"""旅装 (鯨道・潮渡り・峠越え・灰歩き) のドット絵。巨神の防具 (apaint) と違い、組ごとに「材質 → 色と質感」の表を持つ。
質感: leather (革の粒と擦れ) / wool (織り目) / fur (毛の流れ) / rope (縞) / metal (打ち跡と縁の光) / shell (筋) / feather (羽枝) / straw (編み目) / soot (煤のまだら)。
材質名は形の側 (travel_one) と同じ: main sec edge acc cloth cord fur metal glass gem trim dark。"""
from __future__ import annotations

import numpy as np

from akit import *  # noqa: F401,F403
from akit import edge_fields, fill_outside, hash1, vnoise, BAYER
from wkit import pick, ramp, LIGHT

A_ = np.array
C = dict(
    MOSS_WOOL=A_([(34, 46, 30), (48, 64, 40), (64, 84, 50), (84, 106, 62), (106, 128, 78)]),
    LEATHER=A_([(52, 34, 24), (74, 50, 34), (98, 68, 44), (124, 88, 58), (150, 110, 74)]),
    LEATHER_D=A_([(30, 22, 18), (44, 32, 26), (60, 44, 34), (78, 58, 44)]),
    ROCK=A_([(48, 52, 60), (66, 72, 82), (88, 94, 104), (110, 116, 124), (134, 140, 146)]),
    BONE=A_([(120, 108, 88), (156, 142, 116), (190, 176, 146), (216, 204, 176), (238, 230, 206)]),
    ROPE=A_([(104, 86, 58), (134, 112, 78), (162, 140, 100), (188, 168, 126)]),
    FUR_BR=A_([(70, 52, 38), (96, 74, 54), (124, 98, 72), (152, 124, 94), (180, 154, 122)]),
    STRAW=A_([(126, 100, 54), (160, 130, 72), (192, 162, 96), (220, 194, 128), (240, 220, 160)]),
    NET=A_([(70, 78, 80), (96, 104, 104), (126, 134, 130)]),
    WADER=A_([(22, 46, 50), (32, 64, 68), (44, 84, 86), (60, 106, 104), (80, 128, 122)]),
    SALT=A_([(196, 200, 198), (222, 226, 224), (244, 246, 244)]),
    PEARL=A_([(186, 178, 186), (216, 208, 214), (238, 232, 236), (252, 248, 250)]),
    CORK=A_([(170, 86, 30), (206, 112, 42), (232, 142, 60), (248, 176, 96)]),
    SHELL=A_([(46, 100, 92), (60, 124, 108), (78, 148, 128), (100, 170, 146)]),
    PARKA=A_([(36, 46, 70), (48, 62, 92), (64, 80, 116), (84, 102, 140), (110, 128, 164)]),
    FEATHER=A_([(150, 162, 188), (190, 200, 220), (226, 232, 246), (248, 250, 255)]),
    COPPER=A_([(84, 46, 28), (128, 70, 38), (172, 100, 52), (212, 136, 76), (240, 176, 112)]),
    FUR_W=A_([(150, 146, 140), (184, 180, 172), (212, 208, 200), (236, 232, 226)]),
    GLASS=A_([(60, 120, 150), (100, 170, 200), (160, 214, 236), (220, 244, 252)]),
    HEMP=A_([(70, 64, 54), (94, 86, 72), (118, 108, 90), (142, 132, 110), (168, 158, 134)]),
    IRON=A_([(36, 34, 38), (58, 54, 58), (84, 78, 80), (112, 104, 102), (142, 132, 126)]),
    SOOT=A_([(18, 16, 16), (30, 26, 24), (46, 40, 36), (64, 56, 50)]),
    ASH=A_([(98, 96, 98), (128, 126, 126), (156, 154, 152)]),
    DARK=A_([(10, 10, 16), (20, 20, 28), (34, 34, 46)]),
    TRIM_U=A_([(54, 92, 60), (78, 124, 80), (110, 160, 104), (156, 200, 140)]),
    G_CYAN=A_([(70, 160, 200), (110, 210, 240), (170, 238, 252), (230, 252, 255)]),
    G_PINK=A_([(240, 130, 180), (255, 170, 205), (255, 210, 228), (255, 240, 248)]),
    G_VEIN=A_([(120, 176, 240), (170, 214, 255), (220, 240, 255), (255, 255, 255)]),
    G_LAVA=A_([(150, 40, 10), (210, 70, 16), (240, 112, 26), (255, 164, 48), (255, 218, 110)]),
)
# 組 → 材質 → (色, 明るさの基準, 質感)
SETS = {
    "whale_road": dict(main=("MOSS_WOOL", 0.52, "wool"), sec=("LEATHER", 0.50, "leather"), edge=("BONE", 0.72, "shell"), acc=("ROCK", 0.55, "metal"),
                       cloth=("LEATHER_D", 0.42, "leather"), cord=("ROPE", 0.55, "rope"), fur=("FUR_BR", 0.55, "fur"), metal=("ROCK", 0.60, "metal"),
                       glow="G_CYAN"),
    "tide_wader": dict(main=("STRAW", 0.58, "straw"), sec=("WADER", 0.50, "leather"), edge=("SALT", 0.65, "shell"), acc=("SHELL", 0.55, "shell"),
                       cloth=("NET", 0.50, "rope"), cord=("ROPE", 0.55, "rope"), fur=("CORK", 0.55, "leather"), metal=("PEARL", 0.65, "metal"),
                       glow="G_PINK"),
    "peak_crossing": dict(main=("PARKA", 0.50, "wool"), sec=("FEATHER", 0.66, "feather"), edge=("FUR_W", 0.62, "fur"), acc=("COPPER", 0.55, "metal"),
                          cloth=("LEATHER_D", 0.45, "leather"), cord=("ROPE", 0.58, "rope"), fur=("FUR_W", 0.60, "fur"), metal=("COPPER", 0.62, "metal"),
                          glass=("GLASS", 0.6, "glass"), glow="G_VEIN"),
    "ash_walking": dict(main=("HEMP", 0.50, "wool"), sec=("IRON", 0.52, "metal"), edge=("IRON", 0.70, "metal"), acc=("LEATHER", 0.48, "leather"),
                        cloth=("SOOT", 0.50, "soot"), cord=("ROPE", 0.48, "rope"), fur=("ASH", 0.55, "soot"), metal=("IRON", 0.58, "metal"),
                        glass=("SOOT", 0.4, "glass"), glow="G_LAVA"),
}


