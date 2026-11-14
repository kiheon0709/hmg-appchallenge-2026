package io.moodlab.sideseat.ui.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import io.moodlab.sideseat.core.SituationId
import io.moodlab.sideseat.core.TrainingConfig
import io.moodlab.sideseat.core.TrainingPhase
import io.moodlab.sideseat.core.TrainingView
import io.moodlab.sideseat.core.TurnSignal
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.core.WiperLevel
import java.util.Locale

// 3D 장면(assets/scene3d, three.js로 코드만으로 그린 도로). 판정은 core가 하고 장면은 받은 값을 보여 주기만 한다.
// 앞차가 실제로 멈추고, 브레이크·핸들이 장면에 그대로 반영된다. 영상 방식(Backdrop.kt)은 그대로 남아 있다.

/** 3D 장면을 그려 주는 쪽. 앱은 WebView를, 화면 렌더 도구는 아무것도 넣지 않는다(없으면 영상·코드 장면). */
interface Scene3dHost {
    /** [command]는 `sim.set(...)`에 넘길 JSON. 바뀔 때만 장면에 보낸다. */
    @Composable
    fun Layer(command: String, modifier: Modifier)
}

val LocalScene3d = staticCompositionLocalOf<Scene3dHost?> { null }

/** 훈련 장면을 3D로 그릴지 영상으로 그릴지. 기본값은 [TrainingConfig.SCENE_3D] 한 곳에서 정한다. */
object SceneRender {
    var use3d by mutableStateOf(TrainingConfig.SCENE_3D)

    /**
     * 3D 장면과 그 위 운전석 층을 초당 몇 장 그릴지(0이면 화면 주사율 그대로).
     * 에뮬레이터 실측(11-14)에서 60장은 정차 CPU 기준을 넘어 30장으로 낮췄다.
     */
    var fpsCap by mutableStateOf(TrainingConfig.SCENE_3D_FPS)

    /**
     * 3D 장면이 한 장 그릴 때마다 올라가는 값. 장면 위의 Compose 층(운전석, 초시계, 빛 띠)은 이 값이 바뀔 때만 다시 그린다.
     * 창이 다시 그려지는 횟수가 장면 장수와 같아져야 CPU가 준다(따로 돌면 둘이 엇갈려 화면 주사율만큼 그린다).
     */
    var frameTick by mutableLongStateOf(0L)
        private set
    @Volatile private var lastFrameNanos = 0L

    /** 장면이 프레임을 그렸다(메인 스레드에서 부른다). */
    fun onSceneFrame() {
        lastFrameNanos = System.nanoTime()
        frameTick++
    }

    /** 장면이 프레임을 내지 않는 동안(뜨는 중, 멈춤)에도 위 층이 굳지 않게 0.1초마다 대신 올린다. */
    suspend fun keepTicking() {
        while (true) {
            kotlinx.coroutines.delay(100)
            if (System.nanoTime() - lastFrameNanos > 150_000_000L) frameTick++
        }
    }

    /** 기기가 3D를 못 띄웠다(WebGL2 없음 등). 그 뒤로는 영상 방식으로 그린다. */
    var failed by mutableStateOf(false)

    /** 주차장(C4)은 사진 방식을 그대로 쓴다. */
    fun use3dFor(view: TrainingView): Boolean = use3d && !failed && !view.scene.parkingLot

    /**
     * 지금 화면이 3D 장면을 비쳐 보여야 하는가. 3D 장면은 Compose 화면 아래 층에 깔리므로(Scene3dHost 구현 참고),
     * 이때는 화면 바탕색을 칠하지 않는다.
     */
    @Composable
    fun seeThrough(view: TrainingView?): Boolean = view != null && LocalScene3d.current != null && use3dFor(view)
}

/**
 * 3D 장면을 쓰는 동안에는 훈련 상태를 장면 프레임에 맞춰서만 화면에 넘긴다(초시계 숫자 등이 프레임 사이에 따로 바뀌지 않게).
 * 판정과 소리는 그대로 엔진 주기를 따르고, 화면 표시만 한 프레임(약 0.03초) 안에서 맞춰진다.
 */
@Composable
fun <T> latchToSceneFrame(value: T, enabled: Boolean): T {
    if (!enabled) return value
    val latest = rememberUpdatedState(value)
    val shown = remember { mutableStateOf(value) }
    LaunchedEffect(Unit) { snapshotFlow { SceneRender.frameTick }.collect { shown.value = latest.value } }
    return shown.value
}

