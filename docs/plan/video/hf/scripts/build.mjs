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
const clips = [];
for (const c of cuts.clips) {
  const src = resolve(root, c.src.startsWith("..") ? c.src : join(cuts.source_dir, c.src));
  if (c.enabled === false || !existsSync(src)) { console.log(`skip ${c.id} (${c.enabled === false ? "disabled" : "no file"})`); continue; }
  const dur = +(c.end - c.start).toFixed(3);
  const crop = c.crop === undefined ? cuts.crop : c.crop;
  const gain = c.gain_db === undefined ? cuts.gain_db : c.gain_db;
  const key = createHash("sha1").update(JSON.stringify([c.src, c.start, c.end, crop, gain, statSync(src).mtimeMs, statSync(src).size, VW, VH])).digest("hex").slice(0, 10);
  const stamp = join(media, `${c.id}.key`);
  const mp4 = join(media, `${c.id}.mp4`), m4a = join(media, `${c.id}.m4a`);
  if (!existsSync(mp4) || !existsSync(m4a) || !existsSync(stamp) || readFileSync(stamp, "utf8") !== key) {
    console.log(`cut ${c.id}: ${c.src} ${c.start}~${c.end} (${dur}s)`);
    const vf = (crop ? `crop=${crop},` : "") + `scale=${VW}:${VH}:force_original_aspect_ratio=increase,crop=${VW}:${VH},fps=30`;
    ff(["-ss", String(c.start), "-t", String(dur), "-i", src, "-an", "-vf", vf, "-c:v", "libx264", "-crf", "18", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-g", "30", "-movflags", "+faststart", mp4]);
    ff(["-ss", String(c.start), "-t", String(dur), "-i", src, "-vn", "-af", `volume=${gain}dB,alimiter=limit=0.9`, "-c:a", "aac", "-b:a", "160k", m4a]);
    writeFileSync(stamp, key);
  }
  clips.push({ ...c, dur });
}
const clip = Object.fromEntries(clips.map((c) => [c.id, c]));

// ---------- 2. 장면 순서 ----------
const g = (id, dur, sfx = []) => ({ id, dur, sfx, kind: "g" });
const c = (id) => clip[id] ? { id: `clip-${id}`, dur: clip[id].dur + 0.6, kind: "c", clip: clip[id], sfx: [] } : null;
const scenes = [
  g("title", 7.5, [[0.15, "pop", 0.4], [1.3, "whoosh", 0.3]]),
  g("problem", 29, [[0.5, "pop", 0.35], [1.5, "click-soft", 0.5], [6.4, "whoosh-cinematic", 0.3], [17.5, "pop", 0.4], [18.0, "pop", 0.4], [18.5, "pop", 0.4], [24.4, "pop", 0.45]]),
  g("who", 12, [[1.4, "pop", 0.4], [2.7, "pop", 0.4], [4.0, "pop", 0.4]]),
  g("moments", 13, [[1.4, "pop", 0.4], [4.6, "pop", 0.4], [7.8, "pop", 0.4]]),
  g("train-intro", 5.5, [[0.1, "impact-bass-1", 0.4]]),
  c("rain"), c("fog"), c("tunnel"), c("toll"),
  g("route", 12, [[1.6, "pop", 0.4], [3.0, "click-soft", 0.5], [3.8, "click-soft", 0.5], [4.6, "click-soft", 0.5], [6.2, "whoosh-short", 0.3]]),
  c("coach"), c("review"), c("talk"),
  g("sdk", 19, [[1.5, "pop", 0.4], [3.9, "pop", 0.4], [6.3, "pop", 0.4], [8.7, "pop", 0.4], [11.1, "pop", 0.4]]),
  g("numbers", 14.5, [[1.4, "click-soft", 0.5], [3.6, "click-soft", 0.5], [5.8, "click-soft", 0.5], [8.0, "click-soft", 0.5]]),
  g("roadmap", 12, [[1.4, "pop", 0.4], [4.4, "pop", 0.4], [7.4, "pop", 0.4]]),
  g("ending", 10, [[0.2, "impact-bass-1", 0.4]]),
].filter(Boolean);
let t = 0;
for (const s of scenes) { s.start = +t.toFixed(3); t += s.dur; }
const total = +t.toFixed(3);
const SFX = { pop: 0.72, whoosh: 0.575, "whoosh-short": 0.575, "whoosh-cinematic": 2.4, "click-soft": 0.366, "impact-bass-1": 2.116 };

// ---------- 3. 음악: 그래픽 구간에만(클립에는 앱의 실제 소리만) ----------
const firstClip = scenes.find((s) => s.kind === "c"), sdk = scenes.find((s) => s.id === "sdk");
const introLen = +((firstClip ? firstClip.start : total) + 0.4).toFixed(2), outroStart = sdk.start - 0.4, outroLen = +(total - outroStart).toFixed(2);
ff(["-i", join(root, "assets/music/bgm-a.mp3"), "-t", String(introLen), "-af", `afade=t=out:st=${(introLen - 2.2).toFixed(2)}:d=2.2`, join(root, "assets/music/bgm-intro.mp3")]);
ff(["-i", join(root, "assets/music/bgm-b.mp3"), "-t", String(outroLen), "-af", `afade=t=in:st=0:d=1.2,afade=t=out:st=${(outroLen - 3).toFixed(2)}:d=3`, join(root, "assets/music/bgm-outro.mp3")]);

// ---------- 4. 클립 장면 파일 ----------
const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;");
for (const s of scenes.filter((x) => x.kind === "c")) {
  const k = s.clip, id = s.id, D = s.dur, n = k.watch.length;
  const at = k.watch_at || k.watch.map((_, i) => +(1.2 + (k.dur - 4) * i / n).toFixed(2));
  writeFileSync(join(root, `compositions/${id}.html`), `<template>
  <!-- cuts.json에서 만든 장면(${k.src} ${k.start}~${k.end}초). 직접 고치지 말고 cuts.json을 고친 뒤 node scripts/build.mjs -->
  <style>
    #root { position: absolute; inset: 0; background: var(--color-ground); overflow: hidden; }
    #${id}-k { position: absolute; left: 60px; top: 44px; padding: 6px 22px; border-radius: 30px; background: var(--color-accent); color: #fff; font-size: 34px; }
    #${id}-h { position: absolute; left: 60px; top: 98px; font-size: 62px; color: var(--color-ink); }
    #${id}-v { position: absolute; left: 60px; top: 184px; width: ${VW}px; height: ${VH}px; border-radius: 26px; overflow: hidden; background: #0B0E13; box-shadow: 0 24px 60px rgba(16,18,22,0.2); }
    #${id}-v video { position: absolute; left: 0; top: 0; width: 100%; height: 100%; object-fit: cover; }
    #${id}-p { position: absolute; left: 1260px; top: 184px; width: 600px; height: ${VH}px; border-radius: 26px; background: #fff; box-shadow: 0 24px 60px rgba(16,18,22,0.1); padding: 30px 36px; }
    #${id}-m { width: 170px; height: 170px; }
    #${id}-m canvas { width: 170px; height: 170px; display: block; }
    #${id}-p h5 { margin-top: 6px; font-size: 40px; font-weight: 400; color: var(--color-accent); }
    #${id}-p .wl { margin-top: 18px; display: flex; gap: 16px; font-size: 37px; line-height: 1.36; color: var(--color-ink); }
    #${id}-p .wl i { flex: none; margin-top: 16px; width: 14px; height: 14px; border-radius: 50%; background: var(--color-accent); }
    #${id}-n { position: absolute; left: 60px; top: 1018px; font-size: 28px; color: var(--ink2); }
  </style>
  <div id="root" data-composition-id="${id}" data-width="1920" data-height="1080">
    <audio id="${id}-a" class="clip" data-start="0" data-duration="${k.dur}" data-track-index="9" data-volume="1" src="assets/media/${k.id}.m4a"></audio>
    <div id="${id}-k">${esc(k.kicker)}</div>
    <div id="${id}-h">${esc(k.title)}</div>
    <div id="${id}-v"><video id="${id}-vd" class="clip" data-start="0" data-duration="${k.dur}" data-track-index="2" muted playsinline src="assets/media/${k.id}.mp4"></video></div>
    <div id="${id}-p">
      <div id="${id}-m"><canvas id="${id}-cv" width="340" height="340"></canvas></div>
      <h5>여기를 보세요</h5>
${k.watch.map((w) => `      <div class="wl"><i></i><span>${esc(w)}</span></div>`).join("\n")}
    </div>
    <div id="${id}-n">실제 앱 화면을 끊지 않고 녹화한 것이에요. 스위치 신호는 에뮬레이터의 가상 콘솔로 넣었어요</div>
  </div>
  <script>
    (function () {
      const tl = gsap.timeline({ paused: true });
      FX.tabi(tl, document.getElementById("${id}-cv"), ${D}, [[0, "speaking"], [3, "idle"], [${(k.dur - 3.5).toFixed(2)}, "happy"]]);
      tl.fromTo("#${id}-k, #${id}-h", { opacity: 0, y: -18 }, { opacity: 1, y: 0, duration: 0.5, ease: "expo.out", stagger: 0.08 }, 0);
      tl.fromTo("#${id}-v", { opacity: 0, x: -50 }, { opacity: 1, x: 0, duration: 0.6, ease: "expo.out" }, 0.05);
      tl.fromTo("#${id}-p", { opacity: 0, x: 50 }, { opacity: 1, x: 0, duration: 0.6, ease: "expo.out" }, 0.15);
      const at = ${JSON.stringify(at)};
      document.querySelectorAll("#${id}-p .wl").forEach((el, i) => tl.fromTo(el, { opacity: 0, x: 24 }, { opacity: 1, x: 0, duration: 0.45, ease: "expo.out" }, at[i]));
      tl.fromTo("#${id}-n", { opacity: 0 }, { opacity: 1, duration: 0.4 }, 0.6);
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
  if (i > 0) sfx.push([Math.max(0, s.start - 0.18), s.kind === "c" ? "whoosh-short" : "whoosh", 0.28]);
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
