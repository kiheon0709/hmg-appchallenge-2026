# 01. 기획·정책·디자인 레퍼런스 (Pleos Connect)

> 대상: 개발자(AI 코딩 에이전트)·기획자. 원문은 `docs/pleos-docs/raw/` 아래 공식 문서(plain-text 변환본)이며, 각 절 끝 괄호에 출처 파일명을 적었다. 파일명 접두사는 다음과 같이 줄였다.
> - `PG:` = `docs__pleos-only__vehicle-app-planning-guide__`
> - `DR-IL:` = `docs__pleos-only__infolink-emulator__driving-restiction-policy.md`
> - `RV:` = `docs__pleos-only__distribute__app-review-guide`
> - `SUB:` = `docs__pleos-only__distribute__app-submission-guide.md`
> - `REG:` = `docs__pleos-only__app-registration-guide__`
> - `DS:` = `docs__connect__guide__docs-design__`
> - `GL:` = `docs__connect__guide__glossary.md`, `CN:` = `docs__connect__guide__connect.md`
>
> 원문 상수·플래그·수치는 그대로 옮겼다. 원문에 없는 것은 "문서에 없음"이라고 적었다. 이 공모전은 실제 App Market 심사가 아니라 에뮬레이터에서 평가하지만, 심사 규칙도 설계 제약으로는 그대로 쓸 만하다.
> 디자인 문서(DS)는 원문이 대부분 이미지라 텍스트로 남은 수치가 적다. 그리드·px·dp·터치 타깃 크기·폰트 크기(sp)는 **텍스트 원문에 없다**(§4.9 참조).

---

## 0. 기획·개발 시 반드시 지킬 것 TOP 15

| # | 규칙 | 핵심 수치/상수 | 출처 |
|---|---|---|---|
| 1 | **DO(Distraction Optimization)를 선언하지 않은 앱은 Drive(주행) 상태에서 사용할 수 없다.** 주행 중에도 써야 하는 앱이면 DO를 선언하고 `CarUxRestrictions`에 맞춰 UI를 줄이거나 비활성화한다. | Parking=사용 가능 / Drive=사용 불가 | PG:driving-restiction-policy, PG:driver-distraction-guideline |
| 2 | 주행 중 제한은 **지역별로 다르다.** 북미가 가장 엄격하다. 키보드·다이얼패드·설정·차량설정·동영상·내비 검색·QR 로그인·매뉴얼이 모두 ⛔이고, 통합 검색만 "음성한정"이다. 유럽과 그 외 지역은 키보드·다이얼패드·검색·내비 검색을 허용한다. | `UX_RESTRICTIONS_NO_KEYBOARD(8)`, `NO_DIALPAD(1)`, `NO_FILTERING(2)`, `NO_SETUP(64)`, `NO_VIDEO(16)`, `LIMIT_CONTENT(32)`, `LIMIT_STRING_LENGTH(4)` | PG:driving-restiction-policy |
| 3 | 동영상: 주행 규제 상태에서는 **영상 재생을 시작하거나 재개하지 않는다**(모든 지역 Driving ⛔). 미디어 앱(동영상·게임·브라우저)은 운전이 시작되면 UI를 숨기고 일시정지하며, 운전 중에는 일시정지를 해제할 수 없다. | `UX_RESTRICTIONS_NO_VIDEO(16)` | PG:driver-distraction-guideline, RV:__review-driver-distraction-elements |
| 4 | 텍스트 길이: 영문 단일 메시지는 **120자 또는 24단어 이하**, 일본어는 30자(영문 기준) 이하, **한 줄은 80자 이하**, 문장·단락은 **3줄 이하**로 쓴다. 텍스트 자동 스크롤, 애니메이션 이미지, 동영상처럼 시선을 끄는 요소는 금지한다. | 120 / 24 / 30 / 80 / 3 | DS:Introduction__intro |
| 5 | 응답성: 입력 후 시스템 응답은 **0.25초**를 넘지 않는다(터치→터치 효과 애니메이션). 버튼은 **2초** 안에 반응하고, 넘으면 동작 중임을 UI로 표시한다. 앱 실행과 콘텐츠 로드는 각각 **10초** 이내다. | 0.25 s / 2 s / 10 s | RV:__review-functionality-performance-stability, DS:Introduction__intro |
| 6 | 가독성: 아이콘·텍스트·이미지 대비율은 **4.5:1 이상**이다. 중요 정보 텍스트 높이는 **5 mm 이상**, 보조 텍스트는 **3 mm 이상**이다. 정보는 1~2초 안에 파악할 수 있어야 한다. | 4.5:1 / 5 mm / 3 mm / 1~2 s | DS:Introduction__intro |
| 7 | 조작: **한 손**으로 할 수 있고 단순해야 한다. 양손 조작이나 손발을 움직이게 하는 동작은 유도하지 않는다. 키보드보다 **프리셋·최근 항목·음성**을 먼저 쓴다. 음악 앱은 프리셋 플레이리스트·최근 재생·즐겨찾기를 첫 화면에 둔다. | – | DS:Introduction__intro, PG:driver-distraction-guideline |
| 8 | 화면: **portrait(1 window)와 landscape(2 window)를 모두 설계**한다. window가 바뀌어도 상태 손실이 없어야 하고, 기능 패리티를 지키며, 레터박스와 호환성 모드는 쓰지 않는다. | `screenOrientation` 미지정 시 portrait로 1 window를 쓴다 | PG:app-structure-screen-model, RV:__review-functionality-performance-stability, DS:getting-started__layout |
| 9 | 앱은 **Driving view를 가리거나 종료할 수 없다.** 반대로 Status bar·GNB 패널·키보드·Gleo AI는 앱보다 위 레이어라서 앱을 일시적으로 가릴 수 있다. | – | PG:app-structure-screen-model, DS:getting-started__layout, DS:Experiences__gleo-ai |
| 10 | 포그라운드와 백그라운드가 자주 바뀐다. 화면에서 사라져도 앱은 종료되지 않는다. 다시 포그라운드로 오면 **마지막 상태를 복원**한다(수 시간 이상 지났으면 기본 상태로 시작해도 된다). 백그라운드에서는 타이머·폴링·센서·네트워크를 최소화한다. | – | PG:app-structure-screen-model, RV:__review-functionality-performance-stability |
| 11 | 리소스: CPU는 **주행 중 비내비 앱 5% 미만, 내비 앱 15% 이하, 정차 중 모든 앱 25% 이하**다. RAM은 **400 MB 이하**다. 콜드 스타트 때는 최소한만 초기화하고 나머지는 lazy loading한다. | 5% / 15% / 25% / 400 MB | PG:performance-resource-mgmt |
| 12 | 하드웨어에 **직접 접근하지 않는다.** Pleos Connect SDK나 AAOS Car API로만 접근한다. 가용성은 **차종명 분기가 아니라** `checkXXXCapabilities`(Vehicle SDK), `isSupported`(FusedLocation), `CarPropertyManager`로 확인한다. | – | PG:platform-api-architecture, PG:api-compatibility-availability |
| 13 | Vehicle SDK 퍼미션은 **Pleos App Market과 사전에 협의해 승인받은 것만** 선언할 수 있다. 승인되지 않은 퍼미션을 선언하면 심사에서 불통과한다. | – | RV:__review-functionality-performance-stability |
| 14 | 오디오: Pleos Connect **오디오 포커스 정책**을 지키고, 포커스 획득·상실 대응 로직을 **반드시** 구현한다. 운전에 중요한 오디오 안내가 나오면 일시정지하거나 볼륨을 조정한다. | – | PG:performance-resource-mgmt, PG:driver-distraction-guideline |
| 15 | 알림과 보안: 운전과 무관한 알림은 쓰지 않는다. 미디어 앱은 헤드업 알림을 게시할 수 없고, 알림에 광고를 넣을 수 없다. 민감정보는 암호화하고 Keystore에 보관한다. 통신은 HTTPS와 **TLS 1.2 이상**(1.3 권장)으로 하고, 하드코딩·디버깅 설정·디버그 인증서는 배포본에 남기지 않는다. 네트워크 단절에 대비해 캐시·재시도·오프라인 UI를 둔다. | TLS ≥ 1.2 | RV:__review-driver-distraction-elements, DS:Introduction__intro, REG:app-security-guide, PG:platform-api-architecture |

---

## 1. 앱 카테고리와 매니페스트 선언

### 1.1 Pleos Connect 앱 카테고리 정의 (PG:app-category)

앱 카테고리는 설계 시점부터 명확히 정해야 한다. 원문은 아래 8개를 나열하고 "등"으로 끝낸다. 목록이 닫혀 있다는 보장은 없다.

