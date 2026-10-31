"""野の装備 (docs/design/51 の 5 章) の防具 18 式。一式ごとに一品一案 (形の構想は doc の表)。
部品と出し方は淵潜りの鎧 v2 (../../gear_gaps/armor_abyss_v2.py) を借り、塗りは一式ごとの素材の表 (paint_wild と同じ素材と描き方)。
骨: armorHead (頭 y 24〜32)、armorBody (胴 y 12〜24)、腕 ARM[s] (cx = s*6)、脚 LEG[s] (cx = s*2.05、y 0〜12)、靴 BOOT[s] (y 0〜7)。正面は -Z。

実行: python -I tools/rough/lowpoly/armor/wild/build_wild_armor.py [一式 id ...]
出力: docs/review/roughs_wild/raw/armor_<一式>/a/<部位>_{mesh.json,atlas.png,glowmask.png}[_plus5|_plus10]・icons/a_<部位>_32.png、
      docs/review/roughs_wild/armor_<一式>/opt_a.png。入れ方: python -I tools/install/install_wild_gear.py armor
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
sys.path.insert(0, str(ROOT / "tools/rough/lowpoly/gear_gaps"))
sys.path.insert(0, str(ROOT / "tools/rough/lowpoly/weapons/wild"))
sys.path.insert(0, str(ROOT / "tools/gen/wild_gear"))
import armor_abyss_v2 as A  # noqa: E402
from armor_abyss_v2 import (bx, crv, rot_x, rot_y, rot_z, ARM, LEG, BOOT, sring, dloft, bplate, ngon, drape, lume, M_SIDE, E, N)  # noqa: E402,F401
from akit import spk, ext, edge_fields, fill_outside, hash1, vnoise, BAYER  # noqa: E402
from wkit import pick, ramp, LIGHT  # noqa: E402
import paint_wild as PW  # noqa: E402
import spec as SPEC  # noqa: E402

SETS = {}


def armor(sid, glow, mats):
    def deco(fns):
        SETS[sid] = dict(fns=fns, glow=glow, mats=mats)
        return fns
    return deco


# ====================================================================== 共通の部品
def hood(b, mat="cloth", edge="cape", depth=6.0, open_face=True, peak=0.0, tail=None):
    """深い頭巾 (顔を開ける)。peak > 0 で頂が尖る"""
    lv = [(24.2, 5.65, -5.7, 5.8), (26.4, 5.9, -5.9, 6.1), (28.8, 5.95, -5.95, 6.2), (31.0, 5.8, -5.8, 6.1),
          (32.8, 5.5, -5.4, 5.8), (34.1, 4.0, -3.6, 4.6), (34.9 + peak * 0.6, 1.8, -1.2, 2.6)]
    rings = [sring(y, hx, zf, zb, n=16, p=4.0) for (y, hx, zf, zb) in lv]
    f = []
    for fc in dloft(b, mat, rings, caps=(False, mat)):
        c = fc.verts.mean(0)
        if open_face and fc.verts[:, 1].max() < 30.6 and fc.normal[2] < -0.35 and abs(c[0]) < 4.2:
            continue
        f.append(fc)
    if peak > 0:
        f += spk(b, mat, (0, 35.2, 0.6), (0, 1, 0.5), peak, 1.6, sides=6, rings=2)
    f += bx(b, edge, (0, 30.9, -5.95), (8.6, 1.0, 0.8), taper=(0.96, 1.0))
    for s in (-1, 1):
        f += bx(b, edge, (s * 4.6, 27.4, -5.75), (0.9, 7.0, 0.8))
    if tail:
        f += drape(b, [mat, mat, mat, edge], 0.0, 26.4, tail, 5.6, 4.4, 5.2, rows=5, cols=6, fold=0.5, flare_z=1.3, t=0.5)
    return f


def helm(b, mat="plate", bev="bevel", visor=True, crest=None, slit_mat="dark"):
    """丸い鉄の兜 (顔の前に覗きの帯)"""
    lv = [(23.8, 5.6, -5.8, 5.7), (26.0, 5.9, -6.1, 6.0), (29.0, 6.0, -6.2, 6.1), (31.6, 5.6, -5.8, 5.8), (33.6, 4.4, -4.4, 4.6), (34.8, 2.2, -2.2, 2.4)]
    f = dloft(b, mat, [sring(y, hx, zf, zb, n=16, p=3.2) for y, hx, zf, zb in lv], caps=(False, mat))
    if visor:
        f += bx(b, slit_mat, (0, 28.8, -6.15), (7.6, 0.8, 0.3))
    f += dloft(b, bev, [sring(23.6, 5.75, -5.95, 5.85, n=16, p=3.2), sring(24.4, 5.75, -5.95, 5.85, n=16, p=3.2)])
    if crest:
        f += ext(b, crest, [(0, 0), (0.6, 1.8), (0.4, 4.0), (-0.4, 4.6), (-0.6, 1.8)], 0.5, c=(0, 34.2, 0), rot=rot_y(90))
    return f


def brim(b, mat, y, R, h=0.5, n=16):
    return dloft(b, mat, [sring(y, R, -R, R, n=n, p=2.0), sring(y + h, R, -R, R, n=n, p=2.0)], caps=(mat, mat))


def torso(B, mat, wide=1.0, bulk=0.0):
    return dloft(B, mat, [sring(y, hx * wide + bulk, zf - bulk, zb + bulk, n=N, p=3.0) for y, hx, zf, zb in
                          [(12.0, 4.75, -2.85, 2.8), (15.5, 4.6, -2.75, 2.7), (19.0, 4.7, -2.85, 2.75), (22.6, 4.85, -2.95, 2.85), (24.3, 4.4, -2.75, 2.65)]])


def collar(B, mat, h=2.4, flare=0.8, open_front=True):
    f = []
    for fc in dloft(B, mat, [sring(24.0, 4.5, -3.0, 2.9, n=N, p=2.8), sring(24.0 + h * 0.5, 4.5 + flare * 0.5, -3.0 - flare * 0.5, 2.9 + flare * 0.5, n=N, p=2.8),
                             sring(24.0 + h, 4.5 + flare, -3.0 - flare, 2.9 + flare, n=N, p=2.8)]):
        c = fc.verts.mean(0)
        if open_front and fc.normal[2] < -0.5 and abs(c[0]) < 2.0:
            continue
        f.append(fc)
    return f


def belt(B, mat, y=12.6, h=1.3, buckle=None):
    f = dloft(B, mat, [sring(y, 4.95, -3.05, 3.0, n=N, p=3.0), sring(y + h, 4.9, -3.0, 2.95, n=N, p=3.0)])
    if buckle:
        f += bx(B, buckle, (0, y + h / 2, -3.25), (1.4, 1.2, 0.5), taper=(0.8, 0.8))
    return f


def sleeve(ab, s, mat, cuff=None, glove=None, y0=24.0, y1=14.8, wide=1.0):
    cx = s * 6.0
    g = dloft(ab, mat, [sring(y, hx * wide, zf * wide, zb * wide, n=N, p=3.0, cx=cx) for y, hx, zf, zb in
                        [(y0, 2.8, -2.85, 2.85), (18.0, 2.55, -2.7, 2.7), (y1, 2.6, -2.7, 2.7)]])
    if cuff:
        g += dloft(ab, cuff, [sring(15.2, 2.95, -3.0, 3.0, n=N, p=3.0, cx=cx), sring(13.6, 3.2, -3.25, 3.25, n=N, p=3.0, cx=cx)])
    if glove:
        g += dloft(ab, glove, [sring(13.7, 2.75, -2.8, 2.8, n=N, p=3.0, cx=cx), sring(11.9, 2.7, -2.75, 2.75, n=N, p=3.0, cx=cx)])
    return g


def pauldron(ab, s, mat, layers=3, big=1.0, edge=None):
    cx = s * 6.0
    g = []
    for k in range(layers):
        y = 25.2 - 1.3 * k
        hx, hz = (2.6 + 0.35 * k) * big, (2.6 + 0.3 * k) * big
        m = edge if (edge and k == layers - 1) else mat
        g += dloft(ab, m, [sring(y, hx, -hz, hz, n=N, p=2.4, cx=cx + s * 0.3 * k), sring(y - 1.0, hx + 0.25, -hz - 0.2, hz + 0.2, n=N, p=2.4, cx=cx + s * (0.3 * k + 0.25))],
                   caps=(m, False))
    return g


def trousers(lb, s, mat, wraps=None, knee=None, knee_bev="bevel"):
    cx = s * 2.05
    g = dloft(lb, mat, [sring(y, hx, zf, zb, n=N, p=3.0, cx=cx) for y, hx, zf, zb in [(12.0, 2.55, -2.6, 2.55), (8.0, 2.4, -2.55, 2.5), (4.6, 2.35, -2.5, 2.45)]])
    for y in (wraps or ()):
        g += dloft(lb, "sash", [sring(y, 2.56, -2.68, 2.62, n=N, p=3.0, cx=cx), sring(y + 0.9, 2.54, -2.66, 2.6, n=N, p=3.0, cx=cx)])
    if knee:
        g += bplate(lb, knee, knee_bev, ngon(2.2, 2.4, 10), c=(cx, 7.4, -2.75), h=0.6, ins=0.4, bow=0.4)
    return g


def boot(bb, s, mat, cuff=None, toe=None, heel=None, tall=6.6):
    cx = s * 2.05
    g = dloft(bb, mat, [sring(y, hx, zf, zb, n=10, p=2.8, cx=cx) for y, hx, zf, zb in
                        [(tall, 2.55, -2.65, 2.6), (3.6, 2.55, -2.9, 2.6), (1.8, 2.6, -3.7, 2.65), (0.3, 2.55, -4.2, 2.6)]], caps=(False, mat))
    if cuff:
        g += dloft(bb, cuff, [sring(tall - 1.0, 2.75, -2.85, 2.8, n=10, p=2.8, cx=cx), sring(tall + 0.4, 2.95, -3.05, 3.0, n=10, p=2.8, cx=cx)])
    if toe:
        g += bplate(bb, toe, "bevel", ngon(3.4, 2.2, 8, taper=0.1), c=(cx, 1.3, -3.8), R=rot_x(-45), h=0.6, ins=0.4, bow=0.4)
    if heel:
        g += bplate(bb, heel, "bevel", ngon(3.0, 1.8, 8, taper=0.1), c=(cx, 1.2, 2.75), R=rot_x(45), h=0.5, ins=0.3, bow=0.3)
    return g


def pair(fn):
    out = {}
    for s in (-1, 1):
        k, g = fn(s)
        out[k] = g
    return out


# ====================================================================== 帯 I
@armor("wild_oilskin", "lamp", dict(cloth="oilskin", cape="rope", sash="rope", plate="leather", bevel="leather", brass="brass", dark="dark"))
class Oilskin:
    """漁り合羽: 島の漁師の油を引いた黄土の合羽。つば広の雨帽子 (後ろが長い)、胸の浮き玉の紐、ゴムの長靴 (折り返し)"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = dloft(b, "cloth", [sring(y, hx, zf, zb, n=16, p=2.6) for y, hx, zf, zb in [(31.0, 5.9, -6.0, 6.0), (33.0, 5.2, -5.2, 5.4), (34.6, 3.6, -3.4, 3.8),
                                                                                     (35.2, 1.6, -1.4, 1.8)]], caps=(False, "cloth"))
        # つば (前は短く、後ろへ長く垂れる)
        rim = [sring(30.8, 8.2, -7.4, 9.6, n=16, p=2.2), sring(31.2, 7.4, -6.6, 8.6, n=16, p=2.2)]
        f += dloft(b, "cloth", rim, caps=("cloth", "cloth"))
        f += dloft(b, "cape", [sring(31.2, 6.0, -6.1, 6.1, n=16, p=2.6), sring(31.8, 5.95, -6.05, 6.05, n=16, p=2.6)])
        for s in (-1, 1):
            f += crv(b, "cape", [(s * 5.6, 31.0, 0.5), (s * 5.4, 27.0, -1.0), (s * 2.0, 24.6, -4.6)], 0.2, 0.2, sides=4)
        f += lume(b, (0, 31.5, -6.15), (0.6, 0.6, 0.25))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth", bulk=0.25)
        f += collar(B, "cloth", h=1.6, flare=0.6)
        for y in (15.0, 18.0, 21.0):
            f += bx(B, "brass", (0.0, y, -3.25), (0.6, 0.6, 0.4))
        f += bx(B, "cape", (0, 18.4, -3.35), (0.5, 12.0, 0.3), rot=rot_z(-24))
        f += A.E(B, "plate", (2.2, 15.8, -3.6), (0.95, 0.95, 0.8))
        f += lume(B, (2.2, 15.8, -4.35), (0.4, 0.4, 0.2))
        f += drape(B, ["cloth", "cloth", "cloth"], 0, 13.4, 9.0, 3.0, 4.8, 5.4, rows=3, cols=6, fold=0.3, flare_z=0.8, t=0.45)
        out = {B: f}
        for s in (-1, 1):
            out[ARM[s]] = sleeve(ARM[s], s, "cloth", cuff="cape", wide=1.06)
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth", wraps=(9.4,))))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "plate", cuff="plate", tall=7.2) + lume(BOOT[s], (s * 2.05, 6.4, -3.1), (0.5, 0.5, 0.25))))


