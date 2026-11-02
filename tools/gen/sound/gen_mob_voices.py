"""敵の声 (環境音・被弾・倒れる音) を作る。種ごとに材質 (骨・影・水・灯など) と声を組み合わせる (独自合成。乱数の種は固定)。

使い方: python tools/gen/sound/gen_mob_voices.py   (リポジトリ直下から)
出力:   src/main/resources/assets/skyabyss/sounds/voices/<名前>.ogg
        同じ内容の行を sounds.json と lang (ja_jp / en_us) に足す。すでにある行は、中身が同じなら触らない (違えば止まる)。

音の長さ: 環境音 0.7〜1.2 秒、被弾 約 0.3 秒、倒れる 約 1.1 秒 (大きい主は 1.5 秒・低い)。
材質 (material): 骨・石・影・湿り・鐘・硝子・灰・熾り・静電気・塩・潮・羽・布・金属・殻・灯・木
声 (VOX): うなり・うめき・ささやき・さえずり・ハム・口笛。声のない種は材質だけで作る。

登録は各 Setup (MoonMobsSetup / CloudMobsSetup / AbyssMobsBSetup / AbyssMobsA / Ch4Setup / EmberMobSetup / EmberSmithSetup / MobSounds)。
表の引きは entity/MobVoices (AbyssMobB・AbyssMob の getAmbientSound / getHurtSound / getDeathSound)。
"""
import glob
import io
import json
from pathlib import Path

import numpy as np

import gen_sky_sounds as G
from gen_sky_sounds import SR, env, finish, reverb, t_axis, write_ogg
import gen_mob_sounds as M  # 骨・石・声などの部品 (import の時点で乱数の種が決まる)

ROOT = Path(__file__).resolve().parent.parent.parent.parent
ASSETS = ROOT / "src/main/resources/assets"
OUT = ASSETS / "skyabyss/sounds/voices"
rng = np.random.default_rng(20261101)
G.rng = rng  # band_noise が使う乱数 (この生成の種)


# ---------------------------------------------------------------- 部品

def _times(rate, a, b):
    """a〜b の間に、毎秒 rate 回の割合でばらまいた時刻"""
    n = rng.poisson(max(0.0, rate) * (b - a))
    return np.sort(rng.uniform(a, b, n))


def _hits(t, rate, fn, a=0.0, b=None):
    """ランダムな時刻に fn(t, t0) を足す"""
    b = t[-1] if b is None else b
    out = np.zeros_like(t)
    for t0 in _times(rate, a, b):
        out += fn(t, t0)
    return out


def _flutter(t, rate, lo, hi):
    """羽ばたき: 帯域ノイズを rate Hz で揺らす"""
    return M.highpass(M.lowpass(G.band_noise(len(t), lo, hi), hi), lo) * (0.5 + 0.5 * np.sin(2 * np.pi * rate * t)) ** 2


def _thump(t, t0, f, amp=1.0):
    """腹に響く一打ち"""
    k = np.clip(t - t0, 0, None)
    return M.rumble(t, f, f * 0.6) * np.exp(-k / 0.1) * (t >= t0) * amp


# ---------------------------------------------------------------- 材質 (kind: loop 環境 / hit 被弾 / dead 倒れる)

