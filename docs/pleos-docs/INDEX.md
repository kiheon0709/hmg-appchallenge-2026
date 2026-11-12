# 인덱스 (찾기용 목차)

필요한 부분의 파일과 줄 번호를 찾은 뒤 그 부분만 읽으세요(Read의 offset/limit 또는 `sed -n '시작,끝p'`). 2026-11-12 기준 자동 생성.


## 1. 요약본 목차 (파일:줄)


### 00-contest-and-ecosystem.md

- 5: 1. 공모전에서 쓸 수 있는 것과 없는 것 (가장 중요)
- 21: 2. 심사
- 47: 3. Pleos 생태계 지도
- 64: 4. App Market 현황 (레퍼런스)

### 01-planning-policy-design.md

- 17: 0. 기획·개발 시 반드시 지킬 것 TOP 15
- 39: 1. 앱 카테고리와 매니페스트 선언
  - 41: 1.1 Pleos Connect 앱 카테고리 정의 (PG:app-category)
  - 56: 1.2 주행 중 기능이 "제한적으로 허용될 수 있는" 카테고리 (PG:app-category)
  - 66: 1.3 매니페스트 `android:appCategory` 선언 (PG:app-category)
  - 85: 1.4 App Market 쪽 카테고리 규칙 (RV:__review-app-registration-info, RV:__review-functionality-performance-stability, SUB)
- 91: 2. 주행 규제 (Driving restriction / DO / UX restriction)
  - 93: 2.1 개요 (PG:driving-restiction-policy, PG:driver-distraction-guideline, GL)
  - 103: 2.2 DO 미선언 시 기본 동작 (PG:driving-restiction-policy)
  - 110: 2.3 지역별 기능 허용 범위: 주행 규제가 적용된 상태 (PG:driving-restiction-policy)
  - 131: 2.4 주행 규제 해제 조건: InfoLINK 문서 기준 (DR-IL)
  - 142: 2.5 QR 코드 화면별 주행 중 규제 (DR-IL)
  - 156: 2.6 미디어 앱에 대한 심사 규칙 (RV:__review-driver-distraction-elements)
  - 166: 2.7 텍스트·시각 요소 제한: 디자인 가이드 (DS:Introduction__intro, DS:Introduction__designing-apps-for-driving)
  - 176: 2.8 주의분산 방지 UX 원칙 (PG:driver-distraction-guideline)
- 184: 3. 앱 구조·화면 모델, 하드웨어·SDK·호환성·보안·성능
  - 186: 3.1 화면 구성과 멀티 윈도우 (PG:app-structure-screen-model, DS:getting-started__layout, GL)
  - 198: 3.2 화면 방향·크기 (PG:app-structure-screen-model)
  - 203: 3.3 Window Policy: 디스플레이별 (DS:getting-started__window)
  - 219: 3.4 디스플레이 유형 (DS:getting-started__layout, GL)
  - 227: 3.5 포그라운드/백그라운드 동작 (PG:app-structure-screen-model)
  - 234: 3.6 하드웨어 접근 정책 (PG:platform-api-architecture)
  - 239: 3.7 SDK API 정책 (PG:pleos-connect-sdk-api)
  - 248: 3.8 API 호환성·가용성: 차종별 (PG:api-compatibility-availability)
  - 259: 3.9 보안·개인정보: 설계 단계 (PG:security-privacy)
  - 266: 3.10 성능·리소스 (PG:performance-resource-mgmt)
- 280: 4. 디자인 시스템 핵심 규칙
  - 282: 4.1 기본 원칙 (DS:Introduction__intro, DS:Introduction__designing-apps-for-driving)
  - 288: 4.2 인터페이스 수치 (DS:Introduction__intro)
  - 298: 4.3 알림 (DS:Introduction__intro, DS:components__notification__vehicle-notification, GL)
  - 303: 4.4 유연성·확장성 (DS:Introduction__intro)
  - 308: 4.5 Driving view (DS:Experiences__driving-view, GL)
  - 316: 4.6 Gleo AI 경험 (DS:Experiences__gleo-ai)
  - 334: 4.7 App library·Widgets·Theme (DS:components__app-library, DS:components__widgets, DS:Introduction__theme)
  - 340: 4.8 시스템 입력 장치 (GL)
  - 343: 4.9 텍스트 원문에 없는 디자인 수치 (확인 필요)
- 352: 5. 앱 심사 가이드 거부 사유 체크리스트
  - 356: 5.1 운전자 주의 분산 요소 (RV:__review-driver-distraction-elements, RV:.md)
  - 366: 5.2 콘텐츠 안전 (RV:__review-content-safety)
  - 380: 5.3 기능·성능·안정성 (RV:__review-functionality-performance-stability)
  - 397: 5.4 개인정보·보안 (RV:__review-user-privacy-security)
  - 409: 5.5 등록정보 (RV:__review-app-registration-info)
- 425: 6. 제출·등록·업데이트·버전·태깅·보안
  - 427: 6.1 제출 전 준비 (SUB)
  - 439: 6.2 Console 제출 항목 (SUB)
  - 465: 6.3 업데이트 관리 (SUB)
  - 470: 6.4 앱 보안 가이드 (REG:app-security-guide)
  - 519: 6.5 앱 태그 표기 (REG:app-tagging-guide)
- 527: 7. 공모전(에뮬레이터 평가) 관점 메모
- 533: 8. 문서상 모호하거나 확인이 필요한 점

### 02-dev-environment-and-guides.md

