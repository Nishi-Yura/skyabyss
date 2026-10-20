"""巨神シリーズの防具のドット絵。材質名は 4 巨神で共通 (cloth sash plate bevel trim motif motif2 gem lume under dark)。
布は縦の糸目とひだの明暗、板は面取りの縁の光、帯・縁は巨神ごとの色。階級は trim の色と光る筋だけを変える。"""
from __future__ import annotations

import numpy as np

from akit import *  # noqa: F401,F403
from akit import hash1, vnoise, BAYER, edge_fields, fill_outside
from paint import PAL, GLOW
from wkit import pick, ramp, LIGHT

A_ = np.array
CLOTH = dict(
    whale=A_([(18, 26, 42), (28, 40, 60), (40, 56, 82), (54, 74, 104), (70, 92, 124), (88, 110, 142)]),
    tide=A_([(14, 40, 48), (22, 58, 66), (34, 78, 86), (48, 100, 104), (66, 124, 124), (88, 146, 142)]),
    thunder=A_([(20, 24, 42), (30, 36, 60), (42, 50, 80), (56, 66, 102), (74, 86, 126), (96, 108, 148)]),
    ember=A_([(22, 17, 16), (36, 29, 26), (52, 43, 38), (70, 59, 52), (90, 77, 68), (112, 97, 86)]),
)
CAPEP = dict(
    whale=A_([(28, 40, 52), (42, 58, 74), (58, 78, 96), (76, 98, 118), (96, 118, 138), (118, 138, 156)]),
    tide=A_([(22, 56, 70), (34, 76, 92), (48, 98, 114), (66, 120, 134), (88, 142, 152), (112, 164, 170)]),
    thunder=A_([(30, 28, 56), (44, 40, 80), (60, 54, 106), (80, 72, 132), (102, 94, 156), (126, 118, 178)]),
    ember=A_([(40, 18, 16), (62, 28, 24), (86, 40, 32), (112, 54, 40), (140, 70, 48), (168, 88, 56)]),
)
SASHP = dict(
    whale=A_([(120, 108, 84), (158, 146, 114), (194, 182, 146), (222, 212, 178), (240, 232, 204)]),
    tide=PAL["SASH"],
    thunder=A_([(88, 98, 130), (118, 130, 166), (150, 162, 198), (186, 196, 226), (220, 228, 246)]),
    ember=A_([(56, 14, 14), (86, 22, 20), (120, 32, 26), (156, 46, 32), (190, 70, 40)]),
)


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

    def shaded(pal, base, lw=0.30, nw=0.10, extra=0.0, ed=True):
        nw = nw * 0.55
        v = base + lw * lit + nw * (n_lo - 0.5) + 0.04 * (n_mid - 0.5) + 0.05 * bay + extra
        idx = np.floor(v * len(pal)).astype(int)
        if ed:
            idx = idx + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave
        return idx

    m = lambda name: mats == name
    gp = PAL[GLOW[titan]]
    thread = (LU % 3 == 0) & (n_mid > 0.55)
    fold = np.sin(X * 1.7 + Z * 1.3 + 2.2 * n_lo) > 0.55

    # ---- 布・帯
    cp, sp = CLOTH[titan], SASHP[titan]
    put(m("cloth"), pick(cp, shaded(cp, 0.50, 0.36, 0.05) - thread - fold * (n_mid > 0.45)))
    cap_ = CAPEP[titan]
    put(m("cape"), pick(cap_, shaded(cap_, 0.46 + 0.1 * np.clip((Y - 8) / 16.0, -0.3, 0.3) * -1, 0.40, 0.04) - thread - fold * (n_mid > 0.45)))
    put(m("under"), pick(cp, shaded(cp, 0.24, 0.2, 0.04, ed=False)) * 0.6)
    fray = (np.floor(Y * 0.8 + X * 0.25) % 3 == 0)
    put(m("sash"), pick(sp, shaded(sp, 0.55, 0.32, 0.06) - (fray & (n_mid > 0.5)) - (pit < 0.03)))

    # ---- 板・面取りの縁・飾り
    if titan == "whale":
        B, S, MO = PAL["BONE"], PAL["STONE"], PAL["MOSS"]
        put(m("plate"), pick(S, shaded(S, 0.52, 0.30, 0.12) - (pit < 0.02)))
        mo = m("plate") & ((n_mid + 0.5 * n_lo + 0.55 * np.clip(N[..., 1], 0, 1)) > 1.36)
        put(mo, pick(MO, np.floor(1.5 + 1.5 * lit + bay).astype(int)))
        put(m("bevel"), pick(B, shaded(B, 0.82, 0.24, 0.04)))
        ring_ = np.sin(Y * 1.15 + 3.0 * n_lo) > 0.72
        put(m("motif"), pick(B, shaded(B, 0.70, 0.34, 0.06) - ring_ * (n_mid > 0.35) - (pit < 0.03)))
        put(m("motif2"), pick(MO, shaded(MO, 0.55, 0.3, 0.12)))
    elif titan == "tide":
        PE, SH, CO, OC = PAL["PEARL"], PAL["SHELL"], PAL["CORAL"], PAL["CORALO"]
        irid = n_mid * 0.6 + 0.4 * np.sin(Y * 0.5 + X * 0.3)
        tintc = np.where((irid > 0.55)[..., None], A_([-18, 8, 4]), np.where((irid < 0.38)[..., None], A_([6, -4, 10]), A_([0, 0, 0])))
        pe = shaded(PE, 0.52, 0.28, 0.06)
        put(m("plate"), pick(PE, pe) + tintc * (pe[..., None] >= 1))
        put(m("bevel"), pick(PE, shaded(PE, 0.88, 0.2, 0.04)) + tintc * 0.4)
        ci = shaded(CO, 0.56, 0.3, 0.12)
        coral = np.where((n_mid > 0.6)[..., None], pick(OC, shaded(OC, 0.56, 0.3, 0.12)), pick(CO, ci))
        pores = (hash1(xx * 5 + yy * 11 + fi, 3) < 0.07) & (D > 0.9)
        put(m("motif"), np.where(pores[..., None], pick(CO, ci - 2) * 0.7, coral))
        put(m("motif2"), pick(OC, shaded(OC, 0.56, 0.3, 0.12)))
        bar = m("plate") & (hash1(xx * 13 + yy * 7 + fi * 5, 9) < 0.05) & (D > 0.8)
        put(bar, pick(SH, np.floor(2.0 + 1.5 * lit + bay).astype(int)))
    elif titan == "thunder":
        CP, ST, PA, PL = PAL["COPPER"], PAL["STEEL"], PAL["PATINA"], PAL["PLUME"]
        brush = hash1(np.floor(X * 2.0) * 3 + fi, 6)
        put(m("plate"), pick(CP, shaded(CP, 0.54, 0.34, 0.04) + (brush > 0.9) - (brush < 0.06)))
        verd = m("plate") & (concave | ((n_mid > 0.74) & (np.abs(N[..., 1]) < 0.7)))
        put(verd, pick(PA, np.floor(1.0 + 1.6 * lit + bay).astype(int)))
        put(m("bevel"), pick(CP, shaded(CP, 0.80, 0.24, 0.04)))
        barb = np.abs(((X * 0.9 + Y * 0.8) % 1.0) - 0.5) < 0.12
        put(m("motif"), pick(PL, shaded(PL, 0.80, 0.26, 0.04) - barb + (np.abs(X) < 0.35)))
        put(m("motif2"), pick(CP, shaded(CP, 0.6, 0.3, 0.06)))
    else:
        RO, IR, RW, AS, LA = PAL["ROCK"], PAL["IRON"], PAL["ROCKW"], PAL["ASH"], PAL["LAVA"]
        facet = hash1(np.floor(Y * 0.5) * 7 + np.floor(X * 0.5) * 3 + fi, 8) * 0.16 - 0.08
        put(m("plate"), pick(RO, shaded(RO, 0.82, 0.34, 0.08, extra=facet) - (pit < 0.02)))
        put(m("bevel"), pick(IR, shaded(IR, 0.72, 0.28, 0.05) - (pit < 0.03)))
        put(m("motif"), pick(RO, shaded(RO, 0.92, 0.36, 0.08, extra=facet)))
        put(m("motif2"), pick(IR, shaded(IR, 0.6, 0.3, 0.08)))
        ashm = np.isin(mats, ["plate", "motif"]) & (N[..., 1] > 0.4) & ((n_mid + 0.5 * n_lo + 0.4 * N[..., 1]) > 1.22)
        put(ashm, pick(AS, np.floor(1.2 + 1.6 * lit + 1.2 * bay).astype(int)))
        lvl = {"common": 0.005, "rare": 0.008, "legend": 0.013}[rarity]
        rn = np.abs(vnoise(L * 0.20 + 7, 5) - 0.5)
        crack = np.isin(mats, ["plate", "motif"]) & (rn < lvl) & INS & (D > 0.4)
        put(crack, ramp(gp, 0.3 + 0.55 * hash1(xx + yy * 3, 1)))
        glow |= crack
    put(m("dark"), pick(PAL["DARK"], np.floor(1.2 + 1.2 * (0.5 + 0.5 * lit) + 0.8 * bay).astype(int)))

    # ---- 光る面
    gl = np.isin(mats, ["gem", "lume"])
    put(m("lume"), ramp(gp, 0.40 + 0.30 * (0.5 + 0.5 * lit) + 0.06 * bay))
    put(m("gem"), ramp(gp, 0.35 + 0.45 * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.08 * bay))
    glow |= gl

    # ---- 階級の縁の金属 (巨神によらず共通)
    tp = {"common": PAL["TRIM_N"], "rare": PAL["TRIM_R"], "legend": PAL["TRIM_L"]}[rarity]
    step = 0 if plus < 5 else 1 if plus < 10 else 2
    tidx = shaded(tp, 0.56, 0.30, 0.04) + (step if rarity != "common" else 0)
    chase = ((LU + LV) % 4 == 0) & (np.minimum(CW, CH) >= 2) & (D > 0.9)    # 打ち出しの点
    put(m("trim"), pick(tp, tidx - chase))
    put(m("trim") & convex, pick(tp, tidx + 1))
    if rarity in ("rare", "legend") or plus >= 5:
        along_u = CW >= CH
        mid_v = (LV == CH // 2) & (CH >= 3)
        mid_u = (LU == CW // 2) & (CW >= 3)
        pos = np.where(along_u, LU, LV)
        dash = (pos % 8) < 5
        line = np.where(along_u, mid_v, mid_u) & (np.minimum(CW, CH) >= 2) & INS & dash
        tl = m("trim") & line & (np.maximum(CW, CH) >= 6)
        put(tl, ramp(gp, 0.5 + 0.4 * hash1(LU + LV * 3, 2)))
        glow |= tl
    if rarity == "legend" or plus >= 5:   # 面取りの縁の光る筋 (伝説)
        em = m("bevel") & convex & (lit > 0.2) & (hash1(xx + yy * 5, 3) < 0.28)
        put(em, ramp(gp, 0.5 + 0.35 * hash1(xx + yy, 9)))
        glow |= em
    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl_ = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl_ & owned
