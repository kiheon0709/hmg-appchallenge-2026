package io.moodlab.sideseat.app

import android.os.SystemClock
import android.util.Log
import io.moodlab.sideseat.core.AmbientMood
import io.moodlab.sideseat.core.CoachAction
import io.moodlab.sideseat.core.CoachEvent
import io.moodlab.sideseat.core.DefaultDriveCoach
import io.moodlab.sideseat.core.DefaultSafeDialGame
import io.moodlab.sideseat.core.DefaultTrainingEngine
import io.moodlab.sideseat.core.DefaultTurnSignalGame
import io.moodlab.sideseat.core.DefaultWindowBuzzerGame
import io.moodlab.sideseat.core.DriveCoach
import io.moodlab.sideseat.core.DriveSummary
import io.moodlab.sideseat.core.ReplyKind
import io.moodlab.sideseat.core.RouteFeatures
import io.moodlab.sideseat.core.RouteTraining
import io.moodlab.sideseat.core.SchoolZoneIndex
import io.moodlab.sideseat.core.SituationId
import io.moodlab.sideseat.core.TrainingEngine
import io.moodlab.sideseat.core.TrainingPhase
import io.moodlab.sideseat.core.TrainingProgress
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.ui.CoachUi
import io.moodlab.sideseat.ui.HomeUi
import io.moodlab.sideseat.ui.ReviewUi
import io.moodlab.sideseat.ui.TalkUi
import io.moodlab.sideseat.ui.UiAction
import io.moodlab.sideseat.ui.VehicleChoice
import io.moodlab.sideseat.ui.WakeUi
import io.moodlab.sideseat.vehicle.VehicleGateway
import io.moodlab.sideseat.voice.Instructor
import io.moodlab.sideseat.voice.PleosVoice
import io.moodlab.sideseat.voice.VoiceModule
import io.moodlab.sideseat.voice.WakeListening
import io.moodlab.sideseat.voice.WakeState
import io.moodlab.sideseat.voice.Sfx
import io.moodlab.sideseat.voice.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "SideSeatMeasure"

/**
 * 앱의 두뇌. 차 상태로 화면(모드)을 정하고, 훈련 엔진·코치·미니게임을 돌리고, 그들이 내는 말·소리·조명을
 * 음성·효과음·차량으로 보낸다. 프로세스에 하나만 있고([AppGraph]) 화면이 사라져도 코치는 계속 돈다.
 *
 * 모든 호출은 메인 스레드에서 한다(엔진은 단일 스레드 가정).
 */
