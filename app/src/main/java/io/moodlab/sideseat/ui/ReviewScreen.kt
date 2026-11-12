package io.moodlab.sideseat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.moodlab.sideseat.core.DriveSummary

private val observationLabels = mapOf(
    "NO_SIGNAL_TURN" to "깜빡이 없이 크게 돌린 핸들",
    "HARD_BRAKE" to "급하게 밟은 브레이크",
)

/** 리뷰(도착 후). 가로: 왼쪽 오늘 요약, 오른쪽 선배 총평 자막과 1분 훈련 버튼. 점수·등수 없이 "지난번보다"로 말한다. */
@Composable
fun ReviewScreen(state: ReviewUi, onAction: (UiAction) -> Unit) {
    SideSeatFrame(Modifier.background(Sc.bg)) { sz ->
        if (sz.wide) {
            Row(Modifier.fillMaxSize().padding(sz.dp(40f)), horizontalArrangement = Arrangement.spacedBy(sz.dp(40f))) {
                Column(Modifier.weight(0.5f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                    Today(state)
                }
                Column(Modifier.weight(0.5f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                    Senior(state, onAction, Modifier.weight(1f))
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(sz.pad), verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                Senior(state, onAction, Modifier)
                Today(state)
            }
        }
    }
}

private fun delta(now: Int, before: Int?, unit: String): String? {
    if (before == null) return null
    val d = now - before
    return when {
        d == 0 -> "지난번과 같아요"
        d > 0 -> "지난번보다 $d$unit 늘었어요"
        else -> "지난번보다 ${-d}$unit 줄었어요"
    }
}

@Composable
private fun Today(state: ReviewUi) {
    val sz = LocalSz.current
    val s: DriveSummary = state.summary
    val p = state.previous
    Text("오늘 운전", color = Sc.text, fontSize = sz.display, fontWeight = FontWeight.Black)
    Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
        StatTile("${s.durationMin}분", "주행 시간", Modifier.weight(1f), delta(s.durationMin, p?.durationMin, "분"))
        StatTile("${s.utterances.size}번", "선배가 말한 횟수", Modifier.weight(1f), delta(s.utterances.size, p?.utterances?.size, "번"))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
        StatTile("${s.rainMin}분", "비 오는 길", Modifier.weight(1f), delta(s.rainMin, p?.rainMin, "분"))
        StatTile("${s.nightMin}분", "밤길", Modifier.weight(1f), delta(s.nightMin, p?.nightMin, "분"))
    }
    val keys = (observationLabels.keys + s.observations.keys).distinct()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(sz.radius)).background(Sc.surface).padding(sz.dp(20f)), verticalArrangement = Arrangement.spacedBy(sz.dp(10f))) {
        Text("선배가 본 것", color = Sc.textDim, fontSize = sz.caption, fontWeight = FontWeight.Bold)
        keys.forEach { k ->
            val n = s.observations[k] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(observationLabels[k] ?: k, color = Sc.text, fontSize = sz.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (n == 0) "없었어요" else "${n}번", color = if (n == 0) Sc.ok else Sc.warn, fontSize = sz.label, fontWeight = FontWeight.Bold)
                    delta(n, p?.let { it.observations[k] ?: 0 }, "번")?.let { Text(it, color = Sc.accent, fontSize = sz.caption) }
                }
            }
        }
    }
    if (s.utterances.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(sz.radius)).background(Sc.surface).padding(sz.dp(20f)), verticalArrangement = Arrangement.spacedBy(sz.dp(10f))) {
            Text("달리면서 한 말", color = Sc.textDim, fontSize = sz.caption, fontWeight = FontWeight.Bold)
            s.utterances.take(6).forEach { u ->
                Row {
                    Dot(if (u.urgent) Sc.warn else Sc.accent, sz.dp(10f), Modifier.padding(top = sz.dp(10f), end = sz.dp(12f)))
                    Text(u.text, color = Sc.text, fontSize = sz.label, lineHeight = sz.label * 1.35f)
                }
            }
        }
    }
}

@Composable
private fun Senior(state: ReviewUi, onAction: (UiAction) -> Unit, modifier: Modifier) {
    val sz = LocalSz.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(sz.dp(32f))).background(Sc.surface).padding(sz.pad),
        verticalArrangement = Arrangement.spacedBy(sz.dp(14f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeniorBadge(speaking = state.talk.speaking)
            Spacer(Modifier.width(sz.dp(14f)))
            Column {
                Text("선배 한마디", color = Sc.text, fontSize = sz.h2, fontWeight = FontWeight.Bold)
                Text(if (state.reviewStreaming) "말하는 중…" else "점수는 없어요. 지난번과 견줘 볼게요.", color = Sc.textDim, fontSize = sz.caption)
            }
        }
        val lines = state.reviewLines.ifEmpty { if (state.reviewStreaming) emptyList() else state.summary.ruleBasedReview }
        lines.forEachIndexed { i, line ->
            key(i, line) {
                val vis = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(vis, enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 }) {
                    Text(line, color = Sc.text, fontSize = sz.body, lineHeight = sz.body * 1.4f, fontWeight = FontWeight.Medium)
                }
            }
        }
        TalkCaption(state.talk, Modifier.fillMaxWidth())
    }
    val suggested = state.summary.suggestedSituation
    if (suggested != null) {
        BigButton(
            "지금 1분만 연습할까요?", Modifier.fillMaxWidth(), primary = true,
            sub = state.suggestedTitle ?: SituationTexts.title(suggested),
        ) { onAction(UiAction.StartSituation(suggested)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
        TalkButton(state.talk, Modifier.weight(1f), height = sz.dp(96f), onAction = onAction)
        BigButton("홈으로", Modifier.width(sz.dp(180f)).height(sz.dp(96f))) { onAction(UiAction.DismissReview) }
    }
}
