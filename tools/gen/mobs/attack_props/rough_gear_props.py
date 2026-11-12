#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""敵の武器と NPC の持ち物の作り直しの案 A/B/C (2026-11-12 の全モデルの確かめで、ラフ止まりと分かった組。声「敵の武器とNPCの小物」)。
代表の 10 品を三つの直し方で作り、絵だけ出す (ゲームには入れない)。

    python -X utf8 -I tools/gen/mobs/attack_props/rough_gear_props.py

  A 作り込み     今の形のまま、部品を分けて作り込む (継ぎ目・金具・巻き・すり減り)。色は落ち着いた素材の色
  B 持ち主の印   A の作り込みに、持ち主の印を足す (鯨の骨とフジツボ、雷の真鍮、熾の赤熱、町の人は自分の服の色と紋)
  C 大ぶり       頭を大きく柄を太く、遠くから形で読める誇張。細かい部品は少なく、色の塊ではっきり
絵は docs/review/gear_props/rough_<a|b|c>.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build_skill_kit as KT  # noqa: E402
from build_skill_kit import *  # noqa: E402,F401,F403

REVIEW_G = KT.ROOT / "docs/review/gear_props"

GLASSG = [(30, 90, 70), (60, 140, 110), (120, 200, 170), (200, 240, 225)]
LEATHER = [(50, 30, 20), (84, 54, 34), (120, 82, 52), (160, 118, 80)]
DWOOD = [(46, 30, 20), (72, 48, 30), (100, 70, 44), (132, 96, 62)]
CLOTHR = [(90, 24, 24), (140, 40, 36), (186, 70, 60), (220, 120, 100)]
CLOTHB = [(24, 36, 70), (40, 60, 110), (70, 96, 150), (120, 146, 196)]
BRASS = [(120, 84, 30), (170, 124, 44), (214, 170, 74), (246, 216, 130)]
BARN = [(110, 104, 92), (150, 144, 128), (190, 184, 166), (226, 222, 206)]
WAX = [(110, 20, 24), (160, 34, 36), (204, 64, 60), (236, 120, 110)]
STEELD = [(50, 56, 66), (84, 92, 104), (130, 138, 150), (190, 196, 206)]
HERB = [(30, 70, 30), (50, 110, 44), (90, 150, 70), (150, 196, 110)]
YARN = [(150, 90, 40), (196, 130, 60), (230, 176, 100), (250, 220, 160)]
FACE = [(190, 160, 130), (220, 192, 160), (240, 216, 188), (252, 236, 214)]
STORMG = [(160, 130, 20), (220, 190, 40), (250, 230, 90), (255, 250, 190)]


def gmats():
    m = KT.kmats()
    m.update({"glassg": M_(GLASSG, base=0.55, lit=0.4, noise=0.0), "leather": M_(LEATHER, lit=0.32, streak=0.05), "dwood": M_(DWOOD, lit=0.32, streak=0.1),
              "clothr": M_(CLOTHR, lit=0.3, streak=0.04), "clothb": M_(CLOTHB, lit=0.3, streak=0.04), "brass": M_(BRASS, lit=0.46),
              "barn": M_(BARN, lit=0.36), "wax": M_(WAX, lit=0.36), "steeld": M_(STEELD, lit=0.46), "herb": M_(HERB, lit=0.32),
              "yarn": M_(YARN, lit=0.3, streak=0.08), "face": M_(FACE, lit=0.3, noise=0.0), "stormg": M_(STORMG, base=0.85, lit=0.1, min=1)})
    return m


GGLOW = set(KT.KGLOW) | {"stormg"}


def ring(m, nm, mat, c, r, axis="y", w=0.4, n=14):
    c = np.asarray(c, float)
    pts = []
    for k in range(n + 1):
        a = k / n * math.tau
        if axis == "y":
            pts.append(c + P(math.cos(a) * r, 0, math.sin(a) * r))
        elif axis == "z":
            pts.append(c + P(math.cos(a) * r, math.sin(a) * r, 0))
        else:
            pts.append(c + P(0, math.cos(a) * r, math.sin(a) * r))
    m.add(nm, tub("body", mat, pts, [w] * (n + 1), None, hint=(0, 1, 0) if axis != "y" else (1, 0, 0), sides=4, caps=(False, False)))


