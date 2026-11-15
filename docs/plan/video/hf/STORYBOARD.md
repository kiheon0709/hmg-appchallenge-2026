---
message: "초보 운전자를 위한 AI 운전 코치. 출발 전에는 미리 연습하고, 운전 중에는 옆에서 알려 주고, 주차 후에는 같이 돌아봐요"
length: 약 8분 54초 (클립 구간에 따라 달라짐)
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

`cuts.json`만 고치고 아래를 돌려요. 잘라 낸 파일(`assets/media/`), 클립 장면 파일(`compositions/clip-*.html`), 챕터 이름표(`compositions/chap-*.html`), `index.html`이 다시 만들어져요.

```bash
node scripts/build.mjs
```

- 초는 원본 파일의 처음부터이고 `../clips/cuts.md`의 구간표를 따라요. 자르는 곳은 타비의 말이 시작되기 0.3초 전, 말이 끝나고 0.5초 뒤로만 잡아요.
- `start`·`end` 대신 `parts`를 쓰면 여러 구간을 이어 붙여요. `speed`는 배속(그 구간은 소리가 꺼지고 "N배속" 표시가 떠요), `label`은 그 구간 동안 제목 옆에 뜨는 자막이에요. 원본에서 떨어져 있는 구간 사이에는 짧은 전환이 들어가요.
- `quiet`는 앱 소리가 없는 클립(배경 음악이 이어짐), `reveal`은 영상 칸이 나타나는 시각(초), `note`는 아래 설명 줄이에요.
- 오른쪽 "여기를 보세요" 글은 `watch`, 글이 뜨는 시각을 정하려면 `watch_at`(초 배열)을 넣어요.
- 챕터 이름표는 `groups`에서 만들어요. 클립의 `group`이 처음 나오는 곳 앞에 자동으로 들어가요.
- 대화 장면(`talk`)은 `enabled`를 `true`로 바꾸고 `../clips-user/talk.mp4`를 두면 리뷰 다음에 들어가요.
- 장면 순서와 그래픽 장면 길이는 `scripts/build.mjs`의 `scenes`에 있어요.
- 싱크: 원본 webm은 프레임 간격이 일정하지 않아요. 영상과 소리를 함께 고정 30fps 중간 파일(`assets/media/src_*.mp4`)로 바꾼 뒤 거기서 같은 구간으로 잘라요.

## 장면 (v4, 8분 54초, 클립은 v28 녹화본)

| # | 장면 | 길이 | 내용 |
| --- | --- | --- | --- |
| 1 | 제목 `title` | 7.5초 | 타비 TABI / 초보 운전자를 위한 AI 운전 코치 / 출발 전에는 미리 연습하고, 운전 중에는 옆에서 알려 주고, 주차 후에는 같이 돌아봐요. |
| 2 | 문제 `problem` | 31초 | 면허는 땄어요. 하지만 실제 도로는 처음이에요 → 대천 가는 길 이야기 → 이런 상황을 미리 연습해 볼 곳이 없었어요 → 그래서 타비를 만들었어요(출발 전 / 운전 중 / 주차 후 카드 세 장) |
| 3 | 대상 `who` | 12초 | 초보 운전자 / 빌린 차가 처음 / 장롱면허 |
| 4 | 세 가지 때 `moments` | 13초 | 출발 전, 운전 중, 주차 후에 함께해요 |
| 5 | 첫 실행 소개 (r1) | 클립 19초 | 소개 세 장, 차종 고르기, 홈. 화면이 바뀌지 않는 구간은 잘라 냈어요 |
| 6 | 훈련 소개 `train-intro` | 8.5초 | 이 차의 와이퍼, 전조등, 브레이크를 직접 움직여 연습해요 |
| 7 | "첫 빗길": 폭우, 앞유리 김서림, 앞차 급정거 (r2) | 4초 + 78초 | |
| 8 | "밤 고속도로": 터널 진입, 맞은편 차, 졸음 (r4) | 4초 + 44초 | |
| 9 | "길 위의 규칙": 회전교차로, 어린이 보호구역, 고속도로 합류, 일반 차로 요금소, 하이패스 차로 오진입 (r3) | 4초 + 131초 | |
| 10 | "멈춰야 할 때": 고속도로 고장, 의도치 않은 가속 (r6) | 4초 + 68초 | 한 클립으로 이어져요(앱에서 고장 설명 말이 다음 상황과 붙어 있어 사이를 자를 수 없음) |
| 11 | 가는 길 미리 연습 `route` | 12초 | |
| 12 | "운전 중과 주차 후": 운전 중 코치, 주차 후 리뷰 (r5) | 4초 + 18초 + 12초 | 코치는 상황 자막 세 개, 말 사이 대기는 잘라 냄 |
| 13 | (대화) | 클립, 선택 | 사용자가 찍은 클립이 있으면 |
| 14 | SDK `sdk` | 19초 | 와이퍼, 브레이크, 안전벨트 상태는 차만 알아요. 그래서 차량용 앱으로 만들었어요 |
| 15 | 실측 `numbers` | 14.5초 | `docs/STATUS.md`의 release 실측 표 |
| 16 | 로드맵 `roadmap` | 12초 | |
| 17 | 끝 `ending` | 10초 | 제목과 같은 두 줄 |

- 넣지 않은 것: 내리기 전(r7), 경고등(r6), 횡단보도·수막(r3), 짙은 안개(r4). `cuts.json`에 항목을 더하고 `build.mjs`의 `scenes`에 `...c("id")`를 더하면 들어가요.

## 검수

```bash
python3 scripts/qa.py renders/tabi_v4.mp4
```

클립 장면마다 시작·끝 1초의 소리 크기를 뽑고, 영상 칸이 5초 넘게 멈춰 있으면서 소리도 없는 곳을 찾아요.

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
npx --yes hyperframes@0.8.226 render --video-frame-format png -o renders/tabi_v4.mp4
```