def paint(faces, size, P, N, M, L, key):
    spec = SETS[key]
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
    pit = hash1(xx * 7 + yy * 13 + fi * 3, 5)
    col = np.zeros((H, W, 3))
    glow = np.zeros((H, W), bool)

    def put(mask, c):
        nonlocal col
        col = np.where(mask[..., None], c, col)

    def shaded(pal, base, lw=0.30, nw=0.14, extra=0.0):
        v = base + lw * lit + nw * (n_lo - 0.5) + 0.10 * (n_mid - 0.5) + 0.07 * bay + extra
        idx = np.floor(v * len(pal)).astype(int)
        return idx + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave

    for mat, val in spec.items():
        if mat == "glow":
            continue
        pal_name, base, tex = val
        pal = C[pal_name]
        mm = mats == mat
        if not mm.any():
            continue
        idx = shaded(pal, base)
        if tex == "wool":
            idx = idx - ((LU + LV) % 2 == 0) * (n_hi > 0.5)
        elif tex == "leather":
            idx = idx - (pit < 0.06) + ((n_hi > 0.85) & (lit > 0))
        elif tex == "fur":
            idx = idx + (np.sin(Y * 3.1 + X * 0.7 + 4 * n_mid) > 0.6) - (np.sin(Y * 2.3 - Z * 1.1) > 0.75)
        elif tex == "rope":
            idx = idx - (((LU + LV) % 3) == 0)
        elif tex == "metal":
            idx = idx + (hash1(np.floor(X * 1.5) * 3 + np.floor(Y * 1.5) * 7 + fi, 4) > 0.8) - (pit < 0.04)
        elif tex == "shell":
            idx = idx - ((np.floor(Y * 1.2 + 0.4 * X) % 2) == 0) * (n_mid > 0.4)
        elif tex == "feather":
            idx = idx - (np.abs(((X * 0.9 + Y * 0.8) % 1.0) - 0.5) < 0.12)
        elif tex == "straw":
            idx = idx - (((LU // 2) + (LV // 2)) % 2 == 0)
        elif tex == "soot":
            idx = idx - (n_mid > 0.62) + (n_hi > 0.85)
        elif tex == "glass":
            idx = idx + ((LU + LV) % 5 == 0)
        put(mm, pick(pal, idx))
    put(mats == "dark", pick(C["DARK"], np.floor(1.2 + 1.2 * (0.5 + 0.5 * lit) + 0.8 * bay).astype(int)))
    tp = C["TRIM_U"]
    tidx = shaded(tp, 0.55, 0.3, 0.06)
    put(mats == "trim", pick(tp, tidx))
    gp = C[spec["glow"]]
    gm = mats == "gem"
    put(gm, ramp(gp, 0.35 + 0.45 * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.08 * bay))
    glow |= gm
    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl_ = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl_ & owned
