"""岩鯨の心臓と鯨骨の番兵長 (石鎧の将) の技の音を、材質に合わせて作る (独自合成。乱数の種は固定)。

使い方: python tools/gen/sound/gen_boss_skill_sounds.py
出力: src/main/resources/assets/skyabyss/sounds/mobs/stone_heart_*.ogg と captain_*.ogg
      sounds.json の同名の行 (自分の行だけ書き直す) と、skyabyss_mobsounds の字幕 (ja_jp / en_us)。
      登録は registry/MobSounds (BossSkillSounds ではなく MobSounds に足してある)。

材質:
- 石の心臓: 深い石の共鳴 (低い倍音の和)、重なった板の軋み、鎖、骨の棘が割れる音、核の共鳴
- 番兵長: 鯨の骨 (太く低い骨の打撃と軋み) と、石の鎧のぶつかり。バニラの音は使わない
部品は tools/gen/sound/gen_mob_sounds.py のものを使う。
"""
import json
from pathlib import Path

import numpy as np

import gen_mob_sounds as GM
import gen_sky_sounds as G
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg

ROOT = Path(__file__).resolve().parent.parent.parent.parent
RES = ROOT / "src/main/resources/assets/skyabyss"
OUT = RES / "sounds/mobs"
rng = np.random.default_rng(20261031)
G.rng = rng
GM.rng = rng

lowpass, highpass, put = GM.lowpass, GM.highpass, GM.put
modal, click, bone_knock, stone_knock = GM.modal, GM.click, GM.bone_knock, GM.stone_knock
noise_hit, creak, voice, crackle, rumble, bubbles = GM.noise_hit, GM.creak, GM.voice, GM.crackle, GM.rumble, GM.bubbles
E = env


def clink(t, t0, base, amp=1.0):
    """鉄の鎖の輪が当たる、短い金属の音"""
    return modal(t, t0, base, [1, 2.76, 5.4, 8.9], [0.05, 0.03, 0.02, 0.012], amp, 0.03) + click(t, t0, 3000, 9000, 0.004, amp * 0.4)


def sweep_noise(t, f0, f1, lo_ratio=0.5, hi_ratio=2.0, amp=1.0):
    """中心周波数が動く風切りのような帯域ノイズ (時間で分けて作る)"""
    n = len(t)
    out = np.zeros(n)
    steps = 12
    for i in range(steps):
        a, b = i * n // steps, (i + 1) * n // steps
        fc = np.interp((a + b) / 2 / SR, [0, t[-1]], [f0, f1])
        seg = band_noise(n, fc * lo_ratio, fc * hi_ratio)
        w = np.zeros(n)
        w[a:b] = np.hanning(b - a)
        out += seg * w
    return out * amp


def sub_boom(t, f0=70, f1=28, decay=0.6, amp=1.0, t0=0.0):
    k = np.clip(t - t0, 0, None)
    f = f0 + (f1 - f0) * (1 - np.exp(-k / 0.25))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-k / decay) * (t >= t0) * amp


# ================================================================ 岩鯨の心臓

def heart_roar():
    t = t_axis(2.8)
    f0 = np.interp(t, [0, 0.4, 1.4, 2.8], [34, 52, 46, 30])
    v = voice(t, f0, [(120, 60, 1.0), (260, 90, 0.6), (520, 140, 0.25)], rough=0.05, breath=0.05, vib=(2.5, 0.02))
    v = lowpass(v, 700) * E(t, [(0, 0), (0.3, 0.8), (1.6, 1.0), (2.8, 0)])
    grind = creak(t, 6, 16, 90, 700) * E(t, [(0, 0), (0.5, 0.8), (1.6, 0.7), (2.8, 0)])
    plates = np.zeros_like(t)
    for t0 in (0.05, 0.3, 0.62, 1.05):
        plates += stone_knock(t, t0, rng.uniform(70, 130), 0.7, 1.6)
    swell = lowpass(band_noise(len(t), 30, 280), 280) * E(t, [(0, 0), (0.4, 0.8), (1.6, 1.0), (2.8, 0)])
    res = modal(t, 0.0, 88, [1, 1.5, 2.0, 3.0], [1.4, 1.1, 0.9, 0.6], 0.25, 0.01) * E(t, [(0, 0), (0.3, 1), (2.8, 0.2)])
    return finish(reverb(v * 0.8 + grind * 0.7 + plates + swell * 0.8 + res, 2.2, 0.35), -1.5)


