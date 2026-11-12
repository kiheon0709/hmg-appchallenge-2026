package io.moodlab.sideseat.voice

import io.moodlab.sideseat.core.DriveSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamOpsTest {

    @Test fun fillerWhenFirstSentenceIsLate() = runTest {
        val src = flow { delay(900); emit("본문이에요.") }
        assertEquals(listOf("음, 잠깐만요.", "본문이에요."), src.withFiller(600, "음, 잠깐만요.").toList())
    }

    @Test fun noFillerWhenFirstSentenceIsQuick() = runTest {
        val src = flow { delay(300); emit("하나."); delay(2_000); emit("둘.") }
        assertEquals(listOf("하나.", "둘."), src.withFiller(600, "음.").toList())
    }

    @Test fun fillerSuppressedWhenAsked() = runTest {
        val src = flow { delay(900); emit("본문.") }
        assertEquals(listOf("본문."), src.withFiller(600, "음.", suppress = { true }).toList())
    }

    @Test fun noFillerWhenSourceIsEmptyAndQuick() = runTest {
        assertEquals(emptyList<String>(), flow<String> { delay(100) }.withFiller(600, "음.").toList())
    }

    @Test fun fallbackOnErrorBeforeFirstSentence() = runTest {
        var cause: Throwable? = null
        val src = flow<String> { delay(100); throw IllegalStateException("network") }
        val out = src.withFallback(3_000, onFailure = { cause = it }) { listOf("미리 쓴 답.") }.toList()
        assertEquals(listOf("미리 쓴 답."), out)
        assertTrue(cause is IllegalStateException)
    }

    @Test fun fallbackOnFirstSentenceTimeout() = runTest {
        val src = flow { delay(10_000); emit("늦은 답.") }
        val out = src.withFallback(3_000) { listOf("미리 쓴 답.") }.toList()
        assertEquals(listOf("미리 쓴 답."), out)
        assertTrue("시간 초과 직후 끝나야 함: $currentTime", currentTime in 3_000..3_100)
    }

    @Test fun fallbackWhenSourceIsEmpty() = runTest {
        assertEquals(listOf("대신."), flowOf<String>().withFallback(3_000) { listOf("대신.") }.toList())
    }

    @Test fun errorAfterFirstSentenceStopsQuietly() = runTest {
        var failed = false
        val src = flow { emit("첫 문장."); delay(50); throw RuntimeException("cut") }
        val out = src.withFallback(3_000, onFailure = { failed = true }) { listOf("대신.") }.toList()
        assertEquals(listOf("첫 문장."), out)
        assertTrue(failed)
    }

    @Test fun slowLaterSentencesAreNotCutByFirstTimeout() = runTest {
        val src = flow { emit("하나."); delay(5_000); emit("둘.") }
        assertEquals(listOf("하나.", "둘."), src.withFallback(3_000) { listOf("대신.") }.toList())
    }

    @Test fun fillerThenFallbackOrder() = runTest {
        val src = flow<String> { delay(5_000); throw RuntimeException("x") }
        val out = src.withFallback(3_500) { listOf("대신.") }.withFiller(600, "음.").toList()
        assertEquals(listOf("음.", "대신."), out)
    }
}

/** LLM 대역: 호출을 기록하고, 정해 둔 조각을 정해 둔 지연으로 흘려보낸다. */
private class FakeLlm(
    private val pieces: List<String> = listOf("외기로 바꾸면 ", "습기가 빠져요. ", "에어컨도 ", "같이 켜요. ", "셋째. ", "넷째. ", "다섯째."),
    private val firstDelayMs: Long = 300,
    private val fail: Throwable? = null,
    private val pieceDelayMs: Long = 20,
) : LlmClient {
    val calls = mutableListOf<Pair<LlmClient.Route, String>>()
    val prewarms = mutableListOf<LlmClient.Route>()
    val systems = mutableSetOf<String>()

    override fun stream(route: LlmClient.Route, system: String, user: String, maxTokens: Long): Flow<String> = flow {
        calls += route to user
        systems += system
        delay(firstDelayMs)
        fail?.let { throw it }
        pieces.forEach { emit(it); delay(pieceDelayMs) }
    }

    override fun prewarm(route: LlmClient.Route, system: String) { prewarms += route; systems += system }
}

class SeniorInstructorTest {
    private val kb = KnowledgeBase.parse(
        listOf(
            """
            ## defog 김서림
            질문: 김서림, 뿌예
            짧게: 서리 제거 버튼을 눌러요.
            자세히: 서리 제거 버튼을 눌러요. 에어컨도 켜요.
            출처: 설명서
            """.trimIndent(),
        ),
    )
    private val logs = mutableListOf<String>()

    private fun TestScope.instructor(llm: LlmClient?): Pair<SeniorInstructor, LatencyMeter> {
        val meter = LatencyMeter(now = { currentTime }, log = { logs += it })
        return SeniorInstructor(kb, llm, meter, backgroundScope, { currentTime }, { logs += it }) to meter
    }

