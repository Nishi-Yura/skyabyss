"""雲上の巡礼者の効果音を合成して .ogg で書き出す。

使い方: python tools/gen/sound/gen_sky_sounds.py
出力先: src/main/resources/assets/skyabyss/sounds/sky/

- wingbeat.ogg : 巨大な翼のひと羽ばたき（低い風切り音＋腹に響く圧＋金属的な揺らぎ）
- drone.ogg    : 通過中ずっと鳴る低い唸り（8秒で切れ目なくループする）
- call.ogg     : 雲の上から降ってくる、うめき声のような呼び声
"""

# ---------------------------------------------------------------------------
# 上書き防止: 実行すると採用済みの素材を古い版で上書きする。
#   上書きするもの: sounds/sky/*.ogg (採用済みの音)
#   採用版の生成元: 今の src/ が正本
# どうしても動かすときだけ、環境変数 SKYABYSS_ALLOW_REGEN=1 を付ける。一覧は tools/README.md。
# ---------------------------------------------------------------------------
import os as _os
import sys as _sys

if __name__ == "__main__" and _os.environ.get("SKYABYSS_ALLOW_REGEN") != "1":
    _sys.exit("tools/gen/sound/gen_sky_sounds.py は停止中です: 採用済みの素材 (sounds/sky/*.ogg (採用済みの音)) を上書きするため。"
              "採用版の生成元は 今の src/ が正本 です。どうしても実行するときは SKYABYSS_ALLOW_REGEN=1 を付けてください (tools/README.md)。")
from pathlib import Path

import numpy as np
import soundfile as sf

SR = 44100
OUT = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/assets/skyabyss/sounds/sky"
rng = np.random.default_rng(20261015)


def t_axis(seconds):
    return np.arange(int(SR * seconds)) / SR


def band_noise(n, lo, hi, soft=0.25):
    """lo〜hi Hz を通す、なめらかな帯域のノイズ（FFTで作るので長さnで周期的）"""
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    rise = 1 / (1 + np.exp(-(np.log(f + 1e-9) - np.log(lo)) / soft))
    fall = 1 / (1 + np.exp((np.log(f + 1e-9) - np.log(hi)) / soft))
    out = np.fft.irfft(spec * rise * fall, n)
    return out / (np.max(np.abs(out)) + 1e-9)


def reverb(x, seconds=3.5, mix=0.35, circular=False):
    """指数減衰ノイズの残響を畳み込む。circular=True ならループ用に周回させる"""
    m = int(SR * seconds)
    ir = rng.standard_normal(m) * np.exp(-np.arange(m) / (SR * seconds / 6.0))
    # 高音ほど早く減衰させ、大きな空間の響きにする
    ir = np.fft.irfft(np.fft.rfft(ir) * np.exp(-np.fft.rfftfreq(m, 1 / SR) / 2500), m)
    ir /= np.sqrt(np.sum(ir ** 2))
    if circular:
        n = len(x)
        ir_fold = np.zeros(n)
        for i in range(0, m, n):
            seg = ir[i:i + n]
            ir_fold[:len(seg)] += seg
        wet = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(ir_fold), n)
    else:
        n = len(x) + m
        wet = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)
        x = np.concatenate([x, np.zeros(m)])
    return (1 - mix) * x + mix * wet * 0.6


def finish(x, peak_db=-2.0, fade=0.05, trim=True):
    if trim:
        n = int(SR * fade)
        x[-n:] *= np.linspace(1, 0, n)
    return x / (np.max(np.abs(x)) + 1e-9) * 10 ** (peak_db / 20)


def env(t, points):
    """(時刻, 値) の折れ線をなめらかにした包絡線"""
    ts, vs = zip(*points)
    e = np.interp(t, ts, vs)
    k = int(SR * 0.03)
    return np.convolve(e, np.ones(k) / k, mode="same")


