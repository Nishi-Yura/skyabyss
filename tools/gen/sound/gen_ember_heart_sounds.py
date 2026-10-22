"""余燼の心臓 (最終ボス) の効果音を合成する (独自合成。乱数の種は固定)。

使い方: python tools/gen/sound/gen_ember_heart_sounds.py
出力先: src/main/resources/assets/skyabyss/sounds/ember_heart/

技ごとに別の音にする (13 の 5.3節: 予告は粒に加えて、技ごとに別の音)。
- beat.ogg   : 心臓の鼓動。炉の底の低い二拍と、燃える薪のはぜる音 (段階 3 ではだんだん遅く弱く鳴らす)
- roar.ogg   : 段階の変わり目の咆哮。低い炉の唸りと、殻の割れる音
- breath.ogg : 火の息の溜め。鞴が吸い込み、噴き出す
- pulse.ogg  : 熱の脈。低い一打と、熱い風
- orb.ogg    : 熔け玉。熔けた塊が持ち上がり、落ちる
- floor.ogg  : 崩れる床。石が軋んで割れ、ぴしぴしと鳴る
- gale.ogg   : 鞴の嵐。風が心臓へ流れ込み、うなる
- pellet.ogg : 灰の礫。乾いた灰が固まって浮かぶ音
- chill.ogg  : 冷え (段階 3 に入る)。火がすっと落ちる低い音と、静かな金属の鳴り
- embers.ogg : 残り火。火の粉がぱちぱちと飛ぶ
- fading.ogg : 消えかけの鼓動。深く低い一拍。だんだん遅く、弱く
- echo.ogg   : ずっと下から返る拍 (音だけ。13 の 15 章の判断 5)。ごく小さく、遠く、籠もる
- veil.ogg   : 灰の帳。灰がさあっと降る
- death.ogg  : 心臓が止まる。最後の鼓動と、殻が崩れて灰になる (そのあと、遅れて鳴らさない)
"""
from pathlib import Path

import numpy as np

import gen_sky_sounds as G
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg
from gen_tide_sounds import lowpass

OUT = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/assets/skyabyss/sounds/ember_heart"
G.rng = np.random.default_rng(20261019)
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
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-k / 0.2) * (t >= t0)
    return x * amp


def crackles(t, count, lo=1500, hi=6000, amp=0.4):
    """火のはぜる音: 短い雑音の粒をばらまく"""
    out = np.zeros_like(t)
    rng = G.rng
    n = len(t)
    for _ in range(count):
        i = int(rng.uniform(0, n * 0.95))
        ln = int(rng.uniform(0.004, 0.02) * SR)
        burst = band_noise(ln, lo, hi) * np.exp(-np.linspace(0, 6, ln)) * rng.uniform(0.3, 1.0)
        out[i:i + ln] += burst[: n - i]
    return out * amp


def beat():
    t = t_axis(1.6)
    x = thump(t, 0.02, 64, 34) + thump(t, 0.34, 56, 30, 0.7)
    fire = crackles(t, 10, 1200, 5000, 0.12) * env(t, [(0, 0), (0.05, 1), (1.6, 0)])
    return finish(reverb(lowpass(x, 280) + fire, 1.6, 0.3), -4.0)


def roar():
    t = t_axis(3.2)
    n = len(t)
    f0 = np.interp(t, [0, 0.6, 1.6, 3.2], [58, 82, 70, 44])
    horn = tone(t, f0 * (1 + 0.015 * np.sin(2 * np.pi * 4 * t)), 12)
    horn = lowpass(horn, 700) * env(t, [(0, 0), (0.3, 0.8), (1.0, 1.0), (2.4, 0.7), (3.2, 0)])
    crack = band_noise(n, 900, 6000) * env(t, [(0, 0), (1.5, 0), (1.55, 1.0), (1.9, 0.2), (2.5, 0)]) * 0.5
    fire = lowpass(band_noise(n, 80, 1500), 1200) * env(t, [(0, 0), (0.5, 0.5), (2.0, 0.7), (3.2, 0)]) * 0.5
    return finish(reverb(horn + crack + fire + crackles(t, 40, 1000, 6000, 0.2), 3.0, 0.4))


