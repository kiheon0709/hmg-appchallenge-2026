package io.moodlab.sideseat.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiniGameTest {

    // ---------- M1 금고 다이얼 ----------

    private class Dial(val game: SafeDialGame = DefaultSafeDialGame()) {
        var now = 0L
        val events = mutableListOf<CoachEvent>()
        lateinit var view: SafeDialView

        init {
            game.start(0)
            tick(0f)
        }

        fun tick(angle: Float?) {
            val (v, e) = game.tick(angle, now)
            view = v
            events += e
        }

        /** [from]에서 [to]까지 [ms] 동안 고르게 돌린다(50ms 간격). */
        fun turn(from: Float, to: Float, ms: Long) {
            val steps = (ms / 50).toInt()
            for (i in 1..steps) {
                now += 50
                tick(from + (to - from) * i / steps)
            }
        }

        fun hold(angle: Float, ms: Long) {
            val end = now + ms
            while (now < end) {
                now += 50
                tick(angle)
            }
        }

        fun clicks() = events.count { it == CoachEvent.Sfx("click") }
        fun says() = events.filterIsInstance<CoachEvent.Say>().map { it.text }
    }

    @Test
    fun `default dial follows the spec`() {
        assertEquals(listOf(90, -45, 120), SafeDialConfig().targetsDeg)
    }

    @Test
    fun `safe opens after three numbers in order`() {
        val d = Dial()
        assertEquals("핸들로 금고를 열어 볼까요? 첫 번호는 오른쪽 90도예요.", d.says().first())
        d.turn(0f, 90f, 1_000)
        d.hold(90f, 800)
        assertEquals(1, d.view.solved)
        assertTrue(d.events.contains(CoachEvent.Sfx("unlock")))
        assertTrue("철컥! 다음은 왼쪽 45도예요." in d.says())

        d.turn(90f, -45f, 1_500)
        d.hold(-44f, 800)
        assertEquals(2, d.view.solved)
        d.turn(-44f, 120f, 1_500)
        d.hold(121f, 800)
        assertTrue(d.view.done)
        assertTrue(d.events.contains(CoachEvent.Sfx("success")))
        assertTrue(d.says().last().startsWith("열렸어요!"))

        // 다 연 뒤에는 더 돌려도 그대로
        d.turn(121f, 0f, 500)
        assertTrue(d.view.done)
        assertEquals(121f, d.view.currentDeg)
    }

    @Test
    fun `passing through the number does not unlock`() {
        val d = Dial()
        d.turn(0f, 180f, 600)
        d.turn(180f, 0f, 600)
        assertEquals(0, d.view.solved)
    }

    @Test
    fun `clicks get denser near the number and closeness rises`() {
        val far = Dial()
        far.turn(-150f, -90f, 1_000) // 목표(90)에서 멀리 60도
        val near = Dial()
        near.turn(60f, 100f, 1_000) // 목표 근처 40도
        assertTrue(near.clicks() > far.clicks() * 2, "far=${far.clicks()} near=${near.clicks()}")

        val d = Dial()
        d.tick(0f)
        val c0 = d.view.closeness
        d.tick(80f)
        assertTrue(d.view.closeness > c0)
        assertTrue(d.view.closeness > 0.9f)
    }

    @Test
    fun `hold progress fills while resting on the number`() {
        val d = Dial()
        d.turn(0f, 90f, 500)
        d.hold(90f, 350)
        assertTrue(d.view.holdProgress in 0.3f..0.7f)
    }

    @Test
    fun `missing steering signal is reported once`() {
        val d = Dial()
        d.tick(null)
        d.tick(null)
        assertEquals(1, d.says().count { it.startsWith("핸들 각도를 읽을 수 없어요") })
        assertFalse(d.view.done)
    }

    // ---------- M2 깜빡이 모스 ----------

    private class Morse(seed: Int, rounds: Int = 2) {
        val game = DefaultTurnSignalGame(TurnSignalGameConfig(random = Random(seed), maxRounds = rounds))
        var now = 0L
        val events = mutableListOf<CoachEvent>()
        lateinit var view: TurnSignalGameView
        var signal: TurnSignal = TurnSignal.OFF

        init {
            game.start(0)
            tick()
        }

        fun tick() {
            val (v, e) = game.tick(signal, now)
            view = v
            events += e
        }

        fun pass(ms: Long) {
            val end = now + ms
            while (now < end) {
                now += 50
                tick()
            }
        }

        /** 레버를 올렸다(켜짐 0.5초) 내린다. */
        fun flick(s: TurnSignal) {
            signal = s
            pass(500)
            signal = TurnSignal.OFF
            pass(300)
        }

        fun untilInput() {
            while (view.phase != MiniGamePhase.INPUT) pass(50)
        }
    }

    @Test
    fun `morse rounds grow and clear when copied`() {
        val m = Morse(seed = 3)
        assertEquals(MiniGamePhase.SHOW, m.view.phase)
        assertEquals(3, m.view.pattern.size)
        m.untilInput()
        m.view.pattern.forEach { m.flick(it) }
        assertEquals(1, m.view.score)
        m.pass(1_600)
        assertEquals(2, m.view.round)
        assertEquals(4, m.view.pattern.size)
        m.untilInput()
        m.view.pattern.forEach { m.flick(it) }
        assertEquals(MiniGamePhase.DONE, m.view.phase)
        assertEquals(2, m.view.score)
    }

    @Test
    fun `morse ends on the wrong side or timeout`() {
        val m = Morse(seed = 5)
        m.untilInput()
        val wrong = if (m.view.pattern[0] == TurnSignal.LEFT) TurnSignal.RIGHT else TurnSignal.LEFT
        m.flick(wrong)
        assertEquals(MiniGamePhase.OVER, m.view.phase)
        assertTrue(m.events.contains(CoachEvent.Sfx("fail")))

        val t = Morse(seed = 5)
        t.untilInput()
        t.pass(4_100)
        assertEquals(MiniGamePhase.OVER, t.view.phase)
    }

    @Test
    fun `lever left on during show is not an input and changing sides counts`() {
        val m = Morse(seed = 9)
        m.signal = TurnSignal.LEFT
        m.untilInput()
        assertEquals(0, m.view.inputCount)
        // 곧바로 반대쪽으로 넘기는 것도 입력이다
        val first = m.view.pattern[0]
        if (first == TurnSignal.LEFT) {
            m.signal = TurnSignal.OFF
            m.pass(100)
        }
        m.signal = first
        m.pass(100)
        assertEquals(1, m.view.inputCount)
    }

    // ---------- M3 창문 버저 ----------

    private class Buzzer {
        val game = DefaultWindowBuzzerGame()
        var now = 0L
        val events = mutableListOf<CoachEvent>()
        lateinit var view: WindowBuzzerView
        var windows = mapOf(Seat.DRIVER to 0, Seat.PASSENGER to 0, Seat.REAR_LEFT to 0, Seat.REAR_RIGHT to 0)

        init {
            game.start(0)
            tick()
        }

        fun tick() {
            val (v, e) = game.tick(windows, now)
            view = v
            events += e
        }

        fun pass(ms: Long) {
            val end = now + ms
            while (now < end) {
                now += 100
                tick()
            }
        }

        fun says() = events.filterIsInstance<CoachEvent.Say>().map { it.text }
    }

    @Test
    fun `first window to open buzzes and the answer scores`() {
        val b = Buzzer()
        assertTrue(b.says().last().startsWith("1번 문제. 앞유리 안쪽에 김이 서리면"))
        b.windows = b.windows + (Seat.REAR_LEFT to 10) + (Seat.PASSENGER to 3)
        b.tick()
        assertEquals(BuzzerPhase.BUZZED, b.view.phase)
        assertEquals(Seat.REAR_LEFT, b.view.buzzedSeat)
        assertEquals(ReplyKind.CHOICE, b.events.filterIsInstance<CoachEvent.Say>().last().expectReply)
        b.game.answer("성에 제거", b.now)
        b.tick()
        assertEquals(true, b.view.lastCorrect)
        assertEquals(mapOf(Seat.REAR_LEFT to 1), b.view.scores)

        // 다음 문제: 이미 열린 창문은 닫았다 다시 열어야 버저
        b.pass(3_100)
        assertEquals(1, b.view.questionIndex)
        b.pass(500)
        assertEquals(BuzzerPhase.ASK, b.view.phase)
        b.windows = b.windows + (Seat.REAR_LEFT to 0)
        b.tick()
        b.windows = b.windows + (Seat.REAR_LEFT to 8)
        b.tick()
        assertEquals(Seat.REAR_LEFT, b.view.buzzedSeat)
        b.game.answer("1번", b.now) // 정답은 2번(절반으로)
        b.tick()
        assertEquals(false, b.view.lastCorrect)
    }

    @Test
    fun `quiz ends with a winner`() {
        val b = Buzzer()
        repeat(5) { i ->
            val seat = if (i < 3) Seat.DRIVER else Seat.PASSENGER
            b.windows = b.windows.mapValues { 0 }
            b.tick()
            b.windows = b.windows + (seat to 20)
            b.tick()
            val q = WindowBuzzerConfig.DEFAULT_QUESTIONS[i]
            b.game.answer("${q.correct + 1}번", b.now)
            b.pass(3_100)
        }
        assertEquals(BuzzerPhase.DONE, b.view.phase)
        assertEquals(mapOf(Seat.DRIVER to 3, Seat.PASSENGER to 2), b.view.scores)
        assertEquals("운전석 우승! 3문제 맞혔어요.", b.says().last())
    }

    @Test
    fun `no answer in time counts as wrong`() {
        val b = Buzzer()
        b.windows = b.windows + (Seat.DRIVER to 30)
        b.tick()
        b.pass(10_000)
        assertEquals(false, b.view.lastCorrect)
        assertTrue(b.view.scores.isEmpty())
    }

    @Test
    fun `every quiz question has a parsable correct option`() {
        WindowBuzzerConfig.DEFAULT_QUESTIONS.forEach { q ->
            assertEquals(q.correct, q.match(q.options[q.correct].text))
            assertEquals(q.correct, q.match("${q.correct + 1}번"))
        }
    }
}
