---
message: "타비는 처음 타는 차, 혼자 하는 운전의 옆자리에 앉는 운전 코치"
length: 약 5분 7초 (클립 구간에 따라 달라짐)
format: 1920x1080, 30fps, mp4
voice: 내레이션 없음. 클립 구간에는 앱의 실제 소리(타비 목소리, 효과음)만
---

# 타비 소개 영상 콘티 (11/15)

기준은 미티 소개 영상(`Meety/docs/presentation/video`)의 흐름이에요: 제목 → 문제 → 소개 → 실제 화면 → 구조 → 수치 → 끝.

- 글꼴: 주아체(앱과 같음, `assets/fonts/Jua.ttf`). 색: 옅은 회색 바탕 `#F2F4F8`, 글자 `#101216`, 강조 파랑 `#2563EB`, 타비 몸 `#2F6BF2`.
- 타비: 앱의 `ui/Tabi.kt`를 좌표와 수식 그대로 옮긴 `assets/tabi.js`. 상태 일곱 가지(idle, speaking, listening, happy, surprised, thinking, cheer)를 그대로 써요. 새로 그리지 않았어요.
- 소리: 그래픽 구간에는 음악(`bgm-a`, `bgm-b`, 미티 영상의 것)과 효과음, 클립 구간에는 앱의 실제 소리만(+18dB).
- 사실의 출처: `docs/plan/application-draft.md`, `docs/STATUS.md`.

## 클립 구간을 바꾸는 법

`cuts.json`의 `start`·`end`(원본 파일의 초)만 고치고 아래를 돌려요. 잘라 낸 파일(`assets/media/`), 클립 장면 파일(`compositions/clip-*.html`), `index.html`이 다시 만들어져요.

```bash
node scripts/build.mjs
```

- 클립은 타비의 첫 말부터 성공 화면까지 끊지 않고 넣어요. 오른쪽 "여기를 보세요" 글은 `watch`, 글이 뜨는 시각을 정하려면 `watch_at`(초 배열)을 넣어요.
- 대화 장면(`talk`)은 `enabled`를 `true`로 바꾸고 `../clips-user/talk.mp4`를 두면 리뷰 다음에 들어가요. 없으면 빠져요.
- 장면 순서와 그래픽 장면 길이는 `scripts/build.mjs`의 `scenes`에 있어요.

## 장면

| # | 장면 | 길이 | 화면 글 |
| --- | --- | --- | --- |
| 1 | 제목 `title` | 7.5초 | 타비 TABI / 처음 타는 차, 혼자 하는 운전. 옆자리에 앉는 운전 코치 |
| 2 | 문제 `problem` | 29초 | 면허는 땄어요. 그런데 진짜 길은 전부 처음이에요 → 첫 장거리 운전은 대천 가는 길이었어요. 처음 탄 고속도로였고, 비 오는 밤이었어요. 무서웠어요. → 이런 처음은 미리 겪어 볼 곳이 없어요(첫 비 오는 밤길, 첫 고속도로, 처음 타는 차) → 그래서 세워 둔 차에서, 이 차의 진짜 스위치로 미리 겪어 보게 했어요 |
| 3 | 대상 `who` | 12초 | 초보 운전자(면허를 막 딴 대학생) / 처음 타는 차(렌터카, 공유 차, 새 차) / 장롱면허 → 공통점: 이 차에서, 이 상황이 처음 |
| 4 | 세 가지 때 `moments` | 13초 | 출발 전(세워 둔 차에서 돌발 상황 연습) / 달릴 때(꼭 필요할 때만 한 문장 코치) / 도착하면(오늘 운전을 같이 돌아보기). 실제 앱 화면 세 장 |
| 5 | 훈련 소개 `train-intro` | 5.5초 | 화면이 아니라, 이 차의 진짜 스위치로 해요 |
| 6 | 상황 1. 폭우 | 클립 | 왼쪽 실제 화면, 오른쪽 타비와 "여기를 보세요" |
| 7 | 상황 2. 앞유리 김서림 | 클립 | 성에 제거를 누르면 김이 걷히는 장면까지 |
| 8 | 상황 3. 터널 진입 | 클립 | |
| 9 | 상황 4. 일반 차로 요금소 | 클립 | |
| 10 | 가는 길 미리 연습 `route` | 12초 | 내비에 목적지를 넣으면, 가는 길에 나올 구간을 출발 전에 연습해요. 경로 그림(요금소, 터널, 회전교차로)과 실제 홈 화면 |
| 11 | 주행 중 코치 | 클립 | 달릴 때 |
| 12 | 도착 후 리뷰 | 클립 | 도착하면 |
| 13 | (대화) | 클립, 선택 | 사용자가 찍은 클립이 있으면 |
| 14 | SDK `sdk` | 19초 | Vehicle / NaviHelper / TextToSpeech / SpeechToText / FusedLocation과 읽는 신호 → 휴대폰 앱은 와이퍼 레버를 읽을 수 없어요. 그래서 차 안의 앱이에요 |
| 15 | 실측 `numbers` | 14.5초 | 정차 훈련 CPU 평균 16.9%(기준 25% 미만), 3D 훈련 장면 30fps, 정차 훈련 메모리 240.6MB(기준 400MB 미만), 주행 중 코치 CPU 평균 0.12%(기준 5% 미만). `docs/STATUS.md`의 release 실측 표 |
| 16 | 로드맵 `roadmap` | 12초 | 지금: 훈련 5챕터 18상황 / 2차 발표 전: 차량 데이터를 아는 AI, 중계 서버 배포, 출발 전 브리핑 / 그다음: 실제 도로를 미리 달려 보기 |
| 17 | 끝 `ending` | 10초 | 타비 TABI / 처음인 길, 옆자리에서 같이. |

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
npx --yes hyperframes@0.8.226 render --video-frame-format png -o renders/tabi_v2.mp4
```
