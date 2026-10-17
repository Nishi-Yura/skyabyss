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
        # 地面に沿って掘る・盛る列 (x, z) -> [kind, a, b, c]。kind 0 = 掘る [深さ, 底, 雨水], 1 = 盛る [高さ, 上, 中], 2 = 物 [高さ, ブロック]
        self.relief: dict[tuple[int, int], list] = {}
        # 図にだけ置く人の目印 (部品には入らない): [(x, y, z)]
        self.persons: list[tuple[int, int, int]] = []

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
        xs = [k[0] for k in self.b] + [k[0] for k in self.relief] + [p[0] for p in self.persons]
        ys = [k[1] for k in self.b] + [0] + [p[1] + 2 for p in self.persons]
        zs = [k[2] for k in self.b] + [k[1] for k in self.relief] + [p[2] for p in self.persons]
        ys += [max([r[1] for r in self.relief.values() if r[0] != 0] + [0])]
        return min(xs), min(ys), min(zs), max(xs), max(ys), max(zs)

    def max_depth(self):
        return max([r[1] for r in self.relief.values() if r[0] == 0] + [0])

    def relief_top(self, x, z):
        """平らな地面 (上面 y=-1) で、掘る・盛るあとの、その列のいちばん上の固い面の高さ"""
        r = self.relief.get((x, z))
        if r is None:
            return -1
        return -1 - r[1] if r[0] == 0 else -1 + r[1]

    def pool_ok(self, x, z):
        """雨水を張ってよいか (規則は SiteJob.waterOk と同じ): 四方が水面より高い壁か、同じ高さの雨水の列"""
        r = self.relief.get((x, z))
        if r is None or r[0] != 0 or not r[3]:
            return False
        yw = -r[1]
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = self.relief.get((x + dx, z + dz))
            if self.relief_top(x + dx, z + dz) >= yw:
                continue
            if n is not None and n[0] == 0 and n[1] == r[1] and n[3]:
                continue
            return False
        return True


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


# ---------------------------------------------------------------- 巨大な足跡 v2 (長さ 50 ほど・深さ 4〜8。北向き = 指が -z。中心は土踏まず)
# 地面の高さに沿って掘る (起伏のある所でも、足跡は地面に押し込まれた形になる)。ゲーム側 (SiteJob.applyRelief) は
# 列ごとの「relief」(kind 0 = 掘る [深さ, 底のブロック, 雨水], 1 = 盛る [高さ, 上, 中], 2 = 物を立てる [高さ, ブロック]) を地面の高さから適用する。
# 図 (平らな地面 y=-1) では to_dump が同じ規則で描く。
FOOT_X = (-44, 44)
FOOT_Z = (-100, 36)
FLOORS = ["mud", "mud", "mud", "packed_mud", "gravel", "mud", "clay"]
RIM_TOPS = ["coarse_dirt", "coarse_dirt", "dirt", "coarse_dirt", "dirt", "gravel"]
BOULDERS = ["stone", "cobblestone", "mossy_cobblestone", "andesite", "stone", "cobblestone"]


def _ell_depth(X, Z, cx, cz, rx, rz, ang, D, slope=0.9):
    """楕円 (中心 cx, cz、横の半径 rx、前後の半径 rz、前を向く角度 ang = 北から東へ) のくぼみの深さ (端 1、内側へ slope ブロックで 1 ずつ深く、最大 D)"""
    th = math.radians(ang)
    fx, fz = math.sin(th), -math.cos(th)
    dx, dz = X - cx, Z - cz
    v = dx * fx + dz * fz
    u = dx * (-fz) + dz * fx
    r = np.sqrt((u / rx) ** 2 + (v / rz) ** 2)
    e = (1 - r) * min(rx, rz)
    d = np.minimum(D, 1 + np.floor(e * slope)).astype(np.int32)
    return np.where(r < 1, d, 0)


def _relax(d):
    """隣との深さの差を 1 以内に (縁から少しずつ深くなる = 落ちても段で登れる)"""
    d = d.copy()
    while True:
        m = np.minimum.reduce([np.roll(d, 1, 0), np.roll(d, -1, 0), np.roll(d, 1, 1), np.roll(d, -1, 1)])
        nd = np.where(d > 0, np.minimum(d, m + 1), d)
        if (nd == d).all():
            return d
        d = nd


def _grid_xz():
    xs = np.arange(FOOT_X[0], FOOT_X[1] + 1)
    zs = np.arange(FOOT_Z[0], FOOT_Z[1] + 1)
    X, Z = np.meshgrid(xs, zs)  # [z][x]
    return xs, zs, X.astype(np.float32), Z.astype(np.float32)


def _toe(B, ang, L, w, D, claw=7.0):
    """指と爪: 根もと B から ang の向きに長さ L の楕円、先に爪の溝 (細く長く)"""
    th = math.radians(ang)
    f = (math.sin(th), -math.cos(th))
    mid = (B[0] + f[0] * L / 2, B[1] + f[1] * L / 2)
    tip = (B[0] + f[0] * L, B[1] + f[1] * L)
    cm = (tip[0] + f[0] * (claw / 2 - 1.5), tip[1] + f[1] * (claw / 2 - 1.5))
    return [(mid[0], mid[1], w, L / 2 + 1.0, ang, D), (cm[0], cm[1], 1.5, claw / 2, ang, 2)]