def material(kind, t, f, m, rate):
    T = t[-1]
    n = len(t)
    if m == "bone":
        if kind == "loop":
            return 0.5 * M.creak(t, 12, 26, 400, 2000) * env(t, [(0, 0), (0.3, 1), (T * 0.8, 0.5), (T, 0)]) \
                + _hits(t, rate * 0.5, lambda tt, t0: M.bone_knock(tt, t0, f * rng.uniform(0.92, 1.08), 0.35))
        if kind == "hit":
            return M.bone_knock(t, 0, f, 1.0) + M.noise_hit(t, 0, 2500, 8000, 0.02, 0.5)
        out = M.noise_hit(t, 0, 600, 5000, 0.3, 0.3)
        for i, t0 in enumerate(np.sort(rng.uniform(0, T * 0.75, 10))):
            out += M.bone_knock(t, t0, f * rng.uniform(0.9, 1.1), 0.7 * (1 - i / 14))
        return out + M.creak(t, 30, 10, 300, 1500) * env(t, [(0, 0), (0.05, 0.6), (T, 0)]) * 0.5

    if m == "stone":
        if kind == "loop":
            swell = 0.6 + 0.4 * np.sin(2 * np.pi * 0.8 * t)
            return M.lowpass(G.band_noise(n, 60, 300), 300) * swell * 0.6 \
                + _hits(t, rate * 0.4, lambda tt, t0: M.stone_knock(tt, t0, f, 0.3))
        if kind == "hit":
            return M.stone_knock(t, 0, f, 1.0) + M.noise_hit(t, 0, 400, 3000, 0.03, 0.5)
        out = M.lowpass(G.band_noise(n, 80, 400), 400) * env(t, [(0, 0), (0.05, 1), (T, 0)]) * 0.5
        for t0, a in zip((0.0, 0.15, 0.4, 0.7), (1.0, 0.7, 0.5, 0.3)):
            out += M.stone_knock(t, t0, f * rng.uniform(0.95, 1.05), a)
        return out + _thump(t, 0.0, f * 0.3, 0.5)

    if m == "shadow":
        if kind == "loop":
            body = M.lowpass(G.band_noise(n, 50, 380), 380) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.6 * t)) * 0.7
            breath = M.lowpass(G.band_noise(n, 800, 2400), 2400) * 0.12 * env(t, [(0, 0), (T * 0.5, 1), (T, 0)])
            return body + breath + np.sin(2 * np.pi * f * 0.5 * t) * 0.05 * env(t, [(0, 0), (0.3, 1), (T, 0)])
        if kind == "hit":
            return M.lowpass(G.band_noise(n, 80, 900), 900) * env(t, [(0, 1), (0.04, 0.8), (T, 0)]) + _thump(t, 0.0, f * 0.6, 0.6)
        return M.lowpass(G.band_noise(n, 60, 1200), 1200) * env(t, [(0, 0), (0.05, 1), (0.6, 0.4), (T, 0)]) \
            + M.rumble(t, f * 0.8, f * 0.2) * env(t, [(0, 0), (0.05, 0.7), (0.6, 0.3), (T, 0)]) * 0.5

    if m == "wet":
        squelch = M.lowpass(G.band_noise(n, 100, 1500), 1500)
        if kind == "loop":
            return squelch * (0.5 + 0.5 * np.sin(2 * np.pi * 7 * t)) ** 2 * 0.4 * env(t, [(0, 0), (0.2, 1), (T, 0.4)]) \
                + M.bubbles(t, int(rate * T) + 1, f * 2, f * 5, 0.05, 0.4)
        if kind == "hit":
            return squelch * env(t, [(0, 1), (0.1, 0.6), (0.3, 0), (T, 0)]) + M.noise_hit(t, 0, 800, 4000, 0.04, 0.7)
        return squelch * env(t, [(0, 0), (0.05, 1), (0.6, 0.4), (T, 0)]) * 0.8 + M.bubbles(t, 8, f * 2, f * 4, 0.06, 0.6, (0, T * 0.8)) \
            + M.rumble(t, f, f * 0.5) * env(t, [(0, 0), (0.1, 0.5), (T, 0)]) * 0.4

    if m == "bell":
        if kind == "loop":
            return _hits(t, rate, lambda tt, t0: M.modal(tt, t0, f * rng.uniform(0.98, 1.02), [1, 2.0, 2.4, 3.0, 4.1],
                                                          [0.9, 0.5, 0.3, 0.25, 0.1], 0.25))
        if kind == "hit":
            return M.modal(t, 0, f, [1, 2.0, 2.4, 3.0], [0.5, 0.3, 0.2, 0.1], 1.0) + M.click(t, 0, 1200, 5000, 0.005, 0.5)
        return M.modal(t, 0, f, [1, 2.0, 2.4, 3.0, 4.1], [1.2, 0.7, 0.4, 0.3, 0.15], 1.0) \
            + M.modal(t, 0.4, f * 0.97, [1, 2.0, 2.4], [0.8, 0.4, 0.2], 0.5)

    if m == "glass":
        if kind == "loop":
            return _hits(t, rate, lambda tt, t0: M.modal(tt, t0, f * rng.uniform(0.95, 1.05), [1, 2.32, 4.25, 6.63],
                                                          [0.25, 0.12, 0.05, 0.03], 0.3))
        if kind == "hit":
            return M.modal(t, 0, f, [1, 2.32, 4.25], [0.12, 0.06, 0.03], 0.9) + M.noise_hit(t, 0, 3000, 9000, 0.01, 0.6)
        out = M.noise_hit(t, 0, 2000, 9000, 0.12, 0.3)
        for t0 in np.sort(rng.uniform(0, T * 0.9, 12)):
            out += M.modal(t, t0, f * rng.uniform(0.8, 1.3), [1, 2.32], [0.2, 0.08], 0.35)
        return out

    if m == "ember":
        if kind == "loop":
            roar = M.lowpass(G.band_noise(n, 80, 500), 500) * (0.5 + 0.5 * np.sin(2 * np.pi * 1.7 * t)) * 0.5
            return M.crackle(t, rate * 4, 1500, 6000, 0.6) + roar
        if kind == "hit":
            return M.crackle(t, 60, 1500, 7000, 0.8, (0, 0.1)) \
                + M.lowpass(G.band_noise(n, 150, 1200), 1200) * env(t, [(0, 1), (0.2, 0), (T, 0)]) * 0.5
        return M.crackle(t, 25, 1500, 6000, 0.6) * env(t, [(0, 1), (T, 0.2)]) \
            + M.lowpass(G.band_noise(n, 120, 900), 900) * env(t, [(0, 0), (0.05, 1), (T, 0)]) * 0.6 \
            + M.rumble(t, f, f * 0.5) * env(t, [(0, 0), (0.05, 1), (0.5, 0.3), (T, 0)]) * 0.4

    if m == "static":
        if kind == "loop":
            return M.crackle(t, rate * 6, 3000, 10000, 0.5) + M.lowpass(G.band_noise(n, 1500, 5000), 5000) * 0.08
        if kind == "hit":
            return M.crackle(t, 80, 3000, 10000, 0.6, (0, 0.08)) + M.modal(t, 0, f * 2.5, [1, 1.7], [0.05, 0.03], 0.3)
        return M.crackle(t, 40, 2000, 9000, 0.6, (0, T * 0.8)) + M.modal(t, 0.1, f * 2.5, [1, 1.6], [0.25, 0.1], 0.3) \
            + _thump(t, 0.0, f * 0.5, 0.5)

    if m == "salt":
        if kind == "loop":
            return M.crackle(t, rate * 10, 2000, 7000, 0.5) + M.lowpass(G.band_noise(n, 1500, 6000), 6000) * 0.05
        if kind == "hit":
            return M.noise_hit(t, 0, 2000, 8000, 0.05, 0.8) + M.click(t, 0, 2000, 8000, 0.004, 0.5)
        return M.crackle(t, 60, 1500, 7000, 0.6, (0, T * 0.8)) * env(t, [(0, 1), (T, 0)]) \
            + M.lowpass(G.band_noise(n, 80, 300), 300) * env(t, [(0, 0), (0.05, 1), (T, 0)]) * 0.5

    if m == "tide":
        if kind == "loop":
            return M.bubbles(t, int(rate * T) + 1, f * 2, f * 5, 0.05, 0.5) \
                + M.lowpass(G.band_noise(n, 80, 300), 300) * 0.4 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.5 * t))
        if kind == "hit":
            return M.noise_hit(t, 0, 400, 3000, 0.12, 0.7) + M.bubbles(t, 2, f * 3, f * 6, 0.04, 0.5, (0, 0.1))
        return M.noise_hit(t, 0, 300, 2500, 0.5, 0.6) * env(t, [(0, 1), (T, 0)]) \
            + M.bubbles(t, 10, f * 2, f * 5, 0.05, 0.5, (0, T * 0.8))

    if m == "wing":
        if kind == "loop":
            return _flutter(t, 28, 1000, 4000) * 0.4 + M.lowpass(G.band_noise(n, 300, 900), 900) * 0.1
        if kind == "hit":
            return G.band_noise(n, 800, 4000) * env(t, [(0, 1), (0.06, 0.4), (T, 0)]) * 0.6 + _flutter(t, 45, 800, 4000) * 0.3
        sweep = 28 * (1 - t / T) + 3
        return M.highpass(M.lowpass(G.band_noise(n, 600, 3000), 3000), 600) \
            * (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(sweep) / SR)) * env(t, [(0, 1), (T, 0)]) * 0.6

    if m == "cloth":
        if kind == "loop":
            return _hits(t, rate * 2, lambda tt, t0: M.noise_hit(tt, t0, 1000, 4000, 0.03, 0.6)) \
                + M.lowpass(G.band_noise(n, 200, 800), 800) * 0.05
        if kind == "hit":
            return M.noise_hit(t, 0, 700, 3000, 0.08, 0.8) + _thump(t, 0.0, f * 0.5, 0.4)
        return M.noise_hit(t, 0, 400, 2500, 0.5, 0.6) + _thump(t, 0.0, f, 0.5)

    if m == "metal":
        if kind == "loop":
            return _hits(t, rate, lambda tt, t0: M.modal(tt, t0, f, [1, 2.76, 5.4, 8.9], [0.4, 0.2, 0.1, 0.05], 0.2)) \
                + M.lowpass(G.band_noise(n, 100, 600), 600) * 0.2
        if kind == "hit":
            return M.modal(t, 0, f, [1, 2.76, 5.4], [0.15, 0.07, 0.03], 0.9) + M.noise_hit(t, 0, 1500, 6000, 0.02, 0.4)
        return M.modal(t, 0, f, [1, 2.76, 5.4, 8.9], [0.8, 0.4, 0.2, 0.1], 0.9) \
            + M.modal(t, 0.25, f * 0.7, [1, 2.76, 5.4], [0.5, 0.2, 0.1], 0.5) \
            + M.highpass(M.lowpass(G.band_noise(n, 500, 3000), 3000), 500) * env(t, [(0, 0), (0.05, 0.8), (T, 0)]) * 0.4

    if m == "shell":
        if kind == "loop":
            return _hits(t, rate, lambda tt, t0: M.click(tt, t0, 1500, 6000, 0.006, 0.4)) \
                + M.lowpass(G.band_noise(n, 120, 500), 500) * 0.3 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.7 * t))
        if kind == "hit":
            return M.modal(t, 0, f, [1, 1.5, 2.2], [0.04, 0.03, 0.02], 1.0) + M.click(t, 0, 1500, 6000, 0.006, 0.8)
        out = M.modal(t, 0, f * 0.8, [1, 1.5], [0.3, 0.2], 0.6) \
            + M.lowpass(G.band_noise(n, 120, 500), 500) * env(t, [(0, 0.5), (T, 0)]) * 0.4
        for t0 in np.sort(rng.uniform(0, T * 0.6, 6)):
            out += M.click(t, t0, 1500, 6000, 0.006, 0.7)
        return out

    if m == "flame":
        flicker = 0.6 + 0.4 * np.sin(2 * np.pi * 6 * t + 2 * M.lowpass(G.band_noise(n, 2, 8), 8))
        if kind == "loop":
            return M.lowpass(G.band_noise(n, 300, 3000), 3000) * flicker * 0.3
        if kind == "hit":
            return M.noise_hit(t, 0, 1000, 5000, 0.2, 0.8) + M.lowpass(G.band_noise(n, 300, 3000), 3000) * flicker * 0.2
        return M.noise_hit(t, 0, 300, 3000, T * 0.8, 0.5) * env(t, [(0, 1), (T, 0)]) \
            + np.sin(2 * np.pi * f * 0.5 * t) * 0.1 * env(t, [(0, 1), (T, 0)])

    if m == "wood":
        if kind == "loop":
            return M.creak(t, 12, 20, 300, 1400) * 0.6 \
                + _hits(t, rate * 0.4, lambda tt, t0: M.modal(tt, t0, f, [1, 2.1, 3.3], [0.05, 0.03, 0.02], 0.3))
        if kind == "hit":
            return M.modal(t, 0, f, [1, 2.1, 3.3], [0.06, 0.03, 0.02], 1.0) + M.noise_hit(t, 0, 1200, 5000, 0.02, 0.6)
        return M.noise_hit(t, 0, 800, 4000, 0.1, 0.7) + M.modal(t, 0.05, f * 0.7, [1, 2.1], [0.2, 0.1], 0.6) \
            + M.creak(t, 25, 6, 200, 1200) * env(t, [(0, 0.5), (T, 0)]) * 0.5

    if m == "ash":
        if kind == "loop":
            return M.crackle(t, rate * 8, 300, 2000, 0.4) \
                + M.lowpass(G.band_noise(n, 200, 1200), 1200) * 0.12 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.5 * t))
        if kind == "hit":
            return M.lowpass(G.band_noise(n, 400, 3000), 3000) * env(t, [(0, 1), (0.2, 0), (T, 0)]) * 0.8
        return M.crackle(t, 40, 300, 2500, 0.5, (0, T * 0.8)) * env(t, [(0, 1), (T, 0)]) \
            + M.lowpass(G.band_noise(n, 200, 1500), 1500) * env(t, [(0, 0), (0.05, 0.8), (T, 0)]) * 0.5

    raise ValueError("material " + m)


