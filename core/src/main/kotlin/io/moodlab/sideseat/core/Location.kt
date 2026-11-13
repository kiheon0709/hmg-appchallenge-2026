package io.moodlab.sideseat.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// 위치 연계 코치(명세 6.4): 좌표로 속도·방향, 어린이 보호구역 진입, 도로 제한속도.

/** 지구 위 거리·방위. */
object GeoMath {
    private const val R = 6_371_000.0

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * R * kotlin.math.asin(kotlin.math.sqrt(a))
    }

    /** 북쪽 0도, 시계 방향. */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** 두 방위의 차이 0~180. */
    fun angleDiff(a: Double, b: Double): Double {
        val d = abs(a - b) % 360
        return if (d > 180) 360 - d else d
    }

    /** 점에서 선분까지 거리(m). 짧은 거리라 평면 근사. */
    fun distanceToSegmentMeters(lat: Double, lon: Double, aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val kx = 111_320.0 * cos(Math.toRadians(lat))
        val ky = 110_540.0
        val px = (lon - aLon) * kx
        val py = (lat - aLat) * ky
        val dx = (bLon - aLon) * kx
        val dy = (bLat - aLat) * ky
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((px * dx + py * dy) / len2).coerceIn(0.0, 1.0)
        val ex = px - t * dx
        val ey = py - t * dy
        return kotlin.math.sqrt(ex * ex + ey * ey)
    }
}

/**
 * 좌표 열에서 속도와 진행 방향을 계산한다. FusedLocation에는 속도 필드가 없고 heading은 늘 0이라(실측) 좌표 차이로 구한다.
 * 튀는 값을 막으려고 최근 [windowMs] 동안 구간 속도의 중앙값을 쓴다.
 */
class LocationTrack(
    private val windowMs: Long = 3_000,
    /** 이보다 촘촘한 점은 같은 점으로 본다. */
    private val minIntervalMs: Long = 200,
    /** 이보다 멀리 떨어진 두 점 사이는 끊긴 것으로 본다. */
    private val maxGapMs: Long = 5_000,
    /** 이보다 짧게 움직이면 방향을 새로 정하지 않는다(멈춤 잡음). */
    private val minHeadingMeters: Double = 5.0,
) {
    private data class Fix(val lat: Double, val lon: Double, val t: Long)
    private data class Segment(val kmh: Double, val endT: Long)

    private val fixes = ArrayDeque<Fix>()
    private val segments = ArrayDeque<Segment>()
    private var heading: Double? = null

    /** @return 새 점으로 받아들였으면 true(같은 시각 중복은 무시). */
    fun add(lat: Double, lon: Double, timeMs: Long): Boolean {
        val last = fixes.lastOrNull()
        if (last != null) {
            val dt = timeMs - last.t
            if (dt < minIntervalMs) return false
            if (dt > maxGapMs) {
                fixes.clear()
                segments.clear()
            } else {
                val d = GeoMath.distanceMeters(last.lat, last.lon, lat, lon)
                segments.addLast(Segment(d / (dt / 1000.0) * 3.6, timeMs))
            }
        }
        fixes.addLast(Fix(lat, lon, timeMs))
        while (segments.isNotEmpty() && timeMs - segments.first().endT > windowMs) segments.removeFirst()
        while (fixes.size > 1 && timeMs - fixes.first().t > windowMs) fixes.removeFirst()
        val first = fixes.first()
        if (fixes.size > 1 && GeoMath.distanceMeters(first.lat, first.lon, lat, lon) >= minHeadingMeters) {
            heading = GeoMath.bearingDeg(first.lat, first.lon, lat, lon)
        }
        return true
    }

    /** 최근 구간 속도의 중앙값(km/h). 점이 둘 미만이면 null. */
    fun speedKmh(): Float? {
        if (segments.isEmpty()) return null
        val sorted = segments.map { it.kmh }.sorted()
        val m = sorted.size / 2
        val median = if (sorted.size % 2 == 1) sorted[m] else (sorted[m - 1] + sorted[m]) / 2
        return median.toFloat()
    }

    /** 진행 방향(북 0도, 시계 방향). 아직 모르면 null. */
    fun headingDeg(): Double? = heading

    fun latest(): Pair<Double, Double>? = fixes.lastOrNull()?.let { it.lat to it.lon }

    fun clear() {
        fixes.clear()
        segments.clear()
        heading = null
    }
}

