"""岩鯨の背の新しい敵 3 体 (骨の砲手・骨鳥・老番兵、21 の 3.3) の鳴き声・足音・技の音。

使い方: python tools/gen_ch1_back_mob_sounds.py
出力: assets/skyabyss/sounds/mobs/ch1back_*.ogg、sounds.json の行 (ch1back_ の名前。何度流してもよい)、
      assets/skyabyss_ch1back/lang/{ja_jp,en_us}.json の字幕。登録は registry/Ch1BackSounds。
部品は gen_mob_sounds.py のものを使う。
"""
import json
from pathlib import Path

import numpy as np

import gen_mob_sounds as M
from gen_mob_sounds import E, SR, band_noise, bone_knock, bubbles, click, creak, finish, lowpass, noise_hit, reverb, rng, stone_knock, t_axis, voice, write_ogg

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets"
OUT = ASSETS / "skyabyss/sounds/mobs"


def rumble(t, f0, f1):
    f = np.interp(t, [0, t[-1]], [f0, f1])
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


# ---------------------------------------------------------------- 骨の砲手 (背の丸い臼砲。重く、湿った骨と石)

def mortar_ambient():
    t = t_axis(1.8)
    c = creak(t, 8, 18, 250, 1500) * E(t, [(0, 0), (0.4, 0.7), (1.2, 0.5), (1.8, 0)])
    hum = lowpass(band_noise(len(t), 90, 300), 300) * E(t, [(0, 0), (0.8, 0.7), (1.8, 0)])
    return finish(reverb(c * 0.7 + hum * 0.8 + bone_knock(t, 1.3, 340, 0.3), 0.8, 0.25), -8.0)


def mortar_hurt(base):
    t = t_axis(0.5)
    hit = bone_knock(t, 0.0, base, 1.0, 1.3) + stone_knock(t, 0.0, 160, 0.6, 0.6) + noise_hit(t, 0.0, 800, 5000, 0.02, 0.4)
    return finish(reverb(hit, 0.5, 0.2), -3.0)


def mortar_death():
    t = t_axis(1.6)
    out = np.zeros_like(t)
    for i, t0 in enumerate(np.sort(rng.uniform(0.0, 0.9, 12) ** 1.3)):
        out += bone_knock(t, t0, rng.uniform(260, 600), 0.9 * (1 - i / 18), 1.2)
    out += stone_knock(t, 0.05, 90, 0.9, 1.3) + stone_knock(t, 0.45, 110, 0.5, 1.0)
    out += noise_hit(t, 0.0, 300, 3500, 0.4, 0.4)
    return finish(reverb(lowpass(out, 5000), 1.0, 0.25), -2.5)


def mortar_step():
    t = t_axis(0.3)
    out = stone_knock(t, 0.0, 105, 0.9, 0.7) + bone_knock(t, 0.02, 300, 0.4, 0.8) + noise_hit(t, 0.0, 150, 1500, 0.06, 0.4)
    return finish(out, -5.0)


def mortar_charge():
    # 砲身を上げる: 骨のきしみが上がっていく
    t = t_axis(0.9)
    c = creak(t, 10, 40, 300, 2500) * E(t, [(0, 0), (0.3, 0.7), (0.8, 1.0), (0.9, 0)])
    tick = bone_knock(t, 0.75, 420, 0.5, 1.0)
    return finish(reverb(c + tick + lowpass(band_noise(len(t), 80, 300), 300) * 0.3, 0.5, 0.2), -4.0)


def mortar_fire():
    # 臼砲: 低い鳴りと砕ける音
    t = t_axis(0.9)
    boom = rumble(t, 85, 32) * E(t, [(0, 0), (0.01, 1), (0.25, 0.5), (0.9, 0)])
    blast = lowpass(band_noise(len(t), 150, 3500), 3500) * E(t, [(0, 0), (0.01, 1), (0.15, 0.3), (0.5, 0)])
    return finish(reverb(boom * 0.9 + blast * 0.8 + bone_knock(t, 0.0, 260, 0.9, 1.5) + click(t, 0.0, 800, 6000, 0.01, 0.8), 1.0, 0.3), -2.0)


