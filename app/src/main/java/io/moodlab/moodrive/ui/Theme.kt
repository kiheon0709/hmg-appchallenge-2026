package io.moodlab.moodrive.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** design/tokens.json 과 같은 값. 바꿀 땐 tokens.json·tokens.css·이 파일을 같이 바꾼다. */
object Mc {
    val bg = Color(0xFF0B0C10)
    val surface = Color(0xFF15171D)
    val surfaceRaised = Color(0xFF1E2129)
    val outline = Color(0xFF2C303A)
    val text = Color(0xFFF4F5F7)
    val textDim = Color(0xFFA9AFBC)
    val textMuted = Color(0xFF7C8392)
    val accent = Color(0xFFFF9F5A)
    val warning = Color(0xFFFFC46B)
    val positive = Color(0xFF7BE0A6)
    val onAccent = Color(0xFF16110C)
    /** 글자 뒤 반투명 판 (조명 글로우 위에서도 대비 확보) */
    val scrim = Color(0xC70B0C10)
}

object Ms {
    // 프로토타입 px == 앱 dp/sp (1920×720 기준 디자인)
    val sceneDisplay = 104.sp
    val display = 64.sp
    val title = 40.sp
    val body = 26.sp
    val label = 24.sp
    val caption = 20.sp

    val xs = 8.dp
    val s = 16.dp
    val m = 24.dp
    val l = 32.dp
    val xl = 48.dp

    val radiusS = 16.dp
    val radiusM = 24.dp
    val radiusL = 32.dp

    val touchMin = 76.dp
    val touchPrimary = 96.dp

    const val ambientMs = 4000
    const val sceneMs = 600
}

@Composable
fun MooTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Mc.bg, surface = Mc.surface, primary = Mc.accent, onPrimary = Mc.onAccent,
            onBackground = Mc.text, onSurface = Mc.text, outline = Mc.outline,
        ),
        content = content,
    )
}
