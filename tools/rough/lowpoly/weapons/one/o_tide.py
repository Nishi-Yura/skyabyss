"""潮殻 (二章) の一品一案。章の職の武器 4 本 (エピック) と、潮殻の伝説 5 本。
塗りは潮殻 (../paint.py): main/edge = 真珠、spine = 殻 (緑)、guard/grip = 濡れ石、acc = 珊瑚、wrap = 昆布、cloth = 祭具の帯、gem = 薄紅の光。
"""
from __future__ import annotations

import math

from kit_one import *  # noqa: F401,F403
from kit_one import legend_finish, ell, lump, bone, strand, ringy, gbar, gem, arc, bend, mirror_faces_y, string_and_arrow, inlay_line, CY


def kelp_grip(y0, y1, r=1.1, n=3):
    f = []
    for i in range(n):
        y = y0 + (y1 - y0) * (i + 0.5) / n
        f += prism("wrap", (0, y - 0.55, 0), (0, y + 0.55, 0), r, r * 0.98, sides=6, caps=(False, False), phase=20 * i)
    return f


def fan(c, n, L, spread, w, th, mat="main", tilt=0.0, z=0.0, rib="spine"):
    """帆立の殻: 根 c から放射の板と、板の間の筋 (rib)"""
    f = []
    for i in range(n):
        a = math.radians(tilt + (i - (n - 1) / 2) * spread)
        Li = L * (1 - 0.12 * abs(i - (n - 1) / 2) / max(1, (n - 1) / 2))
        tip = (c[0] + math.sin(a) * Li, c[1] + math.cos(a) * Li, z)
        f += feather(mat, (c[0], c[1], z), tip, w, th, big=False)
        f += curve(rib, [(c[0], c[1], z - th * 0.4), (c[0] + math.sin(a) * Li * 0.9, c[1] + math.cos(a) * Li * 0.9, z - th * 0.4)],
                   [0.18, 0.1], [0.18, 0.1], sides=3, caps=(True, True))
    return f


def shell_fan(c, R, a0, a1, th, ribs=7, mat="main", rib="spine", z=0.0, n=9):
    """帆立の殻: 扇の板 (xy 平面、根 c)、前の面に放射の筋、縁に波の粒"""
    x, y = c
    pts = [(x, y)] + [(x + R * math.cos(math.radians(a0 + (a1 - a0) * i / (n - 1))), y + R * math.sin(math.radians(a0 + (a1 - a0) * i / (n - 1)))) for i in range(n)]
    f = extrude(mat, pts, th, c=(0, 0, z))
    for i in range(ribs):
        a = math.radians(a0 + (a1 - a0) * (i + 0.5) / ribs)
        f += curve(rib, [(x + 0.6 * math.cos(a), y + 0.6 * math.sin(a), z - th * 0.5), (x + R * 0.96 * math.cos(a), y + R * 0.96 * math.sin(a), z - th * 0.5)],
                   [0.16, 0.24], [0.16, 0.26], sides=3, caps=(True, True))
        f += ell("edge", (x + R * math.cos(a), y + R * math.sin(a), z), (0.42, 0.42, th * 0.7), segs=5, rings=1)
    return f


def turret(c, L, r, mat="main", down=True):
    """巻貝 (塔の形): 段の積み + 螺旋の筋。down = 口が下"""
    x, y, z = c
    s = -1 if down else 1
    f = []
    for i in range(4):
        y0 = y + s * L * i / 4
        y1 = y + s * L * (i + 1) / 4
        r0 = r * (1 - 0.22 * i)
        f += prism(mat, (x, y0, z), (x, y1, z), r0, r0 * 0.72, sides=7, caps=(i == 0, i == 3), phase=25 * i)
    f += spike(BONE, mat, (x, y + s * L, z), (0, s, 0), L * 0.25, r * 0.3, sides=5, rings=1, hint=(0, 0, 1))
    return f