def breath():
    t = t_axis(2.0)
    n = len(t)
    suck = band_noise(n, 300, 2500) * env(t, [(0, 0), (1.1, 0.8), (1.2, 0.1), (2.0, 0)]) * 0.6
    gush = lowpass(band_noise(n, 80, 3500), 2800) * env(t, [(0, 0), (1.2, 0), (1.25, 1.0), (1.7, 0.4), (2.0, 0)])
    return finish(reverb(suck + gush + crackles(t, 20, 1500, 6000, 0.15) * env(t, [(0, 0), (1.2, 0.3), (1.4, 1), (2.0, 0)]), 1.4, 0.3))


def pulse():
    t = t_axis(1.6)
    n = len(t)
    glow = thump(t, 0.9, 120, 44, 1.2) + band_noise(n, 400, 3000) * env(t, [(0, 0), (0.9, 0), (0.92, 0.6), (1.3, 0)]) * 0.4
    rise = tone(t, np.interp(t, [0, 0.9], [90, 180]), 4) * env(t, [(0, 0), (0.85, 0.5), (0.9, 0), (1.6, 0)]) * 0.5
    return finish(reverb(glow + rise, 1.6, 0.35))


def orb():
    t = t_axis(2.2)
    n = len(t)
    lift = lowpass(band_noise(n, 100, 1500), 1200) * env(t, [(0, 0), (1.2, 0.5), (1.5, 0.2), (2.2, 0)])
    drop = thump(t, 1.5, 180, 60, 1.1) + band_noise(n, 500, 4000) * env(t, [(0, 0), (1.5, 0), (1.51, 0.7), (1.9, 0)]) * 0.5
    return finish(reverb(lift + drop + crackles(t, 18, 1500, 5000, 0.15), 1.6, 0.3))


def floor():
    t = t_axis(2.6)
    n = len(t)
    creak = tone(t, 90 + 25 * np.sin(2 * np.pi * 7 * t), 7) * env(t, [(0, 0), (0.2, 0.5), (1.9, 0.9), (2.0, 0), (2.6, 0)]) * 0.45
    snaps = crackles(t, 26, 800, 4500, 0.9) * env(t, [(0, 0), (0.1, 0.6), (1.9, 1.0), (2.0, 0), (2.6, 0)])
    sink = thump(t, 2.0, 100, 38, 1.2) + lowpass(band_noise(n, 60, 800), 600) * env(t, [(0, 0), (2.0, 0), (2.05, 0.8), (2.6, 0)]) * 0.5
    return finish(reverb(creak + snaps + sink, 2.0, 0.35))


def gale():
    t = t_axis(3.6)
    n = len(t)
    rot = 0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 1.5, 3.6], [1.0, 2.5, 4.0])) / SR)
    wind = lowpass(band_noise(n, 60, 1800), 1500) * (0.45 + 0.55 * rot)
    hiss = band_noise(n, 900, 4000) * rot * 0.25
    amp = env(t, [(0, 0), (0.5, 0.6), (1.5, 0.8), (3.3, 1.0), (3.6, 0)])
    burst = thump(t, 3.5, 90, 36, 1.0) * 0.8
    return finish(reverb((wind + hiss) * amp + burst, 2.0, 0.35))


def pellet():
    t = t_axis(1.8)
    n = len(t)
    dust = band_noise(n, 500, 3500) * env(t, [(0, 0), (0.7, 0.5), (1.0, 0.2), (1.8, 0)]) * 0.5
    clicks = crackles(t, 14, 2000, 7000, 0.6) * env(t, [(0, 0), (0.6, 0.4), (1.0, 1.0), (1.8, 0)])
    low = tone(t, np.interp(t, [0, 1.0, 1.8], [110, 160, 120]), 3) * env(t, [(0, 0), (0.8, 0.4), (1.8, 0)]) * 0.25
    return finish(reverb(dust + clicks + low, 1.2, 0.3))