def wraps(m, nm, mat, y0, y1, r, n):
    for k in range(n):
        y = y0 + (y1 - y0) * (k + 0.5) / n
        m.add(f"{nm}{k}", tub("body", mat, [P(0, y - 0.35, 0), P(0, y + 0.35, 0)], [r, r], None, sides=8, caps=(True, True)))


# ================================================================ 敵の武器 (立てて描く。上 = 穂先)
def pike(m, v):
    # 鯨骨の隊長の槍斧: 柄・斧の刃・穂先・石突
    if v == "c":
        m.add("haft", tub("body", "dwood", [P(0, 0, 0), P(0, 44, 0)], [1.6, 1.4], None, sides=8))
        m.add("blade", tub("body", "bone", [P(0, 34, 0), P(7, 38, 0), P(11, 44, 0)], [5, 6, 2], [1.4, 1.2, 0.6], hint=(0, 0, 1), sides=4, phase=45))
        m.add("tip", cone("body", "bone", (0, 44, 0), (0, 1, 0), 10, 2.4, sides=4))
        m.add("cap", ell("body", "iron", (0, 0, 0), (2.4, 2.0, 2.4), segs=8, rings=2))
        return
    bone = "bone"
    m.add("haft", tub("body", "dwood" if v == "a" else "bone", [P(0, 2, 0), P(0, 46, 0)], [0.9, 0.8], None, sides=8))
    if v == "b":
        for k in range(9):  # 椎骨の継ぎ
            m.add(f"vert{k}", ell("body", "bone", (0, 6 + k * 4.4, 0), (1.3, 0.8, 1.3), segs=8, rings=2))
        for k, (y, a) in enumerate(((12, 0.4), (20, 2.2), (27, 4.0))):
            m.add(f"barn{k}", cone("body", "barn", (math.cos(a) * 1.0, y, math.sin(a) * 1.0), (math.cos(a), 0.3, math.sin(a)), 1.2, 0.8, sides=5))
    wraps(m, "grip", "leather", 14, 22, 1.1, 5)
    m.add("socket", tub("body", "iron", [P(0, 33, 0), P(0, 38, 0)], [1.3, 1.1], None, sides=8))
    m.add("blade", tub("body", bone, [P(0.5, 33.5, 0), P(5, 34, 0), P(8.4, 37, 0), P(9.6, 41, 0)], [2.6, 4.2, 4.4, 1.2], [0.5, 0.45, 0.4, 0.2],
                       hint=(0, 0, 1), sides=4, phase=45))
    m.add("edge", tub("body", "steeld", [P(8.6, 34.4, 0), P(10.2, 38, 0), P(10, 41.6, 0)], [0.5, 0.5, 0.3], None, hint=(0, 0, 1), sides=4))
    m.add("hook", tub("body", bone, [P(-0.5, 35, 0), P(-3.6, 36.4, 0), P(-4.8, 34.4, 0)], [0.9, 0.7, 0.3], None, hint=(0, 0, 1), sides=4))
    m.add("tip", cone("body", "steeld" if v == "a" else "bone", (0, 38, 0), (0, 1, 0), 9, 1.4, sides=4))
    m.add("butt", cone("body", "iron", (0, 2.5, 0), (0, -1, 0), 3, 1.0, sides=6))
    if v == "b":
        m.add("tassel", tub("body", "clothb", [P(0, 32.5, 0), P(-1.2, 29, 0.6), P(-1.6, 25, 0.4)], [0.5, 0.5, 0.3], None, sides=4))