| 카테고리 | 핵심 목적 |
|---|---|
| Maps/Navigation | 위치 기반 길 안내 |
| Mobility Services | 이동 수단/이동 서비스 제공 |
| Lifestyle | 일상생활 관리 및 향상 |
| Entertainment | 오락 및 여가 제공 |
| Business | 업무 및 생산성 지원 |
| Music/Audio | 오디오 콘텐츠 중심 |
| Kids | 아동 대상 콘텐츠 |
| Social/Communication | 사람 간 소통 및 네트워크 |

### 1.2 주행 중 기능이 "제한적으로 허용될 수 있는" 카테고리 (PG:app-category)

원문은 이 내용을 "허용 가능한 기능의 **예시**이며, 실제 적용 여부는 주행 상태와 지역별 정책에 따라 달라질 수 있습니다"라고 설명한다. 나머지 5개 카테고리(Lifestyle, Entertainment, Business, Kids, Social/Communication)에 대한 주행 중 허용 기능은 **문서에 없다.**

| 카테고리 | 주행 중 허용 가능 기능 예시 |
|---|---|
| **Music/Audio** | 앨범 아트 등 단순 시각 정보 표시, 재생/일시정지/곡 넘기기 등 단순 조작, 운전 중 음악 스트리밍 청취, 라디오 채널 변경, 오디오북 재생 |
| **Maps/Navigation** | 주행 경로 안내와 실시간 교통 정보, 주의를 분산시키지 않는 간결한 지도, Turn By Turn(TBT) 음성 안내 활성화, 목적지 경로 탐색, 실시간 빠른 길 안내, 주변 교통 상황 안내 |
| **Mobility Services** | 주행과 밀접한 차량 관리 서비스, 현재 위치 기반 즉각 정보, 전기차 충전소 찾기와 경유지 설정, 가까운 주유소 탐색, 주행 중 접근 가능한 주차장 위치 확인 |

### 1.3 매니페스트 `android:appCategory` 선언 (PG:app-category)

- `<application>` 요소의 `android:appCategory` 속성으로 선언한다.
- 이것은 **Pleos App Market의 서비스 카테고리와 다른 것**이고, AndroidManifest 선언 기준이다. App Market 카테고리는 제출 시 Console에서 따로 고른다(§6.2).
- 원문에 나온 값은 다음 3개뿐이다.

| 앱 카테고리 | 설정 값 |
|---|---|
| 게임 | `android:appCategory="game"` |
| 음악/오디오 | `android:appCategory="audio"` |
| 비디오 전용(예: 유튜브 같은 비디오 전용 앱) | `android:appCategory="video"` |

```xml
<application
    ...
    android:appCategory="audio" />
```
- Maps/Navigation, Mobility, Lifestyle 등에 대응하는 `appCategory` 값은 **문서에 없다.**

### 1.4 App Market 쪽 카테고리 규칙 (RV:__review-app-registration-info, RV:__review-functionality-performance-stability, SUB)
- 앱에 가장 적합한 카테고리를 골라야 한다. 맞지 않으면 **임의로 변경될 수 있다.**
- 카테고리(앱 유형)에서 벗어나는 기능이 들어 있거나 서비스 내용과 다른 유형을 고르면 **제출이 거부될 수 있다.**

---

## 2. 주행 규제 (Driving restriction / DO / UX restriction)

### 2.1 개요 (PG:driving-restiction-policy, PG:driver-distraction-guideline, GL)
- 주행 규제는 차량의 **속도·기어·브레이크** 같은 하드웨어 신호와 지역별 안전 규정에 따라 적용된다. 정책은 플랫폼 버전과 양산 차량에 따라 다를 수 있다.
- 앱은 **`CarUxRestrictions`** 로 제한 상태를 확인하고, 제한 대상 기능의 UI와 동작을 축소하거나 비활성화해야 한다. 연동 방법은 "주행 규제 개발 가이드"(`docs__pleos-only__vehicle-app-developer-guide__driving-restriction-development-guide.md`, 이 문서 범위 밖)에 있다.
- 용어(GL):
  - **Distraction Optimized**: `CarUxRestrictions`와 앱의 `distractionOptimized` 선언에 따라 Driving restriction policy, Home screen, App library에서 사용을 제한하는 정책이다.
  - **Driving restriction policy**: `CarDrivingStateService`와 `CarUxRestrictionsManagerService`를 중심으로, 기어·속도 기반 주행 상태에 따라 앱 실행, 사용자 조작, 프로필 전환을 제한한다.
  - **Driving state**(`CarDrivingStateService`): `parked`(기어 PARK), `idling`(비주차·속도 0), `moving`(비주차·속도 非0), `unknown`(판단 불가)의 4가지다.
  - **CarUxRestrictions**: "idling·moving에서는 `requiresDistractionOptimization`과 `no_setup|no_video` 등을 적용해 앱·설정·프로필 조작을 제한"한다.
  - **App Library**는 Distraction Optimized 앱을 관리한다.

### 2.2 DO 미선언 시 기본 동작 (PG:driving-restiction-policy)

| 상황 | DO 미선언 시 앱 사용 |
|---|---|
| Parking(주차) | 사용 가능 |
| Drive(주행) | **사용 불가** |

### 2.3 지역별 기능 허용 범위: 주행 규제가 적용된 상태 (PG:driving-restiction-policy)

범례: ✅ 사용 가능, ⛔ 사용 제한, 음성한정은 음성으로만 사용 가능하다. 원문은 "주행 상황(Parking, Idling, Driving)"이라고 쓰지만 표에는 **Parking과 Driving 행만** 있다.

| 기능 (해당 플래그) | 북미 P | 북미 D | 유럽 P | 유럽 D | 그외 P | 그외 D |
|---|---|---|---|---|---|---|
| 키보드 — `UX_RESTRICTIONS_NO_KEYBOARD(8)` | ✅ | ⛔ | ✅ | ✅ | ✅ | ✅ |
| 다이얼패드 — `UX_RESTRICTIONS_NO_DIALPAD(1)` | ✅ | ⛔ | ✅ | ✅ | ✅ | ✅ |
| 검색 — `UX_RESTRICTIONS_NO_KEYBOARD(8)` + `UX_RESTRICTIONS_NO_FILTERING(2)` | ✅ | **음성한정** | ✅ | ✅ | ✅ | ✅ |
| 미디어 앱 로그인, 설정, 검색 — `UX_RESTRICTIONS_NO_SETUP(64)` + `NO_KEYBOARD(8)` + `NO_FILTERING(2)` | ✅ | ⛔ | ✅ | ⛔ | ✅ | ✅ |
| 디지털키, 도어, 시트, 차량 설정 — `UX_RESTRICTIONS_NO_SETUP(64)` | ✅ | ⛔ | ✅ | ⛔ | ✅ | ⛔ |
| 동영상 시청 — `UX_RESTRICTIONS_NO_VIDEO(16)` | ✅ | ⛔ | ✅ | ⛔ | ✅ | ⛔ |
| 내비게이션 검색 — `NO_KEYBOARD(8)` + `NO_FILTERING(2)` | ✅ | ⛔ | ✅ | ✅ | ✅ | ✅ |
| QR 로그인 — `UX_RESTRICTIONS_NO_SETUP(64)` | ✅ | ⛔ | ✅ | ⛔ | ✅ | ⛔ |
| 매뉴얼(대용량 텍스트) — `UX_RESTRICTIONS_LIMIT_CONTENT(32)` + `UX_RESTRICTIONS_LIMIT_STRING_LENGTH(4)` | ✅ | ⛔ | ✅ | ⛔ | ✅ | ⛔ |

- 표의 「검색」은 웹 검색이 아니라 **차량 인포테인먼트 시스템 안의 통합 검색**이다(GL의 Global Search: Status bar 검색 아이콘에서 진입).
- 원문 표에 **한국 열은 따로 없다**("북미 / 유럽 / 그외 지역").
- InfoLINK용 앱은 지역별 키보드 정책에 따라 분기 처리가 필요할 수 있다(문의: partnership@pleos.ai).
- `LIMIT_STRING_LENGTH`와 `LIMIT_CONTENT`의 구체적 길이·항목 수 값은 **문서에 없다.** 디자인 가이드의 텍스트 길이 규칙(§2.7)과 명시적으로 연결되어 있지도 않다.

### 2.4 주행 규제 해제 조건: InfoLINK 문서 기준 (DR-IL)

