package io.moodlab.moodrive.core

import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceCommandTest {
    private val cases = mapOf(
        "이거 별로야" to VoiceIntent.DISLIKE,
        "안 좋아" to VoiceIntent.DISLIKE,
        "좋아!" to VoiceIntent.LIKE,
        "이 노래 좋다" to VoiceIntent.LIKE,
        "다음 곡" to VoiceIntent.SKIP,
        "넘겨줘" to VoiceIntent.SKIP,
        "좀 더 신나게" to VoiceIntent.MORE_ENERGY,
        "텐션 올려줘" to VoiceIntent.MORE_ENERGY,
        "차분하게 가자" to VoiceIntent.CALMER,
        "잔잔한 거 틀어줘" to VoiceIntent.CALMER,
        "졸려" to VoiceIntent.REST_STOP,
        "휴게소 들르자" to VoiceIntent.REST_STOP,
        "말 그만해" to VoiceIntent.MUTE_DJ,
        "조용히 해" to VoiceIntent.MUTE_DJ,
        "다시 말해줘" to VoiceIntent.UNMUTE_DJ,
        "이 노래 뭐야" to VoiceIntent.WHAT_SONG,
        "누구 노래야" to VoiceIntent.WHAT_SONG,
        "오늘 저녁 뭐 먹지" to VoiceIntent.UNKNOWN,
        "" to VoiceIntent.UNKNOWN,
    )

    @Test
    fun `parses korean driving commands`() {
        val wrong = cases.filter { (text, want) -> VoiceCommandParser.parse(text) != want }
            .map { (text, want) -> "'$text' → ${VoiceCommandParser.parse(text)} (want $want)" }
        assertEquals(emptyList(), wrong)
    }
}
