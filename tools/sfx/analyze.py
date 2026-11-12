#!/usr/bin/env python3
"""효과음 파일을 수치로 검사하고 tools/sfx/REPORT.md를 쓴다. 사람이 듣는 확인을 대신하지는 않는다.

실행: python3 tools/sfx/analyze.py   (numpy, scipy 필요)

재는 것
- 형식: 길이, 샘플레이트, 채널, 비트
- 크기: 최대 피크(dBFS), 4배 오버샘플링 트루 피크(dBTP), 클리핑 샘플 수(|x| >= 0.999), RMS(dBFS),
  통합 라우드니스 LUFS(ITU-R BS.1770-4, K-가중·게이팅. 0.4초보다 짧은 파일은 게이트 없이 단기값)
- 앱에서 나는 크기: LUFS + 20·log10(SfxPlayer.GAIN). 선배가 말할 때는 여기서 SPEECH_DUCK만큼 더 줄어든다
- 딸깍임: 첫·끝 샘플 크기(dBFS). 첫 샘플이 크면 재생 시작에 '틱'이 난다
- 반복 파일 이음매: 끝→처음 한 칸 차이 / 파일 안 한 칸 차이의 99.9 백분위수. 1보다 크면 이음매가 파일 안의 어느 순간보다 거칠다
"""
import os
import re
import wave

import numpy as np
from scipy import signal

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
SFX = os.path.join(ROOT, "app", "src", "main", "assets", "sfx")
PLAYER = os.path.join(ROOT, "app", "src", "main", "java", "io", "moodlab", "sideseat", "voice", "SfxPlayer.kt")
REPORT = os.path.join(os.path.dirname(__file__), "REPORT.md")

CLIP = 0.999
CLICK_DB = -40.0      # 첫·끝 샘플이 이보다 크면 딸깍임 의심
SEAM_RATIO = 1.0      # 이음매 비율 기준
PEAK_MAX_DB = -1.0    # 피크 상한


def db(x):
    return 20 * np.log10(max(x, 1e-12))


def read(path):
    with wave.open(path) as w:
        sr, ch, bits, n = w.getframerate(), w.getnchannels(), w.getsampwidth() * 8, w.getnframes()
        raw = w.readframes(n)
    x = np.frombuffer(raw, dtype="<i2").astype(np.float64) / 32768.0
    if ch > 1:
        x = x.reshape(-1, ch).mean(axis=1)
    return x, sr, ch, bits


def k_weight(x, sr):
    """BS.1770 K-가중 필터. 계수는 샘플레이트에 맞춰 다시 계산한다(pyloudnorm과 같은 식)."""
    # 1단: 고역 선반(+4dB, 1681Hz)
    G, Q, fc = 3.999843853973347, 0.7071752369554196, 1681.974450955533
    A = 10 ** (G / 40)
    w0 = 2 * np.pi * fc / sr
    alpha = np.sin(w0) / (2 * Q)
    b = [A * ((A + 1) + (A - 1) * np.cos(w0) + 2 * np.sqrt(A) * alpha),
         -2 * A * ((A - 1) + (A + 1) * np.cos(w0)),
         A * ((A + 1) + (A - 1) * np.cos(w0) - 2 * np.sqrt(A) * alpha)]
    a = [(A + 1) - (A - 1) * np.cos(w0) + 2 * np.sqrt(A) * alpha,
         2 * ((A - 1) - (A + 1) * np.cos(w0)),
         (A + 1) - (A - 1) * np.cos(w0) - 2 * np.sqrt(A) * alpha]
    y = signal.lfilter(np.array(b) / a[0], np.array(a) / a[0], x)
    # 2단: 고역 통과(38Hz)
    Q, fc = 0.5003270373238773, 38.13547087602444
    w0 = 2 * np.pi * fc / sr
    alpha = np.sin(w0) / (2 * Q)
    b = [(1 + np.cos(w0)) / 2, -(1 + np.cos(w0)), (1 + np.cos(w0)) / 2]
    a = [1 + alpha, -2 * np.cos(w0), 1 - alpha]
    return signal.lfilter(np.array(b) / a[0], np.array(a) / a[0], y)


def lufs(x, sr):
    y = k_weight(x, sr)
    block, step = int(0.4 * sr), int(0.1 * sr)
    if len(y) < block:
        return -0.691 + 10 * np.log10(np.mean(y ** 2) + 1e-12), "단기"
    z = np.array([np.mean(y[i:i + block] ** 2) for i in range(0, len(y) - block + 1, step)])
    l = -0.691 + 10 * np.log10(z + 1e-12)
    z = z[l > -70]                                   # 절대 게이트
    if len(z) == 0:
        return -70.0, "무음"
    rel = -0.691 + 10 * np.log10(np.mean(z)) - 10    # 상대 게이트
    z2 = z[(-0.691 + 10 * np.log10(z)) > rel]
    return -0.691 + 10 * np.log10(np.mean(z2 if len(z2) else z)), "통합"


def true_peak(x):
    return db(np.max(np.abs(signal.resample_poly(x, 4, 1))))


def player_settings():
    src = open(PLAYER, encoding="utf-8").read()
    gains = {k: float(v) for k, v in re.findall(r'"([a-z_]+)" to ([0-9.]+)f', src)}
    loops = set(re.findall(r'"([a-z_]+)"', re.search(r"val LOOPS = setOf\(([^)]*)\)", src).group(1)))
    duck = float(re.search(r"SPEECH_DUCK = ([0-9.]+)f", src).group(1))
    return gains, loops, duck


