"""場面の台詞の声: 架空の言語 (L1 / L2 / L3) の発話を合成する (gen_cinematic_voice.py から呼ばれる)。

日本語の本文の母音には従わない。本文から使うのは「句の数・長さ・句読点の間」だけ (台詞の長さと自然な間を保つため)。
母音・子音・音節の形・強勢・抑揚・語のまとまりはすべてこの言語の規則で、台詞の id から決まる乱数 (seed) で作る。
同じ台詞はいつも同じ音になる。話し手ごとに訛り (母音のずれ・子音の置き換え・速さ・抑揚の幅) がある。

  L1 やわらかく流れる: 開音節・鼻音・流音が多く、二重母音と長い母音、つながる (レガート)
  L2 子音が多い古い言葉: 破裂・摩擦・喉の閉鎖・声門のクリック、閉音節と子音の連なり、短く強勢のある拍
  L3 ささやきの詠唱: 息の声で、ほぼ一定の高さに乗せて唱える。語の繰り返しと長い母音、拍がそろう
"""
import math
import zlib

import numpy as np

# 母音 (F1, F2, F3 Hz)。日本語の あいうえお とは別の組 (前舌円唇・中舌・奥舌の非円唇など)
VOW = {
    "ø": (430, 1450, 2250), "y": (290, 1750, 2150), "ɛ": (560, 1850, 2550), "a": (700, 1400, 2600),
    "ɔ": (580, 880, 2500), "ə": (500, 1500, 2500), "æ": (760, 1700, 2500), "ɨ": (320, 1600, 2400),
    "ɤ": (440, 1050, 2350),
}
NASAL_F = (250, 1150, 2300)

# 子音: kind, 雑音の帯域, 長さ(秒), 強さ, 母音への移りの目標(locus), 有声
CONS = {
    "p": ("stop", 400, 2600, 0.050, 0.55, (250, 1000, 2100), False), "t": ("stop", 2500, 6500, 0.045, 0.6, (320, 1800, 2600), False),
    "k": ("stop", 1300, 4200, 0.055, 0.6, (300, 1500, 2000), False), "q": ("stop", 600, 2200, 0.055, 0.55, (420, 900, 2200), False),
    "b": ("stop", 300, 2000, 0.040, 0.3, (250, 1000, 2100), True), "d": ("stop", 2000, 5500, 0.038, 0.3, (320, 1800, 2600), True),
    "g": ("stop", 1000, 3500, 0.042, 0.3, (300, 1500, 2000), True), "ʔ": ("gap", 0, 0, 0.040, 0.0, None, False),
    "ǃ": ("click", 700, 2600, 0.030, 0.9, (350, 1200, 2300), False),
    "s": ("fric", 3200, 7000, 0.085, 0.5, (350, 1900, 2600), False), "ʃ": ("fric", 2200, 5200, 0.085, 0.55, (350, 1700, 2400), False),
    "x": ("fric", 1100, 3800, 0.075, 0.55, (350, 1300, 2200), False), "h": ("fric", 500, 3600, 0.060, 0.4, None, False),
    "f": ("fric", 2500, 7000, 0.070, 0.35, (300, 1100, 2200), False), "z": ("fric", 2800, 6500, 0.070, 0.3, (350, 1900, 2600), True),
    "v": ("fric", 1500, 5000, 0.060, 0.25, (300, 1100, 2200), True),
    "m": ("nasal", 0, 0, 0.060, 0.0, (250, 1000, 2200), True), "n": ("nasal", 0, 0, 0.055, 0.0, (250, 1500, 2400), True),
    "ŋ": ("nasal", 0, 0, 0.060, 0.0, (250, 1300, 2200), True),
    "l": ("liquid", 0, 0, 0.050, 0.0, (380, 1100, 2700), True), "r": ("liquid", 0, 0, 0.040, 0.0, (450, 1300, 1750), True),
    "j": ("glide", 0, 0, 0.045, 0.0, (280, 2200, 3000), True), "w": ("glide", 0, 0, 0.045, 0.0, (300, 700, 2300), True),
}

