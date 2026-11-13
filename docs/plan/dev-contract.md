# 개발 계약 (세션 4개가 한 폴더에서 일하는 규칙)

제품 내용은 `product-spec.md`. 여기에는 누가 어떤 파일을 맡고, 세션 사이에 무엇을 주고받는지만 적는다. 계약을 바꿔야 하면 `decisions.md`에 적고 영향받는 세션에 메시지를 보낸다.

## 1. 패키지와 담당

새 코드는 `io.moodlab.sideseat` 아래에 쓴다. 기존 `io.moodlab.moodrive` 코드는 참고·복사해도 되지만 고치지 않는다(통합 세션이 마지막에 지운다).

| 세션 | 담당 경로 (이 밖은 고치지 않음) |
| --- | --- |
| **dev-vehicle-core** (`local_7e9ad84e-3ebf-43c3-82a0-10f4bcc84969`) | `core/src/main/kotlin/io/moodlab/sideseat/core/**`, `core/src/test/kotlin/io/moodlab/sideseat/core/**`, `app/src/main/java/io/moodlab/sideseat/vehicle/**` |
| **dev-sim-ui** (`local_0556570c-ea8e-4145-8673-0dda645d7903`) | `app/src/main/java/io/moodlab/sideseat/ui/**`, `app/src/main/res/drawable*/**`, `app/src/main/res/font/**` |
| **dev-voice-ai** (`local_04e36da6-d9e3-44d7-92cc-a9445b84ba55`) | `app/src/main/java/io/moodlab/sideseat/voice/**`, `app/src/main/assets/sfx/**`, `app/src/main/assets/knowledge/**`, `tools/sfx/**` |
| **dev-integration** (`local_8835d9dc-1e66-4c49-b067-94a2bbdf4b4f`) | `app/build.gradle.kts`, `core/build.gradle.kts`, `settings.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/**`, `app/src/main/java/io/moodlab/sideseat/app/**`(MainActivity, AppViewModel, 서비스, DI), `README.md`, `apk/**`, `docs/STATUS.md`, `docs/screens/sim/**`, 기존 `moodrive` 코드 삭제 |
| 상위 세션 | `docs/plan/**`, `docs/GOAL-2026-11-13-overnight.md` |

- 의존성·퍼미션·빌드 설정이 필요하면 통합 세션에 메시지로 요청한다(직접 고치지 않는다).
- 다른 세션 파일의 버그를 찾으면 그 세션에 메시지로 알린다. 급하면 통합 세션이 고치고 알린다.

## 2. 함께 일하는 규칙

- 커밋: 자기 담당 경로만 `git add <경로>`로 올린다. `git add -A`·`git add .` 금지. 컴파일되는 상태만 커밋한다. `index.lock` 오류가 나면 잠깐 뒤 다시 한다. 푸시 금지.
- 빌드: `./gradlew :core:test`와 `./gradlew :app:compileDebugKotlin`은 누구나 돌려도 된다. 다른 세션 파일 때문에 컴파일이 깨지면 1~2분 뒤 다시 해 보고, 계속 깨지면 그 세션에 알린다.
- **에뮬레이터·adb·APK 설치는 통합 세션만.** 다른 세션이 실측이 필요하면 통합 세션에 "이 로그 태그로 이 값을 재 달라"고 요청한다.
- 실측이 필요한 값은 로그 태그 `SideSeatMeasure`로 남긴다.
- 막히면 `docs/GOAL-2026-11-13-overnight.md` 4절대로. 결정은 `docs/plan/decisions.md`에 한 줄(세션 이름, 무엇을 왜). 사용자만 할 수 있는 일은 `docs/plan/needs-user.md`.
- 턴을 끝낼 때 보고: 만든 것, 검증한 방법과 결과, 못 한 것, 다른 세션에 바라는 것.

## 3. 공용 타입 (core) — dev-vehicle-core가 가장 먼저 만들어 커밋한다

아래 시그니처는 약속이다. 구현 세부와 추가 필드는 dev-vehicle-core가 정하되, 여기 있는 이름과 의미는 유지한다. 순수 Kotlin(JVM), 안드로이드 의존 없음.

