package io.moodlab.sideseat.core

import java.util.Locale

/**
 * 훈련 엔진(명세 5장). 단일 스레드에서 호출한다.
 *
 * 흐름: CRUISE(평온 주행) → BRIEFING(장면이 바뀌기 시작, 스위치가 이미 켜져 있으면 되돌려 달라고 함)
 * → ACTIVE(초시계, 실제 스위치로 단계 완료) → HINT(제한 시간 절반) → SUCCESS 또는 FAIL → 다음 상황 또는 CHAPTER_DONE.
 *
 * [abort] 뒤에 [tick]을 한 번 더 부르면 정리 이벤트(효과음 정지, 조명 OFF)를 받을 수 있다.
 */
class DefaultTrainingEngine(private val config: TrainingConfig = TrainingConfig()) : TrainingEngine {
    private enum class Mode { CHAPTER, SURPRISE, VERSUS }

    private class ResumePoint(val chapterId: String, val remaining: List<SituationId>, val results: List<SituationResult>)

    private inner class Run(val def: SituationDef, val steps: List<StepDef>, val attempt: Int) {
        val limitMs = config.limitOf(def.id)
        val doneAt = linkedMapOf<String, Long>()
        val preDone = mutableSetOf<String>()
        val holdSince = mutableMapOf<String, Long>()
        val penalties = linkedSetOf<String>()
        val detectors = def.penalties.map { it to it.detector(config) }
        var activeStartMs = 0L
        var endMs: Long? = null
        var hinted = false
        var setupAsked = false
        var setupClearedAt: Long? = null
        var choiceAsked: String? = null
        var retryPending = false

        val signalSteps get() = steps.filter { it.choice == null }
        val choiceSteps get() = steps.filter { it.choice != null }
        val allDone get() = steps.all { it.id in doneAt }

        /** 신호 단계가 다 끝나면 선택 단계를 순서대로 묻는다. */
        fun currentChoice(): StepDef? =
            if (signalSteps.all { it.id in doneAt }) choiceSteps.firstOrNull { it.id !in doneAt } else null

        fun shownLimitMs(): Long = if (def.id == SituationId.SUDDEN_STOP) config.suddenStopStarsMs.last() else limitMs

        /** 이 시간이 지나면 실패. null이면 시간 제한 없음. */
        fun deadlineMs(): Long? = when {
            def.id == SituationId.SUDDEN_STOP -> config.suddenStopStarsMs.last()
            limitMs <= 0 -> null
            else -> limitMs + (limitMs * config.overtimeFraction).toLong()
        }
    }

    private val defs = SituationCatalog.all(config).associateBy { it.id }
    private var progress = TrainingProgress()
    private var mode: Mode? = null
    private var chapter: ChapterDef? = null
    private val queue = ArrayDeque<SituationId>()
    private val results = mutableListOf<SituationResult>()
    private var phase = TrainingPhase.IDLE
    private var phaseStartMs = 0L
    private var firstCruise = false
    private var run: Run? = null
    private var lastSituation: SituationId? = null
    private var lastResult: SituationResult? = null
    private var chapterResult: ChapterResult? = null
    private var lastState: VehicleState? = null
    private var lastTickMs: Long? = null
    private var lessonPending: String? = null
    private var resume: ResumePoint? = null
    private val events = mutableListOf<CoachEvent>()
    private val scene = SceneSim(config)

    // ---------- 공개 API ----------

    override fun chapters(): List<ChapterInfo> = ChapterCatalog.chapters.map { ch ->
        val playable = ch.situations.filter { isPlayable(it, lastState) }
        ChapterInfo(ch.id, ch.title, ch.story, playable, ch.estMinutes, locked = playable.isEmpty())
    }

