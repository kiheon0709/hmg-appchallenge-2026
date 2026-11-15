package io.moodlab.sideseat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import io.moodlab.sideseat.voice.Persona

private const val PAGES = 3

/**
 * 첫 실행 소개. 타비가 무엇을 하는 앱인지 세 장으로 보여 준다: 누구인지, 언제 무엇을 하는지, 차를 직접 조작해 연습한다는 것.
 * 한 번 보면 다시 뜨지 않고, 홈의 "타비 소개 다시 보기"로 다시 연다. 정차 중에만 보인다.
 */
@Composable
fun IntroScreen(initialPage: Int = 0, onDone: () -> Unit) {
    SideSeatFrame { sz ->
        var page by remember { mutableIntStateOf(initialPage.coerceIn(0, PAGES - 1)) }
        val visual: @Composable (Modifier) -> Unit = { m -> IntroVisual(page, m) }
        val header: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dots(page)
                Spacer(Modifier.weight(1f))
                BigButton("건너뛰기", onClick = onDone)
            }
        }
        val buttons: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(sz.gap)) {
                if (page > 0) BigButton("이전", Modifier.weight(0.4f)) { page-- }
                BigButton(if (page == PAGES - 1) "시작하기" else "다음", Modifier.weight(1f), primary = true) {
                    if (page == PAGES - 1) onDone() else page++
                }
            }
        }
        if (sz.wide) {
            Row(Modifier.fillMaxSize().padding(sz.pad), horizontalArrangement = Arrangement.spacedBy(sz.dp(40f))) {
                visual(Modifier.weight(0.46f).fillMaxHeight())
                Column(Modifier.weight(0.54f).fillMaxHeight().padding(vertical = sz.dp(8f)), verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                    header()
                    Spacer(Modifier.weight(1f))
                    IntroText(page)
                    Spacer(Modifier.weight(1f))
                    buttons()
                }
            }
        } else {
            // 세로: 내용은 스크롤하고 "다음" 버튼은 늘 아래에 보인다
            Column(Modifier.fillMaxSize().padding(sz.pad), verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(sz.gap)) {
                    visual(Modifier.fillMaxWidth().height(sz.height * 0.32f))
                    header()
                    IntroText(page)
                }
                buttons()
            }
        }
    }
}