def wingbeat():
    t = t_axis(3.6)
    n = len(t)
    # 翼を振り上げる吸い込み → 振り下ろしの圧 → 抜けていく風
    swell = env(t, [(0, 0), (0.9, 0.25), (1.45, 0.85), (1.6, 1.0), (2.2, 0.45), (3.4, 0.0), (3.6, 0)])
    air = band_noise(n, 70, 900) * swell
    push = band_noise(n, 25, 160) * env(t, [(0, 0), (1.4, 0), (1.6, 1.0), (2.4, 0.3), (3.2, 0)])
    # 振り下ろしの瞬間の、腹に響く低い圧
    thump_t = np.clip(t - 1.55, 0, None)
    thump = np.sin(2 * np.pi * (40 * thump_t - 6 * thump_t ** 2)) * np.exp(-thump_t * 3.5) * (t > 1.55)
    # 生き物とも機械ともつかない、金属的で調子の外れた揺らぎ
    shimmer = sum(np.sin(2 * np.pi * f * t + p) for f, p in [(311.1, 0.3), (467.7, 1.1), (733.2, 2.0), (1013.9, 0.7)])
    shimmer *= (0.5 + 0.5 * np.sin(2 * np.pi * 3.1 * t)) * swell * 0.05
    dry = air * 0.55 + push * 0.8 + thump * 0.9 + shimmer
    return finish(reverb(dry, 3.0, 0.3))


def drone():
    seconds = 8.0  # すべての周波数を 1/8 Hz の倍数にして、8秒で継ぎ目なくループさせる
    t = t_axis(seconds)
    n = len(t)
    low = (np.sin(2 * np.pi * 41.0 * t) + np.sin(2 * np.pi * 41.375 * t) * 0.9
           + np.sin(2 * np.pi * 61.5 * t) * 0.35 + np.sin(2 * np.pi * 82.125 * t) * 0.25)
    # ゆっくり膨らんでしぼむ風の層
    wind = band_noise(n, 90, 420) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.25 * t))
    # 遠くで誰かが歌っているような、細く揺れる高い音
    vib = (6.0 / 0.5) * np.sin(2 * np.pi * 0.5 * t)
    sing = np.sin(2 * np.pi * 587.25 * t + vib) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.125 * t)) * 0.06
    sing += np.sin(2 * np.pi * 830.625 * t - vib * 0.7) * (0.5 - 0.5 * np.sin(2 * np.pi * 0.125 * t)) * 0.04
    dry = low * 0.5 + wind * 0.45 + sing
    return finish(reverb(dry, 4.0, 0.4, circular=True), -3.0, trim=False)


def call():
    t = t_axis(6.0)
    # 70Hz から 48Hz へ沈んでいく、喉の奥からのうめき
    f0 = 70 * (48 / 70) ** np.clip(t / 5.0, 0, 1)
    vibrato = 1 + 0.012 * np.clip(t / 3.0, 0, 1) * np.sin(2 * np.pi * 4.3 * t)
    phase = 2 * np.pi * np.cumsum(f0 * vibrato) / SR
    # 母音のような響き。共鳴の山が「お」から「あ」へ動く
    formant1 = 300 + 400 * np.clip(t / 4.0, 0, 1)
    formant2 = 850 + 350 * np.clip(t / 4.0, 0, 1)
    voice = np.zeros_like(t)
    for h in range(1, 24):
        fh = f0 * h
        gain = np.exp(-((fh - formant1) / 160) ** 2) + 0.6 * np.exp(-((fh - formant2) / 220) ** 2) + 0.15 / h
        voice += np.sin(h * phase) * gain
    # 三全音上で、少し遅れてもう一つの声が重なる（不協和で落ち着かない響き）
    second = np.sin(np.sqrt(2) * phase * 3 + 0.5) * np.clip((t - 1.2) / 2.0, 0, 1) * 0.25
    amp = env(t, [(0, 0), (0.8, 0.5), (2.2, 1.0), (4.2, 0.7), (5.6, 0.0), (6.0, 0)])
    dry = (voice / 8 + second) * amp + band_noise(len(t), 150, 2500) * amp * 0.04
    return finish(reverb(dry, 4.5, 0.5))


def write_ogg(path, data):
    """少しずつ書き込む (Windows版の libsndfile は長い音を一度に書くとスタックが溢れるため)"""
    with sf.SoundFile(path, "w", SR, 1, format="OGG", subtype="VORBIS") as f:
        for i in range(0, len(data), 4096):
            f.write(data[i:i + 4096])


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, make in [("wingbeat", wingbeat), ("drone", drone), ("call", call)]:
        data = make().astype(np.float32)
        write_ogg(OUT / f"{name}.ogg", data)
        print(f"{name}.ogg  {len(data) / SR:.1f}s")


if __name__ == "__main__":
    main()
