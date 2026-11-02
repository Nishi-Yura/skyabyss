# -*- coding: utf-8 -*-
"""地下のダンジョンの入口 (大陸の見どころ data/skyabyss/opening/poi_dungeon_<id>.json。docs/design/55 の 2 章)。build_dungeons.py から呼ぶ。

地上の岩の口から階段の坑で下り、本物の地下の大洞窟 (入口の間) へ。範囲 (meta.rect) は地上をならす口の前だけで、
地下の坑と入口の間は範囲の外に書く (SiteJob は範囲の外のブロックもそのまま置く)。前 (南) は一番近い道へ向く (ContinentPois.faceRoad)。
印 (ContinentPois が見どころの extra に覚える): gate = 層の門の天然磁石、back = 帰り着く所、rumor = 噂の主の立つ所、rest = 休み場、tablet = 名前の碑
"""
import json
import math

import numpy as np

import build_dungeons as B
import dungeon_kit as K
from dungeon_kit import AIR, CHAIN_Y, lantern, logs, stair

OPENING = K.ROOT / "src/main/resources/data/skyabyss/opening"
HALL_FLOOR = -25


class Cells:
    """見どころの設計図 (blocks の書式) のセル。poi_kit の形の関数 (set / get / clear …) がそのまま使える"""

    def __init__(self):
        self.cells = {}
        self.marks = {}

    def set(self, x, y, z, s):
        self.cells[(x, y, z)] = s

    def setif(self, x, y, z, s):
        if self.cells.get((x, y, z)) in (None, AIR):
            self.cells[(x, y, z)] = s

    def get(self, x, y, z):
        return self.cells.get((x, y, z))

    def fill(self, x0, y0, z0, x1, y1, z1, s):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.cells[(x, y, z)] = s

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, AIR)

    def save(self, name, rect, ring=6):
        palette, idx, blocks = [], {}, []
        for (x, y, z), s in sorted(self.cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
            if s not in idx:
                idx[s] = len(palette)
                palette.append(s)
            blocks.append([x, y, z, idx[s]])
        data = {"site": name, "note": "地下のダンジョンの入口 (docs/design/55)。tools/gen/world/dungeon_entrances.py が書く。手で直さない",
                "palette": palette, "blocks": blocks, "marks": self.marks, "meta": {"rect": rect, "level": True, "round": True, "ring": ring}}
        out = OPENING / f"{name}.json"
        out.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
        return out.stat().st_size // 1024, len(blocks)


def entrance(dungeon, theme, seed, mouth, hall_decor, stair_block="stone_brick", side_block="minecraft:polished_andesite"):
    """共通の形: 口の前の平地 → 岩山に開いた坑口 → 北へ下る階段の坑 (途中に踊り場) → 入口の間 (大洞窟) → 北の壁の層の門。
    mouth(c, PK) が地上の口を、hall_decor(c, cv) が入口の間の飾りを作る"""
    import poi_kit as PK
    c = Cells()
    cv = K.Cave(-30, 30, HALL_FLOOR - 4, 4, -80, 6, seed)
    cv.tunnel([(0, -1, -3), (0, -1, -6)], 2.2, 4.6, seed=seed + 1)
    y, z = -1, -6
    steps = []
    while y > HALL_FLOOR:
        steps.append((z, y))
        if y == -13:
            for _ in range(3):
                z -= 1
                steps.append((z, y))
        z -= 1
        y -= 1
    for (sz, sy) in steps:
        cv.fill_air(-2, 2, sy, sy + 4, sz, sz)
    cv.room(0, -50, 17, 14, 12, HALL_FLOOR, seed=seed + 2, wall_h=5)
    cv.tunnel([(0, HALL_FLOOR, z), (0, HALL_FLOOR, -38)], 2.6, 5.0, seed=seed + 3)
    # 層の門の奥 (暗い坑が少し続いて曲がる)
    cv.tunnel([(0, HALL_FLOOR, -62), (0, HALL_FLOOR, -70), (-5, HALL_FLOOR - 1, -75)], 2.2, 4.6, seed=seed + 4)
    cv.paint(theme)
    # 地面より上には殻を書かない (地上は口の部品だけ)
    for k in [k for k in cv.mats if k[1] >= -1]:
        del cv.mats[k]
    hall_decor(c, cv)
    for (sz, sy) in steps:
        for x in range(-1, 2):
            c.set(x, sy, sz, stair(stair_block, "south"))
        for x in (-2, 2):
            c.set(x, sy, sz, side_block)
        for x in range(-2, 3):
            c.set(x, sy - 1, sz, side_block)
        if sy % 4 == 0:
            for x in (-2, 2):
                c.set(x, sy + 1, sz, side_block)
                c.set(x, sy + 2, sz, lantern())
    # 彫った空き・壁の殻・飾りを見どころのセルへ (y -1 より上は口の部品に任せる)
    xs, ys, zs = np.nonzero(cv.air)
    for i in range(len(xs)):
        p = (int(xs[i]) + cv.x0, int(ys[i]) + cv.y0, int(zs[i]) + cv.z0)
        if p[1] < 0:
            c.setif(*p, AIR)
    for p, s in cv.mats.items():
        if p not in c.cells:
            c.cells[p] = s
    for p, s in cv.blk.items():
        if c.cells.get(p) in (None, AIR):
            c.cells[p] = s
    mouth(c, PK)
    return c.save("poi_dungeon_" + dungeon, [-9, -6, 9, 9])


# ====================================================================== 鯨骨の坑
def bone_pit_mouth(c, PK):
    """岩山に開いた坑口、左右から前へかぶさる巨神の肋骨の門、線路と樽、灯の柱、名前の碑"""
    layers = [(1, ["minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:andesite"]), (4, ["minecraft:stone", "minecraft:andesite", "minecraft:tuff"]),
              (99, ["minecraft:stone", "minecraft:andesite", "minecraft:diorite"])]
    PK.ground(c, 9, [("minecraft:gravel", 2), ("minecraft:coarse_dirt", 3), ("minecraft:dirt_path", 2), ("minecraft:grass_block[snowy=false]", 1)], 2101)
    PK.rock(c, 0, 3.0, -8.0, 9.5, 6.0, 5.0, layers, 2102, 0.3, below=False)
    PK.rock(c, -8, 1.5, -4.0, 3.2, 3.0, 3.0, layers, 2103, 0.4, below=False)
    PK.rock(c, 8, 2.0, -5.0, 3.4, 3.6, 3.2, layers, 2104, 0.4, below=False)
    c.clear(-2, 0, -12, 2, 4, 1)
    for x in range(-2, 3):
        for z in (0, -1):
            c.set(x, -1, z, "minecraft:gravel")
        c.set(x, -1, -2, "minecraft:stone_bricks")
    for z in (0, -3, -6):
        PK.column(c, -3, z, -1, 3, "minecraft:spruce_log[axis=y]")
        PK.column(c, 3, z, -1, 3, "minecraft:spruce_log[axis=y]")
        for x in range(-3, 4):
            c.set(x, 4, z, "minecraft:spruce_log[axis=x]")
    c.set(0, 3, 0, CHAIN_Y)
    c.set(0, 2, 0, lantern(True))
    for s in (-1, 1):
        for k in range(0, 91, 3):
            t = math.radians(k)
            x = int(round(s * (6 - 3.5 * math.sin(t))))
            yy = int(round(-1 + 8 * math.sin(t)))
            zz = int(round(2 - 3 * math.cos(t)))
            c.set(x, yy, zz, "minecraft:bone_block[axis=y]")
            c.set(x, yy + 1, zz, "minecraft:bone_block[axis=y]")
    for zz in range(1, 8):
        c.set(0, 0, zz, "minecraft:rail[shape=north_south,waterlogged=false]")
    PK.rock(c, 5, -0.2, 5, 2.0, 1.0, 1.8, [(99, ["minecraft:gravel", "minecraft:cobblestone", "minecraft:coal_ore", "minecraft:bone_block[axis=y]"])], 2105, 0.5, below=False)
    for x, zz in ((-5, 4), (-6, 5)):
        c.set(x, 0, zz, "minecraft:barrel[facing=up,open=false]")
    for x in (-4, 4):
        PK.column(c, x, 6, 0, 2, PK.fence("spruce"))
        c.set(x, 3, 6, "minecraft:stripped_spruce_log[axis=y]")
        c.set(x, 3, 7, PK.fence("spruce"))
        c.set(x, 2, 7, lantern(True))
    c.set(3, 0, 3, "skyabyss:tablet[facing=south,standing=true]")
    c.marks["tablet"] = [3, 0, 3]


def bone_pit_hall(c, cv):
    """入口の間: 北の壁に層の門 (骨の弧と天然磁石の台)、西に鉱夫の休み場、東に噂の主の立つ所、柱の木組みと吊り灯、天井に巨神の肋骨"""
    F = HALL_FLOOR
    for k in range(0, 181, 3):
        t = math.radians(k)
        x = int(round(5 * math.cos(t)))
        y = int(round(F + 7 * math.sin(t)))
        for z in (-61, -62):
            c.cells[(x, y, z)] = "minecraft:bone_block[axis=y]"
    for x in range(-2, 3):
        for z in (-58, -57, -56):
            c.cells[(x, F - 1, z)] = "minecraft:polished_deepslate"
    c.cells[(0, F, -57)] = "minecraft:lodestone"
    c.marks["gate"] = [0, F, -57]
    for x in (-2, 2):
        c.cells[(x, F, -58)] = "minecraft:chiseled_stone_bricks"
        c.cells[(x, F + 1, -58)] = lantern(False, True)
    c.marks["back"] = [0, F, -53]
    c.cells[(-9, F, -46)] = "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]"
    for x, z, f in ((-11, -46, "east"), (-7, -46, "west"), (-9, -48, "south"), (-9, -44, "north")):
        c.cells[(x, F, z)] = stair("spruce", f)
    for x, z in ((-13, -42), (-13, -43), (-12, -42)):
        c.cells[(x, F, z)] = "minecraft:barrel[facing=up,open=false]"
    c.cells[(-13, F + 1, -42)] = lantern()
    c.marks["rest"] = [-9, F, -46]
    c.marks["rumor"] = [9, F, -45]
    for x, z in ((12, -44), (12, -45)):
        c.cells[(x, F, z)] = "minecraft:spruce_planks"
    c.cells[(12, F + 1, -44)] = "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]"
    c.cells[(12, F + 1, -45)] = lantern()
    for (x, z) in ((-6, -40), (6, -40), (-6, -54), (6, -54)):
        for y in range(F, F + 7):
            if cv.is_air(x, y, z):
                c.cells[(x, y, z)] = logs("spruce_log")
    for (x, z) in ((0, -44), (-8, -52), (8, -52)):
        for y in range(F + 6, F + 11):
            if cv.is_air(x, y, z):
                c.cells[(x, y, z)] = CHAIN_Y
        c.cells[(x, F + 5, z)] = lantern(True)
    for z in (-42, -50):
        B.rib(cv, 0, z, F, 15, "x", 1, 0.62)
    cv.ceiling_drips(B.drip, 0.03, 2110, 3)
    cv.wall_lichen(0.01, 2111)
    cv.lights(6, 11, 2112)


ENTRANCES = {
    "bone_pit": lambda: entrance("bone_pit", B.bone_theme(2100), 2100, bone_pit_mouth, bone_pit_hall),
}


# ====================================================================== 潮の洞・雷晶の空洞・熔けた坑道 (口と入口の間は同じ組み立てで、材料と飾りを替える)
def themed_mouth(layers, ground_mix, frame_log, frame_beam, arch_block, seed, extras=None):
    def mouth(c, PK):
        PK.ground(c, 9, ground_mix, seed + 1)
        PK.rock(c, 0, 3.0, -8.0, 9.5, 6.0, 5.0, layers, seed + 2, 0.3, below=False)
        PK.rock(c, -8, 1.5, -4.0, 3.2, 3.0, 3.0, layers, seed + 3, 0.4, below=False)
        PK.rock(c, 8, 2.0, -5.0, 3.4, 3.6, 3.2, layers, seed + 4, 0.4, below=False)
        c.clear(-2, 0, -12, 2, 4, 1)
        for x in range(-2, 3):
            for z in (0, -1, -2):
                c.set(x, -1, z, arch_block)
        for z in (0, -3, -6):
            PK.column(c, -3, z, -1, 3, frame_log)
            PK.column(c, 3, z, -1, 3, frame_log)
            for x in range(-3, 4):
                c.set(x, 4, z, frame_beam)
        c.set(0, 3, 0, CHAIN_Y)
        c.set(0, 2, 0, lantern(True))
        for x in (-4, 4):
            PK.column(c, x, 6, 0, 2, frame_log)
            c.set(x, 3, 6, lantern())
        c.set(3, 0, 3, "skyabyss:tablet[facing=south,standing=true]")
        c.marks["tablet"] = [3, 0, 3]
        if extras:
            extras(c, PK)
    return mouth


def themed_hall(arch, dais, side, lamp_soul, pillar, decor):
    def hall(c, cv):
        F = HALL_FLOOR
        for k in range(0, 181, 3):
            t = math.radians(k)
            x = int(round(5 * math.cos(t)))
            y = int(round(F + 7 * math.sin(t)))
            for z in (-61, -62):
                c.cells[(x, y, z)] = arch
        for x in range(-2, 3):
            for z in (-58, -57, -56):
                c.cells[(x, F - 1, z)] = dais
        c.cells[(0, F, -57)] = "minecraft:lodestone"
        c.marks["gate"] = [0, F, -57]
        for x in (-2, 2):
            c.cells[(x, F, -58)] = side
            c.cells[(x, F + 1, -58)] = lantern(False, lamp_soul)
        c.marks["back"] = [0, F, -53]
        c.cells[(-9, F, -46)] = "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]"
        for x, z, f in ((-11, -46, "east"), (-7, -46, "west"), (-9, -48, "south"), (-9, -44, "north")):
            c.cells[(x, F, z)] = stair("spruce", f)
        c.marks["rest"] = [-9, F, -46]
        c.marks["rumor"] = [9, F, -45]
        for (x, z) in ((-6, -40), (6, -40), (-6, -54), (6, -54)):
            for y in range(F, F + 7):
                if cv.is_air(x, y, z):
                    c.cells[(x, y, z)] = pillar
        for (x, z) in ((0, -44), (-8, -52), (8, -52)):
            for y in range(F + 6, F + 11):
                if cv.is_air(x, y, z):
                    c.cells[(x, y, z)] = CHAIN_Y
            c.cells[(x, F + 5, z)] = lantern(True, lamp_soul)
        decor(c, cv)
        cv.wall_lichen(0.01, 2211)
        cv.lights(6, 11, 2212)
    return hall


def tide_hall_decor(c, cv):
    F = HALL_FLOOR
    for x in range(-14, -4):
        for z in range(-56, -50):
            if cv.is_air(x, F, z) and (x, F - 1, z) in cv.mats:
                cv.mats[(x, F - 1, z)] = "minecraft:water[level=0]"
    for (x, z) in ((12, -44), (12, -45)):
        c.cells[(x, F, z)] = "minecraft:barrel[facing=up,open=false]"
    c.cells[(12, F + 1, -44)] = lantern()


def storm_hall_decor(c, cv):
    import dungeons_more as D
    for (x, z, h) in ((-12, -44, 6), (12, -54, 7), (-10, -56, 5)):
        D.spire(cv, x, HALL_FLOOR, z, h)
    for (x, z) in ((12, -44), (12, -45)):
        c.cells[(x, HALL_FLOOR, z)] = "minecraft:oxidized_cut_copper"
    c.cells[(12, HALL_FLOOR + 1, -44)] = "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]"


def ember_hall_decor(c, cv):
    import dungeons_more as D
    for (x, z) in ((-12, -42), (12, -56)):
        D.forge_ruin(cv, x, HALL_FLOOR, z)
    for (x, z) in ((12, -44), (12, -45)):
        c.cells[(x, HALL_FLOOR, z)] = "minecraft:blackstone"
    c.cells[(12, HALL_FLOOR + 1, -44)] = "minecraft:lava_cauldron"


def _entrances():
    import dungeons_more as D
    tide_layers = [(1, ["minecraft:sand", "minecraft:sandstone", "minecraft:calcite"]), (4, ["minecraft:calcite", "minecraft:diorite", "minecraft:stone"]),
                   (99, ["minecraft:calcite", "minecraft:diorite", "minecraft:tuff"])]
    storm_layers = [(1, ["minecraft:cobbled_deepslate", "minecraft:gravel", "minecraft:tuff"]), (4, ["minecraft:deepslate", "minecraft:tuff", "minecraft:calcite"]),
                    (99, ["minecraft:deepslate", "minecraft:smooth_basalt", "minecraft:tuff"])]
    ember_layers = [(1, ["minecraft:blackstone", "minecraft:basalt[axis=y]", "minecraft:tuff"]), (4, ["minecraft:basalt[axis=y]", "minecraft:blackstone", "minecraft:smooth_basalt"]),
                    (99, ["minecraft:basalt[axis=y]", "minecraft:smooth_basalt", "minecraft:blackstone"])]
    return {
        "tide_cave": lambda: entrance("tide_cave", D.tide_theme(2200), 2200,
                                      themed_mouth(tide_layers, [("minecraft:sand", 4), ("minecraft:gravel", 2), ("minecraft:sandstone", 1)],
                                                   "minecraft:stripped_spruce_log[axis=y]", "minecraft:stripped_spruce_log[axis=x]", "minecraft:smooth_sandstone", 2201),
                                      themed_hall("minecraft:prismarine_bricks", "minecraft:dark_prismarine", "minecraft:prismarine_bricks", False,
                                                  logs("stripped_spruce_log"), tide_hall_decor), "sandstone", "minecraft:smooth_sandstone"),
        "storm_hollow": lambda: entrance("storm_hollow", D.storm_theme(2300), 2300,
                                         themed_mouth(storm_layers, [("minecraft:cobbled_deepslate", 3), ("minecraft:gravel", 2), ("minecraft:tuff", 2)],
                                                      "minecraft:oxidized_cut_copper", "minecraft:oxidized_cut_copper", "minecraft:polished_deepslate", 2301),
                                         themed_hall("minecraft:amethyst_block", "minecraft:polished_deepslate", "minecraft:oxidized_cut_copper", True,
                                                     "minecraft:oxidized_cut_copper", storm_hall_decor), "deepslate_brick", "minecraft:polished_deepslate"),
        "molten_mine": lambda: entrance("molten_mine", D.ember_theme(2400), 2400,
                                        themed_mouth(ember_layers, [("minecraft:blackstone", 3), ("minecraft:coarse_dirt", 2), ("minecraft:basalt[axis=y]", 1)],
                                                     "minecraft:dark_oak_log[axis=y]", "minecraft:dark_oak_log[axis=x]", "minecraft:polished_blackstone_bricks", 2401),
                                        themed_hall("minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone", "minecraft:chiseled_polished_blackstone", True,
                                                    logs("dark_oak_log"), ember_hall_decor), "polished_blackstone_brick", "minecraft:polished_blackstone"),
    }


ENTRANCES.update(_entrances())
