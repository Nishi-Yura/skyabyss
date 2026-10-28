"""六章 区画 1「新月の谷」の地形を彫る (docs/design/46 の 4.3・4.3.1 の配置 C 輪の谷)。

  PYTHONIOENCODING=utf-8 python -I tools/gen/world/build_moon_valley.py [--preview]
      -> src/main/resources/data/skyabyss/moon/valley.json   (Java: story/ch6/valley/MoonValley が流し込む)
         docs/review/moon/valley_top.png・valley_cut.png     (--preview: 上から見た色の図と、南北の断面)

座標は月の世界の座標そのまま (着く所 = (0, 12, 0)。月の地面の上面は y 11、北が -z)。谷は着く所の南、z 22〜176。
書くのは「谷の列」だけ (谷のまわり 6 を含む)。列の中は床の下 4 から y 34 まで、空気も含めて全部書く (前に掘ったクレーターを上書きする)。
平らな月の地面の層 (MoonWorld と dimension/moon.json) と同じものは書かない。

形 (上から): 着く所から南へ下る入口の谷 (幅 28) → 真ん中の岩の台地 (主の間が上) をぐるりと回る輪の谷 (外の半径 30・台地の半径 11。南北は 2 倍に伸ばす)。
  - 崖: 下が玄武岩、中が凝灰岩と安山岩、上が月の砂。縦の層、ノイズで張り出しと抉れ。縁の上に砂の尾根
  - 谷底: 月の砂の斑、割れた岩の尖り、転がる岩、壁の根の砂だまり、小さなくぼみ
  - 裂け目: 輪の西を東西に断つ (底は見えない y -52)。真ん中に柱 (影の橋の欠片の台)。影の橋は Java が置く (印 bridge_n0..)
  - 野営: 入口の谷の西の壁の岩陰 (張り出しの屋根)。天幕・寝具と荷・星の溜まり・帰りの台・灯り
  - 鏡の窪: 輪の東。お椀のくぼみ、縁に鏡の石の台 3 (Java が鏡の石を置く)、真ん中に欠片の台
  - 落ちた星の穴: 輪の南。深い穴 (y -24 まで) と、壁に沿って下る螺旋の岩棚。底に落ちた星 (方解石と紫水晶)
  - 奥の門: 台地の北の根元に彫った門 (Java が星の門を置く)。主の間: 台地の上の丸い窪み、まわりに灯りの台 6
"""
from __future__ import annotations

import json
import math
import os
import sys

import numpy as np
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "skyabyss", "moon", "valley.json")
REVIEW = os.path.join(ROOT, "docs", "review", "moon")

X0, X1 = -46, 46
Z0, Z1 = 14, 182
Y0, Y1 = -56, 34
NX, NZ, NY = X1 - X0 + 1, Z1 - Z0 + 1, Y1 - Y0 + 1
SURFACE = 11
MESA_TOP = 22
MESA_Z = 112          # 台地の中心 (z)
RING_OUT = 30.0
RING_IN = 12.5
MESA_R = 11.0

PALETTE = ["minecraft:air"]


def P(name):
    if name not in PALETTE:
        PALETTE.append(name)
    return PALETTE.index(name)


AIR = 0
BEDROCK = P("minecraft:bedrock")
DEEPSLATE = P("minecraft:deepslate")
SBASALT = P("minecraft:smooth_basalt")
BASALT = P("minecraft:basalt[axis=y]")
PBASALT = P("minecraft:polished_basalt[axis=y]")
BLACKSTONE = P("minecraft:blackstone")
TUFF = P("minecraft:tuff")
ANDESITE = P("minecraft:andesite")
CALCITE = P("minecraft:calcite")
POWDER = P("minecraft:light_gray_concrete_powder")
POWDER_G = P("minecraft:gray_concrete_powder")
POWDER_W = P("minecraft:white_concrete_powder")
GRAVEL = P("minecraft:gravel")
COBBLED_DS = P("minecraft:cobbled_deepslate")
DS_TILES = P("minecraft:deepslate_tiles")
POLISHED_DS = P("minecraft:polished_deepslate")
AMETHYST = P("minecraft:amethyst_block")
CLUSTER = P("minecraft:amethyst_cluster[facing=up]")
BUD = P("minecraft:medium_amethyst_bud[facing=up]")
PBLACKSTONE = P("minecraft:polished_blackstone")
PB_BRICKS = P("minecraft:polished_blackstone_bricks")
CH_PB = P("minecraft:chiseled_polished_blackstone")
SQUARTZ = P("minecraft:smooth_quartz")
QUARTZ_BRICKS = P("minecraft:quartz_bricks")
DIORITE_P = P("minecraft:polished_diorite")
TUFF_BRICKS = P("minecraft:tuff_bricks")
POL_TUFF = P("minecraft:polished_tuff")
CH_TUFF = P("minecraft:chiseled_tuff_bricks")