// ---------- 어린이 보호구역 ----------

/** 보호구역 하나. 표준데이터는 시설(학교·유치원 등) 좌표만 있어 반경으로 구역을 본다. */
data class SchoolZone(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val limitKmh: Int = 30,
    val radiusM: Int = 300,
)

/**
 * 격자로 묶어 둔 보호구역 목록. 진입 판정은 가장 가까운 구역 하나만 본다.
 * 파일 형식(assets/zones/school_zones.tsv): `#`으로 시작하는 줄은 설명. 나머지는 탭 구분
 * `위도  경도  이름  [제한속도]  [반경m]`.
 */
class SchoolZoneIndex(val zones: List<SchoolZone>, private val cellDeg: Double = 0.01) {
    private val grid: Map<Pair<Int, Int>, List<SchoolZone>> = zones.groupBy { cell(it.latitude, it.longitude) }

    private fun cell(lat: Double, lon: Double) = floor(lat / cellDeg).toInt() to floor(lon / cellDeg).toInt()

    /** 이 점이 반경(+[marginM]) 안에 드는 가장 가까운 구역과 거리. */
    fun nearestWithin(lat: Double, lon: Double, marginM: Double = 0.0): Pair<SchoolZone, Double>? {
        val (cy, cx) = cell(lat, lon)
        var best: Pair<SchoolZone, Double>? = null
        for (dy in -1..1) for (dx in -1..1) {
            grid[(cy + dy) to (cx + dx)]?.forEach { z ->
                val d = GeoMath.distanceMeters(lat, lon, z.latitude, z.longitude)
                if (d <= z.radiusM + marginM && (best == null || d < best!!.second)) best = z to d
            }
        }
        return best
    }

    companion object {
        val EMPTY = SchoolZoneIndex(emptyList())

        fun parse(text: String, defaultRadiusM: Int = 300, defaultLimitKmh: Int = 30): SchoolZoneIndex {
            val zones = text.lineSequence().mapIndexedNotNull { i, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@mapIndexedNotNull null
                val f = line.split('\t')
                val lat = f.getOrNull(0)?.toDoubleOrNull() ?: return@mapIndexedNotNull null
                val lon = f.getOrNull(1)?.toDoubleOrNull() ?: return@mapIndexedNotNull null
                SchoolZone(
                    id = "z$i",
                    name = f.getOrNull(2)?.trim().orEmpty(),
                    latitude = lat,
                    longitude = lon,
                    limitKmh = f.getOrNull(3)?.toIntOrNull()?.takeIf { it in 10..60 } ?: defaultLimitKmh,
                    radiusM = f.getOrNull(4)?.toIntOrNull()?.takeIf { it in 30..1_000 } ?: defaultRadiusM,
                )
            }.toList()
            return SchoolZoneIndex(zones)
        }
    }
}

// ---------- 도로 제한속도(OpenStreetMap maxspeed) ----------

/** maxspeed가 있는 도로 하나. */
data class RoadWay(val id: Long, val limitKmh: Int, val oneway: Boolean, val points: List<Pair<Double, Double>>)

/**
 * 캐시해 둔 도로 중 현재 좌표에서 가장 가까운 도로의 제한속도를 고른다.
 * 진행 방향을 알면 방향이 맞는 도로만 고른다(교차·고가 도로 오인 방지).
 */
object RoadSpeedLimits {
    /** 이보다 먼 도로는 내 도로가 아니다. */
    const val MAX_MATCH_M = 25.0
    /** 진행 방향과 도로 방향 차이가 이 안이어야 같은 도로. 나들목에서 비스듬히 만나는 길을 빼려고 좁게 둔다. */
    private const val ALIGN_DEG = 30.0

