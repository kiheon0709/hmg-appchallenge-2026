import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import io.moodlab.sideseat.core.*
import io.moodlab.sideseat.ui.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

var simT by mutableFloatStateOf(0f)
val stillBackdrop = StillSceneBackdrop(File("../../app/src/main/assets"))
val homeArt = org.jetbrains.skia.Image.makeFromEncoded(File("../../app/src/main/assets/home/hero.jpg").readBytes()).toComposeImageBitmap()

fun shoot(name: String, w: Int, h: Int, dark: Boolean, t0: Float, t1: Float, content: @Composable () -> Unit) {
    SideSeatUiDebug.forceDark = dark
    val scene = ImageComposeScene(w, h, Density(1.25f)) {
        // 벡터 도로로 보려면 환경 변수 SIDESEAT_VECTOR=1
        androidx.compose.runtime.CompositionLocalProvider(LocalHomeArt provides homeArt) {
            if (System.getenv("SIDESEAT_VECTOR") == "1") content()
            else androidx.compose.runtime.CompositionLocalProvider(io.moodlab.sideseat.ui.scene.LocalSceneBackdrop provides stillBackdrop) { content() }
        }
    }
    var t = t0
    var img = scene.render(0)
    val dt = 1f / 30f
    var n = 0L
    while (t <= t1) {
        simT = t
        n += (dt * 1e9f).toLong()
        img = scene.render(n)
        t += dt
    }
    File("out/$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
    println("wrote $name")
}

@Composable fun Train() { val (v, c) = UiSamples.trainingAt(simT); TrainingScreen(v, c) {} }

/** 상황 [id]가 시작된 뒤 [a]초 시점의 시연 시각. */
fun at(id: SituationId, a: Float): Float = UiSamples.order.indexOf(id) * UiSamples.STAGE + 1.6f + a

fun main(args: Array<String>) {
    File("out").mkdirs()
    val W = 1682; val H = 1210
    val only = args.toSet()
    fun want(n: String) = only.isEmpty() || only.any { n.contains(it) }
    fun train(name: String, id: SituationId, a: Float, dark: Boolean = false, w: Int = W, h: Int = H) {
        if (!want(name)) return
        val t = at(id, a)
        shoot(name, w, h, dark, maxOf(0f, t - 6f), t) { Train() }
    }
    train("t_cruise", SituationId.HEAVY_RAIN, -0.2f)
    train("t_rain_active", SituationId.HEAVY_RAIN, 1.9f)
    train("t_rain_hint", SituationId.HEAVY_RAIN, 2.4f, dark = true)
    train("t_rain_wiping", SituationId.HEAVY_RAIN, 3.6f)
    train("t_rain_success", SituationId.HEAVY_RAIN, 5.0f)
    train("t_fogglass", SituationId.FOGGED_WINDSHIELD, 2.3f)
    train("t_fogglass_clearing", SituationId.FOGGED_WINDSHIELD, 3.8f)
    train("t_stop", SituationId.SUDDEN_STOP, 1.9f)
    train("t_tunnel_enter", SituationId.TUNNEL, 1.0f)
    train("t_tunnel_in", SituationId.TUNNEL, 3.6f)
    train("t_glare", SituationId.ONCOMING_GLARE, 2.4f)
    train("t_densefog", SituationId.DENSE_FOG, 2.6f)
    train("t_drowsy", SituationId.DROWSY, 2.3f)
    train("t_warning", SituationId.WARNING_LIGHT, 2.0f)
    train("t_breakdown", SituationId.BREAKDOWN, 3.4f)
    train("t_accel", SituationId.UNINTENDED_ACCEL, 2.3f)
    train("t_exit", SituationId.BEFORE_EXIT, 1.2f)
    train("t_rightturn", SituationId.RIGHT_TURN_CROSSWALK, 2.0f)
    train("t_merge", SituationId.HIGHWAY_MERGE, 3.2f)
    train("t_hydro", SituationId.HYDROPLANING, 2.0f)
    train("t_hipass", SituationId.HIPASS_WRONG_LANE, 2.0f)
    train("t_portrait_rain", SituationId.HEAVY_RAIN, 2.4f, w = 835, h = 1210)
    if (want("t_chapter_done")) shoot("t_chapter_done", W, H, false, 0f, 3f) {
        val (v, c) = UiSamples.trainingAt(0.5f)
        TrainingScreen(v.copy(phase = TrainingPhase.CHAPTER_DONE, chapterId = "C1", chapterResult = UiSamples.chapterResult), c) {}
    }
    // 점검(11-15) 뒤에 넣은 상태들
    fun state(name: String, w: Int = W, h: Int = H, f: (TrainingView) -> TrainingView) {
        if (!want(name)) return
        val t = at(SituationId.HEAVY_RAIN, 2.4f)
        shoot(name, w, h, false, maxOf(0f, t - 6f), t) { val (v, c) = UiSamples.trainingAt(simT); TrainingScreen(f(v), c) {} }
    }
    state("t_opening") { it.copy(phase = TrainingPhase.CRUISE, situation = null, steps = emptyList(), opening = true, title = "첫 빗길") }
    state("t_versus") { it.copy(phase = TrainingPhase.CRUISE, chapterId = ChapterCatalog.VERSUS, situation = null, steps = emptyList(), title = "교관 대결") }
    state("t_fail_skip") { it.copy(phase = TrainingPhase.FAIL, canSkip = true) }
    val hazardQ = ChoiceView("비상등 켰나요?", listOf("네, 켰어요", "아직이요"))
    state("t_hazard_q") { it.copy(phase = TrainingPhase.ACTIVE, choice = hazardQ) }
    state("t_hazard_q_hint") { it.copy(phase = TrainingPhase.HINT, choice = hazardQ, canSkip = true) }
    state("t_overtime") { it.copy(phase = TrainingPhase.HINT, overtime = true, elapsedMs = it.limitMs + 2_300) }
    state("t_overtime_portrait", w = 835) { it.copy(phase = TrainingPhase.HINT, overtime = true, canSkip = true, elapsedMs = it.limitMs + 2_300) }
    if (want("t_done_unfinished")) shoot("t_done_unfinished", W, H, false, 0f, 3f) {
        val (v, c) = UiSamples.trainingAt(0.5f)
        val res = UiSamples.chapterResult.copy(results = UiSamples.chapterResult.results.map { if (it.id == SituationId.SUDDEN_STOP) it.copy(stars = 0, reactionMs = null, missedStepIds = listOf("brake")) else it })
        TrainingScreen(v.copy(phase = TrainingPhase.CHAPTER_DONE, chapterId = "C1", chapterResult = res), c) {}
    }
    // v21: 소개 → 차종 고르기 → 홈(상태별)
    for (dark in listOf(false, true)) {
        val n = if (dark) "_night" else ""
        for (pg in 0..2) if (want("v21_intro")) shoot("v21_intro_${pg + 1}$n", W, H, dark, 0f, 0.3f) { IntroScreen(pg) {} }
        if (want("v21_vehicle")) shoot("v21_vehicle$n", W, H, dark, 0f, 0.3f) { HomeScreen(UiSamples.home.copy(vehicleChosen = false, vehicleChoiceId = null, vehicleName = null)) {} }
        val base = UiSamples.home.copy(talk = TalkUi())
        fun home(name: String, s: HomeUi) { if (want("v21_home")) shoot("v21_home_$name$n", W, H, dark, 0f, 0.3f) { HomeScreen(s) {} } }
        home("first", base.copy(
            greeting = "세워 둔 차에서 연습해요.", subGreeting = "비·안개·급정거 같은 상황을 이 차의 진짜 스위치로 미리 겪어 봐요.",
            progress = TrainingProgress(), recommendedChapterId = "C1", recommendReason = "처음이라면 여기서 시작해요.", miniGamesUnlocked = false,
        ))
        home("resume", base.copy(
            greeting = "하던 연습이 남아 있어요.", subGreeting = "첫 빗길, 남은 상황부터 이어서 해요.", resumeChapterId = "C1", resumeChapterIds = setOf("C1"),
        ))
        home("route", base.copy(
            greeting = "가는 길, 미리 해 봐요.", subGreeting = "출발 전에, 세워 둔 차에서 한 번 겪어 봐요.", recommendedChapterId = "C5",
            recommendLabel = "가는 길 연습", recommendReason = "가는 길에 요금소가 있어요. 미리 연습해 볼까요?",
        ))
        home("after_drive", base.copy(
            greeting = "잘 다녀왔어요.", subGreeting = "오늘 길에서 나온 것만 1분 연습해 볼까요?", recommendedChapterId = "C1",
            recommendReason = "지난 주행을 보고 골랐어요. 폭우 연습이에요.", talk = TalkUi(answerText = "전조등은 와이퍼를 켤 때 같이 켜면 돼요."),
        ))
    }
    if (want("v21_portrait")) {
        shoot("v21_intro_1_portrait", 835, H, false, 0f, 0.3f) { IntroScreen(0) {} }
        shoot("v21_intro_2_portrait", 835, H, false, 0f, 0.3f) { IntroScreen(1) {} }
        shoot("v21_home_first_portrait", 835, H, false, 0f, 0.3f) { HomeScreen(UiSamples.home.copy(talk = TalkUi(), greeting = "세워 둔 차에서 연습해요.", subGreeting = "비·안개·급정거 같은 상황을 이 차의 진짜 스위치로 미리 겪어 봐요.", progress = TrainingProgress(), recommendedChapterId = "C1", recommendReason = "처음이라면 여기서 시작해요.")) {} }
    }
    if (want("h_home")) shoot("h_home", W, H, false, 0f, 0.3f) { HomeScreen(UiSamples.home) {} }
    if (want("h_home_night")) shoot("h_home_night", W, H, true, 0f, 0.3f) { HomeScreen(UiSamples.home) {} }
    if (want("h_home_portrait")) shoot("h_home_portrait", 835, H, false, 0f, 0.3f) { HomeScreen(UiSamples.home) {} }
    if (want("h_home_portrait_night")) shoot("h_home_portrait_night", 835, H, true, 0f, 0.3f) { HomeScreen(UiSamples.home) {} }
    // 가는 길 추천, 대화 없음, 처음 쓰는 상태(v15 캡처와 같은 조건)
    if (want("h_home_route")) shoot("h_home_route", W, H, false, 0f, 0.3f) {
        HomeScreen(UiSamples.home.copy(
            subGreeting = "3분만 같이 해 볼까요?", progress = TrainingProgress(), recommendedChapterId = "C5", recommendLabel = "가는 길 연습",
            recommendReason = "가는 길에 요금소가 있어요. 미리 연습해 볼까요?", talk = TalkUi(answerText = "전조등을 켜요."),
        )) {}
    }
    if (want("r_result")) shoot("r_result", W, H, false, 0f, 2f) { ResultScreen(UiSamples.chapterResult, UiSamples.progress) {} }
    if (want("r_result_zero")) shoot("r_result_zero", W, H, false, 0f, 2f) {
        ResultScreen(UiSamples.chapterResult.copy(results = UiSamples.chapterResult.results.map { it.copy(stars = 0, reactionMs = null, missedStepIds = listOf("wiper_fast").takeIf { _ -> it.id == SituationId.HEAVY_RAIN }.orEmpty()) }), UiSamples.progress) {}
    }
    if (want("r_review")) shoot("r_review", W, H, false, 0f, 0.3f) { ReviewScreen(UiSamples.review) {} }
    if (want("h_vehicle")) shoot("h_vehicle", W, H, false, 0f, 0.3f) { HomeScreen(UiSamples.home.copy(vehicleChosen = false, vehicleChoiceId = null, vehicleName = null)) {} }
    val car = VehicleState(0, parked = false, wiper = WiperLevel.LOW, lowBeamOn = false, recirculationOn = true, speedLimitKmh = 80, speedKmhEstimate = 93f)
    fun coach(name: String, dark: Boolean, ui: CoachUi, w: Int = W, h: Int = H) { if (want(name)) shoot(name, w, h, dark, 0f, 0.2f) { CoachShot(ui, car) } }
    coach("c_k01", false, UiSamples.coach)
    coach("c_k12_speaking", false, CoachUi(lastLine = "제한속도가 80이에요. 조금 줄여요.", urgent = true, wake = WakeUi.WAITING, talk = TalkUi(speaking = true)))
    coach("c_idle", false, CoachUi(wake = WakeUi.WAITING))
    coach("c_listening", false, CoachUi(lastLine = "비가 와요. 전조등을 켜 주세요.", wake = WakeUi.HEARD, talk = TalkUi(listening = true, partialText = "앞유리가 뿌예")))
    coach("c_k01_night", true, UiSamples.coach)
    coach("c_long_night", true, CoachUi(lastLine = "앞유리에 김이 설 수 있어요. 외기로 바꾸거나 에어컨을 켜 보세요.", wake = WakeUi.YIELDED))
    coach("c_portrait", false, UiSamples.coach, w = 835, h = 1210)
}
