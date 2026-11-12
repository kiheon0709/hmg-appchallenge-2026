package io.moodlab.sideseat.core

/**
 * 훈련 상황 정의(명세 5.2). 판정 조건, 대사, 효과음을 한곳에 둔다.
 * 대처 절차는 docs/plan/safety-sources.md에 출처가 있는 것만 쓴다. 출처가 없는 상황은 [TrainingConfig.disabled]로 끈다.
 */
class SituationDef(
    val id: SituationId,
    val title: String,
    /** 상황이 터질 때 선배가 하는 말. */
    val intro: String,
    /** 성공 뒤 평온 구간에서 짚는 한 줄. */
    val lesson: String,
    val steps: List<StepDef>,
    val penalties: List<PenaltyDef> = emptyList(),
    /** 상황 동안 반복 재생할 소리. 끝나면 멈춘다. */
    val loopSfx: List<String> = emptyList(),
    /** 상황이 터질 때 한 번 재생할 소리. */
    val onceSfx: List<String> = emptyList(),
    /** 힌트를 말하지 않는다(반응 시간 상황). */
    val noHint: Boolean = false,
)

/**
 * 해야 할 조작 하나. [choice]가 있으면 말이나 화면 선택으로 답하는 단계이고, 신호 단계가 모두 끝난 뒤 순서대로 묻는다.
 * @param available 이 단계를 판정할 신호가 있는가. 없으면 단계를 빼고, [essential] 단계가 없으면 상황 전체를 건너뛴다.
 * @param setup 시작 때 이미 [done] 조건이 참이면 되돌려 달라고 할 말("와이퍼를 꺼 두세요").
 * @param holdMs 조건을 이만큼 유지해야 완료(반응 시간은 처음 참이 된 때).
 */
class StepDef(
    val id: String,
    val label: String,
    /** 힌트·실패 때 말하는 스위치 위치(차종 공통 표현). */
    val hint: String,
    val setup: String? = null,
    val essential: Boolean = false,
    val holdMs: Long = 0,
    val available: (VehicleState) -> Boolean = { true },
    val done: (VehicleState) -> Boolean = { false },
    val choice: ChoiceDef? = null,
)

class ChoiceDef(
    val question: String,
    val options: List<ChoiceOption>,
    val correct: Int,
    /** 틀렸을 때 바로잡는 말. */
    val wrongLine: String,
)

class ChoiceOption(val text: String, val keywords: List<String>)

/** 감점 조작. 상태를 가지므로 상황 시작마다 [detector]로 새로 만든다. */
class PenaltyDef(
    val id: String,
    /** 감점이 생기는 순간 선배가 하는 말. */
    val line: String?,
    val detector: (TrainingConfig) -> PenaltyDetector,
)

fun interface PenaltyDetector {
    /** @param sinceActiveMs 상황이 터진 뒤 지난 시간 @param doneSteps 끝난 단계 id */
    fun update(state: VehicleState, nowMs: Long, sinceActiveMs: Long, doneSteps: Set<String>): Boolean
}

/** 선택지 답 해석: 보기 문장 그대로, "1번"·"첫 번째" 같은 번호, 보기의 핵심어 순으로 본다. 못 알아들으면 null. */
fun ChoiceDef.match(answer: String): Int? {
    val a = answer.trim()
    if (a.isEmpty()) return null
    options.indexOfFirst { it.text == a }.takeIf { it >= 0 }?.let { return it }
    val compact = a.replace(" ", "")
    val numbered = listOf(
        listOf("1", "1번", "일번", "첫번째", "첫번", "첫째"),
        listOf("2", "2번", "두번째", "둘째"),
        listOf("3", "3번", "삼번", "세번째", "셋째"),
    )
    numbered.forEachIndexed { i, words ->
        if (i < options.size && words.any { w -> compact == w || (w.length >= 2 && compact.startsWith(w)) }) return i
    }
    val scores = options.map { o -> o.keywords.count { k -> a.contains(k) } }
    val best = scores.max()
    if (best == 0 || scores.count { it == best } > 1) return null
    return scores.indexOf(best)
}

object SituationCatalog {
    private const val HEADLIGHT_HINT = "전조등은 핸들 왼쪽 레버 끝을 돌려서 켜요."
    private const val HAZARD_HINT = "비상등은 가운데 빨간 삼각형 버튼이에요."

