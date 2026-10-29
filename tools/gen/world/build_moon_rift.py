# -*- coding: utf-8 -*-
"""六章 区画 4「下弦の淵」の地形を彫る (docs/design/46 の 4.6)。

  PYTHONIOENCODING=utf-8 python -I tools/gen/world/build_moon_rift.py [--preview]
      -> src/main/resources/data/skyabyss/moon/rift.json   (Java: story/ch6/rift/MoonRift が流し込む)
         docs/review/moon/rift_top.png                     (--preview: 上から見た色の図)

座標は月の世界の座標そのまま (着く所 = (0, 12, 0)。月の地面の上面は y 11、北が -z)。淵は着く所の南東、x 52〜250・z 116〜316。
  - 淵: 中心 (150, 216) のまわり、半径 48〜92 の弧の帯が深い淵 (底 y -40 の闇)。弧は東を回って北西 (-150°) から南西 (150°) まで。
       西の口 (帯のない 60°) は内の台地と外の月の面がつながる。内の台地は暗い月の砂と玄武岩 (下弦の欠けた側)
  - 野営 (内の台地の西): 前の自分 (四人目)・帰りの台・月の溜まり・寝床と荷・灯り・練習の鏡と残像の板。Java が物を置く
  - 1 二つの板の門 (北): 内の縁の岩山に掘った格子の門。門の前の板 A、縁から淵の上へ北へ伸びる細い棚の先の板 B (A から 18)、鏡
  - 2 残像の渡り (南東): 淵の帯の真ん中に岩の柱の島 (上面 y 20・半径 3.5)。内の縁からも外の縁からも 18 離れる。縁に鏡
  - 3 灯りの回廊 (南西): 内の壁を下る段から、淵の中の y -12 の棚 (屋根の張り出しの下の暗い回廊) が弧に沿って西へ。
       棚の切れ目 3 つを影の橋 (1 幅) が渡り、脇に残り影の溜まり 4。奥の窪に欠片
  - 主の間 (東): 帯の真ん中の柱の上の丸い床 (中心 (220, 216)・半径 15・床 y 0)。東の三分の一の外周は欠けている。
       内の縁に門 (星の門 3 × 4)、門から床へ下りる石の段の橋
  - "shadow" に影の橋の列 (x, y, z)、"pools" は印に。ブロックは Java が置く
"""
from __future__ import annotations

import json
import math
import os
import sys

import numpy as np
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "skyabyss", "moon", "rift.json")
REVIEW = os.path.join(ROOT, "docs", "review", "moon")

X0, X1 = 52, 250
Z0, Z1 = 116, 316
Y0, Y1 = -44, 40
NX, NZ, NY = X1 - X0 + 1, Z1 - Z0 + 1, Y1 - Y0 + 1
SURFACE = 11
CX, CZ = 150, 216
R_IN, R_OUT, R_MID = 48, 92, 70
PHI_MAX = 150.0
FLOOR = -40
CAMP = (112, 216)
DOOR_PHI = -90.0
LEAP_PHI = 58.0
HALL_PHI0, HALL_PHI1 = 98.0, 146.0
HALL_Y = -12
ARENA = (CX + R_MID, CZ)
ARENA_R = 15
ARENA_Y = 0
ISLAND_TOP = SURFACE + 9

PALETTE = ["minecraft:air"]


def P(name):
    PALETTE.append(name)
    return len(PALETTE) - 1


