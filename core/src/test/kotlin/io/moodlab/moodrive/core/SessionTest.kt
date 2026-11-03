package io.moodlab.moodrive.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionTest {
    private val day = VehicleSnapshot(timestampMs = 0, minuteOfDay = 13 * 60, gear = Gear.D, etaMinutes = 20)

    @Test
    fun `rain picks a calmer track than clear day`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        val first = s.onSnapshot(day).filterIsInstance<Action.Play>().single().track
        s.onSnapshot(day.copy(timestampMs = 1_000, wiper = WiperLevel.INTERMITTENT))
        val rain = s.onSnapshot(day.copy(timestampMs = 20_000, wiper = WiperLevel.INTERMITTENT))
        val rainTrack = rain.filterIsInstance<Action.Play>().single().track
        assertTrue(rainTrack.mood.energy < first.mood.energy, "$rainTrack vs $first")
        assertTrue(rain.any { it is Action.Speak })
    }

    @Test
    fun `ambient is held while driving by default and applied when parked`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        assertTrue(s.onSnapshot(day).none { it is Action.SetAmbient })
        val parked = s.onSnapshot(day.copy(timestampMs = 1_000, gear = Gear.P))
        assertTrue(parked.any { it is Action.SetAmbient })
        assertTrue(parked.any { it is Action.TripEnded })
    }

    @Test
    fun `ambient follows mood when driving is allowed`() {
        val s = MooDriveSession(SampleCatalog.tracks, ambient = AmbientPlanner(allowWhileDriving = true))
        val a = s.onSnapshot(day).filterIsInstance<Action.SetAmbient>().single()
        // 같은 상태면 다시 보내지 않음
        assertTrue(s.onSnapshot(day.copy(timestampMs = 500)).none { it is Action.SetAmbient })
        val b = s.onSnapshot(day.copy(timestampMs = 1_000, lowBeamOn = true)).filterIsInstance<Action.SetAmbient>().single()
        assertNotEquals(a.command.color, b.command.color)
    }

    @Test
    fun `dislike lowers genre score and skips`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        val t = s.onSnapshot(day).filterIsInstance<Action.Play>().single().track
        val next = s.onFeedback(Feedback.DISLIKE).filterIsInstance<Action.Play>().single().track
        assertNotEquals(t.id, next.id)
        assertTrue(s.taste.score(Scene.CLEAR_DAY, t.genre) < 0f)
    }

    @Test
    fun `no immediate repeats`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        val ids = mutableListOf(s.onSnapshot(day).filterIsInstance<Action.Play>().single().track.id)
        repeat(7) { ids += s.onTrackEnded(completed = true).filterIsInstance<Action.Play>().single().track.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `dj lines are rate limited except safety`() {
        val s = MooDriveSession(SampleCatalog.tracks, engine = ContextEngine(ContextEngine.Config(sceneHoldMs = 0)))
        s.onSnapshot(day)
        assertTrue(s.onSnapshot(day.copy(timestampMs = 1_000, wiper = WiperLevel.LOW)).any { it is Action.Speak })
        // 1분 뒤 정체: 10분 간격 제한으로 침묵
        assertTrue(s.onSnapshot(day.copy(timestampMs = 61_000, congestion = true)).none { it is Action.Speak })
        // 폭우는 안전 멘트라 바로 말함
        assertTrue(s.onSnapshot(day.copy(timestampMs = 70_000, wiper = WiperLevel.HIGH)).any { it is Action.Speak })
    }

    @Test
    fun `finale queue fits eta and ends with finale`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        assertNull(s.planFinaleQueue())
        s.onSnapshot(day)
        val plan = assertNotNull(s.planFinaleQueue())
        val budget = 20 * 60
        assertTrue(plan.totalSec in (budget - 240)..(budget + 45), "total=${plan.totalSec}")
        assertTrue(plan.queue.last().mood.energy > 0.5f)
        assertEquals(plan.queue.size, plan.queue.map { it.id }.toSet().size)
    }

    @Test
    fun `finale planner handles very short eta`() {
        val f = SampleCatalog.tracks.first()
        val p = FinalePlanner.plan(SampleCatalog.tracks, remainingSec = 60, finale = f)
        assertEquals(listOf(f), p.queue)
    }

    @Test
    fun `ambient colors - rain is cool, sunset is warm, night is dimmed`() {
        val a = AmbientPlanner()
        val rain = a.colorFor(Mood(-0.4f, -0.2f))
        val warm = a.colorFor(Mood(-0.1f, 0.7f))
        assertTrue(rain.b > rain.r, "rain=${rain.hex()}")
        assertTrue(warm.r > warm.b, "warm=${warm.hex()}")
        assertTrue(a.commandFor(Mood(0.9f, 0.6f), night = true).brightness <= 40)
    }

    @Test
    fun `voice more energy raises next track energy`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        val first = s.onSnapshot(day).filterIsInstance<Action.Play>().single().track
        val up = s.onVoice("좀 더 신나게").filterIsInstance<Action.Play>().single().track
        assertTrue(up.mood.energy > first.mood.energy, "$up vs $first")
    }

    @Test
    fun `voice mute silences normal lines but not safety`() {
        val s = MooDriveSession(SampleCatalog.tracks, engine = ContextEngine(ContextEngine.Config(sceneHoldMs = 0)))
        s.onSnapshot(day)
        s.onVoice("말 그만해")
        assertTrue(s.djMuted)
        assertTrue(s.onSnapshot(day.copy(timestampMs = 1_000, wiper = WiperLevel.LOW)).none { it is Action.Speak })
        assertTrue(s.onSnapshot(day.copy(timestampMs = 2_000, wiper = WiperLevel.HIGH)).any { it is Action.Speak })
    }

    @Test
    fun `voice rest stop and what song`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        s.onSnapshot(day)
        assertTrue(s.onVoice("졸려").contains(Action.SuggestRestStop))
        val say = s.onVoice("이 노래 뭐야").filterIsInstance<Action.Speak>().single().text
        assertTrue(say.contains(s.currentTrack!!.title))
        assertTrue(s.onVoice("오늘 저녁 뭐 먹지").isEmpty())
    }

    @Test
    fun `fatigue suggests rest stop`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        val night = day.copy(minuteOfDay = 2 * 60, drivingMinutes = 125)
        assertTrue(s.onSnapshot(night).contains(Action.SuggestRestStop))
    }
}
