"""心臓の領域 (石の海・満ち潮・雷の檻・炉心) の宣言の音 (音楽の合図)。独自合成、乱数の種は固定。

使い方: python tools/gen/sound/gen_heart_domain_stings.py
出力: src/main/resources/assets/skyabyss/sounds/mobs/domain_<id>.ogg (各 約 3.6 秒)
      sounds.json の同名の行 (自分の行だけ書き直す) と、skyabyss_mobsounds の字幕 (ja_jp / en_us)。
      登録は registry/MobSounds (DOMAIN_STONE_SEA 〜 DOMAIN_HEARTH_CORE)。
どれも静かに立ち上がり (ジャンプスケアなし)、宣言の 4 秒のあいだに大きくなって、題字が出る頃に最大になる。
- stone_sea    : 深い石の和音 (D2・A2・F3 の低い倍音)、石の打撃、うねる低い風
- rising_tide  : 上へ向かう帯域ノイズの満ち、水中の鐘 (音程が上がる)、泡
- thunder_cage : 金属の網がきしむ唸り (120 Hz の唸り)、稲妻の裂ける音が三度、非整数倍音の金属の余韻
- hearth_core  : 低い炎の唸り、炉の鎚 (非整数倍音の鐘)、はぜる粒
"""
import json
from pathlib import Path

import numpy as np
import soundfile as sf

SR = 44100
ROOT = Path(__file__).resolve().parent.parent.parent.parent
RES = ROOT / "src/main/resources/assets/skyabyss"
OUT = RES / "sounds/mobs"
rng = np.random.default_rng(20261019)
LEN = 3.6


def t_axis(seconds=LEN):
    return np.arange(int(SR * seconds)) / SR


def band_noise(n, lo, hi):
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    mask = (f >= lo) & (f <= hi)
    smooth = np.convolve(mask.astype(float), np.hanning(41) / np.hanning(41).sum(), mode="same")
    out = np.fft.irfft(spec * smooth, n)
    return out / (np.max(np.abs(out)) + 1e-9)


def swell(t, peak=2.6, width=1.3):
    """立ち上がって peak 秒で最大、そのあと減衰"""
    up = np.clip(t / peak, 0, 1) ** 2
    down = np.exp(-np.clip(t - peak, 0, None) / width)
    return up * down


def hit(t, t0, decay):
    k = np.clip(t - t0, 0, None)
    return (t >= t0) * np.exp(-k / decay)


def modal(t, t0, base, ratios, decays, amp=1.0):
    out = np.zeros_like(t)
    for r, d in zip(ratios, decays):
        out += np.sin(2 * np.pi * base * r * np.clip(t - t0, 0, None)) * hit(t, t0, d) * (1 / (1 + 0.4 * len(ratios)))
    return out * amp


def reverb(x, seconds=2.0, mix=0.3):
    m = int(SR * seconds)
    ir = rng.standard_normal(m) * np.exp(-np.arange(m) / (SR * seconds / 6.0))
    ir = np.fft.irfft(np.fft.rfft(ir) * np.exp(-np.fft.rfftfreq(m, 1 / SR) / 3000), m)
    ir /= np.sqrt(np.sum(ir ** 2))
    n = len(x) + m
    wet = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)[:len(x)]
    return x * (1 - mix) + wet * mix


def lowpass(x, fc):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec / (1 + (f / fc) ** 4), len(x))


def finish(x):
    n = len(x)
    x = x * np.minimum(1, np.arange(n) / (SR * 0.05))
    x[-int(SR * 0.25):] *= np.linspace(1, 0, int(SR * 0.25))
    return (x / (np.abs(x).max() + 1e-9) * 0.85).astype(np.float32)


def stone_sea():
    t = t_axis()
    chord = sum(np.sin(2 * np.pi * f * t + p) * a for f, p, a in [(73.4, 0, 1.0), (110.0, 1, 0.8), (174.6, 2, 0.5), (73.9, 3, 0.7), (220.5, 1, 0.25)])
    x = chord * swell(t, 2.4, 1.1) * 0.5
    x += lowpass(band_noise(len(t), 40, 400), 260) * swell(t, 2.0, 1.4) * 0.9
    x += modal(t, 2.5, 82, [1, 1.9, 3.1, 4.6], [0.9, 0.5, 0.3, 0.2], 1.1)
    x += np.sin(2 * np.pi * (55 - 14 * (1 - np.exp(-np.clip(t - 2.5, 0, None) / 0.4))) * t) * hit(t, 2.5, 0.8) * 0.9
    x += band_noise(len(t), 600, 2500) * hit(t, 2.5, 0.12) * 0.35
    return finish(reverb(x, 2.4, 0.32))