def glaive(m, v):
    # 雷翼の鉤の薙刀: 柄・反った刃・鉤
    if v == "c":
        m.add("haft", tub("body", "dwood", [P(0, 0, 0), P(0, 40, 0)], [1.5, 1.3], None, sides=8))
        m.add("blade", tub("body", "steeld", [P(0, 36, 0), P(3, 46, 0), P(1, 56, 0)], [4.4, 4.0, 0.6], [1.0, 0.9, 0.3], hint=(0, 0, 1), sides=4, phase=45))
        m.add("hook", tub("body", "steeld", [P(0, 38, 0), P(-6, 41, 0), P(-6, 36, 0)], [1.6, 1.2, 0.4], None, hint=(0, 0, 1), sides=4))
        return
    m.add("haft", tub("body", "dwood", [P(0, 0, 0), P(0, 40, 0)], [0.8, 0.75], None, sides=8))
    wraps(m, "grip", "leather" if v == "a" else "clothb", 10, 18, 1.0, 4)
    m.add("collar", tub("body", "brass" if v == "b" else "iron", [P(0, 38, 0), P(0, 41, 0)], [1.2, 1.4], None, sides=8))
    pts = [P(0, 40, 0), P(1.6, 45, 0), P(2.4, 50, 0), P(1.6, 55, 0), P(-0.4, 59, 0)]
    m.add("blade", tub("body", "steeld", pts, [2.2, 2.6, 2.4, 1.6, 0.3], [0.45, 0.4, 0.35, 0.3, 0.15], hint=(0, 0, 1), sides=4, phase=45))
    m.add("fuller", tub("body", "iron", [P(0.9, 43, -0.3), P(1.6, 50, -0.3), P(0.6, 56, -0.3)], [0.35, 0.35, 0.2], None, hint=(0, 0, 1), sides=4))
    m.add("hook", tub("body", "steeld", [P(-0.6, 41.5, 0), P(-4.2, 43.5, 0), P(-5.4, 41, 0), P(-4.6, 39.4, 0)], [1.0, 0.8, 0.5, 0.2], None, hint=(0, 0, 1), sides=4))
    m.add("butt", cone("body", "iron", (0, 0.5, 0), (0, -1, 0), 2.5, 0.9, sides=6))
    if v == "b":
        for k, y in enumerate((44, 48, 52)):  # 刃に走る雷の筋
            m.add(f"bolt{k}", tub("body", "stormg", [P(0.4, y, -0.5), P(1.6, y + 1.2, -0.5), P(1.0, y + 2.6, -0.5)], [0.25, 0.25, 0.2], None, hint=(0, 0, 1), sides=4))
        m.add("feather", ell("body", "feath", (0, 36.5, 1.2), (0.6, 2.4, 0.3), segs=6, rings=2, rx=20))


def hammer(m, v):
    # 灰の鍛冶の大槌: 柄・頭・楔
    if v == "c":
        m.add("haft", tub("body", "dwood", [P(0, 0, 0), P(0, 34, 0)], [1.6, 1.5], None, sides=8))
        m.add("head", tub("body", "iron", [P(-9, 36, 0), P(9, 36, 0)], [5.4, 5.4], [5.0, 5.0], hint=(0, 1, 0), sides=4, phase=45, caps=(True, True)))
        m.add("hot", ell("body", "ember", (9.6, 36, 0), (0.8, 4.0, 3.8), segs=8, rings=2))
        return
    m.add("haft", tub("body", "dwood", [P(0, 0, 0), P(0, 34, 0)], [0.9, 0.8], None, sides=8))
    wraps(m, "grip", "leather", 3, 12, 1.05, 5)
    m.add("head", tub("body", "iron", [P(-6, 36, 0), P(6, 36, 0)], [3.4, 3.4], [3.0, 3.0], hint=(0, 1, 0), sides=6, phase=30, caps=(True, True)))
    for s in (-1, 1):
        m.add(f"face{s}", tub("body", "steeld", [P(s * 6, 36, 0), P(s * 7.4, 36, 0)], [3.0, 2.6], None, hint=(0, 1, 0), sides=6, phase=30, caps=(False, True)))
    m.add("band", tub("body", "iron", [P(0, 32.4, 0), P(0, 39.8, 0)], [1.3, 1.3], None, sides=8))
    m.add("wedge", ell("body", "dwood", (0, 39.6, 0), (0.8, 0.5, 0.8), segs=6, rings=1))
    if v == "b":
        m.add("glow", ell("body", "ember", (7.5, 36, 0), (0.5, 2.6, 2.2), segs=8, rings=2))
        for k in range(4):
            a = k / 4 * math.tau
            m.add(f"crack{k}", tub("body", "ember", [P(-4 + k * 2.4, 36 + math.sin(a) * 2.2, -3.2), P(-3 + k * 2.4, 36 + math.cos(a) * 1.4, -3.2)],
                                   [0.25, 0.2], None, hint=(0, 0, 1), sides=4))
        m.add("rivet_strip", tub("body", "brass", [P(0, 20, 0), P(0, 22, 0)], [1.15, 1.15], None, sides=8))