def heart_shockwave():
    t = t_axis(1.7)
    boom = sub_boom(t, 78, 26, 0.55, 1.2)
    crack = stone_knock(t, 0.0, 140, 1.0, 1.4) + stone_knock(t, 0.03, 90, 0.8, 1.4) + noise_hit(t, 0.0, 700, 7000, 0.05, 0.9)
    ring = sweep_noise(t, 900, 140, 0.4, 2.0) * E(t, [(0, 0), (0.05, 0.8), (0.9, 0.3), (1.6, 0)]) * 0.5
    debris = lowpass(band_noise(len(t), 600, 6000), 4500) * E(t, [(0, 0.6), (0.3, 0.35), (1.3, 0)]) * 0.35
    for t0 in rng.uniform(0.1, 0.9, 6):
        debris += stone_knock(t, t0, rng.uniform(200, 500), 0.15, 0.4)
    return finish(reverb(boom + crack + ring + debris, 1.6, 0.3), -1.5)


def heart_phase():
    t = t_axis(2.6)
    out = np.zeros_like(t)
    for i, t0 in enumerate(np.linspace(0.0, 0.8, 8)):
        out += stone_knock(t, t0, 110 * (1.12 ** i), 0.55, 0.9)
    chord = modal(t, 0.7, 110, [1, 1.5, 2.0, 2.51, 3.0], [1.8, 1.5, 1.3, 1.0, 0.8], 0.45, 0.005)
    chord += modal(t, 0.9, 55, [1, 2.0], [2.0, 1.4], 0.4, 0.005)
    glim = modal(t, 0.75, 1760, [1, 2.76, 4.1], [0.9, 0.5, 0.3], 0.07, 0.01)
    sw = sub_boom(t, 60, 40, 1.0, 0.7, 0.7)
    return finish(reverb(out + chord + glim + sw, 2.2, 0.4), -2.0)


def heart_grind():
    t = t_axis(1.3)
    g = creak(t, 8, 22, 80, 800) * E(t, [(0, 0), (0.5, 1.0), (1.1, 0.6), (1.3, 0)])
    rum = lowpass(band_noise(len(t), 30, 200), 200) * E(t, [(0, 0), (0.6, 0.9), (1.3, 0)])
    sc = highpass(band_noise(len(t), 400, 2200), 400) * E(t, [(0, 0), (0.7, 0.35), (1.3, 0)]) * (0.5 + 0.5 * np.sin(2 * np.pi * 11 * t)) ** 2
    return finish(reverb(g * 0.9 + rum * 0.8 + sc * 0.4, 1.0, 0.25), -3.0)


def heart_slam():
    t = t_axis(1.7)
    boom = sub_boom(t, 90, 30, 0.5, 1.0)
    hit = stone_knock(t, 0.0, 100, 1.2, 1.5) + stone_knock(t, 0.0, 230, 0.7, 1.2) + noise_hit(t, 0.0, 500, 6000, 0.03, 0.8)
    ring = modal(t, 0.0, 190, [1, 2.4, 4.3], [0.5, 0.3, 0.2], 0.25, 0.02)
    debris = lowpass(band_noise(len(t), 600, 5000), 4000) * E(t, [(0, 0.6), (0.3, 0.3), (1.4, 0)]) * 0.3
    return finish(reverb(boom * 0.9 + hit + ring + debris, 1.5, 0.3), -1.5)


def heart_plate_lock():
    t = t_axis(0.8)
    out = stone_knock(t, 0.0, 200, 1.0, 0.8) + modal(t, 0.0, 420, [1, 2.32, 4.0, 5.9], [0.18, 0.1, 0.06, 0.04], 0.6, 0.02) + click(t, 0.0, 800, 5000, 0.006)
    out += sub_boom(t, 120, 50, 0.2, 0.5)
    return finish(reverb(out, 0.8, 0.22), -2.5)


def heart_spike_charge():
    t = t_axis(1.2)
    out = np.zeros_like(t)
    for i, t0 in enumerate(np.sort(rng.uniform(0.0, 1.0, 14) ** 0.8)):
        out += bone_knock(t, t0, rng.uniform(300, 700), 0.4 + 0.5 * t0, 0.9)
    sc = creak(t, 10, 35, 300, 2000) * E(t, [(0, 0), (0.7, 0.8), (1.2, 0)])
    rum = lowpass(band_noise(len(t), 40, 220), 220) * E(t, [(0, 0), (0.9, 0.6), (1.2, 0)])
    return finish(reverb(out + sc * 0.6 + rum * 0.6, 0.9, 0.25), -3.0)


