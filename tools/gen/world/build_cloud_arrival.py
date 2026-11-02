# -*- coding: utf-8 -*-
"""終章「雲海」の着く所「巡礼の門の島」(docs/design/50 の 4.1)。彫って data/skyabyss/finale/arrival.json に書く (FinaleWorld が一度だけ流し込む)。

雲の海 (y 36〜39) の上、地面 y 64 に浮く直径 70 ほどの島。
  上: 白い石の円い広場 (真ん中に着く所)、北に崩れた巡礼の門 (白い石と金のアーチ。下に門の鐘 = 印 gate)、南に帰りの台 (天然磁石 = 印 return)、
      広場の四隅に四体の巨神の記念の台 (北東 鯨の骨の弧・南東 殻の盾・南西 羽の柱・北西 燠の炉)、草と白い花、縁の岩。
  下: 層の筋のある逆さの岩の根 (先は鍾乳石)、縁の下に垂れる蔓。
  北の縁: 雲の島 1 へ渡る橋の台 (印 bridge_n。作る順 2 で橋を架ける)。

  python -X utf8 tools/gen/world/build_cloud_arrival.py
"""
import json
import math
from pathlib import Path

import numpy as np

OUT = Path(__file__).resolve().parents[3] / "src/main/resources/data/skyabyss/finale/arrival.json"
X0, X1, Z0, Z1 = -44, 44, -48, 44
Y0, Y1 = 14, 84
G = 64          # 地面の上面
R = 33.0        # 島の半径 (縁は揺らぐ)
PLAZA = 11.5

NX, NY, NZ = X1 - X0 + 1, Y1 - Y0 + 1, Z1 - Z0 + 1
AIR = "minecraft:air"
cells = {}