    private val lowBeamOn = StepDef(
        id = "low_beam_on", label = "전조등 켜기", hint = HEADLIGHT_HINT, setup = "전조등을 꺼 두세요",
        available = { it.lowBeamOn != null }, done = { it.lowBeamOn == true },
    )

    private fun highBeamPenalty(line: String) = PenaltyDef("HIGH_BEAM", line) { _ ->
        PenaltyDetector { s, _, _, _ -> s.highBeamOn == true }
    }

    fun all(config: TrainingConfig = TrainingConfig()): List<SituationDef> = listOf(
        SituationDef(
            id = SituationId.HEAVY_RAIN, title = "폭우",
            intro = "비가 쏟아져요. 앞이 안 보여요!",
            lesson = "폭우엔 와이퍼를 가장 빠르게, 전조등도 같이 켜요.",
            steps = listOf(
                StepDef(
                    id = "wiper_fast", label = "와이퍼 빠르게", essential = true,
                    hint = "와이퍼 레버는 핸들 오른쪽이에요. 가장 빠른 단으로 옮겨요.", setup = "와이퍼를 꺼 두세요",
                    available = { it.wiper != null }, done = { (it.wiper?.ordinal ?: -1) >= config.heavyRainMinWiper.ordinal },
                ),
                lowBeamOn,
            ),
            loopSfx = listOf("rain"), onceSfx = listOf("thunder"),
        ),
        SituationDef(
            id = SituationId.FOGGED_WINDSHIELD, title = "앞유리 김서림",
            intro = "터널을 나왔더니 앞유리가 뿌예져요!",
            lesson = "김서림엔 성에 제거, 에어컨, 외기. 이 세 개예요.",
            steps = listOf(
                StepDef(
                    id = "defrost_on", label = "앞유리 성에 제거", essential = true,
                    hint = "성에 제거는 공조 패널의 앞유리 모양 버튼이에요.", setup = "앞유리 성에 제거를 꺼 두세요",
                    available = { it.defrostFrontOn != null }, done = { it.defrostFrontOn == true },
                ),
                StepDef(
                    id = "ac_on", label = "에어컨 켜기",
                    hint = "에어컨은 공조 패널의 A/C 버튼이에요.", setup = "에어컨을 꺼 두세요",
                    available = { it.acOn != null }, done = { it.acOn == true },
                ),
                StepDef(
                    id = "fresh_air", label = "외기로 바꾸기",
                    hint = "공조 패널의 차 안 화살표 버튼을 꺼서 외기로 바꿔요.", setup = "내기순환을 켜 두세요",
                    available = { it.recirculationOn != null }, done = { it.recirculationOn == false },
                ),
            ),
            penalties = listOf(
                PenaltyDef("WIPER_ONLY", "안쪽 김이라 와이퍼로는 안 닦여요. 성에 제거 버튼이에요.") { _ -> WiperOnlyDetector() },
            ),
            loopSfx = listOf("breath"),
        ),
        SituationDef(
            id = SituationId.SUDDEN_STOP, title = "앞차 급정거",
            intro = "앞차가 갑자기 서요!",
            lesson = "앞차와 거리를 넉넉히 두면 이 시간을 벌 수 있어요.",
            steps = listOf(
                StepDef(
                    id = "brake", label = "브레이크 밟기", essential = true,
                    hint = "앞차 브레이크등이 보이면 바로 브레이크예요.", setup = "브레이크에서 발을 떼 주세요",
                    available = { it.brakePressed != null }, done = { it.brakePressed == true },
                ),
            ),
            penalties = listOf(
                PenaltyDef("STEER_JERK", "핸들을 급하게 꺾으면 더 위험해요. 브레이크가 먼저예요.") { c -> SteerJerkDetector(c) },
            ),
            onceSfx = listOf("tire"),
            noHint = true,
        ),
        SituationDef(
            id = SituationId.TUNNEL, title = "터널 진입",
            intro = "터널이에요. 어두워져요.",
            lesson = "터널 들어가기 전에 전조등부터 켜요.",
            steps = listOf(lowBeamOn.copyEssential()),
            penalties = listOf(highBeamPenalty("터널 안에선 상향등 말고 하향등이에요.")),
        ),
        SituationDef(
            id = SituationId.DENSE_FOG, title = "짙은 안개",
            intro = "안개가 짙어요. 앞이 하얘요.",
            lesson = "안개엔 하향등과 안개등이에요. 상향등은 오히려 하얗게 번져요.",
            steps = listOf(
                lowBeamOn,
                StepDef(
                    id = "fog_on", label = "안개등 켜기", essential = true,
                    hint = "안개등은 왼쪽 레버의 가운데 링을 돌려요.", setup = "안개등을 꺼 두세요",
                    available = { it.anyFogOn != null }, done = { it.anyFogOn == true },
                ),
            ),
            penalties = listOf(highBeamPenalty("안개에선 상향등이 빛을 흩뿌려요. 하향등이 나아요.")),
        ),
        SituationDef(
            id = SituationId.ONCOMING_GLARE, title = "맞은편 차",
            intro = "맞은편에서 차가 와요. 우리 상향등이 켜져 있어요.",
            lesson = "맞은편 차가 보이면 상향등부터 내려요.",
            steps = listOf(
                StepDef(
                    id = "high_beam_off", label = "상향등 끄기", essential = true,
                    hint = "상향등은 왼쪽 레버예요. 원래 자리로 돌려요.", setup = "상향등을 켜 두세요",
                    available = { it.highBeamOn != null }, done = { it.highBeamOn == false },
                ),
            ),
        ),
        SituationDef(
            id = SituationId.BREAKDOWN, title = "고속도로 고장",
            intro = "차가 멈췄어요. 여기는 고속도로예요!",
            lesson = "비상등 켜고, 트렁크 열고, 가드레일 밖으로. 그다음 신고예요.",
            steps = listOf(
                StepDef(
                    id = "hazard_on", label = "비상등 켜기", essential = true, hint = HAZARD_HINT, setup = "비상등을 꺼 두세요",
                    available = { it.turnSignal != null }, done = { it.turnSignal == TurnSignal.HAZARD },
                ),
                StepDef(
                    id = "evacuate", label = "다음 행동 고르기", hint = "트렁크를 열고 가드레일 밖으로 피해요.",
                    choice = ChoiceDef(
                        question = "비상등 다음엔 뭘 할까요?",
                        options = listOf(
                            ChoiceOption("트렁크를 열고 가드레일 밖으로 피해요", listOf("트렁크", "가드레일", "밖으로", "대피", "피해")),
                            ChoiceOption("차 안에서 안전벨트 매고 기다려요", listOf("차 안", "안에서", "기다", "벨트")),
                            ChoiceOption("차 뒤에 서서 손을 흔들어요", listOf("뒤에", "손", "흔들")),
                        ),
                        correct = 0,
                        wrongLine = "차 안이나 차 뒤는 뒤차에 부딪힐 수 있어요. 가드레일 밖이 안전해요.",
                    ),
                ),
            ),
            loopSfx = listOf("hazard"), onceSfx = listOf("alarm"),
        ),
        SituationDef(
            id = SituationId.WARNING_LIGHT, title = "타이어 공기압 경고등",
            intro = "계기판에 노란 경고등이 떴어요.",
            lesson = "노란 경고등은 급하진 않지만 미루면 안 돼요.",
            steps = listOf(
                StepDef(
                    id = "meaning", label = "경고등 뜻 맞히기", hint = "말발굽 모양 안에 느낌표는 타이어 공기압 경고예요.", essential = true,
                    choice = ChoiceDef(
                        question = "이 경고등은 무슨 뜻일까요?",
                        options = listOf(
                            ChoiceOption("타이어 공기압이 낮아요", listOf("타이어", "공기압", "바람")),
                            ChoiceOption("엔진 오일이 부족해요", listOf("오일", "엔진")),
                            ChoiceOption("브레이크가 고장 났어요", listOf("브레이크")),
                        ),
                        correct = 0,
                        wrongLine = "말발굽 모양 안에 느낌표, 타이어 공기압 경고예요.",
                    ),
                ),
                StepDef(
                    id = "action", label = "맞는 조치 고르기", hint = "속도를 줄이고 안전한 곳에 세워 타이어를 봐요.",
                    choice = ChoiceDef(
                        question = "그럼 어떻게 할까요?",
                        options = listOf(
                            ChoiceOption("속도를 줄이고 안전한 곳에 세워 타이어를 봐요", listOf("줄이", "세워", "세우", "정차", "확인", "안전한")),
                            ChoiceOption("그냥 목적지까지 달려요", listOf("그냥", "달려", "목적지")),
                            ChoiceOption("경고등이 꺼질 때까지 기다려요", listOf("꺼질", "기다")),
                        ),
                        correct = 0,
                        wrongLine = "공기압이 낮은 채로 달리면 타이어가 상할 수 있어요. 속도를 줄이고 세워서 봐요.",
                    ),
                ),
            ),
            onceSfx = listOf("alarm"),
        ),
        SituationDef(
            id = SituationId.DROWSY, title = "졸음",
            intro = "히터 켜고 한 시간째예요. 눈이 감겨요.",
            lesson = "외기와 창문은 잠깐 버티는 방법이에요. 졸리면 쉬어 가는 게 답이에요.",
            steps = listOf(
                StepDef(
                    id = "fresh_air", label = "외기로 바꾸기", essential = true,
                    hint = "공조 패널의 차 안 화살표 버튼을 꺼서 외기로 바꿔요.", setup = "내기순환을 켜 두세요",
                    available = { it.recirculationOn != null }, done = { it.recirculationOn == false },
                ),
                StepDef(
                    id = "window_open", label = "창문 조금 열기",
                    hint = "운전석 창문 스위치를 살짝 내려요.", setup = "운전석 창문을 닫아 두세요",
                    available = { it.driverWindowOpenPct != null }, done = { (it.driverWindowOpenPct ?: 0) > 0 },
                ),
            ),
            loopSfx = listOf("yawn"),
        ),
        SituationDef(
            id = SituationId.UNINTENDED_ACCEL, title = "의도치 않은 가속",
            intro = "차가 계속 빨라져요! 브레이크예요!",
            lesson = "브레이크는 떼지 말고 끝까지, 주차 브레이크는 계속 당겨요.",
            steps = listOf(
                StepDef(
                    id = "brake_hold", label = "브레이크 밟고 유지", essential = true, holdMs = config.brakeHoldMs,
                    hint = "브레이크를 끝까지 밟고 떼지 마세요.", setup = "브레이크에서 발을 떼 주세요",
                    available = { it.brakePressed != null }, done = { it.brakePressed == true },
                ),
                StepDef(
                    id = "epb_pull", label = "주차 브레이크 당기기",
                    hint = "전자식 주차 브레이크는 기어 근처 P 스위치예요. 계속 당겨요.", setup = "주차 브레이크를 풀어 두세요",
                    available = { it.parkingBrakeOn != null }, done = { it.epbOn() },
                ),
            ),
            penalties = listOf(
                PenaltyDef("BRAKE_PUMP", "브레이크를 떼었다 밟으면 힘이 약해질 수 있어요. 계속 밟아요.") { c -> BrakePumpDetector(c.brakePumpReleases) },
            ),
            loopSfx = listOf("engine"),
        ),
        SituationDef(
            id = SituationId.BEFORE_EXIT, title = "내리기 전",
            intro = "다 왔어요. 내리기 전에 세 가지만 볼까요?",
            lesson = "전조등, 창문, 주차 브레이크. 내리기 전 세 가지예요.",
            steps = listOf(
                StepDef(
                    id = "lights_off", label = "전조등 끄기", hint = "전조등 스위치를 꺼요.", setup = "전조등을 켜 두세요", essential = true,
                    available = { it.lowBeamOn != null }, done = { it.lowBeamOn == false },
                ),
                StepDef(
                    id = "windows_closed", label = "창문 닫기", hint = "창문 스위치를 끝까지 올려 닫아요.", setup = "운전석 창문을 조금 열어 두세요",
                    available = { it.driverWindowOpenPct != null },
                    done = { (it.driverWindowOpenPct ?: 0) == 0 && it.windowOpenPct.values.all { p -> p == 0 } },
                ),
                StepDef(
                    id = "parking_brake", label = "주차 브레이크", hint = "주차 브레이크를 걸어요.", setup = "주차 브레이크를 풀어 두세요",
                    available = { it.parkingBrakeOn != null }, done = { it.epbOn() },
                ),
            ),
        ),
        SituationDef(
            id = SituationId.WIPER_DEBRIS, title = "앞유리 이물질",
            intro = "앞유리에 비닐이 붙었어요!",
            lesson = "비상등 켜고 와이퍼로 떼어 내요. 급브레이크는 뒤차가 위험해요.",
            steps = listOf(
                StepDef(
                    id = "hazard_on", label = "비상등 켜기", hint = HAZARD_HINT, setup = "비상등을 꺼 두세요",
                    available = { it.turnSignal != null }, done = { it.turnSignal == TurnSignal.HAZARD },
                ),
                StepDef(
                    id = "wiper_on", label = "와이퍼로 떼기", essential = true, hint = "와이퍼를 켜서 떼어 내요.", setup = "와이퍼를 꺼 두세요",
                    available = { it.wiper != null }, done = { (it.wiper ?: WiperLevel.OFF) != WiperLevel.OFF },
                ),
            ),
            penalties = listOf(
                PenaltyDef("HARD_BRAKE", "급브레이크는 뒤차가 위험해요. 비상등부터예요.") { c -> HardBrakeDetector(c.hardBrakeWindowMs, c.hardBrakeHoldMs) },
            ),
        ),
    )

