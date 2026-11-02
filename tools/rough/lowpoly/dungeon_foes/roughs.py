# -*- coding: utf-8 -*-
"""地下のダンジョンの専用の敵 (道中の敵 2 種 + 中ボス 1 体 × 4 つ) の形のラフ (docs/design/55 の 4 章の 3)。一体ごとに三案。
部品は乗り物の低ポリの部品 (tools/gen/mobs/mounts_lp/mlp.py)。一体ごとに一枚の比べシートを書く。
長さの単位は 1/16 ブロック (人 = 29)。前は -z。

    python -X utf8 -I tools/rough/lowpoly/dungeon_foes/roughs.py [digger grub foreman sailor octoling bellcrab beetle bat golem miner slug warden ...]
    → docs/review/roughs_dungeon_foes/<名>.png と <名>_<a|b|c>.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(ROOT / "tools/gen/mobs/mounts_lp"))
from mlp_extra import *  # noqa: E402,F401,F403

OUT = ROOT / "docs/review/roughs_dungeon_foes"


def bpal(mats, glow=()):
    base = {"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0)}
    base.update(mats)
    return dict(mats=base, glow_mats=list(glow))


def eye(m, bone, c, r, d, iris, glow=False, lid=(20, 20, 24)):
    c = np.asarray(c, float)
    m.add(f"eye{len(m.eyes)}", ell(bone, "eye", c, (r, r, r), segs=8, rings=2))
    m.eyes.append(dict(c=c, r=r * 1.08, dir=tuple(nrm(np.asarray(d, float))), a=r * 0.8, b=r * 0.8, bone=bone, iris=iris, lid=lid, pa=0.5, pb=0.5, glow=glow))


def P(*a):
    return np.array(a, float)


def grow(m, k):
    """形を丸ごと k 倍に (骨の支点・目も)"""
    for f in m.F:
        f.verts = np.asarray(f.verts, float) * k
        f.ref = np.asarray(f.ref, float) * k
        if f.loc is not None:
            f.loc = np.asarray(f.loc, float) * k
    for n, (par, pv) in list(m.bones.items()):
        m.bones[n] = (par, tuple(x * k for x in pv))
    for e in m.eyes:
        e["c"] = np.asarray(e["c"], float) * k
        e["r"] *= k
        e["a"] *= k
        e["b"] *= k


# ---------------------------------------------------------------------- 色
BONE = [(110, 100, 84), (150, 140, 120), (190, 180, 158), (220, 212, 192), (240, 234, 218)]
FLESH = [(40, 22, 22), (62, 34, 32), (88, 50, 44), (116, 70, 60)]
STONE = [(40, 40, 46), (58, 58, 66), (80, 80, 90), (104, 104, 114)]
ORE = [(150, 90, 30), (210, 140, 50), (250, 196, 96), (255, 236, 170)]
IRON = [(40, 36, 36), (62, 56, 54), (88, 80, 76), (118, 108, 100)]
WOOD = [(46, 30, 18), (70, 46, 28), (98, 66, 40), (128, 90, 56)]
CLOTH = [(40, 34, 30), (62, 54, 46), (86, 76, 64), (112, 100, 84)]
SEA = [(22, 50, 58), (34, 76, 84), (52, 108, 112), (80, 140, 138)]
CORAL = [(120, 44, 52), (170, 70, 72), (214, 112, 100), (240, 160, 140)]
BARN = [(150, 146, 132), (186, 182, 166), (214, 210, 196), (236, 232, 220)]
INK = [(30, 20, 40), (48, 32, 60), (72, 48, 88), (100, 70, 118)]
SHELL = [(110, 80, 60), (150, 112, 84), (190, 150, 112), (220, 186, 146)]
BRASSY = [(78, 56, 22), (126, 94, 38), (178, 138, 62), (222, 184, 98)]
GLOWSEA = [(40, 150, 160), (90, 210, 210), (170, 245, 240), (230, 255, 252)]
CHIT = [(26, 30, 44), (40, 46, 66), (58, 66, 92), (80, 90, 120)]
CRY = [(60, 120, 200), (110, 180, 240), (170, 220, 255), (230, 245, 255)]
STORM = [(52, 54, 70), (74, 78, 98), (100, 106, 128), (130, 136, 158)]
FUR = [(40, 38, 48), (60, 56, 70), (84, 80, 96), (110, 106, 124)]
BAS = [(24, 22, 24), (38, 34, 36), (54, 48, 50), (72, 64, 64), (94, 84, 82)]
MAG = [(160, 50, 16), (220, 96, 30), (252, 156, 56), (255, 214, 120), (255, 244, 200)]
ASH = [(60, 56, 54), (86, 80, 76), (112, 104, 98), (140, 132, 124)]
BRICK = [(80, 34, 24), (112, 50, 34), (146, 70, 46), (176, 96, 66)]
LEATHR = [(46, 28, 18), (76, 48, 28), (108, 72, 42), (142, 102, 62)]


# ---------------------------------------------------------------------- 人の形の下ごしらえ
def legs(m, mat, hip_y, spread, foot_mat=None, knee_fwd=1.0, thick=4.0, foot=(3.0, 1.8, 4.2), z=0.0, bend=0.0):
    for s in (1, -1):
        hip = P(s * spread, hip_y, z)
        knee = P(s * (spread + 0.4), hip_y * 0.5, z - knee_fwd)
        ank = P(s * (spread + 0.3), 2.0, z + bend)
        m.add(f"leg{s}", limb("body", mat, [hip, knee, ank], [thick, thick * 0.85, thick * 0.7], [thick, thick * 0.85, thick * 0.7], n=8))
        m.add(f"foot{s}", ell("body", foot_mat or mat, ank + P(0, -0.6, -1.2), foot, segs=8, rings=2))


def arm(m, key, mat, sh, el, wr, r=3.0, fist=None, fist_mat=None):
    sh, el, wr = P(*sh), P(*el), P(*wr)
    m.add(f"arm{key}", limb("body", mat, [sh, el, wr], [r, r * 0.85, r * 0.75], [r, r * 0.85, r * 0.75], n=8))
    if fist:
        m.add(f"fist{key}", ell("body", fist_mat or mat, wr + P(0, -1.2, 0), (fist, fist, fist), segs=7, rings=2))
    return wr


def shaft(m, key, mat, a, b, r=0.7, sides=5):
    m.add(key, tub("body", mat, [P(*a), P(*b)], [r, r], None, sides=sides))


# ====================================================================== 鯨骨の坑 (Lv 10)

def digger_a():
    """A 骨の坑夫: 腰の曲がった骸の坑夫。肋骨の籠の胸、鶴嘴を担ぎ、背に灯りの籠"""
    m = Mount("digger_a", "骨の坑夫")
    m.bone("body", "root", (0, 14, 0))
    legs(m, "bone", 13, 3.2, thick=2.6, foot=(2.4, 1.4, 3.6))
    m.add("pelvis", ell("body", "bone", (0, 14, 0.6), (4.4, 2.4, 3.2), segs=8, rings=2), core=True)
    m.add("spine", tub("body", "bone", [P(0, 14, 1), P(0, 19, 2.5), P(0, 23, 1.0)], [1.2, 1.2, 1.2], None, sides=5))
    m.add("ribs", ell("body", "bone", (0, 21, -0.6), (5.6, 4.4, 4.4), segs=10, rings=3))
    m.add("ribgap", ell("body", "flesh", (0, 21, -2.6), (3.6, 3.4, 2.6), segs=8, rings=2))
    m.add("skull", ell("body", "bone", (0, 25.4, -3.4), (3.2, 3.4, 3.6), segs=9, rings=3))
    m.add("jaw", ell("body", "bone", (0, 23.4, -4.8), (2.4, 1.2, 2.0), segs=7, rings=2))
    m.add("cap", ell("body", "cloth", (0, 27.6, -3.0), (3.6, 1.6, 3.8), segs=9, rings=2))
    for s in (1, -1):
        eye(m, "body", (s * 1.3, 25.8, -6.6), 0.7, (s * 0.2, 0, -1), (255, 190, 90), glow=True)
    wr = arm(m, "l", "bone", (5, 23, -0.5), (7.5, 18, -3), (6, 14, -6), r=1.8, fist=1.6)
    arm(m, "r", "bone", (-5, 23, -0.5), (-6.5, 26, -2), (-4, 29, -1), r=1.8, fist=1.6)
    shaft(m, "haft", "wood", (-4, 29, -1), (-3, 30, 10), r=0.8)
    m.add("pick", cone("body", "iron", P(-3, 30, 10), (0, -0.4, 1), 6.0, 1.0, sides=4, rings=2, bend=(0, -2.0, 0)))
    m.add("pick2", cone("body", "iron", P(-3, 30, 10), (0, 0.6, -0.2), 4.0, 1.0, sides=4, rings=2, bend=(0, 0, -1.4)))
    m.add("basket", box("body", "wood", (0, 21, 5.2), (6, 6, 3.6), taper=(0.85, 0.9), cut=0.2))
    m.add("lamp", ell("body", "ore", (0, 25.6, 5.2), (1.6, 2.0, 1.6), segs=7, rings=2))
    m.pal = bpal({"bone": M_(BONE, lit=0.34), "flesh": M_(FLESH), "cloth": M_(CLOTH), "wood": M_(WOOD), "iron": M_(IRON), "ore": M_(ORE, base=0.64, lit=0.2)}, glow=["ore"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["bone"], (120, 110, 92), density=0.06, size=0.8), glow)
    m.view = dict(center=(0, 15, 0), scale=5.0)
    m.caption = ["腰の曲がった骸の坑夫（人より少し低い）", "肋骨の籠の胸、布の帽子、鶴嘴を担ぐ", "背に光る灯りの籠"]
    return m


def digger_b():
    """B 鯨骨の鎧の坑夫: 鯨の頭骨の兜をかぶった、ずんぐりした坑夫。両手で大きな円匙"""
    m = Mount("digger_b", "鯨骨の兜の坑夫")
    m.bone("body", "root", (0, 12, 0))
    legs(m, "cloth", 11, 3.6, foot_mat="leath", thick=4.0, foot=(3.0, 1.8, 4.2))
    m.add("torso", lof("body", "cloth", [rsec(-4, 22, 11, 6.0, n=12), rsec(0, 24, 10, 7.0, n=12), rsec(4, 22, 11, 6.0, n=12)]), core=True)
    m.add("belt", ell("body", "leath", (0, 12.5, 0), (7.4, 1.4, 5.2), segs=10, rings=2))
    m.add("plate", ell("body", "bone", (0, 19, -3.6), (5.4, 4.2, 2.0), segs=9, rings=2))
    m.add("helm", lof("body", "bone", [rsec(3.2, 30, 22.5, 5.0, n=12), rsec(-1.5, 31.5, 22, 5.6, n=12), rsec(-6, 29, 23.5, 4.0, n=12), rsec(-9.5, 26.5, 24.5, 2.0, n=12)]))
    m.add("face", ell("body", "flesh", (0, 24.5, -3.6), (3.2, 2.6, 2.2), segs=8, rings=2))
    for s in (1, -1):
        eye(m, "body", (s * 1.4, 25.2, -5.4), 0.6, (s * 0.2, 0, -1), (255, 210, 120), glow=True)
        m.add(f"horn{s}", cone("body", "bone", P(s * 4.2, 28, 1), (s * 0.7, 0.4, 0.6), 5.0, 1.3, sides=5, rings=2, bend=(0, 1.0, 0)))
    arm(m, "l", "cloth", (6.6, 21, 0), (8, 16, -3), (4, 13, -7), r=2.6, fist=1.8, fist_mat="leath")
    arm(m, "r", "cloth", (-6.6, 21, 0), (-8, 17, -2), (-3, 18, -7), r=2.6, fist=1.8, fist_mat="leath")
    shaft(m, "haft", "wood", (-3, 18, -7), (4, 4, -8), r=0.8)
    m.add("blade", box("body", "iron", (5.2, 1.8, -8.2), (4.0, 4.6, 1.0), taper=(0.7, 1.0), cut=0.3))
    m.pal = bpal({"bone": M_(BONE, lit=0.34), "flesh": M_(FLESH), "cloth": M_(CLOTH, streak=0.05), "leath": M_(LEATHR), "wood": M_(WOOD), "iron": M_(IRON)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["bone"], (120, 110, 92), density=0.05, size=0.8), glow)
    m.view = dict(center=(0, 15, 0), scale=5.0)
    m.caption = ["鯨の頭骨を兜にかぶったずんぐりした坑夫", "胸に骨の当て板、両手で大きな円匙", "兜の両わきに曲がった角"]
    return m


def digger_c():
    """C 四つ腕の掘り骸: 腕が四本の骸。上の二本は鶴嘴そのもの (前腕が刃)、下の二本で這う"""
    m = Mount("digger_c", "四つ腕の掘り骸")
    m.bone("body", "root", (0, 10, 0))
    legs(m, "bone", 10, 3.0, thick=2.4, foot=(2.2, 1.2, 3.2), knee_fwd=-1.5)
    m.add("pelvis", ell("body", "bone", (0, 10.5, 1.0), (4.0, 2.2, 3.0), segs=8, rings=2), core=True)
    m.add("spine", tub("body", "bone", [P(0, 10.5, 1), P(0, 15, -1), P(0, 18, -4)], [1.3, 1.3, 1.3], None, sides=5))
    m.add("ribs", ell("body", "bone", (0, 16.5, -2.6), (5.4, 3.6, 4.6), segs=10, rings=3, rx=-25))
    m.add("ribgap", ell("body", "ore", (0, 16.0, -4.0), (2.4, 2.0, 2.4), segs=8, rings=2))
    m.add("skull", ell("body", "bone", (0, 19.5, -7.4), (3.0, 3.0, 3.6), segs=9, rings=3))
    for s in (1, -1):
        eye(m, "body", (s * 1.2, 19.8, -10.6), 0.7, (s * 0.2, 0, -1), (255, 180, 70), glow=True)
        # 上の腕: 前腕が刃
        sh = P(s * 4.6, 18.5, -3.5)
        el = P(s * 9, 22, -5)
        m.add(f"up{s}", limb("body", "bone", [sh, el], [2.0, 1.6], [2.0, 1.6], n=7))
        m.add(f"blade{s}", cone("body", "iron", el, (s * -0.2, -0.3, -1), 10.0, 1.6, sides=4, rings=2, bend=(0, -3.0, 0)))
        # 下の腕: 地を這う
        sh2 = P(s * 4.0, 14.5, -3.5)
        m.add(f"lo{s}", limb("body", "bone", [sh2, sh2 + P(s * 3, -4, -3), P(s * 6, 1.5, -9)], [1.6, 1.4, 1.1], [1.6, 1.4, 1.1], n=7))
        m.add(f"hand{s}", ell("body", "bone", (s * 6, 1.4, -9.6), (1.6, 0.8, 2.0), segs=6, rings=2))
    m.pal = bpal({"bone": M_(BONE, lit=0.34), "iron": M_(IRON), "ore": M_(ORE, base=0.64, lit=0.2)}, glow=["ore"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["bone"], (120, 110, 92), density=0.06, size=0.8), glow)
    m.view = dict(center=(0, 12, -2), scale=5.0)
    m.caption = ["腕が四本の骸。前かがみで這う", "上の二本は前腕が鶴嘴の刃", "胸の奥に光る鉱石"]
    return m


def grub_a():
    """A 骨喰いの幼虫: 白く太い節の芋虫。頭に骨の大顎、背に小さな骨のかけら"""
    m = Mount("grub_a", "骨喰いの幼虫")
    m.bone("body", "root", (0, 4, 0))
    pts = [P(0, 5.0, -8 + 3.2 * i) for i in range(6)]
    for i, p in enumerate(pts):
        r = 4.2 - 0.35 * i
        m.add(f"seg{i}", ell("body", "flesh", p + P(0, r * 0.1, 0), (r, r * 0.85, r * 0.75), segs=10, rings=3), core=i == 2)
        m.add(f"chip{i}", cone("body", "bone", p + P(0, r * 0.75, 0), (0.2 * (-1) ** i, 1, 0.3), 2.0, 0.9, sides=4, rings=1))
    head = P(0, 5.6, -11)
    m.add("head", ell("body", "bone", head, (3.4, 3.0, 2.8), segs=9, rings=3))
    for s in (1, -1):
        m.add(f"mand{s}", cone("body", "bone", head + P(s * 2.0, -1.4, -1.6), (s * 0.4, -0.2, -1), 4.0, 1.0, sides=4, rings=2, bend=(-s * 1.6, 0, 0)))
        eye(m, "body", head + P(s * 1.6, 1.0, -2.4), 0.55, (s * 0.3, 0.2, -1), (240, 120, 60), glow=True)
    m.pal = bpal({"flesh": M_([(170, 150, 130), (200, 182, 160), (226, 210, 190), (244, 234, 218)], lit=0.3), "bone": M_(BONE, lit=0.34)})
    m.detail = lambda c, col, glow: (bands_y(c, col, ["flesh"], 3.2, 0.6, k=0.85), glow)
    m.view = dict(center=(0, 4, -2), scale=5.4)
    m.caption = ["白く太い節の芋虫（膝ほどの高さ）", "骨の大顎で噛みつく、群れで出る", "背に骨のかけらが刺さる"]
    return m


def grub_b():
    """B 骨甲の団子虫: 骨の板を重ねた甲を負う。転がって突っ込む"""
    m = Mount("grub_b", "骨甲の団子虫")
    m.bone("body", "root", (0, 4, 0))
    for i in range(7):
        a = math.radians(-70 + 140 * i / 6)
        c = P(0, 3.6 + 2.8 * math.cos(a), -6.4 * math.sin(a))
        m.add(f"plate{i}", ell("body", "bone", c, (5.6 - abs(i - 3) * 0.5, 1.6, 2.4), segs=10, rings=2, rx=-math.degrees(a)), core=i == 3)
    m.add("belly", ell("body", "chitin", (0, 3.2, 0), (4.6, 2.4, 5.6), segs=10, rings=2))
    m.add("head", ell("body", "chitin", (0, 3.4, -7.0), (2.8, 2.2, 2.0), segs=8, rings=2))
    for s in (1, -1):
        eye(m, "body", (s * 1.4, 4.2, -8.6), 0.5, (s * 0.3, 0.2, -1), (230, 220, 160), glow=False)
        m.add(f"ant{s}", tub("body", "chitin", [P(s * 1.0, 4.6, -8.4), P(s * 3.2, 6.4, -11), P(s * 4.6, 6.0, -12.6)], [0.4, 0.3, 0.2], None, sides=4))
        for k in range(4):
            z = -4.5 + 3 * k
            m.add(f"leg{s}{k}", tub("body", "chitin", [P(s * 3.6, 2.4, z), P(s * 5.0, 1.6, z - 0.5), P(s * 5.4, 0.3, z - 0.8)], [0.6, 0.5, 0.3], None, sides=4))
    m.pal = bpal({"bone": M_(BONE, lit=0.34), "chitin": M_(CHIT)})
    m.detail = lambda c, col, glow: (edge_dark(c, col, ["bone"], k=0.8), glow)
    m.view = dict(center=(0, 4, -1), scale=5.4)
    m.caption = ["骨の板を重ねた甲を負う団子虫", "丸まって転がり、突っ込む", "群れで坑道の床を這う"]
    return m


def grub_c():
    """C 骨の壁蜘蛛: 小さな頭骨を胴にした六本脚の蜘蛛。壁と天井を這い、上から落ちてくる"""
    m = Mount("grub_c", "骨の壁蜘蛛")
    m.bone("body", "root", (0, 6, 0))
    m.add("skull", ell("body", "bone", (0, 6.5, -1.0), (3.4, 3.0, 3.8), segs=10, rings=3), core=True)
    m.add("abdomen", ell("body", "flesh", (0, 7.0, 4.4), (3.6, 3.2, 4.2), segs=10, rings=3))
    for s in (1, -1):
        eye(m, "body", (s * 1.4, 7.2, -4.4), 0.8, (s * 0.2, 0.1, -1), (255, 90, 60), glow=True)
        for k in range(3):
            z = -2.0 + 2.4 * k
            hip = P(s * 2.8, 6.4, z)
            knee = P(s * 7.0, 10.0, z - 1.0 + k)
            foot = P(s * 9.6, 0.4, z - 2 + 2 * k)
            m.add(f"leg{s}{k}", tub("body", "bone", [hip, knee, foot], [0.8, 0.6, 0.2], None, sides=4))
    m.add("fang", cone("body", "bone", P(0, 4.8, -4.2), (0, -0.6, -1), 2.4, 0.9, sides=4, rings=1))
    m.pal = bpal({"bone": M_(BONE, lit=0.34), "flesh": M_(FLESH)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["bone"], (120, 110, 92), density=0.06, size=0.8), glow)
    m.view = dict(center=(0, 5, 0), scale=5.4)
    m.caption = ["小さな頭骨を胴にした六本脚の蜘蛛", "壁と天井を這い、上から落ちてくる", "赤く光る目"]
    return m


def foreman_a():
    """A 骨鯨の兜の坑頭: 大男。鯨の頭骨の兜と骨の肩当て、肩に担いだ大きな骨の槌"""
    m = Mount("foreman_a", "骨鯨の兜の坑頭")
    m.bone("body", "root", (0, 20, 0))
    legs(m, "leath", 19, 6.0, foot_mat="leath", thick=6.4, foot=(4.6, 2.6, 6.0))
    m.add("torso", lof("body", "flesh", [rsec(-6, 38, 18, 10, n=14), rsec(0, 42, 17, 12, n=14), rsec(6, 38, 18, 10, n=14)]), core=True)
    m.add("apron", box("body", "leath", (0, 22, -6.4), (14, 12, 1.4), taper=(1.0, 1.0), cut=0.2))
    m.add("helm", lof("body", "bone", [rsec(5, 52, 40, 7.0, n=12), rsec(-1, 54, 39, 8.0, n=12), rsec(-8, 50, 41, 6.0, n=12), rsec(-14, 46, 43, 2.6, n=12)]))
    m.add("jawface", ell("body", "flesh", (0, 42, -6.0), (4.4, 3.4, 3.0), segs=8, rings=2))
    for s in (1, -1):
        eye(m, "body", (s * 2.6, 46, -8.2), 1.0, (s * 0.2, 0, -1), (255, 170, 70), glow=True)
        m.add(f"pauld{s}", ell("body", "bone", (s * 12, 41, 0), (6.0, 3.6, 6.0), segs=10, rings=2))
        m.add(f"spike{s}", cone("body", "bone", P(s * 13, 43, 0), (s * 0.5, 1, 0), 5.0, 1.6, sides=5, rings=2))
    arm(m, "l", "flesh", (12, 38, 0), (15, 28, -3), (13, 20, -8), r=4.4, fist=3.2)
    arm(m, "r", "flesh", (-12, 38, 0), (-15, 44, 2), (-11, 50, 4), r=4.4, fist=3.2)
    shaft(m, "haft", "bone", (-11, 50, 4), (-6, 40, 26), r=1.6, sides=6)
    m.add("maul", lof("body", "bone", [rsec(26, 46, 34, 6, n=10, cx=-6), rsec(30, 47, 33, 7, n=10, cx=-6), rsec(34, 46, 34, 6, n=10, cx=-6)]))
    m.pal = bpal({"bone": M_(BONE, lit=0.34), "flesh": M_([(70, 52, 44), (98, 74, 62), (128, 98, 82), (156, 124, 104)]), "leath": M_(LEATHR)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["bone"], (120, 110, 92), density=0.05, size=0.9), glow)
    m.view = dict(center=(0, 28, 2), scale=3.0)
    m.caption = ["人の倍ほどの大男（坑夫の頭）", "鯨の頭骨の兜、骨の肩当て", "肩に担いだ大きな骨の槌で地を割る"]
    return m


def foreman_b():
    """B 骨と岩の掘り機: 坑夫が作った掘る機械。岩と骨の体、右腕は回る錐、背に煙突"""
    m = Mount("foreman_b", "骨と岩の掘り機")
    m.bone("body", "root", (0, 18, 0))
    legs(m, "iron", 17, 7.0, foot_mat="stone", thick=6.0, foot=(5.0, 2.6, 6.4), knee_fwd=-1)
    m.add("hull", box("body", "stone", (0, 30, 0), (22, 20, 18), taper=(0.85, 0.9), cut=0.3), core=True)
    m.add("ribcage", ell("body", "bone", (0, 31, -8.6), (8, 7, 2.0), segs=10, rings=2))
    m.add("core", ell("body", "ore", (0, 31, -9.6), (3.4, 3.4, 1.4), segs=8, rings=2))
    m.add("head", box("body", "iron", (0, 43, -3), (9, 6, 8), taper=(0.8, 0.9), cut=0.3))
    for s in (1, -1):
        eye(m, "body", (s * 2.4, 43.5, -7.2), 1.0, (0, 0, -1), (255, 200, 90), glow=True)
    m.add("stack", tub("body", "iron", [P(5, 40, 7), P(6, 54, 9)], [2.2, 1.8], None, sides=6))
    arm(m, "l", "iron", (12, 36, 0), (16, 27, -3), (15, 20, -6), r=4.0, fist=3.6, fist_mat="stone")
    arm(m, "r", "iron", (-12, 36, 0), (-16, 30, -4), (-14, 26, -12), r=4.0)
    m.add("drill", cone("body", "iron", P(-14, 26, -12), (0, -0.2, -1), 14.0, 4.4, sides=8, rings=4))
    m.pal = bpal({"stone": M_(STONE, lit=0.32, streak=0.04), "bone": M_(BONE, lit=0.34), "iron": M_(IRON), "ore": M_(ORE, base=0.66, lit=0.2)}, glow=["ore"])
    m.detail = lambda c, col, glow: (plates(c, col, ["stone"], size=4.0, edge=0.05, k_edge=0.6), glow)
    m.view = dict(center=(0, 26, -2), scale=3.0)
    m.caption = ["坑夫の作った掘る機械（人の倍ほど）", "岩と骨の体、胸の奥に光る鉱石", "右腕は回る錐、背に煙突"]
    return m


def foreman_c():
    """C 鉱石負いの大土竜: 大きな鼠のような土竜。背に鉱石の結晶の房、前足は大きな掘る爪"""
    m = Mount("foreman_c", "鉱石負いの大土竜")
    m.bone("body", "root", (0, 12, 0))
    m.add("trunk", lof("body", "fur", [rsec(16, 16, 4, 8, n=14), rsec(6, 22, 3, 11, n=14), rsec(-6, 22, 3, 11, n=14), rsec(-14, 17, 5, 8, n=14)]), core=True)
    m.add("snout", lof("body", "fur", [rsec(-14, 17, 6, 7, n=12), rsec(-20, 14, 7, 4.6, n=12), rsec(-24, 11.5, 8, 2.2, n=12)]))
    m.add("nose", ell("body", "flesh", (0, 10.4, -24.6), (1.8, 1.6, 1.2), segs=7, rings=2))
    for s in (1, -1):
        eye(m, "body", (s * 4.2, 15.4, -17.0), 0.7, (s * 0.6, 0.2, -1), (240, 220, 160))
        sh = P(s * 8, 10, -10)
        m.add(f"fore{s}", limb("body", "fur", [sh, sh + P(s * 3, -5, -3), P(s * 12, 1.5, -14)], [4, 3.4, 3], [4, 3.4, 3], n=8))
        for k in range(3):
            m.add(f"claw{s}{k}", cone("body", "bone", P(s * (12 + (k - 1) * 1.6), 1.6, -15.5), (s * 0.2 * (k - 1), -0.3, -1), 5.0, 0.9, sides=4, rings=2, bend=(0, -1.4, 0)))
        m.add(f"hind{s}", limb("body", "fur", [P(s * 7, 9, 10), P(s * 9, 5, 12), P(s * 9, 1.4, 10)], [4, 3.4, 2.8], [4, 3.4, 2.8], n=8))
    for k in range(9):
        a = k * 2.399
        r = 3 + 1.2 * (k % 3)
        base = P(math.cos(a) * r, 21.5, -2 + math.sin(a) * r * 1.6)
        m.add(f"ore{k}", cone("body", "ore", base, (math.cos(a) * 0.4, 1, math.sin(a) * 0.3), 6 + (k % 4) * 2, 1.8, sides=5, rings=1))
    m.add("tail", tub("body", "flesh", [P(0, 10, 16), P(0, 6, 24), P(0, 3, 30)], [1.6, 1.0, 0.4], None, sides=5))
    m.pal = bpal({"fur": M_(FUR, streak=0.04), "flesh": M_([(150, 100, 100), (180, 126, 120), (206, 156, 146), (226, 186, 176)]), "bone": M_(BONE, lit=0.34),
                  "ore": M_(ORE, base=0.66, lit=0.2)}, glow=["ore"])
    m.detail = lambda c, col, glow: (fur(c, col, ["fur"], amt=0.10), glow)
    m.view = dict(center=(0, 12, -2), scale=3.4)
    m.caption = ["大きな鼠のような土竜（人の肩ほどの高さ）", "背に光る鉱石の結晶の房", "前足は大きな掘る爪、潜って出る"]
    return m


# ====================================================================== 潮の洞 (Lv 16)

def sailor_a():
    """A 沈み船の水夫: ふくれた水死人の水夫。藤壺と貝の殻、手に銛"""
    m = Mount("sailor_a", "沈み船の水夫")
    m.bone("body", "root", (0, 14, 0))
    legs(m, "cloth", 13, 3.2, foot_mat="sea", thick=3.4)
    m.add("torso", ell("body", "cloth", (0, 19.4, 0), (6.4, 7.4, 4.4), segs=12, rings=4), core=True)
    m.add("belly", ell("body", "sea", (0, 17.6, -2.2), (4.6, 4.4, 3.0), segs=10, rings=3))
    m.add("neck", ell("body", "sea", (0, 26.4, -0.4), (2.2, 1.8, 2.2), segs=8, rings=2))
    m.add("head", ell("body", "sea", (0, 29, -0.6), (3.4, 3.6, 3.4), segs=9, rings=3))
    m.add("hat", lof("body", "cloth", [ysec(31.4, 9, 9), ysec(32.2, 6.4, 6.4), ysec(34.0, 5.0, 5.0)]))
    for s in (1, -1):
        eye(m, "body", (s * 1.3, 29.4, -3.6), 0.7, (s * 0.2, 0, -1), (170, 255, 240), glow=True)
    for k, (x, y, z) in enumerate(((3, 22, -5), (-4, 18, -4.6), (5.4, 26, 1), (-2, 27.6, 3))):
        m.add(f"barn{k}", cone("body", "barn", P(x, y, z), (x * 0.1, 0.2, z * 0.1), 1.6, 1.2, sides=6, rings=1))
    arm(m, "l", "sea", (6.2, 24, 0), (8, 18, -2), (6, 14, -5), r=2.6, fist=1.8)
    arm(m, "r", "sea", (-6.2, 24, 0), (-8, 22, -4), (-5, 24, -9), r=2.6, fist=1.8)
    shaft(m, "spear", "wood", (-5, 24, -4), (-5, 24, -22), r=0.6)
    m.add("tip", cone("body", "iron", P(-5, 24, -22), (0, 0, -1), 3.4, 1.0, sides=4, rings=1))
    m.pal = bpal({"sea": M_(SEA, streak=0.05), "cloth": M_([(70, 70, 90), (96, 96, 118), (126, 126, 146), (160, 160, 176)], stripe=4, axis="u"),
                  "barn": M_(BARN, lit=0.34), "wood": M_(WOOD), "iron": M_(IRON)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["sea"], (30, 90, 80), density=0.08, size=0.8), glow)
    m.view = dict(center=(0, 17, -4), scale=4.6)
    m.caption = ["ふくれた水死人の水夫（人ほど）", "縞の服、藤壺と貝の殻が付く", "手に銛。投げてくる"]
    return m


def sailor_b():
    """B 珊瑚の骸の水夫: 骸に珊瑚が生え、片目の眼帯と曲刀"""
    m = Mount("sailor_b", "珊瑚の骸の水夫")
    m.bone("body", "root", (0, 14, 0))
    legs(m, "bone", 13, 3.0, foot_mat="bone", thick=2.4, foot=(2.2, 1.2, 3.2))
    m.add("pelvis", ell("body", "bone", (0, 14, 0), (4.2, 2.2, 3.0), segs=8, rings=2), core=True)
    m.add("spine", tub("body", "bone", [P(0, 14, 0.5), P(0, 21, 1)], [1.2, 1.2], None, sides=5))
    m.add("ribs", ell("body", "bone", (0, 21, -0.4), (5.0, 4.4, 4.0), segs=10, rings=3))
    m.add("sash", ell("body", "cloth", (0, 15.4, 0), (5.0, 1.4, 3.6), segs=10, rings=2))
    m.add("skull", ell("body", "bone", (0, 27, -0.8), (3.0, 3.4, 3.4), segs=9, rings=3))
    m.add("patch", ell("body", "cloth", (1.2, 27.6, -3.8), (1.2, 1.0, 0.5), segs=6, rings=1))
    eye(m, "body", (-1.2, 27.6, -3.8), 0.7, (0, 0, -1), (140, 255, 230), glow=True)
    for k, (x, y, z, h) in enumerate(((3, 23, 2, 5), (-3.4, 28, 1.6, 4), (4, 17, -1, 3), (-2, 21, 3.4, 4))):
        m.add(f"coral{k}", cone("body", "coral", P(x, y, z), (x * 0.15, 1, 0.3), h, 0.9, sides=5, rings=2, bend=(x * 0.2, 0, 0.6)))
    arm(m, "l", "bone", (5, 23, 0), (7, 18, -2), (6, 14, -4), r=1.6, fist=1.4)
    arm(m, "r", "bone", (-5, 23, 0), (-7.5, 20, -3), (-6, 19, -8), r=1.6, fist=1.4)
    m.add("cutlass", cone("body", "iron", P(-6, 19, -8), (0, 0.5, -1), 10.0, 1.6, sides=3, rings=3, bend=(0, 2.0, 0)))
    m.pal = bpal({"bone": M_(BONE, lit=0.34), "cloth": M_([(90, 30, 30), (130, 44, 40), (170, 64, 56), (200, 92, 80)]), "coral": M_(CORAL), "iron": M_(IRON)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["bone"], (80, 120, 110), density=0.08, size=0.8), glow)
    m.view = dict(center=(0, 17, -3), scale=4.6)
    m.caption = ["珊瑚の生えた骸の水夫", "片目の眼帯、赤い腰帯", "曲刀で斬りかかる"]
    return m


def sailor_c():
    """C 藻を巻いた空ろの水夫: 中身のない藻の衣の人形。顔は無く、光る魚を入れた角灯を下げる"""
    m = Mount("sailor_c", "藻を巻いた空ろの水夫")
    m.bone("body", "root", (0, 14, 0))
    m.add("robe", lof("body", "kelp", [rsec(0, 3, 0, 6.6, n=12), rsec(0, 12, 0, 5.4, n=12), rsec(0, 22, 0, 5.8, n=12), rsec(0, 27, 0, 4.2, n=12)]) if False else
          lof("body", "kelp", [ysec(1, 12, 10), ysec(12, 9.6, 8), ysec(22, 11, 8.4), ysec(27, 8, 7)]), core=True)
    m.add("hood", ell("body", "kelp", (0, 29.6, 0.4), (4.2, 4.4, 4.4), segs=10, rings=3))
    m.add("void", ell("body", "ink", (0, 29.2, -3.0), (2.8, 3.0, 1.6), segs=8, rings=2))
    for s in (1, -1):
        eye(m, "body", (s * 1.1, 29.6, -4.2), 0.5, (0, 0, -1), (120, 255, 230), glow=True)
        for k in range(3):
            m.add(f"strand{s}{k}", tub("body", "kelp", [P(s * (3 + k), 24 - 4 * k, -3), P(s * (4 + k), 18 - 4 * k, -4.4), P(s * (4 + k), 12 - 4 * k, -3)], [0.6, 0.5, 0.3], None, sides=4))
    arm(m, "l", "kelp", (5.4, 24, 0), (7, 19, -2), (6.4, 15, -5), r=2.0)
    arm(m, "r", "kelp", (-5.4, 24, 0), (-7, 19, -2), (-6.4, 15, -5), r=2.0)
    m.add("chain", tub("body", "iron", [P(6.4, 15, -5), P(6.4, 11, -5)], [0.3, 0.3], None, sides=4))
    m.add("lantern", box("body", "iron", (6.4, 9, -5), (3, 4, 3), taper=(0.8, 0.8), cut=0.3))
    m.add("fish", ell("body", "glow", (6.4, 9, -5), (1.0, 1.4, 1.0), segs=6, rings=2))
    m.pal = bpal({"kelp": M_([(30, 50, 30), (46, 72, 40), (66, 96, 52), (90, 120, 70)], streak=0.08), "ink": M_(INK, base=0.3),
                  "iron": M_(IRON), "glow": M_(GLOWSEA, base=0.7, lit=0.2)}, glow=["glow"])
    m.detail = lambda c, col, glow: (bands_y(c, col, ["kelp"], 2.4, 0.5, k=0.82, wobble=0.6), glow)
    m.view = dict(center=(0, 16, -2), scale=4.6)
    m.caption = ["中身のない藻の衣の人形", "顔の奥は暗く、二つの小さな光", "光る魚を入れた角灯を下げる"]
    return m


def octoling_a():
    """A 洞の小蛸: 人の膝ほどの蛸。八本の腕で歩き、墨を吐く"""
    m = Mount("octoling_a", "洞の小蛸")
    m.bone("body", "root", (0, 6, 0))
    m.add("mantle", ell("body", "skin", (0, 9.6, 1.4), (4.0, 4.6, 4.6), segs=10, rings=3, rx=20), core=True)
    m.add("head", ell("body", "skin", (0, 6.2, -1.0), (3.4, 2.8, 3.0), segs=10, rings=3))
    for s in (1, -1):
        eye(m, "body", (s * 2.4, 7.0, -2.8), 0.9, (s * 0.7, 0.2, -1), (250, 210, 90))
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        d = P(math.sin(a), 0, -math.cos(a))
        root = P(0, 4.6, -1.0) + d * 1.6
        m.add(f"arm{k}", tub("body", "skin", [root, root + d * 4 + P(0, -2.4, 0), root + d * 8 + P(0, -4.0, 0), root + d * 10.4 + P(0, -3.4, 0)],
                             [1.3, 1.0, 0.6, 0.2], None, sides=5))
    m.pal = bpal({"skin": M_([(110, 50, 60), (150, 74, 80), (190, 104, 104), (220, 140, 134)], lit=0.3)})
    m.detail = lambda c, col, glow: (spots(c, col, ["skin"], (90, 40, 50), scale=0.3, thr=0.7), glow)
    m.view = dict(center=(0, 5, 0), scale=5.4)
    m.caption = ["人の膝ほどの蛸", "八本の腕で這い、壁にも張りつく", "墨を吐いて目を眩ます"]
    return m


def octoling_b():
    """B 浮かぶ灯烏賊: 宙を泳ぐ小さな烏賊。胴の灯りが光り、触手で叩く"""
    m = Mount("octoling_b", "浮かぶ灯烏賊")
    m.bone("body", "root", (0, 14, 0))
    m.add("mantle", lof("body", "skin", [ysec(24, 1.0, 1.0), ysec(21, 5.4, 5.0), ysec(16, 6.4, 6.0), ysec(12, 5.0, 4.6)]), core=True)
    for s in (1, -1):
        m.add(f"fin{s}", slab("body", "skin", [P(s * 2.6, 23, 0), P(s * 7, 20, 0), P(s * 2.8, 17, 0)], (0, 0, 1), 0.8))
        eye(m, "body", (s * 2.6, 12.6, -1.6), 1.0, (s, 0, -0.4), (240, 240, 160), glow=True)
    m.add("lamp", ell("body", "glow", (0, 17, -2.6), (1.6, 2.6, 0.8), segs=7, rings=2))
    for k in range(6):
        a = math.radians(30 + 60 * k)
        d = P(math.sin(a), 0, -math.cos(a))
        root = P(0, 11.4, 0) + d * 2
        m.add(f"arm{k}", tub("body", "skin", [root, root + d * 1.6 + P(0, -4, 0), root + d * 2.4 + P(0, -8, 0)], [0.9, 0.6, 0.2], None, sides=4))
    for s in (1, -1):
        m.add(f"long{s}", tub("body", "skin", [P(s * 1.0, 11, -1), P(s * 2.0, 4, -4), P(s * 2.4, 0.6, -8)], [0.6, 0.5, 0.9], None, sides=5))
    m.pal = bpal({"skin": M_([(70, 80, 120), (96, 110, 156), (130, 146, 190), (176, 190, 220)], lit=0.3), "glow": M_(GLOWSEA, base=0.7, lit=0.2)}, glow=["glow"])
    m.detail = lambda c, col, glow: (spots(c, col, ["skin"], (60, 70, 120), scale=0.4, thr=0.74), glow)
    m.view = dict(center=(0, 12, 0), scale=5.0)
    m.caption = ["宙を泳ぐ小さな烏賊（人の腰の高さに浮く）", "胴の灯りが光る", "長い二本の触手で叩く"]
    return m


def octoling_c():
    """C 洞の水母: 傘の下に光る刺胞の糸を垂らして漂う。近づくと痺れる"""
    m = Mount("octoling_c", "洞の水母")
    m.bone("body", "root", (0, 16, 0))
    m.add("bell", lof("body", "jelly", [ysec(26, 1.0, 1.0), ysec(25, 6, 6), ysec(22, 10, 10), ysec(18.6, 11, 11), ysec(18, 9, 9)]), core=True)
    m.add("core", ell("body", "glow", (0, 21.4, 0), (2.4, 2.0, 2.4), segs=8, rings=2))
    for k in range(10):
        a = math.radians(36 * k)
        r = 4.4
        root = P(math.cos(a) * r, 18.4, math.sin(a) * r)
        m.add(f"thread{k}", tub("body", "glow", [root, root + P(math.cos(a) * 0.8, -6, math.sin(a) * 0.8), root + P(math.cos(a) * 0.2, -13 - (k % 3) * 2, math.sin(a) * 0.2)],
                                [0.35, 0.3, 0.15], None, sides=4))
    for k in range(4):
        a = math.radians(45 + 90 * k)
        m.add(f"frill{k}", tub("body", "jelly", [P(math.cos(a) * 1.4, 18.6, math.sin(a) * 1.4), P(math.cos(a) * 2.0, 12, math.sin(a) * 2.0), P(math.cos(a) * 1.2, 6, math.sin(a) * 1.2)],
                               [1.2, 1.0, 0.4], None, sides=5))
    m.pal = bpal({"jelly": M_([(80, 120, 160), (110, 156, 196), (150, 196, 226), (200, 230, 246)], base=0.6, lit=0.3), "glow": M_(GLOWSEA, base=0.7, lit=0.2)}, glow=["glow"])
    m.detail = lambda c, col, glow: (bands_y(c, col, ["jelly"], 2.0, 0.5, k=0.88), glow)
    m.view = dict(center=(0, 14, 0), scale=4.6)
    m.caption = ["傘の下に光る糸を垂らして漂う水母", "近づくと痺れる（糸に触れると傷）", "満ち潮で水の上を流れてくる"]
    return m


def bellcrab_a():
    """A 鐘負いの大寄居蟹: 沈んだ船の鐘を殻にした寄居蟹。大きな右の鋏"""
    m = Mount("bellcrab_a", "鐘負いの大寄居蟹")
    m.bone("body", "root", (0, 12, 0))
    m.add("bell", lof("body", "brass", [ysec(10, 30, 30), ysec(14, 26, 26), ysec(26, 20, 20), ysec(36, 16, 16), ysec(39, 10, 10)]) if False else
          lof("body", "brass", [rsec(14, 30, 8, 13, n=14, mid=18), rsec(4, 36, 8, 14, n=14, mid=20), rsec(-4, 34, 10, 12, n=14, mid=20)]), core=True)
    m.add("lip", lof("body", "brass", [rsec(16, 26, 6, 15, n=14, mid=16), rsec(14, 27, 6, 14.6, n=14, mid=16)]))
    m.add("rope", ell("body", "wood", (0, 35.6, 4), (4.6, 1.6, 4.6), segs=8, rings=2))
    m.add("front", ell("body", "shell", (0, 13, -8), (9.0, 6.0, 6.0), segs=12, rings=3))
    for s in (1, -1):
        eye(m, "body", (s * 3.0, 20, -11), 1.0, (s * 0.2, 0.3, -1), (40, 30, 20))
        m.add(f"stalk{s}", tub("body", "shell", [P(s * 2.6, 16, -10), P(s * 3.0, 19.4, -11)], [0.8, 0.7], None, sides=5))
        for k in range(3):
            z = -6 + 5 * k
            m.add(f"leg{s}{k}", tub("body", "shell", [P(s * 8, 12, z), P(s * 15, 15, z - 1), P(s * 18, 0.6, z - 2)], [1.8, 1.4, 0.6], None, sides=5))
    m.add("claw_r_arm", limb("body", "shell", [P(-7, 12, -10), P(-12, 14, -16), P(-10, 12, -22)], [3, 2.6, 2.4], [3, 2.6, 2.4], n=8))
    m.add("claw_r", lof("body", "shell", [rsec(-21, 16, 6, 4.6, n=12, cx=-10), rsec(-27, 17, 6, 5.6, n=12, cx=-10), rsec(-32, 14, 9, 3.0, n=12, cx=-10)]))
    m.add("claw_r_fing", cone("body", "shell", P(-10, 8, -26), (0, 0.2, -1), 7, 1.8, sides=5, rings=2, bend=(0, 2.0, 0)))
    m.add("claw_l_arm", limb("body", "shell", [P(7, 12, -10), P(10, 12, -15), P(9, 10, -18)], [2, 1.8, 1.6], [2, 1.8, 1.6], n=7))
    m.add("claw_l", ell("body", "shell", (9, 10, -20), (2.6, 2.2, 3.4), segs=8, rings=2))
    m.pal = bpal({"brass": M_([(60, 70, 50), (88, 104, 70), (120, 140, 92), (160, 176, 120)], lit=0.36, streak=0.06), "shell": M_(CORAL, lit=0.32), "wood": M_(WOOD)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["brass"], (80, 140, 120), density=0.10, size=1.0), glow)
    m.view = dict(center=(0, 16, -4), scale=2.8)
    m.caption = ["沈んだ船の鐘を殻にした大寄居蟹", "緑青の鐘を鳴らして波を呼ぶ", "大きな右の鋏で挟む"]
    return m


def bellcrab_b():
    """B 船骸負いの蟹: 沈んだ船の舳先を背負う蟹。片方の鋏に錨"""
    m = Mount("bellcrab_b", "船骸負いの蟹")
    m.bone("body", "root", (0, 12, 0))
    m.add("carapace", ell("body", "shell", (0, 14, 0), (13, 6.4, 11), segs=14, rings=3), core=True)
    m.add("hull", lof("body", "wood", [rsec(10, 34, 18, 9, n=12), rsec(0, 36, 17, 10, n=12), rsec(-10, 34, 18, 8, n=12), rsec(-18, 32, 22, 3, n=12)]))
    m.add("mast", tub("body", "wood", [P(0, 34, 2), P(1, 50, 4)], [1.2, 0.9], None, sides=5))
    m.add("sail", slab("body", "cloth", [P(1, 48, 4.2), P(9, 44, 4.6), P(8, 38, 5.0), P(1, 38, 4.4)], (0, 0, 1), 0.6))
    for s in (1, -1):
        eye(m, "body", (s * 3.4, 18, -11), 1.0, (s * 0.2, 0.3, -1), (40, 30, 20))
        for k in range(3):
            z = -5 + 5 * k
            m.add(f"leg{s}{k}", tub("body", "shell", [P(s * 11, 12, z), P(s * 17, 15, z), P(s * 20, 0.6, z - 1)], [1.8, 1.4, 0.6], None, sides=5))
    m.add("claw_arm", limb("body", "shell", [P(9, 12, -9), P(14, 12, -15), P(12, 10, -20)], [2.8, 2.4, 2.2], [2.8, 2.4, 2.2], n=8))
    m.add("claw", ell("body", "shell", (12, 10, -22), (3.6, 3.0, 4.4), segs=8, rings=2))
    m.add("anchor_arm", limb("body", "shell", [P(-9, 12, -9), P(-14, 13, -13), P(-14, 12, -16)], [2.8, 2.4, 2.2], [2.8, 2.4, 2.2], n=8))
    m.add("anchor", tub("body", "iron", [P(-14, 12, -16), P(-14, 4, -20)], [0.9, 0.9], None, sides=5))
    m.add("fluke", tub("body", "iron", [P(-19, 4, -20), P(-14, 1.6, -20), P(-9, 4, -20)], [0.9, 1.1, 0.9], None, sides=5))
    m.pal = bpal({"shell": M_(SHELL, lit=0.32), "wood": M_(WOOD, streak=0.06), "cloth": M_(CLOTH), "iron": M_(IRON)})
    m.detail = lambda c, col, glow: (plates(c, col, ["shell"], size=3.6, edge=0.05, k_edge=0.7), glow)
    m.view = dict(center=(0, 20, -2), scale=2.6)
    m.caption = ["沈んだ船の舳先を背負う蟹", "背に折れた帆柱と破れた帆", "片方の鋏に錨。振り回す"]
    return m


def bellcrab_c():
    """C 藤壺の大海老: 藤壺に覆われた長い海老。長い髭と大きな二本の鋏、尾で跳ねる"""
    m = Mount("bellcrab_c", "藤壺の大海老")
    m.bone("body", "root", (0, 10, 0))
    segs = [P(0, 12, -8), P(0, 12.6, -2), P(0, 12.4, 4), P(0, 11.4, 10), P(0, 9.6, 15), P(0, 7.6, 19.4)]
    for i, p in enumerate(segs):
        r = 7.0 - 0.7 * i
        m.add(f"seg{i}", ell("body", "shell", p, (r, r * 0.7, 3.8), segs=12, rings=3), core=i == 1)
        m.add(f"barn{i}", cone("body", "barn", p + P((-1) ** i * 2.0, r * 0.6, 0), (0, 1, 0), 1.6, 1.4, sides=6, rings=1))
    m.add("fan", slab("body", "shell", [P(-4, 6, 22), P(4, 6, 22), P(6, 4.6, 27), P(-6, 4.6, 27)], (0, 1, 0.2), 0.8))
    m.add("head", ell("body", "shell", (0, 12.6, -13), (4.6, 3.6, 4.6), segs=10, rings=3))
    for s in (1, -1):
        eye(m, "body", (s * 2.4, 15, -16), 0.8, (s * 0.4, 0.3, -1), (30, 20, 20))
        m.add(f"whisker{s}", tub("body", "shell", [P(s * 1.4, 13, -17), P(s * 6, 20, -26), P(s * 14, 18, -32)], [0.4, 0.3, 0.15], None, sides=4))
        m.add(f"arm{s}", limb("body", "shell", [P(s * 5, 10, -12), P(s * 9, 11, -18), P(s * 8, 10, -22)], [2.4, 2.2, 2.0], [2.4, 2.2, 2.0], n=8))
        m.add(f"claw{s}", lof("body", "shell", [rsec(-22, 12.6, 7.4, 3.0, n=10, cx=s * 8), rsec(-27, 13, 7, 3.6, n=10, cx=s * 8), rsec(-31, 11.4, 8.6, 1.6, n=10, cx=s * 8)]))
        for k in range(4):
            z = -6 + 4 * k
            m.add(f"leg{s}{k}", tub("body", "shell", [P(s * 5, 8, z), P(s * 10, 9, z), P(s * 12, 0.6, z - 1)], [1.0, 0.8, 0.4], None, sides=4))
    m.pal = bpal({"shell": M_([(120, 50, 40), (160, 74, 56), (196, 104, 76), (226, 140, 104)], lit=0.32), "barn": M_(BARN, lit=0.34)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["shell"], (200, 196, 180), density=0.10, size=0.9), glow)
    grow(m, 1.9)
    m.view = dict(center=(0, 19, -8), scale=2.0)
    m.caption = ["藤壺に覆われた長い大海老（人の背ほどの高さ）", "長い髭と大きな二本の鋏", "尾を打って跳ねて下がる"]
    return m


# ====================================================================== 雷晶の空洞 (Lv 22)

def beetle_a():
    """A 晶殻の甲虫: 背の殻が青い結晶の甲虫。殻で受け、角で突く"""
    m = Mount("beetle_a", "晶殻の甲虫")
    m.bone("body", "root", (0, 6, 0))
    m.add("shell", lof("body", "cry", [rsec(8, 10, 4, 6.0, n=12), rsec(2, 13, 4, 7.4, n=12), rsec(-4, 12, 4, 7.0, n=12), rsec(-7, 9, 5, 5.0, n=12)]), core=True)
    m.add("seam", tub("body", "chitin", [P(0, 13.2, 2), P(0, 12.4, -5)], [0.6, 0.6], None, sides=4))
    m.add("body", ell("body", "chitin", (0, 5.0, 0), (6.0, 2.8, 8.0), segs=10, rings=2))
    m.add("head", ell("body", "chitin", (0, 6.4, -8.6), (3.6, 2.8, 2.8), segs=9, rings=2))
    m.add("horn", cone("body", "cry", P(0, 7.4, -10.4), (0, 0.5, -1), 6.0, 1.4, sides=6, rings=2, bend=(0, 2.0, 0)))
    for s in (1, -1):
        eye(m, "body", (s * 2.2, 7.4, -10.6), 0.6, (s * 0.4, 0, -1), (200, 230, 255), glow=True)
        for k in range(3):
            z = -4 + 4 * k
            m.add(f"leg{s}{k}", tub("body", "chitin", [P(s * 5, 4.4, z), P(s * 8.6, 6.4, z - 0.5), P(s * 10, 0.4, z - 1)], [0.9, 0.7, 0.3], None, sides=4))
    m.pal = bpal({"cry": M_(CRY, base=0.6, lit=0.26), "chitin": M_(CHIT)}, glow=["cry"])
    m.detail = lambda c, col, glow: (plates(c, col, ["cry"], size=2.4, edge=0.06, k_edge=0.7), glow)
    m.view = dict(center=(0, 6, -2), scale=5.6)
    m.caption = ["背の殻が青い結晶の甲虫（膝の上ほど）", "殻で前の傷を受ける", "結晶の角で突く"]
    return m


def beetle_b():
    """B 雷角の兜虫: 二本の大きな結晶の角の兜虫。角の間に雷を溜めて放つ"""
    m = Mount("beetle_b", "雷角の兜虫")
    m.bone("body", "root", (0, 7, 0))
    m.add("elytra", lof("body", "chitin", [rsec(10, 10, 4, 6, n=12), rsec(3, 14, 4, 8, n=12), rsec(-4, 13, 4, 7.4, n=12)]), core=True)
    m.add("pronotum", ell("body", "chitin", (0, 11, -6.6), (6.6, 4.4, 4.0), segs=10, rings=3))
    m.add("head", ell("body", "chitin", (0, 7.4, -10), (3.4, 2.6, 2.6), segs=8, rings=2))
    m.add("horn_up", cone("body", "cry", P(0, 14, -7), (0, 0.6, -1), 12, 1.6, sides=6, rings=3, bend=(0, 4.0, 0)))
    m.add("horn_lo", cone("body", "cry", P(0, 7.6, -12), (0, 0.2, -1), 9, 1.4, sides=6, rings=3, bend=(0, 3.0, 0)))
    m.add("spark", ell("body", "glow", (0, 13, -19), (1.4, 1.4, 1.4), segs=7, rings=2))
    for s in (1, -1):
        eye(m, "body", (s * 2.2, 8.2, -12), 0.6, (s * 0.4, 0, -1), (255, 240, 120), glow=True)
        for k in range(3):
            z = -5 + 5 * k
            m.add(f"leg{s}{k}", tub("body", "chitin", [P(s * 5.6, 5, z), P(s * 9.4, 7, z), P(s * 11, 0.4, z - 1)], [1.0, 0.8, 0.3], None, sides=4))
    m.pal = bpal({"chitin": M_([(30, 30, 40), (46, 46, 62), (66, 66, 88), (92, 92, 118)], lit=0.34), "cry": M_(CRY, base=0.6, lit=0.26),
                  "glow": M_([(200, 200, 90), (240, 240, 140), (255, 255, 200), (255, 255, 240)], base=0.72, lit=0.2)}, glow=["cry", "glow"])
    m.detail = lambda c, col, glow: (edge_dark(c, col, ["chitin"], k=0.8), glow)
    m.view = dict(center=(0, 8, -4), scale=5.0)
    m.caption = ["二本の大きな結晶の角の兜虫", "角の間に雷を溜めて放つ", "黒い殻、黄色に光る目"]
    return m


def beetle_c():
    """C 晶背の岩亀虫: 平たい岩の亀のような虫。背に結晶の柱が並ぶ。遅いが硬い"""
    m = Mount("beetle_c", "晶背の岩亀虫")
    m.bone("body", "root", (0, 5, 0))
    m.add("shell", ell("body", "stone", (0, 7, 0), (8.6, 4.2, 10), segs=14, rings=3), core=True)
    for k in range(7):
        a = k * 2.399
        r = 2 + 1.4 * (k % 3)
        m.add(f"pillar{k}", cone("body", "cry", P(math.cos(a) * r, 9.6, math.sin(a) * r * 1.2), (math.cos(a) * 0.2, 1, math.sin(a) * 0.2), 5 + (k % 3) * 2, 1.4, sides=6, rings=1))
    m.add("head", ell("body", "stone", (0, 5.4, -11), (3.2, 2.4, 3.0), segs=8, rings=2))
    for s in (1, -1):
        eye(m, "body", (s * 1.8, 6.2, -13.4), 0.6, (s * 0.3, 0, -1), (170, 220, 255), glow=True)
        for k in range(2):
            z = -6 + 12 * k
            m.add(f"leg{s}{k}", limb("body", "stone", [P(s * 7, 5, z), P(s * 9.4, 3, z), P(s * 9.6, 0.8, z)], [2.4, 2.2, 2.0], [2.4, 2.2, 2.0], n=7))
    m.pal = bpal({"stone": M_(STORM, lit=0.32, streak=0.04), "cry": M_(CRY, base=0.6, lit=0.26)}, glow=["cry"])
    m.detail = lambda c, col, glow: (plates(c, col, ["stone"], size=3.0, edge=0.05, k_edge=0.65), glow)
    m.view = dict(center=(0, 6, -2), scale=5.0)
    m.caption = ["平たい岩の亀のような虫", "背に結晶の柱が並ぶ（光る）", "遅いが硬い。結晶が割れると弱る"]
    return m


def bat_a():
    """A 晶翼の蝙蝠: 翼の膜に結晶が生えた蝙蝠。群れで飛び、雷の粉を落とす"""
    m = Mount("bat_a", "晶翼の蝙蝠")
    m.bone("body", "root", (0, 16, 0))
    m.add("torso", ell("body", "fur", (0, 16, 0), (2.6, 2.8, 3.4), segs=9, rings=3), core=True)
    m.add("head", ell("body", "fur", (0, 18.6, -2.4), (2.0, 2.0, 2.0), segs=8, rings=2))
    for s in (1, -1):
        eye(m, "body", (s * 0.8, 19.0, -4.2), 0.4, (s * 0.3, 0, -1), (170, 230, 255), glow=True)
        m.add(f"ear{s}", cone("body", "fur", P(s * 1.0, 20.2, -2.0), (s * 0.4, 1, 0), 2.6, 0.7, sides=4, rings=1))
        m.add(f"wing{s}", wing("body", "memb", [(s * 2.0, -2.0, 2.0, 16.6, 0.5), (s * 7, -3.0, 4.0, 18.2, 0.4), (s * 12, -2.0, 1.0, 17.0, 0.3), (s * 15, -0.6, 0.0, 15.4, 0.2)]))
        for k in range(3):
            m.add(f"cry{s}{k}", cone("body", "cry", P(s * (5 + 3.4 * k), 17.6 + 0.2 * k, -1.2), (s * 0.3, 1, 0.2), 2.6 - 0.4 * k, 0.6, sides=5, rings=1))
    m.pal = bpal({"fur": M_(FUR), "memb": M_([(40, 40, 60), (60, 60, 86), (84, 84, 112), (110, 110, 140)], lit=0.3), "cry": M_(CRY, base=0.62, lit=0.24)}, glow=["cry"])
    m.detail = lambda c, col, glow: (fur(c, col, ["fur"], amt=0.08), glow)
    m.view = dict(center=(0, 16, 0), scale=6.0)
    m.caption = ["翼の膜に結晶が生えた蝙蝠", "群れで飛び、上から雷の粉を落とす", "青く光る目"]
    return m


def bat_b():
    """B 晶の目玉: 結晶の目玉が小さな翼で浮く。見つめた所に雷を落とす (予告の円)"""
    m = Mount("bat_b", "晶の目玉")
    m.bone("body", "root", (0, 18, 0))
    m.add("orb", ell("body", "stone", (0, 18, 0), (4.2, 4.2, 4.2), segs=12, rings=4), core=True)
    m.add("lens", ell("body", "cry", (0, 18, -3.6), (2.4, 2.4, 1.2), segs=10, rings=2))
    m.eyes.append(dict(c=P(0, 18, -4.6), r=1.2, dir=(0, 0, -1), a=1.0, b=1.0, bone="body", iris=(140, 220, 255), lid=(10, 10, 12), pa=0.5, pb=0.5, glow=True))
    for k in range(5):
        a = math.radians(-60 + 30 * k)
        m.add(f"shard{k}", cone("body", "cry", P(math.sin(a) * 3.4, 18 + math.cos(a) * 3.4, 1.0), (math.sin(a), math.cos(a), 0.3), 3.4, 0.8, sides=5, rings=1))
    for s in (1, -1):
        m.add(f"wing{s}", wing("body", "memb", [(s * 3.6, -1.0, 2.0, 18.4, 0.4), (s * 7, -1.4, 3.0, 19.6, 0.3), (s * 10, -0.4, 1.4, 19.0, 0.2)]))
    m.add("tail", tub("body", "stone", [P(0, 15, 3), P(0, 11, 5), P(0, 8, 4)], [0.8, 0.5, 0.2], None, sides=4))
    m.pal = bpal({"stone": M_(STORM, lit=0.32), "cry": M_(CRY, base=0.62, lit=0.24), "memb": M_([(40, 40, 60), (60, 60, 86), (84, 84, 112), (110, 110, 140)])}, glow=["cry"])
    m.detail = lambda c, col, glow: (plates(c, col, ["stone"], size=2.0, edge=0.06, k_edge=0.7), glow)
    m.view = dict(center=(0, 16, 0), scale=6.0)
    m.caption = ["結晶の目玉が小さな翼で浮く", "見つめた所に雷を落とす（予告の円）", "まわりに結晶のかけら"]
    return m


def bat_c():
    """C 雷の蛾: 翅に結晶の鱗粉が光る大きな蛾。羽ばたくと痺れの粉が舞う"""
    m = Mount("bat_c", "雷の蛾")
    m.bone("body", "root", (0, 16, 0))
    m.add("thorax", ell("body", "fur", (0, 16, -1), (2.2, 2.4, 2.6), segs=9, rings=3), core=True)
    m.add("abdomen", ell("body", "fur", (0, 15, 3.6), (1.8, 1.8, 4.0), segs=9, rings=3))
    m.add("head", ell("body", "fur", (0, 16.8, -4.0), (1.6, 1.6, 1.4), segs=8, rings=2))
    for s in (1, -1):
        eye(m, "body", (s * 1.0, 17.0, -5.0), 0.6, (s * 0.6, 0, -1), (60, 60, 80))
        m.add(f"ant{s}", tub("body", "fur", [P(s * 0.6, 18, -5), P(s * 3, 22, -8), P(s * 5, 23, -8)], [0.25, 0.2, 0.15], None, sides=4))
        m.add(f"fw{s}", wing("body", "wingp", [(s * 2.0, -3.4, 0.6, 16.6, 0.4), (s * 7, -6.0, 1.4, 17.6, 0.3), (s * 12, -5.0, 0.0, 17.0, 0.25), (s * 14, -3.0, -1.4, 16.4, 0.2)]))
        m.add(f"hw{s}", wing("body", "wingp", [(s * 2.0, 0.8, 4.0, 15.6, 0.4), (s * 6, 1.4, 7.0, 14.8, 0.3), (s * 9, 2.0, 6.0, 14.4, 0.2)]))
    m.pal = bpal({"fur": M_([(70, 70, 86), (96, 96, 116), (126, 126, 148), (160, 160, 180)]), "wingp": M_(CRY, base=0.56, lit=0.28)}, glow=["wingp"])
    m.detail = lambda c, col, glow: (spots(c, col, ["wingp"], (250, 240, 140), scale=0.5, thr=0.78, a=0.8), glow)
    m.view = dict(center=(0, 16, 0), scale=5.4)
    m.caption = ["翅に結晶の鱗粉が光る大きな蛾", "羽ばたくと痺れの粉が舞う（範囲）", "ゆらゆら飛ぶ"]
    return m


def golem_a():
    """A 晶洞の傀儡: 割れた晶洞 (ジオード) の半分ずつを胴と頭にした岩の巨人。内側の結晶が光る"""
    m = Mount("golem_a", "晶洞の傀儡")
    m.bone("body", "root", (0, 20, 0))
    legs(m, "stone", 18, 6.4, foot_mat="stone", thick=7, foot=(5.0, 2.8, 6.4))
    m.add("torso", ell("body", "stone", (0, 33, 1.0), (13, 12, 10), segs=14, rings=4), core=True)
    m.add("hollow", ell("body", "cry", (0, 33, -7.4), (8.6, 8.0, 3.2), segs=12, rings=3))
    for k in range(8):
        a = math.radians(45 * k)
        m.add(f"inner{k}", cone("body", "cry", P(math.cos(a) * 6.0, 33 + math.sin(a) * 5.6, -6.0), (-math.cos(a) * 0.7, -math.sin(a) * 0.7, -0.6), 4, 1.4, sides=6, rings=1))
    m.add("head", ell("body", "stone", (0, 48, -1.0), (6, 5, 5.4), segs=10, rings=3))
    m.add("visor", ell("body", "cry", (0, 48, -5.6), (4.0, 1.4, 1.2), segs=8, rings=1))
    arm(m, "l", "stone", (14, 40, 0), (19, 30, -2), (18, 21, -5), r=5.6, fist=5.0)
    arm(m, "r", "stone", (-14, 40, 0), (-19, 30, -2), (-18, 21, -5), r=5.6, fist=5.0)
    m.pal = bpal({"stone": M_(STORM, lit=0.32, streak=0.04), "cry": M_(CRY, base=0.64, lit=0.24)}, glow=["cry"])
    m.detail = lambda c, col, glow: (plates(c, col, ["stone"], size=4.4, edge=0.05, k_edge=0.6), glow)
    m.view = dict(center=(0, 28, -2), scale=3.0)
    m.caption = ["割れた晶洞を胴にした岩の巨人（人の倍ほど）", "胸の内側の結晶が光る（弱点）", "大きな岩の拳で叩く"]
    return m


def golem_b():
    """B 晶の大蛇: 岩の節に結晶が生えた大蛇。とぐろを巻き、鎌首から雷を吐く"""
    m = Mount("golem_b", "晶の大蛇")
    m.bone("body", "root", (0, 6, 0))
    coil = []
    for i in range(14):
        a = i * 0.48
        r = 13 - i * 0.62
        p = P(math.cos(a) * r, 4.4 + i * 0.22, math.sin(a) * r)
        coil.append(p)
        rr = 4.8 - i * 0.1
        m.add(f"seg{i}", ell("body", "stone", p, (rr, rr * 0.9, rr), segs=10, rings=3), core=i == 4)
        if i % 2 == 0:
            m.add(f"cry{i}", cone("body", "cry", p + P(0, rr * 0.8, 0), (0.2, 1, 0.2), 3.4, 1.0, sides=5, rings=1))
    neck = [coil[-1], coil[-1] + P(0, 10, -2), P(0, 30, -4), P(0, 38, -6), P(0, 40, -8)]
    m.add("neck", limb("body", "stone", neck, [8.0, 7.4, 6.6, 6.0, 5.6], [8.0, 7.4, 6.6, 6.0, 5.6], n=10))
    for k, p in enumerate(neck[1:4]):
        m.add(f"ncry{k}", cone("body", "cry", p + P(0, 0.6, 3.0), (0, 0.4, 1), 3.0, 0.9, sides=5, rings=1))
    head = P(0, 40, -10)
    m.add("head", lof("body", "stone", [rsec(-6, 43, 37, 4.4, n=12), rsec(-11, 43.6, 37.6, 5.0, n=12), rsec(-17, 41.6, 38.4, 2.8, n=12)]))
    for s in (1, -1):
        eye(m, "body", (s * 3.2, 42.6, -13), 0.9, (s, 0.2, -0.4), (200, 230, 255), glow=True)
        m.add(f"crest{s}", cone("body", "cry", P(s * 2.0, 43.6, -9), (s * 0.4, 1, 0.6), 6, 1.2, sides=5, rings=2))
    m.pal = bpal({"stone": M_(STORM, lit=0.32), "cry": M_(CRY, base=0.64, lit=0.24)}, glow=["cry"])
    m.detail = lambda c, col, glow: (scales(c, col, ["stone"], size=1.8), glow)
    m.view = dict(center=(0, 20, -2), scale=2.8)
    m.caption = ["岩の節に結晶の生えた大蛇", "とぐろを巻き、鎌首を持ち上げる", "口から雷を吐く（前の帯の予告）"]
    return m


def golem_c():
    """C 三つ晶の芯: 光る芯のまわりを三つの大きな結晶の板が回る浮かぶもの。板で薙ぎ、芯が雷を放つ"""
    m = Mount("golem_c", "三つ晶の芯")
    m.bone("body", "root", (0, 26, 0))
    m.add("core", ell("body", "glow", (0, 28, 0), (4.6, 4.6, 4.6), segs=12, rings=4), core=True)
    m.add("cage", ell("body", "stone", (0, 28, 0), (6.4, 1.6, 6.4), segs=12, rings=2))
    for k in range(3):
        a = math.radians(120 * k)
        c = P(math.cos(a) * 13, 28 + (k - 1) * 3, math.sin(a) * 13)
        d = P(math.cos(a), 0, math.sin(a))
        m.add(f"slab{k}", lof("body", "cry", [[c + P(0, -9, 0) + d * -1.6, c + P(0, -9, 0) + d * 1.6, c + P(0, 9, 0) + d * 1.0, c + P(0, 9, 0) + d * -1.0][:0] or
                                               ysec(c[1] - 9, 3.0, 5.0, zc=c[2], xc=c[0]), ysec(c[1], 5.0, 7.0, zc=c[2], xc=c[0]), ysec(c[1] + 9, 1.0, 1.0, zc=c[2], xc=c[0])]))
    m.add("tail", cone("body", "stone", P(0, 23, 0), (0, -1, 0), 10, 2.4, sides=6, rings=2))
    m.eyes.append(dict(c=P(0, 28, -4.6), r=1.4, dir=(0, 0, -1), a=1.0, b=1.0, bone="body", iris=(255, 250, 200), lid=(10, 10, 12), pa=0.5, pb=0.5, glow=True))
    m.pal = bpal({"stone": M_(STORM, lit=0.32), "cry": M_(CRY, base=0.64, lit=0.24),
                  "glow": M_([(200, 220, 255), (230, 240, 255), (250, 252, 255), (255, 255, 255)], base=0.8, lit=0.2)}, glow=["cry", "glow"])
    m.detail = lambda c, col, glow: (glow and col, glow) if False else (col, glow)
    m.view = dict(center=(0, 24, 0), scale=2.8)
    m.caption = ["光る芯のまわりを三つの結晶の板が回る", "板で薙ぎ、芯が雷を放つ", "板を割ると芯がむき出しになる"]
    return m


# ====================================================================== 熔けた坑道 (Lv 28)

def miner_a():
    """A 灰の坑夫: 灰をかぶった坑夫。顔に鉄の覆い、赤く焼けた鶴嘴、背に熾火の壺"""
    m = Mount("miner_a", "灰の坑夫")
    m.bone("body", "root", (0, 14, 0))
    legs(m, "ash", 13, 3.4, foot_mat="iron", thick=3.6)
    m.add("torso", lof("body", "ash", [rsec(-3.6, 24, 13, 5.6, n=12), rsec(0, 25.6, 12, 6.4, n=12), rsec(3.6, 24, 13, 5.6, n=12)]), core=True)
    m.add("apron", box("body", "leath", (0, 17, -4.4), (9, 9, 1.0), taper=(0.9, 1.0), cut=0.2))
    m.add("head", ell("body", "ash", (0, 29, -0.4), (3.2, 3.4, 3.2), segs=9, rings=3))
    m.add("mask", box("body", "iron", (0, 29, -3.0), (5.2, 4.6, 1.4), taper=(0.9, 0.9), cut=0.3))
    for s in (1, -1):
        eye(m, "body", (s * 1.2, 29.6, -3.8), 0.5, (0, 0, -1), (255, 140, 40), glow=True)
    m.add("pot", ell("body", "iron", (0, 23, 5.2), (3.2, 3.6, 2.6), segs=9, rings=2))
    m.add("ember", ell("body", "magma", (0, 26.2, 5.2), (2.0, 0.8, 1.6), segs=8, rings=1))
    arm(m, "l", "ash", (6.2, 24, 0), (8, 18, -2), (6, 14, -5), r=2.6, fist=1.8, fist_mat="leath")
    arm(m, "r", "ash", (-6.2, 24, 0), (-8, 27, -2), (-5, 31, -2), r=2.6, fist=1.8, fist_mat="leath")
    shaft(m, "haft", "wood", (-5, 31, -2), (-4, 32, 9), r=0.8)
    m.add("pick", cone("body", "magma", P(-4, 32, 9), (0, -0.4, 1), 6.0, 1.0, sides=4, rings=2, bend=(0, -2.0, 0)))
    m.add("pick2", cone("body", "magma", P(-4, 32, 9), (0, 0.6, -0.2), 4.0, 1.0, sides=4, rings=2, bend=(0, 0, -1.4)))
    m.pal = bpal({"ash": M_(ASH, streak=0.05), "leath": M_(LEATHR), "iron": M_(IRON), "wood": M_(WOOD), "magma": M_(MAG, base=0.68, lit=0.2)}, glow=["magma"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["ash"], (60, 56, 54), density=0.10, size=0.8), glow)
    m.view = dict(center=(0, 17, 0), scale=4.8)
    m.caption = ["灰をかぶった坑夫（人ほど）", "顔に鉄の覆い、赤く焼けた鶴嘴", "背の熾火の壺から火の粉を投げる"]
    return m


def miner_b():
    """B 鞴の小鬼: 背に鞴を負った背の低い小鬼。仮面と長い火掻き棒、鞴で火を吹きつける"""
    m = Mount("miner_b", "鞴の小鬼")
    m.bone("body", "root", (0, 9, 0))
    legs(m, "skin", 8.6, 2.8, foot_mat="iron", thick=2.8, foot=(2.4, 1.4, 3.4))
    m.add("torso", ell("body", "skin", (0, 13, 0), (5.0, 5.4, 4.2), segs=10, rings=3), core=True)
    m.add("head", ell("body", "skin", (0, 20, -1.4), (3.6, 3.4, 3.4), segs=9, rings=3))
    m.add("mask", ell("body", "iron", (0, 20, -4.0), (3.4, 3.2, 1.2), segs=9, rings=2))
    for s in (1, -1):
        eye(m, "body", (s * 1.3, 20.6, -5.0), 0.55, (0, 0, -1), (255, 160, 40), glow=True)
        m.add(f"ear{s}", cone("body", "skin", P(s * 3.2, 21, -0.6), (s, 0.4, 0.3), 4.0, 1.0, sides=4, rings=1))
    m.add("bellows", lof("body", "leath", [rsec(3.4, 17, 9, 4.6, n=10), rsec(6.6, 16, 10, 3.6, n=10), rsec(8.4, 15, 11, 1.6, n=10)]))
    m.add("nozzle", tub("body", "iron", [P(0, 17, 4), P(4, 22, -2), P(6, 20, -8)], [0.7, 0.6, 0.5], None, sides=5))
    arm(m, "l", "skin", (4.6, 15, 0), (6.4, 12, -2), (6, 9, -4), r=1.8, fist=1.4)
    arm(m, "r", "skin", (-4.6, 15, 0), (-6.4, 13, -3), (-5, 14, -7), r=1.8, fist=1.4)
    shaft(m, "poker", "iron", (-5, 14, -2), (-5, 10, -20), r=0.5)
    m.add("hook", cone("body", "magma", P(-5, 10, -20), (0, -0.6, -0.6), 2.4, 0.6, sides=4, rings=1))
    m.pal = bpal({"skin": M_([(70, 50, 44), (98, 70, 60), (126, 92, 78), (152, 116, 98)]), "iron": M_(IRON), "leath": M_(LEATHR, streak=0.06), "magma": M_(MAG, base=0.68, lit=0.2)},
                 glow=["magma"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["skin"], (50, 40, 36), density=0.08, size=0.8), glow)
    m.view = dict(center=(0, 12, -2), scale=5.4)
    m.caption = ["背に鞴を負った背の低い小鬼（人の胸ほど）", "鉄の仮面と長い火掻き棒", "鞴で前へ火を吹きつける"]
    return m


def miner_c():
    """C 鉱滓の小人形: 冷えた鉱滓の塊が人の形をとった小さな人形。割れ目から熔けた光、倒れると熔岩溜まり"""
    m = Mount("miner_c", "鉱滓の小人形")
    m.bone("body", "root", (0, 10, 0))
    for s in (1, -1):
        m.add(f"leg{s}", lof("body", "slag", [ysec(0.5, 4.6, 5.0, xc=s * 3.0), ysec(5, 4.0, 4.0, xc=s * 3.0), ysec(10, 4.4, 4.2, xc=s * 2.6)]))
    m.add("torso", lof("body", "slag", [ysec(9, 9, 6.4), ysec(16, 11, 7.0), ysec(21, 9, 6.0)]), core=True)
    m.add("crack", ell("body", "magma", (0, 15, -3.4), (2.4, 4.0, 0.8), segs=7, rings=2))
    m.add("head", lof("body", "slag", [ysec(20.5, 5.4, 5.0), ysec(25, 5.6, 5.2), ysec(27, 3.4, 3.4)]))
    for s in (1, -1):
        eye(m, "body", (s * 1.2, 23.6, -2.7), 0.6, (0, 0, -1), (255, 170, 60), glow=True)
        m.add(f"arm{s}", lof("body", "slag", [ysec(19, 3.6, 3.6, xc=s * 6.4), ysec(13, 3.0, 3.0, xc=s * 7.6), ysec(8.6, 3.8, 3.6, xc=s * 7.4)]))
    m.pal = bpal({"slag": M_([(30, 26, 26), (48, 42, 40), (66, 58, 54), (88, 78, 72)], lit=0.32), "magma": M_(MAG, base=0.68, lit=0.2)}, glow=["magma"])
    m.detail = lambda c, col, glow: (plates(c, col, ["slag"], size=2.4, edge=0.08, k_edge=0.6), glow)
    m.view = dict(center=(0, 13, 0), scale=5.2)
    m.caption = ["冷えた鉱滓の塊の小さな人形（人の胸ほど）", "割れ目から熔けた光", "倒れると熔岩溜まりが残る"]
    return m


def slug_a():
    """A 熔岩の蛞蝓: 黒い皮の大きな蛞蝓。背の割れ目が光り、通った跡に火"""
    m = Mount("slug_a", "熔岩の蛞蝓")
    m.bone("body", "root", (0, 4, 0))
    m.add("foot", lof("body", "skin", [rsec(10, 3, 0, 3.0, n=12, mid=1.2), rsec(4, 7, 0, 5.4, n=12, mid=2.4), rsec(-3, 9, 0, 6.0, n=12, mid=3.0),
                                       rsec(-9, 8, 0, 5.0, n=12, mid=3.0), rsec(-12.4, 6, 0.6, 3.0, n=12, mid=3.0)]), core=True)
    for k in range(4):
        m.add(f"fissure{k}", ell("body", "magma", (0, 8.6 - abs(k - 1.5) * 0.4, -6 + 4 * k), (1.4, 0.6, 1.6), segs=7, rings=1))
    for s in (1, -1):
        m.add(f"stalk{s}", tub("body", "skin", [P(s * 1.6, 6, -11), P(s * 2.6, 11, -14)], [0.7, 0.5], None, sides=5))
        eye(m, "body", (s * 2.6, 11.4, -14.2), 0.6, (s * 0.3, 0.3, -1), (255, 180, 70), glow=True)
    m.pal = bpal({"skin": M_([(26, 22, 22), (40, 34, 32), (56, 48, 44), (74, 64, 58)], lit=0.34), "magma": M_(MAG, base=0.68, lit=0.2)}, glow=["magma"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["skin"], (120, 50, 20), density=0.05, size=0.6), glow)
    m.view = dict(center=(0, 4, -2), scale=5.0)
    m.caption = ["黒い皮の大きな蛞蝓（膝ほど）", "背の割れ目が熔けて光る", "通った跡にしばらく火が残る"]
    return m


def slug_b():
    """B 炉殻の蝸牛: 小さな炉そのものを殻にした蝸牛。殻の口から火の玉を撃つ"""
    m = Mount("slug_b", "炉殻の蝸牛")
    m.bone("body", "root", (0, 4, 0))
    m.add("foot", lof("body", "skin", [rsec(9, 3, 0, 2.6, n=12, mid=1.2), rsec(2, 5, 0, 4.4, n=12, mid=2.2), rsec(-6, 5.4, 0, 4.0, n=12, mid=2.4), rsec(-11, 5, 0.6, 2.6, n=12, mid=2.6)]), core=True)
    m.add("kiln", lof("body", "brick", [ysec(4, 10, 10, zc=2), ysec(12, 9, 9, zc=2), ysec(16, 6, 6, zc=2)]))
    m.add("mouth", ell("body", "magma", (0, 10, -3.0), (2.4, 2.0, 0.8), segs=8, rings=1))
    m.add("flue", tub("body", "brick", [P(1, 16, 3), P(1.4, 21, 4)], [1.4, 1.1], None, sides=6))
    for s in (1, -1):
        m.add(f"stalk{s}", tub("body", "skin", [P(s * 1.4, 4.4, -10), P(s * 2.0, 8, -12)], [0.6, 0.45], None, sides=5))
        eye(m, "body", (s * 2.0, 8.4, -12.2), 0.5, (s * 0.3, 0.3, -1), (255, 190, 80), glow=True)
    m.pal = bpal({"skin": M_(ASH), "brick": M_(BRICK, streak=0.04), "magma": M_(MAG, base=0.68, lit=0.2)}, glow=["magma"])
    m.detail = lambda c, col, glow: (plates(c, col, ["brick"], size=1.6, edge=0.08, k_edge=0.6), glow)
    m.view = dict(center=(0, 7, -2), scale=5.0)
    m.caption = ["小さな炉そのものを殻にした蝸牛", "殻の口から火の玉を撃つ", "煙突から煙"]
    return m


def slug_c():
    """C 熾火の山椒魚: 黒い肌に熾火の斑の山椒魚。熔岩の中を泳ぎ、跳び出して噛む"""
    m = Mount("slug_c", "熾火の山椒魚")
    m.bone("body", "root", (0, 4, 0))
    m.add("body", lof("body", "skin", [rsec(-9, 6, 2, 2.6, n=12), rsec(-5, 7.4, 1.6, 4.0, n=12), rsec(1, 7, 1.6, 4.4, n=12), rsec(6, 6, 2, 3.0, n=12)]), core=True)
    m.add("head", lof("body", "skin", [rsec(-8, 6.4, 2, 3.2, n=12), rsec(-12, 6.6, 2.4, 4.0, n=12), rsec(-15, 5.4, 3, 2.6, n=12)]))
    m.add("tail", lof("body", "skin", [rsec(5, 6, 2.4, 2.6, n=10), rsec(12, 5.4, 3, 1.4, n=10), rsec(18, 4.6, 3.6, 0.4, n=10)]))
    for s in (1, -1):
        eye(m, "body", (s * 2.6, 6.8, -12.4), 0.6, (s, 0.4, -0.4), (255, 200, 80), glow=True)
        for k, z in enumerate((-6, 4)):
            m.add(f"leg{s}{k}", tub("body", "skin", [P(s * 3.4, 3.6, z), P(s * 6.0, 2.6, z - 1), P(s * 6.6, 0.4, z - 1.6)], [1.1, 0.9, 0.7], None, sides=5))
    for k in range(6):
        m.add(f"spot{k}", ell("body", "magma", ((-1) ** k * 2.0, 6.8 - 0.1 * k, -6 + 2.4 * k), (1.0, 0.4, 1.0), segs=6, rings=1))
    m.pal = bpal({"skin": M_([(26, 22, 22), (40, 34, 32), (56, 48, 44), (74, 64, 58)], lit=0.34), "magma": M_(MAG, base=0.68, lit=0.2)}, glow=["magma"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["skin"], (100, 40, 20), density=0.05, size=0.6), glow)
    m.view = dict(center=(0, 4, -2), scale=5.0)
    m.caption = ["黒い肌に熾火の斑の山椒魚", "熔岩の中を泳ぐ", "熔岩から跳び出して噛む"]
    return m


def warden_a():
    """A 鎖の炉番: 背に大きな鞴と炉を負った仮面の番人。両手に鎖の先の焼けた鉄球"""
    m = Mount("warden_a", "鎖の炉番")
    m.bone("body", "root", (0, 20, 0))
    legs(m, "leath", 19, 6.0, foot_mat="iron", thick=6.0, foot=(4.6, 2.6, 6.0))
    m.add("torso", lof("body", "ash", [rsec(-6, 38, 18, 10, n=14), rsec(0, 41, 17, 11.4, n=14), rsec(6, 38, 18, 10, n=14)]), core=True)
    m.add("apron", box("body", "leath", (0, 24, -6.4), (14, 14, 1.4), taper=(1.0, 1.0), cut=0.2))
    m.add("head", ell("body", "ash", (0, 46, -2), (5, 5, 5), segs=10, rings=3))
    m.add("mask", lof("body", "iron", [rsec(-5.4, 50, 41, 5.0, n=12), rsec(-7.2, 49, 42, 4.2, n=12)]))
    for s in (1, -1):
        eye(m, "body", (s * 2.0, 46.4, -7.4), 0.8, (0, 0, -1), (255, 140, 40), glow=True)
    m.add("forge", box("body", "brick", (0, 40, 10), (14, 14, 8), taper=(0.9, 0.9), cut=0.25))
    m.add("forge_mouth", ell("body", "magma", (0, 40, 14.2), (4, 3, 0.8), segs=8, rings=1))
    m.add("stack", tub("body", "brick", [P(4, 46, 11), P(5, 58, 12)], [2.0, 1.6], None, sides=6))
    for s in (1, -1):
        wr = arm(m, s, "ash", (s * 12, 38, 0), (s * 15, 28, -3), (s * 14, 21, -7), r=4.0, fist=3.0, fist_mat="leath")
        m.add(f"chain{s}", tub("body", "iron", [wr + P(0, -2, 0), wr + P(s * 2, -8, -4), wr + P(s * 3, -14, -8)], [0.6, 0.6, 0.6], None, sides=4))
        m.add(f"ball{s}", ell("body", "magma", wr + P(s * 3, -16.4, -9), (3.2, 3.2, 3.2), segs=9, rings=3))
    m.pal = bpal({"ash": M_(ASH, streak=0.05), "leath": M_(LEATHR), "iron": M_(IRON), "brick": M_(BRICK), "magma": M_(MAG, base=0.68, lit=0.2)}, glow=["magma"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["ash"], (60, 56, 54), density=0.08, size=0.9), glow)
    m.view = dict(center=(0, 28, 0), scale=3.0)
    m.caption = ["背に炉を負った仮面の番人（人の倍ほど）", "両手の鎖の先に焼けた鉄球", "振り回して薙ぎ、叩きつける"]
    return m


def warden_b():
    """B 牛頭の鍛冶番: 牛の頭の大男。片手に大きな火ばさみ、片手に焼けた鉄の板の盾"""
    m = Mount("warden_b", "牛頭の鍛冶番")
    m.bone("body", "root", (0, 20, 0))
    legs(m, "fur", 19, 6.0, foot_mat="hoof", thick=6.4, foot=(4.0, 3.0, 5.0))
    m.add("torso", lof("body", "skin", [rsec(-6, 38, 18, 10.6, n=14), rsec(0, 42, 17, 12, n=14), rsec(6, 38, 18, 10.6, n=14)]), core=True)
    m.add("kilt", lof("body", "leath", [ysec(22, 22, 14), ysec(14, 24, 15)]))
    m.add("head", lof("body", "fur", [rsec(2, 50, 41, 6, n=12), rsec(-4, 51, 40, 6.6, n=12), rsec(-9, 47, 41, 4.6, n=12), rsec(-12, 45, 42, 3.4, n=12)]))
    m.add("ring", tub("body", "brass", [P(-1.6, 43, -12.4), P(1.6, 43, -12.4)], [0.5, 0.5], None, sides=5))
    for s in (1, -1):
        eye(m, "body", (s * 3.6, 48.4, -6.4), 0.8, (s * 0.6, 0, -1), (255, 120, 40), glow=True)
        m.add(f"horn{s}", cone("body", "bone", P(s * 5, 50, -2), (s, 0.5, -0.2), 9, 1.8, sides=6, rings=3, bend=(0, 4.0, -1.0)))
    arm(m, "l", "skin", (13, 38, 0), (17, 29, -3), (15, 22, -8), r=4.4, fist=3.2)
    m.add("shield", box("body", "magma", (18, 24, -10), (2, 16, 12), taper=(1.0, 1.0), cut=0.15))
    arm(m, "r", "skin", (-13, 38, 0), (-17, 30, -4), (-15, 26, -12), r=4.4, fist=3.2)
    m.add("tong1", tub("body", "iron", [P(-15, 26, -12), P(-14, 28, -26), P(-13, 26, -32)], [0.9, 0.8, 0.6], None, sides=5))
    m.add("tong2", tub("body", "iron", [P(-15, 26, -12), P(-16, 24, -26), P(-17, 26, -32)], [0.9, 0.8, 0.6], None, sides=5))
    m.pal = bpal({"skin": M_([(70, 46, 40), (98, 66, 56), (128, 90, 74), (156, 116, 96)]), "fur": M_(FUR), "leath": M_(LEATHR), "hoof": M_(BAS), "bone": M_(BONE, lit=0.34),
                  "brass": M_(BRASSY), "iron": M_(IRON), "magma": M_(MAG, base=0.6, lit=0.24)}, glow=["magma"])
    m.detail = lambda c, col, glow: (fur(c, col, ["fur"], amt=0.08), glow)
    m.view = dict(center=(0, 28, -2), scale=3.0)
    m.caption = ["牛の頭の大男の鍛冶番（人の倍ほど）", "左手に焼けた鉄の板の盾", "右手の大きな火ばさみで掴んで投げる"]
    return m


def warden_c():
    """C 歩く炉: 四本の鉄の脚で歩く炉。前の二本の鎖の腕、炉の口が顔"""
    m = Mount("warden_c", "歩く炉")
    m.bone("body", "root", (0, 18, 0))
    m.add("kiln", lof("body", "brick", [ysec(14, 22, 20), ysec(28, 22, 20), ysec(38, 18, 16), ysec(44, 10, 10)]), core=True)
    m.add("mouth", ell("body", "magma", (0, 26, -10.2), (6, 4.6, 1.0), segs=10, rings=2))
    for s in (1, -1):
        eye(m, "body", (s * 4.2, 33, -9.0), 1.2, (0, 0, -1), (255, 200, 90), glow=True)
    m.add("grate", box("body", "iron", (0, 21, -10.4), (12, 2, 2)))
    m.add("stack", tub("body", "brick", [P(0, 44, 2), P(0, 56, 3)], [3.0, 2.4], None, sides=6))
    for s in (1, -1):
        for k, z in enumerate((-7, 7)):
            hip = P(s * 10, 16, z)
            m.add(f"leg{s}{k}", tub("body", "iron", [hip, hip + P(s * 7, 2, 0), P(s * 20, 0.8, z + (2 if k else -2))], [2.4, 2.0, 1.4], None, sides=6))
        sh = P(s * 11, 32, -6)
        el = P(s * 16, 30, -14)
        m.add(f"carm{s}", tub("body", "iron", [sh, el, el + P(0, -6, -4)], [1.8, 1.6, 1.4], None, sides=6))
        m.add(f"chain{s}", tub("body", "iron", [el + P(0, -6, -4), el + P(0, -12, -6)], [0.6, 0.6], None, sides=4))
        m.add(f"hook{s}", cone("body", "magma", el + P(0, -12, -6), (0, -0.4, -1), 4.0, 1.2, sides=4, rings=2, bend=(0, 2.0, 0)))
    m.pal = bpal({"brick": M_(BRICK, streak=0.04), "iron": M_(IRON), "magma": M_(MAG, base=0.68, lit=0.2)}, glow=["magma"])
    m.detail = lambda c, col, glow: (plates(c, col, ["brick"], size=2.6, edge=0.08, k_edge=0.6), glow)
    m.view = dict(center=(0, 24, -2), scale=2.8)
    m.caption = ["四本の鉄の脚で歩く炉（人の倍ほど）", "炉の口が顔、前に鎖の腕", "口から熔けた鉄を吐く"]
    return m


SUBJECTS = {
    "digger": ("骨掘りの坑夫（鯨骨の坑・道中）", [digger_a, digger_b, digger_c]),
    "grub": ("骨喰いの小虫（鯨骨の坑・道中の群れ）", [grub_a, grub_b, grub_c]),
    "foreman": ("坑の頭（鯨骨の坑・中ボス）", [foreman_a, foreman_b, foreman_c]),
    "sailor": ("沈み船の水夫（潮の洞・道中）", [sailor_a, sailor_b, sailor_c]),
    "octoling": ("洞の小さな軟体（潮の洞・道中）", [octoling_a, octoling_b, octoling_c]),
    "bellcrab": ("沈み船の大物（潮の洞・中ボス）", [bellcrab_a, bellcrab_b, bellcrab_c]),
    "beetle": ("晶の虫（雷晶の空洞・道中）", [beetle_a, beetle_b, beetle_c]),
    "bat": ("晶の飛ぶもの（雷晶の空洞・道中）", [bat_a, bat_b, bat_c]),
    "golem": ("晶の大物（雷晶の空洞・中ボス）", [golem_a, golem_b, golem_c]),
    "miner": ("炉の坑夫（熔けた坑道・道中）", [miner_a, miner_b, miner_c]),
    "slug": ("熔岩の這うもの（熔けた坑道・道中）", [slug_a, slug_b, slug_c]),
    "warden": ("炉の番（熔けた坑道・中ボス）", [warden_a, warden_b, warden_c]),
}


def render(m, PW=340, PH=300):
    size, atlas, glow = paint_mount(m, m.pal, m.detail, 256)
    cen, sc = np.array(m.view["center"], float), m.view["scale"]
    ims = [view(m.F, atlas, glow, 215, 14, (PW, PH), cen, sc), view(m.F, atlas, glow, 270, 6, (PW, PH), cen, sc),
           view(m.F, atlas, glow, 330, 26, (PW, PH), cen, sc)]
    tris = sum(len(f.verts) - 2 for f in m.F)
    return ims, tris


def sheet(key):
    title, fns = SUBJECTS[key]
    F = voxel.font
    PW, PH = 340, 300
    cols = []
    for i, fn in enumerate(fns):
        m = fn()
        ims, tris = render(m, PW, PH)
        cols.append((m, ims, tris))
        print(key, "ABC"[i], m.title, tris, flush=True)
    W_ = 24 + 3 * (PW + 16)
    H_ = 80 + 120 + 3 * (PH + 8) + 20
    cv = Image.new("RGB", (W_, H_), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((20, 14), f"{title} — 三案のラフ", font=F(28), fill=(236, 232, 218))
    d.text((20, 50), "上から: 斜め前 / 横 / 斜め上の後ろ。左の黄色い目盛りが人 (1.8 ブロック)", font=F(15), fill=(176, 176, 192))
    for i, (m, ims, tris) in enumerate(cols):
        x = 20 + i * (PW + 16)
        d.text((x, 80), f"{'ABC'[i]}  {m.title}", font=F(24), fill=(150, 232, 255))
        for j, line in enumerate(m.caption):
            d.text((x, 114 + j * 22), line, font=F(15), fill=(210, 208, 196))
        d.text((x, 114 + 3 * 22), f"{tris} 三角形（ラフ）", font=F(13), fill=(150, 150, 166))
        y = 200
        for im in ims:
            cv.paste(im, (x, y))
            y += PH + 8
    OUT.mkdir(parents=True, exist_ok=True)
    cv.save(OUT / f"{key}.png")
    for i, (m, ims, tris) in enumerate(cols):
        big = Image.new("RGB", (PW * 3 + 16, PH), (16, 16, 22))
        for j, im in enumerate(ims):
            big.paste(im, (j * (PW + 8), 0))
        big.save(OUT / f"{key}_{'abc'[i]}.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    for key in (sys.argv[1:] or list(SUBJECTS)):
        sheet(key)
