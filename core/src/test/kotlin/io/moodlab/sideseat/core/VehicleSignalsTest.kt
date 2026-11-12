package io.moodlab.sideseat.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VehicleSignalsTest {
    @Test
    fun `parked follows ux restrictions first then gear then parking brake`() {
        assertEquals(true to ParkedSource.UX_RESTRICTIONS, ParkedInputs(uxRestricted = false, pleosGearPark = false).resolve())
        assertEquals(false to ParkedSource.UX_RESTRICTIONS, ParkedInputs(uxRestricted = true, pleosGearPark = true).resolve())
        assertEquals(true to ParkedSource.PLEOS_GEAR, ParkedInputs(pleosGearPark = true, aaosGearPark = false).resolve())
        assertEquals(false to ParkedSource.AAOS_GEAR, ParkedInputs(aaosGearPark = false, parkingBrakeOn = true).resolve())
        assertEquals(true to ParkedSource.PARKING_BRAKE, ParkedInputs(parkingBrakeOn = true).resolve())
        assertEquals(false to null, ParkedInputs().resolve())
        // 순서를 바꿀 수 있다(실측 결과에 따라)
        assertEquals(
            true to ParkedSource.AAOS_GEAR,
            ParkedInputs(uxRestricted = true, aaosGearPark = true).resolve(listOf(ParkedSource.AAOS_GEAR, ParkedSource.UX_RESTRICTIONS)),
        )
    }

    @Test
    fun `hazard is both turn telltales and blinking holds`() {
        val t = TurnSignalResolver(holdMs = 1_000)
        assertNull(t.resolve(null, 0))
        assertEquals(TurnSignal.OFF, t.resolve(TurnSignal.OFF, 0))
        t.onTelltales(leftOn = true, rightOn = true, nowMs = 100)
        assertEquals(TurnSignal.HAZARD, t.resolve(TurnSignal.OFF, 100))
        // 깜빡임으로 잠깐 꺼져도 비상등 유지
        t.onTelltales(leftOn = false, rightOn = false, nowMs = 500)
        assertEquals(TurnSignal.HAZARD, t.resolve(TurnSignal.OFF, 600))
        assertEquals(TurnSignal.OFF, t.resolve(TurnSignal.OFF, 1_200))
        // 한쪽만이면 API 값, API가 없으면 경고등
        t.onTelltales(leftOn = true, rightOn = false, nowMs = 2_000)
        assertEquals(TurnSignal.LEFT, t.resolve(null, 2_000))
        assertEquals(TurnSignal.LEFT, t.resolve(TurnSignal.LEFT, 2_000))
    }

    @Test
    fun `telltale color accepts enum index or argb`() {
        assertEquals(TelltaleColor.RED, telltaleColorOf(4))
        assertEquals(TelltaleColor.YELLOW, telltaleColorOf(3))
        assertEquals(TelltaleColor.RED, telltaleColorOf(0xFFFF2020.toInt()))
        assertEquals(TelltaleColor.YELLOW, telltaleColorOf(0xFFFFC000.toInt()))
        assertEquals(TelltaleColor.GREEN, telltaleColorOf(0xFF20C040.toInt()))
    }

    @Test
    fun `window percent and model key`() {
        assertEquals(0, windowOpenPercent(0, 0, 100))
        assertEquals(50, windowOpenPercent(5, 0, 10))
        assertEquals(100, windowOpenPercent(12, 0, 10))
        assertEquals(0, windowOpenPercent(0, 0, 0))
        assertEquals("KMHLM41C", vehicleModelKeyFromVin("kmhlm41cbpu123456"))
        assertNull(vehicleModelKeyFromVin("short"))
        assertNull(vehicleModelKeyFromVin(null))
    }
}