@armor("wild_belfry", "lamp", dict(cloth="bluecloth", cape="whitecloth", sash="leather", plate="bronze", bevel="bronze", brass="brass", dark="dark"))
class Belfry:
    """鐘撞きの法衣: 都の鐘楼の鐘撞きの青い法衣。深い頭巾に白い縁、肩に鐘の縫い取り、帯から小さな鐘と撞木の紐が下がる、裾は足首まで"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = hood(b, "cloth", "cape", tail=20.0)
        f += A.E(b, "plate", (0, 33.6, -4.0), (0.8, 0.9, 0.5))
        f += lume(b, (0, 33.6, -4.6), (0.4, 0.4, 0.2))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth")
        f += collar(B, "cape", h=1.0, flare=0.4, open_front=False)
        f += belt(B, "sash", buckle="brass")
        f += bx(B, "cape", (0, 18.0, -3.05), (1.6, 11.0, 0.4))
        for x in (-1.5, 1.5):
            f += bx(B, "brass", (x, 12.0, -3.4), (0.1, 1.6, 0.1))
            f += A.E(B, "plate", (x, 10.6, -3.4), (0.55, 0.7, 0.55))
        for s in (-1, 1):
            f += drape(B, ["cloth", "cloth", "cloth", "cape"], s * 2.4, 13.0, 1.0, 2.95, 2.3, 3.0, rows=5, cols=4, fold=0.4, flare_z=1.0, t=0.5)
            f += drape(B, ["cloth", "cloth", "cape"], s * 2.4, 12.6, 1.4, -2.95, 2.2, 2.8, rows=4, cols=4, fold=0.3, flare_z=0.6, t=0.5, out=-1.0, ov=(0, 0, -1))
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "cloth", cuff="cape", wide=1.12)
            g += bplate(ARM[s], "plate", "bevel", ngon(1.8, 2.2, 8, bottom=0.6), c=(s * 8.85, 21.0, 0), R=M_SIDE(s), h=0.4, ins=0.3, bow=0.3)
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth")))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "sash", cuff="cape", tall=5.0) + bx(BOOT[s], "brass", (s * 2.05, 3.8, -3.0), (0.8, 0.6, 0.3))
                               + lume(BOOT[s], (s * 2.05, 3.8, -3.2), (0.3, 0.3, 0.15))))


@armor("wild_oldsoldier", "lamp", dict(cloth="redcloth", cape="redcloth", sash="leather", plate="iron", bevel="rust", brass="bronze", chain="iron", dark="dark"))
class OldSoldier:
    """骸兵の古鎧: 昔の戦の兵の錆びた鎧。欠けた前立ての兜、破れた赤い陣羽織、肩の段の大袖、鎖の垂れ、すね当て"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = helm(b, "plate", "bevel", crest="brass")
        for s in (-1, 1):
            f += bplate(b, "plate", "bevel", ngon(3.4, 3.8, 6, bottom=0.6), c=(s * 6.2, 25.6, 0.8), R=M_SIDE(s), h=0.5, ins=0.3, bow=0.6)
        f += lume(b, (0, 28.8, -6.35), (2.0, 0.3, 0.15))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "plate", bulk=0.3)
        f += bplate(B, "plate", "bevel", ngon(8.4, 7.6, 8, bottom=0.3), c=(0, 19.6, -3.3), h=0.8, ins=0.6, bow=0.8)
        f += belt(B, "sash", buckle="brass")
        f += drape(B, ["cloth", "cloth", "cloth", "cloth"], 0, 24.0, 10.0, -3.6, 4.0, 4.6, rows=4, cols=5, fold=0.3, flare_z=0.2, t=0.4, out=-1.0,
                   ov=(0, 0, -1), hem=[0.0, -1.2, 0.4, -0.8, 0.2])
        for s in (-1, 1):
            f += drape(B, ["chain", "chain", "chain"], s * 2.4, 12.6, 8.4, -3.0, 2.0, 2.4, rows=3, cols=4, fold=0.2, flare_z=0.5, t=0.4, out=-1.0, ov=(0, 0, -1))
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "chain", glove="sash")
            g += pauldron(ARM[s], s, "plate", layers=4, big=1.1, edge="brass")
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "chain", knee="plate")
                               + bplate(LEG[s], "plate", "bevel", ngon(2.4, 4.2, 8), c=(s * 2.05, 10.0, -2.8), h=0.5, ins=0.3, bow=0.5)))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "sash", toe="plate", tall=6.0)
                               + bplate(BOOT[s], "plate", "bevel", ngon(2.4, 3.6, 8), c=(s * 2.05, 4.8, -2.9), h=0.5, ins=0.3, bow=0.5)
                               + lume(BOOT[s], (s * 2.05, 4.8, -3.45), (0.3, 0.3, 0.15))))


