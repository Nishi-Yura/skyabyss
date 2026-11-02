"""章の武器の空き 8 品 x 3 案の形 (2026-10-26 の声「全職に専用の武器」)。巨神の装備に、その章で職の武器が無かった所を足す:
  一章 岩鯨: 鍛冶師の鎚・弓使いの弓 / 二章 潮殻: 鍛冶師の鎚・弓使いの弓 / 三章 雷翼: 鍛冶師の鎚 /
  四章 余燼: 戦士の大剣・弓使いの弓・暗殺者の短剣
材質名と塗りは巨神の武器と同じ (../paint.py。main edge spine guard grip wrap acc gem trim dark cord)。階級はエピック (形の段 rare)。
座標は他の武器と同じ (1 単位 = 1/16 ブロック、+Y が先、x = 0・z = 0 が握り、正面 = -Z)。弓は握りが (5, 8)、弦は -x (d_thunder と同じ)。
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE.parent.parent))
sys.path.insert(0, str(HERE.parent / "chapter_staves"))
from wkit import *  # noqa: F401,F403,E402
from wkit import block, blade, prism, curve, extrude, V, rot_x, rot_y, rot_z  # noqa: E402
from paint import line_mask  # noqa: E402

import numpy as np  # noqa: E402
from lpkit import ellipsoid, spike, loft  # noqa: E402
import d_thunder  # noqa: E402
from d_staves import circ, ell  # noqa: E402

CY = d_thunder.CY
TIP_PULL = d_thunder.TIP_PULL


def inlay(env, kind):
    """光る筋: エピックは刃・頭の根に短い筋、+5 で点線、+10 で長い筋 (main の正面)"""
    face = np.isin(env["mats"], ["main"]) & (np.abs(env["N"][..., 2]) > 0.5)
    if kind == "rare":
        return face & (np.abs(env["X"]) < 0.35) & (env["Y"] > 4) & (env["Y"] < 8)
    if kind == "p5":
        return face & (np.abs(env["X"]) < 0.35) & ((env["Y"] * 0.8) % 2.0 < 1.0)
    if kind == "p10":
        return face & (np.abs(env["X"]) < 0.45)
    return env["lit"] < -9


# ---------------------------------------------------------------- 部品
def haft(top=13.5, mat="grip"):
    f = []
    f += prism(mat, (0, -3.0, 0), (0, top, 0), 1.0, 0.9, sides=6, caps=(True, True))
    f += prism("wrap", (0, -2.0, 0), (0, 2.6, 0), 1.15, 1.15, sides=6, caps=(False, False))
    f += prism("guard", (0, -3.8, 0), (0, -2.8, 0), 1.3, 1.2, sides=6, caps=(True, False))
    f += block("trim", (0, 2.9, 0), (2.4, 0.6, 2.4))
    return f


def hilt(long=False):
    f = []
    f += prism("grip", (0, -2.4 if long else -1.6, 0), (0, 2.8, 0), 1.0, 0.9, sides=6, caps=(False, False))
    f += prism("wrap", (0, -0.6, 0), (0, 2.2, 0), 1.1, 1.0, sides=6, caps=(False, False))
    f += prism("guard", (0, -3.4 if long else -2.6, 0), (0, -2.2 if long else -1.4, 0), 1.4, 1.2, sides=6, caps=(True, False))
    return f


def bow_grip():
    f = []
    f += prism("grip", (5.0, CY - 2.6, 0), (5.0, CY + 2.6, 0), 1.05, 1.05, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (5.0, CY - 1.0, 0), (5.0, CY + 1.0, 0), 1.25, 1.25, sides=6, caps=(True, True), hint=(0, 0, 1))
    f += block("trim", (5.0, CY + 2.9, 0), (2.4, 0.5, 2.4))
    f += block("trim", (5.0, CY - 2.9, 0), (2.4, 0.5, 2.4))
    f += block("gem", (6.2, CY, 0), (0.9, 1.2, 1.4), taper=(0.8, 0.8))
    return f


def limbs(up, ra, rb, mat="main"):
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    f = []
    for path in (up, lo):
        f += curve(mat, path, ra, rb, sides=4, caps=(False, True), hint=(0, 0, 1))
    return f, lo


def finish_bow(f, up, lo, stage):
    return f + d_thunder.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))


def bend(stage, up):
    """引きの段で、腕の先の三つを弦の側へ寄せる"""
    p = TIP_PULL[stage]
    n = len(up)
    return [(x - (p * (i - (n - 4)) / 3.0 if i >= n - 3 else 0), y, z) for i, (x, y, z) in enumerate(up)]


# ================================================================== 一章 岩鯨
def whm_a(stage=0):
    """A 石の頭: 鯨骨の柄の先に苔むした石の頭。片側が平ら、片側に小さな骨の角。"""
    f = haft(13.6, mat="main")
    f += block("guard", (0.6, 14.0, 0), (6.0, 3.6, 3.4), taper=(0.92, 0.92))
    f += spike(BONE, "main", (-2.6, 14.0, 0), (-1, 0, 0), 2.4, 1.0, sides=4, rings=1, hint=(0, 0, 1))
    f += ell("gem", (0.6, 14.0, -1.6), (0.8, 0.8, 0.4))
    f += block("acc", (1.6, 15.6, 0), (2.4, 0.6, 2.4))
    return f


def whm_b(stage=0):
    """B 椎骨の鎚: 頭が鯨の椎骨 (丸い胴に左右の突起)。突起の間に水色の石。"""
    f = haft(13.4, mat="spine")
    f += prism("main", (-2.0, 14.0, 0), (2.0, 14.0, 0), 2.2, 2.2, sides=8, caps=(True, True), hint=(0, 1, 0))
    for s in (1, -1):
        f += spike(BONE, "edge", (0, 14.0 + s * 2.0, 0), (0, s, 0), 2.6, 0.9, sides=4, rings=1, hint=(0, 0, 1))
    f += ell("gem", (0, 14.0, -2.2), (0.9, 0.9, 0.5))
    return f


def whm_c(stage=0):
    """C 両手の大槌: 長い柄の先に大きな石の塊 (角を落とした)。苔の帯が巻く。"""
    f = haft(15.0)
    f += prism("grip", (0, 15.0, 0), (0, 16.0, 0), 0.9, 0.9, sides=6, caps=(False, False))
    f += block("guard", (0, 16.4, 0), (7.6, 4.4, 4.0), taper=(0.85, 0.85))
    f += block("acc", (0, 16.4, 0), (7.8, 1.2, 4.2))
    f += block("main", (3.9, 16.4, 0), (0.8, 3.6, 3.2))
    f += block("main", (-3.9, 16.4, 0), (0.8, 3.6, 3.2))
    return f


def wbw_a(stage=0):
    """A 肋の弓: 二本の鯨の肋をそのまま腕にした弓。継ぎ目に石の輪。"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.0, CY + 5.0, 0), (2.2, CY + 7.4, 0), (0.0, CY + 9.2, 0), (-2.0, CY + 10.2, 0)])
    g, lo = limbs(up, [1.1, 1.0, 0.85, 0.65, 0.45], [1.25, 1.1, 0.95, 0.75, 0.5])
    f += g
    for path in (up, lo):
        x, y, _ = path[1]
        f += prism("guard", (x, y - 0.5, 0), (x, y + 0.5, 0), 1.3, 1.3, sides=6, caps=(True, True), hint=(0, 0, 1))
    return finish_bow(f, up, lo, stage)