# ---------------------------------------------------------------- 声 (formant の声帯)

VOX = {
    "growl": dict(form=[(320, 130, 1.0), (950, 220, 0.45)], rough=0.04, breath=0.05, nh=24),
    "moan": dict(form=[(480, 160, 1.0), (1150, 260, 0.5)], rough=0.02, breath=0.12, nh=24),
    "whisper": dict(form=[(1300, 700, 0.5), (2600, 900, 0.35)], rough=0.0, breath=0.7, nh=12),
    "chirp": dict(form=[(1800, 400, 1.0), (3200, 500, 0.5)], rough=0.01, breath=0.03, nh=16),
    "hum": dict(form=[(260, 90, 1.0)], rough=0.0, breath=0.02, nh=8),
}


def voice(kind, t, f, v):
    T = t[-1]
    n = len(t)
    if v == "whistle":
        fr = f * (1 + 0.02 * np.sin(2 * np.pi * 5 * t))
        ph = 2 * np.pi * np.cumsum(fr) / SR
        tone = np.sin(ph) + 0.3 * np.sin(2 * ph)
        if kind == "loop":
            return tone * 0.25 * (0.5 + 0.5 * np.sin(2 * np.pi * 1.1 * t)) ** 2
        return tone * env(t, [(0, 0), (0.03, 1), (T, 0)]) * 0.5
    c = VOX[v]
    if kind == "loop":
        x = M.voice(t, f, c["form"], c["rough"], None, c["breath"], c["nh"])
        return x * (0.5 + 0.5 * np.sin(2 * np.pi * 2.3 * t)) ** 2 * 0.5
    if kind == "hit":
        x = M.voice(t, np.linspace(f * 1.1, f * 0.8, n), c["form"], c["rough"], None, c["breath"], c["nh"])
        return x * env(t, [(0, 0), (0.02, 1), (T * 0.5, 0.6), (T, 0)])
    x = M.voice(t, np.linspace(f, f * 0.5, n), c["form"], c["rough"], None, c["breath"], c["nh"])
    return x * env(t, [(0, 0), (0.05, 1), (T * 0.6, 0.5), (T, 0)])


