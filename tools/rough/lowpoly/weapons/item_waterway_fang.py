"""水路の牙 (岩鯨)。反った 1 本の牙の短剣。石の鍔は小さな肋 2 本。y 0..16、握り y 2.5。"""
from __future__ import annotations

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, at, V, rot_z
from wt_common import blade_inlay, cx_interp

TITAN = "whale"
GRIP = (0.0, 2.5, 0.0)
NAME = "水路の牙"
ID = "waterway_fang"
EDGE = ("edge",)
CX = cx_interp([5.8, 8, 11, 14], [0, 0.4, 1.3, 2.5])


def faces(r="common"):
    f = []
    f += prism("grip", (0, 0.4, 0), (0, 1.2, 0), 1.1, 1.0, sides=6, caps=(False, False))
    f += prism("wrap", (0, 1.2, 0), (0, 4.6, 0), 1.2, 1.1, sides=6, caps=(False, False))
    f += block("guard", (0, 0.2, 0), (3.2, 1.2, 2.8), taper=(0.7, 0.7), skip=("u",))
    f += block("guard", (0, 4.9, 0), (3.6, 1.8, 3.0), taper=(0.9, 0.9))
    for s in (1, -1):
        f += curve("guard", [(s * 1.2, 5.0, 0), (s * 3.2, 4.8, 0), (s * 4.5, 5.8, 0), (s * 4.8, 7.2, 0)],
                   [1.1, 1.0, 0.8, 0.0], [1.2, 1.1, 0.85, 0.0], sides=4)
    f += block("gem", (0, 6.1, -0.2), (1.3, 1.0, 1.5), taper=(0.8, 0.8))
    st = [(5.8, -2.2, 2.2, 1.1, 1.1, 0.0), (8.0, -2.6, 2.4, 1.2, 1.2, 0.4), (11.0, -2.2, 1.8, 1.0, 1.0, 1.3),
          (14.0, -1.3, 1.0, 0.6, 0.6, 2.5), (16.0, 0, 0, 0, 0, 3.6)]
    f += blade("main", st)
    # 背の副牙 (根元から)
    f += spike(BONE, "edge", (-2.3, 7.0, 0), (-0.5, 1, 0), 4.2, 1.0, sides=4, rings=2, hint=(0, 0, 1))
    f += block("acc", (-2.2, 9.6, 0), (1.1, 1.1, 1.5), taper=(0.5, 0.7))
    if r in ("rare", "legend"):
        f += block("trim", (0, 6.1, 0), (3.8, 0.9, 2.6), taper=(0.9, 0.9))
        f += block("trim", (0, -0.5, 0), (3.2, 0.8, 2.8))
        f += spike(BONE, "edge", (2.0, 7.2, 0), (0.55, 1, 0), 3.0, 0.7, sides=4, rings=2, hint=(0, 0, 1))
        f += block("acc", (-1.6, 12.2, 0), (1.0, 1.0, 1.4), taper=(0.5, 0.7))
    if r == "legend":
        # 第二の牙 (刃に寄り添う長い牙) と鍔の石の翼
        f += spike(BONE, "main", (2.7, 7.6, 0), (0.25, 1, 0), 7.6, 1.1, sides=4, rings=3, hint=(0, 0, 1), bend=(1.4, 0, 0))
        for s in (1, -1):
            f += block("spine", (s * 2.7, 4.1, 0), (1.6, 3.0, 1.5), rot=rot_z(-s * 16), taper=(0.5, 0.6))
    return f


inlay = blade_inlay(["main"], CX, (6.0, 8.0), (8.4, 11.0), 6.0, 15.0, 0.5,
                    extra_spots=[(["main"], (3.0, 11.0, 0), 1.5)])
