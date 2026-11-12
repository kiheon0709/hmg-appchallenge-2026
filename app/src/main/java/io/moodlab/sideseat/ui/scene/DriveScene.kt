package io.moodlab.sideseat.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import io.moodlab.sideseat.core.SceneParams
import io.moodlab.sideseat.core.TrainingPhase
import io.moodlab.sideseat.core.TrainingView
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.ui.FrameMeter
import io.moodlab.sideseat.ui.SideSeatUiDebug

/**
 * 운전석 시점의 가짜 3D 주행 화면. 옛 레이싱 게임처럼 도로를 거리별로 투영해 2D로 그린다.
 * 층: 하늘·먼 배경 → 도로·차선 → 차·가로등·표지 → 안개·비 → 앞유리(물막·물방울·김서림·이물질·와이퍼) → 운전석 틀.
 * 그림은 전부 코드로 그리고, 프레임마다 새로 만드는 객체가 없도록 붓·경로·입자를 미리 만들어 둔다.
 */
@Composable
fun DriveScene(view: TrainingView, vehicle: VehicleState, modifier: Modifier = Modifier) {
    DriveScene(view.scene, vehicle, view.virtualSpeedKmh, modifier, success = view.phase == TrainingPhase.SUCCESS)
}

@Composable
fun DriveScene(
    scene: SceneParams,
    vehicle: VehicleState,
    speedKmh: Float,
    modifier: Modifier = Modifier,
    success: Boolean = false,
) {
    val input = remember { SceneInput() }
    input.set(scene, vehicle, speedKmh, success)
    val sim = remember { SceneSim() }
    val world = remember { WorldLayer() }
    val glass = remember { GlassLayer() }
    val cockpit = remember { CockpitLayer() }
    val meter = remember { FrameMeter("scene") }
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    sim.step((now - last) / 1e9f, input)
                    meter.frame(now - last)
                    // 프레임이 모자라면 효과를 줄인다(한번 줄이면 그 화면에서는 유지)
                    if (!sim.lowDetail && (SideSeatUiDebug.lowDetail || (meter.fps > 0f && meter.fps < 26f))) {
                        sim.lowDetail = true
                        SideSeatUiDebug.lowDetail = true
                        if (SideSeatUiDebug.logFrames) android.util.Log.i("SideSeatMeasure", "frame scene lowDetail=on fps=%.1f".format(meter.fps))
                    }
                }
                last = now
                frame = now
            }
        }
    }

    Canvas(modifier.clipToBounds()) {
        @Suppress("UNUSED_EXPRESSION") frame // 프레임마다 다시 그리게 한다(재구성 없이 그리기 단계만)
        val t0 = System.nanoTime()
        sim.setSize(size.width, size.height)
        world.draw(this, sim)
        glass.draw(this, sim)
        cockpit.draw(this, sim, input)
        meter.draw(System.nanoTime() - t0)
    }
}
