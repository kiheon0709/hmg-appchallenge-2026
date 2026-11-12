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
