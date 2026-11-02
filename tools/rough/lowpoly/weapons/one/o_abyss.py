"""五章 (深淵) の職の武器 3 本 (エピック) の一品一案。前の探掘隊の道具 (灯り・測り・弔い) から作る。
塗りは五章の装備 (../gear_gaps/paint_gaps.py): main = 虚の紫黒、edge = 紫の縁、spine/acc/guard = 錨鉄、grip/wrap = 黒、cord = 紐、gem = 灯り (lamp) / 淵 (void) / 鼓動 (pulse)。
豪華さ: 巨神の武器より一段上 (~650 面)、伝説 (~850 面) より一段下。
"""
from __future__ import annotations

import math

from kit_one import *  # noqa: F401,F403
from kit_one import ell, lump, strand, ringy, gbar, gem, bend, mirror_faces_y, string_and_arrow, inlay_line, CY


def iron_pole(y0, y1, r=0.9):
    f = prism("grip", (0, y0, 0), (0, y1, 0), r, r * 0.95, sides=8, caps=(True, True))
    f += prism("wrap", (0, -1.6, 0), (0, 3.0, 0), r + 0.16, r + 0.16, sides=8, caps=(False, False))
    for y in (-1.8, 3.2):
        f += ringy("trim", y, r + 0.3, 0.45)
    for y in (6.0, 9.4, 12.6):
        f += ringy("spine", y, r + 0.2, 0.55, sides=8)
    f += spike(BONE, "spine", (0, y0, 0), (0, -1, 0), 1.6, r, sides=8, rings=1, hint=(0, 0, 1))
    return f


def lamp_staff(stage=0):
    """灯籠の杖: 錨鉄の柄の先に、石灯籠をそのまま載せた杖。台・火袋 (窓から灯りがこぼれる)・反った笠・宝珠。笠の角から小さな灯りの鈴が下がる"""
    f = iron_pole(-4.0, 13.2)
    # 中台と竿の首
    f += prism("spine", (0, 13.0, 0), (0, 14.0, 0), 1.3, 2.3, sides=6, caps=(True, True), phase=30)
    f += prism("trim", (0, 14.0, 0), (0, 14.4, 0), 2.4, 2.4, sides=6, caps=(True, True), phase=30)
    # 火袋: 六角の箱、各面に窓 (灯りの板)、柱は錨鉄
    f += prism("gem", (0, 14.4, 0), (0, 17.6, 0), 1.55, 1.55, sides=6, caps=(False, False), phase=30)
    for k in range(6):
        a = math.radians(60 * k)
        f += prism("acc", (2.0 * math.cos(a), 14.4, 2.0 * math.sin(a)), (2.0 * math.cos(a), 17.6, 2.0 * math.sin(a)), 0.32, 0.32, sides=4, caps=(True, True))
    f += prism("acc", (0, 14.3, 0), (0, 14.8, 0), 2.25, 2.25, sides=6, caps=(True, True), phase=30)
    f += prism("acc", (0, 17.2, 0), (0, 17.7, 0), 2.25, 2.25, sides=6, caps=(True, True), phase=30)
    # 笠 (反った六角の屋根、角が上がる)
    rings = [[V(rr * math.cos(math.radians(60 * k)), y + (0.5 if rr > 3 else 0), rr * math.sin(math.radians(60 * k))) for k in range(6)]
             for y, rr in ((17.6, 1.6), (17.9, 3.5), (18.5, 2.6), (19.6, 1.1), (20.2, 0.4))]
    f += loft(BONE, "main", rings, caps=("dark", "main"))
    for k in range(6):
        a = math.radians(60 * k)
        f += spike(BONE, "edge", (3.4 * math.cos(a), 18.3, 3.4 * math.sin(a)), (math.cos(a), 0.9, math.sin(a)), 0.9, 0.3, sides=4, rings=1, hint=(0, 1, 0))
    # 宝珠
    f += ell("trim", (0, 20.5, 0), (0.7, 0.5, 0.7), segs=6, rings=1)
    f += gem((0, 21.4, 0), 0.65, 0.85, 0.65, segs=6, rings=2)
    f += spike(BONE, "trim", (0, 22.1, 0), (0, 1, 0), 0.9, 0.25, sides=4, rings=1, hint=(0, 0, 1))
    # 角の鈴 (二つ) と、柄の札
    for k in (0, 3):
        a = math.radians(60 * k)
        x, z = 3.4 * math.cos(a), 3.4 * math.sin(a)
        f += prism("cord", (x, 18.2, z), (x, 16.6, z), 0.12, 0.12, sides=3, caps=(False, False))
        f += ell("acc", (x, 16.2, z), (0.45, 0.5, 0.45), segs=5, rings=1)
        f += gem((x, 15.7, z), 0.2, 0.2, 0.2, segs=4, rings=1)
    f += block("edge", (1.2, 10.8, -0.6), (1.1, 2.0, 0.2))
    f += prism("cord", (1.2, 12.6, -0.6), (1.2, 11.8, -0.6), 0.1, 0.1, sides=3, caps=(False, False))
    return f


