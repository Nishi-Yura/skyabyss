"""場面 (シネマティック) の台詞の「声のようなもの」を合成して .ogg で書き出す (docs/design/20_cinematics.md の 6 章)。

言葉にならない声 (どうぶつの森・Undertale 風のつぶやき)。サンプルは使わず、numpy / scipy だけで作る (著作権の問題なし)。
台詞の本文は日本語の文字数 (かな = 1 拍、漢字 = 約 2 拍、句読点 = 間) が拍の数と間になる。英語も同じクリップを使う。

使い方 (リポジトリ直下で):
  python tools/gen_cinematic_voice.py --install [--style a|b|c]   # 全台詞の声を assets に書く (既定 a)
  python tools/gen_cinematic_voice.py --check                      # 全台詞にクリップと音の登録があり、長さが台詞の表示時間に収まるか
  python tools/gen_cinematic_voice.py --review                     # 3 案の見本を docs/review/voice/ に書く (選定ページ)

様式 (--style): a = 音節のブリップ (形のある母音を拍ごとの高さで)、b = 息のささやきのつぶやき (息 + 母音の滑らかな移り)、
c = 歌うハミング・詠唱 (母音がなめらかに移り、残響が深い)。選んだあとは --style を替えて --install し直すだけ。
話し手ごとの音色: 天の声 = 空気・重ね・広い・遅い、主人公 = やわらかい中域、イサナ = 明るい、語り部 = 老いて低く息、見る者 = うつろ・ささやき。

台詞の一覧: src/main/resources/assets/skyabyss_cinematic/voice_manifest.json (台本 Scenes の台詞。自己テストが台本と合っているか確かめる。
台本を変えたら SKYABYSS_DUMP_VOICE=<パス> を付けて自己テスト (cinematic) を一度流して書き直す)。
出力: sounds/voice/<場面>/l<番号>.ogg (22.05 kHz mono) と sounds.json (音の名前 voice.<場面>.l<番号>)。
"""
import argparse
import json
import math
import sys
import zlib
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import fftconvolve, lfilter

SR = 22050
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets"
CINE = ASSETS / "skyabyss_cinematic"
MANIFEST = CINE / "voice_manifest.json"
VOICE_DIR = CINE / "sounds" / "voice"
REVIEW = ROOT / "docs/review/voice"
STYLES = ("a", "b", "c")

# ----------------------------------------------------------------------------- 話し手
# f0: 基本の高さ (Hz)、form: 口の大きさ (formant の倍率)、mora: 一拍の秒、breath: 息の量、voiced: ささやきに混ぜる声 (B)、
# layers: (半音, 音量, 離調 cent) の重ね、rev: (残響の長さ秒, 混ぜ具合)、note: 詠唱 (C) の一音の長さ、hollow: うつろ (櫛形の共鳴)、
# bright: 高域の持ち上げ (-1〜1)
SPEAKERS = {
    "heaven": dict(f0=165, form=1.0, mora=0.19, breath=0.38, voiced=0.25, layers=[(0, 1.0, 0), (0, 0.6, 14), (-12, 0.55, -6), (7, 0.32, 9), (12, 0.22, -10)],
                   rev=(1.9, 0.5), note=0.85, hollow=0.0, bright=0.1, jitter=0.03),
    "hero": dict(f0=132, form=1.0, mora=0.125, breath=0.12, voiced=0.12, layers=[(0, 1.0, 0)],
                 rev=(0.35, 0.08), note=0.5, hollow=0.0, bright=0.0, jitter=0.07),
    "isana": dict(f0=236, form=1.14, mora=0.108, breath=0.1, voiced=0.1, layers=[(0, 1.0, 0)],
                  rev=(0.4, 0.1), note=0.45, hollow=0.0, bright=0.45, jitter=0.07),
    "storyteller": dict(f0=96, form=0.88, mora=0.158, breath=0.42, voiced=0.3, layers=[(0, 1.0, 0), (0, 0.25, 11)],
                        rev=(0.5, 0.12), note=0.7, hollow=0.0, bright=-0.35, jitter=0.1),
    "watcher": dict(f0=118, form=0.82, mora=0.16, breath=0.9, voiced=0.05, layers=[(0, 1.0, 0), (-5, 0.35, 0)],
                    rev=(1.1, 0.34), note=0.8, hollow=0.7, bright=-0.1, jitter=0.04),
}

