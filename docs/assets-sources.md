# 외부 소재 출처

앱에 넣은 외부 영상·사진의 출처와 라이선스. 새 소재를 넣을 때 여기에 한 줄씩 더해요.

## 훈련 장면 실사 배경 (`app/src/main/assets/scene/`, 2026-11-14 받음)

모두 [Pexels](https://www.pexels.com/license/)에서 로그인 없이 받았어요. Pexels 라이선스: 무료 사용, 상업 이용·수정 가능, 출처 표시 의무 없음. 금지: 수정 없이 그대로 재판매, 다른 스톡 사이트에 재배포, 식별되는 사람을 나쁘게 보이게 쓰기, 영상 속 사람·브랜드가 제품을 보증하는 것처럼 쓰기, 상표의 일부로 쓰기.

| 앱 파일 | 원본 | 작가 | 원본 규격 | 쓴 부분과 가공 |
| --- | --- | --- | --- | --- |
| `night_road.mp4`, `night_road.jpg` | [Dashcam Footage of a Wet Road](https://www.pexels.com/video/dashcam-footage-of-a-wet-road-13858526/) | ANTONIS KARAMPALIS | 1280×720 판, 50fps, 2:00 | 106.3초부터 12초. 30fps, 밝기 조금 올림(gamma 1.18), 끝 0.6초를 처음과 겹쳐 이음매 없이 반복, 소리 없음. H.264 CRF 27 |
| `tunnel.mp4`, `tunnel.jpg` | [Driving car in tunnel](https://www.pexels.com/video/driving-car-in-tunnel-28057678/) | Darina Evstafeva | 1280×720 판, 24fps, 1:04 | 15.5초부터 10초. 끝 0.6초를 처음과 겹쳐 반복, 소리 없음. H.264 CRF 31 |
| `parking.jpg` | [Parking Area in a Basement](https://www.pexels.com/photo/parking-area-in-a-basement-7231775/) | Karen Laårk Boshoff | 1920×1440 판 | 1440×1080으로 줄임. 기둥 표지의 작은 글자 줄(구역 이름)을 가림 |

- 고른 기준: 운전석(대시캠) 시점, 우측통행, 컬러, 가로 화면. 번호판·얼굴·상호가 읽히는 구간은 피했어요(야간 도로 영상의 102~105초는 옆 차 번호판이 가까워서 쓰지 않음).
- 받아서 확인만 하고 쓰지 않은 것(저장소에 없음): Altaf Shah의 야간 고속도로 3편(31842064, 31940381, 31720440 — 흑백이고 좌측통행 도로), 터널 영상 35987757(실내가 크게 나옴).
- 앞차 급정거 장면의 앞차 위치는 `night_road.mp4`에서 빨간 미등을 찾아 0.5초 간격으로 적은 값이에요(`ui/scene/Backdrop.kt`의 `LeadTrack`). 영상을 바꾸면 다시 재야 해요.
- 표시 의무가 없어 앱 화면에는 출처를 넣지 않았어요.

## 그 밖

- 제한속도·어린이 보호구역 자료: OpenStreetMap(ODbL). 앱 화면에 "© OpenStreetMap contributors" 표기(`ui/ReviewScreen.kt`의 `OSM_CREDIT`).
- 효과음(`assets/sfx/`): 코드로 합성(`tools/sfx/`).