def depth_bow(stage=0):
    """測鉛の弓: 腕は錨鉄の背に虚の板を張った重ね弓。両の先から結び目の糸で深さを測る鉛が下がり、握りには測り糸の糸巻き。腕に深さの目盛りの鋲"""
    f = []
    f += prism("grip", (5.0, CY - 2.8, 0), (5.0, CY + 2.8, 0), 1.0, 1.0, sides=8, caps=(False, False), hint=(0, 0, 1))
    # 糸巻き (握りの外、z 向きの軸)
    f += prism("spine", (6.2, CY, -1.4), (6.2, CY, 1.4), 1.5, 1.5, sides=10, caps=(True, True), hint=(1, 0, 0))
    f += prism("cord", (6.2, CY, -1.0), (6.2, CY, 1.0), 1.65, 1.65, sides=10, caps=(False, False), hint=(1, 0, 0))
    f += prism("trim", (6.2, CY, -1.6), (6.2, CY, 1.6), 0.4, 0.4, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += gem((6.2, CY, -1.75), 0.35, 0.35, 0.15, segs=5, rings=1)
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.4, CY + 5.0, 0), (2.9, CY + 7.6, 0), (0.7, CY + 9.6, 0), (-1.7, CY + 10.6, 0)])
    half = []
    half += curve("main", up, [0.95, 0.88, 0.75, 0.58, 0.38], [1.15, 1.05, 0.9, 0.7, 0.45], sides=5, caps=(False, True), hint=(0, 0, 1))
    half += curve("acc", [(x + 0.75, y, 0) for x, y, z in up[:4]], [0.3, 0.28, 0.25, 0.15], [0.75, 0.7, 0.6, 0.3], sides=4, caps=(True, True), hint=(0, 0, 1))
    for i in range(3):
        (x0, y0, _), (x1, y1, _) = up[i], up[i + 1]
        x, y = (x0 + x1) / 2, (y0 + y1) / 2
        half += ell("trim", (x + 0.85, y, -0.4), (0.22, 0.22, 0.22), segs=4, rings=1)
        half += ell("trim", (x + 0.85, y, 0.4), (0.22, 0.22, 0.22), segs=4, rings=1)
    tx, ty, _ = up[-1]
    half += prism("spine", (tx, ty - 0.4, 0), (tx, ty + 0.4, 0), 0.6, 0.6, sides=6, caps=(True, True), hint=(0, 0, 1))
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    # 測りの鉛 (上の先は短く、下の先は長く垂らす)
    for (x, y), drop in (((up[-1][0] + 0.6, up[-1][1]), 2.2), ((lo[-1][0] + 0.6, lo[-1][1]), 1.6)):
        f += prism("cord", (x, y, 0.6), (x, y - drop, 0.6), 0.1, 0.1, sides=3, caps=(False, False))
        for k in range(2):
            f += ell("cord", (x, y - drop * (0.35 + 0.35 * k), 0.6), (0.2, 0.2, 0.2), segs=4, rings=1)
        rings = [[V(x + rr * math.cos(math.radians(45 * k)), y - drop - h, 0.6 + rr * math.sin(math.radians(45 * k))) for k in range(8)]
                 for h, rr in ((0.0, 0.25), (0.4, 0.6), (1.4, 0.7), (2.2, 0.35))]
        f += loft(BONE, "spine", rings, caps=("trim", "spine"))
        f += spike(BONE, "spine", (x, y - drop - 2.2, 0.6), (0, -1, 0), 0.7, 0.35, sides=6, rings=1, hint=(0, 0, 1))
        f += gem((x, y - drop - 1.0, 0.0), 0.2, 0.3, 0.12, segs=4, rings=1)
    return f + string_and_arrow(stage, "rare", up, lo)


