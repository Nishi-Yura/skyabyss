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
    """畑と納屋: 木組みの二階の納屋 (都のまわりの作り)・二枚の畑・かかし・干し草・水桶・牧場の柵"""
    import town_arch as ta
    st = ta.STYLES['meadow']
    bp = BP('poi_farm')
    ground(bp, 13, 'minecraft:grass_block[snowy=false]', rng, 'minecraft:coarse_dirt', 0.15)
    ta.farm(bp, st, -12, -11, -2, -1, rng)
    ta.farm(bp, st, 2, -11, 12, -1, rng)
    # かかし
    bp.fill(-7, 0, -6, -7, 1, -6, 'minecraft:oak_fence')
    bp.set(-7, 2, -6, 'minecraft:carved_pumpkin[facing=south]')
    bp.set(-8, 1, -6, 'minecraft:oak_fence')
    bp.set(-6, 1, -6, 'minecraft:oak_fence')
    # 納屋 (南。二階、大きな戸口)
    ta.building(bp, st, -5, 3, 5, 10, 2, (0, 3, 'north'), rng, chimney=False, kind='hall')
    for x in (-1, 1):
        for y in range(0, 3):
            bp.set(x, y, 3, AIR)
    for x, z in ((-4, 8), (-4, 9), (4, 8), (4, 9), (3, 9)):
        bp.set(x, 0, z, 'minecraft:hay_block[axis=y]')
    bp.set(-4, 1, 9, 'minecraft:hay_block[axis=y]')
    # 牧場 (東。柵と水桶)
    for x in range(7, 13):
        for z in range(2, 9):
            if x in (7, 12) or z in (2, 8):
                bp.set(x, 0, z, 'minecraft:oak_fence')
    bp.set(7, 0, 5, 'minecraft:oak_fence_gate[facing=east,in_wall=false,open=false,powered=false]')
    bp.set(10, 0, 5, 'minecraft:water_cauldron[level=3]')
    bp.set(-9, 0, 4, 'minecraft:composter[level=4]')
    ta.lamp(bp, st, 0, 0)
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
    """道ばたの祠: 苔むした石の台に小さな屋根の祠・供え物・二つの灯籠・腰かけ"""
    bp = BP('poi_roadside_shrine')
    ground(bp, 5, 'minecraft:moss_block', rng, 'minecraft:grass_block[snowy=false]', 0.4)
    bp.fill(-2, -1, -2, 2, -1, 2, 'minecraft:mossy_stone_bricks')
    bp.fill(-1, 0, -2, 1, 0, -1, 'minecraft:mossy_stone_bricks')
    bp.set(0, 1, -2, 'minecraft:chiseled_stone_bricks')
    bp.set(0, 2, -2, 'minecraft:candle[candles=3,lit=true,waterlogged=false]')
    for x in (-1, 1):
        bp.set(x, 1, -1, 'minecraft:flower_pot' if x < 0 else 'minecraft:potted_red_tulip')
        bp.fill(x * 2, 0, -2, x * 2, 2, -2, 'minecraft:spruce_log[axis=y]')
    for x in range(-3, 4):
        for z in range(-3, 0):
            bp.set(x, 3, z, 'minecraft:spruce_slab[type=bottom,waterlogged=false]' if abs(x) == 3 or z == -3 else 'minecraft:spruce_planks')
    bp.set(0, 4, -2, 'minecraft:spruce_slab[type=bottom,waterlogged=false]')
    # 灯籠 (石の柱と灯り)
    for x in (-3, 3):
        bp.set(x, 0, 2, 'minecraft:stone_bricks')
        bp.set(x, 1, 2, 'minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]')
        bp.set(x, 2, 2, 'minecraft:lantern[hanging=false,waterlogged=false]')
    bp.set(0, 0, 3, stairs('minecraft:mossy_stone_brick_stairs', 'north'))
    for x in (-1, 1):
        bp.set(x, 0, 4, stairs('minecraft:spruce_stairs', 'south'))
    for x, z in ((-4, -3), (4, -3)):
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
    """漁師の小屋: 汐守の作りの小さな家二つ・網を干す棚・陸に上げた小舟・焚き火と樽"""
    import town_arch as ta
    st = ta.STYLES['tide']
    bp = BP('poi_fishing_huts')
    ground(bp, 11, 'minecraft:sand', rng, 'minecraft:coarse_dirt', 0.25)
    ta.building(bp, st, -9, -6, -3, 0, 1, (-6, 0, 'south'), rng, chimney=True)
    ta.building(bp, st, 3, -7, 9, -1, 2, (6, -1, 'south'), rng, chimney=False)
    # 網を干す棚
    for zz in (4, 7):
        for x in (-6, 0):
            bp.fill(x, 0, zz, x, 2, zz, 'minecraft:birch_fence')
        for x in range(-6, 1):
            bp.set(x, 3, zz, 'minecraft:birch_slab[type=bottom,waterlogged=false]')
            if -6 < x < 0:
                bp.set(x, 2, zz, 'minecraft:chain[axis=y,waterlogged=false]')
    # 小舟 (伏せて置いた舟の形)
    for x in range(4, 10):
        bp.set(x, 0, 5, 'minecraft:spruce_slab[type=top,waterlogged=false]')
        if 4 < x < 9:
            bp.set(x, 0, 4, 'minecraft:spruce_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
            bp.set(x, 0, 6, 'minecraft:spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
    for x, z in ((2, 1), (3, 1), (-1, 1)):
        bp.set(x, 0, z, 'minecraft:barrel[facing=up,open=false]')
    bp.set(0, 0, 9, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    for x, z in ((-1, 9), (1, 9)):
        bp.set(x, 0, z, 'minecraft:spruce_log[axis=z]')
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
    """山小屋: 風待の作りの二階の小屋・薪の山・囲った物置・石の暖炉の煙突・道しるべの杭"""
    import town_arch as ta
    st = ta.STYLES['storm']
    bp = BP('poi_mountain_hut')
    ground(bp, 9, 'minecraft:gravel', rng, 'minecraft:snow_block', 0.3)
    ta.building(bp, st, -4, -4, 4, 3, 2, (0, 3, 'south'), rng, chimney=True)
    for x in range(5, 9):
        bp.set(x, 0, 1, 'minecraft:spruce_log[axis=x]')
        bp.set(x, 0, 2, 'minecraft:spruce_log[axis=x]')
        if x < 8:
            bp.set(x, 1, 1, 'minecraft:spruce_log[axis=x]')
    for x, z in ((5, 0), (8, 0), (5, 3), (8, 3)):
        bp.fill(x, 0, z, x, 2, z, st['fence'])
    for x in range(5, 9):
        for z in range(0, 4):
            bp.set(x, 3, z, 'minecraft:dark_oak_slab[type=bottom,waterlogged=false]')
    bp.set(-6, 0, 2, 'minecraft:barrel[facing=up,open=false]')
    bp.set(-6, 0, 3, 'minecraft:barrel[facing=up,open=false]')
    ta.lamp(bp, st, 2, 6)
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
    """炉の跡: 崩れた黒い石の鍛冶場 (壁は上ほど欠ける)・冷えた大きな炉と煙突・金床・溶けかけた鉄の塊・崩れた石"""
    bp = BP('poi_forge_ruin')
    ground(bp, 10, 'minecraft:blackstone', rng, 'minecraft:basalt[axis=y]', 0.3)
    walls(bp, -6, -6, 6, 4, 5, 'minecraft:polished_blackstone_bricks', 'minecraft:polished_blackstone', broken=0.75, rng=rng, windows=False)
    for x in range(-5, 6):
        for z in range(-5, 4):
            bp.set(x, -1, z, 'minecraft:polished_blackstone_bricks' if rng.random() > 0.3 else 'minecraft:cracked_polished_blackstone_bricks')
    for x in (-1, 0, 1):
        for y in range(0, 3):
            bp.set(x, y, 4, AIR)
    # 大きな炉 (奥。煉瓦の箱と、高い煙突)
    bp.fill(-2, 0, -5, 2, 2, -3, 'minecraft:bricks')
    bp.fill(-1, 0, -4, 1, 1, -4, AIR)
    bp.set(0, 0, -4, 'minecraft:magma_block')
    bp.fill(0, 3, -4, 0, 11, -4, 'minecraft:bricks')
    bp.set(0, 12, -4, 'minecraft:cracked_stone_bricks')
    for x, z in ((-4, -1), (4, -1)):
        bp.set(x, 0, z, 'minecraft:anvil[facing=north]' if x > 0 else 'minecraft:chipped_anvil[facing=east]')
    bp.set(-4, 0, -4, 'minecraft:blast_furnace[facing=south,lit=false]')
    bp.set(4, 0, -4, 'minecraft:cauldron')
    for x, z in ((2, 1), (-2, 2)):
        bp.set(x, 0, z, 'minecraft:raw_iron_block')
    # 崩れた石 (まわり)
    for k in range(16):
        a = rng.random() * 2 * math.pi
        r = 7 + rng.random() * 3
        x, z = int(round(math.cos(a) * r)), int(round(math.sin(a) * r))
        bp.set(x, 0, z, rng.choice(['minecraft:blackstone', 'minecraft:polished_blackstone_brick_slab[type=bottom,waterlogged=false]', 'minecraft:basalt[axis=y]']))
    bp.mark('loot', 0, 0, -2)
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
        kinds = kinds + kinds  # 二巡 (2026-10-25: まだ寂しい。間は 170 まで詰める)
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
            if any(math.hypot(x - ox, z - oz) < 140 for _, ox, oz in out):
                continue
            if near_road(x, z, 28):
                continue
            # 道から遠すぎない (見つけやすい): 400 以内
            if not near_road(x, z, 400):
                continue
            out.append((kind, int(round(x)), int(round(z))))
            placed += 1
    return out


def signpost(rng):
    bp = BP('poi_signpost')
    bp.fill(0, 0, 0, 0, 2, 0, 'minecraft:spruce_fence')
    bp.set(0, 3, 0, 'minecraft:spruce_fence')
    bp.set(0, 0, 1, 'minecraft:oak_sign[rotation=0,waterlogged=false]')
    bp.set(0, 4, 0, 'minecraft:lantern[hanging=false,waterlogged=false]')
    bp.mark('sign', 0, 0, 1)
    return bp


BUILDERS['signpost'] = signpost


def hamlet(region):
    """小さな集落: 木組みの家三つ (町と同じ作り。土地の色)・井戸・畑・薪の山・街灯一つ。町より小さく、人はいない (のちに頼みの人を置く)"""
    import town_arch as ta
    s = ta.STYLES[region]

    def build(rng):
        bp = BP('poi_hamlet_' + region)
        ground(bp, 13, 'minecraft:grass_block[snowy=false]' if region in ('meadow', 'whale') else 'minecraft:coarse_dirt', rng, s['road2'], 0.3)
        for i in range(-12, 13):
            for w in (-1, 0, 1):
                bp.set(w, -1, i, s['road'] if w == 0 or rng.random() < 0.6 else s['road2'])
        ta.building(bp, s, -10, -10, -3, -4, 2, (-6, -4, 'south'), rng, chimney=True)
        ta.building(bp, s, 3, -11, 10, -4, 2, (6, -4, 'south'), rng, chimney=True)
        ta.building(bp, s, 3, 4, 9, 10, 1, (6, 4, 'north'), rng, chimney=False)
        # 井戸 (屋根付き)
        bp.fill(-4, 0, 2, -2, 0, 4, s['base2'])
        bp.set(-3, 0, 3, 'minecraft:water[level=0]')
        bp.set(-3, -1, 3, s['base2'])
        for x, z in ((-4, 2), (-2, 2), (-4, 4), (-2, 4)):
            bp.fill(x, 1, z, x, 2, z, s['fence'])
        bp.fill(-4, 3, 2, -2, 3, 4, ta.slab(s['roofslab']))
        ta.farm(bp, s, -12, 6, -6, 11, rng)
        for x in range(4, 8):
            bp.set(x, 0, -2, 'minecraft:spruce_log[axis=x]')
        bp.set(5, 1, -2, 'minecraft:spruce_log[axis=x]')
        ta.tree(bp, region, 12, 2)
        ta.lamp(bp, s, 2, -1)
        bp.mark('loot', 7, 0, -8)
        return bp
    return build


def barrow(rng):
    """古い塚 (小さな迷宮): 土と石の小山に南から入る通路、奥の石室に宝箱。近づくと骨の兵 (foes)"""
    bp = BP('poi_barrow')
    R = 9
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            d = math.hypot(x, z)
            if d > R:
                continue
            h = int(round((1 - (d / R) ** 2) * 8))
            for y in range(0, h):
                outer = y == h - 1 or d > R - 1.5
                bp.set(x, y, z, ('minecraft:grass_block[snowy=false]' if y == h - 1 else 'minecraft:dirt') if outer else
                       rng.choice(['minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:stone']))
    # 通路 (南から北へ、幅 3・高さ 3) と石室 (7 × 7、高さ 4)
    for z in range(-1, R + 1):
        for x in (-1, 0, 1):
            for y in range(0, 3):
                bp.set(x, y, z, AIR)
        for x in (-2, 2):
            for y in range(0, 3):
                if bp.get(x, y, z):
                    bp.set(x, y, z, 'minecraft:stone_bricks' if (z + y) % 3 else 'minecraft:mossy_stone_bricks')
        bp.set(0, -1, z, 'minecraft:stone_bricks')
    for x in range(-3, 4):
        for z in range(-5, 0):
            for y in range(0, 4):
                bp.set(x, y, z, AIR)
            bp.set(x, -1, z, 'minecraft:polished_andesite' if (x + z) % 2 else 'minecraft:andesite')
    for x, z in ((-3, -5), (3, -5), (-3, -1), (3, -1)):
        bp.fill(x, 0, z, x, 3, z, 'minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]')
    bp.set(0, 0, -5, 'minecraft:chest[facing=south,type=single,waterlogged=false]')
    for x, z in ((-2, -4), (2, -4)):
        bp.set(x, 3, z, 'minecraft:lantern[hanging=true,waterlogged=false]')
    bp.set(0, 3, -3, 'minecraft:chain[axis=y,waterlogged=false]')
    for x, z in ((-2, -2), (2, -2), (-1, -4)):
        bp.set(x, 0, z, 'minecraft:skeleton_skull[rotation=%d]' % int(rng.integers(0, 16)))
    # 入口の門石
    for x in (-2, 2):
        bp.fill(x, 0, R, x, 3, R, 'minecraft:chiseled_stone_bricks')
    for x in range(-2, 3):
        bp.set(x, 3, R, 'minecraft:stone_brick_slab[type=bottom,waterlogged=false]')
    # まわりの立石 (遠くから塚とわかるように) と、入口へ続く踏み跡
    for k in range(7):
        a = (k + 0.5) / 7 * 2 * math.pi
        x, z = int(round(math.sin(a) * 12)), int(round(-math.cos(a) * 12))
        h = 2 + int(rng.integers(0, 2))
        bp.fill(x, 0, z, x, h - 1, z, 'minecraft:mossy_cobblestone' if k % 2 else 'minecraft:cobblestone')
        bp.set(x, h, z, 'minecraft:mossy_cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]' if k % 3 == 0 else AIR)
    for z in range(R + 1, R + 5):
        for x in (-1, 0, 1):
            bp.set(x, -1, z, 'minecraft:coarse_dirt' if rng.random() > 0.4 else 'minecraft:gravel')
    bp.mark('loot', 0, 0, -5)
    bp.mark('foes', 0, 0, -2)
    return bp


BUILDERS['barrow'] = barrow
BY_REGION['meadow'] = BY_REGION['meadow'] + ['barrow']
BY_REGION['whale'] = BY_REGION['whale'] + ['barrow']
BY_REGION['storm'] = BY_REGION['storm'] + ['barrow']

for _r in ('meadow', 'whale', 'tide', 'storm', 'ember'):
    BUILDERS['hamlet_' + _r] = hamlet(_r)
for _r, _k in (('meadow', 'hamlet_meadow'), ('whale', 'hamlet_whale'), ('tide', 'hamlet_tide'), ('storm', 'hamlet_storm'), ('ember', 'hamlet_ember')):
    BY_REGION[_r] = BY_REGION[_r] + [_k]


def mine(rng):
    """古い坑道: 木枠の入口から階段で下り、線路の残る掘り場に宝箱。中に骨の兵 (foes は地下)"""
    bp = BP('poi_mine')
    ground(bp, 6, 'minecraft:coarse_dirt', rng, 'minecraft:gravel', 0.35)
    # 入口の小屋 (北を背に、南が口)
    for x in (-2, 2):
        bp.fill(x, 0, 2, x, 3, 2, 'minecraft:spruce_log[axis=y]')
        bp.fill(x, 0, -2, x, 3, -2, 'minecraft:spruce_log[axis=y]')
    for x in range(-3, 4):
        for z in range(-3, 4):
            bp.set(x, 4, z, slab('minecraft:spruce'))
    for x in range(-2, 3):
        bp.set(x, 3, 2, 'minecraft:spruce_planks')
    bp.set(1, 3, 3, 'minecraft:lantern[hanging=true,waterlogged=false]')
    # 階段 (北へ下る。幅 3。地面より上には壁も天井も出さない)
    depth = 8
    for k in range(depth):
        z = 1 - k
        y = -1 - k
        for x in (-1, 0, 1):
            for yy in range(y, y + 4):
                bp.set(x, yy, z, AIR)
            bp.set(x, y - 1, z, 'minecraft:cobblestone')
            bp.set(x, y, z, 'minecraft:cobblestone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
        for x in (-2, 2):
            for yy in range(y, min(y + 4, 0)):
                bp.set(x, yy, z, 'minecraft:stone' if rng.random() > 0.2 else 'minecraft:andesite')
        if y + 3 < 0:
            for x in (-1, 0, 1):
                bp.set(x, y + 3, z, 'minecraft:spruce_planks' if k % 3 == 0 else 'minecraft:stone')
    # 掘り場 (9 × 9、高さ 4)
    fy = -depth - 1
    for x in range(-5, 6):
        for z in range(-depth - 10, -depth + 2):
            edge = abs(x) == 5 or z in (-depth - 10, -depth + 1)
            for y in range(fy - 1, fy + 5):
                if y == fy - 1 or y == fy + 4 or edge:
                    if not (edge and z == -depth + 1 and abs(x) <= 1 and fy <= y < fy + 4):
                        bp.set(x, y, z, rng.choice(['minecraft:stone', 'minecraft:stone', 'minecraft:andesite', 'minecraft:tuff']))
                else:
                    bp.set(x, y, z, AIR)
    for z in range(-depth - 9, -depth):
        bp.set(-3, fy, z, 'minecraft:rail[shape=north_south,waterlogged=false]' if rng.random() > 0.2 else AIR)
    for x, z in ((-4, -depth - 1), (4, -depth - 1), (-4, -depth - 9), (4, -depth - 9)):
        bp.fill(x, fy, z, x, fy + 3, z, 'minecraft:spruce_log[axis=y]')
    for x in range(-4, 5):
        bp.set(x, fy + 3, -depth - 5, 'minecraft:spruce_planks')
    bp.set(2, fy + 3, -depth - 3, 'minecraft:chain[axis=y,waterlogged=false]')
    bp.set(2, fy + 2, -depth - 3, 'minecraft:lantern[hanging=true,waterlogged=false]')
    bp.set(-2, fy + 3, -depth - 7, 'minecraft:chain[axis=y,waterlogged=false]')
    bp.set(-2, fy + 2, -depth - 7, 'minecraft:lantern[hanging=true,waterlogged=false]')
    for (x, z, ore) in ((5, -depth - 3, 'minecraft:iron_ore'), (-5, -depth - 6, 'minecraft:coal_ore'), (4, -depth - 10, 'minecraft:copper_ore')):
        bp.set(x, fy + 1, z, ore)
    for x, z in ((3, -depth - 8), (1, -depth - 2)):
        bp.set(x, fy, z, 'minecraft:barrel[facing=up,open=false]')
    bp.set(-4, fy, -depth - 5, 'minecraft:cobweb')
    bp.set(4, fy + 2, -depth - 6, 'minecraft:cobweb')
    bp.set(0, fy, -depth - 9, 'minecraft:chest[facing=south,type=single,waterlogged=false]')
    bp.mark('loot', 0, fy, -depth - 9)
    bp.mark('foes', 0, fy, -depth - 5)
    return bp


def ruined_tower(rng):
    """崩れた見張り塔 (丸い石の塔。上が欠け、内のはしごで上の床に宝箱)"""
    bp = BP('poi_ruined_tower')
    ground(bp, 7, 'minecraft:grass_block[snowy=false]', rng, 'minecraft:mossy_cobblestone', 0.2)
    R = 4
    H = 18
    for y in range(0, H):
        for x in range(-R, R + 1):
            for z in range(-R, R + 1):
                d = math.hypot(x, z)
                if d > R + 0.4:
                    continue
                wall = d > R - 0.8
                if not wall:
                    bp.set(x, y, z, AIR)
                    continue
                # 上ほど欠ける (東の側が大きく崩れる)
                if y > 9 and rng.random() < (y - 9) / (H - 9) * (0.9 if x > 0 else 0.35):
                    continue
                bp.set(x, y, z, rng.choice(['minecraft:stone_bricks', 'minecraft:stone_bricks', 'minecraft:mossy_stone_bricks', 'minecraft:cracked_stone_bricks']))
    for y in range(0, 12):
        bp.set(0, y, -R + 1, 'minecraft:ladder[facing=south,waterlogged=false]')
    # 入口 (南) と窓
    for y in (0, 1):
        bp.set(0, y, R, AIR)
    bp.set(0, 2, R, 'minecraft:stone_brick_stairs[facing=north,half=top,shape=straight,waterlogged=false]')
    for y in (5, 9):
        for x, z in ((-R, 0), (R, 0), (0, R)):
            bp.set(x, y, z, AIR)
    # 中ほどの床 (半分抜けている)
    for x in range(-R + 1, R):
        for z in range(-R + 1, R):
            if math.hypot(x, z) < R - 0.8 and (x < 1 or rng.random() < 0.3) and not (x == 0 and z == -R + 1):
                bp.set(x, 12, z, 'minecraft:spruce_planks' if rng.random() > 0.15 else 'minecraft:spruce_slab[type=bottom,waterlogged=false]')
    bp.set(0, 12, -R + 1, 'minecraft:spruce_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]')
    bp.set(-2, 13, 0, 'minecraft:chest[facing=east,type=single,waterlogged=false]')
    bp.set(-2, 13, 1, 'minecraft:skeleton_skull[rotation=4]')
    # 崩れた石 (まわり)
    for k in range(14):
        a = rng.random() * 2 * math.pi
        r = R + 1 + rng.random() * 3
        x, z = int(round(math.cos(a) * r)), int(round(math.sin(a) * r))
        if x > -1:
            bp.set(x, 0, z, rng.choice(['minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:stone_brick_slab[type=bottom,waterlogged=false]']))
    bp.mark('loot', -2, 13, 0)
    return bp


def traveler_camp(rng):
    """旅の一座の野営 (幌馬車・天幕・焚き火。誰もいない。荷に宝箱)"""
    bp = BP('poi_traveler_camp')
    ground(bp, 8, 'minecraft:grass_block[snowy=false]', rng, 'minecraft:coarse_dirt', 0.35)
    # 幌馬車 (x に沿う。車輪・荷台・白い幌)
    for x in range(-6, 1):
        for z in (-4, -2):
            bp.set(x, 1, z, 'minecraft:spruce_slab[type=top,waterlogged=false]')
        bp.set(x, 1, -3, 'minecraft:spruce_slab[type=top,waterlogged=false]')
    for x in (-6, 0):
        for z in (-5, -1):
            bp.set(x, 0, z, 'minecraft:stripped_spruce_wood[axis=z]')
    for x in range(-5, 0):
        for (z, y) in ((-5, 2), (-5, 3), (-1, 2), (-1, 3)):
            bp.set(x, y, z, 'minecraft:white_wool')
        for z in (-4, -3, -2):
            bp.set(x, 4, z, 'minecraft:white_wool')
    bp.set(-3, 2, -3, 'minecraft:barrel[facing=up,open=false]')
    bp.set(-4, 2, -3, 'minecraft:chest[facing=east,type=single,waterlogged=false]')
    bp.set(1, 0, -3, 'minecraft:spruce_fence')
    bp.set(2, 0, -3, 'minecraft:spruce_fence')
    # 天幕 (青と黄)
    for (ox, oz, wool) in ((4, 2, 'minecraft:blue_wool'), (-3, 4, 'minecraft:yellow_wool')):
        for k in range(0, 3):
            for z in range(oz - 1, oz + 2):
                bp.set(ox - 2 + k, k, z, wool)
                bp.set(ox + 2 - k, k, z, wool)
        for z in range(oz - 1, oz + 2):
            bp.set(ox, 2, z, wool)
        bp.set(ox, 0, oz, 'minecraft:hay_block[axis=y]')
    bp.set(1, 0, 1, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    for x, z in ((0, 3), (3, -0)):
        bp.set(x, 0, z, 'minecraft:spruce_log[axis=x]')
    bp.set(-1, 0, 0, 'minecraft:cauldron')
    bp.set(6, 0, -2, 'minecraft:note_block[instrument=harp,note=0,powered=false]')
    bp.set(6, 0, -3, 'minecraft:jukebox[has_record=false]')
    # 立て札の灯り
    bp.fill(-1, 0, 6, -1, 1, 6, 'minecraft:spruce_fence')
    bp.set(-1, 2, 6, lantern())
    bp.mark('loot', -4, 2, -3)
    return bp


def graveyard(rng):
    """丘の墓所 (石の柵の中に墓石。奥に小さな礼拝堂の跡。夜でなくても骨が起きる)"""
    bp = BP('poi_graveyard')
    ground(bp, 9, 'minecraft:grass_block[snowy=false]', rng, 'minecraft:podzol', 0.3)
    for x in range(-8, 9):
        for z in range(-8, 9):
            if max(abs(x), abs(z)) == 8 and not (abs(x) <= 1 and z == 8) and rng.random() > 0.15:
                bp.set(x, 0, z, 'minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]' if rng.random() > 0.3 else 'minecraft:mossy_cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]')
    for x in (-2, 2):
        bp.fill(x, 0, 8, x, 2, 8, 'minecraft:stone_bricks')
        bp.set(x, 3, 8, lantern())
    # 墓石 (三列)
    for row, z in enumerate((4, 1, -2)):
        for x in (-6, -4, 4, 6):
            if rng.random() < 0.2:
                continue
            bp.set(x, 0, z, rng.choice(['minecraft:andesite_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]',
                                        'minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]']))
            bp.set(x, -1, z + 1, 'minecraft:coarse_dirt')
            if rng.random() < 0.4:
                bp.set(x, 0, z + 1, rng.choice(['minecraft:poppy', 'minecraft:oxeye_daisy', 'minecraft:dead_bush']))
    for z in range(-3, 8):
        bp.set(0, -1, z, 'minecraft:gravel' if rng.random() > 0.3 else 'minecraft:coarse_dirt')
        bp.set(-1, -1, z, 'minecraft:gravel' if rng.random() > 0.5 else 'minecraft:coarse_dirt')
    # 礼拝堂の跡 (北)
    walls(bp, -3, -8, 3, -4, 5, 'minecraft:stone_bricks', 'minecraft:chiseled_stone_bricks', broken=0.6, rng=rng, windows=False)
    for y in (0, 1, 2):
        bp.set(0, y, -4, AIR)
    for x in range(-2, 3):
        for z in range(-7, -4):
            bp.set(x, -1, z, 'minecraft:polished_andesite' if (x + z) % 2 else 'minecraft:mossy_stone_bricks')
    bp.set(0, 0, -7, 'minecraft:chest[facing=south,type=single,waterlogged=false]')
    for x in (-2, 2):
        bp.set(x, 0, -7, 'minecraft:candle[candles=3,lit=true,waterlogged=false]')
    bp.set(0, 1, -8, 'minecraft:chiseled_stone_bricks')
    # 枯れ木
    bp.fill(6, 0, -6, 6, 4, -6, 'minecraft:dark_oak_log[axis=y]')
    bp.set(5, 4, -6, 'minecraft:dark_oak_log[axis=x]')
    bp.set(7, 3, -6, 'minecraft:dark_oak_log[axis=x]')
    bp.mark('loot', 0, 0, -7)
    bp.mark('foes', 0, 0, 2)
    return bp


def great_tree(rng):
    """大樹と樹上の小屋 (太い幹にはしご、枝の上に板の床と小屋。上に宝箱)"""
    bp = BP('poi_great_tree')
    ground(bp, 8, 'minecraft:grass_block[snowy=false]', rng, 'minecraft:moss_block', 0.3)
    H = 22
    for y in range(-1, H):
        r = 2.2 if y < 3 else 1.6 if y < 14 else 1.0
        for x in range(-3, 4):
            for z in range(-3, 4):
                if math.hypot(x, z) <= r:
                    bp.set(x, y, z, 'minecraft:oak_wood[axis=y]' if y < 3 else 'minecraft:oak_log[axis=y]')
    # 根
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1)):
        for k in range(3, 6):
            if rng.random() < 0.8:
                bp.set(dx * k, 0 if k < 5 else -1, dz * k, 'minecraft:oak_wood[axis=y]' if k < 5 else 'minecraft:rooted_dirt')
    # はしご (南の面)
    for y in range(0, 13):
        bp.set(0, y, 2, 'minecraft:ladder[facing=south,waterlogged=false]')
    # 樹上の床と小屋
    fy = 13
    for x in range(-5, 6):
        for z in range(-5, 6):
            if math.hypot(x, z) <= 5.3 and not (math.hypot(x, z) <= 1.6):
                bp.set(x, fy, z, 'minecraft:spruce_planks')
                if math.hypot(x, z) > 4.4:
                    bp.set(x, fy + 1, z, 'minecraft:spruce_fence')
    bp.set(0, fy, 2, 'minecraft:spruce_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]')
    bp.set(0, fy + 1, 2, AIR)
    # 小屋 (北東)
    for x in range(1, 5):
        for z in range(-5, -1):
            if x in (1, 4) or z in (-5, -2):
                bp.fill(x, fy + 1, z, x, fy + 3, z, 'minecraft:spruce_planks' if not (x in (1, 4) and z in (-5, -2)) else 'minecraft:stripped_spruce_log[axis=y]')
            else:
                bp.fill(x, fy + 1, z, x, fy + 3, z, AIR)
            bp.set(x, fy + 4, z, slab('minecraft:spruce'))
    bp.set(2, fy + 1, -2, AIR)
    bp.set(2, fy + 2, -2, AIR)
    bp.set(3, fy + 1, -4, 'minecraft:chest[facing=south,type=single,waterlogged=false]')
    bp.set(2, fy + 3, -3, lantern(True))
    # 枝と葉
    for y in range(fy + 2, H + 6):
        rr = 7 - abs(y - (H + 1)) * 0.6
        for x in range(-8, 9):
            for z in range(-8, 9):
                d = math.hypot(x, z)
                if d <= rr and rng.random() < 0.85 and bp.get(x, y, z) is None:
                    bp.set(x, y, z, 'minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]')
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for k in range(2, 6):
            bp.set(dx * k, fy + 5 + k // 2, dz * k, 'minecraft:oak_log[axis=%s]' % ('x' if dx else 'z'))
    bp.mark('loot', 3, fy + 1, -4)
    return bp


def cave(rng):
    """洞窟の小さな迷宮: 岩の口から階段で下り、地下の広間三つを細い通路でつなぐ。奥の広間に宝箱、中ほどに骨の兵 (foes は地下)"""
    bp = BP('poi_cave')
    # 岩の口 (地上の小山。南が開く)
    for x in range(-5, 6):
        for z in range(-5, 6):
            d = math.hypot(x, z * 1.2)
            h = int(round(4.5 - d * 0.9 + rng.random() * 0.8))
            for y in range(0, max(0, h)):
                bp.set(x, y, z, rng.choice(['minecraft:stone', 'minecraft:stone', 'minecraft:andesite', 'minecraft:mossy_cobblestone', 'minecraft:cobblestone']))
    for z in range(-1, 6):
        for x in (-1, 0, 1):
            for y in range(0, 4):
                bp.set(x, y, z, AIR)
    # 下る階段 (北へ。口の奥から)
    for k in range(11):
        z = -1 - k
        y = -1 - k
        for x in (-1, 0, 1):
            for yy in range(y, y + 4):
                bp.set(x, yy, z, AIR)
            bp.set(x, y - 1, z, 'minecraft:cobblestone')
            bp.set(x, y, z, 'minecraft:stone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]' if rng.random() > 0.25 else 'minecraft:mossy_cobblestone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
    fy = -11

    def room(cx, cz, rx, rz, h):
        for x in range(cx - rx - 1, cx + rx + 2):
            for z in range(cz - rz - 1, cz + rz + 2):
                d = ((x - cx) / (rx + 0.5)) ** 2 + ((z - cz) / (rz + 0.5)) ** 2
                if d > 1.0:
                    continue
                hh = int(round(h * (1 - d * 0.6) + rng.random()))
                bp.set(x, fy - 1, z, rng.choice(['minecraft:gravel', 'minecraft:stone', 'minecraft:moss_block', 'minecraft:tuff']))
                for y in range(fy, fy + hh):
                    bp.set(x, y, z, AIR)
                if rng.random() < 0.06:
                    bp.set(x, fy, z, rng.choice(['minecraft:brown_mushroom', 'minecraft:cobweb', 'minecraft:moss_carpet', 'minecraft:pointed_dripstone[thickness=tip,vertical_direction=up,waterlogged=false]']))

    def tunnel(ax, az, bx, bz):
        n = max(abs(bx - ax), abs(bz - az))
        for i in range(n + 1):
            x = int(round(ax + (bx - ax) * i / n))
            z = int(round(az + (bz - az) * i / n))
            wob = int(round(math.sin(i / 3.0) * 1.2))
            for dx in (-1, 0, 1):
                for y in range(fy, fy + 3):
                    bp.set(x + dx + (wob if abs(bz - az) > abs(bx - ax) else 0), y, z + (wob if abs(bx - ax) >= abs(bz - az) else 0), AIR)
                bp.set(x + dx, fy - 1, z, 'minecraft:gravel')
    # 広間: 入口 (階段の下)・東の広間 (骨の兵)・北西の奥の広間 (宝箱)
    room(0, -15, 3, 3, 4)
    room(10, -20, 4, 3, 5)
    room(-4, -29, 5, 4, 6)
    tunnel(2, -16, 8, -20)
    tunnel(8, -22, -1, -27)
    # 灯り: 広間ごとに一つ (床の蝋燭か、壁ぎわの灯り)
    bp.set(2, fy, -14, 'minecraft:candle[candles=3,lit=true,waterlogged=false]')
    bp.set(12, fy, -19, 'minecraft:lantern[hanging=false,waterlogged=false]')
    bp.set(-6, fy, -28, 'minecraft:lantern[hanging=false,waterlogged=false]')
    # 奥の広間: 骨と宝箱
    bp.set(-4, fy, -32, 'minecraft:chest[facing=south,type=single,waterlogged=false]')
    for x, z in ((-6, -31), (-2, -31), (-3, -29)):
        bp.set(x, fy, z, 'minecraft:bone_block[axis=x]' if (x + z) % 2 else 'minecraft:skeleton_skull[rotation=6]')
    bp.mark('loot', -4, fy, -32)
    bp.mark('foes', 10, fy, -20)
    return bp


BUILDERS.update({'cave': cave, 'mine': mine, 'ruined_tower': ruined_tower, 'traveler_camp': traveler_camp, 'graveyard': graveyard, 'great_tree': great_tree})
BY_REGION['meadow'] = BY_REGION['meadow'] + ['traveler_camp', 'great_tree', 'ruined_tower', 'graveyard', 'cave']
BY_REGION['whale'] = BY_REGION['whale'] + ['mine', 'graveyard', 'great_tree', 'ruined_tower', 'cave']
BY_REGION['tide'] = BY_REGION['tide'] + ['traveler_camp', 'great_tree', 'ruined_tower']
BY_REGION['storm'] = BY_REGION['storm'] + ['mine', 'graveyard', 'ruined_tower', 'cave']
BY_REGION['ember'] = BY_REGION['ember'] + ['mine', 'traveler_camp', 'graveyard']


def signposts(lay):
    """道しるべ: 道の上の決まった所 (都・町・跡地) を節にして、節を出たところと、長い区間の中ほどの脇に一本ずつ。
    札は「この先の節と道のり / 来た節と道のり」 (道の途中の町も節に数える)"""
    towns = {'鯨見': 'whale', '汐守': 'tide', '風待': 'storm', '灰炉': 'ember'}
    sites = {'岩鯨の跡地': 'whale', '潮殻の跡地': 'tide', '雷翼の跡地': 'storm', '余燼の跡地': 'ember'}
    named = [('capital', lay['capital'])] + [('town_' + towns[n], q) for n, q in lay['towns']] + [('site_' + sites[n], q) for n, q in lay['sites']]

    def leave(name):
        # 節を出たところ (都は大きいので遠め。町は壁の外。跡地は整えた場所の外)
        return 150 if name == 'capital' else 64 if name.startswith('town_') else 190

    def r10(v):
        return int(round(v / 10) * 10)
    out = []
    for r in lay['roads']:
        cum = [0.0]
        for k in range(len(r) - 1):
            cum.append(cum[-1] + math.dist(r[k], r[k + 1]))
        total = cum[-1]
        # 節: 道の点のうち、決まった所から 5 以内のもの
        nodes = []
        for k, pt in enumerate(r):
            for name, q in named:
                if math.dist(pt, q) < 5 and (not nodes or nodes[-1][0] != name):
                    nodes.append((name, cum[k]))
        if len(nodes) < 2:
            continue

        def point_at(want):
            for k in range(len(r) - 1):
                L = cum[k + 1] - cum[k]
                if want <= cum[k + 1] and L > 0:
                    (ax, az), (bx, bz) = r[k], r[k + 1]
                    t = (want - cum[k]) / L
                    x, z = ax + (bx - ax) * t, az + (bz - az) * t
                    nx, nz = -(bz - az) / L, (bx - ax) / L
                    return int(round(x + nx * 6)), int(round(z + nz * 6))
            return None
        for (na, da), (nb, db) in zip(nodes, nodes[1:]):
            span = db - da
            stops = []
            if span >= leave(na) + 120:
                stops.append((da + leave(na), True))
            if span >= 250:
                stops.append(((da + db) / 2, True))
            if span >= leave(nb) + 120:
                stops.append((db - leave(nb), False))
            # 近すぎる札は一本に (120 より近いものは先の一本だけ)
            kept = []
            for d, forward in sorted(stops):
                if not kept or d - kept[-1][0] >= 120:
                    kept.append((d, forward))
            for d, forward in kept:
                at = point_at(d)
                if at is None:
                    continue
                if forward:
                    out.append({'kind': 'signpost', 'x': at[0], 'z': at[1], 'a': nb, 'da': r10(db - d), 'b': na, 'db': r10(d - da)})
                else:
                    out.append({'kind': 'signpost', 'x': at[0], 'z': at[1], 'a': na, 'da': r10(d - da), 'b': nb, 'db': r10(db - d)})
    return out


# 伝承の碑 (見どころの由来を三行で。文は lang の sign.skyabyss.poi_<種類>.1〜3。ContinentPois が建てたあとに書く)
TABLETS = {
    'titan_ribs': (0, 10), 'monastery': (0, 8), 'stone_circle': (0, 3), 'lighthouse': (2, 5), 'wind_gate': (0, 4),
    'basalt_gate': (0, 3), 'burnt_fort': (3, 12), 'rod_tower': (2, 4), 'forge_ruin': (0, 6),
    'cave': (3, 6), 'mine': (4, 3), 'ruined_tower': (0, 6), 'graveyard': (3, 6), 'great_tree': (0, 6), 'traveler_camp': (3, 6),
}


def main():
    os.makedirs(OUT, exist_ok=True)
    rng = np.random.default_rng(5)
    for kind, fn in BUILDERS.items():
        bp = fn(np.random.default_rng(abs(hash(kind)) % (2 ** 31) if False else len(kind) * 7919))
        if kind in TABLETS:
            tx, tz = TABLETS[kind]
            bp.set(tx, 0, tz, 'skyabyss:tablet[facing=south,standing=true]')
            bp.mark('tablet', tx, 0, tz)
        bp.name = 'poi_' + kind
        # 均す範囲は地上に出る所だけ (坑道の掘り場のような地の下は数えない)。まわりは広めになじませる
        r = max(max(abs(x), abs(z)) for (x, y, z) in bp.cells if y >= -1) + 1
        bp.meta_rect = r
        bp.meta_ring = 6
        bp.meta_round = True
        print(f'poi_{kind}: {bp.save()} blocks (rect {r})')
    # 置き場
    sys.path.insert(0, HERE)
    import gen_continent as gc
    lay, height, biome, reg = gc.build()
    pois = [(k, int(x), int(z)) for k, (x, z) in lay.get('fixed_pois', [])] + place(height, reg, lay)
    with open(os.path.join(CONT, 'pois.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump([{'kind': k, 'x': x, 'z': z} for k, x, z in pois] + signposts(lay), f, ensure_ascii=False, separators=(',', ':'))
        f.write('\n')
    print(len(pois), 'pois placed')


if __name__ == '__main__':
    main()
