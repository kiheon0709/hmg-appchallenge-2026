package io.moodlab.moodrive.core

data class ContextResult(
    val scene: Scene,
    val mood: Mood,
    /** 직전 판단과 장면이 바뀌었는지. */
    val changed: Boolean,
    /** 보이스 DJ가 말할 한 문장 (장면이 바뀌었을 때만). */
    val djLine: String?,
    /**
     * 장시간 운전 경보. 장면과 별개로 겹쳐서 켜진다(장면을 덮지 않음 → 폭우·정체·피날레 판정은 계속 동작).
     * 한국도로공사 권고: 2시간 이상 연속 운전 시 휴식. 심야(23~05시)는 90분.
     */
    val fatigue: Boolean = false,
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
        /** 한국도로공사 권고: 2시간 이상 연속 운전 시 휴식. 심야(23~05시)에는 90분부터. */
        val fatigueDrivingMinutes: Int = 120,
        val fatigueLateNightMinutes: Int = 90,
        val finaleEtaMinutes: Int = 5,
        /** 신호대기·잠깐 정차로 매번 '도착'이 되지 않도록, P단이 이 시간 이상 이어져야 정차 장면 */
        val parkConfirmMs: Long = 60_000,
        /** 목적지까지 아직 멀면(ETA > 피날레 기준) 휴게소·주유소 정차로 보고 30분 기다린다 */
        val midTripParkConfirmMs: Long = 30 * 60_000,
        /** 목적지가 없으면 5분 정차부터 주행 종료로 본다 */
        val noDestParkConfirmMs: Long = 5 * 60_000,
    )

    private var current: Scene? = null
    private var pending: Scene? = null
    private var pendingSinceMs = 0L
    private var daytimeLowBeamSinceMs: Long? = null
    private var prevLowBeam: Boolean? = null
    private var parkedSinceMs: Long? = null

    fun reset() {
        current = null
        pending = null
        daytimeLowBeamSinceMs = null
        prevLowBeam = null
        parkedSinceMs = null
    }

    fun update(s: VehicleSnapshot): ContextResult {
        val candidate = detectScene(s)
        val prev = current
        // 터널·각성·주차·출발은 기다리지 않고 바로 전환
        val immediate = candidate == Scene.TUNNEL ||
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
            djLine = if (changed && prev != null) djLineFor(next, prev) else null,
            fatigue = s.isDriving && s.drivingMinutes >= fatigueThreshold(s.minuteOfDay),
        )
    }

    fun fatigueThreshold(minuteOfDay: Int): Int =
        if (isLateNight(minuteOfDay)) config.fatigueLateNightMinutes else config.fatigueDrivingMinutes

    private fun detectScene(s: VehicleSnapshot): Scene {
        // 터널 판정용 상태는 장면과 무관하게 항상 갱신
        val tunnel = updateTunnel(s)

        if (!s.isDriving) {
            val since = parkedSinceMs ?: s.timestampMs.also { parkedSinceMs = it }
            val prev = current
            // 출발 전(첫 판정)이거나 이미 정차면 바로. 주행 중 P단이면 60초, 목적지가 아직 멀면 10분 기다림
            val eta = s.etaMinutes
            val confirm = when {
                eta == null -> config.noDestParkConfirmMs
                eta > config.finaleEtaMinutes -> config.midTripParkConfirmMs
                else -> config.parkConfirmMs
            }
            return if (prev == null || prev == Scene.PARKED || s.timestampMs - since >= confirm) Scene.PARKED else prev
        }
        parkedSinceMs = null
        val candidates = buildList {
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

    /**
     * 터널 추정: 주간에 하향등이 **꺼짐→켜짐으로 바뀐 순간**부터 [Config.tunnelMaxMs] 동안.
     * 제외: 와이퍼 작동 중(비·흐림 오토라이트), 비가 그쳐도 이미 켜져 있던 전조등, 주간 상시 점등,
     * 일몰 15분 전~60분 후(해질녘 오토라이트).
     * 한계: 야간 터널, 4분 넘는 장대 터널은 구분 못 함 → NaviHelper 도로 정보가 확인되면 교체.
     */
    private fun updateTunnel(s: VehicleSnapshot): Boolean {
        // 정차 중에 켠 전조등은 터널 진입이 아니다 → 주행 중의 전환만 인정
        val turnedOn = s.isDriving && prevLowBeam == false && s.lowBeamOn
        prevLowBeam = s.lowBeamOn
        if (!s.lowBeamOn || !s.isDaytime() || s.wiper != WiperLevel.OFF || nearSunset(s)) {
            daytimeLowBeamSinceMs = null
            return false
        }
        if (turnedOn) daytimeLowBeamSinceMs = s.timestampMs
        val since = daytimeLowBeamSinceMs ?: return false
        val inWindow = s.timestampMs - since <= config.tunnelMaxMs
        // GPS 상태를 알면 '약해진 뒤'에만 터널로 확정 (전조등보다 몇 초 늦게 약해져도 창 안이면 인정).
        // GPS가 계속 양호하면(흐린 날 오토라이트) 터널 아님. 모르면(null) 조명 규칙만 사용
        return inWindow && s.gpsWeak != false
    }

    private fun nearSunset(s: VehicleSnapshot): Boolean {
        val set = s.sunsetMinute ?: return false
        return s.minuteOfDay in (set - 15)..(set + 60)
    }

    private fun isLateNight(minute: Int) = minute >= 23 * 60 || minute < 5 * 60

    private fun isSunset(s: VehicleSnapshot): Boolean {
        val set = s.sunsetMinute ?: return false
        return s.minuteOfDay in (set - 40)..(set + 20)
    }

    /** 장면별 기본 무드 + 가벼운 보정. */
    fun moodFor(scene: Scene, s: VehicleSnapshot): Mood {
        val base = when (scene) {
            Scene.HEAVY_RAIN -> Mood(-0.7f, -0.4f)
            Scene.TUNNEL -> Mood(0.1f, -0.5f)
            Scene.RAIN -> Mood(-0.4f, -0.2f)
            Scene.TRAFFIC_JAM -> Mood(0.0f, 0.6f)
            Scene.ARRIVAL_FINALE -> Mood(0.6f, 0.8f)
            Scene.SPORT -> Mood(0.6f, 0.3f)
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

    /**
     * 기본 장면(노을·낮·밤·크루징·에코·스포츠)으로 '돌아올 때'는 돌아온 이유를 말한다.
     * 터널은 짧아서 침묵. 단 터널을 나오자마자 폭우면 안전 안내는 한다.
     */
    private fun djLineFor(scene: Scene, prev: Scene): String? {
        if (scene == Scene.TUNNEL) return null
        if (prev == Scene.TUNNEL && scene != Scene.HEAVY_RAIN) return null
        val back = scene.priority <= Scene.SPORT.priority && scene != Scene.PARKED
        if (back) when (prev) {
            Scene.RAIN, Scene.HEAVY_RAIN -> return "비가 그쳤네요. 다시 ${scene.label} 분위기로 갈게요."
            Scene.TRAFFIC_JAM -> return "길이 뚫렸네요. 다시 ${scene.label} 분위기로 갈게요."
            else -> Unit
        }
        return djLineFor(scene)
    }

    private fun djLineFor(scene: Scene): String = when (scene) {
        Scene.HEAVY_RAIN -> "비가 많이 와요. 아주 차분한 곡으로 갈게요. 운전 조심하세요."
        Scene.TUNNEL -> "터널이에요. 잠깐 공간감 있는 곡으로 바꿀게요."
        Scene.RAIN -> "비가 오기 시작했네요. 조금 차분하게 갈게요."
        Scene.TRAFFIC_JAM -> "길이 막히네요. 기분 좋아지는 곡으로 바꿀게요."
        Scene.ARRIVAL_FINALE -> "곧 도착이에요. 오늘의 피날레 곡입니다."
        Scene.SPORT -> "스포츠 모드네요. 경쾌한 곡으로 갈게요."
        Scene.SUNSET -> "노을 시간이에요. 따뜻한 곡으로 갈게요."
        Scene.NIGHT -> "어두워졌네요. 조용한 밤 드라이브 곡으로 바꿀게요."
        Scene.CRUISING -> "도로가 시원하게 뚫렸네요. 그루브 있는 곡으로 갈게요."
        Scene.ECO -> "편안하게 가볼까요."
        Scene.CLEAR_DAY -> "날이 개었네요. 다시 밝게 갈게요."
        Scene.PARKED -> "도착했어요. 오늘 드라이브 카드를 만들어 둘게요."
    }
}
