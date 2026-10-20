"""3 つの様式 (A 量塊 / B 祭具 / C 遺骸) 共通のドット絵描き。材質名は武器・防具で共通:
main edge spine guard grip wrap acc cloth trim dark gem vein (gem と vein は光る)。
様式ごとに、材質 -> (パレット, 基準の明るさ) と、描き込み (欠け・金の線・ひび・苔) の量が変わる。"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "weapons"))
from wkit import pick, ramp, LIGHT, hash1, vnoise, BAYER, edge_fields, fill_outside  # noqa: E402

A_ = np.array
P = dict(
    BONE=A_([(52, 44, 44), (96, 84, 70), (140, 124, 100), (182, 166, 132), (216, 202, 168), (240, 232, 206)]),
    BONE_D=A_([(40, 34, 32), (74, 64, 52), (110, 96, 76), (146, 130, 102), (178, 162, 130), (206, 192, 160)]),
    IVORY=A_([(86, 88, 100), (140, 142, 152), (190, 192, 198), (226, 226, 226), (246, 244, 236), (255, 254, 248)]),
    STONE=A_([(34, 40, 52), (50, 58, 72), (68, 77, 92), (88, 98, 114), (110, 120, 134), (134, 144, 156)]),
    STONE_D=A_([(20, 24, 32), (30, 35, 46), (42, 48, 62), (56, 64, 80), (72, 82, 98), (90, 100, 116)]),
    PALE=A_([(70, 84, 104), (100, 118, 140), (132, 152, 172), (164, 184, 202), (196, 214, 228), (224, 238, 246)]),
    MOSS=A_([(26, 42, 30), (42, 64, 36), (62, 90, 46), (88, 118, 60)]),
    CYAN=A_([(70, 160, 200), (110, 210, 240), (170, 238, 252), (230, 252, 255)]),
    GOLD=A_([(104, 70, 24), (160, 112, 34), (214, 162, 54), (246, 208, 100), (255, 238, 170)]),
    IRON=A_([(30, 32, 38), (50, 54, 62), (74, 80, 90), (100, 108, 118), (130, 138, 148)]),
    RUST=A_([(48, 30, 24), (82, 48, 34), (118, 70, 44), (150, 96, 58)]),
    NAVY=A_([(14, 22, 44), (22, 36, 70), (32, 52, 98), (46, 74, 128), (68, 100, 158)]),
    LEATHER=A_([(30, 22, 20), (50, 36, 30), (74, 54, 42), (100, 74, 56)]),
    TAN=A_([(70, 60, 46), (108, 94, 72), (146, 130, 100), (176, 160, 126)]),
    ASH=A_([(36, 38, 40), (60, 62, 64), (88, 90, 90), (118, 120, 118)]),
    PEARL=A_([(150, 150, 160), (190, 190, 200), (220, 220, 230), (240, 240, 248), (255, 255, 255)]),
    DARK=A_([(10, 10, 16), (20, 20, 28), (34, 34, 46)]),
)

# 材質 -> (パレット, 基準の明るさ 0..1, 光の揺れ)
MAT = dict(
    A=dict(main=("BONE", .70), edge=("BONE", .88), spine=("STONE", .50), guard=("STONE", .56), grip=("STONE_D", .45),
           wrap=("LEATHER", .55), acc=("MOSS", .55), cloth=("STONE_D", .42), trim=("IRON", .62), dark=("DARK", .5), cord=("TAN", .55)),
    B=dict(main=("IVORY", .72), edge=("IVORY", .92), spine=("PALE", .55), guard=("PALE", .62), grip=("NAVY", .45),
           wrap=("NAVY", .60), acc=("PEARL", .60), cloth=("NAVY", .52), trim=("GOLD", .62), dark=("DARK", .5), cord=("PEARL", .5)),
    C=dict(main=("BONE_D", .66), edge=("BONE_D", .82), spine=("STONE_D", .62), guard=("STONE_D", .60), grip=("LEATHER", .50),
           wrap=("TAN", .55), acc=("MOSS", .55), cloth=("ASH", .45), trim=("RUST", .55), dark=("DARK", .5), cord=("TAN", .45)),
)
# 描き込みの量
OPT = dict(
    A=dict(moss=1.46, chip=0.06, crack=0.0, gold=False, facet=0.16),
    B=dict(moss=9.0, chip=0.02, crack=0.0, gold=True, facet=0.04),
    C=dict(moss=1.18, chip=0.018, crack=0.012, gold=False, facet=0.10),
)


def paint(faces, size, Pp, N, M, L, style, rarity="rare", plus=0):
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
    op = OPT[style]

    def put(mask, c):
        nonlocal col
        col = np.where(mask[..., None], c, col)

    def shaded(pal, base, lw=0.30, nw=0.14, extra=0.0):
        v = base + lw * lit + nw * (n_lo - 0.5) + 0.10 * (n_mid - 0.5) + 0.07 * bay + extra
        idx = np.floor(v * len(pal)).astype(int)
        return idx + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave

    m = lambda name: mats == name
    facet = hash1(np.floor(Y * 0.5) * 7 + np.floor(X * 0.5) * 3 + fi, 8) * op["facet"] - op["facet"] / 2
    for name, (pn, base) in MAT[style].items():
        pal = P[pn]
        idx = shaded(pal, base, extra=facet) - (pit < op["chip"]) - (n_hi > 0.86) * (style == "A")
        put(m(name), pick(pal, idx))
    # 骨の年輪 (A, C)
    if style in ("A", "C"):
        ring_ = np.sin(Y * 1.15 + 3.0 * n_lo) > 0.74
        put(m("main") & ring_ & (n_mid > 0.35), pick(P["BONE" if style == "A" else "BONE_D"], shaded(P["BONE"], 0.55)))
    # 苔: 石・鍔・柄の上向きの面
    mo = np.isin(mats, ["spine", "guard", "grip", "cloth", "wrap"]) & ((n_mid + 0.5 * n_lo + 0.5 * np.clip(N[..., 1], 0, 1)) > op["moss"])
    put(mo, pick(P["MOSS"], np.floor(1.5 + 1.5 * lit + 1.2 * bay).astype(int)))
    if style == "C":   # 骨にも苔が這う
        mo2 = m("main") & ((n_mid * 1.0 + 0.4 * n_lo + 0.4 * np.clip(N[..., 1], 0, 1)) > 1.30)
        put(mo2, pick(P["MOSS"], np.floor(1.2 + 1.5 * lit + 1.2 * bay).astype(int)))
    # 縦に走る細い筋 (B: 金の細線が刃の縁に沿う)
    if op["gold"]:
        thin = m("main") & (np.abs(((X * 1.0) % 2.0) - 1.0) < 0.12) & (np.abs(N[..., 2]) > 0.5) & INS
        put(thin, pick(P["GOLD"], np.floor(2.4 + 0.8 * lit + bay).astype(int)))
    # ひび (C: 暗い線、一部が水色に光る)
    if op["crack"] > 0:
        rn = np.abs(vnoise(L * 0.22 + 7, 5) - 0.5)
        ck = np.isin(mats, ["main", "edge", "spine", "guard"]) & (rn < op["crack"]) & INS & (D > 0.4)
        put(ck, pick(P["DARK"], A_(1)))
        lit_ck = ck & (vnoise(L * 0.07 + 2, 9) > 0.70)
        put(lit_ck, ramp(P["CYAN"], 0.35 + 0.5 * hash1(xx + yy * 3, 1)))
        glow |= lit_ck
    # 光る面: gem (宝石・核)・vein (筋)
    gp = P["CYAN"]
    for name, lo, hi in (("gem", 0.40, 0.85), ("vein", 0.30, 0.70)):
        gm = m(name)
        put(gm, ramp(gp, lo + (hi - lo) * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.08 * bay))
        glow |= gm
    # 縁の明るい 1 画素 (全材質の凸の縁)
    return_col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = return_col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl_ = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl_ & owned