    fun match(lat: Double, lon: Double, headingDeg: Double?, ways: List<RoadWay>, maxMatchM: Double = MAX_MATCH_M): RoadWay? {
        var best: RoadWay? = null
        var bestScore = Double.MAX_VALUE
        for (w in ways) {
            for (i in 0 until w.points.size - 1) {
                val (aLat, aLon) = w.points[i]
                val (bLat, bLon) = w.points[i + 1]
                val d = GeoMath.distanceToSegmentMeters(lat, lon, aLat, aLon, bLat, bLon)
                if (d > maxMatchM) continue
                // 진행 방향을 알면 방향이 맞지 않는 도로는 뺀다. 교차로의 다른 길, 위·아래로 지나는 고가도로를
                // 내 도로로 잘못 고르지 않게 한다(내 도로에 maxspeed가 없으면 모름으로 둔다).
                if (headingDeg != null) {
                    val seg = GeoMath.bearingDeg(aLat, aLon, bLat, bLon)
                    val diff = GeoMath.angleDiff(seg, headingDeg)
                    val aligned = diff <= ALIGN_DEG || (!w.oneway && diff >= 180 - ALIGN_DEG)
                    if (!aligned) continue
                }
                if (d < bestScore) {
                    bestScore = d
                    best = w
                }
            }
        }
        return best
    }

    /** "50", "50 km/h", "30 mph", "KR:urban" 등. 숫자가 없으면 null. */
    fun parseMaxspeed(raw: String?): Int? {
        val s = raw?.trim()?.lowercase() ?: return null
        val n = Regex("""^(\d{1,3})""").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val kmh = if ("mph" in s) Math.round(n * 1.609).toInt() else n
        return kmh.takeIf { it in 5..150 }
    }

    /**
     * Overpass `out tags geom;` 응답에서 maxspeed 도로를 꺼낸다.
     * 질의 예: `[out:json][timeout:8];way[highway][maxspeed](s,w,n,e);out tags geom;`
     */
    fun parseOverpassWays(json: String): List<RoadWay> = runCatching {
        val root = Json.parseToJsonElement(json).jsonObject
        (root["elements"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el.jsonObject
            if (o["type"]?.jsonPrimitive?.content != "way") return@mapNotNull null
            val tags = o["tags"] as? JsonObject ?: return@mapNotNull null
            val limit = parseMaxspeed(tags["maxspeed"]?.jsonPrimitive?.content) ?: return@mapNotNull null
            val geom = (o["geometry"] as? JsonArray).orEmpty().mapNotNull { p ->
                val po = p as? JsonObject ?: return@mapNotNull null
                val la = po["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val lo = po["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                la to lo
            }
            if (geom.size < 2) return@mapNotNull null
            RoadWay(
                id = o["id"]?.jsonPrimitive?.longOrNull ?: 0,
                limitKmh = limit,
                oneway = tags["oneway"]?.jsonPrimitive?.content == "yes",
                points = geom,
            )
        }
    }.getOrDefault(emptyList())

    /** 격자 칸(조회 단위). 칸이 바뀔 때만 외부에 묻는다. */
    fun cellOf(lat: Double, lon: Double, cellDeg: Double = 0.005): Pair<Int, Int> =
        floor(lat / cellDeg).toInt() to floor(lon / cellDeg).toInt()

    /** 칸의 Overpass bbox(남,서,북,동). 경계의 도로를 놓치지 않게 [marginDeg]만큼 넓힌다. */
    fun bboxOf(cell: Pair<Int, Int>, cellDeg: Double = 0.005, marginDeg: Double = 0.0005): DoubleArray {
        val s = cell.first * cellDeg - marginDeg
        val w = cell.second * cellDeg - marginDeg
        return doubleArrayOf(s, w, s + cellDeg + 2 * marginDeg, w + cellDeg + 2 * marginDeg)
    }

    fun overpassQuery(bbox: DoubleArray): String =
        "[out:json][timeout:8];way[highway][maxspeed](${bbox.joinToString(",") { "%.5f".format(java.util.Locale.ROOT, it) }});out tags geom;"
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
