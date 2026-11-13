package io.moodlab.sideseat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordDetectorTest {
    private fun d(s: String) = WakeWordDetector.detect(s)

    @Test fun wakeAtStartWithQuestionInSameUtterance() {
        assertEquals(WakeMatcher.Result.Wake("앞유리가 뿌예"), d("선배, 앞유리가 뿌예"))
        assertEquals(WakeMatcher.Result.Wake("안개등 어떻게 켜?"), d("선배 안개등 어떻게 켜?"))
        assertEquals(WakeMatcher.Result.Wake("이 경고등 뭐야"), d("선배님 이 경고등 뭐야"))
    }

    @Test fun wakeAlone() {
        assertEquals(WakeMatcher.Result.Wake(""), d("선배"))
        assertEquals(WakeMatcher.Result.Wake(""), d("선배!"))
    }

    @Test fun commonMisrecognitionsAndLeadingWord() {
        assertTrue(d("선베 터널 들어가") is WakeMatcher.Result.Wake)
        assertTrue(d("섬배 졸려") is WakeMatcher.Result.Wake)
        assertEquals(WakeMatcher.Result.Wake("비 와"), d("저기 선배 비 와"))
        assertEquals(WakeMatcher.Result.Wake("깜빡이 언제 켜"), d("야 선배, 깜빡이 언제 켜"))
    }

    @Test fun wakeInMiddleIsIgnored() {
        assertEquals(WakeMatcher.Result.None, d("우리 선배가 그러는데 비 온대"))
        assertEquals(WakeMatcher.Result.None, d("어제 회사 선배랑 밥 먹었어"))
        assertEquals(WakeMatcher.Result.None, d("오늘 날씨 좋다"))
        assertEquals(WakeMatcher.Result.None, d(""))
    }

    @Test fun gleoMeansBackOff() {
        assertEquals(WakeMatcher.Result.Gleo, d("글레오 음악 틀어 줘"))
        assertEquals(WakeMatcher.Result.Gleo, d("헤이 글레오"))
        assertEquals(WakeMatcher.Result.Gleo, d("클레오 집으로 안내해"))
        assertEquals(WakeMatcher.Result.Gleo, d("저기 글레오"))
        // 중간에 나온 글레오는 우리 판단과 무관
        assertEquals(WakeMatcher.Result.None, d("아까 글레오가 그러던데"))
    }

    @Test fun partialCheckIsCheap() {
        assertTrue(WakeWordDetector.startsWithWake("선배"))
        assertTrue(WakeWordDetector.startsWithWake("저기 선배 앞"))
        assertFalse(WakeWordDetector.startsWithWake("우리 선배"))
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
        val d = l.onUtterance("선배, 앞유리가 뿌예", now = 1_000)
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

    @Test fun stopTurnsEverythingOff() {
        val l = WakeLoop(cfg).apply { start() }
        l.onError(0)
        l.stop()
        assertEquals(WakeState.OFF, l.state)
        assertEquals(-1L, l.waitBeforeListen(0))
        assertEquals(WakeLoop.Decision.Ignore, l.onUtterance("선배", 0))
    }
}