# ================================================================== 章の武器 (エピック)
def tide_staff(stage=0):
    """潮殻の真珠杖: 柄の先で帆立の殻が口を開け、上の殻の窪みに大きな真珠が灯る。蝶番から珊瑚の枝、真珠の雫が下がる"""
    f = []
    f += prism("grip", (0, -4.0, 0), (0, 14.4, 0), 0.9, 0.85, sides=6, caps=(True, True))
    f += kelp_grip(-1.4, 3.4)
    f += lump("guard", (0, -4.3, 0), (1.2, 1.0, 1.2), seed=31)
    f += barnacles((0.9, -3.8, 0), n=3, spread=0.5, r=0.3, h=0.5, dirv=(1, 0, 0), mat="edge")
    f += ringy("trim", 3.8, 1.2, 0.5)
    f += ringy("trim", 13.6, 1.2, 0.5)
    # 下の殻 (受け): 浅い扇、口は下向きに少し開く
    f += shell_fan((0, 15.0), 3.0, 200, 340, 0.6, ribs=5, z=0.4)
    # 上の殻 (立てた扇) と、窪みの真珠
    f += shell_fan((0, 14.6), 6.0, 15, 165, 0.7, ribs=8, z=0.7)
    for s in (1, -1):
        f += extrude("edge", [(s * 0.6, 14.2), (s * 1.9, 14.6), (s * 1.9, 15.6), (s * 0.6, 15.4)][::s], 0.8, c=(0, 0, 0.4))
    f += ell("edge", (0, 14.8, 0.2), (1.3, 0.9, 1.0), segs=6, rings=1)
    f += gem((0, 18.6, -0.6), 1.65, 1.65, 1.65, segs=8, rings=3)
    f += torus("trim", (0, 18.6, -0.2), 1.7, 0.18, 0.22, n=10, k=3, ax="z")
    # 蝶番の珊瑚の枝 (左右へ)
    for s in (1, -1):
        f += curve("acc", [(s * 0.6, 14.2, 0), (s * 2.2, 15.0, -0.2), (s * 2.8, 16.6, -0.3)], [0.4, 0.32, 0.12], [0.4, 0.32, 0.12], sides=4, caps=(False, True))
        f += curve("acc", [(s * 2.0, 14.9, -0.2), (s * 3.2, 14.6, -0.3), (s * 3.8, 15.6, -0.3)], [0.3, 0.22, 0.1], [0.3, 0.22, 0.1], sides=4, caps=(False, True))
    # 真珠の雫 (帯に下がる)
    f += sash([(1.0, 13.8, -0.9), (1.5, 11.6, -1.0), (1.3, 9.4, -0.9)], 0.55, mat="cloth")
    f += gem((1.3, 8.8, -0.9), 0.45, 0.5, 0.45, segs=5, rings=1)
    f += gem((-1.2, 12.2, -0.9), 0.45, 0.5, 0.45, segs=5, rings=1)
    f += prism("cord", (-1.2, 13.8, -0.9), (-1.2, 12.6, -0.9), 0.15, 0.15, sides=3, caps=(False, False))
    return f


def tide_bell_staff(stage=0):
    """潮鳴りの鈴杖: 柄の先から珊瑚が角のように枝分かれし、枝ごとに口を下にした巻貝の鐘が下がる。分かれ目にヒトデ"""
    f = []
    f += prism("grip", (0, -4.0, 0), (0, 13.2, 0), 0.9, 0.85, sides=6, caps=(True, False))
    f += kelp_grip(-1.4, 3.4)
    f += spike(BONE, "guard", (0, -4.0, 0), (0, -1, 0), 1.8, 1.0, sides=6, rings=1, hint=(0, 0, 1))
    f += ringy("trim", 3.8, 1.2, 0.5)
    # 珊瑚の幹と枝 (xy 平面に広がる)
    f += curve("acc", [(0, 12.6, 0), (0.2, 15.0, 0), (-0.2, 18.6, 0), (0.3, 21.4, 0)], [0.85, 0.72, 0.55, 0.25], [0.85, 0.72, 0.55, 0.25], sides=6, caps=(False, True))
    branches = [((0.1, 15.2), (2.6, 16.8), (4.4, 16.2)), ((-0.1, 16.4), (-2.6, 18.2), (-4.2, 17.6)), ((0.0, 19.0), (2.0, 20.4), (2.8, 22.0))]
    tips = []
    for a, b, c in branches:
        f += curve("acc", [(a[0], a[1], 0), (b[0], b[1], 0), (c[0], c[1], 0)], [0.55, 0.42, 0.22], [0.55, 0.42, 0.22], sides=5, caps=(False, True))
        f += curve("acc", [(b[0], b[1], 0), (b[0] + 0.2, b[1] + 1.6, -0.3)], [0.3, 0.12], [0.3, 0.12], sides=4, caps=(False, True))
        tips.append(c)
    # 巻貝の鐘 (口が下、昆布の紐で吊る)
    for (x, y), L in zip(tips[:2], (3.2, 3.0)):
        f += prism("wrap", (x, y, 0), (x, y - 1.6, 0), 0.18, 0.18, sides=4, caps=(False, False))
        f += turret((x, y - 1.6 - L, 0), L, 1.25, mat="main", down=False)
        f += ell("dark", (x, y - 1.7 - L, 0), (0.9, 0.3, 0.9), segs=6, rings=1)
        f += gem((x, y - 2.1 - L, 0), 0.4, 0.4, 0.4, segs=5, rings=1)
    f += prism("wrap", (-0.9, 12.4, -0.6), (-0.9, 10.4, -0.6), 0.16, 0.16, sides=4, caps=(False, False))
    f += turret((-0.9, 7.8, -0.6), 2.4, 0.9, mat="edge", down=False)
    # ヒトデ (分かれ目の正面に張り付く)
    for k in range(5):
        a = math.radians(90 + 72 * k)
        f += spike(BONE, "acc", (0, 15.6, -0.8), (math.cos(a), math.sin(a), -0.1), 1.6, 0.45, sides=4, rings=1, hint=(0, 0, 1))
    f += ell("edge", (0, 15.6, -0.9), (0.55, 0.55, 0.35), segs=5, rings=1)
    f += barnacles((0.6, 13.4, 0.5), n=3, spread=0.4, r=0.25, h=0.4, dirv=(0, 0, 1), mat="edge")
    return f


