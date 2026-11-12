# 진행 상황

> **최종 빌드 (2026-11-13 06:22, dev-integration)**: 소스 기준 커밋 `5f76c80`(vehicle 6b80523·ui 4144bb7·voice 5f76c80 반영, 세 세션 "완료" 확인). `./gradlew :core:test` 77개·`:app:testDebugUnitTest` 57개 통과. `apk/MoodLab_SideSeat_release.apk` 22,839,355바이트(22.8 MB), 06:22 빌드. release 스모크(에뮬레이터, 크래시 0): C1 폭우 ★3·김서림 ★3·급정거 ★1 → D단 코치 K01 발화 → P단 규칙 기반 리뷰(첫 문장 1.5초) → 홈 → 금고 다이얼 8.1초에 열림. 캡처 `v3_*.jpg`(급정거 근접 `v3_s03_sudden_stop_close.jpg`). 같은 소스의 디버그 빌드로 Claude 질문 3회: 채움말 0.61~0.64초, 실제 답 시작 1.31 / 1.34 / 1.72초.

## 옆자리(SideSeat) 통합 검증 — 완료 조건 장면 (2026-11-13, dev-integration)

빌드: 디버그 APK(공유 폴더를 복사한 격리 빌드), Pleos Connect Emulator 2.1.0. 조작은 모두 Virtual Console HTTP API(아래 실측 절)로 했고, 시나리오 실행기가 앱 로그(`SideSeatMeasure`)를 보며 다음 조작과 캡처를 했다. 캡처는 `docs/screens/sim/`(파일 이름이 장면 이름).

### 통합 뒤 실측 (공유 폴더 빌드 a25a667 기준, 2026-11-13 06시)

**① 주행 화면 프레임** (`frame scene` 로그, 2초 구간, 교관 대결로 장면을 붙잡아 측정. 저사양 모드는 켜지지 않음)

| 장면 | 가로 1682×1210 | 세로 835×1210 |
| --- | --- | --- |
| 평온 주행(앞차·옆 차로 차) | 60.0 / 60.0 fps, 33 ms 넘는 프레임 0 | 60.0 / 60.0 fps, 0 |
| 폭우, 와이퍼 꺼짐 | 60.0 / 60.0 / 59.0 fps(최대 50 ms 1회) | 60.0 / 60.0 / 58.0 fps(최대 83 ms 1회) |
| 짙은 안개 + 상향등 | 60.0 / 58.5 / 60.0 fps(최대 50 ms 1회) | 59.5 / 60.0 / 60.0 fps |

그리기 기록 시간은 평균 0.6~1.0 ms, 최대 9 ms.

**② 음성 지연 "말 끝 → 첫 음성"** (입력: 맥 합성 음성 PCM을 `listenFromPcm`으로 실시간 속도로 넣음, 디버그 명령 `ask_pcm`. 값은 STT 최종 결과 → TTS 시작. STT 최종 결과는 음성이 끝난 뒤 약 0.4초에 오므로 실제 말 끝 기준은 +0.4초)

| 길 | 시행 | STT 최종 → 첫 음성 | 중앙값(범위) | 말 끝 기준 추정 |
| --- | --- | --- | --- | --- |
| 빠른 길(지식 파일 즉답: "안개등 어떻게 켜?", "앞유리에 김이 서려") | 6 | 7, 14, 25, 16, 10, 7 ms | **10 ms (7~25)** | 약 0.4초 |
| Claude 스트리밍(claude-haiku-5-5, "왜 외기로 바꿔야 해") | 3(+이전 1) | 869, 1290, 1981 ms (이전 939) | **1.1초 (0.87~1.98)** | 약 1.5초 |

- Claude 길의 첫 음성은 0.6초에 넣는 필러("음, 잠깐만요.")이고, 실제 LLM 첫 글자는 705 / 1114 / 1767 ms에 왔다(프롬프트 캐시 적중 `cacheRead=8688`). 필러의 TTS 시작이 266~1377 ms로 흔들려 voice 세션에 알림.
- 스트리밍 확인: `llm start … cacheRead=8688` → `llm first text` → `tts chunk first chars=…` → `tts chunk final totalChars=104~163`. 문장 단위로 TTS에 이어 보냄(`chunkedTts=true`).
- 리뷰 총평(claude-opus-5-5) 첫 문장 0.7초 / 2.8초. 키가 없는 release에서는 `llmEnabled=false`로 규칙 기반 3문장이 나옴(release 스모크에서 확인).
- 짧은 "응" PCM은 listen이 빈 문자열을 돌려줌(3/3). voice 세션에 알림.

**②-2 음성 지연 재측정 (voice c8040d8 반영: 문장별 순차 재생, 최종 결과 대기, 지연 로그 분리)** — 같은 PCM 4개 × 3회

| 길 | 첫 음성(`latency total`) | 실제 답 시작(`latency answer`) |
| --- | --- | --- |
| 짧은 대답 "응"(fast-reply) | 50, 4, 4 ms — 이제 "응"으로 인식(3/3) | 같음 |
| 지식 즉답("앞유리에 김이 서려", "안개등 어떻게 켜?") | 10, 36, 23, 6, 23, 17 ms(중앙값 20 ms) | 같음 |
| Claude 스트리밍(claude-haiku-5-5) | **610, 620, 610 ms**(채움말 "음, 잠깐만요.") | **2083, 2105, 1999 ms**(LLM 첫 글자는 660 / 733 / 1554 ms) |