def shield(m, v):
    # 骨の番兵の盾: 面・縁・裏の握り
    if v == "c":
        m.add("face", ell("body", "bone", (0, 16, 0), (12, 18, 3), segs=10, rings=3))
        m.add("boss", cone("body", "iron", (0, 16, -2.4), (0, 0, -1), 6, 3.4, sides=6))
        return
    pts = []
    for k in range(13):  # 上が広い凧形
        t = k / 12
        pts.append((t, 9.4 * math.sin(math.pi * (0.15 + 0.85 * t)) if t < 0.85 else 9.4 * math.sin(math.pi * 0.85 * 0.98) * (1 - (t - 0.85) / 0.15) + 0.6))
    for k, (t, w) in enumerate(pts[:-1]):
        y0, y1 = 2 + 30 * t, 2 + 30 * pts[k + 1][0]
        m.add(f"plank{k}", tub("body", "dwood" if v == "a" else "bone", [P(0, y0, 0), P(0, y1, 0)], [max(0.6, w), max(0.6, pts[k + 1][1])],
                               [1.0, 1.0], hint=(1, 0, 0), sides=4, phase=45, caps=(k == 0, k == len(pts) - 2)))
    m.add("boss", ell("body", "iron", (0, 19, -1.2), (2.4, 2.4, 1.2), segs=8, rings=2))
    for k, (x, y) in enumerate(((-6, 26), (6, 26), (-4, 10), (4, 10))):
        m.add(f"rivet{k}", ell("body", "iron", (x, y, -1.0), (0.5, 0.5, 0.4), segs=6, rings=1))
    m.add("strap", tub("body", "leather", [P(-4, 18, 1.1), P(0, 18.4, 2.2), P(4, 18, 1.1)], [0.5, 0.5, 0.5], None, hint=(0, 1, 0), sides=4))
    if v == "b":
        for k in range(5):  # 面に肋骨
            y = 8 + k * 4.6
            m.add(f"rib{k}", tub("body", "bone", [P(-6.6 + k * 0.5, y, -1.1), P(0, y + 1.2, -1.4), P(6.6 - k * 0.5, y, -1.1)], [0.6, 0.7, 0.6], None,
                                 hint=(0, 0, 1), sides=4))
        for k, (x, y, a) in enumerate(((-7, 14, 0.4), (6.5, 22, 2.0), (-3, 4, 4.0))):
            m.add(f"barn{k}", cone("body", "barn", (x, y, -0.9), (0.2, 0.1, -1), 1.4, 1.0, sides=5))


