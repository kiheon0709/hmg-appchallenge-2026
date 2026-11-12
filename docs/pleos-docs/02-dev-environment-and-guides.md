# 02. 개발 환경 · 에뮬레이터 · 개발 가이드 · 릴리스 노트 · 생태계 판단

> 대상: 이 저장소에서 작업하는 개발자(AI 코딩 에이전트).
> 근거: `docs/pleos-docs/raw/` 아래 공식 문서 사본(각 파일 첫 줄 `<!-- source: URL -->`). 각 절 끝의 `출처:` 는 raw 파일명이다.
> 원칙: 문서에 있는 상수·클래스명·버전은 원문 그대로 옮겼다. 문서에 없는 내용은 "문서에 없음"이라고 적었고, 추론은 **[추론]** 으로 표시했다.
> 공모전 맥락(주최 측 2026-11-11 안내, 호출자 제공): 사용 가능 SDK는 **Vehicle, NaviHelper, TextToSpeech, SpeechToText, FusedLocation** 5종뿐(워크스페이스에서 "Coming Soon"이어도 사용 가능). LLM SDK와 공개 문서에 없는 SDK는 제공하지 않음. 공모전 팀은 Gleo Interface(App Schema) 검토·승인을 신청할 수 없음. 차량 마이크 음성을 외부 음성인식으로 보내면 안 됨(SpeechToText SDK 사용). 심사는 실차가 아니라 Pleos Connect Emulator에서 진행. 그 밖의 외부 네트워크·API는 사용 가능.

---

## 0. 개발 시 반드시 알아야 할 것 TOP 15

