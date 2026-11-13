package io.moodlab.sideseat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 11-13 실측(dev-integration) 결과로 맞춘 호출어(타비)·Gleo·되묻기·추측 타이머 규칙. */
class WakeTuningTest {

    // ---------- 이름 후보 실측(합성 음성, STT가 실제로 받아 적은 글) ----------
    /** (PCM 파일, 호출어를 말했나, 받아 적은 글). wake_name_trial_order.tsv 순서대로 run1~4 로그의 #번호가 이어진다. */
    private val trial: List<Triple<String, Boolean, String>> by lazy {
        val dir = File("../docs/screens/sim/logs")
        val order = File(dir, "wake_name_trial_order.tsv").readLines().filter { it.isNotBlank() }.map { it.split('\t') }
        val heard = (1..4).flatMap { run ->
            File(dir, "wake_name_trial_run$run.log").readLines().mapNotNull { HEARD.find(it)?.groupValues?.get(1) }
        }
        assertEquals(order.size, heard.size)
        order.zip(heard) { o, h -> Triple(o[0], o[1] == "wake", h) }
    }

    @Test fun tabiTranscriptsWakeWithoutFalseAlarms() {
        val tabi = trial.filter { it.first.startsWith("n05_") }.map { it.third to true }
        val plain = trial.filter { !it.second }.map { it.third to false }
        assertEquals(12, tabi.size)
        assertEquals(80, plain.size)
        val strict = WakeMatcher().score(tabi + plain)
        // 탑이야 6 + 따비야 2. 나비야 3·아비야 1은 기본 규칙에서 놓침.
        assertEquals("$strict", 8, strict.wakeHit)
        assertEquals("$strict", 0, strict.falseAlarm)
        val loose = WakeMatcher(allowWeak = true).score(tabi + plain)
        // 나비야 + 질문 꼴 1개만 더 받음("나비야 와이브 어디 있어?").
        assertEquals("$loose", 9, loose.wakeHit)
        assertEquals("$loose", 0, loose.falseAlarm)
    }

    @Test fun otherNamesTranscriptsNeverWakeTabi() {
        val others = trial.filter { it.second && !it.first.startsWith("n05_") }.map { it.third to false }
        assertEquals(0, WakeMatcher(allowWeak = true).score(others).falseAlarm)
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
        val s = m.score(listOf("하늘아 와이퍼 어디야" to true, "하눌아 졸려" to true, "아늘아 비 와" to true, "하늘이 맑다" to false, "하늘 정말 맑다" to false))
        assertEquals(2, s.wakeHit)
        assertEquals("호칭 꼴 규칙이라 일상 문장은 안 깨어남", 0, s.falseAlarm)
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
        assertNull(g.onPartial("타비야 와이퍼 어", 0))
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

    private companion object {
        val HEARD = Regex("wake trial #\\d+ expect=\\w+ woke=\\w+ heard='(.*)'")
    }
}
