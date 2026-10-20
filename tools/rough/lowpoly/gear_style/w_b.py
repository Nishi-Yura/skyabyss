"""様式 B「祭具の優美」(Ritual Grace): 岩鯨の武器 5 点。
原神・ゼルダ TotK 系。細く長い輪郭、金の縁、浮く環と垂れる布 (房) で動きを足し、石は淡い青灰、骨は象牙色。
大剣 = 細い長刃と鯨の尾びれの鍔、弓 = 先が尾びれに反る長弓、槍 = 柳葉の穂と背の環、杖 = 尾びれが抱く真珠、短剣 = 波刃。"""
from __future__ import annotations

import math

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, feather, remat, at, V, rot_x, rot_y, rot_z
import d_thunder as T
from lpkit import ellipsoid

GRIP = dict(greatsword=(0, 1.8, 0), spear=(0, 3.0, 0), staff=(0, 3.0, 0), dagger=(0, 1.0, 0), bow=(5.0, 8.0, 0))
STAGES = T.STAGES


def circle(cx, cy, R, z=0.0, a0=0, a1=360, n=12):
    return [(cx + R * math.sin(math.radians(a0 + (a1 - a0) * i / n)), cy + R * math.cos(math.radians(a0 + (a1 - a0) * i / n)), z) for i in range(n + 1)]


def ribbon(c, length, w, curl=1.2, mat="cloth", th=0.3, sign=1):
    """垂れる布: 上が細く下が広がり、先が割れる。広い面は ±Z。"""
    pts = [(0, 0), (w * 0.5, -length * 0.1), (w * 0.62 + curl * 0.4, -length * 0.55), (curl, -length),
           (curl * 0.2, -length * 0.82), (-w * 0.4 + curl * 0.4, -length * 0.9), (-w * 0.55, -length * 0.5), (-w * 0.4, -length * 0.1)]
    pts = [(sign * x, y) for x, y in pts]
    if sign < 0:
        pts = pts[::-1]
    return extrude(mat, pts, th, c=c)


def hilt(y0=-1.8, y1=3.4):
    f = prism("grip", (0, y0, 0), (0, y1, 0), 1.0, 1.0, sides=6, caps=(False, False))
    for y in (-0.4, 1.0, 2.4):
        f += prism("trim", (0, y, 0), (0, y + 0.5, 0), 1.25, 1.25, sides=6, caps=(False, False))
    return f


def fluke(sign, y, spread=7.4, rot=0.0, mat="guard", rim="trim"):
    pts = [(0.0, 0.0), (3.0, -0.8), (spread - 0.4, 1.0), (spread, 3.6), (spread - 3.0, 2.6), (1.2, 2.0)]
    pts = [(sign * x, yy) for x, yy in pts]
    if sign < 0:
        pts = pts[::-1]
    return extrude(mat, pts, 1.3, c=(0, y, 0), mats=[rim, mat, mat, rim, mat, mat])


def greatsword(tier=1):
    f = hilt()
    f += ellipsoid(BONE, "acc", (0, -2.7, 0), (1.5, 1.5, 1.5), segs=8, rings=3)
    f += block("trim", (0, -1.8, 0), (2.4, 0.5, 2.4))
    f += ribbon((0.0, -3.8, 0.0), 6.5, 1.6, curl=1.4, sign=1)
    sp = 5.6 if tier == 0 else 7.4 if tier == 1 else 9.6
    f += fluke(1, 3.6, spread=sp) + fluke(-1, 3.6, spread=sp)
    if tier >= 2:   # 尾びれが二重になる
        f += fluke(1, 5.0, spread=6.4) + fluke(-1, 5.0, spread=6.4)
    f += block("guard", (0, 4.0, 0), (2.8, 2.2, 3.0), taper=(0.9, 0.9))
    f += block("gem", (0, 4.2, -1.58), (1.3, 1.5, 0.35), taper=(0.8, 0.8))
    if tier >= 1:   # 日輪の環
        f += curve("trim", circle(0, 6.6, 2.8, -0.0, 0, 360, 12), 0.5, 0.5, sides=4, hint=(0, 0, 1))
    st = [(6.0, -1.9, 1.9, 1.0, 1.0, 0.0), (11.0, -2.1, 2.1, 1.0, 1.0, 0.3), (16.0, -1.8, 1.8, 0.9, 0.9, 0.7),
          (19.6, -1.0, 1.0, 0.6, 0.6, 1.0), (22.4, 0, 0, 0, 0, 1.4)]
    f += blade("main", st)
    f += block("vein", (0.05, 12.4, -1.07), (0.55, 10.5 if tier >= 1 else 5.0, 0.2))
    if tier >= 1:
        for s in (1, -1):
            f += block("trim", (s * 7.2, 6.4, 0), (1.0, 1.0, 1.4), rot=rot_z(45), taper=(0.9, 0.9))
        f += block("trim", (0, 7.4, 0), (4.4, 0.7, 2.2))
    if tier >= 2:
        # 刃の背に浮く大きな環と、環に沿う小さな玉、二本目の吹き流し
        f += curve("trim", circle(1.0, 15.0, 6.2, 1.6, 20, 340, 16), 0.5, 0.5, sides=4, hint=(0, 0, 1))
        for a in (60, 300):
            x, y = 1.0 + 6.2 * math.sin(math.radians(a)), 15.0 + 6.2 * math.cos(math.radians(a))
            f += ellipsoid(BONE, "gem", (x, y, 1.6), (0.9, 0.9, 0.9), segs=6, rings=2)
        f += ribbon((1.4, -3.8, 0.4), 7.5, 1.6, curl=-1.2, sign=-1)
    return f


