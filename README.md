# 타비 (TABI)

**초보 운전자를 위한 AI 운전 코치, 타비**

출발 전에는 세워 둔 차의 와이퍼·전조등·브레이크를 직접 움직여 폭우나 앞차 급정거 같은 상황을 미리 연습하고, 내비에 목적지를 넣으면 가는 길의 요금소·합류 구간을 연습해요. 운전 중에는 "비가 와요. 전조등을 켜 주세요."처럼 필요한 것 하나만 말로 알려 주고, 주차 후에는 방금 운전을 같이 돌아봐요.

현대자동차그룹 차량용 앱 개발 공모전(Pleos Connect) 출품작 · 개인 참가

소개 영상(7분 45초): https://youtu.be/Am3B5dWXMRc

## 무엇을 하나

| 차 상태 | 화면 | 하는 일 |
| --- | --- | --- |
| P단(정차) | 홈 → 훈련 → 결과 | 폭우·김서림·앞차 급정거·터널·안개·맞은편 상향등·졸음·타이어 공기압 경고등·고속도로 고장 정차·의도치 않은 가속·우회전 횡단보도·회전교차로·어린이 보호구역·합류·수막현상·하이패스 오진입·일반 차로 요금소·내리기 전의 18개 상황(5챕터)을 3D 주행 장면으로 보여 주고, 와이퍼 레버·성에 제거·브레이크·핸들을 **직접 움직여** 대처하게 해요. 장소(고속도로·도심·보호구역·내리막·회전교차로·요금소)와 시간대(낮·해 질 녘·밤)가 바뀌어요. 반응 시간과 별로 채점하고, 상황에 맞춰 실내 앰비언트 조명·효과음·음성이 바뀌어요 |
| P단(정차) | 홈 추천 | 내비에 경로가 있으면 회전 안내·주의 지점·정체를 읽어 "가는 길에 요금소가 있어요. 미리 연습해 볼까요?"처럼 그 길에 맞는 훈련을 권해요 |
| P단(정차) | 미니게임 | 핸들로 여는 금고 다이얼, 깜빡이 모스, 창문 버저 퀴즈 |
| P를 벗어남 | 코치 | 훈련은 바로 닫혀요. 여러 신호를 함께 봐야 알 수 있는 것만 한 문장으로 말해요(와이퍼는 켰는데 전조등이 꺼져 있음, 안전벨트를 안 맴, 문이 열림, 깜빡이 없이 차로 변경 등). 30분에 최대 3번(안전벨트·문은 바로). 화면은 한 줄과 "질문하기" 버튼뿐이에요 |
| 다시 P | 리뷰 | 주행 시간·비/야간 주행·계기판 주행 거리·타비가 한 말을 보여 주고, 음성으로 총평해요(LLM이 없으면 규칙 기반 3문장). 놓친 것은 "1분 연습"으로 이어져요 |

쓰는 Pleos SDK: Vehicle 2.3.2(와이퍼·전조등·공조·브레이크·조향·창문·깜빡이·안전벨트·문·사이드미러 열선·주행 거리·배터리 잔량 읽기, 정차 중 앰비언트 제어), NaviHelper 3.0.5(경로의 회전 안내·주의 지점·정체, 휴게소 검색·도착), TextToSpeech 3.3.1, SpeechToText 3.3.1(ON_DEVICE), FusedLocation 1.1.3(어린이 보호구역·제한속도 안내, 일몰·일출). 정차 판정은 **표준 주행 규제 API만 사용**해요(AAOS `CarUxRestrictionsManager`, Pleos 주행 규제 가이드가 안내하는 방식). 기어·차속 같은 비공개 경로는 쓰지 않아요.

## APK

- **`apk/홍기헌_TABI_release.apk`** (release 빌드, R8 적용, 약 28 MB. 파일명은 공모전 규칙 `팀명_앱이름_release.apk`를 따랐고, 개인 참가라 팀명 자리에 성명이 들어가요)
- 이 APK에는 LLM API 키가 들어 있지 않아요. 키가 없으면 AI 교관은 미리 쓴 문장과 규칙 기반 총평으로 동작해요(모든 기능이 키 없이 돌아요). 키를 앱에 넣지 않고 서버가 보관하는 중계 서버 코드는 `server/`에 있고, 아직 배포하지 않았어요.