    fun title(id: SituationId): String = all().first { it.id == id }.title

    private fun StepDef.copyEssential() = StepDef(id, label, hint, setup, true, holdMs, available, done, choice)
}

/** 주차 브레이크(일반·전자식) 경고등 또는 주차 브레이크 신호. */
internal fun VehicleState.epbOn(): Boolean =
    parkingBrakeOn == true || "ELECTRONIC_PARKING_BRAKE" in activeTelltales || "PARKING_BRAKE" in activeTelltales

/** [TrainingConfig.steerJerkWindowMs] 안에서 조향각이 [TrainingConfig.steerJerkDeg] 이상 바뀌면 급조작. */
internal class SteerJerkDetector(private val c: TrainingConfig) : PenaltyDetector {
    private val samples = ArrayDeque<Pair<Long, Float>>()
    override fun update(state: VehicleState, nowMs: Long, sinceActiveMs: Long, doneSteps: Set<String>): Boolean {
        val a = state.steeringAngleDeg ?: return false
        samples.addLast(nowMs to a)
        while (samples.isNotEmpty() && nowMs - samples.first().first > c.steerJerkWindowMs) samples.removeFirst()
        val values = samples.map { it.second }
        return values.max() - values.min() >= c.steerJerkDeg
    }
}

/** 성에 제거 전에 와이퍼를 새로 켜거나 올리면 감점. 앞 상황에서 켜 둔 와이퍼는 감점하지 않는다. */
internal class WiperOnlyDetector : PenaltyDetector {
    private var baseline: WiperLevel? = null
    override fun update(state: VehicleState, nowMs: Long, sinceActiveMs: Long, doneSteps: Set<String>): Boolean {
        val w = state.wiper ?: return false
        val base = baseline ?: w.also { baseline = it }
        return w.ordinal > base.ordinal && "defrost_on" !in doneSteps
    }
}

/** 브레이크를 밟은 뒤 [releases]번 이상 뗐다 밟으면 감점. */
internal class BrakePumpDetector(private val releases: Int) : PenaltyDetector {
    private var pressedOnce = false
    private var prev = false
    private var count = 0
    override fun update(state: VehicleState, nowMs: Long, sinceActiveMs: Long, doneSteps: Set<String>): Boolean {
        val p = state.brakePressed ?: return false
        if (p) pressedOnce = true
        if (pressedOnce && prev && !p) count++
        prev = p
        return count >= releases
    }
}

/** 상황이 터지고 [windowMs] 안에 밟은 브레이크를 [holdMs] 넘게 유지하면 급브레이크. */
internal class HardBrakeDetector(private val windowMs: Long, private val holdMs: Long) : PenaltyDetector {
    private var pressStart: Long? = null
    override fun update(state: VehicleState, nowMs: Long, sinceActiveMs: Long, doneSteps: Set<String>): Boolean {
        if (state.brakePressed == true) {
            if (pressStart == null && sinceActiveMs <= windowMs) pressStart = nowMs
        } else {
            pressStart = null
        }
        return pressStart?.let { nowMs - it >= holdMs } ?: false
    }
}