def wbw_b(stage=0):
    """B 鯨髭の板弓: 平たい鯨髭の板を重ねた弓。腕の外に苔の房。"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.6, CY + 5.2, 0), (3.2, CY + 8.0, 0), (1.0, CY + 9.8, 0), (-1.0, CY + 10.6, 0)])
    g, lo = limbs(up, [0.7, 0.65, 0.55, 0.45, 0.3], [1.6, 1.5, 1.3, 1.0, 0.6])
    f += g
    g2, lo2 = limbs([(x + 0.6, y, z) for x, y, z in up[:3]], [0.4, 0.35, 0.3], [1.2, 1.1, 0.9], mat="edge")
    f += g2
    for path in (up, lo):
        x, y, _ = path[2]
        f += ell("acc", (x + 1.0, y, 0), (0.7, 0.7, 0.7))
    return finish_bow(f, up, lo, stage)


def wbw_c(stage=0):
    """C 尾鰭の弓: 腕の先が鯨の尾鰭の形に広がる。握りは石。"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.2, CY + 5.2, 0), (2.6, CY + 7.8, 0), (0.6, CY + 9.4, 0), (-1.4, CY + 10.2, 0)])
    g, lo = limbs(up, [1.0, 0.95, 0.8, 0.6, 0.4], [1.15, 1.05, 0.9, 0.7, 0.45])
    f += g
    for path, s in ((up, 1), (lo, -1)):
        x, y, _ = path[-1]
        f += extrude("edge", [(0, 0), (2.4, s * 1.4), (1.2, s * 0.4), (2.0, s * -0.6)], 0.6, c=(x, y, 0))
    return finish_bow(f, up, lo, stage)


