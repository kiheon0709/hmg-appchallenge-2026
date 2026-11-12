package io.moodlab.sideseat.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoTest {
    @Test
    fun `seoul sunrise and sunset in mid november`() {
        // 2026-11-13은 317번째 날. 한국천문연구원 기준 서울 일출 약 07:08, 일몰 약 17:23.
        val (rise, set) = assertNotNull(SolarTimes.sunriseSunset(37.5665, 126.9780, 317, 9 * 60))
        assertTrue(abs(rise - (7 * 60 + 8)) <= 5, "rise=$rise")
        assertTrue(abs(set - (17 * 60 + 23)) <= 5, "set=$set")
    }

    @Test
    fun `summer days are longer than winter days`() {
        val (sr, ss) = SolarTimes.sunriseSunset(37.5665, 126.9780, 172, 540)!!
        val (wr, ws) = SolarTimes.sunriseSunset(37.5665, 126.9780, 355, 540)!!
        assertTrue(ss - sr > ws - wr + 4 * 60)
    }

    @Test
    fun `polar night has no sunrise`() {
        assertNull(SolarTimes.sunriseSunset(80.0, 0.0, 355, 0))
    }

    @Test
    fun `speed from two fixes one second apart`() {
        val e = SpeedEstimator(alpha = 1f)
        assertNull(e.add(37.0, 127.0, 0))
        // 위도 0.0001도 ≈ 11.1m → 1초에 약 40km/h
        val v = assertNotNull(e.add(37.0001, 127.0, 1_000))
        assertEquals(40f, v, 1f)
        // 너무 가까운 점은 무시, 끊긴 뒤에는 모름
        assertEquals(v, e.add(37.00011, 127.0, 1_100))
        assertNull(e.add(37.01, 127.0, 20_000))
    }

    @Test
    fun `speed is smoothed`() {
        val e = SpeedEstimator(alpha = 0.5f)
        e.add(37.0, 127.0, 0)
        val first = e.add(37.0001, 127.0, 1_000)!!
        val second = e.add(37.0001, 127.0, 2_000)!! // 멈춤
        assertEquals(first / 2, second, 0.5f)
    }
}
