package io.moodlab.sideseat.core

import kotlin.math.abs
import kotlin.math.floor

data class SafeDialConfig(
    /** 오른쪽 +. 명세 8절: 오른쪽 90도 → 왼쪽 45도 → 오른쪽 120도. */
    val targetsDeg: List<Int> = listOf(90, -45, 120),
    /** 번호와 이만큼 가까우면 맞은 것. */
    val toleranceDeg: Float = 6f,
    /** 맞은 자리에서 이만큼 멈춰 있어야 딸깍(실수로 지나가는 것 방지). */
    val holdMs: Long = 700,
    /** closeness가 0이 되는 거리. */
    val farDeg: Float = 120f,
    /** 딸깍 간격(각도): 멀면 성기게, 가까우면 촘촘하게. */
    val clickStepFarDeg: Float = 15f,
    val clickStepMidDeg: Float = 6f,
    val clickStepNearDeg: Float = 2f,
    val midDeg: Float = 30f,
    val nearDeg: Float = 12f,
    /** 딸깍 소리 최소 간격. */
    val minClickGapMs: Long = 50,
)

/** M1 금고 다이얼. 핸들을 돌려 번호 세 개를 차례로 맞춘다. */
class DefaultSafeDialGame(private val config: SafeDialConfig = SafeDialConfig()) : SafeDialGame {
    private var solved = 0
    private var startMs = 0L
    private var holdSince: Long? = null
    private var lastBucket: Int? = null
    private var lastClickMs = Long.MIN_VALUE / 2
    private var current = 0f
    private var done = false
    private var finishedMs: Long? = null
    private var warnedNoSignal = false
    private val events = mutableListOf<CoachEvent>()

    override fun start(nowMs: Long) {
        solved = 0
        startMs = nowMs
        holdSince = null
        lastBucket = null
        done = false
        finishedMs = null
        warnedNoSignal = false
        events += CoachEvent.Ambient(AmbientMood.CALM)
        events += CoachEvent.Say("핸들로 금고를 열어 볼까요? 첫 번호는 ${describe(config.targetsDeg[0])}예요.")
    }

    override fun tick(steeringAngleDeg: Float?, nowMs: Long): Pair<SafeDialView, List<CoachEvent>> {
        if (steeringAngleDeg == null) {
            if (!warnedNoSignal) {
                warnedNoSignal = true
                events += CoachEvent.Say("핸들 각도를 읽을 수 없어요. 이 차에서는 금고 게임을 할 수 없어요.")
            }
        } else if (!done) {
            current = steeringAngleDeg
            update(steeringAngleDeg, nowMs)
        }
        val out = events.toList()
        events.clear()
        return view(nowMs) to out
    }

    private fun update(angle: Float, nowMs: Long) {
        val target = config.targetsDeg[solved].toFloat()
        val diff = abs(angle - target)
        clicks(angle, diff, nowMs)
        if (diff <= config.toleranceDeg) {
            val since = holdSince ?: nowMs.also { holdSince = it }
            if (nowMs - since >= config.holdMs) unlock(nowMs)
        } else {
            holdSince = null
        }
    }

    private fun clicks(angle: Float, diff: Float, nowMs: Long) {
        val step = when {
            diff <= config.nearDeg -> config.clickStepNearDeg
            diff <= config.midDeg -> config.clickStepMidDeg
            else -> config.clickStepFarDeg
        }
        val bucket = floor(angle / step).toInt() * 1000 + step.toInt()
        val prev = lastBucket
        lastBucket = bucket
        if (prev != null && prev != bucket && nowMs - lastClickMs >= config.minClickGapMs) {
            lastClickMs = nowMs
            events += CoachEvent.Sfx("click")
        }
    }

    private fun unlock(nowMs: Long) {
        solved++
        holdSince = null
        events += CoachEvent.Sfx("unlock")
        if (solved >= config.targetsDeg.size) {
            done = true
            finishedMs = nowMs
            val sec = (nowMs - startMs) / 1000
            events += CoachEvent.Sfx("success")
            events += CoachEvent.Ambient(AmbientMood.SUCCESS)
            events += CoachEvent.Say("열렸어요! ${sec}초 걸렸어요.")
        } else {
            events += CoachEvent.Say("철컥! 다음은 ${describe(config.targetsDeg[solved])}예요.")
        }
    }

    private fun view(nowMs: Long): SafeDialView {
        val target = config.targetsDeg.getOrNull(solved)?.toFloat()
        val closeness = if (done || target == null) 1f else (1f - abs(current - target) / config.farDeg).coerceIn(0f, 1f)
        return SafeDialView(
            targetsDeg = config.targetsDeg,
            solved = solved,
            currentDeg = current,
            closeness = closeness,
            done = done,
            elapsedMs = (finishedMs ?: nowMs) - startMs,
            holdProgress = holdSince?.let { ((nowMs - it).toFloat() / config.holdMs).coerceIn(0f, 1f) } ?: 0f,
        )
    }

    private fun describe(deg: Int) = if (deg >= 0) "오른쪽 ${deg}도" else "왼쪽 ${-deg}도"
}
