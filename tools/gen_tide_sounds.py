"""潮殻の巨神まわりの効果音を合成する (独自合成。乱数の種は固定)。

使い方: python tools/gen_tide_sounds.py
出力先: src/main/resources/assets/skyabyss/sounds/tide/

- song.ogg       : 潮殻の巨神の声。水の底で鳴る巨きな角笛のような、丸く低い声に泡がまじる (岩鯨の歌より低く、息が多い)
- surf.ogg       : 遠い潮騒。波が引いていく音 (干潮の刻の知らせと、出現のとき)
- gate.ogg       : 潮門をくぐる。水に飲まれる低いうねりと、抜けたあとの明るいきらめき
- step.ogg       : 海の底を踏む、くぐもった低い響きと砂の揺れ
- back_waves.ogg : 背中の島に寄せる穏やかな波 (12 秒で切れ目なくループする)
"""
from pathlib import Path

import numpy as np

import gen_sky_sounds as G
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss/sounds/tide"
G.rng = np.random.default_rng(20261016)
rng = G.rng


def lowpass(x, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec / (1 + (f / hz) ** 4), len(x))


def bubbles(t, rate, lo, hi, dur=0.05, amp=1.0):
    """泡の粒: 上へ滑る短い正弦波を散らす"""
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


def song():
    t = t_axis(8.0)
    # 低く始まり、ゆっくり上がって、うなりながら沈む (40〜70 Hz)
    f0 = np.interp(t, [0, 1.5, 3.0, 4.5, 6.5, 8.0], [38, 52, 64, 58, 44, 36])
    f0 = np.convolve(f0, np.ones(6000) / 6000, mode="same")
    f0 *= 1 + 0.01 * np.sin(2 * np.pi * 3.1 * t)
    phase = 2 * np.pi * np.cumsum(f0) / SR
    # 角笛: 奇数の倍音を強く、高い倍音はこもらせる (水の中)
    horn = sum(np.sin(h * phase) / (h ** (0.9 if h % 2 else 1.6)) for h in range(1, 14))
    horn = lowpass(horn, 420)
    breath = band_noise(len(t), 120, 900) * 0.18
    amp = env(t, [(0, 0), (1.2, 0.7), (3.0, 1.0), (4.2, 0.75), (5.6, 0.9), (7.4, 0.3), (8.0, 0)])
    dry = (horn * 0.6 + breath) * amp + bubbles(t, 9, 300, 900, amp=0.12) * env(t, [(0, 0), (2, 1), (7, 1), (8, 0)])
    return finish(reverb(dry, 4.5, 0.5))


def surf():
    t = t_axis(7.0)
    n = len(t)
    # 寄せて (0〜2 秒)、長く引いていく。引くときは高い砂のさざめきが細っていく
    roar = band_noise(n, 150, 1400) * env(t, [(0, 0), (1.5, 1.0), (2.4, 0.8), (5.5, 0.25), (7.0, 0)])
    hiss = band_noise(n, 1800, 7000) * env(t, [(0, 0), (1.8, 0.2), (2.6, 0.55), (4.5, 0.3), (6.8, 0.0), (7.0, 0)]) * 0.5
    low = band_noise(n, 40, 160) * env(t, [(0, 0), (1.6, 0.9), (3.5, 0.4), (7, 0)]) * 0.6
    # 遠い音なので、全体を少しこもらせる
    return finish(reverb(lowpass(roar + hiss + low, 2600), 3.0, 0.45), -3.0)


def gate():
    t = t_axis(2.6)
    n = len(t)
    rush = band_noise(n, 80, 2400) * env(t, [(0, 0), (0.25, 1.0), (1.0, 0.8), (1.6, 0.2), (2.6, 0)])
    rush = lowpass(rush, 1600)
    plunge = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.8, 2.6], [140, 60, 50])) / SR) * env(t, [(0, 0), (0.15, 0.8), (1.2, 0.3), (2.6, 0)])
    bub = bubbles(t, 50, 400, 1400, amp=0.25) * env(t, [(0, 0), (0.3, 1), (1.4, 0.6), (2.0, 0)])
    # 抜けたあとのきらめき (高い鈴のような和音)
    shimmer = sum(np.sin(2 * np.pi * f * t) for f in (1568.0, 2093.0, 2637.0)) / 3
    shimmer *= env(t, [(0, 0), (1.3, 0), (1.6, 0.35), (2.6, 0)])
    return finish(reverb(rush * 0.7 + plunge * 0.6 + bub + shimmer * 0.4, 2.0, 0.35))


def step():
    t = t_axis(2.0)
    n = len(t)
    thump = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.3, 2.0], [55, 34, 30])) / SR)
    thump *= env(t, [(0, 0), (0.04, 1.0), (0.5, 0.4), (1.6, 0.05), (2.0, 0)])
    sand = lowpass(band_noise(n, 200, 1800), 900) * env(t, [(0, 0), (0.1, 0.5), (0.8, 0.25), (1.8, 0), (2.0, 0)])
    return finish(reverb(thump + sand * 0.5, 2.5, 0.4), -3.0)


def back_waves():
    seconds = 12.0
    t = t_axis(seconds)
    n = len(t)
    # 4 秒ごとに寄せる波 (周期は 12 秒に合わせる)。帯域のノイズは FFT で作るので周期的につながる
    swell = 0.5 + 0.5 * np.sin(2 * np.pi * t / 4.0 - np.pi / 2)
    swell2 = 0.5 + 0.5 * np.sin(2 * np.pi * t / 6.0 + 1.0)
    wash = band_noise(n, 200, 2200) * (0.25 + 0.75 * swell ** 2)
    fizz = band_noise(n, 2500, 8000) * (swell ** 4) * 0.35
    low = band_noise(n, 40, 180) * (0.4 + 0.6 * swell2) * 0.5
    return finish(reverb(wash + fizz + low, 2.0, 0.3, circular=True), -6.0, trim=False)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in (("song", song), ("surf", surf), ("gate", gate), ("step", step), ("back_waves", back_waves)):
        data = fn().astype(np.float32)
        write_ogg(OUT / f"{name}.ogg", data)
        print(name, "%.1f s" % (len(data) / SR))


if __name__ == "__main__":
    main()
