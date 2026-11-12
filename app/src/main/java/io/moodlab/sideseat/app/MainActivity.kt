package io.moodlab.sideseat.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.moodlab.sideseat.BuildConfig
import io.moodlab.sideseat.app.measure.SignalProbe

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var probe: SignalProbe? = null

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        android.util.Log.i("SideSeatMeasure", "app permissions $result")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 실측 도구는 디버그 빌드에서 `--ez probe true`로 켤 때만(TTS·STT를 따로 붙잡기 때문)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("probe", false)) {
            probe = SignalProbe(applicationContext).also { it.start() }
        }
        requestMissingPermissions()
        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            AppScreens(ui, vm::onAction, vm.debugChange)
        }
    }

    override fun onStart() {
        super.onStart()
        CoachService.start(this)
    }

    /** 최상단 화면에서 Back은 앱을 숨긴다(CCP Back 가이드: finish 대신 moveTaskToBack). */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (vm.ui.value.mode == AppMode.HOME || vm.ui.value.mode == AppMode.COACH) moveTaskToBack(true)
        else vm.onAction(io.moodlab.sideseat.ui.UiAction.GoHome)
    }

    private fun requestMissingPermissions() {
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add("pleos.car.permission.FUSED_LOCATION")
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
    }

    override fun onDestroy() {
        probe?.stop()
        super.onDestroy()
    }
}
