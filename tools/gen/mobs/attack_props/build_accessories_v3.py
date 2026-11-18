#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""装飾品 41 品の作り直し (2026-11-12。全モデルの確かめで「六角の輪 + 板や塊」の同じ作り・絵 32px のラフ止まりと分かった組。
直し方は案 A「細工」に決まった: rough_charms_tools.py / docs/review/charms_tools/rough_a.png)。淵の 3 品 (脈を聴く器・灯の御守り・測りの錘) は作り込み済みなので残す。

    python -X utf8 -I tools/gen/mobs/attack_props/build_accessories_v3.py [品 ...] [--no-install]

どの品も金具の細工物として作る: 透かしの枠・爪留めの石・鎖の輪・刻み・提げ輪。土地ごとの材の言葉
(岩鯨 = 石と骨と真鍮、潮殻 = 珊瑚と貝と真珠と銀、雷翼 = 真鍮と羽と雷、余燼 = 鉄と熾と金、野 = その土地の物、地下 = その巣の物)。
今の模型の向き (y が上・平らな面は ±Z・提げ輪が上) と大きさ (いちばん長い辺) と中心に合わせて置き換える (体に付けた姿の位置が変わらない)。
入れる物: lowpoly/items/<品>/<段>.json (骨 weapon)・textures/lowpoly/items/<品>/<段>_atlas.png (+ _glow)。絵は 128。
見本: docs/review/charms_tools/acc_<番号>.png
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import rough_charms_tools as RCT  # noqa: E402
from rough_charms_tools import *  # noqa: E402,F401,F403
import rough_charms_tools as RCT  # noqa: E402,F811  (名前の衝突を避けて、読み込みの後で結び直す)

LP = KT.ROOT / "src/main/resources/assets/skyabyss/lowpoly/items"
TEX = KT.ROOT / "src/main/resources/assets/skyabyss/textures/lowpoly"

AMBER = [(120, 50, 10), (180, 90, 20), (230, 150, 40), (255, 210, 110)]
MOSS = [(30, 50, 26), (50, 80, 40), (80, 116, 56), (120, 150, 80)]
GLASSV = [(40, 90, 70), (70, 150, 110), (130, 210, 160), (210, 250, 220)]
TEAL = [(20, 60, 70), (30, 100, 110), (60, 150, 160), (130, 210, 210)]
NAVY = [(20, 24, 50), (30, 40, 80), (50, 64, 120), (90, 110, 170)]
RED = [(90, 20, 20), (150, 36, 30), (200, 64, 48), (236, 120, 100)]
FEATHW = [(120, 130, 150), (170, 180, 200), (210, 218, 232), (240, 244, 250)]
CRYST = [(80, 120, 200), (130, 170, 240), (190, 220, 255), (240, 250, 255)]


def amats():
    m = RCT.cmats()
    m.update({"amber": M_(AMBER, base=0.75, lit=0.25, min=1), "moss": M_(MOSS, lit=0.32), "glassv": M_(GLASSV, base=0.55, lit=0.45, noise=0.0),
              "teal": M_(TEAL, lit=0.4), "navy": M_(NAVY, lit=0.36, streak=0.05), "red": M_(RED, lit=0.36), "cryst": M_(CRYST, base=0.7, lit=0.25, min=1), "featherw": M_(FEATHW, lit=0.34, streak=0.08)})
    return m


AGLOW = sorted(RCT.RG.GGLOW | {"light", "ember", "pearl", "amber", "cryst"})


# ---------------------------------------------------------------- 細工の部品
def hang(m, top, mat="brass", r=0.9, links=0):
    """提げ輪 (+ 鎖の輪 links 個)"""
    ring(m, "bail", mat, (0, top, 0), r, axis="z", w=0.25, n=10)
    if links:
        chain(m, "lk", (0, top + r, 0), (0, top + r + links * 1.0, 0), links=links, mat=mat)


def bezel(m, nm, c, r, mat="brass", prongs=6, stone="stone", depth=0.9, sr=None):
    """爪留めの石: 縁の輪・爪・石"""
    c = np.asarray(c, float)
    sr = sr or r * 0.92
    m.add(nm + "_stone", ell("body", stone, tuple(c), (sr, sr * 1.08, depth), segs=8, rings=3))
    ring(m, nm + "_rim", mat, c, r, axis="z", w=0.3, n=12)
    for k in range(prongs):
        a = k / prongs * math.tau + math.pi / prongs
        m.add(f"{nm}_pr{k}", cone("body", mat, tuple(c + P(math.cos(a) * r * 1.05, math.sin(a) * r * 1.05, -0.2)), (-math.cos(a), -math.sin(a), -0.6), 0.9, 0.22,
                                  sides=4))


def filigree(m, nm, c, r, mat="brass", n=8):
    """透かしの枠 (外の輪と、内へ巻く小さな渦)"""
    c = np.asarray(c, float)
    ring(m, nm + "_out", mat, c, r, axis="z", w=0.22, n=16)
    for k in range(n):
        a = k / n * math.tau
        p0 = c + P(math.cos(a) * r, math.sin(a) * r, 0)
        p1 = c + P(math.cos(a + 0.25) * r * 0.78, math.sin(a + 0.25) * r * 0.78, 0)
        p2 = c + P(math.cos(a + 0.05) * r * 0.66, math.sin(a + 0.05) * r * 0.66, 0)
        m.add(f"{nm}_sc{k}", tub("body", mat, [p0, p1, p2], [0.14, 0.13, 0.1], None, sides=4))


def etch(m, nm, c, w, h, n, z, mat="goldl"):
    c = np.asarray(c, float)
    for k in range(n):
        x = -w / 2 + w * (k + 0.5) / n
        m.add(f"{nm}{k}", tub("body", mat, [c + P(x, -h / 2, z), c + P(x * 0.8, h / 2, z)], [0.07, 0.07], None, sides=3))


def drops(m, nm, xs, y, mat="pearl", r=0.4, mount="brass"):
    for k, x in enumerate(xs):
        m.add(f"{nm}{k}", ell("body", mat, (x, y, 0), (r, r * 1.3, r), segs=6, rings=2))
        m.add(f"{nm}c{k}", tub("body", mount, [P(x, y + r * 1.2, 0), P(x * 0.9, y + r * 2.6, 0)], [0.08, 0.08], None, sides=3))


def plate_v(m, nm, mat, c, w, h, t=0.5, sides=6):
    """平たい板 (前 -Z を向く)"""
    c = np.asarray(c, float)
    m.add(nm, tub("body", mat, [c + P(0, -h / 2, 0), c + P(0, h / 2, 0)], [w * 0.5, w * 0.5], [t, t], hint=(1, 0, 0), sides=sides, phase=30,
                  caps=(True, True)))


# ================================================================ 雷翼 (真鍮と羽と雷)
def storm_charm(m):
    filigree(m, "f", (0, 0, 0), 3.2, "brass")
    bezel(m, "g", (0, 0, 0), 1.6, "brass", 6, "stormg")
    for s in (-1, 1):
        m.add(f"feather{s}", ell("body", "featherw", (s * 2.6, -3.6, 0.2), (0.6, 2.4, 0.2), segs=6, rings=2, rz=s * 25))
        m.add(f"quill{s}", tub("body", "brass", [P(s * 1.8, -1.6, 0), P(s * 2.2, -2.4, 0.1)], [0.14, 0.12], None, sides=4))
    m.add("bolt", tub("body", "stormg", [P(0, -2.2, -0.2), P(0.4, -3.4, -0.2), P(-0.3, -4.2, -0.2), P(0.2, -5.4, -0.2)], [0.2, 0.18, 0.16, 0.08], None, sides=4))
    hang(m, 3.9, "brass", links=1)