def rising_tide():
    t = t_axis()
    n = len(t)
    surge = np.zeros(n)
    steps = 14
    for i in range(steps):
        a, b = i * n // steps, (i + 1) * n // steps
        fc = 180 * (1.0 + 7.0 * i / steps)
        seg = band_noise(n, fc * 0.5, fc * 1.8)
        w = np.zeros(n)
        w[a:b] = np.hanning(b - a)
        surge += seg * w
    x = surge * swell(t, 2.6, 1.0) * 0.8
    f = 330 * (2 ** (np.clip(t, 0, 3.0) / 3.0))
    bell = np.sin(2 * np.pi * np.cumsum(f) / SR) * swell(t, 2.6, 0.7) * 0.35
    x += bell + np.sin(2 * np.pi * np.cumsum(f * 1.5) / SR) * swell(t, 2.6, 0.7) * 0.18
    for _ in range(26):
        t0 = rng.uniform(0.6, 3.3)
        fb = rng.uniform(500, 1400)
        k = np.clip(t - t0, 0, None)
        x += np.sin(2 * np.pi * (fb + 900 * k) * k) * np.exp(-k / 0.03) * (t >= t0) * 0.1
    x += lowpass(band_noise(n, 30, 200), 120) * swell(t, 2.6, 0.9) * 0.8
    return finish(reverb(x, 2.8, 0.4))


def thunder_cage():
    t = t_axis()
    n = len(t)
    hum = (np.sin(2 * np.pi * 120 * t) + 0.7 * np.sin(2 * np.pi * 126 * t) + 0.4 * np.sin(2 * np.pi * 240 * t)) * swell(t, 2.4, 1.2) * 0.35
    x = hum
    x += band_noise(n, 2000, 9000) * swell(t, 2.4, 0.8) * 0.12 * (rng.random(n) > 0.6)
    for t0, amp in [(0.9, 0.55), (1.8, 0.75), (2.6, 1.0)]:
        crack = band_noise(n, 800, 12000) * hit(t, t0, 0.06) * amp
        rumble = lowpass(band_noise(n, 30, 400), 220) * hit(t, t0, 0.5) * amp * 0.9
        ring = modal(t, t0, 880, [1, 2.76, 5.4, 8.93], [0.5, 0.35, 0.2, 0.12], amp * 0.45)
        x += crack + rumble + ring
    return finish(reverb(x, 2.0, 0.28))


def hearth_core():
    t = t_axis()
    n = len(t)
    roar = lowpass(band_noise(n, 50, 900), 500) * swell(t, 2.5, 1.2) * (0.8 + 0.2 * np.sin(2 * np.pi * 3.1 * t))
    x = roar * 0.9
    x += np.sin(2 * np.pi * 49 * t) * swell(t, 2.5, 1.2) * 0.5
    for t0, base, amp in [(1.3, 196, 0.5), (2.5, 147, 1.0)]:
        x += modal(t, t0, base, [1, 2.0, 2.76, 4.07, 5.4], [1.2, 0.8, 0.5, 0.35, 0.2], amp)
        x += band_noise(n, 1500, 7000) * hit(t, t0, 0.04) * amp * 0.45
    for _ in range(60):
        t0 = rng.uniform(0.3, 3.4)
        x += band_noise(n, 2500, 9000) * hit(t, t0, 0.012) * rng.uniform(0.05, 0.14)
    return finish(reverb(x, 2.2, 0.3))


SOUNDS = {
    "domain_stone_sea": (stone_sea, "石の海が広がる", "The Sea of Stone spreads"),
    "domain_rising_tide": (rising_tide, "合わせ貝が開く", "The Matching Shells open"),
    "domain_thunder_cage": (thunder_cage, "雷の檻が閉じる", "The Thunder Cage closes"),
    "domain_hearth_core": (hearth_core, "炉心が灯る", "The Hearth Core ignites"),
}


def write_ogg(path, data):
    with sf.SoundFile(path, "w", SR, 1, format="OGG", subtype="VORBIS") as f:
        for i in range(0, len(data), 4096):
            f.write(data[i:i + 4096])


def write_json_rows():
    path = RES / "sounds.json"
    rows = json.loads(path.read_text(encoding="utf-8"))
    for name in SOUNDS:
        rows[name] = {"subtitle": f"subtitles.skyabyss.{name}", "sounds": [{"name": f"skyabyss:mobs/{name}", "attenuation_distance": 64}]}
    path.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for lang, idx in (("ja_jp", 1), ("en_us", 2)):
        lp = RES.parent / "skyabyss_mobsounds/lang" / f"{lang}.json"
        data = json.loads(lp.read_text(encoding="utf-8"))
        for name, row in SOUNDS.items():
            data[f"subtitles.skyabyss.{name}"] = row[idx]
        lp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (fn, _, _) in SOUNDS.items():
        data = np.nan_to_num(fn()).astype(np.float32)
        assert np.abs(data).max() > 0.05, name
        write_ogg(OUT / f"{name}.ogg", data)
        print(f"{name:22s} {len(data) / SR:.2f} s  peak {np.abs(data).max():.2f}  rms {np.sqrt(np.mean(data ** 2)):.3f}")
    write_json_rows()


if __name__ == "__main__":
    main()
