package io.moodlab.moodrive.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** GOALS.md G3.2: 데모 시나리오가 시연 품질 기준을 만족하는지 */
class DemoTraceTest {
    private val trace = DemoTrace.generate()
    private val frames = trace.frames
    private val tracks = trace.tracks.associateBy { it.id }

    @Test
    fun `at least six scene changes`() {
        val changes = frames.zipWithNext().count { (a, b) -> a.scene != b.scene }
        assertTrue(changes >= 6, "changes=$changes")
        // 기획서·드라이브 카드가 인용하는 수치: 정차 제외 장면 전환 7회, 곡 9개, DJ 멘트 7회
        val driving = frames.filter { it.gear != "P" }
        assertEquals(7, driving.zipWithNext().count { (a, b) -> a.scene != b.scene })
        assertEquals(9, driving.mapNotNull { it.trackId }.fold(mutableListOf<String>()) { acc, id -> if (acc.lastOrNull() != id) acc.add(id); acc }.size)
        assertEquals(7, frames.count { it.speak != null })
    }

    @Test
    fun `selected tracks fit scene mood`() {
        // 곡이 새로 선택된 시점에 그 곡과 목표 무드의 거리
        val picks = frames.zipWithNext().filter { (a, b) -> b.trackId != a.trackId }.map { it.second } + frames.first()
        val far = picks.map { f -> f.t to tracks.getValue(f.trackId!!).mood.distanceTo(f.mood) }.filter { it.second > 0.6f }
        assertEquals(emptyList(), far, "tracks too far from target mood")
    }

    @Test
    fun `dj is not chatty in vehicle time`() {
        // 일반 멘트 사이는 차량 시간 5분 이상. 안전·핵심 순간 멘트는 예외.
        val exempt = setOf("FATIGUE_BOOST", "HEAVY_RAIN", "ARRIVAL_FINALE", "PARKED")
        val normal = frames.filter { it.speak != null && it.scene !in exempt && it.t > 0 }
        normal.zipWithNext().forEach { (a, b) ->
            val gapSec = (b.t - a.t) * trace.timeScale
            assertTrue(gapSec >= 300, "DJ lines too close: ${a.t}s and ${b.t}s")
        }
        val total = frames.count { it.speak != null }
        val vehicleMinutes = frames.size * trace.timeScale / 60f
        assertTrue(total / vehicleMinutes <= 0.3f, "too many lines: $total in $vehicleMinutes min")
    }

    @Test
    fun `tunnel keeps the song and is silent`() {
        val tunnel = frames.filter { it.scene == "TUNNEL" }
        assertTrue(tunnel.isNotEmpty())
        // 들어갈 때·나올 때 곡을 끊지 않는다 (곡이 자연스럽게 끝나 바뀌는 건 허용)
        val enter = tunnel.first().t
        val exit = tunnel.last().t + 1
        assertEquals(frames[enter - 1].trackId, frames[enter].trackId, "switched on tunnel entry")
        assertEquals(frames[exit - 1].trackId, frames[exit].trackId, "switched on tunnel exit")
        assertTrue(tunnel.none { it.speak != null })
    }

    @Test
    fun `ambient is held while driving and applied after parking`() {
        assertTrue(frames.filter { it.gear == "D" }.all { it.ambient == null })
        assertTrue(frames.last().ambient != null)
    }

    @Test
    fun `trip ends with a card listing the scenes`() {
        val end = frames.single { it.tripEnded != null }
        assertEquals("정차", end.sceneLabel)
        assertTrue(end.tripEnded!!.containsAll(listOf("비", "폭우", "터널", "정체", "도착 피날레")))
    }

    @Test
    fun `finale track is high energy and bright`() {
        val finale = frames.first { it.scene == "ARRIVAL_FINALE" }
        val m = tracks.getValue(finale.trackId!!).mood
        assertTrue(m.energy > 0.4f && m.valence > 0.5f, "finale mood=$m")
    }

    @Test
    fun `catalog json is valid and rich enough`() {
        val all = SampleCatalog.tracks
        assertTrue(all.size >= 40)
        // 무드 사분면마다 최소 5곡
        val quads = all.groupBy { (it.mood.energy >= 0) to (it.mood.valence >= 0) }
        assertEquals(4, quads.size)
        assertTrue(quads.values.all { it.size >= 5 }, quads.mapValues { it.value.size }.toString())
    }
}