def storm_anklet(m):
    # 足首の輪 (横に寝た輪)。真鍮の編み帯に雷の玉と羽の房
    for k in range(3):
        pts = [P(math.cos(a) * (5.6 + 0.25 * k), 0.4 * math.sin(a * 6 + k * 2) + k * 0.5 - 0.5, math.sin(a) * (5.6 + 0.25 * k)) for a in np.linspace(0, math.tau, 25)]
        m.add(f"band{k}", tub("body", "brass" if k != 1 else "navy", pts, [0.32] * 25, None, sides=4, caps=(False, False)))
    for k in range(4):
        a = k / 4 * math.tau + 0.4
        m.add(f"bead{k}", ell("body", "stormg", (math.cos(a) * 6.0, 0, math.sin(a) * 6.0), (0.55, 0.55, 0.55), segs=6, rings=2))
    m.add("tag", tub("body", "brass", [P(0, -0.4, -6.0), P(0, -2.4, -6.2)], [0.2, 0.15], None, sides=4))
    for k in range(3):
        m.add(f"plume{k}", ell("body", "featherw", (-0.8 + k * 0.8, -4.2, -6.2), (0.35, 1.8, 0.15), segs=6, rings=2, rz=-20 + k * 20))


def storm_eye_feather(m):
    # 雷翼の目の羽: 長い羽を真鍮の口金に差し、根に雷の眼の石
    m.add("vane", tub("body", "featherw", [P(0, -1.0, 0), P(0.4, 4.0, 0), P(0.1, 8.0, 0), P(-0.4, 10.6, 0)], [1.4, 2.4, 1.8, 0.2], [0.2, 0.2, 0.2, 0.1], hint=(0, 0, 1),
                     sides=4))
    m.add("rachis", tub("body", "brass", [P(0, -3.0, -0.25), P(0.2, 6.0, -0.25), P(-0.3, 10.0, -0.25)], [0.15, 0.12, 0.05], None, sides=4))
    for k in range(4):
        m.add(f"bar{k}", tub("body", "navy", [P(-1.6, 1.5 + k * 2.0, -0.22), P(1.8, 2.2 + k * 2.0, -0.22)], [0.1, 0.1], None, sides=3))
    m.add("ferrule", tub("body", "brass", [P(0, -4.6, 0), P(0, -1.6, 0)], [0.7, 0.9], None, sides=8))
    bezel(m, "eye", (0, -2.8, -0.6), 0.8, "brass", 4, "stormg", depth=0.4)
    ring(m, "bail", "brass", (0, -5.4, 0), 0.7, axis="z", w=0.22, n=10)


def storm_crystal_shard(m):
    # 地下の雷の巣: 真鍮の籠に収めた雷の晶
    m.add("shard", cone("body", "cryst", (0, -4.0, 0), (0, 1, 0), 9.0, 1.8, sides=6))
    m.add("shard2", cone("body", "cryst", (0, -4.0, 0), (0, -1, 0), 2.0, 1.8, sides=6))
    for k in range(4):
        a = k / 4 * math.tau
        m.add(f"cage{k}", tub("body", "brass", [P(math.cos(a) * 0.6, 4.4, math.sin(a) * 0.6), P(math.cos(a) * 1.9, 0.0, math.sin(a) * 1.9),
                                                 P(math.cos(a) * 1.4, -4.6, math.sin(a) * 1.4)], [0.16, 0.16, 0.12], None, sides=4))
    for y in (-2.4, 1.6):
        ring(m, f"band{y}", "brass", (0, y, 0), 1.75 if y < 0 else 1.25, axis="y", w=0.2, n=10)
    m.add("spark", tub("body", "stormg", [P(-0.6, -1.0, -1.2), P(0.3, 0.4, -1.3), P(-0.2, 1.6, -1.2)], [0.12, 0.1, 0.06], None, sides=3))
    hang(m, 5.2, "brass")


# ================================================================ 余燼 (鉄と熾と金)
def kindling_charm(m):
    # 熾の守り札: 鉄の小さな炉の扉に、熾の石と金の格子
    plate_v(m, "body", "iron", (0, 0, 0), 6.0, 7.6, 0.8, sides=6)
    m.add("grate_frame", tub("body", "goldl", [P(-1.8, -2.0, -0.85), P(-1.8, 1.8, -0.85), P(1.8, 1.8, -0.85), P(1.8, -2.0, -0.85), P(-1.8, -2.0, -0.85)],
                             [0.2] * 5, None, sides=4, caps=(False, False)))
    for k in range(3):
        m.add(f"bar{k}", tub("body", "goldl", [P(-1.2 + k * 1.2, -2.0, -0.9), P(-1.2 + k * 1.2, 1.8, -0.9)], [0.12, 0.12], None, sides=4))
    m.add("ember", ell("body", "ember", (0, -0.2, -0.5), (1.5, 1.6, 0.4), segs=8, rings=2))
    for s in (-1, 1):
        m.add(f"hinge{s}", tub("body", "iron", [P(s * 3.0, -2.6, 0), P(s * 3.0, 2.6, 0)], [0.35, 0.35], None, sides=6))
    m.add("key", tub("body", "goldl", [P(0, -3.8, 0), P(0, -6.2, 0)], [0.2, 0.15], None, sides=4))
    ring(m, "keybow", "goldl", (0, -6.8, 0), 0.6, axis="z", w=0.16, n=8)
    hang(m, 4.6, "iron")


def ember_mantle(m):
    # 肩掛けの熾の帯: 煤けた布の肩当てに、鉄の留め具・金の縁・熾のひび
    for s in (-1, 1):
        m.add(f"pad{s}", ell("body", "iron", (s * 6.0, 4.0, 0), (4.6, 1.6, 4.0), segs=8, rings=3))
        m.add(f"rim{s}", tub("body", "goldl", [P(s * 2.0, 3.0, -3.6), P(s * 6.0, 2.8, -4.2), P(s * 10.0, 3.2, -3.0)], [0.25] * 3, None, sides=4))
        for k in range(3):
            m.add(f"crack{s}{k}", tub("body", "ember", [P(s * (4 + k * 1.4), 5.4, -1.6), P(s * (4.6 + k * 1.4), 5.6, 1.0)], [0.15, 0.12], None, sides=3))
    m.add("cloth", tub("body", "ash", [P(-8, 2.6, 0.8), P(0, 1.6, 2.2), P(8, 2.6, 0.8)], [2.4, 2.8, 2.4], [0.4, 0.4, 0.4], hint=(0, 1, 0), sides=4))
    m.add("tail", tub("body", "ash", [P(0, 1.0, 2.6), P(0, -6.0, 3.0), P(0.6, -12.0, 2.8)], [3.0, 2.6, 1.4], [0.3, 0.3, 0.2], hint=(1, 0, 0), sides=4))
    for k in range(3):
        m.add(f"glow{k}", tub("body", "ember", [P(-1.6 + k * 1.6, -4.0 - k * 2, 2.6), P(-1.0 + k * 1.6, -6.0 - k * 2, 2.7)], [0.14, 0.1], None, sides=3))
    bezel(m, "clasp", (0, 3.2, -2.6), 1.2, "goldl", 6, "ember", depth=0.5)


def ember_lantern(m):
    # 余燼の灯: 鉄の角灯に金の笠、熾の芯、鎖
    m.add("base", tub("body", "iron", [P(0, -5.0, 0), P(0, -3.8, 0)], [3.2, 3.0], None, sides=8, caps=(True, True)))
    m.add("glass", tub("body", "lantern", [P(0, -3.8, 0), P(0, 2.6, 0)], [2.4, 2.4], None, sides=8, caps=(False, False)))
    m.add("core", ell("body", "ember", (0, -0.8, 0), (1.0, 1.8, 1.0), segs=8, rings=3))
    for k in range(4):
        a = k / 4 * math.tau + math.pi / 4
        m.add(f"bar{k}", tub("body", "iron", [P(math.cos(a) * 2.6, -3.8, math.sin(a) * 2.6), P(math.cos(a) * 2.6, 2.6, math.sin(a) * 2.6)], [0.3, 0.3], None, sides=4))
    m.add("roof", cone("body", "goldl", (0, 2.6, 0), (0, 1, 0), 2.6, 3.4, sides=8))
    m.add("finial", ell("body", "goldl", (0, 5.6, 0), (0.5, 0.5, 0.5), segs=6, rings=2))
    etch(m, "band", (0, -4.4, -3.1), 4.0, 0.6, 5, 0, "goldl")
    hang(m, 6.4, "iron")


