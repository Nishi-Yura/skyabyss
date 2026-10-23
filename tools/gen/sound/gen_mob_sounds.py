"""モブの鳴き声・足音・攻撃音を、見た目と材質に合わせて作る (独自合成。乱数の種は固定)。

使い方: python tools/gen/sound/gen_mob_sounds.py
出力: src/main/resources/assets/skyabyss/sounds/mobs/*.ogg  (同じ事象の別テイクは _1 _2 を付ける)
sounds.json の行は末尾に足してある (キーは <モブ>_<ambient|hurt|death|step|...>)。登録は registry/MobSounds。

材質ごとの音の作り方:
- 骨 (骨の番兵・骨棘の射手・番兵長): 減衰する倍音の打撃 (こつ、からん) と、骨がこすれるきしみ
- 苔・虫 (苔這い): 湿った小さな粒と、濡れたノイズのつぶれ
- 水 (水路の顎): 低い喉の声に泡と水のうねり
- 石・岩 (岩鯨の心臓): 低い脈動、石の割れ、ごろごろ
- 余燼の心臓: 玄武岩の割れと、炎のはぜる音 / 嵐の心臓: 放電と金属の響き、雷鳴 / 潮殻の心臓: 水晶と水の破裂
- 足音: 灰の番人=灰を踏む / 珊瑚の従者=濡れた殻 / 羽の番人=乾いた羽と爪 / 雷の請願者=布と静電気
"""
from pathlib import Path

import numpy as np

import gen_sky_sounds as G
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg

ROOT = Path(__file__).resolve().parent.parent.parent.parent
OUT = ROOT / "src/main/resources/assets/skyabyss/sounds/mobs"
G.rng = np.random.default_rng(20261020)
rng = G.rng


# ---------------------------------------------------------------- 部品

def lowpass(x, hz, order=4):
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(np.fft.rfft(x) / (1 + (f / hz) ** order), len(x))


def highpass(x, hz, order=4):
    f = np.fft.rfftfreq(len(x), 1 / SR)
    r = (f / hz) ** order
    return np.fft.irfft(np.fft.rfft(x) * r / (1 + r), len(x))


def put(out, seg, t0):
    i0 = int(t0 * SR)
    if i0 >= len(out) or i0 < 0:
        return
    seg = seg[:len(out) - i0]
    out[i0:i0 + len(seg)] += seg


def modal(t, t0, base, ratios, decays, amp=1.0, jitter=0.03):
    """硬いものを叩いたときの、減衰する倍音の和"""
    out = np.zeros_like(t)
    i0 = int(t0 * SR)
    if i0 >= len(t):
        return out
    k = t[:len(t) - i0]
    for r, d in zip(ratios, decays):
        f = base * r * (1 + rng.uniform(-jitter, jitter))
        out[i0:] += np.sin(2 * np.pi * f * k + rng.uniform(0, 6)) * np.exp(-k / d) * (1 / (1 + 0.3 * r))
    return out * amp


def click(t, t0, lo=1500, hi=7000, dur=0.006, amp=1.0):
    out = np.zeros_like(t)
    n = int(dur * SR)
    put(out, band_noise(n, lo, hi) * np.exp(-np.arange(n) / (n * 0.3)) * rng.uniform(0.6, 1.0) * amp, t0)
    return out


def bone_knock(t, t0, base=650, amp=1.0, decay=1.0):
    """骨を叩く、こつ・からん"""
    d = np.array([0.045, 0.03, 0.02, 0.012]) * decay
    return (modal(t, t0, base, [1, 1.93, 3.07, 4.6], d, amp) + click(t, t0, 1200, 6500, 0.005, amp * 0.5))


def stone_knock(t, t0, base=170, amp=1.0, decay=1.0):
    """石を叩く、重い響き"""
    d = np.array([0.14, 0.09, 0.05, 0.03]) * decay
    return modal(t, t0, base, [1, 1.58, 2.41, 3.7], d, amp, 0.05) + click(t, t0, 400, 3000, 0.012, amp * 0.5)


def noise_hit(t, t0, lo, hi, dur, amp=1.0):
    out = np.zeros_like(t)
    n = int(dur * SR)
    put(out, band_noise(n, lo, hi) * np.exp(-np.arange(n) / (n * 0.3)) * amp, t0)
    return out


