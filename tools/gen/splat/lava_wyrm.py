# -*- coding: utf-8 -*-
"""熔岩の大蛇 (lava_wyrm) の点の模型 (試作。2026-11-15「ガウシアンスプラットのピクセルアートの水準に上げられるか」)。

骨・骨の位置・アニメは今の低ポリ版 (tools/gen/mobs/dungeon_bosses_lp/lava_wyrm.py) と同じ。形の道すじ (とぐろ・首・頭の輪) もそちらの数字を使い、
距離の関数で滑らかにつないで彫り直す。とぐろの区間・首の継ぎ目は二本の骨に重みを分けて、曲げても折れ目が出ない。

使い方:
  python -I tools/gen/splat/lava_wyrm.py            # 書き出し (assets/skyabyss/splat/lava_wyrm.bin) と下見 (docs/review/splat_pilot/lava_wyrm_preview.png)
  python -I tools/gen/splat/lava_wyrm.py --h 0.6    # 格子を粗く (点が減る)
"""
import argparse
import math
import sys
import time
from pathlib import Path

import numpy as np
from scipy.spatial import cKDTree

sys.path.insert(0, str(Path(__file__).resolve().parent))
import splat_lib as S  # noqa: E402

BONES = ["root", "body", "neck", "neck2", "head", "frill_l", "frill_r", "jaw", "seg1", "seg2", "seg3", "seg4"]

BAS = [(9, 8, 13), (18, 17, 25), (30, 29, 40), (46, 45, 58), (68, 67, 80), (98, 96, 108)]
BELLY = [(40, 14, 10), (74, 24, 12), (116, 40, 16), (164, 66, 22), (210, 104, 34)]
MAG = [(200, 70, 20), (240, 120, 36), (255, 170, 64), (255, 214, 120), (255, 244, 200)]
HORN = [(16, 13, 14), (30, 25, 24), (48, 40, 36), (70, 60, 52), (98, 86, 74)]
FRILL = [(56, 14, 10), (92, 26, 14), (138, 44, 20), (188, 72, 30), (230, 112, 48)]
TOOTH = [(96, 88, 78), (140, 130, 114), (182, 172, 152), (214, 206, 186)]
RAMPS = {"basalt": BAS, "belly": BELLY, "horn": HORN, "frill": FRILL, "tooth": TOOTH, "magma": MAG, "mouth": FRILL}

COIL_R = 15.0
CUTS = [0.0, 0.25, 0.45, 0.65, 0.82, 1.0]
SEGS = ["body", "seg1", "seg2", "seg3", "seg4"]


def coil_point(t):
    a = math.radians(-90 + t * 470)
    r = COIL_R * (1 - 0.42 * t)
    return np.array([math.cos(a) * r, 6.0 + 2.6 * (1 - t) + 1.2 * math.sin(t * math.pi * 3), math.sin(a) * r + 2.0])


def coil_rad(t):
    return (7.2 * (1 - 0.78 * t) + 0.6) * 0.86


def coil_w(t, band=0.035):
    """とぐろの t での骨の重み (区間の境の前後 band で隣と混ぜる)"""
    for i in range(1, 5):
        c = CUTS[i]
        if abs(t - c) < band:
            s = (t - c + band) / (2 * band)
            s = s * s * (3 - 2 * s)
            return {SEGS[i - 1]: 1 - s, SEGS[i]: s}
    for i in range(5):
        if CUTS[i] <= t <= CUTS[i + 1]:
            return {SEGS[i]: 1.0}
    return {"seg4": 1.0}


FRILL_COLLAR = np.array([0, 38.5, -13.5])
# 角の根元と長さ (色の根元 → 先の移りに使う。build が足す)
HORN_BASES = []


def frill_pt(a, r):
    """襟巻の膜の上の点。a: 首のまわりの角 (0 = 真後ろ)、r: 根元 0 → 縁 1"""
    base = FRILL_COLLAR + np.array([math.sin(a) * 3.6, 0, math.cos(a) * 1.4 + 1.4])
    tip = FRILL_COLLAR + np.array([math.sin(a) * 12.5, 3.4 + math.cos(a) * 2.4, math.cos(a) * 3.0 + 3.6])
    p = base + (tip - base) * r
    # 前へ反る (縁ほど前)・中ほどが盛り上がる・角に沿って波打つ
    p = p + np.array([0, 1.1 * math.sin(math.pi * min(r, 1.0)), -1.6 * r * r]) + np.array([0, 0.45 * math.sin(a * 6.0) * r, 0])
    return p


