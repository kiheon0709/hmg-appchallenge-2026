package io.moodlab.moodrive.core

/**
 * F2 취향 보정. (장면 × 장르) 버킷마다 선호 점수를 둔다.
 * 장르 온보딩 선택은 모든 장면에 사전 점수로 들어간다.
 */
class TasteModel(preferredGenres: Set<String> = emptySet()) {
    private val prior = preferredGenres.associateWith { 1f }
    private val scores = mutableMapOf<String, Float>()

    fun score(scene: Scene, genre: String): Float =
        (prior[genre] ?: 0f) + (scores[key(scene, genre)] ?: 0f)

    fun record(scene: Scene, genre: String, feedback: Feedback) {
        val k = key(scene, genre)
        scores[k] = ((scores[k] ?: 0f) + feedback.weight).coerceIn(-6f, 6f)
    }

    fun snapshot(): Map<String, Float> = scores.toMap()

    fun restore(saved: Map<String, Float>) {
        scores.clear()
        scores.putAll(saved)
    }

    private fun key(scene: Scene, genre: String) = "${scene.name}|$genre"
}

/** 무드 목표에 가까우면서 취향 점수가 높은 곡을 고른다. */
class Curator(
    private val catalog: List<Track>,
    private val taste: TasteModel,
    private val config: Config = Config(),
) {
    data class Config(
        val moodWeight: Float = 1f,
        val tasteWeight: Float = 0.12f,
        /** 최근 재생한 곡 몇 개를 다시 고르지 않을지. */
        val noRepeatCount: Int = 8,
    )

    private val history = ArrayDeque<String>()

    init {
        require(catalog.isNotEmpty()) { "catalog is empty" }
    }

    fun score(track: Track, target: Mood, scene: Scene): Float =
        -config.moodWeight * track.mood.distanceTo(target) +
            config.tasteWeight * taste.score(scene, track.genre)

    fun rank(target: Mood, scene: Scene, exclude: Set<String> = emptySet()): List<Track> {
        val blocked = history.toSet() + exclude
        val pool = catalog.filter { it.id !in blocked }.ifEmpty { catalog.filter { it.id !in exclude } }.ifEmpty { catalog }
        return pool.sortedByDescending { score(it, target, scene) }
    }

    fun next(target: Mood, scene: Scene): Track = rank(target, scene).first().also { markPlayed(it) }

    fun markPlayed(track: Track) {
        history.remove(track.id)
        history.addLast(track.id)
        while (history.size > config.noRepeatCount) history.removeFirst()
    }
}

/**
 * F4 도착 피날레. 남은 시간 안에 곡을 채우고, 마지막에 클라이맥스 곡을 둔다.
 * 배낭 문제의 탐욕 근사: 점수 순으로 넣되 남은 시간을 넘기지 않는다.
 */
object FinalePlanner {
    data class Plan(val queue: List<Track>, val totalSec: Int)

    /**
     * @param ranked 현재 장면 기준 점수 순으로 정렬된 후보
     * @param remainingSec 도착까지 남은 시간
     * @param finale 마지막에 둘 곡 (보통 FINALE 무드 기준 1위)
     */
    fun plan(ranked: List<Track>, remainingSec: Int, finale: Track, toleranceSec: Int = 45): Plan {
        val budget = remainingSec - finale.durationSec
        if (budget <= 0) return Plan(listOf(finale), finale.durationSec)
        val picked = mutableListOf<Track>()
        var used = 0
        for (t in ranked) {
            if (t.id == finale.id) continue
            if (used + t.durationSec <= budget + toleranceSec) {
                picked += t
                used += t.durationSec
            }
            if (used >= budget - toleranceSec) break
        }
        return Plan(picked + finale, used + finale.durationSec)
    }
}
