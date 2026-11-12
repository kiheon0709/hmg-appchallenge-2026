# 03. Pleos Connect SDK 사용 가이드 정리 (Vehicle · NaviHelper · TextToSpeech · SpeechToText · FusedLocation)

> 대상 독자: 이 저장소에서 코드를 작성하는 AI 코딩 에이전트/개발자.
> 1차 근거: `docs/pleos-docs/raw/docs__pleos-only__connect-sdk-pleos__*.md` (공식 사용 가이드, 41개 파일 전부 확인).
> 근거 표기: **[문서]** 사용 가이드 원문 · **[릴리즈]** `raw/docs__pleos-only__release-note__sdk-release-note.md` (2026-10-19) · **[시작]** `raw/docs__pleos-only__getting-started.md` · **[바이너리]** Gradle 캐시의 실제 AAR을 `javap`로 확인한 값(문서 아님, 참고용) · **[코드]** 이 저장소 코드.
> 전체 API 카탈로그(클래스·필드 전부)는 별도 문서에서 다룬다. 여기서는 "어떻게 쓰는가 / 어디까지 되는가"에 집중한다.
> LLM SDK: `LLM__entry.md`는 제목(`# LLM`)뿐인 빈 페이지이며, 공모전 참가팀에게 제공되지 않는다(`contest/notice-08_개발범위_기술문의.md`).

---

## 0. SDK별 한 줄 요약 + 우리가 만들 수 있는 것의 경계

