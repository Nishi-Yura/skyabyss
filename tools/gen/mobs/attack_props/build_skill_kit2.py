#!/usr/bin/env python3
"""粒だけだった三つの仕組みの形 (2026-11-12 の声「上の3つを作る」= 粉の技・伝説の書き換え・型の溜まるものの放ち)。

    python -X utf8 -I tools/gen/mobs/attack_props/build_skill_kit2.py [種類 ...] [--no-install]

  粉の技 (item/extra/PowderArts)        pa_quake 地割れ / pa_whirl つむじ / pa_ebb 引き潮 / pa_brand 熾の印 / pa_brand_burst 熾の印が爆ぜる / pa_curse 骨の呪い
  伝説の書き換え (rpg/skill/LegendRewrites) 巨神の系統ごと lg_<whale|tide|storm|ember|moon|light>_<rise|wave> と lg_whale_fall・lg_moon_fall
  型の放ち (rpg/skill/Gauges)           gx_<smash|cleaver|forge|temper|ash|thunder|fire|freeze|pierce|trap|shadow|lone|ward|tide|curse>
どれも人の技なので青緑の印 (輪・芯) を持つ。wave は半径 16 = 1 ブロック (大きさ = 半径)。
確かめの絵は docs/review/skill_props/kit2_<組>.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build_skill_kit as K  # noqa: E402
from build_skill_kit import *  # noqa: E402,F401,F403

DEEPS = [(30, 32, 38), (48, 50, 58), (70, 72, 80), (98, 100, 108)]
STORMY = [(200, 170, 40), (240, 214, 70), (255, 240, 130), (255, 252, 210)]
ICE = [(120, 180, 220), (160, 210, 240), (200, 232, 252), (236, 248, 255)]
SHADE = [(20, 10, 30), (44, 22, 64), (80, 40, 110), (130, 80, 170)]


def k2mats():
    m = K.kmats()
    m.update({"deeps": M_(DEEPS, lit=0.4, streak=0.05), "stormy": M_(STORMY, base=0.9, lit=0.1, noise=0.0, min=1),
              "icy": M_(ICE, base=0.6, lit=0.25), "shade": M_(SHADE, base=0.4, lit=0.3)})
    return m


K2GLOW = set(K.KGLOW) | {"stormy"}


def ring_wave(m, n, fn, r=16):
    ring_of(m, "w", n, r, fn)
    sig_loop(m, r, 0.4)


def flame(m, name, base, h, w, mat="ember", lean=(0.0, 0.0)):
    b = P(*base)
    m.add(name, tub("body", mat, [b, b + P(lean[0] * 0.4, h * 0.5, lean[1] * 0.4), b + P(lean[0], h, lean[1])], [w, w * 0.7, 0.15], None, sides=5))


def bolt_zig(m, name, mat, top, bottom, n=5, amp=2.6, w=1.1):
    pts = []
    for k in range(n + 1):
        t = k / n
        p = P(*top) * (1 - t) + P(*bottom) * t
        if 0 < k < n:
            p = p + P(amp * (1 if k % 2 else -1), 0, amp * 0.4 * (1 if k % 3 else -1))
        pts.append(p)
    m.add(name, tub("body", mat, pts, [w] * len(pts), None, sides=4))


# ================================================================ 粉の技
def pa_quake(m):
    # 地割れ: 外へ傾いて割れ上がる岩の板の輪と、割れ目の筋
    def slab(m, nm, a, x, z):
        out = P(math.cos(a), 0, math.sin(a))
        m.add(nm, tub("body", "granite", [P(x, -6, z), P(x, 2, z) + out * 2.0, P(x, 6, z) + out * 3.4], [3.4, 2.8, 1.2], None, sides=4, phase=30))
    ring_wave(m, 9, slab)
    for k in range(6):
        a = k / 6 * math.tau
        m.add(f"crack{k}", tub("body", "sigil", [P(math.cos(a) * 3, 0.8, math.sin(a) * 3), P(math.cos(a + 0.2) * 9, 0.8, math.sin(a + 0.2) * 9),
                                                 P(math.cos(a) * 14, 0.8, math.sin(a) * 14)], [0.5, 0.4, 0.2], None, hint=(0, 1, 0), sides=4))
    base_debris(m, "granite", 14, seed=71)


def pa_whirl(m):
    # つむじ: 下が細く上が広がる二本の風の帯と、巻き上がる葉
    for k in range(2):
        pts, rad = [], []
        for j in range(13):
            t = j / 12
            a = k * math.pi + t * 7.0
            r = 2 + t * 10
            pts.append(P(math.cos(a) * r, -4 + t * 34, math.sin(a) * r))
            rad.append(0.6 + t * 1.2)
        m.add(f"band{k}", tub("body", "windp" if k == 0 else "wind", pts, rad, None, sides=4))
    for k in range(5):
        a = k * 1.4
        m.add(f"leaf{k}", ell("body", "moss", (math.cos(a) * (4 + k * 1.6), 6 + k * 5, math.sin(a) * (4 + k * 1.6)), (1.4, 0.3, 0.8), segs=4, rings=1, ry=k * 50))
    sig_loop(m, 6, 0.5)


def pa_ebb(m):
    # 引き潮: 水の柱が噴き、まわりへ弧を描いて降る水と泡の輪
    m.add("col", tub("body", "seaw", [P(0, -4, 0), P(0, 12, 0), P(0, 20, 0)], [3.2, 2.2, 0.8], None, sides=8))
    m.add("crown", ell("body", "foam", (0, 19, 0), (3.0, 1.6, 3.0), segs=8, rings=2))
    for k in range(6):
        a = k / 6 * math.tau
        d = P(math.cos(a), 0, math.sin(a))
        m.add(f"arc{k}", tub("body", "water", [P(0, 18, 0) + d * 1.5, P(0, 21, 0) + d * 6, P(0, 12, 0) + d * 10, P(0, 2, 0) + d * 11.5],
                             [1.0, 0.9, 0.7, 0.4], None, sides=4))
    loop(m, "foam_ring", "foam", 11.5, 0.6, 0.8)
    sig_loop(m, 6, 0.5)


def pa_brand(m):
    # 熾の印: 足もとの赤熱した印の輪と、短い炎の舌
    loop(m, "ring", "ironhot", 9, 0.8, 0.8)
    m.add("brand", ell("body", "ember", (0, 0.8, 0), (4.0, 0.5, 4.0), segs=6, rings=1))
    for k in range(6):
        a = k / 6 * math.tau
        flame(m, f"f{k}", (math.cos(a) * 9, 0, math.sin(a) * 9), 7 + (k % 2) * 3, 1.4, "ember" if k % 2 else "lantern")
    sig_loop(m, 4, 1.2)


def pa_brand_burst(m):
    # 熾の印が爆ぜる: 外へ開く炎の花弁と、真ん中の赤熱した芯
    m.add("core", ell("body", "lantern", (0, 6, 0), (4.0, 4.0, 4.0), segs=8, rings=3))
    for k in range(8):
        a = k / 8 * math.tau
        d = P(math.cos(a), 0, math.sin(a))
        m.add(f"petal{k}", tub("body", "ember", [P(0, 4, 0) + d * 2, P(0, 9, 0) + d * 7, P(0, 6, 0) + d * 12], [2.4, 1.6, 0.2], None, sides=5))
    for k in range(5):
        a = k * 1.25
        m.add(f"spark{k}", ell("body", "lantern", (math.cos(a) * 9, 13 + k, math.sin(a) * 9), (0.7, 0.7, 0.7), segs=4, rings=1))
    sig_loop(m, 6, 0.5)


def pa_curse(m):
    # 骨の呪い: 床から上がって頭の上で閉じる肋骨の籠と、紫の芯
    for k in range(6):
        a = k / 6 * math.tau
        base = P(math.cos(a) * 9, -4, math.sin(a) * 9)
        m.add(f"rib{k}", tub("body", "bone", [base, P(math.cos(a) * 10, 12, math.sin(a) * 10), P(math.cos(a) * 3, 26, math.sin(a) * 3)], [1.6, 1.2, 0.5],
                             None, sides=5))
    m.add("orb", ell("body", "violet", (0, 27, 0), (2.8, 2.8, 2.8), segs=8, rings=2))
    m.add("eye", ell("body", "sigil", (0, 27, -2.4), (1.0, 1.0, 0.6), segs=6, rings=1))
    sig_loop(m, 9, 0.5)


# ================================================================ 伝説の書き換え (巨神の系統)
def lg_whale_rise(m):
    # 岩鯨: 深い岩から立ち上がる骨の背鰭
    pts = [P(0, -6, 0), P(1, 10, 2), P(-1, 22, 6), P(-3, 30, 12)]
    m.add("fin", tub("body", "deeps", pts, [6.0, 4.6, 2.6, 0.4], None, hint=(1, 0, 0), sides=5))
    for k in range(4):
        p = pts[0] * (1 - k / 4) + pts[2] * (k / 4)
        m.add(f"ridge{k}", cone("body", "bone", tuple(p + P(0, 2, -3.5)), (0, 0.5, -1), 4 - k * 0.5, 1.4, sides=4, rings=1))
    m.add("rib", tub("body", "bone", [P(4, -6, 0), P(8, 8, 1), P(5, 18, 2)], [1.6, 1.2, 0.5], None, sides=5))
    m.add("eye", ell("body", "sigil", (0.5, 14, -3.2), (1.2, 1.2, 0.6), segs=6, rings=1))
    base_debris(m, "deeps", 9, seed=81)
    sig_loop(m, 10)


def lg_whale_wave(m):
    # 岩鯨の輪: 深い岩の板と骨の棘が交互に突き上がる
    def piece(m, nm, a, x, z):
        out = P(math.cos(a), 0, math.sin(a))
        k = int(round(a / math.tau * 10)) % 2
        if k == 0:
            m.add(nm, tub("body", "deeps", [P(x, -6, z), P(x, 4, z) + out * 2.2, P(x, 8, z) + out * 3.6], [3.6, 2.6, 0.8], None, sides=4, phase=20))
        else:
            m.add(nm, cone("body", "bone", (x, -6, z), tuple(P(0, 1, 0) + out * 0.4), 14, 1.8, sides=5, rings=2))
    ring_wave(m, 10, piece)
    base_debris(m, "deeps", 14, seed=82)


def lg_whale_fall(m):
    # 岩鯨の星: 骨の背を持つ深い岩の塊 (落ちる)
    m.add("rock", ell("body", "deeps", (0, 8, 0), (7, 6, 7), segs=7, rings=3, ry=20))
    for k in range(4):
        m.add(f"ridge{k}", cone("body", "bone", (-4.5 + k * 3, 13, 0), (0, 1, 0), 4.5 - abs(k - 1.5), 1.4, sides=4, rings=1))
    m.add("glint", ell("body", "sigil", (0, 8, -6.4), (1.6, 1.6, 0.6), segs=6, rings=1))


def lg_tide_rise(m):
    # 潮殻: 巻き貝の殻の尖塔と、巻きつく水
    pts, rad = [], []
    for j in range(9):
        t = j / 8
        pts.append(P(math.cos(t * 6) * (1 - t) * 1.5, -6 + t * 36, math.sin(t * 6) * (1 - t) * 1.5))
        rad.append(5.4 * (1 - t) + 0.4)
    m.add("shell", tub("body", "shell", pts, rad, None, sides=7))
    for k in range(4):
        m.add(f"whorl{k}", ell("body", "coral", tuple(pts[1 + k * 2] + P(0, 0, 0)), (rad[1 + k * 2] * 1.08, 0.8, rad[1 + k * 2] * 1.08), segs=10, rings=1))
    pw, rw = [], []
    for j in range(10):
        t = j / 9
        a = t * 8
        pw.append(P(math.cos(a) * (7 - t * 3), -2 + t * 22, math.sin(a) * (7 - t * 3)))
        rw.append(1.2 - t * 0.8)
    m.add("water", tub("body", "water", pw, rw, None, sides=4))
    sig_loop(m, 9)


def lg_tide_wave(m):
    # 潮殻の輪: 内へ巻く波頭の輪 (頭は泡)
    def crest(m, nm, a, x, z):
        out = P(math.cos(a), 0, math.sin(a))
        m.add(nm, tub("body", "seaw", [P(x, -6, z), P(x, 4, z) + out * 1.5, P(x, 9, z) - out * 0.5, P(x, 9, z) - out * 3.5], [3.0, 2.4, 1.6, 0.4], None,
                       sides=5))
        m.add(nm + "_f", ell("body", "foam", tuple(P(x, 9.6, z) - out * 1.2), (1.8, 1.0, 1.8), segs=6, rings=2))
    ring_wave(m, 12, crest)


def lg_storm_rise(m):
    # 雷翼: 空から地へ折れて落ちる雷と、落ちた所の焦げの輪
    bolt_zig(m, "bolt", "stormy", (0, 44, 0), (0, -2, 0), n=6, amp=3.2, w=1.4)
    bolt_zig(m, "branch", "stormy", (2.6, 30, 0.8), (9, 14, 2), n=3, amp=1.4, w=0.7)
    m.add("strike", ell("body", "lantern", (0, 1, 0), (4, 1.0, 4), segs=8, rings=1))
    loop(m, "scorch", "night", 7, 0.5, 0.7)
    sig_loop(m, 10)


def lg_storm_wave(m):
    # 雷翼の輪: 黄に光る晶の棘と、棘を結ぶ雷の筋
    n = 10
    tips = []
    for k in range(n):
        a = k / n * math.tau
        x, z = math.cos(a) * 16, math.sin(a) * 16
        out = P(math.cos(a), 0, math.sin(a))
        m.add(f"spike{k}", cone("body", "stormy", (x, -6, z), tuple(P(0, 1, 0) + out * 0.3), 15, 2.0, sides=4, rings=1))
        tips.append(P(x, 6, z) + out * 2.6)
    for k in range(0, n, 2):
        bolt_zig(m, f"arc{k}", "stormy", tuple(tips[k]), tuple(tips[(k + 1) % n]), n=2, amp=1.0, w=0.35)
    sig_loop(m, 16, 0.4)


def lg_ember_rise(m):
    # 余燼: 炭の台から立つ炎の柱
    for k in range(5):
        a = k / 5 * math.tau
        m.add(f"coal{k}", ell("body", "basalt", (math.cos(a) * 5, 0.5, math.sin(a) * 5), (2.6, 1.8, 2.6), segs=5, rings=2, ry=k * 40))
        m.add(f"glow{k}", ell("body", "ironhot", (math.cos(a) * 5, 1.6, math.sin(a) * 5), (1.4, 0.8, 1.4), segs=5, rings=1))
    flame(m, "pillar", (0, -4, 0), 34, 4.6, "ember", (1.5, -1.0))
    flame(m, "inner", (0, -4, 0), 24, 2.6, "lantern", (-0.5, 0.5))
    for k in range(3):
        a = k / 3 * math.tau
        flame(m, f"side{k}", (math.cos(a) * 3, -2, math.sin(a) * 3), 14, 1.8, "ember", (math.cos(a) * 2, math.sin(a) * 2))
    sig_loop(m, 9)


def lg_ember_wave(m):
    # 余燼の輪: 炎の舌の輪 (外へ傾いて揺れる)
    def tongue(m, nm, a, x, z):
        flame(m, nm, (x, -4, z), 12 + (int(a * 10) % 3) * 2, 2.4, "ember", (math.cos(a) * 2.5, math.sin(a) * 2.5))
        flame(m, nm + "_i", (x, -4, z), 7, 1.3, "lantern", (math.cos(a), math.sin(a)))
    ring_wave(m, 12, tongue)


def lg_moon_rise(m):
    # 月: 床から立つ白金の三日月の刃
    pts, rad = [], []
    n = 13
    for j in range(n):
        t = j / (n - 1)
        a = math.radians(-115 + t * 230)
        pts.append(P(math.sin(a) * 12, 14 - math.cos(a) * 14, 0))
        rad.append(3.0 * (0.15 + 0.85 * math.sin(math.pi * t)))
    m.add("blade", tub("body", "pearl", pts, rad, None, hint=(0, 0, 1), sides=5))
    m.add("edge", tub("body", "goldl", [P(p[0] * 1.08, 14 - (14 - p[1]) * 1.08, 0) for p in pts[2:-2]], [0.5] * (n - 4), None, hint=(0, 0, 1), sides=4))
    m.add("stem", tub("body", "silver", [P(0, -6, 0), P(0, 1, 0)], [2.0, 1.6], None, sides=6))
    m.add("star", ell("body", "sigil", (0, 16, 0), (1.6, 1.6, 1.6), segs=6, rings=2))
    sig_loop(m, 9)


def lg_moon_wave(m):
    # 月の輪: 外へ開く銀の月の欠片
    def shard(m, nm, a, x, z):
        out = P(math.cos(a), 0, math.sin(a))
        m.add(nm, tub("body", "pearl", [P(x, -6, z), P(x, 5, z) + out * 2.6, P(x, 11, z) + out * 1.0], [2.6, 1.8, 0.3], None, hint=tuple(out), sides=4))
    ring_wave(m, 10, shard)


def lg_moon_fall(m):
    # 落ちる星: 尖った白い星と金の芯
    m.add("core", ell("body", "goldl", (0, 8, 0), (2.6, 2.6, 2.6), segs=8, rings=2))
    for k in range(5):
        a = k / 5 * math.tau
        m.add(f"pt{k}", cone("body", "pearl", (math.cos(a) * 2.2, 8 + math.sin(a) * 2.2, 0), (math.cos(a), math.sin(a), 0), 6, 1.8, sides=4, rings=1))
    m.add("sig", ell("body", "sigil", (0, 8, -2.4), (1.0, 1.0, 0.5), segs=6, rings=1))


def lg_light_rise(m):
    # 神器の光: 地に突き立つ金の光の槍
    m.add("shaft", tub("body", "light", [P(0, -6, 0), P(0, 34, 0)], [1.4, 1.0], None, sides=6))
    m.add("head", cone("body", "goldl", (0, 34, 0), (0, 1, 0), 9, 2.6, sides=4, rings=1))
    for s in (1, -1):
        m.add(f"wing{s}", cone("body", "goldl", (s * 1.6, 31, 0), (s, 0.6, 0), 6, 1.2, sides=4, rings=1))
    loop(m, "halo", "goldl", 4.0, 24, 0.4)
    sig_loop(m, 8)


def lg_light_wave(m):
    # 神器の光の輪: 細い光の柱の輪
    def pil(m, nm, a, x, z):
        m.add(nm, tub("body", "light", [P(x, -6, z), P(x, 12 + (int(a * 7) % 3) * 3, z)], [1.2, 0.6], None, sides=6))
    ring_wave(m, 14, pil)
    loop(m, "outer", "goldl", 17.5, 0.6, 0.5)


# ================================================================ 型の溜まるものの放ち
def gx_smash(m):
    # 岩砕き: 盾の守り手のまわりに落ちる大岩の輪
    def boulder(m, nm, a, x, z):
        m.add(nm, ell("body", "granite", (x, 3, z), (4.2, 4.8, 4.2), segs=6, rings=2, ry=math.degrees(a) * 2))
    ring_wave(m, 7, boulder)
    base_debris(m, "granite", 14, seed=91)


def gx_cleaver(m):
    # 巨神の大鉈: 地に打ち込まれた幻の大鉈の刃
    m.add("blade", tub("body", "steel", [P(0, -2, 0), P(0, 16, 0), P(0, 30, 0)], [10.0, 11.5, 11.0], [1.2, 1.4, 1.6], hint=(1, 0, 0), sides=4, phase=0))
    m.add("edge", tub("body", "sigil", [P(-11, 6, -1.2), P(0, 0.5, -1.2), P(11, 6, -1.2)], [0.6, 0.6, 0.6], None, hint=(0, 0, 1), sides=4))
    m.add("back", tub("body", "granite", [P(-10, 30, 0), P(10, 30, 0)], [2.0, 2.0], None, hint=(0, 1, 0), sides=6))
    m.add("haft", tub("body", "totem", [P(8, 30, 0), P(14, 44, 0)], [1.4, 1.2], None, sides=6))
    sig_loop(m, 12)


def gx_forge(m):
    # 炉開き: 鉄の輪で締めた火の柱
    flame(m, "fire", (0, -4, 0), 34, 4.2, "ember")
    flame(m, "core", (0, -4, 0), 26, 2.4, "lantern")
    for k, y in enumerate((4, 13, 22)):
        loop(m, f"band{k}", "iron", 4.6 - k * 0.9, y, 0.9, n=12)
    sig_loop(m, 7)


def gx_temper(m):
    # 鍛えの極み: 足もとの金床と、散る火花の輪
    m.add("anvil", ell("body", "iron", (0, 7, 0), (7, 2.4, 3.2), segs=6, rings=2))
    m.add("horn", cone("body", "iron", (7, 7.4, 0), (1, 0.05, 0), 5, 1.8, sides=5, rings=1))
    m.add("waist", tub("body", "iron", [P(0, -4, 0), P(0, 5, 0)], [3.6, 2.2], None, sides=6))
    m.add("hot", ell("body", "ironhot", (0, 9.4, 0), (4.5, 0.6, 2.0), segs=6, rings=1))
    for k in range(8):
        a = k / 8 * math.tau
        m.add(f"spark{k}", cone("body", "lantern", (math.cos(a) * 3, 10, math.sin(a) * 3), (math.cos(a), 0.9, math.sin(a)), 5, 0.5, sides=4, rings=1))
    sig_loop(m, 10)


def gx_ash(m):
    # 灰を燃やす: 灰の噴き上がりと、中から覗く燠
    for k in range(5):
        a = k / 5 * math.tau
        m.add(f"plume{k}", ell("body", "silver", (math.cos(a) * 3, 8 + k * 2.4, math.sin(a) * 3), (4.2 - k * 0.4, 3.6, 4.2 - k * 0.4), segs=10, rings=3))
    for k in range(4):
        a = k / 4 * math.tau + 0.4
        m.add(f"ember{k}", ell("body", "ironhot", (math.cos(a) * 4.6, 6 + k * 3, math.sin(a) * 4.6), (1.2, 1.2, 1.2), segs=5, rings=1))
    sig_loop(m, 7)


def gx_thunder(m):
    # 帯電の跳ね: 敵へ落ちる雷と、まわりの帯電の輪
    bolt_zig(m, "bolt", "stormy", (0, 36, 0), (0, 0, 0), n=5, amp=2.4, w=1.1)
    loop(m, "ring", "stormy", 6, 2, 0.5)
    loop(m, "ring2", "stormy", 4, 10, 0.4)
    sig_loop(m, 7)


def gx_fire(m):
    # 熾火の花: 大きく開いた炎の花と、残る熾
    for k in range(10):
        a = k / 10 * math.tau
        d = P(math.cos(a), 0, math.sin(a))
        m.add(f"petal{k}", tub("body", "ember" if k % 2 else "lantern", [P(0, 2, 0) + d * 2, P(0, 10, 0) + d * 8, P(0, 14, 0) + d * 13], [3.0, 2.0, 0.2],
                               None, sides=5))
    m.add("heart", ell("body", "lantern", (0, 5, 0), (4.6, 4.6, 4.6), segs=8, rings=3))
    sig_loop(m, 8)


def gx_freeze(m):
    # 凍え: 敵を閉じこめる氷の棺 (内へ寄る氷の板)
    for k in range(7):
        a = k / 7 * math.tau
        x, z = math.cos(a) * 7, math.sin(a) * 7
        m.add(f"slab{k}", tub("body", "icy", [P(x, -6, z), P(x * 0.85, 14, z * 0.85), P(x * 0.4, 28, z * 0.4)], [3.0, 2.6, 0.4], None, sides=4, phase=45))
    m.add("frost", ell("body", "foam", (0, 0.6, 0), (9, 0.8, 9), segs=12, rings=1))
    sig_loop(m, 10)


def gx_pierce(m):
    # 狙いの貫き (飛ぶ): 大きな骨の槍 (前 = -Z)
    m.add("shaft", tub("body", "bone", [P(0, CY, 14), P(0, CY, -10)], [1.2, 1.0], None, hint=(0, 1, 0), sides=6))
    m.add("head", cone("body", "bone", (0, CY, -10), (0, 0, -1), 8, 2.6, sides=4, rings=1))
    for s in (1, -1):
        m.add(f"barb{s}", cone("body", "bone", (s * 1.6, CY, -9), (s * 0.6, 0, 1), 4, 0.9, sides=4, rings=1))
        m.add(f"fl{s}", ell("body", "sigil", (s * 1.8, CY, 11), (1.8, 0.3, 3.6), segs=4, rings=1))
    m.add("ring", tub("body", "sigil", [P(math.cos(k / 12 * math.tau) * 3.4, CY + math.sin(k / 12 * math.tau) * 3.4, -4) for k in range(13)], [0.35] * 13,
                      None, hint=(0, 0, 1), sides=4, caps=(False, False)))


def gx_trap(m):
    # 罠のはぜ: 閉じる骨の顎 (上下の歯の列)
    for s, y in ((1, 0.0), (-1, 12.0)):
        pts = [P(math.cos(k / 14 * math.tau) * 8, y, math.sin(k / 14 * math.tau) * 8) for k in range(15)]
        m.add(f"jaw{s}", tub("body", "bone", pts, [1.6] * 15, None, hint=(0, 1, 0), sides=5, caps=(False, False)))
        for k in range(10):
            a = k / 10 * math.tau + (0.3 if s < 0 else 0)
            m.add(f"t{s}{k}", cone("body", "bone", (math.cos(a) * 8, y, math.sin(a) * 8), (0, s, 0), 5, 1.2, sides=4, rings=1))
    m.add("hinge", tub("body", "bone", [P(8, 0, 0), P(10, 6, 0), P(8, 12, 0)], [1.6, 1.6, 1.6], None, sides=5))
    sig_loop(m, 10)


def gx_shadow(m):
    # 影の爆ぜ: 影から突き出る爪
    for k in range(6):
        a = k / 6 * math.tau
        base = P(math.cos(a) * 3, -4, math.sin(a) * 3)
        tip = P(math.cos(a) * 6, 16 + (k % 2) * 4, math.sin(a) * 6)
        m.add(f"claw{k}", tub("body", "shade", [base, base + P(math.cos(a) * 3, 9, math.sin(a) * 3), tip], [2.0, 1.4, 0.2], None, sides=4))
    m.add("pool", ell("body", "shade", (0, 0.4, 0), (8, 0.7, 8), segs=12, rings=1))
    m.add("eye", ell("body", "violet", (0, 8, 0), (2.0, 1.2, 2.0), segs=6, rings=1))
    sig_loop(m, 9)


def gx_lone(m):
    # 孤りの一撃: 縦に裂ける銀の十字の斬り跡
    m.add("v", ell("body", "silver", (0, 16, 0), (1.4, 16, 0.8), segs=4, rings=1))
    m.add("h", ell("body", "silver", (0, 18, 0), (10, 1.2, 0.8), segs=4, rings=1))
    m.add("glow", ell("body", "sigil", (0, 18, -0.6), (2.0, 2.0, 0.5), segs=4, rings=1))
    for k in range(4):
        a = k / 4 * math.tau + math.pi / 4
        m.add(f"ray{k}", cone("body", "light", (math.cos(a) * 2.4, 18 + math.sin(a) * 2.4, 0), (math.cos(a), math.sin(a), 0), 5, 0.5, sides=4, rings=1))
    sig_loop(m, 6)


def gx_ward(m):
    # 調べの守り: 大きく広がる灯籠の輪
    def lamp(m, nm, a, x, z):
        m.add(nm, tub("body", "totem", [P(x, -4, z), P(x, 7, z)], [0.8, 0.6], None, sides=6))
        m.add(nm + "_l", ell("body", "lantern", (x, 9, z), (1.8, 2.2, 1.8), segs=6, rings=2))
        m.add(nm + "_r", cone("body", "goldl", (x, 11, z), (0, 1, 0), 2.4, 2.2, sides=6, rings=1))
    ring_wave(m, 8, lamp)
    loop(m, "thread", "goldl", 16, 9, 0.25)


def gx_tide(m):
    # 満ち潮: 前へ巻いて砕ける大波 (前 = -Z)
    n = 9
    for k in range(n):
        x = -16 + k * 4
        h = 18 + 4 * math.sin(math.pi * k / (n - 1))
        m.add(f"wave{k}", tub("body", "seaw", [P(x, -6, 4), P(x, h * 0.6, 3), P(x, h, -2), P(x, h - 4, -7)], [3.0, 2.6, 1.6, 0.4], None, sides=5))
        m.add(f"foam{k}", ell("body", "foam", (x, h - 0.5, -3.5), (2.2, 1.4, 2.2), segs=6, rings=2))
    sig_loop(m, 12)


def gx_curse(m):
    # 呪いの爆ぜ: 紫の髑髏と、まわりを巻く鎖
    m.add("skull", ell("body", "bone", (0, 14, 0), (5.0, 5.4, 5.0), segs=8, rings=3))
    m.add("jaw", ell("body", "bone", (0, 9.4, -1), (3.4, 1.6, 3.4), segs=6, rings=2))
    for s in (1, -1):
        m.add(f"eye{s}", ell("body", "violet", (s * 2.0, 14.6, -4.4), (1.3, 1.5, 0.6), segs=6, rings=1))
    for k in range(10):
        a = k / 10 * math.tau
        m.add(f"link{k}", ell("body", "iron", (math.cos(a) * 8, 6 + k * 1.4, math.sin(a) * 8), (1.6, 0.8, 1.0), segs=6, rings=1,
                              ry=-math.degrees(a) + (90 if k % 2 else 0)))
    m.add("haze", ell("body", "curse", (0, 2, 0), (8, 1.4, 8), segs=10, rings=1))
    sig_loop(m, 10)


PA = ("quake", "whirl", "ebb", "brand", "brand_burst", "curse")
LG = ("whale_rise", "whale_wave", "whale_fall", "tide_rise", "tide_wave", "storm_rise", "storm_wave", "ember_rise", "ember_wave", "moon_rise", "moon_wave",
      "moon_fall", "light_rise", "light_wave")
GX = ("smash", "cleaver", "forge", "temper", "ash", "thunder", "fire", "freeze", "pierce", "trap", "shadow", "lone", "ward", "tide", "curse")
BUILDERS = {}
for k in PA:
    BUILDERS["pa_" + k] = globals()["pa_" + k]
for k in LG:
    BUILDERS["lg_" + k] = globals()["lg_" + k]
for k in GX:
    BUILDERS["gx_" + k] = globals()["gx_" + k]
FLYING = {"gx_pierce"}
SPIN = {"pa_whirl", "gx_thunder", "gx_ward", "lg_light_wave"}


def build(kind):
    m = Mount("prop_" + kind, kind)
    m.bone("body", "root", (0, CY, 0) if kind in FLYING else (0, 0, 0))
    BUILDERS[kind](m)
    m.pal = dict(mats=k2mats(), glow_mats=sorted(K2GLOW))
    m.detail = None
    m.hint = 256
    return m


def idle(kind, m):
    g = Gait("idle", 1.6)
    if kind in SPIN:
        g.rot("body", None, W(0).s(40, 1, 0, "t"), None)
    elif kind in FLYING:
        g.rot("body", W(0).s(3, 1, 0, "t"))
    else:
        g.rot("body", W(0).s(0.6, 1, 0, "t"))
    return g


GROUPS = {"powder": ["pa_" + k for k in PA], "legend": ["lg_" + k for k in LG], "gauge": ["gx_" + k for k in GX]}


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    kinds = args or list(BUILDERS)
    REVIEW.mkdir(parents=True, exist_ok=True)
    tiles = {}
    for kind in kinds:
        m = build(kind)
        size, atlas, glow = blp.paint_mount(m, m.pal, m.detail, m.hint)
        if "--no-install" not in sys.argv:
            blp.install(m, "prop_" + kind, size, atlas, glow, [idle(kind, m)], (4, 8))
        lo = np.min([f.verts.min(0) for f in m.F], axis=0)
        hi = np.max([f.verts.max(0) for f in m.F], axis=0)
        c = (lo + hi) / 2
        sc = 220 / max(1.0, float(np.max(hi - lo)))
        tiles[kind] = (view(m.F, atlas, glow, 210, 24, (300, 300), c, sc, ground=False), sum(len(f.verts) - 2 for f in m.F))
        print(kind, "tris", tiles[kind][1], flush=True)
    for gname, keys in GROUPS.items():
        keys = [k for k in keys if k in tiles]
        if not keys:
            continue
        cols = 5
        rows = (len(keys) + cols - 1) // cols
        cv = Image.new("RGB", (cols * 310 + 10, rows * 334 + 10), (16, 16, 22))
        d = ImageDraw.Draw(cv)
        for i, k in enumerate(keys):
            x, y = 10 + (i % cols) * 310, 10 + (i // cols) * 334
            d.text((x, y), f"{k}  {tiles[k][1]} tris", font=voxel.font(15), fill=(150, 232, 255))
            cv.paste(tiles[k][0], (x, y + 24))
        cv.save(REVIEW / f"kit2_{gname}.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