# ---------------------------------------------------------------- 組み立て

def build(kind, s):
    """kind: ambient / hurt / death。s は種の表の一行"""
    boss = s.get("boss", False)
    f = s["f"]
    rate = s.get("rate", 2.0)
    if kind == "ambient":
        L = s.get("amb", 0.9)
    elif kind == "hurt":
        L = 0.32
    else:
        L = 1.5 if boss else 1.1
    t = t_axis(L)
    x = material(kind, t, f, s["mat"], rate)
    if s.get("m2"):
        x = x + 0.7 * material(kind, t, s["f2"], s["m2"], rate)
    if s.get("voice"):
        x = x + {"ambient": 0.45, "hurt": 0.6, "death": 0.7}[kind] * voice(kind, t, s["vf"], s["voice"])
    if boss:
        sub = M.rumble(t, f * 0.5, f * 0.3)
        if kind == "ambient":
            x = x + 0.3 * sub
        else:
            x = x + 0.5 * sub * env(t, [(0, 0), (0.05, 1), (L, 0)])
    if s.get("beat") and kind == "ambient":
        for k in range(int(L)):
            x = x + _thump(t, k + 0.05, 60, 0.7) + _thump(t, k + 0.3, 50, 0.5)
    if kind == "ambient":
        x = x * env(t, [(0, 0), (0.12, 1), (L - 0.25, 0.8), (L, 0)])
    wet = {"ambient": (0.3, 0.15), "hurt": (0.2, 0.06), "death": (0.4, 0.15)}[kind]
    x = reverb(x, wet[0], wet[1])
    peak = {"ambient": -9.0, "hurt": -3.0, "death": -3.5}[kind]
    return finish(x, peak)


