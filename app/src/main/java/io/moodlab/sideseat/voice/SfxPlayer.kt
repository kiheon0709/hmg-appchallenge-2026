package io.moodlab.sideseat.voice

import android.content.Context
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 훈련 효과음. `assets/sfx/<이름>.wav`(tools/sfx/generate.py가 코드로 합성)를 SoundPool로 미리 올려 지연 없이 낸다.
 *
 * - 반복음(rain·wind·engine)은 서서히 커지고 서서히 꺼진다.
 * - 선배가 말하는 동안([setDucked]) 효과음을 줄인다. 말이 끝나면 되돌린다.
 * - 오디오 포커스: 소리가 나는 동안 GAIN_TRANSIENT_MAY_DUCK. LOSS면 전부 멈추고, LOSS_TRANSIENT면 잠시 멈췄다 이어 간다.
 *
 * 메인 스레드에서만 상태를 바꾼다([scope]는 Dispatchers.Main).
 */
internal class SfxPlayer(context: Context, private val scope: CoroutineScope) : Sfx {

    private class Clip(val soundId: Int, val durationMs: Long)
    private class Playing(val name: String, val streamId: Int, val loop: Boolean, val endsAt: Long) {
        var level = 0f
        var fade: Job? = null
    }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(AudioFocusGate.sfxAttributes())
        .build()
    private val clips = mutableMapOf<String, Clip>()
    private val loaded = mutableSetOf<Int>()
    private val playing = mutableListOf<Playing>()
    private val waitingForLoad = mutableListOf<Pair<String, Boolean>>()
    private var duck = 1f
    private var releaseJob: Job? = null

    private val focus = AudioFocusGate(
        audio = context.getSystemService(AudioManager::class.java),
        attributes = AudioFocusGate.sfxAttributes(),
        name = "sfx",
        onLoss = { permanent -> if (permanent) stopAll() else pool.autoPause() },
        onDuck = { ducked -> if (!ducked) pool.autoResume(); applyDuck(if (ducked) SYSTEM_DUCK else 1f) },
    )

    init {
        pool.setOnLoadCompleteListener { _, soundId, status ->
            scope.launch {
                if (status != 0) {
                    Log.w(MEASURE_TAG, "sfx load failed id=$soundId status=$status")
                    return@launch
                }
                loaded += soundId
                val ready = waitingForLoad.filter { (n, _) -> clips[n]?.soundId == soundId }
                waitingForLoad.removeAll(ready)
                ready.forEach { (n, l) -> play(n, l) }
            }
        }
        val names = runCatching { context.assets.list(DIR)?.toList().orEmpty() }.getOrDefault(emptyList())
        for (file in names.filter { it.endsWith(".wav") }) {
            runCatching {
                context.assets.openFd("$DIR/$file").use { afd ->
                    val id = pool.load(afd, 1)
                    val ms = ((afd.length - WAV_HEADER_BYTES).coerceAtLeast(0) * 1000L) / BYTES_PER_SECOND
                    clips[file.removeSuffix(".wav")] = Clip(id, ms)
                }
            }.onFailure { Log.w(MEASURE_TAG, "sfx open failed $file: $it") }
        }
        Log.i(MEASURE_TAG, "sfx loading ${clips.keys.sorted()}")
    }

    override fun play(name: String, loop: Boolean) {
        scope.launch {
            val clip = clips[name]
            if (clip == null) {
                Log.w(MEASURE_TAG, "sfx unknown name=$name")
                return@launch
            }
            if (clip.soundId !in loaded) {
                waitingForLoad += name to loop
                return@launch
            }
            val isLoop = loop || name in LOOPS
            if (isLoop && playing.any { it.name == name && it.loop }) return@launch
            releaseJob?.cancel()
            if (!focus.acquire(FOCUS_WAIT_MS)) {
                Log.w(MEASURE_TAG, "sfx focus denied name=$name")
                return@launch
            }
            val gain = GAIN[name] ?: 0.8f
            val startLevel = if (isLoop) 0f else gain
            val streamId = pool.play(clip.soundId, startLevel * duck, startLevel * duck, if (isLoop) 1 else 2, if (isLoop) -1 else 0, 1f)
            if (streamId == 0) {
                Log.w(MEASURE_TAG, "sfx play failed name=$name")
                return@launch
            }
            val p = Playing(name, streamId, isLoop, if (isLoop) Long.MAX_VALUE else SystemClock.elapsedRealtime() + clip.durationMs)
            p.level = startLevel
            playing += p
            if (isLoop) fadeTo(p, gain, LOOP_FADE_IN_MS)
            Log.i(MEASURE_TAG, "sfx play name=$name loop=$isLoop")
            scheduleRelease()
        }
    }

