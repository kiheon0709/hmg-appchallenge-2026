package io.moodlab.sideseat.core

/**
 * 말(TTS)로 내보낼 문장의 숫자를 읽는 법에 맞게 한글로 바꾼다(v28). 화면 글자는 바꾸지 않는다.
 * Pleos TTS가 "1분"을 "한 분"으로 읽어서, 시간 단위(분·초)는 한자어 수("일 분", "사 점 영 초"),
 * 횟수·개수(번·개)는 고유어 수("한 번", "세 개")로 바꾼다. 퀴즈 보기 번호("1번,")는 한자어("일 번,").
 */
object SpeechText {
    private val SINO = arrayOf("영", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구")
    private val NATIVE = arrayOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")

    private val TIME = Regex("""(\d+)(?:\.(\d+))?\s?(분|초)""")
    private val OPTION_NO = Regex("""(\d+)번(?=,)""")
    private val COUNT = Regex("""(\d+)\s?(번|개)""")

    fun forTts(text: String): String {
        var s = TIME.replace(text) { m ->
            val whole = sino(m.groupValues[1].toLong())
            val frac = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { f -> " 점 " + f.map { SINO[it - '0'] }.joinToString("") }.orEmpty()
            "$whole$frac ${m.groupValues[3]}"
        }
        s = OPTION_NO.replace(s) { m -> "${sino(m.groupValues[1].toLong())} 번" }
        s = COUNT.replace(s) { m ->
            val n = m.groupValues[1].toInt()
            if (n in 1..10) "${NATIVE[n]} ${m.groupValues[2]}" else "${sino(n.toLong())} ${m.groupValues[2]}"
        }
        return s
    }

    /** 0~9999를 한자어 수로("십오", "이백삼십"). 더 큰 수는 숫자 그대로 둔다. */
    fun sino(n: Long): String {
        if (n == 0L) return SINO[0]
        if (n >= 10_000) return n.toString()
        val units = listOf(1000L to "천", 100L to "백", 10L to "십")
        val sb = StringBuilder()
        var rest = n
        for ((u, name) in units) {
            val d = (rest / u).toInt()
            if (d > 0) {
                if (d > 1) sb.append(SINO[d])
                sb.append(name)
            }
            rest %= u
        }
        if (rest > 0) sb.append(SINO[rest.toInt()])
        return sb.toString()
    }
}