def ember_horn(m):
    # 余燼の角笛: 反った黒い角に金の口金と吹き口、熾の筋、提げ紐
    pts = [P(-5.0, -4.6, 0), P(-2.6, -1.6, 0), P(0.4, 1.6, 0), P(3.6, 4.4, 0), P(5.4, 5.6, 0)]
    m.add("horn", tub("body", "iron", pts, [2.4, 2.0, 1.6, 1.0, 0.4], None, sides=8))
    m.add("bell", tub("body", "goldl", [P(-5.6, -5.4, 0), P(-4.4, -3.8, 0)], [2.8, 2.6], None, sides=10))
    m.add("mouth", tub("body", "goldl", [P(4.6, 5.0, 0), P(5.8, 5.8, 0)], [0.6, 0.5], None, sides=8))
    for k, t in enumerate((0.3, 0.55)):
        p = pts[1] + (pts[3] - pts[1]) * t
        ring(m, f"band{k}", "goldl", p, 1.9 - k * 0.4, axis="x", w=0.22, n=10)
    m.add("vein", tub("body", "ember", [P(-3.6, -2.6, -1.9), P(-1.0, 0.4, -1.6), P(1.6, 2.8, -1.1)], [0.15, 0.13, 0.1], None, sides=3))
    m.add("strap", tub("body", "leather", [P(-2.4, -1.2, 1.9), P(0, 3.4, 2.0), P(2.6, 3.0, 1.2)], [0.25] * 3, None, sides=4))


def cinder_lamp(m):
    # 燠の小灯: 鉄の丸籠に燠、金の口輪
    for k in range(6):
        a = k / 6 * math.pi
        pts = [P(math.cos(a) * math.sin(t) * 3.4, -math.cos(t) * 3.8, math.sin(a) * math.sin(t) * 3.4) for t in np.linspace(0.15, math.pi - 0.15, 9)]
        m.add(f"rib{k}", tub("body", "iron", pts, [0.25] * 9, None, sides=4))
    m.add("ember", ell("body", "ember", (0, -0.6, 0), (2.0, 1.6, 2.0), segs=8, rings=3))
    m.add("ash", ell("body", "ash", (0, -2.0, 0), (2.4, 0.8, 2.4), segs=8, rings=2))
    ring(m, "rim", "goldl", (0, 3.5, 0), 1.4, axis="y", w=0.25, n=10)
    ring(m, "belt", "goldl", (0, 0, 0), 3.5, axis="y", w=0.2, n=14)
    hang(m, 4.4, "iron")


def mine_forge_ember(m):
    # 地下の炉の巣: 小さな金床の上の熾の塊、鉄の爪で留める
    m.add("anvil", tub("body", "iron", [P(-3.0, -2.6, 0), P(3.0, -2.6, 0)], [1.4, 1.4], [1.8, 1.8], hint=(0, 1, 0), sides=4, phase=45, caps=(True, True)))
    m.add("horn", cone("body", "iron", (3.0, -2.4, 0), (1, 0.15, 0), 2.4, 1.0, sides=6))
    m.add("foot", tub("body", "iron", [P(0, -5.0, 0), P(0, -3.6, 0)], [2.0, 1.2], [1.6, 1.0], hint=(1, 0, 0), sides=4, phase=45, caps=(True, True)))
    m.add("ember", ell("body", "ember", (0, 0.2, 0), (1.8, 1.6, 1.4), segs=8, rings=3))
    for k in range(4):
        a = k / 4 * math.tau + 0.4
        m.add(f"claw{k}", tub("body", "goldl", [P(math.cos(a) * 1.2, -1.4, math.sin(a) * 1.0), P(math.cos(a) * 1.9, 0.6, math.sin(a) * 1.5),
                                                 P(math.cos(a) * 1.0, 1.8, math.sin(a) * 0.8)], [0.2, 0.18, 0.1], None, sides=4))
    hang(m, 2.8, "iron")


def wyrm_ember_scale(m):
    m.add("plate", tub("body", "scale", [P(0, -3.0, 0), P(0, 1.0, 0), P(0, 4.4, 0)], [0.6, 3.6, 2.4], [0.4, 0.6, 0.5], hint=(1, 0, 0), sides=6))
    m.add("rim", tub("body", "silver", [P(-3.2, 1.0, -0.5), P(-1.6, -2.4, -0.5), P(0, -3.4, -0.5), P(1.6, -2.4, -0.5), P(3.2, 1.0, -0.5)], [0.22] * 5, None, sides=4))
    for k, y in enumerate((-1.2, 0.6, 2.4)):
        m.add(f"vein{k}", tub("body", "ember", [P(-1.6 + k * 0.4, y, -0.7), P(0, y + 0.6, -0.7), P(1.6 - k * 0.4, y, -0.7)], [0.15] * 3, None, sides=4))
    m.add("cap", tub("body", "silver", [P(0, 4.2, 0), P(0, 5.0, 0)], [1.2, 0.9], None, sides=8))
    hang(m, 5.8, "silver", links=1)


# ================================================================ 岩鯨 (石と骨と真鍮)
def resonance_charm(m):
    m.add("stone", ell("body", "stone", (0, 1.5, 0), (3.0, 3.4, 0.9), segs=6, rings=2))
    ring(m, "bezel", "brass", (0, 1.5, 0), 3.2, axis="z", w=0.35, n=12)
    for k in range(6):
        a = k / 6 * math.tau
        m.add(f"prong{k}", cone("body", "brass", (math.cos(a) * 3.3, 1.5 + math.sin(a) * 3.6, -0.3), (-math.cos(a), -math.sin(a), -0.5), 1.0, 0.25, sides=4))
    for s in (-1, 1):
        m.add(f"horn{s}", tub("body", "bone", [P(s * 3.0, 2.5, 0), P(s * 5.4, 4.4, 0), P(s * 6.4, 7.6, 0), P(s * 5.6, 8.6, 0)], [0.8, 0.7, 0.45, 0.15], None, sides=6))
        m.add(f"cap{s}", tub("body", "brass", [P(s * 3.0, 2.5, 0), P(s * 3.9, 3.2, 0)], [0.9, 0.9], None, sides=6))
    drops(m, "drop", (-2.2, 2.2), -2.6)
    etch(m, "etch", (0, 1.5, 0), 3.2, 2.2, 5, -0.95)
    hang(m, 5.6, "brass")


def moss_talisman(m):
    # 苔の守り札: 石板に苔の刻み、骨の枠、真鍮の角と玉
    plate_v(m, "slab", "stone", (0, 0, 0), 5.6, 7.0, 0.7, sides=6)
    m.add("moss", ell("body", "moss", (0, 2.4, -0.4), (2.2, 1.2, 0.5), segs=8, rings=2))
    m.add("frame", tub("body", "bone", [P(-3.0, -3.6, -0.3), P(-3.2, 3.6, -0.3), P(0, 4.6, -0.3), P(3.2, 3.6, -0.3), P(3.0, -3.6, -0.3), P(0, -4.4, -0.3),
                                        P(-3.0, -3.6, -0.3)], [0.35] * 7, None, sides=5, caps=(False, False)))
    etch(m, "glyph", (0, -0.8, 0), 2.8, 3.0, 4, -0.75, "teal")
    for k, (x, y) in enumerate(((-3.0, 3.6), (3.0, 3.6), (-3.0, -3.6), (3.0, -3.6))):
        m.add(f"stud{k}", ell("body", "brass", (x, y, -0.5), (0.4, 0.4, 0.3), segs=6, rings=1))
    hang(m, 5.4, "brass")


