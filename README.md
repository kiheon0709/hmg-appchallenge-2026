# MooDrive (무드라이브)

와이퍼·조명·주행모드 등 차량 신호로 음악과 앰비언트 라이트를 자동으로 맞춰주는 인카 무드 DJ 앱.
현대자동차그룹 차량용 앱 개발 공모전(Pleos Connect) 출품작 · 팀 기어르(MoodLab)

## 구조

| 모듈 | 내용 | 빌드 |
|---|---|---|
| `core/` | 순수 Kotlin. 컨텍스트 엔진(F1), 선곡·취향(F2), 앰비언트 계획(F3), 도착 피날레(F4), 시나리오 플레이어 | 어디서나 `./gradlew :core:test` |
| `app/` | Android Automotive 앱(Compose). 화면, 실내 조명 시각화, 디버그 패널, Pleos SDK 어댑터 | Android SDK가 있을 때만 포함됨 |

```
VehicleSignalSource(Pleos / 시나리오 / 수동) ─▶ MooDriveSession ─▶ Action
                                               ├ ContextEngine  → Scene + Mood
                                               ├ Curator/Taste  → Play
                                               ├ AmbientPlanner → SetAmbient (주행 중 보류 정책)
                                               └ DJ 멘트         → Speak (일반 멘트 5분 간격, 안전 멘트는 예외)
```

## 실행 (맥)

1. Android Studio에서 저장소 열기 (`local.properties`의 `sdk.dir`이 잡히면 `app` 모듈이 자동 포함)
2. Pleos Connect Emulator 2.1.0 (API 34, Automotive) 실행 → `docs/notion/02_개발 환경·SDK 구현 가능성.md` 설치 순서 참고
3. `./gradlew :app:installDebug` 또는 Run
4. 첫 실행 시 장르 3개 선택 → 주행 화면 왼쪽 위 기어 칩(P)을 **길게 눌러** 데모 패널을 열고 **▶ 비 오는 퇴근길**로 시나리오 재생 (약 3분: 노을 → 비 → 폭우 → 터널 → 정체 → 도착 피날레 → 주차)

## APK와 테스트 환경

- APK: `apk/MoodLab_MooDrive_release.apk`
- 테스트 환경: Pleos Connect Emulator 2.1.0 (API 34, Automotive), Vehicle SDK 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1
- 차량 신호 조작: `adb forward tcp:8088 tcp:8088` 후 http://127.0.0.1:8088/ (Pleos Connect Virtual Console). Gear D에서 Windshield Wiper Switch를 Low·High로 바꾸면 장면·곡·DJ 멘트가 바뀐다
- 실측 결과: `docs/STATUS.md`의 "맥 검증 결과"
- 시연 음원은 `tools/demo-music/generate.py`로 합성한 것이다

## 남은 작업

- [ ] Pleos STT 음성 명령
- [ ] FusedLocation 일몰·터널 GPS, 휴게소 경유지 추가
- [ ] 실제 음원 교체 (`docs/music-tagging.md`)

Playground 프로젝트 Key, release 서명 키와 비밀번호는 `local.properties`와 로컬 파일에만 두고 커밋하지 않는다.
