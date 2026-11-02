"""巨神のヒロイック武器 (専用の逸品) を全職ぶんへ (2026-10-28 の試遊「なぜ巨神のヒロイック武器、暗殺者と戦士しかないの」→ 1 本ずつ 3 案のラフから選ぶ)。
今ある専用の逸品 (鉾・顎牙・爪・銛・鉤槍・鍛ち鎚・火掻き) にない職の武器を巨神ごとに足す。一本ずつ形を考える (使い回しの骨組みに飾りを載せるのでなく、品ごとに一案ずつ)。
材質名は巨神の武器と同じ (main edge spine guard grip wrap acc gem trim dark cord)。塗りは ../paint.py の巨神ごとの塗り (岩鯨 = 鯨骨・石・苔・水色の光)。
座標は他の武器と同じ (1 単位 = 1/16 ブロック、+Y が先、x = 0・z = 0 が握り、広い面は ±Z、正面 = -Z)。弓は握り (5, 8)、弦は -x。部品は親へ沈めて継ぐ。

  WEAPONS[id] = dict(jp, tier, glow (巨神), pose, grip, cen, scale, opts={"a": (形の関数, inlay, edge_mats, 一言), ...})
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
for p in (HERE.parent, HERE.parent.parent, HERE.parent / "chapter_staves", HERE.parent / "chapter_gear", HERE.parent / "legends"):
    sys.path.insert(0, str(p))
from wkit import *  # noqa: F401,F403,E402
from wkit import block, blade, prism, curve, extrude, V, rot_x, rot_y, rot_z  # noqa: E402
from paint import line_mask  # noqa: E402

import numpy as np  # noqa: E402
from lpkit import ellipsoid, spike, loft  # noqa: E402
import d_thunder  # noqa: E402
from d_staves import circ, ell, shaft, bell, ring, hook  # noqa: E402
from d_gear import bow_grip, limbs, bend, hilt  # noqa: E402

CY = d_thunder.CY


def inlay(env, kind):
    """ヒロイックの光る筋: 頭・刃の正面の中心に短い筋。+5 で点線、+10 で長い筋"""
    face = np.isin(env["mats"], ["main", "spine"]) & (np.abs(env["N"][..., 2]) > 0.5)
    if kind == "rare":
        return face & (np.abs(env["X"]) < 0.3) & (env["Y"] > 11) & (env["Y"] < 15)
    if kind == "p5":
        return face & (np.abs(env["X"]) < 0.3) & (env["Y"] > 8) & ((env["Y"] * 0.7) % 2.2 < 1.1)
    if kind == "p10":
        return face & (np.abs(env["X"]) < 0.4) & (env["Y"] > 6)
    return env["lit"] < -9


def haft(top=12.0, mat="spine"):
    """鎚の柄: 握り・布の巻き・上の柄・石突き"""
    f = []
    f += prism("grip", (0, -3.0, 0), (0, 0.4, 0), 1.0, 1.0, sides=6, caps=(True, False))
    f += prism("wrap", (0, 0.2, 0), (0, 4.6, 0), 1.15, 1.15, sides=6, caps=(False, False))
    f += prism(mat, (0, 4.4, 0), (0, top, 0), 0.95, 0.85, sides=6, caps=(False, True))
    f += block("trim", (0, 4.6, 0), (2.3, 0.7, 2.3))
    f += spike(BONE, "guard", (0, -3.0, 0), (0, -1, 0), 1.6, 1.0, sides=6, rings=1, hint=(0, 0, 1))
    return f


def barnacles(c, n=5, r=1.4, s=0.55, mat="acc", seed=1):
    """フジツボの群れ (小さな円錐を寄せる)"""
    rng = np.random.default_rng(seed)
    f = []
    x, y, z = c
    for _ in range(n):
        a = rng.uniform(0, math.tau)
        d = rng.uniform(0.2, r)
        q = (x + math.cos(a) * d, y + math.sin(a) * d * 0.7, z)
        f += prism(mat, q, (q[0], q[1], q[2] - s * 1.2), s, s * 0.45, sides=5, caps=(False, True), hint=(1, 0, 0))
    return f


def link(c, R=0.9, axis="x", mat="trim"):
    """鎖の輪一つ (平たい楕円の環)"""
    f = []
    n = 8
    for k in range(n):
        a0 = math.tau * k / n
        a1 = math.tau * (k + 1) / n
        if axis == "x":
            p0 = (c[0], c[1] + R * 1.3 * math.sin(a0), c[2] + R * math.cos(a0))
            p1 = (c[0], c[1] + R * 1.3 * math.sin(a1), c[2] + R * math.cos(a1))
        else:
            p0 = (c[0] + R * math.cos(a0), c[1] + R * 1.3 * math.sin(a0), c[2])
            p1 = (c[0] + R * math.cos(a1), c[1] + R * 1.3 * math.sin(a1), c[2])
        f += curve(mat, [p0, p1], [0.28, 0.28], [0.28, 0.28], sides=4, caps=(True, True))
    return f


# ================================================================== 一章 岩鯨: 鍛冶師の鎚
def wh_hammer_a(stage=0):
    """A 椎骨の大槌: 頭は鯨の椎骨 (厚い円盤を横に寝かせ、上に背の突起、左右に横の突起)。打面は円盤の両面。柄は鯨骨"""
    f = haft(12.0, mat="main")
    f += prism("main", (-3.6, 13.6, 0), (3.6, 13.6, 0), 3.0, 3.0, sides=8, caps=(True, True), hint=(0, 0, 1))
    f += prism("spine", (-3.9, 13.6, 0), (-3.4, 13.6, 0), 3.2, 3.2, sides=8, caps=(True, True), hint=(0, 0, 1))
    f += prism("spine", (3.4, 13.6, 0), (3.9, 13.6, 0), 3.2, 3.2, sides=8, caps=(True, True), hint=(0, 0, 1))
    # 背の突起 (上へ反る板) と横の突起 (前後へ)
    f += extrude("edge", [(-1.2, 0), (1.2, 0), (0.8, 3.4), (-0.4, 5.2), (-1.0, 3.0)], 1.0, c=(0, 16.0, -0.5))
    for s in (1, -1):
        f += spike(BONE, "edge", (0, 13.6, s * 2.6), (0, -0.2, s), 2.6, 0.9, sides=4, rings=1, hint=(1, 0, 0))
    f += ell("gem", (3.95, 13.6, 0), (0.3, 1.2, 1.2))
    f += ell("gem", (-3.95, 13.6, 0), (0.3, 1.2, 1.2))
    f += ell("acc", (1.6, 11.0, -1.2), (1.3, 0.8, 0.7))
    return f


def wh_hammer_b(stage=0):
    """B 顎石の鎚: 頭は鯨の顎のような石の塊 (前へ細る)。打面にフジツボの群れ、後ろは反った牙一本。苔の房"""
    f = haft(12.4)
    f += block("guard", (0.6, 14.0, 0), (7.6, 4.4, 4.2), taper=(0.75, 0.8))
    f += block("spine", (4.4, 14.0, 0), (0.8, 4.6, 4.4))
    f += barnacles((4.9, 14.0, 0), n=6, r=1.6, s=0.55, seed=3)
    f += curve("edge", [(-3.0, 14.6, 0), (-5.2, 15.4, 0), (-6.6, 13.8, 0), (-6.8, 11.4, 0)], [1.2, 1.0, 0.7, 0.1], [1.2, 1.0, 0.7, 0.1], sides=4, caps=(False, True))
    f += ell("acc", (0.0, 16.4, -0.6), (2.2, 0.9, 1.6))
    f += ell("gem", (1.0, 14.0, -2.2), (1.0, 1.0, 0.5))
    return f


def wh_hammer_c(stage=0):
    """C 錨鎖の鎚: 頭は六角の錨石。鎖が二巻き絡み、上に吊り環。正面に水色の石が嵌まる"""
    f = haft(12.0)
    f += prism("guard", (-3.4, 14.0, 0), (3.4, 14.0, 0), 2.8, 2.8, sides=6, caps=(True, True), hint=(0, 0, 1))
    f += prism("trim", (-3.6, 14.0, 0), (-3.0, 14.0, 0), 3.0, 3.0, sides=6, caps=(True, True), hint=(0, 0, 1))
    f += prism("trim", (3.0, 14.0, 0), (3.6, 14.0, 0), 3.0, 3.0, sides=6, caps=(True, True), hint=(0, 0, 1))
    for x in (-1.4, 1.4):
        for k in range(4):
            a = math.radians(90 * k + 45)
            f += link((x, 14.0 + 2.9 * math.sin(a), 2.9 * math.cos(a)), R=0.7, axis="x")
    f += link((0, 18.0, 0), R=1.1, axis="z", mat="spine")
    f += ell("gem", (0, 14.0, -2.6), (1.2, 1.2, 0.5))
    return f


# ================================================================== 一章 岩鯨: 魔術師の杖
def wh_staff_a(stage=0):
    """A 鯨唄の竪琴: 柄の先で二本の肋が竪琴の枠になり、三本の光る弦が張られる。枠の根に水色の石"""
    f = shaft(14.6, upper="main")
    for s in (1, -1):
        f += curve("main", [(s * 0.5, 14.2, 0), (s * 3.4, 16.2, 0), (s * 3.8, 19.6, 0), (s * 2.4, 22.4, 0), (s * 0.6, 23.4, 0)],
                   [1.0, 0.9, 0.8, 0.6, 0.45], [1.0, 0.9, 0.8, 0.6, 0.45], sides=4, caps=(False, True))
    f += prism("trim", (-2.0, 23.0, 0), (2.0, 23.0, 0), 0.5, 0.5, sides=4, caps=(True, True), hint=(0, 0, 1))
    for x in (-1.6, 0.0, 1.6):
        f += prism("gem", (x, 15.6, 0), (x, 22.8, 0), 0.18, 0.18, sides=4, caps=(True, True))
    f += ell("gem", (0, 15.0, 0), (1.3, 1.1, 1.3))
    f += block("guard", (0, 14.0, 0), (2.6, 1.2, 2.4), taper=(0.85, 0.85))
    return f


def wh_staff_b(stage=0):
    """B 潮吹きの杖: 柄の先に石の噴気孔 (厚い環)。そこから水色の結晶が扇に噴き上がる"""
    f = shaft(15.0)
    f += prism("guard", (0, 14.6, 0), (0, 16.4, 0), 2.6, 2.2, sides=8, caps=(True, False))
    f += prism("spine", (0, 16.2, 0), (0, 16.9, 0), 2.4, 2.3, sides=10, caps=(True, True))
    for k, (ax, h) in enumerate(((-0.55, 5.0), (-0.25, 6.6), (0.0, 7.6), (0.25, 6.6), (0.55, 5.0))):
        f += spike(BONE, "gem", (ax * 2.0, 16.8, 0), (ax, 1, 0), h, 0.75 if k == 2 else 0.6, sides=4, rings=1, hint=(0, 0, 1))
    f += barnacles((0, 15.4, -2.2), n=4, r=1.4, s=0.45, seed=7)
    return f


def wh_staff_c(stage=0):
    """C 顎骨の月: 柄の先に三日月の顎骨 (歯が内へ並ぶ)。顎の内に三つの小石が細い棒で浮く"""
    f = shaft(14.0)
    pts = []
    for k in range(9):
        a = math.radians(200 + 140 * k / 8)
        pts.append((4.2 * math.cos(a) + 0.0, 19.4 + 4.2 * math.sin(a) + 4.2, 0))
    f += curve("main", pts, [0.6] + [1.1] * 7 + [0.6], [0.8] + [1.3] * 7 + [0.8], sides=4, caps=(True, True))
    for k in range(1, 8):
        x, y, _ = pts[k]
        cx, cy = 0.0, 23.6
        d = math.hypot(cx - x, cy - y)
        f += spike(BONE, "edge", (x, y, 0), ((cx - x) / d, (cy - y) / d, 0), 1.0, 0.35, sides=4, rings=1, hint=(0, 0, 1))
    f += prism("spine", (0, 13.8, 0), (0, 15.6, 0), 1.1, 0.9, sides=6, caps=(False, True))
    for k, (x, y) in enumerate(((-1.4, 20.6), (1.4, 20.6), (0.0, 22.8))):
        f += prism("trim", (0, 15.4, 0), (x, y, 0), 0.15, 0.15, sides=4, caps=(False, False))
        f += ell("gem", (x, y, 0), (0.8, 0.8, 0.8))
    return f


# ================================================================== 一章 岩鯨: 弓
def wh_bow_a(stage=0):
    """A 肋の長弓: 腕は二本の大きな鯨の肋 (太い根から細る)。握りは椎骨の環。腕の背に苔の筋"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.4, CY + 5.6, 0), (3.0, CY + 8.6, 0), (0.8, CY + 10.8, 0), (-1.6, CY + 11.8, 0)])
    g, lo = limbs(up, [1.3, 1.15, 0.95, 0.7, 0.4], [1.4, 1.25, 1.0, 0.75, 0.42])
    f += g
    f += prism("spine", (5.0, CY - 1.2, -1.3), (5.0, CY - 1.2, 1.3), 1.7, 1.7, sides=8, caps=(True, True), hint=(1, 0, 0))
    f += prism("spine", (5.0, CY + 1.2, -1.3), (5.0, CY + 1.2, 1.3), 1.7, 1.7, sides=8, caps=(True, True), hint=(1, 0, 0))
    for path in (up, lo):
        f += spike(BONE, "edge", path[-1], (-0.5, 1 if path is up else -1, 0), 1.6, 0.4, sides=4, rings=1, hint=(0, 0, 1))
        f += ell("acc", path[1], (0.9, 1.0, 0.8))
    return f + d_thunder.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


