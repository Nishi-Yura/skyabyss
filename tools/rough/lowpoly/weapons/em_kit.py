"""余燼の武器の共通部品。A の量塊 (大きな玄武岩の面) + C の鍛冶場の使い込み (冷めゆく溶岩の筋・灰・鉄の帯・鎖) + 火とかげの背びれ。
座標は他の武器と同じ。材質: main = 玄武岩 / edge・spine = 鉄 / guard・acc = 風化した岩 / grip・wrap = 焦げた柄 / chain = 鎖 / gem = 溶岩の割れ目 / trim = 階級の縁。"""
from __future__ import annotations

import math

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, V, rot_x, rot_y, rot_z, nrm


def chain(p0, p1, n=4, sag=0.0, mat="chain"):
    """鎖: p0 から p1 へ、輪を交互に 90 度回して n 個。sag = 途中の垂れ。"""
    p0, p1 = V(*p0), V(*p1)
    out = []
    d = p1 - p0
    for i in range(n):
        u = (i + 0.5) / n
        c = p0 + d * u + V(0, -sag * math.sin(math.pi * u), 0)
        L = float(np.linalg.norm(d)) / n * 1.25
        a = nrm(d)
        ang = math.atan2(a[0], a[1])
        R = rot_z(-math.degrees(ang))
        if i % 2:
            R = R @ rot_y(90)
        out += block(mat, tuple(c), (0.55, L, 1.1), rot=R, taper=(1.0, 1.0))
    return out


def ridge(base, dirv, n, L0, step, r=0.9, mat="main", shrink=0.8):
    """火とかげの背びれ: base から step ずつ進みながら n 本の棘が並ぶ。dirv = 棘の向き、step = (dx, dy, dz)。"""
    out = []
    for i in range(n):
        p = V(*base) + V(*step) * i
        out += spike(BONE, mat, p, dirv, L0 * (shrink ** i), r * (shrink ** (i * 0.6)), sides=4, rings=1, hint=(0, 0, 1))
    return out


def band(c, r=1.3, h=0.7, axis="y", mat="spine", sides=6):
    c = V(*c)
    if axis == "y":
        return prism(mat, tuple(c - V(0, h / 2, 0)), tuple(c + V(0, h / 2, 0)), r, r, sides=sides, caps=(False, False), hint=(0, 0, 1))
    return prism(mat, tuple(c - V(h / 2, 0, 0)), tuple(c + V(h / 2, 0, 0)), r, r, sides=sides, caps=(False, False), hint=(0, 1, 0))
