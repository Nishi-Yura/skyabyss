# -*- coding: utf-8 -*-
"""書き出した曲を耳の代わりに検める。

1. 楽器の音程: 一音ずつ鳴らして基本の高さを測り、狙いとのずれ (セント) を出す
2. 和音との合い方: 拍ごとの音の類 (クロマ) が、その拍の和音の音にどれだけ入っているか
3. 音量の流れ・左右の釣り合い・割れ (0.99 を超えた点)・ぷつっという雑音 (高い帯の急な山)
4. 図: 音量の流れとスペクトログラム (PNG)
使い方: python check.py <曲.ogg> <図.png> [track_capital]
"""
import importlib
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np  # noqa: E402
import soundfile as sf  # noqa: E402
from scipy import signal  # noqa: E402

import synth as S  # noqa: E402
from score import chord, progression  # noqa: E402


def f0_of(y, want):
    """狙いの高さのまわり (±1/3 オクターブ) で、スペクトルのいちばん高い山を細かく測る"""
    seg = y[int(0.1 * S.SR): int(0.6 * S.SR)]
    seg = seg * np.hanning(len(seg))
    nfft = 1 << 19
    sp = np.abs(np.fft.rfft(seg, nfft))
    fr = np.fft.rfftfreq(nfft, 1 / S.SR)
    m = (fr > want * 0.8) & (fr < want * 1.25)
    return float(fr[m][np.argmax(sp[m])])


def pitch_report():
    print("== 音程 (セント。±10 以内なら良い)")
    for name in ("lute", "harp", "flute", "strings", "bass", "celesta"):
        errs = []
        for m in (40, 45, 50, 57, 62, 69, 74, 81, 86):
            if name == "bass" and m > 62:
                continue
            if name in ("flute", "celesta") and m < 57:
                continue
            y = S.INSTRUMENTS[name](m, 1.0, 0.7)
            want = S.midi_hz(m)
            f = f0_of(y, want)
            # 8 度ずれ (倍音を拾った) は数えない
            c = 1200 * math.log2(f / want)
            c = c - 1200 * round(c / 1200)
            errs.append(c)
        print("  %-8s worst %+6.1f  mean %+6.1f" % (name, max(errs, key=abs), float(np.mean(errs))))


def chroma(y, at, length):
    a, b = int(at * S.SR), int((at + length) * S.SR)
    seg = y[a:b]
    if len(seg) < 2048:
        return None
    seg = seg * np.hanning(len(seg))
    spec = np.abs(np.fft.rfft(seg)) ** 2
    fr = np.fft.rfftfreq(len(seg), 1 / S.SR)
    m = (fr > 70) & (fr < 1400)
    pcs = np.round(12 * np.log2(fr[m] / 440.0) + 69).astype(int) % 12
    ch = np.bincount(pcs, weights=spec[m], minlength=12)
    return ch / (ch.sum() + 1e-12)


def harmony_report(y, track):
    """拍ごとに: 和音の音 + その拍の旋律の音 に入る割合"""
    mono = np.mean(y, axis=1)
    sec = getattr(track, "sec", None) or (lambda b: b * 60.0 / track.BPM)
    rows = []
    for text, bar0 in track.CHORD_PLAN:
        for at, length, name in progression(text, track.BAR, bar0):
            bass_pc, tones = chord(name)
            ch = chroma(mono, sec(at) + 0.05, sec(length) - 0.05)
            if ch is None:
                continue
            # 低音を別に指定した和音 (F#/E など) は、その低音も和音の音に数える
            inside = float(sum(ch[t] for t in set(tones) | {bass_pc}))
            rows.append((at / track.BAR, name, inside, ch))
    vals = [r[2] for r in rows]
    print("== 和音の音に入る割合 (旋律の経過音があるので 0.6 以上なら良い): 平均 %.2f  最低 %.2f" % (np.mean(vals), min(vals)))
    names = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
    for bar, name, inside, ch in sorted(rows, key=lambda r: r[2])[:6]:
        top = np.argsort(ch)[::-1][:4]
        print("  bar %5.1f %-6s %.2f  強い音: %s" % (bar + 1, name, inside, " ".join("%s%.0f%%" % (names[i], ch[i] * 100) for i in top)))


def level_report(y, png):
    mono = np.mean(y, axis=1)
    k = int(0.5 * S.SR)
    m = len(mono) // k
    rms = 20 * np.log10(np.sqrt(np.mean(mono[: m * k].reshape(m, k) ** 2, axis=1)) + 1e-9)
    lr = 20 * np.log10((np.sqrt(np.mean(y[:, 0] ** 2)) + 1e-9) / (np.sqrt(np.mean(y[:, 1] ** 2)) + 1e-9))
    clip = int(np.sum(np.abs(y) > 0.99))
    hp = S.highpass(mono, 7000, 4)
    e = np.abs(hp)
    win = int(0.003 * S.SR)
    env = np.convolve(e, np.ones(win) / win, "same")
    base = np.convolve(e, np.ones(int(0.2 * S.SR)) / int(0.2 * S.SR), "same") + 1e-6
    spikes = np.where(env > base * 8)[0]
    groups = 0 if len(spikes) == 0 else 1 + int(np.sum(np.diff(spikes) > S.SR * 0.05))
    print("== 音量: 鳴っている所 %.1f dB、0.5 秒ごとの最小 %.1f / 最大 %.1f、左右の差 %+.1f dB、割れ %d 点、高い帯の急な山 %d 回"
          % (20 * math.log10(S.active_rms(y)), rms[2:-8].min(), rms.max(), lr, clip, groups))
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    fig, ax = plt.subplots(2, 1, figsize=(14, 7), gridspec_kw={"height_ratios": [1, 2]})
    ax[0].plot(np.arange(m) * 0.5, rms)
    ax[0].set_ylim(-60, 0)
    ax[0].set_ylabel("dB")
    ax[0].grid(alpha=0.3)
    f, t, Z = signal.spectrogram(mono, S.SR, nperseg=4096, noverlap=2048)
    Z = 10 * np.log10(Z + 1e-12)
    ax[1].pcolormesh(t, f, Z, shading="auto", vmin=Z.max() - 90, vmax=Z.max(), cmap="magma")
    ax[1].set_yscale("symlog", linthresh=200)
    ax[1].set_ylim(40, 16000)
    ax[1].set_xlabel("s")
    fig.tight_layout()
    fig.savefig(png, dpi=80)


def loop_report(y):
    """終わりから頭へつないだ所: 段差 (隣の点の差) と、前後 0.25 秒の大きさの差"""
    d = np.abs(np.diff(y, axis=0))
    typical = np.percentile(d, 99.9)
    seam = float(np.max(np.abs(y[0] - y[-1])))
    k = int(0.25 * S.SR)
    a = 20 * math.log10(np.sqrt(np.mean(y[-k:] ** 2)) + 1e-9)
    b = 20 * math.log10(np.sqrt(np.mean(y[:k] ** 2)) + 1e-9)
    print("== ループのつなぎ目: 段差 %.4f (曲の中の 99.9%% の段差 %.4f。下なら聞こえない)、大きさ 終わり %.1f dB / 頭 %.1f dB"
          % (seam, typical, a, b))


if __name__ == "__main__":
    y, sr = sf.read(sys.argv[1])
    assert sr == S.SR
    pitch_report()
    if len(sys.argv) > 3:
        harmony_report(y, importlib.import_module(sys.argv[3]))
    level_report(y, sys.argv[2])
    loop_report(y)
