// cuts.json을 읽어 (1) 클립을 잘라 assets/media에 두고 (2) 클립 장면 파일과 index.html을 만든다.
// 클립 구간을 바꾸려면 cuts.json의 start·end만 고치고 이 스크립트를 다시 돌린다.
import { readFileSync, writeFileSync, existsSync, mkdirSync, statSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const cuts = JSON.parse(readFileSync(join(root, "cuts.json"), "utf8"));
const media = join(root, "assets/media");
mkdirSync(media, { recursive: true });
const ff = (args) => execFileSync("ffmpeg", ["-v", "error", "-y", ...args], { stdio: ["ignore", "inherit", "inherit"] });
const VW = 1160, VH = 822; // 화면 안 영상 자리(앱 창 비율 1707:1210)

// ---------- 1. 클립 자르기 ----------
// 원본(webm)은 프레임 간격이 일정하지 않다. 먼저 영상과 소리를 함께 고정 30fps 중간 파일로 바꾸고(src_*.mp4),
// 거기서 정확한 시각으로 잘라 낸다. 영상과 소리가 같은 시간축에서 잘려 싱크가 어긋나지 않는다.
const sha = (v) => createHash("sha1").update(JSON.stringify(v)).digest("hex").slice(0, 10);
const fresh = (file, key) => existsSync(file) && existsSync(file + ".key") && readFileSync(file + ".key", "utf8") === key;
const clips = [];
for (const c of cuts.clips) {
  const src = resolve(root, c.src.startsWith("..") ? c.src : join(cuts.source_dir, c.src));
  if (c.enabled === false || !existsSync(src)) { console.log(`skip ${c.id} (${c.enabled === false ? "disabled" : "no file"})`); continue; }
  // parts: 원본의 여러 구간을 이어 붙인다. speed가 1보다 크면 그 구간은 빨리 돌리고 소리를 끈다. label은 그 구간 동안 화면 아래에 뜨는 자막
  const parts = (c.parts || [{ start: c.start, end: c.end }]).map((p) => ({ speed: 1, ...p }));
  let off = 0;
  for (const p of parts) { p.at = +off.toFixed(3); p.len = +((p.end - p.start) / p.speed).toFixed(3); off += p.len; }
  const dur = +off.toFixed(3);
  const crop = c.crop === undefined ? cuts.crop : c.crop;
  const gain = c.gain_db === undefined ? cuts.gain_db : c.gain_db;
  const st = statSync(src);
  const name = c.src.replace(/^.*\//, "").replace(/\.[^.]+$/, "");
  const inter = join(media, `src_${name}.mp4`), ikey = sha([crop, st.mtimeMs, st.size, VW, VH]);
  if (!fresh(inter, ikey)) {
    console.log(`transcode ${c.src}`);
    const vf = (crop ? `crop=${crop},` : "") + `scale=${VW}:${VH}:force_original_aspect_ratio=increase,crop=${VW}:${VH},fps=30`;
    ff(["-i", src, "-vf", vf, "-fps_mode", "cfr", "-af", "aresample=async=1:first_pts=0", "-c:v", "libx264", "-crf", "15", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-g", "15", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", inter]);
    writeFileSync(inter + ".key", ikey);
  }
  const mp4 = join(media, `${c.id}.mp4`), m4a = join(media, `${c.id}.m4a`), key = sha([ikey, parts, gain]);
  if (!fresh(mp4, key) || !existsSync(m4a)) {
    console.log(`cut ${c.id}: ${c.src} ${parts.map((p) => `${p.start}~${p.end}${p.speed > 1 ? "x" + p.speed : ""}`).join(", ")} (${dur}s)`);
    // 영상과 소리를 같은 입력에서 같은 구간으로 잘라 이어 붙인다(프레임 단위로 정확하게)
    const fc = parts.map((p, i) =>
      `[0:v]trim=start=${p.start}:end=${p.end},setpts=(PTS-STARTPTS)/${p.speed},fps=30[v${i}];` +
      `[0:a]atrim=start=${p.start}:end=${p.end},asetpts=PTS-STARTPTS${p.speed > 1 ? `,atempo=${Math.min(p.speed, 2)}${p.speed > 2 ? `,atempo=${p.speed / 2}` : ""},volume=0` : ""}[a${i}];`).join("") +
      parts.map((_, i) => `[v${i}][a${i}]`).join("") + `concat=n=${parts.length}:v=1:a=1[v][a0];[a0]volume=${gain}dB,alimiter=limit=0.9[a]`;
    ff(["-i", inter, "-filter_complex", fc, "-map", "[v]", "-c:v", "libx264", "-crf", "17", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-g", "30", "-movflags", "+faststart", mp4, "-map", "[a]", "-c:a", "aac", "-b:a", "160k", m4a]);
    writeFileSync(mp4 + ".key", key);
  }
  clips.push({ ...c, dur, parts });
}
const clip = Object.fromEntries(clips.map((c) => [c.id, c]));

// ---------- 2. 장면 순서 ----------
// 그래픽 장면: 애니메이션은 원래 길이(orig) 기준으로 짜여 있고, 실제 길이(dur)에 맞춰 빠르게 돌린다(FX.fit)
// HOLD: 아무것도 움직이지 않는 구간 중 잘라 낼 부분(장면 원래 시간축의 초). 렌더한 뒤 scripts/qa.py가 찾은 1.5초 넘는 정지 구간에서 1.3초만 남기고 뺀 값
const HOLD = {"problem": [[14.39, 16.39], [21.48, 23.03], [30.11, 31.0]], "who": [[6.8, 7.2], [9.8, 12.0]], "moments": [[4.27, 4.64], [7.2, 7.75], [10.77, 13.0]], "train-intro": [[5.1, 8.5]], "sdk": [[13.68, 14.82], [17.86, 19.0]], "numbers": [[11.06, 14.5]], "roadmap": [[3.77, 4.46], [9.77, 12.0]]};
const g = (id, orig, dur0, sfx = []) => {
  const k = orig / dur0, cuts = HOLD[id] || [];
  const map = (t) => (t - cuts.reduce((n, [a, b]) => n + Math.max(0, Math.min(t, b) - a), 0)) / k; // 원래 시각 → 줄인 뒤 시각
  return { id, dur: +map(orig).toFixed(3), fit: { k: +k.toFixed(4), orig, cuts }, sfx: sfx.map(([at, n, v]) => [map(at), n, v]), kind: "g" };
};
const seenGroup = new Set();
// 클립 장면. 그 묶음(챕터)의 첫 클립 앞에는 이름표 장면을 넣는다
const c = (id) => {
  const k = clip[id];
  if (!k) return [];
  const out = [];
  if (k.group && !seenGroup.has(k.group)) { seenGroup.add(k.group); out.push({ id: `chap-${k.group}`, dur: 2.2, kind: "chap", group: cuts.groups[k.group], sfx: [[0.1, "impact-bass-1", 0.35]] }); }
  out.push({ id: `clip-${id}`, dur: k.dur, kind: "c", clip: k, sfx: [] });
  return out;
};
const scenes = [
  g("title", 7.5, 3.5, [[0.15, "pop", 0.4], [1.3, "whoosh", 0.3]]),
  g("problem", 31, 14, [[0.5, "pop", 0.35], [1.5, "click-soft", 0.5], [6.4, "whoosh-cinematic", 0.3], [17.5, "pop", 0.4], [18.0, "pop", 0.4], [18.5, "pop", 0.4], [24.4, "pop", 0.45]]),
  g("who", 12, 6, [[1.4, "pop", 0.4], [2.7, "pop", 0.4], [4.0, "pop", 0.4]]),
  g("moments", 13, 7, [[1.4, "pop", 0.4], [4.6, "pop", 0.4], [7.8, "pop", 0.4]]),
  ...c("onboard"),
  g("train-intro", 8.5, 4, [[0.1, "impact-bass-1", 0.4]]),
  ...c("rain"), ...c("fog"), ...c("stop"),
  ...c("tunnel"), ...c("glare"), ...c("drowsy"),
  ...c("roundabout"), ...c("school"), ...c("merge"), ...c("toll"), ...c("hipass"),
  ...c("breakdown"), ...c("accel"),
  g("route", 12, 7, [[1.6, "pop", 0.4], [3.0, "click-soft", 0.5], [3.8, "click-soft", 0.5], [4.6, "click-soft", 0.5], [6.2, "whoosh-short", 0.3]]),
  ...c("coach"), ...c("review"), ...c("talk"),
  g("sdk", 19, 10, [[1.5, "pop", 0.4], [3.9, "pop", 0.4], [6.3, "pop", 0.4], [8.7, "pop", 0.4], [11.1, "pop", 0.4]]),
  g("numbers", 14.5, 8, [[1.4, "click-soft", 0.5], [3.6, "click-soft", 0.5], [5.8, "click-soft", 0.5], [8.0, "click-soft", 0.5]]),
  g("roadmap", 12, 7, [[1.4, "pop", 0.4], [4.4, "pop", 0.4], [7.4, "pop", 0.4]]),
  g("ending", 10, 5, [[0.2, "impact-bass-1", 0.4]]),
];
let t = 0;
for (const s of scenes) { s.start = +t.toFixed(3); t += s.dur; }
const total = +t.toFixed(3);
const SFX = { pop: 0.72, whoosh: 0.575, "whoosh-short": 0.575, "whoosh-cinematic": 2.4, "click-soft": 0.366, "impact-bass-1": 2.116 };

// ---------- 3. 음악: 그래픽 구간에만(클립에는 앱의 실제 소리만) ----------
const firstClip = scenes.find((s) => s.kind === "c" && !s.clip.quiet), sdk = scenes.find((s) => s.id === "sdk");
const introLen = Math.min(114, +((firstClip ? firstClip.start : total) + 0.4).toFixed(2)) /* bgm-a 길이 114.5초 */, outroStart = sdk.start - 0.4, outroLen = +(total - outroStart).toFixed(2);
ff(["-i", join(root, "assets/music/bgm-a.mp3"), "-t", String(introLen), "-af", `afade=t=out:st=${(introLen - 2.2).toFixed(2)}:d=2.2`, join(root, "assets/music/bgm-intro.mp3")]);
ff(["-i", join(root, "assets/music/bgm-b.mp3"), "-t", String(outroLen), "-af", `afade=t=in:st=0:d=1.2,afade=t=out:st=${(outroLen - 3).toFixed(2)}:d=3`, join(root, "assets/music/bgm-outro.mp3")]);

// ---------- 4. 클립 장면 파일 ----------
const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;");
for (const s of scenes.filter((x) => x.kind === "c")) {
  const k = s.clip, id = s.id, D = s.dur, n = k.watch.length;
  const at = k.watch_at || k.watch.map((_, i) => +(1.2 + (k.dur - 4) * i / n).toFixed(2));
  writeFileSync(join(root, `compositions/${id}.html`), `<template>
  <!-- cuts.json에서 만든 장면(${k.src}). 직접 고치지 말고 cuts.json을 고친 뒤 node scripts/build.mjs -->
  <style>
    #root { position: absolute; inset: 0; background: var(--color-ground); overflow: hidden; }
    #${id}-k { position: absolute; left: 60px; top: 44px; padding: 6px 22px; border-radius: 30px; background: var(--color-accent); color: #fff; font-size: 34px; }
    #${id}-h { position: absolute; left: 60px; top: 98px; font-size: 62px; color: var(--color-ink); }
    #${id}-v { position: absolute; left: 60px; top: 184px; width: ${VW}px; height: ${VH}px; border-radius: 26px; overflow: hidden; background: #0B0E13; box-shadow: 0 24px 60px rgba(16,18,22,0.2); }
    #${id}-v video { position: absolute; left: 0; top: 0; width: 100%; height: 100%; object-fit: cover; }
    #${id}-p { position: absolute; left: 1260px; top: 184px; width: 600px; height: ${VH}px; border-radius: 26px; background: #E6EEFF; border: 4px solid #B9CEFF; padding: 30px 36px; }
    #${id}-p .tg { position: absolute; right: 30px; top: 34px; padding: 6px 20px; border-radius: 30px; background: var(--color-accent); color: #fff; font-size: 30px; }
    #${id}-m { width: 170px; height: 170px; }
    #${id}-m canvas { width: 170px; height: 170px; display: block; }
    #${id}-p h5 { margin-top: 6px; font-size: 40px; font-weight: 400; color: var(--color-accent); }
    #${id}-p .wl { margin-top: 18px; display: flex; gap: 16px; font-size: 37px; line-height: 1.36; color: var(--color-ink); }
    #${id}-p .wl i { flex: none; margin-top: 16px; width: 14px; height: 14px; border-radius: 50%; background: var(--color-accent); }
    #${id}-n { position: absolute; left: 60px; top: 1018px; font-size: 28px; color: var(--ink2); }
    #${id}-v .fl { position: absolute; inset: 0; background: #F2F4F8; opacity: 0; }
    #root .cp { position: absolute; right: 700px; top: 100px; padding: 12px 30px; border-radius: 22px; background: #101216; color: #fff; font-size: 42px; opacity: 0; }
    #${id}-v .sx { position: absolute; right: 24px; top: 24px; padding: 8px 20px; border-radius: 30px; background: rgba(16,18,22,0.8); color: #fff; font-size: 30px; opacity: 0; }
  </style>
  <div id="root" data-composition-id="${id}" data-width="1920" data-height="1080">
${k.quiet ? "" : `    <audio id="${id}-a" class="clip" data-start="0" data-duration="${k.dur}" data-track-index="9" data-volume="1" src="assets/media/${k.id}.m4a"></audio>\n`}    <div id="${id}-k">${esc(k.kicker)}</div>
    <div id="${id}-h">${esc(k.title)}</div>
    <div id="${id}-v"><video id="${id}-vd" class="clip" data-start="0" data-duration="${k.dur}" data-track-index="2" muted playsinline src="assets/media/${k.id}.mp4"></video><div class="fl" id="${id}-fl"></div>${k.parts.map((p, i) => (p.speed > 1 ? `<div class="sx" id="${id}-sx${i}">${p.speed}배속</div>` : "")).join("")}</div>
${k.parts.map((p, i) => (p.label ? `    <div class="cp" id="${id}-cp${i}">${esc(p.label)}</div>\n` : "")).join("")}
    <div id="${id}-p">
      <div class="tg">타비의 설명</div>
      <div id="${id}-m"><canvas id="${id}-cv" width="340" height="340"></canvas></div>
      <h5>이 장면에서는</h5>
${k.watch.map((w) => `      <div class="wl"><i></i><span>${esc(w)}</span></div>`).join("\n")}
    </div>
    <div id="${id}-n">${esc(k.note || "실제 앱 화면을 녹화한 것이에요. 차량 신호는 에뮬레이터의 가상 콘솔로 넣었어요")}</div>
  </div>
  <script>
    (function () {
      const tl = gsap.timeline({ paused: true });
      FX.tabi(tl, document.getElementById("${id}-cv"), ${D}, [[0, "speaking"], [3, "idle"], [${(k.dur - 3.5).toFixed(2)}, "happy"]]);
      tl.fromTo("#${id}-k, #${id}-h", { opacity: 0, y: -18 }, { opacity: 1, y: 0, duration: 0.5, ease: "expo.out", stagger: 0.08 }, 0);
      tl.fromTo("#${id}-v", { opacity: 0, x: -50 }, { opacity: 1, x: 0, duration: ${k.reveal ? 0.3 : 0.6}, ease: "expo.out" }, ${k.reveal || 0.05});
      tl.fromTo("#${id}-p", { opacity: 0, x: 50 }, { opacity: 1, x: 0, duration: 0.6, ease: "expo.out" }, 0.15);
      const at = ${JSON.stringify(at)};
      document.querySelectorAll("#${id}-p .wl").forEach((el, i) => tl.fromTo(el, { opacity: 0, x: 24 }, { opacity: 1, x: 0, duration: 0.45, ease: "expo.out" }, at[i]));
      tl.fromTo("#${id}-n", { opacity: 0 }, { opacity: 1, duration: 0.4 }, 0.6);
${k.parts.map((p, i) => [
        i > 0 && p.start !== k.parts[i - 1].end ? `      tl.to("#${id}-fl", { opacity: 0.9, duration: 0.1, ease: "power1.in" }, ${Math.max(0, p.at - 0.1).toFixed(2)}); tl.to("#${id}-fl", { opacity: 0, duration: 0.22, ease: "power1.out" }, ${p.at.toFixed(2)});` : "",
        p.label ? `      tl.set("#${id}-cp${i}", { opacity: 1 }, ${(p.at + (i > 0 ? 0 : 0.3)).toFixed(2)}); tl.set("#${id}-cp${i}", { opacity: 0 }, ${(p.at + p.len - 0.02).toFixed(2)});` : "",
        p.speed > 1 ? `      tl.to("#${id}-sx${i}", { opacity: 1, duration: 0.15 }, ${(p.at + 0.05).toFixed(2)}); tl.to("#${id}-sx${i}", { opacity: 0, duration: 0.15 }, ${(p.at + p.len - 0.2).toFixed(2)});` : "",
      ].filter(Boolean).join("\n")).filter(Boolean).join("\n")}
      window.__timelines["${id}"] = tl;
    })();
  </script>
</template>
`);
}

// ---------- 4-2. 챕터 이름표 장면 ----------
for (const s of scenes.filter((x) => x.kind === "chap")) {
  const id = s.id, gr = s.group;
  writeFileSync(join(root, `compositions/${id}.html`), `<template>
  <!-- cuts.json의 groups에서 만든 이름표 장면 -->
  <style>
    #root { position: absolute; inset: 0; background: var(--color-accent); overflow: hidden; }
    #${id}-m { position: absolute; left: 190px; top: 330px; width: 420px; height: 420px; }
    #${id}-m canvas { width: 420px; height: 420px; display: block; }
    #${id}-k { position: absolute; left: 700px; top: 330px; font-size: 52px; color: #C9DBFF; }
    #${id}-h { position: absolute; left: 700px; top: 410px; font-size: 150px; line-height: 1.1; color: #fff; }
    #${id}-s { position: absolute; left: 700px; top: 630px; width: 1060px; font-size: 48px; line-height: 1.4; color: #DCE7FF; }
  </style>
  <div id="root" data-composition-id="${id}" data-width="1920" data-height="1080">
    <div id="${id}-m"><canvas id="${id}-cv" width="630" height="630"></canvas></div>
    <div id="${id}-k">${esc(gr.kicker)}</div>
    <div id="${id}-h">${esc(gr.title)}</div>
    <div id="${id}-s">${esc(gr.sub)}</div>
  </div>
  <script>
    (function () {
      const tl = gsap.timeline({ paused: true });
      FX.tabi(tl, document.getElementById("${id}-cv"), ${s.dur}, [[0, "cheer"]]);
      tl.fromTo("#${id}-m", { scale: 0.5, opacity: 0 }, { scale: 1, opacity: 1, duration: 0.6, ease: "back.out(1.6)" }, 0.05);
      tl.fromTo("#${id}-k", { opacity: 0, x: 40 }, { opacity: 1, x: 0, duration: 0.45, ease: "expo.out" }, 0.25);
      tl.fromTo("#${id}-h", { opacity: 0, x: 60, filter: "blur(8px)" }, { opacity: 1, x: 0, filter: "blur(0px)", duration: 0.5, ease: "expo.out" }, 0.4);
      tl.fromTo("#${id}-s", { opacity: 0, y: 20 }, { opacity: 1, y: 0, duration: 0.5, ease: "expo.out" }, 0.9);
      window.__timelines["${id}"] = tl;
    })();
  </script>
</template>
`);
}

// ---------- 5. index.html ----------
const hosts = scenes.map((s) => `      <div id="${s.id}" data-composition-id="${s.id}" data-composition-src="compositions/${s.id}.html" data-start="${s.start}" data-duration="${s.dur}" data-track-index="1" data-track-kind="graphics" data-width="1920" data-height="1080"></div>`).join("\n");
let n = 0;
const sfx = [];
scenes.forEach((s, i) => {
  if (i > 0 && s.kind !== "chap") sfx.push([Math.max(0, s.start - 0.18), s.kind === "c" ? "whoosh-short" : "whoosh", 0.28]);
  for (const [at, name, vol] of s.sfx) sfx.push([s.start + at, name, vol]);
});
const sfxTags = sfx.sort((a, b) => a[0] - b[0]).map(([at, name, vol]) => `      <audio id="sfx-${String(++n).padStart(2, "0")}" class="clip" data-start="${at.toFixed(3)}" data-duration="${SFX[name]}" data-track-index="10" data-volume="${vol}" src="assets/sfx/${name}.mp3"></audio>`).join("\n");
writeFileSync(join(root, "index.html"), `<!doctype html>
<!-- scripts/build.mjs가 만든 파일. 장면 순서·길이는 build.mjs, 클립 구간은 cuts.json에서 고친다 -->
<html lang="ko"
  data-composition-variables='[
    {"id":"color-ground","type":"color","label":"바탕","default":"#F2F4F8"},
    {"id":"color-ink","type":"color","label":"글자","default":"#101216"},
    {"id":"color-accent","type":"color","label":"강조","default":"#2563EB"}
  ]'>
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=1920, height=1080" />
    <script src="https://cdn.jsdelivr.net/npm/gsap@3.15.0/dist/gsap.min.js"></script>
    <script src="assets/tabi.js"></script>
    <script>window.__FIT = ${JSON.stringify(Object.fromEntries(scenes.filter((x) => x.fit).map((x) => [x.id, x.fit])))};</script>
    <style>
      @font-face { font-family: "Jua"; src: url("assets/fonts/Jua.ttf") format("truetype"); font-display: block; }
      :root { --color-ground: #F2F4F8; --color-ink: #101216; --color-accent: #2563EB; --ink2: #5B616B; --line: #DADDE2; }
      * { margin: 0; padding: 0; box-sizing: border-box; }
      html, body { margin: 0; width: 1920px; height: 1080px; overflow: hidden; background: var(--color-ground); }
      body { font-family: "Jua", sans-serif; color: var(--color-ink); word-break: keep-all; -webkit-font-smoothing: antialiased; }
      .w { display: inline-block; }
      #bg { position: absolute; inset: 0; background: var(--color-ground); }
    </style>
  </head>
  <body>
    <div id="root" data-composition-id="main" data-start="0" data-duration="${total}" data-width="1920" data-height="1080">
      <div id="bg"></div>
${hosts}
      <!-- 음악은 그래픽 구간에만. 클립 구간에는 앱의 실제 소리(타비 목소리, 효과음)만 난다 -->
      <audio id="music" class="clip" data-start="0" data-duration="${introLen}" data-track-index="11" data-volume="0.7" src="assets/music/bgm-intro.mp3"></audio>
      <audio id="music-b" class="clip" data-start="${outroStart.toFixed(3)}" data-duration="${outroLen}" data-track-index="11" data-volume="0.7" src="assets/music/bgm-outro.mp3"></audio>
${sfxTags}
    </div>
    <script>
      const tl = gsap.timeline({ paused: true });
      window.__timelines["main"] = tl;
    </script>
  </body>
</html>
`);
console.log(`total ${total}s (${Math.floor(total / 60)}분 ${(total % 60).toFixed(1)}초)`);
for (const s of scenes) console.log(`  ${s.start.toFixed(1).padStart(6)}  ${s.dur.toFixed(1).padStart(5)}  ${s.id}`);