# 種の表。ns は音の名前空間、id は種の名前 (lang の entity.skyabyss.<id>)、mat / f は主の材質と高さ (Hz)
# m2 / f2 は二つ目の材質、voice / vf は声とその高さ、rate は環境音の出来事の数 (毎秒)、parts は作る音 (a 環境 h 被弾 d 倒れる)
# event / file は登録名と .ogg の名前 (省くと <id>_<kind>)。boss は主 (低く、大きく)。beat は鼓動
MOON = "skyabyss_moon"
FINALE = "skyabyss_finale"
ABYSS_B = "skyabyss_abyss_b"
ABYSS_A = "skyabyss_abyss_a"
CH4 = "skyabyss_ch4"
MAIN = "skyabyss"

SPEC = [
    # ---- 月の敵 (区画 1〜4)
    dict(ns=MOON, id="dust_crawler", mat="salt", f=300, rate=6),
    dict(ns=MOON, id="shell_hare", mat="shell", f=900, rate=2, voice="chirp", vf=760),
    dict(ns=MOON, id="moon_leaper", mat="stone", f=240, rate=2),
    dict(ns=MOON, id="cliff_creeper", mat="stone", f=170, rate=3),
    dict(ns=MOON, id="push_stone", mat="metal", f=380, rate=2, m2="glass", f2=760),
    dict(ns=MOON, id="shadow_stitcher", mat="shadow", f=120, rate=1, voice="whisper", vf=220),
    dict(ns=MOON, id="shadow_embracer", mat="shadow", f=90, rate=0.5, voice="moan", vf=150, boss=True),
    dict(ns=MOON, id="light_swarm", mat="glass", f=1600, rate=10),
    dict(ns=MOON, id="shade_strider", mat="glass", f=560, rate=1, m2="shadow", f2=180),
    dict(ns=MOON, id="mirror_bearer", mat="glass", f=1100, rate=3, m2="metal", f2=500),
    dict(ns=MOON, id="full_keeper", mat="bell", f=330, rate=0.5, voice="moan", vf=180, boss=True),
    dict(ns=MOON, id="rift_wing", mat="wing", f=600, rate=0),
    dict(ns=MOON, id="rift_armor", mat="metal", f=240, rate=1, m2="salt", f2=400),
    dict(ns=MOON, id="rift_serpent", mat="salt", f=200, rate=4, voice="whisper", vf=260),
    dict(ns=MOON, id="moon_scale", mat="metal", f=130, rate=1, m2="stone", f2=90, voice="growl", vf=95, boss=True),
    dict(ns=MOON, id="waning_blade", mat="metal", f=600, rate=1, voice="growl", vf=150, boss=True),
    dict(ns=MOON, id="moon_core", mat="bell", f=220, rate=0.5, m2="glass", f2=900, boss=True),
    # ---- 雲海の敵 (鐘の島・灯の島・炉の島・約束の島・座)
    dict(ns=FINALE, id="bell_mourner", mat="bell", f=420, rate=0.8, voice="moan", vf=115),
    dict(ns=FINALE, id="mist_bird", mat="wing", f=800, rate=2, voice="chirp", vf=1100),
    dict(ns=FINALE, id="last_bellkeeper", mat="bell", f=170, rate=0.4, voice="moan", vf=80, boss=True),
    dict(ns=FINALE, id="net_caster", mat="cloth", f=180, rate=2, voice="moan", vf=150),
    dict(ns=FINALE, id="lamp_moth", mat="wing", f=1200, rate=3, m2="flame", f2=600),
    dict(ns=FINALE, id="lamp_eye", mat="flame", f=260, rate=1, voice="hum", vf=180),
    dict(ns=FINALE, id="ash_striker", mat="ash", f=130, rate=2, m2="metal", f2=300, voice="growl", vf=120),
    dict(ns=FINALE, id="backfire", mat="ember", f=240, rate=4, voice="whisper", vf=260),
    dict(ns=FINALE, id="rewinder", mat="metal", f=280, rate=2, m2="wood", f2=420, voice="growl", vf=105, boss=True),
    dict(ns=FINALE, id="fallen_plume", mat="wing", f=500, rate=2),
    dict(ns=FINALE, id="plume_warden", mat="metal", f=420, rate=1.5, voice="growl", vf=150),
    dict(ns=FINALE, id="pilgrim_plume", mat="wing", f=150, rate=1, m2="cloth", f2=200, boss=True),
    dict(ns=FINALE, id="pilgrim_hand", mat="cloth", f=260, rate=1.5, m2="glass", f2=1400),
    dict(ns=FINALE, id="pilgrim_eye", mat="flame", f=150, rate=0.5, voice="hum", vf=110, boss=True),
    dict(ns=FINALE, id="pilgrim_moon", mat="bell", f=200, rate=0.6, m2="metal", f2=330, boss=True),
    dict(ns=FINALE, id="founder", mat="bell", f=250, rate=0.5, voice="moan", vf=135, boss=True),
    # ---- 深淵の B 組 (灰の層・深み・深淵だけ)
    dict(ns=ABYSS_B, id="ash_trail", mat="ash", f=200, rate=6),
    dict(ns=ABYSS_B, id="ceiling_walker", mat="cloth", f=200, rate=2, voice="whisper", vf=240),
    dict(ns=ABYSS_B, id="ember_keeper", mat="ember", f=160, rate=2, voice="moan", vf=170),
    dict(ns=ABYSS_B, id="heartbeat_mimic", mat="wet", f=70, rate=0.5, beat=True),
    dict(ns=ABYSS_B, id="lamp_eater", mat="flame", f=300, rate=2),
    dict(ns=ABYSS_B, id="nameless_shape", mat="shell", f=360, rate=1, voice="whisper", vf=280),
    dict(ns=ABYSS_B, id="past_shadow", mat="shadow", f=150, rate=1, voice="whisper", vf=200),
    dict(ns=ABYSS_B, id="rope_hanger", mat="wood", f=300, rate=1.5),
    dict(ns=ABYSS_B, id="soot_child", mat="ash", f=500, rate=4, voice="chirp", vf=620),
    # ---- 深淵の A 組 (骨・塩・雷)
    dict(ns=ABYSS_A, id="bone_picker", mat="bone", f=700, rate=4),
    dict(ns=ABYSS_A, id="rib_guard", mat="bone", f=340, rate=1, m2="metal", f2=500),
    dict(ns=ABYSS_A, id="bone_flute", mat="bone", f=620, rate=1.5, voice="whistle", vf=620),
    dict(ns=ABYSS_A, id="salt_husk", mat="salt", f=260, rate=3, m2="bone", f2=400),
    dict(ns=ABYSS_A, id="tide_hands", mat="tide", f=200, rate=2),
    dict(ns=ABYSS_A, id="white_shell_keeper", mat="shell", f=280, rate=1.5),
    dict(ns=ABYSS_A, id="charged_moth", mat="static", f=1000, rate=6, m2="wing", f2=1600),
    dict(ns=ABYSS_A, id="lightning_idol", mat="stone", f=180, rate=1.5, m2="static", f2=3000),
    dict(ns=ABYSS_A, id="scorched_bird", mat="ember", f=480, rate=2, m2="wing", f2=900, voice="chirp", vf=420),
    # ---- 章四の地上の敵 (灰ひきずり・熾り蟲) と、余燼の背の敵 (燠虫・灰の鍛え手・火の粉蛾)
    dict(ns=CH4, id="ash_dragger", mat="ash", f=200, rate=4, m2="cloth", f2=200),
    dict(ns=CH4, id="cinder_mite", mat="ember", f=900, parts="h", m2="shell", f2=1200),
    dict(ns=MAIN, id="cinder_roller", event="cinder_roller_ambient", file="cinder_roller_ambient", mat="shell", f=150,
         rate=3, m2="stone", f2=90, parts="a"),
    dict(ns=MAIN, id="ash_smith", event="ash_smith_ambient", file="ash_smith_ambient", mat="ember", f=105, m2="metal", f2=220,
         voice="growl", vf=95, boss=True, parts="a"),
    dict(ns=MAIN, id="ember_moth", event="ember_moth_hurt", file="ember_moth_hurt", mat="ember", f=1300, parts="h"),
    # ---- 心臓 (三つ。石鯨の心臓は既存の STONE_WHALE_HEART_DEATH を使う)
    dict(ns=MAIN, id="ember_heart", event="ember_heart_voice_death", file="ember_heart_death", mat="ember", f=70, m2="stone", f2=110,
         boss=True, parts="d", sub=("心臓が止まり、殻がくずれる声", "The heart stops and the shell gives way")),
    dict(ns=MAIN, id="storm_heart", event="storm_heart_voice_death", file="storm_heart_death", mat="static", f=90, m2="bell", f2=260,
         boss=True, parts="d", sub=("心臓が止まり、風がやむ声", "The heart stops and the wind falls still")),
    dict(ns=MAIN, id="tide_heart", event="tide_heart_voice_death", file="tide_heart_death", mat="tide", f=110, m2="bell", f2=330,
         boss=True, parts="d", sub=("心臓が止まり、潮が引く声", "The heart stops and the tide goes out")),
]

