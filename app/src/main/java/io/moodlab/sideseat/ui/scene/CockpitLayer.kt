package io.moodlab.sideseat.ui.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import io.moodlab.sideseat.core.TurnSignal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 운전석 틀: A필러, 지붕, 룸미러, 대시보드, 계기판, 핸들, 실내 조명 띠, 졸음 눈꺼풀. */
internal class CockpitLayer {
    private var w = 0f
    private var h = 0f
    private val dash = Path()
    private val dashEdge = Path()
    private val pillarL = Path()
    private val pillarR = Path()
    private val roof = Path()
    private val arrow = Path()
    private val tri = Path()
    private var dashBrush: Brush? = null
    private var lidTop: Brush? = null
    private var lidBottom: Brush? = null
    private var clusterBrush: Brush? = null
    private val red = GlowBrush(Color(0xFFFF2A3C))
    private val green = GlowBrush(Color(0xFF3DF08A))
    private val calm = GlowBrush(Color(0xFFBFD4FF))

    private fun ensure(size: Size) {
        if (size.width == w && size.height == h) return
        w = size.width; h = size.height
        val dy = h * SceneSim.DASH_Y
        val cx = w / 2f
        val hood = minOf(w * 0.24f, h * 0.36f)
        dashEdge.rewind()
        dashEdge.moveTo(-2f, dy + h * 0.035f)
        dashEdge.cubicTo(w * 0.12f, dy + h * 0.012f, cx - hood * 1.5f, dy + h * 0.012f, cx - hood, dy - h * 0.004f)
        dashEdge.cubicTo(cx - hood * 0.6f, dy - h * 0.03f, cx + hood * 0.6f, dy - h * 0.03f, cx + hood, dy - h * 0.004f)
        dashEdge.cubicTo(cx + hood * 1.5f, dy + h * 0.012f, w * 0.88f, dy + h * 0.012f, w + 2f, dy + h * 0.03f)
        dash.rewind(); dash.addPath(dashEdge)
        dash.lineTo(w + 2f, h + 2f); dash.lineTo(-2f, h + 2f); dash.close()
        dashBrush = Brush.verticalGradient(0f to Color(0xFF151922), 0.25f to Color(0xFF0C0F15), 1f to Color(0xFF050608), startY = dy - h * 0.03f, endY = h)
        clusterBrush = Brush.verticalGradient(0f to Color(0xFF05070B), 1f to Color(0xFF0C1119), startY = h * 0.79f, endY = h)

        pillarL.rewind()
        pillarL.moveTo(-2f, -2f); pillarL.lineTo(w * 0.105f, -2f); pillarL.lineTo(w * 0.028f, dy + h * 0.04f); pillarL.lineTo(-2f, dy + h * 0.04f); pillarL.close()
        pillarR.rewind()
        pillarR.moveTo(w + 2f, -2f); pillarR.lineTo(w * 0.925f, -2f); pillarR.lineTo(w * 0.982f, dy + h * 0.04f); pillarR.lineTo(w + 2f, dy + h * 0.04f); pillarR.close()
        roof.rewind()
        roof.moveTo(-2f, -2f); roof.lineTo(w + 2f, -2f); roof.lineTo(w + 2f, h * 0.03f)
        roof.quadraticBezierTo(cx, h * 0.062f, -2f, h * 0.03f); roof.close()

        val s = h * 0.03f
        arrow.rewind()
        arrow.moveTo(-s, 0f); arrow.lineTo(0f, -s * 0.85f); arrow.lineTo(0f, -s * 0.38f); arrow.lineTo(s, -s * 0.38f)
        arrow.lineTo(s, s * 0.38f); arrow.lineTo(0f, s * 0.38f); arrow.lineTo(0f, s * 0.85f); arrow.close()
        tri.rewind(); tri.moveTo(0f, -s * 0.62f); tri.lineTo(s * 0.7f, s * 0.55f); tri.lineTo(-s * 0.7f, s * 0.55f); tri.close()

        lidTop = Brush.verticalGradient(0f to Color(0xFF000000), 0.75f to Color(0xF2000000), 1f to Color(0x00000000), startY = 0f, endY = h * 0.6f)
        lidBottom = Brush.verticalGradient(0f to Color(0x00000000), 0.25f to Color(0xF2000000), 1f to Color(0xFF000000), startY = 0f, endY = h * 0.6f)
    }

