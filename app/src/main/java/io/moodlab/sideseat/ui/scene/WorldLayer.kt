package io.moodlab.sideseat.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/** 색이 가운데에서 바깥으로 사라지는 빛 한 점. 반지름 1짜리 붓을 만들어 두고 크기만 바꿔 쓴다. */
internal class GlowBrush(color: Color, core: Float = 0.12f) {
    val brush: Brush = Brush.radialGradient(
        0f to color,
        core to color.copy(alpha = 0.85f),
        core + (1f - core) * 0.3f to color.copy(alpha = 0.32f),
        core + (1f - core) * 0.65f to color.copy(alpha = 0.08f),
        1f to color.copy(alpha = 0f),
        center = Offset.Zero, radius = 1f,
    )
}

internal fun DrawScope.glow(g: GlowBrush, x: Float, y: Float, rx: Float, ry: Float = rx, alpha: Float = 1f) {
    if (alpha <= 0.004f || rx <= 0.3f) return
    // translate·scale은 인라인이라 프레임마다 람다 객체가 생기지 않는다
    translate(x, y) {
        scale(rx, ry, Offset.Zero) {
            drawCircle(g.brush, radius = 1f, center = Offset.Zero, alpha = alpha.coerceAtMost(1f), blendMode = BlendMode.Plus)
        }
    }
}

internal fun mix(a: Color, b: Color, f: Float): Color {
    val t = f.coerceIn(0f, 1f)
    return Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha + (b.alpha - a.alpha) * t)
}

