"""巨神ごとの材質の描き込み。材質名は 4 巨神で共通 (main edge spine guard grip wrap acc gem trim dark) で、
巨神ごとに中身が変わる。階級 (rarity) と強化 (plus) は trim の色・光る画素 (inlay) だけを変える。"""
from __future__ import annotations

import numpy as np

from wkit import *  # noqa: F401,F403
from wkit import pick, ramp, LIGHT, hash1, vnoise, BAYER, edge_fields, fill_outside

A_ = np.array
PAL = dict(
    # 岩鯨
    BONE=A_([(52, 44, 44), (96, 84, 70), (140, 124, 100), (182, 166, 132), (216, 202, 168), (240, 232, 206)]),
    STONE=A_([(40, 46, 58), (56, 62, 76), (72, 80, 94), (92, 100, 114), (112, 120, 132), (134, 142, 152)]),
    MOSS=A_([(26, 42, 30), (42, 64, 36), (62, 90, 46), (88, 118, 60)]),
    CYAN=A_([(70, 160, 200), (110, 210, 240), (170, 238, 252), (230, 252, 255)]),
    # 潮殻
    SHELL=A_([(34, 78, 74), (46, 100, 92), (60, 124, 108), (78, 148, 128), (100, 170, 146), (126, 192, 164)]),
    PEARL=A_([(150, 142, 150), (186, 178, 186), (216, 208, 214), (238, 232, 236), (252, 248, 250)]),
    WET=A_([(36, 52, 58), (52, 72, 78), (70, 94, 100), (92, 118, 122), (116, 142, 144), (142, 166, 164)]),
    CORAL=A_([(150, 48, 84), (190, 66, 104), (222, 92, 128), (242, 126, 154), (255, 164, 182)]),
    CORALO=A_([(176, 78, 24), (214, 106, 34), (240, 138, 52), (255, 172, 84)]),
    KELP=A_([(26, 66, 38), (38, 92, 48), (56, 120, 62), (82, 148, 82)]),
    SASH=A_([(92, 42, 66), (134, 60, 88), (172, 86, 112), (204, 120, 142), (228, 160, 174)]),
    PINK=A_([(240, 130, 180), (255, 170, 205), (255, 210, 228), (255, 240, 248)]),
    # 雷翼
    STEEL=A_([(26, 30, 46), (38, 44, 64), (52, 60, 84), (68, 78, 106), (86, 98, 128), (108, 122, 152)]),
    COPPER=A_([(84, 46, 28), (128, 70, 38), (172, 100, 52), (212, 136, 76), (240, 176, 112)]),
    PATINA=A_([(40, 92, 82), (60, 124, 104), (88, 156, 124)]),
    PLUME=A_([(110, 122, 150), (150, 162, 188), (190, 200, 220), (226, 232, 246), (248, 250, 255)]),
    VEIN=A_([(120, 176, 240), (170, 214, 255), (220, 240, 255), (255, 255, 255)]),
    CORD=A_([(150, 140, 120), (186, 176, 152), (214, 206, 180), (234, 228, 206)]),
    # 余燼
    ROCK=A_([(14, 13, 16), (24, 23, 27), (36, 35, 40), (50, 48, 54), (66, 64, 70), (84, 82, 88)]),
    IRON=A_([(36, 34, 38), (58, 54, 58), (84, 78, 80), (112, 104, 102), (142, 132, 126), (174, 162, 150)]),
    ROCKW=A_([(20, 15, 15), (32, 25, 24), (46, 38, 35), (62, 53, 48), (80, 69, 62)]),
    LAVA=A_([(150, 40, 10), (210, 70, 16), (240, 112, 26), (255, 164, 48), (255, 218, 110)]),
    HAFT=A_([(30, 22, 20), (50, 36, 30), (72, 52, 40), (98, 72, 54)]),
    ASH=A_([(70, 68, 70), (98, 96, 98), (128, 126, 126), (156, 154, 152)]),
    # 階級の縁の金属 (巨神によらず共通。レア = 青み、伝説 = 金)
    TRIM_N=A_([(40, 42, 50), (62, 66, 76), (88, 92, 104), (116, 120, 132)]),
    TRIM_R=A_([(46, 76, 128), (70, 110, 180), (110, 156, 222), (170, 204, 246), (224, 238, 255)]),
    TRIM_L=A_([(132, 90, 24), (192, 138, 36), (238, 188, 66), (255, 224, 126), (255, 246, 196)]),
    DARK=A_([(10, 10, 16), (20, 20, 28), (34, 34, 46)]),
)
GLOW = dict(whale="CYAN", tide="PINK", thunder="VEIN", ember="LAVA")


