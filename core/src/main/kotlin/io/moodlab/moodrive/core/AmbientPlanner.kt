package io.moodlab.moodrive.core

import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * F3 앰비언트 싱크. 무드 좌표를 실내 조명 색·밝기로 바꾼다.
 *
 * 정책 플래그 [allowWhileDriving]: 주행 중 set 호출 허용 여부가 공식 답변 전이라 기본값은 false.
 * false면 주행 중에는 조명을 바꾸지 않고(null), 정차하면 마지막 목표를 적용한다.
 */
class AmbientPlanner(
    var allowWhileDriving: Boolean = false,
    private val nightMaxBrightness: Int = 40,
    private val transitionMs: Long = 4_000,
) {
    private var lastSent: AmbientCommand? = null

    /** 이번 스냅샷에서 보낼 명령. 바꿀 필요가 없거나 정책상 못 바꾸면 null. */
    fun plan(mood: Mood, s: VehicleSnapshot): AmbientCommand? {
        if (s.isDriving && !allowWhileDriving) return null
        val cmd = commandFor(mood, night = !s.isDaytime())
        val prev = lastSent
        if (prev != null && prev.color == cmd.color && prev.brightness == cmd.brightness) return null
        lastSent = cmd
        return cmd
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
        // 각도 → 색상(hue). valence+ → 30°(주황), energy+ → 330°(핑크), valence− → 220°(딥블루), energy− → 170°(민트)
        val hue = when {
            angle >= 0 && angle <= 90 -> lerp(30.0, -30.0, angle / 90.0)
            angle > 90 -> lerp(-30.0, -140.0, (angle - 90) / 90.0) // 핑크 → 보라 → 딥블루(220°)
            angle < 0 && angle >= -90 -> lerp(30.0, 170.0, -angle / 90.0)
            else -> lerp(170.0, 220.0, (-angle - 90) / 90.0)
        }.let { ((it % 360) + 360) % 360 }
        val intensity = sqrt(mood.energy * mood.energy + mood.valence * mood.valence).coerceIn(0f, 1f)
        val saturation = 0.45 + 0.5 * intensity
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
