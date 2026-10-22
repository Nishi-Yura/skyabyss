"""序盤の拍子 (docs/design/17 の担当 3) の効果音を合成して .ogg で書き出す。

使い方: SKYABYSS_ALLOW_REGEN=1 python tools/gen/sound/gen_opening_sounds.py
出力先: src/main/resources/assets/skyabyss/sounds/opening/

- heartbeat.ogg : B0 目覚め。ゆっくり落ち着いていく鼓動 (7 秒)
- bell.ogg      : B0 の遠い鐘 / B2 の鐘の宿りの鐘。風のない朝の小さな音 (6 秒)
- step.ogg      : B6 夜の足音。遠くの巨大な足音 + 地鳴り (3.6 秒。重い低音を厚く。立ち上がりはなめらかで、急な大音は出さない)
- rumble.ogg    : B6 足音の下で続く地鳴り (6 秒)
- wind.ogg      : B0 目覚めの低い風 (9 秒。ゆっくり寄せて引く。音量は小さく、立ち上がりはなめらか)
- song.ogg      : B9 夜の歌。低く長い歌 (40 秒。音量は小さく、5 秒かけて入って 7 秒かけて消える)

ジャンプスケア禁止 (17 の 6 章): どの音も数十〜数百ミリ秒の立ち上がりを持ち、最大音量はそろえてある。
"""

# ---------------------------------------------------------------------------
# 上書き防止 (docs/review/assets_audit.md の S1): 実行すると採用済みの素材を上書きする。
#   上書きするもの: sounds/opening/*.ogg (採用済みの音)
# どうしても動かすときだけ、環境変数 SKYABYSS_ALLOW_REGEN=1 を付ける。一覧は tools/README.md。
# ---------------------------------------------------------------------------
import os as _os
import sys as _sys

if __name__ == "__main__" and _os.environ.get("SKYABYSS_ALLOW_REGEN") != "1":
    _sys.exit("tools/gen/sound/gen_opening_sounds.py は停止中です: 採用済みの素材 (sounds/opening/*.ogg) を上書きするため。"
              "どうしても実行するときは SKYABYSS_ALLOW_REGEN=1 を付けてください (tools/README.md)。")
from pathlib import Path

import numpy as np
import soundfile as sf

SR = 22050
OUT = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/assets/skyabyss/sounds/opening"
rng = np.random.default_rng(20261017)


def t_axis(seconds):
    return np.arange(int(SR * seconds)) / SR


def band_noise(n, lo, hi):
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    gate = ((f >= lo) & (f <= hi)).astype(float)
    # 縁をなめらかに (ぷつっとした帯の端を作らない)
    k = max(1, int(len(f) * 0.002))
    win = np.hanning(k * 2 + 1)
    gate = np.convolve(gate, win / np.sum(win), mode="same")
    out = np.fft.irfft(spec * gate, n)
    return out / (np.max(np.abs(out)) + 1e-9)


def lowpass(x, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    spec *= 1 / (1 + (f / hz) ** 4)
    return np.fft.irfft(spec, len(x))


def reverb(x, seconds=2.5, mix=0.3):
    m = int(SR * seconds)
    ir = rng.standard_normal(m) * np.exp(-np.arange(m) / (SR * seconds / 6.0))
    ir = lowpass(ir, 1800)
    ir /= np.sqrt(np.sum(ir ** 2))
    n = len(x) + m
    wet = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)
    dry = np.concatenate([x, np.zeros(m)])
    return dry * (1 - mix) + wet * mix


def smooth_env(n, attack, release):
    """立ち上がり attack 秒、終わり release 秒の、角のない包絡"""
    env = np.ones(n)
    a = int(SR * attack)
    r = int(SR * release)
    if a > 0:
        env[:a] = np.sin(np.linspace(0, np.pi / 2, a)) ** 2
    if r > 0:
        env[-r:] *= np.cos(np.linspace(0, np.pi / 2, r)) ** 2
    return env


