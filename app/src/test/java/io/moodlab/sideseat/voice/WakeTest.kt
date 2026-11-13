package io.moodlab.sideseat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordDetectorTest {
    private fun d(s: String) = WakeWordDetector.detect(s)

    @Test fun wakeAtStartWithQuestionInSameUtterance() {
        assertEquals(WakeMatcher.Result.Wake("앞유리가 뿌예"), d("타비야, 앞유리가 뿌예"))
        assertEquals(WakeMatcher.Result.Wake("안개등 어떻게 켜?"), d("타비야 안개등 어떻게 켜?"))
        assertEquals(WakeMatcher.Result.Wake("이 경고등 뭐야"), d("타비아 이 경고등 뭐야"))
    }

    @Test fun wakeAlone() {
        assertEquals(WakeMatcher.Result.Wake(""), d("타비야"))
        assertEquals(WakeMatcher.Result.Wake(""), d("타비!"))
        assertEquals(WakeMatcher.Result.Wake(""), d("타비"))
    }

    @Test fun commonMisrecognitionsAndLeadingWord() {
        // 합성 음성 실측 11-13: "탑이야" 6/12, "따비야" 2/12
        assertEquals(WakeMatcher.Result.Wake("와이퍼 어디에 있어?"), d("탑이야 와이퍼 어디에 있어?"))
        assertEquals(WakeMatcher.Result.Wake("앞유리가 또 해"), d("따비야 앞유리가 또 해"))
        assertTrue(d("다비야 졸려") is WakeMatcher.Result.Wake)
        assertTrue(d("타피야 비 와") is WakeMatcher.Result.Wake)
        assertEquals(WakeMatcher.Result.Wake("비 와"), d("저기 타비야 비 와"))
        assertEquals(WakeMatcher.Result.Wake("깜빡이 언제 켜"), d("야 타비야, 깜빡이 언제 켜"))
    }

    @Test fun vocativeShapeRequired() {
        assertEquals("이름 뒤에 바로 다른 말", WakeMatcher.Result.None, d("타비 와이퍼 어디 있어"))
        assertEquals("조사", WakeMatcher.Result.None, d("타비가 그러는데 비 온대"))
        assertEquals("탑이는 일상어", WakeMatcher.Result.None, d("탑이 높다"))
        assertEquals("호칭 꼴이 띄어 있음", WakeMatcher.Result.None, d("타비 아까 그거 뭐야"))
        assertEquals("호칭 꼴 뒤에 말이 붙음", WakeMatcher.Result.None, d("타비아까 그거 뭐야"))
    }

    @Test fun wakeInMiddleIsIgnored() {
        assertEquals(WakeMatcher.Result.None, d("우리 타비야 오늘 잘했어"))
        assertEquals(WakeMatcher.Result.None, d("어제 회사 선배랑 밥 먹었어"))
        assertEquals(WakeMatcher.Result.None, d("오늘 날씨 좋다"))
        assertEquals(WakeMatcher.Result.None, d(""))
    }

    @Test fun weakVariantsOffByDefault() {
        assertEquals(WakeMatcher.Result.None, d("나비야 와이퍼 어디 있어?"))
        assertEquals(WakeMatcher.Result.None, d("아비야"))
        assertEquals(WakeMatcher.Result.None, d("차비야 얼마야?"))
    }

    @Test fun weakVariantsNarrowRuleWhenEnabled() {
        val m = WakeMatcher(allowWeak = true)
        assertEquals(WakeMatcher.Result.Wake("와이브 어디 있어?"), m.detect("나비야 와이브 어디 있어?")) // 실측 남성 음성
        assertEquals("질문 꼴이 아니면 아님", WakeMatcher.Result.None, m.detect("나비야 암 유리가 뿌얘"))
        assertEquals("노랫말", WakeMatcher.Result.None, m.detect("나비야 나비야 이리 날아오너라"))
        assertEquals("뒤가 없으면 아님", WakeMatcher.Result.None, m.detect("나비야"))
        assertEquals("앞말이 붙으면 아님", WakeMatcher.Result.None, m.detect("저기 나비야 어디 있어?"))
        assertEquals("호칭 꼴이 없으면 아님", WakeMatcher.Result.None, m.detect("나비 어디 있어?"))
        assertEquals("강한 변형은 그대로", WakeMatcher.Result.Wake(""), m.detect("탑이야"))
    }

    @Test fun gleoMeansBackOff() {
        assertEquals(WakeMatcher.Result.Gleo, d("글레오 음악 틀어 줘"))
        assertEquals(WakeMatcher.Result.Gleo, d("헤이 글레오"))
        assertEquals(WakeMatcher.Result.Gleo, d("클레오 집으로 안내해"))
        assertEquals(WakeMatcher.Result.Gleo, d("저기 글레오"))
        // 중간에 나온 글레오는 우리 판단과 무관
        assertEquals(WakeMatcher.Result.None, d("아까 글레오가 그러던데"))
    }

    @Test fun partialCheck() {
        assertTrue(WakeWordDetector.startsWithWake("타비야"))
        assertTrue(WakeWordDetector.startsWithWake("저기 탑이야 앞"))
        assertFalse(WakeWordDetector.startsWithWake("우리 타비야"))
    }
}

