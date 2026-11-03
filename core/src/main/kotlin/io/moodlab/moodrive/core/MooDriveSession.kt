package io.moodlab.moodrive.core

/** 세션이 앱(플레이어·조명·TTS·UI)에 내리는 지시. 앱은 이걸 받아 실제 SDK를 호출한다. */
sealed interface Action {
    data class Play(val track: Track, val scene: Scene) : Action
    data class SetAmbient(val command: AmbientCommand) : Action
    data class Speak(val text: String) : Action
    data class SceneChanged(val scene: Scene, val mood: Mood) : Action
    data class TripEnded(val log: List<TripEvent>) : Action
    /** 휴게소 경유 제안(확인 대기). 앱은 질문 UI를 띄우고, 음성 "응/아니"나 버튼으로 답을 받는다. */
    data object SuggestRestStop : Action
    /** 운전자가 수락 → 앱이 NaviHelper로 가장 가까운 휴게소를 경유지로 추가. */
    data object AddRestStop : Action
}

data class TripEvent(val timestampMs: Long, val scene: Scene, val trackId: String?)

/**
 * 컨텍스트 엔진 → 선곡 → 조명 → DJ 멘트를 묶는 진입점.
 * - [onSnapshot]: 차량 신호가 바뀔 때마다 호출
 * - [onTrackEnded]: 곡이 끝나거나 스킵됐을 때 호출 → 다음 곡
 * - [onFeedback]: 음성/버튼 피드백
 */