@Composable
private fun Dots(page: Int) {
    val sz = LocalSz.current
    Row(horizontalArrangement = Arrangement.spacedBy(sz.dp(10f)), verticalAlignment = Alignment.CenterVertically) {
        repeat(PAGES) { i ->
            Box(Modifier.height(sz.dp(10f)).width(sz.dp(if (i == page) 34f else 10f)).clip(CircleShape).background(if (i == page) Sc.accent else Sc.outline))
        }
        Spacer(Modifier.width(sz.dp(6f)))
        Text("${page + 1} / $PAGES", color = Sc.textDim, fontSize = sz.caption, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IntroText(page: Int) {
    val sz = LocalSz.current
    val name = Persona.NAME
    Column(verticalArrangement = Arrangement.spacedBy(sz.dp(18f))) {
        when (page) {
            0 -> {
                Headline("처음 타는 차,\n혼자 하는 운전.")
                Body("옆자리에 운전 코치 ${name}가 앉아요.")
                Column(verticalArrangement = Arrangement.spacedBy(sz.dp(10f))) {
                    Bullet("이제 막 면허를 딴 초보 운전")
                    Bullet("오래 쉬다 다시 잡는 운전대")
                    Bullet("렌터카·새 차처럼 처음 타는 차")
                }
                Body("이 차에서, 이 상황이 처음일 때 ${name}가 같이 해요.", dim = true)
            }
            1 -> {
                Headline("${name}가 하는 일,\n세 가지 때")
                Moment("1", "출발 전", "세워 둔 차에서 폭우·안개·급정거 같은 돌발 상황을 미리 연습해요.", now = true)
                Moment("2", "달릴 때", "꼭 필요할 때만 한 문장으로 알려 줘요. \"비가 와요. 전조등을 켜 주세요.\"")
                Moment("3", "도착하면", "오늘 운전을 같이 돌아보고, 필요한 것만 1분 연습해요.")
            }
            else -> {
                Headline("이 차의 와이퍼와 브레이크로\n직접 연습해요")
                Body("와이퍼 레버, 전조등, 브레이크를 실제로 움직이면 ${name}가 알아봐요. 손이 스위치 자리를 기억하게 하는 연습이에요.")
                Column(verticalArrangement = Arrangement.spacedBy(sz.dp(10f))) {
                    Bullet("연습은 주차(P)에 두고 세워 둔 동안에만 해요.")
                    Bullet("${name}에게 말로 물어볼 수 있어서 마이크를 써요.")
                    Bullet("제한속도와 어린이 보호구역을 알려 주려고 위치를 써요.")
                }
            }
        }
    }
}

@Composable
private fun Headline(text: String) {
    val sz = LocalSz.current
    Text(text, color = Sc.text, fontSize = sz.sp(54f), fontWeight = FontWeight.Bold, lineHeight = sz.sp(54f) * 1.22f)
}

@Composable
private fun Body(text: String, dim: Boolean = false) {
    val sz = LocalSz.current
    val size = if (dim) sz.body else sz.h2
    Text(text, color = if (dim) Sc.textDim else Sc.text, fontSize = size, fontWeight = FontWeight.Medium, lineHeight = size * 1.35f)
}

@Composable
private fun Bullet(text: String) {
    val sz = LocalSz.current
    Row {
        Dot(Sc.accent, sz.dp(10f), Modifier.padding(top = sz.dp(13f), end = sz.dp(14f)))
        Text(text, color = Sc.text, fontSize = sz.body, lineHeight = sz.body * 1.35f)
    }
}

/** "세 가지 때"의 한 줄: 번호, 언제, 무엇을. */
@Composable
private fun Moment(no: String, whenText: String, what: String, now: Boolean = false) {
    val sz = LocalSz.current
    Row(Modifier.fillMaxWidth().card().padding(sz.dp(20f)), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(sz.dp(60f)).clip(RoundedCornerShape(sz.dp(16f))).background(if (now) Sc.accent else Sc.surfaceHi), contentAlignment = Alignment.Center) {
            Text(no, color = if (now) Sc.onAccent else Sc.text, fontSize = sz.h2, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(sz.dp(18f)))
        Column(verticalArrangement = Arrangement.spacedBy(sz.dp(2f))) {
            Text(whenText, color = Sc.text, fontSize = sz.sp(28f), fontWeight = FontWeight.Bold)
            Text(what, color = Sc.textDim, fontSize = sz.label, lineHeight = sz.label * 1.35f)
        }
    }
}

/** 왼쪽(세로에서는 위) 그림: 3D 훈련 장면 위에 타비 표식이나 조작 목록을 얹는다. 글만 있는 화면이 되지 않게 한다. */
@Composable
private fun IntroVisual(page: Int, modifier: Modifier) {
    val sz = LocalSz.current
    val base = Color(0xFF0B0E13)
    Box(modifier.card(RoundedCornerShape(sz.dp(28f)), base)) {
        LocalHomeArt.current?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to base.copy(alpha = 0.25f), 0.45f to base.copy(alpha = 0.35f), 1f to base.copy(alpha = 0.92f))))
        when (page) {
            0 -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                // 세로 창은 그림 자리가 낮아서 작게 그린다
                SeniorBadge(size = sz.dp(if (sz.wide) 230f else 110f), mood = TabiMood.HAPPY)
                Spacer(Modifier.height(sz.dp(if (sz.wide) 22f else 8f)))
                Text(Persona.NAME, color = Color.White, fontSize = if (sz.wide) sz.display else sz.title, fontWeight = FontWeight.Bold)
                Text("옆자리 운전 코치", color = Color(0xFFD5D9E0), fontSize = if (sz.wide) sz.h2 else sz.label, fontWeight = FontWeight.Medium)
            }
            1 -> Column(Modifier.align(Alignment.BottomStart).padding(sz.pad), verticalArrangement = Arrangement.spacedBy(sz.dp(12f))) {
                // 달릴 때의 화면처럼: 타비가 한 문장으로 말한다
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SeniorBadge(size = sz.dp(84f), mood = TabiMood.SPEAKING)
                    Spacer(Modifier.width(sz.dp(14f)))
                    Text(Persona.NAME, color = Color.White, fontSize = sz.h2, fontWeight = FontWeight.Bold)
                }
                Text("비가 와요.\n전조등을 켜 주세요.", color = Color.White, fontSize = sz.sp(50f), fontWeight = FontWeight.SemiBold, lineHeight = sz.sp(50f) * 1.25f)
            }
            else -> Column(
                Modifier.align(Alignment.BottomStart).padding(sz.pad).fillMaxWidth().clip(RoundedCornerShape(sz.radius)).background(base.copy(alpha = 0.78f)).padding(sz.dp(22f)),
                verticalArrangement = Arrangement.spacedBy(sz.dp(14f)),
            ) {
                // 훈련 화면의 조작 목록처럼: 실제 스위치를 움직이면 체크된다
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tabi(TabiMood.CHEER, size = sz.dp(64f))
                    Spacer(Modifier.width(sz.dp(12f)))
                    Text("차를 직접 조작해요", color = Color(0xFFA6ABB5), fontSize = sz.caption, fontWeight = FontWeight.SemiBold)
                }
                Check("와이퍼 빠르게", done = true)
                Check("전조등 켜기", done = true)
                Check("비상등 켜기", done = false)
            }
        }
    }
}

@Composable
private fun Check(label: String, done: Boolean) {
    val sz = LocalSz.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(sz.dp(36f))) {
            val s = size.minDimension
            val c = Offset(s / 2f, s / 2f)
            if (done) {
                drawCircle(Color(0xFF35D07F), s * 0.47f, c)
                val a = Offset(s * 0.29f, s * 0.52f); val b = Offset(s * 0.44f, s * 0.66f); val d = Offset(s * 0.72f, s * 0.36f)
                drawLine(Color.Black, a, b, s * 0.1f, cap = StrokeCap.Round)
                drawLine(Color.Black, b, d, s * 0.1f, cap = StrokeCap.Round)
            } else {
                drawCircle(Color.White, s * 0.42f, c, style = Stroke(s * 0.09f))
            }
        }
        Spacer(Modifier.width(sz.dp(14f)))
        Text(label, color = if (done) Color(0xFFA6ABB5) else Color.White, fontSize = sz.sp(30f), fontWeight = if (done) FontWeight.Medium else FontWeight.Bold)
    }
}
