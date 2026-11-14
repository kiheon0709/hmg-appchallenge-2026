import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.moodlab.sideseat.ui.scene.BackdropFrame
import io.moodlab.sideseat.ui.scene.SceneBackdrop
import io.moodlab.sideseat.ui.scene.drawStill
import org.jetbrains.skia.Image
import java.io.File

/** 렌더 도구용 실사 배경: 영상 대신 앱 assets의 정지 그림을 그린다. */
class StillSceneBackdrop(private val assets: File) : SceneBackdrop {
    private val cache = HashMap<String, ImageBitmap>()

    @Composable
    override fun Layer(frame: BackdropFrame, modifier: Modifier) {
        Canvas(modifier) {
            simT // 프레임마다 다시 그린다
            val image = cache.getOrPut(frame.clip.still) { Image.makeFromEncoded(File(assets, frame.clip.still).readBytes()).toComposeImageBitmap() }
            drawStill(frame, image)
        }
    }
}