| SDK (가이드 기준 버전) | 한 줄 요약 | 할 수 있는 것 | 할 수 없는 것 / 경계 | 매니페스트 퍼미션 | 앱 등록(Client ID/Secret) |
|---|---|---|---|---|---|
| **Vehicle** v2.3.2 (Vehicle Service v2.3.21 기준) | 도메인 객체(Door, Light, HVAC…)로 차량 상태 조회·구독·제어 | 19개 도메인의 capability 확인 / get / set / callback(구독) | 주행 중 제한 기능은 `ERROR_DRIVING_RESTRICTED`로 거부. 차종·영역별 미지원 가능(capability로 확인). 결과는 전부 비동기 | `pleos.car.permission.CAR_*`(조회), `pleos.car.permission.CONTROL_CAR_*`(제어) | 불필요 |
| **NaviHelper** v3.0.5 | Pleos Connect Navigation 앱에 목적지·경유지·경로 요청 + 주행/경로 이벤트 수신 | 목적지 설정/확인/취소, 경로옵션 7종 변경, 재탐색, 경유지 최대 2개, 경유지 포함 경로, 남은 거리·시간·TBT·속도제한·카메라·주의지점·충전 정보 등 이벤트, Navigation 화면 요청 | 지도를 직접 그리거나 경로를 계산하지 않음(Navigation 앱이 처리). 정보 조회 API는 **값을 반환하지 않고** 콜백으로만 옴. 실제 전달되는 이벤트는 Navigation 앱 구현·상태에 따라 다름 | `pleos.car.permission.NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `NAVI_CUSTOM_MAP` | 불필요 |
| **TextToSpeech** v3.3.1 | 텍스트를 합성해 **차량 오디오 시스템으로 직접 재생** | `speak(text, isFinal)`(조각 스트리밍 가능), `stop()`, 로케일 5종, 재생 이벤트(준비/시작/RMS/완료/중지/오류) | 합성된 오디오 바이트를 앱이 받는 API는 가이드에 없음(재생은 Speech Service가 수행). HYBRID/SERVER는 앱 등록 필요 | `pleos.car.permission.TTS_SERVICE` | ON_DEVICE만 쓰면 **불필요**. HYBRID(기본)·SERVER는 필요 |
| **SpeechToText** v3.3.1 | 차량 마이크(또는 앱 PCM)를 텍스트로 변환 | `request()`로 차량 마이크 인식, 중간/최종 결과, EPD, RMS, `sendAudio()`로 앱 PCM 인식(ON_DEVICE 전용), 로케일 5종 | **명령 실행 없음**(텍스트만 반환. 의도 해석은 앱이 직접). Gleo Interface는 공모전 팀 사용 불가. 차량 마이크 음성을 외부 STT로 보내는 것은 금지 → 이 SDK만 사용 | `pleos.car.permission.STT_SERVICE` | ON_DEVICE만 쓰면 **불필요**. HYBRID(기본)·SERVER는 필요 |
| **FusedLocation** v1.1.3 | 차량 위치(LocationData) 1회 조회·연속 수신·근접 알림 | `getLastKnownLocation()`, `registerFusedLocationCallback()`, `addProximityAlert()`/`removeProximityAlert()`, `isSupported()`, `showLocationAccessBlockedPopup()` | 새 위치를 강제로 계산하는 API 없음(마지막 위치만, null 가능). 개인정보 설정으로 차단되면 데이터 없음(팝업은 안내만, 허용 아님). 갱신 주기·정확도 옵션은 가이드에 없음 | `pleos.car.permission.FUSED_LOCATION` (**보호 수준 dangerous**) | 불필요 |

공통 경계(공모전): 위 5개 SDK만 사용 가능. "문서에서 지원되지 않는 기능은 사용할 수 없습니다"(notice-08). 심사는 Pleos Connect 에뮬레이터 시연.

---

## 1. 공통 사항

### 1.1 Maven 저장소 / Gradle 좌표 — 출처: [시작] `docs__pleos-only__getting-started.md`, [코드] `app/build.gradle.kts`, 루트 `build.gradle.kts`

```kotlin
// settings.gradle.kts (문서 예시) — 이 저장소는 루트 build.gradle.kts의 repositories에 같은 URL을 둠
maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
```

| SDK | 좌표 | 근거 |
|---|---|---|
| Vehicle | `ai.pleos.playground:Vehicle:2.3.2` | [코드]·[바이너리] 캐시 존재. ※ [시작] 문서 예시는 `:2.3.1`(가이드 본문 v2.3.2와 불일치) |
| NaviHelper | `ai.pleos.playground:NaviHelper:3.0.5` | [코드]·[바이너리] |
| TextToSpeech | `ai.pleos.playground:TextToSpeech:3.3.1` | [코드]·[바이너리] |
| SpeechToText | `ai.pleos.playground:SpeechToText:3.3.1` **(추정, 미검증)** | "Importing SpeechToText SDK" 페이지 미수집. TTS 명명 규칙에서 유추 |
| FusedLocation | **artifactId 미확인** (버전 1.1.3) | "FusedLocation SDK 가져오기" 페이지 미수집 |

전이 의존성 [바이너리]: Vehicle → `vehicle-aidl:1.1.1`, `vehicle-data:1.1.11` / NaviHelper → `navi-aidl:3.0.1`, `navi-data:3.0.4` / TTS → `ai.pleos.playground.security:universal-access:0.6.0`(STT 가이드의 "UA 인증"과 관련 추정).

AAR이 자동으로 병합하는 매니페스트 항목 [바이너리, merged manifest]: `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` 퍼미션, `<queries>`의 `ai.pleos.playground.vehicle.VEHICLE_START` / `ai.pleos.playground.caas.intent.action.SPEECH_START` / `ai.pleos.playground.intent.action.NAVI_START`, TTS의 `android.permission.INTERNET`, 최소 서비스 버전 meta-data(Vehicle Service ≥ 2.3.13, CAAS(Speech) ≥ 4.1.29, Navi helper service ≥ 2.0.1). → 앱에서 직접 선언할 필요 없음.

개발 환경 요구사항(FAQ): Android API 28 이상, Gradle 8.0 이상, Java/Kotlin.

### 1.2 에뮬레이터 준비 (5개 SDK intro 공통 목록)
소프트웨어 요구사항 확인 → Pleos Connect Emulator 설치 → AVD 생성·실행 → Pleos Connect Virtual Console 설정 → **마이크 접근 설정**(STT 테스트용: Extended Controls > Microphone에서 오디오 입력 수신 허용, `configure-pleos-connect-emulator.md`) → **Cold boot**(quick boot면 VHAL 사용 불가).

### 1.3 이번 수집본에 없는 가이드 페이지 (intro의 "다음 단계"에 링크만 존재)
- 5개 SDK 모두의 "SDK 가져오기(Importing)" 페이지
- NaviHelper / TTS / STT / FusedLocation의 "SDK 초기화 및 해제" 페이지
- FusedLocation "오류 처리" 페이지
- 각 SDK API Reference(별도 카탈로그 담당)
→ 아래에서 이 페이지들에 해당하는 내용은 다른 가이드 페이지·릴리즈노트·바이너리로 보완했고, 그 경우 근거를 명시했다.

### 1.4 퍼미션 보호 수준
문서에 보호 수준이 명시된 것은 **`pleos.car.permission.FUSED_LOCATION` = dangerous** 하나뿐이다(→ 런타임 권한 요청 필요). CAR_*/CONTROL_CAR_*/NAVI_*/TTS_SERVICE/STT_SERVICE의 보호 수준은 가이드에 없다. [코드] 매니페스트 주석: "개발 단계에선 선언만으로 사용 가능, 설명회 확인"(2차 근거).

---

## 2. Vehicle SDK

출처: `Vehicle__intro.md`, `Vehicle__obtain-domain-object.md`, `Vehicle__check-capability.md`, `Vehicle__get-property.md`, `Vehicle__set-property.md`, `Vehicle__callback.md`, `Vehicle__vehicle-error-code.md`, `Vehicle__vehicle-permission.md`, `Vehicle__entry.md`(빈 제목)

### 2.1 기능 범위
- 진입점 `Vehicle` → `getXxx()`로 도메인 객체를 얻음(직접 생성 불가).
- 도메인 19개 [문서]: Brake(브레이크 페달·주차 브레이크 조회), CarInfo(외부 크기·제조사·연식 등), Display(온도 단위 등 표시 단위 조회/설정), Door(열림/닫힘·잠금 제어·조회), DrivingMode(드라이브 모드·크루즈 컨트롤 조회), EvBattery(배터리·충전 상태·전비), Fuel(연비 등), HVAC(온도·풍량·습도 등 제어·조회), Light(조명 상태 조회, 앰비언트 라이트 등 제어), Odometer(총 주행거리·트립 거리/시간·단위), Safety(경고 정보), Seat(위치·각도·기능 제어·조회), SideMirror(열선 조회/설정), Steeringwheel(상태·열선), Tire(상태), Toll(ETCS 음성 안내 음량 등), TurnSignal(방향지시등 상태), Window(제어·상태), Wiper(제어·상태).
- [바이너리] `Vehicle`에는 위 목록에 없는 **`getGear()`**(`getCurrentGearSelectionState`, `registerGearSelection`/`unregisterGearSelection`)도 존재한다. 가이드 도메인 표에는 Gear가 없으므로 API Reference 게재 여부를 확인한 뒤 사용 여부를 결정할 것(§2.9 참조).
- 네 가지 API 타입 [문서 `obtain-domain-object`]:

| 타입 | 하는 일 | 결과 전달 |
|---|---|---|
| Capability `checkXxxCapabilities` | 지원 여부·허용 범위 | `onComplete(config, areaMap)` / `onFailed` |
| Get `getCurrentXxx` | 현재 값 조회 | `onComplete(값)` / `onFailed` |
| Set `setXxx` | 값 설정 | `onFailed`(실패 시) |
| Callback `registerXxx`/`unregisterXxx` | 상태 변화 구독 | 리스너 `onXxxUpdated` / `onFailed` |

### 2.2 가용성 확인 (Capability) — `Vehicle__check-capability.md`
- `CapabilityConfig.accessType`: `AccessType.NONE`, `READ`, `WRITE`, `READ_WRITE`.
- `AreaBounds`: 영역별 `minValue`/`maxValue`. `Map<Area, AreaBounds>`: 영역(예: `DoorArea`)별 범위.
- "반환 형태는 API마다 다르다" — 예: [코드] `checkAmbientLightBrightnessColorCapabilities`는 영역 **리스트**를 반환(`areas?.firstOrNull()`), `checkWiperStateCapabilities`는 `(config, _)`.
- 권장 흐름: capability → READ/READ_WRITE이면 get/register, NONE·WRITE만이면 UI 비활성 또는 대체 흐름.

### 2.3 초기화·해제 순서와 스레드
```
Vehicle(context) → initialize() → getXxx() → capability/get/set/register → unregister → release()
```
- 차량 통신은 IPC → 결과가 즉시 반환되지 않고 비동기 람다로 옴. **비동기 콜백이 끝나기 전에 `release()` 호출 금지** [문서].
- `initialize()`에는 완료 콜백이 없다([바이너리] `initialize()` 단일 시그니처). 연결 실패는 이후 API의 `ERROR_SERVICE_CONNECTION_FAILURE`로 드러남. [코드]는 첫 capability 호출의 `onComplete`를 "연결됨" 신호로 사용.
- 콜백 스레드는 문서에 명시 없음. [코드]는 바인더 스레드로 올 수 있다고 보고 `MutableStateFlow.update`로만 상태를 바꾼다(권장).

### 2.4 퍼미션 — `Vehicle__vehicle-permission.md`
형식: `pleos.car.permission.{상수명}`. `CAR_*` = 조회(get), `CONTROL_CAR_*` = 제어(set). 세부 API마다 다를 수 있으니 각 함수의 `@RequiresPermission` 확인.

| 도메인 | 조회 퍼미션(android:name) |
|---|---|
| Door | `pleos.car.permission.CAR_DOORS` |
| Window, Wiper | `pleos.car.permission.CAR_WINDOWS` |
| HVAC | `pleos.car.permission.CAR_CLIMATE` |
| DrivingMode, Brake, Odometer | `pleos.car.permission.CAR_DRIVE` |
| EvBattery, Fuel | `pleos.car.permission.CAR_ENERGY` |
| CarInfo, Display | `pleos.car.permission.CAR_INFO` |
| Light, TurnSignal | `pleos.car.permission.CAR_LIGHTS` |
| SideMirror | `pleos.car.permission.CAR_MIRRORS` |
| Safety, Tire | `pleos.car.permission.CAR_SAFETY` |
| Seat | `pleos.car.permission.CAR_SEATS` |
| Toll | `pleos.car.permission.CAR_TOLL` |

제어 예: `CONTROL_CAR_DOORS`(문서 예시). [바이너리] `PleosPermission`에는 `CONTROL_CAR_{CLIMATE,DOORS,ENERGY,MIRRORS,SEATS,WINDOWS,INFO,TOLL,DRIVE,LIGHTS,SAFETY}`와 문서에 없는 `PRIVATE_CAR_*` 11종도 정의돼 있음. 퍼미션 누락 시 `onFailed`(VehicleException이면 `ERROR_SECURITY_PERMISSION`).

### 2.5 에러 코드 (전부) — `Vehicle__vehicle-error-code.md`
`onFailed`는 Kotlin 표준 `Exception`. `VehicleException`이면 `errorCode`(enum `ai.pleos.playground.vehicle.constant.ErrorCode`)로 분기. **모든 onFailed가 VehicleException은 아님.**

| ErrorCode | 설명 | 권장 처리 | 숫자 id [바이너리, 문서 미기재] |
|---|---|---|---|
| `ERROR_NOT_SUPPORTED_PROPERTY` | 해당 속성(기능) 미지원 | UI 비활성·대체 흐름 | 60001 |
| `ERROR_NOT_SUPPORTED_AREA` | 해당 영역 미지원 | 영역 선택 변경 | 60003 |
| `ERROR_NOT_SUPPORTED_VALUE_TYPE` | 지원하지 않는 값 타입 | 파라미터·타입 재확인 | 60002 |
| `ERROR_SECURITY_PERMISSION` | 퍼미션 부족 | Permission·@RequiresPermission 확인 | 800001 |
| `ERROR_SERVICE_CONNECTION_FAILURE` | Vehicle 서비스 연결 실패 | initialize()·에뮬레이터 상태 확인 | 40001 |
| `ERROR_INTERNAL_SERVICE` | 내부 서비스 오류 | 재시도·일시 오류 안내 | 30001 |
| `ERROR_DRIVING_RESTRICTED` | 주행 중 기능 제한 | 주행 제한 안내 | 50001 |
| `ERROR_EMPTY_RESULT` | 결과 없음 | 빈 상태 UI 처리 | 70001 |
| `ERROR_UNDEFINED` | 정의되지 않은 오류 | 로그·재시도 | 99999 |

### 2.6 최소 동작 코드 (Kotlin, 문서 예시 축약)
```kotlin
val vehicle = Vehicle(context).apply { initialize() }
val door = vehicle.getDoor()
val area = DoorArea(DoorValue.ROW_1_LEFT)

