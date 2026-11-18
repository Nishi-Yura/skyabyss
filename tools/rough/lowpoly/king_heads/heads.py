# -*- coding: utf-8 -*-
"""淵の王 5 体の頭 (2026-11-11 の声「顔の作り同じだと手抜きがばれる」「顔なくても王に見える」「歯や骨のような細かい形」)。

前は 3 体が同じ人の頭 (丸い頭と二つの黒い目) で、冠だけ違っていた。頭の骨 (head) の三角形を外し、王ごとに作りの違う頭を足す。
  骨の王  獣の頭蓋: 長い鼻づら・一本ずつの歯と牙・開いた下顎・眉の張り・頬骨・巻いた角・頭頂の棘・首の背骨
  塩の王  塩の晶の冠 (顔なし): 六角の柱に尖った先の結晶の束、内側に光る芯、首もとの塩の殻と小さな晶
  雷の王  閉じた鉄の兜 (顔なし): 細い光る覗き窓と正面の稜、通気の穴、銅の帯と鋲、頭の上の碍子と銅の巻線と避雷針
  灰の王  炉の頭 (顔なし): 鋳鉄の釜、正面に格子の焚き口と燠、横の光る通気口と庇、三本の煙突 (縁・つば・中の火・煤の垂れ)
  影の王  白い面と頭巾 (顔なし): 深い頭巾と尖った先、目のない白い面に光る割れ目、背に黒い刃の後光と銀の輪
座標は今の模型と同じ (前は -Z、単位は模型の画素)。部品はすべて閉じた形で、つなぎ目は親へ沈める。
"""
from __future__ import annotations

import math

import numpy as np

V3 = np.array


def _env():
    import blp  # noqa: F401  (blp の * は呼び出し側で読み込み済み)
    return blp


def anchor(R):
    lo, hi, _ = R.box("head")
    return V3([0.0, hi[1] - 4.5, 0.6])


def nrm(v):
    v = np.asarray(v, float)
    return v / max(1e-9, np.linalg.norm(v))


def crystal(b, m, mat, base, d, L, r):
    """六角の柱に尖った先 (根元は沈める)。"""
    d = nrm(d)
    base = np.asarray(base, float)
    return b.tub("root", mat, [base, base + d * L * 0.74, base + d * L], [r, r * 0.94, 0.04], None, sides=6)


def sring(c, a, d, n=20, p=3.0):
    """超楕円の輪の点 (水平、閉じる)。"""
    pts = []
    for k in range(n + 1):
        t = k / n * math.tau
        cc, ss = math.cos(t), math.sin(t)
        pts.append(np.asarray(c, float) + V3([a * math.copysign(abs(cc) ** (2 / p), cc), 0, d * math.copysign(abs(ss) ** (2 / p), ss)]))
    return pts


