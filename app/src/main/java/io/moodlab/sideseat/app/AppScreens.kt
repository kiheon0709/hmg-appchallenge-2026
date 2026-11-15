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
import io.moodlab.sideseat.ui.LocalHomeArt
import io.moodlab.sideseat.ui.LocalSwitchLocation
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import io.moodlab.sideseat.ui.scene.LocalSceneBackdrop
import io.moodlab.sideseat.ui.scene.LocalScene3d
import io.moodlab.sideseat.ui.scene.SceneRender
import io.moodlab.sideseat.uivideo.VideoSceneBackdrop
import io.moodlab.sideseat.uivideo.WebViewScene3d
import androidx.compose.runtime.remember
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
import io.moodlab.sideseat.ui.WakeTestUi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** 모드에 맞는 화면 하나를 고른다. 화면은 ui 패키지(dev-sim-ui)가 그린다. */
@Composable
fun AppScreens(ui: AppUi, onAction: (UiAction) -> Unit, onDebugChange: ((VehicleState) -> Unit)? = null) {
    // 이 차의 스위치 위치 문장(차종별 지식 파일). 화면은 음성 모듈을 직접 만지지 않는다.
    // 정차 화면의 주행 장면은 실사 영상을 배경으로 쓴다(재생기는 화면이 사라지면 같이 풀린다)
    val backdrop = remember { VideoSceneBackdrop() }
    // 훈련 장면은 3D(WebView)로 그린다. 못 띄우는 기기와 주차장 장면은 위의 영상·사진 방식으로 돌아간다
    val scene3d = remember { WebViewScene3d() }
    // 홈 미리보기 그림(3D 훈련 장면 캡처). 못 읽으면 홈이 예전처럼 주행 장면을 그린다
    val context = LocalContext.current
    val homeArt = remember { runCatching { context.assets.open("home/hero.jpg").use { BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull() }
    // 앱이 가려지면 훈련·미니게임을 멈추고, 다시 보이면 이어 가게 알린다
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { onAction(UiAction.Background) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) { onAction(UiAction.Foreground) }
    val appFont = remember { androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(io.moodlab.sideseat.R.font.jua_regular)) }
    CompositionLocalProvider(
        io.moodlab.sideseat.ui.LocalAppFont provides appFont,
        LocalHomeArt provides homeArt,
        LocalSwitchLocation provides { id -> VoiceModule.switchLocation(id) },
        LocalSceneBackdrop provides backdrop,
        LocalScene3d provides scene3d,
    ) {
        AppScreensBody(ui, onAction, onDebugChange)
    }
}

@Composable
private fun AppScreensBody(ui: AppUi, onAction: (UiAction) -> Unit, onDebugChange: ((VehicleState) -> Unit)?) {
    val seeThrough = ui.mode == AppMode.TRAINING && SceneRender.seeThrough(ui.training)
    Box(if (seeThrough) Modifier.fillMaxSize() else Modifier.fillMaxSize().background(Sc.bg)) {
        when (ui.mode) {
            AppMode.HOME -> HomeScreen(ui.home, onAction)
            AppMode.TRAINING -> ui.training?.let { TrainingScreen(it, ui.vehicle, onAction) } ?: Waiting()
            AppMode.RESULT -> ui.result?.let { ResultScreen(it, ui.progress, ui.bestBefore, onAction) } ?: Waiting()
            AppMode.COACH -> CoachScreen(ui.coach, onAction, ui.vehicle)
            AppMode.REVIEW -> ui.review?.let { ReviewScreen(it, onAction) } ?: Waiting()
            AppMode.MINIGAME -> when (ui.miniGame) {
                MiniGameKind.SAFE_DIAL -> ui.safeDial?.let { SafeDialScreen(it, onAction) } ?: Waiting()
                MiniGameKind.TURN_SIGNAL -> ui.turnGame?.let { TurnSignalGameScreen(it, onAction) } ?: Waiting()
                MiniGameKind.WINDOW_BUZZER -> ui.windowGame?.let { WindowBuzzerScreen(it, onAction) } ?: Waiting()
                null -> Waiting()
            }
        }
        if (ui.debugPanel && onDebugChange != null) {
            // 호출어 직접 불러 보기 시험(사람 목소리). 음성 모듈 상태를 화면 모델로 옮긴다.
            val wt by VoiceModule.wakeTest.collectAsState()
            DebugPanel(
                ui.vehicle, onDebugChange,
                Modifier.align(Alignment.BottomEnd).padding(16.dp).widthIn(max = 560.dp).heightIn(max = 640.dp),
                onClose = { onAction(UiAction.ToggleDebugPanel) },
                wakeTest = WakeTestUi(wt.running, wt.lines, wt.summary),
                onWakeTest = { start -> if (start) VoiceModule.startWakeTest() else VoiceModule.stopWakeTest() },
            )
        }
    }
}

@Composable
private fun Waiting() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("…", color = Sc.textDim) }
