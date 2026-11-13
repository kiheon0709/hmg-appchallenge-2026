package io.moodlab.sideseat.voice

/**
 * 차종별 스위치 위치. 안드로이드 의존 없음(JVM 테스트 대상). 원본 조사: docs/research/2026-11-13_switch-locations-by-model.md.
 *
 * 데이터
 * - 여섯 차종 공통: `assets/knowledge/switches.md`(조사 문서 4절의 문장만: 상향등·방향지시등·창문·주차 브레이크·보닛·주유구 방향).
 * - 차종별: `assets/knowledge/vehicles/<차종>.md`. 머리말 + 같은 형식의 항목. 같은 아이디(sw_wiper 등)면 공통을 대신한다.
 *   ```
 *   차종: avante_cn7
 *   이름: 더 뉴 아반떼(CN7)
 *   VIN:                 ← VIN 앞자리 접두어(쉼표로 여러 개). 국내 사양 코드를 확인하지 못해 지금은 모두 비어 있다.
 *   기본: 예             ← 차종을 모를 때 쓰는 기본 차종(pleos_default: 그랜저 GN7 부분변경·아반떼 CN8 공통)
 *   ## sw_wiper 와이퍼 켜는 법
 *   짧게: …   자세히: …   출처: 취급설명서 쪽·항목
 *   ```
 * - 그림을 보고 읽은 위치는 문장에 "설명서 그림으로는"을 붙이고, 확인 못 한 것은 "확인하지 못했어요"로 쓴다.
 * 차종을 모르면 기본 차종 문장 끝에 "차마다 조금 달라요."를 붙인다(이미 "차종마다/차마다"가 있으면 안 붙임).
 */
class SwitchGuide(
    private val procedures: List<KnowledgeBase.Entry>,
    /** 여섯 차종 모두에 맞는 문장(조사 문서 4절). 아는 차종에 그 항목이 없으면 이것을 쓴다. */
    private val common: List<KnowledgeBase.Entry>,
    val profiles: List<VehicleProfile>,
) {
    /** 차종을 모를 때 쓰는 기본 차종(머리말 `기본: 예`). 앱이 도는 Pleos 탑재 차(그랜저·아반떼 CN8) 공통. */
    val defaultProfile: VehicleProfile? = profiles.firstOrNull { it.isDefault }

    /** VIN(또는 앞 8자리 차종 키)으로 차종을 고른다. 가장 길게 맞는 접두어. 표가 비어 있거나 안 맞으면 null(→ 기본 차종). */
    fun resolve(vinOrKey: String?): VehicleProfile? {
        val v = vinOrKey?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        return profiles.mapNotNull { p -> p.vinPrefixes.filter { v.startsWith(it) }.maxByOrNull { it.length }?.let { p to it.length } }
            .maxByOrNull { it.second }?.first
    }

    fun byId(id: String?): VehicleProfile? = profiles.firstOrNull { it.id == id }

    /**
     * 이 차종의 스위치 항목. 아는 차종: 그 차 항목 → 없으면 여섯 차종 공통(다른 차 문장은 쓰지 않음).
     * 모르는 차종(null): 기본 차종 항목 + "차마다 조금 달라요" → 없으면 공통.
     * 질문 낱말이 빠진 항목은 공통·기본 차종의 같은 아이디에서 물려받는다.
     */
    fun switchesFor(profile: VehicleProfile?): List<KnowledgeBase.Entry> {
        val primary = profile ?: defaultProfile
        val unknown = profile == null
        val ids = LinkedHashSet<String>().apply {
            common.forEach { add(it.id) }
            primary?.entries?.forEach { add(it.id) }
        }
        return ids.mapNotNull { id ->
            val own = primary?.entries?.firstOrNull { it.id == id }
            val base = common.firstOrNull { it.id == id }
            val e = when {
                own != null -> if (unknown) withVariesNote(own) else own
                else -> base
            } ?: return@mapNotNull null
            if (e.keywords.isNotEmpty()) e else e.copy(keywords = keywordsFor(id))
        }
    }

    /** 이 차종의 지식(절차 + 스위치 위치). */
    fun knowledgeFor(profile: VehicleProfile?): KnowledgeBase {
        val entries = procedures + switchesFor(profile)
        val header = if (profile == null) {
            "# 대처 절차와 스위치 위치(차종을 모름: Pleos 탑재 차 기준이고 차마다 조금 달라요)"
        } else {
            "# 대처 절차와 스위치 위치(이 차: ${profile.name}. 스위치 위치는 이 차 취급설명서 기준이고, \"설명서 그림으로는\"이 붙은 것은 그림을 보고 읽은 위치예요)"
        }
        return KnowledgeBase(entries, KnowledgeBase(entries, "").render(header))
    }

    /** 결과 화면·힌트에 쓸 이 차의 스위치 위치 문장. 없으면 null. */
    fun location(entryId: String, profile: VehicleProfile?, detailed: Boolean = false): String? {
        val e = switchesFor(profile).firstOrNull { it.id == entryId } ?: return null
        return if (detailed) e.detail else e.short
    }

    private fun keywordsFor(id: String): List<String> =
        common.firstOrNull { it.id == id }?.keywords?.takeIf { it.isNotEmpty() }
            ?: defaultProfile?.entries?.firstOrNull { it.id == id }?.keywords.orEmpty()

    private fun withVariesNote(e: KnowledgeBase.Entry): KnowledgeBase.Entry {
        fun add(t: String) = if (VARIES.any { it in t }) t else "${t.trimEnd()} $VARIES_NOTE"
        return e.copy(short = add(e.short), detail = add(e.detail))
    }

    companion object {
        const val VARIES_NOTE = "차마다 조금 달라요."
        private val VARIES = listOf("차종마다", "차마다")

        /** procedures·switches 파일 내용과 차종 파일 내용으로 만든다. sw_로 시작하는 항목이 스위치 위치. */
        fun parse(knowledgeFiles: List<String>, vehicleFiles: List<String>): SwitchGuide {
            val all = knowledgeFiles.flatMap(KnowledgeBase::entriesOf)
            return SwitchGuide(
                procedures = all.filterNot { it.id.startsWith(SWITCH_PREFIX) },
                common = all.filter { it.id.startsWith(SWITCH_PREFIX) },
                profiles = vehicleFiles.mapNotNull(VehicleProfile::parse),
            )
        }

        const val SWITCH_PREFIX = "sw_"
    }
}

/** 차종 한 개의 스위치 위치. */
data class VehicleProfile(
    val id: String,
    val name: String,
    /** VIN 앞자리 접두어. 국내 사양 코드를 확인하지 못한 차종은 비어 있다(디버그 강제 지정으로만 고름). */
    val vinPrefixes: List<String>,
    val entries: List<KnowledgeBase.Entry>,
    /** 차종을 모를 때 쓰는 기본 차종. */
    val isDefault: Boolean = false,
) {
    companion object {
        /** 머리말(차종·이름·VIN·기본)과 항목을 읽는다. 차종 아이디가 없으면 null. */
        fun parse(text: String): VehicleProfile? {
            val head = text.lineSequence().takeWhile { !it.startsWith("## ") }.toList()
            fun field(key: String) = head.firstOrNull { it.trimStart().startsWith("$key:") }?.substringAfter(':')?.trim().orEmpty()
            val id = field("차종")
            if (id.isEmpty()) return null
            val vins = field("VIN").split(',', '，', ' ').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
            return VehicleProfile(id, field("이름").ifEmpty { id }, vins, KnowledgeBase.entriesOf(text), isDefault = field("기본") == "예")
        }
    }
}