def stone_whale_plating(m):
    # 岩鯨の甲板の欠け: 縁を真鍮で包んだ岩の板に、肋の浮き彫りと水色の核
    plate_v(m, "plate", "stone", (0, 0, 0), 11.0, 12.0, 1.4, sides=6)
    for k in range(4):
        y = 3.6 - k * 2.4
        m.add(f"rib{k}", tub("body", "bone", [P(-4.0, y - 0.6, -1.3), P(0, y + 0.4, -1.6), P(4.0, y - 0.6, -1.3)], [0.4, 0.5, 0.4], None, sides=5))
    m.add("rim_t", tub("body", "brass", [P(-5.2, 6.0, 0), P(5.2, 6.0, 0)], [0.5, 0.5], [1.6, 1.6], hint=(0, 1, 0), sides=4, phase=45))
    m.add("rim_b", tub("body", "brass", [P(-5.2, -6.0, 0), P(5.2, -6.0, 0)], [0.5, 0.5], [1.6, 1.6], hint=(0, 1, 0), sides=4, phase=45))
    bezel(m, "core", (0, 0.6, -1.4), 1.4, "brass", 6, "light", depth=0.6)
    for s in (-1, 1):
        for y in (-3.0, 0.0, 3.0):
            m.add(f"rivet{s}{y}", ell("body", "brass", (s * 5.4, y, -0.9), (0.45, 0.45, 0.35), segs=6, rings=1))


def pit_jaw_pendant(m):
    # 地下の穴の顎: 骨の顎の弧に歯を並べ、真鍮の留め具と牙の飾り
    pts = [P(math.cos(a) * 4.6, math.sin(a) * 4.6 - 1.0, 0) for a in np.linspace(math.radians(200), math.radians(340), 9)]
    m.add("jaw", tub("body", "bone", pts, [0.9] * 9, [0.7] * 9, hint=(0, 0, 1), sides=6))
    for k, p in enumerate(pts[1:-1]):
        d = np.asarray([0, 0, 0]) - np.asarray(p) + np.asarray([0, -1.0, 0])
        d = d / np.linalg.norm(d)
        m.add(f"tooth{k}", cone("body", "bone", tuple(p), tuple(d), 1.4 - abs(k - 3) * 0.15, 0.35, sides=4))
    for s in (-1, 1):
        m.add(f"clasp{s}", tub("body", "brass", [pts[0 if s < 0 else -1] + P(0, -0.4, 0), pts[0 if s < 0 else -1] + P(0, 0.6, 0)], [1.0, 1.0], None, sides=8))
        chain(m, f"ch{s}", pts[0 if s < 0 else -1] + P(0, 0.8, 0), P(s * 0.9, 4.6, 0), links=6)
    m.add("fang", cone("body", "bone", (0, -5.2, 0), (0, -1, 0), 3.2, 0.7, sides=5))
    m.add("fang_cap", tub("body", "brass", [P(0, -4.6, 0), P(0, -5.4, 0)], [0.8, 0.8], None, sides=8))
    hang(m, 5.2, "brass")


def fort_bone_horn(m):
    # 地下の骨の砦: 骨の角笛に真鍮の口金と帯、吹き口の鎖
    pts = [P(-5.2, -3.4, 0), P(-2.2, -1.2, 0), P(1.0, 1.4, 0), P(4.0, 3.4, 0), P(5.4, 4.4, 0)]
    m.add("horn", tub("body", "bone", pts, [2.0, 1.7, 1.3, 0.8, 0.35], None, sides=8))
    m.add("bell", tub("body", "brass", [P(-5.8, -3.9, 0), P(-4.6, -2.9, 0)], [2.4, 2.2], None, sides=10))
    for k, t in enumerate((0.25, 0.5, 0.75)):
        p = pts[0] + (pts[3] - pts[0]) * t
        ring(m, f"band{k}", "brass", p, 1.8 - k * 0.35, axis="x", w=0.2, n=10)
    m.add("mouth", tub("body", "brass", [P(4.8, 3.9, 0), P(6.0, 4.6, 0)], [0.5, 0.45], None, sides=8))
    chain(m, "ch", (-4.6, -2.0, 0), (5.0, 4.8, 0), links=6)


# ================================================================ 潮殻 (珊瑚と貝と真珠と銀)
def tide_chime_charm(m):
    # 潮の風鈴: 銀の傘に珊瑚の舌、三つの貝の鈴、真珠
    m.add("cap", tub("body", "silver", [P(0, 3.6, 0), P(0, 2.4, 0), P(0, 1.6, 0)], [0.6, 2.6, 3.4], None, sides=12, caps=(True, False)))
    for k, x in enumerate((-2.2, 0, 2.2)):
        y = -1.4 - (0 if k == 1 else 0.8)
        m.add(f"cord{k}", tub("body", "silver", [P(x * 0.6, 1.6, 0), P(x, y + 1.2, 0)], [0.08, 0.08], None, sides=3))
        m.add(f"shell{k}", tub("body", "shell", [P(x, y + 1.2, 0), P(x, y, 0), P(x, y - 1.0, 0)], [0.4, 1.0, 1.2], None, sides=10, caps=(True, False)))
    m.add("tongue", tub("body", "coral", [P(0, 1.6, 0), P(0.4, -2.8, 0), P(-0.2, -4.8, 0)], [0.3, 0.25, 0.15], None, sides=5))
    m.add("pearl", ell("body", "pearl", (-0.2, -5.2, 0), (0.55, 0.55, 0.55), segs=8, rings=3))
    hang(m, 4.4, "silver")


def moontide_pearl(m):
    # 月潮の真珠: 銀の三日月の枠に大きな真珠、珊瑚の枝と小さな真珠の房
    m.add("pearl", ell("body", "pearl", (0, 0.4, 0), (2.6, 2.6, 2.6), segs=12, rings=5))
    pts = [P(math.cos(a) * 3.6, math.sin(a) * 3.6 + 0.4, 0) for a in np.linspace(math.radians(110), math.radians(430), 14)]
    m.add("moon", tub("body", "silver", pts, [0.25 + 0.35 * math.sin(math.pi * k / 13) for k in range(14)], None, sides=5))
    for s in (-1, 1):
        m.add(f"coral{s}", tub("body", "coral", [P(s * 2.8, -2.4, 0.4), P(s * 3.8, -4.4, 0.6), P(s * 3.2, -6.2, 0.4)], [0.4, 0.3, 0.12], None, sides=5))
    drops(m, "drop", (-1.2, 0, 1.2), -4.6, "pearl", 0.35, "silver")
    hang(m, 4.6, "silver", links=1)


def tide_spiral_shell(m):
    # 地下の潮の神殿: 銀の螺旋の帯を巻いた巻き貝、口に真珠
    n = 9
    for k in range(n):
        t = k / (n - 1)
        r = 2.8 * (1 - t) + 0.4
        m.add(f"whorl{k}", ell("body", "shell", (0, -3.6 + t * 9.0, 0), (r, 1.1, r), segs=10, rings=2, ry=k * 30))
    pts = [P(math.cos(t * 9) * (3.0 * (1 - t / 1.1) + 0.5), -3.6 + t * 9.0, math.sin(t * 9) * (3.0 * (1 - t / 1.1) + 0.5)) for t in np.linspace(0, 1, 26)]
    m.add("spiral", tub("body", "silver", pts, [0.2] * 26, None, sides=4))
    m.add("pearl", ell("body", "pearl", (0, -4.2, -1.6), (0.8, 0.8, 0.8), segs=8, rings=3))
    m.add("lip", tub("body", "coral", [P(-2.4, -4.0, -1.2), P(0, -4.8, -2.0), P(2.4, -4.0, -1.2)], [0.35] * 3, None, sides=5))
    hang(m, 6.2, "silver")


