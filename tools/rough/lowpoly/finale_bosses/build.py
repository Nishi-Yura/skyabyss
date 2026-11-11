# -*- coding: utf-8 -*-
"""終章の 8 体の作り込みのラフ (2026-11-11 の声「お願いおすすめから」。最初の目的の一つ目「部品数と、歯や骨のような細かい形」)。

今の模型に、そのボスのためだけに考えた細かい部品を足す三案 (案どうし・ボスどうしで同じ足し方はしない)。
部品は付ける骨の名前で作る (入れるときにその骨へ付く)。色はそのボスの今の絵に合わせた段。

    python -X utf8 -I tools/rough/lowpoly/finale_bosses/build.py [名前 ...]
出力: docs/review/finale_bosses/<名前>.png
"""
from __future__ import annotations

import importlib.util
import math
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[4]
spec = importlib.util.spec_from_file_location("bs", ROOT / "tools/rough/lowpoly/boss_split/build.py")
bs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bs)
b = bs.blp
from PIL import Image, ImageDraw  # noqa: E402

V3 = np.array
OUT = ROOT / "docs/review/finale_bosses"

# 終章の色の段 (切り絵の黒・紙の白・薄い桃の光。初代の空見だけは灰の衣と青緑)
bs.PAL.update({
    "ink": [(6, 2, 10), (16, 8, 22), (30, 18, 38), (50, 36, 58)],
    "paper": [(120, 122, 134), (170, 174, 186), (212, 218, 222), (238, 244, 244), (252, 254, 254)],
    "brass": [(70, 50, 22), (120, 90, 40), (176, 136, 64), (220, 186, 110)],
    "robe": [(70, 74, 80), (110, 114, 122), (150, 154, 162), (190, 194, 200)],
    "teal": [(20, 60, 60), (40, 100, 96), (70, 150, 140), (130, 200, 190)],
    "leather": [(30, 18, 12), (58, 36, 22), (90, 58, 36), (124, 86, 56)],
    "wood": [(40, 26, 16), (70, 46, 28), (104, 72, 44), (140, 104, 66)],
    "g_pink": [(230, 120, 190), (255, 170, 220), (255, 230, 248)],
    "g_teal": [(40, 190, 170), (110, 240, 220), (200, 255, 245)],
})


def nrm(v):
    v = np.asarray(v, float)
    return v / max(1e-9, np.linalg.norm(v))


def basis(nv):
    n = nrm(nv)
    a = nrm(np.cross(n, (0, 1, 0) if abs(n[1]) < 0.9 else (1, 0, 0)))
    return a, np.cross(n, a)


def edge_points(R, bone, n, low=True):
    V = np.concatenate([f.verts for f in R.faces if f.bone == bone])
    xs = np.linspace(V[:, 0].min(), V[:, 0].max(), n + 1)
    out = []
    for i in range(n):
        sel = V[(V[:, 0] >= xs[i]) & (V[:, 0] <= xs[i + 1])]
        if len(sel):
            out.append(sel[np.argmin(sel[:, 1])] if low else sel[np.argmax(sel[:, 1])])
    return out


def ring(bone, mat, c, r, normal, th, n=18):
    a, c2 = basis(normal)
    pts = [np.asarray(c, float) + (a * math.cos(t) + c2 * math.sin(t)) * r for t in np.linspace(0, math.tau, n + 1)]
    return b.tub(bone, mat, pts, [th] * len(pts), None, sides=5)


def chain(m, key, bone, mat, p0, p1, link=1.4, th=0.22):
    p0, p1 = np.asarray(p0, float), np.asarray(p1, float)
    d = p1 - p0
    L = np.linalg.norm(d)
    k = max(2, int(L / (link * 0.8)))
    t = nrm(d)
    a, c2 = basis(t)
    for i in range(k):
        c = p0 + d * ((i + 0.5) / k)
        side = a if i % 2 == 0 else c2
        pts = [c + t * math.cos(u) * link * 0.55 + side * math.sin(u) * link * 0.3 for u in np.linspace(0, math.tau, 11)]
        m.add(f"{key}{i}", b.tub(bone, mat, pts, [th] * len(pts), None, sides=4))


def bell(m, key, bone, mat, top, s, down=(0, -1, 0), clapper="ink"):
    """小さな鐘 (口は下。中に舌)。"""
    dn = nrm(down)
    a, c2 = basis(dn)
    top = np.asarray(top, float)
    prof = [(0.0, 0.32), (0.25, 0.42), (0.55, 0.52), (0.85, 0.78), (1.0, 0.86)]
    rings = []
    for h, r in prof:
        c = top + dn * h * s
        rings.append([c + (a * math.cos(u) + c2 * math.sin(u)) * r * s for u in np.linspace(0, math.tau, 10, endpoint=False)])
    m.add(key, b.lof(bone, mat, rings))
    m.add(key + "_cl", b.ell(bone, clapper, top + dn * s * 0.95, (0.18 * s, 0.22 * s, 0.18 * s), segs=6, rings=2))
    m.add(key + "_lp", ring(bone, mat, top - dn * 0.12 * s, 0.16 * s, a, 0.06 * s, n=8))


def feather(m, key, bone, mat, base, d, L, w, flat_n, shaft="paper"):
    """羽根一枚: 葉の形の薄い板と、軸。"""
    base = np.asarray(base, float)
    d = nrm(d)
    side = nrm(np.cross(flat_n, d))
    poly = [base, base + d * L * 0.3 + side * w * 0.5, base + d * L * 0.75 + side * w * 0.4, base + d * L,
            base + d * L * 0.75 - side * w * 0.45, base + d * L * 0.3 - side * w * 0.5]
    m.add(key, b.slab(bone, mat, poly, flat_n, 0.22))
    m.add(key + "_q", b.tub(bone, shaft, [base - d * 0.4, base + d * L * 0.9], [0.14, 0.06], None, sides=4))


