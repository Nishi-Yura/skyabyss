"""島の祠 (宿り木の祠) で職を選ぶと受け取る始めの武器 (docs/design/43 の 4 節)。今ある品で足りない 3 品 x 3 案の形。
魔法使いの杖・呪術師の鈴の杖・暗殺者の短剣。どれも祠の品なので、流木の柄 (grip)、麻縄 (cord)、祠の布 (wrap)、
真鍮の縁 (trim)、宿り木の葉 (leaf) と実 (gem。職ごとの光) で揃える。刃と金具は素朴な鉄 (main / edge / spine / guard)、
鈴は青銅 (acc)、紙垂は白い紙 (paper)、鯨の骨は骨 (bone)。階級はふつう (common)。
座標は他の武器と同じ (1 単位 = 1/16 ブロック、+Y が先、x = 0・z = 0 が握り、広い面は ±Z、正面 = -Z)。
部品は親へ沈めて継ぐ (継ぎ目で中が見えない)。入れるときに tools/lib/mesh_close.py が穴を閉じる。

  WEAPONS[id] = dict(jp, tier, glow, pose, grip, cen, scale, opts={"a": (形の関数, inlay, edge_mats, 一言), ...})
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE.parent.parent))
from wkit import *  # noqa: F401,F403,E402
from wkit import block, blade, prism, curve, extrude, V, rot_x, rot_y, rot_z  # noqa: E402

from lpkit import ellipsoid, spike, loft  # noqa: E402


def off(env):
    return env["lit"] < -9


def circ(y, r, n=8, cx=0.0, cz=0.0, ph=0.0):
    return [V(cx + r * math.cos(math.radians(ph + 360.0 * k / n)), y, cz + r * math.sin(math.radians(ph + 360.0 * k / n))) for k in range(n)]


def ell(mat, c, r, segs=6, rings=2, rot=None):
    return ellipsoid(BONE, mat, c, r, segs=segs, rings=rings, rot=rot)


def berry(c, r=0.75):
    """宿り木の実 (光る)"""
    return ell("gem", c, (r, r, r), segs=6, rings=2)


def sprig(c, s=1, size=1.0):
    """宿り木の小枝: 二股の葉 2 対と実 1 つ。c は付け根。s = ±1 で左右"""
    x, y, z = c
    f = []
    for k, (dx, dy, a) in enumerate(((1.4, 0.9, 35), (1.0, 2.0, 70))):
        f += block("leaf", (x + s * dx * size, y + dy * size, z - 0.2), (2.2 * size, 0.9 * size, 0.6), rot=rot_z(s * a), taper=(0.5, 0.8))
    f += berry((x + s * 0.5 * size, y + 1.3 * size, z - 0.7), 0.6 * size)
    return f


def rope(y, r, h=0.7):
    return prism("cord", (0, y, 0), (0, y + h, 0), r, r, sides=6, caps=(True, True))


def shide(c, n=3, s=1):
    """紙垂 (ジグザグの白い紙)。c から下へ"""
    x, y, z = c
    f = []
    for i in range(n):
        f += block("paper", (x + s * (0.5 if i % 2 else -0.1), y - 0.9 - i * 1.5, z), (1.3, 1.4, 0.3), rot=rot_z(s * (12 if i % 2 else -12)))
    return f


# ================================================================== 宿り木の杖 (魔法使い。星読みの青白い光)
def wood_shaft(top=16.0, bend=0.0):
    """流木の柄: 少し曲がった 6 角の棒、握りに布、石突きに鉄の輪"""
    f = []
    pts = [(0, -4.0, 0), (bend * 0.3, 2.0, 0), (bend * 0.8, 9.0, 0), (bend, top, 0)]
    f += curve("grip", pts, [1.05, 1.0, 0.95, 0.9], [1.05, 1.0, 0.95, 0.9], sides=6, caps=(True, False), phase=30)
    f += prism("wrap", (0, -0.4, 0), (0, 3.4, 0), 1.2, 1.2, sides=6, caps=(False, False))
    f += prism("spine", (0, -4.4, 0), (0, -3.2, 0), 1.25, 1.15, sides=6, caps=(True, False))
    return f


def sf_a(stage=0):
    """A 枝の籠: 曲がった流木の先が三つの枝に割れ、星の石 (光) を抱く。割れ目の下に宿り木の房と縄。"""
    f = wood_shaft(top=16.0, bend=0.8)
    for k in range(3):
        a = math.radians(90 + 120 * k)
        dx, dz = math.cos(a), math.sin(a)
        f += curve("grip", [(0.8, 15.4, 0), (0.8 + dx * 1.6, 17.4, dz * 1.6), (0.8 + dx * 1.9, 19.6, dz * 1.9), (0.8 + dx * 0.9, 21.6, dz * 0.9)],
                   [0.7, 0.6, 0.5, 0.3], [0.7, 0.6, 0.5, 0.3], sides=4, caps=(False, True))
    f += ell("gem", (0.8, 19.2, 0), (1.5, 1.9, 1.5), segs=6, rings=2)
    f += prism("cord", (0.75, 13.6, 0), (0.75, 14.6, 0), 1.25, 1.2, sides=6, caps=(True, True))
    f += sprig((1.6, 13.4, -0.6), s=1)
    f += sprig((-0.2, 13.6, -0.6), s=-1, size=0.85)
    return f


def sf_b(stage=0):
    """B 星の輪: まっすぐな柄の先に真鍮の輪、輪の四方に小さな尖り (星図の羅針)。輪の真ん中に星の石が浮く。布の吹き流し。"""
    f = wood_shaft(top=15.0)
    f += block("trim", (0, 15.0, 0), (2.6, 0.8, 2.6), taper=(0.8, 0.8))
    cy, R = 19.2, 3.6
    n = 10
    for k in range(n):
        a0 = math.radians(360.0 * k / n)
        a1 = math.radians(360.0 * (k + 1) / n)
        p0 = (R * math.cos(a0), cy + R * math.sin(a0), 0)
        p1 = (R * math.cos(a1), cy + R * math.sin(a1), 0)
        f += curve("trim", [p0, p1], [0.55, 0.55], [0.7, 0.7], sides=4, caps=(True, True))
    for k in range(4):
        a = math.radians(90 * k)
        f += spike(BONE, "trim", (R * math.cos(a) * 1.05, cy + R * math.sin(a) * 1.05, 0), (math.cos(a), math.sin(a), 0), 1.6, 0.55, sides=4, rings=1, hint=(0, 0, 1))
    f += block("trim", (0, 15.4, 0), (0.9, 1.4, 0.9))
    f += ell("gem", (0, cy, 0), (1.3, 1.6, 1.3), segs=6, rings=2)
    # 吹き流し (輪の根から垂れる布)
    f += block("wrap", (0.95, 13.2, -0.2), (0.9, 3.6, 0.3), rot=rot_z(-6), taper=(0.6, 1.0))
    f += block("wrap", (1.3, 10.9, -0.2), (0.7, 2.0, 0.3), rot=rot_z(-14), taper=(0.4, 1.0))
    return f


def sf_c(stage=0):
    """C 鯨骨の三日月: 柄の先に鯨の骨を削った三日月。三日月の抱く所に星の石。柄に縄の巻きと宿り木の小枝。"""
    f = wood_shaft(top=15.4)
    f += prism("bone", (0, 14.8, 0), (0, 16.2, 0), 1.3, 1.0, sides=6, caps=(False, False))
    pts, wa, wb = [], [], []
    for k in range(7):
        a = math.radians(-150 + 50 * k)
        pts.append((3.2 * math.sin(a) * -1, 19.0 - 3.2 * math.cos(a), 0))
        w = 1.0 - abs(k - 3) / 3 * 0.7
        wa.append(w)
        wb.append(0.8 * w)
    f += curve("bone", pts, wa, wb, sides=4, caps=(True, True))
    f += ell("gem", (0, 19.4, 0), (1.3, 1.3, 1.3), segs=6, rings=2)
    f += rope(12.0, 1.2)
    f += rope(13.0, 1.15)
    f += sprig((0.9, 12.2, -0.7), s=1, size=0.9)
    return f


# ================================================================== 祠の鈴杖 (呪術師。潮の青緑の光)
def small_bell(c, r=0.9, h=1.3):
    """丸い小鈴 (神楽鈴の鈴): 青銅の玉に暗い口の筋"""
    x, y, z = c
    f = ell("acc", (x, y, z), (r, r * 0.95, r), segs=6, rings=2)
    f += block("dark", (x, y - h * 0.18, z - r * 0.82), (r * 1.2, 0.25, 0.3))
    return f


def bl_a(stage=0):
    """A 神楽鈴: 短い柄の先に三段の小鈴の輪 (上 3・中 4・下 5)。柄頭に宿り木の実、根に五色ならぬ二色の布の垂れ。"""
    f = wood_shaft(top=12.0)
    f += prism("trim", (0, 11.6, 0), (0, 12.4, 0), 1.6, 1.4, sides=6, caps=(True, True))
    f += prism("spine", (0, 12.2, 0), (0, 18.8, 0), 0.45, 0.4, sides=6, caps=(False, False))
    for (y, n, R) in ((17.8, 3, 1.5), (15.4, 4, 2.1), (13.0, 5, 2.7)):
        f += prism("trim", (0, y + 0.7, 0), (0, y + 1.0, 0), R * 0.55, R * 0.55, sides=8, caps=(True, True))
        for k in range(n):
            a = math.radians(360.0 * k / n + 90)
            f += prism("spine", (0, y + 0.85, 0), (R * math.cos(a) * 0.95, y + 0.6, R * math.sin(a) * 0.95), 0.2, 0.2, sides=4, caps=(False, False))
            f += small_bell((R * math.cos(a), y, R * math.sin(a)), r=0.95)
    f += berry((0, 19.5, 0), 0.9)
    # 布の垂れ (柄の根から左右に)
    for s in (1, -1):
        f += block("wrap", (s * 1.05, 9.8, 0.0), (0.8, 3.8, 0.3), rot=rot_z(s * 6), taper=(0.7, 1.0))
    return f


def bl_b(stage=0):
    """B 鉤の一鈴: 流木の先が鉤に曲がり、大きめの鈴が一つ縄で吊られる。鉤に紙垂。鈴の舌は潮の光る石。"""
    f = wood_shaft(top=15.0)
    f += curve("grip", [(0, 14.4, 0), (0, 17.4, 0), (1.4, 19.6, 0), (3.6, 19.8, 0), (4.6, 18.4, 0)],
               [0.95, 0.9, 0.8, 0.7, 0.55], [0.95, 0.9, 0.8, 0.7, 0.55], sides=6, caps=(False, True), phase=30)
    f += prism("cord", (4.6, 15.8, 0), (4.6, 18.6, 0), 0.35, 0.35, sides=4, caps=(False, False))
    rings = [circ(11.0, 2.6, 8, 4.6), circ(11.6, 2.4, 8, 4.6), circ(13.6, 1.9, 8, 4.6), circ(15.4, 1.3, 8, 4.6), circ(16.0, 0.8, 8, 4.6)]
    f += loft(BONE, "acc", rings, caps=("dark", "acc"))
    f += prism("trim", (4.6, 10.8, 0), (4.6, 11.4, 0), 2.9, 2.75, sides=8, caps=(True, True))
    f += ell("gem", (4.6, 10.2, 0), (0.9, 0.9, 0.9), segs=6, rings=2)
    f += shide((1.0, 19.0, -0.9), n=3, s=-1)
    f += rope(13.4, 1.2)
    return f


def bl_c(stage=0):
    """C 錫杖の輪: 柄の先に真鍮の大きな輪、輪に小さな鉄の環が四つ通る (振ると鳴る)。輪の頂きに宿り木の実と葉。"""
    f = wood_shaft(top=14.6)
    f += block("trim", (0, 14.6, 0), (2.4, 1.0, 2.4), taper=(0.8, 0.8))
    cy, R = 18.6, 3.4
    n = 10
    for k in range(n):
        a0 = math.radians(-90 + 360.0 * k / n)
        a1 = math.radians(-90 + 360.0 * (k + 1) / n)
        f += curve("trim", [(R * math.cos(a0), cy + R * 1.15 * math.sin(a0), 0), (R * math.cos(a1), cy + R * 1.15 * math.sin(a1), 0)],
                   [0.55, 0.55], [0.7, 0.7], sides=4, caps=(True, True))
    f += prism("trim", (0, 14.8, 0), (0, 21.6, 0), 0.45, 0.45, sides=4, caps=(False, False))
    for s in (1, -1):
        for yy in (17.2, 20.0):
            dx = R * math.sqrt(max(0.0, 1 - ((yy - cy) / (R * 1.15)) ** 2))
            f += prism("acc", (s * dx, yy, -0.9), (s * dx, yy, 0.9), 1.0, 1.0, sides=6, caps=(True, True), hint=(0, 1, 0))
    f += berry((0, 23.0, 0), 0.8)
    f += sprig((0.3, 22.4, -0.4), s=1, size=0.8)
    f += sprig((-0.3, 22.4, -0.4), s=-1, size=0.8)
    return f


# ================================================================== 宿り木の短剣 (暗殺者。風の淡い緑の光)
def dagger_hilt(wood=True):
    f = []
    f += prism("grip" if wood else "bone", (0, -1.4, 0), (0, 2.6, 0), 0.85, 0.78, sides=6, caps=(False, False))
    for y in (-0.6, 0.4, 1.4):
        f += prism("cord", (0, y, 0), (0, y + 0.55, 0), 0.98, 0.98, sides=6, caps=(True, True))
    return f


def dg_a(stage=0):
    """A 枝打ちの小刀: 鍔のない片刃のやや反った刃 (山の小刀)。柄は流木に縄、柄頭に宿り木の実。"""
    f = dagger_hilt()
    f += prism("spine", (0, 2.5, 0), (0, 3.3, 0), 1.05, 1.0, sides=6, caps=(True, True))
    cx = lambda y: -0.03 * (y - 3.2) ** 2
    ys = [3.2, 5.6, 8.0, 10.2, 11.8]
    lo = [-1.5, -1.6, -1.45, -1.0, 0.0]
    hi = [0.9, 0.85, 0.7, 0.35, 0.0]
    th = [0.6, 0.6, 0.5, 0.35, 0.0]
    f += blade("main", [(y, a, b, t, t, cx(y)) for y, a, b, t in zip(ys, lo, hi, th)])
    f += berry((0, -2.2, 0), 0.85)
    f += block("leaf", (0.9, -1.9, -0.4), (1.6, 0.7, 0.4), rot=rot_z(-35), taper=(0.5, 0.8))
    return f


def dg_b(stage=0):
    """B 葉の刃: 宿り木の葉の形の両刃 (根が細く、腹がふくらみ、先が尖る)。小さな真鍮の鍔、縄の柄、柄頭は鉄の輪。"""
    f = dagger_hilt()
    st = [(3.0, -0.7, 0.7, 0.55, 0.55), (5.4, -1.7, 1.7, 0.6, 0.6), (8.2, -1.6, 1.6, 0.55, 0.55), (10.6, -0.8, 0.8, 0.4, 0.4), (12.4, 0, 0, 0, 0)]
    f += blade("main", st)
    f += block("trim", (0, 2.8, 0), (3.6, 0.8, 1.6), taper=(0.8, 0.9))
    f += prism("spine", (0, -2.2, -0.45), (0, -2.2, 0.45), 1.1, 1.1, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += ell("gem", (0, 2.8, -0.85), (0.5, 0.5, 0.35), segs=6, rings=1)
    return f


def dg_c(stage=0):
    """C 鉤の牙: 内へ巻く鉤の刃 (牙・爪の仲間)。柄頭に指を通す鉄の輪、刃の根に縄と小さな紙垂。"""
    f = dagger_hilt()
    pts = [(0, 2.6, 0), (0.6, 5.2, 0), (0.4, 8.0, 0), (-1.0, 10.2, 0), (-3.0, 11.0, 0)]
    f += curve("main", pts, [0.55, 0.55, 0.45, 0.3, 0.1], [1.5, 1.6, 1.4, 0.9, 0.15], sides=4, caps=(False, True), hint=(0, 0, 1))
    f += block("spine", (0, 2.6, 0), (2.2, 1.0, 1.4), taper=(0.8, 0.9))
    # 指の輪
    n = 8
    for k in range(n):
        a0 = math.radians(360.0 * k / n)
        a1 = math.radians(360.0 * (k + 1) / n)
        f += curve("spine", [(1.3 * math.cos(a0), -3.0 + 1.3 * math.sin(a0), 0), (1.3 * math.cos(a1), -3.0 + 1.3 * math.sin(a1), 0)],
                   [0.4, 0.4], [0.45, 0.45], sides=4, caps=(True, True))
    f += shide((0.9, 2.4, -0.6), n=2, s=1)
    return f


# ================================================================== 一覧
WEAPONS = {
    "shrine_staff": dict(
        jp="宿り木の杖", tier="common", glow="star", pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 8.0, 0.0), scale=9.5,
        opts=dict(
            a=(sf_a, None, (), "枝の籠。曲がった流木の先が三つの枝に割れて星の石を抱く。宿り木の房と縄。いちばん「魔法使いの杖」らしい。"),
            b=(sf_b, None, (), "星の輪。まっすぐな柄の先に真鍮の輪と四方の尖り、真ん中に星の石が浮く。星読みの道具らしい。"),
            c=(sf_c, None, (), "鯨骨の三日月。柄の先に鯨の骨の三日月、その抱く所に星の石。島の品らしい。"))),
    "shrine_bell_staff": dict(
        jp="祠の鈴杖", tier="common", glow="tide", pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 8.0, 0.0), scale=9.5,
        opts=dict(
            a=(bl_a, None, (), "神楽鈴。三段の小鈴の輪 (3・4・5)。柄頭に宿り木の実、根に布の垂れ。祠の祭具そのもの。"),
            b=(bl_b, None, (), "鉤の一鈴。流木の鉤から鈴が一つ縄で吊られ、舌が潮の光。鉤に紙垂。"),
            c=(bl_c, None, (), "錫杖の輪。真鍮の大きな輪に鉄の環が四つ。頂きに宿り木。振ると鳴る旅の杖。"))),
    "shrine_dagger": dict(
        jp="宿り木の短剣", tier="common", glow="wind", pose="whale", grip=(0.0, 0.6, 0.0), cen=(0.0, 4.5, 0.0), scale=17.0,
        opts=dict(
            a=(dg_a, None, (), "枝打ちの小刀。鍔のない片刃のやや反った刃。柄頭に宿り木の実。素朴でいちばん小さい。"),
            b=(dg_b, None, (), "葉の刃。宿り木の葉の形の両刃と小さな真鍮の鍔。「短剣」と一目で読める。"),
            c=(dg_c, None, (), "鉤の牙。内へ巻く鉤の刃と指の輪。牙・爪の仲間らしい暗殺者の得物。"))),
}


# ================================================================== 戦士・鍛冶師・弓使いの始めの武器 (2026-10-26 の声「全職に専用の武器」)
import d_thunder  # noqa: E402

CY = d_thunder.CY
TIP_PULL = d_thunder.TIP_PULL


def sw_hilt(pommel=True):
    f = []
    f += prism("grip", (0, -2.0, 0), (0, 0.4, 0), 0.95, 0.85, sides=6, caps=(False, False))
    for y in (-1.6, -0.6, 0.4, 1.4):
        f += prism("cord", (0, y, 0), (0, y + 0.55, 0), 1.05, 1.05, sides=6, caps=(True, True))
    f += prism("grip", (0, 0.4, 0), (0, 3.0, 0), 0.9, 0.85, sides=6, caps=(False, False))
    if pommel:
        f += berry((0, -2.8, 0), 0.9)
    return f


def ssw_a(stage=0):
    """A 祠の直剣: まっすぐな両刃と真鍮の十字鍔。柄頭に宿り木の実、鍔に小枝。"""
    f = sw_hilt()
    st = [(3.4, -1.6, 1.6, 0.7, 0.7), (9.0, -1.6, 1.6, 0.7, 0.7), (15.0, -1.4, 1.4, 0.6, 0.6), (17.6, -0.8, 0.8, 0.45, 0.45), (19.4, 0, 0, 0, 0)]
    f += blade("main", st)
    f += block("trim", (0, 3.2, 0), (7.0, 1.0, 1.8), taper=(0.85, 0.9))
    f += sprig((1.6, 3.6, -0.8), s=1, size=0.8)
    return f


def ssw_b(stage=0):
    """B 山刀: 背が厚く、先が広がる片刃 (藪を払う刃)。鍔は鉄の小さな板、柄は縄巻き。"""
    f = sw_hilt(pommel=False)
    st = [(3.2, -1.2, 1.4, 0.8, 0.8), (8.0, -1.4, 1.9, 0.8, 0.8), (13.0, -1.4, 2.6, 0.75, 0.75), (16.0, -1.4, 2.4, 0.6, 0.6), (17.4, -0.4, 0.6, 0.3, 0.3)]
    f += blade("main", st)
    f += block("spine", (-1.2, 9.5, 0), (0.6, 12.0, 1.0))
    f += block("guard", (0, 3.0, 0), (3.6, 0.9, 2.2))
    f += prism("spine", (0, -2.9, 0), (0, -1.9, 0), 1.2, 1.1, sides=6, caps=(True, False))
    return f


def ssw_c(stage=0):
    """C 葉の長剣: 宿り木の葉の形の長い刃。鍔は鯨骨の二本の牙、柄頭に実。"""
    f = sw_hilt()
    st = [(3.4, -0.8, 0.8, 0.6, 0.6), (7.0, -1.9, 1.9, 0.7, 0.7), (12.0, -2.1, 2.1, 0.7, 0.7), (17.0, -1.2, 1.2, 0.5, 0.5), (20.2, 0, 0, 0, 0)]
    f += blade("main", st)
    for s_ in (1, -1):
        f += curve("bone", [(s_ * 0.6, 3.0, 0), (s_ * 2.6, 3.2, 0), (s_ * 3.6, 4.4, 0)], [0.6, 0.5, 0.2], [0.7, 0.6, 0.25], sides=4, caps=(False, True))
    f += block("leaf", (0, 5.4, -0.4), (0.8, 2.2, 0.3), taper=(0.4, 0.9))
    return f


def hm_haft(top=13.0):
    f = []
    f += prism("grip", (0, -2.6, 0), (0, top, 0), 0.95, 0.9, sides=6, caps=(True, True))
    f += prism("wrap", (0, -1.8, 0), (0, 2.6, 0), 1.1, 1.1, sides=6, caps=(False, False))
    f += prism("spine", (0, -3.2, 0), (0, -2.4, 0), 1.2, 1.1, sides=6, caps=(True, False))
    return f


def shm_a(stage=0):
    """A 鍛冶の片手鎚: 鉄の四角い頭 (片側は平ら、片側はすぼむ)。流木の柄に布。"""
    f = hm_haft(13.2)
    f += block("spine", (1.6, 13.4, 0), (4.4, 3.0, 2.8), taper=(1.0, 1.0))
    f += block("spine", (-2.4, 13.4, 0), (3.2, 2.2, 2.2), taper=(0.6, 0.7), rot=rot_z(90))
    f += block("trim", (0, 13.4, 0), (1.6, 3.4, 3.2))
    return f


def shm_b(stage=0):
    """B 木槌: 太い丸太の頭の両口に鉄の輪。祠の杭を打つ槌。"""
    f = hm_haft(13.0)
    f += prism("grip", (-3.6, 13.4, 0), (3.6, 13.4, 0), 2.0, 2.0, sides=8, caps=(True, True), hint=(0, 1, 0))
    for x in (-3.0, 3.0):
        f += prism("spine", (x - 0.4, 13.4, 0), (x + 0.4, 13.4, 0), 2.15, 2.15, sides=8, caps=(True, True), hint=(0, 1, 0))
    f += rope(11.0, 1.15)
    return f


def shm_c(stage=0):
    """C 鎚と鉤: 片側が鎚、反対が鳶の嘴のように反った鉤 (山の道具)。鉤の根に紙垂。"""
    f = hm_haft(13.4)
    f += block("spine", (1.4, 13.6, 0), (3.2, 2.4, 2.4))
    f += curve("edge", [(-0.6, 13.6, 0), (-2.8, 13.8, 0), (-4.6, 12.8, 0), (-5.4, 11.0, 0)], [1.0, 0.8, 0.5, 0.15], [1.0, 0.8, 0.5, 0.15], sides=4, caps=(False, True))
    f += block("trim", (0, 13.6, 0), (1.4, 2.8, 2.8))
    f += shide((0.8, 12.0, -1.2), n=2, s=1)
    return f


def sbow_grip():
    f = []
    f += prism("grip", (5.0, CY - 2.6, 0), (5.0, CY + 2.6, 0), 1.0, 1.0, sides=6, caps=(False, False), hint=(0, 0, 1))
    for y in (-1.6, -0.5, 0.6, 1.7):
        f += prism("cord", (5.0, CY + y - 0.25, 0), (5.0, CY + y + 0.25, 0), 1.15, 1.15, sides=6, caps=(True, True), hint=(0, 0, 1))
    return f


def bow_limbs(up, ra, rb, mat="grip"):
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    f = []
    for path in (up, lo):
        f += curve(mat, path, ra, rb, sides=4, caps=(False, True), hint=(0, 0, 1))
    return f, lo


def sbw_a(stage=0):
    """A 流木の弓: 一本の流木を削った素朴な弓。腕の中ほどに縄の巻き、先に宿り木の実。"""
    f = sbow_grip()
    p = TIP_PULL[stage]
    up = [(5.0, CY + 2.4, 0), (4.4, CY + 5.0, 0), (2.8, CY + 7.6, 0), (0.6 - p * 0.6, CY + 9.4, 0), (-1.6 - p, CY + 10.2, 0)]
    g, lo = bow_limbs(up, [1.0, 0.95, 0.8, 0.6, 0.4], [1.1, 1.0, 0.85, 0.65, 0.45])
    f += g
    for path in (up, lo):
        x, y, _ = path[2]
        f += prism("cord", (x, y - 0.4, 0), (x, y + 0.4, 0), 1.0, 1.0, sides=6, caps=(True, True), hint=(0, 0, 1))
    f += berry((up[-1][0], up[-1][1] + 0.4, 0), 0.6)
    f += berry((lo[-1][0], lo[-1][1] - 0.4, 0), 0.6)
    f += d_thunder.string_and_arrow(stage, "common", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))
    return f


def sbw_b(stage=0):
    """B 鯨骨の小弓: 短めの腕を鯨骨で作り、先が外へ反る (反り弓)。握りに藍の布。"""
    f = sbow_grip()
    f += prism("wrap", (5.0, CY - 1.0, 0), (5.0, CY + 1.0, 0), 1.25, 1.25, sides=6, caps=(True, True), hint=(0, 0, 1))
    p = TIP_PULL[stage]
    up = [(5.0, CY + 2.4, 0), (3.4, CY + 5.2, 0), (2.4 - p * 0.4, CY + 7.6, 0), (2.8 - p * 0.8, CY + 9.2, 0), (1.6 - p, CY + 10.0, 0)]
    g, lo = bow_limbs(up, [1.0, 0.9, 0.75, 0.6, 0.45], [1.15, 1.0, 0.85, 0.7, 0.5], mat="bone")
    f += g
    f += d_thunder.string_and_arrow(stage, "common", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))
    return f


def sbw_c(stage=0):
    """C 枝の弓: 先が二股の小枝に分かれ、葉が付く。宿り木の枝そのままの弓。"""
    f = sbow_grip()
    p = TIP_PULL[stage]
    up = [(5.0, CY + 2.4, 0), (4.0, CY + 5.4, 0), (2.0, CY + 8.0, 0), (-0.4 - p * 0.6, CY + 9.6, 0), (-2.2 - p, CY + 10.2, 0)]
    g, lo = bow_limbs(up, [1.05, 0.95, 0.8, 0.6, 0.4], [1.15, 1.0, 0.85, 0.65, 0.45])
    f += g
    for path, s_ in ((up, 1), (lo, -1)):
        x, y, _ = path[2]
        f += curve("grip", [(x, y, 0), (x + 1.6, y + s_ * 1.6, 0), (x + 2.2, y + s_ * 3.0, 0)], [0.45, 0.35, 0.15], [0.45, 0.35, 0.15], sides=4, caps=(False, True))
        f += block("leaf", (x + 2.4, y + s_ * 3.3, -0.2), (1.6, 0.8, 0.4), rot=rot_z(s_ * 50), taper=(0.5, 0.8))
    f += d_thunder.string_and_arrow(stage, "common", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))
    return f


WEAPONS.update({
    "shrine_sword": dict(
        jp="祠の剣", tier="common", glow="wind", pose="whale", grip=(0.0, 0.6, 0.0), cen=(0.0, 8.5, 0.0), scale=11.0,
        opts=dict(
            a=(ssw_a, None, (), "祠の直剣。まっすぐな両刃と真鍮の十字鍔、柄頭に宿り木の実。いちばん「剣」らしい。"),
            b=(ssw_b, None, (), "山刀。背が厚く先が広がる片刃。島の暮らしの刃。"),
            c=(ssw_c, None, (), "葉の長剣。宿り木の葉の形の長い刃と、鯨骨の牙の鍔。"))),
    "shrine_hammer": dict(
        jp="祠の鎚", tier="common", glow="star", pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 6.5, 0.0), scale=12.0,
        opts=dict(
            a=(shm_a, None, (), "鍛冶の片手鎚。鉄の四角い頭と流木の柄。鍛冶師らしい。"),
            b=(shm_b, None, (), "木槌。太い丸太の頭の両口に鉄の輪。祠の杭を打つ槌。"),
            c=(shm_c, None, (), "鎚と鉤。片側が鎚、反対が反った鉤。山の道具。"))),
    "shrine_bow": dict(
        jp="祠の弓", tier="common", glow="wind", pose="thunder", grip=(5.0, 8.0, 0.0), cen=(2.0, 8.0, 0.0), scale=12.0, stages=4,
        opts=dict(
            a=(sbw_a, None, (), "流木の弓。一本の流木の素朴な弓、腕に縄、先に実。"),
            b=(sbw_b, None, (), "鯨骨の小弓。先が外へ反る短い骨の弓。"),
            c=(sbw_c, None, (), "枝の弓。先が二股の小枝に分かれ、葉が付く。"))),
})