class SideSeatController(
    private val scope: CoroutineScope,
    private val gatewayFlow: StateFlow<VehicleGateway>,
    private val voice: Voice,
    private val sfx: Sfx,
    private val instructor: Instructor,
    private val store: ProgressStore,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    /** 주행 중 호출어("선배") 연속 청취. 코치 모드에서만 켠다. 없으면 버튼으로만 질문. */
    private val wake: WakeListening? = null,
    /** 어린이 보호구역(K11). 비면 K11은 꺼진 채로 돈다. */
    zones: SchoolZoneIndex = SchoolZoneIndex.EMPTY,
) {
    private val engine: TrainingEngine = DefaultTrainingEngine()
    private var progress: TrainingProgress = store.loadProgress().also { engine.restore(it) }
    private val coach: DriveCoach = DefaultDriveCoach(zones = zones, practiced = ::practicedSituations)
    private val safeDial = DefaultSafeDialGame()
    private val turnGame = DefaultTurnSignalGame()
    private val windowGame = DefaultWindowBuzzerGame()
    private var lastDrive: DriveSummary? = store.loadLastDrive()

    private val _ui = MutableStateFlow(AppUi(progress = progress))
    val ui: StateFlow<AppUi> = _ui.asStateFlow()

    private val gateway get() = gatewayFlow.value
    private var loopJob: Job? = null
    private var talkJob: Job? = null
    private var lastAmbient: AmbientMood? = null
    private var firstState = true
    /** 내비 경로에서 읽은 것. 없으면 null(기존 "오늘의 추천"). */
    private var route: RouteFeatures? = null
    private val debugRoute = MutableStateFlow<RouteFeatures?>(null)

    /** 디버그: 내비 대신 가짜 경로 정보를 넣는다. null이면 내비 값으로 돌아간다. */
    fun debugSetRoute(features: RouteFeatures?) { debugRoute.value = features }

    fun start() {
        scope.launch {
            gatewayFlow.collectLatest { g ->
                _ui.update { it.copy(usingDebugVehicle = g !== gatewayFlow.value || isDebugGateway(g)) }
                g.states.collect(::onVehicle)
            }
        }
        scope.launch {
            combine(voice.speaking, voice.listening) { s, l -> s to l }.collect { (s, l) ->
                updateTalk { it.copy(speaking = s, listening = l, partialText = if (l) it.partialText else null) }
            }
        }
        scope.launch { gatewayFlow.collectLatest { g -> g.arrivals.collect { Log.i(TAG, "app navi arrival") } } }
        scope.launch {
            gatewayFlow.collectLatest { g ->
                combine(g.routeFeatures, debugRoute) { real, fake -> fake ?: real }.collect {
                    route = it
                    if (_ui.value.mode == AppMode.HOME) refreshHome()
                }
            }
        }
        wake?.let { w -> scope.launch { w.state.collect { st -> _ui.update { it.copy(coach = it.coach.copy(wake = wakeUi(st))) } } } }
        restoreVehicleChoice()
        refreshHome()
    }

    // ---------- 차종 고르기 ----------
    // 국내 VIN 차종 코드를 확인하지 못해 자동 판정이 없다. 사용자가 고른 차종을 저장하고,
    // 음성 모듈의 차종 지정 경로로 넘겨 힌트·결과·질문 답이 그 차의 스위치 위치를 쓰게 한다.

    private fun restoreVehicleChoice() {
        // Pleos 탑재 차를 앞에 둔다
        val order = listOf("grandeur_gn7", "avante_cn8", "avante_cn7", "casper_ax", "ray_tam", "seltos_sp2")
        val choices = VoiceModule.selectableVehicles().map { (id, name) -> VehicleChoice(id, name) }
            .sortedBy { c -> order.indexOf(c.id).let { i -> if (i < 0) order.size else i } }
        val saved = store.loadVehicleChoice()
        val id = saved?.takeIf { s -> choices.any { it.id == s } }
        if (id != null) VoiceModule.forceVehicle(id)
        _ui.update {
            it.copy(home = it.home.copy(
                vehicleChoices = choices, vehicleChoiceId = id, vehicleChosen = saved != null || choices.isEmpty(),
                vehicleName = choices.firstOrNull { c -> c.id == id }?.name,
            ))
        }
    }

    /** 디버그 명령으로 음성 모듈의 차종을 직접 바꾼 뒤 홈의 "이 차" 표시를 맞춘다(저장하지 않는다). */
    fun syncVehicleChoice() {
        val id = VoiceModule.currentVehicle?.id
        val choice = _ui.value.home.vehicleChoices.firstOrNull { it.id == id }
        _ui.update { it.copy(home = it.home.copy(vehicleChoiceId = choice?.id, vehicleName = choice?.name)) }
    }

    private fun chooseVehicle(id: String?) {
        // 주행 중에는 바꾸지 않는다(고르는 판은 정차 중 홈에만 뜬다)
        if (!_ui.value.vehicle.parked) return
        val choice = _ui.value.home.vehicleChoices.firstOrNull { it.id == id }
        VoiceModule.forceVehicle(choice?.id)
        store.saveVehicleChoice(choice?.id)
        Log.i(TAG, "app vehicle choice ${choice?.id ?: "other"}")
        _ui.update { it.copy(home = it.home.copy(vehicleChoiceId = choice?.id, vehicleChosen = true, vehicleName = choice?.name)) }
    }

    private fun isDebugGateway(g: VehicleGateway) = g.javaClass.simpleName.startsWith("Debug")

    /** 말하는 중(SPEAKING)은 talk.speaking으로 보이므로 호출어 안내는 끈다. */
    private fun wakeUi(st: WakeState): WakeUi = when (st) {
        WakeState.LISTENING -> WakeUi.WAITING
        WakeState.FOLLOW_UP -> WakeUi.FOLLOW_UP
        WakeState.HANDLING -> WakeUi.HEARD
        WakeState.BACKED_OFF -> WakeUi.YIELDED
        WakeState.OFF, WakeState.SPEAKING -> WakeUi.OFF
    }

    // ---------- 차 상태 → 모드 ----------

    private fun onVehicle(state: VehicleState) {
        val before = _ui.value
        _ui.update { it.copy(vehicle = state) }
        val mode = before.mode
        if (firstState) {
            firstState = false
            Log.i(TAG, "app first vehicle state parked=${state.parked}")
            if (!state.parked) enterCoach(state)
            return
        }
        if (!state.parked && mode != AppMode.COACH) {
            enterCoach(state)
        } else if (state.parked && mode == AppMode.COACH) {
            enterReview()
        } else if (mode == AppMode.HOME && before.vehicle.vehicleModelKey != state.vehicleModelKey) {
            refreshHome()
        }
        if (before.home.canTrain != state.parked) _ui.update { it.copy(home = it.home.copy(canTrain = state.parked)) }
    }

    private fun setMode(mode: AppMode) {
        val old = _ui.value.mode
        if (old == mode) return
        Log.i(TAG, "app mode $old -> $mode")
        _ui.update { it.copy(mode = mode) }
    }

    /** P를 벗어남: 훈련·미니게임을 즉시 닫고 코치로(명세 4장). */
    private fun enterCoach(state: VehicleState) {
        val t = now()
        when (_ui.value.mode) {
            AppMode.TRAINING -> {
                engine.abort(t)
                val (_, events) = engine.tick(state, t)
                dispatch(events.filterNot { it is CoachEvent.Say || it is CoachEvent.Ambient })
                saveProgress()
            }
            else -> Unit
        }
        stopLoop()
        talkJob?.cancel()
        sfx.stopAll()
        voice.stopSpeaking()
        setAmbient(AmbientMood.OFF, force = true)
        _ui.update {
            it.copy(training = null, miniGame = null, safeDial = null, turnGame = null, windowGame = null, review = null,
                coach = CoachUi(lastLine = null, wake = it.coach.wake, talk = it.coach.talk.copy(partialText = null, answerText = null)))
        }
        setMode(AppMode.COACH)
        coach.onDriveStart(t)
        wake?.start { q -> answerWakeQuestion(q) }
        startLoop(periodMs = 1_000) { coachTick() }
    }

    private fun coachTick() {
        val state = _ui.value.vehicle
        val u = coach.tick(state, now()) ?: return
        Log.i(TAG, "app coach say rule=${u.ruleId} urgent=${u.urgent} '${u.text}'")
        // 위험 단계: 경고등(K10, "안전한 곳에 세우고")만. 다른 안내는 기본 글자색.
        _ui.update { it.copy(coach = it.coach.copy(lastLine = coachLine(u.text), urgent = u.urgent, danger = u.ruleId == "K10")) }
        voice.say(u.text, urgent = u.urgent)
        val kind = u.expectReply ?: return
        talkJob?.cancel()
        talkJob = scope.launch {
            val reply = listenOnce(kind) ?: return@launch
            when (val action = coach.onReply(u.ruleId, reply, now())) {
                is CoachAction.AddRestStopWaypoint -> {
                    val ok = gateway.addRestStopWaypoint()
                    Log.i(TAG, "app rest stop waypoint ok=$ok reason=${action.reason}")
                    voice.say(if (ok) "가까운 휴게소를 경유지로 넣었어요." else "경유지를 넣지 못했어요. 표지판을 같이 봐요.")
                }
                null -> Unit
            }
        }
    }

    /** 다시 P: 주행 요약을 만들고 선배가 총평한다(명세 7장). */
    /** 호출어로 들어온 질문에 주행 중 답(1~2문장). 말이 끝날 때까지 기다려야 다음 청취가 이어진다. */
    private suspend fun answerWakeQuestion(q: String) {
        Log.i(TAG, "app wake question '$q'")
        updateTalk { it.copy(partialText = q, thinking = true, answerText = null) }
        val context = questionContext()
        instructor.prepareQuestion(false, context)
        voice.sayStreaming(instructor.answer(q, false, context).onEach { line ->
            updateTalk { it.copy(thinking = false, answerText = listOfNotNull(it.answerText, line).joinToString(" ")) }
            _ui.update { s -> s.copy(coach = s.coach.copy(lastLine = coachLine(line))) }
        })
        updateTalk { it.copy(thinking = false) }
    }

    private fun enterReview() {
        wake?.stop()
        stopLoop()
        talkJob?.cancel()
        val summary = coach.onDriveEnd(now())
        val previous = lastDrive
        lastDrive = summary
        store.saveLastDrive(summary)
        Log.i(TAG, "app review summary min=${summary.durationMin} rain=${summary.rainMin} night=${summary.nightMin} said=${summary.utterances.size} obs=${summary.observations} " +
            "speeding=${summary.speedingCount}/${summary.longestSpeedingSec}s zonePasses=${summary.schoolZonePasses} zoneMax=${summary.schoolZoneMaxKmh} review=${summary.ruleBasedReview}")
        val suggested = summary.suggestedSituation
        _ui.update {
            it.copy(review = ReviewUi(summary = summary, previous = previous, reviewStreaming = true,
                suggestedTitle = suggested?.let(::situationTitle), talk = it.coach.talk.copy(answerText = null)))
        }
        setMode(AppMode.REVIEW)
        setAmbient(AmbientMood.CALM)
        talkJob = scope.launch {
            val t0 = now()
            voice.sayStreaming(instructor.review(summary).onEach { line ->
                if (_ui.value.review?.reviewLines.isNullOrEmpty()) Log.i(TAG, "app review first sentence after=${now() - t0}ms")
                _ui.update { s -> s.copy(review = s.review?.let { r -> r.copy(reviewLines = r.reviewLines + line) }) }
            })
            _ui.update { s -> s.copy(review = s.review?.copy(reviewStreaming = false)) }
        }
    }

    // ---------- 훈련 ----------

    private fun trainingTick() {
        val state = _ui.value.vehicle
        val (view, events) = engine.tick(state, now())
        val prev = _ui.value.training
        if (prev != null && prev.situation == view.situation) {
            view.steps.filter { s -> s.done && prev.steps.firstOrNull { it.id == s.id }?.done == false }
                .forEach { Log.i(TAG, "app step done ${view.situation} ${it.id}") }
        }
        if (prev?.phase != view.phase || prev.situation != view.situation) {
            Log.i(TAG, "app training ${view.chapterId} ${view.situation} ${view.phase} steps=${view.steps.joinToString { "${it.id}:${if (it.done) 1 else 0}" }} result=${view.lastResult?.let { "${it.id}★${it.stars} ${it.reactionMs}ms miss=${it.missedStepIds} pen=${it.penalties}" }}")
        }
        _ui.update { it.copy(training = view) }
        dispatch(events)
        if (view.phase == TrainingPhase.CHAPTER_DONE && view.chapterResult != null && _ui.value.result == null) {
            progress = engine.progress()
            saveProgress()
            _ui.update { it.copy(result = view.chapterResult, progress = progress) }
            Log.i(TAG, "app chapter done ${view.chapterResult}")
        }
    }

    private fun startTraining(begin: (Long) -> Unit) {
        if (!_ui.value.vehicle.parked) return
        talkJob?.cancel()
        voice.stopSpeaking()
        begin(now())
        _ui.update { it.copy(result = null, training = null) }
        setMode(AppMode.TRAINING)
        startLoop(periodMs = 33) { trainingTick() }
    }

    // ---------- 미니게임 ----------

    private fun startMiniGame(kind: MiniGameKind) {
        if (!_ui.value.vehicle.parked) return
        val t = now()
        when (kind) {
            MiniGameKind.SAFE_DIAL -> safeDial.start(t)
            MiniGameKind.TURN_SIGNAL -> turnGame.start(t)
            MiniGameKind.WINDOW_BUZZER -> windowGame.start(t)
        }
        _ui.update { it.copy(miniGame = kind) }
        setMode(AppMode.MINIGAME)
        startLoop(periodMs = 33) {
            val s = _ui.value.vehicle
            val n = now()
            when (kind) {
                MiniGameKind.SAFE_DIAL -> safeDial.tick(s.steeringAngleDeg, n).let { (v, e) ->
                    val old = _ui.value.safeDial
                    if (old?.solved != v.solved || old.done != v.done) Log.i(TAG, "app safedial solved=${v.solved}/${v.targetsDeg.size} targets=${v.targetsDeg} deg=${v.currentDeg} done=${v.done} t=${v.elapsedMs}ms")
                    _ui.update { it.copy(safeDial = v) }; dispatch(e)
                }
                MiniGameKind.TURN_SIGNAL -> turnGame.tick(s.turnSignal, n).let { (v, e) -> _ui.update { it.copy(turnGame = v) }; dispatch(e) }
                MiniGameKind.WINDOW_BUZZER -> windowGame.tick(s.windowOpenPct, n).let { (v, e) -> _ui.update { it.copy(windowGame = v) }; dispatch(e) }
            }
        }
    }

    // ---------- 이벤트 → 음성·효과음·조명 ----------

    private fun dispatch(events: List<CoachEvent>) {
        for (e in events) when (e) {
            is CoachEvent.Say -> {
                Log.i(TAG, "app say p=${e.priority} '${e.text}'")
                // 2 = 상황이 터지는 첫 마디만 앞 말을 끊는다. 1·0은 순서대로(voice 계약)
                voice.say(e.text, urgent = e.priority >= 2)
                _ui.update { it.copy(home = it.home.copy(talk = it.home.talk.copy(answerText = e.text))) }
                e.expectReply?.let { kind ->
                    talkJob?.cancel()
                    talkJob = scope.launch {
                        val answer = listenOnce(kind) ?: return@launch
                        when (_ui.value.mode) {
                            AppMode.TRAINING -> engine.answer(answer, now())
                            AppMode.MINIGAME -> windowGame.answer(answer, now())
                            else -> Unit
                        }
                    }
                }
            }
            is CoachEvent.Sfx -> sfx.play(e.name, e.loop)
            is CoachEvent.StopSfx -> sfx.stop(e.name)
            is CoachEvent.Ambient -> setAmbient(e.mood)
        }
    }

    /** 앰비언트는 정차 중에만 바꾼다(주행 중 시선 분산, 명세 6.1). */
    private fun setAmbient(mood: AmbientMood, force: Boolean = false) {
        if (!force && !_ui.value.vehicle.parked) return
        if (mood == lastAmbient) return
        lastAmbient = mood
        scope.launch {
            val t0 = now()
            val ok = gateway.setAmbient(mood)
            Log.i(TAG, "app ambient $mood ok=$ok took=${now() - t0}ms")
        }
    }

    // ---------- 말하기·듣기 ----------

    private suspend fun listenOnce(kind: ReplyKind, pcm: ByteArray? = null): String? {
        updateTalk { it.copy(partialText = null) }
        // 대체 입력(녹음 PCM)일 때는 화면에 그렇게 보인다(시연에서 차량 마이크로 오해하지 않게)
        val tag = if (pcm != null) "[녹음 입력] " else ""
        val onPartial: (String) -> Unit = { partial -> updateTalk { it.copy(partialText = tag + partial) } }
        val pleos = voice as? PleosVoice
        val text = if (pcm != null && pleos != null) pleos.listenFromPcm(pcm, kind, onPartial) else voice.listen(kind, onPartial)
        Log.i(TAG, "app heard kind=$kind source=${if (pcm != null) "pcm" else "mic"} '${text ?: ""}'")
        updateTalk { it.copy(partialText = text?.let { tag + it }) }
        return text
    }

    /** @param pcm 실측·시연용: 마이크 대신 넣을 16kHz 16bit mono PCM(디버그 명령에서만). */
    fun askQuestion(pcm: ByteArray? = null) {
        talkJob?.cancel()
        voice.stopSpeaking()
        val parked = _ui.value.vehicle.parked
        val context = questionContext()
        talkJob = scope.launch {
            instructor.prepareQuestion(parked, context)
            val q = listenOnce(ReplyKind.FREE, pcm) ?: return@launch
            updateTalk { it.copy(thinking = true, answerText = null) }
            voice.sayStreaming(instructor.answer(q, parked, context).onEach { line ->
                updateTalk { it.copy(thinking = false, answerText = listOfNotNull(it.answerText, line).joinToString(" ")) }
                if (_ui.value.mode == AppMode.COACH) _ui.update { s -> s.copy(coach = s.coach.copy(lastLine = coachLine(line))) }
            })
            updateTalk { it.copy(thinking = false) }
        }
    }

    /** LLM에 넘기는 지금 상황 한 줄. 지식 파일 밖의 답을 막는 것은 voice 쪽이 한다. */
    private fun questionContext(): String {
        val s = _ui.value.vehicle
        return buildList {
            add(if (s.parked) "정차 중" else "주행 중")
            s.wiper?.let { add("와이퍼 $it") }
            s.lowBeamOn?.let { add(if (it) "전조등 켜짐" else "전조등 꺼짐") }
            s.defrostFrontOn?.let { if (it) add("앞유리 성에 제거 켜짐") }
            s.recirculationOn?.let { add(if (it) "내기순환" else "외기") }
            s.acOn?.let { add(if (it) "에어컨 켜짐" else "에어컨 꺼짐") }
            _ui.value.training?.situation?.let { add("훈련 중 상황 ${situationTitle(it)}") }
        }.joinToString(", ")
    }

    /** 홈·코치·리뷰가 같은 대화 상태를 보여 준다. */
    private fun updateTalk(f: (TalkUi) -> TalkUi) = _ui.update {
        it.copy(
            home = it.home.copy(talk = f(it.home.talk)),
            coach = it.coach.copy(talk = f(it.coach.talk)),
            review = it.review?.let { r -> r.copy(talk = f(r.talk)) },
        )
    }

    // ---------- 화면 동작 ----------

    fun onAction(a: UiAction) {
        Log.i(TAG, "app action $a")
        when (a) {
            is UiAction.StartChapter -> startTraining { engine.start(a.chapterId, it, a.from) }
            UiAction.StartSurprise -> startTraining { engine.startSurprise(it) }
            // 교관 대결: 엔진은 친구가 첫 상황을 던질 때(Inject) 시작한다. 그전까지 훈련 화면은 IDLE 뷰로 상황 고르는 판을 보여 준다.
            UiAction.StartVersus -> startTraining { }
            is UiAction.Inject -> engine.inject(a.situation, now())
            is UiAction.StartSituation -> startTraining { engine.inject(a.situation, it) }
            is UiAction.Answer -> when (_ui.value.mode) {
                AppMode.MINIGAME -> windowGame.answer(a.text, now())
                else -> engine.answer(a.text, now())
            }
            UiAction.AbortTraining -> {
                engine.abort(now())
                engine.tick(_ui.value.vehicle, now()).second.let { e -> dispatch(e.filterNot { it is CoachEvent.Say }) }
                sfx.stopAll()
                saveProgress()
                goHome()
            }
            UiAction.ShowResult -> if (_ui.value.result != null) {
                stopLoop()
                setMode(AppMode.RESULT)
            }
            UiAction.GoHome, UiAction.CloseMiniGame, UiAction.DismissReview -> goHome()
            UiAction.OpenSafeDial -> startMiniGame(MiniGameKind.SAFE_DIAL)
            UiAction.OpenTurnSignalGame -> startMiniGame(MiniGameKind.TURN_SIGNAL)
            UiAction.OpenWindowBuzzer -> startMiniGame(MiniGameKind.WINDOW_BUZZER)
            UiAction.AskQuestion -> askQuestion()
            UiAction.StopTalking -> {
                talkJob?.cancel()
                voice.stopSpeaking()
                updateTalk { it.copy(thinking = false, partialText = null) }
            }
            UiAction.ToggleDebugPanel -> _ui.update { it.copy(debugPanel = !it.debugPanel) }
            is UiAction.ChooseVehicle -> chooseVehicle(a.id)
        }
    }

    private fun goHome() {
        stopLoop()
        talkJob?.cancel()
        sfx.stopAll()
        if (_ui.value.mode == AppMode.TRAINING) voice.stopSpeaking()
        setAmbient(AmbientMood.CALM)
        _ui.update { it.copy(training = null, miniGame = null, review = null) }
        if (!_ui.value.vehicle.parked) return
        refreshHome()
        setMode(AppMode.HOME)
    }

    // ---------- 홈 ----------

    private fun refreshHome() {
        progress = engine.progress()
        val state = _ui.value.vehicle
        val key = state.vehicleModelKey
        val chapters = engine.chapters()
        val firstTime = progress.totalStarsEarned == 0
        val fromDrive = lastDrive?.suggestedSituation
        val recommended = when {
            fromDrive != null -> chapters.firstOrNull { fromDrive in it.situations && !it.locked }
            else -> chapters.firstOrNull { !it.locked && (progress.chapterBestStars[it.id] ?: 0) == 0 }
        } ?: chapters.firstOrNull { !it.locked }
        val reason = when {
            fromDrive != null -> "지난 주행에서 ${situationTitle(fromDrive)} 상황이 있었어요."
            firstTime -> "처음이라면 여기서 시작해요."
            else -> "아직 별이 없는 챕터예요."
        }
        // 내비에 경로가 있고 가는 길에 연습할 만한 것이 있으면 그것을 먼저 권한다.
        val onRoute = route?.let { RouteTraining.recommend(it, chapters, practicedSituations()) }
        if (onRoute != null) Log.i(TAG, "app route recommend chapter=${onRoute.chapterId} situation=${onRoute.situation} reason=${onRoute.reason}")
        _ui.update {
            it.copy(
                progress = progress,
                home = it.home.copy(
                    greeting = if (firstTime) "이 차는 처음이시네요." else "다시 왔네요. 반가워요.",
                    subGreeting = if (firstTime) "3분만 같이 해 볼까요?" else "오늘도 세워 둔 김에 한 판 해요.",
                    chapters = chapters,
                    progress = progress,
                    vehicleModelKey = key,
                    recommendedChapterId = onRoute?.chapterId ?: recommended?.id,
                    recommendReason = onRoute?.reason ?: recommended?.let { reason },
                    recommendedSituation = onRoute?.situation,
                    recommendLabel = if (onRoute != null) "가는 길 연습" else "오늘의 추천",
                    resumeChapterId = engine.resumableChapter(),
                    canTrain = state.parked,
                    miniGamesUnlocked = progress.chapterBestStars.values.any { s -> s > 0 },
                ),
            )
        }
    }

    private fun practicedSituations(): Set<SituationId> =
        progress.bestStars.filterValues { it > 0 }.keys.mapNotNull { runCatching { SituationId.valueOf(it) }.getOrNull() }.toSet()

    private fun saveProgress() {
        progress = engine.progress()
        store.saveProgress(progress)
        _ui.update { it.copy(progress = progress) }
    }

    // ---------- 루프 ----------

    private fun startLoop(periodMs: Long, body: () -> Unit) {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive) {
                runCatching(body).onFailure { Log.e(TAG, "app loop failed", it) }
                delay(periodMs)
            }
        }
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
    }
}

