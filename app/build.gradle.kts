import java.util.Properties

// 서명 정보는 커밋하지 않는 local.properties에 둔다
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val embedLlmKey = (findProperty("embedLlmKey") as String?) == "true"

fun com.android.build.api.dsl.DefaultConfig.llmField(name: String) {
    val v = localProps.getProperty(name)?.trim().orEmpty().replace("\\", "\\\\").replace("\"", "\\\"")
    buildConfigField("String", name, "\"$v\"")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.moodlab.sideseat"
    compileSdk = 34
    // 정차 판정: AAOS 표준 주행 규제 API(CarUxRestrictionsManager). Pleos 주행 규제 가이드가 안내하는 방식
    useLibrary("android.car")

    defaultConfig {
        applicationId = "io.moodlab.sideseat"
        minSdk = 28 // Pleos 요구사항: API 28 이상
        targetSdk = 34 // Pleos Connect Emulator 2.1.0 = API 34
        versionCode = 1
        versionName = "0.1.0"

        // AI 교관(LLM) 설정. 값은 커밋하지 않는 local.properties에서만 읽는다. 없으면 빈 문자열 → 미리 쓴 문장으로 동작.
        llmField("LLM_PROVIDER")
        llmField("LLM_API_KEY")
        llmField("LLM_MODEL_FAST")
        llmField("LLM_MODEL_REVIEW")
    }

    signingConfigs {
        localProps.getProperty("release.storeFile")?.let { path ->
            create("release") {
                storeFile = rootProject.file(path)
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8로 줄인다(디버그 dex 40MB). Pleos SDK·직렬화 규칙은 proguard-rules.pro
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            // 저장소에 올리는 release APK에는 키를 넣지 않는다. 로컬 시연용으로만 -PembedLlmKey=true
            if (!embedLlmKey) buildConfigField("String", "LLM_API_KEY", "\"\"")
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
        buildConfig = true
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

    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")

    // voice 단위 테스트(app/src/test)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
