# -*- coding: utf-8 -*-
"""巨神の背の到着の台 (潮殻・雷翼・余燼。trial/ArrivalDeck が置く)。data/skyabyss/opening/arrival_<titan>.json (部品の形) を書く。

  python -X utf8 tools/gen/world/build_arrival_decks.py

2026-11-07 の声「塀で囲むのは突貫工事すぎる。ちゃんと建築を凝って (岩鯨のように)」。どの台も同じ骨組みで、石と飾りは巨神ごと:
  台     到着の足もと (0, 0, 0) を真ん中に、床 (y -1) は ±6。縁 (±7) は欄干 (塀の石 1 段。高さ 1.5 で跳んで越せない)、
         その外 (±8) は植え込みの棚、さらに外 (±9・±10) は一段ずつ下がる石組みの裾。下は地面まで支える (ArrivalDeck)
  床     縁の帯・十字の道・真ん中の紋 (巨神の石)・その間は二色の市松
  塔     四隅に 2x2 の塔 (高さ 5 + 軒 + 屋根)。軒の外に吊り灯り
  柱     三辺の真ん中に灯りの柱と、内向きの旗
  祠     北 (z -7) の辺は、帰りの石 (0, 0, -6) を納める切妻の祠 (奥の壁・窓・左右の柱・屋根・吊り灯り・紋)
  中は y 0〜8 を空ける (前の版の塀・浜の砂の盛り上がり・前の到着の台の手すりを取り除く。封じの戸・石・鐘・箱は残す。ArrivalDeck)
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/data/skyabyss/opening"
NOTE = "tools/gen/world/build_arrival_decks.py が書く。手で直さない"

STYLES = {
    "tide_shell": dict(
        floor="minecraft:smooth_sandstone", floor2="minecraft:cut_sandstone", path="minecraft:sandstone",
        border="minecraft:prismarine_bricks", emblem="minecraft:dark_prismarine", emblem2="minecraft:sea_lantern",
        base="minecraft:prismarine_bricks", wall="minecraft:prismarine_wall", post="minecraft:prismarine_bricks",
        post2="minecraft:dark_prismarine", accent="minecraft:dark_prismarine", light="minecraft:sea_lantern",
        lantern="minecraft:lantern", roof="minecraft:dark_prismarine_stairs", roof_full="minecraft:dark_prismarine",
        roof_slab="minecraft:dark_prismarine_slab", foundation="minecraft:prismarine_bricks", skirt="minecraft:prismarine",
        lip="minecraft:prismarine_brick_slab", banner="cyan", window="minecraft:light_blue_stained_glass_pane",
        plants=["minecraft:moss_block", "minecraft:flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]",
                "minecraft:sea_pickle[pickles=3,waterlogged=false]", "minecraft:moss_block"],
        top="minecraft:sea_lantern"),
    "thunder_wing": dict(
        floor="minecraft:polished_diorite", floor2="minecraft:calcite", path="minecraft:smooth_quartz",
        border="minecraft:waxed_cut_copper", emblem="minecraft:waxed_oxidized_cut_copper", emblem2="minecraft:waxed_copper_bulb[lit=true,powered=false]",
        base="minecraft:polished_diorite", wall="minecraft:diorite_wall", post="minecraft:calcite",
        post2="minecraft:polished_diorite", accent="minecraft:waxed_chiseled_copper", light="minecraft:waxed_copper_bulb[lit=true,powered=false]",
        lantern="minecraft:lantern", roof="minecraft:waxed_weathered_cut_copper_stairs", roof_full="minecraft:waxed_weathered_cut_copper",
        roof_slab="minecraft:waxed_weathered_cut_copper_slab", foundation="minecraft:polished_diorite", skirt="minecraft:diorite",
        lip="minecraft:polished_diorite_slab", banner="white", window="minecraft:white_stained_glass_pane",
        plants=["minecraft:moss_block", "minecraft:azalea_leaves[distance=1,persistent=true,waterlogged=false]",
                "minecraft:white_wool", "minecraft:moss_block"],
        top="minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]"),
    "ember": dict(
        floor="minecraft:polished_blackstone", floor2="minecraft:polished_blackstone_bricks", path="minecraft:smooth_basalt",
        border="minecraft:chiseled_polished_blackstone", emblem="minecraft:gilded_blackstone", emblem2="minecraft:shroomlight",
        base="minecraft:polished_blackstone_bricks", wall="minecraft:polished_blackstone_brick_wall", post="minecraft:polished_basalt[axis=y]",
        post2="minecraft:polished_blackstone_bricks", accent="minecraft:gilded_blackstone", light="minecraft:shroomlight",
        lantern="minecraft:lantern", roof="minecraft:polished_blackstone_brick_stairs", roof_full="minecraft:polished_blackstone_bricks",
        roof_slab="minecraft:polished_blackstone_brick_slab", foundation="minecraft:blackstone", skirt="minecraft:basalt[axis=y]",
        lip="minecraft:polished_blackstone_slab", banner="red", window="minecraft:iron_bars[east=true,north=false,south=false,waterlogged=false,west=true]",
        plants=["minecraft:crimson_nylium", "minecraft:nether_wart_block", "minecraft:crimson_nylium", "minecraft:magma_block"],
        top="minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]"),
}

INNER = 6
RING = 7


def stairs(block, facing, half="bottom"):
    return f"{block}[facing={facing},half={half},shape=straight,waterlogged=false]"


def slab(block, typ="bottom"):
    return f"{block}[type={typ},waterlogged=false]"


def wall_state(block):
    # 欄干の塀: 隣どうしがつながる形はゲームが直すので、ここは低い柱の形で置く
    return f"{block}[east=low,north=none,south=none,up=true,waterlogged=false,west=low]"


def build(name, s):
    cells = {}

    def put(x, y, z, b):
        cells[(x, y, z)] = b

    r = lambda x, z: max(abs(x), abs(z))
    # ---- 中を空ける (y 0..8, ±8)
    for x in range(-8, 9):
        for z in range(-8, 9):
            for y in range(0, 9):
                put(x, y, z, "minecraft:air")
    # ---- 台の床と基礎 (y -1 / -2)
    for x in range(-10, 11):
        for z in range(-10, 11):
            d = r(x, z)
            if d <= INNER:
                if d == INNER:
                    b = s["border"]
                elif x * x + z * z <= 2:
                    b = s["emblem2"] if x == 0 and z == 0 else s["emblem"]
                elif abs(x) + abs(z) == 4:
                    b = s["emblem"]
                elif x == 0 or z == 0:
                    b = s["path"]
                else:
                    b = s["floor"] if (x + z) % 2 == 0 else s["floor2"]
                put(x, -1, z, b)
                put(x, -2, z, s["foundation"])
            elif d <= 8:
                put(x, -1, z, s["base"])
                put(x, -2, z, s["foundation"])
            elif d == 9:
                put(x, -1, z, slab(s["lip"]))
                put(x, -2, z, s["skirt"])
                put(x, -3, z, s["skirt"])
            else:
                put(x, -3, z, slab(s["lip"]))
                put(x, -4, z, s["skirt"])
    # ---- 欄干 (±7) と植え込み (±8)
    for x in range(-8, 9):
        for z in range(-8, 9):
            d = r(x, z)
            if d == RING:
                put(x, 0, z, wall_state(s["wall"]))
            elif d == 8 and not (abs(x) >= 7 and abs(z) >= 7):
                put(x, 0, z, s["plants"][(abs(x) * 3 + abs(z)) % len(s["plants"])])
    # ---- 四隅の塔 (2x2: ±7..±8)
    for sx in (-1, 1):
        for sz in (-1, 1):
            xs = (7 * sx, 8 * sx)
            zs = (7 * sz, 8 * sz)
            for x in xs:
                for z in zs:
                    for y in range(0, 5):
                        put(x, y, z, s["post"] if y not in (1, 3) else s["post2"])
                    put(x, 5, z, s["accent"])
            # 軒 (塔のまわりの一回り大きい石の帯。y 5) と、その上の板の段 (y 6)・塔の上の低い屋根 (y 6 の板の上に y 7 の石)
            for x in range(min(xs) - 1, max(xs) + 2):
                for z in range(min(zs) - 1, max(zs) + 2):
                    if abs(x) > 9 or abs(z) > 9:
                        continue
                    if x in xs and z in zs:
                        put(x, 6, z, s["roof_full"])
                        continue
                    put(x, 5, z, s["roof_full"])
                    put(x, 6, z, slab(s["roof_slab"]))
            # 塔の頂の飾り (内側の角の上) と、軒の下 (外の面) の吊り灯り
            put(7 * sx, 7, 7 * sz, s["top"])
            put(9 * sx, 4, 7 * sz, s["lantern"] + "[hanging=true,waterlogged=false]")
            put(7 * sx, 4, 9 * sz, s["lantern"] + "[hanging=true,waterlogged=false]")
    # ---- 三辺の真ん中の灯りの柱と旗 (南 z +7・東 x +7・西 x -7)
    for (x, z, face) in ((0, 7, "north"), (7, 0, "west"), (-7, 0, "east")):
        put(x, 0, z, s["post"])
        put(x, 1, z, s["post2"])
        put(x, 2, z, s["post"])
        put(x, 3, z, s["light"])
        put(x, 4, z, slab(s["roof_slab"]))
        dx = {"north": (0, -1), "west": (-1, 0), "east": (1, 0)}[face]
        put(x + dx[0], 2, z + dx[1], f"minecraft:{s['banner']}_wall_banner[facing={face}]")
        # 柱の両わきの欄干の上に小さな灯り
        for side in (-2, 2):
            if face == "north":
                put(side, 1, z, s["lantern"] + "[hanging=false,waterlogged=false]")
            else:
                put(x, 1, side, s["lantern"] + "[hanging=false,waterlogged=false]")
    # ---- 北の祠 (z -7。x -4..4)。帰りの石は (0, 0, -6)
    for x in range(-4, 5):
        for y in range(0, 5):
            b = s["post2"] if abs(x) == 4 else s["base"]
            if y == 2 and abs(x) in (2,):
                b = s["window"]
            if y == 4:
                b = s["accent"] if x == 0 else s["post2"]
            put(x, y, -7, b)
        # 奥の壁の裏 (外) も厚くして、外から見ても祠の背
        for y in range(-1, 4):
            put(x, y, -8, s["post2"] if abs(x) == 4 else s["foundation"])
    put(0, 2, -7, s["accent"])
    put(0, 3, -7, s["light"])
    for x in (-4, 4):
        for y in range(0, 4):
            put(x, y, -5, s["post"] if y != 1 else s["post2"])
        put(x, 0, -6, s["base"])
    # 切妻の屋根 (棟は z -7、y 7)
    for x in range(-5, 6):
        put(x, 4, -4, stairs(s["roof"], "north"))
        put(x, 5, -5, stairs(s["roof"], "north"))
        put(x, 6, -6, stairs(s["roof"], "north"))
        put(x, 7, -7, slab(s["roof_slab"]))
        put(x, 6, -8, s["roof_full"])
        put(x, 7, -8, slab(s["roof_slab"]))
        put(x, 5, -9, slab(s["roof_slab"]))
    # 妻の壁 (x ±5) と、屋根の下を詰める
    for x in (-5, 5):
        for (y, z) in ((4, -5), (4, -6), (5, -6), (4, -7), (5, -7), (6, -7), (4, -8), (5, -8)):
            put(x, y, z, s["post2"])
    for x in range(-4, 5):
        put(x, 5, -7, s["roof_full"])
        put(x, 6, -7, s["roof_full"])
        put(x, 5, -8, s["roof_full"])
        put(x, 5, -6, s["roof_full"])
        put(x, 4, -8, s["roof_full"])
    for x in (-3, 0, 3):
        put(x, 4, -6, s["lantern"] + "[hanging=true,waterlogged=false]")
    # 祠の前の旗 (左右の柱の内側)
    put(-3, 2, -5, f"minecraft:{s['banner']}_wall_banner[facing=east]")
    put(3, 2, -5, f"minecraft:{s['banner']}_wall_banner[facing=west]")
    # 帰りの石の台
    put(0, -1, -6, s["emblem"])
    put(0, 0, -6, "minecraft:lodestone")

    pal = []
    idx = {}
    blocks = []
    for (x, y, z), b in sorted(cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
        if b not in idx:
            idx[b] = len(pal)
            pal.append(b)
        blocks.append([x, y, z, idx[b]])
    data = {"note": NOTE, "titan": name, "palette": pal, "blocks": blocks,
            "marks": {"home_stone": [0, 0, -6]}, "meta": {"rect": [-10, -10, 10, 10]}}
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f"arrival_{name}.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf8")
    return len(blocks)


if __name__ == "__main__":
    for name, s in STYLES.items():
        print(name, build(name, s), "cells")
