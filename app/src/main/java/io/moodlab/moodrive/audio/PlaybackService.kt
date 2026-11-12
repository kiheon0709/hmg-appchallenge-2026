package io.moodlab.moodrive.audio

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * 포그라운드 재생 서비스. 내비가 앞으로 와도 프로세스가 멈추지 않게 한다.
 * ExoPlayer의 오디오 포커스 처리: GAIN 재생, LOSS 정지, LOSS_TRANSIENT 일시정지 후 복귀, CAN_DUCK 감쇄.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(attributes, /* handleAudioFocus = */ true)
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_ONE }
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