    override fun start(chapterId: String, nowMs: Long) {
        val ch = ChapterCatalog.chapters.firstOrNull { it.id == chapterId } ?: return
        reset(Mode.CHAPTER)
        chapter = ch
        val saved = resume?.takeIf { it.chapterId == chapterId }
        if (saved != null) {
            queue.addAll(saved.remaining)
            results.addAll(saved.results)
            say("이어서 할게요. ${ch.title}.")
        } else {
            queue.addAll(ch.situations.filter { isPlayable(it, lastState) })
            say("${ch.title}. ${ch.story}")
        }
        resume = null
        events += CoachEvent.Ambient(AmbientMood.CALM)
        enterCruise(nowMs, first = true)
    }

    override fun startSurprise(nowMs: Long) {
        reset(Mode.SURPRISE)
        say("돌발 모드예요. 언제 뭐가 터질지 몰라요. 몇 개나 버티나 볼까요?")
        events += CoachEvent.Ambient(AmbientMood.CALM)
        enterCruise(nowMs, first = true)
    }

    override fun inject(situation: SituationId, nowMs: Long) {
        if (mode != Mode.VERSUS || phase == TrainingPhase.IDLE || phase == TrainingPhase.CHAPTER_DONE) {
            reset(Mode.VERSUS)
            events += CoachEvent.Ambient(AmbientMood.CALM)
            enterCruise(nowMs, first = false)
        }
        if (!isPlayable(situation, lastState)) {
            say("그 상황은 이 차에서 아직 연습할 수 없어요.")
            return
        }
        if (phase == TrainingPhase.CRUISE) enterBriefing(situation, nowMs, attempt = 0) else queue.addLast(situation)
    }

    override fun tick(state: VehicleState, nowMs: Long): Pair<TrainingView, List<CoachEvent>> {
        val dtMs = lastTickMs?.let { (nowMs - it).coerceIn(0, 1_000) } ?: 0
        lastTickMs = nowMs
        lastState = state
        when (phase) {
            TrainingPhase.CRUISE -> onCruise(nowMs)
            TrainingPhase.BRIEFING -> onBriefing(state, nowMs)
            TrainingPhase.ACTIVE, TrainingPhase.HINT -> onActive(state, nowMs)
            TrainingPhase.SUCCESS -> if (nowMs - phaseStartMs >= config.successHoldMs) afterSuccess(nowMs)
            TrainingPhase.FAIL -> if (nowMs - phaseStartMs >= config.failHoldMs) afterFail(nowMs)
            TrainingPhase.IDLE, TrainingPhase.CHAPTER_DONE -> Unit
        }
        val r = run
        scene.update(
            dtMs = dtMs, phase = phase, situation = r?.def?.id, doneSteps = r?.doneAt?.keys.orEmpty(),
            stepCount = r?.steps?.size ?: 0, state = state,
        )
        val out = events.toList()
        events.clear()
        return view(nowMs) to out
    }

    override fun answer(choiceOrText: String, nowMs: Long) {
        val r = run ?: return
        if (phase != TrainingPhase.ACTIVE && phase != TrainingPhase.HINT) return
        val step = r.currentChoice() ?: return
        val choice = step.choice ?: return
        when (choice.match(choiceOrText)) {
            null -> say("잘 못 들었어요. 번호로 말해도 돼요.", priority = 1)
            choice.correct -> {
                r.doneAt[step.id] = nowMs
                events += CoachEvent.Sfx("click")
                r.currentChoice()?.let { askChoice(r, it) }
            }
            else -> {
                r.penalties.add("WRONG_${step.id}")
                say(choice.wrongLine, priority = 1)
            }
        }
    }

    override fun abort(nowMs: Long) {
        val r = run
        if (r != null && phase != TrainingPhase.SUCCESS) r.def.loopSfx.forEach { events += CoachEvent.StopSfx(it) }
        if (phase != TrainingPhase.IDLE && phase != TrainingPhase.CHAPTER_DONE) {
            when (mode) {
                Mode.CHAPTER -> chapter?.let { ch ->
                    val current = r?.def?.id?.takeIf { phase != TrainingPhase.SUCCESS }
                    val remaining = listOfNotNull(current) + queue
                    val kept = results.filter { it.id !in remaining }
                    resume = if (remaining.isEmpty()) null else ResumePoint(ch.id, remaining, kept)
                    if (remaining.isEmpty()) completeChapterProgress(ch, kept)
                }
                Mode.SURPRISE -> updateSurpriseBest()
                else -> Unit
            }
            events += CoachEvent.Ambient(AmbientMood.OFF)
        }
        run = null
        queue.clear()
        mode = null
        phase = TrainingPhase.IDLE
        phaseStartMs = nowMs
    }

