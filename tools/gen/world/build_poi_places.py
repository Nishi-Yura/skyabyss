#!/usr/bin/env python3
"""見どころの一か所ずつの部品・名・書付・道しるべを書く (docs/design/40 の 3.3。中身は poi_places_data.py)。

    PYTHONIOENCODING=utf-8 python -I tools/gen/world/build_poi_places.py

書くもの (何度流しても同じになる):
  data/skyabyss/continent/pois.json        消す見どころを "gone" に、場所の名 (name) を足す、道しるべを足す
  data/skyabyss/opening/poi_<種類>__<名>.json  元の部品 poi_<種類>.json に、その場所の添え物・碑 (tablet)・旅人の立つ所 (folk) を重ねたもの
  data/skyabyss/opening/poi_signpost.json   道しるべの部品 (石の台・柱・灯・板の看板。印 sign)
  assets/skyabyss/lang/ja_jp.json・en_us.json  名・書付・道しるべの 4 行・頭目の名 (この道具の書く鍵は、ないものを消してから書き直す)
撮影: tools/shots/poi_places.txt
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from poi_kit import AIR, CHAIN_Y, column, fence, hash3, lantern, log, sl, st, trap  # noqa: E402
import poi_places_data as D  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
RES = os.path.join(ROOT, "src", "main", "resources")
OPENING = os.path.join(RES, "data", "skyabyss", "opening")
CONT = os.path.join(RES, "data", "skyabyss", "continent")
LANG = os.path.join(RES, "assets", "skyabyss", "lang")
NOTE = "見どころの一か所の部品 (docs/design/40 の 3.3)。tools/gen/world/build_poi_places.py が元の部品に添え物を重ねて書く。手で直さない"
TABLET = "skyabyss:tablet[facing=south,standing=true]"
GRASS = "minecraft:grass_block[snowy=false]"
# この道具が書く lang の鍵の頭 (書き直す前に消す)
PREFIXES = ("map.skyabyss.poi_name.", "sign.skyabyss.poi_note.", "sign.skyabyss.waypost.", "entity.skyabyss.bandit_brother.")


class Piece:
    """添え物 (前 = 南 +z。原点のまわりに置く。y = -1 は地面の層)"""

    def __init__(self):
        self.cells = {}

    def set(self, x, y, z, s):
        self.cells[(x, y, z)] = s

    def get(self, x, y, z):
        return self.cells.get((x, y, z))


def stairs(block, facing, half="bottom"):
    return st(block, facing, half)


def slab(block, half="bottom"):
    return sl(block, half)


def candle(n=1, color=""):
    return f"minecraft:{color + '_' if color else ''}candle[candles={n},lit=true,waterlogged=false]"


def button_floor(block="stone"):
    return f"minecraft:{block}_button[face=floor,facing=north,powered=false]"


# ---------------------------------------------------------------- 添え物
def offering(p):
    """苔むした石の供え台: ろうそく・花・背の石と灯"""
    for x in (-1, 0, 1):
        for z in (-1, 0, 1):
            p.set(x, -1, z, "minecraft:mossy_cobblestone" if (x + z) % 2 else "minecraft:cobblestone")
    p.set(-1, 0, 0, "minecraft:mossy_stone_bricks")
    p.set(0, 0, 0, "minecraft:chiseled_stone_bricks")
    p.set(1, 0, 0, "minecraft:cracked_stone_bricks")
    p.set(-1, 1, 0, "minecraft:potted_poppy")
    p.set(0, 1, 0, candle(3, "white"))
    p.set(1, 1, 0, candle(2))
    column(p, 0, -1, 0, 1, "minecraft:mossy_stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    p.set(0, 2, -1, lantern())
    p.set(0, 0, 1, slab("stone_brick"))


def pen(p):
    """空の羊の囲い: 柵・木戸・干し草・水桶"""
    for x in range(-2, 3):
        for z in range(-2, 2):
            p.set(x, -1, z, "minecraft:coarse_dirt" if hash3(x, 0, z, 5) < 0.5 else "minecraft:podzol")
            if x in (-2, 2) or z in (-2, 1):
                p.set(x, 0, z, fence("oak"))
    p.set(0, 0, 1, "minecraft:oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]")
    p.set(-1, 0, -1, "minecraft:hay_block[axis=x]")
    p.set(1, 0, -1, "minecraft:cauldron")
    p.set(-1, 0, 0, "minecraft:white_carpet")


def bedroll(p):
    """寝床と消えた焚き火、背負い袋"""
    for x in (-2, -1, 0, 1, 2):
        for z in (-1, 0, 1):
            p.set(x, -1, z, "minecraft:coarse_dirt" if hash3(x, 1, z, 7) < 0.6 else "minecraft:dirt")
    p.set(-1, 0, -1, "minecraft:white_carpet")
    p.set(-1, 0, 0, "minecraft:brown_carpet")
    p.set(-1, 0, 1, "minecraft:brown_carpet")
    p.set(1, 0, 0, "minecraft:campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]")
    p.set(2, 0, 0, slab("cobblestone"))
    p.set(1, 0, -1, slab("mossy_cobblestone"))
    p.set(1, 0, 1, slab("cobblestone"))
    p.set(0, 0, -1, "minecraft:barrel[facing=up,open=false]")
    p.set(0, 1, -1, trap("spruce", "north"))


def cairn_lamp(p):
    """石を積んだ塚と灯"""
    p.set(0, 0, 0, "minecraft:mossy_cobblestone")
    p.set(1, 0, 0, slab("cobblestone"))
    p.set(-1, 0, 0, slab("mossy_cobblestone"))
    p.set(0, 0, -1, slab("cobblestone"))
    p.set(0, 1, 0, "minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    p.set(0, 2, 0, lantern())
    p.set(1, 0, -1, button_floor())


def _scarecrow(p, x, back=False, head="minecraft:carved_pumpkin"):
    column(p, x, 0, 0, 1, fence("spruce"))
    p.set(x, 2, 0, "minecraft:hay_block[axis=y]")
    p.set(x, 3, 0, f"{head}[facing={'north' if back else 'south'}]")
    p.set(x, 4, 0, "minecraft:brown_carpet")
    p.set(x - 1, 2, 0, log("stripped_spruce_log", "x"))
    p.set(x + 1, 2, 0, log("stripped_spruce_log", "x"))


def scarecrow(p):
    """かかしと干し草・かぼちゃ"""
    _scarecrow(p, 0)
    p.set(1, 0, 1, "minecraft:hay_block[axis=x]")
    p.set(-1, 0, 1, "minecraft:pumpkin")


def scarecrow_pair(p):
    """かかし二体 (一体だけ、後ろを向いている)"""
    _scarecrow(p, -2)
    _scarecrow(p, 2, back=True)
    p.set(0, 0, 1, "minecraft:hay_block[axis=z]")


def bell_post(p):
    """小さな鐘楼: 丸太の枠・梁から吊った鐘 (叩くと鳴る)・切妻の小屋根"""
    for x in (-1, 1):
        column(p, x, 0, 0, 3, log("dark_oak_log"))
        p.set(x, -1, 0, "minecraft:cobblestone")
    p.set(0, 3, 0, log("dark_oak_log", "x"))
    p.set(0, 2, 0, "minecraft:bell[attachment=ceiling,facing=south,powered=false]")
    p.set(-1, 4, 0, stairs("dark_oak", "east"))
    p.set(0, 4, 0, "minecraft:dark_oak_planks")
    p.set(1, 4, 0, stairs("dark_oak", "west"))
    p.set(0, 5, 0, slab("dark_oak"))
    p.set(0, 0, 0, slab("cobblestone"))


def well(p):
    """石組みの井戸: 水・屋根・鎖"""
    for x in (-1, 0, 1):
        for z in (-1, 0, 1):
            p.set(x, -2, z, "minecraft:cobblestone")
            if x == 0 and z == 0:
                p.set(x, -1, z, "minecraft:water[level=0]")
                p.set(x, 0, z, AIR)
            else:
                s = "minecraft:mossy_cobblestone" if hash3(x, 0, z, 3) < 0.5 else "minecraft:stone_bricks"
                p.set(x, -1, z, "minecraft:cobblestone")
                p.set(x, 0, z, s)
    for x in (-1, 1):
        column(p, x, 0, 1, 2, fence("spruce"))
    p.set(-1, 3, 0, log("spruce_log", "x"))
    p.set(0, 3, 0, log("spruce_log", "x"))
    p.set(1, 3, 0, log("spruce_log", "x"))
    for x in (-1, 0, 1):
        p.set(x, 3, -1, stairs("spruce", "south"))
        p.set(x, 3, 1, stairs("spruce", "north"))
        p.set(x, 4, 0, slab("spruce"))
    p.set(0, 2, 0, CHAIN_Y)
    p.set(0, 1, 0, lantern(True))


def fresh_grave(p):
    """新しい墓: 盛り土・石の札・花・ろうそく"""
    for x in (-1, 0, 1):
        for z in (-1, 0, 1, 2):
            p.set(x, -1, z, "minecraft:podzol" if x == 0 and z in (0, 1) else GRASS)
    p.set(0, 0, -1, "minecraft:polished_andesite")
    p.set(0, 1, -1, slab("polished_andesite"))
    p.set(0, 0, 0, "minecraft:moss_carpet")
    p.set(0, 0, 1, "minecraft:moss_carpet")
    p.set(1, 0, 0, "minecraft:lily_of_the_valley")
    p.set(-1, 0, 1, "minecraft:white_tulip")
    p.set(-1, 0, -1, candle(1, "white"))


def banner_pole(p, color="light_gray"):
    """旗竿: 石の根もと・柵の竿・旗"""
    p.set(0, -1, 0, "minecraft:cobblestone")
    column(p, 0, 0, 0, 2, fence("spruce"))
    p.set(0, 3, 0, log("stripped_spruce_log"))
    p.set(0, 4, 0, f"minecraft:{color}_banner[rotation=0]")
    p.set(1, 0, 0, slab("cobblestone"))
    p.set(-1, 0, 0, slab("mossy_cobblestone"))
    p.set(0, 0, -1, slab("cobblestone"))


def swing(p):
    """ぶらんこ: 丸太の門形・鎖・板の座"""
    for x in (-2, 2):
        column(p, x, 0, 0, 3, log("oak_log"))
    for x in range(-2, 3):
        p.set(x, 4, 0, log("oak_log", "x"))
    for x in (-1, 1):
        column(p, x, 0, 2, 3, CHAIN_Y)
    for x in (-1, 0, 1):
        p.set(x, 1, 0, slab("oak", "top"))


def view_bench(p):
    """見晴らしの腰かけ: 南を向いた長椅子・灯・鉢"""
    for x in (-1, 0, 1):
        p.set(x, 0, 0, stairs("spruce", "north"))
    p.set(-2, 0, 0, trap("spruce", "east", "bottom", True))
    p.set(2, 0, 0, trap("spruce", "west", "bottom", True))
    column(p, 2, -1, 0, 1, fence("spruce"))
    p.set(2, 2, -1, lantern())
    p.set(-2, 0, -1, log("oak_log"))
    p.set(-2, 1, -1, "minecraft:potted_blue_orchid")
    for x in range(-2, 3):
        p.set(x, -1, 1, "minecraft:dirt_path")


def dovecote(p):
    """鳩小屋: 柱の上の小さな箱と出入りの戸"""
    column(p, 0, 0, 0, 2, log("dark_oak_log"))
    for x in (-1, 0, 1):
        for z in (-1, 0, 1):
            p.set(x, 3, z, "minecraft:spruce_planks")
            corner = abs(x) == 1 and abs(z) == 1
            if corner:
                p.set(x, 4, z, "minecraft:spruce_planks")
            elif not (x == 0 and z == 0):
                f = {(0, -1): "north", (0, 1): "south", (-1, 0): "west", (1, 0): "east"}[(x, z)]
                p.set(x, 4, z, trap("spruce", f, "bottom", True))
        p.set(x, 5, -1, stairs("dark_oak", "south"))
        p.set(x, 5, 1, stairs("dark_oak", "north"))
        p.set(x, 5, 0, "minecraft:dark_oak_planks")
    p.set(0, 0, 1, "minecraft:barrel[facing=up,open=false]")


def lantern_row(p):
    """道の灯: 三本の灯の柱と砂利の小径"""
    for x in range(-3, 4):
        p.set(x, -1, 0, "minecraft:gravel" if hash3(x, 0, 0, 9) < 0.5 else "minecraft:dirt_path")
    for x in (-3, 0, 3):
        column(p, x, -1, 0, 1, fence("dark_oak"))
        p.set(x, 2, -1, lantern())


def cairn(p):
    """小石を積んだ山 (願いの石)"""
    p.set(0, 0, 0, "minecraft:cobblestone")
    p.set(0, 1, 0, slab("mossy_cobblestone"))
    p.set(1, 0, 0, slab("cobblestone"))
    p.set(-1, 0, 0, slab("andesite"))
    p.set(0, 0, 1, slab("mossy_cobblestone"))
    p.set(0, 0, -1, "minecraft:mossy_cobblestone")
    p.set(0, 1, -1, "minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    for x, z in ((2, 1), (-2, -1), (1, -2), (-2, 1)):
        p.set(x, 0, z, button_floor())


def loom(p):
    """機の小屋: 機・糸の羊毛・四本柱の片流れの屋根 (奥が高い)"""
    for x in (-2, 2):
        column(p, x, -1, 0, 2, fence("spruce"))
        column(p, x, 0, 0, 1, fence("spruce"))
    for x in range(-2, 3):
        p.set(x, 3, -1, stairs("spruce", "north"))
        p.set(x, 2, 0, stairs("spruce", "north"))
    p.set(0, 0, -1, "minecraft:loom[facing=south]")
    p.set(1, 0, -1, "minecraft:white_wool")
    p.set(1, 1, -1, "minecraft:light_blue_wool")
    p.set(-1, 0, -1, "minecraft:barrel[facing=up,open=false]")
    p.set(-1, 1, -1, "minecraft:red_carpet")


def garden(p):
    """小さな花壇: 石の縁と色の混じった花"""
    flowers = ["minecraft:poppy", "minecraft:cornflower", "minecraft:oxeye_daisy", "minecraft:allium", "minecraft:azure_bluet", "minecraft:pink_tulip"]
    for x in range(-2, 3):
        for z in (-1, 0):
            p.set(x, -1, z, GRASS)
            p.set(x, 0, z, flowers[int(hash3(x, 2, z, 11) * len(flowers)) % len(flowers)])
        p.set(x, 0, 1, slab("mossy_cobblestone"))
    for z in (-1, 0, 1):
        p.set(-3, 0, z, slab("cobblestone"))
        p.set(3, 0, z, slab("cobblestone"))
    p.set(3, 1, 1, "minecraft:potted_fern")


def woodpile(p):
    """薪の山と割り台"""
    for k in range(4):
        x = k - 2
        p.set(x, 0, 0, log("spruce_log", "z"))
        if k < 3:
            p.set(x, 1, 0, log("stripped_spruce_log", "z"))
    p.set(-1, 2, 0, log("spruce_log", "z"))
    p.set(2, 0, 1, log("oak_log"))
    p.set(-2, 0, 1, "minecraft:barrel[facing=up,open=false]")


def beacon_cairn(p):
    """狼煙の台: 石の台と火 (四辺は中へ上がる段、四隅は角の段 = 外角)"""
    for x in (-1, 0, 1):
        for z in (-1, 0, 1):
            p.set(x, -1, z, "minecraft:cobblestone")
            if x == 0 and z == 0:
                p.set(x, 0, z, "minecraft:stone_bricks")
            elif x == 0 or z == 0:
                f = {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}[(x, z)]
                p.set(x, 0, z, stairs("cobblestone", f))
            else:
                p.set(x, 0, z, stairs("mossy_cobblestone", "west" if x == 1 else "east"))
    p.set(0, 1, 0, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")


def broken_cart(p):
    """片輪の落ちた荷車と散った荷"""
    for x in (-1, 0, 1):
        for z in (0, 1):
            p.set(x, 0, z, slab("spruce", "top"))
    p.set(-2, 0, 0, trap("dark_oak", "west", "bottom", True))
    p.set(-2, 0, 1, trap("dark_oak", "west", "bottom", True))
    p.set(2, 0, 2, trap("dark_oak", "north", "bottom", False))
    p.set(1, 0, -1, "minecraft:hay_block[axis=x]")
    p.set(2, 0, 0, "minecraft:barrel[facing=east,open=false]")
    p.set(0, 1, 0, "minecraft:barrel[facing=up,open=false]")
    p.set(-1, 0, 2, fence("spruce"))


def sacks(p):
    """粉の袋の山"""
    p.set(-1, 0, 0, "minecraft:white_wool")
    p.set(0, 0, 0, "minecraft:white_wool")
    p.set(0, 1, 0, "minecraft:white_wool")
    p.set(1, 0, 0, "minecraft:barrel[facing=up,open=false]")
    p.set(-1, 0, 1, "minecraft:white_carpet")
    p.set(1, 1, 0, "minecraft:white_carpet")
    p.set(2, 0, 0, "minecraft:composter[level=5]")


def weapon_rack(p):
    """野盗の武器の棚・的・研ぎ台・旗"""
    for x in (-1, 1):
        column(p, x, 0, 0, 1, fence("spruce"))
    p.set(0, 1, 0, log("stripped_spruce_log", "x"))
    p.set(0, 2, 0, "minecraft:red_banner[rotation=0]")
    p.set(0, 0, 0, "minecraft:spruce_planks")
    p.set(2, 0, 0, "minecraft:grindstone[face=floor,facing=north]")
    p.set(-2, 0, -1, "minecraft:hay_block[axis=y]")
    p.set(-2, 1, -1, "minecraft:target[power=0]")


def bone_pile(p):
    """骨の山と頭の骨"""
    p.set(0, 0, 0, "minecraft:bone_block[axis=x]")
    p.set(1, 0, 0, "minecraft:bone_block[axis=y]")
    p.set(0, 1, 0, "minecraft:bone_block[axis=z]")
    p.set(2, 0, -1, "minecraft:bone_block[axis=z]")
    p.set(-1, 0, 0, "minecraft:calcite")
    p.set(-1, 0, 1, "minecraft:skeleton_skull[rotation=10]")
    p.set(1, 1, 0, "minecraft:skeleton_skull[rotation=3]")


def lectern(p):
    """書見台と本の山"""
    p.set(0, 0, 0, "minecraft:lectern[facing=south,has_book=false,powered=false]")
    p.set(-1, 0, -1, "minecraft:bookshelf")
    p.set(-2, 0, -1, "minecraft:bookshelf")
    p.set(-2, 1, -1, "minecraft:bookshelf")
    p.set(-1, 1, -1, candle(2))
    p.set(1, 0, -1, "minecraft:barrel[facing=up,open=false]")


def stone_stack(p):
    """切り出した石の山と石切り台"""
    p.set(-1, 0, 0, "minecraft:stone_bricks")
    p.set(0, 0, 0, "minecraft:smooth_stone")
    p.set(0, 1, 0, "minecraft:stone_bricks")
    p.set(1, 0, 0, slab("smooth_stone"))
    p.set(1, 0, 1, "minecraft:stonecutter[facing=south]")
    p.set(-1, 1, 0, slab("stone_brick"))


def anchor(p):
    """砂に刺さった錨と鎖"""
    column(p, 0, 0, 0, 2, "minecraft:deepslate_tile_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    p.set(0, 3, 0, "minecraft:chain[axis=x,waterlogged=false]")
    p.set(-1, 0, 0, stairs("deepslate_tile", "east"))
    p.set(1, 0, 0, stairs("deepslate_tile", "west"))
    p.set(0, 0, 1, "minecraft:chain[axis=z,waterlogged=false]")
    p.set(0, 0, 2, "minecraft:chain[axis=z,waterlogged=false]")


def sea_chests(p):
    """打ち上げられた箱と、鳴る箱 (音符の箱。叩くと一つ鳴る)"""
    p.set(-1, 0, 0, "minecraft:barrel[facing=east,open=false]")
    p.set(0, 0, 0, "minecraft:note_block[instrument=harp,note=12,powered=false]")
    p.set(1, 0, 0, "minecraft:barrel[facing=up,open=false]")
    p.set(1, 1, 0, slab("spruce"))
    p.set(-1, 0, 1, slab("spruce"))
    p.set(2, 0, -1, "minecraft:chain[axis=x,waterlogged=false]")


def drying_rack(p):
    """網干し: 柵の竿・干した昆布"""
    for x in (-2, 2):
        p.set(x, 0, 0, fence("spruce"))
    for x in range(-2, 3):
        p.set(x, 1, 0, fence("spruce"))
    p.set(0, 0, 1, "minecraft:dried_kelp_block")
    p.set(1, 0, 1, "minecraft:barrel[facing=up,open=false]")
    p.set(-1, 0, 1, "minecraft:cobweb")


def upturned_boat(p):
    """伏せた舟"""
    for x in range(-2, 3):
        p.set(x, 0, -1, stairs("dark_oak", "south"))
        p.set(x, 0, 1, stairs("dark_oak", "north"))
        p.set(x, 0, 0, "minecraft:dark_oak_planks")
        p.set(x, 1, 0, slab("dark_oak"))
    p.set(-3, 0, 0, stairs("dark_oak", "east"))
    p.set(3, 0, 0, stairs("dark_oak", "west"))


def rod_stub(p):
    """折れた避雷の棒と焦げた地面"""
    for x in (-1, 0, 1):
        for z in (-1, 0, 1):
            p.set(x, -1, z, "minecraft:blackstone" if hash3(x, 4, z, 13) < 0.5 else "minecraft:basalt[axis=y]")
    p.set(0, 0, 0, "minecraft:weathered_copper")
    p.set(0, 1, 0, "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]")
    p.set(0, 2, 0, "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]")
    p.set(1, 0, 1, "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]")


def wind_chime(p):
    """風鈴の門: 梁から下がる紫水晶と灯"""
    for x in (-2, 2):
        column(p, x, 0, 0, 3, fence("spruce"))
    for x in range(-2, 3):
        p.set(x, 4, 0, log("stripped_spruce_log", "x"))
    p.set(-1, 3, 0, "minecraft:amethyst_cluster[facing=down,waterlogged=false]")
    p.set(1, 3, 0, "minecraft:amethyst_cluster[facing=down,waterlogged=false]")
    p.set(0, 3, 0, CHAIN_Y)
    p.set(0, 2, 0, lantern(True))


def ash_statue(p):
    """灰に半ば埋もれた像"""
    for x in (-1, 0, 1):
        for z in (-1, 0, 1):
            p.set(x, -1, z, "minecraft:gray_concrete_powder" if hash3(x, 6, z, 17) < 0.5 else "minecraft:tuff")
    p.set(0, 0, 0, "minecraft:polished_blackstone")
    p.set(0, 1, 0, "minecraft:chiseled_polished_blackstone")
    p.set(0, 2, 0, "minecraft:polished_blackstone_bricks")
    p.set(-1, 1, 0, stairs("polished_blackstone", "east", "top"))
    p.set(1, 0, 0, slab("polished_blackstone"))
    p.set(0, 0, 1, slab("blackstone"))


def anvil(p):
    """金床と冷えた炭"""
    p.set(0, 0, 0, "minecraft:damaged_anvil[facing=east]")
    p.set(1, 0, 0, "minecraft:coal_block")
    p.set(-1, 0, 0, "minecraft:barrel[facing=up,open=false]")
    p.set(1, 1, 0, "minecraft:campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]")


def anvil_pair(p):
    """金床が二つ"""
    p.set(-1, 0, 0, "minecraft:chipped_anvil[facing=north]")
    p.set(1, 0, 0, "minecraft:damaged_anvil[facing=east]")
    p.set(0, 0, -1, "minecraft:coal_block")
    p.set(0, 0, 1, slab("blackstone"))


PIECES = {f.__name__: f for f in (offering, pen, bedroll, cairn_lamp, scarecrow, scarecrow_pair, bell_post, well, fresh_grave, banner_pole, swing,
                                  view_bench, dovecote, lantern_row, cairn, loom, garden, woodpile, beacon_cairn, broken_cart, sacks, weapon_rack,
                                  bone_pile, lectern, stone_stack, anchor, sea_chests, drying_rack, upturned_boat, rod_stub, wind_chime, ash_statue,
                                  anvil, anvil_pair)}


def connect_fences(cells):
    """柵どうし・柵と木戸をつなぐ"""
    sides = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
    for (x, y, z), s in list(cells.items()):
        if not s.endswith("]") or "_fence[" not in s:
            continue
        base = s.split("[")[0]
        props = []
        for name, (dx, dz) in sides.items():
            n = cells.get((x + dx, y, z + dz), "")
            props.append(f"{name}={'true' if ('_fence' in n or '_fence_gate' in n) else 'false'}")
        cells[(x, y, z)] = f"{base}[east={props[3][5:]},north={props[0][6:]},south={props[1][6:]},waterlogged=false,west={props[2][5:]}]"


# ---------------------------------------------------------------- 置き場を探す
def load(name):
    return json.load(open(os.path.join(OPENING, name + ".json"), encoding="utf-8"))


def occupancy(bp):
    pal = bp["palette"]
    top = set()
    hollow = set()
    for x, y, z, i in bp["blocks"]:
        s = pal[i]
        # 添え物の高さ (7 まで) にあるものだけ見る (大樹の梢の下にも置ける)
        if 0 <= y <= 7 and s != AIR:
            top.add((x, z))
        if y < -1 and s == AIR:
            hollow.add((x, z))
        if y == -1 and s == AIR:
            hollow.add((x, z))
    return top, hollow


def place(bp, piece, slug, need_tablet, folk_spot, r):
    """添え物を置く原点を決める (範囲 r の中で空いていて、前 (南) 寄り・真ん中から離れた所)"""
    rr = min(-r[0], -r[1], r[2], r[3])
    top, hollow = occupancy(bp)
    marks = {(m[0], m[2]) for m in bp["marks"].values()}
    cols = {(x, z) for (x, y, z) in piece.cells if y >= 0}
    if need_tablet:
        zmax = max(z for (x, y, z) in piece.cells)
        cols.add((0, zmax + 1))
    under = {(x, z) for (x, y, z) in piece.cells}
    best = None
    for ax in range(r[0], r[2] + 1):
        for az in range(r[1], r[3] + 1):
            ok = True
            for (x, z) in cols | under:
                wx, wz = ax + x, az + z
                limit = rr - 0.5 if bp["meta"].get("round") else rr
                if math.hypot(wx, wz) > limit if bp["meta"].get("round") else max(abs(wx), abs(wz)) > limit:
                    ok = False
                    break
                if (wx, wz) in hollow:
                    ok = False
                    break
                for dx in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        c = (wx + dx, wz + dz)
                        if c in top or c in marks or (folk_spot and c == folk_spot):
                            ok = False
                            break
                    if not ok:
                        break
                if not ok:
                    break
            if not ok:
                continue
            d = math.hypot(ax, az)
            # 前 (南) 寄り・真ん中から半径の 0.6 あたり。同じ点が並んだら名から決まる揺らぎで選ぶ
            score = abs(d - rr * 0.6) + (0 if az > 0 else 2.5) + hash3(ax, 0, az, sum(map(ord, slug)) % 997) * 0.8
            if best is None or score < best[0]:
                best = (score, ax, az)
    return None if best is None else (best[1], best[2])


def build_site(index, kind, slug, piece_name, folk):
    base = load("poi_" + kind)
    cells = {}
    pal = base["palette"]
    for x, y, z, i in base["blocks"]:
        cells[(x, y, z)] = pal[i]
    marks = {k: list(v) for k, v in base["marks"].items()}
    need_tablet = "tablet" not in marks
    folk_spot = None
    if folk:
        folk_spot = find_folk_spot(cells, marks)
        marks["folk"] = [folk_spot[0], 0, folk_spot[1]]
    if piece_name:
        p = Piece()
        PIECES[piece_name](p)
        # 部品の範囲に空きがなければ、範囲 (地均しの所) を 2 ずつ広げる
        at = None
        for grow in (0, 2, 4, 6):
            r = [v - grow if k < 2 else v + grow for k, v in enumerate(base["meta"]["rect"])]
            at = place(base, p, slug, need_tablet, folk_spot, r)
            if at is not None:
                if grow:
                    base["meta"] = dict(base["meta"], rect=r)
                break
        if at is None:
            raise SystemExit(f"no room for {piece_name} at {index} {kind} {slug}")
        ax, az = at
        for (x, y, z), s in p.cells.items():
            cells[(ax + x, y, az + z)] = s
            # 添え物の上は空ける (元の部品の草花など)
            if y >= 0:
                for yy in range(y + 1, y + 3):
                    if (ax + x, yy, az + z) in cells and (ax + x, yy, az + z) not in {(ax + px, py, az + pz) for (px, py, pz) in p.cells}:
                        cells[(ax + x, yy, az + z)] = AIR
        if need_tablet:
            zmax = max(z for (x, y, z) in p.cells)
            tx, tz = ax, az + zmax + 1
            cells[(tx, 0, tz)] = TABLET
            cells.setdefault((tx, -1, tz), "minecraft:cobblestone")
            marks["tablet"] = [tx, 0, tz]
    elif need_tablet:
        raise SystemExit(f"{index} {kind} {slug} needs a piece to carry its tablet")
    connect_fences(cells)
    palette = []
    idx = {}
    blocks = []
    for (x, y, z), s in sorted(cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
        if s not in idx:
            idx[s] = len(palette)
            palette.append(s)
        blocks.append([x, y, z, idx[s]])
    name = f"poi_{kind}__{slug}"
    out = {"site": name, "note": NOTE, "palette": palette, "blocks": blocks, "marks": marks, "meta": base["meta"]}
    with open(os.path.join(OPENING, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    return name


def find_folk_spot(cells, marks):
    """旅人の立つ所: 焚き火のそばの空いた地面"""
    fires = [(x, z) for (x, y, z), s in cells.items() if y == 0 and s.startswith("minecraft:campfire")]
    fx, fz = fires[0] if fires else (0, 0)
    for r in (2, 3):
        for dx, dz in ((0, r), (-r, 0), (r, 0), (0, -r), (-r, r), (r, r)):
            x, z = fx + dx, fz + dz
            if all((x, y, z) not in cells or cells[(x, y, z)] == AIR for y in (0, 1)) and [x, 0, z] not in marks.values():
                return (x, z)
    raise SystemExit("no folk spot")


# ---------------------------------------------------------------- 道しるべの部品
def signpost():
    cells = {}
    for x in range(-1, 2):
        for z in range(-1, 2):
            cells[(x, -1, z)] = "minecraft:cobblestone" if (x + z) % 2 else "minecraft:mossy_cobblestone"
            if abs(x) + abs(z) == 1 and z != 1:
                f = {(1, 0): "west", (-1, 0): "east", (0, -1): "south"}[(x, z)]
                cells[(x, 0, z)] = stairs("stone_brick", f)
            elif abs(x) == 1 and z == -1:
                # 北の二隅は横と北の段の列が出会うので角の段 (外角)。南の二隅は看板の前の半ブロックの列に続く
                cells[(x, 0, z)] = stairs("mossy_stone_brick", "west" if x == 1 else "east")
            elif abs(x) == 1 and abs(z) == 1:
                cells[(x, 0, z)] = slab("mossy_stone_brick")
    column(cells_bp(cells), 0, 0, 0, 3, log("dark_oak_log"))
    cells[(0, 4, 0)] = slab("dark_oak")
    # 横木と吊った灯
    cells[(1, 3, 0)] = fence("dark_oak")
    cells[(-1, 3, 0)] = fence("dark_oak")
    cells[(1, 2, 0)] = lantern(True)
    cells[(0, 0, 1)] = slab("stone_brick")
    # 看板 (柱の南の面。行き先・土地の名・目安のレベル)
    cells[(0, 1, 1)] = "minecraft:birch_wall_sign[facing=south,waterlogged=false]"
    connect_fences(cells)
    palette, idx, blocks = [], {}, []
    for (x, y, z), s in sorted(cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
        if s not in idx:
            idx[s] = len(palette)
            palette.append(s)
        blocks.append([x, y, z, idx[s]])
    out = {"site": "poi_signpost", "note": "道しるべ (docs/design/40 の 3.3)。tools/gen/world/build_poi_places.py が書く。手で直さない", "palette": palette,
           "blocks": blocks, "marks": {"sign": [0, 1, 1]}, "meta": {"rect": [-2, -2, 2, 2], "level": True, "round": True, "ring": 2}}
    with open(os.path.join(OPENING, "poi_signpost.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")


class cells_bp:
    def __init__(self, cells):
        self.cells = cells

    def set(self, x, y, z, s):
        self.cells[(x, y, z)] = s


# ---------------------------------------------------------------- 道の上の点
def road_point(layout, road, dist):
    pts = layout["roads"][road]
    left = dist
    for a, b in zip(pts, pts[1:]):
        seg = math.dist(a, b)
        if left <= seg:
            t = left / seg
            x = a[0] + (b[0] - a[0]) * t
            z = a[1] + (b[1] - a[1]) * t
            # 道の右手へ 6 ずらす (道の縁の外。置くときに RoadClearance がさらに離す)
            nx, nz = -(b[1] - a[1]) / seg, (b[0] - a[0]) / seg
            return round(x + nx * 6), round(z + nz * 6), sum(math.dist(p, q) for p, q in zip(pts, pts[1:])) - dist
        left -= seg
    raise SystemExit(f"road {road} shorter than {dist}")


def main():
    pois_path = os.path.join(CONT, "pois.json")
    pois = json.load(open(pois_path, encoding="utf-8"))
    layout = json.load(open(os.path.join(CONT, "layout.json"), encoding="utf-8"))
    lang = {"ja": {}, "en": {}}

    for i in D.GONE:
        assert pois[i]["kind"] in ("gone", "signpost", "bandit_camp", "traveler_camp"), (i, pois[i])
        pois[i] = {"kind": "gone", "x": pois[i]["x"], "z": pois[i]["z"]}

    # 道しるべ
    signpost()
    for i, (name, ja, en) in D.WAYPOSTS.items():
        assert pois[i]["kind"] == "signpost", (i, pois[i])
        pois[i] = {"kind": "signpost", "x": pois[i]["x"], "z": pois[i]["z"], "name": name}
        for k in range(4):
            lang["ja"][f"sign.skyabyss.waypost.{name}.{k + 1}"] = ja[k]
            lang["en"][f"sign.skyabyss.waypost.{name}.{k + 1}"] = en[k]
    for name, _, road, dist, ja, en in D.NEW_WAYPOSTS:
        x, z, far = road_point(layout, road, dist)
        if name == "tide_landing":
            t = layout["towns"]["tide"]
            far = math.dist((x, z), t)
        far = int(round(far / 10.0) * 10)
        hit = [k for k, p in enumerate(pois) if p.get("name") == name]
        entry = {"kind": "signpost", "x": x, "z": z, "name": name}
        if hit:
            pois[hit[0]] = entry
        else:
            pois.append(entry)
        for k in range(4):
            lang["ja"][f"sign.skyabyss.waypost.{name}.{k + 1}"] = ja[k].format(far=far)
            lang["en"][f"sign.skyabyss.waypost.{name}.{k + 1}"] = en[k].format(far=far)

    # 場所
    built = []
    for i, p in enumerate(pois):
        if p["kind"] in ("gone", "signpost") or p["kind"].startswith("dungeon_") or i == 0:
            continue
        if i not in D.PLACES:
            raise SystemExit(f"no place content for {i} {p['kind']}")
    for i, (slug, ja, en, piece, nja, nen) in sorted(D.PLACES.items()):
        p = pois[i]
        assert p["kind"] not in ("gone", "signpost"), (i, p)
        kind = p["kind"]
        pois[i] = {"kind": kind, "x": p["x"], "z": p["z"], "name": slug}
        built.append(build_site(i, kind, slug, piece, i in D.TRAVELERS))
        lang["ja"][f"map.skyabyss.poi_name.{slug}"] = ja
        lang["en"][f"map.skyabyss.poi_name.{slug}"] = en
        lj, le = nja.split("|"), nen.split("|")
        assert len(lj) <= 4 and len(le) <= 4, slug
        for k in range(4):
            lang["ja"][f"sign.skyabyss.poi_note.{slug}.{k + 1}"] = lj[k] if k < len(lj) else ""
            lang["en"][f"sign.skyabyss.poi_note.{slug}.{k + 1}"] = le[k] if k < len(le) else ""
    for i, (bid, ja, en) in D.BROTHERS.items():
        assert pois[i]["kind"] == "bandit_camp", i
        pois[i]["chief"] = bid
        lang["ja"][f"entity.skyabyss.bandit_brother.{bid}"] = ja
        lang["en"][f"entity.skyabyss.bandit_brother.{bid}"] = en
    for i in D.TRAVELERS:
        assert pois[i]["kind"] == "traveler_camp", i

    # 部品のうち、もう使わない場所の部品を消す
    keep = set(built)
    for f in os.listdir(OPENING):
        if f.startswith("poi_") and "__" in f and f[:-5] not in keep:
            os.remove(os.path.join(OPENING, f))

    with open(pois_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("[\n" + ",\n".join("  " + json.dumps(p, ensure_ascii=False) for p in pois) + "\n]\n")

    for code, key in (("ja_jp", "ja"), ("en_us", "en")):
        path = os.path.join(LANG, code + ".json")
        old = json.load(open(path, encoding="utf-8"))
        new = lang[key]
        # 今ある鍵は今の場所のまま書き直し、もう無い鍵 (この道具の頭のもの) は消し、新しい鍵は終わりに足す (前は全部を終わりへ回して並びが崩れた)
        data = {}
        for k, v in old.items():
            if k in new:
                data[k] = new[k]
            elif not k.startswith(PREFIXES):
                data[k] = v
        for k, v in new.items():
            if k not in data:
                data[k] = v
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print(len(built), "sites,", sum(1 for p in pois if p["kind"] == "signpost"), "signposts,", sum(1 for p in pois if p["kind"] == "gone"), "gone")


if __name__ == "__main__":
    main()
