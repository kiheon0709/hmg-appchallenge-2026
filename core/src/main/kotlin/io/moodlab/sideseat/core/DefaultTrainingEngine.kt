package io.moodlab.sideseat.core

/** 훈련 엔진 구현. (첫 커밋: 뼈대. 판정 로직은 이어서 채운다.) */
class DefaultTrainingEngine : TrainingEngine {
    private var progress = TrainingProgress()

    override fun chapters(): List<ChapterInfo> = emptyList()
    override fun start(chapterId: String, nowMs: Long) {}
    override fun startSurprise(nowMs: Long) {}
    override fun inject(situation: SituationId, nowMs: Long) {}
    override fun tick(state: VehicleState, nowMs: Long): Pair<TrainingView, List<CoachEvent>> =
        TrainingView(TrainingPhase.IDLE, null, null, "", emptyList(), 0, 0, 0f, SceneParams(), null) to emptyList()
    override fun answer(choiceOrText: String, nowMs: Long) {}
    override fun abort(nowMs: Long) {}
    override fun progress(): TrainingProgress = progress
    override fun restore(progress: TrainingProgress) { this.progress = progress }
}