이 내용은 "InfoLINK 추가 구현 항목" 문서에 있지만 "Pleos Connect의 주행 규제 적용 여부는…"이라는 문장으로 서술된다.

| 지역 | 해제 조건 | 비고 |
|---|---|---|
| **북미** | ① 시동(IGN1) 또는 엔진(구동 준비)이 OFF일 때, 또는 ② 속도 **≤ 5 km/h**이면서 다음 중 해당 조건: 자동 변속기(Auto)는 기어 **P**, 수동 변속기(Manual)는 **Parking Brake ON + 기어 N** | **Auto Hold는 정차로 인정하지 않는다.** 물리적 기어 상태로 판단한다. |
| **북미 외** | ① 시동(IGN1)/엔진 OFF, 또는 ② 속도 **≤ 5 km/h**이면서 다음 중 **하나라도**: 기어 **P** / **Parking Brake ON** / **Auto Hold Active**(정차 유지 중, 장착 차종) | 위 조건을 모두 만족하지 않거나 속도가 **5 km/h를 넘으면** 규제가 ON이 되어 서비스 이용이 제한된다. |

- 지역 식별: 앱은 **`product.sw.target_region`** 으로 지역을 구분해 같은 UX 정책을 맞춘다. 값은 한국 `KOR`, 유럽 `EUR`, 북미 `USA`, `CAN`이다(DR-IL).

### 2.5 QR 코드 화면별 주행 중 규제 (DR-IL)
- 해당 플래그는 **`CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP_QR_SCAN`** 이고, 사유는 "휴대전화 활용 유도(QR 코드)"다.

| 모드 | 화면/시나리오 | 주행 중 잠금 처리 | 북미 | 유럽 | 한국 | 기타 |
|---|---|---|---|---|---|---|
| 연결(블루투스) | BYOD > QR 코드 | QR 이미지를 다른 이미지로 대체 | ⛔ | ⛔ | ✅ | ⛔ |
| 연결(Wi-Fi) | Wi-Fi 연결 > URL QR 코드 | 콘텐츠 영역 차단 | ⛔ | ⛔ | 규격 미정 | ⛔ |
| S/W 업데이트 | What's New > QR 코드 | 콘텐츠 영역 차단 | ⛔ | ⛔ | ✅ | ⛔ |
| 매뉴얼 | QR 코드 | 콘텐츠 영역 차단 | ⛔ | ⛔ | ✅ | ⛔ |
| 고객센터 | 설정 > 인증 코드 > QR 코드 | QR 이미지를 다른 이미지로 대체 | ⛔ | ⛔ | ✅ | ⛔ |
| 고객센터 | 활성화 > 인증 코드 > QR 코드 | QR 이미지를 다른 이미지로 대체 | ⛔ | ⛔ | ✅ | ⛔ |
| 차량 관리 | 서비스 점검 > QR 코드 | 콘텐츠 영역 차단 | ⛔ | ⛔ | ✅ | ⛔ |
| 프로필 | QR 코드 | QR 이미지를 다른 이미지로 대체 | ⛔ | ⛔ | ✅ | ⛔ |

### 2.6 미디어 앱에 대한 심사 규칙 (RV:__review-driver-distraction-elements)
> 여기서 "미디어 앱"은 **동영상, 게임, 브라우저 기능을 가진 앱**을 뜻한다. 음악 앱은 이 정의에 들어가지 않는다.
- [ ] 모든 미디어 앱은 **운전 중에 실행하거나 사용할 수 없고**, **정차 중에만** 사용할 수 있다.
- [ ] 운전이 시작되면 사용을 **일시 중지**하고 **앱 UI를 숨긴다.** 운전 중에는 동영상·게임·브라우저의 "일시 중지"를 해제할 수 없다.
- [ ] 백그라운드에서 돌아오면 마지막 상태를 복원한다. 수 분 안에 돌아오면 최대한 가깝게 복원하고, 수 시간 이상 지났으면 마지막 상태나 기본 상태에서 시작해도 된다.
- [ ] 모든 미디어 항목에 한눈에 들어오는 **제목과 썸네일**을 제공하고, 부적절한 내용은 출력하지 않는다.
- [ ] 미디어 앱은 **헤드업 알림(HUN)을 게시하면 안 된다.** 운전과 관련 없는 알림은 쓸 수 없다.
- [ ] 동영상 앱은 **미디어 세션과 통합**한다. 여러 미디어를 동시에 재생할 수 없고, 콘텐츠에 따라 재생·일시정지·중지 명령을 지원해야 한다.
- [ ] 브라우저 앱은 비밀번호나 결제 정보 접근을 저장하거나 허용하면 안 된다. 예외는 프로필 잠금으로 비밀번호 접근을 차단할 수 있는 경우다. 비밀번호·결제 데이터를 동기화하는 브라우저는 동기화 전에 인증 메시지를 띄우고, 자동차 화면에서 동기화된다는 사실을 알린다.

### 2.7 텍스트·시각 요소 제한: 디자인 가이드 (DS:Introduction__intro, DS:Introduction__designing-apps-for-driving)
| 항목 | 기준 |
|---|---|
| 영어 단일 메시지/텍스트 | **120자(영문 기준) 또는 24단어(영문 기준) 이하** |
| 일본어 단일 메시지/텍스트 | **30자(영문 기준) 이하** |
| 한 줄 텍스트 | **80자(영문 기준) 이하** |
| 문장/단락 | **텍스트 3줄 이하** |
| 금지 | 운전 중 **동영상, 애니메이션 이미지, 텍스트 자동 스크롤**로 시선 유도 |
| 한국어 길이 기준 | **문서에 없음** |

### 2.8 주의분산 방지 UX 원칙 (PG:driver-distraction-guideline)
- **시선 분산 최소화**: 짧은 시간에 이해되도록 간결하게 쓴다. HUD·클러스터·OSD 영역을 쓸 때는 해당 영역의 정책을 따른다. 주행 중에는 동영상·애니메이션·움직이는 이미지를 최소화하거나 제한한다. 장식적이거나 반복적으로 움직이는 시각 요소는 쓰지 않는다.
- **정보 구조·인터랙션**: 자주 쓰는 기능은 첫 화면과 상단에 둔다. 드롭다운과 다단 메뉴는 남용하지 않는다. 자동 애니메이션과 반복적 화면 전환을 최소화한다. 운전에 중요한 오디오 안내가 나오면 오디오 포커스 정책에 따라 대응한다.
- **터치 조작 최적화**: large touch target, high contrast, 텍스트 최소화 패턴을 쓴다. 음성 명령과 물리 버튼 연계를 지원한다. **게임, 인터넷 서핑, 피트니스** 등 주의를 분산시키는 활동은 유도하지 않는다. 버튼 크기와 간격은 UI Design Guide를 따른다("버튼 보러 가기" 링크가 있으나 **수치는 텍스트에 없다**).
- **텍스트 입력 최소화**: 검색과 로그인에서도 프리셋 선택, 최근 항목, 음성 입력을 먼저 쓴다.

---

## 3. 앱 구조·화면 모델, 하드웨어·SDK·호환성·보안·성능

### 3.1 화면 구성과 멀티 윈도우 (PG:app-structure-screen-model, DS:getting-started__layout, GL)
- 화면 구성 요소:
  - **Status bar**: Driving status, Gleo AI와 검색, 시계, 네트워크, 마이크 상태, 알림 센터, 프로필 선택
  - **Driving view**: Telltale, 속도, 차량 상태 등 주행·안전 정보. 운전자 가까이 고정되어 있으며 **앱이 가리거나 종료할 수 없다.**
  - **GNB**: App library, 즐겨찾는 앱, 공조. 하단에 상시 표시된다.
  - **Application window**: 앱 **1개 또는 2개**
- **레이어**: Status bar, GNB의 App library·공조 패널, 키보드 같은 System UI는 앱보다 위 레이어라서 앱을 일시적으로 가릴 수 있다.
- 가로 전용 게임처럼 한 모드로만 실행 가능한 앱을 빼면, **모든 앱은 Landscape와 Portrait 두 레이아웃에 맞게 설계**해야 한다.
- **portrait**: window 1개를 쓴다. 두 앱을 동시에 표시하면 각 앱은 일반적으로 portrait가 되며, 핵심 기능과 주요 정보를 둔다.
- **landscape**: 앱 영역 2개를 확장해 쓴다. 부가 정보와 확장 기능을 둔다.
- multi window는 최대 **2개** 앱의 TaskView를 배치한다(GL: Window policy, TaskView).

