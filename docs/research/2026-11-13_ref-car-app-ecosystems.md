# 레퍼런스: 타사 차량 앱 생태계 (2026-11-13)

다른 자동차 회사와 플랫폼의 차량 내 앱 생태계에서 무엇이 인기 있는지 조사하고, Pleos 제약 안에서 차용할 만한 후보를 추린 문서예요. 차량 음성 에이전트와 차량 음악 도메인은 `2026-11-12_in-car-agent-domain.md`, `2026-11-12_in-car-music-domain.md`에 따로 있어서 여기서는 깊게 다루지 않았어요.

## 결론부터

- **앱별 다운로드·이용 순위를 공개한 생태계는 한 곳도 없어요.** 이용 수치가 실제로 나온 것은 중국 OEM의 연휴 출행 리포트(리오토·샤오미)와 일부 서드파티의 자사 발표뿐이에요. 나머지 "인기"의 근거는 제조사가 대표로 내세운 것, 설문, 커뮤니티 반응이에요.
- 생태계를 가로질러 반복되는 유형은 여덟 가지예요: ① 정차·충전 대기 엔터테인먼트 ② 주차 중 공간 모드(낮잠·캠핑·반려동물) ③ 떠나 있을 때의 안심(감시·알림) ④ 사용자 자동화(장면·루틴) ⑤ 차량 데이터 기록·관리 ⑥ 돈으로 돌아오는 점수 ⑦ 밖에 보여 주는 재미(조명 쇼·테마·캐릭터) ⑧ 가족·아이.
- 차주 반응이 가장 구체적인 것은 **폰 앱으로 대체할 수 없는 "차만 할 수 있는 기능"**(공조·조명·창문·시트를 묶은 모드, 자동화)이에요. 영상·게임 같은 이식형 콘텐츠는 어디에나 있지만 이용 근거가 약해요.
- Pleos에 차용할 후보 상위 5개: ① 장면 자동화(샤오미 超级任务·리오토 任务大师·메르세데스 Routines) ② 정차 휴식 모드(小憩模式·Camp Mode·Energizing) ③ 경고등 통역 겸 차량 일지(OBD 앱·마이클·TeslaFi) ④ 차 조작이 입력인 폰 컨트롤러 파티 게임(AirConsole·Ford Arcade) ⑤ 반려동물·동승자 대기 모드(Dog Mode·Pet Comfort). 자세한 내용은 6절에 있어요.

## 조사 방법과 한계

- 2026-11-13에 다섯 갈래(테슬라 / 유럽·미국 OEM / 중국 콕핏 / 플랫폼 / 차량 데이터 서드파티·한국 앱)로 나눠 웹 검색 약 105회, 페이지 열람 약 90건을 했어요. 로그인하거나 글을 쓴 곳은 없어요.
- 확인 수준 표기
  - **[원문]**: 페이지 본문을 열어 읽었어요. 다만 열람 도구가 본문을 요약해 돌려주는 방식이라, 기획서에 숫자를 옮기기 전에 원문과 한 번 더 대조해야 해요.
  - **[검색 발췌]**: 검색 결과 요약만 봤어요.
  - **수치 없음**: 순위·다운로드·이용 수치를 찾지 못했어요.
  - **미확인**: 이번 조사에서 출처를 확보하지 못했어요.
- 자사 발표 수치(Tessie, Optiwatt, ev.energy, DIMO, AirConsole CEO 발언 등)는 독립 검증이 안 됐어요.
- 열지 못한 주요 페이지: 테슬라 공식 매뉴얼(403), S&P Global 2025 블로그(403), J.D. Power 보도자료 일부(403), Harman Ignite Store 공식 페이지(404), ABRP 공식 사이트(본문 미노출).
- 조사하지 못한 항목: NIO 낮잠·캠핑·노래방·PanoCinema, 샤오펑 영화관·명상 모드, BYD 회전 스크린·V2L, 화웨이 차량 앱 수, 지커, 메르세데스 Antstream·Digital Extras 판매 현황, BMW Caring Car·Experience Modes, Audi functions on demand, 볼보 앱 목록, GM Super Cruise 중 앱, 루시드 앱스토어, 리비안 Google Cast, 몬스터게이지·Chargemap·모두의충전·카닥·오일나우·오피넷·Fuelly·Drivvo.

---

## 1. 테슬라

### 1.1 설문·순위 근거

