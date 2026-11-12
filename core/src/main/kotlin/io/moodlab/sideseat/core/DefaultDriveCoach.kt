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
    /** 끈 규칙. 안개등·경고등은 일반 앱이 읽을 수 없어 기본으로 끈다(명세 6.2 실측 반영). */
    val disabledRules: Set<String> = setOf("K07", "K10"),
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
    private val practiced: () -> Set<SituationId> = { emptySet() },
) : DriveCoach {

    private class Rule(val id: String, val safety: Boolean, val urgent: Boolean)

    private val rules = listOf(
        Rule("K10", safety = true, urgent = true),
        Rule("K01", safety = true, urgent = true),
        Rule("K02", safety = true, urgent = true),
        Rule("K05", safety = true, urgent = false),
        Rule("K06", safety = true, urgent = false),
        Rule("K03", safety = false, urgent = false),
        Rule("K04", safety = false, urgent = false),
        Rule("K07", safety = false, urgent = false),
        Rule("K08", safety = false, urgent = false),
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
    }

    override fun tick(state: VehicleState, nowMs: Long): CoachUtterance? {
        val dt = lastTickMs?.let { (nowMs - it).takeIf { d -> d in 0..config.maxTickGapMs } } ?: 0
        lastTickMs = nowMs
        if (state.wiperOn()) rainMs += dt
        if (isNight(state)) nightMs += dt
        observe(state, nowMs)

        val newTelltale = newWarningTelltale(state)
        updateTimers(state, nowMs)
        val candidates = rules.filter {
            it.id !in config.disabledRules && condition(it.id, state, nowMs, newTelltale) && canSpeak(it, nowMs)
        }
        val rule = candidates.firstOrNull() ?: return null
        val u = utter(rule, state, newTelltale)
        if (rule.id == "K10" && newTelltale != null) announcedTelltales += newTelltale.first
        spokenAt.getOrPut(rule.id) { mutableListOf() } += nowMs
        utteranceTimes += nowMs
        lastSpokenMs = nowMs
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
            else -> false
        }
    }

    /** 말수 제한(6.1). 같은 규칙은 한 번, 안전 규칙은 10분 뒤 한 번 더. K10은 경고등마다. */
    private fun canSpeak(rule: Rule, nowMs: Long): Boolean {
        val times = spokenAt[rule.id].orEmpty()
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
            // 경고등 색깔 일반 규칙은 공식 근거가 없어 쓰지 않는다(safety-sources.md 8절).
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

    // ---------- 관찰(말하지 않음) ----------

    private fun observe(s: VehicleState, nowMs: Long) {
        if (s.turnSignal != null && s.turnSignal != TurnSignal.OFF) lastSignalOnMs = nowMs
        s.steeringAngleDeg?.let { angle ->
            val abs = kotlin.math.abs(angle)
            if (abs < config.o1ResetDeg) o1Armed = true
            if (o1Armed && abs >= config.o1SteerDeg) {
                o1Armed = false
                val signalled = lastSignalOnMs?.let { nowMs - it <= config.o1SignalLookbackMs } == true
                if (!signalled) count("NO_SIGNAL_TURN")
            }
        }
        s.speedKmhEstimate?.let { v ->
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
        utterances.firstNotNullOfOrNull { byRule[it.ruleId] }?.let { return it }
        if ((obs["HARD_BRAKE"] ?: 0) > 0) return SituationId.SUDDEN_STOP
        if (rainMs > 0) return SituationId.HEAVY_RAIN
        if (nightMs > 0) return SituationId.ONCOMING_GLARE
        return null
    }

    private fun review(durationMin: Int, rainMin: Int, nightMin: Int, obs: Map<String, Int>, suggestion: SituationId?): List<String> {
        val said = utterances.map { it.ruleId }.toSet()
        val good = when {
            rainMin > 0 && "K01" !in said -> "비 오는 길에서도 전조등을 잘 챙겼어요."
            nightMin > 0 && "K02" !in said -> "어두워지자 전조등을 바로 켰어요."
            utterances.isEmpty() -> "오늘은 제가 한 번도 끼어들 일이 없었어요."
            else -> "${durationMin}분 동안 침착하게 잘 왔어요."
        }
        val noSignal = obs["NO_SIGNAL_TURN"] ?: 0
        val hardBrake = obs["HARD_BRAKE"] ?: 0
        val fix = when {
            noSignal > 0 -> "깜빡이 없이 크게 돈 게 ${noSignal}번 있었어요. 돌기 전에 먼저 켜 봐요."
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
