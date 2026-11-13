package io.moodlab.sideseat.voice

/**
 * 듣는 중 "말이 끝난 것 같다"를 추측하는 타이머. 안드로이드 의존 없음(JVM 테스트 대상). 시계는 밖에서 넣는다.
 *
 * - 중간 결과가 올 때마다 [onPartial]: 글이 바뀌면 타이머를 새로 건다. 같은 글이 다시 오면 바로 추측(stable).
 * - 듣기 루프는 [dueIn]만큼 기다리다가 0 이하면 [fire]를 부른다. **fire는 결과와 상관없이 타이머를 푼다**.
 *   그래서 루프가 기다림 없이 다시 도는 일이 없다(11-13 실측 버그: 추측을 버린 경우 타이머가 계속 0이라
 *   events.receive() 없이 Main 스레드에서 약 17초 헛돌고 최종 결과를 놓침. 통합 세션 a76254b의 응급 수정을 여기서 구조로 막음).
 * - [enabled]가 false(연속 청취)면 타이머를 아예 걸지 않는다.
 */
class SpeechEndGuesser(
    private val enabled: Boolean,
    private val quietMs: Long = 450,
    private val quietCompleteMs: Long = 220,
    private val maxGuesses: Int = 3,
) {
    private var lastNorm = ""
    private var lastText = ""
    private var armedAt: Long? = null
    private var guessedNorm = ""
    var guesses = 0
        private set

    /** 중간 결과. 같은 글이 다시 왔으면 추측할 글을 돌려준다(없으면 null). */
    fun onPartial(text: String, now: Long): String? {
        val norm = ReplyClassifier.normalize(text)
        if (norm.isEmpty()) return null
        val repeated = norm == lastNorm
        lastNorm = norm
        lastText = text
        if (!enabled) return null
        if (repeated) {
            armedAt = null
            return take()
        }
        armedAt = if (norm == guessedNorm) null else now
        return null
    }

    /** 다음 추측까지 남은 시간. 걸린 타이머가 없으면 null. */
    fun dueIn(now: Long): Long? {
        val at = armedAt ?: return null
        val wait = if (ReplyClassifier.looksComplete(lastText)) quietCompleteMs else quietMs
        return wait - (now - at)
    }

    /** 타이머가 다 됨: 타이머를 풀고, 추측할 글이 있으면 돌려준다(이미 추측한 글·횟수 초과면 null). */
    fun fire(): String? {
        armedAt = null
        return take()
    }

    /** 인식이 끝났다(onEnded): 더 추측하지 않는다. */
    fun stop() { armedAt = null }

    private fun take(): String? {
        if (!enabled || lastNorm.isEmpty() || lastNorm == guessedNorm || guesses >= maxGuesses) return null
        guessedNorm = lastNorm
        guesses++
        return lastText
    }
}
