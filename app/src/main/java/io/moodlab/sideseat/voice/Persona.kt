package io.moodlab.sideseat.voice

/**
 * 캐릭터 이름·호출어를 한 곳에 모은다. 이름을 바꾸려면 여기만 고친다(사용자가 새 이름을 정할 예정).
 * 안드로이드 의존 없음(JVM 테스트 대상). 화면·음성 문구의 호칭도 [NAME]을 쓰면 같이 바뀐다.
 */
object Persona {
    /** 캐릭터 이름(호칭). 화면·음성 문구에서 부르는 이름. */
    const val NAME = "선배"

    /**
     * 호출어와 STT가 흔히 잘못 받아 적는 변형(띄어쓰기·문장부호는 비교 전에 지운다).
     * 이름을 바꾸면 이 목록도 새 이름의 변형으로 바꾼다.
     */
    val WAKE_WORDS: List<String> = listOf(
        "선배", "선배님", "선베", "선베님", "섬배", "선뱌", "썬배", "선바이", "성배",
    )

    /** 호출어 앞에 붙어도 되는 말("저기 선배", "야 선배"). */
    val WAKE_PREFIXES: List<String> = listOf("저기", "야", "어", "음", "헤이", "얘")

    /**
     * Gleo(차량 기본 음성 비서) 호출어 계열. 우리 STT에 이게 잡히면 우리는 물러난다(Gleo 우선).
     * Gleo SDK는 쓰지 않는다(공모전 금지). 이 목록은 우리 STT 결과 문자열 비교에만 쓴다.
     */
    val GLEO_WORDS: List<String> = listOf(
        "글레오", "글래오", "글레호", "글리오", "클레오", "그레오", "글레어", "gleo", "헤이글레오", "하이글레오",
    )
}

/** 호출어 판정. 안드로이드 의존 없음(JVM 테스트 대상). */
object WakeWordDetector {

    sealed interface Result {
        /** 호출어가 맨 앞에 있음. [rest]는 호출어 뒤의 말(질문, 없으면 빈 문자열). */
        data class Wake(val rest: String) : Result
        /** Gleo를 부른 말. 우리는 물러난다. */
        object Gleo : Result
        object None : Result
    }

    private val wakeNorm = Persona.WAKE_WORDS.map(ReplyClassifier::normalize).sortedByDescending { it.length }
    private val prefixNorm = Persona.WAKE_PREFIXES.map(ReplyClassifier::normalize)
    private val gleoNorm = Persona.GLEO_WORDS.map(ReplyClassifier::normalize)

    /**
     * 호출어는 발화 맨 앞(앞에 "저기"·"야" 같은 말 하나까지 허용)에 있을 때만 인정한다. 중간에 나온 "선배"는 대화 속 낱말로 본다.
     * Gleo 계열이 맨 앞에 있으면 [Result.Gleo].
     */
    fun detect(text: String): Result {
        val head = ReplyClassifier.normalize(text.take(HEAD_CHARS))
        if (head.isEmpty()) return Result.None
        if (gleoNorm.any { head.startsWith(it) || stripPrefix(head)?.startsWith(it) == true }) return Result.Gleo
        val body = matchWake(head) ?: stripPrefix(head)?.let(::matchWake) ?: return Result.None
        return Result.Wake(restAfter(text, head.length - body.length))
    }

    /** 중간 결과용 가벼운 판정(할당 최소): 맨 앞이 호출어로 시작하는지만. */
    fun startsWithWake(partial: String): Boolean {
        val head = ReplyClassifier.normalize(partial.take(HEAD_CHARS))
        return matchWake(head) != null || stripPrefix(head)?.let(::matchWake) != null
    }

    /** 호출어를 떼고 남은 정규화 문자열. 호출어가 아니면 null. */
    private fun matchWake(head: String): String? {
        val w = wakeNorm.firstOrNull { head.startsWith(it) } ?: return null
        return head.substring(w.length)
    }

    private fun stripPrefix(head: String): String? =
        prefixNorm.firstOrNull { head.startsWith(it) && head.length > it.length }?.let { head.substring(it.length) }

    /** 원문에서 정규화 글자 [consumed]개(호출어와 앞말)를 건너뛴 나머지. 원래 띄어쓰기는 살린다. */
    private fun restAfter(text: String, consumedNorm: Int): String {
        var seen = 0
        var i = 0
        while (i < text.length && seen < consumedNorm) {
            if (text[i].isLetterOrDigit()) seen++
            i++
        }
        return text.substring(i).trimStart { !it.isLetterOrDigit() }.trim()
    }

    private const val HEAD_CHARS = 40
}
