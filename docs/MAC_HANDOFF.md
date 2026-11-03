# 맥 로컬 세션 작업 지시서

맥의 Claude Code(데스크톱 앱 또는 터미널 `claude`)에서 이 저장소 폴더를 열고 아래를 그대로 붙여 넣는다.

---

이 저장소는 Pleos Connect(Android Automotive) 공모전 앱 MooDrive다. README.md와 docs/STATUS.md를 먼저 읽어라.
브랜치 `main-bpn5uz`를 체크아웃하고 아래 순서로 진행한 뒤 결과를 docs/STATUS.md의 "맥 검증 결과" 절에 기록하고 커밋·푸시해라.

1. `./gradlew :core:test` 통과 확인.
2. `local.properties`에 `sdk.dir`이 있는지 확인(없으면 Android Studio SDK 경로로 추가, 커밋 금지).
3. `./gradlew :app:assembleDebug` 실행. app 모듈은 클라우드에서 한 번도 컴파일되지 않았다. 컴파일 오류를 고쳐라. 로직 수정은 core 테스트가 계속 통과하는 범위에서만.
4. Pleos Connect Emulator 2.1.0(API 34, Automotive)을 띄우고 `./gradlew :app:installDebug` 후 앱 실행. 디버그 패널의 "▶ 비 오는 퇴근길"을 눌러 장면·조명 미리보기·곡이 바뀌는지 확인하고 스크린샷을 `docs/screens/`에 저장.
5. Pleos Playground > My Project > SDK 탭에서 Vehicle SDK 의존성 좌표·저장소 주소를 확인해 build.gradle.kts / app/build.gradle.kts의 TODO를 채워라. Playground 프로젝트 Key는 절대 커밋하지 말고 local.properties에 둬라.
6. `PleosAmbientController`에 setAmbientLightBrightnessColor 연결 → Virtual Console에서 기어를 P와 D로 바꿔 가며 호출이 성공/거부되는지 확인하고 결과(에러 메시지 포함)를 기록. 이것이 접수 메일 1번 질문의 실측 답이다.
7. 가능하면 `PleosVehicleSignalSource`에서 Wiper·LowBeam 구독을 먼저 연결.

막히는 지점(로그인, SDK 신청 승인 대기 등)은 억지로 우회하지 말고 STATUS.md에 적고 멈춰라.

## 11-03 이후 추가된 확인 항목

- app 코드는 클라우드에서 JVM 하네스(`tools/app-compile-check`)로 컴파일 오류 0까지 확인함. AGP·리소스·매니페스트 처리는 미확인이라 3번 빌드에서 처음 드러날 수 있음.
- 첫 실행 시 온보딩(장르 3개) → 주행 화면. 데모 패널은 왼쪽 위 기어 칩을 **길게 눌러** 연다.
- `Action.AddRestStop`(휴게소 수락) 처리부는 TODO: NaviHelper로 가까운 휴게소 검색 → 경유지 추가.
- 주행/정차 판정: 기어를 어느 Vehicle 도메인에서 주는지 확인. 없으면 FusedLocation 속도 5km/h 기준으로 대체하고 결과를 STATUS.md에 기록.
- 와이퍼 강도 단계(INT/LOW/HIGH)가 API에 있는지 확인. 없으면 폭우 장면은 비 장면으로 합침.
- 음성 인식·TTS가 에뮬레이터에서 동작하는지 확인(매니페스트 `<queries>` 선언 포함됨).
