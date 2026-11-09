# -*- coding: utf-8 -*-
"""奥のダンジョンの中ボス 4 体の形のラフ (2026-11-09 の声「中ボス (専用に)」)。中ボスごとに三案。
部品は地下のダンジョンの主と同じ (tools/gen/mobs/mounts_lp/mlp.py)。中ボスごとに一枚の比べシート。

    python -X utf8 -I tools/rough/lowpoly/deep_mids/roughs.py [fort|temple|ice|lava ...]   → docs/review/roughs_deep_mids/<id>.png

  fort   崩れた見張りの砦 (岩鯨 Lv 15) の内郭: A 大盾と鉄球の門番 / B 骨の軍旗の旗手 / C 骨の馬に乗る槍騎兵
  temple 沈んだ神殿 (潮殻 Lv 21) の珊瑚の中庭: A 背に鐘を負う溺れた鐘撞き / B 貝の大盾の珊瑚の騎士 / C 宙に浮く大海月の巫女
  ice    吹雪の氷穴 (雷翼 Lv 27) の氷の舞台: A 雪の大猿 / B 氷塊の番兵 (ゴーレム) / C 雷を帯びた大梟
  lava   熔岩の管の坑 (余燼 Lv 33) の熔岩の島: A 熔けた鉄の柄杓の鋳物師 / B 熔岩を背負う大蛙 / C 黒曜石の大甲虫
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(ROOT / "tools/gen/mobs/mounts_lp"))
sys.path.insert(0, str(ROOT / "tools/gen/mobs/dungeon_bosses_lp"))
from mlp_extra import *  # noqa: E402,F401,F403
import mlp  # noqa: E402
import blp  # noqa: E402

OUT = ROOT / "docs/review/roughs_deep_mids"

BONE = [(110, 100, 84), (150, 140, 120), (190, 180, 158), (220, 212, 192)]
LEATHER = [(34, 22, 16), (56, 36, 24), (82, 54, 36), (110, 76, 52)]
CLOTH = [(52, 18, 16), (82, 30, 24), (116, 46, 34), (146, 66, 46)]
IRON = [(34, 34, 40), (58, 58, 66), (88, 88, 96), (124, 124, 132)]
SKIN = [(78, 50, 36), (112, 76, 56), (146, 104, 78)]
WOOD = [(40, 26, 16), (62, 42, 26), (88, 60, 38)]
CORAL = [(120, 34, 48), (176, 56, 66), (224, 92, 92)]
SHELL = [(150, 136, 116), (196, 182, 160), (226, 216, 198)]
ROBE = [(14, 40, 50), (22, 60, 72), (34, 86, 98), (52, 114, 124)]
BRASS = [(110, 80, 30), (160, 120, 50), (206, 170, 90)]
JELLY = [(60, 90, 140), (100, 150, 200), (160, 210, 240)]
FURW = [(120, 126, 138), (170, 176, 188), (214, 220, 228)]
ICE = [(70, 120, 170), (110, 170, 220), (170, 220, 250)]
FEATH = [(40, 46, 70), (70, 80, 110), (110, 120, 150)]
BAS = [(20, 18, 20), (38, 34, 36), (58, 52, 54)]
MAG = [(160, 50, 16), (230, 110, 30), (255, 190, 80)]
OBS = [(14, 10, 20), (30, 22, 40), (54, 40, 70)]
FROG = [(50, 40, 30), (80, 60, 40), (120, 90, 56)]
GLOW = [(60, 200, 210), (150, 244, 240)]
BOLT = [(120, 190, 255), (220, 244, 255)]


def pal(**mats):
    m = {"eye": M_([(255, 160, 60), (255, 220, 140)], base=0.7, min=1)}
    m.update({k: M_(v, lit=0.34) for k, v in mats.items()})
    return dict(mats=m, glow_mats=["eye"] + [k for k in mats if k in ("mag", "glow", "bolt")])


def biped(m, top, legs, h=1.0, bulk=1.0, boots="leather"):
    """人型の胴と脚 (腰 22・肩 38 を h 倍)。返り値は肩と腰の高さ"""
    hip, sh = 22 * h, 38 * h
    m.add("torso", lof("body", top, [rsec(z, sh + 2 - abs(z) * 0.2, hip, 8.4 * bulk - abs(z) * 0.2, mid=(sh + hip) / 2 + 3, n=14) for z in (-5, -2, 0, 2, 5)]))
    for s in (1, -1):
        m.add(f"leg{s}", limb("body", legs, [(s * 4 * bulk, hip, 0), (s * 4.4 * bulk, hip * 0.5, -0.4), (s * 4.6 * bulk, 3, 0)], [6 * bulk, 5 * bulk, 4.4 * bulk], [6 * bulk, 5 * bulk, 4.6 * bulk], n=8))
        m.add(f"boot{s}", ell("body", boots, (s * 4.6 * bulk, 1.6, -1.2), (3.0 * bulk, 2.0, 4.2 * bulk), segs=8, rings=2))
    return sh, hip


def arms(m, mat, sh, bulk=1.0, out=11, hand="skin", reach=(0, 0)):
    for s in (1, -1):
        a = np.array([s * out * bulk * 0.95, sh - 1, 0])
        e = np.array([s * (out + 2) * bulk, sh - 11, -1 + reach[0]])
        w = np.array([s * (out + 2.4) * bulk, sh - 19, -3 + reach[1]])
        m.add(f"up{s}", limb("body", mat, [a, e], [5.4 * bulk, 4.8 * bulk], [5.4 * bulk, 4.8 * bulk], n=8))
        m.add(f"lo{s}", limb("body", mat, [e, w], [4.6 * bulk, 4.0 * bulk], [4.6 * bulk, 4.0 * bulk], n=8))
        m.add(f"hand{s}", ell("body", hand, w + np.array([0, -1.6, 0]), (2.4 * bulk, 2.6 * bulk, 2.4 * bulk), segs=8, rings=2))


def head(m, mat, c, r=3.4, eyes=True):
    c = np.asarray(c, float)
    m.add("head", ell("body", mat, c, (r, r * 1.1, r), segs=10, rings=3))
    if eyes:
        for s in (1, -1):
            m.add(f"eye{s}", ell("body", "eye", c + np.array([s * r * 0.4, 0.3, -r * 0.9]), (0.6, 0.6, 0.5), segs=6, rings=2))


# ====================================================================== 砦
def fort_a():
    m = Mount("fort_a", "A")
    sh, hip = biped(m, "leather", "leather", h=1.15, bulk=1.35, boots="iron")
    arms(m, "skin", sh, bulk=1.35)
    head(m, "iron", (0, sh + 5, -0.6), 3.8)
    m.add("visor", box("body", "eye", (0, sh + 5.2, -4.0), (4.6, 0.6, 0.4)))
    m.add("shield", box("body", "iron", (14.5, sh - 14, -6), (2.0, 24, 14), taper=(0.9, 1.0)))
    m.add("shield_boss", ell("body", "bone", (16, sh - 12, -6), (2.0, 3.0, 3.0), segs=8, rings=2))
    for k in range(4):
        m.add(f"chain{k}", ell("body", "iron", (-16.0, sh - 20 - k * 3.0, -4 - k * 1.2), (1.1, 1.5, 0.6), segs=6, rings=2))
    m.add("ball", ell("body", "iron", (-16.4, sh - 34, -9), (4.2, 4.2, 4.2), segs=10, rings=3))
    for k in range(6):
        a = math.radians(k * 60)
        m.add(f"spk{k}", cone("body", "bone", (-16.4 + math.cos(a) * 3.8, sh - 34, -9 + math.sin(a) * 3.8), (math.cos(a), 0, math.sin(a)), 2.4, 0.9, sides=4))
    m.add("pauldron", ell("body", "iron", (11, sh + 1.5, 0), (6, 3, 6), segs=10, rings=2))
    m.pal = pal(leather=LEATHER, skin=SKIN, iron=IRON, bone=BONE)
    return m


def fort_b():
    m = Mount("fort_b", "B")
    sh, hip = biped(m, "cloth", "leather", h=1.1, bulk=1.1)
    arms(m, "leather", sh, bulk=1.1)
    head(m, "bone", (0, sh + 5, -0.6), 3.6)
    m.add("horn_l", cone("body", "bone", (2.6, sh + 7.6, 0), (0.6, 1, 0.3), 5, 1.0, sides=5))
    m.add("horn_r", cone("body", "bone", (-2.6, sh + 7.6, 0), (-0.6, 1, 0.3), 5, 1.0, sides=5))
    m.add("pole", tub("body", "bone", [(-13.5, 4, -4), (-13.5, sh + 28, -4)], [0.9, 0.8], None, sides=6))
    m.add("banner", box("body", "cloth", (-20.5, sh + 18, -4), (13, 18, 0.5), taper=(0.9, 1.0)))
    m.add("skull_top", ell("body", "bone", (-13.5, sh + 30, -4), (2.6, 2.8, 2.6), segs=8, rings=3))
    for k in range(3):
        m.add(f"rib{k}", tub("body", "bone", [(-6, sh - k * 3, -5.6), (0, sh - 1 - k * 3, -6.6), (6, sh - k * 3, -5.6)], [0.6, 0.7, 0.6], None, sides=4))
    m.add("cape", box("body", "cloth", (0, sh - 12, 6), (14, 28, 0.6)))
    m.pal = pal(leather=LEATHER, cloth=CLOTH, bone=BONE)
    return m


def fort_c():
    m = Mount("fort_c", "C")
    # 骨の馬
    m.add("horse", lof("body", "bone", [rsec(z, 30 - abs(z) * 0.1, 18, 6.2, n=12) for z in (-14, -8, 0, 8, 14)]))
    for sx in (1, -1):
        for sz in (1, -1):
            m.add(f"hleg{sx}{sz}", limb("body", "bone", [(sx * 4.5, 20, sz * 11), (sx * 4.8, 10, sz * 11.5), (sx * 4.8, 1, sz * 11)], [2.6, 2.0, 2.2], [2.6, 2.0, 2.2], n=6))
    m.add("neck", tub("body", "bone", [(0, 26, -14), (0, 36, -20)], [3.4, 2.6], None, sides=8))
    m.add("skull", lof("body", "bone", [rsec(-19, 40, 33, 2.6, n=10), rsec(-26, 37, 34, 1.8, n=10)]))
    m.add("barding", box("body", "cloth", (0, 22, 0), (13.4, 8, 22), taper=(1.0, 1.0)))
    # 騎手
    m.add("rider", lof("body", "leather", [rsec(z, 48 - abs(z) * 0.2, 30, 6.4, n=12) for z in (-4, 0, 4)]))
    head(m, "iron", (0, 52, -0.5), 3.2)
    m.add("lance", tub("body", "wood", [(-8, 40, 10), (-8, 44, -34)], [0.9, 0.5], None, sides=6))
    m.add("lance_tip", cone("body", "iron", (-8, 44, -34), (0, 0.1, -1), 6, 1.4, sides=4))
    m.add("pennant", box("body", "cloth", (-8, 46.5, -28), (0.4, 3, 6)))
    m.pal = pal(leather=LEATHER, cloth=CLOTH, bone=BONE, iron=IRON, wood=WOOD)
    return m


# ====================================================================== 神殿
def temple_a():
    m = Mount("temple_a", "A")
    sh, hip = biped(m, "robe", "robe", h=1.1, bulk=1.25)
    arms(m, "robe", sh, bulk=1.25, hand="shell")
    head(m, "shell", (0, sh + 3.6, -3.4), 3.2)
    m.add("bell", lof("body", "brass", [oct_ring((0, sh + 10, 7), 8, 8, R=R_(90, 0, 0)), oct_ring((0, sh - 4, 7.5), 16, 16, R=R_(90, 0, 0)),
                                         oct_ring((0, sh - 10, 8), 20, 20, R=R_(90, 0, 0))]))
    m.add("yoke", tub("body", "wood", [(-9, sh + 2, 4), (9, sh + 2, 4)], [1.0, 1.0], None, sides=6))
    m.add("clapper", tub("body", "iron", [(-14, sh - 18, -3), (-14, sh - 30, -5)], [1.6, 1.0], None, sides=6))
    for k in range(5):
        m.add(f"barn{k}", cone("body", "shell", (math.cos(k) * 7, sh - 2 - k * 2, 9 + math.sin(k) * 2), (0, 0.2, 1), 1.4, 1.0, sides=5, rings=1))
    m.pal = pal(robe=ROBE, shell=SHELL, brass=BRASS, wood=WOOD, iron=IRON)
    return m


def temple_b():
    m = Mount("temple_b", "B")
    sh, hip = biped(m, "coral", "coral", h=1.15, bulk=1.25, boots="shell")
    arms(m, "coral", sh, bulk=1.25, hand="shell")
    head(m, "shell", (0, sh + 5, -0.6), 3.8)
    for k in range(5):
        a = math.radians(-60 + k * 30)
        m.add(f"crest{k}", cone("body", "coral", (math.sin(a) * 2, sh + 8.4, math.cos(a) * 1.2 + 1), (math.sin(a) * 0.4, 1, 0.2), 3.4, 0.7, sides=4))
    m.add("shield", ell("body", "shell", (15, sh - 12, -6), (2.2, 11, 9), segs=12, rings=3))
    for k in range(6):
        m.add(f"ridge{k}", tub("body", "shell", [(16.6, sh - 12, -6), (17.2, sh - 12 + math.cos(k) * 10, -6 + math.sin(k) * 8)], [0.6, 0.3], None, sides=4))
    m.add("trident", tub("body", "brass", [(-15, sh - 34, -2), (-15, sh + 10, -6)], [0.8, 0.8], None, sides=6))
    for k in (-1.6, 0, 1.6):
        m.add(f"prong{k}", cone("body", "brass", (-15 + k, sh + 10, -6), (k * 0.2, 1, -0.1), 5, 0.6, sides=4))
    m.pal = pal(coral=CORAL, shell=SHELL, brass=BRASS)
    return m


def temple_c():
    m = Mount("temple_c", "C")
    m.add("bell", lof("body", "jelly", [oct_ring((0, 46, 0), 6, 6, R=R_(90, 0, 0)), oct_ring((0, 42, 0), 22, 22, R=R_(90, 0, 0)), oct_ring((0, 34, 0), 26, 26, R=R_(90, 0, 0))]))
    m.add("core", ell("body", "glow", (0, 38, 0), (4, 4, 4), segs=10, rings=3))
    for k in range(10):
        a = math.radians(k * 36)
        p = (math.cos(a) * 10, 33, math.sin(a) * 10)
        m.add(f"ten{k}", tub("body", "jelly", [p, (p[0] * 1.1, 20, p[2] * 1.1), (p[0] * 0.8, 6 + (k % 3) * 3, p[2] * 0.8)], [1.2, 0.8, 0.3], None, sides=5))
    # 傘の下に吊られた巫女
    m.add("priest", lof("body", "robe", [rsec(z, 30 - abs(z) * 0.2, 14, 4.4, n=12) for z in (-3, 0, 3)]))
    head(m, "shell", (0, 31, -0.5), 2.6)
    m.pal = pal(jelly=JELLY, glow=GLOW, robe=ROBE, shell=SHELL)
    return m


# ====================================================================== 氷穴
def ice_a():
    m = Mount("ice_a", "A")
    m.add("torso", lof("body", "fur", [rsec(z, 42 - abs(z) * 0.3, 18, 11 - abs(z) * 0.2, mid=32, n=14) for z in (-7, -3, 0, 3, 7)]))
    head(m, "fur", (0, 42, -7), 4.6)
    m.add("face", ell("body", "skin", (0, 41, -10.6), (3.2, 3.0, 1.4), segs=8, rings=2))
    for s in (1, -1):
        m.add(f"arm{s}", limb("body", "fur", [(s * 12, 38, -2), (s * 16, 22, -6), (s * 15, 6, -8)], [8, 7, 6], [8, 7, 6], n=8))
        m.add(f"fist{s}", ell("body", "skin", (s * 15, 4, -8), (4, 3.4, 4), segs=8, rings=2))
        m.add(f"leg{s}", limb("body", "fur", [(s * 6, 18, 2), (s * 7, 9, 2), (s * 7, 2, 0)], [7, 6, 6], [7, 6, 6], n=8))
        m.add(f"icicle{s}", cone("body", "ice", (s * 8, 40, 4), (s * 0.4, 1, 0.5), 5, 1.4, sides=6))
    m.pal = pal(fur=FURW, skin=[(70, 80, 100), (100, 110, 130), (140, 150, 170)], ice=ICE)
    return m


def ice_b():
    m = Mount("ice_b", "B")
    m.add("torso", box("body", "ice", (0, 32, 0), (22, 18, 14), taper=(0.85, 1.0), cut=0.3))
    m.add("core", ell("body", "bolt", (0, 33, -7.2), (3, 3, 1), segs=8, rings=2))
    m.add("head", box("body", "ice", (0, 45, -1), (8, 7, 8), cut=0.3))
    for s in (1, -1):
        m.add(f"eye{s}", box("body", "eye", (s * 1.8, 45.6, -5.1), (1.4, 0.8, 0.4)))
        m.add(f"arm{s}", box("body", "ice", (s * 15, 26, -2), (7, 20, 8), cut=0.3))
        m.add(f"fist{s}", box("body", "ice", (s * 15.4, 13, -3), (9, 8, 9), cut=0.3))
        m.add(f"leg{s}", box("body", "ice", (s * 6, 11, 0), (8, 22, 9), cut=0.3))
        for k in range(3):
            m.add(f"crys{s}{k}", cone("body", "ice", (s * (5 + k * 3), 41, 3 + k), (s * 0.3, 1, 0.4), 6 - k, 1.6, sides=6))
    m.pal = pal(ice=ICE, bolt=BOLT)
    return m


def ice_c():
    m = Mount("ice_c", "C")
    m.add("body_o", ell("body", "feath", (0, 26, 0), (10, 13, 9), segs=12, rings=4))
    m.add("belly", ell("body", "fur", (0, 24, -4), (7.5, 10, 6), segs=10, rings=3))
    m.add("head_o", ell("body", "feath", (0, 41, -1), (8, 7, 7), segs=12, rings=3))
    for s in (1, -1):
        m.add(f"eyeo{s}", ell("body", "eye", (s * 3.2, 42, -6.8), (1.8, 1.8, 0.8), segs=8, rings=2))
        m.add(f"tuft{s}", cone("body", "feath", (s * 5, 46.5, -1), (s * 0.4, 1, 0.2), 4, 1.6, sides=4))
        m.add(f"wing{s}", slab("body", "feath", [(s * 9, 34, 0), (s * 26, 36, 4), (s * 30, 24, 6), (s * 12, 14, 2)], (1, 0, 0), 1.2))
        m.add(f"talon{s}", ell("body", "bolt", (s * 4, 12, -2), (2.6, 1.6, 3.2), segs=8, rings=2))
    m.add("beak", cone("body", "fur", (0, 39.5, -7.6), (0, -0.4, -1), 3, 1.2, sides=4))
    m.pal = pal(feath=FEATH, fur=FURW, bolt=BOLT)
    return m


# ====================================================================== 熔岩
def lava_a():
    m = Mount("lava_a", "A")
    sh, hip = biped(m, "leather", "leather", h=1.15, bulk=1.35, boots="bas")
    arms(m, "skin", sh, bulk=1.35, reach=(-4, -6))
    head(m, "bas", (0, sh + 4.6, -1.2), 3.6)
    m.add("mask", box("body", "iron", (0, sh + 4.6, -4.6), (5.4, 6, 0.8)))
    m.add("apron", box("body", "leather", (0, sh - 18, -6.8), (11, 20, 0.8), taper=(1.0, 0.9)))
    m.add("ladle_haft", tub("body", "iron", [(-16, sh - 20, -10), (-16, sh - 6, -26)], [0.8, 0.8], None, sides=6))
    m.add("ladle", ell("body", "iron", (-16, sh - 6, -30), (5, 3.0, 5), segs=10, rings=2))
    m.add("molten", ell("body", "mag", (-16, sh - 4.4, -30), (4, 0.8, 4), segs=10, rings=1))
    m.add("crucible", lof("body", "bas", [oct_ring((0, sh + 4, 8), 10, 10, R=R_(90, 0, 0)), oct_ring((0, sh - 10, 9), 14, 14, R=R_(90, 0, 0))]))
    m.add("crucible_glow", ell("body", "mag", (0, sh + 4.4, 8), (4, 0.8, 4), segs=8, rings=1))
    m.pal = pal(leather=LEATHER, skin=SKIN, bas=BAS, iron=IRON, mag=MAG)
    return m


def lava_b():
    m = Mount("lava_b", "B")
    m.add("body_f", ell("body", "frog", (0, 16, 2), (14, 11, 16), segs=14, rings=4))
    m.add("head_f", ell("body", "frog", (0, 20, -14), (11, 7, 8), segs=12, rings=3))
    m.add("mouth", box("body", "mag", (0, 17, -21.6), (14, 1.0, 1.0)))
    for s in (1, -1):
        m.add(f"eyef{s}", ell("body", "eye", (s * 6, 26, -14), (2.4, 2.4, 2.4), segs=8, rings=2))
        m.add(f"fl{s}", limb("body", "frog", [(s * 10, 12, -10), (s * 14, 4, -14), (s * 15, 1, -18)], [5, 4, 4], [5, 4, 4], n=8))
        m.add(f"hl{s}", limb("body", "frog", [(s * 12, 14, 10), (s * 20, 8, 6), (s * 16, 2, 14)], [7, 6, 5], [7, 6, 5], n=8))
    for k in range(5):
        m.add(f"vent{k}", lof("body", "bas", [oct_ring((math.cos(k * 1.3) * 6, 25, 4 + math.sin(k * 1.3) * 6), 5, 5, R=R_(90, 0, 0)),
                                               oct_ring((math.cos(k * 1.3) * 6, 29, 4 + math.sin(k * 1.3) * 6), 3, 3, R=R_(90, 0, 0))]))
        m.add(f"ventg{k}", ell("body", "mag", (math.cos(k * 1.3) * 6, 29.2, 4 + math.sin(k * 1.3) * 6), (1.4, 0.5, 1.4), segs=6, rings=1))
    m.pal = pal(frog=FROG, bas=BAS, mag=MAG)
    return m


def lava_c():
    m = Mount("lava_c", "C")
    m.add("shell", ell("body", "obs", (0, 16, 2), (13, 10, 18), segs=14, rings=4))
    m.add("seam", box("body", "mag", (0, 26.2, 2), (1.0, 0.6, 30)))
    m.add("headc", ell("body", "obs", (0, 12, -17), (7, 5, 5), segs=10, rings=3))
    m.add("horn", cone("body", "obs", (0, 15, -21), (0, 0.8, -1), 12, 2.4, sides=6, rings=3, bend=(0, 4, 0)))
    for s in (1, -1):
        m.add(f"eyec{s}", ell("body", "eye", (s * 3.4, 13, -21), (1.2, 1.2, 1), segs=6, rings=2))
        m.add(f"mand{s}", cone("body", "obs", (s * 3, 9, -21), (s * 0.4, -0.2, -1), 6, 1.2, sides=4, rings=2, bend=(-s * 1.6, 0, 0)))
        for k in range(3):
            z = -8 + k * 9
            m.add(f"legc{s}{k}", limb("body", "obs", [(s * 11, 12, z), (s * 18, 14, z), (s * 20, 1, z - 2)], [2.6, 2.2, 1.6], [2.6, 2.2, 1.6], n=6))
    for k in range(4):
        m.add(f"crack{k}", box("body", "mag", (math.cos(k) * 8, 22 - k, -6 + k * 5), (2.4, 0.6, 3)))
    m.pal = pal(obs=OBS, mag=MAG)
    return m


SETS = {
    "fort": ("砦の中ボス（崩れた見張りの砦 Lv 15）", [("A 大盾と鉄球の門番", fort_a), ("B 骨の軍旗の旗手", fort_b), ("C 骨の馬の槍騎兵", fort_c)]),
    "temple": ("神殿の中ボス（沈んだ神殿 Lv 21）", [("A 鐘を負う溺れた鐘撞き", temple_a), ("B 貝の大盾の珊瑚の騎士", temple_b), ("C 大海月の巫女", temple_c)]),
    "ice": ("氷穴の中ボス（吹雪の氷穴 Lv 27）", [("A 雪の大猿", ice_a), ("B 氷塊の番兵", ice_b), ("C 雷の大梟", ice_c)]),
    "lava": ("熔岩の坑の中ボス（熔岩の管の坑 Lv 33）", [("A 熔けた鉄の鋳物師", lava_a), ("B 熔岩を背負う大蛙", lava_b), ("C 黒曜石の大甲虫", lava_c)]),
}


def sheet(key):
    title, opts = SETS[key]
    F = voxel.font
    PW, PH = 380, 360
    W_ = 20 + 3 * (PW + 10)
    H_ = 70 + 2 * (PH + 34) + 10
    cv = Image.new("RGB", (W_, H_), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((20, 16), title, font=F(26), fill=(236, 232, 218))
    for i, (label, fn) in enumerate(opts):
        m = fn()
        size, atlas, glow = mlp.paint_mount(m, m.pal, None, 256)
        tris = sum(len(f.verts) - 2 for f in m.F)
        for r, (yaw, pitch) in enumerate(((210, 12), (290, 8))):
            im = view(m.F, atlas, glow, yaw, pitch, (PW, PH), (0, 26, 0), 2.6)
            x = 20 + i * (PW + 10)
            y = 64 + r * (PH + 34)
            cv.paste(im, (x, y + 26))
            if r == 0:
                d.text((x, y), f"{label}  {tris} 三角形", font=F(17), fill=(150, 232, 255))
    OUT.mkdir(parents=True, exist_ok=True)
    cv.save(OUT / f"{key}.png")
    print("wrote", OUT / f"{key}.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    for k in (sys.argv[1:] or list(SETS)):
        sheet(k)
