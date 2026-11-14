package io.moodlab.sideseat.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainingEngineTest {

    private fun driver(config: TrainingConfig = TrainingConfig(), state: VehicleState = baseState): TrainingDriver {
        val d = TrainingDriver(DefaultTrainingEngine(config), state)
        d.tick()
        return d
    }

    // ---------- 설정값 고정 ----------

    @Test
    fun `default numbers follow the spec`() {
        val c = TrainingConfig()
        assertEquals(16_000, c.limitOf(SituationId.HEAVY_RAIN))
        assertEquals(18_000, c.limitOf(SituationId.FOGGED_WINDSHIELD))
        assertEquals(listOf(700L, 1_000L, 1_500L), c.suddenStopStarsMs)
        assertEquals(6_000, c.limitOf(SituationId.TUNNEL))
        assertEquals(14_000, c.limitOf(SituationId.DENSE_FOG))
        assertTrue(c.lowVisibilityHazardStep) // 감속 다음 비상등 확인(사용자 결정)
        assertEquals(14f, c.brakeDecelKmhPerSec)
        assertEquals(5_000, c.limitOf(SituationId.ONCOMING_GLARE))
        assertEquals(0, c.limitOf(SituationId.WARNING_LIGHT)) // 말로 답하는 상황은 틀린 횟수로
        assertEquals(0, c.limitOf(SituationId.BREAKDOWN))
        assertEquals(3_000, c.brakeHoldMs)
        assertEquals(0.5f, c.fogSlowFraction)
        assertEquals(10_000, c.limitOf(SituationId.DROWSY))
        assertEquals(8_000, c.limitOf(SituationId.UNINTENDED_ACCEL))
        assertEquals(0, c.limitOf(SituationId.BEFORE_EXIT))
        assertEquals(0.5f, c.hintAtFraction)
        assertEquals(0.5f, c.threeStarFraction)
        assertEquals(WiperLevel.HIGH, c.heavyRainMinWiper)
        assertTrue(SituationId.WIPER_DEBRIS in c.disabled)
    }

    @Test
    fun `no line uses the forbidden word`() {
        val lines = SituationCatalog.all().flatMap { d ->
            listOf(d.title, d.intro, d.lesson) + d.steps.flatMap { listOfNotNull(it.label, it.hint, it.setup, it.choice?.question) } +
                d.penalties.mapNotNull { it.line }
        }
        assertTrue(lines.none { "급발진" in it })
    }

    @Test
    fun `hints do not claim switch positions that differ by model`() {
        // 그랜저·아반떼 CN8은 와이퍼가 왼쪽 레버, 전조등은 버튼, 공조는 화면 안이라 위치 단정은 틀린다.
        // 예외: 상향등 왼쪽 레버, 창문 팔걸이 스위치는 여섯 차종 공통(조사 4절).
        val claims = listOf("오른쪽", "공조 패널", "레버 끝", "가운데", "핸들 오른쪽")
        val hints = SituationCatalog.all().flatMap { d -> d.steps.map { it.hint } + d.penalties.mapNotNull { it.line } }
        hints.forEach { h -> assertTrue(claims.none { it in h }, "위치를 단정함: $h") }
        val wiper = SituationCatalog.all().first { it.id == SituationId.HEAVY_RAIN }.steps.first { it.id == "wiper_fast" }
        assertFalse("레버" in wiper.hint)
    }

    // ---------- 챕터 목록과 신호 가용성 ----------

    @Test
    fun `chapters follow spec order and drop disabled situations`() {
        val e = DefaultTrainingEngine()
        val ch = e.chapters()
        assertEquals(listOf("C1", "C2", "C3", "C4", "C5"), ch.map { it.id })
        assertEquals(
            listOf(
                SituationId.RIGHT_TURN_CROSSWALK, SituationId.ROUNDABOUT_ENTRY, SituationId.SCHOOL_ZONE,
                SituationId.HIGHWAY_MERGE, SituationId.HYDROPLANING, SituationId.TOLL_BOOTH, SituationId.HIPASS_WRONG_LANE,
            ),
            ch[4].situations,
        )
        assertEquals(
            listOf(SituationId.HEAVY_RAIN, SituationId.FOGGED_WINDSHIELD, SituationId.SUDDEN_STOP),
            ch.first().situations,
        )
        assertEquals(
            listOf(SituationId.TUNNEL, SituationId.ONCOMING_GLARE, SituationId.DENSE_FOG, SituationId.DROWSY),
            ch[1].situations,
        )
        assertTrue(ch.none { SituationId.WIPER_DEBRIS in it.situations })
        assertTrue(ch.none { it.locked })
    }

    @Test
    fun `situations needing a missing signal are skipped`() {
        val d = driver(state = baseState.copy(defrostFrontOn = null, wiper = null))
        val c1 = d.engine.chapters().first()
        assertEquals(listOf(SituationId.SUDDEN_STOP), c1.situations)

        d.engine.start("C1", d.now)
        d.untilActive(SituationId.SUDDEN_STOP)
    }

    @Test
    fun `missing optional signal drops only that step`() {
        val d = driver(state = baseState.copy(acOn = null))
        d.engine.start("C1", d.now)
        d.untilActive(SituationId.HEAVY_RAIN)
        d.slowDownFirst()
        d.set { it.copy(wiper = WiperLevel.HIGH, lowBeamOn = true) }
        d.untilActive(SituationId.FOGGED_WINDSHIELD)
        assertEquals(listOf("slow_down", "hazard_confirm", "defrost_on", "fresh_air"), d.view.steps.map { it.id })
    }

    @Test
    fun `without hazard and fog signals breakdown is a choice and fog is headlights only`() {
        val emu = baseState.copy(hazardOn = null, frontFogOn = null, rearFogOn = null)
        val d = driver(state = emu)
        d.engine.inject(SituationId.BREAKDOWN, d.now)
        d.untilActive(SituationId.BREAKDOWN)
        assertEquals(listOf("first", "trunk", "evacuate", "report"), d.view.steps.map { it.id })
        assertNotNull(d.view.choice)

        // 안개: 브레이크로 가상 속도를 절반 이하로(먼저) + 전조등
        val f = driver(state = emu)
        f.engine.inject(SituationId.DENSE_FOG, f.now)
        f.untilActive(SituationId.DENSE_FOG)
        assertEquals(listOf("slow_down", "hazard_confirm", "low_beam_on"), f.view.steps.map { it.id })
        f.set { it.copy(lowBeamOn = true) }
        f.advance(1_000)
        assertEquals(listOf(false, false, true), f.view.steps.map { it.done })
        f.slowDownFirst()
        f.until { it.phase == TrainingPhase.SUCCESS }
        assertTrue(f.view.virtualSpeedKmh <= 40f)
        assertEquals(3, f.view.lastResult?.stars)
    }

    @Test
    fun `chapter is locked when no situation is playable`() {
        val e = DefaultTrainingEngine()
        e.tick(baseState.copy(lowBeamOn = null, driverWindowOpenPct = null, parkingBrakeOn = null), 0)
        assertTrue(e.chapters().first { it.id == "C4" }.locked)
    }

    // ---------- 시나리오: 챕터 1 전체 ----------

    @Test
    fun `chapter 1 scenario gives expected stars and events`() {
        val d = driver()
        d.engine.start("C1", d.now)
        assertEquals(TrainingPhase.CRUISE, d.tick().phase)
        assertTrue(d.says().first().startsWith("첫 빗길."))

        // S01 폭우: 먼저 감속과 비상등 확인, 그다음 와이퍼 High + 전조등을 4초에 → 제한 16초의 절반 안 → ★3
        d.untilActive(SituationId.HEAVY_RAIN)
        assertTrue(d.events.contains(CoachEvent.Sfx("rain", loop = true)))
        assertTrue(d.events.contains(CoachEvent.Ambient(AmbientMood.ALERT)))
        assertTrue("비가 쏟아져요. 앞이 안 보여요! 먼저 브레이크를 나눠서 지그시 밟아 속도를 줄여요." in d.says())
        val rainStart = d.now
        d.slowDownFirst()
        assertTrue(d.view.virtualSpeedKmh <= 40f)
        d.set { it.copy(wiper = WiperLevel.HIGH) }
        assertEquals(listOf(true, true, true, false), d.view.steps.map { it.done })
        d.advance(4_000 - (d.now - rainStart))
        d.set { it.copy(lowBeamOn = true) }
        assertEquals(TrainingPhase.SUCCESS, d.view.phase)
        assertEquals(3, d.view.lastResult?.stars)
        assertEquals(4_000L, d.view.lastResult?.reactionMs)
        assertTrue(d.events.contains(CoachEvent.StopSfx("rain")))
        assertTrue(d.events.contains(CoachEvent.Ambient(AmbientMood.SUCCESS)))
        assertTrue("완벽해요. 4.0초 걸렸어요." in d.says())

        // 평온 구간에서 방금 것을 짚는다
        d.until { it.phase == TrainingPhase.CRUISE }
        assertTrue(SituationCatalog.all().first { it.id == SituationId.HEAVY_RAIN }.lesson in d.says())

        // S02 김서림: 감속 뒤 10초에 세 조작 → 절반(9초) 넘김 → ★2
        d.untilActive(SituationId.FOGGED_WINDSHIELD)
        val fogStart = d.now
        d.slowDownFirst()
        d.advance(10_000 - (d.now - fogStart))
        d.set { it.copy(defrostFrontOn = true, acOn = true, recirculationOn = false) }
        assertEquals(2, d.view.lastResult?.stars)

        // S03 급정거: 0.6초에 브레이크 → ★3. 멈춘 뒤 비상등 확인은 시간에 쫓기지 않는다
        d.untilActive(SituationId.SUDDEN_STOP)
        d.advance(600)
        d.set { it.copy(brakePressed = true) }
        assertEquals("비상등 켰나요?", d.view.choice?.question)
        d.advance(5_000)
        assertEquals(TrainingPhase.ACTIVE, d.view.phase)
        d.confirmHazard()
        assertEquals(3, d.view.lastResult?.stars)

        val done = d.until { it.phase == TrainingPhase.CHAPTER_DONE }
        val res = assertNotNull(done.chapterResult)
        assertEquals("C1", res.chapterId)
        assertEquals(listOf(3, 2, 3), res.results.map { it.stars })
        assertEquals(8, res.totalStars)
        assertTrue(d.says().last().startsWith("첫 빗길 끝! 별 8개예요."))
        assertEquals(8, d.engine.progress().chapterBestStars["C1"])
    }

    // ---------- 시작 준비, 힌트, 실패 ----------

    @Test
    fun `switch already on at briefing asks to reset and waits`() {
        val d = driver(state = baseState.copy(wiper = WiperLevel.HIGH))
        d.engine.start("C1", d.now)
        d.until { it.situation == SituationId.HEAVY_RAIN }
        d.advance(3_000)
        assertEquals(TrainingPhase.BRIEFING, d.view.phase)
        assertTrue(d.says().any { it.startsWith("시작하기 전에 와이퍼를 꺼 두세요") })
        d.set { it.copy(wiper = WiperLevel.OFF) }
        d.untilActive(SituationId.HEAVY_RAIN)
        assertFalse(d.view.steps.first().done)
    }

    @Test
    fun `setup timeout counts the already-on switch as done`() {
        val d = driver(state = baseState.copy(lowBeamOn = true))
        d.engine.start("C1", d.now)
        d.untilActive(SituationId.HEAVY_RAIN)
        assertEquals(listOf(false, false, false, true), d.view.steps.map { it.done })
        val start = d.now
        d.slowDownFirst()
        d.set { it.copy(wiper = WiperLevel.HIGH) }
        assertEquals(d.now - start, d.view.lastResult?.reactionMs) // 미리 된 단계는 반응 시간에 넣지 않는다
    }

    @Test
    fun `hint at half of the limit names the switch`() {
        val d = driver()
        d.engine.start("C1", d.now)
        d.untilActive(SituationId.HEAVY_RAIN)
        d.clearEvents()
        d.advance(7_900)
        assertEquals(TrainingPhase.ACTIVE, d.view.phase)
        d.advance(200)
        assertEquals(TrainingPhase.HINT, d.view.phase)
        // 아직 안 한 첫 단계(감속)를 말한다
        assertTrue("브레이크를 급하게 밟지 말고 나눠서 지그시 밟아 속도를 절반으로 줄여요." in d.says())
    }

    @Test
    fun `finishing in overtime gives one star`() {
        val d = driver()
        d.engine.start("C1", d.now)
        d.untilActive(SituationId.HEAVY_RAIN)
        d.advance(17_000)
        d.slowDownFirst()
        d.set { it.copy(wiper = WiperLevel.HIGH, lowBeamOn = true) }
        assertEquals(1, d.view.lastResult?.stars)
    }

    @Test
    fun `failure tells the location and gives one retry capped at one star`() {
        val d = driver()
        d.engine.start("C1", d.now)
        d.untilActive(SituationId.HEAVY_RAIN)
        d.until { it.phase == TrainingPhase.FAIL }
        assertTrue(d.view.elapsedMs in 32_000L..32_050L, "제한 16초 + 초과 16초 뒤 실패: ${d.view.elapsedMs}")
        val failed = assertNotNull(d.view.lastResult)
        assertEquals(0, failed.stars)
        assertEquals(listOf("slow_down", "hazard_confirm", "wiper_fast", "low_beam_on"), failed.missedStepIds)
        assertTrue(d.events.contains(CoachEvent.Sfx("fail")))
        assertTrue(d.says().last().endsWith("한 번 더 해 볼까요?"))

        // 다시: 바로 같은 상황, 빠르게 해도 ★1
        d.untilActive(SituationId.HEAVY_RAIN)
        d.advance(500)
        d.slowDownFirst()
        d.set { it.copy(wiper = WiperLevel.HIGH, lowBeamOn = true) }
        assertEquals(1, d.view.lastResult?.stars)
        d.set { it.copy(wiper = WiperLevel.OFF) }
        d.until { it.situation == SituationId.FOGGED_WINDSHIELD }
    }

    // ---------- 감점 ----------

    @Test
    fun `wiper only on fogged windshield is a penalty with a hint`() {
        val d = driver()
        d.engine.start("C1", d.now)
        d.untilActive(SituationId.HEAVY_RAIN)
        d.slowDownFirst()
        d.set { it.copy(wiper = WiperLevel.HIGH, lowBeamOn = true) }
        d.set { it.copy(wiper = WiperLevel.OFF) }
        d.untilActive(SituationId.FOGGED_WINDSHIELD)
        d.clearEvents()
        d.set { it.copy(wiper = WiperLevel.LOW) }
        assertTrue(d.says().any { it.startsWith("안쪽 김이라") })
        d.slowDownFirst()
        d.set { it.copy(defrostFrontOn = true, acOn = true, recirculationOn = false) }
        assertEquals(1, d.view.lastResult?.stars)
        assertEquals(listOf("WIPER_ONLY"), d.view.lastResult?.penalties)
    }

    private fun suddenStop(reactMs: Long?, steer: Boolean = false): TrainingDriver {
        val d = driver()
        d.engine.inject(SituationId.SUDDEN_STOP, d.now)
        d.untilActive(SituationId.SUDDEN_STOP)
        if (steer) {
            d.advance(100)
            d.set { it.copy(steeringAngleDeg = 60f) }
        }
        if (reactMs != null) {
            d.advance(reactMs - (if (steer) 100 else 0))
            d.set { it.copy(brakePressed = true) }
            d.confirmHazard()
        } else {
            d.until { it.phase == TrainingPhase.FAIL }
        }
        return d
    }

    @Test
    fun `sudden stop stars follow reaction thresholds`() {
        assertEquals(3, suddenStop(700).view.lastResult?.stars)
        assertEquals(2, suddenStop(950).view.lastResult?.stars)
        assertEquals(1, suddenStop(1_400).view.lastResult?.stars)
        val crashed = suddenStop(null)
        assertEquals(0, crashed.view.lastResult?.stars)
        assertEquals(0f, crashed.view.scene.leadCarDistance)
    }

    @Test
    fun `sharp steering on sudden stop caps stars`() {
        val d = suddenStop(500, steer = true)
        assertEquals(1, d.view.lastResult?.stars)
        assertEquals(listOf("STEER_JERK"), d.view.lastResult?.penalties)
    }

    @Test
    fun `tunnel and fog have no penalty for high beam`() {
        val d = driver()
        d.engine.inject(SituationId.TUNNEL, d.now)
        d.untilActive(SituationId.TUNNEL)
        assertTrue(d.view.scene.tunnel)
        d.set { it.copy(highBeamOn = true) }
        d.set { it.copy(lowBeamOn = true) }
        assertEquals(3, d.view.lastResult?.stars)
        assertTrue(d.view.lastResult!!.penalties.isEmpty())
        val penaltyFree = listOf(SituationId.TUNNEL, SituationId.DENSE_FOG, SituationId.UNINTENDED_ACCEL, SituationId.BEFORE_EXIT)
        assertTrue(SituationCatalog.all().filter { it.id in penaltyFree }.all { it.penalties.isEmpty() })
    }

    // ---------- 장면이 신호에 반응 ----------

    @Test
    fun `defrost clears windshield fog and brake holds the lead car`() {
        val fogged = driver()
        fogged.engine.inject(SituationId.FOGGED_WINDSHIELD, fogged.now)
        fogged.untilActive(SituationId.FOGGED_WINDSHIELD)
        val start = fogged.view.scene.windshieldFog
        assertTrue(start > 0.7f, "브리핑 동안 김이 찬다: $start")
        fogged.advance(2_000)
        val noAction = fogged.view.scene.windshieldFog
        assertTrue(noAction >= start)
        fogged.set { it.copy(defrostFrontOn = true) }
        fogged.advance(2_000)
        val withDefrost = fogged.view.scene.windshieldFog
        // 성에 제거만으로는 천천히 걷힌다(2초에 0.07쯤). 실제 김서림은 몇 초 만에 사라지지 않는다
        val slow = noAction - withDefrost
        assertTrue(slow in 0.04f..0.12f, "성에 제거만으로는 천천히 걷힌다: $noAction → $withDefrost")
        // 에어컨과 외기를 더하면 눈에 띄게 빨라진다(세 조작을 다 해야 하는 이유)
        fogged.set { it.copy(acOn = true, recirculationOn = false) }
        fogged.advance(2_000)
        val fast = withDefrost - fogged.view.scene.windshieldFog
        assertTrue(fast > slow * 2f, "에어컨·외기를 더하면 빨라진다: 느릴 때 $slow, 다 켰을 때 $fast")
        // 다 켠 뒤 10초쯤이면 다 걷힌다
        fogged.advance(9_000)
        assertTrue(fogged.view.scene.windshieldFog < 0.05f, "다 켜고 11초 뒤: ${fogged.view.scene.windshieldFog}")

        val stop = driver()
        stop.engine.inject(SituationId.SUDDEN_STOP, stop.now)
        stop.untilActive(SituationId.SUDDEN_STOP)
        stop.advance(300)
        stop.set { it.copy(brakePressed = true) }
        val held = stop.view.scene.leadCarDistance!!
        assertTrue(stop.view.scene.leadCarBraking)
        stop.advance(1_000)
        assertTrue(stop.view.scene.leadCarDistance!! >= held)
        assertTrue(stop.view.virtualSpeedKmh < 80f)
    }

    @Test
    fun `alert rises when a situation fires and falls after success`() {
        val d = driver()
        d.engine.inject(SituationId.ONCOMING_GLARE, d.now)
        d.until { it.situation == SituationId.ONCOMING_GLARE }
        d.set { it.copy(highBeamOn = true) }
        d.untilActive(SituationId.ONCOMING_GLARE)
        d.advance(1_000)
        assertEquals(1f, d.view.scene.alert)
        assertTrue(d.view.scene.oncomingGlare > 0.3f)
        d.set { it.copy(highBeamOn = false) }
        d.advance(2_000)
        assertTrue(d.view.scene.alert < 0.5f)
    }

    // ---------- 말로 답하는 상황 ----------

    @Test
    fun `breakdown is a four step order quiz scored by mistakes`() {
        val d = driver()
        d.engine.inject(SituationId.BREAKDOWN, d.now)
        d.untilActive(SituationId.BREAKDOWN)
        val choice = assertNotNull(d.view.choice)
        assertEquals("가장 먼저 할 일은 뭘까요?", choice.question)
        val ask = d.events.filterIsInstance<CoachEvent.Say>().last()
        assertEquals(ReplyKind.CHOICE, ask.expectReply)
        assertTrue(ask.text.contains("1번, 비상등 켜기."))

        d.engine.answer("비상등", d.now)
        assertEquals("그다음엔요?", d.tick().choice?.question)
        d.engine.answer("트렁크 열어", d.now)
        d.engine.answer("차 바로 앞", d.now) // 틀림 → 바로잡는 말, 같은 질문 유지
        assertEquals("사람은 어디로 갈까요?", d.tick().choice?.question)
        assertTrue(d.says().any { it.startsWith("차 근처는 위험해요.") })
        d.engine.answer("가드레일 밖으로 대피", d.now)
        d.engine.answer("112에 신고", d.now)
        assertEquals(TrainingPhase.SUCCESS, d.tick().phase)
        assertEquals(2, d.view.lastResult?.stars)
        assertEquals(listOf("WRONG_evacuate"), d.view.lastResult?.penalties)
        // 시간 제한 없음: 오래 고민해도 실패하지 않는다
        val slow = driver()
        slow.engine.inject(SituationId.BREAKDOWN, slow.now)
        slow.untilActive(SituationId.BREAKDOWN)
        slow.advance(60_000)
        assertEquals(TrainingPhase.HINT, slow.view.phase)
    }

    @Test
    fun `before exit asks about the parking brake by voice without penalty`() {
        val d = driver(state = baseState.copy(lowBeamOn = true, driverWindowOpenPct = 30))
        d.engine.start("C4", d.now)
        d.untilActive(SituationId.BEFORE_EXIT)
        assertEquals(listOf("lights_off", "windows_closed", "parking_brake"), d.view.steps.map { it.id })
        assertNull(d.view.choice)
        d.set { it.copy(lowBeamOn = false, driverWindowOpenPct = 0) }
        assertEquals("주차 브레이크 채웠나요?", d.view.choice?.question)
        val ask = d.events.filterIsInstance<CoachEvent.Say>().last()
        assertEquals("주차 브레이크 채웠나요?", ask.text)
        assertEquals(ReplyKind.YES_NO, ask.expectReply)
        d.engine.answer("아직이요", d.now)
        assertEquals(ReplyKind.YES_NO, d.tick().let { d.events.filterIsInstance<CoachEvent.Say>().last().expectReply })
        d.engine.answer("응 채웠어", d.now)
        assertEquals(TrainingPhase.SUCCESS, d.tick().phase)
        assertEquals(3, d.view.lastResult?.stars)
        assertTrue(d.view.lastResult!!.penalties.isEmpty())
    }

    @Test
    fun `warning light asks two questions in order`() {
        val d = driver()
        d.engine.inject(SituationId.WARNING_LIGHT, d.now)
        d.untilActive(SituationId.WARNING_LIGHT)
        assertEquals("TPMS", d.view.scene.warningLight)
        assertEquals("이 경고등은 무슨 뜻일까요?", d.view.choice?.question)
        d.engine.answer("1번", d.now)
        assertEquals("그럼 어떻게 할까요?", d.tick().choice?.question)
        d.engine.answer("속도 줄이고 안전한 곳에 세울게", d.now)
        assertEquals(TrainingPhase.SUCCESS, d.tick().phase)
        assertEquals(3, d.view.lastResult?.stars)
    }

    @Test
    fun `choice answers are parsed by text, number and keywords`() {
        val c = SituationCatalog.all().first { it.id == SituationId.WARNING_LIGHT }.steps.first().choice!!
        assertEquals(0, c.match("타이어 공기압이 낮아요"))
        assertEquals(1, c.match("2번"))
        assertEquals(2, c.match("세 번째"))
        assertEquals(1, c.match("엔진 오일 같은데"))
        assertEquals(0, c.match("바람이 빠졌나"))
        assertNull(c.match("모르겠어"))
        assertNull(c.match(""))
    }

    // ---------- 의도치 않은 가속 ----------

    @Test
    fun `unintended acceleration needs the brake held for three seconds`() {
        val d = driver()
        d.engine.inject(SituationId.UNINTENDED_ACCEL, d.now)
        d.untilActive(SituationId.UNINTENDED_ACCEL)
        assertEquals(listOf("brake_hold"), d.view.steps.map { it.id })
        d.advance(1_000)
        assertTrue(d.view.virtualSpeedKmh > 80f, "브레이크 전에는 빨라진다")
        d.set { it.copy(brakePressed = true) }
        d.advance(2_900)
        assertFalse(d.view.steps.first().done, "3초 유지 전")
        d.advance(200)
        assertEquals(TrainingPhase.SUCCESS, d.view.phase)
        // 반응 시간은 처음 밟은 때
        assertTrue(d.view.lastResult!!.reactionMs!! in 1_000L..1_050L, "${d.view.lastResult}")
        assertEquals(3, d.view.lastResult?.stars)
        d.advance(3_100)
        assertTrue(d.says().any { it.contains("기어는 중립으로요") }, "EPB·중립은 말로 안내")
    }

    @Test
    fun `releasing the brake restarts the three seconds without penalty`() {
        val d = driver()
        d.engine.inject(SituationId.UNINTENDED_ACCEL, d.now)
        d.untilActive(SituationId.UNINTENDED_ACCEL)
        assertTrue(d.events.contains(CoachEvent.Sfx("engine_rise")))
        d.set { it.copy(brakePressed = true) }
        d.advance(2_000)
        d.set { it.copy(brakePressed = false) }
        d.advance(500)
        d.set { it.copy(brakePressed = true) }
        d.advance(2_000)
        assertTrue(d.view.phase != TrainingPhase.SUCCESS, "다시 밟은 뒤 아직 3초가 안 됨")
        d.advance(1_100)
        assertEquals(TrainingPhase.SUCCESS, d.view.phase)
        assertTrue(d.view.lastResult!!.penalties.isEmpty())
    }

    // ---------- 모드 ----------

    @Test
    fun `surprise mode runs until the first failure and records survive count`() {
        val d = driver(TrainingConfig(random = Random(7)))
        d.engine.startSurprise(d.now)
        // 첫 두 상황은 모든 스위치를 바로 해결 상태로 바꿔 통과
        repeat(2) {
            val id = d.until { it.phase == TrainingPhase.BRIEFING }.situation!!
            d.set { setupFor(id, baseState) }
            d.untilActive(id)
            d.set { solveAll(it, id) }
            d.until { it.phase == TrainingPhase.SUCCESS }
            d.until { it.phase == TrainingPhase.CRUISE }
        }
        // 세 번째는 아무것도 안 함 → 실패 → 끝
        val third = d.until { it.phase == TrainingPhase.BRIEFING }.situation!!
        d.set { setupFor(third, baseState) }
        d.untilActive(third)
        val v = d.until { it.phase == TrainingPhase.CHAPTER_DONE }
        assertEquals(2, v.surviveCount)
        assertEquals(ChapterCatalog.SURPRISE, v.chapterResult?.chapterId)
        assertEquals(2, d.engine.progress().surpriseBest)
        assertTrue(d.says().last().startsWith("2개 버텼어요."))
    }

    @Test
    fun `surprise mode never picks disabled or untimed situations`() {
        val d = driver(TrainingConfig(random = Random(1), cruiseMs = 100, firstCruiseMs = 100))
        d.engine.startSurprise(d.now)
        val seen = mutableSetOf<SituationId>()
        repeat(15) {
            val id = d.until { it.phase == TrainingPhase.BRIEFING }.situation!!
            seen += id
            d.set { setupFor(id, baseState) }
            d.untilActive(id)
            d.set { solveAll(it, id) }
            d.until { it.phase == TrainingPhase.CRUISE }
        }
        assertFalse(SituationId.WIPER_DEBRIS in seen)
        assertFalse(SituationId.BEFORE_EXIT in seen)
        assertTrue(seen.size >= 4)
    }

    @Test
    fun `instructor battle fires the chosen situation right away`() {
        val d = driver()
        d.engine.inject(SituationId.TUNNEL, d.now)
        assertEquals(TrainingPhase.BRIEFING, d.tick().phase)
        assertEquals(ChapterCatalog.VERSUS, d.view.chapterId)
        // 상황 중에 던진 것은 다음 차례
        d.untilActive(SituationId.TUNNEL)
        d.engine.inject(SituationId.DENSE_FOG, d.now)
        d.set { it.copy(lowBeamOn = true) }
        d.set { it.copy(lowBeamOn = false) }
        d.until { it.situation == SituationId.DENSE_FOG }
        // 고를 때까지는 계속 평온 주행
        d.set { it.copy(lowBeamOn = true, brakePressed = true) }
        d.until { it.phase == TrainingPhase.CRUISE }
        d.advance(30_000)
        assertEquals(TrainingPhase.CRUISE, d.view.phase)
    }

    @Test
    fun `leaving park saves the chapter and resumes later`() {
        val d = driver()
        d.engine.start("C1", d.now)
        d.untilActive(SituationId.HEAVY_RAIN)
        d.slowDownFirst()
        d.set { it.copy(wiper = WiperLevel.HIGH, lowBeamOn = true) }
        d.untilActive(SituationId.FOGGED_WINDSHIELD)
        d.clearEvents()
        d.engine.abort(d.now)
        val v = d.tick()
        assertEquals(TrainingPhase.IDLE, v.phase)
        assertTrue(d.events.contains(CoachEvent.StopSfx("breath")))
        assertTrue(d.events.contains(CoachEvent.Ambient(AmbientMood.OFF)))
        assertEquals("C1", d.engine.resumableChapter())

        d.set { baseState }
        d.engine.start("C1", d.now)
        d.tick()
        assertTrue(d.says().last().startsWith("이어서 할게요."))
        d.untilActive(SituationId.FOGGED_WINDSHIELD)
        d.slowDownFirst()
        d.set { it.copy(defrostFrontOn = true, acOn = true, recirculationOn = false) }
        d.untilActive(SituationId.SUDDEN_STOP)
        d.set { it.copy(brakePressed = true) }
        d.confirmHazard()
        val done = d.until { it.phase == TrainingPhase.CHAPTER_DONE }
        assertEquals(
            listOf(SituationId.HEAVY_RAIN, SituationId.FOGGED_WINDSHIELD, SituationId.SUDDEN_STOP),
            done.chapterResult?.results?.map { it.id },
        )
        assertNull(d.engine.resumableChapter())
    }

    // ---------- 기록과 이 차 면허 ----------

    @Test
    fun `clearing all chapters with two stars grants the license for this car only`() {
        val d = driver()
        for (ch in listOf("C1", "C2", "C3", "C4")) {
            d.set { baseState }
            d.engine.start(ch, d.now)
            playChapterPerfectly(d)
        }
        val p = d.engine.progress()
        assertTrue(p.licenseFor("avante").licensed)
        assertEquals(setOf("C1", "C2", "C3", "C4"), p.licenseFor("avante").clearedChapters)
        assertFalse(p.licenseFor("sonata").licensed)
        assertTrue(d.says().any { it.contains("이 차 숙련 배지를 받았어요!") })
        assertEquals(3, p.bestStars["HEAVY_RAIN"])
    }

    @Test
    fun `best reaction keeps the fastest and restore loads saved progress`() {
        val saved = TrainingProgress(bestReactionMs = mapOf("SUDDEN_STOP" to 650L), surpriseBest = 5)
        val d = driver()
        d.engine.restore(saved)
        d.engine.inject(SituationId.SUDDEN_STOP, d.now)
        d.untilActive(SituationId.SUDDEN_STOP)
        d.advance(900)
        d.set { it.copy(brakePressed = true) }
        d.confirmHazard()
        assertEquals(650L, d.engine.progress().bestReactionMs["SUDDEN_STOP"])
        assertEquals(5, d.engine.progress().surpriseBest)
        assertEquals(2, d.engine.progress().bestStars["SUDDEN_STOP"])
    }

    private fun playChapterPerfectly(d: TrainingDriver) {
        while (true) {
            val v = d.until { it.phase == TrainingPhase.BRIEFING || it.phase == TrainingPhase.CHAPTER_DONE }
            if (v.phase == TrainingPhase.CHAPTER_DONE) return
            val id = v.situation!!
            d.set { setupFor(id, baseState) }
            d.untilActive(id)
            d.advance(200)
            d.set { solveAll(it, id) }
            // 말로 답하는 단계는 모두 1번(정답)
            d.answerUntilSuccess(id)
            assertTrue(d.view.lastResult!!.stars >= 2, "$id stars=${d.view.lastResult}")
            d.until { it.phase != TrainingPhase.SUCCESS }
        }
    }
}

