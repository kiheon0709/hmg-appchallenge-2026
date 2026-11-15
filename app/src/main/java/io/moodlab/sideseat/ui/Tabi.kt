package io.moodlab.sideseat.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 타비의 기분. 화면이 지금 상황에 맞는 것을 고른다. */
enum class TabiMood {
    /** 가만히: 천천히 위아래로 뜨고 가끔 눈을 깜빡인다. */
    IDLE,
    /** 말하는 중: 통통 튀고 팔을 흔든다. */
    SPEAKING,
    /** 듣는 중: 눈이 커지고 몸을 기울인다. */
    LISTENING,
    /** 기쁨(성공): 웃는 눈, 폴짝. */
    HAPPY,
    /** 놀람(위급): 눈이 커진다. */
    SURPRISED,
    /** 생각 중: 위를 보고, 생각 방울이 뜬다. */
    THINKING,
    /** 격려(실패 뒤): 팔을 들어 올린다. */
    CHEER,
}

/** 대화 상태에 맞는 기분. */
fun TalkUi.mood(): TabiMood = when {
    listening -> TabiMood.LISTENING
    thinking -> TabiMood.THINKING
    speaking -> TabiMood.SPEAKING
    else -> TabiMood.IDLE
}

/**
 * 타비 캐릭터. 코드로만 그린다: 둥글고 한쪽으로 살짝 기운 파란 몸, 머리 위의 작은 삐침, 세로로 긴 눈 둘, 옆으로 나온 짧은 팔 하나.
 * [animated]가 false면 그 기분의 한 장면으로 멈춰 있다(주행 중 화면, 훈련 장면 위).
 * 움직임은 그리기 단계에서만 읽어서 재구성 없이 이 작은 칸만 다시 그린다.
 */
@Composable
fun Tabi(mood: TabiMood = TabiMood.IDLE, modifier: Modifier = Modifier, size: Dp = LocalSz.current.dp(72f), animated: Boolean = true) {
    val phase = if (animated) {
        rememberInfiniteTransition(label = "tabi").animateFloat(0f, 1f, infiniteRepeatable(tween(3_600, easing = LinearEasing)), label = "phase")
    } else {
        null
    }
    val path = remember { Path() }
    Canvas(modifier.size(size)) { drawTabi(mood, phase?.value, path) }
}

private val Body = Color(0xFF2F6BF2)
private val Eye = Color(0xFF0B1533)