- 10: 0. 개발 시 반드시 알아야 할 것 TOP 15
- 30: 1. 개발 환경 요구사항 · 프로젝트 생성 · SDK 신청 · 의존성
  - 32: 1.1 소프트웨어 개발 환경 (원문 표)
  - 44: 1.2 그래픽 카드 사양 (에뮬레이터 실행에 GPU 필수)
  - 51: 1.3 에뮬레이터 설치 (SDK Update Sites)
  - 60: 1.4 AVD 생성
  - 72: 1.5 Pleos Playground 가입 · 프로젝트 생성 · SDK 신청
  - 88: 1.6 Gradle 설정 (원문 그대로)
  - 109: 1.7 SDK 버전 (SDK 릴리스 노트, 2026-10-19)
  - 118: 1.8 첫 API 호출 예시 (getting-started)
  - 135: 1.9 기타 (in-prep)
- 142: 2. 에뮬레이터 · Virtual Console · 폼 팩터 테스트
  - 144: 2.1 Virtual Console 개요
  - 152: 2.2 시뮬레이션 가능한 신호 (Emulation Controls 카드 전체)
  - 203: 2.3 시뮬레이션할 수 없는 것 / 문서에 없는 것
  - 212: 2.4 ADB · 포트 정리
  - 224: 2.5 폼 팩터 테스트
- 240: 3. 개발 가이드 요점
  - 244: 3.1 오디오 포커스
  - 294: 3.2 Suspend 모드
  - 304: 3.3 Music/Audio 앱 가이드 (App Market 심사 기준)
  - 337: 3.4 Maps/Navigation 앱 가이드
  - 358: 3.5 CCP(로터리 컨트롤러)
  - 378: 3.6 IME
  - 381: 3.7 주행 규제(Driving restriction) 처리 코드
  - 403: 3.8 음성 안내 선행 묵음
  - 412: 3.9 FAQ (앱 실행 환경 · 플랫폼 리소스)
- 431: 4. 릴리스 노트
  - 433: 4.1 Pleos Connect Emulator v2.1.0 (2026-10-19, 정식)
  - 442: 4.2 Pleos Playground
  - 448: 4.3 Pleos Connect SDK (2026-10-19)
  - 497: 4.4 알려진 제약 (릴리스 노트·가이드 종합)
- 507: 5. Vehicle SDK 샘플앱 튜토리얼에서 배울 코드 패턴
  - 511: 5.1 기준 환경 (원문 표)
  - 530: 5.2 표준 호출 흐름 (Door Position 기준 예제)
  - 539: 5.3 반드시 지킬 패턴
  - 549: 5.4 샘플앱 public permission 전체 목록 (참고용, 원문)
- 557: 6. Gleo Interface / App Integration, 그리고 생태계 제품의 공모전 사용 가능성
  - 559: 6.1 Gleo Interface(App Integration Guide)란
  - 581: 6.2 공모전에서 쓸 수 없는 이유
  - 589: 6.3 생태계 다른 제품 판단
- 608: 7. 문서상 모호하거나 확인이 필요한 점
- 628: 부록 (요약 작성 후 추가 수집, 2026-11-12)

### 03-sdk-usage-guides.md

- 11: 0. SDK별 한 줄 요약 + 우리가 만들 수 있는 것의 경계
- 25: 1. 공통 사항
  - 27: 1.1 Maven 저장소 / Gradle 좌표 — 출처: [시작] `docs__pleos-only__getting-started.md`, [코드] `app/build.gradle.kts`, 루트 `build.gradle.kts`
  - 48: 1.2 에뮬레이터 준비 (5개 SDK intro 공통 목록)
  - 51: 1.3 이번 수집본에 없는 가이드 페이지 (intro의 "다음 단계"에 링크만 존재)
  - 58: 1.4 퍼미션 보호 수준
- 63: 2. Vehicle SDK
  - 67: 2.1 기능 범위
  - 80: 2.2 가용성 확인 (Capability) — `Vehicle__check-capability.md`
  - 86: 2.3 초기화·해제 순서와 스레드
  - 94: 2.4 퍼미션 — `Vehicle__vehicle-permission.md`
  - 113: 2.5 에러 코드 (전부) — `Vehicle__vehicle-error-code.md`
  - 128: 2.6 최소 동작 코드 (Kotlin, 문서 예시 축약)
  - 155: 2.7 저장소 코드 대조 (Vehicle)
  - 166: 2.8 v2.3.2 마이그레이션 주의 [릴리즈]
- 175: 3. NaviHelper SDK
  - 179: 3.1 기능 범위 [문서 intro]
  - 185: 3.2 퍼미션 — `NaviHelper__declaring-permissions.md`
  - 192: 3.3 초기화·해제 순서
  - 200: 3.4 목적지 / 경로 / 경유지 API [문서 + 바이너리 시그니처]
  - 220: 3.5 이벤트 (전부) — `NaviHelper__event-listeners.md`
  - 238: 3.6 에러 코드 — `NaviHelper__error-handling.md`
  - 251: 3.7 최소 동작 코드 (Kotlin)
  - 283: 3.8 문서 내 불일치 / 저장소 대조 (NaviHelper)