def tide_hammer(stage=0):
    """潮殻の鎚: 濡れ石の頭の打つ面に、潮殻の殻の板を三枚、瓦のように重ねて打ち付けた鎚。背は蟹のはさみの鉤、柄は昆布巻き"""
    f = []
    f += prism("grip", (0, -3.6, 0), (0, 11.0, 0), 0.95, 0.9, sides=6, caps=(True, True))
    f += kelp_grip(-2.6, 2.4)
    f += lump("acc", (0, -4.0, 0), (1.1, 1.0, 1.1), seed=41, segs=6, rings=2)
    f += ringy("trim", 2.8, 1.2, 0.5)
    # 頭: 濡れ石の箱 (x へ長い)
    f += lump("guard", (0.2, 12.4, 0), (3.2, 2.2, 1.9), seed=43, segs=8, rings=3, rough=0.1)
    f += block("trim", (0.4, 10.4, 0), (3.6, 0.5, 3.4))
    # 打つ面 (+x) の殻の板: 四枚が上から瓦のように重なる (殻の緑、縁は真珠、打ち付けた鋲)
    for i, (y, h) in enumerate(((14.2, 1.7), (13.0, 1.8), (11.8, 1.8), (10.7, 1.4))):
        f += block("spine", (3.2 + 0.25 * i, y, 0), (1.0, h, 4.2 - 0.25 * i), rot=rot_z(-12), taper=(0.6, 0.92))
        f += block("edge", (3.75 + 0.25 * i, y - h * 0.46, 0), (0.4, 0.4, 4.0 - 0.25 * i), rot=rot_z(-12))
        for z in (-1.2, 1.2):
            f += ell("trim", (3.85 + 0.25 * i, y + 0.2, z), (0.28, 0.28, 0.28), segs=5, rings=1)
    # 上に伸びた珊瑚
    f += curve("acc", [(-0.6, 14.2, 0.4), (-0.9, 15.8, 0.5), (-0.2, 17.0, 0.4)], [0.45, 0.35, 0.12], [0.45, 0.35, 0.12], sides=4, caps=(False, True))
    f += curve("acc", [(-0.9, 15.8, 0.5), (-2.0, 16.6, 0.5)], [0.28, 0.1], [0.28, 0.1], sides=4, caps=(False, True))
    # 背: 蟹のはさみ (上下二本の鉤)
    f += curve("acc", [(-2.2, 12.8, 0), (-4.0, 13.6, 0), (-5.4, 13.0, 0)], [0.85, 0.6, 0.15], [0.7, 0.5, 0.12], sides=5, caps=(False, True))
    f += curve("acc", [(-2.2, 11.8, 0), (-3.8, 11.0, 0), (-4.8, 11.8, 0)], [0.7, 0.5, 0.12], [0.6, 0.42, 0.1], sides=5, caps=(False, True))
    for z in (-1.7, 1.7):
        f += barnacles((-0.6, 12.6, z), n=4, spread=0.8, r=0.3, h=0.5, dirv=(0, 0, z / 1.7), mat="edge")
    f += sash([(0.6, 10.6, -1.7), (1.2, 8.6, -1.8), (0.8, 6.8, -1.7)], 0.6, mat="cloth")
    f += gem((0.4, 12.4, -1.65), 0.55, 0.55, 0.3, segs=5, rings=1)
    return f


