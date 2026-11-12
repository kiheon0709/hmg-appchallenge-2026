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
    /** S10 브레이크 유지 시간. */
    val brakeHoldMs: Long = 1_500,
    /** S10 브레이크를 이만큼 뗐다 밟으면 감점. */
    val brakePumpReleases: Int = 2,
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
    val licenseChapters: List<String> = listOf("C1", "C2", "C3", "C4"),
    /** 면허 챕터 통과 기준(모든 상황 이 별 이상). */
    val licenseMinStars: Int = 2,
    val random: Random = Random.Default,
) {
    fun limitOf(id: SituationId): Long = limitsMs[id] ?: 10_000

    companion object {
        val DEFAULT_LIMITS: Map<SituationId, Long> = mapOf(
            SituationId.HEAVY_RAIN to 8_000,
            SituationId.FOGGED_WINDSHIELD to 10_000,
            SituationId.SUDDEN_STOP to 1_500,
            SituationId.TUNNEL to 6_000,
            SituationId.DENSE_FOG to 10_000,
            SituationId.ONCOMING_GLARE to 5_000,
            // 명세는 8초지만 말로 고르는 단계(STT 왕복)가 있어 15초로 둔다(decisions.md).
            SituationId.BREAKDOWN to 15_000,
            SituationId.WARNING_LIGHT to 15_000,
            SituationId.DROWSY to 10_000,
            SituationId.UNINTENDED_ACCEL to 8_000,
            SituationId.BEFORE_EXIT to 0, // 시간 제한 없음
            SituationId.WIPER_DEBRIS to 8_000,
        )
    }
}

/** 챕터 정의(명세 5.3). */
data class ChapterDef(val id: String, val title: String, val story: String, val situations: List<SituationId>, val estMinutes: Int)

object ChapterCatalog {
    val chapters: List<ChapterDef> = listOf(
        ChapterDef(
            "C1", "첫 빗길", "금요일 밤, 친구 넷을 태우고 강릉 엠티 가는 길이에요.",
            listOf(SituationId.HEAVY_RAIN, SituationId.FOGGED_WINDSHIELD, SituationId.SUDDEN_STOP), 3,
        ),
        ChapterDef(
            "C2", "밤 고속도로", "새벽에 혼자 돌아오는 길이에요.",
            listOf(SituationId.TUNNEL, SituationId.ONCOMING_GLARE, SituationId.DENSE_FOG, SituationId.DROWSY), 4,
        ),
        ChapterDef(
            "C3", "멈춰야 할 때", "차가 말을 안 들을 때예요. 침착하게 해 봐요.",
            listOf(SituationId.WARNING_LIGHT, SituationId.BREAKDOWN, SituationId.UNINTENDED_ACCEL), 4,
        ),
        ChapterDef("C4", "내리기 전에", "부모님 차를 돌려드리기 전이에요.", listOf(SituationId.BEFORE_EXIT), 1),
    )

    const val SURPRISE = "SURPRISE"
    const val VERSUS = "VERSUS"
}
