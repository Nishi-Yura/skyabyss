"""余燼の三つの試練 (灰の道・鍛えの間・火守り) の音 14 個を作る (独自合成。乱数の種は固定。13 の 4 章、11.2節の担当 C1・C2・C3)。

使い方: python tools/gen/sound/gen_ember_trial_sounds.py
出力: src/main/resources/assets/skyabyss/sounds/ember_trial/*.ogg と、sounds.json の自分の行 (名前は EmberIds の SOUND_*)

- ember_ash_scatter  : 灰をまく (さらさら落ちる灰と、息のような短い風)
- ember_crust_crack  : 冷え殻の割れ目 (ぴしっ、ぱきっ)
- ember_crust_sink   : 殻が沈む (重い石が溶岩に落ちる、じゅっ)
- ember_engulf       : 呑まれた (溶岩のぼこぼこと低い唸り)
- ember_bellows      : 鞴 (ふうっと空気が抜けて、炉が息を吹き返す)
- ember_anvil_strike : 金床を打つ (金属の鈍い一打と火花)
- ember_anvil_ting   : 打てる色に入った瞬間 (小さく澄んだ「ちん」)
- ember_bloom_crack  : 塊が割れる (鉄の割れる音と、砕ける破片)
- ember_quench       : 焼き入れ (じゅわっと蒸気が立つ)
- ember_door_kindle  : 扉の文様が灯る (低い共鳴と、火が走る音)
- ember_vent_hiss    : 噴気の予告 (しゅうと高い息)
- ember_vent_burst   : 噴気 (どんと吹き上がる轟き)
- ember_hearth_feed  : 熾をくべる (ぼっと火が上がる)
- ember_hearth_douse : 灰をかけられる (どさっと灰、しゅうと火がしぼむ)
"""
import json
from pathlib import Path

import numpy as np

import gen_ember_mob_sounds as M
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg

ROOT = Path(__file__).resolve().parent.parent.parent.parent
OUT = ROOT / "src/main/resources/assets/skyabyss/sounds/ember_trial"
SOUNDS_JSON = ROOT / "src/main/resources/assets/skyabyss/sounds.json"
M.G.rng = np.random.default_rng(20261020)
rng = M.G.rng
lowpass, grains, crackle, rumble, roar = M.lowpass, M.grains, M.crackle, M.rumble, M.roar


def ash_scatter():
    t = t_axis(0.9)
    pour = lowpass(band_noise(len(t), 600, 6000), 4500) * env(t, [(0, 0), (0.05, 0.8), (0.4, 0.5), (0.9, 0)])
    puff = roar(t, 200, 1200) * env(t, [(0, 0), (0.04, 0.5), (0.3, 0.1), (0.9, 0)])
    return finish(reverb(pour + puff * 0.5 + grains(t, np.sort(rng.uniform(0.05, 0.7, 14)), 2500, 8000, dur=0.01, amp=0.3), 0.7, 0.2), -6.0)


def crust_crack():
    t = t_axis(0.5)
    snap = grains(t, [0.0, 0.03, 0.09], 1200, 7000, dur=0.02, amp=1.1)
    tick = grains(t, np.sort(rng.uniform(0.0, 0.4, 6)), 3000, 9000, dur=0.006, amp=0.5)
    return finish(reverb(snap + tick, 0.5, 0.2), -4.0)


def crust_sink():
    t = t_axis(1.1)
    drop = rumble(t, 120, 50) * env(t, [(0, 0), (0.02, 1.0), (0.3, 0.3), (1.1, 0)])
    hiss = band_noise(len(t), 2000, 8000) * env(t, [(0, 0), (0.05, 0.6), (0.6, 0.2), (1.1, 0)])
    bub = grains(t, np.sort(rng.uniform(0.1, 0.9, 10)), 300, 1500, dur=0.04, amp=0.5)
    return finish(reverb(drop + hiss * 0.5 + bub, 0.9, 0.25), -3.5)


def engulf():
    t = t_axis(1.4)
    growl = roar(t, 40, 400) * env(t, [(0, 0), (0.05, 1.0), (0.6, 0.6), (1.4, 0)])
    bub = grains(t, np.sort(rng.uniform(0.0, 1.2, 22)), 200, 1200, dur=0.05, amp=0.7)
    hiss = band_noise(len(t), 1500, 7000) * env(t, [(0, 0), (0.1, 0.5), (1.0, 0.15), (1.4, 0)])
    return finish(reverb(growl + bub + hiss * 0.5, 1.1, 0.3), -2.5)


def bellows():
    t = t_axis(1.0)
    out = lowpass(band_noise(len(t), 200, 3000), 2500) * env(t, [(0, 0), (0.08, 0.9), (0.5, 0.5), (1.0, 0)])
    creak = band_noise(len(t), 300, 1200) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 30 * t))) * env(t, [(0, 0), (0.1, 0.3), (0.4, 0.2), (0.5, 0)])
    glow = roar(t, 60, 300) * env(t, [(0, 0), (0.3, 0.2), (0.6, 0.5), (1.0, 0)])
    return finish(reverb(out + creak * 0.4 + glow * 0.6, 0.8, 0.2), -4.0)


def anvil_strike():
    t = t_axis(1.0)
    hit = grains(t, [0.0, 0.004], 800, 9000, dur=0.03, amp=1.3)
    ring = (np.sin(2 * np.pi * 540 * t) * 0.5 + np.sin(2 * np.pi * 1330 * t) * 0.3) * np.exp(-t * 7)
    body = rumble(t, 180, 90) * env(t, [(0, 1), (0.08, 0.4), (0.3, 0)])
    sparks = grains(t, np.sort(rng.uniform(0.02, 0.5, 16)), 4000, 10000, dur=0.005, amp=0.4)
    return finish(reverb(hit + ring * 0.4 + body * 0.7 + sparks, 0.9, 0.25), -2.0)


