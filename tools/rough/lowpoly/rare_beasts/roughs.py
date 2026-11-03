# -*- coding: utf-8 -*-
"""逃げる別の獣 (2026-11-03 の案「逃げる別の獣」。ドラクエのメタルスライム) の形のラフ。土地ごとに一種、一種ごとに三案。
色違いにはせず、形の違う一体にする。部品と描き方は地下の敵のラフ (tools/rough/lowpoly/dungeon_foes/roughs.py) と同じ。
長さの単位は 1/16 ブロック (人 = 29)。前は -z。どれも膝より少し高いくらいの小さな獣 (追いかけて捕まえる)。

    python -X utf8 -I tools/rough/lowpoly/rare_beasts/roughs.py [meadow whale tide storm ember]
    → docs/review/roughs_rare_beasts/<土地>.png と <土地>_<a|b|c>.png
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(ROOT / "tools/rough/lowpoly/dungeon_foes"))
import roughs as base  # noqa: E402
from mlp_extra import *  # noqa: E402,F401,F403

P = base.P
eye = base.eye
bpal = base.bpal

# ---------------------------------------------------------------------- 色
WHEAT = [(120, 86, 46), (164, 122, 70), (204, 162, 100), (232, 200, 140)]
CREAM = [(170, 160, 140), (204, 196, 176), (230, 224, 206), (246, 242, 230)]
BRASS = base.BRASSY
MOSS = [(40, 64, 34), (58, 88, 44), (82, 116, 58), (112, 146, 76)]
STONE = base.STONE
BONE = base.BONE
OTTER = [(52, 38, 30), (76, 56, 42), (102, 78, 58), (130, 104, 80)]
PEARL = [(176, 180, 196), (206, 210, 222), (230, 232, 240), (250, 250, 255)]
SHELL = base.SHELL
SAND = [(150, 130, 96), (184, 164, 124), (212, 194, 154), (234, 220, 184)]
SEA = base.SEA
FEATHER = [(56, 60, 80), (80, 86, 112), (110, 118, 148), (146, 154, 184)]
SPARK = [(200, 200, 90), (240, 240, 140), (255, 255, 200), (255, 255, 240)]
CLOUD = [(150, 156, 172), (184, 190, 204), (214, 218, 228), (238, 240, 246)]
HORN = [(60, 56, 52), (90, 84, 76), (122, 114, 102), (156, 146, 130)]
FOX = [(110, 50, 24), (156, 76, 36), (198, 108, 54), (228, 146, 84)]
MAG = base.MAG
CHAR = [(24, 20, 20), (38, 32, 30), (54, 46, 42), (72, 62, 56)]
ASH = base.ASH


def quad(m, mat, hip_y, half_w, fz, bz, thick=1.5, foot=(1.1, 0.7, 1.5), bend=0.8, foot_mat=None, bone="body"):
    """四つ足 (前 fz・後 bz)。前の膝は前へ、後ろの膝は後ろへ曲げる"""
    for s in (1, -1):
        for k, z, d in (("f", fz, -1), ("b", bz, 1)):
            top = P(s * half_w, hip_y, z)
            mid = P(s * (half_w + 0.2), hip_y * 0.52, z + d * bend)
            low = P(s * (half_w + 0.2), 0.9, z)
            m.add(f"leg{k}{s}", limb(bone, mat, [top, mid, low], [thick, thick * 0.8, thick * 0.6], [thick, thick * 0.8, thick * 0.6], n=6))
            m.add(f"paw{k}{s}", ell(bone, foot_mat or mat, low + P(0, -0.3, -0.4), foot, segs=6, rings=2))


def biped(m, mat, hip_y, half_w, z, thick=1.4, foot=(1.4, 0.6, 2.4), foot_mat=None):
    """鳥・蜥蜴の二本足 (膝は後ろへ)"""
    for s in (1, -1):
        top = P(s * half_w, hip_y, z)
        mid = P(s * (half_w + 0.3), hip_y * 0.55, z + 1.6)
        low = P(s * (half_w + 0.3), 0.8, z)
        m.add(f"leg{s}", limb("body", mat, [top, mid, low], [thick, thick * 0.7, thick * 0.5], [thick, thick * 0.7, thick * 0.5], n=6))
        m.add(f"foot{s}", ell("body", foot_mat or mat, low + P(0, -0.3, -0.9), foot, segs=6, rings=2))


def eyes(m, c, r, iris, spread, glow=False):
    for s in (1, -1):
        eye(m, "body", (s * spread, c[1], c[2]), r, (s * 0.5, 0.1, -1), iris, glow=glow)


def look(m, cy, scale, caption):
    m.view = dict(center=(0, cy, 0), scale=scale)
    m.caption = caption


# ====================================================================== 都のまわり (野の獣)

def meadow_a():
    """A 鈴尾の野兎: 長い耳の野兎。長い尾の先に真鍮の鈴。走ると鈴が鳴る"""
    m = Mount("meadow_a", "鈴尾の野兎")
    m.bone("body", "root", (0, 6, 0))
    m.add("torso", ell("body", "fur", (0, 6.2, 0.6), (3.4, 3.2, 5.0), segs=10, rings=3), core=True)
    m.add("haunch", ell("body", "fur", (0, 5.6, 3.4), (3.8, 3.4, 3.0), segs=9, rings=2))
    m.add("belly", ell("body", "cream", (0, 4.8, 0.2), (2.6, 2.0, 3.8), segs=8, rings=2))
    m.add("head", ell("body", "fur", (0, 9.2, -4.6), (2.4, 2.4, 2.8), segs=9, rings=3))
    m.add("snout", ell("body", "cream", (0, 8.4, -7.0), (1.3, 1.1, 1.0), segs=7, rings=2))
    for s in (1, -1):
        m.add(f"ear{s}", cone("body", "fur", P(s * 1.0, 11.0, -4.2), (s * 0.15, 1, 0.25), 7.0, 1.0, sides=6, rings=3, bend=(0, 0, 1.6)))
        m.add(f"earin{s}", cone("body", "cream", P(s * 1.0, 11.4, -4.5), (s * 0.15, 1, 0.25), 5.8, 0.55, sides=5, rings=2, bend=(0, 0, 1.4)))
    eyes(m, (0, 9.8, -6.2), 0.6, (60, 40, 30), 1.6)
    quad(m, "fur", 4.4, 2.0, -2.2, 3.6, thick=1.3, foot=(1.1, 0.6, 2.0), foot_mat="cream")
    m.add("tail", tub("body", "fur", [P(0, 6.6, 5.8), P(0, 8.6, 9.0), P(0, 8.0, 12.0)], [0.8, 0.6, 0.45], None, sides=5))
    m.add("bell", ell("body", "brass", (0, 7.0, 12.6), (1.3, 1.5, 1.3), segs=8, rings=3))
    m.add("clapper", ell("body", "dark", (0, 5.8, 12.6), (0.4, 0.4, 0.4), segs=5, rings=2))
    m.pal = bpal({"fur": M_(WHEAT, lit=0.3), "cream": M_(CREAM), "brass": M_(BRASS, base=0.6, lit=0.3), "dark": M_(base.IRON)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["fur"], (150, 110, 60), density=0.05, size=0.6), glow)
    look(m, 7, 6.6, ["麦色の野兎（膝ほど）。耳が長い", "長い尾の先に真鍮の鈴", "走ると鈴が鳴り、音で居所が分かる"])
    return m


def meadow_b():
    """B 麦穂の小狐: 細身の狐。尾が実った麦の穂の形 (房が節に並ぶ)"""
    m = Mount("meadow_b", "麦穂の小狐")
    m.bone("body", "root", (0, 7, 0))
    m.add("torso", ell("body", "fur", (0, 7.4, 0), (2.8, 2.8, 6.0), segs=10, rings=3), core=True)
    m.add("chest", ell("body", "cream", (0, 6.8, -3.6), (2.2, 2.4, 2.2), segs=8, rings=2))
    m.add("head", ell("body", "fur", (0, 10.2, -6.6), (2.4, 2.2, 2.4), segs=9, rings=3))
    m.add("muzzle", cone("body", "cream", P(0, 9.6, -8.2), (0, -0.15, -1), 3.0, 1.1, sides=6, rings=2))
    for s in (1, -1):
        m.add(f"ear{s}", cone("body", "fur", P(s * 1.4, 11.8, -6.4), (s * 0.3, 1, 0.1), 3.0, 1.0, sides=4, rings=2))
    eyes(m, (0, 10.8, -8.2), 0.5, (230, 170, 40), 1.4)
    quad(m, "fur", 5.6, 1.8, -4.0, 4.0, thick=1.1, foot=(0.9, 0.6, 1.4), foot_mat="dark")
    # 麦の穂の尾: 軸と、節ごとに左右の房
    pts = [P(0, 8.0, 5.6), P(0, 10.0, 9.0), P(0, 10.6, 13.0), P(0, 9.6, 16.6)]
    m.add("stalk", tub("body", "cream", pts, [0.9, 0.8, 0.6, 0.3], None, sides=5))
    for i in range(6):
        t = i / 6
        c = pts[1] * (1 - t) + pts[3] * t
        for s in (1, -1):
            m.add(f"grain{i}{s}", ell("body", "grain", c + P(s * 1.0, 0.3, 0), (1.0, 0.8, 1.4), segs=6, rings=2))
    m.pal = bpal({"fur": M_(FOX, lit=0.3), "cream": M_(CREAM), "grain": M_(WHEAT, base=0.6, lit=0.3), "dark": M_(CHAR)})
    m.detail = lambda c, col, glow: (col, glow)
    look(m, 8, 5.6, ["細身の小狐（膝より少し高い）", "尾が実った麦の穂の形", "麦畑に紛れて走る"])
    return m


def meadow_c():
    """C 鐘角の小鹿: 白い斑の子鹿。小さな鐘の形の角が二つ"""
    m = Mount("meadow_c", "鐘角の小鹿")
    m.bone("body", "root", (0, 9, 0))
    m.add("torso", ell("body", "fur", (0, 10.0, 0), (3.0, 3.2, 5.6), segs=10, rings=3), core=True)
    m.add("neck", tub("body", "fur", [P(0, 11.2, -4.0), P(0, 14.2, -5.6)], [1.6, 1.3], None, sides=6))
    m.add("head", ell("body", "fur", (0, 15.2, -6.6), (1.8, 1.9, 2.6), segs=9, rings=3))
    m.add("nose", ell("body", "dark", (0, 14.6, -9.0), (0.7, 0.6, 0.6), segs=6, rings=2))
    for s in (1, -1):
        m.add(f"ear{s}", ell("body", "fur", (s * 2.4, 16.0, -6.0), (1.6, 0.6, 0.9), segs=6, rings=2))
        m.add(f"stem{s}", tub("body", "horn", [P(s * 0.9, 16.6, -6.2), P(s * 1.6, 18.6, -5.8)], [0.35, 0.3], None, sides=4))
        m.add(f"bell{s}", cone("body", "brass", P(s * 1.7, 19.6, -5.8), (0, -1, 0), 1.8, 1.1, sides=7, rings=2))
    eyes(m, (0, 15.6, -8.0), 0.55, (40, 30, 24), 1.3)
    quad(m, "fur", 7.6, 1.8, -3.6, 3.6, thick=0.9, foot=(0.6, 0.6, 0.9), bend=0.6, foot_mat="dark")
    m.add("tail", cone("body", "cream", P(0, 11.0, 5.6), (0, 0.6, 1), 2.0, 0.8, sides=5, rings=2))
    m.pal = bpal({"fur": M_(FOX, lit=0.3), "cream": M_(CREAM), "brass": M_(BRASS, base=0.6, lit=0.3), "horn": M_(HORN), "dark": M_(CHAR)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["fur"], (236, 226, 206), density=0.04, size=1.2), glow)
    look(m, 11, 5.0, ["白い斑の子鹿（人の腰ほど）", "小さな鐘の形の角が二つ", "跳ねて逃げ、角の鐘が鳴る"])
    return m


# ====================================================================== 鯨見の土地 (石と骨と苔)

def whale_a():
    """A 苔石の丸獣: 石の板の殻に苔の生えた、丸まれる獣 (アルマジロのような形)"""
    m = Mount("whale_a", "苔石の丸獣")
    m.bone("body", "root", (0, 6, 0))
    for i, z in enumerate((-3.6, -1.2, 1.2, 3.6)):
        r = 4.6 - abs(z) * 0.18
        m.add(f"band{i}", ell("body", "stone", (0, 6.0, z), (r, r * 0.92, 1.6), segs=10, rings=3), core=(i == 1))
        m.add(f"moss{i}", ell("body", "moss", (0, 6.0 + r * 0.7, z), (r * 0.55, 0.7, 1.2), segs=7, rings=2))
    m.add("belly", ell("body", "skin", (0, 4.0, 0), (3.4, 2.2, 5.0), segs=8, rings=2))
    m.add("head", ell("body", "skin", (0, 5.0, -6.4), (1.8, 1.6, 2.4), segs=8, rings=2))
    m.add("helm", ell("body", "stone", (0, 6.0, -6.2), (2.0, 1.0, 2.2), segs=8, rings=2))
    for s in (1, -1):
        m.add(f"ear{s}", ell("body", "skin", (s * 1.5, 6.6, -5.4), (0.6, 0.9, 0.4), segs=5, rings=2))
    eyes(m, (0, 5.6, -8.0), 0.45, (40, 30, 20), 1.1)
    quad(m, "skin", 3.0, 2.6, -2.6, 2.6, thick=1.1, foot=(1.0, 0.5, 1.2), bend=0.4)
    m.add("tail", cone("body", "stone", P(0, 4.8, 5.0), (0, -0.3, 1), 3.4, 1.0, sides=5, rings=2))
    m.pal = bpal({"stone": M_(STONE, lit=0.3), "moss": M_(MOSS), "skin": M_(SAND)})
    m.detail = lambda c, col, glow: (plates(c, col, ["stone"], size=2.0, edge=0.08, k_edge=0.6), glow)
    look(m, 6, 6.4, ["石の板の殻に苔の生えた獣（膝ほど）", "四つの帯の殻、小さな頭と脚", "丸まって坂を転がり逃げる"])
    return m


def whale_b():
    """B 骨冠の跳び鹿: 細い脚の小さな羚羊。鯨の肋骨のように反った白い角の冠"""
    m = Mount("whale_b", "骨冠の跳び鹿")
    m.bone("body", "root", (0, 10, 0))
    m.add("torso", ell("body", "fur", (0, 11.0, 0), (2.6, 2.8, 5.0), segs=10, rings=3), core=True)
    m.add("rump", ell("body", "cream", (0, 11.2, 4.0), (2.2, 2.2, 1.4), segs=8, rings=2))
    m.add("neck", tub("body", "fur", [P(0, 12.0, -3.6), P(0, 15.0, -4.8)], [1.3, 1.0], None, sides=6))
    m.add("head", ell("body", "fur", (0, 15.8, -6.0), (1.5, 1.6, 2.4), segs=9, rings=3))
    for s in (1, -1):
        for k in range(3):
            base_ = P(s * 0.8, 17.0, -5.6 + k * 0.4)
            m.add(f"rib{s}{k}", cone("body", "bone", base_, (s * (0.5 + 0.2 * k), 1, 0.4 + 0.3 * k), 4.8 - k * 0.8, 0.45, sides=4, rings=3,
                                    bend=(s * -1.2, 0, 1.4)))
    eyes(m, (0, 16.2, -7.2), 0.5, (30, 24, 20), 1.2)
    quad(m, "fur", 8.6, 1.6, -3.4, 3.4, thick=0.8, foot=(0.5, 0.7, 0.7), bend=0.9, foot_mat="dark")
    m.pal = bpal({"fur": M_(ASH, lit=0.3), "cream": M_(CREAM), "bone": M_(BONE, lit=0.34), "dark": M_(CHAR)})
    m.detail = lambda c, col, glow: (col, glow)
    look(m, 11, 5.0, ["細い脚の小さな羚羊（人の腰ほど）", "鯨の肋骨のように反った白い角の冠", "岩から岩へ高く跳んで逃げる"])
    return m


def whale_c():
    """C 石鰭の走り蜥蜴: 後ろ足で走る蜥蜴。背に鯨の鰭のような石の鰭"""
    m = Mount("whale_c", "石鰭の走り蜥蜴")
    m.bone("body", "root", (0, 8, 0))
    m.add("torso", ell("body", "skin", (0, 8.4, 0.4), (2.4, 2.6, 4.4), segs=10, rings=3), core=True)
    m.add("neck", tub("body", "skin", [P(0, 9.6, -3.2), P(0, 11.8, -5.2)], [1.4, 1.1], None, sides=6))
    m.add("head", ell("body", "skin", (0, 12.2, -6.6), (1.4, 1.3, 2.4), segs=8, rings=2))
    m.add("fin", cone("body", "stone", P(0, 10.6, 0.8), (0, 1, 0.5), 5.2, 2.4, sides=4, rings=3, bend=(0, 0, 2.0)))
    m.add("tail", tub("body", "skin", [P(0, 8.4, 4.4), P(0, 7.0, 8.4), P(0, 6.4, 12.6)], [1.6, 1.0, 0.3], None, sides=6))
    m.add("tailfin", cone("body", "stone", P(0, 7.4, 9.0), (0, 1, 0.3), 2.2, 1.0, sides=4, rings=2))
    eyes(m, (0, 12.8, -7.2), 0.45, (220, 190, 80), 1.1)
    biped(m, "skin", 7.2, 1.8, 1.6, thick=1.3, foot=(1.2, 0.5, 2.2))
    for s in (1, -1):
        m.add(f"arm{s}", tub("body", "skin", [P(s * 1.8, 8.6, -2.4), P(s * 2.2, 7.2, -3.6)], [0.5, 0.4], None, sides=4))
    m.pal = bpal({"skin": M_(MOSS, lit=0.3), "stone": M_(STONE, lit=0.3)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["skin"], (130, 150, 90), density=0.05, size=0.6), glow)
    look(m, 9, 5.4, ["後ろ足で走る蜥蜴（膝より高い）", "背に鯨の鰭のような石の鰭", "前のめりに道を駆ける"])
    return m


# ====================================================================== 汐守の土地 (潮と貝と真珠)

def tide_a():
    """A 真珠背の海獺: 背に真珠貝を負った海獺。貝の口から真珠がのぞく"""
    m = Mount("tide_a", "真珠背の海獺")
    m.bone("body", "root", (0, 5, 0))
    m.add("torso", ell("body", "fur", (0, 5.0, 0.4), (2.8, 2.6, 6.0), segs=10, rings=3), core=True)
    m.add("belly", ell("body", "cream", (0, 4.0, -0.6), (2.0, 1.6, 4.6), segs=8, rings=2))
    m.add("head", ell("body", "fur", (0, 7.0, -6.4), (2.2, 2.0, 2.2), segs=9, rings=3))
    m.add("face", ell("body", "cream", (0, 6.4, -8.0), (1.4, 1.1, 0.8), segs=7, rings=2))
    eyes(m, (0, 7.6, -8.0), 0.45, (20, 20, 24), 1.2)
    quad(m, "fur", 3.2, 2.0, -3.6, 3.6, thick=1.0, foot=(1.0, 0.4, 1.4), bend=0.4)
    m.add("tail", tub("body", "fur", [P(0, 4.6, 6.0), P(0, 3.6, 9.6)], [1.4, 0.6], None, sides=6))
    m.add("shell_lo", ell("body", "shell", (0, 7.6, 1.0), (3.2, 1.0, 3.6), segs=10, rings=2))
    m.add("shell_hi", ell("body", "shell", (0, 9.0, 1.4), (3.0, 1.4, 3.4), segs=10, rings=3))
    m.add("pearl", ell("body", "pearl", (0, 8.4, -1.6), (1.1, 1.1, 1.1), segs=8, rings=3))
    m.pal = bpal({"fur": M_(OTTER, lit=0.3), "cream": M_(CREAM), "shell": M_(SHELL, lit=0.3), "pearl": M_(PEARL, base=0.62, lit=0.3)}, glow=["pearl"])
    m.detail = lambda c, col, glow: (plates(c, col, ["shell"], size=1.6, edge=0.06, k_edge=0.5), glow)
    look(m, 6, 6.6, ["背に真珠貝を負った海獺（膝ほど）", "貝の口から光る真珠がのぞく", "浜を這い、水に飛び込んで逃げる"])
    return m


def tide_b():
    """B 潮羽の千鳥: 脚の長い浜の鳥。翼の羽が貝の板。砂の上を小走りに逃げる"""
    m = Mount("tide_b", "潮羽の千鳥")
    m.bone("body", "root", (0, 10, 0))
    m.add("torso", ell("body", "feather", (0, 11.0, 0.6), (2.6, 2.6, 4.2), segs=10, rings=3), core=True)
    m.add("breast", ell("body", "cream", (0, 10.4, -1.8), (2.2, 2.2, 2.4), segs=8, rings=2))
    m.add("head", ell("body", "feather", (0, 14.6, -3.4), (1.6, 1.6, 1.8), segs=8, rings=3))
    m.add("beak", cone("body", "dark", P(0, 14.4, -5.0), (0, -0.1, -1), 2.6, 0.45, sides=4, rings=2))
    m.add("neck", tub("body", "cream", [P(0, 12.2, -2.4), P(0, 13.8, -3.2)], [1.1, 0.9], None, sides=6))
    eyes(m, (0, 15.0, -4.6), 0.4, (20, 20, 24), 1.0)
    for s in (1, -1):
        for k in range(3):
            m.add(f"plate{s}{k}", ell("body", "shell", (s * 2.6, 11.6 - k * 0.6, 0.2 + k * 1.8), (0.6, 1.6 - k * 0.2, 2.0), segs=7, rings=2))
    m.add("tail", cone("body", "feather", P(0, 11.4, 4.6), (0, 0.2, 1), 3.0, 1.4, sides=5, rings=2))
    biped(m, "leg", 9.0, 1.2, 0.6, thick=0.5, foot=(0.9, 0.3, 1.6))
    m.pal = bpal({"feather": M_(SEA, lit=0.3), "cream": M_(CREAM), "shell": M_(SHELL, lit=0.34), "leg": M_(CORAL_), "dark": M_(CHAR)})
    m.detail = lambda c, col, glow: (col, glow)
    look(m, 9, 5.4, ["脚の長い浜の鳥（膝より高い）", "翼の羽が貝の板、潮色の胸", "砂の上を小走りに、波打ち際へ逃げる"])
    return m


CORAL_ = [(150, 70, 60), (190, 100, 80), (220, 136, 110), (240, 170, 146)]


def tide_c():
    """C 鱗殻の跳び鼠: 後ろ足の長い跳び鼠。背は鱗の殻、尾の先に鰭"""
    m = Mount("tide_c", "鱗殻の跳び鼠")
    m.bone("body", "root", (0, 6, 0))
    m.add("torso", ell("body", "fur", (0, 6.6, 0.6), (2.6, 3.0, 3.4), segs=10, rings=3), core=True)
    m.add("scales", ell("body", "scale", (0, 8.0, 1.0), (2.6, 2.0, 3.2), segs=10, rings=2))
    m.add("head", ell("body", "fur", (0, 9.6, -3.0), (2.2, 2.1, 2.4), segs=9, rings=3))
    for s in (1, -1):
        m.add(f"ear{s}", ell("body", "fur", (s * 1.8, 12.0, -2.6), (0.8, 1.8, 0.4), segs=6, rings=2))
        m.add(f"thigh{s}", ell("body", "fur", (s * 2.2, 5.2, 2.0), (1.2, 2.2, 2.2), segs=7, rings=2))
        m.add(f"shin{s}", tub("body", "fur", [P(s * 2.4, 3.6, 3.0), P(s * 2.4, 0.8, 0.6)], [0.6, 0.4], None, sides=5))
        m.add(f"foot{s}", ell("body", "fur", (s * 2.4, 0.5, -0.6), (0.7, 0.4, 1.8), segs=6, rings=2))
        m.add(f"hand{s}", tub("body", "fur", [P(s * 1.2, 6.0, -2.4), P(s * 1.3, 4.6, -3.0)], [0.35, 0.3], None, sides=4))
    eyes(m, (0, 10.2, -4.8), 0.6, (20, 30, 40), 1.4)
    m.add("tail", tub("body", "fur", [P(0, 6.0, 3.8), P(0, 4.6, 8.0), P(0, 4.4, 13.0)], [0.6, 0.4, 0.3], None, sides=5))
    m.add("tailfin", cone("body", "scale", P(0, 4.4, 13.0), (0, 0, 1), 2.4, 1.2, sides=4, rings=2))
    m.pal = bpal({"fur": M_(SAND, lit=0.3), "scale": M_(SEA, lit=0.34)})
    m.detail = lambda c, col, glow: (plates(c, col, ["scale"], size=1.2, edge=0.06, k_edge=0.5), glow)
    look(m, 6, 6.6, ["後ろ足の長い跳び鼠（膝ほど）", "背は潮色の鱗の殻、尾の先に鰭", "跳ねて砂丘を越えて逃げる"])
    return m


# ====================================================================== 風待の土地 (雷と風と羽)

def storm_a():
    """A 雷尾の鼬: 細長い鼬。稲妻の形に折れた尾が光る"""
    m = Mount("storm_a", "雷尾の鼬")
    m.bone("body", "root", (0, 4, 0))
    m.add("torso", ell("body", "fur", (0, 4.6, 0), (2.0, 2.0, 6.4), segs=10, rings=3), core=True)
    m.add("belly", ell("body", "cream", (0, 3.8, -0.4), (1.5, 1.2, 5.0), segs=8, rings=2))
    m.add("head", ell("body", "fur", (0, 6.2, -7.0), (1.7, 1.5, 2.2), segs=9, rings=3))
    for s in (1, -1):
        m.add(f"ear{s}", ell("body", "fur", (s * 1.2, 7.6, -6.4), (0.6, 0.6, 0.3), segs=5, rings=2))
    eyes(m, (0, 6.8, -8.6), 0.45, (120, 220, 255), 1.0, glow=True)
    quad(m, "fur", 3.0, 1.4, -4.4, 4.4, thick=0.8, foot=(0.7, 0.4, 1.0), bend=0.5)
    zig = [P(0, 5.0, 6.2), P(0, 8.0, 8.0), P(0, 6.4, 9.6), P(0, 10.0, 11.6), P(0, 8.6, 13.0), P(0, 12.4, 15.0)]
    m.add("tail", tub("body", "spark", zig, [1.0, 0.9, 0.8, 0.7, 0.6, 0.3], None, sides=5))
    m.pal = bpal({"fur": M_(FEATHER, lit=0.3), "cream": M_(CLOUD), "spark": M_(SPARK, base=0.7, lit=0.2)}, glow=["spark"])
    m.detail = lambda c, col, glow: (col, glow)
    look(m, 6, 6.0, ["細長い鼬（足首より高い）", "稲妻の形に折れた尾が光る", "草の間を稲妻のように縫って逃げる"])
    return m


def storm_b():
    """B 風切りの走り鳥: 飛べない走り鳥。頭に雷の冠羽、短い翼を広げて走る"""
    m = Mount("storm_b", "風切りの走り鳥")
    m.bone("body", "root", (0, 12, 0))
    m.add("torso", ell("body", "feather", (0, 13.0, 0.8), (3.0, 3.0, 4.6), segs=10, rings=3), core=True)
    m.add("neck", tub("body", "feather", [P(0, 14.6, -2.6), P(0, 18.0, -3.8), P(0, 20.0, -4.0)], [1.4, 1.0, 0.9], None, sides=6))
    m.add("head", ell("body", "feather", (0, 21.0, -4.6), (1.5, 1.5, 1.9), segs=8, rings=3))
    m.add("beak", cone("body", "horn", P(0, 20.6, -6.2), (0, -0.2, -1), 2.0, 0.7, sides=4, rings=2))
    for k in range(3):
        m.add(f"crest{k}", cone("body", "spark", P(0, 22.2, -4.4 + k * 0.8), (0, 1, 0.4 + k * 0.3), 3.0 - k * 0.5, 0.45, sides=4, rings=2,
                                bend=(0, 0, 0.8)))
    eyes(m, (0, 21.4, -5.6), 0.45, (255, 230, 120), 1.1)
    for s in (1, -1):
        m.add(f"wing{s}", cone("body", "feather", P(s * 2.8, 14.0, 0), (s * 1, 0.1, 0.6), 4.0, 1.6, sides=4, rings=2, bend=(0, -0.8, 0)))
        m.add(f"tip{s}", cone("body", "spark", P(s * 6.4, 13.6, 2.0), (s * 1, -0.1, 0.5), 1.6, 0.6, sides=4, rings=2))
    m.add("tail", cone("body", "feather", P(0, 13.6, 5.2), (0, 0.4, 1), 3.4, 1.8, sides=5, rings=2))
    biped(m, "horn", 10.4, 1.6, 1.0, thick=0.9, foot=(1.4, 0.4, 2.0))
    m.pal = bpal({"feather": M_(STORM_, lit=0.3), "spark": M_(SPARK, base=0.66, lit=0.2), "horn": M_(HORN)}, glow=["spark"])
    m.detail = lambda c, col, glow: (col, glow)
    look(m, 12, 4.6, ["飛べない走り鳥（人の胸ほど）", "頭に雷の冠羽、翼の先が光る", "長い脚で道をまっすぐ駆けて逃げる"])
    return m


STORM_ = base.STORM


def storm_c():
    """C 雲毛の山羊仔: 雲のような綿毛の子山羊。短い巻き角。毛が静電気で逆立つ"""
    m = Mount("storm_c", "雲毛の山羊仔")
    m.bone("body", "root", (0, 8, 0))
    for i, (x, y, z, r) in enumerate([(0, 9.4, 0, 3.6), (1.6, 10.4, -2.0, 2.6), (-1.6, 10.4, -2.0, 2.6), (1.4, 10.6, 2.0, 2.6), (-1.4, 10.6, 2.0, 2.6),
                                      (0, 11.6, 0.4, 2.8)]):
        m.add(f"puff{i}", ell("body", "wool", (x, y, z), (r, r * 0.9, r), segs=8, rings=3), core=(i == 0))
    m.add("head", ell("body", "skin", (0, 12.0, -5.4), (1.6, 1.8, 2.2), segs=8, rings=3))
    m.add("cap", ell("body", "wool", (0, 13.6, -5.0), (1.8, 1.0, 1.6), segs=7, rings=2))
    for s in (1, -1):
        m.add(f"horn{s}", cone("body", "horn", P(s * 0.9, 13.6, -5.0), (s * 0.4, 0.6, 0.8), 2.6, 0.5, sides=5, rings=3, bend=(s * 0.8, -1.2, 0.6)))
        m.add(f"ear{s}", ell("body", "skin", (s * 2.0, 12.4, -4.8), (1.0, 0.4, 0.6), segs=5, rings=2))
    eyes(m, (0, 12.6, -7.2), 0.45, (200, 160, 60), 1.1)
    quad(m, "skin", 6.6, 1.8, -2.4, 2.6, thick=0.8, foot=(0.6, 0.6, 0.8), bend=0.4, foot_mat="dark")
    m.pal = bpal({"wool": M_(CLOUD, lit=0.34), "skin": M_(FEATHER), "horn": M_(SPARK, base=0.5), "dark": M_(CHAR)})
    m.detail = lambda c, col, glow: (speckle(c, col, ["wool"], (250, 252, 255), density=0.08, size=0.8), glow)
    look(m, 9, 5.6, ["雲のような綿毛の子山羊（膝より高い）", "短い巻き角、毛が逆立つ", "崖の岩場を跳ねて逃げる"])
    return m


# ====================================================================== 灰炉の土地 (熾と灰と炭)

def ember_a():
    """A 熾火の大耳狐: 耳の大きな砂漠の狐。耳の内と尾の先が熾火のように光る"""
    m = Mount("ember_a", "熾火の大耳狐")
    m.bone("body", "root", (0, 6, 0))
    m.add("torso", ell("body", "fur", (0, 6.4, 0), (2.4, 2.4, 4.2), segs=10, rings=3), core=True)
    m.add("chest", ell("body", "cream", (0, 6.0, -2.6), (1.8, 2.0, 1.8), segs=8, rings=2))
    m.add("head", ell("body", "fur", (0, 9.0, -4.6), (2.2, 2.0, 2.0), segs=9, rings=3))
    m.add("muzzle", cone("body", "cream", P(0, 8.4, -6.0), (0, -0.2, -1), 2.0, 0.9, sides=6, rings=2))
    for s in (1, -1):
        m.add(f"ear{s}", cone("body", "fur", P(s * 1.3, 10.4, -4.4), (s * 0.55, 1, 0.1), 5.6, 1.7, sides=5, rings=2))
        m.add(f"earin{s}", cone("body", "ember", P(s * 1.35, 10.6, -4.7), (s * 0.55, 1, 0.1), 4.6, 1.1, sides=5, rings=2))
    eyes(m, (0, 9.6, -6.0), 0.5, (40, 24, 16), 1.3)
    quad(m, "fur", 4.6, 1.6, -2.8, 2.8, thick=0.9, foot=(0.7, 0.5, 1.0), bend=0.5, foot_mat="dark")
    m.add("tail", tub("body", "fur", [P(0, 7.0, 4.0), P(0, 7.6, 7.0), P(0, 6.6, 9.6)], [1.4, 1.6, 1.0], None, sides=6))
    m.add("tailtip", ell("body", "ember", (0, 6.4, 10.4), (1.0, 1.0, 1.2), segs=7, rings=2))
    m.pal = bpal({"fur": M_(SAND, lit=0.3), "cream": M_(CREAM), "ember": M_(MAG, base=0.7, lit=0.2), "dark": M_(CHAR)}, glow=["ember"])
    m.detail = lambda c, col, glow: (col, glow)
    look(m, 7, 6.2, ["耳の大きな砂漠の狐（膝ほど）", "耳の内と尾の先が熾火のように光る", "灰の上に光の跡を残して逃げる"])
    return m


def ember_b():
    """B 炭火の丸鳥: 丸い鶉のような鳥。黒い炭の羽に、熾の尾羽"""
    m = Mount("ember_b", "炭火の丸鳥")
    m.bone("body", "root", (0, 5, 0))
    m.add("torso", ell("body", "char", (0, 6.0, 0), (3.6, 3.6, 4.2), segs=12, rings=4), core=True)
    m.add("breast", ell("body", "ash", (0, 5.4, -2.2), (2.8, 2.8, 2.2), segs=8, rings=2))
    m.add("head", ell("body", "char", (0, 9.4, -2.8), (1.8, 1.8, 1.8), segs=8, rings=3))
    m.add("beak", cone("body", "horn", P(0, 9.0, -4.4), (0, -0.3, -1), 1.2, 0.5, sides=4, rings=2))
    m.add("plume", cone("body", "ember", P(0, 11.0, -3.0), (0, 1, -0.4), 2.2, 0.4, sides=4, rings=2, bend=(0, 0, -0.8)))
    eyes(m, (0, 9.8, -4.0), 0.4, (255, 160, 60), 1.0, glow=True)
    for s in (1, -1):
        m.add(f"wing{s}", ell("body", "char", (s * 3.2, 6.4, 0.6), (0.8, 2.4, 3.0), segs=7, rings=2))
    for k, (dx, dy) in enumerate(((0, 1.0), (1.2, 0.4), (-1.2, 0.4))):
        m.add(f"tail{k}", cone("body", "ember", P(dx * 0.5, 7.0, 3.6), (dx * 0.3, dy, 1), 3.2, 0.7, sides=4, rings=2))
    biped(m, "horn", 3.6, 1.4, 0.4, thick=0.5, foot=(0.9, 0.3, 1.4))
    m.pal = bpal({"char": M_(CHAR, lit=0.3), "ash": M_(ASH), "ember": M_(MAG, base=0.7, lit=0.2), "horn": M_(HORN)}, glow=["ember"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["char"], (110, 50, 30), density=0.05, size=0.6), glow)
    look(m, 6, 6.4, ["丸い鶉のような鳥（膝ほど）", "黒い炭の羽、熾の尾羽と冠", "ちょこまか走り、灰を巻き上げて逃げる"])
    return m


def ember_c():
    """C 熔背の土竜: 背に熔けた溝の走る板を負った土竜。大きな前の爪で地に潜る"""
    m = Mount("ember_c", "熔背の土竜")
    m.bone("body", "root", (0, 5, 0))
    m.add("torso", ell("body", "fur", (0, 5.0, 0), (3.2, 2.8, 5.4), segs=10, rings=3), core=True)
    for i, z in enumerate((-2.4, 0.2, 2.8)):
        m.add(f"plate{i}", box("body", "rock", (0, 7.6, z), (4.4 - abs(z) * 0.3, 1.2, 2.2), taper=(0.8, 0.9), cut=0.3))
        m.add(f"seam{i}", box("body", "ember", (0, 8.3, z), (3.0 - abs(z) * 0.3, 0.3, 0.5), taper=(1, 1), cut=0.1))
    m.add("head", ell("body", "fur", (0, 5.0, -6.0), (2.0, 1.8, 2.2), segs=8, rings=2))
    m.add("snout", cone("body", "skin", P(0, 4.8, -7.8), (0, -0.1, -1), 1.6, 0.8, sides=6, rings=2))
    eyes(m, (0, 5.8, -7.4), 0.3, (255, 120, 60), 1.2, glow=True)
    for s in (1, -1):
        m.add(f"arm{s}", tub("body", "fur", [P(s * 2.6, 4.6, -3.6), P(s * 3.8, 2.4, -5.0)], [1.0, 0.8], None, sides=5))
        m.add(f"claw{s}", ell("body", "skin", (s * 4.0, 1.6, -5.6), (1.6, 0.6, 1.4), segs=6, rings=2))
        for k in range(3):
            m.add(f"nail{s}{k}", cone("body", "rock", P(s * (3.2 + k * 0.6), 1.4, -6.6), (0, -0.2, -1), 1.2, 0.3, sides=4, rings=1))
        m.add(f"hind{s}", ell("body", "fur", (s * 2.4, 1.6, 3.4), (1.0, 1.0, 1.6), segs=6, rings=2))
    m.add("tail", cone("body", "skin", P(0, 4.6, 5.2), (0, -0.2, 1), 2.0, 0.5, sides=4, rings=2))
    m.pal = bpal({"fur": M_(CHAR, lit=0.3), "skin": M_(SKIN_), "rock": M_(base.BAS, lit=0.3), "ember": M_(MAG, base=0.74, lit=0.2)}, glow=["ember"])
    m.detail = lambda c, col, glow: (col, glow)
    look(m, 5, 6.4, ["背に熔けた溝の板を負った土竜（膝ほど）", "大きな前の爪", "灰の地に潜って逃げ、別の所から出る"])
    return m


SKIN_ = [(120, 70, 66), (156, 100, 92), (190, 130, 118), (220, 164, 150)]


SUBJECTS = {
    "meadow": ("都のまわりの逃げる獣", [meadow_a, meadow_b, meadow_c]),
    "whale": ("鯨見の土地の逃げる獣", [whale_a, whale_b, whale_c]),
    "tide": ("汐守の土地の逃げる獣", [tide_a, tide_b, tide_c]),
    "storm": ("風待の土地の逃げる獣", [storm_a, storm_b, storm_c]),
    "ember": ("灰炉の土地の逃げる獣", [ember_a, ember_b, ember_c]),
}


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    base.SUBJECTS = SUBJECTS
    base.OUT = ROOT / "docs/review/roughs_rare_beasts"
    for key in (sys.argv[1:] or list(SUBJECTS)):
        base.sheet(key)
