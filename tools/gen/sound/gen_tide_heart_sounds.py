"""潮殻の心臓 (最終ボス) の効果音を合成する (独自合成。乱数の種は固定)。

使い方: python tools/gen/sound/gen_tide_heart_sounds.py
出力先: src/main/resources/assets/skyabyss/sounds/tide_heart/

技ごとに別の音にする (08 の 5.3節: 予告は粒に加えて、技ごとに別の音)。
- beat.ogg       : 心臓の鼓動。水の中のくぐもった二拍と、貝殻の鳴り (近くで時々)
- roar.ogg       : 段階の変わり目の咆哮。巻貝の殻が鳴る低い角笛と、殻の割れる音
- breath.ogg     : 潮の吐息の溜め。水が吸い込まれて、噴き出す
- shell.ogg      : 殻打ちの溜め。殻が震えるきしみ
- whirl.ogg      : 渦潮。低く回るうなり
- flood.ogg      : 満ち潮。潮鳴りと、溝から水が上がる音
- spear.ogg      : 珊瑚の槍が突き上がる (乾いた割れる音)
- bubble.ogg     : 泡の牢 (泡がふくらみ、閉じる)
- pearl.ogg      : 真珠の脈 (澄んだ鈴)
- ebb.ogg        : 引き潮の溜め。高い音で水が中心へ流れる
- wave.ogg       : 月潮の大波。遠くから寄せる白い波頭
- rain.ogg       : 真珠の雨 (粒が落ちる、明るい鳴り)
- death.ogg      : 心臓が止まる。最後の鼓動と、殻が崩れて水に沈む
"""
from pathlib import Path

import numpy as np

import gen_sky_sounds as G
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg
from gen_tide_sounds import bubbles, lowpass

OUT = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/assets/skyabyss/sounds/tide_heart"
G.rng = np.random.default_rng(20261018)
import gen_tide_sounds as T  # noqa: E402
T.rng = G.rng


def tone(t, f, harmonics=1, decay=None):
    ph = 2 * np.pi * np.cumsum(np.broadcast_to(f, t.shape)) / SR
    x = sum(np.sin(h * ph) / h ** 1.2 for h in range(1, harmonics + 1))
    if decay:
        x = x * np.exp(-t / decay)
    return x


def thump(t, t0, f0=60, f1=34, amp=1.0):
    k = np.clip(t - t0, 0, None)
    f = f1 + (f0 - f1) * np.exp(-k / 0.08)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-k / 0.18) * (t >= t0)
    return x * amp


def beat():
    t = t_axis(1.6)
    x = thump(t, 0.02, 70, 38) + thump(t, 0.32, 62, 34, 0.7)
    ring = tone(t, 520, 3, 0.5) * 0.05 * env(t, [(0, 0), (0.05, 1), (1.6, 0)])
    return finish(reverb(lowpass(x, 300) + ring, 1.8, 0.35), -4.0)


def roar():
    t = t_axis(3.2)
    n = len(t)
    f0 = np.interp(t, [0, 0.6, 1.6, 3.2], [70, 96, 82, 52])
    horn = tone(t, f0 * (1 + 0.012 * np.sin(2 * np.pi * 5 * t)), 11)
    horn = lowpass(horn, 900) * env(t, [(0, 0), (0.3, 0.8), (1.0, 1.0), (2.4, 0.7), (3.2, 0)])
    crack = band_noise(n, 1200, 6000) * env(t, [(0, 0), (1.5, 0), (1.55, 1.0), (1.9, 0.2), (2.5, 0)]) * 0.5
    water = band_noise(n, 100, 1200) * env(t, [(0, 0), (0.5, 0.4), (2.0, 0.6), (3.2, 0)]) * 0.4
    return finish(reverb(horn + crack + water, 3.5, 0.45))


def breath():
    t = t_axis(2.0)
    n = len(t)
    suck = band_noise(n, 300, 2500) * env(t, [(0, 0), (1.1, 0.8), (1.2, 0.1), (2.0, 0)]) * 0.6
    gush = lowpass(band_noise(n, 80, 3000), 2200) * env(t, [(0, 0), (1.2, 0), (1.25, 1.0), (1.7, 0.4), (2.0, 0)])
    bub = bubbles(t, 30, 300, 1200, amp=0.2) * env(t, [(0, 0), (1.2, 0.2), (1.4, 1), (2.0, 0)])
    return finish(reverb(suck + gush + bub, 1.5, 0.3))


def shell():
    t = t_axis(1.6)
    n = len(t)
    creak = tone(t, 140 + 30 * np.sin(2 * np.pi * 9 * t), 6) * env(t, [(0, 0), (0.1, 0.6), (0.95, 0.8), (1.0, 0), (1.6, 0)]) * 0.5
    knock = thump(t, 1.0, 120, 50, 1.2) + band_noise(n, 600, 3000) * env(t, [(0, 0), (1.0, 0), (1.01, 0.6), (1.2, 0)]) * 0.5
    return finish(reverb(creak + knock, 1.6, 0.35))


