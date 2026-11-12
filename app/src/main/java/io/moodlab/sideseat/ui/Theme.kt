package io.moodlab.sideseat.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 색. 글자색은 바탕(bg·surface) 위에서 대비 4.5:1 이상이 되게 골랐다. */
object Sc {
    val bg = Color(0xFF070A12)
    val surface = Color(0xFF111725)
    val surfaceHi = Color(0xFF1A2233)
    val outline = Color(0xFF2B3753)
    val text = Color(0xFFF3F6FB)
    val textDim = Color(0xFFB4BFD1)
    val accent = Color(0xFF53D8FF)
    val onAccent = Color(0xFF04121A)
    val pink = Color(0xFFFF5FB0)
    val warn = Color(0xFFFFB84A)
    val danger = Color(0xFFFF5A68)
    val ok = Color(0xFF3DF08A)
    val star = Color(0xFFFFD25E)
    val scrim = Color(0xD9070A12)
}

/** 창 크기에 맞춰 늘고 주는 치수. 기준은 짧은 변 900dp. */
@Immutable
class Sz(val scale: Float, val wide: Boolean, val width: Dp, val height: Dp) {
    fun dp(v: Float): Dp = (v * scale).dp
    fun sp(v: Float): TextUnit = (v * scale).sp
    val display get() = sp(64f)
    val title get() = sp(42f)
    val h2 get() = sp(32f)
    val body get() = sp(26f)
    val label get() = sp(22f)
    val caption get() = sp(18f)
    val pad get() = dp(28f)
    val gap get() = dp(16f)
    val radius get() = dp(24f)
    val touch get() = dp(84f)
}

val LocalSz = staticCompositionLocalOf { Sz(1f, true, 1280.dp, 800.dp) }

/** 모든 화면의 바깥 틀: 테마와 치수를 정하고, 가로(창 2칸)·세로(창 1칸)를 구분해 준다. */
@Composable
fun SideSeatFrame(modifier: Modifier = Modifier, content: @Composable BoxWithConstraintsScope.(Sz) -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Sc.bg, surface = Sc.surface, primary = Sc.accent, onPrimary = Sc.onAccent,
            onBackground = Sc.text, onSurface = Sc.text, outline = Sc.outline, error = Sc.danger,
        ),
    ) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight * 1.08f
            val short = if (maxWidth < maxHeight) maxWidth else maxHeight
            val sz = Sz((short / 900.dp).coerceIn(0.5f, 1.5f), wide, maxWidth, maxHeight)
            CompositionLocalProvider(LocalSz provides sz) { content(sz) }
        }
    }
}
