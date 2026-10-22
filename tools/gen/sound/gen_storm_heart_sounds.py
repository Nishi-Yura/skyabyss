"""雷翼の心臓 (最終ボス) の効果音を合成する (独自合成。乱数の種は固定。01 の「既存の音を使わない」)。

使い方: python tools/gen/sound/gen_storm_heart_sounds.py
出力先: src/main/resources/assets/skyabyss_storm_heart/sounds/*.ogg と sounds.json
        (共有の assets/skyabyss/sounds.json と、伝承の担当の assets/skyabyss_storm には触らないよう、心臓だけの名前空間に置く。
         登録の名前は skyabyss:storm_heart_<名前>、音の場所は skyabyss_storm_heart:<名前>。StormHeartSetup)

技ごとに別の音にする (12 の 5.3節: 予告は色の粒に加えて、技ごとに別の音)。
- beat     : 鼓動。乾いた二拍と、遠い雷の残響
- roar     : 段階の変わり目。嵐の風の咆哮と、翼の布が裂ける音
- summon   : 招雷の溜め。上で雲が鳴り、静電がはじける
- strike   : 落雷 (輪へ・柱へ)。鋭い割れる音と低い轟き
- blade    : 風の刃の溜め。高まる笛の音
- chain    : 雷の鎖の溜め。うなる電気の唸り
- split    : 翼が裂ける。銅が鳴り、心臓が落ちる
- wingbeat : 羽ばたきの溜め。低い風を吸い込む音と、打ち下ろす重い風
- charge   : 帯電の床。床を這う火花のジジという唸り
- feather  : 羽根の雨。軽い羽の擦れと、はじける静電
- flash    : 閃光の溜め。光を吸い込んで高まる音 (2 秒で頂点)
- web      : 雷の網。回る電気の唸り (うねる)
- barrage  : 招雷の連打の溜め。短い三つの鳴り
- death    : 心臓が止まる。最後の鼓動と、嵐が遠のく音
"""
import json
from pathlib import Path

import numpy as np

import gen_sky_sounds as G
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg
from gen_tide_sounds import lowpass

ROOT = Path(__file__).resolve().parent.parent.parent.parent
OUT = ROOT / "src/main/resources/assets/skyabyss_storm_heart/sounds"
JSON = ROOT / "src/main/resources/assets/skyabyss_storm_heart/sounds.json"
G.rng = np.random.default_rng(20261024)
rng = G.rng


def tone(t, f, harmonics=1, decay=None):
    ph = 2 * np.pi * np.cumsum(np.broadcast_to(f, t.shape)) / SR
    x = sum(np.sin(h * ph) / h ** 1.2 for h in range(1, harmonics + 1))
    if decay:
        x = x * np.exp(-t / decay)
    return x


def thump(t, t0, f0=60, f1=34, amp=1.0):
    k = np.clip(t - t0, 0, None)
    f = f1 + (f0 - f1) * np.exp(-k / 0.08)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-k / 0.18) * (t >= t0) * amp


def crackle(t, rate, amp=0.5, lo=2000, hi=9000):
    """静電のはじける粒 (ばらばらの短いクリック)"""
    n = len(t)
    x = np.zeros(n)
    count = int(rate * t[-1])
    for _ in range(count):
        i = rng.integers(0, n - 200)
        L = rng.integers(40, 200)
        x[i:i + L] += rng.uniform(-1, 1, L) * np.exp(-np.arange(L) / (L / 4)) * rng.uniform(0.3, 1.0)
    return x * amp


def bolt_crack(t, t0, amp=1.0):
    n = len(t)
    k = np.clip(t - t0, 0, None)
    snap = band_noise(n, 1500, 10000) * np.exp(-k / 0.05) * (t >= t0)
    rumble = lowpass(band_noise(n, 30, 400), 250) * np.exp(-k / 0.9) * (t >= t0) * 1.4
    return (snap + rumble) * amp


def buzz(t, f, amp=1.0):
    """電気の唸り (のこぎり波に近い倍音)"""
    return tone(t, f, 9) * amp