/** 상황이 요구하는 시작 상태(되돌려 둘 스위치). */
fun setupFor(id: SituationId, s: VehicleState): VehicleState = when (id) {
    SituationId.ONCOMING_GLARE -> s.copy(highBeamOn = true)
    SituationId.BEFORE_EXIT -> s.copy(lowBeamOn = true, driverWindowOpenPct = 30, parkingBrakeOn = false)
    else -> s
}

/** 상황의 모든 신호 단계를 만족하는 상태. */
fun solveAll(s: VehicleState, id: SituationId): VehicleState = when (id) {
    // 앞이 안 보이는 상황은 브레이크로 속도를 줄이는 것이 먼저다
    SituationId.HEAVY_RAIN -> s.copy(brakePressed = true, wiper = WiperLevel.HIGH, lowBeamOn = true)
    SituationId.FOGGED_WINDSHIELD -> s.copy(brakePressed = true, defrostFrontOn = true, acOn = true, recirculationOn = false)
    SituationId.SUDDEN_STOP -> s.copy(brakePressed = true)
    SituationId.TUNNEL -> s.copy(lowBeamOn = true)
    SituationId.DENSE_FOG -> s.copy(lowBeamOn = true, brakePressed = true)
    SituationId.ONCOMING_GLARE -> s.copy(highBeamOn = false)
    SituationId.BREAKDOWN -> s
    SituationId.WARNING_LIGHT -> s
    SituationId.DROWSY -> s.copy(recirculationOn = false, driverWindowOpenPct = 20)
    SituationId.UNINTENDED_ACCEL -> s.copy(brakePressed = true)
    SituationId.BEFORE_EXIT -> s.copy(lowBeamOn = false, driverWindowOpenPct = 0, parkingBrakeOn = true)
    SituationId.WIPER_DEBRIS -> s.copy(hazardOn = true, wiper = WiperLevel.LOW)
    SituationId.RIGHT_TURN_CROSSWALK -> s.copy(brakePressed = true)
    SituationId.HIGHWAY_MERGE -> s.copy(turnSignal = TurnSignal.LEFT, steeringAngleDeg = -15f)
    SituationId.HYDROPLANING -> s
    SituationId.HIPASS_WRONG_LANE -> s
    SituationId.ROUNDABOUT_ENTRY -> s.copy(turnSignal = TurnSignal.LEFT, brakePressed = true)
    SituationId.SCHOOL_ZONE -> s.copy(brakePressed = true)
    SituationId.TOLL_BOOTH -> s.copy(brakePressed = true, driverWindowOpenPct = 30)
}
