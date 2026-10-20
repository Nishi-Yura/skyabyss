# -*- coding: utf-8 -*-
"""はじまりの都の建物のラフ (一棟ずつ 3 案。docs/design/33 の 2.2)。

    python -I tools/rough/capital_buildings.py bell        # 大鐘楼 (語り部の家つき) の 3 案 → docs/review/town_roughs/bell_tower_sheet.png
絵は opening_sites.py と同じ描き方 (上から・斜め・人の目線)。採用した案は tools/gen_capital.py に写す。
座標: x = 東、z = 南、y = 上。地面の上面は y=-1、構造物の最初の段が y=0。
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
sys.path.insert(0, str(ROOT / "tools/structure"))
import opening_sites as osite  # noqa: E402
from opening_sites import Grid  # noqa: E402

OUT = ROOT / "docs/review/town_roughs"


# ---------------------------------------------------------------- 部品
def box(g, x0, y0, z0, x1, y1, z1, s):
    g.fill(x0, y0, z0, x1, y1, z1, s)


def shell(g, x0, y0, z0, x1, y1, z1, wall, corner=None):
    box(g, x0, y0, z0, x1, y1, z0, wall)
    box(g, x0, y0, z1, x1, y1, z1, wall)
    box(g, x0, y0, z0, x0, y1, z1, wall)
    box(g, x1, y0, z0, x1, y1, z1, wall)
    if corner:
        for cx, cz in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            box(g, cx, y0, cz, cx, y1, cz, corner)


def air(g, x0, y0, z0, x1, y1, z1):
    box(g, x0, y0, z0, x1, y1, z1, "air")


def gable(g, x0, x1, z0, z1, y, block="spruce_stairs", cap="spruce_slab[type=bottom]"):
    """東西に棟のある切妻 (opening_sites の _gable_roof と同じ)"""
    osite._gable_roof(g, x0, x1, z0, z1, y, block, cap)


def house(g, x0, z0, x1, z1, wall="spruce_planks", corner="stripped_spruce_log[axis=y]", h=3, door=("south", 0)):
    """語り部の家 (壁 h 段、切妻)。door = (向き, 中心からのずれ)"""
    box(g, x0, -1, z0, x1, -1, z1, "spruce_planks")
    shell(g, x0, 0, z0, x1, h, z1, wall, corner)
    gable(g, x0 - 1, x1 + 1, z0 - 1, z1 + 1, h + 1)
    cx = (x0 + x1) // 2 + door[1]
    if door[0] == "south":
        g.set(cx, 0, z1, "spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
        g.set(cx, 1, z1, "spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
    for x in range(x0 + 2, x1 - 1, 3):
        g.set(x, 1, z0, "glass_pane[north=false,south=false,east=true,west=true]")
        g.set(x, 1, z1, "glass_pane[north=false,south=false,east=true,west=true]") if x != cx else None
    g.set(x0 + 1, 0, z0 + 1, "red_bed[facing=south,part=head,occupied=false]")
    g.set(x0 + 1, 0, z0 + 2, "red_bed[facing=south,part=foot,occupied=false]")
    g.set(x1 - 1, 0, z0 + 1, "lectern[facing=south,has_book=false,powered=false]")
    g.set(x1 - 1, 0, z1 - 1, "barrel[facing=up,open=false]")
    g.set(cx, h, (z0 + z1) // 2, "lantern[hanging=true]")


def smoke_fire(g, x, z):
    g.set(x, 0, z, "hay_block[axis=y]")
    g.set(x, 1, z, "campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]")
    g.mark("smoke", x, 1, z)


def lantern_post(g, x, y, z, h=3):
    box(g, x, y, z, x, y + h - 1, z, "spruce_fence")
    g.set(x, y + h, z, "lantern[hanging=false]")


# ---------------------------------------------------------------- 大鐘楼 3 案
def bell_a(g: Grid, rng=None):
    """A「石の鐘楼」: 四角い石レンガの塔 9×9・高さ 24。一階は四方のアーチで吹き抜け、鐘はその天井に。頂は鐘の間と尖り屋根。東に語り部の家"""
    g.meta = {"rect": [-6, -6, 14, 7], "level": True}
    x0, z0, x1, z1 = -4, -4, 4, 4
    box(g, x0, -1, z0, x1, -1, z1, "polished_deepslate")
    shell(g, x0, 0, z0, x1, 23, z1, "stone_bricks", "deepslate_bricks")
    for (ax0, az0, ax1, az1) in ((-1, z0, 1, z0), (-1, z1, 1, z1), (x0, -1, x0, 1), (x1, -1, x1, 1)):
        air(g, ax0, 0, az0, ax1, 2, az1)
    box(g, x0 + 1, 3, z0 + 1, x1 - 1, 3, z1 - 1, "spruce_planks")
    g.set(0, 2, 0, "bell[attachment=ceiling,facing=north,powered=false]")
    g.mark("bell", 0, 2, 0)
    for y in (8, 13, 18):
        for (ax0, az0, ax1, az1) in ((0, z0, 0, z0), (0, z1, 0, z1), (x0, 0, x0, 0), (x1, 0, x1, 0)):
            air(g, ax0, y, az0, ax1, y + 1, az1)
    # 鐘の間 (y 20〜22 は四方が開く)
    for (ax0, az0, ax1, az1) in ((-2, z0, 2, z0), (-2, z1, 2, z1), (x0, -2, x0, 2), (x1, -2, x1, 2)):
        air(g, ax0, 20, az0, ax1, 22, az1)
    box(g, x0 + 1, 19, z0 + 1, x1 - 1, 19, z1 - 1, "spruce_planks")
    g.set(0, 22, 0, "bell[attachment=ceiling,facing=north,powered=false]")
    # 尖り屋根 (階段で段々に)
    for i in range(5):
        y = 24 + i
        box(g, x0 + i, y, z0 + i, x1 - i, y, z1 - i, "deepslate_tiles" if i < 4 else "deepslate_tile_slab[type=bottom]")
        if i >= 1:
            air(g, x0 + i + 1, y, z0 + i + 1, x1 - i - 1, y, z1 - i - 1)
    g.set(0, 29, 0, "lantern[hanging=false]")
    for dx, dz in ((-6, 6), (6, 6)):
        lantern_post(g, dx, 0, dz, 2)
    house(g, 6, -5, 13, 4)
    smoke_fire(g, 9, 6)
    g.mark("door", 9, 0, 4)
    g.mark("stand", 0, 0, 6)


def bell_b(g: Grid, rng=None):
    """B「鯨骨の鐘楼」: 深層岩の体に鯨の骨 (骨ブロック) の肋を四隅に立てた塔 11×11・高さ 26。骨の肋が屋根の上で合わさり、鐘は骨の梁に吊る。国の印の骨"""
    g.meta = {"rect": [-7, -7, 15, 8], "level": True}
    x0, z0, x1, z1 = -5, -5, 5, 5
    box(g, x0, -1, z0, x1, -1, z1, "polished_deepslate")
    shell(g, x0 + 1, 0, z0 + 1, x1 - 1, 21, z1 - 1, "deepslate_bricks")
    for (ax0, az0, ax1, az1) in ((-1, z0 + 1, 1, z0 + 1), (-1, z1 - 1, 1, z1 - 1), (x0 + 1, -1, x0 + 1, 1), (x1 - 1, -1, x1 - 1, 1)):
        air(g, ax0, 0, az0, ax1, 3, az1)
    box(g, x0 + 2, 4, z0 + 2, x1 - 2, 4, z1 - 2, "spruce_planks")
    g.set(0, 3, 0, "bell[attachment=ceiling,facing=north,powered=false]")
    g.mark("bell", 0, 3, 0)
    # 四隅の骨の肋: 外側に立ち、上で内へ曲がって頂で合わさる
    for sx, sz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        for y in range(0, 22):
            g.set(sx * 5, y, sz * 5, "bone_block[axis=y]")
        for i in range(1, 6):
            g.set(sx * (5 - i), 21 + i, sz * (5 - i), "bone_block[axis=y]")
    box(g, -1, 27, -1, 1, 27, 1, "bone_block[axis=y]")
    g.set(0, 28, 0, "lantern[hanging=false]")
    # 骨の梁 (一階の上、鐘の上の骨)
    box(g, x0, 5, 0, x1, 5, 0, "bone_block[axis=x]")
    box(g, 0, 5, z0, 0, 5, z1, "bone_block[axis=z]")
    for y in (9, 14, 19):
        for (ax0, az0, ax1, az1) in ((0, z0 + 1, 0, z0 + 1), (0, z1 - 1, 0, z1 - 1), (x0 + 1, 0, x0 + 1, 0), (x1 - 1, 0, x1 - 1, 0)):
            air(g, ax0, y, az0, ax1, y + 1, az1)
    box(g, x0 + 1, 22, z0 + 1, x1 - 1, 22, z1 - 1, "deepslate_tile_slab[type=bottom]")
    house(g, 7, -5, 14, 4, wall="stone_bricks", corner="bone_block[axis=y]")
    smoke_fire(g, 10, 6)
    g.mark("door", 10, 0, 4)
    g.mark("stand", 0, 0, 7)


def bell_c(g: Grid, rng=None):
    """C「段の鐘楼」: 三段の塔 (13 → 9 → 5)。段ごとに庇と灯り。一段目が鐘の間 (鐘は梁に)、二段目が語り部の部屋 (中に階段)。家は塔の中"""
    g.meta = {"rect": [-8, -8, 8, 8], "level": True}
    tiers = ((-6, 6, 0, 6), (-4, 4, 7, 12), (-2, 2, 13, 18))
    box(g, -6, -1, -6, 6, -1, 6, "stone_bricks")
    for (x0, x1, y0, y1) in tiers:
        shell(g, x0, y0, x0, x1, y1, x1, "stone_bricks" if y0 == 0 else "spruce_planks", "stripped_spruce_log[axis=y]")
        # 庇 (段の上に一回り大きい板)
        box(g, x0 - 1, y1 + 1, x0 - 1, x1 + 1, y1 + 1, x1 + 1, "spruce_slab[type=bottom]")
        air(g, x0, y1 + 1, x0, x1, y1 + 1, x1) if y0 < 13 else None
        for cx, cz in ((x0 - 1, x0 - 1), (x1 + 1, x0 - 1), (x0 - 1, x1 + 1), (x1 + 1, x1 + 1)):
            g.set(cx, y1, cz, "lantern[hanging=true]")
    # 一段目: 四方のアーチ、鐘
    for (ax0, az0, ax1, az1) in ((-1, -6, 1, -6), (-1, 6, 1, 6), (-6, -1, -6, 1), (6, -1, 6, 1)):
        air(g, ax0, 0, az0, ax1, 2, az1)
    box(g, -5, 6, 0, 5, 6, 0, "stripped_spruce_log[axis=x]")
    g.set(0, 5, 0, "bell[attachment=ceiling,facing=north,powered=false]")
    g.mark("bell", 0, 5, 0)
    # 二段目: 語り部の部屋 (床・寝台・書見台)。中の階段 (北東の隅)
    box(g, -3, 6, -3, 3, 6, 3, "spruce_planks")
    for i in range(6):
        g.set(4, i, 4 - i if i <= 4 else -1, "spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]") if i <= 4 else None
    g.set(-3, 7, -3, "red_bed[facing=south,part=head,occupied=false]")
    g.set(-3, 7, -2, "red_bed[facing=south,part=foot,occupied=false]")
    g.set(3, 7, -3, "lectern[facing=south,has_book=false,powered=false]")
    for x in (-2, 2):
        g.set(x, 9, -4, "glass_pane[north=false,south=false,east=true,west=true]")
        g.set(x, 9, 4, "glass_pane[north=false,south=false,east=true,west=true]")
    # 三段目: 見晴らしと頂の灯り
    box(g, -1, 19, -1, 1, 19, 1, "deepslate_tiles")
    g.set(0, 20, 0, "lantern[hanging=false]")
    smoke_fire(g, 0, 8)
    g.mark("door", 0, 0, 6)
    g.mark("stand", 0, 0, 8)


# ---------------------------------------------------------------- 空見の砦 3 案 (天守のない見張りの砦。王の館ではない。本丸は閉じ、塔だけが生きている)
def _wall_ring(g, x0, z0, x1, z1, h, t=2, wall="stone_bricks", cap=True):
    for (ax0, az0, ax1, az1) in ((x0, z0, x1, z0 + t - 1), (x0, z1 - t + 1, x1, z1), (x0, z0, x0 + t - 1, z1), (x1 - t + 1, z0, x1, z1)):
        box(g, ax0, 0, az0, ax1, h, az1, wall)
    if cap:
        for i in range(x0, x1 + 1, 2):
            g.set(i, h + 1, z0, wall)
            g.set(i, h + 1, z1, wall)
        for i in range(z0, z1 + 1, 2):
            g.set(x0, h + 1, i, wall)
            g.set(x1, h + 1, i, wall)


def _tower(g, x0, z0, w, h, wall="deepslate_bricks", cap="deepslate_tile_slab[type=bottom]"):
    box(g, x0, 0, z0, x0 + w - 1, h, z0 + w - 1, wall)
    air(g, x0 + 1, 1, z0 + 1, x0 + w - 2, h - 1, z0 + w - 2)
    box(g, x0, h + 1, z0, x0 + w - 1, h + 1, z0 + w - 1, cap)
    g.set(x0 + w // 2, h + 2, z0 + w // 2, "lantern[hanging=false]")


def _shut_gate(g, x, z, w, h, axis="z"):
    """閉じた門 (鉄格子) と碑"""
    if axis == "z":
        air(g, x - w // 2, 0, z - 1, x + w // 2, h, z)
        box(g, x - w // 2, 0, z, x + w // 2, h, z, "iron_bars[north=false,south=false,east=true,west=true,waterlogged=false]")
        g.set(x - w // 2 - 2, 0, z + 1, "skyabyss:tablet[facing=south,standing=true]")
        lantern_post(g, x - w // 2 - 1, 0, z + 2)
        lantern_post(g, x + w // 2 + 1, 0, z + 2)


def keep_a(g: Grid, rng=None):
    """A「四隅の塔の砦」: 44×40 の城壁 (高さ 10) と四隅の塔、北東に見張り塔 (高さ 30)。南の門は閉じ、中は閉じた本丸 (低い石の館)"""
    g.meta = {"rect": [-24, -22, 24, 22], "level": True}
    x0, z0, x1, z1 = -22, -20, 22, 20
    box(g, x0, -1, z0, x1, -1, z1, "cobbled_deepslate")
    _wall_ring(g, x0, z0, x1, z1, 9)
    for cx, cz in ((x0, z0), (x1 - 5, z0), (x0, z1 - 5), (x1 - 5, z1 - 5)):
        _tower(g, cx, cz, 6, 13)
    _tower(g, 8, -16, 9, 29, wall="stone_bricks", cap="spruce_slab[type=bottom]")
    for y in (8, 16, 24):
        air(g, 12, y, -16, 12, y + 1, -16)
        air(g, 12, y, -8, 12, y + 1, -8)
    # 本丸 (閉じた石の館 20×12、高さ 7)
    shell(g, -14, 0, -10, 5, 6, 1, "stone_bricks", "deepslate_bricks")
    box(g, -14, 7, -10, 5, 7, 1, "deepslate_tiles")
    g.set(-5, 0, 1, "iron_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
    g.set(-5, 1, 1, "iron_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
    _shut_gate(g, 0, z1, 5, 4)
    g.mark("stand", 0, 0, 26)


def keep_b(g: Grid, rng=None):
    """B「一本の大塔」: 低い輪の壁 (高さ 5) の中に、一本の太い見張り塔 (15×15・高さ 40) だけが立つ。塔の足もとに閉じた本丸の戸。「塔だけが生きている」を形に"""
    g.meta = {"rect": [-24, -22, 24, 22], "level": True}
    x0, z0, x1, z1 = -22, -20, 22, 20
    box(g, x0, -1, z0, x1, -1, z1, "cobbled_deepslate")
    _wall_ring(g, x0, z0, x1, z1, 4, t=2)
    for cx, cz in ((x0, z0), (x1 - 3, z0), (x0, z1 - 3), (x1 - 3, z1 - 3)):
        _tower(g, cx, cz, 4, 7)
    # 大塔 15×15、高さ 40。段ごとに細い窓、上の 3 段は少し広がる見張り台
    box(g, -7, 0, -7, 7, 36, 7, "stone_bricks")
    air(g, -6, 1, -6, 6, 35, 6)
    for cx, cz in ((-7, -7), (7, -7), (-7, 7), (7, 7)):
        box(g, cx, 0, cz, cx, 36, cz, "deepslate_bricks")
    for y in range(6, 34, 7):
        for (ax, az) in ((0, -7), (0, 7), (-7, 0), (7, 0)):
            air(g, ax, y, az, ax, y + 1, az)
    box(g, -8, 37, -8, 8, 39, 8, "deepslate_bricks")
    air(g, -7, 38, -7, 7, 39, 7)
    for i in range(-8, 9, 2):
        g.set(i, 40, -8, "deepslate_bricks")
        g.set(i, 40, 8, "deepslate_bricks")
        g.set(-8, 40, i, "deepslate_bricks")
        g.set(8, 40, i, "deepslate_bricks")
    box(g, -2, 37, -2, 2, 37, 2, "spruce_planks")
    g.set(0, 41, 0, "lantern[hanging=false]")
    # 足もとの閉じた戸 (鉄の戸) と碑
    air(g, 0, 0, 7, 0, 1, 7)
    g.set(0, 0, 7, "iron_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
    g.set(0, 1, 7, "iron_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
    g.set(-3, 0, 8, "skyabyss:tablet[facing=south,standing=true]")
    _shut_gate(g, 0, z1, 5, 3)
    g.mark("stand", 0, 0, 26)


def keep_c(g: Grid, rng=None):
    """C「岩の上の砦」: 中央に盛った岩 (高さ 6) の上に小さな砦 (24×18) と見張り塔。岩の南の階段が門へ。壁は岩の縁に沿う低い壁。遠くから「丘の上の砦」に見える"""
    g.meta = {"rect": [-24, -22, 24, 22], "level": True}
    x0, z0, x1, z1 = -22, -20, 22, 20
    box(g, x0, -1, z0, x1, -1, z1, "cobbled_deepslate")
    _wall_ring(g, x0, z0, x1, z1, 3, t=1, cap=False)
    # 岩 (段々に 3 段)
    for i, (hx, hz, y0, y1) in enumerate(((18, 15, 0, 1), (15, 12, 2, 3), (12, 9, 4, 5))):
        box(g, -hx, y0, -hz, hx, y1, hz, "cobbled_deepslate" if i < 2 else "deepslate_bricks")
    # 岩の上の砦 24×18 (y 6..13)、四隅の小塔、見張り塔
    shell(g, -12, 6, -9, 11, 12, 8, "stone_bricks", "deepslate_bricks")
    box(g, -11, 13, -8, 10, 13, 7, "deepslate_tiles")
    for cx, cz in ((-12, -9), (9, -9), (-12, 6), (9, 6)):
        box(g, cx, 6, cz, cx + 2, 15, cz + 2, "deepslate_bricks")
        g.set(cx + 1, 16, cz + 1, "lantern[hanging=false]")
    box(g, 2, 6, -8, 8, 30, -2, "stone_bricks")
    air(g, 3, 7, -7, 7, 29, -3)
    box(g, 2, 31, -8, 8, 31, -2, "spruce_slab[type=bottom]")
    g.set(5, 32, -5, "lantern[hanging=false]")
    # 南の階段 (岩を上る) と、砦の閉じた戸
    for i in range(6):
        box(g, -2, i, 15 - i, 2, i, 15 - i, "stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]")
    air(g, -1, 6, 8, 1, 8, 8)
    box(g, -1, 6, 8, 1, 8, 8, "iron_bars[north=false,south=false,east=true,west=true,waterlogged=false]")
    g.set(-3, 6, 9, "skyabyss:tablet[facing=south,standing=true]")
    _shut_gate(g, 0, z1, 5, 2)
    g.mark("stand", 0, 0, 26)




SUBJECTS = {
    "bell": {
        "title": "大鐘楼（語り部の家つき）",
        "options": {"a": bell_a, "b": bell_b, "c": bell_c},
        "notes": {"a": "A 石の鐘楼（別棟の家）", "b": "B 鯨骨の鐘楼（骨の肋）", "c": "C 段の鐘楼（家は塔の中）"},
        "eye": ((0, 1.6, 22), (0, 8, 0)),
    },
}


SUBJECTS["keep"] = {
    "title": "空見の砦（天守のない見張りの砦）",
    "options": {"a": keep_a, "b": keep_b, "c": keep_c},
    "notes": {"a": "A 四隅の塔の砦（城壁と本丸）", "b": "B 一本の大塔（塔だけが生きている）", "c": "C 岩の上の砦（丘の砦）"},
    "eye": ((0, 1.6, 44), (0, 10, 0)),
}


# ---------------------------------------------------------------- 鯨追いの組合 3 案 (イサナの一族の組合。板・名簿・順位の石。一章で開く。戸は閉じている)
def _board(g, x, z, facing="west"):
    """言伝ての板 (二本柱と庇。07 の 85) を建物の外に"""
    for dz in (-2, 2):
        box(g, x, 0, z + dz, x, 3, z + dz, "stripped_spruce_log[axis=y]")
    box(g, x, 4, z - 3, x, 4, z + 3, "spruce_slab[type=bottom]")
    box(g, x, 1, z - 1, x, 2, z + 1, "spruce_planks")
    g.set(x, 0, z - 4, "skyabyss:tablet[facing=%s,standing=true]" % facing)


def guild_a(g: Grid, rng=None):
    """A「長屋の組合」: 45×23 の長い館 (石の腰壁と木の壁、切妻)。棟の上に鯨骨の肋を三つ渡す。西の大戸は閉じ、戸の脇に言伝ての板と名簿の碑"""
    g.meta = {"rect": [-26, -14, 24, 14], "level": True}
    x0, z0, x1, z1 = -22, -11, 22, 11
    box(g, x0, -1, z0, x1, -1, z1, "spruce_planks")
    shell(g, x0, 0, z0, x1, 2, z1, "stone_bricks", "stripped_spruce_log[axis=y]")
    shell(g, x0, 3, z0, x1, 7, z1, "spruce_planks", "stripped_spruce_log[axis=y]")
    for x in range(x0 + 3, x1 - 2, 4):
        g.set(x, 4, z0, "glass_pane[north=false,south=false,east=true,west=true]")
        g.set(x, 4, z1, "glass_pane[north=false,south=false,east=true,west=true]")
    gable(g, x0 - 1, x1 + 1, z0 - 1, z1 + 1, 8)
    for x in (-14, 0, 14):
        for i in range(0, 12):
            g.set(x, 8 + 6 - abs(i - 6) + 7 - 7, z0 - 1 + i * 2 if False else z0 - 1 + i * 2, "bone_block[axis=z]") if False else None
    # 肋: 棟をまたぐ骨の弧 (三つ)
    for x in (-14, 0, 14):
        for i, z in enumerate(range(z0 - 2, z1 + 3)):
            y = 9 + 11 - abs(z) * 11 // 13
            g.set(x, y, z, "bone_block[axis=z]")
    # 西の大戸 (閉じた鉄の戸 2 枚) と板・碑
    for dz in (-1, 0):
        g.set(x0, 0, dz, "iron_door[facing=east,half=lower,hinge=%s,open=false,powered=false]" % ("left" if dz else "right"))
        g.set(x0, 1, dz, "iron_door[facing=east,half=upper,hinge=%s,open=false,powered=false]" % ("left" if dz else "right"))
    air(g, x0, 2, -1, x0, 2, 0)
    _board(g, x0 - 3, 6, "west")
    g.set(x0 - 2, 0, -5, "skyabyss:tablet[facing=west,standing=true]")
    lantern_post(g, x0 - 2, 0, -3)
    lantern_post(g, x0 - 2, 0, 3)
    g.mark("stand", x0 - 6, 0, 0)


def guild_b(g: Grid, rng=None):
    """B「鯨骨の屋根」: 鯨の肋骨そのものを屋根にした館 (骨の弧が 7 本並び、あいだに木の板)。入口に鯨の頭骨の門。前庭に板と碑"""
    g.meta = {"rect": [-26, -14, 24, 14], "level": True}
    x0, z0, x1, z1 = -20, -10, 20, 10
    box(g, x0, -1, z0, x1, -1, z1, "stone_bricks")
    shell(g, x0, 0, z0, x1, 3, z1, "spruce_planks", "bone_block[axis=y]")
    for x in range(x0, x1 + 1, 6):
        for z in range(z0 - 1, z1 + 2):
            y = 4 + 9 - (abs(z) * 9) // 11
            g.set(x, y, z, "bone_block[axis=z]")
            if x + 3 <= x1:
                g.set(x + 3, y, z, "spruce_slab[type=bottom]") if z % 2 == 0 else g.set(x + 3, y, z, "spruce_planks")
    for x in range(x0 + 1, x1, 1):
        if x % 6 != 0 and (x - x0) % 6 != 3:
            for z in range(z0 - 1, z1 + 2):
                y = 4 + 9 - (abs(z) * 9) // 11
                g.set(x, y, z, "spruce_planks")
    # 頭骨の門 (西): 骨の弧と、目の穴の灯り
    for i in range(6):
        g.set(x0 - 4, i, -3 + 0, "bone_block[axis=y]")
        g.set(x0 - 4, i, 3, "bone_block[axis=y]")
    box(g, x0 - 4, 6, -3, x0 - 4, 6, 3, "bone_block[axis=z]")
    g.set(x0 - 4, 7, -1, "bone_block[axis=y]")
    g.set(x0 - 4, 7, 1, "bone_block[axis=y]")
    g.set(x0 - 4, 5, -2, "lantern[hanging=true]")
    g.set(x0 - 4, 5, 2, "lantern[hanging=true]")
    for dz in (-1, 0):
        g.set(x0, 0, dz, "iron_door[facing=east,half=lower,hinge=%s,open=false,powered=false]" % ("left" if dz else "right"))
        g.set(x0, 1, dz, "iron_door[facing=east,half=upper,hinge=%s,open=false,powered=false]" % ("left" if dz else "right"))
    _board(g, x0 - 7, 7, "west")
    g.set(x0 - 6, 0, -7, "skyabyss:tablet[facing=west,standing=true]")
    g.mark("stand", x0 - 9, 0, 0)


def guild_c(g: Grid, rng=None):
    """C「二階の組合」: 二階建ての館 (一階は石、二階は木の張り出し)。西に見張りの帆柱 (鯨追いの印の旗) と、二階の露台。板は露台の下"""
    g.meta = {"rect": [-26, -14, 24, 14], "level": True}
    x0, z0, x1, z1 = -20, -10, 20, 10
    box(g, x0, -1, z0, x1, -1, z1, "stone_bricks")
    shell(g, x0, 0, z0, x1, 4, z1, "stone_bricks", "deepslate_bricks")
    box(g, x0 - 1, 5, z0 - 1, x1 + 1, 5, z1 + 1, "spruce_planks")
    shell(g, x0 - 1, 6, z0 - 1, x1 + 1, 9, z1 + 1, "spruce_planks", "stripped_spruce_log[axis=y]")
    for x in range(x0 + 2, x1 - 1, 4):
        g.set(x, 2, z0, "glass_pane[north=false,south=false,east=true,west=true]")
        g.set(x, 7, z0 - 1, "glass_pane[north=false,south=false,east=true,west=true]")
        g.set(x, 7, z1 + 1, "glass_pane[north=false,south=false,east=true,west=true]")
    gable(g, x0 - 2, x1 + 2, z0 - 2, z1 + 2, 10)
    # 西の露台 (二階) と帆柱・旗
    box(g, x0 - 5, 5, -4, x0 - 2, 5, 4, "spruce_planks")
    box(g, x0 - 5, 6, -4, x0 - 5, 6, 4, "spruce_fence")
    box(g, x0 - 6, 0, 0, x0 - 6, 16, 0, "stripped_spruce_log[axis=y]")
    box(g, x0 - 5, 12, 0, x0 - 2, 12, 0, "white_wool")
    box(g, x0 - 5, 13, 0, x0 - 2, 14, 0, "light_blue_wool")
    g.set(x0 - 6, 17, 0, "lantern[hanging=false]")
    for dz in (-1, 0):
        g.set(x0, 0, dz, "iron_door[facing=east,half=lower,hinge=%s,open=false,powered=false]" % ("left" if dz else "right"))
        g.set(x0, 1, dz, "iron_door[facing=east,half=upper,hinge=%s,open=false,powered=false]" % ("left" if dz else "right"))
    _board(g, x0 - 3, 7, "west")
    g.set(x0 - 2, 0, -6, "skyabyss:tablet[facing=west,standing=true]")
    g.mark("stand", x0 - 9, 0, 0)


SUBJECTS["guild"] = {
    "title": "鯨追いの組合（板・名簿・順位の石）",
    "options": {"a": guild_a, "b": guild_b, "c": guild_c},
    "notes": {"a": "A 長屋の組合（棟に骨の肋）", "b": "B 鯨骨の屋根（肋骨の館）", "c": "C 二階の組合（露台と帆柱）"},
    "eye": ((-44, 1.6, 0), (0, 6, 0)),
}


# ---------------------------------------------------------------- 四巨神の社 3 案 (国の信仰「四体が空を支える」。転生の台座・鍵の間。二章で開く。戸は閉じている)
TITAN_BLOCKS = ("stone_bricks", "prismarine_bricks", "cut_copper", "polished_blackstone_bricks")  # 岩鯨・潮殻・雷翼・余燼


def _altar(g, cx, cz, y):
    """鍵の間の台 (四つの欠片を納める台)。中央に灯り"""
    box(g, cx - 1, y, cz - 1, cx + 1, y, cz + 1, "polished_deepslate")
    g.set(cx, y + 1, cz, "lantern[hanging=false]")


def shrine_a(g: Grid, rng=None):
    """A「八角の社」: 深層岩の八角堂 (幅 28・高さ 10)。四方に巨神の色の柱と灯り、頂に小さな塔と灯り。東の戸は閉じる"""
    g.meta = {"rect": [-20, -20, 20, 20], "level": True}
    r = 14
    box(g, -r, -1, -r, r, -1, r, "polished_deepslate")
    box(g, -r, 0, -r, r, 9, r, "deepslate_bricks")
    air(g, -r + 1, 0, -r + 1, r - 1, 8, r - 1)
    for ax, az in ((-r, -r), (r - 5, -r), (-r, r - 5), (r - 5, r - 5)):
        air(g, ax, -1, az, ax + 5, 10, az + 5)
        box(g, ax, -1, az, ax + 5, -1, az + 5, "grass_block")
    box(g, -r + 2, 10, -r + 2, r - 2, 10, r - 2, "deepslate_tile_slab[type=bottom]")
    box(g, -2, 11, -2, 2, 13, 2, "deepslate_bricks")
    g.set(0, 14, 0, "lantern[hanging=false]")
    _altar(g, 0, 0, 0)
    for (dx, dz), blk in zip(((-r - 3, 0), (r + 3, 0), (0, -r - 3), (0, r + 3)), TITAN_BLOCKS):
        box(g, dx, 0, dz, dx, 4, dz, blk)
        g.set(dx, 5, dz, "lantern[hanging=false]")
    g.set(r, 0, 0, "iron_door[facing=west,half=lower,hinge=left,open=false,powered=false]")
    g.set(r, 1, 0, "iron_door[facing=west,half=upper,hinge=left,open=false,powered=false]")
    g.set(r + 1, 0, 2, "skyabyss:tablet[facing=east,standing=true]")
    g.mark("stand", r + 6, 0, 0)


def shrine_b(g: Grid, rng=None):
    """B「四つの塔の社」: 四角い堂 (24×24・高さ 8) の四隅に巨神ごとの材質の塔 (高さ 16)。堂の屋根は段々、中央の鍵の間に天窓。戸は東"""
    g.meta = {"rect": [-20, -20, 20, 20], "level": True}
    h = 12
    box(g, -h, -1, -h, h, -1, h, "polished_deepslate")
    shell(g, -h, 0, -h, h, 7, h, "stone_bricks", "deepslate_bricks")
    for i in range(3):
        box(g, -h + 1 + i * 2, 8 + i, -h + 1 + i * 2, h - 1 - i * 2, 8 + i, h - 1 - i * 2, "deepslate_tiles")
        if i < 2:
            air(g, -h + 3 + i * 2, 8 + i, -h + 3 + i * 2, h - 3 - i * 2, 8 + i, h - 3 - i * 2)
    air(g, -1, 10, -1, 1, 10, 1)
    for (ax, az), blk in zip(((-h - 2, -h - 2), (h - 2, -h - 2), (-h - 2, h - 2), (h - 2, h - 2)), TITAN_BLOCKS):
        box(g, ax, 0, az, ax + 4, 15, az + 4, blk)
        air(g, ax + 1, 1, az + 1, ax + 3, 14, az + 3)
        box(g, ax, 16, az, ax + 4, 16, az + 4, "deepslate_tile_slab[type=bottom]")
        g.set(ax + 2, 17, az + 2, "lantern[hanging=false]")
    _altar(g, 0, 0, 0)
    g.set(h, 0, 0, "iron_door[facing=west,half=lower,hinge=left,open=false,powered=false]")
    g.set(h, 1, 0, "iron_door[facing=west,half=upper,hinge=left,open=false,powered=false]")
    g.set(h + 1, 0, 2, "skyabyss:tablet[facing=east,standing=true]")
    g.mark("stand", h + 6, 0, 0)


def shrine_c(g: Grid, rng=None):
    """C「石の輪の社」: 屋根のない社。四つの大きな巨神の石 (高さ 9) が輪に立ち、中央の鍵の間を骨の梁が覆う。輪は低い壁と閉じた門で囲う"""
    g.meta = {"rect": [-20, -20, 20, 20], "level": True}
    r = 15
    box(g, -r, -1, -r, r, -1, r, "polished_deepslate")
    for i in range(-r, r + 1):
        for j in range(-r, r + 1):
            if abs(i) == r or abs(j) == r:
                g.set(i, 0, j, "deepslate_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]")
    for (dx, dz), blk in zip(((-9, -9), (9, -9), (-9, 9), (9, 9)), TITAN_BLOCKS):
        box(g, dx - 1, 0, dz - 1, dx + 1, 8, dz + 1, blk)
        g.set(dx, 9, dz, "lantern[hanging=false]")
        # 骨の梁: 石から中央へ
        for k in range(1, 9):
            x = dx - (dx // 9) * k
            z = dz - (dz // 9) * k
            g.set(x, 8 + k // 3, z, "bone_block[axis=y]")
    box(g, -1, 11, -1, 1, 11, 1, "bone_block[axis=y]")
    g.set(0, 12, 0, "lantern[hanging=true]")
    _altar(g, 0, 0, 0)
    air(g, r, 0, -1, r, 0, 1)
    box(g, r, 0, -1, r, 2, 1, "iron_bars[north=false,south=false,east=true,west=true,waterlogged=false]")
    g.set(r + 1, 0, 3, "skyabyss:tablet[facing=east,standing=true]")
    g.mark("stand", r + 6, 0, 0)


SUBJECTS["shrine"] = {
    "title": "四巨神の社（転生の台座・鍵の間）",
    "options": {"a": shrine_a, "b": shrine_b, "c": shrine_c},
    "notes": {"a": "A 八角の社（深層岩の堂）", "b": "B 四つの塔の社（巨神の色の塔）", "c": "C 石の輪の社（屋根のない輪）"},
    "eye": ((36, 1.6, 0), (0, 6, 0)),
}


# ---------------------------------------------------------------- 空見の館 3 案 (議事の間と伝承の書庫。「忘れないうちに書け」の出どころ。四章で開く。戸は閉じている)
def _shelves(g, x0, z0, x1, z1, y0, y1):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            g.set(x, y, z0, "bookshelf")
            g.set(x, y, z1, "bookshelf")


def hall_a(g: Grid, rng=None):
    """A「書庫の館」: 石の長い館 (23×13・高さ 7)。背の高い窓、切妻の屋根、中は二段の本棚と書見台。南の戸は閉じた鉄の戸"""
    g.meta = {"rect": [-14, -9, 14, 11], "level": True}
    x0, z0, x1, z1 = -11, -6, 11, 6
    box(g, x0, -1, z0, x1, -1, z1, "polished_andesite")
    shell(g, x0, 0, z0, x1, 6, z1, "stone_bricks", "deepslate_bricks")
    for x in range(x0 + 2, x1 - 1, 3):
        for y in (1, 2, 3):
            g.set(x, y, z0, "glass_pane[north=false,south=false,east=true,west=true]")
            if x != 0:
                g.set(x, y, z1, "glass_pane[north=false,south=false,east=true,west=true]")
    gable(g, x0 - 1, x1 + 1, z0 - 1, z1 + 1, 7, block="deepslate_tile_stairs", cap="deepslate_tile_slab[type=bottom]")
    _shelves(g, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, 1)
    g.set(0, 0, 0, "lectern[facing=south,has_book=false,powered=false]")
    g.set(-4, 5, 0, "lantern[hanging=true]")
    g.set(4, 5, 0, "lantern[hanging=true]")
    g.set(0, 0, z1, "iron_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
    g.set(0, 1, z1, "iron_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
    g.set(-2, 0, z1 + 1, "skyabyss:tablet[facing=south,standing=true]")
    lantern_post(g, -3, 0, z1 + 2)
    lantern_post(g, 3, 0, z1 + 2)
    g.mark("stand", 0, 0, 10)


def hall_b(g: Grid, rng=None):
    """B「記録の塔の館」: 館 (19×13) の東に丸い記録の塔 (直径 9・高さ 18)。塔に巻き窓と頂の灯り。帳面は塔に積まれる。戸は南"""
    g.meta = {"rect": [-14, -9, 16, 11], "level": True}
    x0, z0, x1, z1 = -11, -6, 7, 6
    box(g, x0, -1, z0, x1 + 8, -1, z1, "polished_andesite")
    shell(g, x0, 0, z0, x1, 5, z1, "stone_bricks", "deepslate_bricks")
    for x in range(x0 + 2, x1 - 1, 3):
        for y in (1, 2):
            g.set(x, y, z0, "glass_pane[north=false,south=false,east=true,west=true]")
            if x != -2:
                g.set(x, y, z1, "glass_pane[north=false,south=false,east=true,west=true]")
    gable(g, x0 - 1, x1 + 1, z0 - 1, z1 + 1, 6, block="deepslate_tile_stairs", cap="deepslate_tile_slab[type=bottom]")
    _shelves(g, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, 1)
    # 丸い塔 (八角で近似)
    cx, cz, rr = 11, 0, 4
    for y in range(0, 18):
        for x in range(cx - rr, cx + rr + 1):
            for z in range(cz - rr, cz + rr + 1):
                d = abs(x - cx) + abs(z - cz)
                if d == rr + 1 or (abs(x - cx) == rr and abs(z - cz) <= 1) or (abs(z - cz) == rr and abs(x - cx) <= 1):
                    g.set(x, y, z, "deepslate_bricks" if y % 6 != 3 or (x + z) % 2 else "glass_pane[north=false,south=false,east=false,west=false]")
    for x in range(cx - rr, cx + rr + 1):
        for z in range(cz - rr, cz + rr + 1):
            if abs(x - cx) + abs(z - cz) <= rr:
                g.set(x, 18, z, "deepslate_tile_slab[type=bottom]")
    g.set(cx, 19, cz, "lantern[hanging=false]")
    g.set(-2, 0, z1, "iron_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
    g.set(-2, 1, z1, "iron_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
    g.set(-4, 0, z1 + 1, "skyabyss:tablet[facing=south,standing=true]")
    lantern_post(g, -5, 0, z1 + 2)
    lantern_post(g, 1, 0, z1 + 2)
    g.mark("stand", -2, 0, 10)


def hall_c(g: Grid, rng=None):
    """C「回廊の館」: 館 (23×11) の南に柱の回廊 (庇の下の通路)。回廊の壁に四つの章の碑が並ぶ。屋根は平らな露台で見張りに上がれる"""
    g.meta = {"rect": [-14, -9, 14, 12], "level": True}
    x0, z0, x1, z1 = -11, -7, 11, 3
    box(g, x0, -1, z0, x1, -1, z1 + 4, "polished_andesite")
    shell(g, x0, 0, z0, x1, 6, z1, "stone_bricks", "deepslate_bricks")
    box(g, x0 - 1, 7, z0 - 1, x1 + 1, 7, z1 + 4, "deepslate_tiles")
    box(g, x0 - 1, 8, z0 - 1, x1 + 1, 8, z0 - 1, "deepslate_brick_wall[up=true,north=none,south=none,east=low,west=low,waterlogged=false]")
    box(g, x0 - 1, 8, z1 + 4, x1 + 1, 8, z1 + 4, "deepslate_brick_wall[up=true,north=none,south=none,east=low,west=low,waterlogged=false]")
    # 回廊: 南の面に柱 (3 おき)。庇は露台の屋根がそのまま
    for x in range(x0 - 1, x1 + 2, 3):
        box(g, x, 0, z1 + 4, x, 6, z1 + 4, "deepslate_bricks")
    for x in (-8, -3, 3, 8):
        g.set(x, 0, z1 + 1, "skyabyss:tablet[facing=south,standing=true]")
        g.set(x, 2, z1, "glass_pane[north=false,south=false,east=true,west=true]")
    for x in range(x0 + 2, x1 - 1, 3):
        for y in (1, 2, 3):
            g.set(x, y, z0, "glass_pane[north=false,south=false,east=true,west=true]")
    _shelves(g, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, 1)
    g.set(0, 0, z1, "iron_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
    g.set(0, 1, z1, "iron_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
    for x in (-6, 0, 6):
        g.set(x, 6, z1 + 2, "lantern[hanging=true]")
    g.mark("stand", 0, 0, 11)


SUBJECTS["hall"] = {
    "title": "空見の館（議事・伝承の書庫）",
    "options": {"a": hall_a, "b": hall_b, "c": hall_c},
    "notes": {"a": "A 書庫の館（高い窓と切妻）", "b": "B 記録の塔の館（丸い塔）", "c": "C 回廊の館（柱の回廊と露台）"},
    "eye": ((0, 1.6, 26), (0, 5, 0)),
}


def render(subject: str):
    sub = SUBJECTS[subject]
    cols = []
    out = OUT / subject
    out.mkdir(parents=True, exist_ok=True)
    for key, fn in sub["options"].items():
        g = Grid()
        fn(g)
        d = osite.to_dump("shelter", g, margin=4)
        osite.srender._colors = lambda dd: osite.colors_of(dd).astype(osite.np.int16)
        top = osite.srender.top(d, scale=10)
        iso = osite.srender.iso(d, step=1)
        f = max(1, 460 // iso.width)
        iso = iso.resize((iso.width * f, iso.height * f), osite.Image.NEAREST)
        if iso.width > 700:
            iso = iso.resize((700, int(iso.height * 700 / iso.width)), osite.Image.NEAREST)
        (ex, ey, ez), (ax, ay, az) = sub["eye"]
        dx, dy, dz = ax - ex, ay - ey, az - ez
        yaw = math.degrees(math.atan2(-dx, dz)) % 360
        pitch = math.degrees(math.atan2(-dy, math.hypot(dx, dz)))
        eye = osite.eye_view(d, (ex, ey, ez), yaw, pitch, far=60)
        top.save(out / f"{key}_top.png")
        iso.save(out / f"{key}_iso.png")
        eye.save(out / f"{key}_eye.png")
        cols.append((sub["notes"][key], [top, iso, eye]))
    sh = osite.sheet(sub["title"] + " 3 案（上から・斜め・人の目線）", cols, "ブロックの配置をそのまま描いた図。採用案を tools/gen_capital.py に写す。")
    path = OUT / f"{subject}_sheet.png"
    sh.save(path)
    print("wrote", path)


if __name__ == "__main__":
    render(sys.argv[1] if len(sys.argv) > 1 else "bell")
