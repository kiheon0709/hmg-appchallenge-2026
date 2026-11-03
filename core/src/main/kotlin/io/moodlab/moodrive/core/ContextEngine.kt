package io.moodlab.moodrive.core

data class ContextResult(
    val scene: Scene,
    val mood: Mood,
    /** 직전 판단과 장면이 바뀌었는지. */
    val changed: Boolean,
    /** 보이스 DJ가 말할 한 문장 (장면이 바뀌었을 때만). */
    val djLine: String?,
)

/**
 * F1 컨텍스트 엔진 (규칙 기반 v1).
 * 스냅샷 스트림을 받아 지금의 장면과 무드 목표를 계산한다. 상태를 가지며(터널 판정, 디바운스) 단일 스레드에서 호출한다.
 */
class ContextEngine(
    private val config: Config = Config(),
) {
    data class Config(
        /** 새 장면이 이 시간 이상 유지돼야 전환 (깜빡임 방지). 안전·터널은 즉시. */
        val sceneHoldMs: Long = 15_000,
        /** 주간 하향등 ON이 이 시간 안에 꺼지면 터널로 본다. 넘으면 그냥 전조등 켠 것. */
        val tunnelMaxMs: Long = 4 * 60_000,
        val fatigueDrivingMinutes: Int = 120,
        val finaleEtaMinutes: Int = 5,
    )

    private var current: Scene? = null
    private var pending: Scene? = null
    private var pendingSinceMs = 0L
    private var daytimeLowBeamSinceMs: Long? = null

    fun reset() {
        current = null
        pending = null
        daytimeLowBeamSinceMs = null
    }

    fun update(s: VehicleSnapshot): ContextResult {
        val candidate = detectScene(s)
        val prev = current
        // 터널·각성·주차·출발은 기다리지 않고 바로 전환
        val immediate = candidate == Scene.TUNNEL || candidate == Scene.FATIGUE_BOOST ||
            candidate == Scene.PARKED || prev == Scene.PARKED || prev == Scene.TUNNEL || prev == null

        val next: Scene = when {
            candidate == prev -> {
                pending = null
                candidate
            }
            immediate -> candidate
            else -> {
                if (pending != candidate) {
                    pending = candidate
                    pendingSinceMs = s.timestampMs
                }
                if (s.timestampMs - pendingSinceMs >= config.sceneHoldMs) candidate else prev
            }
        }
        val changed = next != prev
        if (changed) pending = null
        current = next
        return ContextResult(
            scene = next,
            mood = moodFor(next, s),
            changed = changed,
            djLine = if (changed && prev != null) djLineFor(next) else null,
        )
    }

    private fun detectScene(s: VehicleSnapshot): Scene {
        // 터널 판정용 상태는 장면과 무관하게 항상 갱신
        val tunnel = updateTunnel(s)

        if (!s.isDriving) return Scene.PARKED
        val candidates = buildList {
            if (s.drivingMinutes >= config.fatigueDrivingMinutes && isLateNight(s.minuteOfDay)) add(Scene.FATIGUE_BOOST)
            if (s.wiper == WiperLevel.HIGH) add(Scene.HEAVY_RAIN)
            if (tunnel) add(Scene.TUNNEL)
            if (s.wiper == WiperLevel.INTERMITTENT || s.wiper == WiperLevel.LOW) add(Scene.RAIN)
            if (s.congestion) add(Scene.TRAFFIC_JAM)
            s.etaMinutes?.let { if (it in 0..config.finaleEtaMinutes) add(Scene.ARRIVAL_FINALE) }
            if (s.driveMode == DriveMode.SPORT) add(Scene.SPORT)
            if (isSunset(s)) add(Scene.SUNSET)
            if (!s.isDaytime()) add(Scene.NIGHT)
            if (s.cruiseOn) add(Scene.CRUISING)
            if (s.driveMode == DriveMode.ECO) add(Scene.ECO)
            add(Scene.CLEAR_DAY)
        }
        return candidates.maxBy { it.priority }
    }

    private fun updateTunnel(s: VehicleSnapshot): Boolean {
        if (s.lowBeamOn && s.isDaytime()) {
            val since = daytimeLowBeamSinceMs ?: s.timestampMs.also { daytimeLowBeamSinceMs = it }
            return s.timestampMs - since <= config.tunnelMaxMs
        }
        daytimeLowBeamSinceMs = null
        return false
    }

    private fun isLateNight(minute: Int) = minute >= 23 * 60 || minute < 5 * 60

    private fun isSunset(s: VehicleSnapshot): Boolean {
        val set = s.sunsetMinute ?: return false
        return s.minuteOfDay in (set - 40)..(set + 20)
    }

    /** 장면별 기본 무드 + 가벼운 보정. */
    fun moodFor(scene: Scene, s: VehicleSnapshot): Mood {
        val base = when (scene) {
            Scene.FATIGUE_BOOST -> Mood(0.9f, 0.6f)
            Scene.HEAVY_RAIN -> Mood(-0.7f, -0.4f)
            Scene.TUNNEL -> Mood(0.1f, -0.5f)
            Scene.RAIN -> Mood(-0.4f, -0.2f)
            Scene.TRAFFIC_JAM -> Mood(0.0f, 0.6f)
            Scene.ARRIVAL_FINALE -> Mood(0.7f, 0.8f)
            Scene.SPORT -> Mood(0.8f, 0.3f)
            Scene.SUNSET -> Mood(-0.1f, 0.7f)
            Scene.NIGHT -> Mood(-0.3f, -0.1f)
            Scene.CRUISING -> Mood(0.4f, 0.4f)
            Scene.ECO -> Mood(-0.2f, 0.3f)
            Scene.CLEAR_DAY -> Mood(0.3f, 0.6f)
            Scene.PARKED -> Mood(-0.5f, 0.3f)
        }
        // 실내가 설정 온도보다 더우면 상쾌하게(밝음 +), 추우면 포근하게(에너지 −). 가중치는 최대 0.2.
        val cabin = s.cabinTempC
        val target = s.targetTempC
        if (cabin == null || target == null) return base
        val delta = ((cabin - target) / 10f).coerceIn(-0.2f, 0.2f)
        return if (delta > 0) {
            base.copy(valence = base.valence + delta).clamped()
        } else {
            base.copy(energy = base.energy + delta).clamped()
        }
    }

    private fun djLineFor(scene: Scene): String = when (scene) {
        Scene.FATIGUE_BOOST -> "오래 달리셨네요. 잠 깨는 곡으로 바꾸고, 가까운 휴게소도 찾아볼게요."
        Scene.HEAVY_RAIN -> "비가 많이 와요. 아주 차분한 곡으로 갈게요. 운전 조심하세요."
        Scene.TUNNEL -> "터널이에요. 잠깐 공간감 있는 곡으로 바꿀게요."
        Scene.RAIN -> "비가 오기 시작했네요. 조금 차분하게 갈게요."
        Scene.TRAFFIC_JAM -> "길이 막히네요. 기분 좋아지는 곡으로 바꿀게요."
        Scene.ARRIVAL_FINALE -> "곧 도착이에요. 오늘의 마지막 곡입니다."
        Scene.SPORT -> "스포츠 모드네요. 템포 올립니다."
        Scene.SUNSET -> "노을 시간이에요. 따뜻한 곡으로 갈게요."
        Scene.NIGHT -> "어두워졌네요. 조용한 밤 드라이브 곡으로 바꿀게요."
        Scene.CRUISING -> "도로가 시원하게 뚫렸네요. 그루브 있는 곡으로 갈게요."
        Scene.ECO -> "편안하게 가볼까요."
        Scene.CLEAR_DAY -> "날이 개었네요. 다시 밝게 갈게요."
        Scene.PARKED -> "도착했어요. 오늘 드라이브 카드를 만들어 둘게요."
    }
}