# ---------------------------------------------------------------- 初代の空見 (人の大きさ。衣・尖った頭巾・杖の輪・腰の鈴・肩掛け)
def fo_a(m, R):
    """A 星図の外套: 肩掛けに星の飾りが糸で下がり、杖の先は入れ子の三つの輪 (天球儀)"""
    lo, hi, c = R.box("shawl")
    rng = np.random.default_rng(1)
    for k in range(16):
        x = rng.uniform(lo[0] + 0.5, hi[0] - 0.5)
        y = rng.uniform(lo[1] + 6, hi[1] - 2)
        z = lo[2] - 0.2 if k % 2 else hi[2] + 0.2
        L = rng.uniform(1.2, 2.6)
        m.add(f"st_t{k}", b.tub("shawl", "brass", [V3([x, y, z]), V3([x, y - L, z])], [0.06, 0.06], None, sides=3))
        m.add(f"st_s{k}", b.cone("shawl", "brass", V3([x, y - L - 0.4, z]), (0, 1, 0), 0.8, 0.35, sides=5, rings=1))
        m.add(f"st_s2{k}", b.cone("shawl", "brass", V3([x, y - L - 0.4, z]), (0, -1, 0), 0.8, 0.35, sides=5, rings=1))
    plo, phi, pc = R.box("pole")
    top = V3([pc[0], phi[1] - 3.5, pc[2]])
    for i, (r, nv) in enumerate(((3.2, (1, 0, 0)), (2.7, (0, 0, 1)), (2.2, (1, 0, 1)))):
        m.add(f"as_r{i}", ring("pole", "brass", top, r, nv, 0.16))
    m.add("as_core", b.ell("pole", "glow", top, (0.7, 0.7, 0.7), segs=8, rings=3))
    hlo, hhi, hc = R.box("head")
    m.add("hood_trim", ring("head", "brass", V3([hc[0], hlo[1] + 7.5, hc[2] - 0.6]), 2.9, (0, 1, 0.15), 0.2))


def fo_b(m, R):
    """B 鐘の舌の杖: 杖の先に大きな鐘の舌が鎖で下がり、腰には舌の付いた小鐘が五つ"""
    plo, phi, pc = R.box("pole")
    top = V3([pc[0], phi[1] - 1.5, pc[2]])
    m.add("cl_hook", b.tub("pole", "ink", [top, top + V3([1.6, 0.8, 0]), top + V3([2.4, -0.5, 0])], [0.25, 0.2, 0.18], None, sides=5))
    chain(m, "cl_ch", "pole", "ink", top + V3([2.4, -0.5, 0]), top + V3([2.4, -5.5, 0]), link=0.9, th=0.12)
    m.add("cl_tongue", b.tub("pole", "brass", [top + V3([2.4, -5.6, 0]), top + V3([2.4, -8.5, 0]), top + V3([2.4, -9.6, 0])], [0.3, 0.45, 0.9], None, sides=8))
    m.add("cl_ball", b.ell("pole", "brass", top + V3([2.4, -10.2, 0]), (1.1, 1.1, 1.1), segs=8, rings=3))
    blo, bhi, bc = R.box("hipbell")
    for k in range(5):
        a = math.radians(-80 + k * 40)
        p = V3([bc[0] + math.sin(a) * 2.5, bhi[1] - 0.6 - (k % 2) * 1.2, bc[2] - math.cos(a) * 2.5])
        m.add(f"hb_t{k}", b.tub("body", "ink", [p + V3([0, 1.2, 0]), p], [0.08, 0.08], None, sides=3))
        bell(m, f"hb_b{k}", "body", "brass", p, 1.3)
    hlo, hhi, hc = R.box("head")
    m.add("hood_lamp", b.ell("head", "glow", V3([hc[0], hhi[1] - 9, hlo[2] + 0.4]), (0.9, 1.2, 0.5), segs=8, rings=3))


def fo_c(m, R):
    """C 骨の空見: 裂けた衣の胸から肋が覗き、手は指の骨、頭巾の下に顎の骨、杖から背骨の数珠"""
    lo, hi, c = R.box("body")
    zf = lo[2] - 0.1
    for k in range(5):
        y = hi[1] - 6 - k * 1.5
        for s in (1, -1):
            pts = [V3([s * 0.4, y, zf + 0.3]), V3([s * 2.0, y - 0.3, zf - 0.2]), V3([s * 3.6, y - 1.0, zf + 0.6])]
            m.add(f"rb{k}{s}", b.tub("body", "paper", pts, [0.3, 0.32, 0.22], None, sides=5))
    m.add("sternum", b.tub("body", "paper", [V3([0, hi[1] - 5, zf + 0.1]), V3([0, hi[1] - 13, zf + 0.1])], [0.35, 0.3], None, sides=5))
    m.add("tear", b.ell("body", "ink", V3([0, hi[1] - 9, zf + 0.5]), (4.2, 4.5, 0.4), segs=10, rings=3))
    for s, arm in ((1, "arm_r"), (-1, "arm_l")):
        alo, ahi, ac = R.box(arm)
        for j in range(4):
            x0 = ac[0] + s * 0.2 + (j - 1.5) * 0.45
            m.add(f"fg{arm}{j}", b.tub(arm, "paper", [V3([x0, alo[1] + 0.8, ac[2] - 0.6]), V3([x0, alo[1] - 1.0, ac[2] - 1.0]),
                                                       V3([x0, alo[1] - 2.2, ac[2] - 0.5])], [0.18, 0.15, 0.1], None, sides=4))
    hlo, hhi, hc = R.box("head")
    jaw = V3([hc[0], hlo[1] + 4.2, hlo[2] + 0.8])
    m.add("jaw", b.tub("head", "paper", [jaw + V3([-1.6, 0.8, 0.8]), jaw + V3([-0.8, -0.2, 0]), jaw + V3([0.8, -0.2, 0]), jaw + V3([1.6, 0.8, 0.8])],
                       [0.3, 0.32, 0.32, 0.3], None, sides=5))
    for k in range(5):
        m.add(f"jt{k}", b.cone("head", "paper", jaw + V3([-1.0 + k * 0.5, 0.0, -0.1]), (0, 1, 0), 0.7, 0.13, sides=4, rings=1))
    plo, phi, pc = R.box("pole")
    for k in range(9):
        p = V3([pc[0] + 1.0, phi[1] - 6 - k * 1.1, pc[2]])
        m.add(f"vt{k}", b.ell("pole", "paper", p, (0.55, 0.38, 0.55), segs=7, rings=2))
        m.add(f"vs{k}", b.cone("pole", "paper", p, (1, 0, 0), 0.8, 0.18, sides=4, rings=1))


