package io.moodlab.sideseat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.moodlab.sideseat.core.ChapterInfo
import io.moodlab.sideseat.core.SceneParams
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.ui.scene.DriveScene
import io.moodlab.sideseat.voice.Persona

/** 홈(정차). 가로: 왼쪽 선배 인사와 추천 훈련, 오른쪽 챕터·면허·미니게임. 세로: 위아래로 쌓는다. */
@Composable
fun HomeScreen(state: HomeUi, onAction: (UiAction) -> Unit) {
    SideSeatFrame { sz ->
        // 차종 고르기: 처음에는 먼저 뜨고, 그 뒤에는 "차종 바꾸기"로 연다. 정차 중에만 보인다
        var pickerOpen by remember { mutableStateOf(false) }
        if (state.vehicleChoices.isNotEmpty() && state.canTrain && (!state.vehicleChosen || pickerOpen)) {
            VehiclePicker(state, onChoose = { pickerOpen = false; onAction(UiAction.ChooseVehicle(it)) }, onClose = { pickerOpen = false })
        } else if (sz.wide) {
            Row(Modifier.fillMaxSize().padding(sz.pad), horizontalArrangement = Arrangement.spacedBy(sz.pad)) {
                Column(Modifier.weight(0.46f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                    Hero(state, onAction, Modifier.fillMaxWidth().weight(1f))
                    TalkArea(state, onAction)
                }
                Column(
                    Modifier.weight(0.54f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(sz.gap),
                ) { Catalog(state, onAction) { pickerOpen = true } }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(sz.pad), verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                Hero(state, onAction, Modifier.fillMaxWidth().height(sz.height * 0.5f))
                TalkArea(state, onAction)
                Catalog(state, onAction) { pickerOpen = true }
            }
        }
    }
}

/** 홈 미리보기 그림(3D 훈련 장면 캡처). 앱과 화면 렌더 도구가 넣어 준다. 없으면 주행 장면을 그린다. */
val LocalHomeArt = staticCompositionLocalOf<ImageBitmap?> { null }

@Composable
private fun Hero(state: HomeUi, onAction: (UiAction) -> Unit, modifier: Modifier) {
    val sz = LocalSz.current
    val shape = RoundedCornerShape(sz.dp(28f))
    Box(modifier.card(shape, HeroBase)) {
        // 위쪽은 장면, 아래쪽은 어두운 바탕. 인사와 추천은 늘 어두운 바탕 위에 놓여서 장면이 밝아도 잘 읽힌다
        val art = LocalHomeArt.current
        if (art == null) {
            val calm = remember { SceneParams() }
            val car = remember { VehicleState(timestampMs = 0, parked = true, lowBeamOn = true) }
            DriveScene(calm, car, 80f, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to HeroBase.copy(alpha = 0.3f), 0.3f to HeroBase.copy(alpha = 0.1f), 0.6f to HeroBase.copy(alpha = 0.9f), 1f to HeroBase)))
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (art != null) {
                    Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, 0.5f))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to HeroBase.copy(alpha = 0.12f), 0.55f to HeroBase.copy(alpha = 0f), 1f to HeroBase)))
                }
            }
            HeroText(state, onAction)
        }
        Row(
            Modifier.align(Alignment.TopStart).padding(sz.dp(20f)).clip(RoundedCornerShape(sz.dp(22f))).background(HeroBase.copy(alpha = 0.62f))
                .padding(start = sz.dp(10f), top = sz.dp(10f), end = sz.dp(22f), bottom = sz.dp(10f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeniorBadge(size = sz.dp(60f), speaking = state.talk.speaking)
            Spacer(Modifier.width(sz.dp(14f)))
            Column {
                Text(Persona.NAME, color = HeroWhite, fontSize = sz.sp(28f), fontWeight = FontWeight.Bold)
                Text(state.vehicleName ?: "옆자리에 ${Persona.NAME}가 앉아요", color = HeroDim, fontSize = sz.caption)
            }
        }
    }
}

