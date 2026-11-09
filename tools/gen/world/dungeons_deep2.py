# -*- coding: utf-8 -*-
"""奥のダンジョン四つの二層 (2026-11-09 の声「新しくできた4つ中ダンジョン、ボスの前の中ボス付けてほしいのと、1層と2層にしてほしい。
手前にある中ダンジョンの方が豪華になってる」)。層は一層・二層・主の間 (中ダンジョンと同じ)。二層の終わりに中ボスの間 (room_mid) があり、
中ボスを倒すと扉 (door_2_mid) が開いて主の間へ。中ボスは専用 (Dungeon.java の midBoss: 骨馬の槍騎兵・大海月の巫女・雷の大梟・熔岩の大蛙)。dungeons_deep.BUILDERS に足す。

  deep_fort    天守の内: 大広間 (A) → 落石の廊下 → 武具庫 (B、鍵なし) → 内郭 (中ボス: 骨馬の槍騎兵) → 天守の頂への石段
  deep_temple  水の書庫: 沈んだ身廊 (A、満ち引きの床 tide_2) → 書架の回廊 (B) → 珊瑚の中庭 (中ボス: 大海月の巫女) → 潮の門
  deep_ice     凍った大滝: 氷柱の原 (A) → 滝の脇の段を回って登る → 雷の棚 (B) → 吹きさらしの氷の舞台 (中ボス: 雷の大梟) → 氷の門
  deep_lava    熔岩の大管: 管の合流 (A) → 入れ替わる熔岩の渡り (lava_2_a/b) → 灰の民の鋳場 (B) → 熔岩の島 (中ボス: 熔岩の大蛙) → 管の口
どの段にも登る段 (階段) を置く (上れない足場を作らない決まり)
"""
import math

import build_dungeons as B
import dungeon_kit as K
import dungeons_more as D
from dungeon_kit import AIR, CHAIN_Y, fence, hsh, lantern, logs, slab, stair


def gate_out(cv, z_bars, z_next, y=0, block="minecraft:lodestone"):
    """中ボスの扉 (鉄格子) と、その先の次の層への印。格子の箱の下の隅 (_lo) は坑道の空いた所に置く"""
    cv.fill_air(-1, 1, y, y + 2, z_bars, z_bars)
    B.bars(cv, "door_2_mid", -1, 1, y, y + 3, z_bars, z_bars)
    cv.put(0, y - 1, z_next, block)
    cv.mark("next", 0, y, z_next)


def pillar(cv, x, z, y0, y1, mat, cap=None):
    for y in range(y0, y1):
        cv.put(x, y, z, mat)
    if cap:
        cv.put(x, y1, z, cap)


