# 진행 상황 (2026-11-07 갱신)

노션 메인 페이지가 원본이고, `docs/notion/`은 11-04 백업이다. 목표와 통과 기준은 `docs/GOALS.md`.

## 결정

- **아이디어**: 5번 맥락 기반 음악 추천 **MooDrive**로 확정 (사용자 확인 11-07). 2순위 카시트 달래기, 3순위 뒷좌석 놀이터. 노션 메인 할 일도 11-03에 갱신함.
- **Playground**: 프로젝트 `hmg-appchallenge-2026`, 개발사명 "기어르", 카테고리 음악/오디오. SDK 신청과 권한 화면 확인은 아직.
- **조명 정책**: 앰비언트 set의 P단 밖 허용 여부를 확인하기 전까지 실내 조명은 "P단에서 적용"이 기본값. N단·D단 신호대기는 P단이 아님. `ERROR_DRIVING_RESTRICTED`를 받으면 주행 중 전환을 멈추고 다음 P단에 재적용. 주행 중에는 앱 화면 무드 색으로 보여 주고, 데모 패널에서 허용 모드로 전환할 수 있음.

## 현황

| 항목 | 상태 |
|---|---|
| core 로직 | 자동 테스트 80개 통과 (`./gradlew :core:test`) |
| 데모 시나리오 | 3분 동안 장면 전환 7회(정차 제외), 곡 10개, DJ 멘트 7회 (테스트로 고정) (`./gradlew :core:demoTrace`) |
| 디자인 | 화면 3종. 자동 검사(조작 수·터치·글자·실측 대비) 1920×720, 1408×792 모두 통과. 토큰 동기화 검사 통과 |
| app (Android) | Pleos Vehicle·NaviHelper SDK 연결, 에뮬레이터 실행 확인 (11/07). 음원 재생·오디오 포커스, Pleos TTS/STT, 휴게소 경유지는 아직 |
| 기획서 | 독립 심사 점수: v0.2 2.8 → 1바퀴 2.9 (`plan-review-v3.md`) → 2바퀴 3.1 (`plan-review-r2.md`) → 3바퀴 3.3 (`plan-review-r3.md`) → 4바퀴 3.35 (`plan-review-r4.md`) → 5바퀴 3.50 (`plan-review-r5.md`) → 6바퀴 3.53 (`plan-review-r6.md`) → 7바퀴 **3.55** (`plan-review-r7.md`). 상승폭이 0.02까지 줄어 클라우드 루프 종료. 심사 판단: SDK·APK·영상 없이 도달 가능 3.7~3.8, 4.0+는 실측 1건 필요 |
| 음원 | 자리표시 66곡(장르마다 차분한 곡·밝은 곡 포함). 실제 음원 태깅은 `docs/music-tagging.md` 기준으로 진행 예정 |

## 운영사무국 답변 (10-29 문의, 11-06 수신)

| 항목 | 답변 | 상태 |
|---|---|---|
| 주행 제한 기준 | Vehicle Service는 NO_SETUP이 아니라 P단 여부를 확인. 시트 이동·메모리 선택·도어 제어 등 제한 대상 set은 P단이 아니면 `ERROR_DRIVING_RESTRICTED` | 확인 |
| 주행 중 앰비언트·공조 | 일괄 확정 답 아님. 추가 안내 예정 | **미확정**, 에뮬레이터 실측 예정 |
| 에뮬레이터 조명 표시 | 눈으로 확인할 방법 없음. 조명 API·실차 미지원이라는 뜻은 아님 | 확인 → 앱 미리보기와 API 응답으로 시연 |
| 개발·배포 권한 | 개발·시험은 매니페스트 선언만으로 가능. 배포 시 승인된 권한만 심사 통과. 공개 문서 외 API 없음, 별도 신청 절차 없음 | 확인 |
| NaviHelper 잔여 시간·거리 | 공식 안내는 NaviHelper intro 문서. `onTripInfo`·`onDrivingInfo` 콜백 존재, 필드 문서는 비어 있음 | 실측 예정 |
| PT 세부사항 | 서류 통과팀에 개별 안내. 일정은 공지사항 | 확인 |
| 오디오 | 재생 전 포커스 요청. GAIN 재생, LOSS 정지, LOSS_TRANSIENT 일시정지 후 복귀, CAN_DUCK 감쇄. 동시 재생은 보장되지 않고 가장 보수적인 결과 적용. TTS라는 이유로 NAVIGATION 컨텍스트 불가 | 확인, 앱 미구현 |
| 차속, Coming Soon·개별 API, PT 장비 | 추가 안내 예정 | **미확정** |

