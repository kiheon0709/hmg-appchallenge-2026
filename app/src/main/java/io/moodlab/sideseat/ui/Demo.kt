package io.moodlab.sideseat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import io.moodlab.sideseat.core.ChapterCatalog
import io.moodlab.sideseat.core.ChapterInfo
import io.moodlab.sideseat.core.ChapterResult
import io.moodlab.sideseat.core.ChoiceView
import io.moodlab.sideseat.core.CoachUtterance
import io.moodlab.sideseat.core.DriveSummary
import io.moodlab.sideseat.core.SceneParams
import io.moodlab.sideseat.core.SituationId
import io.moodlab.sideseat.core.SituationResult
import io.moodlab.sideseat.core.StepView
import io.moodlab.sideseat.core.TrainingPhase
import io.moodlab.sideseat.core.TrainingProgress
import io.moodlab.sideseat.core.TrainingView
import io.moodlab.sideseat.core.VehicleLicense
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.core.WiperLevel

/**
 * 엔진·차량 없이 화면만 확인하는 가짜 데이터와 자동 시연.
 * [TrainingDemo]는 모든 상황을 차례로 돌며 "상황 발생 → 조작 → 성공"을 흉내 낸다(디버그·미리보기 전용).
 */
object UiSamples {
    private const val CRUISE = 1.6f
    private const val ACTIVE = 4.2f
    private const val REACT = 2.6f
    private const val SUCCESS = 1.9f
    const val STAGE = CRUISE + ACTIVE + SUCCESS

    val order: List<SituationId> = listOf(
        SituationId.HEAVY_RAIN, SituationId.FOGGED_WINDSHIELD, SituationId.SUDDEN_STOP, SituationId.TUNNEL,
        SituationId.ONCOMING_GLARE, SituationId.DENSE_FOG, SituationId.DROWSY, SituationId.WARNING_LIGHT,
        SituationId.BREAKDOWN, SituationId.UNINTENDED_ACCEL, SituationId.WIPER_DEBRIS, SituationId.BEFORE_EXIT,
    )

    private fun ramp(t: Float, dur: Float) = (t / dur).coerceIn(0f, 1f)

