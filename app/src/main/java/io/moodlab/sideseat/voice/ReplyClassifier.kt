package io.moodlab.sideseat.voice

/** 짧은 대답의 뜻. */
enum class Reply { YES, NO, LATER, REPEAT, UNKNOWN }

/**
 * "응/아니/나중에" 같은 짧은 대답을 규칙으로 바로 가른다. STT 중간 결과에도 돌릴 수 있게 가볍게 만든다.
 * 안드로이드 의존 없음(JVM 테스트 대상). 통합 세션이 [io.moodlab.sideseat.core.ReplyKind.YES_NO] 대답을 해석할 때도 쓴다.
 *
 * 우선순위: 다시(REPEAT) > 나중에(LATER) > 부정(NO) > 긍정(YES).
 * "안 갈래", "괜찮아(=사양)", "됐어"처럼 긍정 낱말이 섞인 부정을 먼저 잡는다.
 */
object ReplyClassifier {
    private val REPEAT = listOf("다시말", "다시한번", "뭐라고", "못들었", "한번더말", "잘안들")
    private val LATER = listOf("나중에", "이따", "다음에", "좀있다", "조금있다", "잠깐만", "있다가")
    private val NO = listOf(
        "아니", "아뇨", "아닌", "싫어", "싫은", "안돼", "안되", "안할", "안갈", "안가", "안쉬", "안쉴", "괜찮아", "괜찮습",
        "됐어", "됐습", "필요없", "필요업", "말자", "말래", "그만", "노노", "놉", "패스", "no", "nope",
    )
    private val YES = listOf(
        "응", "엉", "어어", "웅", "네", "예", "넵", "녜", "그래", "그러자", "그럽시다", "좋아", "좋지", "좋습",
        "콜", "오케이", "오키", "ok", "okay", "yes", "맞아", "맞습", "가자", "갈래", "해줘", "해주세요", "해요",
        "부탁", "쉬자", "쉬어", "쉴래", "알았어", "알겠", "당연",
    )

    /** 짧은 대답을 가른다. 대답이 아니라 질문·긴 문장이면 UNKNOWN. */
    fun classify(text: String): Reply {
        val t = normalize(text)
        if (t.isEmpty()) return Reply.UNKNOWN
        if (REPEAT.any { it in t }) return Reply.REPEAT
        // 긴 문장은 대답이 아니라 질문일 가능성이 높다("아니 근데 안개등은 어떻게 켜?").
        if (t.length > MAX_REPLY_CHARS) return Reply.UNKNOWN
        if (LATER.any { it in t }) return Reply.LATER
        if (NO.any { it in t }) return Reply.NO
        // "어"·"네"는 한 글자라 다른 낱말 속에 잘 숨는다. 한 글자 낱말은 맨 앞에 올 때만 인정한다.
        if (YES.any { (it.length >= 2 && it in t) || t.startsWith(it) }) return Reply.YES
        if (t == "어") return Reply.YES
        return Reply.UNKNOWN
    }

    /**
     * 중간 결과가 끝난 문장처럼 보이는지(말 끝 추측용). 물음표·마침표로 끝나거나, 마지막 글자가 한국어 문장 끝 어미이면 true.
     * "너의 의기로 바꿔야 해" → true, "너의 의기로" → false. 틀려도 추측 요청이 하나 버려질 뿐이다.
     */
    fun looksComplete(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.last() in "?.!？。！") return true
        val last = t.lastOrNull { it.isLetterOrDigit() } ?: return false
        return last in SENTENCE_ENDINGS
    }

    /** 반말·해요체 질문과 서술의 끝 글자. */
    private const val SENTENCE_ENDINGS = "요까해어야지니나냐래데죠줘돼게다라봐워와랴쥬예얘려"

    /** 띄어쓰기·문장부호를 없애고 소문자로. 비교용. */
    fun normalize(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }

    private const val MAX_REPLY_CHARS = 12
}