# ====================================================================== 帯 II
@armor("wild_sailcloth", "sea", dict(cloth="sailcloth", cape="bluecloth", sash="rope", plate="leather", bevel="leather", brass="brass", dark="dark"))
class Sailcloth:
    """汐守の帆布装: 帆布と縄の船乗りの装い。結んだ青い頭巾 (後ろに結び目の尾)、胸の帆の縫い目、縄の帯と錨の留め、甲板の靴"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = dloft(b, "cape", [sring(y, hx, zf, zb, n=16, p=2.8) for y, hx, zf, zb in [(29.6, 6.0, -6.1, 6.1), (32.2, 5.6, -5.6, 5.9), (34.0, 4.0, -3.8, 4.4),
                                                                                    (34.8, 1.6, -1.4, 2.0)]], caps=(False, "cape"))
        f += dloft(b, "cape", [sring(29.4, 6.05, -6.15, 6.15, n=16, p=2.8), sring(30.2, 6.05, -6.15, 6.15, n=16, p=2.8)])
        f += A.E(b, "cape", (0, 30.6, 6.4), (1.0, 0.8, 0.7))
        for s in (-1, 1):
            f += crv(b, "cape", [(s * 0.4, 30.4, 6.8), (s * 1.6, 28.0, 7.4), (s * 2.2, 25.6, 7.0)], [0.5, 0.42, 0.2], [0.2, 0.2, 0.1], sides=4)
        f += lume(b, (0, 32.0, -5.9), (0.4, 0.4, 0.2))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth", bulk=0.15)
        for x in (-1.6, 1.6):
            f += bx(B, "sash", (x, 18.0, -3.05), (0.2, 11.4, 0.2))
        f += belt(B, "sash", buckle="brass")
        f += ext(B, "brass", [(0, 1.2), (0.3, 0.2), (1.0, -0.6), (0, -1.0), (-1.0, -0.6), (-0.3, 0.2)], 0.4, c=(0, 13.2, -3.6))
        f += lume(B, (0, 13.4, -3.85), (0.3, 0.3, 0.12))
        f += collar(B, "cape", h=1.2, flare=1.2)
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "cloth", cuff="cape")
            g += crv(ARM[s], "sash", [(s * 6.0 + s * 2.8, 22.0, -2.0), (s * 6.0 + s * 3.0, 20.0, 0), (s * 6.0 + s * 2.8, 18.0, 2.0)], 0.25, 0.25, sides=4)
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth", wraps=(5.0,))
                               + dloft(LEG[s], "cape", [sring(3.8, 2.45, -2.6, 2.55, n=N, p=3.0, cx=s * 2.05), sring(5.0, 2.7, -2.85, 2.8, n=N, p=3.0, cx=s * 2.05)])))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "plate", tall=4.0)
                               + dloft(BOOT[s], "sash", [sring(0.2, 2.65, -4.3, 2.7, n=10, p=2.8, cx=s * 2.05), sring(0.9, 2.65, -4.1, 2.7, n=10, p=2.8, cx=s * 2.05)])
                               + lume(BOOT[s], (s * 2.05, 3.0, -3.0), (0.3, 0.3, 0.15))))


@armor("wild_lurelight", "lamp", dict(cloth="darkwood", cape="rope", sash="leather", plate="iron", bevel="iron", brass="brass", dark="dark"))
class Lurelight:
    """漁り火の外套: 沈んだ漁師の網を重ねた黒い外套。網の目の垂れ、腰に小さな漁り火の提灯、肩に浮き玉、深い頭巾から海藻"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = hood(b, "cloth", "cape", tail=21.0)
        for x in (-3.6, 3.8):
            f += crv(b, "cape", [(x, 31.0, -5.8), (x * 1.05, 27.0, -6.0), (x * 0.9, 24.0, -5.6)], 0.18, 0.18, sides=3)
        f += A.E(b, "gem", (2.4, 33.2, -4.8), (0.5, 0.5, 0.4))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth")
        f += collar(B, "cloth", h=2.2, flare=1.0)
        f += belt(B, "sash")
        for s in (-1, 1):
            f += drape(B, ["cape", "cape", "cape", "cape"], s * 2.4, 13.4, 2.6, 3.0, 2.4, 3.1, rows=5, cols=4, fold=0.5, flare_z=1.5, t=0.35,
                       hem=[0.0, -0.8, 0.3, -1.1])
        # 腰の提灯
        f += bx(B, "brass", (3.6, 12.0, -3.2), (0.8, 0.5, 0.8))
        f += bx(B, "gem", (3.6, 11.0, -3.2), (1.0, 1.4, 1.0))
        f += bx(B, "brass", (3.6, 10.1, -3.2), (1.0, 0.4, 1.0))
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "cloth", cuff="cape", wide=1.1)
            g += A.E(ARM[s], "plate", (s * 8.6, 23.6, 0), (1.0, 1.0, 1.0))
            g += crv(ARM[s], "cape", [(s * 8.6, 23.0, 0.0), (s * 8.8, 20.6, 0.4), (s * 8.4, 18.6, 0.2)], 0.12, 0.12, sides=3)
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth", wraps=(5.0, 9.0))))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "sash", cuff="cape", tall=6.0) + lume(BOOT[s], (s * 2.05, 5.6, -3.0), (0.3, 0.3, 0.15))))


@armor("wild_ramfleece", "spark", dict(cloth="whitefur", cape="brownfur", sash="leather", plate="steel", bevel="iron", brass="copper", horn="tusk", dark="dark"))
class Ramfleece:
    """雷角の毛皮鎧: 雷角の羊の白い毛皮の上に鋼の胸当て、兜の両脇に渦巻く角、肩は巻き角の肩当て、胸に帯電の銅の留め"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = helm(b, "plate", "bevel", visor=True)
        f += dloft(b, "cloth", [sring(23.0, 6.4, -6.0, 6.6, n=16, p=2.6), sring(24.6, 6.2, -6.2, 6.4, n=16, p=2.6)])
        for s in (-1, 1):
            pts = []
            for i in range(8):
                a = math.radians(70 * i)
                R = 2.6 - 0.2 * i
                pts.append((s * (6.0 + R * (1 - math.cos(a)) * 0.6), 31.0 - R * math.sin(a) * 0.9, 1.0 - 0.3 * i))
            f += crv(b, "horn", pts, [1.0 - 0.1 * i for i in range(8)], [1.0 - 0.1 * i for i in range(8)], sides=6, caps=(True, True))
        f += lume(b, (0, 28.8, -6.35), (2.4, 0.3, 0.15))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth", bulk=0.6)
        f += bplate(B, "plate", "bevel", ngon(7.6, 7.8, 8, bottom=0.4), c=(0, 19.0, -3.8), h=0.8, ins=0.6, bow=0.8)
        f += A.E(B, "brass", (0, 21.4, -4.4), (0.9, 0.9, 0.5))
        f += lume(B, (0, 21.4, -4.9), (0.4, 0.4, 0.2))
        f += belt(B, "sash", buckle="brass")
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "cloth", glove="sash", wide=1.12)
            pts = [(s * (6.0 + 1.6 + 1.6 * (1 - math.cos(math.radians(60 * i)))), 25.0 - 1.8 * math.sin(math.radians(60 * i)), 0) for i in range(6)]
            g += crv(ARM[s], "horn", pts, [1.2 - 0.15 * i for i in range(6)], [1.2 - 0.15 * i for i in range(6)], sides=6, caps=(True, True))
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth", knee="plate")))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "sash", cuff="cloth", toe="plate", tall=6.4) + lume(BOOT[s], (s * 2.05, 1.6, -4.3), (0.4, 0.3, 0.15))))


# ====================================================================== 帯 III
@armor("wild_windweave", "frost", dict(cloth="whitecloth", cape="bluecloth", sash="bluecloth", plate="leather", bevel="leather", brass="brass", dark="dark"))
class Windweave:
    """風待の織り装: 風の里の織物の装い。額に長い織り帯の鉢巻 (後ろへなびく)、白い胴に青い織りの紋、腰から長い吹き流し、軽い草鞋"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = dloft(b, "cape", [sring(29.6, 5.95, -6.05, 6.05, n=16, p=2.8), sring(31.2, 5.95, -6.05, 6.05, n=16, p=2.8)])
        f += A.E(b, "cape", (0, 30.4, 6.3), (1.0, 0.9, 0.6))
        for s, ln in ((-1, 7.0), (1, 9.0)):
            f += drape(b, ["cape", "cape", "cloth"], s * 0.8, 30.4, 30.4 - ln, 6.6, 0.6, 0.8, rows=3, cols=3, fold=0.6, flare_z=2.0, t=0.25)
        f += lume(b, (0, 30.4, -6.2), (0.5, 0.5, 0.2))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth")
        for y in (15.0, 18.0, 21.0):
            f += dloft(B, "cape", [sring(y, 4.75, -2.95, 2.85, n=N, p=3.0), sring(y + 0.6, 4.75, -2.95, 2.85, n=N, p=3.0)])
        f += belt(B, "sash", h=1.8)
        for s in (-1, 1):
            f += drape(B, ["cape", "cape", "cape", "cloth"], s * 3.6, 13.4, 1.0, 3.0, 0.7, 1.0, rows=5, cols=3, fold=0.7, flare_z=2.4, t=0.3)
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "cloth", cuff="cape", wide=1.05)
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth", wraps=(4.8, 6.4, 8.0))))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "plate", tall=2.6)
                               + crv(BOOT[s], "sash", [(s * 2.05 - 2.4, 2.4, -1.5), (s * 2.05, 3.4, -3.0), (s * 2.05 + 2.4, 2.4, -1.5)], 0.25, 0.25, sides=4)
                               + lume(BOOT[s], (s * 2.05, 3.2, -3.0), (0.3, 0.3, 0.15))))