def wh_bow_b(stage=0):
    """B 胸鰭の弓: 腕は鯨の胸鰭 (幅のある板が先で細る)。腕の外縁に石の鱗が並ぶ"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.6, CY + 5.0, 0), (3.6, CY + 7.6, 0), (1.8, CY + 9.8, 0), (-0.4, CY + 11.0, 0)])
    g, lo = limbs(up, [0.7, 0.75, 0.7, 0.5, 0.25], [2.2, 2.0, 1.6, 1.0, 0.4])
    f += g
    for path, s_ in ((up, 1), (lo, -1)):
        for i in (1, 2, 3):
            x, y, z = path[i]
            f += block("guard", (x + 0.9, y, z), (1.2, 1.4, 1.6), rot=rot_z(-s_ * 25), taper=(0.6, 0.6))
    f += ell("gem", (5.6, CY, 0), (0.6, 0.9, 0.9))
    return f + d_thunder.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


def wh_bow_c(stage=0):
    """C 鎖鯨の弓: 腕は石の板、先に鎖の輪が二つずつ下がる。握りの前に水色の石。フジツボの群れ"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.0, CY + 5.2, 0), (2.4, CY + 7.8, 0), (0.4, CY + 9.6, 0), (-1.6, CY + 10.4, 0)])
    g, lo = limbs(up, [1.0, 0.95, 0.85, 0.65, 0.45], [1.2, 1.1, 0.95, 0.75, 0.5], mat="guard")
    f += g
    for path, s_ in ((up, 1), (lo, -1)):
        x, y, z = path[-1]
        f += link((x + 0.6, y + s_ * 1.0, z), R=0.6, axis="z")
        f += link((x + 0.9, y + s_ * 2.4, z), R=0.6, axis="x")
        f += barnacles(path[2], n=3, r=0.6, s=0.4, seed=11 if s_ > 0 else 12)
    f += ell("gem", (5.8, CY, -0.6), (0.7, 1.0, 0.7))
    return f + d_thunder.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


