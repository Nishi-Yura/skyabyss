"""余燼の背の敵 (灰の番人・火の粉蛾・燠虫) の音を作る (独自合成。乱数の種は固定。13 の 5.1節、11.2節の担当 D)。

使い方: python tools/gen/sound/gen_ember_mob_sounds.py
出力: src/main/resources/assets/skyabyss/sounds/ember_mobs/*.ogg  (sounds.json の行は手で足してある。名前は EmberIds の SOUND_*)

音 (乾いた灰と、奥でくすぶる火。大きな鳴き声は入れない):
- ash_thrall_ambient     : 灰の衣のこすれと、胸の奥でくすぶる低い息 (ふいごの残り)
- ash_thrall_hurt        : 灰の殻が欠ける乾いた音と、詰まった息
- ash_thrall_death       : 崩れて灰の山になる (さらさら落ちる灰と、消える火の小さな破裂)
- ash_thrall_shell_break : 殻が割れる (重い陶器が割れる音、破片)
- ash_thrall_ignite      : 割れ目から火が吹き出す (ぼっという着火と、燃え上がる轟き)
- ember_moth_flutter     : 細かい羽ばたき (乾いた紙をふるわせる音)
- ember_moth_death       : 火の粉が散る (ぱちぱちと小さくはじける)
- cinder_roller_curl     : 殻の節が重なり合う、かちかちという音 (丸まる)
- cinder_roller_roll     : 石の玉が転がるごろごろ (2 秒)
- cinder_roller_bonk     : 壁に当たる鈍い衝突と、殻の反響
- cinder_roller_hurt     : 殻のきしみ
- cinder_roller_death    : 殻がほどけて、中の熾が消える (しゅう)
"""
from pathlib import Path

import numpy as np

import gen_sky_sounds as G
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg

ROOT = Path(__file__).resolve().parent.parent.parent.parent
OUT = ROOT / "src/main/resources/assets/skyabyss/sounds/ember_mobs"
G.rng = np.random.default_rng(20261019)
rng = G.rng


def lowpass(x, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec / (1 + (f / hz) ** 4), len(x))


def grains(t, times, lo, hi, dur=0.012, amp=1.0):
    """乾いた小さな粒 (短い帯域ノイズ)"""
    out = np.zeros_like(t)
    for t0 in times:
        i0 = int(t0 * SR)
        n = int(dur * SR)
        if i0 >= len(out):
            continue
        seg = band_noise(n, lo, hi) * np.exp(-np.arange(n) / (n * 0.25)) * rng.uniform(0.4, 1.0)
        out[i0:i0 + n] += seg[:max(0, len(out) - i0)]
    return out * amp


def crackle(t, rate, lo=1500, hi=7000, amp=1.0):
    """火のぱちぱち (ランダムな時刻の粒)"""
    times = np.sort(rng.uniform(0, t[-1], int(rate * t[-1])))
    return grains(t, times, lo, hi, dur=0.006, amp=amp)


def rumble(t, f0, f1):
    f = np.interp(t, [0, t[-1]], [f0, f1])
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def roar(t, lo=80, hi=900):
    """燃え上がる轟き (低い帯域ノイズ)"""
    return lowpass(band_noise(len(t), lo, hi), hi)


# ---------------------------------------------------------------- 灰の番人

def thrall_ambient():
    t = t_axis(2.4)
    breath = roar(t, 60, 400) * env(t, [(0, 0), (0.8, 0.7), (1.3, 0.8), (2.1, 0.1), (2.4, 0)])
    cloth = band_noise(len(t), 2000, 7000) * env(t, [(0, 0), (0.3, 0.15), (0.9, 0.05), (1.6, 0.2), (2.4, 0)])
    embers = crackle(t, 6, amp=0.25)
    return finish(reverb(breath + cloth * 0.5 + embers, 1.4, 0.3), -7.0)


def thrall_hurt():
    t = t_axis(0.6)
    chip = grains(t, [0.0, 0.02, 0.05, 0.09], 900, 6000, dur=0.03)
    choke = roar(t, 100, 700) * env(t, [(0, 0), (0.05, 0.9), (0.3, 0.3), (0.6, 0)])
    return finish(reverb(chip + choke * 0.6, 0.8, 0.25), -3.0)


def thrall_death():
    t = t_axis(2.0)
    pour = lowpass(band_noise(len(t), 400, 5000), 3000) * env(t, [(0, 0), (0.2, 0.3), (0.6, 0.8), (1.6, 0.4), (2.0, 0)])
    thud = rumble(t, 70, 40) * env(t, [(0, 0), (0.55, 0), (0.6, 1.0), (1.1, 0.1), (2.0, 0)])
    pops = grains(t, np.sort(rng.uniform(0.6, 1.8, 12)), 2000, 8000, dur=0.008, amp=0.6)
    sigh = roar(t, 60, 300) * env(t, [(0, 0), (0.1, 0.6), (0.7, 0.3), (1.2, 0)])
    return finish(reverb(pour * 0.7 + thud * 0.8 + pops + sigh * 0.5, 1.8, 0.35), -3.0)


