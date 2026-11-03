package io.moodlab.moodrive.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.moodlab.moodrive.UiState
import io.moodlab.moodrive.core.Mood

/**
 * 주행 화면. 조작 요소 3개(좋아·별로·말하기), 정보 덩어리 3개(장면·곡·DJ 한 줄).
 * 프로토타입 design/prototype/index.html #drive 와 같은 구성. 데모 패널은 기어 칩을 길게 눌러 연다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DriveScreen(
    ui: UiState,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onMic: () -> Unit,
    onLongPressGear: () -> Unit,
    onRestStop: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxSize().background(Mc.bg).padding(Ms.l), horizontalArrangement = Arrangement.spacedBy(Ms.l)) {
        // ── 왼쪽: 무드 스테이지 ──
        val target = ui.screenAmbient?.color?.let { Color(it.toArgb()) } ?: Mc.surface
        val glowColor by animateColorAsState(target, tween(Ms.ambientMs), label = "glow")
        val strength by animateFloatAsState(if (ui.ambientHeld) 0.55f else 1f, tween(Ms.ambientMs), label = "strength")
        val bright = (ui.screenAmbient?.brightness ?: 30) / 100f
        Box(
            Modifier.weight(0.58f).fillMaxHeight().clip(RoundedCornerShape(Ms.radiusL)).background(Mc.surface)
                .drawBehind {
                    // 프로토타입과 같은 위치: 오른쪽 위(85%, 10%)에서 퍼지는 글로우
                    drawRect(
                        Brush.radialGradient(
                            0f to glowColor.copy(alpha = (0.25f + bright * 0.6f) * strength),
                            0.45f to glowColor.copy(alpha = 0.12f * strength),
                            0.8f to Color.Transparent,
                            center = Offset(size.width * 0.85f, size.height * 0.1f),
                            radius = size.maxDimension,
                        ),
                    )
                }
                .padding(Ms.xl),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Ms.m)) {
                    val s = ui.snapshot
                    Pill(s?.gear?.name ?: "-", Modifier.combinedClickable(onClick = {}, onLongClick = onLongPressGear))
                    s?.let { Pill("%02d:%02d".format(it.minuteOfDay / 60, it.minuteOfDay % 60)) }
                    Pill(s?.etaMinutes?.let { "도착 ${it}분" } ?: "목적지 없음")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    ui.scene?.label ?: "—", color = Mc.text, fontSize = Ms.sceneDisplay, fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = Shadow(Color(0x73000000), blurRadius = 24f), lineHeight = 108.sp),
                )
                ui.mood?.let { Pill(moodWords(it), Modifier.padding(top = Ms.s), size = Ms.body) }
                Box(Modifier.padding(top = Ms.l).heightIn(min = 74.dp)) {
                    if (ui.djLine != null) { // ViewModel이 12초 뒤 지움
                        Text(
                            "🎙  ${ui.djLine}", color = Mc.text, fontSize = Ms.body,
                            modifier = Modifier.clip(RoundedCornerShape(Ms.radiusM)).background(Mc.scrim).padding(horizontal = Ms.m, vertical = Ms.s),
                        )
                    }
                }
            }
            if (ui.ambientHeld) {
                Pill("실내 조명은 정차 시 적용", Modifier.align(Alignment.TopEnd), color = Mc.warning)
            }
        }

        // ── 오른쪽: 플레이어 ──
        Column(
            Modifier.weight(0.42f).fillMaxHeight().clip(RoundedCornerShape(Ms.radiusL)).background(Mc.surface).padding(Ms.xl),
        ) {
            val t = ui.track
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ms.l), modifier = Modifier.padding(top = Ms.s)) {
                Box(
                    Modifier.size(232.dp).clip(RoundedCornerShape(Ms.radiusM))
                        .background(Brush.linearGradient(listOf(glowColor, Mc.surfaceRaised))),
                )
                Column {
                    Text(t?.title ?: "—", color = Mc.text, fontSize = Ms.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t?.let { "${it.artist} · ${it.genre}" } ?: "", color = Mc.textDim, fontSize = Ms.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Ms.xs))
                }
            }
            val progress = if (t != null) (ui.positionSec / t.durationSec.toFloat()).coerceIn(0f, 1f) else 0f
            Box(Modifier.padding(top = Ms.l).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Mc.outline)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Mc.text))
            }
            val eta = ui.snapshot?.etaMinutes
            Text(
                if (eta != null && eta <= 5) "도착 피날레 곡" else ui.scene?.let { "${it.label}에 맞춘 선곡" } ?: "",
                color = Mc.textDim, fontSize = Ms.label, modifier = Modifier.padding(top = Ms.m),
            )
            Spacer(Modifier.weight(1f))
            if (ui.restStopPrompt) {
                // 휴게소 제안: 버튼 2개로 교체 (주행 중 조작 요소 4개 이하 유지)
                Text("가까운 휴게소를 경유지로 넣을까요?", color = Mc.text, fontSize = Ms.label, modifier = Modifier.padding(bottom = Ms.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Ms.s)) {
                    BigButton("괜찮아요", Modifier.weight(1f)) { onRestStop(false) }
                    BigButton("경유지 추가", Modifier.weight(1f), primary = true) { onRestStop(true) }
                    // 음성으로도 답할 수 있게 말하기 유지 (조작 요소 3개)
                    BigButton(if (ui.listening) "듣는 중…" else "🎤", Modifier.weight(0.6f), onClick = onMic)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Ms.s)) {
                    BigButton("👍  좋아", Modifier.weight(1f), onClick = onLike)
                    BigButton("✕  별로", Modifier.weight(1f), onClick = onDislike)
                    BigButton(if (ui.listening) "듣는 중…" else "🎤  말하기", Modifier.weight(1f), primary = true, onClick = onMic)
                }
            }
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = Mc.text, size: TextUnit = Ms.label) {
    Text(
        text, color = color, fontSize = size, fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(999.dp)).background(Mc.scrim).padding(horizontal = Ms.s, vertical = 4.dp),
    )
}

@Composable
fun BigButton(label: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Ms.touchPrimary),
        shape = RoundedCornerShape(Ms.radiusM),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Mc.accent else Mc.surfaceRaised,
            contentColor = if (primary) Mc.onAccent else Mc.text,
        ),
    ) { Text(label, fontSize = Ms.label, fontWeight = FontWeight.SemiBold) }
}

/** 프로토타입 moodWords()와 같은 규칙 */
fun moodWords(m: Mood): String {
    val e = when { m.energy > 0.35f -> "신나게"; m.energy < -0.35f -> "차분하게"; else -> "적당히" }
    val v = when { m.valence > 0.35f -> "밝고 따뜻하게"; m.valence < -0.35f -> "깊고 어둡게"; else -> "담담하게" }
    return "$e · $v"
}

