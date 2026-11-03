package io.moodlab.moodrive.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CatalogFile(val version: Int, val note: String = "", val tracks: List<Track>)

/** 곡 카탈로그. 원본은 core/src/main/resources/catalog/tracks.json (JVM·Android 공통 리소스). */
object Catalog {
    val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun parse(text: String): List<Track> {
        val tracks = json.decodeFromString(CatalogFile.serializer(), text).tracks
        validate(tracks)
        return tracks
    }

    /** 잘못 태깅된 곡을 조기에 잡는다. */
    fun validate(tracks: List<Track>) {
        require(tracks.isNotEmpty()) { "catalog is empty" }
        val dup = tracks.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(dup.isEmpty()) { "duplicate ids: $dup" }
        tracks.forEach {
            require(it.mood.energy in -1f..1f && it.mood.valence in -1f..1f) { "mood out of range: ${it.id}" }
            require(it.durationSec in 30..900) { "duration out of range: ${it.id}" }
        }
    }

    fun loadDefault(): List<Track> {
        val stream = Catalog::class.java.getResourceAsStream("/catalog/tracks.json")
            ?: error("catalog/tracks.json not found on classpath")
        return parse(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }
}

/** 기본 카탈로그 (테스트·데모용). */
object SampleCatalog {
    val tracks: List<Track> by lazy { Catalog.loadDefault() }
}
