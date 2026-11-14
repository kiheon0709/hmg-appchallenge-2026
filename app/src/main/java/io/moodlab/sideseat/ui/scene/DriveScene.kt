package io.moodlab.sideseat.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.rememberTextMeasurer
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
    val host3d = LocalScene3d.current
    if (host3d != null && SceneRender.use3dFor(view)) {
        DriveScene3d(host3d, view, vehicle, modifier)
        return
    }
    DriveScene(view.scene, vehicle, view.virtualSpeedKmh, modifier, success = view.phase == TrainingPhase.SUCCESS)
}

/** 3D 장면 위에 운전석 틀(대시보드·계기판·핸들·경고등)만 얹는다. 비·김서림·와이퍼·앞차는 3D 장면이 그린다. */
@Composable
private fun DriveScene3d(host: Scene3dHost, view: TrainingView, vehicle: VehicleState, modifier: Modifier) {
    val input = remember { SceneInput() }
    input.set(view, vehicle)
    input.sign = null // 횡단보도·요금소는 3D 장면에 있어 표지 판을 띄우지 않는다
    val sim = remember { SceneSim().also { it.photo = true; it.lowDetail = true } }
    val cockpit = remember { CockpitLayer() }
    val meter = remember { FrameMeter("scene3d-overlay") }
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val clock = remember { LongArray(1) }
    LaunchedEffect(Unit) { SceneRender.keepTicking() }
    Box(modifier) {
        host.Layer(Scene3dCommand.build(view, vehicle), Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            // 장면이 한 장 그릴 때마다 같이 다시 그린다(따로 프레임 루프를 돌리지 않는다)
            @Suppress("UNUSED_EXPRESSION") SceneRender.frameTick
            val t0 = System.nanoTime()
            if (clock[0] != 0L) {
                sim.step((t0 - clock[0]) / 1e9f, input)
                meter.frame(t0 - clock[0])
            }
            clock[0] = t0
            sim.setSize(size.width, size.height)
            cockpit.draw(this, sim, input, measurer)
            meter.draw(System.nanoTime() - t0)
        }
    }
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
    val measurer = rememberTextMeasurer(cacheSize = 0)
    // 실사 배경이 있으면 도로는 영상이 보여 주고, 여기서는 그 위의 효과와 앞유리·운전석만 그린다
    val backdrop = LocalSceneBackdrop.current
    val backdropFrame = remember { BackdropFrame() }
    sim.photo = backdrop != null
    val blurs = remember { arrayOfNulls<BlurEffect>(BLUR_STEPS + 1) }
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    sim.step((now - last) / 1e9f, input)
                    if (backdrop != null) sim.direct(backdropFrame, input)
                    meter.frame(now - last)
                    // 프레임이 두 구간(약 4초) 연달아 모자라면 효과를 줄인다(한번 줄이면 그 화면에서는 유지)
                    if (!sim.lowDetail && (SideSeatUiDebug.lowDetail || meter.slowStreak >= 2)) {
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

    if (backdrop != null) {
        Box(modifier.clipToBounds()) {
            // 닦이지 않은 빗물·졸음만큼 바깥이 번져 보인다(안드로이드 12 이상에서만 적용된다)
            backdrop.Layer(
                backdropFrame,
                Modifier.fillMaxSize().graphicsLayer {
                    @Suppress("UNUSED_EXPRESSION") frame
                    val step = if (sim.lowDetail) 0 else (sim.glassBlur * BLUR_STEPS).toInt().coerceIn(0, BLUR_STEPS)
                    renderEffect = if (step == 0) null else blurs[step] ?: BlurEffect(step * 1.6f * density, step * 1.6f * density, TileMode.Clamp).also { blurs[step] = it }
                },
            )
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") frame
                val t0 = System.nanoTime()
                sim.setSize(size.width, size.height)
                world.drawOverBackdrop(this, sim, backdropFrame)
                glass.draw(this, sim)
                cockpit.draw(this, sim, input, measurer)
                meter.draw(System.nanoTime() - t0)
            }
        }
        return
    }
    Canvas(modifier.clipToBounds()) {
        @Suppress("UNUSED_EXPRESSION") frame // 프레임마다 다시 그리게 한다(재구성 없이 그리기 단계만)
        val t0 = System.nanoTime()
        sim.setSize(size.width, size.height)
        world.draw(this, sim)
        glass.draw(this, sim)
        cockpit.draw(this, sim, input, measurer)
        meter.draw(System.nanoTime() - t0)
    }
}

private const val BLUR_STEPS = 10
