package io.moodlab.sideseat.core

import kotlin.test.fail

/** 모든 신호를 아는 "평상시" 상태: 스위치는 다 꺼짐, 내기순환 켜짐, 창문 닫힘. */
val baseState = VehicleState(
    timestampMs = 0,
    parked = true,
    wiper = WiperLevel.OFF,
    lowBeamOn = false,
    highBeamOn = false,
    frontFogOn = false,
    rearFogOn = false,
    turnSignal = TurnSignal.OFF,
    brakePressed = false,
    steeringAngleDeg = 0f,
    defrostFrontOn = false,
    acOn = false,
    recirculationOn = true,
    driverWindowOpenPct = 0,
    parkingBrakeOn = false,
    vehicleModelKey = "avante",
    minuteOfDay = 21 * 60,
)

/** 엔진에 시간과 신호를 흘려 넣는 시험 도구. 50ms 간격으로 tick한다. */
class TrainingDriver(val engine: TrainingEngine, var state: VehicleState = baseState) {
    var now = 0L
    val events = mutableListOf<CoachEvent>()
    lateinit var view: TrainingView

    fun tick(): TrainingView {
        val (v, e) = engine.tick(state.copy(timestampMs = now), now)
        view = v
        events += e
        return v
    }

    fun advance(ms: Long, step: Long = 50) {
        val end = now + ms
        while (now < end) {
            now = minOf(now + step, end)
            tick()
        }
    }

    fun until(maxMs: Long = 60_000, step: Long = 50, cond: (TrainingView) -> Boolean): TrainingView {
        val end = now + maxMs
        tick()
        while (!cond(view)) {
            if (now >= end) fail("조건을 ${maxMs}ms 안에 만족하지 못함. 마지막 view: phase=${view.phase} situation=${view.situation}")
            now += step
            tick()
        }
        return view
    }

    fun set(f: (VehicleState) -> VehicleState) {
        state = f(state)
        tick()
    }

    fun untilActive(id: SituationId) = until { it.phase == TrainingPhase.ACTIVE && it.situation == id }

    fun says(): List<String> = events.filterIsInstance<CoachEvent.Say>().map { it.text }

    fun clearEvents() = events.clear()
}
