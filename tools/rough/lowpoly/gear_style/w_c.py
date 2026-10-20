"""様式 C「遺骸」(Grown Remains): 岩鯨の武器 5 点。
エルデンリング・ブラッドボーン・モンハンの素材武器系。左右非対称、欠け・割れ・ぎざぎざ、骨そのものを武器にし、
ひび割れの奥だけが水色に光る。苔・縄・ぼろ布・骨の護符で使い込まれた物語を足す。
大剣 = 鯨の顎骨の欠けた刃、弓 = 長さの違う肋の弓、槍 = ねじれた牙の穂、杖 = 曲がった骨の杖と椎骨の頭、短剣 = 返しのある牙。"""
from __future__ import annotations

import math

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, feather, remat, at, V, rot_x, rot_y, rot_z
import d_thunder as T
from lpkit import ellipsoid

GRIP = dict(greatsword=(0, 1.8, 0), spear=(0, 3.0, 0), staff=(0, 3.0, 0), dagger=(0, 1.0, 0), bow=(5.0, 8.0, 0))
STAGES = T.STAGES


def rag(c, length, w, sign=1, mat="cloth", th=0.3, tilt=0.0):
    """ぼろ布: 先が不ぞろいに裂けている。"""
    pts = [(0, 0), (w * 0.6, -length * 0.15), (w * 0.8 + tilt * 0.3, -length * 0.5), (w * 0.5 + tilt * 0.7, -length * 0.62),
           (w * 0.1 + tilt, -length), (-w * 0.2 + tilt * 0.8, -length * 0.7), (-w * 0.7 + tilt * 0.4, -length * 0.85),
           (-w * 0.7, -length * 0.3), (-w * 0.4, -length * 0.08)]
    pts = [(sign * x, y) for x, y in pts]
    if sign < 0:
        pts = pts[::-1]
    return extrude(mat, pts, th, c=c)


def charm(x, y, drop=2.4, mat="edge"):
    """縄に吊るした骨の護符。"""
    f = prism("cord", (x, y, 0), (x, y - drop, 0), 0.2, 0.2, sides=4, caps=(False, False), hint=(0, 0, 1))
    f += block(mat, (x, y - drop - 0.7, 0), (0.9, 1.4, 0.8), taper=(0.6, 0.7))
    return f


