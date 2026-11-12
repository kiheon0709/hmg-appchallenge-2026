#!/usr/bin/env python3
"""옆자리 훈련 효과음을 코드로 합성한다. 외부 음원을 쓰지 않으므로 저작권 문제가 없다.

출력: app/src/main/assets/sfx/<이름>.wav (44.1kHz, 16-bit, mono)
실행: python3 tools/sfx/generate.py  (numpy, scipy 필요)

소리 목록과 쓰임
- rain        반복  폭우(S01). 넓은 빗소리 + 앞유리에 떨어지는 굵은 빗방울 + 낮은 웅웅거림
- wind        반복  터널·졸음(S04·S09) 배경. 차 안에서 듣는 노면·바람 소리
- engine      반복  의도치 않은 가속(S10). 높은 회전수의 4기통 엔진음
- engine_rise 단발  S10 시작. 회전수가 치솟는 소리
- thunder     단발  S01. 가까운 낙뢰의 '쩍' + 길게 굴러가는 우르릉
- tire        단발  급정거(S03). 타이어 끌리는 소리
- alarm       단발  고장·경고등(S07·S08). 차량 경고음 같은 3회 이중음
- chime       단발  상황 시작 알림. 부드러운 두 음 '띵-동'
- click       단발  금고 다이얼 딸깍(M1). 기계식 짧은 딸깍
- tick        단발  초시계 째깍
- wiper       단발  와이퍼가 한 번 쓸고 가는 소리
- unlock      단발  금고 번호 하나를 맞춤(M1). 묵직한 걸쇠 '철컥' + 짧은 금속 울림
- buzzer      단발  퀴즈 미니게임 먼저 누르기. 밝고 경쾌한 '삐빅'
- success     단발  해결. 밝은 종소리 아르페지오
- fail        단발  실패. 부드럽게 내려가는 두 음(다그치지 않는 느낌)

크기 기준: 반복음은 RMS로, 단발음은 피크로 맞춘다. 장면 속 균형은 앱의 SfxPlayer.GAIN에서 다시 잡는다.
같은 시드로 만들어 매번 같은 파일이 나온다.
"""
import os
import sys
import wave

import numpy as np
from scipy import signal

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets", "sfx")
rng = np.random.default_rng(20261113)


# ---------------------------------------------------------------- 기본 도구

def t_axis(sec):
    return np.arange(int(SR * sec)) / SR


def white(n):
    return rng.standard_normal(n)


def pink(n):
    """Voss-McCartney 근사 대신 1/f 필터(Paul Kellet)로 만든 핑크 노이즈."""
    w = white(n)
    b = [0.049922035, -0.095993537, 0.050612699, -0.004408786]
    a = [1, -2.494956002, 2.017265875, -0.522189400]
    return signal.lfilter(b, a, w)


def brown(n):
    x = np.cumsum(white(n))
    x = signal.lfilter([1, -1], [1, -0.995], x)  # 직류 성분 제거
    return x


