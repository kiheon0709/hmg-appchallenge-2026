package io.moodlab.sideseat.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 위도 1도 ≈ 111,195m (지구 반지름 6,371km). */
const val M_PER_DEG_LAT = 111_194.93

class LocationTest {
    private val baseLat = 37.4127
    private val baseLon = 127.0947

    @Test
    fun `speed is the median of the last three seconds`() {
        val t = LocationTrack()
        assertNull(t.speedKmh())
        var lat = baseLat
        t.add(lat, baseLon, 0)
        // 60km/h로 북쪽 → 1초에 16.67m
        for (i in 1..5) {
            lat += 16.667 / M_PER_DEG_LAT
            t.add(lat, baseLon, i * 1_000L)
        }
        assertEquals(60f, t.speedKmh()!!, 0.5f)
        assertEquals(0.0, t.headingDeg()!!, 1.0)
        // 한 번 튀는 점(100m 점프)은 중앙값이 막는다
        lat += 100 / M_PER_DEG_LAT
        t.add(lat, baseLon, 6_000)
        assertTrue(t.speedKmh()!! < 70f, "튄 값: ${t.speedKmh()}")
    }

    @Test
    fun `skipped or late fixes do not double the speed`() {
        // 36km/h(초당 10m). 전달이 한 번 밀려 같은 점이 두 번 오고, 다음에 두 칸을 한 번에 따라잡는 경우
        val step = 10.0 / M_PER_DEG_LAT
        val positions = listOf(0, 1, 2, 3, 3, 5, 6, 7, 7, 9, 10)
        val t = LocationTrack()
        val speeds = positions.mapIndexed { i, k -> t.add(baseLat + k * step, baseLon, i * 1_000L); t.speedKmh() }.drop(3)
        assertTrue(speeds.all { it!! <= 40f }, "speeds=$speeds")
        // 전달 시각이 흔들려(0.5초·1.5초) 한 구간이 72km/h로 계산돼도
        val j = LocationTrack()
        val times = listOf(0L, 1_000, 2_000, 3_000, 3_500, 5_000, 6_000, 7_000, 7_500, 9_000, 10_000)
        val jittered = times.mapIndexed { i, tm -> j.add(baseLat + i * step, baseLon, tm); j.speedKmh() }.drop(3)
        assertTrue(jittered.all { it!! <= 40f }, "jittered=$jittered")
    }

    @Test
    fun `heading follows movement east and ignores standing still`() {
        val t = LocationTrack()
        val dLon = 16.667 / (M_PER_DEG_LAT * kotlin.math.cos(Math.toRadians(baseLat)))
        for (i in 0..3) t.add(baseLat, baseLon + i * dLon, i * 1_000L)
        assertEquals(90.0, t.headingDeg()!!, 1.0)
        for (i in 4..8) t.add(baseLat, baseLon + 3 * dLon, i * 1_000L)
        assertEquals(0f, t.speedKmh()!!, 0.1f)
        assertEquals(90.0, t.headingDeg()!!, 1.0, "멈추면 마지막 방향 유지")
    }

    @Test
    fun `duplicate fixes and long gaps`() {
        val t = LocationTrack()
        t.add(baseLat, baseLon, 0)
        assertEquals(false, t.add(baseLat, baseLon, 100))
        t.add(baseLat + 0.0001, baseLon, 1_000)
        assertNotNull(t.speedKmh())
        t.add(baseLat + 0.0002, baseLon, 20_000) // 끊김
        assertNull(t.speedKmh())
    }

    @Test
    fun `school zone file is parsed and nearest zone found within radius`() {
        val text = """
            # 출처: 테스트
            37.4130	127.0950	가초등학교
            37.4300	127.1000	나유치원	20	200
            bad line
        """.trimIndent()
        val idx = SchoolZoneIndex.parse(text)
        assertEquals(2, idx.zones.size)
        assertEquals(30, idx.zones[0].limitKmh)
        assertEquals(300, idx.zones[0].radiusM)
        assertEquals(20, idx.zones[1].limitKmh)
        assertEquals(200, idx.zones[1].radiusM)
        assertEquals("가초등학교", idx.nearestWithin(baseLat, baseLon)?.first?.name)
        // 400m 북쪽은 어느 반경에도 안 듦
        assertNull(idx.nearestWithin(37.4130 + 400 / M_PER_DEG_LAT, 127.0950))
        // 격자 경계를 넘어도 찾는다
        val edge = SchoolZoneIndex(listOf(SchoolZone("e", "경계", 37.99999, 127.0)))
        assertNotNull(edge.nearestWithin(38.00010, 127.0))
    }

