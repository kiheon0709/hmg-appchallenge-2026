package io.moodlab.sideseat.ui

import io.moodlab.sideseat.core.SceneParams
import io.moodlab.sideseat.core.SituationId
import io.moodlab.sideseat.core.StepView
import io.moodlab.sideseat.core.TrainingPhase
import io.moodlab.sideseat.core.TrainingView
import io.moodlab.sideseat.core.TurnSignal
import io.moodlab.sideseat.core.VehicleState
import io.moodlab.sideseat.core.WiperLevel
import io.moodlab.sideseat.ui.scene.Scene3dCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 훈련 상태 → 3D 장면 `sim.set` 값. 판정은 core가 하고 장면은 이 값만 따라간다. */
class Scene3dCommandTest {
    private val car = VehicleState(timestampMs = 0, parked = true, steeringAngleDeg = 0f, brakePressed = false)

    private fun view(
        phase: TrainingPhase,
        situation: SituationId?,
        scene: SceneParams = SceneParams(),
        speed: Float = 80f,
        steps: List<StepView> = emptyList(),
    ) = TrainingView(phase, "C1", situation, "", steps, 0, 0, speed, scene, null)

    private fun num(json: String, key: String) = Regex("\"$key\":(-?[0-9.]+)").find(json)!!.groupValues[1].toFloat()

    @Test
    fun `cruise follows the core speed and real switches`() {
        val json = Scene3dCommand.build(
            view(TrainingPhase.CRUISE, null, speed = 66.04f),
            car.copy(wiper = WiperLevel.HIGH, lowBeamOn = true, turnSignal = TurnSignal.LEFT, steeringAngleDeg = -12.34f),
        )
        assertEquals(66.0f, num(json, "hostSpeed"), 0.001f)
        assertEquals(55f, num(json, "hostGap"), 0.001f)
        assertEquals(2f, num(json, "wiper"), 0f)
        assertEquals(1f, num(json, "headlights"), 0f)
        assertEquals(-12.3f, num(json, "steer"), 0.001f)
        assertTrue(json, "\"turn\":\"left\"" in json && "\"landmark\":null" in json && "\"crash\":false" in json)
        assertTrue(json.startsWith("{") && json.endsWith("}"))
        assertFalse("glare" in json)
    }

    @Test
    fun `sudden stop brings the lead car right in front`() {
        fun gap(d: Float, speed: Float, phase: TrainingPhase = TrainingPhase.ACTIVE) = num(
            Scene3dCommand.build(view(phase, SituationId.SUDDEN_STOP, SceneParams(leadCarDistance = d, leadCarBraking = true), speed), car),
            "hostGap",
        )
        assertEquals(30f, gap(0.75f, 80f), 0.1f) // 시작: 30m 앞
        assertTrue(gap(0.05f, 80f) < 3.5f) // 늦게 밟으면 코앞
        assertTrue(gap(0.4f, 0f) < gap(0.4f, 80f) * 0.5f) // 밟아서 멈추면 앞차 바로 뒤에 선다
        val crash = Scene3dCommand.build(view(TrainingPhase.FAIL, SituationId.SUDDEN_STOP, SceneParams(leadCarDistance = 0f), 80f), car)
        assertEquals(1f, num(crash, "hostGap"), 0.001f)
        assertTrue("\"crash\":true" in crash)
    }

    @Test
    fun `crosswalk and toll gate are placed as landmarks and the gate waits for the answer`() {
        val cross = Scene3dCommand.build(view(TrainingPhase.ACTIVE, SituationId.RIGHT_TURN_CROSSWALK, SceneParams(sign = "CROSSWALK"), 30f), car)
        assertTrue(cross, "\"landmark\":\"crosswalk\"" in cross && "\"hold\":true" in cross)
        assertEquals(400f, num(cross, "hostGap"), 0f)
        val asking = view(TrainingPhase.ACTIVE, SituationId.HIPASS_WRONG_LANE, SceneParams(sign = "HIPASS"), 30f, listOf(StepView("pass", "", false), StepView("pay", "", false)))
        assertTrue("\"landmark\":\"tollgate\",\"hold\":true" in Scene3dCommand.build(asking, car))
        val passed = asking.copy(steps = listOf(StepView("pass", "", true), StepView("pay", "", false)))
        assertTrue("\"landmark\":\"tollgate\",\"hold\":false" in Scene3dCommand.build(passed, car))
    }

    @Test
    fun `merging starts in the right lane and moves over once the wheel is turned`() {
        val steps = listOf(StepView("signal_left", "", true), StepView("steer_left", "", false))
        val before = Scene3dCommand.build(view(TrainingPhase.ACTIVE, SituationId.HIGHWAY_MERGE, steps = steps), car)
        assertEquals(1f, num(before, "lane"), 0f)
        val after = Scene3dCommand.build(view(TrainingPhase.SUCCESS, SituationId.HIGHWAY_MERGE, steps = steps.map { it.copy(done = true) }), car)
        assertEquals(0f, num(after, "lane"), 0f)
    }

    @Test
    fun `place and the new landmarks are passed to the scene`() {
        val rb = Scene3dCommand.build(view(TrainingPhase.ACTIVE, SituationId.ROUNDABOUT_ENTRY, SceneParams(sign = "ROUNDABOUT", place = "CITY"), 30f), car)
        assertTrue(rb, "\"place\":\"city\"" in rb && "\"landmark\":\"roundabout\",\"hold\":true" in rb)
        assertEquals(400f, num(rb, "hostGap"), 0f)
        val school = Scene3dCommand.build(view(TrainingPhase.ACTIVE, SituationId.SCHOOL_ZONE, SceneParams(sign = "SCHOOL", place = "SCHOOL"), 50f), car)
        assertTrue(school, "\"place\":\"school\"" in school && "\"landmark\":\"school\"" in school)
        val rain = Scene3dCommand.build(view(TrainingPhase.ACTIVE, SituationId.HEAVY_RAIN, SceneParams(rain = 1f, place = "DOWNHILL")), car)
        assertTrue(rain, "\"place\":\"downhill\"" in rain && "\"landmark\":null" in rain)
    }
}