# ================================================================== 一章 岩鯨: 呪術師の鈴杖
def wh_bell_a(stage=0):
    """A 鯨の喉鈴: 柄の先の骨の鉤から、鯨の頭の形の石の鐘が一つ垂れる (口が下)。舌は水色の石"""
    f = shaft(15.0, upper="main")
    f += hook(15.0, 5.0, mat="main")
    f += prism("cord", (5.0, 16.0, 0), (5.0, 18.8, 0), 0.35, 0.35, sides=4, caps=(False, False))
    rings = [circ(10.4, 2.4, 8, 5.0), circ(11.6, 2.9, 8, 5.0), circ(13.8, 2.8, 8, 5.0), circ(15.6, 1.9, 8, 5.0), circ(16.4, 0.8, 8, 5.0)]
    f += loft(BONE, "guard", rings, caps=("dark", "guard"))
    f += barnacles((5.0, 14.0, -2.6), n=4, r=1.0, s=0.4, seed=21)
    f += ell("gem", (5.0, 9.8, 0), (1.0, 1.0, 1.0))
    return f


def wh_bell_b(stage=0):
    """B 椎骨の鈴連: 柄の上に椎骨の円盤が四枚重なり、円盤の間に小鈴が二つずつ下がる。頂きに水色の石"""
    f = shaft(13.0)
    for k, y in enumerate((14.0, 16.4, 18.8, 21.2)):
        R = 2.6 - 0.3 * k
        f += prism("main", (0, y - 0.5, 0), (0, y + 0.5, 0), R, R, sides=8, caps=(True, True))
        if k < 3:
            for s in (1, -1):
                f += bell(s * (R - 0.5), y - 1.6, 1.4, 0.65, 0.3, mat="acc")
    f += prism("spine", (0, 12.6, 0), (0, 22.0, 0), 0.5, 0.45, sides=6, caps=(False, False))
    f += ell("gem", (0, 22.6, 0), (1.0, 1.0, 1.0))
    return f


def wh_bell_c(stage=0):
    """C 肋籠の鐘: 柄の先に肋が籠のように組まれ、その中に石の鐘が吊られる。籠の上に鎖の環"""
    f = shaft(13.6)
    for k in range(5):
        a = math.radians(360 * k / 5)
        f += curve("main", [(0, 13.4, 0), (math.cos(a) * 3.0, 15.4, math.sin(a) * 3.0), (math.cos(a) * 3.2, 19.0, math.sin(a) * 3.2), (0, 21.6, 0)],
                   [0.55, 0.55, 0.5, 0.4], [0.55, 0.55, 0.5, 0.4], sides=4, caps=(False, False))
    f += prism("cord", (0, 18.6, 0), (0, 21.4, 0), 0.25, 0.25, sides=4, caps=(False, False))
    f += bell(0, 15.0, 3.6, 1.8, 0.7, mat="guard")
    f += ell("gem", (0, 14.4, 0), (0.7, 0.7, 0.7))
    f += link((0, 22.6, 0), R=0.8, axis="z")
    return f


# ================================================================== 二章 潮殻 (塗り: 真珠 main/edge・殻 spine・濡れ石 guard/grip・珊瑚 acc・昆布 wrap・薄紅の光)
def hexplate(c, r, mat="spine", th=0.6, axis="z"):
    """甲羅の鱗板一枚 (六角の厚い板)"""
    x, y, z = c
    if axis == "z":
        return prism(mat, (x, y, z), (x, y, z - th), r, r * 0.85, sides=6, caps=(True, True), hint=(1, 0, 0))
    return prism(mat, (x, y, z), (x - th, y, z), r, r * 0.85, sides=6, caps=(True, True), hint=(0, 0, 1))


def spiral(mat, c, r0, turns=1.6, rise=4.0, w0=1.2, n=14, axis="y"):
    """巻き (貝・渦)。c から外へ広がりながら上がる"""
    pts, ra = [], []
    for k in range(n + 1):
        t = k / n
        a = math.tau * turns * t
        r = r0 * (0.25 + 0.75 * t)
        if axis == "y":
            pts.append((c[0] + r * math.cos(a), c[1] + rise * t, c[2] + r * math.sin(a)))
        else:
            pts.append((c[0] + rise * t, c[1] + r * math.sin(a), c[2] + r * math.cos(a)))
        ra.append(w0 * (0.35 + 0.65 * t))
    return curve(mat, pts, ra, ra, sides=5, caps=(True, True))


def td_hammer_a(stage=0):
    """A 甲羅の鎚: 頭は亀の甲羅の一片 (丸い背の塊)。両面に六角の鱗板、縁は真珠の帯。頂きに珊瑚の小枝"""
    f = haft(12.0)
    f += ell("guard", (0, 14.0, 0), (4.0, 2.6, 2.8))
    for x in (-2.6, 0.0, 2.6):
        for z in (-2.5, 2.5):
            f += hexplate((x * 0.95, 14.4, z), 1.1, th=0.5 if z < 0 else -0.5)
    f += prism("main", (-4.2, 14.0, 0), (4.2, 14.0, 0), 1.0, 1.0, sides=8, caps=(True, True), hint=(0, 0, 1))
    f += curve("acc", [(0.8, 16.0, 0), (1.4, 17.6, 0), (2.4, 18.4, 0)], [0.45, 0.35, 0.15], [0.45, 0.35, 0.15], sides=4, caps=(False, True))
    f += curve("acc", [(1.2, 17.0, 0), (0.6, 18.4, 0)], [0.3, 0.12], [0.3, 0.12], sides=4, caps=(False, True))
    return f


def td_hammer_b(stage=0):
    """B 巻貝の鎚: 頭は横に寝かせた大きな巻貝。口 (前) が打面で、後ろは尖った巻きの先。口の奥に薄紅の光"""
    f = haft(12.0)
    f += spiral("main", (-3.8, 14.0, 0), 2.6, turns=1.4, rise=7.0, w0=2.2, n=14, axis="x")
    f += prism("main", (2.4, 14.0, 0), (3.6, 14.0, 0), 2.6, 2.8, sides=8, caps=(False, True), hint=(0, 0, 1))
    f += ell("gem", (3.7, 14.0, 0), (0.4, 1.4, 1.4))
    f += block("spine", (0, 12.6, 0), (2.4, 1.0, 2.4))
    return f


def td_hammer_c(stage=0):
    """C 灯台石の鎚: 頭は小さな灯台 (濡れ石の筒) を横に寝かせたもの。真ん中の灯室が薄紅に光り、両端の石が打面"""
    f = haft(12.0)
    f += prism("guard", (-4.0, 14.0, 0), (-1.2, 14.0, 0), 2.4, 2.0, sides=8, caps=(True, False), hint=(0, 0, 1))
    f += prism("guard", (1.2, 14.0, 0), (4.0, 14.0, 0), 2.0, 2.4, sides=8, caps=(False, True), hint=(0, 0, 1))
    f += prism("gem", (-1.3, 14.0, 0), (1.3, 14.0, 0), 1.7, 1.7, sides=8, caps=(False, False), hint=(0, 0, 1))
    for x in (-1.3, 1.3):
        f += prism("trim", (x - 0.3, 14.0, 0), (x + 0.3, 14.0, 0), 2.2, 2.2, sides=8, caps=(True, True), hint=(0, 0, 1))
    for k in range(4):
        a = math.radians(90 * k)
        f += prism("trim", (-1.3, 14.0 + 1.75 * math.sin(a), 1.75 * math.cos(a)), (1.3, 14.0 + 1.75 * math.sin(a), 1.75 * math.cos(a)), 0.2, 0.2, sides=4, caps=(False, False), hint=(0, 0, 1))
    f += spike(BONE, "spine", (0, 16.0, 0), (0, 1, 0), 2.0, 1.2, sides=8, rings=1, hint=(0, 0, 1))
    return f


