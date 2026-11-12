package io.moodlab.sideseat.app

import android.content.Context
import android.util.Log
import io.moodlab.sideseat.core.CoachUtterance
import io.moodlab.sideseat.core.DriveSummary
import io.moodlab.sideseat.core.SituationId
import io.moodlab.sideseat.core.TrainingProgress
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** 훈련 기록과 지난 주행 요약을 앱 내부 저장소에 JSON으로 둔다. 실패해도 앱은 계속 돈다. */
class ProgressStore(context: Context) {
    private val dir = context.filesDir
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun loadProgress(): TrainingProgress = read("progress.json") { json.decodeFromString(TrainingProgress.serializer(), it) }
        ?: TrainingProgress()

    fun saveProgress(p: TrainingProgress) = write("progress.json", json.encodeToString(TrainingProgress.serializer(), p))

    fun loadLastDrive(): DriveSummary? = read("last_drive.json") { json.decodeFromString(StoredSummary.serializer(), it).toSummary() }

    fun saveLastDrive(s: DriveSummary) = write("last_drive.json", json.encodeToString(StoredSummary.serializer(), StoredSummary.of(s)))

    private fun <T> read(name: String, parse: (String) -> T): T? = runCatching {
        File(dir, name).takeIf { it.exists() }?.readText()?.let(parse)
    }.onFailure { Log.w(TAG, "load $name failed: $it") }.getOrNull()

    private fun write(name: String, text: String) {
        runCatching {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(dir, name))
        }.onFailure { Log.w(TAG, "save $name failed: $it") }
    }

    /** core의 DriveSummary는 직렬화 표시가 없어서 저장용 사본을 둔다. */
    @Serializable
    private data class StoredSummary(
        val durationMin: Int,
        val rainMin: Int,
        val nightMin: Int,
        val utterances: List<StoredUtterance>,
        val observations: Map<String, Int>,
        val suggestedSituation: String?,
        val ruleBasedReview: List<String>,
    ) {
        fun toSummary() = DriveSummary(
            durationMin, rainMin, nightMin,
            utterances.map { CoachUtterance(it.ruleId, it.text, it.urgent) },
            observations,
            suggestedSituation?.let { runCatching { SituationId.valueOf(it) }.getOrNull() },
            ruleBasedReview,
        )

        companion object {
            fun of(s: DriveSummary) = StoredSummary(
                s.durationMin, s.rainMin, s.nightMin,
                s.utterances.map { StoredUtterance(it.ruleId, it.text, it.urgent) },
                s.observations, s.suggestedSituation?.name, s.ruleBasedReview,
            )
        }
    }

    @Serializable
    private data class StoredUtterance(val ruleId: String, val text: String, val urgent: Boolean)

    private companion object {
        const val TAG = "SideSeat"
    }
}
