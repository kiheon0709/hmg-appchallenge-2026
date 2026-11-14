import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.moodlab.sideseat.core.*
import io.moodlab.sideseat.ui.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

var simT by mutableFloatStateOf(0f)
val stillBackdrop = StillSceneBackdrop(File("../../app/src/main/assets"))

fun shoot(name: String, w: Int, h: Int, dark: Boolean, t0: Float, t1: Float, content: @Composable () -> Unit) {
    SideSeatUiDebug.forceDark = dark
    val scene = ImageComposeScene(w, h, Density(1.25f)) {
        // 벡터 도로로 보려면 환경 변수 SIDESEAT_VECTOR=1
        if (System.getenv("SIDESEAT_VECTOR") == "1") content()
        else androidx.compose.runtime.CompositionLocalProvider(io.moodlab.sideseat.ui.scene.LocalSceneBackdrop provides stillBackdrop) { content() }
    }
    var t = t0
    var img = scene.render(0)
    val dt = 1f / 30f
    var n = 0L
    while (t <= t1) {
        simT = t
        n += (dt * 1e9f).toLong()
        img = scene.render(n)
        t += dt
    }
    File("out/$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
    println("wrote $name")
}

@Composable fun Train() { val (v, c) = UiSamples.trainingAt(simT); TrainingScreen(v, c) {} }

/** 상황 [id]가 시작된 뒤 [a]초 시점의 시연 시각. */
fun at(id: SituationId, a: Float): Float = UiSamples.order.indexOf(id) * UiSamples.STAGE + 1.6f + a

fun main(args: Array<String>) {
    File("out").mkdirs()
    val W = 1682; val H = 1210
    val only = args.toSet()
    fun want(n: String) = only.isEmpty() || only.any { n.contains(it) }
    fun train(name: String, id: SituationId, a: Float, dark: Boolean = false, w: Int = W, h: Int = H) {
        if (!want(name)) return
        val t = at(id, a)
        shoot(name, w, h, dark, maxOf(0f, t - 6f), t) { Train() }
    }
    train("t_cruise", SituationId.HEAVY_RAIN, -0.2f)
    train("t_rain_active", SituationId.HEAVY_RAIN, 1.9f)
    train("t_rain_hint", SituationId.HEAVY_RAIN, 2.4f, dark = true)
    train("t_rain_wiping", SituationId.HEAVY_RAIN, 3.6f)
    train("t_rain_success", SituationId.HEAVY_RAIN, 5.0f)
    train("t_fogglass", SituationId.FOGGED_WINDSHIELD, 2.3f)
    train("t_fogglass_clearing", SituationId.FOGGED_WINDSHIELD, 3.8f)
    train("t_stop", SituationId.SUDDEN_STOP, 1.9f)
    train("t_tunnel_enter", SituationId.TUNNEL, 1.0f)
    train("t_tunnel_in", SituationId.TUNNEL, 3.6f)
    train("t_glare", SituationId.ONCOMING_GLARE, 2.4f)
    train("t_densefog", SituationId.DENSE_FOG, 2.2f)
    train("t_drowsy", SituationId.DROWSY, 2.3f)
    train("t_warning", SituationId.WARNING_LIGHT, 2.0f)
    train("t_breakdown", SituationId.BREAKDOWN, 3.4f)
    train("t_accel", SituationId.UNINTENDED_ACCEL, 2.3f)
    train("t_exit", SituationId.BEFORE_EXIT, 1.2f)
    train("t_portrait_rain", SituationId.HEAVY_RAIN, 2.4f, w = 835, h = 1210)
    if (want("h_home")) shoot("h_home", W, H, false, 0f, 5f) { HomeScreen(UiSamples.home) {} }
    val car = VehicleState(0, parked = false, wiper = WiperLevel.LOW, lowBeamOn = false, recirculationOn = true, speedLimitKmh = 80, speedKmhEstimate = 93f)
    fun coach(name: String, dark: Boolean, ui: CoachUi, w: Int = W, h: Int = H) { if (want(name)) shoot(name, w, h, dark, 0f, 0.2f) { CoachShot(ui, car) } }
    coach("c_k01", false, UiSamples.coach)
    coach("c_k12_speaking", false, CoachUi(lastLine = "제한속도가 80이에요. 조금 줄여요.", urgent = true, wake = WakeUi.WAITING, talk = TalkUi(speaking = true)))
    coach("c_idle", false, CoachUi(wake = WakeUi.WAITING))
    coach("c_listening", false, CoachUi(lastLine = "비가 와요. 전조등을 켜 주세요.", wake = WakeUi.HEARD, talk = TalkUi(listening = true, partialText = "앞유리가 뿌예")))
    coach("c_k01_night", true, UiSamples.coach)
    coach("c_long_night", true, CoachUi(lastLine = "앞유리에 김이 설 수 있어요. 외기로 바꾸거나 에어컨을 켜 보세요.", wake = WakeUi.YIELDED))
    coach("c_portrait", false, UiSamples.coach, w = 835, h = 1210)
}