@Composable
private fun HeroText(state: HomeUi, onAction: (UiAction) -> Unit) {
    val sz = LocalSz.current
    Column(Modifier.fillMaxWidth().padding(start = sz.pad, end = sz.pad, bottom = sz.pad), verticalArrangement = Arrangement.spacedBy(sz.dp(8f))) {
        Text(state.greeting, color = HeroWhite, fontSize = sz.title, fontWeight = FontWeight.Bold, lineHeight = sz.title * 1.2f, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(state.subGreeting, color = HeroDim, fontSize = sz.body, lineHeight = sz.body * 1.3f, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(sz.dp(10f)))
        val resume = state.chapters.firstOrNull { it.id == state.resumeChapterId }
        val pick = resume ?: state.chapters.firstOrNull { it.id == state.recommendedChapterId } ?: state.chapters.firstOrNull { !it.locked }
        if (!state.canTrain) {
            Chip("주차(P)에 두면 훈련을 시작할 수 있어요", color = HeroWarn, bg = Color(0x33FFC266), size = sz.label)
        } else if (pick != null) {
            RecommendCard(
                eyebrow = if (resume != null) "이어서 하기" else state.recommendLabel,
                title = pick.title,
                sub = state.recommendReason?.takeIf { resume == null } ?: "약 ${pick.estMinutes}분 · 상황 ${pick.situations.size}개",
            ) {
                // 가는 길 추천이면 그 상황부터 시작한다(끊긴 훈련을 잇는 중이 아닐 때)
                onAction(UiAction.StartChapter(pick.id, state.recommendedSituation.takeIf { resume == null && pick.id == state.recommendedChapterId }))
            }
        }
    }
}

/** 홈의 주 행동: 추천 훈련 시작. 강조색으로 채워 화면에서 가장 먼저 보이게 한다. */
@Composable
private fun RecommendCard(eyebrow: String, title: String, sub: String, onClick: () -> Unit) {
    val sz = LocalSz.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(sz.radius)).background(HeroAccent).clickable(onClick = onClick)
            .padding(start = sz.dp(26f), top = sz.dp(20f), end = sz.dp(20f), bottom = sz.dp(20f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(sz.dp(2f))) {
            Text(eyebrow, color = HeroWhite.copy(alpha = 0.85f), fontSize = sz.caption, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(title, color = HeroWhite, fontSize = sz.h2, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = HeroWhite.copy(alpha = 0.92f), fontSize = sz.label, lineHeight = sz.label * 1.3f, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(sz.dp(16f)))
        Canvas(Modifier.size(sz.dp(68f))) {
            val s = size.minDimension
            drawCircle(HeroWhite)
            val p = Path().apply { moveTo(s * 0.41f, s * 0.31f); lineTo(s * 0.69f, s * 0.5f); lineTo(s * 0.41f, s * 0.69f); close() }
            drawPath(p, HeroAccent)
        }
    }
}

/** 차종 고르기. 조사된 차종 가운데 하나를 고르면 힌트·결과·질문 답이 그 차의 스위치 위치로 나온다. */
@Composable
private fun VehiclePicker(state: HomeUi, onChoose: (String?) -> Unit, onClose: () -> Unit) {
    val sz = LocalSz.current
    Box(Modifier.fillMaxSize().padding(sz.pad), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = sz.dp(1000f)).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(sz.gap),
        ) {
            Text("어떤 차를 타고 있나요?", color = Sc.text, fontSize = sz.title, fontWeight = FontWeight.SemiBold)
            Text(
                "스위치가 어디 있는지 이 차에 맞춰 알려 줄게요. 나중에 홈에서 바꿀 수 있어요.",
                color = Sc.textDim, fontSize = sz.body, lineHeight = sz.body * 1.35f,
            )
            Spacer(Modifier.height(sz.dp(4f)))
            // 마지막 칸은 "그 밖의 차"(아이디 없음)
            val items: List<VehicleChoice?> = state.vehicleChoices + null
            items.chunked(if (sz.wide) 2 else 1).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
                    row.forEach { c ->
                        val selected = state.vehicleChosen && state.vehicleChoiceId == c?.id
                        BigButton(
                            c?.name ?: "그 밖의 차", Modifier.weight(1f), primary = selected,
                            sub = if (c == null) "차종을 정하지 않고 알려 줘요" else null,
                        ) { onChoose(c?.id) }
                    }
                    if (row.size == 1 && sz.wide) Spacer(Modifier.weight(1f))
                }
            }
            if (state.vehicleChosen) BigButton("닫기", Modifier.padding(top = sz.dp(8f)), onClick = onClose)
        }
    }
}