AIR = 0
POWDER = P("minecraft:light_gray_concrete_powder")
POWDER_G = P("minecraft:gray_concrete_powder")
POWDER_D = P("minecraft:black_concrete_powder")
TUFF = P("minecraft:tuff")
ANDESITE = P("minecraft:andesite")
DEEPSLATE = P("minecraft:deepslate")
C_DEEPSLATE = P("minecraft:cobbled_deepslate")
SBASALT = P("minecraft:smooth_basalt")
BASALT = P("minecraft:basalt[axis=y]")
BLACKSTONE = P("minecraft:blackstone")
GRAVEL = P("minecraft:gravel")
POL_TUFF = P("minecraft:polished_tuff")
TUFF_BRICKS = P("minecraft:tuff_bricks")
CH_TUFF = P("minecraft:chiseled_tuff_bricks")
PBLACKSTONE = P("minecraft:polished_blackstone")
PB_BRICKS = P("minecraft:polished_blackstone_bricks")
CH_PB = P("minecraft:chiseled_polished_blackstone")
PB_WALL = P("minecraft:polished_blackstone_wall")
PB_STAIRS_E = P("minecraft:polished_blackstone_brick_stairs[facing=west,half=bottom]")
PB_SLAB = P("minecraft:polished_blackstone_brick_slab[type=bottom]")
SQUARTZ = P("minecraft:smooth_quartz")
QBRICKS = P("minecraft:quartz_bricks")
CALCITE = P("minecraft:calcite")
BLACK_CONC = P("minecraft:black_concrete")
CRYING = P("minecraft:crying_obsidian")
OBSIDIAN = P("minecraft:obsidian")
LANTERN = P("minecraft:soul_lantern[hanging=false]")
LANTERN_H = P("minecraft:soul_lantern[hanging=true]")
CHAIN = P("minecraft:iron_chain[axis=y]") if False else P("minecraft:chain[axis=y]")
BUD = P("minecraft:small_amethyst_bud[facing=up]")
SCULK = P("minecraft:sculk")
IRON_BARS = P("minecraft:iron_bars")


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
RR = np.hypot(XX - CX, ZZ - CZ)
PHI = np.degrees(np.arctan2(ZZ - CZ, XX - CX))
N_ROLL = noise2(12, 41)
N_PATCH = noise2(6, 42)
N_EDGE = noise2(5, 43)
N_WALL = noise3(4, 44) * 2 - 1
N_STRATA = noise3(7, 45)

EDGE = np.minimum.reduce([XX - X0, X1 - XX, ZZ - Z0, Z1 - ZZ])
FADE = np.clip((EDGE - 2) / 10.0, 0, 1)

vol = np.zeros((NX, NY, NZ), dtype=np.int16)
for yi in range(NY):
    vol[:, yi, :] = base_state(Y0 + yi)


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
SHADOW = []


def mark(name, x, y, z):
    MARKS[name] = [int(x), int(y), int(z)]


def polar(phi, r, y=SURFACE):
    a = math.radians(phi)
    return int(round(CX + math.cos(a) * r)), y, int(round(CZ + math.sin(a) * r))


def rock_mat(y, xi, zi, k):
    """淵の壁の材質 (縦の層): 底は黒石と深層岩、中は玄武岩と凝灰岩、縁の近くは灰の凝灰岩"""
    yy = y + (N_STRATA[xi, max(0, min(NY - 1, y - Y0)), zi] - 0.5) * 5.0
    if yy < -26:
        return BLACKSTONE if (k % 3) else DEEPSLATE
    if yy < -10:
        return DEEPSLATE if (k % 4) else C_DEEPSLATE
    if yy < 2:
        return SBASALT if (k % 4) else BASALT
    return TUFF if (k % 3) else ANDESITE


# ---------------------------------------------------------------- 内の台地と外の面 (暗い砂の起伏)
ROLL = np.round((N_ROLL - 0.5) * 2.4 * FADE).astype(int)
for xi in range(NX):
    for zi in range(NZ):
        t = SURFACE + ROLL[xi, zi]
        for y in range(SURFACE - 3, SURFACE + 3):
            vol[xi, y - Y0, zi] = AIR if y > t else (POWDER if y >= t - 2 else TUFF)
        p = N_PATCH[xi, zi]
        inner = RR[xi, zi] < R_IN + 2
        vol[xi, t - Y0, zi] = (POWDER_D if p > 0.62 else POWDER_G) if inner else (POWDER if p > 0.3 else POWDER_G)


# ---------------------------------------------------------------- 淵 (弧の帯)
def band(phi, r):
    """その所が淵の帯の中か。端 (±150°) は尖って細くなる。縁は少し出入りする"""
    a = abs(phi)
    if a > PHI_MAX + 6:
        return False
    tip = float(np.clip((PHI_MAX + 6 - a) / 26.0, 0, 1))
    half = (R_OUT - R_IN) / 2 * math.sqrt(tip)
    return abs(r - R_MID) <= half


for xi in range(NX):
    for zi in range(NZ):
        x, z = X0 + xi, Z0 + zi
        r, phi = RR[xi, zi], PHI[xi, zi]
        wob = (N_EDGE[xi, zi] - 0.5) * 3.0
        if not band(phi, r + wob * (1 if r > R_MID else -1)):
            continue
        for y in range(FLOOR, SURFACE + 4):
            vol[xi, y - Y0, zi] = AIR
        vol[xi, FLOOR - 1 - Y0, zi] = BLACKSTONE if hsh(x, 1, z) % 5 else SCULK
        vol[xi, FLOOR - 2 - Y0, zi] = BLACK_CONC
        for y in range(Y0, FLOOR - 2):
            vol[xi, y - Y0, zi] = BLACK_CONC