1. **minSdk ≥ 28 필수**: 차량 사이버 보안 요건(사이닝 키 로테이션·리니지) 때문. 샘플앱 minSdk 23은 따라 하면 안 된다. (setup-connect-sdk-emulator, tutorial)
2. **에뮬레이터는 `Pleos Connect Emulator v2.1.0`, API 34, AVD는 Cold boot + RAM 4 GB**. quick boot로 만들면 **VHAL을 쓸 수 없다**. 이전 버전이 설치되어 있으면 지운 뒤 설치한다. (configure-pleos-connect-emulator, setup-connect-sdk-emulator)
3. **Maven 저장소는 `https://nexus-playground.pleos.ai/repository/maven-releases/`**, Vehicle 좌표는 `ai.pleos.playground:Vehicle:2.3.1`(getting-started 예시). 그런데 SDK 릴리스 노트와 샘플앱 기준은 **Vehicle SDK 2.3.2**다. 이 버전 불일치는 9절에서 다룬다. 나머지 4개 SDK의 Gradle 좌표는 이 범위 문서에 없다.
4. **Vehicle SDK 2.3.2에서 `VehicleSpeedListener`(`onSpeedUpdated` 포함)가 삭제되었다**. 차량 속도 구독 코드는 이 API를 쓰면 안 된다. FAQ는 클러스터와 같은 속도가 필요하면 AAOS `CarPropertyManager`로 `PERF_VEHICLE_SPEED`를 읽으라고 한다. (sdk-release-note, vehicle-app-faq)
5. **Virtual Console에는 "차량 속도" 카드가 없다**. 셋업 가이드 소개 문구에는 "차량 속도"가 있지만 사용 가이드의 카드 목록에는 없다. Motion 도메인 카드는 Gear/Brake/Odometer/Steering Angle/Steering Wheel Heat뿐이다. 속도·위치 기반 시나리오는 에뮬레이터에서 직접 재현할 수단이 문서에 없다. (use-vehicle-emulator, virtual-console-guide)
6. **Virtual Console 접속 방법**: `adb forward tcp:8088 tcp:8088`을 실행한 뒤 PC 브라우저에서 `http://127.0.0.1:8088/`를 연다. 에뮬레이터 v2.1.0 이상이 필요하다. (use-vehicle-emulator)
7. **SDK 신청 상태**(create-my-project 기준): `Vehicle State`·`NaviHelper`는 신청 없이 "사용 가능"이다. `FusedLocation, TextToSpeech, SpeechToText, Vehicle Control SDK`는 "준비 중(Comming Soon)"이라 신청 버튼이 없다. 주최 측은 이 5종을 Coming Soon이어도 쓸 수 있다고 했다. **TTS/STT는 클라이언트 ID·시크릿이 필요하다**.
8. **LLM SDK 제공 종료**(Emulator v2.1.0, Playground v2.6.0 릴리스 노트). Gleo 연동이 필요하면 Gleo Interface를 쓰라고 되어 있지만, 공모전 팀은 App Schema 승인을 받을 수 없으므로 **Gleo 연동은 공모전에서 사실상 불가**하다.
9. **CRN 제공 종료**: Emulator 2.1.0부터 CRN 입력이 필요 없다. `getCRNInfo`/`checkCRNInfoCapabilities`는 Deprecated이며 `getVINInfo`/`checkVINInfoCapabilities`로 바꾼다. 블루투스 동글 연동도 종료되었다. (emulator-release-note, create-my-project, sdk-release-note)
10. **오디오 포커스**: 재생 전에 반드시 `AudioAttributes` + `AudioFocusRequest`로 요청한다. 음악과 공존하는 안내음은 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`을 쓴다. `AUDIOFOCUS_LOSS`를 받으면 정지하고 abandon한다. `LOSS_TRANSIENT`는 일시정지, `LOSS_TRANSIENT_CAN_DUCK`은 볼륨 감쇄로 처리한다. Gleo 호출로 `AUDIOFOCUS_LOSS_TRANSIENT`를 받으면 즉시 중단하고 필요하면 abandon한다. (audio-focus-development)
11. **Suspend(IGN OFF) 진입 시**: 재생을 중단하고 포커스를 abandon한 뒤 상태를 저장하고 리소스를 해제한다. Resume 때 자동 복원·자동 재생은 하지 않는다. (suspend-mode-audio-focus)
12. **Music/Audio 앱이라면 `MediaBrowserService`(MBS) + `MediaSession` 구현은 필수**다(미구현 시 배포 제외 가능). `android:appCategory="audio"`와 `androidx.car.app.launchable=true` 메타데이터를 넣는다. 앱 연결·프로필 변경 시 **자동 재생은 금지**다. `AUDIOFOCUS_LOSS`를 받으면 세션을 비활성화하고, `onStop()`에서는 `ExoPlayer.stop()`을 호출한다. (music-audio-app-developement-guide)
13. **주행 규제**: `CarUxRestrictionsManager.registerListener()`를 등록하고 `onUxRestrictionsChanged()`로 받은 `ActiveRestrictions` 비트를 그대로 적용한다(예: `UX_RESTRICTIONS_NO_VIDEO`). 규제 해제 기준은 IGN OFF, 또는 속도 ≤ 5 km/h이면서 (P단 / 파킹브레이크 / Auto Hold; 북미는 P 또는 N+파킹브레이크)일 때다. (driving-restriction-development-guide, infolink driving-restiction-policy)
14. **TTS·음성 안내는 선행 묵음 150~200 ms**를 넣는다. 오디오 데이터 앞단에 silence padding을 넣는 방식을 우선하고, 재생 지연 방식은 비권장이다. 묵음이 출력 경로 초기화나 포커스 확보를 대신하지는 못한다. (voice-guidance-preroll-silence-guide)
15. **CCP Back 하드키**: 최상단 화면에서는 `finish()`를 호출하지 말고 `moveTaskToBack(true)`로 숨긴다(미구현 시 심사 반려 가능). CCP를 지원하지 않으면 `com.hmg.rotary.ROTARY_SUPPORTED=false`를 선언하고 모든 필수 조작을 터치만으로 완결할 수 있어야 한다. `Build.DEVICE`(CONNECT_S/L/W)는 에뮬레이터에서 쓸 수 없다. (ccp-rotary-controller-development, vehicle-app-faq)

---

## 1. 개발 환경 요구사항 · 프로젝트 생성 · SDK 신청 · 의존성

### 1.1 소프트웨어 개발 환경 (원문 표)
| 구분 | 값 |
|---|---|
| Minimum Android SDK | Android API 28 이상 (사이닝 키 로테이션·리니지 지원 요건) |
| Gradle 버전 | 8.0 이상 |
| 개발 언어 | Java, Kotlin |
| Pleos Connect Emulator | 버전 2.1.0 |
| Pleos Connect Virtual Console | 지원 에뮬레이터 최소 버전 2.1.0 |
| (getting-started 사전 준비) | Android Studio, Android SDK API 34, minSdk API 28 이상, Pleos Connect Emulator system image |

출처: `docs__connect__guide__getting-started__application-development__setup-connect-sdk-emulator.md`, `docs__pleos-only__getting-started.md`

### 1.2 그래픽 카드 사양 (에뮬레이터 실행에 GPU 필수)
- 권장 사양: NVIDIA RTX 4060/5060 Laptop 8GB 이상, AMD Radeon RX 7700S/RX 7600M XT 8GB급 이상 외장 GPU
- 최소 사양: NVIDIA GeForce RTX 3060 Laptop GPU 6GB 이상, AMD Radeon RX 6600M 8GB 또는 RX 6700S 8GB 이상
- Apple Silicon(Mac)에 대한 GPU 요건 언급은 문서에 없다. Mac용 arm64 시스템 이미지 URL은 따로 제공된다(1.3 참고).

출처: `docs__connect__guide__getting-started__application-development__configure-pleos-connect-emulator.md`

### 1.3 에뮬레이터 설치 (SDK Update Sites)
1. Android Studio에서 `Tools > SDK Manager > SDK Update Sites` 탭을 열고 `+`를 누른다.
2. 다음 값을 입력한다.
   - Name: `Pleos Connect System Image`
   - URL for Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - URL for Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
3. `SDK Platforms` 탭에서 Android API Level 34의 `Pleos Connect system Image`를 체크하고 `Show Package Details`를 켠 뒤 OK를 누른다. 설치가 끝나면 Android Studio를 재시작한다.
4. 이전 버전 에뮬레이터가 설치되어 있으면 먼저 삭제한다.

### 1.4 AVD 생성
- `Tools > Device Manager > Create device`에서 Form Factor로 **Automotive**를 고르고 `Pleos Connect v2.1.0`을 선택한다.
- `Advanced Settings`에서 `Startup > Default boot = Cold`로 설정한다. 구버전 Android Studio에서는 `Graphics = Hardware`, `Boot option = Cold boot`로 설정한다.
- `Emulated Performance > RAM Size = 4 GB`로 설정한다.
- `Select system image > API 34 > Pleos Connect v2.1.0 APIs`를 고르고 Finish를 누른다.
- **Cold boot 필수**: quick boot로 설정하면 VHAL을 이용할 수 없다.
- 기본 언어는 한국어다. 영어로 바꾸려면 `차량 설정 > 일반 설정 > 언어/Language > English`로 이동한다.
- **마이크(STT 테스트용)**: 에뮬레이터 `Extended Controls > Microphone`에서 오디오 입력을 받도록 설정한다.
- AVD Bluetooth 동글 연동은 지원하지 않는다.

출처: `setup-connect-sdk-emulator.md`, `configure-pleos-connect-emulator.md`, `docs__pleos-only__getting-started.md`

### 1.5 Pleos Playground 가입 · 프로젝트 생성 · SDK 신청
- 회원 가입: Pleos Playground에서 로그인한 뒤 "Pleos 계정 만들기"를 선택한다. Playground 주소는 v2.7.0(2026-10-29)부터 `pleos.ai/playground`에서 **`developers.pleos.ai`** 로 바뀌었다.
- 프로젝트 생성: `Workspace > My Project > 프로젝트 생성`에서 프로젝트 이름(나중에 변경 가능), 개발사명, 카테고리, 애플리케이션 설명을 입력한다.
- 프로젝트 정보 화면 항목:
  - `키 정보 > 프로젝트 ID(Key)`: 앱 심사를 요청할 때 Pleos App Market Console에 입력한다.
  - `SDK > 클라이언트 ID`, `SDK > 클라이언트 시크릿`: 서버 연결이 필요한 SDK(**TextToSpeech, SpeechToText**)에 쓴다.
  - `테스트 정보 > CRN`: Emulator 2.1.0 이후로는 입력할 필요가 없다.
- SDK 상태값:
  - **준비 중**("Comming Soon" 배지, 신청 버튼 없음): `FusedLocation, TextToSpeech, SpeechToText, Vehicle Control SDK`
  - **사용 가능**(신청 없이 바로 사용): `Vehicle State`, `NaviHelper`
  - **신청 필요 → 검토 중 → 사용 가능 / 반려됨**
  - **제휴 계약 필요**: 웹 신청이 없고 계약 담당자에게 문의해야 한다.
- 공모전 맥락: 주최 측은 위 5종이 Coming Soon이어도 사용 가능하다고 안내했다. 다만 Coming Soon 상태에서 TTS/STT 클라이언트 ID·시크릿을 어떻게 받는지는 이 문서에 없다(9절 참고).

출처: `docs__pleos-only__create-my-project.md`, `docs__pleos-only__release-note__playground-release-note.md`

### 1.6 Gradle 설정 (원문 그대로)
```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
        }
    }
}
// app/build.gradle.kts
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.1")
}
```
- Vehicle 외 SDK(NaviHelper/TTS/STT/FusedLocation)의 artifact 좌표는 **이 범위의 문서에 없다**. 각 SDK 페이지(connect-sdk-pleos__*)를 확인해야 한다.
- 문서에 나오는 패키지명: `ai.pleos.playground.tts.TextToSpeech`, `ai.pleos.playground.stt.SpeechToText`, `ai.pleos.playground.vehicle.constant`, `ai.pleos.playground.navi.helper.listener.NaviHelperEventListener` 등(raw 전체 grep 결과).

### 1.7 SDK 버전 (SDK 릴리스 노트, 2026-10-19)
| SDK | Version |
|---|---|
| Vehicle SDK | v2.3.2 |
| NaviHelper | v3.0.5 |
| TextToSpeech | v3.3.1 |
| SpeechToText | v3.3.1 |
| FusedLocation SDK | v1.1.3 |

### 1.8 첫 API 호출 예시 (getting-started)
```kotlin
// Manifest: <uses-permission android:name="pleos.car.permission.CAR_DOORS" />
val vehicle = Vehicle(context)
vehicle.initialize()
val door = vehicle.getDoor()
door.getCurrentDoorPosition(
    area = DoorArea(DoorValue.ROW_1_LEFT),
    onComplete = { pos: Int? -> /* use */ },
    onFailed = { e: Exception -> /* handle */ }
)
// release()는 콜백 처리 이후 또는 onDestroy 등 SDK가 더는 필요 없을 때 호출
```
값을 읽지 못할 때 확인할 것: minSdk 28 이상인지, `pleos.car.permission.CAR_DOORS`를 선언했는지, 의존성 sync가 되었는지, 에뮬레이터가 정상 부팅했는지, `onFailed`의 예외 메시지.

출처: `docs__pleos-only__getting-started.md`

### 1.9 기타 (in-prep)
- `docs__pleos-only__in-prep___payment-system-migration.md`: 차량 내 결제는 플랫폼 전용 **Payment SDK**를 쓰는 것이 원칙이지만 **"현재 준비 중"** 이다. 따라서 공모전에서는 사용할 수 없다(허용 5종에도 없음). 광고는 정차·보조화면·승객 디스플레이로 제한될 수 있다. 위치·마이크·카메라에 접근하는 서드파티 SDK는 차량 보안 가이드라인 부합 여부를 검토해야 한다.
- `docs__pleos-only__connect-sdk-service-status.md`: "요청하신 Pleos Connect SDK 문서는 현재 해당 경로에서 제공되지 않습니다"라는 안내 페이지일 뿐 내용은 없다. 제공 여부가 확정되지 않은 기능은 별도로 안내하지 않는다.
- `docs__pleos-only__infolink-emulator__setup-infolink-emulator.md`: **404**(페이지 없음). InfoLINK 에뮬레이터 셋업 정보는 없다.

---

## 2. 에뮬레이터 · Virtual Console · 폼 팩터 테스트

### 2.1 Virtual Console 개요
- 에뮬레이터 환경에서 차량 상태와 이벤트를 조작하는 웹 도구다. **에뮬레이터 v2.1.0 이상**이 필요하다.
- 실행: `adb forward tcp:8088 tcp:8088`을 실행한 뒤 `http://127.0.0.1:8088/`에 접속한다.
- 접속 문제 해결 순서: 에뮬레이터 실행 여부 → 이미지 버전 → `adb devices` → forward 재실행 → PC의 8088 포트 점유 여부 → 재접속.
- 테마(주행 시각화 영역): 기어가 P일 때 Highway/Forest 테마로 바꿀 수 있고, Highway 테마에서는 First person/Third person 시점을 고를 수 있다. 테마는 테스트 기능에 영향을 주지 않는다.

출처: `docs__connect__guide__getting-started__application-development__use-vehicle-emulator.md`