### 3.2 화면 방향·크기 (PG:app-structure-screen-model)
- `screenOrientation`을 지정하지 않으면 Activity는 **portrait로 window 1개**를 쓴다.
- `screenOrientation`을 **landscape**로 지정하면 **window 2개 영역**을 쓸 수 있다.
- 반응형 레이아웃을 써서 방향이나 window 크기가 바뀌어도 레이아웃이 깨지지 않게 한다. 사용자는 window bar나 제스처로 위치와 크기를 바꿀 수 있다.

### 3.3 Window Policy: 디스플레이별 (DS:getting-started__window)

| 항목 | Connect-S | Connect-W |
|---|---|---|
| 화면 구성 | 앱 2개: **1:1** / 앱 1개: **2:0** (기본값: 1:1) | **2:1 또는 1:2** (기본값 2:1) |
| 기본 화면 | 최초 실행과 재탑승 모두 Primary는 Navigation 앱 | 최초: Primary Navigation, Secondary Media / 재탑승: Primary Navigation, Secondary는 마지막에 쓴 Media 앱 |
| 앱 실행 시 | 신규 앱은 **이전 앱의 orientation을 따른다.** landscape 1개가 실행 중이면 landscape로, portrait 2개가 표시 중이면 primary window에 portrait로 연다. | 신규 앱은 **항상 landscape로 primary window**에 열린다. 기존 primary는 secondary로 가고, 기존 secondary는 사라진다. |
| 위치 변경 | **3 핑거 스와이프**나 윈도 바 탭 | 동일 |
| 크기 변경 | 3 핑거 스와이프, 윈도 바 드래그, 앱 윈도 버튼 탭으로 landscape↔portrait 전환 | 동일 |
| 앱 종료 | 3 핑거 스와이프로 종료하면 오른쪽에서 왼쪽으로 최근 앱이 표시된다. landscape window가 닫히면 landscape로, portrait가 닫히면 portrait로 채운다. | 3 핑거 스와이프로 종료한다. primary가 닫히면 secondary가 orientation을 유지한 채 primary로 이동한다. secondary가 닫히면 최근 앱이 종료된 앱의 orientation으로 표시된다. |
| 공통 | 화면에서 사라진 앱은 **종료되지 않는다.** 디폴트 앱인 Navigation은 종료할 수 없다. 모든 앱이 종료되면 IVI 최초 실행 화면이 된다. | 동일 |

- Connect-W: 최대 실행 앱은 **2개**다. App library는 운전석과 조수석에 모두 있고, 조수석 App library에서 실행한 앱은 **조수석 윈도에서만** 열린다. Status bar는 운전석 GNB와 앱 영역 사이에 있다.
- 알아두기: 프리로딩 앱은 landscape와 portrait를 모두 지원한다. Driving view의 landscape view는 삭제되었고 resize handle도 없다. **Deep link(App to App)**: App A에서 App B를 실행하면 B가 A를 덮고, B가 끝나면 A로 돌아온다. 후방카메라처럼 3:0 window를 full screen으로 쓰는 예외 케이스는 **적용 예정**이다.
- Full Screen Mode / Entertainment mode(GL): **Parking 상태**에서 진입 UI를 띄운다. 진입하면 TaskView가 전체 bounds로 확장되고 System UI가 숨는다. 기어 변경·브레이크·도어 개방 같은 조건이나 timeout에 걸리면 자동 해제된다. 서드파티 앱이 이걸 어떻게 쓰는지는 **문서에 없다.**

### 3.4 디스플레이 유형 (DS:getting-started__layout, GL)
| 유형 | 설명 |
|---|---|
| Connect-S | OS 기본 크기(Standard). **17인치, 14.6인치, 12.9인치(구 Connect-L)**. GL에는 "14.5인치"로 적혀 있다(§8). |
| Connect-W | Wide. 가로가 길고 주로 고급 차종에 탑재된다. 세로가 확장되면 **Connect-W-Extended**가 된다. |
| Connect-WF | Waterfall. Wide 대비 작은 해상도다. |
- 디스플레이별 px 해상도는 **텍스트 원문에 없다**("Pleos Connect display guide" 다운로드 링크만 있다).

### 3.5 포그라운드/백그라운드 동작 (PG:app-structure-screen-model)
- 포그라운드로 진입하면 상태를 복원하고 현재 크기와 방향에 맞게 UI를 다시 구성한다.
- 백그라운드로 가면 화면 갱신, 애니메이션, 불필요한 센서 수집처럼 바로 멈출 수 있는 작업을 정리한다.
- 오디오 재생·통신·다운로드처럼 백그라운드에서 계속할 작업은 실행 조건과 중단·재개 정책을 명확히 정한다.
- 백그라운드 상주, 타이머, 폴링, 네트워크 요청을 최소화한다.
- 프로세스가 종료되거나 재생성돼도 사용자 데이터와 진행 상태를 복원할 수 있게 상태 저장 전략을 마련한다.

### 3.6 하드웨어 접근 정책 (PG:platform-api-architecture)
- **직접 접근 금지**: 차량의 물리 제어나 센서에 직접 접근하지 않는다. **플랫폼 API 우선**: 차량 상태는 Pleos Connect SDK 표준 플랫폼 API로 확인하고 제어한다.
- 모바일 API 중 제한될 수 있는 것: **Telephony(전화), SMS, 일부 센서(자이로, 근접 등), 캘린더, 주소록** 등. 차량에서 필수가 아니면 제거하고, 아니면 지원 여부를 확인한 뒤 동작하는 **Optional 구조**로 만든다.
- 주행 중 네트워크: 셀룰러나 Wi-Fi가 끊기거나 대역폭이 떨어질 수 있다. 권장 대응은 캐싱(최근 콘텐츠 로컬 저장), 재시도(Retry)와 적절한 타임아웃, 오프라인 모드 안내 UI다.

### 3.7 SDK API 정책 (PG:pleos-connect-sdk-api)
- **AAOS Alignment**: 같거나 비슷한 기능이 AAOS 표준에 있으면 표준 API를 쓰고, SDK는 중복 기능을 제공하지 않는다. 표준으로 접근할 수 없는 영역이나 제조사 고유의 보안·특화 제어에만 SDK 전용 API를 제공한다.
- **하위 호환**: SDK API가 AAOS 표준으로 편입되어도 기존 SDK API는 **최소 2개 메이저 버전**까지 하위 호환을 유지한다.

| 구분 | 접근 경로 | 예시 |
|---|---|---|
| 표준 데이터 | **AAOS Car API** | 속도, 기어 상태, 외기 온도, 연료 레벨 등 |
| 특화 기능 | **Pleos Connect SDK** | 제조사 전용 시트 제어, 특화 라이팅, 고유 보안 인증 등 |

### 3.8 API 호환성·가용성: 차종별 (PG:api-compatibility-availability)
| SDK | 차량 모델별 호환성 | 확인 방법 |
|---|---|---|
| Vehicle SDK | 모델에 따라 다름 | 도메인별 **`checkXXXCapabilities`** API |
| NaviHelper SDK | 내비게이션이 탑재된 차량에 한해 전체 호환 | – |
| TextToSpeech SDK, SpeechToText SDK | 전체 호환 | – |
| FusedLocation SDK | 모델에 따라 다름 | **`isSupported`** API |
- AAOS Vehicle Property는 `VehiclePropertyIds`(정의·ID)와 `CarPropertyManager`(실제 지원·접근 가능 여부)로 확인한다. **모델명 기준으로 분기하지 않는다.**
- SDK에 없는 특수 프로퍼티가 필요하면 직접 구현하기 전에 **기술 지원팀 검토를 반드시** 요청한다.
- 차종별 실제 API 지원 매트릭스는 **문서에 없다.**

### 3.9 보안·개인정보: 설계 단계 (PG:security-privacy)
- 차량은 공용 기기다. 소유자 변경이나 운전자 교체 때 쉽게 **로그아웃하고 계정을 전환**할 수 있어야 한다.
- 소셜 로그인·OTT 계정 토큰은 **Keystore** 같은 안전한 저장소에 둔다.
- 위치·음성·계정 정보를 요구할 때는 "왜 필요한지"를 밝히고, 동의 플로우는 간단하게 만든다. 불필요한 PII는 저장하지 않고 로그에서는 마스킹한다.
- Telemetry를 쓸 때는 차량 ID와 사용자 ID의 익명화·가명화 정책을 세우고, 운전 중 행동 데이터를 **마케팅 목적으로 쓰지 않는다.**
- 상세 기준은 §6.4 앱 보안 가이드에 있다.

