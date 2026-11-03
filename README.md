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
4. 디버그 패널의 **▶ 비 오는 퇴근길**로 시나리오 재생 (약 3분: 노을 → 비 → 폭우 → 터널 → 정체 → 도착 피날레 → 주차)

## 남은 작업

- [ ] Pleos SDK 의존성·저장소 추가 (`build.gradle.kts`, `app/build.gradle.kts`의 TODO)
- [ ] `PleosVehicleSignalSource` / `PleosAmbientController` 구현 (파일 안 TODO 순서)
- [ ] D단에서 앰비언트 set 호출이 막히는지 에뮬레이터로 시험 → `docs/STATUS.md` 기록
- [ ] 로열티프리 음원 번들 + 무드 태깅, Media3 재생
- [ ] Pleos TTS(HYBRID), STT 음성 명령
- [ ] release APK → `apk/` (제출 파일명: 영문팀명_MooDrive_release.apk)

Playground 프로젝트 Key 같은 비밀값은 `local.properties`에만 두고 커밋하지 않는다.
