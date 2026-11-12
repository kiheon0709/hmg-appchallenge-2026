package io.moodlab.sideseat.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

/**
 * LLM이 조금씩 흘려보내는 글자를 TTS에 넘길 문장으로 끊는다. 안드로이드 의존 없음(JVM 테스트 대상).
 *
 * - 끝맺음(. ? ! … 줄바꿈 등) 뒤에서 끊는다. "3.8초"처럼 숫자 사이 점은 끊지 않는다.
 * - 끝맺음 없이 [maxChars]를 넘으면 마지막 쉼표나 띄어쓰기에서 끊는다(첫 음성을 늦추지 않으려고).
 * - TTS가 그대로 읽어 버리는 마크다운 기호(*, #, `, 목록 머리표)는 지운다.
 */
class SentenceChunker(
    private val maxChars: Int = 90,
    /** 첫 조각만 첫 쉼표에서 끊는다(쉼표 앞이 [MIN_FIRST_CHARS]자 이상일 때). 첫 음성을 문장 끝까지 기다리지 않으려고. */
    private val firstPieceAtComma: Boolean = false,
) {
    private val buf = StringBuilder()
    private var emittedAny = false

    /** 조각을 넣고, 완성된 문장들을 돌려준다. */
    fun push(delta: String): List<String> {
        buf.append(delta)
        val out = mutableListOf<String>()
        while (true) {
            val cut = findCut() ?: break
            emit(buf.substring(0, cut), out)
            buf.delete(0, cut)
        }
        return out
    }

    /** 흐름이 끝났을 때 남은 글자를 마지막 문장으로 돌려준다. */
    fun flush(): List<String> {
        val out = mutableListOf<String>()
        emit(buf.toString(), out)
        buf.setLength(0)
        return out
    }

    private fun findCut(): Int? {
        if (firstPieceAtComma && !emittedAny) {
            val comma = buf.indexOfFirst { it == ',' || it == '，' }
            val stop = buf.indexOfFirst { it in TERMINATORS || it == '\n' }
            if (comma >= 0 && (stop < 0 || comma < stop) && buf.substring(0, comma).count { it.isLetterOrDigit() } >= MIN_FIRST_CHARS) {
                return comma + 1
            }
        }
        for (i in buf.indices) {
            val c = buf[i]
            if (c == '\n') return i + 1
            if (c !in TERMINATORS) continue
            // 숫자 사이의 점(3.8)은 문장 끝이 아니다. 다음 글자가 아직 안 왔으면 기다린다.
            if (c == '.' && i > 0 && buf[i - 1].isDigit()) {
                if (i + 1 >= buf.length) return null
                if (buf[i + 1].isDigit()) continue
            }
            // 끝맺음이 이어지면(?! ... "요." 뒤 닫는 따옴표) 모두 붙여서 끊는다.
            var end = i + 1
            while (end < buf.length && (buf[end] in TERMINATORS || buf[end] in CLOSERS)) end++
            if (end >= buf.length) {
                // 끝맺음이 버퍼 끝에 있으면 다음 조각에 이어질 기호(…, ?!)가 올 수 있다.
                // 한국어 문장은 "요." "다." 뒤에 이어지는 일이 거의 없어 바로 끊는다.
                return end
            }
            if (buf[end].isWhitespace()) return end + 1
            // "예: 3.5" 같은 경우를 빼면 끝맺음 바로 뒤에 글자가 붙는 일은 드물다. 그래도 끊는다.
            return end
        }
        if (buf.length > maxChars) {
            val soft = buf.lastIndexOfAny(charArrayOf(',', '，', ' '), startIndex = maxChars)
            return if (soft > maxChars / 3) soft + 1 else maxChars
        }
        return null
    }

    private fun emit(raw: String, out: MutableList<String>) {
        val cleaned = clean(raw)
        if (cleaned.any { it.isLetterOrDigit() }) {
            out += cleaned
            emittedAny = true
        }
    }

    companion object {
        private const val MIN_FIRST_CHARS = 6
        private val TERMINATORS = setOf('.', '?', '!', '…', '。', '？', '！')
        private val CLOSERS = setOf('"', '\'', '”', '’', ')', '」', '』')
        private val LIST_MARK = Regex("""^\s*(?:[-*•·]|\d+[.)])\s+""")
        private val MARKDOWN = Regex("""[*#`_>|]""")
        private val SPACES = Regex("""\s+""")

        fun clean(raw: String): String =
            raw.lines().joinToString(" ") { it.replace(LIST_MARK, "") }
                .replace(MARKDOWN, "")
                .replace(SPACES, " ")
                .trim()
    }
}

/** 글자 조각 흐름 → 문장 흐름. */
fun Flow<String>.toSentences(maxChars: Int = 90, firstPieceAtComma: Boolean = false): Flow<String> = flow {
    val chunker = SentenceChunker(maxChars, firstPieceAtComma)
    collect { delta -> chunker.push(delta).forEach { emit(it) } }
    chunker.flush().forEach { emit(it) }
}

/** 문장 [n]개까지 흘려보낸다. 쉼표로 끝나는 조각(첫 쉼표 조각)은 문장으로 세지 않는다. */
fun Flow<String>.takeSentences(n: Int): Flow<String> = flow {
    var count = 0 // 모을 때마다 새로 센다
    emitAll(
        this@takeSentences.transformWhile { piece ->
            emit(piece)
            if (!piece.endsWith(",") && !piece.endsWith("，")) count++
            count < n
        },
    )
}
