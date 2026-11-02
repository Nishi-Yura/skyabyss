# -*- coding: utf-8 -*-
"""大樹の見どころ (poi_great_tree) を作る。2026-10-29 の試遊「大樹もっと大きくして」で、高さ 27・幅 17 から作り直した。
幹 (根もとが張り出す板根、上へ細り、少しうねる)・七本の太い枝と小枝・枝の先ごとの葉の房・垂れる蔓・
幹を巻いて上がる階段 (地面から樹上の床へ)・樹上の床と小屋 (宝箱 = 印 loot)・南の碑 (印 tablet)。
y = -1 が地面の層、y = 0 から上が地上。書き出しは data/skyabyss/opening/poi_great_tree.json
使い方: python -X utf8 tools/gen/world/build_great_tree.py
"""
import json
import math
from pathlib import Path

OUT = Path(__file__).resolve().parents[3] / "src/main/resources/data/skyabyss/opening/poi_great_tree.json"

AIR = "minecraft:air"
OAK_W = "minecraft:oak_wood[axis=y]"
DOAK_W = "minecraft:dark_oak_wood[axis=y]"
MOSS = "minecraft:moss_block"
LEAVES = ["minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]",
          "minecraft:dark_oak_leaves[distance=1,persistent=true,waterlogged=false]",
          "minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]",
          "minecraft:azalea_leaves[distance=1,persistent=true,waterlogged=false]",
          "minecraft:flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]"]
PLANKS = "minecraft:spruce_planks"
SLOG = "minecraft:stripped_spruce_log[axis=y]"
FENCE = "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]"
GRASS = "minecraft:grass_block[snowy=false]"

H = 36          # 幹の頂
D = 17          # 樹上の床の高さ
DECK_R = 6.6    # 床の外の半径


def hash3(x, y, z, s):
    h = (x * 374761393 + y * 668265263 + z * 2147483647 + s * 1274126177) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFF) / float(0x1000000)


def noise3(x, y, z, sc, s):
    # 値の格子を滑らかにつないだ雑音 (0..1)
    fx, fy, fz = x / sc, y / sc, z / sc
    ix, iy, iz = math.floor(fx), math.floor(fy), math.floor(fz)
    tx, ty, tz = fx - ix, fy - iy, fz - iz
    tx, ty, tz = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty), tz * tz * (3 - 2 * tz)
    v = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (tx if dx else 1 - tx) * (ty if dy else 1 - ty) * (tz if dz else 1 - tz)
                v += w * hash3(ix + dx, iy + dy, iz + dz, s)
    return v


cells = {}


def put(x, y, z, b):
    cells[(x, y, z)] = b


def get(x, y, z):
    return cells.get((x, y, z))


def wood(x, y, z):
    return DOAK_W if hash3(x, y, z, 3) < 0.22 else OAK_W


def is_wood(b):
    return b is not None and ("_wood" in b or "_log" in b)


# ---------------------------------------------------------------- 幹
def trunk_r(y):
    if y <= 2:
        return 3.4
    if y <= 16:
        return 3.4 - (y - 2) / 14 * 1.0
    return max(1.1, 2.4 - (y - 16) / 20 * 1.3)


def trunk_c(y):
    # 床より上で少しうねる (下はまっすぐ: 階段と床が巻きやすいように)
    if y <= D + 2:
        return 0.0, 0.0
    k = (y - D - 2) / (H - D - 2)
    return 1.3 * math.sin(k * 2.1) * k, -0.9 * math.sin(k * 2.7 + 0.4) * k


for y in range(0, H + 1):
    cx, cz = trunk_c(y)
    r = trunk_r(y)
    for x in range(-6, 7):
        for z in range(-6, 7):
            a = math.atan2(z, x)
            bump = 0.35 * (noise3(math.cos(a) * 4, y * 0.6, math.sin(a) * 4, 2.0, 11) - 0.5)
            if math.hypot(x - cx, z - cz) <= r + bump:
                # 北の根もとに苔
                b = MOSS if (z < -1 and y < 7 and hash3(x, y, z, 12) < 0.45) else wood(x, y, z)
                put(x, y, z, b)

