"""島の祠の始めの武器の絵。素朴な品: 流木 (grip)、素の鉄 (main edge spine guard)、青銅の鈴 (acc)、真鍮の縁 (trim)、
祠の布 (wrap。褪せた藍)、麻縄 (cord)、宿り木の葉 (leaf) と実 (gem。職ごとの光)、紙垂 (paper)、鯨の骨 (bone)。
強化 (+5 / +10) は縁が一段ずつ明るく、+10 で刃の縁に職の光。"""
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
    WOOD=A_([(40, 30, 24), (66, 50, 38), (94, 74, 56), (124, 102, 80), (156, 134, 108), (186, 166, 140)]),
    IRON=A_([(30, 32, 36), (52, 56, 62), (78, 84, 92), (108, 114, 122), (144, 150, 158), (184, 188, 194)]),
    STEEL=A_([(60, 64, 72), (96, 102, 112), (136, 142, 152), (176, 182, 190), (214, 218, 224)]),
    BRONZE=A_([(54, 36, 20), (88, 60, 32), (124, 90, 48), (160, 122, 66), (196, 160, 96), (226, 198, 140)]),
    BRASS=A_([(70, 54, 22), (112, 88, 36), (154, 126, 54), (194, 164, 78), (228, 204, 120)]),
    CLOTH=A_([(26, 34, 52), (40, 52, 78), (58, 74, 104), (80, 98, 130), (108, 126, 156)]),
    CORD=A_([(62, 50, 34), (96, 80, 56), (132, 114, 82), (170, 152, 114), (204, 190, 152)]),
    LEAF=A_([(28, 46, 26), (44, 70, 36), (64, 98, 48), (90, 128, 62), (124, 160, 84)]),
    PAPER=A_([(150, 146, 136), (190, 186, 174), (222, 218, 206), (244, 242, 232)]),
    BONEP=A_([(88, 80, 66), (130, 120, 98), (172, 160, 132), (208, 198, 170), (236, 228, 204)]),
    DARK=A_([(8, 8, 10), (18, 18, 22), (30, 30, 36)]),
    G_star=A_([(110, 140, 220), (160, 190, 250), (210, 228, 255), (244, 248, 255)]),
    G_tide=A_([(30, 130, 130), (60, 190, 180), (130, 230, 214), (210, 252, 240)]),
    G_wind=A_([(90, 150, 90), (140, 200, 130), (196, 236, 180), (236, 252, 226)]),
)
GLOW_RGB = dict(star=(190, 215, 255), tide=(110, 230, 210), wind=(180, 240, 170))


def paint(faces, size, P, N, M, L, glow_key="star", tier="common", plus=0, inlay=None, edge_mats=()):
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
    gp = PAL["G_" + glow_key]

    # 流木: 縦の木目と節
    grain = np.sin(Y * 0.4 + 6.0 * n_lo + X * 2.2) > 0.55
    knot = n_hi > 0.86
    put(m("grip"), pick(PAL["WOOD"], shaded(PAL["WOOD"], 0.5, 0.3, 0.14) - grain - knot))
    # 鉄: 刃 (main) は明るい鋼、縁 (edge) はもっと明るい、金具 (spine / guard) は鉄
    put(m("main"), pick(PAL["STEEL"], shaded(PAL["STEEL"], 0.52, 0.34, 0.08) - (pit < 0.04)))
    put(m("edge"), pick(PAL["STEEL"], shaded(PAL["STEEL"], 0.72, 0.26, 0.04)))
    hammer = hash1(np.floor(X * 0.8) * 7 + np.floor(Y * 0.8) * 3 + fi, 4) * 0.14 - 0.07
    for nm, b in (("spine", 0.5), ("guard", 0.44)):
        put(m(nm), pick(PAL["IRON"], shaded(PAL["IRON"], b, 0.32, 0.08, extra=hammer) - (pit < 0.04)))
    # 青銅の鈴 (緑青の点)
    verd = (n_mid > 0.62) & (n_hi > 0.5)
    bz = shaded(PAL["BRONZE"], 0.56, 0.34, 0.08)
    put(m("acc"), pick(PAL["BRONZE"], bz))
    put(m("acc") & verd, pick(PAL["G_tide"], np.zeros_like(bz)))
    # 布・縄・葉・紙・骨・暗
    stripe = (np.floor(Y * 0.85 + X * 0.3) % 2) == 0
    put(m("wrap"), pick(PAL["CLOTH"], shaded(PAL["CLOTH"], 0.5, 0.3, 0.1) - stripe))
    put(m("cord"), pick(PAL["CORD"], shaded(PAL["CORD"], 0.55, 0.3, 0.06) - ((np.floor(Y * 1.6 + X * 0.8) % 2) == 0)))
    vein = np.abs(LV - CH / 2) < 0.6
    put(m("leaf"), pick(PAL["LEAF"], shaded(PAL["LEAF"], 0.55, 0.3, 0.08) + vein))
    put(m("paper"), pick(PAL["PAPER"], shaded(PAL["PAPER"], 0.6, 0.3, 0.04)))
    crack = (n_hi > 0.84)
    put(m("bone"), pick(PAL["BONEP"], shaded(PAL["BONEP"], 0.6, 0.3, 0.1) - crack))
    put(m("dark"), pick(PAL["DARK"], np.floor(1.2 + 1.2 * (0.5 + 0.5 * lit) + 0.8 * bay).astype(int)))

    # 光る実・石
    gm = m("gem")
    put(gm, ramp(gp, np.minimum(0.15 + 0.55 * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.08 * bay, 0.85)))
    glow |= gm
    # 真鍮の縁。強化で一段ずつ明るく
    tidx = shaded(PAL["BRASS"], 0.52, 0.30, 0.06) + (1 if plus >= 5 else 0) + (1 if plus >= 10 else 0)
    put(m("trim"), pick(PAL["BRASS"], tidx))
    put(m("trim") & convex, pick(PAL["BRASS"], tidx + 1))
    if plus >= 10:
        em = np.isin(mats, ["main", "edge"]) & convex & (lit > -0.1)
        put(em, ramp(gp, 0.45 + 0.3 * hash1(xx + yy, 9)))
        glow |= em

    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl_ = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl_ & owned