def heart_spike_burst():
    t = t_axis(0.9)
    out = stone_knock(t, 0.0, 130, 1.0, 1.0) + click(t, 0.0, 600, 6000, 0.012, 1.0)
    for t0 in rng.uniform(0.0, 0.25, 8):
        out += bone_knock(t, t0, rng.uniform(500, 1100), 0.45, 0.7)
    out += sub_boom(t, 90, 40, 0.18, 0.6)
    dust = lowpass(band_noise(len(t), 800, 6000), 4500) * E(t, [(0, 0.8), (0.4, 0.2), (0.9, 0)]) * 0.35
    return finish(reverb(out + dust, 0.9, 0.25), -2.5)


def heart_pulse():
    t = t_axis(1.2)
    out = np.zeros_like(t)
    for t0, a in ((0.0, 1.0), (0.32, 0.75)):
        k = np.clip(t - t0, 0, None)
        out += rumble(t, 58, 38) * np.exp(-k / 0.16) * (t >= t0) * a
        out += noise_hit(t, t0, 40, 400, 0.05, 0.3 * a)
    res = modal(t, 0.0, 124, [1, 1.5, 2.01, 3.0], [0.6, 0.45, 0.35, 0.2], 0.3, 0.01)
    return finish(reverb(out + res, 1.2, 0.3), -2.0)


def heart_flame_charge():
    t = t_axis(1.7)
    hiss = band_noise(len(t), 1800, 9000) * E(t, [(0, 0), (1.0, 0.5), (1.6, 0.9), (1.7, 0)]) * 0.3
    roar = lowpass(band_noise(len(t), 60, 600), 600) * E(t, [(0, 0), (0.9, 0.7), (1.6, 1.0), (1.7, 0)])
    cr = crackle(t, 40, 700, 6000, 0.7, span=(0.0, 1.6)) * E(t, [(0, 0.3), (1.6, 1.0)])
    cracks = np.zeros_like(t)
    for t0 in (0.05, 0.4, 0.8, 1.2):
        cracks += stone_knock(t, t0, rng.uniform(180, 320), 0.5, 0.6)
    return finish(reverb(hiss * 0.6 + roar * 0.8 + cr + cracks, 1.0, 0.25), -3.0)


def heart_flame_burst():
    t = t_axis(1.0)
    whoomp = lowpass(band_noise(len(t), 50, 700), 700) * E(t, [(0, 0), (0.02, 1), (0.25, 0.5), (1.0, 0)])
    hiss = band_noise(len(t), 2000, 9000) * E(t, [(0, 0), (0.02, 0.6), (0.5, 0.2), (1.0, 0)]) * 0.3
    cr = crackle(t, 70, 800, 7000, 0.8, span=(0.0, 0.7))
    return finish(reverb(whoomp * 0.9 + hiss + cr + stone_knock(t, 0.0, 150, 0.6, 0.6), 0.8, 0.2), -2.5)


def heart_chain():
    t = t_axis(1.5)
    out = np.zeros_like(t)
    times = np.sort(np.concatenate([rng.uniform(0.0, 1.3, 20), [0.0, 0.05]]))
    for t0 in times:
        out += clink(t, t0, rng.uniform(1500, 3200), rng.uniform(0.3, 0.8))
    drag = creak(t, 14, 40, 200, 2500) * E(t, [(0, 0.2), (0.7, 0.8), (1.5, 0)]) * 0.5
    low = lowpass(band_noise(len(t), 50, 300), 300) * E(t, [(0, 0), (0.4, 0.6), (1.5, 0)])
    return finish(reverb(out + drag + low * 0.5 + stone_knock(t, 0.0, 110, 0.5, 0.8), 1.0, 0.25), -3.0)


def heart_volley_shot():
    t = t_axis(0.7)
    out = stone_knock(t, 0.0, 160, 1.0, 0.7) + bone_knock(t, 0.0, 440, 0.8, 0.9) + click(t, 0.0, 1500, 8000, 0.01)
    wh = sweep_noise(t, 3000, 700, 0.4, 2.0) * E(t, [(0, 0), (0.03, 0.8), (0.4, 0)]) * 0.5
    twang = modal(t, 0.0, 310, [1, 1.5, 2.0], [0.12, 0.08, 0.05], 0.4, 0.02)
    return finish(reverb(out + wh + twang, 0.6, 0.18), -2.5)