def tide_bow(stage=0):
    """珊瑚の弓: 腕は枝を伸ばした珊瑚そのもの (外へ小枝が何本も)。握りは貝の板、先に真珠"""
    f = []
    f += prism("grip", (5.0, CY - 2.8, 0), (5.0, CY + 2.8, 0), 1.0, 1.0, sides=6, caps=(False, False), hint=(0, 0, 1))
    for y in (CY - 1.2, CY, CY + 1.2):
        f += block("main", (5.4, y, 0), (1.4, 1.3, 2.4), rot=rot_z(10), taper=(0.7, 0.9))
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.5, CY + 5.0, 0), (3.0, CY + 7.6, 0), (0.9, CY + 9.6, 0), (-1.4, CY + 10.6, 0)])
    half = []
    half += curve("acc", up, [1.0, 0.92, 0.78, 0.6, 0.38], [1.05, 0.95, 0.8, 0.62, 0.4], sides=5, caps=(False, True), hint=(0, 0, 1))
    # 小枝 (外へ)。枝の先にまた小さな枝
    for i, (L, a) in enumerate(((2.4, 35), (2.0, 55), (1.6, 70))):
        x, y, _ = up[i + 1]
        d = (math.cos(math.radians(a)), math.sin(math.radians(a)), 0.15 * (-1) ** i)
        tip = (x + 0.5 + d[0] * L, y + d[1] * L, d[2] * L)
        half += curve("acc", [(x + 0.5, y, 0), tip], [0.42, 0.15], [0.42, 0.15], sides=4, caps=(False, True))
        half += curve("acc", [((x + 0.5 + tip[0]) / 2, (y + tip[1]) / 2, tip[2] / 2), ((x + tip[0]) / 2 + 1.0, (y + tip[1]) / 2 - 0.2, tip[2])],
                      [0.25, 0.1], [0.25, 0.1], sides=4, caps=(False, True))
    tx, ty, _ = up[-1]
    half += gem((tx, ty, 0), 0.5, 0.5, 0.5, segs=5, rings=1, mat="edge")
    half += prism("wrap", (up[1][0], up[1][1] - 0.4, 0), (up[1][0], up[1][1] + 0.4, 0), 1.15, 1.15, sides=6, caps=(True, True), hint=(0, 0, 1))
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    f += gem((6.2, CY, 0), 0.5, 0.75, 0.6, segs=5, rings=1)
    f += sash([(4.6, CY - 2.2, -1.1), (4.9, CY - 4.4, -1.2), (4.5, CY - 6.0, -1.1)], 0.5, mat="cloth")
    return f + string_and_arrow(stage, "rare", up, lo)


# ================================================================== 伝説
def abyss_tide_sword(stage=0):
    """螺旋貝の大剣: 刃は一本の長い巻貝 (段の積みと螺旋の筋)。鍔は貝の口の唇が広がった形で、内側は薄紅、奥に淵。柄は真珠を巻いて金の輪"""
    f = []
    f += prism("grip", (0, -3.6, 0), (0, 2.6, 0), 0.95, 0.9, sides=8, caps=(True, False))
    f += kelp_grip(-2.6, 2.0, r=1.05, n=3)
    for y in (-2.8, 2.4):
        f += ringy("trim", y, 1.15, 0.45)
    f += turret((0, -3.6, 0), 2.0, 1.1, mat="edge", down=True)
    # 鍔: 貝の口の唇 (広がる板が三枚)、内側の薄紅、奥の淵
    f += extrude("edge", [(-4.2, 3.4), (-2.6, 2.4), (2.6, 2.4), (4.2, 3.4), (3.0, 4.6), (-3.0, 4.6)], 1.6, c=(0, 0, 0))
    f += extrude("acc", [(-3.0, 4.6), (3.0, 4.6), (2.4, 5.4), (-2.4, 5.4)], 1.2, c=(0, 0, -0.3))
    f += ell("void", (0, 4.6, -0.9), (1.6, 0.8, 0.5), segs=6, rings=1)
    for s in (1, -1):
        f += spike(BONE, "edge", (s * 4.0, 3.6, 0), (s, -0.4, 0), 1.8, 0.5, sides=4, rings=1, hint=(0, 0, 1))
        f += torus("trim", (s * 3.0, 3.8, 0), 0.5, 0.15, 0.2, n=6, k=3, ax="z")
    # 刃: 巻貝の段 (下から 9 段、細りながら)。段の継ぎに螺旋の筋 (殻の色)
    n = 9
    for i in range(n):
        y0 = 5.0 + i * 1.75
        r0 = 1.9 * (1 - 0.095 * i)
        f += prism("main", (0, y0, 0), (0, y0 + 1.75, 0), r0, r0 * 0.82, sides=8, caps=(False, False), phase=22 * i)
        a = math.radians(40 * i)
        f += curve("spine", [(r0 * 0.95 * math.cos(a), y0 + 0.2, r0 * 0.95 * math.sin(a)), (r0 * 0.86 * math.cos(a + 1.6), y0 + 1.0, r0 * 0.86 * math.sin(a + 1.6)),
                             (r0 * 0.8 * math.cos(a + 3.0), y0 + 1.7, r0 * 0.8 * math.sin(a + 3.0))], [0.22, 0.22, 0.2], [0.22, 0.22, 0.2], sides=3, caps=(True, True))
    f += spike(BONE, "edge", (0, 5.0 + n * 1.75, 0), (0, 1, 0), 2.4, 0.38, sides=6, rings=1, hint=(0, 0, 1))
    # 刃に付いた小さな藤壺と真珠
    f += barnacles((1.2, 8.0, -1.2), n=3, spread=0.4, r=0.25, h=0.4, mat="guard")
    f += gem((-1.1, 10.6, -1.1), 0.35, 0.35, 0.35, segs=5, rings=1)
    f += gem((0.9, 14.2, -0.9), 0.3, 0.3, 0.3, segs=5, rings=1)
    return f


