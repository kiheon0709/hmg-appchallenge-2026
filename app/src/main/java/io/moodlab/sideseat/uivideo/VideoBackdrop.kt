package io.moodlab.sideseat.uivideo

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.moodlab.sideseat.ui.scene.BackdropClip
import io.moodlab.sideseat.ui.scene.BackdropFrame
import io.moodlab.sideseat.ui.scene.SceneBackdrop
import io.moodlab.sideseat.ui.scene.drawStill
import kotlin.math.abs

/**
 * 실사 배경(안드로이드). assets의 영상을 소리 없이 되풀이 재생하고, 영상이 없는 소재와 영상이 뜨기 전에는 정지 그림을 보여 준다.
 * 화면이 사라지면(P를 벗어나 훈련이 닫히면) 재생기도 바로 풀린다. 정차 중 화면에서만 쓴다.
 */
class VideoSceneBackdrop : SceneBackdrop {
    @Composable
    override fun Layer(frame: BackdropFrame, modifier: Modifier) {
        val context = LocalContext.current.applicationContext
        val player = remember { ClipPlayer(context) }
        val stills = remember { HashMap<String, ImageBitmap?>() }
        var tick by remember { mutableLongStateOf(0L) }
        DisposableEffect(Unit) { onDispose { player.release() } }
        LaunchedEffect(Unit) {
            while (true) withFrameNanos { now -> player.apply(frame); tick = now }
        }
        Box(modifier.background(Color.Black)) {
            AndroidView(factory = { TextureView(it).also(player::attach) }, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") tick
                val clip = frame.clip
                if (!player.showing(clip)) {
                    val image = stills.getOrPut(clip.still) {
                        runCatching { context.assets.open(clip.still).use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
                    }
                    if (image != null) drawStill(frame, image)
                }
            }
        }
    }
}

/** 영상 한 편을 붙잡고 있는 재생기. 모든 호출은 메인 스레드에서 온다. */
private class ClipPlayer(private val context: Context) : TextureView.SurfaceTextureListener {
    private var view: TextureView? = null
    private var surface: Surface? = null
    private var player: MediaPlayer? = null
    private var loaded: BackdropClip? = null
    private var prepared = false
    private var rendered = false
    private var appliedRate = -1f
    private val matrix = Matrix()
    private var mx = Float.NaN
    private var my = Float.NaN
    private var mw = Float.NaN

    fun attach(v: TextureView) {
        view = v
        v.isOpaque = true
        v.surfaceTextureListener = this
    }

    /** 이 소재의 영상이 화면에 나오고 있다(아니면 정지 그림을 대신 그려야 한다). */
    fun showing(clip: BackdropClip): Boolean = clip.video != null && loaded == clip && rendered

    fun apply(frame: BackdropFrame) {
        val v = view ?: return
        val want = frame.clip
        if (want.video != null && want != loaded && surface != null) open(want)
        if (v.width > 0 && want.video != null) {
            frame.layout(v.width.toFloat(), v.height.toFloat())
            if (frame.x0 != mx || frame.y0 != my || frame.dw != mw) {
                mx = frame.x0; my = frame.y0; mw = frame.dw
                matrix.setScale(frame.dw / v.width, frame.dh / v.height)
                matrix.postTranslate(frame.x0, frame.y0)
                v.setTransform(matrix)
            }
        }
        val mp = player ?: return
        if (!prepared) return
        try {
            if (want.video == null) {
                if (mp.isPlaying) mp.pause()
                return
            }
            if (frame.seekTo >= 0f) {
                mp.seekTo((frame.seekTo * 1000f).toLong(), MediaPlayer.SEEK_CLOSEST)
                frame.seekTo = -1f
            }
            val rate = frame.rate
            if (rate < 0.06f) {
                if (mp.isPlaying) mp.pause()
                appliedRate = 0f
            } else if (abs(rate - appliedRate) > 0.04f || !mp.isPlaying) {
                // 속도를 정하면 재생이 시작된다
                mp.playbackParams = mp.playbackParams.setSpeed(rate.coerceIn(0.25f, 2f))
                if (!mp.isPlaying) mp.start()
                appliedRate = rate
            }
            frame.positionSec = mp.currentPosition / 1000f
        } catch (e: IllegalStateException) {
            Log.w(TAG, "backdrop player state: ${e.message}")
        }
    }

    private fun open(clip: BackdropClip) {
        val path = clip.video ?: return
        closePlayer()
        loaded = clip
        try {
            val mp = MediaPlayer()
            context.assets.openFd(path).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            mp.setSurface(surface)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setOnPreparedListener { prepared = true; appliedRate = -1f }
            mp.setOnErrorListener { _, what, extra -> Log.w(TAG, "backdrop player error $what/$extra"); true }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            // 영상을 못 열면 정지 그림으로 남는다
            Log.w(TAG, "backdrop open failed: ${e.message}")
        }
    }

    private fun closePlayer() {
        player?.let { runCatching { it.release() } }
        player = null
        prepared = false
        rendered = false
        appliedRate = -1f
    }

    fun release() {
        closePlayer()
        loaded = null
        surface?.release()
        surface = null
        view?.surfaceTextureListener = null
        view = null
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(st)
        mx = Float.NaN
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) { mx = Float.NaN }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        closePlayer()
        loaded = null
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
        if (prepared) rendered = true
    }

    private companion object { const val TAG = "SideSeatMeasure" }
}
