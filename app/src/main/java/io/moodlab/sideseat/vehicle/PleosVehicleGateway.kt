package io.moodlab.sideseat.vehicle

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacDefrosterStateListener
import ai.pleos.playground.vehicle.listener.HvacMaxDefrostStateListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import io.moodlab.sideseat.core.AmbientMood
import io.moodlab.sideseat.core.ParkedInputs
import io.moodlab.sideseat.core.ParkedSource
import io.moodlab.sideseat.core.Seat
import io.moodlab.sideseat.core.SolarTimes
import io.moodlab.sideseat.core.LocationTrack
import io.moodlab.sideseat.core.RoadSpeedLimits
import io.moodlab.sideseat.core.RoadWay
import io.moodlab.sideseat.core.RouteFeatures
import io.moodlab.sideseat.core.SpeedLimitSource
import io.moodlab.sideseat.core.TelltaleColor
import io.moodlab.sideseat.core.TurnSignal
import io.moodlab.sideseat.core.TurnSignalResolver
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.core.WiperLevel
import io.moodlab.sideseat.core.telltaleColorOf
import io.moodlab.sideseat.core.vehicleModelKeyFromVin
import io.moodlab.sideseat.core.windowOpenPercent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.TimeZone
import kotlin.coroutines.resume

/**
 * 실차·에뮬레이터 신호. 값은 [GatewayConfig]의 실측 보정을 거친다.
 * 에뮬레이터 실측(docs/STATUS.md 11-13): 조향 부호 반대, 착좌 반대, 창문 0~10, 안개등 미지원, 비상등·경고등 읽기 불가.
 * 정차 판정은 주행 규제(CarUxRestrictions)로 한다. AAOS 기어·차속은 쓰지 않는다.
 * 속도·방향은 FusedLocation 좌표로 계산하고, 제한속도는 내비 값 → 없으면 OpenStreetMap maxspeed(명세 6.4).
 */
data class GatewayConfig(
    /** 와이퍼가 켜져 있는 동안 스위치 조회 주기. 단계만 바뀌면 알림이 없다(실측). 200ms 조회에서 지연 30~160ms. */
    val wiperPollMs: Long = 200,
    /** SDK 조향각 부호가 VC와 반대(VC +45 → SDK -45). 앱은 오른쪽 +로 쓴다. */
    val invertSteering: Boolean = true,
    /** 에뮬레이터는 착좌가 반대로 온다(Occupied → false). */
    val invertOccupancy: Boolean = isEmulator(),
    /** 정차 판정 순서. 주행 규제만 쓴다(주차 브레이크 경고등은 권한이 있는 차에서만 보조로). */
    val parkedOrder: List<ParkedSource> = listOf(ParkedSource.UX_RESTRICTIONS, ParkedSource.PARKING_BRAKE),
    /** 내비 제한속도가 0일 때 OpenStreetMap maxspeed로 대신한다(명세 6.4). 밖으로 보내는 것은 좌표 칸(bbox)뿐. */
    val useMapSpeedLimit: Boolean = true,
    val overpassUrl: String = "https://overpass-api.de/api/interpreter",
    /** 조회 단위 격자(도). 0.005도 ≈ 남북 550m·동서 440m라 이 정도 이동마다 한 번 묻는다. */
    val mapCellDeg: Double = 0.005,
    /** 외부 조회 사이 최소 간격. */
    val mapMinIntervalMs: Long = 10_000,
    /** 실패한 칸을 다시 묻기까지. */
    val mapRetryMs: Long = 60_000,
    val mapTimeoutMs: Int = 6_000,
    /** 내비 제한속도가 이 시간 안에 왔으면 유효. */
    val naviLimitFreshMs: Long = 3_000,
) {
    companion object {
        fun isEmulator(): Boolean =
            Build.FINGERPRINT.contains("generic") || Build.HARDWARE.contains("ranchu") ||
                Build.HARDWARE.contains("goldfish") || Build.PRODUCT.contains("sdk")
    }
}

/** 정차 판정에 쓴 원시 신호. 디버그 화면·실측 로그용. */
data class ParkedDiagnostics(val inputs: ParkedInputs, val parked: Boolean, val source: ParkedSource?)