def anvil_ting():
    t = t_axis(0.8)
    tone = (np.sin(2 * np.pi * 1760 * t) + 0.4 * np.sin(2 * np.pi * 2640 * t)) * np.exp(-t * 6)
    return finish(reverb(tone, 0.6, 0.15), -9.0)


def bloom_crack():
    t = t_axis(0.9)
    crack = grains(t, [0.0, 0.015, 0.04, 0.08], 500, 6000, dur=0.05, amp=1.2)
    shards = grains(t, np.sort(rng.uniform(0.05, 0.7, 26)), 2500, 9000, dur=0.012, amp=0.5)
    ring = np.sin(2 * np.pi * 420 * t) * np.exp(-t * 11) * 0.35
    return finish(reverb(crack + shards + ring, 0.8, 0.25), -2.5)


def quench():
    t = t_axis(1.6)
    steam = band_noise(len(t), 2500, 9500) * env(t, [(0, 0), (0.04, 1.0), (0.5, 0.6), (1.6, 0)])
    bub = grains(t, np.sort(rng.uniform(0.0, 1.0, 18)), 300, 1500, dur=0.05, amp=0.5)
    thud = rumble(t, 100, 60) * env(t, [(0, 0), (0.02, 0.8), (0.2, 0)])
    return finish(reverb(steam * 0.8 + bub + thud * 0.5, 1.0, 0.25), -3.0)


def door_kindle():
    t = t_axis(1.8)
    hum = (np.sin(2 * np.pi * 82 * t) + 0.5 * np.sin(2 * np.pi * 123 * t)) * env(t, [(0, 0), (0.3, 0.6), (1.2, 0.5), (1.8, 0)])
    run = band_noise(len(t), 800, 5000) * env(t, [(0, 0), (0.2, 0.5), (1.0, 0.4), (1.8, 0)])
    return finish(reverb(hum * 0.6 + run * 0.4 + crackle(t, 20, amp=0.4), 1.5, 0.3), -4.0)


def vent_hiss():
    t = t_axis(1.5)
    h = band_noise(len(t), 3000, 10000) * env(t, [(0, 0), (0.6, 0.35), (1.5, 0.8)])
    return finish(reverb(h, 0.7, 0.2), -6.0)


def vent_burst():
    t = t_axis(1.6)
    boom = roar(t, 40, 500) * env(t, [(0, 0), (0.03, 1.0), (0.4, 0.7), (1.6, 0)])
    blast = band_noise(len(t), 800, 7000) * env(t, [(0, 0), (0.03, 0.8), (1.0, 0.3), (1.6, 0)])
    return finish(reverb(boom + blast * 0.5 + crackle(t, 25, amp=0.3), 1.3, 0.3), -1.5)


def hearth_feed():
    t = t_axis(1.0)
    whoomp = roar(t, 50, 600) * env(t, [(0, 0), (0.05, 1.0), (0.4, 0.5), (1.0, 0)])
    sparks = crackle(t, 40, amp=0.5)
    return finish(reverb(whoomp + sparks, 0.8, 0.2), -3.0)


def hearth_douse():
    t = t_axis(1.4)
    pour = lowpass(band_noise(len(t), 400, 5000), 3500) * env(t, [(0, 0), (0.05, 0.9), (0.4, 0.5), (0.7, 0)])
    hiss = band_noise(len(t), 3000, 9000) * env(t, [(0, 0), (0.3, 0.2), (0.6, 0.7), (1.4, 0)])
    thud = rumble(t, 90, 50) * env(t, [(0, 0), (0.02, 1.0), (0.3, 0)])
    return finish(reverb(pour + hiss * 0.5 + thud * 0.6, 1.0, 0.25), -3.0)


SOUNDS = {
    "ember_ash_scatter": ash_scatter,
    "ember_crust_crack": crust_crack,
    "ember_crust_sink": crust_sink,
    "ember_engulf": engulf,
    "ember_bellows": bellows,
    "ember_anvil_strike": anvil_strike,
    "ember_anvil_ting": anvil_ting,
    "ember_bloom_crack": bloom_crack,
    "ember_quench": quench,
    "ember_door_kindle": door_kindle,
    "ember_vent_hiss": vent_hiss,
    "ember_vent_burst": vent_burst,
    "ember_hearth_feed": hearth_feed,
    "ember_hearth_douse": hearth_douse,
}


def write_json():
    """sounds.json の自分の行だけを足す (ほかの行は書き換えない)。"""
    raw = SOUNDS_JSON.read_bytes().decode("utf-8")
    data = json.loads(raw)
    for name in SOUNDS:
        data[name] = {"subtitle": f"subtitles.skyabyss.{name}", "sounds": [{"name": f"skyabyss:ember_trial/{name}"}]}
    SOUNDS_JSON.write_bytes((json.dumps(data, indent=2, ensure_ascii=False) + "\n").encode("utf-8"))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUNDS.items():
        data = np.nan_to_num(fn()).astype(np.float32)
        assert np.abs(data).max() > 0.05, name
        write_ogg(OUT / f"{name}.ogg", data)
        print(name, "%.2f s" % (len(data) / SR))
    write_json()


if __name__ == "__main__":
    main()
