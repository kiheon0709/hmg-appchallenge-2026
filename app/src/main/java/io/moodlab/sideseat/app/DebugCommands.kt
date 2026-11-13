package io.moodlab.sideseat.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import io.moodlab.sideseat.ui.SideSeatUiDebug
import io.moodlab.sideseat.ui.UiAction
import java.io.File
import kotlinx.coroutines.launch

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
 * - `ambient CALM|ALERT|SUCCESS|OFF`, `reststop`: 게이트웨이 제어를 바로 시험
 * - `vehicle_force <차종 아이디|off>`: 차종 강제 지정, `where`: 지금 차종의 스위치 위치 문장
 * - `wake_pcm <files 안 PCM>`: 연속 청취의 다음 한 번을 PCM으로, `wake_status`: 상태·횟수
 * - `navi start|route <lat>,<lon>|trip <lat>,<lon>|get|reroute|cancel|stop`: NaviHelper 콜백 실측(NaviProbe)
 * - `loc start|stop`: FusedLocation 콜백 실측(LocationProbe)
 * - `wake_trial <files 안 목록 파일>`: 호출어 이름 후보 시험(VoiceModule.debugWakeTrial). 목록 줄은 `<PCM 파일>\t<wake|plain>`
 */
class DebugCommands(private val context: Context, private val controller: SideSeatController) : BroadcastReceiver() {
    private val naviProbe by lazy { io.moodlab.sideseat.app.measure.NaviProbe(context) }
    private val locationProbe by lazy { io.moodlab.sideseat.app.measure.LocationProbe(context) }

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
            "ambient" -> runCatching { io.moodlab.sideseat.core.AmbientMood.valueOf(arg) }.onSuccess { mood ->
                gatewayCall("ambient $mood") { it.setAmbient(mood) }
            }
            "reststop" -> gatewayCall("rest stop") { it.addRestStopWaypoint() }
            "navi" -> naviProbe.command(arg)
            "loc" -> locationProbe.command(arg)
            "wake_trial" -> runCatching {
                File(context.filesDir, arg).readLines().filter { it.isNotBlank() }.map { line ->
                    val (name, expect) = line.split('\t')
                    File(context.filesDir, name).readBytes() to (expect == "wake")
                }
            }.onSuccess { samples ->
                kotlinx.coroutines.MainScope().launch {
                    val score = io.moodlab.sideseat.voice.VoiceModule.debugWakeTrial(io.moodlab.sideseat.voice.Persona.WAKE_WORDS, io.moodlab.sideseat.voice.Persona.WEAK_WAKE_WORDS, samples)
                    Log.i("SideSeatMeasure", "app debug wake trial done n=${samples.size} $score")
                }
            }.onFailure { Log.w("SideSeatMeasure", "app debug wake trial read failed: $it") }
            "wake_pcm" -> runCatching { File(context.filesDir, arg).readBytes() }
                .onSuccess { io.moodlab.sideseat.voice.VoiceModule.wake?.debugFeedPcm(it) }
                .onFailure { Log.w("SideSeatMeasure", "app debug wake pcm read failed: $it") }
            "vehicle_force" -> Log.i("SideSeatMeasure", "app debug vehicle force $arg ok=${io.moodlab.sideseat.voice.VoiceModule.forceVehicle(arg.takeUnless { it == "off" || it.isEmpty() })} profiles=${io.moodlab.sideseat.voice.VoiceModule.vehicleProfiles()}")
            "where" -> Log.i("SideSeatMeasure", "app debug where vehicle=${io.moodlab.sideseat.voice.VoiceModule.currentVehicle?.id} " +
                listOf("sw_wiper", "sw_headlight", "sw_defrost", "sw_hazard", "sw_epb").joinToString(" | ") { "$it=${io.moodlab.sideseat.voice.VoiceModule.switchLocation(it)}" })
            "wake_status" -> Log.i("SideSeatMeasure", "app debug wake status ${io.moodlab.sideseat.voice.VoiceModule.wake?.debugStatus()}")
        }
    }

    private fun gatewayCall(what: String, f: suspend (io.moodlab.sideseat.vehicle.VehicleGateway) -> Boolean) {
        val g = AppGraph.currentGateway?.value ?: return
        kotlinx.coroutines.MainScope().launch {
            val t0 = android.os.SystemClock.elapsedRealtime()
            val ok = f(g)
            Log.i("SideSeatMeasure", "app debug $what ok=$ok took=${android.os.SystemClock.elapsedRealtime() - t0}ms")
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
