"""六章「月」の武器の絵 (o_moon.py)。月の言葉: 月の石 (青みの灰白、小さなクレーターの点)、銀 (磨いた冷たい銀)、
藍の黒 (柄・影)、星の光 (青白)。縁 (trim) は階級の色: レア (エピックの形) = 青い銀、伝説 (ヒロイックの形) = 淡い金。
材質名は paint.py と同じ (main edge spine guard grip wrap acc gem trim dark cord chain cloth soot void)。"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from wkit import *  # noqa: F401,F403,E402
from wkit import pick, ramp, LIGHT, hash1, vnoise, BAYER, edge_fields, fill_outside  # noqa: E402

A_ = np.array
PAL = dict(
    # 月の石: 青みの灰白
    STONE=A_([(58, 62, 78), (84, 90, 108), (112, 118, 136), (142, 148, 166), (172, 178, 194), (204, 208, 220), (228, 230, 238)]),
    # 銀
    SILVER=A_([(52, 58, 74), (82, 90, 108), (118, 126, 146), (156, 164, 182), (192, 198, 214), (222, 226, 236), (246, 248, 252)]),
    # 藍の黒 (柄・影の板)
    INDIGO=A_([(10, 12, 26), (18, 22, 42), (28, 34, 62), (40, 48, 84), (56, 66, 110), (76, 88, 138)]),
    WRAP=A_([(16, 20, 40), (28, 34, 62), (44, 52, 90)]),
    CORD=A_([(70, 76, 104), (100, 108, 138), (134, 142, 172), (170, 178, 204)]),
    CLOTH=A_([(104, 110, 130), (140, 146, 166), (176, 182, 200), (210, 214, 228)]),
    DARK=A_([(4, 5, 12), (10, 12, 24), (20, 24, 42)]),
    # 縁: レア (青い銀) / 伝説 (淡い金)
    T_rare=A_([(60, 80, 118), (92, 120, 166), (132, 162, 206), (176, 202, 236), (220, 234, 252)]),
    T_legend=A_([(110, 92, 52), (156, 134, 78), (200, 178, 112), (232, 214, 156), (250, 240, 200)]),
    # 光
    G_moon=A_([(120, 150, 200), (176, 204, 244), (220, 234, 255), (246, 250, 255)]),
    G_star=A_([(90, 140, 210), (150, 196, 250), (200, 226, 255), (240, 248, 255)]),
    G_shade=A_([(80, 70, 170), (124, 112, 226), (176, 166, 255), (226, 220, 255)]),
)
GLOW_RGB = dict(moon=(220, 234, 255), star=(170, 210, 255), shade=(160, 150, 255))


def paint(faces, size, P, N, M, L, glow_key="moon", tier="rare", plus=0, inlay=None, edge_mats=()):
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
    ST, SV, IN = PAL["STONE"], PAL["SILVER"], PAL["INDIGO"]
    TM = PAL["T_" + tier]
    gp = PAL["G_" + glow_key]
    env = dict(L=L, N=N, mats=mats, convex=convex, concave=concave, D=D, INS=INS, K=K, lit=lit, edge=edge, X=X, Y=Y, Z=Z,
               LU=LU, LV=LV, CW=CW, CH=CH, fi=fi)

    # 月の石 (main): クレーターの点 (暗い穴に明るい縁) と、ゆるい濃淡
    cell = vnoise(L * 0.55 + 21, 3)
    crater = (cell > 0.78) & INS & (D > 0.9)
    rim = (cell > 0.70) & (cell <= 0.78) & INS
    idx = shaded(ST, 0.60, 0.32, 0.12) - 2 * crater + rim - (pit < 0.04)
    put(m("main"), pick(ST, idx))
    # 銀 (edge・spine・acc・guard): 磨き。縁に明るい線、面に細いへアライン
    hair = (np.sin(Y * 3.1 + X * 0.4 + 6 * n_hi) > 0.86)
    for nm, b in (("edge", 0.70), ("spine", 0.58), ("acc", 0.62), ("guard", 0.50)):
        put(m(nm), pick(SV, shaded(SV, b, 0.36, 0.06) + hair - (pit < 0.03)))
    put(m("grip"), pick(IN, shaded(IN, 0.40, 0.26, 0.10)))
    diamond = ((np.floor(Y * 0.9 + X * 0.6) + np.floor(Y * 0.9 - X * 0.6 + Z * 0.6)) % 2) == 0
    put(m("wrap"), pick(PAL["WRAP"], shaded(PAL["WRAP"], 0.55, 0.3, 0.08) - diamond))
    put(m("dark") | m("void"), pick(PAL["DARK"], np.floor(1.2 + 1.2 * (0.5 + 0.5 * lit) + 0.8 * bay).astype(int)))
    put(m("cord") | m("chain"), pick(PAL["CORD"], shaded(PAL["CORD"], 0.6, 0.3, 0.06) - ((np.floor(Y * 1.6) % 2) == 0)))
    put(m("cloth") | m("soot"), pick(PAL["CLOTH"], shaded(PAL["CLOTH"], 0.55, 0.3, 0.1)))

    # 光る核
    gm = m("gem")
    put(gm, ramp(gp, np.minimum(0.16 + 0.55 * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.08 * bay, 0.86)))
    glow |= gm
    tidx = shaded(TM, 0.55, 0.30, 0.06) + (1 if plus >= 5 else 0) + (1 if plus >= 10 else 0)
    put(m("trim"), pick(TM, tidx))

    kinds = (["legend"] if tier == "legend" else []) + (["p5"] if plus >= 5 else []) + (["p10"] if plus >= 10 else [])
    if inlay is not None:
        for k in kinds:
            gmask = inlay(env, k) & INS
            put(gmask, ramp(gp, 0.40 + 0.4 * hash1(LU + LV * 3, 2) + (0.15 if k == "p10" else 0.0)))
            glow |= gmask
    if plus >= 10 and edge_mats:
        em = np.isin(mats, list(edge_mats)) & convex & (lit > -0.1)
        put(em, ramp(gp, 0.45 + 0.3 * hash1(xx + yy, 9)))
        glow |= em
    tr = m("trim") & convex
    put(tr, pick(TM, tidx + 1))

    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl_ = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl_ & owned