def foot_shapes(kind: str):
    """(cx, cz, rx, rz, ang, D) のリスト"""
    sh = []
    if kind == "a":  # 足の形: かかと・土踏まず・母指球・5 本の指と爪
        sh += [(1.5, 13.0, 8.5, 8.0, 0, 8, 1.3), (-0.5, 3.5, 7.0, 7.0, 0, 5, 1.3), (0, -4.0, 12.5, 6.5, 0, 8, 1.3)]
        sh += _toe((-10.5, -7), -48, 10, 4.0, 5, 8) + _toe((-5.8, -9.5), -24, 12, 3.7, 5, 8) + _toe((0.2, -10.2), 0, 13, 4.0, 5, 8)
        sh += _toe((6.2, -9.5), 22, 12, 3.7, 5, 8) + _toe((11, -7), 46, 9, 3.3, 4, 8)
    elif kind == "b":  # 引きずった足: 太い 4 本指 (水かき) と、かかとから後ろへ引きずった溝
        sh += [(0, 9, 9.5, 9.0, 0, 6), (0, -2, 11.5, 8.0, 0, 7)]
        sh += _toe((-8, -6), -28, 11, 4.0, 5, 6) + _toe((-2.7, -9), -8, 14, 3.9, 5, 6) + _toe((3, -9), 10, 14, 3.9, 5, 6) + _toe((8.5, -6), 30, 11, 3.8, 5, 6)
        sh += [(0, 30, 3.0, 14, 0, 3), (0, 40, 2.0, 8, 0, 2)]
    else:  # c: 丸い足裏 (猫のような肉球) と、放射状に開いた 4 本の爪の溝
        sh += [(0, 4, 14.0, 13.0, 0, 8), (-9, -12, 4.2, 4.5, 0, 5), (-3, -16, 4.2, 4.6, 0, 5), (3.5, -16, 4.2, 4.6, 0, 5), (9.5, -12, 4.2, 4.5, 0, 5)]
        for (bx, ang) in [(-10, -30), (-3.5, -10), (3.5, 10), (10, 30)]:
            th = math.radians(ang)
            f = (math.sin(th), -math.cos(th))
            sh.append((bx + f[0] * 12, -17 + f[1] * 12, 1.6, 6.5, ang, 2))
    return sh


def foot_depth(kind: str, rng, mirror=False):
    xs, zs, X, Z = _grid_xz()
    d = np.zeros(X.shape, np.int32)
    for sh in foot_shapes(kind):
        cx, cz, rx, rz, ang, D = sh[:6]
        d = np.maximum(d, _ell_depth(X, Z, cx, cz, rx, rz, ang, D, sh[6] if len(sh) > 6 else 0.9))
    d = _relax(d)
    # 底の凸凹 (深い所だけ、少し浅くして段を作る)
    for zi in range(d.shape[0]):
        for xi in range(d.shape[1]):
            if d[zi, xi] >= 4 and rng.random() < 0.16:
                d[zi, xi] -= 1
    d = _relax(d)
    if mirror:
        d = d[:, ::-1]
    return xs, zs, d


def _dilate_dist(mask, maxd):
    """穴の外の、穴までの距離 (チェビシェフ、1..maxd)。穴の中は 0"""
    dist = np.full(mask.shape, 99, np.int32)
    dist[mask] = 0
    cur = mask.copy()
    for k in range(1, maxd + 1):
        grown = cur.copy()
        for dz in (-1, 0, 1):
            for dx in (-1, 0, 1):
                grown |= np.roll(np.roll(cur, dz, 0), dx, 1)
        new = grown & ~cur
        dist[new] = k
        cur = grown
    return dist