def toll_bell_staff(stage=0):
    """弔鐘の鈴杖: 柄の先に小さな鐘楼 (二本の柱と反った梁) が立ち、重い鐘が吊られる。鐘の口の奥で鼓動の赤が脈打ち、結び目の綱が垂れる。梁から弔いの札"""
    f = iron_pole(-4.0, 13.4)
    # 鐘楼の台と柱
    f += block("spine", (0, 13.6, 0), (6.2, 0.9, 2.2), taper=(0.95, 0.95))
    for s in (1, -1):
        f += block("acc", (s * 2.7, 17.4, 0), (0.8, 7.0, 0.9))
        f += block("trim", (s * 2.7, 14.4, 0), (1.1, 0.6, 1.2))
    # 反った梁 (二段)
    f += extrude("main", [(-4.2, 21.4), (-3.6, 20.6), (3.6, 20.6), (4.2, 21.4), (3.0, 21.9), (-3.0, 21.9)], 1.4, c=(0, 0, 0))
    f += block("acc", (0, 20.3, 0), (5.8, 0.6, 1.0))
    f += spike(BONE, "edge", (-4.2, 21.4, 0), (-1, 0.6, 0), 0.9, 0.35, sides=4, rings=1, hint=(0, 0, 1))
    f += spike(BONE, "edge", (4.2, 21.4, 0), (1, 0.6, 0), 0.9, 0.35, sides=4, rings=1, hint=(0, 0, 1))
    f += gem((0, 22.5, 0), 0.45, 0.55, 0.45, segs=5, rings=1)
    # 鐘 (錨鉄、口が下) と、口の奥の鼓動
    f += prism("spine", (0, 20.0, 0), (0, 19.4, 0), 0.45, 0.45, sides=6, caps=(True, True))
    rings = [[V(rr * math.cos(math.radians(22.5 * k)), y, rr * math.sin(math.radians(22.5 * k))) for k in range(16)]
             for y, rr in ((14.8, 2.0), (15.1, 1.85), (16.6, 1.55), (18.4, 1.35), (19.4, 0.9))]
    f += loft(BONE, "guard", rings, caps=("gem", "guard"))
    f += prism("trim", (0, 14.7, 0), (0, 15.2, 0), 2.1, 2.05, sides=16, caps=(False, False))
    f += torus("trim", (0, 17.6, 0), 1.48, 0.12, 0.16, n=12, k=3, ax="y")
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += ell("edge", (1.5 * math.cos(a), 18.2, 1.5 * math.sin(a)), (0.2, 0.2, 0.2), segs=4, rings=1)
    # 結び目の綱 (鐘の舌から垂れる)
    f += prism("cord", (0, 14.8, 0), (0.2, 10.2, -0.3), 0.18, 0.18, sides=4, caps=(False, False))
    for y in (13.4, 11.8):
        f += ell("cord", (0.1, y, -0.2), (0.32, 0.3, 0.32), segs=5, rings=1)
    f += prism("cord", (0.2, 10.2, -0.3), (0.3, 9.4, -0.3), 0.36, 0.1, sides=6, caps=(True, True))
    # 弔いの札 (梁から)
    for x in (-3.4, 3.4):
        f += prism("cord", (x, 20.3, -0.6), (x, 19.2, -0.6), 0.08, 0.08, sides=3, caps=(False, False))
        f += block("edge", (x, 18.2, -0.6), (0.9, 2.0, 0.15))
    return f


def _w(fn, jp, glow, pose, grip, cen, scale, inlay, stages=1):
    e = dict(jp=jp, tier="rare", glow=glow, pose=pose, grip=grip, cen=cen, scale=scale,
             opts={"a": ((lambda stage=0: fn(stage)), inlay, ("edge",), (fn.__doc__ or "").split(":", 1)[-1].strip())})
    if stages > 1:
        e["stages"] = stages
    return e


ST = dict(pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 9.0, 0.0), scale=9.5)
BW = dict(pose="thunder", grip=(5.0, 8.0, 0.0), cen=(2.0, 8.0, 0.0), scale=12.0, stages=4)
off = inlay_line(["main"], 99.0, 99.5)
WEAPONS = {
    "abyss_lamp_staff": _w(lamp_staff, "灯籠の杖", "lamp", inlay=off, **ST),
    "abyss_depth_bow": _w(depth_bow, "測鉛の弓", "void", inlay=off, **BW),
    "abyss_toll_bell_staff": _w(toll_bell_staff, "弔鐘の鈴杖", "pulse", inlay=off, **ST),
}
