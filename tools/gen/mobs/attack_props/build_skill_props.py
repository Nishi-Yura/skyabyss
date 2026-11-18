#!/usr/bin/env python3
"""自分の技の形の材 (色) と、人の技の印 (青緑)。build_skill_each / build_skill_kit / build_basic_props が使う。

職ごとの三案 (sk_<職>_<案>_<rise|shot>) と型 (sk_<職>_<slash|wave|aura|zone>) の模型は、2026-11-12 に技ごとの専用の形
(build_skill_each.py の prop_ss_<技>) へ替えて外した (声「スキルの種類によっても変えないと。使い回しは良くない」)。
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build_props as BP  # noqa: E402
from build_props import *  # noqa: E402,F401,F403

REVIEW = ROOT / "docs/review/skill_props"

SIGIL = [(40, 200, 180), (90, 236, 214), (170, 252, 238), (230, 255, 250)]
GRANITE = [(70, 74, 78), (98, 102, 104), (128, 130, 128), (158, 158, 150)]
OBSID = [(18, 16, 26), (32, 28, 44), (52, 46, 70), (80, 72, 104)]
STEEL = [(80, 86, 96), (120, 128, 138), (170, 178, 188), (220, 226, 232)]
PAPER = [(196, 180, 150), (222, 208, 178), (240, 230, 206), (252, 248, 236)]
INKRED = [(120, 24, 24), (170, 40, 36), (210, 70, 56)]
TOTEM = [(70, 48, 36), (100, 70, 50), (132, 98, 70), (166, 128, 92)]
SEAW = [(30, 90, 130), (50, 130, 170), (90, 180, 210), (170, 226, 240)]
LANTERN = [(200, 130, 40), (236, 176, 70), (252, 216, 120), (255, 240, 190)]
IRONHOT = [(60, 30, 24), (120, 50, 30), (200, 90, 40), (250, 160, 70)]
BRONZE = [(110, 72, 34), (150, 104, 52), (190, 140, 74), (226, 184, 110)]
THORN = [(40, 52, 30), (62, 80, 40), (90, 108, 52), (124, 140, 70)]
HAWK = [(88, 64, 44), (126, 96, 66), (170, 136, 98), (220, 196, 160)]
STAR = [(150, 170, 255), (190, 206, 255), (226, 234, 255), (250, 252, 255)]
RUNE = [(60, 50, 120), (90, 76, 170), (130, 116, 220), (180, 170, 250)]
DEEP = [(40, 10, 60), (80, 30, 120), (140, 70, 200), (200, 140, 255)]
GOLDL = [(170, 130, 50), (214, 176, 80), (240, 214, 130), (255, 240, 190)]


def smats():
    m = BP.mats()
    m.update({"sigil": M_(SIGIL, base=0.9, lit=0.1, noise=0.0, min=1), "granite": M_(GRANITE, lit=0.36, streak=0.05),
              "obsid": M_(OBSID, lit=0.4), "steel": M_(STEEL, lit=0.45), "paper": M_(PAPER, lit=0.3, streak=0.05), "inkred": M_(INKRED, lit=0.3),
              "totem": M_(TOTEM, lit=0.34, streak=0.08), "seaw": M_(SEAW, base=0.7, lit=0.25), "lantern": M_(LANTERN, base=0.85, lit=0.12, min=1),
              "ironhot": M_(IRONHOT, base=0.7, lit=0.2, min=1), "bronze": M_(BRONZE, lit=0.42), "thorn": M_(THORN, lit=0.34),
              "hawk": M_(HAWK, lit=0.34, streak=0.08), "star": M_(STAR, base=0.9, lit=0.1, noise=0.0, min=1), "rune": M_(RUNE, base=0.7, lit=0.25),
              "deep": M_(DEEP, base=0.75, lit=0.2, min=1), "goldl": M_(GOLDL, base=0.85, lit=0.12, min=1)})
    return m


SGLOW = set(BP.GLOW) | {"sigil", "lantern", "ironhot", "star", "deep", "goldl"}


def ring_sigil(m, r=9.0, y=0.6):
    """rise の根元の、青緑に光る人の技の印 (細い輪と四つの小さな菱)"""
    m.add("sig_ring", ell("body", "sigil", (0, y, 0), (r, 0.45, r), segs=16, rings=1))
    for k in range(4):
        a = k / 4 * math.tau + math.pi / 4
        m.add(f"sig_d{k}", ell("body", "sigil", (math.cos(a) * r, y + 0.3, math.sin(a) * r), (1.2, 0.6, 1.2), segs=4, rings=1, ry=45))


def core_sigil(m, c, r=1.6):
    """shot の芯の光"""
    m.add("sig_core", ell("body", "sigil", c, (r, r, r), segs=6, rings=2))