# ---------------------------------------------------------------- 最後の鐘守 (背に大鐘・杖・頭巾)
def lb_a(m, R):
    """A 割れた大鐘と縄: 背の鐘に黒い割れ目、口の中に大きな舌、胸を交差する縄で背負う"""
    lo, hi, c = R.box("bell")
    rng = np.random.default_rng(4)
    for k in range(5):
        a = rng.uniform(-1.2, 1.2) + math.pi / 2
        p0 = V3([c[0] + math.cos(a) * 8.0, hi[1] - 6, c[2] + math.sin(a) * 8.0])
        pts = [p0]
        for j in range(4):
            pts.append(pts[-1] + V3([rng.uniform(-1, 1), -3.0, rng.uniform(-0.5, 0.5)]))
        m.add(f"crk{k}", b.tub("bell", "ink", pts, [0.35, 0.3, 0.25, 0.18, 0.1], None, sides=4))
    m.add("tongue", b.tub("bell", "brass", [V3([c[0], hi[1] - 4, c[2]]), V3([c[0], lo[1] + 6, c[2]])], [0.6, 0.6], None, sides=6))
    m.add("tongue_b", b.ell("bell", "brass", V3([c[0], lo[1] + 4.5, c[2]]), (2.2, 2.6, 2.2), segs=10, rings=4))
    blo, bhi, bc = R.box("body")
    for s in (1, -1):
        pts = [V3([s * 7, bhi[1] - 12, bc[2] + 5]), V3([s * 6, bhi[1] - 14, blo[2] - 0.2]), V3([-s * 4, bhi[1] - 26, blo[2] - 0.3]),
               V3([-s * 7, bhi[1] - 30, bc[2] + 5])]
        m.add(f"rope{s}", b.tub("body", "wood", pts, [0.7] * 4, None, sides=6))
    m.add("knot", b.ell("body", "wood", V3([0, bhi[1] - 21, blo[2] - 0.6]), (1.6, 1.4, 1.0), segs=8, rings=3))
    for k in range(10):
        a = k / 10 * math.tau
        m.add(f"rv{k}", b.ell("bell", "brass", V3([c[0] + math.cos(a) * 9.6, lo[1] + 3, c[2] + math.sin(a) * 9.6]), (0.5, 0.5, 0.5), segs=5, rings=2))


def lb_b(m, R):
    """B 小鐘の衣: 衣の裾と肩から、舌の付いた小鐘がいくつも下がる (動くたびに鳴る姿)"""
    lo, hi, c = R.box("body")
    for k in range(14):
        a = k / 14 * math.tau
        p = V3([c[0] + math.cos(a) * 9.4, lo[1] + 4.5 + (k % 2) * 1.0, c[2] + math.sin(a) * 6.8])
        m.add(f"ht{k}", b.tub("body", "ink", [p + V3([0, 1.6, 0]), p], [0.1, 0.1], None, sides=3))
        bell(m, f"hb{k}", "body", "brass", p, 1.6)
    for s, arm in ((1, "arm_r"), (-1, "arm_l")):
        alo, ahi, ac = R.box(arm)
        for j in range(4):
            p = V3([ac[0] + s * 1.3, ahi[1] - 4 - j * 3.0, ac[2] + (j - 1.5) * 1.0])
            m.add(f"sb{arm}{j}", b.tub(arm, "ink", [p + V3([-s * 0.8, 1.2, 0]), p], [0.08, 0.08], None, sides=3))
            bell(m, f"sbb{arm}{j}", arm, "brass", p, 1.2)


def lb_c(m, R):
    """C 鐘楼を背負う: 背の鐘を木の梁の枠で吊り、滑車と綱。頭巾は鐘の形の兜"""
    lo, hi, c = R.box("bell")
    for s in (1, -1):
        m.add(f"post{s}", b.box("bell", "wood", V3([c[0] + s * 10.5, (lo[1] + hi[1]) / 2 + 2, c[2]]), (1.4, hi[1] - lo[1] + 8, 1.4)))
    m.add("beam", b.box("bell", "wood", V3([c[0], hi[1] + 5, c[2]]), (23, 1.6, 1.8)))
    m.add("beam2", b.box("bell", "wood", V3([c[0], hi[1] + 6.4, c[2]]), (16, 1.0, 1.4)))
    m.add("pulley", ring("bell", "brass", V3([c[0] + 7, hi[1] + 3.2, c[2] - 1.2]), 1.4, (0, 0, 1), 0.35))
    m.add("axle", b.tub("bell", "brass", [V3([c[0] + 7, hi[1] + 3.2, c[2] - 2]), V3([c[0] + 7, hi[1] + 3.2, c[2] - 0.2])], [0.3, 0.3], None, sides=6))
    m.add("rope", b.tub("bell", "wood", [V3([c[0] + 8.4, hi[1] + 3.2, c[2] - 1.2]), V3([c[0] + 8.8, lo[1] - 4, c[2] - 1.2])], [0.25, 0.25], None, sides=4))
    m.add("hang", b.tub("bell", "ink", [V3([c[0], hi[1] + 4.2, c[2]]), V3([c[0], hi[1] - 0.5, c[2]])], [0.5, 0.5], None, sides=6))
    hlo, hhi, hc = R.box("head")
    m.add("helm", b.lof("head", "ink", [[V3([hc[0], hhi[1] + 0.6 - h * 7.0, hc[2]]) + V3([math.cos(u), 0, math.sin(u)]) * r
                                            for u in np.linspace(0, math.tau, 12, endpoint=False)]
                                           for h, r in ((0, 1.2), (0.25, 2.8), (0.6, 3.6), (0.9, 4.6), (1.0, 4.9))]))
    m.add("helm_slit", b.box("head", "glow", V3([hc[0], hhi[1] - 4.8, hlo[2] - 0.4]), (5.0, 0.5, 0.6)))
    m.add("helm_rim", ring("head", "paper", V3([hc[0], hhi[1] + 0.6 - 7.0, hc[2]]), 4.9, (0, 1, 0), 0.35))


# ---------------------------------------------------------------- 灯守の目 (籠に宿る一つ目・鎧戸)
def le_a(m, R):
    """A 灯芯の睫毛: 目のまわりに長い睫毛の棘、白目に細い血管、瞳のまわりにレンズの輪"""
    lo, hi, c = R.box("eye")
    rr = (hi[0] - lo[0]) / 2
    for k in range(20):
        a = k / 20 * math.tau
        p = V3([c[0] + math.cos(a) * rr * 0.92, c[1] + math.sin(a) * rr * 0.92, lo[2] + 2.0])
        m.add(f"lash{k}", b.cone("eye", "ink", p, (math.cos(a), math.sin(a), -0.6), 3.2 + (k % 3) * 0.8, 0.22, sides=3, rings=2,
                                 bend=(math.cos(a) * 0.6, math.sin(a) * 0.6 + 0.4, 0)))
    rng = np.random.default_rng(6)
    for k in range(8):
        a = rng.uniform(0, math.tau)
        pts = []
        for j in range(4):
            r = rr * (0.9 - j * 0.15)
            pts.append(V3([c[0] + math.cos(a + j * 0.15) * r, c[1] + math.sin(a + j * 0.15) * r, lo[2] + 0.4 + (1 - (r / rr) ** 2) * -1.5]))
        m.add(f"vein{k}", b.tub("eye", "g_pink_m", pts, [0.12, 0.1, 0.08, 0.05], None, sides=3))
    for i, r in enumerate((2.6, 3.2)):
        m.add(f"lens{i}", ring("eye", "brass", V3([c[0], c[1], lo[2] - 0.4 - i * 0.3]), r, (0, 0, 1), 0.14))


