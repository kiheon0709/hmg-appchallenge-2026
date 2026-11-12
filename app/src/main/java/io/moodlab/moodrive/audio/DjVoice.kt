package io.moodlab.moodrive.audio

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

private const val TAG = "MooDriveMeasure"

/** Pleos TTS. 앱 등록이 필요 없는 온디바이스 모드를 쓴다. [onSpeaking]으로 멘트 시작·끝을 알린다. */
class DjVoice(context: Context, private val onSpeaking: (Boolean) -> Unit) {
    private val tts = TextToSpeech(context, Mode.ON_DEVICE)
    private var ready = false
    private val listener = object : EventListener {
        override fun onReady() {
            Log.i(TAG, "tts ready")
        }
        override fun onStart() {
            Log.i(TAG, "tts start")
            onSpeaking(true)
        }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() {
            Log.i(TAG, "tts done")
            onSpeaking(false)
        }
        override fun onStop() {
            onSpeaking(false)
        }
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "tts error=$errorType")
            onSpeaking(false)
        }
    }

    init {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                Log.i(TAG, "tts initialized")
                ready = true
                tts.setLocale(Locale.KOREA)
            }
            override fun onFailure(errorType: ErrorType) {
                Log.w(TAG, "tts init failed=$errorType")
            }
        })
        tts.addEventListener(listener)
    }

    fun speak(text: String) {
        if (!ready) {
            Log.w(TAG, "tts not ready, dropped: $text")
            return
        }
        tts.speak(text)
    }

    fun release() {
        tts.removeEventListener(listener)
        tts.release()
    }
}
