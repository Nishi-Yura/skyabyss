"""序盤の構造物 4 つ (野営地・鐘の宿り・巨大な足跡・巨神の骨。docs/design/17 の 4 章) のラフ。3 案ずつ。

ブロックの構造物なので、絵は「ブロックの配置」をそのまま描く (上から・斜め・人の目線)。
採用した案は、そのまま `--install` でゲームの部品 (src/main/resources/data/skyabyss/opening/<名前>.json) になる。
ゲーム側 (opening/site/Blueprint.java) はこの JSON を読んで、地形に合わせて置く。ラフと実物が同じブロックの配置になる。

    python tools/rough/opening_sites.py                   # docs/review/opening_site_roughs/ に 3 案の比較を書く
    python tools/rough/opening_sites.py --install          # 採用案 (ADOPTED) を src の JSON に書く
    python tools/rough/opening_sites.py --option camp=b    # 採用案を変えて --install (何度でも)

座標: x = 東、z = 南、y = 上。地面の上面は y=-1、構造物の最初の段が y=0。中心は (0, 0, 0)。
"""
from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/structure"))
sys.path.insert(0, str(ROOT / "tools/rough"))
import render as srender  # noqa: E402
from sbd import Dump  # noqa: E402
from voxel import font  # noqa: E402

_ORIG_COLORS = srender._colors

OUT = ROOT / "docs/review/opening_site_roughs"
DATA = ROOT / "src/main/resources/data/skyabyss/opening"

# 採用案 (17 の 9 章の推奨に合わせて、ラフを見て選んだもの。理由は docs/review/opening_site_roughs/README.md)
ADOPTED = {"camp": "a", "shelter": "a", "footprint": "a", "ribs": "b"}

M = "minecraft:"


# ---------------------------------------------------------------- 格子
class Grid:
    def __init__(self):
        self.b: dict[tuple[int, int, int], str] = {}
        self.marks: dict[str, tuple[int, int, int]] = {}
        # 地形の整え方 (ゲーム側が読む): 水平の範囲 (x0, z0, x1, z1)、整えるか、水を張るか
        self.meta: dict = {}

    def set(self, x, y, z, s):
        self.b[(x, y, z)] = s if ":" in s else M + s

    def get(self, x, y, z):
        return self.b.get((x, y, z))

    def fill(self, x0, y0, z0, x1, y1, z1, s):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, s)

    def mark(self, name, x, y, z):
        self.marks[name] = (x, y, z)

    def bounds(self):
        xs = [k[0] for k in self.b]
        ys = [k[1] for k in self.b]
        zs = [k[2] for k in self.b]
        return min(xs), min(ys), min(zs), max(xs), max(ys), max(zs)


