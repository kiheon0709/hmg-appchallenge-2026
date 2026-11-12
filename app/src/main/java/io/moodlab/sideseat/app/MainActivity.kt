package io.moodlab.sideseat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import io.moodlab.sideseat.BuildConfig
import io.moodlab.sideseat.app.measure.SignalProbe

class MainActivity : ComponentActivity() {
    private var probe: SignalProbe? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) probe = SignalProbe(applicationContext).also { it.start() }
        setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0E14)), contentAlignment = Alignment.Center) {
                Text("옆자리 실측 모드", color = Color.White, fontSize = 32.sp)
            }
        }
    }

    override fun onDestroy() {
        probe?.stop()
        super.onDestroy()
    }
}
