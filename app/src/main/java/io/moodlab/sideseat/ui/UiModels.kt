package io.moodlab.sideseat.ui

import io.moodlab.sideseat.core.ChapterInfo
import io.moodlab.sideseat.core.DriveSummary
import io.moodlab.sideseat.core.SituationId
import io.moodlab.sideseat.core.TrainingProgress

// 화면이 받는 상태와 화면이 내는 동작. 통합 세션(AppViewModel)이 채우고 처리한다.
// 화면은 이 값만 보고 그리며 ViewModel·SDK를 직접 만지지 않는다.

/** 음성 대화 상태. 홈·코치·리뷰가 같이 쓴다. */
data class TalkUi(
    /** 마이크가 열려 있다. */
    val listening: Boolean = false,
    /** 듣는 중의 중간 인식 결과. */
    val partialText: String? = null,
    /** 선배가 말하는 중. */
    val speaking: Boolean = false,
    /** 답을 준비하는 중(LLM 대기). */
    val thinking: Boolean = false,
    /** 선배가 방금 한 말(자막). */
    val answerText: String? = null,
)

/** 홈(정차). */
data class HomeUi(
    /** 선배 인사. 예: "이 차는 처음이시네요." */
    val greeting: String = "안녕하세요. 타비예요.",
    /** 인사 아래 한 줄. 예: "3분만 같이 해 볼까요?" */
    val subGreeting: String = "세워 둔 지금, 진짜 스위치로 연습해 봐요.",
    val chapters: List<ChapterInfo> = emptyList(),
    val progress: TrainingProgress = TrainingProgress(),
    /** "이 차 면허"를 나눠 세는 차종 키. */
    val vehicleModelKey: String? = null,
    /** 화면에 보여 줄 차 이름. 모르면 null. */
    val vehicleName: String? = null,
    /** 오늘의 추천 챕터 id와 이유 한 줄. */
    val recommendedChapterId: String? = null,
    val recommendReason: String? = null,
    /** 추천이 내비 경로에서 나온 것이면 그 상황. 추천 버튼은 챕터를 이 상황부터 시작한다. */
    val recommendedSituation: io.moodlab.sideseat.core.SituationId? = null,
    /** 추천 카드 머리말. 내비 경로에서 고른 추천이면 "가는 길 연습". */
    val recommendLabel: String = "오늘의 추천",
    /** 이어 할 수 있는 챕터(P를 벗어나 끊긴 훈련). */
    val resumeChapterId: String? = null,
    /** 이어 할 수 있는 챕터 전부. 챕터마다 따로 기억한다. */
    val resumeChapterIds: Set<String> = emptySet(),
    /** 정차 중이라 훈련을 시작할 수 있다. */
    val canTrain: Boolean = true,
    /** 미니게임이 열렸다(챕터를 하나라도 깸). */
    val miniGamesUnlocked: Boolean = false,
    /** 사용자가 고를 수 있는 차종. 비어 있으면 차종 고르기를 보여 주지 않는다. */
    val vehicleChoices: List<VehicleChoice> = emptyList(),
    /** 고른 차종의 아이디. "그 밖의 차"를 골랐거나 아직 안 골랐으면 null. */
    val vehicleChoiceId: String? = null,
    /** 차종을 한 번이라도 골랐다. false면 홈에 들어올 때 고르는 판이 먼저 뜬다. */
    val vehicleChosen: Boolean = true,
    /** 첫 실행 소개를 봤다. false면 홈에 들어올 때 소개가 먼저 뜬다(그다음이 차종 고르기). */
    val introSeen: Boolean = true,
    val talk: TalkUi = TalkUi(),
)

/** 고를 수 있는 차종 하나(스위치 위치 안내가 조사된 차). */
data class VehicleChoice(val id: String, val name: String)

/** 주행 중 호출어 대기 상태(화면 표시용). 음성 모듈의 WakeState를 통합 세션이 이것으로 옮긴다. */
enum class WakeUi {
    /** 호출어 기능이 꺼져 있다. 안내를 보여 주지 않는다. */
    OFF,
    /** 호출어를 기다리는 중(WakeState.LISTENING). */
    WAITING,
    /** 방금 답했고, 호출어 없이 이어서 말해도 듣는다(WakeState.FOLLOW_UP). */
    FOLLOW_UP,
    /** 호출어를 들었고 질문을 받는 중(WakeState.HANDLING). */
    HEARD,
    /** 차량 기본 비서(Gleo)를 부른 말이라 물러나 있다(WakeState.BACKED_OFF). */
    YIELDED,
}

