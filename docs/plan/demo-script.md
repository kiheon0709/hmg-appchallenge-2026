# 시연 대본

1분 소개 영상과 2차 PT 시연의 장면 순서. Virtual Console에서 누를 카드까지 적는다. 카드 이름은 통합 세션의 실측 뒤 실제 표기로 맞춘다(`docs/STATUS.md`).

## 촬영·시연 공통 준비

- Pleos Connect Emulator 2.1.0(`Pleos_Connect_v2.1`, cold boot), Virtual Console 연결(`adb forward tcp:8088 tcp:8088`).
- 화면 배치: 왼쪽에 에뮬레이터(앱), 오른쪽에 Virtual Console. **카드를 누르는 손과 앱의 반응이 한 화면에 보여야 한다.**
- 시작 상태: Gear P, 와이퍼 Off, 등화 Off, 공조 내기순환 On·에어컨 Off, 창문 닫힘.
- 앰비언트 조명 색은 Virtual Console 화면에 보인다(실측). 앱 화면 가장자리의 빛 띠와 Virtual Console의 조명 색이 같이 바뀌는 것을 한 화면에 담는다.
- 소리가 영상에 들어가게 시스템 오디오를 같이 녹음한다.
- 녹화: `adb exec-out screenrecord --output-format=h264 - > screen.h264` 또는 맥 화면 녹화.

## Virtual Console 카드 이름 (실측)

| 대본 표기 | Virtual Console 실제 위치 |
| --- | --- |
| Gear | SYSTEMS → GEAR (P/R/N/D) |
| Wiper Switch | SYSTEMS → FRONT WIPER (Off / Auto / Low / High) |
| Low Beam, High Beam | Emulation Controls → Exterior Lights Mode |
| Defroster | CLIMATE → Front |
| A/C, Recirculation | CLIMATE → A/C, 내기순환 아이콘 |
| Brake | BRAKE 패드 |
| Steering Angle | STEERING 슬라이더(deg). SDK에는 부호가 반대로 온다 |
| Window | BODY → WINDOWS |
| Ambient | SYSTEMS → AMBIENT. 앱이 색을 바꾸면 여기와 콘솔 화면 빛이 같이 바뀐다 |

- 주행 입력(브레이크·조향·기어)은 Virtual Console 브라우저 창이 열려 있어야 받는다.
- 자동 재현: 디버그 빌드에서 `adb shell am broadcast -a io.moodlab.sideseat.DEBUG --es cmd chapter --es arg C1` 같은 명령으로 장면을 바로 열 수 있다(`docs/STATUS.md`의 "디버그 빌드 도구").
- 차량 마이크로 말하기는 맥의 마이크 권한을 에뮬레이터에 줘야 한다(`needs-user.md`). 안 되면 화면의 선택 버튼으로 답하고, 그렇게 했다고 밝힌다.

## A. 1분 소개 영상 (60초)

| 시간 | 화면 | 자막·내레이션 | Virtual Console |
| --- | --- | --- | --- |
| 0~6초 | 검은 화면에 글자. 빗소리 | "부모님 차를 처음 빌린 날, 비가 왔어요. 앞유리가 뿌옇게 됐는데 버튼이 어디 있는지 몰랐어요." | – |
| 6~10초 | 앱 로고 "옆자리" | "처음 타는 차, 혼자 하는 운전. 옆자리에 선배가 앉아요." | – |
| 10~14초 | 홈 화면. "이 차는 처음이시네요" | "세워 둔 내 차가 시뮬레이터가 돼요." | Gear P |
| 14~24초 | 훈련 "첫 빗길". 폭우가 화면을 덮고 빛 띠가 빨강 | 선배 음성: "비가 쏟아져요!" → 와이퍼 카드를 누르자 화면 속 와이퍼가 비를 닦음 → 전조등 | Wiper Switch → High, Low Beam → On |
| 24~32초 | 터널을 나오며 화면이 뿌옇게 → 성에 제거를 누르자 아래부터 걷힘. 체크리스트가 채워지고 "3.8초 ★★★" | "진짜 스위치로, 손이 기억할 때까지." | Defroster → On, A/C → On, Recirculation → Fresh |
| 32~37초 | 앞차 급정거 → 브레이크 → "반응 0.6초" | "반응 시간까지 재요." | Brake → On |
| 37~40초 | 기어를 D로 → 훈련 화면이 닫히고 한 줄 화면으로 | "달리기 시작하면 화면은 비워요." | Gear → D |
| 40~48초 | 코치 화면. 와이퍼 켜짐 + 전조등 꺼짐 | 선배 음성: "연습했던 거예요. 비가 와요. 전조등을 켜 주세요." | Wiper → Low (Low Beam Off 상태) |
| 48~54초 | P단. 리뷰 화면과 음성 총평. "왜 외기로 바꿔야 해?"에 답함 | "도착하면 같이 돌아봐요." | Gear → P |
| 54~58초 | 금고 다이얼. 핸들 각도에 따라 다이얼이 돌고 열림 | "친구들과는 핸들로 금고 따기." | Steering Angle 조절 |
| 58~60초 | 로고와 한 줄 | "옆자리. Pleos Connect의 다섯 가지 SDK로 만들었어요." | – |