def bow(tier=1, stage=0):
    CY = T.CY
    f = []
    f += prism("grip", (5.0, CY - 2.4, 0), (5.0, CY + 2.4, 0), 0.95, 0.95, sides=6, caps=(False, False), hint=(0, 0, 1))
    for dy in (-1.6, 0.0, 1.6):
        f += prism("trim", (5.0, CY + dy, 0), (5.0, CY + dy + 0.4, 0), 1.2, 1.2, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += ellipsoid(BONE, "gem", (6.0, CY, 0), (0.8, 1.0, 1.0), segs=6, rings=2)
    pull = T.TIP_PULL[stage]
    up = [(5.0, 10.4, 0), (4.4, 12.8, 0), (2.4, 15.4, 0), (-0.4 - pull * 0.5, 17.2, 0), (-3.0 - pull, 17.4, 0)]
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    for path in (up, lo):
        sg = 1 if path is up else -1
        f += curve("main", path, [0.85, 0.75, 0.6, 0.45, 0.3], [0.95, 0.85, 0.7, 0.55, 0.4], sides=4, caps=(False, False), hint=(0, 0, 1))
        px, py, _ = path[1]
        f += block("trim", (px, py, 0), (1.2, 1.0, 1.4), rot=rot_z(sg * 16), taper=(0.9, 0.9))
        tx, ty, _ = path[-1]
        # 先の尾びれ
        pts = [(0, 0), (-1.6, sg * 0.9), (-3.0, sg * 2.2), (-1.0, sg * 1.7), (0.6, sg * 1.2)]
        f += at(extrude("edge", pts if sg > 0 else pts[::-1], 0.6), None, (tx + 0.4, ty, 0))
        f += ellipsoid(BONE, "acc", (tx - 0.2, ty + sg * 0.2, 0), (0.7, 0.7, 0.7), segs=6, rings=2)
    f += ribbon((5.0, CY - 2.6, 0.0), 6.0, 1.3, curl=1.0, sign=1)
    f += T.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))
    if tier >= 2:
        f += curve("trim", circle(8.6, CY, 3.0, 0, 0, 360, 10), 0.3, 0.3, sides=4, hint=(1, 0, 0))
    return f


def spear(tier=1):
    f = []
    f += prism("grip", (0, -2.4, 0), (0, 11.6, 0), 0.9, 0.9, sides=6, caps=(False, False))
    for y in (0.4, 2.6, 6.4, 10.8):
        f += prism("trim", (0, y, 0), (0, y + 0.6, 0), 1.2, 1.2, sides=6, caps=(False, False))
    f += prism("wrap", (0, 3.2, 0), (0, 6.0, 0), 1.15, 1.15, sides=6, caps=(False, False))
    f += spike(BONE, "trim", (0, -2.3, 0), (0, -1, 0), 2.6, 0.7, sides=4, rings=1, hint=(0, 0, 1))
    f += block("guard", (0, 11.9, 0), (2.6, 1.4, 2.6), taper=(0.8, 0.8))
    st = [(12.4, -1.1, 1.1, 0.7, 0.7), (14.6, -2.2, 2.2, 0.9, 0.9), (17.4, -1.8, 1.8, 0.8, 0.8), (20.0, -0.7, 0.7, 0.4, 0.4), (22.4, 0, 0, 0, 0)]
    f += blade("main", st)
    f += block("vein", (0, 16.0, -0.92), (0.5, 4.2, 0.2))
    # 背の大きな環 (半月)
    f += curve("trim", circle(0, 16.2, 4.8, 1.8, 20, 340, 14), 0.55, 0.55, sides=4, hint=(0, 0, 1))
    for a in (60, 300):
        x, y = 4.8 * math.sin(math.radians(a)), 16.2 + 4.8 * math.cos(math.radians(a))
        f += ellipsoid(BONE, "acc", (x, y, 1.8), (0.9, 0.9, 0.9), segs=6, rings=2)
    # 吹き流し
    f += ribbon((0.8, 11.4, 0.0), 10.0, 1.8, curl=2.2, sign=1)
    if tier >= 2:
        f += ribbon((-0.8, 11.0, 0.4), 8.0, 1.6, curl=-1.8, sign=-1, mat="acc")
    return f


