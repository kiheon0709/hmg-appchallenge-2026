package io.moodlab.sideseat.core

import kotlinx.serialization.Serializable

// 공용 타입. 이름과 의미는 docs/plan/dev-contract.md 3절의 약속이다. 순수 Kotlin(JVM), 안드로이드 의존 없음.

/** 에뮬레이터 Low=CONTINUOUS_LEVEL_1 → LOW, High=CONTINUOUS_LEVEL_3 → HIGH. */
enum class WiperLevel { OFF, MIST, INTERMITTENT, LOW, HIGH }

enum class TurnSignal { OFF, LEFT, RIGHT, HAZARD }

enum class SpeedLimitSource { NAVI, MAP }

enum class TelltaleColor { WHITE, GREEN, BLUE, YELLOW, RED, GRAY }

enum class Seat { DRIVER, PASSENGER, REAR_LEFT, REAR_CENTER, REAR_RIGHT }

/** 한 시점의 차량 상태. 모르는 값은 null. 판정·규칙은 null인 신호가 필요한 항목을 건너뛴다. */
data class VehicleState(
    val timestampMs: Long,
    /** 정차(훈련 가능) 여부. */
    val parked: Boolean,
    val wiper: WiperLevel? = null,
    val lowBeamOn: Boolean? = null,
    val highBeamOn: Boolean? = null,
    val frontFogOn: Boolean? = null,
    val rearFogOn: Boolean? = null,
    val turnSignal: TurnSignal? = null,
    /** 비상등. Pleos TurnSignal API에는 값이 없고 경고등은 권한이 막혀 있어 모르면 null(실측 11-13). */
    val hazardOn: Boolean? = null,
    val brakePressed: Boolean? = null,
    /** 오른쪽 +. */
    val steeringAngleDeg: Float? = null,
    /** 앞유리 성에 제거 또는 Max Defrost. */
    val defrostFrontOn: Boolean? = null,
    val acOn: Boolean? = null,
    val recirculationOn: Boolean? = null,
    /** 0 = 닫힘. */
    val driverWindowOpenPct: Int? = null,
    /** 좌석별 창문 열림(%). 게이트웨이는 운전석도 함께 넣는다(M3 버저). */
    val windowOpenPct: Map<Seat, Int> = emptyMap(),
    val occupied: Set<Seat> = emptySet(),
    val parkingBrakeOn: Boolean? = null,
    /** 키는 SDK TelltaleType 이름(예: "TIRE_PRESSURE", "PARKING_BRAKE"). */
    val activeTelltales: Map<String, TelltaleColor> = emptyMap(),
    /** FusedLocation 좌표로 계산한 속도(최근 3초 중앙값). */
    val speedKmhEstimate: Float? = null,
    /** FusedLocation 좌표(명세 6.4). 모르면 null. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** 좌표를 받은 시각. 같은 좌표를 두 번 세지 않도록 쓴다. */
    val locationTimeMs: Long? = null,
    /** 지금 도로의 제한속도. 내비 값이 있으면 그것, 없으면 지도(OSM) 값. 둘 다 없으면 null → 과속 판정 안 함. */
    val speedLimitKmh: Int? = null,
    val speedLimitSource: SpeedLimitSource? = null,
    /** 현지 시각(0~1439분). */
    val minuteOfDay: Int = 0,
    val sunsetMinute: Int? = null,
    val sunriseMinute: Int? = null,
    /** "이 차 면허" 구분용. */
    val vehicleModelKey: String? = null,
) {
    /** 앞 또는 뒤 안개등. 둘 다 모르면 null. */
    val anyFogOn: Boolean?
        get() = if (frontFogOn == null && rearFogOn == null) null else frontFogOn == true || rearFogOn == true
}

// ---------- 훈련 ----------