# ------------------------------------------------------------------ 骨の王
def bone_head(m, R):
    b = _env()
    H = anchor(R)
    P = lambda x, y, z: H + V3([x, y, z])  # noqa: E731
    # 頭蓋 (後ろが丸く、前で鼻づらへ)
    m.add("kh_cran", b.limb("root", "bone", [P(0, 0.4, 4.6), P(0, 1.2, 1.2), P(0, 0.8, -2.4)], [6.4, 9.0, 7.6], [6.2, 8.6, 7.0], n=14))
    m.add("kh_occ", b.ell("root", "bone", P(0, -0.6, 4.2), (2.6, 2.0, 1.4), segs=8, rings=3))
    # 鼻づら (上顎)
    m.add("kh_snout", b.limb("root", "bone", [P(0, -0.4, -2.2), P(0, -1.1, -6.8), P(0, -1.5, -10.6)], [7.0, 5.0, 3.4], [5.0, 3.5, 2.6], n=12))
    m.add("kh_ridge", b.tub("root", "bone", [P(0, 2.6, -2.6), P(0, 1.0, -6.6), P(0, 0.0, -10.2)], [0.9, 0.7, 0.5], None, sides=5))
    for s in (1, -1):
        m.add(f"kh_nost{s}", b.ell("root", "void", P(s * 0.7, -0.9, -11.6), (0.55, 0.45, 0.5), segs=6, rings=2))
        # 目の窪み (奥に赤い灯) と眉の張り
        m.add(f"kh_sock{s}", b.ell("root", "void", P(s * 2.7, 1.5, -2.4), (1.6, 1.4, 1.0), segs=8, rings=3))
        m.add(f"kh_pupil{s}", b.ell("root", "glow", P(s * 2.6, 1.4, -3.3), (0.42, 0.42, 0.25), segs=6, rings=2))
        m.add(f"kh_brow{s}", b.tub("root", "bone", [P(s * 0.6, 3.3, -3.0), P(s * 2.6, 3.4, -3.3), P(s * 4.4, 2.6, -2.2)], [0.8, 0.85, 0.6], None, sides=5))
        # 頬骨 (目の下から耳へ)
        m.add(f"kh_zyg{s}", b.tub("root", "bone", [P(s * 2.5, -0.3, -3.2), P(s * 4.4, -0.4, -0.6), P(s * 4.3, -0.9, 2.6)], [0.6, 0.65, 0.5], None, sides=5))
        # 上の歯 (奥から前へ、一本ずつ。二本目が牙)
        for k in range(7):
            t = k / 6
            z = -3.4 - t * 6.8
            half = 3.3 - t * 1.6
            L, r = (3.4, 0.62) if k == 1 else (1.5 + 0.35 * (k % 2), 0.36)
            m.add(f"kh_ut{s}{k}", b.cone("root", "salt", P(s * half * 0.86, -2.5, z), (s * 0.08, -1, -0.12 if k == 1 else 0), L, r, sides=4, rings=2,
                                         bend=(0, 0, -0.5) if k == 1 else (0, 0, 0)))
        # 下顎 (少し開いている): 蝶番から顎先へ
        hinge = P(s * 3.7, -1.6, 1.6)
        mid = P(s * 3.0, -4.6, -3.0)
        chin = P(s * 1.1, -6.0, -8.4)
        m.add(f"kh_jaw{s}", b.tub("root", "bone", [hinge, mid, chin], [1.0, 0.85, 0.7], None, sides=6))
        m.add(f"kh_cor{s}", b.cone("root", "bone", hinge + V3([0, 0.2, -0.4]), (0, 1, -0.5), 2.2, 0.6, sides=4, rings=1))
        for k in range(5):
            t = (k + 0.5) / 5
            q = mid + (chin - mid) * t if t > 0 else mid
            L = 2.6 if k == 4 else 1.3
            m.add(f"kh_lt{s}{k}", b.cone("root", "salt", q + V3([0, 0.5, 0]), (0, 1, -0.15 if k == 4 else 0.05), L, 0.32 if k < 4 else 0.5, sides=4, rings=2))
        # 巻いた角 (後ろへ巻いて下へ)
        pts = []
        for i in range(15):
            t = i / 14
            a = math.radians(-20 + 250 * t)
            rad = 4.2 - 1.6 * t
            pts.append(P(s * (3.8 + 2.6 * t), 3.0 + rad * math.sin(a) - 0.6 * t, 1.4 + rad * (1 - math.cos(a)) * 0.9))
        m.add(f"kh_horn{s}", b.tub("root", "bone", pts, [1.5 - 1.15 * (i / 14) for i in range(15)], None, sides=7))
        for j in range(5):
            q = pts[2 + j * 2]
            m.add(f"kh_hr{s}{j}", b.ell("root", "bone", q, (1.65 - 0.2 * j, 0.35, 1.65 - 0.2 * j), segs=8, rings=1))
    # 顎先と下の前歯
    m.add("kh_chin", b.ell("root", "bone", P(0, -6.1, -8.6), (1.7, 0.85, 1.1), segs=8, rings=2))
    for k, x in enumerate((-0.6, 0.0, 0.6)):
        m.add(f"kh_inc{k}", b.cone("root", "salt", P(x, -5.4, -9.3), (0, 1, -0.2), 1.1, 0.28, sides=4, rings=1))
        m.add(f"kh_uinc{k}", b.cone("root", "salt", P(x, -2.3, -10.6), (0, -1, -0.2), 1.2, 0.3, sides=4, rings=1))
    # 頭頂の棘 (骨の冠)
    for k in range(6):
        z = -1.6 + k * 1.3
        m.add(f"kh_crest{k}", b.cone("root", "bone", P(0, 4.6 - 0.25 * abs(k - 2), z), (0, 1, 0.35 + 0.08 * k), 4.6 - 0.5 * abs(k - 1.5), 0.62, sides=4, rings=3,
                                      bend=(0, -0.4, 0.8)))
    # 首の背骨
    for k in range(3):
        c = P(0, -2.6 - k * 1.7, 3.6 - k * 0.6)
        m.add(f"kh_vert{k}", b.ell("root", "bone", c, (2.0, 0.8, 2.0), segs=8, rings=2))
        m.add(f"kh_spin{k}", b.cone("root", "bone", c + V3([0, 0.2, 1.2]), (0, 0.3, 1), 2.0, 0.5, sides=4, rings=1))
        for s in (1, -1):
            m.add(f"kh_trans{k}{s}", b.cone("root", "bone", c + V3([s * 1.4, 0, 0]), (s, -0.2, 0.2), 1.4, 0.35, sides=4, rings=1))
    m.add("kh_neck", b.tub("root", "bone", [P(0, -7.5, 1.6), P(0, -2.0, 3.2)], [1.2, 1.1], None, sides=6))


