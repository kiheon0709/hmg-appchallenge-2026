# 차량 내 AI 에이전트·음성비서 도메인 리서치

- 작성일: 2026-11-12
- 용도: Pleos Connect 앱 공모전(접수 마감 2026-11-15)에서 "차량 내 에이전트형 앱"을 검토하기 위한 도메인 근거
- 조사 방법: 웹 검색과 원문 열람(서브 에이전트 4개를 병렬로 돌려 수집하고 종합). 공모전 제약은 `docs/pleos-docs/00-contest-and-ecosystem.md`, `02-dev-environment-and-guides.md` 6절, `03-sdk-usage-guides.md` 5절 기준.
- 근거 표기
  - **[원문]** 페이지나 PDF를 직접 열어 확인함
  - **[스니펫]** 검색 결과 요약에서만 봄. 기획서에 인용하기 전에 원문을 다시 확인해야 함
  - **[메타]** 논문 서지 정보(Crossref·dblp)만 확인함
  - **[추론]** 근거를 바탕으로 우리 팀이 해석한 내용
- 수집 한계
  - 네이버 블로그·카페(그랜저/아반떼 동호회)는 크롤러 차단으로 못 봤음
  - damoang.net 403, jdpower.com 보도자료 403, ACM DL·ScienceDirect 원문 다수 차단
  - YouTube는 제목·채널만 확인했고 조회수·댓글은 못 봤음
  - DC인사이드는 댓글 수만 보였고 댓글 본문은 못 봤음
  - 일부 서브 에이전트가 검색 한도(200회)에 도달해 S&P Global·Strategy Analytics·SBD 등은 조사하지 못함

---

## 0. 요약 (10줄)

1. **글레오 AI는 이미 "범용 차량 비서" 자리를 차지했다.** 2026-05-14 더 뉴 그랜저부터 탑재되었다. 할 수 있는 일은 자연어·복합 명령, 좌석 위치 인식, 공조·창문·시트 제어, 웹 검색(뉴스·날씨), 매뉴얼 Q&A, 여행 일정 제안, "감성 대화"다. 10-30 OTA로 경로 주변 장소 제안과 문자 읽기가 더해졌다.
2. **실사용 평가는 "예전보다 낫지만 아직 미완성"이다.**
   - 언론 시승기의 공통 지적은 1~2초 지연, 음악·큰 목소리에서 웨이크워드 실패, 같은 명령인데 결과가 들쭉날쭉함, 주행·ADAS 기능 거부다. 동아일보 체감 이해율은 60~70%였다.
   - 커뮤니티의 분노는 음성 자체보다 **플레오스 탑재로 인한 가격 인상(약 600만 원)과 "유료 베타테스터"라는 인식**에 몰려 있다.
3. **해외 흐름은 세 갈래다.**
   - OEM이 자기 브랜드 비서를 유지하고 뒤의 LLM만 갈아 끼운다. 예: Mercedes(ChatGPT+Gemini 멀티 에이전트), BMW(Alexa+), GM(Gemini에서 자체 OnStar AI로).
   - 중국은 선제 제안(proactive)과 감정형 동반자(NIO NOMI, Xpeng 톈지 6.0)가 앞서 있다.
   - 서구 양산 비서는 여전히 대부분 "물으면 답하는" 반응형이다.
4. **사람들이 실제로 음성으로 하는 일은 좁다.**
   - 미국 2020년 조사(n=1,090)에서 1위는 전화(73%), 다음이 길안내(50%)와 문자(39%)였다. 차량 기능 제어는 3.5%로 최하위권이다.
   - 국내 커뮤니티도 같다. "내비 목적지는 90% 음성"인 반면, 다른 기능은 오인식 때문에 버튼으로 돌아간다.
5. **한국 소비자의 차량 AI 만족도는 가장 낮다.** 컨슈머인사이트 2025 조사에서 "AI 서비스" 만족도는 52%로 8개 기능 중 꼴찌였다. 사용 경험은 9%에 그쳤고, 이용자 다수는 자신이 AI를 쓰는지조차 인지하지 못했다.
6. **선제 발화는 조건부로 환영받는다.**
   - 운전 관련 제안의 수락률은 82.5%(Schmidt 2020, n=42)였다.
   - 반면 "휴식하세요" 제안은 61%가 거절했다.
   - 42명 중 33명이 "주제별로 켜고 끄는 스위치"를 원했다.
   - 선호 패턴은 "강하게 추정해서 제안하되, 실행 전에 확인받기"다(Du 2024).
7. **음성도 인지 부하가 크다.**
   - 핸즈프리 음성 과제도 인지 부하 척도 2~4 수준이고, 끝난 뒤에도 15~27초 동안 잔여 주의 분산이 남는다(AAA·Strayer).
   - 짧은 문장, 한 턴에 한 가지 정보, 1초 안의 응답이 원칙이다.
8. **졸음 대응에는 대화가 효과가 있다.** 다만 "듣기"보다 "대답하기"가 효과적이다. 주관적 졸음 점수는 무처치 7.98에서 대답 과제 5.28로 낮아졌다(Zhang 2025). 시점도 중요해서, 지루한 주행의 후반에 짧게 개입할 때만 효과가 있었다(Atchley & Chan 2011).
9. **우리 제약에서 Gleo와 겹치는 것은 피해야 한다.** 범용 Q&A, 음성 차량 제어, 경로 주변 POI 추천, 문자 읽기, 음악 검색, "말동무 챗봇"이 여기에 해당한다. Gleo는 웨이크워드·차량 제어 권한·좌석 인식을 갖고 있어 이 영역에서는 이길 수 없다.
10. **이길 수 있는 틈은 "특정 상황 하나를 끝까지 책임지는 앱 에이전트"다.**
    - 차량 신호(트립 시간·재실·안전벨트·경고등·남은 시간)로 **앱이 먼저 묻는다**. TTS로 질문한 뒤 STT `request()`로 짧은 답을 받는다(웨이크워드 불필요). 그 결과로 **NaviHelper나 P단 차량 제어로 끝까지 실행**한다.
    - 후보 상황: 장거리 졸음·휴식, 아이 동승, P단 정차·대기, 도착 직전 준비.
    - STT 텍스트를 외부 LLM으로 보내는 것은 [추론] 허용 범위로 보이지만 확인된 해석은 아니다.

---

## 1. 플레이어별 비교 표

표기: 선제 = 차가 먼저 말하거나 제안하는 기능. 감성 = 페르소나·감정 표현. 생태계 = 외부 앱·에이전트 연동. 날짜는 발표일 또는 배포일.

| 플레이어 | 제품 / 시기 | LLM | 핵심 기능 | 선제 | 감성·페르소나 | 생태계·멀티 에이전트 | 평가·논란 |
|---|---|---|---|---|---|---|---|
| **현대차그룹** | 글레오 AI(Pleos Connect). 공개 2026-04-30, 첫 양산 그랜저 2026-05-14, 아반떼 CN8 2026-06 공개, 첫 OTA 2026-10-30 | 단계별로 여러 LLM을 씀. 업체명 비공개. "현재 외부 API 의존" 발언 [스니펫]. 온디바이스+클라우드 하이브리드 | 자연어·복합 명령, 맥락("거기로 가자"), 좌석 위치 인식, 웹 검색, 매뉴얼 Q&A, 여행 일정, 경로 주변 장소, 문자 읽기 | 차량 상태 기반 선제 정보 제공을 표방. "더워"라고 하면 공조 모드 제안 [스니펫] | "조수석의 동반자", 감성 대화 | Gleo Interface(function calling, 앱당 action 3개). 승인제라 공모전에서는 불가 | 8절 참고 |
| 기아(이전 세대) | Kia AI Assistant("Hey Kia"). EV3 2024-05, 유럽 2025-03 | ChatGPT 기반 [스니펫] | 자연어 Q&A, 차량 기능 | — | — | — | — |
| **Mercedes-Benz** | MBUX Virtual Assistant(MB.OS). CES 2024 공개, CLA 2025-07, 2027 C클래스 | ChatGPT-4o + Bing(일반 지식), Google Gemini 기반 Automotive AI Agent(지도·POI) | 다중 턴, 단기 기억, 에이전트 간 맥락 유지, 오프라인 시 기본 명령 | "Predictive"를 표방했지만 리뷰어는 자발적 발화를 못 봄(InsideEVs 2025-12) | 별 모양 아바타가 감정에 따라 색이 바뀜. "친구처럼 공감"을 표방 | 주제별 라우팅 멀티 에이전트(서구 대표 사례) | "중국 비서와 대등"(InsideEVs). 데이터 연결 의존 |
| **BMW** | Intelligent Personal Assistant(Alexa+ 기술). CES 2026, iX3 2026-05 OTA, 2026 하반기 확대 | Amazon Alexa Custom Assistant + Alexa+ | 자유 발화, 다중 경유 경로, 지나가는 명소 질문, 아마존 계정 연동 | 관광 주행 중 명소 설명 정도 | 특별한 페르소나 없음 | Alexa 생태계 | 제스처 컨트롤 폐지(2025-11). "LLM 음성이 대체" |
| **Volkswagen** | IDA + ChatGPT(Cerence Chat Pro). CES 2024, 유럽 2024-06, 미국 2024-09, 2025-08 업그레이드 | ChatGPT(Cerence 경유). 자체 처리가 불가할 때 익명화해 전달 [스니펫] | 다중 턴 대화, **아이에게 이야기 들려주기**(2025-08) | 차세대에서 "능동 서비스" 예고 [스니펫] | — | — | — |
| **Tesla** | Grok(xAI). 2025-07(2025.26), 2026-07 차량 명령 추가, 유럽 확대 | Grok | 초기에는 "차에 명령 불가"였음. 2026년부터 공조·미러·와이퍼·내비. 커뮤니티 테스트에서 명령 170개 중 116개(약 68%) 처리(Motor1 2026-09) | 위치 기반 리마인더 | 성격 선택: Unhinged·Sexy·Kids Story·Therapist 등 [스니펫·펌웨어 분석] | — | 출시 직전 "MechaHitler" 사태(2025-07). 12세 아동에게 부적절한 요청을 했다는 보도(2025-10) [스니펫]. CNBC "명백한 주의 분산"(2026-04) [스니펫] |
| **GM** | Gemini(2025-10 발표, 2026-04 배포. MY2022+ 약 400만 대) → 자체 OnStar AI Assistant(2026-10 발표, 2027 Lyriq) | Gemini, 이후 자체(제공사 비공개) | 메시지 작성, 경유 경로, 정비 알림, 실내 사전 공조, 습관 기반 추천 계획 | 습관 기반 추천(계획 단계) | — | Gemini 확장 | 대상이 MY2015+에서 MY2022+로 축소됨. FTC 데이터 공유 명령 이후 옵트인 중심 |
| **Google** | Android Auto Gemini(2025-11, 45개 언어). Google built-in Gemini(2026-04, Volvo·Honda 등) | Gemini | Gemini Live 대화, 메시지 번역·요약, "분위기"로 음악 재생, 매뉴얼 Q&A | 설명된 자발적 제안 없음 | — | Gemini 확장(Home·Keep·Maps) | 연락처 별명 인식 퇴행(9to5Google 2025-11). 2026-09 Assistant 제거 [스니펫] |
| **Apple** | CarPlay Siri. iOS 26.4에서 ChatGPT·Claude 등 대화형 앱 허용(차량 제어 불가) [스니펫]. Siri AI 2026-06 발표, iOS 27 2026-09 | Apple Foundation Models(Gemini 협업설은 미확인) | 대화형 Siri, 서드파티 챗봇 | — | — | 앱 개방, 단 차량 제어 금지 | 출시 시점에 EU·중국 제외. 한국어 지원 미비 지적(클리앙 2026-06) |
| **NIO** | NOMI(2017~, 대시보드 로봇). NOMI GPT 2024-04 | 자체 | 차량·무드등 제어, 사용자 단축 명령, 백과사전형 Q&A, 무제한 잡담 | 멀티 에이전트 기능(주차 도우미, 여행 기억 등) [스니펫·날짜 미상] | **물리 로봇 얼굴, 감정 엔진** [스니펫] | 멀티 에이전트, 실내 전체 기억 [스니펫] | 2024-06 상호작용 1천만 회 [스니펫] |
| **Li Auto** | 리샹퉁쉐(理想同学). MindGPT, 2025-02 DeepSeek R1/V3 통합 | MindGPT + DeepSeek | 온라인 검색(RAG), 에이전트 | — | — | 차량 에이전트가 주도하는 MCP/A2A 구조, GUI 에이전트로 음식 주문 [스니펫] | — |
| **Xpeng** | 샤오P. 톈지 OS 6.0, 2026-01~02 OTA | 자체 + 로컬 VLM [스니펫] | 웨이크워드 없이 대화(2024) [스니펫] | "업계 최초 선제 서비스 콕핏"(구체 사례 미공개) | — | — | — |
| **BYD·Geely 등** | 2025-02 "DeepSeek 탑재" 붐(20개 이상 브랜드) | DeepSeek + 자체 | 대부분 "기존 챗봇 업그레이드 수준" [스니펫] | — | — | — | — |
| **Cerence** | Chat Pro, CaLLM / CaLLM Edge, xUI(2025-03). CES 2026 에이전트 발표 | 오픈소스를 파인튜닝한 CaLLM | 엣지+클라우드 하이브리드 에이전트 | **"오너십 동반 에이전트"**(정비·잘 안 쓰는 기능을 먼저 알려줌), 캘린더 기반 선제 내비 | — | 도메인별 에이전트(오너십·딜러·모바일 업무) | 매출 감소(FY2025 약 -24%), 구조조정 [스니펫] |
| **SoundHound** | Chat AI(Stellantis DS·Peugeot·Opel). 음성 커머스 데모(CES 2025·2026) | 자체 + LLM | 음식 주문·결제 후 픽업지로 길안내, OpenTable 예약 | — | — | 커머스 파트너 | **양산 차에서 음식 주문이 실제 운영되는 사례는 확인 못 함** |
| **Amazon** | Alexa Built-in, Echo Auto | Alexa+ | — | — | — | — | Echo Auto 2세대 품절 상태, 앱 은퇴(2025-10) [스니펫]. Auto SDK 일부 비공개 전환(2023-06) [스니펫] |
| Stellantis | Mistral 기반 비서(2025-02 발표). 경고등·트러블슈팅 설명 | Mistral | 차량 기능·**경고등 설명** | — | — | — | 출시일 미확인 |
| Toyota | "Hey Toyota" 온디바이스(2026 RAV4, Arene) | LLM 여부 미확인 | 오디오·공조·차량 기능 | — | — | — | — |