def greatsword(tier=1):
    f = prism("grip", (0, -1.6, 0), (0, 3.6, 0), 1.2, 1.1, sides=6, caps=(False, False))
    f += prism("wrap", (0, -0.2, 0), (0, 2.2, 0), 1.4, 1.3, sides=6, caps=(False, False))
    f += block("guard", (0, -2.5, 0), (3.6, 2.0, 3.2), taper=(0.7, 0.7), skip=("u",), rot=rot_z(8))
    # 鍔: 椎骨の円盤と、長い鉤 (左) と折れた株 (右)
    f += prism("guard", (0, 3.4, 0), (0, 5.0, 0), 3.6, 3.0, sides=6, caps=(True, True), phase=30)
    f += curve("edge", [(-2.0, 4.2, 0), (-5.2, 4.0, 0), (-7.4, 2.6, 0), (-7.8, 0.2, 0)], [1.4, 1.2, 0.9, 0.0], [1.5, 1.3, 1.0, 0.0], sides=4)
    f += block("edge", (3.8, 4.4, 0), (3.4, 1.6, 2.4), rot=rot_z(-12), taper=(0.6, 0.8))
    f += block("gem", (0, 5.0, -1.65), (1.3, 1.0, 0.3))
    # 刃: 顎骨の厚い板、右へ反る。先は斜めに折れている
    st = [(5.2, -2.4, 3.0, 1.2, 1.2, 0.0), (10.0, -2.7, 3.1, 1.3, 1.3, 0.5), (14.2, -2.4, 3.0, 1.1, 1.1, 1.1),
          (17.4, -1.8, 2.6, 0.9, 0.9, 1.8), (19.6, -0.8, 2.4, 0.7, 0.7, 2.4), (20.6, 2.2, 2.2, 0, 0, 2.9)]
    f += blade("main", st)
    # 左の縁: 顎の歯 (ぎざぎざ)
    for y, L, ang in ((7.6, 3.4, -0.5), (11.4, 3.0, -0.4), (15.0, 2.4, -0.3)):
        x = -2.6 + (0.5 + 0.3 * (y - 5) / 5 * 0.2)
        f += spike(BONE, "edge", (x + 0.2, y, 0), (-1, ang, 0), L, 0.85, sides=4, rings=2, hint=(0, 0, 1))
    # 折れ口の核 (ここが光る)
    f += block("gem", (2.4, 19.8, 0), (1.3, 1.4, 1.5), taper=(0.6, 0.6), rot=rot_z(-20))
    # 付け根の包帯と護符
    f += block("wrap", (0.4, 6.0, 0), (6.2, 1.8, 3.4), rot=rot_z(-6), taper=(0.96, 0.96))
    f += rag((3.2, 5.6, -1.9), 4.6, 1.4, sign=1, tilt=1.0)
    f += charm(-5.6, 3.2, 2.2)
    if tier >= 1:
        f += block("trim", (0, -1.2, 0), (3.2, 0.6, 3.0))
        f += charm(6.2, 4.0, 3.0, "main")
        f += spike(BONE, "edge", (3.4, 4.6, 0), (1, 0.9, 0), 3.6, 1.0, sides=4, rings=2, hint=(0, 0, 1))   # 折れ株が生える
    if tier >= 2:
        # 背の肋: 刃の背に生える大きな反った肋 3 本 (輪郭が変わる)
        for y, L in ((7.0, 8.0), (11.6, 7.2), (16.0, 5.4)):
            f += spike(BONE, "edge", (2.6 + (y - 7) * 0.14, y, 0.0), (1, 0.55, 0), L, 1.1, sides=4, rings=4, bend=(0, -1.6, 0), hint=(0, 0, 1))
        # 左の鉤が二つに増え、柄頭にも角
        f += curve("edge", [(-2.0, 3.6, 0), (-5.6, 3.0, 0), (-8.6, 1.0, 0), (-9.2, -1.6, 0)], [1.3, 1.1, 0.8, 0.0], [1.4, 1.2, 0.9, 0.0], sides=4)
        f += spike(BONE, "edge", (0, -3.2, 0), (0.4, -1, 0), 3.4, 1.2, sides=4, rings=2, hint=(0, 0, 1))
        for y in (13.0, 16.5):
            f += spike(BONE, "edge", (-2.4 + (y - 10) * 0.04, y, 0), (-1, 0.5, 0), 3.8, 0.9, sides=4, rings=2, hint=(0, 0, 1))
    return f