def hsh(*a):
    h = 2166136261
    for v in a:
        h = ((h ^ (int(v) & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    h ^= h >> 13
    h = (h * 1274126177) & 0xFFFFFFFF
    return (h & 0xFFFFFF) / float(0x1000000)


def noise2(x, z, sc, s):
    fx, fz = x / sc, z / sc
    ix, iz = math.floor(fx), math.floor(fz)
    tx, tz = fx - ix, fz - iz
    tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
    v = 0.0
    for dx in (0, 1):
        for dz in (0, 1):
            w = (tx if dx else 1 - tx) * (tz if dz else 1 - tz)
            v += w * hsh(ix + dx, iz + dz, s)
    return v


def put(x, y, z, b):
    if X0 <= x <= X1 and Y0 <= y <= Y1 and Z0 <= z <= Z1:
        cells[(x, y, z)] = b


def get(x, y, z):
    return cells.get((x, y, z))


# ---------------------------------------------------------------- 島の形
def edge_r(x, z):
    a = math.atan2(z, x)
    wob = 4.5 * (noise2(math.cos(a) * 6 + 30, math.sin(a) * 6 + 30, 2.2, 1) - 0.5) + 2.0 * (noise2(math.cos(a) * 14, math.sin(a) * 14, 2.0, 2) - 0.5)
    # 北の縁は橋の台へ少し張り出す
    north = max(0.0, -math.sin(a)) ** 6 * 4.0
    return R + wob + north


def top_at(x, z):
    """上面の高さ (島の外は None)。広場は平ら、外ほどわずかに下がり、縁の岩で少し上がる"""
    d = math.hypot(x, z)
    er = edge_r(x, z)
    if d > er:
        return None
    if d <= PLAZA + 1.5:
        return G
    h = G + 0.9 * (noise2(x, z, 7.0, 3) - 0.5) * 2 - (d - PLAZA) / (er - PLAZA) * 1.5
    return int(round(h))


def depth_at(x, z):
    d = math.hypot(x, z)
    er = edge_r(x, z)
    k = max(0.0, 1 - d / er)
    return int(round(4 + 30 * k ** 1.4 + 6 * (noise2(x, z, 6.0, 4) - 0.5) * k))


STRATA = ["minecraft:calcite", "minecraft:diorite", "minecraft:tuff", "minecraft:andesite", "minecraft:calcite", "minecraft:smooth_basalt", "minecraft:diorite"]
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        t = top_at(x, z)
        if t is None:
            continue
        bottom = t - depth_at(x, z)
        for y in range(bottom, t + 1):
            dt = t - y
            if dt == 0:
                b = "minecraft:grass_block[snowy=false]"
            elif dt <= 2 and math.hypot(x, z) < edge_r(x, z) - 1.5:
                b = "minecraft:dirt" if hsh(x, y, z, 5) < 0.85 else "minecraft:coarse_dirt"
            elif dt <= 2:
                b = "minecraft:moss_block" if hsh(x, y, z, 5) < 0.4 else "minecraft:calcite"
            else:
                band = int((y + 3 * (noise2(x, z, 9.0, 6) - 0.5)) // 3) % len(STRATA)
                b = STRATA[band]
                if hsh(x, y, z, 7) < 0.08:
                    b = "minecraft:stone"
            put(x, y, z, b)
        # 根の先の鍾乳石 (下へ尖る)
        if math.hypot(x, z) < 6 and hsh(x, z, 8) < 0.5:
            for k in range(1, 2 + int(hsh(x, z, 9) * 3)):
                put(x, bottom - k, z, "minecraft:dripstone_block")
            put(x, bottom - 4, z, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]")

# 縁の下の垂れ (蔓と根の岩)
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        t = top_at(x, z)
        if t is None or math.hypot(x, z) < edge_r(x, z) - 2.5:
            continue
        bottom = t - depth_at(x, z)
        if hsh(x, z, 11) < 0.18:
            for k in range(1, 2 + int(hsh(x, z, 12) * 4)):
                if get(x, bottom - k, z) is None:
                    put(x, bottom - k, z, "minecraft:vine[east=false,north=false,south=false,up=true,west=false]" if k == 1 else
                        "minecraft:vine[east=false,north=false,south=false,up=false,west=false]")

# ---------------------------------------------------------------- 広場
GOLD = "minecraft:gold_block"
for x in range(-13, 14):
    for z in range(-13, 14):
        d = math.hypot(x, z)
        if d <= PLAZA:
            ring = d > PLAZA - 1.0
            b = "minecraft:polished_diorite" if ring else ("minecraft:smooth_quartz" if (x + z) % 2 == 0 else "minecraft:calcite")
            if 2.6 < d < 3.4:
                b = "minecraft:chiseled_quartz_block"
            put(x, G, z, b)
            for y in range(G + 1, G + 8):
                put(x, y, z, AIR)
# 真ん中の金の星 (着く所)
for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
    put(dx, G, dz, GOLD if (dx, dz) == (0, 0) else "minecraft:quartz_bricks")

# ---------------------------------------------------------------- 崩れた巡礼の門 (広場の北、幅 9・高さ 12)
GZ = -14
for side in (-1, 1):
    px = side * 4
    top = 12 if side < 0 else 8  # 右の柱は折れている
    for y in range(G, G + top + 1):
        for dx in (0, side):
            for dz in (0, -1):
                b = "minecraft:quartz_pillar[axis=y]"
                if y == G or y == G + 1:
                    b = "minecraft:chiseled_quartz_block"
                put(px + dx, y, GZ + dz, b)
    # 柱の帯 (金)
    for y in (G + 4, G + 9):
        if y <= G + top:
            for dx in (0, side):
                for dz in (0, -1):
                    put(px + dx, y, GZ + dz, GOLD if (dx + dz) % 2 == 0 else "minecraft:smooth_quartz")
# 梁 (左から、右の折れた柱の上で途切れる)
for x in range(-5, 3):
    for dz in (0, -1):
        put(x, G + 12, GZ + dz, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]" if x >= 1 else "minecraft:smooth_quartz")
    if x <= 0:
        put(x, G + 13, GZ, "minecraft:quartz_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]")
put(-1, G + 12, GZ - 1, GOLD)
# 崩れた石 (右の柱のまわり)
for x, y, z in ((5, G + 1, GZ + 2), (6, G + 1, GZ + 1), (3, G + 1, GZ + 3), (6, G + 1, GZ - 2), (7, G + 1, GZ)):
    put(x, y, z, "minecraft:smooth_quartz" if (x + z) % 2 else "minecraft:quartz_pillar[axis=x]")
put(5, G + 2, GZ + 2, "minecraft:quartz_slab[type=bottom,waterlogged=false]")
# 門の床と鐘の台 (印 gate = 鐘)
for x in range(-3, 4):
    for z in range(GZ - 1, GZ + 2):
        put(x, G, z, "minecraft:quartz_bricks")
put(0, G + 1, GZ, "minecraft:chiseled_quartz_block")
GATE = (0, G + 2, GZ)
put(*GATE, "minecraft:bell[attachment=floor,facing=south,powered=false]")

# ---------------------------------------------------------------- 帰りの台 (広場の南)
RETURN = (0, G + 1, 7)
put(0, G, 7, "minecraft:polished_diorite")
put(*RETURN, "minecraft:lodestone")

# ---------------------------------------------------------------- 四体の巨神の記念の台 (広場の縁の四隅、半径 8.5)
def plinth(cx, cz):
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            put(x, G + 1, z, "minecraft:quartz_slab[type=bottom,waterlogged=false]" if (x, z) != (cx, cz) else "minecraft:chiseled_quartz_block")


S = 6
# 北東: 岩鯨 — 鯨の骨の弧
plinth(S, -S)
for dx, ys in ((-2, (1, 2, 3)), (-1, (3, 4)), (0, (4,)), (1, (3, 4)), (2, (1, 2, 3))):
    for dy in ys:
        put(S + dx, G + 1 + dy, -S, "minecraft:bone_block[axis=y]" if abs(dx) == 2 else "minecraft:bone_block[axis=x]")
put(S, G + 2, -S, "minecraft:sea_lantern")
# 南東: 潮殻 — 殻の盾 (青緑の半球と珊瑚)
plinth(S, S)
# 殻の盾: 青緑の丸い殻を伏せた形 (下 3x3、上 3x3 の角を落とし、頂に一つ)。筋は磨いた石
for dx in range(-1, 2):
    for dz in range(-1, 2):
        put(S + dx, G + 2, S + dz, "minecraft:dark_prismarine" if (dx + dz) % 2 == 0 else "minecraft:prismarine_bricks")
        if abs(dx) + abs(dz) < 2:
            put(S + dx, G + 3, S + dz, "minecraft:prismarine")
put(S, G + 4, S, "minecraft:prismarine_bricks")
put(S, G + 5, S, "minecraft:sea_lantern")
for dx, dz in ((2, 0), (0, 2), (-2, 0), (0, -2)):
    put(S + dx, G + 2, S + dz, "minecraft:dark_prismarine_slab[type=bottom,waterlogged=false]")
# 南西: 雷翼 — 羽の柱 (銅の柱に避雷の針)
plinth(-S, S)
for dy in range(1, 6):
    put(-S, G + 1 + dy, S, "minecraft:waxed_cut_copper" if dy % 2 else "minecraft:waxed_exposed_cut_copper")
put(-S, G + 7, S, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
for dx, dz in ((1, 0), (-1, 0)):
    put(-S + dx, G + 5, S + dz, "minecraft:white_wool")
# 北西: 余燼 — 燠の炉 (黒石の椀と火)
plinth(-S, -S)
for dx in range(-1, 2):
    for dz in range(-1, 2):
        if (dx, dz) != (0, 0):
            put(-S + dx, G + 2, -S + dz, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"
                if abs(dx) + abs(dz) == 2 else "minecraft:polished_blackstone_bricks")
put(-S, G + 2, -S, "minecraft:magma_block")
put(-S, G + 3, -S, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")

# ---------------------------------------------------------------- 北の道と橋の台
for z in range(-31, GZ - 1):
    for x in (-1, 0, 1):
        t = top_at(x, z)
        if t is not None:
            put(x, t, z, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]" if (x + z) % 3 == 0 else "minecraft:smooth_quartz")
            for y in range(t + 1, t + 4):
                put(x, y, z, AIR)
BZ = -36
for z in range(BZ, -30):
    for x in (-2, -1, 0, 1, 2):
        put(x, G, z, "minecraft:quartz_bricks" if abs(x) < 2 else "minecraft:chiseled_quartz_block")
        put(x, G - 1, z, "minecraft:smooth_quartz")
        for y in range(G + 1, G + 4):
            put(x, y, z, AIR)
for x in (-2, 2):
    put(x, G + 1, BZ, "minecraft:quartz_pillar[axis=y]")
    put(x, G + 2, BZ, "minecraft:end_rod[facing=up]")
BRIDGE = (0, G + 1, BZ)

# ---------------------------------------------------------------- 草花と縁の岩と灯り
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        t = top_at(x, z)
        if t is None or math.hypot(x, z) <= PLAZA + 0.5 or get(x, t + 1, z) is not None:
            continue
        if get(x, t, z) != "minecraft:grass_block[snowy=false]":
            continue
        h = hsh(x, z, 21)
        d = math.hypot(x, z)
        if d > edge_r(x, z) - 3 and h < 0.035:
            # 縁の岩: 低い塊を二、三つ寄せる (高さ 1〜2、苔をかぶる)
            for dx, dz in ((0, 0), (1, 0), (0, 1)):
                if hsh(x + dx, z + dz, 24) < 0.7 and top_at(x + dx, z + dz) == t:
                    hh = 1 + int(hsh(x + dx, z + dz, 22) * 2)
                    for y in range(t + 1, t + 1 + hh):
                        put(x + dx, y, z + dz, "minecraft:andesite" if hsh(x + dx, y, z + dz, 23) < 0.4 else "minecraft:calcite")
                    if hsh(x + dx, z + dz, 25) < 0.5:
                        put(x + dx, t + 1 + hh, z + dz, "minecraft:moss_carpet")
        elif h < 0.05:
            put(x, t + 1, z, "minecraft:lily_of_the_valley")
        elif h < 0.09:
            put(x, t + 1, z, "minecraft:azure_bluet")
        elif h < 0.24:
            put(x, t + 1, z, "minecraft:short_grass")
for a in range(8):
    ang = a / 8 * math.tau + math.pi / 8
    x, z = round(math.cos(ang) * (PLAZA + 1.5)), round(math.sin(ang) * (PLAZA + 1.5))
    if abs(x) <= 2 and z < 0:
        continue
    t = top_at(x, z) or G
    put(x, t + 1, z, "minecraft:diorite_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    put(x, t + 2, z, "minecraft:end_rod[facing=up]")

# ---------------------------------------------------------------- 島のまわりの雲のうねり (平らな雲の海の上に、低い雲の塊)
CLOUD = "skyabyss:cloud"
import random
rnd = random.Random(50)
for _ in range(34):
    cx, cz = rnd.uniform(X0 + 6, X1 - 6), rnd.uniform(Z0 + 6, Z1 - 6)
    if math.hypot(cx, cz) < 18:
        continue
    rh, rv = rnd.uniform(4, 9), rnd.uniform(1.5, 3.5)
    for x in range(int(cx - rh) - 1, int(cx + rh) + 2):
        for z in range(int(cz - rh) - 1, int(cz + rh) + 2):
            for y in range(40, 40 + int(rv) + 2):
                d = ((x - cx) / rh) ** 2 + ((y - 39.5) / rv) ** 2 + ((z - cz) / rh) ** 2
                if d <= 1 + (noise2(x, z, 3.0, 31) - 0.5) * 0.6 and get(x, y, z) is None:
                    put(x, y, z, CLOUD)

# ---------------------------------------------------------------- 書き出し
palette, index = [], {}
runs = []
for y in range(Y0, Y1 + 1):
    for z in range(Z0, Z1 + 1):
        row = [cells.get((x, y, z)) for x in range(X0, X1 + 1)]
        if all(b is None for b in row):
            continue
        out = []
        i = 0
        while i < len(row):
            b = row[i]
            j = i
            while j < len(row) and row[j] == b:
                j += 1
            if b is None:
                idx = -1
            else:
                if b not in index:
                    index[b] = len(palette)
                    palette.append(b)
                idx = index[b]
            out += [idx, j - i]
            i = j
        runs.append([y, z, X0] + out)
marks = {"arrival": [0, G + 1, 3], "return": list(RETURN), "gate": list(GATE), "bridge_n": list(BRIDGE),
         "whale": [S, G + 1, -S], "tide": [S, G + 1, S], "storm": [-S, G + 1, S], "ember": [-S, G + 1, -S]}
data = {"note": "tools/gen/world/build_cloud_arrival.py が書く。手で直さない", "palette": palette, "runs": runs, "marks": marks,
        "rect": [X0, Z0, X1, Z1], "ground": G}
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
print("arrival", OUT.stat().st_size // 1024, "KB", len(cells), "cells", len(runs), "runs")
