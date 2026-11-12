# 밤샘 작업 목표 (2026-11-13 밤 → 11-14 아침)

초보 운전 코치 + 위기 상황 시뮬레이터를 기획하고 에뮬레이터에서 돌아가는 앱으로 만든다. 공모전 1등이 목표다. 범위를 줄여 빨리 끝내지 않는다.

## 1. 완료 조건

아래가 전부 참이고, 각 항목의 증거 파일이 있어야 끝이다.

**기획**
- [x] `docs/plan/product-spec.md`: 한 문장 정의, 타깃과 불편(근거는 `docs/research/` 파일·절 표기), 모드별 사용 흐름(정차 훈련 / 주행 코치 / 도착 요약 / 보너스 미니게임), 훈련 상황 목록(상황마다 판정 신호·성공 조건·점수·대사), 코치 규칙 목록(조건·문장·말하는 횟수 상한), 화면 구성(가로·세로), Gleo·순정 경고와의 차이, 사업성, 위험.
- [x] 훈련 상황의 대처 절차마다 공식 출처(도로교통공단, 차량 취급설명서 등) 링크. 출처를 못 찾은 상황은 앱에서 뺀다.
- [x] `docs/plan/application-draft.md`: 공모전 접수양식 항목 순서대로 쓴 기획서 초안(`docs/pleos-docs/contest/guide_submission.md` 기준).

**앱**
- [x] `./gradlew :core:test` 통과. 상황 판정·점수·코치 규칙은 `core`에 있고 테스트가 있다.
- [x] `./gradlew :app:assembleRelease` 성공, `apk/`에 release APK.
- [x] Pleos Connect Emulator 2.1.0에 설치해 아래를 Virtual Console 조작으로 확인하고, 화면 캡처를 `docs/screens/sim/`에, 로그 발췌를 `docs/STATUS.md`에 남긴다.
  - P단에서 훈련 시작 → 가짜 3D 주행 화면이 움직인다.
  - 훈련 상황 5개 이상이 실제 신호로 판정된다(예: 와이퍼 스위치 → 화면 속 비가 닦임, 성에 제거 → 김서림 걷힘, 브레이크 → 급정거 반응 시간).
  - 상황 중 앰비언트 조명 set 호출과 효과음·TTS 대사가 나온다.
  - 기어를 D로 바꾸면 훈련 화면이 닫히고 코치 모드가 된다.
  - 코치 규칙 3개 이상이 신호 조합으로 발화한다(예: 와이퍼 켜짐 + 전조등 꺼짐).
  - P단으로 돌아오면 요약 화면과 음성 총평이 나온다.
  - 미니게임 1개 이상(핸들 금고 다이얼)이 조향각으로 동작한다.
  - 가로·세로 창 모두에서 레이아웃이 깨지지 않는다.
- [x] 음성 대화는 스트리밍 파이프라인으로 만든다: STT 중간 결과(`onUpdated`)가 오는 동안 답 준비를 시작하고, LLM 응답을 스트리밍으로 받아 문장 단위로 끊어 TTS `speak(text, isFinal=false)`로 이어 보낸다. 짧은 명령·정해진 대답은 LLM 없이 앱 규칙으로 즉시 처리한다. 말 끝 → 첫 음성까지의 시간을 에뮬레이터에서 재서 `docs/STATUS.md`에 적는다(목표 1초 이내, 못 미치면 원인과 측정값을 적는다). TTS 재생 중 STT를 열 수 있는지(끼어들기)도 실측해 기록한다.
- [x] 실측 기록: 차량 신호 지연, 화면 프레임 속도, P단·D단에서의 제어 성공 여부를 `docs/STATUS.md`에 표로.
- [x] `docs/plan/demo-script.md`: 1분 소개 영상과 PT 시연의 장면 순서(Virtual Console에서 누를 것 포함).

## 2. 제약