def bow(tier=1, stage=0):
    CY = T.CY
    f = []
    # 握り: 椎骨を 3 つ積む
    for dy in (-2.2, 0.0, 2.2):
        f += prism("guard", (5.0, CY + dy - 0.9, 0), (5.0, CY + dy + 0.9, 0), 1.9 - abs(dy) * 0.1, 1.7, sides=6, caps=(True, True), hint=(0, 0, 1))
    f += prism("wrap", (5.0, CY - 0.9, 0), (5.0, CY + 0.9, 0), 2.1, 2.1, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += ellipsoid(BONE, "gem", (6.6, CY, 0), (0.7, 0.9, 0.9), segs=6, rings=2)
    pull = T.TIP_PULL[stage]
    # 上の肢は長く、下は欠けて短い
    up = [(5.0, 10.8, 0), (4.8, 13.0, 0), (3.0, 15.6, 0), (0.0 - pull * 0.5, 17.6, 0), (-3.0 - pull, 18.2, 0)]
    lo = [(5.0, 5.2, 0), (4.4, 3.4, 0), (2.8, 1.6, 0), (0.6 - pull * 0.4, 0.4, 0)]
    f += curve("main", up, [1.3, 1.2, 1.0, 0.8, 0.4], [1.4, 1.3, 1.1, 0.9, 0.5], sides=4, hint=(0, 0, 1))
    f += curve("main", lo, [1.3, 1.1, 0.9, 0.5], [1.4, 1.2, 1.0, 0.6], sides=4, hint=(0, 0, 1))
    for y, L in ((13.0, 2.6), (15.6, 2.4)):
        x = 4.0 if y < 14 else 3.0
        f += spike(BONE, "edge", (x + 0.8, y, 0), (1, 0.4, 0), L, 0.6, sides=4, rings=2, hint=(0, 0, 1))
    f += spike(BONE, "edge", (3.6, 2.4, 0), (1, -0.6, 0), 2.2, 0.55, sides=4, rings=2, hint=(0, 0, 1))
    f += rag((5.0, CY - 3.6, 0.2), 5.6, 1.5, sign=1, tilt=-0.8)
    f += rag((5.0, CY + 3.4, -0.2), -3.6, 1.2, sign=-1, mat="wrap")
    f += T.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))
    if tier >= 2:
        f += charm(8.2, CY - 1.0, 3.2)
    return f


def spear(tier=1):
    f = []
    # 曲がった柄 (木のように)
    f += curve("grip", [(0, -2.6, 0), (0.4, 3.0, 0), (-0.3, 8.0, 0), (0.2, 12.4, 0)], 1.15, 1.1, sides=6, hint=(0, 0, 1))
    f += prism("wrap", (0.4, 2.0, 0), (0.0, 6.0, 0), 1.45, 1.45, sides=6, caps=(False, False))
    f += prism("wrap", (0, 10.4, 0), (0.2, 12.6, 0), 1.45, 1.45, sides=6, caps=(False, False))
    f += spike(BONE, "edge", (0, -2.4, 0), (0, -1, 0), 2.6, 1.0, sides=4, rings=1, hint=(0, 0, 1))
    f += block("guard", (0.3, 12.8, 0), (3.6, 1.8, 3.2), taper=(0.8, 0.8), rot=rot_z(-6))
    # ねじれた牙の穂 (中心がずれる)
    f += spike(BONE, "main", (0.4, 13.2, 0), (0.18, 1, 0), 9.4, 1.7, sides=6, rings=4, hint=(0, 0, 1), phase=30)
    for k in range(5):
        y = 14.4 + 1.5 * k
        a = k * 38
        r = 1.4 - 0.19 * k
        f += block("edge", (0.4 + 0.18 * (y - 13.2) + math.sin(math.radians(a)) * 0.6, y, math.cos(math.radians(a)) * 0.6),
                   (3.0 * r / 1.4, 0.6, 1.0), rot=rot_y(a), taper=(0.8, 0.8))
    # 返し (牙の根の 2 本。左右で長さが違う)
    f += spike(BONE, "edge", (-1.2, 12.6, 0), (-1, -0.4, 0), 4.6, 0.9, sides=4, rings=2, hint=(0, 0, 1))
    f += spike(BONE, "edge", (1.6, 12.6, 0), (1, -0.5, 0), 2.6, 0.8, sides=4, rings=2, hint=(0, 0, 1))
    f += block("gem", (0.2, 14.0, -1.3), (0.9, 1.4, 0.4))
    f += charm(-1.8, 9.6, 2.8)
    f += charm(1.6, 8.0, 2.0, "main")
    f += rag((1.2, 12.2, 0.3), 7.0, 1.5, sign=1, tilt=1.4)
    if tier >= 1:
        f += block("trim", (0, 6.4, 0), (3.0, 0.7, 3.0))
    return f


