# -*- coding: utf-8 -*-
"""奥のダンジョン四つ (issue #19 の ②。2026-11-06 の頁「大陸の外れ」の m_far_dungeons = a、主の形は 2026-11-09 の頁で A)。
一層・二層・主の間 (data/skyabyss/dungeon/<id>_<1|2|boss>.json。二層は dungeons_deep2.py) と、外れの置き場の入口 (opening/poi_dungeon_<id>.json)。build_dungeons.py から呼ぶ。

  python -X utf8 tools/gen/world/build_dungeons.py deep_fort deep_temple deep_ice deep_lava

  deep_fort    崩れた見張りの砦 (岩鯨、Lv 15): 中庭 → 城壁の歩廊へ段を登る → 鍵持ちの野盗の頭 → 天守の段 → 頂の主の間 (骨鎧の野盗王)
  deep_temple  沈んだ神殿 (潮殻、Lv 21): 列柱の回廊 → 満ち引きで沈む下の回廊 (tide_1) → 祭壇の間 (鍵持ち) → 主の間 (珊瑚に呑まれた祭司)
  deep_ice     吹雪の氷穴 (雷翼、Lv 27): 滑る氷の床 (青氷) の洞 → 吹き抜けの氷の段 → 鍵持ち → 主の間 (氷鎧の大熊)
  deep_lava    熔岩の管の坑 (余燼、Lv 33): 熔岩の管 → 入れ替わる熔岩の渡り (lava_1_a/b) → 鍵持ち → 主の間 (熔岩の大蛇)
印の約束は build_dungeons.py の頭のとおり。どの段にも登る段 (階段) を置く (上れない足場を作らない決まり)
"""
import math

import build_dungeons as B
import dungeon_kit as K
import dungeons_more as D
from dungeon_kit import AIR, CHAIN_Y, fence, hsh, lantern, logs, slab, stair

# ====================================================================== 崩れた見張りの砦 (岩鯨、Lv 15)
FT_WALL = ["minecraft:stone", "minecraft:andesite", "minecraft:tuff", "minecraft:stone", "minecraft:cobblestone", "minecraft:mossy_cobblestone"]
FT_FLOOR = ["minecraft:coarse_dirt", "minecraft:gravel", "minecraft:grass_block", "minecraft:packed_mud", "minecraft:coarse_dirt"]
FT_BRICK = ["minecraft:stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:stone_bricks", "minecraft:cobblestone"]


def fort_theme(seed):
    def wall(x, y, z, band, fossil):
        if fossil > 0.86:
            return "minecraft:bone_block[axis=y]"
        return FT_WALL[int(band * 3.0 * len(FT_WALL) + y * 0.2) % len(FT_WALL)]

    def floor(x, y, z, band, fossil):
        if hsh(x, y, z, seed + 9) < 0.15:
            return K.pick(FT_FLOOR, x, y, z, seed + 7)
        return "minecraft:coarse_dirt" if (band * 3) % 1 < 0.5 else "minecraft:grass_block"

    def ceil(x, y, z, band, fossil):
        return K.pick(["minecraft:stone", "minecraft:andesite", "minecraft:tuff", "minecraft:moss_block"], x, y, z, seed + 8)
    return {"wall": wall, "floor": floor, "ceil": ceil}


def brick(x, y, z, seed=5):
    return K.pick(FT_BRICK, x, y, z, seed)


def fort_wall(cv, x0, x1, z0, z1, h, crenel=True, seed=5):
    """厚さ 1 の石レンガの壁 (箱の周り)。上に胸壁 (一つおき)"""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                for y in range(0, h):
                    cv.put(x, y, z, brick(x, y, z, seed))
                if crenel and (x + z) % 2 == 0:
                    cv.put(x, h, z, brick(x, h, z, seed))


def stair_run(cv, x, z0, y0, n, facing, step_dz, mat="stone_brick", fill="minecraft:stone_bricks"):
    """z 方向へ一段ずつ上る石段 (下は詰める)。facing = 上る向き"""
    for k in range(n):
        z = z0 + k * step_dz
        y = y0 + k
        for dx in (0, 1):
            cv.put(x + dx, y, z, stair(mat, facing))
            for yy in range(0, y):
                cv.put(x + dx, yy, z, fill)
            for yy in range(y + 1, y + 4):
                if not cv.is_air(x + dx, yy, z):
                    cv.put(x + dx, yy, z, AIR)


