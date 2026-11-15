package io.moodlab.sideseat.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SpeechTextTest {
    @Test
    fun `minutes and seconds are read with sino-korean numbers`() {
        assertEquals("일 분 동안 침착하게 잘 왔어요.", SpeechText.forTts("1분 동안 침착하게 잘 왔어요."))
        assertEquals("다음에 세웠을 때 폭우 훈련을 일 분만 해 볼까요?", SpeechText.forTts("다음에 세웠을 때 폭우 훈련을 1분만 해 볼까요?"))
        assertEquals("삼십오 분 동안", SpeechText.forTts("35분 동안"))
        assertEquals("칭찬해요. 사 점 영 초 걸렸어요.", SpeechText.forTts("칭찬해요. 4.0초 걸렸어요."))
        assertEquals("가장 길게 십이 초예요.", SpeechText.forTts("가장 길게 12초예요."))
    }

    @Test
    fun `counts are read with native numbers and quiz option numbers with sino-korean`() {
        assertEquals("깜빡이 없이 차로를 바꾼 게 한 번 있었어요.", SpeechText.forTts("깜빡이 없이 차로를 바꾼 게 1번 있었어요."))
        assertEquals("과속이 세 번 있었어요.", SpeechText.forTts("과속이 3번 있었어요."))
        assertEquals("일 번, 비상등 켜기. 이 번, 보험사에 전화하기.", SpeechText.forTts("1번, 비상등 켜기. 2번, 보험사에 전화하기."))
        assertEquals("별 세 개", SpeechText.forTts("별 3개"))
        // 단위가 없는 숫자는 그대로
        assertEquals("제한속도가 50이에요. 30 아래로 가요.", SpeechText.forTts("제한속도가 50이에요. 30 아래로 가요."))
        assertEquals("배터리가 18% 남았어요.", SpeechText.forTts("배터리가 18% 남았어요."))
    }
}
