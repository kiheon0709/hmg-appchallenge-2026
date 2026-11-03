# app 컴파일 점검 (Android SDK 없는 환경용)

클라우드처럼 Google Maven·Android SDK에 접근할 수 없는 곳에서 `app/` Kotlin 코드가 컴파일되는지 확인한다.
JetBrains Compose(데스크톱, androidx.compose API 동일)와 Robolectric의 Android API 34 프레임워크 jar로 컴파일하고,
Maven Central에 없는 AndroidX 일부(ComponentActivity, viewModels, AndroidViewModel 등)는 `stubs/`의 시그니처로 대신한다.

```bash
cd tools/app-compile-check && ../../gradlew compileKotlin -q
```

한계: 스텁 시그니처는 표준 API를 손으로 옮긴 것이라 실제 AndroidX와 다를 수 있고, AGP·리소스·매니페스트 처리는 검사하지 않는다. 최종 확인은 맥 Android Studio에서.
