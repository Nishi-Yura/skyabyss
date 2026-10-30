# -*- coding: utf-8 -*-
"""終章「雲海」の巡礼者の座 (docs/design/50 の 4.3)。彫って data/skyabyss/finale/seat.json に書く (FinaleWorld の版 6 で流し込む)。

  約束の島の月見の岩の上 (主の間。床 y 140、真ん中 (3, -604)) の北の縁の橋の台から、光の橋が北へ上る (印 bridge_gate = 約束の島の主を倒した人だけ渡れる所)。
  座の床 (真ん中 (3, -705)、床 y 156): 内の半径 7 は固い床 (白と金の敷石)、7〜16.5 は鐘雲の輪 (印 ring)。南北に幅 3 の固い道。
  内の床の縁の四隅に小さな鐘 (印 seat_bell_<ne|se|sw|nw>)。輪の上の四隅に陰の柱 (印 pillar_<k>)。
  北の台 (半径 17〜24) に巡礼者の座 (白い石と金の大きな椅子、背に金の輪)。座の前の段に触れる所 (印 throne)。台に帰りの台 (印 arena_back)。
  台の西に雲の暦 (石英の台に日時計。印 calendar。クリア後の巡礼の試し。FinaleWorld の版 7)。
  下は白い岩の逆さの根。

  python -X utf8 tools/gen/world/build_cloud_seat.py
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cloud_kit as K  # noqa: E402

OUT = K.ROOT / "src/main/resources/data/skyabyss/finale/seat.json"
F = 156
CX, CZ = 3, -705
R_IN, R_RING = 7.2, 16.5
# 約束の島の主の間の北の縁の橋の台 (build_cloud_isle4.py の seat_bridge)
B_X, B_Y, B_Z = 3, 141, -619
g = K.Grid(-34, 40, -740, -616, 100, 186)
BC = "skyabyss:bell_cloud[fade=false,solid=false]"
LP = "skyabyss:light_path"
LPS = "skyabyss:light_path_slab[type=bottom,waterlogged=false]"
AIR = K.AIR


def d_of(x, z):
    return math.hypot(x - CX, z - CZ)


def root(x, z, top, depth):
    """白い岩の逆さの根 (top の下に depth まで。層は方解石と石英と閃緑岩)"""
    for y in range(top - depth, top):
        b = "minecraft:calcite" if K.hsh(x, y, z, 501) < 0.55 else "minecraft:polished_diorite" if K.hsh(x, y, z, 502) < 0.6 else "minecraft:smooth_quartz"
        g.put(x, y, z, b)


# ---------------------------------------------------------------- 内の床と根
for x in range(CX - 8, CX + 9):
    for z in range(CZ - 8, CZ + 9):
        d = d_of(x, z)
        if d > R_IN:
            continue
        k = 1 - d / R_IN
        depth = int(3 + 18 * k ** 1.3 + 3 * (K.noise2(x, z, 4.0, 503) - 0.5))
        root(x, z, F, depth)
        # 敷石: 外の帯は白、内に金の八芒の星、真ん中に金の円
        a = (math.degrees(math.atan2(z - CZ, x - CX)) + 360) % 45
        b = "minecraft:smooth_quartz"
        if d < 1.6:
            b = "minecraft:gold_block"
        elif d < 2.4:
            b = "minecraft:chiseled_quartz_block"
        elif d < 5.6 and (a < 4 or a > 41):
            b = "minecraft:gold_block" if d < 4.4 else "minecraft:raw_gold_block"
        elif 5.6 <= d < 6.4:
            b = "minecraft:quartz_bricks"
        elif d >= 6.4:
            b = "minecraft:chiseled_quartz_block" if (x + z) % 2 else "minecraft:quartz_bricks"
        g.put(x, F, z, b)
        for y in range(F + 1, F + 14):
            g.put(x, y, z, AIR)
# 鐘 (内の床の縁の四隅。小さな柱の上)
for key, (sx, sz) in {"ne": (1, -1), "se": (1, 1), "sw": (-1, 1), "nw": (-1, -1)}.items():
    x, z = round(CX + sx * 4.4), round(CZ + sz * 4.4)
    g.put(x, F + 1, z, "minecraft:quartz_pillar[axis=y]")
    g.put(x, F + 2, z, "minecraft:bell[attachment=floor,facing=%s,powered=false]" % ("east" if sx > 0 else "west"))
    g.marks["seat_bell_" + key] = [x, F + 2, z]

# ---------------------------------------------------------------- 鐘雲の輪と南北の道
for x in range(CX - 17, CX + 18):
    for z in range(CZ - 17, CZ + 18):
        d = d_of(x, z)
        if not (R_IN < d <= R_RING):
            continue
        road = abs(x - CX) <= 1
        if road:
            g.put(x, F, z, "minecraft:quartz_bricks" if (z % 3) else "minecraft:gold_block")
            g.put(x, F - 1, z, "minecraft:quartz_slab[type=top,waterlogged=false]")
        else:
            g.put(x, F, z, BC)
        for y in range(F + 1, F + 14):
            g.put(x, y, z, AIR)
g.marks["ring"] = [CX, F, CZ]

# 陰の柱 (輪の上の四隅。2x2、高さ 6、白い石と金の冠)
PILLARS = {}
for i, ang in enumerate((45, 135, 225, 315)):
    px = round(CX + math.cos(math.radians(ang)) * 11.5)
    pz = round(CZ + math.sin(math.radians(ang)) * 11.5)
    for dx in (0, 1):
        for dz in (0, 1):
            for y in range(F - 2, F + 7):
                b = "minecraft:quartz_pillar[axis=y]" if F <= y < F + 6 else "minecraft:gold_block" if y == F + 6 else "minecraft:smooth_quartz"
                g.put(px + dx, y, pz + dz, b)
    g.marks["pillar_%d" % (i + 1)] = [px, F + 1, pz]
    PILLARS[i] = (px, pz)

# ---------------------------------------------------------------- 北の台と巡礼者の座
for x in range(CX - 10, CX + 11):
    for z in range(CZ - 26, CZ - 15):
        dz = CZ - z
        if dz < 16.6 or math.hypot((x - CX) / 10.0, (z - (CZ - 20)) / 5.6) > 1.0:
            continue
        root(x, z, F, int(4 + 10 * (1 - abs(x - CX) / 11.0)))
        g.put(x, F, z, "minecraft:quartz_bricks" if (x + z) % 3 else "minecraft:smooth_quartz")
        for y in range(F + 1, F + 16):
            g.put(x, y, z, AIR)
TZ = CZ - 22
# 座: 段 (幅 7)、腰 (幅 5)、背 (高さ 12、上に金の輪)、肘掛け
for x in range(CX - 3, CX + 4):
    for z in range(TZ - 1, TZ + 3):
        g.put(x, F + 1, z, "minecraft:quartz_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]" if z == TZ + 2 else "minecraft:smooth_quartz")
for x in range(CX - 2, CX + 3):
    for z in range(TZ - 1, TZ + 2):
        g.put(x, F + 2, z, "minecraft:gold_block" if z == TZ + 1 and abs(x - CX) <= 1 else "minecraft:smooth_quartz")
for x in range(CX - 3, CX + 4):
    for y in range(F + 2, F + 15):
        h = y - F
        w = 3 if h < 10 else 2 if h < 13 else 1
        if abs(x - CX) <= w:
            g.put(x, y, TZ - 1, "minecraft:chiseled_quartz_block" if abs(x - CX) == w else "minecraft:quartz_pillar[axis=y]")
for s in (-1, 1):
    for z in range(TZ - 1, TZ + 2):
        g.put(CX + s * 3, F + 3, z, "minecraft:quartz_slab[type=bottom,waterlogged=false]")
    g.put(CX + s * 3, F + 2, TZ + 1, "minecraft:quartz_pillar[axis=y]")
# 背の金の輪 (半径 4、背の上の方)
for a in range(0, 360, 6):
    x = round(CX + math.cos(math.radians(a)) * 4.2)
    y = round(F + 11 + math.sin(math.radians(a)) * 4.2)
    g.put(x, y, TZ - 2, "minecraft:gold_block")
g.put(CX, F + 11, TZ - 2, "minecraft:sea_lantern")
THRONE = (CX, F + 2, TZ + 1)
g.marks["throne"] = list(THRONE)
g.marks["throne_front"] = [CX, F + 1, TZ + 4]
g.marks["arena_back"] = [CX + 6, F + 1, TZ + 3]
g.put(CX + 6, F + 1, TZ + 3, "minecraft:lodestone")
for x in (CX - 6, CX + 6):
    g.put(x, F + 1, TZ, "minecraft:quartz_pillar[axis=y]")
    g.put(x, F + 2, TZ, "minecraft:end_rod[facing=up]")
# 雲の暦 (巡礼の試し): 石英の台に日時計、まわりに金の四つの刻み
CAL = (CX - 6, F + 2, TZ + 3)
g.put(CAL[0], F + 1, CAL[2], "minecraft:chiseled_quartz_block")
g.put(*CAL, "minecraft:daylight_detector[inverted=false,power=0]")
for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    g.put(CAL[0] + dx, F + 1, CAL[2] + dz, "minecraft:gold_block" if dx else "minecraft:quartz_slab[type=bottom,waterlogged=false]")
g.marks["calendar"] = list(CAL)

g.marks["arena"] = [CX, F, CZ]
g.marks["lord_spawn"] = [CX, F + 1, CZ]
g.marks["hand_e"] = [CX + 20, F + 1, CZ]
g.marks["hand_w"] = [CX - 20, F + 1, CZ]
g.marks["arena_entry"] = [CX, F + 1, CZ + 6]

# ---------------------------------------------------------------- 光の橋 (約束の島の主の間の北の縁 → 座の南の道)
LAND_Z = round(CZ + R_RING) + 1
g.marks["bridge_gate"] = [B_X, B_Y, B_Z - 2]
for zz in range(B_Z - 1, LAND_Z - 1, -1):
    t = (B_Z - 1 - zz) / (B_Z - 1 - LAND_Z)
    h2 = round((B_Y - 1 + (F - (B_Y - 1)) * t) * 2)
    y, half = h2 // 2, h2 % 2 == 1
    for x in (B_X - 1, B_X, B_X + 1):
        g.put(x, y, zz, LP)
        g.put(x, y + 1, zz, LPS if half else AIR)
        for yy in range(y + 2, y + 5):
            g.put(x, yy, zz, AIR)
    if (B_Z - zz) % 9 == 4:
        for x in (B_X - 2, B_X + 2):
            g.put(x, y + 1, zz, "minecraft:quartz_pillar[axis=y]")
            g.put(x, y + 2, zz, "minecraft:end_rod[facing=up]")
for zz in (LAND_Z, LAND_Z - 1):
    for x in (B_X - 1, B_X, B_X + 1):
        g.put(x, F, zz, "minecraft:quartz_bricks")
        for yy in range(F + 1, F + 5):
            g.put(x, yy, zz, AIR)
g.marks["arrive"] = [CX, F + 1, LAND_Z - 1]

K.billows(g, 520, 30, lambda x, z: d_of(x, z) < 20 or abs(x - B_X) < 4)
kb, cells, runs = g.write(OUT, "tools/gen/world/build_cloud_seat.py が書く。手で直さない", {"floor": F})
print("seat", kb, "KB", cells, "cells", runs, "runs", "land", LAND_Z, "throne", THRONE)
