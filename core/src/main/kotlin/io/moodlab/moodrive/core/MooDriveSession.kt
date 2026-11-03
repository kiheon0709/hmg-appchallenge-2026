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
    private var heldMood: Mood? = null
    private var nowMs = 0L
    private var trackStartMs = 0L
    /** 연속 운전 시간 계산 (차량 시간). 10분 넘게 정차하면 초기화. */
    private var drivingSinceMs: Long? = null
    private var parkedSinceMs: Long? = null
    /** 마지막 휴게소 제안 시각. 거절해도 30분 뒤 다시 제안. */
    private var lastRestOfferMs: Long? = null
    /** 이번 주행에서 반응이 좋았던 장르 (피날레 선곡에 사용) */
    private val tripGenre = mutableMapOf<String, Float>()
    /** 마지막 주행 중 무드 → 정차 시 실내 조명을 '그날의 마지막 무드'로 */
    private var lastDrivingMood: Mood? = null

    val currentScene: Scene? get() = lastResult?.scene
    val currentMood: Mood? get() = lastResult?.mood
    val currentTrack: Track? get() = nowPlaying
    val fatigue: Boolean get() = lastResult?.fatigue == true
    val continuousDrivingMinutes: Int get() = drivingSinceMs?.let { ((nowMs - it) / 60_000L).toInt() } ?: 0

    private var lastRaw: VehicleSnapshot? = null
    private var restOfferedAtMs = 0L

    /**
     * 시간만 흐를 때 호출 (앱이 1초마다). 차량 SDK는 값이 바뀔 때만 콜백을 주므로,
     * 마지막 스냅샷을 현재 시각으로 다시 넣어 15초 유지 판정·연속 운전 시간·질문 시간 초과가 진행되게 한다.
     */
    fun tick(nowMs: Long): List<Action> {
        val s = lastRaw ?: return emptyList()
        if (nowMs <= s.timestampMs) return emptyList()
        // 마지막 실제 신호 기준으로 흐른 만큼 시계·ETA도 진행 (일몰·도착 판정이 멈추지 않게)
        val elapsedMin = ((nowMs - s.timestampMs) / 60_000L).toInt()
        return process(
            s.copy(
                timestampMs = nowMs,
                minuteOfDay = (s.minuteOfDay + elapsedMin) % 1440,
                // 남은 시간은 달리는 동안만 줄어든다 (주유소에 서 있다고 도착이 가까워지지 않음)
                etaMinutes = if (s.isDriving) s.etaMinutes?.let { (it - elapsedMin).coerceAtLeast(0) } else s.etaMinutes,
            ),
        )
    }

    fun onSnapshot(raw: VehicleSnapshot): List<Action> {
        lastRaw = raw
        return process(raw)
    }

    private fun process(raw: VehicleSnapshot): List<Action> {
        nowMs = raw.timestampMs
        // 휴게소 질문에 1분 동안 답이 없으면 거절로 보고 닫는다(30분 뒤 재제안)
        if (restStopPending && raw.timestampMs - restOfferedAtMs >= REST_ANSWER_TIMEOUT_MS) restStopPending = false
        updateDrivingTimer(raw)
        // 어댑터가 주는 값과 세션이 잰 값 중 큰 쪽 (어댑터가 모르면 0)
        val s = raw.copy(drivingMinutes = maxOf(raw.drivingMinutes, continuousDrivingMinutes))
        lastSnapshot = s
        val r = engine.update(s)
        lastResult = r
        val actions = mutableListOf<Action>()

        if (r.changed) {
            // 정차 → 주행이면 새 주행: 이전 주행 기록·보정·질문 상태를 비운다 (데모 재생 반복·하루 여러 번 운전)
            if (r.scene != Scene.PARKED && !tripActive) startTrip()
            val prevScene = log.lastOrNull()?.scene
            actions += Action.SceneChanged(r.scene, r.mood)
            log += TripEvent(s.timestampMs, r.scene, nowPlaying?.id)
            // 주행의 첫 장면은 항상 출발 인사 (두 번째 주행이 "날이 개었네요"로 시작하지 않게)
            val greeting = log.size == 1 && r.scene != Scene.PARKED
            val line = if (greeting) "출발할게요. ${r.scene.label} 분위기로 시작합니다." else r.djLine
            line?.let { text ->
                // 안전 멘트는 음소거·간격 제한 무시. 출발·도착·피날레는 간격 제한만 무시(연출 핵심 순간).
                val safety = r.scene == Scene.HEAVY_RAIN
                val keyMoment = greeting || r.scene == Scene.PARKED || r.scene == Scene.ARRIVAL_FINALE
                // 분위기 유지 중엔 무드가 안 바뀌므로 피날레 안내도 하지 않음
                val heldFinale = r.scene == Scene.ARRIVAL_FINALE && isHeld()
                val last = lastDjAtMs
                val intervalOk = last == null || s.timestampMs - last >= djMinIntervalMs
                if (safety || (!djMuted && !heldFinale && (keyMoment || intervalOk))) {
                    actions += Action.Speak(text)
                    if (!keyMoment) lastDjAtMs = s.timestampMs
                }
            }
            if (r.scene == Scene.PARKED) {
                if (tripActive) actions += Action.TripEnded(log.toList())
                tripActive = false
                energyBias = 0f
            } else {
                tripActive = true
                // 분위기 유지 중이어도 안전 장면이거나, 지금 곡이 새 장면의 템포 상한을 넘으면 바꾼다
                val overCap = Curator.bpmCap(r.scene)?.let { cap -> (nowPlaying?.bpm ?: 0) > cap } == true
                // 터널은 잠깐이라 곡을 유지(들어갈 때·나올 때 두 번 끊기는 것 방지).
                // 단 템포 상한을 넘는 곡이거나, 터널을 나와 비·폭우·정체로 바뀌면 바꾼다
                val calmNeeded = r.scene == Scene.RAIN || r.scene == Scene.HEAVY_RAIN || r.scene == Scene.TRAFFIC_JAM
                val brief = !overCap && (r.scene == Scene.TUNNEL || (prevScene == Scene.TUNNEL && !calmNeeded))
                val held = isHeld() && r.scene != Scene.HEAVY_RAIN && !overCap
                if (nowPlaying == null || (switchTrackOnSceneChange && !brief && !held)) actions += play(r)
            }
        }
        actions += fatigueCheck(r, s)
        if (r.scene != Scene.PARKED) lastDrivingMood = targetMood(r)
        // 정차하면 실내 조명은 그날 마지막 주행 무드(대개 피날레)로
        val ambientMood = if (r.scene == Scene.PARKED) lastDrivingMood ?: targetMood(r) else targetMood(r)
        ambient.plan(ambientMood, s)?.let { actions += Action.SetAmbient(it) }
        return actions
    }

    /** 새 주행 시작. 엔진 판정 상태는 유지(이미 이번 스냅샷을 판정함), 주행 단위 상태만 초기화. */
    fun startTrip() {
        log.clear()
        tripGenre.clear()
        energyBias = 0f
        holdUntilMs = 0L
        heldMood = null
        restStopPending = false
        // lastRestOfferMs는 유지: 잠깐 쉬었다 출발해도 30분 재제안 간격을 지킨다 (10분 이상 정차하면 별도로 초기화)
        lastDjAtMs = null
        lastDrivingMood = null
        tripActive = false
    }

    private fun updateDrivingTimer(s: VehicleSnapshot) {
        if (s.isDriving) {
            parkedSinceMs = null
            if (drivingSinceMs == null) drivingSinceMs = s.timestampMs
        } else {
            val since = parkedSinceMs ?: s.timestampMs.also { parkedSinceMs = it }
            if (s.timestampMs - since >= REST_RESET_MS) {
                drivingSinceMs = null
                lastRestOfferMs = null
            }
        }
    }

    /** 장시간 운전 경보: 장면과 별개. 처음 넘을 때, 그리고 거절 후 30분마다 다시 제안. 안전 멘트라 음소거 무시. */
    private fun fatigueCheck(r: ContextResult, s: VehicleSnapshot): List<Action> {
        if (!r.fatigue || restStopPending) return emptyList()
        val last = lastRestOfferMs
        if (last != null && s.timestampMs - last < REST_REOFFER_MS) return emptyList()
        lastRestOfferMs = s.timestampMs
        restOfferedAtMs = s.timestampMs
        restStopPending = true
        val h = s.drivingMinutes / 60
        val m = s.drivingMinutes % 60
        val dur = if (h > 0 && m > 0) "${h}시간 ${m}분" else if (h > 0) "${h}시간" else "${m}분"
        lastDjAtMs = s.timestampMs
        return listOf(Action.Speak("${dur}째 운전 중이에요. 휴게소에 들를까요?"), Action.SuggestRestStop)
    }

    private fun isHeld() = nowMs < holdUntilMs

    /**
     * @param completed 끝까지 들었는지
     * @param playedSec 스킵 시 들은 시간. 30초 안에 넘기면 감점, 그 뒤 스킵은 중립.
     */
    fun onTrackEnded(completed: Boolean, playedSec: Int? = null): List<Action> {
        val r = lastResult ?: return emptyList()
        // 들은 시간을 모르면 세션이 잰 차량 시간으로 계산
        val played = playedSec ?: ((nowMs - trackStartMs) / 1000L).toInt()
        nowPlaying?.let {
            when {
                completed -> record(r.scene, it, Feedback.COMPLETED)
                played < EARLY_SKIP_SEC -> record(r.scene, it, Feedback.SKIPPED_EARLY)
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
        record(r.scene, track, feedback)
        return if (feedback == Feedback.DISLIKE) listOf(play(r)) else emptyList()
    }

    /** 특정 곡에 대한 피드백 기록 (테스트·드라이브 카드에서 지난 곡 평가용). */
    internal fun onFeedbackFor(track: Track, feedback: Feedback) {
        record(lastResult?.scene ?: Scene.CLEAR_DAY, track, feedback)
    }

    private fun record(scene: Scene, track: Track, feedback: Feedback) {
        taste.record(scene, track.genre, feedback)
        tripGenre[track.genre] = (tripGenre[track.genre] ?: 0f) + feedback.weight
    }

    /** STT 텍스트 처리. 인식 못 하면 빈 목록. */
    fun onVoice(text: String, playedSec: Int? = null): List<Action> {
        val r = lastResult ?: return emptyList()
        val intent = VoiceCommandParser.parse(text)
        // 휴게소 질문 중: "응/네/휴게소"만 수락. "이 노래 좋아"는 곡 평가로 처리
        // 휴게소 질문 중: "응/네/좋아/휴게소/졸려"는 수락, "아니/괜찮아/안 졸려"는 거절.
        // 단 "이 노래 좋아"처럼 곡을 가리키면 곡 평가로 처리
        if (restStopPending) {
            val aboutSong = Regex("노래|곡|이거").containsMatchIn(text)
            when {
                intent == VoiceIntent.NO -> return answerRestStop(false)
                // "싫어"처럼 곡을 가리키지 않는 거절은 휴게소 거절로 (곡을 넘기지 않음)
                intent == VoiceIntent.DISLIKE && !aboutSong -> return answerRestStop(false)
                intent == VoiceIntent.YES || intent == VoiceIntent.REST_STOP || intent == VoiceIntent.REST_ASK || intent == VoiceIntent.DROWSY -> return answerRestStop(true)
                intent == VoiceIntent.LIKE && !aboutSong -> return answerRestStop(true)
            }
        }
        return when (intent) {
            VoiceIntent.LIKE -> onFeedback(Feedback.LIKE) + Action.Speak("좋아요, 기억해 둘게요.")
            VoiceIntent.DISLIKE -> onFeedback(Feedback.DISLIKE)
            VoiceIntent.SKIP -> onTrackEnded(completed = false, playedSec = playedSec)
            VoiceIntent.MORE_ENERGY -> {
                energyBias = (energyBias + 0.3f).coerceAtMost(0.6f)
                listOf(Action.Speak("조금 더 신나게 갈게요."), play(r))
            }
            VoiceIntent.CALMER -> {
                energyBias = (energyBias - 0.3f).coerceAtLeast(-0.6f)
                listOf(Action.Speak("조금 차분하게 갈게요."), play(r))
            }
            VoiceIntent.REST_STOP -> answerRestStop(true)
            VoiceIntent.REST_ASK -> {
                restStopPending = true
                restOfferedAtMs = nowMs
                lastRestOfferMs = nowMs
                listOf(Action.Speak("가까운 휴게소에 들를까요?"), Action.SuggestRestStop)
            }
            VoiceIntent.DROWSY -> {
                // 졸림 호소엔 바로 경유지를 넣지 않고 먼저 묻는다
                restStopPending = true
                restOfferedAtMs = nowMs
                lastRestOfferMs = nowMs
                listOf(Action.Speak("졸리시면 위험해요. 가까운 휴게소에 들를까요?"), Action.SuggestRestStop)
            }
            VoiceIntent.HOLD -> {
                holdUntilMs = nowMs + HOLD_MS
                heldMood = targetMood(r)
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
        return if (accept) listOf(Action.Speak("알겠어요. 가까운 휴게소를 찾아볼게요."), Action.AddRestStop)
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

    /**
     * 목표 무드 = 장면 무드 + 사용자 보정(더 신나게/차분하게)
     * + 장시간 운전이면 조금 더 밝고 경쾌하게(비·정체·터널에선 그대로, 템포 상한은 장면 기준 유지)
     * 분위기 유지 중이면 유지 시작 때의 무드(폭우는 예외).
     */
    fun targetMood(r: ContextResult = lastResult!!): Mood {
        heldMood?.let { if (isHeld() && r.scene != Scene.HEAVY_RAIN) return it }
        val calmScene = r.scene in setOf(Scene.RAIN, Scene.HEAVY_RAIN, Scene.TRAFFIC_JAM, Scene.TUNNEL, Scene.PARKED)
        val boost = if (r.fatigue && !calmScene) 0.2f else 0f
        return Mood(r.mood.energy + energyBias + boost, r.mood.valence + boost).clamped()
    }

    private fun play(r: ContextResult): Action.Play {
        // 피날레는 이번 주행에서 반응이 좋았던 장르에 가산점
        // 그리고 남은 시간을 덮는 길이의 곡을 우선 (도착 전에 끝나 다른 곡이 끼어드는 일을 줄임)
        val remainSec = (lastSnapshot?.etaMinutes ?: 0) * 60
        val bonus: (Track) -> Float = if (r.scene == Scene.ARRIVAL_FINALE) {
            { t -> (tripGenre[t.genre] ?: 0f).coerceIn(-2f, 3f) * 0.1f + if (t.durationSec >= remainSec) 0.15f else 0f }
        } else {
            { 0f }
        }
        val t = curator.next(targetMood(r), r.scene, bonus)
        nowPlaying = t
        trackStartMs = nowMs
        return Action.Play(t, r.scene)
    }

    private companion object {
        const val EARLY_SKIP_SEC = 30
        const val HOLD_MS = 20 * 60_000L
        const val REST_REOFFER_MS = 30 * 60_000L
        const val REST_RESET_MS = 10 * 60_000L
        const val REST_ANSWER_TIMEOUT_MS = 60_000L
    }
}