/**
 * 주행 화면의 한 줄은 한글 24자 이내(명세 9장). 넘치면 앞 문장(예: K09의 "연습했던 거예요.")부터 덜어 내고,
 * 그래도 길면 마지막 문장만 남긴다. 말은 원문 그대로 한다.
 */
fun coachLine(text: String, max: Int = 24): String {
    if (text.length <= max) return text
    val sentences = text.split(Regex("(?<=[.?!])\\s+")).filter { it.isNotBlank() }
    for (i in sentences.indices) {
        val rest = sentences.drop(i).joinToString(" ")
        if (rest.length <= max) return rest
    }
    return sentences.last()
}

/** 화면·로그에 쓰는 상황 이름. */
fun situationTitle(id: SituationId): String = when (id) {
    SituationId.HEAVY_RAIN -> "폭우"
    SituationId.FOGGED_WINDSHIELD -> "앞유리 김서림"
    SituationId.SUDDEN_STOP -> "앞차 급정거"
    SituationId.TUNNEL -> "터널"
    SituationId.DENSE_FOG -> "짙은 안개"
    SituationId.ONCOMING_GLARE -> "맞은편 눈부심"
    SituationId.BREAKDOWN -> "고속도로 고장"
    SituationId.WARNING_LIGHT -> "경고등"
    SituationId.DROWSY -> "졸음"
    SituationId.UNINTENDED_ACCEL -> "의도치 않은 가속"
    SituationId.BEFORE_EXIT -> "내리기 전"
    SituationId.WIPER_DEBRIS -> "앞유리 이물질"
    SituationId.RIGHT_TURN_CROSSWALK -> "우회전 횡단보도"
    SituationId.HIGHWAY_MERGE -> "고속도로 합류"
    SituationId.HYDROPLANING -> "수막현상"
    SituationId.HIPASS_WRONG_LANE -> "하이패스 차로 오진입"
    SituationId.ROUNDABOUT_ENTRY -> "회전교차로"
    SituationId.SCHOOL_ZONE -> "어린이 보호구역"
    SituationId.TOLL_BOOTH -> "일반 차로 요금소"
}
