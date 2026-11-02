# -*- coding: utf-8 -*-
"""終章「雲海」の島 3「炉の島」(docs/design/50 の 4.2.3)。彫って data/skyabyss/finale/isle3.json に書く (FinaleWorld の版 4 で流し込む)。

  灯の島の灯台の上 (主の間。床 y 120、真ん中 (3, -322)) の北の縁から、光の橋が北へ下る (印 bridge_gate = 灯の島の主を倒した人だけ渡れる所)。
  島 (中心 (3, -445)、東西 68・南北 60、地面 y 102): 前の灰炉。灰と黒石の地面、崩れた鍛冶場 4 (炉・金床・砥石・鎖)、冷えた溶け跡、折れた煙突。
  まわりのかけら 3 (西 = 吹子場・東 = 鋳型場・北東 = 炭置き場)。それぞれに冷えた炉 (印 forge_<w|e|n>) と、島から渡る崩れた石の橋。
  橋の崩れた所は戻り石 (印 gap_<k>。ForgeIsle が巻き戻す)。
  北の端に大炉 (煉瓦。高さ 26)。南の面に扉 (印 door)。上は主の間 (内の半径 6 は固い床、6〜11 は戻り石の輪 = 印 gap_arena、縁 11〜12.5 は固い輪)。

  python -X utf8 tools/gen/world/build_cloud_isle3.py
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cloud_kit as K  # noqa: E402

OUT = K.ROOT / "src/main/resources/data/skyabyss/finale/isle3.json"
G = 102
CX, CZ = 3, -445
RX, RZ = 34, 30
# 灯の島の主の間 (build_cloud_isle2.py の TOP と回廊の真ん中)
A2_TOP, A2_X, A2_Z = 120, 3, -322
g = K.Grid(-72, 80, -512, -308, 14, 150)
LP = "skyabyss:light_path"
LPS = "skyabyss:light_path_slab[type=bottom,waterlogged=false]"
RW = "skyabyss:rewind_stone[phase=0]"
ASH = ["minecraft:coarse_dirt", "minecraft:coarse_dirt", "minecraft:tuff", "minecraft:gravel", "minecraft:smooth_basalt", "minecraft:blackstone", "minecraft:coarse_dirt"]

# ---------------------------------------------------------------- 島とかけら
K.island(g, CX, CZ, RX, RZ, G, 34, 301, wobble=5.0, rim_rocks=0.03)
FRAG = {"w": (-48, -428, 10, 9, 104), "e": (58, -452, 10, 10, 106), "n": (44, -494, 9, 8, 108)}
for k, (fx, fz, rx, rz, fg) in FRAG.items():
    K.island(g, fx, fz, rx, rz, fg, 18, 310 + ord(k))


def top(x, z):
    return g.tops.get((x, z))


def on_main(x, z):
    return math.hypot((x - CX) / RX, (z - CZ) / RZ) < 1.0


# 灰の地面 (草を灰・凝灰岩・砂利・黒石に。ところどころ枯れ草)
for (x, z), t in list(g.tops.items()):
    # 斑はなめらかな雑音で (一つずつの乱数だと、つぎはぎに見える)
    n = K.noise2(x, z, 6.0, 320) + 0.25 * (K.hsh(x, z, 321) - 0.5)
    if n < 0.18:
        b = "minecraft:grass_block[snowy=false]" if K.noise2(x, z, 4.0, 323) > 0.6 else "minecraft:tuff"
    elif n < 0.55:
        b = "minecraft:coarse_dirt"
    elif n < 0.7:
        b = "minecraft:tuff"
    elif n < 0.82:
        b = "minecraft:gravel"
    else:
        b = "minecraft:blackstone"
    g.put(x, t, z, b)
    for y in range(t - 2, t):
        g.put(x, y, z, "minecraft:coarse_dirt" if K.hsh(x, y, z, 322) < 0.6 else "minecraft:tuff")
# 冷えた溶け跡 (黒曜石と滑らかな玄武岩の細い流れ)
for i in range(5):
    a = K.hsh(i, 330) * math.pi * 2
    x, z = CX + math.cos(a) * 8, CZ + math.sin(a) * 6
    for step in range(22):
        a += (K.hsh(i, step, 331) - 0.5) * 0.9
        x += math.cos(a)
        z += math.sin(a)
        for w in (0, 1):
            px, pz = round(x + w * math.sin(a)), round(z - w * math.cos(a))
            t = top(px, pz)
            if t is not None and on_main(px, pz):
                g.put(px, t, pz, "minecraft:obsidian" if K.hsh(px, pz, 332) < 0.35 else "minecraft:smooth_basalt" if K.hsh(px, pz, 333) < 0.7 else "minecraft:crying_obsidian")


def clear_above(x, z, t, h=5):
    for y in range(t + 1, t + 1 + h):
        if g.get(x, y, z) is None:
            g.put(x, y, z, K.AIR)


# ---------------------------------------------------------------- 崩れた鍛冶場 (煉瓦と黒石の壁の名残、中に炉・金床・砥石・鎖)
def smithy(x0, z0, w, d, seed):
    t = max(top(x, z) or G for x in range(x0, x0 + w) for z in range(z0, z0 + d))
    for x in range(x0 - 1, x0 + w + 1):
        for z in range(z0 - 1, z0 + d + 1):
            if top(x, z) is not None:
                for y in range(top(x, z) + 1, t + 1):
                    g.put(x, y, z, "minecraft:cobbled_deepslate")
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            g.put(x, t, z, "minecraft:polished_blackstone_bricks" if K.hsh(x, z, seed) < 0.6 else "minecraft:cracked_polished_blackstone_bricks")
            edge = x in (x0, x0 + w - 1) or z in (z0, z0 + d - 1)
            hmax = 1 + int(K.hsh(x, z, seed + 1) * 4)
            for y in range(t + 1, t + 6):
                if not edge:
                    g.put(x, y, z, K.AIR)
                elif y - t <= hmax:
                    g.put(x, y, z, "minecraft:bricks" if K.hsh(x, y, z, seed + 2) < 0.65 else "minecraft:polished_blackstone_bricks")
                else:
                    break
    # 開いた戸口 (南)
    for y in range(t + 1, t + 4):
        g.put(x0 + w // 2, y, z0 + d - 1, K.AIR)
    g.put(x0 + 1, t + 1, z0 + 1, "minecraft:blast_furnace[facing=south,lit=false]")
    g.put(x0 + 2, t + 1, z0 + 1, "minecraft:smoker[facing=south,lit=false]")
    g.put(x0 + w - 2, t + 1, z0 + 1, "minecraft:damaged_anvil[facing=east]")
    g.put(x0 + w - 2, t + 1, z0 + d - 3, "minecraft:grindstone[face=floor,facing=north]")
    g.put(x0 + 1, t + 1, z0 + d - 2, "minecraft:cauldron")
    for y in range(t + 2, t + 5):
        g.put(x0 + w // 2, y, z0 + d // 2, "minecraft:chain[axis=y,waterlogged=false]")
    # 折れた煙突
    for y in range(t + 1, t + 6 + int(K.hsh(seed, 7) * 5)):
        g.put(x0, y, z0, "minecraft:bricks")


for i, (x0, z0, w, d) in enumerate(((-20, -440, 8, 7), (-12, -458, 7, 7), (12, -438, 8, 7), (18, -456, 7, 8))):
    smithy(x0, z0, w, d, 340 + i * 5)
# 割れた金床と灰の山 (広場)
for i, (x, z) in enumerate(((0, -432), (6, -428), (-4, -446), (9, -448))):
    t = top(x, z)
    if t is not None:
        g.put(x, t + 1, z, "minecraft:chipped_anvil[facing=%s]" % ("north" if i % 2 else "east"))
for i in range(8):
    x, z = round(CX - 24 + K.hsh(i, 350) * 48), round(CZ - 20 + K.hsh(i, 351) * 40)
    t = top(x, z)
    if t is not None and on_main(x, z):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                tt = top(x + dx, z + dz)
                if tt is not None and g.get(x + dx, tt + 1, z + dz) is None:
                    g.put(x + dx, tt + 1, z + dz, "minecraft:gravel" if (dx, dz) == (0, 0) else "minecraft:tuff_slab[type=bottom,waterlogged=false]")
# 燃え残りの焚き火 (煙)
for x, z in ((-6, -436), (14, -446), (2, -458)):
    t = top(x, z)
    if t is not None:
        g.put(x, t + 1, z, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")

# ---------------------------------------------------------------- 大炉 (北の端。煉瓦。上に主の間)
TX, TZ = CX, -466
tt = max(top(x, z) or G for x in range(TX - 7, TX + 8) for z in range(TZ - 7, TZ + 8))
H = 26
TOP = tt + H
for y in range(tt - 4, TOP):
    r = 7.6 - 2.2 * max(0, (y - tt) - 8) / (H - 8)
    for x in range(TX - 8, TX + 9):
        for z in range(TZ - 8, TZ + 9):
            d = math.hypot(x - TX, z - TZ)
            if d <= r:
                shell = d > r - 1.3
                if shell:
                    b = "minecraft:bricks" if ((y - tt) // 4) % 3 else "minecraft:polished_blackstone_bricks"
                    if K.hsh(x, y, z, 360) < 0.07:
                        b = "minecraft:cracked_polished_blackstone_bricks"
                else:
                    b = "minecraft:deepslate"
                g.put(x, y, z, b)
# 炉の口 (四方の下に、冷えた溶け跡の詰まった口)
for dx, dz in ((0, -1), (1, 0), (-1, 0)):
    for k in (-1, 0, 1):
        for y in (tt + 1, tt + 2):
            x, z = TX + dx * 7 + (k if dz else 0), TZ + dz * 7 + (k if dx else 0)
            g.put(x, y, z, "minecraft:magma_block" if y == tt + 1 else "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]")
# 扉 (南の面)
DOOR = (TX, tt + 1, TZ + 7)
for z in range(TZ + 6, TZ + 11):
    for x in range(TX - 1, TX + 2):
        g.put(x, tt, z, "minecraft:polished_blackstone_bricks")
        for y in range(tt + 1, tt + 4):
            if z >= TZ + 8:
                g.put(x, y, z, K.AIR)
for y in range(tt + 1, tt + 4):
    for x in (TX - 1, TX + 1):
        g.put(x, y, TZ + 7, "minecraft:polished_blackstone")
g.put(TX, tt + 4, TZ + 7, "minecraft:polished_blackstone")
g.put(*DOOR, "minecraft:chiseled_polished_blackstone")
g.put(TX, tt + 2, TZ + 7, "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]")
g.marks["door"] = list(DOOR)
g.marks["door_front"] = [TX, tt + 1, TZ + 9]

# ---------------------------------------------------------------- 主の間 (大炉の上の丸い床。内は固い、外の輪は戻り石、縁は固い輪)
AX, AZ = TX, TZ
for x in range(AX - 13, AX + 14):
    for z in range(AZ - 13, AZ + 14):
        d = math.hypot(x - AX, z - AZ)
        if d > 12.5:
            continue
        if d <= 6.2:
            b = "minecraft:polished_blackstone_bricks" if (x + z) % 2 else "minecraft:polished_blackstone"
            if 2.6 < d < 3.4:
                b = "minecraft:gilded_blackstone"
            g.put(x, TOP, z, b)
        elif d <= 11:
            g.put(x, TOP, z, RW)
        else:
            g.put(x, TOP, z, "minecraft:polished_blackstone_bricks")
            if d > 12:
                g.put(x, TOP + 1, z, "minecraft:chain[axis=y,waterlogged=false]" if K.hsh(x, z, 370) < 0.5 else "minecraft:polished_blackstone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
        for y in range(TOP + (2 if d > 12 else 1), TOP + 10):
            g.put(x, y, z, K.AIR)
# 外の輪の下の梁
for a in range(8):
    ang = a * math.pi / 4
    for r in range(6, 12):
        g.put(round(AX + math.cos(ang) * r), TOP - 1, round(AZ + math.sin(ang) * r), "minecraft:polished_blackstone_bricks")
g.marks["arena"] = [AX, TOP, AZ]
g.marks["gap_arena"] = [AX, TOP, AZ]
g.marks["lord_spawn"] = [AX, TOP + 1, AZ]
g.marks["arena_entry"] = [AX, TOP + 1, AZ + 4]
g.marks["arena_back"] = [AX - 4, TOP + 1, AZ + 2]
g.put(AX - 4, TOP + 1, AZ + 2, "minecraft:lodestone")

# ---------------------------------------------------------------- かけらの炉と記憶の場所
def forge_post(x, z, key):
    t = top(x, z)
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            g.put(x + dx, t, z + dz, "minecraft:polished_blackstone_bricks")
            clear_above(x + dx, z + dz, t, 4)
    g.put(x, t + 1, z, "minecraft:blast_furnace[facing=south,lit=false]")
    g.put(x, t + 2, z, "minecraft:bricks")
    g.put(x, t + 3, z, "minecraft:bricks")
    g.put(x - 1, t + 1, z, "minecraft:chain[axis=y,waterlogged=false]")
    g.marks["forge_" + key] = [x, t + 1, z]


# 西: 吹子場 (壊れた吹子 = 革の板と柱)
fx, fz = FRAG["w"][:2]
forge_post(fx, fz, "w")
t = top(fx + 3, fz)
for y in range(t + 1, t + 3):
    g.put(fx + 3, y, fz - 1, "minecraft:stripped_dark_oak_log[axis=y]")
    g.put(fx + 3, y, fz + 1, "minecraft:stripped_dark_oak_log[axis=y]")
g.put(fx + 3, t + 3, fz - 1, "minecraft:brown_carpet")
g.put(fx + 3, t + 2, fz, "minecraft:brown_wool")
g.marks["frag_w"] = [fx, top(fx, fz + 3) + 1, fz + 3]
# 東: 鋳型場 (砂の型と割れた鋳物)
fx, fz = FRAG["e"][:2]
forge_post(fx, fz, "e")
for dx in range(-4, -1):
    for dz in range(2, 5):
        tt2 = top(fx + dx, fz + dz)
        if tt2 is not None:
            g.put(fx + dx, tt2, fz + dz, "minecraft:sand" if (dx + dz) % 2 else "minecraft:red_sand")
g.put(fx - 3, top(fx - 3, fz + 3) + 1, fz + 3, "minecraft:heavy_core")
g.marks["frag_e"] = [fx - 1, top(fx - 1, fz + 4) + 1, fz + 4]
# 北東: 炭置き場 (炭の山)
fx, fz = FRAG["n"][:2]
forge_post(fx, fz, "n")
for dx in range(2, 5):
    for dz in range(-2, 2):
        tt2 = top(fx + dx, fz + dz)
        if tt2 is not None:
            for y in range(tt2 + 1, tt2 + 1 + (2 if abs(dz + 0.5) < 1 and dx == 3 else 1)):
                g.put(fx + dx, y, fz + dz, "minecraft:coal_block")
g.marks["frag_n"] = [fx - 2, top(fx - 2, fz + 2) + 1, fz + 2]

# ---------------------------------------------------------------- 崩れた石の橋 (島の縁 → かけら。幅 3。真ん中が戻り石)
def broken_bridge(a, b, key):
    ax, az, ay = a
    bx, bz, by = b
    n = int(math.hypot(bx - ax, bz - az)) + 1
    nx, nz = -(bz - az) / n, (bx - ax) / n
    ln = math.hypot(nx, nz) or 1
    nx, nz = nx / ln, nz / ln
    gap0, gap1 = int(n * 0.35), int(n * 0.65)
    for i in range(n + 1):
        t = i / n
        x, z = ax + (bx - ax) * t, az + (bz - az) * t
        y = round(ay + (by - ay) * t)
        gap = gap0 <= i <= gap1
        for w in (-2, -1, 0, 1, 2):
            px, pz = round(x + nx * w), round(z + nz * w)
            if top(px, pz) is not None and abs(w) < 2:
                continue
            if abs(w) == 2:
                if not gap and i % 2 == 0:
                    g.put(px, y + 1, pz, "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
                    g.put(px, y, pz, "minecraft:stone_bricks")
                continue
            g.put(px, y, pz, RW if gap else ("minecraft:stone_bricks" if K.hsh(px, pz, 380) < 0.7 else "minecraft:cracked_stone_bricks"))
            if not gap:
                g.put(px, y - 1, pz, "minecraft:stone_brick_slab[type=top,waterlogged=false]")
            for yy in range(y + 1, y + 4):
                if g.get(px, yy, pz) is None:
                    g.put(px, yy, pz, K.AIR)
        # 崩れた所の縁に折れた石
        if i in (gap0 - 1, gap1 + 1):
            for w in (-1, 1):
                g.put(round(x + nx * w), y + 1, round(z + nz * w), "minecraft:cobblestone_slab[type=bottom,waterlogged=false]")
    mid = (gap0 + gap1) / 2 / n
    g.marks["gap_" + key] = [round(ax + (bx - ax) * mid), round(ay + (by - ay) * mid), round(az + (bz - az) * mid)]
    g.marks["path_" + key] = [round(ax), ay + 1, round(az)]


broken_bridge((-28, -432, G), (-39, -429, FRAG["w"][4]), "w")
broken_bridge((36, -448, G), (49, -451, FRAG["e"][4]), "e")
broken_bridge((26, -468, G), (37, -487, FRAG["n"][4]), "n")

# ---------------------------------------------------------------- 光の橋 (灯の島の主の間の北の縁 → 島 3 の南の端)
z = CZ + RZ + 6
while top(A2_X, z) is None:
    z -= 1
LAND_Z = z
LAND = top(A2_X, LAND_Z)
for zz in range(A2_Z - 10, A2_Z - 15, -1):
    for x in (A2_X - 1, A2_X, A2_X + 1):
        g.put(x, A2_TOP, zz, LP)
        for y in range(A2_TOP + 1, A2_TOP + 5):
            g.put(x, y, zz, K.AIR)
g.marks["bridge_gate"] = [A2_X, A2_TOP + 1, A2_Z - 13]
ZS = A2_Z - 15
for zz in range(ZS, LAND_Z, -1):
    t = (ZS - zz) / (ZS - LAND_Z)
    h2 = round((A2_TOP + (LAND - A2_TOP) * t) * 2)
    y, half = h2 // 2, h2 % 2 == 1
    for x in (A2_X - 1, A2_X, A2_X + 1):
        if top(x, zz) is not None and top(x, zz) >= y:
            continue
        g.put(x, y, zz, LP)
        g.put(x, y + 1, zz, LPS if half else K.AIR)
        for yy in range(y + 2, y + 5):
            g.put(x, yy, zz, K.AIR)
    if (ZS - zz) % 9 == 4:
        for x in (A2_X - 2, A2_X + 2):
            g.put(x, y + 1, zz, "minecraft:quartz_pillar[axis=y]")
            g.put(x, y + 2, zz, "minecraft:end_rod[facing=up]")
g.marks["arrive"] = [A2_X, LAND + 1, LAND_Z - 3]
g.marks["center"] = [CX, (top(CX, CZ) or G) + 1, CZ]

# ---------------------------------------------------------------- 雲のうねり
K.billows(g, 390, 40, lambda x, z: on_main(x, z) or any(math.hypot(x - f[0], z - f[1]) < 14 for f in FRAG.values()) or abs(x - A2_X) < 4)
kb, cells, runs = g.write(OUT, "tools/gen/world/build_cloud_isle3.py が書く。手で直さない", {"ground": G, "top": TOP})
print("isle3", kb, "KB", cells, "cells", runs, "runs", "forge top", TOP, "land", LAND, LAND_Z)