def heart_beam_charge():
    t = t_axis(2.2)
    f = np.interp(t, [0, 2.2], [180, 700])
    ph = np.cumsum(f) / SR * 2 * np.pi
    hum = (np.sin(ph) + 0.5 * np.sin(2.01 * ph) + 0.3 * np.sin(3.02 * ph)) * E(t, [(0, 0), (1.0, 0.5), (2.0, 1.0), (2.2, 0)])
    hum *= 1 + 0.25 * np.sin(2 * np.pi * np.interp(t, [0, 2.2], [4, 18]) * t)
    sw = sweep_noise(t, 400, 6000, 0.5, 1.6) * E(t, [(0, 0), (1.5, 0.4), (2.1, 0.9), (2.2, 0)]) * 0.5
    low = rumble(t, 45, 70) * E(t, [(0, 0), (1.0, 0.6), (2.2, 0.9)]) * 0.6
    cr = crackle(t, 50, 1500, 8000, 0.5, span=(0.8, 2.1))
    return finish(reverb(hum * 0.45 + sw + low + cr, 1.5, 0.3), -2.5)


def heart_beam_fire():
    t = t_axis(1.9)
    f = 160 * (1 + 0.04 * np.sin(2 * np.pi * 6 * t))
    ph = np.cumsum(f) / SR * 2 * np.pi
    saw = sum(np.sin(h * ph) / h for h in range(1, 14)) * E(t, [(0, 0), (0.03, 1), (1.4, 0.8), (1.9, 0)])
    roar = band_noise(len(t), 300, 5000) * E(t, [(0, 0), (0.03, 1), (1.4, 0.7), (1.9, 0)])
    sub = sub_boom(t, 60, 40, 0.8, 0.9)
    cr = crackle(t, 80, 1000, 9000, 0.5, span=(0.0, 1.7))
    return finish(reverb(saw * 0.35 + roar * 0.4 + sub + cr, 1.4, 0.3), -1.5)


def heart_guard():
    t = t_axis(0.7)
    out = stone_knock(t, 0.0, 240, 1.0, 1.0) + modal(t, 0.0, 480, [1, 2.4, 4.4], [0.3, 0.2, 0.1], 0.5, 0.02) + click(t, 0.0, 1000, 6000, 0.006)
    return finish(reverb(out, 0.6, 0.2), -2.5)


def heart_emerge():
    t = t_axis(3.0)
    rum = lowpass(band_noise(len(t), 25, 180), 180) * E(t, [(0, 0), (1.0, 0.8), (2.2, 1.0), (3.0, 0)])
    g = creak(t, 5, 14, 70, 600) * E(t, [(0, 0), (0.6, 0.8), (2.4, 0.8), (3.0, 0)])
    out = np.zeros_like(t)
    for t0 in np.sort(rng.uniform(0.3, 2.6, 14)):
        out += stone_knock(t, t0, rng.uniform(80, 260), 0.5, 1.0)
    swell = rumble(t, 28, 52) * E(t, [(0, 0), (1.8, 0.7), (2.6, 1.0), (3.0, 0)]) * 0.5
    return finish(reverb(rum * 0.8 + g * 0.8 + out + swell, 2.0, 0.35), -2.0)


def heart_resonance():
    t = t_axis(3.2)
    core = modal(t, 0.0, 220, [1, 1.5, 2.0, 2.99, 4.01], [2.0, 1.6, 1.4, 1.0, 0.7], 0.5, 0.004)
    shim = modal(t, 0.05, 1320, [1, 1.5, 2.76], [1.4, 1.0, 0.7], 0.12, 0.01)
    low = sub_boom(t, 55, 38, 1.4, 1.0)
    beat = modal(t, 0.0, 223.5, [1, 2.0], [2.0, 1.4], 0.35, 0.0)
    return finish(reverb(core + shim + low * 0.8 + beat, 2.6, 0.4), -2.0)


