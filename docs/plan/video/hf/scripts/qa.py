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
print("== 5초 넘게 멈춘 구간(영상 칸 기준) ==")
out = subprocess.run(["ffmpeg", "-v", "info", "-i", f, "-vf", "crop=1160:822:60:184,freezedetect=n=0.002:d=5", "-an", "-f", "null", "-"], capture_output=True, text=True).stderr
st = None; n = 0
for line in out.splitlines():
    m = re.search(r"freeze_start: ([0-9.]+)", line)
    if m: st = float(m.group(1))
    m = re.search(r"freeze_end: ([0-9.]+)", line)
    if m and st is not None:
        en = float(m.group(1)); who = [i for i, s, d in sc if s <= st < s + d]
        if who:
            quiet = db(st, en) < -50
            print(f"{st:7.1f}~{en:7.1f} ({en - st:.1f}초) {who[0]} {'소리도 없음 ← 고칠 곳' if quiet else '말·소리는 있음'}"); n += 1
        st = None
print("없음" if n == 0 else f"{n}곳")
