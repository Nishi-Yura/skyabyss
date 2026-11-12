#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""敵の武器の作り直し (2026-11-12。全モデルの確かめでラフ止まりと分かった組。直し方は案 B「持ち主の印」に決まった:
rough_gear_props.py / docs/review/gear_props/rough_b.png)。

    python -X utf8 -I tools/gen/mobs/attack_props/build_foe_weapons.py [名前 ...] [--no-install]

どれも今のメッシュ (assets/skyabyss/lowpoly/<名前>.json) の骨の名前・親・pivot・付け方 (attach) をそのまま使い、
形だけを入れ替える。持つ位置 (原点 = 受け口) と向き・長さは今のものに合わせる (下の各関数の注)。
光る所は textures/lowpoly/<名前>_glow.png (光る画素の色) に書く (GearLayer.lowPoly が読む)。
持ち主の印: 鯨 (骨・椎骨・フジツボ・紺の房)、潮 (珊瑚・貝・真珠)、雷 (真鍮・雷の筋・羽根)、熾と灰 (赤熱・炭・灰の布)。
絵は docs/review/gear_props/foe_weapons.png
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import rough_gear_props as RG  # noqa: E402
from rough_gear_props import *  # noqa: E402,F401,F403

LOWPOLY = KT.ROOT / "src/main/resources/assets/skyabyss/lowpoly"
TEXLP = KT.ROOT / "src/main/resources/assets/skyabyss/textures/lowpoly"

STONE = [(80, 84, 86), (112, 116, 116), (146, 150, 148), (184, 188, 184)]
CHAR = [(22, 18, 16), (40, 32, 28), (62, 50, 42), (90, 74, 60)]
CORALP = [(150, 50, 70), (200, 80, 96), (236, 130, 140), (250, 190, 190)]


def wmats():
    m = RG.gmats()
    m.update({"stone": M_(STONE, lit=0.36, streak=0.04), "char": M_(CHAR, lit=0.3, streak=0.06), "coralp": M_(CORALP, lit=0.36)})
    return m


# 軸の付け替え (正の回転だけ。法線が裏返らない)
ROT = {
    "id": np.eye(3),
    "pike": np.array([[0, 0, 1], [-1, 0, 0], [0, -1, 0]], float),     # 作図の +y (穂先) → -z、+x (斧の刃) → -y
    "hammer": np.array([[0, 0, 1], [0, -1, 0], [1, 0, 0]], float),    # 作図の +y (頭) → -y、+x (頭の長さ) → +z
    "tongs": np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]], float),     # 作図の +y (挟み口) → +z
    "shield": np.array([[0, 0, -1], [0, 1, 0], [1, 0, 0]], float),    # 作図の -z (表) → +x、+x (幅) → +z
}


