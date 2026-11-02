# -*- coding: utf-8 -*-
"""地下のダンジョンの二つ目から四つ目 (潮の洞・雷晶の空洞・熔けた坑道。docs/design/55 の 3 章)。build_dungeons.py から読む。
印の約束は build_dungeons.py の頭と同じ。仕掛けの印:
  tide_<n>_lo / _hi            満ち引きの箱 (潮の洞。Mechanics が 70 秒で水と空きを入れ替える)
  crystal_<k>_<扉>             雷の結晶 (雷晶の空洞。3 回当てると満ち、同じ扉の結晶がみな満ちると door_<扉>_crystal が開く。主の間の結晶は大蜥蜴の溜まりを抜く)
  lava_<n>_<a|b>_lo / _hi      入れ替わる溶岩の箱 (熔けた坑道。a と b が 20 秒ごとに逆になる)
"""
import math

import dungeon_kit as K
from dungeon_kit import AIR, CHAIN_Y, fence, frame, hsh, lantern, logs, slab, stair
import build_dungeons as B


def water_basin(cv, x0, x1, y0, y1, z0, z1, block="minecraft:water[level=0]"):
    """彫った空きの一部を、いつも水 (飾りのたまり) にする"""
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                if cv.is_air(x, y, z):
                    cv.put(x, y, z, block)


def box_mark(cv, name, x0, x1, y0, y1, z0, z1):
    cv.mark(name + "_lo", x0, y0, z0)
    cv.mark(name + "_hi", x1, y1, z1)


def end_room(cv, x, y, z, seed, next_block):
    cv.room(x, z, 6, 5, 6, y, seed=seed)
    cv.mark("next", x, y, z + 2)
    return next_block


def common_marks(cv, entry=(0, 0, -2), cp=(3, 0, -4), leave=(-3, 0, -4)):
    cv.mark("entry", *entry)
    B.checkpoint_stone(cv, *cp)
    cv.mark("cp", cp[0], cp[1] + 1, cp[2])
    B.leave_stone(cv, *leave)


# ====================================================================== 潮の洞 (潮の浜、Lv 16)
TC_WALL = ["minecraft:calcite", "minecraft:diorite", "minecraft:stone", "minecraft:tuff", "minecraft:calcite", "minecraft:smooth_basalt", "minecraft:diorite"]


def tide_theme(seed):
    def wall(x, y, z, band, fossil):
        if fossil > 0.82:
            return "minecraft:prismarine" if hsh(x, y, z, seed) < 0.6 else "minecraft:dark_prismarine"
        if hsh(x, y, z, seed + 5) < 0.02:
            return "minecraft:sea_lantern"
        return TC_WALL[int(band * 3.2 * len(TC_WALL) + y * 0.18) % len(TC_WALL)]

    def floor(x, y, z, band, fossil):
        # 砂を主に、砂利・粘土・方解石の斑 (濃い泥と苔は使わない。筋が目立つ)
        if hsh(x, y, z, seed + 9) < 0.08:
            return K.pick(["minecraft:gravel", "minecraft:clay", "minecraft:sand"], x, y, z, seed + 7)
        k = (band * 2.0) % 1.0
        return ("minecraft:sand" if k < 0.55 else "minecraft:gravel" if k < 0.72 else "minecraft:clay" if k < 0.86 else "minecraft:smooth_sandstone")

    def ceil(x, y, z, band, fossil):
        if fossil > 0.84:
            return "minecraft:prismarine"
        return K.pick(["minecraft:calcite", "minecraft:diorite", "minecraft:dripstone_block", "minecraft:tuff"], x, y, z, seed + 8)
    return {"wall": wall, "floor": floor, "ceil": ceil}


def tide_finish(cv, seed):
    B.finish_common(cv, seed, light_step=6, light_level=11, drips=0.03, lichen=0.02, scatter=0.0)
    cv.floor_scatter(["minecraft:sea_pickle[pickles=2,waterlogged=false]", "minecraft:dead_tube_coral[waterlogged=false]",
                      "minecraft:dead_brain_coral_fan[waterlogged=false]", "minecraft:brown_mushroom"], 0.03, seed + 11)


def kelp_in(cv, x, z, y0, y1):
    for y in range(y0, y1):
        cv.put(x, y, z, "minecraft:kelp_plant")
    cv.put(x, y1, z, "minecraft:kelp[age=10]")


