# -*- coding: utf-8 -*-
"""点の模型の材質の作り込み (熔岩の大蛇の鱗・割れ目・角の節と同じ考え方を、材質の種類ごとにまとめたもの)。

材質の名前 (設計の m.pal の鍵) から種類を決め、
  relief(): 距離の場を動かして本当に凹凸を彫る (割れ目の溝・板の段・継ぎ目・鋲・血管・穴・節・ひだ・毛の筋)
  paint():  色を描き足す (溝の奥の暗さ・鋲の光・鱗の膨らみ・照り)
大きさは点の間隔 h の倍数なので、模型の大きさが違っても同じ細かさで彫れる。
"""
import numpy as np

import splat_lib as S

FAMILIES = (
    ("glow", ("magma", "ember", "lava", "fire", "glow", "core", "spark", "rune", "light", "flame", "hot")),
    ("eye", ("eye", "iris", "pupil")),
    ("metal", ("iron", "steel", "brass", "gold", "metal", "bronze", "copper", "silver", "chain", "rivet", "blade", "armor", "plate")),
    ("brick", ("brick", "tile", "mason")),
    ("stone", ("basalt", "rock", "stone", "ash", "crust", "obsidian", "slate", "shale", "granite", "salt", "crystal", "ice", "geode", "marble")),
    ("bone", ("bone", "shell", "ivory", "tooth", "teeth", "horn", "chitin", "coral", "claw", "talon", "skull", "pearl", "nacre", "beak")),
    ("flesh", ("flesh", "skin", "meat", "heart", "muscle", "gum", "tongue", "lip", "belly", "vein")),
    ("fur", ("fur", "feather", "wool", "hair", "plume", "mane", "down", "fluff")),
    ("cloth", ("cloth", "leath", "robe", "fabric", "rope", "strap", "sack", "linen", "silk", "cape", "hood", "wrap", "bandage", "sail")),
    ("wood", ("wood", "bark", "plank", "staff", "haft", "root", "branch")),
    ("jelly", ("jelly", "membrane", "glass", "water", "slime", "bubble")),
)


def family(mat):
    m = str(mat).lower()
    for fam, keys in FAMILIES:
        if any(k in m for k in keys):
            return fam
    return "plain"


def relief(P, fams, h, glow):
    """格子の点 P (表面の近く) をどれだけ外へ (負) / 内へ (正) 動かすか"""
    d = np.zeros(len(P))
    f1, f2, idv = S.worley(P, 7.0 * h, 3)
    e = f2 - f1
    st = fams == "stone"
    # 石: 割れ目の溝と、板ごとの段違い
    d += st * (0.9 * h * np.clip(1 - e / 0.07, 0, 1) - 0.4 * h * (idv - 0.5))
    # 煉瓦: 段ごとにずれる目地
    br = fams == "brick"
    if br.any():
        row = np.floor(P[:, 1] / (3.2 * h))
        u = (P[:, 0] + P[:, 2]) / (6.0 * h) + 0.5 * (row % 2)
        joint = (np.abs(P[:, 1] / (3.2 * h) - np.round(P[:, 1] / (3.2 * h))) < 0.12) | (np.abs(u - np.round(u)) < 0.06)
        d += br * joint * 0.6 * h
    # 金属: 大きな板の継ぎ目の溝と、継ぎ目に沿った鋲
    mt = fams == "metal"
    if mt.any():
        m1, m2, mid = S.worley(P, 10.0 * h, 31)
        seam = np.clip(1 - (m2 - m1) / 0.05, 0, 1)
        rv = S.worley(P, 2.2 * h, 37)
        rivet = (rv[0] < 0.22) & ((m2 - m1) < 0.16) & ((m2 - m1) > 0.06)
        d += mt * (0.45 * h * seam - 0.45 * h * rivet)
    # 肉: 盛り上がる血管と、波打つ筋
    fl = fams == "flesh"
    if fl.any():
        v1, v2, vid = S.worley(P, 6.0 * h, 11)
        vein = np.clip(1 - (v2 - v1) / 0.06, 0, 1) * (vid > 0.25)
        wave = np.sin((P @ np.array([0.25, 1.0, 0.15])) / (1.1 * h) + 3.0 * S.worley(P, 9.0 * h, 17)[2])
        d += fl * (-0.65 * h * vein + 0.15 * h * wave)
    # 骨・殻: 細かい穴と、縦に並ぶ節 (角・爪の輪)
    bn = fams == "bone"
    if bn.any():
        p1, p2, pid_ = S.worley(P, 2.4 * h, 23)
        pore = (p1 < 0.22) & (pid_ > 0.55)
        ring = np.abs(np.sin(P[:, 1] / (1.6 * h))) < 0.18
        d += bn * (pore * 0.5 * h + ring * 0.25 * h)
    # 毛・羽: 下へ流れる筋
    fu = fams == "fur"
    if fu.any():
        strand = S.value_noise(P * np.array([1.0, 0.25, 1.0]), 1.4 * h, 41)
        d += fu * (strand - 0.5) * 0.7 * h
    # 布・革: ゆるい縦のひだ
    cl = fams == "cloth"
    if cl.any():
        fold = np.sin((P[:, 0] + 0.6 * P[:, 2]) / (2.6 * h) + 2.0 * S.value_noise(P, 8.0 * h, 43))
        d += cl * fold * 0.3 * h
    # 木: 木目の筋
    wd = fams == "wood"
    if wd.any():
        grain = np.sin((P[:, 0] * 0.3 + P[:, 2] * 0.3) / (0.9 * h) + 4.0 * S.value_noise(P * np.array([1, 0.2, 1]), 5.0 * h, 47))
        d += wd * grain * 0.18 * h
    d[glow] = 0
    d[fams == "eye"] = 0
    d[fams == "jelly"] = 0
    return d