```kotlin
package io.moodlab.sideseat.core

enum class WiperLevel { OFF, MIST, INTERMITTENT, LOW, HIGH }   // 에뮬레이터 Low=CONTINUOUS_LEVEL_1 → LOW, High=LEVEL_3 → HIGH
enum class TurnSignal { OFF, LEFT, RIGHT, HAZARD }
enum class TelltaleColor { WHITE, GREEN, BLUE, YELLOW, RED, GRAY }
enum class Seat { DRIVER, PASSENGER, REAR_LEFT, REAR_CENTER, REAR_RIGHT }

/** 한 시점의 차량 상태. 모르는 값은 null. 판정·규칙은 null인 신호가 필요한 항목을 건너뛴다. */
data class VehicleState(
    val timestampMs: Long,
    val parked: Boolean,                    // 정차(훈련 가능) 여부
    val wiper: WiperLevel? = null,
    val lowBeamOn: Boolean? = null,
    val highBeamOn: Boolean? = null,
    val frontFogOn: Boolean? = null,
    val rearFogOn: Boolean? = null,
    val turnSignal: TurnSignal? = null,
    val brakePressed: Boolean? = null,
    val steeringAngleDeg: Float? = null,    // 오른쪽 +
    val defrostFrontOn: Boolean? = null,    // 앞유리 성에 제거 또는 Max Defrost
    val acOn: Boolean? = null,
    val recirculationOn: Boolean? = null,
    val driverWindowOpenPct: Int? = null,   // 0 = 닫힘
    val windowOpenPct: Map<Seat, Int> = emptyMap(),
    val occupied: Set<Seat> = emptySet(),
    val parkingBrakeOn: Boolean? = null,
    val activeTelltales: Map<String, TelltaleColor> = emptyMap(), // 키는 SDK TelltaleType 이름
    val speedKmhEstimate: Float? = null,    // FusedLocation 기반 추정
    val minuteOfDay: Int = 0,
    val sunsetMinute: Int? = null,
    val sunriseMinute: Int? = null,
    val vehicleModelKey: String? = null,    // "이 차 면허" 구분용
)

// ---------- 훈련 ----------
enum class SituationId { HEAVY_RAIN, FOGGED_WINDSHIELD, SUDDEN_STOP, TUNNEL, DENSE_FOG, ONCOMING_GLARE,
    BREAKDOWN, WARNING_LIGHT, DROWSY, UNINTENDED_ACCEL, BEFORE_EXIT, WIPER_DEBRIS }

data class StepView(val id: String, val label: String, val done: Boolean)
enum class TrainingPhase { IDLE, CRUISE, BRIEFING, ACTIVE, HINT, SUCCESS, FAIL, CHAPTER_DONE }

/** UI가 그대로 그리는 훈련 상태. */
data class TrainingView(
    val phase: TrainingPhase,
    val chapterId: String?,
    val situation: SituationId?,
    val title: String,                 // "폭우"
    val steps: List<StepView>,
    val elapsedMs: Long,
    val limitMs: Long,
    val virtualSpeedKmh: Float,
    val scene: SceneParams,
    val lastResult: SituationResult?,
)

/** 주행 화면이 그릴 장면 값. 0f~1f는 강도. UI는 이것만 보고 그린다. */
data class SceneParams(
    val night: Boolean = true,
    val rain: Float = 0f,
    val fogOutside: Float = 0f,
    val windshieldFog: Float = 0f,     // 김서림 남은 양 (엔진이 성에 제거에 따라 줄인다)
    val tunnel: Boolean = false,
    val leadCarDistance: Float? = null,// 0f(충돌)~1f(멀리), null이면 앞차 없음
    val leadCarBraking: Boolean = false,
    val oncomingGlare: Float = 0f,
    val debris: Float = 0f,
    val alert: Float = 0f,             // 위기 강도 → 화면 가장자리 빛 띠와 앰비언트 색
)

data class SituationResult(val id: SituationId, val stars: Int, val reactionMs: Long?,
    val missedStepIds: List<String>, val penalties: List<String>)
data class ChapterResult(val chapterId: String, val results: List<SituationResult>, val totalStars: Int)

/** 훈련 엔진이 밖으로 내는 일회성 신호. 음성·효과음·조명이 이것을 듣는다. */
sealed interface CoachEvent {
    data class Say(val text: String, val priority: Int = 0, val expectReply: ReplyKind? = null) : CoachEvent
    data class Sfx(val name: String, val loop: Boolean = false) : CoachEvent   // "rain", "thunder", "tire", "alarm", "click", "success", "fail"
    data class StopSfx(val name: String) : CoachEvent
    data class Ambient(val mood: AmbientMood) : CoachEvent
}
enum class AmbientMood { CALM, ALERT, SUCCESS, OFF }
enum class ReplyKind { YES_NO, CHOICE, FREE }

interface TrainingEngine {
    fun chapters(): List<ChapterInfo>
    fun start(chapterId: String, nowMs: Long)
    fun startSurprise(nowMs: Long)                                   // 돌발 모드
    fun inject(situation: SituationId, nowMs: Long)                  // 교관 대결
    /** 프레임마다 또는 신호가 바뀔 때 호출. 뷰와 그 사이 생긴 이벤트를 돌려준다. */
    fun tick(state: VehicleState, nowMs: Long): Pair<TrainingView, List<CoachEvent>>
    fun answer(choiceOrText: String, nowMs: Long)                    // S07·S08의 말·선택 답
    fun abort(nowMs: Long)                                           // P를 벗어남
    fun progress(): TrainingProgress                                 // 별, 최고 기록, 이 차 면허
}
data class ChapterInfo(val id: String, val title: String, val story: String, val situations: List<SituationId>,
    val estMinutes: Int, val locked: Boolean)

// ---------- 주행 코치 ----------
data class CoachUtterance(val ruleId: String, val text: String, val urgent: Boolean, val expectReply: ReplyKind? = null)
interface DriveCoach {
    fun onDriveStart(nowMs: Long)
    fun tick(state: VehicleState, nowMs: Long): CoachUtterance?      // 말할 것이 있으면 하나
    fun onReply(ruleId: String, reply: String, nowMs: Long): CoachAction?
    fun onDriveEnd(nowMs: Long): DriveSummary
}
sealed interface CoachAction { data class AddRestStopWaypoint(val reason: String) : CoachAction }
data class DriveSummary(val durationMin: Int, val rainMin: Int, val nightMin: Int,
    val utterances: List<CoachUtterance>, val observations: Map<String, Int>,   // "NO_SIGNAL_TURN" to 1 ...
    val suggestedSituation: SituationId?, val ruleBasedReview: List<String>)    // LLM 없이도 읽을 3문장

// ---------- 미니게임 ----------
interface SafeDialGame {   // M1
    fun start(nowMs: Long)
    fun tick(steeringAngleDeg: Float?, nowMs: Long): Pair<SafeDialView, List<CoachEvent>>
}
data class SafeDialView(val targetsDeg: List<Int>, val solved: Int, val currentDeg: Float, val closeness: Float, val done: Boolean)
```

