package io.moodlab.moodrive.core

/** 세션이 앱(플레이어·조명·TTS·UI)에 내리는 지시. 앱은 이걸 받아 실제 SDK를 호출한다. */
sealed interface Action {
    data class Play(val track: Track, val scene: Scene) : Action
    data class SetAmbient(val command: AmbientCommand) : Action
    data class Speak(val text: String) : Action
    data class SceneChanged(val scene: Scene, val mood: Mood) : Action
    data class TripEnded(val log: List<TripEvent>) : Action
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
    private val djMinIntervalMs: Long = 10 * 60_000,
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

    val currentScene: Scene? get() = lastResult?.scene
    val currentMood: Mood? get() = lastResult?.mood
    val currentTrack: Track? get() = nowPlaying

    fun onSnapshot(s: VehicleSnapshot): List<Action> {
        lastSnapshot = s
        val r = engine.update(s)
        lastResult = r
        val actions = mutableListOf<Action>()

        if (r.changed) {
            actions += Action.SceneChanged(r.scene, r.mood)
            log += TripEvent(s.timestampMs, r.scene, nowPlaying?.id)
            r.djLine?.let { line ->
                val safety = r.scene == Scene.FATIGUE_BOOST || r.scene == Scene.HEAVY_RAIN
                val last = lastDjAtMs
                if (safety || last == null || s.timestampMs - last >= djMinIntervalMs || r.scene == Scene.PARKED) {
                    actions += Action.Speak(line)
                    lastDjAtMs = s.timestampMs
                }
            }
            if (r.scene == Scene.PARKED) {
                if (tripActive) actions += Action.TripEnded(log.toList())
                tripActive = false
            } else {
                tripActive = true
                if (nowPlaying == null || switchTrackOnSceneChange) actions += play(r)
            }
        }
        ambient.plan(r.mood, s)?.let { actions += Action.SetAmbient(it) }
        return actions
    }

    fun onTrackEnded(completed: Boolean): List<Action> {
        val r = lastResult ?: return emptyList()
        nowPlaying?.let { taste.record(r.scene, it.genre, if (completed) Feedback.COMPLETED else Feedback.SKIPPED_EARLY) }
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

    /** 도착 피날레 큐 (남은 시간에 맞춰 곡 순서 제안). ETA를 모르면 null. */
    fun planFinaleQueue(): FinalePlanner.Plan? {
        val s = lastSnapshot ?: return null
        val r = lastResult ?: return null
        val eta = s.etaMinutes ?: return null
        val finaleMood = engine.moodFor(Scene.ARRIVAL_FINALE, s)
        val finale = curator.rank(finaleMood, Scene.ARRIVAL_FINALE).first()
        return FinalePlanner.plan(curator.rank(r.mood, r.scene, exclude = setOf(finale.id)), eta * 60, finale)
    }

    private fun play(r: ContextResult): Action.Play {
        val t = curator.next(r.mood, r.scene)
        nowPlaying = t
        return Action.Play(t, r.scene)
    }
}
