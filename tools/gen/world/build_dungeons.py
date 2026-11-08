# -*- coding: utf-8 -*-
"""地下のダンジョンの層と主の間 (docs/design/55)。彫って data/skyabyss/dungeon/<ダンジョン>_<1|2|boss>.json に書く。

  python -X utf8 tools/gen/world/build_dungeons.py [bone_pit tide_cave storm_hollow molten_mine ...]

座標は層の手元 (原点 = 入口の立つ所、y 0 が立つ高さ)。印 (Java の dungeon/DungeonFloor が読む):
  entry        立つ所 (層に入ったとき・倒れて起きたとき)。向きは entry_face (同じ層の印へ向く)
  leave        帰り石 (天然磁石。右クリックで入口の間へ帰る)
  room_<r>     部屋の真ん中。人が近づくと foe_<r>_<n>_<種> の敵が出る (種は Dungeon.java の表)
  chest_<n>    宝箱 (人ごとに 20 分。中身は DungeonLoot)
  trap_<n>_<種> 罠の真ん中 (踏むと予告の輪のあとに落石・骨の棘・蒸気・雷)
  door_<n>_<条件>_lo / _hi   扉 (鉄格子) の箱。条件 key = 鍵持ち (key のついた敵) を倒して寄る、mid = 中ボスを倒す、crystal = 結晶を満たす
  next         次の層へ (鯨骨の坑はトロッコ。踏むと運ばれる)
  boss         主の湧く所 (主の間)。exit = 主のあとの帰りの光
  そのほか仕掛けの印 (tide_*、crystal_*、lava_*、burrow_*) は各ダンジョンの節
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import dungeon_kit as K  # noqa: E402
from dungeon_kit import AIR, CHAIN_Y, fence, frame, hsh, lantern, logs, rail, slab, stair  # noqa: E402

OUT = K.ROOT / "src/main/resources/data/skyabyss/dungeon"
NOTE = "tools/gen/world/build_dungeons.py が書く。手で直さない"


# ====================================================================== 鯨骨の坑 (岩鯨の丘陵、Lv 10)
BP_WALL = ["minecraft:stone", "minecraft:andesite", "minecraft:tuff", "minecraft:stone", "minecraft:cobblestone", "minecraft:andesite", "minecraft:stone"]
BP_FLOOR = ["minecraft:coarse_dirt", "minecraft:gravel", "minecraft:stone", "minecraft:packed_mud", "minecraft:cobblestone", "minecraft:coarse_dirt"]
BP_CEIL = ["minecraft:stone", "minecraft:andesite", "minecraft:tuff", "minecraft:dripstone_block"]


def bone_theme(seed):
    def wall(x, y, z, band, fossil):
        if fossil > 0.80:
            return "minecraft:bone_block[axis=y]"
        if hsh(x, y, z, seed + 5) < 0.025:
            return "minecraft:coal_ore"
        if hsh(x, y, z, seed + 6) < 0.012:
            return "minecraft:iron_ore"
        return BP_WALL[int(band * 3.2 * len(BP_WALL) + y * 0.15) % len(BP_WALL)]

    def floor(x, y, z, band, fossil):
        # まとまった斑 (帯のノイズの段で材料を決め、ところどころ混ぜる)
        if fossil > 0.86:
            return "minecraft:bone_block[axis=y]"
        if hsh(x, y, z, seed + 9) < 0.12:
            return K.pick(BP_FLOOR, x, y, z, seed + 7)
        k = (band * 3.0) % 1.0
        return ("minecraft:coarse_dirt" if k < 0.34 else "minecraft:gravel" if k < 0.52 else
                "minecraft:packed_mud" if k < 0.68 else "minecraft:stone" if k < 0.86 else "minecraft:cobblestone")

    def ceil(x, y, z, band, fossil):
        if fossil > 0.82:
            return "minecraft:bone_block[axis=y]"
        return K.pick(BP_CEIL, x, y, z, seed + 8)
    return {"wall": wall, "floor": floor, "ceil": ceil}


def drip(n):
    if n == 1:
        return ["minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]"]
    if n == 2:
        return ["minecraft:pointed_dripstone[thickness=frustum,vertical_direction=down,waterlogged=false]",
                "minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]"]
    return ["minecraft:pointed_dripstone[thickness=base,vertical_direction=down,waterlogged=false]",
            "minecraft:pointed_dripstone[thickness=frustum,vertical_direction=down,waterlogged=false]",
            "minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]"]


def stones(cv, cx, cz, y, n=1, mats=("minecraft:cobblestone", "minecraft:andesite", "minecraft:gravel")):
    """床の上の小さなずり山"""
    for dx in range(-n, n + 1):
        for dz in range(-n, n + 1):
            h = n + 1 - max(abs(dx), abs(dz)) - (1 if hsh(cx + dx, cz + dz, 9) < 0.3 else 0)
            for k in range(max(0, h)):
                if cv.is_air(cx + dx, y + k, cz + dz):
                    cv.put(cx + dx, y + k, cz + dz, K.pick(list(mats), cx + dx, y + k, cz + dz, 3))


def rails_along(cv, pts, y_of=None):
    """坑道の床にトロッコの線路 (折れ線を一マスずつ)"""
    for a, c in zip(pts, pts[1:]):
        n = int(max(abs(c[0] - a[0]), abs(c[2] - a[2])))
        for i in range(n + 1):
            t = i / max(1, n)
            x = int(round(a[0] + (c[0] - a[0]) * t))
            z = int(round(a[2] + (c[2] - a[2]) * t))
            y = int(round(a[1] + (c[1] - a[1]) * t))
            fy = cv.floor_y(x, z, y, 3)
            if fy is not None and (x, fy, z) not in cv.blk:
                shape = "north_south" if abs(c[2] - a[2]) >= abs(c[0] - a[0]) else "east_west"
                cv.put(x, fy, z, rail(shape))


def frames_along(cv, pts, every=5, w=2, h=4, lamp_every=3, wood="spruce"):
    k = 0
    for a, c in zip(pts, pts[1:]):
        L = math.hypot(c[0] - a[0], c[2] - a[2])
        along = "z" if abs(c[2] - a[2]) >= abs(c[0] - a[0]) else "x"
        steps = int(L // every)
        for i in range(1, steps + 1):
            t = i * every / max(L, 1)
            x = int(round(a[0] + (c[0] - a[0]) * t))
            z = int(round(a[2] + (c[2] - a[2]) * t))
            y = int(round(a[1] + (c[1] - a[1]) * t))
            fy = cv.floor_y(x, z, y, 3)
            if fy is None:
                continue
            frame(cv, x, fy, z, along, w, h, wood, lamp=(k % lamp_every == 0))
            k += 1


def walkway(cv, x0, x1, z0, z1, y, wood="spruce", rail_side=None):
    """木組みの足場の床 (立つ高さ y。板は y-1)。下に柱"""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            cv.put(x, y - 1, z, f"minecraft:{wood}_planks" if hsh(x, z, 77) < 0.8 else slab(wood, "top"))
            cv.clear(x, x, y, y + 2, z, z)
    for x in (x0, x1):
        for z in (z0, z1):
            for yy in range(y - 2, y - 30, -1):
                if not cv.is_air(x, yy, z):
                    break
                cv.put(x, yy, z, logs(f"{wood}_log"))
    if rail_side:
        side, at = rail_side
        rng = range(x0, x1 + 1) if side == "z" else range(z0, z1 + 1)
        for v in rng:
            px, pz = (v, at) if side == "z" else (at, v)
            cv.put(px, y, pz, fence(wood))


def ramp(cv, x, z0, z1, y0, wood="spruce", w=1):
    """南北の木の坂 (z0 → z1 で一つずつ上がる)"""
    step = 1 if z1 > z0 else -1
    facing = "south" if step > 0 else "north"
    # 最初の段は床の上に置く (床より高いと跳ばないと乗れない。2026-11-08 の声「上らせる気の無い建築」)
    fy = cv.floor_y(x, z0, y0, 4)
    y = fy if fy is not None else y0
    for z in range(z0, z1 + step, step):
        for dx in range(-w, w + 1):
            cv.put(x + dx, y, z, stair(wood, facing))
            cv.clear(x + dx, x + dx, y + 1, y + 3, z, z)
            for yy in range(y - 1, y - 20, -1):
                if not cv.is_air(x + dx, yy, z):
                    break
                if dx in (-w, w) and (z - z0) % 3 == 0:
                    cv.put(x + dx, yy, z, logs(f"{wood}_log"))
        y += 1
    return y


def checkpoint_stone(cv, x, y, z, soul=True):
    """戻り石 (層の始まりの印。踏めば戻り石が点く)"""
    cv.put(x, y, z, "minecraft:chiseled_stone_bricks")
    cv.put(x, y + 1, z, lantern(False, soul))
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if cv.is_air(x + dx, y, z + dz):
            cv.put(x + dx, y - 1, z + dz, "minecraft:polished_andesite")


def leave_stone(cv, x, y, z):
    cv.put(x, y, z, "minecraft:lodestone")
    cv.mark("leave", x, y, z)


def bars(cv, name, x0, x1, y0, y1, z0, z1):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                if cv.is_air(x, y, z):
                    cv.put(x, y, z, "minecraft:iron_bars[east=true,north=false,south=false,waterlogged=false,west=true]" if z0 == z1
                           else "minecraft:iron_bars[east=false,north=true,south=true,waterlogged=false,west=false]")
    cv.mark(f"{name}_lo", x0, y0, z0)
    cv.mark(f"{name}_hi", x1, y1, z1)


def chest(cv, name, x, y, z, facing="north"):
    cv.put(x, y, z, f"minecraft:chest[facing={facing},type=single,waterlogged=false]")
    cv.mark(name, x, y, z)


def skulls(cv, pts):
    for i, (x, y, z) in enumerate(pts):
        if cv.is_air(x, y, z):
            cv.put(x, y, z, f"minecraft:skeleton_skull[rotation={(i * 5) % 16}]")


def bone_pile(cv, x, y, z, seed):
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            if hsh(x + dx, z + dz, seed) < 0.55 and cv.is_air(x + dx, y, z + dz):
                cv.put(x + dx, y, z + dz, "minecraft:bone_block[axis=%s]" % ("x" if hsh(x, dz, seed) < 0.5 else "z"))
    if cv.is_air(x, y + 1, z):
        cv.put(x, y + 1, z, "minecraft:skeleton_skull[rotation=%d]" % int(hsh(x, z, seed + 1) * 16))


def rib(cv, cx, cz, y0, r, along="x", thick=1, lift=0.9):
    """横たわる巨神の肋骨 (縦の半円の弧)。along = 弧の面"""
    for k in range(0, 181, 2):
        t = math.radians(k)
        u = r * math.cos(t)
        v = r * math.sin(t) * lift
        x, z = (cx + u, cz) if along == "x" else (cx, cz + u)
        for d in range(thick):
            px, pz = (int(round(x)), int(round(z)) + d) if along == "x" else (int(round(x)) + d, int(round(z)))
            cv.put(px, int(round(y0 + v)), pz, "minecraft:bone_block[axis=y]")
            cv.put(px, int(round(y0 + v)) + 1, pz, "minecraft:bone_block[axis=y]")


def beam(cv, x0, x1, y, z, block="minecraft:bone_block[axis=x]", w=1):
    for x in range(x0, x1 + 1):
        for dz in range(w):
            for dy in range(w):
                cv.put(x, y + dy, z + dz, block)


def finish_common(cv, seed, light_step=7, light_level=11, drips=0.035, lichen=0.012, scatter=0.025):
    cv.ceiling_drips(drip, drips, seed + 101, 3)
    cv.wall_lichen(lichen, seed + 102)
    cv.floor_scatter(["minecraft:brown_mushroom", "minecraft:stone_button[face=floor,facing=north,powered=false]", "minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"],
                     scatter, seed + 103)
    cv.lights(light_step, light_level, seed + 104)


def bone_pit_1():
    """一層目: 掘り場と木組みの足場。入口の間 → 坑道 → 掘り場 (A) → 落石の坑道 → 足場の大部屋 (B、鍵を持つ旗手) → 扉 → トロッコ乗り場"""
    cv = K.Cave(-34, 46, -8, 26, -12, 122, 1011)
    # 入口の間
    cv.room(0, 0, 7, 6, 6, 0, seed=11)
    # 坑道 1
    t1 = [(0, 0, 4), (2, 0, 13), (-3, -1, 21), (-1, -2, 30), (1, -2, 33)]
    cv.tunnel(t1, 1.9, 4.2, seed=12)
    # 掘り場 (A): 西の壁に巨神の肋骨の化石、東に二段の足場
    cv.room(2, 46, 17, 13, 12, -2, seed=13, wall_h=4)
    cv.room(-8, 52, 7, 6, 7, -1, seed=14)
    # 坑道 2 (落石)
    t2 = [(14, -2, 56), (17, -2, 63), (19, -1, 70), (20, -1, 74)]
    cv.tunnel(t2, 1.8, 4.0, seed=15)
    # 足場の大部屋 (B)
    cv.room(20, 86, 13, 11, 16, -1, seed=16, wall_h=5)
    # 扉の先とトロッコ乗り場
    t3 = [(20, -1, 96), (20, -1, 104)]
    cv.tunnel(t3, 2.2, 4.6, seed=17)
    cv.room(20, 110, 7, 5, 6, -1, seed=18)
    # 下りの坑 (乗り場から南へ暗く下る)
    cv.tunnel([(20, -1, 114), (20, -4, 119), (20, -6, 121)], 1.6, 3.6, seed=19)

    cv.paint(bone_theme(1011))

    # ---- 入口の間
    cv.mark("entry", 0, 0, -2)
    checkpoint_stone(cv, 3, 0, -4)
    cv.mark("cp", 3, 1, -4)
    leave_stone(cv, -3, 0, -4)
    for x, z in ((-5, -2), (5, -2)):
        cv.put(x, 0, z, "minecraft:barrel[facing=up,open=false]")
    cv.put(-5, 1, -2, lantern())
    frames_along(cv, t1, 5, 2, 4)
    rails_along(cv, t1)
    # ---- 掘り場 A
    cv.mark("room_a", 2, -2, 46)
    # 西の壁の化石の肋骨 (壁から突き出す弧)
    for i, z in enumerate((38, 43, 48, 53)):
        rib(cv, -16 + (i % 2), z, -2, 7 + (i % 2), "x", 1, 0.8)
    # 東の足場 (二段)
    walkway(cv, 9, 16, 38, 54, 4, rail_side=("x", 9))
    ramp(cv, 12, 56, 51, -1)
    # 二段目の足場は置かない (洞の天井が低く、立つ所が岩に埋まっていた。2026-11-08 の声「上らせる気の無い建築」)
    # 掘り場の道具・荷車・ずり山
    for (x, z) in ((-4, 40), (6, 36), (-2, 55), (8, 50)):
        stones(cv, x, z, -2, 1)
    for (x, z) in ((-10, 46), (-6, 50), (0, 38)):
        bone_pile(cv, x, -2, z, 31 + x)
    for (x, z) in ((4, 44), (5, 44), (4, 45)):
        cv.put(x, -2, z, "minecraft:barrel[facing=up,open=false]" if (x + z) % 2 else "minecraft:spruce_planks")
    cv.put(4, -1, 44, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]")
    rails_along(cv, [(1, -2, 33), (3, -2, 40), (6, -2, 48), (12, -2, 55)])
    for (x, y, z) in ((2, 6, 46), (-6, 5, 44), (10, 7, 52)):
        for k in range(4):
            if cv.is_air(x, y + k, z):
                cv.put(x, y + k, z, CHAIN_Y)
        cv.put(x, y - 1, z, lantern(True))
    chest(cv, "chest_1", 16, 4, 41, "west")
    cv.mark("view_dig", -6, 2, 34)
    cv.mark("view_dig_to", 8, 2, 50)
    cv.mark("foe_a_1_footman", -2, -2, 44)
    cv.mark("foe_a_2_footman", 4, -2, 50)
    cv.mark("foe_a_3_footman", -8, -1, 52)
    cv.mark("foe_a_4_bowman", 12, 4, 44)
    cv.mark("foe_a_5_bowman", 14, 4, 40)
    cv.mark("foe_a_6_miner", 0, -2, 40)
    # ---- 坑道 2 と落石の罠
    frames_along(cv, t2, 4, 2, 4, lamp_every=2)
    cv.mark("trap_1_rocks", 17, -2, 63)
    for (x, y, z) in ((16, 2, 62), (18, 2, 64)):
        if cv.is_air(x, y, z):
            cv.put(x, y, z, "minecraft:cobbled_deepslate")
    # ---- 足場の大部屋 B
    cv.mark("room_b", 20, -1, 86)
    walkway(cv, 9, 14, 78, 94, 5, rail_side=("x", 14))
    walkway(cv, 9, 31, 76, 79, 5, rail_side=("z", 79))
    walkway(cv, 26, 31, 76, 90, 10, rail_side=("x", 26))
    # 手すりが足場どうしのつなぎ目をふさがないように (二つ目の手すり z 79 が一つ目の足場を横切り、三つ目の手すり x 26 が坂の上をふさいでいた)
    cv.clear(9, 14, 5, 5, 79, 79)
    cv.clear(26, 26, 10, 10, 76, 79)
    # 床から一段目への坂 (南の壁ぎわ、北向きに上がる)
    yy = cv.floor_y(12, 95, -1, 4)
    yy = -1 if yy is None else yy
    for z in range(95, 89, -1):
        for dx in (-1, 0, 1):
            cv.put(12 + dx, yy, z, stair("spruce", "north"))
            cv.clear(12 + dx, 12 + dx, yy + 1, yy + 3, z, z)
        yy += 1
    # 一段目から二段目への坂 (東の足場へ)
    yy = 5
    for x in range(20, 26):
        for dz in (0, 1):
            cv.put(x, yy, 77 + dz, stair("spruce", "east"))
            cv.clear(x, x, yy + 1, yy + 3, 77 + dz, 77 + dz)
        yy += 1
    for (x, y, z) in ((20, 12, 86), (14, 12, 90), (28, 14, 84)):
        for k in range(3):
            if cv.is_air(x, y + k, z):
                cv.put(x, y + k, z, CHAIN_Y)
        cv.put(x, y - 1, z, lantern(True))
    for (x, z) in ((24, 92), (16, 82), (22, 80)):
        stones(cv, x, z, -1, 1)
    bone_pile(cv, 18, -1, 90, 55)
    chest(cv, "chest_2", 30, 10, 88, "west")
    cv.mark("view_hall", 20, 3, 95)
    cv.mark("view_hall_to", 20, 6, 78)
    cv.mark("foe_b_1_standard_key", 28, 10, 82)
    cv.mark("foe_b_2_footman", 18, -1, 84)
    cv.mark("foe_b_3_footman", 24, -1, 88)
    cv.mark("foe_b_4_bowman", 11, 5, 84)
    cv.mark("foe_b_5_miner", 20, 5, 77)
    # ---- 扉 1 (旗手の鍵) とトロッコ乗り場
    bars(cv, "door_1_key", 18, 22, -1, 2, 97, 97)
    frames_along(cv, t3, 4, 2, 4, lamp_every=1)
    rails_along(cv, [(20, -1, 99), (20, -1, 113), (20, -4, 119), (20, -6, 121)])
    cv.put(20, -1, 110, "minecraft:powered_rail[powered=true,shape=north_south,waterlogged=false]")
    cv.mark("next", 20, -1, 110)
    cv.mark("view_cart", 20, 1, 104)
    cv.mark("view_cart_to", 20, -2, 114)
    for (x, z) in ((16, 108), (24, 108)):
        cv.put(x, -1, z, "minecraft:barrel[facing=up,open=false]")
        cv.put(x, 0, z, lantern())
    cv.put(23, -1, 112, "minecraft:spruce_planks")
    cv.put(23, 0, 112, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]")
    finish_common(cv, 1011)
    return cv


def bone_pit_2():
    """二層目: 骨の層を貫く大縦坑。上の縁のトロッコの着き場 → 縦坑の壁をらせんに下る道 (横穴 2・骨の棘の罠) → 底の広場 (中ボス 岩背の大猪) → 扉 → 主の間への坑"""
    cv = K.Cave(-36, 36, -6, 46, -44, 44, 1021)
    R = 13
    # 縦坑
    cv.shaft(0, 0, R, 0, 44, seed=21)
    # 底の広場
    cv.room(0, 0, 16, 16, 12, 0, seed=22, wall_h=6)
    # 上の着き場 (北の縁)
    cv.room(0, -21, 6, 5, 6, 36, seed=23)
    cv.tunnel([(0, 36, -27), (0, 36, -17)], 2.2, 4.2, seed=24)
    # らせんの道: 北 (36) から時計回りに 1.4 周下りて南の底へ
    spiral = []
    turns = 1.45
    n = 60
    for i in range(n + 1):
        t = i / n
        a = math.radians(-90 + 360 * turns * t)
        r = R + 2.4
        spiral.append((r * math.cos(a), 36 - 36 * t, r * math.sin(a)))
    cv.tunnel(spiral, 2.3, 4.6, seed=25)
    # 横穴 (二つ)
    g1a = math.radians(-90 + 360 * turns * 0.28)
    g1 = (int((R + 9) * math.cos(g1a)), int(36 - 36 * 0.28), int((R + 9) * math.sin(g1a)))
    cv.room(g1[0], g1[2], 6, 5, 6, g1[1], seed=26)
    g2a = math.radians(-90 + 360 * turns * 0.72)
    g2 = (int((R + 9) * math.cos(g2a)), int(36 - 36 * 0.72), int((R + 9) * math.sin(g2a)))
    cv.room(g2[0], g2[2], 6, 5, 6, g2[1], seed=27)
    # 扉の先の坑 (南へ)
    tb = [(0, 0, 17), (0, 0, 30), (3, -1, 38), (3, -1, 42)]
    cv.tunnel(tb, 2.0, 4.4, seed=28)

    cv.paint(bone_theme(1021))

    # 着き場
    cv.mark("entry", 0, 36, -23)
    checkpoint_stone(cv, 3, 36, -24)
    cv.mark("cp", 3, 37, -24)
    leave_stone(cv, -3, 36, -24)
    rails_along(cv, [(0, 36, -27), (0, 36, -19)])
    cv.put(0, 36, -27, "minecraft:rail[shape=north_south,waterlogged=false]")
    # らせんの道の縁の柵 (縦坑の側)
    for i, p in enumerate(spiral):
        a = math.atan2(p[2], p[0])
        x, z = int(round((R + 0.2) * math.cos(a))), int(round((R + 0.2) * math.sin(a)))
        fy = cv.floor_y(x, z, int(round(p[1])), 3)
        if fy is not None and i % 1 == 0 and cv.is_air(x, fy, z):
            cv.put(x, fy, z, fence("spruce") if i % 6 else logs("spruce_log"))
            if i % 6 == 0 and cv.is_air(x, fy + 1, z):
                cv.put(x, fy + 1, z, lantern())
    # 縦坑を渡る巨神の肋骨 (二本の梁と、壁から生える弧)
    beam(cv, -R - 2, R + 2, 22, -2, "minecraft:bone_block[axis=x]", 2)
    beam(cv, -R - 2, R + 2, 30, 5, "minecraft:bone_block[axis=x]", 2)
    for z, y in ((-6, 8), (6, 14)):
        rib(cv, 0, z, y, R - 1, "x", 1, 0.6)
    # 縦坑の真ん中に垂れる鎖と灯
    for y in range(12, 44):
        cv.put(0, y, 0, CHAIN_Y)
    cv.put(0, 11, 0, lantern(True))
    for y in range(18, 44):
        cv.put(-6, y, 7, CHAIN_Y)
    cv.put(-6, 17, 7, lantern(True, True))
    # 横穴の敵と宝箱
    cv.mark("room_g1", g1[0], g1[1], g1[2])
    cv.mark("foe_g1_1_footman", g1[0] + 2, g1[1], g1[2])
    cv.mark("foe_g1_2_bowman", g1[0] - 2, g1[1], g1[2] + 1)
    cv.mark("foe_g1_3_miner", g1[0], g1[1], g1[2] - 2)
    chest(cv, "chest_1", g1[0] + int(math.copysign(4, g1[0])), g1[1], g1[2], "north")
    bone_pile(cv, g1[0] - 3, g1[1], g1[2] - 2, 61)
    cv.mark("room_g2", g2[0], g2[1], g2[2])
    cv.mark("foe_g2_1_footman", g2[0] + 2, g2[1], g2[2] - 1)
    cv.mark("foe_g2_2_footman", g2[0] - 2, g2[1], g2[2] + 1)
    cv.mark("foe_g2_3_bowman", g2[0], g2[1], g2[2] + 3)
    chest(cv, "chest_2", g2[0] + int(math.copysign(4, g2[0])), g2[1], g2[2], "north")
    bone_pile(cv, g2[0] + 2, g2[1], g2[2] + 3, 62)
    # らせんの道の上の敵 (ところどころ)
    for k, t in enumerate((0.12, 0.45, 0.6, 0.86)):
        p = spiral[int(t * n)]
        fy = cv.floor_y(int(round(p[0])), int(round(p[2])), int(round(p[1])), 3)
        if fy is not None:
            cv.mark(f"room_s{k}", int(round(p[0])), fy, int(round(p[2])))
            cv.mark(f"foe_s{k}_1_{'bowman' if k % 2 else 'footman'}", int(round(p[0])), fy, int(round(p[2])))
    # 骨の棘の罠 (らせんの道の半ば)
    p = spiral[int(0.52 * n)]
    fy = cv.floor_y(int(round(p[0])), int(round(p[2])), int(round(p[1])), 3)
    cv.mark("trap_1_spikes", int(round(p[0])), fy if fy is not None else int(round(p[1])), int(round(p[2])))
    # 撮影の視点: 着き場から縦坑を見下ろす・底から見上げる
    cv.mark("view_rim", 0, 42, -10)
    cv.mark("view_rim_to", 0, 10, 6)
    cv.mark("view_bottom", 8, 2, 10)
    cv.mark("view_bottom_to", -4, 18, -6)
    # 底の広場: 中ボス (岩背の大猪) と骨の山
    cv.mark("room_mid", 0, 0, 0)
    cv.mark("foe_mid_1_mid", 0, 0, 4)
    for (x, z) in ((-9, -6), (8, -9), (10, 6), (-11, 5)):
        bone_pile(cv, x, 0, z, 70 + x)
    for (x, z) in ((-5, 10), (6, 11)):
        stones(cv, x, z, 0, 1)
    # 扉 2 (中ボスを倒すと開く) と主の間への坑
    bars(cv, "door_2_mid", -2, 2, 0, 3, 18, 18)
    frames_along(cv, tb, 4, 2, 4, lamp_every=1)
    cv.put(3, -1, 41, "minecraft:soul_lantern[hanging=false,waterlogged=false]")
    cv.mark("next", 3, -1, 41)
    for x in range(1, 6):
        for z in range(40, 43):
            if (x, -2, z) in cv.mats:
                cv.mats[(x, -2, z)] = "minecraft:polished_deepslate"
    finish_common(cv, 1021, light_step=6, light_level=11)
    return cv


def bone_pit_boss():
    """主の間: 骨の壁の丸い大洞。壁に六つの掘り穴 (主が潜っては出る)。南に入口、北に帰りの光"""
    cv = K.Cave(-30, 30, -4, 22, -30, 30, 1031)
    cv.room(0, 0, 19, 19, 15, 0, seed=31, wall_h=6)
    cv.tunnel([(0, 0, 18), (0, 0, 27)], 2.0, 4.2, seed=32)
    holes = []
    for k in range(6):
        a = math.radians(30 + k * 60)
        x0, z0 = 17 * math.cos(a), 17 * math.sin(a)
        x1, z1 = 25 * math.cos(a), 25 * math.sin(a)
        cv.tunnel([(x0, 0, z0), (x1, 1, z1)], 2.4, 4.8, seed=33 + k)
        holes.append((int(round(19 * math.cos(a))), 0, int(round(19 * math.sin(a)))))

    def theme_boss(seed):
        base = bone_theme(seed)

        def wall(x, y, z, band, fossil):
            if fossil > 0.62 or (y + int(band * 6)) % 7 == 0:
                return "minecraft:bone_block[axis=y]"
            return base["wall"](x, y, z, band, fossil)
        return {"wall": wall, "floor": base["floor"], "ceil": base["ceil"]}

    cv.paint(theme_boss(1031))
    cv.mark("entry", 0, 0, 22)
    cv.mark("cp", 0, 0, 22)
    cv.mark("boss", 0, 0, -3)
    cv.mark("exit", 0, 0, -14)
    cv.mark("view_lair", 6, 4, 16)
    cv.mark("view_lair_to", -2, 2, -6)
    for k, h in enumerate(holes):
        cv.mark(f"burrow_{k + 1}", *h)
    # 床の骨の山と頭骨、壁ぎわの巨神の肋骨
    for k in range(10):
        a = math.radians(k * 36 + 10)
        bone_pile(cv, int(round(13 * math.cos(a))), 0, int(round(13 * math.sin(a))), 80 + k)
    for z in (-10, 10):
        rib(cv, 0, z, 0, 17, "x", 1, 0.75)
    for (x, z) in ((-4, 16), (4, 16)):
        cv.put(x, 0, z, logs("spruce_log"))
        cv.put(x, 1, z, logs("spruce_log"))
        cv.put(x, 2, z, lantern())
    finish_common(cv, 1031, light_step=6, light_level=11, drips=0.05)
    return cv


BUILDERS = {
    "bone_pit": [("1", bone_pit_1), ("2", bone_pit_2), ("boss", bone_pit_boss)],
}


if __name__ == "__main__":
    import dungeons_more
    BUILDERS.update(dungeons_more.BUILDERS)
    names = sys.argv[1:] or list(BUILDERS)
    for name in names:
        for part, fn in BUILDERS[name]:
            cv = fn()
            kb, cells, runs = cv.write(OUT / f"{name}_{part}.json", NOTE)
            print(f"{name}_{part}", kb, "KB", cells, "cells", runs, "runs", len(cv.marks), "marks", flush=True)
        import dungeon_entrances as E
        if name in E.ENTRANCES:
            kb, n = E.ENTRANCES[name]()
            print(f"poi_dungeon_{name}", kb, "KB", n, "blocks", flush=True)
