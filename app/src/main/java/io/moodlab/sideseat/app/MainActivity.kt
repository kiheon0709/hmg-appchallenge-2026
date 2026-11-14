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
        if (result["pleos.car.permission.FUSED_LOCATION"] == true) AppGraph.restartVehicle()
        awaitingPermissions = false
        if (coveredByDialog) relaunchInNewTask()
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

    override fun onPause() {
        super.onPause()
        if (awaitingPermissions) coveredByDialog = true
    }

    /** 최상단 화면에서 Back은 앱을 숨긴다(CCP Back 가이드: finish 대신 moveTaskToBack). */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (vm.ui.value.mode == AppMode.HOME || vm.ui.value.mode == AppMode.COACH) moveTaskToBack(true)
        else vm.onAction(io.moodlab.sideseat.ui.UiAction.GoHome)
    }

    private fun requestMissingPermissions() {
        if (asked) return
        asked = true
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add("pleos.car.permission.FUSED_LOCATION")
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) {
            awaitingPermissions = true
            permissions.launch(wanted.toTypedArray())
        }
    }

    /**
     * 권한 대화상자가 이 태스크 위에 한 번 뜨면 Pleos 홈 화면이 태스크를 "권한 화면"으로 기억해,
     * 주행(D)이 되면 코치 화면을 "주행 중에는 사용할 수 없습니다."로 가린다(대화상자를 닫아도 그대로).
     * 그래서 대화상자가 닫히면 태스크를 지우고 새 태스크로 다시 연다. 새 태스크에서는 다시 묻지 않는다.
     */
    private fun relaunchInNewTask() {
        val again = packageManager.getLaunchIntentForPackage(packageName) ?: return
        finishAndRemoveTask()
        startActivity(again)
    }

    private companion object {
        /** 한 프로세스에서 권한은 한 번만 묻는다(다시 연 태스크에서 또 묻지 않게). */
        var asked = false
        var awaitingPermissions = false
        var coveredByDialog = false
    }

    override fun onDestroy() {
        probe?.stop()
        super.onDestroy()
    }
}