- Pleos SDK는 Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech, SpeechToText, FusedLocation만. Gleo 연동, LLM SDK, 카메라, 차량 마이크 음성의 외부 전송 금지. 음성 인식은 Pleos STT만.
- 차속·기어는 공개 SDK에 없다. 기존 코드는 기어를 AAOS Car API로 읽는다(5종 밖). 정차 판정은 공개 SDK 범위(주차 브레이크, 주행 규제 리스너 등)로 바꿀 수 있는지 실측하고, 안 되면 현행 유지하되 `docs/STATUS.md`에 적는다.
- 주행 중에는 영상·애니메이션·긴 글·키보드 금지. 훈련과 미니게임은 정차 중에만. 드라이빙 뷰를 가리지 않는다. 가로·세로 모두 대응, 레터박스 금지.
- "급발진"이라는 말을 쓰지 않는다("의도치 않은 가속", "페달 오조작 대응").
- LLM이 안전 정보를 지어내지 않게 한다. 대처법은 검증한 자료 안에서만 답한다. 외부 LLM은 Claude API를 쓴다. 키는 `local.properties`의 `LLM_PROVIDER`·`LLM_API_KEY`에 있다(값을 로그·문서·커밋에 남기지 않는다, 빌드 설정으로 주입). 키가 없거나 네트워크가 끊기면 미리 쓴 문장으로 동작하게 한다.
- 건드리지 않는 것: `moodrive-release.jks`, `local.properties`, `docs/pleos-docs/raw/`·`reference/`(커밋 금지), `기획서_초안.md`, 기존 노션 페이지.
- 푸시, 공모전 제출, 메일 발송, 외부 사이트 로그인·글쓰기 금지. 커밋은 로컬에만, 기능 단위로.
- 하위 세션은 같은 작업 폴더를 쓴다. 세션마다 맡은 파일만 고친다. 에뮬레이터 설치·실행은 통합 담당 세션만 한다.

## 3. 맥락

- 왜: 2026-11-15(일) 23:59 접수 마감. 1차는 서류(기획서) 심사이고 시연 영상·GitHub는 가산점. 평가항목은 차량특화성, 창의성(불편과 해결의 명확성), 기술 활용성, 사업성. 2차 PT는 에뮬레이터 시연.
- 기획 재료: 노션 「차용 방향 4가지 상세」 방향 1, 노션 「아이디어 후보」 3차 후보 1, `docs/research/2026-11-13_ref-hackathons-github.md`(JALIS, Driving Buddy, javascript-racer, CarMate), `docs/research/2026-11-13_ref-car-app-ecosystems.md`(기능 지도), `docs/research/2026-11-12*.md`(초보·김서림·후미등 근거).
- Pleos 자료: `docs/pleos-docs/README.md`부터. API는 `INDEX.md`를 grep.
- 기존 코드: `core`(순수 로직, 테스트 80개), `app`(`io.moodlab.moodrive`: 차량 신호 `vehicle/`, 재생 `audio/`, 화면 `ui/`). Pleos TTS·차량 신호·내비 연결과 release 빌드는 이미 된다. 이전 기획(MooDrive)의 음악 로직은 쓰지 않아도 된다.
- 이미 실측한 것(`docs/STATUS.md`): 와이퍼 스위치는 단계 변화 알림이 없어 1초마다 조회. 앰비언트 set은 P단·D단 모두 성공, 지원 영역 그룹으로 호출. 도어 같은 제한 대상 set은 P단 밖에서 메시지 `50001`로 실패. 안드로이드 기본 TTS·음성 인식은 엔진이 없어 실패. 에뮬레이터는 조명 변화를 화면에 보여 주지 않는다.
- 아직 모르는 것: Pleos STT 동작, 공조·창문 set, 조향각·브레이크 신호의 갱신 속도, 에뮬레이터 그래픽 성능.

## 4. 막혔을 때

- 멈추지 않는다. 대안을 골라 진행하고, 무엇을 왜 골랐는지 `docs/plan/decisions.md`에 한 줄씩 남긴다.
- 실측으로 안 되는 기능은 빼거나 대체하고 "미확인"·"불가"로 기록한다. 된다고 쓰지 않는다.
- 사용자만 할 수 있는 일(API 키, 계정, 소리 직접 듣기, 규정 문의)은 `docs/plan/needs-user.md`에 모아 두고 나머지를 계속한다.
- 같은 문제로 세 번 실패하면 접근을 바꾼다.

## 5. 점검 결과 (2026-11-13, 상위 세션이 저장소 파일과 직접 대조)