### 3.10 성능·리소스 (PG:performance-resource-mgmt)
| 항목 | 기준 |
|---|---|
| CPU: 내비게이션 앱(주행 중) | **15% 이하** |
| CPU: 비내비 앱(주행 중) | **5% 미만** |
| CPU: 모든 앱(정차 중) | **25% 이하** |
| RAM | **400 MB 이하** |
| Cold start | 스플래시에 오래 머물지 않게 최소한만 초기화하고 나머지는 lazy loading |
| 기타 | 백그라운드의 긴 작업·타이머·폴링 최소화, 리사이클 뷰와 캐시 전략으로 메모리 최적화 |
| 오디오 | Pleos Connect 오디오 포커스 정책 준수, 포커스 획득·상실 대응 로직 **반드시** 구현 |
- CPU 측정 방식(코어 기준인지 전체 기준인지, 측정 구간)과 RAM 지표(PSS 등)는 **문서에 없다.**

---

## 4. 디자인 시스템 핵심 규칙

### 4.1 기본 원칙 (DS:Introduction__intro, DS:Introduction__designing-apps-for-driving)
- **Easy & Safe**: 시각적·인지적 부담을 최소화한다. 동영상, 애니메이션 이미지, 텍스트 자동 스크롤로 시선을 끌지 않는다. **한 손 조작**을 원칙으로 하고 양손 조작은 금지한다. 손이나 발을 움직이게 하는 동작은 유도하지 않는다.
- **Consistency**: 아이콘, 용어, 인터랙션 패턴을 일관되게 쓰고 기본 탑재 UI와 맞춘다.
- **Glanceability**: 운전자가 **1~2초** 안에 파악할 수 있어야 한다. 입력 후 응답은 **0.25초 이하**이고, 지연되면 응답 중 상태를 시각적으로 보여준다.
- Connect-W는 좌석에 따라 특화된 UI와 인터랙션을 제공한다.

### 4.2 인터페이스 수치 (DS:Introduction__intro)
| 항목 | 기준 |
|---|---|
| 대비율(아이콘·텍스트·이미지) | **4.5:1 이상** |
| 중요 정보 텍스트(속도, 내비 안내, 경고 등) 높이 | **5 mm 이상** |
| 보조 텍스트 높이 | **최소 3 mm 이상** |
| 텍스트 길이 | §2.7 참조 |
| 아이콘 | 국제적으로 통용되는 직관적 아이콘에 **텍스트 라벨을 함께** 단다 |
| 터치 피드백 | 햅틱 또는 음성 피드백 |

### 4.3 알림 (DS:Introduction__intro, DS:components__notification__vehicle-notification, GL)
- 알림은 간결해야 하고 **운전자의 필요와 관련된 경우에만** 표시한다. 알림에 **광고를 넣거나, 광고주명·제품명 외의 텍스트 광고를 표시하면 안 된다.**
- 알림 유형(GL): Vehicle notification, HUN(Heads-Up Notification: 통화·메시지·내비처럼 즉시 인지가 필요한 알림), Toast, Dialog popup이 있다. Notification center가 프로필별 정렬·그룹화·삭제를 관리한다.
- Vehicle Notification(Driving Notification) 구성: Root, Telltale, Title text, Description text, Guide image. Properties와 Position 내용은 **텍스트에 없다.**

### 4.4 유연성·확장성 (DS:Introduction__intro)
- 야간/주간 모드에 맞춰 UI 밝기와 대비를 자동 조정한다.
- 음성 인터랙션(Gleo AI)을 활용할 수 있는지 검토한다. 핸즈프리로 말하고 들을 수 있어야 하며, 음성 대화의 시작·종료·중단은 수동으로도 할 수 있어야 한다.
- OTA 업데이트를 고려한다. 다국어를 지원하고 다양한 해상도와 비율에 대응한다.

### 4.5 Driving view (DS:Experiences__driving-view, GL)
- 구성:
  1. **Indicator**: Telltale, 도로표지판, 에너지, 자율주행 indicator, 회생제동 indicator, 도어 잠금 switch
  2. **Driving information**: 변속단, 속도, 에너지/퍼포먼스 게이지, 주행거리계, **miniTBT**, 드라이빙모드
  3. **View contents**: 차량 외부 컨트롤, 근접 센서·주변 물체, 차선·노면
- GL: Driving view는 AD(3D 주행 보조), Driver information(cluster overlay), Vehicle control(3D 차량)로 이루어진다. **Mini app**은 Driving view 하단의 소형 overlay로, Media control·TPMS·Call 등이 있으며 **AppWidget 기반**이다. **OSD**는 Mini app 위에 icon·text·level bar로 나온다.
- 서드파티 앱이 Mini app이나 Driving view에 기여할 수 있는지는 **문서에 없다.**

### 4.6 Gleo AI 경험 (DS:Experiences__gleo-ai)
| Status | 정의 |
|---|---|
| Sleep | 대기 중, 대화하지 않음 |
| Listening_Passive | 입력 대기 상태. **음성이 2초간 없으면** "운전석에서 듣는 중..." 같은 좌석 메시지를 표시 |
| Listening_Active | 사용자가 발화 중 |
| Thinking | 발화 종료 후 처리 중 |
| Asking | 응답 전에 되묻는 중 |
| Speaking | 최종 응답 제공 중 |
| Standby | 대화 종료 후 Sleep 전 대기. 응답 화면이 있으면 **5초**, 없으면 **0초** 유지한 뒤 자동으로 Sleep |
- Chrome(상태 표시)은 **lottie** 애니메이션이다.
- **Elevation**: Gleo는 OS layer에서 **GNB 바로 아래** 레이어에 있다. 표시되면 **다른 앱 뷰를 일시적으로 가릴 수 있다.** 대화는 PTT, wake word("Gleo/글레오"), 또는 proactive suggestion으로 시작된다.
- Layout: Chrome, Bubble, Gleo Panel(Template은 list와 card, Chips는 선택지).
- Panel 터치: list item이나 chip을 탭하면 기능이 실행된다. 위로 드래그하거나 바깥을 터치하면 대화가 끝난다. 아래로 드래그하면 Gleo AI App으로 전환된다.
- **CCP(Center Control Panel)**: 조그 다이얼(rotate, tilt, push)이다. Gleo 대화는 OS CCP 정책상 "팝업"으로 취급되어 tilt가 제한되고 Gleo가 포커스를 먼저 받는다.
- 화면 크기 변형: Standard 14.6, Standard 17, Wide Standard, Wide Extended, Waterfall. Wide에서는 발화 좌석 가까이에 표시된다. 각 레이아웃 수치는 **텍스트에 없다.**
- 서드파티 앱이 Gleo 패널이나 템플릿에 연동하는 방법은 이 범위 문서에 **없다**(GL에는 LLM SDK, STT/TTS SDK 존재만 적혀 있다).

### 4.7 App library·Widgets·Theme (DS:components__app-library, DS:components__widgets, DS:Introduction__theme)
- App library: Connect-S 기준 **한 줄에 최대 7개**이고, 2줄을 넘으면 좌우 이동 인디케이터가 생긴다. 상단은 차량 제어, 하단은 앱 실행이다. 편집 모드에서는 기본 앱과 GNB 고정 앱이 비활성화된다. 국가 법규에 따라 Frost/Defrost가 GNB에 필수로 들어갈 수 있다.
- GL: GNB의 Recent apps는 **2슬롯**이다. Favorite apps 중 Vehicle settings와 Navigation은 always-favorite이다.
- Widgets 구성: Root, Handle(아래로 끌어 숨김), Pagination, Title/Action area, Information area. Properties와 Interaction은 **텍스트에 없다.**
- Theme: 아이콘·폰트·컬러 중심으로 브랜드 아이덴티티를 유지한다. 세부 내용은 **텍스트에 없다.**

### 4.8 시스템 입력 장치 (GL)
- **PTT**(핸들 물리 버튼), **IVI button bar / PnP**(센터 디스플레이 하단의 물리 버튼·다이얼, 온도·볼륨·미디어 0-depth 제어), **CCP**, **Keyboard App**(한/영, 와이드 키보드, 음성 입력).

### 4.9 텍스트 원문에 없는 디자인 수치 (확인 필요)
- 레이아웃 그리드, 컬럼, 마진, 거터
- 창(window)별 px/dp 크기와 디스플레이 해상도
- 최소 터치 타깃 크기와 버튼 간격("버튼 보러 가기" 링크만 있음)
- 폰트 패밀리와 sp 크기("글자 사이즈 가이드 보러 가기" 링크만 있음), 색상 토큰("색상 가이드 보러 가기")
- 위 수치는 "Pleos Connect display guide / design assets" 다운로드나 해당 하위 페이지에서 받아야 한다.