@armor("wild_ashleather", "ember", dict(cloth="leather", cape="ash", sash="blackleather", plate="ashwood", bevel="ash", brass="bone", dark="dark"))
class Ashleather:
    """灰炉の焼き革: 灰の民の焼き革。灰の面 (目の穴だけ、額に熾)、焦げ跡の外套、首から胸へ熾の数珠、腕に焼き革の帯"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = hood(b, "cape", "sash", tail=22.0, open_face=False)
        f += bplate(b, "plate", "bevel", ngon(7.6, 7.0, 10, bottom=0.4), c=(0, 28.4, -6.2), h=0.6, ins=0.4, bow=0.8)
        for s in (-1, 1):
            f += bx(b, "dark", (s * 1.6, 29.0, -6.85), (1.2, 0.5, 0.2))
        f += A.E(b, "gem", (0, 31.4, -6.6), (0.6, 0.6, 0.4))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth")
        f += collar(B, "cape", h=2.4, flare=1.4)
        for i in range(9):
            a = math.radians(-60 + 15 * i)
            f += A.E(B, "gem" if i % 3 == 1 else "brass", (3.6 * math.sin(a), 22.0 - 3.0 * math.cos(a) + 1.0, -3.2 - 0.3 * math.cos(a)), (0.45, 0.45, 0.45))
        f += belt(B, "sash")
        for s in (-1, 1):
            f += drape(B, ["cape", "cape", "cape", "cape"], s * 2.4, 13.2, 2.0, 2.95, 2.3, 3.2, rows=5, cols=4, fold=0.5, flare_z=1.4, t=0.45,
                       hem=[0.0, -1.0, 0.4, -0.6])
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "cloth", glove="sash")
            for y in (16.6, 19.4):
                g += dloft(ARM[s], "sash", [sring(y, 2.62, -2.78, 2.78, n=N, p=3.0, cx=s * 6.0), sring(y + 0.8, 2.6, -2.76, 2.76, n=N, p=3.0, cx=s * 6.0)])
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth", wraps=(5.0,), knee="plate")))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "sash", cuff="cape", tall=6.0) + lume(BOOT[s], (s * 2.05, 5.4, -3.0), (0.3, 0.3, 0.15))))


@armor("wild_fortress", "ember", dict(cloth="redcloth", cape="redcloth", sash="blackleather", plate="blacksteel", bevel="iron", brass="rust", dark="dark"))
class Fortress:
    """焼け砦の重鎧: 焼けた砦の騎士の重鎧。兜は塔の狭間 (頂に凹凸の冠)、煤けた板金に門の鋲、厚い肩の板、焦げた赤い前垂れ、鉄靴"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = helm(b, "plate", "bevel")
        for k in range(8):
            a = math.radians(45 * k + 22.5)
            f += bx(b, "plate", (4.6 * math.sin(a), 34.6, -4.6 * math.cos(a)), (1.6, 1.6, 1.6), rot=rot_y(-45 * k - 22.5))
        for k in range(5):
            f += A.E(b, "brass", (-3.2 + 1.6 * k, 26.6, -6.2), (0.3, 0.3, 0.2))
        f += lume(b, (0, 28.8, -6.35), (3.0, 0.3, 0.15))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "plate", bulk=0.5)
        f += bplate(B, "plate", "bevel", ngon(9.0, 9.6, 8, bottom=0.3), c=(0, 18.6, -3.8), h=1.0, ins=0.7, bow=0.9)
        for y in (15.0, 18.0, 21.0):
            for x in (-2.6, 2.6):
                f += A.E(B, "brass", (x, y, -4.7), (0.35, 0.35, 0.25))
        f += lume(B, (0, 21.6, -4.8), (0.5, 1.4, 0.2))
        f += belt(B, "sash", buckle="brass")
        f += drape(B, ["cloth", "cloth", "cloth"], 0, 12.8, 4.0, -3.6, 2.4, 2.8, rows=3, cols=4, fold=0.2, flare_z=0.5, t=0.4, out=-1.0, ov=(0, 0, -1),
                   hem=[0.0, -0.7, 0.2, -0.9])
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "plate", glove="plate")
            g += pauldron(ARM[s], s, "plate", layers=3, big=1.3, edge="bevel")
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "plate", knee="plate")
                               + bplate(LEG[s], "plate", "bevel", ngon(2.6, 4.0, 8), c=(s * 2.05, 10.0, -2.9), h=0.6, ins=0.3, bow=0.6)))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "plate", cuff="bevel", toe="plate", heel="plate", tall=6.6)
                               + lume(BOOT[s], (s * 2.05, 4.4, -3.1), (0.3, 0.5, 0.15))))


# ====================================================================== 帯 IV
@armor("wild_delver", "lamp", dict(cloth="sailcloth", cape="leather", sash="leather", plate="iron", bevel="iron", brass="brass", rope="rope", dark="dark"))
class Delver:
    """探掘隊の作業着: 探掘隊の帆布の作業着。額の灯の付いた革の帽子、胸に縄束を斜めに掛け、腰につるはし差しと袋、鋲の長靴"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = dloft(b, "cape", [sring(y, hx, zf, zb, n=16, p=2.6) for y, hx, zf, zb in [(29.8, 6.0, -6.1, 6.1), (32.4, 5.6, -5.6, 5.8), (34.4, 3.8, -3.6, 4.0),
                                                                                    (35.0, 1.6, -1.4, 1.8)]], caps=(False, "cape"))
        f += brim(b, "cape", 29.6, 7.0, h=0.5)
        f += bx(b, "brass", (0, 32.0, -6.0), (1.6, 1.6, 0.9))
        f += bx(b, "gem", (0, 32.0, -6.5), (1.2, 1.2, 0.3))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth", bulk=0.1)
        f += belt(B, "sash", buckle="brass")
        f += bx(B, "rope", (0, 18.4, -3.3), (1.0, 13.6, 0.6), rot=rot_z(30))
        f += bx(B, "rope", (0, 18.4, 3.2), (1.0, 13.6, 0.6), rot=rot_z(30))
        f += A.E(B, "rope", (-3.6, 15.0, -3.4), (1.2, 1.2, 0.6))
        f += bx(B, "sash", (3.6, 11.6, -2.4), (1.8, 2.0, 1.4))
        f += bx(B, "plate", (-4.2, 10.6, 0), (0.4, 3.6, 0.4))
        f += lume(B, (3.6, 12.0, -3.15), (0.4, 0.4, 0.15))
        out = {B: f}
        for s in (-1, 1):
            out[ARM[s]] = sleeve(ARM[s], s, "cloth", cuff="cape", glove="sash")
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth", knee="cape", knee_bev="cape")
                               + bx(LEG[s], "sash", (s * 2.05 + s * 2.6, 9.0, 0), (0.8, 2.4, 2.0))))

    @staticmethod
    def boots():
        g = lambda s: boot(BOOT[s], s, "sash", cuff="sash", toe="plate", tall=7.0) + sum(
            (A.E(BOOT[s], "brass", (s * 2.05 + x, 6.2, -3.0), (0.2, 0.2, 0.15)) for x in (-0.8, 0.8)), []) + lume(BOOT[s], (s * 2.05, 1.6, -4.3), (0.3, 0.3, 0.15))
        return pair(lambda s: (BOOT[s], g(s)))


@armor("wild_saltplate", "salt", dict(cloth="whitecloth", cape="whitecloth", sash="leather", plate="salt", bevel="glass", brass="salt", dark="dark"))
class Saltplate:
    """塩晶の鎧: 塩の結晶が板に育った白い鎧。兜と肩から結晶の群れが噴き出し、胸の板は透ける縁、関節は白い革"""
    @staticmethod
    def _crystals(bone, base, n, size, seed, up=(0, 1, 0)):
        import random
        rnd = random.Random(seed)
        f = []
        for i in range(n):
            d = np.array([up[0] + rnd.uniform(-0.6, 0.6), up[1] + rnd.uniform(0.2, 0.8), up[2] + rnd.uniform(-0.6, 0.6)])
            d /= np.linalg.norm(d)
            L = size * rnd.uniform(0.7, 1.2)
            b0 = np.array(base) + np.array([rnd.uniform(-1, 1), 0, rnd.uniform(-1, 1)]) * size * 0.4
            f += spk(bone, "bevel", tuple(b0), tuple(d), L, size * 0.3, sides=6, rings=1)
        return f

    @staticmethod
    def helmet():
        b = "armorHead"
        f = helm(b, "plate", "bevel")
        f += Saltplate._crystals(b, (0, 34.0, 1.0), 5, 2.6, 3)
        for s in (-1, 1):
            f += Saltplate._crystals(b, (s * 5.2, 31.0, 1.0), 2, 1.8, 4 + s, up=(s, 0.6, 0))
        f += lume(b, (0, 28.8, -6.35), (2.4, 0.3, 0.15))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "plate", bulk=0.4)
        f += bplate(B, "plate", "bevel", ngon(8.4, 8.4, 6, bottom=0.3), c=(0, 18.8, -3.7), h=0.9, ins=0.7, bow=0.8)
        f += lume(B, (0, 19.0, -4.6), (0.6, 0.6, 0.2))
        f += belt(B, "sash")
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "cloth", glove="plate")
            g += pauldron(ARM[s], s, "plate", layers=2, big=1.2)
            g += Saltplate._crystals(ARM[s], (s * 7.2, 25.6, 0), 4, 2.0, 10 + s, up=(s * 0.6, 1, 0))
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth", knee="plate") + Saltplate._crystals(LEG[s], (s * 2.05, 8.0, -2.8), 2, 1.2, 20 + s, up=(0, 0.4, -1))))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "plate", cuff="cloth", toe="plate", tall=6.2) + lume(BOOT[s], (s * 2.05, 1.6, -4.3), (0.3, 0.3, 0.15))))


@armor("wild_lampkeeper", "lamp", dict(cloth="purplecloth", cape="blackleather", sash="leather", plate="brass", bevel="brass", brass="brass", dark="dark"))
class Lampkeeper:
    """灯守の法衣: 深淵の灯を守る者の紫の法衣。頭巾の額に灯の窓、背から灯を吊る竿が肩越しに前へ、竿の先に灯籠"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = hood(b, "cloth", "cape", tail=21.0, peak=1.4)
        f += bx(b, "brass", (0, 32.6, -5.4), (1.8, 1.8, 0.6))
        f += bx(b, "gem", (0, 32.6, -5.8), (1.2, 1.2, 0.2))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth")
        f += collar(B, "cape", h=2.0, flare=0.8)
        f += belt(B, "sash", buckle="brass")
        for s in (-1, 1):
            f += drape(B, ["cloth", "cloth", "cloth", "cape"], s * 2.4, 13.0, 0.6, 2.95, 2.3, 3.2, rows=5, cols=4, fold=0.5, flare_z=1.6, t=0.5)
        # 背の竿と、肩越しの灯籠
        f += crv(B, "cape", [(-2.0, 12.0, 3.4), (-2.6, 22.0, 3.6), (-3.2, 27.0, 2.4), (-3.6, 29.0, -1.0), (-3.4, 28.6, -3.2)], 0.3, 0.3, sides=5)
        f += bx(B, "brass", (-3.4, 27.6, -3.4), (1.2, 0.5, 1.2))
        f += bx(B, "gem", (-3.4, 26.4, -3.4), (1.2, 1.8, 1.2))
        f += bx(B, "brass", (-3.4, 25.3, -3.4), (1.3, 0.4, 1.3))
        out = {B: f}
        for s in (-1, 1):
            out[ARM[s]] = sleeve(ARM[s], s, "cloth", cuff="cape", wide=1.15)
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth")))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "cape", cuff="cloth", tall=5.0) + lume(BOOT[s], (s * 2.05, 4.8, -3.0), (0.3, 0.3, 0.15))))


