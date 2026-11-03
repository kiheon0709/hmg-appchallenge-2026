# 음원 태깅 가이드

카탈로그 원본: `core/src/main/resources/catalog/tracks.json` (앱·테스트·프로토타입이 같이 씀)
현재 44곡은 **자리표시**(`license: "placeholder"`, 음원 없음). 제출 전 실제 음원으로 교체한다.

## 1. 음원 고르기

- 라이선스: CC0 또는 상업적 이용·재배포가 명시된 로열티프리만. 곡마다 출처 URL과 라이선스 이름을 `license`에 적는다 (예: `"Pixabay Content License — https://pixabay.com/music/..."`).
- 분량: 40곡 이상, 4개 사분면(아래 표)마다 최소 5곡. 테스트 `catalog json is valid and rich enough`가 검사한다.
- 형식: 128~192kbps MP3 또는 AAC, 곡당 2~5분. 파일은 `app/src/main/assets/music/<id>.mp3`, `source`에 `"music/<id>.mp3"`.
- APK 크기: 40곡 × 약 4MB ≈ 160MB. 크면 30초~1분 편집본으로 줄이거나 곡 수를 조정.

## 2. 무드 태깅 (energy, valence: -1.0 ~ +1.0)

| | valence − (어둡고 깊음) | valence + (밝고 따뜻함) |
|---|---|---|
| **energy +** (빠름·강함) | 긴장감 있는 일렉트로닉, 다크 록 — 터널·야간 고속 | 업템포 팝·시티팝·펑크 — 크루징·피날레·각성 |
| **energy −** (느림·잔잔) | 앰비언트·피아노·비 오는 날 어쿠스틱 — 비·폭우·심야 | 따뜻한 어쿠스틱·로파이·클래식 — 노을·정차·에코 |

기준값(맞춰서 상대 평가):
- energy: BPM 60 → -0.8, 90 → -0.2, 110 → +0.3, 130 이상 → +0.8. 드럼이 강하면 +0.1~0.2, 보컬 없이 패드만이면 -0.2.
- valence: 장조·밝은 음색 +, 단조·리버브 많은 음색 −. 가사가 있으면 가사 분위기도 반영.
- 두 명이 따로 태깅하고 차이가 0.3 넘는 곡만 다시 듣고 합의한다.

## 3. 확인

```bash
./gradlew :core:test        # 카탈로그 형식·범위·사분면 분포 검사
./gradlew :core:demoTrace   # 데모 시나리오에서 어떤 곡이 골리는지 확인
node design/check.mjs --shots   # 프로토타입 화면 다시 생성
```

`demoTrace` 출력에서 장면별 선곡이 어색하면 곡의 태그를 고치고 다시 돌린다. 테스트 `selected tracks fit scene mood`가 곡과 목표 무드의 거리가 0.6을 넘지 않는지 확인한다.
