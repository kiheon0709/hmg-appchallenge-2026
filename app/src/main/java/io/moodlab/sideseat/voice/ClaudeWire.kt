package io.moodlab.sideseat.voice

/**
 * Claude Messages API의 요청 본문 만들기와 스트리밍(SSE) 응답 해석. 순수 Kotlin(JVM 테스트 대상).
 *
 * 공식 Java SDK 대신 직접 부르는 이유는 docs/plan/decisions.md(dev-voice-ai) 참고.
 * 요청 형태는 claude-api 스킬의 cURL 문서(anthropic-version 2026-10-21, stream, system 블록 cache_control,
 * output_config.effort, server-side fallbacks)를 따른다.
 */
object ClaudeWire {
    const val URL = "https://api.anthropic.com/v1/messages"
    const val API_VERSION = "2026-10-21"
    const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
    /** 중계 서버(server/)가 확인하는 앱 식별 헤더. 비밀이 아니다. */
    const val RELAY_APP_HEADER = "x-sideseat-app"
    const val RELAY_APP_ID = "io.moodlab.sideseat"

    /**
     * 모델별 생각(thinking) 설정. 음성 응답은 첫 문장까지의 시간이 중요하다.
     * - Haiku 5.5: low 이하에서 끌 수 있다 → 끈다.
     * - Sonnet 5.5: disabled는 400, 끄려면 between_tools.
     * - Opus 5.5 등: 끌 수 없다(400) → 생략(adaptive)하고 effort를 낮춘다.
     */
    fun thinkingFor(model: String): String? = when {
        model.startsWith("claude-haiku-5") -> """{"type":"disabled"}"""
        model.startsWith("claude-sonnet-5-5") -> """{"type":"between_tools"}"""
        else -> null
    }

    /** 서버 쪽 거절 대비(fallbacks: "default"). Haiku 5.5는 서버 쪽 대비가 없다. */
    fun supportsServerFallback(model: String): Boolean =
        listOf("claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5", "claude-fable-5-2", "claude-fable-5-1", "claude-fable-5")
            .any { model == it }

    fun body(model: String, system: String, user: String, maxTokens: Long, stream: Boolean, effort: String = "low"): String = buildString {
        append('{')
        append("\"model\":").append(quote(model))
        append(",\"max_tokens\":").append(maxTokens)
        if (stream) append(",\"stream\":true")
        // 시스템 프롬프트는 요청마다 같으므로 그 끝에 캐시 지점을 둔다. 바뀌는 질문은 messages에.
        append(",\"system\":[{\"type\":\"text\",\"text\":").append(quote(system))
        append(",\"cache_control\":{\"type\":\"ephemeral\"}}]")
        append(",\"messages\":[{\"role\":\"user\",\"content\":").append(quote(user)).append("}]")
        thinkingFor(model)?.let { append(",\"thinking\":").append(it) }
        append(",\"output_config\":{\"effort\":").append(quote(effort)).append('}')
        if (supportsServerFallback(model)) append(",\"fallbacks\":\"default\"")
        append('}')
    }