def frill_edge(u):
    """筋の間 (u: 0..1) の膜の縁の長さ。筋のところは 1、間はえぐれる"""
    return 1.0 - 0.24 * math.sin(math.pi * u) ** 1.4


_FRILL_TREE = None


def frill_param(P):
    """膜の上の点の (a, r)"""
    global _FRILL_TREE
    if _FRILL_TREE is None:
        ga = np.linspace(math.radians(-160), math.radians(160), 260)
        gr = np.linspace(0, 1, 40)
        pts, ar = [], []
        for a in ga:
            for r in gr:
                pts.append(frill_pt(a, r))
                ar.append((a, r))
        _FRILL_TREE = (cKDTree(np.array(pts)), np.array(ar))
    tree, ar = _FRILL_TREE
    _, idx = tree.query(P)
    return ar[idx, 0], ar[idx, 1]


def build(h):
    HORN_BASES.clear()
    sc = S.Sculpt("lava_wyrm", BONES, h=h)
    # ---- とぐろ (太い管と腹の帯)。区間の t を細かく取り、骨の重みを t ごとに
    ts = np.linspace(0, 1, 56)
    for i in range(len(ts) - 1):
        t0, t1 = ts[i], ts[i + 1]
        tm = (t0 + t1) / 2
        p0, p1 = coil_point(t0), coil_point(t1)
        sc.add(S.sd_round_cone(p0, p1, coil_rad(t0), coil_rad(t1)), "basalt", coil_w(tm), k=1.4)
        dn = np.array([0, 1, 0])
        sc.add(S.sd_round_cone(p0 - dn * coil_rad(t0) * 0.5, p1 - dn * coil_rad(t1) * 0.5, coil_rad(t0) * 0.52, coil_rad(t1) * 0.52), "belly", coil_w(tm), k=1.0)
    # 尾の先 (細く伸ばして棘で終わる)
    tip = coil_point(1.0)
    d = tip - coil_point(0.97)
    d /= np.linalg.norm(d)
    sc.add(S.sd_round_cone(tip, tip + d * 5.5 + np.array([0, 0.8, 0]), coil_rad(1.0), 0.15), "horn", "seg4", k=0.8)
    # 背の鱗の板と棘 (板は管の上に重ねて段にする、棘は一枚おき)
    for k in range(1, 40):
        t = k / 40
        p = coil_point(t)
        r = coil_rad(t)
        dd = coil_point(min(1, t + 0.01)) - coil_point(max(0, t - 0.01))
        dd /= np.linalg.norm(dd)
        side = np.cross(dd, [0, 1, 0])
        side /= np.linalg.norm(side)
        c = p + np.array([0, r * 0.74, 0]) + dd * 0.5
        sc.add(S.sd_ellipsoid(c, (abs(side[0]) * r * 0.6 + abs(dd[0]) * r * 0.42 + 0.2, r * 0.26, abs(side[2]) * r * 0.6 + abs(dd[2]) * r * 0.42 + 0.2)),
               "basalt", coil_w(t), k=0.5)
        if k % 2 == 1:
            base = p + np.array([0, r * 0.86, 0])
            tipp = base + (np.array([0, 1, 0]) - dd * 0.55) / np.linalg.norm(np.array([0, 1, 0]) - dd * 0.55) * (1.6 + r * 0.42)
            sc.add(S.sd_round_cone(base, tipp, r * 0.2 + 0.25, 0.08), "horn", coil_w(t), k=0.5)

    # ---- 首 (とぐろの前から立ち上がって頭の下で前へ曲がる)。下は neck、上は neck2、根元はとぐろ (body) へ混ぜる
    path = [(0, 8, -13), (0, 18, -17.5), (0, 28, -15.0), (0, 36.5, -11.5), (0, 43.5, -14.5)]
    rads = [5.6, 5.0, 4.5, 4.0, 3.7]

    def neck_w(t):
        if t < 0.12:
            s = t / 0.12
            return {"body": 1 - s, "neck": s}
        if abs(t - 0.5) < 0.07:
            s = (t - 0.43) / 0.14
            s = s * s * (3 - 2 * s)
            return {"neck": 1 - s, "neck2": s}
        return {"neck": 1.0} if t < 0.5 else {"neck2": 1.0}
    sc.tube(path, rads, "basalt", neck_w, k=1.4, n_per=6)
    sc.tube([np.array(p) + np.array([0, 0, -r * 0.48]) for p, r in zip(path, rads)], [r * 0.55 for r in rads], "belly", neck_w, k=1.0, n_per=6)
    sp = S.catmull(path, 6)
    rr = np.interp(np.linspace(0, 1, len(sp)), np.linspace(0, 1, len(rads)), rads)
    for i in range(2, len(sp) - 1, 2):
        t = i / (len(sp) - 1)
        p, r = sp[i], rr[i]
        sc.add(S.sd_ellipsoid(p + np.array([0, 0, r * 0.72]), (r * 0.66, r * 0.42, r * 0.3)), "basalt", neck_w(t), k=0.5)
        if i % 4 == 2:
            base = p + np.array([0, 0.4, r * 0.86])
            sc.add(S.sd_round_cone(base, base + np.array([0, 0.55, 1.0]) / 1.14 * 2.8, 0.9, 0.08), "horn", neck_w(t), k=0.4)

    # ---- 熾の襟巻: 骨の筋 7 本 (膜の縁より先へ突き出す棘) と、間の膜 (前へ反って波打つ曲面。縁は筋の間でえぐれた扇)
    def fb(a):
        s_ = math.sin(a)
        return "frill_l" if s_ > 0.05 else ("frill_r" if s_ < -0.05 else "neck2")
    ribs = [math.radians(-160 + k * (320 / 6)) for k in range(7)]
    for k, a in enumerate(ribs):
        pts = [frill_pt(a, r) for r in (0.0, 0.35, 0.72, 1.0, 1.2)]
        sc.tube(pts, [0.75, 0.55, 0.38, 0.22, 0.04], "horn", lambda t, a=a: {fb(a): 1.0} if t > 0.12 else {fb(a): 0.6, "neck2": 0.4}, k=0.25, n_per=3,
                tag="rib")
        if k < 6:
            a2 = ribs[k + 1]
            fw = "frill_l" if math.sin((a + a2) / 2) > 0 else "frill_r"
            nu, nr = 7, 6
            grid = [[frill_pt(a + (a2 - a) * (i / nu), frill_edge(i / nu) * j / nr) for j in range(nr + 1)] for i in range(nu + 1)]
            for i in range(nu):
                for j in range(nr):
                    p00, p10, p01, p11 = grid[i][j], grid[i + 1][j], grid[i][j + 1], grid[i + 1][j + 1]
                    sc.add(S.sd_triangle_slab(p00, p10, p11, 0.2), "frill", fw, k=0.2, tag="web")
                    sc.add(S.sd_triangle_slab(p00, p11, p01, 0.2), "frill", fw, k=0.2, tag="web")

    # ---- 頭: 楔の頭 (下の面を上げて口の隙間を作る)。眉の稜は目の上に張り出させ、目と鼻の穴は削って窪ませてから光る玉を入れる。
    # 頬の稜と、後ろへ流れる頬の棘、上あごの歯の列は頭の幅に沿わせる
    hb = np.array([0, 46, -16])
    skull = [(-12.0, 49.0, 44.9, 4.7, 46.2), (-16.0, 49.5, 45.0, 4.6, 46.4), (-20.0, 48.6, 45.0, 3.9, 46.3), (-24.0, 47.2, 45.0, 2.9, 46.0),
             (-26.6, 46.2, 45.1, 1.7, 45.6)]
    sc.add(S.sd_loft(skull, p=2.6), "basalt", "head", k=0.5, tag="skull")
    sc.add(S.sd_ellipsoid((0, 46.4, -12.6), (4.5, 3.2, 3.4)), "basalt", {"head": 0.7, "neck2": 0.3}, k=1.2)

    def width_at(z):
        zs = [q[0] for q in skull][::-1]
        return float(np.interp(z, zs, [q[3] for q in skull][::-1]))
    for s in (1, -1):
        # 眉の稜 (目の上に張り出す) と頬の稜
        sc.tube([(s * 2.0, 48.7, -25.0), (s * 3.5, 49.6, -21.6), (s * 4.0, 49.6, -18.0), (s * 3.6, 49.2, -14.8)], [0.45, 0.95, 0.85, 0.5], "basalt",
                lambda t: {"head": 1.0}, k=0.3, n_per=4, tag="brow")
        sc.tube([(s * 3.0, 46.2, -24.6), (s * 4.1, 46.5, -20.0), (s * 4.9, 46.5, -15.0), (s * 5.0, 46.8, -12.0)], [0.35, 0.7, 0.85, 0.6], "basalt",
                lambda t: {"head": 1.0}, k=0.3, n_per=4, tag="brow")
        # 目: 窪み → 光る玉
        sc.sub(S.sd_ellipsoid((s * 3.75, 47.9, -21.6), (1.35, 1.0, 1.55)), k=0.25)
        sc.add(S.sd_ellipsoid((s * 3.3, 47.9, -21.6), (0.75, 0.72, 1.05)), "magma", "head", k=0.08, glow=True, tag="eye")
        # 鼻の穴: 削って奥を光らせる
        sc.sub(S.sd_ellipsoid((s * 1.05, 46.75, -26.5), (0.5, 0.38, 0.7)), k=0.15)
        sc.add(S.sd_ellipsoid((s * 0.95, 46.6, -26.1), (0.3, 0.25, 0.3)), "magma", "head", k=0.05, glow=True)
        # 角 (反って後ろへ伸びる) と、耳の脇の小さな角
        d = np.array([s * 0.5, 0.6, 1.0])
        d /= np.linalg.norm(d)
        base = hb + np.array([s * 3.0, 3.4, -2.4])
        pts = [base + d * 9.5 * (i / 4) + np.array([s * 0.9, 3.4, 1.6]) * (i / 4) ** 2 for i in range(5)]
        hr = [1.7, 1.3, 0.95, 0.55, 0.06]
        sc.tube(pts, hr, "horn", lambda t: {"head": 1.0}, k=0.45, n_per=4, tag="horn")
        # 節の輪 (根元から先へ間を詰めて 8 本。輪の間の窪みが曲がり具合で暗くなり、段が出る)
        sp = S.catmull(pts, 12)
        for kk in range(8):
            t = 0.06 + 0.8 * (kk / 8) ** 0.85
            i0 = int(t * (len(sp) - 2))
            c, c2 = sp[i0], sp[i0 + 1]
            tan = (c2 - c) / np.linalg.norm(c2 - c)
            r = float(np.interp(t, np.linspace(0, 1, len(hr)), hr)) * 1.2 + 0.08
            sc.add(S.sd_round_cone(c - tan * 0.25, c + tan * 0.25, r, r * 0.9), "horn", "head", k=0.1, tag="horn")
        HORN_BASES.append((base, 13.0))
        d2 = np.array([s, 0.1, 0.8])
        d2 /= np.linalg.norm(d2)
        base2 = hb + np.array([s * 4.2, 1.2, -1.0])
        sc.tube([base2 + d2 * 4.8 * (i / 2) + np.array([0, 0.7, 0]) * (i / 2) ** 2 for i in range(3)], [0.95, 0.5, 0.06], "horn", lambda t: {"head": 1.0},
                k=0.35, n_per=3, tag="horn")
        HORN_BASES.append((base2, 5.0))
        # 頬の棘 (三本、後ろへ流れる)
        for k in range(3):
            cb = np.array([s * 4.6, 45.6 + k * 1.0, -13.5 + k * 0.6])
            sc.add(S.sd_round_cone(cb, cb + np.array([s * (1.4 - k * 0.2), 0.2 + k * 0.3, 3.0 - k * 0.5]), 0.5, 0.05), "horn", "head", k=0.25, tag="horn")
        # 上あごの牙と歯の列 (口の縁に沿って)
        fang_b = np.array([s * 2.0, 45.2, -24.4])
        sc.tube([fang_b, fang_b + np.array([0, -1.7, -0.3]), fang_b + np.array([0, -3.2, 0.25])], [0.6, 0.38, 0.04], "tooth", lambda t: {"head": 1.0}, k=0.12,
                n_per=4, tag="tooth")
        for k in range(6):
            z = -22.6 + k * 1.55
            tb = np.array([s * width_at(z) * 0.8, 45.15, z])
            sc.add(S.sd_round_cone(tb, tb + np.array([0, -(1.15 - k * 0.1), -0.12]), 0.3, 0.03), "tooth", "head", k=0.08, tag="tooth")
    # 鼻すじから頭頂、うなじへ続く稜
    sc.tube([(0, 47.9, -25.5), (0, 49.4, -21.0), hb + np.array([0, 4.0, -2.0]), hb + np.array([0, 3.7, 2.8])], [0.35, 0.55, 0.9, 0.4], "horn",
            lambda t: {"head": 1.0}, k=0.4, n_per=4, tag="horn")
    sc.add(S.sd_ellipsoid((0, 44.75, -20.0), (2.7, 0.3, 5.2)), "magma", "head", k=0.2, glow=True, tag="palate")
    # 下あご (頭とは継がない: 口を開くと中の光る面が見える)
    sc.add(S.sd_loft([(-13.5, 44.2, 41.6, 3.7, 43.0), (-19.0, 44.0, 42.0, 3.2, 43.0), (-24.6, 44.1, 43.0, 1.8, 43.6)], p=2.4), "basalt", "jaw", k=0.5, tag="jaw")
    sc.add(S.sd_ellipsoid((0, 43.95, -18.6), (2.3, 0.3, 5.0)), "magma", "jaw", k=0.2, glow=True, tag="tongue")
    for s in (1, -1):
        for k in range(5):
            z = -23.6 + k * 1.9
            w = float(np.interp(z, [-24.6, -19.0, -13.5], [1.8, 3.2, 3.7]))
            tb = np.array([s * w * 0.78, 43.95, z])
            sc.add(S.sd_round_cone(tb, tb + np.array([0, 1.0 - k * 0.08, -0.1]), 0.28, 0.03), "tooth", "jaw", k=0.08, tag="tooth")
        # あごの下の小さな棘
        cb = np.array([s * 1.3, 42.0, -20.5])
        sc.add(S.sd_round_cone(cb, cb + np.array([s * 0.5, -1.5, 1.6]), 0.42, 0.05), "horn", "jaw", k=0.25, tag="horn")
    return sc


