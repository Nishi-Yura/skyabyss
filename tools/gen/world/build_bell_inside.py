# -*- coding: utf-8 -*-
"""裏ボス「初代の空見」の舞台「鐘の内」(docs/design/53 の 4 章)。彫って data/skyabyss/finale/founder.json に書く (FinaleWorld の版 8 で流し込む)。

  雲海の次元の、島から遠い南 (真ん中 (0, 1200)、床 y 150)。閉じた大鐘の内側で、空は見えない。
  床      鐘の間の古い石の床が、鐘の口に浮く (半径 16)。真ん中に月への門と同じ天然磁石の跡 (印 center)、
          縁の内に七つの刻み (歌の七行。印 verse_1..7、北から時計回り)。南の縁に帰りの台 (天然磁石。印 return)
  壁      内から見た青銅の鐘 (蝋引きの銅。緑青の帯 2 本、刻みの帯、金の口縁)。床の縁と壁のあいだは鐘の口の下の暗い空 (落ちたら戻す)
  舌      鐘の頭から鎖で吊った大きな舌 (床から 9〜13 の高さ。始祖が「みっつ」で跳び上がる所。印 clapper)
  灯り    見えない光のブロック (壁際の二段)
  印      arrive (着く所)・founder_spawn (始祖の出る所)・center・return・clapper・verse_1..7

  python -X utf8 tools/gen/world/build_bell_inside.py
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cloud_kit as K  # noqa: E402

OUT = K.ROOT / "src/main/resources/data/skyabyss/finale/founder.json"
F = 150
CX, CZ = 0, 1200
R_FLOOR = 16.4
g = K.Grid(CX - 26, CX + 26, CZ - 26, CZ + 26, F - 8, F + 40)

# 内の半径 (床からの高さ h → 半径)。口は広く、腰でしぼみ、肩で丸く閉じる
PROFILE = [(-4, 19.8), (-2, 19.4), (0, 19.0), (4, 17.6), (8, 16.2), (14, 14.6), (20, 13.9), (26, 13.2), (30, 12.0), (33, 9.6), (35, 6.4), (36, 3.0), (37, 0.0)]
CU = "minecraft:waxed_cut_copper"
CU2 = "minecraft:waxed_exposed_cut_copper"
CU3 = "minecraft:waxed_weathered_cut_copper"
VERD = "minecraft:waxed_oxidized_cut_copper"
CHIS = "minecraft:waxed_chiseled_copper"
GOLD = "minecraft:gold_block"


def radius(h):
    for (h0, r0), (h1, r1) in zip(PROFILE, PROFILE[1:]):
        if h0 <= h <= h1:
            t = (h - h0) / float(h1 - h0)
            return r0 + (r1 - r0) * t
    return None


def wall_block(x, h, z):
    """鐘の内の面。銅の色むらと縦の緑青の筋、緑青の帯 (h 7・h 25)、刻みの帯 (h 18〜19)、口の金"""
    if h <= -3:
        return GOLD
    if h in (7, 25):
        return VERD
    if h in (18, 19):
        a = (math.degrees(math.atan2(z - CZ, x - CX)) + 360) % 360
        return CHIS if int(a / 7.5) % 2 == 0 else GOLD if h == 18 and int(a / 7.5) % 8 == 1 else CU
    a = math.atan2(z - CZ, x - CX)
    streak = K.noise2(a * 20, 0, 1.6, 811)
    if streak > 0.78 and h < 30:
        return VERD if K.hsh(x, h, z, 812) < 0.6 else CU3
    n = K.hsh(x, h, z, 813)
    # 古い青銅: 新しい銅の色は少しだけ、くすんだ茶と緑がかった茶を主に
    return CU if n < 0.15 else CU2 if n < 0.6 else CU3


# ---------------------------------------------------------------- 壁 (厚み 2。上の段ほど内へ)
for h in range(-4, 38):
    r_in = radius(h)
    if r_in is None:
        continue
    y = F + h
    for x in range(CX - 24, CX + 25):
        for z in range(CZ - 24, CZ + 25):
            d = math.hypot(x - CX, z - CZ)
            if r_in <= 0.5:
                if d <= 3.2:
                    g.put(x, y, z, CU2)
                continue
            if r_in <= d <= r_in + 2.2:
                g.put(x, y, z, wall_block(x, h, z))
            elif d < r_in and h > 0:
                g.put(x, y, z, K.AIR)
# 鐘の頭 (冠): 上の閉じた所の外に金の環
for x in range(CX - 4, CX + 5):
    for z in range(CZ - 4, CZ + 5):
        d = math.hypot(x - CX, z - CZ)
        if d <= 3.6:
            g.put(x, F + 38, z, CU3 if d < 2.6 else GOLD)

# ---------------------------------------------------------------- 床 (鐘の間の古い石。下へ細る根)
for x in range(CX - 17, CX + 18):
    for z in range(CZ - 17, CZ + 18):
        d = math.hypot(x - CX, z - CZ)
        if d > R_FLOOR:
            continue
        depth = int(2 + 5 * (1 - d / R_FLOOR) + 2 * K.noise2(x, z, 3.0, 821))
        for y in range(F - depth, F):
            g.put(x, y, z, "minecraft:deepslate_bricks" if K.hsh(x, y, z, 822) < 0.6 else "minecraft:cracked_deepslate_bricks")
        n = K.hsh(x, z, 823)
        b = "minecraft:stone_bricks" if n < 0.55 else "minecraft:cracked_stone_bricks" if n < 0.75 else "minecraft:polished_andesite" if n < 0.92 else "minecraft:mossy_stone_bricks"
        if d > R_FLOOR - 1.2:
            b = "minecraft:polished_deepslate"
        elif 11.6 < d < 12.6:
            b = "minecraft:polished_andesite"
        elif d < 1.0:
            b = "minecraft:lodestone"
        elif d < 2.2:
            b = "minecraft:chiseled_stone_bricks"
        elif d < 2.9:
            b = "minecraft:polished_deepslate"
        g.put(x, F, z, b)

# 七つの刻み (縁の内、北から時計回り。歌の一行ずつ)
for i in range(7):
    a = -math.pi / 2 + i * 2 * math.pi / 7
    x = CX + round(math.cos(a) * 14.0)
    z = CZ + round(math.sin(a) * 14.0)
    for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        g.put(x + dx, F, z + dz, "minecraft:chiseled_deepslate" if (dx, dz) != (0, 0) else "minecraft:waxed_copper_bulb[lit=false,powered=false]")
    g.marks["verse_%d" % (i + 1)] = [x, F + 1, z]

# 帰りの台 (南の縁)
g.put(CX, F, CZ + 15, "minecraft:lodestone")
g.marks["return"] = [CX, F, CZ + 15]

# ---------------------------------------------------------------- 舌 (鎖と大きな玉)
for y in range(F + 14, F + 38):
    g.put(CX, y, CZ, "minecraft:chain[axis=y,waterlogged=false]")
for x in range(CX - 3, CX + 4):
    for z in range(CZ - 3, CZ + 4):
        for y in range(F + 8, F + 15):
            d = math.sqrt((x - CX) ** 2 + (z - CZ) ** 2 + ((y - (F + 11)) * 1.15) ** 2)
            if d <= 2.6:
                g.put(x, y, z, VERD if y == F + 11 else CU3 if K.hsh(x, y, z, 831) < 0.5 else CU2)
g.marks["clapper"] = [CX, F + 15, CZ]

# ---------------------------------------------------------------- 灯り (見えない光。壁際の二段)
for k in range(12):
    a = k * math.pi / 6
    for h, r, lv in ((3, 15.0, 11), (16, 12.0, 9)):
        g.put(CX + round(math.cos(a) * r), F + h, CZ + round(math.sin(a) * r), "minecraft:light[level=%d,waterlogged=false]" % lv)
for k in range(6):
    a = k * math.pi / 3 + 0.3
    g.put(CX + round(math.cos(a) * 6), F + 5, CZ + round(math.sin(a) * 6), "minecraft:light[level=8,waterlogged=false]")

g.marks["center"] = [CX, F + 1, CZ]
g.marks["arrive"] = [CX, F + 1, CZ + 12]
g.marks["founder_spawn"] = [CX, F + 1, CZ - 7]
kb, cells, runs = g.write(OUT, "tools/gen/world/build_bell_inside.py が書く。手で直さない", {"floor": F})
print("founder", kb, "KB", cells, "cells", runs, "runs")