- PCM 경로에서는 `onUpdatedRms`·EPD가 오지 않아 `speechEnd->final`이 비어 있다. 말 끝 기준은 여전히 +약 0.4초로 추정.
- 순차 재생으로 바뀐 뒤 실제 답 시작이 이전(0.7~1.8초)보다 늦어졌다(약 2.0초). 채움말이 끝나기를 기다리는 것으로 보여 voice 세션에 알림.
- 앰비언트 원래 색 복원(vehicle 6b80523): VC를 `#2979ff`·60 %로 둔 뒤 실행 → `ambient original color=ff2979ff`, `brightness=60` → ALERT·SUCCESS 뒤 OFF에서 `#2979ff`·60 %로 돌아옴.

**③ 정차 중 CPU·메모리** (훈련 화면 폭우, `top -d 2` 3회, `dumpsys meminfo`)

| 항목 | 가로 | 세로 | 기준 |
| --- | --- | --- | --- |
| 앱 CPU | 50 / 54 / 59 % (코어 1개 기준) → 4코어 전체의 약 13~15 % | 38 / 42 / 45 % → 약 10~11 % | 정차 25 % 이하 |
| 메모리 PSS | 113.7 MB (RSS 217 MB) | 115.3 MB (RSS 220 MB) | 400 MB 이하 |

**④ 신호 → 판정(화면 반영) 지연** (VC 요청 시각 → `app step done` 로그, 3회씩. 화면에는 다음 프레임(최대 16.7 ms)에 보인다)

| 조작 | 지연 |
| --- | --- |
| 와이퍼 Off → High (폭우) | 26, 10, 22 ms |
| 앞유리 성에 제거 On (김서림) | 20, -6, 8 ms (±10 ms는 시계 보정 오차) |
| 브레이크 80 % (급정거) | 30, 13, 55 ms |
| 와이퍼 단계만 바뀜(켜진 채 Low↔High, 200 ms 조회) | 59, 116 ms |

**vehicle 게이트웨이 확인(dev-vehicle-core 요청 8항목)**: P→D `parked=false` 18 ms(UX_RESTRICTIONS), 조향 VC +45 → 45.0(부호 반전 맞음), 착좌 VC Occupied → `occupied[PASSENGER]=true`, 창문 VC 50 % → `window[DRIVER]=5→50%`, setAmbient ALERT `#ff1e1e`·SUCCESS `#1ee86a`·CALM `#fff1dc` 각 2~5 ms(VC 색 일치), 휴게소 → 내비 "휴게소" 검색 화면(`coach_k08_rest_stop_navi_search.jpg`), FusedLocation `sun rise=404 set=1021`. 위치 권한을 실행 뒤에 허용하면 게이트웨이를 다시 시작하도록 함.

**차량 마이크 STT(`request()`)**: 마이크가 열리고(`createAudioRecord`) 녹음 루프가 69번 읽어 인식기에 넣지만 말소리가 없어 7초 뒤 `stt timeout speechSeen=false`로 끝남. `adb emu avd hostmicon`으로 호스트 마이크를 켰으므로 맥의 마이크 권한(에뮬레이터)이 원인으로 보임 → `docs/plan/needs-user.md`에 절차를 적음. 시연 대체 입력: 화면 선택 버튼(정차 중), 디버그 녹음 입력(화면에 "[녹음 입력]" 표시).

**관찰**: 같은 세션에서 `am task resize`로 창을 여러 번 바꾼 뒤 D단에서 차량 시스템이 "주행 중에는 사용할 수 없습니다" 화면을 앱 위에 두 번 띄웠다. 앱을 새로 시작한 뒤에는 가로·세로·훈련 중단 직후 D 모두 3/3 정상(코치 화면)이었다. 실제 창 크기 변경이 아닌 시험용 강제 리사이즈의 부작용으로 보지만, 최종 확인에서 계속 본다.

**테스트**: `./gradlew :core:test` 77개 통과, `./gradlew :app:testDebugUnitTest` 57개 통과.

다시 찍은 캡처(ui 검수 반영 뒤): `v2_calm_cruise_traffic_wiper_off.jpg`, `v2_s03_sudden_stop_just_after.jpg`, `v2_s03_after_brake.jpg`, `v2_s01_heavy_rain_wiper_off.jpg`, `v2_s01_heavy_rain_wiper_high.jpg`, `v2_home_landscape.jpg`, `v2_portrait_home.jpg`, `v2_portrait_s01_heavy_rain.jpg`, `v2_coach_k01_landscape.jpg`, `v2_portrait_coach_k01.jpg`, `v2_coach_landscape_after_gear_d.jpg`, `v2_portrait_coach_after_gear_d.jpg`, 앱과 Virtual Console을 나란히 둔 `11-virtual-console.png`.

### 폼 팩터·차량 마이크 시도 (2026-11-13 06:30~06:55)

**폼 팩터: 확인됨.** 문서 절차의 Host Display Bridge는 `host-display-bridge.zip`(문서 사이트에서 내려받기)과 Go 1.22가 필요한데 둘 다 이 맥에 없고, 내려받기는 사용자 승인 없이 하지 않았다. 대신 브리지가 하는 일(에뮬레이터에 DISPLAY2 만들기)을 에뮬레이터 콘솔 명령으로 직접 했다. 크기는 기기 안 FormFactorConsole이 보여 주는 프리셋 값을 그대로 썼다.

