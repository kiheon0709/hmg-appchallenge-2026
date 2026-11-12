# 00. 공모전 규칙과 Pleos 생태계

2026-11-12에 수집했어요. 원문은 `contest/`(공모전 사이트), `ecosystem/`(pleos.ai), `raw/product-*.md`(Playground 제품 안내)에 있어요.

## 1. 공모전에서 쓸 수 있는 것과 없는 것 (가장 중요)

근거는 공지 「공모전 개발 범위 및 기술 문의 안내」(`contest/notice-08_개발범위_기술문의.md`)와 운영사무국 메일 답변(11/11 19:15 정정본)이에요.

| 구분 | 내용 |
| --- | --- |
| ✅ 사용 가능 SDK | Vehicle, NaviHelper, TextToSpeech, SpeechToText, FusedLocation 5종. Playground 워크스페이스에 '준비 중(Coming Soon)'으로 보여도 문서 방법대로 에뮬레이터에서 개발·시험·출품 가능 |
| ❌ 제공 안 됨 | 개발 문서에 공개되지 않은 SDK, Pleos LLM SDK (문서 사이트에 LLM 레퍼런스 페이지는 있지만 공모전에선 못 씀) |
| ❌ 신청 불가 | Gleo Interface(App Schema) 검토·승인. 즉 Gleo AI 음성 명령 연동은 출품작에 넣을 수 없음 |
| ❌ 사용 불가 | 차량 마이크 음성을 외부 음성 인식 서비스로 보내는 방식. 음성 인식은 SpeechToText SDK만 |
| ⚠️ 문서 밖 기능 | 사용 불가. 필요하면 기획서에 "향후 계획"으로 적기 |
| ✅ 외부 네트워크 | 설명회에서 외부 API·퍼블릭 네트워크 사용 가능하다고 안내됨(10/28). 단, 마이크 음성의 외부 전송은 위 금지 |
| ℹ️ AAOS 표준 API | 문서 「API 호환성 및 가용성」은 SDK에 없는 기능을 AAOS `CarPropertyManager`/`VehiclePropertyIds`로 확인하라고 안내함. 공모전 허용 여부는 명시된 답이 없음 (기존 앱은 기어를 표준 Car API로 읽음) |

운영사무국은 구현 방법이나 API 동작 문의에는 더 이상 답하지 않는다고 공지했어요. 장비 대여·실차 측정·기술지원 창구도 없어요.

## 2. 심사

| 단계 | 일정 | 방식 |
| --- | --- | --- |
| 접수 마감 | 2026-11-15(일) 23:59 | 접수양식(기획서) 필수. 시연 영상·GitHub(소스+APK)은 선택, 내면 단계별 가산점 |
| 1차 결과 | 11-23(월) | 20팀 통과 |
| 2차 PT | 12-03~04 | 현대차 강남대로사옥 1층 UX스튜디오 서울. **Pleos Connect 에뮬레이터 구동 시연** (실차 불필요). 장비·인터넷 등은 본선 진출팀에만 안내 |
| 최종 발표 | 12-18 | 10팀 선정 |
| 전시·시상 | 2027-02 | 전시용 release APK 필수 제출. 추가 개발은 순위에 반영 안 됨 |

**1차 평가항목** (세부 배점 비공개, `contest/guide_judging-awards.md`)
- 차량특화성: 모바일 대비 차량 환경만의 가치, 주행·정차 맥락과 차량 기능의 결합
- 창의성: 독창성, 운전자·동승자 Pain Point와 해결 방식의 명확성
- 기술 활용성: Playground SDK·API를 앱 핵심 기능과 경험에 적절히 활용
- 사업성: 타깃 고객, 예상 MAU, Retention, 서비스 확대 계획
- 가산점: 시연 영상, GitHub 저장소(소스+APK)

