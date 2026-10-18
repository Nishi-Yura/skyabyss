"""珊瑚の盾 (潮殻)。帆立の貝殻の盾。濡れた石の縁、真珠色の面に扇の筋、中央に真珠の瘤、縁に珊瑚の鋲。
x ±5.6, y -1..17 (中心 y 8)、広い面は ±Z。"""
from __future__ import annotations

import numpy as np

from wkit import *  # noqa: F401,F403
from wkit import block, prism, curve, extrude, at, V, rot_z
from paint import spot_mask, edge_mask

TITAN = "tide"
GRIP = (0.0, 8.0, 0.0)
NAME = "珊瑚の盾"
ID = "coral_shield"
EDGE = ("main",)

OUT = [(0, -1.0), (3.8, 2.4), (5.6, 7.4), (4.8, 12.4), (2.6, 15.8), (0, 17.0),
       (-2.6, 15.8), (-4.8, 12.4), (-5.6, 7.4), (-3.8, 2.4)]


def shrink(k):
    return [(x * k, 8.0 + (y - 8.0) * k) for x, y in OUT]


def faces(r="common"):
    f = []
    # 縁 (濡れた石) と面 (真珠、少し出る)
    f += extrude("guard", OUT, 2.4, c=(0, 0, 0))
    f += extrude("main", shrink(0.82), 3.2, c=(0, 0, 0), mats=["edge"] * 10)
    # 扇の筋 (前)。下の要から 5 本
    for tx, ty in ((-3.6, 11.4), (-1.8, 13.8), (0.0, 14.4), (1.8, 13.8), (3.6, 11.4)):
        f += curve("spine", [(0, 0.8, -1.55), (tx, ty, -1.65)], [0.6, 0.25], [0.5, 0.25], sides=4, caps=(False, False))
    # 中央の瘤 (光る真珠)
    f += block("gem", (0, 8.4, -1.9), (2.0, 2.0, 1.0), taper=(0.7, 0.7))
    # 縁の珊瑚の鋲
    for x, y in ((4.9, 8.5), (-4.9, 8.5), (3.8, 12.4), (-3.8, 12.4), (0, 16.2)):
        f += block("acc", (x, y, -1.15), (1.1, 1.1, 0.7), taper=(0.6, 0.6))
    # 裏: 握りと腕の帯
    f += block("grip", (0, 8.0, 1.9), (5.6, 1.0, 1.0))
    f += block("dark", (0, 11.2, 1.7), (1.4, 5.0, 0.8))
    if r in ("rare", "legend"):
        # 縁の金属の帯 (上の弧と下の先) と、添えの鋲
        f += block("trim", (0, 15.6, 0), (4.4, 1.0, 3.0), taper=(0.8, 0.9))
        f += block("trim", (0, -0.2, 0), (2.2, 1.0, 3.0), taper=(1.0, 0.9))
        for s in (1, -1):
            f += block("trim", (s * 5.2, 6.0, 0), (0.9, 2.4, 3.0))
            f += block("trim", (s * 4.4, 11.0, 0), (0.9, 2.0, 3.0), rot=rot_z(-s * 14))
    if r == "legend":
        # 珊瑚の冠: 上の縁から立ち上がる 3 本の枝と、肩の側枝
        for dx, ln, dxx in ((0, 1.8, 0.0), (-2.4, 1.4, -0.5), (2.4, 1.4, 0.5)):
            f += spike(BONE, "acc", (dx, 16.4 - 0.6 * abs(dx) * 0.5, -0.3), (dxx, 1, 0), 1.0 + ln * 1.4, 0.8, sides=4, rings=2, hint=(0, 0, 1))
        for s in (1, -1):
            f += spike(BONE, "acc", (s * 5.2, 11.0, 0), (s * 0.8, 0.6, 0), 2.4, 0.7, sides=4, rings=2, hint=(0, 0, 1))
    return f


CEN = (0, 8.4, -1.9)


def inlay(env, kind):
    if kind == "rare":
        return spot_mask(env, ["main"], CEN, 1.7)
    if kind == "legend":
        return spot_mask(env, ["acc"], (0, 16.2, -1.15), 1.4) | spot_mask(env, ["acc"], (4.9, 8.5, -1.15), 1.2) | spot_mask(env, ["acc"], (-4.9, 8.5, -1.15), 1.2)
    rr = np.hypot(env["X"], env["Y"] - 8.4)
    ring = np.abs(rr - 3.6) < 0.35
    ok = np.isin(env["mats"], ["main"]) & (env["N"][..., 2] < -0.5)
    if kind == "p5":
        ang = np.arctan2(env["Y"] - 8.4, env["X"])
        return ok & ring & (np.sin(ang * 6.0) > 0.35)
    if kind == "p10":
        ang = np.arctan2(env["Y"] - 8.4, env["X"])
        return ok & (ring | ((np.abs(rr - 5.0) < 0.35) & (np.sin(ang * 8.0) > 0.0))) | spot_mask(env, ["spine"], (0, 13.0, -1.7), 0.9)
    return env["lit"] < -9