# ================================================================ 鯨 (骨の番兵・鯨骨の隊長・肋骨の弓兵)
def captain_pike(m):
    # 鯨骨の隊長の槍斧。作図: 柄 y -16..27 (穂先 +y)、斧の刃 +x。ROT pike
    for k in range(10):  # 椎骨を連ねた柄
        y = -14 + k * 3.6
        m.add(f"vert{k}", ell("body", "bone", (0, y, 0), (1.25, 0.9, 1.25), segs=8, rings=2))
    m.add("core", tub("body", "bone", [P(0, -16, 0), P(0, 20, 0)], [0.8, 0.75], None, sides=8))
    wraps(m, "grip", "leather", -4, 4, 1.05, 5)
    for k, (y, a) in enumerate(((-9, 0.4), (-1.5, 2.2), (9, 4.0), (13, 1.2))):
        m.add(f"barn{k}", cone("body", "barn", (math.cos(a) * 1.0, y, math.sin(a) * 1.0), (math.cos(a), 0.2, math.sin(a)), 1.3, 0.8, sides=5))
    m.add("socket", tub("body", "iron", [P(0, 16, 0), P(0, 21, 0)], [1.4, 1.2], None, sides=8))
    m.add("blade", tub("body", "bone", [P(0.6, 14.5, 0), P(5.5, 15, 0), P(9.4, 18.5, 0), P(10.6, 23, 0)], [3.2, 5.0, 5.2, 1.4], [0.6, 0.55, 0.45, 0.2],
                       hint=(0, 0, 1), sides=4, phase=45))
    m.add("edge", tub("body", "steeld", [P(9.6, 15.6, 0), P(11.3, 19.6, 0), P(11.1, 23.6, 0)], [0.55, 0.55, 0.3], None, hint=(0, 0, 1), sides=4))
    m.add("hook", tub("body", "bone", [P(-0.6, 17, 0), P(-4.2, 18.6, 0), P(-5.4, 16.2, 0)], [1.0, 0.8, 0.3], None, hint=(0, 0, 1), sides=4))
    m.add("tip", cone("body", "bone", (0, 20.5, 0), (0, 1, 0), 7, 1.5, sides=4))
    m.add("butt", cone("body", "iron", (0, -15.5, 0), (0, -1, 0), 1.2, 1.1, sides=6))
    m.add("tassel", tub("body", "clothb", [P(0, 15.5, 0), P(-1.4, 12, 0.7), P(-1.8, 8, 0.4)], [0.55, 0.5, 0.3], None, sides=4))
    m.add("knot", ell("body", "clothb", (0, 15.6, 0), (1.0, 0.6, 1.0), segs=6, rings=2))


def bone_sentinel_shield(m):
    # 骨の番兵の盾。作図: 面 = xy (表 -z)、高さ y -9.5..10、幅 x ±4.5、裏の握り +z。ROT shield
    rows = 12
    for k in range(rows):
        t0, t1 = k / rows, (k + 1) / rows
        def w(t):
            return 4.4 * math.sin(math.pi * (0.2 + 0.8 * t)) ** 0.6 if t < 0.92 else 4.4 * (1 - t) / 0.08 * 0.5 + 0.3
        y0, y1 = 10 - 19.5 * t0, 10 - 19.5 * t1
        m.add(f"plank{k}", tub("body", "bone", [P(0, y0, 0), P(0, y1, 0)], [max(0.5, w(t0)) * 1.414, max(0.5, w(t1)) * 1.414], [0.9, 0.9],
                               hint=(1, 0, 0), sides=4, phase=45, caps=(k == 0, k == rows - 1)))
    for k in range(5):  # 表に肋骨
        y = 7 - k * 3.6
        m.add(f"rib{k}", tub("body", "bone", [P(-3.6 + k * 0.3, y, -0.8), P(0, y + 0.8, -1.25), P(3.6 - k * 0.3, y, -0.8)], [0.45, 0.55, 0.45], None,
                             hint=(0, 0, 1), sides=4))
    m.add("boss", ell("body", "iron", (0, 1, -1.1), (1.6, 1.6, 0.8), segs=8, rings=2))
    m.add("spike", cone("body", "bone", (0, 1, -1.6), (0, 0, -1), 3.0, 0.9, sides=5))
    for k, (x, y) in enumerate(((-3, 6), (3, -2), (-2.4, -6))):
        m.add(f"barn{k}", cone("body", "barn", (x, y, -0.7), (0.1, 0.1, -1), 1.1, 0.8, sides=5))
    m.add("strap", tub("body", "leather", [P(-2.6, 1, 0.9), P(0, 1.2, 2.4), P(2.6, 1, 0.9)], [0.45, 0.45, 0.45], None, hint=(0, 1, 0), sides=4))
    m.add("rim", tub("body", "iron", [P(-3.2, 9.6, -0.2), P(0, 10.4, -0.2), P(3.2, 9.6, -0.2)], [0.4, 0.45, 0.4], None, hint=(0, 0, 1), sides=4))