최종 소스 `90f23d7`, 최종 빌드 커밋 `f509329`(08:02 빌드, release 스모크 크래시 0, core 77개·app 67개 테스트 통과). 푸시하지 않았다. 1절의 `[x]`는 아래 표의 대조를 마친 항목이다.

| # | 완료 조건 | 판정 | 대조한 증거 |
| --- | --- | --- | --- |
| 기획 1 | 제품 명세의 필수 내용 | 충족 | `docs/plan/product-spec.md`: 1절 한 문장, 2절 타깃·불편(리서치 파일 표기), 4~8절 모드별 흐름, 5.2 상황 표(판정 신호·제한 시간)·5.4 점수·5.5 대사, 6.2 코치 규칙·6.1 말수 상한, 9절 화면(가로·세로), 3절 Gleo·순정 경고와의 차이, 12절 사업성, 13절 위험 |
| 기획 2 | 상황별 공식 출처, 없는 것은 제외 | 충족 | `docs/plan/safety-sources.md`(15개 상황, 출처 URL과 확인 수준), 명세 5.2.1 표(S01~S11 링크). 출처 없는 S12는 챕터에서 제외(core 커밋 `4bf8a16`) |
| 기획 3 | 접수양식 순서의 기획서 초안 | 충족 | `docs/plan/application-draft.md` 1~8번 절(기존 접수양식 초안 `기획서_초안.md`와 같은 항목 순서) |
| 앱 1 | core 테스트 통과, 판정·점수·코치가 core에 | 충족 | 상위 세션이 `./gradlew :core:test` 직접 실행: 77개 통과, 실패 0. `core/src/main/kotlin/io/moodlab/sideseat/core/`의 `DefaultTrainingEngine.kt`·`DefaultDriveCoach.kt`·`TrainingConfig.kt`, 테스트 `TrainingEngineTest.kt`·`DriveCoachTest.kt`·`MiniGameTest.kt` 등 |
| 앱 2 | assembleRelease, apk/ | 충족 | `apk/MoodLab_SideSeat_release.apk` 22,839,355바이트(08:02). `docs/STATUS.md` "최종 빌드"와 "release APK" 절(release 스모크 크래시 0) |
| 앱 3-1 | P단 훈련 → 주행 화면이 움직임 | 충족 | `docs/STATUS.md` 장면 표 1번, 프레임 표(58~60fps). 캡처 `c1_s01_heavy_rain_active_landscape.jpg`, `v2_calm_cruise_traffic_wiper_off.jpg`(상위 세션이 캡처를 직접 확인) |
| 앱 3-2 | 실제 신호 판정 5개 이상 | 충족 (9개) | 장면 표 2번과 로그 발췌(`training … SUCCESS steps=…`). 캡처 `c1_*`, `c2_*`, `c3_s10_*`, `c4_*` |
| 앱 3-3 | 앰비언트 set, 효과음·TTS | 충족 | 로그 `app ambient ALERT ok=true`, `app say …`. 캡처 `11-virtual-console.png`, `virtual_console_ambient_alert_red.jpg`. 소리는 `docs/STATUS.md` "소리 출력 확인(기기 기준)"에서 믹서 트랙으로 확인: 빗소리 반복과 TTS가 동시에 활성, TTS 중 효과음 약 10dB 감쇄, 주행 전환 때 정지. 효과음 파일 18개는 `tools/sfx/REPORT.md`에서 클리핑 0, 트루 피크 -1.4dBTP 이하 |
| 앱 3-4 | D로 바꾸면 훈련 닫힘·코치 | 충족 | 로그 `app mode TRAINING -> COACH`. 캡처 `coach_after_leaving_p_training_closed.jpg` |
| 앱 3-5 | 코치 규칙 3개 이상 | 충족 (K01·K05·K06, K08 경유지) | 로그 `app coach say rule=K01…`. 캡처 `coach_k01_*`, `coach_k05_*`, `coach_k06_*`, `coach_k08_rest_stop_navi_search.jpg` |
| 앱 3-6 | P 복귀 → 요약·음성 총평 | 충족 | 로그 `app mode COACH -> REVIEW`, `review first sentence`. 캡처 `review_after_park_*.jpg` |
| 앱 3-7 | 금고 다이얼(조향각) | 충족 | 로그 `app safedial solved=3/3`. 캡처 `m1_safe_dial_*.jpg` |
| 앱 3-8 | 가로·세로 창 | 충족 (창 크기 변경으로 확인) | 가로 1682×1210, 세로 835×1210. 캡처 `portrait_*`, `v2_portrait_*`. 폼 팩터 콘솔(12.9·14.6인치) 확인은 아래 "추가 확인" 참조 |
| 앱 4 | 음성 스트리밍과 지연 측정 | 충족 (최종 측정 24회 중 23회가 1초 이내) | 코드 `app/src/main/java/io/moodlab/sideseat/voice/`: `TtsSpeaker.kt`(문장을 `speak(s, false)`로 이어 보냄), `SeniorInstructor.kt`(STT 중간 결과로 빠른 길 판단·예열, Claude 스트리밍), `FastPath.kt`, `SentenceChunker.kt`. 실측 `docs/STATUS.md`: 빠른 길 중앙값 약 20ms. Claude 답 시작 중앙값 0.75초(15초 간격 12회, 범위 0.65~1.25)·0.76초(60초 간격 12회, 범위 0.61~1.33), 24회 모두 연결 재사용. 꼬리를 줄인 뒤(voice `90f23d7`) 최종 측정: 15초 간격 12회 × 2세트, 중앙값 0.77초·0.87초, 24회 중 23회가 1초 이내, 최대 1.03초(그 시행은 서버 첫 토큰이 0.82초). 남은 변동은 서버 응답 시간이다. 처음에는 1.3~1.7초였고, 원인(답을 자를 때 소켓을 끊어 매번 새 연결)을 찾아 고쳤다(voice `76e8f7a`). 음성 인식이 말 끝 뒤 약 0.4초에 결과를 주므로 실제 말 끝 기준으로는 약 1.2초. 말하는 중 `request()`는 열림, 끼어들기 인식은 미확인으로 기록 |
| 앱 5 | 실측 표 | 충족 | `docs/STATUS.md` 실측 표(신호 지연, 프레임, P·D단 제어)와 ③ CPU·메모리(4코어 기준 10~15%, PSS 114MB) |
| 앱 6 | 시연 대본 | 충족 | `docs/plan/demo-script.md`(1분 영상 표, PT 순서, Virtual Console 실제 카드 이름) |

