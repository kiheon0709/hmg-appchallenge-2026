package io.moodlab.sideseat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 반투명 판. 주행 화면 위에 얹어도 글자 대비가 유지된다. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Sc.scrim,
    border: Color = Sc.outline,
    padding: Dp = LocalSz.current.dp(22f),
    content: @Composable ColumnScope.() -> Unit,
) {
    val sz = LocalSz.current
    val shape = RoundedCornerShape(sz.radius)
    Column(modifier.clip(shape).background(color).border(sz.line, border, shape).padding(padding), content = content)
}

@Composable
fun BigButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    color: Color = Sc.primary,
    sub: String? = null,
    onClick: () -> Unit,
) {
    val sz = LocalSz.current
    val shape = RoundedCornerShape(sz.radius)
    // 시스템 선택 칸과 같은 모양: 주 버튼은 검은(야간은 흰) 채움, 나머지는 바탕색에 테두리
    val bg = when { !enabled -> Sc.surface; primary -> color; else -> Sc.bg }
    val fg = when { !enabled -> Sc.textDim; primary -> if (color == Color.White) Color.Black else if (color == Sc.primary) Sc.onPrimary else Sc.onAccent; else -> Sc.text }
    Column(
        modifier.defaultMinSize(minHeight = sz.touch).clip(shape).background(bg)
            .border(sz.line, if (primary) Color.Transparent else if (!enabled) Sc.outline else Sc.text, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = sz.dp(24f), vertical = sz.dp(12f)),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = fg, fontSize = sz.label, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, color = fg.copy(alpha = 0.8f), fontSize = sz.caption, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, color: Color = Sc.text, bg: Color = Sc.scrim, size: TextUnit = LocalSz.current.caption) {
    val sz = LocalSz.current
    Text(
        text, color = color, fontSize = size, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(sz.radius)).background(bg).padding(horizontal = sz.dp(12f), vertical = sz.dp(5f)),
    )
}

