package io.moodlab.sideseat.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 길 위의 규칙(C5) 네 상황과, 앞이 안 보이는 상황의 공통 절차(감속 → 비상등 확인 → 그 상황의 조작). */
class RoadRulesTest {

    private fun driver(config: TrainingConfig = TrainingConfig(), state: VehicleState = baseState): TrainingDriver {
        val d = TrainingDriver(DefaultTrainingEngine(config), state)
        d.tick()
        return d
    }

    private fun active(id: SituationId, config: TrainingConfig = TrainingConfig()): TrainingDriver {
        val d = driver(config)
        d.engine.inject(id, d.now)
        d.untilActive(id)
        return d
    }

    @Test
    fun `right turn at a crosswalk needs a full stop with the brake`() {
        val d = active(SituationId.RIGHT_TURN_CROSSWALK)
        assertEquals("CROSSWALK", d.view.scene.sign)
        d.advance(1_000)
        assertEquals(TrainingPhase.ACTIVE, d.view.phase) // 밟지 않으면 멈추지 않는다
        assertEquals(TrainingConfig().slowApproachKmh, d.view.virtualSpeedKmh, 1f) // 우회전하러 천천히 들어선다
        d.set { it.copy(brakePressed = true) }
        d.until { it.phase == TrainingPhase.SUCCESS }
        assertTrue(d.view.virtualSpeedKmh <= 0.5f)
        assertEquals(3, d.view.lastResult?.stars)
        // 멈춘 뒤 발을 떼도 상황이 끝날 때까지 서 있는다
        d.set { it.copy(brakePressed = false) }
        d.advance(1_000)
        assertEquals(0f, d.view.virtualSpeedKmh)
    }

    @Test
    fun `merging counts the steering only after the turn signal has been on for a while`() {
        val d = active(SituationId.HIGHWAY_MERGE)
        assertEquals(listOf("signal_left", "steer_left"), d.view.steps.map { it.id })
        d.set { it.copy(turnSignal = TurnSignal.LEFT) }
        d.advance(1_000)
        d.set { it.copy(steeringAngleDeg = -12f) }
        d.advance(1_000)
        assertEquals(listOf(false, false), d.view.steps.map { it.done }) // 깜빡이를 켠 지 얼마 안 됐다
        d.until { it.phase == TrainingPhase.SUCCESS }
        assertEquals(emptyList(), d.view.lastResult?.penalties)
        assertEquals(3, d.view.lastResult?.stars)
        assertTrue((d.view.lastResult?.reactionMs ?: 0) >= TrainingConfig().mergeSignalHoldMs)
    }

    @Test
    fun `steering without the turn signal is a penalty when merging`() {
        val d = active(SituationId.HIGHWAY_MERGE)
        d.clearEvents()
        d.set { it.copy(steeringAngleDeg = -12f) }
        assertTrue("깜빡이를 먼저 켜고 들어가요." in d.says())
        d.set { it.copy(turnSignal = TurnSignal.LEFT) }
        d.until { it.phase == TrainingPhase.SUCCESS }
        assertEquals(listOf("STEER_NO_SIGNAL"), d.view.lastResult?.penalties)
        assertEquals(1, d.view.lastResult?.stars)
    }

    @Test
    fun `hydroplaning is passed by not braking hard and keeping the wheel straight`() {
        val d = active(SituationId.HYDROPLANING)
        assertTrue(d.view.scene.rain > 0f)
        d.advance(3_900)
        assertEquals(TrainingPhase.ACTIVE, d.view.phase)
        assertTrue(d.view.scene.slip > 0.3f)
        d.advance(200)
        assertEquals(TrainingPhase.SUCCESS, d.view.phase)
        assertEquals(3, d.view.lastResult?.stars)
        assertNull(d.view.lastResult?.reactionMs) // 버티는 상황은 걸린 시간을 재지 않는다
        assertTrue("잘 버텼어요. 급하게 밟지 않고 핸들을 곧게 잡는 게 먼저예요." in d.says())
        assertTrue(d.says().any { it.endsWith("급하게 밟지 말고 발을 떼고 핸들을 곧게 잡아요.") })
    }