진행 기록 저장(`TrainingProgress`)은 core가 직렬화 가능한 형태로 주고, 파일 저장은 통합 세션이 한다.

## 4. 앱 쪽 인터페이스

```kotlin
// vehicle (dev-vehicle-core)
package io.moodlab.sideseat.vehicle
interface VehicleGateway {
    val states: kotlinx.coroutines.flow.StateFlow<io.moodlab.sideseat.core.VehicleState>
    fun start(); fun stop()
    suspend fun setAmbient(mood: io.moodlab.sideseat.core.AmbientMood): Boolean   // 실패해도 예외 없이 false
    suspend fun addRestStopWaypoint(): Boolean                                    // NaviHelper
    val navRemainingMinutes: kotlinx.coroutines.flow.StateFlow<Int?>
    val arrivals: kotlinx.coroutines.flow.SharedFlow<Unit>                        // 목적지 도착
}
class PleosVehicleGateway(context: android.content.Context) : VehicleGateway      // 실차·에뮬레이터
class DebugVehicleGateway : VehicleGateway                                        // 화면 디버그 패널에서 값을 바꿈 (Virtual Console 없이 UI 확인용)

// voice (dev-voice-ai)
package io.moodlab.sideseat.voice
interface Voice {
    fun say(text: String, urgent: Boolean = false)            // 큐에 넣고 순서대로. urgent면 앞의 말을 끊음
    fun sayStreaming(sentences: kotlinx.coroutines.flow.Flow<String>)
    fun stopSpeaking()
    val speaking: kotlinx.coroutines.flow.StateFlow<Boolean>
    /** 마이크를 열고 한 번 듣는다. 중간 결과는 partial로, 끝나면 최종 텍스트(없으면 null). */
    suspend fun listen(kind: io.moodlab.sideseat.core.ReplyKind, onPartial: (String) -> Unit = {}): String?
    val listening: kotlinx.coroutines.flow.StateFlow<Boolean>
}
interface Sfx { fun play(name: String, loop: Boolean = false); fun stop(name: String); fun stopAll() }
interface Instructor {    // AI 교관
    /** 리뷰 총평을 문장 단위로 흘려보낸다. LLM이 안 되면 summary.ruleBasedReview를 그대로. */
    fun review(summary: io.moodlab.sideseat.core.DriveSummary): kotlinx.coroutines.flow.Flow<String>
    /** 질문에 답한다. parked=false면 1~2문장. 빠른 길로 끝나면 LLM을 부르지 않는다. */
    fun answer(question: String, parked: Boolean, context: String): kotlinx.coroutines.flow.Flow<String>
    val lastLatencyMs: kotlinx.coroutines.flow.StateFlow<Long?>     // 말 끝 → 첫 음성
}
// 생성: VoiceModule.create(context, config): Triple<Voice, Sfx, Instructor> 형태의 진입점 하나를 둔다.
// config(API 키, 모델 이름)는 통합 세션이 BuildConfig에서 꺼내 넘긴다.

// ui (dev-sim-ui) — 상태를 받아 그리기만 한다. ViewModel·SDK를 직접 만지지 않는다.
package io.moodlab.sideseat.ui
@Composable fun HomeScreen(state: HomeUi, onAction: (UiAction) -> Unit)
@Composable fun TrainingScreen(view: TrainingView, vehicle: VehicleState, onAction: (UiAction) -> Unit)
@Composable fun ResultScreen(result: ChapterResult, progress: TrainingProgress, onAction: (UiAction) -> Unit)
@Composable fun CoachScreen(state: CoachUi, onAction: (UiAction) -> Unit)        // 주행 중: 최소 화면
@Composable fun ReviewScreen(state: ReviewUi, onAction: (UiAction) -> Unit)
@Composable fun SafeDialScreen(view: SafeDialView, onAction: (UiAction) -> Unit)
@Composable fun DebugPanel(...)                                                   // DebugVehicleGateway 조작용, 디버그 빌드에서만
// HomeUi, CoachUi, ReviewUi, UiAction은 ui 패키지의 `UiModels.kt`에 dev-sim-ui가 정의하고 통합 세션이 채운다.
```

