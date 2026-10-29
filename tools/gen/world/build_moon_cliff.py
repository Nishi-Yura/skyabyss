"""六章 区画 2「上弦の崖」の地形を彫る (docs/design/46 の 4.4・4.4.1 の配置 A 弧の登り)。

  PYTHONIOENCODING=utf-8 python -I tools/gen/world/build_moon_cliff.py [--preview]
      -> src/main/resources/data/skyabyss/moon/cliff.json   (Java: story/ch6/cliff/MoonCliff が流し込む)
         docs/review/moon/cliff_top.png・cliff_cut.png     (--preview: 上から見た色の図と、道に沿った断面)

座標は月の世界の座標そのまま (着く所 = (0, 12, 0)。月の地面の上面は y 11、北が -z)。崖は着く所の東、x 52〜282・z -120〜62。
形 (上から): 中心 C (160, 50)・半径 100 の弧に沿って、上弦の月の形に北東へ登る尾根。
  - 頂の線: 弧の上 (弧から外へ 3 まで)。高さは西の端 y 13 から東の端へ y 80 近くまで
  - 道: 弧の内側の幅 7 の棚。頂より 4〜6 低い。半ブロックの段 (下付きの板) で、跳ばずに歩いて登れる
  - 内側の段々: 道の下に段々の岩棚が四つ、月の面へ下りる (落ちてもここで止まる)
  - 外側: 頂から月の面まで切り立つ崖 (張り出しのノイズ)
  - 野営: 西の端の内側の月の面。岩の張り出しの屋根 (Java が天幕・寝具・月の溜まり・帰りの台・灯りを置く)
  - 探す場所: 1 跳び石の塔 (道の内側に浮く石 6 つが螺旋に 5 ずつ登る。真ん中の頂の台に欠片の台)
              2 崩れる棚 (道から内へ伸びる細い岩の鰭に沿う 26 の棚。先の孤立した岩の柱に欠片の台。棚は Java が置く)
              3 天秤の岩 (道の内に二枚の皿の場所と、真ん中の柱と梁。皿の先の高い岩の柱に欠片の台。皿と鎖は Java が動かす)
  - 頂の門: 道の終わり。東の端の岩の塊の西の面に彫る (Java が星の門を置く)
  - 主の間: 東の端の岩の塊の上に、柱で支えた丸い皿 (半径 14、床 y 80)。まわりは堀 (落ちると Java が入口へ戻す)
"""
from __future__ import annotations

import json
import math
import os
import sys

import numpy as np
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "skyabyss", "moon", "cliff.json")
REVIEW = os.path.join(ROOT, "docs", "review", "moon")

X0, X1 = 52, 282
Z0, Z1 = -120, 62
Y0, Y1 = 4, 98
NX, NZ, NY = X1 - X0 + 1, Z1 - Z0 + 1, Y1 - Y0 + 1
SURFACE = 11
CX, CZ, R = 160.0, 50.0, 100.0
A_START, A_END = 200.0, 300.0
A_GATE = 289.0
ARENA_Y = 80
ARENA_R = 14

PALETTE = ["minecraft:air"]


def P(name):
    if name not in PALETTE:
        PALETTE.append(name)
    return PALETTE.index(name)


