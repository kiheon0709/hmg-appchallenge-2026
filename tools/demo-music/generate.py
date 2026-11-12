#!/usr/bin/env python3
"""시연용 음원 합성기. catalog/tracks.json의 곡마다 무드·BPM·장르에 맞는 짧은 반복 음원을 만든다.

자체 합성물이라 외부 저작권이 없다. 실제 음원으로 바꿀 때는 docs/music-tagging.md 참고.
사용: python3 tools/demo-music/generate.py <임시 폴더>  (numpy, ffmpeg 필요)
"""
import hashlib
import json
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np

SR = 22050
ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "core/src/main/resources/catalog/tracks.json"
OUT = ROOT / "app/src/main/assets/music"

MAJOR = [[0, 7, 9, 5], [0, 9, 5, 7], [5, 0, 7, 9], [0, 5, 9, 7]]
MINOR = [[0, 8, 3, 10], [0, 5, 10, 3], [0, 10, 8, 10], [0, 3, 8, 7]]
KEYS = {"drums": {"pop", "citypop", "edm", "rock", "hiphop", "funk", "indie", "lofi", "jazz"},
        "arp": {"acoustic", "classical", "indie", "ambient", "jazz", "pop", "citypop", "lofi"},
        "four": {"edm", "pop"}, "sync_bass": {"funk", "hiphop", "citypop", "jazz"},
        "crackle": {"lofi"}, "bright": {"edm", "pop", "citypop", "funk", "rock"}}


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def tone(f, dur, harm, decay):
    t = np.arange(int(dur * SR)) / SR
    wave_ = sum(a * np.sin(2 * np.pi * f * (i + 1) * t) * np.exp(-decay * (i + 1) * t) for i, a in enumerate(harm))
    attack = np.minimum(1.0, t / 0.008)
    return wave_ * attack


def add(buf, at, sig, gain=1.0):
    """원형 버퍼에 더해 꼬리가 곡 앞으로 이어지게 한다 (끊김 없는 반복)."""
    idx = (int(at * SR) + np.arange(len(sig))) % len(buf)
    np.add.at(buf, idx, sig * gain)


def render(track):
    seed = int(hashlib.sha1(track["id"].encode()).hexdigest()[:8], 16)
    rng = np.random.default_rng(seed)
    genre = track["genre"]
    energy, valence = track["mood"]["energy"], track["mood"]["valence"]
    bpm = track.get("bpm") or int(95 + 35 * energy)
    beat = 60 / bpm
    bars = 16
    buf = np.zeros(int(bars * 4 * beat * SR))
    prog = (MAJOR if valence >= 0 else MINOR)[rng.integers(4)]
    third = 4 if valence >= 0 else 3
    root = 45 + int(rng.integers(0, 8))
    bright = genre in KEYS["bright"]
    pad_harm = [1, 0.45, 0.2, 0.1] if bright else [1, 0.25, 0.08]

    for bar in range(bars):
        at = bar * 4 * beat
        degree = prog[bar % 4]
        chord = [root + 12 + degree + n for n in (0, third, 7, 12)]
        dur = 4 * beat * 1.15
        env_decay = 0.12 if energy < 0 else 0.3
        for n in chord:
            add(buf, at, tone(hz(n), dur, pad_harm, env_decay), 0.11)
        # 베이스
        pattern = [0, 1.5, 2.5, 3] if genre in KEYS["sync_bass"] else ([0, 1, 2, 3] if energy > 0.3 else [0, 2])
        for step in pattern:
            add(buf, at + step * beat, tone(hz(root + degree - 12 if root + degree > 50 else root + degree), beat * 0.9, [1, 0.5, 0.2], 1.6), 0.26)
        # 아르페지오
        if genre in KEYS["arp"]:
            per_beat = 2 if energy > -0.3 else 1
            for i in range(4 * per_beat):
                n = chord[int(rng.integers(len(chord)))] + 12
                if rng.random() < 0.82:
                    add(buf, at + i * beat / per_beat, tone(hz(n), beat * 1.4, [1, 0.35, 0.18, 0.08], 2.6), 0.13)
        # 멜로디 (드문드문)
        if bar % 2 == 0:
            scale = [0, 2, 4, 5, 7, 9, 11] if valence >= 0 else [0, 2, 3, 5, 7, 8, 10]
            for i in range(4):
                if rng.random() < 0.55:
                    n = root + 24 + degree + scale[int(rng.integers(7))]
                    add(buf, at + i * beat, tone(hz(n), beat * 1.8, [1, 0.3, 0.1], 1.4), 0.09)
        # 드럼
        if genre in KEYS["drums"] and energy > -0.55:
            level = 0.55 + 0.45 * max(energy, 0)
            kicks = [0, 1, 2, 3] if genre in KEYS["four"] else [0, 2.5] if genre in {"hiphop", "lofi"} else [0, 2]
            for step in kicks:
                t = np.arange(int(0.22 * SR)) / SR
                kick = np.sin(2 * np.pi * (55 + 90 * np.exp(-t * 35)) * t) * np.exp(-t * 16)
                add(buf, at + step * beat, kick, 0.5 * level)
            for step in (1, 3):
                t = np.arange(int(0.18 * SR)) / SR
                snare = rng.normal(0, 1, len(t)) * np.exp(-t * 22) + 0.4 * np.sin(2 * np.pi * 190 * t) * np.exp(-t * 30)
                add(buf, at + step * beat, snare, 0.17 * level)
            for i in range(8):
                t = np.arange(int(0.05 * SR)) / SR
                hat = np.diff(rng.normal(0, 1, len(t) + 1)) * np.exp(-t * 90)
                add(buf, at + i * beat / 2, hat, 0.05 * level)
    if genre in KEYS["crackle"]:
        buf += (rng.random(len(buf)) > 0.9994) * rng.normal(0, 0.15, len(buf))
    buf = np.tanh(buf * 1.3)
    return (buf / np.max(np.abs(buf)) * 0.82 * 32767).astype(np.int16), len(buf) / SR


def main():
    tmp = Path(sys.argv[1])
    tmp.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    catalog = json.loads(CATALOG.read_text(encoding="utf8"))
    catalog["note"] = "곡 정보는 시연용. 음원은 tools/demo-music/generate.py로 합성. 실제 음원 교체는 docs/music-tagging.md"
    for track in catalog["tracks"]:
        pcm, _ = render(track)
        wav = tmp / f"{track['id']}.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        m4a = OUT / f"{track['id']}.m4a"
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "aac", "-b:a", "64k", str(m4a)], check=True)
        wav.unlink()
        track["source"] = f"music/{track['id']}.m4a"
        track["license"] = "자체 합성 시연 음원 (tools/demo-music)"
    CATALOG.write_text(json.dumps(catalog, ensure_ascii=False, indent=1) + "\n", encoding="utf8")


if __name__ == "__main__":
    main()