def staff(tier=1):
    f = []
    f += curve("grip", [(0, -2.6, 0), (0.7, 3.0, 0), (-0.5, 9.0, 0), (0.4, 14.0, 0)], 1.15, 1.05, sides=6, hint=(0, 0, 1))
    f += prism("wrap", (0.6, 2.0, 0), (0.1, 6.0, 0), 1.45, 1.45, sides=6, caps=(False, False))
    f += spike(BONE, "edge", (0, -2.4, 0), (0, -1, 0), 2.2, 1.0, sides=4, rings=1, hint=(0, 0, 1))
    # 頭: 鯨の椎骨 (六角の円盤を立てる)。中央が窪んで目のように光る
    f += prism("main", (0.4, 14.0, -1.9), (0.4, 14.0, 1.9), 4.0, 4.0, sides=6, caps=(True, True), hint=(1, 0, 0), phase=0)
    f = [x for x in f]
    f += ellipsoid(BONE, "gem", (0.4, 14.2, -1.9), (1.7, 1.7, 0.7), segs=8, rings=2)
    f += ellipsoid(BONE, "gem", (0.4, 14.2, 1.9), (1.7, 1.7, 0.7), segs=8, rings=2)
    # 角: 非対称の 2 本
    f += spike(BONE, "edge", (-2.6, 17.0, 0), (-0.8, 1, 0), 5.2, 1.0, sides=4, rings=3, bend=(-1.4, 0, 0), hint=(0, 0, 1))
    f += spike(BONE, "edge", (3.0, 16.6, 0), (0.6, 1, 0), 3.0, 0.9, sides=4, rings=2, bend=(0.8, 0, 0), hint=(0, 0, 1))
    f += spike(BONE, "main", (0.4, 10.6, 0), (-0.2, -1, 0), 3.0, 1.2, sides=4, rings=2, hint=(0, 0, 1))
    f += charm(-3.8, 13.4, 3.0)
    f += charm(4.4, 12.6, 2.2, "main")
    f += rag((0.6, 11.0, 0.5), 8.0, 1.6, sign=-1, tilt=-1.2)
    if tier >= 1:
        f += block("trim", (0.2, 7.6, 0), (3.0, 0.7, 3.0))
    return f


def dagger(tier=1):
    f = prism("grip", (0, -1.8, 0), (0, 2.4, 0), 1.15, 1.05, sides=6, caps=(False, False))
    f += prism("wrap", (0, -1.0, 0), (0, 1.6, 0), 1.35, 1.3, sides=6, caps=(False, False))
    f += block("guard", (0, -2.5, 0), (3.2, 1.6, 2.8), taper=(0.7, 0.7), skip=("u",))
    f += block("edge", (-2.6, 2.8, 0), (3.6, 1.6, 2.2), rot=rot_z(10), taper=(0.6, 0.8))
    f += block("edge", (1.8, 2.6, 0), (1.8, 1.4, 2.0), taper=(0.6, 0.8))
    # 反った牙の刃
    st = [(3.6, -1.6, 2.0, 1.1, 1.1, 0.0), (6.4, -1.7, 2.1, 1.1, 1.1, 0.7), (9.2, -1.5, 1.9, 0.9, 0.9, 1.7),
          (11.8, -1.0, 1.4, 0.7, 0.7, 3.0), (13.6, -0.4, 0.4, 0.3, 0.3, 4.4), (14.4, 0, 0, 0, 0, 5.2)]
    f += blade("main", st)
    for y, x in ((6.2, -2.2), (8.6, -1.6), (10.8, -0.6)):   # 背の返し
        f += spike(BONE, "edge", (x, y, 0), (-1, -0.7, 0), 1.9, 0.55, sides=4, rings=1, hint=(0, 0, 1))
    f += block("gem", (0.2, 5.0, -1.2), (0.9, 1.0, 0.3))
    f += rag((-2.0, 2.2, 0.0), 4.2, 1.1, sign=-1, tilt=-0.5)
    f += charm(2.4, 2.0, 2.4)
    if tier >= 1:
        f += block("trim", (0, 3.6, 0), (3.4, 0.6, 2.4))
    return f
