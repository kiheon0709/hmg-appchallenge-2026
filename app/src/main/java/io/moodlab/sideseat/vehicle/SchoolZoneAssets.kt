package io.moodlab.sideseat.vehicle

import android.content.Context
import android.util.Log
import io.moodlab.sideseat.core.SchoolZoneIndex

/** 앱에 넣어 둔 어린이 보호구역 좌표(assets/zones/school_zones.tsv). 없거나 읽기 실패면 빈 목록(K11 안 함). */
object SchoolZoneAssets {
    const val PATH = "zones/school_zones.tsv"

    fun load(context: Context): SchoolZoneIndex = runCatching {
        context.assets.open(PATH).bufferedReader().use { SchoolZoneIndex.parse(it.readText()) }
    }.onSuccess { Log.i(VEHICLE_TAG, "school zones loaded=${it.zones.size}") }
        .onFailure { Log.w(VEHICLE_TAG, "FAIL school zones $it") }
        .getOrDefault(SchoolZoneIndex.EMPTY)
}
