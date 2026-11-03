pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "MooDrive"

include(":core")

// app 모듈(Android)은 Android SDK가 있는 환경(로컬 맥)에서만 포함한다.
// 클라우드처럼 SDK가 없는 곳에서는 ./gradlew :core:test 로 로직만 검증한다.
val localProps = java.util.Properties().apply {
    val f = file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasAndroidSdk = localProps.getProperty("sdk.dir") != null ||
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null
if (hasAndroidSdk) {
    include(":app")
}
gradle.extra["hasAndroidSdk"] = hasAndroidSdk
