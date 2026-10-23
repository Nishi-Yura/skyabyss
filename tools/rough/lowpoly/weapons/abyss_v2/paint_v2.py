"""五章「深淵」の装備 v2 の絵。土台の言葉は ../gear_gaps/paint_gaps.py (紫黒の虚・錨鉄・品ごとの光) のまま、
材質を増やして描き込む: 錨鉄の打ち跡と鋲と錆の垂れ、古い真鍮の緑青、革の巻きの縫い目、骨の成長線、付着物 (藤壺・塩)、
灯の硝子 (枠の近くが暗く真ん中が明るい)、刃の樋 (fuller) と研ぎ跡。縁 (trim) は階級の色、強化 +5 / +10 で光の筋が増える。"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from wkit import pick, ramp, LIGHT, hash1, vnoise, BAYER, edge_fields, fill_outside  # noqa: E402

A_ = np.array
PAL = dict(
    VOID=A_([(12, 8, 22), (22, 15, 38), (34, 24, 58), (48, 35, 80), (66, 49, 106), (88, 68, 134), (112, 92, 160)]),
    VEDGE=A_([(56, 44, 92), (80, 64, 124), (108, 90, 156), (142, 124, 188), (182, 168, 220), (220, 212, 240)]),
    AIRON=A_([(20, 22, 32), (34, 38, 52), (52, 58, 76), (74, 82, 102), (100, 110, 130), (132, 142, 160), (170, 178, 194)]),
    RUST=A_([(54, 30, 24), (84, 46, 30), (118, 66, 38), (150, 92, 52)]),
    BRASS=A_([(40, 30, 20), (66, 50, 30), (96, 74, 42), (128, 100, 56), (160, 128, 72), (192, 160, 96), (220, 194, 132)]),
    VERD=A_([(30, 66, 62), (46, 96, 84), (70, 128, 106), (100, 158, 130)]),
    LEATHER=A_([(26, 16, 14), (42, 26, 22), (60, 38, 30), (82, 54, 40), (106, 72, 52)]),
    SINEW=A_([(44, 14, 20), (70, 24, 32), (98, 38, 44), (128, 58, 60)]),
    BONE=A_([(84, 74, 76), (110, 100, 98), (134, 124, 116), (158, 148, 134), (180, 170, 152), (200, 191, 170), (218, 210, 188), (236, 230, 210)]),
    BARN=A_([(70, 78, 84), (104, 112, 114), (140, 148, 146), (178, 184, 178), (212, 216, 206)]),
    CORD=A_([(40, 36, 44), (62, 56, 66), (88, 82, 92), (120, 114, 124)]),
    DARK=A_([(5, 3, 10), (12, 8, 20), (22, 16, 34)]),
    T_rare=A_([(30, 46, 72), (48, 74, 112), (72, 108, 156), (106, 148, 200), (152, 194, 238), (210, 230, 252)]),
    T_legend=A_([(48, 36, 70), (78, 62, 108), (112, 94, 146), (150, 132, 184), (192, 178, 222), (232, 224, 248)]),
    G_lamp=A_([(196, 100, 26), (236, 146, 44), (255, 192, 92), (255, 226, 160), (255, 246, 222)]),
    G_void=A_([(112, 56, 210), (150, 96, 246), (196, 150, 255), (228, 204, 255), (246, 236, 255)]),
    G_pulse=A_([(140, 16, 36), (200, 34, 54), (244, 82, 98), (255, 150, 156), (255, 214, 214)]),
)
GLOW_RGB = dict(lamp=(255, 180, 80), void=(190, 140, 255), pulse=(255, 90, 100))
ALIAS = {"spine": "iron", "acc": "iron", "guard": "iron"}


def paint(faces, size, P, N, M, L, glow_key="lamp", tier="rare", plus=0, inlay=None, edge_mats=(), fuller=None):
    """fuller(env) -> bool: 刃の樋 (暗い溝) の画素。inlay(env, kind) -> bool: kind = legend (伝説はいつも) / p5 / p10"""
    D, K, LU, LV, CW, CH, INS = edge_fields(faces, size)
    H = W = size
    yy, xx = np.mgrid[0:H, 0:W]
    fi = np.where(M >= 0, M, 0)
    owned = M >= 0
    mats = np.array([ALIAS.get(f.mat, f.mat) for f in faces])[fi]
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
    up = np.clip(N[..., 1], -1, 1)

    def put(mask, c):
        nonlocal col
        col = np.where(mask[..., None], c, col)

    def shaded(pal, base, lw=0.30, nw=0.12, extra=0.0, ed=True):
        v = base + lw * lit + nw * 0.7 * (n_lo - 0.5) + 0.06 * (n_mid - 0.5) + 0.045 * bay + extra
        idx = np.floor(v * len(pal)).astype(int)
        if ed:
            idx = idx + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave
        return idx

    m = lambda name: mats == name
    VO, VE, AI = PAL["VOID"], PAL["VEDGE"], PAL["AIRON"]
    TM = PAL["T_" + tier]
    gp = PAL["G_" + glow_key]
    env = dict(L=L, N=N, mats=mats, convex=convex, concave=concave, D=D, INS=INS, K=K, lit=lit, edge=edge, X=X, Y=Y, Z=Z,
               LU=LU, LV=LV, CW=CW, CH=CH, fi=fi)

    # ---- 虚の黒紫 (刃・錨の腕): 斜めの流れの筋、研ぎ跡 (縦の細い明るい線)、点の欠け
    flow = np.sin((X * 1.3 + Y * 0.9) * 1.1 + 4.0 * n_lo) > 0.82
    hone = (hash1(np.floor(X * 3.0) * 5 + fi, 3) > 0.86) & (np.abs(N[..., 2]) > 0.5)
    put(m("main"), pick(VO, shaded(VO, 0.62, 0.34, 0.10) + (flow & (n_mid > 0.35)) + hone - (pit < 0.05)))
    if fuller is not None:
        fm = fuller(env) & m("main")
        put(fm, pick(VO, shaded(VO, 0.30, 0.20, 0.06, ed=False)))
        # 樋の縁の 1 画素の明るい線 (研いだ肩)
        near = np.zeros_like(fm)
        for ax_ in (0, 1):
            for s_ in (1, -1):
                near |= np.roll(fm, s_, ax_)
        fl = near & ~fm & m("main") & (np.abs(N[..., 2]) > 0.5) & (D > 0.6)
        put(fl, pick(VO, shaded(VO, 0.62, 0.2, 0.04, ed=False) + 2))
    put(m("edge"), pick(VE, shaded(VE, 0.60, 0.32, 0.06) + (convex & (lit > 0.2)) - (pit < 0.03)))

    # ---- 錨鉄: 打ち跡のまだら、縁の摩耗 (明るい)、縁の近くの鋲、上向きの面の錆の垂れ
    hammer = hash1(np.floor(X * 0.8) * 7 + np.floor(Y * 0.8) * 3 + np.floor(Z * 0.8) * 11 + fi, 4) * 0.10 - 0.05
    rivet = (D > 1.2) & (D < 2.2) & ((((LU // 3) + (LV // 3)) % 4) == 0) & INS & (CW >= 8) & (CH >= 8)
    iron = m("iron")
    iidx = shaded(AI, 0.52, 0.34, 0.08, extra=hammer) - (n_hi > 0.9) - (pit < 0.025)
    put(iron, pick(AI, iidx))
    put(iron & convex & (lit > -0.2), pick(AI, iidx + 2))
    put(iron & rivet, pick(AI, iidx + 2))
    rust_n = vnoise(L * 0.22 + V3(17), 3)
    streak = (np.abs(np.sin(X * 2.3 + Z * 1.7 + 3 * n_lo)) > 0.93) & (n_mid > 0.45)
    rust = iron & (((rust_n > 0.82) & (np.abs(up) < 0.7) & (D > 1.0)) | (streak & (D > 0.8) & (n_mid > 0.6)) | (concave & (n_mid > 0.7)))
    put(rust, pick(PAL["RUST"], np.floor(1.2 + 1.4 * (0.5 + 0.5 * lit) + bay + (n_hi - 0.5)).astype(int)))
    put(m("chain"), pick(AI, shaded(AI, 0.42, 0.3, 0.08) - (pit < 0.08)))
    put(m("chain") & (n_mid > 0.62), pick(PAL["RUST"], np.floor(1.4 + 1.4 * lit + bay).astype(int)))

    # ---- 古い真鍮: 磨かれた出っ張り、凹みと下向きの面に緑青
    br = m("brass")
    bidx = shaded(PAL["BRASS"], 0.56, 0.40, 0.05)
    put(br, pick(PAL["BRASS"], bidx))
    put(br & convex & (lit > 0), pick(PAL["BRASS"], bidx + 1))
    verd = br & ((concave & (n_mid > 0.45)) | ((n_mid + 0.5 * n_lo - 0.4 * up) > 1.3))
    put(verd, pick(PAL["VERD"], np.floor(1.0 + 1.6 * (0.5 + 0.5 * lit) + bay).astype(int)))

    # ---- 革の巻き (斜めの帯と縫い目)、腱の巻き
    turn = (X * 0.55 + Y * 0.95 + Z * 0.55)
    seam = np.abs((turn % 1.6) - 0.8) < 0.16
    stitch = seam & ((np.floor(Y * 2.2 + X) % 2) == 0)
    put(m("leather"), pick(PAL["LEATHER"], shaded(PAL["LEATHER"], 0.52, 0.30, 0.12) - seam + (n_hi > 0.85)))
    put(m("leather") & stitch, pick(PAL["CORD"], np.floor(1.6 + lit + bay).astype(int)))
    put(m("grip"), pick(PAL["LEATHER"], shaded(PAL["LEATHER"], 0.40, 0.26, 0.10)))
    wt = (np.floor(Y * 1.4 - X * 0.6) % 2) == 0
    put(m("wrap"), pick(PAL["SINEW"], shaded(PAL["SINEW"], 0.52, 0.30, 0.10) - wt))
    put(m("cord"), pick(PAL["CORD"], shaded(PAL["CORD"], 0.6, 0.30, 0.06) - ((np.floor(Y * 1.6 + X * 0.8) % 2) == 0)))

    # ---- 骨: 成長線 (縦の筋)、凹みの影、小さな穴
    growth = np.sin(Y * 1.6 + 2.5 * n_lo + X * 0.4) > 0.7
    bo = m("bone")
    put(bo, pick(PAL["BONE"], shaded(PAL["BONE"], 0.66, 0.30, 0.08) - (growth & (n_mid > 0.45)) - (pit < 0.015)))
    put(bo & concave, pick(PAL["BONE"], A_(1)))

    # ---- 付着物 (藤壺と塩): 小さな白い点、間は暗く
    ba = m("barn")
    put(ba, pick(PAL["BARN"], shaded(PAL["BARN"], 0.56, 0.30, 0.14) - (pit < 0.12) * 2))
    put(m("dark"), pick(PAL["DARK"], np.floor(0.6 + 1.0 * (0.5 + 0.5 * lit) + 0.6 * bay + (D < 2.5) + (D < 1.2)).astype(int)))

    # ---- 光る核と硝子
    gm = m("gem")
    put(gm, ramp(gp, np.clip(0.18 + 0.50 * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.08 * bay + 0.25 * (D > 2), 0, 0.92)))
    glow |= gm
    gl = m("glass")
    u = (LU + 0.5) / np.maximum(CW, 1) - 0.5
    v = (LV + 0.5) / np.maximum(CH, 1) - 0.5
    pane = 1.0 - 1.7 * np.maximum(np.abs(u), np.abs(v))
    flame = (np.abs(u) < 0.16 + 0.12 * (v + 0.5)) & (v > -0.30)
    put(gl, ramp(gp, np.clip(0.10 + 0.55 * pane + 0.10 * bay + 0.25 * flame, 0, 0.95)))
    glow |= gl

    # ---- 縁 (階級の色)。強化で一段ずつ明るく
    tidx = shaded(TM, 0.55, 0.30, 0.06) + (1 if plus >= 5 else 0) + (1 if plus >= 10 else 0)
    put(m("trim"), pick(TM, tidx))

    # ---- 光の筋
    kinds = (["legend"] if tier == "legend" else []) + (["p5"] if plus >= 5 else []) + (["p10"] if plus >= 10 else [])
    if inlay is not None:
        for k in kinds:
            gmask = inlay(env, k) & INS
            put(gmask, ramp(gp, 0.40 + 0.35 * hash1(LU + LV * 3, 2) + (0.15 if k == "p10" else 0.0)))
            glow |= gmask
    if plus >= 10 and edge_mats:
        em = np.isin(mats, list(edge_mats)) & convex & (lit > -0.1)
        put(em, ramp(gp, 0.45 + 0.3 * hash1(xx + yy, 9)))
        glow |= em
    put(m("trim") & convex, pick(TM, tidx + 1))

    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl_ = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl_ & owned


def V3(k):
    return np.array([k, k * 0.37, k * 0.71])


# ------------------------------------------------------------------ inlay の部品

def along(env, mats_, cx, y0, y1, half, dash=None, front_back=True, zf=None):
    """刃の中心線 x = cx(y) 沿いの光る画素 (広い面だけ)"""
    e = env
    ok = np.isin(e["mats"], list(mats_)) & (e["Y"] >= y0) & (e["Y"] <= y1) & (np.abs(e["X"] - cx(e["Y"])) <= half)
    ok &= np.abs(e["N"][..., 2]) > 0.5
    if dash:
        p, l = dash
        ok &= (e["Y"] % p) < l
    return ok


def veins(env, mats_, width, seed=7, y0=-99, y1=99):
    """割れ目の筋 (ノイズの尾根)。width が太いほど多く見える"""
    e = env
    r = np.abs(vnoise(e["L"] * 0.22 + seed, 5) - 0.5)
    return np.isin(e["mats"], list(mats_)) & (r < width) & (e["D"] > 0.4) & (e["Y"] >= y0) & (e["Y"] <= y1)


def bands_y(env, mats_, ys, half=0.22):
    e = env
    ok = np.zeros(e["Y"].shape, bool)
    for y in ys:
        ok |= np.abs(e["Y"] - y) < half
    return np.isin(e["mats"], list(mats_)) & ok