enum class SituationId {
    HEAVY_RAIN, FOGGED_WINDSHIELD, SUDDEN_STOP, TUNNEL, DENSE_FOG, ONCOMING_GLARE,
    BREAKDOWN, WARNING_LIGHT, DROWSY, UNINTENDED_ACCEL, BEFORE_EXIT, WIPER_DEBRIS,
    // 길 위의 규칙(11-14 추가). 기록은 이름으로 저장하므로 뒤에 붙여도 이전 기록과 호환된다.
    RIGHT_TURN_CROSSWALK, HIGHWAY_MERGE, HYDROPLANING, HIPASS_WRONG_LANE,
    // 장소가 다른 상황(11-15 추가): 회전교차로 진입, 어린이 보호구역의 신호등 없는 횡단보도.
    ROUNDABOUT_ENTRY, SCHOOL_ZONE,
}

data class StepView(val id: String, val label: String, val done: Boolean)

enum class TrainingPhase { IDLE, CRUISE, BRIEFING, ACTIVE, HINT, SUCCESS, FAIL, CHAPTER_DONE }

/** 화면에 띄울 선택지(S07·S08). 말로 답해도 되고 눌러도 된다. */
data class ChoiceView(val question: String, val options: List<String>)

/** UI가 그대로 그리는 훈련 상태. */
data class TrainingView(
    val phase: TrainingPhase,
    val chapterId: String?,
    val situation: SituationId?,
    /** 예: "폭우". */
    val title: String,
    val steps: List<StepView>,
    val elapsedMs: Long,
    val limitMs: Long,
    val virtualSpeedKmh: Float,
    val scene: SceneParams,
    val lastResult: SituationResult?,
    /** 지금 답해야 할 선택지. 없으면 null. */
    val choice: ChoiceView? = null,
    /** 챕터가 끝났을 때의 결과. */
    val chapterResult: ChapterResult? = null,
    /** 돌발 모드에서 버틴 개수. */
    val surviveCount: Int = 0,
)

/** 주행 화면이 그릴 장면 값. 0f~1f는 강도. UI는 이것만 보고 그린다. */
data class SceneParams(
    val night: Boolean = true,
    val rain: Float = 0f,
    val fogOutside: Float = 0f,
    /** 김서림 남은 양 (엔진이 성에 제거에 따라 줄인다). */
    val windshieldFog: Float = 0f,
    val tunnel: Boolean = false,
    /** 0f(충돌)~1f(멀리), null이면 앞차 없음. */
    val leadCarDistance: Float? = null,
    val leadCarBraking: Boolean = false,
    val oncomingGlare: Float = 0f,
    val debris: Float = 0f,
    /** 위기 강도 → 화면 가장자리 빛 띠와 앰비언트 색. */
    val alert: Float = 0f,
    /** 화면에 띄울 경고등 키(S08). 없으면 null. */
    val warningLight: String? = null,
    /** 주차장 장면(S11). */
    val parkingLot: Boolean = false,
    /** 졸음 흐림(S09). */
    val drowsyBlur: Float = 0f,
    /** 화면에 띄울 도로 표지 키. "CROSSWALK"(횡단보도), "HIPASS"(하이패스 차로), "ROUNDABOUT"(회전교차로), "SCHOOL"(보호구역 횡단보도). 없으면 null. */
    val sign: String? = null,
    /** 차가 미끄러지는 정도(수막현상). 화면이 좌우로 흔들린다. */
    val slip: Float = 0f,
    /** 장소([ScenePlace]의 이름). 문제 상황과 따로 정해져 "장소 × 상황"으로 조합된다. 3D 장면만 쓴다. */
    val place: String = ScenePlace.HIGHWAY,
)

data class SituationResult(
    val id: SituationId,
    val stars: Int,
    val reactionMs: Long?,
    val missedStepIds: List<String>,
    val penalties: List<String>,
)

data class ChapterResult(val chapterId: String, val results: List<SituationResult>, val totalStars: Int)

/** 훈련 엔진이 밖으로 내는 일회성 신호. 음성·효과음·조명이 이것을 듣는다. */
sealed interface CoachEvent {
    data class Say(val text: String, val priority: Int = 0, val expectReply: ReplyKind? = null) : CoachEvent