def td_staff_a(stage=0):
    """A 真珠貝の杖: 柄の先で二枚貝が口を開き、大きな真珠を抱く。貝の縁は波打つ"""
    f = shaft(15.0)
    for s in (1, -1):
        f += extrude("main", [(0, 0), (3.6, 1.4), (4.0, 3.6), (3.0, 5.4), (1.0, 6.2), (0.2, 4.0)], 0.9, c=(s * 0.5, 15.0, s * 1.2 - 0.45), rot=None if s > 0 else rot_y(180))
    f += ell("gem", (0, 18.6, 0), (2.0, 2.0, 2.0))
    f += block("spine", (0, 15.2, 0), (2.6, 1.2, 2.6), taper=(0.8, 0.8))
    return f


def td_staff_b(stage=0):
    """B 珊瑚樹の杖: 柄の先で珊瑚が枝分かれして広がり、枝先に小さな真珠が実る"""
    f = shaft(14.0, upper="acc")
    branches = [((0, 14, 0), (-2.4, 18.0, 0), (-3.6, 21.0, -0.6)), ((0, 15, 0), (2.2, 18.6, 0.4), (3.0, 22.2, 0)),
                ((0, 16, 0), (0.4, 20.0, -0.4), (-0.6, 23.6, 0)), ((-1.2, 17.0, 0), (-0.6, 19.8, 1.0), (0.8, 21.4, 1.4))]
    for b0, b1, b2 in branches:
        f += curve("acc", [b0, b1, b2], [0.7, 0.5, 0.25], [0.7, 0.5, 0.25], sides=5, caps=(False, True))
        f += ell("gem", b2, (0.6, 0.6, 0.6))
    f += ell("main", (0, 17.4, 0), (1.1, 1.1, 1.1))
    return f


def td_staff_c(stage=0):
    """C 潮渦の杖: 柄の先で真珠の帯が渦を巻いて上り、渦の中心に大きな真珠が浮く"""
    f = shaft(14.4)
    f += spiral("main", (0, 14.4, 0), 3.0, turns=1.8, rise=8.0, w0=0.9, n=18)
    f += ell("gem", (0, 19.0, 0), (1.6, 1.6, 1.6))
    f += block("spine", (0, 14.4, 0), (2.4, 1.0, 2.4), taper=(0.8, 0.8))
    return f


def td_bow_a(stage=0):
    """A 鰭脚の弓: 腕は亀の前脚の鰭 (幅のある反った板が先で細る)。握りに甲羅の鱗板"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.6, CY + 5.4, 0), (3.2, CY + 8.4, 0), (0.8, CY + 10.4, 0), (-1.8, CY + 11.0, 0)])
    g, lo = limbs(up, [0.7, 0.7, 0.6, 0.45, 0.2], [2.4, 2.4, 1.9, 1.2, 0.4], mat="spine")
    f += g
    f += hexplate((5.8, CY, 0), 1.6, axis="x", th=-0.6)
    return f + d_thunder.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


def td_bow_b(stage=0):
    """B 珊瑚枝の弓: 真珠の腕の外へ珊瑚の枝が二本ずつ伸びる。先に小さな真珠"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.0, CY + 5.2, 0), (2.4, CY + 7.8, 0), (0.4, CY + 9.6, 0), (-1.6, CY + 10.4, 0)])
    g, lo = limbs(up, [1.0, 0.95, 0.85, 0.65, 0.45], [1.1, 1.0, 0.9, 0.7, 0.45])
    f += g
    for path, s_ in ((up, 1), (lo, -1)):
        for i in (1, 3):
            x, y, z = path[i]
            tip = (x + 2.6, y + s_ * 1.6, z)
            f += curve("acc", [(x, y, z), (x + 1.4, y + s_ * 0.4, z), tip], [0.45, 0.35, 0.15], [0.45, 0.35, 0.15], sides=4, caps=(False, True))
            f += ell("gem", tip, (0.45, 0.45, 0.45))
    return f + d_thunder.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