# ================================================================ NPC の持ち物 (手のひらほどの大きさ)
def flask(m, v):
    # 薬屋の瓶
    if v == "c":
        m.add("body", ell("body", "glassg", (0, 3.4, 0), (2.6, 3.2, 2.6), segs=8, rings=3))
        m.add("cork", tub("body", "wood", [P(0, 6.2, 0), P(0, 8.6, 0)], [1.2, 1.3], None, sides=6))
        return
    m.add("body", ell("body", "glassg", (0, 2.4, 0), (1.5, 1.9, 1.5), segs=12, rings=5))
    m.add("liquid", ell("body", "herb" if v == "a" else "wax", (0, 1.9, 0), (1.2, 1.1, 1.2), segs=10, rings=3))
    m.add("neck", tub("body", "glassg", [P(0, 4.0, 0), P(0, 5.4, 0)], [0.55, 0.5], None, sides=8))
    m.add("lip", tub("body", "glassg", [P(0, 5.3, 0), P(0, 5.7, 0)], [0.72, 0.72], None, sides=8))
    m.add("cork", tub("body", "wood", [P(0, 5.5, 0), P(0, 6.6, 0)], [0.5, 0.56], None, sides=6))
    m.add("cord", tub("body", "rope", [P(0, 4.6, 0)], [0.7], None, sides=6) if False else tub("body", "rope", [P(-0.6, 4.6, -0.3), P(0.6, 4.6, -0.3)],
                                                                                              [0.2, 0.2], None, sides=4))
    m.add("label", tub("body", "paper", [P(0, 1.4, -1.45), P(0, 3.0, -1.45)], [0.9, 0.9], [0.1, 0.1], hint=(1, 0, 0), sides=4, phase=45))
    if v == "b":
        m.add("seal", ell("body", "wax", (0, 6.6, 0), (0.7, 0.3, 0.7), segs=8, rings=1))
        m.add("sprig", tub("body", "herb", [P(0.3, 4.6, -0.5), P(1.2, 6.2, -0.9), P(1.6, 7.4, -0.8)], [0.2, 0.18, 0.1], None, sides=4))
        for k in range(3):
            m.add(f"leaf{k}", ell("body", "herb", (1.0 + k * 0.25, 5.8 + k * 0.6, -0.9), (0.5, 0.2, 0.25), segs=6, rings=1, rz=40))


def chest(m, v):
    # 箱開けの宝箱
    if v == "c":
        m.add("box", tub("body", "dwood", [P(0, 0, 0), P(0, 5, 0)], [5, 5], [3.6, 3.6], hint=(1, 0, 0), sides=4, phase=45, caps=(True, True)))
        m.add("lid", tub("body", "dwood", [P(-5, 6, 0), P(5, 6, 0)], [3.6, 3.6], None, hint=(0, 1, 0), sides=8, caps=(True, True)))
        m.add("lock", ell("body", "gold", (0, 4.6, -3.8), (1.4, 1.6, 0.6), segs=6, rings=2))
        return
    m.add("box", tub("body", "dwood", [P(0, 0, 0), P(0, 3.6, 0)], [5.66, 5.66], [3.82, 3.82], hint=(1, 0, 0), sides=4, phase=45, caps=(True, True)))
    m.add("lid", tub("body", "dwood", [P(-4, 4.2, 0), P(4, 4.2, 0)], [2.7, 2.7], None, hint=(0, 1, 0), sides=10, caps=(True, True)))
    for k, x in enumerate((-3.2, 0, 3.2)):
        m.add(f"band{k}", tub("body", "iron" if v == "a" else "brass", [P(x - 0.3, 0, 0), P(x + 0.3, 0, 0)], [0.1, 0.1], None, sides=4) if False else
              tub("body", "iron" if v == "a" else "brass", [P(x, 0.0, -2.8)] + [P(x, 4.2 + 2.95 * math.cos(a), 2.95 * math.sin(a) * -1) for a in np.linspace(-math.pi / 2, math.pi / 2, 9)]
                                                          + [P(x, 0.0, 2.8)], [0.35] * 11, None, hint=(1, 0, 0), sides=4))
    for k, x in enumerate((-4.05, 4.05)):
        m.add(f"corner{k}", tub("body", "iron", [P(x, 0, -2.7), P(x, 3.6, -2.7)], [0.4, 0.4], None, sides=4))
    m.add("lock", tub("body", "gold" if v == "b" else "iron", [P(0, 3.0, -2.8), P(0, 4.6, -2.8)], [0.75, 0.75], [0.3, 0.3], hint=(1, 0, 0), sides=4, phase=45))
    m.add("keyhole", ell("body", "night", (0, 3.6, -3.15), (0.2, 0.35, 0.1), segs=6, rings=1))
    if v == "b":
        m.add("emblem", ell("body", "gold", (2.0, 2.0, -2.75), (0.8, 0.8, 0.15), segs=8, rings=1))
        for k in range(3):
            m.add(f"gem{k}", ell("body", "violet", (-2.2 + k * 0.6, 7.0 - abs(k - 1) * 0.3, 0.2 * k), (0.4, 0.4, 0.4), segs=6, rings=2))