class MooDriveSession(
    catalog: List<Track>,
    val taste: TasteModel = TasteModel(),
    val engine: ContextEngine = ContextEngine(),
    val ambient: AmbientPlanner = AmbientPlanner(),
    /** 일반 멘트 최소 간격(차량 시간). 안전·출발·피날레·도착 멘트는 예외. */
    private val djMinIntervalMs: Long = 5 * 60_000,
    /** 장면이 바뀌면 지금 곡을 끊고 바로 바꿀지. false면 곡이 끝날 때 반영. */
    private val switchTrackOnSceneChange: Boolean = true,
) {
    private val curator = Curator(catalog, taste)
    private var lastResult: ContextResult? = null
    private var lastSnapshot: VehicleSnapshot? = null
    private var nowPlaying: Track? = null
    private var lastDjAtMs: Long? = null
    private val log = mutableListOf<TripEvent>()
    private var tripActive = false
    /** 음성 "더 신나게/차분하게"로 쌓이는 사용자 보정. 장면이 바뀌어도 유지(이번 주행 한정). */
    private var energyBias = 0f
    var djMuted = false
        private set
    /** 휴게소 제안에 대한 답을 기다리는 중 */
    var restStopPending = false
        private set
    /** 음성 "이 분위기 유지" → 이 시각(차량 시간)까지 장면이 바뀌어도 곡을 바꾸지 않음. 안전 장면은 예외. */
    private var holdUntilMs = 0L
    private var nowMs = 0L

    val currentScene: Scene? get() = lastResult?.scene
    val currentMood: Mood? get() = lastResult?.mood
    val currentTrack: Track? get() = nowPlaying

    fun onSnapshot(s: VehicleSnapshot): List<Action> {
        lastSnapshot = s
        nowMs = s.timestampMs
        val r = engine.update(s)
        lastResult = r
        val actions = mutableListOf<Action>()

        if (r.changed) {
            val prevScene = log.lastOrNull()?.scene
            actions += Action.SceneChanged(r.scene, r.mood)
            log += TripEvent(s.timestampMs, r.scene, nowPlaying?.id)
            val greeting = r.djLine == null && log.size == 1 && r.scene != Scene.PARKED
            val line = if (greeting) "출발할게요. ${r.scene.label} 분위기로 시작합니다." else r.djLine
            line?.let { text ->
                // 안전 멘트는 음소거·간격 제한 무시. 출발·도착·피날레는 간격 제한만 무시(연출 핵심 순간).
                val safety = r.scene == Scene.FATIGUE_BOOST || r.scene == Scene.HEAVY_RAIN
                val keyMoment = greeting || r.scene == Scene.PARKED || r.scene == Scene.ARRIVAL_FINALE
                val last = lastDjAtMs
                val intervalOk = last == null || s.timestampMs - last >= djMinIntervalMs
                if (safety || (!djMuted && (keyMoment || intervalOk))) {
                    actions += Action.Speak(text)
                    if (!keyMoment) lastDjAtMs = s.timestampMs
                }
            }
            if (r.scene == Scene.FATIGUE_BOOST && !restStopPending) {
                restStopPending = true
                actions += Action.SuggestRestStop
            }
            if (r.scene == Scene.PARKED) {
                if (tripActive) actions += Action.TripEnded(log.toList())
                tripActive = false
                energyBias = 0f
            } else {
                tripActive = true
                // 터널은 잠깐이라 곡은 그대로 두고 조명만 바꾼다(들어갈 때·나올 때 두 번 끊기는 것 방지)
                val brief = r.scene == Scene.TUNNEL || prevScene == Scene.TUNNEL
                val held = s.timestampMs < holdUntilMs && r.scene != Scene.FATIGUE_BOOST && r.scene != Scene.HEAVY_RAIN
                if (nowPlaying == null || (switchTrackOnSceneChange && !brief && !held)) actions += play(r)
            }
        }
        ambient.plan(targetMood(r), s)?.let { actions += Action.SetAmbient(it) }
        return actions
    }

    /**
     * @param completed 끝까지 들었는지
     * @param playedSec 스킵 시 들은 시간. 30초 안에 넘기면 감점, 그 뒤 스킵은 중립.
     */
    fun onTrackEnded(completed: Boolean, playedSec: Int = 0): List<Action> {
        val r = lastResult ?: return emptyList()
        nowPlaying?.let {
            when {
                completed -> taste.record(r.scene, it.genre, Feedback.COMPLETED)
                playedSec < EARLY_SKIP_SEC -> taste.record(r.scene, it.genre, Feedback.SKIPPED_EARLY)
            }
        }
        if (r.scene == Scene.PARKED) {
            nowPlaying = null
            return emptyList()
        }
        return listOf(play(r))
    }

    /** "좋아" / "이거 별로야". 별로면 바로 다음 곡. */
    fun onFeedback(feedback: Feedback): List<Action> {
        val r = lastResult ?: return emptyList()
        val track = nowPlaying ?: return emptyList()
        taste.record(r.scene, track.genre, feedback)
        return if (feedback == Feedback.DISLIKE) listOf(play(r)) else emptyList()
    }

    /** STT 텍스트 처리. 인식 못 하면 빈 목록. */
    fun onVoice(text: String): List<Action> {
        val r = lastResult ?: return emptyList()
        val intent = VoiceCommandParser.parse(text)
        if (restStopPending) when (intent) {
            VoiceIntent.YES, VoiceIntent.LIKE, VoiceIntent.REST_STOP -> return answerRestStop(true)
            VoiceIntent.NO, VoiceIntent.DISLIKE -> return answerRestStop(false)
            else -> Unit
        }
        return when (intent) {
            VoiceIntent.LIKE -> onFeedback(Feedback.LIKE) + Action.Speak("좋아요, 기억해 둘게요.")
            VoiceIntent.DISLIKE -> onFeedback(Feedback.DISLIKE)
            VoiceIntent.SKIP -> onTrackEnded(completed = false)
            VoiceIntent.MORE_ENERGY -> {
                energyBias = (energyBias + 0.3f).coerceAtMost(0.6f)
                listOf(Action.Speak("조금 더 신나게 갈게요."), play(r))
            }
            VoiceIntent.CALMER -> {
                energyBias = (energyBias - 0.3f).coerceAtLeast(-0.6f)
                listOf(Action.Speak("조금 차분하게 갈게요."), play(r))
            }
            VoiceIntent.REST_STOP -> answerRestStop(true)
            VoiceIntent.HOLD -> {
                holdUntilMs = nowMs + HOLD_MS
                listOf(Action.Speak("20분 동안 이 분위기 그대로 갈게요."))
            }
            VoiceIntent.YES, VoiceIntent.NO -> emptyList()
            VoiceIntent.MUTE_DJ -> { djMuted = true; listOf(Action.Speak("알겠어요. 안전 안내만 할게요.")) }
            VoiceIntent.UNMUTE_DJ -> { djMuted = false; listOf(Action.Speak("다시 이야기할게요.")) }
            VoiceIntent.WHAT_SONG -> nowPlaying?.let { listOf(Action.Speak("${it.artist}의 ${it.title}이에요.")) } ?: emptyList()
            VoiceIntent.UNKNOWN -> emptyList()
        }
    }

    /** 휴게소 제안에 답함 (음성 또는 화면 버튼). */
    fun answerRestStop(accept: Boolean): List<Action> {
        restStopPending = false
        return if (accept) listOf(Action.Speak("가까운 휴게소를 경유지로 넣을게요."), Action.AddRestStop)
        else listOf(Action.Speak("알겠어요. 졸리면 언제든 말씀하세요."))
    }

    /** 도착 피날레 큐 (남은 시간에 맞춰 곡 순서 제안). ETA를 모르면 null. */
    fun planFinaleQueue(): FinalePlanner.Plan? {
        val s = lastSnapshot ?: return null
        val r = lastResult ?: return null
        val eta = s.etaMinutes ?: return null
        val finaleMood = engine.moodFor(Scene.ARRIVAL_FINALE, s)
        val finale = curator.rank(finaleMood, Scene.ARRIVAL_FINALE).first()
        return FinalePlanner.plan(curator.rank(r.mood, r.scene, exclude = setOf(finale.id)), eta * 60, finale)
    }

    /** 장면 무드 + 사용자 보정. */
    fun targetMood(r: ContextResult = lastResult!!): Mood = r.mood.copy(energy = r.mood.energy + energyBias).clamped()

    private fun play(r: ContextResult): Action.Play {
        val t = curator.next(targetMood(r), r.scene)
        nowPlaying = t
        return Action.Play(t, r.scene)
    }

    private companion object {
        const val EARLY_SKIP_SEC = 30
        const val HOLD_MS = 20 * 60_000L
    }
}
