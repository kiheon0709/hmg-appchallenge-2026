package io.moodlab.sideseat.voice

/**
 * 차종별 스위치 위치. 안드로이드 의존 없음(JVM 테스트 대상).
 *
 * 데이터
 * - 공통 기본값: `assets/knowledge/switches.md`(sw_로 시작하는 항목, "보통 ~" 표현).
 * - 차종별 덮어쓰기: `assets/knowledge/vehicles/<차종>.md`. 머리말 3줄 + 같은 형식의 항목. 같은 아이디(sw_wiper 등)면 공통을 대신한다.
 *   ```
 *   차종: avante_cn7
 *   이름: 아반떼(CN7)
 *   VIN: KMHL, KMHLM          ← VIN 앞자리 접두어(쉼표로 여러 개). 가장 길게 맞는 것을 고른다.
 *   ## sw_wiper 와이퍼 켜는 법
 *   짧게: …(취급설명서 기준, 위치를 단정해도 됨)
 *   자세히: …
 *   출처: 현대자동차 취급설명서 …
 *   ```
 * 차종을 모르면 공통 문장 끝에 "차마다 조금 달라요."를 붙인다(이미 "차종마다/차마다"가 있으면 안 붙임).
 */
class SwitchGuide(
    private val procedures: List<KnowledgeBase.Entry>,
    private val common: List<KnowledgeBase.Entry>,
    val profiles: List<VehicleProfile>,
) {

    /** VIN(또는 앞 8자리 차종 키)으로 차종을 고른다. 가장 길게 맞는 접두어. 없으면 null. */
    fun resolve(vinOrKey: String?): VehicleProfile? {
        val v = vinOrKey?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        return profiles.mapNotNull { p -> p.vinPrefixes.filter { v.startsWith(it) }.maxByOrNull { it.length }?.let { p to it.length } }
            .maxByOrNull { it.second }?.first
    }

    fun byId(id: String?): VehicleProfile? = profiles.firstOrNull { it.id == id }

    /** 이 차종의 지식(절차 + 스위치 위치). [profile]이 null이면 공통 + "차마다 조금 달라요". */
    fun knowledgeFor(profile: VehicleProfile?): KnowledgeBase {
        val switches = if (profile == null) {
            common.map(::withVariesNote)
        } else {
            val overrides = profile.entries.associateBy { it.id }
            common.map { overrides[it.id] ?: withVariesNote(it) } + profile.entries.filter { e -> common.none { it.id == e.id } }
        }
        val entries = procedures + switches
        val header = if (profile == null) {
            "# 대처 절차와 스위치 위치(차종을 모름: 위치는 일반 표현이고 차마다 달라요)"
        } else {
            "# 대처 절차와 스위치 위치(이 차: ${profile.name}. 스위치 위치는 이 차 취급설명서 기준)"
        }
        val kb = KnowledgeBase(entries, "")
        return KnowledgeBase(entries, kb.render(header))
    }

    /** 결과 화면·힌트에 쓸 이 차의 스위치 위치 문장. 없으면 null. */
    fun location(entryId: String, profile: VehicleProfile?, detailed: Boolean = false): String? {
        val e = profile?.entries?.firstOrNull { it.id == entryId }
            ?: common.firstOrNull { it.id == entryId }?.let(::withVariesNote)
            ?: return null
        return if (detailed) e.detail else e.short
    }

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

/** 차종 한 개의 스위치 위치 덮어쓰기. */
data class VehicleProfile(
    val id: String,
    val name: String,
    val vinPrefixes: List<String>,
    val entries: List<KnowledgeBase.Entry>,
) {
    companion object {
        /** 머리말(차종·이름·VIN)과 항목을 읽는다. 차종 아이디나 VIN이 없으면 null. */
        fun parse(text: String): VehicleProfile? {
            val head = text.lineSequence().takeWhile { !it.startsWith("## ") }.toList()
            fun field(key: String) = head.firstOrNull { it.trimStart().startsWith("$key:") }?.substringAfter(':')?.trim().orEmpty()
            val id = field("차종")
            val vins = field("VIN").split(',', '，', ' ').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
            if (id.isEmpty() || vins.isEmpty()) return null
            return VehicleProfile(id, field("이름").ifEmpty { id }, vins, KnowledgeBase.entriesOf(text))
        }
    }
}
