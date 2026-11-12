package io.moodlab.sideseat.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** LLM 클라이언트 생성. 키가 있을 때만 부른다. */
internal fun createLlmClient(config: VoiceConfig, log: (String) -> Unit): LlmClient = AnthropicLlmClient(config, log)

/** 거절·오류로 답을 못 받았을 때. withFallback이 받아 미리 쓴 문장으로 바꾼다. 메시지에 키나 본문을 넣지 않는다. */
class LlmException(message: String) : IOException(message)

/**
 * Claude Messages API를 HttpURLConnection + SSE로 직접 부른다(의존성 없음).
 *
 * - 스트리밍: text_delta가 오는 대로 흘려보낸다. 생각 블록·fallback 경계 블록은 건너뛴다.
 * - 거절(stop_reason=refusal)·오류 이벤트·HTTP 오류는 [LlmException]. 이미 말한 문장은 되돌리지 않는다.
 * - 연결 재사용(지연의 핵심): HTTP 읽기는 받는 쪽과 따로 [io]에서 돈다. 받는 쪽이 먼저 그만두면(문장 수 제한, 추측 요청 버림)
 *   남은 응답을 끝까지 읽어 버리고 닫는다 → 연결이 풀로 돌아가 다음 질문이 TLS 핸드셰이크 없이 나간다.
 *   실측(11-14, 12회): 받는 쪽이 그만둘 때 끊었더니(Socket closed) 3번째 질문부터 매번 새 연결 0.54~1.21초.
 * - 예열: 같은 시스템 프롬프트·생각·effort로 max_tokens=0 요청 → 연결이 풀에 남고 프롬프트 캐시가 써진다.
 * - 키는 헤더에만 싣는다. 로그에는 상태 코드·오류 종류·토큰 수·시간·연결 재사용 여부만 남긴다.
 */
