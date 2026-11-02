# -*- coding: utf-8 -*-
"""六章 区画 3「満月の野」の地形を彫る (docs/design/46 の 4.5)。

  PYTHONIOENCODING=utf-8 python -I tools/gen/world/build_moon_field.py [--preview]
      -> src/main/resources/data/skyabyss/moon/field.json   (Java: story/ch6/field/MoonField が流し込む)
         docs/review/moon/field_top.png                     (--preview: 上から見た色の図)

座標は月の世界の座標そのまま (着く所 = (0, 12, 0)。月の地面の上面は y 11、北が -z)。野は着く所の西、x -236〜-88・z -112〜92。
  - 野: 白い月の砂の広い原 (端は月の面と同じ高さ y 11 に溶ける)。光の草 (エンドロッド)・方解石の岩・石英の碑 (目印)・浅いくぼみ
  - 野営 (東の端): 倒れた石英の碑の陰と、崩れた石の環。Java が天幕・寝具・月の溜まり・帰りの台・灯りを置く
  - 1 満ち引きの池 (北): 半径 30 の深い穴。底は遠い光の溜まり。真ん中の石の柱 (頂に欠片の台) へ、光の石と影の石の飛び石が螺旋に渡る
  - 2 降る光の溝 (南): 東から西へ長さ 96・幅 9・深さ 7 の溝。両側は高い岩の尾根 (縁を歩いて越せない)。北の壁に影の窪 (屋根は影の石)。
       西の端は岩の覆いの下の台に欠片。光は Java (FieldTicker) が溝に降らす
  - 3 影の塔 (西): 前の時代の巡礼の塔の跡 (外の壁は崩れて高さがまちまち)。中の真ん中の柱のまわりに 14 段の螺旋の段
       (下の 7 段は影の石、上の 7 段は光の石)、頂の台に欠片
  - 満月の台 (真ん中): 白い方解石と石英の切り立った台地 (半径 20、上面 y 30)。東の根元に門、上が主の間 (半径 15)。
       まわりに影の柱 6 本 (影の石)
  - 満ち引きの石 (光の石・影の石) は "tide" に並べる (x, y, z, 種類 0 = 光 / 1 = 影)。ブロックは Java が満ち引きに合わせて置く
"""
from __future__ import annotations

import json
import math
import os
import sys

import numpy as np
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "skyabyss", "moon", "field.json")
REVIEW = os.path.join(ROOT, "docs", "review", "moon")

X0, X1 = -236, -88
Z0, Z1 = -112, 92
Y0, Y1 = -34, 66
NX, NZ, NY = X1 - X0 + 1, Z1 - Z0 + 1, Y1 - Y0 + 1
SURFACE = 11

CAMP = (-104, 4)
POND = (-150, -70)
POND_R = 30
PILLAR_R = 3
PILLAR_TOP = SURFACE + 13            # 柱の頂のブロック (y 24。石は 2 ずつ上がって近づく)
GULLY_Z = 60
GULLY_X0, GULLY_X1 = -202, -104      # 西の端 (台) と東の端 (入口の坂)
GULLY_W = 4                           # 溝の真ん中から壁まで (幅 9)
GULLY_FLOOR = 4
TOWER = (-212, -14)
TOWER_R = 9
MESA = (-152, 2)
MESA_R = 20
ARENA_Y = 30
ARENA_R = 15

PALETTE = ["minecraft:air"]


def P(name):
    if name not in PALETTE:
        PALETTE.append(name)
    return PALETTE.index(name)


AIR = 0
POWDER = P("minecraft:light_gray_concrete_powder")
POWDER_W = P("minecraft:white_concrete_powder")
POWDER_G = P("minecraft:gray_concrete_powder")
TUFF = P("minecraft:tuff")
ANDESITE = P("minecraft:andesite")
CALCITE = P("minecraft:calcite")
DIORITE = P("minecraft:diorite")
SBASALT = P("minecraft:smooth_basalt")
BASALT = P("minecraft:basalt[axis=y]")
BLACKSTONE = P("minecraft:blackstone")
GRAVEL = P("minecraft:gravel")
SQUARTZ = P("minecraft:smooth_quartz")
QBRICKS = P("minecraft:quartz_bricks")
QPILLAR = P("minecraft:quartz_pillar[axis=y]")
QPILLAR_X = P("minecraft:quartz_pillar[axis=x]")
QPILLAR_Z = P("minecraft:quartz_pillar[axis=z]")
CH_QUARTZ = P("minecraft:chiseled_quartz_block")
QSLAB = P("minecraft:smooth_quartz_slab[type=bottom]")
POL_TUFF = P("minecraft:polished_tuff")
TUFF_BRICKS = P("minecraft:tuff_bricks")
CH_TUFF = P("minecraft:chiseled_tuff_bricks")
TUFF_WALL = P("minecraft:tuff_wall")
TUFF_SLAB = P("minecraft:tuff_slab[type=bottom]")
PBLACKSTONE = P("minecraft:polished_blackstone")
PB_BRICKS = P("minecraft:polished_blackstone_bricks")
WHITE_CONC = P("minecraft:white_concrete")
SEA_LANTERN = P("minecraft:sea_lantern")
LB_GLASS = P("minecraft:light_blue_stained_glass")
END_ROD = P("minecraft:end_rod[facing=up]")
LANTERN = P("minecraft:soul_lantern[hanging=false]")
PB_WALL = P("minecraft:polished_blackstone_wall")
BUD = P("minecraft:small_amethyst_bud[facing=up]")


def base_state(y):
    """平らな月の地面 (dimension/moon.json の層)"""
    if y <= 8:
        return TUFF
    if y <= SURFACE:
        return POWDER
    return AIR


