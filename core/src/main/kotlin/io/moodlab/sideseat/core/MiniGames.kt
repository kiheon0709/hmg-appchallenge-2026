package io.moodlab.sideseat.core

import kotlin.random.Random

// M2 깜빡이 모스, M3 창문 버저 퀴즈 (명세 8절). 정차 중에만 연다.

data class TurnSignalGameConfig(
    val startLength: Int = 3,
    val maxRounds: Int = 5,
    /** 순서를 보여 줄 때 한 칸에 머무는 시간. */
    val showStepMs: Long = 900,
    /** 입력 한 번을 기다리는 최대 시간. */
    val inputStepLimitMs: Long = 4_000,
    val roundClearMs: Long = 1_500,
    val random: Random = Random.Default,
)

/** M2. 화면에 나온 좌·우 순서를 방향지시등으로 따라 한다. 라운드마다 한 칸씩 길어진다. */
class DefaultTurnSignalGame(private val config: TurnSignalGameConfig = TurnSignalGameConfig()) : TurnSignalGame {
    private var round = 1
    private var pattern = emptyList<TurnSignal>()
    private var phase = MiniGamePhase.OVER
    private var phaseStartMs = 0L
    private var lastInputMs = 0L
    private var inputCount = 0
    private var score = 0
    private var prevSignal: TurnSignal? = null
    private var lastShown = -1
    private var warnedNoSignal = false
    private val events = mutableListOf<CoachEvent>()

    override fun start(nowMs: Long) {
        round = 1
        score = 0
        warnedNoSignal = false
        events += CoachEvent.Say("깜빡이로 따라 해 봐요. 잘 보세요!")
        newRound(nowMs)
    }

    private fun newRound(nowMs: Long) {
        val length = config.startLength + round - 1
        pattern = List(length) { if (config.random.nextBoolean()) TurnSignal.LEFT else TurnSignal.RIGHT }
        phase = MiniGamePhase.SHOW
        phaseStartMs = nowMs
        inputCount = 0
        lastShown = -1
    }

    override fun tick(turnSignal: TurnSignal?, nowMs: Long): Pair<TurnSignalGameView, List<CoachEvent>> {
        if (turnSignal == null && !warnedNoSignal) {
            warnedNoSignal = true
            events += CoachEvent.Say("깜빡이 신호를 읽을 수 없어요. 이 차에서는 할 수 없어요.")
        }
        when (phase) {
            MiniGamePhase.SHOW -> {
                val idx = ((nowMs - phaseStartMs) / config.showStepMs).toInt()
                if (idx != lastShown && idx < pattern.size) {
                    lastShown = idx
                    events += CoachEvent.Sfx("click")
                }
                if (idx >= pattern.size) {
                    phase = MiniGamePhase.INPUT
                    phaseStartMs = nowMs
                    lastInputMs = nowMs
                    // 보여 주는 동안 켜 둔 레버는 입력으로 치지 않는다.
                    prevSignal = turnSignal
                    events += CoachEvent.Say("이제 따라 해요!")
                }
            }
            MiniGamePhase.INPUT -> onInput(turnSignal, nowMs)
            MiniGamePhase.ROUND_CLEAR -> if (nowMs - phaseStartMs >= config.roundClearMs) {
                round++
                newRound(nowMs)
            }
            MiniGamePhase.OVER, MiniGamePhase.DONE -> Unit
        }
        val out = events.toList()
        events.clear()
        return view(nowMs) to out
    }

    private fun onInput(signal: TurnSignal?, nowMs: Long) {
        val prev = prevSignal
        prevSignal = signal
        val isInput = (signal == TurnSignal.LEFT || signal == TurnSignal.RIGHT) && signal != prev
        if (isInput) {
            lastInputMs = nowMs
            if (signal == pattern[inputCount]) {
                inputCount++
                events += CoachEvent.Sfx("click")
                if (inputCount == pattern.size) clearRound(nowMs)
            } else {
                over("반대쪽이에요.")
            }
            return
        }
        if (nowMs - lastInputMs >= config.inputStepLimitMs) over("시간이 다 됐어요.")
    }

    private fun clearRound(nowMs: Long) {
        score++
        if (round >= config.maxRounds) {
            phase = MiniGamePhase.DONE
            events += CoachEvent.Sfx("success")
            events += CoachEvent.Say("완벽해요! ${score}라운드 다 맞혔어요.")
        } else {
            phase = MiniGamePhase.ROUND_CLEAR
            phaseStartMs = nowMs
            events += CoachEvent.Sfx("success")
            events += CoachEvent.Say("좋아요! 한 칸 늘려 볼게요.")
        }
    }

