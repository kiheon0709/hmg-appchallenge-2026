package io.moodlab.sideseat.voice

/**
 * 디버그: 사람 목소리로 호출어를 직접 불러 보는 시험(VoiceModule.startWakeTest)의 판정·집계. 안드로이드 의존 없음(JVM 테스트 대상).
 * 받아 적은 글마다 기본 규칙과 일상어 변형 규칙(나비야 등, [Persona.WEAK_WAKE_WORDS])을 둘 다 판정해, 설정을 바꾸지 않고 두 값을 같이 본다.
 */
class WakeLiveTally(
    private val strict: WakeMatcher = WakeMatcher(),
    private val loose: WakeMatcher = WakeMatcher(allowWeak = true),
) {
    data class Entry(val n: Int, val heard: String, val result: WakeMatcher.Result, val wokeIfWeak: Boolean) {
        val woke: Boolean get() = result is WakeMatcher.Result.Wake
        val line: String
            get() = when (result) {
                is WakeMatcher.Result.Wake -> "#$n 깨어남 '$heard'" + if (result.rest.isBlank()) "" else " → 질문 '${result.rest}'"
                WakeMatcher.Result.Gleo -> "#$n Gleo로 봄 '$heard'"
                WakeMatcher.Result.None -> "#$n 안 깨어남 '$heard'" + if (wokeIfWeak) " (나비야 규칙이면 깨어남)" else ""
            }
    }

    var count = 0; private set
    var woke = 0; private set
    var wokeIfWeak = 0; private set
    private val recent = ArrayDeque<Entry>()

    fun record(heard: String): Entry {
        count++
        val r = strict.detect(heard)
        val weak = loose.detect(heard) is WakeMatcher.Result.Wake
        if (r is WakeMatcher.Result.Wake) woke++
        if (weak) wokeIfWeak++
        return Entry(count, heard, r, weak).also {
            recent.addLast(it)
            if (recent.size > MAX_LINES) recent.removeFirst()
        }
    }

    /** 최근 줄(오래된 것부터). */
    fun lines(): List<String> = recent.map { it.line }

    fun summary(): String = "깨어남 $woke/$count (나비야 규칙을 켜면 $wokeIfWeak/$count)"

    private companion object {
        const val MAX_LINES = 10
    }
}