door.checkDoorPositionCapabilities(
    onComplete = { config, areaMap ->
        if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) {
            val bounds = areaMap[area] // bounds?.minValue / maxValue
            door.getCurrentDoorPosition(area, onComplete = { pos: Int? -> }, onFailed = { e -> })
        }
    },
    onFailed = { e -> /* (e as? VehicleException)?.errorCode */ },
)
door.setDoorPosition(area = area, value = 1, onFailed = { e -> })

val listener = object : DoorPositionListener {
    override fun onPositionUpdated(pos: Int?) {}
    override fun onFailed(e: Exception) {}
}
door.registerDoorPosition(doorPositionListener = listener)
// ...
door.unregisterDoorPosition(doorPositionListener = listener)
vehicle.release() // 진행 중 콜백이 끝난 뒤
```

### 2.7 저장소 코드 대조 (Vehicle)
| 항목 | 문서 | 저장소/바이너리 실제 | 영향 |
|---|---|---|---|
| `VehicleException` 타입 | `e is VehicleException`, `e.errorCode` | [바이너리] 공개 이름의 `VehicleException` 클래스가 없음. `ai.pleos.playground.vehicle.callback.exception.a`(난독화)이고 ErrorCode 접근자도 `a()`로 난독화. [코드] 주석도 "컴파일 시점에 참조할 수 없다" | 문서 예시 그대로는 컴파일 불가. [코드]의 `getMethod("getErrorCode")` 리플렉션은 **항상 null이 될 가능성이 높음** → 실제로는 메시지 비교(`"...: 50001"`)에만 의존하고 있음 |
| 주행 제한 판별 | `ERROR_DRIVING_RESTRICTED` | [코드] 에뮬레이터 실측: P단이 아닐 때 set이 일반 Exception, 메시지 끝 `50001`(2026-11-07) | 50001 = `ERROR_DRIVING_RESTRICTED` id와 일치 [바이너리] |
| Set 결과 | `onFailed`만 | [코드] Light set은 `onComplete = { _, value -> }`도 받음 | 일부 set API는 완료 콜백 있음(API별 확인) |
| 기어 | 가이드에 Gear 도메인 없음 | [코드] AAOS `android.car` `CarPropertyManager`(`GEAR_SELECTION`, `android.car.permission.CAR_POWERTRAIN`)로 읽음. [바이너리] `Vehicle.getGear()` 존재 | 공모전 "5개 SDK만" 규정과 충돌 소지. API Reference에 Gear가 있으면 Vehicle SDK로 교체 검토 |
| 버전 | 가이드 v2.3.2, 시작 문서 2.3.1 | [코드] 2.3.2 | OK |
| CruiseControl | [릴리즈] OFF/READY/ACTIVE 제거 → OTHER, ENABLED, ACTIVATED, USER_OVERRIDE, SUSPENDED, FORCED_DEACTIVATION_WARNING | [코드] `CruiseControl.ACTIVATED` 사용 | OK |
| 퍼미션 | — | [코드] CAR_LIGHTS, CONTROL_CAR_LIGHTS, CAR_CLIMATE, CAR_DRIVE, CAR_WINDOWS | Wiper=CAR_WINDOWS, DrivingMode=CAR_DRIVE 표와 일치 |

### 2.8 v2.3.2 마이그레이션 주의 [릴리즈]
- `checkHvacFanSpeedCapabilities` 콜백 값 `Map<SeatArea, List<Int>>` → `Map<SeatArea, AreaBounds<Int>>`.
- `FanDirectionType` 3 → 8개(UNKNOWN·복합 풍향 포함), ordinal 변경.
- Area 클래스들(DoorArea, MirrorArea, SeatArea, TurnSignalArea, WheelArea, WindowArea) 내용 기반 equals/hashCode.
- 기존 capability API는 `CapabilityConfig`를 함께 주는 새 오버로드 권장(구형 deprecated).
- 삭제: `getHvacTemperatureValueSuggestion`, `VehicleSpeedListener`(차속 리스너 없음). Deprecated: `getCRNInfo`→`getVINInfo`, `checkWheelAreaCapabilities`→`checkLowTirePressureCapabilities`.

---

## 3. NaviHelper SDK

출처: `NaviHelper__intro.md`, `NaviHelper__declaring-permissions.md`, `NaviHelper__destinations.md`, `NaviHelper__routes.md`, `NaviHelper__waypoint-settings.md`, `NaviHelper__route-with-waypoints.md`, `NaviHelper__event-listeners.md`, `NaviHelper__error-handling.md`, `NaviHelper__entry.md`(빈 제목)

### 3.1 기능 범위 [문서 intro]
목적지 설정·확인·취소 / 경로 옵션 변경·재탐색 / 경유지 추가·조회·제거 / 경유지 포함 경로 요청 / 경로 탐색·주행 상태 이벤트 수신 / 내비 정보 조회 및 Navigation 화면 요청.
- 요청은 Pleos Connect **Navigation 앱**으로 전달되어 처리됨. 실제 이벤트는 Navigation 앱 구현·현재 상태에 따라 달라질 수 있음.
- `requestRoute()`가 정상 반환돼도 탐색 완료가 아님 → 이벤트로 확인. **정보 조회 API(`get*Info()`)는 값을 직접 반환하지 않음**(void) → 대응 콜백에서 처리.
- 경로 옵션 변경·재탐색은 **활성 경로가 있을 때만** 요청.

### 3.2 퍼미션 — `NaviHelper__declaring-permissions.md`
| Permission | 주요 API |
|---|---|
| `pleos.car.permission.NAVI_ROUTE` | `get*Info()`, `requestTrip()` |
| `pleos.car.permission.NAVI_ROUTE_SEARCH` | `requestRoute()`, `requestRouteWithWaypoints()`, `cancelRoute()`, `requestReRoute()`, `addWaypoint()`, `removeWaypoint()`, `changeRouteOption()` |
| `pleos.car.permission.NAVI_CUSTOM_MAP` | `requestNavigationScreen()` |

### 3.3 초기화·해제 순서
```
NaviHelper(context) → initialize(onConnected, onFailed) → (onConnected 안에서) addListener → 요청/조회 → removeListener → release()
```
- `initialize()`(무인자)와 `initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit)` 둘 다 존재([문서 error-handling], [릴리즈] v3.0.5 신규, [바이너리]). 문서 권장: **onConnected에서 리스너 등록과 API 호출**.
- 리스너 제거는 등록 때와 **동일 인스턴스**로. SDK 해제 전 제거.
- `NaviHelperEventListener`의 모든 메서드는 default 구현 [바이너리] → 필요한 콜백만 override.

### 3.4 목적지 / 경로 / 경유지 API [문서 + 바이너리 시그니처]
| 메서드 | 결과 콜백 | 비고 |
|---|---|---|
| `requestRoute(RouteInfo)` | `onRouteStarted(RouteStartInfo)`, `onRoutingCompleted()`, `onDestinationChanged()`, `onError(NaviErrorCode)` | 재설정도 같은 메서드 |
| `getDestinationInfo()` | `onDestinationInfo(DestinationInfo)` | |
| `cancelRoute()` | `onRouteCancelled()`, `onDestinationChanged()` (추정: 콜백 이름 기반) | |
| `changeRouteOption(RouteOption)` | `onRouteOptionChanged(RouteOption)` | 활성 경로 필요 |
| `requestReRoute()` | `onReRouteEnded()` (추정) | 현재 위치·목적지 기준 |
| `addWaypoint(RequestWaypointInfo)` | `onWaypointChanged(WaypointChangedInfo)` (추정) | 최대 2개(`WaypointIndex.FIRST`/`SECOND`) |
| `removeWaypoint(WaypointIndex)` | `onWaypointChanged(...)` (추정) | `WaypointIndex.ALL` = 전부 삭제 |
| `getWaypointInfo()` | `onWayPointInfo(List<WaypointInfo>)` | |
| `requestRouteWithWaypoints(RouteWithWaypointsRequest(destination: RouteInfo, waypoints: List<RequestWaypointInfo>))` | `onRouteStarted`, `onRoutingCompleted`, `onError` | NAVI_ROUTE_SEARCH |
| `requestTrip(TripRequest)` | `onTripInfo(TripInfo)` [추정] | NAVI_ROUTE. 가이드에 사용법 없음 |
| `requestNavigationScreen(NavigationScreenRequest)` | — | NAVI_CUSTOM_MAP. 가이드에 사용법 없음 |
| `getBookmarkInfo()`, `getRecentDestinationInfo()`, `getRouteStateInfo()`, `getCurrentLocationInfo()`, `getTBTInfo()`, `getChargerOperatorInfo()` | 각각 `onBookmarkInfo`, `onRecentDestinationInfo`, `onRouteStateInfo`, `onCurrentLocationInfo`, `onTBTInfo`, `onChargerOperatorInfo` | [바이너리] 메서드명. 문서는 `get*Info()`로만 표기 |

`RouteOption` (전부): `RECOMMENDED`(추천), `SHORT_DISTANCE`(최단 거리), `SHORT_TIME`(최단 시간), `FREE`(무료 도로 우선), `HIGHWAY`(고속도로 우선), `COMFORTABLE`(편안한 경로), `SCHOOL_ZONE`(스쿨존 고려). 최초 설정 시 `RouteInfo.routeOption`으로도 지정 가능.

`RouteInfo` 필드 [바이너리 getter 순서]: `latitude`, `longitude`, `displayLatitude?`, `displayLongitude?`, `poiName`, `poiId`, `address`, `poiSubId`, `routeOption`, `poiProvider`, `mobileRequest`. `RequestWaypointInfo`: `latitude`, `longitude`, `poiName`, `waypointIndex`, `poiId`, `address`, `poiSubId`, `poiProvider`.

### 3.5 이벤트 (전부) — `NaviHelper__event-listeners.md`
| 분류 | 콜백 |
|---|---|
| 경로 탐색 | `onRouteStarted()`, `onRoutingCompleted()`, `onRouteCancelled()`, `onReRouteEnded()`, `onRouteOptionChanged()` |
| 목적지와 경유지 | `onDestinationChanged()`, `onDestinationArrived()`, `onWaypointChanged()`, `onWayPointArrived()` |
| 정보 조회 | `onBookmarkInfo()`, `onBookMarkChanged()`, `onRecentDestinationInfo()`, `onCurrentLocationInfo()`, `onDestinationInfo()`, `onWayPointInfo()`, `onRouteStateInfo()`, `onTBTInfo()`, `onChargerOperatorInfo()`, `onTripInfo()`, `onNavigationStatus()`, `onDrivingInfo()`, `onCurrentRoadSpeedLimit()`, `onCameraAlert()`, `onCautionPointInfo()` |
| 오류 | `onError()` |
| (deprecated) | `onRouteEnded()` → `onRoutingCompleted()` 사용 권장 [릴리즈] |

"남은 시간·거리" 등 주행 정보가 들어 있는 곳 [바이너리 필드, 단위는 가이드에 없음]:
- `DestinationInfo`: `remainDistance`, `remainTime`, `tollFare`, 좌표, `name`, `address`, POI. (v3.0.5에서 `totalDistance/totalTime` → `remainDistance/remainTime` [릴리즈])
- `WaypointInfo`: `remainDistance`, `remainTime`, 이름·주소·좌표·POI.
- `DrivingInfo`: `destination: DrivingState`, `waypoints: List<DrivingState>`; `DrivingState`: `name`, `duration`, `distance`, `remainDestinationChargingValue`, `chargingStation`, `recommendedChargingStation`, `maxChargingPower`, `targetSoc`, `chargerCount`, `chargingPlan`.
- `TripInfo`: `distance`, `eta`, `estimatedSocAtDest`, `energyConsumption`, `summary`.
- `RouteStateInfo`: `termsAgreement`, `naviMode`, `routing`, `routeOption`, `remainDestinationChargingInt`, `remainWaypointChargingInt`, `trafficStatus`.
- `TBTInfo`(리스트): `type: TurnType`, `distance: Double`, `description`.
- `onCurrentRoadSpeedLimit(Int)`, `onCameraAlert(List<CameraAlert>)`, `onCautionPointInfo(List<...>)`, `onNavigationStatus(NavigationStatus)` — `NavigationStatus`: `INACTIVE`, `INITIALIZING`, `FREE_DRIVE`, `ROUTE_DRIVE` [바이너리].

### 3.6 에러 코드 — `NaviHelper__error-handling.md`
초기 연결 실패 → `initialize(onFailed = { throwable -> })`. Navigation 앱 처리 오류 → `NaviHelperEventListener.onError(errorCode: NaviErrorCode)`.

| NaviErrorCode | 설명(문서) |
|---|---|
| `CAUSE_LOW_BATTERY` | 배터리가 부족합니다. |
| `CAUSE_SEARCH_ROUTE_OVER_DISTANCE` | 경로 탐색 가능 거리를 초과했습니다. |
| `CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE` | 현재 위치와 목적지 사이의 거리가 너무 짧습니다. |
| `CAUSE_SEARCH_ROUTE` | 경로 탐색 서버에서 오류가 발생했습니다. |
| `CAUSE_SEARCH_TRIP` | **[바이너리]에만 존재, 가이드 미기재**(requestTrip 실패로 추정) — `when`에 `else`/해당 분기 필요 |

추가 점검 항목 [문서]: SDK 연결 완료 후 호출했는지 / 퍼미션 선언 / 좌표·POI ID·경유지 인덱스 / 현재 Navigation 앱 상태에서 처리 가능한지.

### 3.7 최소 동작 코드 (Kotlin)
```kotlin
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