KINDS = {"a": "ambient", "h": "hurt", "d": "death"}
SUB_JA = {"ambient": "%sの気配", "hurt": "%sがひるむ", "death": "%sが崩れる"}
SUB_EN = {"ambient": "%s stirs", "hurt": "%s recoils", "death": "%s collapses"}


def lang_names():
    """entity.skyabyss.<id> の表示名 (全部の名前空間の lang から)"""
    names = {"ja_jp": {}, "en_us": {}}
    for loc in names:
        for p in sorted(glob.glob(str(ASSETS / ("*/lang/%s.json" % loc)))):
            with io.open(p, encoding="utf-8") as fh:
                for k, v in json.load(fh).items():
                    if k.startswith("entity.skyabyss."):
                        names[loc].setdefault(k[len("entity.skyabyss."):], v)
    return names


def merge_json(path, rows, create=False):
    """rows の行を、なければ末尾に足す。あって中身が違えばエラー (他の作業の行を壊さない)"""
    raw = None
    data = {}
    if path.exists():
        with io.open(path, encoding="utf-8") as fh:
            raw = fh.read()
        data = json.loads(raw)
    elif not create:
        raise SystemExit("無い sounds.json / lang: " + str(path))
    for k, v in rows.items():
        if k in data:
            if data[k] != v:
                raise SystemExit("行が食い違う: %s %s" % (path, k))
            continue
        data[k] = v
    text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    if text != raw:
        path.parent.mkdir(parents=True, exist_ok=True)
        with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)


