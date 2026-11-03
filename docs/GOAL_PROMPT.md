# 다음 세션용 목표 프롬프트

아래 블록을 그대로 붙여 넣는다 (`/goal`, `/loop`, 또는 새 세션 첫 메시지).

---

저장소 kiheon0709/hmg-appchallenge-2026, 브랜치 `main-bpn5uz`에서 작업한다. 먼저 `docs/GOALS.md`, `docs/STATUS.md`, `README.md`, `docs/review/plan-review.md`를 읽어라. 현대차그룹 차량용 앱 공모전(Pleos Connect) 출품작 MooDrive이고 접수 마감은 11/15, 목표 제출일은 11/14다.

**목표: `docs/GOALS.md`의 G1~G3 통과 기준을 모두 만족시키는 것.** 아래 루프를 반복하고, 한 바퀴가 끝날 때마다 커밋하고 `git push -u origin main-bpn5uz` 한다.

1. **검증**: 아래 네 가지를 실행해 전부 통과해야 한다.
   - `./gradlew :core:test`
   - `./gradlew :core:demoTrace`
   - `node design/check.mjs --shots`, `node design/check-tokens.mjs`
   - `cd tools/app-compile-check && ../../gradlew compileKotlin -q` (오류 0)
   - Maven Central이 429를 주면 30~60초 기다렸다가 다시 시도한다.
2. **기획 심사 (G1.4)**: 결론을 모르는 독립 서브에이전트에게 심사를 맡긴다.
   - 심사 대상: `기획서_초안.md`(v0.3), `docs/research/user-evidence.md`, `docs/screens/*.png`, core 코드
   - 심사 방식: 심사 4축(차량특화성·창의성·기술활용성·사업성) 각 1~5점. 기획서 주장이 코드·근거·시안과 맞는지 직접 대조하게 한다.
   - 결과는 `docs/review/plan-review-v3.md`에 쓴다. 이전 리뷰(`plan-review.md`, 2.8점)의 지적이 해소됐는지도 표로 확인하게 한다.
3. **고치기**:
   - 평균 4.0 미만이면 상위 지적부터 고친다. 기획서 문장, 코드, 시안 중 필요한 쪽을 고치고, 코드를 고치면 테스트를 추가한다.
   - 기획서의 모든 주장은 코드·테스트·근거 문서로 뒷받침돼야 한다. 구현하지 않은 것은 '예정'으로 적는다.
4. **반복**: 1로 돌아간다. 평균 4.0 이상이 되거나 3바퀴를 돌면 멈춘다.

**정리할 것**
- README의 "DJ 10분 간격"을 코드 기준인 5분으로 고친다.
- `docs/STATUS.md`에 현재 상태(테스트 수, 심사 점수, 남은 일)를 반영한다.
- 기획서 숫자와 시안·트레이스 숫자를 일치시킨다. 장면 전환 횟수, 곡 수, 테스트 수가 대상이다.

**하지 말 것**
- Playground 프로젝트 Key나 비밀값을 커밋하지 않는다.
- 근거 없는 수치를 만들지 않는다. 확인하지 못한 수치는 '가정치'로 표시한다.
- 테스트를 지우거나 약화해서 통과시키지 않는다.
- `main`에 푸시하지 않는다. PR도 만들지 않는다.

**클라우드에서 못 하는 것**
- Pleos SDK 연결, 에뮬레이터 실행, D단 앰비언트 시험은 클라우드에서 할 수 없다. `docs/MAC_HANDOFF.md`에 맥에서 할 일로 남긴다.

**마지막 보고**: 바퀴별 점수 변화, 고친 것, 아직 남은 지적, 맥에서 할 일을 짧게 정리한다.
