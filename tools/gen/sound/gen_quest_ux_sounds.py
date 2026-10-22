"""クエストの更新の知らせ音 (docs/design/18 の 3.5 節) を合成して .ogg で書き出す。

使い方: python tools/gen/sound/gen_quest_ux_sounds.py
出力先: src/main/resources/assets/skyabyss/sounds/ui/

- quest_main.ogg : メインの段の更新。小さな鐘が二つ、五度上がる (1.3 秒)。控えめだが、何か進んだと分かる
- quest_side.ogg : サブの更新。ひとつだけ、もっと柔らかく短い (0.7 秒)。メインと聞き分けられる

序盤の鐘 (tools/gen/sound/gen_opening_sounds.py の bell) と同じ部分音の組み方。ジャンプスケア禁止: 立ち上がりは 10 ミリ秒以上、最大音量は低め。
新しい出力先なので採用済みの素材は上書きしない。
"""
from pathlib import Path

import numpy as np
import soundfile as sf

SR = 22050
OUT = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/assets/skyabyss/sounds/ui"
rng = np.random.default_rng(20261019)


def lowpass(x, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    spec *= 1 / (1 + (f / hz) ** 4)
    return np.fft.irfft(spec, len(x))


def reverb(x, seconds, mix):
    m = int(SR * seconds)
    ir = rng.standard_normal(m) * np.exp(-np.arange(m) / (SR * seconds / 6.0))
    ir = lowpass(ir, 2200)
    ir /= np.sqrt(np.sum(ir ** 2))
    n = len(x) + m
    wet = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)
    dry = np.concatenate([x, np.zeros(m)])
    return (dry * (1 - mix) + wet * mix)[:len(x)]


def note(t, t0, base, amp, decay):
    """t0 に鳴らす小さな鐘の一音 (部分音は鐘の比、なめらかな立ち上がり)"""
    local = np.clip(t - t0, 0, None)
    parts = [(1.0, 1.0, 1.0), (2.0, 0.42, 1.5), (2.76, 0.2, 2.2), (4.1, 0.1, 3.0)]
    x = np.zeros_like(t)
    for ratio, a, d in parts:
        f = base * ratio * (1 + rng.uniform(-0.0015, 0.0015))
        x += a * np.sin(2 * np.pi * f * local) * np.exp(-local * d * decay)
    rise = np.clip(local / 0.012, 0, 1) ** 2
    return x * rise * (t >= t0) * amp


def write(name, x, peak):
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    # 終わりをなめらかに消す
    r = int(SR * 0.12)
    x[-r:] *= np.cos(np.linspace(0, np.pi / 2, r)) ** 2
    OUT.mkdir(parents=True, exist_ok=True)
    with sf.SoundFile(str(OUT / name), "w", samplerate=SR, channels=1, format="OGG", subtype="VORBIS") as f:
        f.write(x.astype(np.float32))
    print(name, round(len(x) / SR, 2), "s")


def main_sound():
    dur = 1.3
    t = np.arange(int(SR * dur)) / SR
    # 523 Hz (ド) のあと 784 Hz (ソ): 五度上がる
    x = note(t, 0.0, 523.25, 1.0, 2.4) + note(t, 0.17, 784.0, 0.85, 2.4)
    x = lowpass(x, 3200)
    return reverb(x, 0.9, 0.25)


def side_sound():
    dur = 0.7
    t = np.arange(int(SR * dur)) / SR
    x = note(t, 0.0, 659.25, 1.0, 4.2)
    x = lowpass(x, 2600)
    return reverb(x, 0.5, 0.18)


if __name__ == "__main__":
    write("quest_main.ogg", main_sound(), 0.5)
    write("quest_side.ogg", side_sound(), 0.32)