def main():
    gains, loops, duck = player_settings()
    rows, issues = [], []
    for f in sorted(os.listdir(SFX)):
        if not f.endswith(".wav"):
            continue
        name = f[:-4]
        x, sr, ch, bits = read(os.path.join(SFX, f))
        peak = db(np.max(np.abs(x)))
        tp = true_peak(x)
        clips = int(np.sum(np.abs(x) >= CLIP))
        rms = db(np.sqrt(np.mean(x ** 2)))
        loud, kind = lufs(x, sr)
        g = gains.get(name, 0.8)
        app = loud + db(g)
        first, last = db(abs(x[0])), db(abs(x[-1]))
        loop = name in loops
        steps = np.abs(np.diff(x))
        seam = abs(x[0] - x[-1]) / (np.percentile(steps, 99.9) + 1e-12) if loop else None
        rows.append(dict(name=name, dur=len(x) / sr, sr=sr, ch=ch, bits=bits, peak=peak, tp=tp, clips=clips, rms=rms,
                         lufs=loud, kind=kind, gain=g, app=app, first=first, last=last, loop=loop, seam=seam))
        if clips:
            issues.append(f"{name}: 클리핑 {clips}개")
        if tp > PEAK_MAX_DB:
            issues.append(f"{name}: 트루 피크 {tp:.1f} dBTP > {PEAK_MAX_DB}")
        # 반복음은 이음매를 위해 파형 중간에서 시작하고, 앱이 0에서 0.6초 페이드인하므로 첫 샘플은 보지 않는다.
        if not loop and first > CLICK_DB:
            issues.append(f"{name}: 첫 샘플 {first:.1f} dBFS(시작 딸깍임 의심)")
        if not loop and last > CLICK_DB:
            issues.append(f"{name}: 끝 샘플 {last:.1f} dBFS(끝 딸깍임 의심)")
        if loop and seam > SEAM_RATIO:
            issues.append(f"{name}: 반복 이음매 비율 {seam:.2f} > {SEAM_RATIO}")

    one = [r["app"] for r in rows if not r["loop"]]
    bg = [r["app"] for r in rows if r["loop"]]
    lines = [
        "# 효과음 수치 검사",
        "",
        "`python3 tools/sfx/analyze.py`가 만든 표예요. 사람이 듣는 확인을 대신하지 않아요.",
        "",
        f"- 파일: `app/src/main/assets/sfx/` {len(rows)}개, 생성 스크립트 `tools/sfx/generate.py`(코드 합성, 외부 음원 없음)",
        f"- 기준: 클리핑 |x| ≥ {CLIP}, 트루 피크 ≤ {PEAK_MAX_DB} dBTP, 첫·끝 샘플 ≤ {CLICK_DB} dBFS(딸깍임), 반복 이음매 비율 ≤ {SEAM_RATIO}",
        f"- 앱 음량 = 파일 LUFS + 20·log10(SfxPlayer.GAIN). 선배가 말하거나 듣는 동안 효과음은 ×{duck} ({db(duck):.1f} dB)로 줄어요(0.18초에 걸쳐).",
        f"- 반복음은 0.6초에 걸쳐 커지고, 멈출 때 0.9초에 걸쳐 작아져요. 반복 파일은 이음매를 위해 파형 중간에서 시작하므로 첫 샘플 기준은 단발음에만 적용해요.",
        f"- 앱 음량 범위: 단발음 {min(one):.1f} ~ {max(one):.1f} LUFS, 반복(배경)음 {min(bg):.1f} ~ {max(bg):.1f} LUFS",
        "",
        "| 이름 | 반복 | 길이(초) | 형식 | 피크 dBFS | 트루 피크 dBTP | 클리핑 | RMS dBFS | LUFS | GAIN | 앱 음량 LUFS | 말할 때 LUFS | 첫/끝 샘플 dBFS | 이음매 비율 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for r in rows:
        seam_s = "" if r["seam"] is None else "%.2f" % r["seam"]
        lines.append(
            f"| {r['name']} | {'○' if r['loop'] else ''} | {r['dur']:.2f} | {r['sr'] // 1000}k/{r['bits']}bit/{'mono' if r['ch'] == 1 else r['ch']} | "
            f"{r['peak']:.1f} | {r['tp']:.1f} | {r['clips']} | {r['rms']:.1f} | {r['lufs']:.1f}{'(단기)' if r['kind'] == '단기' else ''} | "
            f"{r['gain']:.2f} | {r['app']:.1f} | {r['app'] + db(duck):.1f} | {r['first']:.0f} / {r['last']:.0f} | "
            f"{seam_s} |"
        )
    lines += ["", "## 판정", ""]
    lines += [f"- {i}" for i in issues] if issues else ["- 기준을 넘는 항목 없음(클리핑 0, 트루 피크·딸깍임·이음매 모두 기준 안)."]
    lines += ["", "## 사람이 들어서 확인할 것", "",
              "- 빗소리·천둥·타이어가 실제처럼 들리는지, 하품·숨소리가 어색하지 않은지(합성음 한계)",
              "- 차량 스피커에서 선배 목소리와 겹칠 때 효과음이 충분히 작아지는지",
              "- 단발음끼리 크기 차이가 거슬리지 않는지(경고음·타이어는 일부러 크게 둠)"]
    open(REPORT, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print("\n".join(lines[8:]))


if __name__ == "__main__":
    main()
