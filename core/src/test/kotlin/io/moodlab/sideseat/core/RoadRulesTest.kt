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
    fun `hydroplaning is passed by slowing down before the standing water and crossing it steadily`() {
        val c = TrainingConfig()
        val d = active(SituationId.HYDROPLANING)
        assertTrue(d.view.scene.rain > 0f)
        assertEquals(listOf("slow_down", "cross_steady"), d.view.steps.map { it.id })
        assertEquals(c.hydroApproachM, d.view.scene.puddleM!!, 3f)
        assertEquals(0f, d.view.scene.slip, 0.01f) // 물 고인 곳에 닿기 전에는 미끄러지지 않는다
        val cruise = d.view.virtualSpeedKmh
        // 비가 내려 노면이 젖어 있으면 20% 감속
        d.set { it.copy(brakePressed = true) }
        d.until { v -> v.steps.first { it.id == "slow_down" }.done }
        assertEquals(cruise * c.hydroSlowRatio, d.view.virtualSpeedKmh, 1.5f)
        assertTrue(d.view.scene.puddleM!! > 0f)
        d.set { it.copy(brakePressed = false) }
        // 물 위를 다 지나가야 끝난다
        d.until { it.scene.puddleM!! <= 0f }
        assertEquals(TrainingPhase.ACTIVE, d.view.phase)
        d.advance(1_000)
        assertTrue(d.view.scene.slip > 0.1f)
        d.until { it.phase == TrainingPhase.SUCCESS }
        assertTrue(d.view.scene.puddleM!! <= -c.hydroPuddleM)
        assertEquals(3, d.view.lastResult?.stars)
        assertNull(d.view.lastResult?.reactionMs) // 걸린 시간을 재지 않는다
        assertTrue("미리 줄였어요. 실제 도로에서는 훨씬 어려워요. 그래서 미리 줄이는 거예요." in d.says())
        assertTrue(d.says().none { "완벽" in it })
    }

    @Test
    fun `reaching the standing water without slowing down fails and rough inputs on the water are only remarked on`() {
        val d = active(SituationId.HYDROPLANING)
        d.until { it.phase == TrainingPhase.FAIL }
        assertTrue(d.view.scene.puddleM!! <= 0f)
        assertTrue(d.says().any { "물 고인 곳에 닿기 전에 미리 속도를 줄여요." in it }, d.says().toString())
        assertEquals(0, d.view.lastResult?.stars)

        // 줄이고 들어간 뒤 물 위에서 밟거나 꺾으면 한마디만 하고 감점하지 않는다
        val s = active(SituationId.HYDROPLANING)
        s.slowDownFirst(confirmHazard = false)
        s.until { it.scene.puddleM!! <= 0f }
        s.clearEvents()
        s.set { it.copy(steeringAngleDeg = 25f) }
        assertTrue("물 위에서는 급하게 밟거나 꺾지 않아요. 그대로 지나가요." in s.says())
        s.advance(300)
        assertTrue(s.view.scene.slip > 0.3f)
        s.set { it.copy(steeringAngleDeg = 0f, brakePressed = true) }
        assertEquals(1, s.says().count { "급하게 밟거나 꺾지 않아요" in it }) // 한 번만 말한다
        s.set { it.copy(brakePressed = false) }
        s.until { it.phase == TrainingPhase.SUCCESS }
        assertEquals(emptyList(), s.view.lastResult?.penalties)
        assertEquals(3, s.view.lastResult?.stars)
    }

    @Test
    fun `wrong hi-pass lane is a two question quiz`() {
        val d = active(SituationId.HIPASS_WRONG_LANE)
        assertEquals("HIPASS", d.view.scene.sign)
        assertEquals("어떻게 할까요?", d.view.choice?.question)
        d.engine.answer("차를 세우고 후진해요", d.now)
        d.tick()
        assertEquals(listOf(false, false), d.view.steps.map { it.done })
        d.engine.answer("멈추지 않고 그대로 지나가요", d.now)
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
            d.answerUntilSuccess(id)
            d.until { it.phase != TrainingPhase.SUCCESS }
            check(++guard < 10)
        }
        val res = assertNotNull(d.view.chapterResult)
        assertEquals(
            listOf(
                SituationId.RIGHT_TURN_CROSSWALK, SituationId.ROUNDABOUT_ENTRY, SituationId.SCHOOL_ZONE,
                SituationId.HIGHWAY_MERGE, SituationId.HYDROPLANING, SituationId.TOLL_BOOTH, SituationId.HIPASS_WRONG_LANE,
            ),
            res.results.map { it.id },
        )
        assertTrue(res.results.all { it.stars == 3 }, "${res.results}")
        assertEquals(21, d.engine.progress().chapterBestStars["C5"])
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
    fun `after slowing down Tabi only recommends the hazard lights without asking`() {
        val d = active(SituationId.HEAVY_RAIN)
        assertEquals(listOf("slow_down", "wiper_fast", "low_beam_on"), d.view.steps.map { it.id })
        // 순서를 건너뛰어 와이퍼부터 켜도 그 단계는 인정된다
        d.set { it.copy(wiper = WiperLevel.HIGH) }
        assertEquals(listOf(false, true, false), d.view.steps.map { it.done })
        d.clearEvents()
        d.slowDownFirst()
        assertNull(d.view.choice)
        assertEquals(1, d.says().count { it == "비상등도 켜 주세요." })
        d.set { it.copy(lowBeamOn = true) }
        assertEquals(TrainingPhase.SUCCESS, d.view.phase)
        assertTrue(d.says().none { "비상등 켰나요" in it || "여쭤봐요" in it })
    }

    @Test
    fun `fogged windshield, dense fog and sudden stop also recommend the hazard lights after slowing`() {
        for (id in listOf(SituationId.FOGGED_WINDSHIELD, SituationId.DENSE_FOG)) {
            val d = active(id)
            assertTrue(d.view.steps.none { it.id == "hazard_confirm" }, "$id")
            d.clearEvents()
            d.slowDownFirst()
            assertTrue("비상등도 켜 주세요." in d.says(), "$id ${d.says()}")
        }
        val s = active(SituationId.SUDDEN_STOP)
        assertEquals(listOf("brake"), s.view.steps.map { it.id })
        s.advance(600)
        s.set { it.copy(brakePressed = true) }
        // 브레이크만으로 성공. 별은 브레이크 반응 시간으로만 매긴다
        assertEquals(TrainingPhase.SUCCESS, s.view.phase)
        assertEquals(3, s.view.lastResult?.stars)
        assertEquals(600L, s.view.lastResult?.reactionMs)
        assertTrue("비상등도 켜 주세요." in s.says())
    }

    @Test
    fun `hazard step can be switched off in one place`() {
        val off = TrainingConfig(lowVisibilityHazardStep = false)
        val d = active(SituationId.HEAVY_RAIN, off)
        d.slowDownFirst()
        assertTrue(d.says().none { "비상등" in it }, "${d.says()}")
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
            // 회전교차로는 도는 차를 보낸 뒤 브레이크를 떼야 끝난다
            if (id == SituationId.ROUNDABOUT_ENTRY) d.answerUntilSuccess(id)
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

    // ---------- 장소가 다른 상황과 "장소 × 상황" ----------

    @Test
    fun `entering a roundabout means waiting at the yield line until the circulating car has passed`() {
        val c = TrainingConfig()
        val d = active(SituationId.ROUNDABOUT_ENTRY)
        assertEquals("ROUNDABOUT", d.view.scene.sign)
        assertEquals(ScenePlace.CITY, d.view.scene.place)
        assertEquals(listOf("signal_left", "yield_slow", "enter"), d.view.steps.map { it.id })
        assertEquals(c.roundaboutPassMs / 1000f, d.view.scene.ringPassSec!!, 0.2f)
        d.set { it.copy(turnSignal = TurnSignal.LEFT, brakePressed = true) }
        // 양보선 앞에 멈춰도 도는 차가 지나가기 전에는 끝나지 않는다
        d.until { it.virtualSpeedKmh <= 0.5f }
        if (d.view.scene.ringPassSec!! > 0f) assertEquals(listOf(true, false, false), d.view.steps.map { it.done })
        d.until { v -> v.steps.first { it.id == "yield_slow" }.done }
        // 도는 차가 진입 지점을 지나가고 1초 뒤에야 비었다고 본다
        assertEquals(-c.roundaboutClearAfterMs / 1000f, d.view.scene.ringPassSec!!, 0.1f)
        assertEquals(0f, d.view.virtualSpeedKmh, 0.5f)
        // 밟고 있는 동안은 들어가지 않는다
        d.advance(300)
        assertEquals(TrainingPhase.ACTIVE, d.view.phase)
        assertEquals(listOf(true, true, false), d.view.steps.map { it.done })
        // 비었으니 브레이크를 떼면 진입 → 성공
        d.set { it.copy(brakePressed = false) }
        assertEquals(TrainingPhase.SUCCESS, d.view.phase)
        assertEquals(3, d.view.lastResult?.stars)
        d.advance(1_000)
        assertEquals(15f, d.view.virtualSpeedKmh, 1f)
    }

    @Test
    fun `releasing the brake before the circulating car has passed fails the roundabout`() {
        val d = active(SituationId.ROUNDABOUT_ENTRY)
        d.set { it.copy(turnSignal = TurnSignal.LEFT, brakePressed = true) }
        d.advance(3_000)
        assertTrue(d.view.scene.ringPassSec!! > 0f)
        d.clearEvents()
        d.set { it.copy(brakePressed = false) }
        d.until { it.phase == TrainingPhase.FAIL }
        assertTrue(d.view.scene.ringPassSec!! > 0f, "도는 차가 지나가기 전에 실패해야 한다")
        assertTrue(d.says().any { "회전교차로 안에서 도는 차가 먼저예요" in it }, d.says().toString())
        assertEquals(0, d.view.lastResult?.stars)
        d.advance(1_000)
        assertEquals(0f, d.view.virtualSpeedKmh, 0.5f)
    }

    @Test
    fun `driving into the roundabout without braking fails before the circulating car has passed`() {
        val d = active(SituationId.ROUNDABOUT_ENTRY)
        d.set { it.copy(turnSignal = TurnSignal.LEFT) }
        d.until { it.phase == TrainingPhase.FAIL }
        assertTrue(d.view.scene.ringPassSec!! > 0f)
        assertTrue(d.says().any { "회전교차로 안에서 도는 차가 먼저예요" in it })
    }

    @Test
    fun `school zone needs slowing under the limit and a full stop at the unsignalled crosswalk`() {
        val d = active(SituationId.SCHOOL_ZONE)
        assertEquals("SCHOOL", d.view.scene.sign)
        assertEquals(ScenePlace.SCHOOL, d.view.scene.place)
        assertEquals(listOf("school_slow", "full_stop"), d.view.steps.map { it.id })
        d.advance(1_000)
        assertEquals(TrainingPhase.ACTIVE, d.view.phase) // 건너는 사람이 없어도 멈춰야 끝난다
        d.set { it.copy(brakePressed = true) }
        d.until { v -> v.steps.first().done }
        assertTrue(d.view.virtualSpeedKmh <= 30f)
        assertFalse(d.view.steps[1].done)
        d.until { it.phase == TrainingPhase.SUCCESS }
        assertEquals(0f, d.view.virtualSpeedKmh, 0.5f)
        assertTrue(SituationCatalog.all().first { it.id == SituationId.SCHOOL_ZONE }.lesson.contains("건너는 사람이 없어도"))
    }

    @Test
    fun `the place is picked per situation and kept until the next one starts`() {
        // 장소가 정해진 상황은 늘 그 장소다
        assertEquals(ScenePlace.CITY, active(SituationId.RIGHT_TURN_CROSSWALK).view.scene.place)
        assertEquals(ScenePlace.HIGHWAY, active(SituationId.TUNNEL).view.scene.place)
        // 같은 상황도 장소가 바뀐다: 폭우는 고속도로·도심·내리막
        val seen = mutableSetOf<String>()
        for (seed in 0 until 12) {
            val d = active(SituationId.HEAVY_RAIN, TrainingConfig(placeRandom = kotlin.random.Random(seed)))
            val place = d.view.scene.place
            seen += place
            d.advance(3_000)
            assertEquals(place, d.view.scene.place) // 상황 도중에는 바뀌지 않는다
        }
        assertEquals(setOf(ScenePlace.HIGHWAY, ScenePlace.CITY, ScenePlace.DOWNHILL), seen)
        // 한 곳으로 고정할 수도 있다
        val fixed = TrainingConfig(places = mapOf(SituationId.HEAVY_RAIN to listOf(ScenePlace.DOWNHILL)))
        assertEquals(ScenePlace.DOWNHILL, active(SituationId.HEAVY_RAIN, fixed).view.scene.place)
    }

    @Test
    fun `usual speed follows the place and slowing to half is judged against it`() {
        val city = TrainingConfig(places = mapOf(SituationId.HEAVY_RAIN to listOf(ScenePlace.CITY)))
        val d = active(SituationId.HEAVY_RAIN, city)
        assertEquals(ScenePlace.CITY, d.view.scene.place)
        d.advance(4_000)
        assertEquals(50f, d.view.virtualSpeedKmh, 0.5f) // 도심의 평소 속도
        d.set { it.copy(brakePressed = true) }
        d.until { v -> v.steps.first().done }
        assertEquals(25f, d.view.virtualSpeedKmh, 1.5f) // 도심에서는 절반이 25
        // 고속도로에서는 그대로 80과 40
        val hw = active(SituationId.HEAVY_RAIN, TrainingConfig(places = emptyMap()))
        assertEquals(80f, hw.view.virtualSpeedKmh, 0.5f)
        hw.slowDownFirst(confirmHazard = false)
        assertEquals(40f, hw.view.virtualSpeedKmh, 1.5f)
        // 보호구역은 들어서는 속도 그대로
        assertEquals(50f, TrainingConfig().cruiseKmhAt(ScenePlace.SCHOOL))
        assertEquals(80f, TrainingConfig().cruiseKmhAt(ScenePlace.DOWNHILL))
    }

    @Test
    fun `a chapter can start from the recommended situation and then runs the rest in order`() {
        val d = driver()
        d.engine.start("C2", d.now, SituationId.DENSE_FOG)
        d.tick()
        assertTrue(d.says().first().contains("짙은 안개부터"), d.says().first())
        val order = mutableListOf<SituationId>()
        while (true) {
            val v = d.until { it.phase == TrainingPhase.BRIEFING || it.phase == TrainingPhase.CHAPTER_DONE }
            if (v.phase == TrainingPhase.CHAPTER_DONE) break
            val id = v.situation!!
            if (order.lastOrNull() != id) order += id
            d.set { setupFor(id, baseState) }
            d.untilActive(id)
            d.set { solveAll(it, id) }
            d.answerUntilSuccess(id)
            d.until { it.phase != TrainingPhase.SUCCESS }
        }
        assertEquals(listOf(SituationId.DENSE_FOG, SituationId.TUNNEL, SituationId.ONCOMING_GLARE, SituationId.DROWSY), order)
        assertTrue("C2" in d.engine.progress().licenseFor("avante").clearedChapters) // 챕터 전체를 했으므로 그대로 인정된다

        // 그 챕터에 없는 상황이면 평소처럼 처음부터
        val other = driver()
        other.engine.start("C2", other.now, SituationId.HIPASS_WRONG_LANE)
        assertEquals(SituationId.TUNNEL, other.until { it.phase == TrainingPhase.BRIEFING }.situation)
        // 끊겼던 훈련이 있어도 추천으로 시작하면 새로 시작한다
        val r = driver()
        r.engine.start("C2", r.now)
        r.untilActive(SituationId.TUNNEL)
        r.engine.abort(r.now); r.tick()
        assertEquals("C2", r.engine.resumableChapter())
        r.engine.start("C2", r.now, SituationId.DROWSY)
        assertEquals(SituationId.DROWSY, r.until { it.phase == TrainingPhase.BRIEFING }.situation)
    }

    @Test
    fun `braking early at the toll lane creeps up to the booth before stopping`() {
        val d = active(SituationId.TOLL_BOOTH)
        d.set { it.copy(brakePressed = true) }
        d.advance(3_000)
        // 부스가 아직 앞이라 멈추지 않고 천천히 굴러간다
        assertFalse(d.view.steps[0].done)
        assertEquals(20f, d.view.virtualSpeedKmh, 1f)
        d.until(maxMs = 30_000) { v -> v.steps[0].done }
        assertEquals(0f, d.view.virtualSpeedKmh, 0.5f)
    }

    @Test
    fun `ordinary toll lane is stop, window down, take the ticket, window up`() {
        val d = active(SituationId.TOLL_BOOTH)
        assertEquals("TOLLBOOTH", d.view.scene.sign)
        assertEquals(listOf("full_stop", "window_open", "ticket_confirm", "window_close"), d.view.steps.map { it.id })
        // 멈추기 전에 내린 창문은 멈춘 뒤에 인정된다
        d.set { it.copy(driverWindowOpenPct = 40) }
        assertEquals(listOf(false, false, false, false), d.view.steps.map { it.done })
        assertNull(d.view.choice)
        d.set { it.copy(brakePressed = true) }
        d.until { v -> v.steps[1].done }
        assertEquals(0f, d.view.virtualSpeedKmh, 0.5f)
        assertEquals("통행권을 받아 주세요. 받았나요?", d.view.choice?.question)
        // 통행권을 받기 전에는 창문을 올려도 끝나지 않는다
        d.engine.answer("아직이요", d.now); d.tick()
        assertFalse(d.view.steps[2].done)
        d.engine.answer("응", d.now); d.tick()
        assertTrue(d.view.steps[2].done)
        assertEquals(TrainingPhase.ACTIVE, d.view.phase)
        d.advance(40_000)
        assertTrue(d.view.phase == TrainingPhase.ACTIVE || d.view.phase == TrainingPhase.HINT) // 시간 제한이 없다
        d.set { it.copy(driverWindowOpenPct = 0, brakePressed = false) }
        assertEquals(TrainingPhase.SUCCESS, d.view.phase)
        assertEquals(3, d.view.lastResult?.stars)
        assertEquals(emptyList(), d.view.lastResult?.penalties)
        assertTrue(SituationCatalog.all().first { it.id == SituationId.TOLL_BOOTH }.lesson.contains("들어갈 땐 통행권"))
        assertTrue(SituationId.TOLL_BOOTH in TrainingConfig().surpriseExclude)
    }

    // ---------- v25: 사이드미러 열선(하면 좋은 단계) ----------

    @Test
    fun `side mirror heater is an optional step that does not hold back success or stars`() {
        val car = baseState.copy(sideMirrorHeatOn = false)
        val d = driver(state = car)
        d.engine.inject(SituationId.HEAVY_RAIN, d.now)
        d.untilActive(SituationId.HEAVY_RAIN)
        assertTrue(d.view.steps.any { it.id == "mirror_heat" && it.label.startsWith("사이드미러 열선") && it.optional })
        d.advance(200)
        d.set { solveAll(it, SituationId.HEAVY_RAIN) }
        d.answerUntilSuccess(SituationId.HEAVY_RAIN)
        assertTrue(d.view.lastResult!!.stars >= 2)
        assertTrue(d.view.steps.none { it.id == "mirror_heat" && it.done })
        // 신호를 못 읽는 차에서는 단계가 아예 없다
        val none = driver()
        none.engine.inject(SituationId.FOGGED_WINDSHIELD, none.now)
        none.untilActive(SituationId.FOGGED_WINDSHIELD)
        assertTrue(none.view.steps.none { it.id == "mirror_heat" })
        // 켜면 체크된다
        val on = driver(state = car)
        on.engine.inject(SituationId.FOGGED_WINDSHIELD, on.now)
        on.untilActive(SituationId.FOGGED_WINDSHIELD)
        on.set { it.copy(sideMirrorHeatOn = true) }
        assertTrue(on.view.steps.first { it.id == "mirror_heat" }.done)
        assertEquals(TrainingPhase.ACTIVE, on.view.phase)
    }

    // ---------- v26: 회전교차로 녹화 자동 조작에서 나온 실패 ----------

    @Test
    fun `roundabout late brake still stops before the yield line and entering without the signal is a penalty not a failure`() {
        // 녹화 자동 조작: 1.5초에 깜빡이, 2.5초부터 브레이크 유지 → 이전에는 4.3초에 선을 넘어 실패했다
        val d = active(SituationId.ROUNDABOUT_ENTRY)
        d.advance(1_500)
        d.set { it.copy(turnSignal = TurnSignal.LEFT) }
        d.advance(1_000)
        d.set { it.copy(brakePressed = true) }
        d.until { it.phase != TrainingPhase.ACTIVE && it.phase != TrainingPhase.HINT || it.steps.first { s -> s.id == "yield_slow" }.done }
        assertTrue(d.view.steps.first { it.id == "yield_slow" }.done, "phase=${d.view.phase} ring=${d.view.scene.ringPassSec}")
        assertEquals(0f, d.view.virtualSpeedKmh, 0.5f)
        d.set { it.copy(brakePressed = false) }
        assertEquals(TrainingPhase.SUCCESS, d.view.phase)
        assertEquals(emptyList(), d.view.lastResult?.penalties)

        // 깜빡이를 안 켜고 (멈춰 보낸 뒤) 들어가면 실패가 아니라 감점
        val n = active(SituationId.ROUNDABOUT_ENTRY)
        n.set { it.copy(brakePressed = true) }
        n.until { v -> v.steps.first { it.id == "yield_slow" }.done }
        n.clearEvents()
        n.set { it.copy(brakePressed = false) }
        assertEquals(TrainingPhase.SUCCESS, n.view.phase)
        assertEquals(listOf("ROUNDABOUT_NO_SIGNAL"), n.view.lastResult?.penalties)
        assertTrue("들어가기 전에 왼쪽 깜빡이를 먼저 켜요." in n.says())
    }
}
