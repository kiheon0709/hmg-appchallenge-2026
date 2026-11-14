// 옆자리(SideSeat) 중계 서버. 앱이 보낸 글자 요청에 서버의 키를 붙여 Claude Messages API로 넘기고,
// 응답(SSE 또는 JSON)을 그대로 돌려준다. 키는 환경 변수 ANTHROPIC_API_KEY에만 있다.
//
// 받는 것: 앱이 만든 Messages 요청 본문(시스템 글, 사용자 글 한 개). 음성·이미지 블록은 받지 않는다.
// 로그에는 상태 코드와 거절 이유만 남긴다(본문·키는 남기지 않는다).

export const UPSTREAM = "https://api.anthropic.com/v1/messages";
export const APP_HEADER = "x-sideseat-app";
export const APP_ID = "io.moodlab.sideseat";
/** 요청 본문 상한(바이트). 시스템 글(차종 지식 포함)이 수십 KB라 넉넉히 둔다. */
export const MAX_BODY_BYTES = 160_000;
/** 앱이 쓰는 가장 큰 값(리뷰 총평 4,000)까지만. */
export const MAX_TOKENS = 4_000;
const DEFAULT_VERSION = "2026-10-21";
const MODELS = new Set([
  "claude-haiku-5-5",
  "claude-sonnet-5-5",
  "claude-opus-5-5",
  "claude-opus-5",
  "claude-fable-5-2",
  "claude-fable-5-1",
  "claude-fable-5",
]);

type Env = { ANTHROPIC_API_KEY?: string };

function fail(status: number, type: string, message: string): Response {
  console.log(`relay reject status=${status} type=${type}`);
  // 앱의 ClaudeWire.errorType이 읽는 모양과 같다.
  return new Response(JSON.stringify({ type: "error", error: { type, message } }), {
    status,
    headers: { "content-type": "application/json" },
  });
}

const isText = (v: unknown): v is string => typeof v === "string";

/** 시스템 글: 문자열이거나 text 블록 배열만. */
function validSystem(v: unknown): boolean {
  if (v === undefined || isText(v)) return true;
  return Array.isArray(v) && v.every((b) => b && b.type === "text" && isText(b.text));
}

/** 메시지: 사용자 글 한 개(문자열)만. 음성·이미지·도구 블록은 거절한다. */
function validMessages(v: unknown): boolean {
  return Array.isArray(v) && v.length === 1 && v[0]?.role === "user" && isText(v[0]?.content);
}

export async function handle(req: Request, env: Env, upstreamFetch: typeof fetch = fetch): Promise<Response> {
  if (req.method !== "POST") return fail(405, "method_not_allowed", "POST only");
  if (req.headers.get(APP_HEADER) !== APP_ID) return fail(403, "permission_error", "unknown client");
  const key = env.ANTHROPIC_API_KEY;
  if (!key) return fail(500, "api_error", "relay is not configured");
  if (Number(req.headers.get("content-length") ?? 0) > MAX_BODY_BYTES) return fail(413, "request_too_large", "body too large");

  const raw = new Uint8Array(await req.arrayBuffer());
  if (raw.byteLength > MAX_BODY_BYTES) return fail(413, "request_too_large", "body too large");
  let body: Record<string, unknown>;
  try {
    body = JSON.parse(new TextDecoder().decode(raw));
  } catch {
    return fail(400, "invalid_request_error", "body is not JSON");
  }
  if (!body || typeof body !== "object") return fail(400, "invalid_request_error", "body is not an object");
  if (!isText(body.model) || !MODELS.has(body.model)) return fail(400, "invalid_request_error", "model not allowed");
  const maxTokens = body.max_tokens;
  if (typeof maxTokens !== "number" || !Number.isInteger(maxTokens) || maxTokens < 0 || maxTokens > MAX_TOKENS) {
    return fail(400, "invalid_request_error", "max_tokens out of range");
  }
  if (!validSystem(body.system)) return fail(400, "invalid_request_error", "system must be text");
  if (!validMessages(body.messages)) return fail(400, "invalid_request_error", "one text user message only");

  // 아는 항목만 골라 넘긴다(도구·파일 등은 버린다).
  const out: Record<string, unknown> = {};
  for (const k of ["model", "max_tokens", "stream", "system", "messages", "thinking", "output_config", "fallbacks"]) {
    if (body[k] !== undefined) out[k] = body[k];
  }
  const headers: Record<string, string> = {
    "content-type": "application/json",
    "x-api-key": key,
    "anthropic-version": req.headers.get("anthropic-version") ?? DEFAULT_VERSION,
  };
  const beta = req.headers.get("anthropic-beta");
  if (beta) headers["anthropic-beta"] = beta;

  let upstream: Response;
  try {
    upstream = await upstreamFetch(UPSTREAM, { method: "POST", headers, body: JSON.stringify(out) });
  } catch {
    return fail(502, "api_error", "upstream unreachable");
  }
  console.log(`relay upstream status=${upstream.status} model=${body.model} stream=${body.stream === true}`);
  // 본문을 건드리지 않고 그대로 흘려보낸다 → 앱의 SSE 해석이 직접 호출 때와 같다.
  return new Response(upstream.body, {
    status: upstream.status,
    headers: {
      "content-type": upstream.headers.get("content-type") ?? "application/json",
      "cache-control": "no-cache",
    },
  });
}

if (import.meta.main) {
  Deno.serve((req) => handle(req, { ANTHROPIC_API_KEY: Deno.env.get("ANTHROPIC_API_KEY") }));
}
