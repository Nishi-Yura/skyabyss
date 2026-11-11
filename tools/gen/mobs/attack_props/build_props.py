#!/usr/bin/env python3
"""技の形 (combat/prop/AttackProp) の低ポリの模型を作って入れる (2026-11-11 の声「遠距離攻撃とかはきちんと円の予告とエフェクトだけじゃなくて、
そこに攻撃専用のモデルが出てきたり」)。

    python -X utf8 -I tools/gen/mobs/attack_props/build_props.py [種類 ...] [--no-install]

種類ごとに prop_<種類> の模型 (骨 root → body、触手などは先の骨も) と、待ちの揺れ (idle) を書き、
確かめの絵を docs/review/attack_props/props_sheet.png に並べる。原点は床の真ん中 (RISE は床から上、FALL の氷柱は先が原点)。
TOSS で飛ぶ物は前 (-Z) を先に作る。単位は 1/16 ブロック。
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "dungeon_bosses_lp"))
from blp import *  # noqa: E402,F401,F403
import blp  # noqa: E402

REVIEW = ROOT / "docs/review/attack_props"

BONE = [(108, 98, 82), (148, 138, 118), (188, 178, 156), (220, 212, 192), (242, 236, 220)]
ROCK = [(40, 36, 36), (62, 56, 54), (88, 80, 76), (116, 106, 98), (146, 134, 124)]
OCT = [(40, 14, 30), (66, 24, 46), (98, 38, 62), (132, 58, 80), (168, 86, 104)]
SUCK = [(170, 140, 136), (208, 182, 170), (236, 216, 202)]
INK = [(10, 8, 18), (22, 16, 36), (40, 28, 64), (70, 50, 104)]
CRY = [(70, 140, 226), (120, 190, 250), (184, 226, 255), (236, 250, 255)]
MAG = [(160, 50, 16), (220, 96, 30), (252, 156, 56), (255, 214, 120), (255, 244, 200)]
BAS = [(22, 20, 22), (36, 32, 34), (54, 48, 50), (74, 66, 66)]
ICE = [(120, 170, 210), (160, 204, 236), (200, 230, 250), (236, 248, 255)]
WATER = [(20, 70, 110), (40, 110, 150), (80, 160, 190), (150, 210, 226), (220, 244, 250)]
FOAM = [(190, 220, 230), (220, 238, 244), (246, 252, 254)]
CORAL = [(120, 34, 48), (176, 56, 66), (224, 92, 92), (246, 140, 126)]
SHELL = [(150, 136, 116), (196, 182, 160), (226, 216, 198), (244, 238, 226)]
BOLT = [(150, 200, 255), (200, 230, 255), (240, 250, 255)]
ASH = [(50, 46, 44), (78, 72, 68), (110, 102, 96), (140, 130, 122)]
PEARL = [(190, 196, 210), (220, 226, 236), (244, 246, 252)]
WOOD = [(70, 48, 30), (98, 70, 44), (128, 96, 62), (156, 122, 84)]
IRON = [(70, 72, 78), (110, 114, 120), (160, 164, 170), (210, 214, 220)]
FEATH = [(120, 30, 30), (170, 52, 44), (214, 90, 70), (236, 200, 180)]
WIND = [(150, 190, 200), (186, 220, 226), (218, 240, 244), (244, 252, 252)]
MOSS = [(36, 62, 30), (58, 92, 40), (90, 124, 52), (130, 156, 70)]
SPORE = [(150, 120, 60), (190, 164, 86), (226, 206, 120), (246, 236, 170)]
ROPE = [(96, 80, 56), (132, 112, 80), (170, 150, 110), (204, 188, 150)]
FLOAT = [(150, 40, 30), (200, 70, 44), (232, 120, 70)]
GOLD = [(150, 110, 40), (200, 156, 64), (236, 200, 110), (252, 236, 180)]
LIGHT = [(230, 214, 160), (246, 236, 196), (255, 250, 230), (255, 255, 248)]
PLUME = [(196, 196, 206), (222, 222, 230), (240, 240, 246), (252, 252, 255)]
WISP = [(70, 170, 160), (120, 220, 200), (190, 250, 236), (240, 255, 250)]


def mats():
    return {"bone": M_(BONE, lit=0.34), "rock": M_(ROCK, lit=0.34, streak=0.04), "oct": M_(OCT, streak=0.05), "suck": M_(SUCK),
            "ink": M_(INK, lit=0.4), "cry": M_(CRY, base=0.64, lit=0.22), "magma": M_(MAG, base=0.7, lit=0.2, min=1), "basalt": M_(BAS, lit=0.34),
            "ice": M_(ICE, lit=0.42), "water": M_(WATER, lit=0.36, streak=0.1), "foam": M_(FOAM, lit=0.3), "coral": M_(CORAL, lit=0.36),
            "shell": M_(SHELL, lit=0.4), "bolt": M_(BOLT, base=0.9, lit=0.05, noise=0.0, min=1), "ash": M_(ASH, lit=0.3), "pearl": M_(PEARL, base=0.8, lit=0.3),
            "wood": M_(WOOD, lit=0.34, streak=0.08), "iron": M_(IRON, lit=0.4), "feath": M_(FEATH, lit=0.36), "wind": M_(WIND, base=0.75, lit=0.25, noise=0.02),
            "moss": M_(MOSS, lit=0.34), "rope": M_(ROPE, lit=0.34, streak=0.06), "gold": M_(GOLD, lit=0.42), "light": M_(LIGHT, base=0.9, lit=0.1, noise=0.0, min=1),
            "plume": M_(PLUME, lit=0.3, streak=0.08), "float": M_(FLOAT, lit=0.36), "spore": M_(SPORE, base=0.62, lit=0.3), "wisp": M_(WISP, base=0.85, lit=0.15, min=1)}


GLOW = {"cry", "magma", "bolt", "pearl", "wisp", "spore", "light"}


def base_debris(m, mat, r, n=7, seed=0):
    """突き出た根元の床の割れた欠片 (根元を隠す)"""
    rng = np.random.default_rng(seed)
    for k in range(n):
        a = k / n * math.tau + rng.uniform(-0.3, 0.3)
        rr = r * rng.uniform(0.7, 1.1)
        m.add(f"deb{k}", ell("body", mat, (math.cos(a) * rr, 1.0, math.sin(a) * rr), (rng.uniform(2.0, 3.4), rng.uniform(1.2, 2.2), rng.uniform(2.0, 3.0)),
                             segs=6, rings=2, ry=rng.uniform(0, 180), rz=rng.uniform(-20, 20)))


def p_bone_spike(m):
    for k, (x, z, h, r, lean) in enumerate(((0, 0, 44, 4.6, 0.0), (5, 3, 28, 3.2, 0.35), (-4, -4, 22, 2.8, -0.35), (-3, 5, 16, 2.2, -0.2))):
        m.add(f"spike{k}", cone("body", "bone", (x, -6, z), (lean, 1, lean * 0.4), h + 6, r, sides=6, rings=4, bend=(lean * 6, 0, 2.0)))
    base_debris(m, "rock", 7)


def p_rock(m):
    rng = np.random.default_rng(3)
    m.add("core", ell("body", "rock", (0, 9, 0), (9.0, 8.0, 8.6), segs=10, rings=4), core=True)
    for k in range(7):
        d = nrm(rng.normal(size=3))
        m.add(f"lump{k}", ell("body", "rock", np.array([0, 9, 0]) + d * 6.5, (4.2, 3.6, 4.0), segs=7, rings=2, ry=rng.uniform(0, 180)))
    for k in range(4):
        m.add(f"bonebit{k}", cone("body", "bone", np.array([0, 9, 0]) + nrm(rng.normal(size=3)) * 7, nrm(rng.normal(size=3)), 4.0, 1.0, sides=4))


def p_bone_shard(m):
    m.add("shard", cone("body", "bone", (0, 3, 6), (0, 0, -1), 16, 2.8, sides=5, rings=3, bend=(0, 0.8, 0)))
    for s in (1, -1):
        m.add(f"barb{s}", cone("body", "bone", (s * 1.2, 3, 2), (s * 0.6, 0.2, -1), 6, 1.0, sides=4))


def p_tentacle(m):
    m.bone("t1", "body", (0, 16, 0))
    m.bone("t2", "t1", (1.5, 32, -1))
    pts = [(0, -8, 0), (0, 6, 0), (1.0, 16, -1.0), (3.0, 26, -2.4), (4.0, 34, -1.6), (2.6, 41, 2.0), (-1.6, 44, 6.0)]
    rad = [7.0, 6.2, 5.2, 4.2, 3.2, 2.0, 0.6]
    m.add("tb", tub("body", "oct", [np.array(p) for p in pts[:3]], rad[:3], None, sides=8))
    m.add("tm", tub("t1", "oct", [np.array(p) for p in pts[1:5]], rad[1:5], None, sides=8))
    m.add("tt", tub("t2", "oct", [np.array(p) for p in pts[3:]], rad[3:], None, sides=7))
    for i in range(1, 6):
        b = "body" if i < 2 else ("t1" if i < 4 else "t2")
        p = np.array(pts[i]) + np.array([0, 0, -rad[i] * 0.8])
        m.add(f"suck{i}", ell(b, "suck", p, (rad[i] * 0.4, rad[i] * 0.4, 0.5), segs=7, rings=1))
        m.add(f"hook{i}", cone(b, "bone", p + np.array([0, 0, -0.3]), (0, -0.3, -1), rad[i] * 0.5, rad[i] * 0.14, sides=4))
    base_debris(m, "rock", 8, seed=2)


def p_ink_blob(m):
    m.add("blob", ell("body", "ink", (0, 4, 0), (5.6, 5.0, 6.4), segs=10, rings=4), core=True)
    for k in range(5):
        a = k / 5 * math.tau
        m.add(f"drop{k}", ell("body", "ink", (math.cos(a) * 3.6, 2.0, math.sin(a) * 3.6 + 4.0), (1.6, 1.4, 2.6), segs=6, rings=2))
    m.add("tail", cone("body", "ink", (0, 4, 4), (0, 0, 1), 10, 3.4, sides=7, rings=2))


def p_crystal_spike(m):
    for k, (x, z, h, r, lx, lz) in enumerate(((0, 0, 40, 4.4, 0, 0), (5, 2, 26, 3.0, 0.4, 0.1), (-4, 3, 22, 2.6, -0.4, 0.2), (1, -5, 18, 2.4, 0.1, -0.4))):
        m.add(f"c{k}", cone("body", "cry", (x, -6, z), (lx, 1, lz), h + 6, r, sides=6, rings=2))
    base_debris(m, "rock", 7, seed=4)


def p_fireball(m):
    m.add("core", ell("body", "magma", (0, 7, 0), (6.2, 6.2, 6.2), segs=10, rings=4), core=True)
    rng = np.random.default_rng(5)
    for k in range(8):
        d = nrm(rng.normal(size=3))
        m.add(f"crust{k}", ell("body", "basalt", np.array([0, 7, 0]) + d * 5.2, (3.0, 2.4, 3.0), segs=6, rings=2, ry=rng.uniform(0, 180)))
    for k in range(5):
        a = k / 5 * math.tau
        m.add(f"flame{k}", cone("body", "magma", (math.cos(a) * 2.6, 7 + math.sin(a) * 2.6, 3.0), (math.cos(a) * 0.3, math.sin(a) * 0.3, 1), 10, 2.0, sides=5, rings=2))


def p_lava_pillar(m):
    m.bone("t1", "body", (0, 24, 0))
    m.add("col", tub("body", "magma", [np.array(p) for p in ((0, -8, 0), (0, 12, 0), (0.8, 28, 0.4))], [7.0, 6.2, 5.0], None, sides=9))
    m.add("top", tub("t1", "magma", [np.array(p) for p in ((0.6, 22, 0.3), (0.4, 40, 0), (-0.6, 52, -0.4), (0, 58, 0))], [5.4, 4.2, 3.0, 0.8], None, sides=8))
    rng = np.random.default_rng(6)
    for k in range(10):
        y = rng.uniform(0, 48)
        a = rng.uniform(0, math.tau)
        r = 6.0 - y * 0.07
        m.add(f"crust{k}", ell("body" if y < 24 else "t1", "basalt", (math.cos(a) * r, y, math.sin(a) * r), (2.6, 3.2, 2.6), segs=6, rings=2))
    for k in range(6):
        a = k / 6 * math.tau
        m.add(f"splash{k}", cone("t1", "magma", (0, 50, 0), (math.cos(a), -0.4, math.sin(a)), 7, 1.4, sides=4, rings=2, bend=(0, -2.4, 0)))
    base_debris(m, "basalt", 9, seed=7)


def p_ice_spike(m):
    for k, (x, z, h, r, lx, lz) in enumerate(((0, 0, 38, 4.6, 0, 0), (5, 3, 24, 3.2, 0.4, 0.2), (-5, 2, 20, 2.8, -0.45, 0.1), (0, -5, 16, 2.4, 0, -0.45))):
        m.add(f"i{k}", cone("body", "ice", (x, -6, z), (lx, 1, lz), h + 6, r, sides=5, rings=2))
    base_debris(m, "ice", 7, seed=8)


def p_icicle(m):
    # 落ちてくる氷柱: 先が下 (原点)。根元に氷の塊
    m.add("main", cone("body", "ice", (0, 34, 0), (0, -1, 0), 34, 5.0, sides=6, rings=3))
    for k in range(4):
        a = k / 4 * math.tau + 0.4
        m.add(f"side{k}", cone("body", "ice", (math.cos(a) * 3.6, 30, math.sin(a) * 3.6), (math.cos(a) * 0.15, -1, math.sin(a) * 0.15), 16, 2.4, sides=5, rings=2))
    m.add("cap", ell("body", "ice", (0, 35, 0), (6.4, 3.4, 6.4), segs=8, rings=2))


def p_water_spout(m):
    m.bone("t1", "body", (0, 24, 0))
    m.add("col", tub("body", "water", [np.array(p) for p in ((0, -8, 0), (0, 12, 0), (1.0, 26, 0))], [7.6, 6.0, 5.2], None, sides=10))
    m.add("top", tub("t1", "water", [np.array(p) for p in ((0.8, 22, 0), (-0.6, 38, 0.6), (0.4, 50, -0.4))], [5.6, 6.4, 7.6], None, sides=10, caps=(True, True)))
    m.add("crown", ell("t1", "foam", (0.4, 50, -0.4), (8.4, 2.6, 8.4), segs=10, rings=2))
    for k in range(3):
        y = 10 + k * 13
        m.add(f"swirl{k}", ell("body" if y < 24 else "t1", "foam", (0, y, 0), (6.8 - k * 0.2, 1.0, 6.8 - k * 0.2), segs=10, rings=1, rz=12 * (-1) ** k))
    m.add("ring", ell("body", "foam", (0, 1, 0), (10, 1.4, 10), segs=12, rings=1))


def p_coral_spear(m):
    m.add("shaft", tub("body", "coral", [np.array(p) for p in ((0, 3, 12), (0, 3, -6))], [1.0, 1.0], None, sides=6))
    for k, off in enumerate((-1.8, 0, 1.8)):
        m.add(f"prong{k}", cone("body", "shell", (off * 0.4, 3, -5), (off * 0.12, 0, -1), 9 if k == 1 else 7, 1.0, sides=5, rings=2))
    m.add("collar", ell("body", "coral", (0, 3, -5.4), (2.0, 2.0, 1.4), segs=7, rings=2))


def p_lightning(m):
    rng = np.random.default_rng(9)
    pts = [np.array([0, 120, 0])]
    for k in range(9):
        y = 120 - (k + 1) * 13.3
        pts.append(np.array([rng.uniform(-5, 5), max(-6.0, y), rng.uniform(-5, 5)]))
    for k in range(len(pts) - 1):
        m.add(f"bolt{k}", tub("body", "bolt", [pts[k], pts[k + 1]], [1.6, 1.4], None, sides=5))
        m.add(f"node{k}", ell("body", "bolt", pts[k + 1], (1.8, 1.8, 1.8), segs=6, rings=2))
    for k in (3, 6):
        b = pts[k]
        d = nrm(np.array([rng.uniform(-1, 1), -0.8, rng.uniform(-1, 1)]))
        m.add(f"branch{k}", tub("body", "bolt", [b, b + d * 14], [1.0, 0.4], None, sides=4))
    m.add("scorch", ell("body", "basalt", (0, 0.4, 0), (7, 0.8, 7), segs=10, rings=1))


def p_ash_ball(m):
    m.add("core", ell("body", "ash", (0, 6, 0), (5.6, 5.4, 5.6), segs=10, rings=3), core=True)
    rng = np.random.default_rng(10)
    for k in range(9):
        d = nrm(rng.normal(size=3))
        m.add(f"ember{k}", ell("body", "magma", np.array([0, 6, 0]) + d * 5.0, (1.2, 1.2, 1.2), segs=5, rings=1))
    m.add("trail", cone("body", "ash", (0, 6, 4), (0, 0, 1), 9, 3.6, sides=6, rings=2))


def p_shell_spike(m):
    for k, (x, z, h, r) in enumerate(((0, 0, 36, 5.0), (5, 3, 22, 3.4), (-4, -3, 18, 3.0))):
        m.add(f"s{k}", cone("body", "shell", (x, -6, z), (x * 0.05, 1, z * 0.05), h + 6, r, sides=7, rings=4, bend=(0, 0, 1.4)))
        for j in range(3):
            y = -2 + (j + 1) * (h / 4)
            rr = r * (1 - (j + 1) / 4.6)
            m.add(f"ring{k}{j}", ell("body", "coral", (x + x * 0.05 * y / 10, y, z), (rr * 1.15, 0.7, rr * 1.15), segs=8, rings=1))
    base_debris(m, "rock", 7, seed=11)


def p_pearl(m):
    m.add("orb", ell("body", "pearl", (0, 6, 0), (5.0, 5.0, 5.0), segs=12, rings=5), core=True)
    m.add("halo", ell("body", "water", (0, 6, 0), (6.6, 0.6, 6.6), segs=12, rings=1))


def _arrow(m, tag, p, d, length=22.0, roll=0.0):
    """矢一本: p は鏃の先、d は飛ぶ向き (先へ)。柄・鏃・羽根三枚"""
    d = nrm(np.array(d, float))
    p = np.array(p, float)
    tail = p - d * length
    m.add(f"{tag}shaft", tub("body", "wood", [p - d * 4.0, tail], [0.55, 0.55], None, sides=5))
    m.add(f"{tag}head", cone("body", "iron", p - d * 4.6, d, 5.2, 1.5, sides=4, rings=1))
    up = nrm(np.cross(d, [0.0, 1.0, 0.0]) if abs(d[1]) < 0.9 else np.cross(d, [1.0, 0.0, 0.0]))
    for k in range(3):
        a = roll + k * math.tau / 3
        side = up * math.cos(a) + nrm(np.cross(d, up)) * math.sin(a)
        c = tail + d * 3.2 + side * 1.2
        m.add(f"{tag}f{k}", ell("body", "feath", c, (0.35, 1.3, 3.4), segs=4, rings=1, ry=math.degrees(math.atan2(d[0], d[2])),
                                 rz=math.degrees(a)))


def p_arrow(m):
    # 撃たれた矢 (TOSS・SHOTS): 前 (-Z) が鏃。三本の束で、遠くからも矢と分かる太さ
    for k, (x, y, z) in enumerate(((0, 3.2, -10), (2.4, 4.8, -6), (-2.2, 2.0, -7))):
        _arrow(m, f"a{k}", (x, y, z), (0, 0, -1), 22, roll=k * 0.7)


def p_arrow_volley(m):
    # 降ってくる矢の雨 (FALL・RAIN): 鏃が下 (原点の床)。床に刺さって残る。根元に土の欠片
    rng = np.random.default_rng(12)
    for k in range(6):
        a = k / 6 * math.tau + rng.uniform(-0.3, 0.3)
        r = 0 if k == 0 else rng.uniform(3.0, 6.5)
        lean = (math.cos(a) * rng.uniform(0.1, 0.3), -1, math.sin(a) * rng.uniform(0.1, 0.3))
        _arrow(m, f"v{k}", (math.cos(a) * r, -1.5, math.sin(a) * r), lean, rng.uniform(20, 24), roll=rng.uniform(0, 3))
    base_debris(m, "rock", 6, n=6, seed=13)


def p_wind_blade(m):
    # 風の刃 (TOSS・SHOTS): 横に寝た三日月。前 (-Z) へ飛ぶ。刃の縁と、後ろへ流れる筋
    n = 9
    for k in range(n):
        t = k / (n - 1) * 2 - 1
        a = t * 1.15
        p = np.array([math.sin(a) * 13, 4.0, -math.cos(a) * 6.0])
        w = 4.2 * (1 - abs(t) ** 1.6) + 0.6
        m.add(f"edge{k}", ell("body", "wind", p, (w * 0.95, 1.8 * (1 - abs(t) * 0.6), w * 0.95), segs=7, rings=2))
        m.add(f"streak{k}", cone("body", "wind", p + np.array([0, 0, 1.0]), (t * 0.25, 0, 1), 7 + 6 * (1 - abs(t)), w * 0.45, sides=5, rings=1))
    m.add("core", ell("body", "wind", (0, 4.0, -5.6), (5.6, 2.4, 2.8), segs=8, rings=2))


def p_spore(m):
    # 胞子の玉 (RISE・SCATTER): 床から苔の茎が伸び、先の玉がはじけそうに膨らむ
    rng = np.random.default_rng(14)
    for k, (x, z, h, r) in enumerate(((0, 0, 14, 6.0), (6, 3, 9, 3.6), (-5, 4, 8, 3.2), (2, -6, 7, 2.8))):
        m.add(f"stalk{k}", tub("body", "moss", [np.array([x, -4.0, z]), np.array([x * 1.05, h - r * 0.6, z * 1.05])], [r * 0.45, r * 0.3], None, sides=6))
        c = np.array([x * 1.05, h, z * 1.05])
        m.add(f"pod{k}", ell("body", "spore", c, (r, r * 0.9, r), segs=9, rings=3), core=(k == 0))
        for j in range(5):
            d = nrm(rng.normal(size=3) + np.array([0, 0.6, 0]))
            m.add(f"bump{k}{j}", ell("body", "moss", c + d * r * 0.85, (r * 0.28, r * 0.22, r * 0.28), segs=5, rings=1))
    base_debris(m, "moss", 7, seed=15)


def p_wisp_orb(m):
    # 鬼火の玉 (TOSS・FALL): 光る芯と、後ろ (+Z) へなびく三本の炎の尾
    m.add("core", ell("body", "wisp", (0, 6, 0), (5.4, 5.4, 5.4), segs=10, rings=4), core=True)
    for k, (dx, dy) in enumerate(((0, 0), (2.8, 2.0), (-2.6, -1.4), (0.4, 3.2))):
        m.add(f"tail{k}", cone("body", "wisp", (dx * 0.7, 6 + dy * 0.7, 3.0), (dx * 0.06, dy * 0.06, 1), 16 - k * 3.0, 3.6 - k * 0.6, sides=6, rings=2,
                               bend=(dx * 0.8, 1.6, 0)))
    for k in range(6):
        a = k / 6 * math.tau
        m.add(f"flick{k}", cone("body", "wisp", (math.cos(a) * 3.8, 6 + math.sin(a) * 3.8, 0.5), (math.cos(a) * 0.5, math.sin(a) * 0.5, 0.6), 4.5, 1.4,
                                sides=4, rings=1))


def p_net(m):
    # 投げ網 (TOSS): 床に落ちて広がった網。縁に赤い浮き、網目は縄の格子、真ん中に重り
    R = 13.0
    n = 7
    for i in range(n):
        t = (i / (n - 1)) * 2 - 1
        h = math.sqrt(max(0.0, 1 - t * t)) * R
        x = t * R
        sag = lambda u: 1.6 + 1.4 * (1 - u * u)
        m.add(f"rx{i}", tub("body", "rope", [np.array([x, sag(t) * 0.4 + 0.6, -h]), np.array([x, sag(0) * 0.5 + 0.6, 0]), np.array([x, sag(t) * 0.4 + 0.6, h])],
                            [0.45, 0.45, 0.45], None, sides=4))
        m.add(f"rz{i}", tub("body", "rope", [np.array([-h, sag(t) * 0.4 + 0.6, x]), np.array([0, sag(0) * 0.5 + 0.6, x]), np.array([h, sag(t) * 0.4 + 0.6, x])],
                            [0.45, 0.45, 0.45], None, sides=4))
    for k in range(10):
        a = k / 10 * math.tau
        m.add(f"float{k}", ell("body", "float", (math.cos(a) * R, 1.4, math.sin(a) * R), (1.6, 1.3, 1.6), segs=6, rings=2))
    m.add("weight", ell("body", "iron", (0, 2.2, 0), (2.6, 1.8, 2.6), segs=7, rings=2), core=True)


def p_whirlwind(m):
    # 灰の旋風 (RISE): 下が細く上が広がる渦の漏斗。帯を重ねて少しずつずらし、熾の粒が混じる
    m.bone("t1", "body", (0, 20, 0))
    m.add("funnel_lo", tub("body", "ash", [np.array(p) for p in ((0, -4, 0), (0.6, 10, 0.4), (0, 22, 0))], [2.2, 3.8, 6.0], None, sides=10))
    m.add("funnel_hi", tub("t1", "ash", [np.array(p) for p in ((0, 20, 0), (-0.8, 34, -0.4), (0, 46, 0))], [5.8, 8.6, 11.0], None, sides=12,
                           caps=(True, True)))
    for k in range(9):
        y = 1 + k * 5.2
        r = 2.6 + y * 0.19 + 0.8
        b = "body" if y < 20 else "t1"
        m.add(f"band{k}", ell(b, "wind", (math.sin(k * 1.3) * 0.8, y, math.cos(k * 1.3) * 0.8), (r, 1.5, r * 0.92), segs=11, rings=1, rz=10 * (-1) ** k,
                               ry=k * 37))
    rng = np.random.default_rng(16)
    for k in range(10):
        y = rng.uniform(4, 44)
        a = rng.uniform(0, math.tau)
        r = 2.6 + y * 0.19
        m.add(f"cinder{k}", ell("body" if y < 20 else "t1", "magma", (math.cos(a) * r, y, math.sin(a) * r), (0.9, 0.9, 0.9), segs=4, rings=1))
    base_debris(m, "ash", 7, seed=17)


def p_light_pillar(m):
    # 光の柱 (RISE): 空から床へ刺さる白金の柱。床に金の輪、柱のまわりに浮く三つの輪
    m.bone("t1", "body", (0, 30, 0))
    m.add("col", tub("body", "light", [np.array(p) for p in ((0, -4, 0), (0, 30, 0))], [5.6, 4.8], None, sides=10))
    m.add("top", tub("t1", "light", [np.array(p) for p in ((0, 28, 0), (0, 90, 0))], [4.8, 3.4], None, sides=10, caps=(False, True)))
    for k, y in enumerate((6, 20, 40)):
        m.add(f"halo{k}", ell("body" if y < 30 else "t1", "gold", (0, y, 0), (8.4 - k * 0.8, 0.7, 8.4 - k * 0.8), segs=14, rings=1))
    m.add("disc", ell("body", "gold", (0, 0.6, 0), (11, 0.6, 11), segs=16, rings=1))
    for k in range(8):
        a = k / 8 * math.tau
        m.add(f"ray{k}", cone("body", "light", (math.cos(a) * 9, 0.8, math.sin(a) * 9), (math.cos(a), 0.05, math.sin(a)), 6, 1.2, sides=4, rings=1))


def p_feather(m):
    # 羽根 (FALL・TOSS): 先が下 (原点)。白い羽根の大きな一枚、羽軸は金。斜めに刺さる
    tip = np.array([0.0, -1.0, 0.0])
    d = nrm(np.array([0.15, 1.0, 0.1]))
    L = 30.0
    m.add("quill", tub("body", "gold", [tip, tip + d * L], [0.7, 0.45], None, sides=5))
    side = nrm(np.cross(d, [0.0, 0.0, 1.0]))
    for k in range(10):
        t = 0.18 + k * 0.08
        w = 5.2 * math.sin(min(1.0, t * 1.15) * math.pi) + 0.8
        c = tip + d * (L * t)
        for sgn in (1, -1):
            m.add(f"vane{k}{'r' if sgn > 0 else 'l'}", ell("body", "plume", c + side * sgn * w * 0.5 + d * 1.6, (w * 0.55, 2.4, 0.6), segs=6, rings=1,
                                                         rz=-sgn * 28))


def p_weight(m):
    # 秤の錘 (FALL): 月の銀の分銅。上に輪、胴に満ち欠けの刻み。床に割れた欠片
    m.add("body_", tub("body", "iron", [np.array(p) for p in ((0, 0, 0), (0, 10, 0), (0, 16, 0))], [9.0, 8.2, 5.2], None, sides=10, caps=(True, True)), core=True)
    m.add("neck", tub("body", "iron", [np.array(p) for p in ((0, 15, 0), (0, 19, 0))], [3.0, 3.0], None, sides=8))
    m.add("ring", ell("body", "gold", (0, 22, 0), (4.4, 3.4, 1.2), segs=12, rings=1))
    for k in range(4):
        a = k / 4 * math.tau + 0.4
        m.add(f"phase{k}", ell("body", "pearl", (math.cos(a) * 8.6, 6, math.sin(a) * 8.6), (2.0, 2.0, 0.8), segs=8, rings=1, ry=-math.degrees(a) + 90))
    m.add("band", ell("body", "gold", (0, 10.5, 0), (8.6, 0.9, 8.6), segs=14, rings=1))
    base_debris(m, "rock", 11, seed=18)


BUILDERS = {"bone_spike": p_bone_spike, "rock": p_rock, "bone_shard": p_bone_shard, "tentacle": p_tentacle, "ink_blob": p_ink_blob,
            "crystal_spike": p_crystal_spike, "fireball": p_fireball, "lava_pillar": p_lava_pillar, "ice_spike": p_ice_spike, "icicle": p_icicle,
            "water_spout": p_water_spout, "coral_spear": p_coral_spear, "lightning": p_lightning, "ash_ball": p_ash_ball,
            "shell_spike": p_shell_spike, "pearl": p_pearl, "arrow": p_arrow, "arrow_volley": p_arrow_volley, "wind_blade": p_wind_blade,
            "spore": p_spore, "wisp_orb": p_wisp_orb, "net": p_net, "whirlwind": p_whirlwind, "light_pillar": p_light_pillar,
            "feather": p_feather, "weight": p_weight}


def idle_gait(kind, m):
    g = Gait("idle", 1.6)
    if "t1" in m.bones:
        g.rot("t1", W(0).s(8, 1, 0, "t"), None, W(0).s(6, 1, 90, "t"))
    if "t2" in m.bones:
        g.rot("t2", W(0).s(14, 1, 60, "t"), None, W(0).s(10, 1, 150, "t"))
    if kind in ("fireball", "ash_ball", "rock", "pearl", "wisp_orb"):
        g.rot("body", None, W(0).s(0, 1, 0, "t").s(0, 1, 0, "t"))
        g.bones["body"]["rot"] = (W(0).s(40, 1, 0, "t"), W(0), W(0))
    if kind in ("water_spout", "lava_pillar", "whirlwind"):
        g.rot("body", None, W(0).s(30, 1, 0, "t"))
    if not g.bones:
        g.rot("body", W(0).s(0.5, 1, 0, "t"))
    return g


def build(kind):
    m = Mount("prop_" + kind, kind)
    m.bone("body", "root", (0, 0, 0))
    BUILDERS[kind](m)
    m.pal = dict(mats=mats(), glow_mats=[g for g in GLOW])
    m.detail = None
    m.hint = 256
    return m


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    kinds = args or list(BUILDERS)
    REVIEW.mkdir(parents=True, exist_ok=True)
    tiles = []
    for kind in kinds:
        m = build(kind)
        size, atlas, glow = blp.paint_mount(m, m.pal, m.detail, m.hint)
        g = idle_gait(kind, m)
        name = "prop_" + kind
        if "--no-install" not in sys.argv:
            blp.install(m, name, size, atlas, glow, [g], (4, 8))
        lo = np.min([f.verts.min(0) for f in m.F], axis=0)
        hi = np.max([f.verts.max(0) for f in m.F], axis=0)
        c = (lo + hi) / 2
        sc = 220 / max(1.0, float(np.max(hi - lo)))
        im = view(m.F, atlas, glow, 210, 14, (300, 300), c, sc, ground=False)
        tiles.append((kind, im, sum(len(f.verts) - 2 for f in m.F)))
        print(kind, "tris", tiles[-1][2], flush=True)
    cols = 4
    rows = (len(tiles) + cols - 1) // cols
    cv = Image.new("RGB", (cols * 310 + 10, rows * 334 + 10), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    for i, (kind, im, tris) in enumerate(tiles):
        x, y = 10 + (i % cols) * 310, 10 + (i // cols) * 334
        d.text((x, y), f"{kind}  {tris} tris", font=voxel.font(15), fill=(150, 232, 255))
        cv.paste(im, (x, y + 24))
    cv.save(REVIEW / "props_sheet.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
