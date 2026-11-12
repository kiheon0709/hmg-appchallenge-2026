package io.moodlab.sideseat.app.measure

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacDefrosterStateListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

const val MEASURE_TAG = "SideSeatMeasure"

/**
 * 디버그 빌드 전용 실측 도구. 모든 신호를 구독해 로그로 남기고, adb 브로드캐스트로 set·음성 시험을 한다.
 *
 * 로그: `adb logcat -v epoch -s SideSeatMeasure`
 * 명령: `adb shell am broadcast -a io.moodlab.sideseat.MEASURE --es cmd <cmd> [--es arg <arg>]`
 *   cmd = ambient <argb hex> | defrost on/off | ac on/off | recirc on/off | window <0~100> | wiperpoll <ms>
 *         | tts <문장> | stt | tts_stt <문장> | stt_pcm <assets 경로 또는 /sdcard 경로> | dump
 */
class SignalProbe(private val context: Context) {
    private val vehicle: Vehicle? = runCatching { Vehicle(context).also { it.initialize() } }
        .onFailure { log("vehicle init failed: $it") }.getOrNull()
    private val main = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var seatAreas: List<SeatArea> = emptyList()
    private var defrostAreas: List<WindowArea> = emptyList()
    private var ambientAreas: List<SeatArea> = emptyList()
    private val driverWindow = WindowArea(WindowValue.ROW_1_LEFT)
    private val frontWindshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private var wiperPollMs = 0L
    private var lastWiper: String? = null
    val voice = VoiceProbe(context)

