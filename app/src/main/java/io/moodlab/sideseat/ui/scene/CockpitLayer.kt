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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import io.moodlab.sideseat.core.TurnSignal
import kotlin.math.sin

/**
 * 운전석 틀: 앞유리 테두리(A필러·지붕·룸미러), 대시보드, 디지털 계기판, 핸들 윗부분, 실내 조명선, 졸음 눈꺼풀.
 * 계기판은 실제 차처럼 켜진 표시등만 보인다.
 */
internal class CockpitLayer {
    private var w = 0f
    private var h = 0f
    private val dash = Path()
    private val dashEdge = Path()
    private val pillarL = Path()
    private val pillarR = Path()
    private val roof = Path()
    private val mirror = Path()
    private var dashBrush: Brush? = null
    private var fritBottom: Brush? = null
    private var fritTop: Brush? = null
    private var lidTop: Brush? = null
    private var lidBottom: Brush? = null
    private var screenBrush: Brush? = null
    private var mirrorBrush: Brush? = null

    // 속도 숫자는 값마다 한 번만 재서 담아 둔다(프레임마다 글자를 다시 재지 않는다)
    private val speedText = arrayOfNulls<TextLayoutResult>(300)
    private var unitText: TextLayoutResult? = null

    private fun ensure(size: Size) {
        if (size.width == w && size.height == h) return
        w = size.width; h = size.height
        val dy = h * SceneSim.DASH_Y
        val cx = w / 2f
        val hood = minOf(w * 0.25f, h * 0.38f)
        dashEdge.rewind()
        dashEdge.moveTo(-2f, dy + h * 0.03f)
        dashEdge.cubicTo(w * 0.12f, dy + h * 0.014f, cx - hood * 1.6f, dy + h * 0.014f, cx - hood, dy - h * 0.002f)
        dashEdge.cubicTo(cx - hood * 0.6f, dy - h * 0.024f, cx + hood * 0.6f, dy - h * 0.024f, cx + hood, dy - h * 0.002f)
        dashEdge.cubicTo(cx + hood * 1.6f, dy + h * 0.014f, w * 0.88f, dy + h * 0.014f, w + 2f, dy + h * 0.026f)
        dash.rewind(); dash.addPath(dashEdge)
        dash.lineTo(w + 2f, h + 2f); dash.lineTo(-2f, h + 2f); dash.close()
        dashBrush = Brush.verticalGradient(0f to Color(0xFF17191C), 0.18f to Color(0xFF0D0E10), 1f to Color(0xFF040405), startY = dy - h * 0.025f, endY = h)
        // 앞유리 아래·위의 검은 테두리(유리 가장자리의 세라믹 띠)
        fritBottom = Brush.verticalGradient(0f to Color(0x00000000), 1f to Color(0xB3000000), startY = dy - h * 0.045f, endY = dy + h * 0.02f)
        fritTop = Brush.verticalGradient(0f to Color(0xCC000000), 1f to Color(0x00000000), startY = h * 0.03f, endY = h * 0.1f)
        screenBrush = Brush.verticalGradient(0f to Color(0xFF020203), 1f to Color(0xFF08090B), startY = h * 0.8f, endY = h * 0.96f)

        pillarL.rewind()
        pillarL.moveTo(-2f, -2f); pillarL.lineTo(w * 0.1f, -2f); pillarL.lineTo(w * 0.024f, dy + h * 0.04f); pillarL.lineTo(-2f, dy + h * 0.04f); pillarL.close()
        pillarR.rewind()
        pillarR.moveTo(w + 2f, -2f); pillarR.lineTo(w * 0.93f, -2f); pillarR.lineTo(w * 0.984f, dy + h * 0.04f); pillarR.lineTo(w + 2f, dy + h * 0.04f); pillarR.close()
        roof.rewind()
        roof.moveTo(-2f, -2f); roof.lineTo(w + 2f, -2f); roof.lineTo(w + 2f, h * 0.026f)
        roof.quadraticBezierTo(cx, h * 0.056f, -2f, h * 0.026f); roof.close()

        // 룸미러: 아래가 조금 좁은 둥근 사다리꼴
        val mw = minOf(w * 0.15f, h * 0.27f); val mh = mw * 0.26f
        val mx = cx + w * 0.02f; val my = h * 0.04f
        mirror.rewind()
        mirror.moveTo(mx + mh * 0.25f, my)
        mirror.lineTo(mx + mw - mh * 0.25f, my)
        mirror.quadraticBezierTo(mx + mw, my, mx + mw - mh * 0.08f, my + mh * 0.5f)
        mirror.quadraticBezierTo(mx + mw - mh * 0.2f, my + mh, mx + mw - mh * 0.6f, my + mh)
        mirror.lineTo(mx + mh * 0.6f, my + mh)
        mirror.quadraticBezierTo(mx + mh * 0.2f, my + mh, mx + mh * 0.08f, my + mh * 0.5f)
        mirror.quadraticBezierTo(mx, my, mx + mh * 0.25f, my)
        mirror.close()
        mirrorBrush = Brush.verticalGradient(0f to Color(0xFF14181E), 1f to Color(0xFF07090C), startY = my, endY = my + mh)

        lidTop = Brush.verticalGradient(0f to Color(0xFF000000), 0.75f to Color(0xF2000000), 1f to Color(0x00000000), startY = 0f, endY = h * 0.6f)
        lidBottom = Brush.verticalGradient(0f to Color(0x00000000), 0.25f to Color(0xF2000000), 1f to Color(0xFF000000), startY = 0f, endY = h * 0.6f)
        speedText.fill(null); unitText = null
    }