통합 세션(`app` 패키지)이 하는 일: `AppViewModel`이 `VehicleGateway.states`를 받아 모드(홈·훈련·코치·리뷰·미니게임)를 정하고, `TrainingEngine`·`DriveCoach`를 돌리고, `CoachEvent`를 `Voice`·`Sfx`·`setAmbient`로 보내고, UI 상태를 만든다. 주행 중 코치는 포그라운드 서비스에서 돈다.

## 5. 순서

1. dev-vehicle-core: 3절 타입을 먼저 커밋(구현은 빈 껍데기여도 됨) → 다른 세션에 "타입 커밋됨" 메시지.
2. 그동안 dev-sim-ui는 `DebugVehicleGateway` 없이도 볼 수 있게 미리보기용 가짜 `TrainingView`로 주행 화면부터 만든다. dev-voice-ai는 voice 패키지를 독립적으로 만든다. dev-integration은 빌드 설정·매니페스트·앱 뼈대를 만든다.
3. 타입이 올라오면 각자 연결. dev-integration이 묶어서 에뮬레이터에 올리고 실측한다.
4. 실측 결과에 따라 각 세션이 고친다. 끝까지 반복.

## 6. 위치 연계 코치 추가 (11-13, dev-vehicle-core, 명세 6.4)

담당 경로 추가: dev-vehicle-core가 `app/src/main/assets/zones/**`, `tools/zones/**`를 맡는다.

core 타입 추가(기존 생성자와 호환, 모두 기본값 있음):

```kotlin
enum class SpeedLimitSource { NAVI, MAP }
data class VehicleState(
    ...,
    val latitude: Double? = null, val longitude: Double? = null, val locationTimeMs: Long? = null,
    val speedLimitKmh: Int? = null,              // 내비 값 → 없으면 OSM maxspeed. null이면 과속 판정 안 함
    val speedLimitSource: SpeedLimitSource? = null,
)
data class DriveSummary(
    ...,
    val speedingCount: Int = 0,                  // 과속 구간 수(제한 +10km/h를 5초 넘게). 말하지 않은 것도 셈
    val longestSpeedingSec: Int = 0,
    val schoolZonePasses: Int = 0,
    val schoolZoneMaxKmh: Int? = null,           // 보호구역 안 최고 속도
)
class DefaultDriveCoach(config: CoachConfig = CoachConfig(), zones: SchoolZoneIndex = SchoolZoneIndex.EMPTY, practiced: () -> Set<SituationId> = { emptySet() })
```

- 생성자 순서가 바뀌었다: 두 번째 인자가 `zones`. 앱은 `DefaultDriveCoach(config, SchoolZoneAssets.load(context)) { practiced }`.
- K12를 끄려면 `CoachConfig(disabledRules = setOf("K07", "K10", "K12"))`. 끄더라도 요약의 과속 수는 센다.
- 새 CoachUtterance ruleId: `K11`(urgent=true), `K12`(urgent=false). 둘 다 30분당 말수 제한 밖.
- `speedKmhEstimate`는 이제 좌표 3초 중앙값(게이트웨이와 코치가 같은 `LocationTrack`을 쓴다).
- DebugVehicleGateway: `setLocation(lat, lon)`, `setSpeedLimit(kmh)` 추가.