- 295: 4. TextToSpeech SDK
  - 299: 4.1 기능 범위와 모드
  - 307: 4.2 퍼미션 / 앱 등록
  - 313: 4.3 초기화·해제 순서 [문서 intro]
  - 321: 4.4 합성 시작·중지 — `TextToSpeech__start-and-stop.md`
  - 329: 4.5 이벤트 — `TextToSpeech__event-listeners.md`
  - 341: 4.6 로케일 — `TextToSpeech__set-locale.md`
  - 347: 4.7 에러 코드 (전부) — `TextToSpeech__error-code.md`
  - 365: 4.8 최소 동작 코드 (Kotlin, ON_DEVICE)
  - 390: 4.9 저장소 대조 (TTS) — [코드] `app/src/main/java/io/moodlab/moodrive/audio/DjVoice.kt`
- 397: 5. SpeechToText SDK
  - 401: 5.1 기능 범위와 모드
  - 411: 5.2 퍼미션 / 앱 등록
  - 417: 5.3 초기화·해제 순서 [문서 intro]
  - 425: 5.4 인식 시작·중지 — `SpeechToText__start-and-stop.md`
  - 429: 5.5 결과 리스너 — `SpeechToText__result-listeners.md`
  - 440: 5.6 앱 PCM 전송 (sendAudio) — `SpeechToText__sending-audio-data.md`
  - 452: 5.7 로케일 — `SpeechToText__set-locale.md`
  - 455: 5.8 에러 코드 (전부) — `SpeechToText__error-code.md`
  - 473: 5.9 최소 동작 코드 (Kotlin, ON_DEVICE, 차량 마이크)
  - 496: 5.10 저장소 대조 (STT)
- 502: 6. FusedLocation SDK
  - 506: 6.1 기능 범위 [문서 intro]
  - 518: 6.2 퍼미션 — `FusedLocation__declaring-permissions.md`
  - 526: 6.3 초기화·해제 순서 [문서 intro]
  - 533: 6.4 에러 처리
  - 537: 6.5 최소 동작 코드 (Kotlin, 문서 예시)
  - 557: 6.6 저장소 대조
- 562: 7. 저장소 코드 ↔ 문서 차이 요약
- 576: 8. 문서상 모호하거나 확인이 필요한 점
- 601: 부록 (요약 작성 후 추가 수집·확인, 2026-11-12)


## 2. raw/ 원문 목차 (파일 — 제목: 소제목들)


### api-reference/fleet-api

- `api-reference__fleet-api__getting-started.md` — **Getting started**
- `api-reference__fleet-api__intro.md` — **Fleet API**

### api-reference/pleos-platform-sdk

- `api-reference__pleos-platform-sdk__compatibility-matrix.md` — **페이지를 찾을 수 없습니다.**
- `api-reference__pleos-platform-sdk__feature-matrix.md` — **페이지를 찾을 수 없습니다.**
- `api-reference__pleos-platform-sdk__get-started.md` — **페이지를 찾을 수 없습니다.**
- `api-reference__pleos-platform-sdk__intro.md` — **페이지를 찾을 수 없습니다.**
- `api-reference__pleos-platform-sdk__permission-matrix.md` — **페이지를 찾을 수 없습니다.**

### api-reference/vehicle-data-api

- `api-reference__vehicle-data-api__intro.md` — **Vehicle Data API**

### docs/connect-mobile/connect-mobile-developer-guide

- `docs__connect-mobile__connect-mobile-developer-guide__app-install-guide.md` — **앱 설치 가이드**
- `docs__connect-mobile__connect-mobile-developer-guide__connect-mobile-console-overview.md` — **Connect Mobile Console**
- `docs__connect-mobile__connect-mobile-developer-guide__rcs-mock-control-guide.md` — **Mock 차량 제어 가이드**
- `docs__connect-mobile__connect-mobile-developer-guide__register-profile-guide.md` — **프로필 등록 가이드**

### docs/connect-mobile/connect-mobile-user-guide

- `docs__connect-mobile__connect-mobile-user-guide__02-01-vehicle-shared-guide.md` — **차량 공유 가이드**
- `docs__connect-mobile__connect-mobile-user-guide__02-02-vehicle-shared-release-guide.md` — **차량 공유 해제 가이드**
- `docs__connect-mobile__connect-mobile-user-guide__03-01-vehicle-control-control-guide.md` — **차량 기본 제어 가이드**
- `docs__connect-mobile__connect-mobile-user-guide__03-02-vehicle-control-hvac-guide.md` — **공조 제어 가이드**
- `docs__connect-mobile__connect-mobile-user-guide__03-03-vehicle-control-charge-guide.md` — **충전 제어 가이드**
- `docs__connect-mobile__connect-mobile-user-guide__03-04-vehicle-control-v2l-guide.md` — **V2L 가이드**
- `docs__connect-mobile__connect-mobile-user-guide__03-05-vehicle-control-schedule-guide.md` — **예약 가이드**
- `docs__connect-mobile__connect-mobile-user-guide__03-06-vehicle-control-convenience-guide.md` — **편의 기능 가이드**
- `docs__connect-mobile__connect-mobile-user-guide__qr-vehicle-registration-guide.md` — **QR 차량 등록 가이드**
- `docs__connect-mobile__connect-mobile-user-guide__unavailable-features-guide.md` — **미지원 및 제한 기능**

### docs/connect-mobile/release-note

- `docs__connect-mobile__release-note__connect-mobile-1.0.0.md` — **Page Not Found**

### docs/connect/guide

