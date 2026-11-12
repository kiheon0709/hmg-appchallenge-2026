package io.moodlab.sideseat.vehicle

import io.moodlab.sideseat.core.AmbientMood
import io.moodlab.sideseat.core.Seat
import io.moodlab.sideseat.core.TelltaleColor
import io.moodlab.sideseat.core.TurnSignal
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.core.WiperLevel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Calendar

/** 실측 로그 태그: `adb logcat -v epoch -s SideSeatMeasure` */
const val VEHICLE_TAG = "SideSeatMeasure"

/** 차량 신호 읽기와 정차 중 연출 제어(dev-contract 4절). */
interface VehicleGateway {
    val states: StateFlow<VehicleState>
    fun start()
    fun stop()

    /** 실내 조명. 실패해도 예외 없이 false. */
    suspend fun setAmbient(mood: AmbientMood): Boolean

    /** 휴게소 경유지(NaviHelper). 내비의 장소 검색 화면을 "휴게소"로 연다. */
    suspend fun addRestStopWaypoint(): Boolean
    val navRemainingMinutes: StateFlow<Int?>

    /** 목적지 도착. */
    val arrivals: SharedFlow<Unit>
}

/**
 * 화면 디버그 패널에서 값을 바꾸는 게이트웨이. Virtual Console 없이 UI·엔진을 확인할 때 쓴다.
 * 처음 상태: 정차, 모든 스위치 꺼짐, 내기순환 켜짐, 창문 닫힘.
 */
class DebugVehicleGateway : VehicleGateway {
    private val state = MutableStateFlow(
        VehicleState(
            timestampMs = System.currentTimeMillis(),
            parked = true,
            wiper = WiperLevel.OFF,
            lowBeamOn = false,
            highBeamOn = false,
            frontFogOn = false,
            rearFogOn = false,
            turnSignal = TurnSignal.OFF,
            hazardOn = false,
            brakePressed = false,
            steeringAngleDeg = 0f,
            defrostFrontOn = false,
            acOn = false,
            recirculationOn = true,
            driverWindowOpenPct = 0,
            windowOpenPct = mapOf(Seat.DRIVER to 0, Seat.PASSENGER to 0, Seat.REAR_LEFT to 0, Seat.REAR_RIGHT to 0),
            occupied = setOf(Seat.DRIVER),
            parkingBrakeOn = false,
            minuteOfDay = nowMinute(),
            vehicleModelKey = "DEBUG",
        ),
    )
    override val states: StateFlow<VehicleState> = state.asStateFlow()

    private val remaining = MutableStateFlow<Int?>(null)
    override val navRemainingMinutes: StateFlow<Int?> = remaining.asStateFlow()
    private val arrived = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val arrivals: SharedFlow<Unit> = arrived.asSharedFlow()

    /** 마지막으로 요청받은 조명. 디버그 패널 표시용. */
    private val ambient = MutableStateFlow<AmbientMood?>(null)
    val lastAmbient: StateFlow<AmbientMood?> = ambient.asStateFlow()

    override fun start() = Unit
    override fun stop() = Unit

    override suspend fun setAmbient(mood: AmbientMood): Boolean {
        ambient.value = mood
        return true
    }

    override suspend fun addRestStopWaypoint(): Boolean = true

    /** 시각을 고정해 야간 규칙을 시험할 때. null이면 현재 시각. */
    private var minuteOverride: Int? = null

    /** 아무 값이나 바꾼다. */
    fun update(f: (VehicleState) -> VehicleState) =
        state.update { f(it).copy(timestampMs = System.currentTimeMillis(), minuteOfDay = minuteOverride ?: nowMinute()) }

    fun setParked(parked: Boolean) = update { it.copy(parked = parked) }
    fun cycleWiper() = update {
        val order = listOf(WiperLevel.OFF, WiperLevel.INTERMITTENT, WiperLevel.LOW, WiperLevel.HIGH)
        it.copy(wiper = order[(order.indexOf(it.wiper ?: WiperLevel.OFF) + 1) % order.size])
    }
    fun toggleLowBeam() = update { it.copy(lowBeamOn = it.lowBeamOn != true) }
    fun toggleHighBeam() = update { it.copy(highBeamOn = it.highBeamOn != true) }
    fun toggleFrontFog() = update { it.copy(frontFogOn = it.frontFogOn != true) }
    fun toggleRearFog() = update { it.copy(rearFogOn = it.rearFogOn != true) }
    fun setTurnSignal(signal: TurnSignal) = update { it.copy(turnSignal = signal) }
    fun toggleHazard() = update { it.copy(hazardOn = it.hazardOn != true) }
    fun setBrake(pressed: Boolean) = update { it.copy(brakePressed = pressed) }
    fun setSteering(deg: Float) = update { it.copy(steeringAngleDeg = deg) }
    fun toggleDefrost() = update { it.copy(defrostFrontOn = it.defrostFrontOn != true) }
    fun toggleAc() = update { it.copy(acOn = it.acOn != true) }
    fun toggleRecirculation() = update { it.copy(recirculationOn = it.recirculationOn != true) }
    fun setWindow(seat: Seat, pct: Int) = update {
        it.copy(
            windowOpenPct = it.windowOpenPct + (seat to pct),
            driverWindowOpenPct = if (seat == Seat.DRIVER) pct else it.driverWindowOpenPct,
        )
    }
    fun setOccupied(seat: Seat, occupied: Boolean) = update { it.copy(occupied = if (occupied) it.occupied + seat else it.occupied - seat) }
    fun toggleParkingBrake() = update { it.copy(parkingBrakeOn = it.parkingBrakeOn != true) }
    fun setTelltale(type: String, color: TelltaleColor?) = update {
        it.copy(activeTelltales = if (color == null) it.activeTelltales - type else it.activeTelltales + (type to color))
    }
    fun setSpeed(kmh: Float?) = update { it.copy(speedKmhEstimate = kmh) }
    fun setMinuteOfDay(minute: Int?) {
        minuteOverride = minute
        update { it }
    }
    fun setNavRemaining(minutes: Int?) {
        remaining.value = minutes
    }
    fun fireArrival() {
        arrived.tryEmit(Unit)
    }

    private fun nowMinute(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
}
