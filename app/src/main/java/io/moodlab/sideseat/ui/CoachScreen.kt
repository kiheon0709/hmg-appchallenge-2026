package io.moodlab.sideseat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.core.WiperLevel
import io.moodlab.sideseat.ui.scene.airIcon
import io.moodlab.sideseat.ui.scene.beamIcon
import io.moodlab.sideseat.ui.scene.wiperIcon
import io.moodlab.sideseat.voice.Persona

/**
 * 코치(주행 중). 왼쪽은 마지막으로 한 말 한 줄과 코치가 지켜보는 차 신호, 오른쪽은 호출어 안내와 "질문하기" 버튼.
 * 영상·애니메이션·긴 글·키보드가 없다(주행 중 정책). 값이 바뀔 때만 다시 그린다.
 * [vehicle]을 주면 전조등·와이퍼·공기 순환·제한속도를 계기판 표시등처럼 한 줄로 보여 준다(없으면 그 줄을 뺀다).
 */
@Composable
fun CoachScreen(state: CoachUi, onAction: (UiAction) -> Unit, vehicle: VehicleState? = null) {
    SideSeatFrame { sz ->
        val buttonHeight = sz.dp(if (state.wake == WakeUi.OFF) 190f else 130f)
        if (sz.wide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = sz.dp(56f), end = sz.dp(48f), top = sz.dp(40f), bottom = sz.dp(36f))) {
                    Header(state)
                    Spacer(Modifier.weight(1f))
                    Message(state)
                    Spacer(Modifier.weight(1.15f))
                    SignalStrip(vehicle)
                }
                // 손이 가는 것은 오른쪽 한 곳에 모은다
                Column(
                    Modifier.width((sz.width * 0.31f).coerceIn(sz.dp(320f), sz.dp(460f))).fillMaxHeight().background(Sc.surface).padding(sz.dp(28f)),
                ) {
                    SpeedLimit(vehicle?.speedLimitKmh, sz.dp(132f))
                    Spacer(Modifier.weight(1f))
                    WakeStatus(state)
                    Spacer(Modifier.height(sz.dp(20f)))
                    TalkButton(state.talk, Modifier.fillMaxWidth(), height = buttonHeight, onAction = onAction)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = sz.dp(32f), vertical = sz.dp(32f))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Header(state) }
                    SpeedLimit(vehicle?.speedLimitKmh, sz.dp(96f))
                }
                Spacer(Modifier.weight(1f))
                Message(state)
                Spacer(Modifier.weight(1f))
                SignalStrip(vehicle)
                Spacer(Modifier.height(sz.dp(28f)))
                WakeStatus(state)
                Spacer(Modifier.height(sz.dp(18f)))
                TalkButton(state.talk, Modifier.fillMaxWidth(), height = buttonHeight, onAction = onAction)
            }
        }
    }
}