def temple_coral_bell(m):
    # 地下の潮の神殿: 珊瑚の枝の柄に銀の鐘、真珠の舌
    m.add("stem", tub("body", "coral", [P(0, 2.0, 0), P(0, 5.6, 0)], [0.45, 0.35], None, sides=6))
    for s in (-1, 1):
        m.add(f"branch{s}", tub("body", "coral", [P(0, 3.6, 0), P(s * 1.8, 5.0, 0), P(s * 2.4, 6.6, 0)], [0.35, 0.25, 0.1], None, sides=5))
    m.add("bell", tub("body", "silver", [P(0, 2.2, 0), P(0, 0.4, 0), P(0, -2.6, 0)], [0.8, 2.2, 3.0], None, sides=12, caps=(True, False)))
    m.add("lip", tub("body", "silver", [P(0, -2.6, 0), P(0, -2.2, 0)], [3.2, 3.1], None, sides=12))
    m.add("band", tub("body", "teal", [P(0, -0.8, 0), P(0, -0.3, 0)], [2.55, 2.45], None, sides=12))
    m.add("tongue", ell("body", "pearl", (0, -3.2, 0), (0.6, 0.6, 0.6), segs=8, rings=3))
    hang(m, 6.6, "silver")


# ================================================================ 野: 草地・丘
def wild_guard_badge(m):
    # 番人の徽章: 革の盾形に真鍮の縁と鋲、交差した槍の刻み
    pts = [P(-2.4, 4.0, 0), P(2.4, 4.0, 0), P(2.4, 0.4, 0), P(0, -4.6, 0), P(-2.4, 0.4, 0), P(-2.4, 4.0, 0)]
    m.add("shield", tub("body", "leather", [P(0, -4.4, 0), P(0, 0.4, 0), P(0, 4.0, 0)], [0.2, 2.5, 2.5], [0.45, 0.45, 0.45], hint=(1, 0, 0), sides=4, phase=45))
    m.add("rim", tub("body", "brass", pts, [0.22] * 6, None, sides=4, caps=(False, False)))
    for s in (-1, 1):
        m.add(f"spear{s}", tub("body", "brass", [P(s * 1.6, -2.4, -0.55), P(-s * 1.4, 2.8, -0.55)], [0.12, 0.12], None, sides=3))
        m.add(f"tip{s}", cone("body", "brass", (-s * 1.4, 2.8, -0.55), (-s * 0.5, 1, 0), 0.8, 0.25, sides=4))
    for k, (x, y) in enumerate(((-1.8, 3.4), (1.8, 3.4), (0, -3.4))):
        m.add(f"stud{k}", ell("body", "brass", (x, y, -0.5), (0.3, 0.3, 0.2), segs=6, rings=1))
    hang(m, 4.9, "brass")


def wild_mossball_netsuke(m):
    # 苔玉の根付: 苔の玉を真鍮の網で包み、紐と木の留め玉
    m.add("ball", ell("body", "moss", (0, 0, 0), (2.2, 2.2, 2.2), segs=10, rings=5))
    ring(m, "net0", "brass", (0, 0, 0), 2.3, axis="y", w=0.12, n=14)
    ring(m, "net1", "brass", (0, 0, 0), 2.3, axis="z", w=0.12, n=14)
    for k in range(3):
        m.add(f"leaf{k}", ell("body", "herb", (math.cos(k * 2.1) * 1.8, 1.6, math.sin(k * 2.1) * 1.8), (0.6, 0.25, 0.4), segs=6, rings=1, ry=k * 60))
    m.add("cord", tub("body", "rope", [P(0, 2.2, 0), P(0, 5.4, 0)], [0.15, 0.15], None, sides=4))
    m.add("toggle", ell("body", "dwood", (0, 5.8, 0), (0.7, 0.6, 0.7), segs=8, rings=2))
    m.add("tassel", cone("body", "clothr", (0, -2.2, 0), (0, -1, 0), 2.4, 0.5, sides=6))


def wild_herb_vial(m):
    # 薬草の小瓶: 硝子の瓶に薬草、真鍮の口と栓、首の札
    m.add("body", ell("body", "glassv", (0, -0.6, 0), (2.0, 2.4, 2.0), segs=12, rings=5))
    m.add("herb", tub("body", "herb", [P(0, -2.4, 0), P(0.4, 0.4, 0), P(-0.3, 1.4, 0)], [0.25, 0.2, 0.1], None, sides=4))
    for k in range(3):
        m.add(f"leaf{k}", ell("body", "herb", (0.3 * (k - 1), -1.2 + k * 0.7, -0.2), (0.5, 0.2, 0.25), segs=6, rings=1, rz=40 * (k - 1)))
    m.add("neck", tub("body", "glassv", [P(0, 1.6, 0), P(0, 2.6, 0)], [0.7, 0.6], None, sides=8))
    m.add("collar", tub("body", "brass", [P(0, 2.5, 0), P(0, 3.0, 0)], [0.8, 0.8], None, sides=8))
    m.add("cork", tub("body", "wood", [P(0, 2.9, 0), P(0, 3.6, 0)], [0.6, 0.65], None, sides=8))
    m.add("tag", tub("body", "paper", [P(0.9, 2.2, -0.6), P(1.5, 0.8, -1.0)], [0.4, 0.4], [0.05, 0.05], hint=(0, 0, 1), sides=4))
    hang(m, 4.3, "brass")


def wild_float_netsuke(m):
    # 浮きの根付: 硝子の浮き玉を縄の網で包み、真鍮の口と紐
    m.add("float", ell("body", "teal", (0, 0, 0), (2.2, 2.2, 2.2), segs=12, rings=5))
    for k in range(3):
        ring(m, f"net{k}", "rope", (0, 0, 0), 2.3, axis="z" if k == 0 else "x" if k == 1 else "y", w=0.14, n=14)
    m.add("cap", tub("body", "brass", [P(0, 2.0, 0), P(0, 2.7, 0)], [0.7, 0.5], None, sides=8))
    m.add("shine", ell("body", "light", (-0.8, 0.9, -1.8), (0.4, 0.3, 0.2), segs=6, rings=1))
    m.add("cord", tub("body", "rope", [P(0, 2.7, 0), P(0, 5.0, 0)], [0.15, 0.15], None, sides=4))
    m.add("toggle", ell("body", "shell", (0, 5.4, 0), (0.7, 0.5, 0.5), segs=8, rings=2))


def wild_wolf_tooth_necklace(m):
    # 狼の牙の首飾り: 革紐に五本の牙と真鍮の座金、真ん中に琥珀
    pts = [P(math.sin(a) * 3.8, -math.cos(a) * 1.6 + 2.0, 0) for a in np.linspace(-1.4, 1.4, 11)]
    m.add("cord", tub("body", "leather", [P(-3.6, 4.6, 0)] + pts + [P(3.6, 4.6, 0)], [0.18] * 13, None, sides=4))
    for k in range(5):
        a = -0.9 + k * 0.45
        p = P(math.sin(a) * 3.8, -math.cos(a) * 1.6 + 2.0, 0)
        if k == 2:
            bezel(m, "amber", p + P(0, -0.6, -0.1), 0.6, "brass", 4, "amber", depth=0.4)
            continue
        m.add(f"cap{k}", tub("body", "brass", [p, p + P(0, -0.6, 0)], [0.45, 0.4], None, sides=6))
        m.add(f"tooth{k}", cone("body", "bone", tuple(p + P(0, -0.6, 0)), (math.sin(a) * 0.3, -1, 0), 2.6 - abs(k - 2) * 0.3, 0.42, sides=5))