    /** 시연 시각 [tSec]의 훈련 화면 상태와 차량 상태. */
    fun trainingAt(tSec: Float): Pair<TrainingView, VehicleState> {
        val stage = (tSec / STAGE).toInt()
        val id = order[stage % order.size]
        val t = tSec - stage * STAGE
        val a = t - CRUISE // 상황이 터진 뒤 지난 시간
        val reacted = a >= REACT
        val r = a - REACT
        val phase = when {
            a < 0f -> TrainingPhase.CRUISE
            a < ACTIVE * 0.5f || reacted && a < ACTIVE -> TrainingPhase.ACTIVE
            a < ACTIVE -> TrainingPhase.HINT
            else -> TrainingPhase.SUCCESS
        }
        val live = a in 0f..ACTIVE
        val alert = if (live) (if (reacted) 0.4f else 1f) else 0f
        var scene = SceneParams(alert = alert)
        var car = VehicleState(timestampMs = (tSec * 1000).toLong(), parked = true, lowBeamOn = true, wiper = WiperLevel.OFF)
        var speed = 100f
        var choice: ChoiceView? = null
        if (a >= 0f) when (id) {
            SituationId.HEAVY_RAIN -> {
                scene = scene.copy(rain = if (phase == TrainingPhase.SUCCESS) 0.5f else 1f)
                car = car.copy(lowBeamOn = reacted, wiper = if (reacted) WiperLevel.HIGH else WiperLevel.OFF)
            }
            SituationId.FOGGED_WINDSHIELD -> {
                scene = scene.copy(windshieldFog = if (reacted) 1f - ramp(r, 2.6f) else ramp(a, 1.4f))
                car = car.copy(defrostFrontOn = reacted, acOn = reacted, recirculationOn = !reacted)
            }
            SituationId.SUDDEN_STOP -> {
                val hit = a >= 1.2f
                scene = scene.copy(leadCarDistance = if (hit) 0.5f - 0.36f * ramp(a - 1.2f, 0.9f) else 0.5f, leadCarBraking = hit, alert = if (hit && live) 1f else 0f)
                car = car.copy(brakePressed = a >= 1.9f && live)
                if (a >= 1.9f) speed = 100f * (1f - ramp(a - 1.9f, 1.8f))
            }
            SituationId.TUNNEL -> { scene = scene.copy(night = false, tunnel = true); car = car.copy(lowBeamOn = reacted) }
            SituationId.ONCOMING_GLARE -> {
                scene = scene.copy(oncomingGlare = if (phase == TrainingPhase.SUCCESS) 0f else ramp(a, 3.4f))
                car = car.copy(highBeamOn = !reacted)
            }
            SituationId.DENSE_FOG -> { scene = scene.copy(fogOutside = ramp(a, 1.2f)); car = car.copy(frontFogOn = reacted); speed = 60f }
            SituationId.DROWSY -> {
                scene = scene.copy(drowsyBlur = if (reacted) 0.85f * (1f - ramp(r, 1.2f)) else 0.85f * ramp(a, 2.2f))
                car = car.copy(recirculationOn = !reacted, driverWindowOpenPct = if (reacted) 20 else 0)
            }
            SituationId.WARNING_LIGHT -> {
                scene = scene.copy(warningLight = "TPMS")
                if (live) choice = ChoiceView("이 경고등은 무슨 뜻일까요?", listOf("타이어 공기압이 낮아요", "엔진 오일이 부족해요", "브레이크가 닳았어요"))
            }
            SituationId.BREAKDOWN -> {
                speed = 100f * (1f - ramp(a, 2f))
                car = car.copy(hazardOn = reacted)
                if (reacted && live) choice = ChoiceView("비상등을 켰어요. 다음엔 어떻게 할까요?", listOf("트렁크를 열고 가드레일 밖으로 피해요", "차 안에서 기다려요", "차 뒤에 서서 손을 흔들어요"))
            }
            SituationId.UNINTENDED_ACCEL -> {
                speed = if (reacted) 170f * (1f - ramp(r, 2.5f)) else 100f + 70f * ramp(a, 2.2f)
                car = car.copy(brakePressed = reacted && live, parkingBrakeOn = reacted && r > 0.8f)
            }
            SituationId.WIPER_DEBRIS -> {
                scene = scene.copy(debris = if (reacted) 1f - ramp(r - 0.5f, 0.8f) else ramp(a, 0.25f))
                car = car.copy(hazardOn = a > 1.4f && live, wiper = if (reacted && live) WiperLevel.LOW else WiperLevel.OFF)
                speed = 70f
            }
            SituationId.BEFORE_EXIT -> {
                scene = scene.copy(parkingLot = true, alert = 0f)
                car = car.copy(lowBeamOn = !reacted, parkingBrakeOn = a > 1.5f, driverWindowOpenPct = if (a > 2f) 0 else 30)
                speed = 0f
            }
        }
        val defSteps = SituationTexts.all.firstOrNull { it.id == id }?.steps.orEmpty()
        val steps = defSteps.mapIndexed { i, s ->
            StepView(s.id, s.label, a >= 0f && (if (defSteps.size == 1) reacted else a >= REACT - (defSteps.size - 1 - i) * 0.7f))
        }
        val result = if (phase == TrainingPhase.SUCCESS) SituationResult(id, 3 - stage % 2, (REACT * 1000).toLong(), emptyList(), emptyList()) else null
        val limit = if (id == SituationId.SUDDEN_STOP) 1500L else if (id == SituationId.BEFORE_EXIT) 0L else 8000L
        val view = TrainingView(
            phase = phase, chapterId = "C${stage % order.size / 3 + 1}", situation = if (a >= 0f) id else null,
            title = if (a >= 0f) SituationTexts.title(id) else "", steps = if (a >= 0f) steps else emptyList(),
            elapsedMs = (a.coerceIn(0f, REACT) * 1000).toLong(), limitMs = limit, virtualSpeedKmh = speed, scene = scene,
            lastResult = result, choice = choice,
        )
        return view to car
    }