# ---------------------------------------------------------------- 野営地 (9x9。x, z は -4..4)
def camp_a(g: Grid, rng):
    """焚き火を囲む輪: 消えた焚き火と石の輪、丸太の腰かけ、倒れた荷車、開いた寝袋、木箱、切り株の上の帳面"""
    g.meta = {"rect": [-5, -5, 5, 5], "level": True}
    # 焼けた地面
    for x in range(-2, 3):
        for z in range(-2, 3):
            if abs(x) + abs(z) <= 2:
                g.set(x, -1, z, "coarse_dirt" if (x + z) % 2 else "podzol")
    g.set(0, 0, 0, "campfire[lit=false,facing=north,signal_fire=false,waterlogged=false]")
    ring = [(-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (1, 1), (0, 1)]
    for i, (x, z) in enumerate(ring):
        if i in (2, 6):
            continue  # 抜けた石
        g.set(x, 0, z, "mossy_cobblestone" if i % 3 == 0 else "cobblestone")
    g.set(1, 0, -1, "cobblestone_slab[type=bottom]")  # ずれた石
    # 丸太の腰かけ (倒れている)
    g.set(-3, 0, 2, "spruce_log[axis=x]")
    g.set(-2, 0, 2, "spruce_log[axis=x]")
    g.set(1, 0, 3, "stripped_spruce_log[axis=z]")
    g.set(1, 0, 4, "stripped_spruce_log[axis=z]")
    # 倒れた荷車 (東側。横倒しで、車輪が空を向く)
    for z in range(-2, 2):
        g.set(3, 0, z, "spruce_planks")
        g.set(3, 1, z, "spruce_planks")
    for z in range(-2, 2):
        g.set(4, 0, z, "spruce_slab[type=bottom]")
    g.set(2, 0, -2, "spruce_stairs[facing=east,half=bottom,shape=straight]")
    g.set(2, 0, 1, "spruce_stairs[facing=east,half=bottom,shape=straight]")
    # 車輪 (地面に立てかけて、輪の形)
    for dx, dy in [(-1, 0), (0, 0), (1, 0), (-1, 1), (1, 1), (-1, 2), (0, 2), (1, 2)]:
        g.set(3 + dx, dy, 3, "dark_oak_planks")
    g.set(3, 1, 3, "stripped_dark_oak_log[axis=z]")
    g.set(4, 0, 2, "dark_oak_slab[type=bottom]")  # 折れた輻
    # 開いた寝袋 (西側。ベッドは置かない): 灰の布 2 枚と赤の枕
    g.set(-3, 0, -2, "light_gray_carpet")
    g.set(-3, 0, -3, "light_gray_carpet")
    g.set(-3, 0, -4, "red_carpet")
    g.set(-4, 0, -2, "gray_carpet")
    # 木箱 (北)
    g.set(0, 0, -3, "barrel[facing=up,open=false]")
    g.mark("barrel", 0, 0, -3)
    # 切り株の上の古い帳面
    g.set(2, 0, -4, "stripped_spruce_log[axis=y]")
    g.mark("notebook", 2, 1, -4)
    g.mark("stand", 0, 0, 3)


def camp_b(g: Grid, rng):
    """崩れた天幕: 倒れた柱と裂けた布、脇に消えた焚き火、木箱が二つ"""
    g.meta = {"rect": [-5, -5, 5, 5], "level": True}
    # 倒れた天幕 (白い布が地面に広がり、折れた柱が突き出る)
    cloth = [(-3, -3), (-2, -3), (-1, -3), (0, -3), (-3, -2), (-2, -2), (-1, -2), (0, -2), (-2, -1), (-1, -1), (0, -1), (-1, 0), (0, 0)]
    for x, z in cloth:
        g.set(x, 0, z, "white_carpet")
    for x, z in [(-3, -3), (0, -3), (-3, -1)]:
        g.set(x, 0, z, "oak_fence")
    g.set(-3, 1, -3, "oak_fence")
    g.set(0, 1, -3, "oak_fence")
    g.set(-1, 1, -2, "white_carpet")
    g.set(-2, 1, -2, "light_gray_carpet")
    g.set(-3, 0, 0, "oak_fence")
    g.set(-3, 0, 1, "oak_fence")  # 横に倒れた柱 (連結は無視)
    # 寝袋 (天幕の下から覗く)
    g.set(-1, 0, -1, "red_carpet")
    g.set(-1, 0, 0, "light_gray_carpet")
    # 消えた焚き火 (東寄り)
    g.set(2, 0, 1, "campfire[lit=false,facing=north,signal_fire=false,waterlogged=false]")
    for x, z in [(1, 0), (2, 0), (3, 0), (1, 1), (3, 1), (1, 2), (2, 2), (3, 2)]:
        g.set(x, 0, z, "cobblestone" if (x + z) % 2 else "mossy_cobblestone")
    g.set(3, 0, 1, "air")
    g.set(1, 1, 2, "stone_button[face=floor,facing=north,powered=false]")
    for x in range(1, 4):
        for z in range(0, 3):
            g.set(x, -1, z, "coarse_dirt")
    # 荷が二つ
    g.set(3, 0, -2, "barrel[facing=up,open=false]")
    g.set(4, 0, -2, "barrel[facing=north,open=false]")
    g.mark("barrel", 3, 0, -2)
    g.set(0, 0, 3, "spruce_log[axis=x]")
    g.set(1, 0, 3, "spruce_log[axis=x]")
    g.set(-2, 0, 3, "hay_block[axis=y]")
    # 帳面は丸太の上
    g.mark("notebook", 0, 1, 3)
    g.mark("stand", 1, 0, 4)


def camp_c(g: Grid, rng):
    """荷車の陰: 横倒しの荷車を風よけに、その陰で消えた焚き火と寝袋"""
    g.meta = {"rect": [-5, -5, 5, 5], "level": True}
    # 荷車 (北に長く、横倒し。高さ 3)
    for x in range(-3, 4):
        for y in range(0, 3):
            g.set(x, y, -2, "spruce_planks" if (x + y) % 3 else "oak_planks")
    for x in (-3, 3):
        g.set(x, 0, -3, "spruce_slab[type=bottom]")
        g.set(x, 1, -3, "spruce_fence")
    g.set(-4, 0, -2, "spruce_stairs[facing=west,half=bottom,shape=straight]")
    # 車輪 (2 つ。倒れた側の外)
    for cx in (-2, 2):
        for dx, dy in [(-1, 0), (0, 0), (1, 0), (-1, 1), (1, 1), (-1, 2), (0, 2), (1, 2)]:
            g.set(cx + dx, dy, -1, "dark_oak_planks")
        g.set(cx, 1, -1, "stripped_dark_oak_log[axis=z]")
    # 陰の焚き火 (消えている)
    g.set(0, 0, 1, "campfire[lit=false,facing=north,signal_fire=false,waterlogged=false]")
    for x, z in [(-1, 0), (0, 0), (1, 0), (-1, 1), (1, 1), (-1, 2), (0, 2), (1, 2)]:
        g.set(x, 0, z, "stone" if (x + z) % 2 else "cobblestone")
    g.set(0, -1, 1, "coarse_dirt")
    # 寝袋 (開いたまま、焚き火の脇)
    g.set(-3, 0, 1, "light_gray_carpet")
    g.set(-3, 0, 2, "light_gray_carpet")
    g.set(-3, 0, 3, "red_carpet")
    # 荷
    g.set(3, 0, 1, "barrel[facing=up,open=false]")
    g.mark("barrel", 3, 0, 1)
    g.set(3, 0, 2, "hay_block[axis=y]")
    # 帳面は焚き火の前の石の上
    g.set(2, 0, 3, "smooth_stone_slab[type=bottom]")
    g.mark("notebook", 2, 0, 3)  # 石の板の上 (板の天面は 0.5)
    g.mark("stand", 0, 0, 4)


# ---------------------------------------------------------------- 鐘の宿り (17 x 17 ほど。小屋は北、庭は南。扉は南向き)
def _gable_roof(g, x0, x1, z0, z1, y, block="spruce_stairs", cap="spruce_slab[type=bottom]"):
    """南北に勾配のある切妻 (棟が東西)。z0 が北端、z1 が南端。y は軒の高さ"""
    n = (z1 - z0) // 2
    for i in range(n + 1):
        zn, zs = z0 - 1 + i, z1 + 1 - i
        yy = y + i
        for x in range(x0 - 1, x1 + 2):
            g.set(x, yy, zn, f"{block}[facing=south,half=bottom,shape=straight]")
            g.set(x, yy, zs, f"{block}[facing=north,half=bottom,shape=straight]")
        if zn + 1 < zs - 0:
            pass
    zc = (z0 + z1) // 2
    # 棟
    top = y + n
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1 + n, z1 + 2 - n):
            g.set(x, top, z, cap if False else "spruce_planks")