## 테스트 환경

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | **Pleos Connect Emulator v2.1.0** (Android API 34, Automotive), AVD `Pleos_Connect_v2.1` |
| AVD 설정 | Default boot = **Cold**(quick boot면 VHAL 사용 불가), RAM 4 GB |
| 화면 규격 | 디스플레이 **2560×1440, 160 dpi**. 앱 창(가로, 기본) **약 1682×1210 px**. 세로 창 835×1210 px에서도 확인 |
| 차량 신호 | Pleos Connect Virtual Console(아래) |
| 개발 도구 | Android Studio, JDK 17, Gradle(래퍼 포함), Kotlin 2.0.21, Compose |

## 실행 방법

### APK로 바로

1. Pleos Connect Emulator 2.1.0을 cold boot로, 호스트 마이크 소리를 받도록 켜요(옵션이 없으면 에뮬레이터가 마이크 입력을 0으로 만들어요):
   ```bash
   emulator -avd Pleos_Connect_v2.1 -no-snapshot-load -allow-host-audio
   ```
   그다음 `adb emu avd hostmicon`으로 호스트 마이크를 켜요(Extended Controls → Microphone의 "Virtual microphone uses host audio input"과 같아요).
2. `adb install -g apk/홍기헌_TABI_release.apk` (`-g`는 마이크·위치 권한을 미리 줘요. 없으면 첫 실행 때 물어봐요)
3. 앱 목록에서 **타비**를 실행해요.

- 위치를 쓰려면 앱 첫 실행 때 위치 권한을 허용해요(일몰 시각 계산). 거부해도 나머지는 동작해요.
- 차량 마이크 음성 인식은 에뮬레이터에서 맥의 마이크 권한이 필요해요(시스템 설정 → 개인정보 보호 및 보안 → 마이크에서 에뮬레이터 허용, Extended Controls → Microphone → "Virtual microphone uses host audio input"). 마이크 없이도 정차 중 선택지는 화면 버튼으로 답할 수 있어요.

### 소스로 빌드

```bash
./gradlew :core:test            # 훈련 판정·점수·코치 규칙·미니게임 테스트 (Android SDK 없이도 됨)
./gradlew :app:installDebug     # 디버그 빌드 설치
./gradlew :app:assembleRelease  # release APK (서명 정보는 local.properties)
```