def build_relief(g: Grid, xs, zs, d, rng, rim_max=3, rim_reach=4, boulders=7, logs=6, cracks=8, pools=True, front_boost=0.0):
    """掘る・盛る・物を立てる列を g.relief に入れる"""
    H, W = d.shape
    pit = d > 0
    dist = _dilate_dist(pit, rim_reach)
    relief = {}
    for zi in range(H):
        for xi in range(W):
            x, z = int(xs[xi]), int(zs[zi])
            if pit[zi, xi]:
                dd = int(d[zi, xi])
                fl = rng.choice(FLOORS)
                if dd >= 5 and rng.random() < 0.3:
                    fl = "gravel"
                relief[(x, z)] = [0, dd, M + fl, 0]
    # 縁: 押し上げられた土 (進む向き = 北の側は強く)
    for zi in range(H):
        for xi in range(W):
            o = int(dist[zi, xi])
            if 1 <= o <= rim_reach:
                z = int(zs[zi])
                boost = 1.0 + front_boost * (1 if z < -15 else 0)
                base = rim_max * boost * (1 - (o - 1) / (rim_reach - 0.3))
                h = int(round(base * (0.55 + 0.9 * rng.random()) + rng.uniform(-0.3, 0.5)))
                if h >= 1 and rng.random() < 0.85:
                    top = M + rng.choice(RIM_TOPS)
                    relief[(int(xs[xi]), z)] = [1, min(h, rim_max + 2), top, M + "dirt"]
    # 割れた地面: 縁から外へ放射状の割れ目 (深さ 1)
    edge = [(xi, zi) for zi in range(H) for xi in range(W) if dist[zi, xi] == 1]
    for _ in range(cracks):
        if not edge:
            break
        xi, zi = rng.choice(edge)
        ang = rng.uniform(0, math.tau)
        L = rng.randint(6, 15)
        px, pz = float(xi), float(zi)
        for k in range(L):
            px += math.cos(ang) + rng.uniform(-0.4, 0.4)
            pz += math.sin(ang) + rng.uniform(-0.4, 0.4)
            cx, cz = int(round(px)), int(round(pz))
            if not (0 <= cx < W and 0 <= cz < H) or pit[cz, cx]:
                continue
            key = (int(xs[cx]), int(zs[cz]))
            relief[key] = [0, 2 if k < 3 and rng.random() < 0.6 else 1, M + rng.choice(["coarse_dirt", "gravel"]), 0]
    # 岩 (はね飛ばされた石)
    cand = [(xi, zi) for zi in range(H) for xi in range(W) if 3 <= dist[zi, xi] <= 9]
    for _ in range(boulders):
        if not cand:
            break
        xi, zi = rng.choice(cand)
        rad = rng.uniform(1.3, 2.4)
        for dz in range(-3, 4):
            for dx in range(-3, 4):
                dd = math.hypot(dx, dz)
                cx, cz = xi + dx, zi + dz
                if dd <= rad and 0 <= cx < W and 0 <= cz < H and not pit[cz, cx]:
                    h = max(1, int(round(rad * 1.2 - dd * 0.8)))
                    relief[(int(xs[cx]), int(zs[cz]))] = [1, h, M + rng.choice(BOULDERS), M + "cobblestone"]
    # 押しつぶされた木: 穴から外へ倒れた幹と、折れた切り株
    for _ in range(logs):
        if not cand:
            break
        xi, zi = rng.choice(cand)
        axis = rng.choice(["x", "z"])
        sgn = 1 if rng.random() < 0.5 else -1
        n = rng.randint(5, 9)
        for k in range(n):
            cx, cz = (xi + sgn * k, zi) if axis == "x" else (xi, zi + sgn * k)
            if 0 <= cx < W and 0 <= cz < H and not pit[cz, cx] and relief.get((int(xs[cx]), int(zs[cz])), [1])[0] != 0:
                relief[(int(xs[cx]), int(zs[cz]))] = [2, 1, M + f"spruce_log[axis={axis}]", 0]
        # 根もとの切り株 (高さ 2〜3)
        relief[(int(xs[xi]), int(zs[zi]))] = [2, rng.randint(2, 3), M + "spruce_log[axis=y]", 0]
        if rng.random() < 0.7:
            lx, lz = xi + rng.randint(-1, 1), zi + rng.randint(-1, 1)
            if 0 <= lx < W and 0 <= lz < H and not pit[lz, lx]:
                relief[(int(xs[lx]), int(zs[lz]))] = [2, 1, M + "spruce_leaves[distance=1,persistent=true,waterlogged=false]", 0]
    # 雨水: 深い底の、ところどころの 1 ブロックのたまり (池に見えないよう、点々と)
    if pools:
        for (x, z), r in relief.items():
            if r[0] == 0 and r[1] >= 4 and math.sin(x * 0.55 + 1.3) * math.sin(z * 0.5 + 0.4) > 0.42:
                r[3] = 1
    g.relief = relief
    return relief


def _recenter_rect(g: Grid, margin=2):
    xs = [k[0] for k in g.relief]
    zs = [k[1] for k in g.relief]
    return [min(xs) - margin, min(zs) - margin, max(xs) + margin, max(zs) + margin]


def _foot_marks(g: Grid, d_at):
    """印: floor = 土踏まずの底、rim = 縁の外 (歩いて登れるかの検査)"""
    g.mark("floor", 0, 0, 3)
    for x in range(26, 0, -1):
        if not any((x + k, 5) in g.relief for k in range(-1, 2)):
            g.mark("rim", x, 0, 5)
            break
    edge = max((x for (x, z), r in g.relief.items() if z == 5 and r[0] == 0), default=8)
    g.mark("edge", edge, 0, 5)
    g.mark("center", 0, 0, 3)


def _footprint(g: Grid, rng, kind):
    xs, zs, d = foot_depth(kind, rng)
    build_relief(g, xs, zs, d, rng, front_boost=0.4)
    g.meta = {"rect": _recenter_rect(g), "level": False, "relief": True, "height": 7, "ring": 2}
    _foot_marks(g, d)


def footprint_a(g: Grid, rng):
    """足の形 (指 5 本と爪): かかと・土踏まず・母指球・5 本の指に爪の溝。深さ 4〜8、縁に盛り上がった土と割れた地面、はね飛ばされた岩、押しつぶされた木。底に雨水がところどころ"""
    _footprint(g, rng, "a")


def footprint_b(g: Grid, rng):
    """引きずった足 (太い 4 本指): 水かきの太い 4 本指と、かかとから後ろへ引きずった長い溝。進む向きがはっきり、ただし足の形が丸く、指の数は読みにくい"""
    _footprint(g, rng, "b")