def wild_ramhorn_bugle(m):
    # 羊角の喇叭: 巻いた羊の角に真鍮の吹き口と口金、紐
    pts = [P(math.cos(a) * (2.6 - a * 0.18), math.sin(a) * (2.6 - a * 0.18), a * 0.25) for a in np.linspace(0, 4.4, 14)]
    m.add("horn", tub("body", "bone", pts, [1.4 - k * 0.07 for k in range(14)], None, sides=8))
    for k in range(5):
        ring(m, f"ridge{k}", "bone", pts[2 + k * 2], 1.35 - k * 0.12, axis="x", w=0.12, n=8)
    m.add("bell", tub("body", "brass", [pts[0] + P(0, -0.6, 0), pts[0] + P(0, 0.4, 0)], [1.8, 1.6], None, sides=10))
    m.add("mouth", tub("body", "brass", [pts[-1], pts[-1] + P(0.6, 0.8, 0.2)], [0.4, 0.35], None, sides=8))
    m.add("strap", tub("body", "leather", [pts[1] + P(0, 0, -1.2), P(0, 4.6, 0), pts[8] + P(0, 0, -1.0)], [0.2] * 3, None, sides=4))


# ================================================================ 野: 浜・高地・荒れ地
def wild_crab_backplate(m):
    # 蟹の背甲: 赤い甲羅の板に銀の縁と鋲、棘
    m.add("shell", ell("body", "red", (0, 0, 0), (5.2, 4.2, 1.2), segs=12, rings=4))
    m.add("rim", tub("body", "silver", [P(math.cos(a) * 5.3, math.sin(a) * 4.3, -0.2) for a in np.linspace(0, math.tau, 17)], [0.22] * 17, None, sides=4,
                     caps=(False, False)))
    for k in range(6):
        a = k / 6 * math.tau + 0.3
        m.add(f"spike{k}", cone("body", "red", (math.cos(a) * 5.0, math.sin(a) * 4.0, 0), (math.cos(a), math.sin(a), -0.2), 1.4, 0.4, sides=4))
    for k in range(3):
        m.add(f"ridge{k}", tub("body", "red", [P(-3.0 + k * 0.5, -1.4 + k * 1.4, -1.0), P(3.0 - k * 0.5, -1.4 + k * 1.4, -1.0)], [0.25, 0.25], None, sides=4))
    for s in (-1, 1):
        m.add(f"strap{s}", tub("body", "leather", [P(s * 3.6, 2.4, 0.6), P(s * 5.6, 3.6, 1.4)], [0.3, 0.3], None, sides=4))


def wild_fog_handbell(m):
    # 霧の手鐘: 木の柄に真鍮の鐘、白い霧の紐
    m.add("grip", tub("body", "dwood", [P(0, 1.4, 0), P(0, 5.6, 0)], [0.45, 0.5], None, sides=8))
    m.add("knob", ell("body", "dwood", (0, 5.9, 0), (0.6, 0.5, 0.6), segs=8, rings=2))
    m.add("bell", tub("body", "brass", [P(0, 1.6, 0), P(0, -0.4, 0), P(0, -3.6, 0)], [0.7, 1.8, 2.6], None, sides=12, caps=(True, False)))
    m.add("lip", tub("body", "brass", [P(0, -3.6, 0), P(0, -3.2, 0)], [2.75, 2.7], None, sides=12))
    m.add("band", tub("body", "navy", [P(0, -1.6, 0), P(0, -1.1, 0)], [2.15, 2.05], None, sides=12))
    m.add("tongue", ell("body", "iron", (0, -3.8, 0), (0.4, 0.4, 0.4), segs=6, rings=2))
    m.add("ribbon", tub("body", "paper", [P(0.5, 1.6, -0.5), P(1.4, 0.0, -1.2), P(1.0, -2.2, -1.6)], [0.3, 0.3, 0.2], [0.06] * 3, hint=(0, 0, 1), sides=4))


def wild_weave_bracelet(m):
    # 編みの腕輪 (横に寝た輪): 紺と白の糸を編み、銀の留め具と小石
    for k in range(3):
        pts = [P(math.cos(a) * 5.0, 0.6 * math.sin(a * 8 + k * 2.1), math.sin(a) * 5.0) for a in np.linspace(0, math.tau, 33)]
        m.add(f"strand{k}", tub("body", "navy" if k != 1 else "paper", pts, [0.32] * 33, None, sides=4, caps=(False, False)))
    m.add("clasp", tub("body", "silver", [P(-0.8, 0, -5.0), P(0.8, 0, -5.0)], [0.6, 0.6], [0.9, 0.9], hint=(0, 1, 0), sides=6))
    bezel(m, "stone", (0, 0, -5.6), 0.5, "silver", 4, "teal", depth=0.3)


def wild_emberbug_amber(m):
    # 熾虫の琥珀: 琥珀の雫に閉じた小さな虫、金の爪と口金
    m.add("amber", ell("body", "amber", (0, -0.6, 0), (2.0, 2.8, 1.6), segs=10, rings=5))
    m.add("bug", ell("body", "iron", (0, -0.6, -0.2), (0.5, 0.9, 0.4), segs=6, rings=2))
    for s in (-1, 1):
        m.add(f"wing{s}", ell("body", "ember", (s * 0.6, -0.2, -0.3), (0.5, 0.3, 0.1), segs=6, rings=1, rz=s * 30))
    for k in range(4):
        a = k / 4 * math.tau + 0.4
        m.add(f"claw{k}", tub("body", "goldl", [P(0, 2.0, 0), P(math.cos(a) * 1.8, 1.0, math.sin(a) * 1.3), P(math.cos(a) * 1.6, -0.2, math.sin(a) * 1.2)],
                              [0.18, 0.15, 0.08], None, sides=4))
    m.add("cap", tub("body", "goldl", [P(0, 1.8, 0), P(0, 2.6, 0)], [0.9, 0.6], None, sides=8))
    hang(m, 3.4, "goldl")


def wild_ash_talisman(m):
    # 灰の守り札: 煤けた布の札を鉄の枠に張り、白い文字と三本の房
    plate_v(m, "cloth", "ash", (0, 0, 0), 3.4, 9.0, 0.25, sides=4)
    m.add("frame", tub("body", "iron", [P(-1.9, 4.8, 0), P(1.9, 4.8, 0)], [0.35, 0.35], None, hint=(0, 1, 0), sides=6))
    m.add("frame_b", tub("body", "iron", [P(-1.9, -4.8, 0), P(1.9, -4.8, 0)], [0.3, 0.3], None, hint=(0, 1, 0), sides=6))
    for k in range(4):
        m.add(f"glyph{k}", tub("body", "paper", [P(-0.7, 2.8 - k * 2.0, -0.3), P(0.6, 2.2 - k * 2.0, -0.3)], [0.14, 0.12], None, sides=3))
    for k in range(3):
        m.add(f"tassel{k}", cone("body", "clothr", (-1.2 + k * 1.2, -5.1, 0), (0, -1, 0), 1.6, 0.25, sides=4))
    hang(m, 5.8, "iron")


def wild_signal_fire(m):
    # のろしの火口: 鉄の小さな火籠に熾、真鍮の蓋の鎖
    m.add("cup", tub("body", "iron", [P(0, -4.0, 0), P(0, -1.0, 0), P(0, 2.0, 0)], [1.4, 1.8, 2.0], None, sides=8, caps=(True, False)))
    for k in range(6):
        a = k / 6 * math.tau
        m.add(f"slot{k}", tub("body", "ember", [P(math.cos(a) * 1.85, -2.4, math.sin(a) * 1.85), P(math.cos(a) * 1.95, 0.6, math.sin(a) * 1.95)], [0.12, 0.12], None,
                              sides=3))
    m.add("ember", ell("body", "ember", (0, 1.8, 0), (1.6, 0.8, 1.6), segs=8, rings=2))
    for k in range(3):
        a = k / 3 * math.tau
        m.add(f"flame{k}", cone("body", "magma", (math.cos(a) * 0.6, 2.0, math.sin(a) * 0.6), (0, 1, 0), 2.2 - k * 0.3, 0.5, sides=5))
    for y in (-3.4, 1.6):
        ring(m, f"band{y}", "brass", (0, y, 0), 1.6 if y < 0 else 2.05, axis="y", w=0.2, n=10)
    m.add("leg", tub("body", "iron", [P(0, -4.0, 0), P(0, -4.6, 0)], [0.8, 1.2], None, sides=8))


