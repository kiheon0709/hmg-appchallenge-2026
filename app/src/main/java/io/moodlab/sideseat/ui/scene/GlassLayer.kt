package io.moodlab.sideseat.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 앞유리 층: 물막, 물방울, 김서림, 이물질, 와이퍼. */
internal class GlassLayer {
    private var w = 0f
    private var h = 0f
    private val wedgeA = Array(SceneSim.WIPE_BINS) { Path() }
    private val wedgeB = Array(SceneSim.WIPE_BINS) { Path() }
    private val unswept = Path()
    private val tmp = Path()
    private var fogEdgeUp: Brush? = null
    private var fogEdgeDown: Brush? = null
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

        val band = h * 0.16f
        // 김이 걷히는 경계: 위쪽이 뿌옇고 아래가 맑다 / 그 반대
        fogEdgeUp = Brush.verticalGradient(0f to Color(0xFFFFFFFF), 1f to Color(0x00FFFFFF), startY = -band, endY = 0f)
        fogEdgeDown = Brush.verticalGradient(0f to Color(0x00FFFFFF), 1f to Color(0xFFFFFFFF), startY = 0f, endY = band)
        var seed = 911
        fun r(): Float { seed = seed * 1103515245 + 12345; return ((seed ushr 8) and 0xFFFF) / 65535f }
        for (k in 0 until BOKEH) {
            bokehX[k] = r() * w; bokehY[k] = h * (0.2f + r() * 0.5f); bokehR[k] = h * (0.03f + r() * 0.06f)
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
        val maxA = 0.8f
        val un = sim.ambientWet
        if (un > 0.01f) drawPath(unswept, film, alpha = filmAlpha(un) * maxA)
        for (k in 0 until SceneSim.WIPE_BINS) {
            val a = filmAlpha(sim.wet[k]) * maxA
            if (a < 0.012f) continue
            drawPath(wedgeA[k], film, alpha = a)
            drawPath(wedgeB[k], film, alpha = a)
        }
        // 물막 너머로 번진 불빛
        var avg = 0f
        for (k in 0 until SceneSim.WIPE_BINS) avg += sim.wet[k]
        avg /= SceneSim.WIPE_BINS
        if (avg > 0.05f && sim.night > 0.3f) {
            for (k in 0 until BOKEH) {
                val g = when (k % 3) { 0 -> bokehWarm; 1 -> bokehRed; else -> bokehCool }
                val wob = sin(sim.t * 0.7f + k) * h * 0.01f
                glow(g, bokehX[k] + wob, bokehY[k], bokehR[k], alpha = avg * 0.22f * sim.night)
            }
        }
        // 물방울
        val hi = Color.White
        val lo = Color(0xFF03060A)
        val up = sim.speed * 0.0025f * h
        for (n in 0 until SceneSim.MAX_DROPS) {
            val a = sim.dropAlpha(n)
            if (a <= 0.02f) continue
            val r = sim.dropR[n]
            val age = sim.t - sim.dropBorn[n]
            val x = sim.dropX[n]
            val y = sim.dropY[n] - up * age * (r / (h * 0.02f))
            drawCircle(lo, r, Offset(x + r * 0.12f, y + r * 0.18f), alpha = 0.34f * a)
            drawCircle(hi, r * 0.92f, Offset(x, y), alpha = 0.085f * a)
            drawCircle(hi, r * 0.3f, Offset(x - r * 0.32f, y - r * 0.34f), alpha = 0.5f * a)
        }
    }

    private fun filmAlpha(wet: Float): Float = if (wet <= 0f) 0f else kotlin.math.sqrt(wet.coerceAtMost(1f))

    private fun DrawScope.drawFog(sim: SceneSim, glassBottom: Float) {
        val f = sim.wsFog
        if (f < 0.005f) return
        val c = mix(Color(0xFFE4E9EE), Color(0xFFB9C3CE), sim.night)
        val a = 0.93f
        val band = h * 0.16f
        val span = glassBottom + band
        if (sim.wsFogGrowing) {
            // 아래에서부터 뿌옇게 올라온다
            val line = glassBottom - f * span
            drawRect(c, Offset(0f, line + band), Size(w, (glassBottom - line - band).coerceAtLeast(0f)), alpha = a)
            translate(0f, line) { drawRect(fogEdgeDown!!, Offset(0f, 0f), Size(w, band), alpha = a, colorFilter = tint(c)) }
        } else {
            // 아래에서부터 걷힌다
            val line = glassBottom + band - (1f - f) * span
            drawRect(c, Offset(0f, 0f), Size(w, (line - band).coerceAtLeast(0f)), alpha = a)
            translate(0f, line) { drawRect(fogEdgeUp!!, Offset(0f, -band), Size(w, band), alpha = a, colorFilter = tint(c)) }
        }
        // 김의 얼룩
        val soft = minOf(1f, f * 1.6f)
        for (k in 0 until 5) {
            val x = w * (0.12f + 0.19f * k) + sin(sim.t * 0.3f + k * 1.7f) * w * 0.02f
            val y = sim.dashY * (0.3f + 0.12f * ((k * 3) % 4))
            glow(blobs, x, y, w * 0.16f, h * 0.2f, alpha = 0.1f * soft)
        }
    }

    private var tintColor = Color.Unspecified
    private var tintFilter: androidx.compose.ui.graphics.ColorFilter? = null
    private fun tint(c: Color): androidx.compose.ui.graphics.ColorFilter {
        if (c != tintColor || tintFilter == null) {
            tintColor = c
            tintFilter = androidx.compose.ui.graphics.ColorFilter.tint(c, androidx.compose.ui.graphics.BlendMode.Modulate)
        }
        return tintFilter!!
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
            // 팔
            drawLine(Color(0xFF0A0C10), Offset(px, py), Offset(px + dx * l * 0.62f, py + dy * l * 0.62f), h * 0.011f, cap = StrokeCap.Round)
            // 날
            val x0 = px + dx * l * SceneSim.BLADE_INNER; val y0 = py + dy * l * SceneSim.BLADE_INNER
            val x1 = px + dx * l; val y1 = py + dy * l
            drawLine(Color(0xFF040507), Offset(x0, y0), Offset(x1, y1), h * 0.009f, cap = StrokeCap.Round)
            drawLine(Color(0xFF3A4350), Offset(x0, y0), Offset(x1, y1), h * 0.002f, alpha = 0.8f)
        }
    }

    private companion object {
        const val BOKEH = 12
    }
}
