# -*- coding: utf-8 -*-
"""章の跡地の小ダンジョン (docs/design/55 の 6 章。dungeon/MiniDungeons)。彫って data/skyabyss/dungeon/<id>_<1|boss>.json に書く。

  python -X utf8 tools/gen/world/build_mini_dungeons.py [ch1_bone_pit ch1_cleft_shell_cave ch2_salt_vault storm_ruin ch3_scorched_watch ch4_dead_forge_pit]

どれも同じ骨組み (2026-11-07 の頁: 部屋 3 + 主の間。扉は前の部屋の敵を全部倒すと開く):
  層 1   入口の間 (帰り石 leave) → 坑道 → 部屋 A → 扉 1 (door_1_cleara) → 部屋 B (宝箱) → 扉 2 (door_2_clearb) → 部屋 C (宝箱・前の私の荷 pack)
         → 扉 3 (door_3_clearc) → 奥の光 next (主の間へ)
  主の間 入口 entry → 広間 (主 boss) → 奥の壇 (碑 tablet:<碑の鍵>) → 帰りの光 exit
座標は層の手元 (原点 = 入口の立つ所、y 0 が立つ高さ、奥は +z)。印の意味は build_dungeons.py の頭と同じ。foe_<部屋>_<n>_<種> の種は Dungeon.java の表
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import dungeon_kit as K  # noqa: E402
from dungeon_kit import hsh, lantern, logs, slab, stair, fence  # noqa: E402
import build_dungeons as B  # noqa: E402

OUT = K.ROOT / "src/main/resources/data/skyabyss/dungeon"
NOTE = "tools/gen/world/build_mini_dungeons.py が書く。手で直さない"

# 部屋の中心 (z) と大きさ (rx, rz, 天井)
ROOMS = [("a", 18, 9, 8, 7), ("b", 46, 11, 9, 8), ("c", 76, 12, 10, 9)]
DOORS = [("door_1_cleara", 30), ("door_2_clearb", 59), ("door_3_clearc", 90)]
NEXT_Z = 100


# ====================================================================== 土地ごとの見た目
def theme_stone(walls, floors, ceils, accent=None, accent_chance=0.0):
    def wall(x, y, z, band, fossil):
        if accent and fossil > 1 - accent_chance:
            return accent
        return walls[int(band * 3.3 * len(walls) + y * 0.2) % len(walls)]

    def floor(x, y, z, band, fossil):
        return K.pick(floors, x, y, z, 7)

    def ceil(x, y, z, band, fossil):
        return K.pick(ceils, x, y, z, 8)
    return {"wall": wall, "floor": floor, "ceil": ceil}


THEMES = {
    "ch1_bone_pit": dict(
        theme=lambda: B.bone_theme(5101),
        floor_props="mine",
        lamp=lambda: lantern(),
        scatter=["minecraft:brown_mushroom", "minecraft:bone_block[axis=x]", "minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"],
        drips=B.drip, drip_chance=0.04, light=10,
        foes={"a": ["crawler"] * 4, "b": ["beetle"] * 3 + ["archer"] * 2, "c": ["hauler"] * 2 + ["crawler"] * 2 + ["archer"] * 2},
        tablet="sign.skyabyss.ch1_cold_door", pack=True, tablet_block="skyabyss:tablet[facing=south,standing=true]",
        dais="minecraft:polished_deepslate", trim="minecraft:deepslate_bricks"),
    "ch1_cleft_shell_cave": dict(
        theme=lambda: theme_stone(["minecraft:tuff", "minecraft:calcite", "minecraft:stone", "minecraft:tuff", "minecraft:smooth_basalt"],
                                  ["minecraft:tuff", "minecraft:gravel", "minecraft:calcite", "minecraft:stone"],
                                  ["minecraft:tuff", "minecraft:dripstone_block", "minecraft:calcite"], "minecraft:bone_block[axis=y]", 0.12),
        floor_props="shell",
        lamp=lambda: lantern(),
        scatter=["minecraft:pointed_dripstone[thickness=tip,vertical_direction=up,waterlogged=false]", "minecraft:brown_mushroom", "minecraft:small_amethyst_bud[facing=up,waterlogged=false]"],
        drips=B.drip, drip_chance=0.05, light=10,
        foes={"a": ["beetle"] * 4, "b": ["hare"] * 3 + ["archer"] * 2, "c": ["beetle"] * 2 + ["archer"] * 2 + ["crawler"] * 2},
        tablet=None, pack=False, tablet_block=None,
        dais="minecraft:polished_tuff", trim="minecraft:tuff_bricks"),
    "ch2_salt_vault": dict(
        theme=lambda: theme_stone(["minecraft:smooth_sandstone", "minecraft:prismarine_bricks", "minecraft:sandstone", "minecraft:calcite", "minecraft:cut_sandstone"],
                                  ["minecraft:sand", "minecraft:smooth_sandstone", "minecraft:sand", "minecraft:white_concrete_powder"],
                                  ["minecraft:prismarine_bricks", "minecraft:dark_prismarine", "minecraft:calcite"], "minecraft:dead_brain_coral_block", 0.06),
        floor_props="salt",
        lamp=lambda: "minecraft:sea_lantern",
        scatter=["minecraft:dead_tube_coral[waterlogged=false]", "minecraft:sea_pickle[pickles=2,waterlogged=false]", "minecraft:dead_brain_coral[waterlogged=false]"],
        drips=None, drip_chance=0.0, light=11,
        foes={"a": ["clam"] * 4, "b": ["burrower"] * 3 + ["scuttler"] * 2, "c": ["scuttler"] * 3 + ["thrall"] * 2},
        tablet="sign.skyabyss.ch2_salt_wall", pack=False, tablet_block="skyabyss:tablet[facing=south,standing=true]",
        dais="minecraft:smooth_quartz", trim="minecraft:prismarine_bricks"),
    "storm_ruin": dict(
        theme=lambda: theme_stone(["minecraft:polished_diorite", "minecraft:calcite", "minecraft:diorite", "minecraft:smooth_basalt", "minecraft:calcite"],
                                  ["minecraft:polished_diorite", "minecraft:calcite", "minecraft:cut_copper", "minecraft:polished_andesite"],
                                  ["minecraft:calcite", "minecraft:polished_diorite"], "minecraft:oxidized_cut_copper", 0.08),
        floor_props="copper",
        lamp=lambda: lantern(),
        scatter=["minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]", "minecraft:white_carpet"],
        drips=None, drip_chance=0.0, light=11,
        foes={"a": ["puff"] * 4, "b": ["fledgling"] * 3 + ["puff"] * 2, "c": ["gleaner"] * 3 + ["fledgling"] * 2 + ["puff"]},
        tablet="sign.skyabyss.ch3_vault", pack=False, tablet_block="skyabyss:tablet[facing=south,standing=true]",
        dais="minecraft:waxed_cut_copper", trim="minecraft:polished_diorite"),
    "ch3_scorched_watch": dict(
        theme=lambda: theme_stone(["minecraft:polished_blackstone", "minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks", "minecraft:basalt[axis=y]", "minecraft:tuff"],
                                  ["minecraft:deepslate_tiles", "minecraft:coarse_dirt", "minecraft:polished_deepslate", "minecraft:gravel"],
                                  ["minecraft:deepslate_bricks", "minecraft:blackstone"], "minecraft:weathered_cut_copper", 0.06),
        floor_props="scorched",
        lamp=lambda: lantern(),
        scatter=["minecraft:dead_bush", "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]", "minecraft:cobbled_deepslate_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"],
        drips=None, drip_chance=0.0, light=10,
        foes={"a": ["fledgling"] * 4, "b": ["puff"] * 3 + ["fledgling"] * 2, "c": ["gleaner"] * 3 + ["puff"] * 2 + ["fledgling"]},
        tablet=None, pack=False, tablet_block=None,
        dais="minecraft:polished_deepslate", trim="minecraft:deepslate_tiles"),
    "ch4_dead_forge_pit": dict(
        theme=lambda: theme_stone(["minecraft:blackstone", "minecraft:polished_blackstone_bricks", "minecraft:basalt[axis=y]", "minecraft:cracked_polished_blackstone_bricks", "minecraft:blackstone"],
                                  ["minecraft:polished_blackstone", "minecraft:blackstone", "minecraft:smooth_basalt", "minecraft:polished_basalt[axis=y]", "minecraft:blackstone"],
                                  ["minecraft:blackstone", "minecraft:basalt[axis=y]"], "minecraft:gilded_blackstone", 0.03),
        floor_props="forge",
        lamp=lambda: lantern(),
        scatter=["minecraft:polished_blackstone_button[face=floor,facing=north,powered=false]", "minecraft:chain[axis=y,waterlogged=false]"],
        drips=None, drip_chance=0.0, light=9,
        foes={"a": ["mite"] * 5, "b": ["husk"] * 3 + ["mite"] * 2, "c": ["husk"] * 3 + ["mite"] * 3},
        tablet="sign.skyabyss.ch4_forge", pack=True, tablet_block="skyabyss:tablet[facing=south,standing=true]",
        dais="minecraft:polished_blackstone_bricks", trim="minecraft:chiseled_polished_blackstone"),
}


# ====================================================================== 部品
def pillar(cv, x, z, y, h, block, cap=None):
    for k in range(h):
        if cv.is_air(x, y + k, z):
            cv.put(x, y + k, z, block)
    if cap and cv.is_air(x, y + h, z):
        cv.put(x, y + h, z, cap)


def place_foes(cv, room, cx, cz, rx, rz, kinds, seed):
    n = len(kinds)
    for i, kind in enumerate(kinds):
        a = math.radians(i * 360.0 / n + hsh(cx, cz, seed + i) * 25)
        r = 0.55 + 0.2 * hsh(i, seed, 3)
        x = int(round(cx + math.cos(a) * rx * r))
        z = int(round(cz + math.sin(a) * rz * r))
        fy = cv.floor_y(x, z, 0, 4)
        if fy is None:
            fy = 0
        cv.mark(f"foe_{room}_{i + 1}_{kind}", x, fy, z)


def props(cv, kind, cx, cz, rx, rz, seed, th):
    """部屋の中の飾り (土地ごと)。通り道 (x = cx ± 2) はあける"""
    lamp = th["lamp"]()
    trim = th["trim"]
    # 四隅の柱と灯り
    for sx in (-1, 1):
        for sz in (-1, 1):
            x = int(round(cx + sx * rx * 0.62))
            z = int(round(cz + sz * rz * 0.62))
            fy = cv.floor_y(x, z, 0, 4)
            if fy is None:
                continue
            if kind == "mine":
                B.frame(cv, x, fy, z, "z", 1, 4, "spruce", lamp=True)
            else:
                pillar(cv, x, z, fy, 4, trim, lamp if "lantern" not in lamp else None)
                if "lantern" in lamp and cv.is_air(x, fy + 4, z):
                    cv.put(x, fy + 4, z, lamp)
    if kind == "mine":
        B.rails_along(cv, [(cx - 2, 0, cz - rz), (cx - 2, 0, cz + rz)])
        for k in (-1, 1):
            # ずり山は通り道 (x = cx ± 2) の外
            B.stones(cv, int(cx + k * rx * 0.55), cz + rz // 2, 0, 1)
        B.bone_pile(cv, int(cx + rx * 0.4), 0, int(cz - rz * 0.3), seed)
        B.rib(cv, cx, int(cz + rz * 0.85), 0, max(4, rx - 2), "x", 1, 0.7)
    elif kind == "shell":
        for k in range(5):
            a = math.radians(k * 72 + 20)
            x = int(round(cx + math.cos(a) * rx * 0.45))
            z = int(round(cz + math.sin(a) * rz * 0.45))
            if abs(x - cx) <= 2:
                continue
            fy = cv.floor_y(x, z, 0, 4) or 0
            pillar(cv, x, z, fy, 2 + int(hsh(x, z, seed) * 3), "minecraft:calcite", "minecraft:small_amethyst_bud[facing=up,waterlogged=false]")
        B.bone_pile(cv, int(cx - rx * 0.5), 0, int(cz + rz * 0.4), seed)
    elif kind == "salt":
        # 浅い潮だまり (床の一段下に水) と、塩の柱
        for k in range(4):
            a = math.radians(k * 90 + 45)
            px = int(round(cx + math.cos(a) * rx * 0.45))
            pz = int(round(cz + math.sin(a) * rz * 0.45))
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    if abs(px + dx - cx) > 2:
                        cv.put(px + dx, -1, pz + dz, "minecraft:water[level=0]")
            cv.put(px, -2, pz, "minecraft:sea_lantern")
        for k in range(3):
            x = int(cx + (k - 1) * rx * 0.6)
            if abs(x - cx) <= 2:
                continue
            pillar(cv, x, int(cz + rz * 0.75), 0, 3, "minecraft:calcite", "minecraft:white_concrete_powder")
    elif kind == "copper":
        for k in range(4):
            a = math.radians(k * 90)
            x = int(round(cx + math.cos(a) * rx * 0.5))
            z = int(round(cz + math.sin(a) * rz * 0.5))
            if abs(x - cx) <= 2:
                continue
            fy = cv.floor_y(x, z, 0, 4) or 0
            pillar(cv, x, z, fy, 3, "minecraft:waxed_cut_copper", "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
        for x in range(int(cx - rx * 0.7), int(cx + rx * 0.7) + 1):
            if abs(x - cx) > 2 and hsh(x, cz, seed) < 0.5:
                cv.putif_air(x, 0, int(cz - rz * 0.8), "minecraft:white_carpet")
    elif kind == "scorched":
        for k in range(6):
            a = math.radians(k * 60 + 15)
            x = int(round(cx + math.cos(a) * rx * 0.55))
            z = int(round(cz + math.sin(a) * rz * 0.55))
            if abs(x - cx) <= 2:
                continue
            fy = cv.floor_y(x, z, 0, 4) or 0
            pillar(cv, x, z, fy, 1 + int(hsh(x, z, seed) * 3), "minecraft:cracked_deepslate_bricks", "minecraft:campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]" if k % 2 else None)
    elif kind == "forge":
        # 冷えた鍛冶場: 金床・鞴の炉・水の釜・鎖
        for k, (dx, dz) in enumerate(((-0.55, -0.4), (0.55, -0.4), (-0.55, 0.45), (0.55, 0.45))):
            x = int(round(cx + dx * rx))
            z = int(round(cz + dz * rz))
            fy = cv.floor_y(x, z, 0, 4) or 0
            if k == 0:
                cv.putif_air(x, fy, z, "minecraft:damaged_anvil[facing=north]")
            elif k == 1:
                cv.putif_air(x, fy, z, "minecraft:blast_furnace[facing=south,lit=false]")
                cv.putif_air(x, fy + 1, z, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
            elif k == 2:
                cv.putif_air(x, fy, z, "minecraft:water_cauldron[level=2]")
            else:
                cv.putif_air(x, fy, z, "minecraft:smithing_table")
        for k in range(4):
            x = int(cx + (k - 1.5) * rx * 0.5)
            if abs(x - cx) <= 2:
                continue
            for y in range(4, 9):
                cv.putif_air(x, y, int(cz), "minecraft:chain[axis=y,waterlogged=false]")


def hang(cv, x, z, length, block):
    """天井から鎖で吊る灯り (床 y 0 から上へ空きをたどり、天井の下に鎖 length と灯り)"""
    y = 0
    while y < 20 and cv.is_air(x, y + 1, z):
        y += 1
    if y < length + 3:
        return
    for k in range(length):
        cv.put(x, y - k, z, "minecraft:chain[axis=y,waterlogged=false]")
    cv.put(x, y - length, z, block)


def finish(cv, th, seed):
    if th["drips"]:
        cv.ceiling_drips(th["drips"], th["drip_chance"], seed + 101, 3)
    cv.wall_lichen(0.01, seed + 102)
    cv.floor_scatter(th["scatter"], 0.02, seed + 103, only=lambda x, y, z: abs(x) > 2)
    cv.lights(6, th["light"], seed + 104)


# ====================================================================== 層 1 (部屋 3)
def floor1(name, th, seed):
    cv = K.Cave(-26, 26, -6, 16, -10, NEXT_Z + 8, seed)
    # 入口の間
    cv.room(0, 0, 5, 5, 5, 0, seed=seed + 1)
    # 坑道と部屋
    prev = (0, 0, 4)
    for i, (room, cz, rx, rz, h) in enumerate(ROOMS):
        cv.tunnel([prev, (0, 0, cz - rz)], 1.7, 4.0, seed=seed + 10 + i)
        cv.room(0, cz, rx, rz, h, 0, seed=seed + 20 + i, wall_h=4.0)
        prev = (0, 0, cz + rz)
    cv.tunnel([prev, (0, 0, NEXT_Z)], 1.7, 4.0, seed=seed + 30)
    cv.room(0, NEXT_Z, 3, 3, 4, 0, seed=seed + 31)
    cv.paint(th["theme"]())
    cv.mark("entry", 0, 0, 1)
    cv.mark("cp", 0, 0, 1)
    B.leave_stone(cv, -3, 0, -2)
    cv.put(3, 0, -2, th["lamp"]() if "lantern" not in th["lamp"]() else th["trim"])
    if "lantern" in th["lamp"]():
        cv.put(3, 1, -2, th["lamp"]())
    for i, (room, cz, rx, rz, h) in enumerate(ROOMS):
        cv.mark(f"room_{room}", 0, 0, cz)
        props(cv, th["floor_props"], 0, cz, rx, rz, seed + 40 + i, th)
        place_foes(cv, room, 0, cz, rx, rz, th["foes"][room], seed + 50 + i)
    for (door, z) in DOORS:
        # 坑道の幅より広く (粗い壁のすき間からも抜けない)
        B.bars(cv, door, -3, 3, 0, 4, z, z)
    # 宝箱 (部屋 B と C の奥の壁ぎわ)
    for k, (room, cz, rx, rz, h) in enumerate(ROOMS[1:]):
        x = int(rx * 0.7)
        fy = cv.floor_y(x, cz + 2, 0, 4) or 0
        B.chest(cv, f"chest_{k + 1}", x, fy, cz + 2, "west")
    # 前の私の荷 (部屋 C)
    if th["pack"]:
        room, cz, rx, rz, h = ROOMS[2]
        x = -int(rx * 0.7)
        fy = cv.floor_y(x, cz - 2, 0, 4) or 0
        cv.put(x, fy, cz - 2, "minecraft:barrel[facing=up,open=false]")
        cv.mark("pack", x, fy, cz - 2)
    # 温石の台 (消えた炉の坑の部屋 A の西の壁ぎわ。温石を持って触れると台が温まり、熾り蟲が人より台へ寄る。DeadForgePit.touchStand)
    if th["floor_props"] == "forge":
        room, cz, rx, rz, h = ROOMS[0]
        x = -int(rx * 0.75)
        fy = cv.floor_y(x, cz, 0, 4) or 0
        cv.put(x, fy, cz, "minecraft:basalt[axis=y]")
        cv.put(x, fy + 1, cz, "minecraft:smooth_basalt")
        cv.mark("warm_stand", x, fy + 1, cz)
    cv.mark("next", 0, 0, NEXT_Z)
    cv.put(0, -1, NEXT_Z, "minecraft:crying_obsidian" if "forge" in th["floor_props"] else "minecraft:sea_lantern")
    finish(cv, th, seed)
    return cv


# ====================================================================== 主の間
def boss_floor(name, th, seed):
    cv = K.Cave(-24, 24, -6, 20, -26, 34, seed + 500)
    cv.tunnel([(0, 0, 28), (0, 0, 15)], 1.8, 4.2, seed=seed + 501)
    cv.room(0, 0, 15, 15, 11, 0, seed=seed + 502, wall_h=6.0)
    cv.tunnel([(0, 0, -14), (0, 0, -20)], 1.6, 4.0, seed=seed + 503)
    cv.room(0, -21, 3, 3, 5, 0, seed=seed + 504)
    cv.paint(th["theme"]())
    cv.mark("entry", 0, 0, 27)
    cv.mark("cp", 0, 0, 27)
    B.leave_stone(cv, -2, 0, 29)
    cv.mark("boss", 0, 0, 0)
    cv.mark("exit", 0, 0, -21)
    # 奥の壇と碑
    for x in range(-4, 5):
        for z in range(-14, -10):
            cv.put(x, -1, z, th["dais"])
    for x in (-5, 5):
        pillar(cv, x, -12, 0, 6, th["trim"], th["lamp"]() if "lantern" not in th["lamp"]() else None)
        if "lantern" in th["lamp"]():
            cv.put(x, 6, -12, th["lamp"]())
    if th["tablet"]:
        cv.put(0, 0, -12, th["tablet_block"])
        cv.mark("tablet:" + th["tablet"], 0, 0, -12)
    # 広間の柱 (八本) と飾り
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        x = int(round(math.cos(a) * 11))
        z = int(round(math.sin(a) * 11))
        fy = cv.floor_y(x, z, 0, 4) or 0
        pillar(cv, x, z, fy, 7, th["trim"], th["lamp"]() if "lantern" not in th["lamp"]() else None)
        if "lantern" in th["lamp"]():
            cv.put(x, fy + 7, z, th["lamp"]())
    props(cv, th["floor_props"], 0, 0, 13, 13, seed + 520, th)
    # 吊り灯り (広間の輪 2 つ) と、撮影・開発の見る所 (壇の前から碑へ)
    for k in range(8):
        a = math.radians(k * 45)
        hang(cv, int(round(math.cos(a) * 6)), int(round(math.sin(a) * 6)), 3, lantern(hang=True))
    for k in range(12):
        a = math.radians(k * 30 + 15)
        hang(cv, int(round(math.cos(a) * 12.5)), int(round(math.sin(a) * 12.5)), 2, lantern(hang=True))
    cv.mark("view_dais", 0, 0, -4)
    cv.mark("dais", 0, 1, -12)
    finish(cv, th, seed + 500)
    return cv


BUILDERS = {name: [("1", (lambda n=name: floor1(n, THEMES[n], 6000 + i * 97))), ("boss", (lambda n=name: boss_floor(n, THEMES[n], 6000 + i * 97)))]
            for i, name in enumerate(THEMES)}

if __name__ == "__main__":
    names = sys.argv[1:] or list(BUILDERS)
    for name in names:
        for part, fn in BUILDERS[name]:
            cv = fn()
            kb, cells, runs = cv.write(OUT / f"{name}_{part}.json", NOTE)
            print(f"{name}_{part}", kb, "KB", cells, "cells", runs, "runs", len(cv.marks), "marks", flush=True)