def rib_archer_bow(m):
    # 肋骨の弓兵の弓 (bow の骨だけ。弦の骨はそのまま)。作図そのまま: 腕 y ±8.2、弦の側 -z (先 z -1.75)、腹 +z
    for s in (1, -1):
        pts = [P(0, 0.4 * s, 0.6), P(0.3, 3 * s, 0.4), P(0.3, 5.6 * s, -0.4), P(0.2, 7.6 * s, -1.5), P(0.2, 8.2 * s, -1.9)]
        m.add(f"limb{s}", tub("body", "bone", pts, [0.7, 0.62, 0.5, 0.38, 0.25], [0.5, 0.45, 0.4, 0.3, 0.2], hint=(1, 0, 0), sides=6))
        m.add(f"nock{s}", cone("body", "bone", tuple(P(0.25, 7.3 * s, -1.75)), (0, s, 0.4), 1.2, 0.35, sides=4))
        for k in range(2):
            m.add(f"barn{s}{k}", cone("body", "barn", (0.5, (2.4 + k * 2.2) * s, 0.6 - k * 0.5), (1, 0, 0.3), 0.6, 0.4, sides=5))
    for k in range(3):  # 握りの椎骨
        m.add(f"grip{k}", ell("body", "bone", (0.1, -0.9 + k * 0.9, 0.7), (0.75, 0.42, 0.75), segs=8, rings=2))
    m.add("wrap", tub("body", "clothb", [P(0.1, -1.5, 0.7), P(0.1, 1.5, 0.7)], [0.62, 0.62], None, sides=6))


# ================================================================ 潮 (珊瑚の兵・潮の番人)
def coral_spear(m, stone=False):
    # 珊瑚の兵の三叉。作図そのまま: 柄 y -11.7..25.4 (穂先 +y)、叉は x に開く
    mat = (lambda s: "stone") if stone else (lambda s: s)
    m.add("haft", tub("body", mat("dwood"), [P(0, -11.7, 0), P(0, 17, 0)], [0.6, 0.55], None, sides=8))
    m.add("butt", cone("body", mat("shell"), (0, -11.2, 0), (0, -1, 0), 0.6, 0.7, sides=6))
    wraps(m, "grip", mat("rope"), -3, 3, 0.75, 4)
    for k, (y, a) in enumerate(((7, 0.3), (10, 2.4), (-6, 4.1))):  # 柄に育った珊瑚
        o = P(math.cos(a), 0, math.sin(a))
        m.add(f"growth{k}", tub("body", mat("coralp"), [o * 0.4 + P(0, y, 0), o * 1.4 + P(0, y + 1.2, 0), o * 1.8 + P(0, y + 2.6, 0)], [0.45, 0.35, 0.15], None, sides=5))
    m.add("collar", ell("body", mat("shell"), (0, 17.4, 0), (1.3, 0.9, 1.0), segs=8, rings=2))
    m.add("pearl", ell("body", "pearl" if not stone else "stone", (0, 18.4, -0.8), (0.55, 0.55, 0.55), segs=8, rings=2))
    for s in (-1, 0, 1):
        x0 = s * 0.6
        pts = [P(x0, 17.8, 0), P(s * 2.4, 20, 0), P(s * 3.3, 22.4, 0), P(s * 3.1 * (1 if s else 0), 25.4 if s == 0 else 24.6, 0)]
        if s == 0:
            pts = [P(0, 17.8, 0), P(0, 21, 0), P(0, 24.4, 0)]
        m.add(f"prong{s}", tub("body", mat("coralp"), pts, [0.55] + [0.45] * (len(pts) - 2) + [0.2], None, sides=5))
        top = pts[-1]
        m.add(f"tip{s}", cone("body", mat("coralp"), tuple(top), (s * 0.15, 1, 0), 1.4, 0.4, sides=4))
        m.add(f"barb{s}", cone("body", mat("shell"), tuple(pts[-2] + P(-s * 0.2, 0, 0)), (-s * 0.6 if s else 0.6, -0.6, 0), 0.9, 0.3, sides=4))


def coral_spear_statue(m):
    coral_spear(m, stone=True)