def deep_fort_1():
    """一層: 崩れた砦の中庭 (大きな洞の中に建つ)。入口 → 坂道 → 中庭 (A: 野盗と弓手、壁の上にも弓手) → 城壁の歩廊へ石段 →
    歩廊 (B: 鍵持ちの野盗の頭、骨垣の狼煙台) → 扉 → 天守の内の段 → 頂の門 (次へ)"""
    cv = K.Cave(-40, 40, -4, 34, -10, 110, 5011)
    cv.room(0, 0, 6, 5, 5, 0, seed=11)
    cv.tunnel([(0, 0, 4), (0, 1, 14), (0, 2, 22)], 2.4, 4.6, seed=12)
    cv.room(0, 56, 30, 30, 26, 2, seed=13, wall_h=10)
    cv.paint(fort_theme(5011))
    # 坂道から中庭への口 (洞の縁の段差 2 を均す)
    for x in range(-3, 4):
        for z in range(22, 31):
            cv.put(x, 1, z, "minecraft:coarse_dirt")
            for y in range(2, 6):
                cv.put(x, y, z, AIR)
    D.common_marks(cv)
    # 砦: 外壁 (高さ 7、歩廊は 7 の上)。正面 (南、z = 30) に崩れた門
    fort_wall(cv, -20, 20, 32, 80, 7)
    for x in range(-2, 3):
        for y in range(2, 6):
            cv.put(x, y, 32, AIR)
    for x in (-3, 3):
        for y in range(2, 8):
            cv.put(x, y, 32, "minecraft:stripped_spruce_log[axis=y]")
    # 歩廊の床 (壁の内側に 2 幅の板)
    for x in range(-19, 20):
        for z in range(33, 80):
            if x in (-19, -18, 18, 19) or z in (33, 34, 78, 79):
                cv.put(x, 8, z, "minecraft:spruce_planks")
                cv.put(x, 9, z, AIR)
                cv.put(x, 10, z, AIR)
    # 中庭 → 歩廊への石段 (東西に一つずつ。段の下は詰める)
    stair_run(cv, 15, 70, 2, 7, "north", -1)
    stair_run(cv, -16, 70, 2, 7, "north", -1)
    # 段の頂 (z 64、高さ 8) から歩廊 (x = ±18) へ渡す板
    for sx in (17, -17):
        for z in range(63, 66):
            cv.put(sx, 8, z, "minecraft:spruce_planks")
            cv.put(sx, 9, z, AIR)
            cv.put(sx, 10, z, AIR)
    cv.mark("room_a", 0, 2, 52)
    for n, (x, z, kind) in enumerate(((-8, 44, "bandit"), (8, 46, "bandit"), (-4, 60, "dog"), (6, 62, "dog"), (0, 66, "bandit"),
                                      (-18, 40, "archer"), (18, 42, "archer")), 1):
        y = 9 if kind == "archer" else 2
        cv.mark(f"foe_a_{n}_{kind}", x, y, z)
    # 中庭の焚き火と野営の天幕
    cv.put(0, 2, 52, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
    for (x, z) in ((-10, 58), (10, 58)):
        for dx in (-1, 0, 1):
            cv.put(x + dx, 2, z, "minecraft:white_wool" if dx == 0 else "minecraft:spruce_fence")
            cv.put(x + dx, 3, z, "minecraft:white_carpet")
    B.chest(cv, "chest_1", -14, 2, 66, "east")
    cv.mark("trap_1_rocks", 0, 2, 72)
    # 歩廊の北東の角に狼煙台と鍵持ち
    cv.mark("room_b", 14, 9, 76)
    cv.mark("foe_b_1_chief_key", 16, 9, 77)
    cv.mark("foe_b_2_archer", 4, 9, 78)
    cv.mark("foe_b_3_bandit", 18, 9, 66)
    for (x, z) in ((17, 79), (16, 79)):
        cv.put(x, 9, z, "minecraft:campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]")
    B.chest(cv, "chest_2", -17, 9, 78, "south")
    # 天守 (北、中庭から入る扉の先に、歩廊の高さまでの石段と頂の門)
    fort_wall(cv, -8, 8, 80, 96, 14, seed=7)
    for x in range(-7, 8):
        for z in range(81, 96):
            for y in range(2, 14):
                cv.put(x, y, z, AIR)
            cv.put(x, 1, z, "minecraft:stone_bricks")
    for x in range(-1, 2):
        for y in range(2, 6):
            cv.put(x, y, 80, AIR)
    B.bars(cv, "door_1_key", -1, 1, 2, 5, 79, 79)
    stair_run(cv, -2, 83, 2, 9, "north", 1)
    for x in range(-7, 8):
        for z in range(92, 96):
            cv.put(x, 10, z, "minecraft:spruce_planks")
    cv.put(0, 11, 94, "minecraft:lodestone")
    cv.mark("next", 0, 11, 93)
    for (x, z) in ((-6, 82), (6, 82), (-6, 94), (6, 94)):
        cv.put(x, 6, z, lantern())
    cv.mark("view_court", -16, 12, 40)
    cv.mark("view_court_to", 4, 3, 60)
    D_finish(cv, 5011)
    return cv


def D_finish(cv, seed):
    cv.wall_lichen(0.01, seed + 1)
    cv.lights(7, 12, seed + 2, height=5)
    cv.floor_scatter(["minecraft:short_grass", "minecraft:fern", "minecraft:dead_bush"], 0.02, seed + 3)


def deep_fort_boss():
    """主の間: 天守の頂の平屋根。胸壁に囲まれた広い屋上、四隅に見張りの小屋、真ん中に骨鎧の野盗王"""
    cv = K.Cave(-30, 30, -6, 26, -30, 30, 5031)
    cv.room(0, 0, 19, 18, 18, 0, seed=31, wall_h=8)
    cv.tunnel([(0, 0, 17), (0, 0, 27)], 2.0, 4.2, seed=32)
    cv.paint(fort_theme(5031))
    for x in range(-17, 18):
        for z in range(-17, 18):
            if (x, -1, z) in cv.mats or not cv.is_air(x, -1, z):
                if cv.is_air(x, 0, z):
                    cv.mats[(x, -1, z)] = brick(x, -1, z, 9)
    for x in range(-16, 17):
        for z in range(-16, 17):
            if abs(x) == 16 or abs(z) == 16:
                if cv.is_air(x, 0, z) and not (abs(x) <= 1 and z == 16):
                    cv.put(x, 0, z, brick(x, 0, z, 9))
                    if (x + z) % 2 == 0:
                        cv.put(x, 1, z, brick(x, 1, z, 9))
    for (x, z) in ((-13, -13), (13, -13), (-13, 13), (13, 13)):
        for y in range(0, 3):
            for dx in (-1, 1):
                cv.put(x + dx, y, z, "minecraft:stripped_spruce_log[axis=y]")
        cv.put(x, 3, z, slab("spruce", "bottom"))
        cv.put(x, 2, z, lantern(True))
    cv.mark("entry", 0, 0, 22)
    cv.mark("cp", 0, 0, 22)
    cv.mark("boss", 0, 0, -3)
    cv.mark("exit", 0, 0, -14)
    cv.mark("view_lair", 7, 4, 15)
    cv.mark("view_lair_to", -2, 2, -6)
    D_finish(cv, 5031)
    return cv


# ====================================================================== 沈んだ神殿 (潮殻、Lv 21)
def temple_1():
    """一層: 列柱の回廊 (A) → 満ち引きで沈む下の回廊 (tide_1) → 祭壇の間 (B、鍵持ちの磯の番人) → 扉 → 潮の門"""
    cv = K.Cave(-36, 36, -12, 22, -12, 110, 6011)
    cv.room(0, 0, 6, 5, 5, 0, seed=11)
    cv.tunnel([(0, 0, 4), (0, -1, 16)], 2.4, 4.4, seed=12)
    cv.room(0, 34, 12, 16, 9, -1, seed=13, wall_h=5)
    cv.tunnel([(0, -1, 50), (0, -4, 56), (0, -4, 66), (0, -1, 72)], 2.4, 3.6, seed=14)
    cv.room(0, 86, 14, 12, 10, -1, seed=15, wall_h=5)
    cv.tunnel([(0, -1, 98), (0, -1, 104)], 2.2, 4.2, seed=16)
    cv.paint(D.tide_theme(6011))
    D.common_marks(cv)
    # 回廊の列柱 (海晶の柱) と床の海晶の敷石
    for z in range(22, 48, 5):
        for x in (-6, 6):
            for y in range(-1, 6):
                cv.put(x, y, z, "minecraft:prismarine_bricks" if y % 3 else "minecraft:dark_prismarine")
            cv.put(x, 6, z, "minecraft:sea_lantern")
    for x in range(-4, 5):
        for z in range(22, 48):
            if (x, -2, z) in cv.mats:
                cv.mats[(x, -2, z)] = "minecraft:prismarine_bricks" if (x + z) % 3 else "minecraft:dark_prismarine"
    cv.mark("room_a", 0, -1, 34)
    for n, (x, z, kind) in enumerate(((-3, 28, "thrall"), (3, 30, "sailor"), (-3, 40, "angler"), (3, 42, "thrall"), (0, 46, "jelly")), 1):
        cv.mark(f"foe_a_{n}_{kind}", x, -1, z)
    B.chest(cv, "chest_1", -9, -1, 34, "east")
    D.box_mark(cv, "tide_1", -2, 2, -4, -1, 54, 68)
    cv.mark("trap_1_brine", 0, -1, 74)
    # 祭壇の間: 段の上の祭壇 (段は石段で上がる)
    for x in range(-4, 5):
        for z in range(90, 95):
            cv.put(x, -1, z, "minecraft:dark_prismarine")
            cv.put(x, 0, z, "minecraft:prismarine_bricks")
    for x in range(-4, 5):
        cv.put(x, -1, 89, stair("prismarine_brick", "south"))
    cv.put(0, 1, 92, "minecraft:sea_lantern")
    cv.mark("room_b", 0, -1, 84)
    cv.mark("foe_b_1_warden_key", 0, 1, 92)
    cv.mark("foe_b_2_sailor", -8, -1, 82)
    cv.mark("foe_b_3_thrall", 8, -1, 84)
    B.chest(cv, "chest_2", 11, -1, 90, "west")
    B.bars(cv, "door_1_key", -1, 1, -1, 2, 99, 99)
    for a in range(0, 360, 30):
        x = int(round(2.4 * math.cos(math.radians(a))))
        z = 104 + int(round(2.4 * math.sin(math.radians(a))))
        cv.put(x, -2, z, "minecraft:prismarine_bricks")
    cv.put(0, -2, 104, "minecraft:sea_lantern")
    cv.mark("next", 0, -1, 104)
    D.tide_finish(cv, 6011)
    return cv


def temple_boss():
    """主の間: 沈んだ大聖堂。縁は浅い水、列柱の輪、真ん中に珊瑚に呑まれた祭司"""
    cv = K.Cave(-30, 30, -6, 24, -30, 30, 6031)
    cv.room(0, 0, 19, 18, 15, 0, seed=31, wall_h=7)
    cv.tunnel([(0, 0, 17), (0, 0, 27)], 2.0, 4.2, seed=32)
    cv.paint(D.tide_theme(6031))
    for k in range(10):
        a = math.radians(k * 36)
        x, z = int(round(14 * math.cos(a))), int(round(13 * math.sin(a)))
        if z < 14:
            for y in range(0, 8):
                cv.put(x, y, z, "minecraft:prismarine_bricks" if y % 3 else "minecraft:dark_prismarine")
            cv.put(x, 8, z, "minecraft:sea_lantern")
    for x in range(-19, 20):
        for z in range(-18, 14):
            d = math.hypot(x / 18, z / 17)
            if 0.84 < d < 1.0 and (x, -1, z) in cv.mats and cv.is_air(x, 0, z):
                cv.mats[(x, -1, z)] = "minecraft:water[level=0]"
                cv.mats[(x, -2, z)] = "minecraft:prismarine"
    cv.mark("entry", 0, 0, 22)
    cv.mark("cp", 0, 0, 22)
    cv.mark("boss", 0, 0, -3)
    cv.mark("exit", 0, 0, -14)
    cv.mark("view_lair", 7, 4, 15)
    cv.mark("view_lair_to", -2, 2, -6)
    D.tide_finish(cv, 6031)
    return cv


# ====================================================================== 吹雪の氷穴 (雷翼、Lv 27)
ICE = ["minecraft:packed_ice", "minecraft:blue_ice", "minecraft:packed_ice", "minecraft:snow_block", "minecraft:calcite"]


def ice_theme(seed):
    def wall(x, y, z, band, fossil):
        return K.pick(["minecraft:packed_ice", "minecraft:snow_block", "minecraft:calcite", "minecraft:packed_ice", "minecraft:deepslate"], x, y, z, seed)

    def floor(x, y, z, band, fossil):
        # 滑る床 (青氷) の筋と、固めた氷・雪
        return "minecraft:blue_ice" if (band * 4) % 1 < 0.35 else "minecraft:packed_ice" if (band * 4) % 1 < 0.75 else "minecraft:snow_block"

    def ceil(x, y, z, band, fossil):
        return K.pick(["minecraft:packed_ice", "minecraft:snow_block", "minecraft:blue_ice"], x, y, z, seed + 1)
    return {"wall": wall, "floor": floor, "ceil": ceil}


def ice_spire(cv, x, y, z, h):
    """氷柱の林の一本 (固めた氷の太い根もと、青氷の芯、先は白い雪の氷)"""
    for k in range(h):
        r = max(0, int(round((h - k) / h * 2.0)))
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) <= r:
                    m = "minecraft:blue_ice" if dx == 0 and dz == 0 and k < h * 0.8 else "minecraft:packed_ice" if k < h * 0.75 else "minecraft:snow_block"
                    cv.put(x + dx, y + k, z + dz, m)