    override fun progress(): TrainingProgress = progress

    override fun restore(progress: TrainingProgress) {
        this.progress = progress
    }

    override fun resumableChapter(): String? = resume?.chapterId

    // ---------- 단계 처리 ----------

    private fun reset(m: Mode) {
        mode = m
        chapter = null
        queue.clear()
        results.clear()
        run = null
        lastResult = null
        chapterResult = null
        lessonPending = null
        lastSituation = null
    }

    private fun enterCruise(nowMs: Long, first: Boolean) {
        phase = TrainingPhase.CRUISE
        phaseStartMs = nowMs
        firstCruise = first
        run = null
    }

    private fun onCruise(nowMs: Long) {
        val wait = if (firstCruise) config.firstCruiseMs else config.cruiseMs
        if (mode == Mode.VERSUS) {
            // 교관 대결은 친구가 고를 때까지 계속 달린다.
            queue.removeFirstOrNull()?.let { enterBriefing(it, nowMs, 0) }
            return
        }
        if (nowMs - phaseStartMs < wait) return
        while (true) {
            val next = nextSituation()
            if (next == null) {
                if (mode == Mode.CHAPTER) finishChapter(nowMs) else finishSurprise(nowMs)
                return
            }
            if (enterBriefing(next, nowMs, 0)) return
        }
    }

    private fun nextSituation(): SituationId? = when (mode) {
        Mode.SURPRISE -> {
            val pool = SituationId.entries.filter {
                it !in config.surpriseExclude && isPlayable(it, lastState) && defs.getValue(it).steps.none { s -> s.choice != null }
            }
            val choices = pool.filter { it != lastSituation }.ifEmpty { pool }
            if (choices.isEmpty()) null else choices[config.random.nextInt(choices.size)]
        }
        else -> queue.removeFirstOrNull()
    }

    /** @return 상황을 시작했으면 true. 필요한 신호가 없으면 false(건너뜀). */
    private fun enterBriefing(id: SituationId, nowMs: Long, attempt: Int): Boolean {
        val def = defs[id] ?: return false
        if (!isPlayable(id, lastState)) return false
        val state = lastState
        val steps = def.steps.filter { state == null || it.available(state) }
        run = Run(def, steps, attempt)
        lastSituation = id
        phase = TrainingPhase.BRIEFING
        phaseStartMs = nowMs
        return true
    }

    private fun onBriefing(state: VehicleState, nowMs: Long) {
        val r = run ?: return
        val elapsed = nowMs - phaseStartMs
        val conflicts = r.signalSteps.filter { it.setup != null && it.done(state) }
        if (conflicts.isNotEmpty() && elapsed < config.setupTimeoutMs) {
            if (!r.setupAsked) {
                r.setupAsked = true
                say("시작하기 전에 " + conflicts.joinToString(" ") { "${it.setup}." })
            }
            r.setupClearedAt = null
            return
        }
        if (r.setupAsked && conflicts.isEmpty()) {
            val cleared = r.setupClearedAt ?: nowMs.also { r.setupClearedAt = it }
            if (nowMs - cleared < SETUP_SETTLE_MS) return
        }
        if (elapsed < config.briefingMs) return
        activate(r, conflicts, state, nowMs)
    }