def footprint_c(g: Grid, rng):
    """丸い足裏 (肉球と 4 本の爪): 一つの深い丸い穴に、放射状の爪の溝。大きさは出るが、足跡というより「落ちたクレーター」に近い"""
    _footprint(g, rng, "c")


def footprint_end(g: Grid, rng=None):
    """道のいちばん先の足跡 (足跡 a の前半) と、その先の、地の中へ潜った穴 (土が投げ出された火口) と、地中を進んだ土のうねり (余燼の通り道、17-3)"""
    import random
    rng = rng or random.Random(7)
    xs, zs, d = foot_depth("a", rng)
    # 後ろ半分 (かかと) だけ残し、前半は深い火口へ
    H, W = d.shape
    for zi in range(H):
        if zs[zi] < -6:
            d[zi, :] = 0
    # 火口: 足跡の先 (z -34) の、土の投げ出された穴 (深さ 9)
    X, Z = np.meshgrid(xs.astype(np.float32), zs.astype(np.float32))
    crater = np.maximum(_ell_depth(X, Z, 0, -26, 11.5, 12.5, 0, 9, 0.8), _ell_depth(X, Z, 0, -8, 9.0, 8.0, 0, 7, 0.9))
    d = _relax(np.maximum(d, crater))
    build_relief(g, xs, zs, d, rng, rim_max=5, rim_reach=7, boulders=9, logs=6, cracks=12, front_boost=0.6)
    # 火口の底は焦げた岩 (余燼の熱)。かかとの底は粘土のまま
    for (x, z), r in g.relief.items():
        if r[0] == 0 and r[1] >= 5 and z < -14:
            r[2] = M + rng.choice(["basalt[axis=y]", "blackstone", "smooth_basalt", "basalt[axis=y]", "blackstone"])
            r[3] = 0
    # 地中を進んだ土のうねり (火口の先へ、幅 5 → 1、高さ 3 → 0 に細る。ところどころ途切れる)
    for k in range(0, 34):
        z = -40 - k
        w = max(1, int(round(3.2 - k * 0.07)))
        hh = max(1, int(round(3.4 - k * 0.09 + rng.uniform(-0.5, 0.5))))
        sway = int(round(2.2 * math.sin(k * 0.22)))
        for dx in range(-w, w + 1):
            hh2 = max(0, hh - (abs(dx) > w - 1))
            if hh2 >= 1 and not (k > 18 and rng.random() < 0.18):
                g.relief[(sway + dx, z)] = [1, hh2, M + rng.choice(RIM_TOPS), M + "dirt"]
    g.meta = {"rect": _recenter_rect(g), "level": False, "relief": True, "height": 8, "ring": 2}
    g.mark("floor", 0, 0, 3)
    g.mark("rim", 22, 0, 5)
    g.mark("edge", max((x for (x, z), r in g.relief.items() if z == 5 and r[0] == 0), default=8), 0, 5)
    g.mark("center", 0, 0, 3)


# ---------------------------------------------------------------- 巨神の骨 v2 (高さ 25〜40、長さ 80 ほど。背骨は x 方向、肋骨は z-y 面)
BONE = "bone_block[axis=y]"


class Bones:
    """骨のブロックを集めて、最後に苔と色むらをつける (g に置く)"""

    def __init__(self):
        self.cells: set[tuple[int, int, int]] = set()

    def add(self, x, y, z):
        if y >= -3:
            self.cells.add((x, y, z))

    def finish(self, g: Grid, rng):
        for (x, y, z) in sorted(self.cells):
            r = rng.random()
            axis = rng.choice("xyz")
            blk = f"bone_block[axis={axis}]"
            if r < 0.07:
                blk = "calcite"
            elif r < 0.10:
                blk = "diorite"
            elif (x, y + 1, z) not in self.cells and r < 0.2:
                blk = "moss_block"
            g.set(x, y, z, blk)
        # 上の面に苔
        for (x, y, z) in sorted(self.cells):
            if (x, y + 1, z) not in self.cells and g.get(x, y + 1, z) is None and rng.random() < 0.5:
                g.set(x, y + 1, z, "moss_carpet")


def _rib(b: Bones, x, W, H, rng, side=1, t0=0.0, t1=1.0, gap=None, sink=0.0, lean=0.0):
    """x の位置に、z-y 面の弧 (背骨の位置 z=0 から外へ W、いちばん高い所 H) を立てる。side = +1 南へ / -1 北へ。gap = (a, b) 弧の途中を欠く。t0..t1 だけ描く"""
    N = 420
    for i in range(N + 1):
        t = i / N
        if t < t0 or t > t1 or (gap and gap[0] < t < gap[1]):
            continue
        z = W * t ** 0.9 + lean * math.sin(math.pi * t) * W * 0.12
        y = H * math.sin(math.pi * t ** 0.82) ** 0.9 - sink
        r = 2.5 - 1.35 * t
        hx = 2 if t < 0.1 else 1 if t < 0.55 else 0
        ri = int(math.ceil(r))
        for dz in range(-ri, ri + 1):
            for dy in range(-ri, ri + 1):
                if dz * dz + dy * dy <= r * r + 0.3:
                    for dx in range(-hx, hx + 1):
                        b.add(x + dx, int(round(y)) + dy, side * int(round(z)) + dz)