def creak(t, f0, f1, lo=500, hi=2800, amp=1.0, soft=0.5):
    """骨や木がこすれるきしみ (滑りと引っかかりの繰り返し)"""
    rate = np.interp(t, [0, t[-1]], [f0, f1]) * (1 + 0.15 * np.sin(2 * np.pi * 7 * t))
    ph = np.cumsum(rate) / SR
    saw = ph % 1.0
    pulses = np.exp(-saw * 7) * (0.6 + 0.4 * band_noise(len(t), 20, 90))
    return highpass(lowpass(pulses * band_noise(len(t), lo, hi), hi), lo) * amp * soft


def voice(t, f0, formants, rough=0.01, amp=None, breath=0.1, nh=28, vib=(4.5, 0.01)):
    """声帯の声。f0 は配列、formants は (中心 Hz, 幅, 強さ) の列"""
    f0 = f0 * (1 + vib[1] * np.sin(2 * np.pi * vib[0] * t) + rough * band_noise(len(t), 15, 60))
    ph = 2 * np.pi * np.cumsum(f0) / SR
    out = np.zeros_like(t)
    for h in range(1, nh):
        fh = f0 * h
        g = 0.06 / h
        for c, w, s in formants:
            g = g + s * np.exp(-((fh - c) / w) ** 2)
        out += np.sin(h * ph) * g * (fh < SR / 2.2)
    out = out / (np.max(np.abs(out)) + 1e-9)
    out += band_noise(len(t), 200, 3000) * breath
    return out * (amp if amp is not None else 1.0)


def bubbles(t, n, lo, hi, dur=0.05, amp=1.0, span=None):
    out = np.zeros_like(t)
    a, b = span or (0, t[-1] - dur)
    for _ in range(n):
        t0 = rng.uniform(a, max(a + 0.001, b))
        f0 = rng.uniform(lo, hi)
        k = np.arange(int(dur * SR)) / SR
        f = f0 * (1 + 2.5 * k / dur)
        seg = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-k / (dur * 0.35)) * rng.uniform(0.3, 1.0)
        put(out, seg, t0)
    return out * amp


def crackle(t, rate, lo=1500, hi=7000, amp=1.0, span=None):
    out = np.zeros_like(t)
    a, b = span or (0, t[-1])
    for t0 in np.sort(rng.uniform(a, b, int(rate * (b - a)))):
        out += noise_hit(t, t0, lo, hi, 0.006, rng.uniform(0.3, 1.0))
    return out * amp


def rumble(t, f0, f1, shape=None):
    f = np.interp(t, [0, t[-1]], [f0, f1])
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def mixf(t, *layers):
    return sum(layers)


def E(t, pts):
    return env(t, pts)


# ---------------------------------------------------------------- 骨の番兵 (骨でできた盾持ちの番兵)

def bone_sentinel_ambient():
    t = t_axis(1.6)
    c = creak(t, 14, 30, 400, 2200) * E(t, [(0, 0), (0.3, 0.8), (0.9, 0.5), (1.5, 0)])
    hollow = lowpass(band_noise(len(t), 150, 520), 520) * E(t, [(0, 0), (0.6, 0.6), (1.2, 0.3), (1.6, 0)])
    tick = bone_knock(t, 1.05, 520, 0.25)
    return finish(reverb(c * 0.8 + hollow * 0.8 + tick, 0.7, 0.2), -8.0)


def bone_sentinel_hurt(base):
    t = t_axis(0.45)
    hit = bone_knock(t, 0.0, base, 1.0) + bone_knock(t, 0.035, base * 1.35, 0.6) + noise_hit(t, 0.0, 2500, 8000, 0.02, 0.5)
    rasp = creak(t, 40, 20, 600, 2500) * E(t, [(0, 1), (0.25, 0.3), (0.45, 0)])
    return finish(reverb(hit + rasp * 0.5, 0.5, 0.15), -3.0)


