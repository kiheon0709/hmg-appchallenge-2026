package io.moodlab.sideseat.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import io.moodlab.sideseat.ui.SideSeatUiDebug
import io.moodlab.sideseat.ui.UiAction
import java.io.File

/**
 * 디버그 빌드 전용 adb 명령. 에뮬레이터 실측·시연 녹화에서 화면을 건드리지 않고 같은 경로를 탄다.
 *
 * `adb shell am broadcast -a io.moodlab.sideseat.DEBUG --es cmd <cmd> [--es arg <arg>]`
 * - `ask_pcm <앱 files 안 파일 이름>`: 마이크 대신 PCM(16kHz 16bit mono)으로 "질문하기"
 * - `frames on|off`: 주행 화면 프레임 로그
 * - `action <UiAction 이름>`: 예) OpenSafeDial, ToggleDebugPanel, GoHome, ShowResult, StartSurprise, StartVersus
 * - `answer <문장>`: 선택지 답(화면 터치·말 대신)
 * - `chapter <id>`: 챕터 시작, `inject <SituationId>`: 교관 대결로 상황 던지기
 * - `vehicle debug|pleos`: 차량 신호 출처 전환
 */
class DebugCommands(private val context: Context, private val controller: SideSeatController) : BroadcastReceiver() {
    fun register() {
        val filter = IntentFilter("io.moodlab.sideseat.DEBUG")
        context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED)
    }

    override fun onReceive(c: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd").orEmpty()
        val arg = intent.getStringExtra("arg").orEmpty().trim()
        Log.i("SideSeatMeasure", "app debug cmd=$cmd arg=$arg")
        when (cmd) {
            "ask_pcm" -> runCatching { File(context.filesDir, arg).readBytes() }
                .onSuccess { controller.askQuestion(it) }
                .onFailure { Log.w("SideSeatMeasure", "app debug pcm read failed: $it") }
            "frames" -> SideSeatUiDebug.logFrames = arg == "on"
            "action" -> uiAction(arg)?.let(controller::onAction)
            "chapter" -> controller.onAction(UiAction.StartChapter(arg))
            "answer" -> controller.onAction(UiAction.Answer(arg))
            "inject" -> runCatching { io.moodlab.sideseat.core.SituationId.valueOf(arg) }
                .onSuccess { controller.onAction(UiAction.Inject(it)) }
            "vehicle" -> AppGraph.useDebugVehicle(arg == "debug")
        }
    }

    private fun uiAction(name: String): UiAction? = when (name) {
        "StartSurprise" -> UiAction.StartSurprise
        "StartVersus" -> UiAction.StartVersus
        "AbortTraining" -> UiAction.AbortTraining
        "ShowResult" -> UiAction.ShowResult
        "GoHome" -> UiAction.GoHome
        "OpenSafeDial" -> UiAction.OpenSafeDial
        "OpenTurnSignalGame" -> UiAction.OpenTurnSignalGame
        "OpenWindowBuzzer" -> UiAction.OpenWindowBuzzer
        "CloseMiniGame" -> UiAction.CloseMiniGame
        "AskQuestion" -> UiAction.AskQuestion
        "StopTalking" -> UiAction.StopTalking
        "DismissReview" -> UiAction.DismissReview
        "ToggleDebugPanel" -> UiAction.ToggleDebugPanel
        else -> null
    }
}