def tide_cave_1():
    """一層目: 満ち引きの洞。入口 → 坑道 → 潮だまりの大洞 (A) → 満ち引きで沈む低い抜け道 (tide_1) → 岩棚の洞 (B、鍵持ちの磯の番人) → 扉 → 海の門"""
    cv = K.Cave(-36, 44, -12, 24, -12, 120, 2011)
    cv.room(0, 0, 7, 6, 6, 0, seed=11)
    t1 = [(0, 0, 4), (4, -1, 14), (2, -2, 24), (0, -3, 32)]
    cv.tunnel(t1, 2.0, 4.4, seed=12)
    cv.room(0, 46, 16, 13, 12, -3, seed=13, wall_h=5)
    # 潮だまり (いつも水の飾りのたまり)
    cv.fill_air(-7, 7, -6, -4, 41, 51)
    # 低い抜け道 (潮が満ちると沈む)
    low = [(0, -3, 57), (0, -6, 62), (0, -6, 70), (2, -3, 75)]
    cv.tunnel(low, 2.0, 3.4, seed=14)
    cv.room(4, 88, 13, 11, 13, -3, seed=15, wall_h=5)
    cv.tunnel([(4, -3, 98), (4, -3, 106)], 2.2, 4.4, seed=16)
    cv.room(4, 112, 6, 5, 7, -3, seed=17)
    cv.paint(tide_theme(2011))
    common_marks(cv)
    # 潮だまりの水と昆布
    water_basin(cv, -7, 7, -6, -4, 41, 51)
    for (x, z) in ((-4, 44), (3, 48), (5, 43), (-2, 50)):
        kelp_in(cv, x, z, -6, -4)
    cv.mark("room_a", 0, -3, 46)
    cv.mark("foe_a_1_scuttler", -9, -3, 40)
    cv.mark("foe_a_2_scuttler", 9, -3, 52)
    cv.mark("foe_a_3_clam", -10, -3, 52)
    cv.mark("foe_a_4_hermit", 8, -3, 38)
    cv.mark("foe_a_5_angler", 0, -3, 56)
    B.chest(cv, "chest_1", -12, -3, 46, "east")
    cv.mark("view_pool", -10, 1, 36)
    cv.mark("view_pool_to", 4, -4, 52)
    # 満ち引きの箱 (低い抜け道)
    box_mark(cv, "tide_1", -2, 2, -6, -3, 59, 72)
    for (x, z) in ((-2, 60), (2, 66), (-1, 71)):
        cv.put(x, -6, z, "minecraft:sea_pickle[pickles=3,waterlogged=false]")
    cv.mark("trap_1_brine", 2, -3, 77)
    # 岩棚の洞 B: 東に岩の段、鍵持ちの番人
    for x in range(10, 16):
        for z in range(80, 96):
            for y in range(-3, 1):
                if cv.is_air(x, y, z):
                    cv.put(x, y, z, K.pick(TC_WALL, x, y, z, 77))
    cv.mark("room_b", 4, -3, 88)
    cv.mark("foe_b_1_warden_key", 12, 1, 88)
    cv.mark("foe_b_2_scuttler", 0, -3, 84)
    cv.mark("foe_b_3_hermit", 6, -3, 92)
    cv.mark("foe_b_4_angler", -4, -3, 90)
    B.chest(cv, "chest_2", 14, 1, 82, "west")
    for x in range(10, 16):
        cv.put(x, 0, 96, fence("spruce"))
    for z in range(80, 96, 3):
        cv.put(9, -3, z, "minecraft:spruce_planks")
        cv.put(9, -2, z, "minecraft:spruce_trapdoor[facing=west,half=bottom,open=false,powered=false,waterlogged=false]")
    # 段への坂
    for k, z in enumerate(range(97, 93, -1)):
        cv.put(9 - k * 0, -3 + k, z, stair("stone_brick", "north"))
    cv.mark("view_shelf", -4, 0, 80)
    cv.mark("view_shelf_to", 12, 1, 90)
    B.bars(cv, "door_1_key", 2, 6, -3, 0, 99, 99)
    # 海の門 (次の層へ): 磨いた石の輪と海の灯
    for a in range(0, 360, 30):
        x = 4 + int(round(2.4 * math.cos(math.radians(a))))
        z = 113 + int(round(2.4 * math.sin(math.radians(a))))
        cv.put(x, -4, z, "minecraft:prismarine_bricks")
    cv.put(4, -4, 113, "minecraft:sea_lantern")
    cv.mark("next", 4, -3, 113)
    tide_finish(cv, 2011)
    return cv


