package io.moodlab.sideseat.core

import kotlin.random.Random

/** 훈련 수치. 명세 5.2~5.4의 값을 기본으로 두고 테스트로 고정한다. */
data class TrainingConfig(
    /** 챕터 시작 뒤 첫 상황까지 평온 주행. 선배가 챕터 이야기를 하는 시간. */
    val firstCruiseMs: Long = 5_000,
    /** 상황 사이 평온 주행(명세: 5~10초). */
    val cruiseMs: Long = 7_000,
    /** 상황 예고(장면이 바뀌기 시작) → 상황이 터질 때까지. */
    val briefingMs: Long = 1_500,
    /** 시작 전에 스위치를 되돌려 달라고 기다리는 최대 시간. 넘으면 이미 된 단계는 된 것으로 친다. */
    val setupTimeoutMs: Long = 8_000,
    val successHoldMs: Long = 3_000,
    val failHoldMs: Long = 4_500,
    /** 제한 시간의 이 비율이 지나면 힌트. */
    val hintAtFraction: Float = 0.5f,
    /** ★3: 제한 시간의 이 비율 안. */
    val threeStarFraction: Float = 0.5f,
    /** 제한 시간을 넘긴 뒤 이 비율만큼 더 기다린다. 그 안에 끝내면 ★1, 넘으면 ★0. */
    val overtimeFraction: Float = 1.0f,
    /** 제한 시간 없는 상황(S11)의 힌트 시점. */
    val untimedHintMs: Long = 15_000,
    val limitsMs: Map<SituationId, Long> = DEFAULT_LIMITS,
    /** S03 반응 시간 기준: ★3, ★2, ★1. 마지막 값을 넘기면 충돌(★0). */
    val suddenStopStarsMs: List<Long> = listOf(700, 1_000, 1_500),
    val steerJerkDeg: Float = 45f,
    val steerJerkWindowMs: Long = 500,
    /** S10 브레이크 유지 시간(명세 5.2 실측 반영: 3초 이상). */
    val brakeHoldMs: Long = 3_000,
    /** S01·S05 감속 기준: 가상 속도를 평소의 이 비율 이하로(시행규칙 제19조 50% 감속). */
    val fogSlowFraction: Float = 0.5f,
    /** S02 감속 기준. 김서림은 제19조의 대상이 아니라 "줄였다"만 확인한다(수치 근거 없음). */
    val mistSlowFraction: Float = 0.8f,
    /**
     * S02 앞유리 김이 걷히는 속도(초당 줄어드는 양, 김은 0~1). 성에 제거만으로는 느리고(다 걷히는 데 약 27초),
     * 에어컨을 더하면 약 15초, 외기까지 바꾸면 약 10초다. 화면 연출이고 판정(조작 완료)과는 무관하다.
     */
    val fogClearDefrostPerSec: Float = 0.035f,
    val fogClearAcPerSec: Float = 0.03f,
    val fogClearFreshAirPerSec: Float = 0.03f,
    /**
     * 앞이 안 보이는 상황(폭우·김서림·짙은 안개)에서 감속 다음에 "비상등 켰나요?"를 묻는다.
     * 비상등 신호는 읽을 수 없어 말·화면 선택으로 확인한다.
     * 2026-11-14 조사에서 폭우·김서림은 공식 안내를 찾지 못했고 안개는 오래된 자료뿐이라 꺼 두었다(safety-sources.md 16번).
     * 켜면 이 세 상황에 고르기 단계가 생겨 돌발 모드에는 나오지 않는다.
     */
    val lowVisibilityHazardStep: Boolean = false,
    /** 합류: 깜빡이를 이만큼 켜 둔 뒤에 조향을 인정한다(게임 규칙. 법의 기준은 고속도로 100m 이전). */
    val mergeSignalHoldMs: Long = 3_000,
    /** 합류: 이 각도 이상 왼쪽으로 돌리면 통과. 세워 둔 차라 작게 잡는다. */
    val mergeSteerDeg: Float = 10f,
    /** 수막현상: 브레이크 없이 핸들을 hydroSteerDeg 안으로 유지한 채 버틸 시간. */
    val hydroHoldMs: Long = 4_000,
    val hydroSteerDeg: Float = 10f,
    /** 우회전 접근·하이패스 통과 때의 화면 속 속도. */
    val slowApproachKmh: Float = 30f,
    /** S12 급브레이크: 상황 뒤 이 시간 안에 밟아 이만큼 유지. */
    val hardBrakeWindowMs: Long = 1_000,
    val hardBrakeHoldMs: Long = 1_000,
    /** S01 와이퍼 기준. 에뮬레이터 High = CONTINUOUS_LEVEL_3 → HIGH. */
    val heavyRainMinWiper: WiperLevel = WiperLevel.HIGH,
    /** 공식 출처가 없거나 실측에서 신호가 안 읽힌 상황. */
    val disabled: Set<SituationId> = setOf(SituationId.WIPER_DEBRIS),
    /** ★0이면 위치를 알려 주고 한 번 더(챕터 모드만). */
    val retryOnFail: Boolean = true,
    /** 다시 해서 성공한 경우의 최대 별. */
    val retryMaxStars: Int = 1,
    val cruiseSpeedKmh: Float = 80f,
    /** 돌발 모드에서 뽑지 않는 상황(시간 제한이 없는 것 등). */
    val surpriseExclude: Set<SituationId> = setOf(SituationId.BEFORE_EXIT),
    /** 면허에 필요한 챕터. */
    val licenseChapters: List<String> = ChapterCatalog.LICENSE_CHAPTERS,
    /** 면허 챕터 통과 기준(모든 상황 이 별 이상). */
    val licenseMinStars: Int = 2,
    val random: Random = Random.Default,
) {
    fun limitOf(id: SituationId): Long = limitsMs[id] ?: 10_000

    companion object {
        val DEFAULT_LIMITS: Map<SituationId, Long> = mapOf(
            // 앞이 안 보이는 상황은 감속(+비상등 확인)이 먼저라 시간을 넉넉히 준다(11-14).
            SituationId.HEAVY_RAIN to 14_000,
            SituationId.FOGGED_WINDSHIELD to 16_000,
            SituationId.SUDDEN_STOP to 1_500,
            SituationId.TUNNEL to 6_000,
            SituationId.DENSE_FOG to 12_000,
            SituationId.ONCOMING_GLARE to 5_000,
            // 말로 답하는 상황은 시간 대신 틀린 횟수로 별을 준다(decisions.md).
            SituationId.BREAKDOWN to 0,
            SituationId.WARNING_LIGHT to 0,
            SituationId.DROWSY to 10_000,
            SituationId.UNINTENDED_ACCEL to 8_000,
            SituationId.BEFORE_EXIT to 0, // 시간 제한 없음
            SituationId.WIPER_DEBRIS to 8_000,
            SituationId.RIGHT_TURN_CROSSWALK to 5_000,
            SituationId.HIGHWAY_MERGE to 10_000,
            SituationId.HYDROPLANING to 8_000,
            SituationId.HIPASS_WRONG_LANE to 0, // 말로 답하는 상황
        )
    }
}