def abyss_tide_hammer(stage=0):
    """大蛤の鎚: 閉じた大蛤が頭。波打つ合わせ目の奥で真珠が光り、殻には放射の筋。蝶番が柄を噛み、まわりに珊瑚と昆布"""
    f = []
    f += prism("grip", (0, -4.0, 0), (0, 11.6, 0), 1.0, 0.95, sides=8, caps=(True, True))
    f += kelp_grip(-3.0, 2.2, r=1.15)
    for y in (-3.4, 2.8, 7.2):
        f += ringy("trim", y, 1.2, 0.45)
    f += lump("acc", (0, -4.4, 0), (1.2, 1.0, 1.2), seed=51)
    # 蛤の二枚の殻 (x 向きの打つ面: 殻の膨らみ。合わせ目は xy 平面)
    for s in (1, -1):
        f += ell("main", (0, 13.6, s * 0.75), (4.6, 3.0, 1.55), segs=10, rings=3,
                 deform=lambda p, s=s: (p if p[2] * s > 0 else p * [1, 1, 0.12]) * [1, (1.0 if p[1] > 0 else 0.8), 1])
        for k in range(7):
            a = math.radians(20 + 23.3 * k)
            f += curve("spine", [(0, 11.4, s * 1.5), (4.4 * math.cos(a), 13.6 + 2.8 * math.sin(a), s * 1.05)],
                       [0.22, 0.2], [0.22, 0.2], sides=3, caps=(True, True))
        f += ell("edge", (0, 11.6, s * 1.4), (1.1, 0.8, 0.6), segs=6, rings=1)
    # 合わせ目 (波打つ縁) と、奥の真珠と淵
    pts = [(4.5 * math.cos(math.radians(a)), 13.6 + (2.9 if a <= 180 and a >= 0 else 2.3) * math.sin(math.radians(a)) + 0.25 * math.sin(math.radians(a * 6)), 0)
           for a in range(-170, 181, 17)]
    f += curve("trim", pts, [0.28] * len(pts), [0.36] * len(pts), sides=4, caps=(True, True), hint=(0, 0, 1))
    f += ell("void", (0, 14.2, 0), (3.0, 1.6, 0.6), segs=8, rings=1)
    f += gem((1.6, 13.9, -0.3), 1.0, 1.0, 1.0, segs=6, rings=2)
    # 蝶番 (柄を噛む)
    f += prism("guard", (0, 10.4, -1.6), (0, 10.4, 1.6), 1.2, 1.2, sides=8, caps=(True, True), hint=(1, 0, 0))
    f += prism("trim", (0, 10.4, -1.8), (0, 10.4, 1.8), 0.6, 0.6, sides=6, caps=(True, True), hint=(1, 0, 0))
    # 珊瑚と昆布
    f += curve("acc", [(-2.0, 11.2, 1.6), (-3.4, 10.4, 2.0), (-4.2, 11.6, 2.0)], [0.4, 0.3, 0.12], [0.4, 0.3, 0.12], sides=4, caps=(False, True))
    f += curve("acc", [(-3.4, 10.4, 2.0), (-3.8, 9.2, 2.2)], [0.25, 0.1], [0.25, 0.1], sides=4, caps=(False, True))
    f += strand("wrap", (1.8, 11.0, -1.6), 3.0, 0.5, r=0.32)
    f += strand("wrap", (-1.2, 10.8, -1.4), 2.2, 0.4, r=0.26)
    f += barnacles((2.2, 15.4, 1.7), n=4, spread=0.7, r=0.28, h=0.5, dirv=(0, 0.5, 1), mat="guard")
    return f