def colour(sc, P, n, pid1, ijk, rng):
    parts = sc.parts
    mats = np.array([parts[i][3] for i in pid1])
    tags = np.array([str(parts[i][7]) for i in pid1])
    glow = np.array([parts[i][6] for i in pid1])
    # 光: 前上からの主光 + 空の明るさ + 窪みの暗さ
    key = np.array([0.35, 0.9, -0.55])
    key /= np.linalg.norm(key)
    lam = np.clip(n @ key, 0, 1)
    hemi = 0.5 + 0.5 * n[:, 1]
    ao = sc.ao(P, n)
    L = np.clip((0.04 + 0.3 * hemi + 0.7 * lam) * (0.35 + 0.65 * ao), 0, 1)
    # 尾根は一段明るく、谷は暗く (ピクセルアートの縁取りの光。眉・角・歯の形が立つ)
    lap = sc.curvature(P)
    L = np.where((lap > 2.2) & (lam > 0.25), np.clip(L + 0.15, 0, 1), np.where(lap < -0.9, L * 0.55, L))
    head = np.array([("head" in parts[i][4]) or ("jaw" in parts[i][4]) for i in pid1])
    dither = S.BAYER4[(ijk[:, 0] + ijk[:, 2]) % 4, ijk[:, 1] % 4]
    col = np.zeros((len(P), 3))
    flags = np.zeros(len(P), np.int32)

    # 玄武岩: 小さな鱗 (細胞の中心ほど盛り上がって明るく、縁は暗い溝) と、大きな割れ目の網 (その一部が熔けて光る線)
    f1, f2, idv = S.worley(P, 1.7, 19)
    h1, h2, hid = S.worley(P, 0.85, 29)
    f1 = np.where(head, h1, f1)
    f2 = np.where(head, h2, f2)
    idv = np.where(head, hid, idv)
    edge = f2 - f1
    c1, c2, cid = S.worley(P, 5.5, 41)
    crack = c2 - c1
    for m, ramp in S_RAMPS():
        sel = mats == m
        if not sel.any():
            continue
        Lm = L[sel].copy()
        if m == "basalt":
            dome = np.clip(1.25 - 0.75 * f1[sel], 0.5, 1.25)
            Lm = Lm * dome * (0.9 + 0.2 * idv[sel]) * np.where(edge[sel] < 0.07, 0.45, 1.0)
        col[sel] = S.ramp_pick(ramp, np.clip(Lm, 0, 1), dither[sel])
    # 熔けた割れ目 (線の太さは一定。上を向いた面には少なく)
    seam = (mats == "basalt") & (crack < 0.035) & (cid > 0.3) & (n[:, 1] < 0.6) & (~head | (n[:, 1] < -0.2))
    col[seam] = S.ramp_pick(MAG, 0.3 + 0.6 * (1 - crack[seam] / 0.035), dither[seam])
    flags[seam] |= S.F_GLOW
    rim = (mats == "basalt") & ~seam & (crack < 0.07) & (cid > 0.3) & (n[:, 1] < 0.6)
    col[rim] = col[rim] * 0.5 + np.array([120, 40, 10]) * 0.5
    # 腹: 横縞の板と熱い斑
    b1, b2, bid = S.worley(P, 1.5, 23)
    belly = mats == "belly"
    hot = belly & (bid > 0.86)
    col[hot] = S.ramp_pick(MAG, 0.3 + 0.4 * bid[hot], dither[hot])
    flags[hot] |= S.F_GLOW
    plate = belly & ~hot & ((b2 - b1) < 0.07)
    col[plate] *= 0.6
    # 光る部品 (目・口の中・鼻)
    gsel = glow.astype(bool)
    eye = tags == "eye"
    col[gsel] = S.ramp_pick(MAG, 0.55 + 0.4 * np.clip(n[gsel] @ np.array([0, 0.3, -1]) * 0.5 + 0.5, 0, 1), dither[gsel])
    col[eye] = np.array([255, 236, 150])
    # 口の中 (上あご・舌) は暗めの熾。閉じた口の線が白く飛ばないように
    mouth = (tags == "palate") | (tags == "tongue")
    col[mouth] = S.ramp_pick(MAG, np.full(mouth.sum(), 0.12), dither[mouth]) * 0.7
    # 目の瞳 (縦の細い線)
    for s in (1, -1):
        c = np.array([s * 3.3, 47.9, -21.6])
        q = P - c
        slit = eye & (np.abs(q[:, 1]) < 0.75) & (np.abs(q[:, 2] + q[:, 0] * 0.4 * s) < 0.22) & (q[:, 0] * s > 0.3)
        col[slit] = np.array([40, 10, 6])
    flags[gsel] |= S.F_GLOW
    # 襟巻の膜: 根元は暗い赤、縁へ向かって熾の色。放射状の脈と、えぐれた縁の線が光る (裏から火に透けて見える)
    web = tags == "web"
    if web.any():
        fa, fr = frill_param(P[web])
        span = math.radians(320 / 6)
        u = ((fa - math.radians(-160)) / span) % 1.0
        edge = np.array([frill_edge(x) for x in u])
        rel = fr / edge
        Lw = np.clip(0.15 + 0.75 * rel ** 1.3, 0, 1) * (0.7 + 0.3 * L[web])
        # 筋の近くは影 (膜が筋に引かれて窪む)
        Lw = Lw * np.where((u < 0.07) | (u > 0.93), 0.6, 1.0)
        cw = S.ramp_pick(FRILL, Lw, dither[web])
        vein = (np.abs(((u * 3.0) % 1.0) - 0.5) < 0.05) & (rel > 0.25)
        rimm = rel > 0.93
        cw[vein] = S.ramp_pick(MAG, np.full(vein.sum(), 0.2) + 0.4 * rel[vein], dither[web][vein])
        cw[rimm] = S.ramp_pick(MAG, np.full(rimm.sum(), 0.55), dither[web][rimm])
        col[web] = cw
        fl = flags[web]
        fl[vein | rimm] |= S.F_GLOW
        flags[web] = fl
    # 角・筋: 根元は暗く先ほど骨の色、輪の溝 (下の角の作りの溝に合わせる)
    hornm = (tags == "horn") | (tags == "rib")
    if hornm.any():
        Lh = L[hornm] * (0.75 + 0.25 * ao[hornm])
        # 根元に近い角ほど暗く、先は一段明るい骨の色
        tt = np.ones(hornm.sum())
        for hbase, hlen in HORN_BASES:
            dd = np.linalg.norm(P[hornm] - hbase, axis=1) / hlen
            near = dd < 1.05
            tt = np.where(near, np.minimum(tt, dd), tt)
        tt = np.clip(tt, 0, 1)
        # 明るさは「根元 → 先」を主に、光の当たりは控えめに混ぜる (光だけだと角全体が同じ明るさで飽和する)
        Lh = 0.1 + 0.6 * tt + 0.45 * (Lh - 0.45)
        # 節の溝: 根元ほど間が広い輪
        groove = (((tt ** 0.85) * 8.0) % 1.0 < 0.22) & (tt < 0.88)
        Lh = np.where(groove, Lh * 0.45, Lh)
        col[hornm] = S.ramp_pick(HORN, np.clip(Lh, 0, 1), dither[hornm])
    # 熔けた所の照り返し: 近くの光る点の数で、暗い面に橙を足す
    gp = P[((flags & S.F_GLOW) != 0) & ~mouth]
    if len(gp):
        tree = cKDTree(gp)
        cnt = np.array([len(x) for x in tree.query_ball_point(P, 3.2)])
        warm = np.clip(cnt / (45.0 * (0.42 / sc.h) ** 2), 0, 0.55)[:, None] * ((flags & S.F_GLOW) == 0)[:, None]
        col = col + warm * np.array([90, 30, 6])
    return np.clip(col, 0, 255), flags


