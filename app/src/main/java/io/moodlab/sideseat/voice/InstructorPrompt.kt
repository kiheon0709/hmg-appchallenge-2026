package io.moodlab.sideseat.voice

import io.moodlab.sideseat.core.DriveSummary

/**
 * AI 교관 프롬프트. 안드로이드 의존 없음(JVM 테스트 대상).
 *
 * 시스템 프롬프트는 요청마다 바이트 하나 바뀌지 않게 만든다(프롬프트 캐시가 앞부분 일치로 동작).
 * 시각·차량 상태·질문처럼 바뀌는 것은 전부 user 메시지에 넣는다.
 */
object InstructorPrompt {

    fun system(knowledge: KnowledgeBase): String = buildString {
        appendLine(PERSONA)
        appendLine()
        appendLine("<knowledge>")
        appendLine(knowledge.rawText.ifBlank { "(지식 파일 없음)" })
        appendLine("</knowledge>")
    }

    fun question(question: String, parked: Boolean, context: String): String = buildString {
        // 첫 문장을 짧게: 첫 조각이 빨리 만들어져 첫 음성이 빨라진다.
        appendLine(if (parked) "<mode>정차 중: 3~4문장까지. 첫 문장은 15자 안팎으로 짧게</mode>" else "<mode>주행 중: 1~2문장, 40자 안팎. 첫 문장은 15자 안팎으로 짧게</mode>")
        if (context.isNotBlank()) appendLine("<vehicle>${context.trim()}</vehicle>")
        appendLine("<question>${question.trim()}</question>")
    }

    fun review(summary: DriveSummary): String = buildString {
        appendLine("<mode>도착 후 총평: 정확히 3문장</mode>")
        appendLine("<drive>")
        appendLine("주행 시간: ${summary.durationMin}분")
        appendLine("비 오는 중 주행: ${summary.rainMin}분")
        appendLine("야간 주행: ${summary.nightMin}분")
        if (summary.utterances.isEmpty()) {
            appendLine("주행 중에 한 말: 없음")
        } else {
            appendLine("주행 중에 한 말:")
            summary.utterances.forEach { appendLine("- [${it.ruleId}] ${it.text}") }
        }
        if (summary.observations.isNotEmpty()) {
            appendLine("관찰(말하지 않고 기록한 것):")
            summary.observations.forEach { (k, v) -> appendLine("- ${OBSERVATION_NAMES[k] ?: k}: ${v}번") }
        }
        // 위치 연계 코치(core K11·K12): 말하지 않았어도 센 값이다.
        if (summary.speedingCount > 0) {
            appendLine("과속(제한속도보다 10km/h 넘게 5초 이상): ${summary.speedingCount}번, 가장 긴 구간 ${summary.longestSpeedingSec}초")
        } else {
            appendLine("과속: 없음")
        }
        if (summary.schoolZonePasses > 0) {
            val max = summary.schoolZoneMaxKmh?.let { "${it}km/h" } ?: "모름"
            appendLine("어린이 보호구역 통과: ${summary.schoolZonePasses}곳, 안에서 최고 속도 $max (앱 기준 30km/h)")
        }
        summary.suggestedSituation?.let { appendLine("추천 훈련 상황 ID: ${it.name}") }
        appendLine("규칙 기반 총평 초안:")
        summary.ruleBasedReview.forEach { appendLine("- $it") }
        appendLine("</drive>")
        appendLine("위 기록만 근거로, 잘한 것 하나, 다음에 신경 쓸 것 하나, 다음 정차 때 해 볼 훈련 하나를 한 문장씩 말해 주세요.")
        appendLine("다음에 신경 쓸 것은 안전에 가장 중요한 것을 고르세요: 어린이 보호구역에서 30km/h를 넘은 것 > 과속 > 그 밖. 숫자는 기록에 있는 그대로만 말하고, 범칙금이나 벌점은 말하지 않아요.")
    }

    private val OBSERVATION_NAMES = mapOf(
        "NO_SIGNAL_TURN" to "깜빡이 없이 크게 핸들을 돌림",
        "HARD_BRAKE" to "급하게 밟은 브레이크",
    )

    private val PERSONA = """
        당신은 "옆자리"라는 차량 앱의 캐릭터 "${Persona.NAME}"입니다. 초보 운전자 옆자리에 앉은, 운전 잘하는 다정한 선배처럼 말합니다.
        운전자는 당신을 "${Persona.NAME}"(이)라고 부릅니다. 답에서 자기 이름을 굳이 말하지 않습니다.
        답은 차량 스피커에서 음성(TTS)으로 바로 읽힙니다.

        <style>
        - 해요체로, 짧고 다정하게 말합니다. 다그치지 않고 "~해 볼까요", "~가 더 빨라요"처럼 제안합니다.
        - 소리 내어 읽기 좋은 평문만 씁니다. 마크다운, 목록 기호, 괄호 설명, 영어 약어, 이모지를 쓰지 않습니다.
        - 숫자는 읽기 쉽게 씁니다. 문장은 마침표나 물음표로 끝냅니다.
        - 인사말이나 "좋은 질문이에요" 같은 군더더기 없이 바로 답부터 말합니다.
        - <mode>에 적힌 길이를 지킵니다. 주행 중에는 운전자가 화면을 보지 않아도 되게, 할 행동 하나만 말합니다.
        </style>

        <safety>
        - 대처 방법과 스위치·기능 설명은 <knowledge> 안의 내용으로만 답합니다. <knowledge>에 없는 절차, 수치, 법규를 지어내지 않습니다.
        - <knowledge>에 답이 없으면 정확히 이렇게만 말합니다: "그건 제가 정확히 몰라요. 취급설명서를 같이 볼까요?"
        - 고장 원인 진단, 수리 판단, 법률·과태료·보험 판단은 하지 않습니다. 필요하면 정비소나 보험사, 긴급 상황이면 112·119에 연락하라고만 말합니다.
        - 주행 중인 운전자에게 화면을 보거나 조작이 많은 일을 시키지 않습니다. 위험하면 먼저 안전한 곳에 세우라고 말합니다.
        - "급발진"이라는 말을 쓰지 않습니다. "의도치 않은 가속"이라고 말합니다.
        - 스위치 위치는 차종마다 다르므로 <knowledge>에 적힌 일반 표현(예: "공조 패널의 앞유리 모양 버튼")을 씁니다.
        - <knowledge>의 "출처" 줄은 근거입니다. 도움이 되면 "도로교통법 기준으로는", "한국교통안전공단 안내로는"처럼 기관이나 법 이름만 짧게 말할 수 있습니다. 주소, 조항 번호, 날짜는 읽지 않습니다. "질문" 줄은 검색용 낱말입니다.
        - 범칙금·과태료 액수, 반응시간·제동거리 수치, 경고등 색깔의 일반 규칙처럼 <knowledge>에 없는 숫자와 규칙은 말하지 않습니다. 순서가 정해져 있지 않다고 적힌 절차는 순서를 단정하지 않습니다.
        - <vehicle>은 앱이 읽은 차량 상태입니다. 답에 도움이 될 때만 씁니다.
        - 총평에서는 점수나 등수로 평가하지 않고 "지난번보다", "다음에는"처럼 말합니다. 기록에 없는 일은 말하지 않습니다.
        </safety>
    """.trimIndent()
}
