package io.moodlab.sideseat.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * Pleos TextToSpeech(ON_DEVICE)로 말한다. 앱 등록 없이 동작하는 모드다(기존 DjVoice에서 실측 확인).
 *
 * - 큐: [say]는 순서대로, urgent는 지금 말을 끊고 맨 앞에.
 * - 스트리밍: [sayStreaming]은 문장이 오는 대로 `speak(text, isFinal=false)`로 이어 보내고 마지막만 `isFinal=true`.
 *   마지막 문장인지는 흐름이 끝나야 알 수 있으므로, 첫 문장은 바로 보내고 그 뒤부터는 한 문장씩 늦게 보낸다.
 *   ([chunked]=false면 문장마다 따로 말하고 끝나기를 기다린다. 실측에서 조각 재생이 안 되면 이쪽으로.)
 * - 오디오 포커스: 말하기 전에 GAIN_TRANSIENT_MAY_DUCK. LOSS·LOSS_TRANSIENT면 즉시 멈춘다.
 *
 * 상태는 전부 메인 스레드에서만 바꾼다([scope]는 Dispatchers.Main).
 */
internal class TtsSpeaker(
    context: Context,
    private val scope: CoroutineScope,
    private val meter: LatencyMeter,
    private val chunked: Boolean = false,
) {
    private sealed interface Item {
        val urgent: Boolean
        data class Line(val text: String, override val urgent: Boolean) : Item
        class Stream(val sentences: Flow<String>) : Item { override val urgent = false }
    }

    private sealed interface Event {
        object Ready : Event
        object Start : Event
        object Done : Event
        object Stop : Event
        data class Error(val type: ErrorType) : Event
    }

    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val ready = CompletableDeferred<Boolean>()
    private val queue = ArrayDeque<Item>()
    private var worker: Job? = null
    private var currentItem: Item? = null
    private var initAttempts = 0
    private var startedThisItem = false
    private var lastStopAt = 0L

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    /** TTS onStart·onDone을 밖(효과음 줄이기 등)에 알린다. */
    var onAudible: (Boolean) -> Unit = {}

    private val focus = AudioFocusGate(
        audio = context.getSystemService(AudioManager::class.java),
        attributes = AudioFocusGate.speechAttributes(),
        name = "tts",
        onLoss = { permanent -> onFocusLoss(permanent) },
    )

    private val listener = object : EventListener {
        override fun onReady() { events.trySend(Event.Ready) }
        override fun onStart() { events.trySend(Event.Start) }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() { events.trySend(Event.Done) }
        override fun onStop() { events.trySend(Event.Stop) }
        override fun onError(errorType: ErrorType) { events.trySend(Event.Error(errorType)) }
    }

    init {
        // 순서 주의: initialize()가 SDK 내부 서비스 관리자를 만든다. 그 전에 addEventListener를 부르면
        // UninitializedPropertyAccessException(lateinit tts)로 앱이 죽는다(에뮬레이터 실측). 문서 4.3절 순서와 같다.
        initialize()
        tts.addEventListener(listener)
    }

    private fun initialize() {
        initAttempts++
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                Log.i(MEASURE_TAG, "tts initialized attempt=$initAttempts")
                tts.setLocale(Locale.KOREA)
                runCatching { tts.setAudioAttributes(focus.attributes) }
                    .onFailure { Log.w(MEASURE_TAG, "tts setAudioAttributes failed: $it") }
                ready.complete(true)
            }

            override fun onFailure(errorType: ErrorType) {
                Log.w(MEASURE_TAG, "tts init failed=$errorType attempt=$initAttempts")
                if (initAttempts < MAX_INIT_ATTEMPTS) {
                    scope.launch { delay(2_000); initialize() }
                } else {
                    ready.complete(false)
                }
            }
        })
    }

    // ---------- 큐 ----------

    fun say(text: String, urgent: Boolean) {
        val t = text.trim()
        if (t.isEmpty()) return
        scope.launch {
            val item = Item.Line(t, urgent)
            if (urgent) {
                queue.addFirst(item)
                interruptCurrent()
            } else {
                queue.addLast(item)
            }
            ensureWorker()
        }
    }

    fun sayStreaming(sentences: Flow<String>) {
        scope.launch {
            queue.addLast(Item.Stream(sentences))
            ensureWorker()
        }
    }

    fun stopSpeaking() {
        scope.launch {
            queue.clear()
            interruptCurrent()
        }
    }

    /** 듣기 전에 말이 끝나기를 기다린다. */
    suspend fun awaitIdle(maxMs: Long) {
        withTimeoutOrNull(maxMs) { while (_speaking.value) delay(50) }
    }

    private fun interruptCurrent() {
        val w = worker ?: return
        if (currentItem == null) return
        // 새 일꾼이 말을 시작하기 전에 여기서 바로 멈춘다(끊긴 일꾼의 늦은 stop()이 새 말을 끊지 않게).
        stopEngine()
        worker = null
        w.cancel()
        if (queue.isEmpty()) {
            _speaking.value = false
            focus.release()
        } else {
            ensureWorker()
        }
    }

    private fun ensureWorker() {
        if (worker?.isActive == true) return
        if (queue.isEmpty()) return
        _speaking.value = true
        worker = scope.launch {
            val me = coroutineContext[Job]
            try {
                while (queue.isNotEmpty()) {
                    val item = queue.removeFirst()
                    currentItem = item
                    try {
                        play(item)
                    } finally {
                        currentItem = null
                    }
                }
            } finally {
                // 끊기고 새 일꾼이 이미 돌고 있으면 그쪽이 상태를 맡는다.
                if (worker === me || worker == null) {
                    worker = null
                    if (queue.isEmpty()) {
                        _speaking.value = false
                        onAudible(false)
                        focus.release()
                    } else {
                        ensureWorker()
                    }
                }
            }
        }
    }

    private fun onFocusLoss(permanent: Boolean) {
        val item = currentItem
        if (permanent) queue.clear()
        else if (item is Item.Line) queue.addFirst(item) // 다시 GAIN을 받으면 처음부터 다시 말한다
        val w = worker
        if (item != null) stopEngine()
        worker = null
        w?.cancel()
        if (!permanent && queue.isNotEmpty()) ensureWorker() else _speaking.value = false
    }

    // ---------- 재생 ----------

    private suspend fun play(item: Item) {
        if (withTimeoutOrNull(READY_WAIT_MS) { ready.await() } != true) {
            Log.w(MEASURE_TAG, "tts not ready, dropped item")
            return
        }
        val waitMs = if (item.urgent) URGENT_FOCUS_WAIT_MS else FOCUS_WAIT_MS
        if (!focus.acquire(waitMs)) {
            Log.w(MEASURE_TAG, "tts focus denied, dropped item urgent=${item.urgent}")
            return
        }
        // 앞 발화를 끊은 직후면 늦게 오는 onStop을 흘려보낸다.
        val sinceStop = SystemClock.elapsedRealtime() - lastStopAt
        if (sinceStop < STOP_SETTLE_MS) delay(STOP_SETTLE_MS - sinceStop)
        drainEvents()
        startedThisItem = false
        try {
            when (item) {
                is Item.Line -> playLine(item.text)
                is Item.Stream -> if (chunked) playChunked(item.sentences) else playSequential(item.sentences)
            }
        } catch (e: CancellationException) {
            // 끊어서 취소된 경우는 끊은 쪽이 이미 멈췄다. 스스로 취소된 경우(release 등)만 여기서 멈춘다.
            if (worker === coroutineContext[Job]) stopEngine()
            onAudible(false)
            throw e
        }
    }

    private fun stopEngine() {
        runCatching { tts.stop() }
        lastStopAt = SystemClock.elapsedRealtime()
    }

    private suspend fun playLine(text: String) {
        Log.i(MEASURE_TAG, "tts speak chars=${text.length}")
        tts.speak(text, true)
        awaitFinished(estimateMs(text.length))
    }

    private suspend fun playSequential(sentences: Flow<String>) {
        sentences.collect { s ->
            tts.speak(s, true)
            Log.i(MEASURE_TAG, "tts speak(seq) chars=${s.length}")
            awaitFinished(estimateMs(s.length))
        }
    }

    /** 문장 흐름을 한 발화로: 첫 문장은 바로, 그 뒤는 한 문장 늦게, 마지막만 isFinal=true. */
    private suspend fun playChunked(sentences: Flow<String>) {
        var sentFirst = false
        var held: String? = null
        var totalChars = 0
        val t0 = SystemClock.elapsedRealtime()
        sentences.collect { s ->
            totalChars += s.length
            pumpEvents()
            if (!sentFirst) {
                sentFirst = true
                tts.speak(s, false)
                Log.i(MEASURE_TAG, "tts chunk first chars=${s.length} at=${SystemClock.elapsedRealtime() - t0}ms")
            } else {
                held?.let { tts.speak(it, false) }
                held = s
            }
        }
        if (!sentFirst) return
        val last = held
        if (last != null) {
            tts.speak(last, true)
        } else {
            // 문장이 하나뿐이면 이미 isFinal=false로 보냈다. 발화를 닫는 마침표를 보낸다(실측 대상).
            tts.speak(".", true)
        }
        Log.i(MEASURE_TAG, "tts chunk final totalChars=$totalChars at=${SystemClock.elapsedRealtime() - t0}ms")
        awaitFinished(estimateMs(totalChars), quietMs = QUIET_AFTER_DONE_MS, allowTrailingError = last == null)
    }

    /** 이미 온 이벤트만 처리한다(onStart를 놓치지 않게). */
    private fun pumpEvents() {
        while (true) {
            val e = events.tryReceive().getOrNull() ?: return
            handle(e)
        }
    }

    private fun drainEvents() {
        while (events.tryReceive().isSuccess) Unit
    }

    /**
     * 발화가 끝날 때까지 기다린다. [quietMs]>0이면 onDone 뒤 그만큼 조용할 때 끝으로 본다(조각마다 onDone이 와도 버티게).
     * onStart 전에 온 onStop은 앞 발화를 끊을 때 생긴 늦은 이벤트라 무시한다.
     */
    private suspend fun awaitFinished(estimateMs: Long, quietMs: Long = 0, allowTrailingError: Boolean = false) {
        val finished = withTimeoutOrNull(estimateMs) { waitEvents(quietMs, allowTrailingError); true }
        if (finished == null) Log.w(MEASURE_TAG, "tts watchdog timeout after ${estimateMs}ms")
        onAudible(false)
    }

    private suspend fun waitEvents(quietMs: Long, allowTrailingError: Boolean) {
        var doneSeen = false
        while (true) {
            val e = if (doneSeen) {
                if (quietMs <= 0) return
                withTimeoutOrNull(quietMs) { events.receive() } ?: return
            } else {
                events.receive()
            }
            when (handle(e)) {
                Outcome.STARTED -> doneSeen = false
                Outcome.DONE -> doneSeen = true
                Outcome.STOPPED -> if (startedThisItem) return
                Outcome.ERROR -> if (!(allowTrailingError && doneSeen)) return
                Outcome.NONE -> Unit
            }
        }
    }

    private enum class Outcome { NONE, STARTED, DONE, STOPPED, ERROR }

    private fun handle(e: Event): Outcome = when (e) {
        Event.Ready -> Outcome.NONE
        Event.Start -> {
            startedThisItem = true
            Log.i(MEASURE_TAG, "tts start")
            meter.onTtsStart()
            onAudible(true)
            Outcome.STARTED
        }
        Event.Done -> { Log.i(MEASURE_TAG, "tts done"); Outcome.DONE }
        Event.Stop -> { Log.i(MEASURE_TAG, "tts stop"); Outcome.STOPPED }
        is Event.Error -> { Log.w(MEASURE_TAG, "tts error=${e.type}"); Outcome.ERROR }
    }

    private fun estimateMs(chars: Int): Long = 4_000L + chars * 260L

    fun release() {
        queue.clear()
        worker?.cancel()
        runCatching { tts.stop() }
        focus.release()
        runCatching { tts.removeEventListener(listener) }
        runCatching { tts.release() }
    }

    companion object {
        private const val MAX_INIT_ATTEMPTS = 3
        private const val READY_WAIT_MS = 5_000L
        private const val FOCUS_WAIT_MS = 6_000L
        private const val URGENT_FOCUS_WAIT_MS = 10_000L
        private const val QUIET_AFTER_DONE_MS = 300L
        private const val STOP_SETTLE_MS = 150L
    }
}
