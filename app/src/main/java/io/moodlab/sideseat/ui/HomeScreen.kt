package io.moodlab.sideseat.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
                Hero(state, onAction, Modifier.fillMaxWidth().height(sz.height * 0.46f))
                TalkArea(state, onAction)
                Catalog(state, onAction) { pickerOpen = true }
            }
        }
    }
}

@Composable
private fun Hero(state: HomeUi, onAction: (UiAction) -> Unit, modifier: Modifier) {
    val sz = LocalSz.current
    val calm = remember { SceneParams() }
    val car = remember { VehicleState(timestampMs = 0, parked = true, lowBeamOn = true) }
    Box(modifier.clip(RoundedCornerShape(sz.radius)).border(sz.line, Sc.outline, RoundedCornerShape(sz.radius))) {
        DriveScene(calm, car, 80f, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0xA6000000), 0.35f to Color(0x22000000), 0.6f to Color(0x73000000), 1f to Color(0xF2000000)),
            ),
        )
        Row(Modifier.align(Alignment.TopStart).padding(sz.pad), verticalAlignment = Alignment.CenterVertically) {
            SeniorBadge(speaking = state.talk.speaking)
            Spacer(Modifier.width(sz.dp(14f)))
            Column {
                Text("옆자리", color = HeroText, fontSize = sz.h2, fontWeight = FontWeight.SemiBold)
                Text(state.vehicleName ?: "옆자리에 ${Persona.NAME}가 앉아요", color = HeroDim, fontSize = sz.caption)
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(sz.pad), verticalArrangement = Arrangement.spacedBy(sz.dp(10f))) {
            Text(state.greeting, color = HeroText, fontSize = sz.title, fontWeight = FontWeight.SemiBold, lineHeight = sz.title * 1.2f, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(state.subGreeting, color = HeroDim, fontSize = sz.body, lineHeight = sz.body * 1.3f, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(sz.dp(6f)))
            val resume = state.chapters.firstOrNull { it.id == state.resumeChapterId }
            val pick = resume ?: state.chapters.firstOrNull { it.id == state.recommendedChapterId } ?: state.chapters.firstOrNull { !it.locked }
            if (!state.canTrain) {
                Chip("주차(P)에 두면 훈련을 시작할 수 있어요", color = Sc.warn, size = sz.label)
            } else if (pick != null) {
                BigButton(
                    if (resume != null) "이어서 하기 · ${pick.title}" else "${state.recommendLabel} · ${pick.title}",
                    Modifier.fillMaxWidth(), primary = true, color = Color.White,
                    sub = state.recommendReason?.takeIf { resume == null } ?: "약 ${pick.estMinutes}분 · 상황 ${pick.situations.size}개",
                ) { onAction(UiAction.StartChapter(pick.id)) }
            }
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
    Column(verticalArrangement = Arrangement.spacedBy(sz.dp(10f))) {
        TalkCaption(state.talk, Modifier.fillMaxWidth().clip(RoundedCornerShape(sz.radius)).background(Sc.surface).padding(sz.dp(18f)), maxLines = 5)
        TalkButton(state.talk, Modifier.fillMaxWidth(), height = sz.dp(96f), onAction = onAction)
    }
}

@Composable
private fun Catalog(state: HomeUi, onAction: (UiAction) -> Unit, onPickVehicle: () -> Unit) {
    val sz = LocalSz.current
    if (state.vehicleChoices.isNotEmpty()) {
        // 지금 고른 차종. 스위치 위치 안내가 이 차 기준으로 나온다
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(sz.radius)).background(Sc.surface).padding(horizontal = sz.dp(20f), vertical = sz.dp(14f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("이 차", color = Sc.textDim, fontSize = sz.caption, fontWeight = FontWeight.Medium)
                Text(state.vehicleName ?: "그 밖의 차", color = Sc.text, fontSize = sz.body, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(sz.gap))
            BigButton("차종 바꾸기", enabled = state.canTrain, onClick = onPickVehicle)
        }
    }
    LicenseBar(state.progress, state.vehicleModelKey, state.vehicleName)
    Text("챕터", color = Sc.textDim, fontSize = sz.label, fontWeight = FontWeight.Medium)
    state.chapters.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
            row.forEach { ch -> ChapterCard(ch, state, Modifier.weight(1f)) { onAction(UiAction.StartChapter(ch.id)) } }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Text("더 해 보기", color = Sc.textDim, fontSize = sz.label, fontWeight = FontWeight.Medium)
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
private fun ChapterCard(ch: ChapterInfo, state: HomeUi, modifier: Modifier, onClick: () -> Unit) {
    val sz = LocalSz.current
    val shape = RoundedCornerShape(sz.radius)
    val enabled = !ch.locked && state.canTrain
    val best = state.progress.chapterBestStars[ch.id]
    val max = ch.situations.size * 3
    val recommended = ch.id == state.recommendedChapterId
    Column(
        modifier.clip(shape).background(Sc.bg)
            .border(sz.line, if (recommended) Sc.text else Sc.outline, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(sz.dp(20f)),
        verticalArrangement = Arrangement.spacedBy(sz.dp(6f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ch.id, color = Sc.textDim, fontSize = sz.caption, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(
                when { ch.locked -> "이 차에서는 잠김"; best != null -> "별 $best / $max"; else -> "약 ${ch.estMinutes}분" },
                color = if (best != null) Sc.text else Sc.textDim, fontSize = sz.caption, fontWeight = FontWeight.SemiBold,
            )
        }
        Text(ch.title, color = if (enabled) Sc.text else Sc.textDim, fontSize = sz.h2, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(ch.story, color = Sc.textDim, fontSize = sz.caption, lineHeight = sz.caption * 1.4f, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            ch.situations.joinToString(" · ") { SituationTexts.title(it) }, color = Sc.text, fontSize = sz.caption,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = sz.dp(4f)),
        )
    }
}

// 사진·장면 위에 얹는 글자는 테마와 상관없이 밝은 색으로 고정한다
private val HeroText = Color(0xFFFFFFFF)
private val HeroDim = Color(0xFFD9D9D9)