def wreck(cv, x0, z0, z1, y, lean=1):
    """沈んだ船 (黒い樫の胴と甲板、折れた帆柱)。x0 = 竜骨の x、z0..z1 = 船首から船尾、y = 竜骨の高さ。lean = 傾く側"""
    deck = y + 4
    for z in range(z0, z1 + 1):
        t = (z - z0) / max(1, z1 - z0)
        half = 1 + int(round(3.6 * math.sin(math.pi * min(1.0, t * 1.15))))
        for dx in range(-half, half + 1):
            # 胴の底は弧 (竜骨が低い)、傾く側が少し沈む
            bottom = y + int(round(abs(dx) * 0.8)) - (1 if dx * lean > 0 else 0)
            for yy in range(bottom, deck):
                edge = abs(dx) == half or yy == bottom
                if edge:
                    cv.put(x0 + dx, yy, z, "minecraft:dark_oak_planks" if hsh(dx, yy, z) < 0.85 else "minecraft:stripped_dark_oak_log[axis=z]")
                else:
                    cv.put(x0 + dx, yy, z, AIR)
            # 甲板 (ところどころ抜けている)
            if hsh(dx, z, 77) < 0.86:
                cv.put(x0 + dx, deck, z, "minecraft:dark_oak_slab[type=bottom,waterlogged=false]" if abs(dx) < half else "minecraft:dark_oak_planks")
        # 舷の手すり
        if z % 2 == 0:
            for dx in (-half, half):
                cv.put(x0 + dx, deck + 1, z, "minecraft:dark_oak_fence[east=false,north=true,south=true,waterlogged=false,west=false]")
    # 船首の竜骨の反り
    for k in range(4):
        cv.put(x0, y + 2 + k, z0 - k, "minecraft:stripped_dark_oak_log[axis=y]")
    # 折れた帆柱と帆桁
    mz = (z0 + z1) // 2
    for k in range(1, 9):
        cv.put(x0 + (k // 4) * lean, deck + k, mz, "minecraft:dark_oak_log[axis=y]")
    for dx in range(-3, 4):
        cv.put(x0 + 2 * lean + dx, deck + 6, mz + 1, "minecraft:dark_oak_log[axis=x]")
    for k in range(3):
        cv.put(x0 + 2 * lean - 3 + k, deck + 5 - k, mz + 1, "minecraft:white_wool" if k else "minecraft:cobweb")


def tide_cave_2():
    """二層目: 沈んだ船の淵。着き場 → 船の沈む大洞 (海の湖、船の甲板が道) → 満ち引きの抜け道 (tide_1) → 中ボスの洞 (潮招きの大蟹) → 扉 → 海の門"""
    cv = K.Cave(-42, 42, -12, 30, -40, 76, 2021)
    cv.room(0, -30, 7, 6, 6, 0, seed=21)
    cv.tunnel([(0, 0, -26), (0, 0, -18)], 2.2, 4.4, seed=22)
    cv.room(0, 0, 22, 17, 18, 0, seed=23, wall_h=6)
    cv.fill_air(-20, -2, -4, -1, -12, 14)
    low = [(0, 0, 18), (0, -3, 24), (0, -3, 30), (0, 0, 35)]
    cv.tunnel(low, 2.0, 3.4, seed=24)
    cv.room(0, 48, 13, 11, 11, 0, seed=25, wall_h=5)
    cv.tunnel([(0, 0, 58), (0, 0, 66)], 2.0, 4.2, seed=26)
    cv.room(0, 70, 6, 5, 7, 0, seed=27)
    cv.paint(tide_theme(2021))
    common_marks(cv, (0, 0, -32), (3, 0, -34), (-3, 0, -34))
    # 海の湖と沈んだ船 (甲板が道になる)
    water_basin(cv, -20, -2, -4, -1, -12, 14)
    wreck(cv, -10, -12, 14, -3)
    for (x, z) in ((-17, -6), (-15, 8), (-5, 10), (-4, -9)):
        kelp_in(cv, x, z, -4, -1)
    cv.mark("room_c", 6, 0, 0)
    cv.mark("foe_c_1_scuttler", 8, 0, -6)
    cv.mark("foe_c_2_hermit", 10, 0, 6)
    cv.mark("foe_c_3_angler", -10, 1, 0)
    cv.mark("foe_c_4_clam", 14, 0, 0)
    B.chest(cv, "chest_1", -10, 0, -2, "east")
    cv.mark("view_wreck", 14, 8, -12)
    cv.mark("view_wreck_to", -10, 0, 2)
    box_mark(cv, "tide_1", -2, 2, -3, 0, 20, 33)
    cv.mark("room_mid", 0, 0, 48)
    cv.mark("foe_mid_1_mid", 0, 0, 50)
    for (x, z) in ((-8, 42), (8, 54), (-6, 56)):
        B.stones(cv, x, z, 0, 1, ("minecraft:calcite", "minecraft:prismarine", "minecraft:gravel"))
    B.chest(cv, "chest_2", 10, 0, 44, "west")
    cv.mark("view_mid", -8, 4, 40)
    cv.mark("view_mid_to", 4, 1, 52)
    B.bars(cv, "door_2_mid", -2, 2, 0, 3, 59, 59)
    for a in range(0, 360, 30):
        cv.put(int(round(2.4 * math.cos(math.radians(a)))), -1, 71 + int(round(2.4 * math.sin(math.radians(a)))), "minecraft:prismarine_bricks")
    cv.put(0, -1, 71, "minecraft:sea_lantern")
    cv.mark("next", 0, 0, 71)
    tide_finish(cv, 2021)
    return cv


def tide_cave_boss():
    """主の間: 海の大きな岩屋。縁に浅い水の輪と珊瑚、真ん中に殻負いの大蛸"""
    cv = K.Cave(-30, 30, -6, 22, -30, 30, 2031)
    cv.room(0, 0, 19, 18, 14, 0, seed=31, wall_h=6)
    cv.tunnel([(0, 0, 17), (0, 0, 27)], 2.0, 4.2, seed=32)
    cv.paint(tide_theme(2031))
    cv.mark("entry", 0, 0, 22)
    cv.mark("cp", 0, 0, 22)
    cv.mark("boss", 0, 0, -3)
    cv.mark("exit", 0, 0, -14)
    cv.mark("view_lair", 7, 4, 15)
    cv.mark("view_lair_to", -2, 2, -6)
    # 縁の浅い水 (床を一段掘って水)
    for x in range(-20, 21):
        for z in range(-20, 21):
            d = math.hypot(x / 19, z / 18)
            if 0.82 < d < 1.0 and (x, -1, z) in cv.mats and cv.is_air(x, 0, z) and z < 15:
                cv.mats[(x, -1, z)] = "minecraft:water[level=0]"
                cv.mats[(x, -2, z)] = "minecraft:sand"
    for k in range(12):
        a = math.radians(k * 30 + 8)
        x, z = int(round(15 * math.cos(a))), int(round(14 * math.sin(a)))
        if z < 14:
            B.stones(cv, x, z, 0, 1, ("minecraft:calcite", "minecraft:prismarine", "minecraft:dark_prismarine"))
    tide_finish(cv, 2031)
    return cv


# ====================================================================== 雷晶の空洞 (嵐の高地、Lv 22)
SH_WALL = ["minecraft:deepslate", "minecraft:cobbled_deepslate", "minecraft:tuff", "minecraft:deepslate", "minecraft:smooth_basalt", "minecraft:calcite"]


def storm_theme(seed):
    def wall(x, y, z, band, fossil):
        if fossil > 0.8:
            return "minecraft:amethyst_block"
        if fossil > 0.76:
            return "minecraft:calcite"
        if hsh(x, y, z, seed + 5) < 0.015:
            return "minecraft:copper_ore"
        return SH_WALL[int(band * 3.0 * len(SH_WALL) + y * 0.2) % len(SH_WALL)]

    def floor(x, y, z, band, fossil):
        if hsh(x, y, z, seed + 9) < 0.08:
            return "minecraft:amethyst_block"
        k = (band * 3.0) % 1.0
        return ("minecraft:cobbled_deepslate" if k < 0.35 else "minecraft:tuff" if k < 0.55 else "minecraft:gravel" if k < 0.7
                else "minecraft:polished_deepslate" if k < 0.85 else "minecraft:calcite")

    def ceil(x, y, z, band, fossil):
        if fossil > 0.78:
            return "minecraft:amethyst_block"
        return K.pick(["minecraft:deepslate", "minecraft:tuff", "minecraft:calcite", "minecraft:smooth_basalt"], x, y, z, seed + 8)
    return {"wall": wall, "floor": floor, "ceil": ceil}


def storm_drip(n):
    return ["minecraft:amethyst_cluster[facing=down,waterlogged=false]"] if n == 1 else (
        ["minecraft:large_amethyst_bud[facing=down,waterlogged=false]"] if n == 2 else B.drip(n))


def storm_finish(cv, seed):
    cv.ceiling_drips(storm_drip, 0.035, seed + 101, 3)
    cv.wall_lichen(0.008, seed + 102)
    cv.floor_scatter(["minecraft:small_amethyst_bud[facing=up,waterlogged=false]", "minecraft:medium_amethyst_bud[facing=up,waterlogged=false]",
                      "minecraft:stone_button[face=floor,facing=north,powered=false]"], 0.03, seed + 103)
    cv.lights(6, 11, seed + 104)


def crystal(cv, name, x, y, z):
    """雷の結晶の台: 銅の台座に紫水晶の塊と、上に大きな芽。印は台の上の塊"""
    cv.put(x, y - 1, z, "minecraft:oxidized_cut_copper")
    cv.put(x, y, z, "minecraft:amethyst_block")
    cv.put(x, y + 1, z, "minecraft:large_amethyst_bud[facing=up,waterlogged=false]")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if cv.is_air(x + dx, y, z + dz):
            cv.put(x + dx, y, z + dz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
    cv.mark(name, x, y, z)


def spire(cv, x, y, z, h, lean=(0.0, 0.0)):
    """大きな晶の柱 (紫水晶と、先は空色の板ガラス)"""
    for k in range(h):
        r = max(0, int(round((h - k) / h * 2.2)))
        cx, cz = int(round(x + lean[0] * k)), int(round(z + lean[1] * k))
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) <= r:
                    cv.put(cx + dx, y + k, cz + dz, "minecraft:amethyst_block" if k < h * 0.7 else "minecraft:light_blue_stained_glass")


def storm_hollow_1():
    """一層目: 晶の回廊。入口 → 晶の柱の坑道 → 晶の間 (A、結晶二つで扉 1) → 坑道 (雷の罠) → 銅の装置の洞 (B、鍵持ちの焦げた番人) → 扉 2 → 昇り台"""
    cv = K.Cave(-36, 44, -10, 26, -12, 122, 3011)
    cv.room(0, 0, 7, 6, 6, 0, seed=11)
    t1 = [(0, 0, 4), (-4, 0, 14), (-2, 1, 24), (0, 1, 32)]
    cv.tunnel(t1, 2.2, 4.8, seed=12)
    cv.room(0, 46, 15, 12, 13, 1, seed=13, wall_h=5)
    cv.tunnel([(0, 1, 57), (0, 1, 62)], 2.2, 4.6, seed=14)
    t2 = [(0, 1, 63), (6, 1, 70), (6, 0, 78)]
    cv.tunnel(t2, 2.0, 4.4, seed=15)
    cv.room(6, 90, 13, 11, 14, 0, seed=16, wall_h=5)
    cv.tunnel([(6, 0, 100), (6, 0, 108)], 2.2, 4.6, seed=17)
    cv.room(6, 114, 6, 5, 8, 0, seed=18)
    cv.paint(storm_theme(3011))
    common_marks(cv)
    for (x, z, h) in ((-6, 12, 5), (4, 22, 4), (-10, 40, 8), (10, 52, 7), (-8, 54, 6)):
        fy = cv.floor_y(x, z, 1, 4)
        if fy is not None:
            spire(cv, x, fy, z, h)
    cv.mark("room_a", 0, 1, 46)
    cv.mark("foe_a_1_puff", -6, 1, 42)
    cv.mark("foe_a_2_puff", 6, 1, 50)
    cv.mark("foe_a_3_gleaner", 0, 1, 52)
    cv.mark("foe_a_4_fledgling", 8, 1, 40)
    crystal(cv, "crystal_1_1", -11, 1, 47)
    crystal(cv, "crystal_2_1", 11, 1, 45)
    B.chest(cv, "chest_1", 0, 1, 36, "south")
    cv.mark("view_gallery", -2, 6, 36)
    cv.mark("view_gallery_to", 0, 1, 56)
    B.bars(cv, "door_1_crystal", -2, 2, 1, 4, 59, 59)
    cv.mark("trap_1_spark", 6, 1, 72)
    # 銅の装置の洞 B: 古い避雷針の塔と銅の樋
    for (x, z) in ((0, 84), (12, 94)):
        for k in range(6):
            cv.put(x, k, z, "minecraft:oxidized_cut_copper" if k % 2 else "minecraft:weathered_copper")
        cv.put(x, 6, z, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
    for x in range(0, 13):
        cv.put(x, 7, 90, CHAIN_Y if x % 4 == 0 else "minecraft:oxidized_cut_copper_slab[type=bottom,waterlogged=false]")
    cv.mark("room_b", 6, 0, 90)
    cv.mark("foe_b_1_warden_key", 6, 0, 96)
    cv.mark("foe_b_2_wolf", 0, 0, 88)
    cv.mark("foe_b_3_wolf", 12, 0, 86)
    cv.mark("foe_b_4_gleaner", 2, 0, 94)
    B.chest(cv, "chest_2", 14, 0, 90, "west")
    cv.mark("view_works", -2, 5, 82)
    cv.mark("view_works_to", 8, 2, 96)
    B.bars(cv, "door_2_key", 4, 8, 0, 3, 101, 101)
    # 昇り台 (次の層へ): 銅の円盤
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            cv.put(6 + dx, -1, 115 + dz, "minecraft:waxed_oxidized_copper")
    cv.put(6, -1, 115, "minecraft:sea_lantern")
    cv.mark("next", 6, 0, 115)
    storm_finish(cv, 3011)
    return cv


def geode(cv, cx, cy, cz, r, seed):
    """晶洞の殻: 内は紫水晶、中は方解石、外は滑らかな玄武岩 (空きのまわりの殻を塗り直す)"""
    for (x, y, z), s in list(cv.mats.items()):
        d = math.sqrt((x - cx) ** 2 + ((y - cy) * 1.3) ** 2 + (z - cz) ** 2)
        if d < r + 1.2:
            near_air = any(cv.is_air(x + dx, y + dy, z + dz) for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))
            if near_air and y > cy - 3:
                cv.mats[(x, y, z)] = "minecraft:amethyst_block" if hsh(x, y, z, seed) < 0.86 else "minecraft:budding_amethyst"
            elif d < r + 2.5:
                cv.mats[(x, y, z)] = "minecraft:calcite"


def storm_hollow_2():
    """二層目: 鳴る大晶。着き場 → 大きな晶洞 (結晶二つで扉 3、真ん中に大晶の柱) → 坑道 → 中ボスの洞 (雷角の大羊) → 扉 4 → 昇り台"""
    cv = K.Cave(-40, 40, -8, 32, -40, 78, 3021)
    cv.room(0, -30, 7, 6, 6, 0, seed=21)
    cv.tunnel([(0, 0, -26), (0, 0, -18)], 2.2, 4.4, seed=22)
    cv.room(0, 0, 20, 18, 22, 0, seed=23, wall_h=8)
    cv.tunnel([(0, 0, 18), (4, 0, 28), (0, 0, 36)], 2.2, 4.6, seed=24)
    cv.room(0, 48, 13, 11, 12, 0, seed=25, wall_h=5)
    cv.tunnel([(0, 0, 58), (0, 0, 66)], 2.0, 4.2, seed=26)
    cv.room(0, 70, 6, 5, 7, 0, seed=27)
    cv.paint(storm_theme(3021))
    geode(cv, 0, 8, 0, 20, 3022)
    common_marks(cv, (0, 0, -32), (3, 0, -34), (-3, 0, -34))
    spire(cv, 0, 0, 0, 16)
    for (x, z, h, lean) in ((-10, -8, 9, (0.2, 0.1)), (12, 6, 10, (-0.2, 0)), (-12, 10, 7, (0.1, -0.2)), (9, -12, 8, (0, 0.2))):
        spire(cv, x, 0, z, h, lean)
    cv.mark("room_g", 0, 0, 0)
    cv.mark("foe_g_1_puff", -8, 0, 4)
    cv.mark("foe_g_2_puff", 8, 0, -4)
    cv.mark("foe_g_3_gleaner", 6, 0, 10)
    cv.mark("foe_g_4_wolf", -6, 0, -10)
    crystal(cv, "crystal_1_3", -16, 0, 0)
    crystal(cv, "crystal_2_3", 16, 0, 2)
    B.chest(cv, "chest_1", 0, 0, -14, "south")
    cv.mark("view_geode", 0, 12, -14)
    cv.mark("view_geode_to", 0, 4, 8)
    B.bars(cv, "door_3_crystal", -2, 2, 0, 3, 19, 19)
    cv.mark("trap_1_spark", 4, 0, 28)
    cv.mark("room_mid", 0, 0, 48)
    cv.mark("foe_mid_1_mid", 0, 0, 50)
    B.chest(cv, "chest_2", 10, 0, 44, "west")
    cv.mark("view_mid", -8, 4, 40)
    cv.mark("view_mid_to", 4, 1, 52)
    B.bars(cv, "door_4_mid", -2, 2, 0, 3, 59, 59)
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            cv.put(dx, -1, 71 + dz, "minecraft:waxed_oxidized_copper")
    cv.put(0, -1, 71, "minecraft:sea_lantern")
    cv.mark("next", 0, 0, 71)
    storm_finish(cv, 3021)
    return cv


def storm_hollow_boss():
    """主の間: 晶洞の大きな丸屋根。四方に結晶の台 (満たすと大蜥蜴の背の溜まりが抜ける)、真ん中に背晶の大蜥蜴"""
    cv = K.Cave(-30, 30, -6, 24, -30, 30, 3031)
    cv.room(0, 0, 19, 18, 16, 0, seed=31, wall_h=7)
    cv.tunnel([(0, 0, 17), (0, 0, 27)], 2.0, 4.2, seed=32)
    cv.paint(storm_theme(3031))
    geode(cv, 0, 6, 0, 18, 3032)
    cv.mark("entry", 0, 0, 22)
    cv.mark("cp", 0, 0, 22)
    cv.mark("boss", 0, 0, -3)
    cv.mark("exit", 0, 0, -14)
    cv.mark("view_lair", 7, 4, 15)
    cv.mark("view_lair_to", -2, 2, -6)
    for k, (x, z) in enumerate(((-13, -8), (13, -8), (-13, 8), (13, 8))):
        crystal(cv, f"crystal_{k + 1}_b", x, 0, z)
    for (x, z, h) in ((-16, 0, 9), (16, 0, 9), (0, -16, 8)):
        spire(cv, x, 0, z, h)
    storm_finish(cv, 3031)
    return cv


# ====================================================================== 熔けた坑道 (灰の荒野、Lv 28)
MM_WALL = ["minecraft:basalt[axis=y]", "minecraft:blackstone", "minecraft:smooth_basalt", "minecraft:basalt[axis=y]", "minecraft:tuff", "minecraft:blackstone"]


def ember_theme(seed):
    def wall(x, y, z, band, fossil):
        if fossil > 0.84:
            return "minecraft:magma_block"
        if hsh(x, y, z, seed + 5) < 0.012:
            return "minecraft:gilded_blackstone"
        return MM_WALL[int(band * 3.0 * len(MM_WALL) + y * 0.2) % len(MM_WALL)]

    def floor(x, y, z, band, fossil):
        k = (band * 3.0) % 1.0
        return ("minecraft:blackstone" if k < 0.34 else "minecraft:smooth_basalt" if k < 0.52 else "minecraft:tuff" if k < 0.66
                else "minecraft:cobbled_deepslate" if k < 0.82 else "minecraft:polished_blackstone")

    def ceil(x, y, z, band, fossil):
        if fossil > 0.82:
            return "minecraft:magma_block"
        return K.pick(["minecraft:basalt[axis=y]", "minecraft:blackstone", "minecraft:smooth_basalt"], x, y, z, seed + 8)
    return {"wall": wall, "floor": floor, "ceil": ceil}


def ember_finish(cv, seed):
    cv.ceiling_drips(lambda n: ["minecraft:chain[axis=y,waterlogged=false]"] * (n - 1) + ["minecraft:lantern[hanging=true,waterlogged=false]"] if n >= 3
                     else B.drip(n), 0.02, seed + 101, 4)
    cv.floor_scatter(["minecraft:stone_button[face=floor,facing=north,powered=false]", "minecraft:polished_blackstone_button[face=floor,facing=north,powered=false]"],
                     0.02, seed + 103)
    cv.lights(6, 11, seed + 104)


def lava_trench(cv, x0, x1, z0, z1, y, depth=2):
    """溶岩の溝 (いつも溶岩。上面は y - 1、まわりは岩)"""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for d in range(1, depth + 1):
                cv.mats[(x, y - d, z)] = "minecraft:lava[level=0]" if d == 1 else "minecraft:magma_block"
            cv.mats[(x, y - depth - 1, z)] = "minecraft:blackstone"


def forge_ruin(cv, x, y, z):
    """灰の民の古い鍛冶場: 溶鉱炉・金床・溶岩の大釜"""
    cv.put(x, y, z, "minecraft:blast_furnace[facing=south,lit=true]")
    cv.put(x + 1, y, z, "minecraft:anvil[facing=east]")
    cv.put(x - 1, y, z, "minecraft:lava_cauldron")
    cv.put(x, y + 1, z, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")


def molten_mine_1():
    """一層目: 灰の民の坑。入口 → 黒い樫の坑道 → 古い鍛冶場の洞 (A) → 溶岩の入れ替わる渡り (lava_1_a / b) → 鎖の洞 (B、鍵持ちの古き熾の者) → 扉 → 昇降機"""
    cv = K.Cave(-36, 44, -10, 26, -12, 124, 4011)
    cv.room(0, 0, 7, 6, 6, 0, seed=11)
    t1 = [(0, 0, 4), (3, 0, 14), (-2, -1, 24), (0, -1, 32)]
    cv.tunnel(t1, 2.0, 4.4, seed=12)
    cv.room(0, 44, 15, 12, 12, -1, seed=13, wall_h=5)
    # 溶岩の溝を渡る道 (z 58..74)。道の両脇は溝、道の途中の二つの区間が入れ替わる
    cv.tunnel([(0, -1, 56), (0, -1, 76)], 5.0, 5.0, seed=14)
    cv.room(4, 90, 13, 11, 13, -1, seed=15, wall_h=5)
    cv.tunnel([(4, -1, 100), (4, -1, 108)], 2.2, 4.4, seed=16)
    cv.room(4, 114, 6, 5, 8, -1, seed=17)
    cv.paint(ember_theme(4011))
    common_marks(cv)
    B.frames_along(cv, t1, 5, 2, 4, wood="dark_oak")
    cv.mark("room_a", 0, -1, 44)
    for (x, z) in ((-10, 40), (10, 48), (-8, 50)):
        forge_ruin(cv, x, -1, z)
    cv.mark("foe_a_1_mite", -6, -1, 40)
    cv.mark("foe_a_2_mite", 6, -1, 48)
    cv.mark("foe_a_3_husk", 0, -1, 52)
    cv.mark("foe_a_4_dragger", 8, -1, 38)
    B.chest(cv, "chest_1", -12, -1, 44, "east")
    cv.mark("view_forge", -8, 3, 34)
    cv.mark("view_forge_to", 6, 0, 50)
    # 溝: 道 (x -1..1) の外は溶岩、道の z 61..64 と z 69..72 が入れ替わる区間 (下に溝)
    lava_trench(cv, -5, -2, 57, 75, -1)
    lava_trench(cv, 2, 5, 57, 75, -1)
    for z0, name in ((61, "lava_1_a"), (69, "lava_1_b")):
        for x in range(-1, 2):
            for z in range(z0, z0 + 4):
                cv.mats[(x, -2, z)] = "minecraft:magma_block"
                cv.mats[(x, -3, z)] = "minecraft:blackstone"
        box_mark(cv, name, -1, 1, -1, -1, z0, z0 + 3)
        # 区間の両端に半段の縁 (溶岩は越えられず、人は乗り越えられる)。待つ所が溶岩に覆われない
        for zz in (z0 - 1, z0 + 4):
            for x in range(-1, 2):
                cv.put(x, -1, zz, "minecraft:polished_blackstone_brick_slab[type=bottom,waterlogged=false]")
    cv.mark("view_cross", 0, 3, 56)
    cv.mark("view_cross_to", 0, -1, 74)
    # 鎖の洞 B
    for x in range(-6, 15, 4):
        for y in range(4, 12):
            if cv.is_air(x, y, 90):
                cv.put(x, y, 90, CHAIN_Y)
    cv.mark("room_b", 4, -1, 90)
    cv.mark("foe_b_1_thrall_key", 4, -1, 96)
    cv.mark("foe_b_2_husk", -2, -1, 86)
    cv.mark("foe_b_3_dragger", 10, -1, 88)
    cv.mark("foe_b_4_mite", 6, -1, 84)
    B.chest(cv, "chest_2", 14, -1, 92, "west")
    forge_ruin(cv, -6, -1, 94)
    cv.mark("trap_1_steam", 4, -1, 104)
    B.bars(cv, "door_1_key", 2, 6, -1, 2, 101, 101)
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            cv.put(4 + dx, -2, 115 + dz, "minecraft:iron_block" if dx or dz else "minecraft:shroomlight")
    for (x, z) in ((2, 113), (6, 113), (2, 117), (6, 117)):
        for y in range(-1, 6):
            cv.put(x, y, z, "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]")
    cv.mark("next", 4, -1, 115)
    ember_finish(cv, 4011)
    return cv


def molten_mine_2():
    """二層目: 熔けた溝。着き場 → 溶岩の川の大洞 (二本の橋、片方は入れ替わる区間) → 中ボスの洞 (灰被りの大蜥蜴) → 扉 → 昇降機"""
    cv = K.Cave(-42, 42, -10, 30, -40, 78, 4021)
    cv.room(0, -30, 7, 6, 6, 0, seed=21)
    cv.tunnel([(0, 0, -26), (0, 0, -18)], 2.2, 4.4, seed=22)
    cv.room(0, 0, 22, 18, 16, 0, seed=23, wall_h=6)
    cv.tunnel([(0, 0, 18), (0, 0, 36)], 2.2, 4.6, seed=24)
    cv.room(0, 48, 13, 11, 11, 0, seed=25, wall_h=5)
    cv.tunnel([(0, 0, 58), (0, 0, 66)], 2.0, 4.2, seed=26)
    cv.room(0, 70, 6, 5, 7, 0, seed=27)
    cv.paint(ember_theme(4021))
    common_marks(cv, (0, 0, -32), (3, 0, -34), (-3, 0, -34))
    # 溶岩の川 (東西に流れる溝 z -3..3)。橋は x -9..-7 (いつも渡れる・遠回りの横穴の先) と x -1..1 (入れ替わる)
    for x in range(-22, 23):
        for z in range(-3, 4):
            if (x, -1, z) in cv.mats and not (-9 <= x <= -7) and not (-1 <= x <= 1):
                cv.mats[(x, -1, z)] = "minecraft:lava[level=0]"
                cv.mats[(x, -2, z)] = "minecraft:magma_block"
                cv.mats[(x, -3, z)] = "minecraft:blackstone"
    # 入れ替わる橋 (x -1..1): 溶岩が橋の上を流れる間は渡れない。橋の床は黒石の煉瓦
    for x in range(-1, 2):
        for z in range(-3, 4):
            cv.mats[(x, -1, z)] = "minecraft:polished_blackstone_bricks"
    box_mark(cv, "lava_1_a", -1, 1, 0, 0, -3, 3)
    for zz in (-4, 4):
        for x in range(-1, 2):
            cv.put(x, 0, zz, "minecraft:polished_blackstone_brick_slab[type=bottom,waterlogged=false]")
    for z in range(-3, 4):
        for x in (-10, -6):
            cv.put(x, 0, z, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    cv.mark("room_c", 0, 0, 10)
    cv.mark("foe_c_1_husk", -6, 0, 10)
    cv.mark("foe_c_2_mite", 6, 0, 8)
    cv.mark("foe_c_3_dragger", 10, 0, -10)
    cv.mark("foe_c_4_mite", -10, 0, -10)
    B.chest(cv, "chest_1", 16, 0, 10, "west")
    cv.mark("view_river", 14, 8, -12)
    cv.mark("view_river_to", -4, 0, 2)
    cv.mark("trap_1_steam", 0, 0, 26)
    cv.mark("room_mid", 0, 0, 48)
    cv.mark("foe_mid_1_mid", 0, 0, 50)
    B.chest(cv, "chest_2", 10, 0, 44, "west")
    cv.mark("view_mid", -8, 4, 40)
    cv.mark("view_mid_to", 4, 1, 52)
    for (x, z) in ((-8, 52), (8, 54)):
        forge_ruin(cv, x, 0, z)
    B.bars(cv, "door_2_mid", -2, 2, 0, 3, 59, 59)
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            cv.put(dx, -1, 71 + dz, "minecraft:iron_block" if dx or dz else "minecraft:shroomlight")
    cv.mark("next", 0, 0, 71)
    ember_finish(cv, 4021)
    return cv


def molten_mine_boss():
    """主の間: 鍛冶の大広間。縁に溶岩の堀と低い壁、壁ぎわに古い溶鉱炉、真ん中に炉負いの巨人"""
    cv = K.Cave(-30, 30, -6, 24, -30, 30, 4031)
    cv.room(0, 0, 19, 18, 16, 0, seed=31, wall_h=7)
    cv.tunnel([(0, 0, 17), (0, 0, 27)], 2.0, 4.2, seed=32)
    cv.paint(ember_theme(4031))
    cv.mark("entry", 0, 0, 22)
    cv.mark("cp", 0, 0, 22)
    cv.mark("boss", 0, 0, -3)
    cv.mark("exit", 0, 0, -14)
    cv.mark("view_lair", 7, 4, 15)
    cv.mark("view_lair_to", -2, 3, -6)
    for x in range(-20, 21):
        for z in range(-20, 21):
            d = math.hypot(x / 19, z / 18)
            if 0.86 < d < 1.0 and (x, -1, z) in cv.mats and cv.is_air(x, 0, z) and z < 14:
                cv.mats[(x, -1, z)] = "minecraft:lava[level=0]"
                cv.mats[(x, -2, z)] = "minecraft:magma_block"
                cv.mats[(x, -3, z)] = "minecraft:blackstone"
            elif 0.80 < d <= 0.86 and cv.is_air(x, 0, z) and z < 14:
                cv.put(x, 0, z, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    for k in range(6):
        a = math.radians(200 + k * 28)
        forge_ruin(cv, int(round(12 * math.cos(a))), 0, int(round(11 * math.sin(a))))
    ember_finish(cv, 4031)
    return cv


BUILDERS = {
    "tide_cave": [("1", tide_cave_1), ("2", tide_cave_2), ("boss", tide_cave_boss)],
    "storm_hollow": [("1", storm_hollow_1), ("2", storm_hollow_2), ("boss", storm_hollow_boss)],
    "molten_mine": [("1", molten_mine_1), ("2", molten_mine_2), ("boss", molten_mine_boss)],
}