def bell(m, v):
    # 鐘守の手鐘
    if v == "c":
        m.add("grip", tub("body", "dwood", [P(0, 6, 0), P(0, 11, 0)], [0.9, 0.9], None, sides=6))
        m.add("bell", tub("body", "brass", [P(0, 6.4, 0), P(0, 3, 0), P(0, 0, 0)], [1.2, 3.0, 4.6], None, sides=12, caps=(True, False)))
        return
    m.add("grip", tub("body", "dwood", [P(0, 5.2, 0), P(0, 10, 0)], [0.55, 0.6], None, sides=8))
    m.add("knob", ell("body", "dwood", (0, 10.3, 0), (0.75, 0.6, 0.75), segs=8, rings=2))
    m.add("collar", tub("body", "brass", [P(0, 4.8, 0), P(0, 5.4, 0)], [0.8, 0.8], None, sides=8))
    m.add("bell", tub("body", "brass", [P(0, 5.0, 0), P(0, 3.6, 0), P(0, 1.6, 0), P(0, 0.4, 0)], [0.8, 1.6, 2.4, 3.0], None, sides=14, caps=(True, False)))
    m.add("lip", tub("body", "brass", [P(0, 0.3, 0), P(0, 0.8, 0)], [3.2, 3.1], None, sides=14))
    m.add("inner", tub("body", "night", [P(0, 0.5, 0), P(0, 3.0, 0)], [2.7, 1.2], None, sides=14, caps=(False, True)))
    m.add("clapper", ell("body", "iron", (0, 0.2, 0), (0.6, 0.6, 0.6), segs=6, rings=2))
    m.add("band_e", tub("body", "bronze", [P(0, 2.2, 0), P(0, 2.6, 0)], [2.25, 2.3], None, sides=14))
    if v == "b":
        m.add("ribbon", tub("body", "clothb", [P(0.5, 5.0, -0.5), P(1.6, 4.2, -1.3), P(1.4, 2.4, -1.6), P(2.0, 1.0, -1.8)], [0.35, 0.35, 0.3, 0.25], [0.1] * 4,
                            hint=(0, 0, 1), sides=4))
        for k in range(6):
            a = k / 6 * math.tau
            m.add(f"rune{k}", ell("body", "goldl", (math.cos(a) * 2.32, 2.4, math.sin(a) * 2.32), (0.25, 0.25, 0.25), segs=4, rings=1))


