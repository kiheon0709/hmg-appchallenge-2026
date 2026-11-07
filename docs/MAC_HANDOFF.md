# 맥 로컬 세션 작업 지시서

맥의 Claude Code(데스크톱 앱 또는 터미널 `claude`)에서 이 저장소 폴더를 열고 아래를 그대로 붙여 넣는다.

---

이 저장소는 Pleos Connect(Android Automotive) 공모전 앱 MooDrive다. README.md와 docs/STATUS.md를 먼저 읽어라.
브랜치 `main-bpn5uz`를 체크아웃하고 아래 순서로 진행한 뒤 결과를 docs/STATUS.md의 "맥 검증 결과" 절에 기록하고 커밋·푸시해라.

1. `./gradlew :core:test` 통과 확인.
2. `local.properties`에 `sdk.dir`이 있는지 확인(없으면 Android Studio SDK 경로로 추가, 커밋 금지).
3. `./gradlew :app:assembleDebug` 실행. app 모듈은 클라우드에서 한 번도 컴파일되지 않았다. 컴파일 오류를 고쳐라. 로직 수정은 core 테스트가 계속 통과하는 범위에서만.
4. Pleos Connect Emulator 2.1.0(API 34, Automotive)을 띄우고 `./gradlew :app:installDebug` 후 앱 실행. 디버그 패널의 "▶ 비 오는 퇴근길"을 눌러 장면·조명 미리보기·곡이 바뀌는지 확인하고 스크린샷을 `docs/screens/`에 저장.
5. 공개 문서 기준 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`, 의존성 `ai.pleos.playground:Vehicle:2.3.2`로 build.gradle.kts / app/build.gradle.kts의 TODO를 채워라. Wiper 퍼미션은 `CAR_WINDOWS`다. Playground 프로젝트 Key는 절대 커밋하지 말고 local.properties에 둬라. SDK 라이선스(42dot)는 사용자 동의 후 진행하고, 디컴파일·역공학은 금지 조항이므로 API는 공개 문서로 확인해라.
6. `PleosAmbientController`에 setAmbientLightBrightnessColor 연결 → Virtual Console에서 기어를 P와 D로 바꿔 가며 호출이 성공/거부되는지 확인하고 결과(에러 메시지 포함)를 기록. 이것이 접수 메일 1번 질문의 실측 답이다.
7. 가능하면 `PleosVehicleSignalSource`에서 Wiper·LowBeam 구독을 먼저 연결.

막히는 지점(로그인, SDK 신청 승인 대기 등)은 억지로 우회하지 말고 STATUS.md에 적고 멈춰라.

## 11-03 이후 추가된 확인 항목

- app 코드는 클라우드에서 JVM 하네스(`tools/app-compile-check`)로 컴파일 오류 0까지 확인함. AGP·리소스·매니페스트 처리는 미확인이라 3번 빌드에서 처음 드러날 수 있음.
- 첫 실행 시 온보딩(장르 3개) → 주행 화면. 데모 패널은 왼쪽 위 기어 칩을 **길게 눌러** 연다.
- `Action.AddRestStop`(휴게소 수락) 처리부는 TODO: NaviHelper로 가까운 휴게소 검색 → 경유지 추가.
- 주행/정차 판정: 기어를 어느 Vehicle 도메인에서 주는지 확인. 없으면 FusedLocation 속도 5km/h 기준으로 장면만 대체하고 결과를 STATUS.md에 기록. 조명 허용 판단은 속도로 대체하지 말 것(운영사무국: 제한 기준은 P단).
- 와이퍼 강도 단계(INT/LOW/HIGH)가 API에 있는지 확인. 없으면 폭우 장면은 비 장면으로 합침.
- 음성 인식·TTS가 에뮬레이터에서 동작하는지 확인(매니페스트 `<queries>` 선언 포함됨).

## 11-07 진행 결과

1~7번과 아래 1·2·5번 실측은 완료. 결과는 `docs/STATUS.md`의 "맥 검증 결과". AVD 이름 `Pleos_Connect_v2.1`, Virtual Console은 `adb forward tcp:8088 tcp:8088` 후 http://127.0.0.1:8088/ . 남은 것은 영상, release APK, 음원 재생.

## 심사 4.0을 위한 최소 실측 (11-03 7바퀴 심사 결론)

클라우드에서 할 수 있는 개선은 심사 3.55/5까지 왔고 상한은 약 3.7. 아래 4가지가 있어야 4.0권(3.85~3.95), 5번까지 하면 4.0 이상으로 판단됨.
0. ~~Playground에서 SDK 사용 신청~~ 개발·시험은 매니페스트 선언만으로 가능 (운영사무국 답변, 선행 조건 아님)
1. 와이퍼·하향등 구독 → 앱 장면이 바뀌는지 확인. 날짜, 와이퍼 단계 구분 여부, 기어 신호 출처 기록
2. 앰비언트 set P단·D단 각 1회 → 결과/에러 코드 기록 (`docs/STATUS.md`)
3. Virtual Console + 앱 화면 분할 20~30초 영상
4. release APK 설치·실행, `apk/MoodLab_MooDrive_release.apk`
5. NaviHelper ETA 1회 실측 (또는 클라우드에서 '정체 중 피날레 겹치기' 로직 추가)