# ====================================================================== 崩れた見張りの砦: 天守の内
def deep_fort_2():
    import dungeons_deep as F
    cv = K.Cave(-40, 40, -4, 30, -10, 132, 5021)
    cv.room(0, 0, 6, 5, 5, 0, seed=11)
    cv.tunnel([(0, 0, 4), (0, 0, 20)], 2.2, 4.4, seed=12)
    cv.room(0, 36, 13, 15, 10, 0, seed=13, wall_h=8)
    cv.tunnel([(0, 0, 50), (2, 0, 60), (0, 0, 68)], 2.0, 4.2, seed=14)
    cv.room(0, 80, 11, 10, 7, 0, seed=15, wall_h=6)
    cv.tunnel([(0, 0, 88), (0, 0, 98)], 2.2, 4.4, seed=16)
    cv.room(0, 112, 16, 13, 11, 0, seed=17, wall_h=9)
    cv.tunnel([(0, 0, 124), (0, 0, 131)], 1.6, 3.8, seed=18)
    cv.paint(F.fort_theme(5021))
    D.common_marks(cv)
    # 大広間: 石の床、二列の柱 (丸太と灯)、長い卓と腰掛け、奥に旗
    for x in range(-12, 13):
        for z in range(22, 51):
            if not cv.is_air(x, -1, z) and cv.is_air(x, 0, z):
                cv.mats[(x, -1, z)] = F.brick(x, -1, z, 21) if (x + z) % 5 else "minecraft:polished_andesite"
    for z in range(24, 50, 6):
        for x in (-8, 8):
            pillar(cv, x, z, 0, 8, "minecraft:stripped_spruce_log[axis=y]", "minecraft:spruce_planks")
            cv.put(x + (1 if x < 0 else -1), 5, z, "minecraft:wall_torch[facing=" + ("east" if x < 0 else "west") + "]")
    for z in range(28, 46):
        cv.put(0, 0, z, "minecraft:spruce_fence")
        cv.put(0, 1, z, "minecraft:spruce_pressure_plate" if z % 4 == 0 else slab("spruce", "bottom"))
        for x in (-2, 2):
            if z % 3 == 0:
                cv.put(x, 0, z, stair("spruce", "east" if x < 0 else "west"))
    for x in (-3, 3):
        for y in range(3, 7):
            cv.put(x, y, 50, "minecraft:red_wool" if y > 3 else "minecraft:red_carpet")
    cv.mark("room_a", 0, 0, 36)
    for n, (x, z, kind) in enumerate(((-5, 28, "bandit"), (5, 30, "bandit"), (-6, 40, "dog"), (6, 42, "dog"), (-3, 46, "archer"), (3, 46, "archer")), 1):
        cv.mark(f"foe_a_{n}_{kind}", x, 0, z)
    B.chest(cv, "chest_1", -11, 0, 36, "east")
    # 落石の廊下
    cv.mark("trap_1_rocks", 1, 0, 60)
    for z in (56, 64):
        for x in (-2, 2):
            cv.put(x, 0, z, "minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    # 武具庫: 樽・砥石・鍛冶台・武具立て (柵と鎧)
    for (x, z) in ((-9, 76), (-9, 78), (-9, 80), (9, 76), (9, 78)):
        cv.put(x, 0, z, "minecraft:barrel[facing=up,open=false]")
    cv.put(-8, 0, 84, "minecraft:grindstone[face=floor,facing=north]")
    cv.put(8, 0, 84, "minecraft:smithing_table")
    for x in range(-4, 5, 2):
        cv.put(x, 0, 88, "minecraft:spruce_fence")
        cv.put(x, 1, 88, "minecraft:spruce_fence")
    cv.mark("room_b", 0, 0, 80)
    for n, (x, z, kind) in enumerate(((-4, 76, "bandit"), (4, 78, "archer"), (0, 84, "chief"), (6, 82, "dog")), 1):
        cv.mark(f"foe_b_{n}_{kind}", x, 0, z)
    B.chest(cv, "chest_2", 9, 0, 80, "west")
    # 内郭: 割れた敷石、壊れた荷車 (干し草)、鎖の垂れる梁、四隅の物見の段
    for x in range(-15, 16):
        for z in range(100, 125):
            if not cv.is_air(x, -1, z) and cv.is_air(x, 0, z) and hsh(x, 0, z, 77) < 0.6:
                cv.mats[(x, -1, z)] = K.pick(["minecraft:cracked_stone_bricks", "minecraft:stone_bricks", "minecraft:gravel", "minecraft:mossy_stone_bricks"], x, -1, z, 78)
    for (x, z) in ((-10, 104), (11, 118)):
        cv.put(x, 0, z, "minecraft:hay_block[axis=y]")
        cv.put(x + 1, 0, z, "minecraft:hay_block[axis=x]")
        cv.put(x, 1, z, "minecraft:hay_block[axis=y]")
    for x in range(-12, 13):
        cv.put(x, 9, 112, "minecraft:spruce_log[axis=x]")
    for x in (-8, 0, 8):
        for y in range(5, 9):
            cv.put(x, y, 112, CHAIN_Y)
        cv.put(x, 4, 112, lantern(True))
    cv.mark("room_mid", 0, 0, 110)
    cv.mark("foe_mid_1_mid", 0, 0, 114)
    gate_out(cv, 125, 130)
    cv.mark("view_mid", -10, 6, 100)
    cv.mark("view_mid_to", 2, 1, 114)
    F.D_finish(cv, 5021)
    return cv


# ====================================================================== 沈んだ神殿: 水の書庫
def deep_temple_2():
    cv = K.Cave(-36, 36, -12, 24, -10, 132, 6021)
    cv.room(0, 0, 6, 5, 5, 0, seed=11)
    cv.tunnel([(0, 0, 4), (0, -1, 18)], 2.4, 4.4, seed=12)
    cv.room(0, 36, 13, 16, 10, -1, seed=13, wall_h=7)
    cv.tunnel([(0, -1, 52), (0, -1, 66)], 2.2, 4.2, seed=14)
    cv.room(0, 80, 12, 10, 8, -1, seed=15, wall_h=6)
    cv.tunnel([(0, -1, 90), (0, -1, 98)], 2.2, 4.2, seed=16)
    cv.room(0, 112, 16, 13, 10, -1, seed=17, wall_h=7)
    cv.tunnel([(0, -1, 124), (0, -1, 131)], 1.6, 3.8, seed=18)
    cv.paint(D.tide_theme(6021))
    D.common_marks(cv)
    # 身廊: 海晶の柱の二列、真ん中は満ち引きで水の張る下がった床 (tide_2)
    for z in range(22, 52, 5):
        for x in (-7, 7):
            pillar(cv, x, z, -1, 8, "minecraft:prismarine_bricks", "minecraft:sea_lantern")
    for x in range(-3, 4):
        for z in range(26, 47):
            cv.mats[(x, -2, z)] = "minecraft:dark_prismarine" if (x + z) % 2 else "minecraft:prismarine_bricks"
            cv.mats.pop((x, -1, z), None)
    D.box_mark(cv, "tide_2", -3, 3, -1, -1, 26, 46)
    # 身廊の縁の段 (下がった床から上がる)
    for z in (25, 47):
        for x in range(-3, 4):
            cv.put(x, -1, z, stair("prismarine_brick", "south" if z == 25 else "north"))
    cv.mark("room_a", 0, -1, 36)
    for n, (x, z, kind) in enumerate(((-5, 28, "thrall"), (5, 30, "sailor"), (-5, 42, "angler"), (5, 44, "thrall"), (0, 48, "jelly"), (0, 32, "warden")), 1):
        cv.mark(f"foe_a_{n}_{kind}", x, -1, z)
    B.chest(cv, "chest_1", -11, -1, 36, "east")
    cv.mark("trap_1_brine", 0, -1, 60)
    # 書架の回廊: 苔むした本棚と崩れた書見台
    for z in range(72, 89, 2):
        for x in (-10, 10):
            for y in range(-1, 3):
                cv.put(x, y, z, "minecraft:bookshelf" if hsh(x, y, z, 31) < 0.7 else "minecraft:prismarine_bricks")
    cv.put(-3, -1, 80, "minecraft:lectern[facing=south,has_book=false,powered=false]")
    cv.put(3, -1, 80, "minecraft:lectern[facing=south,has_book=false,powered=false]")
    cv.mark("room_b", 0, -1, 80)
    for n, (x, z, kind) in enumerate(((-5, 76, "sailor"), (5, 78, "angler"), (0, 84, "warden"), (-6, 84, "jelly")), 1):
        cv.mark(f"foe_b_{n}_{kind}", x, -1, z)
    B.chest(cv, "chest_2", 9, -1, 76, "west")
    # 珊瑚の中庭: 縁は浅い水、珊瑚の塊と海晶の柱の輪
    for x in range(-16, 17):
        for z in range(100, 125):
            d = math.hypot(x / 15.5, (z - 112) / 12.5)
            if 0.82 < d < 1.0 and (x, -2, z) in cv.mats and cv.is_air(x, -1, z):
                cv.mats[(x, -2, z)] = "minecraft:water[level=0]"
                cv.mats[(x, -3, z)] = "minecraft:prismarine"
    for k in range(8):
        a = math.radians(k * 45 + 22)
        x, z = int(round(11 * math.cos(a))), 112 + int(round(9 * math.sin(a)))
        pillar(cv, x, z, -1, 6, "minecraft:prismarine_bricks", "minecraft:sea_lantern")
    for (x, z) in ((-6, 104), (7, 120), (-8, 118)):
        cv.put(x, -1, z, "minecraft:brain_coral_block")
        cv.put(x, 0, z, "minecraft:dead_horn_coral_block")
    cv.mark("room_mid", 0, -1, 110)
    cv.mark("foe_mid_1_mid", 0, -1, 114)
    gate_out(cv, 125, 130, y=-1, block="minecraft:sea_lantern")
    cv.mark("view_mid", -10, 5, 100)
    cv.mark("view_mid_to", 2, 0, 114)
    D.tide_finish(cv, 6021)
    return cv


# ====================================================================== 吹雪の氷穴: 凍った大滝
def deep_ice_2():
    import dungeons_deep as F
    cv = K.Cave(-36, 36, -6, 40, -10, 132, 7021)
    cv.room(0, 0, 6, 5, 5, 0, seed=11)
    cv.tunnel([(0, 0, 4), (0, 0, 20)], 2.4, 4.4, seed=12)
    cv.room(0, 38, 15, 16, 11, 0, seed=13, wall_h=7)
    cv.shaft(0, 64, 7, 0, 26, seed=14)
    cv.room(0, 82, 12, 10, 8, 12, seed=15, wall_h=6)
    cv.tunnel([(0, 12, 92), (0, 12, 98)], 2.2, 4.2, seed=16)
    cv.room(0, 112, 16, 13, 12, 12, seed=17, wall_h=8)
    cv.tunnel([(0, 12, 124), (0, 12, 131)], 1.6, 3.8, seed=18)
    cv.paint(F.ice_theme(7021))
    D.common_marks(cv)
    # 氷柱の原
    for (x, z, h) in ((-11, 30, 6), (10, 34, 7), (-9, 46, 5), (12, 46, 6), (-3, 52, 4)):
        F.ice_spire(cv, x, 0, z, h)
    cv.mark("room_a", 0, 0, 38)
    for n, (x, z, kind) in enumerate(((-5, 30, "tortoise"), (5, 32, "slinger"), (-6, 42, "jelly"), (6, 44, "wolf"), (0, 48, "slinger"), (2, 36, "jelly")), 1):
        cv.mark(f"foe_a_{n}_{kind}", x, 0, z)
    B.chest(cv, "chest_1", -13, 0, 38, "east")
    cv.mark("trap_1_spark", 0, 0, 54)
    # 凍った滝 (縦坑の奥の壁を青氷と氷で) と、壁ぎわを回って上がる石段 (12 段)
    for y in range(0, 26):
        for x in range(-4, 5):
            if not cv.is_air(x, y, 72):
                cv.mats[(x, y, 72)] = "minecraft:blue_ice" if (x + y) % 3 else "minecraft:packed_ice"
    for k in range(12):
        a = math.radians(-90 + k * 30)
        x = int(round(5 * math.cos(a)))
        z = 64 + int(round(5 * math.sin(a)))
        facing = ["east", "south", "west", "north"][int(((k * 30) % 360) // 90)]
        for yy in range(0, k):
            cv.put(x, yy, z, "minecraft:snow_block")
        cv.put(x, k, z, stair("stone_brick", facing))
        for yy in range(k + 1, k + 4):
            cv.put(x, yy, z, AIR)
    for x in range(-3, 4):
        for z in range(68, 74):
            cv.put(x, 11, z, "minecraft:packed_ice")
    # 雷の棚
    cv.mark("room_b", 0, 12, 82)
    for n, (x, z, kind) in enumerate(((-6, 78, "jelly"), (6, 80, "slinger"), (0, 86, "warden"), (-6, 86, "tortoise")), 1):
        cv.mark(f"foe_b_{n}_{kind}", x, 12, z)
    B.chest(cv, "chest_2", 10, 12, 84, "west")
    cv.mark("trap_2_spark", 0, 12, 95)
    # 吹きさらしの氷の舞台: 床は青氷の輪、縁に氷柱
    for x in range(-16, 17):
        for z in range(100, 125):
            d = math.hypot(x / 15, (z - 112) / 12)
            if 0.35 < d < 0.6 and (x, 11, z) in cv.mats:
                cv.mats[(x, 11, z)] = "minecraft:blue_ice"
    for k in range(7):
        a = math.radians(200 + k * 22)
        F.ice_spire(cv, int(round(14 * math.cos(a))), 12, 112 + int(round(11 * math.sin(a))), 5 + k % 3)
    cv.mark("room_mid", 0, 12, 110)
    cv.mark("foe_mid_1_mid", 0, 12, 114)
    gate_out(cv, 125, 130, y=12, block="minecraft:blue_ice")
    cv.mark("view_mid", -10, 18, 100)
    cv.mark("view_mid_to", 2, 13, 114)
    F.ice_finish(cv, 7021)
    return cv


# ====================================================================== 熔岩の管の坑: 熔岩の大管
def deep_lava_2():
    cv = K.Cave(-36, 36, -8, 26, -12, 132, 8021)
    cv.room(0, 0, 6, 5, 5, 0, seed=11)
    cv.tunnel([(0, 0, 4), (-3, 0, 14), (0, 0, 22)], 3.0, 5.0, rough=0.08, seed=12)
    cv.room(0, 38, 15, 14, 10, 0, seed=13, wall_h=6)
    cv.tunnel([(0, 0, 52), (0, 0, 72)], 3.2, 5.0, rough=0.08, seed=14)
    cv.room(0, 86, 12, 10, 8, 0, seed=15, wall_h=6)
    cv.tunnel([(0, 0, 96), (0, 0, 100)], 2.4, 4.4, seed=16)
    cv.room(0, 113, 16, 13, 11, 0, seed=17, wall_h=7)
    cv.tunnel([(0, 0, 125), (0, 0, 131)], 1.6, 3.8, seed=18)
    cv.paint(D.ember_theme(8021))
    D.common_marks(cv)
    # 管の合流: 壁の熔岩の筋 (床の外の溝) と玄武岩の柱
    D.lava_trench(cv, -14, -11, 30, 46, 0)
    D.lava_trench(cv, 11, 14, 30, 46, 0)
    for (x, z) in ((-7, 28), (7, 30), (-7, 48), (7, 46)):
        pillar(cv, x, z, 0, 7, "minecraft:basalt[axis=y]")
    cv.mark("room_a", 0, 0, 38)
    for n, (x, z, kind) in enumerate(((-5, 32, "scorpion"), (5, 34, "spitter"), (-5, 44, "jackal"), (5, 44, "whirl"), (0, 48, "spitter"), (0, 30, "jackal")), 1):
        cv.mark(f"foe_a_{n}_{kind}", x, 0, z)
    B.chest(cv, "chest_1", -9, 0, 38, "east")
    # 入れ替わる熔岩の渡り (一層と同じ作り。区間は三つ)
    D.lava_trench(cv, -5, -2, 54, 72, -1)
    D.lava_trench(cv, 2, 5, 54, 72, -1)
    for z0, name in ((55, "lava_2_a"), (61, "lava_2_b"), (67, "lava_2_c_a")):
        for x in range(-1, 2):
            for z in range(z0, z0 + 4):
                cv.mats[(x, -2, z)] = "minecraft:magma_block"
                cv.mats[(x, -3, z)] = "minecraft:blackstone"
        for x in range(-1, 2):
            for z in range(z0, z0 + 4):
                cv.mats.pop((x, -1, z), None)
                cv.blk.pop((x, -1, z), None)
        D.box_mark(cv, name, -1, 1, -1, -1, z0, z0 + 3)
        for zz in (z0 - 1, z0 + 4):
            for x in range(-1, 2):
                cv.put(x, -1, zz, "minecraft:polished_blackstone_brick_slab[type=bottom,waterlogged=false]")
    cv.mark("trap_1_steam", 0, 0, 76)
    # 灰の民の鋳場: 鋳型 (大釜)・溶鉱炉・鎖の吊り鉤
    D.forge_ruin(cv, -8, 0, 90)
    D.forge_ruin(cv, 8, 0, 82)
    for (x, z) in ((-4, 80), (4, 92)):
        for y in range(3, 7):
            cv.put(x, y, z, CHAIN_Y)
        cv.put(x, 2, z, "minecraft:lantern[hanging=true,waterlogged=false]")
    cv.mark("room_b", 0, 0, 86)
    for n, (x, z, kind) in enumerate(((-5, 82, "scorpion"), (5, 86, "spitter"), (0, 90, "thrall"), (-6, 92, "whirl")), 1):
        cv.mark(f"foe_b_{n}_{kind}", x, 0, z)
    B.chest(cv, "chest_2", 10, 0, 88, "west")
    # 熔岩の島: 舞台のまわりに熔岩の輪 (渡る所は四つの橋)
    for x in range(-16, 17):
        for z in range(101, 126):
            d = math.hypot(x / 15, (z - 113) / 12)
            bridge = abs(x) <= 1 or abs(z - 113) <= 1
            if 0.86 < d < 1.0 and not bridge and (x, -1, z) in cv.mats and cv.is_air(x, 0, z):
                cv.mats[(x, -1, z)] = "minecraft:lava[level=0]"
                cv.mats[(x, -2, z)] = "minecraft:magma_block"
    for (x, z) in ((-9, 106), (9, 120), (10, 106), (-10, 120)):
        pillar(cv, x, z, 0, 5, "minecraft:polished_basalt[axis=y]", "minecraft:magma_block")
    cv.mark("room_mid", 0, 0, 111)
    cv.mark("foe_mid_1_mid", 0, 0, 115)
    gate_out(cv, 126, 130, block="minecraft:magma_block")
    cv.mark("view_mid", -10, 6, 101)
    cv.mark("view_mid_to", 2, 1, 115)
    D.ember_finish(cv, 8021)
    return cv


FLOORS2 = {"deep_fort": deep_fort_2, "deep_temple": deep_temple_2, "deep_ice": deep_ice_2, "deep_lava": deep_lava_2}