/** 코치(주행 중). 화면은 한 줄, 호출 안내 한 줄, 버튼 하나뿐이다. */
data class CoachUi(
    /** 마지막으로 한 말. 한글 24자 이내로 잘라서 준다(넘치면 화면이 줄인다). */
    val lastLine: String? = null,
    /** 안전 규칙으로 바로 한 말(K01 등). 색은 바뀌지 않는다. */
    val urgent: Boolean = false,
    /** 위험 단계의 말("지금 안전한 곳에 세워야 해요" 같은 빨간 경고등 안내). 이때만 문장이 위험색이 된다. */
    val danger: Boolean = false,
    /** 호출어 대기 상태. */
    val wake: WakeUi = WakeUi.OFF,
    val talk: TalkUi = TalkUi(),
)

/** 리뷰(도착 후). */
data class ReviewUi(
    val summary: DriveSummary,
    /** 지난 주행. 있으면 "지난번보다"로 비교한다. */
    val previous: DriveSummary? = null,
    /** 선배 총평 자막. 문장이 도착하는 대로 뒤에 붙인다. */
    val reviewLines: List<String> = emptyList(),
    /** 총평이 아직 오는 중. */
    val reviewStreaming: Boolean = false,
    /** "지금 1분만 연습할까요?"로 권할 상황과 그 이름. */
    val suggestedTitle: String? = null,
    val talk: TalkUi = TalkUi(),
)

/** 디버그 패널의 호출어 직접 불러 보기 시험. [lines]는 한 번 부를 때마다 한 줄, [summary]는 "깨어남 a/b" 꼴. */
data class WakeTestUi(val running: Boolean = false, val lines: List<String> = emptyList(), val summary: String = "")

/** 화면에서 일어난 일. */
sealed interface UiAction {
    /** [from]이 있으면 그 상황부터 시작한다(가는 길 추천). */
    data class StartChapter(val chapterId: String, val from: io.moodlab.sideseat.core.SituationId? = null) : UiAction
    data object StartSurprise : UiAction
    /** 교관 대결을 연다(상황 고르는 판이 뜬다). */
    data object StartVersus : UiAction
    /** 교관 대결에서 상황을 던진다. */
    data class Inject(val situation: SituationId) : UiAction
    /** 리뷰에서 권한 상황 하나만 연습한다. */
    data class StartSituation(val situation: SituationId) : UiAction
    /** 훈련 중 선택지 답(S07·S08). */
    data class Answer(val text: String) : UiAction
    data object AbortTraining : UiAction
    /** 다시 하는 상황을 접고 다음 상황으로 넘어간다. */
    data object SkipSituation : UiAction
    /** 앱이 가려졌다(화면이 보이지 않는다). 훈련·미니게임을 그 자리에서 멈춘다. */
    data object Background : UiAction
    /** 앱이 다시 보인다. 멈췄던 훈련·미니게임을 이어 간다. */
    data object Foreground : UiAction
    /** 챕터가 끝난 뒤 결과 화면으로. */
    data object ShowResult : UiAction
    data object GoHome : UiAction
    data object OpenSafeDial : UiAction
    data object OpenTurnSignalGame : UiAction
    data object OpenWindowBuzzer : UiAction
    data object CloseMiniGame : UiAction
    /** 큰 "질문하기" 버튼. 마이크를 연다. */
    data object AskQuestion : UiAction
    /** 듣기·말하기를 멈춘다. */
    data object StopTalking : UiAction
    data object DismissReview : UiAction
    data object ToggleDebugPanel : UiAction
    /** 이 차의 차종을 고른다. null이면 "그 밖의 차"(차종을 모르는 것으로 두고 공통 안내를 쓴다). */
    data class ChooseVehicle(val id: String?) : UiAction
    /** 첫 실행 소개를 끝까지 봤거나 건너뛰었다. */
    data object IntroDone : UiAction
}
