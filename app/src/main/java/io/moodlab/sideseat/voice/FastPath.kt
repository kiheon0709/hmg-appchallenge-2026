package io.moodlab.sideseat.voice

/**
 * 빠른 길: LLM 없이 규칙으로 바로 답한다. 안드로이드 의존 없음(JVM 테스트 대상).
 *
 * 1. 짧은 대답(응/아니/나중에/다시) → 정해진 응답.
 * 2. 지식 파일에 맞는 항목이 있는 질문 → 주행 중이면 "짧게", 정차 중이면 "자세히".
 *    정차 중이고 LLM을 쓸 수 있으면 긴 질문은 LLM에게 넘긴다(더 자연스럽게 풀어 말하도록).
 * 3. 나머지 → null(느린 길). LLM이 없으면 [unknownAnswer].
 */
class FastPath(private val knowledge: KnowledgeBase) {

    sealed interface Decision {
        val sentences: List<String>
        data class ShortReply(val reply: Reply, override val sentences: List<String>) : Decision
        data class Known(val entryId: String, override val sentences: List<String>) : Decision
    }

    fun decide(question: String, parked: Boolean, llmAvailable: Boolean): Decision? {
        val reply = ReplyClassifier.classify(question)
        if (reply != Reply.UNKNOWN) return Decision.ShortReply(reply, listOf(replyLine(reply)))
        val match = knowledge.match(question) ?: return null
        val long = ReplyClassifier.normalize(question).length > SHORT_QUESTION_CHARS
        if (parked && llmAvailable && long) return null
        val text = if (parked) match.entry.detail else match.entry.short
        return Decision.Known(match.entry.id, splitSentences(text))
    }

    /** 느린 길도 못 쓸 때 지식 파일 안에서라도 답한다. 없으면 모른다고 한다. */
    fun fallback(question: String, parked: Boolean): List<String> {
        val match = knowledge.match(question) ?: return splitSentences(UNKNOWN_ANSWER)
        return splitSentences(if (parked) match.entry.detail else match.entry.short)
    }

    companion object {
        const val UNKNOWN_ANSWER = "그건 제가 정확히 몰라요. 취급설명서를 같이 볼까요?"
        private const val SHORT_QUESTION_CHARS = 12

        fun replyLine(reply: Reply): String = when (reply) {
            Reply.YES -> "좋아요."
            Reply.NO -> "알겠어요."
            Reply.LATER -> "네, 나중에 다시 말씀드릴게요."
            Reply.REPEAT -> "다시 말씀드릴게요."
            Reply.UNKNOWN -> UNKNOWN_ANSWER
        }

        fun splitSentences(text: String): List<String> {
            val c = SentenceChunker(maxChars = 200)
            return c.push(text) + c.flush()
        }
    }
}