| 프리셋(FormFactorConsole) | 만든 디스플레이 | 명령 |
| --- | --- | --- |
| Connect-S 12.9″ · 160 DPI · Portrait → 628×855 px | 628×855, 160 dpi | `adb emu multidisplay add 2 628 855 160 0` |
| Connect-S 14.6″ · 200 DPI · Landscape → 1700×1185 px(1360×948 dp) | 1700×1185, 200 dpi | `adb emu multidisplay add 2 1700 1185 200 0` |

앱은 `adb shell am start --display <id>`로 띄우고(14.6″은 디스플레이 id 3), 화면은 `adb emu screenrecord screenshot <폴더> --display 2`로 캡처했다. 홈·훈련(폭우 발생·성공)·코치·코치 K01·리뷰를 두 크기 모두 확인: `ff_12.9_portrait_*.jpg`, `ff_14.6_landscape_*.jpg`. 잘림 1건: 두 크기 모두 리뷰 화면 TalkButton 글자("말하는 중 · 눌러서 멈추기")가 잘림 → dev-sim-ui에 넘김. 나머지 화면은 잘림·겹침 없음.

**차량 마이크 STT `request()`: 불가(사람 목소리 필요), 원인은 확인.**

| 시도 | 결과 |
| --- | --- |
| ① `adb emu avd hostmicon` | 처음 켠 에뮬레이터(옵션 없음)에서는 녹음 루프가 돌지만 무음 |
| ① 실행 옵션 `-allow-host-audio` | 도움말: "Allows sending of audio from audio input devices. Otherwise, zeroes out audio." 이 옵션으로 다시 켜자 STT 입력 RMS가 호스트 마이크 켬 25~44 / 끔 0.1~0.7 → **맥의 실제 마이크 소리가 들어옴** |
| ② 맥 마이크 권한(TCC) 읽기 | `TCC.db`는 읽기 권한이 없어 직접 확인 불가. 대신 위 RMS 차이로 에뮬레이터가 이미 맥 마이크를 받고 있음을 확인(권한 창이 필요 없음) |
| ③ 가상 마이크에 소리 넣기 | 에뮬레이터 콘솔에는 오디오 주입 명령이 없음. gRPC `EmulatorController.injectAudio`(127.0.0.1:8554, discovery 파일 토큰)를 표준 라이브러리 HTTP/2 클라이언트로 호출 → **에뮬레이터가 2/2 즉시 종료**(SIGSEGV `audio_forwarder_enable` ← `QemuAudioInputEngine::start` ← `injectAudio`, 주소 0x58, 맥 크래시 리포트). 에뮬레이터 36.6.11의 버그로 판단. 다시 켤 때 크래시 보고는 보내지 않음(`-crash-report-mode never`) |
| ④ 호스트 마이크 켬 + 맥 스피커로 `say` | 맥 출력이 음소거(`output muted:true`, 볼륨 19)라 소리가 나지 않아 인식 0. 시스템 설정을 바꾸지 않으려고 음소거를 풀지 않음 |

결론: 남은 것은 사람이 맥 마이크에 직접 말하는 것뿐(절차는 `docs/plan/needs-user.md`). 앱 경로(마이크 열기 → 녹음 → 인식기 → 시간 제한 → 결과)는 로그로 확인했고, 인식 자체는 같은 STT 엔진에 PCM을 넣는 경로로 확인함. 에뮬레이터는 지금 `-allow-host-audio -crash-report-mode never`로 켜져 있다.

### 완료 조건 8개 장면

| # | 완료 조건 | 결과 | 증거(캡처) |
| --- | --- | --- | --- |
| 1 | P단에서 훈련 시작 → 가짜 3D 주행 화면이 움직인다 | **확인**. 주행 화면 60 fps(아래 표) | `c1_s01_heavy_rain_active_landscape.jpg`, `c2_s04_tunnel_dark.jpg` |
| 2 | 훈련 상황 5개 이상이 실제 신호로 판정 | **확인: 9개**. S01 와이퍼 High+하향등, S02 성에 제거+에어컨+외기, S03 브레이크 반응, S04 하향등, S05 하향등+브레이크 감속, S06 상향등 끄기, S09 외기+운전석 창문, S10 브레이크 3초 유지, S11 하향등 끄기+창문 닫기. (S07·S08·S11 주차 브레이크는 선택지 답) | `c1_*`, `c2_*`, `c3_s10_*`, `c4_*` |
| 3 | 상황 중 앰비언트 set, 효과음·TTS 대사 | **확인**. 상황마다 `ambient CALM→ALERT→SUCCESS→CALM` set 성공(6~55 ms), VC 화면이 빨갛게 바뀜. TTS 대사 `tts start/done`, 효과음 rain·tire 등 재생 로그. 소리는 사람이 들어 확인 필요 | `virtual_console_ambient_alert_red.jpg`, `c1_s01_app_same_moment_ambient_alert.jpg` |
| 4 | D로 바꾸면 훈련 화면이 닫히고 코치 모드 | **확인**. `app mode TRAINING -> COACH`, 훈련 중단·저장, 효과음 정지 | `c2_s04_tunnel_active_before_gear_d.jpg` → `coach_after_leaving_p_training_closed.jpg` |
| 5 | 코치 규칙 3개 이상이 신호 조합으로 발화 | **확인: K01·K06·K05** (+K09 접두어). 실제 임계값 그대로(20초·60초·3분) | `coach_k01_wiper_on_headlamp_off.jpg`, `coach_k06_turn_signal_left_60s.jpg`, `coach_k05_high_beam_3min.jpg` |
| 6 | P로 돌아오면 요약 화면과 음성 총평 | **확인**. `app mode COACH -> REVIEW`, LLM 총평 첫 문장 0.7초(두 번째 시행 2.8초) | `review_after_park_voice_summary.jpg`, `review_after_park_done.jpg` |
| 7 | 미니게임(핸들 금고 다이얼)이 조향각으로 동작 | **확인**. 90° → -45° → 120°, 8.9초에 열림 | `m1_safe_dial_start.jpg`, `m1_safe_dial_turning_right_90.jpg`, `m1_safe_dial_opened.jpg` |
| 8 | 가로·세로 창에서 레이아웃이 깨지지 않음 | **확인**. 가로 1682×1210(기본 앱 창), 세로 835×1210(`am task resize`로 창을 좁힘). 홈·훈련·코치·리뷰 모두 세로로 쌓임. 폼 팩터 콘솔(12.9·14.6인치)은 Go 브리지가 필요해 미확인 | `home_landscape_first_run.jpg`, `portrait_home.jpg`, `portrait_training_s01_heavy_rain.jpg`, `portrait_coach_k01.jpg`, `portrait_review.jpg` |

