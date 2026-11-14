package io.moodlab.sideseat.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteTrainingTest {
    private val chapters = ChapterCatalog.chapters.map { ChapterInfo(it.id, it.title, "", it.situations, 3, locked = false) }

    private fun situations(turns: Set<String> = emptySet(), cautions: Set<String> = emptySet(), congestion: Set<String> = emptySet()) =
        RouteTraining.matches(RouteFeatures(turns, cautions, congestion)).map { it.situation }

    @Test fun eachRouteFeatureMapsToItsSituation() {
        assertEquals(listOf(SituationId.TOLL_BOOTH, SituationId.HIPASS_WRONG_LANE), situations(turns = setOf("TURN_TOLL")))
        assertEquals(listOf(SituationId.HIGHWAY_MERGE), situations(turns = setOf("TURN_ENTER_IC_R")))
        assertEquals(listOf(SituationId.HIGHWAY_MERGE), situations(turns = setOf("TURN_DEVIDE_MERGE")))
        assertEquals(listOf(SituationId.TUNNEL), situations(turns = setOf("TURN_TUNNEL")))
        assertEquals(listOf(SituationId.RIGHT_TURN_CROSSWALK), situations(turns = setOf("TURN_RIGHT")))
        assertEquals(listOf(SituationId.DENSE_FOG), situations(cautions = setOf("FOG_AREA")))
        assertEquals(listOf(SituationId.HYDROPLANING), situations(cautions = setOf("SLIPPERY_ROAD_ALERT")))
        assertEquals(listOf(SituationId.SUDDEN_STOP), situations(cautions = setOf("ACCIDENT_PRONE_AREA")))
        assertEquals(listOf(SituationId.SUDDEN_STOP), situations(congestion = setOf("HEAVY")))
        assertEquals(listOf(SituationId.SUDDEN_STOP), situations(congestion = setOf("SLOW")))
    }

    @Test fun unrelatedOrEmptyRouteGivesNothing() {
        assertEquals(emptyList(), situations())
        assertEquals(emptyList(), situations(setOf("TURN_LEFT", "TURN_STRAIGHT", "TURN_EXIT_IC_R"), setOf("SCHOOL_ZONE", "NARROW_ROAD"), setOf("FAST", "MODERATE")))
        assertNull(RouteTraining.recommend(RouteFeatures(), chapters))
        assertNull(RouteTraining.recommend(RouteFeatures(turnTypes = setOf("TURN_LEFT")), chapters))
    }

    @Test fun orderAndNoDuplicates() {
        val all = situations(
            setOf("TURN_RIGHT", "TURN_TUNNEL", "TURN_ENTER_IC_L", "TURN_DEVIDE_MERGE", "TURN_TOLL"),
            setOf("SLIPPERY_ROAD_ALERT", "FOG_AREA", "ACCIDENT_PRONE_AREA"), setOf("HEAVY"),
        )
        assertEquals(
            listOf(
                SituationId.TOLL_BOOTH, SituationId.HIPASS_WRONG_LANE, SituationId.HIGHWAY_MERGE, SituationId.TUNNEL, SituationId.DENSE_FOG,
                SituationId.HYDROPLANING, SituationId.SUDDEN_STOP, SituationId.RIGHT_TURN_CROSSWALK,
            ),
            all,
        )
    }

    @Test fun recommendationNamesChapterAndSentence() {
        val toll = RouteTraining.recommend(RouteFeatures(turnTypes = setOf("TURN_TOLL", "TURN_TUNNEL")), chapters)!!
        assertEquals("C5", toll.chapterId)
        assertEquals(SituationId.TOLL_BOOTH, toll.situation) // 요금소는 일반 차로로 지나는 법을 먼저 권한다
        assertEquals("가는 길에 요금소가 있어요. 미리 연습해 볼까요?", toll.reason)

        val tunnel = RouteTraining.recommend(RouteFeatures(turnTypes = setOf("TURN_TUNNEL")), chapters)!!
        assertEquals("C2", tunnel.chapterId)
        assertEquals("가는 길에 터널이 있어요. 미리 연습해 볼까요?", tunnel.reason)

        val jam = RouteTraining.recommend(RouteFeatures(congestion = setOf("HEAVY")), chapters)!!
        assertEquals("C1", jam.chapterId)
        assertEquals("가는 길에 막히는 구간이 있어요. 미리 연습해 볼까요?", jam.reason)
    }

    @Test fun unpracticedSituationComesFirst() {
        val f = RouteFeatures(turnTypes = setOf("TURN_TOLL", "TURN_TUNNEL"))
        assertEquals(SituationId.HIPASS_WRONG_LANE, RouteTraining.recommend(f, chapters, practiced = setOf(SituationId.TOLL_BOOTH))!!.situation)
        assertEquals(SituationId.TUNNEL, RouteTraining.recommend(f, chapters, practiced = setOf(SituationId.TOLL_BOOTH, SituationId.HIPASS_WRONG_LANE))!!.situation)
        // 다 해 본 것이면 원래 순서의 첫째
        assertEquals(
            SituationId.TOLL_BOOTH,
            RouteTraining.recommend(f, chapters, practiced = setOf(SituationId.TOLL_BOOTH, SituationId.HIPASS_WRONG_LANE, SituationId.TUNNEL))!!.situation,
        )
    }

    @Test fun lockedChapterIsSkipped() {
        val locked = chapters.map { if (it.id == "C5") it.copy(locked = true) else it }
        val f = RouteFeatures(turnTypes = setOf("TURN_TOLL", "TURN_TUNNEL"))
        assertEquals(SituationId.TUNNEL, RouteTraining.recommend(f, locked)!!.situation)
        assertNull(RouteTraining.recommend(RouteFeatures(turnTypes = setOf("TURN_TOLL")), locked))
    }

    @Test fun particleFollowsFinalConsonant() {
        assertEquals("가", RouteTraining.subjectParticle("요금소"))
        assertEquals("이", RouteTraining.subjectParticle("터널"))
        assertEquals("이", RouteTraining.subjectParticle("우회전"))
        assertEquals("가", RouteTraining.subjectParticle("고속도로 진입로"))
    }
}