@armor("wild_hanged", "void", dict(cloth="blackleather", cape="ash", sash="rope", plate="iron", bevel="iron", brass="rope", dark="dark"))
class Hanged:
    """縄の人の吊り衣: 縄の人の裂けた黒い長衣。顔を覆う袋の頭巾 (縫い目の口と淵の紫の目)、首に結び目の縄、体を縄が巻き、裾が裂けて地に垂れる"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = dloft(b, "cape", [sring(y, hx, zf, zb, n=16, p=2.4) for y, hx, zf, zb in [(23.6, 5.4, -5.6, 5.6), (27.0, 6.0, -6.2, 6.2), (31.0, 6.0, -6.1, 6.2),
                                                                                   (33.6, 4.8, -4.6, 5.2), (35.0, 2.4, -2.0, 2.8), (35.6, 0.8, -0.6, 1.0)]],
                  caps=(False, "cape"))
        for s in (-1, 1):
            f += A.E(b, "gem", (s * 1.8, 29.6, -6.1), (0.55, 0.35, 0.25))
        f += crv(b, "sash", [(-2.0, 26.4, -6.2), (0.0, 26.0, -6.3), (2.0, 26.4, -6.2)], 0.12, 0.12, sides=3)
        f += dloft(b, "sash", [sring(23.4, 5.5, -5.7, 5.7, n=16, p=2.6), sring(24.4, 5.5, -5.7, 5.7, n=16, p=2.6)])
        f += A.E(b, "sash", (2.4, 23.4, -5.4), (1.0, 1.0, 1.0))
        f += crv(b, "sash", [(2.4, 22.6, -5.4), (2.6, 19.6, -5.0), (2.2, 17.0, -4.6)], 0.25, 0.25, sides=4)
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth")
        for k in range(4):
            y = 14.0 + 2.6 * k
            f += dloft(B, "sash", [sring(y, 4.9, -3.0, 2.95, n=N, p=3.0, dy=[0.6 * math.sin(i * 2 * math.pi / N) for i in range(N)]),
                                   sring(y + 0.6, 4.9, -3.0, 2.95, n=N, p=3.0, dy=[0.6 * math.sin(i * 2 * math.pi / N) for i in range(N)])])
        for s in (-1, 1):
            f += drape(B, ["cloth", "cloth", "cloth", "cape"], s * 2.4, 13.0, -1.0, 2.95, 2.3, 3.2, rows=5, cols=4, fold=0.6, flare_z=0.6, t=0.4,
                       hem=[0.0, -1.6, 0.6, -2.0])
            f += drape(B, ["cloth", "cloth", "cape"], s * 2.4, 12.6, 2.0, -2.95, 2.1, 2.7, rows=4, cols=4, fold=0.4, flare_z=0.4, t=0.4, out=-1.0,
                       ov=(0, 0, -1), hem=[0.0, -1.2, 0.4, -1.4])
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "cloth", wide=1.15)
            g += drape(ARM[s], ["cloth", "cape"], s * 6.0 + s * 2.6, 16.0, 10.0, 0.0, 1.4, 2.0, rows=3, cols=3, fold=0.4, flare_z=0.4, t=0.3, ov=(s, 0, 0))
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth", wraps=(4.6, 8.4))))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "cloth", tall=5.4) + dloft(BOOT[s], "sash", [sring(2.4, 2.62, -3.3, 2.66, n=10, p=2.8, cx=s * 2.05),
                                                                                                         sring(3.0, 2.6, -3.1, 2.64, n=10, p=2.8, cx=s * 2.05)])
                               + A.E(BOOT[s], "gem", (s * 2.05, 4.6, -2.9), (0.3, 0.3, 0.2))))


# ====================================================================== 帯 V
@armor("wild_copperwire", "spark", dict(cloth="leather", cape="blackleather", sash="blackleather", plate="steel", bevel="iron", brass="copper", insul="whitecloth", dark="dark"))
class Copperwire:
    """銅線の避雷鎧: 鋼の板に銅線を巻いた鎧。兜の頂に避雷の針、肩に白い碍子が段に並び、胸の板を銅の巻き線が斜めに走る"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = helm(b, "plate", "bevel")
        f += spk(b, "brass", (0, 34.6, 0), (0, 1, 0), 4.0, 0.5, sides=6, rings=2)
        for y in (35.4, 36.4):
            f += dloft(b, "insul", [sring(y, 0.8, -0.8, 0.8, n=8, p=2.0), sring(y + 0.4, 0.8, -0.8, 0.8, n=8, p=2.0)], caps=("insul", "insul"))
        for k in range(6):
            a = math.radians(30 * k - 75)
            f += A.E(b, "brass", (6.0 * math.sin(a), 30.6, -6.0 * math.cos(a) * 0.98), (0.3, 0.3, 0.3))
        f += lume(b, (0, 28.8, -6.35), (2.6, 0.3, 0.15))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "plate", bulk=0.3)
        f += bplate(B, "plate", "bevel", ngon(8.0, 8.2, 8, bottom=0.3), c=(0, 18.8, -3.6), h=0.8, ins=0.6, bow=0.8)
        for k in range(5):
            f += bx(B, "brass", (-3.0 + 1.5 * k, 16.0 + 1.2 * k, -4.45), (1.6, 0.3, 0.2), rot=rot_z(38))
        f += lume(B, (0, 18.8, -4.5), (0.4, 0.8, 0.2))
        f += belt(B, "sash", buckle="brass")
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "cloth", glove="plate")
            g += pauldron(ARM[s], s, "plate", layers=2, big=1.15)
            for k in range(3):
                g += dloft(ARM[s], "insul", [sring(26.0 + 0.6 * k, 0.7 - 0.08 * k, -0.7, 0.7, n=8, p=2.0, cx=s * 7.6), sring(26.3 + 0.6 * k, 0.9 - 0.08 * k, -0.9, 0.9, n=8, p=2.0, cx=s * 7.6)],
                           caps=("insul", "insul"))
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth", knee="plate") + sum(
            (dloft(LEG[s], "brass", [sring(y, 2.5, -2.62, 2.58, n=N, p=3.0, cx=s * 2.05), sring(y + 0.3, 2.5, -2.62, 2.58, n=N, p=3.0, cx=s * 2.05)]) for y in (4.8, 5.6, 9.4, 10.2)), [])))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "cape", toe="plate", heel="plate", tall=6.0) + lume(BOOT[s], (s * 2.05, 1.6, -4.3), (0.3, 0.3, 0.15))))


