package io.moodlab.sideseat.ui.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

// 실사 배경. 운전석 시점으로 찍은 영상·사진을 앞유리 밖 세상으로 깔고, 그 위에 비·김서림·와이퍼·조명 변화를 코드로 얹는다.
// 소재의 출처와 라이선스는 docs/assets-sources.md.

/**
 * 배경 소재 하나. [video]가 없으면 정지 사진만 쓴다. [still]은 영상이 뜨기 전과 화면 렌더 도구에서 보이는 그림.
 * [horizonU]·[horizonV]는 소재 안에서 길이 사라지는 점(0~1). 화면의 소실점에 이 점을 맞춘다.
 */
enum class BackdropClip(val video: String?, val still: String, val aspect: Float, val horizonU: Float, val horizonV: Float) {
    NIGHT_ROAD("scene/night_road.mp4", "scene/night_road.jpg", 16f / 9f, 0.5f, 0.37f),
    TUNNEL("scene/tunnel.mp4", "scene/tunnel.jpg", 16f / 9f, 0.52f, 0.43f),
    PARKING(null, "scene/parking.jpg", 4f / 3f, 0.4f, 0.5f),
}

/** 장면이 프레임마다 정해 주는 배경 상태. 재생기가 읽어서 따르고, 지금 재생 위치를 되적는다. */
class BackdropFrame {
    var clip = BackdropClip.NIGHT_ROAD
    /** 재생 속도(1 = 촬영한 그대로). 0이면 멈춘다. */
    var rate = 1f
    /** 화면을 덮는 최소 크기에 대한 확대 배율. */
    var zoom = 1f
    /** 소재 안의 점([focusU], [focusV])을 화면의 ([anchorX], [anchorY])(0~1)에 놓는다. */
    var focusU = 0.5f
    var focusV = 0.4f
    var anchorX = 0.5f
    var anchorY = 0.42f
    /** 0 이상이면 재생기가 이 시각(초)으로 건너뛰고 -1로 되돌린다. */
    var seekTo = -1f
    /** 재생기가 적는 지금 재생 위치(초). */
    var positionSec = 0f

    // layout() 결과: 화면 위에 놓인 소재의 왼쪽 위와 크기(px)
    var x0 = 0f
    var y0 = 0f
    var dw = 1f
    var dh = 1f

    /** 화면 크기에 맞춰 소재를 놓을 자리를 계산한다. 빈 곳이 보이지 않게 가장자리에서 멈춘다. */
    fun layout(viewW: Float, viewH: Float) {
        dw = maxOf(viewW, viewH * clip.aspect) * zoom
        dh = dw / clip.aspect
        x0 = (anchorX * viewW - focusU * dw).coerceIn(viewW - dw, 0f)
        y0 = (anchorY * viewH - focusV * dh).coerceIn(viewH - dh, 0f)
    }

    fun viewX(u: Float) = x0 + u * dw
    fun viewY(v: Float) = y0 + v * dh
}

/** 실사 배경을 그려 주는 쪽. 앱은 영상 재생기를, 화면 렌더 도구는 정지 그림을 넣는다. 없으면 코드로 그린 도로를 쓴다. */
interface SceneBackdrop {
    @Composable
    fun Layer(frame: BackdropFrame, modifier: Modifier)
}

val LocalSceneBackdrop = staticCompositionLocalOf<SceneBackdrop?> { null }

/** 정지 그림 한 장을 [frame]이 정한 자리에 그린다. */
fun DrawScope.drawStill(frame: BackdropFrame, image: ImageBitmap) {
    frame.layout(size.width, size.height)
    drawImage(
        image,
        dstOffset = IntOffset(frame.x0.roundToInt(), frame.y0.roundToInt()),
        dstSize = IntSize(frame.dw.roundToInt(), frame.dh.roundToInt()),
    )
}

/**
 * 야간 도로 영상 속 앞차 미등의 위치(0.5초 간격, 소재 안 좌표)와 차 폭. 영상에서 빨간 등을 찾아 적어 둔 값이다.
 * 앞차 급정거 장면에서 이 자리로 다가가고 브레이크등 빛을 얹는다.
 */
internal object LeadTrack {
    private val u = floatArrayOf(
        0.588f, 0.583f, 0.583f, 0.574f, 0.558f, 0.551f, 0.542f, 0.529f, 0.529f, 0.526f, 0.522f, 0.530f,
        0.531f, 0.534f, 0.535f, 0.533f, 0.540f, 0.549f, 0.547f, 0.543f, 0.542f, 0.534f, 0.536f, 0.535f, 0.535f,
    )
    private val w = floatArrayOf(
        0.040f, 0.040f, 0.035f, 0.034f, 0.030f, 0.030f, 0.028f, 0.025f, 0.027f, 0.022f, 0.021f, 0.022f,
        0.022f, 0.019f, 0.020f, 0.018f, 0.017f, 0.016f, 0.015f, 0.016f, 0.014f, 0.016f, 0.013f, 0.013f, 0.013f,
    )
    const val V = 0.368f

    private fun at(a: FloatArray, sec: Float): Float {
        val t = (sec * 2f).coerceIn(0f, (a.size - 1).toFloat())
        val i = t.toInt().coerceAtMost(a.size - 2)
        return a[i] + (a[i + 1] - a[i]) * (t - i)
    }

    fun u(sec: Float) = at(u, sec)
    fun width(sec: Float) = at(w, sec)
}
