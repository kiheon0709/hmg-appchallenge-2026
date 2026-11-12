package io.moodlab.sideseat.ui.scene

import io.moodlab.sideseat.core.SceneParams
import io.moodlab.sideseat.core.TrainingPhase
import io.moodlab.sideseat.core.TrainingView
import io.moodlab.sideseat.core.TurnSignal
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.core.WiperLevel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/** 화면이 받은 최신 값. 재구성 때마다 덮어쓰고, 프레임 루프가 읽는다. */
internal class SceneInput {
    var night = true
    var rain = 0f
    var fogOutside = 0f
    var windshieldFog = 0f
    var tunnel = false
    var leadDistance = -1f // 0..1, 음수면 앞차 없음
    var leadBraking = false
    var glare = 0f
    var debris = 0f
    var alert = 0f
    var warningLight: String? = null
    var parkingLot = false
    var drowsy = 0f
    var speedKmh = 0f
    var success = false

    var wiper = WiperLevel.OFF
    var lowBeam = false
    var highBeam = false
    var fogLamp = false
    var turn = TurnSignal.OFF
    var brake = false
    var steerDeg = 0f
    var parkingBrake = false
    var defrost = false
    var telltaleWarn = false

    fun set(view: TrainingView, v: VehicleState) {
        set(view.scene, v, view.virtualSpeedKmh, view.phase == TrainingPhase.SUCCESS)
    }

    fun set(s: SceneParams, v: VehicleState, speed: Float, successNow: Boolean) {
        night = s.night; rain = s.rain; fogOutside = s.fogOutside; windshieldFog = s.windshieldFog
        tunnel = s.tunnel; leadDistance = s.leadCarDistance ?: -1f; leadBraking = s.leadCarBraking
        glare = s.oncomingGlare; debris = s.debris; alert = s.alert; warningLight = s.warningLight
        parkingLot = s.parkingLot; drowsy = s.drowsyBlur; speedKmh = speed; success = successNow
        wiper = v.wiper ?: WiperLevel.OFF
        lowBeam = v.lowBeamOn == true; highBeam = v.highBeamOn == true; fogLamp = v.anyFogOn == true
        turn = v.turnSignal ?: TurnSignal.OFF; brake = v.brakePressed == true
        steerDeg = v.steeringAngleDeg ?: 0f; parkingBrake = v.parkingBrakeOn == true
        defrost = v.defrostFrontOn == true
        telltaleWarn = v.activeTelltales.keys.any { it.contains("TIRE") || it.contains("TPMS") }
    }
}

/**
 * 주행 화면의 움직이는 상태. 프레임마다 [step]으로 전진시키고, 그리는 쪽은 읽기만 한다.
 * 매 프레임 할당이 없도록 입자는 전부 고정 길이 배열이다.
 */
internal class SceneSim {
    var t = 0f
    var camS = 0f
    var speed = 0f // m/s
    var heading = 0f
    var playerX = 0f
    var steerVis = 0f

    // 부드럽게 따라가는 장면 값
    var night = 1f
    var rain = 0f
    var fogOut = 0f
    var wsFog = 0f
    var wsFogGrowing = true
    var tunnelP = 0f
    var tunnelEntering = false
    var leadZ = 60f
    var leadA = 0f
    var leadBrake = 0f
    var glare = 0f
    var debris = 0f
    var alert = 0f
    var success = 0f
    var drowsy = 0f
    var parking = 0f
    var lowBeam = 0f
    var highBeam = 0f
    var fogLamp = 0f
    var brake = 0f
    var warn = 0f

    // 와이퍼
    var wipeA = 0f // 0 = 쉬는 자리, 1 = 끝까지 올라감
    private var wipePhase = 0f
    private var wipeActive = false
    private var wipeWait = 0f
    private var lastWiper = WiperLevel.OFF
    val wet = FloatArray(WIPE_BINS)
    val clearedAt = FloatArray(WIPE_BINS) { -100f }
    var ambientWet = 0f