    fun draw(scope: DrawScope, sim: SceneSim, input: SceneInput) = with(scope) {
        ensure(size)
        val cx = w / 2f
        // 지붕, 필러, 룸미러
        drawPath(roof, Color(0xFF07080C))
        drawPath(pillarL, Color(0xFF090B10)); drawPath(pillarR, Color(0xFF090B10))
        drawLine(Color(0x33FFFFFF), Offset(w * 0.105f, 0f), Offset(w * 0.028f, h * SceneSim.DASH_Y + h * 0.04f), h * 0.002f)
        val mw = minOf(w * 0.17f, h * 0.3f); val mh = mw * 0.27f
        val mx = cx + w * 0.02f; val my = h * 0.036f
        drawLine(Color(0xFF07080C), Offset(mx + mw / 2f, 0f), Offset(mx + mw / 2f, my + mh * 0.2f), h * 0.012f)
        drawRoundRect(Color(0xFF07080C), Offset(mx, my), Size(mw, mh), CornerRadius(mh * 0.3f))
        drawRoundRect(Color(0xFF141B2B), Offset(mx + mh * 0.1f, my + mh * 0.1f), Size(mw - mh * 0.2f, mh * 0.8f), CornerRadius(mh * 0.24f))
        drawLine(Color(0x44FF4A5A), Offset(mx + mw * 0.3f, my + mh * 0.62f), Offset(mx + mw * 0.36f, my + mh * 0.62f), mh * 0.08f, cap = StrokeCap.Round)
        drawLine(Color(0x44FF4A5A), Offset(mx + mw * 0.6f, my + mh * 0.62f), Offset(mx + mw * 0.66f, my + mh * 0.62f), mh * 0.08f, cap = StrokeCap.Round)

        // 대시보드와 실내 조명 띠
        drawPath(dash, dashBrush!!)
        val pulse = 0.65f + 0.35f * sin(sim.t * 7f)
        val moodC: Color; val moodA: Float
        when {
            sim.success > 0.3f -> { moodC = Color(0xFF3DF08A); moodA = sim.success }
            sim.alert > 0.05f -> { moodC = Color(0xFFFF2A3C); moodA = sim.alert * pulse }
            else -> { moodC = Color(0xFFBFD4FF); moodA = 0.32f }
        }
        drawPath(dashEdge, moodC, alpha = moodA * 0.22f, style = Stroke(h * 0.03f))
        drawPath(dashEdge, moodC, alpha = moodA * 0.5f, style = Stroke(h * 0.012f))
        drawPath(dashEdge, moodC, alpha = (moodA * 1.2f).coerceAtMost(1f), style = Stroke(h * 0.0035f))
        val g = if (sim.success > 0.3f) green else if (sim.alert > 0.05f) red else calm
        glow(g, cx, h * 0.76f, w * 0.6f, h * 0.1f, alpha = moodA * 0.25f)

        drawCluster(sim, input, cx)
        drawWheel(sim, cx)

        // 졸음: 눈꺼풀이 내려온다
        if (sim.drowsy > 0.01f) {
            val d = sim.drowsy
            val blink = 0.75f + 0.25f * sin(sim.t * 0.9f)
            val lid = h * 0.6f
            drawRect(Color.Black, alpha = d * 0.3f)
            translate(0f, -lid + lid * 0.62f * d * blink) { drawRect(lidTop!!, size = Size(w, lid)) }
            translate(0f, h - lid * 0.5f * d * blink) { drawRect(lidBottom!!, size = Size(w, lid)) }
        }
    }

