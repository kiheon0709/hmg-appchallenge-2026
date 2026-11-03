# 진행 상황 (2026-11-03 갱신)

노션 메인 페이지가 원본이고, `docs/notion/`은 11-04 백업이다. 목표와 통과 기준은 `docs/GOALS.md`.

## 결정

- **아이디어**: 5번 맥락 기반 음악 추천 **MooDrive**로 확정. 2순위 카시트 달래기, 3순위 뒷좌석 놀이터. 노션 메인 할 일도 11-03에 갱신함.
- **Playground**: 프로젝트 `hmg-appchallenge-2026`, 개발사명 "기어르", 카테고리 음악/오디오. SDK 신청과 권한 화면 확인은 아직.
- **조명 정책**: 주행 중 set 허용 여부를 확인하기 전까지 실내 조명은 "정차 시 적용"이 기본값. 주행 중에는 앱 화면 무드 색으로 보여 주고, 데모 패널에서 허용 모드로 전환할 수 있음.

## 현황

| 항목 | 상태 |
|---|---|
| core 로직 | 자동 테스트 40개 통과 (`./gradlew :core:test`) |
| 데모 시나리오 | 3분 동안 장면 전환 7회(정차 제외), 곡 9개, DJ 멘트 7회 (`./gradlew :core:demoTrace`) |
| 디자인 | 화면 3종. 자동 검사(조작 수·터치·글자·실측 대비) 1920×720, 1408×792 모두 통과. 토큰 동기화 검사 통과 |
| app (Android) | JVM 하네스 컴파일 오류 0 (`tools/app-compile-check`). 실제 Android 빌드는 맥에서 미확인 |
| 기획서 | v0.3. 독립 심사 1차(v0.2) 2.8/5 → v0.3 재심사는 `docs/review/plan-review-v3.md` |
| 음원 | 자리표시 44곡. 실제 음원 태깅은 `docs/music-tagging.md` 기준으로 진행 예정 |

## 10-29 접수 메일 문의 (11-03 현재 답 없음)

1. 주행 중 NO_SETUP이 Vehicle set 호출(앰비언트)까지 막는지
2. Vehicle Control 'Coming Soon' 표기의 의미
3. NaviHelper 남은 시간·정체 실시간 수신
4. 차속 획득 방법
5. 오디오 포커스·TTS 덕킹 정책
6. 2차 PT 시연 환경

## 남은 일

- 맥: `docs/MAC_HANDOFF.md` (빌드, Pleos SDK 연결, D단 앰비언트 시험, 음원, 시연 영상)
- 클라우드: 독립 심사 평균 4.0 이상까지 기획서·코드 보완 루프 (`docs/GOAL_PROMPT.md`)
- 제출 직전: 접수양식 DOCX에 옮기기, 전원 서명 동의서, 근거 원문 재확인, GitHub 심사 계정 초대

## 작업 환경 메모

- 클라우드에서는 `dl.google.com`(Android SDK·AGP), `document.pleos.ai`, `nexus-playground.pleos.ai`가 막혀 있음. 환경 설정의 허용 도메인에 추가하면 다음 세션부터 Android 빌드 가능.
- Maven Central이 가끔 429를 줌 → 30~60초 기다렸다가 다시 시도.
- Playground 프로젝트 Key는 커밋하지 말 것 (`local.properties` 사용).