---

## 5. 앱 심사 가이드 거부 사유 체크리스트

> 공통 (RV:.md): 운전자 주의분산과 사이버보안을 중점으로 검증한다. 가이드를 전부 지켜도 위험 요소가 완전히 배제되는 것은 아니며, 추가 위험이 있다고 판단되면 거부될 수 있다.

### 5.1 운전자 주의 분산 요소 (RV:__review-driver-distraction-elements, RV:.md)
- [ ] Pleos Connect UI Design Guide를 사전에 확인하고 준수한다.
- [ ] 미디어 앱(동영상·게임·브라우저)을 운전 중에 실행하거나 사용할 수 없게 하고, 정차 중에만 쓰게 한다.
- [ ] 운전이 시작되면 일시정지하고 UI를 숨긴다. 운전 중 재개는 불가능해야 한다.
- [ ] 백그라운드에서 돌아오면 마지막 상태를 복원한다.
- [ ] 미디어 항목마다 제목과 썸네일을 둔다(한눈에 들어오고 부적절하지 않게).
- [ ] 미디어 앱은 HUN을 쓰지 않는다. 운전과 무관한 알림을 쓰지 않는다.
- [ ] 동영상 앱은 미디어 세션과 통합하고, 동시 재생을 막고, 재생·일시정지·중지를 지원한다.
- [ ] 브라우저는 비밀번호·결제 정보를 저장하지 않는다(프로필 잠금 예외). 동기화할 때는 인증 메시지와 동기화 고지를 띄운다.

### 5.2 콘텐츠 안전 (RV:__review-content-safety)
- [ ] 과도한 선정성·폭력·불쾌감을 주는 콘텐츠가 없다. 공익이나 ESDA(교육, 과학, 다큐멘터리, 예술) 목적이면 예외다.
- [ ] 미디어 중계 플랫폼(OTT, 웹툰 등)과 UGC 앱에는 **필터링과 신고 채널**이 있다.
- [ ] **안전 저해** 콘텐츠가 없다: 음주 운전·과속·신호 위반을 권유·조장·미화하지 않는다. 주류 광고는 현지 광고법을 따르고 음주운전 위험 경고를 넣는다.
- [ ] 선정적·폭력적·위험(자살, 자해, **아동이 차량 밖으로 신체를 내밀도록 유도**)·불법(범죄, 테러, 약물, 무기, 인신매매)·사행성(실제 금전 도박)·차별·민감(재난·사고·전쟁·전염병으로 이익을 얻는 등) 콘텐츠가 없다.
- [ ] 승인되지 않은 물품(식·약품, 의료기구, 처방약)을 판매하거나 홍보하지 않는다.
- [ ] 아동 학대·착취가 없다. 아동의 관심을 끌면서 성인용 주제를 담은 앱은 금지다.
- [ ] 인증 없이 보이는 화면, 미리보기, 썸네일, 스크린샷에는 **"전체 이용가"** 콘텐츠만 노출한다.
- [ ] 연령 등급 설문에 정직하게 답한다.
- [ ] 지적재산권을 침해하지 않는다. 신고되면 다운로드가 먼저 차단되고 기간 안에 소명하지 못하면 영구 제외될 수 있다.
- [ ] **UGC·AI 생성 콘텐츠** 앱은 부적절 콘텐츠를 필터링하고 개발자에게 알리는 신고 기능을 넣는다.
- [ ] 암호화폐 채굴 앱은 거부된다.
- [ ] 루트 박스·확률형 아이템은 구입 전에 확률을 공개한다.

### 5.3 기능·성능·안정성 (RV:__review-functionality-performance-stability)
- [ ] 설치·삭제 시 오류가 없다(인증 키 누락, 파일 누락, 삭제 후 잔여 아이콘·파일이 없어야 한다).
- [ ] 실행 시 강제 종료나 하드웨어 재시작이 없다. 사용 중 화면이 멈추지 않고, 기능이 동작하고, 터치가 인식된다.
- [ ] 미디어 출력이 정상이다(사운드 끊김, 그래픽 깨짐이 없다).
- [ ] 제출 앱이 실제 서비스 앱과 같다. **더미 데이터, 임시 파일, 임시 콘텐츠가 없다.**
- [ ] 화질 저하(왜곡, 흐림, 모자이크)가 없다. 안전 영역(UI 세이프티 존, 입력 방법, 컷아웃, 자막, 탐색 메뉴)을 지킨다.
- [ ] 카테고리에서 벗어나는 기능이 없다.
- [ ] 스터터링, 프레임 저하, 화면 끊김이 없고 시스템 성능을 떨어뜨리지 않는다.
- [ ] 입력 후 응답이 **0.25초** 이하다(터치→터치 효과 애니메이션).
- [ ] 실행 **10초** 이내, 콘텐츠 로드 **10초** 이내, 버튼 반응 **2초** 이내다. 2초 안에 못 하면 동작 중임을 UI로 표시한다.
- [ ] 백그라운드에서 돌아오면 마지막 상태를 복원한다(수 시간 이상 미사용 시에만 기본 상태 허용).
- [ ] 하드웨어나 소프트웨어를 손상시키거나 과점유하지 않는다. **기기 재시작이나 핵심 기능과 무관한 시스템 설정 변경을 제안·요구하지 않는다.**
- [ ] **Vehicle SDK 퍼미션**은 App Market과 사전 협의해 승인받은 것만 선언한다. 미승인 퍼미션을 선언하면 불통과다.
- [ ] 디스플레이 호환: 레터박스 없이 화면을 채운다(미세 비율 보정용 한 방향 레터박스만 허용). **호환성 모드로 실행되지 않는다.**
- [ ] 해상도가 바뀌어도 상태 손실이나 화면 이상(늘어짐, 앨리어싱, 블러, 터치 영역 불일치)이 없다.
- [ ] 모든 화면 상태에서 **기능 패리티**를 지킨다. 전환할 때 버튼이나 기능이 바뀌지 않는다.

### 5.4 개인정보·보안 (RV:__review-user-privacy-security)
- [ ] 접근권한을 최소화한다.
- [ ] 법적 고지(수집 항목과 목적, 보유기간, 거부권)를 하고 명시적 동의 절차를 둔다.
- [ ] 개인정보 처리방침을 공개하고 열람청구권 등 권리를 보장한다. GDPR 등 국가별 법령을 따른다.
- [ ] 위치정보 서비스는 서비스 국가의 등록·신고 증빙을 요청받을 수 있다. 거짓이면 판매가 중단된다.
- [ ] **투명성**: 수집 데이터 목록, 사용 방식, 공유 내용을 앱 안에 명시한다.
- [ ] 아동 정보를 수집하기 전에 법정대리인의 명시적 동의를 받는다.
- [ ] 동의 UI 필수 요건: 모호하지 않게 만들고, **동의 버튼을 누르는 것 외의 행동(뒤로가기, 빈 공간 터치 등)을 동의로 간주하지 않으며**, 허가된 항목에만 접근한다.
- [ ] 맞춤형 광고는 동의를 받은 경우에만 한다. 철회하면 **즉시 활용 경로를 끊고 저장 데이터를 삭제**한다.
- [ ] 금지 행위: 바이러스, 스파이웨어, 트로이 목마, 애드웨어, 루팅, 스팸, DDoS, 크립토재킹, 피싱, 사기광고, 동의 없는 코드 설치, 보안 우회. 발견되면 즉시 거부될 수 있다.
- [ ] SDK를 악용하거나 데이터를 무단으로 쓰지 않는다.

### 5.5 등록정보 (RV:__review-app-registration-info)
- [ ] 기본 정보(이름, 설명, 개발자명, 아이콘, 이미지)를 정확하게 쓴다. 욕설, 선정, 범죄 묘사가 없어야 한다.
- [ ] 실적, 순위, 매출처럼 확인할 수 없는 내용이 없다. **텍스트 이모티콘이나 반복 특수문자가 없다.**
- [ ] 다른 앱과 유사하거나 중복되거나 오해를 부르는 제목·아이콘이 없다. **앱 제목은 30자(영문 기준) 이하**다.
- [ ] 서비스 국가 언어별로 모든 메타데이터를 제출한다.
- [ ] 아이콘: **최대 1 MB, 512 x 512 px, PNG 또는 JPEG**
- [ ] 스크린샷: **최소 2장, 최대 8장**. 장당 **최대 8 MB**, **2560 x 1380 / 1700 x 1212 / 840 x 1212 px**, PNG/JPEG. 업로드 순서대로 슬라이드된다. 실제 앱과 다르면 거부된다.
- [ ] 패키지는 **APK**이고 다운로드 압축 크기는 **600 MB 미만**이다.
- [ ] 개인정보 처리 과정(수집, 이용, 저장, 파기, 생성, 연계, 전송), 접근 권한(카메라, 저장공간, 마이크, GPS, 전화, 연락처), 위치정보 여부, 광고성 정보 전송 여부를 정직하게 입력한다.
- [ ] 개인정보처리방침을 등록한다(회사 공통 방침도 가능). 위치정보를 쓰면 "위치정보 이용약관 및 기타 정보 처리방침"도 낸다.
- [ ] 로그인이 필요하면 **실제 사용자 기준의 전체 권한 계정**을 낸다. 관리자 계정이나 게스트 계정은 안 된다.
- [ ] 증빙: 타사 IP 사용 권리, 정부 관련 앱, 웹 앱 도메인 등록 확인증, 의료기관 제휴, 한국 성인 게임의 **GRAC** 등급 증빙.
- [ ] 메타데이터로 사용자를 속이면 **즉시 제외되고 영구 제재**될 수 있다.

