package io.moodlab.sideseat.voice

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 오디오 포커스 한 묶음. Pleos 가이드(audio-focus-development)대로:
 * 재생 전에 요청, 음악과 공존하는 안내음은 GAIN_TRANSIENT_MAY_DUCK,
 * LOSS → 즉시 멈추고 포기, LOSS_TRANSIENT(Gleo 호출 등) → 즉시 멈추고 다시 GAIN을 기다림.
 * 내비·통화가 말하는 중이면 지연 획득(DELAYED)을 받아 기다린다.
 *
 * 메인 스레드에서만 부른다.
 */
internal class AudioFocusGate(
    private val audio: AudioManager,
    val attributes: AudioAttributes,
    private val name: String,
    private val onLoss: (permanent: Boolean) -> Unit,
    private val onDuck: (ducked: Boolean) -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private var held = false
    private var pending: CompletableDeferred<Boolean>? = null

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        Log.i(MEASURE_TAG, "focus[$name] change=${changeName(change)}")
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                held = true
                onDuck(false)
                pending?.complete(true)
                pending = null
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                held = false
                audio.abandonAudioFocusRequest(request)
                onLoss(true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                held = false
                onLoss(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuck(true)
        }
    }

    private val request: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(listener, handler)
            .build()

    val isHeld: Boolean get() = held

    /** 포커스를 얻을 때까지 최대 [waitMs] 기다린다. 못 얻으면 false(이번 소리는 내지 않는다). */
    suspend fun acquire(waitMs: Long): Boolean {
        if (held) return true
        val t0 = System.currentTimeMillis()
        val result = audio.requestAudioFocus(request)
        Log.i(MEASURE_TAG, "focus[$name] request result=${resultName(result)}")
        when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> { held = true; return true }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                val d = pending ?: CompletableDeferred<Boolean>().also { pending = it }
                val ok = withTimeoutOrNull(waitMs) { d.await() } == true
                Log.i(MEASURE_TAG, "focus[$name] delayed gain=$ok waited=${System.currentTimeMillis() - t0}ms")
                if (!ok) release()
                return ok
            }
            else -> return false
        }
    }

    fun release() {
        pending?.complete(false)
        pending = null
        if (held) Log.i(MEASURE_TAG, "focus[$name] abandon")
        held = false
        audio.abandonAudioFocusRequest(request)
    }

    private fun resultName(r: Int) = when (r) {
        AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "GRANTED"
        AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "DELAYED"
        AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "FAILED"
        else -> r.toString()
    }

    private fun changeName(c: Int) = when (c) {
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> "GAIN_TRANSIENT"
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "GAIN_TRANSIENT_MAY_DUCK"
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
        else -> c.toString()
    }

    companion object {
        /**
         * 선배 목소리: USAGE_ASSISTANT(VOICE_COMMAND 컨텍스트, "질문에 대한 음성 응답·안내")+SPEECH.
         * 운영사무국 안내대로 TTS라는 이유로 NAVIGATION(ASSISTANCE_NAVIGATION_GUIDANCE)은 쓰지 않는다.
         */
        fun speechAttributes(): AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        /** 훈련 효과음: GAME(MUSIC 컨텍스트) + SONIFICATION. 정차 중에만 나온다. */
        fun sfxAttributes(): AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