### 2.2 시뮬레이션 가능한 신호 (Emulation Controls 카드 전체)
| Domain | 카드 | 값/범위 |
|---|---|---|
| Vehicle | Notification 1 | 식별자 1인 차량 알림 표시/숨김 |
| Motion | Gear Selection | P, R, N, D |
| Motion | Brake Pedal | 0~100%, 파생 페달 작동 상태 포함 |
| Motion | Odometer | 0~1,000,000 km, 1 km 단위 |
| Motion | Steering Angle | -540~540도, 1도 단위 |
| Motion | Steering Wheel Heat | Off, Low, Medium, High |
| ADAS | Cruise Control State | Not ready, Ready, Longitudinal, Lateral, Full autonomous |
| ADAS | Cruise Target Speed | 5.556~27.778 m/s, 0.277775 m/s 단위 |
| Convenience | ETCS Power | Off/On |
| Convenience | ETCS Scenario | 카드 삽입·제거, 요금소 진입·진출 |
| Convenience | Door Position | 앞/뒷좌석 좌우, 열림/닫힘 |
| Convenience | Door Lock | 모든 승객 도어 Unlock/Lock |
| Convenience | Trunk / Frunk Position | 열림/닫힘 |
| Convenience | Cabin Lights Switch | Off/On |
| Convenience | Reading Lights | 좌석별 Off/On |
| Convenience | Ambient Brightness | 0~100%, 1% |
| Convenience | Ambient Color | Cyan, Blue, Purple, White |
| Convenience | Exterior Lights Mode | Off, Auto, Tail lamp, Low beam (미등·하향등 연동) |
| Convenience | High Beam | Off/On |
| Convenience | Hazard Lights | 스위치와 실제 상태 함께 Off/On |
| Convenience | Stop Lights | Off/On |
| Convenience | Door Lights | 프렁크/트렁크 조명 Off/On |
| Convenience | Turn Signal State | Off, 좌·우 Blink, 좌·우 Flash |
| Convenience | Window Position | 좌석별 0~100%, 1% |
| Convenience | Window Lock | 뒷좌석 전체/좌우 Unlock/Lock |
| Convenience | Seat Occupancy | 운전석/동승석/뒷좌석 좌우 Empty/Occupied |
| Convenience | Windshield Wiper Switch | 앞/뒷유리 Off, Auto, Low, High |
| Convenience | Glove Box | 열림/닫힘 |
| Safety | Telltales | 주차브레이크, 안전벨트, ABS, TPMS 등 Off/On/Blink |
| Energy | Remaining Range | 0~1,000,000 m, 1,000 m 단위 |
| Energy | Battery Energy | 0~200 kWh, 0.1 kWh |
| Energy | Battery SOC | 0~100%, 1% |
| Energy | Battery Capacity | 0~200 kWh, 0.1 kWh |
| Energy | Charge Current Limit | 10~48 A, 0.5 A |
| Energy | Charge Percent Limit | 50~100%, 5% |
| Energy | Charge Switch | Off/On |
| Energy | Charge Port | Closed/Open |
| Climate | HVAC Power / Air Conditioning / HVAC Auto / HVAC Dual | 각 Off/On |
| Climate | Air Recirculation | Fresh/Recirculate |
| Climate | Fan Speed | 0~8단계 |
| Climate | Fan Direction | 운전석/동승석별 Face, Floor, Defrost |
| Climate | Defroster | 전면/후면 Off/On |
| Climate | Target Temperature | 운전석/동승석별 16~30°C, 0.5°C |
| Climate | Seat Temperature | 통풍 3단계, Off, 열선 3단계 |
| Climate | Air Cleaner | Off/On |

출처: `docs__pleos-only__app-test-guide__virtual-console-guide.md`

### 2.3 시뮬레이션할 수 없는 것 / 문서에 없는 것
- **차량 속도(현재 주행 속도) 카드가 없다.** 셋업 가이드는 "차량 속도, 도어, 조명 등 차량 상태 변경"이라고 소개하지만, 사용 가이드 카드 목록에서 속도와 관련된 것은 `Cruise Target Speed`(목표 속도)뿐이다.
- **GPS/위치·경로 주행 시뮬레이션**: Virtual Console 카드에 없다. FusedLocation·NaviHelper 테스트용 위치를 어떻게 주입하는지는 이 범위 문서에 없다. [추론] Android Emulator 기본 Extended Controls(Location)를 쓸 수는 있겠지만 문서화되어 있지 않다.
- **IGN/Suspend 진입**: 카드에 없다. Suspend 처리 코드는 문서에 있지만 에뮬레이터에서 트리거하는 방법이 없다.
- **주행 규제(CarUxRestrictions) ON/OFF를 바꾸는 직접 방법**: Virtual Console에 전용 카드는 없다. 개발 가이드 intro는 "FAQ - 디버깅을 위한 주요 ADB Command 활용법"에 "주행 규제 제어" ADB 명령이 있다고 하지만, **그 페이지는 raw에 없다**. [추론] Gear(D)와 Brake 조합으로 규제가 바뀔 수는 있으나 속도 입력이 없으므로 확인이 필요하다.
- **Bluetooth 동글**: 지원하지 않는다(제공 종료).
- **미디어 코덱**: 에뮬레이터는 `c2.android.*` 소프트웨어 코덱뿐이다(5.8 참고). 실차와 다르다.
- `android.os.Build.DEVICE` 플랫폼 구분값은 에뮬레이터에서 쓸 수 없다.

### 2.4 ADB · 포트 정리
| 목적 | 명령/포트 |
|---|---|
| Virtual Console | `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/` |
| 연결 확인 | `adb devices` (여러 대면 `<SERIAL>` 예: `emulator-5554`) |
| AVD 이름 확인 | `adb emu avd name` (예시 출력 `Pleos_Connect_v2.0` / `OK`) |
| 폼 팩터 Bridge | `go run . --avd "<AVD_NAME>"` → `http://127.0.0.1:8096` 수신, 에뮬레이터 gRPC `127.0.0.1:8554` |
| 폼 팩터 종료 시 | `adb forward --remove tcp:8095` |
| 샘플앱 빌드/설치 | `./gradlew assembleDebug`, `./gradlew installDebug` |

출처: `use-vehicle-emulator.md`, `form-factor-test-guide.md`, `tutorial__vehicle-sdk-sample-app.md`

### 2.5 폼 팩터 테스트
- 준비물: Android Studio + Platform-Tools, **Go 1.22 이상**, 테스트할 앱이 설치된 에뮬레이터, 8095·8096 포트.
- 가상 디스플레이는 **에뮬레이터를 별도 창으로 실행해야** 보인다. Running Devices 내장 실행이나 Headless 실행은 안 된다.
- 순서:
  1. `adb emu avd name`
  2. `host-display-bridge.zip`의 압축을 풀고 `go run . --avd "Pleos Connect v2.0"` 실행 (터미널은 계속 열어 둔다)
  3. 에뮬레이터에서 `FormFactorConsole` 앱 실행 (상단에 `UNAVAILABLE`이 보이면 Bridge를 확인한 뒤 Refresh, `DISCONNECTED`와 `DISPLAY2가 없습니다.`가 보이면 준비 완료)
  4. Display preset과 Orientation(Portrait/Landscape) 선택 → dp/DPI/px 확인 → "가상 디스플레이 생성" → DISPLAY2 창에서 앱 실행
- 문서에 명시된 화면 크기: **12.9인치 Portrait**, **14.6인치 Landscape** 두 예시뿐이다. **preset 전체 목록과 dp/DPI/px 수치는 텍스트에 없다**(스크린샷에만 있음).
- 확인할 것: 요소 잘림·겹침, 텍스트·아이콘 크기와 정렬, 가로·세로 레이아웃, 터치·화면 전환.
- 가상 디스플레이를 제거하면 그 위에서 실행 중이던 앱은 강제 종료된다.

출처: `docs__pleos-only__app-test-guide__form-factor-test-guide.md`

---

## 3. 개발 가이드 요점

출처(목차): `docs__pleos-only__vehicle-app-developer-guide__intro.md`. intro에는 "IME 동작에 따른 window resize 처리 가이드"와 "FAQ - 디버깅을 위한 주요 ADB Command 활용법"이 목차로 나오지만 **두 페이지 모두 raw에 없다**. intro의 요약만 있다: IME 표시 시 window resize 동작 기준과 insets 예외 설정 방법, ADB 설치·연결·캡처·녹화·앱 설치·실행·삭제·주행 규제 제어.

### 3.1 오디오 포커스
- 기본 개념: AAOS는 새 요청과 현재 보유자의 `CarAudioContext` 쌍에 사전 정의된 Interaction을 적용한다.
  - **R(Reject)**: 거부
  - **E(Exclusive)**: 새 요청이 획득하고 기존 보유자는 상실
  - **C(Concurrent)**: 동시 보유, 보통 duck
