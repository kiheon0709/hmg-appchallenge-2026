package io.moodlab.moodrive

import android.app.Application
import android.content.Context
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
import io.moodlab.moodrive.audio.DjVoice
import io.moodlab.moodrive.audio.MusicPlayer
import io.moodlab.moodrive.vehicle.PleosAmbientController
import io.moodlab.moodrive.vehicle.PleosVehicleSignalSource
import io.moodlab.moodrive.vehicle.scenarioFlow
import kotlinx.coroutines.Job
import ai.pleos.playground.vehicle.Vehicle
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    /** 차량이 돌려준 조명 색(ARGB). 에뮬레이터에서 실제 API 동작을 보여 주는 값. */
    val carAmbientReported: Int? = null,
    val vehicleConnected: Boolean = false,
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
    val ambientHeld: Boolean get() = snapshot?.inPark == false && !allowAmbientWhileDriving
}

class MooDriveViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("moodrive", Context.MODE_PRIVATE)
    private val session = MooDriveSession(SampleCatalog.tracks)
    private val screenPlanner = AmbientPlanner(allowWhileDriving = true)
    private val vehicle: Vehicle? = runCatching { Vehicle(app).also { it.initialize() } }
        .onFailure { Log.w("MooDriveMeasure", "vehicle init failed: $it") }.getOrNull()
    private val pleosAmbient = PleosAmbientController(vehicle) { argb -> _ui.update { it.copy(carAmbientReported = argb) } }
    private val pleosSource = vehicle?.let { PleosVehicleSignalSource(app, it) }
    val manual = ManualSignalSource()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var sourceJob: Job? = null
    private val music = MusicPlayer(app)
    private val dj: DjVoice? = runCatching {
        DjVoice(app) { speaking -> viewModelScope.launch { music.setDucked(speaking) } }
    }.onFailure { Log.w("MooDriveMeasure", "tts unavailable: $it") }.getOrNull()
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
        startPlaybackTicker()
        useManual()
        // Vehicle Service가 응답하는 기기(Pleos)면 실제 신호로 시작
        vehicle?.getWiper()?.checkWiperStateCapabilities(
            onComplete = { _, _ -> viewModelScope.launch { useVehicle() } },
            onFailed = { Log.i("MooDriveMeasure", "vehicle service not available: $it") },
        )
    }

    // ── 온보딩 ──
    fun finishOnboarding(genreKeys: Set<String>) {
        require(genreKeys.size == Genres.PICK_COUNT)
        prefs.edit().putStringSet(KEY_GENRES, genreKeys).apply()
        session.taste.setPreferredGenres(genreKeys)
        _ui.update { it.copy(onboarded = true) }
    }

    // ── 신호 소스 ──
    fun useVehicle() {
        val source = pleosSource ?: return
        sourceJob?.cancel()
        resetTrip()
        timeScale = 1
        _ui.update { it.copy(scenarioCaption = null, scenarioT = null, vehicleConnected = true, demoPanel = false) }
        source.start()
        pleosAmbient.prepare()
        sourceJob = viewModelScope.launch { source.snapshots.collect(::onSnapshot) }
    }

    fun useManual() {
        _ui.update { it.copy(vehicleConnected = false) }
        sourceJob?.cancel()
        resetTrip()
        timeScale = 1
        _ui.update { it.copy(scenarioCaption = null, scenarioT = null) }
        sourceJob = viewModelScope.launch { manual.snapshots.collect(::onSnapshot) }
    }

    fun playScenario() {
        _ui.update { it.copy(vehicleConnected = false) }
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
        session.startTrip()
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
    fun onVoiceText(text: String) = handle(session.onVoice(text, playedSec = _ui.value.positionSec))
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
                music.play(a.track)
                val color = screenPlanner.commandFor(session.targetMood(), night = _ui.value.snapshot?.isDaytime() == false)
                if (played.lastOrNull()?.track?.id != a.track.id) played += PlayedTrack(a.track, a.scene, color)
            }
            is Action.SetAmbient -> {
                // 수동·시나리오 신호로는 실제 차량 조명을 바꾸지 않는다
                if (_ui.value.vehicleConnected) {
                    pleosAmbient.apply(a.command, onRestricted = {
                        viewModelScope.launch {
                            session.ambient.onRestricted()
                            _ui.update { it.copy(allowAmbientWhileDriving = false, carAmbient = null) }
                        }
                    })
                }
                _ui.update { it.copy(carAmbient = a.command) }
            }
            is Action.Speak -> {
                speak(a.text)
                val s = _ui.value.snapshot
                if (s != null && session.currentScene != Scene.PARKED) {
                    val m = Moment(clock(s), a.text.substringBefore(". ").removeSuffix("."))
                    if (moments.lastOrNull()?.clock == m.clock) moments[moments.lastIndex] = m else moments += m
                }
            }
            is Action.SceneChanged -> {
                // 말하지 않는 장면(터널 등)도 카드 '순간'에 남긴다. 같은 순간에 멘트가 오면 아래 Speak가 덮어씀
                val s = _ui.value.snapshot
                if (s != null && a.scene != Scene.PARKED && played.isNotEmpty()) {
                    moments += Moment(clock(s), if (a.scene == Scene.TUNNEL) "터널 · 곡 유지" else "${a.scene.label}로 전환")
                }
            }
            Action.SuggestRestStop -> _ui.update { it.copy(restStopPrompt = true) }
            Action.AddRestStop -> {
                // TODO: NaviHelper로 근처 휴게소 검색 → RequestWaypointInfo로 경유지 추가
            }
            is Action.TripEnded -> _ui.update { it.copy(tripCard = buildCard(a.log)) }
        }
    }

    private fun speak(text: String) {
        dj?.speak(text)
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
            // 첫 멘트(출발 인사)는 제외하고 최대 6개, 피날레 순간은 항상 포함
            moments = moments.drop(1).let { m ->
                val fin = m.lastOrNull { it.text.startsWith("곧 도착") }
                if (m.size <= 6) m else m.filter { it != fin }.take(5) + listOfNotNull(fin)
            },
        )
    }

    /**
     * 곡 진행 타이머: 1초마다 위치를 올리고, 곡 길이에 닿으면 다음 곡. 시연 음원은 짧아서 곡 길이까지 반복 재생한다.
     * 시나리오 재생 중에는 차량 시간 배율(timeScale)만큼 빠르게 진행.
     * 오디오 포커스를 잃어 소리가 안 나는 동안에는 진행하지 않는다.
     */
    private fun startPlaybackTicker() = viewModelScope.launch {
        while (isActive) {
            delay(1_000)
            // 차량 SDK·수동 패널은 값이 바뀔 때만 신호를 주므로, 1초마다 시간만 흘려 장면 확정·운전 시간·질문 시간 초과를 진행
            // (시나리오는 가상 차량 시간이라 tick이 무시됨)
            handle(session.tick(System.currentTimeMillis()))
            if (_ui.value.vehicleConnected) pleosSource?.refreshWiper()
            _ui.update { it.copy(scene = session.currentScene, restStopPrompt = it.restStopPrompt && session.restStopPending) }
            val st = _ui.value
            music.setActive(st.scene != null && st.scene != Scene.PARKED)
            val t = st.track ?: continue
            if (st.scene == Scene.PARKED || music.isSilenced) continue
            if (st.positionSec + timeScale >= t.durationSec) {
                handle(session.onTrackEnded(completed = true, playedSec = st.positionSec))
            } else {
                _ui.update { it.copy(positionSec = it.positionSec + timeScale) }
            }
        }
    }

    private fun clock(s: VehicleSnapshot) = "%02d:%02d".format(s.minuteOfDay / 60, s.minuteOfDay % 60)

    override fun onCleared() {
        dj?.release()
        music.release()
        pleosSource?.stop()
        pleosAmbient.release()
        runCatching { vehicle?.release() }
        super.onCleared()
    }

    private companion object {
        const val KEY_GENRES = "preferred_genres"
        const val DJ_CAPTION_MS = 12_000L
    }
}