    @Test
    fun `maxspeed parsing`() {
        assertEquals(50, RoadSpeedLimits.parseMaxspeed("50"))
        assertEquals(60, RoadSpeedLimits.parseMaxspeed("60 km/h"))
        assertEquals(48, RoadSpeedLimits.parseMaxspeed("30 mph"))
        assertNull(RoadSpeedLimits.parseMaxspeed("KR:urban"))
        assertNull(RoadSpeedLimits.parseMaxspeed("none"))
        assertNull(RoadSpeedLimits.parseMaxspeed(null))
    }

    private val overpass = """
        {"elements":[
          {"type":"way","id":1,"tags":{"highway":"primary","maxspeed":"60"},
           "geometry":[{"lat":37.4120,"lon":127.0940},{"lat":37.4140,"lon":127.0940}]},
          {"type":"way","id":2,"tags":{"highway":"residential","maxspeed":"30","oneway":"yes"},
           "geometry":[{"lat":37.4130,"lon":127.0935},{"lat":37.4130,"lon":127.0945}]},
          {"type":"way","id":3,"tags":{"highway":"service","maxspeed":"KR:urban"},
           "geometry":[{"lat":37.4130,"lon":127.0935},{"lat":37.4130,"lon":127.0945}]},
          {"type":"node","id":4}
        ]}
    """.trimIndent()

    @Test
    fun `overpass ways are parsed and matched by distance and heading`() {
        val ways = RoadSpeedLimits.parseOverpassWays(overpass)
        assertEquals(listOf(1L, 2L), ways.map { it.id })
        // 남북 도로 위에서 북쪽으로 가면 60
        assertEquals(60, RoadSpeedLimits.match(37.4125, 127.0940, 0.0, ways)?.limitKmh)
        // 두 도로가 만나는 곳(37.4130,127.0940)에서 동쪽으로 가면 방향이 맞는 30 도로
        assertEquals(30, RoadSpeedLimits.match(37.4130, 127.09402, 90.0, ways)?.limitKmh)
        // 같은 교차점에서 북쪽으로 가면 남북 도로
        assertEquals(60, RoadSpeedLimits.match(37.4130, 127.09402, 0.0, ways)?.limitKmh)
        // 일방통행(동쪽)을 거슬러 서쪽으로 가는 방향이면, 방향이 맞는 도로가 없어 모름
        assertNull(RoadSpeedLimits.match(37.4130, 127.0943, 270.0, ways))
        // 위를 지나는 남북 도로 아래를 동서로 지나도(내 도로에 maxspeed 없음) 남북 도로 값을 쓰지 않는다
        assertNull(RoadSpeedLimits.match(37.4125, 127.09401, 90.0, ways))
        // 25m보다 멀면 모름
        assertNull(RoadSpeedLimits.match(37.4125, 127.0950, null, ways))
        assertTrue(RoadSpeedLimits.parseOverpassWays("not json").isEmpty())
    }

    @Test
    fun `overpass query covers a cell with margin`() {
        val cell = RoadSpeedLimits.cellOf(37.4127, 127.0947)
        assertEquals(7482 to 25418, cell)
        val bbox = RoadSpeedLimits.bboxOf(cell)
        assertTrue(bbox[0] < 37.4127 && bbox[2] > 37.4127 && bbox[1] < 127.0947 && bbox[3] > 127.0947)
        assertEquals(
            "[out:json][timeout:8];way[highway][maxspeed](37.40950,127.08950,37.41550,127.09550);out tags geom;",
            RoadSpeedLimits.overpassQuery(bbox),
        )
    }
}