AIR = 0
SBASALT = P("minecraft:smooth_basalt")
BASALT = P("minecraft:basalt[axis=y]")
BLACKSTONE = P("minecraft:blackstone")
TUFF = P("minecraft:tuff")
ANDESITE = P("minecraft:andesite")
CALCITE = P("minecraft:calcite")
POWDER = P("minecraft:light_gray_concrete_powder")
POWDER_G = P("minecraft:gray_concrete_powder")
POWDER_W = P("minecraft:white_concrete_powder")
GRAVEL = P("minecraft:gravel")
COBBLED_DS = P("minecraft:cobbled_deepslate")
DEEPSLATE = P("minecraft:deepslate")
BUD = P("minecraft:medium_amethyst_bud[facing=up]")
AMETHYST = P("minecraft:amethyst_block")
PBLACKSTONE = P("minecraft:polished_blackstone")
PB_BRICKS = P("minecraft:polished_blackstone_bricks")
CH_PB = P("minecraft:chiseled_polished_blackstone")
SQUARTZ = P("minecraft:smooth_quartz")
POL_DS = P("minecraft:polished_deepslate")
DS_TILES = P("minecraft:deepslate_tiles")
POL_TUFF = P("minecraft:polished_tuff")
TUFF_BRICKS = P("minecraft:tuff_bricks")
CH_TUFF = P("minecraft:chiseled_tuff_bricks")
IRON = P("minecraft:iron_block")
CHAIN = P("minecraft:chain[axis=y]")
LANTERN = P("minecraft:soul_lantern[hanging=false]")
TUFF_SLAB = P("minecraft:tuff_slab[type=bottom]")
POL_TUFF_SLAB = P("minecraft:polished_tuff_slab[type=bottom]")
TUFF_WALL = P("minecraft:tuff_wall")


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
ANG = (np.degrees(np.arctan2(ZZ - CZ, XX - CX)) + 360.0) % 360.0
TT = np.clip((ANG - A_START) / (A_END - A_START), 0, 1)
DR = RR - R                       # 弧からの外への距離 (負 = 内側)

N_EDGE = noise2(6, 21) * 2 - 1
N_CREST = noise2(4, 22)
N_PATCH = noise2(5, 23)
N_WALL = noise3(5, 24) * 2 - 1
N_STRATA = noise3(7, 25)


def path_top(t):
    """道の上面 (立つブロックの y)。西の端 11 → 道の終わり (t 0.89) 67"""
    return SURFACE + 64.0 * np.power(np.clip(t, 0, 1), 1.15)


def at_angle(a, dr):
    """弧の角度 a (度) と、弧からの距離 dr (負 = 内) の点 (x, z)"""
    rr = R + dr
    return CX + rr * math.cos(math.radians(a)), CZ + rr * math.sin(math.radians(a))


PT = path_top(TT)
# 頂: 道より 2〜6 高い (西の端は低く)。ノイズで尖る
CREST = PT + np.minimum(4.0, 1.0 + 30.0 * TT) + 2.5 * N_CREST
ARC = (ANG >= A_START - 14) & (ANG <= A_END + 4)
# 西の端は頂も道も月の面へ溶ける
tail = np.clip((ANG - (A_START - 14)) / 14.0, 0, 1)
CREST = SURFACE + (CREST - SURFACE) * np.where(ANG < A_START, tail, 1.0)

# 東の端の岩の塊 (主の間の下) の中心
AX, AZ = at_angle(A_END, 0.0)
DA = np.hypot(XX - AX, ZZ - AZ)

