package io.moodlab.moodrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.moodlab.moodrive.core.Mood

class MainActivity : ComponentActivity() {
    private val vm: MooDriveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0F13)) {
                    val ui by vm.ui.collectAsStateWithLifecycle()
                    MooDriveScreen(ui, vm)
                }
            }
        }
    }
}

@Composable
private fun MooDriveScreen(ui: UiState, vm: MooDriveViewModel) {
    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        // 왼쪽: 실내 조명 시각화 (시연 영상에서 '조명이 바뀌는 장면'을 보여주는 패널)
        CabinPreview(ui, Modifier.weight(1f).fillMaxHeight())
        // 오른쪽: 주행 중 최소 UI + 디버그 패널
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NowPlaying(ui, vm)
            Spacer(Modifier.weight(1f))
            DebugPanel(ui, vm)
        }
    }
    ui.tripCardScenes?.let { TripCard(it.map { s -> s.label }, onClose = vm::dismissTripCard) }
}

@Composable
private fun CabinPreview(ui: UiState, modifier: Modifier) {
    val target = ui.ambient?.color?.let { Color(it.toArgb()) } ?: Color(0xFF2A2D36)
    val color by animateColorAsState(target, tween(durationMillis = (ui.ambient?.transitionMs ?: 4000L).toInt()), label = "ambient")
    val alpha = (ui.ambient?.brightness ?: 30) / 100f
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color(0xFF111217))))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp))
            .padding(28.dp),
    ) {
        Column {
            Text("실내 조명", color = Color(0x99FFFFFF), fontSize = 16.sp)
            Text(ui.scene?.label ?: "—", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            if (ui.ambientPending) {
                Text("주행 중 조명 변경 보류 (정차 시 적용)", color = Color(0xFFFFC46B), fontSize = 16.sp)
            }
            ui.ambient?.let { Text("${it.color.hex()} · 밝기 ${it.brightness}%", color = Color(0xCCFFFFFF), fontSize = 16.sp) }
        }
        ui.mood?.let { MoodGrid(it, Modifier.align(Alignment.BottomEnd).size(160.dp)) }
        ui.lastDjLine?.let {
            Text("🎙 $it", color = Color.White, fontSize = 20.sp, modifier = Modifier.align(Alignment.BottomStart).padding(end = 180.dp))
        }
    }
}

/** 무드 좌표(가로 밝음, 세로 에너지) 위의 현재 점. */
@Composable
private fun MoodGrid(mood: Mood, modifier: Modifier) {
    Canvas(modifier.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))) {
        val c = Offset(size.width / 2, size.height / 2)
        drawLine(Color(0x33FFFFFF), Offset(0f, c.y), Offset(size.width, c.y))
        drawLine(Color(0x33FFFFFF), Offset(c.x, 0f), Offset(c.x, size.height))
        val p = Offset(c.x + mood.valence * c.x * 0.9f, c.y - mood.energy * c.y * 0.9f)
        drawCircle(Color.White, radius = 10f, center = p)
    }
}

@Composable
private fun NowPlaying(ui: UiState, vm: MooDriveViewModel) {
    val t = ui.track
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t?.title ?: "대기 중", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text(t?.let { "${it.artist} · ${it.genre}" } ?: "", color = Color(0xAAFFFFFF), fontSize = 20.sp)
        if (t != null) {
            LinearProgressIndicator(
                progress = { ui.positionSec / t.durationSec.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
        }
        // 주행 중 큰 버튼 3개만
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("👍 좋아", vm::like)
            BigButton("👎 별로", vm::dislike)
            BigButton("⏭ 다음", vm::skip)
        }
    }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.height(72.dp)) { Text(label, fontSize = 22.sp) }
}

@Composable
private fun DebugPanel(ui: UiState, vm: MooDriveViewModel) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF1A1C22)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("시나리오 / 디버그", color = Color(0x99FFFFFF))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = vm::playScenario) { Text("▶ 비 오는 퇴근길") }
            OutlinedButton(onClick = vm::useManual) { Text("수동") }
            ui.scenarioCaption?.let { Text("${ui.scenarioT}s · $it", color = Color.White) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val s = ui.snapshot
            OutlinedButton(onClick = vm.manual::cycleWiper) { Text("와이퍼 ${s?.wiper ?: ""}") }
            OutlinedButton(onClick = vm.manual::toggleLowBeam) { Text("하향등 ${if (s?.lowBeamOn == true) "ON" else "OFF"}") }
            OutlinedButton(onClick = vm.manual::toggleCongestion) { Text("정체") }
            OutlinedButton(onClick = vm.manual::toggleCruise) { Text("크루즈") }
            OutlinedButton(onClick = vm.manual::toggleGear) { Text(s?.gear?.name ?: "기어") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = ui.allowAmbientWhileDriving, onCheckedChange = vm::setAllowAmbientWhileDriving)
            Text("주행 중 조명 변경 허용 (공식 답변 전까지 OFF)", color = Color(0xCCFFFFFF))
        }
    }
}

@Composable
private fun TripCard(scenes: List<String>, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF1E2028)).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("오늘의 드라이브", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(scenes.joinToString("  →  "), color = Color(0xDDFFFFFF), fontSize = 20.sp)
            Button(onClick = onClose) { Text("닫기") }
        }
    }
}