def wild_plumb_line_ring(m):
    # 錘糸の指輪: 銀の輪に糸を巻き、下に真鍮の錘
    ring(m, "band", "silver", (0, 2.4, 0), 2.4, axis="z", w=0.5, n=16)
    for k in range(5):
        a = k / 5 * math.pi + 0.3
        ring(m, f"wind{k}", "paper", (math.cos(a) * 2.4, 2.4 + math.sin(a) * 2.4, 0), 0.55, axis="x" if k % 2 else "y", w=0.1, n=8)
    m.add("line", tub("body", "paper", [P(0, 0.0, 0), P(0, -2.6, 0)], [0.06, 0.06], None, sides=3))
    m.add("bob", tub("body", "brass", [P(0, -2.6, 0), P(0, -3.6, 0), P(0, -4.6, 0)], [0.3, 0.8, 0.05], None, sides=8))
    bezel(m, "stone", (0, 4.9, 0), 0.6, "silver", 4, "navy", depth=0.5)


def wild_salttear_earring(m):
    # 塩の涙の耳飾り: 銀の鉤に塩の晶の雫、小さな真珠
    m.add("hook", tub("body", "silver", [P(0.6, 1.8, 0), P(0.6, 2.8, 0), P(0, 3.2, 0), P(-0.6, 2.6, 0)], [0.12] * 4, None, sides=4))
    m.add("cap", tub("body", "silver", [P(0, 1.8, 0), P(0, 1.1, 0)], [0.25, 0.6], None, sides=8))
    m.add("tear", ell("body", "cryst", (0, -0.8, 0), (0.95, 1.8, 0.95), segs=8, rings=4))
    m.add("tip", cone("body", "cryst", (0, -2.2, 0), (0, -1, 0), 0.9, 0.5, sides=6))
    m.add("pearl", ell("body", "pearl", (0, 1.4, -0.5), (0.3, 0.3, 0.3), segs=6, rings=2))


def wild_bonepipe_necklace(m):
    # 骨笛の首飾り: 革紐に長短の骨の管、真鍮の帯
    pts = [P(math.sin(a) * 3.4, -math.cos(a) * 1.4 + 2.4, 0) for a in np.linspace(-1.3, 1.3, 11)]
    m.add("cord", tub("body", "leather", [P(-3.2, 4.0, 0)] + pts + [P(3.2, 4.0, 0)], [0.15] * 13, None, sides=4))
    for k in range(5):
        a = -0.8 + k * 0.4
        p = P(math.sin(a) * 3.4, -math.cos(a) * 1.4 + 2.4, 0)
        ln = 3.6 - abs(k - 2) * 0.7
        m.add(f"pipe{k}", tub("body", "bone", [p, p + P(0, -ln, 0)], [0.38, 0.34], None, sides=6))
        m.add(f"hole{k}", ell("body", "night", tuple(p + P(0, -ln * 0.5, -0.36)), (0.12, 0.12, 0.05), segs=4, rings=1))
        ring(m, f"band{k}", "brass", p + P(0, -0.3, 0), 0.42, axis="y", w=0.08, n=8)


def wild_hangman_knot(m):
    # 吊り縄の結び: 縄の輪と巻き結び、銀の留め具、淵の紫の雫
    ring(m, "loop", "rope", (0, 3.6, 0), 2.0, axis="z", w=0.4, n=14)
    for k in range(6):
        ring(m, f"coil{k}", "rope", (0, 1.2 - k * 0.55, 0), 0.65, axis="y", w=0.28, n=10)
    m.add("tail", tub("body", "rope", [P(0, -2.2, 0), P(0.4, -4.4, 0), P(0.1, -5.6, 0)], [0.35, 0.3, 0.2], None, sides=5))
    m.add("clasp", tub("body", "silver", [P(0, 1.7, 0), P(0, 2.2, 0)], [0.55, 0.55], None, sides=8))
    m.add("drop", ell("body", "violet", (0.1, -6.2, 0), (0.45, 0.65, 0.45), segs=6, rings=2))


def wild_lamp_charm(m):
    # 灯の御守り (淵の上層): 真鍮の小さな角灯、硝子の灯、透かしの笠
    m.add("base", tub("body", "brass", [P(0, -3.4, 0), P(0, -2.6, 0)], [1.6, 1.5], None, sides=8, caps=(True, True)))
    m.add("glass", tub("body", "lantern", [P(0, -2.6, 0), P(0, 1.8, 0)], [1.2, 1.2], None, sides=8))
    m.add("flame", ell("body", "ember", (0, -0.6, 0), (0.4, 0.8, 0.4), segs=6, rings=2))
    for k in range(4):
        a = k / 4 * math.tau + math.pi / 4
        m.add(f"bar{k}", tub("body", "brass", [P(math.cos(a) * 1.3, -2.6, math.sin(a) * 1.3), P(math.cos(a) * 1.3, 1.8, math.sin(a) * 1.3)], [0.15, 0.15], None, sides=4))
    m.add("roof", cone("body", "brass", (0, 1.8, 0), (0, 1, 0), 1.6, 1.8, sides=8))
    hang(m, 4.0, "brass")


def wild_mimic_heart_charm(m):
    m.add("gem", ell("body", "heart", (0, 1.0, 0), (2.0, 2.3, 1.2), segs=8, rings=3))
    for k in range(6):
        a = k / 6 * math.pi + math.pi / 12
        m.add(f"bar{k}", tub("body", "goldl", [P(math.cos(a) * 2.2, 1.0 + math.sin(a) * 2.5, 0), P(math.cos(a) * 1.4, 1.0 - math.sin(a) * 2.4, -0.3)], [0.18, 0.15],
                             None, sides=4))
    for k in range(7):
        x = -1.8 + k * 0.6
        m.add(f"tooth{k}", cone("body", "edgew", (x, 2.6 - abs(x) * 0.4, -0.9), (0, -1, -0.2), 0.8, 0.2, sides=4))
    hang(m, 4.0, "goldl")


def wild_fulgurite_necklace(m):
    # 雷管石の首飾り: 枝分かれした雷の砂の石を銀の座で吊る、革紐
    m.add("cord", tub("body", "leather", [P(-3.4, 4.0, 0), P(-1.6, 2.2, 0), P(0, 1.6, 0), P(1.6, 2.2, 0), P(3.4, 4.0, 0)], [0.15] * 5, None, sides=4))
    m.add("mount", tub("body", "silver", [P(0, 1.6, 0), P(0, 0.8, 0)], [0.5, 0.6], None, sides=8))
    m.add("stem", tub("body", "stone", [P(0, 0.8, 0), P(0.3, -1.6, 0), P(-0.2, -3.6, 0)], [0.5, 0.45, 0.3], None, sides=6))
    for s, y in ((-1, -1.0), (1, -2.0), (-1, -2.8)):
        m.add(f"br{y}", tub("body", "stone", [P(0, y, 0), P(s * 1.2, y - 0.8, 0), P(s * 1.6, y - 1.8, 0)], [0.3, 0.2, 0.1], None, sides=5))
    m.add("glass", tub("body", "cryst", [P(0.3, -1.6, -0.45), P(-0.2, -3.4, -0.35)], [0.15, 0.1], None, sides=4))


