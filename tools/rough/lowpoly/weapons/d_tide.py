"""潮殻の三叉槍 3 案。珊瑚・真珠層・濡れた石、光は桃色 (真珠)。"""
from __future__ import annotations

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, feather, mirror_x, remat, at, V, rot_x, rot_y, rot_z
from paint import line_mask, edge_mask, spot_mask

TITAN = "tide"
GRIP = (0.0, 3.0, 0.0)


def shaft(r, top=11.0):
    f = []
    f += prism("guard", (0, -2.2, 0), (0, 0.4, 0), 1.0, 1.0, sides=6, caps=(False, False))
    f += prism("wrap", (0, 0.4, 0), (0, 4.8, 0), 1.12, 1.12, sides=6, caps=(False, False))
    f += prism("guard", (0, 4.8, 0), (0, top, 0), 1.0, 1.0, sides=6, caps=(False, True))
    f += block("trim", (0, 7.6, 0), (2.6, 0.7, 2.6))
    f += block("trim", (0, -0.1, 0), (2.4, 0.6, 2.4))
    # 石突き: 巻貝の先
    f += spike(BONE, "main", (0, -2.2, 0), (0, -1, 0), 2.2, 1.2, sides=6, rings=2, hint=(1, 0, 0))
    return f


# ---------------------------------------------------------------- A 珊瑚の枝槍
def a(r="common"):
    f = shaft(r)
    f += block("acc", (0, 11.2, 0), (3.8, 1.8, 3.0), taper=(0.8, 0.8))
    f += blade("main", [(11.8, -1.5, 1.5, 0.8, 0.8), (14.0, -1.9, 1.9, 0.9, 0.9), (17.0, -1.2, 1.2, 0.6, 0.6), (19.4, 0, 0, 0, 0)])
    for s in (1, -1):
        f += curve("acc", [(s * 1.0, 11.8, 0), (s * 3.2, 13.0, 0), (s * 5.0, 15.4, 0), (s * 5.6, 18.2, 0)],
                   [1.0, 0.9, 0.7, 0.3], [1.0, 0.9, 0.7, 0.3], sides=4)
        f += spike(BONE, "acc", (s * 3.9, 14.0, 0), (-s * 0.35, 1, 0), 3.0, 0.55, sides=4, rings=2, hint=(0, 0, 1))
        f += block("gem", (s * 1.7, 12.4, 0), (1.0, 1.0, 1.1), taper=(0.7, 0.7))
    if r in ("rare", "legend"):
        for s in (1, -1):
            f += spike(BONE, "acc", (s * 5.0, 15.6, 0), (s * 0.9, 0.9, 0), 2.8, 0.55, sides=4, rings=2, hint=(0, 0, 1))
        f += block("trim", (0, 12.4, 0), (5.2, 0.9, 3.8), taper=(0.9, 0.9))
        f += block("trim", (0, 2.2, 0), (2.8, 1.0, 2.8))
        f += block("trim", (0, -2.0, 0), (2.4, 0.8, 2.4))
    if r == "legend":
        # 後ろにも前にも広がる珊瑚の冠 (奥行きの枝)
        for s in (1, -1):
            f += spike(BONE, "acc", (0, 11.8, s * 1.0), (0, 0.9, s * 0.6), 5.2, 0.8, sides=4, rings=2, hint=(1, 0, 0))
            f += spike(BONE, "acc", (s * 2.0, 12.6, 0), (s * 0.5, 0.8, 0.3), 4.2, 0.6, sides=4, rings=2, hint=(0, 0, 1))
        f += block("gem", (0, 16.2, 0), (1.3, 1.3, 1.6), taper=(0.8, 0.8))
    return f


def _cx0(y):
    return 0.0 * y


def inlay_a(env, kind):
    if kind == "rare":
        return line_mask(env, ["main"], _cx0, 12.2, 13.8, 0.45)
    if kind == "legend":
        return line_mask(env, ["main"], _cx0, 14.6, 16.6, 0.45)
    if kind == "p5":
        return line_mask(env, ["main"], _cx0, 12.2, 18.4, 0.45, dash=(2.2, 1.2))
    if kind == "p10":
        return line_mask(env, ["main"], _cx0, 12.2, 19.0, 0.55) | spot_mask(env, ["acc"], (5.3, 17.0, 0), 1.8) | spot_mask(env, ["acc"], (-5.3, 17.0, 0), 1.8)
    return env["lit"] < -9