    val chapters: List<ChapterInfo> = ChapterCatalog.chapters.map { ChapterInfo(it.id, it.title, it.story, it.situations, it.estMinutes, locked = false) }

    val progress = TrainingProgress(
        bestStars = mapOf("HEAVY_RAIN" to 3, "FOGGED_WINDSHIELD" to 2, "SUDDEN_STOP" to 2),
        bestReactionMs = mapOf("HEAVY_RAIN" to 3200L, "FOGGED_WINDSHIELD" to 6100L, "SUDDEN_STOP" to 840L),
        chapterBestStars = mapOf("C1" to 7),
        licenses = mapOf(TrainingProgress.UNKNOWN_VEHICLE to VehicleLicense(setOf("C1"))),
        surpriseBest = 4, totalStarsEarned = 12,
    )

    val home = HomeUi(
        greeting = "이 차는 처음이시네요.", subGreeting = "3분만 같이 해 볼까요? 진짜 스위치로 연습해요.",
        chapters = chapters, progress = progress, recommendedChapterId = "C2", recommendReason = "지난번에 전조등을 늦게 켰어요 · 약 4분",
        miniGamesUnlocked = true,
        talk = TalkUi(answerText = "앞유리가 뿌예지면 공조 패널의 앞유리 모양 버튼을 눌러요. 에어컨을 같이 켜면 더 빨리 걷혀요."),
    )

    val chapterResult = ChapterResult(
        "C1",
        listOf(
            SituationResult(SituationId.HEAVY_RAIN, 3, 3200, emptyList(), emptyList()),
            SituationResult(SituationId.FOGGED_WINDSHIELD, 1, 9400, listOf("fresh_air"), listOf("WIPER_ONLY")),
            SituationResult(SituationId.SUDDEN_STOP, 2, 920, emptyList(), emptyList()),
        ),
        6,
    )

    val summary = DriveSummary(
        durationMin = 42, rainMin = 18, nightMin = 25,
        utterances = listOf(
            CoachUtterance("K01", "연습했던 거예요. 비가 와요. 전조등을 켜 주세요.", urgent = true),
            CoachUtterance("K03", "앞유리에 김이 설 수 있어요. 외기로 바꾸거나 에어컨을 켜 보세요.", urgent = false),
        ),
        observations = mapOf("NO_SIGNAL_TURN" to 1),
        suggestedSituation = SituationId.FOGGED_WINDSHIELD,
        ruleBasedReview = listOf(
            "비가 오자마자 와이퍼를 켠 건 좋았어요.",
            "전조등은 제가 말한 뒤에 켰어요. 와이퍼를 켤 때 같이 켜면 돼요.",
            "다음에 세우면 김서림 대처를 1분만 연습해 봐요.",
        ),
    )

    val review = ReviewUi(
        summary = summary, previous = summary.copy(durationMin = 30, rainMin = 0, nightMin = 30, utterances = summary.utterances + summary.utterances, observations = mapOf("NO_SIGNAL_TURN" to 3)),
        talk = TalkUi(),
    )

    val coach = CoachUi(lastLine = "비가 와요. 전조등을 켜 주세요.", urgent = true, wake = WakeUi.WAITING)
}

/** 모든 상황을 자동으로 도는 훈련 화면 시연. 엔진 없이 화면만 본다. */
@Composable
fun TrainingDemo(onAction: (UiAction) -> Unit = {}) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var start = 0L
        while (true) withFrameNanos { now -> if (start == 0L) start = now; t = (now - start) / 1e9f }
    }
    val (view, car) = UiSamples.trainingAt(t)
    TrainingScreen(view, car, onAction)
}
