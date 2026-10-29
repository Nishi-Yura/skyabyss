# -*- coding: utf-8 -*-
"""六章「月の核」の間を彫る (docs/design/46 の 4.7)。

  PYTHONIOENCODING=utf-8 python -I tools/gen/world/build_moon_core.py [--preview]
      -> src/main/resources/data/skyabyss/moon/core.json   (Java: story/ch6/core/MoonCore が流し込む)
         docs/review/moon/core_cut.png                     (--preview: 南北の切り口の絵)

座標は月の世界の座標そのまま (着く所 = (0, 12, 0))。
  - 核の門: 着く所の北 8 (0, 12, -8)。印だけ (黒石の台と星の門は、まわりのクレーターを壊さないよう Java が置く)。四人の前の自分が立つ印
  - 核の間: 中心 (0, -32, -40)・半径 28 の球の空洞。壁は厚み 4 (深層岩・凝灰岩・方解石の筋、内の面に紫水晶と光る星の粒)。
    赤道の高さに壁から張り出す石英の輪 (天球の輪、幅 2)。底は闇 (黒いコンクリート)
  - 床: 球の真ん中の下に浮く丸い床 (半径 16、上面 y -44)。下は逆さの円錐の岩。床に銀の輪と星図、東西南北の縁に四つの台 (前の自分たちの立つ所)。
    南の縁に入口の印と帰り口
"""
from __future__ import annotations

import json
import math
import os
import sys

import numpy as np
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "skyabyss", "moon", "core.json")
REVIEW = os.path.join(ROOT, "docs", "review", "moon")

SURFACE = 11
CX, CY, CZ = 0, -32, -40
R = 28
SHELL = 4
FLOOR_Y = -44
FLOOR_R = 16
X0, X1 = CX - R - SHELL - 1, CX + R + SHELL + 1
Z0, Z1 = CZ - R - SHELL - 1, CZ + R + SHELL + 1
Y0, Y1 = CY - R - SHELL - 1, CY + R + SHELL
NX, NY, NZ = X1 - X0 + 1, Y1 - Y0 + 1, Z1 - Z0 + 1
GATE = (0, SURFACE + 1, -8)

PALETTE = ["minecraft:air"]


def P(name):
    PALETTE.append(name)
    return len(PALETTE) - 1


AIR = 0
TUFF = P("minecraft:tuff")
DEEPSLATE = P("minecraft:deepslate")
C_DEEPSLATE = P("minecraft:cobbled_deepslate")
CALCITE = P("minecraft:calcite")
SBASALT = P("minecraft:smooth_basalt")
BLACKSTONE = P("minecraft:blackstone")
BLACK_CONC = P("minecraft:black_concrete")
AMETHYST = P("minecraft:amethyst_block")
BUD = P("minecraft:medium_amethyst_bud[facing=up]")
SEA = P("minecraft:sea_lantern")
SQUARTZ = P("minecraft:smooth_quartz")
QBRICKS = P("minecraft:quartz_bricks")
CH_QUARTZ = P("minecraft:chiseled_quartz_block")
PBLACKSTONE = P("minecraft:polished_blackstone")
PB_BRICKS = P("minecraft:polished_blackstone_bricks")
CH_PB = P("minecraft:chiseled_polished_blackstone")
PB_WALL = P("minecraft:polished_blackstone_wall")
LANTERN = P("minecraft:soul_lantern[hanging=false]")
END_ROD = P("minecraft:end_rod[facing=up]")
POL_DIORITE = P("minecraft:polished_diorite")
IRON_CHAIN = P("minecraft:chain[axis=y]")

