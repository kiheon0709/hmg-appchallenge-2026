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
import androidx.compose.foundation.layout.width
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
fun ResultScreen(result: ChapterResult, progress: TrainingProgress, bestBefore: Map<String, Long> = emptyMap(), onAction: (UiAction) -> Unit) {
    SideSeatFrame { sz ->
        val surprise = result.chapterId == ChapterCatalog.SURPRISE
        // 상황 하나만 한 연습(리뷰의 "1분 연습")
        val single = result.chapterId == ChapterCatalog.VERSUS
        val singleId = result.results.firstOrNull()?.id
        val summary: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                val earned = result.results.sumOf { it.stars }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tabi(if (earned * 2 >= result.results.size * 3) TabiMood.HAPPY else TabiMood.CHEER, size = sz.dp(96f))
                    Spacer(Modifier.width(sz.dp(14f)))
                    Chip(if (single) "1분 연습 결과" else "훈련 결과", color = Sc.textDim, bg = Sc.surface)
                }
                Text(if (single && singleId != null) SituationTexts.title(singleId) else SituationTexts.chapterTitle(result.chapterId), color = Sc.text, fontSize = sz.display, fontWeight = FontWeight.SemiBold, maxLines = 2)
                val scored = result.results.filter { it.id != SituationId.BEFORE_EXIT }
                if (scored.isEmpty()) {
                    Text("확인 완료", color = Sc.ok, fontSize = sz.sp(84f), fontWeight = FontWeight.SemiBold)
                } else if (scored.sumOf { it.stars } == 0 && !surprise) {
                    // 하나도 끝내지 못했을 때 큰 "0"부터 보이지 않게 한다
                    Text("아직 손에 안 익었어요", color = Sc.text, fontSize = sz.title, fontWeight = FontWeight.SemiBold, lineHeight = sz.title * 1.25f)
                    Text("놓친 조작과 이 차의 스위치 위치부터 같이 봐요.", color = Sc.textDim, fontSize = sz.body, lineHeight = sz.body * 1.35f)
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
                } else if (!single) {
                    LicenseBar(progress, null)
                }
                Text(
                    "별은 운전 실력이 아니라 이 차의 스위치 위치와 순서가 손에 익은 정도예요.",
                    color = Sc.textDim, fontSize = sz.caption, lineHeight = sz.caption * 1.4f,
                )
                if (sz.wide) Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
                    BigButton("다시 하기", Modifier.weight(1f)) {
                        onAction(
                            when {
                                surprise -> UiAction.StartSurprise
                                single -> singleId?.let { UiAction.StartSituation(it) } ?: UiAction.GoHome
                                else -> UiAction.StartChapter(result.chapterId)
                            },
                        )
                    }
                    BigButton("홈으로", Modifier.weight(1f), primary = true) { onAction(UiAction.GoHome) }
                }
            }
        }
        val list: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                result.results.forEachIndexed { i, r -> SituationResultCard(i, r, bestBefore) }
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
private fun SituationResultCard(index: Int, r: SituationResult, bestBefore: Map<String, Long>) {
    val sz = LocalSz.current
    val where = LocalSwitchLocation.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(sz.radius)).background(Sc.surface).padding(sz.dp(22f))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(SituationTexts.title(r.id), color = Sc.text, fontSize = sz.h2, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // 이번 훈련 전의 최고 기록과 견준다: 처음이면 "첫 기록", 줄였으면 "새 기록!"
                val before = bestBefore[r.id.name]
                val time = r.reactionMs
                val line = when {
                    r.stars == 0 -> "끝내지 못했어요"
                    r.id == SituationId.BEFORE_EXIT -> "세 가지를 확인했어요"
                    time == null -> "끝까지 해냈어요"
                    before == null -> "반응 ${SituationTexts.seconds(time)} · 첫 기록"
                    time < before -> "반응 ${SituationTexts.seconds(time)} · 새 기록!"
                    else -> "반응 ${SituationTexts.seconds(time)} · 최고 ${SituationTexts.seconds(before)}"
                }
                Text(line, color = if (r.stars > 0) Sc.textDim else Sc.warn, fontSize = sz.label, fontWeight = FontWeight.SemiBold)
            }
            if (r.id == SituationId.BEFORE_EXIT) {
                Chip("확인 완료", color = Sc.ok, bg = Sc.surfaceHi, size = sz.label)
            } else {
                Stars(r.stars, size = sz.dp(46f), animateKey = index)
            }
        }
        if (r.missedStepIds.isNotEmpty()) {
            Spacer(Modifier.height(sz.dp(12f)))
            Text("놓친 조작", color = Sc.warn, fontSize = sz.caption, fontWeight = FontWeight.Medium)
            r.missedStepIds.forEach { stepId ->
                Row(Modifier.padding(top = sz.dp(8f))) {
                    Dot(Sc.warn, sz.dp(10f), Modifier.padding(top = sz.dp(10f), end = sz.dp(10f)))
                    Column {
                        Text(SituationTexts.stepLabel(r.id, stepId), color = Sc.text, fontSize = sz.label, fontWeight = FontWeight.SemiBold)
                        SituationTexts.hint(r.id, stepId)?.let {
                            Text(it, color = Sc.textDim, fontSize = sz.label, lineHeight = sz.label * 1.35f)
                        }
                        // 이 차의 스위치 위치(통합 세션이 넣어 준 경우에만)
                        SituationTexts.switchId(stepId)?.let { where(it) }?.let {
                            Text(it, color = Sc.text, fontSize = sz.label, lineHeight = sz.label * 1.35f)
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
internal fun LicenseBar(progress: TrainingProgress, vehicleModelKey: String?, vehicleName: String? = null, bare: Boolean = false) {
    val sz = LocalSz.current
    val license = if (vehicleModelKey != null || progress.licenses.isEmpty()) {
        progress.licenseFor(vehicleModelKey)
    } else {
        progress.licenses.values.maxByOrNull { it.clearedChapters.size }!!
    }
    // 면허는 이 차의 스위치를 익히는 챕터만 센다
    val total = ChapterCatalog.LICENSE_CHAPTERS.size
    val cleared = license.clearedChapters.size.coerceAtMost(total)
    val color = if (license.licensed) Sc.ok else Sc.accent
    // [bare]면 카드 없이 내용만(홈에서는 차종 카드 안에 들어간다)
    Column(if (bare) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().card().padding(sz.dp(22f)), verticalArrangement = Arrangement.spacedBy(sz.dp(8f))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (license.licensed) "이 차 숙련" else "이 차 면허", color = if (license.licensed) Sc.ok else Sc.text, fontSize = sz.label, fontWeight = FontWeight.Bold)
            ProgressBar(cleared / total.toFloat(), Modifier.weight(1f).padding(horizontal = sz.dp(18f)), color = color)
            Text("${total}개 중 ", color = Sc.textDim, fontSize = sz.label, fontWeight = FontWeight.Medium)
            Text("${cleared}개", color = color, fontSize = sz.body, fontWeight = FontWeight.Bold)
        }
        Text(
            (vehicleName?.let { "$it · " } ?: "") + if (license.licensed) "모든 챕터를 별 2개 이상으로 깼어요." else "스위치를 익히는 챕터 ${total}개(C1~C4) 중 별 2개 이상으로 마친 수예요. 다른 차에서는 다시 따요.",
            color = Sc.textDim, fontSize = sz.caption, lineHeight = sz.caption * 1.4f,
        )
    }
}