class PleosVehicleGateway(
    private val context: Context,
    private val config: GatewayConfig = GatewayConfig(),
) : VehicleGateway {

    /** 콜백은 여러 스레드에서 오므로 원시 값은 이 객체 잠금 안에서만 바꾼다. */
    private data class Raw(
        val wiperState: WiperState? = null,
        val wiper: WiperLevel? = null,
        val lowBeam: Boolean? = null,
        val highBeam: Boolean? = null,
        val frontFog: Boolean? = null,
        val rearFog: Boolean? = null,
        val turnApi: TurnSignal? = null,
        val brake: Boolean? = null,
        val steering: Float? = null,
        val defroster: Boolean? = null,
        val maxDefrost: Boolean? = null,
        val ac: Boolean? = null,
        val recirc: Boolean? = null,
        val windows: Map<Seat, Int> = emptyMap(),
        val occupied: Map<Seat, Boolean> = emptyMap(),
        val telltales: Map<String, Pair<Boolean, TelltaleColor>> = emptyMap(),
        val telltalesKnown: Boolean = false,
        val uxRestricted: Boolean? = null,
        val locationSpeedKmh: Float? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val locationTimeMs: Long? = null,
        val naviLimit: Int? = null,
        val naviLimitAtMs: Long = 0,
        val mapLimit: Int? = null,
        val sunrise: Int? = null,
        val sunset: Int? = null,
        val modelKey: String? = null,
    )

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private val lock = Any()
    private var raw = Raw()
    private val turnResolver = TurnSignalResolver()

    private val state = MutableStateFlow(VehicleState(timestampMs = System.currentTimeMillis(), parked = false, minuteOfDay = nowMinute()))
    override val states: StateFlow<VehicleState> = state.asStateFlow()

    private val parkedDiag = MutableStateFlow(ParkedDiagnostics(ParkedInputs(), false, null))
    val parkedDiagnostics: StateFlow<ParkedDiagnostics> = parkedDiag.asStateFlow()

    private val remaining = MutableStateFlow<Int?>(null)
    override val navRemainingMinutes: StateFlow<Int?> = remaining.asStateFlow()
    private val arrived = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val arrivals: SharedFlow<Unit> = arrived.asSharedFlow()
    private val route = MutableStateFlow<RouteFeatures?>(null)
    override val routeFeatures: StateFlow<RouteFeatures?> = route.asStateFlow()

    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var navi: NaviHelper? = null
    private var fused: FusedLocation? = null
    private val track = LocationTrack()
    private var lastFixCallbackMs: Long? = null
    private var lastFixStampMs: Long? = null
    private val mapCells = object : LinkedHashMap<Pair<Int, Int>, List<RoadWay>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, List<RoadWay>>?) = size > 48
    }
    private val mapFailedAt = mutableMapOf<Pair<Int, Int>, Long>()
    private var mapInFlight: Pair<Int, Int>? = null
    private var mapLastRequestMs = 0L
    /** 앱에 넣어 둔 칸(지우지 않음). */
    private val staticCells = mutableMapOf<Pair<Int, Int>, List<RoadWay>>()
    private var preloaded = false
    private val unregister = mutableListOf<() -> Unit>()
    private var started = false

    private val frontWindshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private var defrostArea: WindowArea = frontWindshield
    private var ambientArea: SeatArea? = null
    private var ambientBeforeColor: Int? = null
    private var ambientBeforeBrightness: Int? = null

    // ---------- 수명 ----------

    override fun start() {
        if (started) return
        started = true
        val v = runCatching { Vehicle(context).also { it.initialize() } }
            .onFailure { log("FAIL vehicle init $it") }.getOrNull()
        vehicle = v
        if (v != null) {
            listOf(
                "wiper" to ::startWiper, "lights" to ::startLights, "turn" to ::startTurnSignal,
                "brake" to ::startBrake, "steering" to ::startSteering, "hvac" to ::startHvac,
                "windows" to ::startWindows, "seats" to ::startSeats, "telltales" to ::startTelltales,
                "carInfo" to ::startCarInfo, "ambient" to ::prepareAmbient,
            ).forEach { (name, f) -> runCatching { f(v) }.onFailure { log("FAIL $name threw $it") } }
        }
        runCatching { startCarApi() }.onFailure { log("FAIL car api $it") }
        runCatching { startNavi() }.onFailure { log("FAIL navi $it") }
        runCatching { preloadRoadLimits() }.onFailure { log("FAIL road limits asset $it") }
        runCatching { startLocation() }.onFailure { log("FAIL location $it") }
        startTicker()
        log("gateway started config=$config")
    }

    /**
     * 구독을 모두 풀고 SDK를 놓는다. 같은 객체로 [start]를 다시 불러도 된다(권한 허용 뒤 재시작 등).
     * 마지막 신호 값은 지우지 않는다. 재시작 순간 parked가 잠깐 false로 튀어 훈련이 닫히는 일을 막고,
     * 다시 구독하면서 받는 현재 값으로 곧 바뀐다.
     */
    override fun stop() {
        if (!started) return
        started = false
        job.cancelChildren()
        unregister.forEach { runCatching { it() } }
        unregister.clear()
        runCatching { car?.disconnect() }
        runCatching { navi?.release() }
        runCatching { fused?.release() }
        runCatching { vehicle?.release() }
        car = null
        navi = null
        fused = null
        vehicle = null
        log("gateway stopped")
    }

    // ---------- 와이퍼: 켜짐 알림 + 켜져 있는 동안 스위치 조회 ----------

    private fun startWiper(v: Vehicle) {
        val wiper = v.getWiper()
        val listener = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) {
                edit("wiperState", state) { it.copy(wiperState = state, wiper = if (state == WiperState.OFF) WiperLevel.OFF else it.wiper) }
                readWiperSwitch()
            }
            override fun onFailed(e: Exception) = fail("wiper listener", e)
        }
        wiper.checkWiperStateCapabilities(
            onComplete = { cfg, _ ->
                if (!supported(cfg)) return@checkWiperStateCapabilities log("cap wiper unsupported $cfg")
                wiper.getCurrentWiperState(frontWindshield, { s -> edit("wiperState", s) { it.copy(wiperState = s) }; readWiperSwitch() }, { fail("wiper get", it) })
                wiper.registerWiperState(frontWindshield, listener)
                unregister += { wiper.unregisterWiperState(frontWindshield, listener) }
            },
            onFailed = { fail("cap wiper", it) },
        )
    }

    private fun readWiperSwitch() {
        val v = vehicle ?: return
        v.getWiper().getCurrentWiperSwitch(
            frontWindshield,
            onComplete = { sw ->
                val level = sw.toLevel(raw.wiperState)
                if (level != raw.wiper) edit("wiper", "$sw→$level") { it.copy(wiper = level) }
            },
            onFailed = { fail("wiper switch", it) },
        )
    }

    // ---------- 등화 ----------

    private fun startLights(v: Vehicle) {
        val light = v.getLight()
        fun listener(name: String, set: (Raw, Boolean?) -> Raw) = object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) = edit(name, state) { set(it, state?.let { s -> s == LightState.ON }) }
            override fun onFailed(e: Exception) = fail("$name listener", e)
        }
        val low = listener("lowBeam") { r, b -> r.copy(lowBeam = b) }
        light.checkLowBeamLightStateCapabilities({ cfg, _ ->
            if (!supported(cfg)) return@checkLowBeamLightStateCapabilities log("cap lowBeam unsupported")
            light.getCurrentLowBeamLightState({ s -> low.onLightStateUpdated(s) }, { fail("lowBeam get", it) })
            light.registerLowBeamLightState(low)
            unregister += { light.unregisterLowBeamLightState(low) }
        }, { fail("cap lowBeam", it) })
        val high = listener("highBeam") { r, b -> r.copy(highBeam = b) }
        light.checkHighBeamLightStateCapabilities({ cfg, _ ->
            if (!supported(cfg)) return@checkHighBeamLightStateCapabilities log("cap highBeam unsupported")
            light.getCurrentHighBeamLightState({ s -> high.onLightStateUpdated(s) }, { fail("highBeam get", it) })
            light.registerHighBeamLightState(high)
            unregister += { light.unregisterHighBeamLightState(high) }
        }, { fail("cap highBeam", it) })
        // 에뮬레이터는 안개등 미지원(ERROR_NOT_SUPPORTED_PROPERTY) → 값은 null로 남고 안개등 단계·규칙은 빠진다.
        val frontFog = listener("frontFog") { r, b -> r.copy(frontFog = b) }
        light.checkFrontFogLightStateCapabilities({ cfg, _ ->
            if (!supported(cfg)) return@checkFrontFogLightStateCapabilities log("cap frontFog unsupported")
            light.getCurrentFrontFogLightState({ s -> frontFog.onLightStateUpdated(s) }, { fail("frontFog get", it) })
            light.registerFrontFogLightState(frontFog)
            unregister += { light.unregisterFrontFogLightState(frontFog) }
        }, { fail("cap frontFog", it) })
        val rearFog = listener("rearFog") { r, b -> r.copy(rearFog = b) }
        light.checkRearFogLightStateCapabilities({ cfg, _ ->
            if (!supported(cfg)) return@checkRearFogLightStateCapabilities log("cap rearFog unsupported")
            light.getCurrentRearFogLightState({ s -> rearFog.onLightStateUpdated(s) }, { fail("rearFog get", it) })
            light.registerRearFogLightState(rearFog)
            unregister += { light.unregisterRearFogLightState(rearFog) }
        }, { fail("cap rearFog", it) })
    }

    private fun startTurnSignal(v: Vehicle) {
        val ts = v.getTurnSignal()
        val listener = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = edit("turnSignal", state) { it.copy(turnApi = state?.toTurn()) }
            override fun onFailed(e: Exception) = fail("turnSignal listener", e)
        }
        ts.checkTurnSignalStateCapabilities({ cfg ->
            if (!supported(cfg)) return@checkTurnSignalStateCapabilities log("cap turnSignal unsupported")
            ts.getCurrentTurnSignalState({ s -> listener.onTurnSignalStateUpdated(s) }, { fail("turnSignal get", it) })
            ts.registerTurnSignalState(listener)
            unregister += { ts.unregisterTurnSignalState(listener) }
        }, { fail("cap turnSignal", it) })
    }

    // ---------- 브레이크·조향 ----------

    private fun startBrake(v: Vehicle) {
        val brake = v.getBrake()
        val listener = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(pressed: Boolean?) = edit("brake", pressed) { it.copy(brake = pressed) }
            override fun onFailed(e: Exception) = fail("brake listener", e)
        }
        brake.checkBrakePedalStatusCapabilities({ cfg ->
            if (!supported(cfg)) return@checkBrakePedalStatusCapabilities log("cap brake unsupported")
            brake.getCurrentBrakePedalStatus({ p -> listener.onBrakePedalStatusUpdated(p) }, { fail("brake get", it) })
            brake.registerBrakePedalStatus(listener)
            unregister += { brake.unregisterBrakePedalStatus(listener) }
        }, { fail("cap brake", it) })
    }

    private fun startSteering(v: Vehicle) {
        val sw = v.getSteeringWheel()
        val listener = object : SteeringWheelAngleListener {
            override fun onSteeringWheelAngleUpdated(deg: Float?) {
                val angle = deg?.let { if (config.invertSteering) -it else it }
                // 같은 값이 약 3초마다 다시 온다(실측) → 바뀔 때만 기록
                if (angle != raw.steering) edit("steering", angle) { it.copy(steering = angle) }
            }
            override fun onFailed(e: Exception) = fail("steering listener", e)
        }
        sw.checkSteeringWheelAngleCapabilities({ cfg, bounds ->
            log("cap steering=$cfg bounds=${bounds.minValue}..${bounds.maxValue}")
            if (!supported(cfg)) return@checkSteeringWheelAngleCapabilities
            sw.getCurrentSteeringWheelAngle({ d -> listener.onSteeringWheelAngleUpdated(d) }, { fail("steering get", it) })
            sw.registerSteeringWheelAngle(listener)
            unregister += { sw.unregisterSteeringWheelAngle(listener) }
        }, { fail("cap steering", it) })
    }

    // ---------- 공조 ----------

    private fun startHvac(v: Vehicle) {
        val hvac = v.getHVAC()
        hvac.checkHvacDefrosterCapabilities({ areas: List<WindowArea> ->
            val area = areas.firstOrNull { WindowValue.FRONT_WINDSHIELD in it.values.toList() } ?: return@checkHvacDefrosterCapabilities log("cap defroster no front")
            defrostArea = area
            val l = object : HvacDefrosterStateListener {
                override fun onHvacDefrosterStateUpdated(on: Boolean?) = edit("defroster", on) { it.copy(defroster = on) }
                override fun onFailed(e: Exception) = fail("defroster listener", e)
            }
            hvac.getHvacDefrosterState(area, { l.onHvacDefrosterStateUpdated(it) }, { fail("defroster get", it) })
            hvac.registerHvacDefrosterState(area, l)
            unregister += { hvac.unregisterHvacDefrosterState(area, l) }
        }, { fail("cap defroster", it) })
        hvac.checkHvacMaxDefrostCapabilities({ areas: List<SeatArea> ->
            val area = areas.firstOrNull() ?: return@checkHvacMaxDefrostCapabilities
            val l = object : HvacMaxDefrostStateListener {
                override fun onHvacMaxDefrostStateUpdated(on: Boolean?) = edit("maxDefrost", on) { it.copy(maxDefrost = on) }
                override fun onFailed(e: Exception) = fail("maxDefrost listener", e)
            }
            hvac.getHvacMaxDefrostState(area, { l.onHvacMaxDefrostStateUpdated(it) }, { fail("maxDefrost get", it) })
            hvac.registerHvacMaxDefrostState(area, l)
            unregister += { hvac.unregisterHvacMaxDefrostState(area, l) }
        }, { fail("cap maxDefrost", it) })
        hvac.checkHvacAcCapabilities({ areas: List<SeatArea> ->
            val area = areas.firstOrNull() ?: return@checkHvacAcCapabilities
            val l = object : HvacAcStateListener {
                override fun onHvacAcStateUpdated(on: Boolean?) = edit("ac", on) { it.copy(ac = on) }
                override fun onFailed(e: Exception) = fail("ac listener", e)
            }
            hvac.getHvacAcState(area, { l.onHvacAcStateUpdated(it) }, { fail("ac get", it) })
            hvac.registerHvacAcState(area, l)
            unregister += { hvac.unregisterHvacAcState(area, l) }
        }, { fail("cap ac", it) })
        hvac.checkHvacRecirculationCapabilities({ areas: List<SeatArea> ->
            val area = areas.firstOrNull() ?: return@checkHvacRecirculationCapabilities
            val l = object : HvacRecirculationStateListener {
                // 리스너 메서드 이름이 Auto지만 일반 내기순환 값이 온다(실측).
                override fun onHvacAutoRecirculationStateUpdated(on: Boolean?) = edit("recirc", on) { it.copy(recirc = on) }
                override fun onFailed(e: Exception) = fail("recirc listener", e)
            }
            hvac.getHvacRecirculationState(area, { l.onHvacAutoRecirculationStateUpdated(it) }, { fail("recirc get", it) })
            hvac.registerHvacRecirculationState(area, l)
            unregister += { hvac.unregisterHvacRecirculationState(area, l) }
        }, { fail("cap recirc", it) })
    }

    // ---------- 창문·착좌 ----------

    private fun startWindows(v: Vehicle) {
        val window = v.getWindow()
        window.checkWindowPositionCapabilities({ map: Map<WindowArea, ai.pleos.playground.vehicle.api.model.capability.AreaBounds<Int>> ->
            log("cap window areas=${map.map { (a, b) -> "${a.values.toList()}:${b.minValue}..${b.maxValue}" }}")
            map.forEach { (area, bounds) ->
                val seats = area.values.toList().mapNotNull { it.toSeat() }
                if (seats.isEmpty()) return@forEach
                val l = object : WindowPositionListener {
                    override fun onWindowPositionUpdated(pos: Int?) {
                        val pct = pos?.let { windowOpenPercent(it, bounds.minValue, bounds.maxValue) }
                        edit("window$seats", "$pos→$pct%") { r ->
                            r.copy(windows = if (pct == null) r.windows - seats.toSet() else r.windows + seats.associateWith { pct })
                        }
                    }
                    override fun onFailed(e: Exception) = fail("window listener", e)
                }
                window.getCurrentWindowPosition(area, { l.onWindowPositionUpdated(it) }, { fail("window get", it) })
                window.registerWindowPosition(area, l)
                unregister += { window.unregisterWindowPosition(area, l) }
            }
        }, { fail("cap window", it) })
    }

    private fun startSeats(v: Vehicle) {
        val seat = v.getSeat()
        seat.checkSeatOccupancyCapabilities({ areas: List<SeatArea> ->
            areas.forEach { area ->
                val seats = area.values.toList().mapNotNull { it.toSeat() }
                if (seats.isEmpty()) return@forEach
                val l = object : SeatOccupancyListener {
                    override fun onSeatOccupancyUpdated(occupied: Boolean?) {
                        val value = occupied?.let { if (config.invertOccupancy) !it else it }
                        edit("occupied$seats", value) { r ->
                            r.copy(occupied = if (value == null) r.occupied - seats.toSet() else r.occupied + seats.associateWith { value })
                        }
                    }
                    override fun onFailed(e: Exception) = fail("occupancy listener", e)
                }
                seat.getCurrentSeatOccupancy(area, { l.onSeatOccupancyUpdated(it) }, { fail("occupancy get", it) })
                seat.registerSeatOccupancy(area, l)
                unregister += { seat.unregisterSeatOccupancy(area, l) }
            }
        }, { fail("cap occupancy", it) })
    }

    // ---------- 경고등: 일반 앱은 PRIVATE_CAR_SAFETY 권한이 없어 실패한다(실측). 권한이 열리는 차를 위해 시도만 한다. ----------

    private fun startTelltales(v: Vehicle) {
        val safety = v.getSafety()
        val l = object : TelltaleInfoListener {
            override fun onTelltaleInfoUpdated(t: Telltales?) = onTelltales(t)
            override fun onFailed(e: Exception) = fail("telltale listener", e)
        }
        runCatching {
            safety.registerTelltales(l)
            unregister += { safety.unregisterTelltales(l) }
            safety.getTelltalesInfo({ onTelltales(it) }, { fail("telltale get", it) })
        }.onFailure { log("FAIL telltales unavailable: $it") }
    }

    private fun onTelltales(t: Telltales?) {
        val items = t?.telltales ?: return
        val now = SystemClock.elapsedRealtime()
        val next = synchronized(lock) {
            val merged = raw.telltales.toMutableMap()
            items.forEach { d -> merged[d.type.name] = (d.state != TelltaleState.OFF) to telltaleColorOf(d.color) }
            merged
        }
        turnResolver.onTelltales(next["TURN_SIGNAL_LEFT"]?.first == true, next["TURN_SIGNAL_RIGHT"]?.first == true, now)
        edit("telltales", next.filterValues { it.first }.map { "${it.key}:${it.value.second}" }) { it.copy(telltales = next, telltalesKnown = true) }
    }

    private fun startCarInfo(v: Vehicle) {
        val info = v.getCarInfo()
        info.checkVINInfoCapabilities({ cfg ->
            if (!supported(cfg)) return@checkVINInfoCapabilities log("cap vin unsupported")
            // VIN 전체는 기록하지 않는다. 앞 8자리(차종)만 쓴다.
            info.getVINInfo({ vin -> edit("modelKey", vehicleModelKeyFromVin(vin)) { it.copy(modelKey = vehicleModelKeyFromVin(vin)) } }, { fail("vin get", it) })
        }, { fail("cap vin", it) })
    }

    // ---------- 주행 규제(정차 판정) ----------

    /**
     * 정차 판정은 주행 규제(CarUxRestrictions)만 쓴다. Pleos 가이드가 안내하는 표준 방식이고,
     * 실측에서 P = requiresDO false, R·N·D = true(속도 0이어도)로 기어와 같은 시점에 바뀌었다.
     * AAOS 기어·차속은 공모전 허용 범위를 지키려고 쓰지 않는다(decisions.md 11-14).
     */
    private fun startCarApi() {
        val c = Car.createCar(context) ?: return log("FAIL car service unavailable")
        car = c
        val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
        val l = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r -> onUx(r) }
        ux.registerListener(l)
        unregister += { ux.unregisterListener() }
        ux.currentCarUxRestrictions?.let { onUx(it) }
    }

    private fun onUx(r: CarUxRestrictions) {
        edit("ux", "requiresDO=${r.isRequiresDistractionOptimization} active=0x${Integer.toHexString(r.activeRestrictions)}") {
            it.copy(uxRestricted = r.isRequiresDistractionOptimization)
        }
    }

    // ---------- 내비 ----------

    private val naviListener = object : NaviHelperEventListener {
        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            val min = drivingInfo.destination?.duration?.let { (it + 59) / 60 }
            if (min != remaining.value) {
                remaining.value = min
                log("sig navi remaining=${min}min")
            }
        }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            log("sig navi arrived")
            remaining.value = 0
            arrived.tryEmit(Unit)
            route.value = null
        }
        override fun onRouteCancelled() {
            remaining.value = null
            route.value = null
        }
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            // 새 경로. 통행료가 있으면 요금소를 지나는 길이다(TBT 목록이 경로 전체가 아닐 수 있어 함께 본다).
            route.value = RouteFeatures(turnTypes = if (routeStartInfo.tollFare > 0) setOf(TurnType.TURN_TOLL.name) else emptySet())
            log("sig navi route started toll=${routeStartInfo.tollFare > 0}")
        }
        override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) =
            addRoute(cautions = cautionPointList.map { it.type.name })
        override fun onTripInfo(tripInfo: TripInfo) =
            addRoute(congestion = tripInfo.summary.trafficConditions.map { it.congestionType.name })
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            // 에뮬레이터는 늘 0(실측). 0보다 클 때만 쓴다.
            val v = speedLimit.takeIf { it > 0 }
            if (v != raw.naviLimit) log("sig navi speedLimit=$speedLimit")
            synchronized(lock) { raw = raw.copy(naviLimit = v, naviLimitAtMs = SystemClock.elapsedRealtime()) }
            if (v != null) publish()
        }
        override fun onTBTInfo(tbtInfo: List<TBTInfo>) {
            tbtInfo.firstOrNull { it.type == TurnType.TURN_ENTER_SERVICEAREA || it.type == TurnType.TURN_RESTAREA }
                ?.let { log("sig navi nextRestArea=${it.distance}m") }
            addRoute(turns = tbtInfo.map { it.type.name })
        }
    }

    /** 경로에서 본 것을 모은다. TBT 목록이 다음 몇 개뿐일 수 있어 경로가 끝날 때까지 합쳐 둔다. 같은 값이면 알리지 않는다. */
    private fun addRoute(turns: List<String> = emptyList(), cautions: List<String> = emptyList(), congestion: List<String> = emptyList()) {
        if (turns.isEmpty() && cautions.isEmpty() && congestion.isEmpty()) return
        val before = route.value
        route.update { (it ?: RouteFeatures()).let { r -> r.copy(r.turnTypes + turns, r.cautionTypes + cautions, r.congestion + congestion) } }
        if (route.value != before) log("sig navi route features=${route.value}")
    }

    private fun startNavi() {
        navi = NaviHelper(context).also {
            // 앱을 열기 전에 잡힌 경로도 읽게, 연결되면 TBT를 한 번 요청한다(결과는 onTBTInfo로 온다).
            it.initialize({ runCatching { it.getTBTInfo() }.onFailure { e -> log("FAIL navi getTBTInfo $e") } }, { e -> log("FAIL navi init $e") })
            it.addListener(naviListener)
            unregister += { it.removeListener(naviListener) }
        }
    }

    /**
     * NaviHelper에는 장소 검색 API가 없고 addWaypoint는 좌표가 필요하다. 그래서 내비의 장소 검색 화면을 "휴게소"로 열어
     * 운전자(또는 동승자)가 고르면 내비가 경유지로 넣게 한다(decisions.md).
     */
    override suspend fun addRestStopWaypoint(): Boolean {
        val n = navi ?: return false
        return runCatching { n.requestNavigationScreen(NavigationScreenRequest.SearchPlace("휴게소")) }
            .onFailure { log("FAIL rest stop search $it") }
            .onSuccess { log("navi rest stop search opened") }
            .isSuccess
    }

    // ---------- 위치: 일몰 계산, 속도 추정(O2 급감속 관찰) ----------

    private fun startLocation() {
        val f = FusedLocation(context)
        if (!f.isSupported()) return log("location unsupported")
        f.initialize()
        fused = f
        val l = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                location?.let(::onLocation)
            }
            override fun onError(e: Exception) = fail("location", e)
        }
        f.registerFusedLocationCallback(l)
        unregister += { f.unregisterFusedLocationCallback(l) }
    }

    private fun onLocation(loc: LocationData) {
        val cal = Calendar.getInstance()
        val offset = TimeZone.getDefault().getOffset(cal.timeInMillis) / 60_000
        val sun = SolarTimes.sunriseSunset(loc.latitude, loc.longitude, cal.get(Calendar.DAY_OF_YEAR), offset)
        // 속도 계산 시각은 콜백이 온 시각으로 한다. 콜백 간격은 1002~1008ms로 고르게 실측됐고,
        // LocationData.timestampMillis의 뜻(측위 시각인지, 단위·해상도)은 문서에 없다. 둘 다 로그로 남긴다.
        val t = System.currentTimeMillis()
        val (speed, heading) = synchronized(lock) {
            track.add(loc.latitude, loc.longitude, t)
            track.speedKmh() to track.headingDeg()
        }
        val prevCb = lastFixCallbackMs
        val prevFix = lastFixStampMs
        lastFixCallbackMs = t
        lastFixStampMs = loc.timestampMillis
        log(
            "sig loc ${"%.6f".format(java.util.Locale.ROOT, loc.latitude)},${"%.6f".format(java.util.Locale.ROOT, loc.longitude)} " +
                "dtCb=${prevCb?.let { t - it }} dtFix=${prevFix?.let { loc.timestampMillis - it }} " +
                "v=${speed?.let { "%.1f".format(java.util.Locale.ROOT, it) }} hd=${heading?.toInt()}",
        )
        val changedSun = sun?.first != raw.sunrise || sun?.second != raw.sunset
        if (changedSun) log("sig sun rise=${sun?.first} set=${sun?.second}")
        val mapLimit = if (config.useMapSpeedLimit) mapLimitAt(loc.latitude, loc.longitude, heading) else null
        if (mapLimit != raw.mapLimit) log("sig map speedLimit=$mapLimit")
        synchronized(lock) {
            raw = raw.copy(
                sunrise = sun?.first, sunset = sun?.second, locationSpeedKmh = speed,
                latitude = loc.latitude, longitude = loc.longitude, locationTimeMs = t, mapLimit = mapLimit,
            )
        }
        publish()
    }

    // ---------- 지도 제한속도: OpenStreetMap Overpass, 격자 칸 단위 캐시 ----------

    /**
     * 앱에 넣어 둔 시연 지역 제한속도(assets/zones/road_limits.json, tools/zones/make_demo_route.py)를 칸에 미리 채운다.
     * 이 범위 안 칸은 "아는 칸"이 되어 외부에 묻지 않는다(시연 때 Overpass가 붐벼도 판정된다).
     */
    private fun preloadRoadLimits() {
        if (preloaded) return
        val text = context.assets.open(ROAD_LIMITS_ASSET).bufferedReader().use { it.readText() }
        val ways = RoadSpeedLimits.parseOverpassWays(text)
        val bbox = Regex(""""bbox":\[([^\]]+)]""").find(text)?.groupValues?.get(1)?.split(",")?.map { it.trim().toDouble() }
        val byCell = mutableMapOf<Pair<Int, Int>, MutableList<RoadWay>>()
        ways.forEach { w ->
            w.points.map { (la, lo) -> RoadSpeedLimits.cellOf(la, lo, config.mapCellDeg) }.toSet()
                .forEach { c -> byCell.getOrPut(c) { mutableListOf() } += w }
        }
        synchronized(mapCells) {
            if (bbox != null && bbox.size == 4) {
                val (s0, w0) = RoadSpeedLimits.cellOf(bbox[0], bbox[1], config.mapCellDeg)
                val (n0, e0) = RoadSpeedLimits.cellOf(bbox[2], bbox[3], config.mapCellDeg)
                // 범위 안 칸은 도로가 없어도 아는 칸(빈 목록)으로 둔다.
                for (y in s0 + 1 until n0) for (x in w0 + 1 until e0) staticCells[y to x] = byCell[y to x].orEmpty()
            }
            byCell.forEach { (c, list) -> staticCells.putIfAbsent(c, list) }
        }
        preloaded = true
        log("road limits preloaded ways=${ways.size} cells=${staticCells.size}")
    }

    /** 캐시한 칸의 도로로 제한속도를 고르고, 지금 칸이 없으면 드물게 한 번 묻는다. 모르면 null(판정 안 함). */
    private fun mapLimitAt(lat: Double, lon: Double, heading: Double?): Int? {
        val cell = RoadSpeedLimits.cellOf(lat, lon, config.mapCellDeg)
        val ways = synchronized(mapCells) {
            (-1..1).flatMap { dy ->
                (-1..1).flatMap { dx ->
                    val c = (cell.first + dy) to (cell.second + dx)
                    staticCells[c] ?: mapCells[c].orEmpty()
                }
            }.distinctBy { it.id }
        }
        val known = synchronized(mapCells) { staticCells.containsKey(cell) || mapCells.containsKey(cell) }
        if (!known) requestMapCell(cell)
        return RoadSpeedLimits.match(lat, lon, heading, ways)?.limitKmh
    }

    private fun requestMapCell(cell: Pair<Int, Int>) {
        val now = SystemClock.elapsedRealtime()
        if (mapInFlight != null || now - mapLastRequestMs < config.mapMinIntervalMs) return
        if (mapFailedAt[cell]?.let { now - it < config.mapRetryMs } == true) return
        mapInFlight = cell
        mapLastRequestMs = now
        scope.launch(Dispatchers.IO) {
            val t0 = SystemClock.elapsedRealtime()
            val query = RoadSpeedLimits.overpassQuery(RoadSpeedLimits.bboxOf(cell, config.mapCellDeg))
            val result = runCatching { postOverpass(query) }
            result.onSuccess { body ->
                val ways = RoadSpeedLimits.parseOverpassWays(body)
                synchronized(mapCells) { mapCells[cell] = ways }
                log("map cell=$cell ways=${ways.size} bytes=${body.length} took=${SystemClock.elapsedRealtime() - t0}ms")
            }.onFailure {
                mapFailedAt[cell] = SystemClock.elapsedRealtime()
                log("FAIL map cell=$cell ${it.javaClass.simpleName}: ${it.message}")
            }
            mapInFlight = null
        }
    }

    /** 보내는 것은 칸의 경계 좌표뿐이다. 계정·키·기기 정보는 보내지 않는다. */
    private fun postOverpass(query: String): String {
        val conn = java.net.URL(config.overpassUrl).openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = config.mapTimeoutMs
            conn.readTimeout = config.mapTimeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent", "SideSeat/1.0")
            conn.outputStream.use { it.write(("data=" + java.net.URLEncoder.encode(query, "UTF-8")).toByteArray()) }
            val code = conn.responseCode
            if (code != 200) error("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ---------- 실내 조명 ----------

    private fun prepareAmbient(v: Vehicle) {
        val light = v.getLight()
        light.checkAmbientLightBrightnessColorCapabilities({ cfg, areas ->
            log("cap ambient=$cfg areas=${areas?.map { it.values.toList() }}")
            // 고정 영역(ROW_1_LEFT 단독)은 not supported → 차가 알려 준 영역 그룹을 쓴다(실측 11-07).
            val area = areas?.firstOrNull() ?: return@checkAmbientLightBrightnessColorCapabilities
            ambientArea = area
            // 앱이 바꾸기 전의 색·밝기를 처음 한 번만 기억한다. stop/start로 다시 시작해도 앱이 칠한 색으로 덮어쓰지 않는다.
            if (ambientBeforeColor == null) {
                light.getAmbientLightBrightnessColor(area, { color ->
                    // 읽은 값에는 알파가 없다(실측 11-07: ff47ebff → 47ebff). 불투명으로 맞춘다.
                    if (ambientBeforeColor == null) ambientBeforeColor = color?.let { if (it ushr 24 == 0) it or 0xFF000000.toInt() else it }
                    log("ambient original color=${ambientBeforeColor?.let(Integer::toHexString)}")
                }, { fail("ambient get", it) })
            }
            if (ambientBeforeBrightness == null) {
                light.getAmbientLightBrightness(area, { b ->
                    if (ambientBeforeBrightness == null) ambientBeforeBrightness = b
                    log("ambient original brightness=$b")
                }, { fail("ambient brightness get", it) })
            }
        }, { fail("cap ambient", it) })
    }

    override suspend fun setAmbient(mood: AmbientMood): Boolean {
        val light = vehicle?.getLight() ?: return false
        val area = ambientArea ?: return false.also { log("FAIL ambient no area") }
        val (argb, brightness) = when (mood) {
            AmbientMood.CALM -> 0xFFFFF1DC.toInt() to 35
            AmbientMood.ALERT -> 0xFFFF1E1E.toInt() to 100
            AmbientMood.SUCCESS -> 0xFF1EE86A.toInt() to 80
            // OFF = 앱이 켜기 전 상태로 복원. 읽지 못했으면 평온 색.
            AmbientMood.OFF -> (ambientBeforeColor ?: 0xFFFFF1DC.toInt()) to (ambientBeforeBrightness ?: 35)
        }
        val t0 = SystemClock.elapsedRealtime()
        val ok = withTimeoutOrNull(2_000) {
            suspendCancellableCoroutine { cont ->
                light.setAmbientLightBrightnessColor(
                    area, argb,
                    { _, value ->
                        log("set ambient $mood ok value=${value?.let(Integer::toHexString)} took=${SystemClock.elapsedRealtime() - t0}ms")
                        if (cont.isActive) cont.resume(true)
                    },
                    { e ->
                        log("FAIL set ambient $mood restricted=${e.isDrivingRestricted()} ${e.javaClass.simpleName}: ${e.message}")
                        if (cont.isActive) cont.resume(false)
                    },
                )
            }
        } ?: false.also { log("FAIL set ambient $mood timeout") }
        if (ok) {
            runCatching {
                light.setAmbientLightBrightness(area, brightness, { _, _ -> }, { fail("ambient brightness", it) })
            }
        }
        return ok
    }

    // ---------- 상태 합치기 ----------

    private fun startTicker() {
        scope.launch {
            var lastMinute = -1
            while (isActive) {
                delay(config.wiperPollMs)
                val r = raw
                if (r.wiperState == WiperState.ON || (r.wiper != null && r.wiper != WiperLevel.OFF)) readWiperSwitch()
                val minute = nowMinute()
                // 깜빡이는 경고등 신호(권한이 있는 차)와 시각 변화를 반영한다.
                if (minute != lastMinute || r.telltalesKnown) {
                    lastMinute = minute
                    publish()
                }
            }
        }
    }

    private fun edit(name: String, value: Any?, f: (Raw) -> Raw) {
        synchronized(lock) { raw = f(raw) }
        log("sig $name=$value")
        publish()
    }

    private fun publish() {
        val r = synchronized(lock) { raw }
        val inputs = ParkedInputs(
            uxRestricted = r.uxRestricted,
            parkingBrakeOn = r.telltaleOn("PARKING_BRAKE", "ELECTRONIC_PARKING_BRAKE"),
        )
        val (parked, source) = inputs.resolve(config.parkedOrder)
        val diag = ParkedDiagnostics(inputs, parked, source)
        if (diag != parkedDiag.value) {
            if (diag.parked != parkedDiag.value.parked) log("sig parked=$parked via=$source inputs=$inputs")
            parkedDiag.value = diag
        }
        val now = SystemClock.elapsedRealtime()
        val turn = turnResolver.resolve(r.turnApi, now)
        val navi = r.naviLimit?.takeIf { now - r.naviLimitAtMs <= config.naviLimitFreshMs }
        val limit = navi?.let { it to SpeedLimitSource.NAVI } ?: r.mapLimit?.let { it to SpeedLimitSource.MAP }
        val defrost = if (r.defroster == null && r.maxDefrost == null) null else r.defroster == true || r.maxDefrost == true
        val windows = r.windows
        val next = VehicleState(
            timestampMs = System.currentTimeMillis(),
            parked = parked,
            wiper = r.wiper ?: r.wiperState?.let { if (it == WiperState.OFF) WiperLevel.OFF else null },
            lowBeamOn = r.lowBeam,
            highBeamOn = r.highBeam,
            frontFogOn = r.frontFog,
            rearFogOn = r.rearFog,
            turnSignal = if (turn == TurnSignal.HAZARD) r.turnApi ?: TurnSignal.OFF else turn,
            hazardOn = if (r.telltalesKnown) turn == TurnSignal.HAZARD else null,
            brakePressed = r.brake,
            steeringAngleDeg = r.steering,
            defrostFrontOn = defrost,
            acOn = r.ac,
            recirculationOn = r.recirc,
            driverWindowOpenPct = windows[Seat.DRIVER],
            windowOpenPct = windows,
            occupied = r.occupied.filterValues { it }.keys,
            parkingBrakeOn = inputs.parkingBrakeOn,
            activeTelltales = r.telltales.filterValues { it.first }.mapValues { it.value.second },
            speedKmhEstimate = r.locationSpeedKmh,
            latitude = r.latitude,
            longitude = r.longitude,
            locationTimeMs = r.locationTimeMs,
            speedLimitKmh = limit?.first,
            speedLimitSource = limit?.second,
            minuteOfDay = nowMinute(),
            sunsetMinute = r.sunset,
            sunriseMinute = r.sunrise,
            vehicleModelKey = r.modelKey,
        )
        // 시각 외에 바뀐 것이 있을 때만 내보낸다.
        if (next.copy(timestampMs = 0) != state.value.copy(timestampMs = 0)) state.value = next
    }

    // ---------- 보조 ----------

    private fun Raw.telltaleOn(vararg types: String): Boolean? =
        if (!telltalesKnown) null else types.any { telltales[it]?.first == true }

    private fun supported(cfg: CapabilityConfig?): Boolean = cfg == null || cfg.accessType != AccessType.NONE

    private fun log(msg: String) {
        Log.i(VEHICLE_TAG, "t=${SystemClock.elapsedRealtime()} $msg")
    }

    private fun fail(what: String, e: Exception) =
        log("FAIL $what ${e.javaClass.simpleName}: ${e.message}")

    private fun nowMinute(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

    private companion object {
        const val ROAD_LIMITS_ASSET = "zones/road_limits.json"

        /** 제한 대상 set은 P단 밖에서 메시지 "...: 50001"로 온다(실측 11-07). */
        fun Exception.isDrivingRestricted(): Boolean =
            message?.contains("DRIVING_RESTRICTED") == true || message?.endsWith("50001") == true

        fun WiperSwitch?.toLevel(state: WiperState?): WiperLevel? = when (this) {
            WiperSwitch.OFF -> WiperLevel.OFF
            WiperSwitch.MIST -> WiperLevel.MIST
            WiperSwitch.INTERMITTENT_LEVEL_1, WiperSwitch.INTERMITTENT_LEVEL_2, WiperSwitch.INTERMITTENT_LEVEL_3,
            WiperSwitch.INTERMITTENT_LEVEL_4, WiperSwitch.INTERMITTENT_LEVEL_5, WiperSwitch.AUTO -> WiperLevel.INTERMITTENT
            WiperSwitch.CONTINUOUS_LEVEL_1 -> WiperLevel.LOW
            WiperSwitch.CONTINUOUS_LEVEL_2, WiperSwitch.CONTINUOUS_LEVEL_3,
            WiperSwitch.CONTINUOUS_LEVEL_4, WiperSwitch.CONTINUOUS_LEVEL_5 -> WiperLevel.HIGH
            else -> when (state) {
                WiperState.ON -> WiperLevel.LOW
                WiperState.OFF -> WiperLevel.OFF
                else -> null
            }
        }

        fun TurnSignalValue.toTurn(): TurnSignal = when (this) {
            TurnSignalValue.LEFT -> TurnSignal.LEFT
            TurnSignalValue.RIGHT -> TurnSignal.RIGHT
            else -> TurnSignal.OFF
        }

        /** 한국(왼쪽 운전석) 기준: 1열 왼쪽 = 운전석. */
        fun WindowValue.toSeat(): Seat? = when (this) {
            WindowValue.ROW_1_LEFT -> Seat.DRIVER
            WindowValue.ROW_1_RIGHT -> Seat.PASSENGER
            WindowValue.ROW_2_LEFT -> Seat.REAR_LEFT
            WindowValue.ROW_2_RIGHT -> Seat.REAR_RIGHT
            else -> null
        }

        fun SeatValue.toSeat(): Seat? = when (this) {
            SeatValue.ROW_1_LEFT -> Seat.DRIVER
            SeatValue.ROW_1_RIGHT -> Seat.PASSENGER
            SeatValue.ROW_2_LEFT -> Seat.REAR_LEFT
            SeatValue.ROW_2_CENTER -> Seat.REAR_CENTER
            SeatValue.ROW_2_RIGHT -> Seat.REAR_RIGHT
            else -> null
        }
    }
}