@Composable
private fun Header(state: CoachUi) {
    val sz = LocalSz.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        SeniorBadge(size = sz.dp(52f), speaking = state.talk.speaking)
        Spacer(Modifier.width(sz.dp(16f)))
        Column {
            Text(Persona.NAME, color = Sc.text, fontSize = sz.body, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("옆자리에서 같이 보는 중", color = Sc.textDim, fontSize = sz.caption, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** 마지막으로 한 말(또는 듣는 중의 받아 적기). 왼쪽 띠가 누구의 말인지, 위험한지 알린다. */
@Composable
private fun Message(state: CoachUi) {
    val sz = LocalSz.current
    val heard = state.talk.partialText?.takeIf { state.talk.listening && it.isNotBlank() }
    val line = heard?.let { "“$it”" } ?: state.talk.answerText?.takeIf { it.isNotBlank() } ?: state.lastLine
    // 문장마다 한 줄. 가장 긴 문장이 한 줄에 들어가게 글자를 줄여서 낱말이 쪼개지지 않게 한다
    val sentences = splitSentences(line ?: "필요할 때만 한마디 할게요.")
    val longest = sentences.maxOf { it.length }.coerceAtLeast(1)
    val bar = when { heard != null -> Sc.accent; line == null -> Sc.outline; state.danger -> Sc.danger; else -> Sc.text }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val base = if (sz.wide) 78f * sz.scale else 56f * sz.scale
        val fit = (maxWidth.value - 34f * sz.scale) / (longest * 0.96f)
        val size = fit.coerceIn(30f * sz.scale, base)
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(sz.dp(6f)).fillMaxHeight().background(bar))
            Spacer(Modifier.width(sz.dp(28f)))
            Text(
                sentences.joinToString("\n"),
                // 일반 안내는 기본 글자색, 위험 단계만 위험색
                color = when { heard != null -> Sc.accent; line == null -> Sc.textDim; state.danger -> Sc.danger; else -> Sc.text },
                fontSize = size.sp, fontWeight = FontWeight.Medium,
                lineHeight = (size * 1.32f).sp, maxLines = sentences.size + 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 코치가 지켜보는 차 신호 한 줄. 계기판 표시등처럼 켜진 것은 진하게, 꺼진 것은 흐리게. 모르는 신호는 뺀다. */
@Composable
private fun SignalStrip(vehicle: VehicleState?) {
    val v = vehicle ?: return
    val sz = LocalSz.current
    val lamp = v.lowBeamOn ?: v.highBeamOn
    if (lamp == null && v.wiper == null && v.recirculationOn == null) return
    Column {
        Box(Modifier.fillMaxWidth().height(sz.line).background(Sc.outline))
        Row(Modifier.fillMaxWidth().padding(top = sz.dp(24f)), verticalAlignment = Alignment.CenterVertically) {
            if (lamp != null) {
                val high = v.highBeamOn == true
                val on = high || v.lowBeamOn == true
                Signal("전조등", if (high) "상향등" else if (on) "켜짐" else "꺼짐", on) { c, x, y, s -> beamIcon(x, y, s, c, dipped = !high) }
            }
            v.wiper?.let { level ->
                val text = when (level) {
                    WiperLevel.OFF -> "꺼짐"
                    WiperLevel.MIST -> "한 번"
                    WiperLevel.INTERMITTENT -> "간헐"
                    WiperLevel.LOW -> "1단"
                    WiperLevel.HIGH -> "2단"
                }
                Signal("와이퍼", text, level != WiperLevel.OFF) { c, x, y, s -> wiperIcon(x, y, s, c) }
            }
            v.recirculationOn?.let { inside ->
                Signal("공기", if (inside) "내기" else "외기", true) { c, x, y, s -> airIcon(x, y, s * 0.82f, c, inside) }
            }
        }
    }
}

/** 지금 도로의 제한속도 표지(흰 바탕, 빨간 테, 검은 숫자). 값을 모르면 그리지 않는다. */
@Composable
private fun SpeedLimit(limit: Int?, size: androidx.compose.ui.unit.Dp) {
    if (limit == null) return
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            drawCircle(Color.White, r)
            drawCircle(Color(0xFFD4232B), r * 0.88f, style = Stroke(r * 0.2f))
        }
        Text(limit.toString(), color = Color.Black, fontSize = (size.value * (if (limit >= 100) 0.34f else 0.42f)).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Signal(
    label: String, value: String, on: Boolean, icon: DrawScope.(Color, Float, Float, Float) -> Unit,
) {
    val sz = LocalSz.current
    val c = if (on) Sc.text else Sc.textDim
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(sz.dp(if (sz.wide) 68f else 52f))) { icon(c, size.width / 2f, size.height / 2f, size.minDimension * 0.36f) }
        Spacer(Modifier.width(sz.dp(if (sz.wide) 18f else 12f)))
        Column {
            Text(label, color = Sc.textDim, fontSize = if (sz.wide) sz.label else sz.caption, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(value, color = c, fontSize = if (sz.wide) sz.h2 else sz.label, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** 호출어 안내와 듣는 중·물러남 상태. 움직이는 효과 없이 글자와 점 색만 바뀐다. */
@Composable
private fun ColumnScope.WakeStatus(state: CoachUi) {
    val sz = LocalSz.current
    val talk = state.talk
    val name = Persona.NAME
    val text: String
    val dot: Color
    when {
        talk.listening || state.wake == WakeUi.HEARD -> { text = "듣고 있어요"; dot = Sc.accent }
        talk.thinking -> { text = "잠깐만요"; dot = Sc.accent }
        talk.speaking -> { text = "말하는 중이에요"; dot = Sc.text }
        state.wake == WakeUi.YIELDED -> { text = "차량 비서가 먼저예요"; dot = Sc.textDim }
        state.wake == WakeUi.FOLLOW_UP -> { text = "이어서 바로 말해도 돼요"; dot = Sc.accent }
        state.wake == WakeUi.WAITING -> { text = "‘$name’ 하고 불러 주세요"; dot = Sc.ok }
        else -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(dot, sz.dp(14f))
        Spacer(Modifier.width(sz.dp(14f)))
        Text(
            text, color = if (state.wake == WakeUi.YIELDED && !talk.listening) Sc.textDim else Sc.text,
            fontSize = sz.body, fontWeight = FontWeight.Medium, lineHeight = sz.body * 1.3f, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 마침표·물음표·느낌표 뒤에서 문장을 나눈다. */
internal fun splitSentences(text: String): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    for (i in text.indices) {
        val c = text[i]
        sb.append(c)
        val end = (c == '.' || c == '?' || c == '!') && (i == text.lastIndex || text[i + 1] == ' ')
        if (end) { out += sb.toString().trim(); sb.setLength(0) }
    }
    if (sb.isNotBlank()) out += sb.toString().trim()
    return out.filter { it.isNotEmpty() }.ifEmpty { listOf(text) }
}