def shelter_a(g: Grid, rng):
    """小屋と柵の庭: 北に小屋 (石の土台・唐檜の壁・切妻)、南に柵の庭。鐘は庭の絞首台形の柱、煙の焚き火はわら束の上"""
    g.meta = {"rect": [-8, -9, 8, 10], "level": True}
    x0, x1, z0, z1 = -3, 3, -7, -2
    # 土台と床
    g.fill(x0, -1, z0, x1, -1, z1, "stone_bricks")
    g.fill(x0, 0, z0, x1, 0, z1, "spruce_planks")
    # 壁
    for y in (1, 2, 3):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                g.set(x, y, z, "spruce_planks")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                g.set(x, y, z, "spruce_planks")
    for y in (0, 1, 2, 3):
        for x, z in [(x0, z0), (x1, z0), (x0, z1), (x1, z1)]:
            g.set(x, y, z, "stripped_spruce_log[axis=y]")
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            g.set(x, 1, z, "cobblestone")
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            g.set(x, 1, z, "cobblestone")
    for x, z in [(x0, z0), (x1, z0), (x0, z1), (x1, z1)]:
        g.set(x, 1, z, "stripped_spruce_log[axis=y]")
    # 戸 (南の壁の中央) と窓 (窓は壁を抜いた開口に鎧戸)
    g.set(0, 1, z1, "spruce_door[facing=south,half=lower,hinge=left,open=false,powered=false]")
    g.set(0, 2, z1, "spruce_door[facing=south,half=upper,hinge=left,open=false,powered=false]")
    for x in (-2, 2):
        g.set(x, 2, z1, "glass_pane")
    for z in (-5, -4):
        g.set(x0, 2, z, "glass_pane")
        g.set(x1, 2, z, "glass_pane")
    # 屋根 (東西に棟。軒を出す)
    for i, z in enumerate((z1 + 1, z1, z1 - 1)):
        pass
    # 北側の斜面 (南向きの階段) と南側 (北向きの階段)
    zs_list = [(z0 - 1, z1 + 1), (z0, z1), (z0 + 1, z1 - 1)]
    for i, (zn, zs) in enumerate(zs_list):
        y = 4 + i
        for x in range(x0 - 1, x1 + 2):
            g.set(x, y, zn, "spruce_stairs[facing=south,half=bottom,shape=straight]")
            g.set(x, y, zs, "spruce_stairs[facing=north,half=bottom,shape=straight]")
    for x in range(x0 - 1, x1 + 2):
        g.fill(x, 4, z0 + 1, x, 4, z1 - 1, "spruce_planks")
        g.fill(x, 5, z0 + 2, x, 5, z1 - 2, "spruce_planks")
        g.set(x, 6, (z0 + z1) // 2, "spruce_slab[type=bottom]")
        g.set(x, 6, (z0 + z1) // 2 + 1, "spruce_slab[type=bottom]")
    # 切妻の妻壁 (東西)
    for x in (x0, x1):
        for z in range(z0 + 1, z1):
            g.set(x, 4, z, "spruce_planks")
        for z in range(z0 + 2, z1 - 1):
            g.set(x, 5, z, "spruce_planks")
    # 中: 書見台、本棚、吊り下げ灯、樽
    g.set(-2, 1, -6, "bookshelf")
    g.set(-1, 1, -6, "bookshelf")
    g.set(-2, 2, -6, "bookshelf")
    g.set(1, 1, -6, "lectern[facing=south,has_book=false,powered=false]")
    g.set(2, 1, -6, "barrel[facing=up,open=false]")
    g.set(0, 3, -4, "lantern[hanging=true,waterlogged=false]")
    g.set(0, 4, -4, "chain[axis=y,waterlogged=false]") if False else None
    # 庭 (z -1 .. 8)。柵と門
    for z in range(-1, 9):
        g.set(-6, 0, z, "spruce_fence")
        g.set(6, 0, z, "spruce_fence")
    for x in range(-6, 7):
        g.set(x, 0, 8, "spruce_fence")
    g.set(0, 0, 8, "spruce_fence_gate[facing=south,in_wall=false,open=false,powered=false]")
    g.set(-6, 0, -1, "spruce_fence")
    # 庭の小径
    for z in range(-1, 8):
        g.set(0, -1, z, "dirt_path")
        if z % 3 == 0:
            g.set(1 if z % 2 else -1, -1, z, "dirt_path")
    g.set(0, -1, 8, "dirt_path")
    # 鐘 (絞首台形の柱): 柱は丸太、梁は丸太、鐘は梁の下
    for y in range(0, 4):
        g.set(-3, y, 4, "stripped_spruce_log[axis=y]")
    g.set(-2, 3, 4, "stripped_spruce_log[axis=x]")
    g.set(-1, 3, 4, "stripped_spruce_log[axis=x]")
    g.set(-1, 2, 4, "bell[attachment=ceiling,facing=north,powered=false]")
    g.mark("bell", -1, 2, 4)
    # 煙の出る焚き火 (わら束の上)
    g.set(4, 0, 3, "hay_block[axis=y]")
    g.set(4, 1, 3, "campfire[lit=true,facing=west,signal_fire=true,waterlogged=false]")
    g.mark("smoke", 4, 1, 3)
    # 薪と樽
    for y in range(0, 2):
        g.set(4, y, -1, "spruce_log[axis=z]")
        g.set(5, y, -1, "spruce_log[axis=z]")
    g.set(5, 0, 5, "barrel[facing=up,open=false]")
    g.set(-5, 0, 6, "hay_block[axis=y]")
    g.set(-5, 1, 6, "hay_block[axis=y]") if False else None
    g.mark("stand", 0, 0, 5)
    g.mark("door", 0, 0, 0)


def shelter_b(g: Grid, rec):
    """円い石の小屋: 半径 4 の石積みの丸い小屋に、わらの屋根。鐘は屋根の上の小さな梁。庭は低い石垣"""
    g.meta = {"rect": [-8, -8, 8, 10], "level": True}
    cx, cz, R = 0, -3, 4
    cells = [(x, z) for x in range(-R - 1, R + 2) for z in range(-R - 1, R + 2)]
    inside = lambda x, z: (x - cx) ** 2 + (z - cz) ** 2 <= R * R + 1
    wall = lambda x, z: inside(x, z) and not ((x - cx) ** 2 + (z - cz) ** 2 <= (R - 1) ** 2 + 0.2)
    for x, z in cells:
        if inside(x, z):
            g.set(x, -1, z, "stone_bricks")
            g.set(x, 0, z, "spruce_planks")
        if wall(x, z):
            for y in (1, 2, 3):
                g.set(x, y, z, "mossy_stone_bricks" if (x + z + y) % 5 == 0 else "stone_bricks")
    # 扉と窓
    dz = cz + R
    g.set(0, 1, dz, "spruce_door[facing=south,half=lower,hinge=left,open=false,powered=false]")
    g.set(0, 2, dz, "spruce_door[facing=south,half=upper,hinge=left,open=false,powered=false]")
    g.set(0, 3, dz, "stone_bricks")
    g.set(-R, 2, cz, "glass_pane")
    g.set(R, 2, cz, "glass_pane")
    # わらの円錐屋根
    for i, r in enumerate([R + 1, R, R - 1, R - 2, R - 3, 1]):
        y = 4 + i
        for x, z in cells:
            d2 = (x - cx) ** 2 + (z - cz) ** 2
            if d2 <= r * r + 1 and (i == 5 or d2 > max(0, r - 1) ** 2 - 1 or True):
                g.set(x, y, z, "hay_block[axis=y]")
    g.set(0, 10, cz, "spruce_fence")
    g.set(0, 11, cz, "spruce_fence")
    # 屋根の上の鐘
    g.set(0, 12, cz, "spruce_fence")
    g.set(0, 13, cz, "spruce_slab[type=bottom]")
    g.set(0, 12, cz + 1, "air")
    g.set(0, 12, cz, "bell[attachment=floor,facing=north,powered=false]")
    g.mark("bell", 0, 12, cz)
    # 内: 書見台
    g.set(0, 1, cz - 2, "lectern[facing=south,has_book=false,powered=false]")
    g.set(-2, 1, cz - 1, "barrel[facing=up,open=false]")
    g.set(2, 1, cz - 1, "bookshelf")
    g.set(0, 3, cz, "lantern[hanging=true,waterlogged=false]")
    # 庭: 低い石垣
    for x in range(-6, 7):
        g.set(x, 0, 8, "cobblestone_wall")
    for z in range(2, 9):
        g.set(-6, 0, z, "cobblestone_wall")
        g.set(6, 0, z, "cobblestone_wall")
    g.set(0, 0, 8, "air")
    for z in range(dz + 1, 9):
        g.set(0, -1, z, "dirt_path")
    # 煙の焚き火
    g.set(3, 0, 5, "hay_block[axis=y]")
    g.set(3, 1, 5, "campfire[lit=true,facing=west,signal_fire=true,waterlogged=false]")
    g.mark("smoke", 3, 1, 5)
    g.set(-4, 0, 5, "spruce_log[axis=x]")
    g.set(-3, 0, 5, "spruce_log[axis=x]")
    g.mark("stand", 0, 0, 7)
    g.mark("door", 0, 0, dz + 1)


def shelter_c(g: Grid, rec):
    """鐘楼付き: 小屋の脇に、四本柱の低い鐘楼。庭は柵と植え込み。鐘が遠くからも見える"""
    g.meta = {"rect": [-9, -9, 9, 10], "level": True}
    x0, x1, z0, z1 = -6, 0, -7, -2
    g.fill(x0, -1, z0, x1, -1, z1, "cobblestone")
    g.fill(x0, 0, z0, x1, 0, z1, "spruce_planks")
    for y in (1, 2, 3):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                g.set(x, y, z, "dark_oak_planks" if y != 1 else "stone_bricks")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                g.set(x, y, z, "dark_oak_planks" if y != 1 else "stone_bricks")
    for x, z in [(x0, z0), (x1, z0), (x0, z1), (x1, z1)]:
        for y in (0, 1, 2, 3):
            g.set(x, y, z, "stripped_dark_oak_log[axis=y]")
    g.set(-3, 1, z1, "spruce_door[facing=south,half=lower,hinge=left,open=false,powered=false]")
    g.set(-3, 2, z1, "spruce_door[facing=south,half=upper,hinge=left,open=false,powered=false]")
    g.set(x0, 2, -5, "glass_pane")
    g.set(x1, 2, -5, "glass_pane")
    # 片流れの屋根 (南へ下がる)
    for i, z in enumerate(range(z0 - 1, z1 + 2)):
        y = 6 - min(i, 4)
        for x in range(x0 - 1, x1 + 2):
            g.set(x, 4 + (z1 + 1 - z) // 2, z, "dark_oak_stairs[facing=south,half=bottom,shape=straight]")
    g.set(-5, 1, -6, "lectern[facing=south,has_book=false,powered=false]")
    g.set(-1, 1, -6, "bookshelf")
    g.set(-3, 3, -4, "lantern[hanging=true,waterlogged=false]")
    # 鐘楼 (東に 3x3 の四本柱、高さ 6、四角錐の屋根)
    bx, bz = 4, -2
    for dx in (-1, 1):
        for dz in (-1, 1):
            for y in range(0, 5):
                g.set(bx + dx, y, bz + dz, "stripped_dark_oak_log[axis=y]")
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            g.set(bx + dx, 5, bz + dz, "dark_oak_planks")
    for dx in range(-1, 2):
        g.set(bx + dx, 4, bz, "dark_oak_slab[type=top]") if False else None
    for dx, dz in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
        g.set(bx + dx, 6, bz + dz, "dark_oak_slab[type=bottom]")
    g.set(bx, 6, bz, "dark_oak_planks")
    g.set(bx, 7, bz, "dark_oak_slab[type=bottom]")
    g.set(bx, 4, bz, "dark_oak_planks")
    g.set(bx, 3, bz, "bell[attachment=ceiling,facing=north,powered=false]")
    g.mark("bell", bx, 3, bz)
    # 庭 (南、柵)
    for z in range(0, 9):
        g.set(-8, 0, z, "spruce_fence")
        g.set(8, 0, z, "spruce_fence")
    for x in range(-8, 9):
        g.set(x, 0, 8, "spruce_fence")
    g.set(0, 0, 8, "spruce_fence_gate[facing=south,in_wall=false,open=false,powered=false]")
    for z in range(-1, 8):
        g.set(0 if z > 1 else -3, -1, z, "dirt_path")
    for x in range(-3, 1):
        g.set(x, -1, 1, "dirt_path")
    g.set(5, 0, 4, "hay_block[axis=y]")
    g.set(5, 1, 4, "campfire[lit=true,facing=west,signal_fire=true,waterlogged=false]")
    g.mark("smoke", 5, 1, 4)
    for x, z in [(-6, 3), (-5, 3), (-6, 4), (-5, 6), (6, 6)]:
        g.set(x, 0, z, "azalea_leaves[distance=7,persistent=true,waterlogged=false]" if False else "oak_leaves[distance=7,persistent=true,waterlogged=false]")
    g.mark("stand", 0, 0, 5)
    g.mark("door", -3, 0, -1)


# ---------------------------------------------------------------- 積み石 (道しるべ。野営地から宿りへ 40 ブロックおき)
def cairn(g: Grid, rec=None):
    g.meta = {"rect": [-1, -1, 1, 1], "level": False}
    g.set(0, 0, 0, "stone")
    g.set(0, 1, 0, "cobblestone")
    g.set(0, 2, 0, "andesite_slab[type=bottom]")
    g.set(1, 0, 0, "cobblestone_slab[type=bottom]")
    g.set(0, 0, 1, "mossy_cobblestone_slab[type=bottom]")
    g.mark("top", 0, 3, 0)


# ---------------------------------------------------------------- 巨大な足跡 (約 9 x 11。北向き。中心は土踏まず)
def _foot_mask(kind: str, mirror=False):
    """(x, z) -> 深さ (0 = くぼまない)。x は -4..4、z は -5..5 (北が -z)。右足 (母指が西)。5 本の指は 1 ブロックずつ離れる"""
    out = {}
    # 指 (z -5..-4): 母指は 2 幅、あとは 1 幅 (小指は 1 ブロック)
    for x, zs in [(-4, (-5, -4)), (-3, (-5, -4)), (-1, (-5, -4)), (1, (-5, -4)), (3, (-4,)), ]:
        for z in zs:
            out[(x, z)] = 1
    out[(-2, -4)] = 1
    # 母指球 (z -3..-1): 幅 9 から 8
    for z in (-3, -2, -1):
        for x in range(-4, 5):
            if z == -3 and x in (-4, 4):
                continue
            out[(x, z)] = 3 if (abs(x) <= 2 and z == -2) else 2
    # 土踏まず (z 0..2): 内側 (西) が欠ける
    for z in (0, 1, 2):
        for x in range(-1 if z != 1 else 0, 4):
            out[(x, z)] = 2 if z == 1 else 1 + (x in (1, 2))
    # かかと (z 3..5): 丸い
    for z, xs in [(3, range(-1, 4)), (4, range(-1, 4)), (5, range(0, 3))]:
        for x in xs:
            out[(x, z)] = 3 if z == 4 and x in (0, 1, 2) else 2
    if mirror:
        out = {(-x, z): d for (x, z), d in out.items()}
    return out


def _carve(g, mask, floor_blocks, water_top=-2):
    for (x, z), d in mask.items():
        for y in range(-1, -1 - d, -1):
            g.set(x, y, z, "air")
        fb = floor_blocks(x, z, d)
        g.set(x, -1 - d, z, fb)
        for y in range(-1 - d + 1, water_top + 1):
            if y <= water_top and y >= -d:
                g.set(x, y, z, "water[level=0]")


def footprint_a(g: Grid, rng):
    """足の形のくぼみ: 5 本の指・母指球・土踏まず・かかと。深さ 1〜3 (母指球とかかとが深い)、底は粘土と石、雨水が 1〜2 ブロックたまる"""
    g.meta = {"rect": [-6, -7, 6, 7], "level": True, "water": True, "ring": 3}
    mask = _foot_mask("a")
    _carve(g, mask, lambda x, z, d: "stone" if d == 3 and (x + z) % 4 == 0 else "clay")
    # 縁: 押し出された土の盛り (一段)
    for (x, z) in list(mask):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                n = (x + dx, z + dz)
                if n not in mask and g.get(n[0], 0, n[1]) is None and (n[0] * 7 + n[1] * 3) % 4 == 0:
                    g.set(n[0], 0, n[1], "coarse_dirt")
    g.mark("center", 0, 0, 0)


def footprint_b(g: Grid, rec):
    """ぬかるみの足跡: 丸みのある大きな楕円。底は泥、縁に泥の盛り、水が 2 ブロック"""
    g.meta = {"rect": [-6, -7, 6, 7], "level": True, "water": True, "ring": 3}
    mask = {}
    for x in range(-4, 5):
        for z in range(-5, 6):
            v = (x / 4.2) ** 2 + (z / 5.4) ** 2
            if v <= 1:
                mask[(x, z)] = 1 + (v < 0.7) + (v < 0.3)
    _carve(g, mask, lambda x, z, d: "mud")
    for x in range(-5, 6):
        for z in range(-6, 7):
            v = (x / 4.2) ** 2 + (z / 5.4) ** 2
            if 1 < v <= 1.5 and (x * 5 + z) % 3 == 0:
                g.set(x, 0, z, "mud")
    g.mark("center", 0, 0, 0)


def footprint_c(g: Grid, rec):
    """割れた足跡: 足の形 (左足)。周りの地面が割れて石が跳ね上がる。底は乾いた粘土、小さな水たまりだけ"""
    g.meta = {"rect": [-7, -8, 7, 8], "level": True, "water": False, "ring": 3}
    mask = _foot_mask("c", mirror=True)
    _carve(g, mask, lambda x, z, d: "terracotta" if (x + z) % 3 == 0 else "clay", water_top=-99)
    for (x, z) in list(mask):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                n = (x + dx, z + dz)
                if n not in mask and (n[0] * 5 + n[1] * 7) % 5 == 0 and g.get(n[0], 0, n[1]) is None:
                    g.set(n[0], 0, n[1], "stone" if (n[0] + n[1]) % 2 else "cobblestone")
    for (x, z) in [(-1, -2), (1, 4)]:
        if (x, z) in mask:
            g.set(x, -1 - mask[(x, z)] + 1, z, "water[level=0]")
    g.mark("center", 0, 0, 0)


# ---------------------------------------------------------------- 巨神の骨 (長さ 40、高さ 18。棟は x 方向。肋骨は z-y 面)
def _rib(g, x, half, height, side, rng, broken=0, thick=2, lean=0.0):
    """x の位置に、z-y 面の弧を立てる。half = 半幅、side: 0=両側 / -1=北だけ / 1=南だけ。broken = 先を欠く高さ"""
    pts = []
    N = 90
    for i in range(N + 1):
        t = math.pi * i / N
        z = half * math.cos(t)
        y = height * (math.sin(t) ** 0.8)
        pts.append((z + lean * y, y))
    seen = set()
    for z, y in pts:
        zi, yi = int(round(z)), int(round(y))
        if broken and yi > height - broken:
            continue
        if side == -1 and zi > 0:
            continue
        if side == 1 and zi < 0:
            continue
        w = thick if yi < height * 0.55 else 1
        for dz in range(w):
            for dy in range(1 if yi > height * 0.8 else 2 if w == 2 else 1):
                key = (zi + (dz if zi >= 0 else -dz), yi + dy)
                if key not in seen:
                    seen.add(key)
                    bone = "bone_block[axis=y]" if rng.random() > 0.18 else ("calcite" if rng.random() > 0.5 else "diorite")
                    g.set(x, key[1], key[0], bone)
    return seen


def ribs_common(g: Grid):
    g.meta = {"rect": [-22, -10, 22, 10], "level": False, "anchor": True}
    # 足もとの石板は別の部品 (slab.json) を、骨の東の端 (この印) に、その場の地面の高さで置く
    g.mark("slab", 24, 0, 0)


def slab(g: Grid, rec=None):
    """骨の足もとの石板: 石レンガの低い柱に、一行の碑文の看板 (薄青の光る文字。文は lang の sign.skyabyss.opening_slab)"""
    g.meta = {"rect": [-2, -2, 2, 3], "level": False}
    g.set(0, 0, 0, "stone_bricks")
    g.set(0, 1, 0, "stone_bricks")
    g.set(0, 2, 0, "stone_brick_slab[type=bottom]")
    g.set(0, 1, 1, "spruce_wall_sign[facing=south,waterlogged=false]")
    g.set(-1, 0, 1, "bone_block[axis=x]")
    g.set(2, 0, 2, "bone_block[axis=z]")
    g.mark("sign", 0, 1, 1)


def ribs_a(g: Grid, rng):
    """列をなす肋骨: 7 本の弧が 6 ブロックおきに並び、中ほどが一番高い (18)。先が折れた物もある。棟の骨が低く這う"""
    ribs_common(g)
    xs = [-18, -12, -6, 0, 6, 12, 18]
    hs = [11, 15, 18, 18, 17, 14, 9]
    br = [4, 0, 0, 0, 3, 0, 5]
    for x, h, b in zip(xs, hs, br):
        _rib(g, x, 7, h, 0, rng, broken=b)
    # 棟 (背骨): 低い骨ブロックの列、ところどころ椎骨の瘤
    for x in range(-20, 21):
        g.set(x, 0, 0, "bone_block[axis=x]")
        if x % 3 == 0:
            g.set(x, 1, 0, "bone_block[axis=y]")
    # 倒れた骨
    for x, z in [(-22, 5), (-23, 6), (21, -6)]:
        g.set(x, 0, z, "bone_block[axis=x]")
    g.mark("center", 0, 0, 0)


def ribs_b(g: Grid, rng):
    """片側だけ立つ肋骨: 北側の弧だけが残って内側へ傾き、南側は崩れて地に埋もれる。瓦礫の骨"""
    ribs_common(g)
    xs = [-18, -12, -6, 0, 6, 12, 18]
    hs = [10, 14, 18, 18, 16, 13, 8]
    for x, h in zip(xs, hs):
        _rib(g, x, 7, h, -1, rng, broken=(2 if x in (-18, 12) else 0), lean=0.0)
        # 南側は根もとだけ
        for z in range(1, 8):
            hh = int(max(0, 5 - z * 0.6 + rng.random()))
            for y in range(0, hh):
                if rng.random() > 0.3:
                    g.set(x, y, z, "bone_block[axis=y]")
    for x in range(-19, 20):
        if rng.random() > 0.3:
            g.set(x, 0, 0, "bone_block[axis=x]")
    g.mark("center", 0, 0, 0)


def ribs_c(g: Grid, rng):
    """三本だけ高い: 中央の三本が 18 で堂々と立ち、ほかは砂に半ば埋もれた短い弧 (3〜7)。巨大さを一点に集める"""
    ribs_common(g)
    xs = [-18, -12, -6, 0, 6, 12, 18]
    hs = [4, 7, 18, 18, 18, 6, 3]
    for x, h in zip(xs, hs):
        _rib(g, x, 7 if h > 8 else 6, h, 0, rng, thick=3 if h > 8 else 2)
    for x in (-24, 22):
        g.set(x, 0, 0, "bone_block[axis=x]")
    g.mark("center", 0, 0, 0)


BUILDERS = {
    "camp": {"a": camp_a, "b": camp_b, "c": camp_c},
    "shelter": {"a": shelter_a, "b": shelter_b, "c": shelter_c},
    "footprint": {"a": footprint_a, "b": footprint_b, "c": footprint_c},
    "ribs": {"a": ribs_a, "b": ribs_b, "c": ribs_c},
}
TITLES = {"camp": "旅の野営地", "shelter": "鐘の宿り", "footprint": "巨大な足跡", "ribs": "巨神の骨"}
NOTES = {
    "camp": {"a": "A 焚き火を囲む輪", "b": "B 崩れた天幕", "c": "C 荷車の陰"},
    "shelter": {"a": "A 小屋と柵の庭", "b": "B 円い石の小屋", "c": "C 鐘楼付き"},
    "footprint": {"a": "A 足の形 (指 5 本)", "b": "B ぬかるみの楕円", "c": "C 割れた足跡"},
    "ribs": {"a": "A 列をなす肋骨", "b": "B 片側だけ立つ", "c": "C 三本だけ高い"},
}

# ---------------------------------------------------------------- 図にする
NAMEFIX = {
    "campfire": (70, 52, 40), "bell": (222, 182, 60), "barrel": (125, 92, 52), "lectern": (160, 125, 75),
    "lantern": (240, 190, 90), "stone_button": (126, 126, 126), "water": (50, 100, 205), "bookshelf": (130, 100, 62),
    "dirt_path": (148, 122, 65), "glass_pane": (190, 220, 230), "chain": (60, 62, 70), "mud": (60, 52, 58),
    "oak_leaves": (60, 110, 40), "spruce_wall_sign": (115, 85, 49), "spruce_door": (110, 80, 45), "spruce_fence_gate": (115, 85, 49),
    "hay_block": (190, 160, 40), "clay": (160, 166, 179), "coarse_dirt": (119, 85, 59), "podzol": (91, 63, 24),
    "red_carpet": (160, 39, 34), "light_gray_carpet": (142, 142, 135), "gray_carpet": (62, 68, 71), "white_carpet": (233, 236, 236),
    "bone_block": (229, 226, 208), "calcite": (223, 224, 221), "terracotta": (152, 94, 68),
    "grass_block": (98, 148, 62), "dirt": (134, 96, 67), "stripped_spruce_log": (115, 89, 52),
}
SHAPES = {
    "carpet": [(0, 0, 0, 1, 0.13, 1)],
    "slab": [(0, 0, 0, 1, 0.5, 1)],
    "fence": [(0.375, 0, 0.375, 0.625, 1, 0.625)],
    "pane": [(0.4, 0, 0.4, 0.6, 1, 0.6)],
    "wall": [(0.25, 0, 0.25, 0.75, 1, 0.75)],
    "campfire": [(0, 0, 0, 1, 0.45, 1)],
    "bell": [(0.3, 0.2, 0.3, 0.7, 0.9, 0.7)],
    "lantern": [(0.3, 0, 0.3, 0.7, 0.55, 0.7)],
    "sign": [(0.1, 0.2, 0.9, 0.9, 0.8, 1.0)],
    "button": [(0.3, 0, 0.3, 0.7, 0.12, 0.7)],
    "door": [(0, 0, 0.8, 1, 1, 1)],
    "chain": [(0.45, 0, 0.45, 0.55, 1, 0.55)],
    "gate": [(0.4, 0.2, 0.4, 0.6, 1, 0.6)],
}


def shape_of(name: str):
    n = name.split(":")[-1]
    if n.endswith("_carpet"):
        return SHAPES["carpet"]
    if n.endswith("_slab"):
        return SHAPES["slab"]
    if n.endswith("_fence") or n == "oak_fence":
        return SHAPES["fence"]
    if n.endswith("_fence_gate"):
        return SHAPES["gate"]
    if n.endswith("_pane"):
        return SHAPES["pane"]
    if n.endswith("_wall"):
        return SHAPES["wall"]
    if n.endswith("_wall_sign"):
        return SHAPES["sign"]
    if n.endswith("_door"):
        return SHAPES["door"]
    if n == "campfire":
        return SHAPES["campfire"]
    if n == "bell":
        return SHAPES["bell"]
    if n == "lantern":
        return SHAPES["lantern"]
    if n == "stone_button":
        return SHAPES["button"]
    if n == "chain":
        return SHAPES["chain"]
    return None  # 立方体


def ground_blocks(name):
    return {"footprint": ("grass_block", "dirt"), "ribs": ("grass_block", "dirt"), "camp": ("grass_block", "dirt"), "shelter": ("grass_block", "dirt")}[name]


def to_dump(site: str, g: Grid, margin=3) -> Dump:
    x0, y0, z0, x1, y1, z1 = g.bounds()
    rect = g.meta.get("rect", [x0, z0, x1, z1])
    gx0, gz0, gx1, gz1 = min(x0, rect[0]), min(z0, rect[1]), max(x1, rect[2]), max(z1, rect[3])
    gx0 -= margin
    gz0 -= margin
    gx1 += margin
    gz1 += margin
    ymin = min(y0, -1) - 5
    ymax = max(y1, 3) + 2
    sx, sy, sz = gx1 - gx0 + 1, ymax - ymin + 1, gz1 - gz0 + 1
    palette = [{"name": "minecraft:air", "state": "minecraft:air", "air": True, "collide": False, "color": "#000000"}]
    index = {"minecraft:air": 0}

    def pid(s):
        if s not in index:
            base = s.split("[")[0]
            nm = base.split(":")[-1]
            fl = "water" if nm == "water" else None
            col = NAMEFIX.get(nm)
            palette.append({"name": base, "state": s, "air": base == "minecraft:air", "collide": fl is None and base != "minecraft:air",
                            "fluid": fl, "source": fl is not None, "color": "#%02x%02x%02x" % (col or (128, 128, 128))})
            index[s] = len(palette) - 1
        return index[s]

    ids = np.zeros((sy, sz, sx), np.uint16)
    top, under = ground_blocks(site)
    gtop, gunder = pid(M + top), pid(M + under)
    # 地面: 上面 y=-1 (草)、その下は土
    for yy in range(ymin, 0):
        ids[yy - ymin, :, :] = gunder
    ids[-1 - ymin, :, :] = gtop
    for (x, y, z), s in g.b.items():
        ids[y - ymin, z - gz0, x - gx0] = pid(s)
    header = {"name": site, "dimension": "rough", "origin": [gx0, ymin, gz0], "size": [sx, sy, sz], "palette": palette, "points": {}}
    return Dump(header, ids, palette)


_SUFFIX = ("_stairs", "_slab", "_fence_gate", "_fence", "_wall_sign", "_door", "_trapdoor", "_button", "_wall", "_pane")


def colors_of(d: Dump):
    c = _ORIG_COLORS(d).astype(np.float32)
    for i, e in enumerate(d.palette):
        nm = e["name"].split(":")[-1]
        if nm in NAMEFIX:
            c[i] = NAMEFIX[nm]
            continue
        for suf in _SUFFIX:
            if nm.endswith(suf):
                base = nm[: -len(suf)]
                for cand in (base + "_planks", base, base + "s"):
                    t = srender._texture_color("minecraft:" + cand)
                    if t is not None:
                        c[i] = t
                        break
                break
    return c


def _shape_arrays(d: Dump):
    """パレットごとの箱 (最大 1)。立方体は None"""
    return [shape_of(e["name"]) if not e.get("air") and e.get("fluid") is None else None for e in d.palette]


def eye_view(d: Dump, eye, yaw, pitch, fov=70, size=(480, 280), far=70.0):
    """人の目線 (ボクセルの光線の追跡)。eye は世界の座標 (x, y, z、y は目の高さ)。yaw は 0=南・90=西・180=北・270=東"""
    W, H = size
    colors = colors_of(d)
    shapes = _shape_arrays(d)
    sy, sz, sx = d.shape
    ox, oy, oz = d.origin
    f = (W / 2) / math.tan(math.radians(fov / 2))
    xs = (np.arange(W) - W / 2 + 0.5) / f
    ys = -(np.arange(H) - H / 2 + 0.5) / f
    u, v = np.meshgrid(xs, ys)
    # カメラ: 前 = (-sin yaw, 0, cos yaw) (yaw 0 で南 = +z)、pitch は下向きが正
    yw, pt = math.radians(yaw), math.radians(pitch)
    fwd = np.array([-math.sin(yw) * math.cos(pt), -math.sin(pt), math.cos(yw) * math.cos(pt)])
    right = np.array([-math.cos(yw), 0, -math.sin(yw)])
    up = np.cross(right, fwd)
    up = -up if up[1] < 0 else up
    dirs = fwd[None, None, :] + u[..., None] * right[None, None, :] + v[..., None] * up[None, None, :]
    dirs /= np.linalg.norm(dirs, axis=2, keepdims=True)
    img = np.zeros((H, W, 3), np.float32)
    sky_t = np.clip(0.5 + dirs[..., 1] * 1.2, 0, 1)[..., None]
    img[:] = (np.array([185, 205, 225]) * (1 - sky_t) + np.array([95, 140, 205]) * sky_t)
    alive = np.ones((H, W), bool)
    step = 0.12
    pos = np.broadcast_to(np.array(eye, np.float32), (H, W, 3)).copy()
    prev_cell = np.floor(pos).astype(np.int32)
    maxn = int(far / step)
    shape_boxes = [s for s in shapes]
    for n in range(maxn):
        pos += dirs * step
        cell = np.floor(pos).astype(np.int32)
        ix, iy, iz = cell[..., 0] - ox, cell[..., 1] - oy, cell[..., 2] - oz
        inb = (ix >= 0) & (ix < sx) & (iy >= 0) & (iy < sy) & (iz >= 0) & (iz < sz)
        pid = np.zeros((H, W), np.int32)
        pid[inb] = d.ids[iy[inb], iz[inb], ix[inb]]
        solid = alive & inb & (pid != 0)
        if solid.any():
            # 形のあるものは箱で判定
            loc = pos - cell
            hit = solid.copy()
            for k in np.unique(pid[solid]):
                sh = shape_boxes[k] if k < len(shape_boxes) else None
                if sh is None:
                    continue
                m = solid & (pid == k)
                inside = np.zeros_like(m)
                for (a0, b0, c0, a1, b1, c1) in sh:
                    inside |= (loc[..., 0] >= a0) & (loc[..., 0] <= a1) & (loc[..., 1] >= b0) & (loc[..., 1] <= b1) & (loc[..., 2] >= c0) & (loc[..., 2] <= c1)
                hit[m] = inside[m]
            if hit.any():
                # 面の向き: 前の位置との差で、変わった軸
                diff = cell - prev_cell
                shade = np.full((H, W), 0.8, np.float32)
                shade[diff[..., 1] != 0] = np.where(dirs[..., 1][diff[..., 1] != 0] < 0, 1.0, 0.5)
                shade[diff[..., 0] != 0] = 0.6
                shade[diff[..., 2] != 0] = 0.8
                shade[(diff == 0).all(axis=2)] = 1.0
                col = colors[pid] * shade[..., None]
                fog = np.clip(n * step / far, 0, 1)[..., None] if False else (n * step / far)
                col = col * (1 - 0.35 * fog) + np.array([185, 205, 225]) * (0.35 * fog)
                img[hit] = col[hit]
                alive &= ~hit
        prev_cell = cell
        if not alive.any():
            break
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


# eye = 目の位置、at = 見る点
VIEWS = {
    "camp": dict(eye=(-8.5, 1.7, 9.5), at=(0.5, 0.6, -0.5)),
    "shelter": dict(eye=(1.5, 1.7, 17.5), at=(0.5, 2.5, 0.0)),
    "footprint": dict(eye=(0.5, 1.7, 11.5), at=(0.5, -1.5, -1.0)),
    "ribs": dict(eye=(-26.5, 1.7, 22.5), at=(0.5, 8.0, 0.0)),
}
FAR = {"camp": 40, "shelter": 55, "footprint": 40, "ribs": 80}


def render_option(site, key, seed=7):
    import random
    rng = random.Random(seed)
    g = Grid()
    BUILDERS[site][key](g, rng)
    d = to_dump(site, g)
    ox, oy, oz = d.origin
    scale = {"camp": 16, "shelter": 11, "footprint": 18, "ribs": 9}[site]
    srender._colors = lambda dd: colors_of(dd).astype(np.int16)
    top = srender.top(d, scale=scale)
    iso = srender.iso(d, step=1)
    f = max(1, 460 // iso.width)
    iso = iso.resize((iso.width * f, iso.height * f), Image.NEAREST)
    if iso.width > 700:
        iso = iso.resize((700, int(iso.height * 700 / iso.width)), Image.NEAREST)
    v = VIEWS[site]
    ey = v["eye"]
    dx, dy, dz = v["at"][0] - ey[0], v["at"][1] - ey[1], v["at"][2] - ey[2]
    yaw = math.degrees(math.atan2(-dx, dz)) % 360
    pitch = math.degrees(math.atan2(-dy, math.hypot(dx, dz)))
    eye = eye_view(d, ey, yaw, pitch, far=FAR[site])
    return g, d, top, iso, eye


def compare(site, keys="abc"):
    cols = []
    for k in keys:
        g, d, top, iso, eye = render_option(site, k)
        ims = [top, iso, eye]
        cols.append((NOTES[site][k], ims))
        (OUT / site).mkdir(parents=True, exist_ok=True)
        top.save(OUT / site / f"{k}_top.png")
        iso.save(OUT / site / f"{k}_iso.png")
        eye.save(OUT / site / f"{k}_eye.png")
    return cols


def sheet(title, columns, note=""):
    pad = 20
    colw = max(max(im.width for im in ims) for _, ims in columns) + pad
    rows = max(len(ims) for _, ims in columns)
    rowh = [max((ims[r].height if r < len(ims) else 0) for _, ims in columns) for r in range(rows)]
    head = 70 + (34 if note else 0)
    W = pad + colw * len(columns)
    H = head + 44 + sum(h + pad for h in rowh) + pad
    out = Image.new("RGB", (W, H), (18, 18, 26))
    dr = ImageDraw.Draw(out)
    dr.text((pad, 18), title, font=font(30), fill=(235, 235, 245))
    if note:
        dr.text((pad, 60), note, font=font(18), fill=(170, 170, 190))
    for ci, (label, ims) in enumerate(columns):
        x = pad + ci * colw
        dr.text((x, head), label, font=font(24), fill=(120, 220, 230))
        y = head + 44
        for r, im in enumerate(ims):
            out.paste(im, (x + (colw - pad - im.width) // 2, y))
            y += rowh[r] + pad
    return out


def blueprint(site: str, key: str, seed=7) -> dict:
    """ゲームの部品 (JSON)。ブロックの配置 + 目印 + 地形の整え方"""
    import random
    g = Grid()
    BUILDERS[site][key](g, random.Random(seed))
    palette, idx, blocks = [], {}, []
    for (x, y, z), s in sorted(g.b.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
        if s not in idx:
            idx[s] = len(palette)
            palette.append(s)
        blocks.append([x, y, z, idx[s]])
    return {"site": site, "option": key, "palette": palette, "blocks": blocks,
            "marks": {k: list(v) for k, v in g.marks.items()}, "meta": g.meta}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--install", action="store_true", help="採用案を src の JSON に書く")
    ap.add_argument("--option", action="append", default=[], help="site=a|b|c で採用案を変える")
    ap.add_argument("--sites", default="camp,shelter,footprint,ribs")
    args = ap.parse_args()
    for o in args.option:
        s, k = o.split("=")
        ADOPTED[s] = k
    if args.install:
        DATA.mkdir(parents=True, exist_ok=True)
        for s in args.sites.split(","):
            bp = blueprint(s, ADOPTED[s])
            (DATA / f"{s}.json").write_text(json.dumps(bp, separators=(",", ":")), encoding="utf-8")
            print("installed", s, ADOPTED[s], len(bp["blocks"]), "blocks")
        for name, fn in (("cairn", cairn), ("slab", slab)):
            bp = blueprint_small(name, fn)
            (DATA / f"{name}.json").write_text(json.dumps(bp, separators=(",", ":")), encoding="utf-8")
        return
    OUT.mkdir(parents=True, exist_ok=True)
    for s in args.sites.split(","):
        cols = compare(s)
        sh = sheet(f"{TITLES[s]} 3 案 (上から・斜め・人の目線)", cols, "ブロックの配置をそのまま描いた図。採用案は README.md")
        sh.save(OUT / f"{s}.png")
        print("wrote", OUT / f"{s}.png")


def blueprint_small(name, fn):
    g = Grid()
    fn(g)
    palette, idx, blocks = [], {}, []
    for (x, y, z), s in sorted(g.b.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
        if s not in idx:
            idx[s] = len(palette)
            palette.append(s)
        blocks.append([x, y, z, idx[s]])
    return {"site": name, "option": "a", "palette": palette, "blocks": blocks, "marks": {k: list(v) for k, v in g.marks.items()}, "meta": g.meta}


if __name__ == "__main__":
    main()
