package io.moodlab.moodrive.core

import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * F3 앰비언트 싱크. 무드 좌표를 실내 조명 색·밝기로 바꾼다.
 *
 * 정책 플래그 [allowWhileDriving]: 앰비언트 set이 P단 밖에서 허용되는지 공식 확정 전이라 기본값은 false.
 * false면 P단이 아닐 때 조명을 바꾸지 않고(null), P단이 되면 마지막 목표를 적용한다.
 */
class AmbientPlanner(
    var allowWhileDriving: Boolean = false,
    private val nightMaxBrightness: Int = 40,
    private val transitionMs: Long = 4_000,
) {
    private var lastSent: AmbientCommand? = null

    /** 이번 스냅샷에서 보낼 명령. 바꿀 필요가 없거나 정책상 못 바꾸면 null. */
    fun plan(mood: Mood, s: VehicleSnapshot): AmbientCommand? {
        if (!s.inPark && !allowWhileDriving) return null
        val cmd = commandFor(mood, night = !s.isDaytime())
        val prev = lastSent
        if (prev != null && prev.color == cmd.color && prev.brightness == cmd.brightness) return null
        lastSent = cmd
        return cmd
    }

    /** 차량이 set을 ERROR_DRIVING_RESTRICTED로 거부했을 때. 주행 중 전환을 끄고 다음 P단에서 다시 보낸다. */
    fun onRestricted() {
        allowWhileDriving = false
        lastSent = null
    }

    fun commandFor(mood: Mood, night: Boolean): AmbientCommand {
        val color = colorFor(mood)
        // 에너지가 높을수록 밝게: 30 ~ 90
        var brightness = (60 + mood.energy * 30).roundToInt().coerceIn(10, 100)
        if (night) brightness = brightness.coerceAtMost(nightMaxBrightness)
        return AmbientCommand(color, brightness, transitionMs)
    }

    /**
     * 원형 모델 각도로 색상을 정한다.
     * 밝고 신남=주황·핑크, 밝고 차분=따뜻한 앰버/민트, 어둡고 차분=딥블루, 어둡고 신남=보라.
     */
    fun colorFor(mood: Mood): RgbColor {
        val angle = Math.toDegrees(atan2(mood.energy.toDouble(), mood.valence.toDouble())) // -180..180, 0=밝음
        // 각도 → 색상(hue). valence+ → 35°(앰버), energy+ → 335°(핑크), valence− → 220°(딥블루), energy− → 170°(민트). 경계에서 연속.
        val hue = when {
            angle >= 0 && angle <= 90 -> lerp(35.0, -25.0, angle / 90.0)
            angle > 90 -> lerp(-25.0, -140.0, (angle - 90) / 90.0) // 핑크 → 보라 → 딥블루(220°)
            angle < 0 && angle >= -90 -> lerp(35.0, 170.0, -angle / 90.0)
            else -> lerp(170.0, 220.0, (-angle - 90) / 90.0)
        }.let { ((it % 360) + 360) % 360 }
        val intensity = sqrt(mood.energy * mood.energy + mood.valence * mood.valence).coerceIn(0f, 1f)
        // 실내 조명은 은은하게: 채도 상한 0.8 (원색 빨강·시안 방지)
        val saturation = 0.4 + 0.4 * intensity
        return hsvToRgb(hue, saturation, 1.0)
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t.coerceIn(0.0, 1.0)

    private fun hsvToRgb(h: Double, s: Double, v: Double): RgbColor {
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60) % 2 - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0.0)
            h < 120 -> Triple(x, c, 0.0)
            h < 180 -> Triple(0.0, c, x)
            h < 240 -> Triple(0.0, x, c)
            h < 300 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        fun ch(d: Double) = ((d + m) * 255).roundToInt().coerceIn(0, 255)
        return RgbColor(ch(r), ch(g), ch(b))
    }
}
