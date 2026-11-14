import androidx.compose.runtime.Composable
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.ui.CoachScreen
import io.moodlab.sideseat.ui.CoachUi

@Composable fun CoachShot(ui: CoachUi, car: VehicleState) { CoachScreen(ui, {}, car) }
