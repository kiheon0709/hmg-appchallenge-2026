package io.moodlab.sideseat.core

/** 주행 코치 수치(명세 6장). 테스트로 고정한다. */
data class CoachConfig(
    /** 말수 제한: 이 창 안에서 최대 [maxUtterancesPerWindow]번. 즉시 안전 규칙은 막지 않지만 횟수에는 센다. */
    val windowMs: Long = 30 * MIN,
    val maxUtterancesPerWindow: Int = 3,
    /** 말과 말 사이 최소 간격(즉시 안전 규칙 제외). */
    val minGapMs: Long = 20_000,
    /** 안전 규칙은 첫 말 뒤 이 시간이 지나 조건이 계속되면 한 번 더. */
    val safetyRepeatAfterMs: Long = 10 * MIN,
    val k01WiperMs: Long = 20_000,
    val k02DrivingMs: Long = 1 * MIN,
    val k03RecircMs: Long = 5 * MIN,
    val k04RecircMs: Long = 30 * MIN,
    val k04MinOccupants: Int = 2,
    val k05HighBeamMs: Long = 3 * MIN,
    val k06TurnSignalMs: Long = 60_000,
    val k07FogMs: Long = 10 * MIN,
    val k08DrivingMs: Long = 110 * MIN,
    /** K10이 보는 경고등(SDK TelltaleType 이름). TPMS만 구체적으로 말하고 나머지는 일반 문장(명세 6.2). */
    val k10Telltales: Set<String> = setOf("TPMS", "ENGINE_TEMPERATURE", "ENGINE_OIL_PRESSURE", "LOW_VOLT_BATTERY", "ABS", "MASTER_WARNING"),
    /** O1: 깜빡이 없이 이 각도 이상 조향. */
    val o1SteerDeg: Float = 90f,
    val o1SignalLookbackMs: Long = 3_000,
    /** O1 한 번을 센 뒤 핸들이 이 각도 안으로 돌아와야 다음을 센다. */
    val o1ResetDeg: Float = 30f,
    /** O2: [o2WindowMs] 사이 위치 기반 속도가 이만큼 줄고 브레이크 ON. */
    val o2SpeedDropKmh: Float = 20f,
    val o2WindowMs: Long = 2_000,
    val o2CooldownMs: Long = 5_000,
    /** 일몰·일출을 모를 때 기본값. */
    val defaultSunsetMinute: Int = 18 * 60,
    val defaultSunriseMinute: Int = 6 * 60 + 30,
    /** 두 신호 사이 시간이 이보다 길면 끊긴 것으로 보고 누적하지 않는다. */
    val maxTickGapMs: Long = 5_000,
    /**
     * 끈 규칙. 안개등·경고등은 일반 앱이 읽을 수 없어 기본으로 끈다(명세 6.2 실측 반영).
     * 순정 내비가 제한속도를 말하는 실차에서는 "K12"를 넣어 과속 안내를 끈다(명세 6.4).
     */
    val disabledRules: Set<String> = setOf("K07", "K10"),
    /** K11: 구역 반경을 이만큼 더 벗어나야 나간 것으로 본다(경계에서 GPS가 흔들려도 한 번만 세려고). */
    val k11ExitMarginM: Double = 50.0,
    /** K11: 이 속도 이상으로 움직일 때만 보호구역에 들어선 것으로 본다. */
    val k11MinKmh: Float = 5f,
    /** K12: 제한속도보다 이만큼 넘게. */
    val k12OverKmh: Float = 10f,
    /** K12: 이 시간 넘게 이어지면. */
    val k12HoldMs: Long = 5_000,
    /** K12: 다시 말하기까지. */
    val k12CooldownMs: Long = 3 * MIN,
    /** 과속이 이 시간 넘게 풀려 있으면 한 구간이 끝난 것으로 본다. 늦은 위치 콜백으로 속도가 잠깐 낮게 잡혀도 이어 보려고 5초. */
    val speedingEndMs: Long = 5_000,
    /** K13: P를 벗어난 뒤 운전석 안전벨트가 이 시간 넘게 풀려 있으면(신호가 잠깐 늦게 오는 것을 넘기려고 2초). */
    val k13BeltMs: Long = 2_000,
    /** K15: 주행 중 문이 이 시간 넘게 열려 있으면. */
    val k15DoorMs: Long = 1_000,
    /** K16: 와이퍼가 이 시간 넘게 켜져 있는데 사이드미러 열선이 꺼져 있으면. */
    val k16WiperMs: Long = 2 * MIN,
    /** K18: 깜빡이 없이 크게 조향한 뒤 이 시간 안에만 말한다(늦으면 무엇을 말하는지 모른다). */
    val k18FreshMs: Long = 5_000,
    /** 리뷰·홈: 연료·배터리가 이 % 이하면 한 줄. */
    val lowFuelPct: Int = 15,
    val lowBatteryPct: Int = 20,
) {
    companion object {
        const val MIN = 60_000L
    }
}