`local.properties`(커밋하지 않음):

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
# 선택: AI 교관(Claude API). 비워 두면 키 없이 동작
LLM_PROVIDER=anthropic
LLM_API_KEY=
LLM_MODEL_FAST=      # 비우면 claude-haiku-5-5 (주행 중 질문)
LLM_MODEL_REVIEW=    # 비우면 claude-opus-5-5 (리뷰 총평)
# 선택: release 서명
release.storeFile=
release.storePassword=
release.keyAlias=
release.keyPassword=
```

- 디버그 빌드는 `LLM_API_KEY`를 넣으면 바로 LLM을 써요. release 빌드는 키를 넣지 않는 게 기본이고, 로컬 시연용으로만 `./gradlew :app:assembleRelease -PembedLlmKey=true`로 넣을 수 있어요.
- 에뮬레이터가 인터넷에 연결돼 있지 않으면(차량 설정 → 연결 → Wi-Fi에서 `AndroidWifi` 연결) LLM 대신 미리 쓴 문장으로 답해요.

### Virtual Console로 시험하기

```bash
adb forward tcp:8088 tcp:8088   # 그다음 브라우저에서 http://127.0.0.1:8088/
```

| 해 볼 것 | Virtual Console 조작 |
| --- | --- |
| 훈련 시작 | Gear **P**에서 홈의 "오늘의 추천" 또는 챕터 카드를 눌러요 |
| 폭우(C1) | Front Wiper **High** + Exterior Lights **Low beam** |
| 앞유리 김서림(C1) | Climate **Defroster Front On** + **A/C** + Air **Fresh** |
| 앞차 급정거(C1) | **BRAKE** 누르기(반응 시간 측정) |
| 터널·안개(C2) | Exterior Lights Low beam(안개는 브레이크로 감속까지) |
| 맞은편 차(C2) | High Beam Off |
| 졸음(C2) | Air Fresh + Window **Front left** 열기 |
| 의도치 않은 가속(C3) | BRAKE 3초 이상 누르고 있기 |
| 우회전 횡단보도·어린이 보호구역(C5) | BRAKE로 완전히 멈추기 |
| 고속도로 합류(C5) | Turn signal **Left** 3초 뒤 Steering 왼쪽 10° |
| 회전교차로(C5) | Turn signal Left를 켜고 BRAKE를 밟아 양보선 앞에 멈춤 → 도는 차가 지나간 뒤(시작 6초쯤 뒤) BRAKE를 떼면 진입 |
| 수막현상(C5) | 물이 고인 곳에 닿기 전에 BRAKE로 미리 감속 → 물 위에서는 BRAKE를 떼고 Steering을 가운데로 |
| 일반 차로 요금소(C5) | BRAKE로 부스 옆에 멈춤 → Window Front left 열기 → 화면에서 "네, 받았어요" → 창문 닫기 |
| 경고등·고장 정차·하이패스(C3·C5) | 화면의 보기를 누르거나 말로 답해요 |
| 내리기 전(C4) | Low beam 끄기 + 창문 닫기, 주차 브레이크는 화면 선택지로 답 |
| 금고 다이얼 | Steering 오른쪽 90° → 왼쪽 45° → 오른쪽 120°에서 잠깐 멈추기 |
| 코치 모드 | Gear **D**. 와이퍼 Low + 전조등 Off 20초 → "비가 와요. 전조등을 켜 주세요." 깜빡이 없이 Steering을 크게 돌리면 "방금은 깜빡이 없이 차로를 바꿨어요." 에뮬레이터는 안전벨트를 늘 안 맨 상태로 줘서 D로 가면 "안전벨트부터 매 주세요."가 먼저 나와요 |
| 리뷰 | 다시 Gear **P** |

앰비언트 조명은 Virtual Console 화면의 빛 색으로도 보여요. 화면별 캡처는 `docs/screens/sim/`에 있어요.

## 구조

| 모듈·패키지 | 내용 |
| --- | --- |
| `core/` (`io.moodlab.sideseat.core`) | 순수 Kotlin. 훈련 엔진(상황 판정·별·챕터·돌발·교관 대결·이 차 면허), 주행 코치 규칙(K01~K18, 말수 제한), 리뷰 요약, 미니게임. 테스트 포함 |
| `app/.../vehicle` | Pleos Vehicle SDK·AAOS Car API → `VehicleState`, 정차 판정, 앰비언트·경유지 |
| `app/.../voice` | Pleos TTS·STT, 효과음, AI 교관(빠른 길: 지식 파일 즉답 / 느린 길: Claude API 스트리밍) |
| `app/.../ui` | Compose 화면과 3D 훈련 장면(`assets/scene3d/`, three.js 0.170.0을 WebView로. WebGL2가 없으면 영상 배경 방식), 가로·세로 대응 |
| `server/` | 중계 서버(Supabase Edge Function). API 키를 서버 환경 변수로만 보관 |
| `app/.../app` | 모드 상태 기계, 이벤트 연결, 코치 포그라운드 서비스, 진행 기록 저장 |

## 문서

- 기획서 초안: `docs/plan/application-draft.md` · 제품 명세: `docs/plan/product-spec.md` · 대처 절차 공식 출처: `docs/plan/safety-sources.md`
- 에뮬레이터 실측·검증 기록: `docs/STATUS.md` · 결정 기록: `docs/plan/decisions.md`

Playground 프로젝트 Key, LLM API 키, release 서명 키와 비밀번호는 `local.properties`와 로컬 파일에만 두고 커밋하지 않아요.
