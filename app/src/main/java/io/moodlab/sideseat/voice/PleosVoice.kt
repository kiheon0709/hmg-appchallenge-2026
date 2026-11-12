package io.moodlab.sideseat.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.moodlab.sideseat.core.ReplyKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * [Voice] 구현. 말하기는 [TtsSpeaker](Pleos TTS), 듣기는 Pleos SpeechToText ON_DEVICE.
 *
 * 차량 마이크 음성은 Pleos Speech Service 안에서 텍스트가 되고, 앱은 텍스트만 받는다. 음성 데이터를 밖으로 보내지 않는다.
 * 안드로이드 기본 SpeechRecognizer는 이 이미지에 엔진이 없어 쓰지 않는다.
 *
 * 듣기 흐름: 리스너는 초기화 때 미리 등록(request 전에) → request() → 중간 결과(onUpdated completed=false)는 onPartial로
 * → EPD가 말 끝을 알리면 Instructor에게 추측 요청 신호 → 최종 결과(completed=true) 또는 시간 초과 시 마지막 중간 결과.
 */
internal class PleosVoice(
    context: Context,
    private val scope: CoroutineScope,
    private val speaker: TtsSpeaker,
    private val meter: LatencyMeter,
    private val hooks: ListenHooks,
    /** TTS가 말하는 중에도 마이크를 열지. 실측 전에는 false(말이 끝난 뒤 연다). */
    private val bargeIn: Boolean = false,
) : Voice {

    private sealed interface SttEvent {
        object Ready : SttEvent
        object Started : SttEvent
        object Ended : SttEvent
        data class Updated(val text: String, val completed: Boolean) : SttEvent
        data class Epd(val on: Long, val off: Long) : SttEvent
        data class Rms(val value: Double) : SttEvent
        data class Error(val type: ErrorType) : SttEvent
    }

    private val stt = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
    private val events = Channel<SttEvent>(Channel.UNLIMITED)
    private val sttReady = CompletableDeferred<Boolean>()
    private val listenLock = Mutex()
    private var initAttempts = 0

    private val _listening = MutableStateFlow(false)
    override val listening: StateFlow<Boolean> = _listening.asStateFlow()
    override val speaking: StateFlow<Boolean> get() = speaker.speaking

    private val resultListener = object : ResultListener {
        override fun onReady() { events.trySend(SttEvent.Ready) }
        override fun onStartedRecognition() { events.trySend(SttEvent.Started) }
        override fun onUpdated(stt: String, completed: Boolean) { events.trySend(SttEvent.Updated(stt, completed)) }
        override fun onUpdatedEpdData(on: Long, off: Long) { events.trySend(SttEvent.Epd(on, off)) }
        override fun onUpdatedRms(rms: Double) { events.trySend(SttEvent.Rms(rms)) }
        override fun onEndedRecognition() { events.trySend(SttEvent.Ended) }
        override fun onError(errorType: ErrorType) { events.trySend(SttEvent.Error(errorType)) }
    }

    init {
        // 순서 주의: initialize() 다음에 addListener(TTS와 같은 이유, 실측에서 이 순서로 성공).
        // 리스너는 request()·sendAudio()보다 먼저 등록되므로 이벤트를 놓치지 않는다.
        initialize()
        stt.addListener(resultListener)
    }

    private fun initialize() {
        initAttempts++
        stt.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                Log.i(MEASURE_TAG, "stt initialized attempt=$initAttempts")
                stt.setLocale(Locale.KOREA)
                sttReady.complete(true)
            }

            override fun onFailure(errorType: ErrorType) {
                Log.w(MEASURE_TAG, "stt init failed=$errorType attempt=$initAttempts")
                if (initAttempts < 3) scope.launch { delay(2_000); initialize() } else sttReady.complete(false)
            }
        })
    }

    // ---------- 말하기 ----------

    override fun say(text: String, urgent: Boolean) = speaker.say(text, urgent)
    override fun sayStreaming(sentences: Flow<String>) = speaker.sayStreaming(sentences)
    override fun stopSpeaking() = speaker.stopSpeaking()

    // ---------- 듣기 ----------

    override suspend fun listen(kind: ReplyKind, onPartial: (String) -> Unit): String? = withContext(Dispatchers.Main) {
        listenLock.withLock { listenOnce(kind, onPartial, pcm = null) }
    }

    /**
     * 실측·시연용: 차량 마이크 대신 16kHz 16bit mono PCM을 실시간 속도로 넣어 같은 경로(중간 결과·EPD·추측 요청·지연 측정)로 듣는다.
     * SpeechToText.sendAudio는 ON_DEVICE 모드에서만 된다(문서). 에뮬레이터는 호스트 마이크 소리가 인식되지 않아 이 길로 잰다.
     */
    suspend fun listenFromPcm(pcm: ByteArray, kind: ReplyKind, onPartial: (String) -> Unit = {}): String? = withContext(Dispatchers.Main) {
        listenLock.withLock { listenOnce(kind, onPartial, pcm) }
    }

    private suspend fun listenOnce(kind: ReplyKind, onPartial: (String) -> Unit, pcm: ByteArray?): String? {
        if (withTimeoutOrNull(5_000) { sttReady.await() } != true) {
            Log.w(MEASURE_TAG, "stt not ready")
            return null
        }
        if (!bargeIn) speaker.awaitIdle(20_000)
        val limits = Limits.of(kind)
        hooks.onListenStart()
        while (events.tryReceive().isSuccess) Unit // 지난 듣기의 늦은 이벤트를 버린다
        _listening.value = true
        val t0 = SystemClock.elapsedRealtime()
        var lastPartial = ""
        var final: String? = null
        var speechSeen = false
        var epdAt: Long? = null
        var ended = false
        var endedAt: Long? = null
        var startedLogged = 0
        var rmsCount = 0
        var rmsMax = 0.0
        var rmsMin = -1.0
        var quietSince: Long? = null
        var prevPartialNorm = ""
        var lastPartialAt: Long? = null
        var guessedNorm = ""
        var guesses = 0
        /** 말이 끝났다고 "추측"되면 Instructor가 LLM 요청을 먼저 보낸다. 듣기는 계속한다. 같은 글에는 한 번만. */
        fun guessEnd(source: String) {
            val norm = ReplyClassifier.normalize(lastPartial)
            if (norm.isEmpty() || norm == guessedNorm || guesses >= MAX_GUESSES) return
            guessedNorm = norm
            guesses++
            Log.i(MEASURE_TAG, "stt guess end ($source) #$guesses chars=${lastPartial.length} at=${SystemClock.elapsedRealtime() - t0}ms")
            hooks.onEpdEnd(lastPartial)
        }
        /** 엔진 EPD·RMS로 말 끝이 확인됨: 최종 결과 대기 시간 제한을 건다. */
        fun markSpeechEnd(source: String) {
            if (epdAt != null) return
            epdAt = SystemClock.elapsedRealtime()
            Log.i(MEASURE_TAG, "stt speech end ($source) at=${epdAt!! - t0}ms")
            guessEnd(source)
        }
        var firstPartialAt: Long? = null
        var sender: Job? = null
        try {
            if (pcm == null) {
                stt.request()
            } else {
                sender = scope.launch(Dispatchers.IO) {
                    var off = 0
                    while (off < pcm.size) {
                        val end = minOf(off + PCM_BYTES_PER_100_MS, pcm.size)
                        stt.sendAudio(pcm.copyOfRange(off, end))
                        off = end
                        delay(100)
                    }
                    stt.completeAudioSend()
                    Log.i(MEASURE_TAG, "stt pcm sent bytes=${pcm.size} at=${SystemClock.elapsedRealtime() - t0}ms")
                }
            }
            Log.i(MEASURE_TAG, "stt request kind=$kind source=${if (pcm == null) "mic" else "pcm"} speaking=${speaker.speaking.value}")
            loop@ while (true) {
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - t0
                val deadline = listOfNotNull(
                    if (!speechSeen && endedAt == null) limits.noSpeechMs - elapsed else null,
                    limits.maxMs - elapsed,
                    epdAt?.let { limits.finalAfterEpdMs - (now - it) },
                    // 실측: onEndedRecognition이 먼저 오고 최종 결과(completed=true)가 바로 뒤에 온다. 조금 더 기다린다.
                    endedAt?.let { FINAL_AFTER_END_MS - (now - it) },
                ).min()
                if (deadline <= 0) {
                    Log.i(MEASURE_TAG, "stt ${if (endedAt != null) "no final after end" else "timeout"} speechSeen=$speechSeen epd=${epdAt != null} elapsed=${elapsed}ms")
                    break@loop
                }
                // 중간 결과가 잠시 새로 오지 않으면 말이 끝났다고 추측한다(듣기는 계속).
                // 끝난 문장처럼 보이면 짧게(QUIET_GUESS_COMPLETE_MS), 아니면 길게 기다린다(말하는 중 중간 결과는 약 400ms마다 온다).
                val quietMs = if (ReplyClassifier.looksComplete(lastPartial)) QUIET_GUESS_COMPLETE_MS else QUIET_GUESS_MS
                val guessIn = lastPartialAt?.takeIf { endedAt == null && ReplyClassifier.normalize(lastPartial) != guessedNorm }
                    ?.let { quietMs - (now - it) }
                if (guessIn != null && guessIn <= 0) {
                    guessEnd("quiet")
                    continue@loop
                }
                val e = withTimeoutOrNull(if (guessIn != null) minOf(deadline, guessIn) else deadline) { events.receive() } ?: continue@loop
                when (e) {
                    SttEvent.Ready -> Unit
                    SttEvent.Started -> if (startedLogged++ == 0) Log.i(MEASURE_TAG, "stt started at=${SystemClock.elapsedRealtime() - t0}ms")
                    is SttEvent.Updated -> {
                        val text = e.text.trim()
                        if (e.completed) {
                            if (text.isNotEmpty()) final = text
                            Log.i(MEASURE_TAG, "stt final chars=${text.length} at=${SystemClock.elapsedRealtime() - t0}ms " +
                                "end->final=${endedAt?.let { SystemClock.elapsedRealtime() - it } ?: "-"}ms silence->final=${epdAt?.let { SystemClock.elapsedRealtime() - it } ?: "-"}ms")
                            break@loop
                        }
                        if (text.isNotEmpty()) {
                            if (firstPartialAt == null) {
                                firstPartialAt = SystemClock.elapsedRealtime()
                                Log.i(MEASURE_TAG, "stt first partial at=${firstPartialAt!! - t0}ms")
                            }
                            speechSeen = true
                            lastPartial = text
                            lastPartialAt = SystemClock.elapsedRealtime()
                            onPartial(text)
                            hooks.onPartial(text)
                            // 같은 중간 결과가 한 번 더 오면 말이 멈춘 것으로 보고 LLM 요청을 먼저 보낸다.
                            val norm = ReplyClassifier.normalize(text)
                            if (norm == prevPartialNorm) guessEnd("stable")
                            prevPartialNorm = norm
                        }
                    }
                    is SttEvent.Epd -> {
                        Log.i(MEASURE_TAG, "stt epd on=${e.on} off=${e.off} at=${SystemClock.elapsedRealtime() - t0}ms")
                        if (e.on > 0) speechSeen = true
                        if (e.off > 0) markSpeechEnd("epd")
                    }
                    is SttEvent.Rms -> {
                        rmsCount++
                        if (e.value > rmsMax) rmsMax = e.value
                        if (rmsMin < 0 || e.value < rmsMin) rmsMin = e.value
                        // EPD 이벤트가 오지 않는 환경(실측) 대비: 말소리가 있다가 크게 줄어든 채로 잠시 이어지면 말 끝으로 본다.
                        val quiet = rmsMax > 0 && e.value < rmsMax * SILENCE_RATIO
                        val t = SystemClock.elapsedRealtime()
                        if (!quiet || !speechSeen) quietSince = null
                        else if (quietSince == null) quietSince = t
                        else if (epdAt == null && t - quietSince!! >= SILENCE_HOLD_MS) markSpeechEnd("rms")
                    }
                    SttEvent.Ended -> {
                        if (endedAt == null) {
                            endedAt = SystemClock.elapsedRealtime()
                            ended = true
                            Log.i(MEASURE_TAG, "stt ended at=${endedAt!! - t0}ms")
                        }
                    }
                    is SttEvent.Error -> {
                        Log.w(MEASURE_TAG, "stt error=${e.type}")
                        break@loop
                    }
                }
            }
            if (rmsCount > 0) Log.i(MEASURE_TAG, "stt rms n=$rmsCount max=${"%.4f".format(rmsMax)} min=${"%.4f".format(rmsMin)}")
        } finally {
            sender?.cancel()
            if (!ended) runCatching { stt.stop() }.onFailure { Log.w(MEASURE_TAG, "stt stop failed: $it") }
            _listening.value = false
        }
        val result = (final ?: lastPartial).trim().ifEmpty { null }
        if (result != null) hooks.onFinal(result) else meter.cancel()
        Log.i(MEASURE_TAG, "stt result=${if (result == null) "none" else "chars=${result.length}"} source=${if (final != null) "final" else "partial"}")
        return result
    }

    private companion object {
        /** 16kHz·16bit·mono 100ms = 3,200바이트(SpeechToText sending-audio-data 문서). */
        const val PCM_BYTES_PER_100_MS = 3_200
        /** 인식 종료 뒤 최종 결과를 기다리는 시간. 실측에서는 종료와 거의 동시에 온다. */
        const val FINAL_AFTER_END_MS = 600L
        /** 말하는 동안의 최대 RMS 대비 이 비율 아래로 [SILENCE_HOLD_MS] 이어지면 말 끝으로 본다(추측 요청 신호). */
        const val SILENCE_RATIO = 0.18
        const val SILENCE_HOLD_MS = 350L
        /** 중간 결과가 이만큼 새로 오지 않으면 말 끝으로 추측(실측: 말하는 동안 중간 결과는 약 400ms마다). */
        const val QUIET_GUESS_MS = 450L
        /**
         * 중간 결과가 끝난 문장처럼 보일 때의 기준. 실측(11-14 PCM): 마지막 중간 결과 뒤 약 0.41초에 인식이 끝나
         * 450ms로는 한 번도 추측하지 못했다. 220ms면 최종 결과보다 약 0.19초 먼저 요청이 나간다.
         */
        const val QUIET_GUESS_COMPLETE_MS = 220L
        /** 한 번 듣는 동안 추측 요청 최대 횟수(말을 이어 가면 글이 바뀌어 다시 보낸다). */
        const val MAX_GUESSES = 3
    }

    private data class Limits(val noSpeechMs: Long, val maxMs: Long, val finalAfterEpdMs: Long) {
        companion object {
            fun of(kind: ReplyKind) = when (kind) {
                ReplyKind.YES_NO -> Limits(noSpeechMs = 5_000, maxMs = 7_000, finalAfterEpdMs = 900)
                ReplyKind.CHOICE -> Limits(noSpeechMs = 6_000, maxMs = 9_000, finalAfterEpdMs = 1_000)
                ReplyKind.FREE -> Limits(noSpeechMs = 7_000, maxMs = 14_000, finalAfterEpdMs = 1_200)
            }
        }
    }

    fun release() {
        runCatching { stt.stop() }
        runCatching { stt.removeListener(resultListener) }
        runCatching { stt.release() }
    }
}