    // 앞유리 물방울
    val dropX = FloatArray(MAX_DROPS)
    val dropY = FloatArray(MAX_DROPS)
    val dropR = FloatArray(MAX_DROPS)
    val dropBorn = FloatArray(MAX_DROPS) { -100f }
    val dropLife = FloatArray(MAX_DROPS)
    val dropBin = IntArray(MAX_DROPS) { -1 }
    private var dropCursor = 0
    private var dropDebt = 0f

    // 바깥 빗줄기 (0..1 정규화 좌표)
    val streakX = FloatArray(MAX_STREAKS)
    val streakY = FloatArray(MAX_STREAKS)
    val streakV = FloatArray(MAX_STREAKS)

    // 번개
    var flash = 0f
    var boltSeed = 0
    private var nextFlash = 3f

    // 주변 차 (z: 카메라 앞 거리, lane: -1 왼쪽 / +1 오른쪽)
    val carZ = FloatArray(AMBIENT_CARS)
    val carLane = IntArray(AMBIENT_CARS)
    val carV = FloatArray(AMBIENT_CARS)

    // 맞은편 차
    val onZ = FloatArray(ONCOMING) { -1f }
    private var onWait = 1f

    // 앞유리 기하 (그리는 쪽이 크기를 알려 준다)
    var w = 0f
    var h = 0f
    var dashY = 0f
    var pivAx = 0f
    var pivBx = 0f
    var pivY = 0f
    var bladeL = 0f

    private var seed = 0x2545F491

    init {
        for (i in 0 until MAX_STREAKS) { streakX[i] = rnd(); streakY[i] = rnd(); streakV[i] = 0.7f + rnd() * 0.6f }
        for (i in 0 until AMBIENT_CARS) {
            carLane[i] = if (i % 2 == 0) 1 else -1
            carZ[i] = 40f + i * 70f
            carV[i] = if (carLane[i] < 0) 31f + rnd() * 3f else 21f + rnd() * 3f
        }
    }

    fun rnd(): Float {
        seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
        return (seed and 0xFFFFFF) / 16777216f
    }

    fun setSize(width: Float, height: Float) {
        if (width == w && height == h) return
        w = width; h = height
        dashY = height * DASH_Y
        bladeL = minOf(width * 0.38f, dashY * 0.9f)
        pivAx = width * 0.42f
        pivBx = pivAx + bladeL
        pivY = dashY + height * 0.004f
        for (i in 0 until MAX_DROPS) dropBorn[i] = -100f
    }

    fun step(dtRaw: Float, i: SceneInput) {
        val dt = dtRaw.coerceIn(0f, 0.1f)
        t += dt
        speed = ease(speed, i.speedKmh / 3.6f, 2.2f, dt)
        camS += speed * dt
        heading += curvature(camS) * speed * dt
        playerX = ease(playerX, (i.steerDeg / 120f).coerceIn(-1f, 1f) * 1.6f, 4f, dt)
        steerVis = ease(steerVis, i.steerDeg, 14f, dt)

        night = ease(night, if (i.night) 1f else 0f, 1.5f, dt)
        rain = ease(rain, i.rain, 1.4f, dt)
        fogOut = ease(fogOut, i.fogOutside, 1.2f, dt)
        if (i.windshieldFog > wsFog + 0.003f) wsFogGrowing = true else if (i.windshieldFog < wsFog - 0.003f) wsFogGrowing = false
        wsFog = ease(wsFog, i.windshieldFog, 3.5f, dt)
        glare = ease(glare, i.glare, 3f, dt)
        debris = ease(debris, i.debris, 3f, dt)
        alert = ease(alert, i.alert, 5f, dt)
        success = ease(success, if (i.success) 1f else 0f, if (i.success) 8f else 1.5f, dt)
        drowsy = ease(drowsy, i.drowsy, 1.5f, dt)
        parking = ease(parking, if (i.parkingLot) 1f else 0f, 2.5f, dt)
        lowBeam = ease(lowBeam, if (i.lowBeam || i.highBeam) 1f else 0f, 9f, dt)
        highBeam = ease(highBeam, if (i.highBeam) 1f else 0f, 9f, dt)
        fogLamp = ease(fogLamp, if (i.fogLamp) 1f else 0f, 9f, dt)
        brake = ease(brake, if (i.brake) 1f else 0f, 12f, dt)
        warn = ease(warn, if (i.warningLight != null || i.telltaleWarn) 1f else 0f, 6f, dt)

        // 터널: 입구·출구가 1.6초에 걸쳐 다가와 지나간다
        tunnelEntering = i.tunnel
        tunnelP = (tunnelP + (if (i.tunnel) dt else -dt) / 1.6f).coerceIn(0f, 1f)

        // 앞차
        if (i.leadDistance >= 0f) {
            val target = 5.5f + i.leadDistance * 85f
            leadZ = if (leadA < 0.02f) target else ease(leadZ, target, 6f, dt)
            leadA = ease(leadA, 1f, 3f, dt)
        } else {
            leadA = ease(leadA, 0f, 2f, dt)
            leadZ += 12f * dt
        }
        leadBrake = ease(leadBrake, if (i.leadBraking) 1f else 0f, 14f, dt)

        stepWiper(dt, i.wiper)
        stepWater(dt)
        stepStreaks(dt)
        stepLightning(dt)
        stepTraffic(dt)
    }