LANGS = {
    "l1": dict(
        onset={"l": 3, "m": 3, "n": 3, "r": 2, "j": 2, "w": 2, "h": 2, "s": 1.5, "ʃ": 1, "v": 1.5, "f": .7, "b": .6, "d": .6, "g": .4, "t": .5, "k": .4},
        coda={"n": 3, "m": 1.5, "l": 1, "r": 1, "s": .6, "ŋ": .8},
        shapes={"CV": .62, "V": .14, "CVC": .24}, cluster={"r": 1, "l": 1, "j": 1, "w": 1},
        vowels={"ø": 2, "y": 1, "ɛ": 2, "a": 2, "ɔ": 1.5, "ə": 1.5, "æ": .7}, diph=.30, long=.25,
        vd=0.125, cd=1.0, ratio=0.82, stress="first", wlen={1: 1, 2: 4, 3: 3, 4: 1}, gap=(0.015, 0.04), gap_long=(0.06, 0.1), lilt=1.0,
        att=0.05, rel=0.09, cons_gain=0.7, vmix=0.55, nmix=0.8, bw_whisper=True, chant=False, redup=0.0, glide_gain=1.0),
    "l2": dict(
        onset={"k": 3, "t": 3, "p": 1.5, "q": 1.5, "g": 1.5, "d": 1.5, "x": 2.5, "ʃ": 1.5, "s": 1.5, "ʔ": 1.2, "r": 1.5, "z": 1, "b": .8, "h": .5, "ǃ": .4},
        coda={"k": 2, "t": 2, "x": 2, "r": 1.5, "s": 1, "ʔ": 1, "q": 1, "n": 1, "p": .7},
        shapes={"CVC": .36, "CV": .22, "CCV": .18, "CVCC": .1, "VC": .14}, cluster={"r": 2, "l": 1, "x": 1.5, "w": 1},
        vowels={"ɨ": 2, "ɤ": 2, "ə": 1.5, "a": 2, "ɔ": 1.5, "ɛ": 1, "ø": .6}, diph=.08, long=.05,
        vd=0.085, cd=1.15, ratio=0.72, stress="last", wlen={1: 2, 2: 4, 3: 2, 4: .5}, gap=(0.03, 0.07), gap_long=(0.09, 0.14), lilt=0.7,
        att=0.025, rel=0.06, cons_gain=1.5, vmix=0.62, nmix=0.7, bw_whisper=False, chant=False, redup=0.0, glide_gain=0.6),
    "l3": dict(
        onset={"h": 3, "m": 2.5, "n": 2.5, "s": 2, "ʃ": 2, "l": 1.5, "j": 1.5, "w": 1, "k": 1, "t": 1},
        coda={"n": 2, "m": 2, "s": .5, "ŋ": 1},
        shapes={"CV": .55, "V": .25, "CVC": .2}, cluster={"l": 1, "j": 1},
        vowels={"ɤ": 2, "ɔ": 2, "ø": 1.5, "a": 1.5, "ə": 1, "ɨ": 1}, diph=.2, long=.6,
        vd=0.19, cd=1.0, ratio=0.92, stress="none", wlen={1: 1, 2: 3, 3: 3, 4: 1}, gap=(0.05, 0.1), gap_long=(0.12, 0.2), lilt=0.25,
        att=0.07, rel=0.11, cons_gain=0.8, vmix=0.2, nmix=1.0, bw_whisper=True, chant=True, redup=0.4, glide_gain=1.0),
}

