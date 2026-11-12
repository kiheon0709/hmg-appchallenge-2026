package io.moodlab.sideseat.voice

import io.moodlab.sideseat.core.DriveSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

/** LLM 한 번 호출. 텍스트 조각을 흘려보내고, 실패(거절·네트워크·인증)는 예외로 알린다. */
interface LlmClient {
    enum class Route { FAST, REVIEW }

    fun stream(route: Route, system: String, user: String, maxTokens: Long): Flow<String>

    /** 연결과 프롬프트 캐시를 미리 데운다. 실패해도 조용히 넘어간다. */
    fun prewarm(route: Route, system: String)
}

/**
 * AI 교관. 명세 10.2의 지연 구조를 구현한다.
 *
 * - 빠른 길([FastPath]): 짧은 대답·지식 파일에 있는 질문은 LLM 없이 바로.
 * - 느린 길([LlmClient]): 듣기 시작하면 연결·캐시 예열 → 말 끝(EPD)에서 추측 요청 → STT 최종이 같으면 그대로 사용.
 *   응답은 문장 단위로 끊어 흘려보내고, 0.6초 안에 첫 문장이 없으면 "음, 잠깐만요."를 먼저.
 * - LLM이 없거나 실패하면 지식 파일 안의 답, 그것도 없으면 "모르겠어요", 총평은 규칙 기반 총평.
 */