def abyss_tide_staff(stage=0):
    """渦潮の杖: 先はオウム貝の巻いた殻。殻の口から渦潮が三重の輪になって回り、輪のあいだを真珠がめぐる。柄はねじれた殻の柱"""
    f = []
    f += prism("grip", (0, -4.0, 0), (0, 3.2, 0), 1.0, 1.0, sides=8, caps=(True, False))
    f += kelp_grip(-1.8, 2.8, r=1.12)
    # ねじれた殻の柱 (二本の殻の筒が螺旋に巻き合う)
    for ph in (0, 180):
        pts = [(0.55 * math.cos(math.radians(ph + 50 * i)), 3.0 + 1.3 * i, 0.55 * math.sin(math.radians(ph + 50 * i))) for i in range(10)]
        f += curve("spine", pts, [0.62] * 10, [0.62] * 10, sides=5, caps=(True, True))
    for y in (3.2, 9.6, 14.6):
        f += ringy("trim", y, 1.25, 0.5)
    f += turret((0, -4.0, 0), 1.8, 1.0, mat="edge", down=True)
    # オウム貝: 巻いた殻 (外側の大きな巻きと内側の小さな巻き)。xy 平面
    cx, cy = 0.3, 18.2
    for i in range(10):
        a0, a1 = 36 * i, 36 * (i + 1)
        r0, r1 = 3.4 * 0.9 ** i, 3.4 * 0.9 ** (i + 1)
        w0, w1 = 1.5 * 0.88 ** i, 1.5 * 0.88 ** (i + 1)
        p0 = (cx + r0 * math.cos(math.radians(a0)), cy + r0 * math.sin(math.radians(a0)), 0)
        p1 = (cx + r1 * math.cos(math.radians(a1)), cy + r1 * math.sin(math.radians(a1)), 0)
        f += curve("main", [p0, p1], [w0, w1], [w0 * 0.9, w1 * 0.9], sides=6, caps=(i == 0, i == 9), hint=(0, 0, 1))
        if i % 2 == 0:
            f += curve("acc", [p0, ((p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2, -w0 * 0.85)], [0.18, 0.18], [0.18, 0.18], sides=3, caps=(True, True))
    f += ell("void", (cx + 3.4, cy, 0), (0.9, 1.4, 1.2), segs=6, rings=2)
    # 渦潮の輪 (傾いた三重の光の輪) と、めぐる真珠
    for k, (R, tilt) in enumerate(((4.4, 18), (5.2, -24), (6.0, 8))):
        ring_ = []
        for i in range(12):
            a = math.radians(30 * i + 20 * k)
            ring_.append((cx + R * math.cos(a), cy + R * 0.45 * math.sin(a) * math.cos(math.radians(tilt)), R * math.sin(a) * 0.9))
        ring_.append(ring_[0])
        f += curve("gem", ring_, [0.12] * 13, [0.16] * 13, sides=3, caps=(False, False))
        a = math.radians(60 + 110 * k)
        f += gem((cx + R * math.cos(a), cy + R * 0.45 * math.sin(a), R * math.sin(a) * 0.9), 0.45, 0.45, 0.45, segs=5, rings=1, mat="edge")
    f += strand("wrap", (0.9, 14.4, -0.9), 3.2, 0.5, r=0.3)
    f += barnacles((-1.0, 15.0, 0.8), n=3, spread=0.4, r=0.26, h=0.45, dirv=(0, 0, 1), mat="guard")
    return f


def abyss_tide_bow(stage=0):
    """鰭の弓: 腕は大魚の鰭。背の骨から放射の鰭条が並び、あいだに薄い膜。握りは鱗の板と金、淵の核"""
    f = []
    f += prism("grip", (5.0, CY - 2.8, 0), (5.0, CY + 2.8, 0), 1.05, 1.05, sides=8, caps=(False, False), hint=(0, 0, 1))
    for i, y in enumerate((CY - 2.0, CY - 0.7, CY + 0.7, CY + 2.0)):
        f += block("spine", (5.5, y, 0), (1.1, 1.4, 2.3), rot=rot_z(14), taper=(0.6, 0.85))
    f += prism("trim", (5.0, CY - 3.1, 0), (5.0, CY - 2.6, 0), 1.25, 1.25, sides=8, caps=(True, True), hint=(0, 0, 1))
    f += prism("trim", (5.0, CY + 2.6, 0), (5.0, CY + 3.1, 0), 1.25, 1.25, sides=8, caps=(True, True), hint=(0, 0, 1))
    f += ell("void", (6.3, CY, 0), (0.7, 1.2, 0.8), segs=6, rings=2)
    f += gem((6.8, CY, 0), 0.4, 0.6, 0.5, segs=5, rings=1)
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.6, CY + 5.0, 0), (3.2, CY + 7.6, 0), (1.2, CY + 9.6, 0), (-1.2, CY + 10.8, 0)])
    half = []
    half += curve("edge", up, [0.6, 0.55, 0.48, 0.38, 0.25], [0.7, 0.62, 0.55, 0.42, 0.28], sides=5, caps=(False, True), hint=(0, 0, 1))
    # 鰭条 (外 +x 上へ放射) と膜
    rays = []
    for i in range(6):
        u = 0.1 + 0.15 * i
        k = min(int(u * 4), 3)
        t = u * 4 - k
        p = [up[k][j] + (up[k + 1][j] - up[k][j]) * t for j in range(3)]
        a = math.radians(20 + 12 * i)
        L = 3.6 * (1 - 0.45 * u)
        tip = (p[0] + math.cos(a) * L, p[1] + math.sin(a) * L * 0.6, 0)
        half += curve("main", [tuple(p), tip], [0.22, 0.08], [0.22, 0.08], sides=3, caps=(False, True))
        rays.append((p, tip))
    for (p0, t0), (p1, t1) in zip(rays, rays[1:]):
        half += extrude("acc", [(p0[0], p0[1]), (t0[0] * 0.9 + p0[0] * 0.1, t0[1] * 0.9 + p0[1] * 0.1), (t1[0] * 0.9 + p1[0] * 0.1, t1[1] * 0.9 + p1[1] * 0.1), (p1[0], p1[1])],
                        0.14, c=(0, 0, 0))
    tx, ty, _ = up[-1]
    half += gem((tx, ty, 0), 0.45, 0.45, 0.45, segs=5, rings=1, mat="edge")
    half += prism("trim", (up[1][0], up[1][1] - 0.3, 0), (up[1][0], up[1][1] + 0.3, 0), 0.85, 0.85, sides=6, caps=(True, True), hint=(0, 0, 1))
    f += half + mirror_faces_y(half)
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    f += sash([(4.4, CY - 2.6, -1.1), (4.8, CY - 4.6, -1.2), (4.4, CY - 6.4, -1.1)], 0.5, mat="cloth")
    return f + string_and_arrow(stage, "legend", up, lo)


