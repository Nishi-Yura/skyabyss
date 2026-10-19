"""深淵の専用伝説 4 点 x 3 案の形。深淵の紫黒 (main / edge / grip) に、風化した巨神の素材 (spine / acc / guard)、紫銀の縁 (trim)、
巨神の色の核 (gem) を載せる。座標は他の武器と同じ (1 単位 = 1/16 ブロック、+Y が先、x = 0・z = 0 が中心、広い面は ±Z、正面 = -Z)。
鎌の刃は -x (斧と同じ)、弓は弦が x = -3.7 (休め)・握りが x = 5。

  WEAPONS[id] = dict(titan, grip, name, opts={"a": (形の関数, inlay, edge_mats, 一言), ...}, stages)
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from wkit import *  # noqa: F401,F403,E402
from wkit import block, blade, prism, curve, extrude, feather, at, V, rot_x, rot_y, rot_z  # noqa: E402
from paint import line_mask, edge_mask, spot_mask  # noqa: E402
import d_thunder  # noqa: E402

import numpy as np  # noqa: E402
from lpkit import ellipsoid, tube  # noqa: E402


def circ(y, r, n=8, cx=0.0, cz=0.0, ph=0.0):
    return [V(cx + r * math.cos(math.radians(ph + 360.0 * k / n)), y, cz + r * math.sin(math.radians(ph + 360.0 * k / n))) for k in range(n)]


def off(env):
    return env["lit"] < -9


# ================================================================== 底なしの大剣 (岩鯨。骨の白紫)
def sword_hilt(pommel="disc"):
    f = []
    f += prism("grip", (0, -1.4, 0), (0, 0.2, 0), 1.15, 1.0, sides=6, caps=(False, False))
    f += prism("wrap", (0, 0.2, 0), (0, 1.9, 0), 1.2, 1.05, sides=6, caps=(False, False))
    f += prism("grip", (0, 1.9, 0), (0, 3.4, 0), 1.15, 1.0, sides=6, caps=(False, False))
    if pommel == "disc":      # 椎骨の円盤
        f += prism("spine", (0, -3.0, 0), (0, -1.5, 0), 1.9, 1.5, sides=6, caps=(True, False), phase=30)
    else:
        f += block("guard", (0, -2.3, 0), (3.2, 1.7, 2.8), taper=(0.7, 0.7), skip=("u",))
    f += block("trim", (0, -1.35, 0), (2.6, 0.5, 2.2))
    return f


def gs_a(stage=0):
    """A 裂け刃: 刃が縦に割れて二枚。割れ目の底で虚の光が見える。鍔は下へ垂れる 2 本の肋、背に風化した骨の歯。"""
    f = sword_hilt()
    # 左の刃は真っ直ぐ長く、右の刃は外へ反って短く欠ける
    L_ = [(5.0, -2.0, 2.0, 1.1, 1.1, -2.7), (9.5, -2.1, 2.1, 1.2, 1.2, -2.7), (14.5, -2.0, 2.0, 1.1, 1.1, -2.9),
          (18.0, -1.5, 1.5, 0.9, 0.9, -3.2), (21.4, 0, 0, 0, 0, -3.6)]
    R_ = [(5.0, -2.0, 2.0, 1.1, 1.1, 2.7), (9.5, -2.1, 2.1, 1.2, 1.2, 2.9), (13.5, -1.9, 1.9, 1.1, 1.1, 3.4),
          (16.0, -1.3, 1.3, 0.8, 0.8, 4.1), (18.2, 0, 0, 0, 0, 5.0)]
    f += blade("main", L_)
    f += blade("main", R_)
    # 割れ目の底の光る板 (両方の刃に半分埋まる)
    f += block("gem", (0, 10.0, 0), (2.0, 9.0, 1.6), taper=(0.6, 0.6))
    f += block("gem", (0, 15.0, 0), (1.8, 3.4, 1.2), taper=(0.5, 0.5))
    # 鍔: 下へ垂れて先が反る肋 2 本 + 骨の塊
    for s in (1, -1):
        f += curve("spine", [(s * 1.4, 4.4, 0), (s * 3.8, 4.2, 0), (s * 5.4, 3.0, 0), (s * 5.8, 0.8, 0)],
                   [1.5, 1.3, 1.0, 0.4], [1.7, 1.5, 1.1, 0.5], sides=4, caps=(False, False))
        f += block("acc", (s * 4.0, 5.2, 0), (1.3, 1.5, 1.7), taper=(0.6, 0.7))
    f += block("guard", (0, 4.4, 0), (4.2, 2.2, 3.4), taper=(0.9, 0.9))
    f += block("trim", (0, 5.7, 0), (3.6, 0.7, 2.6))
    # 背の欠けた骨の歯 (左の刃の外側)
    for y, h in ((8.0, 2.6), (12.0, 2.2), (16.0, 1.6)):
        f += spike(BONE, "acc", (-4.8, y, 0), (-1, 0.5, 0), h, 0.9, sides=4, rings=2, hint=(0, 0, 1))
    return f


def inlay_gs_a(env, kind):
    cx = lambda y: np.where(y < 100, -2.4, 0.0)
    if kind == "legend":
        return line_mask(env, ["main"], lambda y: -2.7 + 0 * y, 6.0, 12.0, 0.4)
    if kind == "p5":
        return line_mask(env, ["main"], lambda y: -2.7 + 0 * y, 6.0, 19.0, 0.4, dash=(3.0, 1.6)) | line_mask(env, ["main"], lambda y: 2.8 + 0 * y, 6.0, 14.0, 0.4, dash=(3.0, 1.6))
    if kind == "p10":
        return line_mask(env, ["main"], lambda y: -2.7 + 0 * y, 6.0, 20.0, 0.45) | line_mask(env, ["main"], lambda y: 3.0 + 0 * y, 6.0, 16.0, 0.45)
    return off(env)


def gs_b(stage=0):
    """B 断崖: 厚い一枚刃の右縁が地層のように段になって落ちる。幅広い虚の石の板。背に骨の肋が三本。"""
    f = sword_hilt(pommel="block")
    st = [(5.0, -3.0, 3.0, 1.3, 1.3), (9.0, -3.1, 3.1, 1.4, 1.4), (9.5, -3.1, 2.4, 1.3, 1.3), (13.0, -3.0, 2.4, 1.2, 1.2),
          (13.5, -3.0, 1.7, 1.1, 1.1), (17.0, -2.8, 1.7, 1.0, 1.0), (17.5, -2.6, 1.0, 0.9, 0.9), (19.0, -2.2, 0.5, 0.6, 0.6),
          (21.0, -2.4, -2.4, 0, 0)]
    f += blade("main", st, colmats=["main", "main", "edge", "edge", "main", "main"])
    # 背 (左) の骨の肋 3 本: 風化した白紫、外へ反って先が欠ける
    for y in (7.0, 11.0, 15.0):
        f += curve("acc", [(-2.4, y, 0), (-4.4, y + 0.6, 0), (-5.6, y + 2.2, 0)], [1.3, 1.1, 0.35], [1.5, 1.2, 0.4], sides=4, caps=(False, False))
    # 鍔: 幅広い石板に骨の板
    f += block("guard", (0, 4.2, 0), (9.0, 1.8, 3.4), taper=(0.9, 0.9))
    f += block("spine", (-3.6, 5.4, 0), (3.0, 1.2, 3.0), taper=(0.8, 0.8))
    f += block("trim", (0, 5.3, 0), (7.4, 0.6, 2.8))
    f += block("gem", (0, 4.4, 0), (1.8, 1.2, 3.8), taper=(0.9, 0.9))
    return f


def inlay_gs_b(env, kind):
    ys = (9.2, 13.2, 17.2)
    if kind == "legend":
        m = off(env)
        for y in ys[:1]:
            m = m | line_mask(env, ["main"], lambda yy: 0.0 * yy, y - 0.5, y + 0.5, 3.2, zmax=None)
        return m
    if kind == "p5":
        m = off(env)
        for y in ys[:2]:
            m = m | line_mask(env, ["main"], lambda yy: 0.0 * yy, y - 0.5, y + 0.5, 3.2)
        return m
    if kind == "p10":
        m = off(env)
        for y in ys:
            m = m | line_mask(env, ["main"], lambda yy: 0.0 * yy, y - 0.55, y + 0.55, 3.2)
        return m
    return off(env)


def gs_c(stage=0):
    """C 牙: 反った一本の巨大な牙。根は風化した骨 (白紫)、先へ向かって虚の紫黒に溶ける。鍔は顎のあご骨の二股。"""
    f = sword_hilt()
    cxf = lambda y: 0.012 * (y - 5.0) ** 2 * 0.5
    ys = [5.0, 8.0, 11.0, 14.0, 17.0, 19.6, 21.6]
    wd = [2.3, 2.5, 2.4, 2.0, 1.4, 0.8, 0.0]
    th = [1.5, 1.6, 1.5, 1.3, 1.0, 0.6, 0.0]
    st = [(y, -w, w, t, t, cxf(y)) for y, w, t in zip(ys, wd, th)]
    cm_bone = ["acc", "acc", "acc", "acc", "acc", "acc"]
    cm_void = ["edge", "main", "edge", "edge", "main", "edge"]
    f += blade("acc", st[:3], colmats=cm_bone)
    f += blade("main", st[2:], colmats=cm_void)
    # 根の骨の環 (年輪のような 2 本の帯)
    for y in (6.0, 8.6):
        f += block("trim", (cxf(y), y, 0), (5.4, 0.55, 3.4))
    # 鍔: 二股のあご骨 (下向きに開く)
    for s in (1, -1):
        f += curve("spine", [(s * 1.0, 4.2, 0), (s * 4.0, 4.6, 0), (s * 6.4, 3.6, 0), (s * 6.8, 1.6, 0)],
                   [1.6, 1.3, 1.0, 0.35], [1.8, 1.4, 1.1, 0.45], sides=4, caps=(False, False))
        f += spike(BONE, "acc", (s * 6.6, 2.0, 0), (s * 0.15, -1, 0), 2.6, 0.7, sides=4, rings=2, hint=(0, 0, 1))
    f += block("guard", (0, 4.2, 0), (3.6, 2.0, 3.4), taper=(0.9, 0.9))
    f += block("gem", (0, 5.3, 0), (1.5, 1.1, 1.9), taper=(0.8, 0.8))
    return f


def inlay_gs_c(env, kind):
    cx = lambda y: 0.012 * (y - 5.0) ** 2 * 0.5
    if kind == "legend":
        return line_mask(env, ["main"], cx, 11.5, 15.0, 0.45)
    if kind == "p5":
        return line_mask(env, ["main"], cx, 11.0, 19.0, 0.45, dash=(3.0, 1.6))
    if kind == "p10":
        return line_mask(env, ["main"], cx, 11.0, 20.2, 0.5)
    return off(env)


# ================================================================== 沈み鐘の杖 (潮殻。青緑・真珠)
def staff_shaft(top=13.0):
    f = []
    f += prism("grip", (0, -2.4, 0), (0, 0.2, 0), 1.0, 1.0, sides=6, caps=(False, False))
    f += prism("wrap", (0, 0.2, 0), (0, 4.6, 0), 1.15, 1.15, sides=6, caps=(False, False))
    f += prism("grip", (0, 4.6, 0), (0, top, 0), 0.95, 0.95, sides=6, caps=(False, True))
    f += block("trim", (0, 5.0, 0), (2.5, 0.6, 2.5))
    f += block("trim", (0, -0.1, 0), (2.4, 0.6, 2.4))
    f += spike(BONE, "spine", (0, -2.4, 0), (0, -1, 0), 1.8, 1.2, sides=6, rings=1, hint=(0, 0, 1))
    return f


def bell(cx, cy_mouth, h, r_mouth, r_top, sides=8, mat="guard", lip="trim", cap_mouth="dark"):
    """釣り鐘 (口が下)。口の縁に紫銀の帯、口は暗い板で塞ぐ。"""
    rings = [circ(cy_mouth, r_mouth, sides, cx), circ(cy_mouth + h * 0.18, r_mouth * 0.9, sides, cx),
             circ(cy_mouth + h * 0.55, r_top * 1.5, sides, cx), circ(cy_mouth + h * 0.85, r_top * 1.1, sides, cx), circ(cy_mouth + h, r_top * 0.8, sides, cx)]
    f = loft(BONE, mat, rings, caps=(cap_mouth, mat))
    f += prism(lip, (cx, cy_mouth - 0.25, 0), (cx, cy_mouth + 0.45, 0), r_mouth + 0.35, r_mouth + 0.2, sides=sides, caps=(True, True), phase=0)
    return f


def st_a(stage=0):
    """A 吊り鐘: 杖の先が鉤に曲がり、風化した鐘が一つ吊るされる。鐘の下に真珠の舌が光る。"""
    f = staff_shaft(top=13.4)
    f += curve("spine", [(0, 12.8, 0), (0, 15.8, 0), (1.4, 18.2, 0), (3.8, 18.6, 0), (5.0, 17.2, 0), (5.0, 16.0, 0)],
               [0.95, 0.95, 0.85, 0.75, 0.55, 0.5], [0.95, 0.95, 0.85, 0.75, 0.55, 0.5], sides=6, caps=(False, False), phase=30)
    f += block("trim", (0, 13.4, 0), (2.6, 0.7, 2.6))
    # 鐘 (x = 5、口が y = 8.4)
    f += prism("spine", (5.0, 15.6, 0), (5.0, 16.4, 0), 0.9, 0.7, sides=6, caps=(True, False))
    f += bell(5.0, 8.4, 7.3, 3.3, 1.5, sides=8)
    # 舌 (真珠) と吊り輪
    f += prism("spine", (5.0, 7.0, 0), (5.0, 8.6, 0), 0.4, 0.4, sides=4, caps=(False, False))
    f += ellipsoid(BONE, "gem", (5.0, 6.6, 0), (1.1, 1.1, 1.1), segs=6, rings=2)
    # 鐘の肩の貝の板
    for s in (1, -1):
        f += block("acc", (5.0 + s * 3.0, 11.4, 0), (1.0, 2.4, 1.8), rot=rot_z(-s * 18), taper=(0.6, 0.7))
    return f


def inlay_st_a(env, kind):
    if kind == "legend":
        return spot_mask(env, ["guard"], (5.0, 10.0, -2.6), 0.9)
    if kind == "p5":
        return (np.isin(env["mats"], ["guard"]) & (env["Y"] > 8.5) & (env["Y"] < 14.0) & (np.abs(((env["X"] - 5.0) * 1.5 + env["Z"]) % 2.4) < 0.5))
    if kind == "p10":
        return (np.isin(env["mats"], ["guard"]) & (env["Y"] > 8.5) & (env["Y"] < 15.0) & (np.abs(((env["X"] - 5.0) * 1.5 + env["Z"]) % 1.8) < 0.55))
    return off(env)


def st_b(stage=0):
    """B 真珠の杯: 杖の先に大きく開いた鐘が口を上にして載り、真珠を抱く。外側に風化した貝の鰭が三枚。"""
    f = staff_shaft(top=12.0)
    f += block("trim", (0, 12.0, 0), (3.0, 0.7, 3.0))
    # 鐘の杯 (口が上: 下の細い首から上へ広がる)
    rings = [circ(12.2, 1.1, 8), circ(13.4, 1.8, 8), circ(15.4, 3.3, 8), circ(17.2, 4.3, 8), circ(17.8, 4.5, 8)]
    f += loft(BONE, "guard", rings, caps=("guard", "dark"))
    f += prism("trim", (0, 17.2, 0), (0, 18.0, 0), 4.7, 4.5, sides=8, caps=(True, False), phase=0)
    # 真珠 (杯の口から半分出る) と台座の爪
    f += ellipsoid(BONE, "gem", (0, 18.3, 0), (3.0, 3.0, 3.0), segs=8, rings=3)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += spike(BONE, "acc", (math.cos(a) * 4.4, 17.6, math.sin(a) * 4.4), (math.cos(a) * 0.5, 1, math.sin(a) * 0.5), 3.4, 0.7, sides=4, rings=2, hint=(0, 1, 0))
    # 鐘の外側の貝の鰭 (欠けた扇)
    for s in (1, -1):
        f += feather("acc", (s * 2.0, 14.8, 0), (s * 5.8, 12.0, 0), 1.6, 0.8, big=False)
    return f


def inlay_st_b(env, kind):
    ring_ = np.isin(env["mats"], ["guard"]) & (env["Y"] > 13.0) & (env["Y"] < 17.0)
    if kind == "legend":
        return ring_ & (np.abs((np.arctan2(env["Z"], env["X"]) * 2.0) % 1.5) < 0.2) & (env["Y"] > 15.0)
    if kind == "p5":
        return ring_ & (np.abs((np.arctan2(env["Z"], env["X"]) * 2.0) % 1.0) < 0.2)
    if kind == "p10":
        return ring_ & (np.abs((np.arctan2(env["Z"], env["X"]) * 2.0) % 0.8) < 0.26)
    return off(env)


def st_c(stage=0):
    """C 二つ鐘: 杖の先が横木になり、左右に小さな鐘が垂れ下がる。真ん中の真珠の尖り。"""
    f = staff_shaft(top=13.0)
    f += block("trim", (0, 13.4, 0), (3.0, 0.8, 3.0))
    f += block("guard", (0, 14.6, 0), (3.2, 2.2, 3.0), taper=(0.9, 0.9))
    # 横木 (反って両端が下がる)
    for s in (1, -1):
        f += curve("spine", [(s * 1.0, 14.8, 0), (s * 3.6, 15.2, 0), (s * 6.2, 14.4, 0), (s * 7.2, 13.2, 0)],
                   [1.0, 0.9, 0.8, 0.6], [1.0, 0.9, 0.8, 0.6], sides=6, caps=(False, False), phase=30)
        # 小さな鐘 (口が y = 6.6)
        f += prism("spine", (s * 7.2, 13.0, 0), (s * 7.2, 12.3, 0), 0.6, 0.6, sides=6, caps=(False, False))
        f += bell(s * 7.2, 7.4, 5.0, 2.5, 1.1, sides=8, mat="guard" if s > 0 else "acc")
        f += ellipsoid(BONE, "gem", (s * 7.2, 6.4, 0), (0.8, 0.8, 0.8), segs=6, rings=2)
    # 真ん中の真珠の尖り
    f += spike(BONE, "acc", (0, 15.4, 0), (0, 1, 0), 4.0, 1.5, sides=6, rings=2, hint=(0, 0, 1))
    f += ellipsoid(BONE, "gem", (0, 19.6, 0), (1.2, 1.2, 1.2), segs=6, rings=2)
    return f


def inlay_st_c(env, kind):
    sides = np.isin(env["mats"], ["guard", "acc"]) & (np.abs(env["X"]) > 4.0) & (env["Y"] > 7.6) & (env["Y"] < 12.4)
    if kind == "legend":
        return sides & (np.abs((env["Y"] * 1.2 + env["Z"]) % 2.0) < 0.35) & (env["Y"] > 9.5)
    if kind == "p5":
        return sides & (np.abs((env["Y"] * 1.2 + env["Z"]) % 1.6) < 0.4)
    if kind == "p10":
        return sides & (np.abs((env["Y"] * 1.2 + env["Z"]) % 1.2) < 0.45)
    return off(env)


# ================================================================== 雷淵の弓 (雷翼。嵐の銅)
CY = d_thunder.CY
STRING_X = d_thunder.STRING_X
TIP_PULL = d_thunder.TIP_PULL


def bow_grip():
    f = []
    f += prism("grip", (5.0, CY - 2.6, 0), (5.0, CY - 0.9, 0), 1.0, 1.15, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (5.0, CY - 0.9, 0), (5.0, CY + 0.9, 0), 1.15, 1.3, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += prism("grip", (5.0, CY + 0.9, 0), (5.0, CY + 2.6, 0), 1.15, 1.0, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += block("trim", (5.0, CY + 2.9, 0), (2.4, 0.5, 2.4))
    f += block("trim", (5.0, CY - 2.9, 0), (2.4, 0.5, 2.4))
    f += block("gem", (6.2, CY, 0), (0.9, 1.2, 1.4), taper=(0.8, 0.8))
    return f


def mirror_y(path):
    return [(x, 2 * CY - y, z) for x, y, z in path]


def bw_a(stage=0):
    """A 稲妻の腕: 弓の腕が稲妻のように折れ曲がり、折れ目に風化した銅の継ぎ手。先に銅の爪。"""
    f = bow_grip()
    pull = TIP_PULL[stage]
    up = [(5.0, 10.6, 0), (2.8, 12.2, 0), (5.6, 13.6, 0), (1.8, 15.2, 0), (3.6, 16.4, 0), (-1.8 - pull, 17.8, 0)]
    # 先の寄せ (引くと先が弦の側へ)
    up = [(x - (pull * 0.35 if i >= 3 else 0), y, z) for i, (x, y, z) in enumerate(up)]
    lo = mirror_y(up)
    for path in (up, lo):
        sg = 1 if path is up else -1
        f += curve("main", path, [0.95, 0.9, 0.85, 0.75, 0.6, 0.4], [1.1, 1.0, 0.95, 0.85, 0.7, 0.5], sides=4, caps=(False, False), hint=(0, 0, 1))
        for i in (1, 2, 3):
            px, py, _ = path[i]
            f += block("spine", (px, py, 0), (1.5, 1.2, 1.6), taper=(0.8, 0.8))
        px, py, _ = path[-1]
        f += block("acc", (px, py + sg * 0.25, 0), (1.5, 1.4, 1.7), taper=(0.7, 0.7))
        f += spike(BONE, "acc", (px, py, 0), (-0.8, sg, 0), 2.2, 0.6, sides=4, rings=2, hint=(0, 0, 1))
    f += d_thunder.string_and_arrow(stage, "legend", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))
    return f


def inlay_bw_a(env, kind):
    lim = np.isin(env["mats"], ["main"]) & (np.abs(env["N"][..., 2]) > 0.55)
    band = np.abs(env["Y"] - CY) > 3.4
    if kind == "legend":
        return lim & band & (np.abs(env["Y"] - CY) < 6.6) & (np.abs((env["Y"] * 2.2) % 3.0) < 0.5)
    if kind == "p5":
        return lim & band & (np.abs((env["Y"] * 2.2) % 3.0) < 0.6)
    if kind == "p10":
        return lim & band
    return off(env)


def bw_b(stage=0):
    """B 雷環の弓: なめらかに反った虚の腕。握りの前に銅の環が二つ並び、矢が環をくぐる。"""
    f = bow_grip()
    pull = TIP_PULL[stage]
    up = [(5.0, 10.6, 0), (4.4, 12.8, 0), (2.8, 15.0, 0), (0.4 - pull * 0.5, 16.8, 0), (-2.6 - pull, 17.6, 0)]
    lo = mirror_y(up)
    for path in (up, lo):
        sg = 1 if path is up else -1
        f += curve("main", path, [0.95, 0.85, 0.7, 0.55, 0.4], [1.2, 1.05, 0.85, 0.65, 0.45], sides=4, caps=(False, False), hint=(0, 0, 1))
        px, py, _ = path[-1]
        f += block("spine", (px, py + sg * 0.25, 0), (1.5, 1.5, 1.7), taper=(0.7, 0.7))
        # 腕の途中の風化した銅の帯
        bx, by, _ = path[2]
        f += block("acc", (bx, by, 0), (2.4, 1.2, 2.2), taper=(0.9, 0.9))
    # 銅の環 (矢の通り道。yz 面の八角の環)
    for x, r in ((6.4, 2.9), (8.6, 2.1)):
        pts = [(x, CY + r * math.cos(math.radians(45 * k)), r * math.sin(math.radians(45 * k))) for k in range(9)]
        f += curve("acc", pts, [0.5] * 9, [0.5] * 9, sides=4, caps=(False, False), hint=(1, 0, 0), phase=45)
    f += block("spine", (7.5, CY - 3.6, 0), (3.6, 0.9, 1.0))
    f += block("spine", (7.5, CY + 3.6, 0), (3.6, 0.9, 1.0))
    f += d_thunder.string_and_arrow(stage, "legend", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))
    return f


def inlay_bw_b(env, kind):
    lim = np.isin(env["mats"], ["main"]) & (np.abs(env["N"][..., 2]) > 0.55)
    band = np.abs(env["Y"] - CY) > 4.4
    if kind == "legend":
        return lim & band & (np.abs(env["Y"] - CY) < 6.2) & (np.abs((env["Y"] * 1.6) % 2.4) < 0.5)
    if kind == "p5":
        return lim & band & (np.abs((env["Y"] * 1.6) % 2.4) < 0.6)
    if kind == "p10":
        return lim & band
    return off(env)


def bw_c(stage=0):
    """C 避雷針: 上の腕の先が長い銅の針になって突き出す (下は短く光る石)。上下非対称。"""
    f = bow_grip()
    pull = TIP_PULL[stage]
    up = [(5.0, 10.6, 0), (4.6, 12.6, 0), (3.2, 15.0, 0), (1.0 - pull * 0.5, 17.0, 0), (-1.6 - pull, 18.4, 0)]
    lo = mirror_y(up)
    lo = [(x, y + (0.7 if i >= 2 else 0), z) for i, (x, y, z) in enumerate(lo)]
    for path in (up, lo):
        sg = 1 if path is up else -1
        f += curve("main", path, [0.9, 0.85, 0.75, 0.6, 0.45], [1.1, 1.0, 0.9, 0.7, 0.5], sides=4, caps=(False, False), hint=(0, 0, 1))
        bx, by, _ = path[1]
        f += block("spine", (bx + 0.1, by, 0), (2.2, 1.0, 2.0), taper=(0.9, 0.9))
    ux, uy, _ = up[-1]
    lx, ly, _ = lo[-1]
    # 上: 長い針 (銅)、中ほどに返しが 2 つ
    f += block("acc", (ux, uy + 0.3, 0), (1.5, 1.4, 1.7), taper=(0.7, 0.7))
    f += spike(BONE, "acc", (ux, uy + 0.2, 0), (0.2, 1, 0), 6.0, 0.7, sides=4, rings=3, hint=(0, 0, 1))
    for k, h in enumerate((1.6, 3.4)):
        f += spike(BONE, "spine", (ux + 0.1, uy + 0.2 + h, 0), (1.0, 0.7, 0), 1.9 - k * 0.3, 0.45, sides=4, rings=1, hint=(0, 0, 1))
    # 下: 短く、先に光る石
    f += block("acc", (lx, ly - 0.3, 0), (1.5, 1.4, 1.7), taper=(0.7, 0.7))
    f += block("gem", (lx, ly - 1.4, 0), (1.0, 1.2, 1.2), taper=(0.7, 0.7))
    f += d_thunder.string_and_arrow(stage, "legend", ((ux, uy), (lx, ly)))
    return f


def inlay_bw_c(env, kind):
    lim = np.isin(env["mats"], ["main"]) & (np.abs(env["N"][..., 2]) > 0.55)
    band = np.abs(env["Y"] - CY) > 3.6
    if kind == "legend":
        return lim & band & (np.abs(env["Y"] - CY) < 6.4) & (np.abs((env["Y"] * 2.0) % 3.0) < 0.5)
    if kind == "p5":
        return lim & band & (np.abs((env["Y"] * 2.0) % 3.0) < 0.6)
    if kind == "p10":
        return lim & band
    return off(env)


# ================================================================== 灰喰いの鎌 (余燼。玄武岩と熾)
def scythe_haft(top=13.0, ash=True):
    f = []
    f += prism("grip", (0, -2.4, 0), (0, 0.2, 0), 1.0, 1.0, sides=6, caps=(False, False))
    f += prism("wrap", (0, 0.2, 0), (0, 4.8, 0), 1.15, 1.15, sides=6, caps=(False, False))
    f += prism("grip", (0, 4.8, 0), (0, top, 0), 0.95, 0.95, sides=6, caps=(False, True))
    f += block("trim", (0, 5.2, 0), (2.5, 0.6, 2.5))
    f += block("trim", (0, -0.1, 0), (2.4, 0.6, 2.4))
    f += block("spine", (0, -2.7, 0), (2.4, 1.2, 2.4), taper=(0.7, 0.7), skip=("u",))
    if ash:   # 灰色の布の垂れ
        f += at(extrude("wrap", [(0, 0), (0.9, -0.4), (1.3, -4.6), (0.2, -3.8), (-0.6, -5.0), (-0.9, -0.4)], 0.5), None, (1.0, 5.0, 0.3))
    return f


def crescent(pts, widths, thick, mat="main", mats=None):
    return curve(mat, pts, [thick] * len(pts), widths, sides=4, caps=(False, False), phase=45, hint=(0, 0, 1)) if mats is None else \
        tube(BONE, mat, [V(*p) for p in pts], [thick] * len(pts), widths, sides=4, hint=V(0, 0, 1), phase=45, caps=(False, False), mats=mats)


def sc_a(stage=0):
    """A 大鎌: 長い柄の先から -x へ大きく弧を描く虚の刃。刃の内側の縁だけが熾で薄く光る。付け根に玄武岩の襟。"""
    f = scythe_haft(top=14.2)
    pts = [(0.4, 14.8, 0), (-2.8, 17.6, 0), (-7.0, 18.6, 0), (-11.0, 16.4, 0), (-13.2, 12.2, 0), (-13.6, 10.0, 0)]
    wd = [1.0, 1.7, 1.9, 1.5, 0.9, 0.0]
    f += crescent(pts, wd, 0.8, mats=["edge", "main", "edge", "main"])
    # 玄武岩の襟と背の棘
    f += block("guard", (0, 14.2, 0), (3.2, 2.4, 2.8), taper=(0.9, 0.9))
    f += block("trim", (0, 15.6, 0), (2.8, 0.6, 2.4))
    f += block("acc", (0.9, 16.6, 0), (2.4, 3.0, 2.2), taper=(0.7, 0.8))
    f += spike(BONE, "spine", (1.3, 17.2, 0), (0.7, 1, 0), 3.2, 0.9, sides=4, rings=2, hint=(0, 0, 1))
    f += block("gem", (-1.6, 15.4, 0), (1.0, 1.0, 2.0), taper=(0.8, 0.8))
    return f


def inlay_sc_a(env, kind):
    onblade = np.isin(env["mats"], ["main"]) & (env["X"] < -2.0) & (np.abs(env["N"][..., 2]) > 0.5)
    ang = np.arctan2(env["Y"] - 11.0, env["X"] + 0.5)
    if kind == "legend":
        return onblade & (np.abs((ang * 6.0) % 2.0) < 0.3) & (env["X"] < -6.5) & (env["Y"] > 14.0)
    if kind == "p5":
        return onblade & (np.abs((ang * 6.0) % 2.0) < 0.45)
    if kind == "p10":
        return onblade & (np.abs((ang * 6.0) % 1.5) < 0.6)
    return off(env)


def sc_b(stage=0):
    """B 草刈り鎌: 短い柄、幅の広い鉤の刃。内側に細かい歯が並び、柄尻に灰の布が垂れる。"""
    f = scythe_haft(top=9.4, ash=True)
    # 柄の先の継ぎ手
    f += block("guard", (0, 9.6, 0), (3.0, 2.0, 2.8), taper=(0.9, 0.9))
    f += block("trim", (0, 10.8, 0), (2.6, 0.6, 2.4))
    pts = [(0.2, 11.6, 0), (-2.4, 13.8, 0), (-5.8, 14.2, 0), (-8.6, 12.0, 0), (-9.6, 8.8, 0), (-8.6, 6.4, 0)]
    wd = [1.4, 2.3, 2.5, 2.1, 1.5, 0.0]
    f += crescent(pts, wd, 0.9, mats=["edge", "main", "edge", "main"])
    # 内側の歯 (熾の石の小さな棘)
    for t in (0.45, 0.95, 1.45, 1.95):
        pass
    for (px, py), (dx, dy) in (((-5.6, 12.0), (0.0, -1.0)), ((-7.2, 10.8), (0.6, -0.9)), ((-7.8, 9.0), (0.9, -0.5)), ((-7.3, 7.6), (0.9, -0.2))):
        f += spike(BONE, "acc", (px, py, 0), (dx, dy, 0), 1.7, 0.55, sides=4, rings=1, hint=(0, 0, 1))
    f += block("gem", (-1.0, 11.4, 0), (1.0, 1.0, 2.0), taper=(0.8, 0.8))
    f += block("acc", (1.0, 12.4, 0), (1.8, 2.6, 2.0), taper=(0.7, 0.8))
    return f


def inlay_sc_b(env, kind):
    onblade = np.isin(env["mats"], ["main"]) & (env["X"] < -2.0) & (np.abs(env["N"][..., 2]) > 0.5)
    ang = np.arctan2(env["Y"] - 9.5, env["X"] + 3.0)
    if kind == "legend":
        return onblade & (np.abs((ang * 5.0) % 2.0) < 0.3) & (env["X"] < -4.5)
    if kind == "p5":
        return onblade & (np.abs((ang * 5.0) % 2.0) < 0.45)
    if kind == "p10":
        return onblade & (np.abs((ang * 5.0) % 1.5) < 0.6)
    return off(env)


def sc_c(stage=0):
    """C 顎の鎌: 大小二枚の三日月刃が口のように向かい合う。喉の奥で熾の核が光り、玄武岩の牙が噛み合う。"""
    f = scythe_haft(top=14.0)
    f += block("guard", (0, 14.0, 0), (3.4, 2.6, 3.0), taper=(0.9, 0.9))
    f += block("trim", (0, 15.4, 0), (3.0, 0.6, 2.6))
    # 上顎 (大): 上へ反って -x へ
    up = [(0.4, 15.4, 0), (-3.0, 18.0, 0), (-7.4, 18.4, 0), (-10.8, 15.6, 0), (-11.6, 12.6, 0)]
    f += crescent(up, [1.1, 1.7, 1.7, 1.2, 0.0], 0.8, mats=["edge", "main", "edge", "main"])
    # 下顎 (小): 下へ反って -x へ
    lo = [(0.4, 12.6, 0), (-2.6, 10.4, 0), (-6.4, 9.8, 0), (-9.0, 11.2, 0)]
    f += crescent(lo, [1.0, 1.4, 1.2, 0.0], 0.8, mats=["edge", "main", "edge", "main"])
    # 喉の核と牙
    f += block("gem", (-2.0, 14.0, 0), (1.5, 1.8, 2.2), taper=(0.8, 0.8))
    for x, y in ((-5.0, 16.6), (-7.6, 15.8), (-9.6, 14.0)):
        f += spike(BONE, "acc", (x, y, 0), (0.1, -1, 0), 1.9, 0.6, sides=4, rings=1, hint=(0, 0, 1))
    for x, y in ((-4.0, 11.4), (-6.6, 11.4)):
        f += spike(BONE, "acc", (x, y, 0), (-0.1, 1, 0), 1.6, 0.55, sides=4, rings=1, hint=(0, 0, 1))
    f += block("acc", (0.9, 14.0, 0), (1.8, 4.8, 2.2), taper=(0.7, 0.8))
    return f


def inlay_sc_c(env, kind):
    onblade = np.isin(env["mats"], ["main"]) & (env["X"] < -2.0) & (np.abs(env["N"][..., 2]) > 0.5)
    if kind == "legend":
        return onblade & (np.abs((env["X"] * 1.4) % 3.0) < 0.4) & (env["X"] < -5.0)
    if kind == "p5":
        return onblade & (np.abs((env["X"] * 1.4) % 3.0) < 0.55)
    if kind == "p10":
        return onblade & (np.abs((env["X"] * 1.4) % 2.0) < 0.7)
    return off(env)


# ================================================================== 一覧
WEAPONS = {
    "abyss_greatsword": dict(
        jp="底なしの大剣", titan="whale", grip=(0.0, 1.8, 0.0), cen=(0.0, 9.0, 0.0), scale=11.0, stages=1,
        opts=dict(
            a=(gs_a, inlay_gs_a, ("edge",), "裂け刃。刃が縦に割れ、割れ目の底に虚の光。鍔は下へ垂れる鯨の肋、背に風化した骨の歯。技「奈落断ち」(もう一度裂ける) をそのまま形にした案。"),
            b=(gs_b, inlay_gs_b, ("edge",), "断崖。幅広い一枚刃の右縁が地層のように段になって落ち、段ごとに光る筋。背に風化した肋が三本。重く、遠くから段の輪郭で見分けがつく。"),
            c=(gs_c, inlay_gs_c, ("edge",), "牙。反った一本の巨大な牙。根は風化した鯨骨の白紫、先へ向かって虚の紫黒に溶ける。鍔は二股のあご骨。いちばん細身で静か。"))),
    "abyss_bell_staff": dict(
        jp="沈み鐘の杖", titan="tide", grip=(0.0, 3.0, 0.0), cen=(0.0, 8.5, 0.0), scale=10.0, stages=1,
        opts=dict(
            a=(st_a, inlay_st_a, (), "吊り鐘。杖の先が鉤に曲がり、風化した鐘が一つ吊るされる。鐘の下に真珠の舌。ひとつの輪郭で「鐘の杖」と読める。"),
            b=(st_b, inlay_st_b, (), "真珠の杯。大きく開いた鐘が口を上にして先に載り、真珠を抱く。外側に風化した貝の鰭。左右対称で祭具らしい。"),
            c=(st_c, inlay_st_c, (), "二つ鐘。杖の先が横木になり、左右に小さな鐘が垂れる。真ん中に真珠の尖り。風鈴のような軽さ。"))),
    "abyss_storm_bow": dict(
        jp="雷淵の弓", titan="thunder", grip=(5.0, 8.0, 0.0), cen=(2.0, 8.0, 0.0), scale=12.0, stages=4,
        opts=dict(
            a=(bw_a, inlay_bw_a, (), "稲妻の腕。弓の腕が稲妻のように折れ、折れ目に風化した銅の継ぎ手。先に銅の爪。いちばん「雷」が輪郭に出る。"),
            b=(bw_b, inlay_bw_b, (), "雷環。なめらかに反った虚の腕、握りの前に銅の環が二つ並び、矢が環をくぐる。静かで、引くと環が際立つ。"),
            c=(bw_c, inlay_bw_c, (), "避雷針。上の腕の先が長い銅の針になって突き出し (返し付き)、下は短く光る石。上下非対称で、空へ向ける弓。"))),
    "abyss_ash_scythe": dict(
        jp="灰喰いの鎌", titan="ember", grip=(0.0, 3.0, 0.0), cen=(-4.0, 8.0, 0.0), scale=9.5, stages=1,
        opts=dict(
            a=(sc_a, inlay_sc_a, ("edge",), "大鎌。長い柄の先から大きく弧を描く虚の刃。内側の縁だけが熾で薄く光る。玄武岩の襟と背の棘。いちばん古典的で大きい。"),
            b=(sc_b, inlay_sc_b, ("edge",), "草刈り鎌。短い柄、幅広の鉤刃。内側に細かい歯が並び、灰の布が垂れる。小ぶりで素早そう (速さ 1.2 に合う)。"),
            c=(sc_c, inlay_sc_c, ("edge",), "顎の鎌。大小二枚の三日月刃が口のように向かい合い、喉で熾の核が光る。「灰を喰う」を形にした案。"))),
}