    private fun activate(r: Run, preDone: List<StepDef>, state: VehicleState, nowMs: Long) {
        r.activeStartMs = nowMs
        // 감점 검출기는 터진 순간의 상태를 기준으로 삼는다(앞 상황에서 켜 둔 스위치는 감점하지 않음).
        r.detectors.forEach { (_, d) -> d.update(state, nowMs, 0, emptySet()) }
        preDone.forEach {
            r.doneAt[it.id] = nowMs
            r.preDone += it.id
        }
        phase = TrainingPhase.ACTIVE
        phaseStartMs = nowMs
        events += CoachEvent.Ambient(AmbientMood.ALERT)
        r.def.onceSfx.forEach { events += CoachEvent.Sfx(it) }
        r.def.loopSfx.forEach { events += CoachEvent.Sfx(it, loop = true) }
        say(r.def.intro, priority = 2)
        r.currentChoice()?.let { askChoice(r, it) }
    }

    private fun onActive(state: VehicleState, nowMs: Long) {
        val r = run ?: return
        val sinceActive = nowMs - r.activeStartMs
        for (step in r.signalSteps) {
            if (step.id in r.doneAt) continue
            if (step.done(state)) {
                val since = r.holdSince.getOrPut(step.id) { nowMs }
                if (nowMs - since >= step.holdMs) {
                    r.doneAt[step.id] = since
                    events += CoachEvent.Sfx("click")
                }
            } else {
                r.holdSince.remove(step.id)
            }
        }
        for ((p, d) in r.detectors) {
            if (d.update(state, nowMs, sinceActive, r.doneAt.keys) && r.penalties.add(p.id)) {
                p.line?.let { say(it, priority = 1) }
            }
        }
        r.currentChoice()?.let { if (r.choiceAsked != it.id) askChoice(r, it) }
        if (r.allDone) {
            succeed(r, nowMs)
            return
        }
        val hintAt = if (r.limitMs > 0) (r.limitMs * config.hintAtFraction).toLong() else config.untimedHintMs
        if (!r.def.noHint && !r.hinted && sinceActive >= hintAt) {
            r.hinted = true
            phase = TrainingPhase.HINT
            val missing = r.steps.first { it.id !in r.doneAt }
            if (missing.choice == null) say(missing.hint, priority = 1)
        }
        val deadline = r.deadlineMs()
        if (deadline != null && sinceActive >= deadline) fail(r, nowMs)
    }

    private fun askChoice(r: Run, step: StepDef) {
        val c = step.choice ?: return
        r.choiceAsked = step.id
        val options = c.options.mapIndexed { i, o -> "${i + 1}번, ${o.text}." }.joinToString(" ")
        say("${c.question} $options", priority = 1, expectReply = ReplyKind.CHOICE)
    }

    private fun succeed(r: Run, nowMs: Long) {
        r.endMs = nowMs
        val measured = r.doneAt.filterKeys { it !in r.preDone }.values
        val reaction = if (r.def.id == SituationId.SUDDEN_STOP) {
            r.doneAt["brake"]?.minus(r.activeStartMs)
        } else {
            measured.maxOrNull()?.minus(r.activeStartMs)
        }
        var stars = stars(r, reaction)
        if (r.attempt > 0) stars = minOf(stars, config.retryMaxStars)
        // 시작 전에 이미 켜져 있던 스위치가 있으면 손이 찾아간 게 아니므로 ★3은 주지 않는다.
        if (r.preDone.isNotEmpty()) stars = minOf(stars, 2)
        record(SituationResult(r.def.id, stars, reaction, emptyList(), r.penalties.toList()), r.attempt)
        phase = TrainingPhase.SUCCESS
        phaseStartMs = nowMs
        r.def.loopSfx.forEach { events += CoachEvent.StopSfx(it) }
        events += CoachEvent.Sfx("success")
        events += CoachEvent.Ambient(AmbientMood.SUCCESS)
        say(successLine(r, stars, reaction), priority = 1)
        lessonPending = r.def.lesson
    }