def bone_sentinel_death():
    t = t_axis(1.7)
    out = np.zeros_like(t)
    times = np.sort(rng.uniform(0.0, 1.0, 14) ** 1.4)
    for i, t0 in enumerate(times):
        out += bone_knock(t, t0, rng.uniform(380, 820), 0.9 * (1 - i / 20))
    out += noise_hit(t, 0.0, 600, 5000, 0.35, 0.3) * 0.5
    out += stone_knock(t, 0.05, 120, 0.5)
    dust = lowpass(band_noise(len(t), 1500, 7000), 5000) * E(t, [(0, 0), (0.4, 0.15), (1.0, 0.1), (1.7, 0)])
    return finish(reverb(out + dust * 0.2, 1.0, 0.25), -2.5)


def bone_step(base, amp_soft=1.0):
    t = t_axis(0.22)
    out = bone_knock(t, 0.0, base, 0.8, 0.8) + noise_hit(t, 0.0, 300, 1800, 0.06, 0.3 * amp_soft)
    out += bone_knock(t, 0.07, base * rng.uniform(1.1, 1.3), 0.25, 0.6)
    return finish(out, -5.0)


def bone_guard():
    t = t_axis(0.4)
    out = bone_knock(t, 0.0, 330, 1.0, 1.4) + stone_knock(t, 0.0, 220, 0.6, 0.5)
    return finish(reverb(out, 0.5, 0.15), -3.0)


# ---------------------------------------------------------------- 骨棘の射手 (肋骨を弓にした、細い骨の射手)

def rib_archer_ambient():
    t = t_axis(1.4)
    c = creak(t, 24, 55, 900, 4000) * E(t, [(0, 0), (0.25, 0.8), (0.7, 0.6), (1.1, 0.1), (1.4, 0)])
    hum = np.sin(2 * np.pi * 196 * t) * E(t, [(0, 0), (0.7, 0.25), (1.4, 0)]) * (1 + 0.3 * np.sin(2 * np.pi * 5 * t))
    tick = bone_knock(t, 0.9, 960, 0.3)
    return finish(reverb(c * 0.7 + hum * 0.12 + tick, 0.6, 0.2), -9.0)


def rib_archer_shoot():
    t = t_axis(0.7)
    f = np.interp(t, [0, 0.4], [330, 215])
    ph = 2 * np.pi * np.cumsum(f) / SR
    twang = (np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.3 * np.sin(3 * ph)) * np.exp(-t / 0.18) * (1 + 0.4 * np.sin(2 * np.pi * 38 * t))
    snap = noise_hit(t, 0.0, 1800, 8000, 0.012, 1.0)
    knock = bone_knock(t, 0.0, 480, 0.8, 1.0)
    whoosh = band_noise(len(t), 1200, 5000) * E(t, [(0, 0), (0.03, 0.6), (0.25, 0)]) * 0.4
    return finish(reverb(twang * 0.6 + snap + knock + whoosh, 0.5, 0.15), -3.0)


# ---------------------------------------------------------------- 苔這い (苔むした石の虫)

def skitter(t, span, rate, lo=1800, hi=6000, amp=1.0):
    out = np.zeros_like(t)
    a, b = span
    times = np.cumsum(rng.uniform(0.6, 1.4, int((b - a) * rate * 1.2))) / rate + a
    for t0 in times[times < b]:
        out += noise_hit(t, t0, lo * rng.uniform(0.8, 1.2), hi, 0.009, rng.uniform(0.3, 1.0))
    return out * amp


def moss_crawler_ambient():
    t = t_axis(1.5)
    sk = skitter(t, (0.1, 1.3), 38, 2000, 6500) * E(t, [(0, 0), (0.3, 1), (0.9, 0.7), (1.5, 0)])
    wet = lowpass(band_noise(len(t), 300, 1500), 1500) * (0.5 + 0.5 * np.sin(2 * np.pi * 9 * t)) ** 2 * E(t, [(0, 0), (0.5, 0.5), (1.1, 0.2), (1.5, 0)])
    chirp = np.zeros_like(t)
    for t0 in (0.45, 0.62):
        k = t - t0
        f = 1500 + 900 * np.clip(k, 0, 0.08) / 0.08
        chirp += np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-np.clip(k, 0, None) / 0.03) * (k > 0) * 0.15
    return finish(reverb(sk * 0.5 + wet * 0.4 + chirp, 0.5, 0.15), -8.0)