def _vertebra(b: Bones, x, cz, yoff, rng, spike=6):
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            for dy in range(-3, 5):
                if (dx / 3.4) ** 2 + (dz / 3.4) ** 2 + (dy / 3.6) ** 2 <= 1:
                    b.add(x + dx, yoff + dy, cz + dz)
    # 棘突起 (背に立つ) と横の突起
    for dy in range(3, 3 + spike):
        w = 1 if dy < 3 + spike - 2 else 0
        for dx in range(-w, w + 1):
            b.add(x + dx, yoff + dy, cz)
    for s in (-1, 1):
        for dz in range(3, 6):
            b.add(x, yoff + 1, cz + s * dz)


def _spine(b: Bones, xs, rng, amp=2.0):
    ys = {}
    for x in xs:
        cz = int(round(amp * math.sin(x / 9.0)))
        yoff = -int(max(0, abs(x) - 22) * 0.35)
        ys[x] = (cz, yoff)
        _vertebra(b, x, cz, yoff, rng, spike=rng.randint(4, 9))
    xs = sorted(xs)
    for x0, x1 in zip(xs, xs[1:]):
        for x in range(x0 + 4, x1 - 3):
            t = (x - x0) / (x1 - x0)
            cz = int(round(ys[x0][0] * (1 - t) + ys[x1][0] * t))
            yo = int(round(ys[x0][1] * (1 - t) + ys[x1][1] * t))
            for dz in range(-1, 2):
                for dy in range(-1, 2):
                    b.add(x, yo + dy + 1, cz + dz)
    return ys


def _mound(g: Grid, x, z, r, rng):
    """骨の根もとの土の盛り (苔と草)"""
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            dd = math.hypot(dx, dz)
            h = int(round(2.6 - dd * 0.6 + rng.uniform(-0.4, 0.4)))
            for y in range(0, h):
                if g.get(x + dx, y, z + dz) is None:
                    g.set(x + dx, y, z + dz, "moss_block" if (y == h - 1 and rng.random() < 0.45) else "grass_block[snowy=false]" if y == h - 1 else "dirt")


def _skull(b: Bones, g: Grid, cx, cz, rng, ax=17, ay=24, az=13, yc=-3):
    """頭蓋: 半ば埋まった半球の殻 (内は空洞)、横に大きな眼窩 (半径 6)、前に鼻の穴の口吻"""
    sx, sy = cx + 6, 10  # 眼窩の中心 (x, y)
    Rs = 6.2
    for x in range(cx - ax - 2, cx + ax + 3):
        for z in range(cz - az - 2, cz + az + 3):
            for y in range(-3, ay + 3):
                rr = math.sqrt(((x - cx) / ax) ** 2 + ((y - yc) / ay) ** 2 + ((z - cz) / az) ** 2)
                ds = math.hypot(x - sx, y - sy)
                brow = abs(z - cz) > az * 0.5 and Rs <= ds < Rs + 2.4 and y >= sy - 2
                inshell = (0.80 <= rr <= 1.0) or (brow and 0.8 <= rr <= 1.1)
                if not inshell:
                    continue
                if ds < Rs and abs(z - cz) > az * 0.4:
                    continue  # 眼窩
                b.add(x, y, z)
    # 口吻 (頭の前、低く長く)
    bx, byc, bax, bay, baz = cx + ax + 3, -4, 8, 10, 6
    for x in range(bx - bax - 1, bx + bax + 2):
        for z in range(cz - baz - 1, cz + baz + 2):
            for y in range(-3, bay + 2):
                rr = math.sqrt(((x - bx) / bax) ** 2 + ((y - byc) / bay) ** 2 + ((z - cz) / baz) ** 2)
                if not (0.78 <= rr <= 1.0):
                    continue
                if x > bx + 4 and ((y - 5) / 3.6) ** 2 + ((z - cz) / 3.2) ** 2 < 1:
                    continue  # 鼻の穴
                if x < cx + ax - 2 and rr < 0.86:
                    continue
                b.add(x, y, z)
    # 殻の内側の床: 土 (苔)
    for x in range(cx - ax + 3, cx + ax - 2):
        for z in range(cz - az + 3, cz + az - 2):
            rr = math.sqrt(((x - cx) / (ax - 3)) ** 2 + ((z - cz) / (az - 3)) ** 2)
            if rr < 1:
                for y in range(0, 2):
                    if (x, y, z) not in b.cells:
                        g.set(x, y, z, "moss_block" if y == 1 else "dirt")


def _fallen(b: Bones, x, z, ang, L, rng, thick=1.6):
    """倒れて地面に埋まりかけた肋骨の破片 (地面を這う)"""
    for i in range(L):
        px = x + math.cos(ang) * i
        pz = z + math.sin(ang) * i
        r = thick * (1 - 0.4 * i / L)
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                for dy in range(0, 3):
                    if math.hypot(dx, dz) <= r and dy <= r:
                        b.add(int(round(px)) + dx, dy - 1 + (1 if i % 7 == 3 else 0), int(round(pz)) + dz)


def _ribs_common(g: Grid):
    g.mark("slab", 3, 0, 9)


