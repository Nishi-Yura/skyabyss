"""苔這い (moss_crawler) の低ポリ メッシュ 3 案 (A 忠実 / B 骨・棘 / C 肉・苔)。

座標は geo (moss_crawler.geo.json) と同じ: 1 単位 = 1/16 ブロック、y が上、頭が +Z (root の yaw 180 で前が -Z になる)。
骨の名前・階層・pivot は geo から読む (27 本)。メッシュは骨ごとの別パーツで、頂点は絶対座標。
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(HERE.parent))
from lpkit import (Face, _mk, ellipsoid, loft, nrm, poly, rot_x, rot_y, rot_z, spike, transform, tri_count,  # noqa
                   tube)

GEO = ROOT / "src/main/resources/assets/skyabyss/geo/entity/moss_crawler.geo.json"


def load_bones():
    g = json.loads(GEO.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    out = {}
    for b in g["bones"]:
        out[b["name"]] = (b.get("parent"), [float(x) for x in b.get("pivot", [0, 0, 0])])
    return out


BONES = load_bones()
ZS = [-10.5, -7.0, -3.4, 0.2, 3.8]
RX = [2.3, 3.0, 3.5, 3.5, 3.1]
CY = 4.0
LEGS = [("1", "seg2", -5.2), ("2", "seg3", -1.6), ("3", "seg5", 2.0)]


def V(*a):
    return np.array(a, float)


def bump(amp, f, seed=0.0):
    """面の見える楕円体の表面を少しでこぼこさせる (局所座標 p -> p)。"""
    def d(p):
        k = 1.0 + amp * math.sin(f * p[0] + 1.3 + seed) * math.cos(f * 0.9 * p[2] + 0.4 + seed) \
            + amp * 0.6 * math.sin(f * 1.7 * p[1] + seed * 2)
        return p * k
    return d


def dome(faces, cy):
    """下半分 (体に埋まる側) の面を捨てて、盛り上がりだけにする。"""
    return [f for f in faces if f.verts.mean(0)[1] > cy - 0.05]


def barrel(bone, mat, z, rx, ry, scales, n=8, phase=22.5, cy=CY, zs=None, noise=0.0, seed=0, belly=0.9, caps=(False, False)):
    """前後に細くなる八角の樽。scales = [(z からのずれ, 太さの倍率), ...]"""
    rings = []
    for i, (dz, s) in enumerate(scales):
        pts = []
        for k in range(n):
            a = math.radians(phase + 360.0 * k / n)
            j = 1.0 + noise * math.sin(seed + k * 2.3 + i * 1.7)
            x = rx * s * j * math.cos(a)
            y = ry * s * j * math.sin(a)
            if y < 0:
                y *= belly
            pts.append(V(x, cy + y, z + dz))
        rings.append(pts)
    return loft(bone, mat, rings, caps=caps)


def hoop(bone, mat, z, r, width, thick, a0=-30, a1=210, npts=5, sides=4, off=0.0, hide=0.5):
    """背から脇へ回る骨の輪 (下は開く)。"""
    pts = []
    for t in np.linspace(a0, a1, npts):
        a = math.radians(t)
        pts.append((r * math.cos(a), CY + r * 0.98 * math.sin(a), z + off))
    rs = lambda w: np.interp(np.linspace(0, 1, npts), np.linspace(0, 1, len(w)), w)
    return tube(bone, mat, pts, ra=rs(width), rb=rs(thick), sides=sides, hint=(0, 0, 1),
                axis_fn=lambda p, z=z + off: np.array([0.0, CY, z]), hide_cos=hide)


def build_legs(F, style):
    for key, seg, z in LEGS:
        for sx, s in (("l", 1), ("r", -1)):
            pre = f"{sx}{key}"
            hip = V(s * 3.0, 3.0, z)
            knee = V(s * 4.6, 1.9, z)
            foot = V(s * 5.2, 0.4, z + 0.1)
            if style == "a":
                F += tube(pre + "_u", "bone", [hip, knee], ra=[1.0, 0.85], rb=[0.95, 0.8], sides=4, hint=(0, 0, 1))
                F += tube(pre + "_l", "bone", [knee, foot], ra=[0.85, 0.45], rb=[0.8, 0.45], sides=4, hint=(0, 0, 1))
                F += spike(pre + "_l", "bone", (s * 5.2, 0.9, z + 0.1), (0, -0.55, 0.85), 2.0, 0.8, sides=3, rings=1,
                           hint=(1, 0, 0))
            elif style == "b":
                F += tube(pre + "_u", "bone", [hip, knee], ra=[0.85, 0.65], rb=[0.8, 0.6], sides=3, hint=(0, 0, 1))
                F += spike(pre + "_u", "bone", (s * 4.3, 2.2, z), (s * 0.4, 1.0, 0), 1.8, 0.7, sides=3, rings=1,
                           hint=(0, 0, 1))
                F += tube(pre + "_l", "bone", [knee, foot], ra=[0.7, 0.4], rb=[0.65, 0.4], sides=3, hint=(0, 0, 1))
                F += spike(pre + "_l", "bone", (s * 5.2, 0.8, z + 0.1), (0, -0.7, 1.0), 2.4, 0.55, sides=3, rings=1,
                           hint=(1, 0, 0))
            else:
                F += tube(pre + "_u", "flesh", [hip + V(0, 0.4, 0), knee], ra=[1.35, 1.1], rb=[1.2, 1.0], sides=3,
                          hint=(0, 0, 1))
                F += tube(pre + "_l", "flesh", [knee, foot + V(0, 0.2, 0)], ra=[1.05, 0.8], rb=[1.0, 0.75], sides=3,
                          hint=(0, 0, 1))
                F += dome(ellipsoid(pre + "_l", "bone", (s * 5.3, 0.4, z + 0.45), (1.35, 1.0, 1.6), segs=5, rings=1,
                                    phase=0), 0.4)


def build_antennae(F, style):
    for sx, ant, lure in ((1, "ant_l", "lure_l"), (-1, "ant_r", "lure_r")):
        s = sx
        if style == "a":
            F += tube(ant, "bone", [(s * 1.6, 8.0, 9.0), (s * 2.0, 9.9, 9.7), (s * 2.4, 11.6, 10.4)],
                      ra=[0.7, 0.55, 0.38], rb=[0.65, 0.5, 0.36], sides=4, hint=(1, 0, 0))
            F += ellipsoid(lure, "lure", (s * 2.4, 12.4, 10.4), (0.85, 0.95, 0.85), segs=6, rings=1, phase=0)
        elif style == "b":
            F += tube(ant, "bone", [(s * 1.6, 8.0, 9.0), (s * 1.6, 10.2, 9.0), (s * 2.3, 11.8, 10.2)],
                      ra=[0.7, 0.55, 0.35], rb=[0.6, 0.5, 0.35], sides=3, hint=(1, 0, 0))
            for dx, dz in ((0.9, 0.0), (-0.5, 0.8), (-0.5, -0.8)):
                F += spike(ant, "bone", (s * 2.3, 11.5, 10.2), (s * dx, 0.55, dz), 1.5, 0.28, sides=3, rings=1)
            F += ellipsoid(lure, "lure", (s * 2.3, 12.7, 10.2), (0.8, 1.0, 0.8), segs=4, rings=1, phase=0)
        else:
            F += tube(ant, "flesh", [(s * 1.6, 8.0, 9.0), (s * 1.9, 10.0, 9.6), (s * 2.6, 11.6, 10.6)],
                      ra=[0.95, 0.75, 0.5], rb=[0.9, 0.7, 0.5], sides=4, hint=(1, 0, 0))
            F += ellipsoid(lure, "lure", (s * 2.7, 12.6, 10.7), (1.15, 1.2, 1.15), segs=6, rings=1, phase=0)


def build_head(F, style):
    """頭 = 骨の仮面 + 一つ目 + 顎と歯。目は eyes の骨、顎は jaw、歯は teeth の骨。"""
    c_y = 5.2
    if style == "a":
        spec = [(5.7, 1.7, 1.6), (7.6, 3.5, 3.3), (10.0, 3.8, 3.5), (11.9, 2.5, 2.4)]
        mats = ["bone"] * 3
    elif style == "b":
        spec = [(5.6, 1.5, 1.4), (7.4, 3.4, 3.2), (9.8, 3.8, 3.6), (11.9, 2.6, 2.6)]
        mats = ["bone"] * 3
    else:
        spec = [(5.5, 2.4, 2.2), (7.4, 3.9, 3.6), (10.0, 4.1, 3.7), (11.9, 2.8, 2.6)]
        mats = ["flesh"] * 3
    rings = []
    for i, (z, rx, ry) in enumerate(spec):
        pts = []
        for k in range(8):
            a = math.radians(22.5 + 45 * k)
            x, y = rx * math.cos(a), ry * math.sin(a)
            if y < 0:
                y *= 0.82
            pts.append(V(x, c_y + y, z))
        rings.append(pts)
    if style == "b":
        # 重い眉・こけた頬
        for k in range(8):
            a = 22.5 + 45 * k
            if 20 < a < 160:   # 上側
                rings[2][k] += V(0, 0.5, 0.6)
                rings[3][k] += V(0, 0.4, 0.8)
    F += loft("head", mats[0], rings, caps=(False, "bone2" if style != "c" else "flesh"), mats=mats)
    # 目のまわりの縁と眉
    if style == "a":
        F += dome(ellipsoid("head", "moss", (0, 8.4, 8.8), (2.6, 1.3, 2.4), segs=6, rings=1, phase=0), 8.4)       # 頭頂の苔
        F += dome(ellipsoid("head", "moss2", (-2.0, 7.9, 7.0), (1.3, 0.9, 1.2), segs=4, rings=1, phase=0), 7.9)
    elif style == "b":
        F += tube("head", "bone", [(-3.3, 7.2, 10.8), (0, 8.6, 11.8), (3.3, 7.2, 10.8)], ra=[0.9, 1.1, 0.9],
                  rb=[0.8, 1.0, 0.8], sides=4, hint=(0, 1, 0), axis_fn=lambda p: np.array([0, 5.4, p[2] - 3.0]), hide_cos=0.5)
        for sx in (1, -1):
            F += spike("head", "bone", (sx * 2.6, 8.4, 8.0), (sx * 0.5, 1.0, -0.4), 4.2, 1.1, sides=3, rings=2,
                       bend=(sx * 0.9, 0.0, -1.0), hint=(0, 0, 1))
            F += spike("head", "bone", (sx * 3.7, 6.2, 8.4), (sx * 1.0, 0.25, 0.1), 2.6, 0.9, sides=3, rings=1,
                       hint=(0, 1, 0))
        F += spike("head", "bone", (0, 8.8, 6.8), (0, 0.6, -1.0), 3.0, 0.9, sides=3, rings=1, hint=(1, 0, 0))
        F += dome(ellipsoid("head", "moss", (0, 8.7, 8.0), (1.6, 0.8, 1.6), segs=4, rings=1, phase=0), 8.7)
    else:
        F += dome(ellipsoid("head", "moss", (0, 8.2, 8.6), (3.3, 1.7, 3.2), segs=6, rings=2, deform=bump(0.12, 2.2, 1.0)), 8.2)
    # 目玉 (前が +Z の面取りした円柱の先。前の大きな八角の板に瞳と虹彩を描く)
    ec = V(0, 5.6, 12.1)
    er = (1.9, 1.9, 1.7) if style != "c" else (2.3, 2.3, 2.0)
    zf = 13.8 if style != "c" else 14.2
    rr = []
    for z_, k_ in ((11.8, 1.0), (zf, 0.76)):
        rr.append([V(er[0] * k_ * math.cos(math.radians(22.5 + 45 * k)), ec[1] + er[1] * k_ * math.sin(math.radians(22.5 + 45 * k)), z_)
                   for k in range(8)])
    rr = [r_[1:] + r_[:1] for r_ in rr]   # 板の最初の辺を軸に平行にする (島の向き)
    F += loft("eyes", "eye_w", rr, caps=(False, "eye"))
    # 顎と歯
    if style == "a":
        F += ellipsoid("jaw", "bone2", (0, 2.1, 10.5), (2.9, 1.0, 2.7), segs=6, rings=1, phase=0)
        for x in (-2.0, -1.0, 0.0, 1.0, 2.0):
            zf = 10.4 + 2.9 * math.sqrt(max(0, 1 - (x / 3.1) ** 2)) * 0.9
            F += spike("teeth", "tooth", (x, 2.6, zf), (0, 1, 0.12), 1.7, 0.5, sides=3, rings=1, hint=(1, 0, 0))
    elif style == "b":
        F += tube("jaw", "bone", [(-2.9, 2.4, 8.4), (-2.3, 2.0, 11.2), (0, 1.9, 12.6), (2.3, 2.0, 11.2), (2.9, 2.4, 8.4)],
                  ra=[1.0, 1.0, 1.0, 1.0, 1.0], rb=[0.9, 0.95, 1.0, 0.95, 0.9], sides=4, hint=(0, 1, 0),
                  axis_fn=lambda p: np.array([0, p[1] + 0.2, 8.5]), hide_cos=0.7)
        for x, zf, h in ((-2.4, 11.0, 2.0), (-1.2, 12.3, 2.6), (0.0, 12.8, 2.0), (1.2, 12.3, 2.6), (2.4, 11.0, 2.0)):
            F += spike("teeth", "tooth", (x, 2.6, zf), (0, 1, 0.15), h, 0.5, sides=3, rings=1, hint=(1, 0, 0))
        for x in (-2.0, 2.0):   # 上の牙
            F += spike("head", "tooth", (x, 3.0, 11.2), (0, -1, 0.1), 1.8, 0.5, sides=3, rings=1)
    else:
        F += ellipsoid("jaw", "flesh", (0, 2.2, 10.4), (3.3, 1.2, 2.9), segs=6, rings=1, phase=0)
        for x in (-1.6, 0.0, 1.6):
            F += spike("teeth", "tooth", (x, 2.9, 12.1 - abs(x) * 0.3), (0, 1, 0.1), 1.0, 0.55, sides=3, rings=1,
                       hint=(1, 0, 0))
    return {"eye": ec, "eye_r": er, "style": style}


def build_body(F, style):
    for i, z in enumerate(ZS):
        r = RX[i]
        b = f"seg{i + 1}"
        if style == "a":
            F += barrel(b, "flesh", z, r, r * 0.95, [(-2.4, 0.74), (0.0, 1.0), (2.4, 0.74)],
                        caps=(i == 0, False))
            if i >= 1:
                F += hoop(b, "bone", z, r * 1.1, [0.55, 0.7, 0.75, 0.7, 0.55], [0.5, 0.6, 0.65, 0.6, 0.5], sides=3,
                          off=-1.1)
            cy_ = CY + r * 0.66
            F += dome(ellipsoid(b, "moss" if i % 2 == 0 else "moss2", (0, cy_, z + 0.7), (r * 0.9, 1.7, 1.6), segs=6,
                                rings=2, deform=bump(0.1, 2.0, i * 1.7)), cy_)
        elif style == "b":
            rr = r * 0.9
            F += barrel(b, "flesh", z, rr, rr * 0.95, [(-2.4, 0.7), (0.0, 1.0), (2.4, 0.7)], phase=0, n=6,
                        caps=(i == 0, False))
            if i >= 1:
                F += hoop(b, "bone", z, r * 1.12, [0.8, 1.0, 1.05, 1.0, 0.8], [0.65, 0.85, 0.9, 0.85, 0.65], sides=3,
                          npts=4)
                # 背のとげ (輪から外へ)
                for ang, ln in ((90, 3.0 + 0.4 * i), (40, 1.9), (140, 1.9)):
                    a = math.radians(ang)
                    base = V(r * 1.1 * math.cos(a), CY + r * 1.08 * math.sin(a), z)
                    d = V(math.cos(a) * 0.8, math.sin(a) + 0.2, -0.35)
                    F += spike(b, "bone", base, d, ln, 0.85 if ang == 90 else 0.65, sides=3,
                               rings=2 if ang == 90 else 1, bend=(0, 0, -0.6), hint=(0, 0, 1))
            else:
                F += spike(b, "bone", (0, CY + rr * 0.95, z), (0, 1, -0.2), 2.2, 0.8, sides=3, rings=1, hint=(1, 0, 0))
            # 背の苔は小さな塊だけ
            F += dome(ellipsoid(b, "moss" if i % 2 == 0 else "moss2", (0.0, CY + r * 0.7, z + (0.9 if i % 2 else -0.9)),
                                (r * 0.62, 1.1, 1.3), segs=5, rings=1, phase=0), CY + r * 0.7)
        else:
            F += barrel(b, "flesh", z, r * 1.04, r * 1.0, [(-2.4, 0.78), (0.2, 1.04), (2.4, 0.78)],
                        noise=0.045, seed=i * 3.1, belly=0.85, phase=22.5, caps=(i == 0, False))
            if i in (1, 3):
                F += hoop(b, "bone", z + 0.2, r * 1.0, [0.5, 0.6, 0.65, 0.6, 0.5], [0.4, 0.5, 0.55, 0.5, 0.4], a0=0,
                          a1=180, npts=4, sides=3, hide=0.4)
            # 背と脇に垂れる厚い苔
            cy_ = CY + r * 0.7
            F += dome(ellipsoid(b, "moss" if i % 2 == 0 else "moss2", (0, cy_, z), (r * 1.08, 2.3, 2.4), segs=7,
                                rings=2, deform=bump(0.16, 1.8, i * 2.1)), cy_)
            for sx in (1, -1):
                F += dome(ellipsoid(b, "moss2" if i % 2 == 0 else "moss", (sx * r * 0.98, CY + 0.8, z + (0.4 if i % 2 else -0.4)),
                                    (1.5, 1.5, 1.8), segs=5, rings=1, phase=0), CY + 0.8)
    # 尾
    if style == "a":
        F += spike("tail", "bone", (0, 3.8, -12.0), (0, 0.8, -1.0), 4.4, 0.95, sides=4, rings=2, bend=(0, 0.8, 0),
                   hint=(1, 0, 0))
    elif style == "b":
        F += spike("tail", "bone", (0, 3.8, -12.0), (0, 0.6, -1.0), 5.2, 1.0, sides=3, rings=2, bend=(0, 1.2, 0),
                   hint=(1, 0, 0))
        for sx in (1, -1):
            F += spike("tail", "bone", (0, 4.4, -13.6), (sx * 1.0, 0.5, -0.5), 2.2, 0.55, sides=3, rings=1, hint=(0, 1, 0))
    else:
        F += spike("tail", "flesh", (0, 3.8, -12.0), (0, 0.55, -1.0), 3.6, 1.35, sides=4, rings=2, bend=(0, 0.6, 0),
                   hint=(1, 0, 0))
        F += ellipsoid("tail", "moss", (0, 5.0, -14.4), (1.5, 1.4, 1.5), segs=5, rings=1, phase=0)


def build(style):
    F = []
    build_body(F, style)
    extras = build_head(F, style)
    build_antennae(F, style)
    build_legs(F, style)
    extras["zs"] = ZS
    return F, extras