def le_b(m, R):
    """B 灯台のレンズ: 目の前に重なった段のレンズの輪、籠の天井からプリズムの飾り"""
    lo, hi, c = R.box("eye")
    for i in range(6):
        m.add(f"fr{i}", ring("eye", "paper", V3([c[0], c[1], lo[2] - 1.0 - i * 0.35]), 2.0 + i * 0.7, (0, 0, 1), 0.2))
    clo, chi, cc = R.box("cage")
    for k in range(8):
        a = k / 8 * math.tau
        p = V3([cc[0] + math.cos(a) * 7, chi[1] - 12, cc[2] + math.sin(a) * 7])
        m.add(f"pt{k}", b.tub("cage", "ink", [p + V3([0, 4, 0]), p], [0.08, 0.08], None, sides=3))
        m.add(f"pr{k}", b.cone("cage", "glow", p, (0, -1, 0), 2.4, 0.5, sides=3, rings=1))
        m.add(f"pr2{k}", b.cone("cage", "glow", p + V3([0, 0.01, 0]), (0, 1, 0), 0.8, 0.5, sides=3, rings=1))


def le_c(m, R):
    """C 焼けた蛾の籠: 籠の柵に焼けて貼り付いた蛾がいくつも、底から溶けた蝋が垂れる"""
    clo, chi, cc = R.box("cage")
    rng = np.random.default_rng(8)
    r = (chi[0] - clo[0]) / 2 * 0.86
    floor_y = clo[1] + (chi[1] - clo[1]) * 0.17
    for k in range(14):
        a = rng.uniform(0, math.tau)
        y = rng.uniform(clo[1] + 10, chi[1] - 14)
        p = V3([cc[0] + math.cos(a) * r, y, cc[2] + math.sin(a) * r])
        out = V3([math.cos(a), 0, math.sin(a)])
        side = V3([-math.sin(a), 0, math.cos(a)])
        m.add(f"mb{k}", b.ell("cage", "ink", p + out * 0.3, (0.35, 0.9, 0.35), segs=6, rings=2))
        for s in (1, -1):
            poly = [p + out * 0.3, p + out * 0.3 + side * s * 1.8 + V3([0, 1.0, 0]), p + out * 0.3 + side * s * 2.0 + V3([0, -0.4, 0]),
                    p + out * 0.3 + side * s * 0.8 + V3([0, -1.2, 0])]
            m.add(f"mw{k}{s}", b.slab("cage", "paper", poly, out, 0.12))
    for k in range(10):
        a = k / 10 * math.tau + 0.2
        p = V3([cc[0] + math.cos(a) * r * 0.95, floor_y - 0.4, cc[2] + math.sin(a) * r * 0.95])
        L = 2 + (k % 3) * 1.6
        m.add(f"wax{k}", b.tub("cage", "paper", [p, p + V3([0, -L * 0.6, 0]), p + V3([0, -L, 0])], [0.4, 0.3, 0.2], None, sides=5))
        m.add(f"waxd{k}", b.ell("cage", "paper", p + V3([0, -L - 0.3, 0]), (0.3, 0.42, 0.3), segs=6, rings=2))


# ---------------------------------------------------------------- 戻し手 (歯車を負う鍛冶頭・槌・火箸)
def rw_a(m, R):
    """A 噛み合う歯車: 背の輪に本物の歯、横に小さな歯車が二つ噛み合い、軸に鋲"""
    lo, hi, c = R.box("wheel")
    rr = (hi[0] - lo[0]) / 2
    for k in range(24):
        a = k / 24 * math.tau
        m.add(f"tooth{k}", b.box("wheel", "brass", V3([c[0] + math.cos(a) * (rr + 0.6), c[1] + math.sin(a) * (rr + 0.6), c[2]]), (1.1, 1.1, 1.4),
                                 R=b.R_(0, 0, math.degrees(a))))
    for i, (dx, dy, r2) in enumerate(((rr + 3.2, -4.0, 3.2), (-(rr + 2.4), 3.0, 2.4))):
        cc = V3([c[0] + dx, c[1] + dy, c[2] + 0.4])
        m.add(f"g{i}", ring("wheel", "brass", cc, r2, (0, 0, 1), 0.5))
        m.add(f"gh{i}", b.ell("wheel", "ink", cc, (0.8, 0.8, 0.8), segs=8, rings=2))
        for k in range(10):
            a = k / 10 * math.tau
            m.add(f"gt{i}{k}", b.box("wheel", "brass", cc + V3([math.cos(a) * (r2 + 0.5), math.sin(a) * (r2 + 0.5), 0]), (0.8, 0.8, 1.0),
                                     R=b.R_(0, 0, math.degrees(a))))
        for k in range(3):
            a = k / 3 * math.tau
            m.add(f"gs{i}{k}", b.tub("wheel", "brass", [cc, cc + V3([math.cos(a) * r2, math.sin(a) * r2, 0])], [0.3, 0.3], None, sides=4))
    m.add("axle", b.ell("wheel", "ink", V3([c[0], c[1], c[2] - 0.6]), (1.6, 1.6, 1.0), segs=10, rings=3))


def rw_b(m, R):
    """B 振り子と錘: 背から長い振り子、腰から鎖で時計の錘が二つ、頭の後ろに時計の針の後光"""
    lo, hi, c = R.box("body")
    p0 = V3([0, hi[1] - 8, hi[2] + 1.6])
    m.add("pend_rod", b.tub("body", "brass", [p0, p0 + V3([0, -24, 1.0])], [0.3, 0.3], None, sides=6))
    m.add("pend_bob", b.ell("body", "brass", p0 + V3([0, -25.5, 1.0]), (2.6, 2.6, 0.7), segs=12, rings=3))
    m.add("pend_glow", b.ell("body", "glow", p0 + V3([0, -25.5, 0.2]), (1.2, 1.2, 0.3), segs=8, rings=2))
    for s in (1, -1):
        top = V3([s * 3.5, lo[1] + 22, lo[2] - 0.5])
        chain(m, f"wch{s}", "body", "ink", top, top + V3([0, -8, 0]), link=1.0, th=0.14)
        m.add(f"wt{s}", b.tub("body", "brass", [top + V3([0, -8.4, 0]), top + V3([0, -12.4, 0])], [0.9, 0.9], None, sides=8))
        m.add(f"wtc{s}", b.cone("body", "brass", top + V3([0, -12.4, 0]), (0, -1, 0), 1.0, 0.9, sides=8, rings=1))
    hlo, hhi, hc = R.box("head")
    cc = V3([hc[0], hc[1] + 1, hhi[2] + 1.6])
    for k in range(12):
        a = k / 12 * math.tau
        L = 4.5 if k % 3 == 0 else 3.0
        m.add(f"tick{k}", b.box("head", "paper", cc + V3([math.cos(a) * 5.4, math.sin(a) * 5.4, 0]), (0.35, L * 0.4, 0.2), R=b.R_(0, 0, math.degrees(a) - 90)))
    m.add("dial", ring("head", "paper", cc, 5.4, (0, 0, 1), 0.3, n=24))
    m.add("dial2", ring("head", "ink", cc, 6.4, (0, 0, 1), 0.2, n=24))
    m.add("hand_h", b.cone("head", "ink", cc, (0.5, 1, 0), 4.0, 0.5, sides=4, rings=1))
    m.add("hand_m", b.cone("head", "ink", cc, (-1, -0.3, 0), 5.6, 0.35, sides=4, rings=1))


