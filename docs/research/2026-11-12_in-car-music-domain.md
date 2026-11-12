# 차 안에서 음악 고르고 듣기: 도메인 리서치

- 작성일: 2026-11-12
- 목적: MooDrive를 처음부터 다시 검토하기 위해 "차에서 음악을 고르고 듣는 일" 전반을 조사했습니다. Pleos Connect 앱 공모전(Android Automotive) 기준입니다.
- 선행 문서: `docs/research/user-evidence.md`(2026-11-03). 그 문서에 있는 내용은 반복하지 않고, 보강하거나 정정할 내용만 적었습니다.
- 조사 방법: WebSearch, WebFetch(원문 열람), 저장소의 Pleos 공식 문서(`docs/pleos-docs/`)를 썼습니다. 커뮤니티는 로그인 없이 공개 글만 읽었고, 닉네임은 적지 않았습니다. 커뮤니티 글은 인용하지 않고 의역했습니다.
- 확인 수준 표기
  - **[원문]**: 해당 페이지를 직접 열어 확인함
  - **[검색 발췌]**: 검색 결과 요약으로만 확인함. 쓰기 전에 원문을 다시 확인해야 함
  - **[내부 문서]**: 저장소의 Pleos 공식 문서 사본에서 확인함
- **조사 한계**
  - 이 세션의 웹 검색 한도(200회, 같은 턴의 다른 에이전트와 공유)를 다 써서 후반 항목은 추가로 검색하지 못했습니다. 이어서 조사하려면 후속 요청이 필요합니다.
  - 네이버 블로그·카페, Threads, 엠엘비파크는 수집 도구에서 막혔습니다. 디시인사이드, 뽐뿌(인코딩 깨짐), 보배드림, 더쿠, 에펨코리아는 검색에서 관련 글이 거의 나오지 않았습니다. 그래서 커뮤니티 근거는 **클리앙과 82cook에 치우쳐 있습니다.** 두 곳 모두 30~50대 비중이 높은 커뮤니티입니다.
  - 유튜브 댓글은 수집하지 못했습니다.

---

## 0. 요약 (10줄)

1. **차는 한국에서도 라디오를 가장 많이 듣는 장소입니다.** 2025-07 온라인 설문(1,273명)에서 청취 장소 1위는 차량(54.1%), 청취 기기 1위는 차량 라디오(51.1%)였습니다. 미국도 차 안 오디오 시간의 55%가 AM/FM입니다(2026 Q1).
2. **차량 내 스트리밍은 빠르게 늘고 있고, 주 이용층은 30·40대 남성입니다.** 지니뮤직 차량 MAU는 2024년에 59% 늘었고, 이 중 30·40대가 70%, 남성이 62%입니다. 증가 폭은 폰 미러링(+68%)이 내장 인포테인먼트(+42%)보다 컸습니다.
3. **한국 음악 앱 판도가 바뀌고 있습니다.** 2026-06 MAU는 유튜브 뮤직 949만, 스포티파이 622만(전년 대비 +233만), 멜론 593만(−44만) 순입니다. 스포티파이가 멜론을 처음 앞섰습니다.
4. **커뮤니티에서 반복되는 불편은 선곡보다 "누가, 어떻게 조작하느냐"입니다.** 운전 중 곡 넘기기와 볼륨 조작, 동승자나 아이와의 선곡 충돌(동요), 내비 안내와 음악 볼륨 충돌이 자주 나옵니다. 운전 중 아무것도 틀지 않거나 라디오만 듣는 사람도 꽤 있습니다.
5. **차량 전용 음악 제품은 여러 번 실패했습니다.** 스포티파이는 Car Thing(하드웨어, 3,140만 달러 손실), Car View, Car Mode를 모두 접었습니다. 반대로 플랫폼에 붙은 기능(Android Auto의 Jam, Waymo 연동)은 살아남았습니다.
6. **"주행 상황에 맞춰 자동으로 바뀌는 차량 사운드"는 이미 시도된 영역입니다.** Mercedes MBUX Sound Drive(주행 조작에 음악이 반응), Endel×Mercedes 파일럿(날씨·속도·도로 유형 반영), 기아 R.E.A.D.(CES 2019, 감정 인식)가 있습니다. "기존 무드 기능은 전부 수동 선택"이라는 기존 주장은 틀렸습니다.
7. **연구 근거는 기존 문서보다 약합니다.** 메타분석(Millet 외 2019, 연구 12편)에서 템포는 운전 수행에 유의한 차이를 내지 않았습니다. 운전자가 좋아하는 음악이 초보 운전자의 실수를 가장 많이 늘렸다는 연구도 있습니다. 음악의 졸음 억제 효과는 15~25분만 갔고, 노래 제목 맞히기 같은 **대화형 과제**가 피로 대응에 효과를 보였습니다.
8. **독립 앱에는 라이선스 장벽이 사실상 결정적입니다.** 스포티파이는 2024-11부터 신규 앱에 추천·오디오 특성(템포·에너지 등) API를 막았고, 유튜브 API는 백그라운드·오디오만 재생을 금지합니다. 국내 음원은 신탁단체 전송사용료 체계를 따라야 합니다.
9. **Pleos 자체 정책이 "그냥 음악 플레이어"를 걸러냅니다.** 음악을 보조 기능으로 넣은 앱은 즉시 반려하고, 자동 재생을 금지합니다. Gleo AI는 이미 "멜론에서 ○○ 노래 틀어줘" 같은 음성 재생을 지원합니다. 앱 마켓에는 멜론(취향 추천 포함)과 라디오 앱 3종이 이미 있습니다.
10. **빈 곳은 "음악 선택"이 아니라 그 주변입니다.** 동승자 중재(가족·아이), 졸음 구간의 대화형 오디오, 남은 시간에 맞춘 말 콘텐츠, 출발할 때와 정체 중의 오디오 루틴 같은 영역입니다. 음원을 직접 다루지 않고 차량 신호(좌석 점유, HVAC, 남은 시간)를 핵심에 쓰는 설계가 레드플래그를 피하기 쉽습니다.

---

## 1. 이용 행태 데이터

### 1-1. 한국: 라디오와 차

| 항목 | 수치 | 출처·날짜 | 확인 |
|---|---|---|---|
| 라디오 청취율 | 51% | 아젠다북 「라디오 청취 트렌드·광고효과」, 전국 18세 이상 1,273명, 모바일 패널 온라인 조사, 2025-07, ±2.7%p — https://www.businesskorea.co.kr/news/articleView.html?idxno=247734 | [원문] |
| 청취 기기 | 차량 라디오 51.1%, 스마트폰 앱 39.2%, 집·사무실 라디오 15.3%, PC 9.0% | 같은 조사 | [원문] |
| 청취 장소 | 차량 54.1%, 집 36.1%, 직장·학교 12.1%, 대중교통 7.9% | 같은 조사 | [원문] |
| 청취 시간대 | 06–09시 30.9%, 09–12시 25.0%, 14–18시 22.0%, 12–14시 17.9% | 같은 조사 | [원문] |
| 선호 프로그램·청취 이유 | 음악 프로 59.6%, 뉴스·시사 26.0% / 이유: 음악 56.2%, 정보 28.3%, 배경음 20.4%, 심리적 위안 18.5% | 같은 조사 | [원문] |
| 라디오 광고 인지 | 69.2%가 라디오 광고로 브랜드를 알게 된 적 있음 | 같은 조사 | [원문] |
| 차량 라디오 청취 증가(과거) | 2012→2014 차량용 라디오 청취 +5.6%p, 일반 수신기 −4.6%p | KISDI 방송매체 이용행태조사 분석 — https://www.kisdi.re.kr/report/fileView.do?key=m2101113024973&arrMasterId=3934580&id=520265 | [검색 발췌] |
| 라디오 주 청취 장소(2005) | 자동차 운전 중 49.8% | 『방송연구』 2006 겨울호 인용 — https://m.korea.kr/expertWeb/resources/files/data/document_file/2006/2006win_06.pdf | [검색 발췌] |

