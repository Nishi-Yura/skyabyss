#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""装飾品と道具の作り直しの案 A/B/C (2026-11-12 の全モデルの確かめで、「六角の輪 + 板や塊」の同じ作りのラフ止まりと分かった組。
声「装飾品と道具」)。代表の 6 品 (装飾品 4・道具 2) を三つの直し方で作り、絵だけ出す (ゲームには入れない)。

    python -X utf8 -I tools/gen/mobs/attack_props/rough_charms_tools.py

  A 細工       金具の細工物: 透かしの枠・爪留めの石・鎖の輪・刻み。道具は面取りした鋼・鋲・革巻き・真鍮の口金
  B 由来の物   その土地の本物そのもの: 骨・隕鉄の塊・生き物の心臓・竜の鱗を紐と玉で結ぶ。道具は角や骨の柄を革紐で縛る
  C 光の芯     どれも真ん中に光る芯 (石・炎・眼) を置き、輪郭の枠で囲む。身に付けて遠くからでも光る。道具は大ぶりの頭
向き: 品の模型と同じ (y が上、平らな面は ±Z、提げ輪が上)。絵は docs/review/charms_tools/rough_<a|b|c>.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import rough_gear_props as RG  # noqa: E402
from rough_gear_props import *  # noqa: E402,F401,F403

OUTD = KT.ROOT / "docs/review/charms_tools"
METEOR = [(26, 24, 30), (44, 42, 52), (70, 68, 82), (104, 102, 120)]
HEARTR = [(90, 16, 30), (140, 30, 44), (190, 56, 64), (230, 110, 110)]
STONE = [(80, 84, 86), (112, 116, 116), (146, 150, 148), (184, 188, 184)]
SCALE = [(40, 20, 16), (70, 34, 24), (106, 54, 34), (146, 80, 52)]


def cmats():
    m = RG.gmats()
    m.update({"meteor": M_(METEOR, lit=0.46), "heart": M_(HEARTR, lit=0.4), "scale": M_(SCALE, lit=0.4, streak=0.06), "stone": M_(STONE, lit=0.36, streak=0.04)})
    return m


def chain(m, nm, top, bot, links=5, mat="brass"):
    top, bot = np.asarray(top, float), np.asarray(bot, float)
    for k in range(links):
        c = top + (bot - top) * (k + 0.5) / links
        ring(m, f"{nm}{k}", mat, c, 0.55, axis="z" if k % 2 else "x", w=0.18, n=8)


def bail(m, y, mat="brass", r=0.9):
    ring(m, "bail", mat, (0, y, 0), r, axis="z", w=0.25, n=10)


