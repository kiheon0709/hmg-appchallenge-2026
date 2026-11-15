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
    /** 걸린 시간을 재지 않는 상황(버티기)의 성공 대사. 있으면 "~초 걸렸어요" 대신 이 말을 한다. */
    val successLine: String? = null,
)

/**
 * 해야 할 조작 하나. [choice]가 있으면 말이나 화면 선택으로 답하는 단계이고, 목록에서 자기보다 앞에 있는 단계가 모두 끝나면 묻는다.
 * @param available 이 단계를 판정할 신호가 있는가. 없으면 단계를 빼고, [essential] 단계가 없으면 상황 전체를 건너뛴다.
 * @param setup 시작 때 이미 [done] 조건이 참이면 되돌려 달라고 할 말("와이퍼를 꺼 두세요").
 * @param holdMs 조건을 이만큼 유지해야 완료(반응 시간은 처음 참이 된 때).
 */
class StepDef(
    val id: String,
    val label: String,
    /** 힌트·실패 때 말하는 할 일(차종 중립). 위치는 앱이 [id]로 차종별 문장을 찾아 붙인다. */
    val hint: String,
    val setup: String? = null,
    val essential: Boolean = false,
    val holdMs: Long = 0,
    val available: (VehicleState) -> Boolean = { true },
    val done: (VehicleState) -> Boolean = { false },
    val choice: ChoiceDef? = null,
    /** 화면 속 가상 속도(km/h)까지 보고 판정할 때(S05 감속). 있으면 [done] 대신 쓴다. */
    val doneWithSpeed: ((VehicleState, Float) -> Boolean)? = null,
    /** 이 단계(id)가 끝난 뒤에만 인정한다(합류: 깜빡이 다음에 조향). */
    val after: String? = null,
    /** 화면 속 속도를 그 장소의 평소 속도에 대한 비율(0~1)로 보고 판정할 때(감속: 절반으로). 있으면 [done]·[doneWithSpeed] 대신 쓴다. */
    val doneWithSpeedRatio: ((VehicleState, Float) -> Boolean)? = null,
    /** 회전교차로: 도는 차가 내 진입 지점을 지나간 뒤에만 인정한다. */
    val needsClearRing: Boolean = false,
    /** 수막현상: 물 고인 구간을 다 지나간 뒤에만 인정한다. */
    val needsPuddlePassed: Boolean = false,
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
    // 힌트는 차종 중립으로 "무엇을 할지"만 말한다. 스위치 위치는 차마다 달라서(그랜저·아반떼 CN8은 와이퍼가 왼쪽 레버,
    // 전조등은 왼쪽 아래 버튼, 공조는 화면 안) 앱이 step id로 차종별 문장(assets/knowledge/vehicles)을 붙인다.
    // 위치를 말하는 문장은 여섯 차종 설명서에 모두 맞는 것만 쓴다(docs/research/2026-11-13_switch-locations-by-model.md 4절).
    private const val HEADLIGHT_HINT = "전조등을 켜요."
    private const val HAZARD_HINT = "빨간 삼각형 비상등 버튼을 눌러요."
    private const val SLOW_FIRST = "먼저 브레이크를 나눠서 지그시 밟아 속도를 줄여요."
    private const val GENTLE_BRAKE = "브레이크를 급하게 밟지 말고 나눠서 지그시 밟아"
/** 회전교차로에서 도는 차보다 먼저 들어갔을 때와 힌트에서 하는 말. */
    /** 수막현상: 줄이지 않고 물 고인 곳에 들어갔을 때, 물 위에서 밟거나 꺾었을 때 하는 말. */
    internal const val HYDRO_TOO_FAST = "물 고인 곳에 닿기 전에 미리 속도를 줄여요. 브레이크는 나눠서 밟아요."
    internal const val HYDRO_STEADY = "물 위에서는 급하게 밟거나 꺾지 않아요. 그대로 지나가요."
    internal const val ROUNDABOUT_YIELD_LINE = "회전교차로 안에서 도는 차가 먼저예요. 양보선에서 멈춰서 그 차를 보내요."

    private val lowBeamOn = StepDef(
        id = "low_beam_on", label = "전조등 켜기", hint = HEADLIGHT_HINT, setup = "전조등을 꺼 두세요",
        available = { it.lowBeamOn != null }, done = { it.lowBeamOn == true },
    )

    // 앞이 안 보이는 상황(폭우·김서림·짙은 안개)의 공통 절차: 먼저 브레이크로 속도를 줄이고 그 상황의 조작을 한다.
    // 폭우·안개의 "절반"은 시행규칙 제19조 제2항(가시거리 100m 이내 50% 감속)이고, 김서림은 그 조문 대상이 아니라 수치를 말하지 않는다.
    // 감속 다음의 비상등 단계는 법령 근거가 없는 통상 권고이고 사용자 결정으로 넣었다(TrainingConfig.lowVisibilityHazardStep, docs/plan/safety-sources.md 16번).
    // 브레이크 신호는 밟음/안 밟음뿐이라 세기는 판정하지 않고, 안내 문구로만 "급하게 밟지 말고 나눠서 지그시"를 말한다.
    private fun slowDown(config: TrainingConfig, label: String, hint: String, fraction: Float = config.fogSlowFraction) = StepDef(
        id = "slow_down", label = label, hint = hint,
        available = { it.brakePressed != null },
        // 장소마다 평소 속도가 달라(고속도로 80, 도심 50) 비율로 본다
        doneWithSpeedRatio = { _, ratio -> ratio <= fraction },
    )

    private val hazardConfirm = StepDef(
        id = "hazard_confirm", label = "비상등 켜기", hint = HAZARD_HINT,
        choice = ChoiceDef(
            question = "비상등 켰나요?",
            options = listOf(
                ChoiceOption("네, 켰어요", listOf("네", "응", "예", "켰", "그래")),
                ChoiceOption("아직이요", listOf("아직", "아니", "안 켰", "안켰")),
            ),
            correct = 0,
            wrongLine = "빨간 삼각형 비상등 버튼을 눌러요. 켰으면 '응'이라고 해 주세요.",
            replyKind = ReplyKind.YES_NO,
            penaltyFree = true,
        ),
    )

    private fun sightFirst(config: TrainingConfig, label: String, hint: String, fraction: Float = config.fogSlowFraction): List<StepDef> =
        listOfNotNull(slowDown(config, label, hint, fraction), hazardConfirm.takeIf { config.lowVisibilityHazardStep })

    private fun hazardWords(config: TrainingConfig) =
        if (config.lowVisibilityHazardStep) "급하게 밟지 말고 나눠서 지그시 속도부터 줄이고 비상등을 켜요." else "급하게 밟지 말고 나눠서 지그시 속도부터 줄여요."

    fun all(config: TrainingConfig = TrainingConfig()): List<SituationDef> = listOf(
        SituationDef(
            id = SituationId.HEAVY_RAIN, title = "폭우",
            intro = "비가 쏟아져요. 앞이 안 보여요! $SLOW_FIRST",
            // 출처: 도로교통법 제37조(비 올 때 등화), 시행규칙 제19조(가시거리 100m 이내 50% 감속).
            lesson = "앞이 안 보이면 ${hazardWords(config)} 그다음 와이퍼를 가장 빠르게, 전조등도 켜요.",
            steps = sightFirst(config, "속도 절반으로 줄이기", "$GENTLE_BRAKE 속도를 절반으로 줄여요.") + listOf(
                StepDef(
                    id = "wiper_fast", label = "와이퍼 빠르게", essential = true,
                    hint = "와이퍼를 가장 빠른 단으로 바꿔요.", setup = "와이퍼를 꺼 두세요",
                    available = { it.wiper != null }, done = { (it.wiper?.ordinal ?: -1) >= config.heavyRainMinWiper.ordinal },
                ),
                lowBeamOn,
            ),
            loopSfx = listOf("rain"), onceSfx = listOf("thunder"),
        ),
        SituationDef(
            id = SituationId.FOGGED_WINDSHIELD, title = "앞유리 김서림",
            intro = "터널을 나왔더니 앞유리가 뿌예져요! $SLOW_FIRST",
            lesson = "앞유리가 뿌예지면 ${hazardWords(config)} 그다음 성에 제거, 에어컨, 외기. 이 세 개예요.",
            steps = sightFirst(config, "속도 줄이기", "$GENTLE_BRAKE 속도를 줄여요.", config.mistSlowFraction) + listOf(
                StepDef(
                    id = "defrost_on", label = "앞유리 성에 제거", essential = true,
                    hint = "앞유리 모양 성에 제거를 켜요.", setup = "앞유리 성에 제거를 꺼 두세요",
                    available = { it.defrostFrontOn != null }, done = { it.defrostFrontOn == true },
                ),
                StepDef(
                    id = "ac_on", label = "에어컨 켜기",
                    hint = "에어컨(A/C)을 켜요.", setup = "에어컨을 꺼 두세요",
                    available = { it.acOn != null }, done = { it.acOn == true },
                ),
                StepDef(
                    id = "fresh_air", label = "외기로 바꾸기",
                    hint = "내기순환을 꺼서 바깥 공기가 들어오게 해요.", setup = "내기순환을 켜 두세요",
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
            // 멈춘 뒤 비상등: 법령 근거는 없고 뒤차 추돌을 막기 위한 통상 권고(한국도로공사 2020 "정체 시 비상등 켜기" 캠페인). 사용자 결정으로 넣었다.
            lesson = if (config.lowVisibilityHazardStep) {
                "앞차와 거리를 넉넉히 두면 이 시간을 벌 수 있어요. 급하게 섰거나 고속도로에서 앞이 갑자기 막히면 비상등으로 뒤차에 알려요."
            } else {
                "앞차와 거리를 넉넉히 두면 이 시간을 벌 수 있어요."
            },
            steps = listOfNotNull(
                StepDef(
                    id = "brake", label = "브레이크 밟기", essential = true,
                    hint = "앞차 브레이크등이 보이면 바로 브레이크예요.", setup = "브레이크에서 발을 떼 주세요",
                    available = { it.brakePressed != null }, done = { it.brakePressed == true },
                ),
                hazardConfirm.takeIf { config.lowVisibilityHazardStep },
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
            intro = "안개가 짙어요. 앞이 하얘요. $SLOW_FIRST",
            // 출처: 도로교통법 제37조(등화), 시행규칙 제19조(가시거리 100m 이내 50% 감속). 안개등은 신호를 못 읽어 말로만 안내(실측).
            lesson = "안개가 짙으면 ${hazardWords(config)} 전조등을 켜고, 안개등이 있으면 같이 켜요.",
            steps = sightFirst(config, "속도 절반으로 줄이기", "$GENTLE_BRAKE 속도를 절반으로 줄여요.") + listOf(lowBeamOn.copyEssential()),
            // 상향등 감점 없음: 근거 원문 미확인(명세 5.2).
        ),
        SituationDef(
            id = SituationId.ONCOMING_GLARE, title = "맞은편 차",
            intro = "맞은편에서 차가 와요. 우리 상향등이 켜져 있어요.",
            lesson = "맞은편 차가 보이면 상향등부터 내려요.",
            steps = listOf(
                StepDef(
                    id = "high_beam_off", label = "상향등 끄기", essential = true,
                    hint = "왼쪽 레버를 몸 쪽으로 당기면 상향등이 꺼져요.", setup = "상향등을 켜 두세요",
                    available = { it.highBeamOn != null }, done = { it.highBeamOn == false },
                ),
            ),
        ),
        SituationDef(
            // 비상등은 일반 앱이 읽을 수 없어(실측) 스위치 판정 없이 순서 퀴즈로 한다.
            // 순서는 한국도로공사 「2차사고 예방」 원문: 비상등 → 갓길 등 안전한 곳으로 이동 → (삼각대·불꽃신호기) → 가드레일 밖 대피 → 신고.
            // 트렁크 열기는 국토교통부 2020-09-21 카드뉴스에 있어 한 줄로만 말한다. "비트박스" 기억법은 도로공사 현행 원문에 없어 쓰지 않는다. safety-sources.md 7번.
            id = SituationId.BREAKDOWN, title = "고속도로 고장",
            intro = "차가 멈췄어요. 여기는 고속도로예요!",
            lesson = "비상등을 켜고, 움직일 수 있으면 갓길로 옮기고, 가드레일 밖으로 피한 다음 신고해요. 트렁크를 열어 두면 뒤차가 알아보기 쉬워요.",
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
                    "shoulder", "차 옮기기",
                    question = "그다음엔요? 차는 아직 움직여요.",
                    options = listOf(
                        ChoiceOption("갓길 같은 안전한 곳으로 차 옮기기", listOf("갓길", "옮기", "안전한 곳")),
                        ChoiceOption("그 자리에 서서 차 상태 살피기", listOf("자리", "상태", "살피")),
                        ChoiceOption("차 뒤에서 손 흔들기", listOf("뒤에서", "손", "흔들")),
                    ),
                    wrongLine = "차가 움직이면 갓길 같은 안전한 곳으로 먼저 옮겨요. 차로에 서 있으면 뒤차가 위험해요.",
                ),
                quizStep(
                    "evacuate", "대피",
                    question = "사람은 어디로 갈까요?",
                    options = listOf(
                        ChoiceOption("가드레일 밖 안전한 곳", listOf("가드레일", "밖", "안전")),
                        ChoiceOption("차 바로 앞", listOf("앞")),
                        ChoiceOption("세운 차 옆", listOf("옆")),
                    ),
                    wrongLine = "차 근처는 위험해요. 가드레일 밖으로 먼저 피해요. 차 확인보다 대피가 먼저예요.",
                ),
                quizStep(
                    "report", "신고",
                    question = "마지막으로 할 일은요?",
                    options = listOf(
                        ChoiceOption("112나 119, 한국도로공사 콜센터에 신고", listOf("112", "119", "신고", "도로공사", "콜센터")),
                        ChoiceOption("차 사진 찍어 올리기", listOf("사진", "올리")),
                        ChoiceOption("견인차가 올 때까지 그냥 기다리기", listOf("견인", "그냥")),
                    ),
                    wrongLine = "안전한 곳에서 112나 119, 한국도로공사 콜센터 1588-2504에 신고해요.",
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
                    hint = "내기순환을 꺼서 바깥 공기가 들어오게 해요.", setup = "내기순환을 켜 두세요",
                    available = { it.recirculationOn != null }, done = { it.recirculationOn == false },
                ),
                StepDef(
                    id = "window_open", label = "창문 조금 열기",
                    hint = "운전석 문 팔걸이 스위치로 창문을 조금 열어요.", setup = "운전석 창문을 닫아 두세요",
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
                    id = "lights_off", label = "전조등 끄기", hint = "전조등을 꺼요.", setup = "전조등을 켜 두세요", essential = true,
                    available = { it.lowBeamOn != null }, done = { it.lowBeamOn == false },
                ),
                StepDef(
                    id = "windows_closed", label = "창문 닫기", hint = "창문을 끝까지 닫아요.", setup = "운전석 창문을 조금 열어 두세요",
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
        SituationDef(
            // 출처: 도로교통법 제27조 제1항(보행자가 건너거나 건너려 하면 횡단보도 앞 일시정지), 시행규칙 별표 2(적색 신호 우회전은 정지 후 서행).
            id = SituationId.RIGHT_TURN_CROSSWALK, title = "우회전 횡단보도",
            intro = "우회전할 거예요. 횡단보도에 사람이 있어요!",
            lesson = "우회전할 때 횡단보도에 사람이 건너거나 건너려고 하면 일단 멈춰요. 빨간불이면 사람이 없어도 한 번 멈췄다가 가요.",
            steps = listOf(
                StepDef(
                    id = "full_stop", label = "완전히 멈추기", essential = true,
                    hint = "브레이크를 밟아 완전히 멈춰요.",
                    available = { it.brakePressed != null },
                    doneWithSpeed = { _, v -> v <= 0.5f },
                ),
            ),
        ),
        SituationDef(
            // 출처: 도로교통법 제25조의2 제2항(회전교차로에 진입하려면 서행하거나 일시정지, 이미 진행하는 차에 진로 양보),
            // 제38조 제1항·시행령 별표 2 제7호(진입하려는 때 30미터 전부터 왼쪽 방향지시기), 제8호(진출하려는 때 오른쪽). safety-sources.md 21번.
            id = SituationId.ROUNDABOUT_ENTRY, title = "회전교차로",
            intro = "회전교차로예요. 안에 도는 차가 있어요. 왼쪽 깜빡이를 켜고, 양보선에서 멈춰 그 차를 먼저 보내요.",
            lesson = "회전교차로에서는 안에서 이미 돌고 있는 차가 먼저예요. 들어가기 전에 천천히 가거나 멈춰서 그 차를 보내고, 비면 들어가요. 들어갈 땐 왼쪽, 나갈 땐 오른쪽 깜빡이예요.",
            steps = listOf(
                StepDef(
                    id = "signal_left", label = "왼쪽 깜빡이 켜기",
                    hint = "왼쪽 깜빡이를 켜요.", setup = "깜빡이를 꺼 두세요",
                    available = { it.turnSignal != null }, done = { it.turnSignal == TurnSignal.LEFT },
                ),
                // 양보선 앞에서 거의 멈춘 채로, 도는 차가 내 진입 지점을 지나갈 때까지 기다려야 끝난다
                StepDef(
                    id = "yield_slow", label = "양보선에서 멈춰 도는 차 보내기", essential = true,
                    hint = ROUNDABOUT_YIELD_LINE,
                    available = { it.brakePressed != null },
                    doneWithSpeed = { s, v -> s.brakePressed == true && v <= config.roundaboutYieldKmh },
                    needsClearRing = true,
                ),
                // 가속 페달은 읽을 수 없어 "브레이크를 떼면 진입"으로 본다
                StepDef(
                    id = "enter", label = "비면 진입하기", essential = true, after = "yield_slow",
                    hint = "도는 차가 지나갔어요. 브레이크에서 발을 떼고 들어가요.",
                    available = { it.brakePressed != null }, done = { it.brakePressed == false },
                ),
            ),
        ),
        SituationDef(
            // 출처: 도로교통법 제12조 제1항·제3항(어린이 보호구역 통행속도 시속 30킬로미터 이내로 제한할 수 있음, 운전자는 그 조치를 준수),
            // 제27조 제7항(보호구역 안 신호기 없는 횡단보도 앞에서는 보행자 횡단 여부와 관계없이 일시정지). safety-sources.md 22번.
            // 30은 일률 의무가 아니라 표지로 정하는 상한이라 대사에서 "표지의 제한속도, 보통 시속 30"이라고 말한다.
            id = SituationId.SCHOOL_ZONE, title = "어린이 보호구역",
            intro = "어린이 보호구역이에요. 신호등 없는 횡단보도가 있어요. 속도를 줄이고 일단 멈춰요.",
            lesson = "어린이 보호구역에서는 표지의 제한속도를 지켜요. 보통 시속 30킬로미터예요. 신호등 없는 횡단보도 앞에서는 건너는 사람이 없어도 일단 멈춰요.",
            steps = listOf(
                StepDef(
                    id = "school_slow", label = "시속 30 이하로",
                    hint = "$GENTLE_BRAKE 시속 30 아래로 줄여요.",
                    available = { it.brakePressed != null },
                    doneWithSpeed = { _, v -> v <= config.schoolLimitKmh },
                ),
                StepDef(
                    id = "full_stop", label = "횡단보도 앞에서 멈추기", essential = true,
                    hint = "브레이크를 밟아 횡단보도 앞에서 완전히 멈춰요.",
                    available = { it.brakePressed != null },
                    doneWithSpeed = { _, v -> v <= 0.5f },
                ),
            ),
        ),
        SituationDef(
            // 출처: 도로교통법 제65조(고속도로 진입 시 본선 차 우선), 제38조·시행령 별표 2(진로 변경 신호: 고속도로 100m 이전).
            // 깜빡이 유지 시간은 게임 규칙이고, 대사에서는 "몇 초"라고 말하지 않는다.
            id = SituationId.HIGHWAY_MERGE, title = "고속도로 합류",
            intro = "고속도로로 들어가요. 깜빡이부터 켜고 본선 차를 봐요.",
            lesson = "합류할 땐 깜빡이를 먼저 켜서 뒤차가 볼 시간을 주고, 본선 차가 지나간 뒤에 들어가요.",
            steps = listOf(
                StepDef(
                    id = "signal_left", label = "왼쪽 깜빡이 켜고 유지", essential = true, holdMs = config.mergeSignalHoldMs,
                    hint = "왼쪽 깜빡이를 켜고 그대로 둬요.", setup = "깜빡이를 꺼 두세요",
                    available = { it.turnSignal != null }, done = { it.turnSignal == TurnSignal.LEFT },
                ),
                StepDef(
                    id = "steer_left", label = "핸들 왼쪽으로 조금", after = "signal_left",
                    hint = "깜빡이를 켠 채로 핸들을 왼쪽으로 조금만 돌려요.",
                    available = { it.steeringAngleDeg != null },
                    done = { (it.steeringAngleDeg ?: 0f) <= -config.mergeSteerDeg },
                ),
            ),
            penalties = listOf(
                PenaltyDef("STEER_NO_SIGNAL", "깜빡이를 먼저 켜고 들어가요.") { c -> SteerWithoutSignalDetector(c.mergeSteerDeg) },
            ),
        ),
        SituationDef(
            // "미끄러지기 전에 미리 줄이기"를 익히는 상황. 미끄러지는 순간의 대처는 채점하지 않는다:
            // 짧은 미끄럼 대처 훈련은 실력은 그대로 두고 자신감만 키운다는 연구가 있고, 서 있는 차로는 미끄러지는 감각을 줄 수 없다
            // (docs/research/2026-11-15_feature-gap-review.md (c)절).
            // 출처: 도로교통법 시행규칙 제19조 제2항 제1호(비가 내려 노면이 젖어 있으면 최고속도의 100분의 20을 줄인 속도),
            // 한국도로공사 빗길 요령(브레이크는 여러 번 나누어 밟기, 급출발·급핸들·급브레이크 금지), 도로교통공단·행정안전부(2025-06-27) "급제동하지 않도록".
            // safety-sources.md 19번.
            id = SituationId.HYDROPLANING, title = "수막현상",
            intro = "비가 와요. 앞에 물이 고여 있어요. 닿기 전에 브레이크를 나눠 밟아 속도를 줄여요.",
            lesson = "물이 고인 곳에서는 타이어가 물 위에 떠서 미끄러질 수 있어요. 그래서 닿기 전에 미리 속도를 줄여요. 물 위에서는 급하게 밟거나 꺾지 않아요.",
            successLine = "미리 줄였어요. 실제 도로에서는 훨씬 어려워요. 그래서 미리 줄이는 거예요.",
            steps = listOf(
                StepDef(
                    id = "slow_down", label = "물 고인 곳 전에 속도 줄이기", essential = true,
                    hint = "$GENTLE_BRAKE 물 고인 곳에 닿기 전에 속도를 줄여요.",
                    available = { it.brakePressed != null },
                    doneWithSpeedRatio = { _, r -> r <= config.hydroSlowRatio },
                ),
                StepDef(
                    id = "cross_steady", label = "물 위는 그대로 지나가기", essential = true, after = "slow_down",
                    hint = "물 위에서는 브레이크와 핸들을 크게 쓰지 않고 그대로 지나가요.",
                    available = { it.brakePressed != null }, done = { true },
                    needsPuddlePassed = true,
                ),
            ),
            loopSfx = listOf("rain"),
            noHint = true,
        ),
        SituationDef(
            // 일반 차로 요금소(고속도로에 들어갈 때). 출처: 한국도로공사 「통행료수납」(폐쇄식: 들어갈 때 통행권이 발행되고
            // 나가는 요금소에서 통행권을 읽어 요금을 받는다). safety-sources.md 23번.
            // 멈추기·창문 내리기·올리기는 통행권을 받으려면 해야 하는 조작이고, 일반 차로의 속도·정지 수칙은 공식 문장을 찾지 못해 말하지 않는다.
            id = SituationId.TOLL_BOOTH, title = "일반 차로 요금소",
            intro = "요금소예요. 일반 차로로 들어가요. 부스 옆에 멈추고 운전석 창문을 내려요.",
            lesson = "고속도로에 들어갈 땐 통행권을 받고, 나갈 땐 그 통행권을 내고 요금을 내요. 통행권은 잃어버리지 않게 잘 둬요.",
            steps = listOf(
                StepDef(
                    id = "full_stop", label = "부스 옆에 멈추기", essential = true,
                    hint = "브레이크를 밟아 부스 옆에 멈춰요.",
                    available = { it.brakePressed != null },
                    doneWithSpeed = { _, v -> v <= 0.5f },
                ),
                StepDef(
                    id = "window_open", label = "운전석 창문 내리기", after = "full_stop",
                    hint = "운전석 문 팔걸이 스위치로 창문을 내려요.", setup = "운전석 창문을 닫아 두세요",
                    available = { it.driverWindowOpenPct != null }, done = { (it.driverWindowOpenPct ?: 0) > 0 },
                ),
                StepDef(
                    id = "ticket_confirm", label = "통행권 받기", hint = "통행권을 받아요.",
                    choice = ChoiceDef(
                        question = "통행권을 받아 주세요. 받았나요?",
                        options = listOf(
                            ChoiceOption("네, 받았어요", listOf("네", "응", "예", "받았", "그래")),
                            ChoiceOption("아직이요", listOf("아직", "아니", "안 받", "안받")),
                        ),
                        correct = 0,
                        wrongLine = "창문 밖으로 손을 뻗어 통행권을 받아요. 받았으면 '응'이라고 해 주세요.",
                        replyKind = ReplyKind.YES_NO,
                        penaltyFree = true,
                    ),
                ),
                StepDef(
                    id = "window_close", label = "창문 올리고 출발", after = "ticket_confirm",
                    hint = "창문을 올려요. 차단기가 올라가면 출발해요.",
                    available = { it.driverWindowOpenPct != null }, done = { (it.driverWindowOpenPct ?: 0) == 0 },
                ),
            ),
        ),
        SituationDef(
            // 출처: 정책브리핑 2018-07-20·2019-10-02(들어섰다면 일단 통과, 후진·정지는 큰 사고 위험), 한국도로공사 미납통행료 납부안내.
            id = SituationId.HIPASS_WRONG_LANE, title = "하이패스 차로 오진입",
            intro = "앗, 하이패스 차로로 잘못 들어왔어요. 단말기가 없어요.",
            lesson = "하이패스 차로에 잘못 들어가도 멈추거나 후진하지 않아요. 그대로 지나가고 요금은 나중에 내면 돼요.",
            steps = listOf(
                quizStep(
                    "pass", "대처 고르기", essential = true,
                    question = "어떻게 할까요?",
                    options = listOf(
                        ChoiceOption("멈추지 않고 그대로 지나가요", listOf("그대로", "지나", "통과", "계속")),
                        ChoiceOption("차를 세우고 후진해요", listOf("후진", "뒤로", "세우")),
                        ChoiceOption("차에서 내려 요금소로 가요", listOf("내려", "요금소로", "걸어")),
                    ),
                    wrongLine = "멈추거나 후진하면 큰 사고로 이어질 수 있어요. 그대로 지나가요.",
                ),
                quizStep(
                    "pay", "요금 내는 법 고르기",
                    question = "그럼 요금은 어떻게 할까요?",
                    options = listOf(
                        ChoiceOption("나중에 내요. 나가는 요금소나 누리집에서 정산해요", listOf("나중", "정산", "요금소", "누리집", "홈페이지")),
                        ChoiceOption("안 내도 돼요", listOf("안 내", "안내", "공짜")),
                        ChoiceOption("지금 차를 세우고 내요", listOf("지금", "세우")),
                    ),
                    wrongLine = "요금은 없어지지 않아요. 나가는 요금소에서 말하거나 나중에 정산하면 돼요.",
                ),
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

    private fun StepDef.copyEssential() = StepDef(id, label, hint, setup, true, holdMs, available, done, choice, doneWithSpeed, after, doneWithSpeedRatio)
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

/** 깜빡이를 켜지 않은 채 핸들을 [deg] 넘게 돌리면 감점(합류). */
internal class SteerWithoutSignalDetector(private val deg: Float) : PenaltyDetector {
    override fun update(state: VehicleState, nowMs: Long, sinceActiveMs: Long, doneSteps: Set<String>): Boolean {
        val a = state.steeringAngleDeg ?: return false
        return kotlin.math.abs(a) >= deg && state.turnSignal != TurnSignal.LEFT && "signal_left" !in doneSteps
    }
}