| 자료 | 내용 | 확인 |
| --- | --- | --- |
| SlashGear 독자 설문 (미국 621명, 2022-07) | "내 차에도 있었으면 하는 테슬라 기능": Autopilot 27%, OTA 업데이트 22%, Movie Mode(스트리밍) 17%, 키카드 17%, Sentry Mode 16% | [원문] [링크](https://www.slashgear.com/928716/27-wish-their-current-car-had-this-unique-tesla-feature) |
| SlashGear 설문 (미국 611명, 2022-10) | "몰랐던 숨은 기능": Romance Mode 39.93%, Creep Mode 20.13%, Chill Mode 15.88%, Sentry Mode 4.91%. 인지도이지 사용률이 아니에요 | [원문] [링크](https://www.slashgear.com/1059856/40-of-people-surveyed-didnt-realize-teslas-have-this-hidden-feature-slashgear-survey) |
| Bloomberg 2023 오너 설문 | OTA 업데이트 만족도가 4.79로 기능 중 가장 높음 | [검색 발췌] [링크](https://brp-prod-bcc.bloomberg.com/graphics/2023-tesla-survey/model-3-satisfaction) |
| EVANNEX "2026년에 원하는 것" (2025-04) | 테슬라 임원 게시물의 답글 1만 2천여 개 정리. 음악과 연동되는 앰비언트 조명, 프로필에 요추 지지대 저장, Sentry 개선, 차량 내 Grok. 집계 방법 없음, 발행처가 액세서리 판매사 | [원문] [링크](https://evannex.com/blogs/news/what-tesla-drivers-want-most-in-2026-according-to-12-000-comments) |
| Electrek 2025 Holiday Update 기사 | 필자는 내비 개선을 가장 가치 있다고 평가, Photobooth는 한두 번 쓰고 말 것이라고 평가. 독자 최다 공감 댓글은 "폰 두고 내림 알림이 유용하다" | [원문] [링크](https://electrek.co/2025/12/05/tesla-2025-holiday-update-few-cool-features/) |

"가장 많이 쓰는 테슬라 기능" 사용률 순위는 찾지 못했어요.

### 1.2 내장 기능

| 이름 | 하는 일 | 왜 인기 있는지 (근거) | 쓰는 차량 데이터·기능 | 출처 / 확인 |
| --- | --- | --- | --- | --- |
| Sentry Mode·Dashcam viewer | 주차 중 카메라로 감시, 위협 시 전조등 점멸·알람·녹화, 앱으로 실시간 보기 | 설문에서 가장 널리 알려진 기능(모르는 사람 4.91%), "내 차에도" 16%. 파손범 영상이 반복 화제. 불만은 배터리 소모(250~300W, 하루 배터리 7~14%) | 외부·실내 카메라, 조명, 알람, 외부 스피커, USB, 속도·조향각 | [원문] [링크](https://notateslaapp.com/tesla-reference/1303/tesla-sentry-mode-what-it-is-how-to-use-it) |
| Dog Mode | 자리를 비운 동안 실내 온도 유지, 화면에 "주인이 곧 돌아와요"와 온도 표시. 배터리 20% 미만에서 꺼짐 | 수치 없음. 출시 전 오너들이 요청한 기능이고 "가장 호평받는 기능 중 하나"로 표현됨. 2024년 작동 실패 보도에 반응이 컸음. 2025년에 iPhone Live Activity로 실내 스냅샷·온도 표시 | 공조, 실내 온도, 실내 카메라, 배터리, 화면, 앱 알림 | [검색 발췌] [링크](https://www.teslarati.com/tesla-dog-mode-issues-owners-report) / Live Activity는 [원문] Electrek |
| Camp Mode | 차박용. 온도·송풍·조명·음악 유지, 12V·USB 전원과 화면을 켜 둠 | 수치 없음. 8시간에 배터리 약 10%(추운 날 약 15%)로 소모가 예측 가능하다는 점이 신뢰의 바탕 | 공조, 실내 조명, 전원, 화면, 배터리, 프로필(시트) | [원문] [링크](https://www.notateslaapp.com/news/830/tesla-camp-mode-a-comprehensive-guide) |
| Theater | 주차 중 Netflix·YouTube 등 스트리밍 | "내 차에도 있었으면" 17% | P단 상태, 통신, 오디오 | [원문] SlashGear 621명 설문 |
| Arcade | 주차 중 게임. Beach Buggy Racing 2는 실제 핸들·페달로 조작 | 수치 없음. 2026-01에 YouTube Playables 75종 이상 추가(터치 전용). 기사는 "대부분 운전자는 시간 때울 캐주얼 게임만 원한다"고 평가 | 조향 휠, 페달, 터치스크린, P단 | [원문] [링크](https://www.notateslaapp.com/news/2055/youtube-playables-on-telsa-75-new-games), [원문] [링크](https://evbite.com/teslatari-11-tesla-games-everything-you-need-to-know/) |
| Steam (중단) | Model S/X에서 PC 게임 실행 | 2024-05부터 신규 차량에서 빠짐. 이유는 미공개, 매체는 낮은 사용률을 추정. 고사양 게임보다 캐주얼로 무게가 옮겨 감 | 차량 컴퓨터 | [원문] [링크](https://driveteslacanada.ca/news/tesla-begins-removing-steam-support-in-new-vehicles) |
| Caraoke | 화면에 가사를 띄우는 노래방 (마이크 녹음 없음) | 수치 없음 | 오디오, 화면 | [원문] [링크](https://www.notateslaapp.com/news/700/the-top-features-in-any-tesla) |
| Light Show | 음악에 맞춰 외부 조명·창문 등을 움직이는 쇼 | 수치 없음. 사용자가 xLights로 커스텀 쇼를 만들고 공유 사이트·커뮤니티가 있음. 2025년에 예약 실행, 실내 조명 추가 | 외부·실내 조명, 창문, 스피커 | [검색 발췌] [링크](https://github.com/teslamotors/light-show) |
| Boombox | 외부 스피커로 소리 재생 | 수치 없음. 2022년 NHTSA 리콜로 주행 중 사용이 막힘 | 외부 스피커, 기어 상태 | [검색 발췌] [링크](https://driveteslacanada.ca/news/nhtsa-recalls-teslas-boombox-second-time) |
| Romance Mode | 화면에 벽난로 영상과 음악 | 39.93%가 몰랐던 기능 | 화면, 오디오, (공조) | [원문] SlashGear 611명 설문 |
| Santa Mode | 차량 시각화를 썰매로, 주변 차를 순록으로 | 수치 없음. 매년 연말 업데이트로 화제 | 주변 인식 시각화, 잠금음 | [원문] Not a Tesla App, Electrek |
| Emissions(방귀 모드), Mars, Sketchpad, TRAX | 좌석별 장난 소리 / 지도를 화성으로 / 그림판 / 음악 제작 | 수치 없음 | 좌석별 스피커, 지도, 터치스크린 | [검색 발췌] 테슬라 매뉴얼(직접 열람 403) |
| Car Wash Mode | 세차 전에 창문 닫기·센서 끄기를 한 번에 | 수치 없음. 편집자 선정 목록에 포함 | 창문, 와이퍼, 센서, 충전구 | [원문] Not a Tesla App |
| Joe Mode | 알림음 음량 줄이기 | 수치 없음. "자는 아이를 깨우지 않으려는 용도"는 일반 지식이고 미확인 | 경고음 음량 | [검색 발췌] [링크](https://notateslaapp.com/news/2736/what-is-teslas-joe-mode-what-it-does-how-to-enable-it-future-features) |
| Keep Climate | 하차 후 공조 유지. Dog·Camp Mode의 상위 메뉴 | 수치 없음 | 공조, 배터리 | [검색 발췌] [링크](https://www.slashgear.com/1174805/the-best-modes-in-tesla/) |
| Grok | xAI 대화형 비서. 성격 모드(튜터·스토리텔러·명상 등) | 수치 없음. 기대와 "차량 제어 절반은 옛 음성 시스템이 필요하다"는 비판이 함께 있음 | 마이크, 내비, 통신 | 내비 명령은 [원문] Electrek, 나머지 [검색 발췌] [링크](https://driveteslacanada.ca/news/tesla-finally-launches-grok-in-canada-with-2025-38-software-update/) |

### 1.3 서드파티

| 이름 | 하는 일 | 왜 인기 있는지 (근거) | 쓰는 차량 데이터 | 출처 / 확인 |
| --- | --- | --- | --- | --- |
| Tessie | 주행·충전 기록, 배터리 상태 추적, 자동화, 워치 제어 | 자사 발표 사용자 50만 명 이상. 월 $6.99부터. 같은 모델 오너들과 배터리 열화를 비교하는 기능이 반복 언급됨 | 위치, 배터리·충전, 공조, 잠금, Sentry 상태 | [원문] [링크](https://tessie.com/drivers), [원문] [링크](https://tessie.com/pricing) |
| TeslaFi | 주행·충전·주차를 전부 기록하는 웹 로거 | 사용자 수 없음. 홈페이지 기준 최근 24시간 주행 45,690건 기록. "데이터를 깊게 파는 사람용" | 위치, 배터리, 공조, 타이어 공기압, 소프트웨어 버전 | [원문] [링크](https://www.teslafi.com/) |
| TeslaMate | 직접 서버에 설치하는 오픈소스 로거 | 사용자 수 없음. GitHub 스타 약 9.3천. 구독료가 없고 데이터를 내 서버에 둠 | 위치, 배터리·충전, 온도 | [원문] [링크](https://github.com/teslamate-org/teslamate) |
| Stats for Tesla | 배터리 건강·효율·대기 전력 손실 추적 (iOS) | 다운로드 수치 없음. 비슷한 차량과 최대 주행거리 비교 | 배터리·충전, 위치, 문·창문 | [검색 발췌] [링크](https://apps.apple.com/us/app/stats-for-your-tesla/id1191100729) |
| S3XY Buttons / Commander | 차에 붙이는 무선 물리 버튼. 130개 이상 기능 중 지정 | 판매량 없음. 터치스크린 깊숙한 기능을 버튼 한 번으로 쓰려는 수요 | 차량 내부 통신, 글로브박스, 미러, 조명, 주행 모드 | [검색 발췌] [링크](https://www.enhauto.com/products/commander) |
| Teslascope | 차량 데이터 추적 | 자체 수치 없음. 무료 / 월 $3.99 / 월 $6.99 | 차량 데이터 전반 | [원문] [링크](https://teslascope.com/transition) |
| Sentry 영상 뷰어류 | USB 영상을 여러 카메라 동시 재생·배속·내보내기 | 수치 없음. 내장 뷰어보다 빠른 탐색과 공유 | 카메라 영상, 주행 데이터 | [검색 발췌] [링크](https://insideevs.com/news/352124/teslacam-sentry-reviewer-app/) |
| Tesla Fleet API 요금 | 서드파티용 공식 API의 종량 과금(2024-11 발표) | Teslascope는 월 매출의 7.5~120.5배라고 밝힘. 앱들이 차량당 과금으로 전환. 플랫폼 요금 정책 하나가 생태계 구조를 바꾼 사례 | — | [원문] [링크](https://teslascope.com/transition) |

**테슬라에서 반복되는 유형**: 떠나 있을 때의 안심(Sentry, Dog Mode), 주차·충전 대기 채우기(Theater, 캐주얼 게임), 차를 공간으로 쓰기(Camp Mode), 남에게 보여 주는 재미(Light Show, Santa Mode — 실사용 근거는 약함), 내 차 데이터를 숫자로 보기(서드파티, "같은 모델 다른 차와 비교").

---

## 2. 유럽·미국 완성차

### 2.1 메르세데스

| 이름 | 하는 일 | 왜 인기 있는지 (근거) | 쓰는 차량 데이터·기능 | 출처 / 확인 |
| --- | --- | --- | --- | --- |
| MBUX Sound Drive | 주행 입력에 따라 곡의 레이어가 실시간으로 바뀜 | 수치 없음. 매체 체험 평은 좋음. "공격적으로 몰지 않으면 밋밋하다"는 포럼 글. 전용으로 다시 만든 곡만 됨(시승차 16곡) | 가속, 제동, 조향, 회생제동, GPS | [원문] [링크](https://insideevs.com/news/703693/mercedes-sound-drive-ces/) |
| In-Car App Store | TikTok, Angry Birds, Webex, Zoom, Vivaldi, ZYNC(스트리밍) | 수치 없음 | 실내 카메라, 조수석 화면(운전자 시선 추적으로 밝기 조절) | [원문] [링크](https://autoconnectedcar.com/2023/02/tiktok-zoom-angry-birds-more-for-mb-e-class) |
| Meetings for Teams | 차 안 Teams 회의. 2025년 CLA부터 주행 중 운전자 카메라 송출 | 수치 없음 | 실내 카메라, 주행 상태 | [검색 발췌] [링크](https://fortune.com/2025/07/30/mercedes-benz-luxury-sedan-microsoft-teams-tesla-zoom-meetings/) |
| Routines | 조건과 동작을 묶는 자동화. 예: 외기온이 낮으면 시트 열선과 주황색 조명 | 수치 없음 | 외기·실내 온도, 시트 열선·통풍·마사지, 앰비언트 조명, 음악 | [원문] autoconnectedcar |
| Energizing Comfort / Coach | 공조·조명·음악·마사지를 묶은 웰니스 프로그램. Coach는 워치 데이터로 추천 | 수치 없음 | 공조, 시트 마사지, 조명, 오디오, 웨어러블 맥박 | [원문] autoconnectedcar, [검색 발췌] [링크](https://shop.mercedes-benz.com/en-gb/connect/pdp/ENERGIZING-Package/708) |

### 2.2 BMW

| 이름 | 하는 일 | 왜 인기 있는지 (근거) | 쓰는 차량 데이터·기능 | 출처 / 확인 |
| --- | --- | --- | --- | --- |
| AirConsole | 폰을 컨트롤러로 삼아 중앙 화면에서 여럿이 캐주얼 게임. 주차 중만 | 차량 내 이용 수치 없음. 보급 규모만 있음: 약 200만 차주 접근 가능(CEO 발언, 2025-08), BMW 50만 대·게임 20종(2026-09 기사). 인기 타이틀로 언급: Who Wants to Be a Millionaire, Overcooked, UNO Car Party | 중앙 화면, 주차 상태, QR 연결 | [원문] [링크](https://www.sovereignmagazine.com/article/swiss-startup-airconsole-beats-apple-and-google-to-luxury-car-gaming), [원문] [링크](https://www.startuphub.ai/ai-news/automotive/2026/volkswagen-bets-parked-play-will-sell-subscriptions.md) |
| ConnectedDrive Store | 출고 뒤 기능을 사거나 구독 | 무엇이 잘 팔리는지 수치 없음. **열선 시트 구독은 2023-09 철회**(이용률이 낮고 고객이 "두 번 낸다"고 느낌) | 주행 보조, 주차 보조 | [원문] [링크](https://www.carexpert.com.au/car-news/buyer-pushback-kills-controversial-bmw-subscription) |
| BMW Digital Premium | 지도 강화, 음성 비서, 음악·영상·게임 앱 구독 | 수치 없음 | — | [검색 발췌] [링크](https://www.bmw.co.uk/en/topics/owners/bmw-connecteddrive/bmw-connected-drive-digital-premium-overview.html) |
| Theatre Screen | 7시리즈 뒷좌석 31.3인치 화면, Fire TV 내장 | 수치 없음 | 뒷좌석 디스플레이 | [검색 발췌] [링크](https://carmagazine.co.uk/car-news/tech/bmw-theatre-screen) |
| BMW 텔레메트리 (2025-09) | 커넥티드 BMW 1천만 대 분석 | 운전자가 내장 내비로 돌아오고, CarPlay는 메시징 같은 특정 용도로만 연결. 비율 수치 없음 | — | [원문] [링크](https://jalopnik.com/1964587/apple-carplay-bmw-driver-behavior) |

### 2.3 아우디·폭스바겐·포르쉐

| 이름 | 하는 일 | 왜 인기 있는지 (근거) | 쓰는 차량 데이터·기능 | 출처 / 확인 |
| --- | --- | --- | --- | --- |
| 그룹 앱스토어 (Cariad, Harman Ignite 기반) | Spotify, TikTok, PlugShare, Vivaldi, Yelp, 캐주얼 게임 | 수치 없음. 2023년 출시 때 "20여 개" | — | [원문] [링크](https://techcrunch.com/2023/03/01/vw-software-unit-cariad-launches-app-store-to-bring-spotify-tiktok-and-hundreds-more-to-vehicles) |
| VW AirConsole (미국 2027년형 6개 차종) | 게임 20종. 연 149달러 In-Vehicle Premium에 포함, 3개월 무료 | 수치 없음. 게임이 구독을 끌어오는지 데이터 없음 | **일부 게임이 앰비언트 조명과 연동** | [원문] [링크](https://thenextweb.com/news/volkswagen-airconsole-in-car-gaming-2027-models) |
| Holoride | 차의 움직임에 맞춰 VR 화면이 움직임 | **2024년 봄 파산 신청.** 비싸고 쓸모가 제한적이었다는 평 | 차량의 선회·위치 | [원문] [링크](https://lowpass.cc/p/holoride-car-vr-insolvency-bankruptcy) |

### 2.4 볼보·폴스타, 르노, GM, 포드

| 이름 | 생태계 | 하는 일 | 왜 인기 있는지 (근거) | 쓰는 차량 데이터·기능 | 출처 / 확인 |
| --- | --- | --- | --- | --- | --- |
| Vivaldi 브라우저 | 폴스타 외 | AAOS 첫 정식 브라우저. 전용 앱이 없는 영상 서비스의 통로 | 수치 없음. 르노·포드·메르세데스·VW로 확산 | 주차 상태 | [검색 발췌] [링크](https://www.polestar.com/global/news/the-vivaldi-web-browser/) |
| Performance app | 폴스타 | 가감속·코너링 g값 표시, 가속 시간 측정 | 수치 없음 | 가속도, 속도 | [검색 발췌] [링크](https://www.polestar.com/us/polestar-2-2023/interior/software-apps/in-car-apps) |
| Range Assistant, Journey Log | 폴스타 | 주행거리 보조, 주행 기록 | 수치 없음 | 배터리·전비, 위치 | [검색 발췌] 위와 같음 |
| OpenR Link | 르노 | 구글 플레이 앱 "100개 이상"(영국 페이지). Waze, EasyPark, Prime Video, Vivaldi | 순위 수치 없음 | — | [원문] [링크](https://www.renault.co.uk/renault-connect/partner-apps.html) |
| SongPop for Renault | 르노 | 음악 퀴즈 게임 | 수치 없음 | — | [원문] 위와 같음 |
| take a break! | 르노 | 운전자 피로를 보고 휴식 제안 | 수치 없음 | 운전자 인식 시스템 | [원문] 위와 같음 |
| Dashcam | 르노 | 차량 카메라로 주행 녹화 | 수치 없음 | 차량 카메라 | [원문] 위와 같음 |
| CarPlay·Android Auto 제거 | GM | 내장 AAOS와 네이티브 앱으로 대체 | "CarPlay 없으면 안 산다"는 반발 보도. 정식 설문 수치 없음. 앱마다 따로 로그인해야 하는 약점 | — | [원문] [링크](https://insideevs.com/news/776704/gm-apple-carplay-android-auto-remove-all-cars/) |
| GM 앱 200개 추가 (2026-06) | GM | 운전석은 주차 중 스트리밍·게임, 동승석·뒷좌석은 주행 중에도 영상 | 수치 없음 | 주차 상태, 좌석별 화면 | [원문] [링크](https://www.androidauthority.com/gm-cars-200-android-automotive-apps-3681314/) |
| Arcade Sports Collection | 포드 | 셔플보드 같은 레트로 게임. QR로 폰을 컨트롤러로 써서 4명까지 | 다운로드 수치 없음. 포드 자체 조사: "고객이 하루 평균 5~20분을 차에서 기다린다" | 주차 상태, 중앙 화면 | [원문] [링크](https://www.fromtheroad.ford.com/us/en/articles/2025/ford-exclusive-gaming-app-connects-generations-in-the-car.html) |

### 2.5 리비안, 루시드, 스텔란티스

| 이름 | 생태계 | 하는 일 | 왜 인기 있는지 (근거) | 쓰는 차량 데이터·기능 | 출처 / 확인 |
| --- | --- | --- | --- | --- | --- |
| Pet Comfort | 리비안 | 반려동물용 온도 유지. 화면에 캐릭터와 "반려동물은 안전해요" 문구·실내 온도 | 수치 없음. 리비안이 **"고객 요청이 가장 많았던 기능 중 하나"**라고 밝힘. 폰으로 실내 온도를 확인하며 볼일을 본다는 차주 후기(공식 스토리) | 공조, 화면, 폰 앱 알림 | [원문] [링크](https://rivian.com/stories/software-update-pet-comfort), [원문] [링크](https://rivian.com/stories/owners-words-rivian-software) |
| Gear Guard | 리비안 | 주차 중 카메라 감시. 화면에 만화 캐릭터 | 수치 없음. 차주들이 캐릭터에 "Gary"라는 별명을 붙임. "도둑을 막기엔 너무 귀엽다"는 회의론은 소수. 감지 누락 불만도 있음 | 외부 카메라 | [원문] [링크](https://insideevs.com/news/539382/rivian-gear-guard-animated-character/), [원문] [링크](https://www.rivianforums.com/forum/threads/gear-guard-animation-too-cartoonish.28236/latest) |
| Camp Mode | 리비안 | 캠핑장에서 차를 수평으로 맞추고 전력을 캠핑용으로 조정 | 수치 없음. 트럭 텐트에서 60박 넘게 잔 차주가 "한 번 눌러 수평 맞추기"를 가장 좋아한다고 함 | 에어 서스펜션, 전력 관리 | [원문] rivian.com owners-words |
| 핼러윈 모드 (Car Costumes) | 리비안 | 나이트 라이더 등 테마. 계기판에서 보행자가 좀비로 보임. 주차 중만 | 수치 없음. "어른과 아이 모두 좋아한다"는 후기(공식). 매년 이어짐 | 외부 라이트바, 스피커, 화면, 잠금음 | [원문] [링크](https://rivian.com/stories/halloween-software-2024) |
| Kneel 모드, 운전자 프리셋 | 리비안 | 주차하면 차고를 낮춤 / 폰 키로 운전자를 알아보고 시트·공조 맞춤 | 수치 없음 | 서스펜션, 시트, 공조 | [원문] [링크](https://digitaltrends.com/cars/5-rivian-features-that-dont-get-enough-attention) |
| Creature Comfort Mode | 루시드 | 반려동물용 온도 유지와 화면 문구. 창문·페달 잠금 | 수치 없음. 차주들이 캠핑용으로 대신 쓰는데 화면을 어둡게 할 수 없다는 불만 | 공조, 화면, 폰 앱, 창문 잠금 | [원문] [링크](https://lucidmotors.com/stories/creature-comfort-mode) |
| Jeep Adventure Guides / Trails Offroad | 스텔란티스 | 오프로드 트레일 지도를 차에 내려받아 통신 없이 따라감 | 수치 없음. 기본 62개, 39.99달러로 3,000개 이상 | 위치, 오프라인 저장 | [원문] [링크](https://www.popsci.com/?p=551325) |

### 2.6 구독·이용률 조사

| 조사 | 핵심 수치 | 출처 / 확인 |
| --- | --- | --- |
| S&P Global Mobility 2025 | 커넥티드 서비스에 돈을 낼 의향이 2024년 86% → 2025년 68%. 유료 구독 중인 비율: 미국 35%, 한국 30%, 독일 25%. 미구독 이유는 비용·가치, 개인정보, 구독 피로 | [원문] [링크](https://www.theautopian.com/consumers-hate-paying-a-subscription-to-use-features-their-car-already-has-and-the-data-proves-it/) |
| S&P Global Mobility 2023 | 무료 서비스 대가로 데이터 공유 의향 약 3/4. 28%는 서비스 존재를 모르거나 안내를 못 받음 | [원문] [링크](https://www.wardsauto.com/news/archive-auto-car-shoppers-subscriptions-sp-global-mobility-infotainment-navigation-safety/688771/) |
| Escalent (2020, 미국·유럽 약 2,000명) | **80%가 OEM 앱을 쓰지 않음**(56%는 존재를 모름, 27%는 가치를 못 느낌). 중요하다고 꼽은 유형: 보안·도난 방지 71%, 내비·교통 61%, 차량 상태·정비 56%/50% | [원문] [링크](https://escalent.co/news/escalent-identifies-consumer-priorities-for-in-vehicle-apps/) |
| J.D. Power (2차 인용) | 내장 시스템으로 오디오를 듣는 차주가 2020년 70% → 56% | [검색 발췌] [링크](https://www.imore.com/apple-carplay/people-still-hate-their-cars-infotainment-system-but-love-carplay) |
| J.D. Power 2025 OEM 앱 리포트 | 가장 원하는 일상 기능은 차고 문 연동인데 대부분의 앱에 없음 (제조사 폰 앱 조사) | [검색 발췌] [링크](https://www.jdpower.com/business/press-releases/2025-us-oem-ice-app-report) |

차량 내 앱스토어 이용률을 직접 잰 최근 조사는 찾지 못했어요.

---

## 3. 중국 스마트 콕핏

### 3.1 이용 수치가 확인된 것

OEM이 연휴 뒤에 내는 출행 리포트(IT之家 전재)가 이번 조사에서 유일하게 기능별 이용 횟수가 나온 자료예요.

| 기능 | 생태계 | 수치 | 기간 | 출처 / 확인 |
| --- | --- | --- | --- | --- |
| 理想同学 음성 호출 | 리오토 | 6,026.3만 회 (출행 차량 155.9만 대) | 2026 노동절 5일 | [원문] [링크](https://www.ithome.com/0/947/756.htm) |
| 조수석·뒷좌석 엔터테인먼트 스크린 | 리오토 | 291.3만 시간 | 같은 기간 | [원문] 같은 URL |
| 차량 냉장고 여닫기 | 리오토 | 372.6만 회 | 같은 기간 | [원문] 같은 URL |
| "小同桌"(역할 대화 기능. 아이 전용이라는 근거는 못 찾음) | 리오토 | 162.5만 회 | 같은 기간 | [원문] 같은 URL |
| 무마이크 노래방(无麦K歌) | 리오토 | 25.2만 회 | 같은 기간 | [원문] 같은 URL |
| 小爱同学 호출 | 샤오미 | 1.47억 회 | 2026 춘절 | [원문] [링크](https://www.ithome.com/0/923/617.htm) |
| 무마이크 노래방 | 샤오미 | 39만 회 | 2026 춘절 | [원문] 같은 URL |
| 超级任务(자동화) 신규 생성 | 샤오미 | 15.9만 개 | 2026 춘절 | [원문] 같은 URL |
| 超级任务 누적 생성 | 샤오미 | 28.6만 개 | 2025 국경절 | [원문] [링크](https://www.ithome.com/0/888/817.htm) |
| 小爱同学 최다 장면 | 샤오미 | 차량 제어, 지도 내비, 음악 재생 (8,237.5만 회) | 2025 국경절 | [원문] 같은 URL |
| NOMI 누적 대화 | NIO | 2.09억 회. 최다 요청은 음악, 내비, 전화 | 2021-05 기준 | [원문] [링크](https://cnevpost.com/2021/05/17/nio-says-users-have-communicated-with-nomi-virtual-assistant-over-200-million-times) |

두 리포트 모두 낮잠·캠핑·반려동물 모드의 이용 수치는 없어요.

### 3.2 설문

- **J.D. Power 2025 중국 TXI** (38,752명) [원문] [보도자료 PDF](https://china.jdpower.com/sites/china/files/file/2025-07/2025%20China%20TXI%20CN_FV_0.pdf)
  - 스마트 콕핏 불만 중 "이해하기 어렵다/쓰기 어렵다"가 36%로 올랐어요.
  - 안전 기능(하차 보조, 운전자 모니터링, 능동 알림)이 "높은 사용 빈도와 높은 재구매 의향"을 보였어요.
  - 총괄 코멘트: 콕핏이 동질화됐고, 사용자는 단순 음성 대화·차량 제어에 만족하지 않으며 "킬러급 버티컬 장면 앱"을 기다린다고 했어요.
  - 기능별 이용률 표는 유료 본편에만 있어요.
- 懂车帝·汽车之家의 기능 사용 빈도 순위는 찾지 못했어요. 수치 없음.

### 3.3 브랜드별 기능

| 이름 | 생태계 | 하는 일 | 왜 인기 있는지 (근거) | 쓰는 차량 데이터·기능 | 출처 / 확인 |
| --- | --- | --- | --- | --- | --- |
| 小米超级任务 | 샤오미 | 조건·상태·실행을 편집하는 자동화. 능력 블록 200개 이상, 공유 가능 | 누적 28.6만 개, 춘절 신규 15.9만 개. 커뮤니티 인기 장면: 물리 버튼에 묶기, 2열 안전벨트 알림음 끄기, 비상등 점멸 | 차량 제어 전반 | [원문] [링크](https://www.ithome.com/0/884/123.htm) |
| 任务大师 | 리오토 | 트리거→동작 자동화, 공유 코드로 교환 | 수치 없음. 사용자 글의 장면 예시: 좌석에 앉으면 환영 인사 / 배터리 80·60·40·20%에 음성 보고 / 도착 10분 전 알림 / "낮잠 잘래" 한마디로 전조등 끄기·시트 눕힘 / "환기할래"로 창문 10% 열기 / 낮에는 무드등 끄고 밤에 켜기 | 착좌, 배터리, 내비, 시트, 창문, 조명 | [원문] [링크](https://www.xchuxing.com/article/72915) |
| 小憩模式 (낮잠) | 샤오미 | 빠른 알람, 시트 눕힘·복귀·기억, 폰 방해 금지·워치 진동 연동 | 수치 없음 | 시트, 알람, 폰·워치 | [원문] [링크](https://www.ithome.com/0/773/654.htm) |
| 露营模式 (캠핑) | 샤오미 | 수면 환경을 켜고 귀가용 배터리를 남김 | 수치 없음 | 배터리, 공조 | [원문] ithome 884123 |
| 宠物模式 (반려동물) | 샤오미 | 하차 후 온도 설정, 화면 문구, 도어 오조작 방지 | 수치 없음 | 공조, 화면, 도어 | [원문] ithome 884123 |
| 宠物模式 | 리오토 | 공조 유지, 선셰이드 닫힘, 창문·도어 잠금, 실내 카메라 원격 보기, 위험 시 앱 푸시·전화, 배터리 10% 미만이면 종료하고 창문 열기 | 수치 없음 | 공조, 선셰이드, 창문, 잠금, 카메라, 배터리, 앱 | [원문] [링크](https://www.ithome.com/0/919/045.htm) |
| Mi Home 연동 | 샤오미 | 차에서 집 조명·커튼·카메라 제어 | 수치 없음 | — | [원문] ithome 884123 |
| 无麦K歌 | BYD | 천장 마이크로 외부 마이크 없이 노래. 좌석별 마이크 선택 | 수치 없음. 전 브랜드 OTA(2025-08) | 천장 마이크, 음성 비서 | [원문] [링크](https://www.ithome.com/0/878/371.htm) |
| 앱 선행자 계획 | 화웨이 | 첫 계약 앱: 爱奇艺, 宝宝巴士(유아), bilibili, 唱吧(노래방), 高德, 텐센트뮤직, 喜马拉雅 | 차량 앱 수 수치 없음 | — | [원문] [링크](https://www.autohome.com.cn/news/202504/1305066.html) |
| 睡眠空间 | 샤오펑 | 시트 눕힘, 창문·무드등 끄기, 내기 순환, 헤드레스트 백색소음. 수면 세트 판매 | 수치 없음 | 시트, 창문, 조명, 공조, 스피커 | [검색 발췌] [링크](https://www.ithome.com/0/627/448.htm) |
| 功能地图 (기능 지도) | 리프모터 | 기능을 써 보면 배지와 포인트를 주고, 기능 인기 순위를 차 안에서 보여 줌 | 수치 없음. 기능 발견 문제를 게임화로 푼 사례 | 기능 사용 이력 | [원문] [링크](https://www.ithome.com/0/873/502.htm) |
| 离车供电模式, 休憩模式 | 리프모터 | 하차해도 전원 유지(20% 미만 종료) / 휴식 모드 | 수치 없음 | 전원, 배터리 | [원문] 같은 URL |
| NOMI | NIO | 표정 있는 로봇 비서 | 누적 2.09억 회 대화(2021). 탑재율 수치 없음 | 마이크, 실내 카메라, 내비 | [검색 발췌] [링크](https://us.nio.com/blog/nomi-achieves-over-200-million-human-interactions) |

**중국에서 반복되는 유형**: 음성 비서가 압도적 1위 입구(주 용도는 차량 제어·내비·음악), "주차 중 공간" 모드(낮잠·캠핑·반려동물)가 모든 브랜드 표준, 무마이크 노래방, 공유 코드로 퍼지는 사용자 자동화(인기 장면은 알림음 끄기·조명 자동화 같은 작은 불편 해소), 그리고 "쓰기 어렵다"가 최대 불만이에요.

---

## 4. 플랫폼

### 4.1 Android Automotive OS (구글 빌트인)

- **허용 카테고리** [원문] [developer.android.com/training/cars](https://developer.android.com/training/cars): 미디어(오디오), 내비게이션, POI(주차·충전·주유), IoT(차고 문·조명·보안), 날씨는 주행·주차 모두 가능. 영상은 주로 주차 중(주행 중은 오디오만), 게임과 브라우저는 주차 중만. 메시징·통화는 Android Auto 전용이에요.
- **규모** [원문] 구글 블로그: 구글 빌트인 16개 브랜드 100개 이상 모델(2026-05), 차량용 Play 스토어 앱 300개 이상(2025-05).
- **2023~2026 확대 흐름**: 주차 중 영상(YouTube, Prime Video, Max 등) → 게임 베타(Angry Birds 2, Candy Crush Soda Saga, Beach Buggy Racing 2) → 위젯(차고 문 원탭, 날씨) → Gemini(DoorDash 주문, 경고등 등 차량 질문에 응답), 회의 앱(Zoom) 순이에요. [원문] [2025 블로그](https://android-developers.googleblog.com/2025/05/android-for-cars-google-io-2025.html), [2026 블로그](https://blog.google/products-and-platforms/platforms/android/android-in-cars-updates)
- **대표 앱**: YouTube, Prime Video, Vivaldi, The Weather Channel, Spotify, Waze, Zoom/Webex. 앱별 다운로드 수치는 없어요.

### 4.2 Android Auto

- 호환 차량 2억 5천만 대 이상(2026-05) [원문] 구글 블로그.
- 추천 목록에 반복 등장하는 앱 [검색 발췌]: Google Maps, Waze, Spotify, YouTube Music, Pocket Casts, WhatsApp. 순위 수치는 없어요.
- 주차 중 게임: GameSnacks 미니게임(2021~), 폰에 설치된 게임 실행(베타).
- 새 카테고리: Uber Driver(기사용), 주차 중 영상(2026), 위젯(2026).

### 4.3 Apple CarPlay

- **허용 카테고리** [원문] [developer.apple.com/carplay](https://developer.apple.com/carplay/): 오디오, 영상(주차 중), 메시징·VoIP, 내비, EV 충전, 주유, 주차, 공공 안전, 간편 음식 주문, 음성 기반 대화형, 주행 작업.
- **iOS 26 이후** [원문] [MacRumors](https://www.macrumors.com/2025/07/11/ios-26-adds-these-10-features-to-carplay): 위젯·Live Activities, 주차 중 AirPlay 영상, 음성 대화형 앱(챗봇, 음성이 주 인터랙션이어야 하고 차량 제어 금지), CarPlay Ultra(현대·기아·제네시스도 지원 약속).
- **목록에 반복 등장하는 앱** [원문] 제휴 블로그라 신뢰도는 중간 이하: 내비(Apple Maps, Google Maps, Waze), 음악(Spotify, Apple Music), 팟캐스트(Overcast, Pocket Casts, Audible), 메시징(WhatsApp), EV 충전(PlugShare, ChargePoint), 주유(GasBuddy), 주차(SpotHero, Parkopedia), 날씨(Weather on the Way — 경로 전체 날씨), 음식 주문(Domino's, Dunkin'), ChatGPT.
- **선호도** [원문] [9to5Mac](https://9to5mac.com/2024/06/20/carplay-must-have-survey-results/): McKinsey(2024) 조사에서 스마트폰 연동이 없으면 구매하지 않겠다는 응답이 글로벌 EV 구매자 30%, 내연기관 구매자 35%.

### 4.4 그 밖의 마켓

| 이름 | 무엇 | 규모·근거 | 대표 앱 | 출처 / 확인 |
| --- | --- | --- | --- | --- |
| Appning by Forvia (구 Faurecia Aptoide) | AAOS 기반 화이트라벨 앱 마켓 | 40개 이상 브랜드, 300만 대 이상, 앱 약 300개. 다운로드 수치 없음 | Audible, TikTok, YouTube, Spotify, Booking.com, Zoom, Teams, **Melon**, INRIX 주차 | [원문] [링크](https://appning.com/) |
| Harman Ignite Store | OEM용 화이트라벨 스토어 (VW 그룹, Tata) | "70개 이상"(2022~2024). 현재 수치 없음 | YouTube, Spotify, Yelp, PlugShare, ChargePoint, Beach Buggy Racing | [검색 발췌] [링크](https://www.autocarpro.in/News/tata-motors-cars-to-offer-harman-ignite-store-inside-in-car-infotainment-systems-120275) |
| Antstream Arcade | 레트로 클라우드 게임 (메르세데스) | 수치 없음 | — | [검색 발췌] [링크](https://www.techradar.com/vehicle-tech/hybrid-electric-vehicles/volkswagen-and-mercedes-announce-ev-gaming-upgrades-to-help-you-survive-lengthy-charging-breaks) |

### 4.5 차 안에서 실제로 쓰는 것

| 조사 | 핵심 수치 | 출처 / 확인 |
| --- | --- | --- |
| Canalys (2022, 미·영·독·프 약 2,000명) | 미러링을 자주 또는 매번 쓰는 비율: 미국 65%, 유럽 58% | [원문] [링크](https://appleworld.today/survey-65-of-respondents-depend-on-smartphones-and-digital-cockpits-like-carplay-for-most-of-their-drives) |
| Edison Research (2023, 미국) | CarPlay/Android Auto가 있는 차에서도 청취 시간의 48%는 AM/FM, 스트리밍 음악 18% | [원문, 2차 보도] [링크](https://gigazine.net/gsc_news/en/20231021-carplay-mostly-listening-radio) |

**플랫폼에서 반복되는 유형**: 내비와 오디오가 어디서나 1순위, 2023~2026의 확장은 주차·충전 대기용 영상·게임·브라우저에 몰림, POI 실용 앱(충전·주차·주유·경로 날씨)이 꾸준함, 2026년의 새 축은 음성 대화형과 위젯이에요. **차량 데이터(배터리, 차량 상태, 탑승자)를 실제로 쓰는 서드파티 앱, 동승자·아이용 앱은 추천 목록과 스토어 대표 앱에서 찾지 못했어요.**

---

## 5. 차량 데이터 기반 서드파티와 한국 앱

### 5.1 커넥티드카 API와 그 위의 앱

| 이름 | 하는 일 | 왜 인기 있는지 (근거) | 쓰는 차량 데이터 | 출처 / 확인 |
| --- | --- | --- | --- | --- |
| Smartcar | 제조사 계정 연동으로 하드웨어 없이 차량 데이터·제어를 앱에 제공 | 40개 이상 브랜드. 고객: Uber, Lyft, ChargePoint, Optiwatt 등. 연결 차량 수 없음 | 주행거리, 위치, 연료·배터리, 충전 제어 | [원문] [링크](https://smartcar.com/) |
| Optiwatt | 전기요금이 싼 시간대에 자동 충전 | 자사 발표 다운로드 18만 이상 | 배터리 잔량, 충전 시작·중지 | [검색 발췌] [링크](https://smartcar.com/blog/optiwatt-managed-ev-charging) |
| ev.energy | 출발 시각을 정하면 싼 시간대에 충전하고 보상 | 자사 발표 앱 사용자 20만 이상 | 충전 예약, 텔레매틱스 | [원문] [링크](https://ev.energy/drivers) |
| DIMO | 차를 연결하면 토큰 보상, 데이터를 쓸 앱을 운전자가 승인 | 2024-08 연결 차량 10만 대 돌파 | 타이어 공기압, 연료·오일, 배터리, 위치 | [검색 발췌] [링크](https://dimo.org/rewards) |
| Otonomo | 차량 데이터 마켓플레이스 | 2021년 14억 달러 가치로 상장 → 2023년 인수·합병. 데이터 중개만으로는 어려웠던 사례 | 제조사 텔레매틱스 | [검색 발췌] [링크](https://techcrunch.com/2023/02/10/otonomo-the-mobility-data-business-valued-at-1-4b-in-2021-spac-ipo-acquired-by-urgent-ly-as-market-cap-dips-to-70m) |

### 5.2 OBD 앱

| 이름 | 하는 일 | 왜 인기 있는지 (근거) | 쓰는 차량 데이터 | 출처 / 확인 |
| --- | --- | --- | --- | --- |
| Torque Pro, Car Scanner, OBD Fusion | 동글로 실시간 게이지와 고장 코드 표시 | 다운로드 수치 없음. 리뷰 기사가 "가장 인기 있는 OBD2 앱"으로 소개 | 고장 코드, 실시간 센서값 | [검색 발췌] [링크](https://www.slashgear.com/1365263/best-automotive-diagnostic-apps-for-android) |
| FIXD | 엔진 경고등 코드를 쉬운 말로 풀고 수리 단계를 안내 | 수치 없음 | 고장 코드 | [검색 발췌] 같은 URL |
| 인포카 (INFOCAR) | 차량 자가 진단, 고장 코드 설명, 경제운전 가이드 (한국) | 공식 다운로드 수치 없음 | 표준 OBD-II, 제조사 확장 데이터 | [검색 발췌] [링크](https://apps.apple.com/kr/app/id1447599519) |

운전자가 쓰는 이유는 "경고등이 왜 켜졌는지"와 "정비소에 가기 전에 미리 알기"예요.

### 5.3 주행 습관 보험·운전 점수

| 이름 | 하는 일 | 왜 인기 있는지 (근거) | 쓰는 데이터 | 출처 / 확인 |
| --- | --- | --- | --- | --- |
| 티맵 운전점수 | 주행 습관을 점수로 만들어 보험 할인 특약과 연결 | **가입자 500만 명에 육박**(2026-06 기사). 삼성화재·현대해상·DB·KB | 폰 GPS 기반 과속·급가속·급감속 | [원문] [링크](https://www.fnnews.com/news/202606261004583062) |
| 블루링크·기아 커넥트 안전운전 점수 | 차량이 수집한 주행 기록으로 점수화, 보험 할인 | 가입자 수 없음. 최근 90일·1,000km 이내 기록, 급가속·급감속·심야 운행이 적을수록 높음 | 급가속, 급감속, 심야 운행, 주행거리 | [원문] [링크](https://www.hyundaimotorgroup.com/ko/story/CONT0000000000141883) |
| Progressive Snapshot | 주행을 측정해 보험료 조정 (미국) | 갱신 시 평균 322달러 절약. 10명 중 약 2명은 보험료가 오름 | 급제동, 시간대, 주행거리 | [검색 발췌] [링크](https://www.nerdwallet.com/article/insurance/comparing-drivewise-snapshot) |
| Tesla Safety Score | 차량 센서로 점수를 매겨 보험료에 반영 | 컨슈머리포트: 평범하게 멈춰도 급제동 기준을 넘고, **점수를 지키려고 교차로를 서행 통과하는 왜곡 운전**을 경고 | 급제동, 급회전, 전방충돌경고, 차간거리 | [원문] [링크](https://www.consumerreports.org/cars/car-safety/tesla-safety-score-could-lead-to-unsafe-driving-a3816355048/) |

### 5.4 충전·차량 관리·주행 기록

| 이름 | 하는 일 | 왜 인기 있는지 (근거) | 쓰는 데이터 | 출처 / 확인 |
| --- | --- | --- | --- | --- |
| A Better Routeplanner | 차종과 배터리 잔량에 맞춰 충전 정차 계획 | 공식 사용자 수 없음. 제3자 추정 안드로이드 설치 130만~140만 | 실시간 배터리 잔량 | [검색 발췌] [링크](https://appgoblin.info/apps/com.iternio.abrpapp) |
| EV Infra (소프트베리) | 충전기 위치·요금·실시간 상태, 결제 (한국) | 2023-08 기준 누적 다운로드 53만, MAU 9만 | 위치, 충전기 상태 | [검색 발췌] [링크](https://hankyung.com/data/service/geeks/invest_introduce/9803473a944be37cf30556a8d1e7fe96.pdf) |
| 마이클 (마카롱팩토리) | 주행거리에 따라 소모품 교체 주기를 알리고 정비소 예약 (한국) | **누적 등록 차량 400만 대**, 2025년 매출 약 630억 원 | 주행거리, 정비 이력 | [원문] [링크](https://www.unicornfactory.co.kr/article/2026011315133670119) |
| MileIQ | 주행을 자동 감지해 업무·개인으로 분류, 세금 공제 자료 생성 | 사용자 100만 이상 | 폰 GPS 주행거리 | [검색 발췌] [링크](https://fitsmallbusiness.com/mileiq-review/) |
| 차량운행일지 (카택스) | GPS로 운행거리를 저장해 국세청 운행기록부 양식으로 내보냄 (한국) | 수치 없음. 2016년 세법 개정으로 운행일지를 내야 비용이 인정되는 것이 수요의 근거 | 주행거리, 출발·도착 위치·시각 | [검색 발췌] [링크](https://apps.apple.com/kr/app/id1105310530) |

### 5.5 한국 운전자가 많이 쓰는 앱

| 이름 | 근거 | 출처 / 확인 |
| --- | --- | --- |
| 네이버지도 | 2026-08 MAU 3,079만 명 (모바일인덱스) | [원문] [링크](https://www.etnews.com/20260903000250) |
| 티맵 | 2026년 상반기 평균 MAU 1,468만 명 | [검색 발췌] [링크](https://www.ddaily.co.kr/page/view/2026071410313189027) |
| 카카오맵 | 2026-08 MAU 1,349만 명 | [원문] etnews |
| 실제 내비 이용 점유율 | 컨슈머인사이트 2024 (신차 구입자): 티맵 74%, 카카오맵 12%, 네이버지도 7% | [검색 발췌, 블로그 재인용] [링크](https://foxcg.com/navigation-app-comparison-tmap-naver-kakao/) |
| 현대·기아 커넥티드카 서비스 | 글로벌 가입자 2023-06 1,000만 돌파 | [원문] [링크](https://news.tf.co.kr/read/ogmeta/2026288.htm) |
| 모두의주차장, 아이파킹 등 주차 앱 | MAU 수치 없음 | [검색 발췌] [링크](https://byline.network/2026/10/26_2917273/) |

블루링크·마이현대의 기능별 이용 통계(원격 시동·공조 등)는 찾지 못했어요.

**차량 데이터 서비스에서 반복되는 유형**: 돈으로 바로 돌아오는 점수·기록(운전점수 할인, 스마트 충전, 운행기록부), "내 차가 왜 이래"를 풀어 주는 진단, 주행거리 하나로 자동화되는 관리(소모품 알림), 배터리 잔량과 위치를 묶은 계획이에요. 점수화는 기준이 납득되지 않으면 반발과 왜곡 운전을 낳아요.

---

## 6. 분석

### 6.1 생태계를 가로질러 반복되는 유형과 이유

| # | 유형 | 대표 사례 | 왜 반복되는지 | 근거 강도 |
| --- | --- | --- | --- | --- |
| A | 정차·충전 대기 엔터테인먼트 | Tesla Theater·Arcade, AirConsole, Ford Arcade, AAOS 영상·게임, 르노 SongPop | 충전과 기다림(포드: 하루 5~20분)이라는 빈 시간이 있고, 큰 화면과 스피커가 이미 있어요 | 보급은 넓지만 이용 수치는 없음. Steam 철수처럼 고사양은 실패, 캐주얼만 남음 |
| B | 주차 중 공간 모드 (낮잠·캠핑·휴식) | Tesla Camp Mode, 샤오미·리프모터 小憩模式, 샤오펑 睡眠空间, 메르세데스 Energizing, 리비안 Camp Mode | 공조·시트·조명·전원을 한 번에 묶는 것은 차만 할 수 있어요. 중국에서는 모든 브랜드의 표준이에요 | 이용 수치 없음. 모든 생태계에 존재한다는 점이 근거 |
| C | 떠나 있을 때의 안심 (반려동물·감시·알림) | Tesla Dog Mode·Sentry, 리비안 Pet Comfort·Gear Guard, 루시드 Creature Comfort, 리오토 宠物模式 | 생명과 재산이 걸려 있고, 밖에서 보이는 화면 문구가 행인의 오해를 막아요 | Sentry 인지도 최고, Pet Comfort는 "요청이 가장 많았던 기능". Escalent에서 보안이 중요도 1위(71%) |
| D | 사용자 자동화 (장면·루틴) | 샤오미 超级任务, 리오토 任务大师, 메르세데스 Routines, S3XY Buttons, Tessie 자동화 | 제조사가 다 채우지 못하는 개인별 작은 불편을 스스로 해결하고, 공유 코드로 퍼져요 | **수치 있음**: 샤오미 누적 28.6만 개 |
| E | 차량 데이터 기록·관리 | TeslaFi·Tessie·TeslaMate, 마이클, OBD 앱, 폴스타 Journey Log, 운행일지 | 내 차 상태를 숫자로 보고 남과 비교하려는 욕구, 경고등 불안, 세금·정비 같은 실무 | 수치 있음: Tessie 50만(자사), 마이클 등록 400만 대. Escalent에서 차량 상태·정비 56% |
| F | 돈으로 돌아오는 점수 | 티맵 운전점수, 블루링크 안전운전 점수, Snapshot, 스마트 충전 | 할인·환급으로 바로 이어져요 | 수치 있음: 티맵 약 500만. 단 보험사 제휴가 있어야 성립 |
| G | 밖에 보여 주는 재미 (조명 쇼·테마·캐릭터) | Tesla Light Show·Santa Mode, 리비안 핼러윈·Gary, NIO NOMI | 차량 하드웨어로 공유할 거리를 만들고, 기능에 성격을 입히면 별명과 화제가 생겨요 | 화제성은 크지만 실사용 근거는 약함. 실용 기능에 대한 반응이 더 좋았음 |
| H | 가족·아이 | 리오토 小同桌(대화 기능 162.5만 회, 아이 전용 여부 미확인)·뒷좌석 스크린(291.3만 시간), 화웨이 宝宝巴士, 무마이크 노래방 | 차가 가족의 이동 거실이 될 때 뒷좌석의 지루함이 가장 큰 문제예요 | 수치 있음(리오토). 서구 플랫폼에는 비어 있음 |
| I | 음성 비서 | 理想同学, 小爱同学, NOMI, Grok, Gemini | 주행 중 유일하게 안전한 입력 | 수치 최강(5일에 6천만 회). 이미 조사한 도메인이고 Gleo 연동이 막혀 있어 여기서는 제외 |

눈여겨볼 점 세 가지:

1. **이식형 콘텐츠(영상·게임·브라우저)는 어디에나 있지만 근거가 약하고, 차만 할 수 있는 기능(B·C·D)이 반응이 구체적이에요.** Pleos App Market의 현재 18종은 전부 이식형이에요.
2. **소비자가 중요하다고 답한 것(보안, 차량 상태, 안전)과 스토어에 있는 것(엔터테인먼트)이 어긋나요.** S&P와 Escalent 조사에서 일관되게 나와요.
3. **"쓰기 어렵다"가 최대 불만이에요**(J.D. Power 중국 TXI 36%, Escalent에서 56%가 앱 존재를 모름). 기능을 더하는 것보다 발견하게 해 주는 것(리프모터 功能地图)도 유형이 될 수 있어요.

### 6.2 Pleos 제약 안에서 가능한지

판단 기준은 허용 SDK 5종과 제약이에요. Vehicle SDK의 함수 이름은 `docs/pleos-docs/reference/00-INDEX-api-catalog.md`에서 확인했어요. 확인한 것: 공조(`setHvac*`), 시트 열선·통풍(`setHvacSeatTemperature`, `setHvacSeatVentilation`), 앰비언트·실내등(`setAmbientLight*`, `setCabinLightSwitch`), 창문(`setWindowPosition`), 스마트 글라스(`setSmartGlass*`), 도어 잠금(`setDoorLock`)은 제어할 수 있고, 착좌·안전벨트·조향각·브레이크 페달·와이퍼·연료/배터리·주행거리·타이어 저압·브레이크액 경고는 읽을 수 있어요. **시트 위치(등받이 각도 등)는 읽기만 있고 제어 함수는 찾지 못했어요.** 에뮬레이터에서 실제로 동작하는지는 이 문서에서 실측하지 않았어요.

| 유형 | 가능 여부 | 이유 | Pleos App Market에 있는지 |
| --- | --- | --- | --- |
| A 정차 엔터테인먼트 | **가능** (정차 중 한정) | 정차(P단) 중에는 영상·게임 제한이 풀려요. 조향각·브레이크·창문 상태를 게임 입력으로 읽을 수 있어요. 영상 스트리밍은 이식형이라 차별점이 없어요 | 영상(유튜브)은 있음. 게임은 없음(확대 예정 카테고리) |
| B 주차 중 공간 모드 | **부분 가능** | 공조·시트 열선/통풍·조명·창문·스마트 글라스 제어와 TTS 알람은 가능. **시트 눕힘은 불가**(제어 함수 없음). 시동을 끈 뒤에도 유지되는지는 미확인 | 없음 |
| C 떠나 있을 때의 안심 | **부분 가능 ~ 불가** | 카메라가 없어 Sentry류는 불가. 반려동물 모드는 공조 유지와 화면 문구까지는 가능해 보이지만, 운전자가 내린 뒤 앱이 계속 공조를 잡고 있을 수 있는지 미확인. 폰 알림은 외부 서버를 직접 만들어야 해요 | 없음 |
| D 사용자 자동화 | **가능** | 트리거(착좌, 문, 와이퍼, 등화, 배터리, 도착 이벤트, 위치)와 동작(공조, 조명, 창문, 시트 열선, TTS)이 모두 허용 SDK 안에 있어요. 편집 화면은 정차 중에만 열면 돼요. 차속·기어 트리거는 불가 | 없음 |
| E 차량 데이터 기록·관리 | **가능** | 주행거리, 연료/배터리, 연비, 타이어 저압·브레이크액 경고, 위치를 읽을 수 있어요. OBD 고장 코드는 없고 경고등 종류는 SDK가 주는 범위로 한정돼요 | 없음(확대 예정 카테고리 "차량 관리") |
| F 돈으로 돌아오는 점수 | **부분 가능** | 브레이크·조향각·위치로 점수는 만들 수 있지만 차속을 못 읽고, 보험 제휴 없이는 "돈"이 없어요. 티맵이 이미 500만이에요 | 없음 (내비 앱 안에 유사 기능 가능성) |
| G 밖에 보여 주는 재미 | **부분 가능** | 앰비언트·실내등·전조등·안개등 스위치 제어는 있어요. 외부 스피커와 주변 인식 시각화는 없어요. 외부 등화를 장난으로 켜는 것은 심사에서 안전 문제가 될 수 있어요 | 없음 |
| H 가족·아이 | **가능** (음성 중심) | TTS·STT, 착좌(뒷좌석에 누가 탔는지), 내비 남은 시간을 쓸 수 있어요. 주행 중 영상·긴 텍스트는 금지라 소리 중심이어야 해요. 차량 마이크 음성의 외부 전송은 불가 | 없음 |
| I 음성 비서 | **불가에 가까움** | Gleo 연동 불가, 로컬 LLM 불가, 마이크 음성 외부 전송 불가 | 기본 탑재(Gleo) |

### 6.3 Pleos에 차용할 만한 후보

대학생 팀이 사흘 안에 에뮬레이터로 시제품을 만든다는 점을 기준으로 추렸어요. 순위는 ① 레퍼런스의 근거 강도 ② Pleos SDK로 온전히 되는 정도 ③ 사흘 구현 가능성 ④ App Market에 없는지를 함께 본 제 판단이에요.

| 순위 | 후보 | 원본 레퍼런스 | Pleos SDK로 바꾼 모습 | 차별점 | 위험 |
| --- | --- | --- | --- | --- | --- |
| 1 | **장면 레시피** (차량 자동화) | 샤오미 超级任务(누적 28.6만 개), 리오토 任务大师, 메르세데스 Routines, S3XY Buttons | "조건 → 동작" 카드. 조건: 착좌·문·와이퍼·등화·배터리·목적지 도착 N분 전·위치. 동작: 공조·시트 열선·조명·창문·TTS 한마디. 인기 레시피 10여 개를 기본 제공하고, 정차 중에 켜고 끄기와 간단 편집. 예: "비 오면(와이퍼) 뒷유리 열선과 내기 전환", "뒷좌석에 앉으면 뒷좌석 온도 맞춤", "도착 5분 전 창문 닫힘 확인" | 국내 차에 없는 유형이고 Vehicle SDK를 가장 넓게 써요. 공유 코드로 확산 구조를 보여 줄 수 있어요 | 범위가 넓어 사흘 안에 편집기까지 만들면 얕아져요 → 프리셋 위주로 좁혀야 해요. 주행 중 자동 제어가 운전자를 놀라게 하면 감점. "누구의 어떤 불편인지"가 흐려지기 쉬워요 |
| 2 | **정차 휴식 모드** (낮잠·쉼) | 샤오미·리프모터 小憩模式, 샤오펑 睡眠空间, Tesla Camp Mode, 메르세데스 Energizing, 르노 take a break! | 정차 중 한 번 누르면 공조(내기·온도)·시트 통풍/열선·조명 어둡게·스마트 글라스·창문 틈 환기를 묶고, 정한 시간 뒤 TTS와 조명으로 깨운 다음 원래 상태로 되돌려요. 내비 남은 시간과 연결해 "20분 쉬어도 약속에 늦지 않아요"를 알려 줘요 | 중국 전 브랜드 표준인데 국내 차에는 없어요. 졸음운전 통계(기존 리서치)와 바로 이어져요 | **시트 눕힘은 SDK로 불가.** 시동 상태에서 밀폐 수면의 안전(환기) 설명이 필요해요. `docs/ideas/2026-11-13_ideas-mixed.md`의 "쉼표"와 겹쳐요 |
| 3 | **경고등 통역 겸 차량 일지** | FIXD·인포카(경고등을 쉬운 말로), 마이클(등록 400만 대), TeslaFi·Tessie, 카택스 운행일지, Gemini의 2026년 경고등 응답 | 경고(타이어 저압, 브레이크액 등)가 뜨면 "지금 멈춰야 하는지, 며칠 안에 정비하면 되는지"를 TTS 한 문장으로 말해요. 주행거리로 소모품 교체 시점을 알리고, 운행을 자동 기록해 운행기록부 양식으로 내보내요 | App Market 확대 예정 카테고리(차량 관리)이고, 설문에서 중요도가 높은 유형이에요. 폰 앱과 달리 입력이 필요 없어요 | SDK가 주는 경고 종류가 적으면 "통역"이 빈약해져요(읽을 수 있는 경고 목록을 먼저 확인). 차계부는 흔해서 독창성 점수가 낮을 수 있어요. 재방문 빈도가 낮아요 |
| 4 | **차 조작 파티 게임** | AirConsole(BMW·VW), Ford Arcade Sports Collection, Tesla Beach Buggy Racing(핸들 조작), VW의 조명 연동 게임 | 정차 중 게임. 폰은 QR로 접속한 웹 컨트롤러, 차는 핸들 각도·브레이크 페달·좌석별 창문 스위치가 입력이고 앰비언트 조명이 결과를 보여 줘요. 예: 좌석별 창문 버저 퀴즈, 핸들 미니게임 | App Market에 게임이 없어요(확대 예정). AirConsole은 차량 입력을 쓰지 않아서, 차 조작을 입력으로 쓰는 점이 차별점이에요 | 운전자의 불편이 아니라 재미라서 "불편이 선명해야 한다"는 기준에 약해요. 이용 수치 근거가 없어요. `docs/ideas/2026-11-13_ideas-free.md`의 "카 이스케이프"·"창문 버저 퀴즈쇼"와 겹쳐요 |
| 5 | **반려동물·동승자 대기 모드** | Tesla Dog Mode, 리비안 Pet Comfort("요청이 가장 많았던 기능"), 루시드 Creature Comfort, 리오토·샤오미 宠物模式 | 운전자가 잠깐 내릴 때 공조를 정한 온도로 유지하고, 화면 가득 "에어컨 켜져 있어요, 실내 22도, 주인이 곧 와요"를 띄워요. 창문 잠금, 배터리·연료가 기준 아래면 종료하고 창문을 조금 열어요 | 수요 근거가 여러 생태계에서 가장 일관돼요. 현대차에는 유사 기능이 일부 차종에만 있어요(미확인) | **하차 뒤에도 앱이 공조를 유지할 수 있는지 미확인이고, 안 되면 성립하지 않아요.** 생명이 걸린 기능이라 오작동 책임이 커요. 폰 알림은 서버를 직접 만들어야 해요. 실내 카메라가 없어요 |
| 6 | **하차 한마디** | Tesla "폰 두고 내림 알림"(최다 공감 댓글), 중국 뒷좌석 남겨짐 알림, J.D. Power의 "하차 보조가 사용 빈도·재구매 의향 높음" | 도착 이벤트나 운전석 문 열림에 맞춰 열린 창문·켜진 실내등·뒷좌석 착좌(아이)·우산(와이퍼 이력)을 TTS 한 문장으로 알려요 | 작고 확실한 실용 기능. 사흘 안에 완성도를 높이기 쉬워요 | 폰 두고 내림은 감지할 수 없어요. 기존 후보 "하차 체크"와 같아요. 작아서 단독 출품으로는 약할 수 있어요 |
| 7 | **뒷좌석 아이 음성 놀이** | 리오토 小同桌(대화 기능, 5일에 162.5만 회. 아이 전용 여부 미확인)·小主人 모드(아이 콘텐츠), 화웨이 宝宝巴士, 르노 SongPop, Grok Storyteller 모드 | 뒷좌석 착좌를 감지하면 남은 시간에 맞춘 퀴즈·이야기를 TTS로 들려주고, STT로 답을 받아요. "언제 도착해?"에 아이 말로 대답해요 | 서구 플랫폼과 Pleos 모두 비어 있는 유형이에요. 수치 근거가 있어요 | 차량 마이크 음성을 외부로 못 보내서 대화 품질이 STT 텍스트 범위로 한정돼요. 뒷좌석 목소리가 잘 잡히는지 미확인. 기존 후보 "길동화"·"뒷좌석 아이 동반자"와 겹쳐요 |
| 8 | **기능 지도** (내 차 기능 발견) | 리프모터 功能地图, J.D. Power 중국 TXI("쓰기 어렵다" 36%), Escalent(56%가 앱 존재를 모름) | 공조·조명·시트·창문 상태를 읽어 "아직 안 써 본 기능"을 정차 중에 하나씩 체험시키고 배지를 줘요 | 다른 팀이 낼 가능성이 낮아요. "기능은 많은데 모른다"는 불편이 조사로 뒷받침돼요 | 레퍼런스가 한 곳뿐이고 이용 수치가 없어요. 읽을 수 있는 기능 범위에 따라 내용이 얕아질 수 있어요 |
| 9 | **세차·주차 준비 한 번에** | Tesla Car Wash Mode, 리프모터 洗车模式, 지커 세차 모드 | 한 번 누르면 창문 닫기, 내기 순환, 와이퍼 끄기(`setWiperSwitch`), 열린 문 확인을 하고 끝나면 되돌려요 | 만들기 쉬워요 | 미러 접기·오토 와이퍼 감도는 제어 못 해요. 가끔 쓰는 기능이라 단독으로는 약해요 → 1번 레시피의 하나로 넣는 편이 좋아요 |
| 10 | **실내 조명 쇼** | Tesla Light Show, 리비안 핼러윈 모드, EVANNEX의 "음악 연동 앰비언트 조명" 요청 | 정차 중 음악 박자에 맞춰 앰비언트 조명 색과 밝기를 바꿔요. 생일·크리스마스 테마 | 시연 영상이 화려해요 | 실용성이 없고 실사용 근거가 약해요. 조명 제어 주기 제한이 있을 수 있어요(미확인). 외부 등화까지 쓰면 안전 문제 |

**넣지 않은 것과 이유**: 영상·브라우저·내비·음악(이미 이식형이 있음), 음성 비서(Gleo 연동 불가, 이미 조사), 주행 반응 음악(Sound Drive는 전용 음원이 필요하고 차속을 못 읽음, 기획 보류 중인 MooDrive와 같은 영역), 운전 점수(보험 제휴 없이는 가치가 없고 티맵이 선점), Sentry류(카메라 불가), 노래방(마이크 원음 접근과 음원 저작권 문제), 충전 계획(내비 앱 영역).

### 6.4 이 조사에서 얻은 판단 재료

- 레퍼런스 근거가 가장 단단한 것은 **수치가 있는 D(자동화), H(아이), E(차량 관리)**예요. B(휴식 모드)와 C(반려동물)는 수치는 없지만 모든 생태계에 있다는 점이 근거예요.
- 1·2·6·9번은 서로 붙여서 한 앱으로 만들 수 있어요. "레시피"라는 틀 안에 휴식·하차·세차 장면을 대표 레시피로 넣는 식이에요. 다만 그렇게 묶으면 "누구의 어떤 불편"이 흐려지니, 발표에서는 한 장면(예: 휴식)을 주인공으로 세우는 편이 좋아요.
- 먼저 실측할 것: ① 운전자가 내린 뒤에도 앱이 공조를 유지하는지(5번의 성립 조건) ② SDK가 주는 경고등 종류(3번의 깊이) ③ 뒷좌석 STT 인식률(7번).
