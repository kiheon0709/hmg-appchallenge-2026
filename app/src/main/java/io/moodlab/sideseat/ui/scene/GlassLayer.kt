package io.moodlab.sideseat.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** 앞유리 층: 물막, 물방울, 김서림, 이물질, 와이퍼. */
internal class GlassLayer {
    private var w = 0f
    private var h = 0f
    private val wedgeA = Array(SceneSim.WIPE_BINS) { Path() }
    private val wedgeB = Array(SceneSim.WIPE_BINS) { Path() }
    private val unswept = Path()
    private val tmp = Path()
    private val layerPaint = Paint()
    /** 김이 걷힌 자리를 뚫는 붓(가운데가 완전히 맑고 가장자리로 갈수록 김이 남는다). */
    private val hole: Brush = Brush.radialGradient(
        0f to Color.Black, 0.5f to Color.Black, 0.8f to Color(0x99000000), 1f to Color(0x00000000),
        center = Offset.Zero, radius = 1f,
    )
    private val bokehX = FloatArray(BOKEH)
    private val bokehY = FloatArray(BOKEH)
    private val bokehR = FloatArray(BOKEH)
    private val blobs = GlowBrush(Color.White, core = 0.05f)
    private val bokehWarm = GlowBrush(Color(0xFFFFC27A), core = 0.45f)
    private val bokehRed = GlowBrush(Color(0xFFFF4A5A), core = 0.45f)
    private val bokehCool = GlowBrush(Color(0xFF9FD8FF), core = 0.45f)

    private fun ensure(size: Size, sim: SceneSim) {
        if (size.width == w && size.height == h) return
        w = size.width; h = size.height
        val l = sim.bladeL
        val sectorA = sector(sim.pivAx, sim.pivY, l, SceneSim.WIPE_REST_DEG, SceneSim.WIPE_END_DEG)
        val sectorB = sector(sim.pivBx, sim.pivY, l, SceneSim.WIPE_REST_DEG, SceneSim.WIPE_END_DEG)
        val step = (SceneSim.WIPE_REST_DEG - SceneSim.WIPE_END_DEG) / SceneSim.WIPE_BINS
        for (k in 0 until SceneSim.WIPE_BINS) {
            val a0 = SceneSim.WIPE_REST_DEG - k * step
            val a1 = a0 - step - 0.4f
            wedgeA[k].rewind(); wedgeA[k].addPath(sector(sim.pivAx, sim.pivY, l, a0, a1))
            wedgeB[k].rewind(); wedgeB[k].op(sector(sim.pivBx, sim.pivY, l, a0, a1), sectorA, PathOperation.Difference)
        }
        val glass = Path().apply { addRect(Rect(0f, 0f, w, sim.dashY + h * 0.03f)) }
        tmp.rewind(); tmp.op(glass, sectorA, PathOperation.Difference)
        unswept.rewind(); unswept.op(tmp, sectorB, PathOperation.Difference)

        var seed = 911
        fun r(): Float { seed = seed * 1103515245 + 12345; return ((seed ushr 8) and 0xFFFF) / 65535f }
        for (k in 0 until BOKEH) {
            bokehX[k] = r() * w; bokehY[k] = h * (0.2f + r() * 0.5f); bokehR[k] = h * (0.02f + r() * 0.04f)
        }
    }

