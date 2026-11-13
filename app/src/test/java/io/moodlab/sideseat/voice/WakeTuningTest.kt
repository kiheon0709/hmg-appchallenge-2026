package io.moodlab.sideseat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 11-13 실측(dev-integration da5fe02) 결과로 맞춘 호출어·Gleo·되묻기·추측 타이머 규칙. */
class WakeTuningTest {

    // ---------- 실측 40문장(STT가 실제로 받아 적은 글) ----------
    private val samples: List<Triple<String, String, Boolean>> by lazy {
        File("../docs/screens/sim/logs/wake_40_results.tsv").readLines().filter { it.isNotBlank() }.map { line ->
            val c = line.split('\t')
            Triple(c[0], c[4], c[1] == "wake") // (번호, 받아 적은 글, 호출어를 말했나)
        }
    }

    @Test fun realTranscriptsScoreImprovesWithoutFalseAlarms() {
        val score = WakeMatcher().score(samples.map { it.second to it.third })
        // 수정 전 10/20·0/20. 받아 적은 글 기준으로 다시 채점하면 놓침은 "저기 근데"·"대학생 때"·"300 …무역 계약을" 3개뿐.
        assertEquals("$score", 0, score.falseAlarm)
        assertEquals(20, score.plainTotal)
        assertTrue("$score", score.wakeHit >= 17)
    }

    @Test fun commonWordVariantsOnlyAtStartWithRequestShape() {
        val m = WakeMatcher()
        assertEquals(WakeMatcher.Result.Wake("안개등 어떻게 켜?"), m.detect("선택 안개등 어떻게 켜?"))
        assertEquals(WakeMatcher.Result.Wake("타이어 경고등이 떴어"), m.detect("300 타이어 경고등이 떴어"))
        assertEquals("뒤가 질문 꼴이 아니면 아님", WakeMatcher.Result.None, m.detect("선택 잘했네"))
        assertEquals("붙어 있는 낱말", WakeMatcher.Result.None, m.detect("300원만 줘"))
        assertEquals("붙어 있는 낱말", WakeMatcher.Result.None, m.detect("선택지가 어디 있어?"))
        assertEquals("맨 앞이 아니면 아님", WakeMatcher.Result.None, m.detect("오늘 선택 어떻게 해?"))
        assertEquals("앞말이 붙으면 일상어 변형은 아님", WakeMatcher.Result.None, m.detect("저기 선택 어떻게 해?"))
        assertTrue("중간 결과 판정은 강한 호출어만", !m.startsWithWake("선택 안개등"))
    }

    @Test fun gleoLooseAfterHey() {
        val m = WakeMatcher()
        assertEquals(WakeMatcher.Result.Gleo, m.detect("헤이 클레어 오늘 날씨 어때"))   // 실측
        assertEquals(WakeMatcher.Result.Gleo, m.detect("클레오 음악 틀어 줘"))            // 실측
        assertEquals(WakeMatcher.Result.Gleo, m.detect("하이 글래오"))
        assertEquals(WakeMatcher.Result.Gleo, m.detect("헤이 그리오 집으로"))
        assertEquals(WakeMatcher.Result.None, m.detect("헤이 친구 뭐 해"))
        assertEquals(WakeMatcher.Result.None, m.detect("아까 클레어가 그러던데"))
    }

    @Test fun candidateNameCanBeScored() {
        val m = WakeMatcher(wakeWords = listOf("하늘", "하눌"), weakWakeWords = emptyList())
        val s = m.score(listOf("하늘 와이퍼 어디야" to true, "하눌 졸려" to true, "아늘 비 와" to true, "하늘이 맑다" to false))
        assertEquals(2, s.wakeHit)
        assertEquals(1, s.falseAlarm) // "하늘이 맑다": 이름이 일상어면 오인식이 난다 → 후보를 고를 때 이 수치를 본다
    }

    // ---------- 되묻기 창 문턱 ----------
    @Test fun followUpRejectsRoomNoise() {
        val l = WakeLoop()
        listOf("한숨만", "지금 요즘으로", "시원한", "음", "라디오 소리").forEach { assertFalse(it, l.acceptsFollowUp(it)) } // 실측 방 소리 포함
        listOf("왜 그래야 해?", "응", "아니", "다시 말해 줘", "그럼 외기는 어떻게 해", "그 버튼 어디 있어").forEach { assertTrue(it, l.acceptsFollowUp(it)) }
    }

    @Test fun followUpNoiseIsCountedAndIgnored() {
        val l = WakeLoop().apply { start() }
        l.onSpeaking(true, 0); l.onSpeaking(false, 1_000)
        assertEquals(WakeLoop.Decision.Ignore, l.onUtterance("한숨만", 2_000))
        assertEquals(1, l.followUpRejected)
        assertTrue("창은 그대로", l.inFollowUp(2_000))
    }

    // ---------- 추측 타이머(듣기 루프 헛돌기 회귀) ----------
    @Test fun disabledGuesserNeverArms() {
        val g = SpeechEndGuesser(enabled = false)
        assertNull(g.onPartial("선배 와이퍼 어", 0))
        assertNull("연속 청취에서는 타이머가 없어야 루프가 events를 기다린다", g.dueIn(1_000))
        assertNull(g.fire())
    }

    @Test fun fireAlwaysDisarmsEvenWhenGuessRejected() {
        val g = SpeechEndGuesser(enabled = true, maxGuesses = 1)
        g.onPartial("외기는 왜", 0)
        assertEquals(450L, g.dueIn(0))
        assertEquals("외기는 왜", g.fire())
        assertNull("한 번 쏘면 풀린다", g.dueIn(10_000))
        g.onPartial("외기는 왜 바꿔야", 1_000)             // 새 글 → 다시 걸림
        assertTrue("다 됨", g.dueIn(1_450)!! <= 0)
        assertNull("횟수 초과로 버려도", g.fire())
        assertNull("타이머는 풀려야 한다(11-13 버그: 여기서 계속 0이라 루프가 헛돎)", g.dueIn(99_999))
    }

    @Test fun sameTextAfterGuessDoesNotRearm() {
        val g = SpeechEndGuesser(enabled = true)
        g.onPartial("외기는 왜", 0)
        g.fire()
        g.onPartial("외기는왜", 500) // 띄어쓰기만 다른 같은 글이 다시 옴
        assertNull(g.dueIn(1_000))
    }

    @Test fun stableRepeatGuessesImmediatelyAndCompleteLooksShorter() {
        val g = SpeechEndGuesser(enabled = true)
        assertNull(g.onPartial("너의 의기로", 0))
        assertEquals(450L, g.dueIn(0))
        assertEquals("너의 의기로", g.onPartial("너의 의기로", 400))
        g.onPartial("너의 의기로 바꿔야 해", 800)
        assertEquals("끝난 문장처럼 보이면 220ms", 220L, g.dueIn(800))
        g.stop()
        assertNull(g.dueIn(2_000))
    }
}
