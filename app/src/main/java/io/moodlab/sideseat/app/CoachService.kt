package io.moodlab.sideseat.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 앱이 뒤로 가도(내비가 앞에 있어도) 코치가 말할 수 있게 프로세스를 붙잡아 두는 포그라운드 서비스.
 * 백그라운드에서는 포그라운드 서비스를 새로 시작할 수 없어서, 화면이 보일 때 미리 시작해 둔다.
 * 실제 일은 [SideSeatController]가 한다. 여기서는 알림 문구만 모드에 맞춘다.
 */
class CoachService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "옆자리 코치", NotificationManager.IMPORTANCE_LOW))
        startInForeground(textFor(AppMode.HOME))
        val controller = AppGraph.controller(this)
        scope.launch {
            controller.ui.map { it.mode }.distinctUntilChanged().collect { mode ->
                nm.notify(ID, notification(textFor(mode)))
            }
        }
        Log.i("SideSeatMeasure", "app coach service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(text: String) {
        val n = notification(text)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(ID, n)
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("옆자리")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun textFor(mode: AppMode) = when (mode) {
        AppMode.COACH -> "선배가 옆자리에서 같이 보고 있어요"
        AppMode.REVIEW -> "오늘 운전을 같이 돌아봐요"
        else -> "세워 둔 지금, 진짜 스위치로 연습해요"
    }

    companion object {
        private const val CHANNEL = "coach"
        private const val ID = 1

        fun start(context: Context) = runCatching {
            context.startForegroundService(Intent(context, CoachService::class.java))
        }.onFailure { Log.w("SideSeatMeasure", "app coach service start failed: $it") }
    }
}