def hsh(x, y, z, s=0):
    h = (x * 374761393 + y * 668265263 + z * 2147483647 + s * 974634901) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFFFF


def noise2(scale, seed):
    r = np.random.default_rng(seed)
    g = r.random((NX // scale + 3, NZ // scale + 3))
    return np.clip(ndimage.zoom(g, scale, order=3)[:NX, :NZ], 0, 1)


def noise3(scale, seed):
    r = np.random.default_rng(seed)
    g = r.random((NX // scale + 3, NY // scale + 3, NZ // scale + 3))
    return ndimage.zoom(g, scale, order=1)[:NX, :NY, :NZ]


xs = np.arange(X0, X1 + 1)[:, None].astype(float)
zs = np.arange(Z0, Z1 + 1)[None, :].astype(float)
XX = np.broadcast_to(xs, (NX, NZ))
ZZ = np.broadcast_to(zs, (NX, NZ))
N_ROLL = noise2(12, 31)
N_PATCH = noise2(6, 32)
N_RIDGE = noise2(4, 33)
N_WALL = noise3(4, 34) * 2 - 1
N_STRATA = noise3(7, 35)

# 端 (月の面へ溶ける) までの距離
EDGE = np.minimum.reduce([XX - X0, X1 - XX, ZZ - Z0, Z1 - ZZ])
FADE = np.clip((EDGE - 2) / 10.0, 0, 1)

vol = np.zeros((NX, NY, NZ), dtype=np.int16)
for yi in range(NY):
    vol[:, yi, :] = base_state(Y0 + yi)
WRITE = np.ones((NX, NZ), bool)


def put(x, y, z, s):
    xi, yi, zi = x - X0, y - Y0, z - Z0
    if 0 <= xi < NX and 0 <= yi < NY and 0 <= zi < NZ:
        vol[xi, yi, zi] = s


def get(x, y, z):
    xi, yi, zi = x - X0, y - Y0, z - Z0
    if 0 <= xi < NX and 0 <= yi < NY and 0 <= zi < NZ:
        return int(vol[xi, yi, zi])
    return base_state(y)


def top_at(x, z, start=Y1):
    y = start
    while y > Y0 and get(x, y, z) == AIR:
        y -= 1
    return y


MARKS = {}
TIDE = []


def mark(name, x, y, z):
    MARKS[name] = [int(x), int(y), int(z)]


def tide(x, y, z, kind):
    TIDE.append([int(x), int(y), int(z), int(kind)])
    put(x, y, z, AIR)


def rock_mat(y, xi, zi, k, top):
    """岩の材質 (縦の層): 下が玄武岩、上ほど明るい凝灰岩と方解石"""
    yy = y + (N_STRATA[xi, max(0, min(NY - 1, y - Y0)), zi] - 0.5) * 4.0
    if yy < 2:
        return SBASALT if (k % 4) else BASALT
    if yy < 9:
        return TUFF if (k % 4) else ANDESITE
    if yy < 18:
        return TUFF if (k % 3) else DIORITE
    return CALCITE if (k % 5) else DIORITE


# ---------------------------------------------------------------- 野の上面 (なだらかな起伏。白い砂の斑)
ROLL = np.round((N_ROLL - 0.5) * 2.6 * FADE).astype(int)
for xi in range(NX):
    for zi in range(NZ):
        x, z = X0 + xi, Z0 + zi
        t = SURFACE + ROLL[xi, zi]
        for y in range(SURFACE - 3, SURFACE + 3):
            vol[xi, y - Y0, zi] = AIR if y > t else (POWDER if y >= t - 2 else TUFF)
        p = N_PATCH[xi, zi]
        vol[xi, t - Y0, zi] = POWDER_W if p > 0.44 else POWDER if p > 0.1 else POWDER_G

# 浅いくぼみ (野の上に 14。場所の近くは避ける)
rng = np.random.default_rng(0x46494C44)
hollows = 0
for _ in range(200):
    if hollows >= 14:
        break
    cx = int(rng.integers(X0 + 14, X1 - 14))
    cz = int(rng.integers(Z0 + 14, Z1 - 14))
    rad = int(rng.integers(3, 8))
    far = [(POND, POND_R + 10), (MESA, MESA_R + 10), (TOWER, TOWER_R + 10), (CAMP, 18)]
    if any(math.hypot(cx - a, cz - b) < r + rad for (a, b), r in far) or abs(cz - GULLY_Z) < 16 + rad:
        continue
    for dx in range(-rad - 2, rad + 3):
        for dz in range(-rad - 2, rad + 3):
            d = math.hypot(dx, dz) / rad
            x, z = cx + dx, cz + dz
            t = top_at(x, z, SURFACE + 3)
            if d < 1.0:
                depth = int(round((1 - d * d) * rad * 0.35))
                for y in range(t - depth + 1, t + 1):
                    put(x, y, z, AIR)
                put(x, t - depth, z, POWDER_G if d < 0.5 else POWDER)
            elif d < 1.3 and hsh(x, 5, z) % 3 == 0:
                put(x, t + 1, z, POWDER)
    hollows += 1

# ---------------------------------------------------------------- 1 満ち引きの池 (北)
px, pz = POND
for x in range(px - POND_R - 6, px + POND_R + 7):
    for z in range(pz - POND_R - 6, pz + POND_R + 7):
        d = math.hypot(x - px, z - pz)
        if d <= POND_R:
            # 縁から内へ切り立つ穴。底は y -30 の光の溜まり (遠くに光る)
            wall = d > POND_R - 2.5
            for y in range(-30, SURFACE + 4):
                put(x, y, z, AIR)
            put(x, -31, z, SEA_LANTERN if hsh(x, 1, z) % 4 == 0 else LB_GLASS)
            put(x, -32, z, SEA_LANTERN)
            if wall:
                # 壁の内側の張り出し (上ほど少し外へ)
                for y in range(-30, SURFACE + 1):
                    if N_WALL[x - X0, y - Y0, z - Z0] > 0.35 and d > POND_R - 1.2:
                        put(x, y, z, rock_mat(y, x - X0, z - Z0, hsh(x, y, z), SURFACE))
        elif d <= POND_R + 5:
            # 縁の盛り上がり (外へなだらかに)
            h = int(round((1 - (d - POND_R) / 5.0) * 2.4))
            t = SURFACE
            for y in range(t + 1, t + h + 1):
                put(x, y, z, POWDER_W if y == t + h else CALCITE)
# 穴の壁 (縁から下まで、外側の岩)
for x in range(px - POND_R - 2, px + POND_R + 3):
    for z in range(pz - POND_R - 2, pz + POND_R + 3):
        d = math.hypot(x - px, z - pz)
        if POND_R < d <= POND_R + 2:
            for y in range(-30, SURFACE):
                put(x, y, z, rock_mat(y, x - X0, z - Z0, hsh(x, y, z), SURFACE))
# 真ん中の柱 (底から y 12 まで。頂は磨いた凝灰岩、縁に石英)
for x in range(px - PILLAR_R - 1, px + PILLAR_R + 2):
    for z in range(pz - PILLAR_R - 1, pz + PILLAR_R + 2):
        d = math.hypot(x - px, z - pz)
        if d <= PILLAR_R + 0.4:
            for y in range(-30, PILLAR_TOP + 1):
                put(x, y, z, rock_mat(y, x - X0, z - Z0, hsh(x, y, z), SURFACE) if y < PILLAR_TOP else (POL_TUFF if d < PILLAR_R - 0.6 else SQUARTZ))
mark("shard_pond", px, PILLAR_TOP + 1, pz)
# 入口の岩棚 (南の縁から穴へ 4 張り出す)
for x in range(px - 2, px + 3):
    for z in range(pz + POND_R - 4, pz + POND_R + 1):
        for y in range(SURFACE - 3, SURFACE + 2):
            put(x, y, z, rock_mat(y, x - X0, z - Z0, hsh(x, y, z), SURFACE) if y <= SURFACE else POL_TUFF)
mark("pond_entry", px, SURFACE + 2, pz + POND_R - 2)
# 飛び石 (3 x 3、上面 y 12)。入口の岩棚から螺旋に柱へ。光 光 影 影 光 光 (変わり目は一瞬止めでつなぐ)
POND_STONES = []
kinds = [0, 0, 1, 1, 0, 0]
prev = (px, pz + POND_R - 2)
ang = math.pi / 2
for i, k in enumerate(kinds):
    # 南から西を回る螺旋。半径は 23 から 3 ずつ詰め、前の石から 9 (隙間 6) になる角度を探す
    r = (POND_R - 7) - 3.0 * i
    want = 8.5 if i == 0 else 9.0
    a2 = ang
    while math.hypot(px + math.cos(a2) * r - prev[0], pz + math.sin(a2) * r - prev[1]) < want and a2 < ang + math.pi:
        a2 += 0.01
    ang = a2
    cx, cz = round(px + math.cos(ang) * r), round(pz + math.sin(ang) * r)
    prev = (cx, cz)
    POND_STONES.append((cx, cz, k))
    sy = SURFACE + 1 + 2 * i
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            tide(cx + dx, sy, cz + dz, k)
    mark(f"pond_stone_{i}", cx, sy + 1, cz)

# ---------------------------------------------------------------- 2 降る光の溝 (南)
gz = GULLY_Z
for x in range(GULLY_X0 - 8, GULLY_X1 + 14):
    for z in range(gz - GULLY_W - 9, gz + GULLY_W + 10):
        dz = abs(z - gz)
        xi, zi = x - X0, z - Z0
        if not (0 <= xi < NX and 0 <= zi < NZ):
            continue
        inside_x = GULLY_X0 - 4 <= x <= GULLY_X1
        ramp = GULLY_X1 < x <= GULLY_X1 + 12
        if dz <= GULLY_W and (inside_x or ramp):
            # 溝の底 (東の端は坂で野へ上がる)
            floor = GULLY_FLOOR if inside_x else GULLY_FLOOR + int(round((x - GULLY_X1) / 12.0 * (SURFACE - GULLY_FLOOR)))
            for y in range(floor + 1, SURFACE + 4):
                put(x, y, z, AIR)
            for y in range(floor - 3, floor + 1):
                put(x, y, z, GRAVEL if y == floor and hsh(x, 2, z) % 5 == 0 else POWDER_G if y == floor else TUFF)
        elif GULLY_W < dz <= GULLY_W + 5 and GULLY_X0 - 8 <= x <= GULLY_X1 + 2:
            # 両側の尾根 (縁を歩いて越せない高さ。上は尖る)
            hmax = 13 + N_RIDGE[xi, zi] * 7 - (dz - GULLY_W - 1) * 1.6
            if x > GULLY_X1 - 6:
                hmax -= (x - (GULLY_X1 - 6)) * 1.4
            top = SURFACE + int(round(max(0, hmax)))
            for y in range(GULLY_FLOOR - 3, top + 1):
                put(x, y, z, rock_mat(y, xi, zi, hsh(x, y, z), top))
            if dz == GULLY_W + 1:
                for y in range(GULLY_FLOOR + 2, top - 1):
                    if N_WALL[xi, y - Y0, zi] > 0.55:
                        put(x, y, z, AIR)
# 溝の内の壁 (底から縁まで。壁ぎわの列)
for x in range(GULLY_X0 - 4, GULLY_X1 + 1):
    for s in (-1, 1):
        z = gz + s * (GULLY_W + 1)
        for y in range(GULLY_FLOOR + 1, SURFACE + 1):
            if get(x, y, z) == AIR:
                put(x, y, z, rock_mat(y, x - X0, z - Z0, hsh(x, y, z), SURFACE))
# 西の端: 岩の覆い (溝の上を塞ぐ) と、奥の台
for x in range(GULLY_X0 - 8, GULLY_X0 + 6):
    for z in range(gz - GULLY_W - 3, gz + GULLY_W + 4):
        for y in range(GULLY_FLOOR + 6, SURFACE + 16):
            dome = (y - GULLY_FLOOR - 6) < 10 - abs(z - gz) * 0.6
            if dome or x < GULLY_X0 - 3:
                put(x, y, z, rock_mat(y, x - X0, z - Z0, hsh(x, y, z), SURFACE + 16))
for x in range(GULLY_X0 - 3, GULLY_X0 + 2):
    for z in range(gz - 2, gz + 3):
        put(x, GULLY_FLOOR + 1, z, POL_TUFF)
        put(x, GULLY_FLOOR + 2, z, QSLAB if abs(z - gz) == 2 or x in (GULLY_X0 - 3, GULLY_X0 + 1) else POL_TUFF)
for x in range(GULLY_X0 - 2, GULLY_X0 + 1):
    for z in range(gz - 1, gz + 2):
        put(x, GULLY_FLOOR + 2, z, POL_TUFF)
mark("shard_gully", GULLY_X0 - 1, GULLY_FLOOR + 3, gz)
mark("gully_entry", GULLY_X1 + 14, SURFACE + 1, gz)
mark("gully_east", GULLY_X1, GULLY_FLOOR + 1, gz)
mark("gully_west", GULLY_X0 + 4, GULLY_FLOOR + 1, gz)
# 影の窪 (北の壁に 16 ごと。3 の奥行き・4 の幅、屋根は影の石で y 床 + 3)
nook = 0
for x in range(GULLY_X1 - 10, GULLY_X0 + 8, -16):
    for dx in range(-1, 3):
        for dz in range(1, 4):
            z = gz - GULLY_W - dz
            for y in range(GULLY_FLOOR + 1, SURFACE + 22):
                put(x + dx, y, z, AIR)
            put(x + dx, GULLY_FLOOR, z, POWDER_G)
            tide(x + dx, GULLY_FLOOR + 3, z, 1)
        tide(x + dx, GULLY_FLOOR + 3, gz - GULLY_W, 1)
    mark(f"gully_nook_{nook}", x, GULLY_FLOOR + 1, gz - GULLY_W - 2)
    nook += 1

# ---------------------------------------------------------------- 3 影の塔 (西)
tx, tz = TOWER
for x in range(tx - TOWER_R - 3, tx + TOWER_R + 4):
    for z in range(tz - TOWER_R - 3, tz + TOWER_R + 4):
        d = math.hypot(x - tx, z - tz)
        ang = (math.degrees(math.atan2(z - tz, x - tx)) + 360) % 360
        if d <= TOWER_R - 1:
            # 中: 床 (磨いた凝灰岩の輪の模様) と、上は空
            put(x, SURFACE, z, POL_TUFF if int(d) % 3 else TUFF_BRICKS)
            for y in range(SURFACE + 1, Y1 + 1):
                put(x, y, z, AIR)
        elif d <= TOWER_R + 0.6:
            # 外の壁 (崩れた高さ。東に入口、壁に石英の帯)
            h = 16 + int(28 * (0.5 + 0.5 * math.sin(math.radians(ang * 2 + 40))) * (0.6 + 0.4 * N_RIDGE[x - X0, z - Z0]))
            door = abs(((ang + 180) % 360) - 180) < 9 and 0 < x - tx
            for y in range(SURFACE - 2, SURFACE + h):
                if door and y <= SURFACE + 4:
                    put(x, y, z, AIR)
                    continue
                band = (y - SURFACE) % 9 == 0
                put(x, y, z, SQUARTZ if band else (TUFF_BRICKS if hsh(x, y, z) % 5 else CH_TUFF if hsh(x, y, z) % 3 == 0 else POL_TUFF))
            # 崩れた上の縁は凸凹
            if hsh(x, 9, z) % 3 == 0:
                put(x, SURFACE + h, z, TUFF_WALL)
        elif d <= TOWER_R + 3 and hsh(x, 8, z) % 4 == 0:
            # 崩れて落ちた石
            t = top_at(x, z, SURFACE + 3)
            put(x, t + 1, z, TUFF_BRICKS if hsh(x, 7, z) % 2 else TUFF_SLAB)
# 真ん中の柱 (半径 2、頂の台の下まで) と頂の台 (半径 4)
STEP_N = 14
TOP_Y = SURFACE + 3 * STEP_N + 3
for x in range(tx - 5, tx + 6):
    for z in range(tz - 5, tz + 6):
        d = math.hypot(x - tx, z - tz)
        if d <= 2.2:
            for y in range(SURFACE + 1, TOP_Y):
                put(x, y, z, QPILLAR if (y - SURFACE) % 6 else SQUARTZ)
        if d <= 4.2:
            put(x, TOP_Y, z, CH_QUARTZ if d < 1 else QBRICKS if d < 3.2 else SQUARTZ)
mark("shard_tower", tx, TOP_Y + 1, tz)
mark("tower_entry", tx + TOWER_R - 2, SURFACE + 1, tz)
# 螺旋の段 (2 x 2。下の 7 段は影の石、上の 7 段は光の石。段ごとに +3、48 度ずつ)
for i in range(STEP_N):
    a = math.radians(-10 + i * 48)
    cx, cz = tx + math.cos(a) * 5.6, tz + math.sin(a) * 5.6
    y = SURFACE + 3 * (i + 1)
    k = 1 if i < STEP_N // 2 else 0
    x0, z0 = int(math.floor(cx - 0.5)), int(math.floor(cz - 0.5))
    for dx in (0, 1):
        for dz in (0, 1):
            tide(x0 + dx, y, z0 + dz, k)
    mark(f"tower_step_{i}", x0, y + 1, z0)

# ---------------------------------------------------------------- 満月の台 (真ん中の台地) と主の間
mx, mz = MESA
for x in range(mx - MESA_R - 6, mx + MESA_R + 7):
    for z in range(mz - MESA_R - 6, mz + MESA_R + 7):
        d = math.hypot(x - mx, z - mz)
        ang = math.atan2(z - mz, x - mx)
        edge = MESA_R + 1.4 * math.sin(ang * 5 + 0.7) + 0.9 * math.sin(ang * 11)
        xi, zi = x - X0, z - Z0
        if d <= edge:
            for y in range(SURFACE - 2, ARENA_Y):
                # 根元は少し広く (裾)、上は切り立つ
                if d <= edge - max(0, (y - SURFACE) - 14) * 0.15:
                    put(x, y, z, CALCITE if (y + hsh(x, 0, z) % 3) % 7 else (DIORITE if hsh(x, y, z) % 2 else SQUARTZ))
            # 面の抉れ (縁の近くだけ)
            if d > edge - 1.6:
                for y in range(SURFACE + 3, ARENA_Y - 3):
                    if N_WALL[xi, y - Y0, zi] > 0.6:
                        put(x, y, z, AIR)
        elif d <= edge + 5:
            # 裾の崩れた岩 (台地の縁を跳んで登れないよう、低いまま)
            if hsh(x, 4, z) % 6 == 0:
                put(x, SURFACE + 1, z, CALCITE if hsh(x, 5, z) % 2 else DIORITE)
# 主の間の床 (半径 15、y 30)。石英の煉瓦の輪と満月の円、縁は磨いた石英
for x in range(mx - ARENA_R - 4, mx + ARENA_R + 5):
    for z in range(mz - ARENA_R - 4, mz + ARENA_R + 5):
        d = math.hypot(x - mx, z - mz)
        if d <= ARENA_R + 3.5:
            for y in range(ARENA_Y - 2, ARENA_Y):
                put(x, y, z, CALCITE)
            if d < 4:
                put(x, ARENA_Y, z, WHITE_CONC if d < 3 else CH_QUARTZ)
            elif d < ARENA_R:
                put(x, ARENA_Y, z, QBRICKS if int(d) % 4 else SQUARTZ)
            elif d <= ARENA_R + 1.5:
                put(x, ARENA_Y, z, PB_BRICKS)
            else:
                put(x, ARENA_Y, z, SQUARTZ)
                if hsh(x, 3, z) % 5 == 0:
                    put(x, ARENA_Y + 1, z, QSLAB)
            for y in range(ARENA_Y + 1, ARENA_Y + 12):
                if get(x, y, z) not in (QSLAB,):
                    put(x, y, z, AIR)
mark("arena_center", mx, ARENA_Y + 1, mz)
mark("lord_spawn", mx - 6, ARENA_Y + 1, mz)
mark("arena_entry", mx + ARENA_R - 2, ARENA_Y + 1, mz)
mark("arena_back", mx + ARENA_R + 2, ARENA_Y + 1, mz)
# 影の柱 6 本 (半径 10、2 x 2、高さ 5。影の石)
for i in range(6):
    a = math.radians(30 + i * 60)
    cx, cz = mx + math.cos(a) * 10, mz + math.sin(a) * 10
    x0, z0 = int(math.floor(cx - 0.5)), int(math.floor(cz - 0.5))
    for dx in (0, 1):
        for dz in (0, 1):
            put(x0 + dx, ARENA_Y, z0 + dz, PBLACKSTONE)
            for y in range(ARENA_Y + 1, ARENA_Y + 6):
                tide(x0 + dx, y, z0 + dz, 1)
    mark(f"shade_pillar_{i}", x0, ARENA_Y + 1, z0)
# 門 (東の根元に彫る。磨いた黒石の枠。Java が星の門を 3 x 4 置く)
gx = mx + MESA_R - 1
for dz in range(-3, 4):
    for y in range(SURFACE + 1, SURFACE + 7):
        frame = abs(dz) == 3 or y == SURFACE + 6
        for x in range(gx - 2, gx + 3):
            put(x, y, mz + dz, PB_BRICKS if frame else AIR)
for x in range(gx - 2, gx + 4):
    for dz in range(-2, 3):
        put(x, SURFACE, mz + dz, PBLACKSTONE)
mark("field_gate", gx - 1, SURFACE + 1, mz)
mark("gate_front", gx + 4, SURFACE + 1, mz)

# ---------------------------------------------------------------- 野営 (東の端。倒れた石英の碑の陰と崩れた石の環)
cx, cz = CAMP
mark("camp", cx, SURFACE + 1, cz)
mark("past_self", cx - 3, SURFACE + 1, cz + 3)
mark("star_pool", cx + 3, SURFACE, cz + 4)
mark("return_stone", cx + 5, SURFACE + 1, cz - 2)
# 地面: 踏み固めた砂
for x in range(cx - 9, cx + 10):
    for z in range(cz - 9, cz + 10):
        if math.hypot(x - cx, z - cz) <= 8.5:
            t = top_at(x, z, SURFACE + 3)
            for y in range(t + 1, SURFACE + 4):
                put(x, y, z, AIR)
            for y in range(SURFACE - 1, SURFACE + 1):
                put(x, y, z, POWDER)
            put(x, SURFACE, z, GRAVEL if hsh(x, 1, z) % 6 == 0 else POWDER_G if hsh(x, 2, z) % 3 == 0 else POWDER)
# 倒れた碑 (北の側に斜めに寝た石英の柱。片端が地面、片端が石の環の石に掛かる = 張り出しの屋根)
for i in range(16):
    x = cx - 8 + i
    lift = int(round(i * 0.32))
    for dz in (-6, -5, -4):
        for dy in (0, 1):
            put(x, SURFACE + 1 + lift + dy, cz + dz, QPILLAR_X if dy == 0 else SQUARTZ)
for x in range(cx - 8, cx + 8):
    put(x, SURFACE + 1 + int(round((x - cx + 8) * 0.32)) + 2, cz - 3, QSLAB)
# 崩れた石の環 (半径 11 に 7 本。高さはまちまち、二本に横石)
for i in range(7):
    a = math.radians(200 + i * 40)
    sx, sz = round(cx + math.cos(a) * 11), round(cz + math.sin(a) * 11)
    h = 2 + hsh(sx, 1, sz) % 5
    for y in range(SURFACE + 1, SURFACE + 1 + h):
        for dx in (0, 1):
            put(sx + dx, y, sz, QPILLAR if y < SURFACE + h else CH_QUARTZ)
# 月の溜まりの縁 (石英の板の輪)
spx, spy, spz = MARKS["star_pool"]
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if abs(dx) == 2 or abs(dz) == 2:
            put(spx + dx, SURFACE + 1, spz + dz, QSLAB)
        else:
            put(spx + dx, SURFACE, spz + dz, CALCITE)

# ---------------------------------------------------------------- 踏み跡 (野営から各所へ。灰の砂と礫) と道しるべ
ROUTES = {
    "arrival": [(-88, 2), CAMP],
    "gate": [CAMP, (MESA[0] + MESA_R + 6, MESA[1])],
    "pond": [CAMP, (-118, -26), (POND[0], POND[1] + POND_R + 6)],
    "gully": [CAMP, (-100, 34), (GULLY_X1 + 14, GULLY_Z)],
    "tower": [(MESA[0], MESA[1] - MESA_R - 8), (-186, -18), (TOWER[0] + TOWER_R + 4, TOWER[1])],
    "ring": [(MESA[0] + MESA_R + 6, MESA[1]), (MESA[0] + 14, MESA[1] - MESA_R - 6), (MESA[0], MESA[1] - MESA_R - 8)],
}
posts = 0
for name, pts in ROUTES.items():
    run = 0.0
    for (ax, az), (bx, bz) in zip(pts, pts[1:]):
        L = math.hypot(bx - ax, bz - az)
        n = int(L)
        for i in range(n + 1):
            t = i / max(1, n)
            x = ax + (bx - ax) * t + math.sin(i * 0.21 + len(name)) * 1.6
            z = az + (bz - az) * t + math.cos(i * 0.17 + len(name)) * 1.6
            run += 1
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    xx, zz = int(round(x)) + dx, int(round(z)) + dz
                    if abs(dx) + abs(dz) == 2 and hsh(xx, 6, zz) % 2:
                        continue
                    t0 = top_at(xx, zz, SURFACE + 3)
                    if SURFACE - 2 <= t0 <= SURFACE + 2 and get(xx, t0, zz) in (POWDER, POWDER_W, POWDER_G):
                        put(xx, t0, zz, GRAVEL if hsh(xx, 3, zz) % 4 == 0 else POWDER_G)
            if int(run) % 26 == 13:
                lx, lz = int(round(x + 2.5)), int(round(z + 2.5))
                t0 = top_at(lx, lz, SURFACE + 3)
                if SURFACE - 2 <= t0 <= SURFACE + 2 and get(lx, t0 + 1, lz) == AIR:
                    put(lx, t0 + 1, lz, PB_WALL)
                    put(lx, t0 + 2, lz, LANTERN)
                    posts += 1

# ---------------------------------------------------------------- 目印の石英の碑 (野に 5。折れた柱)
monos = [(-122, -40), (-186, 30), (-196, -60), (-124, 40), (-174, -96)]
for i, (cx, cz) in enumerate(monos):
    h = 8 + hsh(cx, 1, cz) % 7
    lean = 0.12 if i % 2 else -0.1
    for y in range(SURFACE - 1, SURFACE + h):
        ox = int(round((y - SURFACE) * lean))
        for dx in (0, 1):
            for dz in (0, 1):
                put(cx + ox + dx, y, cz + dz, QPILLAR if (y - SURFACE) % 5 else CH_QUARTZ)
    # 折れた上の欠片が足もとに
    for j in range(3):
        put(cx + 2 + j, SURFACE + 1, cz + 2, QPILLAR_X)
    mark(f"monolith_{i}", cx, SURFACE + 1, cz)

# ---------------------------------------------------------------- 飾り: 光の草 (エンドロッドの群れ)・方解石の岩・紫水晶の芽
rng = np.random.default_rng(0x4C494748)
grass = rocks = buds = 0


def busy(x, z):
    """場所のまわりと踏み跡には置かない"""
    if math.hypot(x - CAMP[0], z - CAMP[1]) < 14 or math.hypot(x - POND[0], z - POND[1]) < POND_R + 6:
        return True
    if math.hypot(x - MESA[0], z - MESA[1]) < MESA_R + 7 or math.hypot(x - TOWER[0], z - TOWER[1]) < TOWER_R + 5:
        return True
    if abs(z - GULLY_Z) < GULLY_W + 12 and GULLY_X0 - 12 < x < GULLY_X1 + 16:
        return True
    return get(x, top_at(x, z, SURFACE + 3), z) in (GRAVEL, POWDER_G)


for _ in range(4000):
    if grass >= 70:
        break
    x, z = int(rng.integers(X0 + 6, X1 - 6)), int(rng.integers(Z0 + 6, Z1 - 6))
    if busy(x, z):
        continue
    n = 2 + int(rng.integers(0, 4))
    for _ in range(n):
        xx, zz = x + int(rng.integers(-2, 3)), z + int(rng.integers(-2, 3))
        t = top_at(xx, zz, SURFACE + 3)
        if get(xx, t + 1, zz) == AIR and SURFACE - 2 <= t <= SURFACE + 2:
            put(xx, t + 1, zz, END_ROD)
    grass += 1
for _ in range(3000):
    if rocks >= 30:
        break
    x, z = int(rng.integers(X0 + 6, X1 - 6)), int(rng.integers(Z0 + 6, Z1 - 6))
    if busy(x, z):
        continue
    t = top_at(x, z, SURFACE + 3)
    rr = 1.0 + rng.random() * 1.6
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(0, 3):
                if (dx / rr) ** 2 + (dz / rr) ** 2 + ((dy - 0.3) / (rr * 0.75)) ** 2 <= 1.0 and get(x + dx, t + 1 + dy, z + dz) == AIR:
                    put(x + dx, t + 1 + dy, z + dz, CALCITE if (dx + dy + dz) % 3 else DIORITE)
    rocks += 1
for _ in range(3000):
    if buds >= 24:
        break
    x, z = int(rng.integers(X0 + 6, X1 - 6)), int(rng.integers(Z0 + 6, Z1 - 6))
    if busy(x, z):
        continue
    t = top_at(x, z, SURFACE + 3)
    if get(x, t + 1, z) == AIR and get(x, t, z) != AIR:
        put(x, t + 1, z, BUD)
        buds += 1

# ---------------------------------------------------------------- 作り込みの二回目 (docs/design/46 の 4.5.3)
# 溝の壁: まっすぐな面をやめ、層の筋 (5 ごとに 1 奥へ抉る帯)・縦の割れ目・根の崩れた石。影の窪のまわりはそのまま
nook_x = [x for x in range(GULLY_X1 - 10, GULLY_X0 + 8, -16)]
grooves = rubble = 0
for x in range(GULLY_X0 + 6, GULLY_X1 - 2):
    if any(-3 <= x - nx <= 4 for nx in nook_x):
        continue
    for sgn in (-1, 1):
        wz = GULLY_Z + sgn * (GULLY_W + 1)
        crack = hsh(x, 11, wz) % 9 == 0
        for y in range(GULLY_FLOOR + 2, SURFACE + 1):
            band = (y - GULLY_FLOOR) % 5 == 3 and hsh(x // 3, y, wz) % 3 != 0
            if (band or (crack and y < SURFACE - 1)) and get(x, y, wz) != AIR and get(x, y, wz + sgn) != AIR:
                put(x, y, wz, AIR)
                put(x, y - 1, wz, SBASALT if band else get(x, y - 1, wz))
                grooves += 1
        # 根の崩れた石 (溝の縁の 1 列に、ところどころ 1〜2 段)
        fz = GULLY_Z + sgn * GULLY_W
        if hsh(x, 12, fz) % 4 == 0:
            for y in range(GULLY_FLOOR + 1, GULLY_FLOOR + 2 + hsh(x, 13, fz) % 2):
                put(x, y, fz, TUFF if hsh(x, y, fz) % 3 else GRAVEL)
            rubble += 1

# 影の塔: 外の控え壁 6 (入口の東はあける)、窓 (淡い青の硝子。通り抜けられない)、崩れた縁から突き出す梁
tx, tz = TOWER
butt = 0
for k in range(6):
    ang = 30 + k * 60
    if abs(((ang + 180) % 360) - 180) < 40:
        continue
    ca, sa = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    hb = 14 + hsh(k, 1, 7) % 8
    for j in range(hb):
        reach = 2.6 * (1 - j / hb) + 0.6
        for r in np.arange(TOWER_R + 0.6, TOWER_R + 0.6 + reach, 0.5):
            for w in (-0.5, 0.5):
                x, z = round(tx + ca * r - sa * w), round(tz + sa * r + ca * w)
                put(x, SURFACE + 1 + j, z, TUFF_BRICKS if j % 4 else POL_TUFF)
    butt += 1
windows = 0
for k in range(8):
    ang = 22.5 + k * 45
    if abs(((ang + 180) % 360) - 180) < 30:
        continue
    ca, sa = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    x, z = round(tx + ca * TOWER_R), round(tz + sa * TOWER_R)
    for y0 in (SURFACE + 9, SURFACE + 21):
        if get(x, y0 + 3, z) == AIR:
            continue
        for y in range(y0, y0 + 3):
            put(x, y, z, LB_GLASS)
        put(x, y0 + 3, z, SQUARTZ)
        windows += 1
for k in range(5):
    ang = 50 + k * 70
    ca, sa = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    x, z = round(tx + ca * TOWER_R), round(tz + sa * TOWER_R)
    t = top_at(x, z)
    if t <= SURFACE + 10:
        continue
    for r in range(1, 3):
        put(round(tx + ca * (TOWER_R + r)), t - 1, round(tz + sa * (TOWER_R + r)), QPILLAR_X if abs(ca) > abs(sa) else QPILLAR_Z)

# 野の小さなクレーター (縁は灰の砂、底は暗い玄武岩)。場所と踏み跡は避ける
craters = 0
for _ in range(2000):
    if craters >= 14:
        break
    x, z = int(rng.integers(X0 + 10, X1 - 10)), int(rng.integers(Z0 + 10, Z1 - 10))
    r = 2.5 + rng.random() * 3.0
    if busy(x, z) or any(busy(x + dx, z + dz) for dx, dz in ((6, 0), (-6, 0), (0, 6), (0, -6))):
        continue
    t0 = top_at(x, z, SURFACE + 3)
    if t0 != SURFACE:
        continue
    for dx in range(-8, 9):
        for dz in range(-8, 9):
            d = math.hypot(dx, dz) / r
            xx, zz = x + dx, z + dz
            if d <= 1.0:
                dep = int(round(2 * (1 - d * d)))
                for y in range(SURFACE - dep + 1, SURFACE + 1):
                    put(xx, y, zz, AIR)
                put(xx, SURFACE - dep, zz, SBASALT if d < 0.45 else POWDER_G)
            elif d <= 1.45 and get(xx, SURFACE + 1, zz) == AIR and hsh(xx, 14, zz) % 3:
                put(xx, SURFACE + 1, zz, POWDER_G if d < 1.25 else POWDER)
    craters += 1
print("pass2 grooves", grooves, "rubble", rubble, "buttresses", butt, "windows", windows, "craters", craters)

# ---------------------------------------------------------------- 書き出し (野の列は全部。y 8 から上と、池・溝・塔の深い所)
runs = []
LOW = -34
for yi in range(NY):
    y = Y0 + yi
    if y < LOW:
        continue
    for zi in range(NZ):
        seq = []
        for xi in range(NX):
            s = int(vol[xi, yi, zi])
            if seq and seq[-1][0] == s:
                seq[-1][1] += 1
            else:
                seq.append([s, 1])
        # 月の地面と同じ列 (y 8 より下の凝灰岩・上の空気) は流し込まない
        if len(seq) == 1 and seq[0][0] == base_state(y):
            continue
        runs.append([y, Z0 + zi, X0] + [v for p in seq for v in p])

data = {"note": "build_moon_field.py が書く。手で直さない", "palette": PALETTE, "runs": runs, "marks": MARKS, "tide": TIDE,
        "rect": [X0, Z0, X1, Z1]}
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
print("field", os.path.getsize(OUT) // 1024, "KB", len(runs), "runs", "tide", len(TIDE), "hollows", hollows, "grass", grass, "rocks", rocks,
      "buds", buds, "posts", posts)
for i in range(len(POND_STONES) - 1):
    a, b = POND_STONES[i], POND_STONES[i + 1]
    print("pond step", i, round(math.hypot(a[0] - b[0], a[1] - b[1]), 1))
print("pond first from entry", round(math.hypot(POND_STONES[0][0] - POND[0], POND_STONES[0][1] - (POND[1] + POND_R - 2)), 1),
      "last to pillar", round(math.hypot(POND_STONES[-1][0] - POND[0], POND_STONES[-1][1] - POND[1]), 1))

# ---------------------------------------------------------------- 見る絵
if "--preview" in sys.argv:
    from PIL import Image, ImageDraw
    COL = {POWDER: (190, 194, 202), POWDER_W: (230, 232, 236), POWDER_G: (140, 144, 152), TUFF: (110, 110, 112), ANDESITE: (136, 136, 136),
           CALCITE: (220, 224, 220), DIORITE: (200, 200, 200), SBASALT: (72, 72, 78), BASALT: (60, 60, 66), BLACKSTONE: (40, 36, 42),
           GRAVEL: (120, 116, 112), SQUARTZ: (236, 230, 222), QBRICKS: (232, 226, 218), QPILLAR: (240, 236, 228), QPILLAR_X: (240, 236, 228),
           QPILLAR_Z: (240, 236, 228), CH_QUARTZ: (226, 220, 210), QSLAB: (236, 230, 222), POL_TUFF: (120, 124, 120), TUFF_BRICKS: (104, 108, 104),
           CH_TUFF: (96, 100, 96), TUFF_WALL: (104, 108, 104), TUFF_SLAB: (110, 110, 112), PBLACKSTONE: (54, 50, 58), PB_BRICKS: (48, 44, 52),
           WHITE_CONC: (250, 250, 250), SEA_LANTERN: (180, 220, 230), LB_GLASS: (120, 170, 220), END_ROD: (255, 255, 200), LANTERN: (120, 220, 255),
           PB_WALL: (54, 50, 58), BUD: (190, 140, 240)}
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
    im = Image.fromarray(top).resize((NX * 4, NZ * 4), Image.NEAREST)
    d = ImageDraw.Draw(im)
    for x, y, z, k in TIDE:
        px_, pz_ = (x - X0) * 4, (z - Z0) * 4
        d.rectangle((px_, pz_, px_ + 3, pz_ + 3), fill=(255, 240, 120) if k == 0 else (60, 40, 120))
    for k, (mx_, my_, mz_) in MARKS.items():
        if k.startswith(("shard", "camp", "field_gate", "arena_center", "pond_entry", "gully_entry", "tower_entry", "monolith")):
            px_, pz_ = (mx_ - X0) * 4 + 2, (mz_ - Z0) * 4 + 2
            d.ellipse((px_ - 4, pz_ - 4, px_ + 4, pz_ + 4), outline=(255, 120, 80), width=2)
            d.text((px_ + 6, pz_ - 6), k, fill=(255, 220, 120))
    os.makedirs(REVIEW, exist_ok=True)
    im.save(os.path.join(REVIEW, "field_top.png"))
    print("preview written")
