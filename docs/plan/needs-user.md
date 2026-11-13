# 사용자만 할 수 있는 일

- 소리(빗소리·TTS 음질·볼륨 균형)를 직접 들어 보고 판단하기
- 여러 작품 제출 가능 여부를 운영사무국에 확인하기
- (dev-integration, 11-13, 06:55 갱신) 에뮬레이터 차량 마이크로 말해서 인식되는지 확인하기(사람 목소리 필요). 실측으로 원인을 좁힘: 맥 마이크 권한은 이미 허용돼 있음(호스트 마이크를 켜면 STT 입력 RMS 25~44, 끄면 0.1~0.7). 처음 무음이었던 것은 에뮬레이터를 `-allow-host-audio` 없이 켰기 때문(도움말: "Otherwise, zeroes out audio"). `say`로 스피커 재생을 시험했지만 맥 출력이 음소거(output muted:true)라 소리가 나지 않았고, 밤이라 음소거를 풀지 않았음. 에뮬레이터 gRPC `injectAudio`로 가상 마이크에 녹음을 넣는 방법은 에뮬레이터가 2번 모두 죽어서(SIGSEGV `audio_forwarder_enable`, 에뮬레이터 버그) 쓸 수 없음. 절차: ① 에뮬레이터를 `emulator -avd Pleos_Connect_v2.1 -no-snapshot-load -allow-host-audio`로 켜기(지금 이렇게 켜져 있음) ② `adb emu avd hostmicon` ③ 앱 "질문하기"를 누르고 맥 마이크에 "안개등 어떻게 켜?"라고 말하기 → `adb logcat -s SideSeatMeasure`에 `app heard kind=FREE source=mic '안개등 어떻게 켜?'`가 보이면 성공. 그전까지 시연은 화면 선택 버튼과 디버그 녹음 입력(`ask_pcm`, 화면에 "[녹음 입력]" 표시)으로 함.
- (dev-integration, 11-13 20:30) 호출어 "타비"를 사람 목소리로 직접 불러 보기. 에뮬레이터를 다시 켜도 될 때:
  1. 에뮬레이터를 `emulator -avd Pleos_Connect_v2.1 -no-snapshot-load -allow-host-audio -crash-report-mode never`로 켜고 `adb emu avd hostmicon`(맥 시스템 설정 → 개인정보 보호 및 보안 → 마이크에서 터미널·에뮬레이터 허용).
  2. `./gradlew :app:installDebug` 뒤 앱을 열고 `adb shell am broadcast -a io.moodlab.sideseat.DEBUG --es cmd wake_test --es arg on` (또는 `--es cmd action --es arg ToggleDebugPanel`로 디버그 패널을 열고 "타비야 직접 불러 보기" 시작 키) → 맥 마이크에 "타비야, 와이퍼 어디 있어?"처럼 말하면 화면 토스트와 확인음, 끝낼 때는 `--es arg off`(또는 패널의 멈춤 키).
  3. 결과는 `adb logcat -s SideSeatMeasure | grep "wake test"`(`깨어남 a/b`, 나비야 규칙을 켰을 때 수치도 같이). 나비·아비·차비 규칙을 실제로 켜 보려면 앱 files에 `wake_config.txt`로 `allowWeakWakeWords=1`.