def mortar_land():
    # 骨の塊が落ちる: 低い衝撃と飛び散る骨
    t = t_axis(1.0)
    out = stone_knock(t, 0.0, 75, 1.0, 1.4) + noise_hit(t, 0.0, 120, 2800, 0.3, 0.7)
    for i, t0 in enumerate(np.sort(rng.uniform(0.04, 0.45, 9))):
        out += bone_knock(t, t0, rng.uniform(500, 1400), 0.5 * (1 - i / 14), 0.8)
    return finish(reverb(out, 1.0, 0.3), -2.0)


# ---------------------------------------------------------------- 骨鳥 (細い骨の鳥。かん高い)

def gull_voice(dur, f_a, f_b, amp_pts, vib=(9, 0.04)):
    t = t_axis(dur)
    f0 = np.interp(t, [0, dur], [f_a, f_b])
    v = voice(t, f0, [(2600, 700, 1.0), (4300, 900, 0.5)], rough=0.05, breath=0.15, vib=vib)
    return t, v * E(t, amp_pts)


def gull_ambient():
    t, v = gull_voice(0.5, 1900, 1400, [(0, 0), (0.05, 0.8), (0.3, 0.5), (0.5, 0)])
    return finish(reverb(v + bone_knock(t, 0.3, 1500, 0.2), 0.5, 0.15), -9.0)


def gull_hurt():
    t, v = gull_voice(0.3, 2400, 1200, [(0, 0), (0.02, 1), (0.3, 0)])
    return finish(reverb(v + bone_knock(t, 0.0, 1300, 0.5, 0.8), 0.4, 0.15), -4.0)


def gull_death():
    t, v = gull_voice(0.7, 2200, 500, [(0, 0), (0.03, 1), (0.5, 0.5), (0.7, 0)])
    out = v + sum(bone_knock(t, t0, rng.uniform(700, 1700), 0.5, 0.8) for t0 in np.sort(rng.uniform(0.15, 0.6, 6)))
    return finish(reverb(out, 0.7, 0.2), -3.0)


def gull_cry():
    # 急降下の予告: 長く伸びるかん高い鳴き声 (顎が開く)
    t, v = gull_voice(0.8, 1300, 2700, [(0, 0), (0.1, 0.8), (0.55, 1.0), (0.8, 0)], vib=(11, 0.05))
    return finish(reverb(v + click(t, 0.0, 2000, 9000, 0.006, 0.6), 0.6, 0.2), -2.0)


def gull_dive():
    t = t_axis(0.5)
    w = band_noise(len(t), 700, 6000) * E(t, [(0, 0), (0.25, 0.9), (0.5, 0)])
    tone = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.5], [1800, 600])) / SR) * E(t, [(0, 0), (0.1, 0.4), (0.5, 0)])
    return finish(w * 0.8 + tone * 0.3, -4.0)


# ---------------------------------------------------------------- 老番兵 (骨の番兵の大きな変種。石の板と骨)

def elder_ambient():
    t = t_axis(2.0)
    c = creak(t, 10, 26, 300, 1800) * E(t, [(0, 0), (0.4, 0.8), (1.2, 0.6), (2.0, 0)])
    hum = np.sin(2 * np.pi * 92 * t) * E(t, [(0, 0), (1.0, 0.25), (2.0, 0)]) * (1 + 0.3 * np.sin(2 * np.pi * 4 * t))
    return finish(reverb(c * 0.8 + hum * 0.2 + stone_knock(t, 1.4, 150, 0.2) + bone_knock(t, 1.0, 380, 0.25), 0.9, 0.25), -7.0)


def elder_hurt(base):
    t = t_axis(0.5)
    hit = bone_knock(t, 0.0, base, 1.0, 1.2) + stone_knock(t, 0.0, 140, 0.8, 0.8) + noise_hit(t, 0.0, 1500, 6000, 0.02, 0.4)
    return finish(reverb(hit, 0.6, 0.2), -3.0)


def elder_death():
    t = t_axis(2.0)
    out = np.zeros_like(t)
    for i, t0 in enumerate(np.sort(rng.uniform(0.0, 1.2, 16) ** 1.3)):
        out += bone_knock(t, t0, rng.uniform(240, 700), 0.9 * (1 - i / 22), 1.2)
    out += stone_knock(t, 0.05, 80, 1.0, 1.5) + stone_knock(t, 0.6, 100, 0.6, 1.2)
    out += lowpass(band_noise(len(t), 400, 4000), 4000) * E(t, [(0, 0), (0.3, 0.3), (1.5, 0.1), (2.0, 0)]) * 0.4
    return finish(reverb(out, 1.3, 0.3), -2.0)