챕터 결과(한 번 시행): C1 ★6/9(폭우 ★3 4.0초, 김서림 ★2 6.3초, 급정거 ★1 — 캡처 시간 때문에 반응 1.0초), C2 ★12/12, C3 ★9/9, C4 ★3/3.

### 실측 표

| 항목 | 값 | 방법 |
| --- | --- | --- |
| 차량 신호 지연(VC → 앱 리스너) | 브레이크·방향지시등·등화·공조·창문·와이퍼 ON/OFF·기어·정차 판정 10 ms 미만, 조향각 90~160 ms, 와이퍼 단계(200 ms 조회) 30~160 ms | 아래 "에뮬레이터 실측" 절 |
| 주행 화면 프레임 | **60 fps**(평균 16.7 ms, 33 ms 넘는 프레임 0/121, 그리기 기록 평균 0.6~0.9 ms) | C1 30초 `frame scene` 로그 14구간 |
| 음성 지연(말 끝 → 첫 음성) | 빠른 길(지식 파일 즉답): STT 최종 결과 → TTS 시작 **6~8 ms**. LLM 길(claude-haiku-5-5): **939 ms**(첫 문장 603 ms + TTS 시작 330 ms). STT는 음성이 끝난 뒤 약 0.4초에 최종 결과를 주므로 실제 말 끝 기준은 빠른 길 약 0.4초, LLM 길 약 1.3초 | PCM 질문 4개를 `listenFromPcm`으로 넣음(디버그 명령 `ask_pcm`) |
| 리뷰 총평 첫 문장 | 0.7초 / 2.8초(claude-opus-5-5, 2회) | `app review first sentence` 로그 |
| 앰비언트 set | P단 6~55 ms 성공. 주행 중에는 앱이 부르지 않음(명세 6.1) | `app ambient … ok=true took=` |
| P단·D단 제어 | 앰비언트·성에 제거·에어컨·내기순환·창문 set 모두 P·D 성공(에뮬레이터) | 아래 실측 절 |
| 에뮬레이터 인터넷 | 처음에는 연결 없음(`Active default network: none`) → 차량 설정 Wi-Fi에서 `AndroidWifi`에 연결해 해결 | LLM 예열 HTTP 200 확인 |

### 로그 발췌

```
app mode HOME -> TRAINING
app say p=2 '비가 쏟아져요. 앞이 안 보여요!'
app ambient ALERT ok=true took=37ms
app training C1 HEAVY_RAIN SUCCESS steps=wiper_fast:1, low_beam_on:1 result=HEAVY_RAIN★3 3991ms
app training C1 FOGGED_WINDSHIELD SUCCESS steps=defrost_on:1, ac_on:1, fresh_air:1 result=FOGGED_WINDSHIELD★2 6321ms
app training C2 DROWSY SUCCESS steps=fresh_air:1, window_open:1 result=DROWSY★3 2889ms
app training C3 UNINTENDED_ACCEL SUCCESS steps=brake_hold:1 result=UNINTENDED_ACCEL★3 1949ms
app mode TRAINING -> COACH
app coach say rule=K01 urgent=true '연습했던 거예요. 비가 와요. 전조등을 켜 주세요.'
app coach say rule=K06 urgent=false '깜빡이가 계속 켜져 있어요.'
app coach say rule=K05 urgent=false '상향등이 계속 켜져 있어요.'
app mode COACH -> REVIEW
app review first sentence after=706ms
app safedial solved=3/3 targets=[90, -45, 120] deg=120.0 done=true t=8887ms
frame scene fps=60.0 avgMs=16.7 maxMs=16.7 over33ms=0/121 drawRecordAvgMs=0.91
latency total=939ms path=llm final->answer=6ms answer->firstSentence=603ms firstSentence->ttsStart=330ms
latency total=8ms path=fast-knowledge final->answer=5ms answer->firstSentence=0ms firstSentence->ttsStart=3ms
```

### release APK