- C가 성립하려면 새 요청이 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`을 써야 한다. 기존 보유자가 duck 대신 pause하도록 설정되어 있으면 C가 깨진다.
- 보유자가 여럿이면 각 결과 중 가장 보수적인 것을 적용한다(**R > E > C**).
- 정책: **Pleos Connect Interaction Matrix v1.0 (2026-01-28)**. 행은 현재 보유자, 열은 새 요청이다. 컨텍스트는 `MUSIC, NAVIGATION, VOICE_COMMAND, CALL_RING, CALL, ALARM, NOTIFICATION, SYSTEM_SOUND, EMERGENCY, SAFETY, VEHICLE_STATUS, ANNOUNCEMENT`이다. **매트릭스 셀 값 전체는 텍스트에 없다**(이미지로 추정).
- 텍스트로 확인되는 셀:
  - MUSIC 재생 중 NAVIGATION 요청 → **C** (뮤직 duck + 내비 재생)
  - CALL 중 MUSIC 요청 → **R**
  - MUSIC 중 CALL 요청 → **E**
  - CALL 중 NAVIGATION 요청 → **C** (옵션 플래그로 R로 바꿀 수 있음)
  - EMERGENCY가 current이면 대부분 incoming은 **R**, EMERGENCY끼리는 C
  - EMERGENCY current일 때 SAFETY incoming은 R
- 앱 규칙:
  - 각 스트림은 재생 전에 `AudioAttributes` + `AudioFocusRequest`로 요청한다.
  - 포커스 변경 이벤트 처리는 필수다. 권장 매핑:
    - `AUDIOFOCUS_GAIN` → 재생
    - `AUDIOFOCUS_LOSS` → 즉시 중단·정지
    - `AUDIOFOCUS_LOSS_TRANSIENT` → 일시정지 후 복귀 가능
    - `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` → duck(볼륨 감쇄)
  - Duck: -X dB만큼 감쇄하고, 상대 스트림이 끝나거나 포커스가 반환되면 원복한다.
  - VOICE_COMMAND(Gleo)는 일반 오디오보다 우선한다. `AUDIOFOCUS_LOSS_TRANSIENT`를 받으면 즉시 중단하고 필요하면 abandon한다.
  - 매트릭스는 하드코딩하지 말고 `interaction_matrix.json`/csv로 두기를 권장한다(키 `current_context`, `incoming_context` → `E|C|R`).
  - 로그 권장 항목: incoming context, gain type, client id, holders, holder별 결과, 최종 결과, duck 적용·해제.
- 검증 체크리스트: 위 5개 시나리오 + MUSIC+NAVIGATION 동시 보유 상태에서 VOICE_COMMAND 요청.
- **CarAudioContext ↔ AttributeUsages**(원문 표):

| CarAudioContext | AttributeUsages |
|---|---|
| MUSIC | MUSIC, UNKNOWN, GAME, MEDIA |
| NAVIGATION | NAVIGATION, ASSISTANCE_NAVIGATION_GUIDANCE |
| VOICE_COMMAND | VOICE_COMMAND, ASSISTANT |
| CALL_RING | CALL_RING, NOTIFICATION_RINGTONE |
| CALL | CALL, VOICE_COMMUNICATION, VOICE_COMMUNICATION_SIGNALING |
| ALARM | ALARM |
| NOTIFICATION | NOTIFICATION, NOTIFICATION_* |
| SYSTEM_SOUND | SYSTEM_SOUND, ASSISTANCE_SONIFICATION, ASSISTANCE_ACCESSIBILITY |
| EMERGENCY | EMERGENCY |
| SAFETY | SAFETY |
| VEHICLE_STATUS | VEHICLE_STATUS |
| ANNOUNCEMENT | ANNOUNCEMENT |

(시스템 컨텍스트는 Android 11 이상에서만 사용 가능)

- [추론·적용 제안] 자체 음악 위에 자체 음성 안내를 얹는다면, 안내 스트림을 `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`(NAVIGATION 컨텍스트) + `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`으로 요청해야 문서상 C(duck 공존)가 보장되는 조합이 된다. TextToSpeech SDK가 내부에서 어떤 usage를 쓰는지는 이 범위 문서에 없다.

출처: `docs__pleos-only__vehicle-app-developer-guide__audio-focus-development-guide__audio-focus-development.md`

### 3.2 Suspend 모드
- 정의: IGN OFF 이후 저전력으로 유지되는 상태다. Garage mode 같은 일부 시스템 작업만 수행된다.
- 진입 시 순서: ① 재생 즉시 중단 → ② 포커스 abandon → ③ 상태(재생 여부·위치) 저장 → ④ player/track 해제. 이 과정에서는 새 재생이나 포커스 요청을 할 수 없다.
- 예외: 통화·긴급 통신, 안전 경고음, 시스템 필수 음성 안내(최소 범위).
- Resume: 포커스를 자동 복구하지 않는다. 상태를 재평가한 뒤 필요할 때만 재요청한다. 예제는 `shouldAutoResume = false`로 되어 있다.
- 예제 클래스: `PlayerActivity`의 `onSuspendEntering()` / `onResumeFromSuspend()`. `AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)`을 쓰고 `LOSS`/`LOSS_TRANSIENT`에서 pause와 abandon을 호출한다.
- **Suspend 진입을 감지하는 API(콜백·브로드캐스트명)는 문서에 없다**. 예제도 `onSuspendEntering()`을 누가 호출하는지 명시하지 않는다.

출처: `docs__pleos-only__vehicle-app-developer-guide__audio-focus-development-guide__suspend-mode-audio-focus.md`

### 3.3 Music/Audio 앱 가이드 (App Market 심사 기준)
- 카테고리 요건: 오디오 재생이 주 기능일 것, 주행에 적합한 UI, 백그라운드 재생, 차량 미디어 컨트롤 연동. 음악이 보조 기능인 앱은 즉시 반려된다. 영상이 주 기능인 앱은 이 카테고리에 들어가지 않는다.
- 하위 카테고리(Music/Podcast/Audiobook/Radio)를 정의하고 미디어 라이브러리에 전달해야 한다. 모든 콘텐츠를 단일 리스트로 섞어 노출하는 방식은 제한될 수 있다.
- Manifest:
  ```xml
  <application android:appCategory="audio" ...>
      <meta-data android:name="androidx.car.app.launchable" android:value="true" />
  </application>
  ```
- **필수**: Android `MediaBrowserService`(MBS)와 `MediaSession`. MBS가 없으면 배포 대상에서 제외될 수 있다.
- "미디어 센터"(MBS 기반 개발 템플릿) 사용을 권장한다. 사용할 때와 안 할 때의 차이:
  - 스티어링 휠 키: 기본 제공 / 별도 구현(미구현 시 반려 가능)
  - 미디어 앱 라이브러리 등록: 지원 / 미지원
  - Tune Knob: 지원 / 미지원
  - Media 버튼: 지원 / 미지원
  - Mode 버튼: 둘 다 미지원
- **미디어 센터를 어디서 받는지(artifact·URL)는 문서에 없다.**
- MBS를 직접 구현하는 경우: `MediaSessionCompat` 등록, `MediaSessionCompat.Callback` 구현. 최소 지원: 재생, 일시정지, 정지, 미디어 ID 기반 재생, 검색 기반 재생, (가능하면) 이전/다음 트랙.
- **자동 재생 금지**: 앱 연결 시와 프로필 변경 시 모두 금지다. 사용자의 명시적 동작이나 시스템 재개 요청이 있을 때만 재생한다.
- 포커스 처리 샘플:
  - `AUDIOFOCUS_LOSS` → `stopPlayback()` + `abandonAudioFocusRequest`
  - `LOSS_TRANSIENT`/`LOSS_TRANSIENT_CAN_DUCK` → `pausePlayback()`
  - `GAIN` → `resumePlayback()`
  - 게임 실행 등으로 LOSS를 받으면 MediaSession이 active 상태로 남지 않게 한다.
- 서비스 생명주기:
  - bind/unbind만 하면 `onTaskRemoved()`를 못 받을 수 있으므로 `onCreate()`에서 `startService()`를 호출한다.
  - `onStartCommand()`와 재생 시점에 `startForeground()`를 호출한다.
  - `onTaskRemoved()`는 `stopMedia()` → `stopSelf()` → `super.onTaskRemoved()` 순서로 처리한다.
  - 쓰리핑거 동작 시 `MediaSessionCompat.Callback.onStop()`이 호출되므로 `ExoPlayer.stop()`을 호출한다.
- Gleo AI 연동 방법은 두 가지다. ① Gleo Interface(schema 등록) ② Android 표준 미디어 API(MediaAgent, Media3: `MediaSession`/`MediaLibraryService`, 검색 기반 음성은 `MediaLibraryService` 검색 구현 필요). ②도 "향후 업데이트를 준비 중"이라 현재 동작한다는 보장이 없다. 공모전에서는 ①을 쓸 수 없다(6절).

출처: `docs__pleos-only__vehicle-app-developer-guide__music-audio-app-developement-guide.md`

### 3.4 Maps/Navigation 앱 가이드
- 내비컨테이너를 쓰려면 다음 카테고리를 선언한다.
  ```xml
  <intent-filter>
      <action android:name="android.intent.action.MAIN" />
      <category android:name="android.intent.category.APP_MAPS" />
  </intent-filter>
  ```
  App Market 등록 카테고리와 일치해야 하며, 불일치하면 즉시 반려된다.
- 단일 경로 정책: 경로 안내(음성·TBT)는 Active Route 상태에서만 허용된다. Inactive Route와 Background 상태에서 안내하면 정책 위반이다. Phone Projection과의 관계에도 같은 정책이 적용된다. 구현에는 `CarAppFocusManager.requestAppFocus`와 `CarAppFocusManager.OnAppFocusChangedListener`를 쓴다.
- 경로 전환은 사용자의 명시적 선택이나 기존 앱의 오류·종료 때만 허용된다. 자동 전환은 금지다.
- 주행 중 금지: 3 depth를 넘는 메뉴, 긴 리스트 스크롤, 영상·애니메이션·광고.
- 주행 중 허용: 음성 입력, 단일 터치 주요 기능, 즐겨찾기 목적지 선택.
- UX 요건: 최소 터치 영역, 핵심 정보 상시 노출, 주행 중 전체화면 팝업 금지.
- 권한: 위치는 필수, 마이크는 음성 입력 시에만. 비공식 센서·API 사용은 금지다.
- 보안: 위치 데이터 암호화, HTTPS, 개인정보 처리방침, 불필요한 위치 저장 금지.
- 즉시 반려: 주행 중 광고, 영상·게임 요소, 복수 내비 동시 안내, 카테고리 위반, OEM/AAOS 정책 위반.
- [공모전 적용] 내비 카테고리 앱이 아니라면 이 가이드는 "다른 앱이 경로 안내를 하는 동안 우리 앱이 TBT를 흉내 내지 않는다" 정도로만 참고하면 된다. 경로·목적지는 NaviHelper SDK로 OEM 내비에 요청하는 구조가 자연스럽다[추론].

출처: `docs__pleos-only__vehicle-app-developer-guide__navigation-app-development-guide.md`

### 3.5 CCP(로터리 컨트롤러)
- 앱 분류는 User-installed, Pre-installed, System이다. **User-installed 앱은 CCP 지원이 선택**이다. 다만 CCP를 지원하지 않는 앱은 필수 조작을 터치만으로 완결할 수 있어야 한다. Pre-installed 앱은 CCP 필수다(예외: 정적 콘텐츠, WebView, 게임 등).
- 미지원 선언:
  ```xml
  <meta-data android:name="com.hmg.rotary.ROTARY_SUPPORTED" android:value="false" />
  ```
- **CCP Back 하드키 정책(필수)**:
  - 최상단 화면에서는 `moveTaskToBack(true)`로 숨긴다. `finish()`/`finishAffinity()`는 쓰지 않는다.
  - 하위 화면이나 팝업에서는 기존 Back 처리를 한다.
  - Back 처리 API는 `OnBackInvokedDispatcher` 또는 기존 Back API를 쓴다.
  - 숨긴 뒤에도 미디어 재생 등은 계속한다.
  - 프로세스가 종료될 수 있으므로 상태를 저장하고 복원한다.
  - 이를 지키지 않으면 반려될 수 있다.
- 심사 항목 예시: 포커스 이동, 회전 스크롤, 클릭 선택, 역방향 입력, 터치 전용 영역 없음, 주행 중 차단 요소.
- 브라우저 앱: 회전 → 스크롤, 클릭 → 선택, 좌/우 → 포커스 이동.
- "Pleos Connect CCP UX Guideline"은 다운로드 링크로만 제공되며 내용은 raw에 없다.
- 에뮬레이터에서 CCP 입력을 어떻게 시뮬레이션하는지는 문서에 없다.

출처: `docs__pleos-only__vehicle-app-developer-guide__ccp-rotary-controller-development.md`

### 3.6 IME
- 원문 페이지가 raw에 없다. intro 요약만 있다: "IME 표시 시 window resize 동작 기준과 insets 예외 설정 방법을 안내합니다." **세부 내용은 확인할 수 없다.**

### 3.7 주행 규제(Driving restriction) 처리 코드
- 구현 흐름: `CarUxRestrictionsManager.registerListener()`를 등록하고 `onUxRestrictionsChanged(CarUxRestrictions)`에서 받는다. `isRequiresDistractionOptimization()`과 `getActiveRestrictions()` 비트마스크로 기능별 제한을 적용한다.
- 샘플(원문):
  ```java
  if (mCurrentCarUxRestrictions.isRequiresDistractionOptimization()
      && (mCurrentCarUxRestrictions.getActiveRestrictions()
          & CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO) != 0) {
      stopPlayingVideo();
  }
  ```
- DO(Distraction Optimized)를 선언한 앱도 규제가 활성화되면 직접 제한을 적용해야 한다. DO 미선언 앱이 상황별로 무엇을 쓸 수 있는지는 "지역별 주행 규제 정책"(planning-guide, 이 범위 밖)을 따른다.
- 플랫폼이 지역별로 맞는 ActiveRestrictions를 내려주므로 앱은 받은 값만 반영하면 된다. UX 규제 Figma 사양서는 Playground에 공개하지 않는다.
- 규제 ON/OFF 기준(InfoLINK 문서):
  - 공통: IGN1·엔진 OFF이면 해제된다.
  - 북미: 속도 ≤ 5 km/h이면서 (Auto는 기어 P / Manual은 Parking Brake ON + 기어 N)일 때 해제된다. Auto Hold는 정차로 인정하지 않는다.
  - 북미 외: 속도 ≤ 5 km/h이면서 (기어 P / Parking Brake ON / Auto Hold Active) 중 하나를 만족하면 해제된다. 속도가 5 km/h를 넘으면 규제 ON이다.
  - QR 화면은 `CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP_QR_SCAN`에 해당한다. KOR에서는 대부분 ✅이고, 북미·유럽·기타 지역은 ⛔이다.
  - 지역 구분은 `product.sw.target_region`으로 한다(KOR/EUR/USA/CAN).
- 공모전 주최 측 기존 답변("조명은 P단 기준")과 일치하는 기준으로 보인다[추론, 저장소 커밋 메시지 기준].

출처: `docs__pleos-only__vehicle-app-developer-guide__driving-restriction-development-guide.md`, `docs__pleos-only__infolink-emulator__driving-restiction-policy.md`

### 3.8 음성 안내 선행 묵음
- 모든 음성 안내 앞에 **150~200 ms**를 넣는다. 최소 150 ms이고, 200 ms를 넘으면 체감 지연이 생긴다.
- 적용 위치 우선순위: ① PCM/WAV 앞단 silence padding → ② TTS 결과 후처리 → ③ 플레이어 재생 지연(비권장).
- 연속 발화에는 다시 넣지 않는다. 스트림이 끊기거나 새 세션이면 다시 넣는다.
- 별도로 확인할 것: 출력 경로 초기화, 포커스·세션 확보, 디코더·플레이어 준비 상태.
- [추론] TextToSpeech SDK는 직접 재생하는 구조일 수 있으므로, 데이터 앞단에 패딩을 넣을 수 있는지는 SDK 문서(범위 밖)로 확인해야 한다.

출처: `docs__pleos-only__vehicle-app-developer-guide__voice-guidance-preroll-silence-guide.md`

### 3.9 FAQ (앱 실행 환경 · 플랫폼 리소스)
- 플랫폼 구분: `android.os.Build.DEVICE`(=`ro.product.device`)는 `CONNECT_S` / `CONNECT_L` / `CONNECT_W`이며 **실차에서만 유효**하다.
- 지역 코드: `product.sw.target_region`은 `KOR`, `EUR`, `USA`, `CAN`이다.
- 내비게이션 탑재 여부를 확인하는 방법은 비공개다(문의처: partnership@pleos.ai).
- 클러스터와 같은 속도가 필요하면 `CarPropertyManager`로 `PERF_VEHICLE_SPEED`를 읽는다. GPS로 계산한 속도는 클러스터와 다를 수 있다. 필요한 권한명은 이 문서에 없다. [참고] 샘플앱 Manifest에는 `pleos.car.permission.CAR_SPEED`가 있다.
- 배터리 SOC는 Vehicle SDK `getEvBatteryStateOfCharge` 문서를 참조한다.
- 코덱:
  - Connect-S/L: FLAC, AAC, MP3, OPUS, ... / H.264, HEVC, AV1, ...
  - Connect-W: PCM, AAC, ... / H.264, H.265, AV1, VP8, VP9
  - 에뮬레이터 오디오 디코더: `c2.android.amrnb/amrwb/flac/g711.alaw/g711.mlaw/aac/mp3/opus/raw/vorbis.decoder`
  - 에뮬레이터 비디오 디코더: `h263/av1/avc/hevc/mpeg4/vp8/vp9`
  - 에뮬레이터 인코더: 오디오 `amrnb, amrwb, flac, aac, opus`, 비디오 `h263, av1, avc, hevc, mpeg4, vp8, vp9`
  - 에뮬레이터 결과를 실차에 그대로 적용하면 안 된다.
  - **[공모전 적용] 심사는 에뮬레이터에서 하므로 음원은 MP3/AAC/FLAC/Opus/Vorbis/WAV(PCM) 범위로 준비하는 것이 안전하다.**

출처: `docs__pleos-only__vehicle-app-developer-guide__vehicle-app-faq.md`

---

## 4. 릴리스 노트

### 4.1 Pleos Connect Emulator v2.1.0 (2026-10-19, 정식)
- New:
  - Pleos Connect Virtual Console(도메인별 차량 상태·이벤트 설정)
  - 폼팩터 테스트 기능
- Improvements: 탑재 SDK 버전을 올려 API 지원 범위가 넓어졌다.
- Removed: **LLM SDK 제공 종료, CRN 제공 종료, 블루투스 동글 연동 제공 종료**.

출처: `docs__pleos-only__release-note__emulator-release-note.md`

### 4.2 Pleos Playground
- **v2.7.0 (2026-10-29)**: 주소가 `pleos.ai/playground`에서 `developers.pleos.ai`로 바뀌었다. 메뉴·푸터가 개편되었고, 소제목 단위 검색과 위치 표시가 추가되었다.
- **v2.6.0 (2026-10-19)**: 사이트 구조를 개편했다(Discover > Developer Journey). 문서 검색, Virtual Console·폼 팩터·Vehicle SDK Sample App 가이드가 추가되었다. Removed: **LLM SDK**("Gleo AI와의 연동이 필요한 경우 Gleo Interface를 이용").

출처: `docs__pleos-only__release-note__playground-release-note.md`

### 4.3 Pleos Connect SDK (2026-10-19)
**Vehicle SDK v2.3.2**
- Breaking:
  - `CruiseControl`: `OFF, READY, ACTIVE`가 제거되었다. 새 값은 `OTHER, ENABLED, ACTIVATED, USER_OVERRIDE, SUSPENDED, FORCED_DEACTIVATION_WARNING`이다. 이름만 1:1로 바꾸면 안 된다.
  - `checkHvacFanSpeedCapabilities`: 반환이 `Map<SeatArea, List<Int>>`에서 `Map<SeatArea, AreaBounds<Int>>`로 바뀌었다.
  - `FanDirectionType`: 3개에서 8개(UNKNOWN과 복합 풍향 포함)로 늘었다. ordinal 변화는 `FACE 0→1, FLOOR 1→2, DEFROST 2→4`다.
  - `DoorArea, MirrorArea, SeatArea, TurnSignalArea, WheelArea, WindowArea`: 내용 기반 `equals`/`hashCode`로 바뀌었다(Map·Set 키 가정 점검 필요).
- New:
  - `checkVINInfoCapabilities`, `checkSmartGlassLevelCapabilities` 등
  - `getCurrentEvEfficiency`, `registerEvBatteryStateOfCharge`, `getCurrentSmartGlassLevel` 등(EV/Fuel 효율, SOC, 연료량, ETCS 음성안내 볼륨, Smart Glass)
  - `getVINInfo`
  - 모델 `CapabilityConfig, TripStatistics, TelltaleData, Telltales`
- Changed:
  - 기존 Capability API(`checkDoorLockCapabilities`, `checkWindowPositionCapabilities` 등)에 `CapabilityConfig` 콜백 오버로드가 생겼고, 이전 형태는 WARNING deprecated다.
  - `registerHvacTemperature`/`registerHvacTemperatureCurrent`에 `HvacTemperatureValueListener` 오버로드가 생겼다.
  - `EvBatteryLevelListener, EvChargePortOpenListener, RangeRemainingListener`는 문서에서만 숨겼다(동작 유지).
- Deprecated:
  - `getCRNInfo`, `checkCRNInfoCapabilities` → `getVINInfo`, `checkVINInfoCapabilities`
  - `checkWheelAreaCapabilities` → `checkLowTirePressureCapabilities`
- **Shutdown**: `getHvacTemperatureValueSuggestion` 삭제, **`VehicleSpeedListener` 삭제(리스너와 `onSpeedUpdated` 제거)**.

**NaviHelper v3.0.5**
- Breaking:
  - `onNavigationStatus` 인자가 `RouteDriving`에서 `NavigationStatus`로 바뀌었다.
  - **좌표 인자 순서가 `longitude, latitude`에서 `latitude, longitude`로 바뀌었다**(`BookmarkInfo, CurrentLocationInfo, DestinationInfo, RecentDestinationInfo, RequestWaypointInfo, RouteInfo, WaypointInfo`).
  - `DestinationInfo, WaypointInfo`의 `totalDistance/totalTime`이 `remainDistance/remainTime`으로 바뀌었다.
- New: `requestTrip`, `requestRouteWithWaypoints`, `requestNavigationScreen`, `initialize(onConnected, onFailed)`, `onCautionPointInfo`, `onRoutingCompleted`, `onTripInfo`
- Changed: 모델 필드 확장, `CameraType, CautionPointType, NavigationStatus, RoutePointType`
- Deprecated: `onRouteEnded` → `onRoutingCompleted`
- Shutdown: `FindRouteResult`, `onUserDefinedRoute` 삭제. `CameraType.SPEED_CAMERA_SECTION`은 `SPEED_CAMERA_SECTION_START, SECTION_SPEED_ALERT, SPEED_CAMERA_SECTION_END`로 나뉘었다.

**TextToSpeech v3.3.1**
- Breaking: 오류 콜백 인자가 `String`에서 `ErrorType`으로 바뀌었다.
- New: `TextToSpeech.initialize(listener: OnInitializeListener)`, `TextToSpeech.version`, `ErrorType`
- Changed: `EventListener.onError(errorType: ErrorType)`, `OnServerConnectionListener.onFailed(errorType: ErrorType)`
- Shutdown: `ai.pleos.playground.tts.constant.ServerType` 삭제, `...tts.utils.Log/StringUtils` 비공개 처리

**SpeechToText v3.3.1**
- Breaking: 오류 콜백 인자가 `String`에서 `ErrorType`으로 바뀌었다.
- New: `SpeechToText.initialize(listener: OnInitializeListener)`, `SpeechToText.version`, `ErrorType`, `InferenceSource: ON_DEVICE, SERVER`
- Changed: `ResultListener.onError(errorType: ErrorType)`, `OnServerConnectionListener.onFailed(errorType: ErrorType)`
- Shutdown: `ai.pleos.playground.stt.api.DevelopmentOnly` 삭제, `...stt.utils.Log/StringUtils` 비공개 처리

**FusedLocation SDK v1.1.3**
- Breaking: `FusedLocationListener.onChanged`의 인자가 `LocationData`에서 `LocationData?`(nullable)로 바뀌었다. `FusedLocationListener.onError(exception: Exception)` 구현이 필수가 되었다.
- New: `addProximityAlert`, `removeProximityAlert`, `getLastKnownLocation`, `isSupported`, `showLocationAccessBlockedPopup`, Proximity Alert Request/Event/Transition 모델과 Listener

출처: `docs__pleos-only__release-note__sdk-release-note.md`

### 4.4 알려진 제약 (릴리스 노트·가이드 종합)
- LLM SDK, CRN, BT 동글이 종료되었다.
- 에뮬레이터는 Cold boot가 아니면 VHAL을 쓸 수 없다.
- Virtual Console에는 속도·위치·IGN 신호가 없다.
- `Build.DEVICE`를 쓸 수 없다.
- 코덱은 c2 소프트웨어 코덱뿐이다.
- 샘플앱에서 보이듯 기능이 Supported여도 특정 area(예: Door Lock `ROW_1_LEFT`)는 `area ID: 0x1 not supported`로 실패할 수 있다.

---

## 5. Vehicle SDK 샘플앱 튜토리얼에서 배울 코드 패턴

출처: `docs__pleos-only__tutorial__vehicle-sdk-sample-app.md`

### 5.1 기준 환경 (원문 표)
| 항목 | 기준 |
|---|---|
| Sample App | 1.0.0-sdk35 |
| Vehicle SDK | 2.3.2 |
| Vehicle Service | 2.3.22 |
| AVD | Pleos_Connect_v2.1, Android 14 |
| JDK | 17 |
| Gradle wrapper | 8.10.2 |
| Android Gradle Plugin | 8.8.2 |
| Kotlin | 2.0.0 |
| compileSdk / targetSdk | 35 |
| Sample App minSdk | 23 (제품 앱은 28 이상) |

- SDK 버전은 `gradle/libs.versions.toml`에서 관리한다. APK 경로는 `app/build/outputs/apk/debug/`, 런처 이름은 "Vehicle SDK Sample"이다.
- SDK 버전을 바꾸면 `PublicApiPolicyTest`의 public API 수 기준도 맞춰야 한다.
- **샘플앱 소스 저장소 URL·배포 위치는 문서에 없다.**
- 일반 폰이나 기본 에뮬레이터 이미지가 아니라 **Pleos Vehicle Service가 포함된 전용 AVD**가 필요하다.

### 5.2 표준 호출 흐름 (Door Position 기준 예제)
| 단계 | typed API | 필요 권한 |
|---|---|---|
| 지원 범위 확인 | `checkDoorPositionCapabilities` | `CAR_DOORS` |
| 현재값 조회 | `getCurrentDoorPosition` | `CAR_DOORS` |
| 변경 알림 구독 | `registerDoorPosition` | `CAR_DOORS` |
| 상태 변경 요청 | `setDoorPosition` | `CONTROL_CAR_DOORS` |
| 구독 해제 | `unregisterDoorPosition` | - |

### 5.3 반드시 지킬 패턴
1. **Capability를 먼저 확인한다**: 지원 여부, 읽기·쓰기 접근, area별 값 범위(예: 0..1)를 받는다. 범위를 하드코딩하지 않는다. 확인이 실패하면 set을 시도하지 않는다. "Unsupported"에는 확인 요청 자체의 실패도 들어 있다.
2. **SDK initialized ≠ Service 연결 완료 ≠ 기능 사용 가능**: 초기화 호출이 예외 없이 반환됐다고 연결된 것으로 간주하지 않는다.
3. **set 완료 콜백 ≠ 실제 상태 변경**: 완료 콜백 값은 요청값이다. 실제 값은 재조회하거나 Listener 알림으로 확인한다. 요청이 끝났다고 UI 현재값을 바꾸지 않는다.
4. **register/unregister의 "Complete"는 호출이 예외 없이 반환됐다는 뜻일 뿐**이다. Service 측 완료나 알림 수신을 의미하지 않는다.
5. **해제는 등록할 때와 같은 Listener 객체·같은 area로** 한다. 다른 area로 해제하면 오류가 난다. 화면 이동이나 area 선택 변경으로는 구독이 자동 해제되지 않는다. 다른 area로 재등록할 때는 기존 구독을 해제한 뒤 등록한다. 분류 변경이나 ViewModel 종료 시에도 정리 코드를 둔다.
6. SDK 사용이 끝나면 `Vehicle.release()`를 호출한다.
7. `null`/unavailable(값 없음)과 오류 메시지가 있는 실패를 구분한다. 표시만 보고 권한 누락이라고 단정하지 않는다.
8. 권한은 런타임 승인 창 방식이 아니라 **Manifest 선언**으로 처리한다. **제품 앱은 실제로 쓰는 typed API의 최소 권한만** 선언하고, 샘플의 전체 목록은 복사하지 않는다.

### 5.4 샘플앱 public permission 전체 목록 (참고용, 원문)
- Read: `pleos.car.permission.CAR_CLIMATE, CAR_CONNECT_VENDOR_CONTROL, CAR_DOORS, CAR_DRIVE, CAR_ENERGY, CAR_INFO, CAR_LIGHTS, CAR_MILEAGE, CAR_MIRRORS, CAR_POWERTRAIN, CAR_SAFETY, CAR_SEATS, CAR_SPEED, CAR_TOLL, CAR_TIRES, CAR_WINDOWS`
- Control: `CONTROL_CAR_CLIMATE, CONTROL_CAR_DOORS, CONTROL_CAR_DRIVE, CONTROL_CAR_ENERGY, CONTROL_CAR_INFO, CONTROL_CAR_LIGHTS, CONTROL_CAR_MIRRORS, CONTROL_CAR_SAFETY, CONTROL_CAR_SEATS, CONTROL_CAR_TOLL, CONTROL_CAR_WINDOWS`
- 기타: `READ_CAR_DISPLAY_UNITS, READ_CAR_STEERING, READ_ADAS_STATES, READ_WINDSHIELD_WIPERS, CONTROL_WINDSHIELD_WIPERS`
- `PRIVATE_*` 권한은 예제에 넣지 않는다. 이후 release에서는 샘플 Manifest를 source of truth로 본다.

---

## 6. Gleo Interface / App Integration, 그리고 생태계 제품의 공모전 사용 가능성

### 6.1 Gleo Interface(App Integration Guide)란
- Gleo AI(LLM 기반 차량 음성 에이전트)가 타사 앱의 function을 호출하는 **function calling 방식** 인터페이스다(OpenAI function calling과 유사하고 Android용으로 확장했다). (core-concepts)
- **App Schema**(`assets/schema.json`)의 구조:
  - 필드: `packageName`, `appDescription`, `version`, (예시에만 있는 `invokeWord`), `actionCalls`(**최대 3개**)
  - `actionCalls`의 각 항목: `type: "function"`, `function.name`(대문자+언더스코어), `description`(영어, 1~2문장, 예시 금지), `parameters`(type object, properties type은 string/number/boolean/array 단일, enum은 문자열만, null 미지원), `required`, `component`(`"activity"` → `startActivity`, `"service"` → `sendBroadcast`, 기본값 activity)
  - (app-capability)
- **Gleo Action 수신**:
  - Intent action이 function 이름이다. `setPackage(packageName)`, extra `"ACTION_PARAM"`에 JSON 문자열이 담긴다.
  - Manifest에서 각 function마다 `<intent-filter><action android:name="ADD_REMINDER"/><category android:name="android.intent.category.DEFAULT"/></intent-filter>`를 선언한다.
  - (examples, deliver-schema)
- **Callback**:
  - `Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE")`에 extra `packageName`, `response`(JSON 문자열)를 담아 `sendBroadcast`한다.
  - response 필드: `functionName`, `status`(`success`/`asking`/`fail`), `message`(영어 완전 문장), `mode`(`voice` 기본/`beep`/`silent`), `data`(JSON 배열 문자열).
  - **Gleo는 7초 후 Action을 취소**한다.
  - (action_callback, examples)
