package io.moodlab.sideseat.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.ui.DebugPanel
import io.moodlab.sideseat.ui.TurnSignalGameScreen
import io.moodlab.sideseat.ui.WindowBuzzerScreen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.moodlab.sideseat.ui.LocalSwitchLocation
import io.moodlab.sideseat.voice.VoiceModule
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.moodlab.sideseat.ui.CoachScreen
import io.moodlab.sideseat.ui.HomeScreen
import io.moodlab.sideseat.ui.ResultScreen
import io.moodlab.sideseat.ui.ReviewScreen
import io.moodlab.sideseat.ui.SafeDialScreen
import io.moodlab.sideseat.ui.Sc
import io.moodlab.sideseat.ui.TrainingScreen
import io.moodlab.sideseat.ui.UiAction

/** 모드에 맞는 화면 하나를 고른다. 화면은 ui 패키지(dev-sim-ui)가 그린다. */
@Composable
fun AppScreens(ui: AppUi, onAction: (UiAction) -> Unit, onDebugChange: ((VehicleState) -> Unit)? = null) {
    // 이 차의 스위치 위치 문장(차종별 지식 파일). 화면은 음성 모듈을 직접 만지지 않는다.
    CompositionLocalProvider(LocalSwitchLocation provides { id -> VoiceModule.switchLocation(id) }) {
        AppScreensBody(ui, onAction, onDebugChange)
    }
}

@Composable
private fun AppScreensBody(ui: AppUi, onAction: (UiAction) -> Unit, onDebugChange: ((VehicleState) -> Unit)?) {
    Box(Modifier.fillMaxSize().background(Sc.bg)) {
        when (ui.mode) {
            AppMode.HOME -> HomeScreen(ui.home, onAction)
            AppMode.TRAINING -> ui.training?.let { TrainingScreen(it, ui.vehicle, onAction) } ?: Waiting()
            AppMode.RESULT -> ui.result?.let { ResultScreen(it, ui.progress, onAction) } ?: Waiting()
            AppMode.COACH -> CoachScreen(ui.coach, onAction)
            AppMode.REVIEW -> ui.review?.let { ReviewScreen(it, onAction) } ?: Waiting()
            AppMode.MINIGAME -> when (ui.miniGame) {
                MiniGameKind.SAFE_DIAL -> ui.safeDial?.let { SafeDialScreen(it, onAction) } ?: Waiting()
                MiniGameKind.TURN_SIGNAL -> ui.turnGame?.let { TurnSignalGameScreen(it, onAction) } ?: Waiting()
                MiniGameKind.WINDOW_BUZZER -> ui.windowGame?.let { WindowBuzzerScreen(it, onAction) } ?: Waiting()
                null -> Waiting()
            }
        }
        if (ui.debugPanel && onDebugChange != null) {
            DebugPanel(
                ui.vehicle, onDebugChange,
                Modifier.align(Alignment.BottomEnd).padding(16.dp).widthIn(max = 560.dp).heightIn(max = 640.dp),
                onClose = { onAction(UiAction.ToggleDebugPanel) },
            )
        }
    }
}

@Composable
private fun Waiting() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("…", color = Sc.textDim) }