- `apk/MoodLab_SideSeat_release.apk` 22.8 MB (디버그 60 MB → R8·리소스 축소). 서명은 기존 로컬 키(인증서 CN=MooDrive). LLM 키는 넣지 않음(`llmEnabled=false` 확인).
- R8 주의(실측): Pleos SDK가 Gson `TypeToken`으로 capability 응답을 풀어서, 제네릭 시그니처를 지우면 모든 `check…Capabilities`가 `TypeToken must be created with a type argument`로 실패했다 → `app/proguard-rules.pro`에 `-keepattributes Signature`와 Gson·`ai.pleos.**` keep 규칙을 넣어 해결.
- release 스모크(에뮬레이터): 설치 → C1 폭우·김서림 판정 → D단 코치 K01 → P단 규칙 기반 리뷰까지 크래시 없이 확인.
- 기존 MooDrive 코드·음원 66곡(22 MB)·이전 APK·전용 도구(`tools/demo-music`, `tools/app-compile-check`)는 삭제했다. `design/`(MooDrive 프로토타입)은 문서 성격이라 남겨 둠.

### 디버그 빌드 도구 (실측·시연 녹화용)

- `adb shell am broadcast -a io.moodlab.sideseat.DEBUG --es cmd <cmd> --es arg <arg>`: `chapter C1`, `answer <선택지>`, `action OpenSafeDial|ShowResult|GoHome|ToggleDebugPanel…`, `ask_pcm <files 안 PCM>`, `frames on|off`, `vehicle debug|pleos`
- 실측 도구(신호 전체 로그·set 시험): 앱을 `--ez probe true`로 실행

## 옆자리(SideSeat) 에뮬레이터 실측 (2026-11-13 새벽, dev-integration)

환경: Pleos Connect Emulator 2.1.0 (`Pleos_Connect_v2.1`, cold boot, API 34), Vehicle SDK 2.3.2, TTS·STT 3.3.1, FusedLocation 1.1.3.
방법: 디버그 빌드의 실측 도구(`app/.../app/measure/SignalProbe.kt`)가 모든 신호를 구독해 로그 태그 `SideSeatMeasure`로 남긴다. Virtual Console의 HTTP API(아래 "Virtual Console 자동 조작")로 값을 바꾸고, 요청 시각(호스트)과 로그 시각(`logcat -v epoch`, 기기-호스트 시계 차 0.14초 보정)의 차이를 지연으로 쟀다. ±10 ms는 시계 보정 오차.

### 신호 읽기

| 신호 | SDK 경로 | 결과 | 지연 | 비고 |
| --- | --- | --- | --- | --- |
| 브레이크 페달 | `Brake.registerBrakePedalStatus` | 읽힘 (true/false) | 10 ms 미만 | 세기는 없음. VC 브레이크 1% 이상이면 true |
| 조향각 | `SteeringWheel.registerSteeringWheelAngle` | 읽힘 | 90~160 ms | **부호가 VC와 반대**(VC +45 → SDK -45.0). 범위 -470~500. 값이 같아도 약 3초마다 재전송 |
| 방향지시등 | `TurnSignal.registerTurnSignalState` | 읽힘 (OFF/LEFT/RIGHT) | 10 ms 미만 | `TurnSignalValue`에 비상등 값이 없음 |
| 비상등 | – | **읽을 수 없음** | – | VC Hazard를 켜도 TurnSignal 변화 없음. Telltale은 권한 불가(아래). AAOS `HAZARD_LIGHTS_STATE`는 `CAR_EXTERIOR_LIGHTS`가 부여되지 않아 구독 실패 |
| 하향등 | `Light.registerLowBeamLightState` | 읽힘 | 10 ms 미만 | VC Exterior Lights Mode = Low beam |
| 상향등 | `Light.registerHighBeamLightState` | 읽힘 | 10 ms 미만 | 부팅 직후 기본값이 ON이었음 |
| 안개등(앞·뒤) | `Light.*FogLight*` | **미지원** | – | capability가 `ERROR_NOT_SUPPORTED_PROPERTY`. VC에도 안개등 카드 없음 |
| 앞유리 성에 제거 | `HVAC.registerHvacDefrosterState(FRONT_WINDSHIELD)` | 읽힘 | 10 ms 미만 | 영역 [FRONT_WINDSHIELD], [REAR_WINDSHIELD]. Max Defrost capability도 READ_WRITE |
| 에어컨 | `HVAC.registerHvacAcState` | 읽힘 | 10 ms 미만 | 영역은 ROW_1~2 전체 한 그룹 |
| 내기순환 | `HVAC.registerHvacRecirculationState` | 읽힘 | 10 ms 미만 | 리스너 메서드 이름이 `onHvacAutoRecirculationStateUpdated` |
| 창문 위치 | `Window.registerWindowPosition` | 읽힘 | 첫 변화 10 ms 미만 | **SDK 값 0~10 = VC 0~100%**(10배 차이). 창문은 서서히 움직여 값이 여러 번 온다 |
| 와이퍼 켜짐/꺼짐 | `Wiper.registerWiperState` | 읽힘 | 10 ms 미만 | 켜질 때 스위치를 한 번 읽으면 단계도 앎 |
| 와이퍼 단계 변화 | `Wiper.getCurrentWiperSwitch` 조회 | 조회로만 | 200 ms 주기에서 30~160 ms | 켜진 채 단계만 바뀌면 알림 없음(11-07과 같음). 조회 1회 1~3 ms. VC Low=`CONTINUOUS_LEVEL_1`, High=`CONTINUOUS_LEVEL_3`, Auto=`AUTO` |
| 착좌 | `Seat.registerSeatOccupancy` | 읽힘, **반대로 옴** | 10 ms 미만 | VC Occupied → SDK false, Empty → true (4개 좌석 모두). 에뮬레이터 특성으로 보이며 실차 의미는 문서대로(true=착좌) |
| 경고등(Telltale) | `Safety.registerTelltales`·`getTelltalesInfo`·`checkTelltalesCapabilities` | **권한 불가** | – | `SecurityException: registerTelltales need Permission`. API Reference 상세에 `PRIVATE_CAR_SAFETY`(signature\|privileged)로 표기. 일반 앱은 못 씀 |
| 기어 | AAOS `GEAR_SELECTION` (`CAR_POWERTRAIN`) | 읽힘 | 10 ms 미만 | P=4, R=2, N=1, D=8 |
| 주차 브레이크 | AAOS `PARKING_BRAKE_ON` | 값은 오지만 **바뀌지 않음** | – | VC에는 주차 브레이크 Telltale만 있고, 켜도 `PARKING_BRAKE_ON`은 false 유지 |
| 차속 | AAOS `PERF_VEHICLE_SPEED` (`CAR_SPEED`) | 읽힘 (**m/s**) | 약 100~200 ms 주기 | VC 페이지의 가속 페달 물리로 움직임(D + ACCEL). Pleos FAQ가 안내하는 방법 |
| 주행 규제 | `CarUxRestrictionsManager` | 읽힘 | 기어 변화 뒤 1~3 ms | **P = `requiresDO=false`, R·N·D = `requiresDO=true active=0x50`**(속도 0이어도). N에서는 이벤트가 없을 때가 있었음(직전 상태 유지) |
| 공조 자동 등 | – | 미시험 | – | 필요 없음 |

