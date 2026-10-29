# -*- coding: utf-8 -*-
"""BGM の合成器。楽器の音を numpy で作り、楽譜 (score.py) から曲を組み、残響をかけて .ogg に書く。

外の音源 (SoundFont・録音) は使わない。音はすべてこのファイルの式から作る。
楽器:
- flute   : 笛。倍音 4 つ + 息の音 + 遅れて入るビブラート
- lute    : リュート / ハープ。Karplus-Strong の弦 (はじいた弦の物理模型)
- harp    : lute の減衰を長く、はじきを柔らかく
- strings : 弦の合奏。少しずつずらした鋸波 5 本 + ローパス + ゆっくりした立ち上がり
- bell    : 鐘。教会の鐘の倍音 (ハム・プライム・短三度・五度・ノミナル)
- celesta : 小さな鐘 (チェレスタ)。整数倍に近い倍音で短く
- bass    : 低音。正弦 + 2 倍音
- drum    : 枠太鼓 (低い一打)、shaker: 振り物
- choir   : 「あー」の合唱。鋸波を母音のフォルマントで絞る
"""
import math

import numpy as np
import soundfile as sf
from scipy import signal

SR = 44100
RNG = np.random.default_rng(20261030)


def midi_hz(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


def env_adsr(n, a, d, s, r, hold):
    """n サンプル。a・d・r は秒、s は持続の大きさ、hold は鳴らす長さ (秒。そのあと r で消える)"""
    t = np.arange(n) / SR
    e = np.empty(n)
    a = max(a, 1e-3)
    e[:] = s
    m = t < a
    e[m] = t[m] / a
    m = (t >= a) & (t < a + d)
    e[m] = 1 - (1 - s) * (t[m] - a) / max(d, 1e-3)
    m = t >= hold
    if np.any(m):
        # 離した時点の値から r で消える
        at = np.searchsorted(t, hold)
        v0 = e[at - 1] if at > 0 else s
        e[m] = v0 * np.exp(-(t[m] - hold) / max(r / 4.6, 1e-3))
    return e


def polyblep_saw(freq, n, phase0=0.0, vib=None):
    """帯域を抑えた鋸波 (折り返しの雑音を減らす)。freq は数か配列"""
    f = np.full(n, freq) if np.isscalar(freq) else freq
    if vib is not None:
        f = f * vib
    dt = f / SR
    ph = (phase0 + np.cumsum(dt)) % 1.0
    y = 2 * ph - 1
    m = ph < dt
    x = ph[m] / dt[m]
    y[m] -= x + x - x * x - 1
    m = ph > 1 - dt
    x = (ph[m] - 1) / dt[m]
    y[m] -= x * x + x + x + 1
    return y


def lowpass(x, hz, order=2):
    hz = min(hz, SR * 0.45)
    sos = signal.butter(order, hz, "low", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def highpass(x, hz, order=2):
    sos = signal.butter(order, hz, "high", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def bandpass(x, lo, hi, order=2):
    sos = signal.butter(order, [lo, min(hi, SR * 0.45)], "band", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


# ---------------------------------------------------------------- 楽器 (どれも mono の配列を返す。長さは音の尾まで)
def flute(m, dur, vel=0.8, breath=0.05):
    f = midi_hz(m)
    tail = 0.18
    n = int((dur + tail) * SR)
    t = np.arange(n) / SR
    # ビブラートは 0.3 秒から深くなる
    depth = 0.0045 * np.clip((t - 0.3) / 0.6, 0, 1)
    vib = 1 + depth * np.sin(2 * math.pi * 5.1 * t + RNG.random() * 6)
    ph = np.cumsum(f * vib / SR) * 2 * math.pi
    harm = [(1, 1.0), (2, 0.32 + 0.1 * vel), (3, 0.10 + 0.08 * vel), (4, 0.035), (5, 0.015)]
    y = np.zeros(n)
    for k, a in harm:
        if f * k < SR * 0.45:
            y += a * np.sin(k * ph)
    e = env_adsr(n, 0.06, 0.12, 0.82, tail, dur)
    # 息: 立ち上がりに強く (チフ)、そのあと薄く
    noise = bandpass(RNG.standard_normal(n), 1500, 7000)
    noise /= np.max(np.abs(noise)) + 1e-9
    chiff = np.exp(-t / 0.05)
    y = y * e + noise * (breath * e + 0.08 * chiff * e)
    return y * vel * 0.5


_KS_CACHE = {}


def _ks(m, decay, bright, length, variant):
    key = (m, round(decay, 2), round(bright, 2), round(length, 2), variant)
    if key in _KS_CACHE:
        return _KS_CACHE[key]
    f = midi_hz(m)
    N = max(2, int(round(SR / f - 0.5)))
    f0 = SR / (N + 0.5)  # 二つの平均で半サンプル遅れる
    ratio = f / f0  # 整数の遅れで作ってから、読む速さを変えて音程を合わせる (ratio 倍の速さで読む = 高さ ratio 倍)
    n = int(length * SR * ratio) + N
    rng = np.random.default_rng(1000 + m * 7 + variant)
    burst = rng.standard_normal(N)
    burst = lowpass(np.concatenate([burst, np.zeros(N)]), 600 + bright * 7000)[:N]
    x = np.zeros(n)
    x[:N] = burst
    # 1 周期で失う大きさ: decay 秒で -60 dB
    g = 10 ** (-3.0 * N / (SR * decay))
    a = np.zeros(N + 2)
    a[0] = 1.0
    a[N] = -0.5 * g
    a[N + 1] = -0.5 * g
    y = signal.lfilter([1.0], a, x)
    # 音程を合わせる (伸び縮み)
    src_t = np.arange(len(y))
    want = np.arange(int(length * SR)) * ratio
    y = np.interp(want, src_t, y)
    y /= np.max(np.abs(y)) + 1e-9
    _KS_CACHE[key] = y
    return y


def lute(m, dur, vel=0.8):
    f = midi_hz(m)
    decay = 2.2 * (220.0 / f) ** 0.35
    length = min(max(dur + 0.6, 1.2), 3.0)
    y = _ks(m, decay, 0.35 + 0.4 * vel, length, int(RNG.integers(0, 4))).copy()
    # 胴の響き: 低い帯を少し足す
    y = y + 0.25 * lowpass(y, 900)
    n = len(y)
    # 次の音で止める (弦を押さえ直す) ときは dur + 0.25 で消す
    e = env_adsr(n, 0.002, 0.0, 1.0, 0.25, dur + 0.35)
    return y * e * vel * 0.42


def harp(m, dur, vel=0.8):
    f = midi_hz(m)
    decay = 3.8 * (220.0 / f) ** 0.3
    length = min(max(dur + 1.5, 2.0), 4.5)
    y = _ks(m, decay, 0.18 + 0.3 * vel, length, int(RNG.integers(0, 4))).copy()
    y = lowpass(y, 5200)
    n = len(y)
    t = np.arange(n) / SR
    y *= 1 - np.exp(-t / 0.004)
    return y * vel * 0.4


def strings(m, dur, vel=0.7, attack=0.35, release=0.7, bright=1.0):
    f = midi_hz(m)
    n = int((dur + release) * SR)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for cents in (-11, -5, 0, 5, 11):
        vib = 1 + 0.0018 * np.sin(2 * math.pi * (5.3 + RNG.random() * 0.6) * t + RNG.random() * 6)
        y += polyblep_saw(f * 2 ** (cents / 1200.0), n, RNG.random(), vib)
    cutoff = (900 + 1800 * vel) * bright * (1 + 0.15 * (f / 440.0))
    y = lowpass(y, cutoff, 2)
    y = highpass(y, 90, 1)
    e = env_adsr(n, attack, 0.3, 0.9, release, dur)
    return y * e * vel * 0.09


def choir(m, dur, vel=0.6, vowel="a", attack=0.5, release=1.0):
    f = midi_hz(m)
    n = int((dur + release) * SR)
    t = np.arange(n) / SR
    src = np.zeros(n)
    for cents in (-8, 0, 7):
        vib = 1 + 0.004 * np.sin(2 * math.pi * (4.8 + RNG.random()) * t + RNG.random() * 6)
        src += polyblep_saw(f * 2 ** (cents / 1200.0), n, RNG.random(), vib)
    forms = {"a": [(800, 1.0), (1150, 0.5), (2900, 0.25)], "o": [(450, 1.0), (800, 0.45), (2830, 0.15)],
             "u": [(325, 1.0), (700, 0.3), (2530, 0.08)]}[vowel]
    y = np.zeros(n)
    for fc, g in forms:
        y += g * bandpass(src, fc * 0.85, fc * 1.15, 2)
    y += 0.08 * lowpass(src, 400)
    e = env_adsr(n, attack, 0.3, 0.9, release, dur)
    return y * e * vel * 0.22


def bell(m, dur, vel=0.8, length=6.0):
    """教会の鐘。聞こえる音程 = ノミナル (m)"""
    f = midi_hz(m)
    n = int(length * SR)
    t = np.arange(n) / SR
    parts = [(0.25, 0.35, 7.0), (0.5, 0.55, 5.0), (0.6, 0.30, 3.6), (0.75, 0.22, 3.0), (1.0, 0.65, 2.6),
             (1.25, 0.18, 1.8), (1.5, 0.16, 1.4), (2.0, 0.10, 1.0), (2.66, 0.06, 0.6)]
    y = np.zeros(n)
    for r, a, d in parts:
        fr = f * r * (1 + RNG.normal(0, 0.0008))
        if fr < SR * 0.45:
            y += a * np.sin(2 * math.pi * fr * t + RNG.random() * 6) * np.exp(-t / d)
    strike = highpass(RNG.standard_normal(n), 2000) * np.exp(-t / 0.008) * 0.25
    y = (y + strike) * (1 - np.exp(-t / 0.002))
    return y * vel * 0.28


def celesta(m, dur, vel=0.7):
    f = midi_hz(m)
    length = 2.2
    n = int(length * SR)
    t = np.arange(n) / SR
    y = np.sin(2 * math.pi * f * t) * np.exp(-t / 0.9)
    y += 0.25 * np.sin(2 * math.pi * f * 4.0 * t) * np.exp(-t / 0.15)
    y += 0.12 * np.sin(2 * math.pi * f * 2.0 * t) * np.exp(-t / 0.4)
    y *= 1 - np.exp(-t / 0.002)
    return y * vel * 0.3


def bass(m, dur, vel=0.8):
    f = midi_hz(m)
    rel = 0.25
    n = int((dur + rel) * SR)
    t = np.arange(n) / SR
    y = np.sin(2 * math.pi * f * t) + 0.28 * np.sin(4 * math.pi * f * t) + 0.08 * np.sin(6 * math.pi * f * t)
    y = np.tanh(1.3 * y)
    e = env_adsr(n, 0.015, 0.4, 0.7, rel, dur)
    return y * e * vel * 0.32


def drum(m, dur, vel=0.7):
    """枠太鼓。m は使わない (高さは固定)。vel で大きさ"""
    n = int(0.9 * SR)
    t = np.arange(n) / SR
    fr = 62 + 50 * np.exp(-t / 0.04)
    body = np.sin(2 * math.pi * np.cumsum(fr) / SR) * np.exp(-t / 0.28)
    slap = bandpass(RNG.standard_normal(n), 300, 2500) * np.exp(-t / 0.02) * 0.35
    return (body + slap) * vel * 0.5


def taiko(m, dur, vel=0.8):
    n = int(1.6 * SR)
    t = np.arange(n) / SR
    fr = 48 + 40 * np.exp(-t / 0.05)
    body = np.sin(2 * math.pi * np.cumsum(fr) / SR) * np.exp(-t / 0.45)
    skin = bandpass(RNG.standard_normal(n), 120, 1200) * np.exp(-t / 0.05) * 0.5
    return np.tanh(1.6 * (body + skin)) * vel * 0.55


def shaker(m, dur, vel=0.5):
    n = int(0.18 * SR)
    t = np.arange(n) / SR
    y = highpass(RNG.standard_normal(n), 5000) * (np.exp(-t / 0.03) * (1 - np.exp(-t / 0.008)))
    return y * vel * 0.12


# gm.use_soundfont() が差し替える前の合成の楽器 (差し替えたあとも重ねて使える)
SYNTH = {}

INSTRUMENTS = {
    "flute": flute, "lute": lute, "harp": harp, "strings": strings, "choir": choir, "bell": bell,
    "celesta": celesta, "bass": bass, "drum": drum, "taiko": taiko, "shaker": shaker,
}


# ---------------------------------------------------------------- 残響
def make_ir(seconds=2.6, predelay=0.022, damp=0.5, seed=7):
    """部屋の響き (stereo)。後ろほど高い音が早く消える"""
    rng = np.random.default_rng(seed)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    out = np.zeros((n + int(predelay * SR), 2))
    for ch in range(2):
        noise = rng.standard_normal(n)
        lo = lowpass(noise, 2500)
        hi = highpass(noise, 2500)
        tail = lo * np.exp(-6.9 * t / seconds) + hi * np.exp(-6.9 * t / (seconds * (1 - damp * 0.8)))
        # 初期反射
        for k in range(8):
            at = int(rng.uniform(0.004, 0.06) * SR)
            tail[at] += rng.uniform(0.3, 0.8) * (1 if rng.random() < 0.5 else -1)
        tail *= 1 - np.exp(-t / 0.01)
        out[int(predelay * SR):, ch] = tail
    out /= np.sqrt(np.sum(out ** 2, axis=0, keepdims=True))
    return out


# ---------------------------------------------------------------- 組み立て
def active_rms(y, frame=0.1):
    """鳴っている所だけの大きさ (無音の所は数えない)"""
    mono = np.mean(y, axis=1) if y.ndim == 2 else y
    k = int(frame * SR)
    m = len(mono) // k
    if m == 0:
        return 1e-9
    fr = np.sqrt(np.mean(mono[: m * k].reshape(m, k) ** 2, axis=1))
    on = fr > np.max(fr) * 0.08
    return float(np.sqrt(np.mean(fr[on] ** 2))) if np.any(on) else 1e-9


class Mix:
    """楽器の組 (group) ごとに乾いた音と残響へ送る音を持つ。render で組ごとの大きさをそろえてから混ぜる"""

    def __init__(self, seconds):
        self.n = int(seconds * SR)
        self.groups = {}

    def _g(self, group):
        if group not in self.groups:
            self.groups[group] = (np.zeros((self.n, 2)), np.zeros((self.n, 2)))
        return self.groups[group]

    def add(self, mono, at, pan=0.0, gain=1.0, wet=0.3, group="main"):
        i = max(0, int(at * SR))
        if i >= self.n:
            return
        dry, send = self._g(group)
        y = mono[: self.n - i] * gain
        if y.ndim == 2:
            # ステレオの音 (音源の楽器): pan は左右の釣り合いとして掛ける
            bal = np.array([min(1.0, 1.0 - pan), min(1.0, 1.0 + pan)])
            dry[i:i + len(y)] += y * bal * (1 - wet * 0.5)
            send[i:i + len(y)] += y * bal * wet
            return
        a = (pan + 1) * math.pi / 4
        l, r = math.cos(a), math.sin(a)
        dry[i:i + len(y), 0] += y * l * (1 - wet * 0.5)
        dry[i:i + len(y), 1] += y * r * (1 - wet * 0.5)
        send[i:i + len(y), 0] += y * l * wet
        send[i:i + len(y), 1] += y * r * wet

    def render(self, ir, levels, fade_in=0.0, fade_out=3.0, target_rms_db=-19.0, report=True, loop=0):
        """levels: 組 → 大きさ (dB。鳴っている所の大きさの比)。書いていない組は -12。
        loop: ループの長さ (サンプル)。0 でなければ、それより後ろにはみ出た音 (余韻・残響の尾) を頭へ折り返して足し、
        長さ loop の 1 周を返す。終わりから頭へ構造上かならず途切れずにつながる"""
        dry = np.zeros((self.n, 2))
        send = np.zeros((self.n, 2))
        for g, (d, s) in self.groups.items():
            r = active_rms(d + s)
            if levels.get(g, -12.0) <= -150:
                continue
            gain = 10 ** (levels.get(g, -12.0) / 20) / r
            if report:
                print("  %-10s rms %6.1f dB -> gain %6.2f" % (g, 20 * math.log10(r), gain))
            dry += d * gain
            send += s * gain
        wet = np.zeros_like(dry)
        for ch in range(2):
            wet[:, ch] = signal.fftconvolve(send[:, ch], ir[:, ch])[: self.n]
        y = dry + wet * 1.4
        for ch in range(2):
            y[:, ch] = highpass(y[:, ch], 28, 2)
            # 高い帯を少し持ち上げる (4 kHz から上で約 +2.5 dB)。合成の楽器はこもりやすい
            y[:, ch] = y[:, ch] + 0.33 * highpass(y[:, ch], 4000, 2)
        if loop:
            folded = y[:loop].copy()
            for k in range(loop, len(y), loop):
                part = y[k:k + loop]
                folded[:len(part)] += part
            y = folded
        t = np.arange(len(y)) / SR
        if fade_in > 0:
            y *= np.clip(t / fade_in, 0, 1)[:, None]
        if fade_out > 0:
            y *= np.clip((t[-1] - t) / fade_out, 0, 1)[:, None]
        y *= 10 ** (target_rms_db / 20) / (active_rms(y) + 1e-9)
        knee = 0.7
        over = np.abs(y) > knee
        y[over] = np.sign(y[over]) * (knee + (1 - knee) * np.tanh((np.abs(y[over]) - knee) / (1 - knee)))
        return y * 0.97


def write_ogg(path, y, quality=0.5):
    # quality 0〜1 (大きいほど良い)。soundfile は compression_level (0 = 最良)
    # libsndfile の Vorbis は長い配列を一度に書くとスタックがあふれて落ちる。少しずつ書く
    y = np.ascontiguousarray(y.astype(np.float32))
    with sf.SoundFile(path, "w", SR, y.shape[1], subtype="VORBIS", format="OGG", compression_level=1 - quality) as f:
        for i in range(0, len(y), 4096):
            f.write(y[i:i + 4096])
