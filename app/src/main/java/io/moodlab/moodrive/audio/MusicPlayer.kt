package io.moodlab.moodrive.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.moodlab.moodrive.core.Track

private const val TAG = "MooDriveMeasure"
private const val DUCK_VOLUME = 0.3f

/** [PlaybackService]의 플레이어를 앱에서 다룬다. 메인 스레드에서만 호출한다. */
class MusicPlayer(context: Context) {
    private val future: ListenableFuture<MediaController> = MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, PlaybackService::class.java)),
    ).buildAsync()
    private var controller: MediaController? = null
    private var pendingTrack: Track? = null
    private var active = false

    init {
        future.addListener({
            val c = runCatching { future.get() }.onFailure { Log.w(TAG, "music controller failed: $it") }.getOrNull()
                ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.i(TAG, "music isPlaying=$isPlaying suppression=${c.playbackSuppressionReason} state=${c.playbackState}")
                }
            })
            pendingTrack?.let(::play)
            c.playWhenReady = active
        }, ContextCompat.getMainExecutor(context))
    }

    fun play(track: Track) {
        Log.i(TAG, "music play id=${track.id} source=${track.source}")
        val c = controller
        if (c == null) {
            pendingTrack = track
            return
        }
        pendingTrack = null
        if (track.source.isEmpty()) {
            c.stop()
            c.clearMediaItems()
            return
        }
        c.setMediaItem(
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(Uri.parse("asset:///${track.source}"))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build())
                .build(),
        )
        c.prepare()
        c.playWhenReady = active
    }

    /** 주행 중에만 재생한다. 정차 장면에서는 멈춘다. */
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        controller?.playWhenReady = value
    }

    /** DJ 멘트 동안 앱이 직접 음악을 줄인다. 시스템 덕킹은 보장되지 않는다(운영사무국 안내). */
    fun setDucked(ducked: Boolean) {
        controller?.volume = if (ducked) DUCK_VOLUME else 1f
    }

    /** 오디오 포커스를 잃어 소리가 나지 않는 상태. 이때는 곡 위치를 진행하지 않는다. */
    val isSilenced: Boolean
        get() {
            val c = controller ?: return false
            return active && c.mediaItemCount > 0 && !c.isPlaying
        }

    fun release() {
        MediaController.releaseFuture(future)
    }
}