@armor("wild_soot", "ember", dict(cloth="ashwood", cape="ash", sash="blackleather", plate="basalt", bevel="ash", brass="iron", dark="dark"))
class Soot:
    """煤の外套: 灰の層の煤の黒い外套。煤の面 (下半分を覆う布) と深い頭巾、肩に灰の鱗、裾は焦げてぼろぼろ、細い煤の帯"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = hood(b, "cloth", "cape", tail=22.0, peak=2.0)
        f += bplate(b, "cape", "cape", ngon(8.0, 4.2, 10, bottom=0.6), c=(0, 25.8, -6.0), h=0.4, ins=0.2, bow=1.0)
        for s in (-1, 1):
            f += A.E(b, "gem", (s * 1.4, 28.8, -5.9), (0.35, 0.2, 0.15))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth")
        f += belt(B, "sash", h=0.8)
        for s in (-1, 1):
            f += drape(B, ["cloth", "cloth", "cloth", "cape"], s * 2.4, 13.0, 0.0, 2.95, 2.3, 3.4, rows=5, cols=4, fold=0.6, flare_z=1.8, t=0.4,
                       hem=[0.0, -1.4, 0.6, -1.8])
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "cloth", glove="sash", wide=1.05)
            for k in range(4):
                g += bplate(ARM[s], "plate", "bevel", ngon(1.6, 1.8, 6, bottom=0.6), c=(s * (8.4 - 0.2 * k), 25.0 - 1.1 * k, -1.2 + 0.8 * k), R=M_SIDE(s), h=0.3, ins=0.2, bow=0.3)
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth", wraps=(4.6, 6.0, 7.4))))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "sash", tall=6.4) + lume(BOOT[s], (s * 2.05, 6.0, -3.0), (0.3, 0.3, 0.15))))


@armor("wild_deepwarden", "void", dict(cloth="purplecloth", cape="purplecloth", sash="blackleather", plate="blacksteel", bevel="steel", brass="brass", dark="dark"))
class Deepwarden:
    """深みの番兵鎧: 深みの門を守った番兵の黒い重鎧。閉じた兜に一筋の紫の覗き、胸に門の紋 (二枚の扉の線)、段の草摺、膝の翼"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = helm(b, "plate", "bevel", visor=False)
        f += lume(b, (0, 29.0, -6.3), (6.0, 0.4, 0.15))
        f += ext(b, "plate", [(0, 0), (0.8, 1.6), (0.4, 3.2), (-0.4, 3.2), (-0.8, 1.6)], 0.5, c=(0, 34.0, -1.0))
        for s in (-1, 1):
            f += spk(b, "plate", (s * 5.6, 31.0, 1.0), (s, 0.4, 0.6), 3.0, 0.8, sides=5, rings=1)
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "plate", bulk=0.5)
        f += bplate(B, "plate", "bevel", ngon(9.0, 9.6, 8, bottom=0.3), c=(0, 18.6, -3.8), h=1.0, ins=0.7, bow=0.9)
        for x in (-0.1, 0.1):
            f += bx(B, "lume", (x * 20, 18.6, -4.8), (0.2, 6.4, 0.15))
        f += bx(B, "brass", (0, 21.8, -4.8), (4.0, 0.4, 0.2))
        f += belt(B, "sash", buckle="brass")
        for k in range(3):
            f += dloft(B, "plate", [sring(12.4 - 1.4 * k, 5.0 + 0.2 * k, -3.2 - 0.2 * k, 3.1 + 0.2 * k, n=N, p=3.0),
                                    sring(11.2 - 1.4 * k, 5.2 + 0.2 * k, -3.4 - 0.2 * k, 3.3 + 0.2 * k, n=N, p=3.0)])
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "plate", glove="plate")
            g += pauldron(ARM[s], s, "plate", layers=4, big=1.2, edge="brass")
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "plate", knee="plate")
                               + bplate(LEG[s], "plate", "bevel", ngon(1.4, 2.6, 6), c=(s * 2.05 + s * 2.2, 7.6, -2.0), R=M_SIDE(s), h=0.4, ins=0.2, bow=0.2)))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "plate", cuff="bevel", toe="plate", heel="plate", tall=6.6)
                               + lume(BOOT[s], (s * 2.05, 4.4, -3.1), (0.3, 0.6, 0.15))))


@armor("wild_stargazer", "star", dict(cloth="bluecloth", cape="silver", sash="bluecloth", plate="silver", bevel="silver", brass="gold", dark="dark"))
class Stargazer:
    """星詠みの法衣: 星を詠む者の藍の法衣。頭巾に銀の星図の縫い取り、背に星の輪 (光の粒が並ぶ環) がめぐり、胸に星座の留め、裾は足首まで"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = hood(b, "cloth", "cape", tail=20.0, peak=1.0)
        for p in ((-2.4, 33.0, -4.8), (0.0, 34.0, -3.8), (2.0, 32.4, -5.2), (3.4, 30.0, -5.8)):
            f += lume(b, p, (0.35, 0.35, 0.15))
        f += crv(b, "brass", [(-2.4, 33.0, -5.0), (0.0, 34.0, -4.0), (2.0, 32.4, -5.4), (3.4, 30.0, -6.0)], 0.1, 0.1, sides=3)
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth")
        f += collar(B, "cape", h=2.6, flare=1.2)
        f += belt(B, "brass")
        for p in ((-1.2, 21.0), (0.6, 19.6), (1.6, 17.6), (0.2, 16.0)):
            f += lume(B, (p[0], p[1], -3.15), (0.4, 0.4, 0.15))
        for s in (-1, 1):
            f += drape(B, ["cloth", "cloth", "cloth", "cape"], s * 2.4, 13.0, 0.6, 2.95, 2.3, 3.2, rows=5, cols=4, fold=0.4, flare_z=1.2, t=0.5)
            f += drape(B, ["cloth", "cloth", "cape"], s * 2.4, 12.6, 0.8, -2.95, 2.2, 2.9, rows=4, cols=4, fold=0.3, flare_z=0.6, t=0.5, out=-1.0, ov=(0, 0, -1))
        # 背の星の輪
        R = 6.0
        pts = [(R * math.cos(math.radians(a)), 20.0 + R * math.sin(math.radians(a)), 5.4) for a in range(0, 361, 30)]
        f += crv(B, "brass", pts, 0.22, 0.22, sides=4)
        for a in range(0, 360, 45):
            f += A.E(B, "gem", (R * math.cos(math.radians(a)), 20.0 + R * math.sin(math.radians(a)), 5.4), (0.4, 0.4, 0.4))
        f += crv(B, "brass", [(0, 20.0, 3.0), (0, 20.0, 5.4)], 0.2, 0.2, sides=4)
        out = {B: f}
        for s in (-1, 1):
            out[ARM[s]] = sleeve(ARM[s], s, "cloth", cuff="cape", wide=1.15)
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth")))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "cloth", cuff="cape", toe="plate", tall=5.0) + lume(BOOT[s], (s * 2.05, 4.6, -3.0), (0.3, 0.3, 0.15))))


@armor("wild_cloudknight", "dawn", dict(cloth="bluecloth", cape="cloudwhite", sash="bluecloth", plate="cloudwhite", bevel="gold", brass="gold", dark="dark"))
class Cloudknight:
    """雲上の騎士鎧: 雲の白の板金に金の縁の騎士鎧。兜の両脇に金の翼の前立て、肩に雲の渦の大袖、背に短い白い外套、胸に暁の珠"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = helm(b, "plate", "bevel")
        for s in (-1, 1):
            for k in range(4):
                f += A.feather(b, "brass", (s * 5.4, 30.0 + 0.6 * k, 1.0), (s * (9.0 - 0.6 * k), 33.8 + 0.8 * k, 3.0), 0.7, th=0.3) if hasattr(A, "feather") else \
                    ext(b, "brass", [(0, 0), (0.6, 1.2), (0, 4.0 - 0.4 * k), (-0.6, 1.2)], 0.3, c=(s * 5.6, 30.0 + 0.6 * k, 1.0), rot=rot_z(-s * (50 - 8 * k)))
        f += lume(b, (0, 28.8, -6.35), (2.4, 0.3, 0.15))
        f += A.E(b, "gem", (0, 32.0, -5.6), (0.5, 0.5, 0.3))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "plate", bulk=0.4)
        f += bplate(B, "plate", "bevel", ngon(8.6, 9.0, 8, bottom=0.3), c=(0, 18.8, -3.8), h=0.9, ins=0.7, bow=0.9)
        f += A.E(B, "gem", (0, 20.6, -4.8), (0.9, 0.9, 0.5))
        f += belt(B, "sash", buckle="brass")
        for s in (-1, 1):
            f += drape(B, ["cape", "cape", "cape", "brass"], s * 2.4, 23.6, 8.0, 3.0, 2.4, 3.2, rows=5, cols=4, fold=0.4, flare_z=1.6, t=0.4)
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "cloth", glove="plate")
            g += pauldron(ARM[s], s, "plate", layers=3, big=1.3, edge="bevel")
            pts = [(s * (7.4 + 1.0 * math.cos(math.radians(a))), 24.4 + 1.0 * math.sin(math.radians(a)), -3.6) for a in range(0, 300, 40)]
            g += crv(ARM[s], "brass", pts, 0.14, 0.14, sides=3)
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth", knee="plate")
                               + bplate(LEG[s], "plate", "bevel", ngon(2.6, 4.0, 8), c=(s * 2.05, 10.0, -2.9), h=0.6, ins=0.3, bow=0.6)))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "plate", cuff="bevel", toe="plate", heel="plate", tall=6.6)
                               + lume(BOOT[s], (s * 2.05, 4.4, -3.1), (0.3, 0.5, 0.15))))


