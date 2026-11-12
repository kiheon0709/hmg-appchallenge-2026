package io.moodlab.sideseat.app

import io.moodlab.sideseat.core.ChapterResult
import io.moodlab.sideseat.core.SafeDialView
import io.moodlab.sideseat.core.TrainingProgress
import io.moodlab.sideseat.core.TrainingView
import io.moodlab.sideseat.core.TurnSignalGameView
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.core.WindowBuzzerView
import io.moodlab.sideseat.ui.CoachUi
import io.moodlab.sideseat.ui.HomeUi
import io.moodlab.sideseat.ui.ReviewUi

/** 차 상태가 정하는 화면. 사용자가 고르지 않는다(명세 4장). */
enum class AppMode { HOME, TRAINING, RESULT, MINIGAME, COACH, REVIEW }

enum class MiniGameKind { SAFE_DIAL, TURN_SIGNAL, WINDOW_BUZZER }

/** 화면 하나가 그리는 데 필요한 것 전부. [SideSeatController]가 만든다. */
data class AppUi(
    val mode: AppMode = AppMode.HOME,
    val vehicle: VehicleState = VehicleState(timestampMs = 0, parked = true),
    val home: HomeUi = HomeUi(),
    val training: TrainingView? = null,
    val result: ChapterResult? = null,
    val progress: TrainingProgress = TrainingProgress(),
    val coach: CoachUi = CoachUi(),
    val review: ReviewUi? = null,
    val miniGame: MiniGameKind? = null,
    val safeDial: SafeDialView? = null,
    val turnGame: TurnSignalGameView? = null,
    val windowGame: WindowBuzzerView? = null,
    val debugPanel: Boolean = false,
    /** 디버그 빌드에서 화면 패널 값으로 움직이는 중. */
    val usingDebugVehicle: Boolean = false,
)