def shore_harpoon(m):
    # 潮の番人の銛。作図そのまま: 柄 y -16.8..35 (穂先 +y)、返しは x
    m.add("haft", tub("body", "dwood", [P(0, -16.8, 0), P(0, 27, 0)], [0.62, 0.58], None, sides=8))
    m.add("butt", ell("body", "shell", (0, -16.2, 0), (0.9, 0.8, 0.9), segs=8, rings=2))
    for k, y in enumerate((-12, 2, 14, 22)):
        m.add(f"band{k}", tub("body", "iron", [P(0, y - 0.4, 0), P(0, y + 0.4, 0)], [0.8, 0.8], None, sides=8))
    wraps(m, "grip", "rope", -3.5, 3.5, 0.78, 5)
    m.add("socket", tub("body", "iron", [P(0, 26, 0), P(0, 28.6, 0)], [0.85, 0.7], None, sides=8))
    m.add("head", tub("body", "coralp", [P(0, 28, 0), P(0, 31, 0), P(0, 35, 0)], [1.1, 0.9, 0.1], [0.5, 0.45, 0.1], hint=(1, 0, 0), sides=4, phase=45))
    for s in (-1, 1):
        m.add(f"barb{s}", tub("body", "coralp", [P(s * 0.6, 30.5, 0), P(s * 2.6, 29.2, 0), P(s * 3.6, 30.8, 0)], [0.45, 0.35, 0.1], None, hint=(0, 0, 1), sides=4))
    # 柄に結んだ綱の輪と、貝の浮き
    ring(m, "coil", "rope", (0.0, 20.0, 0.0), 1.6, axis="y", w=0.35, n=12)
    m.add("line", tub("body", "rope", [P(1.5, 20, 0), P(2.6, 16, 0.4), P(2.2, 12, 0.2)], [0.25, 0.25, 0.2], None, sides=4))
    m.add("float", ell("body", "shell", (2.2, 11, 0.2), (0.9, 1.1, 0.9), segs=8, rings=3))


# ================================================================ 雷 (羽守・雷の祈り手)
def hooked_glaive(m, burnt=False):
    # 鉤の薙刀。作図そのまま: 柄 y -12.2..36.5、刃と鉤は -x へ
    wood, metal, accent = ("char", "iron", "ember") if burnt else ("dwood", "steeld", "stormg")
    m.add("haft", tub("body", wood, [P(0, -12.2, 0), P(0, 24, 0)], [0.7, 0.65], None, sides=8))
    m.add("butt", cone("body", "iron", (0, -11.8, 0), (0, -1, 0), 0.6, 0.8, sides=6))
    wraps(m, "grip", "clothb" if not burnt else "ash", -4, 4, 0.9, 5)
    m.add("collar", tub("body", "brass" if not burnt else "iron", [P(0, 22.6, 0), P(0, 25, 0)], [1.0, 1.2], None, sides=8))
    pts = [P(0, 24.5, 0), P(-1.6, 28.4, 0), P(-2.8, 32, 0), P(-2.0, 34.6, 0), P(-0.2, 36.5, 0)]
    m.add("blade", tub("body", metal, pts, [1.8, 2.3, 2.2, 1.4, 0.25], [0.42, 0.38, 0.32, 0.25, 0.12], hint=(0, 0, 1), sides=4, phase=45))
    m.add("edge", tub("body", accent if burnt else "steeld", [P(-3.6, 28.6, 0), P(-4.6, 32, 0), P(-3.6, 34.8, 0)], [0.32, 0.32, 0.2], None, hint=(0, 0, 1), sides=4))
    m.add("hook", tub("body", metal, [P(0.4, 25.6, 0), P(-4.0, 26.6, 0), P(-8.4, 25.4, 0), P(-10.9, 22.6, 0)], [0.9, 0.75, 0.5, 0.15], None, hint=(0, 0, 1), sides=4))
    for k, y in enumerate((27.6, 30.6, 33.2)):  # 刃に走る雷 (焼けた方は熾のひび)
        m.add(f"vein{k}", tub("body", accent, [P(-0.8, y, -0.45), P(-1.8, y + 0.9, -0.45), P(-1.3, y + 2.0, -0.45)], [0.22, 0.22, 0.16], None,
                              hint=(0, 0, 1), sides=4))
    if burnt:
        for k, y in enumerate((-6, 8, 16)):
            m.add(f"crack{k}", tub("body", "ember", [P(0.5, y, -0.5), P(0.62, y + 1.6, -0.3)], [0.18, 0.15], None, sides=4))
    else:
        for k, (y, rz) in enumerate(((21.5, 25), (20.8, -20))):
            m.add(f"feather{k}", ell("body", "feath", (1.2 if k else -1.2, y, 0.6), (0.5, 2.6, 0.2), segs=6, rings=2, rz=rz))