**GitHub 제출** (`contest/notice-07_GitHub제출_추가안내.md`, `contest/guide_submission.md`)
- Collaborator 초대는 필요 없음. 마감까지 비공개 OK, 메일에 저장소 주소 + 최종 커밋 해시를 적음
- **11/16(월)까지 Public 전환, 11/23까지 유지.** 안 하면 가산점 없음. 어려우면 소스+APK 압축 다운로드 링크로 대체
- APK: `apk/` 폴더, 파일명 `팀명_앱이름_release.apk`, release 빌드. 용량 초과 시 GitHub Releases
- README 필수 항목: 앱 이름·한 줄 소개, 실행 방법, 테스트 환경·설정, **에뮬레이터 버전과 화면 규격**, APK 위치
- API 키 등 비밀값은 빼도 됨. 자체 서버 소스 제출은 필수 아님

**접수양식**: DOCX·PDF 모두 가능. 필수 항목과 순서만 지키면 글꼴·표·이미지 자유. 추가 자료(PPT·PDF·HTML) 첨부 가능, 분량 제한 없음. 5번(개발 계획)에 Pleos Connect 구현 방법과 SDK/API 사용 계획을 구체적으로.

## 3. Pleos 생태계 지도

Pleos = 현대차그룹 통합 소프트웨어 브랜드. 아래에서 위로 쌓인 구조예요 (`ecosystem/pleos.ai_technology.md`).

| 층 | 이름 | 우리와의 관계 |
| --- | --- | --- |
| 하드웨어 | **CODA** (중앙집중형 E&E 아키텍처, IPCU 중심) | 직접 접근 불가 |
| 차량 OS | **CODA OS** (VHAL로 HW/SW 분리, 표준 Vehicle API) | Vehicle SDK가 이 위의 API를 감싼 것 |
| 인포테인먼트 | **Pleos Connect** (AAOS 기반, Gleo AI·내비·앱마켓) | **우리 앱이 돌아가는 곳** |
| AI | **Gleo AI** (LLM 기반 차량 음성 에이전트, 자연어·복합 명령·좌석 인식) | 앱 연동(Gleo Interface)은 공모전 신청 불가 |
| 앱 유통 | **Pleos App Market** (차량에서 직접 앱 설치) | 실제 출시 경로. 공모전 심사는 입점 심사가 아님 |
| 개발 플랫폼 | **Pleos Playground** (SDK·API·에뮬레이터·가이드·워크스페이스) | 우리가 쓰는 개발 환경 |
| 클라우드 | **Pleos Fleet / Fleet API, Vehicle Data API** (원격 차량 데이터·제어, B2B) | 서버용 B2B API. 공모전 허용 5종에 없음 → 사용 불가로 봐야 함 |
| 모바일 | **Connect Mobile** (차량 원격제어 앱 개발자 콘솔) | 공모전 범위 밖 |
| 계정 | **Pleos Account** (현대·기아·제네시스 통합 ID) | SDK 없음 |
| 기타 | SDV OS API, Platform SDK, Universal Wallet, Maps 등 (`raw/product-*.md`) | 공개 문서 없거나 404 → 사용 불가 |

## 4. App Market 현황 (레퍼런스)

- 앱마켓 탑재 차종: 더 뉴 그랜저(2026-05), 디 올 뉴 아반떼(2026 하반기). 국내 총 18종 (2026-09 기준)
- 2026-09 추가 7종: 지도/내비 = 카카오내비, what3words, Make My Day - Route Planner / 음악·오디오 = 멜론, Radioline, 래디오오토(RaiDIO Auto), 픽클(Pickle)
- 초기 라인업(언론 보도): 네이버 지도·네이버 앱·웨일, 유튜브, 스포티파이, SBS 고릴라 등. 정확한 11종 목록은 공식 자료에서 확인 못 함
- 확대 예정 카테고리(42dot 발표): 게임, 차량 관리, 라이프스타일
- 관찰: 입점 앱이 대부분 **기존 모바일 서비스의 차량 이식**(내비·음악·영상)이에요. 차량 데이터(Vehicle SDK)를 핵심으로 쓰는 앱 사례는 공개 자료에서 찾지 못했어요. (추정이며, 차량 내 앱마켓 화면으로 확인하지는 않았음)

출처: [현대차그룹 뉴스룸 2026-09-09](https://www.hyundaimotorgroup.com/ko/news/42dot-pleos-connect-app-market-new-apps), [이콘밍글 2026-09-08](https://econmingle.com/car/hyundai-pleos-connect-app-market-melon-kakaonav/)
