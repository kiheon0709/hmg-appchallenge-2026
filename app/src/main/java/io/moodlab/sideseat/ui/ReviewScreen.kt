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
import io.moodlab.sideseat.voice.Persona

private val observationLabels = mapOf(
    "NO_SIGNAL_TURN" to "깜빡이 없이 크게 돌린 핸들",
    "HARD_BRAKE" to "급하게 밟은 브레이크",
)

/** 제한속도·보호구역 자료의 출처 표시(ODbL). */
const val OSM_CREDIT = "제한속도·보호구역 자료 © OpenStreetMap contributors"

/** 리뷰(도착 후). 가로: 왼쪽 오늘 요약, 오른쪽 선배 총평 자막과 1분 훈련 버튼. 점수·등수 없이 "지난번보다"로 말한다. */
@Composable
fun ReviewScreen(state: ReviewUi, onAction: (UiAction) -> Unit) {
    SideSeatFrame { sz ->
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

/** 1분이 안 되면 "0분"이 아니라 초로 보여 준다. 초 값이 없는 이전 기록은 분 그대로. */
private fun span(min: Int, sec: Int): String = if (min == 0 && sec in 1..59) "${sec}초" else "${min}분"

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
    Text("오늘 운전", color = Sc.text, fontSize = sz.display, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
        StatTile(span(s.durationMin, s.durationSec), "주행 시간", Modifier.weight(1f), delta(s.durationMin, p?.durationMin, "분"))
        StatTile("${s.utterances.size}번", "${Persona.NAME}가 말한 횟수", Modifier.weight(1f), delta(s.utterances.size, p?.utterances?.size, "번"))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
        StatTile(span(s.rainMin, s.rainSec), "비 오는 길", Modifier.weight(1f), delta(s.rainMin, p?.rainMin, "분"))
        StatTile(span(s.nightMin, s.nightSec), "밤길", Modifier.weight(1f), delta(s.nightMin, p?.nightMin, "분"))
    }
    val keys = (observationLabels.keys + s.observations.keys).distinct()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(sz.radius)).background(Sc.surface).padding(sz.dp(20f)), verticalArrangement = Arrangement.spacedBy(sz.dp(10f))) {
        Text("${Persona.NAME}가 본 것", color = Sc.textDim, fontSize = sz.caption, fontWeight = FontWeight.Medium)
        keys.forEach { k ->
            val n = s.observations[k] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(observationLabels[k] ?: k, color = Sc.text, fontSize = sz.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (n == 0) "없었어요" else "${n}번", color = if (n == 0) Sc.ok else Sc.warn, fontSize = sz.label, fontWeight = FontWeight.Medium)
                    delta(n, p?.let { it.observations[k] ?: 0 }, "번")?.let { Text(it, color = Sc.textDim, fontSize = sz.caption) }
                }
            }
        }
        // 위치 연계 코치(명세 6.4). 과속은 이번이나 지난번에 있었을 때만, 보호구역은 지나갔을 때만 보인다.
        if (s.speedingCount > 0 || (p?.speedingCount ?: 0) > 0) {
            ObservedRow(
                "제한속도를 넘긴 구간",
                if (s.speedingCount == 0) "없었어요" else "${s.speedingCount}번 (가장 길게 ${s.longestSpeedingSec}초)",
                if (s.speedingCount == 0) Sc.ok else Sc.warn,
                delta(s.speedingCount, p?.speedingCount, "번"),
            )
        }
        // v25: 차가 잰 주행 거리(Odometer)와 차가 알려 준 것(배터리·연료·오일류). 읽을 수 있을 때만 보인다.
        s.distanceKm?.let { km ->
            ObservedRow("주행 거리 (차 계기판)", String.format(java.util.Locale.ROOT, "%.1fkm", km), Sc.text, null)
        }
        s.carNotes.forEach { note -> Text(note, color = Sc.warn, fontSize = sz.label, lineHeight = sz.label * 1.35f) }
        if (s.schoolZonePasses > 0) {
            ObservedRow(
                "어린이 보호구역",
                "${s.schoolZonePasses}번 통과" + (s.schoolZoneMaxKmh?.let { " (최고 ${it}km/h)" } ?: ""),
                if ((s.schoolZoneMaxKmh ?: 0) > 30) Sc.warn else Sc.text,
                null,
            )
        }
    }
    if (s.utterances.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(sz.radius)).background(Sc.surface).padding(sz.dp(20f)), verticalArrangement = Arrangement.spacedBy(sz.dp(10f))) {
            Text("달리면서 한 말", color = Sc.textDim, fontSize = sz.caption, fontWeight = FontWeight.Medium)
            s.utterances.take(6).forEach { u ->
                Row {
                    // 잘못을 표시하는 것처럼 보이지 않게 점은 중립 색 하나로
                    Dot(Sc.textDim, sz.dp(10f), Modifier.padding(top = sz.dp(10f), end = sz.dp(12f)))
                    Text(u.text, color = Sc.text, fontSize = sz.label, lineHeight = sz.label * 1.35f)
                }
            }
        }
    }
    Text(OSM_CREDIT, color = Sc.textDim, fontSize = sz.caption)
}

@Composable
private fun ObservedRow(label: String, value: String, color: androidx.compose.ui.graphics.Color, delta: String?) {
    val sz = LocalSz.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Sc.text, fontSize = sz.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(horizontalAlignment = Alignment.End) {
            Text(value, color = color, fontSize = sz.label, fontWeight = FontWeight.Medium)
            if (delta != null) Text(delta, color = Sc.textDim, fontSize = sz.caption)
        }
    }
}

@Composable
private fun Senior(state: ReviewUi, onAction: (UiAction) -> Unit, modifier: Modifier) {
    val sz = LocalSz.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(sz.radius)).background(Sc.surface).padding(sz.pad),
        verticalArrangement = Arrangement.spacedBy(sz.dp(14f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeniorBadge(size = sz.dp(88f), mood = state.talk.mood())
            Spacer(Modifier.width(sz.dp(14f)))
            Column {
                Text("${Persona.NAME} 한마디", color = Sc.text, fontSize = sz.h2, fontWeight = FontWeight.Medium)
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
            sub = SituationTexts.title(suggested),
        ) { onAction(UiAction.StartSituation(suggested)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
        TalkButton(state.talk, Modifier.weight(1f), height = sz.dp(96f), tonal = true, onAction = onAction)
        BigButton("홈으로", Modifier.width(sz.dp(150f)).height(sz.dp(96f))) { onAction(UiAction.DismissReview) }
    }
}
