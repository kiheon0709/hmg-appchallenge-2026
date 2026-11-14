package io.moodlab.sideseat.core

/**
 * 내비가 알려 준 경로의 특징. SDK 타입에 묶이지 않게 enum 이름(문자열)으로 받는다.
 * - [turnTypes]: NaviHelper TurnType 이름(예: TURN_TOLL, TURN_ENTER_IC_R)
 * - [cautionTypes]: CautionPointType 이름(예: FOG_AREA)
 * - [congestion]: TrafficCongestionType 이름(FAST, MODERATE, SLOW, HEAVY)
 */
data class RouteFeatures(
    val turnTypes: Set<String> = emptySet(),
    val cautionTypes: Set<String> = emptySet(),
    val congestion: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = turnTypes.isEmpty() && cautionTypes.isEmpty() && congestion.isEmpty()
}

/** 경로에서 찾은 것 하나와 그에 맞는 훈련 상황. [place]는 "가는 길에 ○○" 자리에 들어가는 말. */
data class RouteMatch(val situation: SituationId, val place: String)

/** 홈 화면 추천. [reason]은 추천 카드의 한 줄. */
data class RouteRecommendation(val chapterId: String, val situation: SituationId, val reason: String)

/** 목적지까지 가는 길에 있는 것 → 이미 있는 훈련 상황. 순수 함수. */
object RouteTraining {
    /**
     * 경로 특징을 훈련 상황에 대응시킨다. 앞에 있을수록 먼저 권한다(초보가 당황하기 쉬운 순).
     * 같은 상황은 한 번만 나온다. 맞는 것이 없으면 빈 목록.
     */
    fun matches(f: RouteFeatures): List<RouteMatch> {
        val turns = f.turnTypes
        val out = ArrayList<RouteMatch>()
        if ("TURN_TOLL" in turns) out += RouteMatch(SituationId.HIPASS_WRONG_LANE, "요금소")
        if (turns.any { it.startsWith("TURN_ENTER_IC") }) out += RouteMatch(SituationId.HIGHWAY_MERGE, "고속도로 진입로")
        else if ("TURN_DEVIDE_MERGE" in turns) out += RouteMatch(SituationId.HIGHWAY_MERGE, "합류 구간")
        if ("TURN_TUNNEL" in turns) out += RouteMatch(SituationId.TUNNEL, "터널")
        if ("FOG_AREA" in f.cautionTypes) out += RouteMatch(SituationId.DENSE_FOG, "안개가 잦은 구간")
        if ("SLIPPERY_ROAD_ALERT" in f.cautionTypes) out += RouteMatch(SituationId.HYDROPLANING, "미끄러운 길")
        if ("HEAVY" in f.congestion || "SLOW" in f.congestion) out += RouteMatch(SituationId.SUDDEN_STOP, "막히는 구간")
        else if ("ACCIDENT_PRONE_AREA" in f.cautionTypes) out += RouteMatch(SituationId.SUDDEN_STOP, "사고가 잦은 곳")
        if ("TURN_RIGHT" in turns) out += RouteMatch(SituationId.RIGHT_TURN_CROSSWALK, "우회전")
        return out
    }

    /**
     * 홈 화면 추천 하나. 아직 별이 없는 상황([practiced]에 없는 것)을 먼저, 그다음 [matches] 순서.
     * 잠긴 챕터의 상황은 건너뛴다. 권할 것이 없으면 null(기존 "오늘의 추천"을 쓴다).
     */
    fun recommend(f: RouteFeatures, chapters: List<ChapterInfo>, practiced: Set<SituationId> = emptySet()): RouteRecommendation? {
        val open = matches(f).mapNotNull { m ->
            chapters.firstOrNull { !it.locked && m.situation in it.situations }?.let { m to it.id }
        }
        val (m, chapterId) = open.firstOrNull { it.first.situation !in practiced } ?: open.firstOrNull() ?: return null
        return RouteRecommendation(chapterId, m.situation, "가는 길에 ${m.place}${subjectParticle(m.place)} 있어요. 미리 연습해 볼까요?")
    }

    /** 받침이 있으면 "이", 없으면 "가". */
    internal fun subjectParticle(word: String): String {
        val c = word.lastOrNull() ?: return "가"
        return if (c in '가'..'힣' && (c - '가') % 28 != 0) "이" else "가"
    }
}