---

## 6. 제출·등록·업데이트·버전·태깅·보안

### 6.1 제출 전 준비 (SUB)
1. 개발자 정보와 연락처를 최신으로 유지한다(심사팀이 언제든 연락할 수 있어야 한다).
2. 실행 불가, 크래시, 성능 저하, 버그, 이미지 깨짐, 해상도 늘어짐이 없는지 테스트한다. 과부하나 배터리 소모도 확인한다(§3.10 기준).
3. 보안 취약점과 멀웨어를 점검한다(§6.4).
4. 메타데이터를 확인한다: 앱 명칭, **버전명**, 카테고리 / 출시 국가, 연령 등급, 권한, 개인정보 / APK, 아이콘, 스크린샷 / 약관과 방침.
5. 모든 기능에 접근할 수 있는 계정을 준비한다.
6. 외부 서버 등 운영 요소를 **제출 전에 활성화**한다.
7. 운전자 안전 설계를 확인한다.
8. 출시 국가의 법률을 확인하고 증빙을 낸다.
9. 연령 등급은 **IARC** 등급 분류를 받아 증적을 제출한다. 게임은 국가별 별도 심의를 받는다(한국: 게임물관리위원회).
10. 심사 노트에 설명이 필요한 기능의 주석을 달고 기능 사용 설명서를 첨부한다.

### 6.2 Console 제출 항목 (SUB)
| 구분 | 항목 | 필수 | 내용 |
|---|---|---|---|
| 앱 식별 | 앱 관리명 | 필수 | 관리용(마켓에 노출 안 됨) |
| 앱 식별 | 버전명 | 필수 | 앱 버전(마켓에 노출 안 됨) |
| 기본 정보 | 국가/지역 | 필수 | 출시 국가/지역 |
| 기본 정보 | 패키지 파일 | 필수 | **APK, 최대 600 MB** |
| 기본 정보 | 대상 플랫폼 | 필수 | **Connect 또는 InfoLINK** |
| 기본 정보 | 카테고리 | 필수 | App Market 카테고리 |
| 기본 정보 | 연령 등급 | 필수 | – |
| 기본 정보 | 개인정보 처리 | 필수 | 수집·이용·파기·생성·연계·전송 등 |
| 기본 정보 | 위치정보 활용 | 필수 | GPS, 지도, 위치 |
| 기본 정보 | 앱 접근 권한 | 필수 | – |
| 기본 정보 | **SDK 추가 권한** | 필수 | 권한 신청이 필요한 SDK를 쓰면 **My Project > 프로젝트 정보 > 프로젝트 ID** 입력 |
| 기본 정보 | 광고성 정보 | 필수 | 푸시나 SMS로 전송하는지 여부 |
| 언어별 | 앱 이름 | 필수 | 출시 지역의 모든 언어로 |
| 언어별 | 개인정보처리방침 URL | 필수 | 지역 언어별로 |
| 언어별 | 위치정보 이용약관 등 URL | 조건부 필수 | 기본 정보에서 "위치정보"나 "GPS"를 골랐거나 위치정보 "활용함"이면 필수 |
| 언어별 | 간단한 설명 / 자세한 설명 | 필수 | 모든 언어로 |
| 언어별 | 태그 | 선택 | **최대 20개**, 태그 표기 가이드 준수 |
| 언어별 | 앱 아이콘 | 필수 | PNG/JPEG, 최대 1 MB, 512 x 512 px |
| 언어별 | IVI 스크린샷 | 필수 | 장당 최대 8 MB, PNG/JPEG, 최대 8개, 2560 x 1380 / 1700 x 1212 / "840 x 121"(원문 그대로, §8 참조) |
| 언어별 | 출시 노트 | 필수 | – |
| 고객 지원 | 웹사이트 / 이메일 / 전화번호 | 1개 이상 필수 | 지역 언어별로 |
| 심사 요청 | 심사 노트 | 선택 | 첨부 **최대 60 MB** |

### 6.3 업데이트 관리 (SUB)
- 심사를 통과한 뒤에도 계속 업데이트해야 한다. App Market은 주기적으로 샘플링해 모니터링한다.
- **재심사 대상**: 심각한 오류나 손상 보고가 있는 앱, **장기간 업데이트 없이 방치된 앱**, 다수 신고를 받은 앱. 기준에 못 미치면 제외된다.
- 버전 규칙(`versionCode` 증가 규칙, `minSdk`/`targetSdk` 요구치 등)은 이 범위 문서에 **없다.** Console에는 "버전명"만 입력한다.

### 6.4 앱 보안 가이드 (REG:app-security-guide)
> 지키지 않으면 심사를 통과하지 못할 수 있다. 아래에서 "해야"는 원문의 필수 표현, "권장"은 원문의 권장 표현을 따른 것이다.

**민감 데이터**
- 기기에 저장하는 민감정보는 **위치와 관계없이 암호화해야** 한다(내부·외부 저장소, 캐시, SQLite, SharedPreferences, 임시 파일과 사본 포함).
- 디버깅 로그에 민감정보가 남지 않게 하고, 출시 전에 로그를 비활성화하거나 마스킹**해야** 한다.
- 민감정보는 캐시에 두지 않는 것을 권장한다. 꼭 두어야 하면 암호화하고, 다 쓴 캐시는 삭제를 권장한다.
- 자동 백업: Pleos Connect는 **non-GAS AAOS** 환경이라 Google 자동 백업이 동작하지 않는다. 그래도 자동 백업 비활성화를 권장한다.
- 외부 저장소에 둘 때는 암호화**해야** 한다(내부 저장소 사용 권장).
- 토큰, 세션, 비밀번호를 **URL 경로나 쿼리에 넣으면 안 된다.** Authorization 헤더 등을 쓴다.

**암호화**
- 해시는 MD5나 SHA-1 대신 SHA-256을 권장한다.
- 암호 모드는 **AES-GCM**을 권장하고 ECB는 피한다. RSA는 OAEP(SHA-256)를 쓴다.
- 같은 키로 IV/nonce를 재사용하지 않는다. 암호학적으로 안전한 난수 생성기를 쓴다.
- 키는 **Android Keystore**로 보호**해야** 하고, 소스·리소스·설정 파일에 하드코딩하거나 평문으로 저장하면 안 된다.

**통신**
- 외부 통신은 **HTTPS** 등 암호화된 프로토콜을 써야 한다.
- **최소 TLS 1.2**를 쓰고 TLS 1.3을 권장한다. SSL, TLS 1.0, TLS 1.1로 연결되지 않게 한다. 검증된 cipher suite를 권장한다.
- 신뢰할 수 있는 서버하고만 통신한다. 서버 원격 명령은 출처, 유효성, 무결성을 확인하고 범위를 제한한다.

**컴포넌트·권한**
- 필요한 권한만 요청한다.
- 외부 호출이 필요 없는 Activity, Service, BroadcastReceiver, ContentProvider는 **외부에 노출하지 않는다**(exported 제한).
- 민감정보를 암시적 브로드캐스트로 보내지 않는다. 공개 컴포넌트는 호출자 인증과 권한을 **둘 다** 확인한다.
- WebView 로컬 파일 접근은 필요 범위로 제한하고, 필요 없으면 비활성화한다. 외부 URI와 경로는 검증하고 정규화한다.

