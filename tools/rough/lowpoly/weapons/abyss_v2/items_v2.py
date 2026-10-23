"""五章「深淵」の装備 6 点の作り直し (v2)。選ばれた A 案 (docs/review/roughs_gear_gaps/<id>/opt_a_*) の形を、
基準の鯨骨の大剣 (D2: 面取り・柄と鍔の輪・宝石・光る線) より細かく作る。調べたこと: docs/review/abyss_gear_v2/README.md。

  灯り番の剣  (rare,   灯りの琥珀)  A 吊り灯: 片手の直剣。樋のある菱形の刃・刃元 (リカッソ) と鍔の舌・八角の鍔の腕と玉の先、
              右の腕の下の環から鎖で角灯 (真鍮の枠・硝子・屋根・頂の環)。柄は革の巻きと縫い目、真鍮の口金、八角の柄頭に硝子の小窓
  沈め錨      (legend, 淵の紫)    A 古い錨: 胴 (八角の錨鉄、帯と鋲)・頭 (冠) の両面に紫の核、反り下がる二本の腕と鉤の爪 (面取りの板)、
              横木 (両端に玉)、胴に巻いた鎖、柄尻の錨の環と枷、頭と腕の付着物 (藤壺)
  鼓動の短剣  (legend, 鼓動の赤)  A 肋の刃: 反った骨の刃 (研いだ縁は紫鋼)・刃の背の鋸歯・赤い脈の筋、鍔は錨鉄の横木と前へ抱く二本の肋、
              鍔の両面に心臓の核 (二つの丸と尖り)、腱の巻きの柄、柄頭は骨の環と赤い玉
  鼓動の聴き筒 (rare,  鼓動の赤)  A 聴き喇叭: 真鍮の喇叭 (巻いた口の縁と暗い喉)、細い管が赤い核のまわりを一巻き、耳当ての先、吊り環
  灯籠の護符  (rare,   灯りの琥珀)  A 小さな灯籠: 六角の灯籠 (台・硝子・柱・反った屋根の軒先・宝珠と吊り環)、下に錨鉄の重り
  測量士の錘  (legend, 淵の紫)    A 下げ振り: 涙の形の錨鉄の錘 (硬い鋼の先)・紫に光る目盛りの帯と縦の刻み、真鍮の頭 (刻みの輪)・環・測り紐・紐巻き

座標は他の武器と同じ。武器: +Y が先、握りは GRIP、広い面は ±Z。装飾品: 原点まわり (y -8..9)、正面 -Z。
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import numpy as np  # noqa: E402
from kit_v2 import (chamfered, ell, band, disc, rivet, link, chain_path, torus, lathe, slab, lantern, edge_of,  # noqa: E402
                    block, blade, prism, curve, extrude, V, rot_x, rot_y, rot_z, nrm, BONE, spike)
from paint_v2 import along, veins, bands_y  # noqa: E402


def off(env):
    return env["lit"] < -9


def gline(pts, r=0.24, mat="gem"):
    """光る細い線 (表面に半分沈める三角の筒)"""
    return curve(mat, pts, [r] * len(pts), [r] * len(pts), sides=3, caps=(True, True), hint=(0, 0, 1), phase=90.0)


# ====================================================================== 灯り番の剣
LS_ST = [(5.0, -1.55, 1.55, 0.64, 0.64), (9.0, -1.62, 1.62, 0.62, 0.62), (14.0, -1.45, 1.45, 0.56, 0.56),
         (18.0, -1.08, 1.08, 0.46, 0.46), (20.2, -0.5, 0.5, 0.28, 0.28), (21.6, 0, 0, 0, 0)]


@chamfered
def lamp_sword():
    f = []
    # 柄頭: 八角の玉 (下に小さな頭の鋲)、正面と背に琥珀の小窓
    f += lathe("iron", [(-3.45, 0.0), (-3.1, 0.62), (-2.85, 1.28), (-2.35, 1.62), (-1.85, 1.48), (-1.4, 0.95)], sides=8)
    f += block("glass", (0, -2.35, -1.48), (0.8, 0.8, 0.4))
    f += block("glass", (0, -2.35, 1.48), (0.8, 0.8, 0.4))
    f += band("trim", (0, -1.42, 0), 1.08, 0.42)
    # 柄: 革の巻き (少し膨らむ)、斜めの縫い目は絵、巻きの畝 4 本
    f += lathe("leather", [(-1.6, 0.8), (0.2, 0.93), (1.7, 0.95), (3.1, 0.82)], sides=8, caps=(False, False))
    for y in (-0.7, 0.35, 1.4, 2.4):
        f += band("cord", (0, y, 0), 0.99, 0.22)
    f += band("trim", (0, 2.95, 0), 1.05, 0.42)
    # 鍔: 真ん中の板 (両面に琥珀の窓と真鍮の枠)、八角の腕が少し上へ反り、先に玉
    f += block("iron", (0, 3.65, 0), (2.7, 1.7, 2.1), taper=(0.82, 0.9))
    for z in (-1.0, 1.0):
        f += block("brass", (0, 3.65, z), (1.4, 1.3, 0.3))
        f += block("glass", (0, 3.65, z * 1.12), (0.85, 0.8, 0.3))
    for s in (1, -1):
        f += curve("iron", [(s * 1.0, 3.65, 0), (s * 2.6, 3.75, 0), (s * 3.9, 4.05, 0), (s * 4.7, 4.55, 0)],
                   [0.62, 0.56, 0.5, 0.46], [0.62, 0.56, 0.5, 0.46], sides=6, caps=(False, False), hint=(0, 0, 1))
        f += ell("iron", (s * 4.85, 4.7, 0), (0.62, 0.62, 0.62), segs=6, rings=2)
        f += band("brass", (s * 2.2, 3.72, 0), 0.68, 0.32, axis="x", sides=6)
    # 刃元 (リカッソ) と舌 (鍔から刃へ伸びる板)
    f += block("main", (0, 5.0, 0), (2.6, 1.4, 1.2), taper=(1.0, 1.0))
    for z in (-0.62, 0.62):
        f += extrude("iron", [(-0.55, 4.2), (0.55, 4.2), (0.3, 5.9), (-0.3, 5.9)], 0.26, c=(0, 0, z))
        # 灯り番の刻印 (小さな灯の形の真鍮の板)
        f += extrude("brass", [(0, 5.95), (-0.42, 5.55), (-0.32, 5.05), (0.32, 5.05), (0.42, 5.55)], 0.2, c=(0, 0, z * 1.32))
    # 刃: 菱形の断面 (樋は絵)
    f += blade("main", LS_ST)
    # 右の腕の下の環と鎖、角灯
    f += torus("brass", (3.6, 3.05, 0), 0.42, 0.14, n=8, m=4, axis="x")
    f += chain_path([(3.6, 2.75, 0), (3.6, 1.35, 0)], L=1.25, W=0.8, t=0.26)
    f += lantern((3.6, -1.55, 0), w=2.1, h=2.7, roof=1.15, bar=0.4)
    return f


def ls_fuller(env):
    y = env["Y"]
    return (np.abs(env["X"]) < 0.34) & (y > 5.8) & (y < 15.5) & (np.abs(env["N"][..., 2]) > 0.5)


def ls_inlay(env, kind):
    c0 = lambda y: 0.0 * y
    if kind == "p5":
        return along(env, ["main"], c0, 6.0, 15.0, 0.22, dash=(2.2, 1.3))
    if kind == "p10":
        return along(env, ["main"], c0, 5.6, 19.4, 0.26)
    return off(env)


# ====================================================================== 沈め錨
AN_TOP = 21.4


def fluke(s):
    """鉤の爪: 腕の先に付く、下を向いた面取りの板 (s = ±1)"""
    pts = [(5.0, 17.4), (7.0, 17.9), (7.7, 16.2), (6.6, 13.4), (5.3, 15.0)]
    pts = [(s * x, y) for x, y in pts]
    if s < 0:
        pts = pts[::-1]
    return slab("main", pts, 1.05, bevel=0.28, mats=["edge"])


@chamfered
def sinking_anchor():
    f = []
    # 柄尻: 錨の環 (正面向き) と枷
    f += torus("iron", (0, -4.7, 0), 1.45, 0.42, n=10, m=4, axis="z", phase=18)
    f += block("iron", (0, -3.05, 0), (1.9, 1.5, 1.5), taper=(0.8, 0.8))
    f += prism("brass", (0, -3.05, -1.05), (0, -3.05, 1.05), 0.45, 0.45, sides=6, caps=(True, True), hint=(1, 0, 0))
    # 握り: 革の巻き、上と下に錨鉄の帯
    f += lathe("leather", [(-2.6, 1.02), (0.0, 1.1), (2.6, 1.1), (4.6, 1.02)], sides=8, caps=(False, False))
    f += band("iron", (0, -2.3, 0), 1.24, 0.6)
    f += band("trim", (0, 4.7, 0), 1.24, 0.6)
    # 胴: 八角の錨鉄が上へ少し太る
    f += lathe("iron", [(4.4, 1.0), (12.0, 1.06), (18.8, 1.2), (20.4, 1.5)], sides=8, caps=(False, False))
    for y in (8.0, 13.6):
        f += band("iron", (0, y, 0), 1.3, 0.75)
        for k in range(4):
            a = math.radians(45 + 90 * k)
            f += rivet((1.3 * math.cos(a), y, 1.3 * math.sin(a)), r=0.24, h=0.22, n=(math.cos(a), 0, math.sin(a)))
    # 胴に巻いた鎖 (二巻き半)
    pts = []
    for i in range(5):
        a = math.radians(-60 + 90 * i)
        pts.append((1.42 * math.cos(a), 9.2 + 0.75 * i, -1.42 * math.sin(a)))
    f += chain_path(pts, L=1.5, W=0.95, t=0.3)
    # 横木 (胴に直交、両端に玉と帯)
    f += prism("iron", (0, 17.4, -4.0), (0, 17.4, 4.0), 0.72, 0.72, sides=6, caps=(False, False), hint=(1, 0, 0))
    for s in (1, -1):
        f += ell("iron", (0, 17.4, s * 4.15), (0.85, 0.85, 0.85), segs=6, rings=2)
        f += band("brass", (0, 17.4, s * 2.8), 0.84, 0.4, axis="z", sides=6)
    f += block("iron", (0, 17.4, 0), (2.6, 1.6, 2.6), taper=(0.9, 0.9))
    # 冠 (頭): 両面に紫の核と縁の環、上に頂
    f += block("iron", (0, AN_TOP, 0), (3.4, 2.8, 2.7), taper=(0.84, 0.84))
    for z in (-1.38, 1.38):
        f += ell("gem", (0, AN_TOP, z), (0.95, 0.95, 0.5), segs=8, rings=2)
        f += torus("trim", (0, AN_TOP, z * 0.98), 1.12, 0.2, n=8, m=3, axis="z", phase=22.5)
    f += lathe("iron", [(AN_TOP + 1.2, 1.25), (AN_TOP + 1.9, 1.0), (AN_TOP + 2.5, 0.55), (AN_TOP + 2.9, 0.0)], sides=8, caps=(True, False))
    f += band("trim", (0, AN_TOP + 1.35, 0), 1.32, 0.4)
    # 腕: 冠から外へ反り下がる (八角の先細り)、先に鉤の爪
    for s in (1, -1):
        f += curve("main", [(s * 0.8, AN_TOP - 0.3, 0), (s * 3.0, AN_TOP - 0.9, 0), (s * 4.9, 19.4, 0), (s * 6.2, 16.6, 0)],
                   [1.3, 1.2, 1.05, 0.9], [1.2, 1.1, 0.95, 0.82], sides=6, caps=(False, True), hint=(0, 0, 1))
        f += fluke(s)
        f += band("iron", (s * 2.6, AN_TOP - 0.82, 0), 1.3, 0.55, axis="x", sides=6)
    # 付着物 (藤壺): 冠の肩と右の腕
    for (x, y, z, d) in ((1.3, AN_TOP + 1.0, -1.1, (0.3, 0.6, -1)), (-1.5, AN_TOP + 0.7, -0.9, (-0.4, 0.5, -1)),
                         (4.2, 19.9, -0.7, (0.2, 0.6, -1)), (5.0, 18.9, -0.6, (0.4, 0.3, -1)), (-4.6, 19.6, 0.8, (-0.2, 0.6, 1))):
        f += spike(BONE, "barn", (x, y, z), d, 0.75, 0.42, sides=5, rings=1, hint=(0, 1, 0))
    return f


def an_inlay(env, kind):
    if kind == "legend":
        return veins(env, ["main"], 0.022, 7) | (along(env, ["iron"], lambda y: 0.0 * y, 14.4, 16.4, 0.26, dash=(1.0, 0.5)) & (np.abs(env["N"][..., 2]) > 0.6))
    if kind == "p5":
        return veins(env, ["main"], 0.034, 7) | (along(env, ["iron"], lambda y: 0.0 * y, 5.5, 16.5, 0.3, dash=(1.6, 0.8)) & (env["N"][..., 2] < -0.6))
    if kind == "p10":
        return veins(env, ["main", "iron"], 0.03, 7, y0=4.5) | (along(env, ["iron"], lambda y: 0.0 * y, 5.0, 19.5, 0.36) & (np.abs(env["N"][..., 2]) > 0.6))
    return off(env)


# ====================================================================== 鼓動の短剣
DG_CX = lambda y: 0.03 * max(0.0, y - 3.6) ** 2
DG_YS = [3.6, 5.4, 7.2, 9.0, 10.8, 12.4, 13.9]
DG_W = [1.35, 1.48, 1.42, 1.25, 0.98, 0.6, 0.0]
DG_T = [0.62, 0.62, 0.58, 0.5, 0.4, 0.26, 0.0]


@chamfered
def pulse_dagger():
    f = []
    # 柄頭: 骨の環と赤い玉、柄との継ぎの錨鉄
    f += torus("bone", (0, -2.55, 0), 1.0, 0.3, n=10, m=4, axis="z", phase=18)
    f += ell("gem", (0, -2.55, 0), (0.72, 0.72, 0.42), segs=6, rings=2)
    f += block("iron", (0, -1.45, 0), (1.25, 0.9, 1.1), taper=(0.85, 0.85))
    # 柄: 腱の巻き、真鍮の口金
    f += lathe("wrap", [(-1.2, 0.76), (0.6, 0.86), (2.4, 0.78)], sides=8, caps=(False, False))
    for y in (-0.5, 0.45, 1.4):
        f += band("leather", (0, y, 0), 0.9, 0.26)
    f += band("brass", (0, 2.35, 0), 0.92, 0.36)
    # 鍔: 錨鉄の横木 + 前へ抱く二本の肋
    f += block("iron", (0, 2.95, 0), (3.4, 0.95, 1.6), taper=(0.78, 0.9))
    for s in (1, -1):
        f += curve("bone", [(s * 1.3, 2.95, 0), (s * 2.3, 3.3, 0), (s * 2.55, 4.5, 0), (s * 2.0, 5.6, 0)],
                   [0.42, 0.38, 0.3, 0.12], [0.42, 0.38, 0.3, 0.12], sides=6, caps=(False, True), hint=(0, 0, 1))
        f += ell("bone", (s * 1.7, 2.95, 0), (0.48, 0.5, 0.5), segs=6, rings=2)
    # 心臓の核 (両面): 二つの丸と下の尖り
    for z in (-0.78, 0.78):
        sz = 1 if z > 0 else -1
        for s in (1, -1):
            f += ell("gem", (s * 0.42, 3.55, z), (0.55, 0.55, 0.4), segs=6, rings=2)
        f += spike(BONE, "gem", (0, 3.4, z), (0, -1, 0), 0.95, 0.5, sides=6, rings=1, hint=(0, 0, 1))
        # 脈の管: 核から柄へ
        for s in (1, -1):
            f += curve("wrap", [(s * 0.55, 3.2, z * 0.9), (s * 0.7, 2.6, z * 1.05), (s * 0.62, 1.9, sz * 0.86)], [0.16] * 3, [0.16] * 3,
                       sides=4, caps=(True, True), hint=(0, 0, 1))
    # 刃: 反った骨 (研いだ縁は紫鋼)
    st = [(y, -w, w, t, t, DG_CX(y)) for y, w, t in zip(DG_YS, DG_W, DG_T)]
    f += blade("bone", st, colmats=["edge", "bone", "bone", "bone", "bone", "edge"])
    # 刃の背 (右、内側の反り) の鋸歯
    for y in (5.2, 6.6, 8.0, 9.4):
        xl, xr, tf, cx = edge_of(st, y)
        f += spike(BONE, "bone", (xr + cx - 0.15, y, 0), (1.0, 0.75, 0), 0.95, 0.32, sides=4, rings=1, hint=(0, 0, 1))
    # 脈の筋 (両面、伝説はいつも光る)
    for sz in (-1, 1):
        pts = []
        for y in (4.2, 6.4, 8.6, 10.6):
            xl, xr, tf, cx = edge_of(st, y)
            pts.append((cx, y, sz * (tf - 0.04)))
        f += gline(pts, r=0.2)
    return f


def dg_inlay(env, kind):
    if kind == "legend":
        return off(env)
    if kind == "p5":
        return along(env, ["bone"], DG_CX_V, 4.0, 11.5, 0.5, dash=(1.4, 0.8)) & (env["D"] > 0.6)
    if kind == "p10":
        return veins(env, ["bone"], 0.03, 11, y0=3.6) | (along(env, ["bone"], DG_CX_V, 3.8, 12.6, 0.55) & (env["D"] > 0.5))
    return off(env)


def DG_CX_V(y):
    return 0.03 * np.maximum(0.0, y - 3.6) ** 2


# ====================================================================== 鼓動の聴き筒 (装飾)
@chamfered
def pulse_listener():
    f = []
    R = rot_x(-38) @ rot_z(-34)   # 口を少し前 (-Z) へ向ける (品の絵と胸の上で口が見える)
    o = V(0.9, -0.6, 0)

    def P(x, y, z=0.0):
        return tuple(R @ V(x, y, z) + o)

    # 喇叭 (y 0..6.7 を回して斜めに)。口は暗い喉で塞ぎ、巻いた縁 (階級の色)
    horn = lathe("brass", [(-0.4, 0.66), (1.6, 0.74), (3.2, 1.02), (4.6, 1.62), (5.6, 2.5), (6.1, 3.1)], sides=10, caps=(False, "dark"))
    horn += lathe("trim", [(5.9, 3.18), (6.35, 3.42), (6.75, 3.15)], sides=10, caps=(False, "dark"))
    horn += band("iron", (0, 1.3, 0), 0.86, 0.5, sides=10)
    horn += band("iron", (0, 3.3, 0), 1.16, 0.45, sides=10)
    for fc in horn:
        fc.verts = fc.verts @ R.T + o
        fc.ref = R @ fc.ref + o
        fc.normal = R @ fc.normal
        fc.loc = fc.verts.copy()
    f += horn
    # 赤い核 (鼓動) を管が一巻き。核は錨鉄の帯 3 本で押さえる
    cc = V(-1.4, -3.4, 0)
    path = [P(0, 0.0), P(0, -0.9)]
    for i in range(8):
        a = math.radians(40 + 42 * i)
        path.append(tuple(cc + V(1.75 * math.cos(a), 1.75 * math.sin(a), 0.16 * i - 0.6)))
    end = cc + V(0.4, -2.7, 0.7)
    path.append(tuple(end))
    f += curve("brass", path, [0.56] * len(path), [0.56] * len(path), sides=6, caps=(False, True), hint=(0, 0, 1))
    f += band("trim", path[1], 0.7, 0.4, sides=8)
    # 耳当ての先 (骨の玉)
    f += ell("bone", tuple(end + V(0.25, -0.45, 0.05)), (0.82, 0.7, 0.7), segs=6, rings=2)
    f += ell("gem", tuple(cc), (1.3, 1.3, 1.05), segs=8, rings=3)
    for a in (90, 210, 330):
        r_ = math.radians(a)
        f += block("iron", tuple(cc + V(1.2 * math.cos(r_), 1.2 * math.sin(r_), -0.4)), (0.42, 1.6, 0.42), rot=rot_z(a - 90))
    # 吊り環 (身につけるときの留め。喇叭の首の帯から)
    f += torus("brass", tuple(R @ V(1.15, 3.3, 0) + o), 0.48, 0.15, n=8, m=4, axis="z")
    return f


# ====================================================================== 灯籠の護符
@chamfered
def lantern_charm():
    f = []
    f += lantern((0, 0.2, 0), w=3.9, h=4.6, roof=2.3, sides=6, bar=0.55, finial=False, base_h=0.9)
    # 軒先の反り (屋根の角から上へ)
    r = 3.9 / 2 / math.cos(math.pi / 6) * 1.18
    for k in range(6):
        a = math.radians(60 * k)
        f += spike(BONE, "brass", (r * 0.92 * math.cos(a), 2.85, r * 0.92 * math.sin(a)), (math.cos(a), 0.9, math.sin(a)), 0.9, 0.3,
                   sides=4, rings=1, hint=(0, 1, 0))
    # 宝珠と吊り環
    f += lathe("brass", [(4.7, 0.6), (5.1, 0.78), (5.6, 0.5), (5.95, 0.0)], sides=6, caps=(True, False))
    f += torus("trim", (0, 6.55, 0), 0.62, 0.18, n=8, m=4, axis="z")
    # 下の錨鉄の重り (尖り) と継ぎの輪
    f += band("trim", (0, -2.95, 0), 1.2, 0.36, sides=6, phase=0)
    f += lathe("iron", [(-2.9, 1.1), (-3.6, 1.3), (-4.6, 0.95), (-5.9, 0.0)], sides=6, phase=0, caps=(True, False))
    return f


# ====================================================================== 測量士の錘
@chamfered
def surveyor_plumb():
    f = []
    f += lathe("edge", [(-7.2, 0.0), (-6.3, 0.42)], sides=10, caps=(False, False))
    f += lathe("iron", [(-6.3, 0.42), (-5.0, 0.95), (-3.2, 1.9), (-1.4, 2.6), (0.0, 2.72), (1.0, 2.45), (1.7, 1.85)], sides=10, caps=(False, True))
    for y, r in ((-3.2, 1.95), (-1.4, 2.65)):
        f += band("gem", (0, y, 0), r, 0.3, sides=10, phase=18)
    # 真鍮の頭: 刻みの輪と細い首、紐を通す環
    f += lathe("brass", [(1.55, 1.95), (2.05, 2.0), (2.3, 1.45), (2.95, 1.25), (3.2, 0.62)], sides=10, caps=(False, True))
    f += band("trim", (0, 2.55, 0), 1.42, 0.34, sides=10, phase=18)
    f += torus("brass", (0, 3.9, 0), 0.72, 0.22, n=8, m=4, axis="x")
    # 測り紐 (結び目 2 つ) と紐巻きの横木
    f += curve("cord", [(0, 4.3, 0), (0.25, 5.6, 0), (-0.15, 7.0, 0), (0, 8.3, 0)], [0.24] * 4, [0.24] * 4, sides=4, caps=(False, False))
    f += ell("cord", (0.12, 5.6, 0), (0.4, 0.4, 0.4), segs=6, rings=2)
    f += ell("cord", (-0.08, 7.0, 0), (0.4, 0.4, 0.4), segs=6, rings=2)
    f += block("brass", (0, 8.6, 0), (3.2, 0.8, 0.8))
    for s in (1, -1):
        f += block("iron", (s * 1.75, 8.6, 0), (0.5, 1.2, 1.1))
    return f


def pl_inlay(env, kind):
    if kind == "legend":
        # 縦の刻み (錘の腹、正面と背)
        e = env
        ok = np.isin(e["mats"], ["iron"]) & (e["Y"] > -5.4) & (e["Y"] < 0.6)
        ang = np.degrees(np.arctan2(e["Z"], e["X"]))
        return ok & (np.abs(((ang + 18) % 72) - 36) < 3.0) & (e["D"] > 0.5)
    return off(env)


# ====================================================================== 一覧
# kind: weapon / acc。tier は品の階級。glow は光の色。pose = 模型の持ち方の鍵 (mock.POSES)
ITEMS = {
    "abyss_lamp_sword": dict(jp="灯り番の剣", kind="weapon", tier="rare", glow="lamp", fn=lamp_sword, inlay=ls_inlay, fuller=ls_fuller,
                             edge=("edge",), grip=(0.0, 1.0, 0.0), cen=(0.0, 8.5, 0.0), scale=11.0, pose="whale"),
    "abyss_sinking_anchor": dict(jp="沈め錨", kind="weapon", tier="legend", glow="void", fn=sinking_anchor, inlay=an_inlay, fuller=None,
                                 edge=("edge",), grip=(0.0, 1.5, 0.0), cen=(0.0, 9.5, 0.0), scale=8.2, pose="ember"),
    "abyss_pulse_dagger": dict(jp="鼓動の短剣", kind="weapon", tier="legend", glow="pulse", fn=pulse_dagger, inlay=dg_inlay, fuller=None,
                               edge=("edge",), grip=(0.0, 0.8, 0.0), cen=(0.0, 5.6, 0.0), scale=16.0, pose="fangw"),
    "abyss_pulse_listener": dict(jp="鼓動の聴き筒", kind="acc", tier="rare", glow="pulse", fn=pulse_listener, inlay=None, fuller=None),
    "abyss_lantern_charm": dict(jp="灯籠の護符", kind="acc", tier="rare", glow="lamp", fn=lantern_charm, inlay=None, fuller=None),
    "abyss_surveyor_plumb": dict(jp="測量士の錘", kind="acc", tier="legend", glow="void", fn=surveyor_plumb, inlay=pl_inlay, fuller=None),
}
