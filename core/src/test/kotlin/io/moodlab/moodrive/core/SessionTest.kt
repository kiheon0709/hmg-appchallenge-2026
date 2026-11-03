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
        assertTrue(s.onVoice("졸려").contains(Action.AddRestStop))
        val say = s.onVoice("이 노래 뭐야").filterIsInstance<Action.Speak>().single().text
        assertTrue(say.contains(s.currentTrack!!.title))
        assertTrue(s.onVoice("오늘 저녁 뭐 먹지").isEmpty())
    }

    @Test
    fun `fatigue suggests rest stop with the real duration`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        val night = day.copy(minuteOfDay = 2 * 60, drivingMinutes = 95)
        val a = s.onSnapshot(night)
        assertTrue(a.contains(Action.SuggestRestStop))
        assertTrue(a.filterIsInstance<Action.Speak>().any { it.text.contains("1시간 35분째") }, a.toString())
    }

    @Test
    fun `declined rest stop is offered again after 30 minutes, not before`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        val t0 = day.copy(minuteOfDay = 15 * 60, drivingMinutes = 125)
        assertTrue(s.onSnapshot(t0).contains(Action.SuggestRestStop))
        s.answerRestStop(false)
        assertTrue(!s.onSnapshot(t0.copy(timestampMs = 20 * 60_000L, drivingMinutes = 145)).contains(Action.SuggestRestStop))
        assertTrue(s.onSnapshot(t0.copy(timestampMs = 31 * 60_000L, drivingMinutes = 156)).contains(Action.SuggestRestStop))
    }

    @Test
    fun `session measures continuous driving itself and resets after a 10 minute stop`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        val base = day.copy(drivingMinutes = 0) // 어댑터가 주행 시간을 모르는 경우
        var fired = false
        for (m in 0..125 step 5) fired = fired || s.onSnapshot(base.copy(timestampMs = m * 60_000L)).contains(Action.SuggestRestStop)
        assertTrue(fired)
        s.answerRestStop(true)
        s.onSnapshot(base.copy(timestampMs = 126 * 60_000L, gear = Gear.P))
        s.onSnapshot(base.copy(timestampMs = 137 * 60_000L, gear = Gear.P))
        s.onSnapshot(base.copy(timestampMs = 138 * 60_000L))
        assertTrue(s.continuousDrivingMinutes < 5, "${s.continuousDrivingMinutes}")
    }

    @Test
    fun `hold keeps the mood for the next songs, but switches if tempo is over the new cap`() {
        val s = MooDriveSession(SampleCatalog.tracks, engine = ContextEngine(ContextEngine.Config(sceneHoldMs = 0)))
        s.onSnapshot(day)
        s.onVoice("이 분위기 유지")
        val held = s.targetMood()
        s.onSnapshot(day.copy(timestampMs = 1_000, wiper = WiperLevel.LOW))
        assertEquals(held, s.targetMood())
        val next = s.onTrackEnded(completed = true).filterIsInstance<Action.Play>().single().track
        assertTrue(next.mood.distanceTo(held) <= 0.6f)
        // 비(110 BPM 상한)에서 빠른 곡이 걸려 있으면 유지 중이어도 바뀐다
        val fast = MooDriveSession(SampleCatalog.tracks.filter { (it.bpm ?: 0) > 110 }, engine = ContextEngine(ContextEngine.Config(sceneHoldMs = 0)))
        fast.onSnapshot(day)
        fast.onVoice("이 분위기 유지")
        val acts = fast.onSnapshot(day.copy(timestampMs = 1_000, congestion = true))
        assertTrue(acts.any { it is Action.Play })
    }

    @Test
    fun `voice skip uses played time - late voice skip is neutral`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        val t = s.onSnapshot(day).filterIsInstance<Action.Play>().single().track
        s.onVoice("다음 곡", playedSec = 120)
        assertEquals(0f, s.taste.score(Scene.CLEAR_DAY, t.genre))
        // 들은 시간을 안 주면 차량 시간으로 계산: 2분 뒤 스킵은 중립
        val s2 = MooDriveSession(SampleCatalog.tracks)
        val t2 = s2.onSnapshot(day).filterIsInstance<Action.Play>().single().track
        s2.onSnapshot(day.copy(timestampMs = 120_000))
        s2.onVoice("넘겨")
        assertEquals(0f, s2.taste.score(Scene.CLEAR_DAY, t2.genre))
    }

    @Test
    fun `parking applies the last driving mood to the cabin light`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        s.onSnapshot(day.copy(etaMinutes = 3))
        val finale = s.targetMood()
        val light = s.onSnapshot(day.copy(timestampMs = 1_000, gear = Gear.P)).filterIsInstance<Action.SetAmbient>().single()
        assertEquals(AmbientPlanner().commandFor(finale, night = false).color, light.command.color)
    }

    @Test
    fun `onboarding genres get a real share of picks`() {
        val prefs = setOf("jazz", "lofi", "indie")
        val s = MooDriveSession(SampleCatalog.tracks, taste = TasteModel(prefs))
        val sc = Scenario.rainyCommute()
        val picks = mutableListOf<Track>()
        for (t in 0..sc.durationSec) s.onSnapshot(sc.snapshotAt(t)).filterIsInstance<Action.Play>().forEach { picks += it.track }
        val share = picks.count { it.genre in prefs } / picks.size.toFloat()
        assertTrue(share >= 0.4f, "share=$share ${picks.map { it.genre }}")
    }

    @Test
    fun `finale favours the genre liked during this trip`() {
        val s = MooDriveSession(SampleCatalog.tracks, engine = ContextEngine(ContextEngine.Config(sceneHoldMs = 0)))
        s.onSnapshot(day)
        // 이번 주행에서 indie를 세 번 좋아함
        val indie = SampleCatalog.tracks.first { it.genre == "indie" }
        repeat(3) { s.onFeedbackFor(indie, Feedback.LIKE) }
        val finale = s.onSnapshot(day.copy(timestampMs = 1_000, etaMinutes = 3)).filterIsInstance<Action.Play>().single().track
        assertEquals("indie", finale.genre)
    }

    @Test
    fun `no-sleepy is not a rest stop request`() {
        assertEquals(VoiceIntent.NO, VoiceCommandParser.parse("안 졸려"))
    }

    @Test
    fun `rest stop prompt is answered by voice yes or no`() {
        val night = day.copy(minuteOfDay = 15 * 60, drivingMinutes = 125)
        val a = MooDriveSession(SampleCatalog.tracks)
        assertTrue(a.onSnapshot(night).contains(Action.SuggestRestStop))
        assertTrue(a.restStopPending)
        assertTrue(a.onVoice("응").contains(Action.AddRestStop))
        assertTrue(!a.restStopPending)

        val b = MooDriveSession(SampleCatalog.tracks)
        b.onSnapshot(night)
        assertTrue(b.onVoice("괜찮아").none { it == Action.AddRestStop })
        assertTrue(!b.restStopPending)
    }

    @Test
    fun `hold keeps the song across scene changes but not for safety`() {
        val s = MooDriveSession(SampleCatalog.tracks, engine = ContextEngine(ContextEngine.Config(sceneHoldMs = 0)))
        s.onSnapshot(day)
        s.onVoice("이 분위기 유지해줘")
        val t = s.currentTrack
        assertTrue(s.onSnapshot(day.copy(timestampMs = 1_000, congestion = true)).none { it is Action.Play })
        assertEquals(t, s.currentTrack)
        assertTrue(s.onSnapshot(day.copy(timestampMs = 2_000, wiper = WiperLevel.HIGH)).any { it is Action.Play })
    }

    @Test
    fun `late skip is neutral, early skip is negative`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        val t1 = s.onSnapshot(day).filterIsInstance<Action.Play>().single().track
        s.onTrackEnded(completed = false, playedSec = 90)
        assertEquals(0f, s.taste.score(Scene.CLEAR_DAY, t1.genre))
        val t2 = s.currentTrack!!
        s.onTrackEnded(completed = false, playedSec = 10)
        assertTrue(s.taste.score(Scene.CLEAR_DAY, t2.genre) < 0f)
    }

    @Test
    fun `rain and jam respect the 110 bpm cap`() {
        val s = MooDriveSession(SampleCatalog.tracks, engine = ContextEngine(ContextEngine.Config(sceneHoldMs = 0)))
        s.onSnapshot(day)
        val jam = s.onSnapshot(day.copy(timestampMs = 1_000, congestion = true)).filterIsInstance<Action.Play>().single().track
        assertTrue((jam.bpm ?: 0) <= 110, "$jam")
        repeat(10) { val n = s.onTrackEnded(completed = true).filterIsInstance<Action.Play>().single().track; assertTrue((n.bpm ?: 0) <= 110, "$n") }
    }

    @Test
    fun `every single genre preference is heard in the rainy demo`() {
        val sc = Scenario.rainyCommute()
        val silent = Genres.all.map { it.second }.filter { g ->
            val s = MooDriveSession(SampleCatalog.tracks, taste = TasteModel(setOf(g)))
            val picks = (0..sc.durationSec).flatMap { t -> s.onSnapshot(sc.snapshotAt(t)).filterIsInstance<Action.Play>().map { it.track } }
            picks.none { it.genre == g }
        }
        assertEquals(emptyList(), silent, "genres never picked when preferred")
    }

    @Test
    fun `event-based signals - one wiper event plus ticks changes the scene`() {
        // Pleos SDK처럼 값이 바뀔 때만 스냅샷이 오는 경우
        val s = MooDriveSession(SampleCatalog.tracks)
        s.onSnapshot(day)
        s.onSnapshot(day.copy(timestampMs = 1_000, wiper = WiperLevel.LOW))
        assertEquals(Scene.CLEAR_DAY, s.currentScene)
        var acts = emptyList<Action>()
        for (ms in 2_000L..20_000L step 1_000L) acts = acts + s.tick(ms)
        assertEquals(Scene.RAIN, s.currentScene)
        assertTrue(acts.any { it is Action.Play })
    }

    @Test
    fun `ticks advance continuous driving time to the fatigue alert`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        s.onSnapshot(day.copy(drivingMinutes = 0))
        var offered = false
        for (min in 1..121) offered = offered || s.tick(min * 60_000L).contains(Action.SuggestRestStop)
        assertTrue(offered)
    }

    @Test
    fun `unanswered rest stop prompt closes after a minute, and liking a song does not accept it`() {
        val s = MooDriveSession(SampleCatalog.tracks)
        val t0 = day.copy(minuteOfDay = 15 * 60, drivingMinutes = 125)
        s.onSnapshot(t0)
        assertTrue(s.restStopPending)
        assertTrue(s.onVoice("이 노래 좋아").none { it == Action.AddRestStop })
        assertTrue(s.restStopPending)
        s.tick(61_000)
        assertTrue(!s.restStopPending)
    }

    @Test
    fun `fast song is replaced on heavy rain right after a tunnel`() {
        val fastFirst = SampleCatalog.tracks.sortedByDescending { it.bpm ?: 0 }
        val s = MooDriveSession(fastFirst, engine = ContextEngine(ContextEngine.Config(sceneHoldMs = 0)), taste = TasteModel())
        s.onSnapshot(day.copy(driveMode = DriveMode.SPORT))
        s.onSnapshot(day.copy(timestampMs = 1_000, driveMode = DriveMode.SPORT, lowBeamOn = true)) // 터널
        val acts = s.onSnapshot(day.copy(timestampMs = 2_000, driveMode = DriveMode.SPORT, lowBeamOn = true, wiper = WiperLevel.HIGH))
        assertEquals(Scene.HEAVY_RAIN, s.currentScene)
        val t = acts.filterIsInstance<Action.Play>().singleOrNull()?.track ?: s.currentTrack!!
        assertTrue((t.bpm ?: 0) <= 110, "$t")
        assertTrue(acts.any { it is Action.Speak })
    }

    @Test
    fun `tunnel entry replaces a song above the 110 bpm cap`() {
        val fast = SampleCatalog.tracks.filter { (it.bpm ?: 0) > 110 } + SampleCatalog.tracks.filter { (it.bpm ?: 0) <= 110 }
        val s = MooDriveSession(fast, engine = ContextEngine(ContextEngine.Config(sceneHoldMs = 0)))
        s.onSnapshot(day.copy(driveMode = DriveMode.SPORT))
        if ((s.currentTrack!!.bpm ?: 0) <= 110) return // 시작 곡이 이미 느리면 해당 없음
        val acts = s.onSnapshot(day.copy(timestampMs = 1_000, driveMode = DriveMode.SPORT, lowBeamOn = true))
        assertEquals(Scene.TUNNEL, s.currentScene)
        assertTrue((s.currentTrack!!.bpm ?: 0) <= 110 && acts.any { it is Action.Play })
    }
}