def normalize(x, peak):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def write(name, x, peak=0.8):
    x = normalize(x, peak)
    OUT.mkdir(parents=True, exist_ok=True)
    # 長い音を一度に書くと libsndfile が落ちることがあるので、2 秒ずつ書く
    x = x.astype(np.float32)
    with sf.SoundFile(str(OUT / name), "w", samplerate=SR, channels=1, format="OGG", subtype="VORBIS") as f:
        for i in range(0, len(x), SR * 2):
            f.write(x[i:i + SR * 2])
    print(name, round(len(x) / SR, 2), "s")


def thump(t, t0, f0, f1, decay, amp):
    """t0 から始まる低い『どん』: 周波数が f0 から f1 へ落ちる正弦波 (なめらかな立ち上がり付き)"""
    local = np.clip(t - t0, 0, None)
    f = f1 + (f0 - f1) * np.exp(-local * 18)
    phase = 2 * np.pi * np.cumsum(f) / SR
    rise = np.clip(local / 0.025, 0, 1) ** 2
    return np.sin(phase) * np.exp(-local / decay) * rise * (t >= t0) * amp


def heartbeat():
    n = int(SR * 7.0)
    t = t_axis(7.0)
    x = np.zeros(n)
    # 二拍で一組 (どく・どん)。だんだん間があき、静まっていく
    beats = [(0.30, 1.0), (0.62, 0.7), (1.55, 0.9), (1.90, 0.6), (2.95, 0.75), (3.32, 0.5), (4.55, 0.55), (4.95, 0.35), (6.2, 0.3)]
    for t0, a in beats:
        x += thump(t, t0, 70, 42, 0.11, a)
        x += lowpass(band_noise(n, 30, 160), 150) * np.exp(-np.clip(t - t0, 0, None) / 0.08) * (t >= t0) * a * 0.25
    x = reverb(x, 1.6, 0.2)[:n]
    x *= smooth_env(n, 0.05, 0.8)
    return x


def bell():
    dur = 6.0
    n = int(SR * dur)
    t = t_axis(dur)
    x = np.zeros(n)
    # 鐘の部分音 (基音に対する比、強さ、減衰の速さ)
    parts = [(1.0, 1.0, 0.9), (2.0, 0.6, 1.3), (2.4, 0.35, 1.7), (3.0, 0.3, 2.0), (4.2, 0.18, 2.6), (5.4, 0.1, 3.2)]
    base = 196.0
    for ratio, amp, decay_rate in parts:
        detune = 1 + rng.uniform(-0.002, 0.002)
        f = base * ratio * detune
        # わずかなうなり (二つの音がゆらぐ)
        x += amp * np.sin(2 * np.pi * f * t) * np.exp(-t * decay_rate * 0.55)
        x += amp * 0.5 * np.sin(2 * np.pi * (f * 1.0035) * t) * np.exp(-t * decay_rate * 0.6)
    x *= np.clip(t / 0.012, 0, 1) ** 2
    x = lowpass(x, 2600)
    x = reverb(x, 3.0, 0.35)[:n]
    x *= smooth_env(n, 0.0, 1.5)
    return x


def step():
    dur = 3.6
    n = int(SR * dur)
    t = t_axis(dur)
    # 重い『どん』: 超低音の芯 (34〜46 Hz) を厚くし、少し遅れて腹に響く第二の低音を重ねる
    x = thump(t, 0.05, 46, 24, 0.75, 1.0)
    x += 0.8 * thump(t, 0.05, 36, 22, 0.9, 1.0)
    x += 0.6 * thump(t, 0.07, 90, 40, 0.3, 1.0)
    x += 0.55 * thump(t, 0.34, 40, 24, 0.7, 1.0)
    rumble_noise = lowpass(band_noise(n, 18, 140), 140)
    x += rumble_noise * np.clip(t / 0.18, 0, 1) ** 2 * np.exp(-np.clip(t - 0.1, 0, None) / 1.2) * 1.1
    # 土が軋む音 (不気味さのため、前より少し強く)
    crack = lowpass(band_noise(n, 150, 700), 700)
    x += crack * np.clip((t - 0.05) / 0.06, 0, 1) ** 2 * np.exp(-np.clip(t - 0.08, 0, None) / 0.2) * 0.2
    x = reverb(x, 2.2, 0.25)[:n]
    x *= smooth_env(n, 0.02, 1.0)
    return x