def td_bow_c(stage=0):
    """C 貝殻重ねの弓: 腕は重なった貝殻の板 (鱗のように並ぶ)。腕の先は巻き貝の先"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.2, CY + 5.2, 0), (2.8, CY + 7.8, 0), (0.8, CY + 9.8, 0), (-1.4, CY + 10.8, 0)])
    g, lo = limbs(up, [0.8, 0.75, 0.65, 0.5, 0.35], [1.0, 0.95, 0.85, 0.65, 0.4])
    f += g
    for path, s_ in ((up, 1), (lo, -1)):
        for i in range(4):
            (x0, y0, _), (x1, y1, _) = path[i], path[i + 1]
            a = math.degrees(math.atan2(y1 - y0, x1 - x0))
            f += block("spine", ((x0 + x1) / 2 + 0.5, (y0 + y1) / 2, 0), (2.0, 1.4, 1.6), rot=rot_z(a - 90 + s_ * 20), taper=(0.5, 1.0))
        f += spike(BONE, "main", path[-1], (-0.6, s_, 0), 1.8, 0.6, sides=5, rings=1, hint=(0, 0, 1))
    return f + d_thunder.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


def td_bell_a(stage=0):
    """A 巻貝の鈴: 柄の先の鉤から、巻貝が一つ口を下に垂れる。口の奥に真珠の舌"""
    f = shaft(15.0)
    f += hook(15.0, 4.8, mat="spine")
    f += prism("wrap", (4.8, 15.6, 0), (4.8, 18.6, 0), 0.4, 0.4, sides=4, caps=(False, False))
    f += spiral("main", (4.8, 15.6, 0), 2.2, turns=1.5, rise=-5.0, w0=1.6, n=14)
    f += ell("gem", (4.8, 10.0, 0), (0.9, 0.9, 0.9))
    return f


def td_bell_b(stage=0):
    """B 灯台の鐘: 柄の上に小さな灯台。灯室の中に鐘が吊られ、灯室が薄紅に光る。頂きに貝の笠"""
    f = shaft(13.0)
    f += prism("guard", (0, 12.8, 0), (0, 17.6, 0), 1.9, 1.5, sides=8, caps=(True, False))
    f += prism("trim", (0, 17.4, 0), (0, 17.9, 0), 2.1, 2.1, sides=8, caps=(True, True))
    for k in range(6):
        a = math.radians(60 * k)
        f += prism("trim", (1.5 * math.cos(a), 17.8, 1.5 * math.sin(a)), (1.5 * math.cos(a), 20.6, 1.5 * math.sin(a)), 0.2, 0.2, sides=4, caps=(False, False))
    f += bell(0, 18.2, 2.2, 1.0, 0.4, mat="acc")
    f += ell("gem", (0, 19.0, 0), (0.8, 0.9, 0.8))
    f += spike(BONE, "spine", (0, 20.6, 0), (0, 1, 0), 2.2, 2.0, sides=8, rings=1, hint=(0, 0, 1))
    return f


def td_bell_c(stage=0):
    """C 真珠連の鈴: 柄の先の殻の環から、真珠の連が五本垂れ、それぞれの先に小鈴"""
    f = shaft(15.6)
    f += ring(19.0, 3.0, mat="spine", n=12, w=0.7, t=0.9)
    for k in range(5):
        a = math.radians(-90 + 360 * k / 5)
        x, y = 3.0 * math.cos(a), 19.0 + 3.0 * math.sin(a)
        for j in range(3):
            f += ell("main", (x, y - 0.9 - 0.9 * j, 0), (0.42, 0.42, 0.42))
        f += bell(x, y - 4.6, 1.2, 0.55, 0.25, mat="acc")
    f += ell("gem", (0, 19.0, 0), (1.2, 1.2, 1.2))
    return f


# ================================================================== 三章 雷翼 (塗り: 銅 main/edge・鋼 spine/guard/grip・羽 acc・青白い光)
def zig(mat, p0, p1, n=4, amp=0.9, w=0.35):
    """稲妻の折れ線 (p0 から p1 へ、左右に振れる)"""
    pts = []
    for k in range(n + 1):
        t = k / n
        x = p0[0] + (p1[0] - p0[0]) * t + (amp if k % 2 else -amp) * (0 if k in (0, n) else 1)
        y = p0[1] + (p1[1] - p0[1]) * t
        z = p0[2] + (p1[2] - p0[2]) * t
        pts.append((x, y, z))
    return curve(mat, pts, [w] * (n + 1), [w] * (n + 1), sides=4, caps=(True, True))


def plume(c, d, L=3.2, w=0.9, mat="acc"):
    """羽根一枚 (根 c から向き d へ)"""
    a = math.degrees(math.atan2(d[1], d[0]))
    return block(mat, (c[0] + d[0] * L * 0.5, c[1] + d[1] * L * 0.5, c[2]), (w, L, 0.3), rot=rot_z(a - 90), taper=(0.25, 0.9))


def st_hammer_a(stage=0):
    """A 避雷の鎚: 銅の頭の上に避雷の針が立ち、頭のまわりに銅線が二巻き。打面に青白い光の目"""
    f = haft(12.0)
    f += block("main", (0, 14.0, 0), (7.4, 3.6, 3.6), taper=(0.9, 0.9))
    for x in (-1.8, 1.8):
        f += prism("edge", (x - 0.4, 14.0, 0), (x + 0.4, 14.0, 0), 2.3, 2.3, sides=8, caps=(True, True), hint=(0, 0, 1))
    f += prism("spine", (0, 15.8, 0), (0, 21.4, 0), 0.45, 0.2, sides=6, caps=(False, True))
    f += prism("trim", (0, 17.0, 0), (0, 17.4, 0), 1.0, 1.0, sides=8, caps=(True, True))
    for x in (-3.8, 3.8):
        f += ell("gem", (x, 14.0, 0), (0.4, 1.1, 1.1))
    return f


def st_hammer_b(stage=0):
    """B 翼打ちの鎚: 丸い鋼の打面の両脇から、銅の骨の翼が後ろへ開く (羽根三枚ずつ)"""
    f = haft(12.0)
    f += prism("guard", (-3.0, 14.0, 0), (3.0, 14.0, 0), 2.4, 2.4, sides=10, caps=(True, True), hint=(0, 0, 1))
    f += prism("trim", (-3.3, 14.0, 0), (-2.7, 14.0, 0), 2.6, 2.6, sides=10, caps=(True, True), hint=(0, 0, 1))
    for z in (-1, 1):
        f += curve("main", [(0, 15.6, z * 1.2), (0, 18.0, z * 3.0), (0, 19.2, z * 5.2)], [0.5, 0.45, 0.3], [0.5, 0.45, 0.3], sides=4, caps=(False, True))
        for k in range(3):
            f += at(plume((0, 0, 0), (0, 1, 0), L=3.4 - 0.6 * k, w=1.0), rot_x(z * (40 + 22 * k)), (0, 16.6 + 1.0 * k, z * (1.8 + 1.2 * k)))
    f += ell("gem", (3.2, 14.0, 0), (0.4, 1.2, 1.2))
    return f


def st_hammer_c(stage=0):
    """C 雷鳴の鉄床鎚: 頭は鉄床の形 (前へ角が伸び、後ろは平たい)。側面に稲妻の象嵌"""
    f = haft(11.6)
    f += block("guard", (-0.6, 14.0, 0), (5.6, 3.4, 3.4), taper=(0.9, 1.0))
    f += spike(BONE, "edge", (2.2, 14.6, 0), (1, 0.12, 0), 4.2, 1.4, sides=6, rings=2, hint=(0, 0, 1))
    f += block("spine", (-3.6, 14.0, 0), (1.0, 3.8, 3.8))
    for z in (-1.75, 1.75):
        f += zig("gem", (-2.6, 15.2, z), (1.0, 12.8, z), n=3, amp=0.6, w=0.28)
    return f


def st_staff_a(stage=0):
    """A 籠の雷核: 銅の肋の籠が青白い核を抱き、核から稲妻が籠の肋へ走る"""
    f = shaft(14.6, upper="spine")
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += curve("main", [(0, 14.4, 0), (math.cos(a) * 2.6, 16.6, math.sin(a) * 2.6), (math.cos(a) * 2.6, 19.8, math.sin(a) * 2.6), (0, 22.0, 0)],
                   [0.5, 0.5, 0.45, 0.35], [0.5, 0.5, 0.45, 0.35], sides=4, caps=(False, True))
    f += ell("gem", (0, 18.2, 0), (1.5, 1.6, 1.5))
    f += zig("gem", (0, 18.2, 0), (2.4, 20.6, 0), n=3, amp=0.4, w=0.16)
    f += zig("gem", (0, 18.2, 0), (-2.3, 16.4, 0), n=3, amp=0.4, w=0.16)
    f += spike(BONE, "edge", (0, 22.0, 0), (0, 1, 0), 2.4, 0.4, sides=4, rings=1, hint=(0, 0, 1))
    return f


def st_staff_b(stage=0):
    """B 嵐の目: 柄の先の銅の環に羽根が八枚放射に並び、環の中に目のような青白い石"""
    f = shaft(14.6)
    f += ring(19.0, 2.6, mat="main", n=12, w=0.6, t=0.8)
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        f += plume((2.6 * math.cos(a), 19.0 + 2.6 * math.sin(a), 0), (math.cos(a), math.sin(a), 0), L=2.8, w=1.0)
    f += ell("gem", (0, 19.0, 0), (1.4, 1.0, 0.8))
    f += ell("dark", (0, 19.0, -0.5), (0.5, 0.8, 0.4))
    return f


def st_staff_c(stage=0):
    """C 風見の杖: 柄の先に銅の風見 (矢と尾羽) と、回る四つの風杯。根に青白い石"""
    f = shaft(17.0)
    f += prism("trim", (0, 16.4, 0), (0, 17.0, 0), 1.3, 1.3, sides=8, caps=(True, True))
    f += prism("main", (-3.6, 20.4, 0), (3.0, 20.4, 0), 0.35, 0.35, sides=4, caps=(True, True), hint=(0, 0, 1))
    f += spike(BONE, "edge", (3.0, 20.4, 0), (1, 0, 0), 1.6, 0.9, sides=4, rings=1, hint=(0, 0, 1))
    f += extrude("acc", [(0, -1.6), (2.4, -0.6), (2.4, 0.6), (0, 1.6), (0.6, 0)], 0.3, c=(-3.8, 20.4, -0.15), rot=rot_y(180))
    for k in range(4):
        a = math.radians(90 * k)
        cx, cz = 2.0 * math.cos(a), 2.0 * math.sin(a)
        f += prism("spine", (0, 18.2, 0), (cx, 18.2, cz), 0.18, 0.18, sides=4, caps=(False, False))
        f += prism("main", (cx, 18.2, cz - 0.5), (cx, 18.2, cz + 0.5), 0.7, 0.2, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += ell("gem", (0, 16.0, 0), (0.9, 0.9, 0.9))
    return f


def st_bow_a(stage=0):
    """A 翼の弓: 腕は鋼の骨、外へ羽根が並ぶ (広げた翼)。握りに青白い石"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.2, CY + 5.4, 0), (2.6, CY + 8.2, 0), (0.4, CY + 10.0, 0), (-1.8, CY + 10.8, 0)])
    g, lo = limbs(up, [0.8, 0.75, 0.65, 0.5, 0.35], [0.9, 0.85, 0.75, 0.55, 0.38], mat="spine")
    f += g
    for path, s_ in ((up, 1), (lo, -1)):
        for i in (1, 2, 3, 4):
            x, y, z = path[i]
            f += plume((x + 0.3, y, z), (0.8, s_ * (0.4 + 0.15 * i), 0), L=3.6 - 0.5 * i, w=1.1)
    return f + d_thunder.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