# ================================================================== 二章 潮殻
def thm_a(stage=0):
    """A 巻き貝の鎚: 頭が横に寝た巻き貝 (尖った先と口)。"""
    f = haft(13.6, mat="spine")
    pts, ra = [], []
    for k in range(7):
        t = k / 6
        pts.append((-3.0 + t * 6.4, 14.0 + math.sin(t * math.pi * 1.4) * 0.6, 0))
        ra.append(2.2 - 1.8 * t + 0.2)
    f += curve("main", pts, ra, ra, sides=6, caps=(True, True), hint=(0, 1, 0))
    f += ell("gem", (-3.0, 14.0, -1.4), (0.8, 0.8, 0.5))
    return f


def thm_b(stage=0):
    """B 珊瑚巻きの石鎚: 濡れた石の頭に、珊瑚の枝が巻き付く。"""
    f = haft(13.4)
    f += block("guard", (0, 14.0, 0), (5.8, 3.4, 3.2), taper=(0.9, 0.9))
    for k in range(3):
        x = -2.0 + k * 2.0
        f += curve("acc", [(x, 15.6, -0.4), (x + 0.6, 17.0, -0.6), (x + 0.2, 18.0, -0.4)], [0.5, 0.4, 0.2], [0.5, 0.4, 0.2], sides=4, caps=(False, True))
    f += ell("gem", (0, 14.0, -1.7), (0.8, 0.8, 0.4))
    return f


def thm_c(stage=0):
    """C 二枚貝の鎚: 頭が閉じた二枚貝。縁に真珠の光、柄に昆布。"""
    f = haft(13.4)
    for s in (1, -1):
        f += block("main", (0, 14.0 + s * 0.9, 0), (5.6, 1.8, 3.4), taper=(0.7 if s > 0 else 1.0, 1.0 if s > 0 else 0.7))
    f += block("gem", (0, 14.0, -1.6), (4.4, 0.4, 0.4))
    f += block("wrap", (1.1, 11.0, 0), (0.7, 3.4, 0.3), rot=rot_z(-6), taper=(0.6, 1.0))
    return f


def tbw_a(stage=0):
    """A 珊瑚の弓: 腕が珊瑚の枝 (外へ小枝が出る)。"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.0, CY + 5.2, 0), (2.4, CY + 7.6, 0), (0.2, CY + 9.4, 0), (-1.8, CY + 10.2, 0)])
    g, lo = limbs(up, [1.0, 0.9, 0.8, 0.6, 0.4], [1.1, 1.0, 0.85, 0.65, 0.45], mat="acc")
    f += g
    for path, s in ((up, 1), (lo, -1)):
        x, y, _ = path[2]
        f += curve("acc", [(x, y, 0), (x + 1.8, y + s * 1.2, 0), (x + 2.4, y + s * 2.6, 0)], [0.4, 0.3, 0.15], [0.4, 0.3, 0.15], sides=4, caps=(False, True))
    return finish_bow(f, up, lo, stage)


def tbw_b(stage=0):
    """B 殻板の弓: 殻の板を重ねた腕 (板の継ぎ目が段に見える)。"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.4, CY + 5.0, 0), (3.0, CY + 7.6, 0), (0.8, CY + 9.6, 0), (-1.2, CY + 10.4, 0)])
    g, lo = limbs(up, [0.9, 0.85, 0.75, 0.55, 0.35], [1.4, 1.3, 1.1, 0.85, 0.5], mat="spine")
    f += g
    for path in (up, lo):
        for i in (1, 2, 3):
            x, y, _ = path[i]
            f += block("main", (x + 0.3, y, 0), (1.6, 1.0, 1.6), taper=(0.8, 0.8))
    return finish_bow(f, up, lo, stage)