def bp(x, lo, hi, order=4):
    sos = signal.butter(order, [lo, hi], btype="bandpass", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def lp(x, f, order=4):
    sos = signal.butter(order, f, btype="lowpass", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def hp(x, f, order=4):
    sos = signal.butter(order, f, btype="highpass", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def peak_eq(x, f0, q, gain_db):
    """RBJ 피킹 EQ 한 개."""
    a_ = 10 ** (gain_db / 40)
    w0 = 2 * np.pi * f0 / SR
    alpha = np.sin(w0) / (2 * q)
    b = [1 + alpha * a_, -2 * np.cos(w0), 1 - alpha * a_]
    a = [1 + alpha / a_, -2 * np.cos(w0), 1 - alpha / a_]
    return signal.lfilter(b, a, x)


def env_adsr(n, a, d, s, r, sustain_level=0.7):
    """초 단위 ADSR. 합이 길이보다 짧으면 sustain으로 채운다."""
    na, nd, nr = int(a * SR), int(d * SR), int(r * SR)
    ns = max(n - na - nd - nr, 0)
    e = np.concatenate([
        np.linspace(0, 1, na, endpoint=False) ** 1.5 if na else np.zeros(0),
        np.linspace(1, sustain_level, nd, endpoint=False) if nd else np.zeros(0),
        np.full(ns, sustain_level),
        np.linspace(sustain_level, 0, nr) ** 2 if nr else np.zeros(0),
    ])
    return np.pad(e, (0, max(n - len(e), 0)))[:n]


def exp_decay(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))


def reverb(x, sec=1.2, wet=0.18, damp=6000, predelay=0.012):
    """짧은 실내 잔향: 감쇠하는 노이즈 임펄스 응답과 컨볼루션. 차 안 + 약간의 공간감."""
    n = int(SR * sec)
    ir = white(n) * exp_decay(n, sec / 6.5)
    ir = lp(ir, damp, 2)
    ir = np.concatenate([np.zeros(int(predelay * SR)), ir])
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    y = signal.fftconvolve(x, ir)[: len(x) + int(sec * SR * 0.6)]
    dry = np.pad(x, (0, len(y) - len(x)))
    return dry * (1 - wet) + y * wet * 2.2


def fade(x, fin=0.005, fout=0.02):
    x = x.copy()
    ni, no = int(fin * SR), int(fout * SR)
    if ni:
        x[:ni] *= np.linspace(0, 1, ni) ** 2
    if no:
        x[-no:] *= np.linspace(1, 0, no) ** 2
    return x


def make_loop(x, xfade_sec):
    """끝부분을 앞부분에 등전력 크로스페이드해 이음매 없는 반복음을 만든다. 결과 길이 = len(x) - xfade."""
    n = int(xfade_sec * SR)
    body = x[:-n].copy()
    tail = x[-n:]
    th = np.linspace(0, np.pi / 2, n)
    body[:n] = body[:n] * np.sin(th) + tail * np.cos(th)
    return body


def norm_rms(x, dbfs, peak_limit_db=-1.0):
    rms = np.sqrt(np.mean(x ** 2)) + 1e-12
    y = x * (10 ** (dbfs / 20) / rms)
    return soft_limit(y, peak_limit_db)


def norm_peak(x, dbfs):
    return x * (10 ** (dbfs / 20) / (np.max(np.abs(x)) + 1e-12))


def soft_limit(x, ceiling_db=-1.0):
    c = 10 ** (ceiling_db / 20)
    return np.tanh(x / c) * c


def tone(freq, sec, harmonics=((1, 1.0),), phase_jitter=True):
    t = t_axis(sec)
    y = np.zeros_like(t)
    for k, amp in harmonics:
        ph = rng.uniform(0, 2 * np.pi) if phase_jitter else 0
        y += amp * np.sin(2 * np.pi * freq * k * t + ph)
    return y


def write(name, x):
    os.makedirs(OUT, exist_ok=True)
    x = np.clip(x, -1, 1)
    data = (x * 32767).astype("<i2").tobytes()
    path = os.path.join(OUT, f"{name}.wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data)
    peak = 20 * np.log10(np.max(np.abs(x)) + 1e-12)
    rms = 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12)
    print(f"{name:12s} {len(x) / SR:5.2f}s  peak {peak:6.1f} dBFS  rms {rms:6.1f} dBFS  {len(data) / 1024:6.0f} KB")


# ---------------------------------------------------------------- 소리들

def rain():
    sec, xf = 10.0, 1.5
    n = int(SR * sec)
    t = t_axis(sec)
    # 1) 넓게 쏟아지는 빗소리: 핑크 노이즈를 고역 위주로, 굵기가 천천히 출렁이게
    wash = bp(pink(n), 500, 11000, 2)
    wash = peak_eq(wash, 3200, 0.8, 4)
    swell = 1 + 0.18 * np.sin(2 * np.pi * 0.11 * t + 1.3) + 0.1 * np.sin(2 * np.pi * 0.37 * t)
    wash *= swell
    # 2) 앞유리·지붕에 떨어지는 빗방울: 짧은 감쇠 노이즈 버스트를 무작위 시각·높이로
    drops = np.zeros(n)
    count = int(sec * 260)
    for _ in range(count):
        start = rng.integers(0, n - 2000)
        ln = int(rng.uniform(0.004, 0.022) * SR)
        center = rng.uniform(1500, 7500)
        burst = bp(white(ln + 64), center * 0.6, min(center * 1.6, 19000), 2)[64:]
        burst *= exp_decay(ln, rng.uniform(0.0015, 0.006))
        drops[start:start + ln] += burst * rng.lognormal(-1.3, 0.6)
    # 굵은 방울 몇 개(가까이 '톡'): 낮은 공진을 섞는다
    for _ in range(int(sec * 14)):
        start = rng.integers(0, n - 4000)
        ln = int(0.05 * SR)
        f = rng.uniform(700, 1400)
        tk = np.sin(2 * np.pi * f * t_axis(0.05) * (1 - 0.3 * t_axis(0.05) / 0.05)) * exp_decay(ln, 0.008)
        drops[start:start + ln] += tk * rng.uniform(0.15, 0.4)
    # 3) 차체가 받는 낮은 웅웅거림
    rumble = lp(brown(n), 180, 2) * 0.6
    x = wash * 1.0 + drops * 2.4 + rumble * 0.012 / (np.std(rumble) + 1e-9) * 3
    x = hp(x, 60, 2)
    x = make_loop(x, xf)
    return norm_rms(x, -22, -3)


def wind():
    sec, xf = 9.0, 1.5
    n = int(SR * sec)
    t = t_axis(sec)
    road = lp(brown(n), 420, 2)
    road /= np.std(road) + 1e-9
    hiss = bp(pink(n), 600, 4000, 2)
    hiss /= np.std(hiss) + 1e-9
    # 바람이 일고 잦아드는 느낌: 느린 변조 + 대역 이동
    gust = 0.75 + 0.25 * np.sin(2 * np.pi * 0.09 * t) * np.sin(2 * np.pi * 0.23 * t + 0.7)
    whistle = bp(white(n), 900, 1300, 2)
    whistle /= np.std(whistle) + 1e-9
    x = road * 0.9 + hiss * 0.35 * gust + whistle * 0.05 * gust ** 3
    x = make_loop(x, xf)
    return norm_rms(x, -24, -3)


def engine_wave(rpm_curve, n):
    """4기통 4행정: 점화 주파수 = rpm/30. 점화 펄스열에 배기 공진을 입힌다."""
    f0 = rpm_curve / 30.0
    phase = 2 * np.pi * np.cumsum(f0) / SR
    # 점화 펄스: 고조파가 풍부한 톱니에 가까운 파형
    y = np.zeros(n)
    for k, amp in [(0.5, 0.35), (1, 1.0), (1.5, 0.25), (2, 0.6), (3, 0.35), (4, 0.22), (5, 0.12), (6, 0.08), (8, 0.05)]:
        y += amp * np.sin(k * phase + rng.uniform(0, 6.28))
    # 기통마다 조금씩 다른 세기(살아 있는 엔진 느낌)
    y *= 1 + 0.12 * np.sin(phase / 2 + 0.4)
    mech = bp(white(n), 1500, 6000, 2) * (0.08 + 0.05 * np.sin(phase))
    intake = bp(pink(n), 200, 900, 2) * 0.25
    y = y + mech * 4 + intake * 3
    y = peak_eq(y, 120, 0.9, 5)
    y = peak_eq(y, 480, 1.2, 3)
    y = lp(y, 5200, 2)
    return y


def engine():
    sec, xf = 4.0, 1.0
    n = int(SR * sec)
    t = t_axis(sec)
    rpm = 5200 + 90 * np.sin(2 * np.pi * 0.7 * t) + 40 * np.sin(2 * np.pi * 2.3 * t)
    # 반복 이음매가 맞도록 끝 rpm을 처음과 같게(크로스페이드 구간에서 위상 차이는 묻힌다)
    x = engine_wave(rpm, n)
    x = make_loop(x, xf)
    return norm_rms(x, -21, -3)


def engine_rise():
    sec = 2.8
    n = int(SR * sec)
    t = t_axis(sec)
    shape = 1 - np.exp(-t / 0.75)
    rpm = 1400 + 4400 * shape
    x = engine_wave(rpm, n)
    x *= env_adsr(n, 0.05, 0.1, 0, 0.35, 1.0) * (0.55 + 0.45 * shape)
    x = reverb(x, 0.6, 0.08)
    return norm_peak(fade(x, 0.01, 0.2), -2)


def thunder():
    sec = 6.5
    n = int(SR * sec)
    t = t_axis(sec)
    # 1) 가까운 낙뢰의 '쩍': 아주 빠른 어택의 광대역 크랙 여러 개
    crack = np.zeros(n)
    for i in range(5):
        st = int((0.0 + i * rng.uniform(0.012, 0.04)) * SR)
        ln = int(0.25 * SR)
        c = hp(white(ln), 1200, 2) * exp_decay(ln, rng.uniform(0.02, 0.06))
        crack[st:st + ln] += c * (1.0 - i * 0.15)
    # 2) 우르릉: 저역 브라운 노이즈에 불규칙한 굴림(여러 개의 혹)을 준다
    roll = lp(brown(n), 260, 4)
    roll /= np.std(roll) + 1e-9
    bumps = np.zeros(n)
    for _ in range(9):
        c = rng.uniform(0.15, 3.8)
        w = rng.uniform(0.25, 0.9)
        bumps += rng.uniform(0.4, 1.0) * np.exp(-0.5 * ((t - c) / w) ** 2)
    body = roll * (0.25 + bumps) * np.exp(-t / 2.4)
    body = peak_eq(body, 70, 0.8, 4)
    mid = bp(brown(n), 200, 900, 2)
    mid /= np.std(mid) + 1e-9
    mid *= 0.2 * np.exp(-t / 1.0)
    x = crack * 0.9 + body * 0.7 + mid
    x = reverb(x, 2.2, 0.25, 3000, 0.03)[:n]
    x = fade(x, 0.001, 0.8)
    return norm_peak(x, -1.5)


def tire():
    sec = 1.7
    n = int(SR * sec)
    t = t_axis(sec)
    # 타이어 끌림: 1kHz 부근의 흔들리는 끽 소리 + 고무 마찰 노이즈
    f = 1150 - 220 * (t / sec) + 35 * np.sin(2 * np.pi * 13 * t) + 18 * lp(white(n), 30, 1) * 40
    ph = 2 * np.pi * np.cumsum(f) / SR
    squeal = np.sin(ph) + 0.45 * np.sin(2 * ph + 0.3) + 0.2 * np.sin(3 * ph + 1.1)
    am = 0.7 + 0.3 * np.sin(2 * np.pi * 37 * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 5 * t))
    squeal *= am
    scrub = bp(white(n), 1800, 7000, 2)
    scrub /= np.std(scrub) + 1e-9
    e = env_adsr(n, 0.04, 0.15, 0.8, 0.45, 0.85)
    x = (squeal * 0.6 + scrub * 0.18) * e
    x = peak_eq(x, 2300, 1.5, 3)
    x = reverb(x, 0.9, 0.12)[:n + int(0.3 * SR)]
    return norm_peak(fade(x, 0.003, 0.3), -2)


def soft_square(freq, sec, partials=7):
    t = t_axis(sec)
    y = np.zeros_like(t)
    for k in range(1, partials * 2, 2):
        y += np.sin(2 * np.pi * freq * k * t) / k * (1 / (1 + (k * freq / 3500) ** 2))
    return y


def alarm():
    # 차량 경고음 같은 이중음 3회: 높음-낮음, 각각 둥근 사각파 + 짧은 잔향
    seg = []
    for i in range(3):
        for f, ln in [(1046.5, 0.14), (784.0, 0.18)]:
            n = int(SR * ln)
            b = soft_square(f, ln) * env_adsr(n, 0.006, 0.05, 0.75, 0.04, 0.75)
            seg.append(b)
            seg.append(np.zeros(int(0.02 * SR)))
        seg.append(np.zeros(int(0.16 * SR)))
    x = np.concatenate(seg)
    x = reverb(x, 0.5, 0.12, 5000)
    return norm_peak(fade(x, 0.002, 0.1), -3)


def bell(freq, sec, decay=0.9, bright=1.0):
    """종소리: 비조화 배음(1, 2.0, 2.76, 5.4)과 배음마다 다른 감쇠."""
    n = int(SR * sec)
    t = t_axis(sec)
    y = np.zeros(n)
    for ratio, amp, tau in [(1, 1.0, decay), (2.0, 0.45 * bright, decay * 0.6), (2.76, 0.3 * bright, decay * 0.4),
                            (5.4, 0.12 * bright, decay * 0.18), (0.5, 0.15, decay * 1.2)]:
        y += amp * np.sin(2 * np.pi * freq * ratio * t) * np.exp(-t / tau)
    att = np.minimum(1, t / 0.003)
    return fade(y * att, 0.0, 0.04)  # 잘린 꼬리에서 '틱' 소리가 나지 않게


def place(x, n, at):
    out = np.zeros(n)
    s = int(at * SR)
    m = min(len(x), n - s)
    out[s:s + m] = x[:m]
    return out


def chime():
    n = int(SR * 1.6)
    x = place(bell(880, 1.4, 0.55, 0.7), n, 0.0) * 0.8 + place(bell(659.25, 1.4, 0.7, 0.6), n, 0.18)
    x = reverb(x, 1.0, 0.2)[:n]
    return norm_peak(fade(x, 0.001, 0.3), -3)


def click():
    # 기계식 다이얼 딸깍: 아주 짧은 고역 틱 + 몸체 공진(2.4kHz) + 낮은 '톡'
    sec = 0.09
    n = int(SR * sec)
    t = t_axis(sec)
    tick = hp(white(n), 3000, 2) * exp_decay(n, 0.0012)
    body = np.sin(2 * np.pi * 2400 * t) * exp_decay(n, 0.006) * 0.5
    thump = np.sin(2 * np.pi * 180 * t) * exp_decay(n, 0.012) * 0.35
    x = tick + body + thump
    # 두 번째 금속 접점(겹딸깍)
    x += place(hp(white(n), 4000, 2) * exp_decay(n, 0.0008) * 0.5, n, 0.011)
    return norm_peak(fade(x, 0.0005, 0.02), -3)


def tick():
    sec = 0.06
    n = int(SR * sec)
    t = t_axis(sec)
    x = np.sin(2 * np.pi * 1800 * t) * exp_decay(n, 0.004) + bp(white(n), 2500, 8000, 2) * exp_decay(n, 0.001) * 0.6
    return norm_peak(fade(x, 0.0005, 0.01), -6)


def wiper():
    sec = 0.75
    n = int(SR * sec)
    t = t_axis(sec)
    # 고무 날이 젖은 유리를 쓸고 가는 소리: 대역이 움직이는 마찰 노이즈 + 끝에서 '툭'
    sweep_center = 1200 + 1600 * np.sin(np.pi * t / sec)
    noise = white(n)
    x = np.zeros(n)
    hop = 512
    win = np.hanning(hop * 2)
    for i in range(0, n - hop * 2, hop):
        c = sweep_center[i]
        seg = bp(noise[i:i + hop * 2], c * 0.5, c * 1.4, 2) * win
        x[i:i + hop * 2] += seg
    x *= np.sin(np.pi * t / sec) ** 0.7
    squeak = np.sin(2 * np.pi * (2600 + 300 * t) * t) * np.exp(-((t - 0.35) / 0.05) ** 2) * 0.12
    thud = place(np.sin(2 * np.pi * 140 * t_axis(0.08)) * exp_decay(int(0.08 * SR), 0.015), n, sec - 0.09) * 0.6
    x = x + squeak + thud
    return norm_peak(fade(x, 0.01, 0.03), -4)


def unlock():
    sec = 0.6
    n = int(SR * sec)
    t = t_axis(sec)
    # 걸쇠가 빠지는 '철': 고역 금속 접점 두 번
    latch = hp(white(n), 2500, 2) * exp_decay(n, 0.002)
    latch += place(hp(white(n), 3500, 2) * exp_decay(n, 0.0015) * 0.7, n, 0.035)
    # 무거운 몸체 '컥': 낮은 공진 + 짧은 저역 노이즈
    body = np.sin(2 * np.pi * 95 * t) * exp_decay(n, 0.05) + lp(white(n), 400, 2) * exp_decay(n, 0.02) * 2.0
    body = place(body, n, 0.03)
    # 금속 울림(금고 문 철판): 비조화 배음 몇 개
    ring = np.zeros(n)
    for f, a, tau in [(1720, 0.35, 0.18), (2650, 0.2, 0.12), (4130, 0.1, 0.07)]:
        ring += a * np.sin(2 * np.pi * f * t) * exp_decay(n, tau)
    ring = place(ring, n, 0.032)
    x = latch * 0.8 + body * 0.9 + ring * 0.5
    x = reverb(x, 0.5, 0.1)[:n]
    return norm_peak(fade(x, 0.0005, 0.08), -3)


def buzzer():
    # 퀴즈쇼 버튼: 빠르게 올라가는 두 음 + 끝음을 살짝 길게
    n = int(SR * 0.5)
    out = np.zeros(n)
    for f, at, ln in [(1318.5, 0.0, 0.07), (1975.5, 0.075, 0.22)]:
        m = int(SR * ln)
        y = soft_square(f, ln, 4) * 0.6 + np.sin(2 * np.pi * f * t_axis(ln)) * 0.6
        y *= env_adsr(m, 0.004, 0.03, 0.6, ln * 0.5, 0.6)
        out += place(y, n, at)
    out = reverb(out, 0.5, 0.12)[:n]
    return norm_peak(fade(out, 0.001, 0.05), -4)


def success():
    # 밝은 종소리 아르페지오 C6-E6-G6-C7, 마지막 음은 길게
    notes = [(1046.5, 0.0), (1318.5, 0.09), (1568.0, 0.18), (2093.0, 0.29)]
    n = int(SR * 1.9)
    x = np.zeros(n)
    for i, (f, at) in enumerate(notes):
        last = i == len(notes) - 1
        x += place(bell(f, 1.6, 0.9 if last else 0.35, 0.8), n, at) * (1.0 if last else 0.7)
    # 아래에 따뜻한 화음(C5+G5) 패드
    pad = (tone(523.25, 1.6) + 0.6 * tone(783.99, 1.6)) * env_adsr(int(1.6 * SR), 0.08, 0.2, 0.0, 1.2, 0.5) * 0.18
    x += place(pad, n, 0.25)
    x = reverb(x, 1.4, 0.22, 7000)[:n]
    return norm_peak(fade(x, 0.001, 0.4), -2)


def fail():
    # 부드럽게 내려가는 두 음(G4 → D4), 둥근 음색. 다그치지 않는 "괜찮아요" 느낌
    n = int(SR * 1.3)
    out = np.zeros(n)
    for f, at, ln in [(392.0, 0.0, 0.32), (293.66, 0.28, 0.75)]:
        m = int(SR * ln)
        tt = t_axis(ln)
        vib = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * tt) * np.minimum(1, tt / 0.2)
        ph = 2 * np.pi * np.cumsum(f * vib) / SR
        y = np.sin(ph) + 0.3 * np.sin(2 * ph) + 0.12 * np.sin(3 * ph)
        y *= env_adsr(m, 0.02, 0.1, 0.0, ln * 0.6, 0.7)
        out += place(y, n, at)
    out = lp(out, 2500, 2)
    out = reverb(out, 1.0, 0.2)[:n]
    return norm_peak(fade(out, 0.002, 0.3), -5)


SOUNDS = {
    "rain": rain, "wind": wind, "engine": engine, "engine_rise": engine_rise, "thunder": thunder,
    "tire": tire, "alarm": alarm, "chime": chime, "click": click, "tick": tick, "wiper": wiper,
    "unlock": unlock, "buzzer": buzzer, "success": success, "fail": fail,
}

if __name__ == "__main__":
    only = set(sys.argv[1:])
    for name, fn in SOUNDS.items():
        if not only or name in only:
            write(name, fn())