    private val summary = DriveSummary(
        durationMin = 42, rainMin = 10, nightMin = 0, utterances = emptyList(), observations = mapOf("NO_SIGNAL_TURN" to 1),
        suggestedSituation = null, ruleBasedReview = listOf("빗길에서 전조등을 잘 켰어요.", "깜빡이를 한 번 놓쳤어요.", "다음엔 폭우 훈련을 해 봐요."),
    )

    @Test fun fastPathDoesNotCallLlm() = runTest {
        val llm = FakeLlm()
        val (ins, _) = instructor(llm)
        assertEquals(listOf("서리 제거 버튼을 눌러요."), ins.answer("앞유리가 뿌예", parked = false, context = "").toList())
        assertEquals(listOf("좋아요."), ins.answer("응", parked = false, context = "").toList())
        assertTrue(llm.calls.isEmpty())
    }

    @Test fun llmAnswerIsSplitIntoSentencesAndCappedWhileDriving() = runTest {
        val llm = FakeLlm()
        val (ins, _) = instructor(llm)
        val out = ins.answer("외기는 왜 바꿔?", parked = false, context = "와이퍼 LOW").toList()
        assertEquals(listOf("외기로 바꾸면 습기가 빠져요.", "에어컨도 같이 켜요."), out)
        assertEquals(1, llm.calls.size)
        assertTrue(llm.calls[0].second.contains("<question>외기는 왜 바꿔?</question>"))
        assertTrue(llm.calls[0].second.contains("주행 중"))
    }

    @Test fun parkedAnswerAllowsFourSentences() = runTest {
        val (ins, _) = instructor(FakeLlm())
        assertEquals(4, ins.answer("외기는 왜 바꿔?", parked = true, context = "").toList().size)
    }

    @Test fun slowLlmGetsFillerFirst() = runTest {
        val (ins, _) = instructor(FakeLlm(firstDelayMs = 1_200))
        val out = ins.answer("외기는 왜 바꿔?", parked = false, context = "").toList()
        assertEquals(SeniorInstructor.FILLER, out.first())
        assertEquals("외기로 바꾸면 습기가 빠져요.", out[1])
    }

    @Test fun llmFailureFallsBackToKnowledgeOrUnknown() = runTest {
        val (ins, _) = instructor(FakeLlm(fail = RuntimeException("401")))
        assertEquals(listOf(FastPath.UNKNOWN_ANSWER.substringBefore(" 취급"), "취급설명서를 같이 볼까요?"),
            ins.answer("주유 경고등은 얼마나 더 가?", parked = false, context = "").toList())
    }

    @Test fun noKeyUsesKnowledgeAndRuleBasedReview() = runTest {
        val (ins, _) = instructor(null)
        assertEquals(listOf("서리 제거 버튼을 눌러요.", "에어컨도 켜요."),
            ins.answer("아까 김서림 생겼을 때 어떻게 했어야 해?", parked = true, context = "").toList())
        assertEquals(summary.ruleBasedReview, ins.review(summary).toList())
    }

    @Test fun reviewFallsBackToRuleBasedWhenLlmFails() = runTest {
        val (ins, _) = instructor(FakeLlm(fail = RuntimeException("overloaded"), firstDelayMs = 100))
        assertEquals(summary.ruleBasedReview, ins.review(summary).toList())
    }

    @Test fun reviewUsesReviewRouteAndDriveFacts() = runTest {
        val llm = FakeLlm()
        val (ins, _) = instructor(llm)
        ins.review(summary).toList()
        assertEquals(LlmClient.Route.REVIEW, llm.calls.single().first)
        assertTrue(llm.calls.single().second.contains("주행 시간: 42분"))
        assertTrue(llm.calls.single().second.contains("깜빡이 없이 크게 핸들을 돌림: 1번"))
    }

    @Test fun systemPromptIsIdenticalAcrossRequestsForCaching() = runTest {
        val llm = FakeLlm()
        val (ins, _) = instructor(llm)
        ins.onListenStart()
        ins.answer("외기는 왜 바꿔?", false, "a").toList()
        ins.answer("터널은?", true, "b").toList()
        ins.review(summary).toList()
        assertEquals(1, llm.systems.size)
        assertTrue(llm.systems.single().contains("<knowledge>"))
        assertTrue(llm.systems.single().contains("서리 제거 버튼을 눌러요."))
    }

    @Test fun prewarmOnlyOncePerInterval() = runTest {
        val llm = FakeLlm()
        val (ins, _) = instructor(llm)
        ins.onListenStart(); ins.onListenStart()
        assertEquals(1, llm.prewarms.size)
    }

