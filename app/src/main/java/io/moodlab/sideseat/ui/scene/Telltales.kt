package io.moodlab.sideseat.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

// 계기판 표시등 그림. 차에서 쓰는 표준 기호의 모양을 선으로 그린다. (x, y)가 가운데, s가 반 크기.
// 계기판(주행 장면)과 코치 화면이 같이 쓴다.

/** 전조등: 왼쪽으로 뻗는 빛줄기와 오른쪽의 등. [dipped]면 하향등(빛줄기가 아래로 기운다). */
internal fun DrawScope.beamIcon(x: Float, y: Float, s: Float, c: Color, dipped: Boolean) {
    val st = s * 0.17f
    drawArc(c, -90f, 180f, false, Offset(x - s * 0.35f, y - s * 0.68f), Size(s * 1.25f, s * 1.36f), style = Stroke(st))
    drawLine(c, Offset(x + s * 0.27f, y - s * 0.68f), Offset(x + s * 0.27f, y + s * 0.68f), st, cap = StrokeCap.Round)
    for (i in -1..1) {
        val yy = y + i * s * 0.44f
        drawLine(c, Offset(x - s * 0.08f, yy), Offset(x - s * 0.95f, yy + if (dipped) s * 0.26f else 0f), st, cap = StrokeCap.Round)
    }
}

/** 안개등: 전조등 빛줄기를 물결 모양 세로선이 가로지른다. */
internal fun DrawScope.fogIcon(x: Float, y: Float, s: Float, c: Color) {
    beamIcon(x, y, s, c, dipped = false)
    val st = s * 0.15f
    drawLine(c, Offset(x - s * 0.45f, y - s * 0.8f), Offset(x - s * 0.6f, y - s * 0.27f), st, cap = StrokeCap.Round)
    drawLine(c, Offset(x - s * 0.6f, y - s * 0.27f), Offset(x - s * 0.45f, y + s * 0.27f), st, cap = StrokeCap.Round)
    drawLine(c, Offset(x - s * 0.45f, y + s * 0.27f), Offset(x - s * 0.6f, y + s * 0.8f), st, cap = StrokeCap.Round)
}

/** 비상등: 겹친 삼각형. */
internal fun DrawScope.hazardIcon(x: Float, y: Float, s: Float, c: Color) {
    val st = s * 0.15f
    for (k in 0..1) {
        val r = if (k == 0) s * 0.95f else s * 0.45f
        val top = Offset(x, y - r * 0.85f + (if (k == 1) s * 0.14f else 0f))
        val l = Offset(x - r * 0.95f, y + r * 0.72f + (if (k == 1) s * 0.14f else 0f))
        val rt = Offset(x + r * 0.95f, l.y)
        drawLine(c, top, l, st, cap = StrokeCap.Round); drawLine(c, l, rt, st, cap = StrokeCap.Round); drawLine(c, rt, top, st, cap = StrokeCap.Round)
    }
}

/** 앞유리 성에 제거: 부채꼴 유리와 위로 오르는 바람 세 줄. */
internal fun DrawScope.defrostIcon(x: Float, y: Float, s: Float, c: Color) {
    val st = s * 0.16f
    drawArc(c, 205f, 130f, false, Offset(x - s * 1.05f, y - s * 0.75f), Size(s * 2.1f, s * 1.7f), style = Stroke(st, cap = StrokeCap.Round))
    drawLine(c, Offset(x - s * 0.95f, y - s * 0.26f), Offset(x - s * 0.72f, y + s * 0.6f), st, cap = StrokeCap.Round)
    drawLine(c, Offset(x + s * 0.95f, y - s * 0.26f), Offset(x + s * 0.72f, y + s * 0.6f), st, cap = StrokeCap.Round)
    drawLine(c, Offset(x - s * 0.72f, y + s * 0.6f), Offset(x + s * 0.72f, y + s * 0.6f), st, cap = StrokeCap.Round)
    for (i in -1..1) {
        val xx = x + i * s * 0.42f
        drawLine(c, Offset(xx, y + s * 0.85f), Offset(xx + s * 0.1f, y + s * 0.35f), st * 0.85f, cap = StrokeCap.Round)
        drawLine(c, Offset(xx + s * 0.1f, y + s * 0.35f), Offset(xx - s * 0.02f, y - s * 0.12f), st * 0.85f, cap = StrokeCap.Round)
    }
}

/** 타이어 공기압: 타이어 단면과 느낌표. */
internal fun DrawScope.tpmsIcon(x: Float, y: Float, s: Float, c: Color) {
    val st = s * 0.17f
    drawArc(c, -55f, 290f, false, Offset(x - s * 0.82f, y - s * 0.9f), Size(s * 1.64f, s * 1.64f), style = Stroke(st, cap = StrokeCap.Round))
    drawLine(c, Offset(x, y - s * 0.5f), Offset(x, y + s * 0.02f), st, cap = StrokeCap.Round)
    drawCircle(c, st * 0.62f, Offset(x, y + s * 0.34f))
    drawLine(c, Offset(x - s * 0.62f, y + s * 0.9f), Offset(x + s * 0.62f, y + s * 0.9f), st * 0.9f, cap = StrokeCap.Round)
}