# ================================================================ 共鳴の御守り (岩鯨: 石と骨の角)
def resonance(m, v):
    if v == "a":
        m.add("stone", ell("body", "stone", (0, 1.5, 0), (3.0, 3.4, 0.9), segs=6, rings=2))
        ring(m, "bezel", "brass", (0, 1.5, 0), 3.2, axis="z", w=0.35, n=12)
        for k in range(6):
            a = k / 6 * math.tau
            m.add(f"prong{k}", cone("body", "brass", (math.cos(a) * 3.3, 1.5 + math.sin(a) * 3.6, -0.3), (-math.cos(a), -math.sin(a), -0.5), 1.0, 0.25, sides=4))
        for s in (-1, 1):
            m.add(f"horn{s}", tub("body", "bone", [P(s * 3.0, 2.5, 0), P(s * 5.4, 4.4, 0), P(s * 6.4, 7.6, 0), P(s * 5.6, 8.6, 0)], [0.8, 0.7, 0.45, 0.15], None, sides=6))
            m.add(f"cap{s}", tub("body", "brass", [P(s * 3.0, 2.5, 0), P(s * 3.9, 3.2, 0)], [0.9, 0.9], None, sides=6))
            m.add(f"drop{s}", ell("body", "pearl", (s * 2.2, -2.6, 0), (0.45, 0.6, 0.45), segs=6, rings=2))
            chain(m, f"dch{s}", (s * 1.8, -1.6, 0), (s * 2.2, -2.1, 0), links=1)
        for k in range(5):
            m.add(f"etch{k}", tub("body", "goldl", [P(-1.6 + k * 0.8, 0.4, -0.95), P(-1.4 + k * 0.8, 2.6, -0.95)], [0.08, 0.08], None, sides=3))
        bail(m, 5.6)
    elif v == "b":
        m.add("vert", ell("body", "bone", (0, 2.0, 0), (2.6, 2.0, 1.3), segs=8, rings=3))
        m.add("spine", cone("body", "bone", (0, 3.6, 0), (0, 1, 0.2), 3.0, 0.9, sides=5))
        for s in (-1, 1):
            m.add(f"wing{s}", tub("body", "bone", [P(s * 2.0, 2.0, 0), P(s * 4.6, 1.4, 0.3), P(s * 6.0, 0.4, 0.2)], [0.9, 0.7, 0.3], [0.6, 0.5, 0.2], hint=(0, 0, 1), sides=5))
        m.add("pebble", ell("body", "stone", (0, -0.8, -0.3), (1.6, 1.3, 1.0), segs=7, rings=3))
        m.add("cord", tub("body", "rope", [P(-1.6, 3.0, -0.8), P(0, 6.4, -0.4), P(1.6, 3.0, -0.8)], [0.22, 0.22, 0.22], None, sides=4))
        m.add("wrap", tub("body", "rope", [P(-1.4, -0.2, -1.2), P(0, 0.4, -1.4), P(1.4, -0.2, -1.2)], [0.25] * 3, None, sides=4))
        for k, x in enumerate((-0.9, 0.0, 0.9)):
            m.add(f"bead{k}", ell("body", "clothb" if k != 1 else "pearl", (x, 4.6 + abs(x) * -0.6, -0.5), (0.4, 0.4, 0.4), segs=6, rings=2))
    else:
        m.add("core", ell("body", "light", (0, 1.8, 0), (1.4, 1.4, 1.0), segs=8, rings=3))
        for k, r in enumerate((2.3, 3.2, 4.1)):
            ring(m, f"wave{k}", "stone" if k % 2 == 0 else "bone", (0, 1.8, 0), r, axis="z", w=0.35 - k * 0.04, n=14)
        for s in (-1, 1):
            m.add(f"horn{s}", tub("body", "bone", [P(s * 4.0, 2.6, 0), P(s * 6.0, 5.2, 0), P(s * 5.6, 8.0, 0)], [0.9, 0.6, 0.15], None, sides=6))
        bail(m, 6.4, mat="stone")


# ================================================================ 隕鉄の輪 (野: 淵の上層)
def meteor_ring(m, v):
    if v == "a":
        ring(m, "band", "silver", (0, 0, 0), 2.6, axis="z", w=0.55, n=16)
        for k in range(8):
            a = k / 8 * math.tau
            m.add(f"groove{k}", tub("body", "night", [P(math.cos(a) * 2.6, math.sin(a) * 2.6, -0.58), P(math.cos(a + 0.2) * 2.6, math.sin(a + 0.2) * 2.6, -0.58)],
                                    [0.08, 0.08], None, sides=3))
        m.add("bezel", tub("body", "silver", [P(0, 2.7, 0), P(0, 3.6, 0)], [1.3, 1.5], None, sides=8))
        m.add("stone", ell("body", "meteor", (0, 4.2, 0), (1.3, 1.0, 1.2), segs=8, rings=3))
        m.add("spark", ell("body", "light", (0.4, 4.6, -0.9), (0.25, 0.25, 0.2), segs=4, rings=1))
        chain(m, "ch", (0, 5.0, 0), (0, 6.6, 0), links=2, mat="silver")
    elif v == "b":
        pts = []
        for k in range(15):
            a = k / 14 * math.tau
            r = 2.6 + 0.35 * math.sin(a * 3 + 1)
            pts.append(P(math.cos(a) * r, math.sin(a) * r, 0.2 * math.sin(a * 5)))
        m.add("band", tub("body", "meteor", pts, [0.8, 0.6, 0.9, 0.7, 0.8, 0.65, 0.9, 0.75, 0.6, 0.85, 0.7, 0.8, 0.6, 0.9, 0.8], None, sides=5, caps=(False, False)))
        for k, a in enumerate((0.6, 2.2, 4.0)):
            m.add(f"pit{k}", ell("body", "night", (math.cos(a) * 2.6, math.sin(a) * 2.6, -0.7), (0.3, 0.3, 0.15), segs=5, rings=1))
        m.add("cord", tub("body", "rope", [P(-0.8, 2.8, 0), P(0, 6.4, -0.2), P(0.8, 2.8, 0)], [0.2] * 3, None, sides=4))
        m.add("knot", ell("body", "rope", (0, 6.6, -0.2), (0.45, 0.35, 0.35), segs=6, rings=2))
    else:
        ring(m, "band", "meteor", (0, 0, 0), 3.0, axis="z", w=0.65, n=16)
        m.add("core", ell("body", "light", (0, 0, 0), (1.3, 1.3, 1.3), segs=8, rings=3))
        for k in range(4):
            a = k / 4 * math.tau + 0.4
            m.add(f"shard{k}", cone("body", "meteor", (math.cos(a) * 1.8, math.sin(a) * 1.8, 0), (math.cos(a), math.sin(a), 0), 1.2, 0.35, sides=4))
        bail(m, 3.9, mat="meteor")


