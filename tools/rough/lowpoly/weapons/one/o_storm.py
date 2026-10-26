"""雷翼 (三章) の一品一案。章の職の武器 3 本 (エピック) と、雷翼の伝説 5 本。
塗りは雷翼 (../paint.py): main/edge = 銅、spine/guard/grip = 鋼、acc = 羽 (白)、soot = 焦げ、cord = 紐、cloth = 鋼の布、gem = 青白い光。
"""
from __future__ import annotations

import math

from kit_one import *  # noqa: F401,F403
from kit_one import legend_finish, ell, lump, strand, ringy, gbar, gem, arc, inlay_line, CY


def coil(y0, y1, r, turns, mat="main", w=0.22, cx=0.0, cz=0.0, axis="y"):
    """銅の巻き線 (螺旋)"""
    n = int(turns * 8) + 1
    pts = []
    for i in range(n):
        a = 2 * math.pi * turns * i / (n - 1)
        t = y0 + (y1 - y0) * i / (n - 1)
        if axis == "y":
            pts.append((cx + r * math.cos(a), t, cz + r * math.sin(a)))
        else:
            pts.append((t, cx + r * math.cos(a), cz + r * math.sin(a)))
    return curve(mat, pts, [w] * n, [w] * n, sides=3, caps=(True, True))


def shide(x, y, z=-0.2, n=4, w=0.75, h=0.85, mat="acc"):
    """紙垂 (稲妻の形に折れた白い帯)"""
    f = []
    for i in range(n):
        s = 1 if i % 2 == 0 else -1
        f += block(mat, (x + s * w * 0.25, y - h * (i + 0.5), z), (w, h * 1.05, 0.22), rot=rot_z(s * 18))
    return f


def zig(pts, w=0.5, th=0.5, mat="gem"):
    """稲妻の板 (折れ線をなぞる平たい帯)"""
    f = []
    for a, b in zip(pts, pts[1:]):
        f += curve(mat, [a, b], [w, w], [th * 0.5, th * 0.5], sides=4, caps=(True, True), hint=(0, 0, 1))
    return f


def steel_haft(y0, y1, r=0.92, wrap=(-1.6, 3.0)):
    f = prism("grip", (0, y0, 0), (0, y1, 0), r, r * 0.95, sides=6, caps=(True, True))
    f += prism("cord", (0, wrap[0], 0), (0, wrap[1], 0), r + 0.17, r + 0.17, sides=6, caps=(False, False))
    for y in (wrap[0] + 0.4, (wrap[0] + wrap[1]) / 2, wrap[1] - 0.4):
        f += ringy("main", y, r + 0.3, 0.32)
    return f


# ================================================================== 章の武器 (エピック)
def storm_staff(stage=0):
    """雷羽の杖: 銅線が螺旋に巻き上がる鋼の柄。先は雷翼の風切羽が冠のように扇に開き、中央に避雷の銅の穂が立って光の玉を掲げる"""
    f = steel_haft(-4.0, 15.6)
    f += coil(4.0, 14.4, 1.08, 4.5, mat="main", w=0.2)
    f += spike(BONE, "spine", (0, -4.0, 0), (0, -1, 0), 1.6, 0.9, sides=6, rings=1, hint=(0, 0, 1))
    # 羽の付け根の銅の盃
    rings = [[V(rr * math.cos(math.radians(22.5 + 45 * k)), y, rr * math.sin(math.radians(22.5 + 45 * k))) for k in range(8)]
             for y, rr in ((14.6, 0.95), (15.4, 1.6), (16.4, 2.0), (16.8, 1.85))]
    f += loft(BONE, "main", rings, caps=("main", "dark"))
    f += ringy("trim", 15.0, 1.5, 0.45)
    # 冠の風切羽 (左右に五本ずつ、後ろの列は長い)
    for i, (a, L) in enumerate(((-66, 4.6), (-42, 6.0), (-18, 7.0), (18, 7.0), (42, 6.0), (66, 4.6))):
        ra = math.radians(a)
        base = (1.4 * math.sin(ra), 16.4, 0.5 + 0.25 * (i % 2))
        tip = (base[0] + L * math.sin(ra), 16.4 + L * math.cos(ra), 0.7 + 0.25 * (i % 2))
        f += plume(base, tip, 1.15, 0.36, bend=-0.5 * math.copysign(1, a), big=True, scorch=1)
    # 避雷の穂 (中央の銅の針と、光の玉)
    f += prism("edge", (0, 16.2, -0.2), (0, 21.0, -0.2), 0.42, 0.18, sides=6, caps=(False, False))
    f += gem((0, 21.6, -0.2), 0.9, 0.9, 0.9, segs=6, rings=2)
    f += torus("main", (0, 21.6, -0.2), 1.2, 0.16, 0.18, n=8, k=3, ax="z")
    f += zig([(-0.9, 20.2, -0.5), (-0.3, 19.2, -0.5), (-1.0, 18.4, -0.5), (-0.4, 17.4, -0.5)], w=0.22, th=0.3)
    f += strand("cord", (0.9, 14.6, -0.9), 2.2, 0.4, r=0.22)
    f += plume((0.9, 12.6, -0.9), (1.3, 10.4, -0.9), 0.55, 0.3, big=False, scorch=1)
    return f


