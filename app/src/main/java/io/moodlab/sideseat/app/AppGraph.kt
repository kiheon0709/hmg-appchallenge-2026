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
            ),
        )
        val c = SideSeatController(scope, flow, voice, sfx, instructor, ProgressStore(app))
        gatewayFlowRef = flow
        c.start()
        if (BuildConfig.DEBUG) DebugCommands(app, c).register()
        controller = c
        Log.i("SideSeatMeasure", "app graph ready debug=${BuildConfig.DEBUG} llm=${BuildConfig.LLM_API_KEY.isNotBlank()}")
        return c
    }

    private var gatewayFlowRef: MutableStateFlow<VehicleGateway>? = null

    /** 디버그 빌드: 화면 패널로 값을 바꾸는 가짜 차로 전환한다(Virtual Console 없이 UI 확인용). */
    fun debugGateway(): DebugVehicleGateway? = debug

    val currentGateway: StateFlow<VehicleGateway>? get() = gatewayFlowRef

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