def moss_crawler_hurt():
    t = t_axis(0.5)
    squelch = lowpass(band_noise(len(t), 100, 1800), 1800) * E(t, [(0, 1), (0.05, 0.9), (0.3, 0.2), (0.5, 0)])
    splat = noise_hit(t, 0.0, 800, 4000, 0.04, 0.9)
    f = np.interp(t, [0, 0.25], [2400, 900])
    screech = np.sin(2 * np.pi * np.cumsum(f) / SR) * E(t, [(0, 0), (0.02, 0.5), (0.25, 0)]) * 0.2
    return finish(reverb(squelch * 0.6 + splat + screech, 0.4, 0.12), -4.0)


def moss_crawler_death():
    t = t_axis(1.3)
    deflate = lowpass(band_noise(len(t), 100, 1500), 1500) * E(t, [(0, 0), (0.05, 1), (0.6, 0.3), (1.3, 0)])
    rustle = skitter(t, (0.05, 0.9), 55, 1500, 5000) * E(t, [(0, 1), (0.9, 0)]) * 0.5
    thump = rumble(t, 110, 55) * E(t, [(0, 0), (0.05, 0.8), (0.3, 0)]) * 0.5
    f = np.interp(t, [0, 0.7], [1800, 400])
    wail = np.sin(2 * np.pi * np.cumsum(f) / SR) * E(t, [(0, 0), (0.03, 0.4), (0.7, 0)]) * 0.15
    return finish(reverb(deflate * 0.6 + rustle + thump + wail, 0.7, 0.2), -3.5)


def moss_step():
    t = t_axis(0.15)
    out = noise_hit(t, 0.0, 800, 3500, 0.04, 0.7) + noise_hit(t, 0.045, 1500, 5000, 0.02, 0.5)
    out += lowpass(band_noise(len(t), 80, 500), 500) * E(t, [(0, 1), (0.1, 0)]) * 0.4
    return finish(out, -7.0)


# ---------------------------------------------------------------- 水路の顎 (暗い水路の魚のような捕食者)

# ---------------------------------------------------------------- 鯨骨の番兵長 (大きな鯨の骨の戦士)

def captain_ambient():
    t = t_axis(2.2)
    f0 = np.interp(t, [0, 1.0, 2.2], [48, 56, 42])
    moan = voice(t, f0, [(180, 90, 1.0), (420, 150, 0.4)], rough=0.03, breath=0.1, vib=(3, 0.015))
    moan = lowpass(moan, 600) * E(t, [(0, 0), (0.7, 0.7), (1.4, 0.6), (2.2, 0)])
    breath = lowpass(band_noise(len(t), 100, 420), 420) * E(t, [(0, 0), (0.8, 0.5), (1.5, 0.4), (2.2, 0)])
    rattle = np.zeros_like(t)
    for t0 in rng.uniform(0.3, 1.8, 5):
        rattle += bone_knock(t, t0, rng.uniform(260, 420), 0.25, 1.4)
    c = creak(t, 10, 22, 250, 1400) * E(t, [(0, 0), (0.7, 0.5), (1.6, 0.3), (2.2, 0)])
    return finish(reverb(moan * 0.7 + breath * 0.6 + rattle + c * 0.5, 1.2, 0.3), -6.0)


def captain_hurt(base):
    t = t_axis(0.8)
    out = bone_knock(t, 0.0, base, 1.0, 1.6) + stone_knock(t, 0.0, base * 0.35, 0.8, 0.8) + bone_knock(t, 0.05, base * 1.3, 0.5, 1.2)
    f0 = np.interp(t, [0, 0.4], [95, 55])
    grunt = lowpass(voice(t, f0, [(200, 100, 1.0), (500, 160, 0.4)], rough=0.04, breath=0.1), 700) * E(t, [(0, 0), (0.03, 1), (0.4, 0.3), (0.8, 0)])
    return finish(reverb(out + grunt * 0.6, 0.8, 0.2), -2.5)