- **스키마 등록 경로**:
  - `assets/schema.json`을 넣는다.
  - "App Schema를 전달받으면, 당사 가이드라인을 준수하는지 검토합니다. **승인 후에는 통합이 자동으로 활성화됩니다.**"
  - Gleo AI 앱은 부팅 때 한 번, 그리고 Gleo AI 앱 설치·삭제 때 AssetManager로 모든 앱의 `schema.json`을 스캔해 DB에 저장한다.
  - (deliver-schema)
- 라디오 앱 사용 예: `SWITCH_BAND`(service), `SEARCH_STATION`(activity), `PLAY_FREQUENCY`(service). (use-cases__radio-app)

### 6.2 공모전에서 쓸 수 없는 이유
1. **검토·승인이 활성화 조건**인데(deliver-schema), 주최 측은 공모전 팀이 Gleo Interface(App Schema) 검토·승인을 신청할 수 없다고 안내했다.
2. Gleo는 LLM 기반이다. 개발자용 LLM SDK는 종료되었고(Emulator 2.1.0, Playground 2.6.0), 주최 측도 LLM SDK를 제공하지 않는다고 했다.
3. 허용 SDK 5종(Vehicle, NaviHelper, TTS, STT, FusedLocation)에 Gleo가 없다.
4. Music 앱의 "Android 표준 미디어 API(MediaAgent) 기반 Gleo 제어"는 문서상 **"향후 업데이트를 준비 중"** 이다.
- 결론: **음성 상호작용은 SpeechToText(입력) + TextToSpeech(출력) SDK로 앱 안에서 자체 구현**한다. 차량 마이크 음성을 외부 STT로 보내는 것은 금지다. [추론] STT 결과 텍스트를 외부 서버(자체 백엔드나 외부 API)로 보내는 것은 "외부 네트워크·API 허용" 범위로 보이지만, 개인정보·HTTPS 요건은 지켜야 한다.
- [추론] 에뮬레이터에 Gleo AI 앱이 있고 `assets/schema.json`을 넣으면 실제로 스캔되는지는 확인되지 않았다. 승인 없이 활성화되지 않는다고 문서에 명시되어 있으므로 기능 설계를 여기에 의존하면 안 된다.

