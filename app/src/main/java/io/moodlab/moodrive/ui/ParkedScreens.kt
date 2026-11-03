package io.moodlab.moodrive.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.moodlab.moodrive.TripCard
import io.moodlab.moodrive.core.AmbientCommand
import io.moodlab.moodrive.core.Genres

/** 원색은 어두운 UI에서 튀므로 배경과 섞는다 (프로토타입 soft()와 같은 규칙) */
private fun soft(c: AmbientCommand, k: Float = 0.72f): Color = lerp(Mc.bg, Color(c.color.toArgb()), k)

/** 정차 후 「오늘의 드라이브」 카드. 프로토타입 #card 와 같은 구성. */
@Composable
fun TripCardScreen(card: TripCard, onClose: () -> Unit) {
    Row(Modifier.fillMaxSize().background(Mc.bg).padding(Ms.xl), horizontalArrangement = Arrangement.spacedBy(Ms.xl)) {
        Column(Modifier.weight(0.6f).fillMaxHeight()) {
            Text("오늘의 드라이브", color = Mc.text, fontSize = Ms.title, fontWeight = FontWeight.Bold)
            Text("${card.startClock} 출발 · ${card.endClock} 도착", color = Mc.textDim, fontSize = Ms.body, modifier = Modifier.padding(top = Ms.xs))
            Row(Modifier.padding(top = Ms.l).fillMaxWidth().height(72.dp).clip(RoundedCornerShape(Ms.radiusS))) {
                card.segments.forEach { seg -> Box(Modifier.weight(seg.weight).fillMaxHeight().background(soft(seg.color))) }
            }
            Row(Modifier.padding(top = Ms.s), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                card.segments.distinctBy { it.scene }.forEach { seg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(soft(seg.color)))
                        Text(seg.scene.label, color = Mc.textDim, fontSize = Ms.caption, modifier = Modifier.padding(start = Ms.xs))
                    }
                }
            }
            Column(Modifier.padding(top = Ms.l), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                card.moments.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Ms.l)) {
                        row.forEach { m ->
                            Row(Modifier.weight(1f)) {
                                Text(m.clock, color = Mc.textDim, fontSize = Ms.caption)
                                Text(m.text, color = Mc.text, fontSize = Ms.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = Ms.s))
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(Ms.m)) {
                Stat("${card.minutes}분", "주행 시간", Modifier.weight(1f))
                Stat("${card.sceneChanges}번", "장면 전환", Modifier.weight(1f))
                Stat("${card.played.size}곡", "들은 곡", Modifier.weight(1f))
            }
        }
        Column(Modifier.weight(0.4f).fillMaxHeight().clip(RoundedCornerShape(Ms.radiusL)).background(Mc.surface).padding(Ms.l)) {
            Text("이번 길의 사운드트랙", color = Mc.textDim, fontSize = Ms.label, fontWeight = FontWeight.SemiBold)
            Column(Modifier.padding(top = Ms.s), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                card.played.take(7).forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(soft(p.color, 0.85f), Mc.surfaceRaised))))
                        Text(p.track.title, color = Mc.text, fontSize = Ms.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = Ms.s))
                        Text(p.scene.label, color = Mc.textDim, fontSize = Ms.caption)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.padding(top = Ms.m), horizontalArrangement = Arrangement.spacedBy(Ms.s)) {
                BigButton("닫기", Modifier.weight(1f), primary = true, onClick = onClose)
            }
        }
    }
}

@Composable
private fun Stat(value: String, key: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(Ms.radiusM)).background(Mc.surface).padding(Ms.m)) {
        Text(value, color = Mc.text, fontSize = Ms.title, fontWeight = FontWeight.Bold)
        Text(key, color = Mc.textDim, fontSize = Ms.caption, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 첫 실행 온보딩(정차 중). 장르 3개 선택 → 취향 사전 점수. */
@Composable
fun OnboardingScreen(onDone: (Set<String>) -> Unit) {
    val selected = remember { mutableStateListOf<String>() }
    Column(Modifier.fillMaxSize().background(Mc.bg).padding(horizontal = 96.dp, vertical = Ms.xl)) {
        Text("어떤 음악을 좋아하세요?", color = Mc.text, fontSize = Ms.title, fontWeight = FontWeight.Bold)
        Text("3개를 고르면 상황에 맞춰 이 취향 안에서 골라 드려요.", color = Mc.textDim, fontSize = Ms.body, modifier = Modifier.padding(top = Ms.xs))
        Column(Modifier.padding(top = Ms.l), verticalArrangement = Arrangement.spacedBy(Ms.s)) {
            Genres.all.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Ms.s)) {
                    row.forEach { (label, key) ->
                        val on = key in selected
                        Box(
                            Modifier.weight(1f).heightIn(min = Ms.touchPrimary).clip(RoundedCornerShape(Ms.radiusM))
                                .background(if (on) Color(0xFF2A1E14) else Mc.surface)
                                .border(BorderStroke(2.dp, if (on) Mc.accent else Mc.outline), RoundedCornerShape(Ms.radiusM))
                                .clickable {
                                    if (on) selected.remove(key) else if (selected.size < Genres.PICK_COUNT) selected.add(key)
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = Mc.text, fontSize = Ms.label, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        val left = Genres.PICK_COUNT - selected.size
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            BigButton(if (left == 0) "시작하기" else "${left}개 더 골라 주세요", Modifier.width(360.dp), primary = left == 0) {
                if (left == 0) onDone(selected.toSet())
            }
        }
    }
}