/** 주차 브레이크: 동그라미 안의 P와 양옆 괄호. */
internal fun DrawScope.parkIcon(x: Float, y: Float, s: Float, c: Color) {
    val st = s * 0.16f
    drawCircle(c, s * 0.68f, Offset(x, y), style = Stroke(st))
    drawArc(c, 140f, 80f, false, Offset(x - s * 1.05f, y - s * 1.05f), Size(s * 2.1f, s * 2.1f), style = Stroke(st, cap = StrokeCap.Round))
    drawArc(c, -40f, 80f, false, Offset(x - s * 1.05f, y - s * 1.05f), Size(s * 2.1f, s * 2.1f), style = Stroke(st, cap = StrokeCap.Round))
    drawLine(c, Offset(x - s * 0.18f, y - s * 0.36f), Offset(x - s * 0.18f, y + s * 0.4f), st, cap = StrokeCap.Round)
    drawArc(c, -90f, 180f, false, Offset(x - s * 0.42f, y - s * 0.36f), Size(s * 0.52f, s * 0.42f), style = Stroke(st))
}

/** 와이퍼: 부채꼴 유리와 비스듬한 와이퍼 팔. */
internal fun DrawScope.wiperIcon(x: Float, y: Float, s: Float, c: Color) {
    val st = s * 0.16f
    drawArc(c, 205f, 130f, false, Offset(x - s * 1.05f, y - s * 0.75f), Size(s * 2.1f, s * 1.7f), style = Stroke(st, cap = StrokeCap.Round))
    drawLine(c, Offset(x - s * 0.95f, y - s * 0.26f), Offset(x - s * 0.72f, y + s * 0.6f), st, cap = StrokeCap.Round)
    drawLine(c, Offset(x + s * 0.95f, y - s * 0.26f), Offset(x + s * 0.72f, y + s * 0.6f), st, cap = StrokeCap.Round)
    drawLine(c, Offset(x - s * 0.72f, y + s * 0.6f), Offset(x + s * 0.72f, y + s * 0.6f), st, cap = StrokeCap.Round)
    drawLine(c, Offset(x + s * 0.05f, y + s * 0.6f), Offset(x - s * 0.5f, y - s * 0.38f), st, cap = StrokeCap.Round)
    drawCircle(c, st * 0.75f, Offset(x + s * 0.05f, y + s * 0.6f))
}

/** 공기 순환: 차 옆모습. [inside]면 안에서 도는 화살표(내기), 아니면 밖에서 들어오는 화살표(외기). */
internal fun DrawScope.airIcon(x: Float, y: Float, s: Float, c: Color, inside: Boolean) {
    val st = s * 0.16f
    // 차 옆모습: 낮은 보닛에서 지붕으로 올라갔다 내려온다
    val base = y + s * 0.62f
    drawLine(c, Offset(x - s, base), Offset(x + s, base), st, cap = StrokeCap.Round)
    drawLine(c, Offset(x - s, base), Offset(x - s * 0.95f, y + s * 0.08f), st, cap = StrokeCap.Round)
    drawLine(c, Offset(x - s * 0.95f, y + s * 0.08f), Offset(x - s * 0.5f, y - s * 0.05f), st, cap = StrokeCap.Round)
    drawLine(c, Offset(x - s * 0.5f, y - s * 0.05f), Offset(x - s * 0.2f, y - s * 0.62f), st, cap = StrokeCap.Round)
    drawLine(c, Offset(x - s * 0.2f, y - s * 0.62f), Offset(x + s * 0.6f, y - s * 0.62f), st, cap = StrokeCap.Round)
    drawLine(c, Offset(x + s * 0.6f, y - s * 0.62f), Offset(x + s, y + s * 0.02f), st, cap = StrokeCap.Round)
    drawLine(c, Offset(x + s, y + s * 0.02f), Offset(x + s, base), st, cap = StrokeCap.Round)
    if (inside) {
        drawArc(c, 20f, 300f, false, Offset(x - s * 0.02f, y - s * 0.3f), Size(s * 0.62f, s * 0.62f), style = Stroke(st * 0.85f, cap = StrokeCap.Round))
        drawLine(c, Offset(x + s * 0.6f, y - s * 0.1f), Offset(x + s * 0.62f, y + s * 0.14f), st * 0.85f, cap = StrokeCap.Round)
        drawLine(c, Offset(x + s * 0.62f, y + s * 0.14f), Offset(x + s * 0.4f, y + s * 0.1f), st * 0.85f, cap = StrokeCap.Round)
    } else {
        drawLine(c, Offset(x - s * 1.35f, y - s * 0.75f), Offset(x + s * 0.2f, y + s * 0.2f), st * 0.85f, cap = StrokeCap.Round)
        drawLine(c, Offset(x + s * 0.2f, y + s * 0.2f), Offset(x - s * 0.14f, y + s * 0.2f), st * 0.85f, cap = StrokeCap.Round)
        drawLine(c, Offset(x + s * 0.2f, y + s * 0.2f), Offset(x + s * 0.12f, y - s * 0.12f), st * 0.85f, cap = StrokeCap.Round)
    }
}

/** 방향지시등 화살표. [dir] -1 왼쪽, +1 오른쪽. */
internal fun DrawScope.turnArrow(x: Float, y: Float, s: Float, c: Color, dir: Int) {
    val st = s * 0.34f
    val tip = x + dir * s
    drawLine(c, Offset(x - dir * s * 0.9f, y), Offset(tip - dir * s * 0.2f, y), st, cap = StrokeCap.Round)
    drawLine(c, Offset(tip, y), Offset(tip - dir * s * 0.75f, y - s * 0.72f), st, cap = StrokeCap.Round)
    drawLine(c, Offset(tip, y), Offset(tip - dir * s * 0.75f, y + s * 0.72f), st, cap = StrokeCap.Round)
}