def storm_bell_staff(stage=0):
    """雷鳴りの鈴杖: 柄の先の鋼の大輪に銅の鐘が吊られ、輪からは稲妻に折れた紙垂が下がる。輪の頂に雷翼の羽、輪の縁に鋲"""
    f = steel_haft(-4.0, 13.2)
    f += spike(BONE, "spine", (0, -4.0, 0), (0, -1, 0), 1.6, 0.9, sides=6, rings=1, hint=(0, 0, 1))
    f += ringy("trim", 3.4, 1.3, 0.5)
    # 大輪 (正面を向く) と縁の鋲
    f += torus("guard", (0, 17.4, 0), 4.2, 0.55, 0.7, n=14, k=4, ax="z")
    for k in range(10):
        a = math.radians(36 * k + 18)
        f += ell("main", (4.2 * math.cos(a), 17.4 + 4.2 * math.sin(a), -0.7), (0.3, 0.3, 0.25), segs=5, rings=1)
    f += prism("guard", (0, 12.8, 0), (0, 13.4, 0), 1.2, 1.2, sides=6, caps=(True, True))
    # 鐘 (輪の頂から吊る、口が下)
    f += prism("cord", (0, 21.4, 0), (0, 19.8, 0), 0.2, 0.2, sides=4, caps=(False, False))
    rings = [[V(rr * math.cos(math.radians(22.5 + 45 * k)), y, rr * math.sin(math.radians(22.5 + 45 * k))) for k in range(8)]
             for y, rr in ((15.4, 2.0), (15.8, 1.9), (17.6, 1.4), (19.4, 0.85), (19.9, 0.4))]
    f += loft(BONE, "main", rings, caps=("dark", "main"))
    f += prism("trim", (0, 15.2, 0), (0, 15.7, 0), 2.15, 2.1, sides=8, caps=(True, True))
    f += gem((0, 14.8, 0), 0.6, 0.6, 0.6, segs=5, rings=1)
    # 紙垂 (輪の左右の肩から)
    for x in (-3.2, 3.2):
        f += shide(x, 15.0, z=-0.4, n=4)
    # 頂の羽
    for s in (1, -1):
        f += plume((s * 0.4, 21.4, 0.3), (s * 2.6, 24.2, 0.3), 0.8, 0.34, bend=-s * 0.4, big=True, scorch=1)
    f += gem((0, 21.8, 0), 0.5, 0.5, 0.5, segs=5, rings=1)
    return f


def storm_hammer(stage=0):
    """雷の鎚: 頭は太い銅の巻き線を重ねた筒 (打つ面は鋼の円盤)。中央に嵐の目の光、背は稲妻に折れた銅の突き。首に風切羽"""
    f = steel_haft(-3.6, 10.6, r=0.95, wrap=(-2.8, 2.2))
    f += lump("guard", (0, -4.0, 0), (1.15, 0.9, 1.15), seed=61, segs=6, rings=2)
    # 頭の芯 (x 向き) と巻き線
    f += prism("guard", (-2.6, 12.4, 0), (2.8, 12.4, 0), 1.7, 1.7, sides=8, caps=(True, True), hint=(0, 1, 0))
    for x0 in (-2.2, 0.8):
        f += coil(x0, x0 + 1.6, 1.85, 3.0, mat="main", w=0.3, cx=12.4, axis="x")
    f += prism("main", (-0.5, 12.4, 0), (0.6, 12.4, 0), 2.2, 2.2, sides=8, caps=(True, True), hint=(0, 1, 0))
    f += gem((0.05, 12.4, -2.2), 0.65, 0.65, 0.3, segs=6, rings=1)
    # 打つ面 (+x) の鋼の円盤
    f += prism("spine", (2.8, 12.4, 0), (3.8, 12.4, 0), 2.3, 2.3, sides=10, caps=(True, True), hint=(0, 1, 0))
    f += prism("trim", (3.8, 12.4, 0), (4.1, 12.4, 0), 1.7, 1.7, sides=10, caps=(True, True), hint=(0, 1, 0))
    # 背 (-x) の稲妻の突き
    f += extrude("edge", [(-2.6, 13.4), (-4.4, 13.8), (-3.8, 12.6), (-5.6, 12.8), (-4.0, 11.4), (-2.6, 11.6)], 1.0, c=(0, 0, 0))
    # 首の羽
    for s in (1, -1):
        f += plume((s * 0.6, 10.6, 0.6), (s * 2.4, 8.0, 0.8), 0.7, 0.32, bend=s * 0.3, big=True, scorch=1)
    f += ringy("trim", 10.6, 1.3, 0.45)
    return f


