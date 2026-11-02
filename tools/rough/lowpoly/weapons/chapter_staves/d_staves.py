"""章ごとの杖と鈴杖 (docs/design/43 の 5 段目)。巨神の装備の流れに、魔法使いの杖と呪術師の鈴杖を一本ずつ足す。8 品 x 3 案の形。
材質名は巨神の武器と同じ (main edge spine guard grip wrap acc gem trim dark cord)。塗りは ../paint.py の巨神ごとの塗り:
  岩鯨 = 鯨骨 (main/edge)・石 (spine/guard/grip)・苔 (wrap/acc)・水色の光
  潮殻 = 真珠 (main/edge)・殻 (spine)・濡れ石 (guard/grip)・珊瑚 (acc)・昆布 (wrap)・薄紅の光
  雷翼 = 銅 (main/edge)・鋼 (spine/guard/grip)・羽 (acc)・青白い光
  余燼 = 玄武岩 (main)・鉄 (edge/spine)・焼けた岩 (guard/acc)・柄の木 (grip)・灰 (wrap)・橙の光
座標は他の武器と同じ (1 単位 = 1/16 ブロック、+Y が先、x = 0・z = 0 が握り、広い面は ±Z、正面 = -Z)。部品は親へ沈めて継ぐ。
階級はエピック (形の段 rare)。

  WEAPONS[id] = dict(jp, tier, glow (巨神), pose, grip, cen, scale, opts={"a": (形の関数, inlay, edge_mats, 一言), ...})
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE.parent.parent))
from wkit import *  # noqa: F401,F403,E402
from wkit import block, prism, curve, extrude, V, rot_x, rot_y, rot_z  # noqa: E402
from paint import line_mask  # noqa: E402

import numpy as np  # noqa: E402
from lpkit import ellipsoid, spike, loft  # noqa: E402


def circ(y, r, n=8, cx=0.0, cz=0.0, ph=0.0):
    return [V(cx + r * math.cos(math.radians(ph + 360.0 * k / n)), y, cz + r * math.sin(math.radians(ph + 360.0 * k / n))) for k in range(n)]


def ell(mat, c, r, segs=6, rings=2, rot=None):
    return ellipsoid(BONE, mat, c, r, segs=segs, rings=rings, rot=rot)


def inlay(env, kind):
    """光る筋: エピック (rare) は柄の上に短い筋、+5 で点線、+10 で長い筋"""
    if kind == "rare":
        return line_mask(env, ["spine"], lambda y: 0.0 * y, 9.0, 12.0, 0.35)
    if kind == "p5":
        return line_mask(env, ["spine"], lambda y: 0.0 * y, 6.0, 14.0, 0.35, dash=(1.6, 1.0))
    if kind == "p10":
        return line_mask(env, ["spine"], lambda y: 0.0 * y, 5.0, 15.0, 0.45)
    return env["lit"] < -9


def shaft(top=15.0, grip="grip", upper="spine"):
    """杖の柄: 握り (布の巻き)、石突き、上の柄。上の柄の材質で巨神の色が出る"""
    f = []
    f += prism(grip, (0, -4.0, 0), (0, -0.2, 0), 1.05, 1.0, sides=6, caps=(True, False))
    f += prism("wrap", (0, -0.4, 0), (0, 3.6, 0), 1.18, 1.18, sides=6, caps=(False, False))
    f += prism(upper, (0, 3.4, 0), (0, top, 0), 1.0, 0.9, sides=6, caps=(False, True))
    f += block("trim", (0, 3.6, 0), (2.5, 0.7, 2.5))
    f += spike(BONE, "guard", (0, -4.0, 0), (0, -1, 0), 1.8, 1.1, sides=6, rings=1, hint=(0, 0, 1))
    return f


def bell(cx, cy_mouth, h, r_mouth, r_top, mat="acc", sides=8, cz=0.0):
    """釣り鐘 (口が下)。口の縁に縁の金属、口は暗い板で塞ぐ"""
    rings = [circ(cy_mouth, r_mouth, sides, cx, cz), circ(cy_mouth + h * 0.2, r_mouth * 0.9, sides, cx, cz),
             circ(cy_mouth + h * 0.6, r_top * 1.4, sides, cx, cz), circ(cy_mouth + h, r_top, sides, cx, cz)]
    f = loft(BONE, mat, rings, caps=("dark", mat))
    f += prism("trim", (cx, cy_mouth - 0.2, cz), (cx, cy_mouth + 0.4, cz), r_mouth + 0.3, r_mouth + 0.2, sides=sides, caps=(True, True))
    return f


def ring(cy, R, mat="trim", n=10, w=0.55, t=0.7, sy=1.0):
    f = []
    for k in range(n):
        a0 = math.radians(360.0 * k / n)
        a1 = math.radians(360.0 * (k + 1) / n)
        f += curve(mat, [(R * math.cos(a0), cy + R * sy * math.sin(a0), 0), (R * math.cos(a1), cy + R * sy * math.sin(a1), 0)], [w, w], [t, t], sides=4, caps=(True, True))
    return f


def hook(top=15.0, reach=4.6, mat="spine"):
    """柄の先が鉤に曲がる (先は x = reach)"""
    return curve(mat, [(0, top - 0.6, 0), (0, top + 2.6, 0), (reach * 0.3, top + 4.8, 0), (reach * 0.8, top + 5.0, 0), (reach, top + 3.6, 0)],
                 [0.95, 0.9, 0.8, 0.7, 0.55], [0.95, 0.9, 0.8, 0.7, 0.55], sides=6, caps=(False, True), phase=30)


# ================================================================== 一章 岩鯨
def wst_a(stage=0):
    """A 肋の揺り籠: 鯨骨の柄の先で、二本の肋が内へ反って水色の石を抱く。根に苔の房。"""
    f = shaft(16.0, upper="main")
    for s in (1, -1):
        f += curve("main", [(s * 0.6, 15.4, 0), (s * 2.8, 17.6, 0), (s * 3.0, 20.4, 0), (s * 1.2, 22.6, 0)], [1.0, 0.9, 0.7, 0.35], [1.0, 0.9, 0.7, 0.35], sides=4, caps=(False, True))
    f += ell("gem", (0, 19.4, 0), (1.6, 2.0, 1.6))
    f += block("guard", (0, 15.6, 0), (2.8, 1.4, 2.6), taper=(0.8, 0.8))
    f += ell("acc", (0.9, 14.4, -0.8), (1.2, 0.9, 0.7))
    return f


def wst_b(stage=0):
    """B 背骨の輪: 柄の先に鯨の背骨の円盤 (輪)。輪の真ん中に石が浮き、輪の縁に小さな突起が四つ。"""
    f = shaft(15.4)
    f += ring(19.6, 3.6, mat="main", n=10, w=0.9, t=1.1)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += spike(BONE, "edge", (3.9 * math.cos(a), 19.6 + 3.9 * math.sin(a), 0), (math.cos(a), math.sin(a), 0), 1.4, 0.6, sides=4, rings=1, hint=(0, 0, 1))
    f += ell("gem", (0, 19.6, 0), (1.3, 1.5, 1.3))
    f += block("guard", (0, 15.6, 0), (2.4, 1.2, 2.4), taper=(0.8, 0.8))
    return f


def wst_c(stage=0):
    """C 尾鰭: 柄の先が鯨の尾鰭 (左右に開く二枚)。鰭の付け根に石。苔の巻き。"""
    f = shaft(17.0)
    for s in (1, -1):
        f += extrude("main", [(0, 0), (4.6, 1.6), (6.0, 3.6), (3.4, 2.6), (0.6, 2.2)], 1.0, c=(s * 0.4 if s > 0 else -0.4, 17.4, 0),
                     rot=None if s > 0 else rot_y(180))
    f += ell("gem", (0, 18.6, -0.6), (1.2, 1.2, 0.9))
    f += prism("acc", (0, 13.6, 0), (0, 14.6, 0), 1.25, 1.2, sides=6, caps=(True, True))
    return f


def wbl_a(stage=0):
    """A 肋の鉤と石の鐘: 柄の先が肋の鉤に曲がり、苔むした石の鐘が一つ垂れる。舌は水色の石。"""
    f = shaft(15.0)
    f += hook(15.0, 4.6, mat="main")
    f += prism("cord", (4.6, 15.8, 0), (4.6, 18.8, 0), 0.35, 0.35, sides=4, caps=(False, False))
    f += bell(4.6, 11.0, 5.0, 2.7, 1.0, mat="guard")
    f += ell("gem", (4.6, 10.2, 0), (0.9, 0.9, 0.9))
    return f


def wbl_b(stage=0):
    """B 骨の神楽鈴: 柄の上に三段の小さな骨の鈴 (3・4・5)。柄頭に水色の石。"""
    f = shaft(13.0)
    f += prism("spine", (0, 12.6, 0), (0, 19.4, 0), 0.45, 0.4, sides=6, caps=(False, False))
    for (y, n, R) in ((18.2, 3, 1.5), (15.8, 4, 2.1), (13.4, 5, 2.7)):
        f += prism("trim", (0, y + 0.7, 0), (0, y + 1.0, 0), R * 0.55, R * 0.55, sides=8, caps=(True, True))
        for k in range(n):
            a = math.radians(360.0 * k / n + 90)
            f += prism("spine", (0, y + 0.85, 0), (R * math.cos(a) * 0.95, y + 0.6, R * math.sin(a) * 0.95), 0.2, 0.2, sides=4, caps=(False, False))
            f += ell("main", (R * math.cos(a), y, R * math.sin(a)), (0.95, 0.9, 0.95))
    f += ell("gem", (0, 20.2, 0), (0.95, 0.95, 0.95))
    return f


def wbl_c(stage=0):
    """C 立つ鐘: 柄の先に口を上へ向けた石の鐘 (杯)。縁を肋が四本で囲い、中に水色の石。"""
    f = shaft(14.0)
    rings = [circ(14.2, 1.0, 8), circ(15.6, 1.8, 8), circ(17.6, 3.0, 8), circ(19.2, 3.6, 8)]
    f += loft(BONE, "guard", rings, caps=("guard", "dark"))
    f += prism("trim", (0, 18.9, 0), (0, 19.6, 0), 3.8, 3.7, sides=8, caps=(True, False))
    f += ell("gem", (0, 19.4, 0), (2.0, 2.0, 2.0))
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += curve("main", [(math.cos(a) * 3.4, 18.6, math.sin(a) * 3.4), (math.cos(a) * 3.8, 21.0, math.sin(a) * 3.8), (math.cos(a) * 2.2, 22.6, math.sin(a) * 2.2)],
                   [0.6, 0.5, 0.3], [0.6, 0.5, 0.3], sides=4, caps=(False, True))
    return f


# ================================================================== 二章 潮殻
def tst_a(stage=0):
    """A 巻き貝: 柄の先に巻き貝 (口が前)。口の中に真珠の光。"""
    f = shaft(15.4)
    pts, ra = [], []
    for k in range(9):
        t = k / 8
        a = t * math.pi * 1.6
        pts.append((math.sin(a) * (2.6 - 2.0 * t), 16.0 + t * 7.0, -math.cos(a) * (2.6 - 2.0 * t) * 0.5))
        ra.append(2.4 - 2.0 * t + 0.2)
    f += curve("main", pts, ra, ra, sides=6, caps=(True, True), hint=(0, 0, 1))
    f += ell("gem", (0, 17.8, -1.6), (1.3, 1.3, 1.0))
    f += block("guard", (0, 15.6, 0), (2.6, 1.2, 2.6), taper=(0.8, 0.8))
    return f


def tst_b(stage=0):
    """B 珊瑚の枝: 柄の先が珊瑚の枝に分かれ、真ん中に真珠を抱く。"""
    f = shaft(15.6)
    for k, (dx, h) in enumerate(((-2.6, 5.6), (2.4, 6.2), (-0.8, 7.4), (1.4, 4.4))):
        f += curve("acc", [(0, 15.2, 0), (dx * 0.5, 15.2 + h * 0.5, (k % 2 - 0.5) * 1.2), (dx, 15.2 + h, (k % 2 - 0.5) * 1.6)],
                   [0.8, 0.6, 0.35], [0.8, 0.6, 0.35], sides=4, caps=(False, True))
    f += ell("gem", (0, 19.0, 0), (1.6, 1.6, 1.6))
    f += block("guard", (0, 15.4, 0), (2.4, 1.2, 2.4), taper=(0.8, 0.8))
    return f


def tst_c(stage=0):
    """C 殻の三日月: 柄の先に殻の板を重ねた三日月。抱く所に真珠。"""
    f = shaft(15.4)
    for k in range(6):
        a = math.radians(-150 + 50 * k)
        x, y = -3.2 * math.sin(a), 19.2 - 3.2 * math.cos(a)
        f += block("main", (x, y, 0), (2.2, 1.6, 1.0), rot=rot_z(-math.degrees(a)), taper=(0.8, 0.9))
    f += ell("gem", (0, 19.6, 0), (1.3, 1.3, 1.3))
    f += block("guard", (0, 15.6, 0), (2.4, 1.2, 2.4), taper=(0.8, 0.8))
    return f


def tbl_a(stage=0):
    """A 巻き貝の鐘: 鉤の先に巻き貝を逆さにした鐘。舌は真珠。昆布の帯が垂れる。"""
    f = shaft(15.0)
    f += hook(15.0, 4.6, mat="spine")
    f += bell(4.6, 11.0, 5.2, 2.6, 0.8, mat="main")
    f += curve("main", [(4.6, 16.2, 0), (5.4, 17.0, -0.4), (4.4, 17.6, -0.6)], [0.8, 0.6, 0.3], [0.8, 0.6, 0.3], sides=4, caps=(False, True))
    f += ell("gem", (4.6, 10.2, 0), (0.9, 0.9, 0.9))
    f += block("wrap", (1.1, 12.4, 0), (0.8, 3.6, 0.3), rot=rot_z(-6), taper=(0.6, 1.0))
    return f


def tbl_b(stage=0):
    """B 貝の風鈴: 柄の先の輪に、小さな貝が五つ紐で垂れる (振ると鳴る)。輪の頂きに真珠。"""
    f = shaft(14.8)
    f += ring(18.4, 3.2, mat="trim", n=10)
    f += prism("spine", (0, 14.6, 0), (0, 21.6, 0), 0.4, 0.4, sides=4, caps=(False, False))
    for k in range(5):
        x = -2.4 + k * 1.2
        f += prism("cord", (x, 18.4, 0), (x, 16.6 - (k % 2) * 0.8, 0), 0.15, 0.15, sides=4, caps=(False, False))
        f += block("main", (x, 15.9 - (k % 2) * 0.8, 0), (1.0, 1.4, 0.6), taper=(0.6, 0.8))
    f += ell("gem", (0, 22.2, 0), (0.9, 0.9, 0.9))
    return f


def tbl_c(stage=0):
    """C 二枚貝の鐘: 柄の先に、口を少し開いた二枚貝が上を向く。中の真珠が鈴の玉。珊瑚の飾り。"""
    f = shaft(14.6)
    for s in (1, -1):
        f += block("main", (s * 1.4, 18.2, 0), (2.6, 5.4, 2.4), rot=rot_z(s * 14), taper=(0.5, 0.9))
    f += ell("gem", (0, 18.0, 0), (1.4, 1.4, 1.4))
    f += block("guard", (0, 15.2, 0), (3.4, 1.4, 2.6), taper=(0.8, 0.8))
    f += curve("acc", [(1.4, 15.0, -1.0), (2.8, 15.8, -1.4), (3.4, 17.4, -1.4)], [0.5, 0.4, 0.2], [0.5, 0.4, 0.2], sides=4, caps=(False, True))
    return f


# ================================================================== 三章 雷翼
def sst_a(stage=0):
    """A 避雷の穂: 柄の先が細い銅の穂先、根に銅線の巻きと羽が二枚。穂の根に青白い石。"""
    f = shaft(15.0)
    f += prism("main", (0, 15.0, 0), (0, 21.6, 0), 0.7, 0.25, sides=4, caps=(False, True))
    for y in (15.4, 16.2, 17.0):
        f += prism("edge", (0, y, 0), (0, y + 0.45, 0), 1.25, 1.25, sides=6, caps=(True, True))
    f += ell("gem", (0, 18.4, 0), (1.1, 1.3, 1.1))
    for s in (1, -1):
        f += block("acc", (s * 1.8, 15.2, 0), (3.0, 1.0, 0.4), rot=rot_z(s * -30), taper=(0.4, 0.9))
    return f


def sst_b(stage=0):
    """B 羽の冠: 柄の先に羽が扇に開き、その要に青白い石。"""
    f = shaft(15.6)
    for k in range(5):
        a = math.radians(-60 + 30 * k)
        f += block("acc", (math.sin(a) * 2.6, 18.6 + math.cos(a) * 2.6, 0), (1.2, 5.0, 0.4), rot=rot_z(-math.degrees(a)), taper=(0.5, 0.9))
    f += ell("gem", (0, 16.6, -0.6), (1.4, 1.4, 1.0))
    f += block("guard", (0, 15.8, 0), (3.0, 1.2, 2.4), taper=(0.8, 0.8))
    return f


def sst_c(stage=0):
    """C 二股の音叉: 柄の先が銅の二股に割れ、間に青白い石が浮く (雷が渡る)。"""
    f = shaft(15.2)
    for s in (1, -1):
        f += curve("main", [(0, 15.0, 0), (s * 2.2, 16.2, 0), (s * 2.4, 19.0, 0), (s * 2.0, 22.0, 0)], [0.8, 0.7, 0.6, 0.35], [0.9, 0.8, 0.7, 0.4], sides=4, caps=(False, True))
    f += ell("gem", (0, 19.0, 0), (1.2, 1.6, 1.2))
    f += block("guard", (0, 15.2, 0), (2.4, 1.2, 2.4), taper=(0.8, 0.8))
    return f


def sbl_a(stage=0):
    """A 銅の鐘と羽の房: 鉤の先に銅の鐘、鐘の下に羽の房。"""
    f = shaft(15.0)
    f += hook(15.0, 4.6)
    f += bell(4.6, 11.0, 5.0, 2.5, 0.9, mat="main")
    for s in (-1, 0, 1):
        f += block("acc", (4.6 + s * 0.8, 9.2, 0), (0.7, 2.4, 0.3), rot=rot_z(s * 12), taper=(0.4, 1.0))
    f += ell("gem", (4.6, 10.4, 0), (0.8, 0.8, 0.8))
    return f


def sbl_b(stage=0):
    """B 三つ鐘: 柄の先の横木に小さな銅の鐘が三つ並んで垂れる。真ん中に青白い石。"""
    f = shaft(17.0)
    f += block("spine", (0, 17.2, 0), (9.0, 0.9, 0.9))
    for x in (-3.8, 0.0, 3.8):
        f += prism("cord", (x, 16.8, 0), (x, 15.4, 0), 0.2, 0.2, sides=4, caps=(False, False))
        f += bell(x, 12.6, 3.0, 1.5, 0.6, mat="main")
    f += ell("gem", (0, 18.6, 0), (1.0, 1.0, 1.0))
    return f


def sbl_c(stage=0):
    """C 籠の鐘: 柄の先の銅の輪の籠の中に鐘が一つ。籠の頂きに羽。"""
    f = shaft(14.6)
    f += ring(18.6, 3.4, mat="main", n=10, sy=1.15)
    f += bell(0, 16.8, 3.6, 1.8, 0.7, mat="main")
    f += prism("cord", (0, 20.4, 0), (0, 22.4, 0), 0.2, 0.2, sides=4, caps=(False, False))
    f += ell("gem", (0, 16.2, 0), (0.7, 0.7, 0.7))
    f += block("acc", (0, 23.8, 0), (1.0, 3.0, 0.4), taper=(0.4, 1.0))
    return f


# ================================================================== 四章 余燼
def est_a(stage=0):
    """A 火の籠: 鉄の柄の先に鉄の籠、中で熾火 (橙の光) が燃える。"""
    f = shaft(15.4, upper="spine")
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += curve("edge", [(math.cos(a) * 0.8, 15.2, math.sin(a) * 0.8), (math.cos(a) * 2.4, 17.4, math.sin(a) * 2.4), (math.cos(a) * 2.2, 20.4, math.sin(a) * 2.2),
                            (0, 22.2, 0)], [0.4, 0.4, 0.35, 0.3], [0.4, 0.4, 0.35, 0.3], sides=4, caps=(False, True))
    f += ell("gem", (0, 18.4, 0), (1.6, 2.0, 1.6))
    f += block("guard", (0, 15.4, 0), (2.8, 1.2, 2.8), taper=(0.8, 0.8))
    return f


def est_b(stage=0):
    """B 割れた玄武岩: 柄の先に尖った玄武岩の塊。割れ目が光る (余燼の割れ目)。"""
    f = shaft(15.2)
    f += spike(BONE, "main", (0, 15.0, 0), (0, 1, 0), 7.4, 2.4, sides=5, rings=2, hint=(0, 0, 1))
    f += spike(BONE, "main", (1.2, 16.6, 0), (0.6, 1, 0), 3.6, 1.0, sides=4, rings=1, hint=(0, 0, 1))
    f += ell("gem", (0, 17.4, -1.6), (0.9, 1.2, 0.6))
    f += block("guard", (0, 15.0, 0), (3.0, 1.2, 2.8), taper=(0.8, 0.8))
    return f


def est_c(stage=0):
    """C 火箸: 柄の先が鍛冶の火箸の二本の腕になり、熾火の塊を挟む。"""
    f = shaft(15.2)
    for s in (1, -1):
        f += curve("edge", [(s * 0.5, 15.0, 0), (s * 1.8, 17.4, 0), (s * 1.6, 20.0, 0), (s * 0.9, 21.8, 0)], [0.6, 0.55, 0.5, 0.45], [0.7, 0.6, 0.55, 0.5], sides=4, caps=(False, True))
    f += ell("gem", (0, 20.0, 0), (1.2, 1.4, 1.2))
    f += block("guard", (0, 15.2, 0), (2.4, 1.2, 2.4), taper=(0.8, 0.8))
    return f


def censer(cx, cy, r=2.2, h=3.6, mat="acc"):
    """香炉: 丸い壺に蓋と煙の穴 (暗い)、中に橙の光が透ける"""
    rings = [circ(cy - h * 0.5, r * 0.6, 8, cx), circ(cy - h * 0.2, r, 8, cx), circ(cy + h * 0.25, r * 0.95, 8, cx), circ(cy + h * 0.5, r * 0.4, 8, cx)]
    f = loft(BONE, mat, rings, caps=(mat, mat))
    for k in range(4):
        a = math.radians(90 * k)
        f += block("gem", (cx + math.cos(a) * r * 0.95, cy, math.sin(a) * r * 0.95), (0.6, 0.9, 0.6))
    f += prism("trim", (cx, cy - 0.1, 0), (cx, cy + 0.4, 0), r + 0.2, r + 0.15, sides=8, caps=(True, True))
    return f


def ebl_a(stage=0):
    """A 吊り香炉: 鉤の先から鎖で香炉が垂れる。煙の穴から橙の光。"""
    f = shaft(15.0)
    f += hook(15.0, 4.6)
    f += prism("cord", (4.6, 14.2, 0), (4.6, 18.8, 0), 0.3, 0.3, sides=4, caps=(False, False))
    f += censer(4.6, 12.2)
    return f


def ebl_b(stage=0):
    """B 火鉢: 柄の先に浅い鉄の火鉢。熾火の山が光る。縁に鈴が二つ。"""
    f = shaft(14.6)
    rings = [circ(14.8, 1.0, 8), circ(16.0, 2.6, 8), circ(17.2, 3.4, 8)]
    f += loft(BONE, "edge", rings, caps=("edge", "dark"))
    f += ell("gem", (0, 17.2, 0), (2.6, 1.2, 2.6))
    for s in (1, -1):
        f += bell(s * 3.6, 14.8, 1.8, 0.9, 0.4, mat="acc")
    return f


def ebl_c(stage=0):
    """C 鐘の香炉: 柄の先に鐘の形の香炉が座り、腹の窓から熾火が覗く。上に小さな角。"""
    f = shaft(14.6)
    f += censer(0, 18.0, r=2.6, h=4.6, mat="guard")
    f += spike(BONE, "edge", (0, 20.2, 0), (0, 1, 0), 2.4, 0.7, sides=4, rings=1, hint=(0, 0, 1))
    return f


# ================================================================== 一覧
def _w(jp, glow, opts, scale=9.5):
    return dict(jp=jp, tier="rare", glow=glow, pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 9.0, 0.0), scale=scale, opts=opts)


WEAPONS = {
    "whale_staff": _w("鯨骨の星杖", "whale", dict(
        a=(wst_a, inlay, (), "肋の揺り籠。二本の肋が水色の石を抱く。"),
        b=(wst_b, inlay, (), "背骨の輪。鯨の背骨の円盤の真ん中に石が浮く。"),
        c=(wst_c, inlay, (), "尾鰭。柄の先が鯨の尾鰭に開く。"))),
    "whale_bell_staff": _w("鯨歌いの鈴杖", "whale", dict(
        a=(wbl_a, inlay, (), "肋の鉤と石の鐘。苔むした石の鐘が一つ垂れる。"),
        b=(wbl_b, inlay, (), "骨の神楽鈴。三段の骨の小鈴。"),
        c=(wbl_c, inlay, (), "立つ鐘。口を上へ向けた石の鐘を肋が囲う。"))),
    "tide_staff": _w("潮殻の真珠杖", "tide", dict(
        a=(tst_a, inlay, (), "巻き貝。口の中に真珠の光。"),
        b=(tst_b, inlay, (), "珊瑚の枝。枝の真ん中に真珠。"),
        c=(tst_c, inlay, (), "殻の三日月。殻の板を重ねた三日月が真珠を抱く。"))),
    "tide_bell_staff": _w("潮鳴りの鈴杖", "tide", dict(
        a=(tbl_a, inlay, (), "巻き貝の鐘。鉤の先に逆さの巻き貝の鐘、昆布の帯。"),
        b=(tbl_b, inlay, (), "貝の風鈴。輪に小さな貝が五つ垂れる。"),
        c=(tbl_c, inlay, (), "二枚貝の鐘。開いた二枚貝の中の真珠が鈴の玉。"))),
    "storm_staff": _w("雷羽の杖", "thunder", dict(
        a=(sst_a, inlay, (), "避雷の穂。細い銅の穂先と銅線の巻き、羽が二枚。"),
        b=(sst_b, inlay, (), "羽の冠。羽が扇に開き、その要に石。"),
        c=(sst_c, inlay, (), "二股の音叉。銅の二股の間に石が浮く。"))),
    "storm_bell_staff": _w("雷鳴りの鈴杖", "thunder", dict(
        a=(sbl_a, inlay, (), "銅の鐘と羽の房。鉤から銅の鐘、下に羽の房。"),
        b=(sbl_b, inlay, (), "三つ鐘。横木に小さな銅の鐘が三つ。"),
        c=(sbl_c, inlay, (), "籠の鐘。銅の輪の籠の中に鐘、頂きに羽。"))),
    "ember_staff": _w("熾火の杖", "ember", dict(
        a=(est_a, inlay, (), "火の籠。鉄の籠の中で熾火が燃える。"),
        b=(est_b, inlay, (), "割れた玄武岩。尖った玄武岩の割れ目が光る。"),
        c=(est_c, inlay, (), "火箸。鍛冶の火箸が熾火の塊を挟む。"))),
    "ember_censer": _w("灰の香炉", "ember", dict(
        a=(ebl_a, inlay, (), "吊り香炉。鉤から鎖で香炉が垂れる。"),
        b=(ebl_b, inlay, (), "火鉢。浅い鉄の火鉢に熾火、縁に鈴が二つ。"),
        c=(ebl_c, inlay, (), "鐘の香炉。鐘の形の香炉の窓から熾火が覗く。"))),
}
