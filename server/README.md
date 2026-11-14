# 중계 서버 (`server/`)

앱(옆자리)이 Claude API 키 없이 질문 답변과 주행 리뷰를 받을 수 있게, 키를 서버 환경 변수로만 갖고 요청을 Claude Messages API로 넘겨 주는 작은 서버예요.

**형태: Supabase Edge Function 한 파일**(`supabase/functions/relay/index.ts`, 의존성 없음). 고른 이유: 파일 하나를 CLI 명령 한 줄로 올릴 수 있고, 응답 스트리밍(SSE)을 그대로 흘려보낼 수 있고, 이 맥에 `supabase` CLI와 `deno`가 이미 있어서예요.

## 하는 일

- 앱이 보낸 Messages 요청 본문(시스템 글 + 사용자 글 한 개)에 서버의 키를 붙여 `https://api.anthropic.com/v1/messages`로 보내고, 응답(SSE 또는 JSON)과 상태 코드를 바꾸지 않고 돌려줘요. 그래서 앱의 `ClaudeWire` SSE 해석이 직접 호출 때와 똑같이 동작해요.
- 받는 것은 글자뿐이에요(질문 글자, 차량 상태 요약, 주행 요약). 음성·이미지·도구 블록이 들어 있으면 거절해요.
- 남용 방지
  - 앱 식별 헤더 `x-sideseat-app: io.moodlab.sideseat`가 없으면 403
  - 본문 160,000바이트 초과는 413
  - 허용한 모델 이름만, `max_tokens`는 0~4,000만, 사용자 메시지는 글자 한 개만(아니면 400)
  - 아는 항목(`model`, `max_tokens`, `stream`, `system`, `messages`, `thinking`, `output_config`, `fallbacks`)만 골라 넘기고 나머지는 버려요
- 로그에는 상태 코드와 거절 이유만 남겨요. 본문과 키는 남기지 않아요.
- 앱 식별 헤더는 APK에서 읽을 수 있는 값이라 비밀이 아니에요. 호출 횟수 제한은 없으니, 공개 뒤 남용이 걱정되면 Anthropic Console에서 이 키의 사용 한도를 낮게 걸어 두세요.

## 환경 변수

| 이름 | 내용 |
| --- | --- |
| `ANTHROPIC_API_KEY` | Claude API 키. 서버에만 등록해요. 저장소·문서·로그 어디에도 값을 적지 않아요. |

## 배포 (사용자가 직접)

1. Supabase 계정과 프로젝트가 필요해요. 프로젝트 참조 아이디(`<ref>`)를 확인하세요.
2. 로그인하고 프로젝트를 연결해요.

```bash
cd server && supabase login
```

```bash
cd server && supabase link --project-ref <ref>
```

3. 키를 서버에 등록해요. 값은 본인 터미널에서만 입력하세요.

```bash
cd server && supabase secrets set ANTHROPIC_API_KEY=<키 값>
```

4. 함수를 올려요. 앱은 Supabase 키 없이 부르므로 JWT 검사를 꺼요(`supabase/config.toml`에도 같은 설정이 있어요).

```bash
cd server && supabase functions deploy relay --no-verify-jwt
```

5. 앱에 주소를 넣어요. 커밋하지 않는 `local.properties`에 한 줄을 더하고 다시 빌드하면 debug·release 모두 중계 서버로 보내요.

```
LLM_RELAY_URL=https://<ref>.supabase.co/functions/v1/relay
```

`LLM_RELAY_URL`이 비어 있으면 지금처럼 동작해요(debug는 `LLM_API_KEY`로 직접 호출, release는 미리 쓴 문장).

## 로컬 실행·시험

단위 시험(실제 API를 부르지 않아요):

```bash
deno test server/relay_test.ts
```

로컬에서 띄우기(포트 8000). 키는 셸 환경 변수로만 주세요.

```bash
deno run --allow-net --allow-env=ANTHROPIC_API_KEY server/supabase/functions/relay/index.ts
```

요청 보내 보기:

```bash
curl -N http://localhost:8000/ -H 'x-sideseat-app: io.moodlab.sideseat' -H 'content-type: application/json' -d '{"model":"claude-haiku-5-5","max_tokens":100,"stream":true,"messages":[{"role":"user","content":"와이퍼는 어디 있어요?"}]}'
```

헤더를 빼면 403, 키 없이 띄우면 500이 와요.

에뮬레이터의 앱을 로컬 서버(`http://10.0.2.2:8000`)에 붙이는 것은 안 돼요. 앱이 평문 HTTP를 허용하지 않기 때문이에요. 앱과 붙여 볼 때는 배포한 https 주소를 쓰세요.