# ================================================================== 伝説
def abyss_storm_sword(stage=0):
    """避雷の大剣: 刃は銅を巻いた避雷針。先は二股に割れ、刃の芯を稲妻が走る。鍔は鋼の碍子 (重ねた皿)、金の環と淵の核"""
    f = steel_haft(-3.6, 2.8, r=0.95, wrap=(-2.6, 2.2))
    f += ringy("trim", -3.0, 1.2, 0.45)
    f += gem((0, -4.2, 0), 0.6, 0.7, 0.6, segs=6, rings=1, mat="void")
    f += torus("trim", (0, -4.2, 0), 0.8, 0.18, 0.22, n=8, k=3, ax="y")
    # 鍔: 碍子 (鋼の皿を三枚重ね)
    for i, (y, r) in enumerate(((3.2, 2.6), (4.0, 2.2), (4.8, 1.8))):
        f += prism("spine", (0, y - 0.25, 0), (0, y + 0.25, 0), r, r * 0.92, sides=10, caps=(True, True))
        f += prism("trim", (0, y - 0.32, 0), (0, y - 0.22, 0), r + 0.1, r + 0.1, sides=10, caps=(True, True))
    f += ell("void", (0, 4.0, 0), (1.3, 1.2, 1.3), segs=8, rings=2)
    # 刃: 平たい銅の板 + 芯の棒 + 巻き線
    f += blade("main", [(5.2, -1.5, 1.5, 0.6, 0.6), (11.0, -1.7, 1.7, 0.55, 0.55), (16.4, -1.5, 1.5, 0.5, 0.5), (18.0, -1.3, 1.3, 0.45, 0.45)], tip=False)
    f += prism("spine", (0, 5.0, 0), (0, 18.2, 0), 0.62, 0.55, sides=6, caps=(False, False))
    f += coil(5.4, 10.2, 1.0, 4.0, mat="edge", w=0.2)
    # 二股の先
    for s in (1, -1):
        f += curve("edge", [(s * 0.7, 17.8, 0), (s * 1.5, 19.8, 0), (s * 1.1, 21.8, 0), (s * 0.5, 22.8, 0)], [0.55, 0.45, 0.3, 0.05], [0.45, 0.38, 0.25, 0.05],
                   sides=4, caps=(False, True))
    f += gem((0, 19.8, 0), 0.5, 0.7, 0.5, segs=5, rings=1)
    f += zig([(0.1, 18.8, -0.65), (-0.5, 16.6, -0.65), (0.4, 14.6, -0.65), (-0.4, 12.6, -0.65), (0.3, 10.8, -0.65)], w=0.25, th=0.3)
    # 鍔の羽
    for s in (1, -1):
        f += plume((s * 2.0, 4.0, 0.4), (s * 4.6, 2.2, 0.4), 0.8, 0.34, bend=s * 0.4, big=True, scorch=1)
    return f