# 壁: 帯の縁から 3 の内側の岩を層の材質に (月の面の凝灰岩のままにしない)、張り出しと抉れ
air = np.zeros((NX, NZ), bool)
for xi in range(NX):
    for zi in range(NZ):
        air[xi, zi] = vol[xi, SURFACE - Y0, zi] == AIR and vol[xi, FLOOR - Y0, zi] == AIR
near = ndimage.binary_dilation(air, iterations=3) & ~air
for xi, zi in zip(*np.nonzero(near)):
    x, z = X0 + xi, Z0 + zi
    for y in range(FLOOR, SURFACE - 1):
        if vol[xi, y - Y0, zi] != AIR:
            vol[xi, y - Y0, zi] = rock_mat(y, xi, zi, hsh(x, y, z))
edge1 = ndimage.binary_dilation(air, iterations=1) & ~air
for xi, zi in zip(*np.nonzero(edge1)):
    x, z = X0 + xi, Z0 + zi
    # 壁の面の張り出し (帯の中へ 1 出る岩の瘤)
    for y in range(FLOOR + 2, SURFACE - 2):
        if N_WALL[xi, y - Y0, zi] > 0.45:
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, zz = xi + dx, zi + dz
                if 0 <= xx < NX and 0 <= zz < NZ and air[xx, zz] and vol[xx, y - Y0, zz] == AIR:
                    vol[xx, y - Y0, zz] = rock_mat(y, xx, zz, hsh(x + dx, y, z + dz))
                    break

# 淵の中の岩の尖り (底から伸びる。上は y -6〜2。場所の近くは避ける)
spires = []
for phi in (-128, -62, -30, 22, 96, 132):
    sx, _, sz = polar(phi, R_MID + (hsh(phi, 2, 3) % 9 - 4))
    top = -6 + hsh(phi, 4, 5) % 9
    spires.append((sx, sz, top))
    for y in range(FLOOR, top + 1):
        rad = 3.2 * (1 - (y - FLOOR) / (top - FLOOR + 6)) + 0.8
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                if math.hypot(dx, dz) <= rad + (N_WALL[min(NX - 1, max(0, sx + dx - X0)), y - Y0, min(NZ - 1, max(0, sz + dz - Z0))] * 0.6):
                    put(sx + dx, y, sz + dz, rock_mat(y, sx + dx - X0, sz + dz - Z0, hsh(sx + dx, y, sz + dz)))

# 縁の灯り (内の縁に 18 ごと。魂の灯り)
lamps = 0
for phi in range(-144, 145, 12):
    x, _, z = polar(phi, R_IN - 3)
    t = top_at(x, z, SURFACE + 3)
    if SURFACE - 2 <= t <= SURFACE + 2 and get(x, t + 1, z) == AIR:
        put(x, t + 1, z, PB_WALL)
        put(x, t + 2, z, LANTERN)
        lamps += 1

# ---------------------------------------------------------------- 1 二つの板の門 (北)
rx, _, rz = polar(DOOR_PHI, R_IN)           # 内の縁 (z 168)
# 岩山 (縁の南。北の面に門)
hx, hz = rx, rz + 12
for x in range(hx - 9, hx + 10):
    for z in range(hz - 7, hz + 8):
        d = math.hypot((x - hx) / 9.0, (z - hz) / 7.0)
        if d <= 1.0:
            h = int(round((1 - d * d) * 12 + (hsh(x, 9, z) % 3)))
            for y in range(SURFACE - 2, SURFACE + h + 1):
                put(x, y, z, rock_mat(y, x - X0, z - Z0, hsh(x, y, z)) if y < SURFACE + h else (POWDER_G if hsh(x, 1, z) % 2 else POWDER_D))
# 門の奥の窪 (幅 5・奥 7・高さ 5) と欠片
gz = hz - 6
for x in range(hx - 2, hx + 3):
    for z in range(gz, gz + 8):
        for y in range(SURFACE + 1, SURFACE + 6):
            put(x, y, z, AIR)
        put(x, SURFACE, z, PB_BRICKS if (x + z) % 2 else PBLACKSTONE)
