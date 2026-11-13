package io.moodlab.sideseat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.moodlab.sideseat.core.TelltaleColor
import io.moodlab.sideseat.core.TurnSignal
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.core.WiperLevel

/**
 * 디버그 패널(디버그 빌드 전용). Virtual Console 없이 화면에서 차량 신호를 바꿔 본다.
 * 상태를 받아 바뀐 상태를 [onChange]로 돌려줄 뿐이다. 통합 세션이 DebugVehicleGateway에 연결한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugPanel(state: VehicleState, onChange: (VehicleState) -> Unit, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    Column(
        modifier.clip(RoundedCornerShape(2.dp)).background(Sc.scrim).border(1.dp, Sc.text, RoundedCornerShape(2.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "디버그 신호 · 화면 %.0f fps".format(SideSeatUiDebug.lastFps), color = Sc.accent, fontSize = 16.sp, fontWeight = FontWeight.Medium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val wiper = state.wiper ?: WiperLevel.OFF
            Key("와이퍼 ${wiper.name}", wiper != WiperLevel.OFF) {
                onChange(state.copy(wiper = WiperLevel.entries[(wiper.ordinal + 1) % WiperLevel.entries.size]))
            }
            Key("전조등", state.lowBeamOn == true) { onChange(state.copy(lowBeamOn = state.lowBeamOn != true)) }
            Key("상향등", state.highBeamOn == true) { onChange(state.copy(highBeamOn = state.highBeamOn != true)) }
            Key("안개등", state.frontFogOn == true) { onChange(state.copy(frontFogOn = state.frontFogOn != true)) }
            val turn = state.turnSignal ?: TurnSignal.OFF
            Key("깜빡이 ${turn.name}", turn != TurnSignal.OFF) {
                onChange(state.copy(turnSignal = TurnSignal.entries[(turn.ordinal + 1) % TurnSignal.entries.size]))
            }
            Key("비상등", state.hazardOn == true) { onChange(state.copy(hazardOn = state.hazardOn != true)) }
            Key("브레이크", state.brakePressed == true) { onChange(state.copy(brakePressed = state.brakePressed != true)) }
            Key("성에 제거", state.defrostFrontOn == true) { onChange(state.copy(defrostFrontOn = state.defrostFrontOn != true)) }
            Key("에어컨", state.acOn == true) { onChange(state.copy(acOn = state.acOn != true)) }
            Key("내기순환", state.recirculationOn == true) { onChange(state.copy(recirculationOn = state.recirculationOn != true)) }
            val open = (state.driverWindowOpenPct ?: 0) > 0
            Key("운전석 창문 ${if (open) "열림" else "닫힘"}", open) { onChange(state.copy(driverWindowOpenPct = if (open) 0 else 30)) }
            Key("주차 브레이크", state.parkingBrakeOn == true) { onChange(state.copy(parkingBrakeOn = state.parkingBrakeOn != true)) }
            val tpms = state.activeTelltales.containsKey("TPMS")
            Key("공기압 경고등", tpms) {
                onChange(state.copy(activeTelltales = if (tpms) state.activeTelltales - "TPMS" else state.activeTelltales + ("TPMS" to TelltaleColor.YELLOW)))
            }
            Key(if (state.parked) "정차(P)" else "주행", state.parked) { onChange(state.copy(parked = !state.parked)) }
            Key("프레임 로그", SideSeatUiDebug.logFrames) { SideSeatUiDebug.logFrames = !SideSeatUiDebug.logFrames; onChange(state) }
            val dark = SideSeatUiDebug.forceDark
            Key(when (dark) { null -> "테마 시스템"; true -> "테마 야간"; false -> "테마 주간" }, dark != null) {
                SideSeatUiDebug.forceDark = when (dark) { null -> true; true -> false; false -> null }
            }
            if (onClose != null) Key("닫기", false, onClose)
        }
        val steer = state.steeringAngleDeg ?: 0f
        Text("핸들 %.0f°".format(steer), color = Sc.text, fontSize = 16.sp)
        Slider(steer, { onChange(state.copy(steeringAngleDeg = it)) }, Modifier.fillMaxWidth(), valueRange = -180f..180f)
    }
}

@Composable
private fun Key(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (on) Sc.onPrimary else Sc.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(2.dp)).background(if (on) Sc.primary else Sc.surface)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    )
}