def elder_step():
    t = t_axis(0.3)
    out = bone_step_like(t, 300) + stone_knock(t, 0.0, 95, 0.7, 0.6)
    return finish(out, -4.0)


def bone_step_like(t, base):
    return bone_knock(t, 0.0, base, 0.8, 0.9) + noise_hit(t, 0.0, 200, 1500, 0.07, 0.3) + bone_knock(t, 0.08, base * 1.2, 0.25, 0.6)


def elder_rush():
    # 盾の突進: 低い唸りと石の板がこすれる
    t = t_axis(0.9)
    scrape = creak(t, 20, 60, 400, 3500) * E(t, [(0, 0), (0.2, 0.8), (0.7, 1.0), (0.9, 0)])
    roar = rumble(t, 70, 110) * E(t, [(0, 0), (0.15, 0.7), (0.8, 0.6), (0.9, 0)])
    return finish(reverb(scrape * 0.8 + roar * 0.5 + stone_knock(t, 0.0, 120, 0.5), 0.6, 0.2), -3.0)


def elder_stomp():
    t = t_axis(0.9)
    out = stone_knock(t, 0.0, 62, 1.0, 1.6) + noise_hit(t, 0.0, 80, 2500, 0.25, 0.7)
    out += rumble(t, 55, 30) * E(t, [(0, 0), (0.01, 0.9), (0.4, 0.3), (0.9, 0)]) * 0.6
    return finish(reverb(out, 1.0, 0.3), -2.0)


def elder_flute():
    # 骨の笛: 息まじりの高い 2 音 (E 下がる)
    t = t_axis(1.3)
    f0 = np.interp(t, [0, 0.55, 0.62, 1.3], [392, 392, 330, 330]) * (1 + 0.012 * np.sin(2 * np.pi * 5.5 * t))
    ph = 2 * np.pi * np.cumsum(f0) / SR
    tone = (np.sin(ph) + 0.35 * np.sin(2 * ph) + 0.12 * np.sin(3 * ph)) * E(t, [(0, 0), (0.06, 0.9), (0.5, 0.8), (0.6, 0.2), (0.66, 0.8), (1.1, 0.7), (1.3, 0)])
    breath = band_noise(len(t), 1500, 6000) * E(t, [(0, 0), (0.06, 0.18), (1.1, 0.1), (1.3, 0)])
    return finish(reverb(tone * 0.7 + breath, 1.0, 0.3), -4.0)


SOUNDS = {
    "ch1back_mortar_ambient": mortar_ambient,
    "ch1back_mortar_hurt_1": lambda: mortar_hurt(330),
    "ch1back_mortar_hurt_2": lambda: mortar_hurt(420),
    "ch1back_mortar_death": mortar_death,
    "ch1back_mortar_step_1": mortar_step,
    "ch1back_mortar_step_2": mortar_step,
    "ch1back_mortar_charge": mortar_charge,
    "ch1back_mortar_fire": mortar_fire,
    "ch1back_mortar_land": mortar_land,
    "ch1back_gull_ambient": gull_ambient,
    "ch1back_gull_hurt": gull_hurt,
    "ch1back_gull_death": gull_death,
    "ch1back_gull_cry": gull_cry,
    "ch1back_gull_dive": gull_dive,
    "ch1back_elder_ambient": elder_ambient,
    "ch1back_elder_hurt_1": lambda: elder_hurt(260),
    "ch1back_elder_hurt_2": lambda: elder_hurt(340),
    "ch1back_elder_death": elder_death,
    "ch1back_elder_step_1": elder_step,
    "ch1back_elder_step_2": elder_step,
    "ch1back_elder_rush": elder_rush,
    "ch1back_elder_stomp": elder_stomp,
    "ch1back_elder_flute": elder_flute,
}

