package io.moodlab.sideseat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SwitchGuideTest {
    private val common = """
        ## sw_highbeam 상향등
        질문: 상향등 어떻게
        짧게: 왼쪽 레버를 앞으로 밀어요.
        출처: 여섯 차종 공통
    """.trimIndent()
    private val procedures = """
        ## dense_fog 짙은 안개
        질문: 안개
        짧게: 전조등을 켜고 속도를 줄여요.
        출처: 도로교통법
    """.trimIndent()
    private val pleos = """
        차종: pleos_default
        이름: Pleos 탑재 차
        VIN:
        기본: 예
        ## sw_wiper 와이퍼 켜는 법
        질문: 와이퍼, 와이퍼 어떻게
        짧게: 설명서 그림으로는 왼쪽 레버 끝 노브를 돌려요.
        출처: 그랜저·CN8
        ## sw_autohold 자동 정차
        질문: 오토홀드
        짧게: 그랜저는 콘솔, CN8은 화면이에요.
        출처: 그랜저·CN8
    """.trimIndent()
    private val cn7 = """
        차종: avante_cn7
        이름: 아반떼(CN7)
        VIN: KMHL, KMHLM8
        ## sw_wiper 와이퍼 켜는 법
        짧게: 오른쪽 레버를 아래로 내려요.
        출처: CN7 설명서
    """.trimIndent()
    private val n = """
        차종: avante_n
        이름: 아반떼 N
        VIN: KMHLM81
        ## sw_wiper 와이퍼 켜는 법
        짧게: N은 다른 문장.
        출처: 설명서
    """.trimIndent()
    private val guide = SwitchGuide.parse(listOf(procedures, common), listOf(pleos, cn7, n, "이름: 차종 줄 없는 파일"))

    @Test fun parsesProfilesDefaultAndSkipsInvalid() {
        assertEquals(listOf("pleos_default", "avante_cn7", "avante_n"), guide.profiles.map { it.id })
        assertEquals("pleos_default", guide.defaultProfile?.id)
        assertTrue("VIN이 비어도 읽음", guide.profiles[0].vinPrefixes.isEmpty())
    }

    @Test fun resolvesByLongestVinPrefixElseUnknown() {
        assertEquals("avante_cn7", guide.resolve("KMHLL41D")?.id)
        assertEquals("avante_n", guide.resolve("KMHLM81A")?.id)
        assertNull("기본 차종은 VIN으로 고르지 않는다", guide.resolve("KNAXXXXX"))
        assertNull(guide.resolve(null))
    }

    @Test fun unknownCarUsesPleosDefaultWithVariesNote() {
        assertEquals("설명서 그림으로는 왼쪽 레버 끝 노브를 돌려요. ${SwitchGuide.VARIES_NOTE}", guide.location("sw_wiper", null))
        assertEquals("공통 문장은 그대로", "왼쪽 레버를 앞으로 밀어요.", guide.location("sw_highbeam", null))
        val kb = guide.knowledgeFor(null)
        assertEquals("sw_wiper", kb.match("와이퍼 어떻게 켜")?.entry?.id)
        assertTrue(kb.rawText.contains("차종을 모름"))
    }

    @Test fun knownCarUsesItsOwnThenCommonButNeverAnotherCar() {
        val p = guide.byId("avante_cn7")
        assertEquals("오른쪽 레버를 아래로 내려요.", guide.location("sw_wiper", p))
        assertEquals("왼쪽 레버를 앞으로 밀어요.", guide.location("sw_highbeam", p))
        assertNull("CN7 파일에 없는 자동 정차는 그랜저 문장을 쓰지 않는다", guide.location("sw_autohold", p))
        val kb = guide.knowledgeFor(p)
        assertEquals("질문 낱말을 기본 차종에서 물려받아 빠른 길이 맞는다", "오른쪽 레버를 아래로 내려요.", kb.match("와이퍼 어떻게 켜")?.entry?.short)
        assertEquals(1, kb.entries.count { it.id == "sw_wiper" })
        assertTrue(kb.entries.any { it.id == "dense_fog" })
        assertTrue(kb.rawText, "이 차: 아반떼(CN7)" in kb.rawText)
    }

    @Test fun renderRoundTrips() {
        val kb = guide.knowledgeFor(null)
        val again = KnowledgeBase.parse(listOf(kb.rawText))
        assertEquals(kb.entries.map { it.id }, again.entries.map { it.id })
        assertEquals(kb.entries.map { it.short }, again.entries.map { it.short })
    }

    // ---------- 실제 데이터(조사 문서 기준) ----------

    private val dir = File("src/main/assets/knowledge")
    private val real by lazy {
        SwitchGuide.parse(
            dir.listFiles()!!.filter { it.name.endsWith(".md") }.sortedBy { it.name }.map { it.readText() },
            File(dir, "vehicles").listFiles()!!.filter { it.name.endsWith(".md") }.sortedBy { it.name }.map { it.readText() },
        )
    }

    @Test fun realDataHasSixModelsPlusDefault() {
        assertEquals(
            setOf("pleos_default", "grandeur_gn7", "avante_cn8", "avante_cn7", "casper_ax", "ray_tam", "seltos_sp2"),
            real.profiles.map { it.id }.toSet(),
        )
        assertEquals("pleos_default", real.defaultProfile?.id)
        assertTrue("국내 VIN 코드는 미확인이라 비워 둠", real.profiles.all { it.vinPrefixes.isEmpty() })
    }

    @Test fun realDataQualityRules() {
        real.profiles.forEach { p ->
            p.entries.forEach { e ->
                assertTrue("${p.id}/${e.id} 출처 없음", e.source.isNotBlank())
                assertTrue("${p.id}/${e.id} 주행 중 답이 길다 ${e.short.length}자", e.short.length <= 60)
                assertFalse("${p.id}/${e.id} 금지어", "급발진" in e.short || "급발진" in e.detail)
                // 그림 판독인 위치는 단정하지 않는다
                if ("그림 판독" in e.source && "위치 미확인" !in e.source) {
                    assertTrue("${p.id}/${e.id}: 그림 판독인데 '설명서 그림으로는'이 없음", "설명서 그림으로는" in e.short + e.detail || "설명서 그림으로" in e.detail)
                }
            }
        }
    }

    @Test fun pleosCarsWiperIsLeftAndOthersRight() {
        val left = listOf("pleos_default", "grandeur_gn7", "avante_cn8")
        val right = listOf("avante_cn7", "casper_ax", "ray_tam", "seltos_sp2")
        left.forEach { assertTrue(it, "왼쪽" in real.location("sw_wiper", real.byId(it))!!) }
        right.forEach { assertTrue(it, "오른쪽" in real.location("sw_wiper", real.byId(it))!!) }
        assertTrue("캐스퍼는 위로 올릴수록", "위로" in real.location("sw_wiper", real.byId("casper_ax"))!!)
        assertTrue("그랜저 공조는 화면 안", "화면" in real.location("sw_ac", real.byId("grandeur_gn7"))!!)
        assertTrue("CN7 공조는 물리 버튼", "공조 패널" in real.location("sw_ac", real.byId("avante_cn7"))!!)
    }

    @Test fun realUnknownCarRoutesQuestions() {
        val kb = real.knowledgeFor(null)
        listOf("sw_wiper", "sw_headlight", "sw_highbeam", "sw_turn", "sw_fog", "sw_hazard", "sw_defrost", "sw_rear_defrost",
            "sw_ac", "sw_recirc", "sw_window", "sw_shift", "sw_epb", "sw_autohold", "sw_hood", "sw_start", "sw_fuel")
            .forEach { assertTrue(it, kb.entries.any { e -> e.id == it }) }
        assertEquals("sw_ac", kb.match("에어컨 어떻게 켜")?.entry?.id)
        assertEquals("sw_window", kb.match("창문 어떻게 열어")?.entry?.id)
        assertEquals("sw_fog", kb.match("안개등 어떻게 켜?")?.entry?.id)
        assertEquals("sw_hazard", kb.match("비상등 어딨어")?.entry?.id)
        assertTrue("모르는 차는 차마다 다르다고", SwitchGuide.VARIES_NOTE in real.location("sw_wiper", null)!!)
    }
}