# 板根: 七つの尾根が幹から外へ低くなる。その先は地を這う根
BUTTRESS = [(0.35, 9.5, 6), (1.25, 8.5, 5), (2.2, 10.0, 6), (3.05, 8.0, 5), (3.9, 9.5, 6), (4.75, 8.5, 5), (5.6, 9.0, 5)]
for i, (ang, length, hb) in enumerate(BUTTRESS):
    t = 2.6
    while t <= length:
        a = ang + 0.12 * math.sin(t * 0.8 + i)
        px, pz = math.cos(a) * t, math.sin(a) * t
        k = (t - 2.6) / (length - 2.6)
        h = hb * (1 - k) ** 1.5
        w = 0.55 + 0.7 * (1 - k)
        for x in range(math.floor(px - 2), math.ceil(px + 2) + 1):
            for z in range(math.floor(pz - 2), math.ceil(pz + 2) + 1):
                if math.hypot(x - px, z - pz) <= w:
                    for y in range(0, int(round(h)) + 1):
                        put(x, y, z, MOSS if (y == round(h) and hash3(x, y, z, 13) < 0.3) else wood(x, y, z))
        t += 0.35
    # 地を這う根 (地面の層に半分沈む)
    a = ang + 0.15
    t = length - 0.5
    while t < length + 4.0:
        x, z = round(math.cos(a) * t), round(math.sin(a) * t)
        put(x, -1 if hash3(x, 0, z, 14) < 0.5 else 0, z, "minecraft:oak_wood[axis=%s]" % ("x" if abs(math.cos(a)) > 0.7 else "z"))
        a += 0.09 * (1 if i % 2 else -1)
        t += 0.8

# ---------------------------------------------------------------- 枝と葉
BRANCHES = [(15, 60, 12, 6, 1.5), (18, 150, 12, 7, 1.5), (20, 245, 11, 8, 1.35), (23, 320, 10, 7, 1.25),
            (25, 15, 11, 8, 1.2), (28, 200, 9, 7, 1.05), (31, 285, 8, 6, 0.9)]
clusters = []  # (x, y, z, rh, rv)
lanterns = []


def blob(px, py, pz, r, block_fn):
    for x in range(math.floor(px - r), math.ceil(px + r) + 1):
        for y in range(math.floor(py - r), math.ceil(py + r) + 1):
            for z in range(math.floor(pz - r), math.ceil(pz + r) + 1):
                if (x - px) ** 2 + (y - py) ** 2 + (z - pz) ** 2 <= r * r:
                    put(x, y, z, block_fn(x, y, z))


def limb(x0, y0, z0, az, length, rise, r0, r1, depth):
    ca, sa = math.cos(math.radians(az)), math.sin(math.radians(az))
    steps = int(length / 0.4)
    end = None
    for s in range(steps + 1):
        t = s / steps
        px, pz = x0 + ca * length * t, z0 + sa * length * t
        py = y0 + rise * (t ** 1.25) + 0.6 * math.sin(t * 3.0 + az)
        blob(px, py, pz, r0 + (r1 - r0) * t, wood)
        end = (px, py, pz)
        if depth == 0 and abs(t - 0.55) < 0.02:
            for side in (-38, 41):
                limb(px, py, pz, az + side, length * 0.45, rise * 0.6, (r0 + (r1 - r0) * t) * 0.65, 0.5, 1)
        if depth == 0 and abs(t - 0.75) < 0.02:
            lanterns.append((round(px), round(py - (r0 + (r1 - r0) * t)) - 1, round(pz)))
    clusters.append((end[0], end[1] + 1.0, end[2], 5.6 if depth == 0 else 3.6, 3.6 if depth == 0 else 2.6))


for y0, az, length, rise, r0 in BRANCHES:
    cx, cz = trunk_c(y0)
    limb(cx, y0, cz, az, length, rise, r0, 0.6, 0)
tx, tz = trunk_c(H)
clusters.append((tx, H + 2, tz, 7.0, 4.6))
clusters.append((tx + 3, H - 3, tz - 4, 5.0, 3.4))
clusters.append((tx - 4, H - 4, tz + 3, 5.0, 3.4))


def deck_air(x, y, z):
    # 床の上の頭の空き (葉で塞がない)
    return D + 1 <= y <= D + 4 and math.hypot(x, z) <= DECK_R + 1.5


for cx, cy, cz, rh, rv in clusters:
    for x in range(math.floor(cx - rh - 2), math.ceil(cx + rh + 2) + 1):
        for y in range(math.floor(cy - rv - 2), math.ceil(cy + rv + 2) + 1):
            for z in range(math.floor(cz - rh - 2), math.ceil(cz + rh + 2) + 1):
                d = ((x - cx) / rh) ** 2 + ((y - cy) / rv) ** 2 + ((z - cz) / rh) ** 2
                if d <= 1 + (noise3(x, y, z, 2.4, 21) - 0.5) * 0.75 and abs(x) <= 15 and abs(z) <= 15 and y > D - 1:
                    if get(x, y, z) is None and not deck_air(x, y, z):
                        put(x, y, z, LEAVES[int(hash3(x, y, z, 22) * len(LEAVES)) % len(LEAVES)])

