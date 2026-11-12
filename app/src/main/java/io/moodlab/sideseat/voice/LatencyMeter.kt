package io.moodlab.sideseat.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 말 끝 → 첫 음성 지연을 잰다. 안드로이드 의존 없음(시계와 로그를 밖에서 받는다).
 *
 * 구간: 말 끝(STT 최종) → 답 요청 → 첫 문장 준비 → TTS onStart.
 * 한 번 잰 뒤에는 다음 말 끝까지 다시 재지 않는다(이어지는 문장의 onStart는 세지 않음).
 */
class LatencyMeter(
    private val now: () -> Long,
    private val log: (String) -> Unit,
) {
    private val _last = MutableStateFlow<Long?>(null)
    val last: StateFlow<Long?> = _last.asStateFlow()

    private var speechEndAt: Long? = null
    private var epdEndAt: Long? = null
    private var answerAt: Long? = null
    private var firstSentenceAt: Long? = null
    private var path: String = "?"

    @Synchronized fun onEpdEnd() { if (speechEndAt == null) epdEndAt = now() }

    @Synchronized fun onSpeechEnd() {
        speechEndAt = now()
        answerAt = null
        firstSentenceAt = null
        path = "?"
    }

    @Synchronized fun onAnswerRequested(path: String) {
        if (speechEndAt != null && answerAt == null) {
            answerAt = now()
            this.path = path
        }
    }

    @Synchronized fun onFirstSentence() {
        if (speechEndAt != null && firstSentenceAt == null) firstSentenceAt = now()
    }

    /** TTS onStart마다 부른다. 말 끝 이후 첫 onStart만 기록한다. */
    @Synchronized fun onTtsStart() {
        val end = speechEndAt ?: return
        val t = now()
        val total = t - end
        _last.value = total
        val epd = epdEndAt?.let { end - it }
        val req = answerAt?.let { it - end }
        val first = firstSentenceAt?.let { f -> answerAt?.let { f - it } }
        val tts = firstSentenceAt?.let { t - it }
        log("latency total=${total}ms path=$path epd->final=${epd ?: "-"}ms final->answer=${req ?: "-"}ms answer->firstSentence=${first ?: "-"}ms firstSentence->ttsStart=${tts ?: "-"}ms")
        speechEndAt = null
        epdEndAt = null
    }

    /** 듣기가 아무 결과 없이 끝나면 재던 것을 버린다. */
    @Synchronized fun cancel() {
        speechEndAt = null
        epdEndAt = null
    }
}
