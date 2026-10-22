"""潮殻の背の敵 (珊瑚憑き・磯走り・泡吹き) の音と、小さな絵を作る (独自合成。乱数の種は固定)。

使い方: python tools/gen/mobs/gen_tide_mob_assets.py
出力:
  src/main/resources/assets/skyabyss/sounds/tide_mobs/*.ogg
  src/main/resources/assets/skyabyss/textures/entity/bubble_shot.png       (泡吹きの泡。カメラを向く一枚の絵)
  src/main/resources/assets/skyabyss/textures/item/{tide_shell_shard,tide_dried_shell,bubble_sac}.png  (32x32。仮の絵)

音 (静かな恐さ: 大きな鳴き声でなく、乾いた・湿った小さな音):
- coral_thrall_ambient : 珊瑚のこすれる乾いたきしみと、骨の奥の低いうなり
- coral_thrall_hurt    : 珊瑚の枝が折れる音
- coral_thrall_death   : 骨と珊瑚が崩れ、砂に落ちる
- coral_thrall_thrust  : 銛を突き出す風切り
- coral_thrall_petrify : 潮が満ちて固まる: 石のきしみと、泡が閉じこめられる音
- coral_thrall_wake    : 像の殻が割れる
- coral_thrall_stone_tap : 像を叩いたときの、硬い石の音 (傷は通らない)
- reef_scuttler_*      : 殻のかち合う音 (ambient)、甲高いきしみ (hurt)、殻の割れ (death)、小さな足音 (step)、裏返る殻の音 (flip)
- bubble_puffer_*      : 水の中のごぼごぼ (ambient)、ふくらむ (inflate)、しぼむ (deflate)、泡を吐く (shoot)、きしむ (hurt)、しぼんで沈む (death)
- bubble_pop           : 泡がはじける
"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../sound')]
from pathlib import Path

import numpy as np
from PIL import Image

import gen_sky_sounds as G
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg

ROOT = Path(__file__).resolve().parent.parent.parent.parent
OUT = ROOT / "src/main/resources/assets/skyabyss/sounds/tide_mobs"
TEX = ROOT / "src/main/resources/assets/skyabyss/textures"
G.rng = np.random.default_rng(20261018)
rng = G.rng


def lowpass(x, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec / (1 + (f / hz) ** 4), len(x))


def clicks(t, times, lo=1500, hi=5000, dur=0.012, amp=1.0):
    """乾いた小さなかち合い (短い帯域ノイズの粒)"""
    out = np.zeros_like(t)
    for t0 in times:
        i0 = int(t0 * SR)
        n = int(dur * SR)
        seg = band_noise(n, lo, hi) * np.exp(-np.arange(n) / (n * 0.25)) * rng.uniform(0.5, 1.0)
        out[i0:i0 + n] += seg[:max(0, len(out) - i0)]
    return out * amp


def bubbles(t, rate, lo, hi, dur=0.05, amp=1.0):
    out = np.zeros_like(t)
    n = int(rate * t[-1])
    for _ in range(n):
        t0 = rng.uniform(0, t[-1] - dur)
        f0 = rng.uniform(lo, hi)
        i0 = int(t0 * SR)
        k = np.arange(int(dur * SR)) / SR
        f = f0 * (1 + 2.5 * k / dur)
        seg = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-k / (dur * 0.35)) * rng.uniform(0.3, 1.0)
        out[i0:i0 + len(seg)] += seg[:len(out) - i0]
    return out * amp


def creak(t, f0, f1, rough=0.6):
    """きしみ: 周期のゆらぐ、のこぎり波に近い低い音"""
    f = np.interp(t, [0, t[-1]], [f0, f1]) * (1 + 0.15 * band_noise(len(t), 3, 30))
    ph = np.cumsum(f) / SR
    saw = 2 * (ph % 1.0) - 1
    return lowpass(saw * (1 - rough) + band_noise(len(t), 300, 2500) * rough * np.abs(np.sin(2 * np.pi * ph)), 2200)


# ---------------------------------------------------------------- 珊瑚憑き

def thrall_ambient():
    t = t_axis(2.2)
    c = creak(t, 70, 52, 0.5) * env(t, [(0, 0), (0.4, 0.6), (1.2, 0.8), (2.0, 0.2), (2.2, 0)])
    hum = np.sin(2 * np.pi * 46 * t) * env(t, [(0, 0), (0.6, 0.3), (1.8, 0.2), (2.2, 0)])
    k = clicks(t, rng.uniform(0.1, 2.0, 9), 2000, 6000, amp=0.35)
    return finish(reverb(c * 0.7 + hum + k, 1.6, 0.35), -6.0)


def thrall_hurt():
    t = t_axis(0.6)
    snap = clicks(t, [0.0, 0.03, 0.07, 0.12], 1200, 7000, dur=0.03)
    grind = creak(t, 120, 70, 0.7) * env(t, [(0, 0), (0.02, 0.8), (0.35, 0.2), (0.6, 0)])
    return finish(reverb(snap + grind * 0.6, 0.8, 0.25), -3.0)


def thrall_death():
    t = t_axis(1.8)
    crumble = clicks(t, np.sort(rng.uniform(0.0, 1.0, 40)) ** 1.5, 800, 6000, dur=0.025, amp=0.8)
    thud = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 1.8], [80, 40])) / SR) * env(t, [(0, 0), (0.75, 0), (0.8, 1.0), (1.4, 0.1), (1.8, 0)])
    sand = lowpass(band_noise(len(t), 200, 2000), 1200) * env(t, [(0, 0), (0.8, 0.0), (0.85, 0.6), (1.6, 0)])
    groan = creak(t, 60, 35, 0.4) * env(t, [(0, 0), (0.2, 0.5), (0.8, 0.3), (1.2, 0)])
    return finish(reverb(crumble + thud * 0.8 + sand * 0.5 + groan * 0.5, 1.8, 0.35), -3.0)


def thrall_thrust():
    t = t_axis(0.45)
    whoosh = band_noise(len(t), 400, 3500) * env(t, [(0, 0), (0.12, 1.0), (0.25, 0.3), (0.45, 0)])
    return finish(lowpass(whoosh, 2500), -4.0)


def thrall_petrify():
    t = t_axis(1.6)
    grind = creak(t, 45, 30, 0.8) * env(t, [(0, 0), (0.3, 0.8), (1.2, 0.6), (1.6, 0)])
    trapped = bubbles(t, 20, 300, 900, amp=0.4) * env(t, [(0, 1), (0.8, 0.5), (1.3, 0), (1.6, 0)])
    return finish(reverb(grind + trapped, 2.0, 0.4), -4.0)


def thrall_wake():
    t = t_axis(1.2)
    crack = clicks(t, [0.0, 0.05, 0.18, 0.22, 0.4, 0.43, 0.6], 900, 7000, dur=0.04, amp=1.0)
    debris = clicks(t, rng.uniform(0.1, 1.0, 25), 2500, 8000, dur=0.01, amp=0.4)
    breath = band_noise(len(t), 150, 900) * env(t, [(0, 0), (0.6, 0), (0.8, 0.5), (1.2, 0)])
    return finish(reverb(crack + debris + breath * 0.6, 1.4, 0.35), -3.0)


def thrall_stone_tap():
    t = t_axis(0.35)
    ring = sum(np.sin(2 * np.pi * f * t) * a for f, a in ((610, 1.0), (1530, 0.5), (2710, 0.3))) * np.exp(-t / 0.05)
    tick = clicks(t, [0.0], 1500, 6000, dur=0.01)
    return finish(ring * 0.6 + tick, -6.0)


# ---------------------------------------------------------------- 磯走り

def scuttler_ambient():
    t = t_axis(0.9)
    times = np.concatenate([0.05 + np.arange(5) * 0.07, 0.55 + np.arange(3) * 0.06])
    return finish(reverb(clicks(t, times, 2500, 8000, dur=0.008), 0.5, 0.2), -8.0)


def scuttler_hurt():
    t = t_axis(0.35)
    squeak = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.35], [1900, 1300])) / SR) * env(t, [(0, 0), (0.02, 1), (0.3, 0)])
    return finish(squeak * 0.4 + clicks(t, [0.0, 0.02], 2000, 8000, dur=0.015), -4.0)


def scuttler_death():
    t = t_axis(0.7)
    crack = clicks(t, [0.0, 0.03, 0.09, 0.15, 0.2], 1200, 8000, dur=0.03)
    hiss = band_noise(len(t), 3000, 9000) * env(t, [(0, 0), (0.1, 0.4), (0.6, 0)])
    return finish(reverb(crack + hiss * 0.4, 0.6, 0.25), -4.0)


def scuttler_step():
    t = t_axis(0.12)
    return finish(clicks(t, [0.0, 0.04], 2500, 9000, dur=0.01), -10.0)


def scuttler_flip():
    t = t_axis(0.6)
    clack = clicks(t, [0.0, 0.12, 0.2], 600, 4000, dur=0.04)
    flail = clicks(t, 0.25 + np.arange(8) * 0.04, 2500, 8000, dur=0.008, amp=0.6)
    return finish(reverb(clack + flail, 0.5, 0.2), -3.0)


# ---------------------------------------------------------------- 泡吹き

def puffer_ambient():
    t = t_axis(1.6)
    gurgle = bubbles(t, 18, 150, 500, dur=0.07) * env(t, [(0, 0), (0.2, 1), (1.3, 0.8), (1.6, 0)])
    low = lowpass(band_noise(len(t), 60, 400), 300) * env(t, [(0, 0), (0.5, 0.4), (1.6, 0)])
    return finish(reverb(gurgle + low * 0.4, 1.0, 0.3), -8.0)


def puffer_inflate():
    t = t_axis(0.7)
    stretch = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.5, 0.7], [90, 260, 240])) / SR) * env(t, [(0, 0), (0.05, 0.7), (0.5, 1), (0.7, 0)])
    air = band_noise(len(t), 300, 2500) * env(t, [(0, 0), (0.1, 0.6), (0.5, 0.3), (0.7, 0)])
    creaks = creak(t, 140, 220, 0.5) * 0.3
    return finish(reverb(stretch * 0.6 + air * 0.5 + creaks, 0.6, 0.25), -4.0)


def puffer_deflate():
    t = t_axis(0.8)
    hiss = band_noise(len(t), 600, 5000) * env(t, [(0, 0), (0.05, 1), (0.6, 0.3), (0.8, 0)])
    sag = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.8], [220, 80])) / SR) * env(t, [(0, 0), (0.1, 0.5), (0.8, 0)])
    return finish(lowpass(hiss * 0.5 + sag * 0.5, 3500), -5.0)


def puffer_shoot():
    t = t_axis(0.5)
    blorp = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.08, 0.3], [180, 520, 300])) / SR) * env(t, [(0, 0), (0.02, 1), (0.25, 0.2), (0.5, 0)])
    return finish(reverb(blorp + bubbles(t, 40, 500, 1400, amp=0.4), 0.6, 0.25), -3.0)


def puffer_hurt():
    t = t_axis(0.4)
    sq = creak(t, 260, 180, 0.4) * env(t, [(0, 0), (0.02, 1), (0.3, 0.2), (0.4, 0)])
    return finish(sq + bubbles(t, 30, 400, 1200, amp=0.3), -4.0)


def puffer_death():
    t = t_axis(1.4)
    hiss = band_noise(len(t), 500, 4000) * env(t, [(0, 0), (0.05, 0.8), (0.8, 0.2), (1.4, 0)])
    sink = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 1.4], [200, 60])) / SR) * env(t, [(0, 0), (0.1, 0.6), (1.2, 0.1), (1.4, 0)])
    return finish(reverb(lowpass(hiss * 0.5, 3000) + sink * 0.6 + bubbles(t, 12, 200, 600, amp=0.3), 1.2, 0.3), -4.0)


def bubble_pop():
    t = t_axis(0.25)
    pop = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.03, 0.25], [900, 1700, 1500])) / SR) * np.exp(-t / 0.03)
    return finish(pop + clicks(t, [0.0], 3000, 9000, dur=0.006, amp=0.5), -6.0)


SOUNDS = {
    "coral_thrall_ambient": thrall_ambient, "coral_thrall_hurt": thrall_hurt, "coral_thrall_death": thrall_death,
    "coral_thrall_thrust": thrall_thrust, "coral_thrall_petrify": thrall_petrify, "coral_thrall_wake": thrall_wake,
    "coral_thrall_stone_tap": thrall_stone_tap,
    "reef_scuttler_ambient": scuttler_ambient, "reef_scuttler_hurt": scuttler_hurt, "reef_scuttler_death": scuttler_death,
    "reef_scuttler_step": scuttler_step, "reef_scuttler_flip": scuttler_flip,
    "bubble_puffer_ambient": puffer_ambient, "bubble_puffer_inflate": puffer_inflate, "bubble_puffer_deflate": puffer_deflate,
    "bubble_puffer_shoot": puffer_shoot, "bubble_puffer_hurt": puffer_hurt, "bubble_puffer_death": puffer_death,
    "bubble_pop": bubble_pop,
}


# ---------------------------------------------------------------- 絵

def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def bubble_texture():
    n = 32
    img = np.zeros((n, n, 4), np.uint8)
    yy, xx = np.mgrid[0:n, 0:n]
    r = np.hypot(xx - 15.5, yy - 15.5)
    rim = (r <= 14) & (r > 11.5)
    inner = r <= 11.5
    img[inner] = hexc("#8BEEF8", 70)
    img[rim] = hexc("#C8F8FF", 200)
    hl = np.hypot(xx - 10, yy - 10) <= 3
    img[hl & inner] = hexc("#FFFFFF", 230)
    img[(np.hypot(xx - 20, yy - 21) <= 1.5) & inner] = hexc("#DDFDFF", 160)
    return img


def shard_icon():
    """潮殻の殻片: 青緑の甲羅の板のかけら、真珠層の白い筋"""
    img = np.zeros((32, 32, 4), np.uint8)
    pts = [(6, 20), (12, 6), (22, 4), (27, 13), (24, 25), (13, 28)]
    from PIL import ImageDraw
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon(pts, fill=hexc("#2E6F73"), outline=hexc("#173E42"))
    d.line([(10, 18), (16, 10), (23, 8)], fill=hexc("#E9F3EE"), width=2)
    d.line([(12, 24), (20, 18), (25, 15)], fill=hexc("#9FD3CC"), width=1)
    d.line([(13, 7), (21, 5)], fill=hexc("#6AB0B0"), width=1)
    return np.array(im) if img is not None else img


def dried_shell_icon():
    """潮干しの貝: 干した二枚貝、放射の筋"""
    from PIL import ImageDraw
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.pieslice([4, 6, 28, 30], 180, 360, fill=hexc("#C9A37A"), outline=hexc("#6E4E33"))
    d.rectangle([4, 18, 28, 22], fill=hexc("#A9785A"), outline=hexc("#6E4E33"))
    for x in (8, 12, 16, 20, 24):
        d.line([(16, 18), (x, 8 if x == 16 else 10)], fill=hexc("#8C6243"), width=1)
    d.line([(10, 25), (22, 25)], fill=hexc("#E9D8BE"), width=2)
    return np.array(im)


def bubble_sac_icon():
    """泡袋: うすい膜の袋に泡が透ける"""
    from PIL import ImageDraw
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([5, 7, 27, 29], fill=hexc("#7FD0D8", 190), outline=hexc("#2A7F8A"))
    d.rectangle([13, 3, 19, 8], fill=hexc("#3F8189"), outline=hexc("#24565F"))
    for cx, cy, r in ((12, 16, 3), (20, 20, 2), (15, 23, 2), (21, 13, 1)):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=hexc("#E8FDFF"))
    d.point([(10, 12), (11, 11)], fill=hexc("#FFFFFF"))
    return np.array(im)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUNDS.items():
        data = fn().astype(np.float32)
        write_ogg(OUT / f"{name}.ogg", data)
        print(name, "%.2f s" % (len(data) / SR))
    Image.fromarray(bubble_texture(), "RGBA").save(TEX / "entity/bubble_shot.png")
    Image.fromarray(shard_icon(), "RGBA").save(TEX / "item/tide_shell_shard.png")
    Image.fromarray(dried_shell_icon(), "RGBA").save(TEX / "item/tide_dried_shell.png")
    Image.fromarray(bubble_sac_icon(), "RGBA").save(TEX / "item/bubble_sac.png")


if __name__ == "__main__":
    main()
