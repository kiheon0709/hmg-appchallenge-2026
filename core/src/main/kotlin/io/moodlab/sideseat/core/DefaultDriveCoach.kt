package io.moodlab.sideseat.core

/** 주행 코치 구현. (첫 커밋: 뼈대. 규칙은 이어서 채운다.) */
class DefaultDriveCoach : DriveCoach {
    override fun onDriveStart(nowMs: Long) {}
    override fun tick(state: VehicleState, nowMs: Long): CoachUtterance? = null
    override fun onReply(ruleId: String, reply: String, nowMs: Long): CoachAction? = null
    override fun onDriveEnd(nowMs: Long): DriveSummary = DriveSummary(0, 0, 0, emptyList(), emptyMap(), null, emptyList())
}