def tbw_c(stage=0):
    """C 真珠の先の弓: 細い腕の両先に真珠。握りに巻き貝の飾り。"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.4, CY + 5.4, 0), (2.6, CY + 8.0, 0), (0.2, CY + 9.6, 0), (-1.8, CY + 10.4, 0)])
    g, lo = limbs(up, [0.85, 0.8, 0.7, 0.5, 0.35], [1.0, 0.9, 0.8, 0.6, 0.4])
    f += g
    f += ell("gem", (up[-1][0], up[-1][1] + 0.5, 0), (0.9, 0.9, 0.9))
    f += ell("gem", (lo[-1][0], lo[-1][1] - 0.5, 0), (0.9, 0.9, 0.9))
    f += curve("main", [(6.0, CY - 1.0, 0), (7.2, CY, 0), (6.4, CY + 1.2, 0)], [0.6, 0.5, 0.3], [0.6, 0.5, 0.3], sides=4, caps=(True, True))
    return finish_bow(f, up, lo, stage)


# ================================================================== 三章 雷翼
def shm_a(stage=0):
    """A 銅の筒の鎚: 頭が銅の筒、両口に銅線の巻き。真ん中に青白い石。"""
    f = haft(13.4, mat="spine")
    f += prism("main", (-3.2, 14.0, 0), (3.2, 14.0, 0), 1.9, 1.9, sides=8, caps=(True, True), hint=(0, 1, 0))
    for x in (-2.4, -1.8, 1.8, 2.4):
        f += prism("edge", (x - 0.2, 14.0, 0), (x + 0.2, 14.0, 0), 2.05, 2.05, sides=8, caps=(True, True), hint=(0, 1, 0))
    f += ell("gem", (0, 14.0, -1.9), (0.8, 0.8, 0.4))
    return f


def shm_b(stage=0):
    """B 羽の戦鎚: 頭の後ろに羽が三枚、前は四角い銅の打面。"""
    f = haft(13.4, mat="spine")
    f += block("main", (1.6, 14.0, 0), (3.6, 3.0, 2.8))
    for k in range(3):
        f += block("acc", (-2.0, 13.0 + k * 1.0, 0), (3.4, 0.8, 0.4), rot=rot_z(-20 + k * 20), taper=(0.4, 0.9))
    f += ell("gem", (1.6, 14.0, -1.5), (0.7, 0.7, 0.4))
    return f


def shm_c(stage=0):
    """C 避雷の鎚: 鎚の頭の上に細い銅の穂が立つ (雷を受ける)。"""
    f = haft(13.4, mat="spine")
    f += block("main", (0, 14.0, 0), (6.0, 2.8, 2.8), taper=(0.9, 0.9))
    f += prism("edge", (0, 15.2, 0), (0, 20.0, 0), 0.5, 0.2, sides=4, caps=(False, True))
    for y in (16.0, 16.8):
        f += prism("edge", (0, y, 0), (0, y + 0.35, 0), 1.0, 1.0, sides=6, caps=(True, True))
    f += ell("gem", (0, 14.0, -1.5), (0.8, 0.8, 0.4))
    return f


# ================================================================== 四章 余燼
def esw_a(stage=0):
    """A 玄武岩の大剣: 幅広い玄武岩の刃、割れ目が光る。鍔は鉄の板。"""
    f = hilt(long=True)
    st = [(3.4, -2.4, 2.4, 1.0, 1.0), (10.0, -2.6, 2.6, 1.0, 1.0), (17.0, -2.2, 2.2, 0.9, 0.9), (20.4, -1.2, 1.2, 0.6, 0.6), (22.4, 0, 0, 0, 0)]
    f += blade("main", st)
    f += block("edge", (0, 3.0, 0), (7.6, 1.2, 2.6), taper=(0.85, 0.9))
    f += block("gem", (0, 6.0, -1.0), (1.0, 2.4, 0.4))
    return f


def esw_b(stage=0):
    """B 火の籠の鍔: まっすぐな鉄の剣。鍔が小さな火の籠 (中で熾火)。"""
    f = hilt()
    st = [(4.4, -1.8, 1.8, 0.8, 0.8), (12.0, -1.8, 1.8, 0.8, 0.8), (18.0, -1.4, 1.4, 0.7, 0.7), (20.6, 0, 0, 0, 0)]
    f += blade("edge", st)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        f += curve("spine", [(math.cos(a) * 1.0, 2.6, math.sin(a) * 1.0), (math.cos(a) * 2.2, 3.4, math.sin(a) * 2.2), (math.cos(a) * 1.0, 4.4, math.sin(a) * 1.0)],
                   [0.35, 0.35, 0.3], [0.35, 0.35, 0.3], sides=4, caps=(False, False))
    f += ell("gem", (0, 3.4, 0), (1.2, 0.9, 1.2))
    return f


def esw_c(stage=0):
    """C 炎の刃: 左右へ波打つ炎の形の刃。根に橙の光。"""
    f = hilt()
    st = []
    for i, y in enumerate([3.6, 6.0, 8.4, 10.8, 13.2, 15.6, 18.0, 20.0]):
        wv = 2.0 * (1 - i / 9.0)
        st.append((y, -wv, wv, 0.8 * (1 - i / 10), 0.8 * (1 - i / 10), 0.7 * math.sin(i * 1.5)))
    st[-1] = (21.0, 0, 0, 0, 0, 0.0)
    f += blade("main", st)
    f += block("acc", (0, 3.2, 0), (5.2, 1.1, 2.2), taper=(0.7, 0.9))
    f += block("gem", (0, 4.6, -0.8), (1.2, 1.4, 0.4))
    return f


def ebw_a(stage=0):
    """A 鉄の腕の弓: 鉄の板の腕、握りの中に熾火が光る。"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.4, CY + 5.2, 0), (2.8, CY + 7.8, 0), (0.6, CY + 9.6, 0), (-1.4, CY + 10.4, 0)])
    g, lo = limbs(up, [0.85, 0.8, 0.7, 0.55, 0.35], [1.2, 1.1, 0.95, 0.75, 0.45], mat="spine")
    f += g
    f += block("gem", (5.0, CY, -1.1), (0.8, 3.0, 0.4))
    return finish_bow(f, up, lo, stage)


