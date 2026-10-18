"""番兵長の鉾 (岩鯨)。鯨の肋が下へ巻く 2 本の鉤と、反った牙の穂先。石の柄に苔の縄。y -3..19、握り y 3。"""
from __future__ import annotations

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, at, V, rot_z
from wt_common import blade_inlay, cx_interp

TITAN = "whale"
GRIP = (0.0, 3.0, 0.0)
NAME = "番兵長の鉾"
ID = "captain_pike"
EDGE = ("edge",)
CX = cx_interp([12.2, 14, 16.6, 19], [0, 0.2, 0.8, 1.7])


def spike_up(x, y, ln):
    return spike(BONE, "edge", (x, y, 0), (-0.35, 1, 0), ln, 0.8, sides=4, rings=2, hint=(0, 0, 1))


def faces(r="common"):
    f = []
    f += prism("grip", (0, -2.0, 0), (0, 0.4, 0), 1.0, 1.0, sides=6, caps=(False, False))
    f += prism("wrap", (0, 0.4, 0), (0, 5.2, 0), 1.15, 1.15, sides=6, caps=(False, False))
    f += prism("grip", (0, 5.2, 0), (0, 11.0, 0), 1.0, 1.0, sides=6, caps=(False, True))
    f += block("guard", (0, -2.5, 0), (2.6, 1.0, 2.6), taper=(0.6, 0.6), skip=("u",))
    # 襟: 石の段 + 苔のこぶ
    f += block("guard", (0, 11.2, 0), (3.8, 1.8, 3.2), taper=(0.85, 0.85))
    f += block("acc", (0, 10.0, 0), (2.6, 1.0, 2.6), taper=(0.9, 0.9))
    f += block("gem", (0, 12.2, -0.4), (1.5, 1.2, 1.6), taper=(0.8, 0.8))
    # 穂先: 反った牙
    st = [(12.2, -1.7, 1.7, 0.9, 0.9, 0.0), (14.0, -2.1, 2.0, 1.0, 1.0, 0.2), (16.6, -1.5, 1.2, 0.7, 0.7, 0.8),
          (18.2, -0.7, 0.6, 0.4, 0.4, 1.3), (19.0, 0, 0, 0, 0, 1.7)]
    f += blade("main", st)
    # 肋の鉤: 2 本、下へ巻く
    for s in (1, -1):
        f += curve("edge", [(s * 1.3, 12.8, 0), (s * 3.8, 14.0, 0), (s * 5.4, 13.0, 0), (s * 5.6, 10.8, 0)],
                   [1.2, 1.05, 0.7, 0.0], [1.3, 1.1, 0.75, 0.0], sides=4)
    if r in ("rare", "legend"):
        f += block("trim", (0, 12.4, 0), (4.8, 0.9, 3.4), taper=(0.9, 0.9))
        f += block("trim", (0, 2.2, 0), (2.7, 1.0, 2.7))
        f += block("trim", (0, -1.4, 0), (2.5, 0.7, 2.5))
        f += spike_up(-1.8, 13.4, 3.4)
        f += block("acc", (-2.1, 15.6, 0), (1.1, 1.2, 1.4), taper=(0.5, 0.7))
    if r == "legend":
        # 背びれの冠: 穂先の背に並ぶ骨の板 2 枚と、襟から開く石の翼
        for k, (y, h) in enumerate(((13.4, 3.4), (16.0, 2.6))):
            f += at(extrude("edge", [(0, 0), (1.5, h * 0.25), (0.5, h), (-1.0, h * 0.5)], 0.8), None, (-2.6 + 0.3 * k, y, 0))
        for s in (1, -1):
            f += block("spine", (s * 2.6, 10.2, 0), (1.5, 2.8, 1.5), rot=rot_z(-s * 16), taper=(0.5, 0.6))
            f += block("trim", (s * 5.6, 10.8, 0), (1.2, 1.0, 1.4), taper=(0.7, 0.7))
    return f


inlay = blade_inlay(["main"], CX, (12.4, 14.0), (14.4, 17.0), 12.4, 18.2, 0.5,
                    extra_spots=[(["edge"], (5.4, 12.0, 0), 1.6), (["edge"], (-5.4, 12.0, 0), 1.6)])