def paint(P, n, fams, col, glow, h, lap, key):
    """色を描き足す。戻り: 色・光る・照り (陰の後で白へ寄せる)"""
    col = col.copy()
    shine = np.zeros(len(P), bool)
    half = np.array([0.25, 0.85, -0.47])
    half /= np.linalg.norm(half)
    spec = np.clip(n @ half, 0, 1)
    st = fams == "stone"
    if st.any():
        f1, f2, idv = S.worley(P, 1.9 * h, 19)
        dome = np.clip(1.22 - 0.7 * f1, 0.6, 1.22)
        e7 = S.worley(P, 7.0 * h, 3)
        crack = (e7[1] - e7[0]) < 0.05
        k = dome * (0.9 + 0.2 * idv) * np.where((f2 - f1) < 0.07, 0.7, 1.0) * np.where(crack, 0.45, 1.0)
        col[st] *= k[st, None]
    br = fams == "brick"
    if br.any():
        row = np.floor(P[:, 1] / (3.2 * h))
        u = (P[:, 0] + P[:, 2]) / (6.0 * h) + 0.5 * (row % 2)
        joint = (np.abs(P[:, 1] / (3.2 * h) - np.round(P[:, 1] / (3.2 * h))) < 0.14) | (np.abs(u - np.round(u)) < 0.07)
        brickid = S.value_noise(np.stack([np.floor(u), row, np.zeros_like(u)], 1), 1.0, 5)
        col[br] *= np.where(joint, 0.55, 0.85 + 0.3 * brickid)[br, None]
    mt = fams == "metal"
    if mt.any():
        m1, m2, mid = S.worley(P, 10.0 * h, 31)
        seam = (m2 - m1) < 0.04
        rv = S.worley(P, 2.2 * h, 37)
        rivet = (rv[0] < 0.22) & ((m2 - m1) < 0.16) & ((m2 - m1) > 0.06)
        col[mt] *= np.where(seam, 0.5, 0.9 + 0.2 * mid)[mt, None]
        col[mt & rivet] = col[mt & rivet] * 0.6 + np.array([235, 225, 205]) * 0.4
        shine |= mt & (spec > 0.94) & ~seam
    fl = fams == "flesh"
    if fl.any():
        v1, v2, vid = S.worley(P, 6.0 * h, 11)
        vein = ((v2 - v1) < 0.05) & (vid > 0.25)
        col[fl & vein] = col[fl & vein] * 0.55 + np.array([70, 0, 30]) * 0.45
        shine |= fl & (spec > 0.975) & (lap > 0)
    bn = fams == "bone"
    if bn.any():
        p1, p2, pid_ = S.worley(P, 2.4 * h, 23)
        pore = (p1 < 0.2) & (pid_ > 0.55)
        ring = np.abs(np.sin(P[:, 1] / (1.6 * h))) < 0.18
        col[bn] *= np.where(pore, 0.55, np.where(ring, 0.82, 1.0))[bn, None]
    fu = fams == "fur"
    if fu.any():
        strand = S.value_noise(P * np.array([1.0, 0.25, 1.0]), 1.4 * h, 41)
        col[fu] *= (0.82 + 0.36 * strand)[fu, None]
    cl = fams == "cloth"
    if cl.any():
        weave = (np.floor(P[:, 0] / h) + np.floor(P[:, 1] / h) + np.floor(P[:, 2] / h)) % 2
        col[cl] *= (0.94 + 0.08 * weave)[cl, None]
    wd = fams == "wood"
    if wd.any():
        grain = np.sin((P[:, 0] * 0.3 + P[:, 2] * 0.3) / (0.9 * h) + 4.0 * S.value_noise(P * np.array([1, 0.2, 1]), 5.0 * h, 47))
        col[wd] *= (0.85 + 0.15 * grain)[wd, None]
    jl = fams == "jelly"
    shine |= jl & (spec > 0.9)
    shine |= (fams == "eye") & (spec > 0.88)
    shine &= ~glow
    return np.clip(col, 0, 255), glow, shine