def abyss_storm_hammer(stage=0):
    """雷鼓の鎚: 頭は雷の太鼓 (銅の胴に金の鋲の輪、鋼の皮が打つ面)。後ろに雷神の連鼓の輪が背負われ、四つの小太鼓と巴の光"""
    f = steel_haft(-4.0, 11.2, r=1.0, wrap=(-3.0, 2.2))
    for y in (-3.4, 6.0):
        f += ringy("trim", y, 1.25, 0.45)
    f += gem((0, -4.8, 0), 0.6, 0.7, 0.6, segs=6, rings=1, mat="void")
    # 太鼓 (x 向きの樽)
    rings = [[V(x, 13.0 + rr * math.cos(math.radians(22.5 * k)), rr * math.sin(math.radians(22.5 * k))) for k in range(16)]
             for x, rr in ((-3.4, 2.5), (-2.0, 2.85), (0.0, 3.0), (2.0, 2.85), (3.4, 2.5))]
    f += loft(BONE, "main", rings, caps=("spine", "spine"))
    for s in (1, -1):
        f += prism("trim", (s * 3.25, 13.0, 0), (s * 3.55, 13.0, 0), 2.62, 2.62, sides=16, caps=(False, False), hint=(0, 1, 0))
        for k in range(8):
            a = math.radians(45 * k)
            f += ell("trim", (s * 3.0, 13.0 + 2.55 * math.cos(a), 2.55 * math.sin(a)), (0.26, 0.26, 0.26), segs=5, rings=1)
        f += prism("gem", (s * 3.45, 13.0, 0), (s * 3.6, 13.0, 0), 0.7, 0.7, sides=6, caps=(True, True), hint=(0, 1, 0))
    f += torus("trim", (0, 13.0, 0), 3.05, 0.12, 0.18, n=16, k=3, ax="y")
    # 連鼓の輪 (後ろ +Z、正面を向く) と四つの小太鼓
    f += torus("edge", (0, 13.0, 3.4), 4.8, 0.35, 0.4, n=16, k=4, ax="z")
    for k in range(4):
        a = math.radians(45 + 90 * k)
        c = (4.8 * math.cos(a), 13.0 + 4.8 * math.sin(a), 3.4)
        f += prism("main", (c[0], c[1], c[2] - 0.6), (c[0], c[1], c[2] + 0.6), 1.1, 1.1, sides=8, caps=(True, True), hint=(1, 0, 0))
        f += prism("spine", (c[0], c[1], c[2] - 0.7), (c[0], c[1], c[2] - 0.6), 0.9, 0.9, sides=8, caps=(True, True), hint=(1, 0, 0))
        f += gem((c[0], c[1], c[2] - 0.8), 0.32, 0.32, 0.15, segs=5, rings=1)
    f += prism("guard", (0, 13.0, 2.9), (0, 13.0, 3.5), 0.9, 0.9, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += plume((0.6, 10.8, 1.0), (2.2, 7.8, 1.2), 0.7, 0.3, bend=0.3, big=True, scorch=1)
    f += plume((-0.6, 10.8, 1.0), (-2.2, 7.8, 1.2), 0.7, 0.3, bend=-0.3, big=True, scorch=1)
    return f


def abyss_storm_staff(stage=0):
    """雲籠の杖: 銅の細い籠に雷雲をひとつ閉じこめた杖。籠の中で雲が膨らみ、稲妻が光る。籠の頂に避雷の穂、下に金の受け皿"""
    f = steel_haft(-4.0, 13.6)
    f += coil(4.0, 12.6, 1.05, 3.0, mat="trim", w=0.18)
    f += spike(BONE, "spine", (0, -4.0, 0), (0, -1, 0), 1.8, 0.95, sides=6, rings=1, hint=(0, 0, 1))
    f += gem((0, -2.2, -1.0), 0.4, 0.4, 0.25, segs=5, rings=1, mat="void")
    # 受け皿 (金)
    rings = [[V(rr * math.cos(math.radians(22.5 + 45 * k)), y, rr * math.sin(math.radians(22.5 + 45 * k))) for k in range(8)]
             for y, rr in ((13.2, 1.0), (14.0, 2.2), (14.4, 2.6))]
    f += loft(BONE, "trim", rings, caps=("trim", "trim"))
    # 雲 (白い塊の群れ、中に淵) と籠の中の稲妻
    for c, r in (((0, 16.8, 0), (2.5, 1.7, 2.4)), ((1.5, 18.2, 0.3), (1.7, 1.4, 1.7)), ((-1.4, 18.0, -0.4), (1.6, 1.4, 1.6)), ((0.2, 19.4, 0), (1.5, 1.2, 1.4))):
        f += ell("acc", c, r, segs=7, rings=2)
    f += ell("void", (0, 17.6, 0), (1.4, 1.4, 1.3), segs=6, rings=2)
    f += zig([(0.6, 19.4, -1.6), (-0.4, 18.2, -1.7), (0.5, 17.2, -1.7), (-0.3, 15.6, -1.6)], w=0.26, th=0.3)
    # 籠 (縦の銅の骨 8 本と、横の輪 2 本)
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        pts = [(2.5 * math.cos(a) * s, y, 2.5 * math.sin(a) * s) for y, s in ((14.3, 1.0), (16.0, 1.18), (18.0, 1.16), (19.8, 0.9), (21.0, 0.35))]
        f += curve("main", pts, [0.17] * 5, [0.17] * 5, sides=3, caps=(True, True))
    for y, R in ((16.6, 2.95), (19.0, 2.6)):
        f += torus("main", (0, y, 0), R, 0.16, 0.16, n=12, k=3, ax="y")
    # 頂の穂
    f += prism("edge", (0, 20.8, 0), (0, 23.6, 0), 0.35, 0.1, sides=6, caps=(True, False))
    f += gem((0, 22.2, 0), 0.45, 0.45, 0.45, segs=5, rings=1)
    for s in (1, -1):
        f += plume((s * 2.2, 14.2, 0.4), (s * 4.0, 12.0, 0.4), 0.6, 0.3, bend=s * 0.3, big=False, scorch=1)
    return f


def abyss_storm_dagger(stage=0):
    """雷翼の鉤爪: 雷翼の大きな一本の鉤爪 (内へ深く反る) を刃にした短剣。柄尻は指を通す金の輪、手首に焦げた風切羽、爪の根に稲妻"""
    f = steel_haft(-2.6, 2.6, r=0.85, wrap=(-1.8, 2.0))
    # 柄尻の輪 (指を通す)
    f += torus("trim", (0, -4.2, 0), 1.3, 0.3, 0.34, n=10, k=4, ax="z")
    f += prism("trim", (0, -2.8, 0), (0, -3.0, 0), 1.0, 1.0, sides=6, caps=(True, True))
    f += gem((0, -2.9, -1.0), 0.35, 0.35, 0.2, segs=5, rings=1, mat="void")
    # 爪の根 (鋼の節)
    f += ell("guard", (0, 3.4, 0), (1.5, 1.1, 1.3), segs=7, rings=2)
    f += torus("trim", (0, 3.0, 0), 1.25, 0.16, 0.2, n=8, k=3, ax="y")
    # 鉤爪 (+x へ深く反る、断面は前後に薄い)
    pts = [(0, 3.8, 0), (0.5, 6.1, 0), (1.6, 8.3, 0), (3.2, 9.8, 0), (4.8, 9.9, 0), (5.8, 9.0, 0)]
    f += curve("spine", pts, [1.25, 1.2, 1.05, 0.8, 0.5, 0.05], [0.65, 0.6, 0.5, 0.4, 0.25, 0.05], sides=6, caps=(False, True), hint=(0, 0, 1))
    # 内側の刃 (銅の縁)
    inner = [(0.9, 4.2, 0), (1.4, 6.1, 0), (2.6, 7.9, 0), (4.0, 9.1, 0), (5.2, 9.2, 0)]
    f += curve("edge", inner, [0.35, 0.35, 0.3, 0.22, 0.08], [0.3, 0.3, 0.26, 0.2, 0.06], sides=4, caps=(True, True), hint=(0, 0, 1))
    f += zig([(-0.4, 4.4, -0.7), (0.3, 5.6, -0.7), (-0.2, 6.6, -0.7), (0.8, 8.0, -0.6)], w=0.22, th=0.3)
    # 手首の羽
    for s, z in ((1, 0.5), (-1, -0.5)):
        f += plume((s * 0.6, 2.4, z), (s * 2.8, -1.8, z), 0.75, 0.3, bend=s * 0.4, big=True, scorch=1)
    f += plume((-0.8, 3.2, 0.9), (-3.4, 2.0, 0.9), 0.65, 0.3, bend=-0.3, big=True, scorch=1)
    return f


def abyss_storm_bell_staff(stage=0):
    """風鈴の鈴杖: 柄の先の銅の横木に、長さの違う銅の管が七本下がる。中央に打つ円板と、風を受ける白い羽の短冊。横木の上に金の屋根"""
    f = steel_haft(-4.0, 16.0)
    f += spike(BONE, "spine", (0, -4.0, 0), (0, -1, 0), 1.6, 0.9, sides=6, rings=1, hint=(0, 0, 1))
    for y in (3.4, 10.0):
        f += ringy("trim", y, 1.25, 0.45)
    f += gem((0, -2.2, -1.0), 0.4, 0.4, 0.25, segs=5, rings=1, mat="void")
    # 屋根 (金の傘) と横木
    rings = [[V(rr * math.cos(math.radians(22.5 + 45 * k)), y, rr * 0.55 * math.sin(math.radians(22.5 + 45 * k))) for k in range(8)]
             for y, rr in ((17.4, 5.2), (18.4, 3.4), (19.4, 1.0))]
    f += loft(BONE, "trim", rings, caps=("dark", "trim"))
    f += prism("main", (0, 19.2, 0), (0, 21.4, 0), 0.4, 0.15, sides=6, caps=(False, False))
    f += gem((0, 21.8, 0), 0.5, 0.5, 0.5, segs=5, rings=1)
    f += prism("main", (-5.0, 17.0, 0), (5.0, 17.0, 0), 0.4, 0.4, sides=6, caps=(True, True), hint=(0, 1, 0))
    # 管 (長さの違う七本)
    for x, L in ((-4.2, 3.4), (-2.8, 4.6), (-1.4, 5.6), (1.4, 5.4), (2.8, 4.4), (4.2, 3.2)):
        f += prism("cord", (x, 17.0, 0), (x, 16.2, 0), 0.1, 0.1, sides=3, caps=(False, False))
        f += prism("main", (x, 16.2, 0), (x, 16.2 - L, 0), 0.34, 0.34, sides=6, caps=(True, True))
        f += prism("dark", (x, 16.2 - L - 0.02, 0), (x, 16.2 - L + 0.05, 0), 0.22, 0.22, sides=6, caps=(True, True))
    # 中央の打つ円板と、羽の短冊
    f += prism("cord", (0.0, 17.0, 0), (0.0, 13.4, 0), 0.1, 0.1, sides=3, caps=(False, False))
    f += prism("gem", (0, 13.4, -0.12), (0, 13.4, 0.12), 0.9, 0.9, sides=8, caps=(True, True), hint=(1, 0, 0))
    f += plume((0, 12.6, 0), (0.4, 9.4, 0), 0.9, 0.3, big=True, scorch=1)
    return f


# ================================================================== 一覧
def _w(fn, jp, tier, pose, grip, cen, scale, inlay, stages=1):
    if tier == "legend":
        kind = {"thunder": "bow"}.get(pose) or ("hammer" if pose == "ember" else "dagger" if scale >= 15 else "sword" if scale == 10.0 else "staff")
        base, fn = fn, (lambda stage=0, base=fn, kind=kind: base(stage) + legend_finish(kind, seed=len(jp)))
        fn.__doc__ = base.__doc__
    e = dict(jp=jp, tier=tier, glow="thunder", pose=pose, grip=grip, cen=cen, scale=scale,
             opts={"a": ((lambda stage=0: fn(stage)), inlay, ("edge",), (fn.__doc__ or "").split(":", 1)[-1].strip())})
    if stages > 1:
        e["stages"] = stages
    return e


ST = dict(pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 9.0, 0.0), scale=9.5)
HM = dict(pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 7.5, 0.0), scale=10.5)
DG = dict(pose="whale", grip=(0.0, 0.6, 0.0), cen=(0.0, 4.8, 0.0), scale=16.0)
SW = dict(pose="whale", grip=(0.0, 1.0, 0.0), cen=(0.0, 9.0, 0.0), scale=10.0)
WEAPONS = {
    "storm_staff": _w(storm_staff, "雷羽の杖", "rare", inlay=inlay_line(["main"], 15.0, 17.0), **ST),
    "storm_bell_staff": _w(storm_bell_staff, "雷鳴りの鈴杖", "rare", inlay=inlay_line(["main"], 15.5, 19.5), **ST),
    "storm_hammer": _w(storm_hammer, "雷の鎚", "rare", inlay=inlay_line(["main"], 11.0, 14.0), **HM),
    "abyss_storm_sword": _w(abyss_storm_sword, "避雷の大剣", "legend", inlay=inlay_line(["main"], 6.0, 18.0, lambda y: 0.9 + 0.0 * y), **SW),
    "abyss_storm_hammer": _w(abyss_storm_hammer, "雷鼓の鎚", "legend", inlay=inlay_line(["main"], 10.5, 15.5), **HM),
    "abyss_storm_staff": _w(abyss_storm_staff, "雲籠の杖", "legend", inlay=inlay_line(["main"], 14.5, 21.0), **ST),
    "abyss_storm_dagger": _w(abyss_storm_dagger, "雷翼の鉤爪", "legend", inlay=inlay_line(["spine"], 4.0, 10.0), **DG),
    "abyss_storm_bell_staff": _w(abyss_storm_bell_staff, "風鈴の鈴杖", "legend", inlay=inlay_line(["main"], 10.0, 17.0), **ST),
}