# ====================================================================== ヒロイックの一段上の飾り (2026-10-31「ヒロイックの防具はもう一段豪華に」)
# 四式とも: 背に地まで届く外套か長い垂れ、光る継ぎ目、頭の上の飾り。部位ごとの足し分 (骨 → 面) を元の形に重ねる
def _feather(bone, mat, c, ang, L=4.0, w=0.7, th=0.3):
    return ext(bone, mat, [(0, 0), (w, L * 0.3), (0, L), (-w, L * 0.3)], th, c=c, rot=rot_z(ang))


def _fortress(piece):
    if piece == "helmet":
        b = "armorHead"
        f = crv(b, "cloth", [(0, 34.0, 0.0), (0, 37.0, 2.4), (0, 37.0, 5.4), (0, 34.4, 7.8), (0, 30.4, 8.8)], 1.3, 0.5, sides=6)
        for s in (-1, 1):
            f += lume(b, (s * 6.15, 30.0, -1.6), (0.2, 2.6, 0.35), rot=rot_z(s * 12))
            f += spk(b, "bevel", (s * 5.6, 33.0, 0.0), (s * 0.6, 1.0, 0.0), 2.4, 0.55)
        return {b: f}
    if piece == "chestplate":
        B = "armorBody"
        f = drape(B, ["cape", "cape", "cape", "cape", "dark"], 0, 23.6, 1.0, 3.1, 4.2, 5.8, rows=6, cols=6, fold=0.5, flare_z=1.8, t=0.4,
                  hem=[0.0, -1.6, 0.6, -2.2, -0.4, -1.2])
        for s in (-1, 1):
            f += lume(B, (s * 3.7, 18.6, -4.75), (0.25, 4.6, 0.2))
        f += lume(B, (0, 14.4, -4.75), (4.0, 0.25, 0.2))
        out = {B: f}
        for s in (-1, 1):
            g = spk(ARM[s], "bevel", (s * 7.4, 25.8, -1.0), (s * 0.45, 1.0, 0.0), 2.6, 0.55)
            g += spk(ARM[s], "bevel", (s * 7.6, 25.6, 1.4), (s * 0.45, 1.0, 0.2), 2.0, 0.45)
            g += lume(ARM[s], (s * 8.5, 23.6, 0.0), (0.2, 0.3, 4.0))
            out[ARM[s]] = g
        return out
    if piece == "leggings":
        return pair(lambda s: (LEG[s], lume(LEG[s], (s * 2.05, 10.0, -3.55), (1.4, 0.25, 0.15))
                               + bx(LEG[s], "plate", (s * 2.05, 6.4, -2.8), (3.6, 2.8, 0.5), taper=(0.85, 1.0))))
    return pair(lambda s: (BOOT[s], spk(BOOT[s], "bevel", (s * 2.05, 2.0, 3.0), (0, -0.2, 1.0), 1.6, 0.35)
                           + lume(BOOT[s], (s * 2.05, 6.4, -3.1), (2.2, 0.25, 0.15))))


def _hanged(piece):
    if piece == "helmet":
        b = "armorHead"
        f = crv(b, "sash", [(0, 34.8, 0.4), (0.4, 37.6, 0.8), (-0.2, 39.6, 0.4), (0.3, 41.2, 0.0)], 0.55, 0.42, sides=6)
        for k in range(3):
            f += spk(b, "sash", (0.3, 41.0, 0.0), (0.5 * (k - 1), 1.0, 0.3 * (k - 1)), 1.2, 0.2)
        f += lume(b, (0, 26.9, -6.35), (2.8, 0.22, 0.12))
        f += drape(b, ["cape", "cape", "cape", "cloth"], 0, 31.0, 21.0, 6.0, 4.4, 5.6, rows=4, cols=5, fold=0.5, flare_z=1.2, t=0.35,
                   hem=[0.0, -1.0, 0.4, -1.4, -0.2])
        return {b: f}
    if piece == "chestplate":
        B = "armorBody"
        f = drape(B, ["cloth", "cloth", "cape", "cape", "cape", "cape"], 0, 23.8, -3.0, 3.2, 4.4, 6.2, rows=7, cols=6, fold=0.7, flare_z=1.8, t=0.35,
                  hem=[0.0, -2.0, 0.8, -2.6, -0.4, -1.8])
        for k in range(4):
            f += A.E(B, "gem", ((-1) ** k * 2.6, 14.3 + 2.6 * k, -3.25), (0.4, 0.4, 0.3))
        for x, y1 in ((-1.6, 6.0), (1.2, 4.2), (3.0, 7.4)):
            f += crv(B, "sash", [(x, 12.6, -3.2), (x + 0.3, (12.6 + y1) / 2, -3.6), (x - 0.2, y1, -3.4)], 0.2, 0.14, sides=4)
        return {B: f}
    if piece == "leggings":
        return pair(lambda s: (LEG[s], crv(LEG[s], "sash", [(s * 2.05 + s * 1.4, 8.4, -1.0), (s * 2.05 + s * 2.2, 5.0, -1.4), (s * 2.05 + s * 1.8, 1.4, -1.0)],
                                           0.18, 0.12, sides=4)))
    return pair(lambda s: (BOOT[s], crv(BOOT[s], "sash", [(s * 2.05 + s * 2.6, 2.8, 0.0), (s * 2.05 + s * 3.4, 1.2, 0.6), (s * 2.05 + s * 3.0, 0.2, 1.4)],
                                        0.16, 0.1, sides=4)))


def _stargazer(piece):
    if piece == "helmet":
        b = "armorHead"
        R = 4.6
        pts = [(R * math.cos(math.radians(a)), 37.4, R * math.sin(math.radians(a))) for a in range(0, 361, 30)]
        f = crv(b, "brass", pts, 0.16, 0.16, sides=4)
        for a in range(15, 360, 60):
            f += A.E(b, "gem", (R * math.cos(math.radians(a)), 37.4, R * math.sin(math.radians(a))), (0.4, 0.4, 0.4))
        f += ext(b, "brass", [(-1.4, 0.6), (-0.6, -0.8), (0.6, -1.2), (1.4, -0.4), (0.6, -0.4), (-0.2, 0.0)], 0.3, c=(0, 33.0, -5.4))
        return {b: f}
    if piece == "chestplate":
        B = "armorBody"
        f = drape(B, ["cloth", "cloth", "cloth", "cloth", "cape"], 0, 23.8, -2.0, 3.2, 4.2, 6.4, rows=6, cols=6, fold=0.4, flare_z=2.0, t=0.4)
        for p in ((-2.8, 18.0), (1.6, 16.2), (-0.6, 12.6), (3.2, 10.4), (-3.4, 8.2), (0.8, 6.0), (2.6, 2.6), (-2.0, 1.4)):
            f += lume(B, (p[0], p[1], 4.4 + (23.8 - p[1]) * 0.075), (0.35, 0.35, 0.15))
        f += crv(B, "brass", [(0, 23.6, -3.4), (0, 18.0, -3.35), (0, 13.0, -3.35)], 0.14, 0.14, sides=4)
        out = {B: f}
        for s in (-1, 1):
            out[ARM[s]] = drape(ARM[s], ["cape", "cape", "brass"], s * 6.0, 24.6, 20.6, 0.0, 2.6, 3.2, rows=3, cols=4, fold=0.2, flare_z=0.4, t=0.3,
                                ov=(s, 0, 0))
        return out
    if piece == "leggings":
        return pair(lambda s: (LEG[s], lume(LEG[s], (s * 2.05 + s * 0.6, 6.0, -2.4), (0.3, 0.3, 0.15))
                               + lume(LEG[s], (s * 2.05 - s * 0.8, 2.6, -2.4), (0.3, 0.3, 0.15))))
    return pair(lambda s: (BOOT[s], ext(BOOT[s], "brass", [(-0.9, 0.3), (-0.3, -0.6), (0.5, -0.8), (0.9, -0.2), (0.4, -0.2), (-0.1, 0.1)], 0.25,
                                        c=(s * 2.05, 2.2, -3.6))))


def _cloudknight(piece):
    if piece == "helmet":
        b = "armorHead"
        R = 5.6
        pts = [(R * math.cos(math.radians(a)), 29.0 + R * math.sin(math.radians(a)), 6.6) for a in range(0, 361, 24)]
        f = crv(b, "brass", pts, 0.2, 0.2, sides=4)
        for a in range(0, 360, 45):
            f += spk(b, "brass", (R * math.cos(math.radians(a)), 29.0 + R * math.sin(math.radians(a)), 6.6),
                     (math.cos(math.radians(a)), math.sin(math.radians(a)), 0.0), 1.4, 0.22)
        f += crv(b, "cape", [(0, 34.6, -1.0), (0, 37.0, 1.0), (0, 37.2, 3.6), (0, 35.0, 6.0)], 0.9, 0.4, sides=6)
        return {b: f}
    if piece == "chestplate":
        B = "armorBody"
        f = drape(B, ["cape", "cape", "cape", "cape", "cape", "brass"], 0, 23.8, -1.4, 3.3, 4.4, 6.4, rows=7, cols=6, fold=0.5, flare_z=2.4, t=0.4,
                  hem=[0.0, -0.6, 0.2, -0.8, 0.0, -0.4])
        for a in range(0, 360, 45):
            r = math.radians(a)
            f += spk(B, "brass", (0.9 * math.cos(r), 20.6 + 0.9 * math.sin(r), -4.85), (math.cos(r), math.sin(r), 0.0), 1.8 if a % 90 == 0 else 1.1, 0.2)
        out = {B: f}
        for s in (-1, 1):
            g = []
            for k in range(3):
                g += _feather(ARM[s], "brass", (s * 8.4, 25.0, -0.8 + 1.2 * k), -s * (35 + 15 * k), L=3.6 - 0.5 * k, w=0.6)
            out[ARM[s]] = g
        return out
    if piece == "leggings":
        return pair(lambda s: (LEG[s], bx(LEG[s], "brass", (s * 2.05, 11.6, -2.8), (4.0, 0.4, 0.4))))
    return pair(lambda s: (BOOT[s], _feather(BOOT[s], "brass", (s * 2.05 + s * 2.7, 4.6, 0.6), -s * 60, L=3.0, w=0.55)
                           + _feather(BOOT[s], "brass", (s * 2.05 + s * 2.7, 4.2, 1.4), -s * 75, L=2.4, w=0.5)))


