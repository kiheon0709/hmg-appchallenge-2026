package io.moodlab.moodrive.vehicle

import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.DrivingModeStateListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import io.moodlab.moodrive.core.AmbientCommand
import io.moodlab.moodrive.core.DriveMode
import io.moodlab.moodrive.core.Gear
import io.moodlab.moodrive.core.VehicleSnapshot
import io.moodlab.moodrive.core.WiperLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import java.util.Calendar

/** 실측 기록용 로그 태그: adb logcat -s MooDriveMeasure */
private const val TAG = "MooDriveMeasure"

/**
 * Pleos Vehicle SDK / NaviHelper → [VehicleSnapshot] 어댑터.
 * 콜백은 바인더 스레드로 올 수 있어 MutableStateFlow.update로만 상태를 바꾼다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PleosVehicleSignalSource(private val context: Context, private val vehicle: Vehicle) : VehicleSignalSource {
    private val state = MutableStateFlow(
        VehicleSnapshot(timestampMs = System.currentTimeMillis(), minuteOfDay = nowMinute(), gear = Gear.D),
    )
    private val gearKnown = MutableStateFlow(false)

    /** 첫 기어 값을 받기 전에는 내보내지 않는다. P단에서 켰는데 잠깐 주행으로 판단하는 일을 막는다. */
    override val snapshots: Flow<VehicleSnapshot> = gearKnown.filter { it }.flatMapLatest { state }

    private val wiper by lazy { vehicle.getWiper() }
    private val light by lazy { vehicle.getLight() }
    private val drivingMode by lazy { vehicle.getDrivingMode() }
    private val frontWindshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private var car: Car? = null
    private var navi: NaviHelper? = null

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) {
            Log.i(TAG, "wiper state=$state")
            readWiperSwitch(state)
        }
        override fun onFailed(e: Exception) = logFailure("wiper listener", e)
    }
    private val lowBeamListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) {
            Log.i(TAG, "lowBeam state=$state")
            edit { it.copy(lowBeamOn = state == LightState.ON) }
        }
        override fun onFailed(e: Exception) = logFailure("lowBeam listener", e)
    }
    private val driveModeListener = object : DrivingModeStateListener {
        override fun onDrivingModeStateUpdated(state: DrivingModeType?) {
            Log.i(TAG, "driveMode=$state")
            edit { it.copy(driveMode = state?.toDriveMode() ?: DriveMode.UNKNOWN) }
        }
        override fun onFailed(e: Exception) = logFailure("driveMode listener", e)
    }
    private val cruiseListener = object : CruiseControlStateListener {
        override fun onCruiseControlStateUpdated(state: CruiseControl?) {
            Log.i(TAG, "cruise=$state")
            edit { it.copy(cruiseOn = state == CruiseControl.ACTIVATED) }
        }
        override fun onFailed(e: Exception) = logFailure("cruise listener", e)
    }
    private val gearCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            Log.i(TAG, "gear raw=${value.value}")
            (value.value as? Int)?.let { raw -> edit { it.copy(gear = raw.toGear()) } }
            gearKnown.value = true
        }
        override fun onErrorEvent(propId: Int, zone: Int) = Log.w(TAG, "gear error prop=$propId zone=$zone").let { }
    }
    private val naviListener = object : NaviHelperEventListener {
        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            Log.i(TAG, "navi drivingInfo=$drivingInfo")
            drivingInfo.destination?.duration?.let { sec -> edit { it.copy(etaMinutes = sec / 60) } }
        }
        override fun onTripInfo(tripInfo: TripInfo) {
            Log.i(TAG, "navi tripInfo=$tripInfo")
            tripInfo.eta?.let { sec -> edit { it.copy(etaMinutes = sec / 60) } }
        }
    }

    /** 지원 여부를 먼저 확인해 미지원 신호는 기본값을 유지한다. */
    fun start() {
        wiper.checkWiperStateCapabilities(
            onComplete = { config, _ ->
                Log.i(TAG, "wiper capability=$config")
                wiper.getCurrentWiperState(frontWindshield, ::readWiperSwitch) { logFailure("wiper get", it) }
                wiper.registerWiperState(frontWindshield, wiperListener)
            },
            onFailed = { logFailure("wiper capability", it) },
        )
        light.getCurrentLowBeamLightState(
            onComplete = { s -> edit { it.copy(lowBeamOn = s == LightState.ON) } },
            onFailed = { logFailure("lowBeam get", it) },
        )
        light.registerLowBeamLightState(lowBeamListener)
        drivingMode.registerDrivingModeState(driveModeListener)
        drivingMode.registerCruiseControlState(cruiseListener)
        startGear()
        startNavi()
    }

    fun stop() {
        runCatching { wiper.unregisterWiperState(frontWindshield, wiperListener) }
        runCatching { light.unregisterLowBeamLightState(lowBeamListener) }
        runCatching { drivingMode.unregisterDrivingModeState(driveModeListener) }
        runCatching { drivingMode.unregisterCruiseControlState(cruiseListener) }
        runCatching { (car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager)?.unregisterCallback(gearCallback) }
        runCatching { car?.disconnect() }
        runCatching { navi?.removeListener(naviListener); navi?.release() }
    }

    /** 작동 중 단계만 바뀌면 WiperState 알림이 오지 않아 주기적으로 스위치를 읽는다. */
    fun refreshWiper() {
        if (state.value.wiper != WiperLevel.OFF) readWiperSwitch(WiperState.ON)
    }

    private fun readWiperSwitch(wiperState: WiperState?) {
        wiper.getCurrentWiperSwitch(
            frontWindshield,
            onComplete = { sw ->
                if (sw.toWiperLevel(wiperState) != state.value.wiper) Log.i(TAG, "wiper switch=$sw")
                val level = sw.toWiperLevel(wiperState)
                if (level != state.value.wiper) edit { it.copy(wiper = level) }
            },
            onFailed = {
                logFailure("wiper switch", it)
                edit { s -> s.copy(wiper = if (wiperState == WiperState.ON) WiperLevel.LOW else WiperLevel.OFF) }
            },
        )
    }

    /** 공개 SDK에 기어 API가 없다. 읽지 못하면 조명은 계속 보류되도록 D로 둔다. */
    private fun startGear() = runCatching {
        val c = Car.createCar(context) ?: error("Car service unavailable")
        car = c
        val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        pm.registerCallback(gearCallback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        Log.i(TAG, "gear subscribed")
    }.onFailure {
        Log.w(TAG, "gear unavailable, assuming D: $it")
        gearKnown.value = true
    }

    private fun startNavi() = runCatching {
        navi = NaviHelper(context).also {
            it.initialize()
            it.addListener(naviListener)
        }
    }.onFailure { Log.w(TAG, "navi unavailable: $it") }

    private fun edit(f: (VehicleSnapshot) -> VehicleSnapshot) =
        state.update { f(it).copy(timestampMs = System.currentTimeMillis(), minuteOfDay = nowMinute()) }

    private fun nowMinute(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
}

private fun logFailure(what: String, e: Exception) {
    Log.w(TAG, "$what failed type=${e.javaClass.name} code=${e.vehicleErrorCode()} message=${e.message}")
}

/** 배포된 SDK에서 VehicleException 타입을 컴파일 시점에 참조할 수 없어 문서의 getErrorCode()를 이름으로 읽는다. */
private fun Exception.vehicleErrorCode(): String? =
    runCatching { javaClass.getMethod("getErrorCode").invoke(this)?.toString() }.getOrNull()

/**
 * 에뮬레이터 실측: P단이 아닐 때 제한 대상 set은 일반 Exception에 메시지 "...: 50001"로 온다
 * (도어 set으로 P 성공, D·N 실패 확인, 2026-11-07).
 */
private fun Exception.isDrivingRestricted(): Boolean =
    vehicleErrorCode() == "ERROR_DRIVING_RESTRICTED" ||
        message?.contains("DRIVING_RESTRICTED") == true ||
        message?.endsWith("50001") == true

private fun WiperSwitch?.toWiperLevel(state: WiperState?): WiperLevel = when (this) {
    WiperSwitch.OFF -> WiperLevel.OFF
    WiperSwitch.MIST,
    WiperSwitch.INTERMITTENT_LEVEL_1, WiperSwitch.INTERMITTENT_LEVEL_2, WiperSwitch.INTERMITTENT_LEVEL_3,
    WiperSwitch.INTERMITTENT_LEVEL_4, WiperSwitch.INTERMITTENT_LEVEL_5 -> WiperLevel.INTERMITTENT
    WiperSwitch.CONTINUOUS_LEVEL_1, WiperSwitch.CONTINUOUS_LEVEL_2 -> WiperLevel.LOW
    WiperSwitch.CONTINUOUS_LEVEL_3, WiperSwitch.CONTINUOUS_LEVEL_4, WiperSwitch.CONTINUOUS_LEVEL_5 -> WiperLevel.HIGH
    else -> if (state == WiperState.ON) WiperLevel.LOW else WiperLevel.OFF
}

private fun DrivingModeType.toDriveMode(): DriveMode = when (this) {
    DrivingModeType.ECO -> DriveMode.ECO
    DrivingModeType.SPORT -> DriveMode.SPORT
    else -> DriveMode.NORMAL
}

private fun Int.toGear(): Gear = when (this) {
    VehicleGear.GEAR_PARK -> Gear.P
    VehicleGear.GEAR_REVERSE -> Gear.R
    VehicleGear.GEAR_NEUTRAL -> Gear.N
    else -> Gear.D
}

/** 실내 조명 출력. 미리보기(화면 시각화)와 실제 Pleos Light API 두 구현을 같이 쓴다. */
interface AmbientController {
    /** @param onRestricted 차량이 ERROR_DRIVING_RESTRICTED로 거부했을 때 호출 */
    fun apply(command: AmbientCommand, onRestricted: () -> Unit)
}

/**
 * Light 도메인 연결. 에뮬레이터는 조명 변화를 표시하지 않으므로 set 뒤 값을 다시 읽어 [onReported]로 알린다.
 * @param onReported 차량이 돌려준 ARGB. 요청 완료만으로 값이 바뀌었다고 보지 않는다.
 */
class PleosAmbientController(private val vehicle: Vehicle?, private val onReported: (Int) -> Unit) : AmbientController {
    /** 차량이 지원한다고 답한 영역. 고정값을 쓰면 "area ID not supported"로 실패한다. */
    private var area: SeatArea? = null
    private var pending: Pair<AmbientCommand, () -> Unit>? = null
    private var autoColorBefore: Boolean? = null

    /** 주행모드 자동 색이 앱 색을 덮어쓰지 않게 끈다. [release]에서 복원. */
    fun prepare() {
        val light = vehicle?.getLight() ?: return
        light.checkAmbientLightBrightnessColorCapabilities(
            onComplete = { config, areas ->
                Log.i(TAG, "ambient capability=$config areas=${areas?.map { it.values.toList() }}")
                area = areas?.firstOrNull()
                pending?.let { (command, restricted) ->
                    pending = null
                    apply(command, restricted)
                }
            },
            onFailed = { logFailure("ambient capability", it) },
        )
        light.getAmbientColorAutoByDriveModeEnabled(
            onComplete = { enabled ->
                autoColorBefore = enabled
                if (enabled == true) {
                    light.setAmbientColorAutoByDriveModeEnabled(false, onComplete = { }, onFailed = { logFailure("ambient auto off", it) })
                }
            },
            onFailed = { logFailure("ambient auto get", it) },
        )
    }

    override fun apply(command: AmbientCommand, onRestricted: () -> Unit) {
        val light = vehicle?.getLight() ?: return
        val area = area ?: run {
            pending = command to onRestricted
            return
        }
        val argb = command.color.toArgb()
        light.setAmbientLightBrightnessColor(
            area, argb,
            onComplete = { _, value ->
                Log.i(TAG, "ambient set complete requested=${Integer.toHexString(argb)} value=${value?.let(Integer::toHexString)}")
                light.getAmbientLightBrightnessColor(
                    area,
                    onComplete = { read ->
                        Log.i(TAG, "ambient readback=${read?.let(Integer::toHexString)}")
                        read?.let(onReported)
                    },
                    onFailed = { logFailure("ambient readback", it) },
                )
            },
            onFailed = { e ->
                logFailure("ambient set", e)
                if (e.isDrivingRestricted()) onRestricted()
            },
        )
        light.setAmbientLightBrightness(
            area, command.brightness,
            onComplete = { _, value -> Log.i(TAG, "ambient brightness complete value=$value") },
            onFailed = { logFailure("ambient brightness", it) },
        )
    }

    fun release() {
        val light = vehicle?.getLight() ?: return
        if (autoColorBefore == true) {
            runCatching { light.setAmbientColorAutoByDriveModeEnabled(true, onComplete = { }, onFailed = { logFailure("ambient auto restore", it) }) }
        }
    }
}
