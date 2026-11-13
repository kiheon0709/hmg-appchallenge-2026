package io.moodlab.sideseat.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.moodlab.sideseat.core.ChapterCatalog
import io.moodlab.sideseat.core.ChapterResult
import io.moodlab.sideseat.core.SituationId
import io.moodlab.sideseat.core.SituationResult
import io.moodlab.sideseat.core.TrainingProgress

/** 챕터 결과: 상황별 별과 반응 시간, 놓친 조작의 스위치 위치, 다시 하기. */
@Composable
fun ResultScreen(result: ChapterResult, progress: TrainingProgress, onAction: (UiAction) -> Unit) {
    SideSeatFrame { sz ->
        val surprise = result.chapterId == ChapterCatalog.SURPRISE
        val summary: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                Chip("훈련 결과", color = Sc.textDim, bg = Sc.surface)
                Text(SituationTexts.chapterTitle(result.chapterId), color = Sc.text, fontSize = sz.display, fontWeight = FontWeight.SemiBold, maxLines = 2)
                val scored = result.results.filter { it.id != SituationId.BEFORE_EXIT }
                if (scored.isEmpty()) {
                    Text("확인 완료", color = Sc.ok, fontSize = sz.sp(84f), fontWeight = FontWeight.SemiBold)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${scored.sumOf { it.stars }}", color = Sc.text, fontSize = sz.sp(110f), fontWeight = FontWeight.SemiBold)
                        Text(" / ${scored.size * 3} 별", color = Sc.textDim, fontSize = sz.h2, modifier = Modifier.padding(bottom = sz.dp(22f)))
                    }
                }
                if (surprise) {
                    Text(
                        "버틴 상황 ${result.results.count { it.stars > 0 }}개 · 최고 기록 ${progress.surpriseBest}개",
                        color = Sc.text, fontSize = sz.body, fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    LicenseBar(progress, null)
                }
                Text(
                    "별은 운전 실력이 아니라 이 차의 스위치 위치와 순서가 손에 익은 정도예요.",
                    color = Sc.textDim, fontSize = sz.caption, lineHeight = sz.caption * 1.4f,
                )
                if (sz.wide) Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
                    BigButton("다시 하기", Modifier.weight(1f)) {
                        onAction(if (surprise) UiAction.StartSurprise else UiAction.StartChapter(result.chapterId))
                    }
                    BigButton("홈으로", Modifier.weight(1f), primary = true) { onAction(UiAction.GoHome) }
                }
            }
        }
        val list: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                result.results.forEachIndexed { i, r -> SituationResultCard(i, r, progress) }
            }
        }
        if (sz.wide) {
            Row(Modifier.fillMaxSize().padding(sz.dp(40f)), horizontalArrangement = Arrangement.spacedBy(sz.dp(40f))) {
                summary(Modifier.weight(0.42f).fillMaxHeight())
                list(Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()))
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(sz.pad), verticalArrangement = Arrangement.spacedBy(sz.pad)) {
                summary(Modifier.fillMaxWidth())
                list(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SituationResultCard(index: Int, r: SituationResult, progress: TrainingProgress) {
    val sz = LocalSz.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(sz.radius)).background(Sc.surface).padding(sz.dp(22f))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(SituationTexts.title(r.id), color = Sc.text, fontSize = sz.h2, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val best = progress.bestReactionMs[r.id.name]
                val line = buildString {
                    if (r.reactionMs != null) append("반응 ${SituationTexts.seconds(r.reactionMs!!)}") else append("끝내지 못했어요")
                    if (best != null) append(if (r.reactionMs != null && r.reactionMs!! <= best) " · 새 기록!" else " · 최고 ${SituationTexts.seconds(best)}")
                }
                Text(line, color = if (r.reactionMs != null) Sc.textDim else Sc.warn, fontSize = sz.label, fontWeight = FontWeight.SemiBold)
            }
            if (r.id == SituationId.BEFORE_EXIT) {
                Chip("확인 완료", color = Sc.ok, bg = Sc.surfaceHi, size = sz.label)
            } else {
                Stars(r.stars, size = sz.dp(46f), animateKey = index)
            }
        }
        if (r.missedStepIds.isNotEmpty()) {
            Spacer(Modifier.height(sz.dp(12f)))
            Text("놓친 조작 · 이 차에서 스위치는 여기 있어요", color = Sc.warn, fontSize = sz.caption, fontWeight = FontWeight.Medium)
            r.missedStepIds.forEach { stepId ->
                Row(Modifier.padding(top = sz.dp(8f))) {
                    Dot(Sc.warn, sz.dp(10f), Modifier.padding(top = sz.dp(10f), end = sz.dp(10f)))
                    Column {
                        Text(SituationTexts.stepLabel(r.id, stepId), color = Sc.text, fontSize = sz.label, fontWeight = FontWeight.SemiBold)
                        SituationTexts.hint(r.id, stepId)?.let {
                            Text(it, color = Sc.textDim, fontSize = sz.label, lineHeight = sz.label * 1.35f)
                        }
                    }
                }
            }
        }
        r.penalties.forEach { p ->
            Text(
                "감점 · " + SituationTexts.penaltyLine(r.id, p), color = Sc.textDim, fontSize = sz.caption,
                lineHeight = sz.caption * 1.4f, modifier = Modifier.padding(top = sz.dp(10f)),
            )
        }
        if (r.missedStepIds.isEmpty() && r.penalties.isEmpty()) {
            SituationTexts.lesson(r.id)?.let {
                Text(it, color = Sc.textDim, fontSize = sz.label, lineHeight = sz.label * 1.35f, modifier = Modifier.padding(top = sz.dp(10f)))
            }
        }
    }
}

/** "이 차 면허" 진행도: 챕터 C1~C4를 ★2 이상으로 깬 수. */
@Composable
internal fun LicenseBar(progress: TrainingProgress, vehicleModelKey: String?, vehicleName: String? = null) {
    val sz = LocalSz.current
    val license = if (vehicleModelKey != null || progress.licenses.isEmpty()) {
        progress.licenseFor(vehicleModelKey)
    } else {
        progress.licenses.values.maxByOrNull { it.clearedChapters.size }!!
    }
    val total = ChapterCatalog.chapters.size
    val cleared = license.clearedChapters.size.coerceAtMost(total)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(sz.radius)).background(Sc.surface).padding(sz.dp(20f))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (license.licensed) "이 차 숙련" else "이 차 면허", color = if (license.licensed) Sc.ok else Sc.text,
                fontSize = sz.body, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            )
            Text("$cleared / $total", color = Sc.text, fontSize = sz.body, fontWeight = FontWeight.Medium)
        }
        ProgressBar(cleared / total.toFloat(), Modifier.fillMaxWidth().padding(vertical = sz.dp(10f)), color = if (license.licensed) Sc.ok else Sc.text)
        Text(
            (vehicleName?.let { "$it · " } ?: "") + if (license.licensed) "모든 챕터를 별 2개 이상으로 깼어요." else "챕터마다 별 2개 이상이면 면허가 채워져요. 다른 차에서는 다시 따요.",
            color = Sc.textDim, fontSize = sz.caption, lineHeight = sz.caption * 1.4f,
        )
    }
}
