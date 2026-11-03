package io.moodlab.moodrive.core

/** 온보딩 화면 표시명 ↔ 카탈로그 장르 키. 프로토타입(design/prototype)과 같은 순서. */
object Genres {
    val all: List<Pair<String, String>> = listOf(
        "시티팝" to "citypop", "어쿠스틱" to "acoustic", "로파이" to "lofi", "앰비언트" to "ambient",
        "펑크" to "funk", "EDM" to "edm", "팝" to "pop", "재즈" to "jazz",
        "록" to "rock", "클래식" to "classical", "힙합" to "hiphop", "인디" to "indie",
    )
    const val PICK_COUNT = 3

    fun label(key: String): String = all.firstOrNull { it.second == key }?.first ?: key
}
