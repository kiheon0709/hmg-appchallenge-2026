package io.moodlab.sideseat.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** LLM 클라이언트 생성. 키가 있을 때만 부른다. */
internal fun createLlmClient(config: VoiceConfig, log: (String) -> Unit): LlmClient = AnthropicLlmClient(config, log)

/** 거절·오류로 답을 못 받았을 때. withFallback이 받아 미리 쓴 문장으로 바꾼다. 메시지에 키나 본문을 넣지 않는다. */
class LlmException(message: String) : IOException(message)

/**
 * Claude Messages API를 HttpURLConnection + SSE로 직접 부른다(의존성 없음).
 *
 * - 스트리밍: text_delta가 오는 대로 흘려보낸다. 생각 블록·fallback 경계 블록은 건너뛴다.
 * - 거절(stop_reason=refusal)·오류 이벤트·HTTP 오류는 [LlmException]. 이미 말한 문장은 되돌리지 않는다.
 * - 예열: 같은 시스템 프롬프트·생각·effort로 max_tokens=0 요청 → TLS 연결이 풀에 남고 프롬프트 캐시가 써진다.
 * - 키는 헤더에만 싣는다. 로그에는 상태 코드·오류 종류·토큰 수·시간만 남긴다.
 */
internal class AnthropicLlmClient(
    private val config: VoiceConfig,
    private val log: (String) -> Unit,
) : LlmClient {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun model(route: LlmClient.Route) = if (route == LlmClient.Route.FAST) config.fastModel else config.reviewModel

    override fun stream(route: LlmClient.Route, system: String, user: String, maxTokens: Long): Flow<String> = flow {
        val model = model(route)
        val t0 = System.nanoTime()
        val conn = open(model)
        var clean = false
        coroutineScope {
            // 취소되면 막혀 있는 readLine을 풀려고 연결을 끊는다. 정상으로 끝나면 끊지 않는다:
            // 응답을 끝까지 읽고 닫기만 하면 TLS 연결이 풀로 돌아가 다음 질문이 핸드셰이크 없이 바로 나간다(실측 11-14: 매번 끊고 있었음).
            val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { if (!clean) runCatching { conn.disconnect() } }
            }
            try {
                conn.outputStream.use { it.write(ClaudeWire.body(model, system, user, maxTokens, stream = true).toByteArray(Charsets.UTF_8)) }
                log("llm sent route=$route at=${ms(t0)}ms") // 연결(재사용이면 거의 0)·TLS·본문 쓰기까지
                val code = conn.responseCode
                if (code != 200) {
                    val type = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() }?.let(ClaudeWire::errorType) }.getOrNull()
                    log("llm http=$code type=$type route=$route model=$model")
                    throw LlmException("http $code $type")
                }
                log("llm headers route=$route model=$model at=${ms(t0)}ms")
                val parser = ClaudeWire.SseParser()
                var firstText = true
                var stopReason: String? = null
                val reader = conn.inputStream.bufferedReader(Charsets.UTF_8)
                try {
                    while (true) {
                        ensureActive()
                        val line = reader.readLine() ?: break
                        val data = parser.line(line)?.second ?: continue
                        when (val e = ClaudeWire.decode(data)) {
                            is ClaudeWire.StreamEvent.Text -> if (e.text.isNotEmpty()) {
                                if (firstText) { firstText = false; log("llm first text route=$route at=${ms(t0)}ms") }
                                emit(e.text)
                            }
                            is ClaudeWire.StreamEvent.Started ->
                                log("llm start route=$route served=${e.model} input=${e.inputTokens} cacheRead=${e.cacheRead} cacheWrite=${e.cacheWrite} at=${ms(t0)}ms")
                            is ClaudeWire.StreamEvent.Stopped -> {
                                stopReason = e.stopReason ?: stopReason
                                e.outputTokens?.let { log("llm stop=$stopReason out=$it at=${ms(t0)}ms") }
                            }
                            is ClaudeWire.StreamEvent.Failed -> { log("llm stream error type=${e.type}"); throw LlmException("stream ${e.type}") }
                            ClaudeWire.StreamEvent.Fallback -> log("llm server fallback route=$route")
                            ClaudeWire.StreamEvent.Ignored -> Unit
                        }
                    }
                    clean = true
                } finally {
                    runCatching { reader.close() }
                }
                if (stopReason == "refusal") throw LlmException("refusal")
            } finally {
                watcher.cancel()
                if (!clean) runCatching { conn.disconnect() }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun prewarm(route: LlmClient.Route, system: String) {
        val model = model(route)
        io.launch {
            val t0 = System.nanoTime()
            runCatching {
                // 응답을 끝까지 읽고 닫기만 한다. disconnect하지 않아야 TLS 연결이 풀에 남아 다음 요청이 빨라진다.
                val conn = open(model)
                conn.outputStream.use { it.write(ClaudeWire.body(model, system, "warmup", 0, stream = false).toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val body = (if (code == 200) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val usage = cacheUsage(body)
                log("llm prewarm route=$route model=$model http=$code ${usage ?: "type=${ClaudeWire.errorType(body)}"} at=${ms(t0)}ms")
            }.onFailure { log("llm prewarm failed route=$route ${it.javaClass.simpleName}") }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun cacheUsage(body: String): String? {
        val usage = ((MiniJson.parse(body) as? Map<String, Any?>)?.get("usage") as? Map<String, Any?>) ?: return null
        return "cacheRead=${usage["cache_read_input_tokens"]} cacheWrite=${usage["cache_creation_input_tokens"]} input=${usage["input_tokens"]}"
    }

    private fun open(model: String): HttpURLConnection =
        (URL(ClaudeWire.URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("content-type", "application/json")
            setRequestProperty("accept", "text/event-stream, application/json")
            setRequestProperty("anthropic-version", ClaudeWire.API_VERSION)
            setRequestProperty("x-api-key", config.apiKey)
            if (ClaudeWire.supportsServerFallback(model)) setRequestProperty("anthropic-beta", ClaudeWire.FALLBACK_BETA)
        }

    private fun ms(t0: Long) = (System.nanoTime() - t0) / 1_000_000

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 20_000
    }
}
