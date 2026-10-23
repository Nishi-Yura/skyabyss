"""五章「深淵」の装備の絵 (武器と装飾品)。深淵の専用伝説 (../abyss/paint_abyss.py) と同じ言葉: 土台は紫黒の虚 (VOID)、
そこへ錨鉄 (冷たい青灰の鉄。打ち跡と鋲) を載せ、光る核は品ごとの色 (灯りの琥珀・淵の紫・鼓動の赤)。
縁 (trim) は階級の色: レア = 青い鋼、伝説 = 紫銀。材質名は paint.py と同じ (main edge spine guard grip wrap acc gem trim dark cord)。"""
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
    VOID=A_([(10, 7, 20), (20, 14, 36), (32, 23, 56), (46, 33, 78), (64, 47, 104), (86, 65, 132)]),
    VEDGE=A_([(50, 38, 84), (76, 60, 118), (104, 86, 150), (136, 118, 182), (176, 160, 214)]),
    WRAP=A_([(16, 12, 26), (26, 20, 40), (38, 30, 58)]),
    # 錨鉄: 青灰の冷たい鉄
    AIRON=A_([(22, 24, 34), (38, 42, 56), (58, 64, 82), (84, 92, 112), (118, 128, 148), (160, 170, 188)]),
    # 縁: レア (青い鋼) / 伝説 (紫銀)
    T_rare=A_([(34, 48, 72), (52, 76, 110), (78, 110, 152), (112, 150, 196), (160, 196, 236)]),
    T_legend=A_([(40, 34, 58), (66, 58, 90), (96, 88, 124), (130, 122, 158), (170, 162, 196)]),
    # 光
    VGLOW=A_([(110, 70, 210), (150, 108, 244), (196, 160, 255), (234, 214, 255)]),
    G_lamp=A_([(200, 110, 30), (245, 160, 50), (255, 205, 110), (255, 238, 190)]),
    G_void=A_([(120, 60, 220), (160, 100, 250), (204, 156, 255), (238, 214, 255)]),
    G_pulse=A_([(150, 20, 40), (210, 40, 60), (250, 96, 110), (255, 180, 180)]),
    CORD=A_([(46, 44, 56), (70, 68, 84), (100, 98, 116), (140, 138, 156)]),
    DARK=A_([(6, 4, 12), (14, 10, 24), (26, 20, 40)]),
)
GLOW_RGB = dict(lamp=(255, 180, 80), void=(190, 140, 255), pulse=(255, 90, 100))


def paint(faces, size, P, N, M, L, glow_key="lamp", tier="rare", plus=0, inlay=None, edge_mats=()):
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
    VO, VE = PAL["VOID"], PAL["VEDGE"]
    AI = PAL["AIRON"]
    TM = PAL["T_" + tier]
    gp = PAL["G_" + glow_key]
    vg = PAL["VGLOW"] if glow_key != "lamp" else PAL["G_lamp"]
    env = dict(L=L, N=N, mats=mats, convex=convex, concave=concave, D=D, INS=INS, K=K, lit=lit, edge=edge, X=X, Y=Y, Z=Z,
               LU=LU, LV=LV, CW=CW, CH=CH, fi=fi)

    # 虚の黒紫 (刃): 斜めの流れの筋と点の欠け
    flow = np.sin((X * 1.3 + Y * 0.9) * 1.1 + 4.0 * n_lo) > 0.8
    put(m("main"), pick(VO, shaded(VO, 0.64, 0.34, 0.10) + (flow & (n_mid > 0.35)) - (pit < 0.05)))
    put(m("edge"), pick(VE, shaded(VE, 0.62, 0.30, 0.06) - (pit < 0.03)))
    put(m("grip"), pick(VO, shaded(VO, 0.32, 0.24, 0.1)))
    stripe = (np.floor(Y * 0.85 + X * 0.3) % 2) == 0
    put(m("wrap"), pick(PAL["WRAP"], shaded(PAL["WRAP"], 0.5, 0.3, 0.1) - stripe))
    put(m("dark"), pick(PAL["DARK"], np.floor(1.2 + 1.2 * (0.5 + 0.5 * lit) + 0.8 * bay).astype(int)))
    put(m("cord"), pick(PAL["CORD"], shaded(PAL["CORD"], 0.6, 0.3, 0.06) - ((np.floor(Y * 1.6) % 2) == 0)))

    # 錨鉄 (spine / acc / guard): 打ち跡のまだらと、縁の近くの鋲
    hammer = hash1(np.floor(X * 0.8) * 7 + np.floor(Y * 0.8) * 3 + fi, 4) * 0.16 - 0.08
    rivet = (D > 1.2) & (D < 2.2) & ((((LU // 3) + (LV // 3)) % 4) == 0) & INS
    for nm, b in (("spine", 0.56), ("acc", 0.62), ("guard", 0.46)):
        idx = shaded(AI, b, 0.32, 0.08, extra=hammer) - (n_hi > 0.82) - (pit < 0.04)
        put(m(nm), pick(AI, idx))
        put(m(nm) & rivet & (CW >= 8) & (CH >= 8), pick(AI, idx + 2))

    # 光る核
    gm = m("gem")
    put(gm, ramp(gp, np.minimum(0.12 + 0.55 * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.08 * bay, 0.80)))
    glow |= gm
    # 縁 (階級の色)。強化で一段ずつ明るく
    tidx = shaded(TM, 0.55, 0.30, 0.06) + (1 if plus >= 5 else 0) + (1 if plus >= 10 else 0)
    put(m("trim"), pick(TM, tidx))

    # 光の筋 (inlay(env, kind): legend (伝説だけいつも) / p5 / p10)
    kinds = (["legend"] if tier == "legend" else []) + (["p5"] if plus >= 5 else []) + (["p10"] if plus >= 10 else [])
    if inlay is not None:
        for k in kinds:
            gmask = inlay(env, k) & INS
            put(gmask, ramp(vg, 0.40 + 0.4 * hash1(LU + LV * 3, 2) + (0.15 if k == "p10" else 0.0)))
            glow |= gmask
    if plus >= 10 and edge_mats:
        em = np.isin(mats, list(edge_mats)) & convex & (lit > -0.1)
        put(em, ramp(vg, 0.45 + 0.3 * hash1(xx + yy, 9)))
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
