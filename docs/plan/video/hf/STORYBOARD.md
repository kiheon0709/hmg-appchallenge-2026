---
message: "면허는 땄지만 도로는 처음인 초보 운전자의 옆자리에, 운전 코치 타비가 앉아요"
length: 약 8분 57초 (클립 구간에 따라 달라짐)
format: 1920x1080, 30fps, mp4
voice: 내레이션 없음. 클립 구간에는 앱의 실제 소리(타비 목소리, 효과음)만
---

# 타비 소개 영상 콘티 (11/15)

기준은 미티 소개 영상(`Meety/docs/presentation/video`)의 흐름이에요: 제목 → 문제 → 소개 → 실제 화면 → 구조 → 수치 → 끝.

- 글꼴: 주아체(앱과 같음, `assets/fonts/Jua.ttf`). 색: 옅은 회색 바탕 `#F2F4F8`, 글자 `#101216`, 강조 파랑 `#2563EB`, 타비 몸 `#2F6BF2`.
- 타비: 앱의 `ui/Tabi.kt`를 좌표와 수식 그대로 옮긴 `assets/tabi.js`. 상태 일곱 가지(idle, speaking, listening, happy, surprised, thinking, cheer)를 그대로 써요. 새로 그리지 않았어요.
- 소리: 그래픽 구간에는 음악(`bgm-a`, `bgm-b`, 미티 영상의 것)과 효과음, 클립 구간에는 앱의 실제 소리만(+23dB).
- 사실의 출처: `docs/plan/application-draft.md`, `docs/STATUS.md`.

## 클립 구간을 바꾸는 법

`cuts.json`의 `start`·`end`(원본 파일의 초)만 고치고 아래를 돌려요. 잘라 낸 파일(`assets/media/`), 클립 장면 파일(`compositions/clip-*.html`), `index.html`이 다시 만들어져요.

```bash
node scripts/build.mjs
```

- 클립은 타비의 첫 말부터 성공 뒤 설명 말이 끝날 때까지 끊지 않고 넣어요. 오른쪽 "여기를 보세요" 글은 `watch`, 글이 뜨는 시각을 정하려면 `watch_at`(초 배열)을 넣어요.
- 대화 장면(`talk`)은 `enabled`를 `true`로 바꾸고 `../clips-user/talk.mp4`를 두면 리뷰 다음에 들어가요. 없으면 빠져요.
- 장면 순서와 그래픽 장면 길이는 `scripts/build.mjs`의 `scenes`에 있어요.

## 장면 (v3, 8분 57초)

