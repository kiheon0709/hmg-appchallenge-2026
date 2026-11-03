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
        // 리뷰에서 나온 오분류 회귀 케이스
        "휴게소 안 가도 돼" to VoiceIntent.NO,
        "이 노래 뭔지 말해줘" to VoiceIntent.WHAT_SONG,
        "더 크게" to VoiceIntent.UNKNOWN,
        "아니 별로야" to VoiceIntent.DISLIKE,
        "어쿠스틱 틀어줘" to VoiceIntent.UNKNOWN,
        "응" to VoiceIntent.YES,
        "네" to VoiceIntent.YES,
        "경유지 추가해" to VoiceIntent.YES,
        "괜찮아요" to VoiceIntent.NO,
        "이 분위기 유지해줘" to VoiceIntent.HOLD,
        "바꾸지 마" to VoiceIntent.HOLD,
        "졸리지 않아" to VoiceIntent.NO,
        "휴게소 가지 말자" to VoiceIntent.NO,
        "쉬고 싶지 않아" to VoiceIntent.NO,
        "이 노래 좋지 않아" to VoiceIntent.DISLIKE,
        // 3바퀴 심사 회귀 케이스
        "다음 휴게소 들르지 마" to VoiceIntent.NO,
        "신나는 거 말고 차분하게" to VoiceIntent.CALMER,
        "차분한 거 말고 신나게" to VoiceIntent.MORE_ENERGY,
        "졸린데 신나는 노래 틀어줘" to VoiceIntent.REST_STOP,
        "피곤해" to VoiceIntent.REST_STOP,
        "피곤하지 않아" to VoiceIntent.NO,
    )

    @Test
    fun `parses korean driving commands`() {
        val wrong = cases.filter { (text, want) -> VoiceCommandParser.parse(text) != want }
            .map { (text, want) -> "'$text' → ${VoiceCommandParser.parse(text)} (want $want)" }
        assertEquals(emptyList(), wrong)
    }
}

class GenresTest {
    @Test
    fun `every catalog genre has an onboarding label and vice versa`() {
        val catalogGenres = SampleCatalog.tracks.map { it.genre }.toSet()
        val keys = Genres.all.map { it.second }.toSet()
        assertEquals(catalogGenres, keys)
    }

    @Test
    fun `preferred genres bias selection`() {
        val taste = TasteModel()
        taste.setPreferredGenres(setOf("jazz"))
        val c = Curator(SampleCatalog.tracks, taste, Curator.Config(tasteWeight = 0.5f))
        val pick = c.next(Mood(-0.3f, 0.0f), Scene.NIGHT)
        assertEquals("jazz", pick.genre)
    }
}