def main():
    names = lang_names()
    OUT.mkdir(parents=True, exist_ok=True)
    events = {}  # ns -> {event: row}
    langs = {}   # ns -> {loc: {key: text}}
    for s in SPEC:
        ns = s["ns"]
        for letter in s.get("parts", "ahd"):
            kind = KINDS[letter]
            event = s.get("event") or "%s_%s" % (s["id"], kind)
            file_name = s.get("file") or event
            data = np.nan_to_num(build(kind, s)).astype(np.float32)
            assert np.abs(data).max() > 0.05, event
            idx = np.nonzero(np.abs(data) > 0.01 * np.abs(data).max())[0]
            data = data[:min(len(data), idx[-1] + int(0.08 * SR))].copy()
            data[-int(0.06 * SR):] *= np.linspace(1, 0, int(0.06 * SR))
            assert len(data) / SR < 2.0, event
            write_ogg(OUT / (file_name + ".ogg"), data)
            print("%-44s %-30s %.2f s" % (ns + ":" + event, file_name, len(data) / SR))
            if "sub" in s and kind == "death":
                ja, en = s["sub"]
            else:
                ja = SUB_JA[kind] % names["ja_jp"].get(s["id"], s["id"])
                en = SUB_EN[kind] % names["en_us"].get(s["id"], s["id"])
            key = "subtitles.%s.%s" % (ns, event)
            events.setdefault(ns, {})[event] = {"subtitle": key, "sounds": [{"name": "skyabyss:voices/" + file_name}]}
            langs.setdefault(ns, {"ja_jp": {}, "en_us": {}})
            langs[ns]["ja_jp"][key] = ja
            langs[ns]["en_us"][key] = en
    for ns, rows in events.items():
        base = ASSETS / ns
        merge_json(base / "sounds.json", rows, create=(ns == ABYSS_A))
        for loc in ("ja_jp", "en_us"):
            merge_json(base / "lang" / (loc + ".json"), langs[ns][loc], create=(ns == ABYSS_A))
    print("files:", sum(len(v) for v in events.values()))


if __name__ == "__main__":
    main()
