plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}
val repo = rootDir.parentFile.parentFile.absolutePath
sourceSets["main"].kotlin.srcDirs("$repo/core/src/main/kotlin", "$repo/app/src/main/java", "stubs")
sourceSets["main"].resources.srcDirs("$repo/core/src/main/resources")
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17); allWarningsAsErrors.set(false) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.compose.material3:material3-desktop:1.7.0")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:1.7.0")
    implementation("org.jetbrains.compose.ui:ui-desktop:1.7.0")
    implementation("org.jetbrains.compose.animation:animation-desktop:1.7.0")
    compileOnly("org.robolectric:android-all:14-robolectric-10818077")
}