def staff(tier=1):
    f = []
    f += prism("grip", (0, -2.6, 0), (0, 13.0, 0), 0.9, 0.9, sides=6, caps=(False, False))
    for y in (-0.6, 3.0, 8.0, 12.2):
        f += prism("trim", (0, y, 0), (0, y + 0.6, 0), 1.25, 1.25, sides=6, caps=(False, False))
    f += prism("wrap", (0, 4.4, 0), (0, 7.0, 0), 1.2, 1.2, sides=6, caps=(False, False))
    f += spike(BONE, "trim", (0, -2.4, 0), (0, -1, 0), 2.4, 0.8, sides=4, rings=1, hint=(0, 0, 1))
    f += block("guard", (0, 13.4, 0), (2.6, 1.2, 2.6), taper=(0.8, 0.8))
    # 尾びれの冠: 2 本の三日月が真珠を抱く
    for s in (1, -1):
        path = [(s * 0.6, 14.0, 0), (s * 2.6, 14.8, 0), (s * 4.2, 17.0, 0), (s * 3.6, 19.8, 0), (s * 1.6, 21.4, 0)]
        f += curve("edge", path, [0.9, 0.9, 0.8, 0.6, 0.35], [1.0, 1.0, 0.9, 0.7, 0.45], sides=4, hint=(0, 0, 1))
        f += ellipsoid(BONE, "trim", (s * 1.6, 21.6, 0), (0.7, 0.7, 0.7), segs=6, rings=2)
    f += ellipsoid(BONE, "gem", (0, 17.8, 0), (2.0, 2.0, 2.0), segs=8, rings=3)
    f += curve("trim", circle(0, 17.8, 3.0, -0.0, 0, 360, 10), 0.3, 0.3, sides=4, hint=(0, 0, 1))
    f += ribbon((1.0, 13.2, 0.0), 7.5, 1.4, curl=1.6, sign=1)
    f += ribbon((-1.0, 13.2, 0.4), 6.0, 1.2, curl=-1.2, sign=-1)
    if tier >= 2:
        f += curve("trim", circle(0, 17.8, 5.2, 0.0, 30, 330, 12), 0.35, 0.35, sides=4, hint=(0, 0, 1))
    return f


def dagger(tier=1):
    f = hilt(-1.8, 2.4)
    f = [x for x in f]
    f += curve("trim", circle(0, -3.6, 1.6, 0, 0, 360, 10), 0.4, 0.4, sides=4, hint=(0, 0, 1))
    f += ribbon((0.0, -5.0, 0.0), 5.0, 1.3, curl=1.0, sign=1)
    for s in (1, -1):
        path = [(s * 0.6, 2.6, 0), (s * 2.6, 2.4, 0), (s * 3.6, 3.6, 0), (s * 3.2, 4.8, 0)]
        f += curve("trim", path, [0.7, 0.6, 0.5, 0.25], [0.8, 0.7, 0.6, 0.3], sides=4, hint=(0, 0, 1))
    f += block("gem", (0, 3.1, -1.1), (1.0, 1.2, 0.3), taper=(0.8, 0.8))
    st = [(3.6, -1.4, 1.4, 0.8, 0.8, 0.0), (6.0, -1.7, 1.5, 0.8, 0.8, 0.9), (8.4, -1.4, 1.7, 0.8, 0.8, -0.9),
          (10.8, -1.7, 1.4, 0.7, 0.7, 0.9), (13.0, -1.1, 1.1, 0.5, 0.5, -0.6), (15.0, 0, 0, 0, 0, 0.2)]
    f += blade("main", st)
    f += block("vein", (0.0, 8.4, -0.82), (0.45, 6.0, 0.2))
    return f
