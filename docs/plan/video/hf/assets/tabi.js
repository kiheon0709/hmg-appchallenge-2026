// 타비 캐릭터. 앱의 app/src/main/java/io/moodlab/sideseat/ui/Tabi.kt를 좌표와 수식 그대로 옮긴 것(새로 그리지 않음).
// draw(mood, p): mood는 idle·speaking·listening·happy·surprised·thinking·cheer, p는 움직임의 위상(0~1 반복). null이면 멈춘 한 장면.
window.TabiMascot = function (canvas) {
  const ctx = canvas.getContext("2d");
  const BODY = "#2F6BF2", EYE = "#0B1533";
  function oval(cx, cy, rx, ry, color) { ctx.beginPath(); ctx.ellipse(cx, cy, Math.max(rx, 0.01), Math.max(ry, 0.01), 0, 0, Math.PI * 2); ctx.fillStyle = color; ctx.fill(); }
  function arc(x, y, w, h, startDeg, sweepDeg, width) {
    ctx.beginPath(); ctx.ellipse(x + w / 2, y + h / 2, w / 2, h / 2, 0, startDeg * Math.PI / 180, (startDeg + sweepDeg) * Math.PI / 180);
    ctx.strokeStyle = EYE; ctx.lineWidth = width; ctx.lineCap = "round"; ctx.stroke();
  }
  function draw(mood, p) {
    const s = canvas.width;
    ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.clearRect(0, 0, canvas.width, canvas.height);
    const moving = p != null, t = (p || 0) * 2 * Math.PI;
    let lift;
    if (mood === "speaking") lift = moving ? Math.abs(Math.sin(t * 4)) * 0.035 : 0.02;
    else if (mood === "happy") lift = moving ? Math.abs(Math.sin(t * 3)) * 0.08 : 0.06;
    else if (mood === "surprised") lift = 0.03;
    else lift = moving ? (Math.sin(t) * 0.5 + 0.5) * 0.03 : 0;
    let tilt = 0;
    if (mood === "listening") tilt = 8; else if (mood === "thinking") tilt = -6; else if (mood === "happy") tilt = moving ? Math.sin(t * 3) * 4 : 3;
    const blink = moving && mood !== "happy" && mood !== "surprised" && p >= 0.90 && p <= 0.935;
    const shadowW = s * (0.46 - lift * 2);
    ctx.globalAlpha = 0.16; oval(s * 0.5, s * 0.9 + s * 0.0275, shadowW / 2, s * 0.0275, "#000"); ctx.globalAlpha = 1;
    ctx.translate(0, -lift * s);
    ctx.save();
    ctx.translate(s * 0.5, s * 0.84); ctx.rotate(tilt * Math.PI / 180); ctx.translate(-s * 0.5, -s * 0.84);
    let armDeg = 22;
    if (mood === "speaking") armDeg = moving ? -25 + 28 * Math.sin(t * 4) : -35;
    else if (mood === "happy") armDeg = -62;
    else if (mood === "cheer") armDeg = moving ? -68 + 8 * Math.sin(t * 3) : -68;
    else if (mood === "surprised") armDeg = -45;
    else if (mood === "listening") armDeg = -20;
    const a = armDeg * Math.PI / 180, fx = s * 0.8, fy = s * 0.57;
    ctx.beginPath(); ctx.moveTo(fx, fy); ctx.lineTo(fx + Math.cos(a) * s * 0.17, fy + Math.sin(a) * s * 0.17);
    ctx.strokeStyle = BODY; ctx.lineWidth = s * 0.085; ctx.lineCap = "round"; ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(s * 0.50, s * 0.15);
    ctx.bezierCurveTo(s * 0.74, s * 0.13, s * 0.88, s * 0.35, s * 0.86, s * 0.57);
    ctx.bezierCurveTo(s * 0.85, s * 0.77, s * 0.70, s * 0.85, s * 0.50, s * 0.85);
    ctx.bezierCurveTo(s * 0.28, s * 0.85, s * 0.14, s * 0.75, s * 0.15, s * 0.55);
    ctx.bezierCurveTo(s * 0.16, s * 0.31, s * 0.30, s * 0.16, s * 0.50, s * 0.15);
    ctx.closePath();
    ctx.moveTo(s * 0.43, s * 0.19);
    ctx.bezierCurveTo(s * 0.40, s * 0.10, s * 0.44, s * 0.04, s * 0.50, s * 0.05);
    ctx.bezierCurveTo(s * 0.49, s * 0.10, s * 0.52, s * 0.14, s * 0.57, s * 0.17);
    ctx.closePath();
    ctx.fillStyle = BODY; ctx.fill();
    const lookX = mood === "thinking" ? s * 0.025 : 0, lookY = mood === "thinking" ? -s * 0.035 : 0;
    const big = mood === "surprised" ? 1.3 : mood === "listening" ? 1.15 : 1;
    for (const ex of [0.40, 0.62]) {
      const cx = s * ex + lookX, cy = s * 0.46 + lookY;
      if (mood === "happy") {
        arc(cx - s * 0.065, cy - s * 0.04, s * 0.13, s * 0.12, 200, 140, s * 0.034);
      } else {
        const rx = s * 0.054 * big, ry = blink ? s * 0.008 : s * 0.098 * big;
        oval(cx, cy, rx, ry, EYE);
        if (!blink) oval(cx + rx * 0.3, cy - ry * 0.45, s * 0.021 * big, s * 0.021 * big, "#fff");
      }
    }
    if (mood === "speaking") { const ry = s * (0.012 + 0.02 * (moving ? Math.abs(Math.sin(t * 5)) : 0.7)); oval(s * 0.51, s * 0.665, s * 0.026, ry, EYE); }
    else if (mood === "surprised") oval(s * 0.51, s * 0.69, s * 0.024, s * 0.024, EYE);
    else if (mood === "happy" || mood === "cheer") arc(s * 0.465, s * 0.615, s * 0.09, s * 0.07, 20, 140, s * 0.022);
    ctx.restore();
    if (mood === "thinking") {
      const k = moving ? (Math.sin(t * 2) * 0.5 + 0.5) : 1;
      ctx.globalAlpha = 0.55; oval(s * 0.83, s * 0.2, s * 0.02, s * 0.02, BODY);
      ctx.globalAlpha = 0.55 + 0.2 * k; oval(s * 0.89, s * 0.13, s * 0.028, s * 0.028, BODY);
      ctx.globalAlpha = 0.55 + 0.4 * k; oval(s * 0.94, s * 0.06, s * 0.038, s * 0.038, BODY);
      ctx.globalAlpha = 1;
    }
  }
  return { draw };
};

