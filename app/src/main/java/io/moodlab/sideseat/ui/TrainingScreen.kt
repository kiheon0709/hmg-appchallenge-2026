package io.moodlab.sideseat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import io.moodlab.sideseat.core.ChapterCatalog
import io.moodlab.sideseat.core.SituationId
import io.moodlab.sideseat.core.StepView
import io.moodlab.sideseat.core.TrainingPhase
import io.moodlab.sideseat.core.TrainingView
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.ui.scene.DriveScene
import io.moodlab.sideseat.ui.scene.SceneRender
import io.moodlab.sideseat.ui.scene.latchToSceneFrame
import io.moodlab.sideseat.voice.Persona

/**
 * 훈련 화면(정차 중). 가로는 주행 화면이 전체를 채우고 그 위에 상황 카드·체크리스트·초시계가 얹힌다.
 * 세로는 위가 주행 화면, 아래가 체크리스트다.
 */
@Composable
fun TrainingScreen(liveView: TrainingView, liveVehicle: VehicleState, onAction: (UiAction) -> Unit) {
    // 3D 장면은 이 화면 아래 층에 깔린다. 그때는 바탕을 칠하지 않고, 세로 화면의 아래 판만 따로 칠한다
    val seeThrough = SceneRender.seeThrough(liveView)
    // 3D 장면을 쓰는 동안에는 화면이 장면 프레임에 맞춰서만 바뀐다(창을 다시 그리는 횟수 = 장면 장수)
    val view = latchToSceneFrame(liveView, seeThrough)
    val vehicle = latchToSceneFrame(liveVehicle, seeThrough)
    SideSeatFrame(paintBackground = !seeThrough, alwaysDark = true) { sz ->
        val pulse by rememberInfiniteTransition(label = "alert").animateFloat(
            0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
        )
        val success = view.phase == TrainingPhase.SUCCESS
        val glowColor = if (success) Sc.ok else if (view.scene.alert > 0.05f) Sc.danger else Color(0xFFBFD4FF)
        val glow = {
            // 3D 장면 위에서는 빛 띠의 깜빡임도 장면 프레임에 맞춘다(같은 0.9초 왕복)
            val p = if (seeThrough) {
                @Suppress("UNUSED_EXPRESSION") SceneRender.frameTick
                val t = (System.nanoTime() / 1_000_000L % 1_800L) / 900f
                if (t <= 1f) t else 2f - t
            } else pulse
            if (success) 0.6f else if (view.scene.alert > 0.05f) view.scene.alert * (0.45f + 0.3f * p) else 0f
        }

        if (sz.wide) {
            Box(Modifier.fillMaxSize()) {
                DriveScene(view, vehicle, Modifier.fillMaxSize())
                EdgeGlow(glowColor, glow)
                SituationCard(view, Modifier.align(Alignment.TopStart).padding(sz.pad).widthIn(max = sz.width * 0.42f))
                SidePanel(
                    view, onAction,
                    // 힌트는 도로 가운데를 덮지 않게 이 판 안(조작 목록 아래)에 뜬다. 길어지면 판 안에서 스크롤한다
                    Modifier.align(Alignment.TopEnd).padding(sz.pad).width((sz.width * 0.3f).coerceIn(sz.dp(330f), sz.dp(520f)))
                        .heightIn(max = sz.height - sz.pad * 2).verticalScroll(rememberScrollState()),
                    hint = true,
                )
                CenterOverlays(view, onAction)
                if (seeThrough && !SceneRender.ready) SceneLoading()
                Row(Modifier.align(Alignment.BottomStart).padding(sz.pad), horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
                    BigButton("그만하기") { onAction(UiAction.AbortTraining) }
                    if (view.canSkip) BigButton("다음 상황으로") { onAction(UiAction.SkipSituation) }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(0.5f)) {
                    DriveScene(view, vehicle, Modifier.fillMaxSize())
                    SituationCard(view, Modifier.align(Alignment.TopStart).padding(sz.gap).widthIn(max = sz.width * 0.68f))
                    CenterOverlays(view, onAction)
                    if (seeThrough && !SceneRender.ready) SceneLoading()
                    // 빛 띠는 주행 화면에만 두른다(아래 체크리스트 판은 시스템 바탕 그대로)
                    EdgeGlow(glowColor, glow)
                    // 세로에서는 아래 판이 길어져도 항상 보이게 주행 화면 위에 둔다
                    BigButton("그만하기", Modifier.align(Alignment.TopEnd).padding(sz.gap)) { onAction(UiAction.AbortTraining) }
                }
                Box(Modifier.fillMaxWidth().weight(0.5f).background(Sc.bg)) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(sz.gap),
                        verticalArrangement = Arrangement.spacedBy(sz.dp(10f)),
                    ) {
                        // 세로에서는 힌트가 아래로 밀려 잘리지 않게 체크리스트 위에 둔다(선택지가 있으면 선택지 다음)
                        if (view.canSkip) BigButton("다음 상황으로", Modifier.fillMaxWidth()) { onAction(UiAction.SkipSituation) }
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
    val chapter = SituationTexts.chapterTitle(view.chapterId)
    val status = if (view.overtime) "시간이 지났어요 · 천천히 끝까지" else when (view.phase) {
        TrainingPhase.IDLE -> "준비"
        TrainingPhase.CRUISE -> "평온한 주행"
        TrainingPhase.BRIEFING -> "곧 시작"
        TrainingPhase.ACTIVE -> "지금!"
        TrainingPhase.HINT -> "힌트"
        TrainingPhase.SUCCESS -> "성공"
        TrainingPhase.FAIL -> "다시"
        TrainingPhase.CHAPTER_DONE -> "끝"
    }
    // 챕터 · 상태 한 줄과 상황 이름. 상태는 왼쪽 띠 색과 글자색으로만 알린다
    AccentPanel(if (live) Sc.danger else if (view.phase == TrainingPhase.SUCCESS) Sc.ok else Sc.outline, modifier, padding = sz.dp(16f)) {
        Row(horizontalArrangement = Arrangement.spacedBy(sz.dp(8f)), verticalAlignment = Alignment.CenterVertically) {
            // 지금 단계("지금!")는 중요한 정보라 작은 글씨로 두지 않는다
            if (chapter.isNotEmpty()) Text(chapter, color = Sc.textDim, fontSize = sz.label, fontWeight = FontWeight.Medium, maxLines = 1)
            if (view.chapterId == ChapterCatalog.SURPRISE) Text("${view.surviveCount}개 버팀", color = Sc.textDim, fontSize = sz.label, fontWeight = FontWeight.Medium, maxLines = 1)
            if (chapter.isNotEmpty()) Text("·", color = Sc.textDim, fontSize = sz.label)
            Text(status, color = if (live) Sc.danger else Sc.text, fontSize = sz.label, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        AnimatedVisibility(view.situation != null && view.phase != TrainingPhase.CRUISE, enter = fadeIn(tween(200)) + expandVertically(tween(200)), exit = fadeOut(tween(150)) + shrinkVertically(tween(150))) {
            val long = view.title.length > 8
            Text(
                view.title, color = Sc.text, fontSize = if (long) sz.h2 else sz.title, fontWeight = FontWeight.SemiBold,
                lineHeight = (if (long) sz.h2 else sz.title) * 1.2f, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = sz.dp(4f)),
            )
        }
    }
}

@Composable
private fun SidePanel(view: TrainingView, onAction: (UiAction) -> Unit, modifier: Modifier, hint: Boolean = false) {
    val sz = LocalSz.current
    val versusIdle = view.chapterId == ChapterCatalog.VERSUS && (view.phase == TrainingPhase.IDLE || view.phase == TrainingPhase.CRUISE)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(sz.gap)) {
        if (versusIdle) {
            Panel {
                Text("교관 대결", color = Sc.text, fontSize = sz.label, fontWeight = FontWeight.Medium)
                Text("조수석에서 상황을 골라 던져요", color = Sc.textDim, fontSize = sz.caption)
                Spacer(Modifier.height(sz.dp(12f)))
                SituationTexts.playable.chunked(2).forEach { row ->
                    Row(Modifier.padding(bottom = sz.dp(8f)), horizontalArrangement = Arrangement.spacedBy(sz.dp(8f))) {
                        row.forEach { def -> BigButton(SituationTexts.shortTitle(def.id), Modifier.weight(1f)) { onAction(UiAction.Inject(def.id)) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (view.steps.isNotEmpty() && view.phase != TrainingPhase.CRUISE && view.phase != TrainingPhase.CHAPTER_DONE) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("차의 진짜 스위치로 해요", color = Sc.textDim, fontSize = sz.caption, fontWeight = FontWeight.SemiBold)
                        Text("${view.steps.count { it.done }} / ${view.steps.size}", color = Sc.text, fontSize = sz.title, fontWeight = FontWeight.Medium)
                    }
                    Timer(view)
                }
                // 보기가 셋 이상인 질문이 떠 있으면 목록은 접어서 선택지가 화면 밖으로 밀리지 않게 한다("네/아직" 확인 질문은 목록과 같이 보인다)
                val showSteps = !sz.wide || (view.choice?.options?.size ?: 0) <= 2
                if (showSteps) Spacer(Modifier.height(sz.dp(14f)))
                val next = view.steps.indexOfFirst { !it.done }
                if (showSteps) {
                    view.steps.forEachIndexed { i, step -> StepRow(step, current = i == next && view.phase != TrainingPhase.BRIEFING) }
                }
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
        if (hint) HintBanner(view, Modifier.fillMaxWidth())
    }
}

/** 3D 장면이 뜨는 동안 검은 앞유리 대신 보여 주는 준비 표시. */
@Composable
private fun BoxScope.SceneLoading() {
    val sz = LocalSz.current
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B0E13)), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SeniorBadge(size = sz.dp(96f))
        Spacer(Modifier.height(sz.dp(20f)))
        Text("길을 준비하는 중이에요…", color = Color.White, fontSize = sz.h2, fontWeight = FontWeight.SemiBold)
        Text("화면 말고 이 차의 진짜 스위치로 해요", color = Color(0xFFD5D9E0), fontSize = sz.body, modifier = Modifier.padding(top = sz.dp(8f)))
    }
}

@Composable
private fun Timer(view: TrainingView) {
    val running = view.phase == TrainingPhase.ACTIVE || view.phase == TrainingPhase.HINT
    val limit = view.limitMs
    val sz = LocalSz.current
    val ring = sz.dp(if (sz.wide) 132f else 100f)
    val reaction = view.lastResult?.reactionMs?.takeIf { view.phase == TrainingPhase.SUCCESS }
    when {
        // 제한 시간이 지났어도 끝낼 수 있다: 0.0에서 멈춘 것처럼 보이지 않게 넘긴 시간을 보여 준다
        view.overtime -> TimerRing(1f, "+%.0f".format((view.elapsedMs - limit) / 1000f), size = ring, caption = "천천히")
        limit <= 0L -> TimerRing(0f, if (running) "%.0f".format(view.elapsedMs / 1000f) else "–", size = ring, caption = "천천히")
        limit <= 2500L -> TimerRing(
            if (running) view.elapsedMs / limit.toFloat() else 0f,
            // 성공 뒤에는 기준 시간이 아니라 잰 반응 시간을 보여 준다(가운데 판의 숫자와 같게)
            if (running) "%.2f".format(view.elapsedMs / 1000f) else "%.1f".format((reaction ?: limit) / 1000f), size = ring, caption = "반응 시간",
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
    val done by animateFloatAsState(if (step.done) 1f else 0f, tween(220), label = "step")
    // 목록은 칸막이 선으로만 나눈다. 지금 할 조작은 동그라미가 진하고, 끝낸 조작은 체크가 찬다
    Column {
        Box(Modifier.fillMaxWidth().height(sz.line).background(Sc.outline))
        Row(Modifier.fillMaxWidth().padding(vertical = sz.dp(14f)), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(sz.dp(34f))) {
                val s = size.minDimension
                val c = Offset(s / 2f, s / 2f)
                drawCircle(if (current) Sc.text else Sc.textDim, s * 0.42f, c, style = Stroke(s * (if (current) 0.09f else 0.07f)))
                if (done > 0.01f) {
                    drawCircle(Sc.ok, s * 0.47f, c, alpha = done)
                    val a = Offset(s * 0.29f, s * 0.52f); val b = Offset(s * 0.44f, s * 0.66f); val d = Offset(s * 0.72f, s * 0.36f)
                    drawLine(Sc.onAccent, a, b, s * 0.1f, cap = StrokeCap.Round, alpha = done)
                    drawLine(Sc.onAccent, b, Offset(b.x + (d.x - b.x) * done, b.y + (d.y - b.y) * done), s * 0.1f, cap = StrokeCap.Round, alpha = done)
                }
            }
            Spacer(Modifier.width(sz.dp(14f)))
            // 끝낸 조작은 흐리게 줄을 긋고, 아직 안 한 조작은 밝게 둔다(체크 아이콘 말고 글자로도 구별되게)
            Text(
                step.label, color = if (step.done) Sc.textDim.copy(alpha = 0.75f) else Sc.text, fontSize = sz.sp(29f),
                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (step.done) TextDecoration.LineThrough else null,
            )
        }
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
    // 힌트는 장면 효과(안개·김서림) 위에 바로 또렷하게 뜬다
    AnimatedVisibility(hint != null, modifier, enter = fadeIn(tween(120)) + slideInVertically(tween(160)) { it / 4 }, exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 4 }) {
        AccentPanel(Sc.warn) {
            Text("이렇게 해요", color = Sc.warn, fontSize = sz.caption, fontWeight = FontWeight.Medium)
            Text(hint.orEmpty(), color = Sc.text, fontSize = sz.body, fontWeight = FontWeight.SemiBold, lineHeight = sz.body * 1.3f)
            if (where != null) Text(where, color = Sc.text, fontSize = sz.label, lineHeight = sz.label * 1.35f)
        }
    }
}

@Composable
private fun BoxScope.CenterOverlays(view: TrainingView, onAction: (UiAction) -> Unit) {
    val sz = LocalSz.current
    // 튀어나오지 않고 살짝 떠오르며 나타난다
    val enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 8 }
    val exit = fadeOut(tween(200))
    val result = view.lastResult

    AnimatedVisibility(view.phase == TrainingPhase.SUCCESS && result != null, Modifier.align(Alignment.Center), enter = enter, exit = exit) {
        AccentPanel(Sc.ok, padding = sz.dp(28f)) {
            Column(modifier = Modifier.widthIn(min = sz.dp(340f))) {
                // 내리기 전 확인은 점수를 매기지 않는다
                val checkOnly = result?.id == SituationId.BEFORE_EXIT
                if (!checkOnly) {
                    Stars(result?.stars ?: 0, size = sz.dp(44f), animateKey = result)
                    Spacer(Modifier.height(sz.dp(12f)))
                }
                Text(
                    if (checkOnly) "확인 완료" else when (result?.stars) { 3 -> "완벽해요!"; 2 -> "좋아요!"; else -> "됐어요!" },
                    color = Sc.text, fontSize = sz.title, fontWeight = FontWeight.SemiBold,
                )
                result?.reactionMs?.takeIf { !checkOnly }?.let {
                    Text(SituationTexts.seconds(it) + " 걸렸어요", color = Sc.textDim, fontSize = sz.body, fontWeight = FontWeight.Medium)
                }
                result?.penalties?.firstOrNull()?.let { p ->
                    Text(
                        SituationTexts.penaltyLine(result.id, p), color = Sc.warn, fontSize = sz.label,
                        modifier = Modifier.padding(top = sz.dp(8f)).widthIn(max = sz.dp(520f)),
                    )
                }
            }
        }
    }
    // 실패 판은 아래 힌트 판과 겹치지 않게 조금 위에 둔다
    // 가로에서는 오른쪽 조작 목록과 겹치지 않게 왼쪽으로 조금 비켜 둔다
    AnimatedVisibility(
        view.phase == TrainingPhase.FAIL,
        Modifier.align(Alignment.Center).padding(bottom = sz.height * 0.24f, end = if (sz.wide) sz.width * 0.3f else sz.dp(0f)),
        enter = enter, exit = exit,
    ) {
        AccentPanel(Sc.warn, padding = sz.dp(28f)) {
            Column(modifier = Modifier.widthIn(min = sz.dp(340f), max = sz.dp(480f))) {
                Text("괜찮아요", color = Sc.text, fontSize = sz.title, fontWeight = FontWeight.SemiBold)
                Text(
                    if (view.canSkip) "한 번 더 해 볼게요. 넘어가려면 '다음 상황으로'를 눌러요." else "다음으로 넘어갈게요.",
                    color = Sc.textDim, fontSize = sz.body, fontWeight = FontWeight.Medium, lineHeight = sz.body * 1.3f,
                )
            }
        }
    }
    // 챕터를 시작할 때 이 앱의 핵심을 한 번 크게 알려 준다: 화면이 아니라 이 차의 스위치로 답한다
    AnimatedVisibility(view.opening, Modifier.align(Alignment.Center), enter = enter, exit = exit) {
        AccentPanel(Sc.accent, padding = sz.dp(30f)) {
            Column(modifier = Modifier.widthIn(max = sz.dp(700f))) {
                Text("화면 말고, 이 차의 진짜 스위치로 해요", color = Sc.text, fontSize = sz.title, fontWeight = FontWeight.Bold, lineHeight = sz.title * 1.25f)
                Text(
                    "와이퍼, 전조등, 브레이크를 실제로 움직이면 ${Persona.NAME}가 알아봐요. 화면의 조작 목록은 누르는 곳이 아니에요.",
                    color = Sc.textDim, fontSize = sz.body, lineHeight = sz.body * 1.35f, modifier = Modifier.padding(top = sz.dp(10f)),
                )
            }
        }
    }
    AnimatedVisibility(view.phase == TrainingPhase.CHAPTER_DONE, Modifier.align(Alignment.Center), enter = enter, exit = exit) {
        Panel(padding = sz.dp(30f)) {
            Column(modifier = Modifier.widthIn(min = sz.dp(420f), max = sz.dp(560f))) {
                val res = view.chapterResult
                // 끝내지 못한 상황이 있으면 "완료!"라고 하지 않고, 하나도 못 버텼으면 "버텼어요!"라고 하지 않는다
                val unfinished = res?.results?.any { it.stars == 0 } == true
                Text(
                    when {
                        view.chapterId == ChapterCatalog.SURPRISE && view.surviveCount == 0 -> "이번엔 못 버텼어요"
                        view.chapterId == ChapterCatalog.SURPRISE -> "${view.surviveCount}개 버텼어요!"
                        unfinished -> "${SituationTexts.chapterTitle(view.chapterId)} 끝"
                        else -> "${SituationTexts.chapterTitle(view.chapterId)} 완료!"
                    },
                    color = Sc.text, fontSize = sz.title, fontWeight = FontWeight.SemiBold,
                )
                if (unfinished && view.chapterId != ChapterCatalog.SURPRISE) {
                    Text("놓친 조작은 결과에서 같이 볼게요.", color = Sc.textDim, fontSize = sz.label, modifier = Modifier.padding(top = sz.dp(4f)))
                }
                if (res != null) {
                    val scored = res.results.filter { it.id != SituationId.BEFORE_EXIT }
                    if (scored.isEmpty()) {
                        Text("확인 완료", color = Sc.ok, fontSize = sz.h2, fontWeight = FontWeight.Medium)
                    } else {
                        Text("별 ${scored.sumOf { it.stars }} / ${scored.size * 3}", color = Sc.textDim, fontSize = sz.h2, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(sz.dp(18f)))
                BigButton("결과 보기", Modifier.fillMaxWidth(), primary = true) { onAction(UiAction.ShowResult) }
            }
        }
    }
}
