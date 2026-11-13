package io.moodlab.sideseat.voice

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.moodlab.sideseat.core.ReplyKind
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
        val wake: WakeListener,
    )

    /** 주행 중 연속 청취(호출어). [create] 뒤에 쓴다. 켜고 끄는 시점(코치 모드 진입·이탈)은 통합 세션이 정한다. */
    val wake: WakeListening? get() = parts?.wake

    // ---------- 차종별 스위치 위치 ----------
    private var switchGuide: SwitchGuide? = null
    private var detectedVehicle: VehicleProfile? = null
    private var forcedVehicle: VehicleProfile? = null
    private var lastVinKey: String? = null

    /** 지금 쓰는 차종(강제 지정이 우선). 모르면 null(공통 문장 + "차마다 조금 달라요"). */
    val currentVehicle: VehicleProfile? get() = forcedVehicle ?: detectedVehicle

    /** VIN 또는 VIN 앞 8자리 차종 키(VehicleState.vehicleModelKey)가 바뀔 때 부른다. 같은 값이면 아무것도 안 한다. */
    @Synchronized
    fun setVehicle(vinOrKey: String?) {
        if (vinOrKey == lastVinKey) return
        lastVinKey = vinOrKey
        detectedVehicle = switchGuide?.resolve(vinOrKey)
        Log.i(MEASURE_TAG, "vehicle detected=${detectedVehicle?.id ?: "unknown"}")
        applyVehicle()
    }

    /** 디버그: 차종을 강제로 지정(에뮬레이터 VIN은 가짜). null이면 강제 해제. 모르는 아이디면 false. */
    @Synchronized
    fun forceVehicle(profileId: String?): Boolean {
        val p = profileId?.let { id -> switchGuide?.byId(id) ?: return false }
        forcedVehicle = p
        Log.i(MEASURE_TAG, "vehicle forced=${p?.id ?: "off"}")
        applyVehicle()
        return true
    }

    /** 디버그·설정 화면: 데이터가 있는 차종 (아이디, 이름). */
    fun vehicleProfiles(): List<Pair<String, String>> = switchGuide?.profiles?.map { it.id to it.name }.orEmpty()

    /**
     * 훈련 힌트·결과 화면의 "스위치 위치" 문장(현재 차종). 아이디는 sw_wiper, sw_headlight, sw_highbeam, sw_fog, sw_hazard,
     * sw_defrost, sw_ac, sw_recirc, sw_window, sw_epb. 모르는 아이디면 null(core의 공통 문장을 그대로 쓰면 됨).
     */
    fun switchLocation(entryId: String, detailed: Boolean = false): String? =
        switchGuide?.location(entryId, currentVehicle, detailed)

    private fun applyVehicle() {
        val guide = switchGuide ?: return
        parts?.instructor?.setKnowledge(guide.knowledgeFor(currentVehicle))
    }

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
        val guide = loadSwitchGuide(app)
        switchGuide = guide
        val knowledge = guide.knowledgeFor(null)
        Log.i(MEASURE_TAG, "knowledge entries=${knowledge.entries.size} chars=${knowledge.rawText.length} vehicles=${guide.profiles.map { it.id }}")
        val llm: LlmClient? = if (cfg.llmEnabled) createLlmClient(cfg, log) else null
        val instructor = SeniorInstructor(knowledge, llm, meter, s, SystemClock::elapsedRealtime, log, speculative = cfg.speculativeRequests)
        val speaker = TtsSpeaker(app, s, meter, chunked = cfg.chunkedTts)
        val voice = PleosVoice(app, s, speaker, meter, hooks = instructor, bargeIn = cfg.bargeIn)
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

        val wake = WakeListener(app, s, voice, speaker, sfx, cfg.wake)

        scope = s
        parts = Parts(voice, speaker, sfx, instructor, wake)
        return Triple(voice, sfx, instructor)
    }

    /**
     * 실측·시연용: PCM(16kHz 16bit mono, 헤더 없는 raw)을 마이크 대신 넣어 [Voice.listen]과 같은 경로로 듣는다.
     * 결과를 instructor.answer에 넘기면 "말 끝 → 첫 음성" 지연이 SideSeatMeasure에 남는다. create 전이면 null.
     */
    suspend fun debugListenFromPcm(pcm: ByteArray, kind: ReplyKind, onPartial: (String) -> Unit = {}): String? =
        parts?.voice?.listenFromPcm(pcm, kind, onPartial)

    @Synchronized
    fun release() {
        parts?.let {
            runCatching { it.wake.stop() }
            runCatching { it.voice.release() }
            runCatching { it.speaker.release() }
            runCatching { it.sfx.release() }
        }
        parts = null
        switchGuide = null
        detectedVehicle = null
        forcedVehicle = null
        lastVinKey = null
        scope?.cancel()
        scope = null
    }

    private fun loadSwitchGuide(context: Context): SwitchGuide = runCatching {
        fun read(dir: String) = context.assets.list(dir).orEmpty().filter { it.endsWith(".md") }.sorted()
            .map { name -> context.assets.open("$dir/$name").bufferedReader().use { it.readText() } }
        SwitchGuide.parse(read("knowledge"), read("knowledge/vehicles"))
    }.getOrElse {
        Log.w(MEASURE_TAG, "knowledge load failed: $it")
        SwitchGuide(emptyList(), emptyList(), emptyList())
    }
}
