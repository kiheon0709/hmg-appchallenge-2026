package io.moodlab.sideseat.voice

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceChunkerTest {

    private fun chunk(vararg deltas: String, maxChars: Int = 90): List<String> {
        val c = SentenceChunker(maxChars)
        return deltas.flatMap { c.push(it) } + c.flush()
    }

    @Test fun splitsOnTerminators() {
        assertEquals(
            listOf("비가 와요.", "전조등을 켜 주세요!", "괜찮아요?"),
            chunk("비가 와요. 전조등을 켜 주세요! 괜찮아요?"),
        )
    }

    @Test fun emitsSentenceAsSoonAsItEndsWhileStreaming() {
        val c = SentenceChunker()
        assertEquals(emptyList<String>(), c.push("앞유리 서리 제거 버튼을"))
        assertEquals(listOf("앞유리 서리 제거 버튼을 눌러요."), c.push(" 눌러요."))
        assertEquals(emptyList<String>(), c.push(" 에어컨도"))
        assertEquals(emptyList<String>(), c.push(" 켜요"))
        assertEquals(listOf("에어컨도 켜요"), c.flush())
    }

    @Test fun keepsDecimalNumbersTogether() {
        assertEquals(listOf("좋아요.", "3.8초 걸렸어요."), chunk("좋아요. 3", ".", "8초 걸렸어요."))
    }

    @Test fun waitsWhenDotFollowsDigitAtBufferEnd() {
        val c = SentenceChunker()
        assertEquals(emptyList<String>(), c.push("반응 시간 0."))
        assertEquals(emptyList<String>(), c.push("7초"))
        assertEquals(listOf("반응 시간 0.7초예요."), c.push("예요. "))
    }

    @Test fun mergesRepeatedTerminatorsAndClosingQuotes() {
        assertEquals(listOf("정말요?!", "\"좋아요.\""), chunk("정말요?! \"좋아요.\""))
    }

    @Test fun newlineEndsSentence() {
        assertEquals(listOf("첫째 줄", "둘째 줄"), chunk("첫째 줄\n둘째 줄"))
    }

    @Test fun stripsMarkdownForSpeech() {
        assertEquals(listOf("비상등을 켜요.", "트렁크를 열어요."), chunk("- **비상등**을 켜요.\n1. 트렁크를 열어요."))
    }

    @Test fun dropsPunctuationOnlyPieces() {
        assertEquals(listOf("음."), chunk("음.", "..", " "))
    }

    @Test fun forcesSplitOnLongTextWithoutTerminator() {
        val long = "가나다라 마바사아 자차카타 파하가나 다라마바 사아자차 카타파하 가나다라"
        val out = chunk(long, maxChars = 20)
        assert(out.size >= 2) { out.toString() }
        assertEquals(long.replace(" ", ""), out.joinToString("").replace(" ", ""))
        assert(out.all { it.length <= 21 }) { out.toString() }
    }

    @Test fun flowAdapterSplitsDeltas() = runTest {
        val out = listOf("안녕하", "세요. 선배", "예요. 잘 부탁", "해요").asFlow().toSentences().toList()
        assertEquals(listOf("안녕하세요.", "선배예요.", "잘 부탁해요"), out)
    }
}