def doll(m, v):
    # 子どもの人形
    if v == "c":
        m.add("body", ell("body", "clothr", (0, 3, 0), (2.4, 3, 2), segs=10, rings=3))
        m.add("head", ell("body", "face", (0, 7.4, 0), (2.2, 2.2, 2.0), segs=10, rings=3))
        m.add("hair", ell("body", "yarn", (0, 8.4, 0.4), (2.4, 1.6, 2.2), segs=10, rings=3))
        return
    m.add("body", tub("body", "clothr" if v == "b" else "rope", [P(0, 1.6, 0), P(0, 3.4, 0), P(0, 5.0, 0)], [1.6, 1.3, 1.0], None, sides=10))
    m.add("head", ell("body", "face", (0, 6.4, 0), (1.25, 1.3, 1.15), segs=10, rings=4))
    m.add("hair", ell("body", "yarn", (0, 7.0, 0.25), (1.4, 0.95, 1.3), segs=10, rings=3))
    for s in (-1, 1):
        m.add(f"braid{s}", tub("body", "yarn", [P(s * 1.1, 6.6, 0.3), P(s * 1.5, 5.4, 0.4), P(s * 1.4, 4.4, 0.3)], [0.4, 0.35, 0.25], None, sides=5))
        m.add(f"eye{s}", ell("body", "night", (s * 0.45, 6.5, -1.1), (0.16, 0.2, 0.08), segs=4, rings=1))
        m.add(f"arm{s}", tub("body", "clothr" if v == "b" else "rope", [P(s * 1.2, 4.6, 0), P(s * 2.0, 3.6, -0.2), P(s * 2.1, 2.6, -0.3)], [0.45, 0.42, 0.4], None, sides=5))
        m.add(f"leg{s}", tub("body", "rope", [P(s * 0.6, 1.6, 0), P(s * 0.7, 0.0, -0.2)], [0.5, 0.45], None, sides=5))
    m.add("stitch", tub("body", "night", [P(-0.8, 4.2, -1.32), P(0.8, 4.2, -1.32)], [0.08, 0.08], None, sides=4))
    if v == "b":
        m.add("apron", tub("body", "paper", [P(0, 2.0, -1.2), P(0, 4.0, -1.0)], [1.1, 0.9], [0.1, 0.1], hint=(1, 0, 0), sides=4, phase=45))
        m.add("bow", ell("body", "clothb", (0.9, 7.4, 0.2), (0.6, 0.4, 0.3), segs=6, rings=2))
        m.add("cheek", ell("body", "wax", (0.6, 6.1, -1.05), (0.25, 0.15, 0.06), segs=4, rings=1))


def rope(m, v):
    # 水夫の綱の束
    if v == "c":
        for k in range(2):
            ring(m, f"coil{k}", "rope", (0, 4.4, k * 1.4 - 0.7), 3.8, axis="z", w=1.3, n=14)
        return
    for k in range(4):
        ring(m, f"coil{k}", "rope", (0, 4.4, k * 0.7 - 1.05), 3.4 - abs(k - 1.5) * 0.2, axis="z", w=0.55, n=16)
    m.add("tie", tub("body", "rope", [P(0, 7.4, -1.6), P(0, 8.2, 0), P(0, 7.4, 1.6)], [0.4, 0.45, 0.4], None, hint=(1, 0, 0), sides=5))
    m.add("tail", tub("body", "rope", [P(2.2, 1.6, 0.6), P(3.2, 0.4, 0.8), P(3.8, -1.6, 0.6)], [0.5, 0.45, 0.35], None, sides=5))
    m.add("whip", tub("body", "paper", [P(3.8, -1.6, 0.6), P(3.9, -2.4, 0.6)], [0.38, 0.3], None, sides=5))
    if v == "b":
        m.add("charm", ell("body", "shell", (0, 8.6, 0), (0.7, 0.5, 0.3), segs=8, rings=2))
        m.add("float", ell("body", "clothb", (-3.0, 1.0, 0.2), (1.0, 1.0, 1.0), segs=8, rings=3))
        m.add("float_band", tub("body", "paper", [P(-3.0, 0.8, 0.2), P(-3.0, 1.2, 0.2)], [1.05, 1.05], None, sides=8))


