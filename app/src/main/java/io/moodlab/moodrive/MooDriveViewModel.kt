package io.moodlab.moodrive

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.moodlab.moodrive.core.Action
import io.moodlab.moodrive.core.AmbientCommand
import io.moodlab.moodrive.core.Feedback
import io.moodlab.moodrive.core.Mood
import io.moodlab.moodrive.core.MooDriveSession
import io.moodlab.moodrive.core.SampleCatalog
import io.moodlab.moodrive.core.Scenario
import io.moodlab.moodrive.core.Scene
import io.moodlab.moodrive.core.Track
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

data class UiState(
    val snapshot: VehicleSnapshot? = null,
    val scene: Scene? = null,
    val mood: Mood? = null,
    val track: Track? = null,
    val positionSec: Int = 0,
    val ambient: AmbientCommand? = null,
    /** 정책상 보류 중인 조명 목표(주행 중). 화면엔 점선으로 보여 준다. */
    val ambientPending: Boolean = false,
    val lastDjLine: String? = null,
    val scenarioCaption: String? = null,
    val scenarioT: Int? = null,
    val allowAmbientWhileDriving: Boolean = false,
    val tripCardScenes: List<Scene>? = null,
)

class MooDriveViewModel(app: Application) : AndroidViewModel(app) {
    private val session = MooDriveSession(SampleCatalog.tracks)
    private val pleosAmbient = PleosAmbientController(app)
    val manual = ManualSignalSource()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var sourceJob: Job? = null
    private var tts: TextToSpeech? = null

    init {
        // TODO: Pleos TTS SDK(HYBRID)로 교체. 그전까진 안드로이드 기본 TTS
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.KOREAN
        }
        startPlaybackTicker()
        useManual()
    }

    fun useManual() {
        sourceJob?.cancel()
        session.engine.reset()
        _ui.update { it.copy(scenarioCaption = null, scenarioT = null) }
        sourceJob = viewModelScope.launch { manual.snapshots.collect(::onSnapshot) }
    }

    fun playScenario() {
        sourceJob?.cancel()
        session.engine.reset()
        val sc = Scenario.rainyCommute(startMs = System.currentTimeMillis())
        sourceJob = viewModelScope.launch {
            scenarioFlow(sc) { caption, t -> _ui.update { it.copy(scenarioCaption = caption, scenarioT = t) } }
                .collect(::onSnapshot)
        }
    }

    fun setAllowAmbientWhileDriving(allow: Boolean) {
        session.ambient.allowWhileDriving = allow
        _ui.update { it.copy(allowAmbientWhileDriving = allow) }
    }

    fun like() = handle(session.onFeedback(Feedback.LIKE))
    fun dislike() = handle(session.onFeedback(Feedback.DISLIKE))
    fun skip() = handle(session.onTrackEnded(completed = false))
    fun dismissTripCard() = _ui.update { it.copy(tripCardScenes = null) }

    private fun onSnapshot(s: VehicleSnapshot) {
        _ui.update {
            it.copy(snapshot = s, ambientPending = s.isDriving && !it.allowAmbientWhileDriving)
        }
        handle(session.onSnapshot(s))
        _ui.update { it.copy(scene = session.currentScene, mood = session.currentMood) }
    }

    private fun handle(actions: List<Action>) {
        for (a in actions) when (a) {
            is Action.Play -> _ui.update { it.copy(track = a.track, positionSec = 0) }
            is Action.SetAmbient -> {
                pleosAmbient.apply(a.command)
                _ui.update { it.copy(ambient = a.command) }
            }
            is Action.Speak -> {
                tts?.speak(a.text, TextToSpeech.QUEUE_FLUSH, null, "dj")
                _ui.update { it.copy(lastDjLine = a.text) }
            }
            is Action.SceneChanged -> Unit
            is Action.TripEnded -> _ui.update { it.copy(tripCardScenes = a.log.map { e -> e.scene }.distinct()) }
        }
    }

    /**
     * 음원 번들 전까지는 시뮬레이션 재생: 1초마다 위치를 올리고, 곡 길이에 닿으면 다음 곡.
     * TODO: Media3 ExoPlayer로 교체 (assets 음원), 오디오 포커스·TTS 덕킹 처리
     */
    private fun startPlaybackTicker() = viewModelScope.launch {
        while (isActive) {
            delay(1_000)
            val st = _ui.value
            val t = st.track ?: continue
            if (st.scene == Scene.PARKED) continue
            if (st.positionSec + 1 >= t.durationSec) {
                handle(session.onTrackEnded(completed = true))
            } else {
                _ui.update { it.copy(positionSec = it.positionSec + 1) }
            }
        }
    }

    override fun onCleared() {
        tts?.shutdown()
        super.onCleared()
    }
}
