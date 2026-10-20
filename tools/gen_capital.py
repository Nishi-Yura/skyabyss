# -*- coding: utf-8 -*-
"""はじまりの都「鐘の宿り」(docs/design/33 の 2。案 A「鐘の都」) の設計図を書く。

python -I tools/gen_capital.py            → src/main/resources/data/skyabyss/opening/capital.json と docs/review/town_roughs/capital_plan.png
部品の座標: 中心が (0, 0)。+z が南 (野営地の方。SiteFinder が野営地の向きへ回す)。y=0 が地面の上の最初の段 (地面の上面は y=-1)。
範囲は x, z とも -100〜100 (壁の中が約 200×200)。設計図は "boxes" (直方体の塗り) と "blocks" (一つずつ) で書き、Blueprint が広げる。
建物はまず「骨組み」(壁・屋根・戸口・碑) で、閉じた施設 (砦・館・組合・社) は戸が閉じていて碑が一行言う。一棟ずつの作り込みは W1 の次の段。
"""
from __future__ import annotations

import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss', 'opening')
PREVIEW = os.path.join(ROOT, 'docs', 'review', 'town_roughs', 'capital_plan.png')
M = 'minecraft:'

palette: list[str] = []
pidx: dict[str, int] = {}
boxes: list[list[int]] = []
blocks: list[list[int]] = []
marks: dict[str, list[int]] = {}


def P(s: str) -> int:
    s = s if ':' in s else M + s
    if s not in pidx:
        pidx[s] = len(palette)
        palette.append(s)
    return pidx[s]


def box(x0, y0, z0, x1, y1, z1, s):
    boxes.append([min(x0, x1), min(y0, y1), min(z0, z1), max(x0, x1), max(y0, y1), max(z0, z1), P(s)])


def blk(x, y, z, s):
    blocks.append([x, y, z, P(s)])


def mark(name, x, y, z):
    marks[name] = [x, y, z]


# ---------------------------------------------------------------- 部品
def floor(x0, z0, x1, z1, s, y=-1):
    box(x0, y, z0, x1, y, z1, s)


