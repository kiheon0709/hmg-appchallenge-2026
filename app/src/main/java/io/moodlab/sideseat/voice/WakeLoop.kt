package io.moodlab.sideseat.voice

/** 주행 중 연속 청취 설정. 간격·백오프는 실측으로 고칠 수 있게 값으로 둔다. */
data class WakeConfig(
    /** STT 한 번(request)을 최대 이만큼 열어 두고, 말이 없으면 닫고 다시 연다. */
    val cycleMaxMs: Long = 12_000,
    /** 한 번이 끝나고 다음 request까지 쉬는 시간. */
    val restartGapMs: Long = 300,
    /** STT 오류 뒤 첫 대기. 오류가 이어지면 두 배씩, [maxBackoffMs]까지. */
    val errorBackoffMs: Long = 1_000,
    val maxBackoffMs: Long = 30_000,
    /** Gleo 호출어가 들리면 이만큼 물러난다. */
    val gleoBackoffMs: Long = 10_000,
    /** 오디오 포커스를 잃거나 다른 앱이 마이크를 쓰면 이만큼 물러났다가 다시 확인한다. */
    val interferenceBackoffMs: Long = 5_000,
    /** 선배가 말을 마친 직후 호출어 없이 듣는 시간(되묻기). */
    val followUpMs: Long = 6_000,
)

/** 연속 청취 상태(화면·디버그 표시용). */
enum class WakeState { OFF, LISTENING, FOLLOW_UP, SPEAKING, HANDLING, BACKED_OFF }

/**
 * 연속 청취의 판단만 하는 상태 기계. 안드로이드 의존 없음(JVM 테스트 대상). 시계는 밖에서 넣는다.
 *
 * 흐름: 켜면 LISTENING → STT 한 번의 결과마다 [onUtterance]로 판단
 * (맨 앞 호출어 → 질문 / 되묻기 창 안의 말 → 질문 / Gleo 호출 → 물러남 / 그 밖 → 무시).
 * 선배가 말하는 동안은 듣지 않고(에코 제거가 없어 자기 목소리를 호출어로 오인할 수 있음), 말이 끝나면 되묻기 창을 연다.
 */
class WakeLoop(private val config: WakeConfig = WakeConfig()) {

    sealed interface Decision {
        /** 질문으로 받는다. [viaWake]면 호출어로 시작한 것(확인음). [question]이 비면 이어서 질문을 따로 듣는다. */
        data class Question(val question: String, val viaWake: Boolean) : Decision
        object Gleo : Decision
        object Ignore : Decision
    }

    var state: WakeState = WakeState.OFF
        private set

    private var followUpUntil = 0L
    private var backoffUntil = 0L
    private var consecutiveErrors = 0

    // 디버그 통계
    var cycles = 0; private set
    var restarts = 0; private set
    var wakes = 0; private set
    var followUps = 0; private set
    var gleoBackoffs = 0; private set
    var interferenceBackoffs = 0; private set
    var errors = 0; private set

    val enabled: Boolean get() = state != WakeState.OFF

    fun start() {
        if (state == WakeState.OFF) state = WakeState.LISTENING
    }

    fun stop() {
        state = WakeState.OFF
        followUpUntil = 0
        backoffUntil = 0
        consecutiveErrors = 0
    }

    /** 다음 STT를 열기 전 기다릴 시간(0이면 바로). 꺼져 있으면 -1. */
    fun waitBeforeListen(now: Long): Long {
        if (state == WakeState.OFF) return -1
        if (state == WakeState.SPEAKING || state == WakeState.HANDLING) return -1
        if (now < backoffUntil) return backoffUntil - now
        if (state == WakeState.BACKED_OFF) state = WakeState.LISTENING
        if (state == WakeState.FOLLOW_UP && now >= followUpUntil) state = WakeState.LISTENING
        return 0
    }