    fun quote(s: String): String {
        val sb = StringBuilder(s.length + 16).append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    // ---------------------------------------------------------------- SSE

    /** SSE 줄을 받아 (event, data) 묶음으로 만든다. 빈 줄이 묶음의 끝. */
    class SseParser {
        private var event: String? = null
        private val data = StringBuilder()

        /** 한 줄(줄바꿈 제외)을 넣는다. 묶음이 끝나면 돌려준다. */
        fun line(line: String): Pair<String?, String>? {
            if (line.isEmpty()) {
                if (data.isEmpty() && event == null) return null
                val out = event to data.toString()
                event = null
                data.setLength(0)
                return out
            }
            if (line.startsWith(":")) return null
            val field = line.substringBefore(':')
            var value = line.substringAfter(':', "")
            if (value.startsWith(" ")) value = value.substring(1)
            when (field) {
                "event" -> event = value
                "data" -> { if (data.isNotEmpty()) data.append('\n'); data.append(value) }
            }
            return null
        }
    }

    sealed interface StreamEvent {
        data class Text(val text: String) : StreamEvent
        data class Started(val model: String?, val cacheRead: Long?, val cacheWrite: Long?, val inputTokens: Long?) : StreamEvent
        data class Stopped(val stopReason: String?, val outputTokens: Long?) : StreamEvent
        data class Failed(val type: String?, val message: String?) : StreamEvent
        object Fallback : StreamEvent
        object Ignored : StreamEvent
    }

    /** SSE 묶음 하나를 해석한다. 생각 블록·핑 등은 Ignored. */
    @Suppress("UNCHECKED_CAST")
    fun decode(data: String): StreamEvent {
        val obj = MiniJson.parse(data) as? Map<String, Any?> ?: return StreamEvent.Ignored
        return when (obj["type"]) {
            "content_block_delta" -> {
                val delta = obj["delta"] as? Map<String, Any?>
                if (delta?.get("type") == "text_delta") StreamEvent.Text(delta["text"] as? String ?: "") else StreamEvent.Ignored
            }
            "content_block_start" -> {
                val block = obj["content_block"] as? Map<String, Any?>
                when (block?.get("type")) {
                    "fallback" -> StreamEvent.Fallback
                    "text" -> (block["text"] as? String).let { if (it.isNullOrEmpty()) StreamEvent.Ignored else StreamEvent.Text(it) }
                    else -> StreamEvent.Ignored
                }
            }
            "message_start" -> {
                val msg = obj["message"] as? Map<String, Any?>
                val usage = msg?.get("usage") as? Map<String, Any?>
                StreamEvent.Started(
                    model = msg?.get("model") as? String,
                    cacheRead = (usage?.get("cache_read_input_tokens") as? Number)?.toLong(),
                    cacheWrite = (usage?.get("cache_creation_input_tokens") as? Number)?.toLong(),
                    inputTokens = (usage?.get("input_tokens") as? Number)?.toLong(),
                )
            }
            "message_delta" -> {
                val delta = obj["delta"] as? Map<String, Any?>
                val usage = obj["usage"] as? Map<String, Any?>
                StreamEvent.Stopped(delta?.get("stop_reason") as? String, (usage?.get("output_tokens") as? Number)?.toLong())
            }
            "error" -> {
                val err = obj["error"] as? Map<String, Any?>
                StreamEvent.Failed(err?.get("type") as? String, err?.get("message") as? String)
            }
            else -> StreamEvent.Ignored
        }
    }

    /** 오류 응답 본문에서 error.type만 꺼낸다(로그용, 키나 본문 전체를 남기지 않는다). */
    @Suppress("UNCHECKED_CAST")
    fun errorType(body: String): String? =
        ((MiniJson.parse(body) as? Map<String, Any?>)?.get("error") as? Map<String, Any?>)?.get("type") as? String
}

/** 응답 해석용 작은 JSON 파서. Map/List/String/Double·Long/Boolean/null을 돌려준다. 잘못된 입력이면 null. */
object MiniJson {
    fun parse(s: String): Any? = runCatching { Reader(s).run { val v = value(); ws(); if (i != s.length) null else v } }.getOrNull()

    private class Reader(val s: String) {
        var i = 0

        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun value(): Any? {
            ws()
            require(i < s.length)
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("unexpected $c")
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            require(s.startsWith(word, i)); i += word.length; return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++; ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws(); val k = str(); ws(); require(s[i] == ':'); i++
                m[k] = value(); ws()
                when (s[i]) { ',' -> i++; '}' -> { i++; return m }; else -> throw IllegalArgumentException() }
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++; ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value(); ws()
                when (s[i]) { ',' -> i++; ']' -> { i++; return l }; else -> throw IllegalArgumentException() }
            }
        }

        private fun str(): String {
            require(s[i] == '"'); i++
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> throw IllegalArgumentException("bad escape $e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Number {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(start, i)
            return if (t.any { it in ".eE" }) t.toDouble() else t.toLong()
        }
    }
}