/** 훈련 상태와 실제 스위치 신호를 3D 장면의 `sim.set` 값으로 옮긴다. */
object Scene3dCommand {
    /** 앞차가 없는 상황(교차로·요금소·멈춰 선 차)에서는 앞차를 멀리 보낸다. */
    private val NO_LEAD = setOf(
        SituationId.RIGHT_TURN_CROSSWALK, SituationId.HIPASS_WRONG_LANE, SituationId.BREAKDOWN,
        SituationId.ROUNDABOUT_ENTRY, SituationId.SCHOOL_ZONE,
    )
    private const val CRUISE_GAP_M = 55f
    private const val FAR_GAP_M = 400f
    private const val TUNNEL_AHEAD_M = 70

    fun build(view: TrainingView, v: VehicleState): String {
        val s = view.scene
        val sit = view.situation
        val live = view.phase == TrainingPhase.BRIEFING || view.phase == TrainingPhase.ACTIVE || view.phase == TrainingPhase.HINT
        val inSit = live || view.phase == TrainingPhase.SUCCESS || view.phase == TrainingPhase.FAIL
        fun done(id: String) = view.steps.any { it.id == id && it.done }
        val speed = view.virtualSpeedKmh
        val lead = s.leadCarDistance
        val gap = when {
            // 0(충돌)~1(멀리)을 1~40m로. 급정거에서 밟은 뒤에는 속도가 줄수록 더 다가가 앞차 바로 뒤에 선다
            lead != null -> {
                val k = if (sit == SituationId.SUDDEN_STOP && view.phase != TrainingPhase.FAIL) 0.35f + 0.65f * (speed / 80f).coerceIn(0f, 1f) else 1f
                1f + 38.7f * lead * k
            }
            inSit && sit in NO_LEAD -> FAR_GAP_M
            else -> CRUISE_GAP_M
        }
        val landmark = when (s.sign) {
            "CROSSWALK" -> "\"crosswalk\""
            "HIPASS" -> "\"tollgate\""
            "ROUNDABOUT" -> "\"roundabout\""
            "SCHOOL" -> "\"school\""
            else -> "null"
        }
        // 요금소는 "그대로 지나간다"를 고를 때까지 앞에서 기다리고, 횡단보도는 설 수 있는 거리보다 가까워지지 않는다
        val hold = s.sign != "HIPASS" || !done("pass")
        val lane = if (sit == SituationId.HIGHWAY_MERGE && live && !done("steer_left")) 1 else 0
        val wiper = when (v.wiper) {
            null, WiperLevel.OFF -> 0
            WiperLevel.HIGH -> 2
            else -> 1
        }
        val lights = if (v.highBeamOn == true) 2 else if (v.lowBeamOn == true) 1 else 0
        val turn = when {
            v.hazardOn == true || v.turnSignal == TurnSignal.HAZARD -> "hazard"
            v.turnSignal == TurnSignal.LEFT -> "left"
            v.turnSignal == TurnSignal.RIGHT -> "right"
            else -> "off"
        }
        val sb = StringBuilder(320)
        sb.append("{\"hostSpeed\":").append(f1(speed))
        sb.append(",\"hostGap\":").append(f1(gap))
        sb.append(",\"leadBrake\":").append(s.leadCarBraking)
        sb.append(",\"crash\":").append(sit == SituationId.SUDDEN_STOP && view.phase == TrainingPhase.FAIL)
        sb.append(",\"steer\":").append(f1(v.steeringAngleDeg ?: 0f))
        sb.append(",\"turn\":\"").append(turn).append('"')
        sb.append(",\"wiper\":").append(wiper)
        sb.append(",\"headlights\":").append(lights)
        sb.append(",\"rain\":").append(f2(s.rain))
        sb.append(",\"fog\":").append(f2(s.fogOutside))
        sb.append(",\"fogging\":").append(f2(s.windshieldFog))
        sb.append(",\"blur\":").append(f2(s.drowsyBlur))
        sb.append(",\"hydroplane\":").append(f2(if (s.slip > 0.05f) s.slip else 0f))
        sb.append(",\"tunnelDist\":").append(TUNNEL_AHEAD_M)
        sb.append(",\"tunnel\":").append(s.tunnel)
        sb.append(",\"place\":\"").append(s.place.lowercase(Locale.ROOT)).append('"')
        sb.append(",\"landmark\":").append(landmark)
        sb.append(",\"hold\":").append(hold)
        sb.append(",\"lane\":").append(lane)
        // 맞은편 차는 한 번 출발하면 지나갈 때까지 둔다(끄는 값은 보내지 않는다)
        if (sit == SituationId.ONCOMING_GLARE && live) sb.append(",\"glare\":true")
        sb.append('}')
        return sb.toString()
    }

    private fun f1(x: Float) = String.format(Locale.ROOT, "%.1f", x)
    private fun f2(x: Float) = String.format(Locale.ROOT, "%.2f", x)
}