def ebw_b(stage=0):
    """B 玄武岩の板弓: 割れた玄武岩の板を継いだ腕。継ぎ目が光る。"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.2, CY + 5.2, 0), (2.4, CY + 7.8, 0), (0.2, CY + 9.6, 0), (-1.8, CY + 10.4, 0)])
    g, lo = limbs(up, [1.0, 0.95, 0.8, 0.6, 0.4], [1.3, 1.2, 1.0, 0.8, 0.5])
    f += g
    for path in (up, lo):
        for i in (1, 3):
            x, y, _ = path[i]
            f += block("gem", (x, y, -0.6), (1.2, 0.3, 0.3))
    return finish_bow(f, up, lo, stage)


def ebw_c(stage=0):
    """C 鎖の弓: 腕の外に鉄の鎖の輪が三つずつ下がる。"""
    f = bow_grip()
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.2, CY + 5.4, 0), (2.4, CY + 8.0, 0), (0.2, CY + 9.6, 0), (-1.8, CY + 10.4, 0)])
    g, lo = limbs(up, [1.0, 0.9, 0.8, 0.6, 0.4], [1.1, 1.0, 0.85, 0.65, 0.45], mat="spine")
    f += g
    for path in (up, lo):
        x, y, _ = path[2]
        for k in range(3):
            f += prism("edge", (x + 1.2 + k * 0.9, y - 0.4, -0.3), (x + 1.2 + k * 0.9, y - 0.4, 0.3), 0.5, 0.5, sides=6, caps=(True, True), hint=(0, 1, 0))
    return finish_bow(f, up, lo, stage)


def edg_a(stage=0):
    """A 火箸の短剣: 細い鉄の刺突の刃。柄頭に熾火。"""
    f = hilt()
    st = [(3.0, -0.8, 0.8, 0.7, 0.7), (7.4, -0.7, 0.7, 0.6, 0.6), (11.0, -0.4, 0.4, 0.4, 0.4), (13.0, 0, 0, 0, 0)]
    f += blade("edge", st)
    f += block("spine", (0, 2.8, 0), (2.6, 0.8, 1.6))
    f += ell("gem", (0, -2.8, 0), (0.9, 0.9, 0.9))
    return f


def edg_b(stage=0):
    """B 玄武岩の欠片: 欠けた玄武岩の三角の刃。割れ目が光る。"""
    f = hilt()
    f += extrude("main", [(-1.8, 3.0), (1.6, 3.0), (2.0, 6.0), (0.6, 10.8), (-0.4, 12.4), (-1.4, 8.0)], 1.0)
    f += block("gem", (0.2, 7.0, -0.55), (0.3, 3.0, 0.2), rot=rot_z(-8))
    f += block("guard", (0, 2.6, 0), (3.2, 0.9, 1.8))
    return f


def edg_c(stage=0):
    """C 鉄の鉤爪: 内へ反った三本の鉄の爪を握りの上に並べる (爪の仲間)。"""
    f = hilt()
    f += block("spine", (0, 3.0, 0), (3.8, 1.2, 2.0))
    for k, x in enumerate((-1.2, 0.0, 1.2)):
        f += curve("edge", [(x, 3.4, 0), (x + 0.3, 6.4, 0), (x - 0.6, 9.2 - abs(x) * 0.6, 0), (x - 2.0, 10.6 - abs(x) * 0.8, 0)],
                   [0.35, 0.3, 0.25, 0.05], [0.5, 0.45, 0.35, 0.05], sides=4, caps=(False, True))
    f += ell("gem", (0, 3.0, -1.0), (0.6, 0.6, 0.4))
    return f


# ================================================================== 一覧
def _m(jp, glow, opts, pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 7.0, 0.0), scale=11.0, stages=1):
    return dict(jp=jp, tier="rare", glow=glow, pose=pose, grip=grip, cen=cen, scale=scale, stages=stages, opts=opts)


def _bow(jp, glow, opts):
    return _m(jp, glow, opts, pose="thunder", grip=(5.0, 8.0, 0.0), cen=(2.0, 8.0, 0.0), scale=12.0, stages=4)


WEAPONS = {
    "whale_hammer": _m("鯨骨の鎚", "whale", dict(
        a=(whm_a, inlay, ("edge",), "石の頭。苔むした石の頭と骨の角。"),
        b=(whm_b, inlay, ("edge",), "椎骨の鎚。鯨の椎骨の頭、左右の突起。"),
        c=(whm_c, inlay, ("edge",), "両手の大槌。大きな石の塊に苔の帯。"))),
    "whale_bow": _bow("鯨肋の弓", "whale", dict(
        a=(wbw_a, inlay, (), "肋の弓。二本の肋が腕、継ぎ目に石の輪。"),
        b=(wbw_b, inlay, (), "鯨髭の板弓。平たい板を重ね、苔の房。"),
        c=(wbw_c, inlay, (), "尾鰭の弓。腕の先が尾鰭に広がる。"))),
    "tide_hammer": _m("潮殻の鎚", "tide", dict(
        a=(thm_a, inlay, ("edge",), "巻き貝の鎚。横に寝た巻き貝の頭。"),
        b=(thm_b, inlay, ("edge",), "珊瑚巻きの石鎚。石の頭に珊瑚が巻く。"),
        c=(thm_c, inlay, ("edge",), "二枚貝の鎚。閉じた二枚貝の頭、縁に真珠の光。"))),
    "tide_bow": _bow("珊瑚の弓", "tide", dict(
        a=(tbw_a, inlay, (), "珊瑚の弓。腕が珊瑚の枝。"),
        b=(tbw_b, inlay, (), "殻板の弓。殻の板を重ねた腕。"),
        c=(tbw_c, inlay, (), "真珠の先の弓。細い腕の両先に真珠。"))),
    "storm_hammer": _m("雷の鎚", "thunder", dict(
        a=(shm_a, inlay, ("edge",), "銅の筒の鎚。銅の筒の頭に銅線の巻き。"),
        b=(shm_b, inlay, ("edge",), "羽の戦鎚。後ろに羽が三枚、前は銅の打面。"),
        c=(shm_c, inlay, ("edge",), "避雷の鎚。頭の上に細い銅の穂が立つ。"))),
    "ember_greatsword": _m("熾火の大剣", "ember", dict(
        a=(esw_a, inlay, ("edge",), "玄武岩の大剣。幅広い玄武岩の刃、割れ目が光る。"),
        b=(esw_b, inlay, ("edge",), "火の籠の鍔。鉄の直剣、鍔が火の籠。"),
        c=(esw_c, inlay, ("edge",), "炎の刃。左右へ波打つ炎の形の刃。")), pose="whale", grip=(0.0, 1.0, 0.0), cen=(0.0, 9.0, 0.0)),
    "ember_bow": _bow("灰の弓", "ember", dict(
        a=(ebw_a, inlay, (), "鉄の腕の弓。鉄の板の腕、握りに熾火。"),
        b=(ebw_b, inlay, (), "玄武岩の板弓。玄武岩の板の継ぎ目が光る。"),
        c=(ebw_c, inlay, (), "鎖の弓。腕に鉄の鎖の輪が下がる。"))),
    "ember_dagger": _m("熾の短剣", "ember", dict(
        a=(edg_a, inlay, ("edge",), "火箸の短剣。細い鉄の刺突の刃、柄頭に熾火。"),
        b=(edg_b, inlay, ("edge",), "玄武岩の欠片。欠けた三角の刃、割れ目が光る。"),
        c=(edg_c, inlay, ("edge",), "鉄の鉤爪。内へ反った三本の爪。")), pose="whale", grip=(0.0, 0.6, 0.0), cen=(0.0, 4.5, 0.0), scale=17.0),
}