    fun draw(scope: DrawScope, sim: SceneSim, input: SceneInput, measurer: TextMeasurer) = with(scope) {
        ensure(size)
        val cx = w / 2f
        val dy = h * SceneSim.DASH_Y
        // 유리 가장자리 그늘, 지붕, 필러, 룸미러
        drawRect(fritTop!!, topLeft = Offset(0f, h * 0.03f), size = Size(w, h * 0.07f))
        drawRect(fritBottom!!, topLeft = Offset(0f, dy - h * 0.045f), size = Size(w, h * 0.065f))
        drawPath(roof, Color(0xFF060708))
        drawPath(pillarL, Color(0xFF08090A)); drawPath(pillarR, Color(0xFF08090A))
        drawLine(Color.White, Offset(w * 0.1f, 0f), Offset(w * 0.024f, dy + h * 0.04f), h * 0.0015f, alpha = 0.09f)
        drawLine(Color.White, Offset(w * 0.93f, 0f), Offset(w * 0.984f, dy + h * 0.04f), h * 0.0015f, alpha = 0.09f)
        val mw = minOf(w * 0.15f, h * 0.27f)
        val mx = cx + w * 0.02f
        drawLine(Color(0xFF060708), Offset(mx + mw / 2f, 0f), Offset(mx + mw / 2f, h * 0.05f), h * 0.014f)
        drawPath(mirror, Color(0xFF050607), style = Stroke(h * 0.006f))
        drawPath(mirror, mirrorBrush!!)

        // 대시보드와 그 윗선을 따라 흐르는 실내 조명
        drawPath(dash, dashBrush!!)
        val pulse = 0.75f + 0.25f * sin(sim.t * 5f)
        val moodC: Color; val moodA: Float
        when {
            sim.success > 0.3f -> { moodC = Color(0xFF52E08C); moodA = sim.success * 0.9f }
            sim.alert > 0.05f -> { moodC = Color(0xFFFF3B3B); moodA = sim.alert * pulse }
            else -> { moodC = Color(0xFFDDE6F5); moodA = 0.22f }
        }
        drawPath(dashEdge, moodC, alpha = moodA * 0.16f, style = Stroke(h * 0.016f))
        drawPath(dashEdge, moodC, alpha = (moodA * 0.95f).coerceAtMost(1f), style = Stroke(h * 0.0026f))

        drawCluster(sim, input, cx, measurer)
        drawWheel(sim, cx)

        // 경고등 상황: 계기판에 뜬 경고등을 앞에 크게 띄워 보여 준다
        if (sim.warn > 0.02f) {
            val s = h * 0.07f
            val wy = h * 0.4f
            val blink = 0.85f + 0.15f * sin(sim.t * 4f)
            val amberC = Color(0xFFFFC02A)
            drawRoundRect(Color(0xFF07080A), Offset(cx - s * 2.1f, wy - s * 2.1f), Size(s * 4.2f, s * 4.2f), CornerRadius(s * 0.5f), alpha = 0.9f * sim.warn)
            drawRoundRect(Color.White, Offset(cx - s * 2.1f, wy - s * 2.1f), Size(s * 4.2f, s * 4.2f), CornerRadius(s * 0.5f), alpha = 0.1f * sim.warn, style = Stroke(h * 0.0016f))
            val key = input.warningLight
            if (key == null || key.contains("TPMS") || key.contains("TIRE")) {
                tpmsIcon(cx, wy, s * 1.15f, amberC.copy(alpha = sim.warn * blink))
            } else {
                // 그 밖의 경고등: 느낌표 삼각형
                val c = amberC.copy(alpha = sim.warn * blink)
                val st = s * 0.2f
                drawLine(c, Offset(cx, wy - s), Offset(cx - s * 1.05f, wy + s * 0.8f), st, cap = StrokeCap.Round)
                drawLine(c, Offset(cx, wy - s), Offset(cx + s * 1.05f, wy + s * 0.8f), st, cap = StrokeCap.Round)
                drawLine(c, Offset(cx - s * 1.05f, wy + s * 0.8f), Offset(cx + s * 1.05f, wy + s * 0.8f), st, cap = StrokeCap.Round)
                drawLine(c, Offset(cx, wy - s * 0.35f), Offset(cx, wy + s * 0.2f), st, cap = StrokeCap.Round)
                drawCircle(c, st * 0.6f, Offset(cx, wy + s * 0.5f))
            }
        }

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

    private fun DrawScope.drawCluster(sim: SceneSim, input: SceneInput, cx: Float, measurer: TextMeasurer) {
        val cw = minOf(w * 0.34f, h * 0.58f)
        val top = h * 0.806f
        val ch = h * 0.15f
        val left = cx - cw / 2f
        val rr = CornerRadius(h * 0.012f)
        drawRoundRect(screenBrush!!, Offset(left, top), Size(cw, ch), rr)
        drawRoundRect(Color.White, Offset(left, top), Size(cw, ch), rr, alpha = 0.07f, style = Stroke(h * 0.0014f))

        // 속도
        val kmh = (sim.speed * 3.6f + 0.5f).toInt().coerceIn(0, 299)
        val hot = sim.alert > 0.5f && kmh > 120
        val digits = speedText[kmh] ?: measurer.measure(
            kmh.toString(), TextStyle(fontSize = (h * 0.074f).toSp(), fontWeight = FontWeight.Normal, letterSpacing = (-h * 0.001f).toSp()),
        ).also { speedText[kmh] = it }
        val unit = unitText ?: measurer.measure("km/h", TextStyle(fontSize = (h * 0.02f).toSp(), fontWeight = FontWeight.Medium)).also { unitText = it }
        val dTop = top + ch * 0.07f
        drawText(digits, if (hot) Color(0xFFFF6A63) else Color(0xFFF3F5F7), Offset(cx - digits.size.width / 2f, dTop))
        drawText(unit, Color(0xFF8B9299), Offset(cx - unit.size.width / 2f, dTop + digits.size.height * 0.9f))

        // 방향지시등 (켜진 쪽만 깜빡인다)
        val blinkOn = (sim.t * 1.4f) % 1f < 0.5f
        val green = Color(0xFF35D07F)
        val ay = top + ch * 0.36f
        val ax = cw * 0.37f
        val arrow = h * 0.017f
        if (blinkOn && (input.turn == TurnSignal.LEFT || input.turn == TurnSignal.HAZARD)) turnArrow(cx - ax, ay, arrow, green, -1)
        if (blinkOn && (input.turn == TurnSignal.RIGHT || input.turn == TurnSignal.HAZARD)) turnArrow(cx + ax, ay, arrow, green, 1)

        // 표시등: 자리는 정해져 있고 켜진 것만 보인다
        val iy = top + ch * 0.82f
        val s = h * 0.0135f
        val stepX = cw * 0.84f / 6f
        val x0 = cx - cw * 0.42f
        if (sim.lowBeam > 0.5f && sim.highBeam < 0.5f) beamIcon(x0, iy, s, green, dipped = true)
        if (sim.highBeam > 0.5f) beamIcon(x0 + stepX, iy, s, Color(0xFF4B8DFF), dipped = false)
        if (sim.fogLamp > 0.5f) fogIcon(x0 + stepX * 2, iy, s, green)
        if (input.turn == TurnSignal.HAZARD && blinkOn) hazardIcon(x0 + stepX * 3, iy, s, Color(0xFFFF4A45))
        if (input.defrost) defrostIcon(x0 + stepX * 4, iy, s, Color(0xFFF6B21B))
        if (sim.warn > 0.5f) tpmsIcon(x0 + stepX * 5, iy, s, Color(0xFFF6B21B))
        if (input.parkingBrake) parkIcon(x0 + stepX * 6, iy, s, Color(0xFFFF4A45))
    }

    private fun DrawScope.drawWheel(sim: SceneSim, cx: Float) {
        val r = minOf(h * 0.39f, w * 0.31f)
        val cy = h * 0.742f + r
        val rim = r * 0.1f
        val mid = r - rim / 2f
        // 핸들 테: 계기판에 떨어진 그림자, 가죽 테, 위쪽에 얹힌 은은한 빛
        drawCircle(Color.Black, mid, Offset(cx, cy + rim * 0.35f), alpha = 0.35f, style = Stroke(rim * 1.2f))
        drawCircle(Color(0xFF0B0C0E), mid, Offset(cx, cy), style = Stroke(rim))
        drawCircle(Color(0xFF1B1D21), mid + rim * 0.2f, Offset(cx, cy), style = Stroke(rim * 0.36f))
        drawCircle(Color.White, mid + rim * 0.36f, Offset(cx, cy), alpha = 0.08f, style = Stroke(rim * 0.06f))
        rotate(sim.steerVis, Offset(cx, cy)) {
            // 12시 표시(핸들을 돌린 만큼 같이 돈다)
            drawArc(Color(0xFF7C838B), -90.7f, 1.4f, false, Offset(cx - mid, cy - mid), Size(2 * mid, 2 * mid), style = Stroke(rim * 0.7f))
            val hub = r * 0.3f
            val sw = rim * 1.5f
            drawLine(Color(0xFF0B0C0E), Offset(cx - r + rim, cy - r * 0.04f), Offset(cx - hub, cy), sw, cap = StrokeCap.Round)
            drawLine(Color(0xFF0B0C0E), Offset(cx + r - rim, cy - r * 0.04f), Offset(cx + hub, cy), sw, cap = StrokeCap.Round)
            drawLine(Color(0xFF0B0C0E), Offset(cx, cy + r - rim), Offset(cx, cy + hub), sw * 1.2f, cap = StrokeCap.Round)
            drawCircle(Color(0xFF0D0E11), hub * 1.25f, Offset(cx, cy))
        }
    }
}
