"""伝説の武器 (深淵の専用伝説) を巨神ごとに 6 職ぶんへ (2026-10-26 の声)。足す 20 品 x 3 案と、五章の武器 3 品 x 3 案の形。
骨組みは職の武器の種類ごとに 3 案 (剣・鎚・杖・弓・短剣・鈴杖)、そこへ巨神の飾り (motif) を載せて巨神ごとの品にする:
  岩鯨 = 反った骨の棘と椎骨の環 / 潮殻 = 貝の板と珊瑚の枝 / 雷翼 = 羽と稲妻の折れ / 余燼 = 玄武岩の欠片と火の籠 / 深淵 (五章) = 錨鉄の環と鎖
材質名は巨神の武器と同じ。伝説の塗り (../abyss/paint_abyss.py): 土台は深淵の紫黒 (main/edge/grip)、巨神の素材 (spine/acc/guard)、核 (gem)。
五章の 3 品は五章の装備の塗り (../gear_gaps/paint_gaps.py): 紫黒の虚・錨鉄・灯りの琥珀/淵の紫/鼓動の赤。
座標は他の武器と同じ (1 単位 = 1/16 ブロック、+Y が先、x = 0・z = 0 が握り、正面 = -Z)。弓は握り (5, 8)、弦は -x。
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
for p in (HERE.parent, HERE.parent.parent, HERE.parent / "chapter_staves", HERE.parent / "chapter_gear"):
    sys.path.insert(0, str(p))
from wkit import *  # noqa: F401,F403,E402
from wkit import block, blade, prism, curve, extrude, V, rot_x, rot_y, rot_z  # noqa: E402
from paint import line_mask  # noqa: E402

import numpy as np  # noqa: E402
from lpkit import ellipsoid, spike, loft  # noqa: E402
import d_thunder  # noqa: E402
from d_staves import circ, ell, shaft, bell, ring, hook  # noqa: E402
from d_gear import hilt, bow_grip, limbs, bend  # noqa: E402

CY = d_thunder.CY


def off(env):
    return env["lit"] < -9


def inlay(env, kind):
    """伝説の光る筋 (いつも: 刃・頭の正面の中心線。+5 で点線を足し、+10 で縁まで)"""
    face = np.isin(env["mats"], ["main"]) & (np.abs(env["N"][..., 2]) > 0.5)
    if kind == "legend":
        return face & (np.abs(env["X"]) < 0.3) & (env["Y"] > 5) & ((env["Y"] * 0.6) % 2.4 < 1.2)
    if kind == "p5":
        return face & (np.abs(env["X"]) < 0.35)
    if kind == "p10":
        return face & (np.abs(env["X"]) < 0.5)
    return off(env)


# ================================================================== 巨神の飾り
def motif(titan, at, d=(0, 1, 0), s=1.0):
    """at に巨神の飾りを一つ。d は外への向き、s は大きさ"""
    x, y, z = at
    dx, dy, dz = d
    # 巨神ごとの見分けが手の中でも付くよう、飾りは一回り大きく
    s = s * 1.35
    f = []
    if titan == "whale":
        # 反った骨の棘と、根の椎骨の環
        f += spike(BONE, "acc", at, (dx, dy, dz), 2.6 * s, 0.7 * s, sides=4, rings=2, bend=(0, 0.25 * s, 0), hint=(0, 0, 1))
        f += prism("spine", (x, y, z - 0.5 * s), (x, y, z + 0.5 * s), 0.9 * s, 0.9 * s, sides=6, caps=(True, True), hint=(1, 0, 0))
    elif titan == "tide":
        # 貝の板二枚と珊瑚の小枝
        a = math.degrees(math.atan2(dy, dx))
        f += block("acc", (x + dx * 0.8 * s, y + dy * 0.8 * s, z), (2.0 * s, 1.2 * s, 0.7), rot=rot_z(a - 90), taper=(0.6, 0.9))
        f += curve("spine", [(x, y, z), (x + dx * 1.6 * s - dy * 0.6 * s, y + dy * 1.6 * s + dx * 0.6 * s, z - 0.4), (x + dx * 2.4 * s, y + dy * 2.4 * s, z - 0.6)],
                   [0.45 * s, 0.35 * s, 0.15 * s], [0.45 * s, 0.35 * s, 0.15 * s], sides=4, caps=(False, True))
    elif titan == "thunder":
        # 羽二枚と、稲妻の折れ (二つの短い棒)
        a = math.degrees(math.atan2(dy, dx))
        for k, off_a in enumerate((-18, 18)):
            f += block("acc", (x + dx * 1.2 * s, y + dy * 1.2 * s, z + (k - 0.5) * 0.4), (0.9 * s, 2.8 * s, 0.3), rot=rot_z(a - 90 + off_a), taper=(0.3, 0.9))
        f += curve("spine", [(x, y, z - 0.5), (x + dx * 0.9 * s + 0.5 * s, y + dy * 0.9 * s, z - 0.6), (x + dx * 1.6 * s - 0.4 * s, y + dy * 1.6 * s, z - 0.6)],
                   [0.3 * s, 0.3 * s, 0.1 * s], [0.3 * s, 0.3 * s, 0.1 * s], sides=4, caps=(True, True))
    elif titan == "ember":
        # 尖った玄武岩の欠片と、火の籠の小さな環
        f += spike(BONE, "guard", at, (dx, dy, dz), 2.2 * s, 0.9 * s, sides=5, rings=1, hint=(0, 0, 1))
        f += spike(BONE, "acc", (x + dy * 0.6 * s, y - dx * 0.6 * s, z), (dx + dy * 0.4, dy - dx * 0.4, dz), 1.4 * s, 0.5 * s, sides=4, rings=1, hint=(0, 0, 1))
    else:  # abyss: 錨鉄の環と鎖の二つの輪
        f += prism("acc", (x, y, z - 0.45 * s), (x, y, z + 0.45 * s), 1.0 * s, 1.0 * s, sides=8, caps=(True, True), hint=(1, 0, 0))
        f += prism("spine", (x + dx * 1.2 * s, y + dy * 1.2 * s, z - 0.25), (x + dx * 1.2 * s, y + dy * 1.2 * s, z + 0.25), 0.6 * s, 0.6 * s, sides=6, caps=(True, True), hint=(1, 0, 0))
    return f


# ================================================================== 剣 (戦士)
def sword_a(t):
    """A 直の大刃: 幅広のまっすぐな刃と十字の鍔、鍔の両端と柄頭に巨神の飾り"""
    f = hilt(long=True)
    st = [(3.6, -2.4, 2.4, 1.0, 1.0), (11.0, -2.5, 2.5, 1.0, 1.0), (18.0, -2.0, 2.0, 0.85, 0.85), (21.4, -1.0, 1.0, 0.55, 0.55), (23.4, 0, 0, 0, 0)]
    f += blade("main", st)
    f += block("trim", (0, 3.0, 0), (7.4, 1.2, 2.4), taper=(0.85, 0.9))
    f += block("gem", (0, 5.6, -1.0), (1.0, 2.0, 0.4))
    for s_ in (1, -1):
        f += motif(t, (s_ * 3.8, 3.0, 0), (s_, 0.3, 0), 0.9)
    f += motif(t, (0, -3.6, 0), (0, -1, 0), 0.6)
    return f


def sword_b(t):
    """B 片刃の湾刀: 背が反る片刃、鍔は環、刃の背に巨神の飾りが三つ並ぶ"""
    f = hilt()
    cx = lambda y: -0.025 * (y - 3.4) ** 2
    ys = [3.4, 8.0, 12.6, 16.8, 20.0]
    f += blade("main", [(y, -2.2, 1.0, 0.8, 0.8, cx(y)) for y in ys[:-1]] + [(21.6, 0, 0, 0, 0, cx(21.6))])
    f += ring(3.0, 1.6, mat="trim", n=8, w=0.4, t=0.6)
    f += block("gem", (0, 3.0, 0), (1.0, 1.0, 0.8))
    for y in (8.0, 12.4, 16.6):
        f += motif(t, (1.2 + cx(y), y, 0), (1, 0.4, 0), 0.6)
    return f


def sword_c(t):
    """C 二又の刃: 刃が先で二つに割れ、割れ目に核が浮く。鍔の中央に巨神の飾り"""
    f = hilt(long=True)
    f += blade("main", [(3.6, -2.0, 2.0, 0.9, 0.9), (12.0, -2.2, 2.2, 0.9, 0.9), (15.0, -2.2, 2.2, 0.85, 0.85)])
    for s_ in (1, -1):
        f += curve("main", [(s_ * 1.1, 14.6, 0), (s_ * 1.6, 18.0, 0), (s_ * 1.2, 21.0, 0), (s_ * 0.4, 23.0, 0)], [1.1, 0.9, 0.6, 0.1], [0.8, 0.7, 0.5, 0.1], sides=4, caps=(False, True))
    f += ell("gem", (0, 18.6, 0), (0.8, 1.4, 0.6))
    f += block("trim", (0, 3.0, 0), (5.6, 1.1, 2.2), taper=(0.8, 0.9))
    f += motif(t, (0, 3.6, -0.6), (0, 1, -0.3), 0.8)
    return f


# ================================================================== 鎚 (鍛冶師)
def haft(top=14.0):
    f = []
    f += prism("grip", (0, -3.4, 0), (0, top, 0), 1.05, 0.95, sides=6, caps=(True, True))
    f += prism("wrap", (0, -2.4, 0), (0, 2.6, 0), 1.2, 1.2, sides=6, caps=(False, False))
    f += block("trim", (0, 3.0, 0), (2.6, 0.6, 2.6))
    f += prism("trim", (0, -4.2, 0), (0, -3.2, 0), 1.35, 1.25, sides=6, caps=(True, False))
    return f


def hammer_a(t):
    """A 大槌: 角を落とした大きな頭、頭の両側に巨神の飾り"""
    f = haft(14.6)
    f += block("main", (0, 15.4, 0), (7.0, 4.0, 3.8), taper=(0.9, 0.9))
    f += block("trim", (0, 15.4, 0), (7.2, 1.0, 4.0))
    f += block("gem", (0, 15.4, -2.0), (1.2, 1.6, 0.4))
    for s_ in (1, -1):
        f += motif(t, (s_ * 3.6, 15.4, 0), (s_, 0.2, 0), 0.9)
    return f


def hammer_b(t):
    """B 鉤の戦鎚: 前は四角い打面、後ろは巨神の飾りが鉤のように伸びる。頭の上にも小さく"""
    f = haft(14.4)
    f += block("main", (1.6, 15.0, 0), (4.0, 3.4, 3.2), taper=(0.95, 0.95))
    f += block("trim", (3.7, 15.0, 0), (0.6, 3.0, 2.8))
    f += motif(t, (-0.8, 15.0, 0), (-1, -0.3, 0), 1.3)
    f += motif(t, (1.6, 16.8, 0), (0, 1, 0), 0.6)
    f += block("gem", (1.6, 15.0, -1.7), (0.9, 0.9, 0.4))
    return f


def hammer_c(t):
    """C 輪の鎚: 頭が大きな環 (中に核)、環の四方に巨神の飾り"""
    f = haft(13.4)
    f += ring(16.6, 3.0, mat="main", n=10, w=1.0, t=1.4)
    f += ell("gem", (0, 16.6, 0), (1.4, 1.4, 1.0))
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += motif(t, (3.2 * math.cos(a), 16.6 + 3.2 * math.sin(a), 0), (math.cos(a), math.sin(a), 0), 0.6)
    return f


# ================================================================== 杖 (魔法使い)
def staff_a(t):
    """A 籠の杖: 三つの爪が核を抱き、爪の先に巨神の飾り"""
    f = shaft(15.6, upper="grip")
    for k in range(3):
        a = math.radians(90 + 120 * k)
        dx, dz = math.cos(a), math.sin(a)
        f += curve("spine", [(0, 15.2, 0), (dx * 1.8, 17.2, dz * 1.8), (dx * 2.0, 19.8, dz * 2.0), (dx * 0.8, 21.6, dz * 0.8)], [0.7, 0.6, 0.5, 0.3], [0.7, 0.6, 0.5, 0.3], sides=4, caps=(False, True))
    f += ell("gem", (0, 19.0, 0), (1.5, 1.9, 1.5))
    f += motif(t, (0, 21.6, 0), (0, 1, 0), 0.6)
    f += motif(t, (0, 14.6, -0.8), (0.6, -0.6, -0.4), 0.6)
    return f


def staff_b(t):
    """B 輪の杖: 環の中に核が浮き、環の四方に巨神の飾り"""
    f = shaft(15.2, upper="grip")
    f += ring(19.4, 3.4, mat="trim", n=10, w=0.6, t=0.8)
    f += ell("gem", (0, 19.4, 0), (1.3, 1.6, 1.3))
    for k in range(4):
        a = math.radians(90 * k)
        f += motif(t, (3.6 * math.cos(a), 19.4 + 3.6 * math.sin(a), 0), (math.cos(a), math.sin(a), 0), 0.55)
    return f


def staff_c(t):
    """C 角の杖: 柄の先で巨神の飾りが二本の角のように反り、その上に核が浮く"""
    f = shaft(16.0, upper="grip")
    f += motif(t, (0.8, 16.2, 0), (0.7, 1, 0), 1.2)
    f += motif(t, (-0.8, 16.2, 0), (-0.7, 1, 0), 1.2)
    f += ell("gem", (0, 20.4, 0), (1.2, 1.5, 1.2))
    f += block("trim", (0, 16.0, 0), (2.4, 1.0, 2.4), taper=(0.8, 0.8))
    return f


# ================================================================== 弓 (弓使い)
def bow_a(t, stage=0):
    """A 反り弓: 反った腕の先に巨神の飾り"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.0, CY + 5.2, 0), (2.2, CY + 7.8, 0), (0.0, CY + 9.6, 0), (-2.0, CY + 10.4, 0)])
    g, lo = limbs(up, [1.05, 1.0, 0.85, 0.65, 0.45], [1.25, 1.1, 0.95, 0.75, 0.5])
    f += g
    f += motif(t, up[-1], (-0.4, 1, 0), 0.7)
    f += motif(t, lo[-1], (-0.4, -1, 0), 0.7)
    return f + d_thunder.string_and_arrow(stage, "legend", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


def bow_b(t, stage=0):
    """B 重ね腕: 二枚の腕を重ね、腕の中ほどに巨神の飾り"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.4, CY + 5.2, 0), (3.0, CY + 7.8, 0), (0.8, CY + 9.8, 0), (-1.2, CY + 10.6, 0)])
    g, lo = limbs(up, [0.8, 0.75, 0.65, 0.5, 0.3], [1.5, 1.4, 1.2, 0.9, 0.55])
    f += g
    g2, _ = limbs([(x + 0.7, y, z) for x, y, z in up[:4]], [0.4, 0.35, 0.3, 0.2], [1.1, 1.0, 0.85, 0.6], mat="spine")
    f += g2
    for path, s_ in ((up, 1), (lo, -1)):
        f += motif(t, path[2], (1, s_ * 0.3, 0), 0.7)
    return f + d_thunder.string_and_arrow(stage, "legend", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


def bow_c(t, stage=0):
    """C 翼の弓: 腕の外へ巨神の飾りが三つずつ広がる (翼のよう)"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.2, CY + 5.4, 0), (2.4, CY + 8.0, 0), (0.2, CY + 9.6, 0), (-1.8, CY + 10.4, 0)])
    g, lo = limbs(up, [1.0, 0.9, 0.8, 0.6, 0.4], [1.1, 1.0, 0.85, 0.65, 0.45])
    f += g
    for path, s_ in ((up, 1), (lo, -1)):
        for i in (1, 2, 3):
            f += motif(t, path[i], (1, s_ * (0.3 + 0.3 * i), 0), 0.45)
    return f + d_thunder.string_and_arrow(stage, "legend", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


# ================================================================== 短剣 (暗殺者)
def dagger_a(t):
    """A 刺刀: 細い真っ直ぐな刃、柄頭と鍔に巨神の飾り"""
    f = hilt()
    f += blade("main", [(3.0, -1.1, 1.1, 0.75, 0.75), (8.0, -1.0, 1.0, 0.7, 0.7), (11.4, -0.5, 0.5, 0.45, 0.45), (13.4, 0, 0, 0, 0)])
    f += block("trim", (0, 2.8, 0), (3.4, 0.9, 1.8), taper=(0.8, 0.9))
    f += block("gem", (0, 5.2, -0.8), (0.5, 2.4, 0.3))
    f += motif(t, (0, -2.8, 0), (0, -1, 0), 0.6)
    return f


def dagger_b(t):
    """B 反り牙: 内へ反る牙の刃、刃の背に巨神の飾り"""
    f = hilt()
    cx = lambda y: 0.04 * (y - 3.2) ** 2
    ys = [3.2, 6.0, 8.8, 11.2, 13.0]
    wd = [1.4, 1.5, 1.3, 0.8, 0.0]
    th = [0.7, 0.7, 0.6, 0.45, 0.0]
    f += blade("main", [(y, -w, w, h, h, cx(y)) for y, w, h in zip(ys, wd, th)])
    f += block("trim", (0, 2.8, 0), (3.6, 0.9, 1.8), taper=(0.8, 0.9))
    f += motif(t, (-1.4 + cx(7.0), 7.0, 0), (-1, 0.4, 0), 0.7)
    f += block("gem", (0, 2.8, -0.9), (0.8, 0.8, 0.3))
    return f


def dagger_c(t):
    """C 二枚刃: 柄の上に左右へ開く二枚の短い刃、間に巨神の飾りが立つ"""
    f = hilt()
    for s_ in (1, -1):
        f += curve("main", [(s_ * 0.4, 3.0, 0), (s_ * 1.6, 6.0, 0), (s_ * 1.8, 9.0, 0), (s_ * 1.0, 11.4, 0)], [0.9, 0.8, 0.6, 0.1], [0.5, 0.45, 0.35, 0.05], sides=4, caps=(False, True), hint=(0, 0, 1))
    f += motif(t, (0, 4.0, 0), (0, 1, 0), 0.8)
    f += block("trim", (0, 2.8, 0), (3.8, 0.9, 1.8))
    f += ell("gem", (0, -2.6, 0), (0.8, 0.8, 0.8))
    return f


# ================================================================== 鈴杖 (呪術師)
def bellstaff_a(t):
    """A 鉤の鐘: 柄の先が鉤に曲がり、鐘が一つ垂れる。鉤の頂きに巨神の飾り"""
    f = shaft(15.0, upper="grip")
    f += hook(15.0, 4.6, mat="spine")
    f += bell(4.6, 11.0, 5.0, 2.6, 0.9, mat="acc")
    f += ell("gem", (4.6, 10.2, 0), (0.9, 0.9, 0.9))
    f += motif(t, (1.6, 19.8, 0), (-0.2, 1, 0), 0.8)
    return f


def bellstaff_b(t):
    """B 神楽の鈴: 柄の上に三段の小鈴、頂きに巨神の飾り"""
    f = shaft(13.0, upper="grip")
    f += prism("spine", (0, 12.6, 0), (0, 19.4, 0), 0.45, 0.4, sides=6, caps=(False, False))
    for (y, n, R) in ((18.2, 3, 1.5), (15.8, 4, 2.1), (13.4, 5, 2.7)):
        f += prism("trim", (0, y + 0.7, 0), (0, y + 1.0, 0), R * 0.55, R * 0.55, sides=8, caps=(True, True))
        for k in range(n):
            a = math.radians(360.0 * k / n + 90)
            f += prism("spine", (0, y + 0.85, 0), (R * math.cos(a) * 0.95, y + 0.6, R * math.sin(a) * 0.95), 0.2, 0.2, sides=4, caps=(False, False))
            f += ell("acc", (R * math.cos(a), y, R * math.sin(a)), (0.95, 0.9, 0.95))
    f += ell("gem", (0, 20.2, 0), (0.9, 0.9, 0.9))
    f += motif(t, (0, 20.6, 0), (0, 1, 0), 0.6)
    return f


def bellstaff_c(t):
    """C 輪の二つ鐘: 柄の先の環の左右に鐘が下がり、環の上に巨神の飾り"""
    f = shaft(14.8, upper="grip")
    f += ring(18.6, 3.2, mat="trim", n=10)
    f += prism("trim", (0, 14.8, 0), (0, 21.8, 0), 0.4, 0.4, sides=4, caps=(False, False))
    for s_ in (1, -1):
        f += bell(s_ * 3.2, 14.6, 3.0, 1.4, 0.5, mat="acc")
    f += ell("gem", (0, 18.6, 0), (1.0, 1.0, 1.0))
    f += motif(t, (0, 22.0, 0), (0, 1, 0), 0.7)
    return f


# ================================================================== 一覧
KINDS = dict(
    sword=dict(fns=(sword_a, sword_b, sword_c), names=("直の大刃", "片刃の湾刀", "二又の刃"), pose="whale", grip=(0.0, 1.0, 0.0), cen=(0.0, 9.0, 0.0), scale=10.0),
    hammer=dict(fns=(hammer_a, hammer_b, hammer_c), names=("大槌", "鉤の戦鎚", "輪の鎚"), pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 7.5, 0.0), scale=10.5),
    staff=dict(fns=(staff_a, staff_b, staff_c), names=("籠の杖", "輪の杖", "角の杖"), pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 9.0, 0.0), scale=9.5),
    bow=dict(fns=(bow_a, bow_b, bow_c), names=("反り弓", "重ね腕", "翼の弓"), pose="thunder", grip=(5.0, 8.0, 0.0), cen=(2.0, 8.0, 0.0), scale=12.0, stages=4),
    dagger=dict(fns=(dagger_a, dagger_b, dagger_c), names=("刺刀", "反り牙", "二枚刃"), pose="whale", grip=(0.0, 0.6, 0.0), cen=(0.0, 4.8, 0.0), scale=16.0),
    bell=dict(fns=(bellstaff_a, bellstaff_b, bellstaff_c), names=("鉤の鐘", "神楽の鈴", "輪の二つ鐘"), pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 9.0, 0.0), scale=9.5),
)
MOTIF_JP = dict(whale="骨の棘と椎骨の環", tide="貝の板と珊瑚", thunder="羽と稲妻", ember="玄武岩の欠片", abyss="錨鉄の環")


