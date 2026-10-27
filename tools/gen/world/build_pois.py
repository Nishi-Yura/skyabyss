#!/usr/bin/env python3
"""見どころと外れの道の休み場を、作り込みの基準 (削り出した岩・縦の層・細部の密度) で作り直す (2026-10-28)。

    PYTHONIOENCODING=utf-8 python -I tools/gen/world/build_pois.py [名前…]      (名前を省くと全部)

今のファイルの範囲 (meta.rect)・印 (marks)・site は変えない。印のブロック (宝箱・碑・樽・看板) は今のまま残す。
撮影: tools/shots/poi_audit.txt
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from poi_kit import (AIR, BP, CHAIN_Y, Rng, column, disc, fence, gable_roof, ground, hash3, hip_roof, lantern, log, noise3,  # noqa: E402
                     ring_states, rock, scatter, sl, st, trap, wall)

GRASS = "minecraft:grass_block[snowy=false]"
FLOWERS = ["minecraft:short_grass", "minecraft:short_grass", "minecraft:short_grass", "minecraft:fern", "minecraft:dandelion", "minecraft:poppy",
           "minecraft:cornflower", "minecraft:azure_bluet"]
STONE_LAYERS = [(1, ["minecraft:mossy_cobblestone", "minecraft:cobblestone", "minecraft:andesite"]),
                (6, ["minecraft:stone", "minecraft:andesite", "minecraft:cobblestone", "minecraft:tuff"]),
                (99, ["minecraft:stone", "minecraft:smooth_stone", "minecraft:andesite", "minecraft:diorite"])]
EARTH = [(GRASS, 5), ("minecraft:coarse_dirt", 2), ("minecraft:podzol", 1), ("minecraft:rooted_dirt", 1)]
PATH = [("minecraft:dirt_path", 3), ("minecraft:coarse_dirt", 2), ("minecraft:gravel", 1), (GRASS, 1)]


def path_line(bp, x0, z0, x1, z1, width, seed, mix=PATH):
    n = int(max(abs(x1 - x0), abs(z1 - z0))) + 1
    tot = sum(w for _, w in mix)
    for i in range(n + 1):
        t = i / max(1, n)
        cx, cz = x0 + (x1 - x0) * t, z0 + (z1 - z0) * t
        for dx in range(-width, width + 1):
            for dz in range(-width, width + 1):
                if math.hypot(dx, dz) > width + 0.3:
                    continue
                x, z = int(round(cx + dx)), int(round(cz + dz))
                h = hash3(x, 3, z, seed)
                acc = 0
                for s, w in mix:
                    acc += w / tot
                    if h <= acc:
                        bp.set(x, -1, z, s)
                        break


def lamp_post(bp, x, z, h=3, wood="spruce", soul=False):
    column(bp, x, z, 0, h - 1, fence(wood))
    bp.set(x, h, z, log(f"stripped_{wood}_log"))
    bp.set(x + 1, h, z, fence(wood))
    bp.set(x + 1, h - 1, z, lantern(True, soul))


def crate(bp, x, y, z, seed):
    bp.set(x, y, z, "minecraft:barrel[facing=up,open=false]" if hash3(x, y, z, seed) < 0.5 else "minecraft:spruce_planks")
    if hash3(x, y + 1, z, seed) < 0.4:
        bp.set(x, y + 1, z, trap("spruce", "north", "bottom"))


def bench(bp, x0, z0, x1, z1, facing, wood="spruce"):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            bp.set(x, 0, z, st(wood, facing))


def logpile(bp, x, z, axis, n, wood="spruce", seed=0):
    for k in range(n):
        dx, dz = (k, 0) if axis == "x" else (0, k)
        bp.set(x + dx, 0, z + dz, log(f"{wood}_log", "z" if axis == "x" else "x"))
        if k < n - 1 and hash3(x + dx, 1, z + dz, seed) < 0.7:
            bp.set(x + dx, 1, z + dz, log(f"stripped_{wood}_log", "z" if axis == "x" else "x"))


# ====================================================================== 外れの道
def outskirts_fire():
    bp = BP("outskirts_fire")
    rng = Rng(11)
    ground(bp, 5, [("minecraft:coarse_dirt", 3), ("minecraft:podzol", 2), (GRASS, 2), ("minecraft:gravel", 1)], 11)
    # 火の輪 (石を並べ、真ん中に焚き火)
    for x, z in ring_states(0, 0, 1.6):
        bp.set(x, 0, z, rng.pick([sl("cobblestone"), sl("mossy_cobblestone"), sl("stone_brick")]))
        bp.set(x, -1, z, "minecraft:cobblestone")
    bp.set(0, 0, 0, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
    bp.set(0, -1, 0, "minecraft:magma_block")
    # 丸太の腰かけ 3 つ (北・西・南)
    for x in (-1, 0, 1):
        bp.set(x, 0, -3, log("stripped_spruce_log", "x"))
    for z in (-1, 0, 1):
        bp.set(-3, 0, z, log("stripped_spruce_log", "z"))
    bp.set(-1, 0, 3, log("spruce_log", "x"))
    bp.set(0, 0, 3, log("spruce_log", "x"))
    # 片流れの小屋根 (西の腰かけの上。柱 2 本と斜めの屋根、吊り灯)
    for z in (-2, 2):
        column(bp, -4, z, 0, 3, log("spruce_log"))
        column(bp, -2, z, 0, 2, fence("spruce"))
    for z in range(-3, 4):
        bp.set(-5, 3, z, st("spruce", "east"))
        bp.set(-4, 4 if abs(z) <= 2 else 3, z, sl("spruce") if abs(z) > 2 else "minecraft:spruce_planks")
        bp.set(-3, 3, z, st("spruce", "west", "top") if abs(z) <= 2 else sl("spruce"))
        bp.set(-2, 3, z, sl("spruce"))
    bp.set(-3, 2, 0, CHAIN_Y)
    bp.set(-3, 1, 0, lantern(True))
    # 薪の山と道具
    logpile(bp, 2, -4, "x", 3, seed=11)
    bp.set(4, 0, -3, trap("spruce", "west", "bottom", True))
    bp.set(-4, 0, 4, "minecraft:flower_pot")
    bp.set(-4, 0, -4, "minecraft:composter[level=4]")
    scatter(bp, 5, 0, FLOWERS, 0.18, 11, only_on={GRASS})
    bp.keep_marks()
    return bp


def outskirts_shrine():
    bp = BP("outskirts_shrine")
    ground(bp, 4, [("minecraft:moss_block", 2), (GRASS, 3), ("minecraft:coarse_dirt", 1)], 12)
    # 二段の台 (石の切り石、縁は階段)
    for x in range(-3, 4):
        for z in range(-3, 4):
            edge = max(abs(x), abs(z)) == 3
            bp.set(x, -1, z, "minecraft:stone_bricks" if (x + z) % 3 else "minecraft:mossy_stone_bricks")
            if edge:
                f = "north" if z == -3 else "south" if z == 3 else "west" if x == -3 else "east"
                bp.set(x, 0, z, st("stone_brick", OPP_(f)))
            else:
                bp.set(x, 0, z, "minecraft:polished_andesite" if max(abs(x), abs(z)) == 2 else "minecraft:chiseled_stone_bricks" if (x, z) == (0, 0) else "minecraft:smooth_stone")
    # 四つの柱 (石の壁 + 木の柱頭) と反った屋根
    for x, z in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        column(bp, x, z, 1, 3, wall("stone_brick"))
        bp.set(x, 4, z, log("stripped_dark_oak_log"))
    for x in range(-3, 4):
        for z in range(-3, 4):
            m = max(abs(x), abs(z))
            if m == 3:
                corner = abs(x) == 3 and abs(z) == 3
                bp.set(x, 5 if corner else 4, z, st("dark_oak", "north" if z == -3 else "south" if z == 3 else "west" if x == -3 else "east", "top" if corner else "bottom"))
            elif m == 2:
                bp.set(x, 5, z, sl("dark_oak") if (abs(x) == 2 and abs(z) == 2) else "minecraft:dark_oak_planks")
            elif m == 1:
                bp.set(x, 6, z, st("dark_oak", "north" if z == -1 else "south" if z == 1 else "west" if x == -1 else "east"))
            else:
                bp.set(x, 6, z, "minecraft:dark_oak_planks")
                bp.set(x, 7, z, sl("dark_oak"))
    for x, z in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        bp.set(x, 4, z, lantern(True))
    bp.set(0, 5, 0, CHAIN_Y)
    bp.set(0, 4, 0, lantern(True, True))
    # 祭壇 (回復の印 heal は (0, 1, 0): 台の上は空ける) のまわりに花と蝋燭
    for x, z in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        bp.set(x, 1, z, "minecraft:candle[candles=3,lit=true,waterlogged=false]")
    for x, z in ((-2, 0), (2, 0), (0, -2)):
        bp.set(x, 1, z, "minecraft:flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]")
    bp.set(0, 1, 0, AIR)
    bp.set(0, 2, 0, AIR)
    bp.keep_marks()
    return bp


def OPP_(f):
    return {"north": "south", "south": "north", "east": "west", "west": "east"}[f]


def outskirts_dock():
    bp = BP("outskirts_dock")
    ground(bp, 6, [("minecraft:sand", 3), ("minecraft:gravel", 2), ("minecraft:coarse_dirt", 1), (GRASS, 1)], 13)
    # 板の桟橋 (東へ伸びる。下に杭)
    for x in range(-3, 7):
        for z in range(-2, 3):
            bp.set(x, -1, z, "minecraft:spruce_planks" if (x + z) % 4 else "minecraft:stripped_spruce_wood[axis=x]")
    for x in (-3, 0, 3, 6):
        for z in (-2, 2):
            column(bp, x, z, -1, 1, log("spruce_log"))
            bp.set(x, 2, z, sl("spruce"))
    for x in range(-2, 7):
        if x not in (0, 3, 6):
            bp.set(x, 0, -2, fence("spruce"))
    # 係留の柱と綱 (鎖)
    for z in (-1, 1):
        bp.set(6, 0, z * 2, log("dark_oak_log"))
    bp.set(6, 1, -2, "minecraft:chain[axis=z,waterlogged=false]")
    # 渡し守の小屋 (西。屋根つきの受付)
    for x in (-5, -3):
        for z in (-4, -1):
            column(bp, x, z, 0, 2, log("spruce_log"))
    for x in range(-6, -1):
        for z in range(-5, 1):
            bp.set(x, 3, z, st("spruce", "south") if z == -5 else st("spruce", "north") if z == 0 else "minecraft:spruce_planks")
        bp.set(x, 4, -3, sl("spruce"))
        bp.set(x, 4, -2, sl("spruce"))
    bp.set(-4, 0, -4, "minecraft:spruce_planks")
    bp.set(-4, 1, -4, trap("spruce", "south", "top"))
    bp.set(-5, 0, -4, "minecraft:barrel[facing=up,open=false]")
    bp.set(-4, 2, -2, lantern(True))
    # 荷 (樽・箱・網)
    for x, z in ((1, -4), (2, -4), (1, -5), (4, 4), (5, 4)):
        crate(bp, x, 0, z, 13)
    bp.set(3, 0, 4, "minecraft:cobweb")
    lamp_post(bp, 5, -3, 3)
    bp.keep_marks()
    return bp


# ====================================================================== 野営
def tent(bp, cx, cz, along, length, color, seed):
    """A 字の天幕: 両端に木の枠、上は羊毛の斜め (階段がないので羊毛と絨毯で形を作る)"""
    for i in range(length):
        x, z = (cx + i, cz) if along == "x" else (cx, cz + i)
        if along == "x":
            bp.set(x, 0, z - 2, f"minecraft:{color}_carpet")
            bp.set(x, 0, z + 2, f"minecraft:{color}_carpet")
            bp.set(x, 1, z - 1, f"minecraft:{color}_wool")
            bp.set(x, 1, z + 1, f"minecraft:{color}_wool")
            bp.set(x, 2, z, f"minecraft:{color}_wool")
            bp.set(x, 3, z, f"minecraft:{color}_carpet")
            bp.set(x, 0, z, AIR if i not in (0, length - 1) else AIR)
        else:
            bp.set(x - 2, 0, z, f"minecraft:{color}_carpet")
            bp.set(x + 2, 0, z, f"minecraft:{color}_carpet")
            bp.set(x - 1, 1, z, f"minecraft:{color}_wool")
            bp.set(x + 1, 1, z, f"minecraft:{color}_wool")
            bp.set(x, 2, z, f"minecraft:{color}_wool")
            bp.set(x, 3, z, f"minecraft:{color}_carpet")
    # 両端の柱と棟の木
    for e in (0, length - 1):
        x, z = (cx + e, cz) if along == "x" else (cx, cz + e)
        bp.set(x, 0, z, fence("spruce") if e == 0 else AIR)
        bp.set(x, 1, z, fence("spruce") if e == 0 else AIR)
    xe, ze = (cx + length, cz) if along == "x" else (cx, cz + length)
    column(bp, xe, ze, 0, 2, fence("spruce"))
    xs, zs = (cx - 1, cz) if along == "x" else (cx, cz - 1)
    column(bp, xs, zs, 0, 2, fence("spruce"))
    # 寝床
    x, z = (cx + 1, cz) if along == "x" else (cx, cz + 1)
    bp.set(x, 0, z, "minecraft:brown_carpet")


def bandit_camp():
    bp = BP("poi_bandit_camp")
    rng = Rng(21)
    ground(bp, 10, [("minecraft:coarse_dirt", 4), ("minecraft:podzol", 2), ("minecraft:mud", 1), (GRASS, 2), ("minecraft:gravel", 1)], 21)
    # 尖った丸太の柵 (北半分の弧。入口は南)
    for x, z in ring_states(0, 0, 8.5):
        if z > 3:
            continue
        h = 2 + int(hash3(x, 0, z, 21) * 2)
        column(bp, x, z, 0, h - 1, log("spruce_log"))
        bp.set(x, h, z, fence("spruce"))
    # 物見の台 (北西)
    for x, z in ((-6, -6), (-4, -6), (-6, -4), (-4, -4)):
        column(bp, x, z, 0, 3, log("dark_oak_log"))
    for x in range(-7, -2):
        for z in range(-7, -2):
            bp.set(x, 4, z, "minecraft:spruce_planks" if abs(x + 5) < 2 and abs(z + 5) < 2 else sl("spruce", "top"))
            if max(abs(x + 5), abs(z + 5)) == 2:
                bp.set(x, 5, z, fence("spruce"))
    for y in range(0, 4):
        bp.set(-3, y, -5, "minecraft:ladder[facing=east,waterlogged=false]")
    bp.set(-5, 6, -5, "minecraft:red_banner[rotation=0]")
    # 天幕 3 張り (くすんだ色)
    tent(bp, 2, -6, "x", 4, "brown", 21)
    tent(bp, -7, 1, "z", 3, "gray", 22)
    tent(bp, 5, 2, "z", 3, "brown", 23)
    # 焚き火の穴 (真ん中は敵の湧き foes (0, 0, 0) なので空ける。火は少し北)
    for x, z in ring_states(0, -2, 1.2):
        bp.set(x, 0, z, sl("cobblestone"))
    bp.set(0, 0, -2, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
    column(bp, -1, -2, 1, 2, fence("spruce"))
    column(bp, 1, -2, 1, 2, fence("spruce"))
    bp.set(0, 2, -2, log("stripped_spruce_log", "x"))
    # 武器の棚・獲物・荷
    for x in (-3, -2):
        bp.set(x, 0, 4, "minecraft:spruce_planks")
        bp.set(x, 1, 4, trap("spruce", "south", "top"))
    bp.set(3, 0, -1, "minecraft:barrel[facing=up,open=false]")
    bp.set(5, 0, -1, "minecraft:barrel[facing=up,open=false]")
    bp.set(5, 1, -1, "minecraft:barrel[facing=up,open=false]")
    bp.set(-2, 0, -4, "minecraft:hay_block[axis=y]")
    bp.set(2, 0, 6, "minecraft:skeleton_skull[rotation=6]")
    bp.set(-4, 0, 6, "minecraft:cobweb")
    lamp_post(bp, -1, 7, 3, "spruce")
    scatter(bp, 10, 0, ["minecraft:dead_bush", "minecraft:short_grass"], 0.08, 21)
    bp.set(0, 0, 0, AIR)
    bp.keep_marks()
    return bp


def wagon(bp, x0, z0, seed):
    """幌馬車 (東西に長い。荷台は y = 1、幌は肋の木と白い羊毛)"""
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 3):
            bp.set(x, 1, z, sl("spruce", "top") if x in (x0, x0 + 4) else "minecraft:spruce_planks")
    # 車輪 (外の面に暗い木の扉の形)
    for x in (x0, x0 + 4):
        for z, f in ((z0 - 1, "north"), (z0 + 3, "south")):
            bp.set(x, 0, z, trap("dark_oak", f, "bottom", True))
            bp.set(x, 1, z, trap("dark_oak", f, "top", True))
    # 幌: 肋 3 本と布
    for x in range(x0, x0 + 5):
        rib = x in (x0, x0 + 2, x0 + 4)
        bp.set(x, 2, z0 - 1 + 1, "minecraft:white_wool" if not rib else log("stripped_spruce_log", "x"))
        bp.set(x, 2, z0 + 2, "minecraft:white_wool" if not rib else log("stripped_spruce_log", "x"))
        bp.set(x, 3, z0, "minecraft:white_wool" if not rib else log("stripped_spruce_log", "x"))
        bp.set(x, 3, z0 + 2, "minecraft:white_wool" if not rib else log("stripped_spruce_log", "x"))
        bp.set(x, 4, z0 + 1, "minecraft:white_wool" if not rib else "minecraft:spruce_planks")
        bp.set(x, 2, z0 + 1, AIR)
        bp.set(x, 3, z0 + 1, AIR)
    # 轅 (前の棒) と荷
    bp.set(x0 + 5, 1, z0 + 1, fence("spruce"))
    bp.set(x0 + 6, 1, z0 + 1, fence("spruce"))
    bp.set(x0 + 1, 2, z0 + 1, "minecraft:barrel[facing=up,open=false]")


def traveler_camp():
    bp = BP("poi_traveler_camp")
    ground(bp, 9, [(GRASS, 6), ("minecraft:coarse_dirt", 2), ("minecraft:dirt_path", 1)], 31)
    path_line(bp, 0, 0, 3, 8, 1, 31)
    # 宝の印 loot (-4, 2, -3) は荷台の上: 荷台 y=1 の西の端を -4 に
    wagon(bp, -5, -4, 31)
    # 焚き火と腰かけ、鍋
    for x, z in ring_states(2, 1, 1.2):
        bp.set(x, 0, z, sl("cobblestone"))
    bp.set(2, 0, 1, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
    column(bp, 1, 1, 1, 2, fence("spruce"))
    column(bp, 3, 1, 1, 2, fence("spruce"))
    bp.set(2, 2, 1, log("stripped_spruce_log", "x"))
    bp.set(-1, 0, 3, log("spruce_log", "x"))
    bp.set(0, 0, 3, log("spruce_log", "x"))
    bp.set(5, 0, 0, log("spruce_log", "z"))
    bp.set(5, 0, 1, log("spruce_log", "z"))
    # 寝床 (絨毯) と小さな天幕
    tent(bp, 5, -5, "x", 3, "green", 32)
    for x, z in ((-4, 3), (-3, 3)):
        bp.set(x, 0, z, "minecraft:green_carpet")
    # 馬繋ぎの柵と干し草、灯り
    for z in range(4, 8):
        bp.set(-6, 0, z, fence("spruce"))
    bp.set(-5, 0, 6, "minecraft:hay_block[axis=y]")
    bp.set(-5, 0, 7, "minecraft:hay_block[axis=x]")
    bp.set(-4, 0, 7, "minecraft:cauldron")
    lamp_post(bp, 6, 4, 3)
    for x, z in ((0, -6), (1, -6)):
        crate(bp, x, 0, z, 33)
    scatter(bp, 9, 0, FLOWERS, 0.12, 31, only_on={GRASS})
    bp.keep_marks()
    return bp


# ====================================================================== 石・骨
BONE = ["minecraft:bone_block[axis=y]", "minecraft:bone_block[axis=y]", "minecraft:calcite", "minecraft:diorite"]


def titan_ribs():
    bp = BP("poi_titan_ribs")
    ground(bp, 13, [(GRASS, 5), ("minecraft:coarse_dirt", 2), ("minecraft:rooted_dirt", 1), ("minecraft:moss_block", 1)], 41)
    # 背骨 (東西。半ば埋もれた椎骨の並び)
    for i, x in enumerate(range(-11, 12, 3)):
        r = 1.5 - abs(x) / 20
        for dx in range(-1, 2):
            for y in range(-1, 2):
                for dz in range(-2, 3):
                    if (dx / 1.2) ** 2 + (y / 1.5) ** 2 + (dz / 2.4) ** 2 <= 1 + r * 0.1:
                        bp.set(x + dx, y, dz, BONE[int(hash3(x + dx, y, dz, 41) * 4) % 4])
        bp.set(x, 2, 0, "minecraft:bone_block[axis=y]")
    # 肋: 背骨から外へ出て弓なりに立ち上がり、先が内へ曲がる U 字 (根元は二重の太さ、先は細い壁)。半ばで折れたものもある
    for k, x in enumerate(range(-9, 10, 3)):
        R = 7.5 - abs(x) * 0.2
        broken = {1: 0.55, 4: 0.7}.get(k, 1.0)
        for side in (-1, 1):
            th = 0.0
            while th <= math.radians(112) * broken:
                zz = side * (1.5 + R * 0.72 * math.sin(th))
                yy = R * 1.5 * (1 - math.cos(th)) - 0.4
                xx = x + (0.8 if side > 0 else -0.8) * (th / 2.0)
                bx, by, bz = int(round(xx)), int(round(yy)), int(round(zz))
                if th < math.radians(70):
                    for ox in (0, 1):
                        bp.set(bx + ox, by, bz, BONE[int(hash3(bx + ox, by, bz, 41) * 4) % 4])
                else:
                    bp.set(bx, by, bz, BONE[int(hash3(bx, by, bz, 41) * 4) % 4] if th < math.radians(95) else wall("diorite"))
                th += 0.03
            if broken < 1:
                # 折れて落ちた先 (根元のそば)
                for t in range(3):
                    bp.set(x + t, 0, int(round(side * (R + 2))), "minecraft:bone_block[axis=x]")
        for side in (-1, 1):
            for dz in range(1, 3):
                bp.set(x, 0, side * dz, rock_dirt(x, side * dz))
    # 頭蓋の欠片 (東。大きな割れた塊) と牙
    rock(bp, 12, 1, 0, 2.2, 2.0, 2.6, [(99, BONE)], 44, 0.4)
    for z in (-2, 2):
        column(bp, 11, z, 0, 1, wall("diorite"))
    bp.clear(13, 0, -1, 13, 2, 1)
    # 苔と草が骨に這う
    for (x, y, z), s in list(bp.cells.items()):
        if s.startswith("minecraft:bone") or s.endswith("calcite"):
            if y >= 0 and hash3(x, y, z, 45) < 0.12 and bp.get(x, y + 1, z) in (None, AIR):
                bp.set(x, y + 1, z, "minecraft:moss_carpet")
    scatter(bp, 13, 0, FLOWERS + ["minecraft:fern"], 0.1, 41, only_on={GRASS})
    bp.clear(0, 0, 3, 0, 1, 3)
    bp.clear(0, 0, 10, 0, 1, 10)
    bp.keep_marks()
    return bp


def rock_dirt(x, z):
    return "minecraft:rooted_dirt" if hash3(x, 0, z, 46) < 0.5 else "minecraft:coarse_dirt"


def standing_stone(bp, x, z, h, seed, mats, lean=(0, 0), width=1):
    """立石: 下が太く、上が細くなる。頭は階段で欠けた形"""
    for y in range(0, h):
        w = width if y < h - 2 else 0
        ox = int(round(lean[0] * y / h))
        oz = int(round(lean[1] * y / h))
        for dx in range(-w, w + 1):
            for dz in range(0, 2 if w else 1):
                if w and abs(dx) == w and hash3(x + dx, y, z + dz, seed) < 0.45 + y / h * 0.4:
                    continue
                bp.set(x + dx + ox, y, z + dz + oz, mats[int(hash3(x + dx, y, z + dz, seed) * len(mats)) % len(mats)])
    ox = int(round(lean[0]))
    oz = int(round(lean[1]))
    bp.set(x + ox, h, z + oz, st("cobblestone", "north") if hash3(x, h, z, seed) < 0.5 else wall("mossy_cobblestone"))


def stone_circle():
    bp = BP("poi_stone_circle")
    ground(bp, 11, [(GRASS, 6), ("minecraft:moss_block", 1), ("minecraft:coarse_dirt", 1)], 51)
    # 輪の中の踏み固めた道 (内の円) と外の溝
    for x, z in ring_states(0, 0, 6.5):
        bp.set(x, -1, z, "minecraft:dirt_path" if hash3(x, 0, z, 51) < 0.7 else "minecraft:coarse_dirt")
    mats = ["minecraft:stone", "minecraft:andesite", "minecraft:tuff", "minecraft:cobblestone", "minecraft:mossy_cobblestone"]
    n = 9
    for k in range(n):
        a = k / n * math.pi * 2 + 0.2
        x, z = int(round(math.cos(a) * 8)), int(round(math.sin(a) * 8))
        if (x, z) in ((0, 3),):
            continue
        h = 4 + int(hash3(k, 0, 0, 52) * 3)
        fallen = k == 5
        if fallen:
            # 倒れた石 (横たわる)
            for t in range(5):
                bp.set(x + t - 2, 0, z, mats[t % len(mats)])
                bp.set(x + t - 2, 1, z, mats[(t + 2) % len(mats)] if t < 4 else sl("stone"))
            continue
        standing_stone(bp, x, z, h, 52 + k, mats, (hash3(k, 1, 0, 53) - 0.5, hash3(k, 2, 0, 53) - 0.5))
    # まぐさ石を渡した二組 (三石門)
    for k0 in (0, 3):
        a0, a1 = k0 / n * math.pi * 2 + 0.2, (k0 + 1) / n * math.pi * 2 + 0.2
        x0, z0 = int(round(math.cos(a0) * 8)), int(round(math.sin(a0) * 8))
        x1, z1 = int(round(math.cos(a1) * 8)), int(round(math.sin(a1) * 8))
        steps = max(abs(x1 - x0), abs(z1 - z0))
        for t in range(steps + 1):
            x = int(round(x0 + (x1 - x0) * t / steps))
            z = int(round(z0 + (z1 - z0) * t / steps))
            bp.set(x, 6, z, "minecraft:stone_bricks" if t % 2 else "minecraft:cracked_stone_bricks")
    # 真ん中の祭壇 (碑の印 (0, 0, 3) は手前に空ける)
    for x in range(-1, 2):
        for z in range(-1, 2):
            bp.set(x, 0, z, st("stone_brick", "north" if z == -1 else "south" if z == 1 else "west" if x == -1 else "east") if (x, z) != (0, 0) else "minecraft:chiseled_stone_bricks")
    bp.set(0, 1, 0, "minecraft:lodestone")
    bp.set(-1, 1, -1, "minecraft:candle[candles=2,lit=true,waterlogged=false]")
    bp.set(1, 1, 1, "minecraft:candle[candles=1,lit=true,waterlogged=false]")
    scatter(bp, 11, 0, FLOWERS + ["minecraft:moss_carpet"], 0.1, 51, only_on={GRASS, "minecraft:moss_block"})
    bp.keep_marks()
    return bp


def gate_towers(bp, mats_layers, seed, span=4, h=12, w=2, arch_mat=None):
    """二つの削り出しの塔と、上を渡る崩れかけのまぐさ"""
    for side in (-1, 1):
        cx = side * (span + w)
        rock(bp, cx, h / 2 - 1, 0, w + 0.8, h / 2 + 0.5, w + 0.6, mats_layers, seed + side, 0.28)
        rock(bp, cx + side * 1.5, 0.5, 0.5, w + 1.5, 1.8, w + 1.8, mats_layers, seed + 5 + side, 0.4)
    for x in range(-span - w, span + w + 1):
        dip = 1 if abs(x) < span - 1 else 0
        for y in (h - 1 - dip, h - dip):
            if hash3(x, y, 0, seed) < 0.12 and abs(x) < span - 1:
                continue
            for z in (-1, 0, 1):
                bp.set(x, y, z, arch_mat or mats_layers[-1][1][0])


def wind_gate():
    bp = BP("poi_wind_gate")
    ground(bp, 12, [("minecraft:stone", 2), ("minecraft:gravel", 2), ("minecraft:andesite", 1), (GRASS, 2)], 61)
    layers = [(2, ["minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:andesite"]), (7, ["minecraft:stone", "minecraft:andesite", "minecraft:calcite"]),
              (99, ["minecraft:calcite", "minecraft:diorite", "minecraft:stone"])]
    gate_towers(bp, layers, 61, span=4, h=12, w=2, arch_mat="minecraft:polished_diorite")
    # 風が穿った穴 (塔を貫く)
    for side in (-1, 1):
        bp.clear(side * 6 - 1, 5, 0, side * 6 + 1, 6, 0)
        bp.clear(side * 6, 8, -1, side * 6, 8, 1)
    # 祈りの旗 (門の下に綱と旗) と風見の杭
    for x in range(-3, 4):
        bp.set(x, 9, 0, "minecraft:chain[axis=x,waterlogged=false]")
        if x % 2 == 0:
            bp.set(x, 8, 1, "minecraft:white_wall_banner[facing=south]" if x % 4 == 0 else "minecraft:cyan_wall_banner[facing=south]")
    for x, z in ((-9, 6), (9, -6)):
        column(bp, x, z, 0, 4, fence("spruce"))
        bp.set(x, 5, z, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
    # 吹き寄せられた石と草 (門の下は通れるように空ける)
    for k in range(6):
        a = k * 1.1
        rock(bp, math.cos(a) * 9, -0.2, math.sin(a) * 9, 1.2, 0.9, 1.0, layers, 62 + k, 0.5)
    bp.clear(-3, 0, -2, 3, 8, 2)
    bp.set(0, -1, 2, "minecraft:polished_andesite")
    scatter(bp, 12, 0, ["minecraft:short_grass", "minecraft:fern", "minecraft:dead_bush"], 0.08, 61, only_on={GRASS})
    bp.keep_marks()
    return bp


def basalt_gate():
    bp = BP("poi_basalt_gate")
    ground(bp, 9, [("minecraft:blackstone", 2), ("minecraft:basalt[axis=y]", 2), ("minecraft:coarse_dirt", 2), ("minecraft:magma_block", 0.2), ("minecraft:gravel", 1)], 71)
    # 玄武岩の柱の束 (高さの違う六角の柱の集まり) が二つ、その上に崩れたまぐさ
    for side in (-1, 1):
        for k in range(9):
            a = k / 9 * math.pi * 2
            x = side * 5 + int(round(math.cos(a) * 1.6 * (k % 3 != 0)))
            z = int(round(math.sin(a) * 1.6 * (k % 3 != 0)))
            h = 9 + int(hash3(k, side, 0, 71) * 4) - (2 if k % 3 == 0 else 0)
            column(bp, x, z, 0, h, "minecraft:basalt[axis=y]" if hash3(x, 1, z, 71) < 0.6 else "minecraft:polished_basalt[axis=y]")
            bp.set(x, h + 1, z, sl("blackstone") if k % 2 else sl("polished_blackstone"))
        # 足もとの割れた柱
        for k in range(4):
            x, z = side * (7 + k % 2), -2 + k
            column(bp, x, z, 0, 1 + k % 3, "minecraft:basalt[axis=y]")
    for x in range(-5, 6):
        y = 11 if abs(x) < 4 else 12
        if hash3(x, y, 0, 72) < 0.15 and abs(x) < 3:
            continue
        for z in (-1, 0, 1):
            bp.set(x, y, z, "minecraft:polished_blackstone_bricks" if (x + z) % 3 else "minecraft:cracked_polished_blackstone_bricks")
        bp.set(x, y + 1, 0, sl("polished_blackstone_brick"))
    # まぐさの真ん中の燠 (光る割れ目)
    bp.set(0, 11, 0, "minecraft:magma_block")
    bp.set(0, 10, 0, "minecraft:chain[axis=y,waterlogged=false]")
    bp.set(0, 9, 0, lantern(True, True))
    # 床の割れ目に熔岩の光
    for x, z in ((-2, 1), (1, -2), (3, 2)):
        bp.set(x, -1, z, "minecraft:magma_block")
    bp.keep_marks()
    return bp


# ====================================================================== 塔
def round_tower(bp, cx, cz, r0, r1, y0, y1, mat_fn, hollow=True):
    for y in range(y0, y1 + 1):
        r = r0 + (r1 - r0) * (y - y0) / max(1, y1 - y0)
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                d = math.hypot(x - cx, z - cz)
                if d <= r + 0.3 and (not hollow or d >= r - 0.9):
                    bp.set(x, y, z, mat_fn(x, y, z))
                elif hollow and d < r - 0.9:
                    bp.set(x, y, z, AIR)


def lighthouse():
    bp = BP("poi_lighthouse")
    ground(bp, 7, [("minecraft:sand", 2), ("minecraft:gravel", 2), ("minecraft:stone", 1), (GRASS, 1)], 81)
    # 岩の台 (削り出し)
    rock(bp, 0, -0.5, 0, 5.5, 1.6, 5.5, [(0, ["minecraft:stone", "minecraft:andesite", "minecraft:cobblestone", "minecraft:mossy_cobblestone"])], 81, 0.3)

    def band(x, y, z):
        if y < 3:
            return "minecraft:stone_bricks" if (x + y + z) % 4 else "minecraft:mossy_stone_bricks"
        return ("minecraft:white_concrete" if hash3(x, y, z, 82) < 0.8 else "minecraft:smooth_quartz") if ((y - 3) // 4) % 2 == 0 else ("minecraft:red_nether_bricks" if hash3(x, y, z, 83) < 0.3 else "minecraft:red_concrete")
    round_tower(bp, 0, 0, 3.6, 2.4, 1, 17, band)
    # 窓 (二つの高さ) と入口
    for y in (7, 12):
        bp.set(2 if y == 7 else -2, y, 2 if y == 7 else -2, "minecraft:glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]")
    bp.clear(0, 1, 3, 0, 2, 4)
    bp.set(0, 1, 4, "minecraft:spruce_door[facing=south,half=lower,hinge=left,open=false,powered=false]")
    bp.set(0, 2, 4, "minecraft:spruce_door[facing=south,half=upper,hinge=left,open=false,powered=false]")
    bp.set(0, 3, 4, st("stone_brick", "north", "top"))
    # 回廊 (張り出しの床と手すり)
    disc(bp, 0, 0, 3.9, 18, "minecraft:polished_andesite")
    for x, z in ring_states(0, 0, 3.9):
        bp.set(x, 19, z, "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]")
    for x, z in ring_states(0, 0, 3.2):
        bp.set(x, 17, z, st("stone_brick", "north", "top") if z < 0 else st("stone_brick", "south", "top"))
    # 灯室 (硝子と光)
    for y in range(19, 22):
        for x, z in ring_states(0, 0, 1.9):
            bp.set(x, y, z, "minecraft:light_blue_stained_glass" if y < 21 else "minecraft:cut_copper")
    column(bp, 0, 0, 19, 20, "minecraft:sea_lantern")
    # 屋根 (銅の円錐) と風見
    disc(bp, 0, 0, 2.4, 22, sl("cut_copper"))
    disc(bp, 0, 0, 1.4, 23, "minecraft:cut_copper")
    bp.set(0, 24, 0, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
    # 番人の小屋 (北東。板と石)
    for x in range(3, 7):
        for z in range(-6, -2):
            edge = x in (3, 6) or z in (-6, -3)
            for y in range(0, 3):
                bp.set(x, y, z, ("minecraft:spruce_planks" if y else "minecraft:cobblestone") if edge else AIR)
    bp.set(5, 1, -3, "minecraft:glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]")
    gable_roof(bp, 3, -6, 6, -3, 3, "spruce", along="x")
    column(bp, 4, -5, 3, 5, "minecraft:bricks")
    # 浜の小物
    bp.set(-4, 1, 3, "minecraft:barrel[facing=up,open=false]")
    bp.set(-5, 1, 2, "minecraft:chain[axis=z,waterlogged=false]")
    bp.keep_marks()
    return bp


def rod_tower():
    bp = BP("poi_rod_tower")
    ground(bp, 6, [("minecraft:stone", 2), ("minecraft:gravel", 2), ("minecraft:cobblestone", 1)], 91)

    def mat(x, y, z):
        if y % 5 == 4:
            return "minecraft:weathered_cut_copper" if hash3(x, y, z, 91) < 0.6 else "minecraft:oxidized_cut_copper"
        return ["minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks", "minecraft:deepslate_tiles", "minecraft:polished_deepslate"][int(hash3(x, y, z, 92) * 4) % 4]
    round_tower(bp, 0, 0, 3.4, 1.8, 0, 17, mat)
    # 控え壁 (四方)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for k in range(4):
            for y in range(0, 6 - k * 2 + 1):
                bp.set(dx * (4 + k), y, dz * (4 + k), "minecraft:deepslate_bricks" if y < 6 - k * 2 else st("deepslate_brick", OPP_({(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[(dx, dz)])))
    # 頂: 銅の帯の冠と避雷の針、鎖で吊った光
    for x, z in ring_states(0, 0, 2.2):
        bp.set(x, 18, z, "minecraft:oxidized_copper")
        if (x + z) % 2 == 0:
            bp.set(x, 19, z, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
    disc(bp, 0, 0, 1.2, 18, "minecraft:oxidized_copper")
    column(bp, 0, 0, 19, 21, "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]")
    bp.set(0, 22, 0, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
    # 焦げた跡 (雷が落ちた所) と散らばった石
    for x, z in ((3, 3), (-3, 4), (4, -3)):
        bp.set(x, -1, z, "minecraft:blackstone")
    for x, z in ((-5, 2), (5, 4), (2, -5)):
        bp.set(x, 0, z, sl("cobbled_deepslate"))
    bp.clear(0, 0, 3, 0, 1, 4)
    bp.keep_marks()
    return bp


def watchtower():
    bp = BP("poi_watchtower")
    ground(bp, 6, [(GRASS, 4), ("minecraft:coarse_dirt", 2), ("minecraft:gravel", 1)], 101)
    # 石の基部 (3 段、外へ広がる)
    for y in range(0, 4):
        r = 3 - (1 if y >= 2 else 0)
        for x in range(-r, r + 1):
            for z in range(-r, r + 1):
                edge = max(abs(x), abs(z)) == r
                bp.set(x, y, z, ("minecraft:stone_bricks" if (x * 3 + y + z) % 5 else "minecraft:mossy_stone_bricks") if edge else AIR)
    bp.clear(0, 0, 3, 0, 1, 3)
    # 木組みの塔 (四本の柱と筋交い)
    for x, z in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        column(bp, x, z, 4, 12, log("spruce_log"))
    for y in (6, 9):
        for x in range(-2, 3):
            for z in (-2, 2):
                if abs(x) < 2:
                    bp.set(x, y, z, log("stripped_spruce_log", "x"))
        for z in range(-1, 2):
            for x in (-2, 2):
                bp.set(x, y, z, log("stripped_spruce_log", "z"))
    for k in range(3):
        for x, z, f in ((-1 + k, -2, "east"), (1 - k, 2, "west")):
            bp.set(x, 4 + k, z, st("spruce", f, "top" if k == 2 else "bottom"))
    # 見張りの床 (張り出し) と胸壁、屋根
    for x in range(-3, 4):
        for z in range(-3, 4):
            bp.set(x, 12, z, "minecraft:spruce_planks")
            if max(abs(x), abs(z)) == 3:
                bp.set(x, 13, z, fence("spruce") if (x + z) % 2 else log("spruce_log"))
            bp.set(x, 11, z, trap("spruce", "north", "top") if max(abs(x), abs(z)) == 3 else bp.get(x, 11, z) or AIR)
    for x, z in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        column(bp, x, z, 13, 15, log("spruce_log"))
    hip_roof(bp, -4, -4, 4, 4, 16, "dark_oak", top=log("spruce_log"))
    bp.set(0, 15, 0, CHAIN_Y)
    bp.set(0, 14, 0, lantern(True))
    # 梯子 (北の内側)
    for y in range(0, 12):
        bp.set(0, y, -1, "minecraft:ladder[facing=south,waterlogged=false]")
    bp.set(0, 12, -1, AIR)
    # 旗と松明
    bp.set(3, 16, 0, "minecraft:red_banner[rotation=4]")
    for x, z in ((3, 0), (-3, 0)):
        bp.set(x, 4, z, "minecraft:wall_torch[facing=%s]" % ("east" if x > 0 else "west"))
    scatter(bp, 6, 0, FLOWERS, 0.1, 101, only_on={GRASS})
    bp.keep_marks()
    return bp


def windmill():
    bp = BP("poi_windmill")
    ground(bp, 8, [(GRASS, 4), ("minecraft:coarse_dirt", 2), ("minecraft:dirt_path", 1)], 111)
    # 八角の石の基部 → 木の胴 (上へ細る) → 帽子の屋根
    def oct_ring(r, y, mat_fn, fill_air=True):
        for x in range(-r - 1, r + 2):
            for z in range(-r - 1, r + 2):
                d = max(abs(x), abs(z), (abs(x) + abs(z)) * 0.72)
                if d <= r + 0.3:
                    edge = d > r - 0.75
                    if edge:
                        bp.set(x, y, z, mat_fn(x, y, z))
                    elif fill_air:
                        bp.set(x, y, z, AIR)
    for y in range(0, 5):
        oct_ring(4, y, lambda x, y, z: "minecraft:stone_bricks" if (x + y + z) % 4 else "minecraft:mossy_cobblestone")
    for y in range(5, 13):
        r = 4 if y < 8 else 3
        oct_ring(r, y, lambda x, y, z: log("stripped_spruce_log") if (abs(x) == abs(z) or x == 0 or z == 0) and y % 4 == 1 else
                 ("minecraft:birch_planks" if y < 10 else "minecraft:smooth_quartz") if y % 4 else log("spruce_log", "x"))
    oct_ring(5, 5, lambda x, y, z: sl("spruce"), False)
    for y in range(13, 17):
        oct_ring(16 - y, y, lambda x, y, z: "minecraft:dark_oak_planks" if y < 16 else sl("dark_oak"), False)
    # 入口・窓
    bp.clear(0, 0, 4, 0, 1, 4)
    bp.set(0, 0, 4, "minecraft:spruce_door[facing=south,half=lower,hinge=left,open=false,powered=false]")
    bp.set(0, 1, 4, "minecraft:spruce_door[facing=south,half=upper,hinge=left,open=false,powered=false]")
    for x, z in ((4, 0), (-4, 0), (0, -4)):
        bp.set(x, 7, z, "minecraft:glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]")
    # 羽 (南の面。軸から四方へ、格子の枠と布)
    hub = (0, 11, 5)
    bp.set(0, 11, 4, log("dark_oak_log", "z"))
    bp.set(*hub, log("dark_oak_log", "z"))
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for t in range(1, 9):
            x, y = dx * t, 11 + dy * t
            if y < 1:
                continue
            bp.set(x, y, 5, fence("spruce") if t % 3 else log("stripped_spruce_log", "x" if dx else "y"))
            px, py = (-dy, dx)
            for w in (1, 2):
                xx, yy = x + px * w, y + py * w
                if yy >= 1 and t > 1:
                    bp.set(xx, yy, 5, "minecraft:white_wool" if (t + w) % 4 else trap("spruce", "south", "bottom", True))
    # 粉の袋 (干し草)・荷車・柵
    bp.set(5, 0, 2, "minecraft:hay_block[axis=y]")
    bp.set(5, 1, 2, "minecraft:hay_block[axis=x]")
    bp.set(6, 0, 1, "minecraft:hay_block[axis=z]")
    bp.set(-6, 0, 3, "minecraft:barrel[facing=up,open=false]")
    for x in range(-7, -3):
        bp.set(x, 0, 6, fence("spruce"))
    scatter(bp, 8, 0, FLOWERS, 0.1, 111, only_on={GRASS})
    bp.keep_marks()
    return bp


# ====================================================================== 塚・崩れた家・祠・坑道
def barrow():
    bp = BP("poi_barrow")
    ground(bp, 13, EARTH, 121)
    # 塚: 草で覆われた丸い盛り土 (ノイズでうねる)
    for x in range(-11, 12):
        for z in range(-11, 12):
            d = math.hypot(x / 10.5, z / 9.5)
            if d > 1:
                continue
            h = int((1 - d * d) * 6.5 + (noise3(x, 0, z, 4, 121) - 0.5) * 2)
            for y in range(0, h + 1):
                top = y == h
                bp.set(x, y, z, GRASS if top else ("minecraft:dirt" if y > h - 2 else "minecraft:stone" if hash3(x, y, z, 122) < 0.6 else "minecraft:cobblestone"))
    # 縁石の輪 (立った石と倒れた石)
    for x, z in ring_states(0, 0, 12):
        if hash3(x, 0, z, 123) < 0.35:
            column(bp, x, z, 0, int(hash3(x, 1, z, 123) * 2), "minecraft:mossy_cobblestone" if hash3(x, 2, z, 123) < 0.5 else "minecraft:andesite")
    # 中の石室 (宝 (0, 0, -5)・敵 (0, 0, -2))。南の入口から、まぐさの石の戸口
    for z in range(-7, 12):
        for x in range(-2, 3):
            inner = abs(x) <= 1 and (z <= 0 or (abs(x) <= 1 and z <= 11))
            room = z <= 0 and z >= -6
            hall = z > 0
            w = 2 if room else 1
            if abs(x) <= w - 0 and (room or (hall and abs(x) <= 1)):
                pass
    for z in range(-6, 1):
        for x in range(-3, 4):
            for y in range(-1, 5):
                edge = abs(x) == 3 or z in (-6,) or y in (-1, 4)
                if edge:
                    bp.set(x, y, z, "minecraft:stone_bricks" if hash3(x, y, z, 124) < 0.5 else "minecraft:mossy_stone_bricks" if hash3(x, y, z, 125) < 0.6 else "minecraft:cracked_stone_bricks")
                else:
                    bp.set(x, y, z, AIR)
    for z in range(1, 11):
        for x in range(-2, 3):
            for y in range(-1, 4):
                edge = abs(x) == 2 or y in (-1, 3)
                if edge:
                    bp.set(x, y, z, "minecraft:mossy_cobblestone" if hash3(x, y, z, 126) < 0.5 else "minecraft:cobblestone")
                else:
                    bp.set(x, y, z, AIR)
        bp.set(0, -1, z, "minecraft:polished_andesite")
    # 入口の戸口 (立った二本と大きなまぐさ)
    for x in (-2, 2):
        column(bp, x, 11, 0, 3, "minecraft:polished_andesite")
    for x in range(-3, 4):
        bp.set(x, 4, 11, "minecraft:polished_andesite" if abs(x) < 3 else st("andesite", "east" if x < 0 else "west"))
    bp.clear(-1, 0, 11, 1, 2, 12)
    # 室の中: 柱・頭蓋・吊り灯・棺
    for x in (-2, 2):
        for z in (-5, -1):
            column(bp, x, z, 0, 3, wall("stone_brick"))
    bp.set(0, 3, -3, CHAIN_Y)
    bp.set(0, 2, -3, lantern(True, True))
    for x, z, r in ((-2, -6 + 1, 4), (2, -3, 9), (-1, -1, 3)):
        bp.setif(x, 0, z, "minecraft:skeleton_skull[rotation=%d]" % r)
    for x in (-2, 2):
        bp.set(x, 0, -3, "minecraft:polished_andesite")
        bp.set(x, 1, -3, sl("polished_andesite"))
    path_line(bp, 0, 12, 0, 13, 1, 121)
    scatter(bp, 13, 0, FLOWERS, 0.06, 121, only_on={GRASS})
    bp.keep_marks()
    return bp


def ruined_house():
    bp = BP("poi_ruined_house")
    ground(bp, 7, EARTH, 131)
    x0, x1, z0, z1 = -4, 4, -4, 3
    # 石の腰壁と崩れた木組みの壁 (高さがばらつく)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            bp.set(x, -1, z, "minecraft:spruce_planks" if not edge and hash3(x, 0, z, 131) < 0.6 else "minecraft:cobblestone")
            if not edge:
                continue
            h = int(1 + noise3(x, 0, z, 3.0, 131) * 5)
            if x == x0 and z == z0:
                h = 6
            for y in range(0, h):
                corner = x in (x0, x1) and z in (z0, z1)
                if corner:
                    s = log("spruce_log")
                elif y < 2:
                    s = "minecraft:cobblestone" if hash3(x, y, z, 132) < 0.6 else "minecraft:mossy_cobblestone"
                else:
                    s = "minecraft:spruce_planks" if hash3(x, y, z, 133) < 0.7 else "minecraft:stripped_spruce_wood[axis=y]"
                bp.set(x, y, z, s)
            if h > 1 and hash3(x, h, z, 134) < 0.4:
                bp.set(x, h, z, st("cobblestone", "north") if z == z0 else sl("cobblestone"))
    # 戸口と窓の穴
    bp.clear(0, 0, z1, 0, 1, z1)
    bp.clear(-2, 1, z0, -2, 1, z0)
    # 立ったままの煙突 (北西)
    column(bp, x0 + 1, z0 + 1, 0, 7, "minecraft:bricks")
    bp.set(x0 + 1, 0, z0 + 1, "minecraft:campfire[facing=south,lit=false,signal_fire=false,waterlogged=false]")
    bp.set(x0 + 1, 1, z0 + 1, AIR)
    # 落ちた梁と屋根の残り
    for x in range(x0, x1 - 2):
        bp.set(x, 4 - (x - x0) // 3, 0, log("stripped_spruce_log", "x"))
    for x in range(x0, x0 + 4):
        bp.set(x, 5, z0 + 1, st("spruce", "south"))
        bp.set(x, 6, z0 + 2, sl("spruce"))
    for x, z in ((2, 1), (3, -1), (-1, 2)):
        bp.set(x, 0, z, sl("spruce") if hash3(x, 0, z, 135) < 0.5 else "minecraft:cobblestone")
    # 草木が入りこむ (苔・蔦・低木)
    for (x, y, z), s in list(bp.cells.items()):
        if y >= 0 and s != AIR and hash3(x, y, z, 136) < 0.15 and bp.get(x, y + 1, z) in (None, AIR):
            bp.set(x, y + 1, z, "minecraft:moss_carpet")
    bp.set(-2, 0, -2, "minecraft:azalea")
    bp.set(3, 0, -3, "minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]")
    bp.set(3, 1, -3, "minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]")
    scatter(bp, 7, 0, FLOWERS + ["minecraft:fern"], 0.15, 131, only_on={GRASS})
    bp.keep_marks()
    return bp


def roadside_shrine():
    bp = BP("poi_roadside_shrine")
    ground(bp, 5, [(GRASS, 3), ("minecraft:dirt_path", 2), ("minecraft:coarse_dirt", 1)], 141)
    # 石の台 (二段) と小さな社 (板と瓦の切妻、棟に飾り)
    for x in range(-2, 3):
        for z in range(-2, 2):
            bp.set(x, 0, z, "minecraft:stone_bricks" if max(abs(x), abs(z + 0.5)) < 2 else st("stone_brick", "south" if z == 1 else "north" if z == -2 else "east" if x == 2 else "west"))
    for x in range(-1, 2):
        for z in range(-1, 1):
            bp.set(x, 1, z, "minecraft:polished_andesite")
    for x in (-1, 1):
        column(bp, x, 2, 0, 2, log("stripped_dark_oak_log"))
        bp.set(x, 2, -1, log("stripped_dark_oak_log"))
        bp.set(x, 3, -1, log("stripped_dark_oak_log"))
    bp.set(0, 2, -1, "minecraft:dark_oak_planks")
    bp.set(0, 3, -1, "minecraft:dark_oak_planks")
    for x in range(-2, 3):
        bp.set(x, 4, -2, st("deepslate_tile", "south"))
        bp.set(x, 4, 1, st("deepslate_tile", "north"))
        bp.set(x, 5, -1, st("deepslate_tile", "south"))
        bp.set(x, 5, 0, st("deepslate_tile", "north"))
        bp.set(x, 6, -1 if False else 0, AIR)
    bp.set(-2, 5, -1, wall("deepslate_tile"))
    bp.set(2, 5, -1, wall("deepslate_tile"))
    # 中の像 (石の人の形) と供え物
    bp.set(0, 2, 0, "minecraft:chiseled_stone_bricks")
    bp.set(0, 3, 0, "minecraft:skeleton_skull[rotation=8]" if False else "minecraft:stone_button[face=floor,facing=south,powered=false]")
    bp.set(-1, 2, 0, "minecraft:candle[candles=2,lit=true,waterlogged=false]")
    bp.set(1, 2, 0, "minecraft:flower_pot")
    # 石灯籠 2 基
    for x in (-4, 4):
        bp.set(x, 0, 1, "minecraft:stone_bricks")
        bp.set(x, 1, 1, wall("stone_brick"))
        bp.set(x, 2, 1, lantern(False))
        bp.set(x, 3, 1, sl("stone_brick"))
    scatter(bp, 5, 0, FLOWERS, 0.18, 141, only_on={GRASS})
    bp.keep_marks()
    return bp


def mine():
    bp = BP("poi_mine")
    # 地下 (階段と掘り場) は今のまま。地上と入口だけ作り直す
    bp.keep_old(lambda x, y, z, s: y <= -2 or (y == -1 and -2 <= x <= 2 and -8 <= z <= 1))
    ground(bp, 7, [("minecraft:gravel", 2), ("minecraft:coarse_dirt", 3), ("minecraft:stone", 1), (GRASS, 1)], 151)
    bp.keep_old(lambda x, y, z, s: y == -1 and -2 <= x <= 2 and -8 <= z <= 1)
    # 入口の後ろの岩山 (北。坑道は岩の下へ)
    layers = [(1, ["minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:gravel"]), (4, ["minecraft:stone", "minecraft:andesite", "minecraft:tuff"]),
              (99, ["minecraft:stone", "minecraft:andesite", "minecraft:diorite"])]
    rock(bp, 0, 2.5, -4.5, 6.5, 4.2, 3.0, layers, 151, 0.3, below=False)
    rock(bp, -5, 1.0, -1, 2.2, 1.8, 2.5, layers, 152, 0.4, below=False)
    rock(bp, 5, 1.5, -2, 2.0, 2.4, 2.4, layers, 153, 0.4, below=False)
    # 坑口: 階段の上を空け、木の枠
    bp.clear(-1, 0, -7, 1, 3, 2)
    for z in (1, -2, -5):
        column(bp, -2, z, 0, 2, log("spruce_log"))
        column(bp, 2, z, 0, 2, log("spruce_log"))
        for x in range(-2, 3):
            bp.set(x, 3, z, log("spruce_log", "x"))
    bp.set(0, 2, 1, CHAIN_Y)
    bp.set(0, 1, 1, lantern(True))
    for x in range(-2, 3):
        bp.set(x, 4, 1, sl("spruce"))
    # 線路の出口と鉱石の荷車・ずり山
    for z in range(2, 6):
        bp.set(-1, 0, z, "minecraft:rail[shape=north_south,waterlogged=false]")
    bp.set(-1, -1, 6, "minecraft:gravel")
    rock(bp, 4, -0.2, 4, 2.0, 1.0, 1.8, [(99, ["minecraft:gravel", "minecraft:cobblestone", "minecraft:coal_ore", "minecraft:gravel"])], 154, 0.5, below=False)
    for x, z in ((-4, 3), (-5, 4)):
        crate(bp, x, 0, z, 155)
    bp.set(2, 0, 3, "minecraft:spruce_planks")
    bp.set(2, 1, 3, trap("spruce", "south", "top"))
    lamp_post(bp, -3, 5, 3)
    bp.keep_marks()
    return bp


BUILDERS = {
    "outskirts_fire": outskirts_fire, "outskirts_shrine": outskirts_shrine, "outskirts_dock": outskirts_dock,
    "poi_bandit_camp": bandit_camp, "poi_traveler_camp": traveler_camp, "poi_titan_ribs": titan_ribs, "poi_stone_circle": stone_circle,
    "poi_wind_gate": wind_gate, "poi_basalt_gate": basalt_gate, "poi_lighthouse": lighthouse, "poi_rod_tower": rod_tower,
    "poi_watchtower": watchtower, "poi_windmill": windmill, "poi_barrow": barrow, "poi_ruined_house": ruined_house,
    "poi_roadside_shrine": roadside_shrine, "poi_mine": mine,
}


def main(names):
    for name, fn in BUILDERS.items():
        if names and name not in names:
            continue
        bp = fn()
        print(name, bp.save())


if __name__ == "__main__":
    main(sys.argv[1:])