정차 판정 결론: **CarUxRestrictions(`isRequiresDistractionOptimization == false`)로 정차를 판정할 수 있다.** 기어 신호와 같은 시점에 바뀐다. 주차 브레이크 Telltale은 권한 때문에, AAOS `PARKING_BRAKE_ON`은 에뮬레이터에서 바뀌지 않아 쓸 수 없다. 기어(AAOS)는 보조로 남긴다.

### 제어(set) — P단·D단

| 호출 | P단 | D단 | 비고 |
| --- | --- | --- | --- |
| 앰비언트 색 `setAmbientLightBrightnessColor(지원 영역 그룹, ffff0000)` | 성공 | 성공 | VC 상태 `ambient_light.color`가 `#ff0000`으로 바뀜 → **VC 화면의 앰비언트 빛으로 보인다** |
| 앞유리 성에 제거 `setHvacDefroster(FRONT_WINDSHIELD, on/off)` | 성공 | 성공 | 리스너로도 같은 값이 돌아옴 |
| 에어컨 `setHvacAc` | 성공 | 성공 | |
| 내기순환 `setHvacRecirculation` | 성공 | 성공 | |
| 운전석 창문 `setWindowPosition(ROW_1_LEFT, 10)` | 성공 | 성공 | 값 범위 0~10. 20을 넣으면 `IllegalArgumentException`(최대값 초과) |

해석 주의: 에뮬레이터에서 D단 set이 성공했다고 실차에서 허용된다는 뜻은 아니다. 제품은 주행 중 제어를 하지 않는다(decisions.md).

### 음성 (Pleos TTS·STT, ON_DEVICE)

| 항목 | 결과 |
| --- | --- |
| TTS 초기화 | 성공. `speak` → `onStart` 8~9 ms, 14자 문장 `onDone`까지 2.9초 |
| STT 초기화 | 성공 (`SpeechToText(context, Mode.ON_DEVICE)`, 앱 등록 불필요) |
| STT `sendAudio`(16 kHz 16-bit mono PCM, macOS 합성 음성) | **동작**. "안개등 어떻게 켜" → `안개등 어떻게 켜?`, "응" → `응`. "앞유리가 뿌예" → `아기 우리가 학부의`(오인식). 중간 결과(`completed=false`)가 약 400 ms마다 옴 |
| STT 말 끝 → 최종 결과 | 음성이 끝난 뒤 약 0.4초에 `onEndedRecognition`과 `completed=true`가 함께 옴 |
| STT `request()`(차량 마이크) | `onReady` 70~100 ms, 마이크가 열림(`AudioRecorderImpl createAudioRecord ns:false, aec:false`). **호스트 마이크로 말한 소리는 인식되지 않음**(미확인: 맥 마이크 권한·스피커→마이크 경로). 소리가 없으면 9초 넘게 끝나지 않음 → 앱이 시간 제한 뒤 `stop()` 해야 함 |
| TTS 말하는 중 STT `request()` | 거부되지 않음(`onReady` 361 ms). 에코 제거(aec:false)가 없어 TTS 소리가 섞일 수 있음 → 실제 끼어들기 인식은 미확인 |

### Virtual Console 자동 조작

- VC는 브라우저 세션 heartbeat(`POST /browser/session?client=…`, 1초마다)가 있어야 주행 입력(브레이크·조향·기어·차속)을 받는다. 없으면 `503 {"error":"emulator_inactive"}`.
- 값 바꾸기: `PUT /vehicle/<endpoint>?<field>=<value>`. 목록은 `GET /vehicle/catalog`, 현재 값은 `GET /vehicle/state`.
  - 기어 `/vehicle/gear?gear=Park|Drive|Neutral|Reverse`, 브레이크 `/vehicle/controls?brake=0~100`, 조향 `/vehicle/controls?steering=-470~500`
  - 와이퍼 `/vehicle/body?front_wiper=Off|Auto|Low|High`, 창문 `/vehicle/body?window_fl=0~100`
  - 등화 `/vehicle/lights?feature=headlamp_mode&value=Off|Auto|Position|LowBeam`, `feature=high_beam|hazard&value=true|false`, 방향지시등 `/vehicle/turn-signal?mode=Off|BlinkLeft|BlinkRight`
  - 공조 `/vehicle/hvac?front_defroster=true|false`, `ac=`, `recirc=`
  - 착좌 `/vehicle/seat?kind=occupied&area=driver|passenger|rear_left|rear_right&value=true|false`
