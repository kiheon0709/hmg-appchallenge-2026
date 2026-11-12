package io.moodlab.sideseat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReplyClassifierTest {
    private fun c(s: String) = ReplyClassifier.classify(s)

    @Test fun yes() {
        listOf("응", "응 그래", "네", "네네", "예", "어", "좋아", "그래 가자", "오케이", "쉬어 가자", "응 부탁해").forEach {
            assertEquals(it, Reply.YES, c(it))
        }
    }

    @Test fun no() {
        listOf("아니", "아니요", "아뇨", "싫어", "괜찮아", "됐어", "안 쉴래", "필요 없어", "그냥 가자 아니 됐어").forEach {
            assertEquals(it, Reply.NO, c(it))
        }
    }

    @Test fun later() {
        listOf("나중에", "이따가", "다음에 할게", "조금 있다가", "잠깐만").forEach { assertEquals(it, Reply.LATER, c(it)) }
    }

    @Test fun repeat() {
        listOf("뭐라고?", "다시 말해 줘", "잘 안 들렸어").forEach { assertEquals(it, Reply.REPEAT, c(it)) }
    }

    @Test fun looksCompleteForSpeechEndGuess() {
        listOf("너의 의기로 바꿔야 해", "안개등 어떻게 켜?", "앞유리가 뿌예", "왜 그래요", "외기로 바꿀까", "이거 뭐야", "졸려 죽겠다").forEach {
            assertTrue(it, ReplyClassifier.looksComplete(it))
        }
        listOf("너의 의기로", "안개등을", "앞유리가", "외기는 왜", "").forEach {
            assertFalse(it, ReplyClassifier.looksComplete(it))
        }
    }

    @Test fun questionsAreNotReplies() {
        listOf("안개등 어떻게 켜", "앞유리가 뿌예", "이 경고등 뭐야", "아니 근데 터널에서는 차로 바꿔도 돼?", "").forEach {
            assertEquals(it, Reply.UNKNOWN, c(it))
        }
    }
}

class KnowledgeBaseTest {
    private val sample = """
        # 제목
        설명 문단은 무시해요.

        ## fog 짙은 안개
        질문: 안개, 안개 낀
        짧게: 전조등을 하향으로 켜요.
        자세히: 첫 문장이에요.
          이어지는 줄이에요.
        출처: 도로교통법 제37조

        ## fog_lamp 안개등 켜는 법
        질문: 안개등 어떻게, 포그등
        짧게: 레버의 안개 모양 스위치를 돌려요.
        출처: 앱 기본 안내

        ## broken 짧게가 없는 항목은 버린다
        질문: 고장
    """.trimIndent()

    @Test fun parsesEntriesAndMultilineValues() {
        val kb = KnowledgeBase.parse(listOf(sample))
        assertEquals(listOf("fog", "fog_lamp"), kb.entries.map { it.id })
        val fog = kb.entries[0]
        assertEquals("짙은 안개", fog.title)
        assertEquals(listOf("안개", "안개 낀"), fog.keywords)
        assertEquals("첫 문장이에요. 이어지는 줄이에요.", fog.detail)
        assertEquals("도로교통법 제37조", fog.source)
        assertEquals("레버의 안개 모양 스위치를 돌려요.", kb.entries[1].detail) // 자세히가 없으면 짧게
    }

    @Test fun moreSpecificKeywordWins() {
        val kb = KnowledgeBase.parse(listOf(sample))
        assertEquals("fog_lamp", kb.match("안개등 어떻게 켜?")?.entry?.id)
        assertEquals("fog", kb.match("안개 낀 날은요?")?.entry?.id)
        assertNull(kb.match("오늘 저녁 뭐 먹지"))
    }

    @Test fun tieIsAmbiguous() {
        val kb = KnowledgeBase.parse(listOf("## a A\n질문: 비가\n짧게: a.\n## b B\n질문: 터널\n짧게: b."))
        assertNull(kb.match("비가 오는데 터널"))
    }

    // ---------- 실제 지식 파일 ----------