# ================================================================ 擬態の心臓の御守り (野: 淵の上層。箱に化ける生き物の心臓)
def mimic_heart(m, v):
    if v == "a":
        m.add("gem", ell("body", "heart", (0, 1.0, 0), (2.0, 2.3, 1.2), segs=8, rings=3))
        for k in range(6):
            a = k / 6 * math.pi + math.pi / 12
            m.add(f"bar{k}", tub("body", "goldl", [P(math.cos(a) * 2.2, 1.0 + math.sin(a) * 2.5, 0), P(math.cos(a) * 1.4, 1.0 - math.sin(a) * 2.4, -0.3)], [0.18, 0.15],
                                 None, sides=4))
        for k in range(7):
            x = -1.8 + k * 0.6
            m.add(f"tooth{k}", cone("body", "edgew", (x, 2.6 - abs(x) * 0.4, -0.9), (0, -1, -0.2), 0.8, 0.2, sides=4))
        bail(m, 4.0, mat="goldl")
    elif v == "b":
        m.add("heart", ell("body", "heart", (0, 1.0, 0), (2.0, 2.2, 1.6), segs=10, rings=4))
        for s in (-1, 1):
            m.add(f"vessel{s}", tub("body", "heart", [P(s * 0.6, 2.8, 0), P(s * 1.2, 4.2, 0.2), P(s * 1.0, 5.2, 0.1)], [0.5, 0.4, 0.3], None, sides=5))
        for k in range(4):
            a = k * 1.5
            m.add(f"vein{k}", tub("body", "violet", [P(math.cos(a) * 0.6, 1.6, -1.4), P(math.cos(a) * 1.4, 0.4, -1.3)], [0.12, 0.08], None, sides=3))
        for k in range(5):
            m.add(f"fang{k}", cone("body", "bone", (-1.2 + k * 0.6, 0.0, -1.5), (0, -1, -0.4), 0.9, 0.2, sides=4))
        m.add("cord", tub("body", "rope", [P(-1.0, 5.2, 0), P(0, 6.4, 0), P(1.0, 5.2, 0)], [0.18] * 3, None, sides=4))
    else:
        m.add("heart", ell("body", "heart", (0, 1.0, 0), (2.2, 2.4, 1.3), segs=8, rings=3))
        m.add("eye", ell("body", "light", (0, 1.2, -1.0), (0.9, 0.7, 0.5), segs=8, rings=2))
        m.add("pupil", ell("body", "night", (0, 1.2, -1.4), (0.25, 0.55, 0.2), segs=6, rings=1))
        ring(m, "frame", "goldl", (0, 1.0, 0), 2.8, axis="z", w=0.3, n=12)
        bail(m, 4.2, mat="goldl")