internal class AnthropicLlmClient(
    private val config: VoiceConfig,
    private val log: (String) -> Unit,
    /** 테스트에서만 바꾼다(로컬 가짜 서버). */
    private val url: String = ClaudeWire.URL,
) : LlmClient {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seq = AtomicInteger(0)

    private fun model(route: LlmClient.Route) = if (route == LlmClient.Route.FAST) config.fastModel else config.reviewModel

    override fun stream(route: LlmClient.Route, system: String, user: String, maxTokens: Long): Flow<String> = flow {
        val ch = Channel<String>(Channel.UNLIMITED)
        val consumerGone = AtomicBoolean(false)
        val id = seq.incrementAndGet()
        io.launch { request(id, route, system, user, maxTokens, ch, consumerGone) }
        try {
            for (piece in ch) emit(piece) // 실패로 닫히면 그 예외를 던진다 → withFallback
        } finally {
            consumerGone.set(true) // 읽기 쪽은 남은 응답을 버리며 끝까지 읽는다
        }
    }

    /** 요청 한 번. 받는 쪽([ch])이 사라져도 응답을 끝까지 읽어(최대 [DRAIN_MAX_MS]) 연결을 풀에 돌려준다. */
    private fun request(
        id: Int, route: LlmClient.Route, system: String, user: String, maxTokens: Long,
        ch: Channel<String>, consumerGone: AtomicBoolean,
    ) {
        val model = model(route)
        val t0 = System.nanoTime()
        var conn: HttpURLConnection? = null
        var clean = false
        var drainingSince = 0L
        try {
            conn = open(model)
            connectLogged(conn, "llm#$id route=$route", t0)
            conn.outputStream.use { it.write(ClaudeWire.body(model, system, user, maxTokens, stream = true).toByteArray(Charsets.UTF_8)) }
            log("llm#$id sent at=${ms(t0)}ms")
            val code = conn.responseCode
            if (code != 200) {
                val type = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() }?.let(ClaudeWire::errorType) }.getOrNull()
                log("llm#$id http=$code type=$type route=$route model=$model")
                clean = true // 오류 본문을 다 읽었으므로 연결은 재사용 가능
                ch.close(LlmException("http $code $type"))
                return
            }
            log("llm#$id headers route=$route model=$model at=${ms(t0)}ms")
            val parser = ClaudeWire.SseParser()
            var firstText = true
            var stopReason: String? = null
            var failure: Throwable? = null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    if (consumerGone.get()) {
                        if (drainingSince == 0L) {
                            drainingSince = System.nanoTime()
                            log("llm#$id consumer stopped, draining at=${ms(t0)}ms")
                        } else if (ms(drainingSince) > DRAIN_MAX_MS) {
                            log("llm#$id drain too long, disconnect")
                            return
                        }
                    }
                    val line = reader.readLine() ?: break
                    val data = parser.line(line)?.second ?: continue
                    when (val e = ClaudeWire.decode(data)) {
                        is ClaudeWire.StreamEvent.Text -> if (e.text.isNotEmpty() && !consumerGone.get()) {
                            if (firstText) { firstText = false; log("llm#$id first text route=$route at=${ms(t0)}ms") }
                            ch.trySend(e.text)
                        }
                        is ClaudeWire.StreamEvent.Started ->
                            log("llm#$id start route=$route served=${e.model} input=${e.inputTokens} cacheRead=${e.cacheRead} cacheWrite=${e.cacheWrite} at=${ms(t0)}ms")
                        is ClaudeWire.StreamEvent.Stopped -> {
                            stopReason = e.stopReason ?: stopReason
                            e.outputTokens?.let { log("llm#$id stop=$stopReason out=$it at=${ms(t0)}ms") }
                        }
                        is ClaudeWire.StreamEvent.Failed -> { log("llm#$id stream error type=${e.type}"); failure = LlmException("stream ${e.type}") }
                        ClaudeWire.StreamEvent.Fallback -> log("llm#$id server fallback route=$route")
                        ClaudeWire.StreamEvent.Ignored -> Unit
                    }
                }
            }
            clean = true
            if (drainingSince != 0L) log("llm#$id drained in ${ms(drainingSince)}ms, connection kept")
            when {
                failure != null -> ch.close(failure)
                stopReason == "refusal" -> ch.close(LlmException("refusal"))
                else -> ch.close()
            }
        } catch (e: Throwable) {
            log("llm#$id failed ${e.javaClass.simpleName}")
            ch.close(e)
        } finally {
            // 끝까지 읽고 닫았으면 끊지 않는다(풀로 돌아감). 중간에 실패·포기했을 때만 끊는다.
            if (!clean) runCatching { conn?.disconnect() }
        }
    }

    /** 연결을 맺고 걸린 시간을 남긴다. 풀의 연결을 다시 쓰면 수 ms, 새로 맺으면 DNS·TCP·TLS로 수백 ms. */
    private fun connectLogged(conn: HttpURLConnection, tag: String, t0: Long) {
        conn.connect()
        val connectMs = ms(t0)
        log("$tag conn=${if (connectMs < REUSED_BELOW_MS) "reused" else "new"} connect=${connectMs}ms")
    }

    override fun prewarm(route: LlmClient.Route, system: String) {
        val model = model(route)
        io.launch {
            val t0 = System.nanoTime()
            runCatching {
                // 응답을 끝까지 읽고 닫기만 한다. disconnect하지 않아야 TLS 연결이 풀에 남아 다음 요청이 빨라진다.
                val conn = open(model)
                connectLogged(conn, "llm prewarm route=$route", t0)
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
        (URL(url).openConnection() as HttpURLConnection).apply {
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
        /** 연결에 이보다 적게 걸리면 풀의 연결을 다시 쓴 것으로 본다(로그용). */
        private const val REUSED_BELOW_MS = 40L
        /** 받는 쪽이 그만둔 뒤 남은 응답을 읽어 버리는 최대 시간. 넘으면 끊는다. */
        private const val DRAIN_MAX_MS = 8_000L
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 20_000
    }
}