// 장면 공용 도우미. 모든 움직임은 타임라인 시간의 함수다.
window.FX = {
  // 글자를 낱말 단위로 나눠 둔다(.sp 안의 텍스트 → .w)
  split(root) {
    root.querySelectorAll(".sp").forEach((el) => {
      const parts = el.innerHTML.split(/(<br\s*\/?>)/);
      el.innerHTML = parts.map((p) => /^<br/.test(p) ? p : p.split(" ").filter(Boolean).map((w) => '<span class="w">' + w + "</span>").join(" ")).join("");
    });
  },
  words(tl, sel, at, st) { tl.fromTo(sel, { opacity: 0, filter: "blur(6px)", y: 14 }, { opacity: 1, filter: "blur(0px)", y: 0, duration: 0.24, ease: "power3.out", stagger: st || 0.07 }, at); },
  pop(tl, sel, at, st) { tl.fromTo(sel, { opacity: 0, scale: 0.86, y: 26 }, { opacity: 1, scale: 1, y: 0, duration: 0.5, ease: "back.out(1.6)", stagger: st || 0.14 }, at); },
  rise(tl, sel, at, st) { tl.fromTo(sel, { opacity: 0, y: 30 }, { opacity: 1, y: 0, duration: 0.5, ease: "expo.out", stagger: st || 0.1 }, at); },
  out(tl, sel, at) { tl.to(sel, { opacity: 0, y: -16, duration: 0.3, ease: "power2.in" }, at); },
  // 장면을 정해진 길이에 맞춰 빠르게 돌린다. window.__FIT[id]가 배율(index.html에서 build.mjs가 넣는다)
  fit(id, tl) {
    const c = (window.__FIT || {})[id];
    if (!c) return tl;
    // 원래 시간축에서 남길 구간만 차례로 k배 빠르게 재생한다(cuts는 화면이 멈춰 있는 구간이라 건너뛰어도 티가 나지 않는다)
    tl.pause(0);
    const o = new gsap.core.Timeline({ paused: true }); // 등록은 장면 파일에서 한다(window.__timelines[id] = FX.fit(id, tl))
    let from = 0, pos = 0;
    for (const [a, b] of c.cuts.concat([[c.orig, c.orig]])) {
      if (a > from) { o.fromTo(tl, { time: from }, { time: a, duration: (a - from) / c.k, ease: "none", immediateRender: false }, pos); pos += (a - from) / c.k; }
      from = b;
    }
    return o;
  },
  // 타비를 장면 길이 D 동안 움직인다. keys는 [[시각, 기분], ...]
  tabi(tl, canvas, D, keys) {
    const m = window.TabiMascot(canvas), st = { t: 0 };
    const mood = (t) => { let k = keys[0][1]; for (const [at, md] of keys) if (t >= at) k = md; return k; };
    const draw = () => m.draw(mood(st.t), (st.t / 3.6) % 1);
    tl.to(st, { t: D, duration: D, ease: "none", onUpdate: draw }, 0);
    draw();
    return m;
  },
};