HEROIC = {"wild_fortress": _fortress, "wild_hanged": _hanged, "wild_stargazer": _stargazer, "wild_cloudknight": _cloudknight}


def _plus(fn, extra, piece):
    def wrapped():
        d = fn()
        for bone, fs in extra(piece).items():
            d[bone] = list(d.get(bone, [])) + [f for f in fs if f is not None]
        return d
    return wrapped


# ====================================================================== 塗り (一式ごとの素材の表)
S_EXTRA = {"oilskin": ("leather", np.array([(70, 54, 18), (110, 86, 28), (150, 120, 40), (186, 154, 60), (214, 186, 92)]))}
CUR = {}


def paint(faces, size, P, N_, M, L, o, lv=0):
    item = CUR["set"]
    D, K, LU, LV, CW, CH, INS = edge_fields(faces, size)
    H = W = size
    yy, xx = np.mgrid[0:H, 0:W]
    fi = np.where(M >= 0, M, 0)
    owned = M >= 0
    mats = np.array([f.mat for f in faces])[fi]
    bay = BAYER[yy % 4, xx % 4] - 0.5
    lit = N_ @ LIGHT
    edge = (D < 1.05) & INS
    convex = edge & (K > 24)
    concave = edge & (K < -24)
    X, Y, Z = L[..., 0], L[..., 1], L[..., 2]
    n_lo = vnoise(L * 0.09 + 3, 1)
    n_mid = vnoise(L * 0.3 + 11, 2)
    n_hi = vnoise(L * 0.9 + 5, 7)
    pit = hash1(xx * 7 + yy * 13 + fi * 3, 5)
    col = np.zeros((H, W, 3))
    glow = np.zeros((H, W), bool)

    def put(mask, c):
        nonlocal col
        col = np.where(mask[..., None], c, col)

    def shaded(pal, base, lw=0.30, nw=0.10, ed=True):
        v = base + lw * lit + nw * (n_lo - 0.5) + 0.06 * (n_mid - 0.5) + 0.05 * bay
        idx = np.floor(v * len(pal)).astype(int)
        if ed:
            idx = idx + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave
        return idx

    gp = PW.GLOWS[item["glow"]]
    TM = PW.TRIM[item["rarity"]]
    for name in set(mats[owned].tolist()):
        mask = (mats == name) & owned
        if name in ("gem", "lume"):
            put(mask, ramp(gp, np.minimum(0.25 + 0.5 * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.06 * bay, 0.9)))
            glow |= mask
            continue
        if name == "trim":
            put(mask, pick(TM, shaded(TM, 0.56, 0.30) + (mask & convex)))
            continue
        key = item["mats"].get(name, "steel")
        kind, pal = S_EXTRA[key] if key in S_EXTRA else PW.S[key]
        if kind == "metal":
            hammer = hash1(np.floor(X * 0.8) * 7 + np.floor(Y * 0.8) * 3 + np.floor(Z * 0.8) * 11 + fi, 4) * 0.10 - 0.05
            idx = shaded(pal, 0.52 + hammer, 0.36) - (pit < 0.02) + 2 * (convex & (lit > -0.2))
        elif kind == "cloth":
            thread = (LU % 3 == 0) & (n_mid > 0.55)
            fold = np.sin(X * 1.7 + Z * 1.3 + 2.2 * n_lo) > 0.55
            idx = shaded(pal, 0.50, 0.34) - thread - fold * (n_mid > 0.45)
        elif kind == "leather":
            seam = np.abs(((X * 0.4 + Y * 0.9 + Z * 0.4) % 1.8) - 0.9) < 0.12
            idx = shaded(pal, 0.52, 0.32) - seam * (n_mid > 0.4) + (n_hi > 0.88)
        elif kind == "fur":
            strand_ = np.sin(X * 4.0 + Z * 4.0 + 5 * n_hi) > 0.3
            idx = shaded(pal, 0.52, 0.30, 0.18) - strand_ + (n_hi > 0.85)
        elif kind == "rope":
            twist = (np.floor(Y * 1.4 + X * 0.9 + Z * 0.9) % 2) == 0
            idx = shaded(pal, 0.55, 0.28) - twist
        elif kind == "crystal":
            facet = hash1(np.floor(X * 1.2) + np.floor(Y * 0.8) * 7 + np.floor(Z * 1.2) * 13 + fi, 4)
            idx = shaded(pal, 0.5 + 0.3 * facet, 0.40, 0.04) + (convex & (lit > 0))
        elif kind == "stone":
            idx = shaded(pal, 0.52, 0.28, 0.2) - 2 * ((n_hi > 0.8) & (n_mid > 0.5))
        elif kind == "wood":
            idx = shaded(pal, 0.52, 0.28) - (np.sin(Y * 0.9 + 9 * vnoise(L * 0.25 + 7, 3)) > 0.55)
        elif kind == "bone":
            idx = shaded(pal, 0.58, 0.30) - (pit < 0.05)
        else:
            idx = np.floor(1.2 + 1.2 * (0.5 + 0.5 * lit) + 0.8 * bay).astype(int)
        put(mask, pick(pal, idx))
    # 強化: +5 で縁 (bevel) に光の破線、+10 で縁の角まで光る
    if lv >= 1:
        along_u = CW >= CH
        dash = (np.where(along_u, LU, LV) % 8) < (5 if lv == 1 else 8)
        line = np.where(along_u, (LV == CH // 2) & (CH >= 3), (LU == CW // 2) & (CW >= 3)) & INS & dash
        tl = (mats == "bevel") & line & (np.maximum(CW, CH) >= 6)
        put(tl, ramp(gp, 0.5 + 0.4 * hash1(LU + LV * 3, 2)))
        glow |= tl
    if lv >= 2:
        te = np.isin(mats, ["bevel", "brass"]) & convex & INS
        put(te, ramp(gp, 0.55 + 0.3 * hash1(LU * 5 + LV, 3)))
        glow |= te
    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl_ = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl_ & owned


A.paint = paint


def _lit(fn, piece):
    """光る画素の無い部位 (GeckoLib は空の glowmask で落ちる) に、目立たない小さな光の鋲を一つ足す"""
    def wrapped():
        d = fn()
        if any(f.mat in ("gem", "lume") for fs in d.values() for f in fs if f is not None):
            return d
        bone = next(iter(d))
        fs = [f for f in d[bone] if f is not None]
        lo = min(float(f.verts[:, 2].min()) for f in fs)
        cx = float(np.mean([f.verts[:, 0].mean() for f in fs]))
        cy = float(np.mean([f.verts[:, 1].mean() for f in fs]))
        d[bone] = fs + lume(bone, (cx, cy, lo - 0.1), (0.3, 0.3, 0.15))
        return d
    return wrapped


def build_set(sid):
    cfg = SETS[sid]
    row = next(a for a in SPEC.ARMORS if a["id"] == sid)
    cls = cfg["fns"]
    CUR["set"] = dict(mats=cfg["mats"], glow=cfg["glow"], rarity=row["tier"])
    A.OUT = ROOT / f"docs/review/roughs_wild/armor_{sid}"
    A.RAW = ROOT / f"docs/review/roughs_wild/raw/armor_{sid}"
    A.NAMES["a"] = row["ja"]
    A.GLOWK["a"] = "wild_" + sid
    A.CLOTH_OF["a"] = "canvas"
    A.DESC["a"] = (cls.__doc__ or "").split(":", 1)[-1].strip()
    A.scene.GLOW_RGB["ad2_wild_" + sid] = tuple(int(c) for c in PW.GLOWS[cfg["glow"]][-2])
    A.PALS["G_wild_" + sid] = PW.GLOWS[cfg["glow"]]
    pieces = ((cls.helmet, "helmet"), (cls.chestplate, "chestplate"), (cls.leggings, "leggings"), (cls.boots, "boots"))
    if sid in HEROIC:
        pieces = tuple((_plus(fn, HEROIC[sid], piece), piece) for fn, piece in pieces)
    A.BUILD["a"] = tuple(_lit(fn, piece) for fn, piece in pieces)
    sys.argv = [sys.argv[0], "a"]
    A.main()


def main(ids):
    for sid in ids or list(SETS):
        build_set(sid)


if __name__ == "__main__":
    main([a for a in sys.argv[1:] if not a.startswith("-")])
