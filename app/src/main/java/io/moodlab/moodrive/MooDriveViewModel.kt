package io.moodlab.moodrive

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.moodlab.moodrive.core.Action
import io.moodlab.moodrive.core.AmbientCommand
import io.moodlab.moodrive.core.AmbientPlanner
import io.moodlab.moodrive.core.Feedback
import io.moodlab.moodrive.core.Genres
import io.moodlab.moodrive.core.Mood
import io.moodlab.moodrive.core.MooDriveSession
import io.moodlab.moodrive.core.SampleCatalog
import io.moodlab.moodrive.core.Scenario
import io.moodlab.moodrive.core.Scene
import io.moodlab.moodrive.core.Track
import io.moodlab.moodrive.core.TripEvent
import io.moodlab.moodrive.core.VehicleSnapshot
import io.moodlab.moodrive.vehicle.ManualSignalSource
import io.moodlab.moodrive.vehicle.PleosAmbientController
import io.moodlab.moodrive.vehicle.scenarioFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class PlayedTrack(val track: Track, val scene: Scene, val color: AmbientCommand)
data class Moment(val clock: String, val text: String)
data class Segment(val scene: Scene, val color: AmbientCommand, val weight: Float)

data class TripCard(
    val startClock: String,
    val endClock: String,
    val minutes: Int,
    val sceneChanges: Int,
    /** 장면별 구간(타임라인 막대, 머문 시간 비례) */
    val segments: List<Segment>,
    val played: List<PlayedTrack>,
    val moments: List<Moment>,
)

data class UiState(
    val onboarded: Boolean = false,
    val snapshot: VehicleSnapshot? = null,
    val scene: Scene? = null,
    val mood: Mood? = null,
    val track: Track? = null,
    val positionSec: Int = 0,
    /** 앱 화면 글로우용 목표 조명 (항상 표시) */
    val screenAmbient: AmbientCommand? = null,
    /** 실제 차량에 보낸 마지막 조명 */
    val carAmbient: AmbientCommand? = null,
    val allowAmbientWhileDriving: Boolean = false,
    val djLine: String? = null,
    val djAtMs: Long = 0,
    val listening: Boolean = false,
    val restStopPrompt: Boolean = false,
    val demoPanel: Boolean = false,
    val scenarioCaption: String? = null,
    val scenarioT: Int? = null,
    val tripCard: TripCard? = null,
) {
    val driving: Boolean get() = snapshot?.isDriving == true
    val ambientHeld: Boolean get() = driving && !allowAmbientWhileDriving
}

class MooDriveViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("moodrive", Context.MODE_PRIVATE)
    private val session = MooDriveSession(SampleCatalog.tracks)
    private val screenPlanner = AmbientPlanner(allowWhileDriving = true)
    private val pleosAmbient = PleosAmbientController(app)
    val manual = ManualSignalSource()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var sourceJob: Job? = null
    private var tts: TextToSpeech? = null
    private var timeScale = 1
    private val played = mutableListOf<PlayedTrack>()
    private val moments = mutableListOf<Moment>()
    private var tripStartClock: String? = null

    init {
        val saved = prefs.getStringSet(KEY_GENRES, null)
        if (saved != null && saved.size == Genres.PICK_COUNT) {
            session.taste.setPreferredGenres(saved)
            _ui.update { it.copy(onboarded = true) }
        }
        // TODO: Pleos TTS SDK(HYBRID)로 교체. 그전까진 안드로이드 기본 TTS
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.KOREAN
        }
        startPlaybackTicker()
        useManual()
    }

    // ── 온보딩 ──
    fun finishOnboarding(genreKeys: Set<String>) {
        require(genreKeys.size == Genres.PICK_COUNT)
        prefs.edit().putStringSet(KEY_GENRES, genreKeys).apply()
        session.taste.setPreferredGenres(genreKeys)
        _ui.update { it.copy(onboarded = true) }
    }

    // ── 신호 소스 ──
    fun useManual() {
        sourceJob?.cancel()
        resetTrip()
        timeScale = 1
        _ui.update { it.copy(scenarioCaption = null, scenarioT = null) }
        sourceJob = viewModelScope.launch { manual.snapshots.collect(::onSnapshot) }
    }

    fun playScenario() {
        sourceJob?.cancel()
        resetTrip()
        val sc = Scenario.rainyCommute(startMs = System.currentTimeMillis())
        timeScale = sc.timeScale
        _ui.update { it.copy(demoPanel = false, tripCard = null) }
        sourceJob = viewModelScope.launch {
            scenarioFlow(sc) { caption, t -> _ui.update { it.copy(scenarioCaption = caption, scenarioT = t) } }
                .collect(::onSnapshot)
        }
    }

    private fun resetTrip() {
        session.engine.reset()
        played.clear(); moments.clear(); tripStartClock = null
    }

    fun setAllowAmbientWhileDriving(allow: Boolean) {
        session.ambient.allowWhileDriving = allow
        _ui.update { it.copy(allowAmbientWhileDriving = allow) }
    }

    fun toggleDemoPanel() = _ui.update { it.copy(demoPanel = !it.demoPanel) }

    // ── 사용자 입력 ──
    fun like() = handle(session.onFeedback(Feedback.LIKE))
    fun dislike() = handle(session.onFeedback(Feedback.DISLIKE))
    fun onVoiceText(text: String) = handle(session.onVoice(text))
    fun skip() = handle(session.onTrackEnded(completed = false, playedSec = _ui.value.positionSec))
    fun setListening(on: Boolean) = _ui.update { it.copy(listening = on) }
    fun dismissTripCard() = _ui.update { it.copy(tripCard = null) }
    fun answerRestStop(accept: Boolean) = handle(session.answerRestStop(accept))

    private fun onSnapshot(s: VehicleSnapshot) {
        if (tripStartClock == null && s.isDriving) tripStartClock = clock(s)
        _ui.update { it.copy(snapshot = s) }
        handle(session.onSnapshot(s))
        // 음성으로 답했거나 제안이 끝났으면 질문 UI를 닫는다
        if (!session.restStopPending) _ui.update { it.copy(restStopPrompt = false) }
        val target = session.currentMood?.let { session.targetMood() }
        _ui.update {
            it.copy(
                scene = session.currentScene,
                mood = target,
                screenAmbient = target?.let { m -> screenPlanner.commandFor(m, night = !s.isDaytime()) },
            )
        }
    }

    private fun handle(actions: List<Action>) {
        if (!session.restStopPending && actions.isNotEmpty()) _ui.update { it.copy(restStopPrompt = false) }
        for (a in actions) when (a) {
            is Action.Play -> {
                _ui.update { it.copy(track = a.track, positionSec = 0) }
                val color = screenPlanner.commandFor(session.targetMood(), night = _ui.value.snapshot?.isDaytime() == false)
                if (played.lastOrNull()?.track?.id != a.track.id) played += PlayedTrack(a.track, a.scene, color)
            }
            is Action.SetAmbient -> {
                pleosAmbient.apply(a.command)
                _ui.update { it.copy(carAmbient = a.command) }
            }
            is Action.Speak -> {
                speak(a.text)
                val s = _ui.value.snapshot
                if (s != null && session.currentScene != Scene.PARKED) moments += Moment(clock(s), a.text.substringBefore(". ").removeSuffix("."))
            }
            is Action.SceneChanged -> Unit
            Action.SuggestRestStop -> _ui.update { it.copy(restStopPrompt = true) }
            Action.AddRestStop -> {
                // TODO: NaviHelper로 근처 휴게소 검색 → RequestWaypointInfo로 경유지 추가
            }
            is Action.TripEnded -> _ui.update { it.copy(tripCard = buildCard(a.log)) }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dj")
        val at = System.currentTimeMillis()
        _ui.update { it.copy(djLine = text, djAtMs = at) }
        viewModelScope.launch {
            delay(DJ_CAPTION_MS)
            _ui.update { if (it.djAtMs == at) it.copy(djLine = null) else it }
        }
    }

    private fun buildCard(log: List<TripEvent>): TripCard {
        val s = _ui.value.snapshot
        val driving = log.filter { it.scene != Scene.PARKED }
        val first = driving.firstOrNull()?.timestampMs ?: 0L
        val last = log.lastOrNull()?.timestampMs ?: first
        val night = s?.isDaytime() == false
        val segments = if (s == null) emptyList() else driving.map { e ->
            val end = log.getOrNull(log.indexOf(e) + 1)?.timestampMs ?: last
            Segment(e.scene, screenPlanner.commandFor(session.engine.moodFor(e.scene, s), night), (end - e.timestampMs).coerceAtLeast(1L).toFloat())
        }
        return TripCard(
            startClock = tripStartClock ?: "--:--",
            endClock = s?.let(::clock) ?: "--:--",
            minutes = ((last - first) / 60_000L).toInt(),
            sceneChanges = (driving.size - 1).coerceAtLeast(0),
            segments = segments,
            played = played.toList(),
            moments = moments.drop(1).take(6), // 첫 멘트(출발 인사)는 제외
        )
    }

    /**
     * 음원 번들 전까지는 시뮬레이션 재생: 1초마다 위치를 올리고, 곡 길이에 닿으면 다음 곡.
     * 시나리오 재생 중에는 차량 시간 배율(timeScale)만큼 빠르게 진행.
     * TODO: Media3 ExoPlayer로 교체 (assets 음원), 오디오 포커스·TTS 덕킹 처리
     */
    private fun startPlaybackTicker() = viewModelScope.launch {
        while (isActive) {
            delay(1_000)
            val st = _ui.value
            val t = st.track ?: continue
            if (st.scene == Scene.PARKED) continue
            if (st.positionSec + timeScale >= t.durationSec) {
                handle(session.onTrackEnded(completed = true, playedSec = st.positionSec))
            } else {
                _ui.update { it.copy(positionSec = it.positionSec + timeScale) }
            }
        }
    }

    private fun clock(s: VehicleSnapshot) = "%02d:%02d".format(s.minuteOfDay / 60, s.minuteOfDay % 60)

    override fun onCleared() {
        tts?.shutdown()
        super.onCleared()
    }

    private companion object {
        const val KEY_GENRES = "preferred_genres"
        const val DJ_CAPTION_MS = 12_000L
    }
}