def rw_c(m, R):
    """C 鍛冶の革: 革の前掛けに鋲と物入れ、道具の帯 (小槌・やすり・火箸)、背にふいご"""
    lo, hi, c = R.box("body")
    zf = lo[2] - 0.3
    m.add("apron", b.slab("body", "leather", [V3([-4, hi[1] - 16, zf]), V3([4, hi[1] - 16, zf]), V3([4.6, lo[1] + 9, zf - 0.5]), V3([-4.6, lo[1] + 9, zf - 0.5])],
                          (0, 0, 1), 0.5))
    for k in range(8):
        y = hi[1] - 17 - k * 2.5
        for s in (1, -1):
            m.add(f"ar{k}{s}", b.ell("body", "brass", V3([s * 4.0, y, zf - 0.4]), (0.25, 0.25, 0.25), segs=5, rings=2))
    for s in (1, -1):
        m.add(f"pk{s}", b.box("body", "leather", V3([s * 2.0, lo[1] + 15, zf - 0.6]), (2.6, 2.8, 0.6)))
    belt_y = lo[1] + 22
    m.add("belt", b.box("body", "leather", V3([0, belt_y, c[2]]), (10.8, 1.2, 7.6)))
    for k, (x, kind) in enumerate(((-4, "hammer"), (-2, "file"), (2, "file"), (4, "hammer"))):
        p = V3([x, belt_y - 0.6, lo[2] - 0.6])
        if kind == "hammer":
            m.add(f"th{k}", b.tub("body", "wood", [p, p + V3([0, -3.2, 0])], [0.2, 0.2], None, sides=4))
            m.add(f"thh{k}", b.box("body", "ink", p + V3([0, -3.4, 0]), (1.6, 0.8, 0.8)))
        else:
            m.add(f"tf{k}", b.box("body", "ink", p + V3([0, -1.8, 0]), (0.4, 3.4, 0.25)))
    p = V3([0, hi[1] - 18, hi[2] + 1.5])
    m.add("bel_l", b.slab("body", "leather", [p + V3([-2.8, 3, 0]), p + V3([2.8, 3, 0]), p + V3([1.2, -4, 0]), p + V3([-1.2, -4, 0])], (0, 0, 1), 2.2))
    for k in range(3):
        m.add(f"bel_f{k}", b.box("body", "leather", p + V3([0, 1.6 - k * 1.8, 1.3]), (5.0 - k * 1.0, 0.3, 0.5)))
    m.add("bel_n", b.cone("body", "brass", p + V3([0, -4, 0]), (0, -1, 0.3), 2.6, 0.5, sides=6, rings=1))


# ---------------------------------------------------------------- 巡礼者の羽 (片方の翼・腕・手・後光)
def pp_a(m, R):
    """A 一枚ずつの羽根: 翼の縁に軸のある羽根が三列に重なる"""
    lo, hi, c = R.box("hand")
    rng = np.random.default_rng(12)
    pts = edge_points(R, "hand", 14) + edge_points(R, "arm", 8)
    for row in range(3):
        for k, e in enumerate(pts):
            base = e + V3([rng.uniform(-0.4, 0.4), 1.2 + row * 2.2, -0.6 - row * 0.4])
            d = nrm(V3([rng.uniform(-0.15, 0.15) - 0.2, -1.0, 0]))
            feather(m, f"fe{row}{k}", "hand", "paper" if row != 1 else "ink", base, d, 8.5 - row * 2.0 + rng.uniform(-0.8, 0.8), 2.6 - row * 0.4, (0, 0, 1))


def pp_b(m, R):
    """B 骨の翼: 翼の骨 (上腕・前腕・指の骨) が浮き出て、骨のあいだに裂けた膜"""
    lo, hi, c = R.box("arm")
    hlo, hhi, hc = R.box("hand")
    s0 = V3([hi[0] - 2, c[1] - 6, c[2] - 1.2])
    elbow = V3([c[0], hi[1] - 6, c[2] - 1.2])
    wrist = V3([hc[0] + 6, hhi[1] - 6, c[2] - 1.2])
    m.add("hum", b.tub("arm", "paper", [s0, (s0 + elbow) / 2 + V3([0, 2, 0]), elbow], [1.0, 0.7, 1.0], None, sides=7))
    m.add("rad", b.tub("arm", "paper", [elbow, (elbow + wrist) / 2 + V3([0, 1.5, 0]), wrist], [0.9, 0.6, 0.8], None, sides=7))
    for j in (s0, elbow, wrist):
        m.add(f"kn{j[0]:.0f}", b.ell("arm", "paper", j, (1.3, 1.3, 1.3), segs=8, rings=3))
    tips = []
    for k in range(4):
        tip = V3([hlo[0] + 4 + k * 3.5, hlo[1] + 4 - k * 2.5, c[2] - 1.2])
        mid = (wrist + tip) / 2 + V3([0, 2.5, 0])
        m.add(f"fb{k}", b.tub("hand", "paper", [wrist, mid, tip], [0.6, 0.45, 0.2], None, sides=5))
        m.add(f"fk{k}", b.ell("hand", "paper", mid, (0.7, 0.7, 0.7), segs=6, rings=2))
        tips.append(tip)
    for k in range(3):
        poly = [wrist + V3([0, 0, 0.3]), tips[k] + V3([0, 0, 0.3]), (tips[k] + tips[k + 1]) / 2 + V3([1.0, 3.0, 0.3]), tips[k + 1] + V3([0, 0, 0.3])]
        m.add(f"mem{k}", b.slab("hand", "ink", poly, (0, 0, 1), 0.2))