def paint(faces, size, P, N, M, L, titan, rarity="common", plus=0, inlay=None, edge_mats=(), tint=None):
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
    env = dict(L=L, N=N, mats=mats, convex=convex, concave=concave, D=D, INS=INS, K=K, lit=lit, edge=edge, X=X, Y=Y, Z=Z,
               LU=LU, LV=LV, CW=CW, CH=CH, fi=fi)

    # ---- 巨神ごとの材質
    if titan == "whale":
        B, S, MO = PAL["BONE"], PAL["STONE"], PAL["MOSS"]
        ring_ = np.sin(Y * 1.15 + 3.0 * n_lo) > 0.72
        put(m("main"), pick(B, shaded(B, 0.70, 0.34, 0.08) - ring_ * (n_mid > 0.35) - (pit < 0.04)))
        put(m("edge"), pick(B, shaded(B, 0.84, 0.26, 0.05) - (pit < 0.03)))
        put(m("spine"), pick(S, shaded(S, 0.50, 0.30, 0.16) - (pit < 0.05) - (n_hi > 0.8)))
        put(m("guard"), pick(S, shaded(S, 0.54, 0.30, 0.16) - (pit < 0.05) - (n_hi > 0.8)))
        put(m("grip"), pick(S, shaded(S, 0.30, 0.24, 0.1)))
        stripe = (np.floor(Y * 0.85) % 2) == 0
        put(m("wrap"), pick(MO, shaded(MO, 0.5, 0.3, 0.1) - stripe))
        put(m("acc"), pick(MO, shaded(MO, 0.55, 0.3, 0.14)))
        # 苔: 石の上向きの面のまだら
        mo = np.isin(mats, ["guard", "spine", "grip"]) & ((n_mid * 1.0 + 0.5 * n_lo + 0.5 * np.clip(N[..., 1], 0, 1)) > 1.32)
        put(mo, pick(MO, np.floor(1.5 + 1.5 * lit + 1.2 * bay).astype(int)))
        # 使い込み (様式 C): ぼろ布・骨の暗いひび・欠けた画素
        put(m("cloth"), pick(PAL["TRIM_N"], shaded(PAL["TRIM_N"], 0.42, 0.3, 0.1) - (n_hi > 0.7)))
        put(m("cloth") & (n_lo > 0.66), pick(MO, np.floor(1.4 + 1.4 * lit + bay).astype(int)))
        rn = np.abs(vnoise(L * 0.24 + 7, 5) - 0.5)
        ck = np.isin(mats, ["main", "edge"]) & (rn < 0.0) & INS & (D > 0.4)
        put(ck, pick(PAL["DARK"], A_(1)))
        dark_c = PAL["DARK"]
    elif titan == "tide":
        S, PE, WE, CO, KE = PAL["SHELL"], PAL["PEARL"], PAL["WET"], PAL["CORAL"], PAL["KELP"]
        irid = n_mid * 0.6 + 0.4 * np.sin(Y * 0.5 + X * 0.3)
        pe_idx = shaded(PE, 0.62, 0.26, 0.08)
        pcol = pick(PE, pe_idx)
        tintc = np.where((irid > 0.55)[..., None], A_([-20, 8, 4]), np.where((irid < 0.38)[..., None], A_([6, -4, 10]), A_([0, 0, 0])))
        put(m("main"), pcol + tintc * (pe_idx[..., None] >= 2))
        put(m("edge"), pick(PE, shaded(PE, 0.80, 0.22, 0.05)) + tintc * 0.5)
        ridge = (np.abs(((Y * 0.5 + 0.12 * X) % 1.0) - 0.5) < 0.1)
        put(m("spine"), pick(S, shaded(S, 0.52, 0.30, 0.12) - ridge))
        put(m("guard"), pick(WE, shaded(WE, 0.55, 0.30, 0.14) - (pit < 0.05)))
        put(m("grip"), pick(WE, shaded(WE, 0.32, 0.24, 0.1)))
        stripe = (np.floor((Y + X * 0.3) * 0.8) % 2) == 0
        put(m("wrap"), pick(KE, shaded(KE, 0.5, 0.3, 0.1) - stripe))
        oc = PAL["CORALO"]
        mixo = n_mid > 0.58
        cidx = shaded(CO, 0.56, 0.3, 0.14)
        coral = np.where(mixo[..., None], pick(oc, shaded(oc, 0.56, 0.3, 0.14)), pick(CO, cidx))
        pores = (hash1(xx * 5 + yy * 11 + fi, 3) < 0.08) & (D > 0.9)
        coral = np.where(pores[..., None], pick(CO, cidx - 2) * 0.7, coral)
        put(m("acc"), coral)
        wet = np.isin(mats, ["guard", "grip"]) & (hash1(xx * 3 + yy * 19, 4) < 0.05) & (lit > 0.2)
        put(wet, pick(WE, A_(5)))
        # 帯 (潮殻の祭具の布): 褪せた薔薇色、縦の織り筋、端はすり切れ
        SA = PAL["SASH"]
        fray = (np.floor(Y * 0.8 + X * 0.25) % 3 == 0)
        put(m("cloth"), pick(SA, shaded(SA, 0.52, 0.30, 0.10) - fray * (n_mid > 0.35) - (pit < 0.05)))
        # 使い込み (様式 C): 藤壺 (石の上の白い点) と、貝の欠けた暗い画素
        bar = np.isin(mats, ["guard", "grip", "spine"]) & (hash1(xx * 13 + yy * 7 + fi * 5, 9) < 0.075) & (D > 0.8)
        put(bar, pick(PE, np.floor(2.0 + 1.5 * lit + bay).astype(int)))
        chip = np.isin(mats, ["main", "edge"]) & (hash1(xx * 11 + yy * 5 + fi, 6) < 0.03) & (D > 0.9)
        put(chip, pick(WE, A_(1)))
        dark_c = PAL["DARK"]
    elif titan == "thunder":
        CP, ST, PA, PL, CD = PAL["COPPER"], PAL["STEEL"], PAL["PATINA"], PAL["PLUME"], PAL["CORD"]
        brush = hash1(np.floor(X * 2.0) * 3 + fi, 6)
        cidx = shaded(CP, 0.56, 0.32, 0.08) + (brush > 0.7) - (brush < 0.2)
        put(m("main"), pick(CP, cidx))
        put(m("edge"), pick(CP, shaded(CP, 0.74, 0.28, 0.05)))
        verd = np.isin(mats, ["main"]) & (concave | ((n_mid > 0.72) & (np.abs(N[..., 1]) < 0.7)))
        put(verd, pick(PA, np.floor(1.0 + 1.6 * lit + bay).astype(int)))
        put(m("spine"), pick(ST, shaded(ST, 0.50, 0.30, 0.12) + (brush > 0.8)))
        put(m("guard"), pick(ST, shaded(ST, 0.56, 0.30, 0.12) + (brush > 0.8)))
        put(m("grip"), pick(ST, shaded(ST, 0.28, 0.24, 0.1)))
        stripe = (np.floor(Y * 0.8) % 2) == 0
        put(m("wrap"), pick(CP, shaded(CP, 0.34, 0.3, 0.08) - stripe))
        barb = np.abs(((X * 0.9 + Y * 0.8) % 1.0) - 0.5) < 0.12
        mid = np.abs(X) < 0.35
        put(m("acc"), pick(PL, shaded(PL, 0.80, 0.26, 0.05) - barb + mid * 1))
        dark_c = PAL["DARK"]
    else:  # ember
        RO, IR, RW, HA, AS = PAL["ROCK"], PAL["IRON"], PAL["ROCKW"], PAL["HAFT"], PAL["ASH"]
        facet = hash1(np.floor(Y * 0.5) * 7 + np.floor(X * 0.5) * 3 + fi, 8) * 0.18 - 0.09
        put(m("main"), pick(RO, shaded(RO, 0.55, 0.34, 0.10, extra=facet) - (pit < 0.04)))
        put(m("edge"), pick(IR, shaded(IR, 0.60, 0.30, 0.06) - (pit < 0.03)))
        put(m("spine"), pick(IR, shaded(IR, 0.42, 0.30, 0.10) - (n_hi > 0.8)))
        put(m("guard"), pick(RW, shaded(RW, 0.55, 0.30, 0.12)))
        grain = (np.sin((X * 2.2 + Z * 1.3) + 5 * n_lo) > 0.6)
        put(m("grip"), pick(HA, shaded(HA, 0.45, 0.26, 0.1) - grain))
        stripe = (np.floor(Y * 0.8) % 2) == 0
        put(m("wrap"), pick(AS, shaded(AS, 0.4, 0.3, 0.1) - stripe))
        put(m("acc"), pick(RW, shaded(RW, 0.5, 0.3, 0.14)))
        dark_c = PAL["DARK"]
    put(m("cord"), pick(PAL["CORD"], shaded(PAL["CORD"], 0.6, 0.3, 0.06)))
    put(m("dark"), pick(PAL["DARK"], np.floor(1.2 + 1.2 * (0.5 + 0.5 * lit) + 0.8 * bay).astype(int)))

    # ---- 光る面 (gem) と巨神ごとの地の光
    gm = m("gem")
    put(gm, ramp(gp, 0.35 + 0.45 * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.08 * bay))
    glow |= gm
    if titan == "ember":
        # 余燼の黒い板の割れ目 (割れ目が光る)。階級・強化で太く・多くなる
        lvl = {"common": 0.010, "rare": 0.018, "legend": 0.026}[rarity] + (0.010 if plus >= 5 else 0) + (0.012 if plus >= 10 else 0)
        ridge_n = np.abs(vnoise(L * 0.20 + 7, 5) - 0.5)
        crack = np.isin(mats, ["main", "acc"]) & (ridge_n < 0.012 + lvl) & INS & (D > 0.4)
        put(crack, ramp(gp, 0.3 + 0.55 * hash1(xx + yy * 3, 1)))
        glow |= crack

    # ---- 階級の縁の金属
    tp = {"common": PAL["TRIM_N"], "rare": PAL["TRIM_R"], "legend": PAL["TRIM_L"]}[rarity]
    step = 0 if plus < 5 else 1 if plus < 10 else 2
    tidx = shaded(tp, 0.55, 0.30, 0.06) + (step if rarity != "common" else 0)
    tcol = pick(tp, tidx)
    put(m("trim"), tcol)

    # ---- 光る画素 (階級 + 強化)。inlay(env, kind) -> bool 配列。kind は rare / legend / p5 / p10
    kinds = []
    if rarity in ("rare", "legend"):
        kinds.append("rare")
    if rarity == "legend":
        kinds.append("legend")
    if plus >= 5:
        kinds.append("p5")
    if plus >= 10:
        kinds.append("p10")
    if inlay is not None:
        for k in kinds:
            gmask = inlay(env, k) & INS
            put(gmask, ramp(gp, 0.45 + 0.4 * hash1(LU + LV * 3, 2) + (0.15 if k == "p10" else 0.0)))
            glow |= gmask
    if plus >= 10 and edge_mats:
        em = np.isin(mats, list(edge_mats)) & convex & (lit > -0.1)
        put(em, ramp(gp, 0.5 + 0.3 * hash1(xx + yy, 9)))
        glow |= em
    # 階級の縁の金属の縁取り (1 画素の明るい線)
    tr = m("trim") & convex
    put(tr, pick(tp, tidx + 1))

    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl_ = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl_ & owned


