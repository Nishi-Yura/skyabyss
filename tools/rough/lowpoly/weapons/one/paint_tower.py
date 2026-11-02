"""あいだの塔の武器の絵 (o_tower.py。docs/design/54 の 2.6)。塔の言葉: 天 = 白の大理石に金の筋と磨いた金、淵 = 黒い石に紫に光る割れ目、
柄は夜の紫。光は朝の光 (金白) と淵の光 (紫)。神器の塗り (paint_divine.py) の作りをそのまま借り、dark を黒い石と割れ目に、void を紫の光にした。
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
    # 雲の白の石 (main): 暖かい白
    STONE=A_([(96, 92, 88), (132, 128, 122), (168, 164, 156), (198, 194, 186), (222, 218, 210), (240, 236, 228), (252, 250, 244)]),
    # 金 (edge・spine・acc・guard)
    SILVER=A_([(72, 50, 22), (110, 80, 36), (150, 112, 52), (192, 148, 72), (226, 186, 104), (246, 218, 146), (255, 242, 196)]),
    # 夜の紫 (柄)
    INDIGO=A_([(18, 12, 30), (30, 22, 50), (46, 34, 74), (66, 50, 100), (90, 70, 128), (118, 96, 156)]),
    WRAP=A_([(40, 30, 62), (62, 48, 92), (90, 72, 124)]),
    # 淵の黒い石 (dark)
    ABYSS=A_([(16, 14, 22), (26, 24, 34), (38, 36, 48), (54, 52, 66), (72, 70, 86)]),
    CORD=A_([(150, 120, 70), (190, 156, 96), (224, 192, 130), (248, 224, 170)]),
    CLOTH=A_([(170, 168, 176), (204, 202, 210), (230, 228, 236), (248, 248, 252)]),
    DARK=A_([(10, 10, 20), (22, 20, 36), (40, 36, 56)]),
    T_rare=A_([(110, 92, 52), (156, 134, 78), (200, 178, 112), (232, 214, 156), (250, 240, 200)]),
    T_legend=A_([(120, 90, 40), (176, 136, 64), (220, 182, 96), (246, 220, 150), (255, 246, 210)]),
    # 光
    G_dawn=A_([(220, 160, 90), (246, 200, 130), (255, 232, 180), (255, 248, 230)]),
    G_sky=A_([(110, 160, 220), (160, 200, 245), (210, 232, 255), (244, 250, 255)]),
    G_void=A_([(110, 60, 200), (150, 100, 235), (195, 160, 255), (232, 214, 255)]),
)
GLOW_RGB = dict(dawn=(255, 232, 180), void=(190, 150, 255))


def paint(faces, size, P, N, M, L, glow_key="dawn", tier="rare", plus=0, inlay=None, edge_mats=()):
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

    # 雲の白の石 (main): 大理石の淡い金の筋と、ゆるい濃淡
    vein = np.abs(np.sin(X * 0.9 + Y * 0.35 + 5.0 * vnoise(L * 0.25 + 9, 4))) < 0.035
    idx = shaded(ST, 0.66, 0.30, 0.10) - (pit < 0.03)
    put(m("main"), pick(ST, idx))
    put(m("main") & vein & INS, pick(PAL["SILVER"], shaded(PAL["SILVER"], 0.62, 0.3, 0.06)))
    # 銀 (edge・spine・acc・guard): 磨き。縁に明るい線、面に細いへアライン
    hair = (np.sin(Y * 3.1 + X * 0.4 + 6 * n_hi) > 0.86)
    for nm, b in (("edge", 0.70), ("spine", 0.58), ("acc", 0.62), ("guard", 0.50)):
        put(m(nm), pick(SV, shaded(SV, b, 0.36, 0.06) + hair - (pit < 0.03)))
    put(m("grip"), pick(IN, shaded(IN, 0.40, 0.26, 0.10)))
    diamond = ((np.floor(Y * 0.9 + X * 0.6) + np.floor(Y * 0.9 - X * 0.6 + Z * 0.6)) % 2) == 0
    put(m("wrap"), pick(PAL["WRAP"], shaded(PAL["WRAP"], 0.55, 0.3, 0.08) - diamond))
    # 淵の黒い石: 濃淡と、紫に光る細い割れ目
    put(m("dark"), pick(PAL["ABYSS"], shaded(PAL["ABYSS"], 0.42, 0.34, 0.16) - (pit < 0.04)))
    crack = m("dark") & INS & (np.abs(vnoise(L * 0.42 + 21, 3) - 0.5) < 0.035)
    put(crack, ramp(PAL["G_void"], 0.45 + 0.3 * n_mid))
    glow |= crack
    vm = m("void")
    put(vm, ramp(PAL["G_void"], np.minimum(0.25 + 0.5 * (0.5 + 0.5 * lit) + 0.08 * bay, 0.9)))
    glow |= vm
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
