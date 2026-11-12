package io.moodlab.sideseat.core

/** M1 금고 다이얼 구현. (첫 커밋: 뼈대.) */
class DefaultSafeDialGame : SafeDialGame {
    override fun start(nowMs: Long) {}
    override fun tick(steeringAngleDeg: Float?, nowMs: Long): Pair<SafeDialView, List<CoachEvent>> =
        SafeDialView(listOf(90, -45, 120), 0, steeringAngleDeg ?: 0f, 0f, false) to emptyList()
}
