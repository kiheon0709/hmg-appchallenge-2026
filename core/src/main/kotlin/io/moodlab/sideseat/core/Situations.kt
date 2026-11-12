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
    /** 화면 속 가상 속도(km/h)까지 보고 판정할 때(S05 감속). 있으면 [done] 대신 쓴다. */
    val doneWithSpeed: ((VehicleState, Float) -> Boolean)? = null,
)

class ChoiceDef(
    val question: String,
    val options: List<ChoiceOption>,
    val correct: Int,
    /** 틀렸을 때 바로잡는 말. */
    val wrongLine: String,
    /** YES_NO면 보기를 읽지 않고 질문만 한다. */
    val replyKind: ReplyKind = ReplyKind.CHOICE,
    /** "아직이요" 같은 답을 감점하지 않는다(S11 확인 질문). */
    val penaltyFree: Boolean = false,
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

    fun all(config: TrainingConfig = TrainingConfig()): List<SituationDef> = listOf(
        SituationDef(
            id = SituationId.HEAVY_RAIN, title = "폭우",
            intro = "비가 쏟아져요. 앞이 안 보여요!",
            // 출처: 도로교통법 제37조(비 올 때 등화), 시행규칙 제19조(가시거리 100m 이내 50% 감속).
            lesson = "폭우엔 전조등을 켜고 와이퍼를 가장 빠르게, 속도는 절반으로 줄여요.",
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
            // 출처: 도로교통법 제37조(터널 안 등화). 감점 조작 없음(명세 5.2).
            steps = listOf(lowBeamOn.copyEssential()),
        ),
        SituationDef(
            id = SituationId.DENSE_FOG, title = "짙은 안개",
            intro = "안개가 짙어요. 앞이 하얘요.",
            // 출처: 도로교통법 제37조(등화), 시행규칙 제19조(가시거리 100m 이내 50% 감속). 안개등은 신호를 못 읽어 말로만 안내(실측).
            lesson = "안개엔 전조등을 켜고 속도를 절반으로 줄여요. 안개등이 있으면 같이 켜요.",
            steps = listOf(
                lowBeamOn.copyEssential(),
                StepDef(
                    id = "slow_down", label = "속도 절반으로 줄이기",
                    hint = "브레이크를 밟아 속도를 절반으로 줄여요.",
                    available = { it.brakePressed != null },
                    doneWithSpeed = { _, v -> v <= config.cruiseSpeedKmh * config.fogSlowFraction },
                ),
            ),
            // 상향등 감점 없음: 근거 원문 미확인(명세 5.2).
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
            // 비상등은 일반 앱이 읽을 수 없어(실측) 스위치 판정 없이 순서 퀴즈로 한다.
            // 출처: 국토교통부 2020-09-21 카드뉴스, 한국도로공사 "비트박스"(정책브리핑 2024-07-31).
            id = SituationId.BREAKDOWN, title = "고속도로 고장",
            intro = "차가 멈췄어요. 여기는 고속도로예요!",
            lesson = "비상등, 트렁크, 밖으로 대피, 스마트폰 신고. '비트박스'로 기억해요.",
            steps = listOf(
                quizStep(
                    "first", "먼저 할 일", essential = true,
                    question = "가장 먼저 할 일은 뭘까요?",
                    options = listOf(
                        ChoiceOption("비상등 켜기", listOf("비상등", "비상")),
                        ChoiceOption("차에서 내려 차 상태 보기", listOf("내려", "상태", "살펴")),
                        ChoiceOption("보험사에 전화하기", listOf("보험", "전화")),
                    ),
                    wrongLine = "먼저 비상등이에요. 뒤차에 알려야 해요.",
                ),
                quizStep(
                    "trunk", "그다음",
                    question = "그다음엔요?",
                    options = listOf(
                        ChoiceOption("트렁크 열기", listOf("트렁크")),
                        ChoiceOption("차 안에서 기다리기", listOf("안에서", "기다")),
                        ChoiceOption("차 뒤에서 손 흔들기", listOf("뒤에서", "손", "흔들")),
                    ),
                    wrongLine = "트렁크를 열어 고장 난 차라는 걸 알려요.",
                ),
                quizStep(
                    "evacuate", "대피",
                    question = "사람은 어디로 갈까요?",
                    options = listOf(
                        ChoiceOption("가드레일 밖 안전한 곳", listOf("가드레일", "밖", "안전")),
                        ChoiceOption("차 바로 앞", listOf("앞")),
                        ChoiceOption("갓길에 세운 차 옆", listOf("옆", "갓길")),
                    ),
                    wrongLine = "차 근처는 위험해요. 가드레일 밖으로 먼저 피해요. 차 확인보다 대피가 먼저예요.",
                ),
                quizStep(
                    "report", "신고",
                    question = "마지막으로 할 일은요?",
                    options = listOf(
                        ChoiceOption("112나 한국도로공사 콜센터에 신고", listOf("112", "119", "신고", "도로공사", "콜센터")),
                        ChoiceOption("차 사진 찍어 올리기", listOf("사진", "올리")),
                        ChoiceOption("견인차가 올 때까지 그냥 기다리기", listOf("견인", "그냥")),
                    ),
                    wrongLine = "안전한 곳에서 스마트폰으로 112나 한국도로공사 콜센터에 신고해요.",
                ),
            ),
            loopSfx = listOf("hazard"), onceSfx = listOf("alarm"),
        ),
        SituationDef(
            id = SituationId.WARNING_LIGHT, title = "타이어 공기압 경고등",
            // 출처: 현대자동차 취급설명서(저압 타이어 경고등). 경고등 색깔 일반 규칙은 근거가 없어 쓰지 않는다.
            intro = "계기판에 타이어 공기압 경고등이 떴어요.",
            lesson = "타이어 공기압 경고등이 켜지면 바로 속도를 줄이고 점검받아요. 추운 날엔 공기압이 괜찮아도 켜질 수 있어요.",
            steps = listOf(
                StepDef(
                    id = "meaning", label = "경고등 뜻 맞히기", hint = "타이어 공기압이 낮다는 경고예요.", essential = true,
                    choice = ChoiceDef(
                        question = "이 경고등은 무슨 뜻일까요?",
                        options = listOf(
                            ChoiceOption("타이어 공기압이 낮아요", listOf("타이어", "공기압", "바람")),
                            ChoiceOption("엔진 오일이 부족해요", listOf("오일", "엔진")),
                            ChoiceOption("브레이크가 고장 났어요", listOf("브레이크")),
                        ),
                        correct = 0,
                        wrongLine = "이건 타이어 공기압이 낮다는 경고예요.",
                    ),
                ),
                StepDef(
                    id = "action", label = "맞는 조치 고르기", hint = "바로 속도를 줄이고 점검받아요.",
                    choice = ChoiceDef(
                        question = "그럼 어떻게 할까요?",
                        options = listOf(
                            ChoiceOption("바로 속도를 줄이고 급커브를 피해 점검받아요", listOf("줄이", "속도", "점검", "커브", "확인")),
                            ChoiceOption("그냥 목적지까지 달려요", listOf("그냥", "달려", "목적지")),
                            ChoiceOption("경고등이 꺼질 때까지 기다려요", listOf("꺼질", "기다")),
                        ),
                        correct = 0,
                        wrongLine = "경고등이 켜지면 바로 속도를 줄여요. 계기판에서 어느 타이어인지 보고 점검받아요.",
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
            onceSfx = listOf("yawn"),
        ),
        SituationDef(
            // 출처: 한국교통안전공단 보도자료 2023-12-08. 브레이크·EPB 유지·중립 세 동작, 순서는 정하지 않음.
            // EPB 신호는 읽을 수 없어(실측) 브레이크 유지만 채점하고 나머지는 말로 안내한다.
            id = SituationId.UNINTENDED_ACCEL, title = "의도치 않은 가속",
            intro = "차가 계속 빨라져요! 브레이크를 꾹 밟아요!",
            lesson = "브레이크는 떼지 말고 계속 밟아요. 전자식 주차 브레이크는 멈출 때까지 당기고, 기어는 중립으로요. 시동 끄기는 권하지 않아요.",
            steps = listOf(
                StepDef(
                    id = "brake_hold", label = "브레이크 3초 이상 밟기", essential = true, holdMs = config.brakeHoldMs,
                    hint = "브레이크를 끝까지 밟고 떼지 마세요.", setup = "브레이크에서 발을 떼 주세요",
                    available = { it.brakePressed != null }, done = { it.brakePressed == true },
                ),
            ),
            // 감점 조작 없음(명세 5.2). 떼면 3초를 처음부터 다시 센다.
            loopSfx = listOf("engine"), onceSfx = listOf("engine_rise"),
        ),
        SituationDef(
            id = SituationId.BEFORE_EXIT, title = "내리기 전",
            intro = "다 왔어요. 내리기 전에 세 가지만 볼까요?",
            // 출처: 도로교통법(주차제동장치). 전조등·창문은 근거 없는 확인 습관이라 채점하지 않는다(모두 감점 없음).
            lesson = "주차 브레이크는 꼭 채워요. 전조등과 창문도 한 번 더 보는 습관이에요.",
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
                // 주차 브레이크 신호는 읽을 수 없어(실측) 선배가 묻고 말로 답한다.
                StepDef(
                    id = "parking_brake", label = "주차 브레이크 확인", hint = "주차 브레이크를 채워요.",
                    choice = ChoiceDef(
                        question = "주차 브레이크 채웠나요?",
                        options = listOf(
                            ChoiceOption("네, 채웠어요", listOf("네", "응", "예", "채웠", "그래")),
                            ChoiceOption("아직이요", listOf("아직", "아니", "안 했", "안했")),
                        ),
                        correct = 0,
                        wrongLine = "그럼 지금 채워 볼까요? 다 되면 '응'이라고 해 주세요.",
                        replyKind = ReplyKind.YES_NO,
                        penaltyFree = true,
                    ),
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
                    available = { it.hazardOn != null || it.turnSignal == TurnSignal.HAZARD }, done = { it.hazardOn == true || it.turnSignal == TurnSignal.HAZARD },
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

    private fun quizStep(
        id: String,
        label: String,
        question: String,
        options: List<ChoiceOption>,
        wrongLine: String,
        essential: Boolean = false,
    ) = StepDef(
        id = id, label = label, hint = options.first().text, essential = essential,
        choice = ChoiceDef(question, options, correct = 0, wrongLine = wrongLine),
    )

    private fun StepDef.copyEssential() = StepDef(id, label, hint, setup, true, holdMs, available, done, choice, doneWithSpeed)
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