val naviHelper = NaviHelper(context)
val listener = object : NaviHelperEventListener {
    override fun onRouteStarted(routeStartInfo: RouteStartInfo) {}
    override fun onRoutingCompleted() {}
    override fun onDestinationInfo(destinationInfo: DestinationInfo) { /* remainDistance, remainTime */ }
    override fun onWayPointInfo(waypointInfo: List<WaypointInfo>) {}   // 대문자 P 주의
    override fun onError(errorCode: NaviErrorCode) {}
}
naviHelper.initialize(
    onConnected = {
        naviHelper.addListener(listener)
        naviHelper.requestRoute(RouteInfo(
            latitude = 37.48544722, longitude = 127.03636666,
            poiName = "42dot", poiId = "36khp37hh8gqcm5nqhe5", poiSubId = "0",
            address = "서울시 강남구 남부순환로 2621",
        ))
    },
    onFailed = { t -> /* 연결 실패 안내 */ },
)
// 경유지: naviHelper.addWaypoint(RequestWaypointInfo(..., waypointIndex = WaypointIndex.FIRST, ...))
// 옵션:   naviHelper.changeRouteOption(RouteOption.HIGHWAY)
// 정리:   naviHelper.removeListener(listener); naviHelper.release()
```
**Kotlin은 반드시 이름 있는 인자 사용**(아래 3.8의 좌표 순서 문제 때문).

### 3.8 문서 내 불일치 / 저장소 대조 (NaviHelper)
| 항목 | 내용 |
|---|---|
| Java 예시 좌표 순서 | [릴리즈] v3.0.5에서 생성자 좌표 순서가 `longitude, latitude` → `latitude, longitude`로 변경. 그런데 `destinations`·`routes`·`waypoint-settings`의 **Java 예시는 여전히 `127.036…, 37.485…`(경도 먼저)**이고 끝에 `RouteOption.RECOMMENDED`를 위치 인자로 넣음. `route-with-waypoints`의 Java 예시만 위도 먼저. → 구버전 예시로 판단, 따르지 말 것 |
| 콜백 이름 | `waypoint-settings` Kotlin 예시는 `onWaypointInfo`로 override하지만 실제 인터페이스는 `onWayPointInfo` [바이너리, 이벤트 표, Java 예시]. 예시 그대로면 "overrides nothing" 컴파일 오류 |
| 경유지 결과 | 문서 예시는 `addWaypoint` 후 `onWayPointInfo`를 받는 것처럼 썼지만, `onWayPointInfo`는 `getWaypointInfo()`의 결과. 추가/삭제 결과는 `onWaypointChanged`일 것으로 추정(문서 미명시) |
| 초기화 [코드] | `PleosVehicleSignalSource.startNavi()`는 `initialize()`(무인자) 직후 곧바로 `addListener` — 문서 권장(onConnected 후 등록)과 다름. 연결 전 등록이 유실되는지 미확인 → `initialize(onConnected, onFailed)`로 바꾸는 것을 권장 |
| 주행 정보 [코드] | `onDrivingInfo` → `drivingInfo.destination?.duration`, `onTripInfo` → `tripInfo.eta`를 **초 단위로 가정**(`/60`). 단위는 가이드에 없음 → 에뮬레이터 로그로 확인 필요 |
| 퍼미션 [코드] | NAVI_ROUTE, NAVI_ROUTE_SEARCH 선언. NAVI_CUSTOM_MAP 미선언(화면 요청 안 쓰면 정상) |

---

## 4. TextToSpeech SDK

출처: `TextToSpeech__intro.md`, `TextToSpeech__declaring-permissions.md`, `TextToSpeech__app-registration.md`, `TextToSpeech__event-listeners.md`, `TextToSpeech__set-locale.md`, `TextToSpeech__start-and-stop.md`, `TextToSpeech__error-code.md`, `TextToSpeech__entry.md`(빈 제목)

### 4.1 기능 범위와 모드
- Speech Service가 합성하고 **차량 오디오 시스템으로 재생**한다. 앱이 PCM을 받아 직접 재생하는 흐름은 가이드에 없음.
- 모드 `ai.pleos.playground.tts.constant.Mode`:
  - `HYBRID` — **기본값**. 서버 streaming 우선, 네트워크 실패 시 On-device로 자동 전환. 서버 사용엔 앱 등록 필요.
  - `SERVER` — 서버 streaming만. 네트워크·앱 등록 필요. 실패 시 On-device 전환 없이 오류 이벤트.
  - `ON_DEVICE` — 차량 On-device 엔진의 streaming TTS. 네트워크·앱 등록 불필요.
- 생성자: `TextToSpeech(applicationContext, Mode.HYBRID)` — Mode 생략 시 HYBRID.

### 4.2 퍼미션 / 앱 등록
- `<uses-permission android:name="pleos.car.permission.TTS_SERVICE" />` — 초기화·리스너 등록·앱 등록·합성 요청 전부에 필요. 누락 시 `PERMISSION_DENIED`.
- 앱 등록(`TextToSpeech__app-registration.md`): Pleos Playground **My Project 생성 시 발급받은 Client ID / Client Secret**으로 `registerApp(clientID, clientSecret, listener: OnServerConnectionListener)` 호출. **SDK 초기화 완료 후** 호출, `onConnected()` 이후 서버 요청 시작. `onFailed(errorType: ErrorType)`.
  - SERVER: 필수 / HYBRID: 서버 합성을 쓰려면 필수 / **ON_DEVICE만: 불필요**.
  - 공모전 관점: 자격 증명 관리·네트워크 의존을 피하려면 ON_DEVICE가 가장 단순(현 [코드]의 선택).

### 4.3 초기화·해제 순서 [문서 intro]
```
TextToSpeech(ctx, mode) → initialize() → addEventListener() → (선택) setLocale()
→ (HYBRID/SERVER) registerApp → onConnected() 이후 speak() → stop() → removeEventListener() → release()
```
- `initialize(listener: OnInitializeListener)` 오버로드가 v3.3.1에 추가 [릴리즈]. 메서드: `onSuccess()`, `onFailure(errorType: ErrorType)` [바이너리, 코드].
- 스레드 주의점은 가이드에 없음.

### 4.4 합성 시작·중지 — `TextToSpeech__start-and-stop.md`
- `speak(text: String, isFinal: Boolean = true)`. 여러 조각을 한 발화로: 마지막 조각 외에는 `isFinal = false`.
  ```kotlin
  textToSpeech.speak("안녕하세요. ", isFinal = false)
  textToSpeech.speak("TextToSpeech SDK입니다.", isFinal = true)
  ```
- `stop()` → 진행 중 합성·재생 중지, 완료 시 `onStop()`.

### 4.5 이벤트 — `TextToSpeech__event-listeners.md`
| 콜백 | 의미 |
|---|---|
| `onReady()` | speak() 요청 처리 준비됨 |
| `onStart()` | 재생 시작 |
| `onUpdatedRms(rms: Double)` | 재생 중 RMS 갱신 |
| `onDone()` | 재생 정상 종료 |
| `onStop()` | 재생 중지됨 |
| `onError(errorType: ErrorType)` | 오류 |

`EventListener`의 6개 메서드는 모두 abstract [바이너리] → 전부 구현해야 함. 해제 전 같은 인스턴스로 `removeEventListener`.

### 4.6 로케일 — `TextToSpeech__set-locale.md`
- 지원: `ko-KR`(한국어), `en-US`(영어), `fr-CA`(프랑스어), `es-US`, `es-MX`(스페인어).
- `setLocale(locale: Locale)`은 **다음** 합성 요청부터 적용 → speak 전에 호출.
- 미호출 시: 같은 앱에서 이전에 setLocale로 설정한 값 → 없으면 시스템 locale.
- 미지원 언어 → `UNAVAILABLE_LANGUAGE`.

### 4.7 에러 코드 (전부) — `TextToSpeech__error-code.md`
`OnInitializeListener` / `OnServerConnectionListener` / `EventListener`에서 `ErrorType`으로 전달. 새 타입이 추가될 수 있으니 `else` 분기 필수.

| ErrorType | 설명 | 권장 조치 |
|---|---|---|
| `AUDIO_ERROR` | 오디오 출력 또는 재생 처리 실패 | 오디오 설정·출력 장치 확인 후 재요청 |
| `INVALID_REQUEST_ERROR` | 요청 값이나 요청 상태가 올바르지 않음 | 텍스트·호출 순서 확인 |
| `MIC_ERROR` | 오디오 입력 장치 처리 중 오류 | 오디오 장치 상태 확인 |
| `NETWORK_ERROR` | 네트워크 통신 실패 | 연결 확인 후 재요청 |
| `NOT_SUPPORTED_API` | 현재 모드/환경에서 지원하지 않는 API | Mode·플랫폼 지원 범위 확인 |
| `ON_DEVICE_ENGINE_ERROR` | On-device 엔진 오류 | 엔진 상태 확인 또는 HYBRID/SERVER 검토 |
| `PERMISSION_DENIED` | 서비스 접근 권한 없음 | TTS_SERVICE 선언·권한 상태 확인 |
| `SERVER_INTERNAL_ERROR` | Speech Service 내부 오류 | 잠시 후 재요청 |
| `TIMEOUT` | 제한 시간 내 미완료 | 네트워크·서버 확인 후 재요청 |
| `UNAUTHENTICATED` | 앱 인증 실패 | My Project 자격 증명 또는 UA 앱 정보 확인 |
| `UNAVAILABLE_LANGUAGE` | 요청 언어 사용 불가 | 지원 Locale로 변경 |
| `UNKNOWN` | 분류되지 않은 오류 | 환경 정보 수집 후 문의 |

### 4.8 최소 동작 코드 (Kotlin, ON_DEVICE)
```kotlin
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener

