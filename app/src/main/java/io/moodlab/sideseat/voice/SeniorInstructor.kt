package io.moodlab.sideseat.voice

import io.moodlab.sideseat.core.DriveSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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
    private class Speculation(val key: String, val parked: Boolean, val context: String, val channel: Channel<String>, val job: Job, val startedAt: Long) {
        @Volatile var firstTokenAt: Long? = null
    }

    @Volatile private var prepared: Prepared? = null
    @Volatile private var speculation: Speculation? = null

    /** 추측 요청 통계(버려지는 비율을 로그로 본다). 시작 = 맞음 + 틀림 + 새 추측에 밀림 + 쓰지 않음. */
    private var specStarted = 0
    private var specHit = 0
    private var specMiss = 0
    private var specReplaced = 0
    private var specUnused = 0

    private fun logSpecStats(event: String) {
        val discarded = specMiss + specReplaced + specUnused
        val rate = if (specStarted == 0) 0 else discarded * 100 / specStarted
        log("llm speculative $event stats started=$specStarted hit=$specHit miss=$specMiss replaced=$specReplaced unused=$specUnused discarded=$rate%")
    }

    override fun prepareQuestion(parked: Boolean, context: String) {
        prepared = Prepared(parked, context)
    }

    override fun onListenStart() {
        val client = llm ?: return
        val t = now()
        if (t - lastPrewarmAt < PREWARM_INTERVAL_MS) return
        lastPrewarmAt = t
        // 연결 두 개를 데운다: 추측 요청이 버려져 남은 응답을 읽는 동안에도 실제 요청이 핸드셰이크 없이 나가게.
        log("llm prewarm route=FAST x$PREWARM_CONNECTIONS")
        repeat(PREWARM_CONNECTIONS) { client.prewarm(LlmClient.Route.FAST, system) }
    }

    override fun onEpdEnd(lastPartial: String) {
        meter.onEpdEnd()
        val p = prepared ?: return
        val client = llm ?: return
        if (lastPartial.isBlank()) return
        if (fastPath.decide(lastPartial, p.parked, llmAvailable = true) != null) return
        val key = ReplyClassifier.normalize(lastPartial)
        if (speculation?.key == key) return
        speculation?.let { it.job.cancel(); specReplaced++ }
        val ch = Channel<String>(Channel.UNLIMITED)
        val startedAt = now()
        lateinit var spec: Speculation
        val job = scope.launch {
            try {
                client.stream(LlmClient.Route.FAST, system, InstructorPrompt.question(lastPartial, p.parked, p.context), maxTokens(p.parked))
                    .collect { if (spec.firstTokenAt == null) spec.firstTokenAt = now(); ch.send(it) }
                ch.close()
            } catch (e: Throwable) {
                ch.close(e)
            }
        }
        spec = Speculation(key, p.parked, p.context, ch, job, startedAt)
        speculation = spec
        specStarted++
        lastPrewarmAt = startedAt // 연결을 방금 썼다
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
            specMiss++
            logSpecStats("miss")
            return null
        }
        specHit++
        log("llm speculative hit lead=${now() - s.startedAt}ms")
        logSpecStats("hit")
        meter.onLlmRequest(s.startedAt)
        s.firstTokenAt?.let { meter.onLlmFirstToken(it) }
        // 실패로 닫힌 채널은 읽을 때 그 예외를 던진다 → withFallback이 받는다.
        return flow {
            try {
                for (piece in s.channel) {
                    s.firstTokenAt?.let { meter.onLlmFirstToken(it) }
                    emit(piece)
                }
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
            speculation?.let { it.job.cancel(); specUnused++; logSpecStats("unused") }; speculation = null
            meter.cancel()
            return@flow
        }
        val fast = fastPath.decide(q, parked, llmAvailable = llm != null)
        if (fast != null) {
            speculation?.let { it.job.cancel(); specUnused++; logSpecStats("unused") }; speculation = null
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
        val textStarted = AtomicBoolean(false) // LLM 스트림(IO)에서 쓰고 채움말 타이머에서 읽는다
        val source = (spec ?: client.stream(LlmClient.Route.FAST, system, InstructorPrompt.question(q, parked, context), maxTokens(parked))
            .onStart { meter.onLlmRequest(); lastPrewarmAt = now() })
            .onEach { textStarted.set(true); meter.onLlmFirstToken() }
        val maxSentences = if (parked) 4 else 2
        val sentences = source.toSentences(firstPieceAtComma = true)
            .takeSentences(maxSentences)
            .withFallback(FIRST_SENTENCE_TIMEOUT_MS, onFailure = { log("llm answer fallback cause=${it?.javaClass?.simpleName}: ${it?.message?.take(120)}") }) {
                fastPath.fallback(q, parked)
            }
            // 첫 문장이 "만들어진" 시각을 잰다(재생 차례를 기다리는 시간과 나눠 보려고). withFiller가 위쪽을 먼저 모아 두므로 여기는 생산 시점이다.
            .onEach { meter.onFirstSentence() }
            // LLM 글자가 이미 오기 시작했으면 첫 조각이 곧 나오므로 채움말을 내지 않는다(채움말이 끝날 때까지 답이 기다리게 되므로).
            .withFiller(FILLER_AFTER_MS, FILLER, onFiller = { log("filler spoken"); meter.onFiller() }, suppress = { textStarted.get() })
        emitAll(sentences)
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
        /**
         * 채움말. 순차 재생이라 채움말이 끝나야 답이 나오므로 아주 짧게 둔다.
         * 실측(11-14): "음, 잠깐만요."(약 1.3초)를 쓰면 LLM 첫 문장이 0.7~1.6초에 와도 답이 약 2초에 나왔다.
         */
        const val FILLER = "음."
        /**
         * 이 시간 안에 첫 조각이 없고 LLM 글자도 아직 안 왔으면 채움말. 명세 10.2는 0.6초였지만 실측(11-14, 24회)에서
         * 1초를 넘은 6회가 모두 "0.6초에 채움말 시작 → 첫 조각이 곧 왔는데 채움말이 끝날 때까지 0.43~0.59초 대기"였다.
         * 서버 첫 토큰 중앙값이 0.53~0.57초라 0.9초면 거의 항상 글자가 먼저 와서 채움말이 생략된다.
         */
        const val FILLER_AFTER_MS = 900L
        /** 이 안에 첫 문장이 없으면 LLM을 버리고 지식 파일 답으로. */
        const val FIRST_SENTENCE_TIMEOUT_MS = 3_500L
        const val REVIEW_FILLER = "오늘 운전, 같이 돌아볼게요."
        const val REVIEW_FILLER_AFTER_MS = 700L
        const val REVIEW_FIRST_TIMEOUT_MS = 6_000L
        /** 답은 문장 수로 자르고, 남은 응답은 연결 재사용을 위해 끝까지 읽어 버리므로 작게 둔다(한국어 2문장 ≈ 100토큰). */
        const val ANSWER_MAX_TOKENS_DRIVING = 200L
        const val ANSWER_MAX_TOKENS_PARKED = 400L
        /** 총평 모델은 생각(thinking)을 끌 수 없어 그 몫까지 둔다. */
        const val REVIEW_MAX_TOKENS = 4_000L
        /**
         * 마이크를 열 때마다 연결·캐시를 데운다(max_tokens 0 요청). 풀에 살아 있는 연결이 있으면 그것을 다시 쓰고,
         * 없으면 사용자가 말하는 동안(1초 이상) TLS를 새로 맺어 둔다. 바로 앞(3초 안)에 연결을 썼으면 건너뛴다.
         */
        const val PREWARM_INTERVAL_MS = 3_000L
        const val PREWARM_CONNECTIONS = 2
    }
}