def heart_plate_fall():
    t = t_axis(1.5)
    crack = stone_knock(t, 0.0, 190, 0.9, 0.8) + noise_hit(t, 0.0, 500, 7000, 0.04, 0.9)
    scrape = creak(t, 12, 5, 150, 1500) * E(t, [(0, 1), (0.4, 0.4), (0.6, 0)]) * 0.5
    fall = stone_knock(t, 0.55, 80, 1.2, 1.4) + sub_boom(t, 70, 30, 0.4, 0.9, 0.55) + noise_hit(t, 0.55, 300, 4000, 0.06, 0.5)
    debris = np.zeros_like(t)
    for t0 in rng.uniform(0.7, 1.2, 6):
        debris += stone_knock(t, t0, rng.uniform(250, 600), 0.2, 0.4)
    return finish(reverb(crack + scrape + fall + debris, 1.3, 0.3), -2.0)


def heart_chain_break():
    t = t_axis(1.2)
    snap = click(t, 0.0, 1500, 9000, 0.012, 1.3) + clink(t, 0.0, 900, 1.0)
    out = np.zeros_like(t)
    for t0 in rng.uniform(0.05, 0.7, 14):
        out += clink(t, t0, rng.uniform(1200, 3000), rng.uniform(0.2, 0.6))
    thud = stone_knock(t, 0.45, 100, 0.8, 1.0)
    return finish(reverb(snap + out + thud, 0.9, 0.25), -2.5)


def heart_core_shatter():
    t = t_axis(3.4)
    suck = sweep_noise(t, 300, 5000, 0.4, 1.5) * E(t, [(0, 0), (1.2, 0.5), (1.45, 0.0), (3.4, 0)]) * 0.35
    boom = sub_boom(t, 90, 24, 1.1, 1.3, 1.5)
    shat = noise_hit(t, 1.5, 800, 9000, 0.12, 1.0) + stone_knock(t, 1.5, 120, 1.2, 1.5) + modal(t, 1.5, 1480, [1, 2.76, 5.4, 8.9], [1.0, 0.6, 0.4, 0.3], 0.5, 0.02)
    for t0 in rng.uniform(1.55, 2.4, 14):
        shat += modal(t, t0, rng.uniform(1500, 4000), [1, 2.76], [0.12, 0.06], 0.18, 0.01)
    rum = lowpass(band_noise(len(t), 25, 250), 250) * E(t, [(0, 0), (1.4, 0.5), (1.6, 1.0), (3.4, 0)])
    return finish(reverb(suck + boom + shat + rum * 0.7, 2.6, 0.4), -1.5)


def heart_shard():
    t = t_axis(1.3)
    out = np.zeros_like(t)
    for t0 in np.sort(rng.uniform(0.0, 0.35, 10)):
        out += modal(t, t0, rng.uniform(1800, 4200), [1, 2.76, 5.4], [0.18, 0.1, 0.05], 0.3, 0.02)
    out += stone_knock(t, 0.0, 170, 0.7, 0.8) + noise_hit(t, 0.0, 1000, 8000, 0.04, 0.6)
    return finish(reverb(out, 1.2, 0.3), -2.5)


# ================================================================ 鯨骨の番兵長 (石鎧の将)

def whale_bone(t, t0, base, amp=1.0, decay=1.0):
    """鯨の骨を叩く。番兵の骨より太く、低く、長く鳴る"""
    d = np.array([0.12, 0.08, 0.05, 0.03]) * decay
    return modal(t, t0, base, [1, 1.7, 2.5, 3.9], d, amp, 0.04) + click(t, t0, 500, 4000, 0.01, amp * 0.5)


def armor_clash(t, t0, amp=1.0):
    """石の鎧板がぶつかる"""
    return stone_knock(t, t0, rng.uniform(150, 260), amp, 0.5) + click(t, t0, 1200, 6000, 0.008, amp * 0.5)


def captain_leap_roar():
    t = t_axis(1.1)
    f0 = np.interp(t, [0, 0.25, 1.1], [60, 100, 55])
    v = lowpass(voice(t, f0, [(180, 90, 1.0), (420, 140, 0.6)], rough=0.05, breath=0.12, vib=(5, 0.02)), 800) * E(t, [(0, 0), (0.05, 1), (0.5, 0.7), (1.1, 0)])
    arm = np.zeros_like(t)
    for t0 in (0.0, 0.05, 0.12, 0.2):
        arm += armor_clash(t, t0, 0.6)
    k = whale_bone(t, 0.02, 190, 0.7, 1.4)
    wind = sweep_noise(t, 300, 1400, 0.5, 2.0) * E(t, [(0, 0), (0.2, 0.7), (0.9, 0)]) * 0.4
    return finish(reverb(v * 0.8 + arm + k + wind, 0.8, 0.22), -2.5)