| # | 장면 | 길이 | 내용 |
| --- | --- | --- | --- |
| 1 | 제목 `title` | 7.5초 | 타비 TABI / 면허는 땄지만 도로는 처음인 초보 운전자의 옆자리에, 운전 코치 |
| 2 | 문제 `problem` | 29초 | 면허는 땄어요. 그런데 진짜 길은 전부 처음이에요 → 대천 가는 길 이야기 → 이런 처음은 미리 겪어 볼 곳이 없어요 → 그래서 세워 둔 차에서, 이 차의 진짜 스위치로 |
| 3 | 대상 `who` | 12초 | 초보 운전자(대학생, 사회초년생) / 빌린 차가 처음(부모님 차, 렌터카, 카셰어링) / 장롱면허 |
| 4 | 세 가지 때 `moments` | 13초 | 출발 전 / 달릴 때 / 도착하면 |
| 5 | 첫 실행 소개 (r1 3.2~44.3) | 클립 41초 | 소개 세 장 → 차종 고르기 → 홈. 앱 소리가 없는 구간이라 음악이 이어져요(`quiet`) |
| 6 | 훈련 소개 `train-intro` | 8.5초 | 화면이 아니라, 이 차의 진짜 스위치로 해요. 5챕터 18상황, 그중 15개는 실제 차량 신호로 판정 |
| 7 | 이름표 "첫 빗길" + 폭우, 앞유리 김서림, 앞차 급정거 (r2) | 4초 + 클립 60초 | |
| 8 | 이름표 "밤 고속도로" + 터널 진입, 맞은편 차, 졸음 (r4) | 4초 + 클립 44초 | |
| 9 | 이름표 "길 위의 규칙" + 회전교차로, 어린이 보호구역, 고속도로 합류, 일반 차로 요금소, 하이패스 차로 오진입 (r3) | 4초 + 클립 108초 | |
| 10 | 이름표 "멈춰야 할 때" + 고속도로 고장 (r6) | 4초 + 클립 36초 | 말이나 선택으로 답하는 상황 |
| 11 | 가는 길 미리 연습 `route` | 12초 | |
| 12 | 이름표 "달릴 때와 도착 후" + 주행 중 코치, 도착 후 리뷰 (r5) | 4초 + 클립 91초 | 코치의 말 세 번이 모두 들어가요 |
| 13 | (대화) | 클립, 선택 | 사용자가 찍은 클립이 있으면 |
| 14 | SDK `sdk` | 19초 | Vehicle(와이퍼, 전조등·상향등, 깜빡이, 브레이크, 조향각, 성에 제거·에어컨·내기순환, 창문, 안전벨트, 문, 사이드미러 열선, 주행 거리, 배터리 잔량) / NaviHelper / TextToSpeech / SpeechToText / FusedLocation |
| 15 | 실측 `numbers` | 14.5초 | `docs/STATUS.md`의 release 실측 표 |
| 16 | 로드맵 `roadmap` | 12초 | |
| 17 | 끝 `ending` | 10초 | 면허는 땄지만 도로는 처음인 초보 운전자의 옆자리에. |

- 한 상황은 BRIEFING 직전부터 다음 상황 BRIEFING 직전(타비의 설명 말이 끝날 때)까지 끊지 않고 넣어요. 같은 챕터의 클립은 원본에서 이어지는 구간이에요.
- 챕터 이름표는 `cuts.json`의 `groups`에서 만들어요. 클립의 `group`이 처음 나오는 곳 앞에 자동으로 들어가요.
- 싱크: 원본 webm은 프레임 간격이 일정하지 않아요. 영상과 소리를 함께 고정 30fps 중간 파일(`assets/media/src_*.mp4`)로 바꾼 뒤 거기서 정확한 시각으로 잘라요. 폭우 클립에서 빨간 테두리가 뜨는 프레임(3.30초)과 천둥이 시작하는 때(3.44초)가 0.14초 차이였어요.
- 넣지 않은 것: 내리기 전(r7), 경고등·급발진(r6 앞뒤), 횡단보도·수막(r3), 짙은 안개(r4). `cuts.json`에 항목을 더하고 `build.mjs`의 `scenes`에 `...c("id")`를 더하면 들어가요.

## 소리 파일

음악과 효과음은 저장소에 올리지 않았어요(Pixabay 라이선스 파일이라 파일 자체를 다시 배포하지 않으려고). 렌더하려면 미티 영상 프로젝트에서 복사해요.

```bash
cp ~/hkheon/Project/Meety/docs/presentation/video/assets/music/bgm-{a,b}.mp3 assets/music/
cp ~/hkheon/Project/Meety/docs/presentation/video/assets/sfx/*.mp3 assets/sfx/
```

- 음악: "Presentation - Presentation Music"(STAROSTIN, Pixabay). 효과음: Pixabay 효과음(`assets/sfx/CREDITS.md`).

## 만들기

```bash
node scripts/build.mjs                       # 클립 자르기 + index.html
npx --yes hyperframes@0.8.226 check --json   # 검사
npx --yes hyperframes@0.8.226 preview        # 미리 보기
npx --yes hyperframes@0.8.226 render --video-frame-format png -o renders/tabi_v3.mp4
```
