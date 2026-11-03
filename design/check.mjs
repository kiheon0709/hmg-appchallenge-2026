// 디자인 자동 검증 (GOALS.md G2.2). 사용: node design/check.mjs [--shots]
// - 주행 화면: 조작 요소 ≤ 4, 터치 영역 ≥ 76px, 글자 ≥ 24px, 대비 ≥ 4.5:1
// - 정차 화면: 글자 ≥ 20px, 대비 ≥ 4.5:1, 터치 영역 ≥ 76px
// - 공통: 화면 밖으로 넘치는 요소 없음, 잘린 텍스트 경고
// 대비는 글자를 숨긴 상태의 실제 렌더 픽셀(배경·조명 글로우 포함)과 글자색으로 계산한다.
import { createRequire } from 'node:module';
import { readFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
// 전역 설치된 playwright 사용 (require는 NODE_PATH를 따른다)
const { chromium } = createRequire(import.meta.url)('playwright');
const tokens = JSON.parse(readFileSync(path.join(here, 'tokens.json'), 'utf8'));
const page0 = pathToFileURL(path.join(here, 'prototype/index.html')).href;
const shotsDir = path.join(here, '../docs/screens');
const wantShots = process.argv.includes('--shots');

const sizes = [{ w: 1920, h: 720 }, { w: 1408, h: 792 }];
const states = [
  { name: 'drive-sunset', q: 'screen=drive&t=10', mode: 'driving' },
  { name: 'drive-rain', q: 'screen=drive&t=30', mode: 'driving' },
  { name: 'drive-heavyrain-allow', q: 'screen=drive&t=55&allow=1', mode: 'driving' },
  { name: 'drive-tunnel-allow', q: 'screen=drive&t=70&allow=1', mode: 'driving' },
  { name: 'drive-jam-allow', q: 'screen=drive&t=100&allow=1', mode: 'driving' },
  { name: 'drive-finale-allow', q: 'screen=drive&t=158&allow=1', mode: 'driving' },
  { name: 'card', q: 'screen=card', mode: 'parked' },
  { name: 'onboard', q: 'screen=onboard&sel=시티팝,재즈,인디', mode: 'parked' },
];

function lum([r, g, b]) {
  const f = v => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
}
const contrast = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };

const browser = await chromium.launch();
let failures = 0;
const report = [];