    /** "rain", "thunder", "tire", "alarm", "click", "success", "fail" 등. */
    data class Sfx(val name: String, val loop: Boolean = false) : CoachEvent
    data class StopSfx(val name: String) : CoachEvent
    data class Ambient(val mood: AmbientMood) : CoachEvent
}

enum class AmbientMood { CALM, ALERT, SUCCESS, OFF }

enum class ReplyKind { YES_NO, CHOICE, FREE }

interface TrainingEngine {
    fun chapters(): List<ChapterInfo>
    /**
     * 챕터를 시작한다. [from]이 그 챕터에서 할 수 있는 상황이면 그 상황부터 하고 나머지를 원래 순서로 이어 간다
     * (가는 길 추천). 이때는 끊겼던 훈련을 잇지 않고 새로 시작한다.
     */
    fun start(chapterId: String, nowMs: Long, from: SituationId? = null)

    /** 돌발 모드. */
    fun startSurprise(nowMs: Long)

    /** 교관 대결. */
    fun inject(situation: SituationId, nowMs: Long)

    /** 프레임마다 또는 신호가 바뀔 때 호출. 뷰와 그 사이 생긴 이벤트를 돌려준다. */
    fun tick(state: VehicleState, nowMs: Long): Pair<TrainingView, List<CoachEvent>>

    /** S07·S08의 말·선택 답. */
    fun answer(choiceOrText: String, nowMs: Long)

    /** P를 벗어남. */
    fun abort(nowMs: Long)

    /** 별, 최고 기록, 이 차 면허. */
    fun progress(): TrainingProgress

    /** 저장해 둔 기록을 불러온다. 앱 시작 때 한 번. */
    fun restore(progress: TrainingProgress)

    /** P를 벗어나 중단된 챕터. 같은 id로 [start]하면 남은 상황부터 이어 한다. 없으면 null. */
    fun resumableChapter(): String?
}

data class ChapterInfo(
    val id: String,
    val title: String,
    val story: String,
    val situations: List<SituationId>,
    val estMinutes: Int,
    val locked: Boolean,
)

/** 차종별 "이 차 면허" 진행. 직렬화해 통합 세션이 파일로 저장한다. */
@Serializable
data class VehicleLicense(
    /** ★2 이상으로 깬 챕터 id. */
    val clearedChapters: Set<String> = emptySet(),
    val licensed: Boolean = false,
)

@Serializable
data class TrainingProgress(
    /** SituationId 이름 → 최고 별. */
    val bestStars: Map<String, Int> = emptyMap(),
    /** SituationId 이름 → 최고(가장 짧은) 반응 시간. */
    val bestReactionMs: Map<String, Long> = emptyMap(),
    /** 챕터 id → 최고 합계 별. */
    val chapterBestStars: Map<String, Int> = emptyMap(),
    /** 차종 키 → 면허 진행. 차종을 모르면 "unknown". */
    val licenses: Map<String, VehicleLicense> = emptyMap(),
    /** 돌발 모드 최고 기록(버틴 개수). */
    val surpriseBest: Int = 0,
    /** 누적 별(모든 시도 합). */
    val totalStarsEarned: Int = 0,
) {
    fun licenseFor(vehicleModelKey: String?): VehicleLicense = licenses[vehicleModelKey ?: UNKNOWN_VEHICLE] ?: VehicleLicense()

    companion object {
        const val UNKNOWN_VEHICLE = "unknown"
    }
}

// ---------- 주행 코치 ----------

@Serializable
data class CoachUtterance(val ruleId: String, val text: String, val urgent: Boolean, val expectReply: ReplyKind? = null)

interface DriveCoach {
    fun onDriveStart(nowMs: Long)

    /** 말할 것이 있으면 하나. */
    fun tick(state: VehicleState, nowMs: Long): CoachUtterance?
    fun onReply(ruleId: String, reply: String, nowMs: Long): CoachAction?
    fun onDriveEnd(nowMs: Long): DriveSummary
}

