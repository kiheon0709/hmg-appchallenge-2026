package io.moodlab.sideseat.voice

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.moodlab.sideseat.core.ReplyKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * [WakeListening] 구현. 판단은 [WakeLoop](순수 로직), 여기서는 STT 한 번씩 열고 닫기와 물러날 신호 받기만 한다.
 *
 * 주행 중 CPU를 아끼려고: 인식은 Pleos Speech Service가 하고, 앱은 중간 결과의 앞 40자만 본다.
 * STT 한 번마다 로그를 남기지 않고(호출·물러남·오류만), 말이 없으면 [WakeConfig.cycleMaxMs] 뒤 닫고 다시 연다.
 *
 * 물러날 신호(Gleo 우선):
 * - Gleo 호출어가 중간 결과에 보이면 그 자리에서 STT를 닫고 [WakeConfig.gleoBackoffMs] 쉰다.
 * - 타비 TTS가 오디오 포커스를 잃음(Gleo 호출·통화).
 * - 우리 STT가 쉬는 사이에 다른 앱이 녹음 중(AudioManager 녹음 콜백, Gleo 마이크로 추정).
 * - 통화 중(AudioManager 모드).
 * - STT 오류(마이크를 다른 쪽이 쓰는 중일 수 있음): 이어지면 대기 두 배.
 */
