package io.moodlab.sideseat.voice

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 음성·효과음·AI 교관의 진입점. 통합 세션은 이것만 부른다.
 *
 * ```
 * val (voice, sfx, instructor) = VoiceModule.create(context, VoiceConfig(
 *     apiKey = BuildConfig.LLM_API_KEY, fastModel = BuildConfig.LLM_MODEL_FAST, reviewModel = BuildConfig.LLM_MODEL_REVIEW,
 *     provider = BuildConfig.LLM_PROVIDER))
 * // 질문 턴: instructor.prepareQuestion(parked, ctx); val q = voice.listen(ReplyKind.FREE) { partial -> ... }
 * //          q?.let { voice.sayStreaming(instructor.answer(it, parked, ctx)) }
 * // 리뷰:   voice.sayStreaming(instructor.review(summary))
 * // 앱 종료: VoiceModule.release()
 * ```
 * 모델 이름이 비어 있으면 기본값([VoiceConfig.DEFAULT_FAST_MODEL], [VoiceConfig.DEFAULT_REVIEW_MODEL])을 쓴다.
 */
object VoiceModule {
    private var scope: CoroutineScope? = null
    private var parts: Parts? = null

    private class Parts(
        val voice: PleosVoice,
        val speaker: TtsSpeaker,
        val sfx: SfxPlayer,
        val instructor: SeniorInstructor,
    )

    @Synchronized
    fun create(context: Context, config: VoiceConfig): Triple<Voice, Sfx, Instructor> {
        parts?.let { return Triple(it.voice, it.sfx, it.instructor) }
        val app = context.applicationContext
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val cfg = config.copy(
            fastModel = config.fastModel.trim().ifEmpty { VoiceConfig.DEFAULT_FAST_MODEL },
            reviewModel = config.reviewModel.trim().ifEmpty { VoiceConfig.DEFAULT_REVIEW_MODEL },
        )
        Log.i(MEASURE_TAG, "voice module create $cfg")

        val log: (String) -> Unit = { Log.i(MEASURE_TAG, it) }
        val meter = LatencyMeter(now = SystemClock::elapsedRealtime, log = log)
        val knowledge = loadKnowledge(app)
        Log.i(MEASURE_TAG, "knowledge entries=${knowledge.entries.size} chars=${knowledge.rawText.length}")
        val llm: LlmClient? = if (cfg.llmEnabled) createLlmClient(cfg, log) else null
        val instructor = SeniorInstructor(knowledge, llm, meter, s, SystemClock::elapsedRealtime, log)
        val speaker = TtsSpeaker(app, s, meter)
        val voice = PleosVoice(app, s, speaker, meter, hooks = instructor)
        val sfx = SfxPlayer(app, s)

        // 선배가 말하거나 듣는 동안 효과음을 줄인다.
        combine(voice.speaking, voice.listening) { a, b -> a || b }
            .distinctUntilChanged()
            .onEach { sfx.setDucked(it) }
            .launchIn(s)

        // 시작할 때 연결·프롬프트 캐시를 데워 첫 질문의 지연을 줄인다.
        if (llm != null) {
            instructor.onListenStart()
            instructor.prewarmReview()
        }

        scope = s
        parts = Parts(voice, speaker, sfx, instructor)
        return Triple(voice, sfx, instructor)
    }

    @Synchronized
    fun release() {
        parts?.let {
            runCatching { it.voice.release() }
            runCatching { it.speaker.release() }
            runCatching { it.sfx.release() }
        }
        parts = null
        scope?.cancel()
        scope = null
    }

    private fun loadKnowledge(context: Context): KnowledgeBase = runCatching {
        val files = context.assets.list("knowledge").orEmpty().filter { it.endsWith(".md") }.sorted()
        KnowledgeBase.parse(files.map { name -> context.assets.open("knowledge/$name").bufferedReader().use { it.readText() } })
    }.getOrElse {
        Log.w(MEASURE_TAG, "knowledge load failed: $it")
        KnowledgeBase.EMPTY
    }
}
