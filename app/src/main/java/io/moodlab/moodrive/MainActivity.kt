package io.moodlab.moodrive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.moodlab.moodrive.ui.DriveScreen
import io.moodlab.moodrive.ui.Mc
import io.moodlab.moodrive.ui.MooTheme
import io.moodlab.moodrive.ui.Ms
import io.moodlab.moodrive.ui.OnboardingScreen
import io.moodlab.moodrive.ui.TripCardScreen
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MooDriveViewModel by viewModels()
    private var recognizer: SpeechRecognizer? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else Toast.makeText(this, "마이크 권한이 필요해요", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MooTheme {
                val ui by vm.ui.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(Mc.bg)) {
                    when {
                        !ui.onboarded && !ui.driving -> OnboardingScreen(onDone = vm::finishOnboarding)
                        ui.tripCard != null && !ui.driving -> TripCardScreen(
                            ui.tripCard!!, onClose = vm::dismissTripCard,
                            onSave = { Toast.makeText(this@MainActivity, "카드 저장은 다음 버전에서 지원해요", Toast.LENGTH_SHORT).show() },
                        )
                        else -> DriveScreen(
                            ui, onLike = vm::like, onDislike = vm::dislike, onMic = ::onMic,
                            onLongPressGear = vm::toggleDemoPanel, onRestStop = vm::answerRestStop,
                        )
                    }
                    if (ui.demoPanel) DemoPanel(ui, vm, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    private fun onMic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** TODO: Pleos SpeechToText SDK로 교체. 지금은 안드로이드 기본 인식기 */
    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "이 기기에서 음성 인식을 쓸 수 없어요", Toast.LENGTH_SHORT).show()
            return
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                vm.setListening(false)
                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(vm::onVoiceText)
            }
            override fun onError(error: Int) = vm.setListening(false)
            override fun onReadyForSpeech(params: Bundle?) = vm.setListening(true)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag()),
        )
    }

    override fun onDestroy() {
        recognizer?.destroy()
        super.onDestroy()
    }
}

/** 시연·디버그 전용 패널. 주행 화면 기어 칩을 길게 누르면 열림 (주행 중 조작 요소 수에서 제외되는 숨김 메뉴). */
@Composable
private fun DemoPanel(ui: UiState, vm: MooDriveViewModel, modifier: Modifier) {
    Column(
        modifier.padding(Ms.l).clip(RoundedCornerShape(Ms.radiusM)).background(Color(0xF01A1C22)).padding(Ms.m),
        verticalArrangement = Arrangement.spacedBy(Ms.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Ms.xs), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = vm::playScenario) { Text("▶ 비 오는 퇴근길 (3분)") }
            OutlinedButton(onClick = vm::useManual) { Text("수동") }
            ui.scenarioCaption?.let { Text("${ui.scenarioT}s · $it", color = Mc.text) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Ms.xs)) {
            val s = ui.snapshot
            OutlinedButton(onClick = vm.manual::cycleWiper) { Text("와이퍼 ${s?.wiper ?: ""}") }
            OutlinedButton(onClick = vm.manual::toggleLowBeam) { Text("하향등 ${if (s?.lowBeamOn == true) "ON" else "OFF"}") }
            OutlinedButton(onClick = vm.manual::toggleCongestion) { Text("정체") }
            OutlinedButton(onClick = vm.manual::toggleCruise) { Text("크루즈") }
            OutlinedButton(onClick = vm.manual::toggleGear) { Text("기어 ${s?.gear?.name ?: ""}") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ms.xs)) {
            Switch(checked = ui.allowAmbientWhileDriving, onCheckedChange = vm::setAllowAmbientWhileDriving)
            Text("주행 중 실내 조명 변경 허용 (공식 답변 전까지 OFF)", color = Mc.text)
            OutlinedButton(onClick = vm::toggleDemoPanel) { Text("닫기") }
        }
    }
}