**개발·배포**
- **Pleos가 공식으로 허용한 API만** 쓰고, 차량 API는 기능과 직접 관련된 범위로 한정한다.
- 토큰과 비밀 키를 하드코딩하지 않는다. 서버는 IP 대신 도메인으로 지정할 것을 권장한다.
- 네이티브 라이브러리에는 **RELRO, Stack Canary, NX** 적용을 권장한다. SDK와 라이브러리는 취약점 패치 버전을 유지한다.
- 배포본은 **정식 배포용 서명 인증서**로 서명한다(디버그·테스트 인증서 금지).
- 배포본에서는 **앱 디버깅, 디버그 빌드 설정, WebView 원격 디버깅을 비활성화**한다.
- **난독화(ProGuard/R8 등) 요구사항은 문서에 없다.**

**WebView / 브라우저 앱**
- WebView 앱: 로드할 콘텐츠와 이동할 사이트 범위를 제한하고, JavaScript와 JS 브리지는 필요할 때만 쓴다.
- 브라우저 앱:
  - 보안 업데이트가 지원되는 엔진을 쓴다.
  - 악성 사이트를 차단한다(권장).
  - HTTPS를 우선하고 HTTP일 때는 경고한다.
  - 인증서 검증을 우회하지 않는다.
  - SOP와 CORS를 지킨다.
  - **CSP Level 2 이상**을 지원한다.
  - 샌드박스를 유지한다.
  - 개발자 모드, 프록시 변경, 확장 프로그램을 비활성화한다.
  - **파일 다운로드를 제한**한다.

### 6.5 앱 태그 표기 (REG:app-tagging-guide)
- 공식 타이틀이 외국어(예: 영어)이면, 한국어로 노출되더라도 **원문(영문) 앱명을 태그에 반드시 넣는다.** 공식 앱명과 똑같이 써야 하고 축약이나 변형은 안 된다.
- 한국어 태그는 선택이다. 다국어 공식명은 각각 태그로 넣을 수 있지만 영문 태그는 필수다.
- 예: 공식명 `Sport`, 노출명 `스포츠`면 필수 태그는 `Sport`, 선택 태그는 `스포츠`, `운동`이다.
- 지키지 않으면 반려되거나 수정 요청을 받는다.

---

## 7. 공모전(에뮬레이터 평가) 관점 메모
- 평가는 에뮬레이터에서 이뤄지므로 §5와 §6의 제출 형식(아이콘 규격, IARC, 약관 URL, 고객지원 정보 등)은 **직접 적용되지 않는다.** 다만 §2(DO, CarUxRestrictions), §3(멀티윈도우, FG/BG, 리소스), §4(텍스트, 대비, 응답 시간), §5.1과 §5.3(주의분산, 성능, 기능 패리티)은 그대로 설계 제약으로 쓸 수 있다.
- 지역 분기가 필요하면 `product.sw.target_region`(`KOR`/`EUR`/`USA`/`CAN`)을 근거로 한다(DR-IL). 이 속성은 InfoLINK 문서에만 나온다.

---

## 8. 문서상 모호하거나 확인이 필요한 점

1. **Idling 상태 동작이 비어 있다.** PG:driving-restiction-policy는 "Parking, Idling, Driving"이라고 쓰지만 표에는 Parking과 Driving만 있다. GL의 CarUxRestrictions 설명은 "idling·moving에서는 `requiresDistractionOptimization`과 `no_setup|no_video` 등을 적용"한다고 해서, idling에서도 규제가 걸린다고 읽힌다.
2. **규제 해제 임계값이 충돌한다.** DR-IL은 "≤ 5 km/h + P/브레이크/Auto Hold"를 기준으로 하지만, GL의 Driving state는 parked=기어 PARK, idling=속도 0, moving=속도 非0이다. 이 해제 조건이 InfoLINK 전용인지 Pleos Connect 공통인지 명시되지 않았다.
3. **한국 지역이 주 표에 없다.** Pleos Connect 지역 표는 북미, 유럽, 그외로만 나뉜다. 한국이 "그외"에 속하는지 명시되지 않았다. DR-IL의 QR 표만 한국을 따로 다룬다.
4. **QR 로그인이 상충한다.** 주 표에서는 모든 지역의 Driving이 ⛔이다. DR-IL QR 표에서는 KOR이 대부분 ✅이다. 플래그 이름도 `UX_RESTRICTIONS_NO_SETUP(64)`(주 표)와 `UX_RESTRICTIONS_NO_SETUP_QR_SCAN`(DR-IL)으로 다르고, 후자의 숫자 값은 문서에 없다.
5. **"그외 지역" Driving에서 "미디어 앱 로그인·설정·검색"이 ✅**다. 같은 열에 `NO_SETUP(64)`이 붙어 있어서, 플래그 의미(설정 금지)와 표 값이 어긋나 보인다.
6. **"미디어 앱" 용어가 섞여 있다.** RV의 "미디어 앱"은 동영상, 게임, 브라우저를 뜻하고 "모든 미디어 앱은 정차 중에만" 쓸 수 있다고 한다. PG에서는 Music/Audio가 주행 중 제한적으로 허용된다. 음악 앱이 RV의 HUN 금지 등 미디어 앱 규칙을 적용받는지 불명확하다.
7. **카테고리 예외를 받는 방법이 없다.** Music/Audio, Navigation, Mobility의 주행 중 허용이 DO 선언만으로 되는지, 심사·승인이 필요한지, 지역에 따라 달라지는지 설명이 없다. DO 선언 방식(매니페스트 meta-data 등)은 이 범위 문서에 없다(개발 가이드 참조).
8. **`appCategory` 값이 3개뿐이다**(`game`, `audio`, `video`). 내비, 모빌리티 등은 무엇을 선언해야 하는지, 선언하지 않아도 되는지 알 수 없다. App Market 카테고리 목록과 8개 Connect 카테고리의 매핑도 없다.
9. **`LIMIT_STRING_LENGTH(4)`와 `LIMIT_CONTENT(32)`의 실제 수치가 없다.** 디자인 가이드의 120자/24단어/80자/3줄과 같은 기준인지 알 수 없다. 한국어 텍스트 길이 기준도 없다.
10. **스크린샷 규격이 오기로 보인다.** RV 등록정보는 "840 x 1212 px", SUB 표는 "840 x 121 px"다.
11. **Connect-S 크기가 다르다.** DS:layout은 14.6인치, GL은 14.5인치다. DS:gleo-ai는 "Standard 14.6"이다.
12. **Window 정책이 디스플레이마다 다르다.** PG는 Connect-S의 동작(신규 앱이 기존 orientation을 따름)만 일반론으로 설명한다. Connect-W는 "신규 앱 항상 landscape"다. Connect-S의 "앱 1개 실행 시 2:0 (기본값 1:1)" 표기도 모호하다. 에뮬레이터가 어떤 디스플레이 유형인지는 이 범위 문서로 확인할 수 없다.
13. **디자인 수치가 없다.** 그리드, 창 px, 터치 타깃, 폰트 sp, 컬러 토큰이 텍스트 원문에 없다(이미지나 다운로드 자산으로만 존재). Theme, Widgets, Vehicle notification 페이지도 섹션 제목만 남아 있다.
14. **시간 기준이 정성적이다.** 상태 복원의 "수 분 이내"와 "수 시간 이상"은 수치가 아니다. 0.25초(터치 피드백), 2초(버튼 반응), 10초(실행, 로드)의 측정 기준점도 일부만 정의되어 있다.
15. **CPU와 RAM 측정 기준이 없다.** CPU %가 단일 코어 기준인지 전체 기준인지, 순간값인지 평균인지, 주행과 정차가 어떤 주행 상태에 대응하는지 정의되지 않았다. RAM 400 MB의 지표(PSS/RSS)도 없다.
16. **Vehicle SDK 퍼미션 사전 승인 절차가 공모전 환경에서 어떻게 적용되는지** 알 수 없다. Console의 "SDK 추가 권한 = 프로젝트 ID"와의 관계도 이 범위 문서에 없다.
17. **난독화와 버전 규칙이 없다.** 난독화, `versionCode`, `minSdk`, `targetSdk` 요구사항이 이 범위 문서에 없다(보안 가이드는 디버깅 비활성화, 배포 인증서, 하드코딩 금지까지만 다룬다).
18. **Full Screen Mode / Entertainment mode, Mini app(AppWidget), Gleo 패널 연동**을 서드파티 앱이 쓸 수 있는지 이 범위 문서에 없다.
19. 3:0 window(full screen) 예외는 "적용할 예정"으로만 적혀 있어 현재 상태를 알 수 없다.
20. **"오디오 포커스 정책" 본문**은 이 범위 밖 문서에 있다(`...audio-focus-development-guide__*`). 범위 안 문서는 "획득·상실 대응 필수"만 말한다.
