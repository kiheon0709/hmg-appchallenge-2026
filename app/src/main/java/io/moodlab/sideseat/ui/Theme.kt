package io.moodlab.sideseat.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * 색 묶음. Pleos Connect 시스템 화면(설정 앱)에 맞춘다: 주간은 흰 바탕·검은 글자·검은 선택 상태,
 * 야간은 그 반전. 공개 디자인 문서에는 색 값이 그림으로만 있어서 에뮬레이터 캡처에서 맞췄다(decisions.md).
 * 글자색은 바탕 위에서 대비 4.5:1 이상이다.
 */
@Immutable
class Palette(
    val dark: Boolean,
    val bg: Color,
    /** 카드·목록 바탕. */
    val surface: Color,
    val surfaceHi: Color,
    /** 구분선. */
    val outline: Color,
    val text: Color,
    val textDim: Color,
    /** 선택 상태와 주 버튼(시스템의 검은 채움). */
    val primary: Color,
    val onPrimary: Color,
    /** 하나뿐인 강조색(시스템 주행 화면의 파랑). */
    val accent: Color,
    /** 강조·성공·위험 색으로 채운 면 위의 글자. */
    val onAccent: Color,
    val warn: Color,
    val danger: Color,
    val ok: Color,
    val star: Color,
    /** 주행 화면 위에 얹는 판. */
    val scrim: Color,
)

val DayPalette = Palette(
    dark = false,
    bg = Color(0xFFFFFFFF), surface = Color(0xFFF3F3F3), surfaceHi = Color(0xFFFFFFFF), outline = Color(0xFFD4D4D4),
    text = Color(0xFF0A0A0A), textDim = Color(0xFF666666),
    primary = Color(0xFF000000), onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFF0B57E0), onAccent = Color(0xFFFFFFFF),
    warn = Color(0xFFA85800), danger = Color(0xFFD0252B), ok = Color(0xFF087A38), star = Color(0xFFE39B00),
    scrim = Color(0xF2FFFFFF),
)

val NightPalette = Palette(
    dark = true,
    bg = Color(0xFF000000), surface = Color(0xFF191919), surfaceHi = Color(0xFF000000), outline = Color(0xFF3D3D3D),
    text = Color(0xFFF5F5F5), textDim = Color(0xFFA6A6A6),
    primary = Color(0xFFFFFFFF), onPrimary = Color(0xFF000000),
    accent = Color(0xFF5C9DFF), onAccent = Color(0xFF000000),
    warn = Color(0xFFFFB84A), danger = Color(0xFFFF5A64), ok = Color(0xFF35D07F), star = Color(0xFFFFC53D),
    scrim = Color(0xEB0C0C0C),
)

/** 지금 쓰는 색. 시스템의 주간·야간 모드를 따라 [SideSeatFrame]이 바꾼다. */
object Sc {
    internal var palette by mutableStateOf(NightPalette)
    val dark get() = palette.dark
    val bg get() = palette.bg
    val surface get() = palette.surface
    val surfaceHi get() = palette.surfaceHi
    val outline get() = palette.outline
    val text get() = palette.text
    val textDim get() = palette.textDim
    val primary get() = palette.primary
    val onPrimary get() = palette.onPrimary
    val accent get() = palette.accent
    val onAccent get() = palette.onAccent
    val warn get() = palette.warn
    val danger get() = palette.danger
    val ok get() = palette.ok
    val star get() = palette.star
    val scrim get() = palette.scrim
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
    /** 시스템 버튼·선택 칸은 모서리가 거의 직각이다. */
    val radius get() = dp(2f)
    val line get() = dp(1.5f)
    val touch get() = dp(84f)
}

val LocalSz = staticCompositionLocalOf { Sz(1f, true, 1280.dp, 800.dp) }

/** 모든 화면의 바깥 틀: 테마와 치수를 정하고, 가로(창 2칸)·세로(창 1칸)를 구분해 준다. */
@Composable
fun SideSeatFrame(modifier: Modifier = Modifier, content: @Composable BoxWithConstraintsScope.(Sz) -> Unit) {
    val dark = SideSeatUiDebug.forceDark ?: isSystemInDarkTheme()
    val p = remember(dark) { (if (dark) NightPalette else DayPalette).also { Sc.palette = it } }
    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(
                background = p.bg, surface = p.surface, primary = p.primary, onPrimary = p.onPrimary,
                onBackground = p.text, onSurface = p.text, outline = p.outline, error = p.danger,
            )
        } else {
            lightColorScheme(
                background = p.bg, surface = p.surface, primary = p.primary, onPrimary = p.onPrimary,
                onBackground = p.text, onSurface = p.text, outline = p.outline, error = p.danger,
            )
        },
    ) {
        // 바탕색은 여기서 칠한다(팔레트를 정한 뒤라 테마가 바뀌어도 어긋나지 않는다)
        BoxWithConstraints(modifier.fillMaxSize().background(p.bg)) {
            val wide = maxWidth > maxHeight * 1.08f
            val short = if (maxWidth < maxHeight) maxWidth else maxHeight
            // 세로(창 1칸)는 폭이 좁아도 글자가 가로 화면과 비슷한 크기가 되게 폭 640dp를 기준으로 잡는다
            val raw = if (wide) short / 900.dp else minOf(maxWidth / 640.dp, maxHeight / 900.dp)
            val sz = Sz(raw.coerceIn(0.5f, 1.5f), wide, maxWidth, maxHeight)
            CompositionLocalProvider(LocalSz provides sz) { content(sz) }
        }
    }
}
