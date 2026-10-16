"""1章の空の演出 (削られた山頂・雲の切れ間の目) の効果音を合成して .ogg で書き出す (既存の音は使わない)。

使い方: python tools/gen_chapter_sky_sounds.py
出力先: src/main/resources/assets/skyabyss/sounds/sky_ch1/
同じ乱数の種を使うので、何度実行しても同じ音ができる (今の src の音の生成元)。

- eye_presence.ogg : 雲の目が開いている間の、雲の上の深い気配 (8 秒で切れ目なくループ)。
                     ずっと低い所で二つの音 (41.25 Hz と 5 度上) がゆっくりうなり、その上を、巨大なものの呼吸のような
                     こもった空気の帯がゆっくり満ち引きする。ごく小さく、高い所で金の鈴のような細い倍音がゆらぐ。
                     鋭い立ち上がりはない。視線が合うとクライアントがすっと消す (音が止むのが怖い)
- peak_scrape.ogg  : 遠くの山頂を巨大なものが擦っていく音 (約 9 秒)。低い岩の擦れ (うねる低音のノイズ) に、
                     崩れて転がる岩の粒が混じり、遠くでこだまして消える。遠い出来事として聞こえるよう、高い音を落とす
"""
from pathlib import Path

import numpy as np
import soundfile as sf

SR = 44100
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss/sounds/sky_ch1"
rng = np.random.default_rng(20261017)


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


def lowpass(x, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec / np.sqrt(1 + (f / hz) ** 4), len(x))


def reverb(x, seconds=3.0, mix=0.35, circular=False):
    """指数減衰ノイズの残響。circular=True ならループ用に周回させる"""
    m = int(SR * seconds)
    ir = rng.standard_normal(m) * np.exp(-np.arange(m) / (SR * seconds / 6.0))
    ir = np.fft.irfft(np.fft.rfft(ir) * np.exp(-np.fft.rfftfreq(m, 1 / SR) / 2500), m)
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


def eye_presence():
    seconds = 8.0
    t = t_axis(seconds)
    n = len(t)
    # すべての周波数・揺れは 8 秒にちょうど整数回入れて、ループの継ぎ目を消す
    def f(hz):
        return round(hz * seconds) / seconds

    swell = 0.5 - 0.5 * np.cos(2 * np.pi * t / seconds)          # 8 秒で一度、ゆっくり満ちる
    slow = 0.5 - 0.5 * np.cos(2 * np.pi * t * 2 / seconds)       # 4 秒の揺れ
    low = (np.sin(2 * np.pi * f(41.25) * t) * 0.55
           + np.sin(2 * np.pi * f(61.875) * t + 0.7) * 0.35        # 5 度上
           + np.sin(2 * np.pi * f(41.875) * t + 1.9) * 0.25)       # わずかにずれた音でうなり
    low *= 0.65 + 0.35 * swell
    # 巨大なものの呼吸: こもった空気の帯 (200〜700 Hz) がゆっくり満ち引きする
    breath = band_noise(n, 180, 700, soft=0.35) * (0.15 + 0.85 * swell ** 1.6) * 0.45
    # 雲の上の風
    wind = band_noise(n, 60, 260, soft=0.4) * (0.6 + 0.4 * slow) * 0.35
    # 金の鈴のような細い倍音 (ごく小さく、ゆらぐ)
    bell = np.zeros(n)
    for hz, amp, ph in [(1320.0, 0.05, 0.0), (1980.0, 0.035, 1.1), (2640.0, 0.025, 2.3), (3300.0, 0.015, 0.4)]:
        trem = 0.5 + 0.5 * np.sin(2 * np.pi * t * 3 / seconds + ph)
        bell += np.sin(2 * np.pi * f(hz) * t + ph) * amp * trem
    x = low + breath + wind + bell
    x = reverb(x, seconds=4.0, mix=0.45, circular=True)
    return normalize(x, -6.0)


def peak_scrape():
    seconds = 9.0
    t = t_axis(seconds)
    n = len(t)
    # 全体の形: 1 秒かけて近づき、3〜5 秒が最も強く、ゆっくり遠ざかる (鋭い立ち上がりはない)
    env = np.clip(t / 1.2, 0, 1) ** 1.5 * np.exp(-np.clip(t - 4.5, 0, None) / 1.6)
    # 岩の擦れ: 低いノイズを、不規則なうねりで強弱させる (引っかかっては滑る)
    grind = band_noise(n, 35, 420, soft=0.3)
    jerk = lowpass(rng.standard_normal(n), 3.0)
    jerk = 0.55 + 0.45 * np.tanh(jerk / (np.std(jerk) + 1e-9))
    grind *= jerk
    rumble = lowpass(rng.standard_normal(n), 45.0)
    rumble /= np.max(np.abs(rumble)) + 1e-9
    # 崩れて転がる岩の粒: 短いこもった粒を、後ろほどまばらに
    debris = np.zeros(n)
    count = 140
    for _ in range(count):
        at = rng.uniform(1.0, 7.5)
        if rng.random() < (at - 1.0) / 9.0:
            continue
        i = int(at * SR)
        length = int(SR * rng.uniform(0.03, 0.12))
        if i + length >= n:
            continue
        grain = rng.standard_normal(length) * np.exp(-np.arange(length) / (length / 4.0))
        debris[i:i + length] += grain * rng.uniform(0.2, 1.0)
    debris = lowpass(debris, 900.0) * 0.6
    x = (grind * 0.7 + rumble * 0.9 + debris) * env
    # 遠い出来事: 高い音を落とし、こだまを長く
    x = lowpass(x, 1400.0)
    x = reverb(x, seconds=3.5, mix=0.5)
    fade = int(SR * 0.6)
    x[-fade:] *= np.linspace(1, 0, fade) ** 2
    return normalize(x, -3.0)


def write_ogg(path, data):
    """少しずつ書き込む (Windows 版の libsndfile は長い音を一度に書くとスタックが溢れるため)"""
    path.parent.mkdir(parents=True, exist_ok=True)
    data = data.astype(np.float32)
    with sf.SoundFile(str(path), "w", SR, 1, format="OGG", subtype="VORBIS") as f:
        for i in range(0, len(data), 4096):
            f.write(data[i:i + 4096])


def main():
    for name, data in [("eye_presence", eye_presence()), ("peak_scrape", peak_scrape())]:
        write_ogg(OUT / f"{name}.ogg", data)
        print(f"{name}.ogg  {len(data) / SR:.1f}s  peak {np.max(np.abs(data)):.2f}")


if __name__ == "__main__":
    main()