@Composable
private fun TalkArea(state: HomeUi, onAction: (UiAction) -> Unit) {
    val sz = LocalSz.current
    Column(verticalArrangement = Arrangement.spacedBy(sz.dp(12f))) {
        // 타비가 한 말(또는 듣는 중인 내 말)은 말풍선 카드로, 질문하기는 그 아래 버튼으로 나눈다
        if (!state.talk.partialText.isNullOrBlank() || !state.talk.answerText.isNullOrBlank()) {
            Row(Modifier.fillMaxWidth().card().padding(sz.dp(18f))) {
                SeniorBadge(size = sz.dp(44f), speaking = state.talk.speaking)
                Spacer(Modifier.width(sz.dp(14f)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(sz.dp(4f))) {
                    Text(Persona.NAME, color = Sc.textDim, fontSize = sz.caption, fontWeight = FontWeight.SemiBold)
                    TalkCaption(state.talk, maxLines = 5)
                }
            }
        }
        TalkButton(state.talk, Modifier.fillMaxWidth(), height = sz.dp(92f), tonal = true, onAction = onAction)
    }
}

@Composable
private fun Catalog(state: HomeUi, onAction: (UiAction) -> Unit, onPickVehicle: () -> Unit) {
    val sz = LocalSz.current
    // 이 차와 이 차 면허를 한 카드에 묶는다. 스위치 위치 안내가 이 차 기준으로 나온다
    Column(Modifier.fillMaxWidth().card().padding(horizontal = sz.dp(24f), vertical = sz.dp(18f)), verticalArrangement = Arrangement.spacedBy(sz.dp(14f))) {
        if (state.vehicleChoices.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("이 차", color = Sc.textDim, fontSize = sz.caption, fontWeight = FontWeight.SemiBold)
                    Text(state.vehicleName ?: "그 밖의 차", color = Sc.text, fontSize = sz.sp(30f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(sz.gap))
                BigButton("차종 바꾸기", enabled = state.canTrain, onClick = onPickVehicle)
            }
            Box(Modifier.fillMaxWidth().height(sz.dp(1f)).background(Sc.outline))
        }
        LicenseBar(state.progress, state.vehicleModelKey, state.vehicleName, bare = true)
    }
    SectionTitle("챕터")
    Column(verticalArrangement = Arrangement.spacedBy(sz.dp(10f))) {
        state.chapters.forEach { ch -> ChapterRow(ch, state) { onAction(UiAction.StartChapter(ch.id)) } }
    }
    SectionTitle("더 해 보기")
    Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
        BigButton("돌발 모드", Modifier.weight(1f), enabled = state.canTrain, sub = "최고 ${state.progress.surpriseBest}개 버팀") { onAction(UiAction.StartSurprise) }
        BigButton("교관 대결", Modifier.weight(1f), enabled = state.canTrain, sub = "조수석이 상황을 던져요") { onAction(UiAction.StartVersus) }
    }
    val open = state.miniGamesUnlocked && state.canTrain
    Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
        BigButton("금고 다이얼", Modifier.weight(1f), enabled = open, sub = "핸들로 열어요") { onAction(UiAction.OpenSafeDial) }
        BigButton("깜빡이 모스", Modifier.weight(1f), enabled = open, sub = "깜빡이로 따라 해요") { onAction(UiAction.OpenTurnSignalGame) }
        BigButton("창문 버저", Modifier.weight(1f), enabled = open, sub = "창문으로 먼저 눌러요") { onAction(UiAction.OpenWindowBuzzer) }
    }
    if (!state.miniGamesUnlocked) Text("미니게임은 챕터를 하나 깨면 열려요.", color = Sc.textDim, fontSize = sz.caption)
    Text(OSM_CREDIT, color = Sc.textDim, fontSize = sz.caption)
}

