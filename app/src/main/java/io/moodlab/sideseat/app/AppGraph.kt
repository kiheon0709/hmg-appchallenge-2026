package io.moodlab.sideseat.app

import android.content.Context
import android.util.Log
import io.moodlab.sideseat.BuildConfig
import io.moodlab.sideseat.vehicle.DebugVehicleGateway
import io.moodlab.sideseat.vehicle.PleosVehicleGateway
import io.moodlab.sideseat.vehicle.VehicleGateway
import io.moodlab.sideseat.voice.VoiceConfig
import io.moodlab.sideseat.voice.VoiceModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 프로세스에 하나뿐인 부품 묶음(간단한 DI). 화면(MainActivity)과 코치 서비스가 같은 컨트롤러를 본다.
 * 화면이 닫혀도 프로세스가 살아 있는 동안 코치는 계속 돈다.
 */
object AppGraph {
    private var controller: SideSeatController? = null
    private lateinit var pleos: VehicleGateway
    private var debug: DebugVehicleGateway? = null
    private val gateway = MutableStateFlow<VehicleGateway?>(null)

    @Synchronized
    fun controller(context: Context): SideSeatController {
        controller?.let { return it }
        val app = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        pleos = PleosVehicleGateway(app)
        debug = if (BuildConfig.DEBUG) DebugVehicleGateway() else null
        val flow = MutableStateFlow<VehicleGateway>(pleos)
        pleos.start()
        val (voice, sfx, instructor) = VoiceModule.create(
            app,
            VoiceConfig(
                apiKey = BuildConfig.LLM_API_KEY,
                fastModel = BuildConfig.LLM_MODEL_FAST,
                reviewModel = BuildConfig.LLM_MODEL_REVIEW,
                provider = BuildConfig.LLM_PROVIDER,
                wake = debugWakeConfig(app),
            ),
        )
        val c = SideSeatController(
            scope, flow, voice, sfx, instructor, ProgressStore(app), wake = VoiceModule.wake,
            zones = io.moodlab.sideseat.vehicle.SchoolZoneAssets.load(app),
        )
        gatewayFlowRef = flow
        c.start()
        // 차종 키(VIN 앞자리)가 바뀌면 음성 모듈의 차종별 위치 안내·지식을 바꾼다.
        scope.launch {
            c.ui.map { it.vehicle.vehicleModelKey }.distinctUntilChanged().collect { VoiceModule.setVehicle(it) }
        }
        if (BuildConfig.DEBUG) DebugCommands(app, c).register()
        controller = c
        Log.i("SideSeatMeasure", "app graph ready debug=${BuildConfig.DEBUG} llm=${BuildConfig.LLM_API_KEY.isNotBlank()}")
        return c
    }

    private var gatewayFlowRef: MutableStateFlow<VehicleGateway>? = null

    /**
     * 디버그 빌드 실측용: files/wake_config.txt(`cycleMaxMs=12000`, `restartGapMs=300` 줄)가 있으면 연속 청취 설정을 바꾼다.
     * release는 항상 기본값.
     */
    private fun debugWakeConfig(app: Context): io.moodlab.sideseat.voice.WakeConfig {
        val base = io.moodlab.sideseat.voice.WakeConfig()
        if (!BuildConfig.DEBUG) return base
        val f = java.io.File(app.filesDir, "wake_config.txt")
        if (!f.exists()) return base
        val kv = f.readLines().mapNotNull { it.split("=").takeIf { p -> p.size == 2 }?.let { p -> p[0].trim() to p[1].trim().toLongOrNull() } }.toMap()
        return base.copy(
            cycleMaxMs = kv["cycleMaxMs"] ?: base.cycleMaxMs,
            restartGapMs = kv["restartGapMs"] ?: base.restartGapMs,
            // 나비·아비·차비 좁은 규칙(기본 꺼짐). wake_config.txt에 allowWeakWakeWords=1
            allowWeakWakeWords = kv["allowWeakWakeWords"]?.let { it != 0L } ?: base.allowWeakWakeWords,
        ).also { Log.i("SideSeatMeasure", "app debug wake config $it") }
    }

    /** 디버그 빌드: 화면 패널로 값을 바꾸는 가짜 차로 전환한다(Virtual Console 없이 UI 확인용). */
    fun debugGateway(): DebugVehicleGateway? = debug

    val currentGateway: StateFlow<VehicleGateway>? get() = gatewayFlowRef

    /** 첫 실행 권한 창에서 위치를 허용하면 게이트웨이를 다시 시작해 FusedLocation 구독을 연다. */
    @Synchronized
    fun restartVehicle() {
        val flow = gatewayFlowRef ?: return
        if (flow.value !== pleos) return
        pleos.stop()
        pleos.start()
        Log.i("SideSeatMeasure", "app vehicle restarted after permission grant")
    }

    @Synchronized
    fun useDebugVehicle(on: Boolean) {
        val flow = gatewayFlowRef ?: return
        val d = debug ?: return
        val next = if (on) d else pleos
        if (flow.value === next) return
        flow.value.stop()
        next.start()
        flow.value = next
        Log.i("SideSeatMeasure", "app vehicle source=${if (on) "debug" else "pleos"}")
    }
}