    private fun stars(r: Run, reaction: Long?): Int {
        val penalized = r.penalties.isNotEmpty()
        if (r.def.id == SituationId.SUDDEN_STOP) {
            val t = config.suddenStopStarsMs
            val rt = reaction ?: return 1
            val base = when {
                rt <= t[0] -> 3
                rt <= t[1] -> 2
                rt <= t[2] -> 1
                else -> 0
            }
            return if (penalized) minOf(base, 1) else base
        }
        if (penalized) return 1
        if (r.limitMs <= 0) return 3
        val rt = reaction ?: 0
        return when {
            rt <= r.limitMs * config.threeStarFraction -> 3
            rt <= r.limitMs -> 2
            else -> 1
        }
    }

    private fun successLine(r: Run, stars: Int, reaction: Long?): String {
        if (r.def.id == SituationId.BEFORE_EXIT) return "좋아요. 이제 내려도 돼요."
        val sec = reaction?.let { String.format(Locale.ROOT, "%.1f", it / 1000.0) }
        return when {
            sec == null -> "좋아요."
            stars >= 3 -> "완벽해요. ${sec}초 걸렸어요."
            stars == 2 -> "좋아요. ${sec}초 걸렸어요."
            r.penalties.isNotEmpty() -> "됐어요. ${sec}초. 다음엔 감점 없이 해 봐요."
            else -> "됐어요. ${sec}초, 조금 늦었지만 다 했어요."
        }
    }

    private fun fail(r: Run, nowMs: Long) {
        r.endMs = nowMs
        val missed = r.steps.filter { it.id !in r.doneAt }
        record(SituationResult(r.def.id, 0, null, missed.map { it.id }, r.penalties.toList()), r.attempt)
        r.retryPending = mode == Mode.CHAPTER && config.retryOnFail && r.attempt == 0
        phase = TrainingPhase.FAIL
        phaseStartMs = nowMs
        r.def.loopSfx.forEach { events += CoachEvent.StopSfx(it) }
        events += CoachEvent.Sfx("fail")
        events += CoachEvent.Ambient(AmbientMood.CALM)
        val tip = missed.firstOrNull()?.hint ?: ""
        val again = if (r.retryPending) " 한 번 더 해 볼까요?" else ""
        say("괜찮아요, 연습이에요. $tip$again".trim(), priority = 1)
    }

    private fun afterSuccess(nowMs: Long) {
        if (mode == Mode.VERSUS || mode == Mode.SURPRISE || queue.isNotEmpty()) {
            events += CoachEvent.Ambient(AmbientMood.CALM)
            lessonPending?.let { say(it) }
        }
        lessonPending = null
        enterCruise(nowMs, first = false)
    }

    private fun afterFail(nowMs: Long) {
        val r = run
        when {
            r != null && r.retryPending -> enterBriefing(r.def.id, nowMs, attempt = 1)
            mode == Mode.SURPRISE -> finishSurprise(nowMs)
            else -> enterCruise(nowMs, first = false)
        }
    }

    private fun record(result: SituationResult, attempt: Int) {
        if (attempt > 0 && results.lastOrNull()?.id == result.id) results[results.lastIndex] = result else results += result
        lastResult = result
        val key = result.id.name
        val bestStars = maxOf(progress.bestStars[key] ?: 0, result.stars)
        val reactions = progress.bestReactionMs.toMutableMap()
        if (result.stars > 0 && result.reactionMs != null) {
            reactions[key] = minOf(reactions[key] ?: Long.MAX_VALUE, result.reactionMs)
        }
        progress = progress.copy(
            bestStars = progress.bestStars + (key to bestStars),
            bestReactionMs = reactions,
            totalStarsEarned = progress.totalStarsEarned + result.stars,
        )
    }