### 추가 확인 (완료 조건에는 없지만 끝까지 시도한 것)

| 항목 | 결과 | 증거 |
| --- | --- | --- |
| 폼 팩터 12.9인치 세로(628×855)·14.6인치 가로(1700×1185) | **확인됨.** 홈·훈련·코치·리뷰가 두 크기에서 정상. 결함 1건(말하기 버튼 글자 잘림)은 고쳐서 두 크기에서 다시 확인(ui `a7d894b`, 통합 `e10f0d1`) | `docs/screens/sim/ff_12.9_portrait_*.jpg`, `ff_14.6_landscape_*.jpg`(12장, 상위 세션이 직접 확인). 공식 문서의 브리지 도구는 내려받지 않고, 같은 일을 에뮬레이터 콘솔 명령(`adb emu multidisplay add`)으로 했다. 크기는 FormFactorConsole 프리셋 값 |
| 차량 마이크(`request()`) 음성 인식 | **사람 목소리가 있어야 끝낼 수 있음.** 무음의 원인은 찾아서 해결했다: 에뮬레이터를 `-allow-host-audio` 없이 켜면 마이크 입력이 0으로 채워진다. 이 옵션으로 켜니 호스트 마이크 소리가 STT 입력으로 들어온다(입력 RMS: 마이크 켬 25~44, 끔 0.1~0.7). 남은 것은 사람이 맥 마이크에 말하는 것뿐 | `docs/STATUS.md` 차량 마이크 STT 항목, `docs/plan/needs-user.md`의 절차. 자동으로 못 한 이유: 가상 마이크에 녹음을 넣는 방법은 에뮬레이터가 두 번 죽었고(에뮬레이터 버그), 맥 스피커는 음소거 상태라 사용자 설정을 바꾸지 않았다. 오디오를 직접 넣는 방식(`sendAudio`)으로는 인식을 확인했다 |

### 사용자만 할 수 있는 것

`docs/plan/needs-user.md` 참조: 소리 직접 듣기, 맥 마이크 권한, 여러 작품 제출 문의.
