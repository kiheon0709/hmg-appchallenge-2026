package io.moodlab.moodrive.vehicle

import android.content.Context
import android.util.Log
import io.moodlab.moodrive.core.AmbientCommand
import io.moodlab.moodrive.core.VehicleSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Pleos Vehicle SDK / NaviHelper → [VehicleSnapshot] 어댑터.
 *
 * TODO(맥, SDK 받은 뒤): 아래 순서로 구현. 클라우드 환경에선 SDK 저장소·문서 접근이 막혀 있어 비워 둠.
 *  1. 각 도메인 checkXXXCapabilities()로 지원 여부 확인 → 미지원 신호는 기본값 유지 (graceful degradation)
 *  2. Wiper: getCurrentWiperState / registerWiperState → WiperLevel 매핑 (강도 단계 값 확인 필요)
 *  3. Light: registerLowBeamLightState → lowBeamOn
 *  4. DrivingMode: registerDrivingModeState, registerCruiseControlState
 *  5. HVAC: registerHvacTemperatureCurrent, getHvacTemperature
 *  6. 기어: 어느 도메인에서 오는지 확인 (DrivingMode? CarInfo?) → Gear
 *  7. NaviHelper: TripInfo/DrivingInfo의 남은 시간, TrafficCongestionInfo → etaMinutes, congestion
 *  8. FusedLocation: 위치 → 일몰/일출 계산 (sunsetMinute, sunriseMinute),
 *     정확도 50m 초과면 gpsWeak = true (터널 확인용), 속도 → 기어를 못 받을 때 주행 판정 대체
 *  각 콜백에서 최신 스냅샷을 copy로 갱신해 MutableStateFlow에 넣으면 된다 (ManualSignalSource 참고).
 */
class PleosVehicleSignalSource(private val context: Context) : VehicleSignalSource {
    override val snapshots: Flow<VehicleSnapshot> = emptyFlow<VehicleSnapshot>().also {
        Log.w(TAG, "Pleos SDK adapter not implemented yet")
    }

    private companion object {
        const val TAG = "PleosVehicle"
    }
}

/** 실내 조명 출력. 미리보기(화면 시각화)와 실제 Pleos Light API 두 구현을 같이 쓴다. */
interface AmbientController {
    fun apply(command: AmbientCommand)
}

/**
 * TODO(맥): Light 도메인 연결
 *  - 시작 시 setAmbientColorAutoByDriveModeEnabled(false), 종료 시 원래 값으로 복원
 *  - setAmbientLightBrightnessColor(밝기, 색). 색이 프리셋/인덱스 방식이면 가장 가까운 값으로 양자화
 *  - transitionMs는 API가 지원하지 않으면 여러 단계로 나눠 호출하지 말고 한 번에 (깜빡임 방지)
 *  - D단에서 호출이 거부되는지 에뮬레이터에서 직접 시험 → 결과를 docs/STATUS.md에 기록
 */
class PleosAmbientController(private val context: Context) : AmbientController {
    override fun apply(command: AmbientCommand) {
        Log.i("PleosAmbient", "TODO set ambient ${command.color.hex()} @${command.brightness}%")
    }
}