    private fun log(msg: String) { Log.i(MEASURE_TAG, "t=${SystemClock.elapsedRealtime()} $msg") }
    private fun fail(what: String) = { e: Exception -> log("FAIL $what ${e.javaClass.simpleName}: ${e.message}") }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = command(i.getStringExtra("cmd").orEmpty(), i.getStringExtra("arg").orEmpty())
    }

    fun start() {
        val v = vehicle ?: return
        listOf(::subscribeLights, ::subscribeMotion, ::subscribeClimate, ::subscribeBody).forEach { f ->
            runCatching { f(v) }.onFailure { log("FAIL ${'$'}{f.name} threw $it") }
        }
        subscribeCarApi()
        val filter = IntentFilter("io.moodlab.sideseat.MEASURE")
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else context.registerReceiver(receiver, filter)
        log("probe started")
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        runCatching { car?.disconnect() }
        voice.release()
    }

    // ---------- 구독 ----------

    private fun lightListener(name: String) = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) = log("sig $name=$state")
        override fun onFailed(e: Exception) = fail("$name listener")(e)
    }

    private fun subscribeLights(v: Vehicle) {
        val light = v.getLight()
        light.registerLowBeamLightState(lightListener("lowBeam"))
        light.registerHighBeamLightState(lightListener("highBeam"))
        light.registerFrontFogLightState(lightListener("frontFog"))
        light.registerRearFogLightState(lightListener("rearFog"))
        light.checkFrontFogLightStateCapabilities({ c, s: List<LightState> -> log("cap frontFog=$c states=$s") }, fail("cap frontFog"))
        light.checkRearFogLightStateCapabilities({ c, s -> log("cap rearFog=$c states=$s") }, fail("cap rearFog"))
        light.checkHighBeamLightStateCapabilities({ c, s -> log("cap highBeam=$c states=$s") }, fail("cap highBeam"))
        light.checkAmbientLightBrightnessColorCapabilities({ c, areas ->
            ambientAreas = areas
            log("cap ambient=$c areas=${areas.map { it.values.toList() }}")
        }, fail("cap ambient"))
        v.getTurnSignal().registerTurnSignalState(object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = log("sig turnSignal=$state")
            override fun onFailed(e: Exception) = fail("turnSignal listener")(e)
        })
        v.getTurnSignal().checkTurnSignalStateCapabilities({ log("cap turnSignal=$it") }, fail("cap turnSignal"))
        // registerTelltales는 PRIVATE_CAR_SAFETY(signature|privileged)라 일반 앱은 SecurityException (실측 11-13)
        runCatching {
            v.getSafety().registerTelltales(object : TelltaleInfoListener {
                override fun onTelltaleInfoUpdated(t: Telltales?) = log("sig telltales=${t.describe()}")
                override fun onFailed(e: Exception) = fail("telltale listener")(e)
            })
        }.onFailure { log("FAIL registerTelltales threw $it") }
        v.getSafety().checkTelltalesCapabilities({ log("cap telltales=$it") }, fail("cap telltales"))
    }

    private fun Telltales?.describe(): String =
        this?.telltales?.filter { it.state.toString() != "OFF" }
            ?.joinToString(prefix = "[", postfix = "]") { "${it.type}:${it.state}:c${it.color}" } ?: "null"

    private fun subscribeMotion(v: Vehicle) {
        v.getBrake().registerBrakePedalStatus(object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(pressed: Boolean?) = log("sig brake=$pressed")
            override fun onFailed(e: Exception) = fail("brake listener")(e)
        })
        v.getBrake().checkBrakePedalStatusCapabilities({ log("cap brake=$it") }, fail("cap brake"))
        v.getSteeringWheel().registerSteeringWheelAngle(object : SteeringWheelAngleListener {
            override fun onSteeringWheelAngleUpdated(deg: Float?) = log("sig steering=$deg")
            override fun onFailed(e: Exception) = fail("steering listener")(e)
        })
        v.getSteeringWheel().checkSteeringWheelAngleCapabilities({ c, b -> log("cap steering=$c bounds=$b") }, fail("cap steering"))
    }

    private fun subscribeClimate(v: Vehicle) {
        val hvac = v.getHVAC()
        hvac.checkHvacDefrosterCapabilities({ c: Any?, areas: List<WindowArea> ->
            defrostAreas = areas
            log("cap defroster=$c areas=${areas.map { it.values.toList() }}")
            areas.forEach { a ->
                hvac.registerHvacDefrosterState(a, object : HvacDefrosterStateListener {
                    override fun onHvacDefrosterStateUpdated(on: Boolean?) = log("sig defroster${a.values.toList()}=$on")
                    override fun onFailed(e: Exception) = fail("defroster listener")(e)
                })
            }
        }, fail("cap defroster"))
        hvac.checkHvacAcCapabilities({ c: Any?, areas: List<SeatArea> ->
            seatAreas = areas
            log("cap ac=$c areas=${areas.map { it.values.toList() }}")
            areas.forEach { a ->
                hvac.registerHvacAcState(a, object : HvacAcStateListener {
                    override fun onHvacAcStateUpdated(on: Boolean?) = log("sig ac${a.values.toList()}=$on")
                    override fun onFailed(e: Exception) = fail("ac listener")(e)
                })
            }
        }, fail("cap ac"))
        hvac.checkHvacRecirculationCapabilities({ c: Any?, areas: List<SeatArea> ->
            log("cap recirc=$c areas=${areas.map { it.values.toList() }}")
            areas.forEach { a ->
                hvac.registerHvacRecirculationState(a, object : HvacRecirculationStateListener {
                    override fun onHvacAutoRecirculationStateUpdated(on: Boolean?) = log("sig recirc${a.values.toList()}=$on")
                    override fun onFailed(e: Exception) = fail("recirc listener")(e)
                })
            }
        }, fail("cap recirc"))
        hvac.checkHvacMaxDefrostCapabilities({ c: Any?, areas: List<SeatArea> -> log("cap maxDefrost=$c areas=${areas.map { it.values.toList() }}") }, fail("cap maxDefrost"))
    }

    private fun subscribeBody(v: Vehicle) {
        v.getWindow().checkWindowPositionCapabilities({ c: Any?, m: Map<WindowArea, *> ->
            log("cap window=$c areas=${m.keys.map { it.values.toList() }}")
            m.keys.forEach { a ->
                v.getWindow().registerWindowPosition(a, object : WindowPositionListener {
                    override fun onWindowPositionUpdated(pos: Int?) = log("sig window${a.values.toList()}=$pos")
                    override fun onFailed(e: Exception) = fail("window listener")(e)
                })
            }
        }, fail("cap window"))
        v.getWiper().registerWiperState(frontWindshield, object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) {
                log("sig wiperState=$state")
                readWiperSwitch("listener")
            }
            override fun onFailed(e: Exception) = fail("wiper listener")(e)
        })
        listOf(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT).forEach { s ->
            v.getSeat().registerSeatOccupancy(SeatArea(s), object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(occupied: Boolean?) = log("sig occupied[$s]=$occupied")
                override fun onFailed(e: Exception) = fail("occupancy listener $s")(e)
            })
        }
    }

    private fun readWiperSwitch(why: String) {
        val t0 = SystemClock.elapsedRealtime()
        vehicle?.getWiper()?.getCurrentWiperSwitch(frontWindshield, { sw ->
            val s = sw.toString()
            if (s != lastWiper || why != "poll") log("sig wiperSwitch=$s via=$why took=${SystemClock.elapsedRealtime() - t0}ms")
            lastWiper = s
        }, fail("wiper switch"))
    }

    private val wiperPoll = object : Runnable {
        override fun run() {
            if (wiperPollMs <= 0) return
            readWiperSwitch("poll")
            main.postDelayed(this, wiperPollMs)
        }
    }

    private val propCallback = object : CarPropertyManager.CarPropertyEventCallback {
        private var lastSpeed = -1f
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED) {
                val v = value.value as? Float ?: return
                if (kotlin.math.abs(v - lastSpeed) < 0.5f) return
                lastSpeed = v
            }
            log("sig car.${VehiclePropertyIds.toString(value.propertyId)}=${value.value}")
        }
        override fun onErrorEvent(propId: Int, zone: Int) = log("FAIL car prop $propId zone=$zone")
    }

    private fun subscribeCarApi() = runCatching {
        val c = Car.createCar(context) ?: error("no car")
        car = c
        val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        listOf(VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.PARKING_BRAKE_ON, VehiclePropertyIds.PERF_VEHICLE_SPEED,
            VehiclePropertyIds.HAZARD_LIGHTS_STATE, VehiclePropertyIds.TURN_SIGNAL_STATE, VehiclePropertyIds.FOG_LIGHTS_STATE,
            VehiclePropertyIds.HEADLIGHTS_STATE, VehiclePropertyIds.NIGHT_MODE)
            .forEach { id ->
                val ok = runCatching { pm.registerCallback(propCallback, id, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
                log("car subscribe ${VehiclePropertyIds.toString(id)} -> $ok")
            }
        val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
        ux.registerListener { r -> log("sig ux=${r.describe()}") }
        log("ux initial=${ux.currentCarUxRestrictions?.describe()}")
    }.onFailure { log("FAIL car api $it") }

    private fun CarUxRestrictions.describe() =
        "requiresDO=${isRequiresDistractionOptimization} active=0x${Integer.toHexString(activeRestrictions)}"

    // ---------- 명령 ----------

    private fun command(cmd: String, arg: String) {
        log("cmd $cmd $arg")
        val v = vehicle ?: return
        val on = arg == "on"
        when (cmd) {
            "ambient" -> {
                val argb = arg.toLong(16).toInt()
                val area = ambientAreas.firstOrNull() ?: return log("FAIL ambient no area")
                v.getLight().setAmbientLightBrightnessColor(area, argb,
                    { _, value -> log("set ambient ok value=${Integer.toHexString(value)}") }, fail("set ambient"))
            }
            "defrost" -> defrostAreas.firstOrNull()?.let { a ->
                v.getHVAC().setHvacDefroster(a, on, { _, r -> log("set defrost ok=$r") }, fail("set defrost"))
            }
            "ac" -> seatAreas.firstOrNull()?.let { a ->
                v.getHVAC().setHvacAc(a, on, { _, r -> log("set ac ok=$r") }, fail("set ac"))
            }
            "recirc" -> seatAreas.firstOrNull()?.let { a ->
                v.getHVAC().setHvacRecirculation(a, on, { _, r -> log("set recirc ok=$r") }, fail("set recirc"))
            }
            "window" -> v.getWindow().setWindowPosition(driverWindow, arg.toInt(), { _, r -> log("set window ok=$r") }, fail("set window"))
            "wiperpoll" -> {
                wiperPollMs = arg.toLongOrNull() ?: 0
                main.removeCallbacks(wiperPoll)
                main.post(wiperPoll)
            }
            "dump" -> dump(v)
            else -> voice.command(cmd, arg)
        }
    }

    private fun dump(v: Vehicle) {
        v.getBrake().getCurrentBrakePedalStatus({ log("get brake=$it") }, fail("get brake"))
        v.getSteeringWheel().getCurrentSteeringWheelAngle({ log("get steering=$it") }, fail("get steering"))
        v.getTurnSignal().getCurrentTurnSignalState({ log("get turnSignal=$it") }, fail("get turnSignal"))
        runCatching { v.getSafety().getTelltalesInfo({ log("get telltales=${it.describe()}") }, fail("get telltales")) }
            .onFailure { log("FAIL getTelltalesInfo threw $it") }
        v.getLight().getCurrentLowBeamLightState({ log("get lowBeam=$it") }, fail("get lowBeam"))
        v.getLight().getCurrentHighBeamLightState({ log("get highBeam=$it") }, fail("get highBeam"))
        v.getLight().getCurrentFrontFogLightState({ log("get frontFog=$it") }, fail("get frontFog"))
        readWiperSwitch("dump")
        defrostAreas.forEach { a -> v.getHVAC().getHvacDefrosterState(a, { log("get defroster${a.values.toList()}=$it") }, fail("get defroster")) }
        seatAreas.forEach { a ->
            v.getHVAC().getHvacAcState(a, { log("get ac${a.values.toList()}=$it") }, fail("get ac"))
            v.getHVAC().getHvacRecirculationState(a, { log("get recirc${a.values.toList()}=$it") }, fail("get recirc"))
        }
        v.getWindow().getCurrentWindowPosition(driverWindow, { log("get window=$it") }, fail("get window"))
        v.getCarInfo().getVINInfo({ log("get vin=$it") }, fail("get vin"))
    }
}
