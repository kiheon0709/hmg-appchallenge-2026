package io.moodlab.sideseat.voice

import io.moodlab.sideseat.core.DriveSummary
import io.moodlab.sideseat.core.ReplyKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 실측 로그 태그. 통합 세션이 logcat에서 이 태그로 값을 모은다. */
internal const val MEASURE_TAG = "SideSeatMeasure"

/** 선배의 목소리와 귀. Pleos TextToSpeech·SpeechToText(ON_DEVICE)로 동작한다. */
interface Voice {
    /** 큐에 넣고 순서대로 말한다. urgent면 지금 하던 말과 대기열을 끊고 먼저 말한다. */
    fun say(text: String, urgent: Boolean = false)

    /** 문장 흐름을 한 발화로 이어 말한다. [Instructor.answer]·[Instructor.review]의 결과를 그대로 넘기면 된다. */
    fun sayStreaming(sentences: Flow<String>)

    fun stopSpeaking()
    val speaking: StateFlow<Boolean>

    /** 마이크를 열고 한 번 듣는다. 중간 결과는 [onPartial]로, 끝나면 최종 텍스트(없으면 null). */
    suspend fun listen(kind: ReplyKind, onPartial: (String) -> Unit = {}): String?
    val listening: StateFlow<Boolean>
}

/**
 * 효과음. 이름: rain·wind·engine·hazard·breath(항상 반복), thunder, tire, alarm, click, tick, chime,
 * wiper, engine_rise, yawn, unlock, buzzer, success, fail. 모르는 이름은 무시하고 로그만 남긴다.
 */
interface Sfx {
    fun play(name: String, loop: Boolean = false)
    fun stop(name: String)
    fun stopAll()
}

/** AI 교관. LLM이 없거나 실패해도 미리 쓴 문장과 규칙 기반 총평으로 끝까지 답한다. */
interface Instructor {
    /** 리뷰 총평을 문장 단위로 흘려보낸다. LLM이 안 되면 summary.ruleBasedReview를 그대로. */
    fun review(summary: DriveSummary): Flow<String>

    /** 질문에 답한다. parked=false면 1~2문장. 빠른 길로 끝나면 LLM을 부르지 않는다. */
    fun answer(question: String, parked: Boolean, context: String): Flow<String>

    /** 말 끝(STT 최종) → 첫 음성(TTS onStart). 아직 잰 적이 없으면 null. */
    val lastLatencyMs: StateFlow<Long?>

    /**
     * (선택, 계약 추가분) 질문을 들으려고 마이크를 열기 직전에 부른다. 같은 parked·context로 [answer]를 부르면
     * 말이 끝나는 순간(EPD) STT 최종 결과를 기다리지 않고 LLM 요청을 먼저 보낸다. 안 불러도 동작은 같다.
     */
    fun prepareQuestion(parked: Boolean, context: String) {}
}

/** Voice가 듣는 동안 Instructor에게 알려 주는 신호. VoiceModule이 둘을 잇는다. */
internal interface ListenHooks {
    fun onListenStart() {}
    fun onPartial(text: String) {}
    /** 음성 끝이 감지됨(EPD). 최종 결과는 아직. */
    fun onEpdEnd(lastPartial: String) {}
    fun onFinal(text: String) {}
}

/** 통합 세션이 BuildConfig에서 꺼내 넘기는 설정. apiKey가 비어 있으면 LLM 없이 동작한다. */
data class VoiceConfig(
    val apiKey: String,
    val fastModel: String = DEFAULT_FAST_MODEL,
    val reviewModel: String = DEFAULT_REVIEW_MODEL,
    /** BuildConfig.LLM_PROVIDER. 비어 있거나 anthropic·claude일 때만 LLM을 켠다(다른 회사 키를 Claude API로 보내지 않게). */
    val provider: String = "",
    /** 실측용: TTS가 말하는 중에도 마이크를 열지(끼어들기). 기본은 말이 끝난 뒤 연다. */
    val bargeIn: Boolean = false,
    /** 실측용: 문장 흐름을 speak(isFinal=false)로 이어 보낼지. false면 문장마다 따로 말한다. */
    val chunkedTts: Boolean = true,
) {
    val llmEnabled: Boolean
        get() = apiKey.isNotBlank() && provider.trim().lowercase().let { it.isEmpty() || it == "anthropic" || it == "claude" }

    /** 키가 로그에 찍히지 않게 한다. */
    override fun toString(): String =
        "VoiceConfig(llmEnabled=$llmEnabled, provider=${provider.ifBlank { "-" }}, fastModel=$fastModel, reviewModel=$reviewModel, bargeIn=$bargeIn, chunkedTts=$chunkedTts)"

    companion object {
        /** 주행 중 질문: 첫 문장까지의 시간이 가장 중요하다. */
        const val DEFAULT_FAST_MODEL = "claude-haiku-5-5"
        /** 리뷰 총평: 정차 중이라 품질을 우선한다. */
        const val DEFAULT_REVIEW_MODEL = "claude-opus-5-5"
    }
}