class WakeLoopTest {
    private val cfg = WakeConfig(errorBackoffMs = 1_000, maxBackoffMs = 8_000, gleoBackoffMs = 10_000, interferenceBackoffMs = 5_000, followUpMs = 6_000)

    @Test fun offUntilStarted() {
        val l = WakeLoop(cfg)
        assertEquals(-1L, l.waitBeforeListen(0))
        l.start()
        assertEquals(0L, l.waitBeforeListen(0))
        assertEquals(WakeState.LISTENING, l.state)
    }

    @Test fun wakeQuestionThenSpeakingThenFollowUpWindow() {
        val l = WakeLoop(cfg).apply { start() }
        val d = l.onUtterance("타비야, 앞유리가 뿌예", now = 1_000)
        assertEquals(WakeLoop.Decision.Question("앞유리가 뿌예", viaWake = true), d)
        assertEquals(WakeState.HANDLING, l.state)
        assertEquals("답하는 동안은 듣지 않는다", -1L, l.waitBeforeListen(1_100))
        l.onSpeaking(true, 1_200)
        assertEquals(WakeState.SPEAKING, l.state)
        assertEquals(-1L, l.waitBeforeListen(2_000))
        l.onSpeaking(false, 5_000)
        assertEquals(WakeState.FOLLOW_UP, l.state)
        assertTrue(l.inFollowUp(6_000))
        // 되묻기 창 안에서는 호출어 없이 받는다
        assertEquals(WakeLoop.Decision.Question("왜 그래야 해?", viaWake = false), l.onUtterance("왜 그래야 해?", 7_000))
        assertEquals(1, l.wakes)
        assertEquals(1, l.followUps)
    }

    @Test fun followUpWindowExpires() {
        val l = WakeLoop(cfg).apply { start() }
        l.onSpeaking(true, 0); l.onSpeaking(false, 1_000)
        assertEquals(0L, l.waitBeforeListen(8_000))
        assertEquals(WakeState.LISTENING, l.state)
        assertEquals(WakeLoop.Decision.Ignore, l.onUtterance("왜 그래야 해?", 8_100))
    }

    @Test fun silenceInFollowUpKeepsWindowUntilItEnds() {
        val l = WakeLoop(cfg).apply { start() }
        l.onSpeaking(true, 0); l.onSpeaking(false, 1_000)
        assertEquals(WakeLoop.Decision.Ignore, l.onUtterance(null, 3_000))
        assertTrue(l.inFollowUp(3_000))
        assertEquals(4_000L, l.followUpLeft(3_000))
    }

