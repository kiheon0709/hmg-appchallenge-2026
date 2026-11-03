package io.moodlab.moodrive.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContextEngineTest {
    private val day = VehicleSnapshot(timestampMs = 0, minuteOfDay = 13 * 60, gear = Gear.D)

    @Test
    fun `rain switches scene only after hold time`() {
        val e = ContextEngine(ContextEngine.Config(sceneHoldMs = 15_000))
        assertEquals(Scene.CLEAR_DAY, e.update(day).scene)

        val rain = day.copy(wiper = WiperLevel.INTERMITTENT)
        assertEquals(Scene.CLEAR_DAY, e.update(rain.copy(timestampMs = 1_000)).scene)
        assertEquals(Scene.CLEAR_DAY, e.update(rain.copy(timestampMs = 10_000)).scene)
        val r = e.update(rain.copy(timestampMs = 16_500))
        assertEquals(Scene.RAIN, r.scene)
        assertTrue(r.changed)
        assertNotNull(r.djLine)
    }

    @Test
    fun `wiper flicker does not change scene`() {
        val e = ContextEngine()
        e.update(day)
        e.update(day.copy(timestampMs = 1_000, wiper = WiperLevel.INTERMITTENT))
        e.update(day.copy(timestampMs = 3_000))
        val r = e.update(day.copy(timestampMs = 20_000, wiper = WiperLevel.INTERMITTENT))
        assertEquals(Scene.CLEAR_DAY, r.scene) // 다시 처음부터 기다려야 함
    }

    @Test
    fun `short daytime low beam is tunnel, long one is not`() {
        val e = ContextEngine(ContextEngine.Config(tunnelMaxMs = 60_000))
        e.update(day)
        assertEquals(Scene.TUNNEL, e.update(day.copy(timestampMs = 1_000, lowBeamOn = true)).scene)
        assertEquals(Scene.TUNNEL, e.update(day.copy(timestampMs = 50_000, lowBeamOn = true)).scene)
        // 1분 넘게 켜져 있으면 그냥 전조등을 켠 것 → 터널 해제
        assertEquals(Scene.CLEAR_DAY, e.update(day.copy(timestampMs = 70_000, lowBeamOn = true)).scene)
    }

    @Test
    fun `low beam at night is not a tunnel`() {
        val e = ContextEngine()
        val night = day.copy(minuteOfDay = 22 * 60, lowBeamOn = true)
        assertEquals(Scene.NIGHT, e.update(night).scene)
    }

    @Test
    fun `fatigue is an overlay - two hours any hour, 90 minutes late night`() {
        val e = ContextEngine()
        assertTrue(e.update(day.copy(minuteOfDay = 15 * 60, drivingMinutes = 121)).fatigue)
        assertTrue(ContextEngine().update(day.copy(minuteOfDay = 1 * 60, drivingMinutes = 95)).fatigue)
        assertFalse(ContextEngine().update(day.copy(minuteOfDay = 15 * 60, drivingMinutes = 95)).fatigue)
    }

    @Test
    fun `fatigue does not hide heavy rain, jam or finale`() {
        val tired = day.copy(drivingMinutes = 150)
        assertEquals(Scene.HEAVY_RAIN, ContextEngine().update(tired.copy(wiper = WiperLevel.HIGH)).scene)
        assertEquals(Scene.TRAFFIC_JAM, ContextEngine().update(tired.copy(congestion = true)).scene)
        assertEquals(Scene.ARRIVAL_FINALE, ContextEngine().update(tired.copy(etaMinutes = 3)).scene)
    }

    @Test
    fun `heavy rain right after a tunnel still gets the safety line`() {
        val e = ContextEngine()
        e.update(day)
        e.update(day.copy(timestampMs = 1_000, lowBeamOn = true))
        val r = e.update(day.copy(timestampMs = 2_000, lowBeamOn = true, wiper = WiperLevel.HIGH))
        assertEquals(Scene.HEAVY_RAIN, r.scene)
        assertNotNull(r.djLine)
    }

    @Test
    fun `rain stopping with auto-lights already on is not a tunnel`() {
        val e = ContextEngine(ContextEngine.Config(sceneHoldMs = 0))
        e.update(day.copy(wiper = WiperLevel.LOW, lowBeamOn = true))
        assertNotEquals(Scene.TUNNEL, e.update(day.copy(timestampMs = 1_000, lowBeamOn = true)).scene)
    }

    @Test
    fun `daytime headlights kept on and dusk auto-lights are not tunnels`() {
        val e = ContextEngine()
        assertNotEquals(Scene.TUNNEL, e.update(day.copy(lowBeamOn = true)).scene) // 처음부터 켜져 있음
        val dusk = day.copy(minuteOfDay = 17 * 60 + 30, sunsetMinute = 17 * 60 + 40)
        val e2 = ContextEngine()
        e2.update(dusk)
        assertNotEquals(Scene.TUNNEL, e2.update(dusk.copy(timestampMs = 1_000, lowBeamOn = true)).scene)
    }

    @Test
    fun `overcast auto-light with good GPS is not a tunnel, weak GPS confirms it`() {
        val e = ContextEngine()
        e.update(day.copy(gpsWeak = false))
        assertNotEquals(Scene.TUNNEL, e.update(day.copy(timestampMs = 1_000, lowBeamOn = true, gpsWeak = false)).scene)
        val e2 = ContextEngine()
        e2.update(day.copy(gpsWeak = false))
        assertEquals(Scene.TUNNEL, e2.update(day.copy(timestampMs = 1_000, lowBeamOn = true, gpsWeak = true)).scene)
    }

    @Test
    fun `lights switched on while parked do not start the trip as a tunnel`() {
        val e = ContextEngine()
        e.update(day.copy(gear = Gear.P))
        e.update(day.copy(timestampMs = 1_000, gear = Gear.P, lowBeamOn = true))
        assertNotEquals(Scene.TUNNEL, e.update(day.copy(timestampMs = 2_000, lowBeamOn = true)).scene)
    }

    @Test
    fun `low beam while wipers run is auto-light, not tunnel`() {
        val e = ContextEngine()
        e.update(day.copy(wiper = WiperLevel.LOW))
        assertNotEquals(Scene.TUNNEL, e.update(day.copy(timestampMs = 1_000, wiper = WiperLevel.LOW, lowBeamOn = true)).scene)
    }

    @Test
    fun `parked overrides everything`() {
        val e = ContextEngine()
        e.update(day.copy(wiper = WiperLevel.HIGH))
        e.update(day.copy(timestampMs = 1_000, gear = Gear.P, wiper = WiperLevel.HIGH)) // P단 시작
        // 60초 이상 P단이면 폭우여도 정차
        assertEquals(Scene.PARKED, e.update(day.copy(timestampMs = 62_000, gear = Gear.P, wiper = WiperLevel.HIGH)).scene)
    }

    @Test
    fun `first result has no dj line`() {
        assertNull(ContextEngine().update(day).djLine)
    }

    @Test
    fun `hot cabin brightens mood`() {
        val e = ContextEngine()
        val normal = e.moodFor(Scene.CLEAR_DAY, day)
        val hot = e.moodFor(Scene.CLEAR_DAY, day.copy(cabinTempC = 28f, targetTempC = 22f))
        assertTrue(hot.valence > normal.valence)
    }

    @Test
    fun `demo scenario visits the expected scenes in order`() {
        val sc = Scenario.rainyCommute()
        val e = ContextEngine()
        val scenes = (0..sc.durationSec).map { e.update(sc.snapshotAt(it)).scene }.distinctConsecutive()
        assertEquals(
            listOf(
                Scene.SUNSET, Scene.RAIN, Scene.HEAVY_RAIN, Scene.TUNNEL, Scene.SUNSET,
                Scene.TRAFFIC_JAM, Scene.SUNSET, Scene.ARRIVAL_FINALE, Scene.PARKED,
            ),
            scenes,
        )
    }

    @Test
    fun `scenario eta counts down with time scale`() {
        val sc = Scenario.rainyCommute()
        assertEquals(30, sc.snapshotAt(0).etaMinutes)
        assertEquals(5, sc.snapshotAt(150).etaMinutes)
        assertNotEquals(sc.snapshotAt(0).minuteOfDay, sc.snapshotAt(120).minuteOfDay)
        assertFalse(sc.snapshotAt(180).isDriving)
    }

    private fun <T> List<T>.distinctConsecutive(): List<T> =
        fold(mutableListOf()) { acc, x -> if (acc.lastOrNull() != x) acc.add(x); acc }
}
