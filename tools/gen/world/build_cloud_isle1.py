# -*- coding: utf-8 -*-
"""終章「雲海」の島 1「鐘の島」(docs/design/50 の 4.2.1)。彫って data/skyabyss/finale/isle1.json に書く (FinaleWorld が一度だけ流し込む)。

  着く所の北の橋の台 (0, 64, -36) から、白い石の橋が上りながら北へ (地面 64 → 76)。
  大きな島 (中心 (0, -150)、南北 80・東西 48、地面 y 76): 前の都の大通り (南北) と十字の通り、広場 (真ん中に鳴り鐘 = 印 bell_c)、崩れた家 6。
  北の端に折れた鐘楼 (9 x 9、高さ 27)。南の面に扉 (印 door)。上は主の間 (丸い床 半径 5 の石、まわり半径 5〜11 は鐘雲。小さな鐘 4 = 印 arena_bell_1〜4)。
  まわりのかけら 3 (西 = 礼拝堂の跡・東 = 市の屋台・北西 = 井戸)。それぞれに鳴り鐘 (印 bell_w / bell_e / bell_n) と、大きな島から渡る鐘雲の道 (幅 3)。

  python -X utf8 tools/gen/world/build_cloud_isle1.py
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cloud_kit as K  # noqa: E402

OUT = K.ROOT / "src/main/resources/data/skyabyss/finale/isle1.json"
G = 76
CZ = -150
g = K.Grid(-80, 80, -228, -37, 14, 112)
BC = "skyabyss:bell_cloud[solid=false]"

# ---------------------------------------------------------------- 島とかけら
K.island(g, 0, CZ, 24, 40, G, 34, 101)
FRAG = {"w": (-58, -140, 10, 9, 74), "e": (56, -168, 11, 10, 78), "n": (-34, -214, 9, 8, 82)}
for k, (fx, fz, rx, rz, fg) in FRAG.items():
    K.island(g, fx, fz, rx, rz, fg, 18, 110 + ord(k))


def on_main(x, z):
    return math.hypot(x / 24, (z - CZ) / 40) < 1.0


# ---------------------------------------------------------------- 大通りと十字の通りと広場
def pave(x, z, t):
    h = K.hsh(x, z, 120)
    b = "minecraft:stone_bricks" if h < 0.45 else "minecraft:polished_andesite" if h < 0.7 else "minecraft:cracked_stone_bricks" if h < 0.85 else "minecraft:mossy_stone_bricks"
    g.put(x, t, z, b)
    for y in range(t + 1, t + 5):
        g.put(x, y, z, K.AIR)


for (x, z), t in list(g.tops.items()):
    if not on_main(x, z):
        continue
    if abs(x) <= 3 and -186 <= z <= -112:
        # 縁の割れ目: ところどころ欠けて草
        if not (abs(x) == 3 and K.hsh(x, z, 121) < 0.2):
            pave(x, z, t)
    if -153 <= z <= -147 and abs(x) <= 20:
        pave(x, z, t)
    if math.hypot(x, z - CZ) <= 8.5:
        pave(x, z, t)
# 広場の縁 (方解石の細い輪) と、真ん中の鐘の台
for x in range(-9, 10):
    for z in range(CZ - 9, CZ + 10):
        d = math.hypot(x, z - CZ)
        if 7.8 <= d <= 8.5:
            t = g.top(x, z)
            if t is not None:
                g.put(x, t, z, "minecraft:calcite")
t0 = g.top(0, CZ)
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        g.put(dx, t0 + 1, CZ + dz, "minecraft:stone_brick_slab[type=bottom,waterlogged=false]" if (dx, dz) != (0, 0) else "minecraft:chiseled_stone_bricks")
g.put(0, t0 + 2, CZ, "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
g.put(0, t0 + 3, CZ, "minecraft:bell[attachment=floor,facing=south,powered=false]")
g.marks["bell_c"] = [0, t0 + 3, CZ]
g.marks["center"] = [0, t0 + 1, CZ + 3]

# ---------------------------------------------------------------- 崩れた家 (大通りの両側)
for i, (x0, z0, facing) in enumerate(((-16, -128, "east"), (-16, -140, "east"), (-16, -170, "east"), (7, -126, "west"), (7, -140, "west"), (7, -172, "west"))):
    K.ruin_house(g, x0, z0, 9, 8, 130 + i, "south" if i % 2 else "north")

# ---------------------------------------------------------------- 折れた鐘楼 (北の端)
TX, TZ = 0, -186
tt = max(g.top(x, z) or G for x in range(TX - 4, TX + 5) for z in range(TZ - 4, TZ + 5))
TOP = tt + 27
for x in range(TX - 4, TX + 5):
    for z in range(TZ - 4, TZ + 5):
        wall = abs(x - TX) == 4 or abs(z - TZ) == 4
        for y in range(tt - 3, TOP):
            if wall:
                b = "minecraft:stone_bricks" if K.hsh(x, y, z, 140) < 0.7 else "minecraft:mossy_stone_bricks" if K.hsh(x, y, z, 141) < 0.6 else "minecraft:cracked_stone_bricks"
                if (y - tt) % 9 == 0:
                    b = "minecraft:polished_andesite"
                if abs(x - TX) == 4 and abs(z - TZ) == 4:
                    b = "minecraft:stone_bricks"
            else:
                b = "minecraft:stone" if y < TOP - 1 else "minecraft:stone_bricks"
            g.put(x, y, z, b)
# 細い窓 (縦の割れ目)
for y in range(tt + 8, TOP - 3, 9):
    for x, z in ((TX, TZ - 4), (TX, TZ + 4), (TX - 4, TZ), (TX + 4, TZ)):
        for k in range(3):
            g.put(x, y + k, z, "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]")
# 控え壁
for sx in (-5, 5):
    for sz in (-5, 5):
        for y in range(tt - 2, tt + 10 - abs(sx)):
            g.put(TX + sx, y, TZ + sz, "minecraft:stone_bricks")
# 扉 (南の面の真ん中。印 door = 彫りの石。まわりは鉄格子の門)
DOOR = (TX, tt + 1, TZ + 4)
for dx in (-1, 0, 1):
    for y in range(tt + 1, tt + 4):
        g.put(TX + dx, y, TZ + 4, "minecraft:iron_bars[east=%s,north=false,south=false,waterlogged=false,west=%s]" % ("true" if dx < 1 else "false", "true" if dx > -1 else "false"))
g.put(*DOOR, "minecraft:chiseled_stone_bricks")
g.put(TX, tt + 4, TZ + 4, "minecraft:bell[attachment=ceiling,facing=south,powered=false]")
g.marks["door"] = list(DOOR)
g.marks["door_front"] = [TX, tt + 1, TZ + 6]
# 扉の前の階段 (大通りから)
for z in range(TZ + 5, TZ + 8):
    for x in range(TX - 2, TX + 3):
        g.put(x, tt, z, "minecraft:stone_bricks")
        for y in range(tt + 1, tt + 5):
            g.put(x, y, z, K.AIR)

# ---------------------------------------------------------------- 主の間 (鐘楼の上の丸い床と、まわりの鐘雲の輪)
AR = 11
for x in range(TX - AR - 1, TX + AR + 2):
    for z in range(TZ - AR - 1, TZ + AR + 2):
        d = math.hypot(x - TX, z - TZ)
        if d <= 5.2:
            b = "minecraft:polished_andesite" if (x + z) % 2 == 0 else "minecraft:stone_bricks"
            if 2.6 < d < 3.4:
                b = "minecraft:chiseled_stone_bricks"
            g.put(x, TOP, z, b)
        elif d <= AR:
            g.put(x, TOP, z, BC)
        elif d <= AR + 0.8 and K.hsh(x, z, 150) < 0.45:
            # 崩れた胸壁 (ところどころ)
            g.put(x, TOP, z, "minecraft:stone_bricks")
            g.put(x, TOP + 1, z, "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
        for y in range(TOP + 1, TOP + 8):
            if d <= AR and g.get(x, y, z) is None:
                g.put(x, y, z, K.AIR)
# 床の下の鐘雲の輪の支え (塔の外の輪の下は空。見た目に梁を 4 本)
for a in range(4):
    ang = a * math.pi / 2 + math.pi / 4
    for r in range(5, AR + 1):
        g.put(round(TX + math.cos(ang) * r), TOP - 1, round(TZ + math.sin(ang) * r), "minecraft:stripped_spruce_log[axis=y]")
for i, ang in enumerate((45, 135, 225, 315)):
    a = math.radians(ang)
    x, z = round(TX + math.cos(a) * 3.6), round(TZ + math.sin(a) * 3.6)
    g.put(x, TOP + 1, z, "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    g.put(x, TOP + 2, z, "minecraft:bell[attachment=floor,facing=south,powered=false]")
    g.marks["arena_bell_%d" % (i + 1)] = [x, TOP + 2, z]
g.marks["arena_entry"] = [TX, TOP + 1, TZ + 4]
g.marks["arena_back"] = [TX, TOP + 1, TZ - 4]
g.put(TX, TOP + 1, TZ - 4, "minecraft:lodestone")
g.marks["lord_spawn"] = [TX, TOP + 1, TZ]
g.marks["arena"] = [TX, TOP, TZ]

# ---------------------------------------------------------------- かけらの建物と鳴り鐘
def bell_post(x, z, key):
    t = g.top(x, z)
    for y in range(t + 1, t + 4):
        g.put(x, y, z, "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]" if y < t + 3 else "minecraft:chiseled_stone_bricks")
    g.put(x, t + 4, z, "minecraft:bell[attachment=floor,facing=south,powered=false]")
    g.marks["bell_" + key] = [x, t + 4, z]


# 西: 礼拝堂の跡 (壁の名残と窓の枠)
fx, fz = FRAG["w"][:2]
t = g.top(fx, fz)
for x in range(fx - 3, fx + 4):
    for z in range(fz - 2, fz + 6):
        edge = x in (fx - 3, fx + 3) or z in (fz - 2, fz + 5)
        g.put(x, t, z, "minecraft:stone_bricks")
        if edge and K.hsh(x, z, 160) < 0.75:
            for y in range(t + 1, t + 2 + int(K.hsh(x, z, 161) * 5)):
                g.put(x, y, z, "minecraft:stone_bricks" if K.hsh(x, y, z, 162) < 0.7 else "minecraft:mossy_stone_bricks")
bell_post(fx, fz + 2, "w")
g.marks["frag_w"] = [fx, t + 1, fz]
# 東: 市の屋台 (柱と布)
fx, fz = FRAG["e"][:2]
for i, (sx, sz) in enumerate(((-4, -3), (3, -3), (-4, 4))):
    t = g.top(fx + sx, fz + sz)
    for dx in (0, 2):
        for dz in (0, 2):
            for y in range(t + 1, t + 4):
                g.put(fx + sx + dx, y, fz + sz + dz, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
    for dx in range(0, 3):
        for dz in range(0, 3):
            if K.hsh(i, dx, dz, 170) < 0.7:
                g.put(fx + sx + dx, t + 4, fz + sz + dz, ["minecraft:red_wool", "minecraft:white_wool", "minecraft:yellow_wool"][i])
    g.put(fx + sx + 1, t + 1, fz + sz + 1, "minecraft:barrel[facing=up,open=false]")
bell_post(fx + 1, fz + 1, "e")
g.marks["frag_e"] = [fx, g.top(fx, fz) + 1, fz]
# 北西: 井戸
fx, fz = FRAG["n"][:2]
t = g.top(fx, fz)
for x in range(fx - 1, fx + 2):
    for z in range(fz - 1, fz + 2):
        for y in range(t - 4, t + 2):
            ring = (x, z) != (fx, fz)
            g.put(x, y, z, ("minecraft:mossy_cobblestone" if ring else K.AIR) if y > t - 4 else "minecraft:cobblestone")
g.put(fx, t - 3, fz, "minecraft:water[level=0]")
for sx in (-1, 1):
    for y in range(t + 2, t + 5):
        g.put(fx + sx, y, fz, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
for x in range(fx - 2, fx + 3):
    g.put(x, t + 5, fz, "minecraft:spruce_slab[type=bottom,waterlogged=false]")
bell_post(fx + 4, fz - 1, "n")
g.marks["frag_n"] = [fx, t + 1, fz + 3]

# ---------------------------------------------------------------- 鐘雲の道 (大きな島の縁 → かけら。幅 3)
def bell_path(a, b, key):
    ax, az, ay = a
    bx, bz, by = b
    n = int(math.hypot(bx - ax, bz - az)) + 1
    for i in range(n + 1):
        t = i / n
        x, z = ax + (bx - ax) * t, az + (bz - az) * t
        y = round(ay + (by - ay) * t)
        nx, nz = -(bz - az) / n, (bx - ax) / n
        ln = math.hypot(nx, nz) or 1
        for w in (-1, 0, 1):
            px, pz = round(x + nx / ln * w), round(z + nz / ln * w)
            if g.top(px, pz) is None:
                g.put(px, y, pz, BC)
                for yy in range(y + 1, y + 4):
                    if g.get(px, yy, pz) is None:
                        g.put(px, yy, pz, K.AIR)
    g.marks["path_" + key] = [round(ax), ay + 1, round(az)]


bell_path((-21, -142, G), (-47, -140, FRAG["w"][4]), "w")
bell_path((21, -160, G), (45, -166, FRAG["e"][4]), "e")
bell_path((-11, -186, G), (-27, -207, FRAG["n"][4]), "n")

# ---------------------------------------------------------------- 着く所からの白い橋 (地面 64 → 島の南の端の大通りの高さ。半分の段は石板)
Z_START, Z_END = -37, -111
LAND = g.top(0, Z_END - 1)
for z in range(Z_END, Z_START + 1):
    t = (Z_START - z) / (Z_START - Z_END)
    h2 = round((64 + (LAND - 64) * t) * 2)
    y = h2 // 2
    half = h2 % 2 == 1
    for x in (-1, 0, 1):
        if g.top(x, z) is not None and g.top(x, z) >= y:
            continue
        g.put(x, y, z, "minecraft:smooth_quartz")
        g.put(x, y + 1, z, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]" if half else K.AIR)
        for yy in range(y + 2, y + 5):
            g.put(x, yy, z, K.AIR)
        g.put(x, y - 1, z, "minecraft:quartz_slab[type=top,waterlogged=false]" if x != 0 else "minecraft:smooth_quartz")
    if (Z_START - z) % 8 == 4:
        for x in (-2, 2):
            g.put(x, y + 1, z, "minecraft:quartz_pillar[axis=y]")
            g.put(x, y + 2, z, "minecraft:end_rod[facing=up]")
g.marks["bridge_s"] = [0, 65, Z_START]
g.marks["arrive"] = [0, g.top(0, -114) + 1, -114]

# ---------------------------------------------------------------- 草花と雲のうねり
K.flowers(g, 180, keep=lambda x, z: False)
K.billows(g, 190, 46, lambda x, z: on_main(x, z) or any(math.hypot(x - f[0], z - f[1]) < 14 for f in FRAG.values()))
kb, cells, runs = g.write(OUT, "tools/gen/world/build_cloud_isle1.py が書く。手で直さない", {"ground": G, "top": TOP})
print("isle1", kb, "KB", cells, "cells", runs, "runs", "tower top", TOP)
