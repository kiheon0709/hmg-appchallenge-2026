package io.moodlab.moodrive.core

/** STT 결과 텍스트에서 뽑는 의도. 운전 중 짧게 말하는 것을 전제로 키워드 기반으로 판정한다. */
enum class VoiceIntent { LIKE, DISLIKE, SKIP, MORE_ENERGY, CALMER, REST_STOP, MUTE_DJ, UNMUTE_DJ, WHAT_SONG, HOLD, YES, NO, UNKNOWN }

object VoiceCommandParser {
    private fun String.norm() = lowercase().replace(Regex("[\\s.,!?~]+"), "")

    // 순서가 곧 우선순위. 부정·복합 표현을 먼저 본다.
    private val rules: List<Pair<VoiceIntent, List<String>>> = listOf(
        VoiceIntent.WHAT_SONG to listOf("무슨노래", "이노래뭐", "노래뭔지", "이곡뭐", "곡뭔지", "제목뭐", "제목알려", "누구노래"),
        VoiceIntent.HOLD to listOf("이분위기유지", "분위기유지", "그대로둬", "바꾸지마", "계속이거"),
        VoiceIntent.UNMUTE_DJ to listOf("다시말해", "말해줘", "디제이켜", "dj켜"),
        VoiceIntent.MUTE_DJ to listOf("말그만", "조용히해", "그만말", "디제이꺼", "dj꺼", "말하지마", "멘트그만"),
        VoiceIntent.DISLIKE to listOf("안좋아", "좋지않", "별로", "싫어", "싫다", "이상해", "구려", "노잼"),
        // 거절은 휴게소보다 먼저: "휴게소 안 가도 돼"는 휴게소 요청이 아니다
        VoiceIntent.NO to listOf("안가", "안들러", "안졸", "안피곤", "지않", "지말", "가지마", "안쉬", "아니", "괜찮아", "됐어", "필요없", "안해도"),
        VoiceIntent.REST_STOP to listOf("휴게소", "졸려", "졸리", "쉬고싶", "쉬어가", "졸음쉼터"),
        VoiceIntent.SKIP to listOf("다음곡", "넘겨", "스킵", "다음노래", "패스", "skip", "next"),
        VoiceIntent.MORE_ENERGY to listOf("신나게", "신나는", "텐션", "업시켜", "빠른곡", "흥겹게", "에너지"),
        VoiceIntent.CALMER to listOf("차분", "조용한곡", "잔잔", "느린곡", "편안하게", "릴렉스"),
        VoiceIntent.LIKE to listOf("좋아", "좋다", "좋네", "최고", "마음에들", "맘에들", "굿", "like"),
        VoiceIntent.YES to listOf("응", "어", "네", "예", "그래", "넣어줘", "추가해", "들르자", "가자"),
    )

    fun parse(text: String): VoiceIntent {
        val t = text.norm()
        if (t.isEmpty()) return VoiceIntent.UNKNOWN
        val hit = rules.firstOrNull { (_, keys) -> keys.any { it in t } }?.first ?: return VoiceIntent.UNKNOWN
        // 짧은 대답(응/네/어)은 문장 전체가 그 말일 때만 YES로 본다 ("어쿠스틱"의 '어' 등 오탐 방지)
        if (hit == VoiceIntent.YES && t.length > 4 && listOf("넣어줘", "추가해", "들르자", "가자").none { it in t }) return VoiceIntent.UNKNOWN
        return hit
    }
}
