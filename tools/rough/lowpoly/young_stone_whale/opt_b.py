"""B 案: 背びれの鞍 — 細身で背と頭に石の板を重ねた仔鯨。両わきから立つ二枚の大きな鯨骨の背びれが
6 人の鞍の壁と背もたれになり、びれの稜線に青緑の光の筋 (歌のかけら)。額には光る潮吹きの穴。胸びれは長く翼のよう。"""
from __future__ import annotations

from ysw import *  # noqa
from opt_a import body_common  # noqa

NAME = "B 背びれの鞍"
HINT = 512
PAL = dict(mats=dict(BASE_MATS), glow_mats=["mote", "glowbone"])

KEYS = [(-88, 30, 7, 7), (-84, 30, 14, 15), (-74, 30, 20, 22), (-56, 30, 23, 27), (-30, 30, 24, 29), (0, 30, 24, 29),
        (30, 31, 22, 27), (52, 32, 18, 22), (68, 34, 12, 14), (82, 35, 7, 8)]
TAIL = [(56, 34, 11, 13), (72, 36, 8, 9), (88, 37, 5, 5.5), (98, 38, 3.5, 4)]
# 背びれの高さ (z → 稜線の y)
PROFILE = [(-60, 56), (-52, 61), (-34, 63), (-6, 66), (18, 71), (38, 77), (52, 80), (62, 79), (72, 72), (80, 64)]


def _interp(tab, z):
    for (z0, a), (z1, b) in zip(tab, tab[1:]):
        if z0 <= z <= z1:
            return a + (b - a) * (z - z0) / (z1 - z0)
    return tab[0][1] if z < tab[0][0] else tab[-1][1]


def dorsal_fin(s):
    zs = [-60, -56, -50, -40, -28, -14, 0, 14, 28, 40, 50, 56, 62, 70, 78, 82]
    rings, tops = [], []
    for z in zs:
        ytop = _interp(PROFILE, z)
        dx = (0 if z <= 50 else 12 * min(z - 50, 20) / 20) + (0 if z >= -50 else 8 * (-50 - z) / 10)
        dy = 0 if z <= 50 else -6 * min(z - 50, 20) / 20
        th = 1.0 if -52 <= z <= 62 else (0.6 if z <= 72 else 0.3)
        if z > 70:   # 後ろへ鉤のように張り出す稜線 (根元は胴の中に残し、上だけ伸ばす)
            dy = -6 + (z - 70) * 2.6
        lean = 4.0 * max(0.0, min(1.0, (ytop - 56) / 20))   # 高いところほど外へ開く (前から見て耳のような V)
        pts = [(22 - dx, 38 + dy), (22 + 5 * th - dx, 38 + dy), (33.5 - dx, 56 + dy * 0.5), (33.5 + lean - dx, ytop),
               (33.5 + lean - 5.5 * th - dx, 57 + dy * 0.5)]
        rings.append([V(s * x, y, z) for x, y in pts])
        tops.append(V(s * (33.5 + lean - dx), ytop, z))
    F = rloft("saddle", "bone", rings, (True, True))
    # 外の面に斜めの骨の条 (鰭条)
    for z in (-44, -24, -4, 16, 36, 52):
        yt = _interp(PROFILE, z + 8) - 3
        lean = 4.0 * max(0.0, min(1.0, (yt + 3 - 56) / 20))
        a0 = V(s * 27.6, 41, z - 4)
        a1 = V(s * 34.6, 56, z + 1)
        a2 = V(s * (34.2 + lean), yt, z + 8)
        F += tube("saddle", "plate", [a0, a1, a2], [1.5, 1.3, 0.8], sides=4, hint=(0, 0, 1), caps=(True, True))
    # 稜線の光の筋
    F += tube("saddle", "glowbone", tops[1:-1], [0.9] * (len(tops) - 2), sides=4, hint=(0, 1, 0), caps=(True, True))
    return F


def build():
    F, eyes = body_common(KEYS, TAIL, eye_z=-66, eye_r=(4.2, 5.0, 5.0), belly=0.88, fin_len=1.25, fin_w=0.85)
    F = [f for f in F if f.bone != "mote"]
    # 額の潮吹きの穴: 骨の縁の中に光る歌のかけら
    m = pv("mote")
    wall, _ = oval_wall("mote", "bone", m[1] - 3.0, m[1] + 1.5, 3.0, 3.8, 1.8, n=10, cz=m[2])
    F += wall
    F += ellipsoid("mote", "mote", m + V(0, -0.5, 0), (3.0, 2.6, 3.8), segs=8, rings=2)
    # 頭と背の石の板 (重ねた鱗のように。背の鞍の範囲には置かない)
    for i, (z, w, sc) in enumerate([(-80, 14, 0.8), (-72, 20, 1.0), (-62, 22, 1.0)]):
        cy, hh, hw = hull_at(KEYS, z)
        y = cy + hh * 0.93
        if z == -62:
            for s in (1, -1):
                F += fbox("body", "plate", V(s * 9, y - 1.5, z), (10, 3, 10), R=rot_z(-s * 22) @ rot_x(-10),
                          taper=(0.8, 0.8), cut=0.3)
        else:
            F += fbox("body", "plate", V(0, y - 1.2, z), (w * sc, 3, 10), R=rot_x(-14), taper=(0.75, 0.8), cut=0.35)
    for s in (1, -1):
        for j, (z, dy, w, l) in enumerate(((-46, 4, 12, 14), (-30, -2, 14, 17), (-12, 5, 13, 15), (6, -1, 15, 18),
                                           (24, 4, 12, 15), (40, -2, 11, 13))):
            cy, hh, hw = hull_at(KEYS, z)
            c = V(s * (hw * 0.93), cy + dy, z)
            F += fbox("body", "plate", c, (w, 3.0, l), R=rot_y(s * (8 if j % 2 else -6)) @ rot_z(-s * 78),
                      taper=(0.72, 0.75), cut=0.4)
    # 尾の付け根の小さな板
    F += fbox("tail", "plate", V(0, 44, 70), (8, 3, 10), R=rot_x(-8), taper=(0.7, 0.8), cut=0.35)
    # ================================================================ 鞍 (石の板の床 + 二枚の背びれ)
    F += fbox("saddle", "stone2", (0, 48.5, 0), (38, 13, 90), taper=(52 / 38, 104 / 90), cut=0.15)
    F += fbox("saddle", "plate", (0, SEAT_Y - 1.5, 0), (54, 3, 106), cut=0.12)
    for s in (1, -1):
        F += dorsal_fin(s)
    # 前の手がかり (低い骨の横木)
    F += tube("saddle", "bone", [V(-27, 58, -55), V(0, 62, -56.5), V(27, 58, -55)], [1.8, 2.2, 1.8], sides=5,
              hint=(0, 0, 1), caps=(True, True))
    # 左わきの小さな錨 (板の留め具から垂れる)
    F += chain("body", "chain", V(-29.5, 45, 36), V(-31.5, 30, 37), links=4, r=1.4)
    F += anchor_stone("body", "stone2", V(-31.8, 30.5, 37), size=5.5, mat2="chain")
    return F, eyes, whale_extra(mouth_z=(-88, -58), mouth_y0=26.5)
