plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.0.21")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// 시연 트레이스: core 엔진이 데모 시나리오를 돌린 결과를 JSON으로 저장 → HTML 프로토타입이 그대로 재생
tasks.register<JavaExec>("demoTrace") {
    group = "moodrive"
    description = "데모 시나리오 트레이스를 design/prototype/demo-trace.json 으로 생성"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.moodlab.moodrive.core.DemoTraceKt")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dfile.encoding=UTF-8")
    args(rootProject.file("design/prototype/demo-trace.json").absolutePath)
}
