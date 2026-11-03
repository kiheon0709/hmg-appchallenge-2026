package io.moodlab.moodrive.core

/**
 * 개발·테스트용 임시 카탈로그. 제목은 자리표시자.
 * TODO: 로열티프리/CC0 음원 약 60곡으로 교체하고 곡마다 energy·valence를 직접 태깅 (app/src/main/assets/tracks.json).
 */
object SampleCatalog {
    val tracks: List<Track> = listOf(
        t("cp1", "City Lights", "citypop", 232, 0.5f, 0.8f),
        t("cp2", "Sunset Avenue", "citypop", 248, 0.2f, 0.9f),
        t("cp3", "Neon Highway", "citypop", 215, 0.7f, 0.6f),
        t("ac1", "Rain on Glass", "acoustic", 201, -0.6f, -0.2f),
        t("ac2", "Wet Asphalt", "acoustic", 189, -0.4f, 0.0f),
        t("ac3", "Grey Morning", "acoustic", 224, -0.7f, -0.5f),
        t("lf1", "Low Tide", "lofi", 176, -0.5f, 0.2f),
        t("lf2", "Window Seat", "lofi", 168, -0.3f, 0.3f),
        t("am1", "Under the Mountain", "ambient", 260, 0.0f, -0.6f),
        t("am2", "Echo Chamber", "ambient", 242, 0.2f, -0.4f),
        t("fk1", "Groove Lane", "funk", 210, 0.5f, 0.5f),
        t("fk2", "Open Road", "funk", 198, 0.4f, 0.4f),
        t("ed1", "Overdrive", "edm", 205, 0.9f, 0.5f),
        t("ed2", "Wake Up Call", "edm", 190, 0.95f, 0.7f),
        t("pp1", "Smile Again", "pop", 203, 0.3f, 0.7f),
        t("pp2", "Golden Hour", "pop", 221, -0.1f, 0.75f),
        t("pp3", "Homecoming", "pop", 236, 0.75f, 0.85f),
        t("jz1", "Midnight Drive", "jazz", 255, -0.3f, -0.1f),
        t("jz2", "Blue Dashboard", "jazz", 244, -0.2f, 0.1f),
        t("rk1", "Redline", "rock", 199, 0.85f, 0.2f),
    )

    private fun t(id: String, title: String, genre: String, dur: Int, e: Float, v: Float) =
        Track(id, title, "MoodLab Sample", genre, dur, Mood(e, v))
}