# ---------------------------------------------------------------- B 巻貝の穂と帆立の扇
def b(r="common"):
    f = shaft(r)
    f += block("acc", (0, 11.2, 0), (3.4, 1.6, 2.8), taper=(0.8, 0.8))
    # 巻貝: 段々に細くなる六角の殻 (縞は 1 列おきの材質)
    f += tube(BONE, "main", [V(0, 11.8, 0), V(0.3, 13.6, 0), V(0.0, 15.6, 0), V(0.2, 17.4, 0), V(0, 19.4, 0)],
              [1.9, 2.3, 1.7, 1.1, 0.0], [1.9, 2.3, 1.7, 1.1, 0.0], sides=6, hint=(1, 0, 0), phase=0, caps=(False, False),
              mats=["main", "edge", "main", "edge", "main", "edge"])
    for s in (1, -1):
        pts = [(0, 0), (1.5, 1.6), (2.2, 4.8), (0.8, 7.0), (-0.9, 5.8), (-1.2, 2.4)]
        pts = [(s * x, y) for x, y in pts]
        if s < 0:
            pts = pts[::-1]
        f += extrude("spine", pts, 1.1, c=(s * 1.8, 11.6, 0), rot=rot_z(-s * 26), mats=["edge", "spine", "spine", "edge", "spine", "spine"])
    f += block("gem", (0, 13.0, -1.2), (1.0, 1.0, 0.7))
    if r in ("rare", "legend"):
        f += block("trim", (0, 12.2, 0), (4.8, 0.9, 3.6), taper=(0.9, 0.9))
        f += block("trim", (0, 2.2, 0), (2.8, 1.0, 2.8))
        for s in (1, -1):
            f += spike(BONE, "acc", (s * 1.6, 11.8, 0), (s * 0.4, 1, 0), 3.2, 0.8, sides=4, rings=2, hint=(0, 0, 1))
    if r == "legend":
        for s in (1, -1):
            pts = [(0, 0), (1.8, 1.4), (2.8, 3.8), (1.0, 5.4), (-1.1, 4.6), (-1.5, 2.0)]
            pts = [(s * x, y) for x, y in pts]
            if s < 0:
                pts = pts[::-1]
            f += extrude("spine", pts, 0.9, c=(s * 3.4, 11.4, 0), rot=rot_z(-s * 62), mats=["edge", "spine", "spine", "edge", "spine", "spine"])
    return f


def inlay_b(env, kind):
    if kind == "rare":
        return line_mask(env, ["main"], _cx0, 13.0, 15.0, 0.4)
    if kind == "legend":
        return spot_mask(env, ["spine"], (3.8, 15.4, 0), 2.4) | spot_mask(env, ["spine"], (-3.8, 15.4, 0), 2.4)
    if kind == "p5":
        return edge_mask(env, ["edge"], 12.0, 19.0, dash=(2.0, 1.0))
    if kind == "p10":
        return edge_mask(env, ["edge"], 12.0, 19.4) | spot_mask(env, ["spine"], (5.0, 15.4, 0), 2.0) | spot_mask(env, ["spine"], (-5.0, 15.4, 0), 2.0)
    return env["lit"] < -9


# ---------------------------------------------------------------- C 三叉の牙 (返しつきの銛)
def c(r="common"):
    f = shaft(r)
    # 二枚貝の襟 (開いた半分の殻 2 枚、前後)
    for sz in (1, -1):
        pts = [(0, 0), (1.9, 0.8), (2.6, 3.2), (1.2, 5.2), (-1.2, 5.2), (-2.6, 3.2), (-1.9, 0.8)]
        f += extrude("main", pts, 0.8, c=(0, 10.6, sz * 1.5), rot=rot_x(-sz * 20), mats=["edge", "main", "main", "edge", "main", "main", "edge"])
    f += prism("trim", (0, 10.9, 0), (0, 11.5, 0), 1.5, 1.5, sides=6, caps=(False, False))
    # 中央の牙 + 返し
    f += spike(BONE, "edge", (0, 11.5, 0), (0, 1, 0), 7.8, 1.2, sides=4, rings=3, hint=(1, 0, 0))
    for s in (1, -1):
        f += at(extrude("main", [(0, 0), (1.5, -1.2), (0.0, 2.2)], 0.8), None, (s * 0.7, 15.2, 0))
    # 側の牙: 内へ曲がる銛
    for s in (1, -1):
        f += curve("acc", [(s * 1.0, 11.6, 0), (s * 5.0, 13.0, 0), (s * 6.0, 16.4, 0), (s * 4.6, 18.8, 0)],
                   [0.9, 0.85, 0.55, 0.0], [0.9, 0.85, 0.55, 0.0], sides=4)
        f += at(extrude("main", [(0, 0), (1.4, -1.0), (0.2, 2.4)], 0.8), None, (s * 6.0, 14.8, 0))
    f += block("gem", (0, 12.2, 0), (1.2, 1.0, 1.4), taper=(0.7, 0.7))
    if r in ("rare", "legend"):
        for s in (1, -1):
            f += at(extrude("main", [(0, 0), (1.3, -1.0), (0.1, 2.2)], 0.7), None, (s * 0.6, 13.0, 0))
            f += block("trim", (s * 3.0, 12.6, 0), (1.6, 0.9, 1.6))
        f += block("trim", (0, 2.2, 0), (2.8, 1.0, 2.8))
    if r == "legend":
        for s in (1, -1):
            f += spike(BONE, "acc", (s * 6.2, 15.0, 0), (s * 1.0, 0.6, 0), 3.2, 0.6, sides=4, rings=2, hint=(0, 0, 1))
            f += spike(BONE, "edge", (0, 11.6, s * 1.1), (0, 0.8, s * 0.7), 5.6, 0.8, sides=4, rings=2, hint=(1, 0, 0))
    return f


def inlay_c(env, kind):
    cx = _cx0
    if kind == "rare":
        return spot_mask(env, ["edge"], (0, 14.0, 0), 1.2)
    if kind == "legend":
        return spot_mask(env, ["acc"], (6.0, 15.0, 0), 1.2) | spot_mask(env, ["acc"], (-6.0, 15.0, 0), 1.2)
    if kind == "p5":
        return line_mask(env, ["edge"], cx, 12.0, 18.6, 0.4, dash=(2.0, 1.1))
    if kind == "p10":
        return line_mask(env, ["edge"], cx, 12.0, 19.0, 0.5) | spot_mask(env, ["acc"], (5.4, 17.0, 0), 1.6) | spot_mask(env, ["acc"], (-5.4, 17.0, 0), 1.6)
    return env["lit"] < -9


OPTS = dict(a=(a, inlay_a, ("main",)), b=(b, inlay_b, ("edge",)), c=(c, inlay_c, ("edge",)))