/** @param p 움직임의 위상(0~1 반복). null이면 멈춘 한 장면. */
private fun DrawScope.drawTabi(mood: TabiMood, p: Float?, path: Path) {
    val s = size.minDimension
    val moving = p != null
    val t = (p ?: 0f) * 2f * PI.toFloat()
    // 위로 뜨는 높이(몸 크기에 대한 비율)
    val lift = when (mood) {
        TabiMood.SPEAKING -> if (moving) abs(sin(t * 4f)) * 0.035f else 0.02f
        TabiMood.HAPPY -> if (moving) abs(sin(t * 3f)) * 0.08f else 0.06f
        TabiMood.SURPRISED -> 0.03f
        else -> if (moving) (sin(t) * 0.5f + 0.5f) * 0.03f else 0f
    }
    val tilt = when (mood) {
        TabiMood.LISTENING -> 8f
        TabiMood.THINKING -> -6f
        TabiMood.HAPPY -> if (moving) sin(t * 3f) * 4f else 3f
        else -> 0f
    }
    val blink = moving && mood != TabiMood.HAPPY && mood != TabiMood.SURPRISED && p!! in 0.90f..0.935f

    // 바닥 그림자: 뜨면 작아진다
    val shadowW = s * (0.46f - lift * 2f)
    drawOval(Color.Black.copy(alpha = 0.16f), Offset(s * 0.5f - shadowW / 2f, s * 0.9f), Size(shadowW, s * 0.055f))

    translate(top = -lift * s) {
        rotate(tilt, Offset(s * 0.5f, s * 0.84f)) {
            // 팔: 몸 오른쪽에서 짧게 나온다. 몸보다 먼저 그려서 뿌리가 가려진다
            val armDeg = when (mood) {
                TabiMood.SPEAKING -> if (moving) -25f + 28f * sin(t * 4f) else -35f
                TabiMood.HAPPY -> -62f
                TabiMood.CHEER -> if (moving) -68f + 8f * sin(t * 3f) else -68f
                TabiMood.SURPRISED -> -45f
                TabiMood.LISTENING -> -20f
                else -> 22f
            }
            val a = armDeg * PI.toFloat() / 180f
            val from = Offset(s * 0.8f, s * 0.57f)
            drawLine(Body, from, Offset(from.x + cos(a) * s * 0.17f, from.y + sin(a) * s * 0.17f), s * 0.085f, cap = StrokeCap.Round)

            // 몸: 한쪽이 조금 더 부푼 물방울 덩어리
            path.rewind()
            path.moveTo(s * 0.50f, s * 0.15f)
            path.cubicTo(s * 0.74f, s * 0.13f, s * 0.88f, s * 0.35f, s * 0.86f, s * 0.57f)
            path.cubicTo(s * 0.85f, s * 0.77f, s * 0.70f, s * 0.85f, s * 0.50f, s * 0.85f)
            path.cubicTo(s * 0.28f, s * 0.85f, s * 0.14f, s * 0.75f, s * 0.15f, s * 0.55f)
            path.cubicTo(s * 0.16f, s * 0.31f, s * 0.30f, s * 0.16f, s * 0.50f, s * 0.15f)
            path.close()
            // 머리 위의 작은 삐침
            path.moveTo(s * 0.43f, s * 0.19f)
            path.cubicTo(s * 0.40f, s * 0.10f, s * 0.44f, s * 0.04f, s * 0.50f, s * 0.05f)
            path.cubicTo(s * 0.49f, s * 0.10f, s * 0.52f, s * 0.14f, s * 0.57f, s * 0.17f)
            path.close()
            drawPath(path, Body)

            // 눈
            val look = if (mood == TabiMood.THINKING) Offset(s * 0.025f, -s * 0.035f) else Offset.Zero
            val big = when (mood) {
                TabiMood.SURPRISED -> 1.3f
                TabiMood.LISTENING -> 1.15f
                else -> 1f
            }
            for (ex in floatArrayOf(0.40f, 0.62f)) {
                val c = Offset(s * ex, s * 0.46f) + look
                if (mood == TabiMood.HAPPY) {
                    // 웃는 눈: 위로 볼록한 선
                    drawArc(Eye, 200f, 140f, false, Offset(c.x - s * 0.065f, c.y - s * 0.04f), Size(s * 0.13f, s * 0.12f), style = Stroke(s * 0.034f, cap = StrokeCap.Round))
                } else {
                    val rx = s * 0.054f * big
                    val ry = if (blink) s * 0.008f else s * 0.098f * big
                    drawOval(Eye, Offset(c.x - rx, c.y - ry), Size(rx * 2f, ry * 2f))
                    if (!blink) drawCircle(Color.White, s * 0.021f * big, Offset(c.x + rx * 0.3f, c.y - ry * 0.45f))
                }
            }

            // 입은 필요할 때만 아주 작게
            when (mood) {
                TabiMood.SPEAKING -> {
                    val ry = s * (0.012f + 0.02f * (if (moving) abs(sin(t * 5f)) else 0.7f))
                    drawOval(Eye, Offset(s * 0.51f - s * 0.026f, s * 0.665f - ry), Size(s * 0.052f, ry * 2f))
                }
                TabiMood.SURPRISED -> drawCircle(Eye, s * 0.024f, Offset(s * 0.51f, s * 0.69f))
                TabiMood.HAPPY, TabiMood.CHEER ->
                    drawArc(Eye, 20f, 140f, false, Offset(s * 0.465f, s * 0.615f), Size(s * 0.09f, s * 0.07f), style = Stroke(s * 0.022f, cap = StrokeCap.Round))
                else -> Unit
            }
        }
        // 생각 방울
        if (mood == TabiMood.THINKING) {
            val k = if (moving) (sin(t * 2f) * 0.5f + 0.5f) else 1f
            drawCircle(Body.copy(alpha = 0.55f), s * 0.02f, Offset(s * 0.83f, s * 0.2f))
            drawCircle(Body.copy(alpha = 0.55f + 0.2f * k), s * 0.028f, Offset(s * 0.89f, s * 0.13f))
            drawCircle(Body.copy(alpha = 0.55f + 0.4f * k), s * 0.038f, Offset(s * 0.94f, s * 0.05f + s * 0.01f))
        }
    }
}
