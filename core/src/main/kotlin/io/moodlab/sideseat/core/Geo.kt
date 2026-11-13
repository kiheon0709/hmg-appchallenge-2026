package io.moodlab.sideseat.core

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** 위치로 일출·일몰 시각을 계산한다(NOAA 근사식, 오차 수 분). K02 "일몰 이후" 판단에 쓴다. */
object SolarTimes {
    /**
     * @param dayOfYear 1~366
     * @param utcOffsetMinutes 현지 시간대(한국 +540)
     * @return (일출, 일몰) 현지 시각 분(0~1439). 백야·극야면 null.
     */
    fun sunriseSunset(latitude: Double, longitude: Double, dayOfYear: Int, utcOffsetMinutes: Int): Pair<Int, Int>? {
        val g = 2 * PI / 365 * (dayOfYear - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val lat = Math.toRadians(latitude)
        val cosHa = cos(Math.toRadians(90.833)) / (cos(lat) * cos(decl)) - tan(lat) * tan(decl)
        if (cosHa !in -1.0..1.0) return null
        val ha = Math.toDegrees(acos(cosHa))
        val rise = 720 - 4 * (longitude + ha) - eqTime + utcOffsetMinutes
        val set = 720 - 4 * (longitude - ha) - eqTime + utcOffsetMinutes
        return wrap(rise) to wrap(set)
    }

    private fun wrap(min: Double): Int = ((Math.round(min).toInt() % 1440) + 1440) % 1440
}
