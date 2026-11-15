package io.moodlab.sideseat.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.moodlab.sideseat.core.ChapterCatalog
import io.moodlab.sideseat.core.SituationCatalog
import io.moodlab.sideseat.core.SituationDef
import io.moodlab.sideseat.core.SituationId

/**
 * 이 차의 스위치 위치 문장을 찾는 함수. 화면은 음성 모듈을 직접 만지지 않으므로 통합 세션이
 * `CompositionLocalProvider(LocalSwitchLocation provides { id -> voiceModule.switchLocation(id) })`로 넣어 준다.
 * 인자는 `sw_wiper` 같은 스위치 아이디이고, 모르면 null을 돌려준다. 넣지 않으면 위치 문장 없이 조작 문장만 보인다.
 */
val LocalSwitchLocation = staticCompositionLocalOf<(String) -> String?> { { null } }

/** 상황·단계의 이름과 조작 안내. 문장은 core의 상황 정의(출처가 확인된 것)에서만 가져온다. */
internal object SituationTexts {
    private val defs: Map<SituationId, SituationDef> by lazy { SituationCatalog.all().associateBy { it.id } }

    val all: List<SituationDef> get() = defs.values.toList()

    /** 교관 대결 판에 올릴 상황: 꺼 둔 상황은 뺀다. */
    val playable: List<SituationDef> by lazy { all.filter { it.id !in io.moodlab.sideseat.core.TrainingConfig().disabled } }

    /** 좁은 버튼에 넣는 짧은 이름. 잘려서 "고속도로 …"가 둘이 되지 않게 한다. */
    fun shortTitle(id: SituationId): String = when (id) {
        SituationId.FOGGED_WINDSHIELD -> "김서림"
        SituationId.SUDDEN_STOP -> "급정거"
        SituationId.TUNNEL -> "터널"
        SituationId.DENSE_FOG -> "안개"
        SituationId.BREAKDOWN -> "고장 정차"
        SituationId.WARNING_LIGHT -> "경고등"
        SituationId.UNINTENDED_ACCEL -> "급가속"
        SituationId.RIGHT_TURN_CROSSWALK -> "우회전"
        SituationId.HIGHWAY_MERGE -> "합류"
        SituationId.SCHOOL_ZONE -> "보호구역"
        SituationId.TOLL_BOOTH -> "요금소"
        SituationId.HIPASS_WRONG_LANE -> "하이패스"
        else -> title(id)
    }

    fun title(id: SituationId): String = defs[id]?.title ?: id.name

    fun lesson(id: SituationId): String? = defs[id]?.lesson

    fun stepLabel(id: SituationId, stepId: String): String = defs[id]?.steps?.firstOrNull { it.id == stepId }?.label ?: stepId

    /** 그 조작의 스위치가 어디 있는지(차종 공통 표현). */
    fun hint(id: SituationId?, stepId: String): String? = defs[id]?.steps?.firstOrNull { it.id == stepId }?.hint

    /** 훈련 단계 아이디 → 스위치 위치 아이디. 스위치가 없는 단계(브레이크, 선택지)는 null. */
    fun switchId(stepId: String): String? = when (stepId) {
        "wiper_fast", "wiper_on" -> "sw_wiper"
        "low_beam_on", "lights_off" -> "sw_headlight"
        "high_beam_off" -> "sw_highbeam"
        "fog_on" -> "sw_fog"
        "hazard_on", "hazard_confirm" -> "sw_hazard"
        "defrost_on" -> "sw_defrost"
        "ac_on" -> "sw_ac"
        "fresh_air" -> "sw_recirc"
        "window_open", "windows_closed" -> "sw_window"
        "parking_brake", "epb_pull" -> "sw_epb"
        else -> null
    }

    fun penaltyLine(id: SituationId, penaltyId: String): String =
        defs[id]?.penalties?.firstOrNull { it.id == penaltyId }?.line ?: "감점 조작이 있었어요."

    fun chapterTitle(chapterId: String?): String = when (chapterId) {
        null -> ""
        ChapterCatalog.SURPRISE -> "돌발 모드"
        ChapterCatalog.VERSUS -> "교관 대결"
        else -> ChapterCatalog.chapters.firstOrNull { it.id == chapterId }?.title ?: chapterId
    }

    fun seconds(ms: Long): String = "%.1f초".format(ms / 1000f)
}