# ------------------------------------------------------------------ 塩の王
def salt_head(m, R):
    b = _env()
    H = anchor(R)
    base = H + V3([0, -3.4, 0])
    rng = np.random.default_rng(31)
    # 首もとの塩の殻
    m.add("ks_crust", b.ell("root", "salt", base + V3([0, -0.6, 0]), (4.6, 2.0, 4.6), segs=12, rings=3))
    for k in range(8):
        a = k / 8 * math.tau + 0.3
        m.add(f"ks_lump{k}", b.ell("root", "salt", base + V3([math.cos(a) * 3.8, -0.3 + rng.uniform(-0.4, 0.4), math.sin(a) * 3.8]),
                                    (1.5, 1.0, 1.5), segs=6, rings=2))
    # 光る芯 (晶のすき間から見える)
    m.add("ks_core", b.ell("root", "glow", base + V3([0, 3.6, 0]), (2.4, 3.0, 2.4), segs=10, rings=4))
    # 真ん中の大きな晶
    m.add("ks_spire", crystal(b, m, "salt", base + V3([0, 1.0, 0.3]), (0.02, 1, 0.08), 16.5, 1.9))
    # 冠の輪: 高い晶 8 本 (正面は低く、芯が見える)
    for k in range(8):
        a = k / 8 * math.tau + math.pi / 8
        front = math.sin(a) < -0.5
        L = (5.5 if front else (12.0 if k % 2 else 9.0))
        lean = 0.42 if not front else 0.7
        m.add(f"ks_tall{k}", crystal(b, m, "salt", base + V3([math.cos(a) * 2.4, 0.6, math.sin(a) * 2.4]), (math.cos(a) * lean, 1, math.sin(a) * lean), L,
                                     1.15 if not front else 0.9))
    # 外へ開く中くらいの晶
    for k in range(10):
        a = k / 10 * math.tau
        m.add(f"ks_mid{k}", crystal(b, m, "salt", base + V3([math.cos(a) * 3.6, 0.0, math.sin(a) * 3.6]),
                                    (math.cos(a), 0.7 + rng.uniform(-0.2, 0.3), math.sin(a)), rng.uniform(3.6, 6.0), rng.uniform(0.6, 0.85)))
    # 光る細い晶 (内側)
    for k in range(4):
        a = k / 4 * math.tau + 0.6
        m.add(f"ks_gl{k}", crystal(b, m, "glow", base + V3([math.cos(a) * 1.2, 2.0, math.sin(a) * 1.2]), (math.cos(a) * 0.2, 1, math.sin(a) * 0.2), 9.5, 0.45))
    # 殻に生えた小さな晶
    for k in range(18):
        a = rng.uniform(0, math.tau)
        rr = rng.uniform(3.2, 4.6)
        p = base + V3([math.cos(a) * rr, rng.uniform(-1.2, 0.6), math.sin(a) * rr])
        m.add(f"ks_tiny{k}", crystal(b, m, "salt", p, (math.cos(a), rng.uniform(0.2, 1.0), math.sin(a)), rng.uniform(1.4, 2.6), rng.uniform(0.3, 0.45)))