def pp_c(m, R):
    """C 札の付いた鎖: 翼から細い鎖が垂れ、先に鈴と紙の札。羽根を留める釘"""
    lo, hi, c = R.box("hand")
    for k in range(7):
        t = k / 6
        e = edge_points(R, "hand", 7)[k]
        top = e + V3([0, 1.0, -1.0])
        L = 5 + (k % 3) * 3
        chain(m, f"pc{k}_", "hand", "ink", top, top + V3([0, -L, 0]), link=1.8, th=0.25)
        if k % 2:
            bell(m, f"pcb{k}", "hand", "brass", top + V3([0, -L - 0.2, 0]), 2.8)
        else:
            m.add(f"tag{k}", b.box("hand", "paper", top + V3([0, -L - 3.0, 0]), (2.8, 5.4, 0.3)))
            m.add(f"tagi{k}", b.box("hand", "ink", top + V3([0, -L - 3.0, -0.2]), (0.8, 3.8, 0.15)))
    for k, e in enumerate(edge_points(R, "hand", 9, low=False)):
        # 羽根を留める釘は翼の上の縁に (11/11: 線の上に並べたら翼から浮いていた)
        p = e + V3([0, -1.5, -0.4])
        m.add(f"nail{k}", b.cone("hand", "ink", p + V3([0, 0, 0.6]), (0, 0, -1), 1.6, 0.25, sides=4, rings=1))
        m.add(f"nh{k}", b.ell("hand", "ink", p + V3([0, 0, -0.9]), (0.45, 0.45, 0.15), segs=6, rings=1))


# ---------------------------------------------------------------- 巡礼者の手 (大きな手・手首)
FING = ("f1", "f2", "f3", "f4", "thumb")


def ph_a(m, R):
    """A 関節の骨と爪: 指の関節に骨の節、指先に長い爪、関節にしわ"""
    for fn in FING:
        lo, hi, c = R.box(fn)
        tip = V3([c[0], hi[1], c[2]]) if fn != "thumb" else V3([lo[0], c[1] + 3, c[2]])
        d = (0, 1, -0.25) if fn != "thumb" else (-1, 0.6, -0.3)
        m.add(f"nail{fn}", b.cone(fn, "ink", tip - nrm(d) * 1.5, d, 5.5, 1.3, sides=4, rings=3, rb=0.6, bend=(0, 0, -1.4)))
        for j in range(2):
            y = lo[1] + (hi[1] - lo[1]) * (0.35 + j * 0.3)
            p = V3([c[0], y, lo[2] - 0.2]) if fn != "thumb" else V3([c[0] - j * 2.5, c[1] + j * 1.2, lo[2] - 0.2])
            m.add(f"kn{fn}{j}", b.ell(fn, "paper", p, (1.6, 0.9, 0.6), segs=8, rings=2))
            for w in range(3):
                m.add(f"wr{fn}{j}{w}", b.tub(fn, "ink", [p + V3([-1.4, 0.9 + w * 0.35, -0.3]), p + V3([1.4, 0.9 + w * 0.35, -0.3])], [0.07, 0.07], None, sides=3))


def ph_b(m, R):
    """B 巡礼の数珠: 手首に数珠が三重に巻き、指のあいだへ垂れ、薬指に輪"""
    lo, hi, c = R.box("wrist")
    for k in range(3):
        y = hi[1] - 6 - k * 2.4
        for j in range(22):
            a = j / 22 * math.tau
            m.add(f"bd{k}{j}", b.ell("wrist", "ink" if j % 7 else "brass", V3([c[0] + math.cos(a) * 8.0, y + math.sin(a * 2) * 0.4, c[2] + math.sin(a) * 8.0]),
                                     (0.6, 0.6, 0.6), segs=6, rings=2))
    plo, phi, pc = R.box("palm")
    for j in range(12):
        t = j / 11
        p = V3([pc[0] - 6 + t * 12, phi[1] - 1 - math.sin(t * math.pi) * 5, plo[2] - 0.6])
        m.add(f"dr{j}", b.ell("palm", "ink", p, (0.55, 0.55, 0.55), segs=6, rings=2))
    m.add("tassel", b.cone("palm", "brass", V3([pc[0], phi[1] - 6.6, plo[2] - 0.6]), (0, -1, 0), 3.0, 0.8, sides=6, rings=2))
    flo, fhi, fc = R.box("f3")
    m.add("ringf", ring("f3", "brass", V3([fc[0], flo[1] + 4, fc[2]]), 2.2, (0, 1, 0), 0.35))


def ph_c(m, R):
    """C 縫い合わせた手: 手の甲と指に縫い目 (糸の目)、継ぎ当ての紙片、針"""
    plo, phi, pc = R.box("palm")
    for line in range(3):
        x0 = pc[0] - 5 + line * 5
        for j in range(8):
            y = plo[1] + 2 + j * 2.2
            m.add(f"st{line}{j}", b.tub("palm", "ink", [V3([x0 - 0.7, y, plo[2] - 0.3]), V3([x0 + 0.7, y + 0.6, plo[2] - 0.3])], [0.12, 0.12], None, sides=3))
        m.add(f"seam{line}", b.tub("palm", "ink", [V3([x0, plo[1] + 1.5, plo[2] - 0.2]), V3([x0, phi[1] - 1, plo[2] - 0.2])], [0.08, 0.08], None, sides=3))
    for k, (dx, dy) in enumerate(((-3, 4), (3.5, -2), (0, 9))):
        m.add(f"patch{k}", b.box("palm", "paper", V3([pc[0] + dx, pc[1] + dy, plo[2] - 0.35]), (3.4, 3.0, 0.2), R=b.R_(0, 0, 12 * (k - 1))))
    for fn in ("f1", "f2", "f3", "f4"):
        lo, hi, c = R.box(fn)
        for j in range(4):
            y = lo[1] + 2 + j * (hi[1] - lo[1] - 4) / 3
            m.add(f"fs{fn}{j}", b.tub(fn, "ink", [V3([c[0] - 0.9, y, lo[2] - 0.2]), V3([c[0] + 0.9, y + 0.5, lo[2] - 0.2])], [0.1, 0.1], None, sides=3))
    m.add("needle", b.tub("palm", "brass", [V3([pc[0] + 4, phi[1] - 2, plo[2] - 1.5]), V3([pc[0] + 9, phi[1] + 3, plo[2] - 3])], [0.15, 0.05], None, sides=4))
    m.add("thread", b.tub("palm", "ink", [V3([pc[0] + 4, phi[1] - 2, plo[2] - 1.5]), V3([pc[0] + 2, phi[1] - 6, plo[2] - 1.0]),
                                         V3([pc[0] + 0, phi[1] - 4, plo[2] - 0.3])], [0.07, 0.07, 0.07], None, sides=3))


