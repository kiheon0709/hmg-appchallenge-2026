plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    application
}
repositories { mavenCentral(); google() }
dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
kotlin {
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin", "../../core/src/main/kotlin", "../../app/src/main/java/io/moodlab/sideseat/ui")
}
application { mainClass.set("RenderKt") }
