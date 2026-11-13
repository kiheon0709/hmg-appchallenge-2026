package io.moodlab.sideseat.voice

/**
 * 캐릭터 이름·호출어를 한 곳에 모은다. 이름을 바꾸려면 여기만 고친다(11-13 사용자 확정: 타비, 부르는 꼴 "타비야").
 * 안드로이드 의존 없음(JVM 테스트 대상). 화면·음성 문구의 호칭도 [NAME]을 쓰면 같이 바뀐다.
 * 새 이름 후보는 [WakeMatcher.score]와 디버그 시험(VoiceModule.debugWakeTrial)으로 STT가 잘 알아듣는지 먼저 재 본다.
 */
object Persona {
    /** 캐릭터 이름(호칭). 화면·음성 문구에서 부르는 이름. 받침이 없어 "${NAME}가", "${NAME}야"로 붙인다. */
    const val NAME = "타비"

    /** 운전자가 부르는 꼴. */
    const val CALL = "타비야"

    /**
     * 호출어와 STT가 흔히 잘못 받아 적는 변형. 사람 목소리 실측 결과를 보고 여기만 고친다.
     * 발화 맨 앞(앞말 하나까지 허용)에 있고 바로 뒤가 호칭 꼴([VOCATIVE_SUFFIXES])이거나 쉼(문장부호·말 끝)일 때만 깨운다.
     * 합성 음성 실측 11-13(타비 12회): "탑이야" 6, "나비야" 3, "따비야" 2, "아비야" 1, 이름 그대로 0.
     */
    val WAKE_WORDS: List<String> = listOf("타비", "따비", "다비", "타피", "탑이")

    /**
     * 일상어·노랫말과 겹치는 변형("나비야 나비야", "아비", "차비"). 기본은 받지 않는다([WakeConfig.allowWeakWakeWords]).
     * 켜면 앞말 없이 맨 앞 + 호칭 꼴 + 뒤에 질문·요청 꼴이 3글자 이상 이어질 때만 깨운다.
     */
    val WEAK_WAKE_WORDS: List<String> = listOf("나비", "아비", "차비")

    /** 이름 바로 뒤(띄어쓰기 없이)에 붙는 호칭 꼴. 긴 것부터 본다. */
    val VOCATIVE_SUFFIXES: List<String> = listOf("이야", "야", "아")