## B. 2차 PT 시연 (4~5분, 말하면서 직접 조작)

1. **문제 (40초)**: 이야기 한 장(부모님 차, 비, 김서림). 근거 숫자 하나. 한 문장 정의.
2. **훈련 시연 (90초)**: 가장 강한 장면. 가능하면 심사위원이 직접 카드를 누르게 한다.
   - Gear P → 홈 → "첫 빗길" 시작.
   - Wiper Switch를 High로 → 화면의 비가 닦인다. "이 카드가 실제 차에서는 와이퍼 레버예요."
   - 일부러 김서림에서 아무것도 하지 않는다 → 선배가 힌트를 말한다 → Defroster On → 걷힌다. (단계가 올라가는 설계를 보여 준다.)
   - 급정거 → Brake On → 반응 시간.
   - 결과 화면: 별, 반응 시간, 스위치 위치.
3. **주행 규제 준수 (15초)**: Gear D → 훈련 화면이 즉시 닫힌다.
4. **코치 시연 (50초)**:
   - Wiper Low + Low Beam Off → 20초 뒤 한 문장. (시연용 빠른 시간 설정을 쓰면 화면에 "시연 배속"이라고 표시한다.)
   - Low Beam On → 조용해진다. "같은 말은 반복하지 않아요."
   - 이름을 불러 질문("<이름>야, 앞유리가 뿌예") → 1초 안에 음성 답. 화면을 누르지 않는 것을 보여 준다. 지연 수치를 화면 구석에 보여 준다.
5. **리뷰 (30초)**: Gear P → 요약과 음성 총평 → 되묻기.
6. **미니게임 (20초)**: Steering Angle로 금고 다이얼. 가볍게 웃고 끝낸다.
7. **구조와 SDK (30초)**: 5종 SDK가 어디에 쓰였는지 한 장. 테스트 수, 실측 수치.
8. **사업성 (30초)**: 카셰어링 첫 탑승 3분 훈련 → 쿠폰. 보험. 확장.

## C. 캡처 목록 (`docs/screens/sim/`)

완료 조건의 증거. 가로와 세로를 각각 찍는다.

| 파일 | 장면 |
| --- | --- |
| `01-home-landscape.png`, `01-home-portrait.png` | 홈 |
| `02-training-rain-before.png`, `02-training-rain-wiped.png` | 폭우, 와이퍼로 닦인 뒤 |
| `03-training-fog-before.png`, `03-training-fog-cleared.png` | 김서림, 걷힌 뒤 |
| `04-training-sudden-stop.png` | 급정거 반응 시간 |
| `05-training-result.png` | 결과 |
| `06-gear-d-coach.png` | D단 전환 직후 코치 화면 |
| `07-coach-utterance.png` | 코치 문장(와이퍼 + 전조등 꺼짐) |
| `08-review.png` | 리뷰 |
| `09-safe-dial.png` | 금고 다이얼 |
| `10-portrait-training.png` | 세로 창의 훈련 화면 |
| `11-virtual-console.png` | Virtual Console과 앱을 나란히 |

## D. 시연에서 지킬 것

- 한 번의 동작보다 "반응이 없으면 다음 단계"를 보여 준다(힌트, 재시도).
- 시연 배속이나 가짜 입력을 쓰면 화면에 표시하고 말로도 밝힌다.
- 안 되는 것은 안 된다고 말한다(차속·기어는 공개 SDK에 없어서 화면 속 속도는 앱이 굴린다 등).
- "급발진"이라는 말을 쓰지 않는다.
