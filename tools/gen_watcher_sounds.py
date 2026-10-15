"""序章「見る者」の効果音を合成して .ogg で書き出す (既存の音は使わない)。

使い方: python tools/gen_watcher_sounds.py
出力先: src/main/resources/assets/skyabyss/sounds/prologue/
同じ乱数の種を使うので、何度実行しても同じ音ができる (今の src の音の生成元)。

- watcher_presence.ogg : 見る者に気づいている間の、頭の中で鳴るようなかすかな耳鳴り。
                         高い細い音がゆっくりうなり、ずっと下で空気の圧がかすかに揺れる (8 秒で切れ目なくループ)
- watcher_fade.ogg     : 見る者が薄れて消えるときの、息を吸い込むような短い音。
                         大きな音や鋭い立ち上がりは入れない (ジャンプスケアにしない)。最後は無音へ溶ける
"""
from pathlib import Path

import numpy as np
import soundfile as sf

SR = 44100
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss/sounds/prologue"
rng = np.random.default_rng(20261016)


def t_axis(seconds):
    return np.arange(int(SR * seconds)) / SR


def band_noise(n, lo, hi, soft=0.25):
    """lo〜hi Hz を通す、なめらかな帯域のノイズ (FFT で作るので長さ n で周期的)"""
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    rise = 1 / (1 + np.exp(-(np.log(f + 1e-9) - np.log(lo)) / soft))
    fall = 1 / (1 + np.exp((np.log(f + 1e-9) - np.log(hi)) / soft))
    out = np.fft.irfft(spec * rise * fall, n)
    return out / (np.max(np.abs(out)) + 1e-9)


def reverb(x, seconds=3.0, mix=0.35, circular=False):
    """指数減衰ノイズの残響。circular=True ならループ用に周回させる"""
    m = int(SR * seconds)
    ir = rng.standard_normal(m) * np.exp(-np.arange(m) / (SR * seconds / 6.0))
    ir = np.fft.irfft(np.fft.rfft(ir) * np.exp(-np.fft.rfftfreq(m, 1 / SR) / 3000), m)
    ir /= np.sqrt(np.sum(ir ** 2))
    if circular:
        n = len(x)
        fold = np.zeros(n)
        for i in range(0, m, n):
            seg = ir[i:i + n]
            fold[:len(seg)] += seg
        wet = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(fold), n)
    else:
        n = len(x) + m
        wet = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)
        x = np.concatenate([x, np.zeros(m)])
    return (1 - mix) * x + mix * wet * 0.6


def normalize(x, peak_db):
    return x / (np.max(np.abs(x)) + 1e-9) * 10 ** (peak_db / 20)


def presence():
    seconds = 8.0  # 周波数はすべて 1/8 Hz の倍数 (8 秒で継ぎ目なくループ)
    t = t_axis(seconds)
    n = len(t)
    # 耳鳴り: 5200 Hz と 5200.5 Hz が 2 秒周期でうなる。ごくわずかに揺れる (0.25 Hz の位相の揺れ)
    wobble = 0.6 * np.sin(2 * np.pi * 0.25 * t)
    ring = (np.sin(2 * np.pi * 5200.0 * t + wobble) + 0.8 * np.sin(2 * np.pi * 5200.5 * t)) * 0.5
    # その少し下に、もっと細い 2 本目 (不協和でも主張しない)
    ring2 = np.sin(2 * np.pi * 3687.5 * t - wobble * 0.5) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.125 * t)) * 0.25
    # 下の方の空気の圧: 36 Hz 前後がゆっくり膨らむ
    pressure = (np.sin(2 * np.pi * 36.0 * t) + 0.7 * np.sin(2 * np.pi * 36.25 * t)) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.125 * t))
    # 遠い空気のさざめき (帯域ノイズ、ゆっくり出入り)
    air = band_noise(n, 250, 1100) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.25 * t + 1.0))
    dry = ring * 0.10 + ring2 * 0.06 + pressure * 0.35 + air * 0.10
    return normalize(reverb(dry, 2.5, 0.3, circular=True), -6.0)


def fade():
    t = t_axis(2.6)
    n = len(t)
    # 吸い込む息: 帯域ノイズが少しずつ高く・強くなり、ふっと途切れる (途切れは 0.25 秒かけてなめらかに)
    swell = np.clip(t / 1.7, 0, 1) ** 2.2
    cut = np.clip((2.0 - t) / 0.3, 0, 1)
    breath_lo = band_noise(n, 300, 1400) * (1 - swell * 0.5)
    breath_hi = band_noise(n, 1500, 5000) * swell
    breath = (breath_lo * 0.6 + breath_hi * 0.5) * swell * cut
    # その奥で、細い音が下がっていく (900 Hz → 610 Hz)
    f = 900 * (610 / 900) ** np.clip(t / 2.0, 0, 1)
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.clip(t / 0.8, 0, 1) * cut * 0.18
    dry = breath + tone
    out = reverb(dry, 2.0, 0.4)
    # 最後の残響も 0.4 秒でなめらかに消す
    k = int(SR * 0.4)
    out[-k:] *= np.linspace(1, 0, k) ** 2
    return normalize(out, -4.0)


def write_ogg(path, data):
    """少しずつ書き込む (Windows 版の libsndfile は長い音を一度に書くとスタックが溢れるため)"""
    with sf.SoundFile(path, "w", SR, 1, format="OGG", subtype="VORBIS") as f:
        for i in range(0, len(data), 4096):
            f.write(data[i:i + 4096])


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, make in [("watcher_presence", presence), ("watcher_fade", fade)]:
        data = make().astype(np.float32)
        write_ogg(OUT / f"{name}.ogg", data)
        print(f"{name}.ogg  {len(data) / SR:.1f}s  peak {np.max(np.abs(data)):.2f}")


if __name__ == "__main__":
    main()
