package io.moodlab.moodrive.core

/**
 * 시나리오 플레이어. 에뮬레이터·시연 영상에서 비 → 터널 → 정체 → 도착을 순서대로 재생한다.
 * 각 Step은 시작 시각(초)과 그때 바뀌는 신호를 갖고, 다음 Step 전까지 유지된다.
 */
data class Scenario(
    val name: String,
    val start: VehicleSnapshot,
    val steps: List<Step>,
    val durationSec: Int,
    /** 시나리오 1초가 차량 시간으로 몇 초인지. 시각·주행시간·ETA·timestamp 모두 이 배율로 진행(가상 차량 시간). */
    val timeScale: Int = 1,
) {
    data class Step(val atSec: Int, val caption: String, val change: (VehicleSnapshot) -> VehicleSnapshot)

    /** 시나리오 시작 후 [tSec]초 시점의 스냅샷. drivingMinutes·etaMinutes는 경과 시간만큼 자동 진행. */
    fun snapshotAt(tSec: Int): VehicleSnapshot {
        var s = start
        for (step in steps) {
            if (step.atSec <= tSec) s = step.change(s) else break
        }
        val elapsedMin = tSec * timeScale / 60
        return s.copy(
            timestampMs = start.timestampMs + tSec * timeScale * 1000L,
            minuteOfDay = (start.minuteOfDay + elapsedMin) % 1440,
            drivingMinutes = if (s.isDriving) s.drivingMinutes + elapsedMin else s.drivingMinutes,
            etaMinutes = s.etaMinutes?.let { (it - elapsedMin).coerceAtLeast(0) },
        )
    }

    fun captionAt(tSec: Int): String = steps.lastOrNull { it.atSec <= tSec }?.caption ?: "출발"

    companion object {
        /**
         * 시연용 퇴근길 시나리오 (약 3분). timeScale=10이라 실제 약 30분 분량을 압축.
         * 맑은 저녁(노을) → 비 → 폭우 → 터널 → 정체 → 비 그침 → 도착 피날레 → 주차
         */
        fun rainyCommute(startMs: Long = 0L): Scenario = Scenario(
            name = "비 오는 퇴근길",
            start = VehicleSnapshot(
                timestampMs = startMs,
                minuteOfDay = 17 * 60 + 10,
                gear = Gear.D,
                cabinTempC = 23f,
                targetTempC = 22f,
                etaMinutes = 30,
                sunsetMinute = 17 * 60 + 40,
                sunriseMinute = 7 * 60,
            ),
            steps = listOf(
                Step(0, "맑은 저녁 출발") { it },
                Step(20, "와이퍼 INT — 비 시작") { it.copy(wiper = WiperLevel.INTERMITTENT) },
                Step(45, "와이퍼 HIGH — 폭우") { it.copy(wiper = WiperLevel.HIGH) },
                Step(65, "비 약해짐, 터널 진입(하향등 ON)") { it.copy(wiper = WiperLevel.OFF, lowBeamOn = true) },
                Step(80, "터널 통과(하향등 OFF)") { it.copy(lowBeamOn = false) },
                Step(95, "정체 구간") { it.copy(congestion = true) },
                Step(125, "정체 해소, 크루즈 ON") { it.copy(congestion = false, cruiseOn = true) },
                Step(150, "도착 5분 전(ETA 자동 감소)") { it.copy(cruiseOn = false) },
                Step(175, "주차(P단)") { it.copy(gear = Gear.P) },
            ),
            durationSec = 185,
            timeScale = 10,
        )
    }
}