def captain_death():
    t = t_axis(2.6)
    out = np.zeros_like(t)
    times = np.sort(rng.uniform(0.2, 1.8, 16) ** 1.2)
    for i, t0 in enumerate(times):
        out += bone_knock(t, t0, rng.uniform(220, 520), 0.9 * (1 - i / 24), 1.6)
    out += stone_knock(t, 0.0, 70, 1.2, 1.5) + stone_knock(t, 1.1, 90, 0.9, 1.2)
    f0 = np.interp(t, [0, 1.6, 2.6], [90, 50, 34])
    moan = lowpass(voice(t, f0, [(170, 90, 1.0), (400, 140, 0.4)], rough=0.05, breath=0.1), 600) * E(t, [(0, 0), (0.1, 0.8), (1.4, 0.5), (2.6, 0)])
    rum = lowpass(band_noise(len(t), 30, 300), 300) * E(t, [(0, 0), (0.1, 0.9), (1.5, 0.4), (2.6, 0)])
    dust = lowpass(band_noise(len(t), 1200, 6000), 4500) * E(t, [(0, 0), (0.8, 0.2), (2.6, 0)])
    return finish(reverb(out + moan * 0.6 + rum * 0.6 + dust * 0.15, 1.8, 0.35), -2.0)


def captain_step(base):
    t = t_axis(0.4)
    out = stone_knock(t, 0.0, base, 1.0, 0.7) + bone_knock(t, 0.01, 260, 0.6, 1.2) + bone_knock(t, 0.09, 330, 0.3, 1.0)
    out += lowpass(band_noise(len(t), 40, 200), 200) * E(t, [(0, 1), (0.2, 0)])
    return finish(reverb(out, 0.5, 0.15), -3.5)


def captain_swing():
    t = t_axis(0.9)
    air = band_noise(len(t), 150, 1800) * E(t, [(0, 0), (0.3, 0.4), (0.5, 1.0), (0.75, 0)])
    low = lowpass(band_noise(len(t), 40, 250), 250) * E(t, [(0, 0), (0.4, 0.3), (0.55, 0.8), (0.9, 0)])
    k = bone_knock(t, 0.0, 300, 0.3, 1.2)
    return finish(reverb(air * 0.7 + low * 0.7 + k, 0.5, 0.15), -3.0)


def captain_slam():
    t = t_axis(1.4)
    out = stone_knock(t, 0.0, 85, 1.2, 1.5) + bone_knock(t, 0.0, 240, 0.9, 1.5) + bone_knock(t, 0.03, 470, 0.5, 1.0)
    boom = rumble(t, 70, 32) * E(t, [(0, 1), (0.2, 0.6), (1.2, 0)])
    debris = lowpass(band_noise(len(t), 500, 5000), 4000) * E(t, [(0, 0.8), (0.3, 0.3), (1.0, 0)])
    return finish(reverb(out + boom * 0.7 + debris * 0.3, 1.2, 0.3), -2.0)


# ---------------------------------------------------------------- 岩鯨の心臓 (鯨の胸の奥で脈打つ石)

def whale_heart_ambient():
    t = t_axis(2.2)
    pulse = np.zeros_like(t)
    for t0 in (0.15, 0.55):
        k = np.clip(t - t0, 0, None)
        pulse += rumble(t, 52, 42) * np.exp(-k / 0.14) * (t > t0) * (1.0 if t0 < 0.3 else 0.7)
    grind = creak(t, 8, 14, 120, 700) * E(t, [(0, 0), (0.9, 0.4), (1.5, 0.5), (2.2, 0)])
    glim = np.zeros_like(t)
    for t0 in (1.1, 1.45):
        glim += modal(t, t0, rng.uniform(1400, 2200), [1, 2.76, 5.4], [0.5, 0.3, 0.15], 0.06, 0.01)
    hum = lowpass(band_noise(len(t), 60, 260), 260) * E(t, [(0, 0), (0.8, 0.4), (2.2, 0)])
    return finish(reverb(pulse * 0.9 + grind * 0.4 + glim + hum * 0.5, 1.6, 0.35), -6.0)


def whale_heart_hurt():
    t = t_axis(1.0)
    crack = stone_knock(t, 0.0, 150, 1.0, 1.4) + stone_knock(t, 0.04, 260, 0.6, 1.0) + noise_hit(t, 0.0, 800, 6000, 0.05, 0.9)
    hum = modal(t, 0.0, 95, [1, 1.5, 2.01, 3.0], [0.7, 0.5, 0.4, 0.25], 0.5, 0.01)
    return finish(reverb(crack + hum * 0.6, 1.2, 0.3), -3.0)