def ribs_a(g: Grid, rng):
    """列をなす肋骨: 背骨の脇から 9 本の弧が 7 ブロックおきに立ち、中ほどが最も高い (36)。先の折れた物・倒れた破片。苔と草。くぐって歩ける"""
    b = Bones()
    xs = [-28, -21, -14, -7, 0, 7, 14, 21, 28]
    hs = [24, 28, 33, 36, 36, 34, 31, 27, 22]
    ws = [15, 16, 18, 19, 19, 18, 17, 16, 14]
    _spine(b, xs, rng)
    for x, h, w in zip(xs, hs, ws):
        gap = (0.34, 0.5) if x in (-28, 21) else None
        t1 = 0.75 if x in (28,) else 1.0
        _rib(b, x, w, h, rng, gap=gap, t1=t1, sink=2.0)
    _fallen(b, -36, 14, 0.5, 18, rng)
    _fallen(b, 34, 22, 2.4, 14, rng)
    for x in xs:
        _mound(g, x, int(round(2 * math.sin(x / 9.0))), 4, rng)
    for x, w in zip(xs, ws):
        _mound(g, x, w, 3, rng)
    b.finish(g, rng)
    _ribs_common(g)
    g.mark("under", 0, 0, 12)
    g.mark("far", 0, 0, 30)
    g.meta = {"rect": _auto_rect(g), "level": False, "anchor": True, "clearNear": 3, "height": 40}


def ribs_b(g: Grid, rng):
    """背骨・肋骨・頭蓋 (眼窩): 半ば埋まった背骨に、7 本の肋骨 (高さ 24〜38、折れ・倒れ・埋もれ)、東の端に頭蓋の殻 (高さ 22、横に半径 6 の眼窩、前に口吻)。苔と草"""
    b = Bones()
    xs = [-24, -17, -10, -3, 4, 11]
    hs = [18, 28, 36, 38, 34, 25]
    ws = [12, 16, 19, 20, 18, 15]
    _spine(b, xs + [18], rng)
    for x, h, w in zip(xs, hs, ws):
        gap = (0.3, 0.52) if x == -17 else (0.5, 0.62) if x == 4 else None
        t1 = 0.62 if x == -24 else 1.0
        _rib(b, x, w, h, rng, gap=gap, t1=t1, sink=2.0 if x != -24 else 5.0)
    _fallen(b, -33, 14, 0.3, 14, rng)
    _fallen(b, 4, 26, 2.8, 16, rng)
    _skull(b, g, 34, 3, rng)
    for x in xs + [18]:
        _mound(g, x, int(round(2 * math.sin(x / 9.0))), 4, rng)
    for x, w in zip(xs, ws):
        _mound(g, x, w, 3, rng)
    b.finish(g, rng)
    _ribs_common(g)
    g.marks["slab"] = (0, 0, 9)
    g.mark("under", 0, 0, 12)
    g.mark("far", -6, 0, 30)
    g.meta = {"rect": _auto_rect(g), "level": False, "anchor": True, "clearNear": 3, "height": 40}
    _recenter(g)


def ribs_c(g: Grid, rng):
    """頭蓋が主役: 大きな頭蓋の殻 (高さ 30、眼窩が二つ並んで空を見る) が地面から顔を出し、肋骨は 4 本の折れた根もとと背骨の続きだけ。巨大さを一点に集めるが、骨の列が見えず「岩」に見えやすい"""
    b = Bones()
    xs = [-26, -18, -10, -2]
    hs = [18, 22, 14, 9]
    ws = [10, 12, 10, 8]
    _spine(b, xs + [6], rng)
    for x, h, w in zip(xs, hs, ws):
        _rib(b, x, w, h, rng, gap=(0.45, 0.62), sink=2.0)
    _skull(b, g, 34, 2, rng, ax=22, ay=30, az=17, yc=-5)
    for x in xs + [6]:
        _mound(g, x, int(round(2 * math.sin(x / 9.0))), 4, rng)
    b.finish(g, rng)
    _ribs_common(g)
    g.marks["slab"] = (-12, 0, 8)
    g.mark("under", -14, 0, 8)
    g.mark("far", -14, 0, 26)
    g.meta = {"rect": _auto_rect(g), "level": False, "anchor": True, "clearNear": 3, "height": 36}
    _recenter(g)


def _auto_rect(g: Grid, margin=2):
    x0, _, z0, x1, _, z1 = g.bounds()
    return [x0 - margin, z0 - margin, x1 + margin, z1 + margin]


