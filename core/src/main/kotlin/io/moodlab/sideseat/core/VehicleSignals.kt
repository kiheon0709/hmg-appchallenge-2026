package io.moodlab.sideseat.core

// 차량 신호를 VehicleState로 옮길 때 쓰는 순수 판단. 안드로이드 쪽 게이트웨이가 원시 값을 넣는다.

/** 정차(훈련 가능) 판정에 쓸 신호. */
enum class ParkedSource { UX_RESTRICTIONS, PLEOS_GEAR, AAOS_GEAR, PARKING_BRAKE }

/**
 * 정차 판정(명세 4절). 기본 순서: 주행 규제(CarUxRestrictions) → 기어(Pleos) → 기어(AAOS) → 주차 브레이크 경고등.
 * 앞의 신호를 모르면 다음 것으로 넘어간다. 모두 모르면 정차가 아닌 것으로 본다(훈련을 열지 않는 쪽이 안전).
 */
data class ParkedInputs(
    /** 규제 중(주행)이면 true. 모르면 null. */
    val uxRestricted: Boolean? = null,
    /** Pleos Vehicle getGear()가 P인가. */
    val pleosGearPark: Boolean? = null,
    /** AAOS GEAR_SELECTION이 P인가. */
    val aaosGearPark: Boolean? = null,
    val parkingBrakeOn: Boolean? = null,
) {
    fun resolve(order: List<ParkedSource> = DEFAULT_ORDER): Pair<Boolean, ParkedSource?> {
        for (src in order) {
            val v = when (src) {
                ParkedSource.UX_RESTRICTIONS -> uxRestricted?.not()
                ParkedSource.PLEOS_GEAR -> pleosGearPark
                ParkedSource.AAOS_GEAR -> aaosGearPark
                ParkedSource.PARKING_BRAKE -> parkingBrakeOn
            }
            if (v != null) return v to src
        }
        return false to null
    }

    companion object {
        val DEFAULT_ORDER = listOf(ParkedSource.UX_RESTRICTIONS, ParkedSource.PLEOS_GEAR, ParkedSource.AAOS_GEAR, ParkedSource.PARKING_BRAKE)
    }
}

/**
 * 방향지시등 판정. Pleos TurnSignal API는 OFF/LEFT/RIGHT만 주므로, 비상등은 경고등 TURN_SIGNAL_LEFT·RIGHT가 함께 켜진 것으로 본다.
 * 경고등은 깜빡이며 ON/OFF를 오갈 수 있어 마지막으로 켜진 뒤 [holdMs] 동안은 켜진 것으로 친다.
 */
class TurnSignalResolver(private val holdMs: Long = 1_200) {
    private var leftOnMs: Long? = null
    private var rightOnMs: Long? = null
    private var telltaleKnown = false

    /** 경고등 상태가 들어올 때마다(켜짐이면 true). */
    fun onTelltales(leftOn: Boolean, rightOn: Boolean, nowMs: Long) {
        telltaleKnown = true
        if (leftOn) leftOnMs = nowMs
        if (rightOn) rightOnMs = nowMs
    }

    /** @param api TurnSignal API 값(모르면 null) */
    fun resolve(api: TurnSignal?, nowMs: Long): TurnSignal? {
        val left = leftOnMs?.let { nowMs - it <= holdMs } == true
        val right = rightOnMs?.let { nowMs - it <= holdMs } == true
        return when {
            left && right -> TurnSignal.HAZARD
            api != null && api != TurnSignal.OFF -> api
            left -> TurnSignal.LEFT
            right -> TurnSignal.RIGHT
            api != null || telltaleKnown -> TurnSignal.OFF
            else -> null
        }
    }
}

/** 경고등 색 값 해석. SDK의 color(Int)가 enum 순서인지 ARGB인지 문서에 없어서 둘 다 받는다. */
fun telltaleColorOf(raw: Int): TelltaleColor {
    if (raw in TelltaleColor.entries.indices) return TelltaleColor.entries[raw]
    val r = (raw shr 16) and 0xFF
    val g = (raw shr 8) and 0xFF
    val b = raw and 0xFF
    return when {
        r > 180 && g > 180 && b > 180 -> TelltaleColor.WHITE
        r > 160 && g > 120 && b < 100 -> TelltaleColor.YELLOW
        r > 160 && g < 100 -> TelltaleColor.RED
        g > 140 && r < 120 -> TelltaleColor.GREEN
        b > 140 && r < 120 -> TelltaleColor.BLUE
        else -> TelltaleColor.GRAY
    }
}

/** 창문 위치를 열림 %로. AAOS WINDOW_POS처럼 최소값 = 닫힘으로 본다. */
fun windowOpenPercent(position: Int, min: Int, max: Int): Int {
    if (max <= min) return if (position > min) 100 else 0
    return (((position - min).toFloat() / (max - min)) * 100).toInt().coerceIn(0, 100)
}

/** "이 차 면허" 차종 키: VIN 앞 8자리(제조사·차종·차체). 일련번호는 쓰지 않는다. */
fun vehicleModelKeyFromVin(vin: String?): String? = vin?.trim()?.takeIf { it.length >= 8 }?.take(8)?.uppercase()
