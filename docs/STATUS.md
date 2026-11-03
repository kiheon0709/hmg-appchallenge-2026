# 진행 상황 (2026-11-03 갱신)

노션 메인 페이지가 원본이고, `docs/notion/`은 11-04 백업이다. 목표와 통과 기준은 `docs/GOALS.md`.

## 결정

- **아이디어**: 5번 맥락 기반 음악 추천 **MooDrive**로 확정. 2순위 카시트 달래기, 3순위 뒷좌석 놀이터. 노션 메인 할 일도 11-03에 갱신함.
- **Playground**: 프로젝트 `hmg-appchallenge-2026`, 개발사명 "기어르", 카테고리 음악/오디오. SDK 신청과 권한 화면 확인은 아직.
- **조명 정책**: 주행 중 set 허용 여부를 확인하기 전까지 실내 조명은 "정차 시 적용"이 기본값. 주행 중에는 앱 화면 무드 색으로 보여 주고, 데모 패널에서 허용 모드로 전환할 수 있음.

## 현황

| 항목 | 상태 |
|---|---|
| core 로직 | 자동 테스트 74개 통과 (`./gradlew :core:test`) |
| 데모 시나리오 | 3분 동안 장면 전환 7회(정차 제외), 곡 10개, DJ 멘트 7회 (테스트로 고정) (`./gradlew :core:demoTrace`) |
| 디자인 | 화면 3종. 자동 검사(조작 수·터치·글자·실측 대비) 1920×720, 1408×792 모두 통과. 토큰 동기화 검사 통과 |
| app (Android) | JVM 하네스 컴파일 오류 0 (`tools/app-compile-check`). 실제 Android 빌드는 맥에서 미확인 |
| 기획서 | 독립 심사 점수: v0.2 2.8 → 1바퀴 2.9 (`plan-review-v3.md`) → 2바퀴 3.1 (`plan-review-r2.md`) → 3바퀴 3.3 (`plan-review-r3.md`) → 4바퀴 3.35 (`plan-review-r4.md`) → 5바퀴 3.50 (`plan-review-r5.md`) → 6바퀴 `plan-review-r6.md`. 심사 판단: SDK·APK·영상 없이 도달 가능 3.7~3.8, 4.0+는 실측 1건 필요 |
| 음원 | 자리표시 66곡(장르마다 차분한 곡·밝은 곡 포함). 실제 음원 태깅은 `docs/music-tagging.md` 기준으로 진행 예정 |

## 10-29 접수 메일 문의 (11-03 현재 답 없음)

1. 주행 중 NO_SETUP이 Vehicle set 호출(앰비언트)까지 막는지
2. Vehicle Control 'Coming Soon' 표기의 의미
3. NaviHelper 남은 시간·정체 실시간 수신
4. 차속 획득 방법
5. 오디오 포커스·TTS 덕킹 정책
6. 2차 PT 시연 환경

## 남은 일

- **사용자 직접**: Playground에서 SDK 사용 신청(아직 안 함). 심사에서 가장 큰 감점 요인이 'SDK 연결 0%'라 11/09까지 Wiper·하향등 구독과 앰비언트 set 1회가 목표.

- 맥: `docs/MAC_HANDOFF.md` (빌드, Pleos SDK 연결, D단 앰비언트 시험, 음원, 시연 영상)
- 클라우드(다음 루프 우선순위, `docs/review/plan-review-r3.md` 기준):
  1. 사업 구조: 1단계 자체 음원만으로는 멜론 대비 쓸 이유가 약함 → AAOS MediaController로 다른 음악 앱 재생 제어가 가능한지 조사, OEM 라이선스 + 스트리밍 배분으로 수익 분리, 가격 근거
  2. 터널 오탐: 비 그친 뒤 오토라이트, 해질녘·주간 상시 전조등 → '꺼짐→켜짐 전환'만 터널로, 정차는 P단 60초 이상일 때만 도착 처리
  3. 세션 초기화: 데모 2회차에 인사·멘트 누락, 카드에 이전 주행 섞임 → `startTrip()` 초기화 + 회귀 테스트, 피날레 뒤 추가 곡 방지(피날레 큐 사용)
  4. 음성: "A 말고 B", "~지 마", "피곤해" 처리, 아무것도 검사하지 않는 테스트 1개(`tunnel entry replaces...`) 수정
  5. APK 파일명 통일(README 영문 vs 기획서 한글 팀명), 각주 [^4] 정리
- 제출 직전: 접수양식 DOCX에 옮기기, 전원 서명 동의서, 근거 원문 재확인, GitHub 심사 계정 초대

## 작업 환경 메모

- 클라우드에서는 `dl.google.com`(Android SDK·AGP), `document.pleos.ai`, `nexus-playground.pleos.ai`가 막혀 있음. 환경 설정의 허용 도메인에 추가하면 다음 세션부터 Android 빌드 가능.
- Maven Central이 가끔 429를 줌 → 30~60초 기다렸다가 다시 시도.
- Playground 프로젝트 Key는 커밋하지 말 것 (`local.properties` 사용).
