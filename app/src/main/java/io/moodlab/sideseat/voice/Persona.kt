package io.moodlab.sideseat.voice

/**
 * 캐릭터 이름·호출어를 한 곳에 모은다. 이름을 바꾸려면 여기만 고친다(사용자가 새 이름을 정할 예정).
 * 안드로이드 의존 없음(JVM 테스트 대상). 화면·음성 문구의 호칭도 [NAME]을 쓰면 같이 바뀐다.
 * 새 이름 후보는 [WakeMatcher.score]와 디버그 시험(VoiceModule.debugWakeTrial)으로 STT가 잘 알아듣는지 먼저 재 본다.
 */
object Persona {
    /** 캐릭터 이름(호칭). 화면·음성 문구에서 부르는 이름. */
    const val NAME = "선배"

    /**
     * 호출어와 STT가 흔히 잘못 받아 적는 변형. 맨 앞(앞말 하나까지 허용)에 있으면 바로 호출로 본다.
     * 띄어쓰기·문장부호는 비교 전에 지운다. 이름을 바꾸면 이 목록도 새 이름의 변형으로 바꾼다.
     */
    val WAKE_WORDS: List<String> = listOf(
        "선배", "선배님", "선베", "선베님", "섬배", "선뱌", "썬배", "선바이", "성배",
    )

    /**
     * 일상어와 겹치는 변형(실측 11-13: "선배,"가 "선택"으로 5번, "300"으로 2번 들림). 오인식을 막으려고
     * 발화 맨 앞(앞말 없이)에 있고, 뒤에 질문·요청 꼴의 말이 3글자 이상 이어질 때만 호출로 본다.
     */
    val WEAK_WAKE_WORDS: List<String> = listOf("선택", "300", "삼백", "선백", "섬백")

    /** 호출어 앞에 붙어도 되는 말("저기 선배", "야 선배"). */
    val WAKE_PREFIXES: List<String> = listOf("저기", "야", "어", "음", "헤이", "얘")

    /**
     * Gleo(차량 기본 음성 비서) 호출어 계열. 우리 STT에 이게 잡히면 우리는 물러난다(Gleo 우선).
     * Gleo SDK는 쓰지 않는다(공모전 금지). 이 목록은 우리 STT 결과 문자열 비교에만 쓴다.
     * 실측 11-13: "글레오야"→"클레오", "헤이 글레오"→"헤이 클레어".
     */
    val GLEO_WORDS: List<String> = listOf(
        "글레오", "글래오", "글레호", "글리오", "클레오", "클래오", "그레오", "글레어", "클레어", "글래어", "클래어", "그레어",
        "gleo", "헤이글레오", "하이글레오",
    )

    /** 이 말 뒤에 글레오와 비슷한 두세 글자가 오면 Gleo로 본다("헤이 클레어", "하이 글래오"). */
    val GLEO_CALL_PREFIXES: List<String> = listOf("헤이", "하이", "hey", "hi")
}

/**
 * 호출어 판정기. 낱말 목록을 받아 만들므로 새 이름 후보도 같은 규칙으로 시험할 수 있다. 안드로이드 의존 없음(JVM 테스트 대상).
 */
