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
    private var fillerAt: Long? = null
    private var firstVoiceAt: Long? = null
    private var llmRequestAt: Long? = null
    private var llmFirstTokenAt: Long? = null
    private var path: String = "?"

    @Synchronized fun onEpdEnd() { if (speechEndAt == null) epdEndAt = now() }

    @Synchronized fun onSpeechEnd() {
        speechEndAt = now()
        answerAt = null
        firstSentenceAt = null
        fillerAt = null
        firstVoiceAt = null
        path = "?"
        // llmRequestAt·llmFirstTokenAt은 지우지 않는다: 추측 요청은 STT 최종보다 먼저 나간다.
    }

    /** LLM 요청을 보낸 시각(추측 요청이면 STT 최종보다 이르다). [at]이 없으면 지금. */
    @Synchronized fun onLlmRequest(at: Long = now()) { llmRequestAt = at; llmFirstTokenAt = null }

    @Synchronized fun onLlmFirstToken(at: Long = now()) { if (llmFirstTokenAt == null) llmFirstTokenAt = at }

    /** 채움말("음, 잠깐만요.")을 내보냄. 채움말은 첫 문장으로 세지 않는다. */
    @Synchronized fun onFiller() {
        if (speechEndAt != null && fillerAt == null) fillerAt = now()
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

    /**
     * TTS onStart마다 부른다. 말 끝 이후 첫 onStart = 첫 음성(total, [last]).
     * 채움말을 먼저 말했다면 실제 답의 첫 문장이 소리 나기 시작한 onStart를 한 번 더 기록한다(answer).
     */
    @Synchronized fun onTtsStart() {
        val end = speechEndAt ?: return
        val t = now()
        val epd = epdEndAt?.let { end - it }
        val req = answerAt?.let { it - end }
        if (firstVoiceAt == null) {
            firstVoiceAt = t
            val total = t - end
            _last.value = total
            val filler = fillerAt != null && (firstSentenceAt == null || fillerAt!! <= firstSentenceAt!!)
            val ready = (if (filler) fillerAt else firstSentenceAt)
            val first = ready?.let { f -> answerAt?.let { f - it } }
            val tts = ready?.let { t - it }
            log("latency total=${total}ms first=${if (filler) "filler" else "answer"} path=$path guessEnd->final=${epd ?: "-"}ms final->answer=${req ?: "-"}ms answer->firstSentence=${first ?: "-"}ms firstSentence->ttsStart=${tts ?: "-"}ms ${llmStages(end, t)}")
            if (!filler) clear()
            return
        }
        // 채움말 뒤: 실제 답의 첫 문장이 준비된 뒤의 첫 onStart
        val real = firstSentenceAt ?: return
        if (t < real) return
        log("latency answer=${t - end}ms path=$path afterFiller=yes final->llmFirstSentence=${answerAt?.let { real - it } ?: "-"}ms firstSentence->ttsStart=${t - real}ms ${llmStages(end, t)}")
        clear()
    }

    /** 구간 분해: final→요청(추측이면 음수), 요청→첫 토큰, 첫 토큰→첫 조각, 첫 조각→TTS 시작. */
    private fun llmStages(end: Long, ttsStart: Long): String {
        val req = llmRequestAt ?: return "llm=-"
        if (!path.startsWith("llm")) return "llm=-"
        val tok = llmFirstTokenAt
        val first = firstSentenceAt
        return "stages final->request=${req - end}ms request->firstToken=${tok?.let { it - req } ?: "-"}ms " +
            "firstToken->firstChunk=${if (tok != null && first != null) first - tok else "-"}ms firstChunk->ttsStart=${first?.let { ttsStart - it } ?: "-"}ms"
    }

    private fun clear() {
        speechEndAt = null
        epdEndAt = null
        fillerAt = null
        firstVoiceAt = null
        llmRequestAt = null
        llmFirstTokenAt = null
    }

    /** 듣기가 아무 결과 없이 끝나면 재던 것을 버린다. */
    @Synchronized fun cancel() = clear()
}