vol = np.zeros((NX, NY, NZ), dtype=np.int16)
XS = np.arange(X0, X1 + 1)[:, None, None]
YS = np.arange(Y0, Y1 + 1)[None, :, None]
ZS = np.arange(Z0, Z1 + 1)[None, None, :]
D = np.sqrt((XS - CX) ** 2 + (YS - CY) ** 2 + (ZS - CZ) ** 2)
rng = np.random.default_rng(0x434F5245)
noise = ndimage.zoom(rng.random((NX // 4 + 3, NY // 4 + 3, NZ // 4 + 3)), 4, order=1)[:NX, :NY, :NZ]

MARKS = {}


def mark(name, x, y, z):
    MARKS[name] = [int(x), int(y), int(z)]


def put(x, y, z, s):
    xi, yi, zi = x - X0, y - Y0, z - Z0
    if 0 <= xi < NX and 0 <= yi < NY and 0 <= zi < NZ:
        vol[xi, yi, zi] = s


def base_state(y):
    if y <= 8:
        return TUFF
    if y <= SURFACE:
        return P_POWDER
    return AIR


P_POWDER = P("minecraft:light_gray_concrete_powder")

# ---------------------------------------------------------------- 殻と空洞
shell = (D > R) & (D <= R + SHELL + 0.5)
inner = D <= R
# 殻の材質: 縦の層と筋
for xi, yi, zi in zip(*np.nonzero(shell)):
    y = Y0 + yi
    n = noise[xi, yi, zi]
    if n > 0.78:
        m = CALCITE
    elif n > 0.66:
        m = SBASALT
    elif y < CY - 10:
        m = BLACKSTONE if n < 0.3 else DEEPSLATE
    elif y < CY + 8:
        m = DEEPSLATE if n < 0.45 else C_DEEPSLATE
    else:
        m = TUFF
    vol[xi, yi, zi] = m
# 殻の外は月の地面 (凝灰岩) のまま: 流し込まない (base_state と同じ列は書かない)
for xi, yi, zi in zip(*np.nonzero(~shell & ~inner)):
    vol[xi, yi, zi] = base_state(Y0 + yi)
vol[inner] = AIR
# 内の面: 紫水晶の塊と芽、星の粒 (海の灯り)
surf = inner & ndimage.binary_dilation(shell, iterations=1)
k = 0
for xi, yi, zi in zip(*np.nonzero(surf)):
    k += 1
    x, y, z = X0 + xi, Y0 + yi, Z0 + zi
    h = (x * 73856093 ^ y * 19349663 ^ z * 83492791) & 0xFFFF
    if h % 37 == 0:
        # 星の粒: 殻の内の面に埋める
        put(x, y, z, SEA)
    elif h % 23 == 0 and y < CY:
        put(x, y, z, AMETHYST)
    elif h % 29 == 0 and y < CY and vol[xi, yi - 1, zi] != AIR:
        put(x, y, z, BUD)
# 底は闇 (球の下の 6 を黒く)
for xi, yi, zi in zip(*np.nonzero(shell)):
    if Y0 + yi < CY - R + 4:
        vol[xi, yi, zi] = BLACK_CONC

# ---------------------------------------------------------------- 天球の輪 (赤道の高さ、壁から 2 張り出す石英の輪)
for x in range(CX - R, CX + R + 1):
    for z in range(CZ - R, CZ + R + 1):
        d = math.hypot(x - CX, z - CZ)
        if R - 2.6 <= d <= R + 0.5:
            put(x, CY, z, CH_QUARTZ if int(math.degrees(math.atan2(z - CZ, x - CX)) + 360) % 30 < 4 else QBRICKS)
            if d > R - 1.2:
                put(x, CY - 1, z, SQUARTZ)

# ---------------------------------------------------------------- 床 (浮く丸い床と下の逆さ円錐)
for x in range(CX - FLOOR_R - 1, CX + FLOOR_R + 2):
    for z in range(CZ - FLOOR_R - 1, CZ + FLOOR_R + 2):
        d = math.hypot(x - CX, z - CZ)
        if d > FLOOR_R + 0.4:
            continue
        a = math.degrees(math.atan2(z - CZ, x - CX))
        ring = abs(d - 6) < 0.55 or abs(d - 12) < 0.55 or abs(d - FLOOR_R) < 0.7
        spoke = d > 6.5 and d < 11.5 and min(abs(((a + 360) % 90) - 45), 45) < 2.5 and False
        if d < 2.2:
            top = CH_QUARTZ
        elif ring:
            top = SQUARTZ
        elif spoke:
            top = QBRICKS
        else:
            top = PB_BRICKS if (int(d) + int((a + 180) / 12)) % 2 else PBLACKSTONE
        put(x, FLOOR_Y, z, top)
        depth = int(round(3 + (FLOOR_R - d) * 0.9 + noise[x - X0, FLOOR_Y - Y0, z - Z0] * 2))
        for y in range(FLOOR_Y - depth, FLOOR_Y):
            put(x, y, z, SBASALT if (y + x + z) % 5 else BLACKSTONE)
# 床の下の光の根 (床の真ん中から垂れる鎖と光)
for y in range(FLOOR_Y - 20, FLOOR_Y - 14):
    put(CX, y, CZ, IRON_CHAIN)
put(CX, FLOOR_Y - 21, CZ, SEA)
# 星図の線 (床の輪の間に、点と点を結ぶ明るい筋)
stars = [(-9, 3), (-7, -5), (-1, -9), (6, -7), (9, 1), (5, 8), (-3, 9)]
for (ax, az), (bx, bz) in zip(stars, stars[1:] + stars[:1]):
    n = max(abs(bx - ax), abs(bz - az))
    for i in range(n + 1):
        x = round(ax + (bx - ax) * i / n)
        z = round(az + (bz - az) * i / n)
        if abs(math.hypot(x, z) - 6) > 0.8 and abs(math.hypot(x, z) - 12) > 0.8:
            put(CX + x, FLOOR_Y, CZ + z, POL_DIORITE)
for sx, sz in stars:
    put(CX + sx, FLOOR_Y, CZ + sz, SEA)

# 四つの台 (東西南北の縁の内、半径 13。2 × 2 の一段高い台と灯り)
SEATS = {"seat_valley": (0, -1), "seat_cliff": (1, 0), "seat_field": (0, 1), "seat_rift": (-1, 0)}
for name, (ux, uz) in SEATS.items():
    sx, sz = CX + ux * 13, CZ + uz * 13
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            put(sx + dx, FLOOR_Y, sz + dz, CH_PB if dx == 0 and dz == 0 else PB_BRICKS)
    mark(name, sx, FLOOR_Y + 1, sz)
    # 台の外の縁に灯り
    put(sx + ux * 2, FLOOR_Y + 1, sz + uz * 2, PB_WALL)
    put(sx + ux * 2, FLOOR_Y + 2, sz + uz * 2, LANTERN)
mark("core_center", CX, FLOOR_Y + 11, CZ)
mark("arena_center", CX, FLOOR_Y + 1, CZ)
# 入口 (床の南西の縁の内) と帰り口 (南の縁)。南の台 (野) を避けて斜めに
mark("arena_entry", CX - 7, FLOOR_Y + 1, CZ + 12)
mark("arena_back", CX - 10, FLOOR_Y + 1, CZ + 12)
mark("fall_floor", CX, FLOOR_Y - 8, CZ)

# ---------------------------------------------------------------- 核の門 (着く所の北 8)
gx, gy, gz = GATE
# 門と台は月の地面の上にあるので、まわりのクレーターを壊さないよう Java (MoonCore.furnish) が置く
mark("core_gate", gx, gy, gz)
mark("gate_front", gx, gy, gz + 3)
# 四人の立つ所 (門の手前の左右)
for i, (dx, dz) in enumerate([(-4, 1), (4, 1), (-3, 3), (3, 3)]):
    mark(f"gather_{i}", gx + dx, gy, gz + dz)

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
data = {"note": "build_moon_core.py が書く。手で直さない", "palette": PALETTE, "runs": runs, "marks": MARKS,
        "rect": [X0, Z0, X1, Z1], "center": [CX, CY, CZ], "radius": R, "floor": [FLOOR_Y, FLOOR_R]}
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
print("core", os.path.getsize(OUT) // 1024, "KB", len(runs), "runs", "marks", len(MARKS))

if "--preview" in sys.argv:
    from PIL import Image
    cut = np.zeros((NY, NX, 3), np.uint8)
    zi = CZ - Z0
    COL = {AIR: (8, 8, 14), SEA: (180, 220, 240), BLACK_CONC: (0, 0, 0), SQUARTZ: (236, 230, 222), QBRICKS: (232, 226, 218), CH_QUARTZ: (226, 220, 210),
           PB_BRICKS: (60, 52, 66), PBLACKSTONE: (54, 50, 58), CH_PB: (70, 60, 80), AMETHYST: (150, 100, 200), CALCITE: (220, 224, 220)}
    for xi in range(NX):
        for yi in range(NY):
            s = int(vol[xi, yi, zi])
            cut[NY - 1 - yi, xi] = COL.get(s, (100, 100, 110))
    Image.fromarray(cut).resize((NX * 6, NY * 6), Image.NEAREST).save(os.path.join(REVIEW, "core_cut.png"))
    print("preview written")
