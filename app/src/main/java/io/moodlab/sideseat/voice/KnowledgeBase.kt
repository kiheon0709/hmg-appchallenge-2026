package io.moodlab.sideseat.voice

/**
 * `assets/knowledge` 폴더의 .md 파일에서 읽은 대처 절차. 안드로이드 의존 없음(JVM 테스트 대상).
 *
 * 파일 형식(항목마다):
 * ```
 * ## defog_windshield 앞유리 김서림
 * 질문: 김서림, 뿌예, 습기, 앞유리 흐려
 * 짧게: 주행 중에 말할 1~2문장.
 * 자세히: 정차 중에 말할 3~4문장.
 * 출처: 기관, 문서 이름, URL
 * ```
 * 같은 파일 전체가 LLM 시스템 프롬프트의 "지식"으로도 들어간다. LLM은 이 안에서만 답한다.
 */
class KnowledgeBase(val entries: List<Entry>, val rawText: String) {

    data class Entry(
        val id: String,
        val title: String,
        val keywords: List<String>,
        val short: String,
        val detail: String,
        val source: String,
    ) {
        internal val normalizedKeywords = keywords.map(ReplyClassifier::normalize).filter { it.length >= 2 }
    }

    data class Match(val entry: Entry, val score: Int)

    /**
     * 질문에 맞는 항목을 찾는다. 질문에 들어 있는 키워드 길이의 합이 점수다.
     * 1등과 2등이 같으면 애매하므로 null(LLM이나 "모르겠어요"로 넘긴다).
     */
    fun match(question: String): Match? {
        val q = ReplyClassifier.normalize(question)
        if (q.isEmpty()) return null
        val scored = entries.map { e -> Match(e, e.normalizedKeywords.filter { it in q }.sumOf { it.length }) }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
        val best = scored.firstOrNull() ?: return null
        if (scored.size > 1 && scored[1].score == best.score) return null
        return best
    }

    companion object {
        val EMPTY = KnowledgeBase(emptyList(), "")

        /** 여러 파일 내용을 받아 합친다. */
        fun parse(files: List<String>): KnowledgeBase {
            val entries = files.flatMap(::parseFile)
            return KnowledgeBase(entries, files.joinToString("\n\n") { it.trim() })
        }

        private fun parseFile(text: String): List<Entry> {
            val out = mutableListOf<Entry>()
            var head: String? = null
            val fields = linkedMapOf<String, String>()
            var lastKey: String? = null

            fun close() {
                val h = head ?: return
                val id = h.substringBefore(' ').trim()
                val title = h.substringAfter(' ', "").trim().ifEmpty { id }
                val short = fields["짧게"].orEmpty()
                if (id.isNotEmpty() && short.isNotEmpty()) {
                    out += Entry(
                        id = id,
                        title = title,
                        keywords = fields["질문"].orEmpty().split(',', '，', '/').map { it.trim() }.filter { it.isNotEmpty() },
                        short = short,
                        detail = fields["자세히"].orEmpty().ifEmpty { short },
                        source = fields["출처"].orEmpty(),
                    )
                }
                head = null
                fields.clear()
                lastKey = null
            }

            for (line in text.lines()) {
                when {
                    line.startsWith("## ") -> { close(); head = line.removePrefix("## ").trim() }
                    line.startsWith("#") -> close()
                    head == null -> Unit
                    else -> {
                        val key = FIELD_KEYS.firstOrNull { line.trimStart().startsWith("$it:") }
                        if (key != null) {
                            fields[key] = line.trimStart().removePrefix("$key:").trim()
                            lastKey = key
                        } else if (line.isNotBlank() && lastKey != null) {
                            // 여러 줄에 걸친 값은 이어 붙인다.
                            fields[lastKey!!] = (fields[lastKey!!].orEmpty() + " " + line.trim()).trim()
                        }
                    }
                }
            }
            close()
            return out
        }

        private val FIELD_KEYS = listOf("질문", "짧게", "자세히", "출처")
    }
}