# 母音の formant (F1, F2, F3 Hz)。n = ん (鼻にこもる)
VOWELS = {
    "a": (800, 1250, 2550), "i": (310, 2250, 3000), "u": (350, 1350, 2350),
    "e": (520, 1900, 2600), "o": (520, 950, 2400), "n": (260, 1100, 2350),
}
FORM_GAIN = (1.0, 0.62, 0.32, 0.14)
FORM_BW = (80, 110, 150, 220)
FORM_BW_WHISPER = (130, 180, 240, 300)

# ----------------------------------------------------------------------------- 文字 → 拍
_ROWS = {
    "": "あいうえお", "k": "かきくけこ", "s": "さしすせそ", "t": "たちつてと", "n": "なにぬねの", "h": "はひふへほ",
    "m": "まみむめも", "y": "や\0ゆ\0よ", "r": "らりるれろ", "w": "わ\0\0\0を", "g": "がぎぐげご", "z": "ざじずぜぞ",
    "d": "だぢづでど", "b": "ばびぶべぼ", "p": "ぱぴぷぺぽ",
}
KANA = {}
for _c, _chars in _ROWS.items():
    for _ch, _v in zip(_chars, "aiueo"):
        if _ch != "\0":
            KANA[_ch] = (_c, _v)
            KANA[chr(ord(_ch) + 0x60)] = (_c, _v)
KANA["う゛"] = ("b", "u")
SMALL_Y = {"ゃ": "a", "ゅ": "u", "ょ": "o", "ャ": "a", "ュ": "u", "ョ": "o"}
SMALL_V = {"ぁ": "a", "ぃ": "i", "ぅ": "u", "ぇ": "e", "ぉ": "o", "ァ": "a", "ィ": "i", "ゥ": "u", "ェ": "e", "ォ": "o"}
ONE_MORA_KANJI = set("空胸歌拍波海殻島地足光山目火土石雲雨風音夜朝手")
PAUSE_SHORT = set("、,，")
PAUSE_LONG = set("。！？!?.")
PAUSE_DASH = set("…―ー―—・")  # ー は下で別扱い
KANJI_CONS = ["", "k", "s", "t", "n", "h", "m", "r", "", "k"]


def is_kanji(ch):
    return "一" <= ch <= "鿿" or ch == "々"