# ------------------------------------------------------------------ 雷の王
def storm_head(m, R):
    b = _env()
    H = anchor(R)
    P = lambda x, y, z: H + V3([x, y, z])  # noqa: E731
    m.add("kt_neck", b.tub("root", "iron", [P(0, -8.0, 0.6), P(0, -4.0, 0.5)], [2.6, 2.6], None, sides=8))
    # 兜 (角の丸い箱)
    m.add("kt_helm", b.limb("root", "steel", [P(0, -4.6, 0.4), P(0, 0.0, 0.2), P(0, 4.0, 0.6)], [8.4, 9.2, 8.6], [8.4, 9.6, 8.8], n=16, p=3.0))
    m.add("kt_top", b.ell("root", "steel", P(0, 4.0, 0.6), (4.2, 1.5, 4.3), segs=12, rings=3))
    # 正面の稜と覗き窓 (細く光る)、通気の穴
    m.add("kt_keel", b.box("root", "steel", P(0, -0.6, -4.55), (1.0, 8.2, 1.1)))
    m.add("kt_slit", b.box("root", "glow", P(0, 1.4, -4.5), (7.2, 0.45, 0.5)))
    m.add("kt_slit_u", b.box("root", "steel", P(0, 2.05, -4.7), (7.6, 0.6, 0.7)))
    for s in (1, -1):
        for j, y in enumerate((-0.8, -1.9, -3.0)):
            zf = 0.2 - 4.8 + (abs(y) / 4.6) * 0.6
            for i, x in enumerate((1.5, 2.6)):
                m.add(f"kt_hole{s}{j}{i}", b.box("root", "void", P(s * x, y, zf - 0.02), (0.55, 0.5, 0.3)))
        # 横の銅の管 (頬から首の後ろへ)
        m.add(f"kt_cable{s}", b.tub("root", "copper", [P(s * 4.5, -0.6, 1.6), P(s * 5.6, -3.2, 2.8), P(s * 3.0, -6.8, 3.4)], [0.5, 0.5, 0.45], None, sides=6))
        m.add(f"kt_boss{s}", b.ell("root", "copper", P(s * 4.6, -0.6, 1.6), (0.6, 1.1, 1.1), segs=8, rings=2))
    # 銅の帯と鋲
    for j, y in enumerate((-3.8, 3.2)):
        a, d = (4.45, 4.45) if j == 0 else (4.55, 4.65)
        ring = sring(P(0, y, 0.45), a, d, n=24)
        m.add(f"kt_band{j}", b.tub("root", "copper", ring, [0.42] * len(ring), None, sides=5))
        for k in range(0, 24, 2):
            q = ring[k]
            o = nrm((q - P(0, y, 0.45)) * V3([1, 0, 1]))
            m.add(f"kt_rv{j}{k}", b.ell("root", "copper", q + o * 0.35, (0.32, 0.32, 0.32), segs=5, rings=2))
    # 碍子と巻線と避雷針
    m.add("kt_rod", b.tub("root", "steel", [P(0, 4.6, 0.6), P(0, 18.0, 0.6)], [0.6, 0.5], None, sides=6))
    for k in range(5):
        r = 2.1 - k * 0.18
        m.add(f"kt_ins{k}", b.ell("root", "salt", P(0, 6.4 + k * 1.35, 0.6), (r, 0.42, r), segs=10, rings=2))
    hel = [P(math.cos(t) * 1.15, 13.0 + t / (6 * math.pi) * 3.6, 0.6 + math.sin(t) * 1.15) for t in np.linspace(0, 6 * math.pi, 37)]
    m.add("kt_coil", b.tub("root", "copper", hel, [0.24] * len(hel), None, sides=4))
    m.add("kt_needle", b.cone("root", "steel", P(0, 17.6, 0.6), (0, 1, 0), 5.0, 0.55, sides=6, rings=2))
    m.add("kt_orb", b.ell("root", "glow", P(0, 23.4, 0.6), (1.3, 1.3, 1.3), segs=8, rings=4))
    # 冠の角: 兜の上の縁から外へ開く銅の針、先が光る
    for k in range(6):
        a = k / 6 * math.tau + math.pi / 6
        q = P(math.cos(a) * 3.4, 3.8, 0.6 + math.sin(a) * 3.4)
        d = nrm(V3([math.cos(a) * 0.55, 1, math.sin(a) * 0.55]))
        L = 5.4 if math.sin(a) > -0.6 else 3.6
        m.add(f"kt_prong{k}", b.cone("root", "copper", q, d, L, 0.5, sides=5, rings=2))
        m.add(f"kt_ptip{k}", b.cone("root", "glow", q + d * (L * 0.62), d, L * 0.5, 0.3, sides=5, rings=1))