# ================================================================ 竜の熾の鱗 (地下の熾の巣)
def ember_scale(m, v):
    if v == "a":
        m.add("plate", tub("body", "scale", [P(0, -3.0, 0), P(0, 1.0, 0), P(0, 4.4, 0)], [0.6, 3.6, 2.4], [0.4, 0.6, 0.5], hint=(1, 0, 0), sides=6))
        m.add("rim", tub("body", "silver", [P(-3.2, 1.0, -0.5), P(-1.6, -2.4, -0.5), P(0, -3.4, -0.5), P(1.6, -2.4, -0.5), P(3.2, 1.0, -0.5)], [0.22] * 5, None,
                         sides=4))
        for k, y in enumerate((-1.2, 0.6, 2.4)):
            m.add(f"vein{k}", tub("body", "ember", [P(-1.6 + k * 0.4, y, -0.7), P(0, y + 0.6, -0.7), P(1.6 - k * 0.4, y, -0.7)], [0.15] * 3, None, sides=4))
        m.add("cap", tub("body", "silver", [P(0, 4.2, 0), P(0, 5.0, 0)], [1.2, 0.9], None, sides=8))
        bail(m, 5.8, mat="silver")
        chain(m, "ch", (0, 6.6, 0), (0, 7.8, 0), links=2, mat="silver")
    elif v == "b":
        pts = [P(0, -3.4, 0.2), P(0, -1.0, -0.2), P(0, 2.0, -0.3), P(0, 4.2, 0.2)]
        m.add("plate", tub("body", "scale", pts, [0.3, 3.2, 3.6, 1.4], [0.3, 0.8, 0.9, 0.5], hint=(1, 0, 0), sides=6))
        for k in range(4):
            m.add(f"ridge{k}", tub("body", "scale", [P(-2.4 + k * 1.6, -1.5, -0.95), P(-1.6 + k * 1.1, 2.8, -0.95)], [0.25, 0.15], None, sides=4))
            m.add(f"glow{k}", tub("body", "ember", [P(-2.0 + k * 1.6, -0.8, -0.98), P(-1.5 + k * 1.1, 1.6, -0.98)], [0.1, 0.08], None, sides=3))
        m.add("cord", tub("body", "leather", [P(-0.8, 3.6, 0), P(0, 6.0, 0), P(0.8, 3.6, 0)], [0.25] * 3, None, sides=4))
        m.add("bead", ell("body", "bone", (0, 6.2, 0), (0.45, 0.45, 0.45), segs=6, rings=2))
    else:
        m.add("plate", tub("body", "scale", [P(0, -3.6, 0), P(0, 1.0, 0), P(0, 4.8, 0)], [0.6, 4.2, 2.6], [0.6, 1.0, 0.7], hint=(1, 0, 0), sides=6))
        m.add("core", ell("body", "ember", (0, 0.8, -0.9), (1.6, 2.0, 0.6), segs=8, rings=3))
        m.add("flare", cone("body", "magma", (0, 2.6, -0.9), (0, 1, 0), 2.2, 0.8, sides=5))
        bail(m, 5.6, mat="iron")


# ================================================================ 採集の斧 (道具)
def gather_axe(m, v):
    if v == "c":
        m.add("haft", tub("body", "dwood", [P(0, -3.4, 0), P(0, 18.6, 0)], [1.2, 1.1], None, sides=8))
        m.add("head", tub("body", "steeld", [P(0.5, 14.5, 0), P(6.0, 13.0, 0), P(8.4, 15.8, 0)], [3.0, 4.6, 5.4], [1.1, 0.8, 0.3], hint=(0, 0, 1), sides=4, phase=45))
        return
    wood = "dwood" if v == "a" else "bone"
    m.add("haft", tub("body", wood, [P(0, -3.4, 0), P(0.3, 6, 0), P(0, 18.6, 0)], [0.85, 0.8, 0.75], None, sides=8))
    m.add("knob", ell("body", "brass" if v == "a" else "bone", (0, -3.6, 0), (1.1, 0.8, 1.1), segs=8, rings=2))
    wraps(m, "grip", "leather", -2.5, 3.5, 0.95, 5)
    if v == "a":
        m.add("head", tub("body", "iron", [P(-1.2, 15.4, 0), P(1.4, 15.4, 0), P(4.0, 14.8, 0)], [1.6, 1.8, 2.4], [1.1, 0.9, 0.5], hint=(0, 1, 0), sides=4, phase=45))
        m.add("blade", tub("body", "steeld", [P(4.6, 11.8, 0), P(5.4, 15.2, 0), P(4.6, 18.6, 0)], [1.6, 2.0, 1.6], [0.35, 0.3, 0.35], hint=(1, 0, 0), sides=4, phase=45))
        m.add("edge", tub("body", "edgew", [P(6.4, 12.8, 0), P(7.1, 15.4, 0), P(6.4, 18.0, 0)], [0.2, 0.2, 0.2], None, sides=4))
        for k, y in enumerate((14.2, 16.6)):
            m.add(f"rivet{k}", ell("body", "brass", (1.0, y, -1.0), (0.3, 0.3, 0.2), segs=6, rings=1))
        for s in (-1, 1):
            m.add(f"langet{s}", tub("body", "iron", [P(0, 13.0, s * 0.85), P(0, 10.6, s * 0.85)], [0.3, 0.2], [0.1, 0.1], hint=(0, 0, 1), sides=4))
        m.add("poll", tub("body", "iron", [P(-1.2, 15.4, 0), P(-2.4, 15.4, 0)], [1.5, 1.3], [1.0, 0.9], hint=(0, 1, 0), sides=4, phase=45))
    else:
        m.add("head", tub("body", "iron", [P(0.6, 15.0, 0), P(3.4, 14.4, 0), P(6.2, 15.6, 0)], [1.8, 3.0, 3.4], [0.8, 0.6, 0.2], hint=(0, 0, 1), sides=4, phase=45))
        for k in range(3):
            m.add(f"lash{k}", tub("body", "leather", [P(-0.9, 13.4 + k * 0.9, -0.9), P(0.9, 14.0 + k * 0.9, -0.9), P(0.9, 14.0 + k * 0.9, 0.9), P(-0.9, 13.4 + k * 0.9, 0.9)],
                                  [0.2] * 4, None, sides=4))
        m.add("tine", cone("body", "bone", (0, 18.4, 0), (0.3, 1, 0), 2.0, 0.6, sides=5))


