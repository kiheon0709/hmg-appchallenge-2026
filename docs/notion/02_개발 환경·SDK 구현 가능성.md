<!-- Notion 백업 (2026-11-04). 원본: https://app.notion.com/p/4083aab6534181a8a77ac80bb85fbded · 최종 수정 2026-10-29 -->

# 🛠️ 개발 환경·SDK 구현 가능성

> ✅ **결론: 테슬라와 달리 꽤 열려 있음.** AAOS 기반에 19개 도메인을 get/set 둘 다 공개. 단, 카메라·센서 직접 접근은 금지, 제어(set)는 배포 시 승인제, 차종별 지원 범위 다름.

## 기본 구조

- **Pleos Connect** = 현대차그룹 차량용 인포테인먼트 플랫폼. **Android Automotive OS(AAOS)** 기반.
- 결과물은 **APK**. 개발은 Android Studio, Kotlin/Java.
- 스토어는 Google Play가 아니라 **현대차 자체 Pleos App Market**. 심사 기준도 자체(주행 규제, 퍼미션 승인).
- 폰 앱 아님. 폰↔차 연동 앱도 범위 밖. 차 디스플레이에 직접 설치되는 앱.

## 개발 환경 요구사항

| 항목 | 버전 |
| --- | --- |
| Min SDK | Android API 28 이상 (차량 보안 요건) |
| Gradle | 8.0 이상 |
| 언어 | Java, Kotlin |
| 에뮬레이터 | Pleos Connect Emulator 2.1.0 (API 34, Form Factor: Automotive) |
| Virtual Console | 차량 신호 시뮬레이션 (기어, 도어, 착좌 등) |

### 에뮬레이터 설치 순서

1. Android Studio > Tools > SDK Manager > SDK Update Sites > + 추가
2. Name: Pleos Connect System Image
3. URL (Mac): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
4. SDK Platforms > API 34 > Pleos Connect System Image 체크 → 설치 → 재시작
5. Device Manager > Create device > Form Factor **Automotive** > Pleos Connect v2.1.0
6. Advanced: Default boot **Cold**, RAM **4GB**
7. Virtual Console 셋업, 마이크 접근 설정

## SDK 구성

| SDK | 역할 |
| --- | --- |
| Vehicle SDK | 차량 상태 조회·제어 (19개 도메인) |
| NaviHelper SDK | 목적지·경유지 설정, 경로 재탐색, 주행 상태 이벤트 |
| TextToSpeech / SpeechToText | 음성 입출력 |
| FusedLocation SDK | 차량 위치 |

Vehicle 도메인: Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, HVAC, Light, Odometer, Safety, Seat, SideMirror, SteeringWheel, Tire, Toll, TurnSignal, Window, Wiper

## 아이디어에 쓸 핵심 API (실제 메서드 확인함)

| 필요 기능 | 실제 API | 판정 |
| --- | --- | --- |
| 앰비언트 색·밝기 | `setAmbientLightBrightnessColor`, `setAmbientLightBrightness` | ✅ |
| 공조 온도·풍량·풍향·내외기 | `setHvacTemperature`, `setHvacFanSpeed`, `setHvacFanDirection`, `setHvacRecirculation` | ✅ |
| 뒷좌석 착좌 감지 | `getCurrentSeatOccupancy`, `registerSeatOccupancy` | ✅ |
| 시트 각도·열선·통풍 | `moveSeatBackrestAngle`, `setHvacSeatTemperature`, `setHvacSeatVentilation` | ✅ |
| 실내등·독서등 | `setCabinLightSwitch`, `setReadingLightSwitch` | ✅ |
| 브레이크·깜빡이·경고 | Brake, TurnSignal, Safety 도메인 get | ✅ |
| 카메라, CO₂ 등 센서 직접 접근 | — | ❌ 금지 |
| 뒷좌석 전용 스피커 출력 | — | ❌ 없음 |

## 퍼미션

- 조회: `pleos.car.permission.CAR_*` / 제어: `CONTROL_CAR_*`
- 예: Door → `CAR_DOORS`, HVAC → `CAR_CLIMATE`, Light·TurnSignal → `CAR_LIGHTS`, Seat → `CAR_SEATS`, DrivingMode·Brake·Odometer → `CAR_DRIVE`
- **개발 단계에선 승인 없이 매니페스트 선언만으로 사용 가능** (설명회 확인). 승인은 App Market 배포 심사 때.
- 모든 API에 `checkXXXCapabilities` 있음 → 차종별 지원 여부 확인 후 fallback 설계 필수.

## 주행 중 규제 (가장 중요)

- **DO(Distraction Optimization) 선언 안 하면 주행 중 앱 자체가 안 열림.**
- 한국(그외 지역) 기준 주행 중: 키보드·검색 ✅ / **동영상 ⛔** / **시트·도어·차량 설정 ⛔ (NO_SETUP)** / 대용량 텍스트 ⛔
- ⚠️ NO_SETUP이 설정 **UI**만 막는지 API **set 호출**까지 막는지 문서상 불명확 → 문의 필요

| 카테고리 | 주행 중 허용 예시 |
| --- | --- |
| Music/Audio | 스트리밍, 오디오북, 단순 조작, 앨범아트 |
| Maps/Navigation | 경로 안내, TBT 음성, 간결한 지도 |
| Mobility Services | 충전소 찾기, 주차장 위치, 차량 관리 |
| Kids / Entertainment / Social 등 | 별도 허용 범위 명시 없음 → 음성 위주 설계 |

## 기타 정책

- 차량 하드웨어 직접 접근 금지, 플랫폼 API로만.
- Telephony, SMS, 일부 센서(자이로·근접), 캘린더, 주소록 제한될 수 있음.
- 주행 중 네트워크 끊김 대비: 캐싱, 재시도, 오프라인 안내 UI 권장.
- 외부 API·퍼블릭 네트워크 연결 가능 (설명회 확인) → LLM API, 날씨 API 사용 OK.

## 문서 링크

- [Pleos Playground](https://pleos.ai/playground)
- [에뮬레이터 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [SDK Overview](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/)
- [Vehicle SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro) · [퍼미션](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)
- [NaviHelper SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)
- [주행 규제 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy) · [앱 카테고리](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category)
- [프로젝트 생성·SDK 신청](https://document.pleos.ai/docs/pleos-only/create-my-project)
