package io.moodlab.sideseat.core

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
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

/** 위치 두 점 사이 거리로 속도를 추정한다. 차속은 공개 SDK에 없어서 O2(급감속) 관찰에만 쓴다. */
class SpeedEstimator(
    /** 이보다 가까운 두 점은 잡음이 커서 쓰지 않는다. */
    private val minIntervalMs: Long = 500,
    /** 이보다 먼 두 점은 끊긴 것으로 본다. */
    private val maxIntervalMs: Long = 10_000,
    /** 지수 평활 가중치(새 값 쪽). */
    private val alpha: Float = 0.6f,
) {
    private var last: Triple<Double, Double, Long>? = null
    private var smoothed: Float? = null

    /** 새 위치를 넣고 추정 속도(km/h)를 돌려준다. 아직 모르면 null. */
    fun add(latitude: Double, longitude: Double, timestampMs: Long): Float? {
        val prev = last
        if (prev == null) {
            last = Triple(latitude, longitude, timestampMs)
            return smoothed
        }
        val dt = timestampMs - prev.third
        if (dt < minIntervalMs) return smoothed
        last = Triple(latitude, longitude, timestampMs)
        if (dt > maxIntervalMs) {
            smoothed = null
            return null
        }
        val kmh = (distanceMeters(prev.first, prev.second, latitude, longitude) / (dt / 1000.0) * 3.6).toFloat()
        smoothed = smoothed?.let { it + alpha * (kmh - it) } ?: kmh
        return smoothed
    }

    companion object {
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * r * asin(sqrt(a))
        }
    }
}