def abyss_tide_dagger(stage=0):
    """海胆の針: 刃は大海胆の長い一本の棘 (細い円錐と縦の筋)。鍔は海胆の殻そのもので、短い棘がぐるりと立つ。柄は真珠巻きに金"""
    f = []
    f += prism("grip", (0, -3.0, 0), (0, 2.2, 0), 0.82, 0.8, sides=8, caps=(True, False))
    for y in (-2.0, -0.6, 0.8):
        f += ell("edge", (0, y, 0), (0.98, 0.62, 0.98), segs=7, rings=1)
    f += ringy("trim", -2.9, 0.95, 0.4)
    f += ringy("trim", 2.0, 0.95, 0.4)
    f += gem((0, -3.7, 0), 0.55, 0.65, 0.55, segs=6, rings=1, mat="void")
    # 海胆の殻 (平たい球) と放射の短い棘
    f += ell("spine", (0, 3.6, 0), (2.4, 1.5, 2.4), segs=10, rings=3)
    for k in range(10):
        a = math.radians(36 * k + 18)
        for lat, L in ((0.0, 1.6), (0.6, 1.1)):
            d = (math.cos(a) * math.cos(lat), math.sin(lat), math.sin(a) * math.cos(lat))
            f += spike(BONE, "acc", (2.1 * d[0], 3.6 + 1.2 * d[1], 2.1 * d[2]), d, L, 0.22, sides=3, rings=1, hint=(0, 1, 0))
    f += torus("trim", (0, 3.6, 0), 2.3, 0.16, 0.2, n=10, k=3, ax="y")
    # 刃 (長い棘)。根に淵の環
    f += prism("main", (0, 4.6, 0), (0, 12.8, 0), 0.8, 0.3, sides=8, caps=(False, False))
    f += spike(BONE, "main", (0, 12.8, 0), (0, 1, 0), 2.2, 0.3, sides=8, rings=1, hint=(0, 0, 1))
    for k in range(4):
        a = math.radians(90 * k + 22)
        f += curve("edge", [(0.72 * math.cos(a), 5.0, 0.72 * math.sin(a)), (0.2 * math.cos(a), 12.8, 0.2 * math.sin(a))], [0.12, 0.05], [0.12, 0.05], sides=3, caps=(True, True))
    f += prism("void", (0, 4.8, 0), (0, 5.4, 0), 0.95, 0.9, sides=8, caps=(True, True))
    f += strand("wrap", (1.2, 3.0, -1.6), 2.2, 0.4, r=0.24)
    return f