    private fun stepWiper(dt: Float, level: WiperLevel) {
        if (level != lastWiper) {
            if (level != WiperLevel.OFF && !wipeActive) { wipeActive = true; wipePhase = 0f }
            wipeWait = 0f
            lastWiper = level
        }
        val period = when (level) {
            WiperLevel.HIGH -> 0.72f
            WiperLevel.LOW -> 1.35f
            else -> 1.2f
        }
        val prev = wipeA
        if (wipeActive) {
            wipePhase += dt / period
            if (wipePhase >= 1f) {
                when (level) {
                    WiperLevel.HIGH, WiperLevel.LOW -> wipePhase -= 1f
                    WiperLevel.INTERMITTENT -> { wipePhase = 0f; wipeActive = false; wipeWait = 2.6f }
                    else -> { wipePhase = 0f; wipeActive = false }
                }
            }
        } else if (level == WiperLevel.INTERMITTENT) {
            wipeWait -= dt
            if (wipeWait <= 0f) wipeActive = true
        }
        wipeA = 0.5f - 0.5f * cos(wipePhase * 2f * PI.toFloat())
        if (wipeA != prev) {
            val a = (minOf(prev, wipeA) * WIPE_BINS).toInt().coerceIn(0, WIPE_BINS - 1)
            val b = (maxOf(prev, wipeA) * WIPE_BINS).toInt().coerceIn(0, WIPE_BINS - 1)
            for (k in a..b) { wet[k] = 0f; clearedAt[k] = t }
        }
    }

    private fun stepWater(dt: Float) {
        val grow = rain * dt * 1.5f
        for (k in 0 until WIPE_BINS) {
            wet[k] = if (rain > 0.03f) minOf(rain.coerceAtLeast(0.25f), wet[k] + grow) else maxOf(0f, wet[k] - dt * 0.25f)
        }
        ambientWet = if (rain > ambientWet) ease(ambientWet, rain, 1.2f, dt) else ease(ambientWet, rain, 0.35f, dt)
        if (w <= 0f) return
        dropDebt += rain * rain * 160f * dt + (if (rain > 0.05f) 6f * dt else 0f)
        while (dropDebt >= 1f) {
            dropDebt -= 1f
            val n = dropCursor
            dropCursor = (dropCursor + 1) % MAX_DROPS
            val x = rnd() * w
            val y = rnd() * dashY
            dropX[n] = x; dropY[n] = y
            dropR[n] = h * (0.006f + rnd() * rnd() * 0.022f)
            dropBorn[n] = t
            dropLife[n] = 2.5f + rnd() * 5f
            dropBin[n] = binAt(x, y)
        }
    }

    /** 이 점을 닦는 와이퍼 구간 번호. 닦이지 않는 자리는 -1. */
    private fun binAt(x: Float, y: Float): Int {
        val a = binFor(x - pivAx, pivY - y)
        return if (a >= 0) a else binFor(x - pivBx, pivY - y)
    }