    private fun finishChapter(nowMs: Long) {
        val ch = chapter ?: return
        val res = ChapterResult(ch.id, results.toList(), results.sumOf { it.stars })
        val newlyLicensed = completeChapterProgress(ch, res.results)
        chapterResult = res
        phase = TrainingPhase.CHAPTER_DONE
        phaseStartMs = nowMs
        resume = null
        events += CoachEvent.Sfx("success")
        events += CoachEvent.Ambient(AmbientMood.CALM)
        val weakest = res.results.minByOrNull { it.stars }?.takeIf { it.stars < 3 }
        val parts = buildList {
            add("${ch.title} 끝! 별 ${res.totalStars}개예요.")
            if (newlyLicensed) add("이 차 숙련 배지를 받았어요!")
            weakest?.let { add("다음엔 ${defs.getValue(it.id).title} 상황을 한 번 더 해 봐요.") }
        }
        say(parts.joinToString(" "), priority = 1)
    }

    /** 챕터 기록과 이 차 면허를 갱신한다. @return 이번에 처음 면허를 땄으면 true */
    private fun completeChapterProgress(ch: ChapterDef, chapterResults: List<SituationResult>): Boolean {
        val total = chapterResults.sumOf { it.stars }
        val vehicleKey = lastState?.vehicleModelKey ?: TrainingProgress.UNKNOWN_VEHICLE
        val before = progress.licenseFor(vehicleKey)
        val passed = ch.id in config.licenseChapters && chapterResults.isNotEmpty() &&
            chapterResults.all { it.stars >= config.licenseMinStars } &&
            chapterResults.map { it.id }.toSet().containsAll(ch.situations.filter { isPlayable(it, lastState) })
        val cleared = if (passed) before.clearedChapters + ch.id else before.clearedChapters
        val required = config.licenseChapters.filter { id ->
            ChapterCatalog.chapters.firstOrNull { it.id == id }?.situations?.any { isPlayable(it, lastState) } == true
        }
        val licensed = before.licensed || (required.isNotEmpty() && cleared.containsAll(required))
        progress = progress.copy(
            chapterBestStars = progress.chapterBestStars + (ch.id to maxOf(progress.chapterBestStars[ch.id] ?: 0, total)),
            licenses = progress.licenses + (vehicleKey to VehicleLicense(cleared, licensed)),
        )
        return licensed && !before.licensed
    }

    private fun finishSurprise(nowMs: Long) {
        val survived = results.count { it.stars > 0 }
        val newBest = survived > progress.surpriseBest
        updateSurpriseBest()
        chapterResult = ChapterResult(ChapterCatalog.SURPRISE, results.toList(), results.sumOf { it.stars })
        phase = TrainingPhase.CHAPTER_DONE
        phaseStartMs = nowMs
        events += CoachEvent.Ambient(AmbientMood.CALM)
        say(if (newBest) "${survived}개 버텼어요. 최고 기록이에요!" else "${survived}개 버텼어요.", priority = 1)
    }

    private fun updateSurpriseBest() {
        val survived = results.count { it.stars > 0 }
        if (survived > progress.surpriseBest) progress = progress.copy(surpriseBest = survived)
    }

    // ---------- 보조 ----------

    /** 꺼 둔 상황이 아니고, 핵심 단계의 신호가 있으면 할 수 있다. 상태를 모르면 일단 된다고 본다. */
    private fun isPlayable(id: SituationId, state: VehicleState?): Boolean {
        if (id in config.disabled) return false
        val def = defs[id] ?: return false
        if (state == null) return true
        return def.steps.filter { it.essential }.all { it.available(state) } && def.steps.any { it.available(state) }
    }

    private fun say(text: String, priority: Int = 0, expectReply: ReplyKind? = null) {
        events += CoachEvent.Say(text, priority, expectReply)
    }