def st_bow_b(stage=0):
    """B 稲妻の弓: 銅の腕が稲妻のように二度折れる。折れ目に青白い光"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (3.2, CY + 5.0, 0), (4.0, CY + 6.6, 0), (1.6, CY + 9.4, 0), (-1.4, CY + 10.8, 0)])
    g, lo = limbs(up, [0.95, 0.9, 0.85, 0.65, 0.4], [1.0, 0.95, 0.85, 0.65, 0.4])
    f += g
    for path in (up, lo):
        for i in (1, 2):
            f += ell("gem", path[i], (0.55, 0.55, 0.55))
    return f + d_thunder.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


def st_bow_c(stage=0):
    """C 銅線の弓: 鋼の腕に銅線が密に巻かれ、腕の先は避雷の針。巻きの間から青白い光"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.0, CY + 5.2, 0), (2.4, CY + 7.8, 0), (0.4, CY + 9.6, 0), (-1.6, CY + 10.4, 0)])
    g, lo = limbs(up, [0.9, 0.85, 0.75, 0.6, 0.4], [1.0, 0.95, 0.85, 0.65, 0.42], mat="spine")
    f += g
    for path, s_ in ((up, 1), (lo, -1)):
        for i in range(3):
            (x0, y0, _), (x1, y1, _) = path[i], path[i + 1]
            for t in (0.3, 0.7):
                x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
                a = math.degrees(math.atan2(y1 - y0, x1 - x0))
                f += block("main", (x, y, 0), (1.5, 0.45, 1.5), rot=rot_z(a - 90))
        f += spike(BONE, "edge", path[-1], (-0.6, s_, 0), 2.2, 0.3, sides=4, rings=1, hint=(0, 0, 1))
        f += ell("gem", path[2], (0.5, 0.5, 0.5))
    return f + d_thunder.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


def st_bell_a(stage=0):
    """A 風鈴の杖: 柄の先の鋼の鉤から銅の風鈴 (短い管が五本、真ん中に短冊の羽根) が下がる"""
    f = shaft(15.0)
    f += hook(15.0, 4.6, mat="spine")
    f += prism("cord", (4.6, 15.8, 0), (4.6, 17.6, 0), 0.25, 0.25, sides=4, caps=(False, False))
    f += prism("main", (4.6, 15.2, 0), (4.6, 15.9, 0), 2.0, 1.6, sides=8, caps=(True, True))
    for k in range(5):
        a = math.radians(72 * k)
        cx, cz = 4.6 + 1.4 * math.cos(a), 1.4 * math.sin(a)
        f += prism("main", (cx, 15.0, cz), (cx, 11.6 - 0.4 * (k % 2), cz), 0.35, 0.35, sides=6, caps=(True, True))
    f += plume((4.6, 14.4, 0), (0, -1, 0), L=4.0, w=1.2)
    f += ell("gem", (4.6, 13.0, 0), (0.5, 0.5, 0.5))
    return f


def st_bell_b(stage=0):
    """B 雷太鼓の鈴: 柄の先の銅の環に、小さな太鼓が六つ並ぶ (雷神の太鼓)。環の中に青白い石"""
    f = shaft(14.6)
    f += ring(19.0, 3.4, mat="main", n=12, w=0.6, t=0.8)
    for k in range(6):
        a = math.radians(30 + 60 * k)
        x, y = 3.4 * math.cos(a), 19.0 + 3.4 * math.sin(a)
        f += prism("guard", (x, y, -0.6), (x, y, 0.6), 1.0, 1.0, sides=8, caps=(True, True), hint=(1, 0, 0))
        f += prism("acc", (x, y, -0.75), (x, y, -0.6), 0.8, 0.8, sides=8, caps=(True, True), hint=(1, 0, 0))
    f += ell("gem", (0, 19.0, 0), (1.3, 1.3, 1.3))
    return f


def st_bell_c(stage=0):
    """C 羽根鐘: 柄の先に銅の鐘が口を下に吊られ、縁から羽根の房が垂れる。頂きに避雷の針"""
    f = shaft(15.2)
    f += bell(0, 15.6, 4.0, 2.4, 0.9, mat="main")
    for k in range(6):
        a = math.radians(60 * k)
        f += plume((2.3 * math.cos(a), 15.6, 2.3 * math.sin(a)), (math.cos(a) * 0.3, -1, 0), L=3.0, w=0.8)
    f += ell("gem", (0, 15.0, 0), (0.8, 0.8, 0.8))
    f += prism("spine", (0, 19.4, 0), (0, 23.0, 0), 0.35, 0.15, sides=6, caps=(False, True))
    return f


# ================================================================== 四章 余燼 (塗り: 玄武岩 main・鉄 edge/spine・焼けた岩 guard/acc・柄の木 grip・灰 wrap・橙の光)
def em_sword_a(stage=0):
    """A 溶け裂けの大刃: 幅の広い玄武岩の刃の中心を、橙に光る裂け目が先まで走る。鍔は鉄の枠"""
    f = hilt(long=True)
    st = [(3.6, -2.6, 2.6, 1.0, 1.0), (11.0, -2.7, 2.7, 1.0, 1.0), (18.0, -2.2, 2.2, 0.9, 0.9), (21.6, -1.1, 1.1, 0.55, 0.55), (23.6, 0, 0, 0, 0)]
    f += blade("main", st)
    f += zig("gem", (0, 4.4, -0.95), (0, 21.0, -0.95), n=8, amp=0.35, w=0.25)
    f += zig("gem", (0, 4.4, 0.95), (0, 21.0, 0.95), n=8, amp=0.35, w=0.25)
    f += block("edge", (0, 3.0, 0), (7.0, 1.3, 2.6), taper=(0.85, 0.9))
    f += block("guard", (0, -3.8, 0), (1.8, 1.2, 1.8))
    return f


def em_sword_b(stage=0):
    """B 鋸の鉄刃: 鉄の片刃の背に鋸の歯が並ぶ。刃の根に熾の石"""
    f = hilt(long=True)
    f += blade("edge", [(3.4, -2.0, 1.4, 0.85, 0.85), (12.0, -2.2, 1.4, 0.85, 0.85), (18.4, -1.8, 1.2, 0.7, 0.7), (22.4, 0, 0, 0, 0)])
    for k in range(6):
        y = 5.0 + 2.6 * k
        f += spike(BONE, "spine", (1.4, y, 0), (1, 0.5, 0), 1.6, 0.6, sides=4, rings=1, hint=(0, 0, 1))
    f += block("guard", (0, 3.0, 0), (5.4, 1.4, 2.6), taper=(0.8, 0.9))
    f += ell("gem", (-0.4, 5.0, -0.9), (0.8, 0.8, 0.4))
    return f