@Composable
private fun SectionTitle(text: String) {
    val sz = LocalSz.current
    Text(text, color = Sc.text, fontSize = sz.label, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = sz.dp(6f), top = sz.dp(6f)))
}

/** 챕터 한 줄: 번호, 제목과 상황 목록, 오른쪽에 진행 상태(모은 별 또는 걸리는 시간). */
@Composable
private fun ChapterRow(ch: ChapterInfo, state: HomeUi, onClick: () -> Unit) {
    val sz = LocalSz.current
    val enabled = !ch.locked && state.canTrain
    val best = state.progress.chapterBestStars[ch.id]
    val max = ch.situations.size * 3
    val recommended = ch.id == state.recommendedChapterId
    val shape = RoundedCornerShape(sz.radius)
    Row(
        Modifier.fillMaxWidth().card(shape)
            .then(if (recommended) Modifier.border(sz.dp(2f), Sc.accent, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = sz.dp(18f), vertical = sz.dp(14f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(sz.dp(60f)).clip(RoundedCornerShape(sz.dp(16f))).background(if (recommended) Sc.accent else Sc.surfaceHi),
            contentAlignment = Alignment.Center,
        ) {
            Text(ch.id, color = if (recommended) Sc.onAccent else if (enabled) Sc.text else Sc.textDim, fontSize = sz.label, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(sz.dp(18f)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(sz.dp(2f))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ch.title, color = if (enabled) Sc.text else Sc.textDim, fontSize = sz.sp(28f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (recommended) {
                    Spacer(Modifier.width(sz.dp(12f)))
                    Chip(state.recommendLabel, color = Sc.accent, bg = Sc.accent.copy(alpha = 0.12f))
                }
                // 끊긴 챕터는 누르면 남은 상황부터 이어 한다
                if (ch.id in state.resumeChapterIds) {
                    Spacer(Modifier.width(sz.dp(8f)))
                    Chip("이어서", color = Sc.text, bg = Sc.surfaceHi)
                }
            }
            Text(
                ch.situations.joinToString(" · ") { SituationTexts.title(it) }, color = Sc.textDim, fontSize = sz.caption,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(sz.dp(14f)))
        when {
            ch.locked -> Text("이 차에서는 잠김", color = Sc.textDim, fontSize = sz.caption, fontWeight = FontWeight.SemiBold)
            best != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Stars(1, max = 1, size = sz.dp(30f))
                Spacer(Modifier.width(sz.dp(6f)))
                Text("$best", color = Sc.text, fontSize = sz.body, fontWeight = FontWeight.Bold)
                Text(" / $max", color = Sc.textDim, fontSize = sz.label, fontWeight = FontWeight.Medium)
            }
            else -> Text("약 ${ch.estMinutes}분", color = Sc.textDim, fontSize = sz.label, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(sz.dp(10f)))
        Canvas(Modifier.size(sz.dp(22f))) {
            val c = if (enabled) Sc.textDim else Sc.outline
            val w = size.width; val st = w * 0.13f
            drawLine(c, Offset(w * 0.36f, w * 0.2f), Offset(w * 0.68f, w * 0.5f), st, cap = StrokeCap.Round)
            drawLine(c, Offset(w * 0.68f, w * 0.5f), Offset(w * 0.36f, w * 0.8f), st, cap = StrokeCap.Round)
        }
    }
}

// 사진·장면 위에 얹는 글자는 테마와 상관없이 밝은 색으로 고정한다
private val HeroWhite = Color(0xFFFFFFFF)
private val HeroDim = Color(0xFFD5D9E0)
private val HeroBase = Color(0xFF0B0E13)
private val HeroAccent = Color(0xFF2563EB)
private val HeroWarn = Color(0xFFFFC266)
