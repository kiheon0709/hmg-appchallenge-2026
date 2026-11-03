// 플러그인은 루트 buildscript 한 곳에서 로드한다 (Kotlin·AGP 클래스로더 충돌 방지).
// AGP는 Android SDK가 있을 때만 받는다. 클라우드 환경에선 Google Maven에 접근할 수 없다.
buildscript {
    val hasAndroidSdk = gradle.extra["hasAndroidSdk"] as Boolean
    val kotlinVersion = "2.0.21"
    repositories {
        if (hasAndroidSdk) google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        if (hasAndroidSdk) {
            classpath("com.android.tools.build:gradle:8.7.3")
            classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
        }
    }
}

allprojects {
    repositories {
        if (gradle.extra["hasAndroidSdk"] as Boolean) google()
        mavenCentral()
        // TODO(맥): Pleos Connect SDK 저장소. Playground > My Project > SDK 탭에 안내된 주소로 교체
        // maven { url = uri("https://nexus-playground.pleos.ai/repository/...") }
    }
}
