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
        var overtimeSaid = false
        var steadySaid = false

        val signalSteps get() = steps.filter { it.choice == null }
        val choiceSteps get() = steps.filter { it.choice != null }
        val allDone get() = steps.all { it.optional || it.id in doneAt }

        /** 선택 단계는 목록에서 자기보다 앞에 있는 단계가 모두 끝나면 묻는다(끝에 있으면 신호 단계가 다 끝난 뒤). */
        fun currentChoice(): StepDef? {
            val i = steps.indexOfFirst { it.id !in doneAt }
            return if (i >= 0 && steps[i].choice != null) steps[i] else null
        }

        fun shownLimitMs(): Long = if (def.id == SituationId.SUDDEN_STOP) config.suddenStopStarsMs.last() else limitMs

        /** 이 시간이 지나면 실패. null이면 시간 제한 없음. */
        fun deadlineMs(): Long? = when {
            // 밟은 뒤의 비상등 확인은 시간에 쫓기지 않는다
            def.id == SituationId.SUDDEN_STOP -> if ("brake" in doneAt) null else config.suddenStopStarsMs.last()
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
    /** 방금 고른 답의 표시(단계 id, 고른 보기, 정답 보기, 고른 때). 맞았으면 표시가 끝날 때 그 단계를 끝낸다. */
    private var answerShown: AnswerShown? = null
    private class AnswerShown(val stepId: String, val picked: Int, val correct: Int, val atMs: Long)
    private var voiceBusy = false
    private var voiceIdleSinceMs = Long.MIN_VALUE / 2
    /** 끊긴 챕터마다 이어 하기 지점을 따로 기억한다. 가장 최근에 끊긴 것이 맨 뒤다. */
    private val resumes = LinkedHashMap<String, ResumePoint>()
    /** 평온 주행 동안 미리 정해 둔 다음 상황(장소를 미리 맞춰 두려고). */
    private var upcoming: SituationId? = null
    /** 교관 대결에서 이 상황 하나만 하고 끝낸다(리뷰의 "1분 연습"). */
    private var single = false
    /** "비상등은 제가 볼 수 없어서 여쭤봐요"는 한 번의 훈련에서 한 번만 말한다. */
    private var wiperSfxOn = false
    private val events = mutableListOf<CoachEvent>()
    private val scene = SceneSim(config)

    // ---------- 공개 API ----------

    override fun chapters(): List<ChapterInfo> = ChapterCatalog.chapters.map { ch ->
        val playable = ch.situations.filter { isPlayable(it, lastState) }
        ChapterInfo(ch.id, ch.title, ch.story, playable, ch.estMinutes, locked = playable.isEmpty())
    }

    override fun start(chapterId: String, nowMs: Long, from: SituationId?) {
        val ch = ChapterCatalog.chapters.firstOrNull { it.id == chapterId } ?: return
        reset(Mode.CHAPTER)
        chapter = ch
        val playable = ch.situations.filter { isPlayable(it, lastState) }
        val first = from?.takeIf { it in playable }
        val saved = resumes[chapterId]?.takeIf { first == null }
        if (first != null) {
            // 추천한 상황부터 하고, 나머지는 원래 순서대로 이어 간다(챕터 전체를 하므로 별·면허 계산은 그대로다)
            queue.add(first)
            queue.addAll(playable - first)
            say("${ch.title}. ${defs.getValue(first).title}부터 해 볼게요.")
        } else if (saved != null) {
            queue.addAll(saved.remaining)
            results.addAll(saved.results)
            say("이어서 할게요. ${ch.title}.")
        } else {
            queue.addAll(playable)
            say("${ch.title}. ${ch.story}")
        }
        // 이 챕터의 지점만 지운다(다른 챕터의 이어 하기는 남는다)
        resumes.remove(chapterId)
        events += CoachEvent.Ambient(AmbientMood.CALM)
        enterCruise(nowMs, first = true)
    }

    override fun startSurprise(nowMs: Long) {
        reset(Mode.SURPRISE)
        say("돌발 모드예요. 언제 뭐가 터질지 몰라요. 몇 개나 버티나 볼까요?")
        events += CoachEvent.Ambient(AmbientMood.CALM)
        enterCruise(nowMs, first = true)
    }

    override fun startVersus(nowMs: Long) {
        reset(Mode.VERSUS)
        say("교관 대결이에요. 옆자리에서 상황을 골라 주세요.")
        events += CoachEvent.Ambient(AmbientMood.CALM)
        enterCruise(nowMs, first = false)
    }

    override fun inject(situation: SituationId, nowMs: Long, single: Boolean) {
        if (mode != Mode.VERSUS || phase == TrainingPhase.IDLE || phase == TrainingPhase.CHAPTER_DONE) {
            reset(Mode.VERSUS)
            events += CoachEvent.Ambient(AmbientMood.CALM)
            enterCruise(nowMs, first = false)
        }
        this.single = single
        if (!isPlayable(situation, lastState)) {
            say("그 상황은 이 차에서 아직 연습할 수 없어요.")
            if (single) finishSingle(nowMs)
            return
        }
        if (phase == TrainingPhase.CRUISE) enterBriefing(situation, nowMs, attempt = 0) else queue.addLast(situation)
    }

    override fun skip(nowMs: Long) {
        val r = run ?: return
        when {
            phase == TrainingPhase.FAIL && r.retryPending -> {
                r.retryPending = false
                afterFail(nowMs)
            }
            r.attempt > 0 && (phase == TrainingPhase.BRIEFING || phase == TrainingPhase.ACTIVE || phase == TrainingPhase.HINT) -> {
                // 다시 하던 상황을 접는다. 결과는 앞선 실패 그대로 남는다
                r.def.loopSfx.forEach { events += CoachEvent.StopSfx(it) }
                events += CoachEvent.Ambient(AmbientMood.CALM)
                say("괜찮아요. 다음 상황으로 넘어갈게요.", priority = 2)
                enterCruise(nowMs, first = false)
            }
        }
    }

    override fun resumeStates(): List<ResumeState> = resumes.values.map { ResumeState(it.chapterId, it.remaining, it.results) }

    override fun restoreResume(states: List<ResumeState>) {
        resumes.clear()
        states.filter { s -> s.remaining.isNotEmpty() && ChapterCatalog.chapters.any { it.id == s.chapterId } }
            .forEach { resumes[it.chapterId] = ResumePoint(it.chapterId, it.remaining, it.results) }
    }

    override fun voiceBusy(busy: Boolean, nowMs: Long) {
        if (voiceBusy && !busy) voiceIdleSinceMs = nowMs
        voiceBusy = busy
    }

    /** 타비의 말이 끝나고 [TrainingConfig.voicePauseMs]만큼 쉬었거나, 이 단계가 시작된 뒤 상한이 지났다. */
    private fun voiceSettled(nowMs: Long): Boolean =
        nowMs - phaseStartMs >= config.voiceWaitMaxMs || (!voiceBusy && nowMs - voiceIdleSinceMs >= config.voicePauseMs)

    override fun tick(state: VehicleState, nowMs: Long): Pair<TrainingView, List<CoachEvent>> {
        val dtMs = lastTickMs?.let { (nowMs - it).coerceIn(0, 1_000) } ?: 0
        lastTickMs = nowMs
        lastState = state
        when (phase) {
            TrainingPhase.CRUISE -> onCruise(nowMs)
            TrainingPhase.BRIEFING -> onBriefing(state, nowMs)
            TrainingPhase.ACTIVE, TrainingPhase.HINT -> onActive(state, nowMs)
            TrainingPhase.SUCCESS -> if (nowMs - phaseStartMs >= config.successHoldMs) afterSuccess(nowMs)
            // "한 번 더 해 볼까요?"가 끝난 뒤에 다시 하거나 넘어간다
            TrainingPhase.FAIL -> if (nowMs - phaseStartMs >= config.failHoldMs && voiceSettled(nowMs)) afterFail(nowMs)
            TrainingPhase.IDLE, TrainingPhase.CHAPTER_DONE -> Unit
        }
        val r = run
        scene.update(
            dtMs = dtMs, phase = phase, situation = r?.def?.id, doneSteps = r?.doneAt?.keys.orEmpty(),
            stepCount = r?.steps?.size ?: 0, state = state,
        )
        // 비가 오는 장면에서 와이퍼를 켜면 와이퍼 소리가 난다
        val wiping = phase != TrainingPhase.IDLE && phase != TrainingPhase.CHAPTER_DONE &&
            scene.raining && (state.wiper ?: WiperLevel.OFF) != WiperLevel.OFF
        if (wiping != wiperSfxOn) {
            wiperSfxOn = wiping
            events += if (wiping) CoachEvent.Sfx("wiper", loop = true) else CoachEvent.StopSfx("wiper")
        }
        val out = events.toList()
        events.clear()
        return view(nowMs) to out
    }

    override fun answer(choiceOrText: String, nowMs: Long) {
        val r = run ?: return
        if (phase != TrainingPhase.ACTIVE && phase != TrainingPhase.HINT) return
        val step = r.currentChoice() ?: return
        val choice = step.choice ?: return
        if (answerShown != null) return // 정답 표시 중에는 다음 답을 받지 않는다
        val m = choice.match(choiceOrText)
        if (m != null) answerShown = AnswerShown(step.id, m, choice.correct, nowMs)
        when (m) {
            null -> say("잘 못 들었어요. 번호로 말해도 돼요.", priority = 1)
            choice.correct -> events += CoachEvent.Sfx("click") // 단계는 정답 표시가 끝날 때 끝난다
            else -> {
                if (!choice.penaltyFree) r.penalties.add("WRONG_${step.id}")
                say(choice.wrongLine, priority = 1, expectReply = choice.replyKind.takeIf { choice.penaltyFree })
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
                    resumes.remove(ch.id)
                    if (remaining.isEmpty()) completeChapterProgress(ch, kept) else resumes[ch.id] = ResumePoint(ch.id, remaining, kept)
                }
                Mode.SURPRISE -> updateSurpriseBest()
                else -> Unit
            }
            events += CoachEvent.Ambient(AmbientMood.OFF)
        }
        if (wiperSfxOn) {
            wiperSfxOn = false
            events += CoachEvent.StopSfx("wiper")
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

    override fun resumableChapter(): String? = resumes.keys.lastOrNull()

    override fun resumableChapters(): List<String> = resumes.keys.toList()

    override fun resumed(nowMs: Long) {
        val r = run ?: return
        if (phase != TrainingPhase.ACTIVE && phase != TrainingPhase.HINT) return
        // 멈춘 동안 꺼 둔 반복 소리를 다시 틀고, 떠 있던 질문은 한 번 더 묻는다
        r.def.loopSfx.forEach { events += CoachEvent.Sfx(it, loop = true) }
        if (wiperSfxOn) events += CoachEvent.Sfx("wiper", loop = true)
        r.choiceAsked = null
    }

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
        upcoming = null
        single = false
    }

    private fun enterCruise(nowMs: Long, first: Boolean) {
        phase = TrainingPhase.CRUISE
        phaseStartMs = nowMs
        firstCruise = first
        run = null
        // 다음 상황을 미리 정해 그 장소에서 평온하게 달리다가 상황을 맞는다(상황이 터질 때 장소가 갑자기 바뀌지 않게)
        upcoming = when (mode) {
            Mode.CHAPTER -> queue.firstOrNull()
            Mode.SURPRISE -> pickSurprise()
            else -> null
        }
        upcoming?.let { scene.prepare(it, allowedPlaces(), jump = first) }
    }

    /** 챕터 이름과 장소가 어긋나지 않게 챕터마다 나올 수 있는 장소를 좁힌다("밤 고속도로"는 고속도로만). */
    private fun allowedPlaces(): List<String>? = if (mode == Mode.CHAPTER) chapter?.let { config.chapterPlaces[it.id] } else null

    private fun onCruise(nowMs: Long) {
        val wait = if (firstCruise) config.firstCruiseMs else config.cruiseMs
        if (mode == Mode.VERSUS) {
            // 하나만 하기로 한 연습은 그 상황이 끝나면 닫는다
            if (single && lastResult != null && queue.isEmpty()) {
                finishSingle(nowMs)
                return
            }
            // 교관 대결은 친구가 고를 때까지 계속 달린다.
            queue.removeFirstOrNull()?.let { enterBriefing(it, nowMs, 0) }
            return
        }
        if (nowMs - phaseStartMs < wait) return
        // 한 줄 정리(교훈)가 끝나고 조금 쉰 뒤에 다음 상황이나 챕터 끝으로 넘어간다
        if (!voiceSettled(nowMs)) return
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
        Mode.SURPRISE -> (upcoming ?: pickSurprise()).also { upcoming = null }
        else -> queue.removeFirstOrNull()
    }

    private fun pickSurprise(): SituationId? {
        val pool = SituationId.entries.filter {
            // 말로 푸는 상황은 빼고, 확인만 하는 단계(비상등 켰나요?)는 enterBriefing에서 뺀다
            it !in config.surpriseExclude && isPlayable(it, lastState) && defs.getValue(it).steps.none { s -> s.choice != null && !s.choice.penaltyFree }
        }
        val choices = pool.filter { it != lastSituation }.ifEmpty { pool }
        return if (choices.isEmpty()) null else choices[config.random.nextInt(choices.size)]
    }

    /** @return 상황을 시작했으면 true. 필요한 신호가 없으면 false(건너뜀). */
    private fun enterBriefing(id: SituationId, nowMs: Long, attempt: Int): Boolean {
        val def = defs[id] ?: return false
        if (!isPlayable(id, lastState)) return false
        val state = lastState
        val steps = def.steps.filter { (state == null || it.available(state)) && !(mode == Mode.SURPRISE && it.choice != null) }
            .map { it.shuffled(config.random) }
        scene.prepare(id, allowedPlaces(), jump = false)
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
        answerShown?.let { a ->
            if (nowMs - a.atMs >= ANSWER_SHOW_MS) {
                answerShown = null
                if (a.picked == a.correct && a.stepId !in r.doneAt) {
                    r.doneAt[a.stepId] = a.atMs
                    r.currentChoice()?.let { askChoice(r, it) }
                }
            }
        }
        val sinceActive = nowMs - r.activeStartMs
        for (step in r.signalSteps) {
            if (step.id in r.doneAt) continue
            if (step.after != null && step.after !in r.doneAt) {
                r.holdSince.remove(step.id)
                continue
            }
            val ok = step.doneWithSpeedRatio?.invoke(state, scene.speed / scene.cruiseKmh)
                ?: step.doneWithSpeed?.invoke(state, scene.speed) ?: step.done(state)
            if (ok && (!step.needsClearRing || scene.ringClear) && (!step.needsPuddlePassed || scene.puddlePassed)) {
                val since = r.holdSince.getOrPut(step.id) { nowMs }
                if (nowMs - since >= step.holdMs) {
                    r.doneAt[step.id] = since
                    events += CoachEvent.Sfx("click")
                    // 감속이 끝나면 비상등을 권하기만 한다(묻거나 판정하지 않는다)
                    if (config.lowVisibilityHazardStep && r.def.id in HAZARD_TIP_AFTER && step.id == HAZARD_TIP_AFTER[r.def.id]) {
                        say(SituationCatalog.HAZARD_TIP, priority = 1)
                    }
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
        // 수막현상: 줄이지 않고 물 고인 곳에 들어가면 실패. 물 위에서 밟거나 꺾으면 한마디만 한다(채점하지 않는다)
        if (r.def.id == SituationId.HYDROPLANING && scene.puddleOn) {
            if ("slow_down" !in r.doneAt) {
                fail(r, nowMs, SituationCatalog.HYDRO_TOO_FAST)
                return
            }
            val rough = state.brakePressed == true || kotlin.math.abs(state.steeringAngleDeg ?: 0f) > config.hydroSteerDeg
            if (rough && !r.steadySaid) {
                r.steadySaid = true
                say(SituationCatalog.HYDRO_STEADY, priority = 1)
            }
        }
        // 회전교차로: 깜빡이 없이 들어갔으면(진입까지 했으면) 실패로 두지 않고 감점하고 끝낸다
        if (r.def.id == SituationId.ROUNDABOUT_ENTRY && "enter" in r.doneAt && "signal_left" !in r.doneAt && r.steps.any { it.id == "signal_left" }) {
            r.doneAt["signal_left"] = nowMs
            if (r.penalties.add("ROUNDABOUT_NO_SIGNAL")) say(SituationCatalog.ROUNDABOUT_NO_SIGNAL, priority = 1)
            succeed(r, nowMs)
            return
        }
        // 회전교차로: 도는 차를 보내기 전에 양보선을 넘으면 실패
        if (r.def.id == SituationId.ROUNDABOUT_ENTRY && "yield_slow" !in r.doneAt && scene.ringEntered) {
            fail(r, nowMs, SituationCatalog.ROUNDABOUT_YIELD_LINE)
            return
        }
        val hintAt = if (r.limitMs > 0) (r.limitMs * config.hintAtFraction).toLong() else config.untimedHintMs
        if (!r.def.noHint && !r.hinted && sinceActive >= hintAt) {
            r.hinted = true
            phase = TrainingPhase.HINT
            val missing = r.steps.first { !it.optional && it.id !in r.doneAt }
            if (missing.choice == null) say(missing.hint, priority = 1)
        }
        val deadline = r.deadlineMs()
        if (deadline != null && sinceActive >= deadline) {
            fail(r, nowMs)
            return
        }
        // 제한 시간이 지나면 바로 알려 준다(그 뒤로도 같은 시간만큼은 끝낼 수 있다)
        if (!r.overtimeSaid && r.limitMs > 0 && r.def.id != SituationId.SUDDEN_STOP && sinceActive >= r.limitMs) {
            r.overtimeSaid = true
            say("시간이 지났어요. 괜찮아요, 천천히 끝까지 해 봐요.", priority = 1)
        }
    }

    private fun askChoice(r: Run, step: StepDef) {
        val c = step.choice ?: return
        r.choiceAsked = step.id
        if (c.replyKind == ReplyKind.YES_NO) {
            // 비상등 신호는 앱이 읽을 수 없어 말로 확인한다. 왜 묻는지 처음 한 번 알려 준다
            say(c.question, priority = 1, expectReply = ReplyKind.YES_NO)
            return
        }
        val options = c.options.mapIndexed { i, o -> "${i + 1}번, ${o.text}." }.joinToString(" ")
        say("${c.question} $options", priority = 1, expectReply = ReplyKind.CHOICE)
    }

    private fun succeed(r: Run, nowMs: Long) {
        r.endMs = nowMs
        val optionalIds = r.steps.filter { it.optional }.map { it.id }.toSet()
        val measured = r.doneAt.filterKeys { it !in r.preDone && it !in optionalIds }.values
        val reaction = when {
            // 밟은 채로 시작했으면 반응 시간을 잴 수 없다
            r.def.id == SituationId.SUDDEN_STOP -> r.doneAt["brake"]?.minus(r.activeStartMs)?.takeIf { "brake" !in r.preDone }
            // 확인만 하는 상황(내리기 전)은 시간을 재지 않는다
            r.def.id == SituationId.BEFORE_EXIT -> null
            // 보기를 읽는 시간이 들어가 부정확하므로 퀴즈·질문이 있는 상황은 시간을 재지 않는다(v29)
            r.def.id in NO_TIME -> null
            r.def.successLine != null -> null // 버티는 상황은 걸린 시간을 재지 않는다
            else -> measured.maxOrNull()?.minus(r.activeStartMs)
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
        // 말로만 답하는 상황(S07·S08)은 시간 대신 틀린 횟수로.
        if (r.steps.all { it.choice != null }) {
            return when (r.penalties.count { it.startsWith("WRONG_") }) {
                0 -> 3
                1 -> 2
                else -> 1
            }
        }
        if (penalized) return 1
        if (r.limitMs <= 0) return 3
        val rt = reaction ?: 0
        return when {
            rt <= (config.threeStarMs[r.def.id] ?: (r.limitMs * config.threeStarFraction).toLong()) -> 3
            rt <= r.limitMs -> 2
            else -> 1
        }
    }

    private fun successLine(r: Run, stars: Int, reaction: Long?): String {
        if (r.def.id == SituationId.BEFORE_EXIT) return "좋아요. 이제 내려도 돼요."
        r.def.successLine?.let { return it }
        val sec = reaction?.let { String.format(Locale.ROOT, "%.1f", it / 1000.0) }
        // 급정거는 밟았는지만 읽는다. 얼마나 세게 밟았는지는 재지 못했다고 알려 준다
        if (r.def.id == SituationId.SUDDEN_STOP) {
            val head = if (sec == null) "밟았어요." else if (stars >= 2) "제때 밟았어요. ${sec}초 걸렸어요." else "밟았어요. ${sec}초, 조금 늦었어요."
            return "$head 세기는 재지 않았어요. 실제로는 끝까지 세게 밟아요."
        }
        return when {
            sec == null && stars >= 3 && r.def.id in EMERGENCIES -> "순서를 지켰어요."
            sec == null && stars >= 3 && r.def.id in NO_TIME -> "칭찬해요."
            sec == null -> "좋아요."
            // 비상 상황은 별 3개여도 칭찬 대신 사실만 말한다(숙달했다는 말이 과신을 키운다)
            stars >= 3 && r.def.id in EMERGENCIES -> "순서를 지켰어요. ${sec}초 걸렸어요."
            stars >= 3 -> "칭찬해요. ${sec}초 걸렸어요."
            stars == 2 -> "좋아요. ${sec}초 걸렸어요."
            r.penalties.isNotEmpty() -> "됐어요. ${sec}초. 다음엔 감점 없이 해 봐요."
            else -> "됐어요. ${sec}초, 조금 늦었지만 다 했어요."
        }
    }

    private fun fail(r: Run, nowMs: Long, line: String? = null) {
        r.endMs = nowMs
        val missed = r.steps.filter { !it.optional && it.id !in r.doneAt }
        record(SituationResult(r.def.id, 0, null, missed.map { it.id }, r.penalties.toList()), r.attempt)
        r.retryPending = mode == Mode.CHAPTER && config.retryOnFail && r.attempt == 0
        phase = TrainingPhase.FAIL
        phaseStartMs = nowMs
        r.def.loopSfx.forEach { events += CoachEvent.StopSfx(it) }
        events += CoachEvent.Sfx("fail")
        events += CoachEvent.Ambient(AmbientMood.CALM)
        val tip = line ?: missed.firstOrNull()?.hint ?: ""
        val again = if (r.retryPending) " 한 번 더 해 볼까요?" else ""
        say("괜찮아요, 연습이에요. $tip$again".trim(), priority = 1)
    }

    private fun afterSuccess(nowMs: Long) {
        if (mode == Mode.VERSUS || mode == Mode.SURPRISE || queue.isNotEmpty()) events += CoachEvent.Ambient(AmbientMood.CALM)
        // 챕터의 마지막 상황도 교훈을 말한다(끝 인사는 평온 주행 뒤에 이어진다)
        lessonPending?.let { say(it) }
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
        resumes.remove(ch.id)
        events += CoachEvent.Sfx("success")
        events += CoachEvent.Ambient(AmbientMood.CALM)
        val checkOnly = res.results.isNotEmpty() && res.results.all { it.id == SituationId.BEFORE_EXIT }
        val weakest = res.results.minByOrNull { it.stars }?.takeIf { it.stars < 3 && !checkOnly }
        val parts = buildList {
            // 확인만 하는 챕터(내리기 전에)는 별을 말하지 않는다
            add(if (checkOnly) "${ch.title} 끝! 확인을 마쳤어요." else "${ch.title} 끝! 별 ${res.totalStars}개예요.")
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
        say(
            when {
                survived == 0 -> "이번엔 버티지 못했어요. 괜찮아요, 한 번 더 해 봐요."
                newBest -> "${survived}개 버텼어요. 최고 기록이에요!"
                else -> "${survived}개 버텼어요."
            },
            priority = 1,
        )
    }

    /** 하나만 하는 연습(리뷰의 "1분 연습")이 끝났다. 결과 화면으로 넘어갈 수 있게 챕터 끝과 같은 상태로 둔다. */
    private fun finishSingle(nowMs: Long) {
        chapterResult = ChapterResult(ChapterCatalog.PRACTICE, results.toList(), results.sumOf { it.stars })
        phase = TrainingPhase.CHAPTER_DONE
        phaseStartMs = nowMs
        single = false
        events += CoachEvent.Ambient(AmbientMood.CALM)
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
            // 앞차 급정거의 초시계는 브레이크를 밟은 순간 멈춘다(그 뒤의 비상등 확인은 시간을 재지 않는다)
            r.def.id == SituationId.SUDDEN_STOP && "brake" in r.doneAt -> r.doneAt.getValue("brake") - r.activeStartMs
            else -> (r.endMs ?: nowMs) - r.activeStartMs
        }
        val choice = if (r != null && (phase == TrainingPhase.ACTIVE || phase == TrainingPhase.HINT)) {
            r.currentChoice()?.let { st ->
                st.choice?.let { c ->
                    val a = answerShown?.takeIf { it.stepId == st.id }
                    ChoiceView(c.question, c.options.map { it.text }, a?.picked, a?.correct)
                }
            }
        } else {
            null
        }
        val title = r?.def?.title ?: when (mode) {
            Mode.CHAPTER -> chapter?.title ?: ""
            Mode.SURPRISE -> "돌발 모드"
            Mode.VERSUS -> if (single) "1분 연습" else "교관 대결"
            null -> chapterResult?.let { res -> ChapterCatalog.chapters.firstOrNull { it.id == res.chapterId }?.title } ?: ""
        }
        val chapterId = chapter?.id ?: when (mode) {
            Mode.SURPRISE -> ChapterCatalog.SURPRISE
            // 상황 하나만 하는 연습은 교관 대결과 다른 이름으로 보인다
            Mode.VERSUS -> if (single) ChapterCatalog.PRACTICE else ChapterCatalog.VERSUS
            else -> chapterResult?.chapterId
        }
        return TrainingView(
            phase = phase,
            chapterId = chapterId,
            situation = r?.def?.id,
            title = title,
            steps = r?.steps?.map { StepView(it.id, it.label, it.id in r.doneAt, it.optional) }.orEmpty(),
            elapsedMs = elapsed,
            limitMs = r?.shownLimitMs() ?: 0,
            virtualSpeedKmh = scene.speed,
            scene = scene.params(),
            lastResult = lastResult,
            choice = choice,
            chapterResult = chapterResult,
            surviveCount = if (mode == Mode.SURPRISE || chapterResult?.chapterId == ChapterCatalog.SURPRISE) results.count { it.stars > 0 } else 0,
            canSkip = r != null && ((phase == TrainingPhase.FAIL && r.retryPending) ||
                (r.attempt > 0 && (phase == TrainingPhase.BRIEFING || phase == TrainingPhase.ACTIVE || phase == TrainingPhase.HINT))),
            overtime = r != null && (phase == TrainingPhase.ACTIVE || phase == TrainingPhase.HINT) && r.limitMs > 0 &&
                r.def.id != SituationId.SUDDEN_STOP && elapsed >= r.limitMs,
            opening = phase == TrainingPhase.CRUISE && firstCruise && mode != Mode.VERSUS,
        )
    }

    private companion object {
        /** 스위치를 되돌린 뒤 상황이 터지기까지 숨 고를 시간. */
        const val SETUP_SETTLE_MS = 1_000L
        /** 고른 답을 맞음·틀림 색으로 보여 주는 시간. */
        const val ANSWER_SHOW_MS = 1_000L
        /** 별 3개여도 "칭찬해요" 대신 "순서를 지켰어요"라고 하는 상황. 수막현상은 따로 정한 성공 대사를 쓴다. */
        /** 이 단계가 끝나면 "비상등도 켜 주세요."라고 권한다. */
        val HAZARD_TIP_AFTER = mapOf(
            SituationId.HEAVY_RAIN to "slow_down", SituationId.FOGGED_WINDSHIELD to "slow_down",
            SituationId.DENSE_FOG to "slow_down", SituationId.SUDDEN_STOP to "brake",
        )
        /** 퀴즈·질문이 들어가 걸린 시간을 말하지 않는 상황. */
        val NO_TIME = setOf(
            SituationId.WARNING_LIGHT, SituationId.BREAKDOWN, SituationId.HIPASS_WRONG_LANE, SituationId.TOLL_BOOTH, SituationId.BEFORE_EXIT,
        )
        val EMERGENCIES = setOf(SituationId.UNINTENDED_ACCEL, SituationId.BREAKDOWN, SituationId.SUDDEN_STOP)
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
    private var sign: String? = null
    private var slip = 0f
    private var place = ScenePlace.HIGHWAY
    /** 지금 장소의 평소 속도. */
    val cruiseKmh: Float get() = config.cruiseKmhAt(place)
    private var placedFor: SituationId? = null
    /** 주차장 장면에서 챕터가 끝나면 끝 화면도 주차장이다. */
    private var parked = false
    /** 일반 차로 요금소까지 남은 거리(m). 3D 장면이 요금소를 세우는 거리와 같은 값에서 시작해 화면 속 속도로 줄인다. */
    private var boothM = 0f
    private var boothAhead = false
    /** 회전교차로: 양보선까지 남은 거리(m)와 도는 차가 내 진입 지점을 지나가기까지 남은 시간(ms, 지나가면 음수). */
    private var ringM = 0f
    private var ringPassMs = 0L
    private var ringAhead = false
    /** 수막현상: 물 고인 곳이 시작되는 곳까지 남은 거리(m, 들어서면 음수). */
    private var puddleM = 0f
    private var puddleAhead = false
    val puddleOn: Boolean get() = puddleAhead && puddleM <= 0f && puddleM > -config.hydroPuddleM
    val puddlePassed: Boolean get() = puddleAhead && puddleM <= -config.hydroPuddleM
    /** 도는 차가 지나가 회전교차로가 비었다. */
    val ringClear: Boolean get() = ringAhead && ringPassMs <= -config.roundaboutClearAfterMs
    /** 양보선을 넘어 회전교차로에 들어섰다. */
    val ringEntered: Boolean get() = ringAhead && ringM <= 0f
    /** 비가 보이는 중(와이퍼 소리를 낼지). */
    val raining: Boolean get() = rain > 0.25f

    /**
     * 다음 상황 [next]의 장소를 미리 정한다. 평온 주행이 시작될 때 불러서, 그 장소를 달리다가 상황을 맞게 한다.
     * @param allowed 챕터가 허락하는 장소. null이면 제한 없음 @param jump 처음 시작이라 속도를 그 장소의 평소 속도로 바로 맞춘다
     */
    fun prepare(next: SituationId, allowed: List<String>?, jump: Boolean) {
        if (next == placedFor) return
        placedFor = next
        var options = config.places[next].orEmpty().ifEmpty { listOf(ScenePlace.HIGHWAY) }
        if (allowed != null) options = options.filter { it in allowed }.ifEmpty { options }
        place = options[config.placeRandom.nextInt(options.size)]
        if (jump) speed = cruiseKmh
    }

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

        // 장소는 엔진이 상황을 시작하기 전에 [prepare]로 정해 둔다. 혹시 빠졌으면 여기서 정한다
        val current = situation.takeIf { inSit }
        if (current != null && current != placedFor) prepare(current, null, jump = false)

        rain = when {
            on(SituationId.HEAVY_RAIN) -> approach(rain, if (briefing) 0.6f else 1f, 0.8f, dt)
            on(SituationId.HYDROPLANING) -> approach(rain, 0.7f, 0.8f, dt)
            // 다른 상황이 시작되면 앞 상황의 비는 바로 걷힌다(교관 대결에서 폭우 뒤 졸음에 빗방울이 남지 않게)
            else -> approach(rain, 0f, if (inSit) 1.5f else 0.1f, dt)
        }
        // 수막현상: 물 위에 뜬 동안 차가 좌우로 흔들린다. 브레이크를 밟거나 핸들을 꺾으면 더 크게 미끄러진다
        slip = when {
            // 물 고인 곳 위에서만 흔들린다. 줄이고 들어왔으면 조금, 밟거나 꺾으면(또는 줄이지 않고 들어왔으면) 크게
            on(SituationId.HYDROPLANING) && phase == TrainingPhase.FAIL -> approach(slip, 1f, 3f, dt)
            on(SituationId.HYDROPLANING) && active && puddleOn -> {
                val rough = state.brakePressed == true || kotlin.math.abs(state.steeringAngleDeg ?: 0f) > config.hydroSteerDeg
                approach(slip, if (rough) 1f else 0.3f, 1.5f, dt)
            }
            else -> approach(slip, 0f, 0.5f, dt)
        }
        sign = when {
            on(SituationId.RIGHT_TURN_CROSSWALK) -> "CROSSWALK"
            on(SituationId.HIPASS_WRONG_LANE) -> "HIPASS"
            on(SituationId.ROUNDABOUT_ENTRY) -> "ROUNDABOUT"
            on(SituationId.SCHOOL_ZONE) -> "SCHOOL"
            on(SituationId.TOLL_BOOTH) -> "TOLLBOOTH"
            else -> null
        }
        fogOutside = if (on(SituationId.DENSE_FOG)) approach(fogOutside, 0.85f, 0.6f, dt) else approach(fogOutside, 0f, 0.15f, dt)

        // 성에 제거가 켜져 있어야 걷히기 시작하고, 에어컨·외기가 속도를 더한다
        val clearRate = if (state.defrostFrontOn == true) {
            config.fogClearDefrostPerSec + (if (state.acOn == true) config.fogClearAcPerSec else 0f) +
                (if (state.recirculationOn == false) config.fogClearFreshAirPerSec else 0f)
        } else 0f
        windshieldFog = when {
            // 성공한 뒤에는 스위치를 꺼도 다시 뿌예지지 않고 걷힌다
            on(SituationId.FOGGED_WINDSHIELD) && phase == TrainingPhase.SUCCESS -> approach(windshieldFog, 0f, 0.6f, dt)
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
        parked = when {
            on(SituationId.BEFORE_EXIT) -> true
            inSit || phase == TrainingPhase.IDLE -> false
            else -> parked
        }
        parkingLot = parked

        val braking = state.brakePressed == true
        speed = when {
            parkingLot || phase == TrainingPhase.CHAPTER_DONE -> approach(speed, 0f, 30f, dt)
            on(SituationId.BREAKDOWN) && !briefing -> approach(speed, 0f, 20f, dt)
            on(SituationId.UNINTENDED_ACCEL) && active ->
                if (braking) (speed - (18f + if (state.epbOn()) 12f else 0f) * dt).coerceAtLeast(0f) else (speed + 10f * dt).coerceAtMost(170f)
            // 일반 차로 요금소: 천천히 들어가 부스 옆에 멈춘 뒤 상황이 끝날 때까지 서 있는다.
            // 부스에 닿기 전에 밟으면 천천히 굴러가다가 부스 옆에 와서야 멈춘다(부스가 한참 앞인데 "멈추기"가 끝나지 않게)
            on(SituationId.TOLL_BOOTH) -> when {
                "full_stop" in doneSteps -> 0f
                braking && boothM > config.tollStopM -> approach(speed, config.tollCreepKmh, config.brakeDecelKmhPerSec, dt)
                braking -> (speed - config.brakeDecelKmhPerSec * dt).coerceAtLeast(0f)
                else -> approach(speed, config.slowApproachKmh, 25f, dt)
            }
            // 회전교차로: 들어간 뒤에는 천천히 돈다. 실패했으면 그 자리에 선다.
            // 양보선에 닿기 전에 밟으면 천천히 굴러가다가 양보선 앞에 와서야 멈춘다(요금소와 같다)
            on(SituationId.ROUNDABOUT_ENTRY) && "enter" in doneSteps -> approach(speed, config.roundaboutEnterKmh, 15f, dt)
            on(SituationId.ROUNDABOUT_ENTRY) && phase == TrainingPhase.FAIL -> approach(speed, 0f, 40f, dt)
            on(SituationId.ROUNDABOUT_ENTRY) && braking && ringM > config.roundaboutStopM ->
                minOf(approach(speed, config.roundaboutCreepKmh, config.brakeDecelKmhPerSec, dt), stopBeforeLineKmh())
            // 양보선 앞에서 밟고 있으면 선을 넘지 않고 선다(늦게 밟아도 실제 차처럼 더 세게 밟아 선 앞에 멈춘다)
            on(SituationId.ROUNDABOUT_ENTRY) && braking ->
                minOf((speed - config.brakeDecelKmhPerSec * dt).coerceAtLeast(0f), stopBeforeLineKmh())
            // 수막현상: 계속 밟고 있어도 평소 속도의 절반 아래로는 줄지 않는다(멈춰 서서 물 고인 곳에 닿지 못하는 일이 없게)
            on(SituationId.HYDROPLANING) -> {
                val floor = cruiseKmh * 0.5f
                when {
                    speed < floor -> approach(speed, floor, 15f, dt)
                    braking -> (speed - config.brakeDecelKmhPerSec * dt).coerceAtLeast(floor)
                    "slow_down" in doneSteps -> speed
                    else -> approach(speed, cruiseKmh, if (speed > cruiseKmh) 12f else 6f, dt)
                }
            }
            // 앞차 급정거: 한 번 밟았으면 발을 떼도 앞차 뒤에 멈춰 선다(다시 80으로 올라가지 않는다)
            on(SituationId.SUDDEN_STOP) && (braking || "brake" in doneSteps) -> (speed - config.suddenStopDecelKmhPerSec * dt).coerceAtLeast(0f)
            braking -> (speed - config.brakeDecelKmhPerSec * dt).coerceAtLeast(0f)
            // 우회전하러 들어서거나 요금소를 지날 때는 천천히 간다. 멈춘 뒤에는 상황이 끝날 때까지 서 있는다
            on(SituationId.RIGHT_TURN_CROSSWALK) -> if ("full_stop" in doneSteps) 0f else approach(speed, config.slowApproachKmh, 25f, dt)
            on(SituationId.HIPASS_WRONG_LANE) -> approach(speed, config.slowApproachKmh, 25f, dt)

            // 발을 떼면 다시 천천히 굴러가 양보선을 넘는다(도는 차가 지나가기 전이면 실패)
            on(SituationId.ROUNDABOUT_ENTRY) -> approach(speed, config.slowApproachKmh, 25f, dt)
            // 보호구역: 시속 50으로 들어서고, 멈춘 뒤에는 상황이 끝날 때까지 서 있는다
            on(SituationId.SCHOOL_ZONE) -> if ("full_stop" in doneSteps) 0f else approach(speed, config.schoolApproachKmh, 25f, dt)
            // 앞이 안 보여 속도를 줄였으면 그 상황이 끝날 때까지 줄인 속도로 간다
            inSit && "slow_down" in doneSteps -> speed
            // 평소 속도는 장소를 따른다. 더 빠르게 달리던 중이면 조금 더 빨리 줄인다
            else -> approach(speed, cruiseKmh, if (speed > cruiseKmh) 12f else 6f, dt)
        }

        if (on(SituationId.TOLL_BOOTH)) {
            if (!boothAhead) boothM = config.tollApproachM
            boothAhead = true
            boothM = (boothM - speed / 3.6f * dt).coerceAtLeast(0f)
        } else {
            boothAhead = false
        }

        if (on(SituationId.ROUNDABOUT_ENTRY)) {
            // 처음과 다시 하기(실패 뒤 브리핑) 모두 양보선·도는 차를 처음 자리로 돌린다
            if (!ringAhead || briefing) {
                ringM = config.roundaboutApproachM
                ringPassMs = config.roundaboutPassMs
            }
            ringAhead = true
            // 도는 차와 양보선까지의 거리는 상황이 터진 뒤부터 움직인다(브리핑 동안은 멀리 있다)
            if (!briefing) {
                ringPassMs -= dtMs
                val next = ringM - speed / 3.6f * dt
                // 진입 전에 브레이크를 밟고 있으면 tick 간격(프레임 속도·신호 지연)과 상관없이 선 0.5m 앞에서 멈춘다.
                // 선을 넘는 실패는 밟지 않은 채 넘었을 때만 생긴다
                val stopLine = minOf(ringM, ROUNDABOUT_STOP_GAP_M)
                if (braking && "enter" !in doneSteps && next <= stopLine) {
                    ringM = stopLine
                    speed = 0f
                } else {
                    ringM = next.coerceAtLeast(0f)
                }
            }
        } else {
            ringAhead = false
        }

        if (on(SituationId.HYDROPLANING)) {
            if (!puddleAhead) puddleM = config.hydroApproachM
            puddleAhead = true
            // 물 고인 곳은 상황이 터진 뒤부터 다가온다. 끝난 뒤에는 그 자리에 둔다
            if (active) puddleM -= speed / 3.6f * dt
        } else {
            puddleAhead = false
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
        sign = sign,
        slip = slip,
        place = place,
        ringPassSec = if (ringAhead) ringPassMs / 1000f else null,
        puddleM = if (puddleAhead) puddleM else null,
    )

    /** 회전교차로: 양보선 0.5m 앞에 설 수 있는 가장 빠른 속도(km/h). 보통 브레이크의 두 배 세기로 본다. */
    private fun stopBeforeLineKmh(): Float {
        val room = (ringM - 0.5f).coerceAtLeast(0f)
        val decel = config.brakeDecelKmhPerSec / 3.6f * 2f
        return kotlin.math.sqrt(2f * decel * room) * 3.6f
    }

    private fun approach(cur: Float, target: Float, ratePerSec: Float, dt: Float): Float {
        val step = ratePerSec * dt
        return if (cur < target) minOf(cur + step, target) else maxOf(cur - step, target)
    }

    private companion object {
        /** 회전교차로: 브레이크를 밟고 있으면 양보선에서 이만큼 앞에 선다(m). */
        const val ROUNDABOUT_STOP_GAP_M = 0.5f
        val IN_SITUATION = setOf(TrainingPhase.BRIEFING, TrainingPhase.ACTIVE, TrainingPhase.HINT, TrainingPhase.SUCCESS, TrainingPhase.FAIL)
    }
}

/** 보기가 셋 이상인 퀴즈는 낼 때마다 보기 순서를 섞는다(정답이 늘 1번이면 읽지 않고 맞힐 수 있다). "네/아직" 확인 질문은 그대로 둔다. */
private fun StepDef.shuffled(random: kotlin.random.Random): StepDef {
    val c = choice ?: return this
    if (c.replyKind != ReplyKind.CHOICE || c.options.size < 3) return this
    val order = c.options.indices.shuffled(random)
    val mixed = ChoiceDef(c.question, order.map { c.options[it] }, order.indexOf(c.correct), c.wrongLine, c.replyKind, c.penaltyFree)
    return StepDef(id, label, hint, setup, essential, holdMs, available, done, mixed, doneWithSpeed, after, doneWithSpeedRatio, needsClearRing, needsPuddlePassed, optional)
}
