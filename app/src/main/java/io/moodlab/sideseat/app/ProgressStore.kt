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

    /** 끊긴 챕터들의 이어 하기 지점. 없으면 빈 목록. */
    fun loadResume(): List<io.moodlab.sideseat.core.ResumeState> = read("resume.json") { text ->
        val one = io.moodlab.sideseat.core.ResumeState.serializer()
        when {
            text.isBlank() -> emptyList()
            // v19의 파일은 지점 하나만 담았다
            text.trimStart().startsWith("{") -> listOf(json.decodeFromString(one, text))
            else -> json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(one), text)
        }
    }.orEmpty()

    fun saveResume(r: List<io.moodlab.sideseat.core.ResumeState>) =
        write("resume.json", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(io.moodlab.sideseat.core.ResumeState.serializer()), r))

    /** 첫 실행 소개를 봤는가. */
    fun loadIntroSeen(): Boolean = read("intro_seen.txt") { true } ?: false

    fun saveIntroSeen() = write("intro_seen.txt", "1")

    /** 사용자가 고른 차종 아이디. 아직 안 골랐으면 null, "그 밖의 차"를 골랐으면 빈 문자열. */
    fun loadVehicleChoice(): String? = read("vehicle_choice.txt") { it.trim() }

    fun saveVehicleChoice(id: String?) = write("vehicle_choice.txt", id.orEmpty())

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
        // 위치 연계 코치(명세 6.4). 이전 파일에는 없으므로 기본값을 둔다.
        val speedingCount: Int = 0,
        val longestSpeedingSec: Int = 0,
        val schoolZonePasses: Int = 0,
        val schoolZoneMaxKmh: Int? = null,
        val durationSec: Int = 0,
        val rainSec: Int = 0,
        val nightSec: Int = 0,
    ) {
        fun toSummary() = DriveSummary(
            durationMin, rainMin, nightMin,
            utterances.map { CoachUtterance(it.ruleId, it.text, it.urgent) },
            observations,
            suggestedSituation?.let { runCatching { SituationId.valueOf(it) }.getOrNull() },
            ruleBasedReview,
            speedingCount, longestSpeedingSec, schoolZonePasses, schoolZoneMaxKmh,
            durationSec, rainSec, nightSec,
        )

        companion object {
            fun of(s: DriveSummary) = StoredSummary(
                s.durationMin, s.rainMin, s.nightMin,
                s.utterances.map { StoredUtterance(it.ruleId, it.text, it.urgent) },
                s.observations, s.suggestedSituation?.name, s.ruleBasedReview,
                s.speedingCount, s.longestSpeedingSec, s.schoolZonePasses, s.schoolZoneMaxKmh,
                s.durationSec, s.rainSec, s.nightSec,
            )
        }
    }

    @Serializable
    private data class StoredUtterance(val ruleId: String, val text: String, val urgent: Boolean)

    private companion object {
        const val TAG = "SideSeat"
    }
}
