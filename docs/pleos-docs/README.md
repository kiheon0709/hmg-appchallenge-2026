# Pleos 공식 자료 모음 (2026-11-12 수집)

기획과 개발에 쓰는 Pleos 공식 자료 전체를 로컬에 옮겨 둔 폴더예요. 새 세션에서는 이 파일부터 읽고, 필요한 파일을 골라 보세요.

## 먼저 읽을 요약본

| 파일 | 내용 |
| --- | --- |
| `00-contest-and-ecosystem.md` | 공모전에서 쓸 수 있는 것과 없는 것, 심사·제출 규칙, Pleos 생태계 지도, App Market 입점 앱 |
| `01-planning-policy-design.md` | 앱 카테고리, 주행 규제, 화면 모델, 디자인 시스템, 앱 심사 기준 |
| `02-dev-environment-and-guides.md` | 개발 환경, 에뮬레이터·Virtual Console, 오디오 포커스·음악·내비 앱 가이드, 릴리스 노트, Gleo Interface |
| `03-sdk-usage-guides.md` | SDK 5종 사용법: 퍼미션, 초기화, 콜백, 에러 코드, 로케일 |
| `reference/00-INDEX-api-catalog.md` | **API 빠른 찾기.** 클래스별 함수 시그니처와 enum 값 이름 |

## 원문

> `raw/`와 `reference/`는 공식 문서를 그대로 옮긴 것이라 공개 저장소에 올리지 않아요(`.gitignore`). 이 맥의 작업 폴더에만 있어요. 다른 환경에서 필요하면 원문 URL(각 파일 첫 줄)이나 document.pleos.ai에서 확인하세요.

| 폴더 | 내용 |
| --- | --- |
| `raw/` | document.pleos.ai 가이드 140여 쪽. 파일명은 URL 경로의 `/`를 `__`로 바꾼 것. 첫 줄에 원문 URL |
| `reference/*.md` | SDK별 API Reference 전체(Dokka 1,848쪽). Vehicle.md가 가장 큼(약 580KB) → grep으로 찾기 |
| `contest/` | 공모전 사이트(appdevchallenge.auto-hmg.io) 공지·FAQ·심사·제출 안내 |
| `ecosystem/` | pleos.ai 소개 페이지 (Connect, App Market, Fleet, Technology 등) |

## 찾는 법

```bash
# 어떤 기능의 API가 있는지
grep -n "fun .*Ambient" docs/pleos-docs/reference/00-INDEX-api-catalog.md
# 상세 시그니처·파라미터·퍼미션
grep -n -A15 "^#### set-ambient-light-brightness-color" docs/pleos-docs/reference/Vehicle.md
# 가이드 본문 검색
grep -rl "ERROR_DRIVING_RESTRICTED" docs/pleos-docs/raw
```

## 수집 메모

- API Reference 본문은 페이지 안 숨은 영역(Shadow DOM)에 그려져서, 브라우저로 1,848쪽을 하나씩 띄워 읽었어요. 패키지 목차 페이지 1곳에 앞 페이지 내용이 잘못 들어간 것을 지웠어요.
- `api-reference/pleos-platform-sdk/*`는 공개 사이트에서 404(비공개)였어요. SDV OS API, Fleet API, Vehicle Data API는 개요만 받았고 공모전 허용 SDK가 아니에요.
- 문서는 계속 바뀌어요. 중요한 판단 전에는 원문 URL에서 다시 확인하세요.