def whale_heart_death():
    t = t_axis(3.2)
    rum = lowpass(band_noise(len(t), 25, 250), 250) * E(t, [(0, 0), (0.2, 1), (1.6, 0.6), (3.2, 0)])
    sink = rumble(t, 80, 28) * E(t, [(0, 0), (0.1, 0.9), (2.0, 0.4), (3.2, 0)])
    out = np.zeros_like(t)
    for t0 in np.sort(rng.uniform(0.0, 2.0, 12)):
        out += stone_knock(t, t0, rng.uniform(100, 320), 0.8 * (1 - t0 / 3), 1.2)
    glim = modal(t, 0.0, 1100, [1, 2.76, 5.4, 8.9], [1.0, 0.6, 0.4, 0.3], 0.1, 0.02)
    dust = lowpass(band_noise(len(t), 1000, 6000), 4000) * E(t, [(0, 0), (0.8, 0.2), (3.2, 0)])
    return finish(reverb(rum * 0.8 + sink * 0.7 + out + glim + dust * 0.2, 2.2, 0.4), -2.0)


# ---------------------------------------------------------------- 余燼の心臓 (熱を持つ玄武岩。死の音は EmberHeartSetup.DEATH があるので被弾だけ)

def ember_heart_hurt():
    t = t_axis(0.9)
    crack = stone_knock(t, 0.0, 230, 1.0, 0.9) + noise_hit(t, 0.0, 900, 7000, 0.04, 0.8)
    hiss = band_noise(len(t), 2500, 9000) * E(t, [(0, 0), (0.05, 0.6), (0.5, 0.2), (0.9, 0)]) * 0.3
    whoomp = lowpass(band_noise(len(t), 60, 500), 500) * E(t, [(0, 0), (0.05, 0.8), (0.4, 0.2), (0.9, 0)])
    return finish(reverb(crack + hiss + whoomp * 0.5 + crackle(t, 30, amp=0.4), 0.8, 0.2), -3.0)


# ---------------------------------------------------------------- 嵐の心臓 (雷を宿した金属と結晶)

def storm_heart_hurt():
    t = t_axis(0.8)
    zap = band_noise(len(t), 1500, 9000) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 180 * t))) * E(t, [(0, 0), (0.01, 1), (0.2, 0.3), (0.4, 0)])
    metal = modal(t, 0.0, 840, [1, 2.32, 4.1, 6.3], [0.3, 0.2, 0.12, 0.08], 0.7, 0.02)
    arc = crackle(t, 70, 2500, 10000, 0.5, span=(0.0, 0.35))
    return finish(reverb(zap * 0.6 + metal + arc, 0.7, 0.2), -3.0)


# ---------------------------------------------------------------- 潮殻の心臓 (水を湛えた珊瑚と水晶)

def tide_heart_hurt():
    t = t_axis(0.8)
    glass = modal(t, 0.0, 1650, [1, 2.4, 4.2, 6.5], [0.25, 0.15, 0.08, 0.05], 0.6, 0.02) + click(t, 0.0, 2000, 9000, 0.006)
    splash = lowpass(band_noise(len(t), 300, 4000), 4000) * E(t, [(0, 0), (0.02, 0.9), (0.25, 0.2), (0.5, 0)])
    return finish(reverb(glass * 0.6 + splash * 0.6 + bubbles(t, 6, 500, 1700, amp=0.2), 0.8, 0.25), -3.0)


# ---------------------------------------------------------------- 足音

def ash_thrall_step():
    t = t_axis(0.3)
    out = lowpass(band_noise(len(t), 300, 3500), 3500) * E(t, [(0, 0), (0.02, 1), (0.12, 0.3), (0.3, 0)])
    out += crackle(t, 40, 1500, 6000, 0.3, span=(0.02, 0.2)) + stone_knock(t, 0.0, 130, 0.35, 0.3)
    return finish(out, -6.0)


def coral_thrall_step():
    t = t_axis(0.25)
    out = modal(t, 0.0, 1700, [1, 2.2, 3.5], [0.02, 0.012, 0.008], 0.6, 0.05) + noise_hit(t, 0.0, 200, 1800, 0.07, 0.8)
    out += lowpass(band_noise(len(t), 150, 1200), 1200) * E(t, [(0, 0), (0.05, 0.7), (0.18, 0)]) * 0.5
    out += bubbles(t, 1, 400, 900, 0.04, 0.3)
    return finish(out, -6.0)