# ---------------------------------------------------------------- 樹上の床と小屋
deck = []
for x in range(-8, 9):
    for z in range(-8, 9):
        d = math.hypot(x, z)
        if d <= DECK_R and not is_wood(get(x, D, z)):
            deck.append((x, z))
            put(x, D, z, PLANKS)
# 床の縁の手すり (小屋と階段の口はあける)
HUT = (3, 6, -2, 2)  # x0, x1, z0, z1


def in_hut(x, z, pad=0):
    return HUT[0] - pad <= x <= HUT[1] + pad and HUT[2] - pad <= z <= HUT[3] + pad


# 支え: 幹から床の縁へ斜めに六本
for k in range(6):
    a = math.radians(k * 60 + 15)
    for s in range(0, 13):
        t = s / 12
        r = 2.6 + t * (DECK_R - 3.0)
        y = D - 5 + t * 4.5
        x, z = round(math.cos(a) * r), round(math.sin(a) * r)
        if get(x, round(y), z) is None:
            put(x, round(y), z, SLOG)

# 小屋: 床の東、幹に背を付けた小さな小屋 (柱・板の壁・窓・平らな屋根)。南に戸口
x0, x1, z0, z1 = HUT
for x in range(x0, x1 + 1):
    for z in range(z0, z1 + 1):
        put(x, D, z, PLANKS)
        edge = x in (x0, x1) or z in (z0, z1)
        corner = x in (x0, x1) and z in (z0, z1)
        for y in range(D + 1, D + 4):
            if corner:
                put(x, y, z, SLOG)
            elif edge:
                door = z == z1 and x == x0 + 2 and y <= D + 2
                window = y == D + 2 and ((x == x1 and z == 0) or (z == z0 and x == x0 + 2))
                if door:
                    put(x, y, z, AIR)
                elif window:
                    put(x, y, z, "minecraft:glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]" if x == x1 else
                        "minecraft:glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]")
                else:
                    put(x, y, z, PLANKS if y < D + 3 else "minecraft:stripped_spruce_log[axis=%s]" % ("z" if x in (x0, x1) else "x"))
            else:
                put(x, y, z, AIR)
for x in range(x0 - 1, x1 + 2):
    for z in range(z0 - 1, z1 + 2):
        put(x, D + 4, z, "minecraft:spruce_slab[type=bottom,waterlogged=false]")
LOOT = (x0 + 1, D + 1, z0 + 1)
put(*LOOT, "minecraft:chest[facing=south,type=single,waterlogged=false]")
put(x1 - 1, D + 1, z0 + 1, "minecraft:barrel[facing=up,open=false]")
put(x0 + 1, D + 3, 0, "minecraft:lantern[hanging=true,waterlogged=false]")

# ---------------------------------------------------------------- 幹を巻く階段 (南から上がり、床の北西へ出る)
path = []
th = math.radians(90)
while len(path) < 60:
    y = len(path)
    R = trunk_r(min(y, D)) + 1.15
    c = (round(math.cos(th) * R), round(math.sin(th) * R))
    if not path or c != path[-1]:
        if path and abs(c[0] - path[-1][0]) + abs(c[1] - path[-1][1]) == 2:
            path.append((c[0], path[-1][1]))
        path.append(c)
    th += 0.02
steps = path[:D + 1]
FACING = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}
for k in range(D):
    x, z = steps[k]
    nx, nz = steps[k + 1]
    f = FACING[(nx - x, nz - z)]
    # 段と頭の空き (根・幹を削る)
    for y in range(k + 1, k + 4):
        if (x, y, z) in cells and y != D or (y == D and (x, z) in deck):
            cells[(x, y, z)] = AIR
    put(x, k, z, "minecraft:spruce_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]" % f)
    # 段の下: 幹へ寄せた支え (三段おき)
    if k % 3 == 2 and get(x, k - 1, z) is None:
        put(x, k - 1, z, "minecraft:spruce_slab[type=top,waterlogged=false]")
# 階段の口の床 (床の高さの手前は抜く。上り切ったら床)
top_x, top_z = steps[D]
put(top_x, D, top_z, PLANKS)
for k in range(D - 3, D):
    x, z = steps[k]
    if get(x, D, z) == PLANKS:
        cells[(x, D, z)] = AIR
stair_open = {steps[k] for k in range(D - 4, D + 1)}

