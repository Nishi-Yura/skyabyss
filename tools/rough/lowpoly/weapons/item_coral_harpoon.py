"""珊瑚の銛 (作り直し)。細身の真珠層の穂先に、大きな板の返し 2 枚 (互い違い)、柄に太い海藻の縄の輪と垂れる縄尾、薔薇色の帯。
y -3..21、握り y 3 (変えない)。
階級: 通常 = 返し 2 枚 / レア = 返し 4 枚 + 珊瑚の襟 + 帯 / 伝説 = 大きな返し 6 枚 + 後ろへ広がる珊瑚の冠 + 縄尾の浮き真珠。"""
from __future__ import annotations

from td_kit import *  # noqa: F401,F403
from td_kit import sash, pearl, barnacles, kelp_wrap
from wt_common import blade_inlay, cx_interp

TITAN = "tide"
GRIP = (0.0, 3.0, 0.0)
NAME = "珊瑚の銛"
ID = "coral_harpoon"
EDGE = ("main",)
CX = cx_interp([12, 21], [0, 0])


def plate(s, y, sz=1.0, mat="edge", dz=0.0, tilt=0):
    """返しの板 (xy 平面の三角形の鉤。先が下を向く)。s = 左右、sz = 大きさ。"""
    pts = [(0.0, 2.6 * sz), (0.0, -0.6 * sz), (3.6 * sz, -2.8 * sz)]
    pts = [(s * x, yy) for x, yy in pts]
    if s < 0:
        pts = pts[::-1]
    return extrude(mat, pts, 0.9, c=(s * 0.8, y, dz), rot=rot_z(s * tilt))


def faces(r="common"):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    f = []
    # 柄
    f += prism("guard", (0, -2.2, 0), (0, 11.6, 0), 0.95, 0.95, sides=6, caps=(False, True))
    f += prism("wrap", (0, 0.4, 0), (0, 5.0, 0), 1.1, 1.1, sides=6, caps=(False, False))
    f += spike(BONE, "main", (0, -2.2, 0), (0, -1, 0), 1.6, 1.0, sides=6, rings=1, hint=(1, 0, 0))
    # 太い縄の輪 (柄に 2 巻き) と垂れる縄尾
    for y in (7.0, 8.8):
        f += prism("wrap", (0, y - 0.6, 0), (0, y + 0.6, 0), 1.6, 1.6, sides=6, caps=(False, False), phase=20 * y)
    f += curve("wrap", [(1.5, 9.6, -0.4), (4.4, 9.0, -0.8), (5.6, 6.0, 0.2), (4.2, 2.8, -0.2)], [0.6, 0.6, 0.55, 0.5],
               [0.6, 0.6, 0.55, 0.5], sides=4, caps=(False, True))
    f += barnacles((0.4, 3.4, -1.0), 3, 0.5, 0.4, 0.8)
    # 襟と穂先
    f += block("guard", (0, 11.8, 0), (3.2, 1.6, 2.8), taper=(0.8, 0.8))
    f += pearl((0, 12.4, -1.7), 0.9)
    f += blade("main", [(12.6, -1.3, 1.3, 0.8, 0.8), (15.6, -1.7, 1.7, 0.9, 0.9), (21.0, 0, 0, 0, 0)])
    # 返し: 大きな板 (右上・左下 の互い違い)
    f += plate(1, 14.6, 1.25, "spine")
    f += plate(-1, 13.0, 1.1, "spine")
    f += sash([(-1.6, 11.6, -0.8), (-3.2, 9.0, -1.4), (-2.4, 5.8, -0.8)], 1.5, th=0.2, taper=0.45)
    if t >= 1:
        f += plate(-1, 16.6, 0.95, "spine")
        f += block("trim", (0, 12.6, 0), (4.2, 0.9, 3.2), taper=(0.9, 0.9))
        f += block("trim", (0, 2.2, 0), (2.5, 1.0, 2.5))
        # 珊瑚の襟: 襟から 2 本の枝
        for s in (1, -1):
            f += spike(BONE, "acc", (s * 1.2, 11.9, 0), (s * 0.5, 1, 0), 3.4, 0.8, sides=4, rings=1, hint=(0, 0, 1))
    if t == 2:
        # 後ろへ広がる珊瑚の冠と、縄尾の浮き真珠
        for sz in (1, -1):
            f += spike(BONE, "acc", (0, 12.0, sz * 1.0), (0, 0.7, sz * 1.0), 4.8, 0.8, sides=4, rings=2, hint=(1, 0, 0))
        f += plate(1, 17.4, 0.8, mat="acc")
        f += pearl((4.2, 2.6, -0.2), 1.0)
        f += sash([(1.4, 11.6, 0.8), (3.0, 8.6, 1.4), (2.0, 4.4, 0.8)], 1.5, th=0.2, taper=0.45)
    return f


inlay = blade_inlay(["main"], CX, (13.6, 15.4), (14.6, 18.6), 13.0, 19.8, 0.45,
                    extra_spots=[(["gem"], (0, 12.4, -1.7), 1.4)])


import wd2  # noqa: E402
wd2.install(ID, __name__)   # D2 (整え): 面取り + 輪 + 宝石 + 光る線
