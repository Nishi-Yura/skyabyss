# -*- coding: utf-8 -*-
"""終章「雲海」の島 4「約束の島」(docs/design/50 の 4.2.4)。彫って data/skyabyss/finale/isle4.json に書く (FinaleWorld の版 5 で流し込む)。

  炉の島の大炉の上 (主の間。床 y 127、真ん中 (3, -466)) の北の縁から、光の橋が北へ下る (印 bridge_gate = 炉の島の主を倒した人だけ渡れる所)。
  島 (中心 (3, -578)、東西 72・南北 60、地面 y 116): 前の月の野営。月の砂 (灰と白の粉) の地面と浅いくぼみ、ところどころ草が戻っている。
  真ん中に焚き火と四つの石の座 (印 camp)。まわりに四人の天幕 (南 = 谷の私・西 = 崖の私・東 = 野の私・北 = 淵の私。印 tent_<1..4>、前に立つ所 self_<1..4>)。
  地面に刺さった巡礼者の羽 (白い大きな羽) がいくつか。北の端に月見の岩 (方解石と凝灰岩の層。南の根元に扉 = 印 door)。
  上は主の間 (丸い床 半径 12。印 arena)。北の縁に、巡礼者の座へ続く橋の台 (印 seat_bridge。作る順 3 で使う)。

  python -X utf8 tools/gen/world/build_cloud_isle4.py
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cloud_kit as K  # noqa: E402

OUT = K.ROOT / "src/main/resources/data/skyabyss/finale/isle4.json"
G = 116
CX, CZ = 3, -578
RX, RZ = 36, 30
# 炉の島の主の間 (build_cloud_isle3.py の TOP と主の間の真ん中)
A3_TOP, A3_X, A3_Z = 127, 3, -466
g = K.Grid(-48, 56, -632, -476, 30, 168)
LP = "skyabyss:light_path"
LPS = "skyabyss:light_path_slab[type=bottom,waterlogged=false]"
DUST = ["minecraft:light_gray_concrete_powder", "minecraft:light_gray_concrete_powder", "minecraft:white_concrete_powder", "minecraft:gray_concrete_powder"]

# ---------------------------------------------------------------- 島
K.island(g, CX, CZ, RX, RZ, G, 36, 401, wobble=5.0, rim_rocks=0.04)


def top(x, z):
    return g.tops.get((x, z))


def on_main(x, z):
    return math.hypot((x - CX) / RX, (z - CZ) / RZ) < 1.0


def clear_above(x, z, t, h=5):
    for y in range(t + 1, t + 1 + h):
        if g.get(x, y, z) is None:
            g.put(x, y, z, K.AIR)


# 月の砂の地面 (縁の近くは草が戻っている。斑はなめらかな雑音で)
for (x, z), t in list(g.tops.items()):
    k = g.edge.get((x, z), 1.0)
    n = K.noise2(x, z, 7.0, 410) + 0.2 * (K.hsh(x, z, 411) - 0.5)
    if k < 0.16 + 0.12 * K.noise2(x, z, 5.0, 412):
        b = K.GRASS if n > 0.3 else "minecraft:moss_block"
    elif n < 0.3:
        b = "minecraft:light_gray_concrete_powder"
    elif n < 0.62:
        b = "minecraft:white_concrete_powder"
    elif n < 0.8:
        b = "minecraft:light_gray_concrete_powder"
    else:
        b = "minecraft:gray_concrete_powder"
    g.put(x, t, z, b)
    if b != K.GRASS and b != "minecraft:moss_block":
        for y in range(t - 2, t):
            g.put(x, y, z, "minecraft:tuff" if K.hsh(x, y, z, 413) < 0.5 else "minecraft:calcite")

# 浅いくぼみ (月の面の名残。縁は白い粉が盛り上がり、底は灰の粉)
CRATERS = [(-20, -566, 4.2), (24, -590, 3.4), (-14, -594, 2.6), (28, -562, 2.4), (-24, -584, 2.0)]
for (cx, cz, r) in CRATERS:
    for x in range(int(cx - r - 2), int(cx + r + 3)):
        for z in range(int(cz - r - 2), int(cz + r + 3)):
            t = top(x, z)
            if t is None or not on_main(x, z):
                continue
            d = math.hypot(x - cx, z - cz)
            if d < r - 0.6:
                dep = 1 if d > r * 0.45 else 2
                g.put(x, t, z, K.AIR)
                if dep == 2:
                    g.put(x, t - 1, z, K.AIR)
                g.put(x, t - dep, z, "minecraft:gray_concrete_powder" if K.hsh(x, z, 420) < 0.7 else "minecraft:tuff")
                g.tops[(x, z)] = t - dep
            elif d < r + 0.9:
                g.put(x, t + 1, z, "minecraft:white_concrete_powder" if K.hsh(x, z, 421) < 0.6 else "minecraft:calcite")
                g.tops[(x, z)] = t + 1

# 月の石 (方解石と凝灰岩の丸い岩。低い)
for i in range(14):
    x, z = round(CX - 30 + K.hsh(i, 430) * 60), round(CZ - 24 + K.hsh(i, 431) * 48)
    if not on_main(x, z) or math.hypot(x - CX, z - (CZ + 4)) < 18:
        continue
    r = 1.0 + K.hsh(i, 432) * 1.4
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            t = top(x + dx, z + dz)
            if t is None:
                continue
            for dy in range(0, 3):
                if dx * dx + dz * dz + (dy * 1.4) ** 2 <= r * r:
                    g.put(x + dx, t + 1 + dy, z + dz, "minecraft:calcite" if K.hsh(x + dx, dy, z + dz, 433) < 0.55 else "minecraft:tuff")

# ---------------------------------------------------------------- 野営 (焚き火・四つの石の座・寝具と荷)
CAMP = (CX, CZ + 4)
ct = top(*CAMP)
for x in range(CAMP[0] - 6, CAMP[0] + 7):
    for z in range(CAMP[1] - 6, CAMP[1] + 7):
        d = math.hypot(x - CAMP[0], z - CAMP[1])
        if d <= 6.4:
            g.put(x, ct, z, "minecraft:light_gray_concrete_powder" if d > 2.4 else "minecraft:gray_concrete_powder")
            for y in range(top(x, z) + 1 if top(x, z) is not None else ct + 1, ct + 1):
                g.put(x, y, z, "minecraft:tuff")
            g.tops[(x, z)] = ct
            clear_above(x, z, ct, 5)
# 焚き火と石の輪
g.put(CAMP[0], ct + 1, CAMP[1], "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
for a in range(10):
    ang = a * math.pi / 5
    x, z = round(CAMP[0] + math.cos(ang) * 1.6), round(CAMP[1] + math.sin(ang) * 1.6)
    if (x, z) != CAMP:
        g.put(x, ct + 1, z, "minecraft:tuff_slab[type=bottom,waterlogged=false]")
# 四つの石の座 (四人の座った所。それぞれの色の小さな印)
SEAT_MARK = {1: "minecraft:blue_carpet", 2: "minecraft:brown_carpet", 3: "minecraft:white_carpet", 4: "minecraft:purple_carpet"}
for i, (dx, dz) in enumerate(((0, 4), (-4, 0), (4, 0), (0, -4))):
    x, z = CAMP[0] + dx, CAMP[1] + dz
    g.put(x, ct + 1, z, "minecraft:polished_tuff")
    g.put(x, ct + 2, z, SEAT_MARK[i + 1])
g.marks["camp"] = [CAMP[0], ct + 1, CAMP[1]]

# ---------------------------------------------------------------- 四人の天幕 (棟のある三角の天幕。入口は焚き火の側。前に立つ所 self_<k>)
TENTS = {1: ((CX, CZ + 17), "minecraft:blue_wool", "south"),
         2: ((CX - 13, CZ + 4), "minecraft:brown_wool", "west"),
         3: ((CX + 13, CZ + 4), "minecraft:white_wool", "east"),
         4: ((CX, CZ - 9), "minecraft:purple_wool", "north")}
FENCE = "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]"


def tent(k, pos, wool, side):
    x0, z0 = pos
    # 焚き火の方へ開く向き (L = 前後、A = 左右)
    ox, oz = CAMP[0] - x0, CAMP[1] - z0
    ln = math.hypot(ox, oz) or 1
    ox, oz = round(ox / ln), round(oz / ln)
    ax, az = -oz, ox

    def at(L, A):
        return x0 + ox * L + ax * A, z0 + oz * L + az * A

    cells = [at(L, A) for L in range(-5, 5) for A in range(-4, 5)]
    t = max(top(x, z) or G for x, z in cells if top(x, z) is not None)
    for x, z in cells:
        tt = top(x, z)
        if tt is None:
            continue
        for y in range(tt + 1, t + 1):
            g.put(x, y, z, "minecraft:tuff")
        g.put(x, t, z, "minecraft:light_gray_concrete_powder")
        g.tops[(x, z)] = t
        clear_above(x, z, t, 6)
    for L in range(-3, 3):
        for A in range(-3, 4):
            x, z = at(L, A)
            g.put(x, t, z, "minecraft:spruce_planks" if abs(A) <= 2 else "minecraft:light_gray_concrete_powder")
            for lay in range(1, 5):
                y = t + lay
                wall = abs(A) == 4 - lay
                if L == -3 and abs(A) <= 4 - lay:
                    g.put(x, y, z, wool)
                elif wall:
                    g.put(x, y, z, wool)
                elif abs(A) < 4 - lay:
                    g.put(x, y, z, K.AIR)
    # 前の幕 (入口の両脇を少し閉じる) と、前後の棟の柱
    for A in (-2, 2):
        x, z = at(2, A)
        g.put(x, t + 1, z, wool)
    for L in (3, -4):
        x, z = at(L, 0)
        for y in range(t + 1, t + 5):
            g.put(x, y, z, FENCE)
        g.put(x, t + 5, z, "minecraft:lantern[hanging=false,waterlogged=false]" if L == 3 else FENCE)
    # 張り綱の杭
    for L, A in ((2, -4), (2, 4), (-3, -4), (-3, 4)):
        x, z = at(L, A)
        g.put(x, t + 1, z, FENCE)
    # 中の寝具と荷
    x, z = at(-1, 0)
    g.put(x, t + 1, z, "minecraft:%s_carpet" % wool.split(":")[1].replace("_wool", ""))
    x, z = at(-2, 1)
    g.put(x, t + 1, z, "minecraft:%s_carpet" % wool.split(":")[1].replace("_wool", ""))
    x, z = at(-2, -2)
    g.put(x, t + 1, z, "minecraft:barrel[facing=up,open=false]")
    x, z = at(-2, 2)
    g.put(x, t + 1, z, "minecraft:lantern[hanging=false,waterlogged=false]")
    g.marks["tent_%d" % k] = [x0, t + 1, z0]
    # 前に立つ所 (入口の外)
    sx, sz = at(5, 0)
    g.marks["self_%d" % k] = [sx, (top(sx, sz) or t) + 1, sz]
    return t, (ox, oz)


TENT_INFO = {}
for k, (pos, wool, side) in TENTS.items():
    TENT_INFO[k] = (pos,) + tent(k, pos, wool, side)

# 天幕ごとの持ち物 (その人の手ざわり)
# 谷の私: 星の溜まり (方解石の縁の鉢、底に紫水晶) と星灯りの灯
(px, pz), t, (ox, oz) = TENT_INFO[1]
bx, bz = px - 6, pz - 1
for x in range(bx - 2, bx + 3):
    for z in range(bz - 2, bz + 3):
        d = math.hypot(x - bx, z - bz)
        tt = top(x, z)
        if tt is not None and d < 2.3:
            g.put(x, tt, z, "minecraft:amethyst_block" if d < 1.1 else "minecraft:calcite")
            if d >= 1.6:
                g.put(x, tt + 1, z, "minecraft:calcite")
            clear_above(x, z, tt + (1 if d >= 1.6 else 0), 3)
g.put(bx, top(bx, bz) + 1, bz, "minecraft:small_amethyst_bud[facing=up,waterlogged=false]")
for dz in (-1, 1):
    x, z = px + 5, pz + dz * 2
    tt = top(x, z)
    if tt is not None:
        g.put(x, tt + 1, z, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
        g.put(x, tt + 2, z, "minecraft:soul_lantern[hanging=false,waterlogged=false]")
# 崖の私: 登りの道具 (梯子を立てかけた岩、巻いた鎖、楔)
(px, pz), t, (ox, oz) = TENT_INFO[2]
rx, rz = px - 1, pz - 7
for dy in range(1, 5):
    for dx in (-1, 0, 1):
        g.put(rx + dx, t + dy, rz, "minecraft:tuff" if dy < 4 or dx == 0 else K.AIR)
    if dy < 4:
        g.put(rx, t + dy, rz + 1, "minecraft:ladder[facing=south,waterlogged=false]")
for dx in range(2):
    g.put(px + 2 + dx, t + 1, pz + 6, "minecraft:chain[axis=x,waterlogged=false]")
g.put(px + 1, t + 1, pz + 6, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
# 野の私: 砂時計 (砂の山と硝子の柱) と数えた石の列
(px, pz), t, (ox, oz) = TENT_INFO[3]
hx, hz = px + 1, pz - 4
g.put(hx, t + 1, hz, "minecraft:smooth_sandstone_slab[type=bottom,waterlogged=false]")
g.put(hx, t + 2, hz, "minecraft:glass")
g.put(hx, t + 3, hz, "minecraft:sand")
g.put(hx, t + 4, hz, "minecraft:smooth_sandstone_slab[type=bottom,waterlogged=false]")
for i in range(6):
    x, z = px - 2 + i, pz + 6
    tt = top(x, z)
    if tt is not None and i % 2 == 0:
        g.put(x, tt + 1, z, "minecraft:stone_button[face=floor,facing=north,powered=false]")
# 淵の私: 鏡 (方解石の枠の硝子の板) と紫水晶
(px, pz), t, (ox, oz) = TENT_INFO[4]
mx, mz = px + 4, pz
for dy in range(1, 4):
    g.put(mx, t + dy, mz - 1, "minecraft:calcite")
    g.put(mx, t + dy, mz + 1, "minecraft:calcite")
    g.put(mx, t + dy, mz, "minecraft:glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]")
g.put(mx, t + 4, mz, "minecraft:calcite")
g.put(mx + 1, t + 1, mz + 2, "minecraft:amethyst_cluster[facing=up,waterlogged=false]")

# ---------------------------------------------------------------- 地面に刺さった巡礼者の羽 (白い大きな羽。軸は金、斜めに刺さる)
FEATHERS = [(-22, -575, 0.6, 9), (20, -600, 2.3, 8), (30, -578, 3.6, 7), (-8, -556, 5.0, 6)]
for i, (fx, fz, ang, ln) in enumerate(FEATHERS):
    t = top(fx, fz)
    if t is None:
        continue
    dx, dz = math.cos(ang), math.sin(ang)
    for s in range(ln):
        y = t + 1 + round(s * 0.8)
        x, z = round(fx + dx * s * 0.6), round(fz + dz * s * 0.6)
        g.put(x, y, z, "minecraft:gold_block" if s < ln - 1 else "minecraft:smooth_quartz")
        w = 1 if s < 2 else 2 if s < ln - 2 else 1
        for side in range(1, w + 1):
            for sg in (-1, 1):
                g.put(round(x - dz * side * sg), y, round(z + dx * side * sg),
                      "minecraft:white_wool" if side < w or s % 3 else "minecraft:smooth_quartz")

# 月の足跡 (着く所から焚き火へ。灰の粉の点)
# (着く所は光の橋の着地で決まるので、橋のあとで刻む)

# ---------------------------------------------------------------- 月見の岩 (北の端。方解石と凝灰岩の層。上に主の間)
TX, TZ = CX, -604
tt = max(top(x, z) or G for x in range(TX - 9, TX + 10) for z in range(TZ - 9, TZ + 10) if top(x, z) is not None)
H = 24
TOP = tt + H
# 方解石と閃緑岩の明るい岩に、凝灰岩と安山岩の斑 (縞にしない)。上へ少し傾き、高さごとに張り出しとくびれ
ROCK = ["minecraft:calcite", "minecraft:calcite", "minecraft:polished_diorite", "minecraft:diorite", "minecraft:calcite"]
for y in range(tt - 22, TOP):
    h = (y - tt) / H
    if y >= tt:
        r0 = 10.2 - 2.4 * h + 1.0 * math.sin(h * 7.0) + 1.6 * max(0.0, h - 0.84) / 0.16
    else:
        r0 = 10.2 - 0.36 * (tt - y)
    lean = 0.09 * max(0, y - tt)
    cxx, czz = TX - lean * 0.6, TZ + lean * 0.3
    for x in range(TX - 14, TX + 13):
        for z in range(TZ - 13, TZ + 14):
            a = math.atan2(z - czz, x - cxx)
            r = r0 + 2.2 * (K.noise2(math.cos(a) * 3 + 50, math.sin(a) * 3 + y * 0.05, 1.4, 440) - 0.5)                 + 1.0 * (K.noise2(math.cos(a) * 8, math.sin(a) * 8 + y * 0.2, 1.6, 444) - 0.5)
            d = math.hypot(x - cxx, z - czz)
            if d <= r:
                n = K.noise2(x * 0.7 + y * 0.3, z * 0.7 - y * 0.2, 4.0, 441)
                b = ROCK[int(K.hsh(x, y, z, 442) * len(ROCK))]
                if n > 0.7:
                    b = "minecraft:tuff" if K.hsh(x, y, z, 445) < 0.6 else "minecraft:andesite"
                elif n < 0.18:
                    b = "minecraft:smooth_quartz" if K.hsh(x, y, z, 446) < 0.3 else "minecraft:calcite"
                g.put(x, y, z, b)
# 根の苔と岩の割れ目の草
for x in range(TX - 14, TX + 13):
    for z in range(TZ - 13, TZ + 14):
        for y in range(tt + 1, TOP - 2):
            if g.get(x, y, z) and g.get(x, y, z) != K.AIR and g.get(x, y + 1, z) is None and K.hsh(x, y, z, 443) < 0.45:
                g.put(x, y + 1, z, "minecraft:moss_carpet" if y < tt + 8 or K.hsh(x, z, 447) < 0.5 else "minecraft:short_grass")
# 扉 (南の面の根元。灯の島・炉の島と同じく、触れると主の間へ)
DZ = TZ + 10
while g.get(TX, tt + 1, DZ) is None and DZ > TZ:
    DZ -= 1
DOOR = (TX, tt + 1, DZ)
for z in range(DZ - 1, DZ + 5):
    for x in range(TX - 1, TX + 2):
        if top(x, z) is not None or g.get(x, tt, z) is not None:
            g.put(x, tt, z, "minecraft:polished_tuff")
        for y in range(tt + 1, tt + 4):
            if z > DZ:
                g.put(x, y, z, K.AIR)
for y in range(tt + 1, tt + 4):
    for x in (TX - 1, TX + 1):
        g.put(x, y, DZ, "minecraft:chiseled_tuff")
g.put(TX, tt + 4, DZ, "minecraft:chiseled_tuff_bricks")
g.put(*DOOR, "minecraft:chiseled_quartz_block")
g.put(TX, tt + 2, DZ, "minecraft:quartz_pillar[axis=y]")
g.put(TX, tt + 3, DZ, "minecraft:quartz_pillar[axis=y]")
for x in (TX - 2, TX + 2):
    g.put(x, tt + 1, DZ + 1, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
    g.put(x, tt + 2, DZ + 1, "minecraft:soul_lantern[hanging=false,waterlogged=false]")
g.marks["door"] = list(DOOR)
g.marks["door_front"] = [TX, tt + 1, DZ + 2]

# ---------------------------------------------------------------- 主の間 (月見の岩の上の丸い床。四人の座の石と、北の縁の橋の台)
AX, AZ = TX, TZ
for x in range(AX - 13, AX + 14):
    for z in range(AZ - 13, AZ + 14):
        d = math.hypot(x - AX, z - AZ)
        if d > 12.5:
            continue
        b = "minecraft:smooth_quartz" if d > 3.4 else "minecraft:chiseled_quartz_block"
        if 7.6 < d < 8.4:
            b = "minecraft:calcite"
        elif d > 11.4:
            b = "minecraft:quartz_bricks"
        g.put(x, TOP, z, b)
        g.put(x, TOP - 1, z, "minecraft:calcite")
        if d > 12.0:
            g.put(x, TOP + 1, z, "minecraft:quartz_slab[type=bottom,waterlogged=false]" if K.hsh(x, z, 450) < 0.7 else K.AIR)
        for y in range(TOP + (2 if d > 12 else 1), TOP + 12):
            g.put(x, y, z, K.AIR)
# 床の下の梁 (縁が岩から張り出す所)
for a in range(12):
    ang = a * math.pi / 6
    for r in range(8, 13):
        for y in (TOP - 2, TOP - 3) if r > 10 else (TOP - 2,):
            g.put(round(AX + math.cos(ang) * r), y, round(AZ + math.sin(ang) * r), "minecraft:quartz_pillar[axis=y]")
# 四人の座の石 (半径 8 の四方。前の自分たちが立つと、色の印が灯る)
for i, (dx, dz) in enumerate(((0, 8), (-8, 0), (8, 0), (0, -8))):
    x, z = AX + dx, AZ + dz
    g.put(x, TOP, z, "minecraft:polished_tuff")
    g.put(x, TOP + 1, z, SEAT_MARK[i + 1])
    g.marks["seat_%d" % (i + 1)] = [x, TOP + 1, z]
g.marks["arena"] = [AX, TOP, AZ]
g.marks["lord_spawn"] = [AX, TOP + 1, AZ]
g.marks["arena_entry"] = [AX, TOP + 1, AZ + 5]
g.marks["arena_back"] = [AX - 4, TOP + 1, AZ + 3]
g.put(AX - 4, TOP + 1, AZ + 3, "minecraft:lodestone")
# 北の縁の橋の台 (巡礼者の座へ。作る順 3)
for z in range(AZ - 13, AZ - 16, -1):
    for x in (AX - 1, AX, AX + 1):
        g.put(x, TOP, z, "minecraft:quartz_bricks")
        g.put(x, TOP - 1, z, "minecraft:quartz_pillar[axis=y]")
for x in (AX - 2, AX + 2):
    g.put(x, TOP + 1, AZ - 15, "minecraft:quartz_pillar[axis=y]")
    g.put(x, TOP + 2, AZ - 15, "minecraft:end_rod[facing=up]")
g.marks["seat_bridge"] = [AX, TOP + 1, AZ - 15]

# ---------------------------------------------------------------- 光の橋 (炉の島の主の間の北の縁 → 島 4 の南の端)
z = CZ + RZ + 6
while top(A3_X, z) is None:
    z -= 1
LAND_Z = z
LAND = top(A3_X, LAND_Z)
# 炉の島の主の間の外の輪 (半径 6〜11) は戻り石なので、橋の付け根は縁 (半径 12) の外から
for zz in range(A3_Z - 12, A3_Z - 17, -1):
    for x in (A3_X - 1, A3_X, A3_X + 1):
        g.put(x, A3_TOP, zz, LP)
        for y in range(A3_TOP + 1, A3_TOP + 5):
            g.put(x, y, zz, K.AIR)
g.marks["bridge_gate"] = [A3_X, A3_TOP + 1, A3_Z - 15]
ZS = A3_Z - 17
for zz in range(ZS, LAND_Z, -1):
    t = (ZS - zz) / (ZS - LAND_Z)
    h2 = round((A3_TOP + (LAND - A3_TOP) * t) * 2)
    y, half = h2 // 2, h2 % 2 == 1
    for x in (A3_X - 1, A3_X, A3_X + 1):
        if top(x, zz) is not None and top(x, zz) >= y:
            continue
        g.put(x, y, zz, LP)
        g.put(x, y + 1, zz, LPS if half else K.AIR)
        for yy in range(y + 2, y + 5):
            g.put(x, yy, zz, K.AIR)
    if (ZS - zz) % 9 == 4:
        for x in (A3_X - 2, A3_X + 2):
            g.put(x, y + 1, zz, "minecraft:quartz_pillar[axis=y]")
            g.put(x, y + 2, zz, "minecraft:end_rod[facing=up]")
g.marks["arrive"] = [A3_X, LAND + 1, LAND_Z - 3]
g.marks["center"] = [CX, (top(CX, CZ) or G) + 1, CZ]

# 月の足跡 (着く所から焚き火へ。灰の粉の小さな点が交互に)
ax, az = A3_X, LAND_Z - 3
for s in range(0, int(abs(az - CAMP[1])) - 7, 2):
    z = az - s
    for side in ((-1,) if (s // 2) % 2 else (1,)):
        x = ax + side
        tt = top(x, z)
        if tt is not None and g.get(x, tt, z) in DUST:
            g.put(x, tt, z, "minecraft:gray_concrete_powder")

# ---------------------------------------------------------------- 花と雲のうねり
K.flowers(g, 460, keep=lambda x, z: math.hypot(x - CAMP[0], z - CAMP[1]) < 7)
K.billows(g, 470, 40, lambda x, z: on_main(x, z) or abs(x - A3_X) < 4)
kb, cells, runs = g.write(OUT, "tools/gen/world/build_cloud_isle4.py が書く。手で直さない", {"ground": G, "top": TOP})
print("isle4", kb, "KB", cells, "cells", runs, "runs", "arena top", TOP, "land", LAND, LAND_Z, "door", DOOR)