    private fun DrawScope.drawCluster(sim: SceneSim, input: SceneInput, cx: Float) {
        val cw = minOf(w * 0.36f, h * 0.6f)
        val top = h * 0.805f
        val ch = h * 0.15f
        val left = cx - cw / 2f
        drawRoundRect(clusterBrush!!, Offset(left, top), Size(cw, ch), CornerRadius(h * 0.02f))
        drawRoundRect(Color(0x3353D8FF), Offset(left, top), Size(cw, ch), CornerRadius(h * 0.02f), style = Stroke(h * 0.0018f))

        // 속도 (7세그먼트)
        val kmh = (sim.speed * 3.6f + 0.5f).toInt().coerceIn(0, 299)
        val dh = h * 0.062f; val dw = dh * 0.5f; val gap = dw * 0.36f
        val total = 3 * dw + 2 * gap
        val dx0 = cx - total / 2f
        val dy0 = top + h * 0.02f
        val hot = sim.alert > 0.5f && kmh > 120
        val segOn = if (hot) Color(0xFFFF5A66) else Color(0xFFE8FBFF)
        val d2 = kmh / 100; val d1 = kmh / 10 % 10; val d0 = kmh % 10
        sevenSeg(dx0, dy0, dw, dh, if (d2 == 0) -1 else d2, segOn)
        sevenSeg(dx0 + dw + gap, dy0, dw, dh, if (d2 == 0 && d1 == 0) -1 else d1, segOn)
        sevenSeg(dx0 + 2 * (dw + gap), dy0, dw, dh, d0, segOn)
        // 속도 막대
        val bw = cw * 0.5f; val by = dy0 + dh + h * 0.012f
        drawLine(Color(0x33FFFFFF), Offset(cx - bw / 2f, by), Offset(cx + bw / 2f, by), h * 0.004f, cap = StrokeCap.Round)
        val frac = (kmh / 200f).coerceIn(0f, 1f)
        if (frac > 0.005f) {
            drawLine(if (hot) Color(0xFFFF5A66) else Color(0xFF53D8FF), Offset(cx - bw / 2f, by), Offset(cx - bw / 2f + bw * frac, by), h * 0.004f, cap = StrokeCap.Round)
        }

        // 방향지시등
        val blinkOn = (sim.t * 1.4f) % 1f < 0.5f
        val leftOn = blinkOn && (input.turn == TurnSignal.LEFT || input.turn == TurnSignal.HAZARD)
        val rightOn = blinkOn && (input.turn == TurnSignal.RIGHT || input.turn == TurnSignal.HAZARD)
        val ay = top + ch * 0.36f
        val ax = cw * 0.36f
        translate(cx - ax, ay) { drawPath(arrow, if (leftOn) Color(0xFF3DF08A) else Color(0xFF1A2420)) }
        translate(cx + ax, ay) { rotate(180f, Offset.Zero) { drawPath(arrow, if (rightOn) Color(0xFF3DF08A) else Color(0xFF1A2420)) } }
        if (leftOn) glow(green, cx - ax, ay, h * 0.07f, alpha = 0.6f)
        if (rightOn) glow(green, cx + ax, ay, h * 0.07f, alpha = 0.6f)

        // 표시등 줄
        val iy = top + ch * 0.8f
        val s = h * 0.019f
        val n = 7
        val stepX = cw * 0.86f / (n - 1)
        val x0 = cx - cw * 0.43f
        val off = Color(0xFF1B222C)
        beamIcon(x0, iy, s, if (sim.lowBeam > 0.5f && sim.highBeam < 0.5f) Color(0xFF3DF08A) else off, slant = true)
        beamIcon(x0 + stepX, iy, s, if (sim.highBeam > 0.5f) Color(0xFF4D8DFF) else off, slant = false)
        fogIcon(x0 + stepX * 2, iy, s, if (sim.fogLamp > 0.5f) Color(0xFF3DF08A) else off)
        // 비상등 삼각형
        val hazard = input.turn == TurnSignal.HAZARD
        translate(x0 + stepX * 3, iy) { drawPath(tri, if (hazard && blinkOn) Color(0xFFFF2A3C) else off, style = Stroke(s * 0.2f)) }
        if (hazard && blinkOn) glow(red, x0 + stepX * 3, iy, s * 2.6f, alpha = 0.55f)
        defrostIcon(x0 + stepX * 4, iy, s, if (input.defrost) Color(0xFFFFB02E) else off)
        tpmsIcon(x0 + stepX * 5, iy, s, if (sim.warn > 0.5f && blinkOn) Color(0xFFFFC21A) else if (sim.warn > 0.5f) Color(0xFF8A6A10) else off)
        parkIcon(x0 + stepX * 6, iy, s, if (input.parkingBrake) Color(0xFFFF2A3C) else off)
        if (input.brake) glow(red, cx, top + ch, cw * 0.4f, h * 0.02f, alpha = 0.5f)
    }

    private fun DrawScope.sevenSeg(x: Float, y: Float, dw: Float, dh: Float, digit: Int, on: Color) {
        val bits = if (digit < 0) 0 else SEGS[digit]
        val t = dh * 0.11f
        val m = y + dh / 2f
        val offC = Color(0x14FFFFFF)
        fun seg(i: Int, x0: Float, y0: Float, x1: Float, y1: Float) {
            drawLine(if (bits shr i and 1 == 1) on else offC, Offset(x0, y0), Offset(x1, y1), t, cap = StrokeCap.Round)
        }
        val p = t * 0.75f
        seg(0, x + p, y, x + dw - p, y)
        seg(1, x + dw, y + p, x + dw, m - p)
        seg(2, x + dw, m + p, x + dw, y + dh - p)
        seg(3, x + p, y + dh, x + dw - p, y + dh)
        seg(4, x, m + p, x, y + dh - p)
        seg(5, x, y + p, x, m - p)
        seg(6, x + p, m, x + dw - p, m)
    }

    private fun DrawScope.beamIcon(x: Float, y: Float, s: Float, c: Color, slant: Boolean) {
        val st = s * 0.2f
        drawArc(c, -90f, 180f, false, Offset(x - s * 0.15f, y - s * 0.7f), Size(s * 1.1f, s * 1.4f), style = Stroke(st))
        drawLine(c, Offset(x + s * 0.4f, y - s * 0.7f), Offset(x + s * 0.4f, y + s * 0.7f), st)
        for (i in -1..1) {
            val yy = y + i * s * 0.45f
            drawLine(c, Offset(x + s * 0.1f, yy), Offset(x - s * 0.9f, yy + if (slant) s * 0.28f else 0f), st, cap = StrokeCap.Round)
        }
    }