def rumble():
    dur = 6.0
    n = int(SR * dur)
    t = t_axis(dur)
    x = lowpass(band_noise(n, 14, 90), 90)
    swell = np.sin(np.pi * t / dur) ** 2
    x *= swell * (0.75 + 0.25 * np.sin(2 * np.pi * 0.7 * t))
    return x


def song():
    dur = 40.0
    n = int(SR * dur)
    t = t_axis(dur)
    x = np.zeros(n)
    # (開始秒, 長さ, 始めの Hz, 終わりの Hz, 強さ)。低く、ゆっくり上がって下がる、鯨のような呼びかけ
    phrases = [
        (2.0, 7.0, 82, 110, 0.9), (8.5, 6.0, 110, 98, 0.8), (14.0, 8.0, 98, 146, 1.0), (21.0, 6.0, 146, 123, 0.9),
        (26.5, 7.0, 123, 82, 0.9), (32.0, 6.0, 87, 65, 0.8),
    ]
    for start, length, f0, f1, amp in phrases:
        m = int(SR * length)
        i0 = int(SR * start)
        local = np.arange(m) / SR
        prog = local / length
        f = f0 + (f1 - f0) * (0.5 - 0.5 * np.cos(np.pi * prog))
        f = f * (1 + 0.006 * np.sin(2 * np.pi * 4.3 * local))
        phase = 2 * np.pi * np.cumsum(f) / SR
        voice = np.zeros(m)
        for h, ha in enumerate([1.0, 0.55, 0.35, 0.22, 0.12, 0.07], start=1):
            voice += ha * np.sin(h * phase + h * 0.4)
        env = np.sin(np.pi * np.clip(prog, 0, 1)) ** 1.6
        x[i0:i0 + m] += voice * env * amp
    # 遠い空気の息 (声の下に、ごく薄く)
    x += lowpass(band_noise(n, 40, 400), 400) * 0.06 * np.sin(np.pi * t / dur) ** 2
    x = reverb(x, 3.5, 0.45)[:n]
    x *= smooth_env(n, 5.0, 7.0)
    return x


def wind():
    dur = 9.0
    n = int(SR * dur)
    t = t_axis(dur)
    # 低い息のような風: 帯域を狭めた雑音を、ゆっくりした波 (二つの周期) で揺らす。ひゅうという高音は作らない
    body = lowpass(band_noise(n, 40, 420), 380)
    air = lowpass(band_noise(n, 300, 1100), 900) * 0.25
    gust = 0.55 + 0.45 * np.sin(2 * np.pi * 0.19 * t + 0.6) * np.sin(2 * np.pi * 0.07 * t + 1.1)
    x = (body + air) * gust
    x = reverb(x, 2.2, 0.25)[:n]
    x *= smooth_env(n, 2.5, 3.5)
    return x


if __name__ == "__main__":
    only = set(_sys.argv[1:])
    if only:
        # 名前を渡したものだけ書く (採用済みの音を上書きしない)。例: SKYABYSS_ALLOW_REGEN=1 python tools/gen/sound/gen_opening_sounds.py wind
        for name, fn, peak in (("heartbeat", heartbeat, 0.75), ("bell", bell, 0.7), ("step", step, 0.97), ("rumble", rumble, 0.6),
                               ("wind", wind, 0.5), ("song", song, 0.6)):
            if name in only:
                write(name + ".ogg", fn(), peak)
        _sys.exit(0)
    write("heartbeat.ogg", heartbeat(), 0.75)
    write("bell.ogg", bell(), 0.7)
    write("step.ogg", step(), 0.97)
    write("rumble.ogg", rumble(), 0.6)
    write("wind.ogg", wind(), 0.5)
    write("song.ogg", song(), 0.6)