sealed interface CoachAction {
    data class AddRestStopWaypoint(val reason: String) : CoachAction
}

/** 직렬화 가능: 앱이 지난 주행 요약을 저장한다. */
@Serializable
data class DriveSummary(
    val durationMin: Int,
    val rainMin: Int,
    val nightMin: Int,
    val utterances: List<CoachUtterance>,
    /** "NO_SIGNAL_TURN" to 1 ... */
    val observations: Map<String, Int>,
    val suggestedSituation: SituationId?,
    /** LLM 없이도 읽을 3문장. */
    val ruleBasedReview: List<String>,
    /** 과속 구간 수(제한 +10km/h를 5초 넘게). 말하지 않은 것도 센다(명세 6.4). */
    val speedingCount: Int = 0,
    /** 가장 길었던 과속 구간(초). */
    val longestSpeedingSec: Int = 0,
    /** 어린이 보호구역 통과 수. */
    val schoolZonePasses: Int = 0,
    /** 보호구역 안에서의 최고 속도(km/h). 통과가 없거나 속도를 모르면 null. */
    val schoolZoneMaxKmh: Int? = null,
)

// ---------- 미니게임 ----------

/** M1 금고 다이얼. */
interface SafeDialGame {
    fun start(nowMs: Long)
    fun tick(steeringAngleDeg: Float?, nowMs: Long): Pair<SafeDialView, List<CoachEvent>>
}

data class SafeDialView(
    val targetsDeg: List<Int>,
    val solved: Int,
    val currentDeg: Float,
    val closeness: Float,
    val done: Boolean,
    val elapsedMs: Long = 0,
    /** 맞은 자리에서 멈춰 있는 정도 0~1. 1이 되면 철컥. */
    val holdProgress: Float = 0f,
)

/** M2 깜빡이 모스. */
interface TurnSignalGame {
    fun start(nowMs: Long)
    fun tick(turnSignal: TurnSignal?, nowMs: Long): Pair<TurnSignalGameView, List<CoachEvent>>
}

enum class MiniGamePhase { SHOW, INPUT, ROUND_CLEAR, OVER, DONE }

data class TurnSignalGameView(
    val round: Int,
    /** 따라 할 순서. LEFT/RIGHT만. */
    val pattern: List<TurnSignal>,
    /** SHOW 단계에서 지금 보여 줄 칸(없으면 -1). */
    val showingIndex: Int,
    /** 지금까지 맞게 입력한 개수. */
    val inputCount: Int,
    val phase: MiniGamePhase,
    /** 깬 라운드 수. */
    val score: Int,
    val inputLimitMs: Long,
    val inputElapsedMs: Long,
)

/** M3 창문 버저 퀴즈. */
interface WindowBuzzerGame {
    fun start(nowMs: Long)
    fun tick(windowOpenPct: Map<Seat, Int>, nowMs: Long): Pair<WindowBuzzerView, List<CoachEvent>>
    /** 버저를 누른 사람의 답(말 또는 화면 선택). */
    fun answer(choiceOrText: String, nowMs: Long)
}

enum class BuzzerPhase { ASK, BUZZED, REVEAL, DONE }

data class WindowBuzzerView(
    val questionIndex: Int,
    val questionCount: Int,
    val question: String,
    val options: List<String>,
    val phase: BuzzerPhase,
    val buzzedSeat: Seat?,
    /** REVEAL에서 맞았는지. */
    val lastCorrect: Boolean?,
    val scores: Map<Seat, Int>,
)

/** 훈련 장면의 장소. 같은 문제 상황(비, 안개, 앞차 급정거 등)도 장소를 바꿔 낼 수 있다. */
object ScenePlace {
    const val HIGHWAY = "HIGHWAY"
    const val CITY = "CITY"
    const val SCHOOL = "SCHOOL"
    const val DOWNHILL = "DOWNHILL"
}