**교차 패턴**
- 비서의 "브랜드 이름"은 OEM이 쥐고, 뒤의 LLM은 바꾼다. 예: Hey BMW, Hey Mercedes, 글레오, OnStar.
- 멀티 에이전트(주제별 라우팅)가 표준이 되고 있다. Mercedes, Li Auto, NIO, Cerence xUI, 그리고 글레오(단계별 LLM과 서브 에이전트)가 그렇다.
- 선제 기능은 중국과 Cerence 데모가 앞서고, 서구 양산 차에서는 거의 체감되지 않는다.
- 서드파티에 열린 경로는 모두 **차량 제어를 막거나 승인제**다. CarPlay 챗봇은 차량 제어 불가, Gleo Interface는 승인 필요.

출처: [HMG 뉴스룸 2026-04-30](https://www.hyundaimotorgroup.com/en/news/hyundai-motor-group-redefines-in-vehicle-experience-with-pleos-connect-next-generation-infotainment-system) [원문], [Mercedes CLA 2025-07-16](https://media.mbusa.com/news/the-all-new-mercedes-benz-cla-gorgeous-effortless-intuitive-and-flexible) [원문], [InsideEVs 2025-12-26](https://insideevs.com/news/782831/mercedes-cla-ai-assistant-review/) [원문], [BMW 2026-01-05](https://www.bmwgroup.com/en/news/general/2026/alexa.html) [원문], [Cerence VW 2025-08-25](https://investors.cerence.com/news-events/press-releases/detail/348/) [원문], [Tesla 2025.26 릴리스 노트](https://www.notateslaapp.com/software-updates/version/2025.26/release-notes) [원문], [Motor1 2026-09-02](https://www.motor1.com/news/806866/tesla-grok-summer-update-brings/) [원문], [TechCrunch GM 2025-10-22](https://techcrunch.com/2025/10/22/gm-is-bringing-google-gemini-powered-ai-assistant-to-cars-in-2026) [원문], [TechCrunch OnStar 2026-10-21](https://techcrunch.com/2026/10/21/gm-is-giving-onstar-an-ai-makeover/) [원문], [9to5Google 2025-11-06](https://9to5google.com/2025/11/06/android-auto-gemini-starts-rolling-out/) [원문], [9to5Google 2026-04-30](https://9to5google.com/2026/04/30/gemini-coming-to-android-automotive/) [원문], [Apple 2026-06](https://www.apple.com/newsroom/2026/06/apple-introduces-siri-ai-a-profoundly-more-capable-and-personal-assistant/) [원문], [CnEVPost NOMI GPT 2024-04-12](https://cnevpost.com/2024/04/12/nio-rolls-out-nomi-gpt/) [원문], [Gasgoo Li Auto](https://autonews.gasgoo.com/articles/icv/70036017) [원문], [news18a Xpeng 2026-02-04](https://english.news18a.com/m/news/english_229601.html) [원문], [Cerence CES 2026](https://investors.cerence.com/news-events/press-releases/detail/361/) [원문], [OECD AI 사건 2025-10-28](https://oecd.ai/en/incidents/2025-10-28-911b) [스니펫].

---

## 2. 글레오 AI 상세와 실사용 반응

### 2.1 공식 사실

| 항목 | 내용 | 근거 |
|---|---|---|
| 기원 | 2025년 개발자 행사 "Pleos 25"의 R&D 버전을 양산화. 개발 착수는 2024년 | [스니펫] newstomato·newstap / [원문] 42dot |
| 공개 | 2026-04-29 UX 스튜디오 서울 미디어 행사, 보도자료 2026-04-30 | [원문] HMG 뉴스룸 |
| 첫 양산 | 더 뉴 그랜저 2026-05-14 출시. 4,185만~4,864만 원. "LLM 기반 차세대 생성형 AI 에이전트" | [원문] [HMG 뉴스룸](https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-company-the-new-grandeur) |
| 확대 | 디 올 뉴 아반떼(CN8) 2026-06-26 부산모빌리티쇼 공개, 8월 시승. 2026-09 기준 5개 차종 [스니펫]. 유럽 첫 차종은 IONIQ 3, 미국은 투싼 [스니펫]. 2030년까지 약 2천만 대 목표 | [스니펫] motorpd, sedaily / [원문] HMG |
| 기능(공식) | 맥락 이해("거기로 가자"), 한 번에 여러 명령("에어컨 켜고 음악 틀고 라디오 켜줘"), 사투리 이해, 웹 검색(뉴스·날씨·스포츠), 내비·공조·매뉴얼 Q&A, **발화 좌석 인식**(그 좌석만 열선 켜기), 대화 기록 앱, 연속 대화, 지식 검색, **여행 일정 제안, 감성 대화, 선제 정보 제공** | [원문] HMG 2026-04-30, 그랜저 보도자료, [아시아투데이 2026-04-30](https://www.asiatoday.co.kr/kn/view.php?key=20260430010009661) |
| 현재 범위(공식 표현) | "현재 Gleo AI의 동작은 차량 제어와 편의 기능 중심" | [원문] HMG 2026-04-30 |
| 기술 구조 | 이해·판단·응답 생성 단계마다 다른 LLM을 선택해 씀. LLM이 서브 에이전트에 작업을 위임. LLM 기반 TTS. 지연에 민감한 처리는 온디바이스, 무거운 연산은 클라우드. Knowledge Agent(웹 검색과 자체 데이터). Gleo Guard(위험·불법 요청 거부) [스니펫]. 차량 제어 전 안전 검사. 4개 축: LLM Intelligence, Hybrid AI, Speech Intelligence, Gleo Interface | [원문] [42dot](https://42dot.ai/en/stories/blog-news/42dot-unveils-gleo-ai), [전자신문 2026-05-21](https://www.etnews.com/20260521000105) |
| LLM 업체 | 비공개. HyperCLOVA X라는 근거는 없음. 42dot 팀장이 "현재 외부 API에 의존, 자체 LLM 개발 중"이라고 말했다는 요약이 있음 | [스니펫] |
| 웨이크워드 | "헤이 글레오" 또는 "글레오야". 자체 모델 [스니펫] | [원문] 이코노미스트 |
| 첫 OTA(2026-10-30) | 경로 주변 장소 제안(휴게소·주유소·화장실, 조건을 말하지 않아도 됨), 유종별 주유 가격, 온디바이스 문자 읽기(TTS), 모든 경유지 정보와 남은 시간, **뒷좌석에서 거는 전화·문자 제한**, 블루투스 음악 개선. 앞으로도 "일상 이용 빈도가 높은 기능 고도화 중심" | [원문] [HMG 뉴스룸](https://www.hyundaimotorgroup.com/ko/news/42dot-gleo-ai-first-ota-in-car-ai-experience-upgrade) |
| 로드맵 | 앱 기능을 음성으로 제어(앱 연동), 개인화, 자체 LLM 비중 확대, 온디바이스 확대. 42dot 대표 박민우: "말하지 않아도 필요한 것을 돕는 개인화 AI" | [원문] HMG, 42dot |
| 요금 | 공식 구독료는 확인 못 함. 커뮤니티에서는 "옵션"이라는 주장과 유료화 추측이 있음(미검증) | [원문] 루리웹 / [스니펫] |

### 2.2 언론 시승기

| 매체·날짜 | 핵심 관찰 |
|---|---|
| 이코노미스트 2026-05-31 [원문] | 큰 목소리로 부르면 오히려 잘 깨지 않음. 정치 질문은 회피함. 개발진: "인식 안 되는 사례를 계속 발견하고 있고, 개선해 나갈 것" |
| ZDNet 2026-05-31 [원문] | "아직은 물리 버튼을 완전히 대체할 수준은 아니었다". "에어컨 높여줘, 더워" 실패 |
| 시사저널e 2026-05-31 [원문] | "훨씬 자연스러운 대화가 가능". 동승자가 "창문 열어줘"라고 하면 조수석 창만 열림(좌석 인식 작동) |
| 디지털타임스 2026-06-07 [원문] | 오토홀드는 "사용할 수 없다"고 답함. 스포츠 모드는 설정 화면만 열림. 특정 곡을 찾아 재생하지 못함. 현대차 엔지니어: "법규·안전상의 이유" |
| 한국일보 2026-06-13 [원문] | "주문 실행까지 1, 2초가량 뜸". "시킨 일은 그런대로 잘 해냈다". 루프 조명 밝기 같은 복합 명령은 실패. 완료 확인 음성 없음 |
| 디지털타임스 2026-08-09 [원문] | 맛집·경로·타이어 공기압 안내, 뉴스 헤드라인 10개 낭독. 실시간 주가는 거부. "완벽하진 않지만 이전 음성 시스템보다 유용" |
| 동아일보 2026-08-20 [원문, 본문 글자 깨짐] | 기자 체감 이해율 약 60~70% |
| 한겨레 via Supple, 아반떼 2026-08-26 [원문] | "같은 명령에도 지원 여부와 작동 결과가 엇갈렸다". 차에 있는 뒷창을 미지원이라고 답함. 바뀐 값을 음성으로 알려주지 않음. 현대차는 2027년 차량 제어 OTA를 검토 중 |
| 동아일보 2026-09-28 [원문 글자 깨짐, 세부는 스니펫] | 인식 정확도가 핵심 쟁점이고 소비자 평가가 갈림. 음악 재생 중 웨이크워드 실패, 42dot은 음성 분리를 개선 중 |
| 2026-09-13 네이트 전재(출처 매체 불명확) [원문 글자 깨짐] | 제목 "플레오스 첫 시험대…초기 사용자 평가는 '글쎄'". 버튼 대신 긴 음성 명령을 해야 하는 이유에 대한 의문, 화면 멈춤·지연 사례 |

### 2.3 요약: 칭찬과 불만

- **칭찬**
  - 정해진 명령어보다 자연스럽다.
  - 좌석 인식이 동작한다.
  - 기본 공조·창문·시트 제어가 된다.
  - 맛집·경로 정보가 쓸 만하다.
- **불만**
  - 웨이크워드가 음악이 나오거나 큰 목소리일 때 실패한다.
  - 이해율이 60~70%이고, 실행까지 1~2초 지연된다.
  - 실행 결과를 음성으로 확인해 주지 않는다.
  - 같은 명령에 결과가 다르다.
  - 주행·ADAS 기능을 거부한다.
  - 곡 검색 재생이 안 된다.
- **확인 못 한 것**: 한 달 이상 써 본 오너의 장기 후기는 찾지 못했다. 가장 큰 공백이다.

---

## 3. 수요와 이용 행태 데이터

### 3.1 무엇에 쓰나

**Voicebot.ai + Cerence, 2020-01** (미국 성인 n=1,090) [원문 PDF](https://voicebot.ai/wp-content/uploads/2020/02/in_car_voice_assistant_consumer_adoption_report_2020_voicebot.pdf)

- 운전 중 음성비서 사용 경험은 50.9%였다. 사용 경험자 중 22.9%가 매일 쓴다.
- 용도(전체 사용자 기준 / 매일 사용자 기준):

| 용도 | 전체 | 매일 |
|---|---|---|
| 전화 | 73.0% | 84.0% |
| 길안내 | 49.7% | 52.8% |
| 문자 보내기 | 38.9% | 54.7% |
| 음악 | 27.0% | 37.7% |
| 라디오 | 14.0% | 20.8% |
| 일반 질문 | 13.8% | 14.2% |
| 날씨 | 13.2% | 15.1% |
| 팟캐스트 | 10.8% | 21.7% |
| 식당 찾기 | 10.6% | 13.2% |
| 음식 주문 | 4.1% | 10.4% |
| **차량 기능 제어(난방·와이퍼)** | **3.5%** | **3.8%** |

- 자기 차 비서의 기능을 "완전히 안다"는 응답은 31.9%에 그쳤다. 44.5%는 튜토리얼이 있으면 쓰겠다고 했다.

**Capgemini "Voice on the Go", 2019-11-14** (10개국 소비자 7,000명 이상, 경영진 300명) [원문](https://www.capgemini.com/news/press-releases/voice-on-the-go-report/)
- 음악·길안내 이용률은 현재 77%였고 앞으로 85%로 예상됐다.
- 서비스 예약은 46%에서 74%로, 음식·서비스 주문은 45%에서 72%로 늘 것으로 예상됐다. 다만 소비자가 "앞으로 쓰겠다"고 답한 기대치다.
- 비서가 내 필요와 취향을 이해한다고 느끼는 비율은 소비자 59%, 경영진 81%였다. **제조사가 생각하는 것보다 소비자는 "똑똑하다"고 덜 느낀다.**

**Cerence 블로그, 2020-01-05** (미국 1,500명 이상) [원문](https://www.cerence.com/newsroom/blog/new-survey-finds-drivers-lean-on-their-in-car-technology-to-keep-them-safe-while-driving)
- 음성비서의 가장 큰 가치로 안전 54%, 엔터테인먼트 22%, 생산성 14%를 꼽았다.
- 44%는 주차·위험 상황·정체처럼 **스트레스 상황에서 도움을 더 받고 싶다**고 답했다.

**스마트비즈/아젠다북, 2026-11-02~03** (국내 성인 801명, ±3.5%p) [원문](https://www.smartbizn.com/news/articleView.html?idxno=158898)
- 음성으로 하는 비율은 공조·볼륨 2~4%, 목적지 입력 11%였다.
- 음성을 선호한다는 비율은 목적지 23%, 공조·볼륨 13~15%였다.
- 최근 6개월 불편 경험은 다단계 조작 49%, 다른 기능이 실행됨 32%, 음성 명령 미인식 24%였다.
- 구매를 망설이게 할 요소로 음성인식 오류 13%가 가장 많이 꼽혔다(48%는 "없음").

### 3.2 만족도와 불만

- **J.D. Power(미국)**
  - 음성인식은 10년 넘게 신차 초기 품질(IQS) 문제 상위 10위 안에 있다. 불만 유형은 무응답, 오해, 어색한 멈춤, 오작동이다(J.D. Power X 계정, 2025-10) [스니펫].
  - 2014년 조사(n=86,118)에서는 문제 1위가 8.3 PP100(차량 100대당 문제 수)이었다. 그중 63%가 "명령을 인식 못 하거나 잘못 해석함"이었다 [원문](https://www.fi-magazine.com/news/voice-recognition-tops-jd-powers-list-of-new-vehicle-problems).
  - 2021 IQS에서는 CarPlay·Android Auto 연결 문제가 1위로 올라섰다. 음성인식이 1위가 아닌 것은 2011년 이후 처음이었다 [원문](https://china.jdpower.com/resources/smartphone-connectivity-now-most-common-problem-cited-new-vehicle-owners-jd-power-finds).
  - 2023 내구품질 조사에서 내장 음성인식은 7.2 PP100으로 CarPlay·AA 연결(5.5)보다 높았다 [원문](https://www.mactech.com/2023/02/09/apple-carplay-android-auto-connectivity-issues-still-a-problem-for-many-drivers).
  - 2025 IQS(n=92,694)에서 인포테인먼트는 42.6 PP100으로 문제가 가장 많은 영역이었다 [원문](https://www.theautochannel.com/news/2025/06/26/1547392-fewer-physical-vehicle-controls-and-more-problematic-touchscreen-make-infotainment.html).
  - 2025 TXI(n=76,230)는 문제의 성격이 "부품 고장에서 사용성 문제로 이동"했다고 정리했다 [원문](https://www.automotiveworld.com/news-releases/ai-based-technologies-improve-customer-experience-but-present-new-challenges-j-d-power-finds).
  - 2026 TXI(n=68,084)에서 동승석 화면은 35%가 한 번도 쓰지 않았다. 핵심 메시지는 "그냥 잘 작동하는 기술"이 가치 있다는 것이다 [원문](https://www.carscoops.com/2026/08/jd-power-tech-study-2026/).
- **J.D. Power 중국 TXI 2025**(n=38,752): 음성인식·이해 정확도가 "주요 불만점"이었다 [원문](https://china.jdpower.com/press-releases/2025-china-tech-experience-index-txi-study).
- **Capgemini 2019**: "훌륭하다"는 28%, "만족하지만 개선 필요"는 59%였다.
- **컨슈머인사이트 25차 자동차 기획조사**(2025-07 조사, 2026-02-12 발표. 커넥티드카 이용자 n=1,489) [원문](https://www.consumerinsight.co.kr/autoinsight/abview?no=3859&id=pr4_list)

| 기능 | 만족률 |
|---|---|
| 원격 제어 | 80% |
| 차량 상태 확인 | 74% |
| 차량 관리 | 74% |
| 안전·보안 | 71% |
| 미디어 | 68% |
| 폰 연동 | 67% |
| 실시간 내비 | 65% |
| **AI 서비스(8개 중 최하)** | **52%** |

  - AI 서비스를 써 본 비율은 9%, 주로 쓰는 비율은 1%였다.
  - 컨슈머인사이트의 해석은 원문 인용으로 남긴다: "현재 자동차 AI 서비스가 대부분 음성 인식과 주행 보조 등 기초적 수준에 머물러 있기 때문"이다. 또 "기존 음성인식 기능과의 차별성이 모호하다 보니 AI 기능 이용 여부를 인지하지 못하는 경우가 많다".
  - "음성인식이 불만 1위 신기술"이라는 컨슈머인사이트 발표는 찾지 못했다. 이와 비슷한 문구는 2014년 J.D. Power(미국) 결과를 전한 국내 보도다 [스니펫](https://www.seoul.co.kr/news/international/2014/08/29/20140829800032).

### 3.3 선제 발화 수용성

- **Schmidt, Minker & Werner (LREC 2020)**, 시뮬레이터 Wizard-of-Oz 실험, n=42 [원문 PDF](https://aclanthology.org/2020.lrec-1.61.pdf)
  - 선제 제안 수락률은 73.8%였다. 운전 관련 제안(재경로, 일정, 주차, 주유)은 **82.5%**였다.
  - 주유 알림은 64%가 수락했고 14%는 "나중에, 예를 들어 주행 가능 거리 150km 미만일 때 알려 달라"고 했다.
  - **"휴식하세요"는 61%가 거절**해 가장 낮았다.
  - "늦을 것 같다" 알림에는 41%가 "문자 보내줘", 20%가 "전화해줘"라고 답했다. 알림에 행동을 이어서 붙이기를 원한다는 뜻이다.
  - 선제 행동에는 반응이 더 빨랐다(부하가 낮다는 해석).
  - **42명 중 33명이 주제별 on/off를 원했다.** 완전히 끄고 싶다는 사람은 5명, 끄는 스위치가 필요 없다는 사람은 4명이었다.
- **Hamid, Li & Liang "When2Talk" (arXiv 2026-09)**, VR 자율주행 동승자, n=41 [원문](https://arxiv.org/html/2609.12503v1)
  - 맥락에 따라 말하기·기다리기·침묵을 고르는 정책과 매 이벤트마다 말하는 정책을 비교했다.
  - 맥락형은 방해감이 2.14로 매번 말하기(3.71)보다 낮았고 적절성은 5.09로 4.50보다 높았다. 신뢰는 같았다.
  - 대신 맥락형이 침묵한 구간 때문에 유용성 점수는 매번 말하기가 더 높았다.
- **Verra Mobility, 2026-10** (미국 2019년식 이후 차주 2,000명): 72%가 주차·주유·충전·통행료에 대한 선제적 트립 제안을 가치 있다고 답했다 [원문](https://www.repairerdrivennews.com/2026/10/16/survey-buyers-want-their-next-vehicle-to-function-like-a-smartphone/).
- **McKinsey China Auto Consumer Insights 2026**: 84%가 차량 AI 에이전트가 "선제적 지능형 동반자"이기를 원했다. 중국 응답자다 [스니펫](https://www.mckinsey.com/cn/our-insights/our-insights/value-is-the-compass-consumers-set-the-pace).
- **Cerence CES 2026 자료**: "60% 이상이 이미 가진 고급 기능을 안 쓴다". 마케팅 자료이며 원 조사는 확인 못 함 [스니펫].

### 3.4 프라이버시와 신뢰

- Mozilla "Privacy Not Included"(2023-09)는 자동차 25개 브랜드 전부가 최소 기준에 미달했다고 평가했다. 19곳은 개인정보를 판매할 수 있다고 명시했다 [스니펫](https://tidbits.com/2023/09/07/mozilla-says-modern-cars-are-data-collection-nightmares-on-wheels/).
- Capgemini 2019에서는 50%가 개인정보를 음성비서에 맡길 수 없다고 답했다.
- Deloitte 2025(30개 시장 31,000명 이상)에서 차량 AI에 회의적인 비율은 미국·영국 26%였다. 반면 인도와 중국의 수용률은 82%, 77%였다 [원문](https://www.deloitte.com/uk/en/about/press-room/uk-drivers-among-most-sceptical-globally-about-ai-in-cars.html).
- Verra 2026에서 자기 차가 무엇을 수집하는지 완전히 안다는 응답은 14%뿐이었다. 그런데도 혜택이 있으면 주행·위치 데이터를 공유하겠다는 응답은 66%였다.

---

## 4. 연구 결과 (HCI·인간공학)

### 4.1 인지 부하

- **Strayer 등 / AAA Foundation (2013~2017)**
  - 인지 주의 분산 척도(1~5)를 만들었다. 라디오 1.21, 동승자와 대화 2.33, 손에 든 전화 2.45, 음성으로 문자 입력(speech-to-text) 3.06이었다 [스니펫](https://trid.trb.org/View/1252566).
  - 2015년 차량 10종 평가(n=257)에서 차량 시스템은 2.4~4.6이었다. 폰 비서는 Google Now 3.0, Siri 3.4, Cortana 3.8이었다. **잔여 주의 분산은 최대 27초, 가장 좋은 시스템도 15초 이상**이었다 [원문 보도자료](https://newsroom.aaa.com/2015/10/new-hands-free-technologies-pose-hidden-dangers-for-drivers/).
  - 2016년 연구: 5일 동안 연습해도 주의 분산이 사라지지 않았다. 고령 운전자의 부하가 더 컸다 [스니펫] doi:10.1186/s41235-016-0018-3.
  - 2017년 연구: 비서 사이의 부하 차이는 **오류 수와 과제 소요 시간**에 따라 갈렸다. 신호 대기 중에 조작해도 출발한 뒤 최대 18초까지 영향이 남았다 [원문 보고서] doi:10.1037/cep0000104.
- **Drews, Pasupathi & Strayer (2008)**: 동승자는 교통 상황을 보고 말을 조절하지만 전화 상대는 그러지 못한다. 전화 통화 조건의 약 절반은 나가야 할 출구를 놓쳤다 [스니펫] doi:10.1037/a0013119. **설계 교훈: 에이전트는 "도로를 보는 동승자"처럼 행동해야 한다.**
- **Monk 등 (2026, arXiv:2601.15034, Google 협업)**: 실도로 실험(n=32)에서 Gemini Live의 인지 부하는 핸즈프리 통화와 비슷했다. 다중 턴 대화에서도 부하가 늘지 않았고, 평균 시선 이탈은 2초 미만이었다 [초록]. 업계 연계 연구다.
- **Dittrich 등 (IVA 2025)**: 자연스러운(구어체) 대화 스타일이 꼭 안전하지는 않다. 인지 분산이 커질 수 있다 [메타·스니펫].
- **음성 길이 연구 (Transportation Research Part F, 2026)**: 비서 발화가 짧을수록 교차로 주행 성능이 좋았다. 인지 부하가 이 효과를 매개했다 [스니펫].
- **응답 지연 연구 (Aalto 석사논문)**: 0.5~1초 지연은 수용됐고 2초를 넘으면 대부분 거부했다. LLM 비서에는 기존 비서보다 지연에 더 엄격했다 [스니펫].
- **Kirmayr 등 (CHI 2026, BMW·LMU)**, n=45: 여러 단계로 처리하는 에이전트가 중간 진행 상황을 알려 주면 체감 속도·신뢰·UX가 좋아지고 부하가 줄었다. 처음에는 투명성을 원하다가 시스템이 믿을 만해지면 말을 줄여 주기를 원했다 [초록] arXiv:2602.15569.

### 4.2 선제(proactive) 에이전트

- **Schmidt 등 2020**: 위 3.3 참고. 수락률 73.8%, 운전 관련 82.5%.
- **Semmens 등 (CHI 2019, Toyota Research Institute)**: 50분 주행(n=63) 동안 "지금 말 걸어도 되나" 응답 2,734건을 모았다. 좋은 시점은 차량 데이터로 일부 예측할 수 있지만, 운전자·환경 상태 정보가 더 필요하다 [스니펫](https://www.tri.global/research/now-good-time-empirical-study-vehicle-driver-communication-timing).
- **Kim, Park & Lee (IMWUT 2020)**: 끼어들어도 되는지는 주행 기동과 음성 과제 난이도에 달려 있었다. 대화 흐름을 운전자와 시스템이 함께 조절하는 방식을 제안했다 [스니펫] doi:10.1145/3381009.
- **Meck, Draxler & Vogt (IJHCI 2023)**: 선제 발화는 **언어적으로 단순하고, 지시가 아닌 제안하는 어조**여야 한다 [메타] doi:10.1080/10447318.2023.2266251.
- **Du 등 (IJCAI 2024)**: 선제성을 5단계로 나눴다(n=40). **"강하게 추정해 제안하고 실행 전에 확인받기"가 가장 선호**됐다. 완전 자율 실행은 수용성이 낮았다 [초록] arXiv:2403.09135.
- **Mathis 등 (MuC 2024)**: 내용과 무관하게 선제 제안이 부적절한 맥락이 따로 있다. 관련성이 높은 제안일수록 더 많은 상황에서 받아들여졌다 [초록].
- **ProVoice (CHI 2026)**, n=19: 개입 전략을 최적화하면 정신적 부하가 줄었다. 운전자는 통제권을 유지하려고 낮은 자율성을 선호했다 [초록] arXiv:2601.19421.
- **Rosenthal-von der Pütten 등 (CHI 2026, Mercedes-Benz 협업)**, n=351: **낮은 자율성의 선제성과 덜 의인화된 디자인**을 선호했다 [메타, 결과는 스니펫].

### 4.3 페르소나와 감성

- **Nass 등 (2005)**, n=40: 운전자 기분과 목소리 톤이 맞을 때(기쁜 운전자에게 활기찬 목소리, 화난 운전자에게 차분한 목소리) **사고가 줄고** 도로 주의가 늘었다 [스니펫] doi:10.17077/drivingassessment.1158.
- **Braun 등 (CHI 2019)**, n=55: 운전자와 성격이 맞지 않는 비서는 호감·신뢰·만족이 낮았다. 성격 표현은 안전과 무관한 상황에서만 하라고 권고했다 [스니펫] doi:10.1145/3290605.3300270.
- **Braun 등 (MTI 2019)**, n=60: 공감형 음성비서가 부정 감정을 가장 크게 개선했다. 다만 **"가르치려 드는 듯한 간섭(paternalism)"은 싫어했다** [스니펫] doi:10.3390/mti3010021.
- **Cheng 등 (Frontiers 2022)**, n=116: 사람을 많이 닮은 **물리적** 에이전트(NOMI 유형)는 화면 속 에이전트보다 신뢰가 낮았다. 운전자가 느끼는 통제감이 이를 매개했다 [초록] doi:10.3389/fpsyg.2022.883417.
- **Bond 등 (arXiv 2025, Virginia Tech)**, n=40: ChatGPT 에이전트 조건에서 가속·차선 편차의 변동이 줄었다. 유능함·신뢰·선호도 높았다 [초록] arXiv:2508.08101.
- **Kiro (Ringfort-Felner 등 2022)**: 사람들은 사회적 대화를 하는 비서를 받아들였지만, **사람과의 대화를 대체하는 것으로는 받아들이지 않았다** [스니펫].
- **AIDA (MIT, Williams 등 2013)**: 휴대폰을 로봇 얼굴로 쓴 동반자는 스트레스를 낮추고 동반감을 높였다 [초록].

### 4.4 졸음·피로·외로움

- **Atchley & Chan (Human Factors 2011)**: 지루한 주행에서 **주행 후반에 시점을 맞춘** 말하기 과제만 차선 유지를 개선했다. 처음부터 계속하는 과제는 효과가 없었다 [초록] doi:10.1177/0018720810391215.
- **Large 등 (IET ITS 2018)**, n=20: 디지털 비서와 대화하면 차선 유지, 위험 반응, 시야 탐색, 주관적 졸음이 모두 나아졌다 [스니펫] doi:10.1049/iet-its.2017.0201.
- **Zhang 등 (Applied Ergonomics 2025)**: 주관적 졸음 점수는 무처치 7.98, 따라 말하기 6.33, **질문에 대답하기 5.28**로 대답하기가 가장 효과적이었다. 연령대별로 생리 반응이 달라 맞춤형 설계가 필요하다 [초록] doi:10.1016/j.apergo.2025.104616.
- **Huang 등 (CHI EA 2024)**: GPT-4 기반 "Driver Mate"에서는 **낮은 복잡도, 낮은 빈도의 대화**가 주행을 개선했다 [스니펫] doi:10.1145/3613905.3651031.
- **Cockram 등 (CHI 2026)**, 테스트 트랙 n=40: **주변 상황과 연결된 짧은 체크인**이 각성 유지에 도움이 됐다. 선호 유형이 3가지로 갈려 적응형 설계가 필요하다 [초록] arXiv:2510.25421.
- **외로움**: 장거리 화물 운전자의 고립과 정신건강 문제를 다룬 연구는 있다(Apostolopoulos 2016). 그러나 **외로움을 직접 겨냥한 대화 에이전트의 동료심사 실험은 찾지 못했다.**

### 4.5 동승자와 아이

- **Wilfinger 등 (INTERACT 2011)**, 20가족: 뒷좌석의 핵심 문제는 지루함이다. 부모는 운전 중에도 **뒷좌석 상황을 알고 싶어 한다**. 글을 못 읽는 아이를 고려해야 한다 [스니펫].
- **Gordon & Breazeal (IDC 2015)**: PANDA 에이전트가 앞·뒷좌석을 오가며 부모 운전자와 아이 사이를 중재해 운전자의 주의 분산을 줄이는 개념이다. 프로토타입 단계다 [메타].
- **Perterer 등 (CSCW 2013)**: 조수석 동승자는 길 찾기를 적극적으로 돕는다(공동 내비게이션) [스니펫].
- **공백**: 운전자와 동승자(아이)가 같은 음성비서를 두고 충돌하는 상황을 측정한 동료심사 연구는 찾지 못했다.

### 4.6 신뢰·오류·회복

- **Koo 등 (2015)**: "차가 브레이크를 밟습니다"처럼 무엇을 하는지만 말하면 주행이 나빴다. **"전방 장애물"처럼 이유를 말하면 선호도와 주행이 모두 좋았다.** 둘 다 말하면 가장 안전했지만 부정 감정이 늘었다 [원문] doi:10.1007/s12008-014-0227-2.
- **Mercurio 등 (CHI 2023, Google)**, 실패 사례 199건: **명령이 아닌 말을 명령으로 받아 실행하는 것(과잉 포착)**이 신뢰를 가장 크게 떨어뜨렸다. 음악 같은 위험이 낮은 과제에서 성공하면 신뢰가 회복됐다 [초록].
- **Mahmood 등 (CHI 2022)**: 오류를 진심으로 인정하고 사과하는 AI가 더 지능적이고 호감 있게 보였다 [스니펫].
- **CAR-bench (Kirmayr 등 2026, BMW)**: 도구 58개 환경에서 최상위 LLM도 모호한 요청을 처리하는 과제의 일관 통과율이 50% 미만이었다. 정보가 없을 때 **지어내는** 경향이 있었다 [초록] arXiv:2601.22027.
- **Afroze 등 (arXiv 2026-09)**: 시나리오 202개로 "실행·거부·되묻기·확인" 판단을 평가했더니 정렬률이 40.1~89.1%였다. 최고 모델도 실행하면 안 되는 161개 시나리오에서 2~3건을 잘못 실행했다. 저자 결론은 **LLM 바깥에 독립적인 권한·차량 상태 검사 계층이 필수**라는 것이다 [초록] arXiv:2609.19630.

**연구 전반의 한계**: 대부분 시뮬레이터나 Wizard-of-Oz 실험이고 참가자는 20~60명이다. LLM 관련 근거는 대부분 2024~2026년 프리프린트와 CHI 논문이며 업계 연계 연구가 많다.

---

## 5. 국내 커뮤니티 반응

원칙: 닉네임은 기록하지 않았다. 인용은 짧게 줄였다. 반응 수치는 열람 시점(2026-11-12) 기준이다. 로그인과 게시는 하지 않았다.

### 5.1 플레오스·글레오 관련

| 출처·날짜 | 반응 수치 | 짧은 인용 / 요지 |
|---|---|---|
| 클리앙 모두의공원 "현대차그룹, 'Pleos Connect' 공개" 2026-04-30 [원문](https://www.clien.net/service/board/park/19184477) | 조회 8,297, 댓글 32 | "음성 명령 잘 실행되고 편한데, 동승자 있으면 음성명령을 사용하지 않게 되더라구요." / "현기 소프트웨어 솔직히 기대는 안합니다." / "너무 테슬라 짭 느낌이..." / "분명히 가격상승이 있을껀데"(공감 1) / "플레오스 1.0 차종은 하드웨어 한계로 더 이상 업뎃이 안됩니다." |
| 클리앙 자동차 "2027 현대 더 뉴 그랜저" 2026-05-14 [원문](https://www.clien.net/service/board/cm_car/19190818) | 조회 6,833, 댓글 4 | "SDV의 전초전 정도이지" |
| 클리앙 모두의공원 출시 글 2026-05-14 [원문](https://www.clien.net/service/board/park/19190572) | 조회 9,948, 댓글 37 | 댓글 중 AI·음성 언급 없음. 관심이 디자인과 가격에 쏠림 |
| FMKorea 포텐 "그랜저 플레오스 대참사.GIF" 2026-06-17 [원문](https://www.fmkorea.com/best/9967942953) | 조회 263,723, 추천 527, 댓글 581 | "진짜 껍데기만 그럴싸했네". 음성이 아니라 주행 보조 화면이 사물을 놓치는 내용. "얼리억세스"라는 조롱 |
| FMKorea 포텐 "현대차가 최근 민심 ㅈ박은 이유.jpg" 2026-11-08 [원문](https://www.fmkorea.com/best/10533292642) | 조회 523,225, 추천 931, 댓글 787 | "600만원 가격 인상의 원인이 플레오스인데" / "옵션장난질이야 말로 소탐대실임" (본문인지 댓글인지 구분 불명확) |
| 루리웹 "플레오스 적용 차량이 비싼 이유" 2026-10-14 [원문](https://bbs.ruliweb.com/family/4526/board/300143/read/77021434) | 조회 414, 추천 5 / 비추 44, 댓글 8 | "ㄹㅇ 베타테스터임 … 돈은 훨씬 더 내야함." |
| 보배드림 자유게시판(내비 앱 개방 뉴스) 2026-11-02 [원문](https://m.bobaedream.co.kr/board/bbs_view/freeb/3448462/2/1) | 조회 528, 댓글 7 | "뭐든 타자치기 거지같은데, 폰으로 입력할수 있게 좀 해라" |
| DC 자동차갤 "플레오스 달리면 좋은점이 뭐냐" 2026-02-27(출시 전) [원문](https://gall.dcinside.com/board/view/?id=car_new1&no=11296814) | 조회 471 | 요지: 이득은 제조사 개발 쪽이고 사용자는 "조금 빠릿해진 정도"(의역) |
| DC 실베 "현대차 플레오스 커넥트 공개" 2026-04-30 [원문](https://gall.dcinside.com/board/view/?id=dcbest&no=425368) | 조회 10,889, 추천 10, 댓글 118 | 댓글 본문은 확인 못 함 |

**YouTube**(제목·채널만 확인. 날짜·조회수는 확인 못 함)
- "신형 그랜저 음성비서 '글레오' AI라면서 왜 이래?!" — 광형의 환카 Fancar (쇼츠). https://www.youtube.com/shorts/IqFewF_sRKk
- "현대차 글레오 vs 테슬라 그록, AI 비교 테스트 해봤습니다" — 차플레이. https://www.youtube.com/watch?v=Psl8zx-mlnw
- "신형 그랜저와 플레오스, 응원하지만 이게 최선이었을까..." — 모트라인. https://www.youtube.com/watch?v=5IxV6ZQ2nRw
- "플레오스가 제대로 바뀌나? 6가지 바뀐 플레오스 OTA 업데이트" — 유자차 개러지. https://www.youtube.com/watch?v=qb64wyMENyo

### 5.2 기존 현대·기아 음성인식, 카플레이 시리, 안드로이드 오토

| 출처·날짜 | 반응 수치 | 짧은 인용 |
|---|---|---|
| 클리앙 자동차 "(신형) 현기차에서 세상 편한데 잘들 안쓰시는 기능" 2023-02-16 [원문](https://www.clien.net/service/board/cm_car/17911626) | 조회 9,481, 댓글 54 | "너무 오래걸려요"(공감 8) / "복불복 명령이라 그냥 버튼 누르고 마는게 편할때가 훨씬 많습니다." / "명령을 들었는지 안 들었는지 디스플레이되지를 아니하니까"(공감 1) / "기계에 뭔가 말을 건다는것 자체가 익숙하지 않습니다"(공감 1) / "내비 목적지는 음성인식으로 98% 씁니다."(공감 1) / "엉따 켜줘, 손따 켜줘 해도 … 잘 켜줘서 자주 애용" / "동승자가 인간 음성인식이 되죠"(공감 1) |
| 클리앙 자동차 "자동차에 있는 음성인식 기능을 잘 사용하시나요?" 2024-11-15 [원문](https://www.clien.net/service/board/cm_car/18840661) | 조회 9,290, 댓글 37 | 글쓴이는 오인식과 동승자 앞의 어색함 때문에 안 쓰게 됐다고 함. "네비 목적지 찰떡같이 알아들어서 잘써요"(공감 5) / "네비 목적지는 90% 이상 음성인식 씁니다." / "제대로 알아먹은적도 한번도 없어서 안씁니다" / "기술은 다 적응하기 나름인것 같아요"(공감 2) |
| 클리앙 자동차 "카플레이 상황에서 시리대신 카카오i 호출법" 2020-07-27 [원문](https://www.clien.net/service/board/cm_car/15214185) | 조회 4,169, 댓글 12 | "카카오i 인식률이 참 좋아서 자연스럽게 사용이 늘더라고요." / "음성인식 버튼 누르면 바보같은 시리만 나오네요." |
| 클리앙 자동차 "카플레이 잘 활용하시나요?" 2023-10-16 [원문](https://www.clien.net/service/board/cm_car/18367917) | 조회 11,740, 댓글 63 | "시리가 너무 답답해서" / "음악도 끄고 네비소리 않나야 시리가 호출 되더라구요" / "혼자 운전하다 심심할때 시리랑 대화용….." |
| 클리앙 사용기 "카플레이 적응 실패한 3가지 이유" 2020-11-26 [원문](https://www.clien.net/service/board/use/15627118) | 조회 37,883, 댓글 47 | "애플 제품은 한국어를 못 알아듣는답니다." |
| 클리앙 아이포니앙 "뉴 시리 사용 후기" 2026-06-09 [원문](https://www.clien.net/service/board/cm_iphonien/19205998) | 조회 7,127, 댓글 11 | 새 Siri는 언어를 영어로 바꿔야 함. "이대로면 반쪽짜리겠네요"(차량 언급 없음) |
| 다모앙 자동차(Android Auto Gemini) [스니펫, 403](https://damoang.net/car/128084) | — | 목적지 변경 중 Gemini가 떠서 "기존 ok구글 보단 좀 편하겠다"고 기대 |

### 5.3 패턴 정리 [추론]

1. **음성이 살아남는 용도는 "목적지 입력"과 "열선 같은 단순 토글"이다.** 이 두 가지는 "90~98% 음성"이라는 열성 사용자도 있다. 나머지 용도는 오인식을 겪으면 버튼으로 돌아간다.
2. **동승자가 있으면 음성을 안 쓴다**는 말이 반복된다(2023, 2024, 2026 글 모두). 혼자 운전할 때와 동승자가 있을 때는 서로 다른 시장이다.
3. **들었는지 알려주지 않는 피드백 부재**가 불만이다. Gleo 시승기의 "바뀐 값을 음성으로 안내하지 않음"과 같은 문제다.
4. 플레오스에 대한 반감은 기능보다 **가격과 "베타테스터" 프레임**에 있다. 우리 앱은 "플레오스를 사니 이게 된다"는 **체감 가치**를 보여 줄 수 있어야 한다.
5. "혼자 운전하다 심심할 때 시리랑 대화" 같은 말동무 수요는 약하게나마 존재한다.

---

## 6. 실패 사례와 교훈

| 사례 | 무슨 일이 있었나 | 교훈 |
|---|---|---|
| Google Assistant Driving Mode (2019~2025) | 폰 화면용 Android Auto 종료(2021), 대시보드 제거(2022), Maps 운전 모드 바 제거(2025-04), Gemini로 교체 [스니펫] | 플랫폼 소유자는 차량 화면을 자주 갈아엎는다. 특정 기능 하나에 기대는 서드파티는 위험하다 |
| Gemini가 Assistant를 대체 (2025-11) | 연락처 별명 인식 퇴행 [원문 9to5Google], Gemini Live 핸즈프리 버그 [스니펫] | LLM으로 갈아타면 "확실히 되던 명령"이 깨진다. 결정적(규칙 기반) 경로를 남겨 둬야 한다 |
| Amazon Echo Auto | 앱 은퇴(2025-10), 2세대 품절 상태 지속(2026-09) [원문 Android Authority / 스니펫] | 별도 기기와 별도 브랜드 비서는 OEM 내장 비서와 폰 미러링에 밀린다 |
| Microsoft Cortana 차량 통합 (CES 2017 발표) | 소비자용 Cortana 퇴장. 차량 통합의 결말은 확인 못 함 [스니펫] | 약한 소비자 브랜드는 차 안에서 살아남지 못한다. 클라우드·모델 계층(Azure)만 남는다 |
| BMW 제스처 컨트롤 (2015~2025) | 이용률이 낮고 버튼보다 느리며 인식이 불안정해 폐지 [원문 BMWBlog 2025-11-27] | 새 입력 방식은 신기함이 아니라 **속도와 신뢰성**으로 기존 조작을 이겨야 한다 |
| Tesla Grok (2025~) | 출시 당시 차량 명령 불가. 1년 뒤 커뮤니티 테스트에서 명령 처리 약 68%. "Unhinged·Sexy" 페르소나, 아동 관련 사건 보도 [스니펫], CNBC 주의 분산 비판 [스니펫] | 재미있는 챗봇이 곧 차량 비서는 아니다. 아이가 탄 공유 공간에서 자극적인 페르소나는 큰 위험이다. 대화를 길게 끄는(참여 극대화) 설계는 운전 주의와 충돌한다 |
| Mercedes ChatGPT (2023 베타 → 2025 멀티 에이전트) | 3개월 옵트인 베타로 시작했다가 주제별 다중 LLM 구조로 바뀜 | 범용 LLM 하나로는 부족하다. 도메인별 라우팅이 필요하다 |
| Cerence(구 Nuance) | 매출 감소와 구조조정 [스니펫] | 기존 임베디드 음성 업체는 빅테크 LLM과 OEM 자체 스택 사이에 끼었다 |
| GM Gemini | 대상이 MY2015+에서 MY2022+로 축소되고, 자체 OnStar AI를 발표. FTC 데이터 공유 명령이 배경 | 구형 하드웨어가 LLM 확산을 막는다. 신뢰가 부족하면 옵트인으로만 갈 수 있다 |
| SoundHound 음식 주문, Stellantis·Mistral | 데모와 발표는 했지만 양산 출시는 확인 못 함 | 커머스는 식당·결제·OEM 통합이 모두 필요해서 발표가 실제 출시를 크게 앞선다 |
| 글레오 초기 반응 (2026) | 이해율 60~70%, 결과 들쭉날쭉, 가격 인상 반감 | "범용"을 약속하면 기대치는 ChatGPT 수준이 된다. 실패는 모두 체감되고 성공은 당연하게 여겨진다 |
| 동승석 화면 (J.D. Power 2026) | 35%가 한 번도 안 씀 | 기능이 있다고 쓰이지 않는다. **쓸 순간에 먼저 나타나야** 한다 |

---

## 7. 우리 제약 아래에서의 기회와 피해야 할 것

### 7.1 제약 다시 보기 (설계에 직접 영향을 주는 것)

| 제약 | 설계 의미 |
|---|---|
| STT SDK는 **텍스트만** 돌려주고, `request()`를 불러야 마이크가 켜진다. 웨이크워드는 문서에 없다 | "헤이 ○○"로 부르는 범용 비서는 만들 수 없다. 대신 **앱이 TTS로 먼저 묻고 곧바로 `request()`로 짧은 답을 받는 "질문형 턴"**이 자연스럽다. 웨이크워드 실패라는 Gleo의 약점도 피한다 |
| Gleo 연동(App Schema)과 LLM SDK는 사용 불가 | 운전자가 "글레오, ○○앱으로 해 줘"라고 말할 수 없다. 앱 진입은 화면 터치나 앱의 선제 발화로만 가능하다 |
| 차량 마이크 음성을 외부 STT로 보내는 것은 금지 | Pleos STT SDK만 쓴다. HYBRID와 SERVER 모드는 Pleos Speech 서버를 쓰므로 규정상 문제없다고 [추론]. 온디바이스 모드면 앱 등록도 불필요하다 |
| 인식된 **텍스트**를 외부 LLM으로 보내는 것 | **[추론] 팀 해석**: "외부 네트워크·API 허용"(설명회 2026-10-28)과 "음성의 외부 전송 금지"를 함께 읽으면 텍스트 전송은 허용 범위로 보인다. 주최 측이 **명시적으로 확인해 준 적은 없다**. 기획서에 "음성은 Pleos STT로 처리하고 외부로는 텍스트만 전송"이라고 적고, 개인정보 최소화와 HTTPS를 명시하는 것이 안전하다. 또 **LLM 없이도 핵심 흐름이 돌아가게** 설계해야 한다(외부 장애와 심사장 네트워크 변수 대비) |
| 차속·기어 API 없음. set API는 P단이 아니면 50001로 거부(에뮬레이터 실측) | 주행 중에 차량을 제어하는 기능은 설계 근거가 없다. 주행 중 무드등·공조 set이 되는지는 미확인이다. **주행 중에는 TTS·STT와 NaviHelper(경유지·목적지)만으로 가치를 내고, P단에서만 차량을 제어**하는 이원 구조가 안전하다. 주행 여부는 NaviHelper 경로 상태, 남은 거리 변화, FusedLocation 위치 변화로 [추론] 추정할 수 있다. FusedLocation이 속도를 주는지는 가이드에 명시가 없다 |
| 심사는 에뮬레이터 | Virtual Console로 재실·안전벨트·경고등·트립 값을 바꾸며 시나리오를 시연할 수 있어야 한다. "실차에서만 의미 있는" 신호에 기대면 안 된다 |

### 7.2 Gleo와 겹쳐서 피해야 할 것

| 피할 것 | 이유 |
|---|---|
| 범용 Q&A, 잡담 챗봇("뭐든 물어보세요") | Gleo가 웹 검색·지식·감성 대화를 공식 기능으로 갖고 있다. 우리는 웨이크워드도 없다. 연구상 긴 잡담은 인지 부하를 키운다(Dittrich 2025, CNBC의 Grok 비판) |
| 음성으로 차량 제어("에어컨 켜줘") | Gleo가 좌석 인식까지 하며 제공한다. 우리는 주행 중 set이 거부된다. 실제 수요도 3.5%로 낮다 |
| 경로 주변 휴게소·주유소 추천, 유가 | 2026-10-30 Gleo OTA에 그대로 들어갔다 |
| 문자 읽기, 전화 | Gleo OTA와 폰 연동 영역이다 |
| "곡명으로 음악 틀어줘" | Gleo와 멜론·스포티파이 영역이다(Gleo도 아직 못 하지만 곧 앱 연동으로 할 것이다) |
| 매뉴얼 Q&A("이 버튼 뭐야") | Gleo 공식 기능이다 |
| 자극적이거나 과하게 사람 같은 페르소나 | Grok 사례, 의인화가 신뢰를 낮춘다는 연구(Cheng 2022, Rosenthal-von der Pütten 2026), 아이 동승 위험 |
| "LLM이 판단해서 차를 바로 조작" | LLM의 잘못된 실행 위험(Afroze 2026, CAR-bench). 확인 단계와 규칙 기반 검사 계층이 필요하다 |

### 7.3 이길 수 있는 틈: 원칙

Gleo는 **"불러야 오는 범용 비서"**다. 우리 앱은 반대로 간다. 원칙은 다섯 가지다.

1. **특정 상황 하나를 끝까지 책임진다.** 도메인에 특화된다.
2. **차량 신호로 먼저 말을 건다.** 선제적이되 "제안 + 한 단어 확인" 방식이다.
3. **답을 받으면 실행까지 닫는다.** NaviHelper 경유지 설정이나 P단 차량 제어로 끝낸다.
4. **짧게 말하고, 주제별로 끌 수 있다.**
5. **이유를 말한다.** 예: "2시간 넘게 달리셨어요" 같은 근거를 붙인다.

근거 연결
- 운전 관련 선제 제안 수락률 82.5%(Schmidt 2020)
- "제안 후 확인" 선호(Du 2024)
- 주제별 on/off 요구 33/42
- 이유를 붙인 메시지의 효과(Koo 2015)
- AI를 써도 "AI인 줄 모름" 문제(컨슈머인사이트) → 먼저 나타나야 체감된다
- 동승석 화면 35% 미사용(J.D. Power 2026)

### 7.4 구체적 후보 (제약 안에서 만들 수 있는지 기준)

| 후보 | 트리거(입력) | 대화 형태 | 실행(출력) | Gleo와 차별점 | 위험·확인할 것 |
|---|---|---|---|---|---|
| **A. 장거리 졸음·휴식 코치** | Vehicle 트립 통계(주행 시간·거리), NaviHelper 남은 시간·거리, 시각(야간·식후), 외부 날씨 API | 주행 후반에 **대답형 미니 퀴즈나 질문**을 짧게, 드물게(Zhang 2025, Atchley 2011, Huang 2024). 휴식은 "명령"이 아니라 근거를 붙여 제안 | NaviHelper 경유지 추가(최대 2개) | Gleo의 휴게소 추천은 "찾아 주기", 우리는 "언제 쉬어야 하는지 판단하고 깨어 있게 하기" | "휴식하세요"는 거절률 61% → 말투와 시점이 핵심. 졸음을 직접 감지하는 센서(DMS)는 없음 → "피로 위험 추정"으로만 표현. 지루함과 각성은 측정 불가 |
| **B. 아이 동승 모드(뒷좌석 동반자)** | 좌석 재실·안전벨트(뒷좌석), 트립 시간, NaviHelper 남은 시간 | 아이 대상 퀴즈·끝말잇기·"언제 도착해?"에 남은 시간으로 답하기(Wilfinger 2011의 지루함 문제). 운전자는 관리하지 않아도 된다 | 남은 시간 안내, 안전벨트 풀림 감지 시 운전자에게 짧게 알림, P단 도착 후 무드등 연출 | Gleo는 뒷좌석 통화를 제한하는 방향(OTA). 아이 콘텐츠는 VW IDA 정도뿐이고 국내에는 비어 있음 | 운전자 귀에도 들린다 → 인지 부하. **운전자 우선 규칙 필요**. 뒷좌석 발화만 골라 받는 STT 좌석 구분은 문서에 없음. 아동 데이터 처리 |
| **C. P단 정차·대기 에이전트**(충전 대기, 픽업 대기, 차박·휴식) | 기어는 못 읽지만 set 성공 여부로 P단 추정. 재실, 도어·창문 상태, EV 배터리 | 대기 목적을 묻고("몇 분 쉬실 거예요?") 그에 맞게 실내 설정 | 시트 눕힘, 무드등, 공조, 창문(**P단에서는 제어 가능**), 출발 전 원복과 체크리스트(창문·도어) | Gleo는 개별 명령 실행. 우리는 "모드 단위"로 상황을 연출하고 원복까지 책임진다. 주행 제약을 피하므로 에뮬레이터 시연에도 강함 | 외부 사용 가치(MAU) 논리 필요. 시트·창문 capability가 차종마다 다를 수 있음 |
| **D. 경고등·차량 상태 설명 에이전트** | Vehicle 텔테일 콜백(점등 이벤트) | 점등 즉시 "무엇·왜·지금 할 일" 3줄로 말하고, 짧은 확인 질문 | 가까운 정비소·주유소를 NaviHelper 경유지로 | Gleo는 물으면 매뉴얼 Q&A. 우리는 점등 순간에 먼저 설명. Stellantis·Mistral, Cerence "오너십 에이전트"가 같은 방향을 표방했지만 국내 양산 사례 없음 | 경고등 이벤트는 드물다 → 단독으로는 리텐션이 약함(다른 후보의 하위 기능에 적합). 안전 정보 오류는 치명적 → 규칙 기반 문구 사용, LLM 금지 |
| **E. 도착 전 준비 브리핑** | NaviHelper 남은 시간이 N분 이하일 때, FusedLocation 근접 알림 | "도착 5분 전이에요. 비가 와요, 창문 닫혀 있어요" 같은 한 문장 | P단 도착 후 창문·도어 점검 안내, 다음 일정 | Gleo는 경유지 정보와 남은 시간을 제공. 우리는 "도착 시 할 일"을 묶어 준다 | 범용화되면 Gleo와 겹침 → 다른 후보와 결합 권장 |
| **F. 동승자 인지 모드** | 좌석 재실(조수석·뒷좌석), 안전벨트 | 동승자가 있으면 **말수를 줄이고** 화면 위주로, 혼자면 대화형으로 | — | "동승자 있으면 음성 안 쓴다"는 반복된 국내 반응(5.3)에 대응 | 기능이 아니라 모든 후보에 공통으로 넣을 정책 |

**[추론] 추천 조합**: **A(장거리 동반자)를 핵심으로, F를 정책으로, C를 정차 중 보상 장면으로** 넣는다. D와 E는 하위 기능이다.

- **주행 중**에는 TTS·STT·NaviHelper만으로 가치를 낸다. set API 제약을 받지 않는다.
- **정차(P단)**에서는 차량 제어로 "보상"을 연출한다. 에뮬레이터 시연이 확실하다.
- 심사 항목의 "차량특화성(주행·정차 맥락)"과 "기술 활용성(SDK 5종 모두)"을 동시에 충족한다.
- 아이가 주요 고객이면 B를 핵심으로 바꿀 수 있다.

### 7.5 설계 체크리스트 (연구 근거)

1. 한 턴에 한 문장, 한 가지 정보만 말한다. 응답은 1초 안에 시작하고, 2초를 넘으면 "확인 중이에요" 같은 중간 피드백을 준다. (TR-F 2026, Aalto, Kirmayr 2026)
2. 선제 발화는 이유 + 제안 + 예/아니오 확인으로 구성한다. 자동 실행은 하지 않는다. (Koo 2015, Du 2024, Meck 2023)
3. 턴 사이 간격을 충분히 둔다. 연속 질문은 금지한다. 잔여 주의 분산이 15~27초이기 때문이다. (Strayer)
4. 주제별 on/off와 "오늘은 그만" 한마디를 지원한다. (Schmidt 2020)
5. 실행 결과는 반드시 음성으로 확인해 준다. Gleo 시승기에서 지적된 공백이다. (한겨레·한국일보 2026)
6. 차량 제어와 경유지 설정은 **규칙 기반 검사 계층**을 거친다. LLM 출력을 그대로 실행하지 않는다. 사실(경고등 의미 등)을 지어내지 않는다. (Afroze 2026, CAR-bench)
7. 페르소나는 "도로를 보는 차분한 동승자"로 한다. 운전자 상태에 맞춰 톤을 바꾸되 가르치려 들지 않는다. 의인화는 낮게 한다. (Drews 2008, Nass 2005, Braun 2019, Cheng 2022)
8. 졸음 대응은 짧게, 드물게, 대답형으로 한다. 휴식 권유와 함께 쓰고 휴식을 대신하지 않는다. (Zhang 2025, Huang 2024)
9. 오디오 포커스는 `GAIN_TRANSIENT_MAY_DUCK`을 쓰고, Gleo가 호출되면 즉시 양보한다. (02 문서 기준)
10. 외부 LLM을 쓴다면 텍스트만 보내고, 위치·이름 같은 개인정보는 최소화한다. LLM이 없을 때의 대체 흐름도 준비한다.

### 7.6 기획서에 쓸 수 있는 문제 정의 문장 (근거 연결)

- "차량 AI 서비스 만족도는 커넥티드 8개 기능 중 최하(52%)이고 사용 경험은 9%다. 사용자는 AI를 쓰는지조차 모른다(컨슈머인사이트 2026-02)." 즉 **불러야 오는 AI는 체감되지 않는다**.
- "운전 관련 선제 제안 수락률 82.5%(Schmidt 2020)", "미국 차주 72%가 선제적 트립 제안을 원함(Verra 2026-10)". 즉 **맥락에 맞으면 먼저 말 거는 것을 환영한다**.
- "운전 중 대답형 대화는 주관적 졸음을 7.98에서 5.28로 낮췄다(Zhang 2025)". 즉 **장거리 졸음에 대한 근거 있는 개입**이다.

---

## 8. 출처

### 공식·보도 (글레오·현대차그룹)
- HMG 뉴스룸, Pleos Connect 공개, 2026-04-30 [원문]: https://www.hyundaimotorgroup.com/en/news/hyundai-motor-group-redefines-in-vehicle-experience-with-pleos-connect-next-generation-infotainment-system
- HMG 뉴스룸, 더 뉴 그랜저, 2026-05-14 [원문]: https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-company-the-new-grandeur
- HMG 뉴스룸, 글레오 AI 첫 OTA, 2026-10-30 [원문]: https://www.hyundaimotorgroup.com/ko/news/42dot-gleo-ai-first-ota-in-car-ai-experience-upgrade
- 42dot 블로그, Gleo AI 기술 공개, 2026-05-21 [원문]: https://42dot.ai/en/stories/blog-news/42dot-unveils-gleo-ai
- 전자신문, 2026-05-21 [원문]: https://www.etnews.com/20260521000105
- 아시아투데이, 2026-04-30 [원문]: https://www.asiatoday.co.kr/kn/view.php?key=20260430010009661
- 아시아투데이(아반떼), 2026-08-31 [스니펫]: https://www.asiatoday.co.kr/kn/view.php?key=20260831010010246
- 서울경제(영문), 2026-10-30 [원문]: https://en.sedaily.com/finance/2026/10/30/hyundai-rolls-out-first-pleos-connect-update-with-new-gleo
- 이코노미스트, 2026-05-31 [원문]: https://economist.co.kr/article/view/ecn202605280065
- ZDNet Korea, 2026-05-31 [원문]: https://zdnet.co.kr/view/?no=20260529223051
- 시사저널e, 2026-05-31 [원문]: https://www.sisajournal-e.com/news/articleView.html?idxno=421373
- 디지털타임스, 2026-06-07 [원문]: https://www.dt.co.kr/article/12066110
- 디지털타임스, 2026-08-09 [원문]: https://www.dt.co.kr/article/12077149
- 한국일보, 2026-06-13 [원문]: https://www.hankookilbo.com/news/article/A2026060816190002505
- 한겨레 via Supple, 2026-08-26 [원문]: https://supple.kr/news/cmt9bmd54009srsb7e5elpj04
- 동아일보 via 네이트, 2026-08-20 [원문, 글자 깨짐]: https://news.nate.com/view/20260820n00313
- 동아일보 via 네이트, 2026-09-28 [원문 글자 깨짐, 세부는 스니펫]: https://m.news.nate.com/view/20260928n00332
- 네이트 전재, 2026-09-13 [원문, 글자 깨짐]: https://m.news.nate.com/view/20260913n03513
- motorpd(아반떼 공개) [스니펫]: https://www.motorpd.com/news/articleView.html?idxno=11550
- 나무위키 Pleos Connect [원문, 2차 자료]: https://namu.wiki/w/Pleos%20Connect

### 해외 플레이어
- Mercedes CLA, 2025-07-16: https://media.mbusa.com/news/the-all-new-mercedes-benz-cla-gorgeous-effortless-intuitive-and-flexible
- InsideEVs, 2025-12-26: https://insideevs.com/news/782831/mercedes-cla-ai-assistant-review/
- How-To Geek, 2026-08-22: https://www.howtogeek.com/mercedes-c-class-three-ai-assistants/
- Forbes(Mercedes·Google), 2025-01-13 [스니펫]: https://www.forbes.com/sites/sashalekach/2025/01/13/mercedes-adds-ai-powered-conversational-navigation-with-google-gemini/
- BMW, 2026-01-05: https://www.bmwgroup.com/en/news/general/2026/alexa.html
- Cerence(VW IDA), 2025-08-25: https://investors.cerence.com/news-events/press-releases/detail/348/
- TechCrunch(VW 미국 출시), 2024-09-03 [스니펫]: https://techcrunch.com/2024/09/03/volkswagen-is-rolling-out-its-chatgpt-assistant-to-the-us/
- Tesla 2025.26 릴리스 노트: https://www.notateslaapp.com/software-updates/version/2025.26/release-notes
- Motor1(Grok), 2026-09-02: https://www.motor1.com/news/806866/tesla-grok-summer-update-brings/
- OECD AI 사건 기록, 2025-10-28 [스니펫]: https://oecd.ai/en/incidents/2025-10-28-911b
- CNBC(Grok), 2026-04-25 [스니펫]: https://www.cnbc.com/2026/04/25/tesla-and-xais-grok-shows-promises-and-risks-of-ai-chatbots-in-cars.html
- TechCrunch(GM Gemini), 2025-10-22: https://techcrunch.com/2025/10/22/gm-is-bringing-google-gemini-powered-ai-assistant-to-cars-in-2026
- TechCrunch(OnStar AI), 2026-10-21: https://techcrunch.com/2026/10/21/gm-is-giving-onstar-an-ai-makeover/
- 9to5Google(Android Auto Gemini), 2025-11-06: https://9to5google.com/2025/11/06/android-auto-gemini-starts-rolling-out/
- 9to5Google(Android Automotive Gemini), 2026-04-30: https://9to5google.com/2026/04/30/gemini-coming-to-android-automotive/
- Cars.com(Honda Gemini), 2026-07-17: https://www.cars.com/articles/honda-acura-add-google-gemini-ai-to-cars-what-does-it-do-527152/
- Apple Newsroom(Siri AI), 2026-06: https://www.apple.com/newsroom/2026/06/apple-introduces-siri-ai-a-profoundly-more-capable-and-personal-assistant/
- MacRumors(Gemini on CarPlay), 2026-11-11: https://www.macrumors.com/2026/11/11/google-gemini-apple-carplay/
- CnEVPost(NOMI GPT), 2024-04-12: https://cnevpost.com/2024/04/12/nio-rolls-out-nomi-gpt/
- Gasgoo(Li Auto): https://autonews.gasgoo.com/articles/icv/70036017
- news18a(Xpeng), 2026-02-04: https://english.news18a.com/m/news/english_229601.html
- CNBC(BYD·DeepSeek), 2025-02-11: https://www.cnbc.com/2025/02/11/byd-rolls-out-driver-assist-tech-for-evs-with-deepseeks-ai-help.html
- CarNewsChina, 2025-02-08 [스니펫]: https://carnewschina.com/2025/02/08/chinese-automakers-are-accelerating-deepseek-ai-model-adoption-to-power-smart-cars
- Cerence CES 2026: https://investors.cerence.com/news-events/press-releases/detail/361/
- SoundHound CES 2025 [스니펫]: https://www.soundhound.com/newsroom/press-releases/ces-2025-soundhound-ai-debuts-its-first-ever-in-vehicle-voice-assistant-with-on-the-go-food-ordering/

### 수요·조사
- Voicebot.ai + Cerence, 2020-01: https://voicebot.ai/wp-content/uploads/2020/02/in_car_voice_assistant_consumer_adoption_report_2020_voicebot.pdf
- Capgemini "Voice on the Go", 2019-11-14: https://www.capgemini.com/news/press-releases/voice-on-the-go-report/
- Cerence 블로그, 2020-01-05: https://www.cerence.com/newsroom/blog/new-survey-finds-drivers-lean-on-their-in-car-technology-to-keep-them-safe-while-driving
- Digital Trends(J.D. Power), 2019-04-18: https://digitaltrends.com/cars/jd-power-voice-assistants-in-cars-study
- J.D. Power 2014(F&I Magazine): https://www.fi-magazine.com/news/voice-recognition-tops-jd-powers-list-of-new-vehicle-problems
- J.D. Power 2021 IQS: https://china.jdpower.com/resources/smartphone-connectivity-now-most-common-problem-cited-new-vehicle-owners-jd-power-finds
- J.D. Power 2022 IQS(Cars.com): https://cars.com/amp/articles/2022-jd-power-initial-quality-study-new-vehicle-problems-hit-all-time-high-451148
- J.D. Power 2023 VDS(MacTech): https://www.mactech.com/2023/02/09/apple-carplay-android-auto-connectivity-issues-still-a-problem-for-many-drivers
- J.D. Power 2025 IQS: https://www.theautochannel.com/news/2025/06/26/1547392-fewer-physical-vehicle-controls-and-more-problematic-touchscreen-make-infotainment.html
- J.D. Power 2025 TXI: https://www.automotiveworld.com/news-releases/ai-based-technologies-improve-customer-experience-but-present-new-challenges-j-d-power-finds
- J.D. Power 2026 TXI(Carscoops): https://www.carscoops.com/2026/08/jd-power-tech-study-2026/
- J.D. Power 2025 China TXI: https://china.jdpower.com/press-releases/2025-china-tech-experience-index-txi-study
- J.D. Power X 계정, 2025-10 [스니펫]: https://x.com/JDPower/status/1975534071989117056
- 컨슈머인사이트 25차 조사, 2026-02-12: https://www.consumerinsight.co.kr/autoinsight/abview?no=3859&id=pr4_list
- 스마트비즈/아젠다북, 2026-11: https://www.smartbizn.com/news/articleView.html?idxno=158898
- 서울신문(J.D. Power 2014 인용) [스니펫]: https://www.seoul.co.kr/news/international/2014/08/29/20140829800032
- Verra Mobility(Repairer Driven News), 2026-10-16: https://www.repairerdrivennews.com/2026/10/16/survey-buyers-want-their-next-vehicle-to-function-like-a-smartphone/
- McKinsey China 2026 [스니펫]: https://www.mckinsey.com/cn/our-insights/our-insights/value-is-the-compass-consumers-set-the-pace
- Deloitte UK, 2025-01-10: https://www.deloitte.com/uk/en/about/press-room/uk-drivers-among-most-sceptical-globally-about-ai-in-cars.html
- Mozilla 자동차 프라이버시(TidBITS), 2023-09 [스니펫]: https://tidbits.com/2023/09/07/mozilla-says-modern-cars-are-data-collection-nightmares-on-wheels/
- AAA 2015 보도자료: https://newsroom.aaa.com/2015/10/new-hands-free-technologies-pose-hidden-dangers-for-drivers/

### 학술
- Schmidt, Minker & Werner 2020 (LREC): https://aclanthology.org/2020.lrec-1.61/
- Hamid, Li & Liang 2026 "When2Talk": https://arxiv.org/html/2609.12503v1
- Semmens et al. 2019 (CHI): https://www.tri.global/research/now-good-time-empirical-study-vehicle-driver-communication-timing
- Kim, Park & Lee 2020 (IMWUT): doi:10.1145/3381009
- Meck, Draxler & Vogt 2023 (IJHCI): doi:10.1080/10447318.2023.2266251
- Bader et al. 2011 (AutomotiveUI): doi:10.1145/2381416.2381424
- Du et al. 2024 (IJCAI): arXiv:2403.09135
- Mathis et al. 2024 (MuC): doi:10.1145/3670653.3670672
- Susak et al. 2026 ProVoice (CHI): arXiv:2601.19421
- Rosenthal-von der Pütten et al. 2026 (CHI): doi:10.1145/3772318.3790435
- Strayer et al. 2013/2015 (Human Factors): doi:10.1177/0018720815575149 / https://trid.trb.org/View/1252566
- Strayer et al. 2016 (CRPI): doi:10.1186/s41235-016-0018-3
- Strayer et al. 2017 (CJEP): doi:10.1037/cep0000104
- Strayer et al. 2019 (CRPI): doi:10.1186/s41235-019-0166-3
- Drews, Pasupathi & Strayer 2008: doi:10.1037/a0013119
- Monk et al. 2026: https://arxiv.org/abs/2601.15034
- Dittrich, Ko & Rieger 2025 (IVA): doi:10.1145/3717511.3749294
- Kirmayr et al. 2026 (CHI): arXiv:2602.15569
- Aalto 석사논문(응답 지연): https://aaltodoc.aalto.fi/items/1a421727-99f3-43b3-bf73-b2576db094e2
- TR-F 2026(음성 길이): https://www.sciencedirect.com/science/article/abs/pii/S1369847826001373
- Nass/Jonsson et al. 2005: doi:10.17077/drivingassessment.1158
- Braun et al. 2019 (CHI): doi:10.1145/3290605.3300270
- Braun et al. 2019 (MTI): doi:10.3390/mti3010021
- Braun, Weber & Alt 2021 (ACM CSUR): doi:10.1145/3460938
- Williams, Peters & Breazeal 2013 (AIDA): https://www.media.mit.edu/publications/towards-leveraging-the-drivers-mobile-device-for-an-intelligent-sociable-in-car-robotic-assistant/
- Cheng et al. 2022 (Frontiers): doi:10.3389/fpsyg.2022.883417
- Li et al. 2024 (in-car social robot): arXiv:2402.11853
- Bond et al. 2025: arXiv:2508.08101
- Ringfort-Felner et al. 2022 (Kiro): doi:10.1145/3492852
- Atchley & Chan 2011: doi:10.1177/0018720810391215
- Large et al. 2018: doi:10.1049/iet-its.2017.0201
- Zhang et al. 2025 (Applied Ergonomics): doi:10.1016/j.apergo.2025.104616
- Huang et al. 2024 (CHI EA): doi:10.1145/3613905.3651031
- Cockram et al. 2026 (CHI): arXiv:2510.25421
- Apostolopoulos et al. 2016: doi:10.1080/0164212X.2015.1093995
- Wilfinger et al. 2011 (INTERACT): https://dl.ifip.org/db/conf/interact/interact2011-2/WilfingerMMOT11.pdf
- Gordon & Breazeal 2015 (IDC): doi:10.1145/2771839.2771916
- Koo et al. 2015: doi:10.1007/s12008-014-0227-2
- Mercurio et al. 2023 (CHI): https://research.google/pubs/a-mixed-methods-approach-to-understanding-user-trust-after-voice-assistant-failures/
- Mahmood et al. 2022 (CHI): doi:10.1145/3491102.3517565
- Kirmayr, Stappen & André 2026 CAR-bench: arXiv:2601.22027
- Afroze et al. 2026: arXiv:2609.19630

### 커뮤니티 (5절 표에 URL을 모두 적음)
- 클리앙: 19184477, 19190818, 19190572, 17911626, 18840661, 15214185, 18367917, 15627118, 19205998
- FMKorea: 9967942953, 10533292642
- 루리웹: 77021434
- 보배드림: freeb/3448462
- DC인사이드: car_new1/11296814, dcbest/425368
- 다모앙: car/128084 [스니펫, 403]
- YouTube 4건(위 5.1)

### 내부 문서
- `docs/pleos-docs/00-contest-and-ecosystem.md`: 공모전 허용 SDK, 심사 기준
- `docs/pleos-docs/02-dev-environment-and-guides.md` 6절: Gleo Interface, 공모전에서 쓸 수 없는 이유
- `docs/pleos-docs/03-sdk-usage-guides.md` 2·5절: 50001 주행 제한, STT `request()` 방식, 모드