## 맥 검증 결과 (2026-11-07, Pleos Connect Emulator 2.1.0 / Vehicle SDK 2.3.2 / NaviHelper 3.0.5)

로그 태그 `MooDriveMeasure`, 신호는 Virtual Console(`adb forward tcp:8088 tcp:8088`). 화면은 `docs/screens/emulator/`.

| 항목 | 결과 |
|---|---|
| 앱 실행 | 설치·실행 정상. 앱 영역 약 1680×1200, 나머지는 차량 UI가 차지 |
| 기어 | 공개 SDK에 없음. AAOS `VehiclePropertyIds.GEAR_SELECTION` + `android.car.permission.CAR_POWERTRAIN`으로 수신 (P=4, N=1, D=8) |
| 와이퍼 | State ON/OFF 알림 수신. Switch: Low=`CONTINUOUS_LEVEL_1`, High=`CONTINUOUS_LEVEL_3`. **단계만 바뀌면 알림이 없어** 1초마다 스위치 조회로 해결 → 비/폭우 장면 전환 확인 |
| 하향등 | `lowBeam state` ON/OFF 수신 |
| 앰비언트 색·밝기 | `areas=[ROW_1_LEFT, ROW_1_RIGHT, ROW_2_LEFT, ROW_2_CENTER, ROW_2_RIGHT]` 한 그룹. 고정 영역(ROW_1_LEFT 단독)은 `area ID: 0x1 not supported` 실패 → 지원 영역 사용. **P단·D단 모두 set 성공, 재조회 값 일치** (예: 요청 ff47ebff → 조회 47ebff, 알파 없음) |
| 주행모드 자동 색 | `getAmbientColorAutoByDriveModeEnabled`는 에뮬레이터 미지원 (`0x21200787` not supported) → 복원 로직만 유지 |
| 제한 대상 set (도어로 시험) | P단 성공, **D·N단 실패**. 오류는 `java.lang.Exception`에 메시지 `IllegalArgumentException: 50001`, `getErrorCode`는 없음 → 메시지 `50001`로 판별. N단을 P단으로 보지 않게 한 수정이 맞음을 확인 |
| 내비 | `requestRoute` 후 `onDrivingInfo`: `destination.duration=60`(초), `distance=50`(m). `requestTrip` 후 `onTripInfo`: `eta=2, distance=12`, 정체 구간 포함. 주행 중 갱신 주기는 미확인 |

해석 주의: 에뮬레이터에서 D단 앰비언트 set이 성공했지만 **실차 허용을 뜻하지 않는다.** 운영사무국 공식 답변 전까지 기본값은 P단 적용 유지.

발견한 문제와 남은 확인:
- 표준 Car API로 기어를 읽는 방식이 App Market 심사를 통과하는지 미확인 (운영사무국 답변: 공개 문서 외 Pleos API는 없음)
- 내비 앱이 앞으로 오면 앱 로그가 멈춤(백그라운드 프로세스 정지로 추정) → 음악 앱으로 쓰려면 Media3 포그라운드 서비스 필요
- 음성 인식·TTS 에뮬레이터 동작, release APK는 아직

## 남은 일

- 맥: `docs/MAC_HANDOFF.md` (빌드, Pleos SDK 연결, D단 앰비언트 시험, 음원, 시연 영상)
- **다음 맥 작업**: Virtual Console과 앱 화면을 나란히 찍은 20~30초 영상, release APK(`apk/MoodLab_MooDrive_release.apk`), 로열티프리 음원 + Media3 포그라운드 재생 + 오디오 포커스
- 7바퀴 심사 뒤 클라우드에서 추가 반영(재심사 안 함): 정체 중에도 피날레, 휴게소 응답 양방향 표현("집에 가자"·"휴게소 바로 가자"·"네 알겠어요"·"이거 별로야"), 시나리오 ③ 크루징 문장, 기술 스택 (예정) 표기
- 클라우드에 남은 작은 일: 앱 시계 표시가 신호 사이에 멈추는 문제, 각주 순서
- 제출 직전: 접수양식 DOCX에 옮기기, 전원 서명 동의서, 근거 원문 재확인, GitHub 심사 계정 초대

## 작업 환경 메모

- 클라우드에서는 `dl.google.com`(Android SDK·AGP), `document.pleos.ai`, `nexus-playground.pleos.ai`가 막혀 있음. 환경 설정의 허용 도메인에 추가하면 다음 세션부터 Android 빌드 가능.
- Maven Central이 가끔 429를 줌 → 30~60초 기다렸다가 다시 시도.
- Playground 프로젝트 Key는 커밋하지 말 것 (`local.properties` 사용).
