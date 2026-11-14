// deno test server/relay_test.ts — 실제 API를 부르지 않는다(가짜 upstream).
import { APP_HEADER, APP_ID, handle, MAX_BODY_BYTES } from "./supabase/functions/relay/index.ts";

function eq(a: unknown, b: unknown, what = "") {
  if (JSON.stringify(a) !== JSON.stringify(b)) throw new Error(`${what} expected ${JSON.stringify(b)} got ${JSON.stringify(a)}`);
}

const env = { ANTHROPIC_API_KEY: "test-key" };
const good = {
  model: "claude-haiku-5-5",
  max_tokens: 200,
  stream: true,
  system: [{ type: "text", text: "s", cache_control: { type: "ephemeral" } }],
  messages: [{ role: "user", content: "와이퍼 어디 있어?" }],
  output_config: { effort: "low" },
};

function post(body: unknown, headers: Record<string, string> = { [APP_HEADER]: APP_ID }) {
  return new Request("http://relay.test/", { method: "POST", headers, body: typeof body === "string" ? body : JSON.stringify(body) });
}

const never: typeof fetch = () => { throw new Error("upstream must not be called"); };

Deno.test("키를 붙여 넘기고 SSE를 그대로 돌려준다", async () => {
  const sse = 'event: content_block_delta\ndata: {"type":"content_block_delta","delta":{"type":"text_delta","text":"네"}}\n\n';
  let seen: { url: string; headers: Headers; body: Record<string, unknown> } | undefined;
  const upstream = (async (url: string, init: { headers: Record<string, string>; body: string }) => {
    seen = { url: String(url), headers: new Headers(init.headers), body: JSON.parse(init.body) };
    return new Response(sse, { status: 200, headers: { "content-type": "text/event-stream" } });
  }) as unknown as typeof fetch;
  const res = await handle(post({ ...good, tools: [{ name: "x" }] }), env, upstream);
  eq(res.status, 200);
  eq(res.headers.get("content-type"), "text/event-stream");
  eq(await res.text(), sse);
  eq(seen!.url, "https://api.anthropic.com/v1/messages");
  eq(seen!.headers.get("x-api-key"), "test-key");
  eq(seen!.body.tools, undefined, "모르는 항목은 버린다");
  eq(seen!.body.messages, good.messages);
});

Deno.test("upstream 오류 상태와 본문을 그대로 돌려준다", async () => {
  const err = '{"type":"error","error":{"type":"overloaded_error","message":"x"}}';
  const res = await handle(post(good), env, async () => new Response(err, { status: 529, headers: { "content-type": "application/json" } }));
  eq(res.status, 529);
  eq(await res.text(), err);
});

Deno.test("앱 식별 헤더가 없으면 403", async () => {
  const res = await handle(post(good, {}), env, never);
  eq(res.status, 403);
  eq((await res.json()).error.type, "permission_error");
});

Deno.test("본문이 상한을 넘으면 413", async () => {
  const big = { ...good, system: "가".repeat(MAX_BODY_BYTES) };
  eq((await handle(post(big), env, never)).status, 413);
});

Deno.test("허용하지 않는 모델·토큰 수·음성 블록은 400", async () => {
  eq((await handle(post({ ...good, model: "other-model" }), env, never)).status, 400);
  eq((await handle(post({ ...good, max_tokens: 100000 }), env, never)).status, 400);
  const audio = { ...good, messages: [{ role: "user", content: [{ type: "audio", source: {} }] }] };
  eq((await handle(post(audio), env, never)).status, 400);
  eq((await handle(post("not json"), env, never)).status, 400);
});

Deno.test("서버에 키가 없으면 500", async () => {
  const res = await handle(post(good), {}, never);
  eq(res.status, 500);
});

Deno.test("POST만 받는다", async () => {
  eq((await handle(new Request("http://relay.test/", { headers: { [APP_HEADER]: APP_ID } }), env, never)).status, 405);
});