def ice_finish(cv, seed):
    cv.lights(7, 12, seed + 2, height=4)
    icicles = ["minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]"]
    cv.ceiling_drips(lambda n: icicles, 0.03, seed + 3, max_len=1)


def ice_1():
    """一層: 滑る青氷の洞 (A) → 吹き抜けの氷の段を登る (段は詰めた雪の上の石段) → 氷の棚 (B、鍵持ちの焼けた羽守) → 扉 → 氷の門"""
    cv = K.Cave(-36, 36, -6, 34, -12, 110, 7011)
    cv.room(0, 0, 6, 5, 5, 0, seed=11)
    cv.tunnel([(0, 0, 4), (0, 0, 18)], 2.4, 4.4, seed=12)
    cv.room(0, 36, 16, 16, 10, 0, seed=13, wall_h=6)
    cv.shaft(0, 62, 7, 0, 22, seed=14)
    # 青氷の洞と縦坑をつなぐ (洞の縁と坑の縁のあいだに岩が残っていた)
    cv.tunnel([(0, 0, 46), (0, 0, 60)], 2.2, 4.0, seed=19)
    cv.room(0, 80, 14, 12, 8, 12, seed=15, wall_h=5)
    cv.tunnel([(0, 12, 90), (0, 12, 100)], 2.2, 4.2, seed=16)
    cv.paint(ice_theme(7011))
    D.common_marks(cv)
    cv.mark("room_a", 0, 0, 36)
    for n, (x, z, kind) in enumerate(((-6, 30, "tortoise"), (6, 32, "jelly"), (-8, 42, "slinger"), (8, 44, "wolf"), (0, 48, "slinger")), 1):
        cv.mark(f"foe_a_{n}_{kind}", x, 0, z)
    B.chest(cv, "chest_1", -13, 0, 36, "east")
    cv.mark("trap_1_spark", 0, 0, 52)
    # 吹き抜け: 壁ぎわを回って上がる石段 (12 段)、下は雪で詰める
    for k in range(12):
        a = math.radians(-90 + k * 30)
        x = int(round(5 * math.cos(a)))
        z = 62 + int(round(5 * math.sin(a)))
        facing = ["east", "south", "west", "north"][int(((k * 30) % 360) // 90)]
        for yy in range(0, k):
            cv.put(x, yy, z, "minecraft:snow_block")
        cv.put(x, k, z, stair("stone_brick", facing))
        for yy in range(k + 1, k + 4):
            cv.put(x, yy, z, AIR)
    for x in range(-3, 4):
        for z in range(66, 72):
            cv.put(x, 11, z, "minecraft:packed_ice")
    cv.mark("room_b", 0, 12, 80)
    cv.mark("foe_b_1_warden_key", 0, 12, 84)
    cv.mark("foe_b_2_jelly", -8, 12, 78)
    cv.mark("foe_b_3_tortoise", 8, 12, 80)
    B.chest(cv, "chest_2", 11, 12, 86, "west")
    B.bars(cv, "door_1_key", -1, 1, 12, 15, 94, 94)
    cv.put(0, 11, 100, "minecraft:blue_ice")
    cv.mark("next", 0, 12, 100)
    ice_finish(cv, 7011)
    return cv


def ice_boss():
    """主の間: 凍った大穴。床は滑る青氷の輪と固めた氷、壁ぎわに氷柱の林、真ん中に氷鎧の大熊"""
    cv = K.Cave(-30, 30, -6, 24, -30, 30, 7031)
    cv.room(0, 0, 19, 18, 16, 0, seed=31, wall_h=7)
    cv.tunnel([(0, 0, 17), (0, 0, 27)], 2.0, 4.2, seed=32)
    cv.paint(ice_theme(7031))
    for k in range(9):
        a = math.radians(200 + k * 18)
        ice_spire(cv, int(round(15 * math.cos(a))), 0, int(round(14 * math.sin(a))), 6 + k % 3)
    cv.mark("entry", 0, 0, 22)
    cv.mark("cp", 0, 0, 22)
    cv.mark("boss", 0, 0, -3)
    cv.mark("exit", 0, 0, -14)
    cv.mark("view_lair", 7, 4, 15)
    cv.mark("view_lair_to", -2, 2, -6)
    ice_finish(cv, 7031)
    return cv


# ====================================================================== 熔岩の管の坑 (余燼、Lv 33)
def lava_1():
    """一層: 熔岩の管 (丸い管の坑道) → 管の大洞 (A) → 入れ替わる熔岩の渡り (lava_1_a / b) → 冷えた岩棚 (B、鍵持ちの古い灰被り) → 扉 → 管の口"""
    cv = K.Cave(-36, 36, -8, 24, -12, 120, 8011)
    cv.room(0, 0, 6, 5, 5, 0, seed=11)
    cv.tunnel([(0, 0, 4), (3, 0, 14), (0, 0, 24)], 3.0, 5.0, rough=0.08, seed=12)
    cv.room(0, 40, 15, 14, 10, 0, seed=13, wall_h=6)
    cv.tunnel([(0, 0, 54), (0, 0, 74)], 3.2, 5.0, rough=0.08, seed=14)
    cv.room(0, 90, 13, 12, 9, 0, seed=15, wall_h=5)
    cv.tunnel([(0, 0, 102), (0, 0, 110)], 2.4, 4.4, seed=16)
    cv.paint(D.ember_theme(8011))
    D.common_marks(cv)
    cv.mark("room_a", 0, 0, 40)
    for n, (x, z, kind) in enumerate(((-6, 34, "scorpion"), (6, 36, "spitter"), (-8, 46, "jackal"), (8, 46, "jackal"), (0, 50, "whirl")), 1):
        cv.mark(f"foe_a_{n}_{kind}", x, 0, z)
    B.chest(cv, "chest_1", -12, 0, 40, "east")
    # 溝: 道 (x -1..1) の外は溶岩、道の z 58..62 と z 66..70 が入れ替わる区間 (熔けた坑道と同じ作り)
    D.lava_trench(cv, -5, -2, 56, 72, -1)
    D.lava_trench(cv, 2, 5, 56, 72, -1)
    for z0, name in ((58, "lava_1_a"), (66, "lava_1_b")):
        for x in range(-1, 2):
            for z in range(z0, z0 + 5):
                cv.mats[(x, -2, z)] = "minecraft:magma_block"
                cv.mats[(x, -3, z)] = "minecraft:blackstone"
        cv.fill_air(-1, 1, -1, -1, z0, z0 + 4)
        for x in range(-1, 2):
            for z in range(z0, z0 + 5):
                cv.mats.pop((x, -1, z), None)
                cv.blk.pop((x, -1, z), None)
        D.box_mark(cv, name, -1, 1, -1, -1, z0, z0 + 4)
        # 区間の両端に半段の縁 (溶岩は越えられず、人は乗り越えられる)
        for zz in (z0 - 1, z0 + 5):
            for x in range(-1, 2):
                cv.put(x, -1, zz, "minecraft:polished_blackstone_brick_slab[type=bottom,waterlogged=false]")
    cv.mark("trap_1_steam", 0, 0, 78)
    cv.mark("room_b", 0, 0, 90)
    cv.mark("foe_b_1_thrall_key", 0, 0, 94)
    cv.mark("foe_b_2_spitter", -8, 0, 86)
    cv.mark("foe_b_3_scorpion", 8, 0, 90)
    B.chest(cv, "chest_2", 10, 0, 96, "west")
    D.forge_ruin(cv, -9, 0, 95)
    B.bars(cv, "door_1_key", -1, 1, 0, 3, 103, 103)
    cv.put(0, -1, 110, "minecraft:magma_block")
    cv.mark("next", 0, 0, 110)
    D.ember_finish(cv, 8011)
    return cv


def lava_boss():
    """主の間: 熔岩の湖の岸。半分は熔岩の湖 (主はそこから上体を出す)、手前は冷えた岩の床"""
    cv = K.Cave(-30, 30, -6, 24, -30, 30, 8031)
    cv.room(0, 0, 19, 18, 16, 0, seed=31, wall_h=7)
    cv.tunnel([(0, 0, 17), (0, 0, 27)], 2.0, 4.2, seed=32)
    cv.paint(D.ember_theme(8031))
    for x in range(-19, 20):
        for z in range(-18, 18):
            d = math.hypot(x / 18, z / 17)
            if d < 1.0 and z < -8 and (x, -1, z) in cv.mats and cv.is_air(x, 0, z) and abs(x) > 4:
                cv.mats[(x, -1, z)] = "minecraft:lava[level=0]"
                cv.mats[(x, -2, z)] = "minecraft:magma_block"
    cv.mark("entry", 0, 0, 22)
    cv.mark("cp", 0, 0, 22)
    cv.mark("boss", 0, 0, -3)
    cv.mark("exit", 0, 0, 10)
    cv.mark("view_lair", 7, 4, 15)
    cv.mark("view_lair_to", -2, 2, -6)
    D.ember_finish(cv, 8031)
    return cv


def _floor2(name):
    def build():
        import dungeons_deep2
        return dungeons_deep2.FLOORS2[name]()
    return build


# 層は一層・二層 (中ボスの間で終わる。dungeons_deep2.py)・主の間 (2026-11-09 の声「1層と2層にしてほしい」「ボスの前の中ボス」)
BUILDERS = {
    "deep_fort": [("1", deep_fort_1), ("2", _floor2("deep_fort")), ("boss", deep_fort_boss)],
    "deep_temple": [("1", temple_1), ("2", _floor2("deep_temple")), ("boss", temple_boss)],
    "deep_ice": [("1", ice_1), ("2", _floor2("deep_ice")), ("boss", ice_boss)],
    "deep_lava": [("1", lava_1), ("2", _floor2("deep_lava")), ("boss", lava_boss)],
}


def ice_hall_decor(c, cv):
    """入口の間の飾り: 氷柱の林と、雪をかぶった荷"""
    import dungeon_entrances as E
    for (x, z, h) in ((-12, -44, 6), (12, -54, 7), (-10, -56, 5)):
        ice_spire(cv, x, E.HALL_FLOOR, z, h)
    for (x, z) in ((12, -44), (12, -45)):
        c.cells[(x, E.HALL_FLOOR, z)] = "minecraft:barrel[facing=up,open=false]"
    c.cells[(12, E.HALL_FLOOR + 1, -44)] = "minecraft:snow[layers=2]"


def _entrances():
    import dungeon_entrances as E
    fort_layers = [(1, ["minecraft:grass_block", "minecraft:coarse_dirt", "minecraft:moss_block"]), (4, ["minecraft:stone", "minecraft:andesite", "minecraft:mossy_cobblestone"]),
                   (99, ["minecraft:stone", "minecraft:andesite", "minecraft:tuff"])]
    tide_layers = [(1, ["minecraft:sand", "minecraft:sandstone", "minecraft:calcite"]), (4, ["minecraft:prismarine", "minecraft:calcite", "minecraft:stone"]),
                   (99, ["minecraft:calcite", "minecraft:diorite", "minecraft:tuff"])]
    ice_layers = [(1, ["minecraft:snow_block", "minecraft:packed_ice", "minecraft:gravel"]), (4, ["minecraft:packed_ice", "minecraft:calcite", "minecraft:deepslate"]),
                  (99, ["minecraft:packed_ice", "minecraft:deepslate", "minecraft:tuff"])]
    lava_layers = [(1, ["minecraft:blackstone", "minecraft:basalt[axis=y]", "minecraft:magma_block"]), (4, ["minecraft:basalt[axis=y]", "minecraft:blackstone", "minecraft:smooth_basalt"]),
                   (99, ["minecraft:basalt[axis=y]", "minecraft:smooth_basalt", "minecraft:blackstone"])]

    def none(c, cv):
        pass
    return {
        "deep_fort": lambda: E.entrance("deep_fort", fort_theme(2500), 2500,
                                        E.themed_mouth(fort_layers, [("minecraft:coarse_dirt", 3), ("minecraft:gravel", 2), ("minecraft:mossy_cobblestone", 1)],
                                                       "minecraft:stripped_spruce_log[axis=y]", "minecraft:stripped_spruce_log[axis=x]", "minecraft:stone_bricks", 2501),
                                        E.themed_hall("minecraft:stone_bricks", "minecraft:polished_andesite", "minecraft:mossy_stone_bricks", False,
                                                      logs("stripped_spruce_log"), none), "stone_brick", "minecraft:stone_bricks"),
        "deep_temple": lambda: E.entrance("deep_temple", D.tide_theme(2600), 2600,
                                          E.themed_mouth(tide_layers, [("minecraft:sand", 4), ("minecraft:prismarine", 1), ("minecraft:gravel", 2)],
                                                         "minecraft:prismarine_bricks", "minecraft:dark_prismarine", "minecraft:prismarine_bricks", 2601),
                                          E.themed_hall("minecraft:prismarine_bricks", "minecraft:dark_prismarine", "minecraft:prismarine_bricks", False,
                                                        "minecraft:prismarine_bricks", E.tide_hall_decor), "prismarine_brick", "minecraft:prismarine_bricks"),
        "deep_ice": lambda: E.entrance("deep_ice", ice_theme(2700), 2700,
                                       E.themed_mouth(ice_layers, [("minecraft:snow_block", 3), ("minecraft:packed_ice", 2), ("minecraft:gravel", 1)],
                                                      "minecraft:stripped_spruce_log[axis=y]", "minecraft:stripped_spruce_log[axis=x]", "minecraft:packed_ice", 2701),
                                       E.themed_hall("minecraft:packed_ice", "minecraft:polished_deepslate", "minecraft:calcite", True,
                                                     "minecraft:calcite", ice_hall_decor), "stone_brick", "minecraft:polished_deepslate"),
        "deep_lava": lambda: E.entrance("deep_lava", D.ember_theme(2800), 2800,
                                        E.themed_mouth(lava_layers, [("minecraft:blackstone", 3), ("minecraft:basalt[axis=y]", 2), ("minecraft:magma_block", 1)],
                                                       "minecraft:dark_oak_log[axis=y]", "minecraft:dark_oak_log[axis=x]", "minecraft:polished_blackstone_bricks", 2801),
                                        E.themed_hall("minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone", "minecraft:chiseled_polished_blackstone", True,
                                                      logs("dark_oak_log"), E.ember_hall_decor), "polished_blackstone_brick", "minecraft:polished_blackstone"),
    }
