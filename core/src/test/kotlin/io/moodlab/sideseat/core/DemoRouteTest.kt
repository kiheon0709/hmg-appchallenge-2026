package io.moodlab.sideseat.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 시연 경로(tools/zones/demo_route_k11_k12.csv)를 앱에 넣는 데이터(assets/zones)와 함께 코치에 1초씩 넣어
 * K11·K12가 실제로 나오는지 본다. 게이트웨이가 하는 일(좌표 → 방향 → 지도 제한속도)을 같은 core 함수로 따라 한다.
 */
class DemoRouteTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private fun file(path: String) = File(root, path)

    @Test
    fun `demo route triggers school zone and speeding`() {
        val zones = SchoolZoneIndex.parse(file("app/src/main/assets/zones/school_zones.tsv").readText())
        val ways = RoadSpeedLimits.parseOverpassWays(file("app/src/main/assets/zones/road_limits.json").readText())
        assertTrue(zones.zones.size > 1_000, "zones=${zones.zones.size}")
        assertTrue(ways.size > 100, "ways=${ways.size}")

        val rows = file("tools/zones/demo_route_k11_k12.csv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("t_sec") }
            .map { it.split(",") }
        val coach = DefaultDriveCoach(CoachConfig(), zones)
        coach.onDriveStart(0)
        val track = LocationTrack()
        val said = mutableListOf<Pair<Int, CoachUtterance>>()
        val base = VehicleState(0, parked = false, lowBeamOn = true, minuteOfDay = 13 * 60)
        for (r in rows) {
            val t = r[0].toInt()
            val lat = r[1].toDouble()
            val lon = r[2].toDouble()
            val now = t * 1_000L
            track.add(lat, lon, now)
            val limit = RoadSpeedLimits.match(lat, lon, track.headingDeg(), ways)?.limitKmh
            val state = base.copy(timestampMs = now, latitude = lat, longitude = lon, locationTimeMs = now, speedLimitKmh = limit)
            coach.tick(state, now)?.let { said += t to it }
        }
        val summary = coach.onDriveEnd(rows.last()[0].toLong() * 1_000)

        val k11 = said.filter { it.second.ruleId == "K11" }
        val k12 = said.filter { it.second.ruleId == "K12" }
        assertEquals(1, k11.size, "said=$said")
        assertEquals("어린이 보호구역이에요. 30 아래로 가요.", k11[0].second.text)
        assertTrue(k12.isNotEmpty(), "said=$said")
        assertEquals("제한속도가 80이에요. 조금 줄여요.", k12.first().second.text)
        assertTrue(k12.first().first in 338..380, "K12 at ${k12.first().first}")
        assertTrue(summary.schoolZonePasses >= 1)
        assertTrue(summary.schoolZoneMaxKmh!! in 34..40, "zoneMax=${summary.schoolZoneMaxKmh}")
        assertEquals("어린이 보호구역에서 최고 ${summary.schoolZoneMaxKmh}km/h였어요. 다음엔 30 아래로 지나가요.", summary.ruleBasedReview[1])
        assertEquals(1, summary.speedingCount)
        assertTrue(summary.longestSpeedingSec >= 10, "longest=${summary.longestSpeedingSec}")
        println("demo route: said=${said.map { "${it.first}s ${it.second.ruleId}" }} summary=$summary")
    }
}