/** 챕터 정의(명세 5.3). */
data class ChapterDef(val id: String, val title: String, val story: String, val situations: List<SituationId>, val estMinutes: Int)

object ChapterCatalog {
    val chapters: List<ChapterDef> = listOf(
        ChapterDef(
            "C1", "첫 빗길", "밤에 비가 오기 시작했어요.",
            listOf(SituationId.HEAVY_RAIN, SituationId.FOGGED_WINDSHIELD, SituationId.SUDDEN_STOP), 3,
        ),
        ChapterDef(
            "C2", "밤 고속도로", "밤 고속도로를 달리는 중이에요.",
            listOf(SituationId.TUNNEL, SituationId.ONCOMING_GLARE, SituationId.DENSE_FOG, SituationId.DROWSY), 4,
        ),
        ChapterDef(
            "C3", "멈춰야 할 때", "차가 말을 안 들을 때예요. 침착하게 해 봐요.",
            listOf(SituationId.WARNING_LIGHT, SituationId.BREAKDOWN, SituationId.UNINTENDED_ACCEL), 4,
        ),
        ChapterDef("C4", "내리기 전에", "주차를 마쳤어요. 내리기 전에 확인해요.", listOf(SituationId.BEFORE_EXIT), 1),
        ChapterDef(
            "C5", "길 위의 규칙", "알고 있으면 덜 당황하는 것들이에요.",
            listOf(SituationId.RIGHT_TURN_CROSSWALK, SituationId.HIGHWAY_MERGE, SituationId.HYDROPLANING, SituationId.HIPASS_WRONG_LANE), 4,
        ),
    )

    /** "이 차 면허"에 필요한 챕터. 이 차의 스위치를 익히는 챕터만 센다(C5는 차와 상관없는 규칙). */
    val LICENSE_CHAPTERS: List<String> = listOf("C1", "C2", "C3", "C4")

    const val SURPRISE = "SURPRISE"
    const val VERSUS = "VERSUS"
}
