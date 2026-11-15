package io.moodlab.sideseat.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.moodlab.sideseat.core.BuzzerPhase
import io.moodlab.sideseat.core.MiniGamePhase
import io.moodlab.sideseat.core.SafeDialView
import io.moodlab.sideseat.core.Seat
import io.moodlab.sideseat.core.TurnSignal
import io.moodlab.sideseat.core.TurnSignalGameView
import io.moodlab.sideseat.core.WindowBuzzerView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// 금고 다이얼은 어두운 금속 물체라 테마와 상관없이 같은 색으로 그린다
private val DialBlue = Color(0xFF5C9DFF)
private val DialGreen = Color(0xFF35D07F)

private fun sideWord(deg: Number): String = if (deg.toFloat() >= 0f) "오른쪽" else "왼쪽"

/** M1 금고 다이얼(정차 중). 핸들 각도대로 다이얼이 돌고, 번호에 가까워질수록 빛난다. */
@Composable
fun SafeDialScreen(view: SafeDialView, onAction: (UiAction) -> Unit) {
    SideSeatFrame { sz ->
        val info: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                Chip("미니게임", color = Sc.textDim, bg = Sc.surface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tabi(if (view.done) TabiMood.HAPPY else TabiMood.IDLE, size = sz.dp(88f))
                    Spacer(Modifier.width(sz.dp(14f)))
                    Text("금고 다이얼", color = Sc.text, fontSize = sz.display, fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(sz.dp(12f))) {
                    view.targetsDeg.forEachIndexed { i, _ ->
                        val solved = i < view.solved || view.done
                        Box(
                            Modifier.size(sz.dp(56f)).clip(RoundedCornerShape(sz.radius))
                                .background(if (solved) Sc.ok else Sc.surface)
                                .border(sz.dp(2f), if (i == view.solved && !view.done) Sc.text else Sc.outline, RoundedCornerShape(sz.radius)),
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", color = if (solved) Sc.onAccent else Sc.textDim, fontSize = sz.label, fontWeight = FontWeight.Medium) }
                    }
                }
                val target = view.targetsDeg.getOrNull(view.solved)
                if (view.done) {
                    Text("열렸어요!", color = Sc.ok, fontSize = sz.display, fontWeight = FontWeight.SemiBold)
                    Text("${SituationTexts.seconds(view.elapsedMs)} 걸렸어요", color = Sc.text, fontSize = sz.h2)
                } else if (target != null) {
                    Text("핸들을 ${sideWord(target)}으로", color = Sc.textDim, fontSize = sz.h2, fontWeight = FontWeight.SemiBold)
                    Text("${abs(target)}°", color = Sc.text, fontSize = sz.sp(120f), fontWeight = FontWeight.SemiBold)
                    Text(
                        "지금 ${sideWord(view.currentDeg)} ${abs(view.currentDeg).roundToInt()}° · 맞는 자리에서 잠깐 멈춰요",
                        color = Sc.text, fontSize = sz.label,
                    )
                }
                if (sz.wide) Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
                    if (view.done) BigButton("한 번 더", Modifier.weight(1f)) { onAction(UiAction.OpenSafeDial) }
                    BigButton("닫기", Modifier.weight(1f), primary = view.done) { onAction(UiAction.CloseMiniGame) }
                }
            }
        }
        if (sz.wide) {
            Row(Modifier.fillMaxSize().padding(sz.dp(40f)), horizontalArrangement = Arrangement.spacedBy(sz.dp(40f)), verticalAlignment = Alignment.CenterVertically) {
                info(Modifier.weight(0.4f).fillMaxHeight())
                Box(Modifier.weight(0.6f).fillMaxHeight(), contentAlignment = Alignment.Center) { Dial(view, Modifier.fillMaxHeight().aspectRatio(1f)) }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(sz.pad), verticalArrangement = Arrangement.spacedBy(sz.gap), horizontalAlignment = Alignment.CenterHorizontally) {
                Dial(view, Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true))
                info(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Dial(view: SafeDialView, modifier: Modifier) {
    val angle by animateFloatAsState(view.currentDeg, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium), label = "dial")
    val close by animateFloatAsState(if (view.done) 1f else view.closeness, tween(120), label = "close")
    val open by animateFloatAsState(if (view.done) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow), label = "open")
    val pulse by rememberInfiniteTransition(label = "dialPulse").animateFloat(0.75f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "p")
    Box(
        modifier.drawWithCache {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val door = Brush.radialGradient(listOf(Color(0xFF2B3546), Color(0xFF151B27), Color(0xFF0A0E16)), c, r)
            val knob = Brush.radialGradient(listOf(Color(0xFF3A4660), Color(0xFF1B2333)), c, r * 0.56f)
            val marker = Path().apply {
                moveTo(c.x, c.y - r * 0.8f); lineTo(c.x - r * 0.045f, c.y - r * 0.9f); lineTo(c.x + r * 0.045f, c.y - r * 0.9f); close()
            }
            onDrawBehind {
                val glowC = lerp(DialBlue, DialGreen, close)
                // 빛: 가까워질수록 세진다
                drawCircle(Brush.radialGradient(listOf(glowC.copy(alpha = 0.55f * close * pulse), Color.Transparent), c, r * 1.05f), r * 1.05f, c)
                drawCircle(door, r * 0.94f, c)
                drawCircle(Color(0xFF3A465C), r * 0.94f, c, style = Stroke(r * 0.012f))
                for (i in 0 until 12) {
                    val a = i * PI.toFloat() / 6f
                    drawCircle(Color(0xFF3F4B62), r * 0.022f, Offset(c.x + cos(a) * r * 0.885f, c.y + sin(a) * r * 0.885f))
                }
                // 눈금 (위가 0°, 오른쪽이 +)
                for (i in -18..18) {
                    val major = i % 3 == 0
                    rotate(i * 10f, c) {
                        drawLine(
                            if (major) Color(0xFFF5F5F5) else Color(0xFFA6A6A6), Offset(c.x, c.y - r * 0.78f), Offset(c.x, c.y - r * (if (major) 0.7f else 0.74f)),
                            r * (if (major) 0.012f else 0.006f), cap = StrokeCap.Round, alpha = if (major) 0.9f else 0.5f,
                        )
                    }
                }
                // 번호 자리
                view.targetsDeg.forEachIndexed { i, t ->
                    val solved = i < view.solved || view.done
                    if (solved || i == view.solved) rotate(t.toFloat(), c) {
                        drawPath(marker, if (solved) DialGreen else glowC, alpha = if (solved) 0.7f else 1f)
                        if (!solved) drawCircle(glowC, r * 0.09f * (0.6f + 0.4f * pulse), Offset(c.x, c.y - r * 0.86f), alpha = 0.25f + 0.4f * close)
                    }
                }
                // 맞은 자리에서 멈춘 정도
                if (view.holdProgress > 0.01f && !view.done) {
                    drawArc(
                        DialGreen, -90f, 360f * view.holdProgress, false, Offset(c.x - r * 0.62f, c.y - r * 0.62f), Size(r * 1.24f, r * 1.24f),
                        style = Stroke(r * 0.03f, cap = StrokeCap.Round),
                    )
                }
                // 다이얼
                val kr = r * 0.56f * (1f - 0.12f * open)
                drawCircle(Color.Black, kr * 1.05f, Offset(c.x, c.y + r * 0.02f), alpha = 0.5f)
                drawCircle(knob, kr, c)
                drawCircle(glowC, kr, c, style = Stroke(r * 0.014f), alpha = 0.35f + 0.65f * close)
                rotate(angle, c) {
                    for (i in 0 until 3) rotate(i * 120f, c) {
                        drawLine(Color(0xFF0C1019), c, Offset(c.x, c.y + kr * 0.82f), r * 0.07f, cap = StrokeCap.Round)
                        drawLine(Color(0xFF55627C), c, Offset(c.x, c.y + kr * 0.82f), r * 0.012f, cap = StrokeCap.Round)
                    }
                    drawLine(glowC, Offset(c.x, c.y - kr * 0.55f), Offset(c.x, c.y - kr * 0.98f), r * 0.03f, cap = StrokeCap.Round)
                    drawCircle(glowC, r * 0.035f, Offset(c.x, c.y - kr * 1.12f))
                }
                drawCircle(Color(0xFF0C1019), kr * 0.26f, c)
                drawCircle(glowC, kr * 0.1f, c, alpha = 0.5f + 0.5f * close)
                // 열릴 때 퍼지는 빛
                if (open > 0.01f) {
                    for (i in 0 until 16) rotate(i * 22.5f, c) {
                        drawLine(DialGreen, Offset(c.x, c.y - r * 0.6f), Offset(c.x, c.y - r * (0.6f + 0.38f * open)), r * 0.012f, cap = StrokeCap.Round, alpha = (1.2f - open).coerceIn(0f, 1f) * 0.8f + 0.2f)
                    }
                }
            }
        },
    )
}

private fun arrowPath(p: Path, s: Float, left: Boolean) {
    val d = if (left) -1f else 1f
    val cx = s / 2f; val cy = s / 2f
    p.rewind()
    p.moveTo(cx + d * s * 0.34f, cy); p.lineTo(cx, cy - s * 0.3f); p.lineTo(cx, cy - s * 0.13f)
    p.lineTo(cx - d * s * 0.32f, cy - s * 0.13f); p.lineTo(cx - d * s * 0.32f, cy + s * 0.13f); p.lineTo(cx, cy + s * 0.13f)
    p.lineTo(cx, cy + s * 0.3f); p.close()
}

/** M2 깜빡이 모스(정차 중). 화면에 나온 좌·우 순서를 방향지시등으로 따라 한다. */
@Composable
fun TurnSignalGameScreen(view: TurnSignalGameView, onAction: (UiAction) -> Unit) {
    SideSeatFrame { sz ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(sz.dp(40f)),
            verticalArrangement = Arrangement.spacedBy(sz.gap), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Chip("미니게임 · ${view.round}라운드", color = Sc.textDim, bg = Sc.surface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                    Tabi(TabiMood.IDLE, size = sz.dp(88f))
                    Spacer(Modifier.width(sz.dp(14f)))
                    Text("깜빡이 모스", color = Sc.text, fontSize = sz.display, fontWeight = FontWeight.SemiBold)
                }
            Text(
                when (view.phase) {
                    MiniGamePhase.SHOW -> "순서를 잘 보세요"
                    MiniGamePhase.INPUT -> "방향지시등으로 따라 해요"
                    MiniGamePhase.ROUND_CLEAR -> "좋아요! 한 칸 더 길어져요"
                    MiniGamePhase.OVER -> "아쉬워요. ${view.score}라운드까지 갔어요"
                    MiniGamePhase.DONE -> "다 맞혔어요!"
                },
                color = when (view.phase) { MiniGamePhase.OVER -> Sc.warn; MiniGamePhase.DONE, MiniGamePhase.ROUND_CLEAR -> Sc.ok; else -> Sc.textDim },
                fontSize = sz.h2, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
            val perRow = if (sz.wide) 8 else 4
            view.pattern.withIndex().chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(sz.dp(12f))) {
                    row.forEach { (i, signal) ->
                        val showing = view.phase == MiniGamePhase.SHOW && i == view.showingIndex
                        val entered = view.phase != MiniGamePhase.SHOW && i < view.inputCount
                        val hidden = view.phase == MiniGamePhase.INPUT && i >= view.inputCount
                        val lit by animateFloatAsState(if (showing || entered) 1f else 0f, tween(140), label = "lit")
                        val tile = sz.dp(if (sz.wide) 120f else 150f)
                        Canvas(
                            Modifier.size(tile).clip(RoundedCornerShape(sz.radius))
                                .background(lerp(Sc.surface, if (entered) Sc.ok.copy(alpha = 0.25f) else Sc.accent.copy(alpha = 0.3f), lit))
                                .border(sz.dp(2f), if (view.phase == MiniGamePhase.INPUT && i == view.inputCount) Sc.text else Sc.outline, RoundedCornerShape(sz.radius)),
                        ) {
                            if (hidden) {
                                drawCircle(Sc.outline, size.minDimension * 0.08f)
                            } else {
                                val p = Path(); arrowPath(p, size.minDimension, signal == TurnSignal.LEFT)
                                drawPath(p, lerp(Sc.textDim, if (entered) Sc.ok else Sc.accent, lit))
                            }
                        }
                    }
                }
            }
            if (view.phase == MiniGamePhase.INPUT && view.inputLimitMs > 0) {
                ProgressBar(1f - view.inputElapsedMs / view.inputLimitMs.toFloat(), Modifier.fillMaxWidth(0.6f), color = Sc.warn)
            }
            Spacer(Modifier.height(sz.gap))
            Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
                if (view.phase == MiniGamePhase.OVER || view.phase == MiniGamePhase.DONE) {
                    BigButton("한 번 더", Modifier.width(sz.dp(240f))) { onAction(UiAction.OpenTurnSignalGame) }
                }
                BigButton("닫기", Modifier.width(sz.dp(240f)), primary = view.phase == MiniGamePhase.DONE) { onAction(UiAction.CloseMiniGame) }
            }
        }
    }
}