private fun starPath(p: Path, cx: Float, cy: Float, r: Float) {
    p.rewind()
    for (i in 0 until 10) {
        val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
        val rr = if (i % 2 == 0) r else r * 0.46f
        val x = cx + cos(a) * rr; val y = cy + sin(a) * rr
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
}

/** 별 0~3개. [animateKey]가 바뀌면 하나씩 튀어나온다. */
@Composable
fun Stars(count: Int, modifier: Modifier = Modifier, max: Int = 3, size: Dp = LocalSz.current.dp(44f), animateKey: Any? = null) {
    val pops = remember(animateKey, count) { List(max) { Animatable(if (animateKey == null) 1f else 0f) } }
    if (animateKey != null) {
        LaunchedEffect(animateKey, count) {
            for (i in 0 until count.coerceAtMost(max)) {
                pops[i].animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    val path = remember { Path() }
    Canvas(modifier.size(size * max + size * 0.18f * (max - 1), size)) {
        val s = size.toPx()
        for (i in 0 until max) {
            val cx = s / 2f + i * s * 1.18f
            val on = i < count
            val k = if (on) pops[i].value else 1f
            starPath(path, cx, s / 2f, s * 0.48f * (if (on) 0.4f + 0.6f * k else 1f))
            if (on) {
                if (k > 0.01f) {
                    drawPath(path, Sc.star, alpha = k.coerceIn(0f, 1f))
                }
            } else {
                drawPath(path, Sc.outline, style = Stroke(s * 0.06f))
            }
        }
    }
}

/**
 * 화면 가장자리 빛 띠. 실내 조명(앰비언트)과 같은 색을 화면에도 보여 준다.
 * 에뮬레이터는 조명 변화를 보여 주지 않아서 시연에서 이 띠가 조명을 대신한다.
 */
@Composable
fun EdgeGlow(color: Color, intensity: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().drawWithCache {
            val t = minOf(size.width, size.height) * 0.065f
            val c0 = color; val c1 = color.copy(alpha = 0f)
            val top = Brush.verticalGradient(listOf(c0, c1), 0f, t)
            val bottom = Brush.verticalGradient(listOf(c1, c0), size.height - t, size.height)
            val left = Brush.horizontalGradient(listOf(c0, c1), 0f, t)
            val right = Brush.horizontalGradient(listOf(c1, c0), size.width - t, size.width)
            onDrawBehind {
                val a = intensity().coerceIn(0f, 1f)
                if (a > 0.01f) {
                    drawRect(top, size = Size(size.width, t), alpha = a)
                    drawRect(bottom, topLeft = Offset(0f, size.height - t), size = Size(size.width, t), alpha = a)
                    drawRect(left, size = Size(t, size.height), alpha = a)
                    drawRect(right, topLeft = Offset(size.width - t, 0f), size = Size(t, size.height), alpha = a)
                    drawRect(color, style = Stroke(t * 0.1f), alpha = a)
                }
            }
        },
    )
}

/** 남은 시간을 보여 주는 고리. [fraction] 0 = 막 시작, 1 = 시간 끝. */
@Composable
fun TimerRing(fraction: Float, label: String, modifier: Modifier = Modifier, size: Dp = LocalSz.current.dp(132f), caption: String? = null) {
    val sz = LocalSz.current
    val f = fraction.coerceIn(0f, 1f)
    val c = when { f > 0.8f -> Sc.danger; f > 0.5f -> Sc.warn; else -> Sc.text }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = this.size.minDimension * 0.09f
            val inset = st / 2f
            val arc = Size(this.size.width - st, this.size.height - st)
            drawArc(Sc.outline, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(st))
            drawArc(c, -90f, 360f * (1f - f), false, Offset(inset, inset), arc, style = Stroke(st, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Sc.text, fontSize = sz.h2, fontWeight = FontWeight.Medium)
            if (caption != null) Text(caption, color = Sc.textDim, fontSize = sz.caption)
        }
    }
}

/** 진행 막대. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Sc.text) {
    val sz = LocalSz.current
    Box(modifier.height(sz.dp(12f)).clip(RoundedCornerShape(50)).background(Sc.outline)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(sz.dp(12f)).clip(RoundedCornerShape(50)).background(color))
    }
}

internal fun DrawScope.micGlyph(c: Color, cx: Float, cy: Float, s: Float) {
    val st = s * 0.12f
    drawRoundRect(c, Offset(cx - s * 0.2f, cy - s * 0.55f), Size(s * 0.4f, s * 0.7f), androidx.compose.ui.geometry.CornerRadius(s * 0.2f))
    drawArc(c, 0f, 180f, false, Offset(cx - s * 0.36f, cy - s * 0.35f), Size(s * 0.72f, s * 0.7f), style = Stroke(st, cap = StrokeCap.Round))
    drawLine(c, Offset(cx, cy + s * 0.35f), Offset(cx, cy + s * 0.58f), st, cap = StrokeCap.Round)
    drawLine(c, Offset(cx - s * 0.2f, cy + s * 0.58f), Offset(cx + s * 0.2f, cy + s * 0.58f), st, cap = StrokeCap.Round)
}

/**
 * 큰 "질문하기" 버튼과 대화 자막. 홈·코치·리뷰가 같이 쓴다.
 * 움직이는 효과가 없어서 주행 화면에도 그대로 쓸 수 있다.
 */
@Composable
fun TalkButton(talk: TalkUi, modifier: Modifier = Modifier, height: Dp = LocalSz.current.dp(120f), onAction: (UiAction) -> Unit) {
    val sz = LocalSz.current
    val active = talk.listening || talk.speaking || talk.thinking
    val label = when {
        talk.listening -> "듣고 있어요"
        talk.thinking -> "잠깐만요"
        talk.speaking -> "눌러서 멈추기"
        else -> "질문하기"
    }
    val bg = when { talk.listening -> Sc.accent; active -> Sc.bg; else -> Sc.primary }
    val fg = when { talk.listening -> Sc.onAccent; active -> Sc.text; else -> Sc.onPrimary }
    val shape = RoundedCornerShape(sz.radius)
    Row(
        modifier.height(height).clip(shape).background(bg)
            .border(sz.line, if (active && !talk.listening) Sc.text else Color.Transparent, shape)
            .clickable { onAction(if (active) UiAction.StopTalking else UiAction.AskQuestion) }
            .padding(horizontal = sz.dp(20f)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(height * 0.42f)) { micGlyph(fg, size.width / 2f, size.height / 2f, size.minDimension * 0.8f) }
        Spacer(Modifier.width(sz.dp(18f)))
        // 좁은 버튼에서도 글자가 잘리지 않게 긴 문구는 한 단계 작게 쓴다
        Text(
            label, color = fg, fontSize = if (label.length > 5) sz.body else sz.h2, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** 듣는 중의 중간 결과와 선배의 답 자막. */
@Composable
fun TalkCaption(talk: TalkUi, modifier: Modifier = Modifier, maxLines: Int = 4) {
    val sz = LocalSz.current
    val heard = talk.partialText?.takeIf { it.isNotBlank() }
    val said = talk.answerText?.takeIf { it.isNotBlank() }
    if (heard == null && said == null) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(sz.dp(8f))) {
        if (heard != null) Text("“$heard”", color = Sc.accent, fontSize = sz.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (said != null) Text(said, color = Sc.text, fontSize = sz.body, lineHeight = sz.body * 1.35f, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** 선배 얼굴 대신 쓰는 표식: 옆자리 좌석 모양의 둥근 아이콘. */
@Composable
fun SeniorBadge(modifier: Modifier = Modifier, size: Dp = LocalSz.current.dp(72f), speaking: Boolean = false) {
    Canvas(modifier.size(size).graphicsLayer { }) {
        val s = this.size.minDimension
        drawCircle(if (speaking) Sc.accent else Sc.primary, s / 2f)
        // 좌석: 등받이와 방석
        val c = if (speaking) Sc.onAccent else Sc.onPrimary
        drawRoundRect(c, Offset(s * 0.36f, s * 0.2f), Size(s * 0.24f, s * 0.42f), androidx.compose.ui.geometry.CornerRadius(s * 0.08f))
        drawRoundRect(c, Offset(s * 0.3f, s * 0.6f), Size(s * 0.42f, s * 0.14f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f))
        drawCircle(c, s * 0.07f, Offset(s * 0.48f, s * 0.14f + s * 0.02f))
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, delta: String? = null, color: Color = Sc.text) {
    val sz = LocalSz.current
    Column(modifier.clip(RoundedCornerShape(sz.radius)).background(Sc.surface).padding(sz.dp(18f))) {
        Text(value, color = color, fontSize = sz.title, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(label, color = Sc.textDim, fontSize = sz.caption, maxLines = 1)
        if (delta != null) Text(delta, color = Sc.textDim, fontSize = sz.caption, maxLines = 1)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}