- 해석: 출근 시간대(06–09시) 청취가 가장 많고, 사람들은 음악을 들으려고 라디오를 켭니다. **"누가 대신 골라주는 음악"이 차 안 오디오의 기본값**이라는 뜻입니다. 운전 중 선곡 부담을 덜고 싶다는 수요는 라디오가 이미 일부 채우고 있습니다.
- 공식 통계 공백: 방송통신위원회 「방송매체 이용행태조사」(2024·2025)의 라디오 장소별 수치는 이번에 확인하지 못했습니다. 2025년 조사는 13세 이상 8,320명 방문면접이며 2025-12-30에 발표됐습니다(https://news.tf.co.kr/read/economy/2277385.htm). 원문은 mediastat.or.kr에서 확인해야 합니다.

### 1-2. 한국: 차량 내 스트리밍

- **지니뮤직 차량 이용(2024년 데이터, 2025-04-16 발표) [원문]**: 차량 MAU가 전년 대비 59% 늘었습니다. 인포테인먼트 경유 소비는 +42%, 스마트폰 미러링(안드로이드 오토·카플레이) 경유는 +68%입니다. 이용자 중 30·40대가 70%, 남성이 62%입니다. 시간대·장르 데이터는 발표에 없었습니다. — https://m.bobaedream.co.kr/board/bbs_view/nnews/228997/2/37
- **국내 음악 앱 MAU(와이즈앱·리테일, 2026-06) [원문]**: 유튜브 뮤직 949만, 스포티파이 622만(+233만), 멜론 593만(−44만), 지니 237만(−22만), 플로 167만(−3만). 2026-01에는 멜론이 약 100만 명 앞섰는데 6월에 역전됐습니다. 스포티파이는 2024-10에 광고 기반 무료 요금제를 시작했고 네이버플러스 멤버십과 연계했습니다. 기사는 국내 플랫폼이 저작권료 구조와 수익성 때문에 무료 요금제를 도입하기 어렵다고 설명합니다. — https://www.ceoscoredaily.com/page/view/2026072916262538897 (2026-07-29)
- **내장 앱과 연동 방식 [검색 발췌]**: 기아 커넥트 스트리밍은 멜론·지니 **유료 구독 계정**을 차량 프로필과 기아 계정에 연동해야 쓸 수 있습니다. 지원 차량은 ccNC와 14.5인치 화면 차량으로 한정됩니다. — https://www.kia.com/kr/kia-connect/service/streaming-game/audio
- **Pleos 앱 마켓 [원문]**: 2026-09 업데이트로 국내 앱이 18종이 됐고, 음악·오디오 신규 앱은 멜론, Radioline, 래디오오토(RaiDIO Auto), 픽클(Pickle) 4종입니다. 멜론은 "취향 기반 음악 추천과 다양한 플레이리스트"를 제공한다고 소개됩니다. 이용 차량은 더 뉴 그랜저, 디 올 뉴 아반떼 등입니다. — https://www.venturesquare.net/1111191/ (2026-09-07). 픽클과 래디오오토가 어떤 앱인지는 검색 한도 때문에 확인하지 못했습니다.
- **스포티파이 AI DJ 한국어 [검색 발췌]**: 2026-06 보도 기준으로 한국어 버전을 개발 중이며 아직 출시하지 않았습니다. 2026-03 확대 국가 목록에도 한국은 없었습니다. — https://v.daum.net/v/20260604180353743 , https://www.digitalmusicnews.com/2026/03/18/spotify-ai-dj-expands-to-more-countries/

### 1-3. 해외 비교

- **미국 차 안 오디오 시간 점유율(Edison Share of Ear, 2026 Q1) [원문: 2차 보도]**: AM/FM 55%, 스트리밍 16%입니다. 13~34세는 AM/FM 46%, 스트리밍 30%입니다. — https://www.jalopnik.com/2147090/radio-wins-in-cars/ (2026-04-13)
- **CarPlay·Android Auto [원문]**: 미국인 32%가 주 차량에 둘 중 하나를 갖고 있습니다(2024 데이터, 2023년 26%). 가진 사람의 83%가 실제로 씁니다. 상위 100개 판매 차종 중 라디오 전용 버튼이 있는 차는 26%(전년 36%)이고, 74%는 시동 시 마지막 음원을 자동으로 이어 재생합니다(Quu 2025). — https://radioink.com/2025/07/31/in-car-audio-behavior-shifts-with-carplay-android-auto-growth/
  - CarPlay·AA가 없는 운전자는 차 안 시간의 62%를 AM/FM으로 듣고, 있는 운전자는 47%로 떨어진다는 Edison 자료도 있습니다. **[검색 발췌]**
- **누가 DJ를 맡아야 하나(호주, Spotify×Censuswide) [검색 발췌]**: 운전자 33%, 조수석 동승자 28%. — https://themusic.com.au/news/shotgun-aussies-debate-who-should-control-the-playlist-on-roadtrips/u0UBr67R0NM/25-03-24
- 스포티파이에는 사용자가 만든 드라이브 관련 플레이리스트가 7천만 개라는 수치가 있지만 날짜가 없습니다. **[검색 발췌]** — https://www.automotiveinteriorsworld.com/news/infotainment/spotify-launches-in-car-streaming-device.html

### 1-4. 커뮤니티에서 본 청취 방식 (한국 30~50대 중심, 의역)

| 글 | 수치 | 내용 |
|---|---|---|
| 클리앙 굴러간당 「운전하실때 음악 들으시나요?」 2020-02-12 — https://www.clien.net/service/board/cm_car/14583431 | 조회 9,273, 댓글 78 | 작성자를 뺀 댓글 32명 중 음악(스트리밍·USB·블루투스) 18, 팟캐스트·말 콘텐츠 4, 상황 따라 4, 아무것도 안 틂 4, 라디오 2. 가장 흔한 이유는 **디젤·타이어·노면 소음 가리기**였음. 안전을 위해 볼륨을 낮추거나 끈다는 사람도 있었음 |
| 클리앙 굴러간당 「출퇴근 때 뭐 들으며 운전하세요?」 2021-09-15 — https://www.clien.net/service/board/cm_car/16501763 | 조회 5,502, 댓글 49 | 라디오 약 20회 언급으로 가장 많음. 그다음 직접 고른 음악 7, 스트리밍 6, 유튜브 5, 팟캐스트 5, 영어 공부 4, 무음 3. 시사 라디오가 스트레스를 줘서 추천 스트리밍으로 바꿨다는 사람과, **추천이 원치 않는 장르로 흘렀다**는 사람이 있었음 |
| 클리앙 굴러간당 「운전할 때 음악 듣는 거 싫어하는 분?」 2014-02-15 — https://www.clien.net/service/board/cm_car/5568261 | 조회 4,122, 댓글 25 | 작성자는 엔진·노면 소리를 듣고 싶어 음악을 싫어함. 댓글은 무음 4, 음악 4, 상황 따라 3, 라디오 1, 말 콘텐츠 1. 여러 명이 **졸음 방지**를 음악의 진짜 목적으로 꼽음 |
| 82cook 「초보운전, 운전할 때 음악 듣나요?」 2023-06-09 — https://www.82cook.com/entiz/read.php?bn=15&num=3658189 | 조회 3,601, 댓글 31 | 초보 때는 집중을 위해 안 듣는다는 댓글 11, 잔잔한 연주곡·클래식 추천 6, 긴장을 풀려고 처음부터 틀었다는 댓글 5. **라디오 DJ의 말소리가 방해된다**는 의견이 여럿 |
| 클리앙 모두의공원 「음악 스트리밍 뭐 쓰시나요?」 2026-09-11 — https://www.clien.net/service/board/park/19262165 | 조회 3,108, 댓글 28 | 유튜브 뮤직 언급이 가장 많음. 차량 관련 댓글 3개: 플로가 순정으로 깔려 있어 운전할 때 가장 편함, 스포티파이가 차에 기본 설치돼 구글 음성으로 잘 찾아줌, 제네시스 뮤직(돌비)을 씀. **"차에 이미 깔려 있는 앱"이 선택을 좌우함** |

- 시사점: 30~50대 운전자에게 차 안 오디오는 (1) 소음 가리기, (2) 졸음 방지, (3) 출퇴근 정보·습관에 가깝습니다. "좋은 곡 발견"은 상대적으로 덜 언급됩니다. 무음이나 라디오를 고르는 사람도 꾸준히 있습니다.

---

## 2. 페인포인트 (커뮤니티 근거 + 반응 수치)

> 커뮤니티 글은 모두 의역입니다. 수치는 수집 시점(2026-11-12) 기준입니다. 클리앙 검색 목록 페이지의 날짜·조회수는 실제 글과 어긋나는 경우가 있어, 직접 연 글의 수치만 적었습니다.

### P1. 운전 중 조작: 곡 넘기기·볼륨이 위험하고 불편함
- 클리앙 질문 게시판(2013-12-19, 조회 1,169, 댓글 0): 아이폰을 블루투스로 연결하면 재생은 되지만, 곡 넘기기와 볼륨은 차량 터치스크린을 눌러야 해서 위험하다는 글. 스티어링 휠에 다는 블루투스 리모컨을 찾았지만 없었음. — https://www.clien.net/service/board/kin/5313892
- 클리앙 2027 A6 시승기(2026-10-27, 조회 4,036, 댓글 14): 스티어링 휠의 곡 넘김 스위치가 터치식이라는 언급. **[검색 목록 발췌]** — https://www.clien.net/service/board/cm_car/19283140
- 82cook 초보운전 글(위 표): 31개 댓글 중 11개가 초보 때는 음악을 안 튼다고 답함. 음악 조작이 집중을 깨기 때문.
- 연결 근거: 터치 조작이 반응시간을 늘린다는 TRL 연구(user-evidence.md 2장). Millet 외(2019)도 곡 바꾸기·채널 찾기·볼륨 조작을 음악 청취가 해로운 경로로 정리함(5장).

### P2. 동승자·가족과의 선곡 충돌 (아이 동요 포함)
- 클리앙 육아당 「차에서 동요 들을 때 팁」(2018-08-15, 조회 1,900, 댓글 9): 아이가 계속 동요를 틀어 달라고 해서 부부가 대화를 못 함. 해결책으로 **밸런스·페이더를 아이 쪽 스피커로만 돌리는 방법**을 공유함. 작성자는 동요가 지겨워서 스트리밍으로 더 듣고 싶지 않다고 함. 댓글에서는 이 기능이 없는 차도 있다는 논의가 이어짐. — https://www.clien.net/service/board/cm_baby/12474427
- 클리앙 굴러간당 PV5 글(2025-06-15, 조회 17.0k, 댓글 113): 음악은 대부분 스트리밍으로 듣지만, 아이 동요 모음은 MP3로 따로 들고 다닌다는 댓글. **[검색 목록 발췌]** — https://www.clien.net/service/board/cm_car/19004890
- 82cook 「차에서 음악 크게 트세요?」(2020-09-19, 조회 2,363, 댓글 16): 고등학생 자녀가 블루투스로 자기 폰을 연결해 가족이 아이 취향 음악을 듣는다는 댓글. 남편이 큰 볼륨을 좋아한다는 본문. — https://www.82cook.com/entiz/read.php?bn=15&num=3082893
- 클리앙 모두의공원(2011-08-07, 조회 1,752, 댓글 8): 남의 차를 타면 운전자가 동승자의 선곡을 무시하고 자기 노래를 틀면서 운전 훈수까지 둔다는 불만. 댓글에서는 와이퍼까지 손대는 배우자 때문에 다툰다는 이야기도 나옴. — https://www.clien.net/service/board/park/1910824
- 클리앙 「장거리 운전 때 동승자가 어떻게 해주면 좋나」(2019-07-11, 조회 2,817, 댓글 37): 가장 많은 답은 "자는 게 최고". 음악 관련 댓글은 4개로, 운전자 취향에 맞춘 선곡이나 DJ 역할을 높게 평가함. — https://www.clien.net/service/board/park/13699962
- 클리앙 「자동화 + 미러링」(2026-04-04, 조회 3,161, 댓글 1): 시동을 걸면 티맵이 자동으로 켜지고, 동승자가 와이파이로 자기 폰에서 음악 위젯을 원격 조작해 곡을 넘기거나 고르게 한 설정을 소개함. **운전자는 운전만 하고 선곡은 동승자에게 넘기려는 수요**를 보여줌. — https://www.clien.net/service/board/cm_car/19172777
- 해외: 동승자가 차 안 대기열에 곡을 넣는 Spotify Jam이 2025-07 Android Auto에 들어왔습니다(4장). 부모 블로그에도 "누가 언제 DJ를 할지 정해 두라"는 조언이 흔합니다(https://www.apartmenttherapy.com/best-playlists-podcasts-audiobooks-for-family-car-rides-37516293). **[검색 발췌]**

### P3. 볼륨·안내 음성 간섭
- 82cook(2020-09-19): 음악 볼륨을 올리면 내비 안내 음성도 같이 커져서 크게 틀 수 없다는 댓글. 차 안에서 오디오 켜는 법을 몰라 DMB 잡음만 났다는 댓글도 있음.
- 클리앙 「기아 커넥트 내장 내비 쓰세요?」(2026-08-31, 조회 4,739, 댓글 30): 내장 내비의 장점으로 음악 볼륨 조절이 쉽다는 점이 언급됨. **[검색 목록 발췌]** — https://www.clien.net/service/board/cm_car/19256352
- Pleos 정책도 앱이 오디오 포커스를 반드시 처리해야 한다고 정합니다(안내 음성이 나오면 일시정지 또는 볼륨 조정). **[내부 문서]** `docs/pleos-docs/01-planning-policy-design.md` 14번

### P4. 추천 피로·반복·취향 이탈
- 클리앙 출퇴근 글(2021): 추천 스트리밍이 원치 않는 장르로 흘렀다는 댓글, 시사 라디오가 스트레스라 음악으로 바꿨다는 댓글.
- 해외 조사 기사 중에 같은 곡을 반복해서 지친다는 언급이 있음(https://www.bumper.co/blog/most-popular-driving-songs). **[검색 발췌]**

### P5. 졸음·장거리
- 클리앙 2014 글: 여러 댓글이 음악의 목적을 졸음 방지로 봄. 졸음을 막으려고 라디오 토크쇼를 권한 댓글도 있음.
- 클리앙 「부산 운전 어려운가요?」(2026-10-17, 조회 3,338, 댓글 61): 까다로운 구간에서는 음악을 끄고 창문을 연다는 언급. **[검색 목록 발췌]** — https://www.clien.net/service/board/cm_car/19278750
- 연구: 음악의 졸음 억제 효과는 짧고(15~25분), 대화형 과제가 더 낫다는 결과가 있습니다(5장).

### P6. 로그인·구독 연동 장벽 (내장 앱)
- 기아 커넥트 스트리밍은 유료 구독, 기아 계정, 차량 프로필을 모두 연동해야 합니다(1-2). Pleos 정책상 **미디어 앱의 로그인·설정·검색은 주행 중에 막힙니다**(`UX_RESTRICTIONS_NO_SETUP`·`NO_KEYBOARD`·`NO_FILTERING`). **[내부 문서]** `01-planning-policy-design.md` 2장. 결국 처음 설정은 정차 중에 끝내야 합니다.
- 차량 내 멜론 로그인 불편을 직접 다룬 최신 후기는 찾지 못했습니다.

### P7. 기타 (근거 약함)
- 라디오 앱 광고가 과하다는 불만(클리앙 라디오 웹앱 소개 글). **[검색 발췌]**
- 연결 끊김, 음질 불만은 이번 조사에서 차량 맥락으로 확인한 글이 거의 없습니다. 삼성 커뮤니티에 업데이트 후 라디오를 듣다가 폰 미디어로 자동 전환된다는 글이 있음. **[검색 발췌]** — https://r1.community.samsung.com/t5/%EA%B0%A4%EB%9F%AD%EC%8B%9C-s/%EC%B0%A8%EC%97%90%EC%84%9C-%EB%9D%BC%EB%94%94%EC%98%A4-%EB%93%A3%EB%8A%94%EB%8D%B0-%EB%B8%94%EB%A3%A8%ED%88%AC%EC%8A%A4%EB%A1%9C-%EC%9E%90%EB%8F%99-%EC%97%B0%EA%B2%B0%EB%90%98%EB%8A%94-%ED%98%84%EC%83%81/m-p/33331672/highlight/true

**페인포인트 강도(정성 판단)**: 조작 위험(P1)과 동승자 충돌(P2)은 반복해서 나오고 해결책을 직접 만들어 쓰는 사람(밸런스 조절, 미러링 자동화, MP3 따로 보관)까지 있습니다. 반면 "음악이 분위기와 안 맞는다"는 불만은 이번 커뮤니티 표본에서 거의 보이지 않았습니다. **MooDrive의 핵심 가정(상황에 안 맞는 음악이 문제)은 커뮤니티 근거가 약합니다.**

---

## 3. 제품·시도 사례와 교훈

| 사례 | 내용 | 결과 | 교훈 | 출처 |
|---|---|---|---|---|
| Spotify Car Thing | 2021-04 출시한 차량용 리모컨 하드웨어(89달러, 재고 정리 때 49.99달러) | 2022-07 생산 중단. 공식 사유는 "제품 수요와 공급망 문제". 3,140만 달러 손실. 2024-12-09 작동 중단, 2025-01-14까지 환불 | 폰·차량 플랫폼이 이미 있는 자리에 별도 기기를 얹으면 수요가 약함 | https://routenote.com/blog/the-spotify-car-thing-discontinued/ , https://www.musicbusinessworldwide.com/spotifys-car-thing-officially-non-operational-less-than-three-years-after-it-rolled-out-in-the-us **[검색 발췌]** |
| Spotify Car View → Car Mode | Car View 2021-10 삭제 → Car Mode 2022-03 도입 → 2024 봄 삭제. 고객지원은 "더 나은 차량 경험을 주지 못했다"고 답함. 삭제 후 앱 리뷰·X에서 불만 급증 | 종료 | 앱 안의 "차량 모드"는 플랫폼(CarPlay·AA)에 밀림. 다만 기능을 없애면 기존 사용자는 반발함 | https://www.enterpret.com/blog/spotifys-driving-features-a-bump-in-the-roadmap (2024-05-21) **[원문]** |
| Spotify Jam on Android Auto | 2025-07. 차 화면 QR로 동승자가 대기열에 곡 추가. 운전자(호스트)가 통제하고 게스트를 내보낼 수 있음. 호스트는 Premium 필요 | 운영 중 | **동승자 선곡 충돌은 이미 대형 서비스가 푸는 문제**. 국내 주력인 멜론·지니에 같은 기능이 있는지는 확인 못 함 | https://www.androidauthority.com/spotify-android-auto-jam-update-3574297 , https://routenote.com/blog/spotify-jam-rolls-out-on-android-auto-the-future-of-car-infotainment/ **[검색 발췌]** |
| Waymo × Spotify / YouTube Music | 2025-08. 로보택시에서 QR·앱으로 자기 계정 연결, Jam 지원, EQ 조정. 이후 YouTube Music 추가 | 운영 중(피닉스·SF·LA) | 차량 쪽은 "음원 계정을 차에 연결하는 다리" 역할만 하고 큐레이션은 음원사에 맡김 | https://www.musicbusinessworldwide.com/waymo-integrates-spotify-into-robotaxis-across-three-us-cities/ **[검색 발췌]** |
| Mercedes MBUX Sound Drive (will.i.am) | CES 2024 공개. 가속·제동·조향·회생제동에 따라 곡의 레이어가 실시간으로 바뀜. AMG 일부 차종 | 출시. 사용 지표는 못 찾음 | 주행 데이터로 음악을 바꾸는 시도는 이미 있음. 리뷰어는 정체 구간에서 거슬릴 수 있다고 했고, CTO는 과속을 보상하지 않는다고 밝힘. 스템을 따로 만든 전용 곡이 필요해서 카탈로그가 좁음 | https://insideevs.com/news/703693/mercedes-sound-drive-ces/ , https://www.axios.com/local/miami/2024/12/05/will-i-am-mercedes-sound-drive-miami **[검색 발췌]** |
| Endel × Mercedes-Benz Group Research | 2021-07 파일럿. 날씨·속도·도로 유형으로 사운드스케이프를 생성해 운전자를 차분하고 깨어 있게 함. Alexa Auto에서는 이동 속도에 따라 Focus/Relax 추천 | 파일럿. 양산 여부 미확인 | **맥락 인식 자동 오디오의 직접 선례.** 노래가 아닌 생성형 사운드라 라이선스 문제를 피함 | https://car.endel.io , https://hypebeast.com/2021/7/mercedes-benz-car-experience-technology-endel , https://developer.amazon.com/en-US/blogs/alexa/alexa-auto/2021/10/Endel-AI-powered-Soundscapes-for-Your-Drive **[검색 발췌]** |
| 기아 R.E.A.D. | CES 2019. MIT 미디어랩과 함께 생체신호·표정으로 감정을 인식해 실내(소리·향·조명)를 조정. 음악에 반응하는 진동 시트 | 콘셉트 | 현대차그룹 자신이 이미 "감정 적응 실내"를 보여줌. 심사위원에게 새롭지 않을 수 있음 | https://press.kia.com/ie/en/home/media-resouces/press-releases/2018/Kia_prepares_for_post_autonomous_driving_era.html **[검색 발췌]** |
| 현대차그룹 e-ASD | 그래뉼러 합성으로 모터 rpm·부하에 맞춘 가상 주행음. EV6 테마 3종, GV60 마그마 e-ASD+ | 양산 | 주행 데이터 기반 사운드는 OEM이 직접 하는 영역 | https://www.hyundaimotorgroup.com/ko/story/CONT0000000000085370 **[검색 발췌]** |
| Gleo AI (Pleos Connect) | 음악 앱이 연동하면 "멜론 앱에서 아이유 노래 틀어줘" 같은 음성 검색·재생이 됨. 앱 핵심 기능 3~5개를 음성 기능으로 추가 가능. Media3 표준 앱은 스키마 없이 연동되도록 업데이트 예정 | 플랫폼 기본 | **음성 선곡과 AI 비서는 플랫폼이 이미 제공함.** 같은 기능을 따로 만들면 중복 | `docs/pleos-docs/raw/docs__pleos-only__vehicle-app-developer-guide__music-audio-app-developement-guide.md` **[내부 문서]** |
| 멜론 (Pleos·현대기아) | 2022 OTA로 32개 차종, 2026-09 Pleos 앱 마켓 입점. 취향 기반 추천 | 운영 중 | 큐레이션 자체는 음원사가 지키는 영역 | user-evidence.md, venturesquare(위) |
| Spotify AI DJ | 영어 중심으로 시작해 스페인어·불어·독어·이탈리아어·포르투갈어로 확장. 한국어는 개발 중 | 한국 미출시 | "AI DJ 음성"은 1년 안에 대형 서비스가 한국어로 낼 가능성이 있음 | 1-2 참고 **[검색 발췌]** |
| Android Auto + Gemini | 기분·상황을 말하면 재생목록 생성 | user-evidence.md에 정리됨 | 요청형. 차량 신호와 연동하지 않음 | user-evidence.md |

**공통 교훈**
1. **음원사와 플랫폼이 이기는 구조입니다.** 차량 전용 음악 UI·기기는 오래 못 갔고, 계정 연결·대기열 공유처럼 플랫폼에 붙은 기능은 남았습니다.
2. **맥락 자동 오디오는 생성형·전용 음원으로 했습니다**(Endel, Sound Drive). 기존 상용 카탈로그를 맥락에 맞춰 자동으로 고르는 서드파티 사례는 찾지 못했습니다. 라이선스와 API 접근 때문으로 보입니다(6장).
3. **고급차 무드 기능의 실제 사용 데이터는 공개된 것이 없습니다.** 그대로 쓰기에는 근거가 부족합니다.

---

## 4. 연구 결과 (user-evidence.md 3-2·3-3을 넘어서)

### 4-1. 음악과 운전 수행: 근거가 섞여 있음
- **Millet, Ahn, Chattah (2019), 「The impact of music on vehicular performance: A meta-analysis」, *Transportation Research Part F* 60:743–760 [원문 PDF 초록]**: 118편을 검토해 12편을 분석했습니다. 음악 청취는 충돌과 종방향 제어(속도·차간 유지)에 **통계적으로 유의하게 해로웠습니다.** 직관과 달리 **작은 볼륨**에서 해로운 효과가 나타났고, **템포는 유의한 차이가 없었습니다.** 해로운 경로로 곡 바꾸기·채널 찾기·볼륨 조작에 따른 주의 분산과 경적·사이렌 소리 가림을 정리했습니다. — https://doi.org/10.1016/j.trf.2018.10.007
- **Ghojazadeh 외 (2023), *Health Promotion Perspectives*, 체계적 문헌고찰·메타분석(19편) [검색 발췌]**: 음악은 평균 반응시간을 줄였지만 각성과 정신적 부하를 높였습니다. 큰·중간 볼륨은 평균 주행속도를 높였고 작은 볼륨은 낮췄습니다. 결론은 "일부 지표에는 해롭고 여러 지표에는 이롭다"이며, 대부분 고소득 국가의 시뮬레이터 연구라는 한계가 있습니다. — https://pmc.ncbi.nlm.nih.gov/articles/PMC10790125/
- **Ünal, de Waard, Epstude, Steg (2013), *TRF* 21:52–65 (n=47) [검색 발췌]**: 단조로운 차량 추종 과제에서 음악은 수행을 해치지 않았고, 각성이 올라가 일부 지표는 좋아졌습니다. 볼륨(큼·보통)과 상관없이 각성이 올랐습니다. 혼잡한 도로에서는 운전자가 소리를 차단하는 식으로 스스로 보상한다고 봤습니다. — https://research.rug.nl/en/publications/driving-with-music-effects-on-arousal-and-performance/
- **Brodsky & Slor (2013), *Accident Analysis & Prevention* 59:382–393 (17~18세 초보 85명, 실차 40분 주행 6회) [검색 발췌]**: **운전자가 좋아하는 음악**을 들을 때 기분은 가장 좋았지만, 심각한 판단 착오·위반·공격적 운전도 가장 많았습니다. 안전을 고려해 고른 배경음악(이지리스닝, 소프트록, 라이트재즈) 조건이 더 나았습니다. 27명은 구두 경고, 17명은 사고 방지를 위해 조향·제동 개입이 필요했습니다. — https://www.sciencedaily.com/releases/2013/08/130823091347.htm
  - 시사점: "선호곡을 틀면 각성에 좋다"는 설계 가정은 초보·젊은 운전자에게는 반대일 수 있습니다.

### 4-2. 기분·분노 조절
- **van der Zwaag 외 (2013), 「Using music to change mood while driving」, *Ergonomics* 56(10):1504–1514 (n=28) [검색 발췌]**: 고부하 주행에서 본인이 고른 밝은 음악을 듣다가 차분한 음악으로 바꿀 때, **점진적 전환보다 급전환**이 생리적 각성을 더 낮추고 사고도 적었습니다. 2012년 선행 연구(n=19)에서는 본인이 고른 음악이 기분을 유지하고 호흡수를 낮추면서 수행을 해치지 않았습니다. — https://research.tue.nl/en/publications/using-music-to-change-mood-while-driving/
  - 시사점: 자동으로 분위기를 바꾸는 설계라면 크로스페이드보다 명확한 전환이 나을 수 있습니다(시뮬레이터 연구 1편).
- **Landry, FakhrHosseini, Jeon 외 (2014 AutoUI), 「If You're Angry, Turn the Music on」 (n≈53)**: 분노를 유도한 운전자에게 행복한·슬픈 기악곡을 틀어주는 설계입니다. 결과 원문은 확인하지 못했습니다. 후속 연구(2016 AutoUI, n=61)에서는 **본인이 고른 음악**을 들은 분노 운전자가 더 공격적으로 운전했습니다. 2019 저널 논문에서는 음악 없이 운전한 분노 운전자가 수행은 낮고 노력은 컸습니다. **[검색 발췌]** — https://vtechworks.lib.vt.edu/items/affe672f-d7dd-479e-afc3-f87d70e3af77/full , https://dblp1.uni-trier.de/rec/conf/automotiveUI/FakhrhosseiniJ16.html

### 4-3. 피로·졸음
- **Orsini, Baldassa, Grassi, Cellini, Rossi (2024), 「Music as a countermeasure to fatigue: A driving simulator study」 (n=60, 단조로운 고속도로 50분) [검색 발췌]**: 20분 동안 음악 없이 달린 뒤 30분간 본인이 고른 플레이리스트를 들려줬습니다. 객관적 피로 지표가 개선됐지만 **효과는 15~25분만 갔습니다.** 탐색 분석에서는 강도·템포·춤추기 좋은 정도가 높고 기악 비중이 낮은 곡이 더 효과적이었습니다. 저자들은 음악을 "단기 대응책"으로 봤습니다. — https://core.ac.uk/works/168161075
- **Trumbo 외 (2017), 「Name that tune: Mitigation of driver fatigue via a song naming game」, *Accident Analysis & Prevention* 108:275–284 [검색 발췌]**: 피로가 시작될 때 **노래 제목 맞히기 게임**을 하게 했더니, 두 번째 세션의 시뮬레이터 수행이 처음 상태 수준 이상으로 돌아왔습니다. — https://www.sandia.gov/research/publications/details/name-that-tune-mitigation-of-driver-fatigue-via-a-song-naming-game-2017-11-01/
  - 시사점: **듣기만 하는 음악보다 음성으로 참여하는 오디오 과제**가 피로 대응에 근거가 있습니다. Pleos의 온디바이스 TTS·STT와 잘 맞습니다.
- 감정 음악과 동공 측정(2025, *Frontiers in Psychology*): 긍정적인 음악이 피로가 오는 시점을 늦췄고, 피로가 온 뒤에도 각성을 도왔습니다. **[검색 발췌]** — https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2025.1647424/full
- 2019 *i-Perception* 실차 연구(16명): 빠른 템포는 피로를 덜어 주지만 오래 운전하면 주의를 떨어뜨렸고, 중간 템포를 권장했습니다. **[검색 발췌]** — https://pmc.ncbi.nlm.nih.gov/articles/PMC6636224

### 4-4. 차량 내 추천과 자동 변경 수용성
- **Baltrunas 외 (2011), 「InCarMusic」, EC-Web, LNBIP 85:89–100**: 교통 상황이나 운전자 기분 같은 맥락을 반영한 차량 음악 추천입니다. 맥락별 평점을 모으는 비용이 크다는 문제를, 사용자에게 맥락이 선택에 주는 영향을 직접 묻는 방식으로 줄였습니다. 이후 연구에서 쓰이는 데이터셋이 됐습니다. **[검색 발췌]** — https://link.springer.com/doi/10.1007/978-3-642-23014-1_8
- **Bader, Siegmund, Woerndl (2011), AutoUI, 「A study on user acceptance of proactive in-vehicle recommender systems」 [원문: 초록]**: 실제 주행 중에 TAM으로 평가했습니다. 능동 추천은 도움이 된다고 받아들여졌고 거슬리지 않았습니다. 다만 **명확한 정보 전달과 신뢰가 수용의 핵심**이고, 사용 편의성이 수용에 크게 작용했습니다. — https://portal.fis.tum.de/en/publications/a-study-on-user-acceptance-of-proactive-in-vehicle-recommender-sy/
- Wizard-of-Oz 연구(24명, 연도 미확인): 운전자는 **음악을 듣는 중에는 AI의 능동 개입을 더 꺼렸습니다.** **[검색 발췌]** — https://vufind-test.katalog.k.utb.cz/SummonRecord/FETCH-LOGICAL-p1586-4e610de455f5eeb7a8837fb425172230ae4e0ebc49a90cec4b4910beca5c45d63
- 이번 조사에서는 "자동 플레이리스트 변경을 운전자가 받아들이는지"를 직접 측정한 연구를 찾지 못했습니다. 이 주제는 근거가 비어 있는 상태입니다.

---

## 5. 사업·라이선스 현실

### 5-1. 기존 음원 서비스 API로 할 수 있는 것
- **Spotify Web API (2024-11-27 변경) [검색 발췌, 공식 블로그 링크 확인]**: 신규 앱은 Recommendations, **Audio Features(템포·에너지·댄서빌리티 등)**, Audio Analysis, Related Artists, 추천·편집 플레이리스트, 30초 미리듣기 URL을 쓸 수 없습니다. 기존에 확장 권한을 받은 앱만 예외입니다. — https://developer.spotify.com/blog/2024-11-27-changes-to-the-web-api , https://techcrunch.com/2024/11/27/spotify-cuts-developer-access-to-several-of-its-recommendation-features
  - **MooDrive식 "템포·에너지로 곡 고르기"는 신규 앱이 스포티파이 데이터로 할 수 없습니다.**
- **Spotify 확장 권한 기준(2025-05-15부터) [원문]**: "확립되고 확장 가능하며 영향력 있는 사용 사례"에만 줍니다. — https://developer.spotify.com/blog/2025-04-15-updating-the-criteria-for-web-api-extended-access
  - "법인 + 출시된 서비스 + MAU 25만 이상" 기준은 2차 자료에만 있고, 공식 글에서는 확인하지 못했습니다. — https://dev.to/leemartin/the-state-of-spotify-web-api-report-2025-4gh3
- **YouTube API 정책 [검색 발췌]**: 오디오·비디오 분리, 오디오만 추출하는 서비스, 창을 닫거나 최소화한 상태의 백그라운드 재생을 금지합니다. — https://developers.google.com/youtube/terms/developer-policies-guide
- **멜론·지니**: 공개 개발자 API는 확인하지 못했습니다. 차량 연동은 완성차와의 B2B 제휴(현대기아 OTA 탑재, 기아 커넥트 계정 연동)로 이뤄졌습니다.
- **다른 앱 재생 제어(AAOS)**: Pleos 문서는 Gleo AI가 MediaAgent(Media3)로 음악 앱을 제어하는 방식만 설명합니다. 서드파티 앱이 멜론 같은 다른 앱의 MediaSession을 조회·제어할 수 있는지는 문서에 없습니다. Android에서 다른 앱의 미디어를 제어하는 권한(`MEDIA_CONTENT_CONTROL`)은 일반적으로 시스템·특권 앱용이고, 대안인 알림 리스너 권한이 Pleos에서 허용되는지는 **확인하지 못했습니다(공식 문의 필요).**

### 5-2. 국내 저작권 구조
- 개인용 스트리밍에는 전송사용료, 매장·공개 재생에는 공연권료가 따로 붙습니다. 스트리밍 상품의 창작자 배분 비율은 징수규정 개정으로 60%에서 65%로 올랐습니다(문체부). **[검색 발췌]** — https://www.mcst.go.kr/kor/s_notice/press/pressView.jsp?pSeq=16742 , https://maily.so/kimlou/posts/cc1908
- 대법원은 매장음악 업체가 웹캐스팅으로 받은 음원을 매장에서 재생한 행위를 공연권 침해로 봤습니다. **[검색 발췌]** — https://www.lawtalk.co.kr/posts/158227
- 직접 음원을 서비스하려면 음악저작권 신탁단체들, 실연자·음반제작자 단체, 유통사와 계약해야 합니다. 공모전 팀이 현실적으로 할 수 있는 일이 아닙니다(구체 요율은 이번에 확인하지 못함).

### 5-3. 독립 앱이 현실적으로 고를 수 있는 모델

| 모델 | 가능성 | 비고 |
|---|---|---|
| 기존 음원 앱에 연결(딥링크·플레이리스트 열기·재생 제어) | 중간~낮음 | 앱 간 제어 권한이 불명확함(5-1). 공모전에서는 "연동 시나리오"로만 보여줄 수 있음 |
| 생성형·로열티프리 음원(Mubert API, Endel류) | 중간 | Mubert는 로열티프리지만 상업 이용은 상위 요금제가 필요하고, 가격은 2차 자료마다 다름(49~199달러/월). 차량 사용 조건은 확인 못 함 — https://mubert.com/api **[검색 발췌]** |
| 직접 만든·합성 음원(MooDrive 66곡) | 데모용으로만 가능 | 심사에서 "실서비스 경로"를 물으면 답하기 어려움 |
| 라디오 스트림 | 낮음(중복) | Pleos에 Radioline, 래디오오토, SBS 고릴라가 이미 있음. 방송사 스트림 재송신은 허락이 필요 |
| 팟캐스트(RSS)·오디오북 | 중간 | 팟캐스트 RSS는 공개된 경우가 많음. Pleos 하위 카테고리(팟캐스트·오디오북)가 정의돼 있음 |
| TTS로 만든 말 콘텐츠(브리핑, 퀴즈, 안내) | 높음 | 라이선스 부담이 적고 차량 신호·내비와 결합하기 쉬움. 온디바이스 TTS·STT 사용 |
| 사용자 로컬 음원(USB·다운로드) | 낮음 | 이용 감소 추세. 일부 부모는 동요를 MP3로 따로 들고 다님(P2) |

### 5-4. 수익화 메모
- 국내 음원 플랫폼은 저작권료 구조 때문에 무료(광고) 요금제를 도입하기 어렵다는 보도가 있습니다(ceoscoredaily 2026-07-29).
- 라디오 광고 인지율 69.2%, CM송 같은 음악형 광고가 가장 기억에 남는다는 응답 43.7%(아젠다북 2025). 오디오 광고는 받아들여지는 편입니다. 다만 Pleos는 미디어 앱이 알림에 광고를 넣는 것을 금지합니다. **[내부 문서]**
- 공모전 사업성 심사는 타깃, 예상 MAU, 리텐션, 확장 계획을 봅니다. **[내부 문서]** `docs/pleos-docs/contest/guide_judging-awards.md`

---

## 6. 기회와 레드플래그

### 6-1. 레드플래그 ("그냥 음악 플레이어"로 보이는 지점)
1. **Pleos 심사 정책**: 음악·오디오 카테고리는 오디오 재생이 핵심 기능이어야 합니다. **음악을 단순 보조 기능으로 넣은 앱은 즉시 반려**됩니다. 반대로 재생이 핵심이면 MBS/Media3, 스티어링 휠 키, 미디어 라이브러리 연동이 필수이고 **자동 재생은 금지**됩니다(앱 연결이나 프로필 전환 때도 사용자 동작이 있어야 재생). **[내부 문서]** 음악 앱 개발 가이드
   - 시동을 걸면 알아서 음악이 나오는 시나리오는 정책과 충돌할 수 있습니다. 이미 재생 중일 때 곡을 바꾸는 것은 별개 문제지만, 심사 해석은 확인이 필요합니다.
2. **중복**: 취향 추천(멜론), 음성 선곡(Gleo AI), 기분 기반 재생목록 생성(Gemini/AA), 동승자 대기열(Spotify Jam), 라디오(앱 3종)가 이미 있습니다. 이 중 하나만 차량 신호로 "조금 더 자동화"하면 심사위원은 멜론에 붙일 기능으로 볼 가능성이 큽니다.
3. **음원 공급 경로가 없음**: 상용 카탈로그를 맥락에 맞춰 자동으로 고르려면 오디오 특성 데이터와 재생 권한이 필요한데, 신규 서드파티 앱에는 둘 다 막혀 있습니다(5-1).
4. **안전 주장의 근거가 약함**: 템포 상한은 메타분석에서 지지받지 못했습니다. 선호곡이 오히려 위험할 수 있다는 연구도 있습니다. "음악으로 졸음을 막는다"도 효과가 15~25분입니다. 안전을 전면에 내세우면 반박당하기 쉽습니다.
5. **현대차그룹 내부 선례**: 기아 R.E.A.D.(감정 인식 실내), e-ASD(주행 연동 사운드), 제네시스 무드 큐레이터가 있습니다. "상황 인식 무드"만으로는 새롭지 않습니다.
6. **자동 개입 수용성**: 음악을 듣는 중에는 능동 개입을 더 꺼린다는 연구가 있습니다. 왜 바뀌었는지 설명이 없으면 신뢰를 잃습니다(Bader 2011).

### 6-2. 빈 곳 (근거와 함께, 우선순위 순)

| 기회 | 근거 | 쓸 수 있는 Pleos 신호·기능 | 중복 위험 |
|---|---|---|---|
| **A. 동승자·가족 오디오 중재**: 아이가 타면 가족 모드로 전환, 선곡 차례 정하기, 동요 시간 제한, 앞·뒷좌석 균형 | P2(밸런스 조절 꼼수, 동요 MP3, 동승자 선곡 갈등), 호주 설문(운전자 33% vs 동승자 28%), 클리앙 미러링 자동화 | 좌석 점유(뒷좌석 감지), 도어, 오디오 포커스, (확인 필요) 오디오 밸런스·페이더 제어 | Spotify Jam이 대기열 공유는 함. 멜론·지니의 차량 공유 기능 유무는 미확인. 가족 규칙·아이 대응은 비어 있음 |
| **B. 졸음 구간 대화형 오디오**: 장거리·심야에 음악만 바꾸지 않고 음성 퀴즈, 노래 제목 맞히기, 휴식 제안 | Orsini 2024(음악 효과 15~25분), Trumbo 2017(노래 맞히기 게임), 도로공사 2시간 휴식 권고와 졸음 사망 비중(user-evidence 6장) | 온디바이스 TTS·STT, NaviHelper 남은 시간·휴게소, HVAC(환기·내기 순환 → CO₂), 주행 시간 | Gleo AI가 범용 대화를 하지만, 피로 대응형 게임은 확인하지 못함. 노래 맞히기에 실제 음원을 쓰면 라이선스 문제가 생기므로 가사·퀴즈 TTS 중심으로 설계 필요 |
| **C. 남은 시간에 맞춘 말 콘텐츠 큐**: 도착까지 남은 시간에 맞춰 팟캐스트 회차·뉴스 브리핑·오디오북 챕터를 구성 | 출근 시간대 청취 최다, 라디오·팟캐스트 선호(커뮤니티 집계), 말 콘텐츠의 라이선스 부담이 상대적으로 적음 | NaviHelper 남은 시간·거리, 경유지, TTS | Pleos에 팟캐스트 전용 앱은 확인 못 함(픽클의 정체 미확인). 라디오 앱과는 겹치지 않음 |
| **D. 출발·정체 오디오 루틴**: 정차 중에 계정·재생목록·볼륨을 미리 세팅하고, 정체 때는 차분한 소리와 조명을 맞춤 | Pleos는 주행 중 로그인·설정·검색을 막음, 차량 74%가 마지막 음원을 자동 재개, 정체 스트레스 연구(van der Zwaag), Endel 선례 | 기어·주행 상태(UX restriction), 앰비언트 라이트, NaviHelper 정체 정보 | 현대기아 "자연의 소리", 무드 큐레이터와 일부 겹침 |

**종합 판단**: "음악을 더 잘 골라준다"는 축은 멜론·스포티파이·Gleo·Gemini와 정면으로 겹치고 음원 접근도 막혀 있습니다. 대신 **음악 바깥의 문제, 즉 누가 조작하는지(동승자·아이), 졸음 대응 상호작용, 남은 시간에 맞춘 말 콘텐츠**는 차량 신호가 핵심이고 라이선스 부담이 작습니다. 심사 기준인 "차량특화성"과 "Pain Point 명확성"에도 더 잘 맞습니다.

---

## 7. user-evidence.md 정정·보강 사항

| # | 기존 내용 | 정정·보강 | 근거 |
|---|---|---|---|
| 1 | 3-2: 템포가 빠를수록 위험하므로 "비·정체 구간 110 BPM 상한" 권고 | **약화 필요.** 메타분석(12편)에서 템포는 유의한 차이가 없었고, 작은 볼륨과 조작 행위가 해로운 요인이었음. 템포 상한은 "가설" 수준으로만 써야 함 | Millet 외 2019 (4-1) |
| 2 | 3-3: "심야 피로 구간에서만 선호곡 위주의 중·빠른 템포로 각성 유도" | 선호곡은 초보 운전자의 위반·공격 운전을 가장 많이 늘렸고(Brodsky & Slor 2013), 분노 상태에서도 공격성을 높였음(FakhrHosseini 2016). 피로 억제 효과는 15~25분(Orsini 2024). 휴식 안내와 대화형 과제를 함께 써야 함 | 4-1, 4-2, 4-3 |
| 3 | 기획서 문장 8: "기존 무드 기능은 모두 운전자가 직접 모드를 골라야" | **틀린 주장.** Endel×Mercedes 파일럿(날씨·속도·도로 유형 자동 반영), MBUX Sound Drive(주행 조작에 실시간 반응), 기아 R.E.A.D.(감정 인식 자동 조정)가 있음. "양산 차종의 대중 기능 중에는"처럼 범위를 좁혀야 함 | 3장 |
| 4 | 4장 Spotify Car View 2021 종료 | Car Mode(2022-03 도입)도 2024 봄에 삭제됨. Car Thing 공식 중단 사유는 "제품 수요·공급망", 손실 3,140만 달러 | 3장 |
| 5 | 1장 지니뮤직 "2025년 초 발표" | 정확한 발표일은 **2025-04-16**, 데이터는 2024년. 30·40대 70%, 남성 62%는 원문으로 확인함 | 1-2 |
| 6 | 1장 Infinite Dial 2025: CarPlay 22%, Android Auto 13% | Android Auto를 10%로 인용한 자료(RAB 인용 검색 발췌)도 있어 수치가 엇갈림. 원 보고서 확인 필요. 최신 지표로 Edison Share of Ear 2026 Q1(AM/FM 55%, 스트리밍 16%)과 보유자 83% 실사용 추가 | 1-3 |
| 7 | 검증 못한 항목: "국내 운전자 음악 청취 비율 없음" | 직접적인 음악 청취율은 여전히 없음. 대신 **라디오 청취 장소 1위 = 차량 54.1%**(아젠다북 2025-07, 1,273명)를 국내 대리 지표로 쓸 수 있음 | 1-1 |
| 8 | 4장 경쟁 표에 Pleos 자체 기능 없음 | Gleo AI의 음악 앱 음성 재생과 Pleos 음악 앱 정책(보조 기능 반려, 자동 재생 금지, MBS 필수)을 경쟁·제약 항목에 추가해야 함 | 3장, 6-1 |
| 9 | (없음) | 국내 MAU 판도: 스포티파이가 2026-06에 멜론을 추월. 경쟁·연동 대상을 멜론 하나로 가정하면 안 됨 | 1-2 |
| 10 | 비교표 "Climatune 등" | 영향 없음. 다만 신규 앱은 스포티파이 Audio Features를 못 쓰므로, 날씨·맥락 매칭을 스포티파이 데이터로 구현한다는 계획은 불가 | 5-1 |

---

## 8. 출처

(본문 각 항목 옆에 URL을 적었습니다. 여기에는 주요 출처만 모았습니다.)

**이용 행태**
- 아젠다북 라디오 조사(2025-07): https://www.businesskorea.co.kr/news/articleView.html?idxno=247734
- 지니뮤직 차량 이용(2025-04-16): https://m.bobaedream.co.kr/board/bbs_view/nnews/228997/2/37
- 음악 앱 MAU(2026-07-29): https://www.ceoscoredaily.com/page/view/2026072916262538897
- Pleos 앱 마켓 7종 추가(2026-09-07): https://www.venturesquare.net/1111191/
- 기아 커넥트 스트리밍: https://www.kia.com/kr/kia-connect/service/streaming-game/audio
- Edison Share of Ear 2026 Q1(Jalopnik, 2026-04-13): https://www.jalopnik.com/2147090/radio-wins-in-cars/
- CarPlay·AA 보유·사용(Radio Ink, 2025-07-31): https://radioink.com/2025/07/31/in-car-audio-behavior-shifts-with-carplay-android-auto-growth/
- 2025 방송매체 이용행태조사 개요: https://news.tf.co.kr/read/economy/2277385.htm

**커뮤니티(공개 글, 의역)**
- https://www.clien.net/service/board/cm_car/14583431 (2020-02-12)
- https://www.clien.net/service/board/cm_car/16501763 (2021-09-15)
- https://www.clien.net/service/board/cm_car/5568261 (2014-02-15)
- https://www.clien.net/service/board/park/19262165 (2026-09-11)
- https://www.clien.net/service/board/cm_baby/12474427 (2018-08-15)
- https://www.clien.net/service/board/park/13699962 (2019-07-11)
- https://www.clien.net/service/board/park/1910824 (2011-08-07)
- https://www.clien.net/service/board/kin/5313892 (2013-12-19)
- https://www.clien.net/service/board/cm_car/19172777 (2026-04-04)
- https://www.82cook.com/entiz/read.php?bn=15&num=3658189 (2023-06-09)
- https://www.82cook.com/entiz/read.php?bn=15&num=3082893 (2020-09-19)

**제품·사례**
- Spotify Car Mode 삭제 분석(2024-05-21): https://www.enterpret.com/blog/spotifys-driving-features-a-bump-in-the-roadmap
- Car Thing 중단: https://routenote.com/blog/the-spotify-car-thing-discontinued/ , https://www.musicbusinessworldwide.com/spotifys-car-thing-officially-non-operational-less-than-three-years-after-it-rolled-out-in-the-us
- Spotify Jam on Android Auto: https://www.androidauthority.com/spotify-android-auto-jam-update-3574297
- Waymo × Spotify: https://www.musicbusinessworldwide.com/waymo-integrates-spotify-into-robotaxis-across-three-us-cities/
- MBUX Sound Drive: https://insideevs.com/news/703693/mercedes-sound-drive-ces/ , https://mercedes-amg.com/en/mercedes-amg-mbux-sound-drive
- Endel × Mercedes: https://car.endel.io , https://hypebeast.com/2021/7/mercedes-benz-car-experience-technology-endel
- 기아 R.E.A.D.: https://press.kia.com/ie/en/home/media-resouces/press-releases/2018/Kia_prepares_for_post_autonomous_driving_era.html
- 현대차그룹 e-ASD: https://www.hyundaimotorgroup.com/ko/story/CONT0000000000085370
- Spotify AI DJ 확대: https://www.digitalmusicnews.com/2026/03/18/spotify-ai-dj-expands-to-more-countries/
- Pleos 음악 앱 개발 가이드(내부 사본): `docs/pleos-docs/raw/docs__pleos-only__vehicle-app-developer-guide__music-audio-app-developement-guide.md`

**연구**
- Millet, Ahn, Chattah (2019) TRF 60: https://doi.org/10.1016/j.trf.2018.10.007
- Ghojazadeh 외 (2023): https://pmc.ncbi.nlm.nih.gov/articles/PMC10790125/
- Ünal 외 (2013) TRF 21: https://research.rug.nl/en/publications/driving-with-music-effects-on-arousal-and-performance/
- Brodsky & Slor (2013) AAP 59: https://www.sciencedaily.com/releases/2013/08/130823091347.htm
- van der Zwaag 외 (2013) Ergonomics 56(10): https://research.tue.nl/en/publications/using-music-to-change-mood-while-driving/
- Landry·FakhrHosseini·Jeon (2014, 2016 AutoUI): https://vtechworks.lib.vt.edu/items/affe672f-d7dd-479e-afc3-f87d70e3af77/full
- Orsini 외 (2024): https://core.ac.uk/works/168161075
- Trumbo 외 (2017) AAP 108: https://www.sandia.gov/research/publications/details/name-that-tune-mitigation-of-driver-fatigue-via-a-song-naming-game-2017-11-01/
- Baltrunas 외 (2011) InCarMusic: https://link.springer.com/doi/10.1007/978-3-642-23014-1_8
- Bader 외 (2011) AutoUI: https://portal.fis.tum.de/en/publications/a-study-on-user-acceptance-of-proactive-in-vehicle-recommender-sy/

**라이선스·API**
- Spotify Web API 변경(2024-11-27): https://developer.spotify.com/blog/2024-11-27-changes-to-the-web-api
- Spotify 확장 권한 기준(2025-04-15): https://developer.spotify.com/blog/2025-04-15-updating-the-criteria-for-web-api-extended-access
- YouTube 개발자 정책: https://developers.google.com/youtube/terms/developer-policies-guide
- 문체부 징수규정 개정: https://www.mcst.go.kr/kor/s_notice/press/pressView.jsp?pSeq=16742
- Mubert API: https://mubert.com/api

**후속 확인이 필요한 항목**
- 픽클(Pickle)과 래디오오토(RaiDIO Auto)가 어떤 앱인지(팟캐스트 앱 여부)
- Pleos에서 서드파티 앱이 다른 미디어 앱의 세션을 제어하거나 오디오 밸런스·페이더를 바꿀 수 있는지(운영사무국 문의)
- 방통위 「방송매체 이용행태조사」 2025 원문의 라디오 청취 장소 수치
- 멜론·지니 차량 앱에 동승자 공유(Jam류) 기능이 있는지
- 보배드림·더쿠·에펨코리아·네이버 카페의 동요·선곡 관련 글(이번 수집 도구로는 접근 실패)