    private fun view(nowMs: Long): TrainingView {
        val r = run
        val elapsed = when {
            r == null || phase == TrainingPhase.BRIEFING -> 0
            else -> (r.endMs ?: nowMs) - r.activeStartMs
        }
        val choice = if (r != null && (phase == TrainingPhase.ACTIVE || phase == TrainingPhase.HINT)) {
            r.currentChoice()?.choice?.let { c -> ChoiceView(c.question, c.options.map { it.text }) }
        } else {
            null
        }
        val title = r?.def?.title ?: when (mode) {
            Mode.CHAPTER -> chapter?.title ?: ""
            Mode.SURPRISE -> "돌발 모드"
            Mode.VERSUS -> "교관 대결"
            null -> chapterResult?.let { res -> ChapterCatalog.chapters.firstOrNull { it.id == res.chapterId }?.title } ?: ""
        }
        val chapterId = chapter?.id ?: when (mode) {
            Mode.SURPRISE -> ChapterCatalog.SURPRISE
            Mode.VERSUS -> ChapterCatalog.VERSUS
            else -> chapterResult?.chapterId
        }
        return TrainingView(
            phase = phase,
            chapterId = chapterId,
            situation = r?.def?.id,
            title = title,
            steps = r?.steps?.map { StepView(it.id, it.label, it.id in r.doneAt) }.orEmpty(),
            elapsedMs = elapsed,
            limitMs = r?.shownLimitMs() ?: 0,
            virtualSpeedKmh = scene.speed,
            scene = scene.params(),
            lastResult = lastResult,
            choice = choice,
            chapterResult = chapterResult,
            surviveCount = if (mode == Mode.SURPRISE || chapterResult?.chapterId == ChapterCatalog.SURPRISE) results.count { it.stars > 0 } else 0,
        )
    }

    private companion object {
        /** 스위치를 되돌린 뒤 상황이 터지기까지 숨 고를 시간. */
        const val SETUP_SETTLE_MS = 1_000L
    }
}

/**
 * 장면 값 시뮬레이션. 실제 신호에 반응한다(성에 제거 → 김서림이 걷힘, 브레이크 → 앞차와 거리 유지 등).
 * 시간 단위는 초당 변화량.
 */
internal class SceneSim(private val config: TrainingConfig) {
    var speed = config.cruiseSpeedKmh
        private set
    private var rain = 0f
    private var fogOutside = 0f
    private var windshieldFog = 0f
    private var lead: Float? = null
    private var leadBraking = false
    private var glare = 0f
    private var debris = 0f
    private var drowsy = 0f
    private var alert = 0f
    private var tunnel = false
    private var warning: String? = null
    private var parkingLot = false