for x in range(hx - 3, hx + 4):
    put(x, SURFACE + 6, gz, CH_PB)
mark("door_gate", hx, SURFACE + 1, gz)
mark("shard_door", hx, SURFACE + 1, gz + 6)
put(hx - 2, SURFACE + 4, gz + 6, LANTERN_H)
put(hx + 2, SURFACE + 4, gz + 6, LANTERN_H)
# 門の前の平ら (磨いた黒石の床) と板 A・鏡
for x in range(hx - 4, hx + 5):
    for z in range(rz + 1, gz):
        t = top_at(x, z, SURFACE + 4)
        for y in range(SURFACE + 1, t + 1):
            put(x, y, z, AIR)
        put(x, SURFACE, z, PBLACKSTONE if (x * 3 + z) % 4 else PB_BRICKS)
mark("door_plate_a", hx, SURFACE + 1, gz - 2)
mark("door_mirror", hx - 3, SURFACE + 1, rz + 2)
# 細い棚 (縁から北へ淵の上を 20、幅 3、下に岩の垂れ)
for z in range(rz - 15, rz + 1):
    for x in range(rx - 1, rx + 2):
        put(x, SURFACE, z, PB_BRICKS if (x + z) % 3 else PBLACKSTONE)
        for y in range(SURFACE - 3 - (hsh(x, 7, z) % 4), SURFACE):
            put(x, y, z, rock_mat(y, x - X0, z - Z0, hsh(x, y, z)))
    if (z - rz) % 6 == 0:
        put(rx - 1, SURFACE + 1, z, PB_WALL)
mark("door_plate_b", rx, SURFACE + 1, rz - 14)
mark("door_entry", hx, SURFACE + 1, rz + 4)

# ---------------------------------------------------------------- 2 残像の渡り (南東)
ix, _, iz = polar(LEAP_PHI, R_MID)
for y in range(FLOOR, ISLAND_TOP + 1):
    rad = 3.5 if y > ISLAND_TOP - 6 else 3.5 + (ISLAND_TOP - 6 - y) * 0.06
    for dx in range(-6, 7):
        for dz in range(-6, 7):
            if math.hypot(dx, dz) <= rad:
                put(ix + dx, y, iz + dz, (PB_BRICKS if math.hypot(dx, dz) < 2.4 else PBLACKSTONE) if y == ISLAND_TOP
                    else rock_mat(y, ix + dx - X0, iz + dz - Z0, hsh(ix + dx, y, iz + dz)))
mark("shard_leap", ix, ISLAND_TOP + 1, iz)
# 内の縁の跳び台 (島を向いた、縁に張り出す磨いた黒石の舌。先は内の縁 R_IN)
lx, _, lz = polar(LEAP_PHI, R_IN - 1)
ux, uz = (ix - lx), (iz - lz)
L = math.hypot(ux, uz)
ux, uz = ux / L, uz / L
for s in range(-8, 2):
    for w in range(-2, 3):
        x = int(round(lx + ux * s - uz * w))
        z = int(round(lz + uz * s + ux * w))
        put(x, SURFACE, z, PB_BRICKS if (s + w) % 2 else PBLACKSTONE)
        for y in range(SURFACE + 1, SURFACE + 4):
            put(x, y, z, AIR)
mark("leap_edge", int(round(lx + ux * 1)), SURFACE + 1, int(round(lz + uz * 1)))
mark("leap_mirror", int(round(lx - ux * 6 - uz * 2)), SURFACE + 1, int(round(lz - uz * 6 + ux * 2)))
mark("leap_entry", int(round(lx - ux * 7)), SURFACE + 1, int(round(lz - uz * 7)))
LEAP_GAP = math.hypot(ix - (lx + ux * 1), iz - (lz + uz * 1)) - 3.5

# ---------------------------------------------------------------- 3 灯りの回廊 (南西。淵の中の棚)
# 入口の段: 内の縁 (HALL_PHI0 - 6) から壁に沿って下る 1 幅の段 (y 11 → -12)
segs = []
phi = HALL_PHI0 - 8
y = SURFACE
stair = []
while y > HALL_Y:
    x, _, z = polar(phi, R_IN + 1.5)
    stair.append((x, y, z))
    phi += 0.9
    y -= 1