val tts = TextToSpeech(applicationContext, Mode.ON_DEVICE)
val events = object : EventListener {
    override fun onReady() = Unit
    override fun onStart() = Unit
    override fun onUpdatedRms(rms: Double) = Unit
    override fun onDone() = Unit
    override fun onStop() = Unit
    override fun onError(errorType: ErrorType) { /* when(errorType){ ...; else -> } */ }
}
tts.initialize(object : OnInitializeListener {
    override fun onSuccess() { tts.setLocale(Locale.KOREA); tts.speak("안녕하세요.") }
    override fun onFailure(errorType: ErrorType) {}
})
tts.addEventListener(events)
// 종료: tts.stop(); tts.removeEventListener(events); tts.release()
```

### 4.9 저장소 대조 (TTS) — [코드] `app/src/main/java/io/moodlab/moodrive/audio/DjVoice.kt`
- `Mode.ON_DEVICE`, `initialize(OnInitializeListener)`, `onSuccess`에서 `setLocale(Locale.KOREA)`, `addEventListener`, `removeEventListener` → `release()` — 문서 순서와 일치.
- `ready` 이전 speak는 버림. 문서상 `onReady()`(EventListener)도 "speak 처리 준비" 신호이므로 이것도 준비 조건으로 쓸 수 있음.
- [바이너리]에 가이드 미기재 공개 메서드 존재: `setAudioAttributes(AudioAttributes)`, `setSpeakerType(String)`, `setAudioZoneId(Int)`, `getAmbientAi()`, `registerApp`의 다른 오버로드 2개. 공모전 "문서에 없는 기능 사용 불가" 규정 → API Reference에 있는지 확인 전 사용 금지.

---

## 5. SpeechToText SDK

출처: `SpeechToText__intro.md`, `SpeechToText__declaring-permissions.md`, `SpeechToText__app-registration.md`, `SpeechToText__result-listeners.md`, `SpeechToText__set-locale.md`, `SpeechToText__start-and-stop.md`, `SpeechToText__sending-audio-data.md`, `SpeechToText__error-code.md`, `SpeechToText__entry.md`(빈 제목)

### 5.1 기능 범위와 모드
- 발화를 **텍스트로만** 반환. "음악 틀어줘"를 인식해도 실제 동작은 하지 않음 → 의도 해석·실행은 앱 몫. (문서가 안내하는 Gleo Interface는 공모전 팀이 승인 신청 불가 — notice-08.)
- 공모전 규정: 차량 마이크 음성을 외부 STT로 보내면 안 됨 → **이 SDK 사용 필수**.
- 모드 `ai.pleos.playground.stt.constant.Mode`:
  - `HYBRID` — 기본값. 서버 인식 우선, 네트워크 실패 시 On-device 자동 전환. 서버 사용엔 앱 등록 필요.
  - `SERVER` — 음성 데이터를 원격 Speech Service로 전송해 인식. 네트워크·앱 등록 필요. 실패해도 On-device로 전환하지 않음.
  - `ON_DEVICE` — 차량 On-device 엔진. 네트워크 불필요, 이 모드만 쓰면 앱 등록 불필요.
- [릴리즈] v3.3.1에 `InferenceSource`(`ON_DEVICE`, `SERVER`) enum 추가 — 어느 엔진이 결과를 냈는지 구분용으로 추정(가이드에 사용법 없음).
- 생성자: `SpeechToText(applicationContext, Mode.HYBRID)` — Mode 생략 시 HYBRID.

### 5.2 퍼미션 / 앱 등록
- `<uses-permission android:name="pleos.car.permission.STT_SERVICE" />` — 초기화·결과 리스너 등록·앱 등록·인식 요청·**오디오 데이터 전송** 전부에 필요. 누락 시 `PERMISSION_DENIED`.
- `android.permission.RECORD_AUDIO` 필요 여부는 가이드에 **언급 없음**(request() 시 "SDK가 마이크를 활성화"한다고만 기술).
- 앱 등록: TTS와 동일 구조. `speechToText.registerApp(clientID, clientSecret, OnServerConnectionListener { onConnected(); onFailed(errorType: ErrorType) })`, SDK 초기화 후 호출, onConnected 이후 서버 요청. SERVER 필수 / HYBRID 서버 사용 시 필수 / ON_DEVICE만이면 불필요.
- `start-and-stop` 문서: "서버 기반 인식이 필요한 모드에서는 **앱 등록 또는 UA 인증**도 완료" — UA 인증 방법은 가이드에 없음.

### 5.3 초기화·해제 순서 [문서 intro]
```
SpeechToText(ctx, mode) → initialize() → addListener(ResultListener) → (선택) setLocale()
→ (HYBRID/SERVER) registerApp → request() 또는 sendAudio()… → stop() → removeListener() → release()
```
- **리스너는 `request()`/`sendAudio()` 전에 등록**(이벤트 유실 방지) [문서 result-listeners].
- `initialize(listener: OnInitializeListener)` 오버로드 v3.3.1 추가 [릴리즈]. 메서드명은 TTS와 같은 `onSuccess()`/`onFailure(ErrorType)`로 추정(STT 바이너리는 캐시에 없어 미검증).

### 5.4 인식 시작·중지 — `SpeechToText__start-and-stop.md`
- `request()` — **차량 마이크** 활성화 + 인식 시작. 결과는 ResultListener.
- `stop()` — 인식 중지·마이크 사용 종료 → `onEndedRecognition()`.

### 5.5 결과 리스너 — `SpeechToText__result-listeners.md`
| 콜백 | 의미 |
|---|---|
| `onReady()` | 인식 요청 처리 준비됨 |
| `onStartedRecognition()` | 인식 시작 |
| `onUpdated(stt: String, completed: Boolean)` | 인식 텍스트 갱신. `completed == true`면 해당 결과 완료(최종) |
| `onUpdatedEpdData(on: Long, off: Long)` | EPD가 감지한 음성 시작/종료 위치. 값은 **입력 오디오 버퍼의 byte offset** |
| `onUpdatedRms(rms: Double)` | 입력 신호 RMS. **dB 아님** |
| `onEndedRecognition()` | 인식 종료 |
| `onError(errorType: ErrorType)` | 오류 |

### 5.6 앱 PCM 전송 (sendAudio) — `SpeechToText__sending-audio-data.md`
- 차량 마이크 대신 앱이 가진 PCM을 인식: `sendAudio(ByteArray)`. **ON_DEVICE 모드에서만 사용 가능.**
- 한 번에 전체 전송 또는 청크 스트리밍. **마지막 데이터 후 반드시 `completeAudioSend()`.**
- 수치(문서 예시 기준): **16 kHz, 16-bit, mono PCM**을 실시간 속도로 → **100 ms = 3,200 byte**(`BYTES_PER_100_MS = 3_200`), 청크마다 `Thread.sleep(100L)`. 다른 형식이면 그 형식의 100 ms 분량으로 계산. (16 kHz 외 샘플레이트 지원 여부는 명시 없음.)
- 예시의 `Thread.sleep`은 블로킹 → 메인 스레드에서 돌리지 말 것(코루틴 `Dispatchers.IO` + `delay` 등).
```kotlin
private const val BYTES_PER_100_MS = 3_200
val pcm = applicationContext.assets.open("test_audio.pcm").use { it.readBytes() }
pcm.asList().chunked(BYTES_PER_100_MS).forEach { speechToText.sendAudio(it.toByteArray()); Thread.sleep(100L) }
speechToText.completeAudioSend()
```

### 5.7 로케일 — `SpeechToText__set-locale.md`
TTS와 동일: `ko-KR`, `en-US`, `fr-CA`, `es-US`, `es-MX`. request() 전에 설정한 값이 그 세션 기준. 미호출 시 앱의 이전 setLocale 값 → 없으면 시스템 locale.

### 5.8 에러 코드 (전부) — `SpeechToText__error-code.md`
`OnInitializeListener` / `OnServerConnectionListener` / `ResultListener`에서 `ErrorType`으로 전달. `else` 분기 권장.

| ErrorType | 설명 | 권장 조치 |
|---|---|---|
| `AUDIO_ERROR` | 입력 오디오 데이터 처리 실패 | 입력 데이터·오디오 형식 확인 |
| `INVALID_REQUEST_ERROR` | 요청 값/상태 오류 | 호출 순서·**중복 요청**·입력 값 확인 |
| `MIC_ERROR` | 마이크 입력 시작/처리 오류 | 마이크 상태와 **센서 개인정보 보호 설정** 확인 |
| `NETWORK_ERROR` | 네트워크 통신 실패 | 연결 확인 후 재요청 |
| `NOT_SUPPORTED_API` | 현재 모드/환경 미지원 API | Mode·플랫폼 확인(예: ON_DEVICE 아닌데 sendAudio) |
| `ON_DEVICE_ENGINE_ERROR` | On-device 엔진 오류 | 엔진 확인 또는 HYBRID/SERVER 검토 |
| `PERMISSION_DENIED` | 서비스 접근 권한 없음 | STT_SERVICE 선언·권한 상태 확인 |
| `SERVER_INTERNAL_ERROR` | Speech Service 내부 오류 | 잠시 후 재요청 |
| `TIMEOUT` | 제한 시간 내 미완료 | 네트워크·서버 확인 |
| `UNAUTHENTICATED` | 앱 인증 실패 | My Project 자격 증명 또는 UA 앱 정보 확인 |
| `UNAVAILABLE_LANGUAGE` | 요청 언어 사용 불가 | 지원 Locale로 변경 |
| `UNKNOWN` | 분류되지 않은 오류 | 환경 정보 수집 후 문의 |

### 5.9 최소 동작 코드 (Kotlin, ON_DEVICE, 차량 마이크)
```kotlin
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode
// ResultListener / ErrorType / OnInitializeListener 패키지 경로는 가이드에 없음 → IDE 자동 import로 확인