    fun update(
        dtMs: Long,
        phase: TrainingPhase,
        situation: SituationId?,
        doneSteps: Set<String>,
        stepCount: Int,
        state: VehicleState,
    ) {
        val dt = dtMs / 1000f
        val inSit = situation != null && phase in IN_SITUATION
        val active = phase == TrainingPhase.ACTIVE || phase == TrainingPhase.HINT
        val briefing = phase == TrainingPhase.BRIEFING
        fun on(id: SituationId) = inSit && situation == id

        rain = if (on(SituationId.HEAVY_RAIN)) approach(rain, if (briefing) 0.6f else 1f, 0.8f, dt) else approach(rain, 0f, 0.1f, dt)
        fogOutside = if (on(SituationId.DENSE_FOG)) approach(fogOutside, 0.85f, 0.6f, dt) else approach(fogOutside, 0f, 0.15f, dt)

        val clearRate = (if (state.defrostFrontOn == true) 0.12f else 0f) +
            (if (state.acOn == true) 0.06f else 0f) + (if (state.recirculationOn == false) 0.05f else 0f)
        windshieldFog = when {
            on(SituationId.FOGGED_WINDSHIELD) && briefing -> approach(windshieldFog, 0.85f, 0.6f, dt)
            on(SituationId.FOGGED_WINDSHIELD) && active && clearRate == 0f -> approach(windshieldFog, 1f, 0.06f, dt)
            else -> approach(windshieldFog, 0f, maxOf(clearRate, if (inSit && situation == SituationId.FOGGED_WINDSHIELD) 0f else 0.1f), dt)
        }

        if (on(SituationId.SUDDEN_STOP)) {
            when {
                briefing -> {
                    lead = lead ?: 0.75f
                    leadBraking = false
                }
                active -> {
                    leadBraking = true
                    val closing = if (state.brakePressed == true || "brake" in doneSteps) 0f else 0.5f
                    lead = ((lead ?: 0.75f) - closing * dt).coerceAtLeast(0f)
                }
                phase == TrainingPhase.FAIL -> lead = 0f
                else -> leadBraking = true
            }
        } else {
            lead?.let {
                leadBraking = false
                val next = it + 0.15f * dt
                lead = if (next >= 1f) null else next
            }
        }

        glare = when {
            on(SituationId.ONCOMING_GLARE) && active -> approach(glare, if (state.highBeamOn == false) 0.3f else 1f, 0.3f, dt)
            on(SituationId.ONCOMING_GLARE) -> approach(glare, 0.3f, 0.5f, dt)
            else -> approach(glare, 0f, 0.3f, dt)
        }
        debris = when {
            on(SituationId.WIPER_DEBRIS) && active && (state.wiper ?: WiperLevel.OFF) != WiperLevel.OFF -> approach(debris, 0f, 0.6f, dt)
            on(SituationId.WIPER_DEBRIS) && (briefing || active) -> approach(debris, 0.7f, 1f, dt)
            else -> approach(debris, 0f, 0.6f, dt)
        }
        drowsy = when {
            on(SituationId.DROWSY) && briefing -> approach(drowsy, 0.3f, 0.3f, dt)
            on(SituationId.DROWSY) && active -> {
                val relief = (if (state.recirculationOn == false) 0.08f else 0f) + (if ((state.driverWindowOpenPct ?: 0) > 0) 0.12f else 0f)
                (drowsy + (0.06f - relief) * dt).coerceIn(0f, 0.9f)
            }
            else -> approach(drowsy, 0f, 0.2f, dt)
        }
        tunnel = on(SituationId.TUNNEL)
        warning = if (on(SituationId.WARNING_LIGHT) && !briefing) "TPMS" else null
        parkingLot = on(SituationId.BEFORE_EXIT)

        val braking = state.brakePressed == true
        speed = when {
            parkingLot || phase == TrainingPhase.CHAPTER_DONE -> approach(speed, 0f, 30f, dt)
            on(SituationId.BREAKDOWN) && !briefing -> approach(speed, 0f, 20f, dt)
            on(SituationId.UNINTENDED_ACCEL) && active ->
                if (braking) (speed - (18f + if (state.epbOn()) 12f else 0f) * dt).coerceAtLeast(0f) else (speed + 10f * dt).coerceAtMost(170f)
            braking -> (speed - 25f * dt).coerceAtLeast(0f)
            else -> approach(speed, config.cruiseSpeedKmh, 6f, dt)
        }

        val doneFraction = if (stepCount == 0) 0f else doneSteps.size.toFloat() / stepCount
        alert = when (phase) {
            TrainingPhase.ACTIVE -> 1f - 0.5f * doneFraction
            TrainingPhase.HINT -> 1f - 0.3f * doneFraction
            TrainingPhase.BRIEFING -> 0.3f
            TrainingPhase.FAIL -> 0.6f
            else -> approach(alert, 0f, 1f, dt)
        }
    }

    fun params() = SceneParams(
        night = true,
        rain = rain,
        fogOutside = fogOutside,
        windshieldFog = windshieldFog,
        tunnel = tunnel,
        leadCarDistance = lead,
        leadCarBraking = leadBraking,
        oncomingGlare = glare,
        debris = debris,
        alert = alert,
        warningLight = warning,
        parkingLot = parkingLot,
        drowsyBlur = drowsy,
    )

    private fun approach(cur: Float, target: Float, ratePerSec: Float, dt: Float): Float {
        val step = ratePerSec * dt
        return if (cur < target) minOf(cur + step, target) else maxOf(cur - step, target)
    }

    private companion object {
        val IN_SITUATION = setOf(TrainingPhase.BRIEFING, TrainingPhase.ACTIVE, TrainingPhase.HINT, TrainingPhase.SUCCESS, TrainingPhase.FAIL)
    }
}