private val seatLabels = mapOf(
    Seat.DRIVER to "운전석", Seat.PASSENGER to "조수석", Seat.REAR_LEFT to "뒤 왼쪽", Seat.REAR_CENTER to "뒤 가운데", Seat.REAR_RIGHT to "뒤 오른쪽",
)

/** M3 창문 버저 퀴즈(정차 중). 자기 자리 창문 스위치를 먼저 누른 사람이 답한다. */
@Composable
fun WindowBuzzerScreen(view: WindowBuzzerView, onAction: (UiAction) -> Unit) {
    SideSeatFrame { sz ->
        val question: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                Chip("창문 버저 퀴즈 · ${view.questionIndex + 1} / ${view.questionCount}", color = Sc.textDim, bg = Sc.surface)
                Text(
                    if (view.phase == BuzzerPhase.DONE) "퀴즈 끝!" else view.question, color = Sc.text, fontSize = sz.title,
                    fontWeight = FontWeight.SemiBold, lineHeight = sz.title * 1.25f, maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (view.phase) {
                        BuzzerPhase.ASK -> "아는 사람은 자기 창문 스위치를 눌러요"
                        BuzzerPhase.BUZZED -> "${seatLabels[view.buzzedSeat] ?: ""} 차례예요. 말하거나 골라요"
                        BuzzerPhase.REVEAL -> if (view.lastCorrect == true) "정답이에요!" else "아쉬워요"
                        BuzzerPhase.DONE -> "점수를 확인해 봐요"
                    },
                    color = when { view.phase == BuzzerPhase.REVEAL && view.lastCorrect == true -> Sc.ok; view.phase == BuzzerPhase.REVEAL -> Sc.warn; else -> Sc.textDim },
                    fontSize = sz.h2, fontWeight = FontWeight.SemiBold,
                )
                if (view.phase != BuzzerPhase.DONE) view.options.forEachIndexed { i, option ->
                    BigButton("${i + 1}. $option", Modifier.fillMaxWidth(), enabled = view.phase == BuzzerPhase.BUZZED) { onAction(UiAction.Answer(option)) }
                }
                BigButton("닫기", Modifier.fillMaxWidth(), primary = view.phase == BuzzerPhase.DONE) { onAction(UiAction.CloseMiniGame) }
            }
        }
        val seats: @Composable (Modifier) -> Unit = { m ->
            Column(m.clip(RoundedCornerShape(sz.radius)).background(Sc.surface).padding(sz.pad), verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                Text("자리별 점수", color = Sc.textDim, fontSize = sz.label, fontWeight = FontWeight.Medium)
                listOf(listOf(Seat.DRIVER, Seat.PASSENGER), listOf(Seat.REAR_LEFT, Seat.REAR_CENTER, Seat.REAR_RIGHT)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
                        row.forEach { seat ->
                            val buzzed = view.buzzedSeat == seat && view.phase != BuzzerPhase.ASK
                            val shape = RoundedCornerShape(sz.radius)
                            Column(
                                Modifier.weight(1f).clip(shape).background(if (buzzed) Sc.accent.copy(alpha = 0.22f) else Sc.surfaceHi)
                                    .border(sz.dp(2f), if (buzzed) Sc.accent else Sc.outline, shape).padding(sz.dp(16f)),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(seatLabels[seat] ?: seat.name, color = Sc.textDim, fontSize = sz.caption, maxLines = 1)
                                Text("${view.scores[seat] ?: 0}", color = if (buzzed) Sc.accent else Sc.text, fontSize = sz.title, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        if (sz.wide) {
            Row(Modifier.fillMaxSize().padding(sz.dp(40f)), horizontalArrangement = Arrangement.spacedBy(sz.dp(40f))) {
                question(Modifier.weight(0.55f).fillMaxHeight().verticalScroll(rememberScrollState()))
                seats(Modifier.weight(0.45f))
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(sz.pad), verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                question(Modifier.fillMaxWidth()); seats(Modifier.fillMaxWidth())
            }
        }
    }
}
