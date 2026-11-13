package io.moodlab.sideseat.app.measure

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * 디버그 실측 전용: FusedLocation이 에뮬레이터 위치 주입(`adb emu geo fix`)을 받는지, 갱신 주기와 값을 로그로 남긴다(기능 아님).
 * LocationData에는 속도 필드가 없어 앞 좌표와의 거리·시각 차로 계산한 속도를 함께 남긴다.
 * `adb shell am broadcast -a io.moodlab.sideseat.DEBUG --es cmd loc --es arg "start|stop"`
 */
class LocationProbe(private val context: Context) {
    private var fused: FusedLocation? = null
    private var prev: LocationData? = null
    private var prevAt = 0L
    private var count = 0

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            val now = SystemClock.elapsedRealtime()
            val loc = location ?: run { Log.i(MEASURE_TAG, "t=$now loc null"); return }
            count++
            val p = prev
            val kmh = if (p != null && loc.timestampMillis > p.timestampMillis) {
                val out = FloatArray(1)
                android.location.Location.distanceBetween(p.latitude, p.longitude, loc.latitude, loc.longitude, out)
                out[0] / ((loc.timestampMillis - p.timestampMillis) / 1000f) * 3.6f
            } else null
            Log.i(
                MEASURE_TAG,
                "t=$now loc #$count gap=${if (prevAt > 0) now - prevAt else "-"}ms lat=${loc.latitude} lon=${loc.longitude} " +
                    "alt=${loc.altitude} heading=${loc.heading} conf=${loc.confidence} ts=${loc.timestampMillis} " +
                    "calcKmh=${kmh?.let { "%.1f".format(it) } ?: "-"}",
            )
            prev = loc
            prevAt = now
        }

        override fun onError(e: Exception) {
            Log.w(MEASURE_TAG, "loc error $e")
        }
    }

    fun command(arg: String) {
        runCatching {
            when (arg.trim()) {
                "start" -> if (fused == null) {
                    fused = FusedLocation(context).also {
                        Log.i(MEASURE_TAG, "loc probe supported=${it.isSupported()}")
                        it.initialize()
                        it.registerFusedLocationCallback(listener)
                    }
                }
                "stop" -> {
                    fused?.unregisterFusedLocationCallback(listener)
                    fused?.release()
                    fused = null
                    Log.i(MEASURE_TAG, "loc probe count=$count")
                }
            }
        }.onFailure { Log.w(MEASURE_TAG, "loc probe $arg failed: $it") }
    }
}
