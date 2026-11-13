# 위치 연계 코치 데이터 (K11 어린이 보호구역 · K12 과속)

명세: `docs/plan/product-spec.md` 6.4. 앱 코드: core `Location.kt`, `DefaultDriveCoach`(K11·K12), vehicle `PleosVehicleGateway`·`SchoolZoneAssets`.

| 파일 | 내용 | 만드는 법 |
| --- | --- | --- |
| `app/src/main/assets/zones/school_zones.tsv` | 보호구역 좌표 2,820곳(수도권) | `convert_zones.py` |
| `app/src/main/assets/zones/road_limits.json` | 시연 지역 도로 제한속도 204개 도로 | `make_demo_route.py` |
| `tools/zones/demo_route_k11_k12.csv` | 시연 경로 453초(1초 간격) | `make_demo_route.py` |
| `tools/zones/replay_geo.sh` | 경로를 `adb emu geo fix`로 재생 | – |

## 보호구역 좌표: 출처와 한계

- **지금 들어간 것은 대체 데이터**다. OpenStreetMap의 초등학교(`amenity=school`, 이름에 "초등학교")와 유치원(`amenity=kindergarten`) 위치를 수도권 범위(37.0~38.0N, 126.5~127.6E)에서 받아(Overpass, 기준 2026-11-13T06:17Z) 반경 300m를 보호구역으로 근사했다. 보호구역은 주 출입문 기준 반경 300m 이내에서 지정한다(어린이·노인 및 장애인 보호구역의 지정 및 관리에 관한 규칙).
  - © OpenStreetMap contributors, ODbL 1.0. 앱 안에 출처 표시가 필요하다(정보·설정 화면 등).
  - 한계: 실제 지정 여부와 다를 수 있다. 어린이집·학원 보호구역은 빠져 있다.
- **원래 쓰려던 공공데이터포털 「전국어린이보호구역표준데이터」**(https://www.data.go.kr/data/15012891/standard.do, 수정 2026-09-22, 반기 갱신, 무료)는 파일 내려받기가 페이지 스크립트로만 열려 명령줄로 받을 수 없었다(로그인 필요 여부 미확인). 계정을 만들거나 로그인하지 않았다. 브라우저에서 CSV를 받으면 아래로 바꾼다.

```bash
python3 tools/zones/convert_zones.py --csv 전국어린이보호구역표준데이터.csv --out app/src/main/assets/zones/school_zones.tsv
```

## 제한속도

- 실차: NaviHelper `onCurrentRoadSpeedLimit`(0보다 클 때). 에뮬레이터는 늘 0(실측).
- 그 밖: OpenStreetMap `maxspeed`. 게이트웨이가 0.005도 칸(약 550×440m)마다 Overpass에 한 번 묻고 캐시한다. 보내는 것은 칸의 경계 좌표뿐. 실패하면 60초 뒤 다시, 판정은 안 함.
- 시연 지역(37.39~37.44N, 127.07~127.125E)은 `road_limits.json`을 앱에 넣어 두어 외부에 묻지 않는다. 시연 중 Overpass가 붐벼도(실제로 502를 받았다) 판정된다.

## 시연 경로

에뮬레이터 기본 위치(37.4127, 127.0947) → 대왕판교로 → 왕남로(왕남초등학교 보호구역, 36km/h) → 분당내곡로 북쪽 방향(OSM 80, 일부 100km/h) → 정지. 경로는 OSRM 공개 서버(router.project-osrm.org)로 받았다.

| 시각 | 일 |
| --- | --- |
| 173s | 보호구역 앞 감속 |
| 199s 무렵 | **K11** "어린이 보호구역이에요. 30 아래로 가요." |
| 337s | 분당내곡로 합류(지도 제한 80) |
| 355~386s | 100km/h. 360s 무렵 **K12** "제한속도가 80이에요. 조금 줄여요." |
| 450s | 정지 → 리뷰: "어린이 보호구역에서 최고 37km/h였어요…", 과속 1번(가장 길게 31초) |

core 테스트 `DemoRouteTest`가 이 경로를 코치에 넣어 위 결과를 확인한다. 재생:

```bash
tools/zones/replay_geo.sh
```

보호구역 직전부터 보려면 `tools/zones/replay_geo.sh tools/zones/demo_route_k11_k12.csv 160`.
