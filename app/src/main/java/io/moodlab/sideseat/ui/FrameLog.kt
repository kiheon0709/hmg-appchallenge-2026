package io.moodlab.sideseat.ui

import android.util.Log

/** 화면 디버그 스위치. 통합 세션이 실측할 때 켠다. */
object SideSeatUiDebug {
    /** true면 주행 화면이 약 2초마다 프레임 시간을 로그 태그 `SideSeatMeasure`로 남긴다. */
    @Volatile var logFrames: Boolean = false

    /** 마지막으로 잰 초당 프레임 수(디버그 패널 표시용). */
    @Volatile var lastFps: Float = 0f

    /** null이면 시스템의 주간·야간 모드를 따른다. true/false면 그 모드로 고정(검수용). */
    var forceDark: Boolean?
        get() = forceDarkState.value
        set(v) { forceDarkState.value = v }
    private val forceDarkState = androidx.compose.runtime.mutableStateOf<Boolean?>(null)

    /** true면 주행 화면이 효과를 줄여 그린다. 프레임이 26fps 아래로 떨어지면 주행 화면이 스스로 켠다. */
    @Volatile var lowDetail: Boolean = false
}

/** 프레임 간격과 그리기 기록 시간을 모아 주기적으로 로그로 남긴다. 할당 없음. */
internal class FrameMeter(private val name: String) {
    private var frames = 0
    private var sumNs = 0L
    private var maxNs = 0L
    private var drawSumNs = 0L
    private var drawMaxNs = 0L
    private var slow = 0

    fun frame(intervalNs: Long) {
        frames++; sumNs += intervalNs
        if (intervalNs > maxNs) maxNs = intervalNs
        if (intervalNs > 34_000_000L) slow++
        if (sumNs >= 2_000_000_000L) flush()
    }

    fun draw(ns: Long) {
        drawSumNs += ns
        if (ns > drawMaxNs) drawMaxNs = ns
    }

    /** 최근 구간의 초당 프레임 수. 아직 못 쟀으면 0. */
    var fps = 0f
        private set

    /** 프레임이 모자랐던 구간이 연달아 몇 번인지. 한 번 길게 멈춘 것(시스템이 앱을 잠깐 세운 경우)과 계속 느린 것을 가른다. */
    var slowStreak = 0
        private set

    private fun flush() {
        fps = frames * 1e9f / sumNs
        slowStreak = if (fps < 26f) slowStreak + 1 else 0
        SideSeatUiDebug.lastFps = fps
        if (SideSeatUiDebug.logFrames) {
            Log.i(
                "SideSeatMeasure",
                "frame $name fps=%.1f avgMs=%.1f maxMs=%.1f over33ms=%d/%d drawRecordAvgMs=%.2f drawRecordMaxMs=%.2f".format(
                    fps, sumNs / 1e6f / frames, maxNs / 1e6f, slow, frames, drawSumNs / 1e6f / frames, drawMaxNs / 1e6f,
                ),
            )
        }
        frames = 0; sumNs = 0; maxNs = 0; drawSumNs = 0; drawMaxNs = 0; slow = 0
    }
}
