package io.moodlab.sideseat.ui

import io.moodlab.sideseat.core.ChapterCatalog
import io.moodlab.sideseat.core.SituationCatalog
import io.moodlab.sideseat.core.SituationDef
import io.moodlab.sideseat.core.SituationId

/** 상황·단계의 이름과 스위치 위치 안내. 문장은 core의 상황 정의(출처가 확인된 것)에서만 가져온다. */
internal object SituationTexts {
    private val defs: Map<SituationId, SituationDef> by lazy { SituationCatalog.all().associateBy { it.id } }

    val all: List<SituationDef> get() = defs.values.toList()

    fun title(id: SituationId): String = defs[id]?.title ?: id.name

    fun lesson(id: SituationId): String? = defs[id]?.lesson

    fun stepLabel(id: SituationId, stepId: String): String = defs[id]?.steps?.firstOrNull { it.id == stepId }?.label ?: stepId

    /** 그 조작의 스위치가 어디 있는지(차종 공통 표현). */
    fun hint(id: SituationId?, stepId: String): String? = defs[id]?.steps?.firstOrNull { it.id == stepId }?.hint

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
