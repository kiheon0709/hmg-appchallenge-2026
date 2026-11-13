package io.moodlab.sideseat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import io.moodlab.sideseat.voice.Persona

/**
 * 코치(주행 중). 마지막으로 한 말 한 줄, 호출어 안내 한 줄, "질문하기" 버튼 하나만 둔다.
 * 영상·애니메이션·긴 글·키보드가 없다(주행 중 정책). 가로·세로 같은 구성.
 */
@Composable
fun CoachScreen(state: CoachUi, onAction: (UiAction) -> Unit) {
    SideSeatFrame { sz ->
        Column(
            Modifier.fillMaxSize().padding(sz.dp(48f)),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text("옆자리 · 주행 중", color = Sc.textDim, fontSize = sz.label, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            val heard = state.talk.partialText?.takeIf { state.talk.listening && it.isNotBlank() }
            val line = heard?.let { "“$it”" } ?: state.talk.answerText?.takeIf { it.isNotBlank() } ?: state.lastLine
            // 문장마다 한 줄. 가장 긴 문장이 한 줄에 들어가게 글자를 줄여서 낱말이 쪼개지지 않게 한다
            val sentences = splitSentences(line ?: "필요할 때만 한마디 할게요.")
            val longest = sentences.maxOf { it.length }.coerceAtLeast(1)
            val base = if (sz.wide) 68f * sz.scale else 56f * sz.scale
            val fit = (sz.width.value - 96f * sz.scale) / (longest * 0.96f)
            val size = fit.coerceIn(30f * sz.scale, base)
            Text(
                sentences.joinToString("\n"),
                // 일반 안내는 기본 글자색, 위험 단계만 위험색
                color = when { heard != null -> Sc.accent; line == null -> Sc.textDim; state.danger -> Sc.danger; else -> Sc.text },
                fontSize = size.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                lineHeight = (size * 1.3f).sp, maxLines = sentences.size + 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            WakeStatus(state)
            Spacer(Modifier.height(sz.dp(20f)))
            // 손으로도 시작·중단할 수 있게 버튼은 남긴다(호출어가 켜져 있으면 조금 작게)
            TalkButton(
                state.talk, Modifier.fillMaxWidth(if (sz.wide) 0.62f else 1f),
                height = sz.dp(if (state.wake == WakeUi.OFF) 190f else 130f), onAction = onAction,
            )
        }
    }
}

/** 호출어 안내와 듣는 중·물러남 상태 한 줄. 움직이는 효과 없이 글자와 점 색만 바뀐다. */
@Composable
private fun WakeStatus(state: CoachUi) {
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
        Dot(dot, sz.dp(16f))
        Spacer(Modifier.width(sz.dp(14f)))
        Text(text, color = if (state.wake == WakeUi.YIELDED && !talk.listening) Sc.textDim else Sc.text, fontSize = sz.h2, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