# ------------------------------------------------------------------ 灰の王
def ash_head(m, R):
    b = _env()
    H = anchor(R)
    P = lambda x, y, z: H + V3([x, y, z])  # noqa: E731
    m.add("ka_neck", b.tub("root", "iron", [P(0, -8.0, 0.6), P(0, -3.6, 0.6)], [2.8, 2.8], None, sides=8))
    # 鋳鉄の釜
    m.add("ka_pot", b.limb("root", "iron", [P(0, -4.0, 0.6), P(0, -0.2, 0.5), P(0, 3.4, 0.7)], [8.0, 10.0, 9.0], [8.0, 9.6, 8.6], n=16, p=2.4))
    m.add("ka_lid", b.ell("root", "iron", P(0, 3.4, 0.7), (4.4, 1.7, 4.2), segs=12, rings=3))
    for j, (y, a, d) in enumerate(((-3.5, 4.2, 4.2), (-0.2, 5.15, 4.95), (3.0, 4.65, 4.45))):
        ring = sring(P(0, y, 0.55), a, d, n=24, p=2.4)
        m.add(f"ka_band{j}", b.tub("root", "iron", ring, [0.5] * len(ring), None, sides=5))
        if j == 1:
            for k in range(1, 24, 2):
                q = ring[k]
                o = nrm((q - P(0, y, 0.55)) * V3([1, 0, 1]))
                m.add(f"ka_rv{k}", b.ell("root", "bronze", q + o * 0.4, (0.34, 0.34, 0.34), segs=5, rings=2))
    # 焚き口: 窪み・燠・アーチの枠・格子
    zf = 0.5 - 4.8
    m.add("ka_hole", b.ell("root", "void", P(0, -1.4, zf + 0.25), (2.9, 2.2, 0.75), segs=10, rings=3))
    m.add("ka_ember", b.ell("root", "glow", P(0, -1.6, zf - 0.2), (2.5, 1.9, 0.45), segs=10, rings=3))
    arch = [P(-2.9, -3.6, zf - 0.25)] + [P(-2.9 * math.cos(t), -1.4 + 2.5 * math.sin(t), zf - 0.25) for t in np.linspace(0, math.pi, 9)] + [P(2.9, -3.6, zf - 0.25),
                                                                                                                                     P(-2.9, -3.6, zf - 0.25)]
    m.add("ka_frame", b.tub("root", "iron", arch, [0.5] * len(arch), None, sides=5))
    for k, x in enumerate((-1.8, -0.9, 0.0, 0.9, 1.8)):
        top = -1.4 + 2.5 * math.sqrt(max(0.0, 1 - (x / 2.9) ** 2))
        m.add(f"ka_bar{k}", b.tub("root", "iron", [P(x, -3.6, zf - 0.45), P(x, top, zf - 0.45)], [0.26, 0.26], None, sides=5))
    m.add("ka_xbar", b.tub("root", "iron", [P(-2.8, -1.9, zf - 0.55), P(2.8, -1.9, zf - 0.55)], [0.24, 0.24], None, sides=5))
    m.add("ka_sill", b.box("root", "iron", P(0, -3.9, zf - 0.3), (7.0, 0.6, 1.4)))
    # 横の通気口と庇
    for s in (1, -1):
        for j, y in enumerate((0.9, 2.0)):
            m.add(f"ka_vent{s}{j}", b.box("root", "glow", P(s * 4.85, y, 0.6), (0.4, 0.34, 2.4)))
            m.add(f"ka_hood{s}{j}", b.box("root", "iron", P(s * 5.05, y + 0.42, 0.6), (0.9, 0.24, 2.8)))
    # 三本の煙突 (縁・中ほどのつば・中の火・煤の垂れ)
    for k, (x, z, h, lean) in enumerate(((-2.7, 1.6, 7.5, -0.18), (0.0, 2.3, 11.5, 0.0), (2.7, 1.6, 8.8, 0.18))):
        b0 = P(x, 3.0, z)
        top = b0 + V3([lean * h, h, 0.15 * h])
        m.add(f"ka_stack{k}", b.tub("root", "iron", [b0, top], [1.15, 1.0], None, sides=8))
        mid = b0 + (top - b0) * 0.45
        m.add(f"ka_flange{k}", b.ell("root", "iron", mid, (1.55, 0.32, 1.55), segs=10, rings=1))
        m.add(f"ka_rim{k}", b.tub("root", "iron", [top - V3([0, 0.5, 0]), top + V3([0, 0.35, 0])], [1.45, 1.45], None, sides=8))
        m.add(f"ka_fire{k}", b.ell("root", "glow", top + V3([0, 0.38, 0]), (1.05, 0.25, 1.05), segs=8, rings=1))
        for j in range(3):
            a = j / 3 * math.tau + k
            q = top + V3([math.cos(a) * 1.4, -0.3, math.sin(a) * 1.4])
            m.add(f"ka_soot{k}{j}", b.cone("root", "char", q, (0, -1, 0), 1.6 + 0.8 * (j % 2), 0.38, sides=4, rings=1))