internal class SeniorInstructor(
    private val knowledge: KnowledgeBase,
    private val llm: LlmClient?,
    private val meter: LatencyMeter,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val log: (String) -> Unit,
) : Instructor, ListenHooks {

    private val fastPath = FastPath(knowledge)
    private val system = InstructorPrompt.system(knowledge)
    private var lastPrewarmAt = Long.MIN_VALUE / 2

    override val lastLatencyMs: StateFlow<Long?> get() = meter.last

    // ---------- 추측 요청 ----------
    private class Prepared(val parked: Boolean, val context: String)
    private class Speculation(val key: String, val parked: Boolean, val context: String, val channel: Channel<String>, val job: Job)

    @Volatile private var prepared: Prepared? = null
    @Volatile private var speculation: Speculation? = null

    override fun prepareQuestion(parked: Boolean, context: String) {
        prepared = Prepared(parked, context)
    }

    override fun onListenStart() {
        val client = llm ?: return
        val t = now()
        if (t - lastPrewarmAt < PREWARM_INTERVAL_MS) return
        lastPrewarmAt = t
        log("llm prewarm route=FAST")
        client.prewarm(LlmClient.Route.FAST, system)
    }

    override fun onEpdEnd(lastPartial: String) {
        meter.onEpdEnd()
        val p = prepared ?: return
        val client = llm ?: return
        if (lastPartial.isBlank()) return
        if (fastPath.decide(lastPartial, p.parked, llmAvailable = true) != null) return
        val key = ReplyClassifier.normalize(lastPartial)
        if (speculation?.key == key) return
        speculation?.job?.cancel()
        val ch = Channel<String>(Channel.UNLIMITED)
        val job = scope.launch {
            try {
                client.stream(LlmClient.Route.FAST, system, InstructorPrompt.question(lastPartial, p.parked, p.context), maxTokens(p.parked))
                    .collect { ch.send(it) }
                ch.close()
            } catch (e: Throwable) {
                ch.close(e)
            }
        }
        speculation = Speculation(key, p.parked, p.context, ch, job)
        log("llm speculative start chars=${lastPartial.length}")
    }

    override fun onFinal(text: String) {
        meter.onSpeechEnd()
    }

    private fun takeSpeculation(question: String, parked: Boolean, context: String): Flow<String>? {
        val s = speculation ?: return null
        speculation = null
        val hit = s.key == ReplyClassifier.normalize(question) && s.parked == parked && s.context == context
        if (!hit) {
            s.job.cancel()
            log("llm speculative miss")
            return null
        }
        log("llm speculative hit")
        // 실패로 닫힌 채널은 읽을 때 그 예외를 던진다 → withFallback이 받는다.
        return flow {
            try {
                for (piece in s.channel) emit(piece)
            } finally {
                s.job.cancel()
            }
        }
    }

    // ---------- Instructor ----------
    override fun answer(question: String, parked: Boolean, context: String): Flow<String> = flow {
        prepared = null
        val q = question.trim()
        if (q.isEmpty()) {
            speculation?.job?.cancel(); speculation = null
            meter.cancel()
            return@flow
        }
        val fast = fastPath.decide(q, parked, llmAvailable = llm != null)
        if (fast != null) {
            speculation?.job?.cancel(); speculation = null
            meter.onAnswerRequested(if (fast is FastPath.Decision.ShortReply) "fast-reply" else "fast-knowledge")
            fast.sentences.forEachIndexed { i, s -> if (i == 0) meter.onFirstSentence(); emit(s) }
            return@flow
        }
        val client = llm
        if (client == null) {
            meter.onAnswerRequested("offline")
            fastPath.fallback(q, parked).forEachIndexed { i, s -> if (i == 0) meter.onFirstSentence(); emit(s) }
            return@flow
        }
        val spec = takeSpeculation(q, parked, context)
        meter.onAnswerRequested(if (spec != null) "llm-speculative" else "llm")
        val source = spec ?: client.stream(LlmClient.Route.FAST, system, InstructorPrompt.question(q, parked, context), maxTokens(parked))
        val maxSentences = if (parked) 4 else 2
        val sentences = source.toSentences()
            .take(maxSentences)
            .withFallback(FIRST_SENTENCE_TIMEOUT_MS, onFailure = { log("llm answer fallback cause=${it?.javaClass?.simpleName}: ${it?.message?.take(120)}") }) {
                fastPath.fallback(q, parked)
            }
            .withFiller(FILLER_AFTER_MS, FILLER, onFiller = { log("filler spoken") })
        var first = true
        sentences.collect {
            if (first) { first = false; meter.onFirstSentence() }
            emit(it)
        }
    }

    override fun review(summary: DriveSummary): Flow<String> {
        val ruleBased = summary.ruleBasedReview.filter { it.isNotBlank() }
        val client = llm ?: return ruleBased.asFlow()
        val user = InstructorPrompt.review(summary)
        return client.stream(LlmClient.Route.REVIEW, system, user, REVIEW_MAX_TOKENS)
            .toSentences()
            .take(4)
            .withFallback(REVIEW_FIRST_TIMEOUT_MS, onFailure = { log("llm review fallback cause=${it?.javaClass?.simpleName}: ${it?.message?.take(120)}") }) {
                ruleBased
            }
            .withFiller(REVIEW_FILLER_AFTER_MS, REVIEW_FILLER)
    }

    /** 리뷰 화면이 열리기 전에 총평 모델의 연결·캐시를 데운다. */
    fun prewarmReview() {
        llm?.prewarm(LlmClient.Route.REVIEW, system)
    }

    private fun maxTokens(parked: Boolean): Long = if (parked) ANSWER_MAX_TOKENS_PARKED else ANSWER_MAX_TOKENS_DRIVING

    companion object {
        const val FILLER = "음, 잠깐만요."
        const val FILLER_AFTER_MS = 600L
        /** 이 안에 첫 문장이 없으면 LLM을 버리고 지식 파일 답으로. */
        const val FIRST_SENTENCE_TIMEOUT_MS = 3_500L
        const val REVIEW_FILLER = "오늘 운전, 같이 돌아볼게요."
        const val REVIEW_FILLER_AFTER_MS = 700L
        const val REVIEW_FIRST_TIMEOUT_MS = 6_000L
        const val ANSWER_MAX_TOKENS_DRIVING = 400L
        const val ANSWER_MAX_TOKENS_PARKED = 800L
        /** 총평 모델은 생각(thinking)을 끌 수 없어 그 몫까지 둔다. */
        const val REVIEW_MAX_TOKENS = 4_000L
        /** 5분 캐시가 끝나기 전에 다시 데운다. */
        const val PREWARM_INTERVAL_MS = 4 * 60_000L
    }
}