def scorched_glaive(m):
    hooked_glaive(m, burnt=True)


def supplicant_staff(m):
    # 雷の祈り手の杖。作図そのまま: 柄 y -15.6..31.5、頭は真鍮の籠に雷の珠
    m.add("haft", tub("body", "dwood", [P(0, -15.6, 0), P(0, 25, 0)], [0.6, 0.55], None, sides=8))
    m.add("butt", cone("body", "brass", (0, -15.2, 0), (0, -1, 0), 0.5, 0.7, sides=6))
    wraps(m, "grip", "clothb", -3, 3, 0.75, 4)
    for k, y in enumerate((12, 18)):
        m.add(f"band{k}", tub("body", "brass", [P(0, y - 0.3, 0), P(0, y + 0.3, 0)], [0.75, 0.75], None, sides=8))
    m.add("cup", tub("body", "brass", [P(0, 24.6, 0), P(0, 26.2, 0)], [0.8, 1.6], None, sides=8))
    m.add("orb", ell("body", "stormg", (0, 28.2, 0), (1.3, 1.5, 1.3), segs=10, rings=4))
    for k in range(4):
        a = k / 4 * math.tau
        m.add(f"cage{k}", tub("body", "brass", [P(math.cos(a) * 1.4, 26, math.sin(a) * 1.4), P(math.cos(a) * 2.1, 28.2, math.sin(a) * 2.1),
                                                 P(math.cos(a) * 0.9, 30.6, math.sin(a) * 0.9), P(0, 31.5, 0)], [0.22, 0.22, 0.2, 0.15], None, sides=4))
    for k, (x, rz) in enumerate(((1.0, -12), (-1.0, 14))):  # 祈りの札
        m.add(f"slip{k}", tub("body", "paper", [P(x, 24.4, 0.5), P(x * 1.4, 21, 0.7), P(x * 1.3, 18.6, 0.6)], [0.45, 0.45, 0.4], [0.06] * 3, hint=(0, 0, 1),
                              sides=4))
        m.add(f"mark{k}", ell("body", "inkred", (x * 1.35, 21.6, 0.62), (0.22, 0.3, 0.05), segs=4, rings=1))