# ---------------------------------------------------------------- 列ごとの上面と材質
TOP = np.full((NX, NZ), SURFACE, dtype=float)
KIND = np.zeros((NX, NZ), dtype=np.int8)        # 0 月の面・1 外の崖・2 頂・3 道・4 内の段々・5 塊・6 皿・7 堀
for xi in range(NX):
    for zi in range(NZ):
        if not ARC[xi, zi]:
            continue
        dr = DR[xi, zi] + 1.2 * N_EDGE[xi, zi]
        pt, ct = PT[xi, zi], CREST[xi, zi]
        if -1.0 <= dr <= 3.0:
            TOP[xi, zi], KIND[xi, zi] = ct, 2
        elif dr > 3.0:
            # 外の崖: 頂から 6 下がるごとに 1 外へ (切り立つ)
            fall = ct - (dr - 3.0) * 6.0
            if fall > SURFACE:
                TOP[xi, zi], KIND[xi, zi] = fall, 1
        elif dr >= -8.0:
            TOP[xi, zi], KIND[xi, zi] = pt, 3
        elif dr >= -20.0:
            k = int((-8.0 - dr) // 3.0) + 1             # 段 1〜4
            drop = max(2.0, (pt - SURFACE) / 4.5)
            top = pt - drop * k
            if top > SURFACE:
                TOP[xi, zi], KIND[xi, zi] = top, 4

# 東の端の岩の塊と主の間: 皿 (半径 14、床 80) を柱が支え、まわりは堀 (半径 15〜18、底 64)。その外は岩山:
#   頂の輪 (半径 18〜21、y 68〜70) から段のついた斜面で裾 (半径 52 ほど) へ下りる。縁は角度のノイズで出入りし、円筒に見せない。
#   頂の輪は皿 (y 80) より 10 低い: 軽身 (9.4) でも跳び移れない (門を通らずに皿へ入れない)
TH = np.degrees(np.arctan2(ZZ - AZ, XX - AX))
RAD_MOD = 1.0 + 0.13 * np.sin(np.radians(3 * TH + 40)) + 0.08 * np.sin(np.radians(5 * TH + 110)) + 0.06 * N_EDGE
for xi in range(NX):
    for zi in range(NZ):
        d0 = DA[xi, zi]
        if d0 > 60:
            continue
        a = ANG[xi, zi]
        on_path = KIND[xi, zi] == 3 and a <= A_GATE
        if d0 <= ARENA_R:
            TOP[xi, zi], KIND[xi, zi] = ARENA_Y, 6
            continue
        if d0 <= 18:
            TOP[xi, zi], KIND[xi, zi] = 64, 7
            continue
        if on_path:
            continue
        de = 18 + (d0 - 18) / RAD_MOD[xi, zi]
        if de <= 21:
            top = 68 + 2 * N_CREST[xi, zi]
        else:
            # 斜面: 2.4 ずつ下がり、6 ごとに段の棚 (平らな所) を挟む
            run = de - 21
            top = 68 - run * 2.4 + 2.0 * (N_CREST[xi, zi] - 0.5)
            if (run % 6.0) < 1.6:
                top = 68 - (run - (run % 6.0)) * 2.4 + 1.0
        if top > TOP[xi, zi] and top > SURFACE:
            TOP[xi, zi], KIND[xi, zi] = top, 5

TOPI = np.round(TOP).astype(int)
# 道は半ブロックずつ: 上の段は切り捨て、端数が半分を超えたら下付きの板を足す (下の「道の半ブロックの段」)
TOPI = np.where(KIND == 3, np.floor(TOP).astype(int), TOPI)
WRITE = ndimage.binary_dilation(KIND > 0, iterations=3)

# ---------------------------------------------------------------- 立体を組む
vol = np.zeros((NX, NY, NZ), dtype=np.int16)
for yi in range(NY):
    vol[:, yi, :] = base_state(Y0 + yi)


def wall_mat(y, xi, zi, k, top):
    """崖の材質 (縦の層): 下が玄武岩、中が凝灰岩と安山岩、上ほど明るい凝灰岩。上面の近くは月の砂"""
    yy = y + (N_STRATA[xi, y - Y0, zi] - 0.5) * 4.0
    if y >= top - 1:
        return POWDER if (k % 5) else POWDER_G
    if yy < 20:
        return SBASALT if (k % 5) else BASALT
    if yy < 30:
        return BASALT if (k % 3) else BLACKSTONE
    if yy < 42:
        return TUFF if (k % 4) else ANDESITE
    if yy < 52:
        return SBASALT if (k % 4) else DEEPSLATE
    if yy < 64:
        return TUFF if (k % 3) else ANDESITE
    return CALCITE if (k % 6) == 0 else TUFF


for xi in range(NX):
    for zi in range(NZ):
        if not WRITE[xi, zi]:
            continue
        x, z = X0 + xi, Z0 + zi
        kind = KIND[xi, zi]
        top = TOPI[xi, zi]
        if kind == 0:
            continue
        for y in range(SURFACE + 1, min(Y1, top) + 1):
            yi = y - Y0
            vol[xi, yi, zi] = wall_mat(y, xi, zi, hsh(x, y, z), top)
        # 外の崖の張り出しと抉れ (面の近くだけ)
        if kind == 1:
            for y in range(SURFACE + 2, min(Y1, top)):
                if N_WALL[xi, y - Y0, zi] > 0.55:
                    vol[xi, y - Y0, zi] = AIR
        # 上面: 道は砂と礫の踏み跡、段々は砂、頂は凝灰岩の尖り
        if kind == 3 and top <= Y1:
            hv = hsh(x, 1, z) % 100
            vol[xi, top - Y0, zi] = GRAVEL if hv < 18 else POWDER_G if hv < 50 else POWDER
        elif kind == 4 and top <= Y1:
            vol[xi, top - Y0, zi] = POWDER_W if N_PATCH[xi, zi] > 0.7 else POWDER
        elif kind == 2:
            spike = int(N_CREST[xi, zi] * 4) if hsh(x, 2, z) % 7 == 0 else 0
            for y in range(top + 1, min(Y1, top + spike) + 1):
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


def write(x, z):
    xi, zi = x - X0, z - Z0
    if 0 <= xi < NX and 0 <= zi < NZ:
        WRITE[xi, zi] = True


def top_at(x, z):
    y = Y1
    while y > SURFACE and get(x, y, z) == AIR:
        y -= 1
    return y


MARKS = {}


def mark(name, x, y, z):
    MARKS[name] = [int(x), int(y), int(z)]


# ---------------------------------------------------------------- 道の半ブロックの段 (下付きの板で、跳ばずに登れる)
for xi in range(NX):
    for zi in range(NZ):
        if KIND[xi, zi] != 3:
            continue
        exact = PT[xi, zi]
        top = TOPI[xi, zi]
        if exact - top >= 0.5:
            x, z = X0 + xi, Z0 + zi
            put(x, top + 1, z, TUFF_SLAB if hsh(x, 3, z) % 3 else POL_TUFF_SLAB)
# 道の上の頂の尖りや張り出しが頭に当たらないよう、道の上 4 は空ける
for xi in range(NX):
    for zi in range(NZ):
        if KIND[xi, zi] == 3:
            top = TOPI[xi, zi]
            for y in range(top + 2, top + 6):
                if 0 <= y - Y0 < NY and vol[xi, y - Y0, zi] not in (AIR,):
                    vol[xi, y - Y0, zi] = AIR

# ---------------------------------------------------------------- 野営 (西の端の内側の月の面。岩の張り出しの屋根)
cx, cz = at_angle(203.0, -16.0)
cx, cz = round(cx), round(cz)
mark("camp", cx, SURFACE + 1, cz)
mark("past_self", cx - 3, SURFACE + 1, cz + 2)
mark("star_pool", cx + 3, SURFACE, cz - 1)
mark("return_stone", cx + 1, SURFACE + 1, cz + 4)
for dx in range(-9, 10):
    for dz in range(-9, 10):
        x, z = cx + dx, cz + dz
        write(x, z)
        d = math.hypot(dx, dz)
        # 北 (尾根の側) の岩の壁と、その上の張り出しの屋根
        if dz <= -5 and d <= 9.5:
            h = 6 + (hsh(x, 4, z) % 3)
            for y in range(SURFACE + 1, SURFACE + 1 + h):
                put(x, y, z, wall_mat(y, x - X0, z - Z0, hsh(x, y, z), SURFACE + h))
        if -5 < dz <= -2 and d <= 8.5:
            put(x, SURFACE + 6, z, TUFF if hsh(x, 5, z) % 4 else ANDESITE)
            put(x, SURFACE + 7, z, POWDER)
        if d <= 7 and dz > -5:
            for y in range(SURFACE + 1, SURFACE + 6):
                put(x, y, z, AIR)
            put(x, SURFACE, z, POWDER_G if hsh(x, 6, z) % 4 == 0 else POWDER)
# 星の溜まりの鉢 (紫水晶の上に Java が溜まりを置く)
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if abs(dx) + abs(dz) <= 3:
            put(cx + 3 + dx, SURFACE, cz - 1 + dz, CALCITE if abs(dx) + abs(dz) == 3 else AMETHYST)

# ---------------------------------------------------------------- 1 跳び石の塔 (道の内側。浮く石 6 つが螺旋に 5 ずつ登る)
TA = 224.0
tx, tz = at_angle(TA, -22.0)
p0x, p0z = at_angle(TA, -8.0)
base_y = int(round(float(path_top((TA - A_START) / (A_END - A_START)))))
toward = math.atan2(p0z - tz, p0x - tx)
for k in range(6):
    th = toward + k * math.radians(60.0)
    sx, sz = round(tx + 6.0 * math.cos(th)), round(tz + 6.0 * math.sin(th))
    sy = base_y + 5 * (k + 1)
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            put(sx + dx, sy, sz + dz, POWDER if (dx, dz) != (0, 0) or k % 2 else POWDER_G)
            put(sx + dx, sy - 1, sz + dz, TUFF if (dx + dz) % 2 else SBASALT)
            write(sx + dx, sz + dz)
    # 下の尖り (岩が垂れる)
    for dy in range(2, 5):
        put(sx, sy - dy, sz, BASALT if dy < 4 else BLACKSTONE)
    mark("tower_stone_%d" % k, sx, sy, sz)
ty = base_y + 35
for dx in range(-3, 4):
    for dz in range(-3, 4):
        if math.hypot(dx, dz) <= 2.6:
            put(round(tx) + dx, ty, round(tz) + dz, POL_DS if math.hypot(dx, dz) > 1.6 else DS_TILES)
            put(round(tx) + dx, ty - 1, round(tz) + dz, SBASALT)
            if math.hypot(dx, dz) < 1.5:
                for dy in range(2, 7):
                    put(round(tx) + dx, ty - dy, round(tz) + dz, BASALT)
            write(round(tx) + dx, round(tz) + dz)
mark("shard_tower", round(tx), ty + 1, round(tz))
# 塔の下の段々は低く (石の下に立って真上へ跳べないように)
for xi in range(NX):
    for zi in range(NZ):
        x, z = X0 + xi, Z0 + zi
        if math.hypot(x - tx, z - tz) < 10.5 and KIND[xi, zi] == 4:
            for y in range(SURFACE + 1, Y1):
                if get(x, y, z) != AIR and y > SURFACE + 3:
                    put(x, y, z, AIR)
            put(x, SURFACE + 3, z, POWDER)

# ---------------------------------------------------------------- 2 崩れる棚 (道から内へ伸びる岩の鰭に沿う 26 の棚。先の孤立した柱に欠片)
LA = 246.0
ly = int(round(float(path_top((LA - A_START) / (A_END - A_START)))))
lax, laz = at_angle(LA, -8.5)
lbx, lbz = at_angle(LA, -34.0)
spx, spz = at_angle(LA, -37.5)
mark("ledge_a", round(lax), ly, round(laz))
mark("ledge_b", round(lbx), ly, round(lbz))
# 棚の筋の横 (時計回りの側) に岩の鰭: 地面から棚の上 7 まで、厚さ 2
L = math.hypot(lbx - lax, lbz - laz)
dxu, dzu = (lbx - lax) / L, (lbz - laz) / L
nx, nz = -dzu, dxu
for i in range(int(L) + 1):
    bx, bz = lax + dxu * i, laz + dzu * i
    for w in (1.4, 2.4):
        fx, fz = round(bx + nx * w), round(bz + nz * w)
        h = ly + 5 + (hsh(fx, 7, fz) % 4)
        for y in range(SURFACE + 1, h + 1):
            put(fx, y, fz, wall_mat(y, fx - X0, fz - Z0, hsh(fx, y, fz), h))
        write(fx, fz)
    # 棚の筋とその反対の脇は空ける (棚は Java が置く)
    for w in (0.0, -1.0):
        cxl, czl = round(bx + nx * w), round(bz + nz * w)
        for y in range(ly - 6, ly + 5):
            if y > SURFACE:
                put(cxl, y, czl, AIR)
        write(cxl, czl)
# 孤立した岩の柱 (半径 3、上面 = 棚の高さ)
for dx in range(-4, 5):
    for dz in range(-4, 5):
        x, z = round(spx) + dx, round(spz) + dz
        d = math.hypot(dx, dz)
        if d <= 3.2 + 0.4 * ((hsh(x, 8, z) % 10) / 10):
            for y in range(SURFACE + 1, ly + 1):
                put(x, y, z, wall_mat(y, x - X0, z - Z0, hsh(x, y, z), ly))
            write(x, z)
mark("shard_ledge", round(spx), ly + 1, round(spz))
# 柱のまわりの段々は月の面まで下げる (段々から跳んで渡れないように)
for xi in range(NX):
    for zi in range(NZ):
        x, z = X0 + xi, Z0 + zi
        if 3.6 < math.hypot(x - spx, z - spz) < 12.0 and KIND[xi, zi] == 4:
            for y in range(SURFACE + 1, Y1):
                put(x, y, z, AIR)

# ---------------------------------------------------------------- 3 天秤の岩 (道の内に二枚の皿の場所、真ん中の柱と梁、先の高い柱に欠片)
BA = 272.0
by = int(round(float(path_top((BA - A_START) / (A_END - A_START)))))
wx, wz = at_angle(BA, -12.5)
ex, ez = at_angle(BA, -23.5)
px_, pz_ = at_angle(BA, -18.0)
hx, hz = at_angle(BA, -31.5)
mark("plate_w", round(wx), by, round(wz))
mark("plate_e", round(ex), by, round(ez))
mark("pivot", round(px_), by + 12, round(pz_))
wpx, wpz = at_angle(BA - 3.0, -7.0)
mark("weight_pile", round(wpx), by + 1, round(wpz))
# 皿の動く筋は空ける (上下 7)
for (qx, qz) in ((wx, wz), (ex, ez)):
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            x, z = round(qx) + dx, round(qz) + dz
            if math.hypot(dx, dz) <= 3.4:
                for y in range(by - 8, by + 9):
                    put(x, y, z, AIR)
                write(x, z)
# 真ん中の柱 (月の面から梁の下まで) と、梁の台
for dx in range(-1, 2):
    for dz in range(-1, 2):
        x, z = round(px_) + dx, round(pz_) + dz
        for y in range(SURFACE + 1, by + 12):
            put(x, y, z, PB_BRICKS if (y // 4) % 2 else PBLACKSTONE)
        write(x, z)
put(round(px_), by + 12, round(pz_), CH_PB)
# 高い柱 (皿の水平から 14 上。上が欠片の台)
hy = by + 14
for dx in range(-3, 4):
    for dz in range(-3, 4):
        x, z = round(hx) + dx, round(hz) + dz
        if math.hypot(dx, dz) <= 2.7:
            for y in range(SURFACE + 1, hy + 1):
                put(x, y, z, wall_mat(y, x - X0, z - Z0, hsh(x, y, z), hy))
            write(x, z)
mark("shard_balance", round(hx), hy + 1, round(hz))
for xi in range(NX):
    for zi in range(NZ):
        x, z = X0 + xi, Z0 + zi
        if 3.0 < math.hypot(x - hx, z - hz) < 9.0 and KIND[xi, zi] == 4:
            for y in range(SURFACE + 1, Y1):
                put(x, y, z, AIR)

# ---------------------------------------------------------------- 頂の門 (道の終わり。塊の西の面に彫る) と主の間
gx, gz = at_angle(A_GATE, -4.5)
gy = int(round(float(path_top((A_GATE - A_START) / (A_END - A_START)))))
mark("cliff_gate", round(gx), gy + 1, round(gz))
# 門の前と門の奥を空ける (幅 5、高さ 6)
gdx, gdz = math.cos(math.radians(A_GATE + 90)), math.sin(math.radians(A_GATE + 90))
for i in range(-3, 3):
    for w in range(-2, 3):
        x = round(gx + gdx * i + (-gdz) * w)
        z = round(gz + gdz * i + gdx * w)
        for y in range(gy + 1, gy + 7):
            put(x, y, z, AIR)
        put(x, gy, z, PB_BRICKS)
        write(x, z)
# 門の枠 (磨いた黒石)
for w in (-3, 3):
    for i in range(-1, 2):
        x = round(gx + gdx * i + (-gdz) * w)
        z = round(gz + gdz * i + gdx * w)
        for y in range(gy + 1, gy + 7):
            put(x, y, z, PB_BRICKS)
for w in range(-3, 4):
    x = round(gx + (-gdz) * w)
    z = round(gz + gdx * w)
    put(x, gy + 6, z, CH_PB)

# 主の間: 皿 (床 80、半径 14)。皿の縁は銀 (鉄と石英)、刻みの輪、真ん中に支点の印。皿の下は柱 (半径 9) だけ
for dx in range(-ARENA_R - 1, ARENA_R + 2):
    for dz in range(-ARENA_R - 1, ARENA_R + 2):
        x, z = round(AX) + dx, round(AZ) + dz
        d = math.hypot(dx, dz)
        if d > ARENA_R + 0.5:
            continue
        write(x, z)
        for y in range(SURFACE + 1, Y1):
            if y > ARENA_Y:
                put(x, y, z, AIR)
            elif y > ARENA_Y - 3:
                ring = int(d)
                if y == ARENA_Y:
                    s = (IRON if d > ARENA_R - 0.8 else SQUARTZ if ring in (4, 9) else CH_PB if d < 1.5
                         else PB_BRICKS if (ring % 2) else POL_DS)
                else:
                    s = PBLACKSTONE if d > ARENA_R - 1.5 else DEEPSLATE
                put(x, y, z, s)
            elif d <= 9.0:
                put(x, y, z, wall_mat(y, x - X0, z - Z0, hsh(x, y, z), ARENA_Y))
            elif y <= 64:
                # 皿の縁の下は堀と同じ底 (柱と堀のあいだに深い穴を作らない)
                put(x, y, z, SBASALT if y < 64 else POWDER_G)
            else:
                put(x, y, z, AIR)
MARKS["arena_center"] = [round(AX), ARENA_Y + 1, round(AZ)]
# 入口 (門の側) と主の出る所 (反対側)
ux_, uz_ = (gx - AX), (gz - AZ)
ul = math.hypot(ux_, uz_)
ux_, uz_ = ux_ / ul, uz_ / ul
mark("arena_entry", round(AX + ux_ * 10.5), ARENA_Y + 1, round(AZ + uz_ * 10.5))
mark("arena_back", round(AX + ux_ * 13.0), ARENA_Y + 1, round(AZ + uz_ * 13.0))
mark("lord_spawn", round(AX - ux_ * 6.0), ARENA_Y + 1, round(AZ - uz_ * 6.0))
# 堀の底 (落ちたら Java が入口へ戻す。底は暗い玄武岩)
for xi in range(NX):
    for zi in range(NZ):
        if KIND[xi, zi] == 7:
            x, z = X0 + xi, Z0 + zi
            for y in range(SURFACE + 1, 65):
                put(x, y, z, SBASALT if y < 64 else (POWDER_G if hsh(x, 9, z) % 3 else GRAVEL))
            for y in range(65, Y1):
                put(x, y, z, AIR)

# ---------------------------------------------------------------- 道しるべ (道の内の縁に 25 ごと、石積みと魂の灯り)
for a in np.arange(A_START + 8, A_GATE - 4, 11.0):
    x, z = at_angle(float(a), -7.2)
    x, z = round(x), round(z)
    t = top_at(x, z)
    if get(x, t, z) in (TUFF_SLAB, POL_TUFF_SLAB):
        t -= 1
    put(x, t + 1, z, TUFF_WALL)
    put(x, t + 2, z, LANTERN)
    write(x, z)

# ---------------------------------------------------------------- 飾り: 段々と月の面の転がる岩・紫水晶の芽
rng = np.random.default_rng(0x434C4946)
rocks = buds = 0
for _ in range(900):
    xi, zi = int(rng.integers(0, NX)), int(rng.integers(0, NZ))
    x, z = X0 + xi, Z0 + zi
    if KIND[xi, zi] != 4 or rocks >= 26:
        continue
    t = top_at(x, z)
    if get(x, t + 1, z) != AIR:
        continue
    rr = 1.2 + rng.random() * 1.2
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(0, 3):
                if (dx / rr) ** 2 + (dz / rr) ** 2 + ((dy - 0.3) / (rr * 0.8)) ** 2 <= 1.0 and get(x + dx, t + 1 + dy, z + dz) == AIR:
                    put(x + dx, t + 1 + dy, z + dz, TUFF if (dx + dy) % 3 else ANDESITE)
    rocks += 1
for _ in range(1500):
    xi, zi = int(rng.integers(0, NX)), int(rng.integers(0, NZ))
    x, z = X0 + xi, Z0 + zi
    if KIND[xi, zi] not in (1, 4) or buds >= 30:
        continue
    t = top_at(x, z)
    if get(x, t + 1, z) == AIR and get(x, t, z) not in (AIR, TUFF_SLAB, POL_TUFF_SLAB, BUD):
        put(x, t + 1, z, BUD)
        buds += 1

# ---------------------------------------------------------------- 書き出し (崖の列だけ。列の中は y 12 から上を全部、空気も含めて)
LOW = SURFACE - 3
runs = []
for yi in range(NY):
    y = Y0 + yi
    if y < LOW:
        continue
    for zi in range(NZ):
        xi = 0
        while xi < NX:
            if not WRITE[xi, zi]:
                xi += 1
                continue
            x0 = xi
            seq = []
            while xi < NX and WRITE[xi, zi]:
                s = int(vol[xi, yi, zi])
                if seq and seq[-1][0] == s:
                    seq[-1][1] += 1
                else:
                    seq.append([s, 1])
                xi += 1
            if y > SURFACE and len(seq) == 1 and seq[0][0] == AIR and y > int(TOPI.max()) + 8:
                continue
            runs.append([y, Z0 + zi, X0 + x0] + [v for p in seq for v in p])

data = {"note": "build_moon_cliff.py が書く。手で直さない", "palette": PALETTE, "runs": runs, "marks": MARKS,
        "rect": [X0, Z0, X1, Z1], "columns": int(WRITE.sum())}
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
print("cliff", os.path.getsize(OUT) // 1024, "KB", len(runs), "runs", int(WRITE.sum()), "columns", "rocks", rocks, "buds", buds)
print("marks", MARKS)

# ---------------------------------------------------------------- 見る絵
if "--preview" in sys.argv:
    from PIL import Image
    COL = {AIR: (0, 0, 0), POWDER: (190, 194, 202), POWDER_G: (130, 134, 144), POWDER_W: (226, 228, 232), TUFF: (110, 110, 112),
           ANDESITE: (136, 136, 136), SBASALT: (72, 72, 78), BASALT: (60, 60, 66), BLACKSTONE: (40, 36, 42), CALCITE: (220, 224, 220),
           GRAVEL: (120, 116, 112), PBLACKSTONE: (54, 50, 58), PB_BRICKS: (48, 44, 52), CH_PB: (60, 56, 66), SQUARTZ: (236, 230, 222),
           POL_DS: (70, 70, 76), DS_TILES: (64, 64, 70), IRON: (220, 220, 226), DEEPSLATE: (70, 70, 76), AMETHYST: (150, 100, 200),
           BUD: (190, 140, 240), TUFF_SLAB: (120, 120, 122), POL_TUFF_SLAB: (126, 128, 126), LANTERN: (120, 220, 255),
           TUFF_WALL: (110, 110, 112)}
    top = np.zeros((NZ, NX, 3), np.uint8)
    for xi in range(NX):
        for zi in range(NZ):
            for yi in range(NY - 1, -1, -1):
                s = int(vol[xi, yi, zi])
                if s != AIR:
                    c = np.array(COL.get(s, (255, 0, 255)), float)
                    shade = 0.35 + 0.65 * (yi / NY)
                    top[zi, xi] = np.clip(c * shade * 1.25, 0, 255)
                    break
    im = Image.fromarray(top).resize((NX * 4, NZ * 4), Image.NEAREST)
    from PIL import ImageDraw
    d = ImageDraw.Draw(im)
    for k, (mx, my, mz) in MARKS.items():
        if k.startswith(("shard", "camp", "cliff_gate", "arena_entry", "plate", "ledge", "lord")):
            px, pz = (mx - X0) * 4 + 2, (mz - Z0) * 4 + 2
            d.ellipse((px - 4, pz - 4, px + 4, pz + 4), outline=(255, 120, 80), width=2)
            d.text((px + 6, pz - 6), k, fill=(255, 220, 120))
    os.makedirs(REVIEW, exist_ok=True)
    im.save(os.path.join(REVIEW, "cliff_top.png"))
    # 道に沿った断面 (弧の内 4 の線を広げて並べる)
    W = 260
    cut = np.zeros((NY, W, 3), np.uint8)
    for i in range(W):
        a = A_START - 10 + (A_END - A_START + 14) * i / (W - 1)
        x, z = at_angle(a, -4.0)
        xi, zi = int(round(x)) - X0, int(round(z)) - Z0
        if 0 <= xi < NX and 0 <= zi < NZ:
            for yi in range(NY):
                cut[NY - 1 - yi, i] = COL.get(int(vol[xi, yi, zi]), (255, 0, 255))
    Image.fromarray(cut).resize((W * 4, NY * 4), Image.NEAREST).save(os.path.join(REVIEW, "cliff_cut.png"))
    print("preview written")