def chill():
    t = t_axis(3.4)
    n = len(t)
    drop = tone(t, np.interp(t, [0, 0.6, 3.4], [220, 110, 62]), 5) * env(t, [(0, 0), (0.05, 0.8), (1.2, 0.5), (3.4, 0)]) * 0.5
    hush = lowpass(band_noise(n, 100, 2000), 1500) * env(t, [(0, 0), (0.1, 0.7), (1.0, 0.3), (3.4, 0)]) * 0.5
    ring = (tone(t, 740, 2, 1.8) * 0.12 + tone(t, 1109, 1, 1.4) * 0.08) * env(t, [(0, 0), (0.4, 1), (3.4, 0)])
    return finish(reverb(drop + hush + ring, 3.2, 0.5))


def embers():
    t = t_axis(2.2)
    n = len(t)
    pops = crackles(t, 60, 1200, 7500, 0.9) * env(t, [(0, 0), (0.1, 0.6), (1.0, 1.0), (2.2, 0)])
    whoosh = lowpass(band_noise(n, 200, 3000), 2400) * env(t, [(0, 0), (1.0, 0.3), (1.05, 0.7), (1.6, 0)]) * 0.45
    return finish(reverb(pops + whoosh, 1.4, 0.3), -4.0)


def fading():
    t = t_axis(3.4)
    n = len(t)
    pre = lowpass(band_noise(n, 40, 400), 300) * env(t, [(0, 0), (1.6, 0.4), (2.0, 0.1), (3.4, 0)]) * 0.5
    hit = thump(t, 2.0, 52, 26, 1.6) + thump(t, 2.0, 100, 40, 0.5)
    ring = tone(t, 196, 4, 1.5) * 0.12 * env(t, [(0, 0), (2.0, 0), (2.02, 1), (3.4, 0)])
    return finish(reverb(pre + lowpass(hit, 240) + ring, 3.4, 0.5))


def echo():
    t = t_axis(1.8)
    x = thump(t, 0.05, 48, 28, 1.0) + thump(t, 0.4, 44, 26, 0.6)
    return finish(reverb(lowpass(x, 160), 3.0, 0.55), -9.0)


def veil():
    t = t_axis(3.0)
    n = len(t)
    fall = band_noise(n, 2000, 9000) * env(t, [(0, 0), (1.0, 0.4), (1.6, 0.7), (3.0, 0)]) * 0.4
    soft = lowpass(band_noise(n, 80, 1200), 900) * env(t, [(0, 0), (0.8, 0.5), (3.0, 0)]) * 0.5
    return finish(reverb(fall + soft + crackles(t, 10, 2000, 6000, 0.1), 2.4, 0.4), -4.0)


def death():
    t = t_axis(5.0)
    n = len(t)
    beats = thump(t, 0.05, 66, 34) + thump(t, 0.55, 56, 30, 0.55) + thump(t, 1.9, 46, 26, 0.3)
    crumble = band_noise(n, 300, 5000) * env(t, [(0, 0), (2.4, 0), (2.6, 0.8), (3.6, 0.3), (4.6, 0)]) * 0.55
    sink = lowpass(band_noise(n, 50, 800), 600) * env(t, [(0, 0), (2.4, 0.2), (3.4, 0.7), (5.0, 0)])
    return finish(reverb(beats + crumble + sink + crackles(t, 30, 1000, 6000, 0.2) * env(t, [(0, 1), (2.0, 0.6), (3.5, 0)]), 3.4, 0.45))


SOUNDS = {"beat": beat, "roar": roar, "breath": breath, "pulse": pulse, "orb": orb, "floor": floor, "gale": gale, "pellet": pellet,
          "chill": chill, "embers": embers, "fading": fading, "echo": echo, "veil": veil, "death": death}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUNDS.items():
        data = fn().astype(np.float32)
        write_ogg(OUT / f"{name}.ogg", data)
        print(name, "%.1f s" % (len(data) / SR))


if __name__ == "__main__":
    main()