    private fun over(reason: String) {
        phase = MiniGamePhase.OVER
        events += CoachEvent.Sfx("fail")
        events += CoachEvent.Say("$reason ${score}라운드 깼어요.")
    }

    private fun view(nowMs: Long) = TurnSignalGameView(
        round = round,
        pattern = pattern,
        showingIndex = if (phase == MiniGamePhase.SHOW) lastShown else -1,
        inputCount = inputCount,
        phase = phase,
        score = score,
        inputLimitMs = config.inputStepLimitMs,
        inputElapsedMs = if (phase == MiniGamePhase.INPUT) nowMs - lastInputMs else 0,
    )
}

data class WindowBuzzerConfig(
    /** 창문이 이만큼(%) 열리면 버저. */
    val buzzThresholdPct: Int = 5,
    val answerLimitMs: Long = 10_000,
    val revealMs: Long = 3_000,
    val questions: List<ChoiceDef> = DEFAULT_QUESTIONS,
) {
    companion object {
        /** 훈련 내용에서 낸 문제. 대처법은 docs/plan/safety-sources.md의 출처 안에서만. */
        val DEFAULT_QUESTIONS = listOf(
            ChoiceDef(
                "앞유리 안쪽에 김이 서리면 먼저 누를 버튼은?",
                listOf(
                    ChoiceOption("와이퍼", listOf("와이퍼")),
                    ChoiceOption("앞유리 성에 제거", listOf("성에", "제거", "앞유리")),
                    ChoiceOption("비상등", listOf("비상등")),
                ),
                correct = 1, wrongLine = "안쪽 김은 성에 제거 버튼이에요.",
            ),
            ChoiceDef(
                "안개로 100미터 앞이 안 보이면 속도를 얼마나 줄일까요?",
                listOf(
                    ChoiceOption("조금만", listOf("조금")),
                    ChoiceOption("절반으로", listOf("절반", "반")),
                    ChoiceOption("그대로", listOf("그대로")),
                ),
                correct = 1, wrongLine = "최고속도의 절반으로 줄여야 해요.",
            ),
            ChoiceDef(
                "고속도로에서 차가 멈추면 사람은 어디로 갈까요?",
                listOf(
                    ChoiceOption("차 안", listOf("안")),
                    ChoiceOption("차 바로 뒤", listOf("뒤")),
                    ChoiceOption("가드레일 밖", listOf("가드레일", "밖")),
                ),
                correct = 2, wrongLine = "가드레일 밖이 안전해요.",
            ),
            ChoiceDef(
                "타이어 공기압 경고등이 켜지면 먼저 할 일은?",
                listOf(
                    ChoiceOption("경적 울리기", listOf("경적", "빵")),
                    ChoiceOption("바로 속도 줄이기", listOf("속도", "줄이")),
                    ChoiceOption("창문 열기", listOf("창문")),
                ),
                correct = 1, wrongLine = "바로 속도를 줄이고 점검받아요.",
            ),
            ChoiceDef(
                "맞은편에서 차가 오면 먼저 할 일은?",
                listOf(
                    ChoiceOption("상향등 끄기", listOf("상향", "끄")),
                    ChoiceOption("경적 울리기", listOf("경적", "빵")),
                    ChoiceOption("비상등 켜기", listOf("비상등")),
                ),
                correct = 0, wrongLine = "상향등을 내려야 맞은편 운전자가 눈부시지 않아요.",
            ),
        )
    }
}

/** M3. 선배가 문제를 내면 자기 자리 창문 스위치를 먼저 누른 사람이 답한다. */
class DefaultWindowBuzzerGame(private val config: WindowBuzzerConfig = WindowBuzzerConfig()) : WindowBuzzerGame {
    private var index = 0
    private var phase = BuzzerPhase.DONE
    private var phaseStartMs = 0L
    private var buzzed: Seat? = null
    private var lastCorrect: Boolean? = null
    private val scores = linkedMapOf<Seat, Int>()
    /** 문제를 낸 뒤 좌석별로 본 가장 닫힌 값. 닫았다가 열어도 버저가 된다. */
    private val lowest = mutableMapOf<Seat, Int>()
    private val events = mutableListOf<CoachEvent>()

