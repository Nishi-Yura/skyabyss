"""珊瑚の銛 (潮殻)。細い真珠の穂先に、片側へ偏った珊瑚の返し。柄に海藻の縄の輪。y -3..19、握り y 3。"""
from __future__ import annotations

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, at, V, rot_z
from wt_common import blade_inlay, cx_interp

TITAN = "tide"
GRIP = (0.0, 3.0, 0.0)
NAME = "珊瑚の銛"
ID = "coral_harpoon"
EDGE = ("main",)
CX = cx_interp([12, 19], [0, 0])


def barb(s, y, ln=3.4, mat="acc"):
    return spike(BONE, mat, (s * 0.9, y, 0), (s * 0.9, 0.55, 0), ln, 0.75, sides=4, rings=2, hint=(0, 0, 1))


def faces(r="common"):
    f = []
    f += prism("guard", (0, -2.2, 0), (0, 0.4, 0), 0.9, 0.9, sides=6, caps=(False, False))
    f += prism("wrap", (0, 0.4, 0), (0, 5.0, 0), 1.05, 1.05, sides=6, caps=(False, False))
    f += prism("guard", (0, 5.0, 0), (0, 11.6, 0), 0.9, 0.9, sides=6, caps=(False, True))
    f += spike(BONE, "main", (0, -2.2, 0), (0, -1, 0), 0.8, 1.0, sides=6, rings=1, hint=(1, 0, 0))
    # 縄の輪 (左へ垂れる)
    f += curve("wrap", [(0, 9.2, 0), (-2.8, 8.2, 0), (-3.6, 5.8, 0), (-2.2, 4.0, 0), (0, 4.8, 0)],
               [0.55] * 5, [0.55] * 5, sides=4)
    # 襟と穂先
    f += block("guard", (0, 11.8, 0), (3.2, 1.6, 2.8), taper=(0.8, 0.8))
    f += block("gem", (0, 12.8, -0.3), (1.3, 1.1, 1.5), taper=(0.8, 0.8))
    f += blade("main", [(12.4, -1.2, 1.2, 0.7, 0.7), (14.6, -1.6, 1.6, 0.8, 0.8), (17.0, -0.9, 0.9, 0.5, 0.5), (19.0, 0, 0, 0, 0)])
    # 返し: 右に 2 本、左に 1 本 (互い違い)
    f += barb(1, 13.4, 4.0)
    f += barb(1, 15.8, 3.0)
    f += barb(-1, 14.4, 3.6)
    if r in ("rare", "legend"):
        f += barb(-1, 16.8, 2.6)
        f += block("trim", (0, 12.6, 0), (4.2, 0.9, 3.2), taper=(0.9, 0.9))
        f += block("trim", (0, 2.2, 0), (2.5, 1.0, 2.5))
        f += block("trim", (0, -1.6, 0), (2.2, 0.7, 2.2))
    if r == "legend":
        # 奥行きへ開く珊瑚の冠 (前後に 2 本ずつ)
        for s in (1, -1):
            f += spike(BONE, "acc", (0, 12.2, s * 0.9), (0, 0.7, s * 1.0), 4.6, 0.8, sides=4, rings=2, hint=(1, 0, 0))
            f += spike(BONE, "acc", (0, 13.8, s * 0.8), (s * 0.3, 0.5, s * 1.0), 3.2, 0.6, sides=4, rings=2, hint=(1, 0, 0))
        f += block("gem", (0, 16.6, 0), (1.2, 1.2, 1.5), taper=(0.8, 0.8))
    return f


inlay = blade_inlay(["main"], CX, (12.8, 14.2), (14.8, 17.0), 12.8, 18.6, 0.45,
                    extra_spots=[(["acc"], (3.6, 14.2, 0), 1.8), (["acc"], (-3.4, 15.2, 0), 1.6)])