# ================================================================ 熾と灰 (灰の鍛冶・灰の下僕・長老の下僕・灰の抜け殻)
def smith_hammer(m):
    # 灰の鍛冶の大槌。作図: 柄 y -7..30、頭 y 30..36 で長さは x ±11.5、幅 z ±7.5 (ROT hammer で頭が下・長さが z へ)
    m.add("haft", tub("body", "dwood", [P(0, -7, 0), P(0, 31, 0)], [1.0, 0.9], None, sides=8))
    wraps(m, "grip", "leather", -6, 3, 1.15, 6)
    m.add("pommel", ell("body", "iron", (0, -7, 0), (1.4, 0.9, 1.4), segs=8, rings=2))
    m.add("head", tub("body", "iron", [P(-9.5, 33, 0), P(9.5, 33, 0)], [4.6, 4.6], [3.8, 3.8], hint=(0, 1, 0), sides=6, phase=30, caps=(True, True)))
    for s in (-1, 1):
        m.add(f"face{s}", tub("body", "steeld", [P(s * 9.5, 33, 0), P(s * 11.5, 33, 0)], [4.0, 3.4], None, hint=(0, 1, 0), sides=6, phase=30, caps=(False, True)))
        m.add(f"ring{s}", tub("body", "iron", [P(s * 6.2, 33, 0), P(s * 7.2, 33, 0)], [4.9, 4.9], None, hint=(0, 1, 0), sides=6, phase=30))
    m.add("hot", ell("body", "ember", (11.7, 33, 0), (0.5, 3.2, 2.6), segs=8, rings=2))
    for k, x in enumerate((-4.2, 0.0, 4.2)):  # 頭を巡る熾の継ぎ目 (どの向きからも見える)
        m.add(f"seam{k}", tub("body", "ember", [P(x - 0.35, 33, 0), P(x + 0.35, 33, 0)], [4.75, 4.75], [3.95, 3.95], hint=(0, 1, 0), sides=6, phase=30))
    m.add("hot2", ell("body", "ember", (-11.7, 33, 0), (0.5, 3.2, 2.6), segs=8, rings=2))
    m.add("band", tub("body", "iron", [P(0, 27.6, 0), P(0, 37.6, 0)], [1.4, 1.4], None, sides=8))
    m.add("wedge", ell("body", "brass", (0, 37.6, 0), (0.9, 0.5, 0.9), segs=6, rings=1))


def smith_tongs(m):
    # 灰の鍛冶のやっとこ。作図: 挟み口 +y (26)、柄 y -9.5、x に開く (ROT tongs で +y → +z)
    for s in (-1, 1):
        m.add(f"handle{s}", tub("body", "iron", [P(s * 3.6, -9.5, 0), P(s * 2.4, 2, 0), P(s * 0.6, 14, 0)], [0.6, 0.65, 0.75], [0.45, 0.5, 0.55], hint=(0, 0, 1),
                                sides=6))
        m.add(f"grip{s}", tub("body", "leather", [P(s * 3.5, -8.5, 0), P(s * 3.0, -3, 0)], [0.8, 0.8], None, sides=6))
        m.add(f"jaw{s}", tub("body", "iron", [P(s * 0.6, 14, 0), P(s * 1.6, 19, 0), P(s * 1.2, 24, 0), P(s * 0.5, 26, 0)], [0.8, 0.85, 0.7, 0.45], [0.6, 0.65, 0.6, 0.4],
                             hint=(0, 0, 1), sides=6))
    m.add("pivot", ell("body", "brass", (0, 14, 0), (1.3, 1.3, 1.0), segs=8, rings=2))
    m.add("ingot", tub("body", "ember", [P(0, 21.5, 0), P(0, 25.2, 0)], [1.0, 0.9], [1.8, 1.7], hint=(1, 0, 0), sides=4, phase=45, caps=(True, True)))
    m.add("ingot_core", ell("body", "magma", (0, 23.4, -1.0), (0.6, 1.2, 0.3), segs=6, rings=2))