# 事象 (sounds.json のキー) → (ファイル名の一覧, 字幕 ja, 字幕 en, 減衰距離 or None)
EVENTS = {
    "bone_mortar_ambient": (["ch1back_mortar_ambient"], "骨の砲手がきしむ", "A mortar creaks", None),
    "bone_mortar_hurt": (["ch1back_mortar_hurt_1", "ch1back_mortar_hurt_2"], "骨の砲手が欠ける", "Bone mortar chips", None),
    "bone_mortar_death": (["ch1back_mortar_death"], "骨の砲手が崩れる", "Mortar collapses", None),
    "bone_mortar_step": (["ch1back_mortar_step_1", "ch1back_mortar_step_2"], "重い足音", "Heavy steps", None),
    "bone_mortar_charge": (["ch1back_mortar_charge"], "砲身が上がる", "Barrel rises", 24),
    "bone_mortar_fire": (["ch1back_mortar_fire"], "骨の塊が撃ち出される", "Bone shell fires", 40),
    "bone_mortar_land": (["ch1back_mortar_land"], "骨の塊が落ちる", "Bone shell lands", 40),
    "bone_gull_ambient": (["ch1back_gull_ambient"], "骨鳥が鳴く", "A bone gull cries", None),
    "bone_gull_hurt": (["ch1back_gull_hurt"], "骨鳥が鳴く", "Bone gull squeals", None),
    "bone_gull_death": (["ch1back_gull_death"], "骨鳥が落ちる", "Bone gull falls", None),
    "bone_gull_cry": (["ch1back_gull_cry"], "骨鳥が鋭く鳴く", "Bone gull shrieks", 32),
    "bone_gull_dive": (["ch1back_gull_dive"], "骨鳥が急降下する", "Bone gull dives", 24),
    "elder_sentinel_ambient": (["ch1back_elder_ambient"], "老番兵の骨がきしむ", "The elder sentinel creaks", None),
    "elder_sentinel_hurt": (["ch1back_elder_hurt_1", "ch1back_elder_hurt_2"], "石板が割れる", "Stone plates crack", None),
    "elder_sentinel_death": (["ch1back_elder_death"], "老番兵が崩れ落ちる", "The elder sentinel collapses", 40),
    "elder_sentinel_step": (["ch1back_elder_step_1", "ch1back_elder_step_2"], "重い骨の足音", "Heavy bone steps", None),
    "elder_sentinel_rush": (["ch1back_elder_rush"], "老番兵が盾ごと突進する", "Shield rushes", 32),
    "elder_sentinel_stomp": (["ch1back_elder_stomp"], "足が床を踏み鳴らす", "A stomp shakes the floor", 40),
    "elder_sentinel_flute": (["ch1back_elder_flute"], "骨笛が鳴る", "A bone flute sounds", 40),
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUNDS.items():
        data = np.nan_to_num(fn()).astype(np.float32)
        assert np.abs(data).max() > 0.05, name
        idx = np.nonzero(np.abs(data) > 0.01 * np.abs(data).max())[0]
        data = data[:min(len(data), idx[-1] + int(0.08 * SR))].copy()
        data[-int(0.06 * SR):] *= np.linspace(1, 0, int(0.06 * SR))
        assert len(data) < SR * 4.5, name
        write_ogg(OUT / f"{name}.ogg", data)
        print(name, "%.2f s" % (len(data) / SR))
    path = ASSETS / "skyabyss/sounds.json"
    sounds = json.loads(path.read_text(encoding="utf-8"))
    ja, en = {}, {}
    for key, (files, sja, sen, att) in EVENTS.items():
        entries = []
        for f in files:
            e = {"name": "skyabyss:mobs/" + f}
            if att:
                e["attenuation_distance"] = att
            entries.append(e)
        sounds[key] = {"subtitle": "subtitles.skyabyss." + key, "sounds": entries}
        ja["subtitles.skyabyss." + key] = sja
        en["subtitles.skyabyss." + key] = sen
    path.write_text(json.dumps(sounds, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for code, data in (("ja_jp", ja), ("en_us", en)):
        d = ASSETS / "skyabyss_ch1back/lang"
        d.mkdir(parents=True, exist_ok=True)
        p = d / (code + ".json")
        cur = json.loads(p.read_text(encoding="utf-8")) if p.exists() else {}
        cur.update(data)
        p.write_text(json.dumps(cur, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