    override fun start(nowMs: Long) {
        index = 0
        scores.clear()
        events += CoachEvent.Say("창문 버저 퀴즈예요. 답을 알면 자기 자리 창문 스위치를 눌러요!")
        ask(nowMs)
    }

    private fun ask(nowMs: Long) {
        phase = BuzzerPhase.ASK
        phaseStartMs = nowMs
        buzzed = null
        lastCorrect = null
        lowest.clear()
        val q = config.questions[index]
        val options = q.options.mapIndexed { i, o -> "${i + 1}번, ${o.text}." }.joinToString(" ")
        events += CoachEvent.Say("${index + 1}번 문제. ${q.question} $options")
    }

    override fun tick(windowOpenPct: Map<Seat, Int>, nowMs: Long): Pair<WindowBuzzerView, List<CoachEvent>> {
        when (phase) {
            BuzzerPhase.ASK -> {
                windowOpenPct.forEach { (seat, pct) -> lowest[seat] = minOf(lowest[seat] ?: pct, pct) }
                val first = Seat.entries.firstOrNull { seat ->
                    val pct = windowOpenPct[seat] ?: return@firstOrNull false
                    pct - (lowest[seat] ?: pct) >= config.buzzThresholdPct
                }
                if (first != null) {
                    buzzed = first
                    phase = BuzzerPhase.BUZZED
                    phaseStartMs = nowMs
                    events += CoachEvent.Sfx("buzzer")
                    events += CoachEvent.Say("${seatName(first)} 먼저! 답은요?", priority = 1, expectReply = ReplyKind.CHOICE)
                }
            }
            BuzzerPhase.BUZZED -> if (nowMs - phaseStartMs >= config.answerLimitMs) judge(false, nowMs)
            BuzzerPhase.REVEAL -> if (nowMs - phaseStartMs >= config.revealMs) next(nowMs)
            BuzzerPhase.DONE -> Unit
        }
        val out = events.toList()
        events.clear()
        return view() to out
    }

    override fun answer(choiceOrText: String, nowMs: Long) {
        if (phase != BuzzerPhase.BUZZED) return
        val q = config.questions[index]
        val idx = q.match(choiceOrText)
        if (idx == null) {
            events += CoachEvent.Say("다시 말해 줄래요? 번호로 말해도 돼요.", expectReply = ReplyKind.CHOICE)
            return
        }
        judge(idx == q.correct, nowMs)
    }

    private fun judge(correct: Boolean, nowMs: Long) {
        val q = config.questions[index]
        val seat = buzzed
        lastCorrect = correct
        phase = BuzzerPhase.REVEAL
        phaseStartMs = nowMs
        if (correct && seat != null) {
            scores[seat] = (scores[seat] ?: 0) + 1
            events += CoachEvent.Sfx("success")
            events += CoachEvent.Say("정답! ${q.options[q.correct].text}.")
        } else {
            events += CoachEvent.Sfx("fail")
            events += CoachEvent.Say("아쉬워요. ${q.wrongLine}")
        }
    }

    private fun next(nowMs: Long) {
        index++
        if (index < config.questions.size) {
            ask(nowMs)
            return
        }
        phase = BuzzerPhase.DONE
        val best = scores.maxByOrNull { it.value }
        val tie = best != null && scores.count { it.value == best.value } > 1
        events += CoachEvent.Sfx("success")
        events += CoachEvent.Say(
            when {
                best == null -> "다 같이 다음에 다시 해 봐요."
                tie -> "동점이에요! ${best.value}문제씩 맞혔어요."
                else -> "${seatName(best.key)} 우승! ${best.value}문제 맞혔어요."
            },
        )
    }

    private fun view(): WindowBuzzerView {
        val q = config.questions[index.coerceAtMost(config.questions.lastIndex)]
        return WindowBuzzerView(
            questionIndex = index.coerceAtMost(config.questions.lastIndex),
            questionCount = config.questions.size,
            question = q.question,
            options = q.options.map { it.text },
            phase = phase,
            buzzedSeat = buzzed,
            lastCorrect = lastCorrect,
            scores = scores.toMap(),
        )
    }

    companion object {
        fun seatName(seat: Seat) = when (seat) {
            Seat.DRIVER -> "운전석"
            Seat.PASSENGER -> "조수석"
            Seat.REAR_LEFT -> "뒷좌석 왼쪽"
            Seat.REAR_CENTER -> "뒷좌석 가운데"
            Seat.REAR_RIGHT -> "뒷좌석 오른쪽"
        }
    }
}