for (const size of sizes) {
  for (const st of states) {
    const ctx = await browser.newContext({ viewport: { width: size.w, height: size.h }, deviceScaleFactor: 1 });
    const page = await ctx.newPage();
    await page.goto(`${page0}?${st.q}&nodemo=1&w=${size.w}&h=${size.h}`);
    await page.evaluate(() => document.fonts.ready);
    await page.addStyleTag({ content: '*{transition:none!important}' });
    await page.waitForTimeout(100);
    const rule = tokens.rules[st.mode];

    const info = await page.evaluate(() => {
      const scr = document.querySelector('.screen.on');
      const carR = document.getElementById('car').getBoundingClientRect();
      const vis = e => { const r = e.getBoundingClientRect(); const cs = getComputedStyle(e); return r.width > 0 && r.height > 0 && cs.visibility !== 'hidden' && cs.display !== 'none'; };
      const controls = [...scr.querySelectorAll('button,[role=button],input,a')].filter(vis)
        .map(e => { const r = e.getBoundingClientRect(); return { label: e.textContent.trim().slice(0, 20), w: r.width, h: r.height }; });
      const texts = [];
      const walker = document.createTreeWalker(scr, NodeFilter.SHOW_TEXT);
      while (walker.nextNode()) {
        const n = walker.currentNode; const e = n.parentElement;
        if (!n.textContent.trim() || !vis(e) || e.closest('[aria-hidden=true]')) continue;
        const range = document.createRange(); range.selectNodeContents(n);
        const r = range.getBoundingClientRect();
        const cs = getComputedStyle(e);
        const m = cs.color.match(/\d+(\.\d+)?/g).map(Number);
        texts.push({ text: n.textContent.trim().slice(0, 24), size: parseFloat(cs.fontSize), color: m.slice(0, 3),
          x: r.x, y: r.y, w: r.width, h: r.height, truncated: e.scrollWidth > e.clientWidth + 1 && cs.textOverflow === 'ellipsis' });
      }
      const overflow = [...scr.querySelectorAll('*')].filter(vis).filter(e => {
        const r = e.getBoundingClientRect();
        return r.right > carR.right + 1 || r.bottom > carR.bottom + 1 || r.left < carR.left - 1 || r.top < carR.top - 1;
      }).map(e => e.className || e.tagName).slice(0, 5);
      return { controls, texts, overflow };
    });

    // 글자를 숨기고 배경만 렌더 → 각 텍스트 영역의 최악 배경 픽셀과 대비 계산
    if (wantShots && size.w === 1920) {
      mkdirSync(shotsDir, { recursive: true });
      const hi = await browser.newContext({ viewport: { width: size.w, height: size.h }, deviceScaleFactor: 2 });
      const p2 = await hi.newPage();
      await p2.goto(`${page0}?${st.q}&nodemo=1`);
      await p2.evaluate(() => document.fonts.ready);
      await p2.addStyleTag({ content: '*{transition:none!important}' });
      await p2.waitForTimeout(100);
      await p2.locator('#car').screenshot({ path: path.join(shotsDir, `${st.name}.png`) });
      await hi.close();
    }
    await page.addStyleTag({ content: 'html body #car *, html body #car *::before, html body #car *::after{color:transparent!important;-webkit-text-fill-color:transparent!important;text-shadow:none!important}' });
    const png = (await page.screenshot()).toString('base64');
    const worst = await page.evaluate(async ({ png, boxes }) => {
      const img = new Image(); img.src = 'data:image/png;base64,' + png; await img.decode();
      const c = document.createElement('canvas'); c.width = img.width; c.height = img.height;
      const g = c.getContext('2d'); g.drawImage(img, 0, 0);
      return boxes.map(b => {
        const x = Math.max(0, Math.floor(b.x)), y = Math.max(0, Math.floor(b.y));
        const w = Math.max(1, Math.min(img.width - x, Math.ceil(b.w))), h = Math.max(1, Math.min(img.height - y, Math.ceil(b.h)));
        const d = g.getImageData(x, y, w, h).data; const px = [];
        for (let i = 0; i < d.length; i += 16) px.push([d[i], d[i + 1], d[i + 2]]);
        return px;
      });
    }, { png, boxes: info.texts });

    const problems = [];
    const controls = info.controls;
    if (st.mode === 'driving' && controls.length > rule.maxControls) problems.push(`조작 요소 ${controls.length}개 > ${rule.maxControls}`);
    for (const c of controls) if (c.w < tokens.touch.min || c.h < tokens.touch.min) problems.push(`터치 영역 작음: "${c.label}" ${c.w | 0}×${c.h | 0}`);
    let minC = 99;
    info.texts.forEach((t, i) => {
      if (t.size < rule.minFontPx) problems.push(`글자 작음: "${t.text}" ${t.size}px < ${rule.minFontPx}`);
      const cs = worst[i].map(p => contrast(t.color, p)).sort((a, b) => a - b);
      const c = cs[Math.floor(cs.length * 0.02)] ?? 21; // 하위 2% (안티에일리어싱·모서리 무시)
      minC = Math.min(minC, c);
      if (c < rule.minContrast) problems.push(`대비 부족: "${t.text}" ${c.toFixed(2)}:1`);
      if (t.truncated) problems.push(`잘림: "${t.text}"`);
    });
    if (info.overflow.length) problems.push(`화면 밖으로 넘침: ${info.overflow.join(', ')}`);

    failures += problems.length;
    report.push({ size: `${size.w}x${size.h}`, state: st.name, controls: controls.length, texts: info.texts.length, minContrast: +minC.toFixed(2), problems });
    await ctx.close();
  }
}
await browser.close();

for (const r of report) {
  console.log(`${r.problems.length ? '✗' : '✓'} ${r.size} ${r.state.padEnd(22)} 조작 ${r.controls} · 텍스트 ${r.texts} · 최저 대비 ${r.minContrast}:1`);
  for (const p of r.problems) console.log(`    - ${p}`);
}
console.log(failures ? `\n실패 ${failures}건` : '\n모든 검증 통과');
process.exit(failures ? 1 : 0);