    @Test
    fun `braking while hydroplaning costs stars but is not a failure and steering restarts the hold`() {
        // 세기를 읽을 수 없어 밟으면 감점만 한다. 버티는 시간은 그대로 센다
        val d = active(SituationId.HYDROPLANING)
        d.advance(2_000)
        d.clearEvents()
        d.set { it.copy(brakePressed = true) }
        assertTrue("급하게 밟지 말고 발을 떼요. 핸들은 곧게 잡아요." in d.says())
        d.advance(500)
        d.set { it.copy(brakePressed = false) }
        d.advance(1_400)
        assertEquals(TrainingPhase.ACTIVE, d.view.phase)
        d.advance(200)
        assertEquals(TrainingPhase.SUCCESS, d.view.phase)
        assertEquals(listOf("HYDRO_BRAKE"), d.view.lastResult?.penalties)
        assertEquals(1, d.view.lastResult?.stars)

        // 끝까지 밟고 있어도 실패(★0)가 아니라 감점(★1)이다
        val held = active(SituationId.HYDROPLANING)
        held.set { it.copy(brakePressed = true) }
        held.until { it.phase == TrainingPhase.SUCCESS || it.phase == TrainingPhase.FAIL }
        assertEquals(TrainingPhase.SUCCESS, held.view.phase)
        assertEquals(1, held.view.lastResult?.stars)

        val s = active(SituationId.HYDROPLANING)
        s.set { it.copy(steeringAngleDeg = 25f) }
        s.set { it.copy(steeringAngleDeg = 0f) }
        s.until { it.phase == TrainingPhase.SUCCESS }
        assertEquals(listOf("HYDRO_STEER"), s.view.lastResult?.penalties)
    }

    @Test
    fun `wrong hi-pass lane is a two question quiz`() {
        val d = active(SituationId.HIPASS_WRONG_LANE)
        assertEquals("HIPASS", d.view.scene.sign)
        assertEquals("어떻게 할까요?", d.view.choice?.question)
        d.engine.answer("차를 세우고 후진해요", d.now)
        d.tick()
        assertEquals(listOf(false, false), d.view.steps.map { it.done })
        d.engine.answer("1번", d.now)
        d.tick()
        assertEquals("그럼 요금은 어떻게 할까요?", d.view.choice?.question)
        d.engine.answer("나중에 정산해요", d.now)
        d.until { it.phase == TrainingPhase.SUCCESS }
        assertEquals(2, d.view.lastResult?.stars) // 한 번 틀림
    }

    @Test
    fun `road rules chapter is playable but does not count toward this car's license`() {
        val d = driver()
        assertEquals(listOf("C1", "C2", "C3", "C4"), TrainingConfig().licenseChapters)
        d.engine.start("C5", d.now)
        var guard = 0
        while (true) {
            val v = d.until { it.phase == TrainingPhase.BRIEFING || it.phase == TrainingPhase.CHAPTER_DONE }
            if (v.phase == TrainingPhase.CHAPTER_DONE) break
            val id = v.situation!!
            d.set { baseState }
            d.untilActive(id)
            d.set { solveAll(it, id) }
            while (d.until { it.choice != null || it.phase == TrainingPhase.SUCCESS }.choice != null) {
                d.engine.answer("1", d.now)
                d.tick()
            }
            d.until { it.phase != TrainingPhase.SUCCESS }
            check(++guard < 10)
        }
        val res = assertNotNull(d.view.chapterResult)
        assertEquals(
            listOf(SituationId.RIGHT_TURN_CROSSWALK, SituationId.HIGHWAY_MERGE, SituationId.HYDROPLANING, SituationId.HIPASS_WRONG_LANE),
            res.results.map { it.id },
        )
        assertTrue(res.results.all { it.stars == 3 }, "${res.results}")
        assertEquals(12, d.engine.progress().chapterBestStars["C5"])
        assertFalse(d.engine.progress().licenseFor("avante").licensed)
        assertTrue(d.engine.progress().licenseFor("avante").clearedChapters.isEmpty())
    }

    @Test
    fun `saved progress from before the new situations still loads`() {
        val old = TrainingProgress(bestStars = mapOf("HEAVY_RAIN" to 3), chapterBestStars = mapOf("C1" to 7))
        val d = driver()
        d.engine.restore(old)
        assertEquals(3, d.engine.progress().bestStars["HEAVY_RAIN"])
        assertEquals(5, d.engine.chapters().size)
    }

    // ---------- 앞이 안 보이는 상황의 공통 절차 ----------

    @Test
    fun `low visibility situations start with slowing down`() {
        for (id in listOf(SituationId.HEAVY_RAIN, SituationId.FOGGED_WINDSHIELD, SituationId.DENSE_FOG)) {
            val d = active(id)
            assertEquals("slow_down", d.view.steps.first().id, "$id")
            assertTrue(d.says().last().endsWith("먼저 브레이크를 나눠서 지그시 밟아 속도를 줄여요."), "$id ${d.says().last()}")
            d.slowDownFirst()
            assertTrue(d.view.steps.first().done)
            // 폭우·안개는 절반(시행규칙 제19조), 김서림은 줄이기만
            val limit = if (id == SituationId.FOGGED_WINDSHIELD) 64f else 40f
            assertTrue(d.view.virtualSpeedKmh <= limit, "$id ${d.view.virtualSpeedKmh}")
            assertEquals(if (id == SituationId.FOGGED_WINDSHIELD) "속도 줄이기" else "속도 절반으로 줄이기", d.view.steps.first().label)
        }
    }

