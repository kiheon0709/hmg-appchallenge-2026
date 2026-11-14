package io.moodlab.sideseat.voice

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import io.moodlab.sideseat.core.ReplyKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /**
     * 디버그: 호출어 이름 후보 시험. 각 PCM(16kHz 16bit mono raw)을 Pleos STT로 받아 적고, 후보 낱말로 판정해
     * 인식률·오인식을 돌려준다(예: "wake=18/20 falseAlarm=0/20"). 받아 적은 글은 SideSeatMeasure 로그에 남긴다.
     * 판정은 호칭 꼴 규칙([WakeMatcher]). 연속 청취는 시험 동안 멈췄다가 다시 켠다.
     * @param words 이 이름과 STT 변형(예: ["타비", "탑이"]), @param weakWords 일상어와 겹치는 변형([allowWeak]일 때만 좁은 규칙으로 인정)
     * @param samples (PCM, 호출어를 말했는지)
     */
    suspend fun debugWakeTrial(
        words: List<String>,
        weakWords: List<String>,
        samples: List<Pair<ByteArray, Boolean>>,
        allowWeak: Boolean = false,
    ): String {
        val p = parts ?: return "voice module not created"
        val matcher = WakeMatcher(wakeWords = words, weakWakeWords = weakWords, allowWeak = allowWeak)
        val heard = p.wake.paused {
            samples.mapIndexed { i, (pcm, expect) ->
                val text = p.voice.wakeCycle(pcm, maxMs = TRIAL_MAX_MS, onPartial = {}, stopWhen = { false }).orEmpty()
                val woke = matcher.detect(text) is WakeMatcher.Result.Wake
                Log.i(MEASURE_TAG, "wake trial #$i expect=${if (expect) "wake" else "plain"} woke=$woke heard='$text'")
                text to expect
            }
        }
        val score = matcher.score(heard)
        Log.i(MEASURE_TAG, "wake trial words=${words.take(3)} allowWeak=$allowWeak $score")
        return score.toString()
    }

    /** 사람 목소리 호출어 시험의 화면 표시용 상태(디버그 패널). */
    data class WakeTestState(val running: Boolean, val lines: List<String>, val summary: String)

    private val _wakeTest = MutableStateFlow(WakeTestState(false, emptyList(), ""))
    val wakeTest: StateFlow<WakeTestState> = _wakeTest.asStateFlow()
    private var wakeTestJob: Job? = null

    /**
     * 디버그: 마이크에 "타비야 …"를 직접 여러 번 불러 보는 시험. 연속 청취를 멈추고 Pleos STT로 계속 들으며,
     * 받아 적은 글과 깨어남 여부를 한 번마다 토스트·[wakeTest]·SideSeatMeasure 로그(`wake test #n …`)로 보여 준다.
     * 깨어나면 확인음. [stopWakeTest]까지 계속하고, 끝나면 연속 청취가 켜져 있었으면 다시 켠다. create 전이면 false.
     */
    fun startWakeTest(): Boolean {
        val p = parts ?: return false
        val s = scope ?: return false
        if (wakeTestJob?.isActive == true) return true
        val tally = WakeLiveTally()
        wakeTestJob = s.launch {
            p.wake.paused {
                _wakeTest.value = WakeTestState(true, emptyList(), tally.summary())
                Log.i(MEASURE_TAG, "wake test start words=${Persona.WAKE_WORDS} weak=${Persona.WEAK_WAKE_WORDS}")
                toast("호출어 시험: \"${Persona.CALL}, …\" 하고 불러 보세요")
                while (isActive) {
                    val text = p.voice.wakeCycle(null, maxMs = TEST_CYCLE_MS, onPartial = {}, stopWhen = { false })
                    val error = p.voice.lastCycleError
                    if (error != null) {
                        Log.w(MEASURE_TAG, "wake test stt error=$error")
                        delay(TEST_ERROR_WAIT_MS)
                        continue
                    }
                    if (text.isNullOrBlank()) continue
                    val e = tally.record(text)
                    if (e.woke) p.sfx.play("wake")
                    Log.i(MEASURE_TAG, "wake test ${e.line} | ${tally.summary()}")
                    _wakeTest.value = WakeTestState(true, tally.lines(), tally.summary())
                    toast(e.line)
                }
            }
        }
        return true
    }

    fun stopWakeTest() {
        wakeTestJob?.cancel()
        wakeTestJob = null
        if (_wakeTest.value.running) {
            _wakeTest.value = _wakeTest.value.copy(running = false)
            Log.i(MEASURE_TAG, "wake test stop ${_wakeTest.value.summary}")
        }
    }

    private fun toast(text: String) {
        val c = appContext ?: return
        Toast.makeText(c, text, Toast.LENGTH_SHORT).show()
    }

    private var appContext: Context? = null
    private const val TEST_CYCLE_MS = 8_000L
    private const val TEST_ERROR_WAIT_MS = 1_000L
    private const val TRIAL_MAX_MS = 8_000L

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

    /** 사용자가 고를 수 있는 차종 (아이디, 이름). 차종을 모를 때 쓰는 기본 차종은 뺀다. */
    fun selectableVehicles(): List<Pair<String, String>> = switchGuide?.profiles?.filter { !it.isDefault }?.map { it.id to it.name }.orEmpty()

    /** 디버그·설정 화면: 데이터가 있는 차종 (아이디, 이름). */
    fun vehicleProfiles(): List<Pair<String, String>> = switchGuide?.profiles?.map { it.id to it.name }.orEmpty()

    /**
     * 훈련 힌트·결과 화면의 "스위치 위치" 문장(현재 차종). 아이디는 sw_wiper, sw_headlight, sw_highbeam, sw_fog, sw_hazard,
     * sw_defrost, sw_ac, sw_recirc, sw_window, sw_epb. 모르는 아이디면 null(core의 공통 문장을 그대로 쓰면 됨).
     */
    fun switchLocation(entryId: String, detailed: Boolean = false): String? =
        switchGuide?.location(entryId, currentVehicle, detailed)

    /**
     * core 훈련 단계 아이디(StepView.id)로 이 차의 스위치 위치 문장. 결과 화면·실패 대사에 붙인다.
     * 스위치가 없는 단계(브레이크 페달, 말로 답하기)거나 데이터가 없으면 null(core 문장만 쓰면 됨).
     */
    fun switchLocationForStep(stepId: String, detailed: Boolean = false): String? =
        SwitchGuide.STEP_TO_SWITCH[stepId]?.let { switchLocation(it, detailed) }

    private fun applyVehicle() {
        val guide = switchGuide ?: return
        parts?.instructor?.setKnowledge(guide.knowledgeFor(currentVehicle))
    }

    @Synchronized
    fun create(context: Context, config: VoiceConfig): Triple<Voice, Sfx, Instructor> {
        parts?.let { return Triple(it.voice, it.sfx, it.instructor) }
        val app = context.applicationContext
        appContext = app
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

        // 타비가 말하거나 듣는 동안 효과음을 줄인다.
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
        stopWakeTest()
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
        appContext = null
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