def beat():
    t = t_axis(1.6)
    x = thump(t, 0.02, 80, 40) + thump(t, 0.3, 70, 36, 0.7)
    tail = lowpass(band_noise(len(t), 30, 300), 200) * env(t, [(0, 0), (0.4, 0.15), (1.6, 0)])
    return finish(reverb(lowpass(x, 400) + tail + crackle(t, 3, 0.05), 1.6, 0.3), -4.0)


def roar():
    t = t_axis(3.2)
    n = len(t)
    wind = lowpass(band_noise(n, 80, 3000), 2500) * env(t, [(0, 0), (0.4, 0.7), (1.4, 1.0), (2.6, 0.7), (3.2, 0)])
    f0 = np.interp(t, [0, 0.8, 2.0, 3.2], [90, 140, 110, 70])
    howl = lowpass(tone(t, f0 * (1 + 0.02 * np.sin(2 * np.pi * 4 * t)), 8), 1400) * env(t, [(0, 0), (0.5, 0.6), (2.4, 0.6), (3.2, 0)]) * 0.6
    tear = band_noise(n, 2000, 7000) * env(t, [(0, 0), (1.6, 0), (1.65, 0.8), (2.1, 0.1), (2.4, 0)]) * 0.5
    return finish(reverb(wind + howl + tear + bolt_crack(t, 2.6, 0.5), 3.0, 0.4))


def summon():
    t = t_axis(1.6)
    n = len(t)
    cloud = lowpass(band_noise(n, 40, 500), 350) * env(t, [(0, 0), (0.6, 0.6), (1.2, 1.0), (1.6, 0)])
    stat = crackle(t, 40, 0.4) * env(t, [(0, 0.2), (1.2, 1.0), (1.6, 0)])
    rise = tone(t, np.interp(t, [0, 1.2], [400, 1600]), 2) * env(t, [(0, 0), (1.1, 0.25), (1.2, 0), (1.6, 0)])
    return finish(reverb(cloud + stat + rise, 1.4, 0.3))


def strike():
    t = t_axis(1.8)
    return finish(reverb(bolt_crack(t, 0.0) + crackle(t, 30, 0.3) * env(t, [(0, 1), (0.6, 0)]), 2.2, 0.4))


def blade():
    t = t_axis(1.8)
    n = len(t)
    f = np.interp(t, [0, 1.2, 1.8], [900, 2600, 1800])
    whistle = tone(t, f * (1 + 0.01 * np.sin(2 * np.pi * 7 * t)), 2) * env(t, [(0, 0), (0.3, 0.3), (1.2, 0.8), (1.5, 0.3), (1.8, 0)]) * 0.4
    swish = band_noise(n, 1500, 7000) * env(t, [(0, 0), (1.2, 0.1), (1.3, 1.0), (1.8, 0)]) * 0.6
    return finish(reverb(whistle + swish, 1.2, 0.3))


def chain():
    t = t_axis(1.6)
    hum = buzz(t, np.interp(t, [0, 1.0], [70, 120]), 0.5) * env(t, [(0, 0), (0.2, 0.5), (1.0, 1.0), (1.05, 0.2), (1.6, 0)])
    return finish(reverb(lowpass(hum, 3000) + bolt_crack(t, 1.0, 0.6), 1.4, 0.3))


def split():
    t = t_axis(2.4)
    clang = sum(tone(t, f, 1, d) * a for f, d, a in ((196, 1.2, 0.5), (392.5, 0.8, 0.3), (623, 0.5, 0.2)))
    fall = thump(t, 0.35, 90, 30, 1.2)
    stat = crackle(t, 20, 0.3) * env(t, [(0, 1), (2.4, 0)])
    return finish(reverb(clang + fall + stat, 2.2, 0.4))


def wingbeat():
    t = t_axis(2.4)
    n = len(t)
    suck = lowpass(band_noise(n, 60, 1500), 1000) * env(t, [(0, 0), (1.4, 0.8), (1.5, 0.1), (2.4, 0)]) * 0.6
    whump = thump(t, 1.5, 70, 28, 1.4) + lowpass(band_noise(n, 40, 800), 500) * env(t, [(0, 0), (1.5, 0), (1.55, 1.0), (2.2, 0)])
    return finish(reverb(suck + whump, 1.8, 0.35))