### 6.3 생태계 다른 제품 판단
| 제품 | 무엇인가 (문서 근거) | 공모전 사용 | 근거 |
|---|---|---|---|
| Connect Mobile (Console) | 42dot 사내 백오피스. 차량·사용자·프로필·앱 릴리즈 관리, Mock 차량 FORM/FUNC 제어. URL `https://connect-mobile-console.stage.42dot.io/`, **사내망(42dot_wifis)/VPN 전용**, 외부 사용자는 R&A로 권한 신청. 앱은 사내 배포(iOS 기업용 42dot Inc. 인증서). 사용자 가이드: 공조 예약 추가 불가, Pet Care 외 편의모드 비활성 | **불가** | 사내 전용이고 외부 접근 불가. 허용 SDK도 아님 |
| Fleet API | Pleos Fleet 기반 REST + Webhook(Vehicle, Device, Matching, Device Model; Abnormal event·Reservation·Driver·Maintenance·Trip은 지원 예정). 토큰: `POST ${FLEET_API_GATEWAY_HOST}/auth/client/token`(client_id, secret, identifier=Project ID), 헤더 `x-42dot-client-id: Bearer ...`, 만료 3600초 | **사실상 불가** | 엔드포인트는 "Pleos Fleet 사용 계약 후" 담당자가 제공하고 별도 사용 승인이 필요. 실제 차량 데이터 대상이라 에뮬레이터와 연결되지 않음 |
| Vehicle Data API | 현대/기아/제네시스 커넥티드카 텔레매틱스 오픈 API(`https://api.pleos.ai/v1/vehicles/{vin}/...`: batteries, powertrains, locations, driving, status, freezer, speed 등), OAuth 2.0 | **불가** | "현재(2026.8월) **유럽 지역에서만** 사용 가능". 실제 VIN·소유자 동의가 필요하고 에뮬레이터 차량과 무관 |
| Pleos Platform SDK | product 페이지에 목차만 있음 | **불가** | api-reference 5개 페이지(intro, get-started, compatibility/feature/permission-matrix) 모두 **404**. 공개 문서 없음 = 주최 측 기준 미제공 |
| Payment SDK | 차량 인앱결제 전용 SDK | **불가** | "현재 준비 중"(in-prep) |
| Gleo AI / LLM SDK / Speech SDK / PEFT 등 (product-gleoai, product-ambient-ai) | 링크 목록뿐 | **불가**(TTS/STT SDK만 가능) | LLM SDK 종료, Gleo 승인 불가 |
| ADAS SDK, NaviProvider SDK (product-ivios, product-ai-navigation) | 목차만 있음 | **불가** | 허용 5종에 없음 |
| Maps API/JS, Place·Map SDK for Android, Navigation Engine API (product-maps) | 목차만 있음 | **불가로 판단** | 허용 5종에 없고 공개 사용 절차가 이 문서들에 없음. 지도 표시가 필요하면 외부 지도 API(외부 API 허용)를 검토하되 내비 정책(3.4)을 지킬 것[추론] |
| Sync/SyncAnalytics, Universal Wallet, Media Server, VDP, DGA, Atria, CODA OS(SDV OS/Standard), HMG ID OAuth, Messaging Platform, App Market | product-* 링크 목록뿐 | **불가** | 허용 SDK가 아니고 공개 상세 문서가 이 범위에 없음. App Market은 배포 채널(공모전 제출과는 별개) |