- 차속은 VC 페이지의 물리 루프가 만든다(D + ACCEL 버튼 누르고 있기). curl로 `speed=`를 보내도 반영되지 않음.

## 이전 기획 MooDrive 기록 (2026-11-07 갱신)

노션 메인 페이지가 원본이고, `docs/notion/`은 11-04 백업이다. 목표와 통과 기준은 `docs/GOALS.md`.

## 결정

- **아이디어**: 5번 맥락 기반 음악 추천 **MooDrive**로 확정 (사용자 확인 11-07). 2순위 카시트 달래기, 3순위 뒷좌석 놀이터. 노션 메인 할 일도 11-03에 갱신함.
- **Playground**: 프로젝트 `hmg-appchallenge-2026`, 개발사명 "기어르", 카테고리 음악/오디오. SDK 신청과 권한 화면 확인은 아직.
- **조명 정책**: 앰비언트 set의 P단 밖 허용 여부를 확인하기 전까지 실내 조명은 "P단에서 적용"이 기본값. N단·D단 신호대기는 P단이 아님. `ERROR_DRIVING_RESTRICTED`를 받으면 주행 중 전환을 멈추고 다음 P단에 재적용. 주행 중에는 앱 화면 무드 색으로 보여 주고, 데모 패널에서 허용 모드로 전환할 수 있음.

## 현황

| 항목 | 상태 |
|---|---|
| core 로직 | 자동 테스트 80개 통과 (`./gradlew :core:test`) |
| 데모 시나리오 | 3분 동안 장면 전환 7회(정차 제외), 곡 10개, DJ 멘트 7회 (테스트로 고정) (`./gradlew :core:demoTrace`) |
| 디자인 | 화면 3종. 자동 검사(조작 수·터치·글자·실측 대비) 1920×720, 1408×792 모두 통과. 토큰 동기화 검사 통과 |
| app (Android) | Pleos Vehicle·NaviHelper·TTS 연결, Media3 포그라운드 재생·오디오 포커스, release APK 에뮬레이터 실행 확인 (11/07). Pleos STT, 휴게소 경유지, FusedLocation은 아직 |
| 기획서 | 독립 심사 점수: v0.2 2.8 → 1바퀴 2.9 (`plan-review-v3.md`) → 2바퀴 3.1 (`plan-review-r2.md`) → 3바퀴 3.3 (`plan-review-r3.md`) → 4바퀴 3.35 (`plan-review-r4.md`) → 5바퀴 3.50 (`plan-review-r5.md`) → 6바퀴 3.53 (`plan-review-r6.md`) → 7바퀴 **3.55** (`plan-review-r7.md`). 상승폭이 0.02까지 줄어 클라우드 루프 종료. 심사 판단: SDK·APK·영상 없이 도달 가능 3.7~3.8, 4.0+는 실측 1건 필요 |
| 음원 | 시연용 자체 합성 66곡 (`tools/demo-music/generate.py`, 저작권 문제 없음, 곡당 약 30~60초 반복). 음질은 사람이 들어 보고 판단 필요. 실제 음원 교체는 `docs/music-tagging.md` |

## 운영사무국 답변 (10-29 문의, 11-06 수신)

| 항목 | 답변 | 상태 |
|---|---|---|
| 주행 제한 기준 | Vehicle Service는 NO_SETUP이 아니라 P단 여부를 확인. 시트 이동·메모리 선택·도어 제어 등 제한 대상 set은 P단이 아니면 `ERROR_DRIVING_RESTRICTED` | 확인 |
| 주행 중 앰비언트·공조 | 일괄 확정 답 아님. 추가 안내 예정 | **미확정**, 에뮬레이터 실측 예정 |
| 에뮬레이터 조명 표시 | 눈으로 확인할 방법 없음. 조명 API·실차 미지원이라는 뜻은 아님 | 확인 → 앱 미리보기와 API 응답으로 시연 |
| 개발·배포 권한 | 개발·시험은 매니페스트 선언만으로 가능. 배포 시 승인된 권한만 심사 통과. 공개 문서 외 API 없음, 별도 신청 절차 없음 | 확인 |
| NaviHelper 잔여 시간·거리 | 공식 안내는 NaviHelper intro 문서. `onTripInfo`·`onDrivingInfo` 콜백 존재, 필드 문서는 비어 있음 | 실측 예정 |
| PT 세부사항 | 서류 통과팀에 개별 안내. 일정은 공지사항 | 확인 |
| 오디오 | 재생 전 포커스 요청. GAIN 재생, LOSS 정지, LOSS_TRANSIENT 일시정지 후 복귀, CAN_DUCK 감쇄. 동시 재생은 보장되지 않고 가장 보수적인 결과 적용. TTS라는 이유로 NAVIGATION 컨텍스트 불가 | 확인, 앱 미구현 |
| 차속, Coming Soon·개별 API, PT 장비 | 추가 안내 예정 | **미확정** |

