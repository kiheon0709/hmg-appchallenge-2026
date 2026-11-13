package io.moodlab.sideseat.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DriveCoachTest {
    private val day = baseState.copy(parked = false, minuteOfDay = 13 * 60, recirculationOn = false)
    private val min = 60_000L
    /** K07·K10까지 켠 설정(실차에서 신호가 읽히는 경우). */
    private val allRules = CoachConfig(disabledRules = emptySet())

    /** 1초 간격으로 [state]를 [ms] 동안 흘려 넣고 나온 말을 모은다. */
    private class Drive(val coach: DriveCoach, var state: VehicleState) {
        var now = 0L
        val said = mutableListOf<CoachUtterance>()

        init {
            coach.onDriveStart(0)
            coach.tick(state, 0)?.let { said += it }
        }

        fun run(ms: Long, step: Long = 1_000) {
            val end = now + ms
            while (now < end) {
                now += step
                coach.tick(state.copy(timestampMs = now), now)?.let { said += it }
            }
        }

        fun set(f: (VehicleState) -> VehicleState) {
            state = f(state)
        }

        fun ids() = said.map { it.ruleId }
    }

    private fun drive(state: VehicleState = day, config: CoachConfig = CoachConfig(), practiced: Set<SituationId> = emptySet()) =
        Drive(DefaultDriveCoach(config) { practiced }, state)

    @Test
    fun `default numbers follow the spec`() {
        val c = CoachConfig()
        assertEquals(30 * min, c.windowMs)
        assertEquals(3, c.maxUtterancesPerWindow)
        assertEquals(10 * min, c.safetyRepeatAfterMs)
        assertEquals(20_000, c.k01WiperMs)
        assertEquals(1 * min, c.k02DrivingMs)
        assertEquals(5 * min, c.k03RecircMs)
        assertEquals(30 * min, c.k04RecircMs)
        assertEquals(2, c.k04MinOccupants)
        assertEquals(3 * min, c.k05HighBeamMs)
        assertEquals(60_000, c.k06TurnSignalMs)
        assertEquals(10 * min, c.k07FogMs)
        assertEquals(110 * min, c.k08DrivingMs)
        assertEquals(setOf("K07", "K10"), c.disabledRules)
        assertEquals(90f, c.o1SteerDeg)
        assertEquals(3_000, c.o1SignalLookbackMs)
    }

    @Test
    fun `K01 wiper without headlights for 20 seconds`() {
        val d = drive(day.copy(wiper = WiperLevel.LOW))
        d.run(19_000)
        assertTrue(d.said.isEmpty())
        d.run(1_000)
        assertEquals(listOf("K01"), d.ids())
        assertEquals("비가 와요. 전조등을 켜 주세요.", d.said[0].text)
        assertTrue(d.said[0].urgent)
    }

    @Test
    fun `K01 stays quiet with headlights on or unknown`() {
        assertTrue(drive(day.copy(wiper = WiperLevel.HIGH, lowBeamOn = true)).also { it.run(5 * min) }.said.isEmpty())
        assertTrue(drive(day.copy(wiper = WiperLevel.HIGH, lowBeamOn = null)).also { it.run(5 * min) }.said.isEmpty())
        assertTrue(drive(day.copy(wiper = WiperLevel.MIST)).also { it.run(5 * min) }.said.isEmpty())
    }

    @Test
    fun `K09 adds practiced prefix`() {
        val d = drive(day.copy(wiper = WiperLevel.LOW), practiced = setOf(SituationId.HEAVY_RAIN))
        d.run(20_000)
        assertEquals("연습했던 거예요. 비가 와요. 전조등을 켜 주세요.", d.said.single().text)
    }

    @Test
    fun `K02 after sunset with headlights off after one minute of driving`() {
        val night = day.copy(minuteOfDay = 19 * 60, sunsetMinute = 18 * 60 + 30)
        val d = drive(night)
        d.run(59_000)
        assertTrue(d.said.isEmpty())
        d.run(1_000)
        assertEquals(listOf("K02"), d.ids())
        assertTrue(drive(day).also { it.run(5 * min) }.said.isEmpty())
        // 일몰을 위치로 알면 그것을 쓴다
        assertTrue(drive(night.copy(sunsetMinute = 19 * 60 + 30)).also { it.run(2 * min) }.said.isEmpty())
    }

    @Test
    fun `K03 rain with recirculation for 5 minutes and AC off`() {
        val d = drive(day.copy(wiper = WiperLevel.INTERMITTENT, lowBeamOn = true, recirculationOn = true, acOn = false))
        d.run(5 * min - 1_000)
        assertTrue(d.said.isEmpty())
        d.run(1_000)
        assertEquals(listOf("K03"), d.ids())
        assertEquals("앞유리에 김이 설 수 있어요. 외기로 바꾸거나 에어컨을 켜 보세요.", d.said[0].text)

        val ac = drive(day.copy(wiper = WiperLevel.INTERMITTENT, lowBeamOn = true, recirculationOn = true, acOn = true))
        ac.run(10 * min)
        assertTrue(ac.said.isEmpty())
    }

    @Test
    fun `K04 recirculation for 30 minutes with two people`() {
        val two = drive(day.copy(recirculationOn = true, occupied = setOf(Seat.DRIVER, Seat.PASSENGER)))
        two.run(30 * min)
        assertEquals(listOf("K04"), two.ids())
        val alone = drive(day.copy(recirculationOn = true, occupied = setOf(Seat.DRIVER)))
        alone.run(40 * min)
        assertTrue(alone.said.isEmpty())
    }

    @Test
    fun `K05 high beam and K06 turn signal left on`() {
        val hb = drive(day.copy(highBeamOn = true))
        hb.run(3 * min - 1_000)
        assertTrue(hb.said.isEmpty())
        hb.run(1_000)
        assertEquals(listOf("K05"), hb.ids())

        val turn = drive(day.copy(turnSignal = TurnSignal.LEFT))
        turn.run(40_000)
        turn.set { it.copy(turnSignal = TurnSignal.RIGHT) } // 방향이 바뀌면 다시 센다
        turn.run(40_000)
        assertTrue(turn.said.isEmpty())
        turn.run(21_000)
        assertEquals(listOf("K06"), turn.ids())

        val hazard = drive(day.copy(turnSignal = TurnSignal.HAZARD))
        hazard.run(5 * min)
        assertTrue(hazard.said.isEmpty(), "비상등은 깜빡이 규칙 대상이 아니다")
    }

    @Test
    fun `K07 fog lights on a clear road for 10 minutes`() {
        val d = drive(day.copy(frontFogOn = true), allRules)
        d.run(10 * min)
        assertEquals(listOf("K07"), d.ids())
        assertTrue(drive(day.copy(frontFogOn = true)).also { it.run(15 * min) }.said.isEmpty(), "기본값은 K07 꺼짐")
        val rainy = drive(day.copy(frontFogOn = true, wiper = WiperLevel.LOW, lowBeamOn = true), allRules)
        rainy.run(15 * min)
        assertTrue(rainy.said.isEmpty())
    }

    @Test
    fun `K08 asks for a rest stop and yes adds a waypoint`() {
        val d = drive(day, CoachConfig(minGapMs = 0))
        d.run(110 * min, step = 10_000)
        val ask = d.said.single()
        assertEquals("K08", ask.ruleId)
        assertEquals(ReplyKind.YES_NO, ask.expectReply)
        assertTrue(d.coach.onReply("K08", "응 가자", d.now) is CoachAction.AddRestStopWaypoint)
        assertNull(d.coach.onReply("K08", "아니 괜찮아", d.now))
        assertNull(d.coach.onReply("K01", "응", d.now))
        d.run(60 * min, step = 10_000)
        assertEquals(1, d.said.size, "한 번만 묻는다")
    }

    @Test
    fun `yes and no replies`() {
        listOf("응", "네", "예 좋아요", "그래", "어", "가자").forEach { assertTrue(DefaultDriveCoach.isYes(it), it) }
        listOf("아니", "괜찮아", "나중에", "아니 됐어", "모르겠어").forEach { assertFalse(DefaultDriveCoach.isYes(it), it) }
    }

    @Test
    fun `K10 names only the tire pressure light, once each, not ones already on`() {
        val d = drive(day.copy(activeTelltales = mapOf("ABS" to TelltaleColor.YELLOW)), allRules)
        d.run(10_000)
        assertTrue(d.said.isEmpty(), "출발 때 이미 켜진 경고등은 말하지 않는다")
        d.set { it.copy(activeTelltales = it.activeTelltales + ("TPMS" to TelltaleColor.YELLOW)) }
        d.run(1_000)
        assertEquals("타이어 공기압 경고등이 켜졌어요. 속도를 줄이고 가까운 곳에서 공기압을 확인해 주세요.", d.said.last().text)
        d.set { it.copy(activeTelltales = it.activeTelltales + ("ENGINE_OIL_PRESSURE" to TelltaleColor.RED)) }
        d.run(1_000)
        assertEquals("경고등이 켜졌어요. 안전한 곳에 세우고 취급설명서를 같이 볼게요.", d.said.last().text)
        // 꺼졌다 다시 켜져도 같은 주행에서는 한 번
        d.set { it.copy(activeTelltales = emptyMap()) }
        d.run(1_000)
        d.set { it.copy(activeTelltales = mapOf("TPMS" to TelltaleColor.YELLOW)) }
        d.run(5_000)
        assertEquals(listOf("K10", "K10"), d.ids())
    }

    @Test
    fun `at most three utterances per 30 minutes and 20 seconds apart`() {
        // K05(3분), K06(1분), K07(10분), K04(30분) 조건을 동시에
        val s = day.copy(
            highBeamOn = true, turnSignal = TurnSignal.LEFT, frontFogOn = true,
            recirculationOn = true, occupied = setOf(Seat.DRIVER, Seat.PASSENGER),
        )
        val d = drive(s, allRules)
        d.run(30 * min)
        assertEquals(listOf("K06", "K05", "K07"), d.ids())
        // 창이 지나면 다음 말(K04, 안전 반복 K06 등)이 가능해진다
        d.run(31 * min)
        assertTrue(d.said.size > 3)
    }

    @Test
    fun `safety rule repeats once after 10 minutes, others never`() {
        val d = drive(day.copy(highBeamOn = true))
        d.run(3 * min)
        d.run(10 * min - 1_000)
        assertEquals(1, d.said.size)
        d.run(1_000)
        assertEquals(listOf("K05", "K05"), d.ids())
        d.run(60 * min)
        assertEquals(2, d.said.size)

        val fog = drive(day.copy(frontFogOn = true), allRules)
        fog.run(60 * min)
        assertEquals(listOf("K07"), fog.ids())
    }

    @Test
    fun `urgent safety bypasses the quota`() {
        val s = day.copy(
            highBeamOn = true, turnSignal = TurnSignal.LEFT, frontFogOn = true, wiper = WiperLevel.OFF,
        )
        val d = drive(s, allRules)
        d.run(11 * min)
        assertEquals(3, d.said.size)
        d.set { it.copy(wiper = WiperLevel.LOW, frontFogOn = false) }
        d.run(21_000)
        assertEquals("K01", d.said.last().ruleId)
    }

    @Test
    fun `O1 counts big steering without turn signal`() {
        val d = drive()
        d.set { it.copy(steeringAngleDeg = 120f) }
        d.run(3_000)
        d.set { it.copy(steeringAngleDeg = 0f) }
        d.run(1_000)
        // 깜빡이를 켜고 2초 뒤 돌면 세지 않는다
        d.set { it.copy(turnSignal = TurnSignal.LEFT) }
        d.run(1_000)
        d.set { it.copy(turnSignal = TurnSignal.OFF) }
        d.run(1_000)
        d.set { it.copy(steeringAngleDeg = -100f) }
        d.run(1_000)
        d.set { it.copy(steeringAngleDeg = 10f) }
        d.run(5_000)
        d.set { it.copy(steeringAngleDeg = -95f) }
        d.run(1_000)
        val summary = d.coach.onDriveEnd(d.now)
        assertEquals(2, summary.observations["NO_SIGNAL_TURN"])
        assertTrue(d.said.isEmpty(), "관찰은 말하지 않는다")
    }

    @Test
    fun `O2 counts hard braking from location speed`() {
        val d = drive(day.copy(speedKmhEstimate = 60f))
        d.run(5_000)
        d.set { it.copy(speedKmhEstimate = 50f, brakePressed = true) }
        d.run(1_000)
        d.set { it.copy(speedKmhEstimate = 35f) }
        d.run(1_000)
        d.set { it.copy(speedKmhEstimate = 30f) }
        d.run(1_000)
        // 천천히 줄인 것은 세지 않는다
        d.set { it.copy(speedKmhEstimate = 60f, brakePressed = false) }
        d.run(10_000)
        for (v in listOf(55f, 50f, 45f, 40f, 35f)) {
            d.set { it.copy(speedKmhEstimate = v, brakePressed = true) }
            d.run(1_500, step = 1_500)
        }
        assertEquals(1, d.coach.onDriveEnd(d.now).observations["HARD_BRAKE"])
    }

    @Test
    fun `rainy night drive summary and rule based review`() {
        val night = day.copy(minuteOfDay = 20 * 60, lowBeamOn = true)
        val d = drive(night, practiced = setOf(SituationId.FOGGED_WINDSHIELD))
        d.run(10 * min)
        d.set { it.copy(wiper = WiperLevel.LOW, recirculationOn = true, acOn = false) }
        d.run(15 * min)
        d.set { it.copy(wiper = WiperLevel.OFF, recirculationOn = false) }
        d.run(5 * min)
        d.set { it.copy(steeringAngleDeg = 110f) }
        d.run(1_000)
        val s = d.coach.onDriveEnd(d.now)
        assertEquals(30, s.durationMin)
        assertEquals(15, s.rainMin)
        assertEquals(30, s.nightMin)
        assertEquals(listOf("K03"), s.utterances.map { it.ruleId })
        assertTrue(s.utterances.single().text.startsWith("연습했던 거예요."))
        assertEquals(SituationId.FOGGED_WINDSHIELD, s.suggestedSituation)
        assertEquals(
            listOf(
                "비 오는 길에서도 전조등을 잘 챙겼어요.",
                "깜빡이 없이 크게 돈 게 1번 있었어요. 돌기 전에 먼저 켜 봐요.",
                "다음에 세웠을 때 앞유리 김서림 훈련을 1분만 해 볼까요?",
            ),
            s.ruleBasedReview,
        )
    }

    @Test
    fun `quiet drive review`() {
        val d = drive()
        d.run(12 * min, step = 5_000)
        val s = d.coach.onDriveEnd(d.now)
        assertEquals(3, s.ruleBasedReview.size)
        assertEquals("오늘은 제가 한 번도 끼어들 일이 없었어요.", s.ruleBasedReview[0])
        assertEquals("고칠 건 딱히 없었어요. 이대로만 가요.", s.ruleBasedReview[1])
        assertNull(s.suggestedSituation)
    }

    @Test
    fun `new drive resets counters`() {
        val d = drive(day.copy(highBeamOn = true))
        d.run(4 * min)
        assertEquals(1, d.said.size)
        d.coach.onDriveStart(d.now)
        d.run(3 * min + 1_000)
        assertEquals(2, d.said.size)
    }

    // ---------- 위치 연계(명세 6.4) ----------

    private val zoneLat = 37.4200
    private val zoneLon = 127.0947
    private val zones = SchoolZoneIndex(listOf(SchoolZone("z1", "시흥초등학교", zoneLat, zoneLon)))

    /** 남쪽 [startM]에서 북쪽으로 [kmh]로 1초마다 좌표를 넣는다. */
    private class GeoDrive(val coach: DriveCoach, base: VehicleState, startM: Double, val lat0: Double, val lon: Double) {
        var now = 0L
        var posM = startM
        var state = base
        val said = mutableListOf<CoachUtterance>()

        init {
            coach.onDriveStart(0)
        }

        fun drive(seconds: Int, kmh: Double, limit: Int? = null) {
            repeat(seconds) {
                now += 1_000
                posM += kmh / 3.6
                state = state.copy(
                    latitude = lat0 + posM / M_PER_DEG_LAT, longitude = lon, locationTimeMs = now,
                    speedLimitKmh = limit ?: state.speedLimitKmh, timestampMs = now,
                )
                coach.tick(state, now)?.let { said += it }
            }
        }
    }

    private fun geo(startM: Double = -1_000.0, config: CoachConfig = CoachConfig()) =
        GeoDrive(DefaultDriveCoach(config, zones), day, startM, zoneLat, zoneLon)

    @Test
    fun `location numbers follow the spec`() {
        val c = CoachConfig()
        assertEquals(10f, c.k12OverKmh)
        assertEquals(5_000, c.k12HoldMs)
        assertEquals(3 * min, c.k12CooldownMs)
        assertEquals(300, SchoolZone("a", "a", 0.0, 0.0).radiusM)
        assertEquals(30, SchoolZone("a", "a", 0.0, 0.0).limitKmh)
    }

    @Test
    fun `K11 on entering a school zone, once per zone, wording by speed`() {
        val fast = geo()
        fast.drive(60, 45.0) // 남쪽 1km에서 45km/h → 약 700m 지점에서 반경 300m 진입
        val k11 = fast.said.filter { it.ruleId == "K11" }
        assertEquals(1, k11.size)
        assertEquals("어린이 보호구역이에요. 30 아래로 가요.", k11[0].text)
        assertTrue(k11[0].urgent)
        // 구역을 지나 나갔다가 되돌아와 같은 구역에 다시 들어와도 다시 말하지 않는다(통과 수는 센다)
        fast.drive(60, 45.0)
        fast.drive(90, -25.0)
        assertEquals(1, fast.said.count { it.ruleId == "K11" })
        val s = fast.coach.onDriveEnd(fast.now)
        assertEquals(2, s.schoolZonePasses)
        assertEquals(45, s.schoolZoneMaxKmh)

        val slow = geo()
        slow.drive(150, 25.0)
        assertEquals("어린이 보호구역이에요.", slow.said.single { it.ruleId == "K11" }.text)
    }

    @Test
    fun `K11 and K12 are outside the 30 minute quota`() {
        // 말수 제한을 다 쓴 뒤에도 보호구역 안내는 나온다
        val c = CoachConfig(maxUtterancesPerWindow = 0)
        val d = GeoDrive(DefaultDriveCoach(c, zones), day.copy(highBeamOn = true), -1_000.0, zoneLat, zoneLon)
        d.drive(240, 15.0)
        assertEquals(listOf("K11"), d.said.map { it.ruleId })
    }

    @Test
    fun `K12 after five seconds over limit plus ten, then every three minutes`() {
        val d = geo(startM = -50_000.0) // 보호구역에서 멀리
        d.drive(10, 68.0, limit = 60) // +8 → 아님
        assertTrue(d.said.isEmpty())
        d.drive(4, 75.0) // 속도가 중앙값으로 올라가는 데 1~2초, 5초 미만
        assertTrue(d.said.isEmpty())
        d.drive(4, 75.0)
        assertEquals("제한속도가 60이에요. 조금 줄여요.", d.said.single().text)
        assertEquals(false, d.said.single().urgent)
        d.drive(120, 75.0)
        assertEquals(1, d.said.size, "3분 안에는 다시 말하지 않는다")
        d.drive(70, 75.0)
        assertEquals(2, d.said.size)
        // 제한속도를 모르면 판정하지 않는다
        val unknown = geo(startM = -50_000.0)
        unknown.drive(60, 120.0)
        assertTrue(unknown.said.isEmpty())
    }

    @Test
    fun `K12 can be turned off but is still counted for the review`() {
        val d = geo(-50_000.0, CoachConfig(disabledRules = setOf("K07", "K10", "K12")))
        d.drive(20, 80.0, limit = 50)
        d.drive(20, 40.0)
        d.drive(9, 80.0)
        d.drive(10, 40.0)
        assertTrue(d.said.isEmpty())
        val s = d.coach.onDriveEnd(d.now)
        assertEquals(2, s.speedingCount)
        assertTrue(s.longestSpeedingSec in 17..20, "longest=${s.longestSpeedingSec}")
        assertEquals("과속이 2번 있었어요. 가장 길게 ${s.longestSpeedingSec}초예요. 제한속도를 한 번 더 봐요.", s.ruleBasedReview[1])
    }

    @Test
    fun `school zone uses its own 30 limit for speeding and review`() {
        val d = geo()
        d.drive(90, 40.0, limit = 60) // 도로 제한 60이어도 구역 안에서는 30 기준 → 40 = +10, 넘지 않음
        d.drive(30, 45.0)
        val s = d.coach.onDriveEnd(d.now)
        assertTrue(d.said.any { it.ruleId == "K12" && it.text == "제한속도가 30이에요. 조금 줄여요." })
        assertEquals(1, s.speedingCount)
        assertTrue(s.ruleBasedReview[1].startsWith("어린이 보호구역에서 최고 45km/h였고, 과속도 1번"), s.ruleBasedReview[1])
        // 과속 없이 보호구역만 30을 넘었으면 보호구역만 짚는다
        val mild = geo()
        mild.drive(200, 36.0)
        assertEquals("어린이 보호구역에서 최고 36km/h였어요. 다음엔 30 아래로 지나가요.", mild.coach.onDriveEnd(mild.now).ruleBasedReview[1])
    }

    @Test
    fun `a single speed spike in a school zone does not become the top speed`() {
        val d = geo()
        d.drive(80, 36.0) // 남쪽 200m, 보호구역(반경 300m) 안
        // 전달이 밀려 같은 점이 한 번 더 오고(0m) 다음에 두 칸(20m)을 한 번에 따라잡음
        d.drive(1, 0.0)
        d.drive(1, 72.0)
        d.drive(20, 36.0)
        // 한 번만 두 칸을 건넘
        d.drive(1, 72.0)
        d.drive(30, 36.0)
        val s = d.coach.onDriveEnd(d.now)
        assertTrue(s.schoolZoneMaxKmh!! <= 40, "zoneMax=${s.schoolZoneMaxKmh}")
    }

    @Test
    fun `slow pass through a school zone is praised`() {
        val d = geo()
        d.drive(200, 25.0)
        val s = d.coach.onDriveEnd(d.now)
        assertEquals(1, s.schoolZonePasses)
        assertEquals("어린이 보호구역 1곳을 천천히 지났어요.", s.ruleBasedReview[0])
    }
}