    override fun stop(name: String) {
        scope.launch {
            playing.filter { it.name == name }.forEach { p ->
                if (p.loop) fadeTo(p, 0f, LOOP_FADE_OUT_MS, thenStop = true) else stopNow(p)
            }
        }
    }

    override fun stopAll() {
        scope.launch {
            playing.toList().forEach { p -> fadeTo(p, 0f, STOP_ALL_FADE_MS, thenStop = true) }
            waitingForLoad.clear()
        }
    }

    /** 선배가 말하는 동안 효과음을 줄인다. TTS onStart·onDone에 맞춰 부른다. */
    fun setDucked(ducked: Boolean) {
        scope.launch { applyDuck(if (ducked) SPEECH_DUCK else 1f) }
    }

    private fun applyDuck(target: Float) {
        if (duck == target) return
        val from = duck
        duck = target
        Log.i(MEASURE_TAG, "sfx duck=$target")
        playing.forEach { p ->
            if (p.fade?.isActive == true) return@forEach // 페이드가 다음 단계에서 duck을 반영한다
            p.fade = scope.launch {
                val steps = DUCK_RAMP_MS / STEP_MS
                for (i in 1..steps) {
                    val d = from + (target - from) * i / steps
                    pool.setVolume(p.streamId, p.level * d, p.level * d)
                    delay(STEP_MS)
                }
            }
        }
    }

    private fun fadeTo(p: Playing, target: Float, ms: Long, thenStop: Boolean = false) {
        p.fade?.cancel()
        val from = p.level
        p.fade = scope.launch {
            val steps = (ms / STEP_MS).coerceAtLeast(1)
            for (i in 1..steps) {
                p.level = from + (target - from) * i / steps
                pool.setVolume(p.streamId, p.level * duck, p.level * duck)
                delay(STEP_MS)
            }
            if (thenStop) stopNow(p)
        }
    }

    private fun stopNow(p: Playing) {
        pool.stop(p.streamId)
        playing.remove(p)
        scheduleRelease()
    }

    /** 소리가 다 끝나면 포커스를 돌려준다. */
    private fun scheduleRelease() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            while (true) {
                val now = SystemClock.elapsedRealtime()
                playing.removeAll { !it.loop && it.endsAt <= now }
                if (playing.isEmpty()) break
                val next = playing.filter { !it.loop }.minOfOrNull { it.endsAt } ?: return@launch // 반복음이 남으면 계속 보유
                delay((next - now).coerceAtLeast(50))
            }
            delay(RELEASE_GRACE_MS)
            if (playing.isEmpty()) focus.release()
        }
    }

    fun release() {
        playing.toList().forEach { pool.stop(it.streamId) }
        playing.clear()
        focus.release()
        pool.release()
    }

    companion object {
        private const val DIR = "sfx"
        private const val MAX_STREAMS = 8
        private const val WAV_HEADER_BYTES = 44L
        private const val BYTES_PER_SECOND = 44_100L * 2 // 44.1kHz 16bit mono
        private const val FOCUS_WAIT_MS = 1_500L
        private const val LOOP_FADE_IN_MS = 600L
        private const val LOOP_FADE_OUT_MS = 900L
        private const val STOP_ALL_FADE_MS = 300L
        private const val DUCK_RAMP_MS = 180L
        private const val STEP_MS = 30L
        private const val RELEASE_GRACE_MS = 800L
        /** 선배 말이 잘 들리도록 효과음을 이만큼으로 줄인다(약 -10dB). */
        private const val SPEECH_DUCK = 0.32f
        private const val SYSTEM_DUCK = 0.4f

        /** 늘 반복으로 트는 소리. play(loop=false)로 불러도 반복한다. */
        val LOOPS = setOf("rain", "wind", "engine", "hazard", "breath")

        /**
         * 소리별 기본 크기. 파일 자체는 합성 단계에서 크기를 맞춰 두었고, 여기서는 장면 속 역할로 균형을 잡는다.
         * 배경(빗소리·바람)은 낮게, 위기 신호(경고음·타이어)는 또렷하게, 피드백(딸깍·성공)은 그 사이.
         * 수치(앱 음량 LUFS)는 tools/sfx/analyze.py → tools/sfx/REPORT.md.
         */
        val GAIN = mapOf(
            "rain" to 0.55f,
            "wind" to 0.6f,
            "engine" to 0.6f,
            "engine_rise" to 0.75f,
            "thunder" to 0.9f,
            "tire" to 0.85f,
            "alarm" to 0.7f,
            "chime" to 0.6f,
            "click" to 0.7f,
            "tick" to 0.5f,
            "wiper" to 0.55f,
            "hazard" to 0.9f,
            "breath" to 0.6f,
            "yawn" to 0.6f,
            "unlock" to 1.0f,
            "wake" to 0.6f,
            "buzzer" to 0.6f,
            "success" to 0.7f,
            "fail" to 0.6f,
        )
    }
}
