package io.moodlab.sideseat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * 코치(주행 중). 마지막으로 한 말 한 줄과 큰 "질문하기" 버튼 하나만 둔다.
 * 영상·애니메이션·긴 글·키보드가 없다(주행 중 정책). 가로·세로 같은 구성.
 */
@Composable
fun CoachScreen(state: CoachUi, onAction: (UiAction) -> Unit) {
    SideSeatFrame(Modifier.background(Sc.bg)) { sz ->
        Column(
            Modifier.fillMaxSize().padding(sz.dp(48f)),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text("옆자리 · 주행 중", color = Sc.textDim, fontSize = sz.label, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            val heard = state.talk.partialText?.takeIf { state.talk.listening && it.isNotBlank() }
            val line = heard?.let { "“$it”" } ?: state.talk.answerText?.takeIf { it.isNotBlank() } ?: state.lastLine
            Text(
                line ?: "필요할 때만 한마디 할게요.",
                color = when { heard != null -> Sc.pink; line == null -> Sc.textDim; state.urgent -> Sc.warn; else -> Sc.text },
                fontSize = if (sz.wide) sz.sp(68f) else sz.sp(56f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                lineHeight = if (sz.wide) sz.sp(86f) else sz.sp(72f), maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            TalkButton(state.talk, Modifier.fillMaxWidth(if (sz.wide) 0.62f else 1f), height = sz.dp(190f), onAction = onAction)
        }
    }
}