# 話し手ごとの訛り: rate = 拍の長さの倍率、lilt = 抑揚の幅、vlen = 母音の長さ、vs = 母音 (F1, F2) の倍率、
# csub = 子音の置き換え、vsub = 母音の置き換え (確率 0.7)、diph = 二重母音の増減、pitch = 高さの補正 (半音)
ACCENT = {
    "heaven": dict(rate=1.2, lilt=1.2, vlen=1.4, vs=(0.95, 1.0), csub={"k": "h", "t": "s", "p": "f"}, vsub={"ɔ": "ø"}, diph=0.2, pitch=0.0),
    "hero": dict(rate=1.0, lilt=1.0, vlen=1.0, vs=(1.0, 1.0), csub={}, vsub={}, diph=0.0, pitch=0.0),
    "isana": dict(rate=0.85, lilt=1.6, vlen=0.85, vs=(1.0, 1.06), csub={"k": "t", "x": "h", "q": "k"}, vsub={"ɔ": "ɛ"}, diph=-0.05, pitch=1.0),
    "storyteller": dict(rate=1.25, lilt=0.7, vlen=1.25, vs=(0.92, 0.94), csub={"s": "ʃ", "t": "d", "k": "g"}, vsub={"ɛ": "ə", "a": "ɔ"}, diph=0.0, pitch=-1.0),
    "watcher": dict(rate=1.1, lilt=0.5, vlen=1.2, vs=(0.88, 0.9), csub={"p": "f", "t": "s", "k": "x", "b": "v", "g": "x"}, vsub={"a": "ɨ", "ɛ": "ɨ"}, diph=-0.05, pitch=-0.5),
}


def _pick(rng, weights):
    ks = list(weights)
    w = np.array([weights[k] for k in ks], float)
    return ks[int(rng.choice(len(ks), p=w / w.sum()))]


def _syllable(lg, rng):
    shape = _pick(rng, lg["shapes"])
    on, co = [], []
    # shape の読み方: V より前の C は onset、後の C は coda
    vi = shape.index("V")
    for _ in shape[:vi]:
        if not on:
            on.append(_pick(rng, lg["onset"]))
        else:
            on.append(_pick(rng, lg["cluster"]))
    for _ in shape[vi + 1:]:
        co.append(_pick(rng, lg["coda"]))
    v1 = _pick(rng, lg["vowels"])
    v2 = _pick(rng, lg["vowels"]) if rng.uniform() < lg["diph"] else None
    if v2 == v1:
        v2 = None
    return dict(on=on, v=(v1, v2), co=co, long=bool(rng.uniform() < lg["long"]))


def _word(lg, rng, n):
    return [_syllable(lg, rng) for _ in range(n)]


_LEX = {}


def lexicon(name):
    """言語ごとに決まった「よく出る語」(台詞をまたいで同じ語が現れる)"""
    if name not in _LEX:
        rng = np.random.default_rng(zlib.crc32(("lexicon-" + name).encode()))
        lg = LANGS[name]
        _LEX[name] = {n: [_word(lg, rng, n) for _ in range(5)] for n in (1, 2, 3)}
    return _LEX[name]


def _dialect(syl, ac, rng):
    s = dict(syl)
    s["on"] = [ac["csub"].get(c, c) for c in syl["on"]]
    s["co"] = [ac["csub"].get(c, c) for c in syl["co"]]
    v1, v2 = syl["v"]
    if v1 in ac["vsub"]:
        v1 = ac["vsub"][v1]
    if v2 and v2 in ac["vsub"]:
        v2 = ac["vsub"][v2]
    if v2 == v1:
        v2 = None
    s["v"] = (v1, v2)
    return s


def _phrase_len(lg, ac, words, pause):
    """見積もりの長さ (秒)。乱数なしの平均"""
    t = 0.0
    for wi, w in enumerate(words):
        for syl in w:
            v = lg["vd"] * ac["rate"] * ac["vlen"] * (1.1 + 0.5 * syl["long"]) * (1.25 if syl["v"][1] else 1.0)
            c = sum(CONS[x][3] for x in syl["on"] + syl["co"]) * lg["cd"] * ac["rate"] * 0.9
            t += v + c
        if wi < len(words) - 1:
            t += (0.7 * sum(lg["gap"]) / 2 + 0.3 * sum(lg["gap_long"]) / 2) * ac["rate"]
    return t + (pause if pause > 0 else 0.0)