- `docs__connect__guide__connect.md` — **Pleos Connect**
- `docs__connect__guide__docs-design__Experiences__driving-view.md` — **Driving view**
- `docs__connect__guide__docs-design__Experiences__gleo-ai.md` — **Gleo AI**
- `docs__connect__guide__docs-design__Introduction__design-token.md` — **Design Token**
- `docs__connect__guide__docs-design__Introduction__designing-apps-for-driving.md` — **Introduction**
- `docs__connect__guide__docs-design__Introduction__intro.md` — **Pleos Connect UI Design Guide**
- `docs__connect__guide__docs-design__Introduction__theme.md` — **Theme**
- `docs__connect__guide__docs-design__components__actions__basic-button.md` — **Basic button**
- `docs__connect__guide__docs-design__components__actions__icon-button.md` — **Icon button**
- `docs__connect__guide__docs-design__components__actions__keypad.md` — **Keypad**
- `docs__connect__guide__docs-design__components__actions__text-button.md` — **Text button**
- `docs__connect__guide__docs-design__components__actions__toggle-button.md` — **Toggle button**
- `docs__connect__guide__docs-design__components__app-library.md` — **App library**
- `docs__connect__guide__docs-design__components__bar.md` — **Bar**
- `docs__connect__guide__docs-design__components__controllers.md` — **Controllers**
- `docs__connect__guide__docs-design__components__dropdown.md` — **Dropdown**
- `docs__connect__guide__docs-design__components__fields__number-field.md` — **Number Field**
- `docs__connect__guide__docs-design__components__fields__text-field.md` — **Text Field**
- `docs__connect__guide__docs-design__components__gnb.md` — **Global Navigation Bar(GNB)**
- `docs__connect__guide__docs-design__components__indicator.md` — **Indicator**
- `docs__connect__guide__docs-design__components__notification__system-notification.md` — **System Notification**
- `docs__connect__guide__docs-design__components__notification__vehicle-notification.md` — **Vehicle Notification**
- `docs__connect__guide__docs-design__components__page-navigation.md` — **Page Navigation**
- `docs__connect__guide__docs-design__components__popups__system-popup.md` — **System Popup**
- `docs__connect__guide__docs-design__components__popups__toast-popup.md` — **Toast Popup**
- `docs__connect__guide__docs-design__components__segmented-menu.md` — **Tabs**
- `docs__connect__guide__docs-design__components__selections__checkbox.md` — **Checkbox**
- `docs__connect__guide__docs-design__components__selections__radio.md` — **Radio button**
- `docs__connect__guide__docs-design__components__selections__switches.md` — **Switch**
- `docs__connect__guide__docs-design__components__spinner.md` — **Spinner**
- `docs__connect__guide__docs-design__components__status-bar.md` — **Status bar**
- `docs__connect__guide__docs-design__components__toast.md` — **Toast**
- `docs__connect__guide__docs-design__components__widgets.md` — **Widgets**
- `docs__connect__guide__docs-design__foundations__animation.md` — **Animation**
- `docs__connect__guide__docs-design__foundations__colors.md` — **Colors**
- `docs__connect__guide__docs-design__foundations__contents-creation__3d-contents.md` — **3D Assets**
- `docs__connect__guide__docs-design__foundations__contents-creation__app-icon.md` — **App Icon**
- `docs__connect__guide__docs-design__foundations__contents-creation__iconography.md` — **Iconography**
- `docs__connect__guide__docs-design__foundations__interaction.md` — **Interaction**
- `docs__connect__guide__docs-design__foundations__shadow.md` — **Shadow**
- `docs__connect__guide__docs-design__foundations__typography.md` — **Typography**
- `docs__connect__guide__docs-design__getting-started__layout.md` — **Display Layout**
- `docs__connect__guide__docs-design__getting-started__window.md` — **Window Policy**
- `docs__connect__guide__getting-started__application-development__configure-pleos-connect-emulator.md` — **Pleos Connect Emulator 설정**
- `docs__connect__guide__getting-started__application-development__setup-connect-sdk-emulator.md` — **Pleos Connect Emulator 설치 및 실행**
- `docs__connect__guide__getting-started__application-development__use-vehicle-emulator.md` — **Pleos Connect Virtual Console 셋업 가이드**
- `docs__connect__guide__glossary.md` — **Glossary**

### docs/pleos-only

- `docs__pleos-only__connect-sdk-pleos.md` — **Overview**
- `docs__pleos-only__connect-sdk-service-status.md` — **Pleos Connect SDK 문서 안내**
- `docs__pleos-only__create-my-project.md` — **프로젝트 생성 및 SDK 사용 신청**
- `docs__pleos-only__getting-started.md` — **Pleos Connect 앱 개발 시작하기**

### docs/pleos-only/app-integration-guide

- `docs__pleos-only__app-integration-guide__guide__action_callback.md` — **Callback Schema Writing Guide**
- `docs__pleos-only__app-integration-guide__guide__app-capability.md` — **App Schema Writing Guide**
- `docs__pleos-only__app-integration-guide__guide__core-concepts.md` — **Gleo Interface**
- `docs__pleos-only__app-integration-guide__guide__deliver-schema.md` — **Delivering the Schema to Gleo**
- `docs__pleos-only__app-integration-guide__guide__examples.md` — **Code Details with Examples**
- `docs__pleos-only__app-integration-guide__use-cases__radio-app.md` — **How to Integrate Radio App with Gleo AI**

### docs/pleos-only/app-registration-guide