def ash_poker(m, husk=False):
    # 灰の下僕の火かき棒。作図そのまま: 柄 y -11.8..28.7、先の鉤は +z へ
    wood = "char" if husk else "dwood"
    m.add("haft", tub("body", "iron", [P(0, -11.8, 0), P(0, 26, 0)], [0.55, 0.5], None, sides=6))
    m.add("ring", ell("body", "iron", (0, -11.3, 0), (0.9, 0.5, 0.9), segs=8, rings=2))
    m.add("grip", tub("body", wood, [P(0, -9, 0), P(0, 2, 0)], [0.9, 0.85], None, sides=8))
    wraps(m, "wrap", "ash", -6, 0, 0.98, 3)
    m.add("collar", tub("body", "iron", [P(0, 2, 0), P(0, 3, 0)], [1.0, 1.0], None, sides=8))
    m.add("hook", tub("body", "iron", [P(0, 25.5, 0), P(0, 27.8, 0.6), P(0, 28.7, 3.0), P(0, 27.4, 5.6), P(0, 25.6, 6.2)], [0.55, 0.55, 0.5, 0.45, 0.25], None,
                      sides=6))
    m.add("hot", tub("body", "ember", [P(0, 27.6, 4.6), P(0, 26.4, 6.0)], [0.5, 0.3], None, sides=6))
    m.add("spike", cone("body", "iron", (0, 26, 0), (0, 1, 0), 2.6, 0.55, sides=5))
    for k, y in enumerate((10, 16, 21)):  # 赤熱の名残
        m.add(f"glow{k}", tub("body", "ember" if husk or k == 2 else "iron", [P(0, y - 0.25, 0), P(0, y + 0.25, 0)], [0.62, 0.62], None, sides=6))
    if husk:
        for k, y in enumerate((-4, 6, 13)):
            m.add(f"crack{k}", tub("body", "ember", [P(0.62, y, 0), P(0.5, y + 1.6, 0.3)], [0.15, 0.12], None, sides=4))


def ash_husk_poker(m):
    ash_poker(m, husk=True)


def elder_rake(m):
    # 長老の下僕の熊手。作図そのまま: 柄 y -14.7..35.9、歯は +z へ
    m.add("haft", tub("body", "char", [P(0, -14.7, 0), P(0, 31, 0)], [0.75, 0.68], None, sides=8))
    m.add("butt", cone("body", "iron", (0, -14.3, 0), (0, -1, 0), 0.5, 0.8, sides=6))
    wraps(m, "grip", "ash", -5, 3, 0.95, 5)
    for k, y in enumerate((8, 16)):  # 骨の飾り
        m.add(f"charm{k}", tub("body", "bone", [P(0.8, y, 0.4), P(1.6, y - 2.2, 0.6)], [0.3, 0.25], None, sides=4))
        m.add(f"bead{k}", ell("body", "ember", (1.7, y - 2.5, 0.6), (0.4, 0.4, 0.4), segs=6, rings=2))
    m.add("crossbar", tub("body", "iron", [P(-2.9, 31.6, 0), P(2.9, 31.6, 0)], [0.6, 0.6], None, hint=(0, 1, 0), sides=6))
    for k, x in enumerate((-2.6, -0.9, 0.9, 2.6)):
        m.add(f"tine{k}", tub("body", "iron", [P(x, 31.6, 0), P(x, 34.8, 0.8), P(x, 35.6, 3.8), P(x, 34.2, 6.2)], [0.42, 0.4, 0.34, 0.14], None, sides=5))
        m.add(f"hot{k}", ell("body", "ember", (x, 34.4, 5.6), (0.32, 0.32, 0.32), segs=6, rings=1))
    m.add("brace", tub("body", "iron", [P(-2, 31.6, 0), P(0, 34, 0), P(2, 31.6, 0)], [0.3, 0.3, 0.3], None, hint=(0, 0, 1), sides=4))


WEAPONS = {
    "captain_pike": "pike", "bone_sentinel_shield": "shield", "rib_archer_bow": "id", "coral_spear": "id", "coral_spear_statue": "id",
    "shore_harpoon": "id", "hooked_glaive": "id", "scorched_glaive": "id", "supplicant_staff": "id", "smith_hammer": "hammer",
    "smith_tongs": "tongs", "ash_poker": "id", "ash_husk_poker": "id", "elder_rake": "id",
}
# 入れ替えるのは骨一つだけ (弓の弦の骨はそのまま)
TARGET_BONE = {"rib_archer_bow": "bow"}


def build(name):
    m = Mount("w_" + name, name)
    m.bone("body", "root", (0, 0, 0))
    globals()[name](m)
    R = ROT[WEAPONS[name]]
    for f in m.F:
        f.verts = f.verts @ R.T
        if getattr(f, "normal", None) is not None:
            f.normal = R @ f.normal
        if getattr(f, "ref", None) is not None:
            f.ref = R @ np.asarray(f.ref, float)
    m.pal = dict(mats=wmats(), glow_mats=sorted(RG.GGLOW | {"pearl"}))
    m.detail = None
    m.hint = 128
    return m


