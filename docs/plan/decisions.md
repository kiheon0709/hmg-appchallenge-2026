# 결정 기록

한 줄씩: 날짜·시각, 세션, 무엇을 왜.

- 11-13 상위: 앱 이름 "옆자리(SideSeat)", 새 패키지 `io.moodlab.sideseat`. 이전 기획(MooDrive)의 음악 로직은 쓰지 않음.
- 11-13 상위: 세션 4개가 한 작업 폴더를 공유(gitignore된 local.properties·서명 키·SDK 문서가 worktree에는 없어서). 파일 담당으로 충돌을 막음.
- 11-13 상위: 주행 중에는 차량 제어를 하지 않고 말로만 권함(실차에서 주행 중 제어 허용 여부 미확정).
- 11-13 상위: 공식 출처 조사 반영. S12 제외, S10 순서 비채점, K10은 TPMS 중심, 수치(공주거리·CO2) 미사용. 상세는 product-spec.md 5.2 '출처 반영'.
- 11-13 dev-vehicle-core: S07 고속도로 고장의 제한 시간을 8초 → 15초. 비상등 뒤에 말로 고르는 단계(STT 왕복)가 있어서. `TrainingConfig.limitsMs`로 바꿀 수 있음.
- 11-13 dev-vehicle-core: 상황 시작 때 이미 켜져 있는 스위치는 "되돌려 두세요"라고 말하고 최대 8초 기다림. 그래도 그대로면 된 것으로 치되 ★3은 주지 않음(손이 찾아간 게 아니라서).
- 11-13 dev-vehicle-core: 제한 시간을 넘겨도 같은 시간만큼 더 기다리고 그 안에 끝내면 ★1(명세 5.4 "시간 초과 ★1"), 넘기면 ★0. S03 급정거만 1.5초 넘으면 충돌(★0).
- 11-13 dev-vehicle-core: 돌발 모드는 말로 답하는 상황(S07·S08)과 시간 제한 없는 S11을 뽑지 않음. 반응 속도 모드라서.
- 11-13 dev-vehicle-core: S12 와이퍼 이물질은 출처 확인 전까지 `TrainingConfig.disabled` 기본값으로 꺼 둠.
- 11-13 dev-voice-ai: Claude API는 공식 Java SDK 대신 HttpURLConnection + SSE로 직접 호출. anthropic-java 3.8.0의 core jar만 43MB(+Jackson·kotlin-reflect·OpenTelemetry)라 APK 크기·dex 시간·Jackson 초기화로 첫 응답 지연이 늘고, SDK README에 Android 지원 언급이 없음. 상위 세션이 "안드로이드에서 안 맞으면 raw HTTP" 허용. 요청 형태는 claude-api 스킬 cURL 문서 기준(anthropic-version 2026-10-21).
- 11-13 dev-voice-ai: 모델 기본값 FAST=claude-haiku-5-5(thinking 끔, effort low: 주행 중 첫 문장 지연 최소), REVIEW=claude-opus-5-5(thinking은 끌 수 없어 effort low, 서버 쪽 fallbacks "default"). local.properties LLM_MODEL_*로 바꿈.
- 11-13 dev-voice-ai: 지연 구조 = 듣기 시작 시 max_tokens 0 예열(TLS 연결+프롬프트 캐시), EPD(말 끝)에서 마지막 중간 결과로 추측 요청 → STT 최종이 같으면 재사용, 0.6초 안에 첫 문장 없으면 "음, 잠깐만요.", 3.5초 안에 없으면 지식 파일 답으로 대체. 추측 요청은 Instructor.prepareQuestion(parked, context)를 부른 경우만(계약에 기본 구현 있는 메서드로 추가).
- 11-13 dev-voice-ai: TTS 스트리밍은 speak(isFinal=false)로 이어 보내되 첫 문장은 즉시, 나머지는 한 문장 늦게 보내 마지막만 isFinal=true. 문장이 하나뿐이면 "."를 isFinal=true로 보내 발화를 닫음(실측 대상, 안 되면 TtsSpeaker chunked=false로 문장별 재생).
- 11-13 dev-voice-ai: TTS 오디오는 USAGE_ASSISTANT+SPEECH, GAIN_TRANSIENT_MAY_DUCK(지연 획득 허용). NAVIGATION 컨텍스트는 쓰지 않음(운영사무국 안내). 효과음은 USAGE_GAME+SONIFICATION, SoundPool. 선배가 말하거나 듣는 동안 효과음 -10dB.
- 11-13 dev-voice-ai: 끼어들기(TTS 중 STT)는 실측 전까지 끔. listen()은 말이 끝난 뒤 마이크를 연다.
- 11-13 dev-voice-ai: 지식 파일(assets/knowledge)은 safety-sources.md의 [원문] 확인분만. 검색 발췌 수치(차간거리 1.5배, 안개 상향등 산란 이유, 반응시간 0.7~1초)와 앞유리 이물질은 넣지 않음. 스위치 위치(switches.md)는 공식 출처가 아닌 "앱 기본 안내(차종마다 다름)"로 표시.
- 11-14 상위: 에뮬레이터 실측 반영. Telltale·비상등·안개등은 읽을 수 없어 S05·S07·S10·S11 판정 변경, K07·K10 제외. 정차 판정은 CarUxRestrictions만 쓰고 AAOS 기어·차속은 쓰지 않음(허용 범위 준수).
- 11-14 dev-vehicle-core: 위 "S07 15초" 결정을 바꿈. S07·S08처럼 말로만 답하는 상황은 시간 제한 없이 틀린 횟수로 별(0번 ★3, 1번 ★2, 2번 이상 ★1). TTS가 보기를 읽는 시간 때문에 시간 채점이 불공정해서.
- 11-14 dev-vehicle-core: S05 감속 판정은 화면 속 가상 속도가 평소(80km/h)의 50% 이하가 되면 완료(`TrainingConfig.fogSlowFraction`). 브레이크를 밟으면 초당 25km/h 줄어 약 1.6초.
- 11-14 dev-vehicle-core: S11 주차 브레이크 질문의 "아직이요"는 감점하지 않고 다시 물음(YES_NO). S11은 별 3개로 기록하고 UI가 "확인 완료"로 보여 줌.
- 11-14 dev-vehicle-core: 휴게소 경유지는 NaviHelper에 장소 검색 API가 없고 addWaypoint는 좌표가 필요해서, `requestNavigationScreen(SearchPlace("휴게소"))`로 내비 검색 화면을 연다(운전자·동승자가 골라 경유지로 넣음). `NAVI_CUSTOM_MAP` 퍼미션 필요.
- 11-14 dev-vehicle-core: 차종 키는 VIN 앞 8자리(제조사·차종)만 쓴다. 일련번호를 포함한 VIN 전체는 저장·기록하지 않는다.
- 11-14 dev-vehicle-core: 게이트웨이 보정값 — 조향 부호 반전(SDK가 VC와 반대), 에뮬레이터에서 착좌 반전(Build 지문으로 판별), 창문은 capability 범위(0~10)를 %로 환산, 와이퍼 스위치 200ms 조회(켜져 있을 때만). `GatewayConfig`로 바꿀 수 있음.
- 11-14 dev-sim-ui: `DebugPanel(state: VehicleState, onChange: (VehicleState) -> Unit, modifier, onClose)`로 정함. 화면이 게이트웨이를 직접 만지지 않게 상태를 받아 바뀐 상태만 돌려줌.
- 11-14 dev-sim-ui: 음성 대화 상태는 `TalkUi` 하나로 묶어 홈·코치·리뷰가 같이 씀. 스위치 위치 안내 문장은 화면에서 새로 쓰지 않고 core `SituationCatalog`의 `hint`만 보여 줌(출처 확인된 문장만 쓰기 위해).
- 11-14 dev-sim-ui: 화면 검수는 저장소 밖 임시 Compose Desktop 프로젝트로 같은 소스를 렌더링해 스크린샷으로 함(에뮬레이터는 통합 세션만 쓰므로). 실기 프레임 속도는 미확인 → `SideSeatUiDebug.logFrames = true`로 `SideSeatMeasure` 로그를 남겨 통합 세션이 잼.
- 11-14 dev-sim-ui: 미니게임 화면은 M1에 더해 M2(깜빡이 모스)·M3(창문 버저)도 만듦(core에 엔진이 이미 있어서).
- 11-14 dev-vehicle-core: 명세 5.2 표(S11 "주차 브레이크만 채점")와 아래 "실측 반영" 목록(S11 전조등·창문은 신호, 주차 브레이크는 말)이 다를 때 나중에 쓴 실측 반영을 따름. 셋 다 확인은 하고 감점은 없음.
- 11-14 dev-vehicle-core: 출처 반영으로 감점 조작을 S02(와이퍼만)·S03(핸들 급조작)에만 남김. S10은 브레이크를 떼면 3초를 처음부터 다시 셈(감점 없음). 미니게임 M3 퀴즈의 안개 상향등·경고등 모양 문제는 출처가 있는 50% 감속·TPMS 조치 문제로 바꿈.
- 11-13 dev-integration: 정차 판정은 CarUxRestrictions(P만 비규제)로 함. 실측에서 기어와 같은 시점에 바뀌고 권한이 필요 없음. Telltale(PRIVATE 권한)·AAOS 주차 브레이크(에뮬레이터에서 안 바뀜)는 못 씀.
- 11-13 dev-integration: 저장소에 올리는 release APK에는 LLM 키를 넣지 않음(APK에서 키를 꺼낼 수 있음). 로컬 시연용만 `-PembedLlmKey=true`.
- 11-13 dev-integration: release에 R8을 켬(60→23 MB). Pleos SDK의 Gson TypeToken 때문에 Signature·Gson keep 규칙 필수.
- 11-13 dev-integration: 세로 화면은 폼 팩터 콘솔(Go 브리지 필요) 대신 `am task resize`로 앱 창을 835×1210으로 좁혀 확인함. `wm size`로 디스플레이를 세로로 바꾸면 차량 런처가 따라오지 않아 시험에 쓸 수 없음.
- 11-13 dev-integration: 에뮬레이터는 처음에 인터넷이 없음. 차량 설정 Wi-Fi에서 AndroidWifi 연결 후 LLM 동작.
- 11-14 dev-voice-ai: 실측 반영. Pleos TTS는 speak(isFinal=false) 조각을 다음 speak가 올 때까지 재생하지 않아(채움말이 LLM 첫 문장을 266~1377ms 기다림) TTS 기본을 문장별 순차 재생(isFinal=true, onDone 뒤 다음)으로 바꿈. onStart가 speak 뒤 약 10ms라 문장 사이 공백도 작음.
- 11-14 dev-voice-ai: STT는 onEndedRecognition이 최종 결과(completed=true)보다 먼저 와서, 종료 뒤 최대 0.6초 최종 결과를 기다리게 고침(짧은 "응"이 빈 결과로 끝나던 문제). EPD 이벤트가 오지 않아 RMS(말하는 동안 최대값의 18% 아래가 0.35초)로 말 끝을 잡아 추측 요청 신호로 씀.