- `docs__pleos-only__app-registration-guide__app-security-guide.md` — **앱 보안 가이드**
- `docs__pleos-only__app-registration-guide__app-tagging-guide.md` — **앱 태그 표기 가이드**
- `docs__pleos-only__app-registration-guide__app-update-guide.md` — **앱 업데이트 가이드**
- `docs__pleos-only__app-registration-guide__app-version-management-policy.md` — **앱 버전 관리 정책**

### docs/pleos-only/app-test-guide

- `docs__pleos-only__app-test-guide__form-factor-test-guide.md` — **폼 팩터 테스트 가이드**
- `docs__pleos-only__app-test-guide__virtual-console-guide.md` — **Pleos Connect Virtual Console 사용 가이드**

### docs/pleos-only/connect-sdk-pleos

- `docs__pleos-only__connect-sdk-pleos__FusedLocation__declaring-permissions.md` — **Declaring permission**
- `docs__pleos-only__connect-sdk-pleos__FusedLocation__entry.md` — **FusedLocation**
- `docs__pleos-only__connect-sdk-pleos__FusedLocation__error-handling.md` — **오류 처리**
- `docs__pleos-only__connect-sdk-pleos__FusedLocation__initialization-and-release.md` — **Initialization / Release**
- `docs__pleos-only__connect-sdk-pleos__FusedLocation__intro.md` — **Introduction**
- `docs__pleos-only__connect-sdk-pleos__FusedLocation__location-data-access.md` — **위치 데이터 접근**
- `docs__pleos-only__connect-sdk-pleos__FusedLocation__project-setup.md` — **Importing FusedLocation SDK**
- `docs__pleos-only__connect-sdk-pleos__LLM__entry.md` — **LLM**
- `docs__pleos-only__connect-sdk-pleos__NaviHelper__declaring-permissions.md` — **Declaring permissions**
- `docs__pleos-only__connect-sdk-pleos__NaviHelper__destinations.md` — **Destinations**
- `docs__pleos-only__connect-sdk-pleos__NaviHelper__entry.md` — **NaviHelper**
- `docs__pleos-only__connect-sdk-pleos__NaviHelper__error-handling.md` — **Error handling**
- `docs__pleos-only__connect-sdk-pleos__NaviHelper__event-listeners.md` — **Event listeners**
- `docs__pleos-only__connect-sdk-pleos__NaviHelper__importing-sdk.md` — **Importing NaviHelper SDK**
- `docs__pleos-only__connect-sdk-pleos__NaviHelper__initialization-and-release.md` — **Initialization**
- `docs__pleos-only__connect-sdk-pleos__NaviHelper__intro.md` — **Introduction**
- `docs__pleos-only__connect-sdk-pleos__NaviHelper__route-with-waypoints.md` — **Route requests with waypoints**
- `docs__pleos-only__connect-sdk-pleos__NaviHelper__routes.md` — **Routes**
- `docs__pleos-only__connect-sdk-pleos__NaviHelper__waypoint-settings.md` — **Waypoints**
- `docs__pleos-only__connect-sdk-pleos__SpeechToText__app-registration.md` — **App registration**
- `docs__pleos-only__connect-sdk-pleos__SpeechToText__declaring-permissions.md` — **Declaring permissions**
- `docs__pleos-only__connect-sdk-pleos__SpeechToText__entry.md` — **SpeechToText**
- `docs__pleos-only__connect-sdk-pleos__SpeechToText__error-code.md` — **Error code**
- `docs__pleos-only__connect-sdk-pleos__SpeechToText__importing-sdk.md` — **Importing SpeechToText SDK**
- `docs__pleos-only__connect-sdk-pleos__SpeechToText__initialization-and-release.md` — **Initialization**
- `docs__pleos-only__connect-sdk-pleos__SpeechToText__intro.md` — **Introduction**
- `docs__pleos-only__connect-sdk-pleos__SpeechToText__result-listeners.md` — **Event listeners**
- `docs__pleos-only__connect-sdk-pleos__SpeechToText__sending-audio-data.md` — **Sending audio data**
- `docs__pleos-only__connect-sdk-pleos__SpeechToText__set-locale.md` — **Set locale**
- `docs__pleos-only__connect-sdk-pleos__SpeechToText__start-and-stop.md` — **Start and stop recognition**
- `docs__pleos-only__connect-sdk-pleos__TextToSpeech__app-registration.md` — **App registration**
- `docs__pleos-only__connect-sdk-pleos__TextToSpeech__declaring-permissions.md` — **Declaring permissions**
- `docs__pleos-only__connect-sdk-pleos__TextToSpeech__entry.md` — **TextToSpeech**
- `docs__pleos-only__connect-sdk-pleos__TextToSpeech__error-code.md` — **Error code**
- `docs__pleos-only__connect-sdk-pleos__TextToSpeech__event-listeners.md` — **Event listeners**
- `docs__pleos-only__connect-sdk-pleos__TextToSpeech__importing-sdk.md` — **Importing TextToSpeech SDK**
- `docs__pleos-only__connect-sdk-pleos__TextToSpeech__initialization-and-release.md` — **Initialization**
- `docs__pleos-only__connect-sdk-pleos__TextToSpeech__intro.md` — **Introduction**
- `docs__pleos-only__connect-sdk-pleos__TextToSpeech__set-locale.md` — **Set locale**
- `docs__pleos-only__connect-sdk-pleos__TextToSpeech__start-and-stop.md` — **Start and stop synthesis**
- `docs__pleos-only__connect-sdk-pleos__Vehicle__callback.md` — **Callback**
- `docs__pleos-only__connect-sdk-pleos__Vehicle__check-capability.md` — **Capability**
- `docs__pleos-only__connect-sdk-pleos__Vehicle__entry.md` — **Vehicle**
- `docs__pleos-only__connect-sdk-pleos__Vehicle__get-property.md` — **Get**
- `docs__pleos-only__connect-sdk-pleos__Vehicle__intro.md` — **Introduction**
- `docs__pleos-only__connect-sdk-pleos__Vehicle__obtain-domain-object.md` — **Obtaining a domain object**
- `docs__pleos-only__connect-sdk-pleos__Vehicle__project-setup.md` — **Importing Vehicle SDK**
- `docs__pleos-only__connect-sdk-pleos__Vehicle__set-property.md` — **Set**
- `docs__pleos-only__connect-sdk-pleos__Vehicle__vehicle-error-code.md` — **Error code**
- `docs__pleos-only__connect-sdk-pleos__Vehicle__vehicle-permission.md` — **Declaring permission**