def _phrases(G, text):
    """本文 → [(拍の数, 後ろの間 秒)]。母音は使わない (句の長さと間だけ)"""
    toks = G.tokenize(text)
    out, m = [], 0
    for t in toks:
        if t["t"] == "m":
            m += 1
        else:
            if m:
                out.append([m, t["s"]])
            elif out:
                out[-1][1] = max(out[-1][1], t["s"])
            m = 0
    if m:
        out.append([m, 0.0])
    return out or [[1, 0.0]]


def build(G, name, text, speaker, avail, seed_key, spk, rng_line):
    """台詞 1 つの声の部品 (gen_cinematic_voice の合成へ渡す c) と長さを返す。(c, length)"""
    SR = G.SR
    lg = LANGS[name]
    ac = ACCENT[speaker]
    rng = np.random.default_rng(zlib.crc32((seed_key + "|" + name + "|" + speaker).encode("utf-8")))
    lex = lexicon(name)
    phrases = _phrases(G, text)
    question = text.rstrip("」』 ").endswith(("？", "?"))
    tail_keep = 0.5 * spk["rev"][0] * (spk["rev"][1] > 0.15)
    hi = max(0.6, avail - 0.3 - tail_keep)
    lo = 0.5 * avail
    shrink = 1.0
    for _ in range(4):
        r2 = np.random.default_rng(zlib.crc32((seed_key + "|" + name + "|" + speaker + "|g").encode("utf-8")))
        plan_p = []
        total = 0.0
        for m, pause in phrases:
            n_syl = max(1, int(round(m * lg["ratio"] * shrink * r2.uniform(0.88, 1.12))))
            words = []
            left = n_syl
            while left > 0:
                n = min(left, _pick(r2, lg["wlen"]))
                if n <= 3 and r2.uniform() < 0.3:
                    w = [dict(s) for s in lex[n][int(r2.integers(5))]]
                else:
                    w = _word(lg, r2, n)
                words.append(w)
                if lg["redup"] and r2.uniform() < lg["redup"] and left - n > 0 and n <= 2:
                    words.append([dict(s) for s in w])
                    left -= n
                left -= n
            plan_p.append((words, pause))
        # 時間の見積もり
        total = sum(_phrase_len(lg, ac, w, p) for w, p in plan_p)
        if total <= hi / 0.78 or shrink < 0.3:
            break
        shrink *= max(0.3, (hi / 0.78) / total) ** 1.0
    # 配置 (話す長さは台詞の表示時間の 5 割〜 hi に収める)
    target = min(max(total, lo), hi)
    scale = float(np.clip(target / total, 0.6, 1.6))
    n = int(SR * (total * scale * 1.25 + 0.8)) + 2
    t_axis = np.arange(n) / SR
    amp = np.zeros(n)
    cons = np.zeros(n)
    breath = np.zeros(n)
    gap_mask = np.ones(n)
    f_keys = [(0.0, 0.0)]  # (秒, 半音)
    forms_keys = []  # (秒, F1, F2, F3)
    line_shift = (rng.uniform(-1.3, 1.3) if name != "l3" else rng.uniform(-0.6, 0.6)) + ac["pitch"]
    cur = 0.0
    walk = 0.9
    wcount = 0
    nphr = len(plan_p)
    vs = ac["vs"]
    cg = lg["cons_gain"]

    def fm(v):
        f = VOW[v]
        return (f[0] * vs[0] * spk["form"], f[1] * vs[1] * spk["form"], f[2] * spk["form"])

    def noise_seg(a, b, lo_hz, hi_hz, shape, gain):
        i0, i1 = max(0, int(a * SR)), min(n, int(b * SR))
        if i1 - i0 < 8:
            return
        x = G.band_noise(i1 - i0, rng, lo_hz, min(hi_hz, 0.45 * SR))
        x = x / (np.abs(x).max() + 1e-9)
        k = np.arange(i1 - i0) / (i1 - i0)
        if shape == "burst":
            e = np.exp(-k * 5) * np.minimum(1, k * 30)
        else:
            e = np.sin(np.pi * np.clip(k, 0, 1)) ** 0.7
        cons[i0:i1] += x * e * gain

    for pi, (words, pause) in enumerate(plan_p):
        last_phr = pi == nphr - 1
        nsyl_p = sum(len(w) for w in words)
        si = 0
        for wi, w in enumerate(words):
            for yi, syl0 in enumerate(w):
                syl = _dialect(syl0, ac, rng)
                if ac["diph"] and syl["v"][1] is None and rng.uniform() < max(0.0, ac["diph"]):
                    syl["v"] = (syl["v"][0], _pick(rng, lg["vowels"]))
                if syl["v"][1] == syl["v"][0]:
                    syl["v"] = (syl["v"][0], None)
                st = (lg["stress"] == "first" and yi == 0) or (lg["stress"] == "last" and yi == len(w) - 1 and len(w) > 1) \
                     or (lg["stress"] == "none" and yi == 0 and wcount % 2 == 0) or (len(w) == 1 and lg["stress"] != "none" and rng.uniform() < 0.4)
                vdur = lg["vd"] * ac["rate"] * ac["vlen"] * (1.45 if st else 1.0) * (1.5 if syl["long"] else 1.0) * (1.25 if syl["v"][1] else 1.0) * scale
                vdur *= rng.uniform(0.92, 1.08)
                t0 = cur
                pos = (si + 0.5) / max(1, nsyl_p)
                walk = float(np.clip(walk + rng.uniform(-0.07, 0.07), 0.72, 1.0))
                loud = walk * (1.0 if st else 0.86)
                # onset
                nxt_f = fm(syl["v"][0])
                for ci, c in enumerate(syl["on"]):
                    kind, lo_h, hi_h, d, a, locus, voiced = CONS[c]
                    d = d * lg["cd"] * ac["rate"] * scale * (0.8 if ci else 1.0)
                    c0, c1 = cur, cur + d
                    if kind == "stop":
                        noise_seg(c1 - 0.025 * scale - (0.012 if lg["cd"] > 1.1 else 0.0), c1 + 0.02, lo_h, hi_h, "burst", a * cg * 0.5)
                        if voiced:
                            amp += G.env_bump(n, c0, c1, 0.02, 0.02, 1.0) * 0.18
                    elif kind == "click":
                        noise_seg(c1 - 0.02, c1 + 0.03, lo_h, hi_h, "burst", a * cg * 0.8)
                    elif kind == "fric":
                        noise_seg(c0, c1, lo_h, hi_h, "fric", a * cg * 0.45)
                        if voiced:
                            amp += G.env_bump(n, c0, c1, 0.02, 0.02, 1.0) * 0.3
                    elif kind in ("nasal", "liquid", "glide"):
                        gain = {"nasal": 0.7, "liquid": 0.8, "glide": 0.75}[kind]
                        amp += G.env_bump(n, c0, c1 + 0.02, 0.015, 0.02, 1.0) * gain * loud
                    elif kind == "gap":
                        i0, i1 = int(c0 * SR), min(n, int((c1 + 0.01) * SR))
                        gap_mask[i0:i1] *= 0.0
                    if locus is not None:
                        forms_keys.append((c0 + d * 0.5, locus[0] * spk["form"], locus[1] * spk["form"], locus[2] * spk["form"]))
                    cur = c1
                # vowel
                v0, v1 = cur, cur + vdur
                att = lg["att"] * (1.3 if name == "l3" else 1.0)
                amp += G.env_bump(n, v0 - 0.01, v1 + 0.02, att, lg["rel"], 0.9) * loud
                forms_keys.append((v0 + vdur * 0.25, *nxt_f))
                if syl["v"][1]:
                    forms_keys.append((v0 + vdur * 0.8, *fm(syl["v"][1])))
                else:
                    forms_keys.append((v0 + vdur * 0.85, *nxt_f))
                # 抑揚 (半音)
                if lg["chant"]:
                    st_semi = (0.0 if wcount % 3 else 1.5) if not (last_phr and pos > 0.7) else -3.0
                    st_semi += rng.uniform(-0.12, 0.12)
                else:
                    st_semi = 1.4 - 2.4 * pos + (1.8 * lg["lilt"] * ac["lilt"] if st else 0.0) + rng.uniform(-0.4, 0.4)
                    st_semi *= (0.6 + 0.4 * ac["lilt"])
                    if yi == 0 and wi == 0:
                        st_semi += 0.6
                    if pos > 0.65:
                        if last_phr:
                            st_semi += (2.8 if question else -2.4) * ((pos - 0.65) / 0.35) * ac["lilt"]
                        elif pause < 0.25:
                            st_semi += 1.6 * ((pos - 0.65) / 0.35)
                f_keys.append((v0 + vdur * 0.5, st_semi + line_shift))
                cur = v1
                # coda
                for ci, c in enumerate(syl["co"]):
                    kind, lo_h, hi_h, d, a, locus, voiced = CONS[c]
                    d = d * lg["cd"] * ac["rate"] * scale * 0.85
                    c0, c1 = cur, cur + d
                    if kind == "stop":
                        noise_seg(c0 + d * 0.35, c1 + 0.015, lo_h, hi_h, "burst", a * cg * 0.4)
                    elif kind == "fric":
                        noise_seg(c0, c1, lo_h, hi_h, "fric", a * cg * 0.4)
                    elif kind in ("nasal", "liquid"):
                        amp += G.env_bump(n, c0, c1 + 0.03, 0.02, 0.04, 1.0) * 0.6 * loud
                        forms_keys.append((c0 + d * 0.5, *(x * spk["form"] for x in CONS[c][5])))
                    elif kind == "gap":
                        i0, i1 = int(c0 * SR), min(n, int((c1 + 0.01) * SR))
                        gap_mask[i0:i1] *= 0.0
                    cur = c1
                si += 1
            wcount += 1
            # 語のあいだの微かな間
            if wi < len(words) - 1:
                gl = lg["gap_long"] if rng.uniform() < 0.3 else lg["gap"]
                cur += rng.uniform(*gl) * ac["rate"] * scale
        # 句の間 (台詞の句読点の間)
        if pi < nphr - 1:
            gp = pause * scale if pause > 0 else 0.15 * scale
            if gp >= 0.15:
                breath += G.env_bump(n, cur + 0.02, cur + min(gp, 0.3) - 0.01, 0.05, 0.08, 1.0) * G.band_noise(n, rng, 600, 4500) * 0.2
            cur += gp
    length = min(max(cur, 0.3), hi + 0.01)
    # 連続量にまとめる
    forms_keys.sort(key=lambda k: k[0])
    if not forms_keys:
        forms_keys = [(0.0, *fm("ø"))]
    xs = [0.0] + [k[0] for k in forms_keys] + [n / SR]
    ys = [forms_keys[0][1:]] + [k[1:] for k in forms_keys] + [forms_keys[-1][1:]]
    forms = np.stack([np.interp(t_axis, xs, [y[i] for y in ys]) for i in range(3)])
    forms = G.smooth(forms, 38 if name == "l2" else 55 if name == "l1" else 80)
    f_keys.sort(key=lambda k: k[0])
    fx = [k[0] for k in f_keys] + [n / SR]
    fy = [k[1] for k in f_keys] + [f_keys[-1][1]]
    semis = G.smooth(np.interp(t_axis, fx, fy), 45 if name != "l3" else 90)
    drift = 0.15 * np.sin(2 * math.pi * 4.6 * t_axis + rng.uniform(0, 6)) * np.clip(t_axis - 0.2, 0, 0.4) / 0.4
    f0 = spk["f0"] * 0.9 * 2 ** ((semis + drift) / 12)
    amp = np.minimum(amp, 1.0) * gap_mask
    gains = (1.0, 0.5, 0.25, 0.1) if name != "l2" else (1.0, 0.6, 0.3, 0.14)
    c = dict(f0=f0, amp=amp, forms=forms, cons=cons, breath=breath, whisper=lg["bw_whisper"],
             vmix=np.full(n, lg["vmix"]), nmix=np.full(n, lg["nmix"]), gains=gains)
    return c, length