# ------------------------------------------------------------------ 影の王
def shadow_head(m, R):
    b = _env()
    H = anchor(R)
    P = lambda x, y, z: H + V3([x, y, z])  # noqa: E731
    # 頭巾
    m.add("kw_hood", b.limb("root", "void", [P(0, -0.8, 5.4), P(0, 0.6, 3.4), P(0, 1.2, 0.4), P(0, 1.3, -2.0), P(0, 0.9, -3.0)],
                            [4.0, 8.8, 10.4, 10.2, 9.6], [5.0, 10.4, 12.2, 11.8, 11.2], n=16))
    m.add("kw_peak", b.cone("root", "void", P(0, 5.0, 1.0), (0, 0.5, 1), 7.0, 2.6, sides=6, rings=5, bend=(0, -3.0, 1.0)))
    for s_ in (1, -1):
        m.add(f"kw_fold{s_}", b.tub("root", "void", [P(s_ * 4.9, 3.0, -2.6), P(s_ * 5.3, 0.4, 0.0), P(s_ * 4.6, -2.6, 2.8)], [0.7, 0.8, 0.5], None, sides=5))
    rim = [P(4.6 * math.sin(t), 0.6 + 5.6 * math.cos(t), -3.15) for t in np.linspace(0, math.tau, 25)]
    m.add("kw_rim", b.tub("root", "violet", rim, [0.42] * len(rim), None, sides=5))
    m.add("kw_dark", b.ell("root", "void", P(0, 0.4, -3.1), (4.1, 5.1, 0.35), segs=12, rings=2))
    # 目のない白い面 (長いしずくの形) と、光る割れ目
    m.add("kw_mask", b.ell("root", "salt", P(0, 0.6, -3.5), (2.8, 4.0, 0.9), segs=12, rings=5))
    m.add("kw_chin", b.ell("root", "salt", P(0, -2.2, -3.45), (1.9, 2.2, 0.75), segs=10, rings=4))

    def mz(x, y):
        f1 = 1 - (x / 2.8) ** 2 - ((y - 0.6) / 4.0) ** 2
        f2 = 1 - (x / 1.9) ** 2 - ((y + 2.2) / 2.2) ** 2
        return min(-3.5 - 0.9 * math.sqrt(max(0.0, f1)), -3.45 - 0.75 * math.sqrt(max(0.0, f2))) - 0.06

    crack = [(0.35, 4.3), (-0.35, 3.1), (0.5, 2.0), (-0.15, 0.9), (0.3, -0.2), (-0.1, -1.0)]
    m.add("kw_crack", b.tub("root", "glow", [P(x, y, mz(x, y)) for x, y in crack], [0.16, 0.2, 0.18, 0.15, 0.12, 0.06], None, sides=4))
    m.add("kw_crack2", b.tub("root", "glow", [P(0.5, 2.0, mz(0.5, 2.0)), P(1.3, 1.5, mz(1.3, 1.5)), P(1.7, 0.6, mz(1.7, 0.6))], [0.13, 0.1, 0.05], None, sides=4))
    # 背の黒い刃の後光と銀の輪
    for k in range(13):
        a = math.radians(-105 + k * 210 / 12)
        q = P(math.sin(a) * 4.2, 0.8 + math.cos(a) * 4.2, 3.4)
        d = V3([math.sin(a), math.cos(a), 0.25])
        L = 9.0 if k % 2 == 0 else 6.0
        m.add(f"kw_blade{k}", b.cone("root", "void", q, d, L, 0.75, sides=3, rings=2, rb=0.25))
    ring = [P(6.4 * math.sin(t), 0.8 + 6.4 * math.cos(t), 3.8) for t in np.linspace(0, math.tau, 33)]
    m.add("kw_halo", b.tub("root", "silver", ring, [0.3] * len(ring), None, sides=5))