    @Test fun chatterWithoutWakeIsIgnored() {
        val l = WakeLoop(cfg).apply { start() }
        assertEquals(WakeLoop.Decision.Ignore, l.onUtterance("어제 회사 선배랑 밥 먹었어", 1_000))
        assertEquals(WakeState.LISTENING, l.state)
    }

    @Test fun gleoBacksOffThenResumes() {
        val l = WakeLoop(cfg).apply { start() }
        assertEquals(WakeLoop.Decision.Gleo, l.onUtterance("글레오 음악 틀어 줘", 1_000))
        assertEquals(WakeState.BACKED_OFF, l.state)
        assertEquals(10_000L, l.waitBeforeListen(1_000))
        assertEquals(0L, l.waitBeforeListen(11_000))
        assertEquals(WakeState.LISTENING, l.state)
    }

    @Test fun interferenceBacksOff() {
        val l = WakeLoop(cfg).apply { start() }
        l.onInterference(2_000)
        assertEquals(5_000L, l.waitBeforeListen(2_000))
        assertEquals(1, l.interferenceBackoffs)
    }

    @Test fun errorsBackOffExponentiallyAndResetOnSuccess() {
        val l = WakeLoop(cfg).apply { start() }
        l.onError(0); assertEquals(1_000L, l.waitBeforeListen(0))
        l.onError(1_000); assertEquals(2_000L, l.waitBeforeListen(1_000))
        l.onError(3_000); assertEquals(4_000L, l.waitBeforeListen(3_000))
        l.onError(7_000); assertEquals(8_000L, l.waitBeforeListen(7_000))
        l.onError(15_000); assertEquals("상한", 8_000L, l.waitBeforeListen(15_000))
        l.onUtterance(null, 30_000)
        l.onError(30_000); assertEquals("성공 뒤 처음부터", 1_000L, l.waitBeforeListen(30_000))
    }

    @Test fun weakVariantFollowsConfig() {
        assertEquals(WakeLoop.Decision.Ignore, WakeLoop(cfg).apply { start() }.onUtterance("나비야 와이퍼 어디 있어?", 0))
        val loose = WakeLoop(cfg.copy(allowWeakWakeWords = true)).apply { start() }
        assertEquals(WakeLoop.Decision.Question("와이퍼 어디 있어?", viaWake = true), loose.onUtterance("나비야 와이퍼 어디 있어?", 0))
    }

    @Test fun stopTurnsEverythingOff() {
        val l = WakeLoop(cfg).apply { start() }
        l.onError(0)
        l.stop()
        assertEquals(WakeState.OFF, l.state)
        assertEquals(-1L, l.waitBeforeListen(0))
        assertEquals(WakeLoop.Decision.Ignore, l.onUtterance("타비야", 0))
    }
}

class WakeLiveTallyTest {
    @Test fun linesAndSummaryShowBothRules() {
        val t = WakeLiveTally()
        assertEquals("#1 깨어남 '탑이야 와이퍼 어디 있어?' → 질문 '와이퍼 어디 있어?'", t.record("탑이야 와이퍼 어디 있어?").line)
        assertEquals("#2 안 깨어남 '나비야 와이브 어디 있어?' (나비야 규칙이면 깨어남)", t.record("나비야 와이브 어디 있어?").line)
        assertEquals("#3 깨어남 '타비야'", t.record("타비야").line)
        assertEquals("#4 Gleo로 봄 '헤이 클레어'", t.record("헤이 클레어").line)
        assertEquals("#5 안 깨어남 '오늘 날씨 좋다'", t.record("오늘 날씨 좋다").line)
        assertEquals("깨어남 2/5 (나비야 규칙을 켜면 3/5)", t.summary())
        assertEquals(5, t.lines().size)
    }

    @Test fun keepsOnlyRecentLines() {
        val t = WakeLiveTally()
        repeat(15) { t.record("타비야 $it") }
        assertEquals(10, t.lines().size)
        assertTrue(t.lines().first().startsWith("#6 "))
    }
}