# ---------------------------------------------------------------- 巡礼者の目 (雲の瞼の一つ目・後光)
def pe_a(m, R):
    """A 雲の睫毛と虹彩: 上下の瞼の縁に長い睫毛、瞳のまわりに虹彩の筋の輪"""
    for lid, sgn in (("lid_top", 1), ("lid_bottom", -1)):
        lo, hi, c = R.box(lid)
        edge = lo[1] if sgn > 0 else hi[1]
        for k in range(16):
            t = k / 15
            x = lo[0] + 3 + t * (hi[0] - lo[0] - 6)
            yy = edge + sgn * 0.5 * math.sin(t * math.pi)
            m.add(f"la{lid}{k}", b.cone(lid, "ink", V3([x, yy, lo[2] + 1.2]), ((x - c[0]) * 0.03, sgn * 0.8, -1), 4.5 + 2 * math.sin(t * math.pi), 0.32,
                                        sides=3, rings=2, bend=(0, sgn * 1.6, 0)))
    lo, hi, c = R.box("eye")
    for k in range(24):
        a = k / 24 * math.tau
        m.add(f"ir{k}", b.tub("eye", "g_pink_m", [V3([c[0] + math.cos(a) * 3.2, c[1] + math.sin(a) * 3.2, lo[2] - 0.15]),
                                                  V3([c[0] + math.cos(a) * 5.4, c[1] + math.sin(a) * 5.4, lo[2] + 0.2])], [0.15, 0.1], None, sides=3))


def pe_b(m, R):
    """B 涙の鎖: 下の瞼から光る涙の滴が鎖のように連なって垂れる"""
    lo, hi, c = R.box("lid_bottom")
    for k in range(7):
        x = c[0] - 9 + k * 3
        top = V3([x, lo[1] + 1, lo[2] + 1.5])
        n = 4 + (k % 3)
        for j in range(n):
            p = top + V3([0, -1.0 - j * 2.0, 0])
            m.add(f"td{k}{j}", b.ell("lid_bottom", "glow", p, (0.45 + 0.1 * (j == n - 1), 0.75, 0.45), segs=6, rings=3))
            if j < n - 1:
                m.add(f"tl{k}{j}", b.tub("lid_bottom", "ink", [p + V3([0, -0.6, 0]), p + V3([0, -1.4, 0])], [0.08, 0.08], None, sides=3))


def pe_c(m, R):
    """C 歯の瞼: 上下の瞼の縁に歯が並び、瞼が顎のように閉じる"""
    for lid, sgn in (("lid_top", 1), ("lid_bottom", -1)):
        lo, hi, c = R.box(lid)
        edge = lo[1] if sgn > 0 else hi[1]
        for k in range(18):
            t = k / 17
            x = lo[0] + 2.5 + t * (hi[0] - lo[0] - 5)
            L = 4.0 + 2.5 * math.sin(t * math.pi) + (2.5 if k in (4, 13) else 0)
            z = lo[2] + 1.0 - 2.5 * math.sin(t * math.pi)
            m.add(f"tt{lid}{k}", b.cone(lid, "paper", V3([x, edge + sgn * 1.5, z]), (0, -sgn, -0.15), L, 1.1, sides=4, rings=2))
        m.add(f"gum{lid}", b.tub(lid, "ink", [V3([lo[0] + 2, edge + sgn * 1.5, lo[2] + 1.0]), V3([c[0], edge + sgn * 1.2, lo[2] - 1.6]),
                                              V3([hi[0] - 2, edge + sgn * 1.5, lo[2] + 1.0])], [1.5, 1.7, 1.5], None, sides=6))


# ---------------------------------------------------------------- 巡礼者の月 (鎖・時計の面・月)
def pm_a(m, R):
    """A 穴と鎖の枷: 月の面に縁の盛り上がった穴がいくつも、鎖は一つずつの輪になり、月に枷がはまる"""
    lo, hi, c = R.box("moon")
    rr = (hi[0] - lo[0]) / 2
    rng = np.random.default_rng(14)
    for k in range(12):
        d = nrm(rng.normal(size=3))
        if d[2] < -0.6:
            d[2] = -0.2
            d = nrm(d)
        r = rng.uniform(1.6, 3.6)
        m.add(f"cr{k}", ring("moon", "paper", c + d * (rr - 0.1), r, d, 0.45))
        m.add(f"crh{k}", b.ell("moon", "ink", c + d * (rr - 0.1), (r * 0.8, r * 0.8, r * 0.8), segs=8, rings=2))
    clo, chi, cc = R.box("chains")
    for k, x in enumerate((-6, -2, 2, 6)):
        chain(m, f"cl{k}_", "chains", "ink", V3([x * 0.4, chi[1] - 2, cc[2]]), V3([x, hi[1] - 1, c[2] + (k % 2) * 3 - 1.5]), link=1.6, th=0.28)
    m.add("shackle", ring("moon", "ink", c, rr + 0.6, (1, 0, 0.3), 0.9))
    for k in range(8):
        a = k / 8 * math.tau
        nn = nrm(V3([0.3, 0, -1]))
        a1, a2 = basis(nrm(V3([1, 0, 0.3])))
        m.add(f"sb{k}", b.ell("moon", "brass", c + (a1 * math.cos(a) + a2 * math.sin(a)) * (rr + 0.9), (0.6, 0.6, 0.6), segs=6, rings=2))


def pm_b(m, R):
    """B 時計の月: 時計の面に目盛りと三本の針、月の割れ目の奥に回る歯車"""
    lo, hi, c = R.box("clock")
    cc = (lo + hi) / 2
    r = (hi[0] - lo[0]) / 2
    for k in range(12):
        a = k / 12 * math.tau
        m.add(f"tk{k}", b.box("clock", "ink", cc + V3([math.cos(a) * r * 0.82, math.sin(a) * r * 0.82, -0.9]), (0.5, 1.6 if k % 3 == 0 else 1.0, 0.3),
                              R=b.R_(0, 0, math.degrees(a) - 90)))
    for k, (ang, L, w) in enumerate(((60, r * 0.5, 0.6), (200, r * 0.75, 0.4), (320, r * 0.85, 0.15))):
        a = math.radians(ang)
        m.add(f"hd{k}", b.cone("clock", "ink" if k < 2 else "glow", cc + V3([0, 0, -1.1 - k * 0.15]), (math.cos(a), math.sin(a), 0), L, w, sides=4, rings=1))
    m.add("pin", b.ell("clock", "brass", cc + V3([0, 0, -1.5]), (0.7, 0.7, 0.4), segs=8, rings=2))
    mlo, mhi, mc = R.box("moon")
    crack = [mc + V3([8, 10, -10]), mc + V3([5, 4, -13]), mc + V3([8, -2, -12]), mc + V3([4, -8, -13])]
    m.add("crack", b.tub("moon", "ink", crack, [1.4, 1.8, 1.6, 1.0], None, sides=5))
    for i, (p, r2) in enumerate(((mc + V3([6.5, 4, -12.8]), 1.8), (mc + V3([6.5, -3, -12.6]), 1.4))):
        m.add(f"cg{i}", ring("moon", "brass", p, r2, (0, 0, 1), 0.3))
        for k in range(8):
            a = k / 8 * math.tau
            m.add(f"cgt{i}{k}", b.box("moon", "brass", p + V3([math.cos(a) * (r2 + 0.35), math.sin(a) * (r2 + 0.35), 0]), (0.5, 0.5, 0.5),
                                      R=b.R_(0, 0, math.degrees(a))))


