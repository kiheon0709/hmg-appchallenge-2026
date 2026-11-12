package io.moodlab.sideseat.voice

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 로컬 가짜 HTTP 서버(ServerSocket, HTTP/1.1 chunked, keep-alive)로 연결 재사용을 확인한다.
 * 받는 쪽이 첫 조각만 받고 그만둬도 클라이언트가 응답을 끝까지 읽어 연결을 풀에 돌려주면,
 * 다음 요청은 같은 TCP 연결로 온다. (JVM HttpURLConnection 기준. 안드로이드 구현도 응답을 다 읽고 닫으면 다시 쓴다.)
 */
class AnthropicLlmClientTest {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    /** 요청마다 (연결 번호, 응답을 끝까지 보냈는지). */
    private val requests = Collections.synchronizedList(mutableListOf<Pair<Int, Boolean>>())
    private val logs = Collections.synchronizedList(mutableListOf<String>())
    @Volatile private var status = 200
    @Volatile private var done = CountDownLatch(1)

    init {
        thread(isDaemon = true) {
            var connId = 0
            while (!server.isClosed) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                val id = ++connId
                thread(isDaemon = true) { serve(sock, id) }
            }
        }
    }

    private fun serve(sock: Socket, id: Int) = sock.use {
        val input = BufferedInputStream(sock.getInputStream())
        val out = sock.getOutputStream()
        while (true) {
            val headers = readHeaders(input) ?: return
            val len = headers.lines().firstOrNull { it.lowercase().startsWith("content-length:") }?.substringAfter(':')?.trim()?.toInt() ?: 0
            repeat(len) { input.read() }
            if (status != 200) {
                val body = """{"type":"error","error":{"type":"authentication_error","message":"x"}}""".toByteArray()
                out.write("HTTP/1.1 $status Unauthorized\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray())
                out.write(body); out.flush()
                requests += id to true
                done.countDown()
                continue
            }
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\n\r\n".toByteArray())
            var ok = true
            try {
                chunk(out, sse("message_start", """{"type":"message_start","message":{"model":"m","usage":{"input_tokens":1,"cache_read_input_tokens":9}}}"""))
                listOf("첫 문장이에요. ", "둘째 문장. ", "셋째 문장. ", "넷째 문장.").forEach { t ->
                    chunk(out, sse("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"$t"}}"""))
                    Thread.sleep(80)
                }
                chunk(out, sse("message_delta", """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":20}}"""))
                chunk(out, sse("message_stop", """{"type":"message_stop"}"""))
                out.write("0\r\n\r\n".toByteArray()); out.flush()
            } catch (e: Exception) {
                ok = false // 클라이언트가 연결을 끊으면 쓰기가 실패한다
            }
            requests += id to ok
            done.countDown()
            if (!ok) return
        }
    }

    private fun readHeaders(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (!sb.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b < 0) return null
            sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun chunk(out: OutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        out.write("${Integer.toHexString(bytes.size)}\r\n".toByteArray())
        out.write(bytes)
        out.write("\r\n".toByteArray())
        out.flush()
    }

    private fun sse(type: String, json: String) = "event: $type\ndata: $json\n\n"

    @After fun stop() = server.close()

    private fun client() = AnthropicLlmClient(
        VoiceConfig(apiKey = "test-key"), { logs += it }, url = "http://127.0.0.1:${server.localPort}/v1/messages",
    )

    @Test fun earlyStopDrainsAndReusesConnection() = runBlocking {
        val c = client()
        // 1) 첫 조각만 받고 그만둔다(문장 수 제한·추측 요청 버림과 같은 상황)
        assertEquals("첫 문장이에요. ", c.stream(LlmClient.Route.FAST, "s", "u", 10).first())
        assertTrue("서버 응답이 끝나야 함", done.await(5, TimeUnit.SECONDS))
        Thread.sleep(300) // 클라이언트가 남은 응답을 다 읽고 닫을 시간
        done = CountDownLatch(1)
        // 2) 다음 요청은 끝까지 받는다
        assertEquals(4, c.stream(LlmClient.Route.FAST, "s", "u", 10).toList().size)
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals("첫 요청도 끊기지 않고 끝까지 보내져야 함 logs=$logs", listOf(true, true), requests.map { it.second })
        assertEquals("두 번째 요청이 같은 연결로 와야 함 logs=$logs", requests[0].first, requests[1].first)
        assertTrue(logs.toString(), logs.any { "draining" in it } && logs.any { "connection kept" in it })
        assertTrue(logs.toString(), logs.any { "conn=reused" in it })
    }

    @Test fun httpErrorBecomesLlmExceptionWithoutLeakingKey() = runBlocking {
        status = 401
        val r = runCatching { client().stream(LlmClient.Route.FAST, "s", "u", 10).toList() }
        assertTrue(r.exceptionOrNull().toString(), r.exceptionOrNull() is LlmException)
        assertTrue(logs.toString(), logs.any { "http=401 type=authentication_error" in it })
        assertTrue("키가 로그에 남으면 안 됨", logs.none { "test-key" in it })
    }
}