def tokenize(text):
    """本文 → [{'t': 'm', 'c': 子音, 'v': 母音, 'len': 拍の長さ}, {'t': 'p', 's': 間 (秒)}, ...]"""
    toks = []
    i = 0
    ell = 0
    while i < len(text):
        ch = text[i]
        i += 1
        if ch in "…・" or ch in "―—":
            ell += 1
            continue
        if ell:
            toks.append({"t": "p", "s": min(0.14 * ell + 0.1, 0.55)})
            ell = 0
        if ch in KANA:
            c, v = KANA[ch]
            toks.append({"t": "m", "c": c, "v": v, "len": 1.0})
        elif ch in SMALL_Y and toks and toks[-1]["t"] == "m":
            toks[-1]["v"] = SMALL_Y[ch]
            toks[-1]["len"] = 1.0
        elif ch in SMALL_V and toks and toks[-1]["t"] == "m":
            toks[-1]["v"] = SMALL_V[ch]
        elif ch in "んン":
            toks.append({"t": "m", "c": "", "v": "n", "len": 1.0})
        elif ch in "っッ":
            toks.append({"t": "p", "s": 0.06})
        elif ch == "ー" and toks and toks[-1]["t"] == "m":
            toks.append({"t": "m", "c": "", "v": toks[-1]["v"], "len": 1.0})
        elif is_kanji(ch):
            n = 1 if ch in ONE_MORA_KANJI else 2
            for k in range(n):
                h = ord(ch) * 31 + k * 17
                toks.append({"t": "m", "c": KANJI_CONS[h % len(KANJI_CONS)] if k == 0 else KANJI_CONS[(h // 7) % len(KANJI_CONS)],
                             "v": "aiueo"[(h // 3) % 5], "len": 1.0})
        elif ch in PAUSE_SHORT:
            toks.append({"t": "p", "s": 0.17})
        elif ch in PAUSE_LONG:
            toks.append({"t": "p", "s": 0.32})
        # ほかの文字 (「」・英数字・空白) は無視
    if ell:
        toks.append({"t": "p", "s": min(0.14 * ell + 0.1, 0.55)})
    # 間の連続をまとめ、最後の間は落とす
    out = []
    for t in toks:
        if t["t"] == "p" and out and out[-1]["t"] == "p":
            out[-1]["s"] = max(out[-1]["s"], t["s"])
        else:
            out.append(t)
    while out and out[-1]["t"] == "p":
        out.pop()
    return out


def mora_count(text):
    return sum(1 for t in tokenize(text) if t["t"] == "m")


# ----------------------------------------------------------------------------- 計画 (拍の時刻)
def plan(text, spk, avail, rng):
    """拍と間に時刻を割り当てる。話す長さは台詞の表示時間 avail の 5 割〜(avail - 余白) に収める。返り値 (events, 長さ)"""
    toks = tokenize(text)
    base = spk["mora"]
    ev = []
    t = 0.0
    for tok in toks:
        if tok["t"] == "p":
            ev.append({"t": "p", "at": t, "d": tok["s"]})
            t += tok["s"]
        else:
            d = base * tok["len"] * (1 + rng.uniform(-spk["jitter"], spk["jitter"]))
            ev.append({"t": "m", "at": t, "d": d, "c": tok["c"], "v": tok["v"]})
            t += d
    t_nat = max(t, 0.2)
    tail_keep = 0.5 * spk["rev"][0] * (spk["rev"][1] > 0.15)
    hi = max(0.6, avail - 0.3 - tail_keep)
    lo = 0.5 * avail
    target = min(max(t_nat, lo), hi)
    s = target / t_nat
    if s < 0.6:
        # 詰めすぎると一つの塊になる: 速さは 6 割までにして、入りきらない尻は落とす (つぶやきなので、言い切らずに消えてよい)
        s = 0.6
    for e in ev:
        e["at"] *= s
        e["d"] *= s
    ev = [e for e in ev if e["at"] + e["d"] * 0.8 <= target]
    while ev and ev[-1]["t"] == "p":
        ev.pop()
    if not ev:
        ev = [{"t": "m", "at": 0.0, "d": min(base, target), "c": "", "v": "n"}]
    return ev, target


# ----------------------------------------------------------------------------- 音の部品
def smooth(x, ms):
    k = max(1, int(SR * ms / 1000))
    if k < 2:
        return x
    w = np.hanning(k * 2 + 1)
    w /= w.sum()
    pad = np.pad(x, ((0, 0), (k, k)) if x.ndim == 2 else (k, k), mode="edge")
    if x.ndim == 2:
        return np.stack([np.convolve(r, w, mode="valid") for r in pad])
    return np.convolve(pad, w, mode="valid")


def saw(f0):
    ph = np.cumsum(f0 / SR)
    return 2.0 * (ph % 1.0) - 1.0


def formant_filter(src, forms, bws, gains):
    """forms: (3, n) の Hz。ブロックごとに係数を替えて 3 + 1 の共鳴を足す (状態を引き継ぐ)"""
    n = len(src)
    out = np.zeros(n)
    nf = len(gains)
    zis = [np.zeros(2) for _ in range(nf)]
    B = 128
    for s in range(0, n, B):
        e = min(n, s + B)
        m = (s + e) // 2
        seg = src[s:e]
        for k in range(nf):
            f = forms[k][m] if k < 3 else min(3300 + 0.0, 0.45 * SR)
            f = float(np.clip(f, 120, 0.45 * SR))
            w0 = 2 * math.pi * f / SR
            al = math.sin(w0) / (2 * (f / bws[k]))
            y, zis[k] = lfilter([al, 0, -al], [1 + al, -2 * math.cos(w0), 1 - al], seg, zi=zis[k])
            out[s:e] += gains[k] * y
    return out


def band_noise(n, rng, lo, hi):
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    gate = ((f >= lo) & (f <= hi)).astype(float)
    gate = np.convolve(gate, np.hanning(9) / np.hanning(9).sum(), mode="same")
    return np.fft.irfft(spec * gate, n)


def env_bump(n, a, b, att, rel, curve=1.0):
    """[a, b) 秒の山 (att 秒で立ち上がり rel 秒で落ちる)。n は全体の標本数"""
    e = np.zeros(n)
    i0, i1 = max(0, int(a * SR)), min(n, int(b * SR))
    if i1 <= i0 + 2:
        return e
    x = np.arange(i1 - i0) / SR
    up = np.clip(x / max(att, 1e-3), 0, 1)
    dn = np.clip(((i1 - i0) / SR - x) / max(rel, 1e-3), 0, 1)
    e[i0:i1] = (np.sin(up * math.pi / 2) * np.sin(dn * math.pi / 2)) ** curve
    return e


def consonant(n, ev, rng, strength=1.0):
    """子音の息・破裂。(型, 帯域, 長さ, 強さ)"""
    c = ev["c"]
    if not c:
        return np.zeros(n)
    kind = {"k": (1800, 5000, 0.03, 0.7), "t": (3000, 7000, 0.022, 0.6), "p": (600, 3000, 0.02, 0.5),
            "g": (1500, 4000, 0.02, 0.35), "d": (2500, 6000, 0.016, 0.3), "b": (400, 2500, 0.015, 0.3),
            "s": (4500, 9500, 0.07, 0.55), "z": (4000, 9000, 0.05, 0.35), "h": (800, 6000, 0.06, 0.45)}.get(c)
    if not kind:
        return np.zeros(n)
    lo, hi, dur, amp = kind
    m = band_noise(n, rng, lo, min(hi, 0.45 * SR))
    return m * env_bump(n, ev["at"] - 0.004, ev["at"] + dur, 0.004, dur * 0.8) * amp * strength


def reverb(y, seconds, mix, rng):
    if mix <= 0.001 or seconds <= 0.01:
        return y
    n = int(SR * seconds)
    t = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-t * 6.9 / seconds)  # 60 dB の減衰
    ir[: int(0.012 * SR)] *= np.linspace(0, 1, int(0.012 * SR))  # 立ち上がりをなめらかに
    ir = np.convolve(ir, np.ones(5) / 5, mode="same")  # 少しこもらせる
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    wet = fftconvolve(np.pad(y, (0, n)), ir)[: len(y) + n]
    dry = np.pad(y, (0, n))
    return dry * (1 - mix * 0.6) + wet * mix * 1.1


def comb_hollow(y, f0, amount):
    """うつろ: 短い遅れを足した共鳴"""
    if amount <= 0:
        return y
    d = int(SR / (f0 * 1.5))
    out = y.copy()
    for k in (1, 2, 3):
        if len(y) > d * k:
            out[d * k:] += y[:-d * k] * amount * (0.55 ** k) * (-1) ** k
    return out


def tilt(y, bright):
    """高域を持ち上げる/落とす (簡単な一次)"""
    if abs(bright) < 0.01:
        return y
    if bright > 0:
        return y + bright * 0.9 * lfilter([1, -0.95], [1], y)
    return lfilter([1 + bright * 0.3], [1, bright * 0.6], y)


# ----------------------------------------------------------------------------- 様式
def formants_for(v, spk):
    return np.array(VOWELS[v], float) * spk["form"]


def f0_contour_semis(events, rng):
    """文全体の抑揚 (半音)。頭が少し高く、だんだん下がり、間でゆるく戻る"""
    n_m = sum(1 for e in events if e["t"] == "m")
    base = []
    drift = 0.0
    k = 0
    for e in events:
        if e["t"] == "p":
            drift += 1.0
            continue
        pos = k / max(1, n_m - 1)
        base.append(2.0 - 3.6 * pos + drift + rng.uniform(-0.4, 0.4))
        k += 1
    return base


def style_a(events, length, spk, rng):
    """A: 音節のブリップ。拍ごとに高さが違う短い母音 (どうぶつの森風)"""
    n = int(SR * length) + 2
    f0 = np.full(n, float(spk["f0"]))
    amp = np.zeros(n)
    forms = np.zeros((3, n))
    forms[:] = np.array(VOWELS["a"])[:, None] * spk["form"]
    cons = np.zeros(n)
    semis = f0_contour_semis(events, rng)
    steps = [-3, -1, 0, 2, 4, 5, 7]
    k = 0
    last = 0
    for e in events:
        if e["t"] != "m":
            continue
        i0 = int(e["at"] * SR)
        i1 = min(n, int((e["at"] + e["d"]) * SR))
        vi1 = min(n, int((e["at"] + e["d"] * 0.84) * SR))
        st = semis[k] + steps[rng.integers(len(steps))] * 0.55
        k += 1
        last = st
        pitch = spk["f0"] * 2 ** (st / 12)
        x = np.linspace(0, 1, max(1, vi1 - i0))
        f0[i0:i1] = pitch
        f0[i0:vi1] *= 2 ** (-0.9 * np.exp(-x * 9) / 12)  # 頭のしゃくり
        amp += env_bump(n, e["at"], e["at"] + e["d"] * 0.84, 0.012, e["d"] * 0.45, 0.8) * (1.0 - 0.18 * x.mean())
        forms[:, i0:i1] = formants_for(e["v"], spk)[:, None]
        cons += consonant(n, e, rng)
    return dict(f0=f0, amp=amp, forms=forms, vmix=np.ones(n), nmix=np.full(n, spk["breath"] * 0.35), cons=cons, whisper=False)


def vowel_track(events, n, spk, smooth_ms):
    """拍の中心で母音の formant を結んで滑らかに動かす"""
    xs, ys = [0.0], []
    ms = [e for e in events if e["t"] == "m"]
    first = formants_for(ms[0]["v"], spk) if ms else formants_for("a", spk)
    ys.append(first)
    for e in ms:
        xs.append((e["at"] + e["d"] * 0.5))
        ys.append(formants_for(e["v"], spk))
    xs.append(n / SR)
    ys.append(ys[-1])
    t = np.arange(n) / SR
    forms = np.stack([np.interp(t, xs, [y[k] for y in ys]) for k in range(3)])
    return smooth(forms, smooth_ms)


def style_b(events, length, spk, rng):
    """B: 息のささやきのつぶやき。ほとんど息 (雑音) を母音の形で動かす"""
    n = int(SR * length) + 2
    forms = vowel_track(events, n, spk, 45)
    amp = np.zeros(n)
    cons = np.zeros(n)
    k = 0
    semis = f0_contour_semis(events, rng)
    f0 = np.full(n, float(spk["f0"]))
    for e in events:
        if e["t"] != "m":
            continue
        a = e["at"] - e["d"] * 0.15
        b = e["at"] + e["d"] * 1.05
        amp += env_bump(n, max(0, a), b, e["d"] * 0.45, e["d"] * 0.55, 1.0) * rng.uniform(0.7, 1.0)
        i0 = int(e["at"] * SR)
        i1 = min(n, int((e["at"] + e["d"]) * SR))
        f0[i0:i1] = spk["f0"] * 2 ** (semis[k] / 12)
        k += 1
        cons += consonant(n, e, rng, 0.8)
    return dict(f0=f0, amp=amp, forms=forms, vmix=np.full(n, spk["voiced"]), nmix=np.ones(n), cons=cons, whisper=True)


def style_c(events, length, spk, rng):
    """C: 歌うハミング・詠唱。拍をまとめた長い音をなめらかにつなぎ、ビブラートと深い残響"""
    n = int(SR * length) + 2
    forms = vowel_track(events, n, spk, 110)
    scale = [0, 2, 3, 5, 7, 8, 10, 12]  # 旋法 (ドリア・エオリア寄り)
    # 拍を音にまとめる
    notes = []
    cur = None
    for e in events:
        if e["t"] == "p":
            if cur:
                notes.append(cur)
                cur = None
            continue
        if cur is None:
            cur = {"a": e["at"], "b": e["at"] + e["d"]}
        else:
            cur["b"] = e["at"] + e["d"]
        if cur["b"] - cur["a"] >= spk["note"] * (length / max(length, 1)):
            notes.append(cur)
            cur = None
    if cur:
        notes.append(cur)
    amp = np.zeros(n)
    ts = [0.0]
    ps = [0.0]
    deg = 4
    for i, nt in enumerate(notes):
        a, b = nt["a"], nt["b"] + 0.06
        amp += env_bump(n, max(0, a - 0.05), b + 0.1, 0.09, 0.16, 1.0) * rng.uniform(0.85, 1.0)
        deg = int(np.clip(deg + rng.choice([-2, -1, -1, 0, 1, 1, 2]), 0, len(scale) - 1))
        if i == len(notes) - 1:
            deg = 0
        ts += [a + 0.02, b - 0.02]
        ps += [scale[deg] - 7, scale[deg] - 7]
    ts.append(length)
    ps.append(ps[-1])
    t = np.arange(n) / SR
    semis = smooth(np.interp(t, ts, ps), 85)
    vib = 0.28 * np.sin(2 * math.pi * 5.2 * t + rng.uniform(0, 6)) * np.clip(t - 0.15, 0, 0.5) / 0.5
    f0 = spk["f0"] * 2 ** ((semis + vib) / 12)
    # 頭と音の変わり目はハミング (こもる鼻音) に寄せる
    cons = np.zeros(n)
    return dict(f0=f0, amp=amp, forms=forms, vmix=np.ones(n), nmix=np.full(n, spk["breath"] * 0.3), cons=cons, whisper=False, hum=True)


STYLE_FN = {"a": style_a, "b": style_b, "c": style_c}


def render_voice(style, text, speaker, avail, seed_key):
    spk = SPEAKERS[speaker]
    rng = np.random.default_rng(zlib.crc32((seed_key + style + speaker).encode("utf-8")))
    events, length = plan(text, spk, avail, rng)
    if not any(e["t"] == "m" for e in events):
        events = [{"t": "m", "at": 0.0, "d": spk["mora"], "c": "", "v": "n"}]
        length = spk["mora"]
    c = STYLE_FN[style](events, length, spk, rng)
    n = len(c["amp"])
    # 声の源 (重ね)
    voiced = np.zeros(n)
    for st, g, cents in spk["layers"]:
        f = c["f0"] * 2 ** ((st + cents / 100.0) / 12.0)
        if style == "c":
            f = f * (1 + 0.0015 * np.sin(2 * math.pi * (4.7 + 0.3 * rng.uniform()) * np.arange(n) / SR + rng.uniform(0, 6)))
        voiced += g * lfilter([1.0], [1, -0.55], saw(f))
    voiced /= max(1.0, sum(g for _, g, _ in spk["layers"]) ** 0.7)
    noise = lfilter([1.0], [1, -0.4], rng.standard_normal(n)) * 0.6
    src = voiced * c["vmix"] + noise * c["nmix"]
    bws = FORM_BW_WHISPER if c["whisper"] else FORM_BW
    y = formant_filter(src, c["forms"], bws, FORM_GAIN)
    y = y * c["amp"] + c["cons"] * 0.5
    if c["whisper"]:
        y = y * 1.0
    y = comb_hollow(y, spk["f0"], spk["hollow"])
    y = tilt(y, spk["bright"])
    rv, mix = spk["rev"]
    if style == "c":
        rv, mix = rv * 1.6 + 0.4, min(0.7, mix * 1.5 + 0.12)
    elif style == "b":
        rv, mix = rv * 0.8, mix * 0.8
    y = reverb(y, rv, mix, rng)
    return finish(y, avail - 0.05)


def finish(y, max_len=None):
    """音量をそろえ (ジャンプスケア禁止: なだらかな立ち上がり)、無音の尻を切る"""
    # 高域の耳に刺さる成分を抑える (22 kHz mono なので 9 kHz より上は元々出ない)
    peak = np.max(np.abs(y)) + 1e-9
    act = y[np.abs(y) > 0.05 * peak]
    rms = float(np.sqrt(np.mean(act ** 2))) if len(act) else peak
    y = y * (0.10 / (rms + 1e-9))
    y = np.tanh(y * 1.4) / 1.4
    y *= min(1.0, 0.6 / (np.max(np.abs(y)) + 1e-9))
    env = np.abs(y)
    keep = np.nonzero(env > 0.002)[0]
    if len(keep):
        y = y[: min(len(y), keep[-1] + int(0.05 * SR))]
    if max_len is not None and len(y) > int(max_len * SR):
        y = y[: int(max_len * SR)]
    fi = int(0.02 * SR)
    fo = min(len(y), int(0.25 * SR) if max_len is not None and len(y) >= int(max_len * SR) - 2 else int(0.12 * SR))
    y[:fi] *= np.linspace(0, 1, fi)
    y[-fo:] *= np.linspace(1, 0, fo)
    return y


def write_ogg(path, y):
    path.parent.mkdir(parents=True, exist_ok=True)
    with sf.SoundFile(str(path), "w", samplerate=SR, channels=1, format="OGG", subtype="VORBIS") as f:
        f.write(y.astype(np.float32))


# ----------------------------------------------------------------------------- 台詞の一覧・日本語の本文
def load_lang():
    out = {}
    for p in sorted(ASSETS.glob("*/lang/ja_jp.json")):
        out.update(json.loads(p.read_text(encoding="utf-8")))
    return out


def speaker_name(line):
    sp = line["speaker"]
    if sp == "HEAVEN":
        return "heaven"
    if sp == "HERO":
        return "hero"
    label = line.get("label", "").lower()
    for key, name in (("isana", "isana"), ("storyteller", "storyteller"), ("katari", "storyteller"), ("watcher", "watcher")):
        if key in label:
            return name
    return "hero"


def load_manifest():
    return json.loads(MANIFEST.read_text(encoding="utf-8"))["lines"]


def event_name(scene, idx):
    return f"voice.{scene}.l{idx}"


def clip_path(scene, idx):
    return VOICE_DIR / scene / f"l{idx}.ogg"


# ----------------------------------------------------------------------------- 操作
def install(style):
    lang = load_lang()
    lines = load_manifest()
    sounds = {}
    total = 0
    for ln in lines:
        text = lang[ln["text"]]
        y = render_voice(style, text, speaker_name(ln), ln["duration"], ln["scene"] + str(ln["idx"]))
        p = clip_path(ln["scene"], ln["idx"])
        write_ogg(p, y)
        total += p.stat().st_size
        sounds[event_name(ln["scene"], ln["idx"])] = {
            "subtitle": f"subtitles.skyabyss_cinematic.voice.{speaker_name(ln)}",
            "sounds": [{"name": f"skyabyss_cinematic:voice/{ln['scene']}/l{ln['idx']}", "stream": False}],
        }
    (CINE / "sounds.json").write_text(json.dumps(sounds, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (CINE / "voice_style.txt").write_text(style + "\n", encoding="utf-8")
    print(f"style {style}: {len(lines)} clips, {total / 1024:.0f} KiB")


def check():
    lines = load_manifest()
    lang = load_lang()
    sj = json.loads((CINE / "sounds.json").read_text(encoding="utf-8"))
    bad = []
    tot = 0
    for ln in lines:
        name = event_name(ln["scene"], ln["idx"])
        p = clip_path(ln["scene"], ln["idx"])
        if name not in sj:
            bad.append(f"no sound event {name}")
        if ln["text"] not in lang:
            bad.append(f"no ja text {ln['text']}")
        if not p.exists():
            bad.append(f"no clip {p}")
            continue
        tot += p.stat().st_size
        info = sf.info(str(p))
        d = info.frames / info.samplerate
        if info.channels != 1 or info.samplerate != SR:
            bad.append(f"{p.name}: not mono {SR}")
        if not (0.4 * ln["duration"] <= d <= ln["duration"] + 0.05):
            bad.append(f"{name}: clip {d:.2f}s vs line {ln['duration']:.2f}s")
    print(f"{len(lines)} lines, {tot / 1024:.0f} KiB")
    if tot > 8 * 1024 * 1024:
        bad.append("clips over 8 MiB")
    for b in bad:
        print("FAIL", b)
    if bad:
        sys.exit(1)
    print("OK")


REVIEW_LINES = [
    ("hero_a", "opening", 0, "主人公 (心の声) 1"),
    ("heaven_a", "opening", 2, "天の声 1"),
    ("hero_b", "opening", 1, "主人公 (心の声) 2"),
    ("heaven_b", "opening", 4, "天の声 2"),
]
SAMPLER_TEXT = "……あれは、なんだ。"
SAMPLER_NAMES = {"heaven": "天の声", "hero": "主人公", "isana": "イサナ", "storyteller": "語り部", "watcher": "見る者"}
STYLE_INFO = {
    "a": ("A 音節のブリップ", "拍ごとに高さの違う短い母音。どうぶつの森・Undertale のような、軽く弾むつぶやき。リズムは本文の拍数と句読点の間に従う。"),
    "b": ("B 息のささやき", "ほとんど息 (雑音) を母音の形で動かす。声帯を震わせない、ひそひそ話のような低い存在感。ホラー寄りで静か。"),
    "c": ("C 歌うハミング", "拍をまとめた長い音を、母音がなめらかに移りながら歌う。ビブラートと深い残響で、詠唱・聖歌のような響き。"),
}


def review():
    lang = load_lang()
    lines = {(l["scene"], l["idx"]): l for l in load_manifest()}
    REVIEW.mkdir(parents=True, exist_ok=True)
    for old in REVIEW.glob("*.ogg"):
        old.unlink()
    rows = []
    for tag, scene, idx, label in REVIEW_LINES:
        ln = lines[(scene, idx)]
        text = lang[ln["text"]]
        row = {"tag": tag, "label": label, "text": text, "files": {}}
        for st in STYLES:
            y = render_voice(st, text, speaker_name(ln), ln["duration"], scene + str(idx))
            fn = f"{st}_{tag}.ogg"
            write_ogg(REVIEW / fn, y)
            row["files"][st] = fn
        rows.append(row)
    sampler = []
    for sp in SPEAKERS:
        row = {"name": SAMPLER_NAMES[sp], "files": {}}
        for st in STYLES:
            y = render_voice(st, SAMPLER_TEXT, sp, 3.0, "sampler")
            fn = f"{st}_who_{sp}.ogg"
            write_ogg(REVIEW / fn, y)
            row["files"][st] = fn
        sampler.append(row)
    html = REVIEW_TEMPLATE
    html = html.replace("/*ROWS*/", json.dumps(rows, ensure_ascii=False))
    html = html.replace("/*SAMPLER*/", json.dumps(sampler, ensure_ascii=False))
    html = html.replace("/*STYLES*/", json.dumps({k: {"title": v[0], "desc": v[1]} for k, v in STYLE_INFO.items()}, ensure_ascii=False))
    html = html.replace("{{SAMPLER_TEXT}}", SAMPLER_TEXT)
    (REVIEW / "index.html").write_text(html, encoding="utf-8")
    size = sum(p.stat().st_size for p in REVIEW.iterdir())
    print(f"review: {len(rows) * 3 + len(sampler) * 3} clips, folder {size / 1024:.0f} KiB -> {REVIEW}")


REVIEW_TEMPLATE = (Path(__file__).resolve().parent / "voice_review_template.html").read_text(encoding="utf-8") \
    if (Path(__file__).resolve().parent / "voice_review_template.html").exists() else ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--style", choices=STYLES, default="a")
    ap.add_argument("--install", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--review", action="store_true")
    a = ap.parse_args()
    if a.install:
        install(a.style)
    if a.review:
        review()
    if a.check:
        check()
    if not (a.install or a.review or a.check):
        ap.print_help()


if __name__ == "__main__":
    main()