def pm_c(m, R):
    """C 欠けた月と骨: 月の縁が噛み取られて歯形が残り、欠けた所から肋の骨が突き出る"""
    lo, hi, c = R.box("moon")
    rr = (hi[0] - lo[0]) / 2
    nb = nrm(V3([0.55, 0.5, -0.67]))
    bite = c + nb * (rr - 0.6)
    a1, a2 = basis(nb)
    m.add("bite", b.lof("moon", "ink", [[bite + nb * dz + (a1 * math.cos(u) + a2 * math.sin(u)) * r for u in np.linspace(0, math.tau, 16, endpoint=False)]
                                         for dz, r in ((-0.8, 6.8), (0.9, 7.2))]))
    for k in range(14):
        u = k / 14 * math.tau
        rim = bite + nb * 0.6 + (a1 * math.cos(u) + a2 * math.sin(u)) * 7.6
        m.add(f"tm{k}", b.cone("moon", "paper", rim, nb * 0.6 - (a1 * math.cos(u) + a2 * math.sin(u)), 2.4, 0.9, sides=4, rings=1))
    for k in range(5):
        a = math.radians(-30 + k * 18)
        u = math.radians(-60 + k * 30)
        base = bite + nb * 0.5 + (a1 * math.cos(u) + a2 * math.sin(u)) * 3.5
        tip = base + nb * 11 + (a1 * math.cos(u) + a2 * math.sin(u)) * 5 + V3([0, 3, 0])
        m.add(f"rib{k}", b.tub("moon", "paper", [base, (base + tip) / 2 + (a1 * math.cos(u) + a2 * math.sin(u)) * 2.5, tip], [1.3, 1.0, 0.35], None, sides=7))
    m.add("spine", b.tub("moon", "paper", [bite + nb * 0.5 - a2 * 4, bite + nb * 3 + a2 * 1, bite + nb * 6 + a2 * 5], [1.6, 1.4, 1.0], None, sides=7))
    for k in range(5):
        m.add(f"vt{k}", b.ell("moon", "paper", bite + nb * (0.5 + k * 1.3) - a2 * (4 - k * 2.2), (1.9, 0.8, 1.9), segs=8, rings=2))


BOSSES = {
    "founder": ("初代の空見", "robe", "brass", "g_teal", [("A 星図の外套", fo_a), ("B 鐘の舌の杖", fo_b), ("C 骨の空見", fo_c)]),
    "last_bellkeeper": ("最後の鐘守", "ink", "brass", "g_pink", [("A 割れた大鐘と縄", lb_a), ("B 小鐘の衣", lb_b), ("C 鐘楼を背負う", lb_c)]),
    "lamp_eye": ("灯守の目", "ink", "brass", "g_pink", [("A 灯芯の睫毛", le_a), ("B 灯台のレンズ", le_b), ("C 焼けた蛾の籠", le_c)]),
    "rewinder": ("戻し手", "ink", "brass", "g_pink", [("A 噛み合う歯車", rw_a), ("B 振り子と錘", rw_b), ("C 鍛冶の革", rw_c)]),
    "pilgrim_plume": ("巡礼者の羽", "paper", "ink", "g_pink", [("A 一枚ずつの羽根", pp_a), ("B 骨の翼", pp_b), ("C 札の付いた鎖", pp_c)]),
    "pilgrim_hand": ("巡礼者の手", "paper", "ink", "g_pink", [("A 関節の骨と爪", ph_a), ("B 巡礼の数珠", ph_b), ("C 縫い合わせた手", ph_c)]),
    "pilgrim_eye": ("巡礼者の目", "paper", "ink", "g_pink", [("A 睫毛と虹彩", pe_a), ("B 涙の鎖", pe_b), ("C 歯の瞼", pe_c)]),
    "pilgrim_moon": ("巡礼者の月", "paper", "ink", "g_pink", [("A 穴と鎖の枷", pm_a), ("B 時計の月", pm_b), ("C 欠けた月と骨", pm_c)]),
}


def parts(fn, R, glow):
    m = b.Mount("fin", "fin")
    fn(m, R)
    pal = {k: b.M_(v, lit=0.34) for k, v in bs.PAL.items() if not k.startswith("g_")}
    pal["glow"] = b.M_(bs.PAL[glow], base=0.8, lit=0.1, min=1)
    pal["g_pink_m"] = b.M_(bs.PAL["g_pink"], base=0.55, lit=0.2, min=1)
    m.pal = dict(mats=pal, glow_mats=["glow", "g_pink_m"])
    size, atlas, gl = b.paint_mount(m, m.pal, None, 512)
    return m.F, atlas, gl


def sheet(name, title, cols):
    F = bs.voxel.font
    allV = np.concatenate([np.concatenate([f.verts for f in c[1]]) for c in cols])
    lo, hi = allV.min(0), allV.max(0)
    c = (lo + hi) / 2
    Rf = float(np.max(hi - lo)) / 2
    PW, PH = 340, 380
    cv = Image.new("RGB", (20 + len(cols) * (PW + 10), 110 + 2 * (PH + 10)), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((20, 14), f"{title} — 作り込みのラフ (左が今)", font=F(26), fill=(236, 232, 218))
    for i, (label, faces, at, g, note, ntri) in enumerate(cols):
        x = 20 + i * (PW + 10)
        d.text((x, 50), f"{label}  (+{ntri})" if ntri else label, font=F(18), fill=(150, 232, 255))
        d.text((x, 76), note, font=F(13), fill=(176, 176, 192))
        a2, g2 = bs.square(at, g)
        for j, (yaw, pitch) in enumerate(((180, 6), (215, 14))):
            im = bs.render(faces, a2[..., :3], g2, yaw, pitch, (PW, PH), c, PW * 0.46 / Rf)
            bgi = Image.new("RGBA", (PW, PH), (40, 40, 52, 255))
            bgi.alpha_composite(im)
            cv.paste(bgi.convert("RGB"), (x, 110 + j * (PH + 10)))
    cv.save(OUT / f"{name}.png")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    names = [a for a in sys.argv[1:] if not a.startswith("--")] or list(BOSSES)
    for name in names:
        faces, img, glow = bs.load(name)
        title, body, metal, light, opts = BOSSES[name]
        R = bs.Ref(faces)
        cols = [("今の姿", faces, img, glow, "", 0)]
        for label, fn in opts:
            af, aa, ag = parts(fn, R, light)
            mf, ma, mg = bs.merge(faces, img, glow, af, aa, ag)
            cols.append((label, mf, ma, mg, fn.__doc__.split(":")[1].strip()[:30], sum(len(f.tris()) for f in af)))
        sheet(name, title, cols)
        print(name, "ok", flush=True)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