## 맥 검증 결과 (2026-11-07, Pleos Connect Emulator 2.1.0 / Vehicle SDK 2.3.2 / NaviHelper 3.0.5)

로그 태그 `MooDriveMeasure`, 신호는 Virtual Console(`adb forward tcp:8088 tcp:8088`). 화면은 `docs/screens/emulator/`.

| 항목 | 결과 |
|---|---|
| 앱 실행 | 설치·실행 정상. 앱 영역 약 1680×1200, 나머지는 차량 UI가 차지 |
| 기어 | 공개 SDK에 없음. AAOS `VehiclePropertyIds.GEAR_SELECTION` + `android.car.permission.CAR_POWERTRAIN`으로 수신 (P=4, N=1, D=8) |
| 와이퍼 | State ON/OFF 알림 수신. Switch: Low=`CONTINUOUS_LEVEL_1`, High=`CONTINUOUS_LEVEL_3`. **단계만 바뀌면 알림이 없어** 1초마다 스위치 조회로 해결 → 비/폭우 장면 전환 확인 |
| 하향등 | `lowBeam state` ON/OFF 수신 |
| 앰비언트 색·밝기 | `areas=[ROW_1_LEFT, ROW_1_RIGHT, ROW_2_LEFT, ROW_2_CENTER, ROW_2_RIGHT]` 한 그룹. 고정 영역(ROW_1_LEFT 단독)은 `area ID: 0x1 not supported` 실패 → 지원 영역 사용. **P단·D단 모두 set 성공, 재조회 값 일치** (예: 요청 ff47ebff → 조회 47ebff, 알파 없음) |
| 주행모드 자동 색 | `getAmbientColorAutoByDriveModeEnabled`는 에뮬레이터 미지원 (`0x21200787` not supported) → 복원 로직만 유지 |
| 제한 대상 set (도어로 시험) | P단 성공, **D·N단 실패**. 오류는 `java.lang.Exception`에 메시지 `IllegalArgumentException: 50001`, `getErrorCode`는 없음 → 메시지 `50001`로 판별. N단을 P단으로 보지 않게 한 수정이 맞음을 확인 |
| 내비 | `requestRoute` 후 `onDrivingInfo`: `destination.duration=60`(초), `distance=50`(m). `requestTrip` 후 `onTripInfo`: `eta=2, distance=12`, 정체 구간 포함. 주행 중 갱신 주기는 미확인 |

해석 주의: 에뮬레이터에서 D단 앰비언트 set이 성공했지만 **실차 허용을 뜻하지 않는다.** 운영사무국 공식 답변 전까지 기본값은 P단 적용 유지.

발견한 문제와 남은 확인:
- 표준 Car API로 기어를 읽는 방식이 App Market 심사를 통과하는지 미확인 (운영사무국 답변: 공개 문서 외 Pleos API는 없음)
- 내비 앱이 앞으로 오면 앱이 멈추던 문제 → Media3 포그라운드 서비스로 해결. 내비가 앞에 있어도 와이퍼 신호로 장면·곡·멘트 전환 확인
- 안드로이드 기본 TTS는 Pleos 이미지에 엔진이 없어 실패(`not bound to TTS engine`) → Pleos TTS(ON_DEVICE)로 교체, `tts start/done` 확인
- 안드로이드 기본 음성 인식도 같은 이유로 동작하지 않을 가능성이 높음 → Pleos STT로 교체 필요 (미구현)
- 오디오 포커스: `requestAudioFocus` USAGE_MEDIA 획득, 재생 상태 PLAYING 확인. 내비 음성과 겹칠 때의 덕킹은 아직 실측 안 함
- release APK 설치·실행 확인 (`apk/MoodLab_MooDrive_release.apk`, 70MB). 서명 키 `moodrive-release.jks`와 비밀번호는 커밋하지 않고 로컬에만 있음 → 분실하면 같은 서명으로 업데이트 불가, 백업 필요
- 소리는 사람이 직접 들어 확인 필요 (로그·시스템 상태로만 확인함)

## 남은 일

- 맥: `docs/MAC_HANDOFF.md` (빌드, Pleos SDK 연결, D단 앰비언트 시험, 음원, 시연 영상)
- **다음 맥 작업**: Pleos STT 연결, FusedLocation(일몰·터널 GPS), 휴게소 경유지, 팀명 확정 후 APK 파일명 변경, 기획서 DOCX 옮기기
- 7바퀴 심사 뒤 클라우드에서 추가 반영(재심사 안 함): 정체 중에도 피날레, 휴게소 응답 양방향 표현("집에 가자"·"휴게소 바로 가자"·"네 알겠어요"·"이거 별로야"), 시나리오 ③ 크루징 문장, 기술 스택 (예정) 표기
- 클라우드에 남은 작은 일: 앱 시계 표시가 신호 사이에 멈추는 문제, 각주 순서
- 제출 직전: 접수양식 DOCX에 옮기기, 전원 서명 동의서, 근거 원문 재확인, GitHub 심사 계정 초대

## 작업 환경 메모

- 클라우드에서는 `dl.google.com`(Android SDK·AGP), `document.pleos.ai`, `nexus-playground.pleos.ai`가 막혀 있음. 환경 설정의 허용 도메인에 추가하면 다음 세션부터 Android 빌드 가능.
- Maven Central이 가끔 429를 줌 → 30~60초 기다렸다가 다시 시도.
- Playground 프로젝트 Key는 커밋하지 말 것 (`local.properties` 사용).