    @Test fun speculativeRequestAtEpdIsReusedWhenFinalMatches() = runTest {
        val llm = FakeLlm(firstDelayMs = 800)
        val (ins, meter) = instructor(llm)
        ins.prepareQuestion(parked = false, context = "ctx")
        ins.onPartial("외기는 왜")
        ins.onEpdEnd("외기는 왜 바꿔")
        delay(500) // STT 최종 결과가 늦게 오는 동안 LLM은 이미 달리고 있다
        ins.onFinal("외기는 왜 바꿔?")
        val out = ins.answer("외기는 왜 바꿔?", parked = false, context = "ctx").toList()
        assertEquals(1, llm.calls.size)
        assertEquals("외기로 바꾸면 습기가 빠져요.", out.first())
        assertTrue(logs.any { "speculative hit" in it })
        meter.onTtsStart()
        // 말 끝(500ms 시점) → 첫 문장은 추측 요청 덕에 300ms 뒤면 나온다
        assertTrue("latency=${meter.last.value}", meter.last.value!! < 800)
    }

    @Test fun speculativeRequestIsDiscardedWhenFinalDiffers() = runTest {
        val llm = FakeLlm()
        val (ins, _) = instructor(llm)
        ins.prepareQuestion(parked = false, context = "ctx")
        ins.onEpdEnd("외기는 왜")
        delay(100) // backgroundScope 작업은 advanceUntilIdle로 돌지 않으므로 시간을 흘린다
        val out = ins.answer("외기는 왜 바꿔야 돼?", parked = false, context = "ctx").toList()
        assertEquals(2, llm.calls.size)
        assertTrue(llm.calls[1].second.contains("<question>외기는 왜 바꿔야 돼?</question>"))
        assertEquals("외기로 바꾸면 습기가 빠져요.", out.first())
        assertTrue(logs.any { "speculative miss" in it })
    }

    @Test fun noSpeculationWithoutPrepare() = runTest {
        val llm = FakeLlm()
        val (ins, _) = instructor(llm)
        ins.onEpdEnd("외기는 왜 바꿔")
        delay(100)
        assertTrue(llm.calls.isEmpty())
    }

    @Test fun latencyIsSplitIntoFillerAndRealAnswer() = runTest {
        val (ins, meter) = instructor(FakeLlm(firstDelayMs = 1_200))
        ins.onFinal("외기는 왜 바꿔?")
        val out = mutableListOf<String>()
        ins.answer("외기는 왜 바꿔?", parked = false, context = "").collect { s ->
            out += s
            delay(10)
            meter.onTtsStart() // 순차 재생: 문장마다 onStart
        }
        assertEquals(SeniorInstructor.FILLER, out.first())
        assertEquals(610L, meter.last.value) // 첫 음성 = 채움말
        val firstLine = logs.single { it.startsWith("latency total=") }
        assertTrue(firstLine, "first=filler" in firstLine)
        val answerLine = logs.single { it.startsWith("latency answer=") }
        // LLM 첫 조각 1,200ms + 문장 완성 20ms + 재생 10ms
        assertTrue(answerLine, answerLine.startsWith("latency answer=12"))
    }

    @Test fun noFillerOnceLlmTextHasStarted() = runTest {
        // 첫 토큰 500ms, 첫 문장 완성 700ms: 600ms에 채움말을 내면 답이 채움말 뒤로 밀리므로 내지 않는다.
        val (ins, meter) = instructor(FakeLlm(firstDelayMs = 500, pieceDelayMs = 200))
        ins.onFinal("외기는 왜 바꿔?")
        val out = mutableListOf<String>()
        ins.answer("외기는 왜 바꿔?", parked = false, context = "").collect { out += it; meter.onTtsStart() }
        assertEquals("외기로 바꾸면 습기가 빠져요.", out.first())
        val line = logs.single { it.startsWith("latency total=") }
        assertTrue(line, "first=answer" in line)
        assertTrue(line, "request->firstToken=500ms" in line)
        assertTrue(line, "firstToken->firstChunk=200ms" in line)
    }

    @Test fun speculativeRequestShowsNegativeFinalToRequest() = runTest {
        val llm = FakeLlm(firstDelayMs = 600)
        val (ins, meter) = instructor(llm)
        ins.prepareQuestion(parked = false, context = "")
        ins.onEpdEnd("외기는 왜 바꿔")
        delay(400)
        ins.onFinal("외기는 왜 바꿔?")
        val out = mutableListOf<String>()
        ins.answer("외기는 왜 바꿔?", parked = false, context = "").collect { out += it; meter.onTtsStart() }
        val line = logs.single { it.startsWith("latency total=") }
        assertTrue(line, "final->request=-400ms" in line)
        assertTrue(line, "path=llm-speculative" in line)
        // 말 끝(400ms) → 첫 문장(600+20ms): 추측 덕에 220ms
        assertEquals(220L, meter.last.value)
    }

    @Test fun latencyMeterRecordsOnlyFirstTtsStartAfterSpeechEnd() = runTest {
        val (ins, meter) = instructor(FakeLlm())
        ins.onFinal("응")
        delay(120)
        ins.answer("응", false, "").toList()
        meter.onTtsStart()
        assertEquals(120L, meter.last.value)
        delay(1_000)
        meter.onTtsStart()
        assertEquals(120L, meter.last.value)
        assertTrue(logs.any { "path=fast-reply" in it })
    }
}