def captain_shockwave():
    t = t_axis(1.9)
    boom = sub_boom(t, 82, 28, 0.55, 1.2)
    impact = armor_clash(t, 0.0, 1.0) + whale_bone(t, 0.0, 130, 1.0, 2.0) + stone_knock(t, 0.02, 95, 1.0, 1.4)
    ring = sweep_noise(t, 1200, 150, 0.4, 2.0) * E(t, [(0, 0), (0.05, 0.8), (0.9, 0.3), (1.8, 0)]) * 0.5
    shards = np.zeros_like(t)
    for t0 in rng.uniform(0.05, 0.8, 10):
        shards += whale_bone(t, t0, rng.uniform(300, 800), 0.2, 0.5)
    debris = lowpass(band_noise(len(t), 600, 6000), 4500) * E(t, [(0, 0.7), (0.3, 0.35), (1.5, 0)]) * 0.35
    return finish(reverb(boom + impact + ring + shards + debris, 1.6, 0.3), -1.5)


def captain_erupt_charge():
    t = t_axis(1.5)
    out = np.zeros_like(t)
    for t0 in np.sort(rng.uniform(0.0, 1.4, 22) ** 0.9):
        out += whale_bone(t, t0, rng.uniform(220, 520), 0.15 + 0.4 * t0, 0.8)
    sc = creak(t, 8, 28, 200, 1800) * E(t, [(0, 0), (0.9, 0.8), (1.5, 1.0)]) * 0.8
    rum = lowpass(band_noise(len(t), 30, 200), 200) * E(t, [(0, 0), (1.0, 0.6), (1.5, 0.9)])
    return finish(reverb(out + sc + rum * 0.7, 1.0, 0.25), -3.0)


def captain_erupt_burst():
    t = t_axis(0.8)
    out = whale_bone(t, 0.0, 260, 1.0, 1.2) + whale_bone(t, 0.02, 520, 0.6, 0.8) + click(t, 0.0, 1000, 7000, 0.012, 1.0)
    out += sub_boom(t, 100, 45, 0.2, 0.7)
    wh = sweep_noise(t, 600, 3000, 0.5, 2.0) * E(t, [(0, 0), (0.04, 0.7), (0.3, 0)]) * 0.4
    for t0 in rng.uniform(0.03, 0.25, 6):
        out += whale_bone(t, t0, rng.uniform(500, 1100), 0.3, 0.5)
    return finish(reverb(out + wh, 0.8, 0.22), -2.5)


def captain_roar():
    t = t_axis(2.4)
    f0 = np.interp(t, [0, 0.4, 1.4, 2.4], [55, 88, 70, 40])
    v = lowpass(voice(t, f0, [(170, 80, 1.0), (380, 130, 0.7), (700, 180, 0.25)], rough=0.06, breath=0.14, vib=(4, 0.03)), 900)
    v *= E(t, [(0, 0), (0.1, 1), (1.5, 0.9), (2.4, 0)])
    arm = np.zeros_like(t)
    for t0 in np.sort(rng.uniform(0.0, 1.5, 9)):
        arm += armor_clash(t, t0, 0.5)
    bones = np.zeros_like(t)
    for t0 in np.sort(rng.uniform(0.0, 1.8, 12)):
        bones += whale_bone(t, t0, rng.uniform(150, 420), 0.35, 1.4)
    rum = lowpass(band_noise(len(t), 25, 260), 260) * E(t, [(0, 0), (0.15, 1), (1.6, 0.6), (2.4, 0)])
    c = creak(t, 8, 22, 200, 1600) * E(t, [(0, 0), (0.4, 0.8), (1.8, 0.4), (2.4, 0)]) * 0.5
    return finish(reverb(v * 0.8 + arm + bones + rum * 0.7 + c, 1.8, 0.33), -1.5)