# 手すりと灯り
for x, z in deck:
    d = math.hypot(x, z)
    if d > DECK_R - 1.0 and not in_hut(x, z, 1) and (x, z) not in stair_open and get(x, D, z) == PLANKS and get(x, D + 1, z) is None:
        lamp = hash3(x, D, z, 31) < 0.12
        put(x, D + 1, z, FENCE)
        if lamp:
            put(x, D + 2, z, "minecraft:lantern[hanging=false,waterlogged=false]")

# 柵のつながり (となりが柵か、板・丸太・木のブロックならつなぐ)
for (x, y, z), bl in list(cells.items()):
    if bl.startswith("minecraft:spruce_fence"):
        def joins(n):
            return n is not None and (n.startswith("minecraft:spruce_fence") or n == PLANKS or "_log" in n or "_wood" in n)
        st = {d: "true" if joins(get(x + dx, y, z + dz)) else "false" for d, dx, dz in (("east", 1, 0), ("west", -1, 0), ("south", 0, 1), ("north", 0, -1))}
        cells[(x, y, z)] = "minecraft:spruce_fence[east=%s,north=%s,south=%s,waterlogged=false,west=%s]" % (st["east"], st["north"], st["south"], st["west"])

# 枝の下の吊り灯り
for x, y, z in lanterns:
    if get(x, y, z) is None and is_wood(get(x, y + 1, z)):
        put(x, y, z, "minecraft:lantern[hanging=true,waterlogged=false]")

# ---------------------------------------------------------------- 垂れる蔓 (葉の房の下)
for (x, y, z), b in list(cells.items()):
    if "leaves" in b and get(x, y - 1, z) is None and hash3(x, y, z, 41) < 0.14 and y > D + 1 and not (math.hypot(x, z) <= DECK_R + 1.5 and y - 4 <= D + 4):
        for k in range(1, 2 + int(hash3(x, y, z, 42) * 5)):
            if get(x, y - k, z) is not None or y - k <= D + 4 and math.hypot(x, z) <= DECK_R + 1.5:
                break
            put(x, y - k, z, "minecraft:cave_vines[age=0,berries=%s]" % ("true" if hash3(x, y - k, z, 43) < 0.3 else "false"))

# ---------------------------------------------------------------- 地面: 根もとの土と苔、まわりの草花、南の碑
for x in range(-14, 15):
    for z in range(-14, 15):
        d = math.hypot(x, z)
        if d > 14.5:
            continue
        if get(x, -1, z) is None:
            if d < 7:
                g = "minecraft:rooted_dirt" if hash3(x, -1, z, 51) < 0.4 else MOSS if hash3(x, -1, z, 52) < 0.5 else "minecraft:coarse_dirt"
            elif d < 10 and hash3(x, -1, z, 53) < 0.35:
                g = MOSS
            else:
                g = GRASS
            put(x, -1, z, g)
        if get(x, 0, z) is None and 4 < d < 14 and get(x, -1, z) in (GRASS, MOSS):
            h = hash3(x, 0, z, 54)
            if h < 0.05:
                put(x, 0, z, "minecraft:fern")
            elif h < 0.10:
                put(x, 0, z, "minecraft:moss_carpet")
            elif h < 0.13:
                put(x, 0, z, ["minecraft:azure_bluet", "minecraft:oxeye_daisy", "minecraft:lily_of_the_valley"][int(h * 1000) % 3])
            elif h < 0.25:
                put(x, 0, z, "minecraft:short_grass")
TABLET = (0, 0, 13)
for y in range(0, 3):
    cells.pop((0, y, 13), None)
put(*TABLET, "skyabyss:tablet[facing=south,standing=true]")
put(0, -1, 13, "minecraft:mossy_cobblestone")

# ---------------------------------------------------------------- 書き出し
palette, index, blocks = [], {}, []
for (x, y, z), b in sorted(cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
    if b not in index:
        index[b] = len(palette)
        palette.append(b)
    blocks.append([x, y, z, index[b]])
data = {"site": "poi_great_tree",
        "note": "天と淵の大陸の見どころ (docs/design/40 の 3 章)。tools/gen/world/build_great_tree.py が作る (2026-10-29 に高さ 45・幅 30 へ作り直し)",
        "palette": palette, "blocks": blocks,
        "marks": {"loot": list(LOOT), "tablet": list(TABLET)},
        "meta": {"rect": [-14, -14, 14, 14], "level": True, "round": True, "ring": 6}}
OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
ys = [c[1] for c in cells]
print("great tree: %d blocks, y %d..%d, steps top %s, loot %s" % (len(blocks), min(ys), max(ys), steps[D], LOOT))