def feather_warden_step():
    t = t_axis(0.2)
    out = noise_hit(t, 0.0, 1200, 7000, 0.05, 0.6) + noise_hit(t, 0.03, 3000, 9000, 0.03, 0.4)
    out += lowpass(band_noise(len(t), 500, 4000), 4000) * E(t, [(0, 0), (0.04, 0.5), (0.15, 0)]) * 0.4
    return finish(out, -9.0)


def thunder_supplicant_step():
    t = t_axis(0.25)
    out = lowpass(band_noise(len(t), 100, 1500), 1500) * E(t, [(0, 0), (0.02, 1), (0.15, 0)]) * 0.7
    out += crackle(t, 70, 3000, 10000, 0.3, span=(0.0, 0.1)) * 0.5
    return finish(out, -9.0)


SOUNDS = {
    "bone_sentinel_ambient": bone_sentinel_ambient,
    "bone_sentinel_hurt_1": lambda: bone_sentinel_hurt(560),
    "bone_sentinel_hurt_2": lambda: bone_sentinel_hurt(700),
    "bone_sentinel_death": bone_sentinel_death,
    "bone_sentinel_step_1": lambda: bone_step(520),
    "bone_sentinel_step_2": lambda: bone_step(610),
    "bone_sentinel_guard": bone_guard,
    "rib_archer_ambient": rib_archer_ambient,
    "rib_archer_hurt_1": lambda: bone_sentinel_hurt(1000),
    "rib_archer_hurt_2": lambda: bone_sentinel_hurt(1200),
    "rib_archer_death": lambda: lowpass(bone_sentinel_death(), 9000),
    "rib_archer_step_1": lambda: bone_step(900, 0.6),
    "rib_archer_step_2": lambda: bone_step(1050, 0.6),
    "rib_archer_shoot": rib_archer_shoot,
    "moss_crawler_ambient": moss_crawler_ambient,
    "moss_crawler_hurt": moss_crawler_hurt,
    "moss_crawler_death": moss_crawler_death,
    "moss_crawler_step_1": moss_step,
    "moss_crawler_step_2": moss_step,
    "whalebone_captain_ambient": captain_ambient,
    "whalebone_captain_hurt_1": lambda: captain_hurt(300),
    "whalebone_captain_hurt_2": lambda: captain_hurt(380),
    "whalebone_captain_death": captain_death,
    "whalebone_captain_step_1": lambda: captain_step(95),
    "whalebone_captain_step_2": lambda: captain_step(110),
    "whalebone_captain_swing": captain_swing,
    "whalebone_captain_slam": captain_slam,
    "stone_whale_heart_ambient": whale_heart_ambient,
    "stone_whale_heart_hurt": whale_heart_hurt,
    "stone_whale_heart_death": whale_heart_death,
    "ember_heart_hurt": ember_heart_hurt,
    "storm_heart_hurt": storm_heart_hurt,
    "tide_heart_hurt": tide_heart_hurt,
    "ash_thrall_step_1": ash_thrall_step,
    "ash_thrall_step_2": ash_thrall_step,
    "coral_thrall_step_1": coral_thrall_step,
    "coral_thrall_step_2": coral_thrall_step,
    "feather_warden_step_1": feather_warden_step,
    "feather_warden_step_2": feather_warden_step,
    "thunder_supplicant_step_1": thunder_supplicant_step,
    "thunder_supplicant_step_2": thunder_supplicant_step,
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUNDS.items():
        data = np.nan_to_num(fn()).astype(np.float32)
        assert np.abs(data).max() > 0.05, name
        # 残響の尾の無音を切る (-40 dB 以下)
        idx = np.nonzero(np.abs(data) > 0.01 * np.abs(data).max())[0]
        data = data[:min(len(data), idx[-1] + int(0.08 * SR))].copy()
        data[-int(0.06 * SR):] *= np.linspace(1, 0, int(0.06 * SR))
        assert len(data) < SR * 4.5, name
        write_ogg(OUT / f"{name}.ogg", data)
        print(name, "%.2f s" % (len(data) / SR))


if __name__ == "__main__":
    main()
