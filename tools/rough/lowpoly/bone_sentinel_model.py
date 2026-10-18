"""骨の番兵の低ポリ メッシュ (骨ごとに別パーツ)。

座標: 1 単位 = 1/16 ブロック、y が上、正面は -Z。ゲーム内の geo (bone_sentinel.geo.json) と同じ骨・同じ pivot で、
今のアニメーションがそのまま動く。顎は geo に骨がないので head に含める。盾は別メッシュ (socket_off の
pivot を原点とする局所座標)。
"""
from __future__ import annotations

import math

import numpy as np

from lpkit import (ellipsoid, grid, loft, poly, ring, rot_x, rot_y, rot_z, spike, transform, tri_count, tube)

# geo と同じ骨の階層と pivot (bone_sentinel.geo.json)
BONES = {
    "body": (None, [0.0, 12.0, 0.0]),
    "head": ("body", [0.0, 24.0, 0.0]),
    "left_arm": ("body", [5.5, 20.0, 0.0]),
    "left_forearm": ("left_arm", [5.5, 14.5, 0.0]),
    "socket_off": ("left_forearm", [7.5, 9.0, 0.0]),
    "right_arm": ("body", [-5.5, 20.0, 0.0]),
    "right_forearm": ("right_arm", [-5.5, 14.5, 0.0]),
    "leg_l": ("body", [2.0, 10.0, 0.0]),
    "leg_r": ("body", [-2.0, 10.0, 0.0]),
}
SOCKET = np.array(BONES["socket_off"][1])


def V(*a):
    return np.array(a, float)


