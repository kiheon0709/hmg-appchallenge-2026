plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.moodlab.moodrive"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.moodlab.moodrive"
        minSdk = 28 // Pleos 요구사항: API 28 이상
        targetSdk = 34 // Pleos Connect Emulator 2.1.0 = API 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // TODO: 제출용 서명 설정 (keystore는 커밋 금지)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // TODO(맥): Playground SDK 탭에서 안내하는 Pleos Connect SDK 좌표로 교체
    // implementation("ai.pleos:connect-vehicle:<version>")
    // implementation("ai.pleos:connect-navihelper:<version>")
    // implementation("ai.pleos:connect-tts:<version>")
}