# ================================================================ 採集のつるはし (道具)
def gather_pick(m, v):
    if v == "c":
        m.add("haft", tub("body", "dwood", [P(0, -3.4, 0), P(0, 16, 0)], [1.2, 1.1], None, sides=8))
        m.add("head", tub("body", "steeld", [P(-8.3, 13.0, 0), P(0, 17.6, 0), P(8.3, 13.0, 0)], [0.6, 2.6, 0.6], [0.6, 1.6, 0.6], hint=(0, 0, 1), sides=4, phase=45))
        return
    wood = "dwood" if v == "a" else "bone"
    m.add("haft", tub("body", wood, [P(0, -3.4, 0), P(0, 16.4, 0)], [0.85, 0.75], None, sides=8))
    wraps(m, "grip", "leather", -2.5, 3.5, 0.95, 5)
    if v == "a":
        m.add("collar", tub("body", "iron", [P(0, 14.8, 0), P(0, 17.4, 0)], [1.3, 1.2], None, sides=8))
        for s in (-1, 1):
            m.add(f"arm{s}", tub("body", "steeld", [P(0, 16.2, 0), P(s * 3.6, 16.6, 0), P(s * 6.6, 15.2, 0), P(s * 8.3, 13.0, 0)], [1.0, 0.9, 0.6, 0.12], [0.8, 0.7, 0.5, 0.1],
                                 hint=(0, 0, 1), sides=4, phase=45))
        m.add("wedge", ell("body", "dwood", (0, 17.6, 0), (0.6, 0.35, 0.6), segs=6, rings=1))
        m.add("band", tub("body", "brass", [P(0, 12.8, 0), P(0, 13.6, 0)], [0.95, 0.95], None, sides=8))
    else:
        for s in (-1, 1):
            m.add(f"arm{s}", tub("body", "bone", [P(0, 16.0, 0), P(s * 3.6, 16.8, 0), P(s * 6.6, 15.0, 0), P(s * 8.0, 12.6, 0)], [1.1, 1.0, 0.7, 0.15], None, sides=6))
        for k in range(3):
            m.add(f"lash{k}", tub("body", "leather", [P(-1.0, 15.0 + k * 0.7, -1.0), P(1.0, 15.4 + k * 0.7, -1.0), P(1.0, 15.4 + k * 0.7, 1.0), P(-1.0, 15.0 + k * 0.7, 1.0)],
                                  [0.2] * 4, None, sides=4))


SUBJECTS = [("resonance", "共鳴の御守り（岩鯨）"), ("meteor_ring", "隕鉄の輪（淵の上層）"), ("mimic_heart", "擬態の心臓（淵の上層）"),
            ("ember_scale", "竜の熾の鱗（地下の熾の巣）"), ("gather_axe", "採集の斧"), ("gather_pick", "採集のつるはし")]


def build(key, v):
    m = Mount(f"ct_{v}_{key}", key)
    m.bone("body", "root", (0, 0, 0))
    globals()[key](m, v)
    m.pal = dict(mats=cmats(), glow_mats=sorted(RG.GGLOW | {"light", "ember", "pearl"}))
    m.detail = None
    m.hint = 128
    return m


def main():
    OUTD.mkdir(parents=True, exist_ok=True)
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
            tiles.append((label, tris, view(m.F, atlas, glow, 200, 10, (300, 300), c, sc, ground=False)))
            print(v, key, tris, flush=True)
        cols = 3
        cv = Image.new("RGB", (cols * 310 + 10, 2 * 334 + 10), (16, 16, 22))
        d = ImageDraw.Draw(cv)
        for i, (label, tris, img) in enumerate(tiles):
            x, y = 10 + (i % cols) * 310, 10 + (i // cols) * 334
            d.text((x, y), f"{label}  {tris}", font=voxel.font(15), fill=(150, 232, 255))
            cv.paste(img, (x, y + 24))
        cv.save(OUTD / f"rough_{v}.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