internal class WakeListener(
    context: Context,
    private val scope: CoroutineScope,
    private val voice: PleosVoice,
    private val speaker: TtsSpeaker,
    private val sfx: Sfx,
    config: WakeConfig,
) : WakeListening {

    private val loop = WakeLoop(config)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val pendingPcm = ConcurrentLinkedQueue<ByteArray>()
    private var job: Job? = null
    private var onQuestion: (suspend (String) -> Unit)? = null
    @Volatile private var inOurCycle = false
    /** 우리 STT(연속 청취·일반 듣기)가 마지막으로 마이크를 쓴 시각. Pleos 서비스가 녹음을 늦게 끝낼 수 있어 잠깐 봐준다. */
    @Volatile private var lastOurMicAt = 0L

    private val _state = MutableStateFlow(WakeState.OFF)
    override val state: StateFlow<WakeState> = _state.asStateFlow()

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            // 우리 STT는 Pleos 서비스가 녹음하므로 "다른 앱"과 구별할 수 없다. 우리가 쉬는 사이의 녹음만 다른 쪽으로 본다.
            val ours = inOurCycle || voice.listening.value || now() - lastOurMicAt < OUR_MIC_GRACE_MS
            if (!ours && configs.isNotEmpty() && loop.enabled) {
                interfere("other recorder n=${configs.size}")
            }
        }
    }

    init {
        speaker.onFocusLost = { permanent -> interfere("tts focus lost permanent=$permanent") }
    }

    override fun start(onQuestion: suspend (String) -> Unit) {
        this.onQuestion = onQuestion
        if (job?.isActive == true) return
        loop.start()
        audio.registerAudioRecordingCallback(recordingCallback, handler)
        Log.i(MEASURE_TAG, "wake start words=${Persona.WAKE_WORDS.first()}")
        job = scope.launch { run() }
        publish()
    }

    override fun stop() {
        loop.stop()
        job?.cancel()
        job = null
        runCatching { audio.unregisterAudioRecordingCallback(recordingCallback) }
        Log.i(MEASURE_TAG, "wake stop ${loop.status(now())}")
        publish()
    }

    /** 연속 청취를 잠시 멈추고 [block]을 돌린 뒤, 켜져 있었으면 다시 켠다(디버그 시험용). */
    suspend fun <T> paused(block: suspend () -> T): T {
        val handler = onQuestion
        val wasOn = loop.enabled
        if (wasOn) stop()
        try {
            return block()
        } finally {
            if (wasOn && handler != null) start(handler)
        }
    }

    override fun debugStatus(): String = "${loop.status(now())} pendingPcm=${pendingPcm.size}"

    override fun debugFeedPcm(pcm: ByteArray) {
        pendingPcm += pcm
        Log.i(MEASURE_TAG, "wake pcm queued bytes=${pcm.size}")
    }

    private fun interfere(reason: String) {
        scope.launch {
            if (!loop.enabled) return@launch
            loop.onInterference(now())
            Log.i(MEASURE_TAG, "wake back off ($reason)")
            publish()
        }
    }

    private suspend fun run() {
        while (scope.isActive && loop.enabled) {
            publish()
            // 타비가 말하는 동안은 듣지 않는다. 끝나면 되묻기 창.
            if (speaker.speaking.value) {
                loop.onSpeaking(true, now()); publish()
                speaker.speaking.first { !it }
                loop.onSpeaking(false, now())
                continue
            }
            val wait = loop.waitBeforeListen(now())
            if (wait < 0) { delay(IDLE_POLL_MS); continue }
            if (wait > 0) { publish(); delay(wait.coerceAtMost(IDLE_POLL_MS)); continue }
            if (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION) {
                loop.onInterference(now())
                Log.i(MEASURE_TAG, "wake back off (call mode=${audio.mode})")
                continue
            }
            val followUp = loop.inFollowUp(now())
            val maxMs = if (followUp) loop.followUpLeft(now()) else loop.cycleMaxMs
            loop.onCycleStart()
            publish()
            val pcm = pendingPcm.poll()
            inOurCycle = true
            val text = try {
                voice.wakeCycle(
                    pcm, maxMs,
                    onPartial = {},
                    // Gleo 호출이 보이면 마이크를 바로 놓는다.
                    stopWhen = { partial -> WakeWordDetector.detect(partial) == WakeMatcher.Result.Gleo },
                )
            } finally {
                inOurCycle = false
                lastOurMicAt = now()
            }
            val error = voice.lastCycleError
            if (error != null) {
                loop.onError(now())
                Log.w(MEASURE_TAG, "wake stt error=$error ${loop.status(now())}")
                continue
            }
            when (val d = loop.onUtterance(text, now())) {
                WakeLoop.Decision.Gleo -> {
                    Log.i(MEASURE_TAG, "wake gleo heard, back off")
                    speaker.stopSpeaking()
                }
                is WakeLoop.Decision.Question -> handle(d)
                WakeLoop.Decision.Ignore -> delay(loop.restartGapMs)
            }
        }
        publish()
    }

    private suspend fun handle(d: WakeLoop.Decision.Question) {
        publish()
        Log.i(MEASURE_TAG, "wake ${if (d.viaWake) "word" else "follow-up"} questionChars=${d.question.length}")
        if (d.viaWake) sfx.play(WAKE_CUE)
        var q = d.question
        if (q.isBlank()) {
            // "타비"만 부른 경우: 확인음 뒤 질문을 한 번 더 듣는다(예열·추측 요청이 있는 일반 듣기).
            delay(CUE_GAP_MS)
            q = voice.listen(ReplyKind.FREE).orEmpty()
            lastOurMicAt = now()
        }
        val h = onQuestion
        if (q.isBlank() || h == null) {
            loop.onHandled(now())
            return
        }
        h(q)
        // 답이 소리 나기 시작하기를 잠깐 기다렸다가, 끝날 때까지 기다린다. 끝나면 되묻기 창이 열린다.
        if (withTimeoutOrNull(ANSWER_START_WAIT_MS) { speaker.speaking.first { it } } == null) {
            loop.onHandled(now())
            return
        }
        loop.onSpeaking(true, now()); publish()
        speaker.speaking.first { !it }
        loop.onSpeaking(false, now())
    }

    private fun publish() { _state.value = loop.state }

    private fun now() = SystemClock.elapsedRealtime()

    private companion object {
        const val WAKE_CUE = "wake"
        const val IDLE_POLL_MS = 250L
        const val CUE_GAP_MS = 250L
        const val ANSWER_START_WAIT_MS = 4_000L
        const val OUR_MIC_GRACE_MS = 1_500L
    }
}
