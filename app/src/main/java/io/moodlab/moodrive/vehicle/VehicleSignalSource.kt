package io.moodlab.moodrive.vehicle

import io.moodlab.moodrive.core.Gear
import io.moodlab.moodrive.core.Scenario
import io.moodlab.moodrive.core.VehicleSnapshot
import io.moodlab.moodrive.core.WiperLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import java.util.Calendar

/** 차량 신호 입력. 실차/에뮬레이터는 [PleosVehicleSignalSource], 시연은 [scenarioFlow]·[ManualSignalSource]. */
interface VehicleSignalSource {
    val snapshots: Flow<VehicleSnapshot>
}

/** 시나리오를 1초 단위로 재생. 캡션은 UI 표시용. */
fun scenarioFlow(scenario: Scenario, onCaption: (String, Int) -> Unit): Flow<VehicleSnapshot> = flow {
    for (t in 0..scenario.durationSec) {
        onCaption(scenario.captionAt(t), t)
        emit(scenario.snapshotAt(t))
        delay(1_000)
    }
}

/** 디버그 패널 버튼으로 신호를 직접 바꾸는 소스. Virtual Console 없이도 반응을 확인할 수 있다. */
class ManualSignalSource : VehicleSignalSource {
    private val state = MutableStateFlow(
        VehicleSnapshot(timestampMs = System.currentTimeMillis(), minuteOfDay = nowMinute(), gear = Gear.P, etaMinutes = 25),
    )
    override val snapshots: StateFlow<VehicleSnapshot> = state.asStateFlow()

    fun cycleWiper() = edit {
        val next = WiperLevel.entries[(it.wiper.ordinal + 1) % WiperLevel.entries.size]
        it.copy(wiper = next)
    }
    fun toggleLowBeam() = edit { it.copy(lowBeamOn = !it.lowBeamOn) }
    fun toggleCongestion() = edit { it.copy(congestion = !it.congestion) }
    fun toggleGear() = edit { it.copy(gear = if (it.isDriving) Gear.P else Gear.D) }
    fun toggleCruise() = edit { it.copy(cruiseOn = !it.cruiseOn) }

    private fun edit(f: (VehicleSnapshot) -> VehicleSnapshot) =
        state.update { f(it).copy(timestampMs = System.currentTimeMillis(), minuteOfDay = nowMinute()) }

    private fun nowMinute(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
}