출처: `docs__connect-mobile__*.md`, `api-reference__fleet-api__*.md`, `api-reference__vehicle-data-api__intro.md`, `api-reference__pleos-platform-sdk__*.md`(404), `product-*.md`, `docs__pleos-only__in-prep___payment-system-migration.md`

- 정리: **공모전 앱이 Pleos 생태계에서 쓸 수 있는 것은 Connect SDK 5종 + AAOS 표준 API(CarUxRestrictionsManager, CarAppFocusManager, AudioManager, MediaBrowserService 등 개발 가이드가 요구하는 것)뿐**이다. AAOS `CarPropertyManager`(`PERF_VEHICLE_SPEED`)도 FAQ가 권장하지만, 주최 측의 "5종만" 규정이 AAOS 표준 API까지 막는지는 확인이 필요하다(9절).

---

## 7. 문서상 모호하거나 확인이 필요한 점

1. **Vehicle SDK 버전 불일치**: getting-started는 `ai.pleos.playground:Vehicle:2.3.1`, SDK 릴리스 노트와 샘플앱은 `2.3.2`다. 2.3.2가 같은 Maven 저장소에 있는지, 에뮬레이터 탑재 Vehicle Service 2.3.22와 2.3.1/2.3.2 중 어느 쪽이 맞는지 확인해야 한다(2.3.2에서 `VehicleSpeedListener` 삭제 등 breaking 변경 있음).
2. **나머지 4개 SDK의 Maven 좌표**가 이 범위 문서에 없다(각 SDK 페이지 확인 필요).
3. **Coming Soon 상태 TTS/STT의 클라이언트 ID·시크릿 발급**: create-my-project는 TTS/STT에 클라이언트 ID·시크릿이 필요하다고 하면서 해당 SDK를 신청할 수 없는 "준비 중"으로 분류한다. 주최 측이 "Coming Soon이어도 사용 가능"이라고 했으므로, 키가 없어도 동작하는지(에뮬레이터 내장 서버·온디바이스 등) 실측해야 한다.
4. **"Vehicle State" vs "Vehicle Control SDK"**: 워크스페이스에서는 상태 조회(State)는 사용 가능, 제어(Control)는 준비 중이다. 에뮬레이터에서 `set*` API가 거부될 수 있다[추론]. 거부 코드 처리가 필요하다(저장소 최근 커밋에 "50001 거부 판별"이 있음).
5. **Virtual Console 속도 카드 부재**: 셋업 가이드 소개("차량 속도... 변경")와 사용 가이드 카드 목록이 서로 다르다. 주행 중/정차 상태(주행 규제 ON)를 에뮬레이터에서 어떻게 만드는지 확인이 필요하다. ADB FAQ의 "주행 규제 제어" 명령이 답일 가능성이 크지만 그 페이지가 raw에 없다.
6. **누락 페이지**: "IME 동작에 따른 window resize 처리 가이드", "FAQ - 디버깅을 위한 주요 ADB Command 활용법", InfoLINK 에뮬레이터 셋업(404), Pleos Platform SDK 5개(404), "Pleos Connect CCP UX Guideline"(다운로드 파일)은 내용을 확인할 수 없다.
7. **Interaction Matrix 셀 값**: v1.0(2026-01-28) 전체 표가 텍스트에 없다. 체크리스트에 나온 몇 셀만 확인된다.
8. **폼 팩터 preset 목록·해상도**: 12.9인치 Portrait / 14.6인치 Landscape만 언급되고 dp/DPI/px 수치는 없다. AVD 이름 예시도 서로 다르다(`Pleos_Connect_v2.0`, `"Pleos Connect v2.0"`, 설치 가이드 `Pleos Connect v2.1.0`, 튜토리얼 `Pleos_Connect_v2.1`). 종료 단계에 `adb forward --remove tcp:8095`가 있지만 8095를 forward하는 단계는 문서에 없다.
9. **Suspend 진입 감지 API**가 문서에 없고, 에뮬레이터에서 IGN OFF를 트리거하는 방법도 없다.
10. **위치 주입**: FusedLocation·NaviHelper 테스트용 위치나 경로 시뮬레이션 방법이 이 범위 문서에 없다.
11. **미디어 센터**: Music 앱에 권장되는 "미디어 센터" 템플릿을 어디서 받는지(라이브러리 좌표) 문서에 없다. User-installed 앱이 쓸 수 있는지도 불명확하다.
12. **AAOS 표준 API 허용 범위**: 주최 측의 "5종 SDK만"이 Pleos SDK에 한정된 말인지, `CarPropertyManager`(`PERF_VEHICLE_SPEED`) 같은 AAOS Car API 사용까지 제한하는지 확인이 필요하다. 개발 가이드는 CarUxRestrictionsManager·CarAppFocusManager·CarPropertyManager 사용을 오히려 요구하거나 권장한다.
13. **샘플앱 소스 위치**: 빌드 방법은 있으나 저장소 URL이나 배포 경로가 문서에 없다.
14. **GPU 요건과 Mac**: 사양은 NVIDIA/AMD 외장 GPU 기준뿐이다. Apple Silicon에서 성능·호환성 기준이 문서에 없다(arm64 이미지 URL은 있음).
15. **Gleo 스캔 동작**: 승인 없이 `assets/schema.json`이 에뮬레이터 Gleo에 스캔되는지 불명확하다. 공모전 규정상 의존하지 않는 것이 맞다.