def base_state(y):
    """平らな月の地面 (dimension/moon.json の層)"""
    if y <= -64:
        return BEDROCK
    if y <= -24:
        return DEEPSLATE
    if y <= -4:
        return SBASALT
    if y <= 8:
        return TUFF
    if y <= SURFACE:
        return POWDER
    return AIR


# ---------------------------------------------------------------- ノイズ (決まった種。なめらかな値のノイズを拡大で作る)
RNG = np.random.default_rng(0x4D4F4F4E)


def noise2(scale, seed):
    r = np.random.default_rng(seed)
    gx, gz = NX // scale + 3, NZ // scale + 3
    g = r.random((gx, gz))
    n = ndimage.zoom(g, scale, order=3)[:NX, :NZ]
    return np.clip(n, 0, 1)


def noise3(scale, seed):
    r = np.random.default_rng(seed)
    g = r.random((NX // scale + 3, NY // scale + 3, NZ // scale + 3))
    n = ndimage.zoom(g, scale, order=1)[:NX, :NY, :NZ]
    return n


xs = np.arange(X0, X1 + 1)[:, None].astype(float)
zs = np.arange(Z0, Z1 + 1)[None, :].astype(float)
XX = np.broadcast_to(xs, (NX, NZ))
ZZ = np.broadcast_to(zs, (NX, NZ))


def ring_r(x, z):
    return np.hypot(x, (z - MESA_Z) / 2.0)


N_EDGE = noise2(6, 11) * 2 - 1        # 谷の縁の揺れ
N_FLOOR = noise2(9, 12) * 2 - 1       # 谷底のうねり
N_PATCH = noise2(4, 13)               # 砂の斑
N_RIM = noise2(5, 14)                 # 縁の尾根の高さ
N_WALL = noise3(5, 15) * 2 - 1        # 崖の張り出し
N_STRATA = noise3(7, 16)              # 層のゆらぎ

R = ring_r(XX, ZZ)
# 入口の谷 (着く所の南 22 から、輪の北へ) と輪の谷
corr_half = 13.0 + 2.2 * N_EDGE
corridor = (np.abs(XX) < corr_half) & (ZZ >= 22) & (ZZ <= 72)
ring = (R < RING_OUT + 2.4 * N_EDGE) & (R > RING_IN + 1.0 * N_EDGE)
VALLEY = corridor | ring
# 入口の谷の頭は丸く (着く所の側)
head = (ZZ < 30) & (np.hypot(XX / corr_half, (ZZ - 30) / 8.0) > 1.0)
VALLEY &= ~head
VALLEY = ndimage.binary_opening(VALLEY, iterations=1)

# 谷底の高さ: 入口で y 10 から下り、輪で y -6 (南ほど少し低く)
t = np.clip((ZZ - 24) / 40.0, 0, 1)
t = t * t * (3 - 2 * t)
FLOOR = (10 - 16 * t) - np.clip((ZZ - 90) / 80.0, 0, 1) * 3 + 1.3 * N_FLOOR
FLOOR = np.round(FLOOR).astype(int)

# 谷の中から縁までの距離 (崖を彫る)
D_IN = ndimage.distance_transform_edt(VALLEY)
D_OUT = ndimage.distance_transform_edt(~VALLEY)

# 台地 (主の間)
MESA = R <= MESA_R + 1.0 * N_EDGE

# 書く列: 谷のまわり 7
WRITE = ndimage.binary_dilation(VALLEY | MESA, iterations=7)

# ---------------------------------------------------------------- 立体を組む (vol[x, y, z] = パレットの番号)
vol = np.zeros((NX, NY, NZ), dtype=np.int16)
for yi in range(NY):
    vol[:, yi, :] = base_state(Y0 + yi)

YY = (np.arange(Y0, Y1 + 1)[None, :, None]).astype(float)


def wall_mat(y, x, z, k):
    """崖の材質 (縦の層): 下が玄武岩、中が凝灰岩と安山岩、上が月の砂。層の境はゆらぐ"""
    yy = y + (N_STRATA[x, y - Y0, z] - 0.5) * 3.0
    if yy < -10:
        return SBASALT if (k % 5) else BASALT
    if yy < -3:
        return BASALT if (k % 3) else BLACKSTONE
    if yy < 1:
        return SBASALT
    if yy < 4:
        return TUFF if (k % 4) else ANDESITE
    if yy < 6:
        return ANDESITE
    if yy < 9:
        return TUFF
    return POWDER if (k % 6) else POWDER_G


def hsh(x, y, z, s=0):
    h = (x * 374761393 + y * 668265263 + z * 2147483647 + s * 974634901) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFFFF


# 谷を彫る: 谷の列は床より上を空気に、崖は張り出しのノイズで削る
for xi in range(NX):
    for zi in range(NZ):
        x, z = X0 + xi, Z0 + zi
        if not WRITE[xi, zi]:
            continue
        fl = FLOOR[xi, zi]
        inside = VALLEY[xi, zi]
        d_in, d_out = D_IN[xi, zi], D_OUT[xi, zi]
        for yi in range(NY):
            y = Y0 + yi
            if y > SURFACE + 8:
                break
            if inside:
                if y > fl:
                    # 崖の根元から縁へ: 縁に近い所は張り出しで残す
                    keep = d_in + 1.8 * N_WALL[xi, yi, zi] - (0.6 if y < fl + 3 else 0.0) < 1.0
                    if keep and y <= SURFACE:
                        vol[xi, yi, zi] = wall_mat(y, xi, zi, hsh(x, y, z))
                    else:
                        vol[xi, yi, zi] = AIR
                elif y == fl:
                    n = N_PATCH[xi, zi]
                    # 谷底: 薄灰の砂を基に、灰の砂の流れ・凝灰岩の砕け・礫。白い砂は壁の根の吹き溜まりだけ (下の飾りで盛る)
                    hh = hsh(x, 0, z, 3) % 100
                    vol[xi, yi, zi] = (POWDER_G if n > 0.5 else TUFF if hh < 5 else GRAVEL if hh < 8 else ANDESITE if hh < 10
                                       else COBBLED_DS if hh < 11 else POWDER)
                elif y >= fl - 3:
                    vol[xi, yi, zi] = TUFF if y > -3 else SBASALT
            else:
                # 崖の外 (縁の上): 縁に近い所は外側へ抉れる (崖の面のノイズ)
                if y <= SURFACE and d_out < 3.0:
                    near_floor = FLOOR[max(0, min(NX - 1, xi)), zi]
                    eat = d_out - 1.6 * N_WALL[xi, yi, zi] < 0.6 and y > near_floor + 2 and y < SURFACE - 1
                    vol[xi, yi, zi] = AIR if eat else (wall_mat(y, xi, zi, hsh(x, y, z)) if y < SURFACE - 1 else vol[xi, yi, zi])
                elif y > SURFACE and d_out < 9.0:
                    # 縁の尾根 (砂と凝灰岩の盛り上がり)
                    h = (1 - d_out / 9.0) * (1.5 + 5.0 * N_RIM[xi, zi])
                    if y <= SURFACE + h:
                        top = y + 1 > SURFACE + h
                        vol[xi, yi, zi] = (POWDER_W if N_PATCH[xi, zi] > 0.6 else POWDER) if top else (TUFF if hsh(x, y, z) % 3 else ANDESITE)
                elif y <= SURFACE and d_out >= 3.0 and y > SURFACE - 3:
                    pass

# 台地: 谷底から y 22 へ。上ほど細く、層と張り出し
for xi in range(NX):
    for zi in range(NZ):
        x, z = X0 + xi, Z0 + zi
        r = R[xi, zi]
        if r > MESA_R + 3:
            continue
        fl = FLOOR[xi, zi]
        for y in range(fl, MESA_TOP + 1):
            yi = y - Y0
            k = (y - fl) / max(1, MESA_TOP - fl)
            rad = MESA_R + 1.0 * N_EDGE[xi, zi] - 1.8 * k + 1.4 * N_WALL[xi, yi, zi] + (1.2 if y < fl + 3 else 0)
            if r <= rad:
                if y == MESA_TOP:
                    vol[xi, yi, zi] = POWDER if N_PATCH[xi, zi] > 0.3 else POWDER_G
                elif y >= MESA_TOP - 2:
                    vol[xi, yi, zi] = TUFF
                else:
                    vol[xi, yi, zi] = wall_mat(y - 6, xi, zi, hsh(x, y, z))

# 台地の上の岩の突き出し (縁に沿って 9 つ)
for i in range(9):
    a = i / 9 * 2 * math.pi + 0.3
    cx, cz = math.cos(a) * 9.5, MESA_Z + math.sin(a) * 19.5
    h = 2 + hsh(i, 1, 2) % 4
    for xi in range(NX):
        for zi in range(NZ):
            x, z = X0 + xi, Z0 + zi
            dd = math.hypot(x - cx, (z - cz) / 1.4)
            if dd < 2.2:
                for y in range(MESA_TOP + 1, MESA_TOP + 1 + int(h * (1 - dd / 2.4)) + 1):
                    vol[xi, y - Y0, zi] = TUFF if (y + x) % 3 else ANDESITE


def put(x, y, z, s):
    xi, yi, zi = x - X0, y - Y0, z - Z0
    if 0 <= xi < NX and 0 <= yi < NY and 0 <= zi < NZ:
        vol[xi, yi, zi] = s


def get(x, y, z):
    xi, yi, zi = x - X0, y - Y0, z - Z0
    if 0 <= xi < NX and 0 <= yi < NY and 0 <= zi < NZ:
        return int(vol[xi, yi, zi])
    return base_state(y)


def floor_at(x, z):
    return int(FLOOR[x - X0, z - Z0])


def in_valley(x, z):
    xi, zi = x - X0, z - Z0
    return 0 <= xi < NX and 0 <= zi < NZ and bool(VALLEY[xi, zi]) and not bool(MESA[xi, zi])


MARKS = {}
KEEP = []      # 飾りを置かない所 (x, z, 半径)


def keep(x, z, r):
    KEEP.append((x, z, r))


def kept(x, z, pad=0):
    return any(math.hypot(x - a, z - b) < r + pad for a, b, r in KEEP)


# ---------------------------------------------------------------- 主の間 (台地の上の丸い窪み、灯りの台 6、北の縁に着く所)
AC = (0, MESA_Z)
for x in range(-11, 12):
    for z in range(MESA_Z - 11, MESA_Z + 12):
        d = math.hypot(x - AC[0], z - AC[1])
        if d <= 9.5:
            # 窪み: 床 y 20、縁へ向かって y 21
            fy = 20 if d < 7.5 else 21
            for y in range(fy + 1, MESA_TOP + 6):
                put(x, y, z, AIR)
            ring_k = int(d)
            put(x, fy, z, CH_PB if d < 1.5 else (PB_BRICKS if ring_k in (3, 6) else PBLACKSTONE if (x + z) % 2 else SBASALT))
            for y in range(fy - 3, fy):
                put(x, y, z, BLACKSTONE)
        elif d <= 10.5:
            put(x, MESA_TOP, z, PB_BRICKS)
            for y in range(MESA_TOP + 1, MESA_TOP + 4):
                if get(x, y, z) != AIR and d > 10.0:
                    put(x, y, z, AIR)
MARKS["arena_center"] = [0, 21, MESA_Z]
for i in range(6):
    a = i / 6 * 2 * math.pi + math.pi / 6
    lx, lz = round(math.cos(a) * 8.6), round(MESA_Z + math.sin(a) * 8.6)
    for y in range(21, 23):
        put(lx, y, lz, PB_BRICKS)
    MARKS["lamp_%d" % i] = [lx, 23, lz]
# 台地の上の着く所 (門から渡る先。北の縁)
for x in range(-2, 3):
    for z in range(MESA_Z - 17, MESA_Z - 11):
        put(x, MESA_TOP, z, PB_BRICKS if abs(x) == 2 else PBLACKSTONE)
        for y in range(MESA_TOP + 1, MESA_TOP + 5):
            put(x, y, z, AIR)
MARKS["arena_entry"] = [0, MESA_TOP + 1, MESA_Z - 15]
MARKS["lord_spawn"] = [0, 21, MESA_Z + 3]

# ---------------------------------------------------------------- 奥の門 (台地の北の根元)
gz = MESA_Z - 22
while not (get(0, floor_at(0, gz) + 2, gz) != AIR) and gz < MESA_Z:
    gz += 1
gfl = floor_at(0, gz - 3)
for x in range(-3, 4):
    for z in range(gz - 3, gz + 3):
        for y in range(gfl + 1, gfl + 7):
            if abs(x) <= 1 and y <= gfl + 4 and z >= gz - 3:
                put(x, y, z, AIR)
        put(x, gfl, z, PBLACKSTONE if abs(x) <= 1 else PB_BRICKS)
# 門の枠 (柱と鴨居)
for y in range(gfl + 1, gfl + 6):
    for x in (-2, 2):
        put(x, y, gz - 3, PBASALT if y < gfl + 5 else CH_PB)
for x in range(-2, 3):
    put(x, gfl + 5, gz - 3, CH_PB if x == 0 else PB_BRICKS)
for x in range(-1, 2):
    for y in range(gfl + 1, gfl + 5):
        put(x, y, gz + 2, PB_BRICKS)
MARKS["valley_gate"] = [0, gfl + 1, gz]
keep(0, gz - 4, 6)

# ---------------------------------------------------------------- 裂け目 (輪の西を東西に断つ)
CH_Z = 132
chasm_pts = [(-34, CH_Z - 2), (-24, CH_Z + 1), (-18, CH_Z), (-11, CH_Z + 2), (-4, CH_Z)]
PILLAR = (-18, CH_Z)


def seg_d(px, pz, a, b):
    (x0, z0), (x1, z1) = a, b
    dx, dz = x1 - x0, z1 - z0
    t = max(0.0, min(1.0, ((px - x0) * dx + (pz - z0) * dz) / (dx * dx + dz * dz)))
    return math.hypot(px - (x0 + t * dx), pz - (z0 + t * dz))


for x in range(-40, 0):
    for z in range(CH_Z - 12, CH_Z + 13):
        d = min(seg_d(x, z, a, b) for a, b in zip(chasm_pts, chasm_pts[1:]))
        wid = 3.2 + 2.8 * math.exp(-((x - PILLAR[0]) / 4.0) ** 2) + 0.8 * (hsh(x, 1, z) % 100) / 100
        if d > wid or MESA[x - X0, z - Z0] or D_OUT[x - X0, z - Z0] > 2.5:
            continue
        top = floor_at(x, z) if VALLEY[x - X0, z - Z0] else SURFACE
        # 柱 (真ん中)
        if math.hypot(x - PILLAR[0], z - PILLAR[1]) < 2.3:
            continue
        for y in range(-52, top + 1):
            jag = (hsh(x, y, z, 7) % 100) / 100
            if d < wid - 0.7 * jag:
                put(x, y, z, AIR)
            elif y < top:
                put(x, y, z, BLACKSTONE if y < -20 else BASALT)
# 柱の頂 (欠片の台)
pfl = floor_at(*PILLAR)
for x in range(PILLAR[0] - 2, PILLAR[0] + 3):
    for z in range(PILLAR[1] - 2, PILLAR[1] + 3):
        if math.hypot(x - PILLAR[0], z - PILLAR[1]) < 2.3:
            for y in range(-52, pfl):
                put(x, y, z, BASALT if (y // 3) % 2 else SBASALT)
            put(x, pfl, z, PBLACKSTONE)
            for y in range(pfl + 1, pfl + 5):
                put(x, y, z, AIR)
MARKS["shard_bridge"] = [PILLAR[0], pfl + 1, PILLAR[1]]
# 影の橋の両岸の端 (Java がここから柱へ 3 幅の橋を架ける)
north_end = CH_Z - 7
south_end = CH_Z + 7
while get(PILLAR[0], floor_at(PILLAR[0], north_end) - 1, north_end) == AIR and north_end > CH_Z - 14:
    north_end -= 1
while get(PILLAR[0], floor_at(PILLAR[0], south_end) - 1, south_end) == AIR and south_end < CH_Z + 14:
    south_end += 1
MARKS["bridge_n"] = [PILLAR[0], pfl, north_end]
MARKS["bridge_s"] = [PILLAR[0], pfl, south_end]
MARKS["chasm"] = [PILLAR[0], -40, CH_Z]
keep(PILLAR[0], CH_Z, 12)

# ---------------------------------------------------------------- 鏡の窪 (輪の東)
MH = (21, 90)
mfl = floor_at(*MH)
for x in range(MH[0] - 8, MH[0] + 9):
    for z in range(MH[1] - 8, MH[1] + 9):
        d = math.hypot(x - MH[0], z - MH[1])
        if d <= 6.5 and in_valley(x, z):
            depth = int(round(3 * (1 - (d / 6.5) ** 2)))
            fy = mfl - depth
            for y in range(fy + 1, mfl + 6):
                put(x, y, z, AIR)
            put(x, fy, z, SQUARTZ if d < 1.6 else CALCITE if d < 3.0 and (x + z) % 2 else POWDER_W if d < 4.5 else POWDER)
            for y in range(fy - 2, fy):
                put(x, y, z, CALCITE)
MARKS["shard_mirror"] = [MH[0], mfl - 2, MH[1]]
put(MH[0], mfl - 3, MH[1], QUARTZ_BRICKS)
for i in range(3):
    a = i / 3 * 2 * math.pi - math.pi / 2
    mx, mz = round(MH[0] + math.cos(a) * 5.0), round(MH[1] + math.sin(a) * 5.0)
    fy = mfl - int(round(3 * (1 - (math.hypot(mx - MH[0], mz - MH[1]) / 6.5) ** 2)))
    put(mx, fy, mz, DIORITE_P)
    MARKS["mirror_%d" % i] = [mx, fy + 1, mz]
keep(MH[0], MH[1], 9)

# ---------------------------------------------------------------- 落ちた星の穴 (輪の南)
SH = (0, 158)
sfl = floor_at(*SH)
HOLE_R, HOLE_BOT = 6.5, -24
for x in range(SH[0] - 10, SH[0] + 11):
    for z in range(SH[1] - 10, SH[1] + 11):
        d = math.hypot(x - SH[0], z - SH[1])
        jag = (hsh(x, 2, z) % 100) / 100 * 0.8
        if d <= HOLE_R + jag:
            for y in range(HOLE_BOT + 1, sfl + 6):
                put(x, y, z, AIR)
            put(x, HOLE_BOT, z, CALCITE if d < 2.5 else BLACKSTONE if (x * z) % 3 else BASALT)
        elif d <= HOLE_R + 2.4:
            # 穴の縁の盛り上がり (落ちたときに跳ねた岩)
            up = int(round((HOLE_R + 2.4 - d) * 0.8))
            for y in range(sfl + 1, sfl + 1 + up):
                put(x, y, z, BASALT if (y + z) % 2 else TUFF)
            for y in range(HOLE_BOT, sfl):
                if get(x, y, z) == AIR:
                    put(x, y, z, SBASALT)
# 螺旋の岩棚 (壁に沿って下る。幅 2、一周で 12 下がる)
steps = 140
for i in range(steps):
    a = i / steps * 2.2 * 2 * math.pi
    y = sfl - int(i / steps * (sfl - HOLE_BOT - 1))
    for w in (HOLE_R - 0.4, HOLE_R - 1.4):
        x, z = round(SH[0] + math.cos(a) * w), round(SH[1] + math.sin(a) * w)
        put(x, y, z, PBASALT if i % 9 else TUFF)
        put(x, y + 1, z, AIR)
        put(x, y + 2, z, AIR)
# 落ちた星 (方解石と紫水晶の塊、芽)
for x in range(-3, 4):
    for z in range(SH[1] - 3, SH[1] + 4):
        d = math.hypot(x, z - SH[1])
        if d < 2.2:
            put(x, HOLE_BOT + 1, z, AMETHYST if (x + z) % 2 else CALCITE)
            if d < 1.2:
                put(x, HOLE_BOT + 2, z, AMETHYST)
for x, z in ((2, SH[1] - 2), (-2, SH[1] + 1), (3, SH[1] + 2), (-3, SH[1] - 2), (1, SH[1] + 3)):
    put(x, HOLE_BOT + 1, z, CLUSTER if (x + z) % 2 else BUD)
MARKS["shard_hole"] = [0, HOLE_BOT + 3, SH[1]]
for i in range(4):
    a = i / 4 * 2 * math.pi + 0.4
    MARKS["nest_%d" % i] = [round(math.cos(a) * 9.5), sfl + 1, round(SH[1] + math.sin(a) * 9.5)]
MARKS["hole_bottom"] = [0, HOLE_BOT + 1, SH[1] - 4]
keep(SH[0], SH[1], 11)

# ---------------------------------------------------------------- 野営 (入口の谷の西の壁の岩陰)
CAMP = (-9, 52)
CX, CZ = CAMP
cfl = floor_at(*CAMP)
for x in range(CX - 11, CX + 7):
    for z in range(CZ - 8, CZ + 9):
        d = math.hypot((x - (CX - 4)) / 7.0, (z - CZ) / 6.5)
        if d < 1.0:
            for y in range(cfl + 1, cfl + 6):
                put(x, y, z, AIR)
            put(x, cfl, z, POWDER if (x + z) % 3 else POWDER_G)
            for y in range(cfl - 2, cfl):
                put(x, y, z, TUFF)
            # 張り出しの屋根 (西の半分。崖の中へ掘った岩陰)
            if x < CX - 2:
                for y in range(cfl + 6, cfl + 9):
                    put(x, y, z, TUFF if (y + x) % 3 else ANDESITE)
MARKS["camp"] = [CX, cfl + 1, CZ]
MARKS["past_self"] = [CX - 4, cfl + 1, CZ - 2]
MARKS["return_stone"] = [CX + 3, cfl + 1, CZ - 4]
MARKS["star_pool"] = [CX - 5, cfl + 1, CZ + 3]
# 星の溜まり (方解石の縁の浅い鉢、底に紫水晶)
for x in range(CX - 8, CX - 2):
    for z in range(CZ + 1, CZ + 6):
        d = math.hypot(x - (CX - 5), z - (CZ + 3))
        if d < 2.6:
            put(x, cfl, z, AMETHYST if d < 1.2 else CALCITE)
            if d >= 1.7:
                put(x, cfl + 1, z, CALCITE)
keep(CAMP[0] - 3, CAMP[1], 9)

# ---------------------------------------------------------------- 谷底の飾り (割れた岩の尖り・転がる岩・壁の根の砂だまり・紫水晶の芽)
def free_floor(x, z):
    return in_valley(x, z) and not kept(x, z) and get(x, floor_at(x, z) + 1, z) == AIR and get(x, floor_at(x, z), z) != AIR


def on_path(x, z, pad=0.0):
    """通り道 (入口の谷の真ん中と、輪の谷の真ん中の帯)。尖りと岩は置かない"""
    if z < 70 and abs(x) < 5 + pad:
        return True
    r = math.hypot(x, (z - MESA_Z) / 2.0)
    return z >= 60 and abs(r - 21.0) < 3.5 + pad


keep(0, MARKS["valley_gate"][2] - 8, 9)


rng = np.random.default_rng(7)
# 谷底の小さなくぼみ (半径 2〜4、深さ 1〜2。底は暗い玄武岩、縁は灰の砂で一段盛る)
dimples = 0
for _ in range(400):
    x = int(rng.integers(-34, 35))
    z = int(rng.integers(28, 174))
    if dimples >= 22 or not free_floor(x, z) or D_IN[x - X0, z - Z0] < 4:
        continue
    dimples += 1
    fl = floor_at(x, z)
    r = 2.0 + rng.random() * 2.2
    dep = 1 + int(r > 3.2)
    for dx in range(-6, 7):
        for dz in range(-6, 7):
            if not in_valley(x + dx, z + dz) or kept(x + dx, z + dz):
                continue
            d = math.hypot(dx, dz) / r
            f2 = floor_at(x + dx, z + dz)
            if d <= 1.0:
                dig = max(0, int(round(dep * (1 - d * d))))
                for y in range(f2 - dig + 1, f2 + 1):
                    put(x + dx, y, z + dz, AIR)
                put(x + dx, f2 - dig, z + dz, SBASALT if d < 0.5 else POWDER_G)
            elif d <= 1.4 and get(x + dx, f2 + 1, z + dz) == AIR and (hsh(x + dx, 9, z + dz) % 3):
                put(x + dx, f2 + 1, z + dz, POWDER_G if d < 1.2 else POWDER)

# 割れた岩の尖り: 壁ぎわ (崖から 2〜7) に群れで立てる。群れの真ん中が一番高く、まわりへ低く。通り道には立てない
spire_at = []
for _ in range(2000):
    x = int(rng.integers(-36, 37))
    z = int(rng.integers(26, 176))
    if len(spire_at) >= 11:
        break
    di = D_IN[x - X0, z - Z0] if in_valley(x, z) else 0
    if not free_floor(x, z) or not (2.5 <= di <= 7) or on_path(x, z, 1) or any(math.hypot(x - a, z - b) < 16 for a, b in spire_at):
        continue
    spire_at.append((x, z))
plan = []
for cx, cz in spire_at:
    plan.append((cx, cz, int(rng.integers(9, 15))))
    for _k in range(int(rng.integers(2, 5))):
        a = rng.random() * 6.28
        d = 2.2 + rng.random() * 2.6
        plan.append((int(round(cx + math.cos(a) * d)), int(round(cz + math.sin(a) * d)), int(rng.integers(3, 9))))
spires = 0
for x, z, h in plan:
    if not free_floor(x, z) or on_path(x, z):
        continue
    spires += 1
    fl = floor_at(x, z)
    base_r = 1.0 + h / 9.0
    lean = (rng.random() - 0.5) * 0.25, (rng.random() - 0.5) * 0.25
    for k in range(h):
        r = base_r * (1 - k / h) + 0.35
        cx, cz = x + lean[0] * k, z + lean[1] * k
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if math.hypot(dx + (x - cx), dz + (z - cz)) <= r:
                    m = PBASALT if k > h - 3 else BASALT if (k + dx) % 3 else BLACKSTONE
                    put(x + dx, fl + 1 + k, z + dz, m)
    # 根の割れた石
    for dx, dz in ((1, 1), (-1, 1), (1, -1), (-2, 0)):
        if free_floor(x + dx * 2, z + dz * 2) and rng.random() < 0.5:
            put(x + dx * 2, floor_at(x + dx * 2, z + dz * 2) + 1, z + dz * 2, COBBLED_DS if rng.random() < 0.5 else BLACKSTONE)
boulders = 0
for _ in range(600):
    x = int(rng.integers(-36, 37))
    z = int(rng.integers(26, 176))
    if not free_floor(x, z) or boulders >= 20 or on_path(x, z) or D_IN[x - X0, z - Z0] > 8:
        continue
    boulders += 1
    fl = floor_at(x, z)
    # 転がる岩: 大きな塊に小さな塊を寄せる (立方体に見えないよう、ノイズで削る)。上に月の砂が積もる
    mat = [TUFF, ANDESITE, SBASALT, BASALT][int(rng.integers(0, 4))]
    lumps = [(0.0, 0.0, 2.0 + rng.random() * 1.6, 1.4 + rng.random() * 1.4)]
    for _k in range(int(rng.integers(1, 4))):
        a = rng.random() * 6.28
        lumps.append((math.cos(a) * 2.4, math.sin(a) * 2.4, 1.0 + rng.random() * 1.0, 0.8 + rng.random() * 0.9))
    for ox, oz, rr, ry in lumps:
        for dx in range(-5, 6):
            for dz in range(-5, 6):
                for dy in range(0, 5):
                    px, pz = dx - ox, dz - oz
                    jag = 0.35 * ((hsh(x + dx, dy, z + dz, 5) % 100) / 100 - 0.5)
                    if (px / rr) ** 2 + ((dy - 0.2) / ry) ** 2 + (pz / (rr * 0.85)) ** 2 <= 1.0 + jag:
                        top = (px / rr) ** 2 + ((dy + 0.8) / ry) ** 2 + (pz / (rr * 0.85)) ** 2 > 1.0 + jag
                        put(x + dx, fl + dy, z + dz, (POWDER if mat != BASALT else POWDER_G) if top and dy >= 1 else mat)
# 踏み跡 (通り道の真ん中の幅 3。前のループの自分が何度も歩いた跡。灰の砂と礫で、谷底の薄灰より一段暗い)
for xi in range(NX):
    for zi in range(NZ):
        x, z = X0 + xi, Z0 + zi
        if not in_valley(x, z) or kept(x, z, -3):
            continue
        if z < 70:
            d = abs(x)
        else:
            d = abs(math.hypot(x, (z - MESA_Z) / 2.0) - 21.0) * 1.6
        if z < 24 or d > 1.6 + 0.6 * N_EDGE[xi, zi]:
            continue
        fl = FLOOR[xi, zi]
        if get(x, fl, z) in (POWDER, POWDER_W, POWDER_G, TUFF, ANDESITE, COBBLED_DS) and get(x, fl + 1, z) == AIR:
            put(x, fl, z, GRAVEL if hsh(x, 4, z) % 5 == 0 else POWDER_G)
# 壁の根の砂だまり (崖のすぐ内側の谷底を一段盛る)
for xi in range(NX):
    for zi in range(NZ):
        if VALLEY[xi, zi] and not MESA[xi, zi] and 1.0 <= D_IN[xi, zi] < 2.6 and N_PATCH[xi, zi] > 0.35:
            x, z = X0 + xi, Z0 + zi
            if kept(x, z):
                continue
            fl = FLOOR[xi, zi]
            if get(x, fl + 1, z) == AIR:
                put(x, fl + 1, z, POWDER_W if N_PATCH[xi, zi] > 0.7 else POWDER)
# 紫水晶の芽 (暗い谷の小さな光。壁の根元にまばら)
buds = 0
for _ in range(900):
    x = int(rng.integers(-36, 37))
    z = int(rng.integers(26, 176))
    xi, zi = x - X0, z - Z0
    if buds >= 24 or not in_valley(x, z) or kept(x, z) or D_IN[xi, zi] > 2.5:
        continue
    fl = floor_at(x, z)
    y = fl + 1
    while get(x, y, z) != AIR and y < fl + 4:
        y += 1
    if get(x, y - 1, z) not in (AIR,) and get(x, y, z) == AIR:
        put(x, y, z, BUD)
        buds += 1

# ---------------------------------------------------------------- 書き出し (谷の列だけ。列の中は床の下 4 から y 34 まで全部書く)
def col_low(xi, zi):
    lo = min(FLOOR[xi, zi], SURFACE) - 4
    if any(abs(X0 + xi - a) < 13 and abs(Z0 + zi - b) < 13 for a, b in ((PILLAR[0], CH_Z), SH)):
        lo = -56
    return max(Y0, lo)


runs = []
cols = 0
for yi in range(NY):
    y = Y0 + yi
    for zi in range(NZ):
        row = []
        xi = 0
        while xi < NX:
            if not WRITE[xi, zi] or y < col_low(xi, zi):
                xi += 1
                continue
            x0 = xi
            seq = []
            while xi < NX and WRITE[xi, zi] and y >= col_low(xi, zi):
                s = int(vol[xi, yi, zi])
                if seq and seq[-1][0] == s:
                    seq[-1][1] += 1
                else:
                    seq.append([s, 1])
                xi += 1
            # 平らな地面と同じ段で、空気でない所だけの並びは省いてもよいが、上書きのために全部書く
            flat = [v for p in seq for v in p]
            runs.append([y, Z0 + zi, X0 + x0] + flat)
for xi in range(NX):
    for zi in range(NZ):
        cols += int(WRITE[xi, zi])

data = {"note": "build_moon_valley.py が書く。手で直さない", "palette": PALETTE, "runs": runs, "marks": MARKS,
        "rect": [X0, Z0, X1, Z1], "columns": cols}
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
print("valley", os.path.getsize(OUT) // 1024, "KB", len(runs), "runs", cols, "columns", "spires", spires, "boulders", boulders, "buds", buds)
print("marks", {k: v for k, v in MARKS.items()})

# ---------------------------------------------------------------- 見る絵 (上から・断面)
if "--preview" in sys.argv:
    from PIL import Image
    COL = {AIR: (0, 0, 0), POWDER: (190, 194, 202), POWDER_G: (130, 134, 144), POWDER_W: (226, 228, 232), TUFF: (110, 110, 112),
           ANDESITE: (136, 136, 136), SBASALT: (72, 72, 78), BASALT: (60, 60, 66), PBASALT: (90, 90, 96), BLACKSTONE: (40, 36, 42),
           CALCITE: (220, 224, 220), AMETHYST: (150, 100, 200), CLUSTER: (190, 140, 240), BUD: (190, 140, 240), GRAVEL: (120, 116, 112),
           PBLACKSTONE: (54, 50, 58), PB_BRICKS: (48, 44, 52), CH_PB: (60, 56, 66), SQUARTZ: (236, 230, 222), QUARTZ_BRICKS: (230, 226, 216),
           DIORITE_P: (200, 200, 200), COBBLED_DS: (80, 80, 84), DEEPSLATE: (70, 70, 76)}
    top = np.zeros((NZ, NX, 3), np.uint8)
    for xi in range(NX):
        for zi in range(NZ):
            for yi in range(NY - 1, -1, -1):
                s = int(vol[xi, yi, zi])
                if s != AIR:
                    c = np.array(COL.get(s, (255, 0, 255)), float)
                    shade = 0.45 + 0.55 * (yi / NY)
                    top[zi, xi] = np.clip(c * shade * 1.3, 0, 255)
                    break
    im = Image.fromarray(top).resize((NX * 5, NZ * 5), Image.NEAREST)
    os.makedirs(REVIEW, exist_ok=True)
    im.save(os.path.join(REVIEW, "valley_top.png"))
    cut = np.zeros((NY, NZ, 3), np.uint8)
    xi = -18 - X0
    for yi in range(NY):
        for zi in range(NZ):
            cut[NY - 1 - yi, zi] = COL.get(int(vol[xi, yi, zi]), (255, 0, 255))
    Image.fromarray(cut).resize((NZ * 4, NY * 4), Image.NEAREST).save(os.path.join(REVIEW, "valley_cut.png"))
    print("preview written")