### docs/pleos-only/distribute

- `docs__pleos-only__distribute__app-review-guide.md` — **App Review Guidelines**
- `docs__pleos-only__distribute__app-review-guide__review-app-registration-info.md` — **Pleos App Market 등록정보**
- `docs__pleos-only__distribute__app-review-guide__review-content-safety.md` — **콘텐츠 안전성**
- `docs__pleos-only__distribute__app-review-guide__review-driver-distraction-elements.md` — **운전자 주의 분산 요소 최소화 설계**
- `docs__pleos-only__distribute__app-review-guide__review-functionality-performance-stability.md` — **앱 기능 및 성능 안정성**
- `docs__pleos-only__distribute__app-review-guide__review-user-privacy-security.md` — **사용자 개인정보 및 보안**
- `docs__pleos-only__distribute__app-submission-guide.md` — **앱 심사 제출**

### docs/pleos-only/in-prep

- `docs__pleos-only__in-prep___payment-system-migration.md` — **결제 시스템 및 외부 라이브러리 마이그레이션**

### docs/pleos-only/infolink-emulator

- `docs__pleos-only__infolink-emulator__driving-restiction-policy.md` — **지역별 주행 규제 정책 (InfoLINK 추가 구현 항목)**
- `docs__pleos-only__infolink-emulator__setup-infolink-emulator.md` — **페이지를 찾을 수 없습니다.**

### docs/pleos-only/release-note

- `docs__pleos-only__release-note__emulator-release-note.md` — **Pleos Connect Emulator 정식 릴리즈**
- `docs__pleos-only__release-note__playground-release-note.md` — **Pleos Playground**
- `docs__pleos-only__release-note__sdk-release-note.md` — **Pleos Connect SDK 릴리즈 노트**

### docs/pleos-only/tutorial

- `docs__pleos-only__tutorial__vehicle-sdk-sample-app.md` — **Vehicle SDK Sample App 사용 가이드**

### docs/pleos-only/vehicle-app-developer-guide

- `docs__pleos-only__vehicle-app-developer-guide__adb-command-reference-guide.md` — **FAQ - 디버깅을 위한 주요 ADB Command 활용법**
- `docs__pleos-only__vehicle-app-developer-guide__audio-focus-development-guide__audio-focus-development.md` — **오디오 포커스 개발 가이드**
- `docs__pleos-only__vehicle-app-developer-guide__audio-focus-development-guide__suspend-mode-audio-focus.md` — **Suspend 모드 - 오디오 포커스 개발 가이드**
- `docs__pleos-only__vehicle-app-developer-guide__ccp-rotary-controller-development.md` — **CCP 개발 가이드**
- `docs__pleos-only__vehicle-app-developer-guide__driving-restriction-development-guide.md` — **주행 규제 개발 가이드**
- `docs__pleos-only__vehicle-app-developer-guide__ime-window-resize-handling-guide.md` — **IME 동작에 따른 window resize 처리 가이드**
- `docs__pleos-only__vehicle-app-developer-guide__intro.md` — **Pleos Connect 앱 개발 가이드**
- `docs__pleos-only__vehicle-app-developer-guide__music-audio-app-developement-guide.md` — **Music/Audio 앱 개발 가이드**
- `docs__pleos-only__vehicle-app-developer-guide__navigation-app-development-guide.md` — **Maps/Navigation 앱 개발 가이드**
- `docs__pleos-only__vehicle-app-developer-guide__vehicle-app-faq.md` — **FAQ - 앱 실행 환경 및 플랫폼 리소스**
- `docs__pleos-only__vehicle-app-developer-guide__voice-guidance-preroll-silence-guide.md` — **TTS / 음성 안내 선행 묵음 적용 가이드**

### docs/pleos-only/vehicle-app-planning-guide

- `docs__pleos-only__vehicle-app-planning-guide__api-compatibility-availability.md` — **API 호환성 및 가용성**
- `docs__pleos-only__vehicle-app-planning-guide__app-category.md` — **앱 카테고리**
- `docs__pleos-only__vehicle-app-planning-guide__app-structure-screen-model.md` — **앱 구조 및 동작 모델**
- `docs__pleos-only__vehicle-app-planning-guide__driver-distraction-guideline.md` — **주행 안전을 고려한 설계**
- `docs__pleos-only__vehicle-app-planning-guide__driving-restiction-policy.md` — **지역별 주행 규제 정책**
- `docs__pleos-only__vehicle-app-planning-guide__intro.md` — **Pleos Connect 앱 설계 가이드**
- `docs__pleos-only__vehicle-app-planning-guide__performance-resource-mgmt.md` — **성능 및 리소스 효율을 고려한 설계**
- `docs__pleos-only__vehicle-app-planning-guide__platform-api-architecture.md` — **하드웨어 접근 정책**
- `docs__pleos-only__vehicle-app-planning-guide__pleos-connect-sdk-api.md` — **SDK API 정책**
- `docs__pleos-only__vehicle-app-planning-guide__security-privacy.md` — **보안 및 개인정보 보호를 고려한 설계**