def checker(x0, z0, x1, z1, a, b, y=-1, step=4):
    """広場の市松 (step ごと)"""
    for x in range(x0, x1 + 1, step):
        for z in range(z0, z1 + 1, step):
            s = a if ((x - x0) // step + (z - z0) // step) % 2 == 0 else b
            box(x, y, z, min(x + step - 1, x1), y, min(z + step - 1, z1), s)


def hollow(x0, y0, z0, x1, y1, z1, wall, roof=None, floor_s=None, corner=None):
    """中が空の建物: 四方の壁 (厚さ 1)、屋根、床。corner は四隅の柱"""
    if floor_s:
        box(x0, y0 - 1, z0, x1, y0 - 1, z1, floor_s)
    box(x0, y0, z0, x1, y1, z0, wall)
    box(x0, y0, z1, x1, y1, z1, wall)
    box(x0, y0, z0, x0, y1, z1, wall)
    box(x1, y0, z0, x1, y1, z1, wall)
    if corner:
        for cx, cz in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            box(cx, y0, cz, cx, y1, cz, corner)
    if roof:
        box(x0, y1 + 1, z0, x1, y1 + 1, z1, roof)


def opening(x0, y0, z0, x1, y1, z1):
    box(x0, y0, z0, x1, y1, z1, 'air')


def windows(x0, z0, x1, z1, y, step=4):
    """壁の中ほどに板ガラスの窓 (北と南の壁)"""
    for x in range(x0 + 2, x1 - 1, step):
        blk(x, y, z0, 'glass_pane[north=false,south=false,east=true,west=true]')
        blk(x, y, z1, 'glass_pane[north=false,south=false,east=true,west=true]')


def tablet(x, y, z, facing, name):
    blk(x, y, z, 'skyabyss:tablet[facing=%s,standing=true]' % facing)
    mark('sign_' + name, x, y, z)


def lantern_post(x, y, z, h=3):
    box(x, y, z, x, y + h - 1, z, 'spruce_fence')
    blk(x, y + h, z, 'lantern[hanging=false]')


# ---------------------------------------------------------------- 都
def walls():
    W = 100
    T = 2  # 壁の厚さ 3
    for (ax0, az0, ax1, az1) in ((-W, -W, W, -W + T), (-W, W - T, W, W), (-W, -W, -W + T, W), (W - T, -W, W, W)):
        box(ax0, 0, az0, ax1, 7, az1, 'stone_bricks')
        box(ax0, -1, az0, ax1, -1, az1, 'cobbled_deepslate')
    # 胸壁 (外側の列に、一つおき)
    for i in range(-W, W + 1, 2):
        blk(i, 8, -W, 'stone_bricks')
        blk(i, 8, W, 'stone_bricks')
        blk(-W, 8, i, 'stone_bricks')
        blk(W, 8, i, 'stone_bricks')
    # 隅の塔 9×9、高さ 14
    for cx, cz in ((-W, -W), (W - 8, -W), (-W, W - 8), (W - 8, W - 8)):
        box(cx, 0, cz, cx + 8, 13, cz + 8, 'stone_bricks')
        box(cx + 1, 1, cz + 1, cx + 7, 12, cz + 7, 'air')
        box(cx, 14, cz, cx + 8, 14, cz + 8, 'deepslate_tile_slab[type=bottom]')
        blk(cx + 4, 14, cz + 4, 'lantern[hanging=false]')
    # 四つの門 (幅 7、高さ 6)。北 鯨門・南 灰門・西 風門・東 潮門
    for name, (x0, z0, x1, z1), (tx, tz, tf) in (
            ('gate_n', (-3, -W, 3, -W + T), (6, -W + T + 1, 'south')),
            ('gate_s', (-3, W - T, 3, W), (6, W - T - 1, 'north')),
            ('gate_w', (-W, -3, -W + T, 3), (-W + T + 1, 6, 'east')),
            ('gate_e', (W - T, -3, W, 3), (W - T - 1, 6, 'west'))):
        box(x0, 0, z0, x1, 5, z1, 'air')
        box(x0, -1, z0, x1, -1, z1, 'stone_bricks')
        # 門の上の帯と灯り
        box(x0, 6, z0, x1, 7, z1, 'polished_deepslate')
        if name in ('gate_n', 'gate_s'):
            blk(x0 - 1, 5, z0 if name == 'gate_n' else z1, 'lantern[hanging=false]')
            blk(x1 + 1, 5, z0 if name == 'gate_n' else z1, 'lantern[hanging=false]')
        else:
            blk(x0 if name == 'gate_w' else x1, 5, z0 - 1, 'lantern[hanging=false]')
            blk(x0 if name == 'gate_w' else x1, 5, z1 + 1, 'lantern[hanging=false]')
        tablet(tx, 0, tz, tf, name)


def streets():
    # 大通り (南北・東西。幅 8)。石レンガと丸石の筋
    for x in range(-4, 4):
        s = 'stone_bricks' if x in (-4, 3) else 'cobblestone'
        box(x, -1, -97, x, -1, 97, s)
    for z in range(-4, 4):
        s = 'stone_bricks' if z in (-4, 3) else 'cobblestone'
        box(-97, -1, z, 97, -1, z, s)
    # 灯りの柱 (大通り沿い 16 ごと)
    for z in range(-88, 89, 16):
        if abs(z) < 6:
            continue
        lantern_post(-6, 0 if z > -41 else 5, z)
        lantern_post(5, 0 if z > -41 else 5, z)
    for x in range(-88, 89, 16):
        if abs(x) < 6:
            continue
        lantern_post(x, 0, -6)
        lantern_post(x, 0, 5)


def terrace():
    # 北の段: x -60..60, z -97..-41。高さ 5 (y 0..4)。前面と側面は石レンガ、上は草
    box(-60, 0, -97, 60, 3, -41, 'dirt')
    box(-60, 4, -97, 60, 4, -41, 'grass_block')
    box(-60, 0, -41, 60, 4, -41, 'stone_bricks')
    box(-60, 0, -97, -60, 4, -41, 'stone_bricks')
    box(60, 0, -97, 60, 4, -41, 'stone_bricks')
    box(-60, 5, -41, 60, 5, -41, 'stone_brick_wall[up=true,north=none,south=none,east=low,west=low,waterlogged=false]')
    # 中央の階段 (大通りが段へ上がる): z -36 → -41 で y 0 → 4
    box(-4, 0, -41, 3, 4, -36, 'stone_bricks')
    for i in range(5):
        z = -36 - i
        box(-4, i, z, 3, 4, z, 'air')
        box(-4, i, z, 3, i, z, 'stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
    box(-4, 5, -41, 3, 5, -41, 'air')
    box(-4, 4, -97, 3, 4, -42, 'stone_bricks')  # 段の上の大通り
    lantern_post(-6, 5, -44)
    lantern_post(5, 5, -44)


def gable(x0, x1, z0, z1, y, block='spruce_stairs', cap='spruce_slab[type=bottom,waterlogged=false]'):
    """東西に棟のある切妻 (ラフ A と同じ)。z0..z1 の両側から段々に上がる"""
    depth = (z1 - z0) // 2
    for i in range(depth + 1):
        zn, zs = z0 + i, z1 - i
        if zn >= zs:
            box(x0, y + i, zn, x1, y + i, zn, cap)
            break
        box(x0, y + i, zn, x1, y + i, zn, block + '[facing=south,half=bottom,shape=straight,waterlogged=false]')
        box(x0, y + i, zs, x1, y + i, zs, block + '[facing=north,half=bottom,shape=straight,waterlogged=false]')


def bell_tower():
    # 大鐘楼 (ラフ A「石の鐘楼」で決定): 石レンガの塔 9×9 (x -34..-26, z -80..-72)、段の上 (床 y 5)、高さ 24。
    # 四方のアーチの吹き抜け、一階の天井に鐘。頂は鐘の間 (四方が開く) と段々の尖り屋根
    x0, z0, x1, z1 = -34, -80, -26, -72
    cx, cz = -30, -76
    box(x0, 4, z0, x1, 4, z1, 'polished_deepslate')
    box(x0, 5, z0, x1, 28, z1, 'stone_bricks')
    box(x0 + 1, 5, z0 + 1, x1 - 1, 27, z1 - 1, 'air')
    for ccx, ccz in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        box(ccx, 5, ccz, ccx, 28, ccz, 'deepslate_bricks')
    # 一階の四方のアーチ (幅 3、高さ 3)
    opening(cx - 1, 5, z0, cx + 1, 7, z0)
    opening(cx - 1, 5, z1, cx + 1, 7, z1)
    opening(x0, 5, cz - 1, x0, 7, cz + 1)
    opening(x1, 5, cz - 1, x1, 7, cz + 1)
    # 一階の天井 (y 8) と鐘 (天井に吊る。語り部はその足もと)
    box(x0 + 1, 8, z0 + 1, x1 - 1, 8, z1 - 1, 'spruce_planks')
    blk(cx, 7, cz, 'bell[attachment=ceiling,facing=north,powered=false]')
    mark('bell', cx, 7, cz)
    # 上階の細い窓
    for y in (13, 18, 23):
        opening(cx, y, z0, cx, y + 1, z0)
        opening(cx, y, z1, cx, y + 1, z1)
        opening(x0, y, cz, x0, y + 1, cz)
        opening(x1, y, cz, x1, y + 1, cz)
    # 頂の鐘の間 (y 25..27 は四方が開く)。床 y 24、二つ目の鐘
    box(x0 + 1, 24, z0 + 1, x1 - 1, 24, z1 - 1, 'spruce_planks')
    opening(cx - 2, 25, z0, cx + 2, 27, z0)
    opening(cx - 2, 25, z1, cx + 2, 27, z1)
    opening(x0, 25, cz - 2, x0, 27, cz + 2)
    opening(x1, 25, cz - 2, x1, 27, cz + 2)
    blk(cx, 27, cz, 'bell[attachment=ceiling,facing=north,powered=false]')
    # 段々の尖り屋根 (y 29..33) と頂の灯り
    for i in range(5):
        y = 29 + i
        box(x0 + i, y, z0 + i, x1 - i, y, z1 - i, 'deepslate_tiles' if i < 4 else 'deepslate_tile_slab[type=bottom,waterlogged=false]')
        if 1 <= i < 4:
            box(x0 + i + 1, y, z0 + i + 1, x1 - i - 1, y, z1 - i - 1, 'air')
    blk(cx, 34, cz, 'lantern[hanging=false]')
    # 足もとの灯り
    for dx, dz in ((-2, 2), (2, 2), (-2, -2), (2, -2)):
        lantern_post(cx + dx * 3, 5, cz + dz * 3, 2)


def storyteller_house():
    # 語り部の家 (鐘楼の東、x -22..-12, z -82..-72)。床 y 4 (段の上)、壁 y 5..8、屋根 y 9
    x0, z0, x1, z1 = -22, -82, -12, -72
    hollow(x0, 5, z0, x1, 8, z1, 'spruce_planks', roof=None, floor_s='spruce_planks', corner='stripped_spruce_log[axis=y]')
    box(x0 + 1, 9, z0 + 1, x1 - 1, 9, z1 - 1, 'spruce_planks')
    gable(x0 - 1, x1 + 1, z0 - 1, z1 + 1, 9)
    # 戸 (南)
    opening(-17, 5, z1, -17, 6, z1)
    blk(-17, 5, z1, 'spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]')
    blk(-17, 6, z1, 'spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]')
    windows(x0, z0, x1, z1, 6, 4)
    # 中: 寝台・書見台・樽・灯り
    blk(x0 + 1, 5, z0 + 1, 'red_bed[facing=south,part=head,occupied=false]')
    blk(x0 + 1, 5, z0 + 2, 'red_bed[facing=south,part=foot,occupied=false]')
    blk(x1 - 1, 5, z0 + 1, 'lectern[facing=south,has_book=false,powered=false]')
    blk(x1 - 1, 5, z1 - 1, 'barrel[facing=up,open=false]')
    blk(x1 - 2, 5, z1 - 1, 'barrel[facing=up,open=false]')
    blk(-17, 8, -77, 'lantern[hanging=true]')
    # 煙の出る焚き火 (わら束の上)。家の前
    blk(-17, 5, -68, 'hay_block[axis=y]')
    blk(-17, 6, -68, 'campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]')
    mark('smoke', -17, 6, -68)
    mark('door', -17, 5, -72)


def keep():
    # 空見の砦 (ラフ A「四隅の塔の砦」で決定。x 10..54, z -92..-52)。段の上。壁 y 5..14 厚さ 2、隅の塔、見張り塔、本丸、南の門は閉じている (鉄格子)
    x0, z0, x1, z1 = 10, -92, 54, -52
    box(x0, 4, z0, x1, 4, z1, 'cobbled_deepslate')
    for (ax0, az0, ax1, az1) in ((x0, z0, x1, z0 + 1), (x0, z1 - 1, x1, z1), (x0, z0, x0 + 1, z1), (x1 - 1, z0, x1, z1)):
        box(ax0, 5, az0, ax1, 14, az1, 'stone_bricks')
    for i in range(x0, x1 + 1, 2):
        blk(i, 15, z0, 'stone_bricks')
        blk(i, 15, z1, 'stone_bricks')
    for i in range(z0, z1 + 1, 2):
        blk(x0, 15, i, 'stone_bricks')
        blk(x1, 15, i, 'stone_bricks')
    for cx, cz in ((x0, z0), (x1 - 5, z0), (x0, z1 - 5), (x1 - 5, z1 - 5)):
        box(cx, 5, cz, cx + 5, 18, cz + 5, 'deepslate_bricks')
        box(cx + 1, 6, cz + 1, cx + 4, 17, cz + 4, 'air')
        box(cx, 19, cz, cx + 5, 19, cz + 5, 'deepslate_tile_slab[type=bottom,waterlogged=false]')
    # 見張り塔 (北東。9×9、高さ 30)
    tx0, tz0 = 40, -88
    box(tx0, 5, tz0, tx0 + 8, 34, tz0 + 8, 'stone_bricks')
    box(tx0 + 1, 6, tz0 + 1, tx0 + 7, 33, tz0 + 7, 'air')
    box(tx0, 35, tz0, tx0 + 8, 35, tz0 + 8, 'spruce_slab[type=bottom,waterlogged=false]')
    box(tx0 + 2, 34, tz0 + 2, tx0 + 6, 34, tz0 + 6, 'air')
    blk(tx0 + 4, 36, tz0 + 4, 'lantern[hanging=false]')
    for y in (12, 20, 28):
        opening(tx0 + 3, y, tz0, tx0 + 5, y + 1, tz0)
        opening(tx0 + 3, y, tz0 + 8, tx0 + 5, y + 1, tz0 + 8)
    # 本丸 (ラフ A で決定): 閉じた低い石の館 20×12 (x 18..37, z -80..-69)。鉄の戸、三章で記録の間が開く
    hollow(18, 5, -80, 37, 11, -69, 'stone_bricks', roof='deepslate_tiles', floor_s='polished_deepslate', corner='deepslate_bricks')
    blk(27, 5, -69, 'iron_door[facing=north,half=lower,hinge=left,open=false,powered=false]')
    blk(27, 6, -69, 'iron_door[facing=north,half=upper,hinge=left,open=false,powered=false]')
    blk(24, 5, -68, 'lantern[hanging=false]')
    # 南の門 (幅 5、高さ 5)。鉄格子で閉じる
    box(30, 5, z1 - 1, 34, 9, z1, 'air')
    box(30, 5, z1, 34, 9, z1, 'iron_bars[north=false,south=false,east=true,west=true,waterlogged=false]')
    tablet(28, 5, z1 + 1, 'south', 'keep')
    lantern_post(29, 5, z1 + 2)
    lantern_post(35, 5, z1 + 2)


def hall():
    # 空見の館 (ラフ B「記録の塔の館」で決定。段の上、床 y 4)。館 19×13 (x -58..-40, z -96..-84) と、東に丸い記録の塔 (直径 9・高さ 18)。
    # 中は本棚と書見台。南の戸は閉じた鉄の戸、碑
    x0, z0, x1, z1 = -58, -96, -40, -84
    cz = (z0 + z1) // 2
    hollow(x0, 5, z0, x1, 10, z1, 'stone_bricks', roof=None, floor_s='polished_andesite', corner='deepslate_bricks')
    for x in range(x0 + 2, x1 - 1, 3):
        for y in (6, 7):
            blk(x, y, z0, 'glass_pane[north=false,south=false,east=true,west=true]')
            if x != x0 + 9:
                blk(x, y, z1, 'glass_pane[north=false,south=false,east=true,west=true]')
    box(x0 + 1, 11, z0 + 1, x1 - 1, 11, z1 - 1, 'deepslate_tiles')
    gable(x0 - 1, x1 + 1, z0 - 1, z1 + 1, 11, block='deepslate_tile_stairs', cap='deepslate_tile_slab[type=bottom,waterlogged=false]')
    # 本棚 (北と南の壁ぎわ、二段) と書見台
    box(x0 + 1, 5, z0 + 1, x1 - 1, 6, z0 + 1, 'bookshelf')
    box(x0 + 1, 5, z1 - 1, x1 - 1, 6, z1 - 1, 'bookshelf')
    blk(x0 + 9, 5, cz, 'lectern[facing=south,has_book=false,powered=false]')
    blk(x0 + 5, 9, cz, 'lantern[hanging=true]')
    blk(x1 - 5, 9, cz, 'lantern[hanging=true]')
    # 丸い記録の塔 (八角で近似。中心 -35, -90)
    tcx, tcz, rr = -35, cz, 4
    box(tcx - rr, 4, tcz - rr, tcx + rr, 4, tcz + rr, 'polished_andesite')
    for y in range(5, 23):
        for x in range(tcx - rr, tcx + rr + 1):
            for z in range(tcz - rr, tcz + rr + 1):
                d = abs(x - tcx) + abs(z - tcz)
                if d == rr + 1 or (abs(x - tcx) == rr and abs(z - tcz) <= 1) or (abs(z - tcz) == rr and abs(x - tcx) <= 1):
                    win = (y - 5) % 6 == 3 and (x + z) % 2 == 0
                    blk(x, y, z, 'glass_pane[north=false,south=false,east=false,west=false]' if win else 'deepslate_bricks')
    for x in range(tcx - rr, tcx + rr + 1):
        for z in range(tcz - rr, tcz + rr + 1):
            if abs(x - tcx) + abs(z - tcz) <= rr:
                blk(x, 23, z, 'deepslate_tile_slab[type=bottom,waterlogged=false]')
    blk(tcx, 24, tcz, 'lantern[hanging=false]')
    # 館と塔をつなぐ戸口 (館の東の壁)
    opening(x1, 5, cz, x1, 6, cz)
    opening(tcx - rr - 1, 5, tcz, tcx - rr - 1, 6, tcz)
    # 南の戸 (閉じた鉄の戸) と碑
    blk(x0 + 9, 5, z1, 'iron_door[facing=north,half=lower,hinge=left,open=false,powered=false]')
    blk(x0 + 9, 6, z1, 'iron_door[facing=north,half=upper,hinge=left,open=false,powered=false]')
    tablet(x0 + 7, 5, z1 + 1, 'south', 'hall')
    lantern_post(x0 + 6, 5, z1 + 2, 2)
    lantern_post(x0 + 12, 5, z1 + 2, 2)


def plaza_market():
    # 広場 (x -36..36, z -36..3) と市 (ラフ C「丸い市」で決定): 井戸の噴水を囲んで露店が輪に並び、外に腰掛けと灯りの輪
    import math
    checker(-36, -36, 36, 3, 'polished_andesite', 'stone_bricks', y=-1, step=4)
    cx, cz, R = 0, -16, 19
    for x in range(cx - R, cx + R + 1):
        for z in range(cz - R, cz + R + 1):
            d = math.hypot(x - cx, z - cz)
            if d <= R:
                blk(x, -1, z, 'polished_andesite' if int(d) % 4 < 2 else 'stone_bricks')
    # 噴水 (井戸)
    box(cx - 2, 0, cz - 2, cx + 2, 0, cz + 2, 'stone_bricks')
    box(cx - 1, -1, cz - 1, cx + 1, -1, cz + 1, 'water')
    box(cx - 1, 0, cz - 1, cx + 1, 0, cz + 1, 'air')
    blk(cx, 0, cz, 'stone_bricks')
    blk(cx, 1, cz, 'stone_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]')
    blk(cx, 2, cz, 'lantern[hanging=false]')
    # 露店 8 (輪)。柱・羊毛の庇・樽
    n = 1
    for k in range(8):
        a = math.radians(k * 45)
        x = int(round(math.cos(a) * 12)) + cx - 2
        z = int(round(math.sin(a) * 12)) + cz - 2
        for ex, ez in ((x, z), (x + 3, z), (x, z + 3), (x + 3, z + 3)):
            box(ex, 0, ez, ex, 2, ez, 'spruce_fence')
        box(x, 3, z, x + 3, 3, z + 3, 'orange_wool' if k % 2 == 0 else 'white_wool')
        blk(x + 1, 0, z + 1, 'barrel[facing=up,open=false]')
        blk(x + 2, 0, z + 2, 'spruce_slab[type=top,waterlogged=false]')
        if n <= 6:
            mark('villager_%d' % n, x + 2, 0, z - 1)
            n += 1
    # 腰掛けと灯りの輪
    for k in range(8):
        a = math.radians(k * 45 + 22)
        lantern_post(int(round(math.cos(a) * 18)) + cx, 0, int(round(math.sin(a) * 18)) + cz)
        blk(int(round(math.cos(a) * 16)) + cx, 0, int(round(math.sin(a) * 16)) + cz, 'spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
    mark('stand', 0, 0, 94)


def guild():
    # 鯨追いの組合 (ラフ A「長屋の組合」で決定。x 40..84, z -32..-10)。石の腰壁と木の壁、切妻、棟に鯨骨の肋を三つ。
    # 西の大戸 (鉄の両開き) は閉じている。外に言伝ての板の枠と名簿の碑
    x0, z0, x1, z1 = 40, -32, 84, -10
    cz = (z0 + z1) // 2
    box(x0, -1, z0, x1, -1, z1, 'spruce_planks')
    hollow(x0, 0, z0, x1, 2, z1, 'stone_bricks', roof=None, floor_s=None, corner='stripped_spruce_log[axis=y]')
    hollow(x0, 3, z0, x1, 7, z1, 'spruce_planks', roof=None, floor_s=None, corner='stripped_spruce_log[axis=y]')
    for x in range(x0 + 3, x1 - 2, 4):
        blk(x, 4, z0, 'glass_pane[north=false,south=false,east=true,west=true]')
        blk(x, 4, z1, 'glass_pane[north=false,south=false,east=true,west=true]')
    box(x0 + 1, 8, z0 + 1, x1 - 1, 8, z1 - 1, 'spruce_planks')
    gable(x0 - 1, x1 + 1, z0 - 1, z1 + 1, 8)
    # 肋: 棟をまたぐ骨の弧 (三つ)
    for x in (x0 + 8, (x0 + x1) // 2, x1 - 8):
        for z in range(z0 - 2, z1 + 3):
            y = 9 + 11 - abs(z - cz) * 11 // 13
            blk(x, y, z, 'bone_block[axis=z]')
    # 西の大戸 (閉じた鉄の戸 2 枚)
    opening(x0, 0, cz - 1, x0, 2, cz)
    blk(x0, 0, cz - 1, 'iron_door[facing=east,half=lower,hinge=left,open=false,powered=false]')
    blk(x0, 1, cz - 1, 'iron_door[facing=east,half=upper,hinge=left,open=false,powered=false]')
    blk(x0, 0, cz, 'iron_door[facing=east,half=lower,hinge=right,open=false,powered=false]')
    blk(x0, 1, cz, 'iron_door[facing=east,half=upper,hinge=right,open=false,powered=false]')
    # 言伝ての板の枠 (二本柱と庇) と名簿の碑
    for dz in (-2, 2):
        box(x0 - 3, 0, cz + 6 + dz, x0 - 3, 3, cz + 6 + dz, 'stripped_spruce_log[axis=y]')
    box(x0 - 3, 4, cz + 3, x0 - 3, 4, cz + 9, 'spruce_slab[type=bottom,waterlogged=false]')
    box(x0 - 3, 1, cz + 5, x0 - 3, 2, cz + 7, 'spruce_planks')
    tablet(x0 - 2, 0, cz - 5, 'west', 'guild')
    lantern_post(x0 - 2, 0, cz - 3)
    lantern_post(x0 - 2, 0, cz + 3)


TITAN_BLOCKS = ('stone_bricks', 'prismarine_bricks', 'cut_copper', 'polished_blackstone_bricks')  # 岩鯨・潮殻・雷翼・余燼


def shrine():
    # 四巨神の社 (ラフ C「石の輪の社」で決定。中心 -64, -16)。屋根のない社: 四つの巨神の石 (3×3・高さ 9) が輪に立ち、
    # 骨の梁が中央の鍵の間の台を覆う。輪は低い壁。東の門は閉じている (鉄格子) と碑
    cx, cz, r = -64, -16, 15
    box(cx - r, -1, cz - r, cx + r, -1, cz + r, 'polished_deepslate')
    wall = 'deepslate_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]'
    box(cx - r, 0, cz - r, cx + r, 0, cz - r, wall)
    box(cx - r, 0, cz + r, cx + r, 0, cz + r, wall)
    box(cx - r, 0, cz - r, cx - r, 0, cz + r, wall)
    box(cx + r, 0, cz - r, cx + r, 0, cz + r, wall)
    for (dx, dz), tb in zip(((-9, -9), (9, -9), (-9, 9), (9, 9)), TITAN_BLOCKS):
        box(cx + dx - 1, 0, cz + dz - 1, cx + dx + 1, 8, cz + dz + 1, tb)
        blk(cx + dx, 9, cz + dz, 'lantern[hanging=false]')
        for k in range(1, 9):
            x = cx + dx - (dx // 9) * k
            z = cz + dz - (dz // 9) * k
            blk(x, 8 + k // 3, z, 'bone_block[axis=y]')
    box(cx - 1, 11, cz - 1, cx + 1, 11, cz + 1, 'bone_block[axis=y]')
    blk(cx, 10, cz, 'lantern[hanging=true]')
    # 鍵の間の台
    box(cx - 1, 0, cz - 1, cx + 1, 0, cz + 1, 'polished_deepslate')
    blk(cx, 1, cz, 'lantern[hanging=false]')
    # 東の門 (閉じている) と碑
    opening(cx + r, 0, cz - 1, cx + r, 2, cz + 1)
    box(cx + r, 0, cz - 1, cx + r, 2, cz + 1, 'iron_bars[north=false,south=false,east=true,west=true,waterlogged=false]')
    tablet(cx + r + 1, 0, cz + 3, 'east', 'shrine')
    lantern_post(cx + r + 2, 0, cz - 3, 3)


def smithy():
    # 鍛冶場 (ラフ A「開いた鍛冶場」で決定。x 6..34, z 12..30)。丸石と石レンガの工房、南が大きく開き庇が張り出す。
    # 奥 (北) に共鳴の炉と煙突、金床・鍛冶台・砥石・溶鉱炉。東の隅に職人の店 (樽と棚)
    x0, z0, x1, z1 = 6, 12, 34, 30
    cx = (x0 + x1) // 2
    box(x0, -1, z0, x1, -1, z1 + 3, 'stone_bricks')
    hollow(x0, 0, z0, x1, 5, z1, 'cobblestone', roof=None, floor_s=None, corner='stripped_spruce_log[axis=y]')
    opening(x0 + 2, 0, z1, x1 - 2, 3, z1)
    for x in range(x0 + 2, x1 - 1, 4):
        blk(x, 2, z0, 'glass_pane[north=false,south=false,east=true,west=true]')
    box(x0 + 1, 6, z0 + 1, x1 - 1, 6, z1 - 1, 'spruce_planks')
    gable(x0 - 1, x1 + 1, z0 - 1, z1 + 3, 6)
    for x in (x0 + 1, cx, x1 - 1):
        box(x, 0, z1 + 3, x, 5, z1 + 3, 'stripped_spruce_log[axis=y]')
    # 炉と煙突 (北の壁ぎわ)
    blk(cx, 0, z0 + 2, 'skyabyss:resonance_forge')
    box(cx - 1, 0, z0 + 1, cx + 1, 1, z0 + 1, 'polished_deepslate')
    box(cx - 1, 2, z0 + 1, cx + 1, 12, z0 + 1, 'deepslate_bricks')
    blk(cx, 1, z0 + 2, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
    blk(cx + 4, 0, z0 + 3, 'anvil[facing=east]')
    blk(cx + 5, 0, z0 + 3, 'smithing_table')
    blk(cx + 6, 0, z0 + 3, 'grindstone[face=floor,facing=north]')
    blk(cx - 4, 0, z0 + 3, 'blast_furnace[facing=south,lit=false]')
    blk(cx - 6, 0, z0 + 3, 'chest[facing=south,type=single,waterlogged=false]')
    # 職人の店 (東の隅: 樽と棚、カウンター)
    box(x1 - 5, 0, z0 + 1, x1 - 1, 0, z0 + 1, 'barrel[facing=up,open=false]')
    box(x1 - 5, 0, z0 + 9, x1 - 1, 0, z0 + 9, 'spruce_slab[type=top,waterlogged=false]')
    blk(x1 - 3, 1, z0 + 1, 'lantern[hanging=false]')
    blk(cx, 5, (z0 + z1) // 2, 'lantern[hanging=true]')
    tablet(x0 + 6, 0, z1 + 4, 'south', 'smithy')


def houses():
    # 住まい (西南。3 列 × 4 行、7×7)。戸口は開いている。中は空
    n = 7
    for i in range(3):
        for j in range(4):
            x0 = -50 + i * 14
            z0 = 12 + j * 20
            x1, z1 = x0 + 6, z0 + 6
            hollow(x0, 0, z0, x1, 3, z1, 'spruce_planks', roof='spruce_slab[type=bottom,waterlogged=false]', floor_s='stone_bricks', corner='stripped_spruce_log[axis=y]')
            box(x0 + 1, 4, z0 + 1, x1 - 1, 4, z1 - 1, 'spruce_planks')
            opening(x0 + 3, 0, z1, x0 + 3, 1, z1)
            blk(x0 + 1, 1, z0, 'glass_pane[north=false,south=false,east=true,west=true]')
            blk(x1 - 1, 1, z0, 'glass_pane[north=false,south=false,east=true,west=true]')
            blk(x0 + 3, 3, z0 + 3, 'lantern[hanging=true]')
            if n <= 10 and (i + j) % 2 == 0:
                mark('villager_%d' % n, x0 + 3, 0, z1 + 2)
                n += 1


def farms():
    # 畑 (x 40..84, z 12..52)。柵、畝、水路
    x0, z0, x1, z1 = 40, 12, 84, 52
    box(x0, 0, z0, x1, 0, z0, 'spruce_fence')
    box(x0, 0, z1, x1, 0, z1, 'spruce_fence')
    box(x0, 0, z0, x0, 0, z1, 'spruce_fence')
    box(x1, 0, z0, x1, 0, z1, 'spruce_fence')
    blk(x0 + 22, 0, z0, 'spruce_fence_gate[facing=south,in_wall=false,open=false,powered=false]')
    for z in range(z0 + 2, z1 - 1, 5):
        box(x0 + 2, -1, z, x1 - 2, -1, z, 'water')
        box(x0 + 2, -1, z + 1, x1 - 2, -1, z + 3, 'farmland[moisture=7]')
        box(x0 + 2, 0, z + 1, x1 - 2, 0, z + 3, 'wheat[age=5]')


def lots():
    # 空き区画 (32×32): 隅 4 つと南 1 つ。丸石の角柱と碑だけ
    n = 1
    for (x0, z0) in ((-96, -96), (65, -96), (-96, 65), (65, 65), (6, 56)):
        x1, z1 = x0 + 31, z0 + 31
        if z0 == -96 and -60 <= x0 <= 60:
            continue
        for cx, cz in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            box(cx, 0, cz, cx, 1, cz, 'cobblestone_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]')
        tablet(x0 + 15, 0, z1 + 1 if z1 < 90 else z0 - 1, 'south' if z1 < 90 else 'north', 'lot_%d' % n)
        n += 1


def main():
    walls()
    streets()
    terrace()
    bell_tower()
    storyteller_house()
    keep()
    hall()
    plaza_market()
    guild()
    shrine()
    smithy()
    houses()
    farms()
    lots()
    out = {
        'site': 'capital', 'option': 'a', 'palette': palette, 'boxes': boxes, 'blocks': blocks, 'marks': marks,
        'meta': {'rect': [-100, -100, 100, 100], 'level': True, 'ring': 12, 'fillWater': True, 'height': 40},
    }
    os.makedirs(DATA, exist_ok=True)
    path = os.path.join(DATA, 'capital.json')
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(out, f, separators=(',', ':'))
    print('wrote', path, 'boxes', len(boxes), 'blocks', len(blocks), 'marks', sorted(marks))
    preview()


def preview():
    from PIL import Image, ImageDraw
    S = 3
    im = Image.new('RGB', (220 * S, 220 * S), (214, 206, 180))
    d = ImageDraw.Draw(im)
    top: dict[tuple[int, int], tuple[int, str]] = {}
    for (x0, y0, z0, x1, y1, z1, p) in boxes:
        s = palette[p]
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if top.get((x, z), (-99, ''))[0] <= y1:
                    top[(x, z)] = (y1, s)
    for (x, y, z, p) in blocks:
        s = palette[p]
        if top.get((x, z), (-99, ''))[0] <= y:
            top[(x, z)] = (y, s)
    col = {'stone_bricks': (150, 150, 150), 'cobblestone': (125, 125, 125), 'polished_andesite': (170, 170, 170), 'deepslate': (70, 70, 80),
           'spruce': (120, 90, 60), 'grass': (110, 160, 90), 'dirt': (130, 100, 70), 'water': (90, 140, 200), 'wool': (230, 170, 90),
           'farmland': (100, 70, 40), 'wheat': (200, 180, 90), 'lantern': (255, 220, 120), 'air': (214, 206, 180), 'bell': (240, 196, 80),
           'iron': (160, 160, 180), 'tablet': (90, 60, 120), 'glass': (200, 220, 240), 'campfire': (230, 110, 60), 'hay': (220, 190, 90)}
    for (x, z), (y, s) in top.items():
        c = (180, 180, 180)
        for k, v in col.items():
            if k in s:
                c = v
                break
        px, pz = (x + 110) * S, (z + 110) * S
        d.rectangle([px, pz, px + S - 1, pz + S - 1], fill=c)
    for name, (x, y, z) in marks.items():
        px, pz = (x + 110) * S, (z + 110) * S
        d.rectangle([px - 2, pz - 2, px + S + 1, pz + S + 1], outline=(220, 40, 40))
    im.save(PREVIEW)
    print('preview', PREVIEW)


if __name__ == '__main__':
    main()