def tide_dagger(stage=0):
    """馬刀貝の短剣: 細長い馬刀貝の殻を刃にした短剣。真珠色の殻に育ちの筋が並び、根を小さな帆立の殻が受けて鍔になる。柄は昆布巻き、柄頭は真珠"""
    f = prism("grip", (0, -3.2, 0), (0, 3.0, 0), 0.8, 0.8, sides=6, caps=(True, True))
    f += kelp_grip(-2.6, 2.4, r=0.95, n=3)
    f += ringy("spine", -2.9, 1.05, 0.35)
    f += pearl((0, -3.9, 0), 0.85, mat="main")
    f += gem((0, -3.9, -0.7), 0.3, 0.3, 0.2, segs=5, rings=1)
    # 鍔: 上へ開いた小さな帆立の殻 (刃の後ろ)、根に珊瑚の小枝
    f += shell_fan((0, 2.6), 2.7, 15, 165, 0.5, ribs=5, z=0.45)
    f += prism("guard", (0, 2.6, 0), (0, 4.0, 0), 1.0, 0.9, sides=6, caps=(True, True))
    f += curve("acc", [(-0.8, 3.4, -0.5), (-1.6, 4.6, -0.6), (-1.9, 5.6, -0.6)], [0.26, 0.2, 0.1], [0.26, 0.2, 0.1], sides=4, caps=(True, True))
    f += curve("acc", [(-1.5, 4.4, -0.6), (-2.4, 4.9, -0.6)], [0.16, 0.08], [0.16, 0.08], sides=4, caps=(True, True))
    # 刃: 馬刀貝 (細長い板、少し反って先は丸く尖る)
    f += blade("main", [(3.8, -0.95, 0.95, 0.5, 0.5), (7.0, -1.0, 0.9, 0.5, 0.5, 0.1), (10.4, -0.95, 0.8, 0.45, 0.45, 0.25),
                        (12.8, -0.75, 0.6, 0.38, 0.38, 0.4), (14.0, -0.35, 0.25, 0.25, 0.25, 0.45), (14.6, 0, 0, 0, 0, 0.45)])
    # 育ちの筋 (殻を横切る細い帯、前の面)
    for y, cx in ((5.4, 0.0), (7.2, 0.1), (9.0, 0.18), (10.8, 0.27), (12.4, 0.37)):
        hw = 0.62 if y < 11 else 0.45
        f += curve("spine", [(cx - hw, y, -0.47), (cx + hw, y + 0.2, -0.47)], [0.11, 0.11], [0.09, 0.09], sides=3, caps=(True, True))
    f += gbar([(0.0, 4.6, -0.48), (0.15, 8.4, -0.46), (0.35, 12.0, -0.38)], r=0.15)
    return f


# ================================================================== 一覧
def _w(fn, jp, tier, pose, grip, cen, scale, inlay, stages=1):
    if tier == "legend":
        kind = {"thunder": "bow"}.get(pose) or ("hammer" if pose == "ember" else "dagger" if scale >= 15 else "sword" if scale == 10.0 else "staff")
        base, fn = fn, (lambda stage=0, base=fn, kind=kind: base(stage) + legend_finish(kind, seed=len(jp)))
        fn.__doc__ = base.__doc__
    e = dict(jp=jp, tier=tier, glow="tide", pose=pose, grip=grip, cen=cen, scale=scale,
             opts={"a": ((lambda stage=0: fn(stage)), inlay, ("edge",), (fn.__doc__ or "").split(":", 1)[-1].strip())})
    if stages > 1:
        e["stages"] = stages
    return e


ST = dict(pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 9.0, 0.0), scale=9.5)
HM = dict(pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 7.5, 0.0), scale=10.5)
BW = dict(pose="thunder", grip=(5.0, 8.0, 0.0), cen=(2.0, 8.0, 0.0), scale=12.0, stages=4)
DG = dict(pose="whale", grip=(0.0, 0.6, 0.0), cen=(0.0, 4.8, 0.0), scale=16.0)
SW = dict(pose="whale", grip=(0.0, 1.0, 0.0), cen=(0.0, 9.0, 0.0), scale=10.0)
WEAPONS = {
    "tide_dagger": _w(tide_dagger, "馬刀貝の短剣", "rare", inlay=inlay_line(["main"], 4.5, 13.0), **DG),
    "tide_staff": _w(tide_staff, "潮殻の真珠杖", "rare", inlay=inlay_line(["main"], 15.0, 21.0), **ST),
    "tide_bell_staff": _w(tide_bell_staff, "潮鳴りの鈴杖", "rare", inlay=inlay_line(["main"], 10.0, 17.0), **ST),
    "tide_hammer": _w(tide_hammer, "潮殻の鎚", "rare", inlay=inlay_line(["spine"], 10.5, 14.5), **HM),
    "tide_bow": _w(tide_bow, "珊瑚の弓", "rare", inlay=inlay_line(["acc"], 9.0, 18.0), **BW),
    "abyss_tide_sword": _w(abyss_tide_sword, "螺旋貝の大剣", "legend", inlay=inlay_line(["spine"], 5.0, 20.0), **SW),
    "abyss_tide_hammer": _w(abyss_tide_hammer, "大蛤の鎚", "legend", inlay=inlay_line(["grip"], 3.0, 9.0), **HM),
    "abyss_tide_staff": _w(abyss_tide_staff, "渦潮の杖", "legend", inlay=inlay_line(["main"], 15.0, 22.0), **ST),
    "abyss_tide_bow": _w(abyss_tide_bow, "鰭の弓", "legend", inlay=inlay_line(["edge"], 9.0, 18.0), **BW),
    "abyss_tide_dagger": _w(abyss_tide_dagger, "海胆の針", "legend", inlay=inlay_line(["edge"], 5.0, 13.0), **DG),
}
