#!/usr/bin/env python3
"""技の形を増やす (2026-11-12 の声「このモデルの数だけじゃ属性変わった時とか、やり込みとかで少しずつ見た目変わったりとか、
スキル変化とかに対応できないんじゃない？ 単体攻撃だけでなく、範囲攻撃等もっといろんな攻撃の種類があるので、もっと種類は作るべき。
神器の大きな能力 18 個のモデルもお願い」)。rpg/skill/SkillProps と item/mythic/MythicAbilities が使う。

    python -X utf8 -I tools/gen/mobs/attack_props/build_skill_kit.py [種類 ...] [--no-install]

  (職ごとの型 sk_<職>_<slash|wave|aura|zone> は 2026-11-12 に技ごとの形 build_skill_each.py へ替えて外した)
  粉の属性 (武器の粉の技が働くとき、形に添える小さな印) sk_el_<stone|wind|tide|ash|bone>
  技の変化 (刻印・書き換え・変異・秘伝のとき、技の芯に添える) sk_ch_<rune|rewrite|mutation|secret>
  やり込み (技の熟練の段) sk_mastery_3 (青緑の細い棘の輪)・sk_mastery_5 (金と青緑の冠)
  神器の大きな能力 (18) my_<名前>
確かめの絵は docs/review/skill_props/kit_<組>.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build_skill_props as SK  # noqa: E402
from build_skill_props import *  # noqa: E402,F401,F403

CURSE = [(30, 12, 40), (62, 26, 84), (100, 48, 130), (146, 86, 180)]
WINDP = [(170, 210, 220), (200, 232, 238), (226, 246, 250), (246, 254, 255)]
VIOLET = [(90, 40, 150), (130, 70, 200), (176, 120, 240), (220, 180, 255)]
SUN = [(220, 110, 30), (250, 160, 50), (255, 206, 100), (255, 238, 180)]
SCROLL = [(170, 200, 220), (200, 224, 236), (226, 240, 248), (246, 252, 255)]
EMBER = [(160, 40, 20), (220, 80, 30), (255, 140, 50), (255, 210, 120)]
MOTH = [(150, 120, 60), (196, 164, 96), (232, 206, 140), (252, 238, 196)]
NIGHT = [(20, 22, 40), (36, 40, 70), (60, 66, 110), (96, 104, 160)]
SILVER = [(130, 136, 150), (170, 176, 188), (206, 212, 222), (236, 240, 246)]
BLOOD = [(90, 10, 18), (140, 24, 30), (190, 50, 50), (230, 100, 90)]


def kmats():
    m = SK.smats()
    m.update({"curse": M_(CURSE, base=0.55, lit=0.3), "windp": M_(WINDP, base=0.8, lit=0.15, noise=0.0, min=1),
              "violet": M_(VIOLET, base=0.85, lit=0.15, min=1), "sun": M_(SUN, base=0.9, lit=0.1, noise=0.0, min=1),
              "scroll": M_(SCROLL, lit=0.3, streak=0.05), "ember": M_(EMBER, base=0.9, lit=0.1, min=1), "moth": M_(MOTH, lit=0.32),
              "night": M_(NIGHT, lit=0.4), "silver": M_(SILVER, lit=0.45), "blood": M_(BLOOD, base=0.6, lit=0.25, min=1)})
    return m


KGLOW = set(SK.SGLOW) | {"windp", "violet", "sun", "ember", "blood"}
CY = 4.0


def P(*a):
    return np.array(a, dtype=float)


def arc_pts(r, a0, a1, n, y=CY, z_sign=-1):
    """XZ 面の弧 (前 = -Z が膨らむ)。a は度で 0 = 真ん前"""
    out = []
    for k in range(n):
        a = math.radians(a0 + (a1 - a0) * k / (n - 1))
        out.append(P(math.sin(a) * r, y, z_sign * math.cos(a) * r))
    return out


def crescent(m, name, mat, r, span, w, n=11, y=CY, edge=None):
    """前へ膨らむ三日月の刃 (真ん中が太く、端が細い)。edge = 外縁に沿う光る筋の材"""
    pts = arc_pts(r, -span / 2, span / 2, n, y)
    rad = [w * (0.25 + 0.75 * math.sin(math.pi * k / (n - 1))) for k in range(n)]
    m.add(name, tub("body", mat, pts, rad, None, hint=(0, 1, 0), sides=4, phase=45))
    if edge:
        pts2 = arc_pts(r + w * 0.6, -span / 2 + 6, span / 2 - 6, n, y)
        m.add(name + "_edge", tub("body", edge, pts2, [0.5] * n, None, hint=(0, 1, 0), sides=4))


def loop(m, name, mat, r, y, w=0.5, n=24):
    """細い輪 (中は空)。平たい楕円体だと中が塗りつぶされるので、輪はこれで作る"""
    pts = [P(math.cos(k / n * math.tau) * r, y, math.sin(k / n * math.tau) * r) for k in range(n + 1)]
    m.add(name, tub("body", mat, pts, [w] * (n + 1), None, hint=(0, 1, 0), sides=4, caps=(False, False)))


def sig_loop(m, r, y=0.6):
    """人の技の印 (青緑の細い輪と四つの小さな菱)。ring_sigil の、中が空の版"""
    loop(m, "sig_ring", "sigil", r, y, 0.45)
    for k in range(4):
        a = k / 4 * math.tau + math.pi / 4
        m.add(f"sig_d{k}", ell("body", "sigil", (math.cos(a) * r, y + 0.3, math.sin(a) * r), (1.2, 0.6, 1.2), segs=4, rings=1, ry=45))


def ring_of(m, name, n, r, fn, phase=0.0):
    for k in range(n):
        a = phase + k / n * math.tau
        fn(m, f"{name}{k}", a, math.cos(a) * r, math.sin(a) * r)


# ================================================================ 粉の属性 (添える小さな印。半径 6 ほど)
def el_stone(m):
    # 岩の粉の印: 割れて立つ花崗岩の楔の輪 (面の多い板)、根元の割れ目、青緑の芯
    for k in range(5):
        a = k / 5 * math.tau + 0.4
        r = 3 + (k % 2) * 2
        h = 7 + (k * 3) % 5
        out = P(math.cos(a), 0, math.sin(a))
        side = P(-math.sin(a), 0, math.cos(a))
        base = out * r + P(0, -4, 0)
        m.add(f"r{k}", tub("body", "granite", [base - side * 1.4, base + out * 0.6 + P(0, h * 0.6, 0), base + out * 1.4 + P(0, h + 3, 0)], [2.2, 1.8, 0.3], [1.0, 0.8, 0.2],
                           hint=tuple(out), sides=4, phase=45))
        m.add(f"chip{k}", ell("body", "granite", tuple(out * (r + 2.4) + P(0, 0.4, 0)), (0.9, 0.6, 0.8), segs=5, rings=1, ry=k * 50))
        m.add(f"crack{k}", tub("body", "sigil", [P(0, 0.5, 0), out * (r * 0.6) + P(0, 0.5, 0) + side * 0.6, out * r + P(0, 0.5, 0)], [0.3, 0.25, 0.15], None, hint=(0, 1, 0), sides=4))
    m.add("dot", ell("body", "sigil", (0, 0.6, 0), (1.2, 0.5, 1.2), segs=6, rings=1))

def el_wind(m):
    pts, rad = [], []
    for j in range(12):
        t = j / 11
        a = t * 5.5
        r = 1.5 + t * 5
        pts.append(P(math.cos(a) * r, -2 + t * 16, math.sin(a) * r))
        rad.append(1.2 * (1 - t) + 0.25)
    m.add("whirl", tub("body", "windp", pts, rad, None, sides=4))
    m.add("whirl2", tub("body", "wind", [P(-q[0], q[1] * 0.8, -q[2]) for q in pts], [r * 0.8 for r in rad], None, sides=4))
    m.add("dot", ell("body", "sigil", (0, 0.6, 0), (1.2, 0.5, 1.2), segs=6, rings=1))


def el_tide(m):
    m.add("col", tub("body", "seaw", [P(0, -4, 0), P(0, 8, 0)], [2.6, 1.8], None, sides=6))
    for k in range(6):
        a = k / 6 * math.tau
        m.add(f"drop{k}", tub("body", "water", [P(math.cos(a) * 1.5, 8, math.sin(a) * 1.5), P(math.cos(a) * 4.5, 11, math.sin(a) * 4.5),
                                                P(math.cos(a) * 6.5, 7, math.sin(a) * 6.5)], [1.0, 0.8, 0.3], None, sides=4))
    m.add("foam", ell("body", "foam", (0, 9, 0), (2.4, 1.4, 2.4), segs=6, rings=2))


def el_ash(m):
    for k in range(4):
        a = k / 4 * math.tau + 0.3
        x, z = math.cos(a) * 2.5, math.sin(a) * 2.5
        pts = [P(x, -4, z), P(x * 1.2, 5, z * 1.2), P(x * 0.6 + math.cos(a + 1) * 1.5, 12 + k, z * 0.6 + math.sin(a + 1) * 1.5)]
        m.add(f"flame{k}", tub("body", "ember", pts, [2.0, 1.4, 0.2], None, sides=5))
    m.add("core", tub("body", "lantern", [P(0, -4, 0), P(0, 9, 0)], [1.6, 0.2], None, sides=5))
    for k in range(3):
        m.add(f"emb{k}", ell("body", "ember", (math.cos(k * 2.1) * 4, 14 + k * 2, math.sin(k * 2.1) * 4), (0.6, 0.6, 0.6), segs=4, rings=1))


def el_bone(m):
    for k in range(4):
        a = k / 4 * math.tau + 0.8
        base = P(math.cos(a) * 3, -4, math.sin(a) * 3)
        m.add(f"thorn{k}", tub("body", "bone", [base, base + P(math.cos(a) * 1.5, 7, math.sin(a) * 1.5), base + P(math.cos(a) * 4, 12, math.sin(a) * 4)],
                               [1.6, 1.0, 0.2], None, sides=5))
    m.add("knuckle", ell("body", "bone", (0, 0, 0), (2.6, 2.0, 2.6), segs=6, rings=2))
    m.add("dot", ell("body", "sigil", (0, 2, 0), (0.8, 0.8, 0.8), segs=4, rings=1))


# ================================================================ 技の変化 (技の芯に添える)
def ch_rune(m):
    # 刻印: 三枚の金縁の刻み石が浮いて回る
    for k in range(3):
        a = k / 3 * math.tau
        x, z = math.cos(a) * 6, math.sin(a) * 6
        m.add(f"tab{k}", ell("body", "granite", (x, 14, z), (2.2, 3.2, 0.8), segs=4, rings=1, ry=-math.degrees(a) + 90))
        m.add(f"rim{k}", ell("body", "goldl", (x, 14, z), (2.6, 3.6, 0.5), segs=4, rings=1, ry=-math.degrees(a) + 90))
        m.add(f"mark{k}", ell("body", "sigil", (x * 1.13, 14, z * 1.13), (0.9, 1.5, 0.3), segs=4, rings=1, ry=-math.degrees(a) + 90))
    loop(m, "ring", "goldl", 7, 0.6, 0.4)


def ch_rewrite(m):
    # 書き換え: 縦に立つ橙の日輪 (光の輪と八本の光)
    m.add("disc", ell("body", "sun", (0, 18, 0), (5.5, 5.5, 0.8), segs=14, rings=1))
    m.add("core", ell("body", "lantern", (0, 18, 0), (3.0, 3.0, 1.1), segs=10, rings=1))
    for k in range(8):
        a = k / 8 * math.tau
        m.add(f"ray{k}", cone("body", "sun", (math.cos(a) * 5.4, 18 + math.sin(a) * 5.4, 0), (math.cos(a), math.sin(a), 0), 3.6 if k % 2 else 5.2, 1.0,
                              sides=4, rings=1))
    m.add("stem", tub("body", "goldl", [P(0, -4, 0), P(0, 12.5, 0)], [1.0, 0.6], None, sides=6))
    loop(m, "ring", "sun", 6, 0.6, 0.4)


def ch_mutation(m):
    # 変異: ねじれて生える紫の晶の束
    for k, (x, z, h, r, lx, lz) in enumerate(((0, 0, 20, 2.6, 0.1, 0), (3, 1, 13, 1.8, 0.5, 0.2), (-3, 2, 11, 1.6, -0.5, 0.3), (1, -3, 14, 1.8, 0.2, -0.5),
                                               (-2, -2, 9, 1.4, -0.4, -0.4))):
        m.add(f"c{k}", cone("body", "violet", (x, -4, z), (lx, 1, lz), h + 4, r, sides=5, rings=2))
    m.add("dot", ell("body", "sigil", (0, 0.6, 0), (1.4, 0.5, 1.4), segs=6, rings=1))


def ch_secret(m):
    # 秘伝: 開きかけの巻物と、青緑の封の印
    m.add("roll1", tub("body", "scroll", [P(-6, 12, 0), P(6, 12, 0)], [1.6, 1.6], None, hint=(0, 1, 0), sides=8, caps=(True, True)))
    m.add("sheet", ell("body", "paper", (0, 7.5, 0.8), (5.6, 4.2, 0.3), segs=4, rings=1, ry=45 * 0))
    m.add("roll2", tub("body", "scroll", [P(-6, 3, 0.6), P(6, 3, 0.6)], [1.3, 1.3], None, hint=(0, 1, 0), sides=8, caps=(True, True)))
    m.add("seal", ell("body", "sigil", (0, 7.5, 1.4), (1.8, 1.8, 0.4), segs=8, rings=1))
    m.add("stem", tub("body", "totem", [P(0, -4, -0.4), P(0, 2, -0.4)], [0.8, 0.6], None, sides=6))


def mastery_3(m):
    # 熟練 3: 青緑の細い棘の輪
    for k in range(12):
        a = k / 12 * math.tau
        m.add(f"sp{k}", cone("body", "sigil", (math.cos(a) * 8, -4, math.sin(a) * 8), (math.cos(a) * 0.2, 1, math.sin(a) * 0.2), 9 + (k % 2) * 4, 0.8,
                             sides=4, rings=1))
    loop(m, "ring", "sigil", 8.5, 0.6, 0.4)


def mastery_5(m):
    # 極めた技: 金の冠に青緑の玉、外にもう一つの細い輪
    m.add("band", tub("body", "goldl", [P(math.cos(k / 16 * math.tau) * 7, 8, math.sin(k / 16 * math.tau) * 7) for k in range(17)], [1.0] * 17, None, hint=(0, 1, 0), sides=4))
    for k in range(8):
        a = k / 8 * math.tau
        m.add(f"pt{k}", cone("body", "goldl", (math.cos(a) * 7, 8, math.sin(a) * 7), (0, 1, 0), 6 if k % 2 == 0 else 3.5, 1.4, sides=4, rings=1))
        if k % 2 == 0:
            m.add(f"gem{k}", ell("body", "sigil", (math.cos(a) * 7, 14.5, math.sin(a) * 7), (1.0, 1.0, 1.0), segs=6, rings=1))
    m.add("post", tub("body", "goldl", [P(0, -4, 0), P(0, 7, 0)], [0.6, 0.6], None, sides=6))
    loop(m, "ring", "sigil", 11, 0.6, 0.4)


# ================================================================ 神器の大きな能力 (18)
def my_sky_hand(m):
    # 天の手 (落ちる): 光の大きな手、指を開いて下へ
    m.add("palm", ell("body", "light", (0, 14, 0), (6.0, 4.0, 2.4), segs=8, rings=2))
    for k in range(4):
        x = -4.5 + k * 3
        m.add(f"f{k}", tub("body", "light", [P(x, 11, 0), P(x * 1.1, 4, 0.6), P(x * 1.15, -2 + abs(k - 1.5), 1.4)], [1.2, 1.0, 0.7], None, sides=6))
    m.add("thumb", tub("body", "light", [P(5.5, 15, 0), P(9, 11, 0.6), P(10, 7, 1.2)], [1.3, 1.1, 0.8], None, sides=6))
    m.add("wrist", tub("body", "goldl", [P(0, 17, 0), P(0, 24, 0)], [3.4, 3.0], None, sides=8))
    m.add("cuff", ell("body", "sigil", (0, 18, 0), (4.0, 1.0, 2.8), segs=8, rings=1))


def my_warden(m):
    # 番人の誓い (突き出る): 羽で縁どった盾と左右の翼
    m.add("shield", ell("body", "silver", (0, 16, 0), (7.0, 10.0, 1.6), segs=6, rings=2))
    m.add("boss", ell("body", "goldl", (0, 17, -1.4), (2.4, 2.4, 1.0), segs=8, rings=1))
    for s in (1, -1):
        for k in range(5):
            m.add(f"w{s}_{k}", ell("body", "silver" if k % 2 else "scroll", (s * (8 + k * 3.4), 22 - k * 2.2, 0.8), (2.0, 9 - k * 1.0, 0.6), segs=4, rings=1,
                                   rz=s * (25 + k * 9)))
    m.add("stem", tub("body", "silver", [P(0, -6, 0), P(0, 7, 0)], [1.6, 1.4], None, sides=6))
    sig_loop(m, 9)


def my_balance(m):
    # 天と淵の天秤: 金の天秤 (片方の皿に光、片方に淵の黒)
    m.add("post", tub("body", "goldl", [P(0, -6, 0), P(0, 28, 0)], [1.4, 1.0], None, sides=6))
    m.add("beam", tub("body", "goldl", [P(-12, 27, 0), P(12, 25, 0)], [0.8, 0.8], None, hint=(0, 1, 0), sides=6))
    for s, mat, y in ((-1, "light", 18), (1, "night", 16)):
        x = s * 12
        for k in range(3):
            a = k / 3 * math.tau
            m.add(f"ch{s}{k}", tub("body", "gold", [P(x, 26 - (1 if s > 0 else -1), 0), P(x + math.cos(a) * 4, y + 1, math.sin(a) * 4)], [0.25, 0.25], None, sides=4))
        m.add(f"pan{s}", ell("body", "gold", (x, y, 0), (5, 1.0, 5), segs=10, rings=1))
        m.add(f"load{s}", ell("body", mat, (x, y + 1.8, 0), (2.6, 2.0, 2.6), segs=8, rings=2))
    m.add("top", ell("body", "sigil", (0, 29, 0), (1.6, 1.6, 1.6), segs=6, rings=2))
    sig_loop(m, 8)


def my_big_bell(m):
    # 七つ鐘 (落ちる): 大きな青銅の鐘、口に七つの小さな鈴
    m.add("bell", tub("body", "bronze", [P(0, 26, 0), P(0, 18, 0), P(0, 8, 0), P(0, 1, 0)], [3.0, 7.0, 9.0, 12.0], None, sides=14, caps=(True, False)))
    m.add("lip", ell("body", "goldl", (0, 1.2, 0), (12.5, 1.2, 12.5), segs=14, rings=1))
    m.add("crown", tub("body", "bronze", [P(-2.5, 26, 0), P(0, 30, 0), P(2.5, 26, 0)], [1.0, 1.0, 1.0], None, sides=6))
    for k in range(7):
        a = k / 7 * math.tau
        m.add(f"b{k}", ell("body", "goldl", (math.cos(a) * 10, 9, math.sin(a) * 10), (1.6, 2.0, 1.6), segs=6, rings=2))
    m.add("clap", ell("body", "sigil", (0, 2.5, 0), (2.4, 2.4, 2.4), segs=6, rings=2))


def my_melt(m):
    # 溶かし打ち (突き出る): 熔けて垂れる鉄の滴と赤熱の溜まり
    m.add("pool", ell("body", "ironhot", (0, 0.6, 0), (7, 1.2, 7), segs=12, rings=1))
    for k in range(4):
        a = k / 4 * math.tau + 0.5
        x, z = math.cos(a) * 3, math.sin(a) * 3
        m.add(f"drip{k}", tub("body", "ironhot", [P(x, 16 - k, z), P(x * 1.1, 9 - k, z * 1.1), P(x * 1.2, 2, z * 1.2)], [1.0, 1.4, 2.2], None, sides=6))
        m.add(f"drop{k}", ell("body", "magma", (x, 16.5 - k, z), (1.3, 1.6, 1.3), segs=6, rings=2))
    m.add("dot", ell("body", "sigil", (0, 1.8, 0), (1.4, 0.5, 1.4), segs=6, rings=1))


def my_pull(m):
    # 引力 (足もと): 内へ巻く三重の輪と、四方から引く鎖
    for k, (r, y) in enumerate(((16, 1.0), (11, 2.5), (6, 4.0))):
        loop(m, f"ring{k}", "violet" if k != 1 else "night", r, y, 0.7)
    for k in range(4):
        a = k / 4 * math.tau
        for j in range(4):
            r = 15 - j * 3.4
            m.add(f"link{k}_{j}", ell("body", "iron", (math.cos(a) * r, 2 + j * 0.6, math.sin(a) * r), (1.6, 0.8, 1.0), segs=6, rings=1,
                                      ry=-math.degrees(a) + (90 if j % 2 else 0)))
    m.add("core", ell("body", "violet", (0, 6, 0), (3, 3, 3), segs=8, rings=2))
    sig_loop(m, 7, 0.4)


def my_life_lamp(m):
    # 命の灯 (足もと): 赤い命の火を灯した灯籠
    m.add("post", tub("body", "goldl", [P(0, -6, 0), P(0, 12, 0)], [1.4, 1.0], None, sides=6))
    m.add("base", ell("body", "gold", (0, 12, 0), (3.6, 0.8, 3.6), segs=8, rings=1))
    for k in range(4):
        a = k / 4 * math.tau + math.pi / 4
        m.add(f"bar{k}", tub("body", "gold", [P(math.cos(a) * 3.2, 12, math.sin(a) * 3.2), P(math.cos(a) * 3.6, 16.5, math.sin(a) * 3.6),
                                              P(math.cos(a) * 3.0, 21, math.sin(a) * 3.0)], [0.4, 0.4, 0.4], None, sides=4))
    m.add("flame", ell("body", "blood", (0, 16.5, 0), (2.4, 3.6, 2.4), segs=8, rings=2))
    m.add("roof", cone("body", "goldl", (0, 21, 0), (0, 1, 0), 5, 4.6, sides=6, rings=1))
    m.add("drop", ell("body", "blood", (0, 9, 2.0), (0.8, 1.4, 0.8), segs=6, rings=2))
    sig_loop(m, 8)


def my_gaze(m):
    # 凝視 (敵の上): 雲のまぶたが開いた大きな眼。まつげの棘、虹彩の輪、まわりを回る小さな雲 (2026-11-12 作り込み)
    m.add("lid_t", ell("body", "scroll", (0, 12.6, 0.4), (9.4, 3.6, 4.0), segs=14, rings=4))
    m.add("lid_b", ell("body", "scroll", (0, 5.2, 0.4), (8.4, 2.4, 3.6), segs=14, rings=3))
    m.add("eye", ell("body", "pearl", (0, 9, -0.6), (6.4, 3.8, 3.2), segs=12, rings=4))
    m.add("iris", ell("body", "sigil", (0, 9, -3.2), (2.8, 2.8, 0.8), segs=12, rings=2))
    m.add("iris_ring", tub("body", "goldl", [P(math.cos(t) * 2.9, 9 + math.sin(t) * 2.9, -3.5) for t in np.linspace(0, math.tau, 15)], [0.25] * 15, None, sides=4))
    m.add("pupil", ell("body", "night", (0, 9, -3.9), (0.9, 2.0, 0.4), segs=8, rings=1))
    m.add("glint", ell("body", "light", (-1.0, 10.2, -4.1), (0.5, 0.5, 0.2), segs=6, rings=1))
    for k in range(9):
        x = -6.4 + k * 1.6
        m.add(f"lash{k}", cone("body", "night", (x, 11.6 - abs(x) * 0.18, -2.8), (x * 0.05, 1, -0.6), 2.4 - abs(k - 4) * 0.15, 0.3, sides=4))
    for k in range(5):
        a = k / 5 * math.tau
        m.add(f"cloud{k}", ell("body", "scroll", (math.cos(a) * 11.6, 9 + math.sin(a) * 6.0, 1.6), (2.4, 1.6, 1.8), segs=8, rings=2))

def my_new_moon(m):
    # 新しい月 (落ちる): 白金の三日月の重なり
    for k, (r, w, mat) in enumerate(((10, 3.0, "pearl"), (7, 1.6, "goldl"))):
        pts, rad = [], []
        n = 13
        for j in range(n):
            t = j / (n - 1)
            a = math.radians(-130 + t * 260)
            pts.append(P(math.sin(a) * r, 10 + math.cos(a) * r, k * 1.2))
            rad.append(w * (0.2 + 0.8 * math.sin(math.pi * t)))
        m.add(f"cres{k}", tub("body", mat, pts, rad, None, hint=(0, 0, 1), sides=6))
    m.add("star", ell("body", "sigil", (0, 10, 0), (1.6, 1.6, 1.6), segs=6, rings=2))


def my_feather(m):
    # 羽の嵐 (飛ぶ): 金の芯を持つ大きな白い羽根 (前 = -Z)
    m.add("shaft", tub("body", "goldl", [P(0, CY, 8), P(0, CY, -9)], [0.5, 0.3], None, hint=(0, 1, 0), sides=5))
    for s in (1, -1):
        for k in range(6):
            z = 6 - k * 2.6
            w = 3.6 * math.sin(math.pi * (k + 0.5) / 6.5) + 0.6
            m.add(f"v{s}{k}", ell("body", "scroll", (s * w * 0.55, CY, z), (w * 0.6, 0.35, 1.6), segs=4, rings=1, ry=s * 25))
    m.add("tip", ell("body", "sigil", (0, CY, -9.4), (0.7, 0.7, 0.9), segs=4, rings=1))


def my_mist(m):
    # 霞隠れ (突き出る): 霞の渦から首を上げる霞鳥。羽根の段の翼、尾羽、霞の筋 (2026-11-12 作り込み)
    m.add("mist", ell("body", "wind", (0, 2, 0), (10, 2.4, 10), segs=14, rings=2))
    for k in range(3):
        pts = [P(math.cos(t + k * 2.1) * (9 - t), 2.6 + t * 1.2, math.sin(t + k * 2.1) * (9 - t)) for t in np.linspace(0, 4.0, 9)]
        m.add(f"swirl{k}", tub("body", "windp", pts, [0.6, 0.6, 0.55, 0.5, 0.45, 0.4, 0.3, 0.2, 0.1], None, sides=4))
    m.add("body_", ell("body", "windp", (0, 8, 0.6), (3.0, 3.4, 4.4), segs=10, rings=3))
    m.add("neck", tub("body", "windp", [P(0, 10, -1.6), P(0.6, 14, -2.4), P(0, 18, -3.4), P(0, 20, -5)], [1.9, 1.5, 1.2, 1.0], None, sides=8))
    m.add("head", ell("body", "windp", (0, 20.5, -5.5), (1.8, 1.6, 2.4), segs=8, rings=3))
    m.add("crest", cone("body", "windp", (0, 21.8, -4.6), (0, 0.6, 1), 3.4, 0.6, sides=4))
    m.add("beak", cone("body", "gold", (0, 20.3, -7.6), (0, -0.1, -1), 4, 0.8, sides=4, rings=1))
    for s in (1, -1):
        m.add(f"eye{s}", ell("body", "sigil", (s * 1.4, 21, -6.2), (0.5, 0.5, 0.5), segs=4, rings=1))
        for k in range(4):
            m.add(f"wing{s}{k}", ell("body", "wind" if k % 2 else "windp", (s * (4.6 + k * 2.2), 10.4 + k * 1.4, 1.0 + k * 0.6), (3.4 - k * 0.3, 0.8, 2.2 - k * 0.2), segs=8,
                                     rings=1, rz=s * (24 + k * 6)))
    for k in range(3):
        m.add(f"tail{k}", ell("body", "windp", ((k - 1) * 1.2, 6.4, 5.6), (0.8, 0.4, 3.0), segs=6, rings=1, ry=(k - 1) * 18))

def my_eye_arrow(m):
    # 凝視の矢 (刺さる): 眼の紋を持つ金の矢が地に刺さる
    m.add("shaft", tub("body", "goldl", [P(0, -6, 0), P(0, 22, 0)], [0.9, 0.8], None, sides=6))
    for s in (1, -1):
        m.add(f"fl{s}", ell("body", "feath", (s * 1.4, 19, 0), (1.4, 3.2, 0.4), segs=4, rings=1))
    m.add("ring", tub("body", "gold", [P(math.cos(k / 16 * math.tau) * 5.2, 9 + math.sin(k / 16 * math.tau) * 5.2, 0) for k in range(17)], [0.5] * 17, None, hint=(0, 0, 1), sides=4, caps=(False, False)))
    m.add("eye", ell("body", "pearl", (0, 9, 0), (4.4, 3.0, 1.2), segs=10, rings=2))
    m.add("iris", ell("body", "sigil", (0, 9, -1.1), (1.8, 1.8, 0.4), segs=8, rings=1))
    sig_loop(m, 7)


def my_rewind(m):
    # 巻き戻し (足もと): 床の時計盤と、逆へ回る二本の針
    m.add("dial", ell("body", "night", (0, 0.6, 0), (12, 1.0, 12), segs=24, rings=1))
    for k in range(12):
        a = k / 12 * math.tau
        m.add(f"tick{k}", ell("body", "goldl", (math.cos(a) * 10.5, 1.8, math.sin(a) * 10.5), (0.7 if k % 3 else 1.3, 0.5, 0.7 if k % 3 else 1.3), segs=4, rings=1))
    m.add("long", tub("body", "goldl", [P(0, 2.4, 0), P(0, 2.4, -9)], [0.8, 0.3], None, hint=(0, 1, 0), sides=4))
    m.add("short", tub("body", "goldl", [P(0, 2.6, 0), P(5.5, 2.6, 2)], [0.9, 0.4], None, hint=(0, 1, 0), sides=4))
    m.add("hub", ell("body", "sigil", (0, 2.8, 0), (1.4, 0.8, 1.4), segs=6, rings=1))
    for k in range(3):
        a = k / 3 * math.tau + 0.4
        m.add(f"sand{k}", ell("body", "lantern", (math.cos(a) * 6, 6 + k * 3, math.sin(a) * 6), (0.7, 0.7, 0.7), segs=4, rings=1))


def my_moth(m):
    # 灯の粉 (弾ける): 翅を開いた灯蛾。翅の脈と縁、目玉の紋、毛の胸、こぼれる金の粉 (2026-11-12 作り込み)
    m.add("thorax", ell("body", "moth", (0, 12, -1.6), (1.8, 1.7, 2.0), segs=8, rings=3))
    m.add("abdomen", tub("body", "moth", [P(0, 12, 0), P(0, 11.6, 3.0), P(0, 11.2, 5.6)], [1.5, 1.3, 0.5], None, sides=8))
    m.add("head", ell("body", "moth", (0, 12.4, -3.8), (1.2, 1.1, 1.0), segs=8, rings=2))
    for s in (1, -1):
        m.add(f"fw{s}", ell("body", "moth", (s * 5.8, 13, -1.4), (5.4, 0.45, 3.8), segs=12, rings=2, ry=s * 20))
        m.add(f"hw{s}", ell("body", "moth", (s * 4.4, 12, 3.0), (3.8, 0.45, 2.8), segs=10, rings=2, ry=-s * 20))
        m.add(f"edge{s}", tub("body", "goldl", [P(s * 1.6, 13.4, -3.0), P(s * 6.0, 13.5, -5.0), P(s * 10.4, 13.4, -3.0), P(s * 10.8, 13.3, -0.6)], [0.22] * 4, None,
                               sides=4))
        for k in range(3):
            m.add(f"vein{s}{k}", tub("body", "goldl", [P(s * 1.6, 13.45, -1.2), P(s * (4.0 + k * 2.0), 13.5, -2.6 + k * 1.2)], [0.1, 0.08], None, sides=3))
        m.add(f"eye{s}", ell("body", "lantern", (s * 6.5, 13.6, -1.6), (1.6, 0.4, 1.6), segs=10, rings=1))
        m.add(f"eyec{s}", ell("body", "night", (s * 6.5, 13.85, -1.6), (0.6, 0.2, 0.6), segs=8, rings=1))
        m.add(f"ant{s}", tub("body", "moth", [P(s * 0.5, 12.8, -4.4), P(s * 1.6, 14.2, -6.0), P(s * 2.6, 14.6, -7.2)], [0.25, 0.2, 0.12], None, sides=4))
        for k in range(4):
            m.add(f"barb{s}{k}", cone("body", "moth", (s * (0.9 + k * 0.5), 13.9 + k * 0.2, -5.0 - k * 0.6), (s, 0.3, 0), 0.8, 0.08, sides=3))
    for k in range(9):
        a = k * 0.85
        m.add(f"dust{k}", ell("body", "lantern", (math.cos(a) * (3 + k * 0.3), 2 + k * 1.0, math.sin(a) * (3 + k * 0.3)), (0.5, 0.5, 0.5), segs=5, rings=1))
    m.add("dot", ell("body", "sigil", (0, 0.6, 0), (1.4, 0.5, 1.4), segs=6, rings=1))

def my_chase_mark(m):
    # 追う印 (印): 赤い菱の印が浮き、下に爪の輪
    m.add("mark", ell("body", "blood", (0, 18, 0), (3.6, 5.6, 1.2), segs=4, rings=1))
    m.add("inner", ell("body", "sigil", (0, 18, 0.9), (1.4, 2.4, 0.6), segs=4, rings=1))
    for k in range(5):
        a = k / 5 * math.tau
        x, z = math.cos(a) * 7, math.sin(a) * 7
        m.add(f"claw{k}", tub("body", "blood", [P(x, -4, z), P(x * 0.9, 4, z * 0.9), P(x * 0.55, 8, z * 0.55)], [1.4, 0.9, 0.2], None, sides=4))
    loop(m, "ring", "blood", 8, 0.6, 0.4)


def my_promise(m):
    # 約束 (足もと): 二つの輪を結ぶ光の糸と小さな鈴
    for s in (1, -1):
        m.add(f"ring{s}", tub("body", "goldl", [P(s * 4 + math.cos(k / 16 * math.tau) * 4.6 * math.cos(math.radians(25)), 12 + math.sin(k / 16 * math.tau) * 4.6,
                                                   -s * math.cos(k / 16 * math.tau) * 4.6 * math.sin(math.radians(25))) for k in range(17)], [0.5] * 17, None,
                                hint=(0, 0, 1), sides=4, caps=(False, False)))
    m.add("thread", tub("body", "light", [P(-8, 6, 0), P(-3, 9, 1), P(3, 9, -1), P(8, 6, 0)], [0.4, 0.4, 0.4, 0.4], None, sides=4))
    m.add("bell", tub("body", "goldl", [P(0, 10, 0), P(0, 7, 0), P(0, 5, 0)], [0.6, 1.8, 2.4], None, sides=10, caps=(True, False)))
    m.add("clap", ell("body", "sigil", (0, 4.6, 0), (0.8, 0.8, 0.8), segs=6, rings=1))
    m.add("post", tub("body", "goldl", [P(0, -6, 0), P(0, 7.6, 0)], [0.8, 0.5], None, sides=6))
    sig_loop(m, 9)


def my_silence(m):
    # 沈黙の鐘 (足もと): 布で包まれた鐘と、止まった舌
    m.add("bell", tub("body", "bronze", [P(0, 26, 0), P(0, 18, 0), P(0, 10, 0)], [2.4, 6.0, 8.6], None, sides=12, caps=(True, False)))
    m.add("cloth", tub("body", "night", [P(0, 23, 0), P(0, 16, 0)], [5.2, 7.6], None, sides=12, caps=(False, False)))
    m.add("knot", ell("body", "night", (0, 23.6, 0), (2.4, 1.6, 2.4), segs=8, rings=2))
    m.add("tongue", tub("body", "iron", [P(0, 18, 0), P(0, 9, 0)], [0.7, 0.7], None, sides=6))
    m.add("ball", ell("body", "sigil", (0, 8.6, 0), (1.4, 1.4, 1.4), segs=6, rings=2))
    m.add("frame", tub("body", "totem", [P(-10, -6, 0), P(-10, 30, 0), P(10, 30, 0), P(10, -6, 0)], [1.4, 1.2, 1.2, 1.4], None, sides=6))
    sig_loop(m, 11)


def my_clap(m):
    # 合掌 (足もと): 合わせた光の両手と、後ろの光輪
    for s in (1, -1):
        m.add(f"palm{s}", ell("body", "light", (s * 1.4, 14, 0), (1.4, 7.0, 3.6), segs=8, rings=2, rz=-s * 6))
        for k in range(4):
            m.add(f"f{s}{k}", tub("body", "light", [P(s * 1.2, 19, -2.4 + k * 1.6), P(s * 1.0, 25 - abs(k - 1.5) * 1.4, -2.4 + k * 1.6)], [0.7, 0.55], None, sides=5))
    m.add("halo", tub("body", "goldl", [P(math.cos(k / 20 * math.tau) * 10, 18 + math.sin(k / 20 * math.tau) * 10, 4.5) for k in range(21)], [0.6] * 21, None, hint=(0, 0, 1), sides=4, caps=(False, False)))
    m.add("wrist", tub("body", "goldl", [P(0, -6, 0), P(0, 8, 0)], [2.4, 2.0], None, sides=8))
    sig_loop(m, 9)


BUILDERS = {}
for el in ("stone", "wind", "tide", "ash", "bone"):
    BUILDERS[f"sk_el_{el}"] = globals()[f"el_{el}"]
for ch in ("rune", "rewrite", "mutation", "secret"):
    BUILDERS[f"sk_ch_{ch}"] = globals()[f"ch_{ch}"]
BUILDERS["sk_mastery_3"] = mastery_3
BUILDERS["sk_mastery_5"] = mastery_5
MYTHIC = ("sky_hand", "warden", "balance", "big_bell", "melt", "pull", "life_lamp", "gaze", "new_moon", "feather", "mist", "eye_arrow", "rewind", "moth",
          "chase_mark", "promise", "silence", "clap")
for k in MYTHIC:
    BUILDERS[f"my_{k}"] = globals()[f"my_{k}"]
FLYING = {"my_feather"}


def build(kind):
    m = Mount("prop_" + kind, kind)
    m.bone("body", "root", (0, CY, 0) if kind in FLYING else (0, 0, 0))
    BUILDERS[kind](m)
    m.pal = dict(mats=kmats(), glow_mats=sorted(KGLOW))
    m.detail = None
    m.hint = 256
    return m


def idle(kind, m):
    g = Gait("idle", 1.6)
    if kind in ("sk_ch_rune", "sk_mastery_5", "my_pull", "my_gaze"):
        g.rot("body", None, W(0).s(25, 1, 0, "t"), None)
    elif kind in FLYING:
        g.rot("body", W(0).s(3, 1, 0, "t"))
    else:
        g.rot("body", W(0).s(0.6, 1, 0, "t"))
    return g


GROUPS = { "marks": [k for k in BUILDERS if k.startswith(("sk_el_", "sk_ch_", "sk_mastery"))],
          "mythic": [f"my_{k}" for k in MYTHIC]}


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
        cols = 6
        rows = (len(keys) + cols - 1) // cols
        cv = Image.new("RGB", (cols * 310 + 10, rows * 334 + 10), (16, 16, 22))
        d = ImageDraw.Draw(cv)
        for i, k in enumerate(keys):
            x, y = 10 + (i % cols) * 310, 10 + (i // cols) * 334
            d.text((x, y), f"{k}  {tiles[k][1]} tris", font=voxel.font(15), fill=(150, 232, 255))
            cv.paste(tiles[k][0], (x, y + 24))
        cv.save(REVIEW / f"kit_{gname}.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