### product-ai-navigation

- `product-ai-navigation.md` — **(제목 없음)**

### product-ambient-ai

- `product-ambient-ai.md` — **(제목 없음)**

### product-appmarket

- `product-appmarket.md` — **(제목 없음)**

### product-atria

- `product-atria.md` — **(제목 없음)**

### product-common-platform

- `product-common-platform.md` — **(제목 없음)**

### product-connect-mobile

- `product-connect-mobile.md` — **(제목 없음)**

### product-dga-guide

- `product-dga-guide.md` — **(제목 없음)**

### product-fleet

- `product-fleet.md` — **(제목 없음)**

### product-gleoai

- `product-gleoai.md` — **(제목 없음)**

### product-guidelines

- `product-guidelines.md` — **(제목 없음)**

### product-ivios

- `product-ivios.md` — **(제목 없음)**

### product-maps

- `product-maps.md` — **(제목 없음)**

### product-mediaserver

- `product-mediaserver.md` — **(제목 없음)**

### product-openapi

- `product-openapi.md` — **(제목 없음)**

### product-pleos-platform-sdk

- `product-pleos-platform-sdk.md` — **(제목 없음)**

### product-pleos-standard

- `product-pleos-standard.md` — **(제목 없음)**

### product-sdvos

- `product-sdvos.md` — **(제목 없음)**

### product-sync

- `product-sync.md` — **(제목 없음)**

### product-universal-wallet

- `product-universal-wallet.md` — **(제목 없음)**

### product-vdp

- `product-vdp.md` — **(제목 없음)**


## 3. reference/ API 목차 (클래스 → 함수 이름, 원문 줄 번호)
enum(constant)·listener·data 클래스는 생략. `grep -n '^### <class-kebab>' reference/<SDK>.md`로 찾기. 예: WiperState → `### wiper-state`


상세 시그니처는 `reference/<SDK>.md`에서 `^#### <함수명 kebab-case>`로 grep. 예: setAmbientLightBrightnessColor → `#### set-ambient-light-brightness-color`


### Vehicle.md