# name -> (generator, subtitle ja, subtitle en, attenuation distance)
SOUNDS = {
    "stone_heart_roar": (heart_roar, "石の心臓が咆哮する", "Stone heart roars", 40),
    "stone_heart_shockwave": (heart_shockwave, "石の輪が床を走る", "Stone ring sweeps the floor", 40),
    "stone_heart_phase": (heart_phase, "殻が噛み合い、心臓が変わる", "Plates lock, the heart changes", 40),
    "stone_heart_grind": (heart_grind, "石の板がきしむ", "Stone plates grind", 32),
    "stone_heart_slam": (heart_slam, "重い石が叩きつけられる", "Heavy stone slams", 40),
    "stone_heart_plate_lock": (heart_plate_lock, "石の板が噛み合う", "Stone plate locks", 32),
    "stone_heart_spike_charge": (heart_spike_charge, "骨と石が鳴り集まる", "Bone and stone clatter", 32),
    "stone_heart_spike_burst": (heart_spike_burst, "石の棘が突き出る", "Stone spike bursts", 32),
    "stone_heart_pulse": (heart_pulse, "心臓が深く脈打つ", "Heart throbs deeply", 40),
    "stone_heart_flame_charge": (heart_flame_charge, "石のひびに熱がこもる", "Heat builds in the cracks", 32),
    "stone_heart_flame_burst": (heart_flame_burst, "ひびから炎が噴く", "Flame bursts from the cracks", 32),
    "stone_heart_chain": (heart_chain, "太い鎖が鳴る", "Heavy chain rattles", 32),
    "stone_heart_volley_shot": (heart_volley_shot, "骨の棘が放たれる", "Bone spikes launch", 32),
    "stone_heart_beam_charge": (heart_beam_charge, "核が光を溜める", "Core gathers light", 40),
    "stone_heart_beam_fire": (heart_beam_fire, "赤熱の光線が薙ぐ", "Red-hot beam sweeps", 40),
    "stone_heart_guard": (heart_guard, "殻が攻撃を弾く", "Shell deflects the blow", 24),
    "stone_heart_emerge": (heart_emerge, "石の殻が床から迫り上がる", "Stone shell rises from the floor", 48),
    "stone_heart_resonance": (heart_resonance, "核が共鳴する", "Core resonates", 48),
    "stone_heart_plate_fall": (heart_plate_fall, "石の板が剥がれ落ちる", "Stone plate peels and falls", 40),
    "stone_heart_chain_break": (heart_chain_break, "鎖が切れる", "Chain snaps", 40),
    "stone_heart_core_shatter": (heart_core_shatter, "核が光を吸い込んで砕ける", "Core draws in light and shatters", 48),
    "stone_heart_shard": (heart_shard, "核のかけらが散る", "Core shards scatter", 40),
    "captain_leap_roar": (captain_leap_roar, "番兵長が跳ぶ", "Captain leaps", 32),
    "captain_shockwave": (captain_shockwave, "番兵長が叩きつけて衝撃が走る", "Captain slams, shockwave", 40),
    "captain_erupt_charge": (captain_erupt_charge, "足元で骨が鳴り集まる", "Bones gather underfoot", 32),
    "captain_erupt_burst": (captain_erupt_burst, "骨の槍が突き上がる", "Bone spear erupts", 32),
    "captain_roar": (captain_roar, "番兵長が咆哮する", "Captain roars", 40),
}


def write_json_rows():
    path = RES / "sounds.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    for name, (_, _, _, dist) in SOUNDS.items():
        data[name] = {
            "subtitle": f"subtitles.skyabyss.{name}",
            "sounds": [{"name": f"skyabyss:mobs/{name}", "attenuation_distance": dist}],
        }
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for lang, idx in (("ja_jp", 1), ("en_us", 2)):
        p = RES.parent / "skyabyss_mobsounds/lang" / f"{lang}.json"
        d = json.loads(p.read_text(encoding="utf-8"))
        for name, row in SOUNDS.items():
            d[f"subtitles.skyabyss.{name}"] = row[idx]
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (fn, _, _, _) in SOUNDS.items():
        data = np.nan_to_num(fn()).astype(np.float32)
        assert np.abs(data).max() > 0.05, name
        idx = np.nonzero(np.abs(data) > 0.01 * np.abs(data).max())[0]
        data = data[:min(len(data), idx[-1] + int(0.08 * SR))].copy()
        data[-int(0.06 * SR):] *= np.linspace(1, 0, int(0.06 * SR))
        assert len(data) < SR * 5.0, name
        write_ogg(OUT / f"{name}.ogg", data)
        print(f"{name:28s} {len(data) / SR:.2f} s  peak {np.abs(data).max():.2f}  rms {np.sqrt(np.mean(data ** 2)):.3f}")
    write_json_rows()


if __name__ == "__main__":
    main()