internal fun smooth(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** 앞유리 밖 세상: 하늘, 먼 배경, 도로, 차, 가로등, 터널, 주차장, 안개, 빗줄기. */
internal class WorldLayer {
    private val zs = FloatArray(N) { Z0 * RATIO.pow(it) }
    private val lat = FloatArray(N)
    private val ys = FloatArray(N)
    private val sy = FloatArray(N)
    private val kk = FloatArray(N)
    private val lnRatio = ln(RATIO)

    private var w = 0f
    private var h = 0f
    private var f = 0f
    private var cx = 0f
    private var horizon = 0f
    private var playerX = 0f

    // project() 결과
    private var px = 0f
    private var py = 0f
    private var pk = 0f

    private val path = Path()
    private val path2 = Path()

    private val warm = GlowBrush(Color(0xFFFFB866))
    private val white = GlowBrush(Color(0xFFEAF2FF))
    private val red = GlowBrush(Color(0xFFFF2A3C))
    private val amber = GlowBrush(Color(0xFFFF9A2E))
    private val magenta = GlowBrush(Color(0xFFE0409A), core = 0.02f)
    private val cyan = GlowBrush(Color(0xFF53D8FF))
    private val fogBloom = GlowBrush(Color(0xFFEAF2FF), core = 0.0f)
    private val beam = GlowBrush(Color(0xFFFFF1D0), core = 0.05f)

    private var skyNight: Brush? = null
    private var skyDay: Brush? = null
    private var roadNight: Brush? = null
    private var roadSheen: Brush? = null
    private var lineNear: Brush? = null
    private var lineFar: Brush? = null
    private var fogGrad: Brush? = null
    private var wallBrush: Brush? = null
    private var floorBrush: Brush? = null
    private var fogTintColor = Color.Unspecified
    private var fogTint: androidx.compose.ui.graphics.ColorFilter? = null
    private var tile = 1f
    private val mountainFar = Path()
    private val mountainNear = Path()
    private val skyline = Path()
    private var windowsWarm: List<Offset> = emptyList()
    private var windowsCool: List<Offset> = emptyList()
    private var stars: List<Offset> = emptyList()
    private var starsBig: List<Offset> = emptyList()
    private val bolt = Path()
    private var boltSeed = -1

    private fun ensure(size: Size) {
        if (size.width == w && size.height == h) return
        w = size.width; h = size.height
        f = minOf(h * 0.8f, w * 0.62f)
        cx = w * 0.5f
        horizon = h * SceneSim.VANISH_Y
        skyNight = Brush.verticalGradient(
            0f to Color(0xFF04050D), 0.5f to Color(0xFF0B0E2A), 0.82f to Color(0xFF241544), 1f to Color(0xFF572257),
            startY = 0f, endY = horizon,
        )
        skyDay = Brush.verticalGradient(0f to Color(0xFF5F81AA), 1f to Color(0xFFC6D3DF), startY = 0f, endY = horizon)
        roadNight = Brush.verticalGradient(0f to Color(0xFF0B0E16), 0.25f to Color(0xFF10141E), 1f to Color(0xFF1A202C), startY = horizon, endY = h)
        roadSheen = Brush.verticalGradient(
            0f to Color(0xAAB03A8C), 0.1f to Color(0x553A3F8A), 0.5f to Color(0x00000000), startY = horizon, endY = h,
        )
        lineNear = Brush.verticalGradient(0f to Color(0x00FFFFFF), 0.18f to Color(0x22FFFFFF), 0.5f to Color(0xFFFFFFFF), startY = horizon, endY = h)
        lineFar = Brush.verticalGradient(0f to Color(0x55FFFFFF), 0.2f to Color(0xCCFFFFFF), 1f to Color(0xFFFFFFFF), startY = horizon, endY = h)
        fogGrad = Brush.verticalGradient(0f to Color(0xFFFFFFFF), 0.38f to Color(0xFFFFFFFF), 1f to Color(0x00FFFFFF), startY = 0f, endY = h)
        wallBrush = Brush.verticalGradient(0f to Color(0xFF232A36), 1f to Color(0xFF12161E), startY = 0f, endY = h)
        floorBrush = Brush.verticalGradient(0f to Color(0xFF151921), 1f to Color(0xFF242A35), startY = h * 0.6f, endY = h)
        buildBackdrop()
    }

    private fun buildBackdrop() {
        tile = w * 3f
        var seed = 77
        fun r(): Float { seed = seed * 1103515245 + 12345; return ((seed ushr 8) and 0xFFFF) / 65535f }
        val twoPi = 2f * PI.toFloat()
        fun ridge(p: Path, amp: Float, base: Float, a: Int, b: Int, c: Int, ph: Float) {
            p.rewind(); p.moveTo(0f, horizon + 2f)
            val steps = 90
            for (i in 0..steps) {
                val u = i / steps.toFloat()
                val y = base + amp * (0.55f * sin(u * twoPi * a + ph) + 0.3f * sin(u * twoPi * b + ph * 2f) + 0.15f * sin(u * twoPi * c))
                p.lineTo(u * tile, horizon - maxOf(0f, y))
            }
            p.lineTo(tile, horizon + 2f); p.close()
        }
        ridge(mountainFar, h * 0.10f, h * 0.085f, 3, 7, 13, 0.4f)
        ridge(mountainNear, h * 0.045f, h * 0.035f, 5, 11, 23, 2.1f)

        skyline.rewind()
        val warmPts = ArrayList<Offset>(); val coolPts = ArrayList<Offset>()
        var x = tile * 0.08f
        val unit = h * 0.008f
        while (x < tile * 0.62f) {
            val bw = unit * (4f + r() * 7f)
            val bh = unit * (3f + r() * r() * 16f)
            if (r() > 0.25f) {
                skyline.addRect(androidx.compose.ui.geometry.Rect(x, horizon - bh, x + bw, horizon + 1f))
                var wy = horizon - bh + unit
                while (wy < horizon - unit) {
                    var wx = x + unit * 0.8f
                    while (wx < x + bw - unit * 0.6f) {
                        val p = r()
                        if (p > 0.62f) (if (p > 0.9f) coolPts else warmPts).add(Offset(wx, wy))
                        wx += unit * 1.3f
                    }
                    wy += unit * 1.5f
                }
            }
            x += bw + unit * r() * 3f
        }
        windowsWarm = warmPts; windowsCool = coolPts
        val s1 = ArrayList<Offset>(); val s2 = ArrayList<Offset>()
        repeat(110) { (if (r() > 0.85f) s2 else s1).add(Offset(r() * w, r() * r() * horizon * 0.95f)) }
        stars = s1; starsBig = s2
    }

    private fun project(z: Float, xWorld: Float, yWorld: Float = 0f) {
        val zc = z.coerceIn(Z0, zs[N - 1])
        val t = ln(zc / Z0) / lnRatio
        val i = t.toInt().coerceIn(0, N - 2)
        val fr = t - i
        val l = lat[i] + (lat[i + 1] - lat[i]) * fr
        val y = ys[i] + (ys[i + 1] - ys[i]) * fr
        pk = f / zc
        px = cx + (l + xWorld - playerX) * pk
        py = horizon - (y + yWorld - CAM_H) * pk
    }

    private fun sx(i: Int, xWorld: Float): Float = cx + (lat[i] + xWorld - playerX) * kk[i]

    /** 도로를 따라가는 띠(세로 면이면 y0·y1을 다르게, 바닥 띠면 x0·x1을 다르게). */
    private fun strip(p: Path, x0: Float, y0: Float, x1: Float, y1: Float, from: Int = 0, to: Int = N - 1) {
        p.rewind()
        p.moveTo(sx(from, x0), sy[from] - y0 * kk[from])
        for (i in from + 1..to) p.lineTo(sx(i, x0), sy[i] - y0 * kk[i])
        for (i in to downTo from) p.lineTo(sx(i, x1), sy[i] - y1 * kk[i])
        p.close()
    }

    fun draw(scope: DrawScope, sim: SceneSim) = with(scope) {
        ensure(size)
        playerX = sim.playerX
        // 흔들림: 속도에 비례한 아주 작은 떨림
        val shake = sin(sim.t * 31f) * sim.speed * 0.012f
        horizon = h * SceneSim.VANISH_Y + shake
        computeRoad(sim)

        val night = sim.night
        val tunnelDark = if (sim.tunnelEntering) sim.tunnelP * sim.tunnelP else sim.tunnelP
        val ambient = (0.32f + 0.68f * (1f - night)) * (1f - 0.75f * tunnelDark)
        val wetRef = 0.4f + 0.6f * sim.rain

        if (sim.parking > 0.5f) {
            drawParking(sim)
        } else {
            drawSky(sim)
            drawGroundAndRoad(sim, ambient)
            drawHeadlightPool(sim)
            drawRoadside(sim, ambient, wetRef)
            drawTraffic(sim, wetRef)
            if (sim.tunnelP > 0f) drawTunnel(sim)
            drawLead(sim, wetRef)
        }
        val cut = 1f - abs(2f * sim.parking - 1f)
        if (cut > 0.01f) drawRect(Color.Black, alpha = cut)

        drawFogAndGlare(sim)
        drawStreaks(sim)
    }

    private fun computeRoad(sim: SceneSim) {
        var dxA = 0f; var x = 0f; var prev = 0f
        val h0 = SceneSim.hill(sim.camS)
        val slope = (SceneSim.hill(sim.camS + 6f) - h0) / 6f
        for (i in 0 until N) {
            val z = zs[i]; val dz = z - prev
            val c = SceneSim.curvature(sim.camS + (prev + z) * 0.5f)
            x += dxA * dz + 0.5f * c * dz * dz
            dxA += c * dz
            lat[i] = x
            ys[i] = SceneSim.hill(sim.camS + z) - h0 - slope * z
            kk[i] = f / z
            sy[i] = horizon - (ys[i] - CAM_H) * kk[i]
            prev = z
        }
    }

    private fun DrawScope.drawSky(sim: SceneSim) {
        val night = sim.night
        drawRect(skyNight!!, size = Size(w, horizon + 2f))
        if (night < 0.99f) drawRect(skyDay!!, size = Size(w, horizon + 2f), alpha = 1f - night)
        val clear = (1f - sim.rain * 0.9f) * (1f - sim.fogOut) * night
        if (clear > 0.02f) {
            val tw = 0.75f + 0.25f * sin(sim.t * 1.7f)
            drawPoints(stars, PointMode.Points, Color.White, strokeWidth = h * 0.0022f, cap = StrokeCap.Round, alpha = clear * 0.7f)
            drawPoints(starsBig, PointMode.Points, Color(0xFFCFE4FF), strokeWidth = h * 0.004f, cap = StrokeCap.Round, alpha = clear * tw)
            val mx = w * 0.74f; val my = h * 0.12f
            glow(white, mx, my, h * 0.11f, alpha = clear * 0.35f)
            drawCircle(Color(0xFFF3F0E2), h * 0.022f, Offset(mx, my), alpha = clear)
            drawCircle(Color(0xFF0B0E2A), h * 0.02f, Offset(mx + h * 0.011f, my - h * 0.004f), alpha = clear * 0.92f)
        }
        // 지평선의 도시 불빛
        glow(magenta, cx, horizon, w * 0.75f, h * 0.2f, alpha = night * 0.55f * (1f - sim.fogOut))
        val shift = ((sim.heading * f) % tile + tile) % tile
        var x0 = -shift
        while (x0 < w) {
            translate(x0, 0f) {
                drawPath(mountainFar, mix(Color(0xFF16122E), Color(0xFF7D93AB), 1f - night))
                drawPath(skyline, mix(Color(0xFF0A0B18), Color(0xFF6B7F96), 1f - night))
                if (night > 0.3f) {
                    drawPoints(windowsWarm, PointMode.Points, Color(0xFFFFC978), strokeWidth = h * 0.0042f, alpha = night * 0.85f)
                    drawPoints(windowsCool, PointMode.Points, Color(0xFF7FE3FF), strokeWidth = h * 0.0042f, alpha = night * 0.9f)
                }
                drawPath(mountainNear, mix(Color(0xFF080914), Color(0xFF4F6A5C), 1f - night))
            }
            x0 += tile
        }
        if (sim.rain > 0.05f) drawRect(Color(0xFF05070C), size = Size(w, horizon + 2f), alpha = sim.rain * 0.45f)
        if (sim.flash > 0.01f) {
            if (boltSeed != sim.boltSeed) {
                boltSeed = sim.boltSeed
                bolt.rewind()
                var bx = w * (0.15f + 0.7f * ((boltSeed * 37 % 100) / 100f)); var by = 0f
                bolt.moveTo(bx, by)
                var k = boltSeed * 7919
                while (by < horizon * 0.92f) {
                    k = k * 1103515245 + 12345
                    bx += (((k ushr 8) and 0xFF) / 255f - 0.5f) * h * 0.09f
                    by += h * 0.045f
                    bolt.lineTo(bx, by)
                }
            }
            drawRect(Color(0xFFCFD8FF), size = Size(w, horizon + 2f), alpha = sim.flash * 0.42f)
            drawPath(bolt, Color.White, alpha = sim.flash, style = Stroke(h * 0.004f))
            drawPath(bolt, Color(0xFF9FB6FF), alpha = sim.flash * 0.5f, style = Stroke(h * 0.012f))
        }
    }

    private fun DrawScope.drawGroundAndRoad(sim: SceneSim, ambient: Float) {
        val night = sim.night
        var top = horizon
        for (i in 0 until N) if (sy[i] < top) top = sy[i]
        drawRect(mix(Color(0xFF06080D), Color(0xFF55664F), 1f - night), topLeft = Offset(0f, top - 1f), size = Size(w, h - top + 1f))
        if (sim.flash > 0.01f) drawRect(Color(0xFF8E9BD0), topLeft = Offset(0f, top), size = Size(w, h - top), alpha = sim.flash * 0.12f)

        strip(path, -ROAD_HALF, 0f, ROAD_HALF, 0f)
        drawPath(path, roadNight!!)
        if (night < 0.99f) drawPath(path, Color(0xFF596070), alpha = (1f - night) * 0.9f)
        drawPath(path, roadSheen!!, alpha = night * (0.5f + 0.5f * sim.rain) * (1f - sim.fogOut))

        // 가장자리 실선 (왼쪽 노랑, 오른쪽 흰색)
        val base = ambient * 0.55f
        val low = sim.lowBeam * 0.75f
        val high = sim.highBeam * 0.5f
        strip(path, EDGE - 0.09f, 0f, EDGE + 0.09f, 0f)
        strip(path2, -EDGE - 0.09f, 0f, -EDGE + 0.09f, 0f)
        if (base + high > 0.01f) {
            drawPath(path, lineFar!!, alpha = (base + high).coerceAtMost(1f))
            drawPath(path2, lineFar!!, alpha = (base + high).coerceAtMost(1f), colorFilter = YELLOW)
        }
        if (low > 0.01f) {
            drawPath(path, lineNear!!, alpha = low)
            drawPath(path2, lineNear!!, alpha = low, colorFilter = YELLOW)
        }

        // 차선 점선: 8m 선, 12m 간격
        val first = floor(sim.camS / 20f) * 20f
        for (j in 13 downTo -1) {
            val z0 = first + j * 20f - sim.camS
            val z1 = z0 + 8f
            if (z1 <= Z0 + 0.2f) continue
            val za = maxOf(z0, Z0)
            val a = (ambient * 0.5f + sim.lowBeam * 0.8f * smooth(95f, 10f, za) + sim.highBeam * 0.55f * smooth(190f, 20f, za)) * (1f - za / 270f)
            if (a <= 0.01f) continue
            for (side in -1..1 step 2) {
                val lx = side * 1.8f
                path.rewind()
                project(za, lx - 0.065f); path.moveTo(px, py)
                project(za, lx + 0.065f); path.lineTo(px, py)
                project(z1, lx + 0.065f); path.lineTo(px, py)
                project(z1, lx - 0.065f); path.lineTo(px, py)
                path.close()
                drawPath(path, Color.White, alpha = a.coerceAtMost(1f))
            }
        }
    }

    private fun DrawScope.drawHeadlightPool(sim: SceneSim) {
        if (sim.lowBeam < 0.01f) return
        project(11f, 0.4f)
        val y = py
        glow(beam, cx, y + h * 0.06f, w * 0.55f, h * 0.26f, alpha = sim.lowBeam * 0.3f)
        if (sim.highBeam > 0.01f) {
            project(70f, 0f)
            glow(beam, px, py + h * 0.03f, w * 0.4f, h * 0.15f, alpha = sim.highBeam * 0.3f)
        }
        if (sim.fogLamp > 0.01f) glow(amber, cx, h * 0.8f, w * 0.5f, h * 0.12f, alpha = sim.fogLamp * 0.16f)
    }

    private fun inTube(sim: SceneSim, z: Float): Boolean {
        if (sim.tunnelP <= 0f) return false
        return if (sim.tunnelEntering) z > portalZ(sim) else z < portalZ(sim)
    }

    private fun portalZ(sim: SceneSim): Float =
        if (sim.tunnelEntering) PORTAL_FAR * (1f - sim.tunnelP).pow(1.4f) - 0.01f else PORTAL_FAR * sim.tunnelP.pow(1.4f)

    private fun DrawScope.drawRoadside(sim: SceneSim, ambient: Float, wetRef: Float) {
        val night = sim.night
        // 중앙분리대와 가드레일
        strip(path, -6.2f, 0f, -6.2f, 0.75f)
        drawPath(path, mix(Color(0xFF1A202B), Color(0xFF8C939C), 1f - night))
        strip(path, -6.2f, 0.75f, -6.2f, 0.84f)
        drawPath(path, Color(0xFF53D8FF), alpha = 0.35f * night + 0.1f)
        strip(path, 6.6f, 0.5f, 6.6f, 0.78f)
        drawPath(path, mix(Color(0xFF2A3342), Color(0xFFB4BBC4), 1f - night), alpha = 0.9f)

        // 표지판 문형
        val gFirst = floor((sim.camS - 450f) / 900f) * 900f + 450f
        for (j in 1 downTo 0) {
            val z = gFirst + j * 900f - sim.camS
            if (z < 6f || z > 330f || inTube(sim, z)) continue
            val lit = (ambient * 0.5f + sim.lowBeam * 0.5f * smooth(140f, 20f, z) + sim.highBeam * 0.4f).coerceAtMost(1f) * (1f - z / 340f)
            project(z, -7.4f); val lx = px; val gy = py; val k = pk
            project(z, 7.4f); val rx = px
            val topY = gy - 8.2f * k
            val sw = maxOf(1f, 0.3f * k)
            drawLine(Color(0xFF2B3442), Offset(lx, gy), Offset(lx, topY), sw)
            drawLine(Color(0xFF2B3442), Offset(rx, gy), Offset(rx, topY), sw)
            drawLine(Color(0xFF2B3442), Offset(lx, topY), Offset(rx, topY), sw)
            for (b in 0..1) {
                project(z, if (b == 0) -4.6f else 0.6f)
                val bx = px; val bw = 4f * k; val bh = 2.3f * k; val by = gy - 8f * k
                drawRect(Color(0xFF0C5B3C), Offset(bx, by), Size(bw, bh), alpha = 0.35f + 0.65f * lit)
                drawRect(Color.White, Offset(bx, by), Size(bw, bh), alpha = lit, style = Stroke(maxOf(1f, 0.07f * k)))
                drawRect(Color.White, Offset(bx + bw * 0.12f, by + bh * 0.25f), Size(bw * 0.5f, bh * 0.14f), alpha = lit)
                drawRect(Color.White, Offset(bx + bw * 0.12f, by + bh * 0.55f), Size(bw * 0.34f, bh * 0.14f), alpha = lit)
                // 아래 화살표
                drawLine(Color.White, Offset(bx + bw * 0.82f, by + bh * 0.2f), Offset(bx + bw * 0.82f, by + bh * 0.8f), maxOf(1f, 0.12f * k), alpha = lit)
            }
        }

        // 가로등 (45m 간격, 좌우 번갈아)
        val lFirst = floor(sim.camS / 45f)
        if (night > 0.05f) for (j in 8 downTo 0) {
            val idx = lFirst.toInt() + j
            val z = idx * 45f + 22f - sim.camS
            if (z < 4f || z > 340f || inTube(sim, z)) continue
            val side = if (idx and 1 == 0) 1f else -1f
            val far = 1f - z / 360f
            project(z, side * (if (side > 0f) 8.2f else 6.4f)); val bx = px; val by = py; val k = pk
            val topY = by - 9f * k
            project(z, side * 4.6f, 9f); val hx = px; val hy = py
            val sw = maxOf(1f, 0.16f * k)
            drawLine(Color(0xFF232B38), Offset(bx, by), Offset(bx, topY), sw)
            drawLine(Color(0xFF232B38), Offset(bx, topY), Offset(hx, hy), sw)
            // 노면에 떨어진 빛과 젖은 반사
            project(z, side * 3f)
            glow(warm, px, py, 7.5f * k, maxOf(2f, 46f * k / (z + 8f)), alpha = night * 0.22f * far)
            project(z, side * 4.6f)
            glow(warm, px, py + 3f * k, maxOf(1.5f, 0.55f * k), 4.5f * k, alpha = night * 0.4f * wetRef * far)
            glow(warm, hx, hy, maxOf(5f, 2.6f * k) * (1f + sim.fogOut * 1.5f), alpha = night * (0.6f + 0.4f * far))
            drawCircle(Color(0xFFFFE9C4), maxOf(1.2f, 0.18f * k), Offset(hx, hy), alpha = night)
        }

        // 반사 표지 (20m 간격). 전조등을 받아야 빛난다
        val pFirst = floor(sim.camS / 20f) * 20f
        for (j in 12 downTo 0) {
            val z = pFirst + j * 20f + 10f - sim.camS
            if (z < 3f || inTube(sim, z)) continue
            val lit = (0.18f * ambient + sim.lowBeam * 0.8f * smooth(110f, 10f, z) + sim.highBeam * 0.6f * smooth(260f, 30f, z)).coerceAtMost(1f)
            if (lit < 0.02f) continue
            project(z, 6.45f, 0.9f)
            val r = maxOf(1.2f, 0.07f * pk)
            drawCircle(Color.White, r, Offset(px, py), alpha = lit)
            project(z, -6.2f, 0.95f)
            drawCircle(Color(0xFFFFB02E), r, Offset(px, py), alpha = lit)
        }
    }

    private fun DrawScope.drawCar(x: Float, groundY: Float, k: Float, braking: Float, alpha: Float, wetRef: Float, body: Color) {
        val cw = 1.84f * k; val ch = 1.42f * k
        val left = x - cw / 2f; val top = groundY - ch
        val tl = maxOf(1.5f, 0.2f * k)
        if (k > 7f) {
            // 그림자, 차체, 뒷유리, 번호판
            drawOval(Color.Black, Offset(left - cw * 0.05f, groundY - ch * 0.07f), Size(cw * 1.1f, ch * 0.16f), alpha = 0.55f * alpha)
            path.rewind()
            path.moveTo(left + cw * 0.04f, groundY - ch * 0.1f)
            path.lineTo(left, groundY - ch * 0.5f)
            path.lineTo(left + cw * 0.05f, groundY - ch * 0.66f)
            path.lineTo(left + cw * 0.2f, top)
            path.lineTo(left + cw * 0.8f, top)
            path.lineTo(left + cw * 0.95f, groundY - ch * 0.66f)
            path.lineTo(left + cw, groundY - ch * 0.5f)
            path.lineTo(left + cw * 0.96f, groundY - ch * 0.1f)
            path.close()
            drawPath(path, body, alpha = alpha)
            path.rewind()
            path.moveTo(left + cw * 0.13f, groundY - ch * 0.66f)
            path.lineTo(left + cw * 0.24f, top + ch * 0.06f)
            path.lineTo(left + cw * 0.76f, top + ch * 0.06f)
            path.lineTo(left + cw * 0.87f, groundY - ch * 0.66f)
            path.close()
            drawPath(path, Color(0xFF0A1424), alpha = alpha)
            drawLine(Color(0x5553D8FF), Offset(left + cw * 0.24f, top + ch * 0.06f), Offset(left + cw * 0.76f, top + ch * 0.06f), maxOf(1f, 0.03f * k), alpha = alpha)
            drawRect(Color(0xFFDCE2E8), Offset(x - cw * 0.14f, groundY - ch * 0.36f), Size(cw * 0.28f, ch * 0.1f), alpha = 0.55f * alpha)
            drawRect(Color(0xFF07090D), Offset(left + cw * 0.06f, groundY - ch * 0.12f), Size(cw * 0.16f, ch * 0.12f), alpha = alpha)
            drawRect(Color(0xFF07090D), Offset(left + cw * 0.78f, groundY - ch * 0.12f), Size(cw * 0.16f, ch * 0.12f), alpha = alpha)
            // 미등 띠
            val ly = groundY - ch * 0.56f
            val on = 0.55f + 0.45f * braking
            drawRect(Color(0xFFFF2038), Offset(left + cw * 0.03f, ly), Size(cw * 0.26f, ch * 0.09f), alpha = alpha * on)
            drawRect(Color(0xFFFF2038), Offset(left + cw * 0.71f, ly), Size(cw * 0.26f, ch * 0.09f), alpha = alpha * on)
            drawRect(Color(0xFFFF2038), Offset(left + cw * 0.29f, ly + ch * 0.03f), Size(cw * 0.42f, ch * 0.025f), alpha = alpha * (0.35f + 0.65f * braking))
            if (braking > 0.05f) drawRect(Color(0xFFFF4A5A), Offset(x - cw * 0.16f, top + ch * 0.02f), Size(cw * 0.32f, ch * 0.03f), alpha = alpha * braking)
        }
        val ly = groundY - ch * 0.52f
        val gr = tl * (2.2f + 5.5f * braking)
        val ga = alpha * (0.5f + 0.5f * braking)
        glow(red, left + cw * 0.14f, ly, gr, alpha = ga)
        glow(red, left + cw * 0.86f, ly, gr, alpha = ga)
        // 젖은 노면 반사
        val ra = alpha * wetRef * (0.3f + 0.5f * braking)
        glow(red, left + cw * 0.14f, groundY + ch * 0.75f, tl * 1.6f, ch * 0.95f, alpha = ra)
        glow(red, left + cw * 0.86f, groundY + ch * 0.75f, tl * 1.6f, ch * 0.95f, alpha = ra)
    }

    private fun DrawScope.drawTraffic(sim: SceneSim, wetRef: Float) {
        // 맞은편 차로의 전조등
        for (j in 0 until SceneSim.ONCOMING) {
            val z = sim.onZ[j]
            if (z < 6f || z > 340f || inTube(sim, z)) continue
            val far = 1f - z / 380f
            for (s in 0..1) {
                project(z, -10.6f + (if (s == 0) -0.72f else 0.72f), 0.68f)
                val r = maxOf(2f, 0.5f * pk)
                glow(white, px, py, r * (1f + sim.fogOut * 2f), alpha = (0.5f + 0.5f * far) * sim.night.coerceAtLeast(0.5f))
                glow(white, px, py + 1.6f * pk, r * 0.5f, 1.8f * pk, alpha = 0.22f * wetRef * far)
            }
        }
        // 맞은편 눈부심
        if (sim.glare > 0.01f) {
            val z = 260f - 232f * sim.glare.pow(0.8f)
            project(z, -9.8f, 0.7f)
            val gx = px.coerceIn(w * 0.08f, w * 0.92f); val gy = py
            val r = w * (0.025f + 0.2f * sim.glare)
            val sep = maxOf(0.75f * pk, r * 0.28f)
            glow(white, gx - sep, gy, r, alpha = sim.glare)
            glow(white, gx + sep, gy, r, alpha = sim.glare)
            glow(cyan, gx, gy, r * 5f, r * 0.1f, alpha = sim.glare * 0.8f)
            glow(fogBloom, gx, gy, r * 3.2f, r * 2.2f, alpha = sim.glare * 0.5f)
        }
        // 옆 차로 차들
        for (j in 0 until SceneSim.AMBIENT_CARS) {
            val z = sim.carZ[j]
            if (z < 2.2f || z > 330f) continue
            project(z, sim.carLane[j] * 3.6f)
            drawCar(px, py, pk, 0f, (1f - z / 340f).coerceIn(0f, 1f), wetRef, if (j % 2 == 0) Color(0xFF151A24) else Color(0xFF1D1622))
        }
    }

    private fun DrawScope.drawLead(sim: SceneSim, wetRef: Float) {
        if (sim.leadA < 0.02f) return
        val z = sim.leadZ.coerceIn(3.2f, 330f)
        project(z, 0f)
        drawCar(px, py, pk, sim.leadBrake, sim.leadA, wetRef, Color(0xFF1A2130))
    }

    private fun DrawScope.drawTunnel(sim: SceneSim) {
        val pz = portalZ(sim)
        val entering = sim.tunnelEntering
        val zNear = if (entering) maxOf(pz, Z0) else Z0
        val zFar = if (entering) TUBE_FAR else minOf(pz, TUBE_FAR)
        if (zFar <= zNear + 0.5f) return
        val lit = sim.lowBeam

        if (entering && pz > Z0) {
            // 입구가 있는 산 벽
            project(pz, -TUBE_HALF); val lx = px; val gy = py; val k = pk
            project(pz, TUBE_HALF); val rx = px
            val topY = gy - TUBE_H * k
            val mid = (lx + rx) * 0.5f
            path.rewind()
            path.fillType = PathFillType.EvenOdd
            path.moveTo(mid - 75f * k, gy + 1f)
            path.cubicTo(mid - 36f * k, gy - 3f * k, mid - 24f * k, gy - 25f * k, mid, gy - 25f * k)
            path.cubicTo(mid + 24f * k, gy - 25f * k, mid + 36f * k, gy - 3f * k, mid + 75f * k, gy + 1f)
            path.close()
            val rr = androidx.compose.ui.geometry.CornerRadius(0.9f * k, 0.9f * k)
            path.addRoundRect(androidx.compose.ui.geometry.RoundRect(lx, topY, rx, gy + 2f, rr))
            drawPath(path, mix(Color(0xFF090B15), Color(0xFF4A6355), 1f - sim.night))
            path.fillType = PathFillType.NonZero
            // 콘크리트 입구 테두리
            drawRoundRect(
                mix(Color(0xFF2C3440), Color(0xFFA3ABB5), 1f - sim.night), Offset(lx - 0.6f * k, topY - 0.6f * k),
                Size(rx - lx + 1.2f * k, gy - topY + 0.6f * k), rr, style = Stroke(1.3f * k),
            )
            drawRoundRect(Color(0xFFFFB866), Offset(lx, topY), Size(rx - lx, gy - topY), rr, style = Stroke(maxOf(1.5f, 0.14f * k)), alpha = 0.7f)
            clipRect(lx, topY, rx, gy + 2f) { drawTube(sim, zNear, zFar, lit, true) }
        } else {
            drawTube(sim, zNear, zFar, lit, entering)
        }
    }

    private fun DrawScope.drawTube(sim: SceneSim, zNear: Float, zFar: Float, lit: Float, capFar: Boolean) {
        if (capFar) {
            project(zFar, -TUBE_HALF); val lx = px; val gy = py
            project(zFar, TUBE_HALF)
            drawRect(Color(0xFF05070B), Offset(lx, gy - TUBE_H * pk), Size(px - lx, TUBE_H * pk))
        }
        val first = floor((sim.camS + zFar) / RING) * RING - sim.camS
        var zb = zFar
        var ring = floor((sim.camS + zFar) / RING).toInt()
        var za = first
        while (zb > zNear) {
            if (za < zNear) za = zNear
            if (zb - za > 0.05f) {
                val mid = (za + zb) * 0.5f
                val b = ((if (ring and 1 == 0) 0.88f else 1.1f) *
                    (0.2f + 0.55f * lit * smooth(95f, 6f, mid) + 0.2f * sim.highBeam) * (1f - mid / (TUBE_FAR * 1.15f))).coerceIn(0f, 1f)
                val dark = Color(0xFF05070B)
                val wallC = mix(dark, Color(0xFF4C5766), b)
                val ceilC = mix(dark, Color(0xFF2B323D), b)
                val bandC = mix(dark, Color(0xFF98A2AE), b)
                project(za, -TUBE_HALF); val ax = px; val ay = py; val ak = pk
                project(za, TUBE_HALF); val bx = px
                project(zb, -TUBE_HALF); val cxl = px; val cy = py; val ck = pk
                project(zb, TUBE_HALF); val dx = px
                val aTop = ay - TUBE_H * ak; val cTop = cy - TUBE_H * ck
                // 낮에는 터널 안 노면이 어두워진다
                val floorA = 0.6f * (1f - sim.night) * (1f - 0.6f * lit * smooth(70f, 6f, mid))
                if (floorA > 0.01f) {
                    path.rewind(); path.moveTo(ax, ay); path.lineTo(bx, ay); path.lineTo(dx, cy); path.lineTo(cxl, cy); path.close()
                    drawPath(path, dark, alpha = floorA)
                }
                path.rewind(); path.moveTo(ax, ay); path.lineTo(ax, aTop); path.lineTo(cxl, cTop); path.lineTo(cxl, cy); path.close()
                drawPath(path, wallC)
                path.rewind(); path.moveTo(bx, ay); path.lineTo(bx, aTop); path.lineTo(dx, cTop); path.lineTo(dx, cy); path.close()
                drawPath(path, wallC)
                path.rewind(); path.moveTo(ax, aTop); path.lineTo(bx, aTop); path.lineTo(dx, cTop); path.lineTo(cxl, cTop); path.close()
                drawPath(path, ceilC)
                path.rewind(); path.moveTo(ax, ay); path.lineTo(ax, ay - 1.1f * ak); path.lineTo(cxl, cy - 1.1f * ck); path.lineTo(cxl, cy); path.close()
                drawPath(path, bandC)
                path.rewind(); path.moveTo(bx, ay); path.lineTo(bx, ay - 1.1f * ak); path.lineTo(dx, cy - 1.1f * ck); path.lineTo(dx, cy); path.close()
                drawPath(path, bandC)
                // 천장 등 (두 구간에 하나)
                if (ring and 1 == 0 && za > zNear + 0.01f) {
                    val far = 1f - za / TUBE_FAR
                    val lw = 1.4f * ak
                    drawRect(Color(0xFFFFC27A), Offset((ax + bx) * 0.5f - 3.2f * ak - lw / 2f, aTop + 0.15f * ak), Size(lw, maxOf(1f, 0.12f * ak)), alpha = 0.5f + 0.5f * far)
                    drawRect(Color(0xFFFFC27A), Offset((ax + bx) * 0.5f + 3.2f * ak - lw / 2f, aTop + 0.15f * ak), Size(lw, maxOf(1f, 0.12f * ak)), alpha = 0.5f + 0.5f * far)
                    glow(warm, (ax + bx) * 0.5f - 3.2f * ak, aTop + 0.2f * ak, 1.6f * ak, 0.8f * ak, alpha = 0.35f * far)
                    glow(warm, (ax + bx) * 0.5f + 3.2f * ak, aTop + 0.2f * ak, 1.6f * ak, 0.8f * ak, alpha = 0.35f * far)
                }
            }
            zb = za
            za -= RING
            ring--
        }
    }

    private fun DrawScope.drawParking(sim: SceneSim) {
        val gy = h * 0.6f // 벽과 바닥이 만나는 선
        drawRect(Color(0xFF0B0D12), size = Size(w, gy))
        // 뒷벽
        drawRect(wallBrush!!, topLeft = Offset(0f, h * 0.12f), size = Size(w, gy - h * 0.12f))
        drawRect(Color(0xFFE8B23A), topLeft = Offset(0f, gy - h * 0.2f), size = Size(w, h * 0.035f), alpha = 0.85f)
        drawRect(Color(0xFF0A0C10), topLeft = Offset(0f, gy - h * 0.165f), size = Size(w, h * 0.012f), alpha = 0.7f)
        // 기둥
        for (i in 0..1) {
            val x = w * (0.16f + 0.62f * i)
            drawRect(Color(0xFF2B323E), topLeft = Offset(x, h * 0.02f), size = Size(w * 0.07f, gy - h * 0.02f))
            drawRect(Color(0xFFE8B23A), topLeft = Offset(x, gy - h * 0.2f), size = Size(w * 0.07f, h * 0.035f))
            drawRect(Color(0xFF53D8FF), topLeft = Offset(x + w * 0.02f, h * 0.2f), size = Size(w * 0.03f, h * 0.012f), alpha = 0.8f)
        }
        // 천장 등
        for (i in 0..2) {
            val x = w * (0.22f + 0.28f * i)
            drawRect(Color(0xFFEAF2FF), topLeft = Offset(x - w * 0.06f, h * 0.085f), size = Size(w * 0.12f, h * 0.008f), alpha = 0.9f)
            glow(white, x, h * 0.09f, w * 0.16f, h * 0.1f, alpha = 0.3f)
        }
        // 바닥과 주차선
        drawRect(floorBrush!!, topLeft = Offset(0f, gy), size = Size(w, h - gy))
        for (i in -2..2) {
            val xt = cx + i * w * 0.15f + w * 0.075f
            val xb = cx + (i + 0.5f) * w * 0.62f
            drawLine(Color(0xFFF2F4F7), Offset(xt, gy), Offset(xb, h), h * 0.008f, alpha = 0.75f)
        }
        drawLine(Color(0xFFF2F4F7), Offset(0f, gy + h * 0.006f), Offset(w, gy + h * 0.006f), h * 0.006f, alpha = 0.6f)
        // 벽에 비친 내 전조등
        if (sim.lowBeam > 0.01f) {
            glow(beam, cx - w * 0.13f, gy - h * 0.11f, w * 0.17f, h * 0.2f, alpha = sim.lowBeam * 0.75f)
            glow(beam, cx + w * 0.13f, gy - h * 0.11f, w * 0.17f, h * 0.2f, alpha = sim.lowBeam * 0.75f)
            glow(beam, cx, gy + h * 0.1f, w * 0.5f, h * 0.14f, alpha = sim.lowBeam * 0.3f)
        }
        if (sim.highBeam > 0.01f) glow(white, cx, gy - h * 0.2f, w * 0.5f, h * 0.3f, alpha = sim.highBeam * 0.5f)
    }

    private fun DrawScope.drawFogAndGlare(sim: SceneSim) {
        val fog = sim.fogOut
        if (fog > 0.01f) {
            val c = mix(Color(0xFF8F98A6), Color(0xFFDDE3E9), 1f - sim.night)
            val bottom = fog * (0.68f - 0.44f * sim.fogLamp - 0.1f * sim.lowBeam).coerceAtLeast(0.1f)
            val top = fog * 0.97f
            drawRect(c, alpha = bottom)
            if (c != fogTintColor) { fogTintColor = c; fogTint = androidx.compose.ui.graphics.ColorFilter.tint(c, BlendMode.Modulate) }
            drawRect(fogGrad!!, alpha = (top - bottom).coerceAtLeast(0f), colorFilter = fogTint)
            // 상향등은 안개에 부딪혀 하얗게 번진다
            if (sim.highBeam > 0.01f) {
                drawRect(Color.White, alpha = fog * sim.highBeam * 0.2f)
                glow(fogBloom, cx, horizon + h * 0.05f, w * 0.8f, h * 0.55f, alpha = fog * sim.highBeam * 0.75f)
            }
        }
        if (sim.glare > 0.01f) drawRect(Color.White, alpha = sim.glare * sim.glare * 0.16f)
    }

    private fun DrawScope.drawStreaks(sim: SceneSim) {
        val rain = sim.rain
        if (rain < 0.03f || sim.parking > 0.5f) return
        val n = (SceneSim.MAX_STREAKS * (0.25f + 0.75f * rain)).toInt()
        val rush = (sim.speed / 30f).coerceIn(0f, 1.3f)
        val a = (0.16f + 0.3f * rain) * (1f - 0.7f * sim.tunnelP)
        val sw = maxOf(1f, h * 0.0016f)
        val c = Color(0xFFBFD3F2)
        for (k in 0 until n) {
            val x = sim.streakX[k]; val y = sim.streakY[k]
            val vx = ((x - 0.5f) * 2.6f * rush + 0.06f) * sim.streakV[k]
            val vy = ((y - SceneSim.VANISH_Y) * 2.6f * rush + 1.5f) * sim.streakV[k]
            drawLine(c, Offset(x * w, y * h), Offset((x - vx * 0.045f) * w, (y - vy * 0.045f) * h), sw, alpha = a)
        }
    }

    companion object {
        const val N = 44
        const val Z0 = 1.4f
        const val Z_MAX = 340f
        val RATIO: Float = (Z_MAX / Z0).pow(1f / (N - 1))
        const val CAM_H = 1.2f
        const val ROAD_HALF = 6.2f
        const val EDGE = 5.4f
        const val TUBE_HALF = 6.35f
        const val TUBE_H = 6.4f
        const val TUBE_FAR = 230f
        const val PORTAL_FAR = 95f
        const val RING = 9f
        val YELLOW = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFFFFC94A), BlendMode.Modulate)
    }
}
