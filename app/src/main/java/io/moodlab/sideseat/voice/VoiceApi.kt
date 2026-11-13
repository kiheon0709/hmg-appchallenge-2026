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
 * 효과음. 이름: rain·wind·engine·hazard·breath(항상 반복), yawn(하품 한 번+숨, 7초), thunder, tire, alarm, click, tick, chime,
 * wiper, engine_rise, unlock, buzzer, success, fail, wake(호출 확인음). 모르는 이름은 무시하고 로그만 남긴다.
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

/**
 * 주행 중 손 안 대는 호출. 켜 두면 Pleos STT(ON_DEVICE)로 계속 들으며 맨 앞의 호출어([Persona.WAKE_WORDS])를 기다린다.
 * 호출어가 들리면 확인음("wake") → 같은 말에 붙은 질문(없으면 이어서 한 번 더 듣기)을 [start]의 onQuestion으로 넘긴다.
 * 선배가 말을 마치면 [WakeConfig.followUpMs] 동안 호출어 없이 되묻기를 받는다.
 * Gleo 우선: Gleo 호출어가 들리거나, 오디오 포커스를 잃거나, 다른 앱이 마이크를 쓰거나, 통화 중이면 즉시 놓고 물러난다.
 * 선배가 말하는 동안은 듣지 않는다(에코 제거가 없어 자기 목소리를 호출어로 오인할 수 있음).
 */
interface WakeListening {
    val state: StateFlow<WakeState>

    /** 연속 청취를 켠다. onQuestion에서 답을 만들어 Voice.sayStreaming으로 말하면, 말이 끝날 때까지 기다렸다가 다시 듣는다. */
    fun start(onQuestion: suspend (question: String) -> Unit)
    fun stop()

    /** 디버그: 상태·횟수 한 줄. */
    fun debugStatus(): String

    /** 디버그·실측: 다음 한 번을 마이크 대신 이 PCM(16kHz 16bit mono raw)으로 듣는다. */
    fun debugFeedPcm(pcm: ByteArray)
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
    /**
     * 문장 흐름을 speak(isFinal=false)로 이어 보낼지. 기본 false(문장마다 speak(isFinal=true) 후 onDone을 기다림).
     * 실측(11-14): Pleos TTS는 isFinal=false 조각을 다음 speak가 올 때까지 재생하지 않아, 채움말이 LLM 첫 문장을 기다렸다.
     */
    val chunkedTts: Boolean = false,
    /**
     * 말 끝 추측 요청: STT 최종 결과 전에 LLM 요청을 먼저 보내고, 최종 결과가 같으면(끝 어미만 더 붙은 것도 같다고 봄) 그 스트림을 쓴다.
     * 끄면 연결 예열만 한다. 버려진 비율은 로그 `llm speculative … discarded=%`.
     */
    val speculativeRequests: Boolean = true,
    /** 주행 중 연속 청취(호출어) 간격·백오프. */
    val wake: WakeConfig = WakeConfig(),
) {
    val llmEnabled: Boolean
        get() = apiKey.isNotBlank() && provider.trim().lowercase().let { it.isEmpty() || it == "anthropic" || it == "claude" }

    /** 키가 로그에 찍히지 않게 한다. */
    override fun toString(): String =
        "VoiceConfig(llmEnabled=$llmEnabled, provider=${provider.ifBlank { "-" }}, fastModel=$fastModel, reviewModel=$reviewModel, bargeIn=$bargeIn, chunkedTts=$chunkedTts, speculative=$speculativeRequests)"

    companion object {
        /** 주행 중 질문: 첫 문장까지의 시간이 가장 중요하다. */
        const val DEFAULT_FAST_MODEL = "claude-haiku-5-5"
        /** 리뷰 총평: 정차 중이라 품질을 우선한다. */
        const val DEFAULT_REVIEW_MODEL = "claude-opus-5-5"
    }
}