def em_sword_c(stage=0):
    """C 炉の焔刃: 刃の縁が焔のように揺れて波打つ。刃の根に炉の口のような鍔 (中に橙の光)"""
    f = hilt(long=True)
    ys = [3.6, 6.4, 9.2, 12.0, 14.8, 17.6, 20.4, 23.0]
    wd = [2.2, 2.6, 2.0, 2.5, 1.8, 2.1, 1.2, 0.0]
    th = [0.9, 0.9, 0.85, 0.85, 0.8, 0.7, 0.5, 0.0]
    f += blade("main", [(y, -w, w, h, h) for y, w, h in zip(ys, wd, th)])
    f += block("guard", (0, 2.6, 0), (6.0, 2.2, 3.0), taper=(0.7, 0.9))
    f += ell("gem", (0, 2.6, -1.4), (1.6, 0.9, 0.4))
    return f


def em_knife_a(stage=0):
    """A 黒曜の小刀: 細い黒曜の欠片の刃 (面が鋭い)。柄頭に熾の石"""
    f = hilt()
    f += blade("main", [(3.0, -1.0, 1.2, 0.7, 0.7), (6.4, -1.3, 1.0, 0.6, 0.6, 0.2), (10.0, -0.7, 0.5, 0.45, 0.45, 0.5), (13.6, 0, 0, 0, 0, 0.9)])
    f += block("edge", (0, 2.8, 0), (3.2, 0.9, 1.8), taper=(0.8, 0.9))
    f += ell("gem", (0, -2.4, 0), (0.8, 0.8, 0.8))
    return f


def em_knife_b(stage=0):
    """B 火掻きの爪: 鉄の刃が先で鉤に曲がる (火掻き棒の先)。刃の背に灰の布"""
    f = hilt()
    f += prism("edge", (0, 2.8, 0), (0, 10.0, 0), 0.9, 0.7, sides=4, caps=(False, False), hint=(0, 0, 1))
    f += curve("edge", [(0, 9.8, 0), (0, 11.6, 0), (-1.6, 13.0, 0), (-3.0, 12.4, 0)], [0.8, 0.7, 0.5, 0.1], [0.6, 0.55, 0.4, 0.1], sides=4, caps=(False, True))
    f += block("guard", (0, 2.8, 0), (3.4, 0.9, 1.8))
    f += block("wrap", (0.8, 6.0, 0), (0.5, 3.0, 1.4), rot=rot_z(-10))
    f += ell("gem", (0, 9.8, -0.7), (0.6, 0.6, 0.3))
    return f


def em_knife_c(stage=0):
    """C 二つ熾: 柄の上で二枚の短い刃が左右に開き、間に熾の石が燃える"""
    f = hilt()
    for s_ in (1, -1):
        f += blade("main", [(3.0, -0.8 + s_ * 0.0, 0.8, 0.6, 0.6, s_ * 0.6), (6.6, -1.0, 1.0, 0.55, 0.55, s_ * 1.6), (10.0, -0.6, 0.6, 0.4, 0.4, s_ * 2.0), (12.0, 0, 0, 0, 0, s_ * 1.8)])
    f += ell("gem", (0, 5.2, 0), (0.9, 1.3, 0.9))
    f += block("edge", (0, 2.8, 0), (4.0, 1.0, 1.8))
    return f


def em_staff_a(stage=0):
    """A 炉の籠の杖: 柄の先の鉄の籠の中で熾が燃える。籠の上に煙の抜ける笠"""
    f = shaft(14.4, grip="grip", upper="spine")
    for k in range(6):
        a = math.radians(60 * k)
        f += curve("edge", [(0, 14.2, 0), (math.cos(a) * 2.4, 15.8, math.sin(a) * 2.4), (math.cos(a) * 2.4, 19.2, math.sin(a) * 2.4), (0, 20.6, 0)],
                   [0.35, 0.35, 0.35, 0.3], [0.35, 0.35, 0.35, 0.3], sides=4, caps=(False, False))
    f += ell("gem", (0, 17.4, 0), (1.6, 1.8, 1.6))
    f += spike(BONE, "guard", (0, 20.6, 0), (0, 1, 0), 1.8, 2.0, sides=6, rings=1, hint=(0, 0, 1))
    return f


def em_staff_b(stage=0):
    """B 火の山の杖: 柄の先に玄武岩の小さな火の山。火口から橙の光が噴き、山肌に溶けの筋"""
    f = shaft(14.0)
    rings = [circ(14.0, 1.0, 8), circ(15.0, 3.0, 8), circ(17.6, 2.2, 8), circ(19.2, 1.2, 8)]
    f += loft(BONE, "main", rings, caps=("main", "gem"))
    f += spike(BONE, "gem", (0, 19.0, 0), (0, 1, 0), 3.0, 0.9, sides=6, rings=1, hint=(0, 0, 1))
    for k in range(3):
        a = math.radians(120 * k + 20)
        f += curve("gem", [(1.1 * math.cos(a), 19.0, 1.1 * math.sin(a)), (2.6 * math.cos(a), 15.4, 2.6 * math.sin(a))], [0.22, 0.22], [0.22, 0.22], sides=4, caps=(True, True))
    return f


def em_staff_c(stage=0):
    """C 鍛ちの火箸: 柄の先が火箸に割れ、橙に焼けた延べ棒を挟む"""
    f = shaft(14.8)
    for s_ in (1, -1):
        f += curve("edge", [(s_ * 0.4, 14.6, 0), (s_ * 1.6, 17.4, 0), (s_ * 1.4, 20.2, 0), (s_ * 0.9, 22.2, 0)], [0.5, 0.45, 0.4, 0.35], [0.5, 0.45, 0.4, 0.35], sides=4, caps=(False, True))
    f += block("gem", (0, 20.4, 0), (2.0, 3.4, 1.2))
    f += block("guard", (0, 14.8, 0), (2.4, 1.0, 2.4))
    return f


def em_bow_a(stage=0):
    """A 鉄打ちの弓: 鉄の腕に鋲が並び、腕の先に玄武岩の重り。握りに熾の石"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.0, CY + 5.2, 0), (2.4, CY + 7.8, 0), (0.4, CY + 9.6, 0), (-1.6, CY + 10.4, 0)])
    g, lo = limbs(up, [1.0, 0.95, 0.85, 0.65, 0.45], [1.2, 1.1, 0.95, 0.75, 0.5], mat="edge")
    f += g
    for path, s_ in ((up, 1), (lo, -1)):
        for i in (1, 2, 3):
            f += ell("trim", (path[i][0], path[i][1], -1.0), (0.35, 0.35, 0.3))
        f += block("main", path[-1], (1.6, 1.6, 1.6), rot=rot_z(30 * s_))
    return f + d_thunder.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


def em_bow_b(stage=0):
    """B 熾の枝弓: 腕は焼けた枝 (節のある木)。裂け目から橙の光が漏れる"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.4, CY + 5.6, 0), (3.0, CY + 8.4, 0), (0.6, CY + 10.4, 0), (-1.8, CY + 11.0, 0)])
    g, lo = limbs(up, [1.1, 1.0, 0.85, 0.65, 0.4], [1.1, 1.0, 0.85, 0.65, 0.4], mat="guard")
    f += g
    for path, s_ in ((up, 1), (lo, -1)):
        for i in (1, 3):
            f += ell("guard", path[i], (1.3, 1.3, 1.3))
        f += zig("gem", (path[1][0] - 0.6, path[1][1], -0.9), (path[3][0] - 0.4, path[3][1], -0.9), n=3, amp=0.3, w=0.2)
    return f + d_thunder.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


