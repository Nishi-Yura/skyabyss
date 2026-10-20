"""水路の牙 (岩鯨・改良)。反った 1 本の牙の短剣 (形は残す)。柄の巻きに護符とぼろ布、刃の根に欠けと縄 (C の使い込み)。
階級で輪郭が変わる: 通常 = 反った牙と小さな鍔 / レア = 肋 2 本の鍔と背の副牙 / 伝説 = 刃に寄り添う第二の牙 + 鍔の石の翼。
握り y 2.5 (変えない)。y 0..16。"""
from __future__ import annotations

from wh_kit import *  # noqa: F401,F403
from wh_kit import rag, charm, tooth
from wt_common import blade_inlay, cx_interp

TITAN = "whale"
GRIP = (0.0, 2.5, 0.0)
NAME = "水路の牙"
ID = "waterway_fang"
EDGE = ("edge",)
CX = cx_interp([5.8, 8, 11, 14], [0, 0.4, 1.3, 2.5])


def faces(r="common"):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    f = []
    f += prism("grip", (0, 0.4, 0), (0, 1.2, 0), 1.1, 1.0, sides=6, caps=(False, False))
    f += prism("wrap", (0, 1.2, 0), (0, 4.6, 0), 1.3, 1.2, sides=6, caps=(False, False))
    f += block("guard", (0, 0.2, 0), (3.4, 1.2, 3.0), taper=(0.7, 0.7), skip=("u",))
    f += block("guard", (0, 4.9, 0), (3.8, 1.8, 3.2), taper=(0.9, 0.9))
    f += block("gem", (0, 6.1, -0.2), (1.3, 1.0, 1.5), taper=(0.8, 0.8))
    st = [(5.8, -2.4, 2.4, 1.1, 1.1, 0.0), (8.0, -2.8, 2.6, 1.2, 1.2, 0.4), (11.0, -2.4, 2.0, 1.0, 1.0, 1.3),
          (14.0, -1.4, 1.1, 0.6, 0.6, 2.5), (16.0, 0, 0, 0, 0, 3.6)]
    f += blade("main", st)
    # 根の欠けと縄・ぼろ布・護符 (使い込み)
    f += block("wrap", (0.2, 6.0, 0), (5.2, 1.0, 3.2), rot=rot_z(-5), taper=(0.96, 0.96))
    f += rag((1.2, 3.8, -1.6), 4.2, 1.0, sign=-1, tilt=-0.4)
    f += charm(-2.0, 4.4, 2.2, z=1.4)
    # 背の副牙
    f += spike(BONE, "edge", (-2.5, 7.0, 0), (-0.5, 1, 0), 4.4, 1.1, sides=4, rings=2, hint=(0, 0, 1))
    if t >= 1:
        for s in (1, -1):
            f += curve("guard", [(s * 1.4, 5.0, 0), (s * 3.6, 4.8, 0), (s * 5.0, 5.8, 0), (s * 5.2, 7.4, 0)],
                       [1.2, 1.1, 0.9, 0.0], [1.3, 1.2, 0.95, 0.0], sides=4)
        f += block("trim", (0, 6.1, 0), (3.8, 0.9, 2.6), taper=(0.9, 0.9))
        f += block("trim", (0, -0.5, 0), (3.4, 0.8, 3.0))
    if t == 2:
        # 第二の牙 (刃に寄り添う長い牙) と鍔の石の翼
        f += spike(BONE, "main", (2.9, 7.6, 0), (0.25, 1, 0), 7.8, 1.2, sides=4, rings=3, hint=(0, 0, 1), bend=(1.4, 0, 0))
        for s in (1, -1):
            f += block("spine", (s * 3.0, 3.8, 0), (1.8, 3.2, 1.7), rot=rot_z(-s * 18), taper=(0.5, 0.6))
    return f


inlay = blade_inlay(["main"], CX, (6.0, 8.0), (8.4, 11.0), 6.0, 15.0, 0.5,
                    extra_spots=[(["main"], (3.0, 11.0, 0), 1.5)])
