"""雷翼の背の敵 (羽守・雷蛾・雷乞い) の音を作る (独自合成。乱数の種は固定)。スポーンエッグのアイテムモデルも書く。

使い方: python tools/gen/mobs/gen_storm_mob_assets.py
出力:
  src/main/resources/assets/skyabyss/sounds/storm_mobs/*.ogg
  src/main/resources/assets/skyabyss/sounds.json  (この 18 個の行だけを足す・書き直す。ほかの行はそのまま)
  src/main/resources/assets/skyabyss/models/item/{feather_warden,static_moth,thunder_supplicant}_spawn_egg.json

音 (嵐・風・銅・静電に寄せる。炎・溶岩の音は使わない。大きな鳴き声は入れない):
- feather_warden_ambient : 鎧羽のこすれる乾いた羽音と、兜の中の空洞に風が鳴る低い音 (中は空っぽ)
- feather_warden_hurt    : 銅の兜を打つ鈍い金属音と、羽の散る音
- feather_warden_death   : 外套の羽が崩れ落ち、兜が床で転がる
- feather_warden_swing   : 鉤槍の重い風切り
- feather_warden_crouch  : 外套が開く音 (布と羽の張る音) と、風を溜める吸い込み (突撃の予告)
- feather_warden_dive    : 一直線に落ちる、太い風切りの唸り
- feather_warden_land    : 着地の重い衝撃と、銅の鳴り
- static_moth_ambient    : 薄い翅のはばたき (細かく速い)
- static_moth_charge     : 帯電: 低い唸りが少しずつ高くなり、ぱちぱちが増える
- static_moth_discharge  : 放電: 乾いた破裂と、跳ねる火花
- static_moth_hurt / death / burst : 紙のような翅の裂け / しぼむ火花 / 弾ける放電
- thunder_supplicant_ambient : 籠の中で閉じこめられた雷の、くぐもった唸り (遠い祈りの声のように揺れる)
- thunder_supplicant_raise    : 杖を掲げる: 輪の鳴りが上ってゆく (招雷の予告)
- thunder_supplicant_hurt / death / swing : 籠の格子の鳴り / 籠が落ちて中の雷が抜ける / 杖の風切り
"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../sound',)]
import json
from pathlib import Path

import numpy as np

import gen_sky_sounds as G
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg

ROOT = Path(__file__).resolve().parent.parent.parent.parent
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
OUT = ASSETS / "sounds/storm_mobs"
G.rng = np.random.default_rng(20261024)
rng = G.rng


def lowpass(x, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec / (1 + (f / hz) ** 4), len(x))


def crackles(t, rate, lo=2500, hi=9000, dur=0.006, amp=1.0, shape=None):
    """静電のぱちぱち: 短い帯域ノイズの粒。shape(t) で時刻ごとの密度を変える"""
    out = np.zeros_like(t)
    n = int(rate * t[-1])
    for _ in range(n):
        t0 = rng.uniform(0, t[-1] - dur)
        if shape is not None and rng.uniform() > shape(t0):
            continue
        i0 = int(t0 * SR)
        m = int(dur * SR * rng.uniform(0.6, 1.6))
        seg = band_noise(m, lo, hi) * np.exp(-np.arange(m) / (m * 0.2)) * rng.uniform(0.3, 1.0)
        out[i0:i0 + m] += seg[:len(out) - i0]
    return out * amp


def flutter(t, rate, lo=300, hi=2500, depth=1.0):
    """羽ばたき: 帯域ノイズを rate Hz で揺らす"""
    am = 0.5 + 0.5 * np.sin(2 * np.pi * rate * t) ** 2 * depth
    return band_noise(len(t), lo, hi) * am


def metal(t, freqs, decay):
    return sum(np.sin(2 * np.pi * f * t) * np.exp(-t / (decay * (1 + 0.3 * i))) / (1 + i * 0.6) for i, f in enumerate(freqs))


def whoosh(t, lo, hi, peak):
    return band_noise(len(t), lo, hi) * env(t, [(0, 0), (peak, 1.0), (t[-1], 0)])


# ---------------------------------------------------------------- 羽守

def warden_ambient():
    t = t_axis(1.8)
    rustle = band_noise(len(t), 1500, 6000) * env(t, [(0, 0), (0.3, 0.5), (0.7, 0.2), (1.1, 0.45), (1.8, 0)]) * 0.35
    hollow = np.sin(2 * np.pi * 96 * t + 3 * np.sin(2 * np.pi * 0.7 * t)) * 0.25 + band_noise(len(t), 150, 500) * 0.3
    hollow *= env(t, [(0, 0), (0.6, 1.0), (1.4, 0.6), (1.8, 0)])
    return finish(reverb(rustle + hollow, 1.2, 0.3), -8.0)


def warden_hurt():
    t = t_axis(0.6)
    clank = metal(t, [420, 683, 1110, 1730], 0.08) * 0.6
    thud = band_noise(len(t), 80, 400) * np.exp(-t / 0.05)
    feathers = band_noise(len(t), 2000, 7000) * np.exp(-t / 0.15) * 0.3
    return finish(clank + thud + feathers, -4.0)


def warden_death():
    t = t_axis(2.2)
    collapse = band_noise(len(t), 600, 5000) * env(t, [(0, 0), (0.15, 1.0), (0.9, 0.5), (1.6, 0.1), (2.2, 0)]) * 0.6
    helm_t = np.clip(t - 1.0, 0, None)
    helm = metal(helm_t, [300, 512, 830], 0.25) * (t > 1.0) * 0.7
    helm2_t = np.clip(t - 1.35, 0, None)
    helm += metal(helm2_t, [310, 520], 0.12) * (t > 1.35) * 0.4
    sigh = band_noise(len(t), 120, 600) * env(t, [(0, 0), (0.2, 0.4), (1.2, 0)]) * 0.5
    return finish(reverb(collapse + helm + sigh, 1.6, 0.3), -3.0)


def warden_swing():
    t = t_axis(0.45)
    return finish(whoosh(t, 200, 1800, 0.18) + whoosh(t, 1500, 5000, 0.2) * 0.3, -4.0)


def warden_crouch():
    t = t_axis(1.0)
    snap = band_noise(len(t), 400, 3000) * env(t, [(0, 0), (0.05, 1.0), (0.25, 0.4), (1.0, 0)]) * 0.6
    inhale = band_noise(len(t), 200, 1500) * env(t, [(0, 0), (0.2, 0.1), (0.95, 1.0), (1.0, 0)])
    rise = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 1.0], [70, 140])) / SR) * env(t, [(0, 0), (1.0, 0.5)])
    return finish(snap + inhale * 0.7 + rise * 0.4, -3.0)


def warden_dive():
    t = t_axis(1.0)
    f = np.interp(t, [0, 1.0], [900, 300])
    roar = band_noise(len(t), 100, 2500) * env(t, [(0, 0), (0.1, 1.0), (0.7, 0.8), (1.0, 0)])
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(t, [(0, 0), (0.1, 0.3), (1.0, 0)]) * 0.3
    return finish(lowpass(roar, 3000) + tone, -2.0)


def warden_land():
    t = t_axis(1.0)
    boom = np.sin(2 * np.pi * (55 * t - 15 * t ** 2)) * np.exp(-t / 0.18)
    dust = band_noise(len(t), 200, 2000) * np.exp(-t / 0.25) * 0.5
    ring = metal(t, [250, 410, 640], 0.3) * 0.35
    return finish(reverb(boom + dust + ring, 1.2, 0.25), -2.0)


# ---------------------------------------------------------------- 雷蛾

def moth_ambient():
    t = t_axis(1.2)
    w = flutter(t, 18, 400, 3000) * env(t, [(0, 0), (0.15, 1.0), (1.0, 0.8), (1.2, 0)]) * 0.5
    hum = np.sin(2 * np.pi * 190 * t) * 0.08 * env(t, [(0, 0), (0.3, 1), (1.2, 0)])
    return finish(w + hum + crackles(t, 8, amp=0.3), -12.0)


def moth_charge():
    t = t_axis(2.5)
    f = np.interp(t, [0, 2.5], [110, 330])
    hum = (np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.4 * np.sin(2 * np.pi * np.cumsum(f * 2.01) / SR)) * env(t, [(0, 0), (0.3, 0.4), (2.4, 1.0), (2.5, 0)])
    cr = crackles(t, 160, shape=lambda x: (x / 2.5) ** 1.5, amp=0.9)
    return finish(hum * 0.5 + cr, -6.0)


def moth_discharge():
    t = t_axis(0.8)
    crack = band_noise(len(t), 1500, 12000) * np.exp(-t / 0.03)
    zap = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.3], [2400, 300])) / SR) * np.exp(-t / 0.12) * 0.4
    return finish(reverb(crack + zap + crackles(t, 120, amp=0.6, shape=lambda x: max(0.0, 1 - x / 0.6)), 0.8, 0.2), -2.0)


def moth_hurt():
    t = t_axis(0.35)
    tear = band_noise(len(t), 2500, 9000) * np.exp(-t / 0.06)
    return finish(tear + crackles(t, 60, amp=0.5), -5.0)


def moth_death():
    t = t_axis(1.3)
    fizz = crackles(t, 200, shape=lambda x: max(0.0, 1 - x / 1.2), amp=0.8)
    flap = flutter(t, 12, 300, 2000) * env(t, [(0, 0.8), (1.0, 0)]) * 0.4
    tone = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 1.3], [400, 90])) / SR) * env(t, [(0, 0.3), (1.3, 0)]) * 0.3
    return finish(fizz + flap + tone, -5.0)


def moth_burst():
    t = t_axis(1.2)
    pop = band_noise(len(t), 200, 12000) * np.exp(-t / 0.05)
    boom = np.sin(2 * np.pi * (90 * t - 30 * t ** 2)) * np.exp(-t / 0.15) * 0.6
    return finish(reverb(pop + boom + crackles(t, 220, amp=0.7, shape=lambda x: max(0.0, 1 - x / 0.8)), 1.0, 0.25), -1.5)


# ---------------------------------------------------------------- 雷乞い

def sup_ambient():
    t = t_axis(2.6)
    vib = 4 * np.sin(2 * np.pi * 0.9 * t)
    voice = (np.sin(2 * np.pi * 147 * t + vib) + 0.5 * np.sin(2 * np.pi * 220.5 * t + vib * 1.5) + 0.3 * np.sin(2 * np.pi * 294 * t))
    voice = lowpass(voice, 900) * env(t, [(0, 0), (0.6, 0.8), (1.6, 1.0), (2.6, 0)]) * 0.3
    buzz = crackles(t, 25, amp=0.25)
    return finish(reverb(voice + buzz, 2.0, 0.4), -10.0)


def sup_cast():
    t = t_axis(1.4)
    f = np.interp(t, [0, 1.2], [330, 880])
    chime = sum(np.sin(2 * np.pi * np.cumsum(f * k) / SR) / k for k in (1, 2.02, 3.1)) * env(t, [(0, 0), (0.1, 0.6), (1.2, 1.0), (1.4, 0)])
    cr = crackles(t, 120, shape=lambda x: x / 1.4, amp=0.5)
    return finish(reverb(chime * 0.4 + cr, 1.5, 0.35), -4.0)


def sup_hurt():
    t = t_axis(0.5)
    bars = metal(t, [880, 1320, 1990, 2710], 0.06) * 0.6
    cloth = band_noise(len(t), 300, 2500) * np.exp(-t / 0.08) * 0.5
    return finish(bars + cloth, -5.0)


def sup_death():
    t = t_axis(2.0)
    fall_t = np.clip(t - 0.5, 0, None)
    cage = metal(fall_t, [700, 1150, 1720], 0.12) * (t > 0.5) * 0.6
    cage += metal(np.clip(t - 0.75, 0, None), [690, 1130], 0.08) * (t > 0.75) * 0.3
    release = crackles(t, 150, shape=lambda x: max(0.0, 1 - abs(x - 0.6) / 0.6), amp=0.7)
    sigh = band_noise(len(t), 150, 900) * env(t, [(0, 0), (0.3, 0.6), (1.6, 0)]) * 0.5
    return finish(reverb(cage + release + sigh, 1.6, 0.3), -4.0)


def sup_swing():
    t = t_axis(0.35)
    return finish(whoosh(t, 400, 3000, 0.12), -6.0)


SOUNDS = {
    "feather_warden_ambient": (warden_ambient, "Feather Warden rustles"),
    "feather_warden_hurt": (warden_hurt, "Copper helm rings"),
    "feather_warden_death": (warden_death, "Feather Warden collapses"),
    "feather_warden_swing": (warden_swing, "Hooked glaive swings"),
    "feather_warden_crouch": (warden_crouch, "Feather Warden spreads its cloak"),
    "feather_warden_dive": (warden_dive, "Feather Warden dives"),
    "feather_warden_land": (warden_land, "Feather Warden slams down"),
    "static_moth_ambient": (moth_ambient, "Static Moth flutters"),
    "static_moth_charge": (moth_charge, "Static Moth charges"),
    "static_moth_discharge": (moth_discharge, "Static Moth discharges"),
    "static_moth_hurt": (moth_hurt, "Static Moth crackles"),
    "static_moth_death": (moth_death, "Static Moth fizzles out"),
    "static_moth_burst": (moth_burst, "Charged moth bursts"),
    "thunder_supplicant_ambient": (sup_ambient, "Thunder Supplicant murmurs"),
    "thunder_supplicant_raise": (sup_cast, "Thunder Supplicant raises its staff"),
    "thunder_supplicant_hurt": (sup_hurt, "Cage rattles"),
    "thunder_supplicant_death": (sup_death, "Thunder Supplicant falls"),
    "thunder_supplicant_swing": (sup_swing, "Staff swings"),
}


def update_sounds_json():
    """sounds.json に、この敵の行だけを足す (1 行 1 項目の書き方に合わせる。ほかの行は触らない)"""
    path = ASSETS / "sounds.json"
    text = path.read_text(encoding="utf-8").rstrip()
    assert text.endswith("}")
    # 項目が複数行にわたる所もあるので、自分の行 (1 行 1 項目) だけを除いて、末尾に足す
    lines = [ln for ln in text[:-1].rstrip().split("\n") if not any(ln.strip().startswith(f'"{k}"') for k in SOUNDS)]
    lines[-1] = lines[-1].rstrip().rstrip(",")
    for k in SOUNDS:
        entry = {"subtitle": f"subtitles.skyabyss.{k}", "sounds": [{"name": f"skyabyss:storm_mobs/{k}"}]}
        lines[-1] += ","
        lines.append(f'  "{k}": {json.dumps(entry, ensure_ascii=False)}')
    text = "\n".join(lines) + "\n}\n"
    json.loads(text)
    path.write_text(text, encoding="utf-8")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (fn, _) in SOUNDS.items():
        data = fn().astype(np.float32)
        write_ogg(OUT / f"{name}.ogg", data)
        print(name, "%.2f s" % (len(data) / SR))
    update_sounds_json()
    for mob in ("feather_warden", "static_moth", "thunder_supplicant"):
        (ASSETS / "models/item" / f"{mob}_spawn_egg.json").write_text('{\n  "parent": "minecraft:item/template_spawn_egg"\n}\n', encoding="utf-8")


if __name__ == "__main__":
    main()