# ------------------------------------------------------------------ inlay の部品

def line_mask(env, mats_, cx=lambda y: 0.0 * y, y0=-99, y1=99, half=0.5, dash=None, front_only=False, zmax=None):
    """刃の中心線 (幅 half) の光る画素。広い面 (|Nz| 大) だけ。dash = (周期, 長さ) で破線。"""
    e = env
    ok = np.isin(e["mats"], list(mats_)) & (np.abs(e["N"][..., 2]) > 0.55) & (e["Y"] >= y0) & (e["Y"] <= y1)
    if front_only:
        ok &= e["N"][..., 2] < 0
    ok &= np.abs(e["X"] - cx(e["Y"])) <= half
    if dash:
        p, l = dash
        ok &= (e["Y"] % p) < l
    return ok


def edge_mask(env, mats_, y0=-99, y1=99, dash=None):
    e = env
    ok = np.isin(e["mats"], list(mats_)) & e["convex"] & (e["Y"] >= y0) & (e["Y"] <= y1)
    if dash:
        p, l = dash
        ok &= (e["Y"] % p) < l
    return ok


def spot_mask(env, mats_, center, r):
    e = env
    d = np.linalg.norm(e["L"] - np.asarray(center, float), axis=-1)
    return np.isin(e["mats"], list(mats_)) & (d < r)