    private fun sector(px: Float, py: Float, l: Float, fromDeg: Float, toDeg: Float): Path {
        val p = Path()
        val r0 = l * SceneSim.BLADE_INNER
        val steps = maxOf(2, ((fromDeg - toDeg) / 6f).toInt())
        for (i in 0..steps) {
            val a = (fromDeg + (toDeg - fromDeg) * i / steps) * PI.toFloat() / 180f
            val x = px + l * cos(a); val y = py - l * sin(a)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        for (i in steps downTo 0) {
            val a = (fromDeg + (toDeg - fromDeg) * i / steps) * PI.toFloat() / 180f
            p.lineTo(px + r0 * cos(a), py - r0 * sin(a))
        }
        p.close()
        return p
    }

    fun draw(scope: DrawScope, sim: SceneSim) = with(scope) {
        ensure(size, sim)
        val glassBottom = sim.dashY + h * 0.03f
        clipRect(0f, 0f, w, glassBottom) {
            drawWater(sim)
            drawFog(sim, glassBottom)
            drawDebris(sim)
        }
        drawWipers(sim)
    }

    private fun DrawScope.drawWater(sim: SceneSim) {
        if (sim.ambientWet < 0.01f && sim.rain < 0.01f) return
        val film = mix(Color(0xFF8B98A8), Color(0xFF1B2634), sim.night)
        val maxA = if (sim.photo) 0.36f else 0.64f
        val un = sim.ambientWet
        if (un > 0.01f) drawPath(unswept, film, alpha = filmAlpha(un) * (if (sim.photo) 0.55f else maxA))
        for (k in 0 until SceneSim.WIPE_BINS) {
            // 실사 배경에서는 막 닦인 자리가 거의 맑다(물이 두껍게 덮일수록 급히 흐려진다)
            val a = (if (sim.photo) sim.wet[k] * sim.wet[k] else filmAlpha(sim.wet[k])) * maxA
            if (a < 0.012f) continue
            drawPath(wedgeA[k], film, alpha = a)
            drawPath(wedgeB[k], film, alpha = a)
        }
        // 물막 너머로 번진 앞차 후미등
        if (!sim.photo && sim.leadA > 0.05f && sim.ambientWet > 0.05f && sim.parking < 0.5f) {
            val k = maxOf(sim.leadK, h * 0.012f)
            val a = sim.ambientWet * sim.leadA * (0.45f + 0.55f * sim.leadBrake)
            glow(bokehRed, sim.leadSx - 0.8f * k, sim.leadSy - 0.7f * k, 1.6f * k + h * 0.02f, alpha = a * 0.7f)
            glow(bokehRed, sim.leadSx + 0.8f * k, sim.leadSy - 0.7f * k, 1.6f * k + h * 0.02f, alpha = a * 0.7f)
            glow(bokehRed, sim.leadSx, sim.leadSy + 1.5f * k, 1.2f * k + h * 0.01f, 3f * k + h * 0.03f, alpha = a * 0.35f)
        }
        // 물막 너머로 번진 불빛
        var avg = 0f
        for (k in 0 until SceneSim.WIPE_BINS) avg += sim.wet[k]
        avg /= SceneSim.WIPE_BINS
        if (!sim.photo && avg > 0.05f && sim.night > 0.3f && !sim.lowDetail) {
            for (k in 0 until BOKEH) {
                val g = when (k % 3) { 0 -> bokehWarm; 1 -> bokehRed; else -> bokehCool }
                val wob = sin(sim.t * 0.7f + k) * h * 0.01f
                glow(g, bokehX[k] + wob, bokehY[k], bokehR[k], alpha = avg * 0.13f * sim.night)
            }
        }
        // 물방울: 작은 렌즈처럼 위쪽은 어둡고 아래 가장자리에 빛이 맺힌다. 달리는 동안에는 바람에 밀려 위로 흐른다
        val hi = Color(0xFFEAF1F8)
        val lo = Color(0xFF04070B)
        val up = sim.speed * 0.0025f * h
        val big = h * 0.0055f
        val stepN = if (sim.lowDetail) 2 else 1
        for (n in 0 until SceneSim.MAX_DROPS step stepN) {
            val a = sim.dropAlpha(n)
            if (a <= 0.02f) continue
            val r = sim.dropR[n]
            val age = sim.t - sim.dropBorn[n]
            val run = up * age * (r / (h * 0.012f))
            val x = sim.dropX[n]
            val y = sim.dropY[n] - run
            drawCircle(lo, r, Offset(x, y), alpha = (if (sim.photo) 0.42f else 0.3f) * a)
            if (r < big) {
                drawCircle(hi, r * 0.42f, Offset(x + r * 0.18f, y + r * 0.3f), alpha = 0.34f * a)
            } else {
                drawArc(hi, 20f, 95f, false, Offset(x - r * 0.8f, y - r * 0.8f), Size(r * 1.6f, r * 1.6f), alpha = 0.3f * a, style = Stroke(r * 0.16f, cap = StrokeCap.Round))
                drawCircle(hi, r * 0.14f, Offset(x - r * 0.36f, y - r * 0.4f), alpha = 0.45f * a)
            }
        }
    }

    private fun filmAlpha(wet: Float): Float = if (wet <= 0f) 0f else kotlin.math.sqrt(wet.coerceAtMost(1f))

    /**
     * 앞유리 안쪽 김서림. 낄 때는 가장자리에서 가운데로 좁혀 오고,
     * 걷힐 때는 송풍구가 있는 아래 두 곳에서부터 부채꼴로 맑아진다.
     */
    private fun DrawScope.drawFog(sim: SceneSim, glassBottom: Float) {
        val f = sim.wsFog
        if (f < 0.005f) return
        val c = mix(Color(0xFFDDE2E6), Color(0xFFA9B1B8), sim.night)
        val a = 0.94f * minOf(1f, f * 3.5f)
        val open = 1f - f
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, w, glassBottom), layerPaint)
            drawRect(c, size = Size(w, glassBottom), alpha = a)
            // 김의 얼룩
            for (k in 0 until 5) {
                val x = w * (0.12f + 0.19f * k) + sin(sim.t * 0.3f + k * 1.7f) * w * 0.02f
                val y = sim.dashY * (0.3f + 0.12f * ((k * 3) % 4))
                glow(blobs, x, y, w * 0.16f, h * 0.2f, alpha = 0.08f * minOf(1f, f * 1.6f))
            }
            if (sim.wsFogGrowing) {
                if (open > 0.05f) punch(w * 0.5f, sim.dashY * 0.52f, w * 0.9f * (open - 0.05f), sim.dashY * 0.95f * (open - 0.05f))
            } else if (open > 0.01f) {
                // 걷히기 시작하면 송풍구 위에 작은 맑은 자리가 바로 생기고, 그 뒤로는 김이 줄어든 만큼만 넓어진다
                val k = 0.1f * minOf(1f, open * 8f) + 0.9f * open.pow(1.4f)
                punch(w * 0.3f, glassBottom, w * 0.58f * k, sim.dashY * 1.45f * k)
                punch(w * 0.7f, glassBottom, w * 0.58f * k, sim.dashY * 1.45f * k)
            }
            canvas.restore()
        }
    }

    private fun DrawScope.punch(x: Float, y: Float, rx: Float, ry: Float) {
        translate(x, y) { scale(rx, ry, Offset.Zero) { drawCircle(hole, radius = 1f, center = Offset.Zero, blendMode = BlendMode.DstOut) } }
    }

    private fun DrawScope.drawDebris(sim: SceneSim) {
        val d = sim.debris
        if (d < 0.01f) return
        val gone = 1f - d
        val cxp = w * (0.42f + 0.7f * gone * gone)
        val cyp = sim.dashY * (0.5f - 0.5f * gone * gone)
        val rad = minOf(w * 0.26f, sim.dashY * 0.42f)
        val a = minOf(1f, d * 2.2f)
        tmp.rewind()
        val n = 13
        for (i in 0 until n) {
            val ang = i * 2f * PI.toFloat() / n + gone * 2.4f
            val rr = rad * (0.86f + 0.16f * sin(i * 2.3f) + 0.04f * sin(sim.t * 5f + i * 1.3f))
            val x = cxp + cos(ang) * rr * 1.25f; val y = cyp + sin(ang) * rr
            if (i == 0) tmp.moveTo(x, y) else tmp.lineTo(x, y)
        }
        tmp.close()
        drawPath(tmp, Color(0xFFCBD6DE), alpha = 0.8f * a)
        drawPath(tmp, Color.White, alpha = 0.7f * a, style = Stroke(h * 0.004f))
        // 구김
        for (i in 0 until 4) {
            val ang = i * 0.9f + 0.4f + gone * 2.4f
            val wob = 0.06f * sin(sim.t * 4f + i)
            drawLine(
                Color(0xFF8E9AA6),
                Offset(cxp + cos(ang) * rad * 0.15f, cyp + sin(ang) * rad * 0.1f),
                Offset(cxp + cos(ang + wob) * rad * 0.95f, cyp + sin(ang + wob) * rad * 0.7f),
                h * 0.003f, alpha = 0.6f * a,
            )
            drawLine(
                Color.White,
                Offset(cxp + cos(ang + 0.3f) * rad * 0.2f, cyp + sin(ang + 0.3f) * rad * 0.2f),
                Offset(cxp + cos(ang + 0.35f + wob) * rad * 0.8f, cyp + sin(ang + 0.35f + wob) * rad * 0.6f),
                h * 0.0025f, alpha = 0.7f * a,
            )
        }
    }

    private fun DrawScope.drawWipers(sim: SceneSim) {
        val deg = SceneSim.WIPE_REST_DEG - sim.wipeA * (SceneSim.WIPE_REST_DEG - SceneSim.WIPE_END_DEG)
        val a = deg * PI.toFloat() / 180f
        val dx = cos(a); val dy = -sin(a)
        val l = sim.bladeL
        for (k in 0..1) {
            val px = if (k == 0) sim.pivAx else sim.pivBx
            val py = sim.pivY
            // 암: 뿌리가 굵고 끝으로 갈수록 가늘다
            val ex = px + dx * l * 0.6f; val ey = py + dy * l * 0.6f
            drawLine(Color(0xFF0B0E13), Offset(px, py), Offset(px + dx * l * 0.3f, py + dy * l * 0.3f), h * 0.016f, cap = StrokeCap.Round)
            drawLine(Color(0xFF0B0E13), Offset(px, py), Offset(ex, ey), h * 0.009f, cap = StrokeCap.Round)
            drawLine(Color(0xFF465263), Offset(px, py), Offset(ex, ey), h * 0.0018f, alpha = 0.7f)
            drawCircle(Color(0xFF151A22), h * 0.012f, Offset(px, py))
            // 블레이드: 고무 날과 그 위의 얇은 뼈대
            val x0 = px + dx * l * SceneSim.BLADE_INNER; val y0 = py + dy * l * SceneSim.BLADE_INNER
            val x1 = px + dx * l; val y1 = py + dy * l
            drawLine(Color(0xFF05070A), Offset(x0, y0), Offset(x1, y1), h * 0.0075f, cap = StrokeCap.Round)
            drawLine(Color(0xFF2B3440), Offset(x0, y0), Offset(x1, y1), h * 0.0022f, cap = StrokeCap.Round)
            drawCircle(Color(0xFF1B222C), h * 0.006f, Offset(ex, ey))
        }
    }

    private companion object {
        const val BOKEH = 12
    }
}
