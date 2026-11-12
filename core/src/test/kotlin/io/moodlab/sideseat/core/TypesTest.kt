package io.moodlab.sideseat.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class TypesTest {
    @Test
    fun `training progress round-trips through json`() {
        val p = TrainingProgress(
            bestStars = mapOf("HEAVY_RAIN" to 3),
            bestReactionMs = mapOf("SUDDEN_STOP" to 640L),
            licenses = mapOf("avante" to VehicleLicense(setOf("C1"), false)),
            surpriseBest = 4,
        )
        val json = Json.encodeToString(p)
        assertEquals(p, Json.decodeFromString<TrainingProgress>(json))
    }

    @Test
    fun `unknown vehicle key falls back to unknown license`() {
        val p = TrainingProgress(licenses = mapOf(TrainingProgress.UNKNOWN_VEHICLE to VehicleLicense(licensed = true)))
        assertEquals(true, p.licenseFor(null).licensed)
        assertEquals(false, p.licenseFor("other").licensed)
    }

    @Test
    fun `fog is on when either front or rear is on`() {
        val s = VehicleState(0, parked = true)
        assertEquals(null, s.anyFogOn)
        assertEquals(true, s.copy(rearFogOn = true, frontFogOn = false).anyFogOn)
        assertEquals(false, s.copy(frontFogOn = false).anyFogOn)
    }
}