def lantern(m, v):
    # 角灯 (灯台守・のろし番など多くの人が持つ)
    if v == "c":
        m.add("glass", tub("body", "lantern", [P(0, 1, 0), P(0, 7, 0)], [2.6, 2.6], None, sides=6, caps=(True, True)))
        m.add("cap", cone("body", "iron", (0, 7, 0), (0, 1, 0), 3.4, 3.4, sides=6))
        m.add("base", tub("body", "iron", [P(0, 0, 0), P(0, 1.4, 0)], [3.0, 3.0], None, sides=6, caps=(True, True)))
        return
    metal = "iron" if v == "a" else "brass"
    m.add("base", tub("body", metal, [P(0, 0, 0), P(0, 0.9, 0)], [2.1, 2.0], None, sides=8, caps=(True, True)))
    m.add("glass", tub("body", "lantern", [P(0, 0.9, 0), P(0, 5.6, 0)], [1.6, 1.6], None, sides=8, caps=(False, False)))
    m.add("flame", ell("body", "ember", (0, 2.8, 0), (0.5, 1.0, 0.5), segs=6, rings=2))
    m.add("wick", tub("body", metal, [P(0, 0.9, 0), P(0, 2.0, 0)], [0.6, 0.4], None, sides=6))
    for k in range(4):
        a = k / 4 * math.tau + math.pi / 4
        m.add(f"bar{k}", tub("body", metal, [P(math.cos(a) * 1.7, 0.9, math.sin(a) * 1.7), P(math.cos(a) * 1.7, 5.6, math.sin(a) * 1.7)], [0.25, 0.25], None,
                             sides=4))
    m.add("roof", cone("body", metal, (0, 5.6, 0), (0, 1, 0), 2.2, 2.3, sides=8))
    m.add("finial", ell("body", metal, (0, 7.9, 0), (0.45, 0.45, 0.45), segs=6, rings=2))
    m.add("bail", tub("body", metal, [P(-1.2, 6.6, 0), P(-1.0, 8.6, 0), P(0, 9.4, 0), P(1.0, 8.6, 0), P(1.2, 6.6, 0)], [0.2] * 5, None, hint=(0, 0, 1), sides=4))
    if v == "b":
        m.add("crest", ell("body", "clothb", (0, 3.4, -1.7), (0.9, 1.1, 0.12), segs=8, rings=1))
        m.add("crest_mark", ell("body", "goldl", (0, 3.4, -1.85), (0.4, 0.5, 0.08), segs=6, rings=1))
        m.add("tag", tub("body", "paper", [P(1.2, 6.4, 0), P(1.6, 4.8, -0.2)], [0.35, 0.35], [0.08, 0.08], hint=(0, 0, 1), sides=4))


SUBJECTS = [("pike", "隊長の槍斧（鯨骨の隊長）"), ("glaive", "鉤の薙刀（雷翼の兵）"), ("hammer", "鍛冶の大槌（灰の鍛冶）"), ("shield", "番兵の盾（骨の番兵）"),
            ("flask", "薬瓶（薬屋）"), ("chest", "宝箱（箱開け）"), ("bell", "手鐘（鐘守）"), ("doll", "人形（子ども）"), ("rope", "綱の束（水夫）"),
            ("lantern", "角灯（灯台守ほか）")]


def build(key, v):
    m = Mount(f"rg_{v}_{key}", key)
    m.bone("body", "root", (0, 0, 0))
    globals()[key](m, v)
    m.pal = dict(mats=gmats(), glow_mats=sorted(GGLOW))
    m.detail = None
    m.hint = 256
    return m


def main():
    REVIEW_G.mkdir(parents=True, exist_ok=True)
    for v in "abc":
        tiles = []
        for key, label in SUBJECTS:
            m = build(key, v)
            size, atlas, glow = blp.paint_mount(m, m.pal, m.detail, m.hint)
            lo = np.min([f.verts.min(0) for f in m.F], axis=0)
            hi = np.max([f.verts.max(0) for f in m.F], axis=0)
            c = (lo + hi) / 2
            sc = 220 / max(1.0, float(np.max(hi - lo)))
            tris = sum(len(f.verts) - 2 for f in m.F)
            tiles.append((label, tris, view(m.F, atlas, glow, 215, 18, (300, 300), c, sc, ground=False)))
            print(v, key, tris, flush=True)
        cols = 5
        cv = Image.new("RGB", (cols * 310 + 10, 2 * 334 + 10), (16, 16, 22))
        d = ImageDraw.Draw(cv)
        for i, (label, tris, img) in enumerate(tiles):
            x, y = 10 + (i % cols) * 310, 10 + (i // cols) * 334
            d.text((x, y), f"{label}  {tris}", font=voxel.font(15), fill=(150, 232, 255))
            cv.paste(img, (x, y + 24))
        cv.save(REVIEW_G / f"rough_{v}.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