---

## 부록 (요약 작성 후 추가 수집, 2026-11-12)

요약을 쓸 때 빠져 있던 페이지를 `raw/`에 추가했어요: ADB Command FAQ, IME 창 크기 가이드, 앱 업데이트·버전 관리, 디자인 컴포넌트·파운데이션 31쪽(대부분 이미지라 텍스트가 짧음), Connect Mobile 사용자 가이드.

- **주행 규제 켜고 끄기 (모든 차종 공통, 시연에 유용)** — `raw/docs__pleos-only__vehicle-app-developer-guide__adb-command-reference-guide.md`
  - 켜기: `adb shell cmd hmg_car_service --service drivingstate driving_regulation on`
  - 끄기: `adb shell cmd hmg_car_service --service drivingstate driving_regulation off`
- 화면 녹화: `adb exec-out screenrecord --output-format=h264 - > screen.h264` → `ffmpeg -framerate 60 -i screen.h264 -c copy out.mp4`
- 기어 변경은 Virtual Console에서. 유틸리티 모드 전환 명령도 같은 페이지에 있음
- 앱 버전: 배포·심사 요청 때마다 반드시 버전을 올려야 함 (`raw/docs__pleos-only__app-registration-guide__app-version-management-policy.md`)
- 위 TOP 15의 "Vehicle Control 준비 중 → set 거부 가능성"은 추정이에요. 2026-11-07 에뮬레이터 실측에서 앰비언트 set(P·D단)과 도어 set(P단)은 성공했어요 (`docs/STATUS.md`).