val stt = SpeechToText(applicationContext, Mode.ON_DEVICE)
val results = object : ResultListener {
    override fun onReady() = Unit
    override fun onStartedRecognition() = Unit
    override fun onUpdated(stt: String, completed: Boolean) { if (completed) handleCommand(stt) }
    override fun onUpdatedEpdData(on: Long, off: Long) = Unit
    override fun onUpdatedRms(rms: Double) = Unit
    override fun onEndedRecognition() = Unit
    override fun onError(errorType: ErrorType) {}
}
stt.initialize()                // 또는 initialize(OnInitializeListener)
stt.addListener(results)        // request() 전에
stt.setLocale(Locale.KOREA)
stt.request()                   // 차량 마이크로 인식 시작
// stt.stop(); stt.removeListener(results); stt.release()
```

### 5.10 저장소 대조 (STT)
- [코드] 아직 STT SDK 미사용. `MainActivity.kt`가 **Android `SpeechRecognizer`/`RecognizerIntent`**를 쓰고, 매니페스트에 `RECORD_AUDIO`와 `android.speech.RecognitionService` `<queries>` 선언. → 공모전 규정(차량 마이크 음성을 외부 STT로 보내지 말고 SpeechToText SDK 사용) 위반 소지. 매니페스트 TODO 주석대로 `pleos.car.permission.STT_SERVICE` + SpeechToText SDK로 교체 필요.
- 의존성 `ai.pleos.playground:SpeechToText:3.3.1`(추정) 추가 필요.

---

## 6. FusedLocation SDK

출처: `FusedLocation__intro.md`, `FusedLocation__declaring-permissions.md`, `FusedLocation__location-data-access.md`, `FusedLocation__entry.md`(빈 제목)

### 6.1 기능 범위 [문서 intro]
| API | 기능 |
|---|---|
| `getLastKnownLocation()` | 마지막으로 확인된 위치 1회 조회(콜백). **새 위치를 계산하지 않음**, 없으면 `null` |
| `registerFusedLocationCallback(listener)` | 위치 변경 시 `onChanged(LocationData?)` |
| `unregisterFusedLocationCallback(listener)` | 수신 중지(등록 때와 같은 인스턴스) |
| `isSupported()` | 현재 실행 환경 지원 여부. **사용 전 반드시 확인**, 미지원이면 기능 제공하지 말 것 |
| `showLocationAccessBlockedPopup()` | 개인정보 설정으로 위치 접근이 차단된 경우 안내 팝업(**허용 API 아님**) |
| `addProximityAlert()` / `removeProximityAlert()` | 설정 영역 진입/이탈 알림 등록/해제. [릴리즈] Request/Event/Transition 모델과 전용 Listener 존재. **가이드에 사용법·파라미터 없음** |

`LocationData` [문서]: 위도, 경도, 고도, 진행 방향, 신뢰도, 타임스탬프. (속도 필드는 언급 없음. 정확한 필드명·타입은 API Reference.) **갱신 주기·간격 설정 옵션은 가이드에 없음.**

### 6.2 퍼미션 — `FusedLocation__declaring-permissions.md`
| 권한 | 보호 수준 | 사용 API |
|---|---|---|
| `pleos.car.permission.FUSED_LOCATION` | **dangerous** | `getLastKnownLocation`, `registerFusedLocationCallback`, `unregisterFusedLocationCallback` |
- dangerous → 매니페스트 선언 + **런타임 권한 요청**("권한 요청과 결과 처리는 앱의 권한 처리 흐름에 맞게 구현").
- Android `ACCESS_COARSE/FINE/BACKGROUND_LOCATION` 동작과 포그라운드/백그라운드 정책은 이 SDK가 보장하지 않음. Google Fused Location 설명을 Pleos 동작으로 해석하지 말 것.
- 위치 미수신 시 `null`과 `onError(Exception)`을 함께 처리하고 화면·기능 제한.

### 6.3 초기화·해제 순서 [문서 intro]
```
FusedLocation 생성 → isSupported() → initialize() → getLastKnownLocation() / registerFusedLocationCallback()
→ unregisterFusedLocationCallback() → release()
```
생성자 인자, `isSupported()`/`getLastKnownLocation()`의 정확한 시그니처(동기/콜백 형태)는 가이드에 없음("SDK 초기화 및 해제" 페이지 미수집).

### 6.4 에러 처리
- `FusedLocationListener.onError(exception: Exception)` — v1.1.3부터 **필수 구현** [릴리즈]. 에러 코드 표는 가이드에 없음("오류 처리" 페이지 미수집).
- `onChanged` 인자는 v1.1.3부터 nullable `LocationData?` [릴리즈].

### 6.5 최소 동작 코드 (Kotlin, 문서 예시)
```kotlin
// 생성자 형태는 미확인: val fusedLocation = FusedLocation(context)  (추정)
if (!fusedLocation.isSupported()) { /* 위치 기능 숨김 */ return }
fusedLocation.initialize()
val listener = object : FusedLocationListener {
    override fun onChanged(location: LocationData?) {
        location ?: return
        // 위도/경도/고도/방향/신뢰도/타임스탬프 사용
    }
    override fun onError(exception: Exception) {
        // 차단 상태면 fusedLocation.showLocationAccessBlockedPopup() 고려
    }
}
fusedLocation.registerFusedLocationCallback(listener)
// ...
fusedLocation.unregisterFusedLocationCallback(listener)
fusedLocation.release()
```

### 6.6 저장소 대조
[코드] FusedLocation 미사용(의존성·퍼미션 없음). 위치가 필요하면 NaviHelper `getCurrentLocationInfo()`→`onCurrentLocationInfo()`도 대안(NAVI_ROUTE 퍼미션, 이미 선언됨).

---

## 7. 저장소 코드 ↔ 문서 차이 요약

| # | 위치 | 차이 | 권장 조치 |
|---|---|---|---|
| 1 | `vehicle/PleosVehicleSignalSource.kt` `vehicleErrorCode()` | 문서의 `VehicleException.errorCode`는 배포 SDK에서 난독화(`callback.exception.a`, 접근자 `a()`). `getErrorCode` 리플렉션은 실패 가능성 높음 | 메시지 끝 숫자 id(50001 등, §2.5) 비교를 주 판별로 명시. 에뮬레이터 로그로 확인 |
| 2 | 같은 파일 `startGear()` | 기어를 AAOS Car API로 읽음(`CAR_POWERTRAIN`) — 5개 SDK 밖 | Vehicle `getGear()`가 API Reference에 있는지 확인 후 교체 검토 |
| 3 | 같은 파일 `startNavi()` | `initialize()` 직후 `addListener` | `initialize(onConnected, onFailed)`로 바꾸고 onConnected에서 등록 |
| 4 | 같은 파일 navi 리스너 | `duration`/`eta`를 초로 가정 | 단위 실측 |
| 5 | `MainActivity.kt`, `AndroidManifest.xml` | Android SpeechRecognizer + RECORD_AUDIO | SpeechToText SDK(ON_DEVICE 권장) + `STT_SERVICE`로 교체 |
| 6 | `audio/DjVoice.kt` | 문서와 일치(ON_DEVICE, 앱 등록 불필요) | — |
| 7 | `app/build.gradle.kts` | Vehicle 2.3.2 / NaviHelper 3.0.5 / TTS 3.3.1 — 가이드 버전과 일치. STT·FusedLocation 미포함 | 필요 시 추가(좌표 확인) |

---

## 8. 문서상 모호하거나 확인이 필요한 점

1. **Gradle 좌표**: SpeechToText·FusedLocation의 artifactId가 수집본에 없음(Importing 페이지 미수집). Vehicle은 시작 문서가 `2.3.1`, 가이드는 v2.3.2.
2. **초기화 페이지 미수집**: NaviHelper/TTS/STT/FusedLocation "SDK 초기화 및 해제" 페이지가 없어, `initialize()` 완료 전 호출 시 동작, 재초기화 가능 여부, `release()` 후 재사용 가능 여부를 알 수 없음.
3. **Vehicle 연결 완료 신호 없음**: `initialize()`에 콜백이 없음. 언제부터 API를 불러도 되는지 명시 없음.
4. **`VehicleException` 공개 여부**: 문서는 `e is VehicleException`을 쓰지만 배포 AAR에서는 난독화됨. 공식 판별 방법 확인 필요.
5. **Vehicle Gear 도메인**: 바이너리엔 `getGear()`가 있으나 가이드 도메인 표(19개)에 없음 → "문서에 공개된 API"로 볼 수 있는지 API Reference 확인 필요.
6. **콜백 스레드**: 5개 SDK 모두 콜백이 어느 스레드로 오는지 명시 없음(메인 가정 금지).
7. **퍼미션 보호 수준**: FUSED_LOCATION(dangerous) 외 CAR_*/CONTROL_CAR_*/NAVI_*/TTS_SERVICE/STT_SERVICE의 보호 수준·런타임 요청 필요 여부 미기재. 문서 링크 `permission-matrix` 페이지는 "페이지를 찾을 수 없습니다".
8. **HYBRID인데 앱 등록을 안 한 경우**: 자동으로 On-device로 동작하는지, `UNAUTHENTICATED`가 나는지 불명. 기본값이 HYBRID이므로 Mode를 명시하는 것이 안전.
9. **Client ID/Secret 발급**: "Pleos Playground에서 My Project 생성 시 발급" — 공모전 팀이 발급 가능한지, 워크스페이스 '준비 중' 상태와의 관계 불명. "UA 인증"(STT start-and-stop) 방법 미기재.
10. **STT와 RECORD_AUDIO**: 차량 마이크 사용에 앱의 `RECORD_AUDIO`가 필요한지 미기재.
11. **sendAudio 형식**: 16 kHz/16-bit/mono는 "예시"라고만 되어 있음. 지원 샘플레이트·엔디안·채널 범위, 최대 길이 미기재.
12. **TTS 오디오 경로**: 차량 오디오로 재생된다는 것 외에 오디오 포커스·덕킹(앱 음악과의 관계), 볼륨 채널, 큐잉(연속 speak 시 대기/끊기) 동작 미기재. TTS intro의 "인식이 끝나면 stop()을 호출합니다"는 STT 문서 복붙으로 보임.
13. **TTS `MIC_ERROR`**: TTS ErrorType에도 MIC_ERROR가 있음(설명: 오디오 입력 장치 처리 오류) — 발생 조건 불명.
14. **NaviHelper Java 예시**: 좌표 순서(경도 먼저)와 위치 인자 `RouteOption`이 v3.0.5 변경 전 형태. Kotlin 예시 `onWaypointInfo`는 실제 `onWayPointInfo`와 불일치.
15. **NaviErrorCode 목록**: 가이드 4개, 바이너리 5개(`CAUSE_SEARCH_TRIP` 추가). "최신 목록은 API Reference"라고 명시.
16. **NaviHelper 수치 단위**: `remainDistance`/`remainTime`/`duration`/`eta`/`distance`의 단위(m·s 추정)가 가이드에 없음. 에뮬레이터에서 실제로 어떤 이벤트가 오는지도 "Navigation 앱 구현에 따라 다름".
17. **`requestTrip`/`requestNavigationScreen`**: 퍼미션 표에만 등장하고 사용법 페이지 없음.
18. **FusedLocation**: 생성자, `getLastKnownLocation` 콜백 시그니처, 갱신 주기, ProximityAlert 파라미터(반경·만료 등)·리스너 형태, 오류 코드 전부 가이드에 없음. LocationData에 속도 필드가 있는지 불명.
19. **Vehicle Set 결과**: 문서는 `onFailed`만 있다고 하지만 일부 set API(Light 등)는 `onComplete(area, value)`를 가짐 → API별 시그니처 확인.
20. **에뮬레이터 주행 제한**: `ERROR_DRIVING_RESTRICTED`가 어떤 API·기어 조건에서 나는지 가이드에 표 없음(저장소 실측: P단 외 set 거부).

---

## 부록 (요약 작성 후 추가 수집·확인, 2026-11-12)

빠져 있던 Importing·초기화·FusedLocation 오류 처리 페이지를 `raw/`에 추가했고, API Reference 전체(`reference/`)와 대조했어요.

**Gradle 좌표 (문서 확정)**

| SDK | 좌표 |
| --- | --- |
| Vehicle | `ai.pleos.playground:Vehicle:2.3.2` (getting-started 예시는 2.3.1) |
| NaviHelper | `ai.pleos.playground:NaviHelper:3.0.5` |
| TextToSpeech | `ai.pleos.playground:TextToSpeech:3.3.1` |
| SpeechToText | `ai.pleos.playground:SpeechToText:3.3.1` |
| FusedLocation | `ai.pleos.playground:FusedLocation:1.1.3` |

**API Reference와 대조한 결과**
- **차속 API 없음.** 2.3.2에서 `VehicleSpeedListener` 삭제. `Display.getCurrentSpeedDisplayUnit`(표시 단위)과 크루즈 목표 속도만 있음
- **기어:** `Vehicle` 클래스 레퍼런스의 함수 목록에 `getGear`가 없음(도메인 19개만). 클래스 설명의 Callback 예시에만 `vehicle.getGear()`/`registerGearSelection`이 남아 있음. AAR 바이너리에는 존재. → 문서상 공식 지원으로 보기 어려움. 쓰려면 위험 감수
- **TTS:** `setAudioAttributes`, `setSpeakerType`, `setAudioZoneId`는 API Reference에 공개돼 있음(`reference/TextToSpeech.md`). 문서 기준으로 사용 가능
- FusedLocation 오류는 `FusedLocationListener.onError(Exception)`으로만 옴. `onChanged(LocationData?)`의 null을 유효 위치로 쓰지 말 것