def _recenter(g: Grid):
    """全体の中心 (x) が 0 になるように、ブロック・印・範囲を横にずらす (場所探しの中心 = 構造の中心)"""
    x0, _, _, x1, _, _ = g.bounds()
    shift = -((x0 + x1) // 2)
    if shift == 0:
        return
    g.b = {(x + shift, y, z): s for (x, y, z), s in g.b.items()}
    g.marks = {k: (x + shift, y, z) for k, (x, y, z) in g.marks.items()}
    r = g.meta["rect"]
    g.meta["rect"] = [r[0] + shift, r[1], r[2] + shift, r[3]]


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


BUILDERS = {
    "camp": {"a": camp_a, "b": camp_b, "c": camp_c},
    "shelter": {"a": shelter_a, "b": shelter_b, "c": shelter_c},
    "footprint": {"a": footprint_a, "b": footprint_b, "c": footprint_c},
    "ribs": {"a": ribs_a, "b": ribs_b, "c": ribs_c},
}
# v2 で作り直した物の図は docs/review/opening_site_roughs/v2/ へ (v1 の図は残す)
V2 = OUT / "v2"
SITE_OUT = {"footprint": V2, "ribs": V2}
TITLES = {"camp": "旅の野営地", "shelter": "鐘の宿り", "footprint": "巨大な足跡", "ribs": "巨神の骨"}
NOTES = {
    "camp": {"a": "A 焚き火を囲む輪", "b": "B 崩れた天幕", "c": "C 荷車の陰"},
    "shelter": {"a": "A 小屋と柵の庭", "b": "B 円い石の小屋", "c": "C 鐘楼付き"},
    "footprint": {"a": "A 足の形 (指 5 本と爪)", "b": "B 引きずった 4 本指", "c": "C 丸い足裏 (クレーター)"},
    "ribs": {"a": "A 列をなす肋骨 (9 本)", "b": "B 背骨・肋骨・頭蓋の眼窩", "c": "C 頭蓋が主役"},
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
    "packed_mud": (142, 106, 79), "mud": (52, 46, 52), "gravel": (131, 127, 126), "moss_block": (89, 109, 45), "moss_carpet": (89, 109, 45),
    "basalt": (80, 81, 86), "blackstone": (42, 36, 41), "smooth_basalt": (72, 72, 78), "mossy_cobblestone": (110, 118, 94),
    "cobblestone": (127, 127, 127), "andesite": (136, 136, 137), "diorite": (188, 188, 188), "stone": (125, 125, 125),
    "spruce_log": (88, 64, 38), "spruce_leaves": (45, 85, 45),
    "red_concrete": (214, 38, 38), "yellow_concrete": (250, 215, 50),
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
    ymin = min(y0, -1) - 5 - g.max_depth()
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
    gtop, gunder, gstone = pid(M + top), pid(M + under), pid(M + "stone")
    # 地面: 上面 y=-1 (草)、その下は土、深いところは石
    for yy in range(ymin, 0):
        ids[yy - ymin, :, :] = gunder if yy > -7 else gstone
    ids[-1 - ymin, :, :] = gtop
    # 掘る・盛る (平らな地面 y=-1 に、ゲームと同じ規則で)
    for (x, z), (kind, a, bb, c) in g.relief.items():
        ix, iz = x - gx0, z - gz0
        if kind == 0:
            for y in range(-1, -1 - a, -1):
                ids[y - ymin, iz, ix] = 0
            ids[-1 - a - ymin, iz, ix] = pid(bb)
            if c and g.pool_ok(x, z):
                ids[-a - ymin, iz, ix] = pid(M + "water[level=0]")
        elif kind == 1:
            for k in range(1, a + 1):
                ids[-1 + k - ymin, iz, ix] = pid(bb if k == a else c)
        else:
            for k in range(1, a + 1):
                ids[-1 + k - ymin, iz, ix] = pid(bb)
    for (x, y, z), s in g.b.items():
        ids[y - ymin, z - gz0, x - gx0] = pid(s)
    for (x, y, z) in g.persons:
        ids[y - ymin, z - gz0, x - gx0] = pid(M + "red_concrete")
        ids[y + 1 - ymin, z - gz0, x - gx0] = pid(M + "yellow_concrete")
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


def eye_view(d: Dump, eye, yaw, pitch, fov=70, size=(480, 280), far=70.0, step=None):
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
    step = step or (0.12 if far <= 80 else 0.2)
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
        # 箱の外へ出て、さらに外へ向かう光線は空 (打ち切る)
        gone = ((ix < 0) & (dirs[..., 0] <= 0)) | ((ix >= sx) & (dirs[..., 0] >= 0)) | ((iy < 0) & (dirs[..., 1] <= 0)) | ((iy >= sy) & (dirs[..., 1] >= 0))             | ((iz < 0) & (dirs[..., 2] <= 0)) | ((iz >= sz) & (dirs[..., 2] >= 0))
        alive &= ~gone
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


def _v2_views(site, g):
    """v2 の大きな構造物の見え方 (名前, 目の位置, 見る点, 遠さ): 人の目線 (人の目印は赤と黄の 2 ブロック)"""
    x0, _, z0, x1, _, z1 = g.bounds()
    if site == "footprint":
        ex, _, ez = g.marks["edge"]
        fx, _, fz = g.marks["floor"]
        ty = g.relief_top(ex + 2, ez) + 1
        return [("縁に立って (人は縁の上)", (ex + 9.0, ty + 1.7, ez), (0.0, -2.0, fz - 4), 90),
                ("縁から引いて見る", (ex + 24.0, 6.0, ez + 12), (0.0, -3.0, -6), 120),
                ("足跡の底から見上げる", (fx + 0.5, -1 - 6 + 1.7, fz + 4), (ex + 4, 6.0, ez - 6), 90)]
    sx, _, sz = g.marks["slab"]
    return [("遠くから (人は石板)", ((x0 + x1) / 2 - 20, 1.7, z1 + 62), ((x0 + x1) / 2, 16.0, 6.0), 190),
            ("骨の列の脇から", (x0 - 14, 1.7, z1 + 8), ((x0 + x1) / 2 + 6, 14.0, 4.0), 160),
            ("骨の下から見上げる", (sx - 5.5, 1.7, sz + 9), (sx + 5.0, 24.0, sz - 2.0), 120)]


def _add_people(site, g):
    if site == "footprint":
        ex, _, ez = g.marks["edge"]
        g.persons = [(ex + 2, g.relief_top(ex + 2, ez) + 1, ez)]
    else:
        sx, _, sz = g.marks["slab"]
        g.persons = [(sx + 2, 0, sz), (sx - 1, 0, sz + 6)]


def render_option(site, key, seed=7):
    import random
    rng = random.Random(seed)
    g = Grid()
    BUILDERS[site][key](g, rng)
    big = site in SITE_OUT
    if big:
        _add_people(site, g)
    d = to_dump(site, g)
    ox, oy, oz = d.origin
    scale = {"camp": 16, "shelter": 11, "footprint": 8, "ribs": 6}[site]
    srender._colors = lambda dd: colors_of(dd).astype(np.int16)
    top = srender.top(d, scale=scale)
    iso = srender.iso(d, step=2 if big else 1)
    f = max(1, 460 // iso.width)
    iso = iso.resize((iso.width * f, iso.height * f), Image.NEAREST)
    if iso.width > 700:
        iso = iso.resize((700, int(iso.height * 700 / iso.width)), Image.NEAREST)
    eyes = []
    views = _v2_views(site, g) if big else [("", VIEWS[site]["eye"], VIEWS[site]["at"], FAR[site])]
    for (_, ey, at, far) in views:
        dx, dy, dz = at[0] - ey[0], at[1] - ey[1], at[2] - ey[2]
        yaw = math.degrees(math.atan2(-dx, dz)) % 360
        pitch = math.degrees(math.atan2(-dy, math.hypot(dx, dz)))
        eyes.append(eye_view(d, ey, yaw, pitch, far=far))
    return g, d, top, iso, eyes


def compare(site, keys="abc"):
    cols = []
    out = SITE_OUT.get(site, OUT)
    for k in keys:
        g, d, top, iso, eyes = render_option(site, k)
        ims = [top, iso] + eyes
        cols.append((NOTES[site][k], ims))
        (out / site).mkdir(parents=True, exist_ok=True)
        top.save(out / site / f"{k}_top.png")
        iso.save(out / site / f"{k}_iso.png")
        for i, e in enumerate(eyes):
            e.save(out / site / f"{k}_eye{i}.png")
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


def _export_relief(g: Grid, out: dict):
    """掘る・盛る列 [x, z, kind, a, b, c] (b, c はブロックの palette の番号。無い値は -1)"""
    if not g.relief:
        return
    pal = out["palette"]

    def pi(x):
        if isinstance(x, str):
            if x not in pal:
                pal.append(x)
            return pal.index(x)
        return -1

    rel = []
    for (x, z), (kind, a, b, c) in sorted(g.relief.items()):
        if kind == 0:
            rel.append([x, z, 0, a, pi(b), int(c)])
        elif kind == 1:
            rel.append([x, z, 1, a, pi(b), pi(c)])
        else:
            rel.append([x, z, 2, a, pi(b), -1])
    out["relief"] = rel


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
    out = {"site": site, "option": key, "palette": palette, "blocks": blocks,
           "marks": {k: list(v) for k, v in g.marks.items()}, "meta": g.meta}
    _export_relief(g, out)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--install", action="store_true", help="採用案を src の JSON に書く")
    ap.add_argument("--option", action="append", default=[], help="site=a|b|c で採用案を変える")
    ap.add_argument("--sites", default="camp,shelter,footprint,ribs")
    ap.add_argument("--keys", default="abc")
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
        for name, fn in (("cairn", cairn), ("slab", slab), ("footprint_end", footprint_end)):
            bp = blueprint_small(name, fn)
            (DATA / f"{name}.json").write_text(json.dumps(bp, separators=(",", ":")), encoding="utf-8")
        return
    OUT.mkdir(parents=True, exist_ok=True)
    for s in args.sites.split(","):
        cols = compare(s, args.keys)
        sh = sheet(f"{TITLES[s]} 3 案 (上から・斜め・人の目線)", cols, "ブロックの配置をそのまま描いた図。採用案は README.md")
        outdir = SITE_OUT.get(s, OUT)
        outdir.mkdir(parents=True, exist_ok=True)
        sh.save(outdir / f"{s}.png")
        print("wrote", outdir / f"{s}.png")


def blueprint_small(name, fn):
    g = Grid()
    fn(g)
    palette, idx, blocks = [], {}, []
    for (x, y, z), s in sorted(g.b.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
        if s not in idx:
            idx[s] = len(palette)
            palette.append(s)
        blocks.append([x, y, z, idx[s]])
    out = {"site": name, "option": "a", "palette": palette, "blocks": blocks, "marks": {k: list(v) for k, v in g.marks.items()}, "meta": g.meta}
    _export_relief(g, out)
    return out


if __name__ == "__main__":
    main()
