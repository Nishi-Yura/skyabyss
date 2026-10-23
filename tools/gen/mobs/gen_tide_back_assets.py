"""潮殻の背の潮招き (tide_beckoner) の効果音を作る (独自合成。乱数の種は固定)。

使い方: リポジトリ直下で  python tools/gen/mobs/gen_tide_back_assets.py
出力:
  src/main/resources/assets/skyabyss/sounds/tide_back/tide_beckoner_*.ogg   潮招きの声・技 (10 本)
  src/main/resources/assets/skyabyss/sounds.json に、足りない項目だけ足す

(潮の真珠・潮鳴り石・角笛・栓・戸・群れの巣の音と絵は消えた。作らない)
"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../sound',)]
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_sky_sounds as G  # noqa: E402
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent.parent.parent
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
SOUNDS = ASSETS / "sounds/tide_back"
G.rng = np.random.default_rng(20261017)
rng = G.rng


# ---------------------------------------------------------------- 絵
# ---------------------------------------------------------------- 音
def lowpass(x, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec / (1 + (f / hz) ** 4), len(x))


def tone(t, f0, harmonics=8, odd=True):
    phase = 2 * np.pi * np.cumsum(f0 if np.ndim(f0) else np.full_like(t, f0)) / SR
    return sum(np.sin(h * phase) / (h ** (0.9 if (h % 2 or not odd) else 1.7)) for h in range(1, harmonics + 1))


def bubbles(t, rate, lo, hi, dur=0.05, amp=1.0):
    out = np.zeros_like(t)
    n = int(rate * t[-1])
    for _ in range(n):
        t0 = rng.uniform(0, max(0.001, t[-1] - dur))
        f0 = rng.uniform(lo, hi)
        i0 = int(t0 * SR)
        k = np.arange(int(dur * SR)) / SR
        f = f0 * (1 + 2.5 * k / dur)
        seg = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-k / (dur * 0.35)) * rng.uniform(0.3, 1.0)
        out[i0:i0 + len(seg)] += seg[:len(out) - i0]
    return out * amp


def crab_click(t, rate, amp):
    out = np.zeros_like(t)
    n = int(rate * t[-1])
    for _ in range(n):
        i0 = int(rng.uniform(0, t[-1] - 0.03) * SR)
        k = np.arange(int(0.025 * SR)) / SR
        seg = band_noise(len(k), 1500, 6000) * np.exp(-k / 0.004)
        out[i0:i0 + len(seg)] += seg[:len(out) - i0] * rng.uniform(0.3, 1.0)
    return out * amp


def beckoner_ambient():
    t = t_axis(2.4)
    grumble = lowpass(band_noise(len(t), 40, 400), 250) * env(t, [(0, 0), (0.5, 0.8), (1.8, 0.6), (2.4, 0)])
    return finish(reverb(grumble + crab_click(t, 12, 0.25) + bubbles(t, 10, 200, 600, amp=0.2), 2.0, 0.35), -4.0)


def beckoner_hurt():
    t = t_axis(0.8)
    crack = band_noise(len(t), 400, 5000) * env(t, [(0, 0), (0.01, 1), (0.2, 0.2), (0.8, 0)])
    groan = lowpass(band_noise(len(t), 60, 500), 300) * env(t, [(0, 0), (0.05, 0.8), (0.6, 0.2), (0.8, 0)])
    return finish(reverb(crack * 0.6 + groan, 0.8, 0.3), -3.0)


def beckoner_death():
    t = t_axis(4.0)
    groan = lowpass(band_noise(len(t), 40, 600), 350) * env(t, [(0, 0), (0.3, 1), (2.5, 0.6), (4, 0)])
    down = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 4], [90, 35])) / SR) * env(t, [(0, 0), (0.2, 0.7), (3.5, 0.2), (4, 0)])
    crunch = band_noise(len(t), 300, 4000) * env(t, [(0, 0), (2.4, 0), (2.45, 1), (3.0, 0.2), (4, 0)])
    return finish(reverb(groan + down * 0.6 + crunch * 0.5 + bubbles(t, 25, 200, 800, amp=0.3), 3.5, 0.45))


def beckoner_raise():
    t = t_axis(1.0)
    creak = lowpass(band_noise(len(t), 100, 1500), 700) * env(t, [(0, 0), (0.1, 0.6), (0.9, 1.0), (1.0, 0)])
    rise = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 1.0], [60, 140])) / SR) * env(t, [(0, 0), (0.2, 0.5), (1.0, 0.8)])
    return finish(reverb(creak + rise * 0.5 + crab_click(t, 20, 0.2), 1.0, 0.3), -3.0)


def beckoner_slam():
    t = t_axis(2.2)
    boom = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.4, 2.2], [80, 38, 30])) / SR) * env(t, [(0, 0), (0.01, 1), (0.8, 0.3), (2.2, 0)])
    sand = band_noise(len(t), 200, 4000) * env(t, [(0, 0), (0.01, 1), (0.4, 0.3), (1.5, 0), (2.2, 0)])
    splash = lowpass(band_noise(len(t), 300, 5000), 3000) * env(t, [(0, 0), (0.05, 0.6), (0.9, 0.2), (2.2, 0)])
    return finish(reverb(boom + sand * 0.5 + splash * 0.4, 2.0, 0.35))


def beckoner_beckon():
    t = t_axis(3.0)
    horn_part = lowpass(tone(t, np.interp(t, [0, 1.5, 3.0], [55, 62, 52]), 10), 500) * env(t, [(0, 0), (0.4, 0.8), (2.2, 0.9), (3, 0)])
    swell = lowpass(band_noise(len(t), 60, 1200), 600) * env(t, [(0, 0), (1.5, 0.3), (2.6, 1.0), (3, 0)])
    return finish(reverb(horn_part * 0.6 + swell * 0.6 + crab_click(t, 8, 0.2), 3.0, 0.45))


def beckoner_breath():
    t = t_axis(1.2)
    inhale = band_noise(len(t), 300, 2000) * env(t, [(0, 0), (0.6, 0.6), (0.7, 0), (1.2, 0)])
    puff = bubbles(t, 80, 300, 1200, dur=0.06, amp=0.6) * env(t, [(0, 0), (0.7, 0), (0.75, 1), (1.2, 0)])
    return finish(reverb(inhale * 0.4 + puff, 1.0, 0.3), -3.0)


def beckoner_burrow():
    t = t_axis(1.8)
    dig = band_noise(len(t), 150, 3000) * (0.6 + 0.4 * np.sin(2 * np.pi * 7 * t)) * env(t, [(0, 0), (0.1, 1), (1.4, 0.7), (1.8, 0)])
    low = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 1.8], [70, 40])) / SR) * env(t, [(0, 0), (0.2, 0.5), (1.8, 0)])
    return finish(reverb(lowpass(dig, 1800) + low * 0.6, 1.5, 0.3), -3.0)


def beckoner_emerge():
    t = t_axis(1.8)
    burst = band_noise(len(t), 100, 5000) * env(t, [(0, 0), (0.02, 1), (0.5, 0.4), (1.8, 0)])
    boom = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 1.8], [90, 40])) / SR) * env(t, [(0, 0), (0.02, 1), (1.0, 0.2), (1.8, 0)])
    return finish(reverb(burst * 0.6 + boom + crab_click(t, 18, 0.3), 2.0, 0.35))


def beckoner_roar():
    t = t_axis(2.6)
    roar = lowpass(band_noise(len(t), 50, 900) * (1 + 0.5 * np.sin(2 * np.pi * 9 * t)), 500) * env(t, [(0, 0), (0.2, 1), (2.0, 0.8), (2.6, 0)])
    stomps = sum(np.sin(2 * np.pi * 45 * t) * env(t, [(0, 0), (s, 0), (s + 0.01, 1), (s + 0.35, 0), (2.6, 0)]) for s in (0.3, 0.9, 1.5))
    return finish(reverb(roar + stomps * 0.8 + crab_click(t, 25, 0.3), 2.5, 0.4))


SOUND_LIST = {
    "tide_beckoner_ambient": beckoner_ambient, "tide_beckoner_hurt": beckoner_hurt, "tide_beckoner_death": beckoner_death,
    "tide_beckoner_raise": beckoner_raise, "tide_beckoner_slam": beckoner_slam, "tide_beckoner_beckon": beckoner_beckon,
    "tide_beckoner_breath": beckoner_breath, "tide_beckoner_burrow": beckoner_burrow, "tide_beckoner_emerge": beckoner_emerge,
    "tide_beckoner_roar": beckoner_roar,
}


def main():
    SOUNDS.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUND_LIST.items():
        data = fn().astype(np.float32)
        write_ogg(SOUNDS / f"{name}.ogg", data)
        print(name, "%.1f s" % (len(data) / SR))
    path = ASSETS / "sounds.json"
    text = path.read_text(encoding="utf-8")
    sounds = json.loads(text)
    added = 0
    for name in SOUND_LIST:
        if name not in sounds:
            sounds[name] = {"subtitle": f"subtitles.skyabyss.{name}", "sounds": [{"name": f"skyabyss:tide_back/{name}"}]}
            added += 1
    if added:
        # 今のファイルの書き方を崩さないよう、最後の } の前に一行ずつ足す
        body = text.rstrip()
        assert body.endswith("}")
        body = body[:-1].rstrip()
        extra = []
        for name in SOUND_LIST:
            if name not in json.loads(text):
                v = {"subtitle": f"subtitles.skyabyss.{name}", "sounds": [{"name": f"skyabyss:tide_back/{name}"}]}
                extra.append("  " + json.dumps(name) + ": " + json.dumps(v, ensure_ascii=False))
        nl = chr(10)
        path.write_text(body + "," + nl + ("," + nl).join(extra) + nl + "}" + nl, encoding="utf-8")
    print("sounds.json: added", added)


if __name__ == "__main__":
    main()