/**
 * 주행 코치(명세 6장). 여러 신호를 엮어야 알 수 있는 것만 한 문장으로 말한다.
 * @param practiced 훈련에서 연습한 상황(K09). 앱이 [TrainingProgress]에서 넘긴다.
 */
class DefaultDriveCoach(
    private val config: CoachConfig = CoachConfig(),
    /** 어린이 보호구역(K11). 앱이 assets/zones에서 읽어 넘긴다. */
    private val zones: SchoolZoneIndex = SchoolZoneIndex.EMPTY,
    private val practiced: () -> Set<SituationId> = { emptySet() },
) : DriveCoach {

    /** @param exempt 30분당 말수 제한·말 간격에서 빼고, 자기 규칙(구역당 1번, 3분에 1번)으로만 제한한다. */
    private class Rule(val id: String, val safety: Boolean, val urgent: Boolean, val exempt: Boolean = false)

    /** 같은 조치를 권하는 규칙 묶음. 한 묶음에서는 하나만 말한다(비 + 어두움 → "전조등을 켜 주세요"는 한 번). */
    private val sameAdvice = listOf(setOf("K01", "K02"), setOf("K03", "K04"))

    private fun adviceGroup(id: String): Set<String> = sameAdvice.firstOrNull { id in it } ?: setOf(id)

    private val rules = listOf(
        Rule("K13", safety = true, urgent = true),
        Rule("K15", safety = true, urgent = true),
        Rule("K14", safety = true, urgent = true),
        Rule("K10", safety = true, urgent = true),
        Rule("K11", safety = true, urgent = true, exempt = true),
        Rule("K01", safety = true, urgent = true),
        Rule("K02", safety = true, urgent = true),
        Rule("K12", safety = true, urgent = false, exempt = true),
        Rule("K05", safety = true, urgent = false),
        Rule("K06", safety = true, urgent = false),
        Rule("K03", safety = false, urgent = false),
        Rule("K04", safety = false, urgent = false),
        Rule("K07", safety = false, urgent = false),
        Rule("K08", safety = false, urgent = false),
        Rule("K16", safety = false, urgent = false),
        Rule("K17", safety = false, urgent = false),
        Rule("K18", safety = false, urgent = false),
    )

    private var driveStartMs = 0L
    private var lastTickMs: Long? = null
    private val since = mutableMapOf<String, Long>()
    private val timers = mutableMapOf<String, Long>()
    private var turnSide: TurnSignal? = null
    private val spokenAt = mutableMapOf<String, MutableList<Long>>()
    private val utterances = mutableListOf<CoachUtterance>()
    private val utteranceTimes = mutableListOf<Long>()
    private var lastSpokenMs: Long? = null
    private val knownTelltales = mutableSetOf<String>()
    private val announcedTelltales = mutableSetOf<String>()
    private var rainMs = 0L
    private var nightMs = 0L
    private val observations = mutableMapOf<String, Int>()
    private var lastSignalOnMs: Long? = null
    private var o1Armed = true
    private val speedSamples = ArrayDeque<Pair<Long, Float>>()
    private var lastHardBrakeMs: Long? = null
    private var firstTick = true
    /** K18: 깜빡이 없이 크게 조향한 순간. */
    private var noSignalAt: Long? = null
    /** 리뷰: 주행 시작·마지막 누적 거리(Odometer), 마지막 상태(차가 알려 준 것). */
    private var odoStart: Float? = null
    private var odoLast: Float? = null
    private var lastState: VehicleState? = null

    // 위치 연계(명세 6.4)
    private val track = LocationTrack()
    private var speedKmh: Float? = null
    /** 직전 위치 갱신 때의 속도. 보호구역 최고 속도는 두 번 연속 본 값(둘 중 작은 값)으로만 올린다. */
    private var prevSpeedKmh: Float? = null
    private var lastFixMs: Long? = null
    private var zone: SchoolZone? = null
    private var zoneToAnnounce: SchoolZone? = null
    private val announcedZones = mutableSetOf<String>()
    private var zonePasses = 0
    private var zoneMaxKmh: Float? = null
    private var zoneOverLimit: Pair<Float, Int>? = null
    private var effectiveLimit: Int? = null
    private var speedingStart: Long? = null
    private var lastOverMs = 0L
    private var speedingCounted = false
    private var speedingCount = 0
    private var longestSpeedingMs = 0L
    private var lastK12Ms: Long? = null

    override fun onDriveStart(nowMs: Long) {
        driveStartMs = nowMs
        lastTickMs = null
        since.clear()
        timers.clear()
        turnSide = null
        spokenAt.clear()
        utterances.clear()
        utteranceTimes.clear()
        lastSpokenMs = null
        knownTelltales.clear()
        announcedTelltales.clear()
        rainMs = 0
        nightMs = 0
        observations.clear()
        lastSignalOnMs = null
        o1Armed = true
        speedSamples.clear()
        lastHardBrakeMs = null
        firstTick = true
        noSignalAt = null
        odoStart = null
        odoLast = null
        lastState = null
        track.clear()
        speedKmh = null
        prevSpeedKmh = null
        lastFixMs = null
        zone = null
        zoneToAnnounce = null
        announcedZones.clear()
        zonePasses = 0
        zoneMaxKmh = null
        zoneOverLimit = null
        effectiveLimit = null
        speedingStart = null
        lastOverMs = 0
        speedingCounted = false
        speedingCount = 0
        longestSpeedingMs = 0
        lastK12Ms = null
    }

    override fun tick(state: VehicleState, nowMs: Long): CoachUtterance? {
        val dt = lastTickMs?.let { (nowMs - it).takeIf { d -> d in 0..config.maxTickGapMs } } ?: 0
        lastTickMs = nowMs
        if (state.wiperOn()) rainMs += dt
        if (isNight(state)) nightMs += dt
        updateLocation(state, nowMs)
        observe(state, nowMs)
        state.odometerKm?.let { if (odoStart == null) odoStart = it; odoLast = it }
        lastState = state

        val newTelltale = newWarningTelltale(state)
        updateTimers(state, nowMs)
        val candidates = rules.filter {
            it.id !in config.disabledRules && condition(it.id, state, nowMs, newTelltale) && canSpeak(it, nowMs)
        }
        val rule = candidates.firstOrNull() ?: return null
        val u = utter(rule, state, newTelltale)
        if (rule.id == "K10" && newTelltale != null) announcedTelltales += newTelltale.first
        spokenAt.getOrPut(rule.id) { mutableListOf() } += nowMs
        when (rule.id) {
            "K11" -> zoneToAnnounce?.let {
                announcedZones += it.id
                zoneToAnnounce = null
            }
            "K12" -> lastK12Ms = nowMs
            "K18" -> noSignalAt = null
        }
        if (!rule.exempt) {
            utteranceTimes += nowMs
            lastSpokenMs = nowMs
        }
        utterances += u
        return u
    }

    override fun onReply(ruleId: String, reply: String, nowMs: Long): CoachAction? {
        if (ruleId != "K08") return null
        return if (isYes(reply)) CoachAction.AddRestStopWaypoint("두 시간 가까이 연속 주행") else null
    }

    override fun onDriveEnd(nowMs: Long): DriveSummary {
        val durationMin = ((nowMs - driveStartMs) / CoachConfig.MIN).toInt()
        val rainMin = (rainMs / CoachConfig.MIN).toInt()
        val nightMin = (nightMs / CoachConfig.MIN).toInt()
        val obs = observations.toMap()
        val suggestion = suggest(obs)
        return DriveSummary(
            durationMin = durationMin,
            rainMin = rainMin,
            nightMin = nightMin,
            utterances = utterances.toList(),
            observations = obs,
            suggestedSituation = suggestion,
            ruleBasedReview = review(durationMin, rainMin, nightMin, obs, suggestion),
            durationSec = ((nowMs - driveStartMs) / 1000).toInt(),
            rainSec = (rainMs / 1000).toInt(),
            nightSec = (nightMs / 1000).toInt(),
            speedingCount = speedingCount,
            longestSpeedingSec = (longestSpeedingMs / 1000).toInt(),
            schoolZonePasses = zonePasses,
            schoolZoneMaxKmh = zoneMaxKmh?.let { Math.round(it) },
            distanceKm = odoStart?.let { a -> odoLast?.let { b -> (b - a).coerceAtLeast(0f) } },
            carNotes = lastState?.let { carNotes(it, config) }.orEmpty(),
        )
    }

    // ---------- 규칙 ----------

    /** 신호가 켜진 채 이어진 시간을 잰다. 꺼지면 지운다. */
    private fun track(key: String, on: Boolean, nowMs: Long): Long {
        if (!on) {
            since.remove(key)
            return -1
        }
        return nowMs - since.getOrPut(key) { nowMs }
    }

    private fun updateTimers(s: VehicleState, nowMs: Long) {
        timers["wiper"] = track("wiper", s.wiperOn(), nowMs)
        timers["recirc"] = track("recirc", s.recirculationOn == true, nowMs)
        timers["highBeam"] = track("highBeam", s.highBeamOn == true, nowMs)
        timers["fogOnly"] = track("fogOnly", s.anyFogOn == true && s.wiper == WiperLevel.OFF && s.lowBeamOn == false, nowMs)
        val side = s.turnSignal?.takeIf { it == TurnSignal.LEFT || it == TurnSignal.RIGHT }
        // 방향이 바뀌면 처음부터 센다.
        if (side != turnSide) since.remove("turn")
        turnSide = side
        timers["turn"] = track("turn", side != null, nowMs)
        timers["belt"] = track("belt", s.driverSeatBeltBuckled == false, nowMs)
        timers["door"] = track("door", s.anyDoorOpen == true, nowMs)
    }

    private fun lasted(key: String, ms: Long) = (timers[key] ?: -1) >= ms

    private fun condition(id: String, s: VehicleState, nowMs: Long, newTelltale: Pair<String, TelltaleColor>?): Boolean {
        val driving = nowMs - driveStartMs
        return when (id) {
            "K01" -> s.lowBeamOn == false && lasted("wiper", config.k01WiperMs)
            "K02" -> s.lowBeamOn == false && isNight(s) && driving >= config.k02DrivingMs
            "K03" -> s.wiperOn() && s.acOn == false && lasted("recirc", config.k03RecircMs)
            "K04" -> s.occupied.size >= config.k04MinOccupants && lasted("recirc", config.k04RecircMs)
            "K05" -> lasted("highBeam", config.k05HighBeamMs)
            "K06" -> lasted("turn", config.k06TurnSignalMs)
            "K07" -> lasted("fogOnly", config.k07FogMs)
            "K08" -> driving >= config.k08DrivingMs
            "K10" -> newTelltale != null
            "K13" -> lasted("belt", config.k13BeltMs)
            "K14" -> s.tirePressureLow == true
            "K15" -> lasted("door", config.k15DoorMs)
            "K16" -> s.sideMirrorHeatOn == false && lasted("wiper", config.k16WiperMs)
            "K17" -> s.washerFluidLow == true && s.wiperOn()
            "K18" -> noSignalAt?.let { nowMs - it <= config.k18FreshMs } == true
            "K11" -> zoneToAnnounce?.let { it.id !in announcedZones } == true
            "K12" -> {
                val start = speedingStart
                start != null && nowMs - start >= config.k12HoldMs && lastOverMs == nowMs &&
                    lastK12Ms?.let { nowMs - it >= config.k12CooldownMs } != false
            }
            else -> false
        }
    }

    /** 말수 제한(6.1). 같은 규칙은 한 번, 안전 규칙은 10분 뒤 한 번 더. K10은 경고등마다. */
    private fun canSpeak(rule: Rule, nowMs: Long): Boolean {
        if (rule.exempt) return true
        // 같은 조치를 권하는 규칙끼리는 횟수와 간격을 함께 센다(목록에서 앞선, 더 급한 규칙이 먼저 말한다)
        val times = adviceGroup(rule.id).flatMap { spokenAt[it].orEmpty() }.sorted()
        if (rule.id != "K10") {
            val allowed = if (rule.safety) 2 else 1
            if (times.size >= allowed) return false
            if (times.isNotEmpty() && nowMs - times.last() < config.safetyRepeatAfterMs) return false
        }
        if (rule.urgent) return true
        val recent = utteranceTimes.count { nowMs - it < config.windowMs }
        if (recent >= config.maxUtterancesPerWindow) return false
        val last = lastSpokenMs
        return last == null || nowMs - last >= config.minGapMs
    }

    private fun utter(rule: Rule, s: VehicleState, newTelltale: Pair<String, TelltaleColor>?): CoachUtterance {
        val practicedSet = practiced()
        val text = when (rule.id) {
            "K01" -> practicedPrefix(SituationId.HEAVY_RAIN, practicedSet) + "비가 와요. 전조등을 켜 주세요."
            "K02" -> "어두워졌어요. 전조등을 켜 주세요."
            "K03" -> practicedPrefix(SituationId.FOGGED_WINDSHIELD, practicedSet) +
                "앞유리에 김이 설 수 있어요. 외기로 바꾸거나 에어컨을 켜 보세요."
            "K04" -> "공기가 답답할 수 있어요. 잠깐 외기로 바꿔 보세요."
            "K05" -> "상향등이 계속 켜져 있어요."
            "K06" -> "깜빡이가 계속 켜져 있어요."
            "K07" -> "안개등이 켜져 있어요. 맑으면 꺼 주세요."
            "K08" -> "두 시간 가까이 달렸어요. 다음 휴게소에서 쉬어 갈까요?"
            "K11" -> {
                val limit = zoneToAnnounce?.limitKmh ?: 30
                val v = speedKmh
                if (v != null && v <= limit) "어린이 보호구역이에요." else "어린이 보호구역이에요. $limit 아래로 가요."
            }
            "K12" -> "제한속도가 ${effectiveLimit}이에요. 조금 줄여요."
            // 경고등 색깔 일반 규칙은 공식 근거가 없어 쓰지 않는다(safety-sources.md 8절).
            "K13" -> "안전벨트부터 매 주세요."
            "K14" -> practicedPrefix(SituationId.WARNING_LIGHT, practicedSet) + "타이어 공기압이 낮아요. 속도를 줄이고 가까운 곳에서 확인해요."
            "K15" -> "문이 열려 있어요. 안전한 곳에 세우고 닫아요."
            "K16" -> "비가 계속 와요. 사이드미러도 뿌옇게 돼요. 열선을 켜 보세요."
            "K17" -> "워셔액이 부족해요. 다음에 세웠을 때 채워 주세요."
            "K18" -> "방금은 깜빡이 없이 차로를 바꿨어요. 다음엔 먼저 켜요."
            "K10" -> if (newTelltale?.first == "TPMS") {
                "타이어 공기압 경고등이 켜졌어요. 속도를 줄이고 가까운 곳에서 공기압을 확인해 주세요."
            } else {
                "경고등이 켜졌어요. 안전한 곳에 세우고 취급설명서를 같이 볼게요."
            }
            else -> ""
        }
        val urgent = rule.urgent
        return CoachUtterance(
            ruleId = rule.id,
            text = text,
            urgent = urgent,
            expectReply = if (rule.id == "K08") ReplyKind.YES_NO else null,
        )
    }

    /** K09: 연습한 상황이 실제로 일어나면 앞에 붙인다. */
    private fun practicedPrefix(id: SituationId, practicedSet: Set<SituationId>) =
        if (id in practicedSet) "연습했던 거예요. " else ""

    /** 주행 중 새로 켜진 경고등. 주행 시작 때 이미 켜져 있던 것은 알리지 않는다(시동 직후 점검등). */
    private fun newWarningTelltale(s: VehicleState): Pair<String, TelltaleColor>? {
        val watched = s.activeTelltales.filterKeys { it in config.k10Telltales }
        if (firstTick) {
            firstTick = false
            knownTelltales += watched.keys
            return null
        }
        knownTelltales.retainAll(watched.keys)
        val fresh = watched.entries.firstOrNull { it.key !in knownTelltales && it.key !in announcedTelltales }
        knownTelltales += watched.keys
        return fresh?.let { it.key to it.value }
    }

    // ---------- 위치 연계: 속도, 보호구역, 과속 구간(명세 6.4) ----------

    private fun updateLocation(s: VehicleState, nowMs: Long) {
        val lat = s.latitude
        val lon = s.longitude
        val fixMs = s.locationTimeMs ?: nowMs
        val newFix = lat != null && lon != null && fixMs != lastFixMs
        if (lat != null && lon != null) {
            if (newFix) {
                track.add(lat, lon, fixMs)
                lastFixMs = fixMs
                prevSpeedKmh = speedKmh
                speedKmh = track.speedKmh()
            }
        } else {
            prevSpeedKmh = speedKmh
            speedKmh = s.speedKmhEstimate
        }
        val v = speedKmh
        // 한 번 튄 값으로 "최고 72km/h"가 남지 않게 두 번 연속 본 값만 쓴다.
        val confirmed = v?.let { cur -> prevSpeedKmh?.let { minOf(it, cur) } }

        // 보호구역: 반경 안이면 진입, 반경 + 여유를 벗어나면 나감.
        if (lat != null && lon != null) {
            val current = zone
            if (current != null) {
                val d = GeoMath.distanceMeters(lat, lon, current.latitude, current.longitude)
                if (d > current.radiusM + config.k11ExitMarginM) {
                    zone = null
                    if (zoneToAnnounce == current) zoneToAnnounce = null
                }
            }
            // 서 있거나 속도를 아직 모르면 진입으로 보지 않는다(보호구역 안에서 출발할 때 시속 0에 안내하고 "최고 0km/h 통과"로 남지 않게)
            if (zone == null && (v ?: 0f) >= config.k11MinKmh) {
                zones.nearestWithin(lat, lon)?.first?.let { z ->
                    zone = z
                    zonePasses++
                    if (z.id !in announcedZones) zoneToAnnounce = z
                }
            }
            zone?.let { z ->
                if (confirmed != null) {
                    zoneMaxKmh = maxOf(zoneMaxKmh ?: 0f, confirmed)
                    if (confirmed > z.limitKmh && (zoneOverLimit?.first ?: 0f) < confirmed) zoneOverLimit = confirmed to z.limitKmh
                }
            }
        }

        // 과속: 보호구역 안이면 구역 제한, 아니면 도로 제한.
        val limit = zone?.limitKmh ?: s.speedLimitKmh
        effectiveLimit = limit
        val over = v != null && limit != null && v > limit + config.k12OverKmh
        if (over) {
            if (speedingStart == null) {
                speedingStart = nowMs
                speedingCounted = false
            }
            lastOverMs = nowMs
            val start = speedingStart!!
            if (!speedingCounted && nowMs - start >= config.k12HoldMs) {
                speedingCounted = true
                speedingCount++
            }
            if (speedingCounted) longestSpeedingMs = maxOf(longestSpeedingMs, nowMs - start)
        } else if (speedingStart != null && nowMs - lastOverMs >= config.speedingEndMs) {
            speedingStart = null
            speedingCounted = false
        }
    }

    // ---------- 관찰(말하지 않음) ----------

    private fun observe(s: VehicleState, nowMs: Long) {
        if (s.turnSignal != null && s.turnSignal != TurnSignal.OFF) lastSignalOnMs = nowMs
        s.steeringAngleDeg?.let { angle ->
            val abs = kotlin.math.abs(angle)
            if (abs < config.o1ResetDeg) o1Armed = true
            if (o1Armed && abs >= config.o1SteerDeg) {
                o1Armed = false
                val signalled = lastSignalOnMs?.let { nowMs - it <= config.o1SignalLookbackMs } == true
                if (!signalled) {
                    count("NO_SIGNAL_TURN")
                    noSignalAt = nowMs
                }
            }
        }
        speedKmh?.let { v ->
            speedSamples.addLast(nowMs to v)
            while (speedSamples.isNotEmpty() && nowMs - speedSamples.first().first > config.o2WindowMs) speedSamples.removeFirst()
            val peak = speedSamples.maxOf { it.second }
            val cooled = lastHardBrakeMs?.let { nowMs - it >= config.o2CooldownMs } ?: true
            if (s.brakePressed == true && peak - v >= config.o2SpeedDropKmh && cooled) {
                lastHardBrakeMs = nowMs
                count("HARD_BRAKE")
            }
        }
    }

    private fun count(key: String) {
        observations[key] = (observations[key] ?: 0) + 1
    }

    // ---------- 리뷰 ----------

    private fun suggest(obs: Map<String, Int>): SituationId? {
        val byRule = mapOf(
            "K01" to SituationId.HEAVY_RAIN,
            "K03" to SituationId.FOGGED_WINDSHIELD,
            "K10" to SituationId.WARNING_LIGHT,
            "K05" to SituationId.ONCOMING_GLARE,
            "K07" to SituationId.DENSE_FOG,
            "K02" to SituationId.TUNNEL,
            "K08" to SituationId.DROWSY,
        )
        // 보호구역을 제한속도보다 빠르게 지났으면 그 연습을 먼저 권한다
        if (zoneOverLimit != null) return SituationId.SCHOOL_ZONE
        utterances.firstNotNullOfOrNull { byRule[it.ruleId] ?: if (it.ruleId == "K14") SituationId.WARNING_LIGHT else null }?.let { return it }
        if ((obs["HARD_BRAKE"] ?: 0) > 0) return SituationId.SUDDEN_STOP
        // 비·밤만으로 권할 때는 1분 넘게 그 길을 달렸을 때만(잠깐 움직인 것으로 "지난 주행에서…"라고 하지 않게)
        if (rainMs >= CoachConfig.MIN) return SituationId.HEAVY_RAIN
        // 과속이 있던 주행에 밤길만으로 상관없는 연습을 권하지 않는다
        if (nightMs >= CoachConfig.MIN && speedingCount == 0) return SituationId.ONCOMING_GLARE
        return null
    }

    private fun review(durationMin: Int, rainMin: Int, nightMin: Int, obs: Map<String, Int>, suggestion: SituationId?): List<String> {
        val said = utterances.map { it.ruleId }.toSet()
        // 과속이나 보호구역 초과가 있었으면 첫 문장에서 "침착하게 잘 왔어요"라고 하지 않는다
        val violated = zoneOverLimit != null || speedingCount > 0
        val span = if (durationMin >= 1) "${durationMin}분 동안" else "짧은 길이었지만"
        // 같은 조치를 권하는 규칙은 묶어서 본다: 비 오는 밤에 K01("비가 와요. 전조등을…")을 들었으면 전조등을 놓친 것이다
        fun advised(id: String) = adviceGroup(id).any { it in said }
        val good = when {
            // 과속이나 보호구역 초과가 있었으면 칭찬이 아니라 속도부터 짚는다
            violated -> "오늘은 속도부터 짚을게요."
            zonePasses > 0 && zoneMaxKmh != null -> "어린이 보호구역 ${zonePasses}곳을 천천히 지났어요."
            rainMin > 0 && !advised("K01") -> "비 오는 길에서도 전조등을 잘 챙겼어요."
            nightMin > 0 && !advised("K02") -> "어두워지자 전조등을 바로 켰어요."
            utterances.isEmpty() -> "오늘은 제가 한 번도 끼어들 일이 없었어요."
            else -> "$span 침착하게 잘 왔어요."
        }
        val noSignal = obs["NO_SIGNAL_TURN"] ?: 0
        val hardBrake = obs["HARD_BRAKE"] ?: 0
        val fix = when {
            // 보호구역 과속과 도로 과속이 함께 있으면 한 문장으로 같이 짚는다(명세 6.4 리뷰).
            zoneOverLimit != null && speedingCount > 0 -> zoneOverLimit!!.let { (v, limit) ->
                "어린이 보호구역에서 최고 ${Math.round(v)}km/h였고, 과속도 ${speedingCount}번(가장 길게 ${longestSpeedingMs / 1000}초) 있었어요. " +
                    "보호구역은 $limit 아래로, 다른 길은 제한속도를 한 번 더 봐요."
            }
            zoneOverLimit != null -> zoneOverLimit!!.let { (v, limit) ->
                "어린이 보호구역에서 최고 ${Math.round(v)}km/h였어요. 다음엔 $limit 아래로 지나가요."
            }
            speedingCount > 0 -> "과속이 ${speedingCount}번 있었어요. 가장 길게 ${longestSpeedingMs / 1000}초예요. 제한속도를 한 번 더 봐요."
            noSignal > 0 -> "깜빡이 없이 차로를 바꾼 게 ${noSignal}번 있었어요. 바꾸기 전에 먼저 켜 봐요."
            hardBrake > 0 -> "급하게 밟은 브레이크가 ${hardBrake}번 있었어요. 앞차와 거리를 조금 더 둬 봐요."
            "K01" in said -> "비가 오면 와이퍼와 전조등을 같이 켜는 습관을 들여 봐요."
            "K03" in said -> "비 올 땐 내기순환보다 외기가 김서림에 좋아요."
            "K05" in said -> "상향등은 앞이나 맞은편에 차가 보이면 내려 주세요."
            "K06" in said -> "차로를 바꾼 뒤엔 깜빡이가 꺼졌는지 봐요."
            utterances.isNotEmpty() -> "제가 말한 것 하나만 다음에 기억해 봐요."
            else -> "고칠 건 딱히 없었어요. 이대로만 가요."
        }
        val next = suggestion?.let { "다음에 세웠을 때 ${SituationCatalog.title(it)} 훈련을 1분만 해 볼까요?" }
            ?: "다음에 세웠을 때 돌발 모드로 감을 유지해 봐요."
        return listOf(good, fix, next)
    }

    // ---------- 보조 ----------

    private fun isNight(s: VehicleState): Boolean {
        val set = s.sunsetMinute ?: config.defaultSunsetMinute
        val rise = s.sunriseMinute ?: config.defaultSunriseMinute
        return s.minuteOfDay >= set || s.minuteOfDay < rise
    }

    private fun VehicleState.wiperOn(): Boolean = when (wiper) {
        WiperLevel.INTERMITTENT, WiperLevel.LOW, WiperLevel.HIGH -> true
        else -> false
    }

    companion object {
        /**
         * 차가 알려 준 것 한 줄씩(리뷰 "타비가 본 것"과 홈 인사 아래). 읽을 수 없는 신호는 null이라 나오지 않는다.
         * 브레이크액은 안전 문제라 운전을 권하지 않는다. 문장은 취급설명서 수준의 일반 안내다.
         */
        fun carNotes(s: VehicleState, config: CoachConfig = CoachConfig()): List<String> = listOfNotNull(
            "브레이크액이 부족해요. 차를 움직이지 말고 정비소에 먼저 연락해요.".takeIf { s.brakeFluidLow == true },
            "타이어 공기압이 낮아요. 가까운 곳에서 공기압을 확인해요.".takeIf { s.tirePressureLow == true },
            "엔진오일이 부족해요. 다음에 운전하기 전에 점검해요.".takeIf { s.engineOilLow == true },
            s.fuelPercent?.takeIf { it <= config.lowFuelPct }?.let { "연료가 ${it}% 남았어요. 다음 길에 주유소를 들러요." },
            s.evBatteryPercent?.takeIf { it <= config.lowBatteryPct }?.let { "배터리가 ${it}% 남았어요. 다음 길에 충전해요." },
            "워셔액이 부족해요. 채워 두면 비 오는 날 편해요.".takeIf { s.washerFluidLow == true },
        )

        private val YES = listOf("응", "네", "예", "그래", "좋아", "가자", "콜", "오케이", "ㅇㅇ")
        private val NO = listOf("아니", "괜찮", "나중", "됐어", "싫어", "안 가", "안가", "모르")

        /** 짧은 대답 해석. 부정이 먼저다("아니 괜찮아"). "어"는 낱말 하나일 때만 긍정. */
        fun isYes(reply: String): Boolean {
            val r = reply.trim()
            if (NO.any { it in r }) return false
            val words = r.split(" ", ",", ".", "!").filter { it.isNotEmpty() }
            return words.firstOrNull() == "어" || YES.any { it in r }
        }
    }
}
