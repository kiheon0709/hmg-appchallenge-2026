package io.moodlab.moodrive.core

import kotlinx.serialization.Serializable

/** 와이퍼 상태. Pleos Wiper API는 State/Switch 수준만 확인됨 → 어댑터에서 가능한 만큼 매핑. */
enum class WiperLevel { OFF, INTERMITTENT, LOW, HIGH }

enum class DriveMode { ECO, NORMAL, SPORT, UNKNOWN }

enum class Gear { P, R, N, D }

/**
 * 한 시점의 차량·주행 상황. Vehicle SDK / NaviHelper / FusedLocation 값을 어댑터가 채운다.
 * 모르는 값은 null 또는 기본값으로 두고, 엔진은 없는 신호를 빼고 판단한다.
 */
data class VehicleSnapshot(
    val timestampMs: Long,
    /** 현지 시각(0~1439분). */
    val minuteOfDay: Int,
    val gear: Gear = Gear.P,
    val wiper: WiperLevel = WiperLevel.OFF,
    val lowBeamOn: Boolean = false,
    val driveMode: DriveMode = DriveMode.NORMAL,
    val cruiseOn: Boolean = false,
    val cabinTempC: Float? = null,
    val targetTempC: Float? = null,
    /** 연속 주행 시간(분). Odometer Trip 또는 앱 내부 계산. */
    val drivingMinutes: Int = 0,
    /** NaviHelper 정체 구간 여부. */
    val congestion: Boolean = false,
    /** NaviHelper 도착까지 남은 시간(분). 확인 대기 중 → null 허용. */
    val etaMinutes: Int? = null,
    /** 위치 기반 일몰 시각(분). */
    val sunsetMinute: Int? = null,
    val sunriseMinute: Int? = null,
    /**
     * FusedLocation 정확도로 본 GPS 약함(예: 정확도 50m 초과). 터널 확인용 보조 신호.
     * null = 모름(조명 규칙만 사용), false = GPS 양호 → 터널 아님.
     */
    val gpsWeak: Boolean? = null,
) {
    val isDriving: Boolean get() = gear == Gear.D || gear == Gear.R

    fun isDaytime(): Boolean {
        val rise = sunriseMinute ?: 6 * 60 + 30
        val set = sunsetMinute ?: 18 * 60
        return minuteOfDay in rise until set
    }
}

/**
 * 무드 좌표 (Russell 감정 원형 모델).
 * energy: -1(차분) ~ +1(신남), valence: -1(어두움·우울) ~ +1(밝음·따뜻)
 */
@Serializable
data class Mood(val energy: Float, val valence: Float) {
    fun distanceTo(o: Mood): Float {
        val de = energy - o.energy
        val dv = valence - o.valence
        return kotlin.math.sqrt(de * de + dv * dv)
    }

    fun clamped() = Mood(energy.coerceIn(-1f, 1f), valence.coerceIn(-1f, 1f))
}

/** 감지된 장면. 위에 있을수록 우선순위가 높다(아래 Scene.priority). */
enum class Scene(val priority: Int, val label: String) {
    HEAVY_RAIN(90, "폭우"),
    TUNNEL(80, "터널"),
    RAIN(70, "비"),
    TRAFFIC_JAM(60, "정체"),
    ARRIVAL_FINALE(65, "도착 피날레"), // 정체(60)보다 위: 막혀도 도착 연출은 한다. 비·폭우·터널(안전)보다는 아래
    SPORT(50, "스포츠 주행"),
    SUNSET(40, "노을"),
    NIGHT(35, "야간"),
    CRUISING(30, "크루징"),
    ECO(25, "에코 주행"),
    CLEAR_DAY(10, "맑은 낮"),
    PARKED(0, "정차"),
}

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val durationSec: Int,
    val mood: Mood,
    val bpm: Int? = null,
    /** 앱 assets 내 경로 또는 URI. 비어 있으면 앱이 시뮬레이션 재생. */
    val source: String = "",
    /** 라이선스 표기 (예: "CC0", "Pixabay Content License"). 제출 전 전 곡 확인. */
    val license: String = "",
)

@Serializable
data class RgbColor(val r: Int, val g: Int, val b: Int) {
    fun toArgb(): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    fun hex(): String = "#%02X%02X%02X".format(r, g, b)
}

@Serializable
data class AmbientCommand(
    val color: RgbColor,
    /** 0~100 */
    val brightness: Int,
    val transitionMs: Long,
)

enum class Feedback(val weight: Float) {
    SKIPPED_EARLY(-1f),
    COMPLETED(0.5f),
    LIKE(2f),
    DISLIKE(-2f),
}
