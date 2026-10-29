# -*- coding: utf-8 -*-
"""終章「雲海」の島 2「灯の島」(docs/design/50 の 4.2.2)。彫って data/skyabyss/finale/isle2.json に書く (FinaleWorld の版 3 で流し込む)。

  鐘の島の鐘楼の上 (主の間。床 y 103、真ん中 (0, -186)) の北の縁から、光の橋が北へ下る (印 bridge_gate = 主を倒した人だけ渡れる所)。
  島 (中心 (0, -300)、東西 72・南北 64、地面 y 90): 前の潮の浜。南の半分は砂と砂利の浜 (潮だまり・流木・伏せた舟・干し網の竿)、
  真ん中に漁師の小屋 5、浜と草地に海食の岩の柱と崩れた石垣 (光の筋の陰)。西・東・南東の縁から突堤、先に漁の灯 (印 lamp_w / lamp_e / lamp_s)。
  北の端に傾いた灯台 (高さ 30、上ほど東へ傾く)。南の面に扉 (印 door)。上は主の間 (丸い回廊 半径 12、陰になる石柱 4。印 arena・lamp = 光の出る所)。

  python -X utf8 tools/gen/world/build_cloud_isle2.py
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cloud_kit as K  # noqa: E402

OUT = K.ROOT / "src/main/resources/data/skyabyss/finale/isle2.json"
G = 90
CZ = -300
RX, RZ = 36, 32
# 鐘の島の主の間 (build_cloud_isle1.py の TOP と鐘楼の位置)
A1_TOP, A1_Z = 103, -186
g = K.Grid(-64, 64, -352, -194, 14, 140)
LP = "skyabyss:light_path"
LPS = "skyabyss:light_path_slab[type=bottom,waterlogged=false]"
SAND = ["minecraft:sand", "minecraft:sand", "minecraft:sand", "minecraft:gravel", "minecraft:suspicious_sand[dusted=0]"]

# ---------------------------------------------------------------- 島
K.island(g, 0, CZ, RX, RZ, G, 36, 201, wobble=5.0, rim_rocks=0.02)


def on_main(x, z):
    return math.hypot(x / RX, (z - CZ) / RZ) < 1.0


def beach(x, z):
    """南の半分 (と縁) は浜"""
    k = g.edge.get((x, z), 0)
    return z > CZ - 6 + 6 * math.sin(x * 0.15) or k < 0.18


# 浜の砂: 上の 3 段を砂と砂利、その下は砂岩
for (x, z), t in list(g.tops.items()):
    if not beach(x, z):
        continue
    h = K.hsh(x, z, 210)
    g.put(x, t, z, "minecraft:gravel" if h < 0.18 else "minecraft:sand")
    for y in range(t - 3, t):
        g.put(x, y, z, "minecraft:sand" if y > t - 2 else "minecraft:sandstone")
# 潮だまり (浜の内側の浅いくぼみに水 1 段。まわりは砂で閉じる)
pools = 0
for (x, z), t in sorted(g.tops.items()):
    if pools >= 9 or not beach(x, z) or g.edge.get((x, z), 0) < 0.35 or K.hsh(x, z, 211) > 0.012:
        continue
    cells = [(x + dx, z + dz) for dx in range(-1, 2) for dz in range(-1, 1 + (K.hsh(x, z, 212) < 0.5))]
    if all(g.tops.get(c) == t and beach(*c) for c in cells) and all(g.tops.get((cx + ox, cz + oz)) is not None and g.tops[(cx + ox, cz + oz)] >= t
                                                                       for cx, cz in cells for ox, oz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
        for cx, cz in cells:
            g.put(cx, t, cz, "minecraft:water[level=0]")
            g.put(cx, t - 1, cz, "minecraft:sand")
        g.put(x, t - 1, z, "minecraft:prismarine")
        pools += 1


def top(x, z):
    return g.tops.get((x, z))


def clear_above(x, z, t, h=5):
    for y in range(t + 1, t + 1 + h):
        if g.get(x, y, z) is None:
            g.put(x, y, z, K.AIR)


# ---------------------------------------------------------------- 灯台 (北の端。上ほど東へ傾く)
TX, TZ = 0, -322
tt = max(top(x, z) or G for x in range(TX - 5, TX + 6) for z in range(TZ - 5, TZ + 6))
H = 30
TOP = tt + H
LEAN = 0.1


def lean(y):
    return LEAN * (y - tt)


for y in range(tt - 4, TOP):
    ox = lean(y)
    band = ((y - tt) // 5) % 2
    for x in range(TX - 7, TX + 8):
        for z in range(TZ - 6, TZ + 7):
            d = math.hypot(x - TX - ox, z - TZ)
            r = 4.6 - 0.025 * (y - tt)
            if d <= r:
                if d > r - 1.2:
                    b = "minecraft:calcite" if band == 0 else "minecraft:terracotta"
                    if K.hsh(x, y, z, 220) < 0.06:
                        b = "minecraft:diorite"
                else:
                    b = "minecraft:stone"
                g.put(x, y, z, b)
# 土台の石の裾 (傾きを支える。南は扉の前を空ける)
for x in range(TX - 7, TX + 8):
    for z in range(TZ - 7, TZ + 8):
        d = math.hypot(x - TX, z - TZ)
        if 4.4 < d <= 6.6 and not (abs(x - TX) <= 1 and z > TZ):
            t = top(x, z) or tt
            for y in range(t - 1, tt + 2 + int(2 * (1 - (d - 4.4) / 2.2))):
                g.put(x, y, z, "minecraft:mossy_cobblestone" if K.hsh(x, y, z, 221) < 0.4 else "minecraft:cobblestone")
# 灯の部屋 (回廊の下の 3 段。海の灯と風化した銅の柱が交互に巡る。島を回る光の筋はここから。印 lamp_room)
for y in range(TOP - 5, TOP - 2):
    ox = lean(y)
    for x in range(TX - 7, TX + 8):
        for z in range(TZ - 6, TZ + 7):
            d = math.hypot(x - TX - ox, z - TZ)
            r = 4.6 - 0.025 * (y - tt)
            if r - 1.2 < d <= r:
                a = math.degrees(math.atan2(z - TZ, x - TX - ox)) % 360
                g.put(x, y, z, "minecraft:sea_lantern" if int(a // 30) % 2 == 0 else "minecraft:weathered_copper")
g.marks["lamp_room"] = [TX + round(lean(TOP - 4)), TOP - 4, TZ]
# 窓 (細い鉄格子。南と東西)
for y in range(tt + 6, TOP - 4, 7):
    ox = round(lean(y))
    for x, z in ((TX + ox, TZ + 4), (TX + ox - 4, TZ), (TX + ox + 4, TZ)):
        for k in range(2):
            g.put(x, y + k, z, "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]")
# 扉 (南の面の真ん中。印 door)
DOOR = (TX, tt + 1, TZ + 5)
for z in range(TZ + 3, TZ + 9):
    for x in range(TX - 1, TX + 2):
        for y in range(tt + 1, tt + 4):
            if z >= TZ + 5:
                g.put(x, y, z, K.AIR)
for x in range(TX - 1, TX + 2):
    for z in range(TZ + 5, TZ + 9):
        g.put(x, tt, z, "minecraft:stone_bricks")
for y in range(tt + 1, tt + 4):
    for x in (TX - 1, TX + 1):
        g.put(x, y, TZ + 4, "minecraft:stripped_spruce_log[axis=y]")
g.put(TX, tt + 4, TZ + 4, "minecraft:stripped_spruce_log[axis=x]")
g.put(*DOOR, "minecraft:chiseled_stone_bricks")
g.put(TX, tt + 2, TZ + 4, "minecraft:spruce_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]")
g.put(TX, tt + 2, TZ + 5, K.AIR)
g.marks["door"] = list(DOOR)
g.marks["door_front"] = [TX, tt + 1, TZ + 7]

# ---------------------------------------------------------------- 主の間 (灯台の上の丸い回廊。陰になる石柱 4)
AX, AZ = TX + round(lean(TOP)), TZ
AR = 12
for x in range(AX - AR - 1, AX + AR + 2):
    for z in range(AZ - AR - 1, AZ + AR + 2):
        d = math.hypot(x - AX, z - AZ)
        if d > AR + 0.5:
            continue
        if d > AR - 0.6:
            g.put(x, TOP, z, "minecraft:weathered_cut_copper")
            g.put(x, TOP + 1, z, "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]")
        else:
            b = "minecraft:polished_andesite" if (int(d) % 3) else "minecraft:smooth_stone"
            if 2.4 < d < 3.2:
                b = "minecraft:oxidized_cut_copper"
            g.put(x, TOP, z, b)
        for y in range(TOP + 2 if d > AR - 0.6 else TOP + 1, TOP + 10):
            g.put(x, y, z, K.AIR)
        # 縁の下の受け (外へ張り出す床を支える)
        if d > 4.5 and (K.hsh(x, z, 230) < 0.6 or d > AR - 1.5):
            g.put(x, TOP - 1, z, "minecraft:spruce_planks" if d < AR - 1 else "minecraft:stripped_spruce_log[axis=y]")
# 張り出しの斜めの梁 8 本
for a in range(8):
    ang = a * math.pi / 4 + math.pi / 8
    for k in range(6):
        r = 4.5 + k
        g.put(round(AX + math.cos(ang) * r), TOP - 2 - (5 - k) // 2, round(AZ + math.sin(ang) * r), "minecraft:stripped_spruce_log[axis=y]")
# 陰の石柱 (2x2、高さ 5)
for i, ang in enumerate((45, 135, 225, 315)):
    a = math.radians(ang)
    px, pz = round(AX + math.cos(a) * 7.2), round(AZ + math.sin(a) * 7.2)
    for dx in (0, 1):
        for dz in (0, 1):
            for y in range(TOP + 1, TOP + 6):
                g.put(px + dx, y, pz + dz, "minecraft:polished_tuff" if y < TOP + 5 else "minecraft:tuff_brick_slab[type=bottom,waterlogged=false]")
    g.marks["pillar_%d" % (i + 1)] = [px, TOP + 1, pz]
g.marks["arena"] = [AX, TOP, AZ]
g.marks["lamp"] = [AX, TOP + 3, AZ]
g.marks["lord_spawn"] = [AX, TOP + 1, AZ]
g.marks["arena_entry"] = [AX, TOP + 1, AZ + 9]
g.marks["arena_back"] = [AX - 9, TOP + 1, AZ]
g.put(AX - 9, TOP + 1, AZ, "minecraft:lodestone")

# ---------------------------------------------------------------- 漁師の小屋 5 (流木の板・切妻の屋根)
def hut(x0, z0, w, d, seed, door_south=True):
    t = max(top(x, z) or G for x in range(x0, x0 + w) for z in range(z0, z0 + d))
    for x in range(x0 - 1, x0 + w + 1):
        for z in range(z0 - 1, z0 + d + 1):
            if top(x, z) is not None:
                for y in range(top(x, z) + 1, t + 1):
                    g.put(x, y, z, "minecraft:cobblestone")
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            g.put(x, t, z, "minecraft:stripped_spruce_wood[axis=y]" if K.hsh(x, z, seed) < 0.3 else "minecraft:spruce_planks")
            edge = x in (x0, x0 + w - 1) or z in (z0, z0 + d - 1)
            corner = x in (x0, x0 + w - 1) and z in (z0, z0 + d - 1)
            door = x == x0 + w // 2 and z == (z0 + d - 1 if door_south else z0)
            for y in range(t + 1, t + 4):
                if not edge or (door and y <= t + 2):
                    g.put(x, y, z, K.AIR)
                elif corner:
                    g.put(x, y, z, "minecraft:stripped_oak_log[axis=y]")
                elif y == t + 2 and (x + z) % 3 == 1 and not door:
                    g.put(x, y, z, "minecraft:spruce_trapdoor[facing=%s,half=bottom,open=true,powered=false,waterlogged=false]"
                          % ("south" if z == z0 + d - 1 else "north" if z == z0 else "east" if x == x0 + w - 1 else "west"))
                else:
                    h = K.hsh(x, y, z, seed + 1)
                    g.put(x, y, z, "minecraft:birch_planks" if h < 0.5 else "minecraft:stripped_birch_log[axis=y]" if h < 0.7 else "minecraft:spruce_planks")
    # 切妻 (東西に棟)
    half = (d + 1) // 2
    for i in range(half + 1):
        y = t + 4 + i
        for x in range(x0 - 1, x0 + w + 1):
            for z, f in ((z0 - 1 + i, "south"), (z0 + d - i, "north")):
                if z0 - 1 + i > z0 + d - i:
                    continue
                if z0 - 1 + i == z0 + d - i:
                    g.put(x, y, z, "minecraft:dark_oak_slab[type=bottom,waterlogged=false]")
                else:
                    g.put(x, y, z, "minecraft:dark_oak_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]" % f)
            # 妻の壁
            if i < half:
                for z in range(z0 + i, z0 + d - i):
                    for x in (x0, x0 + w - 1):
                        g.put(x, y, z, "minecraft:birch_planks")
    # 中: 樽・網 (鎖)・ろうそく
    g.put(x0 + 1, t + 1, z0 + 1, "minecraft:barrel[facing=up,open=false]")
    g.put(x0 + w - 2, t + 1, z0 + 1, "minecraft:chest[facing=south,type=single,waterlogged=false]" if False else "minecraft:composter[level=0]")
    g.put(x0 + 1, t + 3, z0 + d // 2, "minecraft:chain[axis=x,waterlogged=false]")
    g.put(x0 + w - 2, t + 1, z0 + d - 2, "minecraft:candle[candles=2,lit=false,waterlogged=false]")
    # 戸口の前の踏み石
    dz = z0 + d if door_south else z0 - 1
    g.put(x0 + w // 2, top(x0 + w // 2, dz) or t, dz, "minecraft:spruce_planks")
    return t


HUTS = ((-22, -306, 7, 6, True), (-12, -296, 6, 6, True), (8, -298, 7, 6, True), (19, -307, 6, 6, True), (-4, -310, 6, 5, False))
for i, (x0, z0, w, d, s) in enumerate(HUTS):
    hut(x0, z0, w, d, 240 + i * 7, s)

# ---------------------------------------------------------------- 陰: 海食の岩の柱と崩れた石垣 (光の筋から隠れる所)
STACKS = ((-14, -276, 2.2, 8), (12, -279, 2.0, 7), (-27, -287, 1.8, 6), (27, -288, 2.4, 9), (0, -284, 1.6, 6), (-30, -300, 2.0, 7),
          (31, -300, 1.8, 7), (-18, -320, 2.0, 8), (20, -318, 2.2, 8), (-6, -270, 1.5, 5), (22, -272, 1.6, 6), (-24, -270, 1.6, 5))
ROCK = ["minecraft:tuff", "minecraft:andesite", "minecraft:calcite", "minecraft:tuff", "minecraft:diorite"]
for i, (sx, sz, r, h) in enumerate(STACKS):
    t0 = top(sx, sz)
    if t0 is None:
        continue
    for x in range(int(sx - r - 1), int(sx + r + 2)):
        for z in range(int(sz - r - 1), int(sz + r + 2)):
            d = math.hypot(x - sx, z - sz)
            t = top(x, z)
            if t is None:
                continue
            hh = int(h * (1 - (d / (r + 0.6)) ** 2) + 2 * (K.hsh(x, z, 250 + i) - 0.5))
            if d > r + 0.5 or hh <= 0:
                continue
            for y in range(t + 1, t0 + hh + 1):
                band = int((y + 2 * K.hsh(x, z, 251)) // 2) % len(ROCK)
                g.put(x, y, z, ROCK[band])
            if K.hsh(x, z, 252 + i) < 0.35:
                g.put(x, t0 + hh + 1, z, "minecraft:moss_carpet")
WALLS = (((-8, -288), (-3, -288)), ((5, -290), (10, -286)), ((-20, -292), (-20, -286)), ((15, -294), (20, -294)), ((-4, -322), (-4, -316)))
for i, ((ax, az), (bx, bz)) in enumerate(WALLS):
    n = max(abs(bx - ax), abs(bz - az))
    for k in range(n + 1):
        x, z = round(ax + (bx - ax) * k / n), round(az + (bz - az) * k / n)
        t = top(x, z)
        if t is None:
            continue
        hh = 2 + int(K.hsh(x, z, 260 + i) * 2)
        for y in range(t + 1, t + 1 + hh):
            g.put(x, y, z, "minecraft:mossy_stone_bricks" if K.hsh(x, y, z, 261) < 0.4 else "minecraft:stone_bricks" if K.hsh(x, y, z, 262) < 0.7 else "minecraft:cracked_stone_bricks")

# ---------------------------------------------------------------- 浜の小物: 流木・伏せた舟・干し網の竿
def driftwood(x, z, ln, axis):
    t = top(x, z)
    if t is None:
        return
    for k in range(ln):
        px, pz = (x + k, z) if axis == "x" else (x, z + k)
        if top(px, pz) is not None and g.get(px, top(px, pz) + 1, pz) is None:
            g.put(px, top(px, pz) + 1, pz, "minecraft:stripped_oak_log[axis=%s]" % axis)


for i, (x, z, ln, ax) in enumerate(((-16, -279, 4, "x"), (6, -274, 3, "z"), (24, -283, 4, "x"), (-31, -292, 3, "z"), (14, -284, 3, "x"))):
    driftwood(x, z, ln, ax)


def boat(x, z):
    t = top(x, z)
    if t is None:
        return
    for k in range(5):
        w = 1 if k in (0, 4) else 2
        for dx in range(-w + 1, w):
            g.put(x + dx, t + 1, z + k, "minecraft:spruce_stairs[facing=%s,half=top,shape=straight,waterlogged=false]" % ("west" if dx < 0 else "east")
                  if dx != 0 else "minecraft:spruce_slab[type=top,waterlogged=false]")


boat(-9, -281)
boat(17, -276)
for i, (x, z) in enumerate(((-2, -277), (8, -282))):
    t = top(x, z)
    if t is None:
        continue
    for dx in (0, 4):
        for y in range(t + 1, t + 4):
            g.put(x + dx, y, z, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
    for dx in range(1, 4):
        g.put(x + dx, t + 3, z, "minecraft:chain[axis=x,waterlogged=false]")

# ---------------------------------------------------------------- 突堤と漁の灯 (西・東・南東。印 lamp_<k>・frag_<k>)
def jetty(key, ang_deg, length):
    a = math.radians(ang_deg)
    dx, dz = math.cos(a), math.sin(a)
    # 島の縁を探す
    r = 0
    while on_main(round(dx * r), round(CZ + dz * r)) or top(round(dx * r), round(CZ + dz * r)) is not None:
        r += 1
    base = (round(dx * (r - 3)), round(CZ + dz * (r - 3)))
    ty = top(*base) or G
    nx, nz = -dz, dx
    for k in range(-3, length + 1):
        cx, cz = dx * (r + k), CZ + dz * (r + k)
        for w in (-1, 0, 1):
            x, z = round(cx + nx * w), round(cz + nz * w)
            g.put(x, ty, z, "minecraft:spruce_planks" if w == 0 or K.hsh(x, z, 270) < 0.8 else "minecraft:spruce_slab[type=top,waterlogged=false]")
            for y in range(ty + 1, ty + 4):
                if g.get(x, y, z) in (None,) or (g.get(x, y, z) or "").startswith("minecraft:moss_carpet"):
                    g.put(x, y, z, K.AIR)
        if k > 0 and k % 3 == 0:
            for w in (-2, 2):
                x, z = round(cx + nx * w), round(cz + nz * w)
                for y in range(ty - 5, ty + 2):
                    g.put(x, y, z, "minecraft:stripped_spruce_log[axis=y]" if y <= ty else "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
    ex, ez = round(dx * (r + length)), round(CZ + dz * (r + length))
    g.put(ex, ty + 1, ez, "minecraft:polished_andesite")
    g.put(ex, ty + 2, ez, "minecraft:redstone_lamp[lit=false]")
    g.marks["lamp_" + key] = [ex, ty + 2, ez]
    g.marks["frag_" + key] = [base[0], ty + 1, base[1]]


jetty("w", 180, 9)
jetty("e", 0, 9)
jetty("s", 50, 8)

# ---------------------------------------------------------------- 光の橋 (鐘の島の主の間の北の縁 → 島 2 の南の端)
Z0, Z1 = A1_Z - 11, None
z = CZ + RZ + 6
while top(0, z) is None:
    z -= 1
LAND_Z = z
LAND = top(0, LAND_Z)
# 主の間の胸壁を開ける (北の縁) と、平らな出だし
for zz in range(A1_Z - 10, A1_Z - 15, -1):
    for x in (-1, 0, 1):
        g.put(x, A1_TOP, zz, LP)
        for y in range(A1_TOP + 1, A1_TOP + 5):
            g.put(x, y, zz, K.AIR)
g.marks["bridge_gate"] = [0, A1_TOP + 1, A1_Z - 13]
ZS = A1_Z - 15
for zz in range(ZS, LAND_Z, -1):
    t = (ZS - zz) / (ZS - LAND_Z)
    h2 = round((A1_TOP + (LAND - A1_TOP) * t) * 2)
    y, half = h2 // 2, h2 % 2 == 1
    for x in (-1, 0, 1):
        if top(x, zz) is not None and top(x, zz) >= y:
            continue
        g.put(x, y, zz, LP)
        g.put(x, y + 1, zz, LPS if half else K.AIR)
        for yy in range(y + 2, y + 5):
            g.put(x, yy, zz, K.AIR)
    if (ZS - zz) % 9 == 4:
        for x in (-2, 2):
            g.put(x, y + 1, zz, "minecraft:quartz_pillar[axis=y]")
            g.put(x, y + 2, zz, "minecraft:end_rod[facing=up]")
g.marks["arrive"] = [0, LAND + 1, LAND_Z - 3]
g.marks["center"] = [0, (top(0, CZ) or G) + 1, CZ + 8]

# ---------------------------------------------------------------- 草花と雲のうねり
K.flowers(g, 280, keep=lambda x, z: beach(x, z))
for (x, z), t in g.tops.items():
    if beach(x, z) and g.get(x, t, z) == "minecraft:sand" and g.get(x, t + 1, z) is None and K.hsh(x, z, 281) < 0.03:
        g.put(x, t + 1, z, "minecraft:dead_bush")
K.billows(g, 290, 40, lambda x, z: on_main(x, z) or abs(x) < 4)
kb, cells, runs = g.write(OUT, "tools/gen/world/build_cloud_isle2.py が書く。手で直さない", {"ground": G, "top": TOP})
print("isle2", kb, "KB", cells, "cells", runs, "runs", "lighthouse top", TOP, "land", LAND, LAND_Z)
