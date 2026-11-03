package io.moodlab.moodrive.core

import kotlinx.serialization.Serializable
import java.io.File

/** HTML 프로토타입·시연 검증용으로, 데모 시나리오를 1초 단위로 돌린 결과. */
@Serializable
data class TraceFrame(
    val t: Int,
    val clock: String,
    val caption: String,
    val gear: String,
    val wiper: String,
    val lowBeam: Boolean,
    val etaMin: Int?,
    val scene: String,
    val sceneLabel: String,
    val mood: Mood,
    val trackId: String?,
    val positionSec: Int,
    /** 실제로 적용된 조명(주행 중 보류 정책 반영). */
    val ambient: AmbientCommand?,
    /** 정책상 보류 중인 조명 목표. 주행 중 조명 허용 시 보일 값. */
    val ambientTarget: AmbientCommand,
    val speak: String?,
    val restStop: Boolean,
    val tripEnded: List<String>?,
)

@Serializable
data class Trace(
    val scenario: String,
    val timeScale: Int,
    val allowAmbientWhileDriving: Boolean,
    val tracks: List<Track>,
    val frames: List<TraceFrame>,
)

object DemoTrace {
    fun generate(
        scenario: Scenario = Scenario.rainyCommute(),
        catalog: List<Track> = SampleCatalog.tracks,
        allowAmbientWhileDriving: Boolean = false,
    ): Trace {
        val session = MooDriveSession(catalog, ambient = AmbientPlanner(allowWhileDriving = allowAmbientWhileDriving))
        val preview = AmbientPlanner(allowWhileDriving = true)
        var ambient: AmbientCommand? = null
        var track: Track? = null
        var pos = 0
        val frames = mutableListOf<TraceFrame>()

        for (t in 0..scenario.durationSec) {
            val s = scenario.snapshotAt(t)
            val actions = session.onSnapshot(s).toMutableList()
            // 시뮬레이션 재생: 시나리오 1초 = 차량 시간 timeScale초
            if (track != null && session.currentScene != Scene.PARKED && actions.none { it is Action.Play }) {
                pos += scenario.timeScale
                if (pos >= track.durationSec) actions += session.onTrackEnded(completed = true, playedSec = pos)
            }
            var speak: String? = null
            var tripEnded: List<String>? = null
            var rest = false
            for (a in actions) when (a) {
                is Action.Play -> { track = a.track; pos = 0 }
                is Action.SetAmbient -> ambient = a.command
                is Action.Speak -> speak = listOfNotNull(speak, a.text).joinToString(" ")
                is Action.TripEnded -> tripEnded = a.log.map { it.scene.label }.distinctConsecutive()
                Action.SuggestRestStop -> rest = true
                Action.AddRestStop -> Unit
                is Action.SceneChanged -> Unit
            }
            val scene = session.currentScene!!
            val target = session.targetMood()
            frames += TraceFrame(
                t = t,
                clock = "%02d:%02d".format(s.minuteOfDay / 60, s.minuteOfDay % 60),
                caption = scenario.captionAt(t),
                gear = s.gear.name,
                wiper = s.wiper.name,
                lowBeam = s.lowBeamOn,
                etaMin = s.etaMinutes,
                scene = scene.name,
                sceneLabel = scene.label,
                mood = target,
                trackId = track?.id,
                positionSec = pos,
                ambient = ambient,
                ambientTarget = preview.commandFor(target, night = !s.isDaytime()),
                speak = speak,
                restStop = rest,
                tripEnded = tripEnded,
            )
        }
        return Trace(scenario.name, scenario.timeScale, allowAmbientWhileDriving, catalog, frames)
    }

    private fun <T> List<T>.distinctConsecutive(): List<T> =
        fold(mutableListOf()) { acc, x -> if (acc.lastOrNull() != x) acc.add(x); acc }
}

/** ./gradlew :core:demoTrace */
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "demo-trace.json" })
    out.parentFile?.mkdirs()
    val trace = DemoTrace.generate()
    val text = Catalog.json.encodeToString(Trace.serializer(), trace)
    out.writeText(text)
    // file:// 로 열어도 되도록 JS 버전도 같이 쓴다 (프로토타입이 <script>로 로드)
    File(out.parentFile, out.nameWithoutExtension + ".js").writeText("window.TRACE = $text;\n")
    val scenes = trace.frames.map { it.sceneLabel }.fold(mutableListOf<String>()) { a, x -> if (a.lastOrNull() != x) a.add(x); a }
    println("trace: ${trace.frames.size} frames → ${out.path}")
    println("scenes: ${scenes.joinToString(" → ")}")
    trace.frames.filter { it.speak != null }.forEach { println("  ${it.t}s 🎙 ${it.speak}") }
}