def thrall_shell_break():
    t = t_axis(1.0)
    crack = grains(t, [0.0, 0.015, 0.04, 0.07, 0.12], 600, 7000, dur=0.05, amp=1.2)
    shards = grains(t, np.sort(rng.uniform(0.05, 0.8, 30)), 2500, 9000, dur=0.012, amp=0.45)
    body = rumble(t, 140, 70) * env(t, [(0, 1), (0.15, 0.5), (0.5, 0)])
    return finish(reverb(crack + shards + body * 0.6, 1.2, 0.3), -2.0)


def thrall_ignite():
    t = t_axis(1.4)
    whoomp = roar(t, 50, 500) * env(t, [(0, 0), (0.06, 1.0), (0.4, 0.6), (1.4, 0)])
    hiss = band_noise(len(t), 1500, 6000) * env(t, [(0, 0), (0.1, 0.4), (0.8, 0.25), (1.4, 0)])
    return finish(reverb(whoomp + hiss * 0.5 + crackle(t, 30, amp=0.5), 1.2, 0.25), -2.5)


# ---------------------------------------------------------------- 火の粉蛾

def moth_flutter():
    t = t_axis(0.8)
    beat = (0.5 + 0.5 * np.sin(2 * np.pi * 26 * t)) ** 3
    paper = band_noise(len(t), 900, 5000) * beat * env(t, [(0, 0), (0.1, 0.8), (0.6, 0.7), (0.8, 0)])
    return finish(lowpass(paper, 4000), -10.0)


def moth_death():
    t = t_axis(0.7)
    pops = grains(t, np.sort(rng.uniform(0.0, 0.5, 14)) ** 1.3, 2500, 9000, dur=0.006, amp=1.0)
    puff = roar(t, 200, 1500) * env(t, [(0, 0.6), (0.15, 0.2), (0.7, 0)])
    return finish(reverb(pops + puff * 0.4, 0.6, 0.2), -5.0)


# ---------------------------------------------------------------- 燠虫

def roller_curl():
    t = t_axis(0.7)
    clicks = grains(t, [0.0, 0.05, 0.1, 0.14, 0.18, 0.22, 0.26, 0.3, 0.34], 700, 4000, dur=0.02, amp=1.0)
    grind = band_noise(len(t), 150, 900) * env(t, [(0, 0), (0.1, 0.5), (0.4, 0.3), (0.7, 0)])
    return finish(reverb(clicks + grind * 0.5, 0.6, 0.2), -4.0)


def roller_roll():
    t = t_axis(2.0)
    roll = lowpass(band_noise(len(t), 40, 400), 350) * (0.7 + 0.3 * np.sin(2 * np.pi * 7 * t))
    ticks = grains(t, np.arange(0.0, 2.0, 1 / 14), 500, 2500, dur=0.015, amp=0.4)
    e = env(t, [(0, 0), (0.1, 1.0), (1.7, 0.9), (2.0, 0)])
    return finish((roll + ticks) * e, -3.5)


def roller_bonk():
    t = t_axis(1.0)
    hit = rumble(t, 110, 50) * env(t, [(0, 1.0), (0.05, 0.7), (0.4, 0)])
    knock = grains(t, [0.0, 0.01], 300, 3000, dur=0.06, amp=1.2)
    ring = np.sin(2 * np.pi * 620 * t) * np.exp(-t * 9) * 0.3
    return finish(reverb(hit + knock + ring, 0.9, 0.3), -1.5)


def roller_hurt():
    t = t_axis(0.5)
    creak = band_noise(len(t), 300, 1800) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 34 * t))) * env(t, [(0, 0), (0.03, 1), (0.3, 0.3), (0.5, 0)])
    return finish(reverb(lowpass(creak, 2000) + grains(t, [0.0], 800, 4000, dur=0.03), 0.6, 0.2), -4.0)


def roller_death():
    t = t_axis(1.6)
    unroll = grains(t, np.sort(rng.uniform(0.0, 0.5, 9)), 600, 3500, dur=0.03, amp=0.8)
    hiss = band_noise(len(t), 2500, 9000) * env(t, [(0, 0), (0.4, 0.0), (0.5, 0.7), (1.4, 0.1), (1.6, 0)])
    thump = rumble(t, 90, 45) * env(t, [(0, 0), (0.3, 0), (0.35, 0.8), (0.8, 0)])
    return finish(reverb(unroll + hiss * 0.5 + thump * 0.6, 1.2, 0.3), -3.5)


SOUNDS = {
    "ash_thrall_ambient": thrall_ambient,
    "ash_thrall_hurt": thrall_hurt,
    "ash_thrall_death": thrall_death,
    "ash_thrall_shell_break": thrall_shell_break,
    "ash_thrall_ignite": thrall_ignite,
    "ember_moth_flutter": moth_flutter,
    "ember_moth_death": moth_death,
    "cinder_roller_curl": roller_curl,
    "cinder_roller_roll": roller_roll,
    "cinder_roller_bonk": roller_bonk,
    "cinder_roller_hurt": roller_hurt,
    "cinder_roller_death": roller_death,
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUNDS.items():
        data = np.nan_to_num(fn()).astype(np.float32)
        assert np.abs(data).max() > 0.05, name
        write_ogg(OUT / f"{name}.ogg", data)
        print(name, "%.2f s" % (len(data) / SR))


if __name__ == "__main__":
    main()
