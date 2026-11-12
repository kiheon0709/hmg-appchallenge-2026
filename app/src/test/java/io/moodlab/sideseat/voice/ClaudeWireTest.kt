package io.moodlab.sideseat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeWireTest {

    @Suppress("UNCHECKED_CAST")
    private fun parseBody(json: String) = MiniJson.parse(json) as Map<String, Any?>

    @Test fun fastBodyDisablesThinkingAndCachesSystem() {
        val sys = "선배 \"따옴표\"\n줄바꿈\t탭 \\ 역슬래시 \u0001"
        val b = parseBody(ClaudeWire.body("claude-haiku-5-5", sys, "질문", 400, stream = true))
        assertEquals("claude-haiku-5-5", b["model"])
        assertEquals(400L, b["max_tokens"])
        assertEquals(true, b["stream"])
        val system = (b["system"] as List<Map<String, Any?>>).single()
        assertEquals(sys, system["text"]) // 이스케이프 왕복
        assertEquals(mapOf("type" to "ephemeral"), system["cache_control"])
        assertEquals(listOf(mapOf("role" to "user", "content" to "질문")), b["messages"])
        assertEquals(mapOf("type" to "disabled"), b["thinking"])
        assertEquals(mapOf("effort" to "low"), b["output_config"])
        assertFalse("Haiku는 서버 쪽 fallback이 없다", b.containsKey("fallbacks"))
    }

    @Test fun reviewBodyKeepsAdaptiveThinkingAndAddsFallback() {
        val b = parseBody(ClaudeWire.body("claude-opus-5-5", "s", "u", 4000, stream = true))
        assertFalse("Opus 5.5는 thinking을 끌 수 없다(400)", b.containsKey("thinking"))
        assertEquals("default", b["fallbacks"])
        assertTrue(ClaudeWire.supportsServerFallback("claude-opus-5-5"))
    }

    @Test fun sonnetUsesBetweenTools() {
        val b = parseBody(ClaudeWire.body("claude-sonnet-5-5", "s", "u", 10, stream = false))
        assertEquals(mapOf("type" to "between_tools"), b["thinking"])
        assertFalse(b.containsKey("stream"))
    }

    @Test fun prewarmBodyMatchesRealRequestPrefix() {
        // 캐시는 앞부분 일치: 예열과 실제 요청의 system·thinking·effort가 같아야 한다.
        val warm = parseBody(ClaudeWire.body("claude-haiku-5-5", "SYS", "warmup", 0, stream = false))
        val real = parseBody(ClaudeWire.body("claude-haiku-5-5", "SYS", "질문", 400, stream = true))
        listOf("model", "system", "thinking", "output_config").forEach { assertEquals(it, real[it], warm[it]) }
        assertEquals(0L, warm["max_tokens"])
    }

    @Test fun sseParserGroupsEventAndData() {
        val p = ClaudeWire.SseParser()
        assertNull(p.line("event: content_block_delta"))
        assertNull(p.line("data: {\"a\":1}"))
        assertEquals("content_block_delta" to "{\"a\":1}", p.line(""))
        assertNull(p.line(": ping comment"))
        assertNull(p.line(""))
    }

    @Test fun decodesStreamEvents() {
        val text = ClaudeWire.decode("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"비가 와요.\n"}}""")
        assertEquals(ClaudeWire.StreamEvent.Text("비가 와요.\n"), text)
        val thinking = ClaudeWire.decode("""{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":""}}""")
        assertEquals(ClaudeWire.StreamEvent.Ignored, thinking)
        val start = ClaudeWire.decode("""{"type":"message_start","message":{"id":"m","model":"claude-haiku-5-5","usage":{"input_tokens":12,"cache_read_input_tokens":3400,"cache_creation_input_tokens":0}}}""")
        assertEquals(ClaudeWire.StreamEvent.Started("claude-haiku-5-5", 3400, 0, 12), start)
        val stop = ClaudeWire.decode("""{"type":"message_delta","delta":{"stop_reason":"refusal"},"usage":{"output_tokens":5}}""")
        assertEquals(ClaudeWire.StreamEvent.Stopped("refusal", 5), stop)
        val err = ClaudeWire.decode("""{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""")
        assertEquals(ClaudeWire.StreamEvent.Failed("overloaded_error", "Overloaded"), err)
        val fb = ClaudeWire.decode("""{"type":"content_block_start","index":0,"content_block":{"type":"fallback","from":{"model":"a"},"to":{"model":"b"}}}""")
        assertEquals(ClaudeWire.StreamEvent.Fallback, fb)
        assertEquals(ClaudeWire.StreamEvent.Ignored, ClaudeWire.decode("""{"type":"ping"}"""))
        assertEquals(ClaudeWire.StreamEvent.Ignored, ClaudeWire.decode("not json"))
    }

    @Test fun errorTypeFromBody() {
        assertEquals("authentication_error", ClaudeWire.errorType("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""))
        assertNull(ClaudeWire.errorType("<html>"))
    }

    @Test fun miniJsonHandlesUnicodeEscapesAndNumbers() {
        assertEquals(mapOf("t" to "가é", "n" to -1.5, "i" to 42L, "b" to false, "z" to null, "l" to listOf(1L, 2L)),
            MiniJson.parse("""{"t":"가é","n":-1.5,"i":42,"b":false,"z":null,"l":[1, 2]}"""))
        assertNull(MiniJson.parse("""{"a":1"""))
    }
}