def wild_ash_hourglass_charm(m):
    # 灰の砂時計: 鉄の上下の板と三本の柱、灰の砂
    for s in (-1, 1):
        m.add(f"plate{s}", tub("body", "iron", [P(0, s * 3.4, 0), P(0, s * 3.9, 0)], [1.5, 1.5], None, sides=8, caps=(True, True)))
        m.add(f"knob{s}", ell("body", "brass", (0, s * 4.2, 0), (0.4, 0.3, 0.4), segs=6, rings=1))
    for k in range(3):
        a = k / 3 * math.tau
        m.add(f"post{k}", tub("body", "brass", [P(math.cos(a) * 1.2, -3.4, math.sin(a) * 1.2), P(math.cos(a) * 1.2, 3.4, math.sin(a) * 1.2)], [0.15, 0.15], None, sides=5))
    m.add("up", ell("body", "glassv", (0, 1.6, 0), (0.95, 1.6, 0.95), segs=10, rings=4))
    m.add("low", ell("body", "glassv", (0, -1.6, 0), (0.95, 1.6, 0.95), segs=10, rings=4))
    m.add("sand", ell("body", "ash", (0, -2.4, 0), (0.8, 0.8, 0.8), segs=8, rings=2))
    hang(m, 4.9, "iron")


def ice_bear_fang(m):
    # 地下の氷の巣: 大きな熊の牙に銀の口金と氷の晶
    m.add("fang", tub("body", "bone", [P(0, 2.0, 0), P(0.4, -1.6, 0), P(1.0, -4.6, 0), P(1.6, -6.4, 0)], [1.3, 1.1, 0.6, 0.05], None, sides=8))
    m.add("cap", tub("body", "silver", [P(0, 1.8, 0), P(0, 3.4, 0)], [1.4, 1.2], None, sides=10))
    etch(m, "etch", (0, 2.6, -1.35), 2.0, 1.0, 4, 0, "teal")
    for k in range(3):
        a = k / 3 * math.tau
        m.add(f"ice{k}", cone("body", "cryst", (math.cos(a) * 1.0, 3.4, math.sin(a) * 1.0), (math.cos(a) * 0.4, 1, math.sin(a) * 0.4), 1.4, 0.35, sides=5))
    hang(m, 4.4, "silver")


ITEMS = ["storm_charm", "storm_anklet", "storm_eye_feather", "kindling_charm", "ember_mantle", "ember_lantern", "ember_horn", "cinder_lamp",
         "resonance_charm", "moss_talisman", "stone_whale_plating", "tide_chime_charm", "moontide_pearl",
         "wild_guard_badge", "wild_mossball_netsuke", "wild_herb_vial", "wild_float_netsuke", "wild_wolf_tooth_necklace", "wild_crab_backplate",
         "wild_fog_handbell", "wild_weave_bracelet", "wild_emberbug_amber", "wild_ramhorn_bugle", "wild_ash_talisman", "wild_signal_fire",
         "wild_plumb_line_ring", "wild_salttear_earring", "wild_bonepipe_necklace", "wild_hangman_knot", "wild_lamp_charm", "wild_mimic_heart_charm",
         "wild_fulgurite_necklace", "wild_ash_hourglass_charm",
         "pit_jaw_pendant", "tide_spiral_shell", "storm_crystal_shard", "mine_forge_ember", "fort_bone_horn", "temple_coral_bell", "ice_bear_fang",
         "wyrm_ember_scale"]


def old_frame(iid):
    """今のメッシュの段ごとの (パス, 中心, いちばん長い辺)"""
    out = []
    for p in sorted((LP / iid).glob("*.json")):
        d = json.loads(p.read_text(encoding="utf-8"))
        V = np.concatenate([np.asarray(b["vertices"], float) for b in d["bones"].values() if b.get("vertices")])
        lo, hi = V.min(0), V.max(0)
        out.append((p, d, (lo + hi) / 2, float(np.max(hi - lo))))
    return out


def build(iid, center, ext):
    m = Mount("acc_" + iid, iid)
    m.bone("body", "root", (0, 0, 0))
    globals()[iid](m)
    V = np.concatenate([f.verts for f in m.F])
    lo, hi = V.min(0), V.max(0)
    s = ext / max(1e-6, float(np.max(hi - lo)))
    c0 = (lo + hi) / 2
    for f in m.F:
        f.verts = (f.verts - c0) * s + center
        if getattr(f, "ref", None) is not None:
            f.ref = (np.asarray(f.ref, float) - c0) * s + center
    m.pal = dict(mats=amats(), glow_mats=AGLOW)
    m.detail = None
    m.hint = 128
    return m


def _tidy(data):
    """書き出す前の仕上げ: 開いた縁・向きの食い違いがある骨だけ向きをそろえて蓋で塞ぐ (tools/lib/mesh_close.tidy_bones。2026-11-15 の監査)"""
    import importlib.util
    spec = importlib.util.spec_from_file_location("mesh_close", Path(__file__).resolve().parents[3] / "lib/mesh_close.py")
    mc = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mc)
    mc.tidy_bones(data)


def install(path, d, m, size, atlas, glow):
    work = KT.ROOT / "build/accessories_v3" / path.parent.name
    work.mkdir(parents=True, exist_ok=True)
    blp.export_named(m, path.stem, size, atlas, glow, work)
    mesh = json.loads((work / f"{path.stem}_mesh.json").read_text(encoding="utf-8"))
    nb = next(b for b in mesh["bones"].values() if b.get("vertices"))
    bone = next(k for k, b in d["bones"].items() if b.get("vertices"))
    ob = d["bones"][bone]
    ob["vertices"], ob["uvs"], ob["triangles"] = nb["vertices"], nb["uvs"], nb["triangles"]
    ob["triangle_count"] = len(nb["triangles"])
    d["triangle_count"] = ob["triangle_count"]
    d["texture_size"] = [atlas.shape[1], atlas.shape[0]]
    for k in ("texels_per_unit", "uv_inset_texels"):
        if k in mesh:
            d[k] = mesh[k]
    d["skyabyss_closed"] = True
    _tidy(d)
    path.write_bytes(json.dumps(d, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
    tex = TEX / (d["texture"] + ".png")
    tex.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(atlas).save(tex)
    gp = TEX / (d["texture"] + "_glow.png")
    if glow.any():
        g = np.zeros_like(atlas)
        g[glow] = atlas[glow]
        Image.fromarray(g).save(gp)
    elif gp.exists():
        gp.unlink()
    return ob["triangle_count"]


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    ids = args or ITEMS
    tiles = []
    for iid in ids:
        for path, d, center, ext in old_frame(iid):
            m = build(iid, center, ext)
            size, atlas, glow = blp.paint_mount(m, m.pal, m.detail, m.hint)
            tris = sum(len(f.verts) - 2 for f in m.F)
            if "--no-install" not in sys.argv:
                tris = install(path, d, m, size, atlas, glow)
            lo = np.min([f.verts.min(0) for f in m.F], axis=0)
            hi = np.max([f.verts.max(0) for f in m.F], axis=0)
            sc = 200 / max(1.0, float(np.max(hi - lo)))
            tiles.append((f"{iid} {path.stem}", tris, view(m.F, atlas, glow, 200, 10, (240, 240), (lo + hi) / 2, sc, ground=False)))
            print(iid, path.stem, tris, flush=True)
    RCT.OUTD.mkdir(parents=True, exist_ok=True)
    per, cols = 24, 6
    for k in range(0, len(tiles), per):
        chunk = tiles[k:k + per]
        rows = (len(chunk) + cols - 1) // cols
        cv = Image.new("RGB", (cols * 250 + 10, rows * 270 + 10), (16, 16, 22))
        dr = ImageDraw.Draw(cv)
        for i, (label, tris, img) in enumerate(chunk):
            x, y = 10 + (i % cols) * 250, 10 + (i // cols) * 270
            dr.text((x, y), f"{label[:28]} {tris}", font=voxel.font(12), fill=(150, 232, 255))
            cv.paste(img, (x, y + 22))
        cv.save(RCT.OUTD / f"acc_{k // per + 1:02d}.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