class WakeMatcher(
    wakeWords: List<String> = Persona.WAKE_WORDS,
    weakWakeWords: List<String> = Persona.WEAK_WAKE_WORDS,
    prefixes: List<String> = Persona.WAKE_PREFIXES,
    gleoWords: List<String> = Persona.GLEO_WORDS,
    gleoCallPrefixes: List<String> = Persona.GLEO_CALL_PREFIXES,
) {
    sealed interface Result {
        /** 호출어가 맨 앞에 있음. [rest]는 호출어 뒤의 말(질문, 없으면 빈 문자열). */
        data class Wake(val rest: String) : Result
        /** Gleo를 부른 말. 우리는 물러난다. */
        object Gleo : Result
        object None : Result
    }

    private val wakeNorm = wakeWords.map(ReplyClassifier::normalize).filter { it.isNotEmpty() }.sortedByDescending { it.length }
    private val weakNorm = weakWakeWords.map(ReplyClassifier::normalize).filter { it.isNotEmpty() }.sortedByDescending { it.length }
    private val prefixNorm = prefixes.map(ReplyClassifier::normalize)
    private val gleoNorm = gleoWords.map(ReplyClassifier::normalize)
    private val gleoCallNorm = gleoCallPrefixes.map(ReplyClassifier::normalize)

    /**
     * 호출어는 발화 맨 앞(앞에 "저기"·"야" 같은 말 하나까지 허용)에 있을 때만 인정한다. 중간에 나온 "선배"는 대화 속 낱말로 본다.
     * 일상어 변형([Persona.WEAK_WAKE_WORDS])은 앞말 없이 맨 앞이고 뒤가 질문·요청 꼴일 때만.
     * Gleo 계열이 맨 앞에 있으면 [Result.Gleo].
     */
    fun detect(text: String): Result {
        val head = ReplyClassifier.normalize(text.take(HEAD_CHARS))
        if (head.isEmpty()) return Result.None
        if (isGleo(head)) return Result.Gleo
        val body = matchWake(head) ?: stripPrefix(head)?.let(::matchWake)
        if (body != null) return Result.Wake(restAfter(text, head.length - body.length))
        val weak = weakNorm.firstOrNull { head.startsWith(it) }
        if (weak != null && boundaryAfter(text, weak.length)) {
            val rest = restAfter(text, weak.length)
            if (ReplyClassifier.normalize(rest).length >= MIN_WEAK_REST && QuestionShape.looksLikeRequest(rest)) return Result.Wake(rest)
        }
        return Result.None
    }

    /** 중간 결과용 가벼운 판정(할당 최소): 맨 앞이 호출어로 시작하는지만. */
    fun startsWithWake(partial: String): Boolean {
        val head = ReplyClassifier.normalize(partial.take(HEAD_CHARS))
        return matchWake(head) != null || stripPrefix(head)?.let(::matchWake) != null
    }

    private fun isGleo(head: String): Boolean {
        if (gleoNorm.any { head.startsWith(it) || stripPrefix(head)?.startsWith(it) == true }) return true
        // "헤이"·"하이" 뒤에 글레오와 비슷한 두 글자(글·클·그·끌 + 레·래·리)가 오면 Gleo로 본다.
        val afterCall = gleoCallNorm.firstOrNull { head.startsWith(it) }?.let { head.substring(it.length) } ?: return false
        return afterCall.length >= 2 && afterCall[0] in GLEO_FIRST && afterCall[1] in GLEO_SECOND
    }

    private fun matchWake(head: String): String? {
        val w = wakeNorm.firstOrNull { head.startsWith(it) } ?: return null
        return head.substring(w.length)
    }

    private fun stripPrefix(head: String): String? =
        prefixNorm.firstOrNull { head.startsWith(it) && head.length > it.length }?.let { head.substring(it.length) }

    /** 원문에서 정규화 글자 [consumedNorm]개 바로 뒤가 띄어쓰기·문장부호인지("300 타이어" O, "300원" X, "선택지" X). */
    private fun boundaryAfter(text: String, consumedNorm: Int): Boolean {
        var seen = 0
        var i = 0
        while (i < text.length && seen < consumedNorm) {
            if (text[i].isLetterOrDigit()) seen++
            i++
        }
        return i < text.length && !text[i].isLetterOrDigit()
    }

    /** 원문에서 정규화 글자 [consumedNorm]개(호출어와 앞말)를 건너뛴 나머지. 원래 띄어쓰기는 살린다. */
    private fun restAfter(text: String, consumedNorm: Int): String {
        var seen = 0
        var i = 0
        while (i < text.length && seen < consumedNorm) {
            if (text[i].isLetterOrDigit()) seen++
            i++
        }
        return text.substring(i).trimStart { !it.isLetterOrDigit() }.trim()
    }

    /** 실측용 채점: (STT가 받아 적은 글, 호출어를 말했는지) 목록으로 인식률·오인식을 낸다. */
    data class Score(val wakeHit: Int, val wakeTotal: Int, val falseAlarm: Int, val plainTotal: Int) {
        override fun toString() = "wake=$wakeHit/$wakeTotal falseAlarm=$falseAlarm/$plainTotal"
    }

    fun score(samples: List<Pair<String, Boolean>>): Score {
        var hit = 0; var wakeTotal = 0; var fa = 0; var plainTotal = 0
        samples.forEach { (text, expectWake) ->
            val woke = detect(text) is Result.Wake
            if (expectWake) { wakeTotal++; if (woke) hit++ } else { plainTotal++; if (woke) fa++ }
        }
        return Score(hit, wakeTotal, fa, plainTotal)
    }

    private companion object {
        const val HEAD_CHARS = 40
        const val MIN_WEAK_REST = 3
        const val GLEO_FIRST = "글클그끌"
        const val GLEO_SECOND = "레래리래례"
    }
}

/** 기본 호출어(Persona)로 만든 판정기. */
object WakeWordDetector {
    private val default = WakeMatcher()
    fun detect(text: String): WakeMatcher.Result = default.detect(text)
    fun startsWithWake(partial: String): Boolean = default.startsWithWake(partial)
}

/**
 * 말이 질문·요청 꼴인지(되묻기 창과 일상어 호출어 변형의 문턱). 안드로이드 의존 없음(JVM 테스트 대상).
 * 물음표, 의문사(어떻게·어디·왜…), 요청·질문 끝말(줘·할까·돼?…)이 있으면 true.
 * "한숨만", "지금 요즘으로", "시원한" 같은 방 소리 조각은 false(실측 11-13 되묻기 창 오인식).
 */
object QuestionShape {
    private val CUES = listOf(
        "어떻게", "어떡", "어디", "어딨", "뭐", "무슨", "무엇", "왜", "언제", "얼마", "몇", "누구",
        "맞지", "맞아", "맞나", "될까", "되나", "돼", "되지", "해야", "할까", "하나", "까요", "나요", "는지",
        "줘", "주세요", "줄래", "알려", "켜", "꺼", "끌까", "끄는", "있어", "있나", "없애", "다시", "떴",
    )

    fun looksLikeRequest(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if ('?' in t || '？' in t) return true
        val n = ReplyClassifier.normalize(t)
        return CUES.any { it in n }
    }
}