    private fun DrawScope.fogIcon(x: Float, y: Float, s: Float, c: Color) {
        beamIcon(x, y, s, c, slant = false)
        drawLine(c, Offset(x - s * 0.4f, y - s * 0.8f), Offset(x - s * 0.4f, y + s * 0.8f), s * 0.18f, cap = StrokeCap.Round)
    }

    private fun DrawScope.defrostIcon(x: Float, y: Float, s: Float, c: Color) {
        val st = s * 0.2f
        drawArc(c, 200f, 140f, false, Offset(x - s, y - s * 0.7f), Size(s * 2f, s * 1.6f), style = Stroke(st))
        drawLine(c, Offset(x - s * 0.85f, y + s * 0.55f), Offset(x + s * 0.85f, y + s * 0.55f), st, cap = StrokeCap.Round)
        for (i in -1..1) {
            val xx = x + i * s * 0.5f
            drawLine(c, Offset(xx, y + s * 0.35f), Offset(xx, y - s * 0.3f), st * 0.9f, cap = StrokeCap.Round)
        }
    }

    private fun DrawScope.tpmsIcon(x: Float, y: Float, s: Float, c: Color) {
        val st = s * 0.2f
        drawArc(c, -50f, 280f, false, Offset(x - s * 0.8f, y - s * 0.8f), Size(s * 1.6f, s * 1.6f), style = Stroke(st))
        drawLine(c, Offset(x, y - s * 0.4f), Offset(x, y + s * 0.1f), st, cap = StrokeCap.Round)
        drawCircle(c, st * 0.6f, Offset(x, y + s * 0.42f))
        drawLine(c, Offset(x - s * 0.7f, y + s * 0.95f), Offset(x + s * 0.7f, y + s * 0.95f), st * 0.8f, cap = StrokeCap.Round)
    }

    private fun DrawScope.parkIcon(x: Float, y: Float, s: Float, c: Color) {
        val st = s * 0.2f
        drawCircle(c, s * 0.8f, Offset(x, y), style = Stroke(st))
        drawLine(c, Offset(x - s * 0.22f, y - s * 0.42f), Offset(x - s * 0.22f, y + s * 0.45f), st, cap = StrokeCap.Round)
        drawArc(c, -90f, 180f, false, Offset(x - s * 0.5f, y - s * 0.42f), Size(s * 0.6f, s * 0.46f), style = Stroke(st))
    }

    private fun DrawScope.drawWheel(sim: SceneSim, cx: Float) {
        val r = minOf(h * 0.37f, w * 0.3f)
        val cy = h * 0.73f + r
        val rim = r * 0.115f
        val rot = sim.steerVis
        drawCircle(Color(0xFF08090D), r - rim / 2f, Offset(cx, cy), style = Stroke(rim))
        drawCircle(Color(0x30FFFFFF), r - rim * 0.12f, Offset(cx, cy), style = Stroke(rim * 0.07f))
        drawCircle(Color(0x18FFFFFF), r - rim * 0.9f, Offset(cx, cy), style = Stroke(rim * 0.05f))
        rotate(rot, Offset(cx, cy)) {
            // 12시 표시
            drawArc(Color(0xFF3AA8C8), -92.2f, 4.4f, false, Offset(cx - r + rim / 2f, cy - r + rim / 2f), Size(2 * r - rim, 2 * r - rim), style = Stroke(rim * 1.02f))
            // 살
            val hub = r * 0.3f
            val sw = rim * 1.5f
            drawLine(Color(0xFF0B0D12), Offset(cx - r + rim, cy - r * 0.04f), Offset(cx - hub, cy), sw, cap = StrokeCap.Round)
            drawLine(Color(0xFF0B0D12), Offset(cx + r - rim, cy - r * 0.04f), Offset(cx + hub, cy), sw, cap = StrokeCap.Round)
            drawLine(Color(0xFF0B0D12), Offset(cx, cy + r - rim), Offset(cx, cy + hub), sw * 1.2f, cap = StrokeCap.Round)
            drawCircle(Color(0xFF0D1016), hub * 1.25f, Offset(cx, cy))
            drawCircle(Color(0x2253D8FF), hub * 1.25f, Offset(cx, cy), style = Stroke(rim * 0.06f))
        }
    }

    private companion object {
        // 비트: 0 위, 1 오른쪽 위, 2 오른쪽 아래, 3 아래, 4 왼쪽 아래, 5 왼쪽 위, 6 가운데
        val SEGS = intArrayOf(0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F)
    }
}