def _entry(jp, kind, titan, tier, glow):
    k = KINDS[kind]

    def wrap(fn):
        if k.get("stages", 1) > 1:
            return lambda stage=0: fn(titan, stage)
        return lambda stage=0: fn(titan)

    opts = {}
    for letter, fn, name in zip("abc", k["fns"], k["names"]):
        opts[letter] = (wrap(fn), inlay, ("edge",), f"{name}。{(fn.__doc__ or '').split(':', 1)[-1].strip()} 飾りは{MOTIF_JP[titan]}。")
    e = dict(jp=jp, tier=tier, glow=glow, pose=k["pose"], grip=k["grip"], cen=k["cen"], scale=k["scale"], opts=opts)
    if k.get("stages", 1) > 1:
        e["stages"] = k["stages"]
    return e


# 伝説 (巨神ごと。塗りは paint_abyss、glow の鍵 = 巨神)
LEGEND_ITEMS = [
    ("abyss_whale_hammer", "底なしの鯨鎚", "hammer", "whale"), ("abyss_whale_staff", "底なしの星杖", "staff", "whale"),
    ("abyss_whale_bow", "底なしの肋弓", "bow", "whale"), ("abyss_whale_fang", "底なしの牙", "dagger", "whale"),
    ("abyss_whale_bell_staff", "底なしの鯨鈴", "bell", "whale"),
    ("abyss_tide_sword", "沈み潮の剣", "sword", "tide"), ("abyss_tide_hammer", "沈み潮の鎚", "hammer", "tide"),
    ("abyss_tide_staff", "沈み潮の真珠杖", "staff", "tide"), ("abyss_tide_bow", "沈み潮の弓", "bow", "tide"),
    ("abyss_tide_dagger", "沈み潮の短剣", "dagger", "tide"),
    ("abyss_storm_sword", "雷淵の剣", "sword", "thunder"), ("abyss_storm_hammer", "雷淵の鎚", "hammer", "thunder"),
    ("abyss_storm_staff", "雷淵の杖", "staff", "thunder"), ("abyss_storm_dagger", "雷淵の爪", "dagger", "thunder"),
    ("abyss_storm_bell_staff", "雷淵の鈴杖", "bell", "thunder"),
    ("abyss_ember_sword", "灰喰いの大剣", "sword", "ember"), ("abyss_ember_staff", "灰喰いの杖", "staff", "ember"),
    ("abyss_ember_bow", "灰喰いの弓", "bow", "ember"), ("abyss_ember_dagger", "灰喰いの短剣", "dagger", "ember"),
    ("abyss_ember_bell_staff", "灰喰いの香鈴", "bell", "ember"),
]
LEGENDS = {wid: _entry(jp, kind, titan, "legend", titan) for wid, jp, kind, titan in LEGEND_ITEMS}

# 五章の武器 (エピック。塗りは paint_gaps、glow の鍵 = 灯り lamp / 淵 void / 鼓動 pulse)
CH5_ITEMS = [("abyss_lamp_staff", "灯り番の杖", "staff", "lamp"), ("abyss_depth_bow", "淵見の弓", "bow", "void"),
             ("abyss_toll_bell_staff", "弔いの鈴杖", "bell", "pulse")]
CH5 = {wid: _entry(jp, kind, "abyss", "rare", glow) for wid, jp, kind, glow in CH5_ITEMS}