- 17: `vehicle` **class Vehicle(context: Context)** — getBrake getCarInfo getDisplay getDoor getDrivingMode getEvBattery getFuel getHVAC getLight getOdometer getSafety getSeat getSideMirror getSteeringWheel getTire getToll getTurnSignal getWindow getWiper initialize release
- 542: `vehicle.api` **interface Brake** [CAR_DRIVE] — 기능: BrakePedalStatus
- 614: `vehicle.api` **interface CarInfo** [CAR_INFO] — 기능: CRNInfo, VINInfo, CRN, VIN
- 698: `vehicle.api` **interface Display** [CAR_INFO] — 기능: TemperatureDisplayUnit
- 770: `vehicle.api` **interface Door** [CAR_DOORS, CONTROL_CAR_DOORS] — 기능: DoorLock, DoorPosition, GloveBoxLock, Door
- 1060: `vehicle.api` **interface DrivingMode** [CAR_DRIVE] — 기능: CruiseControlState, CruiseControlTargetSpeed, DrivingModeState, CruiseControl, DrivingMode
- 1322: `vehicle.api` **interface EvBattery** [CAR_ENERGY, CONTROL_CAR_ENERGY] — 기능: CumulativeEvEfficiency, CurrentEvEfficiency, EvBatteryStateOfCharge, EvChargeCurrentDrawLimit, EvChargePercentLimit, EvChargePortOpen, EvChargeSwitch, InstantaneousEvEfficiency, SinceChargeEvEfficiency, EvEfficiency, EvCharge
- 1854: `vehicle.api` **interface Fuel** [CAR_ENERGY] — 기능: CumulativeFuelEconomy, CurrentFuelEconomy, FuelPercent, InstantaneousFuelEconomy, SinceChargeFuelEconomy, FuelEconomy
- 2192: `vehicle.api` **interface HVAC** [CAR_CLIMATE, CONTROL_CAR_CLIMATE] — 기능: HvacAc, HvacAirCleaner, HvacAuto, HvacAutoRecirculation, HvacDefroster, HvacDual, HvacFanDirection, HvacFanSpeed, HvacMaxAc, HvacMaxDefrost, HvacPower, HvacRecirculation, HvacTemperature, HvacTemperatureCurrent
- 3614: `vehicle.api` **interface Light** [CAR_LIGHTS, CONTROL_CAR_LIGHTS] — 기능: AmbientColorAutoByDriveModeEnabled, AmbientLightBrightness, AmbientLightBrightnessColor, AmbientLightState, AmbientLightSwitch, CabinLightState, CabinLightSwitch, FrontFogLightState, FrontFogLightSwitch, HighBeamLightState, HighBeamLightSwitch, LowBeamLightState, LowBeamLightSwitch, ReadingLightState, ReadingLightSwitch, RearFogLightState, RearFogLightSwitch, TailLampLightState, TailLampLightSwitch, AmbientLight, CabinLight, FrontFogLight, HighBeamLight, LowBeamLight, ReadingLight, RearFogLight, TailLampLight
- 4901: `vehicle.api` **interface Odometer** [CAR_DRIVE, CONTROL_CAR_DRIVE] — 기능: CurrentDistanceDriven, TripCumulativeDrivingDistance, TripCumulativeDrivingTime, TripCurrentDrivingDistance, TripCurrentDrivingTime, TripDrivingDistance, TripDrivingTime, TripInstantaneousDrivingDistance, TripSinceRefillDrivingDistance, TripSinceRefillDrivingTime, DistanceDriven, RangeRemainingDistance
- 5496: `vehicle.api` **interface Safety** [CAR_SAFETY, PRIVATE_CAR_SAFETY] — 기능: Airbag, AirbagDeployed, BrakeFluidLevelState, EngineOilLevelState, LowWasherFluidWarningState, PowerDownWarningIndicator, SeatBuckled, SeatNotBuckledWarning, Telltales, TransmissionOilLevelState, BrakeFluidLevel, EngineOilLevel, TransmissionOilLevel, LowWasherFluidWarning, SeatAirbagBeenDeployed, SeatAirbagEnabled, SeatBeltNotBuckledWarning, LowWasherFluidWaring, SeatAirbagDeployed
- 6125: `vehicle.api` **interface Seat** [CAR_SEATS, CONTROL_CAR_SEATS] — 기능: MoveSeatBackrestAngle, MoveSeatBeltHeight, MoveSeatDepth, MoveSeatForeAft, MoveSeatHeadrestAngle, MoveSeatHeadrestForeAft, MoveSeatHeadrestHeight, MoveSeatHeight, MoveSeatLumbarForeAft, MoveSeatLumbarSideSupport, MoveSeatTilt, SeatBackrestAngle, SeatBackrestAnglePosition, SeatBeltBuckled, SeatBeltHeight, SeatBeltHeightPosition, SeatDepth, SeatDepthPosition, SeatForeAft, SeatForeAftPosition, SeatHeadrestAngle, SeatHeadrestAnglePosition, SeatHeadrestForeAft, SeatHeadrestForeAftPosition, SeatHeadrestHeight, SeatHeadrestHeightPosition, SeatHeight, SeatHeightPosition, SeatLumbarForeAft, SeatLumbarForeAftPosition, SeatLumbarSideSupport, SeatLumbarSideSupportPosition, SeatMemory, SeatOccupancy, SeatTemperature, SeatTilt, SeatTiltPosition, SeatVentilation, SeatHeadrestHeightAft, HvacSeatTemperature, HvacSeatVentilation
- 7854: `vehicle.api` **interface SideMirror** [CAR_CLIMATE, CAR_MIRRORS, CONTROL_CAR_MIRRORS] — 기능: HvacSideMirrorHeat
- 7961: `vehicle.api` **interface SteeringWheel** [CAR_CLIMATE, CAR_DRIVE, CONTROL_CAR_CLIMATE] — 기능: HvacSteeringWheelHeat, SteeringWheelAngle
- 8124: `vehicle.api` **interface Tire** [CAR_SAFETY] — 기능: LowTirePressure, WheelArea
- 8181: `vehicle.api` **interface Toll** [CAR_TOLL, CONTROL_CAR_TOLL] — 기능: EtcsVoiceGuidanceVolume
- 8272: `vehicle.api` **interface TurnSignal** [CAR_LIGHTS] — 기능: TurnSignalState, TurnSignal
- 8344: `vehicle.api` **interface Window** [CAR_WINDOWS, CONTROL_CAR_WINDOWS] — 기능: MoveWindowBlind, SmartGlassLevel, SmartGlassState, SmartGlassZoneState, WindowBlindPosition, WindowLock, WindowPosition, SmartGlass, WindowBlind, Window, SmartGlassConfig, SmartGlassZone
- 8892: `vehicle.api` **interface Wiper** [CAR_WINDOWS, CONTROL_CAR_WINDOWS] — 기능: WiperState, WiperSwitch, Wiper

### NaviHelper.md

- 3617: `navi.helper` **class NaviHelper(context: Context)** [NAVI_CUSTOM_MAP, NAVI_ROUTE, NAVI_ROUTE_SEARCH] — addListener addWaypoint cancelRoute changeRouteOption getBookmarkInfo getChargerOperatorInfo getCurrentLocationInfo getDestinationInfo getRecentDestinationInfo getRouteStateInfo getTBTInfo getWaypointInfo initialize release removeListener removeWaypoint requestNavigationScreen requestReRoute requestRoute requestRouteWithWaypoints requestTrip

### TextToSpeech.md

- 17: `tts` **class TextToSpeech(context: Context, mode: Mode = Mode.HYBRID)** [TTS_SERVICE] — addEventListener initialize registerApp release removeEventListener setAudioAttributes setAudioZoneId setLocale setSpeakerType speak stop

### SpeechToText.md

- 17: `stt` **class SpeechToText(context: Context, mode: Mode = Mode.HYBRID)** [STT_SERVICE] — addListener completeAudioSend initialize registerApp release removeListener request sendAudio setLocale stop

### FusedLocation.md

- 16: `location.fused` **class FusedLocation(context: Context)** [FUSED_LOCATION] — addProximityAlert getLastKnownLocation initialize isSupported registerFusedLocationCallback release removeProximityAlert showLocationAccessBlockedPopup unregisterFusedLocationCallback

