package io.moodlab.sideseat.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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

/**
 * 앞유리 밖 세상: 하늘, 먼 배경, 도로, 차, 가로등, 터널, 주차장, 안개, 빗줄기.
 * 밤길의 빛은 내 전조등, 가로등, 다른 차의 등뿐이다. 그래서 노면·차선·차체는 전조등을 받는 만큼만 보이게 칠한다.
 */
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

    private val lamp = GlowBrush(Color(0xFFFFDDB0))
    private val white = GlowBrush(Color(0xFFF2F6FF))
    private val red = GlowBrush(Color(0xFFFF2A30))
    private val amber = GlowBrush(Color(0xFFFFA63A))
    private val haze = GlowBrush(Color(0xFF7A6753), core = 0.02f)
    private val fogBloom = GlowBrush(Color(0xFFEAF0F6), core = 0.0f)
    private val beam = GlowBrush(Color(0xFFFFF4DC), core = 0.05f)

    private var skyNight: Brush? = null
    private var skyDay: Brush? = null
    private var litLow: Brush? = null
    private var litHigh: Brush? = null
    private var sideShade: Brush? = null
    private var roadShade: Brush? = null
    private var fogGrad: Brush? = null
    private var floorBrush: Brush? = null
    private var fogTintColor = Color.Unspecified
    private var fogTint: ColorFilter? = null
    private var tile = 1f
    private val ridgeFar = Path()
    private val ridgeNear = Path()
    private val skyline = Path()
    private var windowsWarm: List<Offset> = emptyList()
    private var windowsCool: List<Offset> = emptyList()
    private var stars: List<Offset> = emptyList()

    // 터널 단면(왼쪽 바닥에서 천장을 지나 오른쪽 바닥까지). 한 고리의 화면 좌표를 담아 두고 앞뒤 고리를 잇는다
    private val ringAx = FloatArray(PROF)
    private val ringAy = FloatArray(PROF)
    private val ringBx = FloatArray(PROF)
    private val ringBy = FloatArray(PROF)

    private fun ensure(size: Size) {
        if (size.width == w && size.height == h) return
        w = size.width; h = size.height
        // 초점거리. 길게 잡을수록 멀리 있는 차가 크게, 발밑 노면이 덜 벌어져 보인다
        f = minOf(h * 1.0f, w * 0.75f)
        cx = w * 0.5f
        horizon = h * SceneSim.VANISH_Y
        skyNight = Brush.verticalGradient(
            0f to Color(0xFF070A10), 0.5f to Color(0xFF0D1118), 0.84f to Color(0xFF181C23), 1f to Color(0xFF29272A),
            startY = 0f, endY = horizon,
        )
        skyDay = Brush.verticalGradient(0f to Color(0xFF8D9AA8), 0.7f to Color(0xFFB9C2CA), 1f to Color(0xFFCDD3D7), startY = 0f, endY = horizon)
        // 노면 위 거리 z가 화면에서 놓이는 높이(지평선 0, 화면 맨 아래 1)
        val span = h - horizon
        fun yAt(z: Float) = (CAM_H * f / z / span).coerceIn(0.001f, 0.999f)
        val lit = Color(0xFF5B5C5E)
        litLow = Brush.verticalGradient(
            0f to lit.copy(alpha = 0f), yAt(62f) to lit.copy(alpha = 0f), yAt(40f) to lit.copy(alpha = 0.28f),
            yAt(22f) to lit.copy(alpha = 0.8f), yAt(10f) to lit, 1f to lit.copy(alpha = 0.92f),
            startY = horizon, endY = h,
        )
        litHigh = Brush.verticalGradient(
            0f to lit.copy(alpha = 0.2f), yAt(150f) to lit.copy(alpha = 0.5f), yAt(60f) to lit.copy(alpha = 0.7f),
            yAt(25f) to lit.copy(alpha = 0.3f), 1f to lit.copy(alpha = 0f),
            startY = horizon, endY = h,
        )
        sideShade = Brush.horizontalGradient(
            0f to Color(0xB3000000), 0.3f to Color(0x00000000), 0.7f to Color(0x00000000), 1f to Color(0xB3000000),
            startX = 0f, endX = w,
        )
        roadShade = Brush.verticalGradient(0f to Color(0x00000000), 0.35f to Color(0x80000000), 1f to Color(0xA6000000), startY = horizon, endY = h)
        fogGrad = Brush.verticalGradient(0f to Color(0xFFFFFFFF), 0.38f to Color(0xFFFFFFFF), 1f to Color(0x00FFFFFF), startY = 0f, endY = h)
        floorBrush = Brush.verticalGradient(0f to Color(0xFF1B1F1F), 1f to Color(0xFF2C3231), startY = h * 0.55f, endY = h)
        buildBackdrop()
    }

    private fun buildBackdrop() {
        tile = w * 3f
        var seed = 77
        fun r(): Float { seed = seed * 1103515245 + 12345; return ((seed ushr 8) and 0xFFFF) / 65535f }
        val twoPi = 2f * PI.toFloat()
        fun ridge(p: Path, amp: Float, base: Float, a: Int, b: Int, c: Int, ph: Float) {
            p.rewind(); p.moveTo(0f, horizon + 2f)
            val steps = 120
            for (i in 0..steps) {
                val u = i / steps.toFloat()
                val y = base + amp * (0.55f * sin(u * twoPi * a + ph) + 0.3f * sin(u * twoPi * b + ph * 2f) + 0.15f * sin(u * twoPi * c))
                p.lineTo(u * tile, horizon - maxOf(0f, y))
            }
            p.lineTo(tile, horizon + 2f); p.close()
        }
        ridge(ridgeFar, h * 0.06f, h * 0.06f, 3, 7, 13, 0.4f)
        ridge(ridgeNear, h * 0.028f, h * 0.022f, 5, 11, 23, 2.1f)

        // 멀리 보이는 아파트 단지. 윤곽은 하늘보다 조금 어둡게만, 창은 작고 드문드문
        skyline.rewind()
        val warmPts = ArrayList<Offset>(); val coolPts = ArrayList<Offset>()
        var x = tile * 0.1f
        val unit = h * 0.0055f
        while (x < tile * 0.6f) {
            val bw = unit * (5f + r() * 6f)
            val bh = unit * (5f + r() * r() * 13f)
            if (r() > 0.35f) {
                skyline.addRect(Rect(x, horizon - bh, x + bw, horizon + 1f))
                var wy = horizon - bh + unit
                while (wy < horizon - unit) {
                    var wx = x + unit * 0.8f
                    while (wx < x + bw - unit * 0.6f) {
                        val p = r()
                        if (p > 0.72f) (if (p > 0.95f) coolPts else warmPts).add(Offset(wx, wy))
                        wx += unit * 1.3f
                    }
                    wy += unit * 1.5f
                }
            }
            x += bw + unit * (1f + r() * 5f)
            if (r() > 0.8f) x += tile * 0.03f
        }
        windowsWarm = warmPts; windowsCool = coolPts
        val s1 = ArrayList<Offset>()
        repeat(70) { s1.add(Offset(r() * w, r() * r() * horizon * 0.8f)) }
        stars = s1
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

    /** 길가 숲의 윤곽. 높이가 거리 따라 완만하게 오르내린다. */
    private fun treeLine(p: Path, x: Float, base: Float, amp: Float, camS: Float) {
        p.rewind()
        p.moveTo(sx(0, x), sy[0])
        for (i in 0 until N) {
            val s = camS + zs[i]
            val hgt = base + amp * (0.5f + 0.32f * sin(s * 0.041f) + 0.18f * sin(s * 0.117f + 1.9f))
            p.lineTo(sx(i, x), sy[i] - hgt * kk[i])
        }
        p.lineTo(sx(N - 1, x), sy[N - 1])
        p.close()
    }

    fun draw(scope: DrawScope, sim: SceneSim) = with(scope) {
        ensure(size)
        playerX = sim.playerX
        // 흔들림: 속도에 비례한 아주 작은 떨림
        val shake = sin(sim.t * 31f) * sim.speed * 0.008f + sin(sim.t * 47f) * sim.urgency * h * 0.003f
        horizon = h * SceneSim.VANISH_Y + shake
        computeRoad(sim)

        val night = sim.night
        val tunnelDark = if (sim.tunnelEntering) sim.tunnelP * sim.tunnelP else sim.tunnelP
        val ambient = (0.3f + 0.7f * (1f - night)) * (1f - 0.7f * tunnelDark)
        val wetRef = sim.rain

        if (sim.parking > 0.5f) {
            drawParking(sim)
        } else {
            drawSky(sim)
            drawGroundAndRoad(sim, ambient)
            drawRoadside(sim, ambient, wetRef)
            drawTraffic(sim, ambient, wetRef)
            if (sim.tunnelP > 0f) drawTunnel(sim)
            drawLead(sim, ambient, wetRef)
        }
        val cut = 1f - abs(2f * sim.parking - 1f)
        if (cut > 0.01f) drawRect(Color.Black, alpha = cut)

        drawFogAndGlare(sim)
        drawStreaks(sim)
    }

    /**
     * 실사 배경 위에 얹는 것: 전조등·터널에 따른 밝기, 앞차 브레이크등 빛, 맞은편 눈부심, 안개, 빗줄기.
     * 도로·차·터널은 영상이 보여 주므로 그리지 않는다.
     */
    fun drawOverBackdrop(scope: DrawScope, sim: SceneSim, f: BackdropFrame) = with(scope) {
        ensure(size)
        f.layout(w, h)
        horizon = h * SceneSim.VANISH_Y
        val dark = 1f - sim.lowBeam
        when (f.clip) {
            BackdropClip.NIGHT_ROAD -> {
                // 영상은 전조등을 켜고 찍었다. 끄면 전체가 가라앉고, 발밑 노면은 거의 보이지 않는다
                // 비가 오면 덜 가라앉힌다: 와이퍼가 닦은 자리로 도로·차선·앞차가 보여야 닦인 차이가 난다
                if (dark > 0.01f) {
                    drawRect(Color.Black, alpha = (0.4f - 0.24f * sim.rain) * dark)
                    drawRect(roadShade!!, topLeft = Offset(0f, horizon), size = Size(w, h - horizon), alpha = dark * (1f - 0.55f * sim.rain))
                }
                if (sim.highBeam > 0.01f) glow(beam, cx, horizon + h * 0.06f, w * 0.5f, h * 0.2f, alpha = sim.highBeam * 0.14f)
            }
            // 전조등을 켜기 전에도 터널 벽과 앞차는 알아볼 수 있을 만큼만 어둡게
            BackdropClip.TUNNEL -> if (dark > 0.01f) drawRect(Color.Black, alpha = 0.42f * dark)
            BackdropClip.PARKING -> {
                if (dark > 0.01f) drawRect(Color.Black, alpha = 0.34f * dark)
                // 벽에 닿은 내 전조등
                if (sim.lowBeam > 0.01f) {
                    val by = f.viewY(0.64f)
                    glow(beam, f.viewX(0.27f), by, w * 0.13f, h * 0.085f, alpha = sim.lowBeam * 0.34f)
                    glow(beam, f.viewX(0.5f), by, w * 0.13f, h * 0.085f, alpha = sim.lowBeam * 0.34f)
                }
                if (sim.highBeam > 0.01f) glow(white, f.viewX(0.385f), f.viewY(0.5f), w * 0.4f, h * 0.2f, alpha = sim.highBeam * 0.3f)
            }
        }
        // 앞차 급정거: 영상 속 앞차의 두 미등 자리에 브레이크등 빛을 얹는다. 빛은 등 크기의 두세 배 안에서 끝나고,
        // 다가갈수록 젖은 노면에 비친 빛만 조금 길어진다
        if (f.clip == BackdropClip.NIGHT_ROAD && (sim.leadBrake > 0.02f || sim.leadZoom > 0.02f)) {
            val lx = f.viewX(LeadTrack.u(f.positionSec)); val ly = f.viewY(LeadTrack.v(f.positionSec))
            val cw = LeadTrack.width(f.positionSec) * f.dw
            sim.leadSx = lx; sim.leadSy = ly; sim.leadK = cw / 1.8f
            val on = sim.leadBrake
            val lampR = cw * 0.16f
            glow(red, lx - cw * 0.37f, ly, lampR * 2.4f, alpha = 0.95f * on)
            glow(red, lx + cw * 0.37f, ly, lampR * 2.4f, alpha = 0.95f * on)
            glow(white, lx - cw * 0.37f, ly, lampR * 0.7f, alpha = 0.5f * on)
            glow(white, lx + cw * 0.37f, ly, lampR * 0.7f, alpha = 0.5f * on)
            glow(red, lx, ly - cw * 0.3f, cw * 0.16f, cw * 0.05f, alpha = 0.8f * on)
            val u = sim.leadZoom * on
            glow(red, lx - cw * 0.37f, ly + cw * 0.95f, lampR * 1.2f, cw * 0.7f, alpha = 0.2f * u)
            glow(red, lx + cw * 0.37f, ly + cw * 0.95f, lampR * 1.2f, cw * 0.7f, alpha = 0.2f * u)
        }
        // 맞은편 눈부심: 왼쪽 맞은편 차로에서 전조등이 다가온다
        if (sim.glare > 0.01f && f.clip != BackdropClip.PARKING) {
            val g = sim.glare
            val gx = w * (0.4f - 0.24f * g.pow(1.6f)); val gy = horizon + h * 0.06f * g
            val r = w * (0.015f + 0.15f * g)
            val sep = r * 0.42f
            glow(white, gx - sep, gy, r, alpha = g)
            glow(white, gx + sep, gy, r, alpha = g)
            glow(white, gx, gy, r * 4.2f, r * 0.07f, alpha = g * 0.55f)
            glow(fogBloom, gx, gy, r * 3f, r * 2f, alpha = g * 0.45f)
        }
        // 장면이 바뀌는 순간(터널 드나들기, 주차장)은 잠깐 어두워졌다 밝아진다
        val cut = maxOf(1f - abs(2f * sim.parking - 1f), 1f - abs(2f * sim.tunnelP - 1f))
        if (cut > 0.01f) drawRect(Color.Black, alpha = cut)
        if (sim.rain > 0.05f) drawRect(Color(0xFF05070A), alpha = sim.rain * 0.16f)
        if (sim.flash > 0.01f) drawRect(Color(0xFFCBD3E6), alpha = sim.flash * 0.16f)
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
        val clear = (1f - sim.rain) * (1f - sim.fogOut) * night
        if (clear > 0.02f) drawPoints(stars, PointMode.Points, Color(0xFFDDE6F2), strokeWidth = h * 0.0016f, cap = StrokeCap.Round, alpha = clear * 0.4f)
        // 지평선 위로 번지는 도시의 빛
        glow(haze, cx, horizon, w * 0.8f, h * 0.13f, alpha = night * 0.5f * (1f - sim.fogOut))
        val shift = ((sim.heading * f) % tile + tile) % tile
        var x0 = -shift
        while (x0 < w) {
            translate(x0, 0f) {
                drawPath(ridgeFar, mix(Color(0xFF0C0E13), Color(0xFF8D9AA3), 1f - night))
                drawPath(skyline, mix(Color(0xFF0B0D11), Color(0xFF8A939B), 1f - night))
                if (night > 0.3f) {
                    drawPoints(windowsWarm, PointMode.Points, Color(0xFFFFD7A0), strokeWidth = h * 0.0026f, alpha = night * 0.6f)
                    drawPoints(windowsCool, PointMode.Points, Color(0xFFCFE6FF), strokeWidth = h * 0.0026f, alpha = night * 0.6f)
                }
                drawPath(ridgeNear, mix(Color(0xFF07090C), Color(0xFF5E6E64), 1f - night))
            }
            x0 += tile
        }
        if (sim.rain > 0.05f) drawRect(Color(0xFF07090D), size = Size(w, horizon + 2f), alpha = sim.rain * 0.5f)
        // 번개는 하늘이 잠깐 밝아지는 것으로만 보인다
        if (sim.flash > 0.01f) drawRect(Color(0xFFCBD3E6), size = Size(w, horizon + 2f), alpha = sim.flash * 0.3f)
    }

    private fun DrawScope.drawGroundAndRoad(sim: SceneSim, ambient: Float) {
        val night = sim.night
        var top = horizon
        for (i in 0 until N) if (sy[i] < top) top = sy[i]
        drawRect(mix(Color(0xFF050607), Color(0xFF66705F), 1f - night), topLeft = Offset(0f, top - 1f), size = Size(w, h - top + 1f))

        // 맞은편 차로와 그 너머, 오른쪽 길가의 숲
        val asphalt = mix(Color(0xFF08090B), Color(0xFF666A70), ambient.pow(1.2f) * 0.85f)
        strip(path, -18.5f, 0f, -6.1f, 0f)
        drawPath(path, asphalt, alpha = 0.85f)
        val trees = mix(Color(0xFF040506), Color(0xFF47564A), 1f - night)
        treeLine(path2, -20f, 2.5f, 3.5f, sim.camS + 311f)
        drawPath(path2, trees)
        treeLine(path2, 13f, 2f, 3.5f, sim.camS)
        drawPath(path2, trees)

        // 노면. 전조등이 닿는 곳만 밝고, 멀수록·옆으로 갈수록 어둡다
        strip(path, -6.1f, 0f, SHOULDER, 0f)
        drawPath(path, asphalt)
        val dry = 1f - 0.4f * sim.rain
        if (sim.lowBeam > 0.01f) drawPath(path, litLow!!, alpha = sim.lowBeam * 0.62f * dry * night)
        if (sim.highBeam > 0.01f) drawPath(path, litHigh!!, alpha = sim.highBeam * 0.5f * dry * night)
        if (sim.flash > 0.01f) drawPath(path, Color(0xFFAEB6C8), alpha = sim.flash * 0.1f)
        // 바퀴가 지나는 자리는 조금 더 어둡게 닳아 있다
        for (lane in -1..1) for (side in -1..1 step 2) {
            val c = lane * 3.6f + side * 0.82f
            strip(path2, c - 0.27f, 0f, c + 0.27f, 0f)
            drawPath(path2, Color.Black, alpha = 0.045f + 0.05f * sim.rain)
        }
        if (night > 0.2f && sim.lowBeam > 0.01f) {
            drawRect(sideShade!!, topLeft = Offset(0f, top), size = Size(w, h - top), alpha = sim.lowBeam * night * 0.5f)
        }
        // 젖은 노면에 비친 내 전조등
        if (sim.rain > 0.05f && sim.lowBeam > 0.01f && !sim.lowDetail) {
            project(13f, 0f)
            glow(beam, cx, py, w * 0.2f, h * 0.05f, alpha = sim.lowBeam * sim.rain * 0.16f)
        }

        // 가장자리 실선 (왼쪽 노랑, 오른쪽 흰색). 차선 도료는 전조등을 받아야 떠오른다
        val base = ambient * 0.62f
        val edgeA = (base + sim.lowBeam * 0.45f + sim.highBeam * 0.3f).coerceAtMost(1f) * (1f - 0.3f * sim.rain)
        if (edgeA > 0.01f) {
            strip(path, EDGE - 0.075f, 0f, EDGE + 0.075f, 0f)
            drawPath(path, PAINT, alpha = edgeA)
            strip(path, -EDGE - 0.075f, 0f, -EDGE + 0.075f, 0f)
            drawPath(path, PAINT_YELLOW, alpha = edgeA)
        }

        // 차선 점선: 8m 선, 12m 간격
        val first = floor(sim.camS / 20f) * 20f
        for (j in 15 downTo -1) {
            val z0 = first + j * 20f - sim.camS
            val z1 = z0 + 8f
            if (z1 <= Z0 + 0.2f) continue
            val za = maxOf(z0, Z0)
            val a = (base + sim.lowBeam * 0.62f * smooth(90f, 12f, za) + sim.highBeam * 0.5f * smooth(220f, 25f, za)) *
                (1f - za / 320f) * (1f - 0.3f * sim.rain)
            if (a <= 0.01f) continue
            for (side in -1..1 step 2) {
                val lx = side * 1.8f
                path.rewind()
                project(za, lx - 0.075f); path.moveTo(px, py)
                project(za, lx + 0.075f); path.lineTo(px, py)
                project(z1, lx + 0.075f); path.lineTo(px, py)
                project(z1, lx - 0.075f); path.lineTo(px, py)
                path.close()
                drawPath(path, PAINT, alpha = a.coerceAtMost(0.9f))
            }
        }
    }

    private fun inTube(sim: SceneSim, z: Float): Boolean {
        if (sim.tunnelP <= 0f) return false
        return if (sim.tunnelEntering) z > portalZ(sim) else z < portalZ(sim)
    }

    private fun portalZ(sim: SceneSim): Float =
        if (sim.tunnelEntering) PORTAL_FAR * (1f - sim.tunnelP).pow(1.4f) - 0.01f else PORTAL_FAR * sim.tunnelP.pow(1.4f)

    private fun DrawScope.drawRoadside(sim: SceneSim, ambient: Float, wetRef: Float) {
        val night = sim.night
        val lit = (ambient * 0.55f + sim.lowBeam * 0.22f).coerceAtMost(1f)
        // 중앙분리대: 콘크리트 방호벽의 옆면과 윗면
        strip(path, -6.1f, 0f, -6.1f, 0.82f)
        drawPath(path, mix(Color(0xFF0B0C0E), Color(0xFFA2A6AA), lit * 0.8f))
        strip(path, -6.1f, 0.82f, -6.45f, 0.86f)
        drawPath(path, mix(Color(0xFF111316), Color(0xFFC0C3C6), lit))
        // 오른쪽 가드레일: 물결 모양 철판 한 줄과 지주
        strip(path, RAIL, 0.46f, RAIL, 0.78f)
        drawPath(path, mix(Color(0xFF0D0F12), Color(0xFFB4B9BE), lit * 0.9f))
        strip(path, RAIL, 0.6f, RAIL, 0.64f)
        drawPath(path, Color.Black, alpha = 0.35f)
        val postFirst = floor(sim.camS / 4f) * 4f
        val postC = mix(Color(0xFF090A0C), Color(0xFF7E848A), lit)
        for (j in 17 downTo 0) {
            val z = postFirst + j * 4f + 2f - sim.camS
            if (z < Z0 + 0.4f || inTube(sim, z)) continue
            project(z, RAIL)
            drawLine(postC, Offset(px, py), Offset(px, py - 0.5f * pk), maxOf(1f, 0.11f * pk))
        }

        // 표지판 문형
        val gFirst = floor((sim.camS - 450f) / 900f) * 900f + 450f
        for (j in 1 downTo 0) {
            val z = gFirst + j * 900f - sim.camS
            if (z < 6f || z > 330f || inTube(sim, z)) continue
            val signLit = (ambient * 0.5f + sim.lowBeam * 0.4f * smooth(140f, 20f, z) + sim.highBeam * 0.4f).coerceAtMost(1f) * (1f - z / 340f)
            project(z, -7.2f); val lx = px; val gy = py; val k = pk
            project(z, 8.2f); val rx = px
            val topY = gy - 7.6f * k
            val sw = maxOf(1f, 0.26f * k)
            val steel = mix(Color(0xFF14171B), Color(0xFF8B9096), ambient * 0.6f)
            drawLine(steel, Offset(lx, gy), Offset(lx, topY), sw)
            drawLine(steel, Offset(rx, gy), Offset(rx, topY), sw)
            drawLine(steel, Offset(lx, topY), Offset(rx, topY), sw)
            drawLine(steel, Offset(lx, topY + 0.9f * k), Offset(rx, topY + 0.9f * k), sw * 0.6f)
            for (b in 0..1) {
                project(z, if (b == 0) -4.9f else 0.5f)
                val bx = px; val bw = 4.4f * k; val bh = 2.5f * k; val by = gy - 7.9f * k
                drawRect(mix(Color(0xFF062A1E), Color(0xFF0E6B49), signLit), Offset(bx, by), Size(bw, bh))
                val ink = Color(0xFFF1F4F2)
                drawRect(ink, Offset(bx + 0.1f * k, by + 0.1f * k), Size(bw - 0.2f * k, bh - 0.2f * k), alpha = signLit, style = androidx.compose.ui.graphics.drawscope.Stroke(maxOf(1f, 0.06f * k)))
                drawRect(ink, Offset(bx + bw * 0.1f, by + bh * 0.24f), Size(bw * 0.46f, bh * 0.17f), alpha = signLit * 0.95f)
                drawRect(ink, Offset(bx + bw * 0.1f, by + bh * 0.58f), Size(bw * 0.3f, bh * 0.12f), alpha = signLit * 0.8f)
                // 아래를 가리키는 화살표
                val ax = bx + bw * 0.8f; val ay = by + bh * 0.74f; val aw = maxOf(1f, 0.11f * k)
                drawLine(ink, Offset(ax, by + bh * 0.22f), Offset(ax, ay), aw, alpha = signLit)
                drawLine(ink, Offset(ax - 0.28f * k, ay - 0.3f * k), Offset(ax, ay), aw, alpha = signLit)
                drawLine(ink, Offset(ax + 0.28f * k, ay - 0.3f * k), Offset(ax, ay), aw, alpha = signLit)
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
            project(z, side * (if (side > 0f) 7.5f else 6.3f)); val bx = px; val by = py; val k = pk
            val topY = by - 10f * k
            project(z, side * 4.4f, 10.2f); val hx = px; val hy = py
            val sw = maxOf(1f, 0.13f * k)
            drawLine(Color(0xFF1A1D21), Offset(bx, by), Offset(bx, topY), sw)
            drawLine(Color(0xFF1A1D21), Offset(bx, topY), Offset(hx, hy), sw * 0.8f)
            // 노면에 떨어진 빛, 비가 오면 길게 늘어진 반사
            if (!sim.lowDetail) {
                project(z, side * 3.2f)
                glow(lamp, px, py, 8f * k, maxOf(2f, 52f * k / (z + 8f)), alpha = night * 0.13f * far)
                if (wetRef > 0.05f) {
                    project(z, side * 4.4f)
                    glow(lamp, px, py + 3f * k, maxOf(1.5f, 0.45f * k), 4.5f * k, alpha = night * 0.3f * wetRef * far)
                }
            }
            glow(lamp, hx, hy, maxOf(3f, 1.3f * k) * (1f + sim.fogOut * 2.2f + sim.rain * 0.5f), alpha = night * (0.45f + 0.35f * far))
            drawCircle(Color(0xFFFFF2DC), maxOf(1f, 0.15f * k), Offset(hx, hy), alpha = night)
        }

        // 반사 표지 (20m 간격). 전조등을 받아야 빛난다
        val pFirst = floor(sim.camS / 20f) * 20f
        for (j in 12 downTo 0) {
            val z = pFirst + j * 20f + 10f - sim.camS
            if (z < 3f || inTube(sim, z)) continue
            val refl = (0.12f * ambient + sim.lowBeam * 0.75f * smooth(110f, 10f, z) + sim.highBeam * 0.6f * smooth(260f, 30f, z)).coerceAtMost(1f)
            if (refl < 0.02f) continue
            project(z, RAIL, 0.9f)
            val r = maxOf(1f, 0.05f * pk)
            drawCircle(Color(0xFFF4F6F8), r, Offset(px, py), alpha = refl)
            project(z, -6.25f, 0.95f)
            drawCircle(Color(0xFFFFB23A), r, Offset(px, py), alpha = refl)
        }
    }

    /**
     * 뒤에서 본 차. [body]는 차체 색, [lit]는 내 전조등·주변 빛을 받는 정도, [tall]이면 SUV 비례.
     * 멀리 있을 때는 미등 두 점만 남는다.
     */
    private fun DrawScope.drawCar(x: Float, groundY: Float, k: Float, braking: Float, alpha: Float, wetRef: Float, body: Color, lit: Float, tall: Boolean) {
        val cw = (if (tall) 1.9f else 1.82f) * k
        val ch = (if (tall) 1.66f else 1.43f) * k
        val left = x - cw / 2f
        fun yy(up: Float) = groundY - ch * up
        val lampY = yy(if (tall) 0.6f else 0.56f)
        if (k > 6f) {
            val shade = (0.1f + 0.75f * lit).coerceAtMost(1f)
            val paint = mix(Color(0xFF050607), body, shade)
            drawOval(Color.Black, Offset(left - cw * 0.04f, groundY - ch * 0.06f), Size(cw * 1.08f, ch * 0.15f), alpha = 0.6f * alpha)
            // 타이어
            val tyre = Color(0xFF040405)
            drawRect(tyre, Offset(left + cw * 0.05f, yy(0.2f)), Size(cw * 0.17f, ch * 0.2f), alpha = alpha)
            drawRect(tyre, Offset(left + cw * 0.78f, yy(0.2f)), Size(cw * 0.17f, ch * 0.2f), alpha = alpha)
            // 차체: 넓은 아랫몸에서 어깨선으로 좁아지고 둥근 지붕으로 이어진다
            val belt = if (tall) 0.66f else 0.62f
            path.rewind()
            path.moveTo(left + cw * 0.045f, yy(0.1f))
            path.cubicTo(left + cw * 0.005f, yy(0.14f), left - cw * 0.005f, yy(0.36f), left + cw * 0.012f, yy(belt - 0.07f))
            path.cubicTo(left + cw * 0.02f, yy(belt - 0.01f), left + cw * 0.05f, yy(belt), left + cw * 0.075f, yy(belt + 0.01f))
            path.cubicTo(left + cw * 0.11f, yy(0.84f), left + cw * 0.15f, yy(0.97f), left + cw * 0.26f, yy(0.995f))
            path.quadraticBezierTo(left + cw * 0.5f, yy(1.015f), left + cw * 0.74f, yy(0.995f))
            path.cubicTo(left + cw * 0.85f, yy(0.97f), left + cw * 0.89f, yy(0.84f), left + cw * 0.925f, yy(belt + 0.01f))
            path.cubicTo(left + cw * 0.95f, yy(belt), left + cw * 0.98f, yy(belt - 0.01f), left + cw * 0.988f, yy(belt - 0.07f))
            path.cubicTo(left + cw * 1.005f, yy(0.36f), left + cw * 0.995f, yy(0.14f), left + cw * 0.955f, yy(0.1f))
            path.close()
            drawPath(path, paint, alpha = alpha)
            // 트렁크 윗면은 빛을 더 받고, 범퍼 아래는 그늘진다
            drawRect(Color.White, Offset(left + cw * 0.05f, yy(belt - 0.005f)), Size(cw * 0.9f, ch * 0.035f), alpha = 0.1f * shade * alpha)
            drawRect(Color.Black, Offset(left + cw * 0.04f, yy(0.25f)), Size(cw * 0.92f, ch * 0.15f), alpha = 0.22f * alpha)
            // 뒷유리
            path.rewind()
            path.moveTo(left + cw * 0.12f, yy(belt + 0.02f))
            path.cubicTo(left + cw * 0.15f, yy(0.84f), left + cw * 0.19f, yy(0.94f), left + cw * 0.28f, yy(0.955f))
            path.lineTo(left + cw * 0.72f, yy(0.955f))
            path.cubicTo(left + cw * 0.81f, yy(0.94f), left + cw * 0.85f, yy(0.84f), left + cw * 0.88f, yy(belt + 0.02f))
            path.close()
            drawPath(path, Color(0xFF05070A), alpha = alpha)
            drawLine(Color.White, Offset(left + cw * 0.27f, yy(0.945f)), Offset(left + cw * 0.73f, yy(0.945f)), maxOf(1f, 0.025f * k), alpha = 0.1f * alpha)
            // 번호판
            drawRect(mix(Color(0xFF2A2C2E), Color(0xFFE6E8EA), shade), Offset(x - cw * 0.13f, yy(0.42f)), Size(cw * 0.26f, ch * 0.085f), alpha = alpha)
            // 미등
            val lampH = ch * 0.07f
            val lampC = mix(Color(0xFFB0121C), Color(0xFFFF3A34), braking)
            val lampA = alpha * (0.72f + 0.28f * braking)
            val rr = androidx.compose.ui.geometry.CornerRadius(lampH * 0.4f)
            drawRoundRect(lampC, Offset(left + cw * 0.035f, lampY - lampH / 2f), Size(cw * 0.25f, lampH), rr, alpha = lampA)
            drawRoundRect(lampC, Offset(left + cw * 0.715f, lampY - lampH / 2f), Size(cw * 0.25f, lampH), rr, alpha = lampA)
            if (tall) drawRect(lampC, Offset(left + cw * 0.285f, lampY - lampH * 0.14f), Size(cw * 0.43f, lampH * 0.28f), alpha = lampA * 0.7f)
            if (braking > 0.05f) {
                drawRect(Color(0xFFFF4A40), Offset(x - cw * 0.12f, yy(0.975f)), Size(cw * 0.24f, ch * 0.022f), alpha = alpha * braking)
                drawRoundRect(Color(0xFFFFD0C8), Offset(left + cw * 0.06f, lampY - lampH * 0.22f), Size(cw * 0.2f, lampH * 0.44f), rr, alpha = alpha * braking * 0.8f)
                drawRoundRect(Color(0xFFFFD0C8), Offset(left + cw * 0.74f, lampY - lampH * 0.22f), Size(cw * 0.2f, lampH * 0.44f), rr, alpha = alpha * braking * 0.8f)
            }
        }
        // 등에서 번지는 빛은 등 크기의 몇 배 안에서 끝난다
        val gr = maxOf(1.6f, 0.2f * k) * (1f + 1.5f * braking)
        val ga = alpha * (0.5f + 0.4f * braking)
        glow(red, left + cw * 0.16f, lampY, gr, alpha = ga)
        glow(red, left + cw * 0.84f, lampY, gr, alpha = ga)
        if (wetRef > 0.05f) {
            val ra = alpha * wetRef * (0.22f + 0.4f * braking)
            glow(red, left + cw * 0.16f, groundY + ch * 0.7f, gr * 0.7f, ch * 0.9f, alpha = ra)
            glow(red, left + cw * 0.84f, groundY + ch * 0.7f, gr * 0.7f, ch * 0.9f, alpha = ra)
        }
    }

    private fun carLit(sim: SceneSim, ambient: Float, z: Float): Float =
        (ambient * 0.5f + sim.lowBeam * 0.6f * smooth(75f, 8f, z) + sim.highBeam * 0.4f * smooth(200f, 20f, z)).coerceAtMost(1f)

    private fun DrawScope.drawTraffic(sim: SceneSim, ambient: Float, wetRef: Float) {
        // 맞은편 차로의 전조등
        for (j in 0 until SceneSim.ONCOMING) {
            val z = sim.onZ[j]
            if (z < 6f || z > 340f || inTube(sim, z)) continue
            val far = 1f - z / 380f
            for (s in 0..1) {
                project(z, -10.6f + (if (s == 0) -0.72f else 0.72f), 0.72f)
                val r = maxOf(1.5f, 0.3f * pk)
                glow(white, px, py, r * (1f + sim.fogOut * 2.5f + sim.rain * 0.6f), alpha = (0.55f + 0.45f * far) * sim.night.coerceAtLeast(0.5f))
                if (wetRef > 0.05f) glow(white, px, py + 1.6f * pk, r * 0.5f, 1.8f * pk, alpha = 0.2f * wetRef * far)
            }
        }
        // 맞은편 눈부심
        if (sim.glare > 0.01f) {
            val z = 260f - 232f * sim.glare.pow(0.8f)
            project(z, -9.8f, 0.72f)
            val gx = px.coerceIn(w * 0.08f, w * 0.92f); val gy = py
            val r = w * (0.02f + 0.16f * sim.glare)
            val sep = maxOf(0.75f * pk, r * 0.3f)
            glow(white, gx - sep, gy, r, alpha = sim.glare)
            glow(white, gx + sep, gy, r, alpha = sim.glare)
            // 눈이 부시면 빛이 옆으로 길게 번진다
            glow(white, gx, gy, r * 4.2f, r * 0.07f, alpha = sim.glare * 0.55f)
            glow(fogBloom, gx, gy, r * 3f, r * 2f, alpha = sim.glare * 0.45f)
        }
        // 옆 차로 차들
        for (j in 0 until SceneSim.AMBIENT_CARS) {
            val z = sim.carZ[j]
            if (z < 2.6f || z > 330f) continue
            project(z, sim.carLane[j] * 3.6f)
            drawCar(px, py, pk, 0f, (1f - z / 340f).coerceIn(0f, 1f), wetRef, BODIES[j % BODIES.size], carLit(sim, ambient, z) * 0.8f, j % 2 == 1)
        }
    }

    private fun DrawScope.drawLead(sim: SceneSim, ambient: Float, wetRef: Float) {
        if (sim.leadA < 0.02f) return
        val z = sim.leadZ.coerceIn(3.2f, 330f)
        project(z, 0f)
        sim.leadSx = px; sim.leadSy = py; sim.leadK = pk
        drawCar(px, py, pk, sim.leadBrake, sim.leadA, wetRef, Color(0xFFAEB3B9), carLit(sim, ambient, z), false)
        // 바짝 붙으면 브레이크등 빛이 유리와 노면에 번진다
        if (sim.urgency > 0.01f) {
            val u = sim.urgency * (0.4f + 0.6f * sim.leadBrake)
            glow(red, px, py - 0.75f * pk, 2.4f * pk, 1.3f * pk, alpha = 0.22f * u)
            glow(red, px, py + 0.9f * pk, 2f * pk, 1.6f * pk, alpha = 0.16f * u)
        }
    }

    /** 거리 [z]에 있는 터널 단면을 화면 좌표로. */
    private fun ringAt(z: Float, ox: FloatArray, oy: FloatArray) {
        project(z, 0f)
        for (i in 0 until PROF) { ox[i] = px + PROF_X[i] * TUBE_HALF * pk; oy[i] = py - PROF_Y[i] * pk }
    }

    private fun archPath(p: Path, ox: FloatArray, oy: FloatArray, grow: Float = 0f, lift: Float = 0f) {
        val mid = (ox[0] + ox[PROF - 1]) * 0.5f
        p.moveTo(mid + (ox[0] - mid) * (1f + grow), oy[0] + 2f)
        for (i in 1 until PROF - 1) p.lineTo(mid + (ox[i] - mid) * (1f + grow), oy[i] - lift)
        p.lineTo(mid + (ox[PROF - 1] - mid) * (1f + grow), oy[PROF - 1] + 2f)
        p.close()
    }

    private fun DrawScope.drawTunnel(sim: SceneSim) {
        val pz = portalZ(sim)
        val entering = sim.tunnelEntering
        val zNear = if (entering) maxOf(pz, Z0) else Z0
        val zFar = if (entering) TUBE_FAR else minOf(pz, TUBE_FAR)
        if (zFar <= zNear + 0.5f) return

        if (entering && pz > Z0) {
            ringAt(pz, ringAx, ringAy)
            val k = pk; val gy = ringAy[0]
            val lx = ringAx[0]; val rx = ringAx[PROF - 1]
            val mid = (lx + rx) * 0.5f
            val topY = ringAy[PROF / 2]
            val day = 1f - sim.night
            // 입구를 품은 산자락
            path.rewind()
            path.fillType = PathFillType.EvenOdd
            path.moveTo(mid - 90f * k, gy + 1f)
            path.cubicTo(mid - 40f * k, gy - 4f * k, mid - 26f * k, gy - 27f * k, mid, gy - 28f * k)
            path.cubicTo(mid + 26f * k, gy - 27f * k, mid + 40f * k, gy - 4f * k, mid + 90f * k, gy + 1f)
            path.close()
            archPath(path, ringAx, ringAy)
            drawPath(path, mix(Color(0xFF07090B), Color(0xFF55615A), day))
            // 콘크리트 입구 벽: 아치 구멍이 뚫린 면, 위쪽 턱, 벽에 떨어진 그늘
            val faceL = lx - 3.4f * k; val faceR = rx + 3.4f * k; val faceT = topY - 2.6f * k
            path.rewind()
            path.moveTo(faceL - 1.5f * k, gy + 1f); path.lineTo(faceL, faceT); path.lineTo(faceR, faceT); path.lineTo(faceR + 1.5f * k, gy + 1f)
            path.close()
            archPath(path, ringAx, ringAy)
            drawPath(path, mix(Color(0xFF1A1D21), Color(0xFF9EA2A4), day))
            path.fillType = PathFillType.NonZero
            drawRect(mix(Color(0xFF24282C), Color(0xFFC4C7C9), day), Offset(faceL - 0.5f * k, faceT - 0.7f * k), Size(faceR - faceL + k, 0.7f * k))
            drawRect(Color.Black, Offset(faceL, faceT), Size(faceR - faceL, 0.35f * k), alpha = 0.25f)
            // 입구 위 터널 이름판
            drawRect(mix(Color(0xFF062A1E), Color(0xFF0E6B49), 0.3f + 0.7f * day), Offset(mid - 3f * k, topY - 2f * k), Size(6f * k, 1.25f * k))
            drawRect(Color(0xFFF1F4F2), Offset(mid - 2.1f * k, topY - 1.6f * k), Size(4.2f * k, 0.42f * k), alpha = 0.5f + 0.4f * day)
            path2.rewind()
            archPath(path2, ringAx, ringAy)
            clipPath(path2) { drawTube(sim, zNear, zFar, true) }
            // 아치 테두리
            path2.rewind(); archPath(path2, ringAx, ringAy)
            drawPath(path2, mix(Color(0xFF2D3136), Color(0xFFD2D5D7), day), style = androidx.compose.ui.graphics.drawscope.Stroke(maxOf(1.5f, 0.3f * k)))
        } else {
            drawTube(sim, zNear, zFar, entering)
        }
    }

    private fun DrawScope.drawTube(sim: SceneSim, zNear: Float, zFar: Float, capFar: Boolean) {
        val dark = Color(0xFF040506)
        ringAt(zFar, ringBx, ringBy)
        if (capFar) {
            path.rewind(); archPath(path, ringBx, ringBy)
            drawPath(path, dark)
        }
        var ring = floor((sim.camS + zFar) / RING).toInt()
        var zb = zFar
        var za = ring * RING - sim.camS
        if (za >= zb - 0.05f) { za -= RING; ring-- }
        while (zb > zNear) {
            if (za < zNear) za = zNear
            ringAt(za, ringAx, ringAy)
            val ak = pk
            val mid = (za + zb) * 0.5f
            // 터널 조명이 깔아 주는 밝기에 내 전조등이 닿는 만큼을 더한다
            val b = ((0.36f + 0.34f * sim.lowBeam * smooth(95f, 6f, mid) + 0.14f * sim.highBeam) * (1f - mid / (TUBE_FAR * 1.25f))).coerceIn(0f, 1f)
            val tileC = mix(dark, Color(0xFFA9A69C), b)
            val wallC = mix(dark, Color(0xFF5C5B57), b)
            val ceilC = mix(dark, Color(0xFF34332F), b)
            // 바닥: 낮에는 터널 안이 어둡고, 밤에는 조명이 노면을 데운다
            path.rewind()
            path.moveTo(ringAx[0], ringAy[0]); path.lineTo(ringAx[PROF - 1], ringAy[PROF - 1])
            path.lineTo(ringBx[PROF - 1], ringBy[PROF - 1] - 0.6f); path.lineTo(ringBx[0], ringBy[0] - 0.6f); path.close()
            val floorA = 0.55f * (1f - sim.night) * (1f - 0.5f * sim.lowBeam * smooth(70f, 6f, mid))
            if (floorA > 0.01f) drawPath(path, dark, alpha = floorA)
            drawPath(path, Color(0xFFB59A6A), alpha = 0.07f * b * sim.night)
            for (j in 0 until PROF - 1) {
                path.rewind()
                path.moveTo(ringAx[j], ringAy[j]); path.lineTo(ringAx[j + 1], ringAy[j + 1])
                path.lineTo(ringBx[j + 1], ringBy[j + 1]); path.lineTo(ringBx[j], ringBy[j]); path.close()
                val c = when (j) {
                    0, PROF - 2 -> tileC
                    1, PROF - 3 -> wallC
                    else -> ceilC
                }
                drawPath(path, c)
            }
            // 타일 벽 위의 띠와 고리마다 하나씩 달린 등
            if (ak > 1.2f) {
                drawLine(dark, Offset(ringAx[1], ringAy[1]), Offset(ringBx[1], ringBy[1]), maxOf(1f, 0.1f * ak), alpha = 0.5f)
                drawLine(dark, Offset(ringAx[PROF - 2], ringAy[PROF - 2]), Offset(ringBx[PROF - 2], ringBy[PROF - 2]), maxOf(1f, 0.1f * ak), alpha = 0.5f)
            }
            if (za > zNear + 0.01f) {
                val far = 1f - za / TUBE_FAR
                val lw = 1.5f * ak; val lh = maxOf(1f, 0.16f * ak)
                for (s in 0..1) {
                    val i = if (s == 0) 3 else PROF - 4
                    drawRect(Color(0xFFFFE6BE), Offset(ringAx[i] - lw / 2f, ringAy[i]), Size(lw, lh), alpha = 0.55f + 0.45f * far)
                    glow(lamp, ringAx[i], ringAy[i] + lh, 1.5f * ak, 0.8f * ak, alpha = 0.3f * far)
                }
            }
            for (i in 0 until PROF) { ringBx[i] = ringAx[i]; ringBy[i] = ringAy[i] }
            zb = za
            za -= RING
            ring--
        }
    }

    /** 지하 주차장. 벽을 보고 서 있는 한 칸: 바닥의 주차선, 기둥, 천장 등, 벽에 닿은 내 전조등. */
    private fun DrawScope.drawParking(sim: SceneSim) {
        val hy = h * SceneSim.VANISH_Y
        fun sxp(x: Float, z: Float) = cx + x * f / z
        fun syp(y: Float, z: Float) = hy - (y - CAM_H) * f / z
        val wallZ = 7.6f
        val gy = syp(0f, wallZ)
        val cy = syp(2.7f, wallZ)
        // 천장과 뒷벽
        drawRect(Color(0xFF0E1011), size = Size(w, cy + 1f))
        drawRect(Color(0xFF3A3D3E), topLeft = Offset(0f, cy), size = Size(w, gy - cy))
        val bandY = syp(1.15f, wallZ)
        drawRect(Color(0xFF2C3A36), topLeft = Offset(0f, bandY), size = Size(w, gy - bandY))
        drawRect(Color(0xFFC9CCC8), topLeft = Offset(0f, bandY - h * 0.004f), size = Size(w, h * 0.004f), alpha = 0.55f)
        drawRect(Color.Black, topLeft = Offset(0f, cy), size = Size(w, h * 0.02f), alpha = 0.35f)
        // 벽 이음매, 천장 보와 등
        for (i in -3..3) {
            val x = sxp(i * 2.6f + 1.3f, wallZ)
            drawLine(Color.Black, Offset(x, cy), Offset(x, gy), maxOf(1f, h * 0.0015f), alpha = 0.22f)
        }
        for (i in 0..1) {
            val z = 3.2f + i * 2.4f
            val y0 = syp(2.7f, z); val y1 = syp(2.25f, z)
            drawRect(Color(0xFF17191B), Offset(0f, y0), Size(w, y1 - y0))
            drawRect(Color.Black, Offset(0f, y1 - h * 0.004f), Size(w, h * 0.004f), alpha = 0.5f)
        }
        for (i in -1..1) {
            val z = 6.6f
            val x0 = sxp(i * 2.6f - 0.6f, z); val x1 = sxp(i * 2.6f + 0.6f, z); val y = syp(2.6f, z)
            glow(white, (x0 + x1) / 2f, y + h * 0.03f, (x1 - x0) * 1.3f, h * 0.11f, alpha = 0.22f)
            drawLine(Color(0xFFF4F7FA), Offset(x0, y), Offset(x1, y), maxOf(2f, 0.045f * f / z), cap = StrokeCap.Round)
        }
        // 바닥과 바닥에 비친 등
        drawRect(floorBrush!!, topLeft = Offset(0f, gy), size = Size(w, h - gy))
        for (i in -1..1) glow(white, sxp(i * 2.6f, 6.6f), syp(0f, 5.2f), w * 0.1f, h * 0.03f, alpha = 0.09f)
        // 주차선과 바퀴 멈춤턱
        for (i in -2..2) {
            if (i == 0) continue
            val x = (if (i < 0) i + 0.5f else i - 0.5f) * 2.6f
            path.rewind()
            path.moveTo(sxp(x - 0.06f, wallZ - 0.3f), syp(0f, wallZ - 0.3f)); path.lineTo(sxp(x + 0.06f, wallZ - 0.3f), syp(0f, wallZ - 0.3f))
            path.lineTo(sxp(x + 0.06f, 1.5f), syp(0f, 1.5f)); path.lineTo(sxp(x - 0.06f, 1.5f), syp(0f, 1.5f)); path.close()
            drawPath(path, Color(0xFFDADDD9), alpha = 0.7f)
        }
        val stopZ = 6.7f
        drawRect(Color(0xFF8A7A2C), Offset(sxp(-0.85f, stopZ), syp(0.12f, stopZ)), Size(sxp(0.85f, stopZ) - sxp(-0.85f, stopZ), syp(0f, stopZ) - syp(0.12f, stopZ)))
        // 기둥과 아래쪽 안전 띠
        for (i in -1..1 step 2) {
            val z = 6.9f
            val x0 = sxp(i * 3.9f - 0.35f, z); val x1 = sxp(i * 3.9f + 0.35f, z)
            val base = syp(0f, z)
            drawRect(Color(0xFF4A4D4E), Offset(x0, 0f), Size(x1 - x0, base))
            drawRect(Color.Black, Offset(if (i < 0) x1 - (x1 - x0) * 0.22f else x0, 0f), Size((x1 - x0) * 0.22f, base), alpha = 0.3f)
            val by0 = syp(1.1f, z)
            drawRect(Color(0xFFC9A227), Offset(x0, by0), Size(x1 - x0, base - by0))
            val stripe = (base - by0) / 5f
            for (s in 0 until 5 step 2) drawRect(Color(0xFF151617), Offset(x0, by0 + s * stripe), Size(x1 - x0, stripe * 0.5f))
        }
        // 벽에 닿은 내 전조등: 위가 반듯하게 잘린 두 덩이
        if (sim.lowBeam > 0.01f) {
            val by = syp(0.62f, wallZ)
            glow(beam, sxp(-0.72f, wallZ), by, w * 0.13f, h * 0.085f, alpha = sim.lowBeam * 0.6f)
            glow(beam, sxp(0.72f, wallZ), by, w * 0.13f, h * 0.085f, alpha = sim.lowBeam * 0.6f)
            glow(beam, cx, gy + h * 0.07f, w * 0.42f, h * 0.12f, alpha = sim.lowBeam * 0.28f)
        }
        if (sim.highBeam > 0.01f) glow(white, cx, syp(1.3f, wallZ), w * 0.4f, h * 0.2f, alpha = sim.highBeam * 0.45f)
    }

    private fun DrawScope.drawFogAndGlare(sim: SceneSim) {
        val fog = sim.fogOut
        if (fog > 0.01f) {
            val c = mix(Color(0xFF7E8489), Color(0xFFD9DEE2), 1f - sim.night)
            val bottom = fog * (0.66f - 0.42f * sim.fogLamp - 0.1f * sim.lowBeam).coerceAtLeast(0.1f)
            val top = fog * 0.97f
            drawRect(c, alpha = bottom)
            if (c != fogTintColor) { fogTintColor = c; fogTint = ColorFilter.tint(c, BlendMode.Modulate) }
            drawRect(fogGrad!!, alpha = (top - bottom).coerceAtLeast(0f), colorFilter = fogTint)
            // 전조등 빛이 안개에 부딪혀 앞이 뿌옇게 뜬다. 상향등이면 더 하얗게 번진다
            glow(fogBloom, cx, horizon + h * 0.12f, w * 0.5f, h * 0.3f, alpha = fog * sim.lowBeam * 0.22f)
            if (sim.highBeam > 0.01f) {
                drawRect(Color.White, alpha = fog * sim.highBeam * 0.2f)
                glow(fogBloom, cx, horizon + h * 0.05f, w * 0.8f, h * 0.55f, alpha = fog * sim.highBeam * 0.75f)
            }
        }
        if (sim.glare > 0.01f) drawRect(Color.White, alpha = sim.glare * sim.glare * 0.12f)
    }

    private fun DrawScope.drawStreaks(sim: SceneSim) {
        val rain = sim.rain
        if (rain < 0.03f || sim.parking > 0.5f) return
        val n = (SceneSim.MAX_STREAKS * (0.25f + 0.75f * rain) * (if (sim.lowDetail) 0.5f else 1f)).toInt()
        val rush = (sim.speed / 30f).coerceIn(0f, 1.3f)
        // 빗줄기는 전조등 빛을 받을 때 가장 잘 보인다
        val a = (0.09f + 0.2f * rain) * ((if (sim.photo) 0.95f else 0.65f) + 0.6f * sim.lowBeam) * (1f - 0.7f * sim.tunnelP)
        // 빗줄기마다 굵기·길이·밝기가 다르다. 머리 쪽이 조금 더 밝고 꼬리는 흐려진다
        val thin = maxOf(0.7f, h * 0.0007f)
        val c = Color(0xFFD3DCE8)
        for (k in 0 until n) {
            val x = sim.streakX[k]; val y = sim.streakY[k]
            val v = sim.streakV[k]
            val vx = ((x - 0.5f) * 2.6f * rush + 0.06f) * v
            val vy = ((y - SceneSim.VANISH_Y) * 2.6f * rush + 1.5f) * v
            val r = (k * 0.618034f) % 1f
            val len = 0.012f + 0.05f * r * r
            val ka = a * (0.2f + 0.8f * ((k * 0.381966f) % 1f))
            val sw = thin * (0.7f + 1.1f * r)
            val mx = (x - vx * len * 0.45f) * w; val my = (y - vy * len * 0.45f) * h
            drawLine(c, Offset(x * w, y * h), Offset(mx, my), sw, alpha = ka)
            drawLine(c, Offset(mx, my), Offset((x - vx * len) * w, (y - vy * len) * h), sw, alpha = ka * 0.4f)
        }
    }

    companion object {
        const val N = 44
        const val Z0 = 1.4f
        const val Z_MAX = 340f
        val RATIO: Float = (Z_MAX / Z0).pow(1f / (N - 1))
        const val CAM_H = 1.2f
        /** 가장자리 실선의 위치(차로 3개, 차로 폭 3.6m). */
        const val EDGE = 5.4f
        /** 오른쪽 갓길 끝과 가드레일. */
        const val SHOULDER = 7.1f
        const val RAIL = 7.1f
        const val TUBE_HALF = 6.0f
        const val TUBE_FAR = 230f
        const val PORTAL_FAR = 95f
        const val RING = 9f
        const val PROF = 11
        val PROF_X = floatArrayOf(-1f, -1f, -0.95f, -0.76f, -0.42f, 0f, 0.42f, 0.76f, 0.95f, 1f, 1f)
        val PROF_Y = floatArrayOf(0f, 2.6f, 4.3f, 5.6f, 6.4f, 6.7f, 6.4f, 5.6f, 4.3f, 2.6f, 0f)
        val PAINT = Color(0xFFE4E6E6)
        val PAINT_YELLOW = Color(0xFFE2B33C)
        /** 옆 차로 차들의 차체 색(흰색·은색·짙은 회색·검정 계열). */
        val BODIES = arrayOf(Color(0xFFD5D8DA), Color(0xFF3B3F45), Color(0xFF8E949A), Color(0xFF1F2226))
    }
}
