package io.moodlab.sideseat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SwitchGuideTest {
    private val common = """
        ## sw_wiper 와이퍼 켜는 법
        질문: 와이퍼, 와이퍼 어떻게
        짧게: 와이퍼는 보통 핸들 오른쪽 레버예요.
        출처: 앱 기본 안내

        ## sw_fog 안개등 켜는 법
        질문: 안개등 어떻게
        짧게: 안개등은 보통 레버의 스위치를 돌려요. 차종마다 달라요.
        출처: 앱 기본 안내
    """.trimIndent()
    private val procedures = """
        ## dense_fog 짙은 안개
        질문: 안개
        짧게: 전조등을 켜고 속도를 줄여요.
        출처: 도로교통법
    """.trimIndent()
    private val avante = """
        차종: avante_cn7
        이름: 아반떼(CN7)
        VIN: KMHL, KMHLM8
        ## sw_wiper 와이퍼 켜는 법
        질문: 와이퍼, 와이퍼 어떻게
        짧게: 아반떼는 핸들 오른쪽 레버를 아래로 내리면 와이퍼가 켜져요.
        출처: 현대자동차 취급설명서 아반떼(CN7)
    """.trimIndent()
    private val avanteN = """
        차종: avante_n
        이름: 아반떼 N
        VIN: KMHLM81
        ## sw_wiper 와이퍼 켜는 법
        짧게: N은 다른 문장.
        출처: 설명서
    """.trimIndent()
    private val guide = SwitchGuide.parse(listOf(procedures, common), listOf(avante, avanteN, "이름: 머리말 없는 파일"))

    @Test fun parsesProfilesAndSkipsInvalid() {
        assertEquals(listOf("avante_cn7", "avante_n"), guide.profiles.map { it.id })
        assertEquals(listOf("KMHL", "KMHLM8"), guide.profiles[0].vinPrefixes)
    }

    @Test fun resolvesByLongestVinPrefix() {
        assertEquals("avante_cn7", guide.resolve("KMHLL41D")?.id)
        assertEquals("avante_cn7", guide.resolve("kmhlm8xx")?.id)
        assertEquals("avante_n", guide.resolve("KMHLM81A")?.id)
        assertNull(guide.resolve("KNAXXXXX"))
        assertNull(guide.resolve(null))
        assertNull(guide.resolve("DEBUG"))
    }

    @Test fun knownModelOverridesCommonAndKeepsOthers() {
        val p = guide.byId("avante_cn7")
        assertEquals("아반떼는 핸들 오른쪽 레버를 아래로 내리면 와이퍼가 켜져요.", guide.location("sw_wiper", p))
        // 덮어쓰기가 없는 스위치는 공통 + 차마다 다름
        assertEquals("안개등은 보통 레버의 스위치를 돌려요. 차종마다 달라요.", guide.location("sw_fog", p))
        val kb = guide.knowledgeFor(p)
        assertEquals("빠른 길도 이 차 문장", "아반떼는 핸들 오른쪽 레버를 아래로 내리면 와이퍼가 켜져요.", kb.match("와이퍼 어떻게 켜")?.entry?.short)
        assertTrue(kb.rawText, "이 차: 아반떼(CN7)" in kb.rawText)
        assertTrue("절차는 그대로", kb.entries.any { it.id == "dense_fog" })
        assertEquals("같은 아이디가 겹치지 않음", 1, kb.entries.count { it.id == "sw_wiper" })
    }

    @Test fun unknownModelAddsVariesNoteOnce() {
        assertEquals("와이퍼는 보통 핸들 오른쪽 레버예요. ${SwitchGuide.VARIES_NOTE}", guide.location("sw_wiper", null))
        assertEquals("이미 있으면 안 붙임", "안개등은 보통 레버의 스위치를 돌려요. 차종마다 달라요.", guide.location("sw_fog", null))
        assertTrue(guide.knowledgeFor(null).rawText.contains("차종을 모름"))
        assertNull(guide.location("sw_nope", null))
    }

    @Test fun renderRoundTrips() {
        val kb = guide.knowledgeFor(null)
        val again = KnowledgeBase.parse(listOf(kb.rawText))
        assertEquals(kb.entries.map { it.id }, again.entries.map { it.id })
        assertEquals(kb.entries.map { it.short }, again.entries.map { it.short })
    }

    @Test fun realAssetsLoad() {
        val dir = File("src/main/assets/knowledge")
        val files = dir.listFiles()!!.filter { it.name.endsWith(".md") }.sortedBy { it.name }.map { it.readText() }
        val vehicles = File(dir, "vehicles").listFiles()?.filter { it.name.endsWith(".md") }?.map { it.readText() }.orEmpty()
        val real = SwitchGuide.parse(files, vehicles)
        val kb = real.knowledgeFor(null)
        listOf("sw_wiper", "sw_headlight", "sw_highbeam", "sw_fog", "sw_hazard", "sw_defrost", "sw_ac", "sw_recirc", "sw_window", "sw_epb")
            .forEach { assertTrue(it, kb.entries.any { e -> e.id == it }) }
        assertEquals("sw_ac", kb.match("에어컨 어떻게 켜")?.entry?.id)
        assertEquals("sw_window", kb.match("창문 어떻게 열어")?.entry?.id)
    }
}