    private fun binFor(dx: Float, dy: Float): Int {
        val r = hypot(dx, dy)
        if (r < bladeL * BLADE_INNER || r > bladeL) return -1
        val deg = atan2(dy, dx) * 180f / PI.toFloat()
        if (deg > WIPE_REST_DEG || deg < WIPE_END_DEG) return -1
        return ((WIPE_REST_DEG - deg) / (WIPE_REST_DEG - WIPE_END_DEG) * WIPE_BINS).toInt().coerceIn(0, WIPE_BINS - 1)
    }

    /** 물방울이 지금 보이는 정도(0이면 그리지 않음). */
    fun dropAlpha(n: Int): Float {
        val born = dropBorn[n]
        val age = t - born
        if (age < 0f || age > dropLife[n]) return 0f
        val bin = dropBin[n]
        if (bin >= 0 && clearedAt[bin] > born) return 0f
        val fadeIn = (age / 0.12f).coerceAtMost(1f)
        val fadeOut = ((dropLife[n] - age) / 0.8f).coerceAtMost(1f)
        return fadeIn * fadeOut
    }

    private fun stepStreaks(dt: Float) {
        if (rain < 0.02f) return
        val rush = (speed / 30f).coerceIn(0f, 1.3f)
        for (k in 0 until MAX_STREAKS) {
            val dx = streakX[k] - 0.5f
            val dy = streakY[k] - VANISH_Y
            streakX[k] += (dx * 2.6f * rush + 0.06f) * streakV[k] * dt
            streakY[k] += (dy * 2.6f * rush + 1.5f) * streakV[k] * dt
            if (streakY[k] > 1.05f || streakX[k] < -0.05f || streakX[k] > 1.05f) {
                streakX[k] = rnd()
                streakY[k] = if (rush > 0.3f) VANISH_Y + (rnd() - 0.5f) * 0.5f else -0.05f - rnd() * 0.2f
                if (rush > 0.3f) streakX[k] = 0.5f + (rnd() - 0.5f) * 0.9f
            }
        }
    }

    private fun stepLightning(dt: Float) {
        flash = maxOf(0f, flash - dt * (if (flash > 0.6f) 9f else 2.2f))
        if (rain > 0.6f && parking < 0.5f && tunnelP < 0.5f) {
            nextFlash -= dt
            if (nextFlash <= 0f) { flash = 1f; boltSeed++; nextFlash = 3.5f + rnd() * 5f }
        }
    }

    private fun stepTraffic(dt: Float) {
        for (k in 0 until AMBIENT_CARS) {
            carZ[k] += (carV[k] - speed) * dt
            if (carZ[k] > 420f) { carZ[k] = if (carV[k] > speed) -8f - rnd() * 60f else 420f }
            if (carZ[k] < -80f) { carZ[k] = if (carV[k] > speed) -80f else 320f + rnd() * 100f }
        }
        onWait -= dt
        for (k in 0 until ONCOMING) {
            if (onZ[k] > 0f) {
                onZ[k] -= (speed + 27f) * dt
            } else if (onWait <= 0f) {
                onZ[k] = 380f; onWait = 1.2f + rnd() * 3.5f
            }
        }
    }

    companion object {
        const val WIPE_BINS = 20
        const val MAX_DROPS = 150
        const val MAX_STREAKS = 110
        const val AMBIENT_CARS = 4
        const val ONCOMING = 3
        const val DASH_Y = 0.76f
        const val VANISH_Y = 0.42f
        const val WIPE_REST_DEG = 177f
        const val WIPE_END_DEG = 64f
        const val BLADE_INNER = 0.14f

        fun ease(cur: Float, target: Float, rate: Float, dt: Float): Float = cur + (target - cur) * (1f - exp(-rate * dt))

        /** 도로의 굽음(1/m). 거리의 함수라 구간 배열이 필요 없다. */
        fun curvature(s: Float): Float = 0.0011f * sin(s / 410f) + 0.0007f * sin(s / 137f + 1.3f)

        fun hill(s: Float): Float = 7f * sin(s / 520f) + 3f * sin(s / 190f + 0.7f)
    }
}