def whirl():
    t = t_axis(3.6)
    n = len(t)
    rot = 0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 1.5, 3.6], [1.0, 3.0, 5.0])) / SR)
    rumble = lowpass(band_noise(n, 40, 500), 400) * (0.4 + 0.6 * rot)
    hiss = band_noise(n, 800, 3000) * rot * 0.25
    amp = env(t, [(0, 0), (0.5, 0.6), (1.5, 0.8), (3.3, 1.0), (3.6, 0)])
    return finish(reverb((rumble + hiss) * amp, 2.0, 0.35))


def flood():
    t = t_axis(3.0)
    n = len(t)
    toll = tone(t, 196, 5, 1.5) * 0.4 + tone(t, 294, 3, 1.2) * 0.25
    rise = band_noise(n, 150, 2000) * env(t, [(0, 0), (1.0, 0.3), (1.6, 1.0), (2.6, 0.7), (3.0, 0)])
    return finish(reverb(toll + lowpass(rise, 1800), 2.5, 0.4))


def spear():
    t = t_axis(0.9)
    n = len(t)
    crack = band_noise(n, 1500, 8000) * env(t, [(0, 0), (0.005, 1), (0.12, 0.2), (0.5, 0)])
    pop = thump(t, 0.0, 200, 80, 0.8)
    splash = band_noise(n, 400, 4000) * env(t, [(0, 0), (0.05, 0.5), (0.6, 0)]) * 0.5
    return finish(reverb(crack + pop + splash, 1.0, 0.25))


def bubble():
    t = t_axis(1.8)
    f = np.interp(t, [0, 1.5, 1.8], [180, 520, 300])
    swell = tone(t, f, 2) * env(t, [(0, 0), (0.2, 0.3), (1.45, 0.7), (1.5, 0), (1.8, 0)]) * 0.5
    bub = bubbles(t, 40, 400, 1600, amp=0.35) * env(t, [(0, 0), (0.3, 0.5), (1.5, 1), (1.8, 0)])
    plop = thump(t, 1.5, 400, 150, 0.8)
    return finish(reverb(swell + bub + plop, 1.4, 0.3))


def pearl():
    t = t_axis(2.0)
    chime = sum(tone(t, f, 1, d) * a for f, d, a in ((1318.5, 0.9, 0.5), (1975.5, 0.6, 0.35), (2637.0, 0.4, 0.25)))
    chime2 = np.roll(chime, int(0.12 * SR)) * 0.6
    return finish(reverb(chime + chime2, 2.0, 0.45), -5.0)


def ebb():
    t = t_axis(2.2)
    n = len(t)
    f = np.interp(t, [0, 2.2], [700, 1400])
    whine = tone(t, f, 2) * env(t, [(0, 0), (0.3, 0.3), (2.0, 0.6), (2.2, 0)]) * 0.35
    flow = band_noise(n, 1000, 5000) * env(t, [(0, 0), (0.6, 0.5), (2.0, 1.0), (2.2, 0)]) * 0.6
    return finish(reverb(whine + flow, 1.8, 0.35))


def wave():
    t = t_axis(3.5)
    n = len(t)
    roar_ = lowpass(band_noise(n, 80, 2500), 2000) * env(t, [(0, 0), (1.6, 0.4), (2.2, 1.0), (2.8, 0.6), (3.5, 0)])
    crest = band_noise(n, 2500, 9000) * env(t, [(0, 0), (1.8, 0.1), (2.2, 0.7), (3.0, 0.1), (3.5, 0)]) * 0.5
    low = thump(t, 2.15, 60, 30, 1.0)
    return finish(reverb(roar_ + crest + low, 2.5, 0.4))


def rain():
    t = t_axis(2.0)
    out = np.zeros_like(t)
    rng = G.rng
    for _ in range(14):
        t0 = rng.uniform(0.0, 1.6)
        f = rng.choice([1568.0, 1760.0, 2093.0, 2349.0])
        k = np.clip(t - t0, 0, None)
        out += np.sin(2 * np.pi * f * k) * np.exp(-k / 0.15) * (t >= t0) * rng.uniform(0.3, 0.8)
    return finish(reverb(out, 1.5, 0.4), -5.0)


def death():
    t = t_axis(4.5)
    n = len(t)
    beats = thump(t, 0.05, 70, 36) + thump(t, 0.5, 60, 32, 0.6) + thump(t, 1.6, 50, 28, 0.35)
    crumble = band_noise(n, 400, 5000) * env(t, [(0, 0), (1.8, 0), (2.0, 0.8), (3.0, 0.3), (4.0, 0)]) * 0.6
    sink = lowpass(band_noise(n, 60, 900), 700) * env(t, [(0, 0), (2.0, 0.2), (3.0, 0.7), (4.5, 0)])
    bub = bubbles(t, 20, 200, 900, amp=0.25) * env(t, [(0, 0), (2.5, 0.5), (3.5, 1), (4.5, 0)])
    return finish(reverb(beats + crumble + sink + bub, 3.5, 0.45))


SOUNDS = {"beat": beat, "roar": roar, "breath": breath, "shell": shell, "whirl": whirl, "flood": flood, "spear": spear,
          "bubble": bubble, "pearl": pearl, "ebb": ebb, "wave": wave, "rain": rain, "death": death}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUNDS.items():
        data = fn().astype(np.float32)
        write_ogg(OUT / f"{name}.ogg", data)
        print(name, "%.1f s" % (len(data) / SR))


if __name__ == "__main__":
    main()