def charge():
    t = t_axis(2.2)
    hum = buzz(t, 50 + 6 * np.sin(2 * np.pi * 3 * t), 0.35) * env(t, [(0, 0), (0.6, 0.6), (1.5, 1.0), (2.2, 0.3)])
    zz = crackle(t, 90, 0.35) * env(t, [(0, 0), (1.5, 1.0), (2.2, 0.4)])
    return finish(reverb(lowpass(hum, 2500) + zz, 1.2, 0.25))


def feather():
    t = t_axis(1.6)
    n = len(t)
    rustle = band_noise(n, 3000, 9000) * (0.5 + 0.5 * np.sin(2 * np.pi * 11 * t)) * env(t, [(0, 0), (0.4, 0.6), (1.2, 0.5), (1.6, 0)]) * 0.5
    return finish(reverb(rustle + crackle(t, 15, 0.3), 1.2, 0.3))


def flash():
    t = t_axis(2.4)
    n = len(t)
    f = np.interp(t, [0, 2.0], [200, 2400])
    rise = tone(t, f, 3) * env(t, [(0, 0), (0.3, 0.2), (1.95, 0.9), (2.0, 0), (2.4, 0)]) * 0.5
    inhale = band_noise(n, 500, 6000) * env(t, [(0, 0), (1.9, 0.7), (2.0, 0), (2.4, 0)]) * 0.4
    burst = band_noise(n, 2000, 12000) * env(t, [(0, 0), (2.0, 0), (2.01, 1.0), (2.4, 0)])
    return finish(reverb(rise + inhale + burst, 1.8, 0.35))


def web():
    t = t_axis(2.4)
    wob = 0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 2.4], [2, 6])) / SR)
    hum = buzz(t, 90 + 30 * wob, 0.4) * env(t, [(0, 0), (0.4, 0.6), (1.5, 1.0), (2.4, 0)])
    return finish(reverb(lowpass(hum, 3500) + crackle(t, 50, 0.3) * wob, 1.5, 0.3))


def barrage():
    t = t_axis(1.0)
    out = np.zeros_like(t)
    for k, f in enumerate((1200, 1500, 1800)):
        t0 = k * 0.12
        kk = np.clip(t - t0, 0, None)
        out += np.sin(2 * np.pi * f * kk) * np.exp(-kk / 0.08) * (t >= t0) * 0.5
    return finish(reverb(out + crackle(t, 20, 0.2), 1.0, 0.3), -3.0)


def death():
    t = t_axis(4.5)
    n = len(t)
    beats = thump(t, 0.05, 80, 36) + thump(t, 0.6, 70, 32, 0.6) + thump(t, 1.8, 60, 28, 0.35)
    calm = lowpass(band_noise(n, 60, 2000), 1500) * env(t, [(0, 0.8), (1.5, 0.6), (4.5, 0)])
    ring = tone(t, 659.3, 2, 2.0) * 0.15 * (t >= 2.4)
    return finish(reverb(beats + calm + ring + bolt_crack(t, 0.0, 0.7), 3.5, 0.45))


SOUNDS = {"beat": beat, "roar": roar, "summon": summon, "strike": strike, "blade": blade, "chain": chain, "split": split,
          "wingbeat": wingbeat, "charge": charge, "feather": feather, "flash": flash, "web": web, "barrage": barrage,
          "death": death}


# 字幕の鍵は伝承の担当 (skyabyss_storm の lang) の名前に合わせる。音の名前と違うものだけ
SUBTITLE = {"wingbeat": "flap", "charge": "floor", "feather": "feathers"}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    table = {}
    for name, fn in SOUNDS.items():
        data = fn().astype(np.float32)
        write_ogg(OUT / f"{name}.ogg", data)
        table[name] = {"subtitle": f"subtitles.skyabyss.storm_heart_{SUBTITLE.get(name, name)}", "sounds": [{"name": f"skyabyss_storm_heart:{name}"}]}
        print(name, "%.1f s" % (len(data) / SR))
    JSON.write_text(json.dumps(table, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