def em_bow_c(stage=0):
    """C 火の粉の翼: 玄武岩の腕の外へ、焔の形の棘が並ぶ (先へ小さく)"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.2, CY + 5.4, 0), (2.6, CY + 8.0, 0), (0.4, CY + 9.8, 0), (-1.8, CY + 10.6, 0)])
    g, lo = limbs(up, [0.95, 0.9, 0.8, 0.6, 0.4], [1.0, 0.95, 0.85, 0.65, 0.42])
    f += g
    for path, s_ in ((up, 1), (lo, -1)):
        for i in (1, 2, 3):
            x, y, z = path[i]
            f += spike(BONE, "gem", (x + 0.4, y, z), (0.9, s_ * 0.6, 0), 2.6 - 0.5 * i, 0.55, sides=4, rings=1, bend=(0, s_ * 0.4, 0), hint=(0, 0, 1))
    return f + d_thunder.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


def em_bell_a(stage=0):
    """A 香炉の鈴杖: 柄の先の鉄の鉤から、穴の開いた香炉が鎖で下がる。香炉の中が橙に光る"""
    f = shaft(15.0)
    f += hook(15.0, 4.8, mat="edge")
    f += prism("cord", (4.8, 13.4, 0), (4.8, 18.6, 0), 0.25, 0.25, sides=4, caps=(False, False))
    f += ell("guard", (4.8, 11.6, 0), (2.0, 1.8, 2.0))
    f += ell("gem", (4.8, 11.6, -1.2), (1.0, 0.8, 1.0))
    f += spike(BONE, "edge", (4.8, 13.2, 0), (0, 1, 0), 1.4, 1.0, sides=6, rings=1, hint=(0, 0, 1))
    return f


def em_bell_b(stage=0):
    """B 鉄鈴の連: 柄の上に鉄の小鈴が三段の鎖で連なる。頂きに熾の石"""
    f = shaft(13.0)
    f += prism("edge", (0, 12.6, 0), (0, 20.4, 0), 0.4, 0.35, sides=6, caps=(False, False))
    for y, n, R in ((19.0, 3, 1.4), (16.6, 4, 2.0), (14.2, 5, 2.6)):
        f += prism("trim", (0, y + 0.8, 0), (0, y + 1.1, 0), R * 0.5, R * 0.5, sides=8, caps=(True, True))
        for k in range(n):
            a = math.radians(360 * k / n + 90)
            f += bell(R * math.cos(a), y - 0.6, 1.3, 0.6, 0.25, mat="edge", cz=R * math.sin(a))
    f += ell("gem", (0, 21.0, 0), (1.0, 1.0, 1.0))
    return f


def em_bell_c(stage=0):
    """C 焼け鐘: 柄の先に大きな鉄の鐘 (口が上の杯の形)。割れ目から橙の光、縁に玄武岩の棘"""
    f = shaft(13.6)
    rings = [circ(13.6, 1.0, 8), circ(15.2, 2.0, 8), circ(17.6, 3.0, 8), circ(19.4, 3.4, 8)]
    f += loft(BONE, "edge", rings, caps=("edge", "dark"))
    f += ell("gem", (0, 19.2, 0), (2.0, 1.2, 2.0))
    f += zig("gem", (2.4, 18.8, -1.6), (1.4, 15.0, -1.0), n=3, amp=0.3, w=0.2)
    for k in range(5):
        a = math.radians(72 * k)
        f += spike(BONE, "main", (3.2 * math.cos(a), 19.4, 3.2 * math.sin(a)), (math.cos(a) * 0.5, 1, math.sin(a) * 0.5), 2.0, 0.6, sides=4, rings=1, hint=(0, 0, 1))
    return f


# ================================================================== 一覧
def _w(jp, opts, pose, grip, cen, scale, stages=1, titan="whale"):
    e = dict(jp=jp, tier="rare", glow=titan, pose=pose, grip=grip, cen=cen, scale=scale,
             opts={k: (fn, inlay, ("edge",), (fn.__doc__ or "").split(":", 1)[-1].strip()) for k, fn in zip("abc", opts)})
    if stages > 1:
        e["stages"] = stages
    return e


WEAPONS = {
    "whale_choir_hammer": _w("鯨唄の鎚", (wh_hammer_a, wh_hammer_b, wh_hammer_c), "ember", (0.0, 0.0, 0.0), (0.0, 7.5, 0.0), 10.5),
    "whale_star_staff": _w("鯨星の杖", (wh_staff_a, wh_staff_b, wh_staff_c), "whale", (0.0, 1.5, 0.0), (0.0, 9.5, 0.0), 9.0),
    "whale_rib_longbow": _w("鯨肋の長弓", (wh_bow_a, wh_bow_b, wh_bow_c), "thunder", (5.0, 8.0, 0.0), (2.0, 8.0, 0.0), 11.5, stages=4),
    "whale_deep_bell_staff": _w("深鳴りの鈴杖", (wh_bell_a, wh_bell_b, wh_bell_c), "whale", (0.0, 1.5, 0.0), (0.0, 9.0, 0.0), 9.5),
    "tide_reef_hammer": _w("潮礁の鎚", (td_hammer_a, td_hammer_b, td_hammer_c), "ember", (0.0, 0.0, 0.0), (0.0, 7.5, 0.0), 10.5, titan="tide"),
    "tide_pearl_staff": _w("潮月の真珠杖", (td_staff_a, td_staff_b, td_staff_c), "whale", (0.0, 1.5, 0.0), (0.0, 9.5, 0.0), 9.0, titan="tide"),
    "tide_coral_bow": _w("珊瑚の弓", (td_bow_a, td_bow_b, td_bow_c), "thunder", (5.0, 8.0, 0.0), (2.0, 8.0, 0.0), 11.5, stages=4, titan="tide"),
    "tide_surf_bell_staff": _w("潮騒の鈴杖", (td_bell_a, td_bell_b, td_bell_c), "whale", (0.0, 1.5, 0.0), (0.0, 9.0, 0.0), 9.5, titan="tide"),
    "storm_anvil_hammer": _w("雷床の鎚", (st_hammer_a, st_hammer_b, st_hammer_c), "ember", (0.0, 0.0, 0.0), (0.0, 7.5, 0.0), 10.5, titan="thunder"),
    "storm_cloud_staff": _w("雷雲の杖", (st_staff_a, st_staff_b, st_staff_c), "whale", (0.0, 1.5, 0.0), (0.0, 9.5, 0.0), 9.0, titan="thunder"),
    "storm_gale_bow": _w("疾風の弓", (st_bow_a, st_bow_b, st_bow_c), "thunder", (5.0, 8.0, 0.0), (2.0, 8.0, 0.0), 11.5, stages=4, titan="thunder"),
    "storm_thunder_bell_staff": _w("雷鈴の杖", (st_bell_a, st_bell_b, st_bell_c), "whale", (0.0, 1.5, 0.0), (0.0, 9.0, 0.0), 9.5, titan="thunder"),
    "ember_cinder_blade": _w("熾の大刃", (em_sword_a, em_sword_b, em_sword_c), "whale", (0.0, 1.0, 0.0), (0.0, 9.0, 0.0), 10.0, titan="ember"),
    "ember_ash_knife": _w("灰の小刀", (em_knife_a, em_knife_b, em_knife_c), "whale", (0.0, 0.6, 0.0), (0.0, 4.8, 0.0), 16.0, titan="ember"),
    "ember_kiln_staff": _w("炉心の杖", (em_staff_a, em_staff_b, em_staff_c), "whale", (0.0, 1.5, 0.0), (0.0, 9.5, 0.0), 9.0, titan="ember"),
    "ember_flare_bow": _w("火の粉の弓", (em_bow_a, em_bow_b, em_bow_c), "thunder", (5.0, 8.0, 0.0), (2.0, 8.0, 0.0), 11.5, stages=4, titan="ember"),
    "ember_censer_bell_staff": _w("香炉の鈴杖", (em_bell_a, em_bell_b, em_bell_c), "whale", (0.0, 1.5, 0.0), (0.0, 9.0, 0.0), 9.5, titan="ember"),
}
