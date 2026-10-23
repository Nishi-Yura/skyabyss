"""五章「深淵」の装備 (docs/design/03 の 12 章) の手の武器 3 点 x 3 案の形。
深淵の紫黒の土台 (main / edge / grip)、錨鉄 (spine / acc / guard。冷たい青灰の鉄)、縁 (trim。階級の色)、光る核 (gem)。
光は品ごと: 灯り番の剣 = 灯りの琥珀 (lamp)、沈め錨 = 淵の紫 (void)、鼓動の短剣 = 鼓動の赤 (pulse)。
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
from wkit import *  # noqa: F401,F403,E402
from wkit import block, blade, prism, curve, extrude, V, rot_x, rot_y, rot_z  # noqa: E402
from paint import line_mask  # noqa: E402

import numpy as np  # noqa: E402
from lpkit import ellipsoid, spike  # noqa: E402


def off(env):
    return env["lit"] < -9


def ell(mat, c, r, segs=6, rings=2, rot=None):
    return ellipsoid(BONE, mat, c, r, segs=segs, rings=rings, rot=rot)


def cage(c, w, h, d, bar=0.45, mat="acc"):
    """小さな角灯の籠: 上下の板と 4 本の柱。中に光る核 (gem) を置く。"""
    x, y, z = c
    f = []
    f += block(mat, (x, y + h / 2, z), (w + 0.6, 0.7, d + 0.6))
    f += block(mat, (x, y - h / 2, z), (w + 0.6, 0.7, d + 0.6))
    for sx in (-1, 1):
        for sz in (-1, 1):
            f += block(mat, (x + sx * w / 2, y, z + sz * d / 2), (bar, h, bar))
    f += block("gem", (x, y, z), (w * 0.75, h * 0.8, d * 0.75), taper=(0.8, 0.8))
    f += block(mat, (x, y + h / 2 + 0.7, z), (0.6, 0.9, 0.6))
    return f


# ================================================================== 灯り番の剣 (レア。片手の直刃、灯りの琥珀)
def sword_hilt(pommel="disc"):
    f = []
    f += prism("grip", (0, -1.6, 0), (0, 0.2, 0), 0.95, 0.85, sides=6, caps=(False, False))
    f += prism("wrap", (0, 0.2, 0), (0, 2.0, 0), 1.0, 0.9, sides=6, caps=(False, False))
    f += prism("grip", (0, 2.0, 0), (0, 3.4, 0), 0.95, 0.85, sides=6, caps=(False, False))
    if pommel == "disc":
        f += prism("spine", (0, -2.8, 0), (0, -1.4, 0), 1.5, 1.2, sides=6, caps=(True, False), phase=30)
    f += block("trim", (0, -1.45, 0), (2.0, 0.5, 1.8))
    return f


def straight_blade(y0=4.2, y1=20.0, w=1.7, t=0.8):
    st = [(y0, -w, w, t, t), (y0 + 5.0, -w * 1.02, w * 1.02, t, t), (y1 - 4.5, -w * 0.94, w * 0.94, t * 0.95, t * 0.95),
          (y1 - 1.6, -w * 0.7, w * 0.7, t * 0.75, t * 0.75), (y1, 0, 0, 0, 0)]
    return blade("main", st)


def ls_a(stage=0):
    """A 吊り灯: 真っ直ぐな刃と十字の鍔。鍔の右の腕から小さな角灯が鎖で垂れる (灯り番の目印)。"""
    f = sword_hilt()
    f += straight_blade()
    f += block("guard", (0, 3.9, 0), (7.6, 1.2, 2.2), taper=(0.85, 0.9))
    f += block("trim", (0, 4.6, 0), (3.4, 0.6, 2.0))
    for s in (1, -1):
        f += block("acc", (s * 4.1, 3.9, 0), (1.2, 1.8, 1.8), taper=(0.7, 0.7))
    # 鎖の 2 環と、垂れた角灯 (右の腕の下)
    f += block("acc", (3.9, 2.6, 0), (0.6, 1.4, 0.6))
    f += block("acc", (3.9, 1.5, 0), (0.6, 1.2, 0.6))
    f += cage((3.9, -0.6, 0), 1.6, 2.4, 1.6)
    return f


def inlay_ls_a(env, kind):
    if kind == "legend":
        return off(env)
    if kind == "p5":
        return line_mask(env, ["main"], lambda y: 0.0 * y, 6.0, 14.0, 0.35, dash=(2.6, 1.6))
    if kind == "p10":
        return line_mask(env, ["main"], lambda y: 0.0 * y, 5.0, 18.5, 0.4)
    return off(env)


def ls_b(stage=0):
    """B 灯の柄頭: 柄頭そのものが角灯 (籠の中で琥珀が灯る)。鍔は輪。刃の根に琥珀の小窓。"""
    f = sword_hilt(pommel="none")
    f += cage((0, -3.2, 0), 1.9, 2.6, 1.9)
    f += straight_blade(w=1.6)
    # 輪の鍔 (正面を向く 8 角の輪を 4 つの箱で)
    for a in range(0, 360, 45):
        r = math.radians(a)
        f += block("guard", (2.6 * math.cos(r), 4.2 + 1.4 * math.sin(r) * 0.0, 2.6 * math.sin(r) * 0.0), (1.4, 1.0, 1.9))
    f += block("guard", (0, 4.2, 0), (6.4, 1.0, 1.9), taper=(0.9, 0.9))
    f += block("trim", (0, 4.9, 0), (3.0, 0.5, 1.7))
    f += block("gem", (0, 6.3, 0), (1.1, 1.6, 1.9), taper=(0.8, 0.8))
    return f


def inlay_ls_b(env, kind):
    return inlay_ls_a(env, kind)


def ls_c(stage=0):
    """C 灯窓: 幅広の刃の根に、縦長の灯りの窓 (琥珀) が通る。鍔は錨鉄の円盤、刃の背に小さな角。"""
    f = sword_hilt()
    st = [(4.2, -2.2, 2.2, 0.9, 0.9), (8.5, -2.1, 2.1, 0.9, 0.9), (15.0, -1.7, 1.7, 0.8, 0.8), (18.4, -1.0, 1.0, 0.6, 0.6), (20.4, 0, 0, 0, 0)]
    f += blade("main", st)
    f += block("gem", (0, 7.0, 0), (1.0, 4.4, 2.0), taper=(0.7, 0.7))
    f += prism("guard", (0, 3.4, -1.1), (0, 3.4, 1.1), 2.6, 2.6, sides=8, caps=(True, True), hint=(1, 0, 0))
    f += block("trim", (0, 4.3, 0), (2.8, 0.6, 1.8))
    f += spike(BONE, "acc", (-2.0, 11.0, 0), (-1, 0.6, 0), 1.6, 0.6, sides=4, rings=1, hint=(0, 0, 1))
    return f


def inlay_ls_c(env, kind):
    return inlay_ls_a(env, kind)


# ================================================================== 沈め錨 (伝説。両手の錨、淵の紫)
def anchor_haft(top=19.0):
    f = []
    f += prism("grip", (0, -3.0, 0), (0, 4.0, 0), 1.05, 0.95, sides=6, caps=(True, False))
    f += prism("wrap", (0, 0.0, 0), (0, 3.2, 0), 1.15, 1.05, sides=6, caps=(False, False))
    f += prism("spine", (0, 3.8, 0), (0, top, 0), 1.1, 0.95, sides=6, caps=(False, False))
    # 柄尻の錨の環
    f += prism("trim", (0, -4.2, -0.5), (0, -4.2, 0.5), 1.7, 1.7, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += block("acc", (0, -3.3, 0), (1.4, 1.2, 1.4))
    return f


def fluke(s, y, reach, drop, w=1.5, t=1.3):
    """錨の腕: 頭の根から外へ反り下がり、先に三角の爪。s = ±1 (左右)"""
    f = curve("acc", [(s * 0.6, y, 0), (s * reach * 0.55, y - drop * 0.25, 0), (s * reach * 0.9, y - drop * 0.7, 0), (s * reach, y - drop, 0)],
              [w, w * 0.95, w * 0.85, w * 0.7], [t, t, t * 0.9, t * 0.8], sides=4, caps=(False, True))
    f += extrude("edge", [(0, 1.6), (-1.5, -1.0), (1.7, -0.4)], 1.2, c=(s * reach, y - drop + 0.6, 0), rot=rot_z(s * -25))
    return f


def an_a(stage=0):
    """A 古い錨: 真っ直ぐな胴の先に左右へ反った二本の腕と爪。腕の付け根に紫の核、胴に鎖が二巻き。"""
    f = anchor_haft(top=22.0)
    f += block("guard", (0, 21.6, 0), (3.0, 2.4, 2.6), taper=(0.8, 0.8))
    f += fluke(1, 21.4, 6.2, 5.4)
    f += fluke(-1, 21.4, 6.2, 5.4)
    f += block("gem", (0, 21.4, -1.1), (1.4, 1.4, 1.0))
    f += block("trim", (0, 23.2, 0), (2.0, 0.8, 2.0))
    # 錨の横木 (胴の上の方に直交)
    f += block("acc", (0, 18.0, 0), (1.2, 1.2, 7.0), taper=(1.0, 1.0))
    for y in (8.5, 10.0):
        f += prism("cord", (0, y, 0), (0, y + 0.7, 0), 1.6, 1.6, sides=6, caps=(True, True))
    return f


def inlay_an(env, kind):
    on = np.isin(env["mats"], ["spine"]) & (np.abs(env["N"][..., 2]) > 0.4)
    if kind == "legend":
        return on & (np.abs((env["Y"] * 0.5) % 2.0) < 0.25) & (env["Y"] > 12)
    if kind == "p5":
        return on & (np.abs((env["Y"] * 0.5) % 2.0) < 0.3) & (env["Y"] > 6)
    if kind == "p10":
        return on & (np.abs((env["Y"] * 0.5) % 1.5) < 0.45)
    return off(env)


def an_b(stage=0):
    """B 四つ爪の錨 (錨鉤): 胴の先から四方へ鉤の爪。輪郭が十字に開く。鉤の根に紫の環。"""
    f = anchor_haft(top=21.0)
    f += block("guard", (0, 21.0, 0), (2.6, 2.6, 2.6), taper=(0.8, 0.8))
    for k in range(4):
        R = rot_y(45 + 90 * k)
        pts = [R @ V(0.6, 21.2, 0), R @ V(3.0, 22.6, 0), R @ V(4.8, 21.4, 0), R @ V(5.2, 19.2, 0)]
        f += curve("acc", [tuple(p) for p in pts], [1.0, 0.9, 0.8, 0.5], [1.0, 0.9, 0.8, 0.5], sides=4, caps=(False, True))
        tip = R @ V(5.2, 18.6, 0)
        f += spike(BONE, "edge", tuple(R @ V(5.2, 19.6, 0)), tuple(R @ V(-0.3, -1, 0)), 1.6, 0.6, sides=4, rings=1)
    f += prism("gem", (0, 19.6, 0), (0, 20.6, 0), 1.6, 1.6, sides=6, caps=(True, True))
    f += block("trim", (0, 22.6, 0), (1.8, 0.8, 1.8))
    return f


def an_c(stage=0):
    """C 欠けた錨: 片方の腕が大きく、もう片方は根で折れて紫の割れ目が光る。心臓の錨の欠けた爪という由来をそのまま形に。"""
    f = anchor_haft(top=22.0)
    f += block("guard", (0, 21.4, 0), (3.4, 2.8, 2.8), taper=(0.8, 0.8))
    f += fluke(1, 21.6, 7.4, 6.6, w=1.8, t=1.5)
    # 折れた腕 (短い付け根と、割れた面の光)
    f += curve("acc", [(-0.6, 21.4, 0), (-2.4, 20.8, 0), (-3.4, 19.8, 0)], [1.7, 1.6, 1.5], [1.4, 1.4, 1.3], sides=4, caps=(False, True))
    f += block("gem", (-3.5, 19.6, 0), (1.0, 1.6, 1.6), rot=rot_z(40))
    f += block("gem", (0, 21.4, -1.3), (1.2, 1.6, 1.0))
    f += block("trim", (0, 23.3, 0), (2.2, 0.8, 2.2))
    for y in (9.0,):
        f += prism("cord", (0, y, 0), (0, y + 0.8, 0), 1.6, 1.6, sides=6, caps=(True, True))
    return f


# ================================================================== 鼓動の短剣 (伝説。短い刃、鼓動の赤)
def dagger_hilt():
    f = []
    f += prism("grip", (0, -1.0, 0), (0, 2.6, 0), 0.85, 0.75, sides=6, caps=(False, False))
    f += prism("wrap", (0, 0.2, 0), (0, 2.2, 0), 0.92, 0.82, sides=6, caps=(False, False))
    f += block("trim", (0, -1.2, 0), (1.6, 0.5, 1.5))
    return f


def dg_a(stage=0):
    """A 肋の刃: 番人の肋のように反った刃。鍔の真ん中に心臓の形の赤い核、柄頭は小さな肋の環。"""
    f = dagger_hilt()
    cx = lambda y: 0.035 * (y - 3.4) ** 2
    ys = [3.4, 6.0, 8.6, 10.8, 12.6]
    wd = [1.4, 1.5, 1.3, 0.8, 0.0]
    th = [0.7, 0.7, 0.6, 0.45, 0.0]
    f += blade("main", [(y, -w, w, t, t, cx(y)) for y, w, t in zip(ys, wd, th)])
    f += block("guard", (0, 3.0, 0), (4.6, 1.1, 1.8), taper=(0.8, 0.9))
    f += ell("gem", (0, 3.1, -0.9), (1.0, 1.0, 0.6), segs=6, rings=1)
    for s in (1, -1):
        f += spike(BONE, "acc", (s * 2.1, 3.0, 0), (s * 0.4, -1, 0), 1.8, 0.5, sides=4, rings=1, hint=(0, 0, 1))
    f += prism("spine", (0, -2.4, -0.4), (0, -2.4, 0.4), 1.2, 1.2, sides=6, caps=(True, True), hint=(1, 0, 0))
    return f


def inlay_dg(env, kind):
    if kind == "legend":
        return line_mask(env, ["main"], lambda y: 0.0 * y, 4.5, 8.5, 0.3)
    if kind == "p5":
        return line_mask(env, ["main"], lambda y: 0.0 * y, 4.0, 10.0, 0.32, dash=(1.6, 1.0))
    if kind == "p10":
        return line_mask(env, ["main"], lambda y: 0.0 * y, 3.8, 11.5, 0.4)
    return off(env)


def dg_b(stage=0):
    """B 脈の針: 細い真っ直ぐな刺突の刃に、赤い脈の筋が通る。鍔は肋かごの 4 本の小さな骨。"""
    f = dagger_hilt()
    st = [(3.2, -1.0, 1.0, 0.8, 0.8), (7.0, -0.9, 0.9, 0.75, 0.75), (10.8, -0.6, 0.6, 0.55, 0.55), (13.2, 0, 0, 0, 0)]
    f += blade("main", st)
    f += block("gem", (0, 6.2, 0), (0.45, 5.0, 1.75), taper=(0.6, 0.9))
    for s in (1, -1):
        f += curve("acc", [(s * 0.6, 3.0, 0), (s * 2.0, 3.4, 0), (s * 2.4, 4.6, 0)], [0.5, 0.45, 0.3], [0.6, 0.5, 0.35], sides=4, caps=(False, True))
        f += curve("acc", [(s * 0.6, 2.8, 0), (s * 1.9, 2.4, 0), (s * 2.2, 1.2, 0)], [0.5, 0.45, 0.3], [0.6, 0.5, 0.35], sides=4, caps=(False, True))
    f += block("guard", (0, 2.9, 0), (2.4, 0.9, 1.7))
    f += ell("gem", (0, -2.0, 0), (0.9, 0.9, 0.9), segs=6, rings=1)
    return f


def dg_c(stage=0):
    """C 波の刃: 鼓動の波形のように左右へ揺れる刃。柄頭に大きな心臓の核、鍔は錨鉄の小さな横木。"""
    f = dagger_hilt()
    st = []
    for i, y in enumerate([3.2, 4.8, 6.4, 8.0, 9.6, 11.2, 12.8]):
        w = 1.3 * (1 - i / 7.5)
        cx = 0.55 * math.sin(i * 1.6)
        st.append((y, -w, w, 0.65 * (1 - i / 8), 0.65 * (1 - i / 8), cx))
    st[-1] = (13.6, 0, 0, 0, 0, 0.0)
    f += blade("main", st)
    f += block("guard", (0, 2.9, 0), (4.0, 0.9, 1.6), taper=(0.7, 0.9))
    f += ell("gem", (0, -2.2, 0), (1.3, 1.3, 1.1), segs=6, rings=2)
    f += block("spine", (0, -1.0, 0), (1.5, 0.6, 1.5))
    return f


# ================================================================== 一覧
WEAPONS = {
    "abyss_lamp_sword": dict(
        jp="灯り番の剣", tier="rare", glow="lamp", pose="whale", grip=(0.0, 1.0, 0.0), cen=(0.0, 8.5, 0.0), scale=11.0,
        opts=dict(
            a=(ls_a, inlay_ls_a, ("edge",), "吊り灯。真っ直ぐな刃と十字の鍔。鍔の腕から小さな角灯が鎖で垂れる。灯り番の剣と一目で分かる。"),
            b=(ls_b, inlay_ls_b, ("edge",), "灯の柄頭。柄頭そのものが角灯で、刃の根に琥珀の小窓。手もとが光る。"),
            c=(ls_c, inlay_ls_c, ("edge",), "灯窓。幅広の刃の根に縦長の灯りの窓が通る。鍔は錨鉄の円盤。いちばん静か。"))),
    "abyss_sinking_anchor": dict(
        jp="沈め錨", tier="legend", glow="void", pose="ember", grip=(0.0, 1.5, 0.0), cen=(0.0, 10.0, 0.0), scale=8.5,
        opts=dict(
            a=(an_a, inlay_an, ("edge",), "古い錨。胴の先に左右へ反った二本の腕と爪、横木、胴に鎖。いちばん「錨」と読める。"),
            b=(an_b, inlay_an, ("edge",), "四つ爪の錨鉤。四方へ鉤が開き、輪郭が十字になる。引き下ろす武器らしさ。"),
            c=(an_c, inlay_an, ("edge",), "欠けた錨。片方の腕が大きく、もう片方は根で折れて紫の割れ目が光る。心臓の錨の欠けた爪という由来の形。"))),
    "abyss_pulse_dagger": dict(
        jp="鼓動の短剣", tier="legend", glow="pulse", pose="whale", grip=(0.0, 0.8, 0.0), cen=(0.0, 5.0, 0.0), scale=17.0,
        opts=dict(
            a=(dg_a, inlay_dg, ("edge",), "肋の刃。番人の肋のように反った刃、鍔に心臓の形の赤い核。"),
            b=(dg_b, inlay_dg, ("edge",), "脈の針。細い刺突の刃に赤い脈の筋。鍔は小さな肋かご。"),
            c=(dg_c, inlay_dg, ("edge",), "波の刃。鼓動の波形のように揺れる刃、柄頭に大きな心臓の核。"))),
}