def build_model(report=None):
    F = []
    marks = []
    extras = {}

    def mark(name):
        marks.append((name, tri_count(F)))

    # ================================================================ 頭 (クジラの骨の頭蓋)
    R = [
        ring(33.6, 2.2, -2.4, 2.6, 0.3),
        ring(32.8, 3.4, -3.5, 3.8, 0.25),
        ring(31.4, 3.9, -4.1, 4.2, 0.15),
        ring(29.6, 4.2, -4.6, 3.7, 0.0),
        ring(27.8, 3.4, -5.0, 3.0, 0.0),
        ring(25.8, 2.3, -4.8, 1.8, 0.0),
    ]
    # 眉の張り出し・鼻筋・頬骨・眼窩のへこみ
    R[2][0] += V(0, 0.0, -0.2)
    R[3][0] += V(0, 0.1, -0.5)                 # 鼻筋
    R[4][0] += V(0, -0.5, 0.95)                # 鼻の穴
    for s, k in ((1, 1), (-1, 7)):
        R[3][k] += V(-s * 0.9, -0.1, 1.7)      # 眼窩
        R[4][k] += V(s * 1.1, 0.0, -0.45)      # 頬骨
        R[2][k] += V(-s * 0.1, 0.3, -0.9)      # 眉
        R[4][2 if s > 0 else 6] += V(s * 0.5, 0.0, -0.3)
    extras["eyes"] = [R[3][1].copy() + V(0, 0, -0.4), R[3][7].copy() + V(0, 0, -0.4)]
    extras["nose"] = R[4][0].copy() + V(0, 0.2, 0)
    F += loft("head", "bone", R, caps=("bone", "mouth"))
    mark("skull")
    # 背へ流れる骨の角 (二本だけ)
    for s in (-1, 1):
        F += spike("head", "bone", (s * 2.5, 32.0, 2.4), (s * 0.15, 0.1, 1.0), 4.6, 1.6, sides=4, rings=2,
                   bend=(s * 0.4, 1.1, 0.0))
    mark("horns")
    # 歯 (上)
    for s in (-1, 1):
        for x, z in ((0.85, -4.7), (2.0, -4.0)):
            F += spike("head", "tooth", (s * x, 25.9, z), (0, -1, -0.12), 1.6, 0.5, sides=3, rings=1)
    # 顎 (U 字の骨) と下の歯
    jaw = [(-3.0, 25.4, 0.9), (-2.3, 22.9, -3.9), (2.3, 22.9, -3.9), (3.0, 25.4, 0.9)]
    F += tube("head", "bone", jaw, ra=[0.95, 1.05, 1.05, 0.95], rb=[1.1, 1.2, 1.2, 1.1], sides=4, hint=(0, 1, 0),
              axis_fn=lambda p: np.array([0, p[1] + 0.3, p[2] + 1.5]), hide_cos=0.7)
    for s in (-1, 1):
        for x, z in ((1.4, -4.3), (2.4, -3.0)):
            F += spike("head", "tooth", (s * x, 23.8, z), (0, 1, -0.1), 1.7, 0.5, sides=3, rings=1)
    mark("jaw_teeth")

    # ================================================================ 胴
    # 骨盤: 腸骨の張り出しと恥骨のある椀形
    P = [ring(15.2, 4.1, -2.0, 2.2, 0.1, n=6), ring(13.0, 3.5, -2.6, 2.5, 0.1, n=6),
         ring(10.7, 1.8, -1.9, 1.7, 0.1, n=6)]
    F += loft("body", "bone", P, caps=("bone", False))
    for s in (-1, 1):
        F += spike("body", "bone", (s * 3.5, 14.6, 0.0), (s * 1.0, 0.55, -0.15), 2.7, 1.5, sides=4, rings=1)
    mark("pelvis")
    # 背骨: 椎骨のこぶ
    sp = [(0, 15.0, 1.5), (0, 18.4, 2.4), (0, 21.6, 2.2), (0, 24.6, 1.2)]
    F += tube("body", "bone", sp, ra=[1.3, 1.55, 1.25, 1.1], rb=[1.2, 1.4, 1.15, 1.0], sides=4,
              hint=(1, 0, 0), phase=45)
    for y, z, ln in ((17.8, 3.3, 2.2), (20.6, 3.4, 2.5), (22.8, 2.9, 2.0)):
        F += spike("body", "bone", (0, y, z), (0, 0.35, 1.0), ln, 0.85, sides=3, rings=1)
    mark("spine")
    # 肋骨: 背骨から回り込み、胸骨の左右に出会う
    zc, rz = -0.64, 2.21
    levels = [(21.0, 3.1), (19.1, 3.5), (17.2, 3.6), (15.5, 3.2)]
    for s in (-1, 1):
        for (y, rx) in levels:
            pts = []
            for ph in (15, 58, 112, 168):
                a = math.radians(ph)
                pts.append((s * rx * math.sin(a), y - 1.5 * (ph / 168.0), zc + rz * math.cos(a)))
            F += tube("body", "bone", pts, ra=[1.0, 1.05, 1.0, 0.8], rb=[0.8, 0.85, 0.8, 0.65], sides=3,
                      hint=(0, 1, 0), axis_fn=lambda p: np.array([0, p[1], zc]), hide_cos=0.45, ridge_out=True)
    mark("ribs")
    # 胸骨と鎖骨
    F += tube("body", "bone", [(0, 21.6, -2.7), (0, 14.2, -3.0)], ra=[1.15, 0.75],
              rb=[0.9, 0.65], sides=4, hint=(1, 0, 0), axis_fn=lambda p: np.array([0, p[1], p[2] + 2.0]),
              hide_cos=0.5)
    F += spike("body", "bone", (0, 14.4, -2.9), (0, -1.0, -0.25), 2.3, 0.6, sides=3, rings=1)
    for s in (-1, 1):
        F += tube("body", "bone", [(s * 0.6, 21.6, -2.5), (s * 5.0, 21.4, -0.2)],
                  ra=[0.85, 0.8], rb=[0.85, 0.8], sides=3, hint=(0, 1, 0),
                  axis_fn=lambda p: np.array([p[0] * 0.3, p[1] - 1.5, p[2] + 0.8]), hide_cos=0.7)
    mark("sternum")

    # ================================================================ 腕
    for s, nm in ((1, "left"), (-1, "right")):
        a, fa = f"{nm}_arm", f"{nm}_forearm"
        F += ellipsoid(a, "bone", (s * 5.5, 20.0, 0.0), (1.9, 1.8, 1.9), segs=4, rings=1)      # 肩の玉
        F += ellipsoid(a, "stone", (s * 6.4, 22.0, 0.0), (3.6, 1.5, 3.2), segs=6, rings=1, rot=rot_z(-s * 18))
        el = (s * 5.6, 14.6, 0.0)
        F += tube(a, "bone", [(s * 5.5, 19.4, 0.0), (s * 5.6, 17.0, 0.2), el], ra=[1.7, 1.5, 1.8],
                  rb=[1.7, 1.5, 1.7], sides=5, hint=(1, 0, 0))
        wr = (s * 5.5, 9.4, -1.2)
        F += tube(fa, "bone", [el, (s * 5.6, 11.8, -0.4), wr], ra=[1.75, 1.35, 1.25], rb=[1.65, 1.3, 1.2],
                  sides=5, hint=(1, 0, 0))
        for dx, dz, ln in ((-0.9, -0.2, 3.0), (0.0, -0.5, 3.6), (0.9, -0.2, 3.0)):
            F += spike(fa, "bone", (s * 5.5 + dx, 9.2, -1.2 + dz), (s * dx * 0.15, -1.0, -0.3), ln, 0.6, sides=3,
                       rings=1)
    mark("arms")

    # ================================================================ 脚
    for s, nm in ((1, "leg_l"), (-1, "leg_r")):
        F += ellipsoid(nm, "bone", (s * 2.4, 10.4, 0.1), (1.9, 1.7, 1.9), segs=4, rings=1)     # 股関節の玉
        F += tube(nm, "bone", [(s * 2.4, 10.0, 0.1), (s * 2.5, 7.6, -0.2), (s * 2.6, 5.0, -0.5)],
                  ra=[1.7, 1.4, 1.95], rb=[1.6, 1.4, 1.85], sides=4, hint=(1, 0, 0))
        F += tube(nm, "bone", [(s * 2.6, 5.0, -0.5), (s * 2.5, 3.0, -0.4), (s * 2.4, 1.1, -0.1)],
                  ra=[1.55, 1.1, 1.15], rb=[1.45, 1.05, 1.05], sides=4, hint=(1, 0, 0))
        F += tube(nm, "bone", [(s * 2.4, 1.1, 1.7), (s * 2.4, 1.0, -1.2), (s * 2.4, 0.7, -4.7)],
                  ra=[0.95, 1.0, 0.1], rb=[1.5, 1.7, 0.2], sides=4, hint=(0, 1, 0))
    mark("legs")
    if report is not None:
        report.extend(marks)

    # ================================================================ 盾 (socket_off の pivot が原点)
    # 局所: x が外へ (板の表面側)、y が上、z が前後。板は x≈0 の面で、下へ尖る。
    sf = []
    ys = [8.0, 2.5, -4.0, -9.5]
    hw = [4.1, 4.5, 3.3, 0.0]
    bulge = [0.4, 1.3, 1.0, 0.5]
    pts = []
    for y, h, b in zip(ys, hw, bulge):
        edge = 0.0 if y > -9 else 0.3
        pts.append([V(edge, y, -h), V(b, y, 0.0), V(edge, y, h)])
    sf += grid("shield", "shield", pts, (-6, 0, 0))
    outl = [(0, 8.0, -4.1), (0, 8.0, 4.1), (0, 2.5, 4.5), (0, -4.0, 3.3), (0.3, -9.5, 0.0), (0, -4.0, -3.3),
            (0, 2.5, -4.5)]
    back = [(-1.1, y, z) for x, y, z in outl]
    sf += poly("shield", "shield_back", back[::-1], (6, 0, 0))
    for i in range(len(outl)):
        j = (i + 1) % len(outl)
        sf += poly("shield", "shield_edge", [outl[i], outl[j], back[j], back[i]], (-0.5, 0, 0))
    sf += spike("shield", "bone", (1.1, 2.2, 0.0), (1.0, 0.1, 0.0), 3.4, 1.6, sides=5, rings=2, bend=(0.2, 0.6, 0),
                hint=(0, 0, 1))
    sf += spike("shield", "bone", (0.3, 7.8, 0.0), (0.4, 1.0, 0.0), 2.4, 0.9, sides=4, rings=1, hint=(0, 0, 1))
    # 握り (手のほうへ x の負の向き)
    sf += tube("shield", "bone", [(-1.1, 1.2, 0.0), (-2.4, 1.2, 0.0)], ra=[0.8, 0.8], rb=[0.8, 0.8], sides=4,
               hint=(0, 1, 0), caps=(False, True))
    extras["shield_tris"] = tri_count(sf)
    return F, sf, extras
