package io.moodlab.sideseat.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.moodlab.sideseat.ui.UiAction
import kotlinx.coroutines.flow.StateFlow

/**
 * 화면과 컨트롤러 사이의 얇은 층. 상태와 모드 전환은 프로세스 단위 [SideSeatController]가 갖고 있어서
 * 화면이 다시 만들어지거나(창 크기 변경) 앱이 뒤로 가도 그대로 남는다.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val controller = AppGraph.controller(app)
    val ui: StateFlow<AppUi> = controller.ui

    fun onAction(a: UiAction) {
        if (a == UiAction.ToggleDebugPanel && AppGraph.debugGateway() == null) return
        controller.onAction(a)
    }

    /** 디버그 패널에서 값을 바꾸면 가짜 차로 전환하고 그 값을 그대로 넣는다. 릴리스 빌드에서는 null. */
    val debugChange: ((io.moodlab.sideseat.core.VehicleState) -> Unit)? =
        AppGraph.debugGateway()?.let { g -> { s -> AppGraph.useDebugVehicle(true); g.update { s } } }

    fun useDebugVehicle(on: Boolean) = AppGraph.useDebugVehicle(on)
}
