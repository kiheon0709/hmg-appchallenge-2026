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
    val greeting: String = "안녕하세요. 옆자리예요.",
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
    /** 이어 할 수 있는 챕터(P를 벗어나 끊긴 훈련). */
    val resumeChapterId: String? = null,
    /** 정차 중이라 훈련을 시작할 수 있다. */
    val canTrain: Boolean = true,
    /** 미니게임이 열렸다(챕터를 하나라도 깸). */
    val miniGamesUnlocked: Boolean = false,
    val talk: TalkUi = TalkUi(),
)

/** 코치(주행 중). 화면은 한 줄과 버튼 하나뿐이다. */
data class CoachUi(
    /** 마지막으로 한 말. 한글 24자 이내로 잘라서 준다(넘치면 화면이 줄인다). */
    val lastLine: String? = null,
    /** 안전 규칙으로 급하게 한 말이면 true(색만 바뀐다). */
    val urgent: Boolean = false,
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

/** 화면에서 일어난 일. */
sealed interface UiAction {
    data class StartChapter(val chapterId: String) : UiAction
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
}
