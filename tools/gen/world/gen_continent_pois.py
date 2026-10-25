#!/usr/bin/env python3
"""天と淵の大陸の見どころ (docs/design/40 の 3 章。2026-10-25「大きな地図が平原ばかりで何もない。Wynncraft を手本に作り込む」)。

  python tools/gen/world/gen_continent_pois.py
  -> src/main/resources/data/skyabyss/opening/poi_<種類>.json (部品)
     src/main/resources/data/skyabyss/continent/pois.json (置き場: 種類と x, z。world/continent/ContinentPois が近くに人が来たら建てる)

土地ごとの見どころ (どれも北向きに書き、置くとき回す):
  都のまわり  風車・畑と納屋・見張りの塔・道ばたの祠
  岩鯨の丘陵  巨神の肋骨・崩れた僧院・石の輪
  潮の浜      灯台・打ち上げられた船・漁師の小屋
  嵐の高地    風の民の崩れた門・避雷の塔・山小屋
  灰の荒野    焼けた砦・炉の跡・玄武岩の門
  どこにでも  野盗の野営・崩れた家
置き場は図 (高さ・土地) から決まった乱数で選ぶ: 陸で平ら、道・町・跡地・都・はじまりの島から離れ、見どころどうしも離す
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, 'tools', 'lib'))
from gen_region_towns import BP, stairs  # noqa: E402

OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss', 'opening')
CONT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss', 'continent')

AIR = 'minecraft:air'


def slab(b, half='bottom'):
    return f'{b}_slab[type={half},waterlogged=false]'


def log(b, axis='y'):
    return f'{b}[axis={axis}]'


def lantern(hang=False):
    return f'minecraft:lantern[hanging={"true" if hang else "false"},waterlogged=false]'


def disc(bp, cx, cz, r, y, state, chance=None, rng=None):
    for x in range(int(cx - r), int(cx + r) + 1):
        for z in range(int(cz - r), int(cz + r) + 1):
            if (x - cx) ** 2 + (z - cz) ** 2 <= r * r and (chance is None or rng.random() < chance):
                bp.set(x, y, z, state)


def gable(bp, x0, z0, x1, z1, y, roof, ridge):
    """x に沿う切妻 (北の斜面は南へ上がる)"""
    a, b, layer = z0 - 1, z1 + 1, 0
    while a < b:
        for x in range(x0 - 1, x1 + 2):
            bp.set(x, y + layer, a, stairs(roof, 'south'))
            bp.set(x, y + layer, b, stairs(roof, 'north'))
        a += 1
        b -= 1
        layer += 1
    if a == b:
        for x in range(x0 - 1, x1 + 2):
            bp.set(x, y + layer, a, ridge)
    return y + layer


def walls(bp, x0, z0, x1, z1, h, wall, frame, broken=0.0, rng=None, windows=True):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            for y in range(h):
                if edge:
                    corner = x in (x0, x1) and z in (z0, z1)
                    if not corner and broken and rng.random() < broken * (y / max(1, h - 1)):
                        continue
                    bp.set(x, y, z, frame if corner else wall)
                else:
                    bp.set(x, y, z, AIR)
    if windows:
        for x in range(x0 + 2, x1 - 1, 3):
            for z in (z0, z1):
                if bp.get(x, 1, z) == wall:
                    bp.set(x, 1, z, 'minecraft:glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]')


def door(bp, x, z, facing, wood):
    bp.set(x, 0, z, f'minecraft:{wood}_door[facing={facing},half=lower,hinge=left,open=false,powered=false]')
    bp.set(x, 1, z, f'minecraft:{wood}_door[facing={facing},half=upper,hinge=left,open=false,powered=false]')


def ground(bp, r, top, rng, alt=None, chance=0.25):
    for x in range(-r, r + 1):
        for z in range(-r, r + 1):
            if x * x + z * z <= r * r:
                bp.set(x, -1, z, alt if alt and rng.random() < chance else top)


# ====================================================================== 都のまわり
def windmill(rng):
    bp = BP('poi_windmill')
    ground(bp, 7, 'minecraft:grass_block[snowy=false]', rng, 'minecraft:coarse_dirt', 0.15)
    # 石の胴 (下は太く、上は細い八角)
    for y in range(0, 12):
        r = 4 if y < 5 else 3
        for x in range(-r, r + 1):
            for z in range(-r, r + 1):
                d = abs(x) + abs(z)
                if max(abs(x), abs(z)) == r and d <= r + r // 2 or (max(abs(x), abs(z)) < r and d == r + r // 2):
                    bp.set(x, y, z, 'minecraft:stone_bricks' if y < 5 else 'minecraft:white_terracotta')
                elif max(abs(x), abs(z)) < r and d < r + r // 2:
                    bp.set(x, y, z, AIR)
    for y in range(0, 12, 4):
        bp.set(0, y + 1, -4 if y < 5 else -3, 'minecraft:glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]')
    door(bp, 0, 4, 'south', 'spruce')
    bp.set(0, 2, 5, lantern(True)) if False else None
    # 屋根 (とがった帽子)
    for k in range(0, 5):
        r = 4 - k
        for x in range(-r, r + 1):
            for z in range(-r, r + 1):
                if max(abs(x), abs(z)) == r:
                    bp.set(x, 12 + k, z, 'minecraft:dark_oak_planks' if k < 4 else 'minecraft:dark_oak_slab[type=bottom,waterlogged=false]')
    bp.set(0, 16, 0, 'minecraft:dark_oak_fence')
    # 羽根 (北の面、x と y の十字。軸は胴から一つ外)
    bp.set(0, 10, -4, 'minecraft:spruce_log[axis=z]')
    bp.set(0, 10, -5, 'minecraft:spruce_log[axis=z]')
    for k in range(1, 9):
        for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)):
            bp.set(dx, 10 + dy, -5, 'minecraft:spruce_fence' if k < 2 else 'minecraft:white_wool' if k % 2 else 'minecraft:spruce_planks')
            if k > 1:
                ox, oy = (0, 1) if dy == 0 else (1, 0)
                bp.set(dx + ox, 10 + dy + oy, -5, 'minecraft:white_wool')
    # 粉の袋と荷車
    for x, z in ((3, 5), (4, 4), (-3, 5)):
        bp.set(x, 0, z, 'minecraft:barrel[facing=up,open=false]')
    bp.set(5, 0, 5, 'minecraft:hay_block[axis=y]')
    bp.set(5, 1, 5, 'minecraft:hay_block[axis=y]')
    return bp


def farm(rng):
    bp = BP('poi_farm')
    ground(bp, 11, 'minecraft:grass_block[snowy=false]', rng)
    # 畑 (二枚、水の溝)
    for (x0, x1) in ((-10, -2), (2, 10)):
        for x in range(x0, x1 + 1):
            for z in range(-9, 0):
                if x in (x0, x1) or z in (-9, -1):
                    bp.set(x, -1, z, 'minecraft:spruce_log[axis=y]' if (x in (x0, x1) and z in (-9, -1)) else 'minecraft:oak_log[axis=x]' if z in (-9, -1) else 'minecraft:oak_log[axis=z]')
                elif x == (x0 + x1) // 2:
                    bp.set(x, -1, z, 'minecraft:water[level=0]')
                else:
                    bp.set(x, -1, z, 'minecraft:farmland[moisture=7]')
                    crop = rng.choice(['minecraft:wheat[age=7]', 'minecraft:carrots[age=7]', 'minecraft:potatoes[age=7]', 'minecraft:wheat[age=5]'])
                    bp.set(x, 0, z, crop)
    # かかし
    bp.fill(0, 0, -5, 0, 1, -5, 'minecraft:oak_fence')
    bp.set(0, 2, -5, 'minecraft:carved_pumpkin[facing=south]')
    bp.set(-1, 1, -5, 'minecraft:oak_fence')
    bp.set(1, 1, -5, 'minecraft:oak_fence')
    # 納屋 (南)
    walls(bp, -5, 3, 5, 9, 4, 'minecraft:spruce_planks', 'minecraft:stripped_spruce_log[axis=y]')
    for x in range(-4, 5):
        for z in range(4, 9):
            bp.set(x, -1, z, 'minecraft:coarse_dirt')
    for x in (-1, 0, 1):
        for y in range(0, 3):
            bp.set(x, y, 3, AIR)
    top = gable(bp, -5, 3, 5, 9, 4, 'minecraft:dark_oak_stairs', slab('minecraft:dark_oak'))
    for x, z in ((-3, 7), (-3, 8), (3, 7), (3, 8), (2, 8)):
        bp.set(x, 0, z, 'minecraft:hay_block[axis=y]')
    bp.set(-3, 1, 8, 'minecraft:hay_block[axis=y]')
    bp.set(0, 3, 6, lantern(True))
    # 柵 (畑の外まわり、一部)
    for x in range(-11, 12):
        if x % 5 != 0:
            bp.set(x, 0, -11, 'minecraft:oak_fence')
    bp.set(-11, 0, -5, 'minecraft:composter[level=4]')
    bp.set(9, 0, 2, 'minecraft:water_cauldron[level=3]')
    return bp


def watchtower(rng):
    bp = BP('poi_watchtower')
    ground(bp, 6, 'minecraft:grass_block[snowy=false]', rng, 'minecraft:cobblestone', 0.3)
    for y in range(0, 14):
        for x in range(-3, 4):
            for z in range(-3, 4):
                edge = max(abs(x), abs(z)) == 3
                if edge:
                    c = x in (-3, 3) and z in (-3, 3)
                    bp.set(x, y, z, 'minecraft:spruce_log[axis=y]' if c else ('minecraft:cobblestone' if y < 4 else 'minecraft:mossy_cobblestone' if rng.random() < 0.2 else 'minecraft:stone_bricks'))
                else:
                    bp.set(x, y, z, AIR)
    # はしご (内側の北の壁)
    for y in range(0, 14):
        bp.set(0, y, -2, 'minecraft:ladder[facing=south,waterlogged=false]')
    door(bp, 0, 3, 'south', 'spruce')
    # 見張りの床 (張り出し) と胸壁
    for x in range(-4, 5):
        for z in range(-4, 5):
            bp.set(x, 14, z, 'minecraft:spruce_planks')
            if max(abs(x), abs(z)) == 4:
                bp.set(x, 15, z, 'minecraft:spruce_fence')
    bp.set(0, 14, -2, 'minecraft:spruce_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]')
    for x, z in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
        bp.fill(x, 15, z, x, 17, z, 'minecraft:spruce_log[axis=y]')
    for x in range(-4, 5):
        for z in range(-4, 5):
            if max(abs(x), abs(z)) <= 4:
                bp.set(x, 18, z, slab('minecraft:spruce'))
    bp.set(0, 17, 0, lantern(True))
    bp.set(2, 15, 2, 'minecraft:barrel[facing=up,open=false]')
    for y in range(4, 13, 4):
        for x, z in ((0, -3), (-3, 0), (3, 0)):
            bp.set(x, y, z, AIR)
    return bp


def roadside_shrine(rng):
    bp = BP('poi_roadside_shrine')
    ground(bp, 3, 'minecraft:moss_block', rng, 'minecraft:grass_block[snowy=false]', 0.4)
    bp.fill(-1, 0, -1, 1, 0, 1, 'minecraft:mossy_stone_bricks')
    bp.set(0, 1, 0, 'minecraft:chiseled_stone_bricks')
    bp.set(0, 2, 0, 'minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]')
    bp.set(0, 3, 0, lantern())
    for x, z in ((-1, 1), (1, 1)):
        bp.set(x, 1, z, 'minecraft:flower_pot')
    bp.set(0, 0, 2, stairs('minecraft:mossy_stone_brick_stairs', 'north'))
    for x, z in ((-2, -2), (2, -2)):
        bp.set(x, 0, z, 'minecraft:azalea')
    return bp


# ====================================================================== 岩鯨の丘陵
def titan_ribs(rng):
    bp = BP('poi_titan_ribs')
    ground(bp, 13, 'minecraft:coarse_dirt', rng, 'minecraft:grass_block[snowy=false]', 0.5)
    # 背骨 (x に沿って低く) と、左右に弧を描く肋骨 (骨のブロック)
    for x in range(-12, 13):
        bp.set(x, 0, 0, 'minecraft:bone_block[axis=x]')
        if x % 2 == 0:
            bp.set(x, 1, 0, 'minecraft:bone_block[axis=y]')
    for i, x in enumerate(range(-10, 11, 4)):
        h = 9 - abs(i - 2.5) * 1.5
        for side in (-1, 1):
            for k in range(0, 12):
                t = k / 11.0
                z = side * (1 + math.sin(t * math.pi * 0.85) * 7)
                y = math.sin(t * math.pi) * h
                bp.set(x, int(round(y)), int(round(z)), 'minecraft:bone_block[axis=y]')
            if rng.random() < 0.3:
                # 折れた肋骨: 先を落とす
                for k in range(8, 12):
                    t = k / 11.0
                    z = side * (1 + math.sin(t * math.pi * 0.85) * 7)
                    y = math.sin(t * math.pi) * h
                    bp.set(x, int(round(y)), int(round(z)), AIR)
    # 頭骨のかけら
    bp.fill(11, 0, -2, 13, 2, 2, 'minecraft:bone_block[axis=y]')
    bp.fill(12, 1, -1, 13, 1, 1, AIR)
    for x, z in ((-6, 6), (3, -7), (7, 5)):
        bp.set(x, 0, z, 'minecraft:skeleton_skull[rotation=%d]' % rng.integers(0, 16))
    bp.mark('loot', 0, 0, 3)
    return bp


def monastery(rng):
    bp = BP('poi_monastery')
    ground(bp, 12, 'minecraft:stone_bricks', rng, 'minecraft:mossy_stone_bricks', 0.4)
    walls(bp, -9, -8, 9, 6, 8, 'minecraft:stone_bricks', 'minecraft:chiseled_stone_bricks', broken=0.6, rng=rng, windows=False)
    # 中庭の柱廊と、崩れた鐘楼
    for x in range(-7, 8, 3):
        bp.fill(x, 0, -3, x, 4, -3, 'minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]')
    for x in range(-7, 8):
        if rng.random() < 0.7:
            bp.set(x, 5, -3, slab('minecraft:stone_brick'))
    for y in range(0, 14):
        for x in range(6, 10):
            for z in range(-8, -4):
                if x in (6, 9) or z in (-8, -5):
                    if y < 13 - (2 if x == 9 and z == -5 else 0):
                        bp.set(x, y, z, 'minecraft:stone_bricks' if rng.random() > 0.2 else 'minecraft:cracked_stone_bricks')
    bp.set(7, 11, -6, 'minecraft:bell[attachment=ceiling,facing=north,powered=false]')
    for x in (-1, 0, 1):
        for y in range(0, 3):
            bp.set(x, y, 6, AIR)
    # 祭壇と長椅子
    bp.fill(-1, 0, -7, 1, 0, -7, 'minecraft:polished_andesite')
    bp.set(0, 1, -7, 'minecraft:candle[candles=3,lit=true,waterlogged=false]')
    for z in (-1, 1, 3):
        for x in (-5, -4, -3, 3, 4, 5):
            bp.set(x, 0, z, stairs('minecraft:spruce_stairs', 'south'))
    for k in range(25):
        x, z = int(rng.integers(-11, 12)), int(rng.integers(-11, 9))
        if abs(x) > 9 or z > 6 or z < -8:
            bp.set(x, 0, z, rng.choice(['minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:cracked_stone_bricks']))
    bp.mark('loot', 0, 0, -6)
    return bp


def stone_circle(rng):
    bp = BP('poi_stone_circle')
    ground(bp, 10, 'minecraft:grass_block[snowy=false]', rng, 'minecraft:moss_block', 0.3)
    for i in range(9):
        a = i / 9 * 2 * math.pi
        x, z = int(round(math.cos(a) * 7)), int(round(math.sin(a) * 7))
        h = int(rng.integers(3, 6))
        for y in range(h):
            bp.set(x, y, z, 'minecraft:andesite' if y < h - 1 else 'minecraft:polished_andesite')
            if rng.random() < 0.4:
                bp.set(x + 1 if x < 0 else x - 1, y, z, 'minecraft:mossy_cobblestone') if y == 0 else None
        if i % 3 == 0:
            for y in range(h, h + 1):
                nx, nz = int(round(math.cos(a + 0.35) * 7)), int(round(math.sin(a + 0.35) * 7))
                bp.set(nx, y - 1, nz, 'minecraft:andesite')
                bp.set((x + nx) // 2, h, (z + nz) // 2, 'minecraft:andesite_slab[type=bottom,waterlogged=false]')
    bp.fill(-1, 0, -1, 1, 0, 1, 'minecraft:chiseled_stone_bricks')
    bp.set(0, 1, 0, 'minecraft:lodestone')
    return bp


# ====================================================================== 潮の浜
def lighthouse(rng):
    bp = BP('poi_lighthouse')
    ground(bp, 7, 'minecraft:sand', rng, 'minecraft:gravel', 0.2)
    for y in range(0, 20):
        r = 4 if y < 3 else 3
        for x in range(-r, r + 1):
            for z in range(-r, r + 1):
                d = math.hypot(x, z)
                if r - 0.9 <= d <= r + 0.3:
                    bp.set(x, y, z, 'minecraft:white_concrete' if (y // 4) % 2 == 0 else 'minecraft:red_concrete')
                elif d < r - 0.9:
                    bp.set(x, y, z, AIR)
    # 螺旋のはしご代わりに、内側の段
    for y in range(0, 20):
        a = y * 0.9
        x, z = int(round(math.cos(a) * 2)), int(round(math.sin(a) * 2))
        bp.set(x, y, z, slab('minecraft:spruce', 'top'))
    door(bp, 0, 3, 'south', 'birch')
    # 灯室
    for x in range(-3, 4):
        for z in range(-3, 4):
            if math.hypot(x, z) <= 3.3:
                bp.set(x, 20, z, 'minecraft:smooth_stone')
    for x in range(-2, 3):
        for z in range(-2, 3):
            if math.hypot(x, z) >= 1.9 and math.hypot(x, z) <= 2.6:
                bp.fill(x, 21, z, x, 22, z, 'minecraft:glass')
    bp.set(0, 21, 0, 'minecraft:sea_lantern')
    bp.set(0, 22, 0, 'minecraft:sea_lantern')
    for x in range(-2, 3):
        for z in range(-2, 3):
            if math.hypot(x, z) <= 2.6:
                bp.set(x, 23, z, 'minecraft:red_concrete')
    bp.set(0, 24, 0, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')
    for x, z in ((5, 2), (5, 3), (-5, -2)):
        bp.set(x, 0, z, 'minecraft:barrel[facing=up,open=false]')
    return bp


def shipwreck(rng):
    bp = BP('poi_beached_wreck')
    ground(bp, 12, 'minecraft:sand', rng, 'minecraft:gravel', 0.25)
    # 傾いた船体 (x に沿う、少し埋まる)
    for x in range(-10, 11):
        w = int(round(4 * math.cos(x / 11 * math.pi / 2)))
        for z in range(-w, w + 1):
            bp.set(x, -1, z, 'minecraft:spruce_planks')
            if abs(z) == w:
                for y in range(0, 3 if x < 6 else 2):
                    if rng.random() > 0.15:
                        bp.set(x, y, z, 'minecraft:dark_oak_planks' if y < 2 else 'minecraft:spruce_slab[type=bottom,waterlogged=false]')
            else:
                bp.set(x, 0, z, AIR)
    # 折れた帆柱と帆の切れ端
    bp.fill(-2, 0, 0, -2, 7, 0, 'minecraft:stripped_spruce_log[axis=y]')
    for x in range(-2, 5):
        bp.set(x, 8 - (x + 2) // 2, 0, 'minecraft:spruce_log[axis=x]') if x > -2 else None
    for z in range(-2, 3):
        for y in range(3, 7):
            if rng.random() < 0.6:
                bp.set(-1, y, z, 'minecraft:white_wool')
    for x, z in ((3, 1), (4, -1), (-6, 1)):
        bp.set(x, 0, z, rng.choice(['minecraft:barrel[facing=up,open=false]', 'minecraft:chest[facing=north,type=single,waterlogged=false]']))
    bp.mark('loot', 4, 0, -1)
    return bp


def fishing_huts(rng):
    bp = BP('poi_fishing_huts')
    ground(bp, 10, 'minecraft:sand', rng, 'minecraft:coarse_dirt', 0.25)
    for (ox, oz) in ((-6, -3), (5, -2)):
        walls(bp, ox - 2, oz - 2, ox + 2, oz + 2, 3, 'minecraft:birch_planks', 'minecraft:stripped_birch_log[axis=y]')
        door(bp, ox, oz + 2, 'south', 'birch')
        gable(bp, ox - 2, oz - 2, ox + 2, oz + 2, 3, 'minecraft:dark_prismarine_stairs', slab('minecraft:dark_prismarine'))
        bp.set(ox, 2, oz, lantern(True))
    # 干し網 (柵とクモの巣) と干し魚
    for x in range(-3, 3):
        bp.set(x, 0, 6, 'minecraft:birch_fence')
        bp.set(x, 1, 6, 'minecraft:birch_fence')
        if x % 2 == 0:
            bp.set(x, 2, 6, 'minecraft:cobweb')
    for x, z in ((-1, 3), (1, 3), (8, 4)):
        bp.set(x, 0, z, 'minecraft:barrel[facing=up,open=false]')
    bp.set(0, 0, 8, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    return bp


# ====================================================================== 嵐の高地
def wind_gate(rng):
    bp = BP('poi_wind_gate')
    ground(bp, 9, 'minecraft:gravel', rng, 'minecraft:andesite', 0.4)
    # 崩れた二本の門柱と梁、風見の羽 (銅)
    for x in (-4, 4):
        for y in range(0, 11 if x < 0 else 7):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if abs(dx) + abs(dz) < 2:
                        bp.set(x + dx, y, dz, 'minecraft:deepslate_bricks' if rng.random() > 0.2 else 'minecraft:cracked_deepslate_bricks')
    for x in range(-4, 2):
        bp.set(x, 11, 0, 'minecraft:deepslate_brick_slab[type=bottom,waterlogged=false]')
    for k, (x, y, z) in enumerate(((4, 0, 3), (5, 0, 4), (3, 0, 5), (6, 1, 4))):
        bp.set(x, y, z, 'minecraft:cracked_deepslate_bricks')
    bp.set(-4, 11, 0, 'minecraft:oxidized_copper')
    bp.set(-4, 12, 0, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')
    for x, z in ((-7, 4), (7, -5), (-2, -7)):
        bp.set(x, 0, z, 'minecraft:weathered_cut_copper_slab[type=bottom,waterlogged=false]')
    bp.mark('loot', 0, 0, 2)
    return bp


def rod_tower(rng):
    bp = BP('poi_rod_tower')
    ground(bp, 6, 'minecraft:andesite', rng, 'minecraft:gravel', 0.3)
    for y in range(0, 16):
        for x in range(-2, 3):
            for z in range(-2, 3):
                if max(abs(x), abs(z)) == 2:
                    bp.set(x, y, z, 'minecraft:polished_deepslate' if (x + z + y) % 5 else 'minecraft:oxidized_cut_copper')
                else:
                    bp.set(x, y, z, AIR)
    for y in range(0, 16):
        bp.set(0, y, -1, 'minecraft:ladder[facing=south,waterlogged=false]')
    door(bp, 0, 2, 'south', 'dark_oak')
    bp.fill(-2, 16, -2, 2, 16, 2, 'minecraft:oxidized_cut_copper')
    for y in range(17, 22):
        bp.set(0, y, 0, 'minecraft:oxidized_copper' if y < 21 else 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')
    for x, z in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        bp.set(x, 17, z, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')
    return bp


def mountain_hut(rng):
    bp = BP('poi_mountain_hut')
    ground(bp, 7, 'minecraft:gravel', rng, 'minecraft:snow_block', 0.3)
    walls(bp, -3, -3, 3, 3, 3, 'minecraft:spruce_planks', 'minecraft:spruce_log[axis=y]')
    door(bp, 0, 3, 'south', 'spruce')
    gable(bp, -3, -3, 3, 3, 3, 'minecraft:deepslate_tile_stairs', 'minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]')
    bp.set(-2, 0, -2, 'minecraft:red_bed[facing=south,occupied=false,part=head]')
    bp.set(-2, 0, -1, 'minecraft:red_bed[facing=south,occupied=false,part=foot]')
    bp.set(2, 0, -2, 'minecraft:furnace[facing=south,lit=false]')
    bp.fill(1, 3, -2, 1, 7, -2, 'minecraft:cobblestone')
    bp.set(0, 2, 0, lantern(True))
    for x, z in ((5, 1), (5, 2), (5, 3)):
        bp.set(x, 0, z, 'minecraft:spruce_log[axis=z]')
    return bp


# ====================================================================== 灰の荒野
def burnt_fort(rng):
    bp = BP('poi_burnt_fort')
    ground(bp, 12, 'minecraft:coarse_dirt', rng, 'minecraft:blackstone', 0.35)
    walls(bp, -10, -10, 10, 10, 6, 'minecraft:polished_blackstone_bricks', 'minecraft:blackstone', broken=0.75, rng=rng, windows=False)
    for x, z in ((-10, -10), (10, -10), (-10, 10), (10, 10)):
        for y in range(0, 9 - int(rng.integers(0, 5))):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    bp.set(x + dx, y, z + dz, 'minecraft:polished_blackstone_bricks' if rng.random() > 0.25 else 'minecraft:cracked_polished_blackstone_bricks')
    for x in (-1, 0, 1):
        for y in range(0, 4):
            bp.set(x, y, 10, AIR)
    # 焦げた梁と灰
    for k in range(10):
        x, z = int(rng.integers(-8, 9)), int(rng.integers(-8, 9))
        bp.set(x, 0, z, 'minecraft:stripped_dark_oak_log[axis=%s]' % rng.choice(['x', 'z']))
    for k in range(30):
        x, z = int(rng.integers(-9, 10)), int(rng.integers(-9, 10))
        bp.set(x, 0, z, 'minecraft:gray_concrete_powder' if rng.random() < 0.5 else 'minecraft:light_gray_concrete_powder')
    bp.set(0, 0, 0, 'minecraft:campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]')
    bp.mark('loot', 2, 0, -6)
    return bp


def forge_ruin(rng):
    bp = BP('poi_forge_ruin')
    ground(bp, 8, 'minecraft:blackstone', rng, 'minecraft:basalt[axis=y]', 0.3)
    bp.fill(-2, 0, -2, 2, 2, 2, 'minecraft:bricks')
    bp.fill(-1, 1, -1, 1, 2, 1, AIR)
    bp.set(0, 0, 0, 'minecraft:magma_block')
    bp.fill(0, 3, 0, 0, 8, 0, 'minecraft:bricks')
    for x, z in ((4, 0), (-4, 1)):
        bp.set(x, 0, z, 'minecraft:anvil[facing=north]' if x > 0 else 'minecraft:chipped_anvil[facing=east]')
    for x, z in ((3, 3), (-3, -3), (4, -2)):
        bp.set(x, 0, z, 'minecraft:blast_furnace[facing=south,lit=false]' if x > 0 else 'minecraft:cauldron')
    for x in range(-5, 6):
        if rng.random() < 0.5:
            bp.set(x, 0, 5, 'minecraft:polished_blackstone_brick_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=low]')
    bp.mark('loot', 0, 0, -4)
    return bp


def basalt_gate(rng):
    bp = BP('poi_basalt_gate')
    ground(bp, 9, 'minecraft:basalt[axis=y]', rng, 'minecraft:blackstone', 0.4)
    for x in (-4, 4):
        for y in range(0, 12):
            bp.fill(x - 1, y, -1, x + 1, y, 1, 'minecraft:basalt[axis=y]' if y < 11 else 'minecraft:polished_basalt[axis=y]')
    for x in range(-5, 6):
        bp.set(x, 12, 0, 'minecraft:polished_blackstone_bricks')
        bp.set(x, 13, 0, 'minecraft:polished_blackstone_brick_slab[type=bottom,waterlogged=false]')
    bp.set(0, 11, 0, 'minecraft:chain[axis=y,waterlogged=false]')
    bp.set(0, 10, 0, lantern(True))
    for x, z in ((-7, 3), (6, -4), (2, 6)):
        bp.fill(x, 0, z, x, int(rng.integers(1, 4)), z, 'minecraft:basalt[axis=y]')
    return bp


# ====================================================================== どこにでも
def bandit_camp(rng):
    bp = BP('poi_bandit_camp')
    ground(bp, 9, 'minecraft:coarse_dirt', rng, 'minecraft:podzol', 0.3)
    # 天幕 (羊毛の三角) 二つ
    for (ox, oz, wool) in ((-4, -2, 'minecraft:brown_wool'), (4, -1, 'minecraft:red_wool')):
        for k in range(0, 3):
            for z in range(oz - 2, oz + 3):
                bp.set(ox - 2 + k, k, z, wool)
                bp.set(ox + 2 - k, k, z, wool)
        for z in range(oz - 2, oz + 3):
            bp.set(ox, 2, z, wool)
        bp.set(ox, 0, oz, 'minecraft:barrel[facing=up,open=false]')
    bp.set(0, 0, 3, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    for x, z in ((-2, 3), (2, 3)):
        bp.set(x, 0, z, 'minecraft:spruce_log[axis=z]')
    # 杭の柵
    for i in range(16):
        a = i / 16 * 2 * math.pi
        if i % 4 != 0:
            x, z = int(round(math.cos(a) * 8)), int(round(math.sin(a) * 8))
            bp.fill(x, 0, z, x, 1, z, 'minecraft:spruce_fence')
    bp.set(-6, 0, 4, 'minecraft:target')
    bp.mark('loot', 4, 0, -1)
    bp.mark('foes', 0, 0, 0)
    return bp


def ruined_house(rng):
    bp = BP('poi_ruined_house')
    ground(bp, 7, 'minecraft:grass_block[snowy=false]', rng, 'minecraft:coarse_dirt', 0.3)
    walls(bp, -4, -3, 4, 4, 4, 'minecraft:cobblestone', 'minecraft:oak_log[axis=y]', broken=0.7, rng=rng, windows=False)
    for x in range(-3, 4):
        for z in range(-2, 4):
            bp.set(x, -1, z, 'minecraft:oak_planks' if rng.random() > 0.3 else 'minecraft:coarse_dirt')
    for x in (-1, 0):
        for y in range(0, 2):
            bp.set(x, y, 4, AIR)
    for k in range(6):
        x, z = int(rng.integers(-3, 4)), int(rng.integers(-2, 4))
        bp.set(x, 0, z, rng.choice(['minecraft:oak_slab[type=bottom,waterlogged=false]', 'minecraft:cobblestone', 'minecraft:moss_carpet']))
    bp.set(2, 0, -2, 'minecraft:chest[facing=south,type=single,waterlogged=false]')
    bp.mark('loot', 2, 0, -2)
    return bp


BUILDERS = {
    'windmill': windmill, 'farm': farm, 'watchtower': watchtower, 'roadside_shrine': roadside_shrine,
    'titan_ribs': titan_ribs, 'monastery': monastery, 'stone_circle': stone_circle,
    'lighthouse': lighthouse, 'beached_wreck': shipwreck, 'fishing_huts': fishing_huts,
    'wind_gate': wind_gate, 'rod_tower': rod_tower, 'mountain_hut': mountain_hut,
    'burnt_fort': burnt_fort, 'forge_ruin': forge_ruin, 'basalt_gate': basalt_gate,
    'bandit_camp': bandit_camp, 'ruined_house': ruined_house,
}
# 土地ごとの見どころの並び (この順に一つずつ。足りなければ繰り返す)
BY_REGION = {
    'meadow': ['windmill', 'farm', 'watchtower', 'roadside_shrine', 'bandit_camp', 'farm', 'ruined_house', 'windmill'],
    'whale': ['titan_ribs', 'monastery', 'stone_circle', 'bandit_camp', 'titan_ribs', 'ruined_house', 'watchtower'],
    'tide': ['lighthouse', 'beached_wreck', 'fishing_huts', 'bandit_camp', 'beached_wreck', 'fishing_huts'],
    'storm': ['wind_gate', 'rod_tower', 'mountain_hut', 'wind_gate', 'bandit_camp', 'rod_tower'],
    'ember': ['burnt_fort', 'forge_ruin', 'basalt_gate', 'bandit_camp', 'forge_ruin', 'burnt_fort'],
}
# 浜に近い所を好む見どころ
COASTAL = {'lighthouse', 'beached_wreck', 'fishing_huts'}


def place(height, region, lay, seed=11):
    """見どころの置き場を決める。戻り値: [(種類, x, z)]"""
    from continent_layout import HALF
    from scipy import ndimage
    rng = np.random.default_rng(seed)
    n = height.shape[0]
    scale = 2 * HALF / n
    land = height >= 64
    to_sea = ndimage.distance_transform_edt(land) * scale
    gy, gx = np.gradient(height.astype(float))
    slope = np.hypot(gx, gy)
    region_ids = ['meadow', 'start', 'whale', 'tide', 'storm', 'ember']
    avoid = [(lay['capital'], 260), (lay['start'], 600)] + [(p, 120) for _, p in lay['towns']] + [(p, 160) for _, p in lay['sites']]
    roads = []
    for r in lay['roads']:
        for a, b in zip(r, r[1:]):
            roads.append((a, b))
    out = []

    def near_road(x, z, dmin):
        for (ax, az), (bx, bz) in roads:
            dx, dz = bx - ax, bz - az
            L = dx * dx + dz * dz
            t = 0 if L == 0 else max(0, min(1, ((x - ax) * dx + (z - az) * dz) / L))
            if math.hypot(x - ax - t * dx, z - az - t * dz) < dmin:
                return True
        return False

    for rname, kinds in BY_REGION.items():
        rid = region_ids.index(rname)
        cand = np.argwhere((region == rid) & land & (slope < 1.6) & (height < 175))
        rng.shuffle(cand)
        placed = 0
        for j, i in cand:
            if placed >= len(kinds):
                break
            kind = kinds[placed]
            x = -HALF + i * scale
            z = -HALF + j * scale
            if kind in COASTAL and not (12 < to_sea[j, i] < 60):
                continue
            if kind not in COASTAL and to_sea[j, i] < 40:
                continue
            if any(math.hypot(x - p[0], z - p[1]) < r for p, r in avoid):
                continue
            if any(math.hypot(x - ox, z - oz) < 230 for _, ox, oz in out):
                continue
            if near_road(x, z, 28):
                continue
            # 道から遠すぎない (見つけやすい): 400 以内
            if not near_road(x, z, 400):
                continue
            out.append((kind, int(round(x)), int(round(z))))
            placed += 1
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    rng = np.random.default_rng(5)
    for kind, fn in BUILDERS.items():
        bp = fn(np.random.default_rng(abs(hash(kind)) % (2 ** 31) if False else len(kind) * 7919))
        bp.name = 'poi_' + kind
        r = max(max(abs(x), abs(z)) for (x, _, z) in bp.cells) + 1
        bp.meta_rect = r
        print(f'poi_{kind}: {bp.save()} blocks (rect {r})')
    # 置き場
    sys.path.insert(0, HERE)
    import gen_continent as gc
    lay, height, biome, reg = gc.build()
    pois = place(height, reg, lay)
    with open(os.path.join(CONT, 'pois.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump([{'kind': k, 'x': x, 'z': z} for k, x, z in pois], f, ensure_ascii=False, separators=(',', ':'))
        f.write('\n')
    print(len(pois), 'pois placed')


if __name__ == '__main__':
    main()