    /** 호출어 앞에 붙어도 되는 말("저기 타비야", "야 타비야"). */
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
 *
 * 호칭 꼴 규칙: 이름이 발화 맨 앞(앞말 하나까지)에 있고, 바로 뒤에 "야·아·이야"가 띄어쓰기 없이 붙은 뒤 말이 끊기거나,
 * 이름 바로 뒤가 쉼(문장부호·말 끝)일 때만 깨운다. 11-13 이름 후보 50개 실측에서 일반 문장 80개 오인식이 모든 후보에서 0이 된 규칙.
 */
class WakeMatcher(
    wakeWords: List<String> = Persona.WAKE_WORDS,
    weakWakeWords: List<String> = Persona.WEAK_WAKE_WORDS,
    prefixes: List<String> = Persona.WAKE_PREFIXES,
    gleoWords: List<String> = Persona.GLEO_WORDS,
    gleoCallPrefixes: List<String> = Persona.GLEO_CALL_PREFIXES,
    vocativeSuffixes: List<String> = Persona.VOCATIVE_SUFFIXES,
    /** 일상어 변형([Persona.WEAK_WAKE_WORDS])을 좁은 규칙으로 받을지. */
    private val allowWeak: Boolean = false,
) {
    sealed interface Result {
        /** 호출어가 맨 앞에 있음. [rest]는 호출어 뒤의 말(질문, 없으면 빈 문자열). */
        data class Wake(val rest: String) : Result
        /** Gleo를 부른 말. 우리는 물러난다. */
        object Gleo : Result
        object None : Result
    }

    private val wakeNorm = norm(wakeWords)
    private val weakNorm = norm(weakWakeWords)
    private val suffixNorm = norm(vocativeSuffixes)
    private val prefixNorm = prefixes.map(ReplyClassifier::normalize)
    private val gleoNorm = gleoWords.map(ReplyClassifier::normalize)
    private val gleoCallNorm = gleoCallPrefixes.map(ReplyClassifier::normalize)

    fun detect(text: String): Result {
        val head = Head(text.take(HEAD_CHARS))
        if (head.s.isEmpty()) return Result.None
        if (isGleo(head.s)) return Result.Gleo
        val starts = listOf(0) + prefixNorm.filter { head.s.startsWith(it) && head.s.length > it.length }.map { it.length }
        for (start in starts) {
            val consumed = matchCall(head, start, wakeNorm, allowPause = true) ?: continue
            return Result.Wake(restAfter(text, consumed))
        }
        if (allowWeak) {
            val consumed = matchCall(head, 0, weakNorm, allowPause = false)
            if (consumed != null) {
                val rest = restAfter(text, consumed)
                if (ReplyClassifier.normalize(rest).length >= MIN_WEAK_REST && QuestionShape.looksLikeRequest(rest)) return Result.Wake(rest)
            }
        }
        return Result.None
    }

    /** 중간 결과용 판정: 맨 앞이 호출어(호칭 꼴)로 시작하는지만. */
    fun startsWithWake(partial: String): Boolean = detect(partial) is Result.Wake

    /** 정규화한 글자와 각 글자의 원문 위치. 호칭 꼴이 띄어쓰기 없이 붙었는지 원문으로 확인한다. */
    private class Head(val raw: String) {
        val s: String
        val pos: IntArray
        init {
            val b = StringBuilder()
            val p = ArrayList<Int>()
            raw.lowercase().forEachIndexed { i, c -> if (c.isLetterOrDigit()) { b.append(c); p += i } }
            s = b.toString()
            pos = p.toIntArray()
        }
    }

    /** [start]에서 이름 + 호칭 꼴(또는 쉼)이면 쓴 정규화 글자 수, 아니면 null. */
    private fun matchCall(h: Head, start: Int, words: List<String>, allowPause: Boolean): Int? {
        for (w in words) {
            if (!h.s.startsWith(w, start)) continue
            val end = start + w.length
            val nameEnd = h.pos[end - 1]
            for (suf in suffixNorm) {
                if (!h.s.startsWith(suf, end)) continue
                val last = end + suf.length - 1
                val attached = h.pos[last] - nameEnd == suf.length // 이름과 호칭 사이에 띄어쓰기·부호가 없음
                val cutAfter = last + 1 >= h.s.length || h.pos[last + 1] > h.pos[last] + 1 // 호칭 뒤에서 말이 끊김
                if (attached && cutAfter) return end + suf.length
            }
            if (allowPause && pauseAfter(h.raw, nameEnd + 1)) return end
        }
        return null
    }

    /** 원문 [i]부터가 쉼인지: 말 끝이거나 문장부호("타비, 와이퍼" O, "타비 와이퍼" X, "타비가" X). */
    private fun pauseAfter(raw: String, i: Int): Boolean {
        if (i >= raw.length) return true
        val c = raw[i]
        if (c.isWhitespace()) return raw.substring(i).all { !it.isLetterOrDigit() }
        return !c.isLetterOrDigit()
    }

    private fun isGleo(head: String): Boolean {
        if (gleoNorm.any { head.startsWith(it) || stripPrefix(head)?.startsWith(it) == true }) return true
        // "헤이"·"하이" 뒤에 글레오와 비슷한 두 글자(글·클·그·끌 + 레·래·리)가 오면 Gleo로 본다.
        val afterCall = gleoCallNorm.firstOrNull { head.startsWith(it) }?.let { head.substring(it.length) } ?: return false
        return afterCall.length >= 2 && afterCall[0] in GLEO_FIRST && afterCall[1] in GLEO_SECOND
    }

    private fun stripPrefix(head: String): String? =
        prefixNorm.firstOrNull { head.startsWith(it) && head.length > it.length }?.let { head.substring(it.length) }

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
        fun norm(words: List<String>) = words.map(ReplyClassifier::normalize).filter { it.isNotEmpty() }.sortedByDescending { it.length }
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
