"""潮招きの爪 (潮殻)。真珠の爪 3 本が手のように開き、握りの根元に珊瑚の房。y -1..17、握り y 2.5。"""
from __future__ import annotations

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, at, V, rot_z
from wt_common import blade_inlay, cx_interp

TITAN = "tide"
GRIP = (0.0, 2.5, 0.0)
NAME = "潮招きの爪"
ID = "tidecaller_claw"
EDGE = ("main",)
CX = cx_interp([6.2, 9, 12.5, 15, 17], [0, 0.2, 0.8, 1.6, 2.4])


def faces(r="common"):
    f = []
    f += prism("wrap", (0, -0.4, 0), (0, 4.4, 0), 1.2, 1.1, sides=6, caps=(False, False))
    f += block("guard", (0, -0.6, 0), (3.2, 1.0, 2.8), taper=(0.7, 0.7), skip=("u",))
    f += block("guard", (0, 4.8, 0), (4.0, 1.6, 3.2), taper=(0.9, 0.9))
    # 中央の反った爪
    f += blade("main", [(5.6, -1.6, 1.6, 0.9, 0.9, 0), (9.0, -1.9, 1.8, 1.0, 1.0, 0.2), (12.5, -1.3, 1.1, 0.7, 0.7, 0.8),
                        (15.0, -0.7, 0.5, 0.4, 0.4, 1.6), (17.0, 0, 0, 0, 0, 2.4)])
    # 脇の爪 2 本 (内へ巻く)
    for s in (1, -1):
        f += curve("main", [(s * 1.6, 5.4, 0), (s * 3.8, 7.4, 0), (s * 4.3, 10.6, 0), (s * 3.0, 13.4, 0)],
                   [1.1, 1.0, 0.7, 0.0], [1.0, 0.95, 0.65, 0.0], sides=4)
    # 珊瑚の房 (握りの根元、前へ)
    f += block("acc", (0, 6.0, -0.9), (3.0, 1.6, 1.6), taper=(0.7, 0.8))
    f += spike(BONE, "acc", (-0.8, 6.6, -1.0), (-0.3, 1, -0.2), 3.0, 0.7, sides=4, rings=2, hint=(1, 0, 0))
    f += spike(BONE, "acc", (0.9, 6.6, -1.0), (0.3, 1, -0.2), 2.4, 0.6, sides=4, rings=2, hint=(1, 0, 0))
    f += block("gem", (0, 5.6, 0.0), (1.3, 1.0, 3.4), taper=(0.8, 0.8))
    if r in ("rare", "legend"):
        for s in (1, -1):
            f += spike(BONE, "main", (s * 4.2, 9.0, 0), (s * 0.9, 0.8, 0), 3.0, 0.7, sides=4, rings=2, hint=(0, 0, 1))
        f += block("trim", (0, 5.8, 0), (4.8, 0.8, 3.4), taper=(0.9, 0.9))
        f += block("trim", (0, -1.2, 0), (3.2, 0.7, 2.8))
    if r == "legend":
        # 珊瑚の冠: 爪のあいだに 2 本の枝と、外側の小さな爪
        for s in (1, -1):
            f += spike(BONE, "acc", (s * 1.2, 6.6, 0.4), (s * 0.35, 1, 0), 6.0, 0.8, sides=4, rings=3, hint=(0, 0, 1))
            f += curve("main", [(s * 2.2, 5.2, 0), (s * 5.4, 6.0, 0), (s * 6.4, 8.0, 0)],
                       [0.9, 0.8, 0.0], [0.9, 0.8, 0.0], sides=4)
    return f


inlay = blade_inlay(["main"], CX, (6.2, 8.2), (8.6, 11.4), 6.2, 16.0, 0.5,
                    extra_spots=[(["acc"], (1.0, 9.0, 0), 1.6)])