def S_RAMPS():
    return [(m, r) for m, r in RAMPS.items() if m not in ("magma",)]


HEAD_BOX = ((-18.0, 35.5, -30.0), (18.0, 62.0, -1.0))


def in_box(P, box):
    lo, hi = np.asarray(box[0]), np.asarray(box[1])
    return np.all((P >= lo) & (P <= hi), axis=1)


def bake(h, box=None, keep=None):
    """一回ぶん彫って表面の点を出す。box: 評価する範囲。keep(P) → 残す点"""
    sc = build(h)
    sc.evaluate(box=box)
    P, n, pid1, pid2, gap, ijk = sc.surface()
    m = keep(P)
    P, n, pid1, pid2, gap, ijk = P[m], n[m], pid1[m], pid2[m], gap[m], ijk[m]
    # 頭と下あごは混ぜない (口が開くと伸びてしまう)
    pb = np.array([sorted(sc.parts[i][4])[0] if i >= 0 else "" for i in pid2])
    pa = np.array([sorted(sc.parts[i][4])[0] for i in pid1])
    nomix = ((pa == "jaw") & (pb != "jaw")) | ((pb == "jaw") & (pa != "jaw"))
    pid2 = np.where(nomix, -1, pid2)
    A, B, W = S.bone_weights(sc.parts, pid1, pid2, gap, BONES)
    col, flags = colour(sc, P, n, pid1, ijk, None)
    eye = np.array([sc.parts[i][7] == "eye" for i in pid1], bool)
    mouth = np.array([sc.parts[i][7] in ("palate", "tongue") for i in pid1], bool)
    return dict(P=P, n=n, A=A, B=B, W=W, col=col, flags=flags, size=np.full(len(P), h * 1.4), eye=eye, mouth=mouth)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--h", type=float, default=0.42)
    ap.add_argument("--hh", type=float, default=0.24, help="頭の格子の間隔")
    ap.add_argument("--no-preview", action="store_true")
    a = ap.parse_args()
    t0 = time.time()
    body = bake(a.h, keep=lambda P: ~in_box(P, HEAD_BOX))
    head = bake(a.hh, box=HEAD_BOX, keep=lambda P: in_box(P, HEAD_BOX))
    print(f"body {len(body['P'])} + head {len(head['P'])} splats ({time.time() - t0:.1f}s)")
    sf = {k: np.concatenate([body[k], head[k]]) for k in body}
    P, n, A, B, W, col, flags = sf["P"], sf["n"], sf["A"], sf["B"], sf["W"], sf["col"], sf["flags"]
    rng = np.random.default_rng(5)
    N = len(P)
    size = sf["size"] * rng.uniform(0.85, 1.15, N)

    # 輪郭のけば (表面から少し浮いた小さい点) は使わない (2026-11-16「体の周りにブロックが浮いてるのいらない」)。枠だけ残す
    k = np.zeros(N, bool)
    fz = dict(P=P[k] + n[k] * rng.uniform(0.25, 1.1, k.sum())[:, None], size=size[k] * 0.6, col=col[k] * 0.8, A=A[k], B=B[k], W=W[k],
              flags=flags[k] & S.F_GLOW, n=n[k])
    # にじみ (光る点の一部に、柔らかい大きな丸を足し算で。点の細かさに合わせて間引く)
    g = np.nonzero(((flags & S.F_GLOW) != 0) & ~sf["mouth"])[0]
    prob = 0.22 * (sf["size"][g] / (a.h * 1.4)) ** 2
    gi = g[rng.random(len(g)) < prob]
    eyes = sf["eye"][gi]
    hz = dict(P=P[gi] + n[gi] * 0.4, size=np.where(eyes, 3.4, rng.uniform(2.6, 4.2, len(gi))),
              col=np.where(eyes[:, None], np.array([22, 13, 4]), col[gi] * 0.10), A=A[gi], B=B[gi], W=W[gi], flags=np.full(len(gi), S.F_HALO),
              n=np.zeros((len(gi), 3)))
    # 火の粉 (とぐろと首の上の面から昇る)
    up = np.nonzero((n[:, 1] > 0.5) & (np.isin(A, [BONES.index(b) for b in ("body", "seg1", "seg2", "neck")])))[0]
    ei = rng.choice(up, size=min(160, len(up)), replace=False)
    em = dict(P=P[ei], size=rng.uniform(0.45, 0.8, len(ei)), col=np.tile(np.array([255, 140, 40]), (len(ei), 1)) * rng.uniform(0.6, 1.0, (len(ei), 1)),
              A=A[ei], B=B[ei], W=W[ei], flags=np.full(len(ei), S.F_EMBER), n=np.zeros((len(ei), 3)))
    base = dict(P=P, size=size, col=col, A=A, B=B, W=W, flags=flags, n=n)
    allv = {key: np.concatenate([d[key] for d in (base, fz, hz, em)]) for key in base}
    out = S.ROOT / "src/main/resources/assets/skyabyss/splat/lava_wyrm.bin"
    S.write_bin(out, BONES, allv["P"], allv["size"], S.argb(allv["col"]), allv["A"], allv["B"], allv["W"], allv["flags"], N=allv["n"])
    print(f"wrote {out} total {len(allv['P'])} (fuzz {len(fz['P'])}, halo {len(gi)}, embers {len(ei)}, glow {len(g)}) {out.stat().st_size // 1024} KB "
          f"in {time.time() - t0:.1f}s")
    if not a.no_preview:
        pv = S.ROOT / "docs/review/splat_pilot/lava_wyrm_preview.png"
        pv.parent.mkdir(parents=True, exist_ok=True)
        S.preview(allv["P"], allv["size"], allv["col"], allv["flags"], pv, yaw_list=(200, 150, 90), px=700)
        # 頭のアップ (頭の範囲の点だけ)
        hm = in_box(allv["P"], ((-19, 34, -32), (19, 62, 4)))
        S.preview(allv["P"][hm], allv["size"][hm], allv["col"][hm], allv["flags"][hm], pv.with_name("lava_wyrm_head.png"), yaw_list=(200, 150, 100), px=700)
        print("preview", pv)


if __name__ == "__main__":
    main()