def install(name, m, size, atlas, glow):
    """今のメッシュの見出し・骨の名前・親・pivot を残して、形と絵を入れ替える"""
    path = LOWPOLY / (name + ".json")
    old = json.loads(path.read_text(encoding="utf-8"))
    work = KT.ROOT / "build/foe_weapons" / name
    work.mkdir(parents=True, exist_ok=True)
    data, _ = blp.export_named(m, name, size, atlas, glow, work)
    mesh = json.loads((work / f"{name}_mesh.json").read_text(encoding="utf-8"))
    new_b = None
    for bn, b in mesh["bones"].items():
        if b.get("vertices"):
            new_b = b
    bone = TARGET_BONE.get(name) or next(k for k, b in old["bones"].items() if b.get("vertices"))
    ob = old["bones"][bone]
    ob["vertices"], ob["uvs"], ob["triangles"] = new_b["vertices"], new_b["uvs"], new_b["triangles"]
    ob["triangle_count"] = len(new_b["triangles"])
    tri = sum(len(b.get("triangles") or []) for b in old["bones"].values())
    for k in ("uv", "units", "texels_per_unit", "uv_inset_texels"):
        if k in mesh:
            old[k] = mesh[k]
    old["texture"] = name
    old["texture_size"] = [atlas.shape[1], atlas.shape[0]]
    old["triangle_count"] = tri
    old.pop("glowmask", None)
    old["skyabyss_closed"] = True
    if name in TARGET_BONE:
        # 弦の骨の uv は前の絵のもの。新しい絵の隅の暗い画素 (弦の色) を指すように直す
        for bn, b in old["bones"].items():
            if bn != bone and b.get("uvs"):
                b["uvs"] = [[0.5 / atlas.shape[1], 0.5 / atlas.shape[0]] for _ in b["uvs"]]
        atlas[0:2, 0:2] = (60, 52, 44, 255)
    path.write_bytes(json.dumps(old, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
    Image.fromarray(atlas).save(TEXLP / (name + ".png"))
    gp = TEXLP / (name + "_glow.png")
    if glow.any():
        g = np.zeros_like(atlas)
        g[glow] = atlas[glow]
        Image.fromarray(g).save(gp)
    elif gp.exists():
        gp.unlink()
    for stale in (TEXLP / (name + "_glowmask.png"),):
        if stale.exists():
            stale.unlink()
    return tri


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    names = args or list(WEAPONS)
    tiles = []
    for name in names:
        m = build(name)
        size, atlas, glow = blp.paint_mount(m, m.pal, m.detail, m.hint)
        tris = sum(len(f.verts) - 2 for f in m.F)
        if "--no-install" not in sys.argv:
            tris = install(name, m, size, atlas, glow)
        lo = np.min([f.verts.min(0) for f in m.F], axis=0)
        hi = np.max([f.verts.max(0) for f in m.F], axis=0)
        c = (lo + hi) / 2
        sc = 220 / max(1.0, float(np.max(hi - lo)))
        tiles.append((name, tris, view(m.F, atlas, glow, 215, 18, (300, 300), c, sc, ground=False)))
        print(name, tris, "bbox", lo.round(1).tolist(), hi.round(1).tolist(), flush=True)
    cols = 5
    rows = (len(tiles) + cols - 1) // cols
    cv = Image.new("RGB", (cols * 310 + 10, rows * 334 + 10), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    for i, (label, tris, img) in enumerate(tiles):
        x, y = 10 + (i % cols) * 310, 10 + (i // cols) * 334
        d.text((x, y), f"{label}  {tris}", font=voxel.font(15), fill=(150, 232, 255))
        cv.paste(img, (x, y + 24))
    RG.REVIEW_G.mkdir(parents=True, exist_ok=True)
    cv.save(RG.REVIEW_G / "foe_weapons.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