    @Test
    fun `hazard confirmation is asked right after slowing down`() {
        val d = active(SituationId.HEAVY_RAIN)
        assertEquals(listOf("slow_down", "hazard_confirm", "wiper_fast", "low_beam_on"), d.view.steps.map { it.id })
        assertNull(d.view.choice) // 감속이 먼저다
        // 순서를 건너뛰어 와이퍼부터 켜도 그 단계는 인정된다
        d.set { it.copy(wiper = WiperLevel.HIGH) }
        assertEquals(listOf(false, false, true, false), d.view.steps.map { it.done })
        d.slowDownFirst(confirmHazard = false)
        assertEquals("비상등 켰나요?", d.view.choice?.question)
        d.engine.answer("아직이요", d.now)
        d.tick()
        assertFalse(d.view.steps[1].done)
        d.engine.answer("응", d.now)
        d.tick()
        assertTrue(d.view.steps[1].done)
        assertNull(d.view.choice)
        d.set { it.copy(lowBeamOn = true) }
        assertEquals(TrainingPhase.SUCCESS, d.view.phase)
        assertEquals(emptyList(), d.view.lastResult?.penalties) // "아직이요"는 감점하지 않는다
    }

    @Test
    fun `fogged windshield, dense fog and sudden stop also ask for the hazard lights after slowing`() {
        for (id in listOf(SituationId.FOGGED_WINDSHIELD, SituationId.DENSE_FOG)) {
            val d = active(id)
            assertEquals(listOf("slow_down", "hazard_confirm"), d.view.steps.take(2).map { it.id }, "$id")
        }
        val s = active(SituationId.SUDDEN_STOP)
        assertEquals(listOf("brake", "hazard_confirm"), s.view.steps.map { it.id })
        assertNull(s.view.choice)
        s.advance(600)
        s.set { it.copy(brakePressed = true) }
        assertEquals("비상등 켰나요?", s.view.choice?.question)
        // 밟은 뒤에는 1.5초 제한이 풀린다. 별은 브레이크 반응 시간으로만 매긴다
        s.advance(8_000)
        assertEquals(TrainingPhase.ACTIVE, s.view.phase)
        s.confirmHazard()
        assertEquals(TrainingPhase.SUCCESS, s.view.phase)
        assertEquals(3, s.view.lastResult?.stars)
        assertEquals(600L, s.view.lastResult?.reactionMs)
    }

    @Test
    fun `hazard step can be switched off in one place`() {
        val off = TrainingConfig(lowVisibilityHazardStep = false)
        assertEquals(listOf("slow_down", "wiper_fast", "low_beam_on"), active(SituationId.HEAVY_RAIN, off).view.steps.map { it.id })
        assertEquals(listOf("brake"), active(SituationId.SUDDEN_STOP, off).view.steps.map { it.id })
    }

    @Test
    fun `surprise mode keeps these situations but drops the spoken hazard check`() {
        val d = driver(TrainingConfig(random = kotlin.random.Random(3), cruiseMs = 100, firstCruiseMs = 100))
        d.engine.startSurprise(d.now)
        val seen = mutableSetOf<SituationId>()
        repeat(40) {
            val v = d.until { it.phase == TrainingPhase.BRIEFING }
            val id = v.situation!!
            seen += id
            d.set { setupFor(id, baseState) }
            d.untilActive(id)
            assertTrue(d.view.steps.none { it.id == "hazard_confirm" }, "$id")
            d.set { solveAll(it, id) }
            d.until { it.phase == TrainingPhase.CRUISE }
        }
        assertTrue(seen.containsAll(listOf(SituationId.HEAVY_RAIN, SituationId.FOGGED_WINDSHIELD, SituationId.DENSE_FOG, SituationId.SUDDEN_STOP)), "$seen")
    }

    @Test
    fun `braking slows the virtual speed gently except on a sudden stop`() {
        val d = active(SituationId.TUNNEL)
        val v0 = d.view.virtualSpeedKmh
        d.set { it.copy(brakePressed = true) }
        d.advance(1_000)
        assertEquals(14f, v0 - d.view.virtualSpeedKmh, 0.8f)

        val s = active(SituationId.SUDDEN_STOP)
        val s0 = s.view.virtualSpeedKmh
        s.set { it.copy(brakePressed = true) }
        s.advance(1_000)
        assertEquals(25f, s0 - s.view.virtualSpeedKmh, 1.3f)
    }
}