    /** 이번 STT를 되묻기 창으로 여는지(호출어 없이 받음). */
    fun inFollowUp(now: Long): Boolean = state == WakeState.FOLLOW_UP && now < followUpUntil

    /** 남은 되묻기 시간(STT 한 번의 최대 길이로 쓴다). */
    fun followUpLeft(now: Long): Long = if (inFollowUp(now)) followUpUntil - now else 0

    fun onCycleStart() { cycles++ }

    /** STT 한 번의 결과 판단. [text]가 null이면 말이 없었던 것. */
    fun onUtterance(text: String?, now: Long): Decision {
        if (state == WakeState.OFF) return Decision.Ignore
        consecutiveErrors = 0
        val wasFollowUp = inFollowUp(now)
        if (text.isNullOrBlank()) {
            restarts++
            if (wasFollowUp) return Decision.Ignore // 창이 남아 있으면 계속 되묻기로 연다
            if (state == WakeState.FOLLOW_UP) state = WakeState.LISTENING
            return Decision.Ignore
        }
        when (val r = WakeWordDetector.detect(text)) {
            WakeWordDetector.Result.Gleo -> {
                gleoBackoffs++
                backOff(now, config.gleoBackoffMs)
                return Decision.Gleo
            }
            is WakeWordDetector.Result.Wake -> {
                wakes++
                state = WakeState.HANDLING
                return Decision.Question(r.rest, viaWake = true)
            }
            WakeWordDetector.Result.None -> {
                if (wasFollowUp && ReplyClassifier.normalize(text).length >= MIN_FOLLOW_UP_CHARS) {
                    followUps++
                    state = WakeState.HANDLING
                    return Decision.Question(text.trim(), viaWake = false)
                }
                return Decision.Ignore
            }
        }
    }

    /** STT 오류(마이크를 다른 쪽이 쓰는 중일 수 있음). 이어지면 대기를 두 배로. */
    fun onError(now: Long) {
        if (state == WakeState.OFF) return
        errors++
        consecutiveErrors++
        val wait = (config.errorBackoffMs shl (consecutiveErrors - 1).coerceAtMost(10)).coerceAtMost(config.maxBackoffMs)
        backOff(now, wait)
    }

    /** 오디오 포커스를 잃었거나 다른 앱(Gleo 등)이 마이크를 쓰기 시작함. */
    fun onInterference(now: Long) {
        if (state == WakeState.OFF) return
        interferenceBackoffs++
        backOff(now, config.interferenceBackoffMs)
    }

    /** 선배가 말하기 시작/끝. 끝나면 되묻기 창을 연다. */
    fun onSpeaking(speaking: Boolean, now: Long) {
        if (state == WakeState.OFF) return
        if (speaking) {
            state = WakeState.SPEAKING
        } else if (state == WakeState.SPEAKING || state == WakeState.HANDLING) {
            state = WakeState.FOLLOW_UP
            followUpUntil = now + config.followUpMs
        }
    }

    /** 질문 처리가 말없이 끝남(답이 없거나 실패). */
    fun onHandled(now: Long) {
        if (state == WakeState.HANDLING) {
            state = WakeState.FOLLOW_UP
            followUpUntil = now + config.followUpMs
        }
    }

    /** 다음 STT 사이 간격. */
    val restartGapMs: Long get() = config.restartGapMs
    val cycleMaxMs: Long get() = config.cycleMaxMs

    fun status(now: Long): String =
        "state=$state cycles=$cycles restarts=$restarts wakes=$wakes followUps=$followUps gleo=$gleoBackoffs " +
            "interference=$interferenceBackoffs errors=$errors backoffLeft=${(backoffUntil - now).coerceAtLeast(0)}ms"

    private fun backOff(now: Long, ms: Long) {
        state = WakeState.BACKED_OFF
        backoffUntil = maxOf(backoffUntil, now + ms)
        followUpUntil = 0
    }

    private companion object {
        const val MIN_FOLLOW_UP_CHARS = 2
    }
}
