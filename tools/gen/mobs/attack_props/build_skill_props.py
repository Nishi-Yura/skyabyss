#!/usr/bin/env python3
"""自分の技の形 (combat/prop/AttackProp を人の技に使う。rpg/skill/SkillProps) の模型を作って入れる (2026-11-12 の声
「こちらのスキルや攻撃で敵のボスみたいに下からモデルが出るとか、モデルが飛んでいくみたいなのできたらめっちゃよくない？」)。

    python -X utf8 -I tools/gen/mobs/attack_props/build_skill_props.py [種類 ...] [--no-install]

職ごとに三つの案 (a / b / c。形が違う。色違いではない) × 役 (rise = 床から突き出る、shot = 飛ぶ)。名前は sk_<職>_<案>_<役>。
戦士は rise だけ。淵と前世の木は一つの案 (sk_deep_rise・sk_past_rise)。
人の技の印: どの形も青緑に光る印 (rise は根元の輪、shot は芯の光) を持つ。敵の技と見分けるため。
確かめの絵は docs/review/skill_props/<職>.png (案ごとに一列) と all.png。
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


# ================================================================ 戦士 (岩守り): rise
def warrior_a(m):
    # 花崗岩の石柱: 角ばった柱三本、青緑の刻み帯
    for k, (x, z, h, w, lx) in enumerate(((0, 0, 40, 5.0, 0), (6, 3, 26, 3.6, 0.25), (-5, 4, 22, 3.2, -0.25))):
        m.add(f"slab{k}", tub("body", "granite", [np.array([x, -6.0, z]), np.array([x + lx * h, h, z])], [w, w * 0.8], None, sides=4, phase=45, caps=(True, True)))
        m.add(f"band{k}", ell("body", "sigil", (x + lx * h * 0.55, h * 0.55, z), (w * 1.08, 0.7, w * 1.08), segs=4, rings=1, ry=45))
        m.add(f"cap{k}", cone("body", "granite", (x + lx * h, h, z), (lx, 1, 0), 6, w * 0.8, sides=4, rings=1))
    base_debris(m, "granite", 8, seed=31)
    ring_sigil(m, 10)


def warrior_b(m):
    # 鯨の肋骨のアーチ: 床から弧を描いて二本ずつ交わる骨
    for k in range(3):
        a = k / 3 * math.tau
        p0 = np.array([math.cos(a) * 8, -6.0, math.sin(a) * 8])
        top = np.array([math.cos(a) * 2, 36.0 - k * 4, math.sin(a) * 2])
        mid = (p0 + top) / 2 + np.array([math.cos(a) * 5, 4, math.sin(a) * 5])
        m.add(f"rib{k}", tub("body", "bone", [p0, mid, top], [3.0, 2.4, 1.2], None, sides=6))
        m.add(f"knob{k}", ell("body", "bone", mid, (3.2, 3.2, 3.2), segs=6, rings=2))
    m.add("vert", ell("body", "sigil", (0, 30, 0), (2.4, 2.4, 2.4), segs=6, rings=2))
    base_debris(m, "rock", 9, seed=32)
    ring_sigil(m, 10)


def warrior_c(m):
    # 幻の大剣: 地に突き立った大きな剣 (刃・鍔・柄)、刃に青緑の筋
    m.add("blade", tub("body", "steel", [np.array([0, -8.0, 0]), np.array([0, 30.0, 0])], [4.2, 3.6], None, sides=4, phase=0, caps=(True, True)))
    m.add("tip", cone("body", "steel", (0, -8, 0), (0, -1, 0), 4, 3.0, sides=4, rings=1))
    m.add("fuller", tub("body", "sigil", [np.array([0, -2.0, 0]), np.array([0, 28.0, 0])], [0.9, 0.9], None, sides=4))
    m.add("guard", ell("body", "granite", (0, 31, 0), (9.0, 1.8, 2.4), segs=6, rings=2))
    m.add("grip", tub("body", "totem", [np.array([0, 32.0, 0]), np.array([0, 42.0, 0])], [1.3, 1.3], None, sides=6))
    m.add("pommel", ell("body", "sigil", (0, 43.5, 0), (2.0, 2.0, 2.0), segs=6, rings=2))
    base_debris(m, "rock", 8, seed=33)
    ring_sigil(m, 9)


# ================================================================ 暗殺者 (風読み)
def assassin_a_rise(m):
    # 立つ風の大鎌: 床から立つ二枚の三日月の刃 (縦の面、刃先は上で外へ反る)
    for s_ in (1, -1):
        n = 13
        for k in range(n):
            t = k / (n - 1)
            a = -0.2 + t * 2.0
            p = np.array([s_ * (10 - math.cos(a) * 10 + 2), -4 + math.sin(a) * 26 + t * 8, s_ * 1.5])
            tang = math.degrees(math.atan2(math.cos(a) * 26 + 8, s_ * math.sin(a) * 10))
            w = 3.4 * math.sin(t * math.pi) ** 0.8 + 0.4
            m.add(f"edge{s_}{k}", ell("body", "wind", p, (2.8, w, 0.8), segs=6, rings=2, rz=tang))
        m.add(f"spark{s_}", ell("body", "sigil", np.array([s_ * 3.5, 14, s_ * 1.5]), (1.2, 1.2, 1.2), segs=6, rings=2))
    ring_sigil(m, 9)


def assassin_a_shot(m):
    # 交わる二枚の風の刃 (X)。前 (-Z) へ回りながら飛ぶ
    for s_ in (1, -1):
        n = 9
        for k in range(n):
            t = k / (n - 1) * 2 - 1
            a = t * 1.1
            p = np.array([math.sin(a) * 9 * math.cos(0.6) , 4.0 + math.sin(a) * 9 * math.sin(0.6) * s_, -math.cos(a) * 4.5])
            w = 3.0 * (1 - abs(t) ** 1.6) + 0.5
            m.add(f"x{s_}{k}", ell("body", "wind", p, (w * 0.9, 1.2, w * 0.9), segs=6, rings=2, rz=s_ * 34))
    core_sigil(m, (0, 4.0, -4.6), 1.6)


def assassin_b_rise(m):
    # 影の短刀の束: 黒い苦無が三本、斜めに刺さる
    for k, (x, z, lx, lz) in enumerate(((0, 0, 0.1, 0.0), (4, 2, -0.35, 0.1), (-3, 3, 0.3, -0.2))):
        d = nrm(np.array([lx, 1, lz]))
        base = np.array([x, -4.0, z])
        m.add(f"kblade{k}", cone("body", "obsid", base + d * 18, -d, 22, 2.6, sides=4, rings=1))
        m.add(f"kgrip{k}", tub("body", "inkred", [base + d * 18, base + d * 26], [1.0, 1.0], None, sides=4))
        m.add(f"kring{k}", ell("body", "sigil", base + d * 27.5, (1.8, 1.8, 0.6), segs=8, rings=1))
    base_debris(m, "obsid", 6, seed=34)
    ring_sigil(m, 8)


def assassin_b_shot(m):
    # 投げる苦無三本 (前 -Z)
    for k, (x, y) in enumerate(((0, 4), (3, 5.5), (-3, 3))):
        m.add(f"kb{k}", cone("body", "obsid", (x, y, -2), (0, 0, -1), 12, 1.8, sides=4, rings=1))
        m.add(f"kg{k}", tub("body", "inkred", [np.array([x, y, -2.0]), np.array([x, y, 4.0])], [0.7, 0.7], None, sides=4))
        m.add(f"kr{k}", ell("body", "sigil", (x, y, 5.0), (1.3, 1.3, 0.5), segs=8, rings=1))


def assassin_c_rise(m):
    # 札の渦: 床から昇る紙の札の束が二重に渦を巻く
    for k in range(18):
        a = k * 0.7
        y = 1 + k * 2.3
        r = 6.0 - k * 0.18
        for s_ in (0, math.pi):
            m.add(f"tag{k}{int(s_)}", ell("body", "paper", (math.cos(a + s_) * r, y, math.sin(a + s_) * r), (2.4, 3.6, 0.35), segs=4, rings=1,
                                         ry=-math.degrees(a + s_)))
            m.add(f"glyph{k}{int(s_)}", ell("body", "inkred", (math.cos(a + s_) * (r + 0.4), y, math.sin(a + s_) * (r + 0.4)), (0.9, 1.8, 0.15), segs=4,
                                           rings=1, ry=-math.degrees(a + s_)))
    m.add("axis", tub("body", "sigil", [np.array([0, -2.0, 0]), np.array([0, 44.0, 0])], [0.8, 0.4], None, sides=5))
    ring_sigil(m, 9)


def assassin_c_shot(m):
    # 飛ぶ札: 一枚の札が前へ、後ろに二枚
    for k, (x, y, z) in enumerate(((0, 5, -4), (2.6, 6, 2), (-2.4, 4, 3))):
        m.add(f"t{k}", ell("body", "paper", (x, y, z), (3.0, 0.3, 4.6), segs=4, rings=1))
        m.add(f"g{k}", ell("body", "inkred", (x, y + 0.3, z), (1.2, 0.15, 2.4), segs=4, rings=1))
    core_sigil(m, (0, 5.6, -4), 1.2)


# ================================================================ 呪術師 (潮歌い)
def shaman_a_rise(m):
    # 鯨の霊の柱: 彫った木の柱に水の帯が巻き、上に鯨の頭
    m.add("pole", tub("body", "totem", [np.array([0, -6.0, 0]), np.array([0, 34.0, 0])], [4.0, 3.4], None, sides=8))
    for k in range(3):
        m.add(f"face{k}", ell("body", "totem", (0, 8 + k * 9, -3.2), (3.0, 2.6, 1.2), segs=6, rings=2))
        m.add(f"eye{k}", ell("body", "sigil", (0, 9 + k * 9, -4.2), (1.6, 0.6, 0.4), segs=6, rings=1))
    m.add("whale", ell("body", "seaw", (0, 39, -1), (6.0, 5.0, 10.0), segs=10, rings=3))
    m.add("belly", ell("body", "foam", (0, 36.6, -2), (4.6, 2.4, 8.0), segs=8, rings=2))
    for s_ in (1, -1):
        m.add(f"fluke{s_}", ell("body", "seaw", (s_ * 3.2, 41, 9.5), (4.0, 0.9, 2.2), segs=6, rings=1, ry=s_ * 30))
        m.add(f"weye{s_}", ell("body", "sigil", (s_ * 5.2, 40, -6.5), (0.8, 0.8, 0.8), segs=6, rings=1))
    for k in range(6):
        a = k * 1.1
        m.add(f"swirl{k}", ell("body", "water", (math.cos(a) * 4.6, 4 + k * 5, math.sin(a) * 4.6), (2.2, 1.0, 2.2), segs=6, rings=1))
    ring_sigil(m, 9)


def shaman_a_shot(m):
    # 水の霊の魚: 前 (-Z) へ泳ぐ小さな鯨
    m.add("body_", ell("body", "seaw", (0, 5, 0), (3.4, 3.0, 6.4), segs=10, rings=3), core=True)
    m.add("tail", cone("body", "seaw", (0, 5, 5), (0, 0.2, 1), 6, 2.4, sides=5, rings=1))
    for s in (1, -1):
        m.add(f"fin{s}", ell("body", "water", (s * 3.6, 4, -0.5), (2.6, 0.6, 1.6), segs=6, rings=1, rz=s * 20))
    core_sigil(m, (0, 5.4, -4.6), 1.1)


def shaman_b_rise(m):
    # 珊瑚の渦: ねじれて昇る珊瑚の枝
    for k in range(3):
        a0 = k / 3 * math.tau
        pts = [np.array([math.cos(a0 + t * 2.2) * (5 - t * 3), -6 + t * 40, math.sin(a0 + t * 2.2) * (5 - t * 3)]) for t in np.linspace(0, 1, 6)]
        m.add(f"branch{k}", tub("body", "coral", pts, list(np.linspace(2.6, 0.8, 6)), None, sides=6))
        m.add(f"bud{k}", ell("body", "sigil", pts[-1], (1.4, 1.4, 1.4), segs=6, rings=2))
    for k in range(5):
        a = k * 1.3
        m.add(f"polyp{k}", ell("body", "shell", (math.cos(a) * 3, 6 + k * 6, math.sin(a) * 3), (1.6, 1.0, 1.6), segs=6, rings=1))
    base_debris(m, "rock", 6, seed=35)
    ring_sigil(m, 9)


def shaman_b_shot(m):
    # 珊瑚の珠: 輪をまとった珠
    m.add("orb", ell("body", "coral", (0, 5, 0), (4.0, 4.0, 4.0), segs=10, rings=4), core=True)
    for k in range(5):
        a = k / 5 * math.tau
        m.add(f"spur{k}", cone("body", "shell", (math.cos(a) * 3.4, 5 + math.sin(a) * 3.4, 0), (math.cos(a), math.sin(a), 0.3), 4, 1.0, sides=4, rings=1))
    m.add("halo", ell("body", "sigil", (0, 5, 0), (5.6, 0.4, 5.6), segs=12, rings=1))


def shaman_c_rise(m):
    # 灯の霊の柱: 細い杭の上に吊った灯籠、灯に青緑の炎
    m.add("post", tub("body", "totem", [np.array([0, -6.0, 0]), np.array([0, 30.0, 0])], [1.6, 1.3], None, sides=6))
    m.add("arm", tub("body", "totem", [np.array([0, 29.0, 0]), np.array([0, 31.0, -7])], [0.9, 0.9], None, sides=5))
    m.add("lamp", tub("body", "lantern", [np.array([0, 21.0, -7]), np.array([0, 28.0, -7])], [3.4, 3.0], None, sides=6, caps=(True, True)))
    m.add("roof", cone("body", "totem", (0, 28, -7), (0, 1, 0), 4, 4.4, sides=6, rings=1))
    m.add("flame", ell("body", "sigil", (0, 24.5, -7), (1.6, 2.4, 1.6), segs=6, rings=2))
    for k in range(4):
        a = k / 4 * math.tau
        m.add(f"tassel{k}", cone("body", "inkred", (math.cos(a) * 2.6, 21, -7 + math.sin(a) * 2.6), (0, -1, 0), 5, 0.6, sides=4, rings=1))
    ring_sigil(m, 7)


def shaman_c_shot(m):
    # 浮かぶ灯籠
    m.add("lamp", tub("body", "lantern", [np.array([0, 2.0, 0]), np.array([0, 8.0, 0])], [3.0, 2.6], None, sides=6, caps=(True, True)), core=True)
    m.add("roof", cone("body", "totem", (0, 8, 0), (0, 1, 0), 3.4, 3.8, sides=6, rings=1))
    m.add("flame", ell("body", "sigil", (0, 5, 0), (1.4, 2.0, 1.4), segs=6, rings=2))
    m.add("trail", cone("body", "lantern", (0, 5, 3), (0, 0, 1), 7, 1.6, sides=5, rings=1))


# ================================================================ 鍛冶師 (灰の鍛え人)
def smith_a_rise(m):
    # 鍛えた鉄の棘: 赤熱した鉄の棘の束と、根元の金床の欠片
    for k, (x, z, h, r, lx, lz) in enumerate(((0, 0, 38, 4.0, 0, 0), (5, 2, 24, 2.8, 0.35, 0.1), (-4, 3, 22, 2.6, -0.35, 0.2), (1, -5, 18, 2.4, 0.1, -0.4))):
        m.add(f"s{k}", cone("body", "steel", (x, -6, z), (lx, 1, lz), h + 6, r, sides=4, rings=2))
        m.add(f"hot{k}", cone("body", "ironhot", (x + lx * (h * 0.7), h * 0.7, z + lz * (h * 0.7)), (lx, 1, lz), h * 0.3 + 2, r * 0.4, sides=4, rings=1))
    m.add("anvil", ell("body", "obsid", (0, 1, 0), (7, 2.0, 4), segs=6, rings=2))
    ring_sigil(m, 10)


def smith_a_shot(m):
    # 熔けた鉄の塊: 赤熱の芯と、冷えた殻のひび
    m.add("core", ell("body", "ironhot", (0, 5, 0), (4.6, 4.2, 4.8), segs=10, rings=3), core=True)
    rng = np.random.default_rng(36)
    for k in range(6):
        d = nrm(rng.normal(size=3))
        m.add(f"crust{k}", ell("body", "obsid", np.array([0, 5, 0]) + d * 3.8, (2.4, 1.8, 2.4), segs=6, rings=2))
    m.add("drip", cone("body", "ironhot", (0, 5, 3.6), (0, -0.2, 1), 6, 2.2, sides=5, rings=1))
    core_sigil(m, (0, 5, -4.2), 1.0)


def smith_b_rise(m):
    # 大鎚の頭: 地から鎚頭が突き上がる (柄は折れて短い)
    m.add("head", tub("body", "steel", [np.array([-9.0, 22.0, 0]), np.array([9.0, 22.0, 0])], [5.6, 5.6], None, sides=8, hint=(0, 1, 0), caps=(True, True)))
    for s in (-1, 1):
        m.add(f"face{s}", ell("body", "ironhot", (s * 9.4, 22, 0), (0.8, 4.6, 4.6), segs=8, rings=1))
    m.add("haft", tub("body", "totem", [np.array([0, -6.0, 0]), np.array([0, 17.0, 0])], [1.6, 1.6], None, sides=6))
    m.add("band", ell("body", "sigil", (0, 22, 0), (2.0, 6.0, 6.0), segs=8, rings=1))
    base_debris(m, "basalt", 9, seed=37)
    ring_sigil(m, 10)


def smith_b_shot(m):
    # 回る歯車
    m.add("wheel", ell("body", "bronze", (0, 6, 0), (6.0, 1.4, 6.0), segs=18, rings=2, rx=90), core=True)
    for k in range(10):
        a = k / 10 * math.tau
        m.add(f"tooth{k}", ell("body", "bronze", (math.cos(a) * 6.3, 6 + math.sin(a) * 6.3, 0), (1.3, 1.3, 1.4), segs=4, rings=1, rz=math.degrees(a)))
    m.add("hub", ell("body", "sigil", (0, 6, 0), (2.0, 1.8, 2.0), segs=8, rings=2, rx=90))


def smith_c_rise(m):
    # 半ば埋まった歯車: 大きな歯車が床から縦に立ち上がる
    m.add("wheel", ell("body", "bronze", (0, 12, 0), (14.0, 2.0, 14.0), segs=20, rings=2, rx=90))
    for k in range(14):
        a = k / 14 * math.tau
        m.add(f"tooth{k}", ell("body", "bronze", (math.cos(a) * 14.3, 12 + math.sin(a) * 14.3, 0), (2.0, 2.0, 2.2), segs=4, rings=1, rz=math.degrees(a)))
    for k in range(4):
        a = k / 4 * math.tau
        m.add(f"spoke{k}", tub("body", "steel", [np.array([0, 12.0, 0]), np.array([math.cos(a) * 11, 12 + math.sin(a) * 11, 0])], [1.2, 1.2], None, sides=4))
    m.add("hub", ell("body", "ironhot", (0, 12, 0), (3.4, 3.4, 3.0), segs=8, rings=2))
    base_debris(m, "basalt", 8, seed=38)
    ring_sigil(m, 10)


def smith_c_shot(m):
    # 熱い鋲: 太い釘が前 (-Z) へ
    m.add("shank", tub("body", "steel", [np.array([0, 5.0, -8]), np.array([0, 5.0, 6])], [1.6, 1.6], None, sides=6))
    m.add("point", cone("body", "steel", (0, 5, -8), (0, 0, -1), 4, 1.6, sides=6, rings=1))
    m.add("head", ell("body", "ironhot", (0, 5, 6.5), (3.4, 3.4, 1.2), segs=8, rings=2), core=True)
    m.add("glow", tub("body", "ironhot", [np.array([0, 5.0, -4]), np.array([0, 5.0, 2])], [1.8, 1.8], None, sides=6))
    core_sigil(m, (0, 5, 7.6), 0.9)


# ================================================================ 弓使い (骨の射手)
def archer_a_rise(m):
    # 大きな骨の矢が床に刺さる (矢羽は青緑)
    d = nrm(np.array([0.2, 1, 0.1]))
    m.add("shaft", tub("body", "bone", [np.array([0, -8.0, 0]), d * 40], [1.6, 1.4], None, sides=6))
    for k in range(3):
        a = k / 3 * math.tau
        side = np.array([math.cos(a), 0, math.sin(a)])
        m.add(f"fl{k}", ell("body", "sigil", d * 34 + side * 2.4, (0.5, 5.0, 2.4), segs=4, rings=1, ry=-math.degrees(a)))
    base_debris(m, "rock", 8, seed=39)
    ring_sigil(m, 8)


def archer_a_shot(m):
    # 大きな骨の矢 (前 -Z)
    m.add("shaft", tub("body", "bone", [np.array([0, 4.0, -10]), np.array([0, 4.0, 14])], [1.1, 1.1], None, sides=6))
    m.add("head", cone("body", "bone", (0, 4, -10), (0, 0, -1), 6, 2.4, sides=4, rings=1))
    for k in range(3):
        a = k / 3 * math.tau + 0.5
        m.add(f"fl{k}", ell("body", "sigil", (math.cos(a) * 1.8, 4 + math.sin(a) * 1.8, 11), (0.4, 2.0, 3.4), segs=4, rings=1, rz=math.degrees(a)))


def archer_b_rise(m):
    # 茨: 床から絡んで伸びる棘の蔓
    rng = np.random.default_rng(40)
    for k in range(4):
        a0 = k / 4 * math.tau
        pts = [np.array([math.cos(a0 + t * 3) * (3 + t * 3), -6 + t * 34, math.sin(a0 + t * 3) * (3 + t * 3)]) for t in np.linspace(0, 1, 7)]
        m.add(f"vine{k}", tub("body", "thorn", pts, list(np.linspace(2.0, 0.5, 7)), None, sides=5))
        for j in range(1, 6):
            d = nrm(rng.normal(size=3))
            m.add(f"thorn{k}{j}", cone("body", "bone", pts[j], d, 3.4, 0.7, sides=4, rings=1))
    m.add("bloom", ell("body", "sigil", (0, 30, 0), (2.4, 1.6, 2.4), segs=8, rings=2))
    ring_sigil(m, 8)


def archer_b_shot(m):
    # 茨の矢: 蔓が巻いた矢
    m.add("shaft", tub("body", "thorn", [np.array([0, 4.0, -9]), np.array([0, 4.0, 12])], [0.9, 0.9], None, sides=5))
    m.add("head", cone("body", "bone", (0, 4, -9), (0, 0, -1), 5, 2.0, sides=4, rings=1))
    for k in range(5):
        z = -6 + k * 4
        m.add(f"curl{k}", ell("body", "thorn", (0, 4, z), (1.8, 1.8, 0.6), segs=8, rings=1))
        m.add(f"spike{k}", cone("body", "bone", (1.6 * (-1) ** k, 4, z), ((-1) ** k, 0.3, 0), 2.4, 0.5, sides=4, rings=1))
    core_sigil(m, (0, 4, -9.5), 0.9)


def archer_c_rise(m):
    # 羽根の柱: 大きな鷹の羽根が三枚、扇に立つ
    for k, ang in enumerate((-30, 0, 30)):
        d = nrm(np.array([math.sin(math.radians(ang)) * 0.5, 1, 0]))
        tip = np.array([0, -4.0, 0])
        m.add(f"quill{k}", tub("body", "goldl", [tip, tip + d * 34], [0.7, 0.4], None, sides=5))
        side = nrm(np.cross(d, [0.0, 0.0, 1.0]))
        for j in range(7):
            t = 0.25 + j * 0.1
            w = 4.4 * math.sin(min(1.0, t * 1.2) * math.pi) + 0.6
            c = tip + d * (34 * t)
            for s in (1, -1):
                m.add(f"v{k}{j}{s}", ell("body", "hawk", c + side * s * w * 0.5, (w * 0.55, 2.2, 0.5), segs=6, rings=1, rz=-s * 28 + ang))
    ring_sigil(m, 8)


def archer_c_shot(m):
    # 幻の鷹: 翼を広げて前 (-Z) へ
    m.add("body_", ell("body", "hawk", (0, 5, 0), (2.4, 2.2, 5.0), segs=8, rings=3), core=True)
    m.add("head", ell("body", "hawk", (0, 6, -5), (1.8, 1.8, 2.0), segs=8, rings=2))
    m.add("beak", cone("body", "goldl", (0, 5.6, -7), (0, -0.4, -1), 2.4, 0.8, sides=4, rings=1))
    for s in (1, -1):
        m.add(f"wing{s}", ell("body", "hawk", (s * 7, 6, 0), (6.4, 0.6, 3.2), segs=8, rings=1, rz=-s * 12))
        m.add(f"tip{s}", ell("body", "sigil", (s * 12.6, 7, 0.6), (1.4, 0.4, 2.0), segs=6, rings=1))
    m.add("tail", ell("body", "hawk", (0, 5, 5.6), (2.4, 0.5, 2.6), segs=6, rings=1))


# ================================================================ 魔法使い (星読み)
def mage_a_rise(m):
    # 星の晶の尖塔: 星色の晶が束になって立つ
    for k, (x, z, h, r, lx, lz) in enumerate(((0, 0, 44, 4.2, 0, 0), (5, 2, 28, 3.0, 0.35, 0.1), (-4, 3, 24, 2.6, -0.35, 0.2), (1, -5, 20, 2.4, 0.1, -0.4))):
        m.add(f"c{k}", cone("body", "star", (x, -6, z), (lx, 1, lz), h + 6, r, sides=6, rings=2))
    m.add("star", ell("body", "sigil", (0, 46, 0), (2.6, 2.6, 2.6), segs=6, rings=2))
    base_debris(m, "rune", 7, seed=41)
    ring_sigil(m, 10)


def mage_a_shot(m):
    # 彗星: 星の芯と長い尾
    m.add("core", ell("body", "star", (0, 5, 0), (3.6, 3.6, 3.6), segs=10, rings=3), core=True)
    for k, (dx, dy) in enumerate(((0, 0), (2.0, 1.4), (-2.0, -1.0))):
        m.add(f"tail{k}", cone("body", "rune", (dx * 0.5, 5 + dy * 0.5, 2.6), (dx * 0.04, dy * 0.04, 1), 18 - k * 4, 2.8 - k * 0.6, sides=6, rings=2))
    for k in range(5):
        a = k / 5 * math.tau
        m.add(f"ray{k}", cone("body", "star", (math.cos(a) * 3, 5 + math.sin(a) * 3, 0), (math.cos(a), math.sin(a), 0), 3, 0.8, sides=4, rings=1))
    core_sigil(m, (0, 5, -3.0), 1.0)


def mage_b_rise(m):
    # 刻印の石碑: 床から迫り上がる黒い石碑、浮く刻印の輪
    m.add("slab", tub("body", "obsid", [np.array([0, -6.0, 0]), np.array([0, 34.0, 0])], [5.0, 4.0], None, sides=4, phase=45, caps=(True, True)))
    for k in range(4):
        m.add(f"glyph{k}", ell("body", "rune", (0, 4 + k * 8, -3.4), (2.2, 2.2, 0.4), segs=6, rings=1))
    for k, y in enumerate((14, 26)):
        m.add(f"halo{k}", ell("body", "sigil", (0, y, 0), (9.0 - k * 2, 0.5, 9.0 - k * 2), segs=16, rings=1, rz=12 * (-1) ** k))
    m.add("cap", cone("body", "obsid", (0, 34, 0), (0, 1, 0), 6, 4.0, sides=4, rings=1, hint=(1, 0, 0)))
    base_debris(m, "obsid", 8, seed=42)
    ring_sigil(m, 10)


def mage_b_shot(m):
    # 刻印の珠: 二重の輪をまとう珠
    m.add("orb", ell("body", "rune", (0, 5, 0), (3.6, 3.6, 3.6), segs=10, rings=4), core=True)
    m.add("ring1", ell("body", "sigil", (0, 5, 0), (6.0, 0.4, 6.0), segs=14, rings=1, rz=30))
    m.add("ring2", ell("body", "star", (0, 5, 0), (5.2, 0.4, 5.2), segs=14, rings=1, rx=60))


def mage_c_rise(m):
    # 星座の槍: 細い光の槍が天から刺さり、星の節が連なる
    m.add("lance", tub("body", "star", [np.array([0, -6.0, 0]), np.array([0, 60.0, 0])], [1.6, 0.6], None, sides=6))
    rng = np.random.default_rng(43)
    prev = None
    for k in range(6):
        p = np.array([rng.uniform(-6, 6), 8 + k * 9, rng.uniform(-6, 6)])
        m.add(f"node{k}", ell("body", "sigil", p, (1.4, 1.4, 1.4), segs=6, rings=2))
        if prev is not None:
            m.add(f"link{k}", tub("body", "star", [prev, p], [0.35, 0.35], None, sides=4))
        prev = p
    m.add("burst", ell("body", "star", (0, 0.8, 0), (6, 0.6, 6), segs=12, rings=1))
    ring_sigil(m, 9)


def mage_c_shot(m):
    # 星の投げ槍: 前 (-Z) へ光の槍、星の節
    m.add("lance", tub("body", "star", [np.array([0, 5.0, -12]), np.array([0, 5.0, 10])], [0.9, 0.5], None, sides=6))
    m.add("tip", cone("body", "star", (0, 5, -12), (0, 0, -1), 4, 1.6, sides=6, rings=1))
    for k in range(4):
        m.add(f"n{k}", ell("body", "sigil", (0, 5, -6 + k * 5), (1.2, 1.2, 1.2), segs=6, rings=2))


# ================================================================ 淵・前世 (一案)
def deep_rise(m):
    # 淵の牙: 床から紫の牙が輪になって噛み上がる
    for k in range(6):
        a = k / 6 * math.tau
        p = np.array([math.cos(a) * 6, -6.0, math.sin(a) * 6])
        d = nrm(np.array([-math.cos(a) * 0.45, 1, -math.sin(a) * 0.45]))
        m.add(f"fang{k}", cone("body", "obsid", p, d, 26, 2.6, sides=5, rings=3, bend=(-math.cos(a) * 3, 0, -math.sin(a) * 3)))
        m.add(f"vein{k}", cone("body", "deep", p + d * 6, d, 14, 0.9, sides=4, rings=1))
    m.add("maw", ell("body", "deep", (0, 0.8, 0), (6, 0.8, 6), segs=12, rings=1))
    ring_sigil(m, 9)


def past_rise(m):
    # 前世の金の鐘: 床に降りて響く金の鐘
    m.add("bell", tub("body", "goldl", [np.array(p) for p in ((0, 2.0, 0), (0, 10.0, 0), (0, 20.0, 0), (0, 26.0, 0))], [9.0, 6.6, 5.4, 3.0], None, sides=12,
                       caps=(False, True)))
    m.add("lip", ell("body", "goldl", (0, 2.0, 0), (9.6, 1.2, 9.6), segs=14, rings=1))
    m.add("crown", ell("body", "goldl", (0, 27.5, 0), (2.4, 2.4, 1.0), segs=8, rings=1))
    for k in range(3):
        m.add(f"band{k}", ell("body", "sigil", (0, 6 + k * 6, 0), (8.0 - k * 1.2, 0.6, 8.0 - k * 1.2), segs=14, rings=1))
    ring_sigil(m, 12)


BUILDERS = {
    "sk_warrior_a_rise": warrior_a, "sk_warrior_b_rise": warrior_b, "sk_warrior_c_rise": warrior_c,
    "sk_assassin_a_rise": assassin_a_rise, "sk_assassin_a_shot": assassin_a_shot, "sk_assassin_b_rise": assassin_b_rise, "sk_assassin_b_shot": assassin_b_shot,
    "sk_assassin_c_rise": assassin_c_rise, "sk_assassin_c_shot": assassin_c_shot,
    "sk_shaman_a_rise": shaman_a_rise, "sk_shaman_a_shot": shaman_a_shot, "sk_shaman_b_rise": shaman_b_rise, "sk_shaman_b_shot": shaman_b_shot,
    "sk_shaman_c_rise": shaman_c_rise, "sk_shaman_c_shot": shaman_c_shot,
    "sk_smith_a_rise": smith_a_rise, "sk_smith_a_shot": smith_a_shot, "sk_smith_b_rise": smith_b_rise, "sk_smith_b_shot": smith_b_shot,
    "sk_smith_c_rise": smith_c_rise, "sk_smith_c_shot": smith_c_shot,
    "sk_archer_a_rise": archer_a_rise, "sk_archer_a_shot": archer_a_shot, "sk_archer_b_rise": archer_b_rise, "sk_archer_b_shot": archer_b_shot,
    "sk_archer_c_rise": archer_c_rise, "sk_archer_c_shot": archer_c_shot,
    "sk_mage_a_rise": mage_a_rise, "sk_mage_a_shot": mage_a_shot, "sk_mage_b_rise": mage_b_rise, "sk_mage_b_shot": mage_b_shot,
    "sk_mage_c_rise": mage_c_rise, "sk_mage_c_shot": mage_c_shot,
    "sk_deep_rise": deep_rise, "sk_past_rise": past_rise,
}


def build(kind):
    m = Mount("prop_" + kind, kind)
    m.bone("body", "root", (0, 0, 0))
    BUILDERS[kind](m)
    m.pal = dict(mats=smats(), glow_mats=sorted(SGLOW))
    m.detail = None
    m.hint = 256
    return m


def idle(kind, m):
    g = Gait("idle", 1.6)
    if kind.endswith("_shot") and ("smith_b" in kind):
        g.rot("body", None, None, W(0).s(60, 1, 0, "t"))
    elif kind.endswith("_shot"):
        g.rot("body", W(0).s(4, 1, 0, "t"))
    else:
        g.rot("body", W(0).s(0.6, 1, 0, "t"))
    return g


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
        tiles[kind] = (view(m.F, atlas, glow, 210, 14, (300, 300), c, sc, ground=False), sum(len(f.verts) - 2 for f in m.F))
        print(kind, "tris", tiles[kind][1], flush=True)
    # 職ごとの絵: 行 = 案 (a/b/c)、列 = 役 (rise/shot)
    for job in ("warrior", "assassin", "shaman", "smith", "archer", "mage"):
        rows = [v for v in "abc"]
        cols = ["rise"] if job == "warrior" else ["rise", "shot"]
        if not any(f"sk_{job}_{v}_{r}" in tiles for v in rows for r in cols):
            continue
        cv = Image.new("RGB", (len(cols) * 310 + 10, len(rows) * 334 + 10), (16, 16, 22))
        d = ImageDraw.Draw(cv)
        for i, v in enumerate(rows):
            for j, r in enumerate(cols):
                k = f"sk_{job}_{v}_{r}"
                if k not in tiles:
                    continue
                x, y = 10 + j * 310, 10 + i * 334
                d.text((x, y), f"{job} {v.upper()} {r}  {tiles[k][1]} tris", font=voxel.font(15), fill=(150, 232, 255))
                cv.paste(tiles[k][0], (x, y + 24))
        cv.save(REVIEW / f"{job}.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
