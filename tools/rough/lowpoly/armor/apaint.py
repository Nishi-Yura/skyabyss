"""防具のドット絵。材質名は 4 巨神で共通 (main sec edge acc cloth trim gem dark)、巨神ごとに中身が変わる。
階級 (common / rare / legend) は trim の色 (灰 / 青み / 金) と光る筋だけを変える。"""
from __future__ import annotations

import numpy as np

from akit import *  # noqa: F401,F403
from akit import hash1, vnoise, BAYER, edge_fields, fill_outside
from paint import PAL, GLOW  # weapons/paint.py の材質の色 (読むだけ)
from wkit import pick, ramp, LIGHT

A_ = np.array


def paint(faces, size, P, N, M, L, titan, rarity="common", plus=0):
    D, K, LU, LV, CW, CH, INS = edge_fields(faces, size)
    H = W = size
    yy, xx = np.mgrid[0:H, 0:W]
    fi = np.where(M >= 0, M, 0)
    owned = M >= 0
    mats = np.array([f.mat for f in faces])[fi]
    bay = BAYER[yy % 4, xx % 4] - 0.5
    lit = N @ LIGHT
    edge = (D < 1.05) & INS
    convex = edge & (K > 24)
    concave = edge & (K < -24)
    X, Y, Z = L[..., 0], L[..., 1], L[..., 2]
    n_lo = vnoise(L * 0.09 + 3, 1)
    n_mid = vnoise(L * 0.3 + 11, 2)
    n_hi = vnoise(L * 0.9 + 5, 7)
    col = np.zeros((H, W, 3))
    glow = np.zeros((H, W), bool)
    pit = hash1(xx * 7 + yy * 13 + fi * 3, 5)

    def put(mask, c):
        nonlocal col
        col = np.where(mask[..., None], c, col)

    def shaded(pal, base, lw=0.30, nw=0.14, extra=0.0, ed=True):
        v = base + lw * lit + nw * (n_lo - 0.5) + 0.10 * (n_mid - 0.5) + 0.07 * bay + extra
        idx = np.floor(v * len(pal)).astype(int)
        if ed:
            idx = idx + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave
        return idx

    m = lambda name: mats == name
    gp = PAL[GLOW[titan]]
    weave = ((LU + LV) % 2 == 0)
    stripe_v = (LU % 3 == 0)

    if titan == "whale":
        B, S, MO = PAL["BONE"], PAL["STONE"], PAL["MOSS"]
        put(m("main"), pick(S, shaded(S, 0.50, 0.30, 0.16) - (pit < 0.05) - (n_hi > 0.82)))
        put(m("sec"), pick(B, shaded(B, 0.66, 0.34, 0.08) - ((np.sin(Y * 1.1 + 3.0 * n_lo) > 0.74) & (n_mid > 0.35)) - (pit < 0.04)))
        put(m("edge"), pick(B, shaded(B, 0.84, 0.26, 0.05) - (pit < 0.03)))
        put(m("acc"), pick(MO, shaded(MO, 0.55, 0.3, 0.14)))
        # 下着: 格子をやめ、暗い石のなめらかな面 (かごに見えない)。縄は縞、骨・石に欠けと暗いひび
        put(m("cloth"), pick(S, shaded(S, 0.30, 0.3, 0.08) - (n_hi > 0.8)))
        put(m("cloth") & (n_lo > 0.74), pick(MO, np.floor(1.6 + 1.4 * lit + bay).astype(int)))
        TN = PAL["TAN"] if "TAN" in PAL else PAL["CORD"]
        put(m("cord"), pick(PAL["CORD"], np.floor(1.2 + 1.2 * lit + 0.8 * bay - 0.9 * ((LU + LV) % 3 == 0)).astype(int)) * 0.62)
        mo = np.isin(mats, ["main"]) & ((n_mid + 0.5 * n_lo + 0.55 * np.clip(N[..., 1], 0, 1)) > 1.30)
        put(mo, pick(MO, np.floor(1.5 + 1.5 * lit + 1.2 * bay).astype(int)))
        chip = np.isin(mats, ["sec", "edge"]) & (hash1(xx * 11 + yy * 5 + fi, 6) < 0.035) & (D > 0.9)
        put(chip, pick(PAL["DARK"], A_(2)))
    elif titan == "tide":
        S, PE, WE, CO, KE = PAL["SHELL"], PAL["PEARL"], PAL["WET"], PAL["CORAL"], PAL["KELP"]
        irid = n_mid * 0.6 + 0.4 * np.sin(Y * 0.5 + X * 0.3)
        pe_idx = shaded(PE, 0.50, 0.26, 0.08)
        tintc = np.where((irid > 0.55)[..., None], A_([-20, 8, 4]), np.where((irid < 0.38)[..., None], A_([6, -4, 10]), A_([0, 0, 0])))
        put(m("main"), pick(S, shaded(S, 0.52, 0.30, 0.12) - (pit < 0.05)) + tintc * 0.4)
        ridge = (np.abs(((Y * 0.5 + 0.12 * X) % 1.0) - 0.5) < 0.1)
        put(m("sec"), pick(PE, pe_idx) + tintc * (pe_idx[..., None] >= 2))
        put(m("edge"), pick(PE, shaded(PE, 0.82, 0.22, 0.05)) + tintc * 0.5)
        oc = PAL["CORALO"]
        cidx = shaded(CO, 0.56, 0.3, 0.14)
        coral = np.where((n_mid > 0.58)[..., None], pick(oc, shaded(oc, 0.56, 0.3, 0.14)), pick(CO, cidx))
        pores = (hash1(xx * 5 + yy * 11 + fi, 3) < 0.08) & (D > 0.9)
        put(m("acc"), np.where(pores[..., None], pick(CO, cidx - 2) * 0.7, coral))
        put(m("cloth"), pick(WE, shaded(WE, 0.40, 0.3, 0.08) - weave * (n_mid > 0.3) - (stripe_v & (n_mid > 0.55))))
        # 帯 (薔薇色の布、縦の織り筋とすり切れ)・海藻の縄・使い込み (様式 C)
        SA = PAL["SASH"]
        fray = (np.floor(Y * 0.8 + X * 0.25) % 3 == 0)
        put(m("sash"), pick(SA, shaded(SA, 0.52, 0.30, 0.10) - fray * (n_mid > 0.35) - (pit < 0.05)))
        put(m("cord"), pick(KE, np.floor(1.4 + 1.6 * lit + 0.8 * bay - 1.0 * ((LU + LV) % 3 == 0)).astype(int)))
        chip = np.isin(mats, ["main", "sec", "edge"]) & (hash1(xx * 11 + yy * 5 + fi, 6) < 0.03) & (D > 0.9)
        put(chip, pick(WE, A_(1)))
        wornm = np.isin(mats, ["main"]) & (hash1(xx * 7 + yy * 3 + fi, 8) < 0.06) & (D > 0.9)
        put(wornm, pick(PE, np.floor(2.0 + 1.5 * lit + bay).astype(int)))
    elif titan == "thunder":
        ST, CP, PA, PL = PAL["STEEL"], PAL["COPPER"], PAL["PATINA"], PAL["PLUME"]
        brush = hash1(np.floor(X * 2.0) * 3 + fi, 6)
        put(m("main"), pick(ST, shaded(ST, 0.52, 0.30, 0.12) + (brush > 0.8) - (brush < 0.15)))
        barb = np.abs(((X * 0.9 + Y * 0.8) % 1.0) - 0.5) < 0.12
        put(m("sec"), pick(PL, shaded(PL, 0.80, 0.26, 0.05) - barb + (np.abs(X) < 0.35)))
        put(m("edge"), pick(PL, shaded(PL, 0.88, 0.22, 0.04)))
        cidx = shaded(CP, 0.56, 0.32, 0.08) + (brush > 0.7) - (brush < 0.2)
        put(m("acc"), pick(CP, cidx))
        verd = np.isin(mats, ["acc"]) & (concave | ((n_mid > 0.72) & (np.abs(N[..., 1]) < 0.7)))
        put(verd, pick(PA, np.floor(1.0 + 1.6 * lit + bay).astype(int)))
        put(m("cloth"), pick(ST, shaded(ST, 0.30, 0.26, 0.06) - weave * (n_mid > 0.3) + (stripe_v & (n_mid > 0.6))))
        # 嵐に焼けた傷み: 焦げた羽先 (soot)・羽の縁の焦げ・銅のひび (暗い線) とひびの中の稲妻 (光る)
        SO = PAL["SOOT"]
        put(m("soot"), pick(SO, np.floor(1.4 + 1.6 * lit + 1.2 * bay + 1.2 * (n_mid - 0.5)).astype(int)))
        put(np.isin(mats, ["sec", "edge", "cloth"]) & (D < 0.8) & (n_mid > 0.55), pick(SO, np.floor(1.6 + 1.4 * lit + bay).astype(int)))
        rn = np.abs(vnoise(L * 0.26 + 9, 5) - 0.5)
        put(m("acc") & (rn < 0.02) & INS & (D > 0.4), pick(PAL["CRACK"], np.floor(0.6 + 1.4 * hash1(xx + yy * 3, 2)).astype(int)))
        vlv = {"common": 0.0, "rare": 0.006, "legend": 0.010}[rarity] + (0.004 if plus >= 5 else 0) + (0.006 if plus >= 10 else 0)
        vn = np.abs(vnoise(L * 0.18 + 21, 3) - 0.5)
        vein = m("acc") & (vn < vlv) & INS & (D > 0.4)
        put(vein, ramp(gp, 0.45 + 0.4 * hash1(xx + yy * 3, 6)))
        glow |= vein
    else:  # ember
        RO, IR, RW, AS = PAL["ROCK"], PAL["IRON"], PAL["ROCKW"], PAL["ASH"]
        facet = hash1(np.floor(Y * 0.5) * 7 + np.floor(X * 0.5) * 3 + fi, 8) * 0.18 - 0.09
        put(m("main"), pick(RO, shaded(RO, 0.66, 0.34, 0.10, extra=facet) - (pit < 0.04)))
        put(m("sec"), pick(IR, shaded(IR, 0.62, 0.30, 0.10) - (n_hi > 0.8)))
        put(m("edge"), pick(IR, shaded(IR, 0.78, 0.30, 0.06) - (pit < 0.03)))
        put(m("acc"), pick(RW, shaded(RW, 0.5, 0.3, 0.14)))
        put(m("cloth"), pick(RW, shaded(RW, 0.58, 0.3, 0.1) - weave * (n_mid > 0.3) - (n_hi > 0.78)))
        # 鍛冶場の使い込み: 鎖・灰・錆び・焦げ
        put(m("chain"), pick(IR, shaded(IR, 0.34, 0.3, 0.1) - (pit < 0.1)))
        put(m("soot"), pick(AS, np.floor(0.6 + 1.0 * lit + bay).astype(int)) * 0.55)
        ashm = np.isin(mats, ["main", "acc", "cloth"]) & (N[..., 1] > 0.35) & ((n_mid + 0.5 * n_lo + 0.4 * N[..., 1]) > 1.18)
        put(ashm, pick(AS, np.floor(1.2 + 1.6 * lit + 1.2 * bay).astype(int)))
        put(np.isin(mats, ["sec", "edge"]) & (hash1(xx * 5 + yy * 9 + fi * 3, 4) < 0.07) & (D > 0.7), pick(RW, np.floor(3.0 + bay).astype(int)))
    put(m("dark"), pick(PAL["DARK"], np.floor(1.2 + 1.2 * (0.5 + 0.5 * lit) + 0.8 * bay).astype(int)))

    # ---- 光る面と巨神ごとの地の光
    gm = m("gem")
    put(gm, ramp(gp, 0.35 + 0.45 * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.08 * bay))
    glow |= gm
    crack_lvl = {"common": 0.012, "rare": 0.018, "legend": 0.028}[rarity]
    if titan == "ember":
        ridge_n = np.abs(vnoise(L * 0.20 + 7, 5) - 0.5)
        crack = np.isin(mats, ["main", "acc"]) & (ridge_n < crack_lvl) & INS & (D > 0.4)
        put(crack, ramp(gp, 0.3 + 0.55 * hash1(xx + yy * 3, 1)))
        glow |= crack

    # ---- 階級の縁の金属
    tp = {"common": PAL["TRIM_N"], "rare": PAL["TRIM_R"], "legend": PAL["TRIM_L"]}[rarity]
    step = 0 if plus < 5 else 1 if plus < 10 else 2
    tidx = shaded(tp, 0.55, 0.30, 0.06) + (step if rarity != "common" else 0)
    put(m("trim"), pick(tp, tidx))
    put(m("trim") & convex, pick(tp, tidx + 1))
    # 光る筋: レア以上は trim の帯の中心に 1 テクセルの線、伝説は sec の面にも
    if rarity in ("rare", "legend") or plus >= 5:
        along_u = CW >= CH
        mid_v = (LV == CH // 2) & (CH >= 3)
        mid_u = (LU == CW // 2) & (CW >= 3)
        pos = np.where(along_u, LU, LV)
        dash = (pos % 8) < 4
        line = np.where(along_u, mid_v, mid_u) & (np.minimum(CW, CH) >= 2) & INS & dash
        tl = m("trim") & line & (np.maximum(CW, CH) >= 6)
        put(tl, ramp(gp, 0.5 + 0.4 * hash1(LU + LV * 3, 2)))
        glow |= tl
    if rarity == "legend" or plus >= 5:
        along_u = CW >= CH
        mid_v = (LV == CH // 2) & (CH >= 4)
        mid_u = (LU == CW // 2) & (CW >= 4)
        line = np.where(along_u, mid_v, mid_u) & INS & (np.maximum(CW, CH) >= 6)
        sl = np.isin(mats, ["sec", "acc"]) & line & (titan != "ember")
        put(sl, ramp(gp, 0.5 + 0.4 * hash1(LU + LV * 3, 4)))
        glow |= sl
    if plus >= 10:   # +10: 骨・珊瑚・羽の面の縁が光り、帯の筋がつながる
        em = np.isin(mats, ["sec", "acc"] if titan != "ember" else ["sec", "main"]) & convex & (lit > -0.1)
        put(em, ramp(gp, 0.55 + 0.3 * hash1(xx + yy, 9)))
        glow |= em
        tl2 = m("trim") & (np.where(CW >= CH, LV == CH // 2, LU == CW // 2)) & INS & (np.maximum(CW, CH) >= 4)
        put(tl2, ramp(gp, 0.6 + 0.3 * hash1(LU + LV * 3, 8)))
        glow |= tl2
    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl_ = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl_ & owned