    private val assets = File("src/main/assets/knowledge")
    private val real by lazy { KnowledgeBase.parse(assets.listFiles()!!.filter { it.name.endsWith(".md") }.sortedBy { it.name }.map { it.readText() }) }

    @Test fun realKnowledgeFilesAreComplete() {
        assertTrue("지식 파일이 비어 있음", real.entries.size >= 15)
        real.entries.forEach { e ->
            assertTrue("${e.id} 출처 없음", e.source.isNotBlank())
            assertTrue("${e.id} 키워드 없음", e.normalizedKeywords.isNotEmpty())
            assertTrue("${e.id} 주행 중 답이 길다: ${e.short.length}자", e.short.length <= 60)
            assertTrue("${e.id} 금지어", "급발진" !in e.short && "급발진" !in e.detail)
        }
        assertEquals("아이디 중복", real.entries.size, real.entries.map { it.id }.toSet().size)
    }

    @Test fun realKnowledgeRoutesSpecExampleQuestions() {
        // 명세 6.3의 주행 중 질문 예
        assertEquals("defog_windshield", real.match("앞유리가 뿌예")?.entry?.id)
        assertEquals("warning_light", real.match("이 경고등 뭐야")?.entry?.id)
        assertEquals("sw_fog", real.match("안개등 어떻게 켜?")?.entry?.id)
        // 그 밖의 대표 질문
        assertEquals("breakdown_highway", real.match("고속도로에서 차가 멈췄어")?.entry?.id)
        assertEquals("unintended_accel", real.match("차가 안 멈춰 어떡해")?.entry?.id)
        assertEquals("tpms", real.match("타이어 공기압 경고등이 켜졌어")?.entry?.id)
        assertEquals("sw_hazard", real.match("비상등 어딨어")?.entry?.id)
        assertEquals("drowsy", real.match("너무 졸려")?.entry?.id)
        assertEquals("tunnel", real.match("터널 들어가")?.entry?.id)
    }
}

class FastPathTest {
    private val kb = KnowledgeBase.parse(
        listOf(
            """
            ## defog 김서림
            질문: 김서림, 뿌예
            짧게: 서리 제거 버튼을 눌러요.
            자세히: 서리 제거 버튼을 눌러요. 에어컨도 켜요. 외기로 바꿔요.
            출처: 설명서
            """.trimIndent(),
        ),
    )
    private val fp = FastPath(kb)

    @Test fun shortRepliesAnswerImmediately() {
        val d = fp.decide("응", parked = false, llmAvailable = true)
        assertTrue(d is FastPath.Decision.ShortReply)
        assertEquals(listOf("좋아요."), d!!.sentences)
        assertEquals(listOf("네, 나중에 다시 말씀드릴게요."), fp.decide("나중에", false, true)!!.sentences)
    }

    @Test fun knownQuestionUsesShortWhileDriving() {
        val d = fp.decide("앞유리가 뿌예", parked = false, llmAvailable = true)
        assertEquals(listOf("서리 제거 버튼을 눌러요."), d!!.sentences)
    }

    @Test fun knownQuestionUsesDetailWhenParked() {
        val d = fp.decide("김서림", parked = true, llmAvailable = true)
        assertEquals(3, d!!.sentences.size)
    }

    @Test fun longParkedQuestionGoesToLlmWhenAvailable() {
        val q = "아까 김서림 생겼을 때 왜 외기로 바꿔야 했던 거야?"
        assertNull(fp.decide(q, parked = true, llmAvailable = true))
        assertNotNull(fp.decide(q, parked = true, llmAvailable = false))
        assertNotNull(fp.decide(q, parked = false, llmAvailable = true))
    }

    @Test fun unknownQuestionGoesToSlowPathOrSaysUnknown() {
        assertNull(fp.decide("근처 맛집 알려줘", parked = false, llmAvailable = true))
        assertEquals(listOf("그건 제가 정확히 몰라요.", "취급설명서를 같이 볼까요?"), fp.fallback("근처 맛집 알려줘", false))
    }
}
