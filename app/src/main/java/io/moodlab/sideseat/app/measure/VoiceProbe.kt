package io.moodlab.sideseat.app.measure

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.tts.constant.ErrorType as TtsError

/** Pleos TTS·STT 실측: 초기화, 재생 시작까지 걸린 시간, 인식 결과, TTS 중 STT 가능 여부. */
class VoiceProbe(private val context: Context) {
    private fun log(msg: String) { Log.i(MEASURE_TAG, "t=${SystemClock.elapsedRealtime()} voice $msg") }

    private val tts = TextToSpeech(context, Mode.ON_DEVICE)
    private var ttsAskedAt = 0L
    private val ttsEvents = object : EventListener {
        override fun onReady() = log("tts onReady")
        override fun onStart() = log("tts onStart after=${SystemClock.elapsedRealtime() - ttsAskedAt}ms")
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = log("tts onDone total=${SystemClock.elapsedRealtime() - ttsAskedAt}ms")
        override fun onStop() = log("tts onStop")
        override fun onError(errorType: TtsError) = log("tts onError=$errorType")
    }

    private val stt = SpeechToText(context, SttMode.ON_DEVICE)
    private var sttAskedAt = 0L
    private val sttEvents = SttListener(::log) { sttAskedAt }

    init {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.setLocale(Locale.KOREA)
                log("tts init ok")
            }
            override fun onFailure(errorType: TtsError) = log("tts init fail=$errorType")
        })
        tts.addEventListener(ttsEvents)
        runCatching {
            stt.initialize(sttInitListener(::log))
            stt.addListener(sttEvents)
            stt.setLocale(Locale.KOREA)
        }.onFailure { log("stt init threw $it") }
    }

    fun command(cmd: String, arg: String) {
        when (cmd) {
            "tts" -> say(arg.ifEmpty { "비가 쏟아져요. 앞이 안 보여요!" })
            "stt" -> listen()
            "stt_stop" -> stt.stop()
            "tts_stt" -> {
                say(arg.ifEmpty { "이건 조금 긴 문장이에요. 제가 말하는 중에도 마이크가 열리는지 확인하고 있어요. 계속 말하는 중이에요." })
                listen()
            }
            "stt_pcm" -> sendPcm(arg)
            else -> log("unknown cmd $cmd")
        }
    }

    private fun say(text: String) {
        ttsAskedAt = SystemClock.elapsedRealtime()
        log("tts speak '$text'")
        tts.speak(text)
    }

    private fun listen() {
        sttAskedAt = SystemClock.elapsedRealtime()
        log("stt request")
        runCatching { stt.request() }.onFailure { log("stt request threw $it") }
    }

    /** 16 kHz 16-bit mono PCM 파일을 100 ms씩 실시간 속도로 보낸다(문서 예시와 같은 방식). */
    private fun sendPcm(path: String) = thread(name = "stt-pcm") {
        val bytes = runCatching { File(path).readBytes() }.getOrElse {
            log("pcm read fail $it"); return@thread
        }
        sttAskedAt = SystemClock.elapsedRealtime()
        log("stt sendAudio start bytes=${bytes.size}")
        bytes.toList().chunked(3_200).forEach {
            stt.sendAudio(it.toByteArray())
            Thread.sleep(100)
        }
        stt.completeAudioSend()
        log("stt sendAudio complete after=${SystemClock.elapsedRealtime() - sttAskedAt}ms")
    }

    fun release() {
        runCatching { tts.removeEventListener(ttsEvents); tts.release() }
        runCatching { stt.removeListener(sttEvents); stt.release() }
    }
}

private class SttListener(private val log: (String) -> Unit, private val askedAt: () -> Long) :
    ai.pleos.playground.stt.listener.ResultListener {
    private fun since() = SystemClock.elapsedRealtime() - askedAt()
    override fun onReady() = log("stt onReady after=${since()}ms")
    override fun onStartedRecognition() = log("stt onStarted after=${since()}ms")
    override fun onUpdated(stt: String, completed: Boolean) = log("stt onUpdated completed=$completed after=${since()}ms text='$stt'")
    override fun onUpdatedEpdData(on: Long, off: Long) = log("stt epd on=$on off=$off")
    private var maxRms = 0.0
    private var rmsCount = 0
    override fun onUpdatedRms(rms: Double) {
        rmsCount++
        if (rms > maxRms) maxRms = rms
        if (rmsCount % 20 == 0) log("stt rms count=$rmsCount max=$maxRms last=$rms")
    }
    override fun onEndedRecognition() = log("stt onEnded after=${since()}ms rmsCount=$rmsCount maxRms=$maxRms")
    override fun onError(errorType: ai.pleos.playground.stt.constant.ErrorType) = log("stt onError=$errorType after=${since()}ms")
}

private fun sttInitListener(log: (String) -> Unit) = object : ai.pleos.playground.stt.listener.OnInitializeListener {
    override fun onSuccess() = log("stt init ok")
    override fun onFailure(errorType: ai.pleos.playground.stt.constant.ErrorType) = log("stt init fail=$errorType")
}