hall_start_phi = phi
# 棚: R_IN 〜 R_IN + 5、床 y HALL_Y、屋根 y HALL_Y + 6 (内の壁から張り出す)
gaps = [(hall_start_phi + 5, 5), (hall_start_phi + 15, 6), (hall_start_phi + 25, 6)]


def in_gap(p):
    for g0, w in gaps:
        if g0 <= p <= g0 + w * (180 / math.pi) / (R_IN + 2.5):
            return (g0, w)
    return None


for xi in range(NX):
    for zi in range(NZ):
        r, ph = RR[xi, zi], PHI[xi, zi]
        if not (hall_start_phi - 3 <= ph <= HALL_PHI1 + 2) or not (R_IN - 1 <= r <= R_IN + 5.5):
            continue
        x, z = X0 + xi, Z0 + zi
        g = in_gap(ph)
        if r >= R_IN + 0.3:
            # 床 (切れ目は空ける。下に岩の垂れ)
            if g is None:
                put(x, HALL_Y, z, PB_BRICKS if hsh(x, 2, z) % 3 else PBLACKSTONE)
                for yy in range(HALL_Y - 2 - hsh(x, 3, z) % 3, HALL_Y):
                    put(x, yy, z, rock_mat(yy, xi, zi, hsh(x, yy, z)))
            # 屋根の張り出し (内の壁から外へ、外ほど薄い)
            roof = HALL_Y + 6 + (1 if r < R_IN + 3 else 0)
            for yy in range(roof, roof + 3 - int(r - R_IN) // 2):
                put(x, yy, z, rock_mat(yy, xi, zi, hsh(x, yy, z)))
        # 回廊の中は空ける (内の壁を少し削る)
        for yy in range(HALL_Y + 1, HALL_Y + 6):
            if r >= R_IN - 0.8:
                put(x, yy, z, AIR)
for (x, y, z) in stair:
    for dw in (0, 1):
        xx, zz = x, z
        put(xx, y - 1, zz, PB_BRICKS)
        for yy in range(y, y + 4):
            put(xx, yy, zz, AIR)
        put(xx, y - 2, zz, rock_mat(y - 2, xx - X0, zz - Z0, hsh(xx, y, zz)))
# 影の橋: 切れ目ごとに、棚の真ん中 (R_IN + 2.5) を 1 幅で渡る
for g0, w in gaps:
    steps = int(w * 2) + 4
    seen = set()
    for i in range(steps + 1):
        p = g0 - 1 + (w * (180 / math.pi) / (R_IN + 2.5) + 2) * i / steps
        x, _, z = polar(p, R_IN + 2.5, HALL_Y)
        if (x, z) not in seen and in_gap(p) is not None:
            seen.add((x, z))
            SHADOW.append([x, HALL_Y, z])
# 残り影の溜まり 4 (棚の内の壁際。切れ目の手前と奥)
pools = [hall_start_phi + 2, gaps[0][0] + 8, gaps[1][0] + 8.5, gaps[2][0] + 8.5]
for i, p in enumerate(pools):
    x, _, z = polar(p, R_IN + 1, HALL_Y)
    put(x, HALL_Y, z, CRYING)
    mark(f"pool_{i}", x, HALL_Y + 1, z)
# 奥の窪と欠片
ex, _, ez = polar(HALL_PHI1 - 2, R_IN + 2.5, HALL_Y)
mark("shard_hall", ex, HALL_Y + 1, ez)
mark("hall_entry", stair[0][0], stair[0][1], stair[0][2])
mark("hall_floor", *polar(hall_start_phi + 2, R_IN + 2.5, HALL_Y + 1))
for p in (hall_start_phi + 4, gaps[1][0] - 3, HALL_PHI1 - 4):
    x, _, z = polar(p, R_IN + 0.6, HALL_Y + 4)
    if get(x, HALL_Y + 6, z) != AIR:
        put(x, HALL_Y + 5, z, LANTERN_H)

# ---------------------------------------------------------------- 主の間 (東): 帯の真ん中の柱と丸い床
ax, az = ARENA
for x in range(ax - ARENA_R - 4, ax + ARENA_R + 5):
    for z in range(az - ARENA_R - 4, az + ARENA_R + 5):
        d = math.hypot(x - ax, z - az)
        a = math.degrees(math.atan2(z - az, x - ax))
        cut = abs(a) < 60 and d > ARENA_R - 4      # 東の三分の一の外周は欠けている
        for y in range(FLOOR, ARENA_Y + 1):
            rad = ARENA_R + 2 * (ARENA_Y - y) / (ARENA_Y - FLOOR) + (N_WALL[x - X0, y - Y0, z - Z0] * 1.2 if y < ARENA_Y - 2 else 0)
            if cut and y > ARENA_Y - 10 + int(d - (ARENA_R - 4)) * 2:
                continue
            if d <= rad:
                if y == ARENA_Y:
                    ring = abs(d - 7) < 0.6 or abs(d - 12) < 0.6
                    mat = SQUARTZ if ring else (PB_BRICKS if (int(d) + int((a + 180) / 15)) % 2 else PBLACKSTONE)
                    if d < 2.5:
                        mat = CH_PB
                    put(x, y, z, mat)
                else:
                    put(x, y, z, rock_mat(y, x - X0, z - Z0, hsh(x, y, z)))
mark("arena_center", ax, ARENA_Y + 1, az)
# 門 (内の縁、主の間を向く) と石の段の橋 (縁 y 11 → 床 y 0)
gx = CX + R_IN - 2
mark("rift_gate", gx, SURFACE + 1, CZ)
for x in range(gx - 4, gx + 1):
    for z in range(CZ - 3, CZ + 4):
        put(x, SURFACE, z, PB_BRICKS if (x + z) % 2 else PBLACKSTONE)
        for y in range(SURFACE + 1, SURFACE + 6):
            put(x, y, z, AIR)
for dz in (-2, 2):
    for y in range(SURFACE + 1, SURFACE + 6):
        put(gx, y, CZ + dz, PB_BRICKS)
for y in (SURFACE + 5,):
    for dz in range(-2, 3):
        put(gx, y, CZ + dz, CH_PB)
bx0, bx1 = gx + 1, ax - ARENA_R + 1
for x in range(bx0, bx1 + 1):
    t = SURFACE - int(round((x - bx0) * (SURFACE - ARENA_Y) / max(1, bx1 - bx0)))
    for z in range(CZ - 1, CZ + 2):
        put(x, t, z, PB_BRICKS)
        for y in range(t - 3, t):
            put(x, y, z, rock_mat(y, x - X0, z - Z0, hsh(x, y, z)))
        for y in range(t + 1, t + 4):
            put(x, y, z, AIR)
    put(x, t + 1, CZ - 2, AIR)
mark("arena_entry", ax - ARENA_R + 3, ARENA_Y + 1, az)
mark("arena_back", ax, ARENA_Y + 1, az + ARENA_R - 2)
mark("gate_front", gx - 3, SURFACE + 1, CZ)

# ---------------------------------------------------------------- 野営 (内の台地の西)
cx, cz = CAMP
for x in range(cx - 9, cx + 10):
    for z in range(cz - 9, cz + 10):
        d = math.hypot(x - cx, z - cz)
        if d <= 8.5:
            t = top_at(x, z, SURFACE + 4)
            for y in range(SURFACE + 1, t + 1):
                put(x, y, z, AIR)
            put(x, SURFACE, z, GRAVEL if hsh(x, 4, z) % 4 == 0 else POWDER_G)
        elif d <= 9.6 and hsh(x, 8, z) % 3:
            # 崩れた低い石の環
            put(x, SURFACE + 1, z, TUFF_BRICKS if hsh(x, 5, z) % 2 else POL_TUFF)
            if hsh(x, 6, z) % 4 == 0:
                put(x, SURFACE + 2, z, TUFF_BRICKS)
mark("camp", cx, SURFACE + 1, cz)
mark("star_pool", cx + 4, SURFACE, cz - 4)
mark("return_stone", cx - 5, SURFACE + 1, cz - 5)
mark("camp_mirror", cx + 5, SURFACE + 1, cz + 4)
mark("camp_plate", cx + 6, SURFACE + 1, cz + 8)
mark("past_self", cx - 2, SURFACE + 1, cz + 2)
spx, spy, spz = MARKS["star_pool"]
for dx in range(-2, 3):
    for dz in range(-2, 3):
        put(spx + dx, SURFACE, spz + dz, CALCITE if max(abs(dx), abs(dz)) < 2 else PBLACKSTONE)

# ---------------------------------------------------------------- 踏み跡と道しるべ (着く所から野営、野営から各所)
ROUTES = {
    "arrival": [(52, 150), (80, 190), CAMP],
    "door": [CAMP, (124, 186), (MARKS["door_entry"][0], MARKS["door_entry"][2])],
    "gate": [CAMP, (MARKS["gate_front"][0] - 2, CZ)],
    "leap": [CAMP, (140, 238), (MARKS["leap_entry"][0], MARKS["leap_entry"][2])],
    "hall": [CAMP, (118, 236), (MARKS["hall_entry"][0], MARKS["hall_entry"][2])],
}
posts = 0
for name, pts in ROUTES.items():
    run = 0.0
    for (ax_, az_), (bx_, bz_) in zip(pts, pts[1:]):
        L = math.hypot(bx_ - ax_, bz_ - az_)
        n = int(L)
        for i in range(n + 1):
            t = i / max(1, n)
            x = ax_ + (bx_ - ax_) * t + math.sin(i * 0.21 + len(name)) * 1.2
            z = az_ + (bz_ - az_) * t + math.cos(i * 0.17 + len(name)) * 1.2
            run += 1
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    xx, zz = int(round(x)) + dx, int(round(z)) + dz
                    if abs(dx) + abs(dz) == 2 and hsh(xx, 6, zz) % 2:
                        continue
                    t0 = top_at(xx, zz, SURFACE + 3)
                    if SURFACE - 2 <= t0 <= SURFACE + 2 and get(xx, t0, zz) in (POWDER, POWDER_G, POWDER_D):
                        put(xx, t0, zz, GRAVEL if hsh(xx, 3, zz) % 4 == 0 else POWDER)
            if int(run) % 24 == 12:
                lx_, lz_ = int(round(x + 2.5)), int(round(z + 2.5))
                t0 = top_at(lx_, lz_, SURFACE + 3)
                if SURFACE - 2 <= t0 <= SURFACE + 2 and get(lx_, t0 + 1, lz_) == AIR:
                    put(lx_, t0 + 1, lz_, PB_WALL)
                    put(lx_, t0 + 2, lz_, LANTERN)
                    posts += 1

# ---------------------------------------------------------------- 飾り: 内の台地の黒い岩と紫水晶の芽、折れた巡礼の柱
rng = np.random.default_rng(0x52494654)
rocks = buds = 0


def busy(x, z):
    r = math.hypot(x - CX, z - CZ)
    if R_IN - 4 < r < R_OUT + 4:
        return True
    for k in ("camp", "door_entry", "leap_entry", "hall_entry", "gate_front"):
        mx, _, mz = MARKS[k]
        if math.hypot(x - mx, z - mz) < 12:
            return True
    if math.hypot(x - hx, z - hz) < 14:
        return True
    return get(x, top_at(x, z, SURFACE + 3), z) in (GRAVEL, POWDER)


for _ in range(3000):
    if rocks >= 26:
        break
    x, z = int(rng.integers(X0 + 6, X1 - 6)), int(rng.integers(Z0 + 6, Z1 - 6))
    if busy(x, z):
        continue
    t = top_at(x, z, SURFACE + 3)
    rr = 1.0 + rng.random() * 1.8
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(0, 3):
                if (dx / rr) ** 2 + (dz / rr) ** 2 + ((dy - 0.3) / (rr * 0.75)) ** 2 <= 1.0 and get(x + dx, t + 1 + dy, z + dz) == AIR:
                    put(x + dx, t + 1 + dy, z + dz, SBASALT if (dx + dy + dz) % 3 else BLACKSTONE)
    rocks += 1
for _ in range(3000):
    if buds >= 30:
        break
    x, z = int(rng.integers(X0 + 6, X1 - 6)), int(rng.integers(Z0 + 6, Z1 - 6))
    if busy(x, z):
        continue
    t = top_at(x, z, SURFACE + 3)
    if get(x, t + 1, z) == AIR and get(x, t, z) != AIR:
        put(x, t + 1, z, BUD)
        buds += 1
# 折れた巡礼の柱 (内の台地に 4。崩れた黒石の柱、足もとに鎖)
for i, (px_, pz_) in enumerate([(122, 196), (128, 244), (164, 206), (170, 232)]):
    if busy(px_, pz_) and math.hypot(px_ - CX, pz_ - CZ) > R_IN - 4:
        continue
    h = 5 + hsh(px_, 2, pz_) % 5
    for y in range(SURFACE + 1, SURFACE + 1 + h):
        put(px_, y, pz_, PB_BRICKS if y % 3 else CH_PB)
    put(px_ + 1, SURFACE + 1, pz_, PB_SLAB)
    put(px_, SURFACE + 1, pz_ + 1, PB_WALL)
    mark(f"pillar_{i}", px_, SURFACE + 1, pz_)

# ---------------------------------------------------------------- 書き出し
runs = []
for yi in range(NY):
    y = Y0 + yi
    for zi in range(NZ):
        seq = []
        for xi in range(NX):
            s = int(vol[xi, yi, zi])
            if seq and seq[-1][0] == s:
                seq[-1][1] += 1
            else:
                seq.append([s, 1])
        if len(seq) == 1 and seq[0][0] == base_state(y):
            continue
        runs.append([y, Z0 + zi, X0] + [v for p in seq for v in p])

data = {"note": "build_moon_rift.py が書く。手で直さない", "palette": PALETTE, "runs": runs, "marks": MARKS, "shadow": SHADOW,
        "rect": [X0, Z0, X1, Z1], "center": [CX, CZ], "radii": [R_IN, R_OUT], "phi_max": PHI_MAX}
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
print("rift", os.path.getsize(OUT) // 1024, "KB", len(runs), "runs", "shadow", len(SHADOW), "lamps", lamps, "posts", posts, "rocks", rocks, "buds", buds)
print("door plates", MARKS["door_plate_a"], MARKS["door_plate_b"], "dist", round(math.dist(MARKS["door_plate_a"], MARKS["door_plate_b"]), 1))
print("leap gap edge->island edge", round(LEAP_GAP, 1), "island", ix, iz, "outer rim gap", round(R_OUT - R_MID - 3.5, 1))
print("hall start phi", round(hall_start_phi, 1), "gaps", [(round(a, 1), w) for a, w in gaps], "stair", len(stair))
for name, (sx, sz, top) in zip(range(6), spires):
    print("spire", sx, sz, "top", top, "to island", round(math.hypot(sx - ix, sz - iz), 1), "to arena", round(math.hypot(sx - ARENA[0], sz - ARENA[1]), 1))

# ---------------------------------------------------------------- 見る絵
if "--preview" in sys.argv:
    from PIL import Image, ImageDraw
    rng2 = np.random.default_rng(1)
    COLS = {}
    for i, name in enumerate(PALETTE):
        n = name.split(":")[1].split("[")[0]
        base = {"light_gray_concrete_powder": (180, 184, 192), "gray_concrete_powder": (110, 114, 122), "black_concrete_powder": (50, 52, 60),
                "tuff": (110, 110, 112), "andesite": (136, 136, 136), "deepslate": (70, 70, 76), "cobbled_deepslate": (80, 80, 86),
                "smooth_basalt": (72, 72, 78), "basalt": (60, 60, 66), "blackstone": (40, 36, 42), "gravel": (120, 116, 112),
                "smooth_quartz": (236, 230, 222), "calcite": (220, 224, 220), "black_concrete": (8, 8, 10), "crying_obsidian": (120, 40, 200),
                "soul_lantern": (120, 220, 255), "small_amethyst_bud": (190, 140, 240)}.get(n, (90, 90, 100))
        COLS[i] = base
    top = np.zeros((NZ, NX, 3), np.uint8)
    for xi in range(NX):
        for zi in range(NZ):
            for yi in range(NY - 1, -1, -1):
                s = int(vol[xi, yi, zi])
                if s != AIR:
                    c = np.array(COLS.get(s, (255, 0, 255)), float)
                    shade = 0.3 + 0.7 * (yi / NY)
                    top[zi, xi] = np.clip(c * shade * 1.4, 0, 255)
                    break
    im = Image.fromarray(top).resize((NX * 4, NZ * 4), Image.NEAREST)
    d = ImageDraw.Draw(im)
    for x, y, z in SHADOW:
        px_, pz_ = (x - X0) * 4, (z - Z0) * 4
        d.rectangle((px_, pz_, px_ + 3, pz_ + 3), fill=(80, 60, 160))
    for k, (mx_, my_, mz_) in MARKS.items():
        px_, pz_ = (mx_ - X0) * 4 + 2, (mz_ - Z0) * 4 + 2
        d.ellipse((px_ - 4, pz_ - 4, px_ + 4, pz_ + 4), outline=(255, 120, 80), width=2)
        d.text((px_ + 6, pz_ - 6), k, fill=(255, 220, 120))
    os.makedirs(REVIEW, exist_ok=True)
    im.save(os.path.join(REVIEW, "rift_top.png"))
    print("preview written")
