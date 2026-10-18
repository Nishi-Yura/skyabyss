"""二章「引く海」の音を合成して .ogg で書き出す (既存の音は使わない)。

使い方: python tools/gen_ch2_sounds.py
出力先: src/main/resources/assets/skyabyss_ch2/sounds/
同じ乱数の種を使うので、何度実行しても同じ音ができる。

- conch_far.ogg   : 遠い貝の音 (約 4.5 秒)。ゆっくり立ち上がる低い管の音 (基音 185 Hz に倍音、わずかな音程のゆれ) が、水の上を渡って遠くで消える。
                    予兆の 2 拍目と、潮見の貝を吹く音
- ebb_recede.ogg  : 海が引いていく音 (約 12 秒)。波の砕ける帯域のノイズが、ゆっくり沖へ遠ざかる (音が落ち、こもる)。ときどき小石を引く細かい音
- ebb_return.ogg  : 潮が戻る音 (約 9 秒)。低い満ちる音が近づいてきて、静かに岸を洗う
"""
from pathlib import Path

import numpy as np
import soundfile as sf

SR = 44100
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss_ch2/sounds"
rng = np.random.default_rng(20261018)


def t_axis(seconds):
    return np.arange(int(SR * seconds)) / SR


def band_noise(n, lo, hi, soft=0.3):
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    rise = 1 / (1 + np.exp(-(np.log(f + 1e-9) - np.log(lo)) / soft))
    fall = 1 / (1 + np.exp((np.log(f + 1e-9) - np.log(hi)) / soft))
    out = np.fft.irfft(spec * rise * fall, n)
    return out / (np.max(np.abs(out)) + 1e-9)


def lowpass(x, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec / np.sqrt(1 + (f / hz) ** 4), len(x))


def reverb(x, seconds=2.5, mix=0.4):
    m = int(SR * seconds)
    ir = rng.standard_normal(m) * np.exp(-np.arange(m) / (SR * seconds / 6.0))
    ir = np.fft.irfft(np.fft.rfft(ir) * np.exp(-np.fft.rfftfreq(m, 1 / SR) / 2200), m)
    ir /= np.sqrt(np.sum(ir ** 2))
    n = len(x) + m
    wet = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)
    x = np.concatenate([x, np.zeros(m)])
    return (1 - mix) * x + mix * wet * 0.6


def normalize(x, peak_db):
    return x / (np.max(np.abs(x)) + 1e-9) * 10 ** (peak_db / 20)


def conch_far():
    seconds = 4.5
    t = t_axis(seconds)
    env = np.minimum(1, t / 1.1) ** 2 * np.exp(-np.maximum(0, t - 2.4) * 1.1)
    glide = 185 * (1 + 0.012 * np.sin(2 * np.pi * 0.35 * t) - 0.03 * np.exp(-t * 3))
    phase = 2 * np.pi * np.cumsum(glide) / SR
    tone = np.zeros_like(t)
    for k, amp in enumerate([1.0, 0.55, 0.38, 0.2, 0.12], start=1):
        tone += amp * np.sin(k * phase + k * 0.4)
    breath = band_noise(len(t), 400, 2200) * 0.12 * env
    x = (tone * env + breath)
    x = lowpass(x, 1800)
    x = reverb(x, 3.0, 0.45)
    fade = np.minimum(1, (len(x) - np.arange(len(x))) / (SR * 0.8))
    return normalize(x * fade, -4)


def ebb_recede():
    seconds = 12.0
    n = int(SR * seconds)
    t = t_axis(seconds)
    surf = band_noise(n, 300, 3200)
    # 波の周期: だんだん間が空き、音が小さくなる
    period = 3.0 + t * 0.25
    wave = np.clip(np.sin(2 * np.pi * np.cumsum(1 / period) / SR), 0, 1) ** 1.6
    fall = np.exp(-t / 5.5)
    dull = lowpass(surf, 900) * 0.6 + surf * 0.4 * fall
    x = dull * (0.25 + 0.75 * wave) * (0.15 + fall)
    # 小石を引く細かい音
    grit = band_noise(n, 2500, 7000) * (np.clip(np.sin(2 * np.pi * np.cumsum(1 / (period * 0.5)) / SR + 1.0), 0, 1) ** 3) * 0.18 * fall
    x = x + grit
    fade = np.minimum(1, np.minimum(t / 1.2, (seconds - t) / 2.5))
    return normalize(reverb(x * fade, 2.0, 0.3), -5)


def ebb_return():
    seconds = 9.0
    n = int(SR * seconds)
    t = t_axis(seconds)
    swell = np.minimum(1, t / 5.0) ** 2 * np.exp(-np.maximum(0, t - 6.0) * 0.9)
    body = lowpass(band_noise(n, 120, 1800), 700)
    wash = band_noise(n, 600, 4200) * np.clip(np.sin(2 * np.pi * t / 3.2 - 1.2), 0, 1) ** 1.4 * swell * 0.5
    x = body * swell + wash
    fade = np.minimum(1, np.minimum(t / 1.5, (seconds - t) / 2.0))
    return normalize(reverb(x * fade, 2.2, 0.3), -5)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in [("conch_far", conch_far), ("ebb_recede", ebb_recede), ("ebb_return", ebb_return)]:
        data = fn().astype(np.float32)
        # 一度に渡すと libsndfile (Vorbis) がスタックを使い切って落ちるので、細かく書く
        with sf.SoundFile(str(OUT / (name + ".ogg")), "w", samplerate=SR, channels=1, format="OGG", subtype="VORBIS") as out:
            for i in range(0, len(data), 8192):
                out.write(data[i:i + 8192])
        print("wrote", name)
