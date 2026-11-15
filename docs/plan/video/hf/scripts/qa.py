#!/usr/bin/env python3
"""렌더한 영상 검수. 사용: python3 scripts/qa.py renders/tabi_v4.mp4
1) 클립 장면마다 시작 1초·끝 1초의 소리 크기(0.1초 단위)를 보여 준다. 처음이나 끝 0.1초가 조용하지 않으면 '확인'으로 표시.
2) 클립 장면의 영상 칸이 5초 넘게 멈춰 있고 그동안 소리도 없는 곳을 찾는다."""
import re, subprocess, struct, math, sys
f = sys.argv[1]
h = open("index.html").read()
sc = [(m.group(1), float(m.group(2)), float(m.group(3))) for m in re.finditer(r'<div id="(clip-[a-z0-9]+)"[^>]*data-start="([0-9.]+)" data-duration="([0-9.]+)"', h)]
raw = subprocess.run(["ffmpeg", "-v", "error", "-i", f, "-vn", "-ac", "1", "-ar", "8000", "-f", "s16le", "-"], capture_output=True).stdout
a = struct.unpack("<%dh" % (len(raw) // 2), raw)
def db(t0, t1):
    s = a[int(t0 * 8000):int(t1 * 8000)]
    return 20 * math.log10(max(1, math.sqrt(sum(x * x for x in s) / max(1, len(s)))) / 32768)
print("== 클립 시작·끝 1초 소리(dB, 0.1초 단위) ==")
for id, s, d in sc:
    head = [round(db(s + i / 10, s + (i + 1) / 10)) for i in range(10)]
    tail = [round(db(s + d - 1 + i / 10, s + d - 1 + (i + 1) / 10)) for i in range(10)]
    flag = ("시작 확인 " if head[0] > -50 else "") + ("끝 확인" if tail[-1] > -50 else "")
    print(f"{id:18s} {s:7.1f}~{s + d:7.1f}  앞 {head}  뒤 {tail}  {flag}")
def freezes(vf, d):
    out = subprocess.run(["ffmpeg", "-v", "info", "-i", f, "-vf", vf + f",freezedetect=n=0.003:d={d}", "-an", "-f", "null", "-"], capture_output=True, text=True).stderr
    st = [float(x) for x in re.findall(r"freeze_start: ([0-9.]+)", out)]; en = [float(x) for x in re.findall(r"freeze_end: ([0-9.]+)", out)]
    return list(zip(st, en))
al = [(m.group(1), float(m.group(2)), float(m.group(3))) for m in re.finditer(r'<div id="([a-z0-9-]+)" data-composition-id="[^"]+" data-composition-src="[^"]+" data-start="([0-9.]+)" data-duration="([0-9.]+)"', h)]
print("== 그래픽 장면에서 1.5초 넘게 멈춘 구간(화면 전체) ==")
n = 0
for st, en in freezes("null", 1.5):
    who = [i for i, s, d in al if s <= st < s + d and not i.startswith("clip-")]
    if who: print(f"{st:7.1f}~{en:7.1f} ({en - st:.1f}초) {who[0]}"); n += 1
print("없음" if n == 0 else f"{n}곳")
print("== 클립에서 영상 칸이 3초 넘게 멈추고 그동안 소리도 없는 구간 ==")
n = 0
for st, en in freezes("crop=1160:822:60:184", 3):
    who = [i for i, s, d in sc if s <= st < s + d]
    if not who: continue
    # 멈춘 구간 안에서 가장 길게 조용한 시간
    best = 0; cur = 0; t = st
    while t < en:
        cur = cur + 0.1 if db(t, t + 0.1) < -50 else 0; best = max(best, cur); t += 0.1
    if best >= 3: print(f"{st:7.1f}~{en:7.1f} 멈춤 {en - st:.1f}초, 그중 조용한 시간 {best:.1f}초  {who[0]}"); n += 1
print("없음" if n == 0 else f"{n}곳")
