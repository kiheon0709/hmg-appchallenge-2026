# 사용자만 할 수 있는 일

- 소리(빗소리·TTS 음질·볼륨 균형)를 직접 들어 보고 판단하기
- 여러 작품 제출 가능 여부를 운영사무국에 확인하기
- (dev-integration, 11-13) 에뮬레이터 차량 마이크로 말하기 시험: 앱의 STT는 마이크를 열고 오디오 버퍼를 읽어 인식기에 넣지만(readCnt=69), 들어오는 소리가 무음이라 아무 말도 인식되지 않음. 맥이 에뮬레이터에 마이크를 주지 않은 것으로 보임. 절차: ① 시스템 설정 → 개인정보 보호 및 보안 → 마이크에서 `qemu-system-aarch64`(또는 Android Emulator·Android Studio)를 켜기 ② 에뮬레이터 창 `…` → Extended Controls → Microphone → "Virtual microphone uses host audio input" 켜기(또는 터미널 `adb emu avd hostmicon`) ③ 앱 "질문하기"를 누르고 "안개등 어떻게 켜?"라고 말하기 → `adb logcat -s SideSeatMeasure`에 `app heard kind=FREE source=mic '안개등 어떻게 켜?'`가 보이면 성공. 그전까지 시연은 화면 선택 버튼과 디버그 녹음 입력(`ask_pcm`, 화면에 "[녹음 입력]" 표시)으로 함.
