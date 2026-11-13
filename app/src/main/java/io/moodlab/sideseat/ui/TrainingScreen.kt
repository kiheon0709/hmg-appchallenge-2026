package io.moodlab.sideseat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.moodlab.sideseat.core.ChapterCatalog
import io.moodlab.sideseat.core.SituationId
import io.moodlab.sideseat.core.StepView
import io.moodlab.sideseat.core.TrainingPhase
import io.moodlab.sideseat.core.TrainingView
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.ui.scene.DriveScene

/**
 * 훈련 화면(정차 중). 가로는 주행 화면이 전체를 채우고 그 위에 상황 카드·체크리스트·초시계가 얹힌다.
 * 세로는 위가 주행 화면, 아래가 체크리스트다.
 */
@Composable
fun TrainingScreen(view: TrainingView, vehicle: VehicleState, onAction: (UiAction) -> Unit) {
    SideSeatFrame { sz ->
        val pulse by rememberInfiniteTransition(label = "alert").animateFloat(
            0f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "pulse",
        )
        val success = view.phase == TrainingPhase.SUCCESS
        val glowColor = if (success) Sc.ok else if (view.scene.alert > 0.05f) Sc.danger else Color(0xFFBFD4FF)
        val glow = { if (success) 0.7f else if (view.scene.alert > 0.05f) view.scene.alert * (0.35f + 0.4f * pulse) else 0f }

        if (sz.wide) {
            Box(Modifier.fillMaxSize()) {
                DriveScene(view, vehicle, Modifier.fillMaxSize())
                EdgeGlow(glowColor, glow)
                SituationCard(view, Modifier.align(Alignment.TopStart).padding(sz.pad).widthIn(max = sz.width * 0.42f))
                SidePanel(
                    view, onAction,
                    Modifier.align(Alignment.TopEnd).padding(sz.pad).width((sz.width * 0.3f).coerceIn(sz.dp(330f), sz.dp(520f))),
                )
                CenterOverlays(view, onAction)
                // 선택지가 떠 있으면 오른쪽 판과 겹치지 않게 왼쪽으로 붙인다
                HintBanner(
                    view,
                    if (view.choice != null) {
                        Modifier.align(Alignment.BottomStart).padding(start = sz.pad, bottom = sz.height * 0.27f).widthIn(max = sz.width * 0.5f)
                    } else {
                        Modifier.align(Alignment.BottomCenter).padding(bottom = sz.height * 0.27f).widthIn(max = sz.width * 0.56f)
                    },
                )
                BigButton("그만하기", Modifier.align(Alignment.BottomStart).padding(sz.pad)) { onAction(UiAction.AbortTraining) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(0.5f)) {
                    DriveScene(view, vehicle, Modifier.fillMaxSize())
                    SituationCard(view, Modifier.align(Alignment.TopStart).padding(sz.gap).widthIn(max = sz.width * 0.68f))
                    CenterOverlays(view, onAction)
                    // 빛 띠는 주행 화면에만 두른다(아래 체크리스트 판은 시스템 바탕 그대로)
                    EdgeGlow(glowColor, glow)
                    // 세로에서는 아래 판이 길어져도 항상 보이게 주행 화면 위에 둔다
                    BigButton("그만하기", Modifier.align(Alignment.TopEnd).padding(sz.gap)) { onAction(UiAction.AbortTraining) }
                }
                Box(Modifier.fillMaxWidth().weight(0.5f)) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(sz.gap),
                        verticalArrangement = Arrangement.spacedBy(sz.dp(10f)),
                    ) {
                        // 세로에서는 힌트가 아래로 밀려 잘리지 않게 체크리스트 위에 둔다(선택지가 있으면 선택지 다음)
                        if (view.choice == null) HintBanner(view, Modifier.fillMaxWidth())
                        SidePanel(view, onAction, Modifier.fillMaxWidth())
                        if (view.choice != null) HintBanner(view, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun SituationCard(view: TrainingView, modifier: Modifier) {
    val sz = LocalSz.current
    val live = view.phase == TrainingPhase.ACTIVE || view.phase == TrainingPhase.HINT
    Column(modifier, verticalArrangement = Arrangement.spacedBy(sz.dp(10f))) {
        Row(horizontalArrangement = Arrangement.spacedBy(sz.dp(8f)), verticalAlignment = Alignment.CenterVertically) {
            val chapter = SituationTexts.chapterTitle(view.chapterId)
            if (chapter.isNotEmpty()) Chip(chapter, color = Sc.text, size = sz.label)
            if (view.chapterId == ChapterCatalog.SURPRISE) Chip("${view.surviveCount}개 버팀", color = Sc.text, size = sz.label)
            Chip(
                when (view.phase) {
                    TrainingPhase.IDLE -> "준비"
                    TrainingPhase.CRUISE -> "평온한 주행"
                    TrainingPhase.BRIEFING -> "곧 시작"
                    TrainingPhase.ACTIVE -> "지금!"
                    TrainingPhase.HINT -> "힌트"
                    TrainingPhase.SUCCESS -> "성공"
                    TrainingPhase.FAIL -> "다시"
                    TrainingPhase.CHAPTER_DONE -> "끝"
                },
                color = if (live) Sc.onAccent else Sc.text, bg = if (live) Sc.danger else Sc.scrim, size = sz.label,
            )
        }
        AnimatedVisibility(view.situation != null && view.phase != TrainingPhase.CRUISE, enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut()) {
            Panel(border = if (live) Sc.danger else Sc.outline, padding = sz.dp(18f)) {
                val long = view.title.length > 8
                Text(
                    view.title, color = Sc.text, fontSize = if (long) sz.title else sz.display, fontWeight = FontWeight.SemiBold,
                    lineHeight = (if (long) sz.title else sz.display) * 1.15f, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SidePanel(view: TrainingView, onAction: (UiAction) -> Unit, modifier: Modifier) {
    val sz = LocalSz.current
    val versusIdle = view.chapterId == ChapterCatalog.VERSUS && (view.phase == TrainingPhase.IDLE || view.phase == TrainingPhase.CRUISE)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(sz.gap)) {
        if (versusIdle) {
            Panel {
                Text("교관 대결", color = Sc.text, fontSize = sz.label, fontWeight = FontWeight.Medium)
                Text("조수석에서 상황을 골라 던져요", color = Sc.textDim, fontSize = sz.caption)
                Spacer(Modifier.height(sz.dp(12f)))
                SituationTexts.all.chunked(2).forEach { row ->
                    Row(Modifier.padding(bottom = sz.dp(8f)), horizontalArrangement = Arrangement.spacedBy(sz.dp(8f))) {
                        row.forEach { def -> BigButton(def.title, Modifier.weight(1f)) { onAction(UiAction.Inject(def.id)) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (view.steps.isNotEmpty() && view.phase != TrainingPhase.CRUISE && view.phase != TrainingPhase.CHAPTER_DONE) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("해야 할 조작", color = Sc.textDim, fontSize = sz.caption, fontWeight = FontWeight.SemiBold)
                        Text("${view.steps.count { it.done }} / ${view.steps.size}", color = Sc.text, fontSize = sz.title, fontWeight = FontWeight.Medium)
                    }
                    Timer(view)
                }
                Spacer(Modifier.height(sz.dp(12f)))
                val next = view.steps.indexOfFirst { !it.done }
                view.steps.forEachIndexed { i, step -> StepRow(step, current = i == next && view.phase != TrainingPhase.BRIEFING) }
            }
        }
        view.choice?.let { choice ->
            Panel(border = Sc.text) {
                Text(choice.question, color = Sc.text, fontSize = sz.body, fontWeight = FontWeight.Medium, lineHeight = sz.body * 1.3f)
                Text("말로 답해도 돼요", color = Sc.textDim, fontSize = sz.caption, modifier = Modifier.padding(top = sz.dp(4f), bottom = sz.dp(12f)))
                choice.options.forEachIndexed { i, option ->
                    BigButton("${i + 1}. $option", Modifier.fillMaxWidth().padding(bottom = sz.dp(8f))) { onAction(UiAction.Answer(option)) }
                }
            }
        }
    }
}

@Composable
private fun Timer(view: TrainingView) {
    val running = view.phase == TrainingPhase.ACTIVE || view.phase == TrainingPhase.HINT
    val limit = view.limitMs
    val sz = LocalSz.current
    val ring = sz.dp(if (sz.wide) 132f else 100f)
    when {
        limit <= 0L -> TimerRing(0f, if (running) "%.0f".format(view.elapsedMs / 1000f) else "–", size = ring, caption = "천천히")
        limit <= 2500L -> TimerRing(
            if (running) view.elapsedMs / limit.toFloat() else 0f,
            if (running) "%.2f".format(view.elapsedMs / 1000f) else "%.1f".format(limit / 1000f), size = ring, caption = "반응 시간",
        )
        else -> {
            val left = ((limit - view.elapsedMs).coerceAtLeast(0L)) / 1000f
            TimerRing(if (running) view.elapsedMs / limit.toFloat() else 0f, if (running) "%.1f".format(left) else "%.0f".format(limit / 1000f), size = ring, caption = "초")
        }
    }
}

@Composable
private fun StepRow(step: StepView, current: Boolean) {
    val sz = LocalSz.current
    val done by animateFloatAsState(if (step.done) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "step")
    val shape = RoundedCornerShape(sz.radius)
    Row(
        Modifier.fillMaxWidth().padding(vertical = sz.dp(4f)).clip(shape)
            .background(if (step.done) Sc.ok.copy(alpha = 0.12f) else Color.Transparent)
            .border(sz.line, if (step.done) Sc.ok else if (current) Sc.text else Sc.outline, shape)
            .padding(horizontal = sz.dp(14f), vertical = sz.dp(12f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(sz.dp(40f))) {
            val s = size.minDimension
            val c = Offset(s / 2f, s / 2f)
            drawCircle(Sc.outline, s * 0.42f, c, style = Stroke(s * 0.08f))
            if (done > 0.01f) {
                val k = done.coerceIn(0f, 1.25f)
                drawCircle(Sc.ok, s * 0.46f * k, c)
                val p = k.coerceAtMost(1f)
                val a = Offset(s * 0.28f, s * 0.52f); val b = Offset(s * 0.44f, s * 0.67f); val d = Offset(s * 0.73f, s * 0.35f)
                drawLine(Sc.onAccent, a, b, s * 0.1f, cap = StrokeCap.Round)
                drawLine(Sc.onAccent, b, Offset(b.x + (d.x - b.x) * p, b.y + (d.y - b.y) * p), s * 0.1f, cap = StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(sz.dp(14f)))
        Text(
            step.label, color = if (step.done) Sc.ok else Sc.text, fontSize = sz.body,
            fontWeight = if (current) FontWeight.Medium else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 힌트 단계와 실패 때 해야 할 조작과, 이 차의 스위치 위치(통합 세션이 넣어 준 경우)를 알려 준다. */
@Composable
private fun HintBanner(view: TrainingView, modifier: Modifier) {
    val sz = LocalSz.current
    val show = view.phase == TrainingPhase.HINT || view.phase == TrainingPhase.FAIL
    val step = view.steps.firstOrNull { !it.done }
    val hint = if (show && step != null) SituationTexts.hint(view.situation, step.id) else null
    val where = if (show && step != null) SituationTexts.switchId(step.id)?.let { LocalSwitchLocation.current(it) } else null
    AnimatedVisibility(hint != null, modifier, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        Panel(border = Sc.warn, padding = sz.dp(18f)) {
            Text("이렇게 해요", color = Sc.warn, fontSize = sz.caption, fontWeight = FontWeight.Medium)
            Text(hint.orEmpty(), color = Sc.text, fontSize = sz.body, fontWeight = FontWeight.SemiBold, lineHeight = sz.body * 1.3f)
            if (where != null) Text(where, color = Sc.text, fontSize = sz.label, lineHeight = sz.label * 1.35f)
        }
    }
}

@Composable
private fun BoxScope.CenterOverlays(view: TrainingView, onAction: (UiAction) -> Unit) {
    val sz = LocalSz.current
    val enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), initialScale = 0.7f)
    val exit = fadeOut(tween(250)) + scaleOut(targetScale = 1.1f)
    val result = view.lastResult

    AnimatedVisibility(view.phase == TrainingPhase.SUCCESS && result != null, Modifier.align(Alignment.Center), enter = enter, exit = exit) {
        Panel(border = Sc.ok, padding = sz.dp(30f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = sz.dp(360f))) {
                // 내리기 전 확인은 점수를 매기지 않는다
                val checkOnly = result?.id == SituationId.BEFORE_EXIT
                if (!checkOnly) {
                    Stars(result?.stars ?: 0, size = sz.dp(76f), animateKey = result)
                    Spacer(Modifier.height(sz.dp(10f)))
                }
                Text(
                    if (checkOnly) "확인 완료" else when (result?.stars) { 3 -> "완벽해요!"; 2 -> "좋아요!"; else -> "됐어요!" },
                    color = Sc.ok, fontSize = sz.display, fontWeight = FontWeight.SemiBold,
                )
                result?.reactionMs?.takeIf { !checkOnly }?.let {
                    Text(SituationTexts.seconds(it) + " 걸렸어요", color = Sc.text, fontSize = sz.h2, fontWeight = FontWeight.SemiBold)
                }
                result?.penalties?.firstOrNull()?.let { p ->
                    Text(
                        SituationTexts.penaltyLine(result.id, p), color = Sc.warn, fontSize = sz.label, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = sz.dp(8f)).widthIn(max = sz.dp(520f)),
                    )
                }
            }
        }
    }
    AnimatedVisibility(view.phase == TrainingPhase.FAIL, Modifier.align(Alignment.Center), enter = enter, exit = exit) {
        Panel(border = Sc.warn, padding = sz.dp(30f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = sz.dp(360f), max = sz.dp(620f))) {
                Text("괜찮아요", color = Sc.warn, fontSize = sz.display, fontWeight = FontWeight.SemiBold)
                Text("한 번 더 해 볼까요?", color = Sc.text, fontSize = sz.h2, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    AnimatedVisibility(view.phase == TrainingPhase.BRIEFING && view.situation == null, Modifier.align(Alignment.Center), enter = enter, exit = exit) {
        Panel(padding = sz.dp(30f)) {
            Text(view.title, color = Sc.text, fontSize = sz.display, fontWeight = FontWeight.SemiBold)
            Text("선배 말을 듣고, 진짜 스위치로 대처해요", color = Sc.textDim, fontSize = sz.label)
        }
    }
    AnimatedVisibility(view.phase == TrainingPhase.CHAPTER_DONE, Modifier.align(Alignment.Center), enter = enter, exit = exit) {
        Panel(border = Sc.text, padding = sz.dp(34f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = sz.dp(420f))) {
                val res = view.chapterResult
                Text(
                    if (view.chapterId == ChapterCatalog.SURPRISE) "${view.surviveCount}개 버텼어요!" else "${SituationTexts.chapterTitle(view.chapterId)} 완료!",
                    color = Sc.text, fontSize = sz.display, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                )
                if (res != null) {
                    val scored = res.results.filter { it.id != SituationId.BEFORE_EXIT }
                    if (scored.isEmpty()) {
                        Text("확인 완료", color = Sc.ok, fontSize = sz.title, fontWeight = FontWeight.Medium)
                    } else {
                        Text("별 ${scored.sumOf { it.stars }} / ${scored.size * 3}", color = Sc.text, fontSize = sz.title, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(sz.dp(18f)))
                BigButton("결과 보기", Modifier.fillMaxWidth(), primary = true) { onAction(UiAction.ShowResult) }
            }
        }
    }
}
