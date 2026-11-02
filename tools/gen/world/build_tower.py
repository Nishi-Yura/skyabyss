# -*- coding: utf-8 -*-
"""超大型レイド「あいだの塔」の舞台「橋」(docs/design/54 の 2.4)。彫って data/skyabyss/tower/bridge.json に書く。
2026-11-01 の声で四つの階をやめ、Wynncraft の Annihilation のような「橋の先に座る巨大な主との一戦」だけにした。
座標は手元 (原点 = 橋の真ん中の上面、y 0)。TowerTerrain が区画の原点へずらして流し込む。

  橋        幅 15 (x -7..7)、南の入口の台 z 41..52 から北の広場 z -46 まで。白い石の上面、黒い下面、欄干と灯
  入口      印 entry (立つ所)・heal (癒しの輪)・return (帰りの天然磁石)
  真ん中の筋 三つの印の石 (sigil_1..3。z 24・4・-16。三つの印の光)
  側の台    左右に張り出した台 (eye_big_l・eye_big_r = z -10、eye_l_1/2・eye_r_1/2 = z 26・6。側の目の置き場)
  北の広場  x -20..20、z -33..-46 (手の置き場 hand_l・hand_r、癒し手の台 healer_l・healer_r、月の生まれる所 moon)
  玉座の段  広場の北 z -47..-79。主の座る所 boss (主の胸は橋より高い)。段の下へ鎖が垂れる
  まわり    橋の下は淵へ降りる支柱と鎖、上は白金の空に浮かぶかけら

  python -X utf8 tools/gen/world/build_tower.py
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cloud_kit as K  # noqa: E402

OUT = K.ROOT / "src/main/resources/data/skyabyss/tower"
AIR = K.AIR
LIGHT = "minecraft:light[level=13,waterlogged=false]"
TOP = ["minecraft:smooth_quartz", "minecraft:quartz_bricks", "minecraft:calcite", "minecraft:polished_diorite"]
UNDER = ["minecraft:polished_blackstone", "minecraft:blackstone", "minecraft:polished_deepslate", "minecraft:deepslate_tiles"]


def top(x, z):
    n = K.hsh(x, z, 1101)
    return TOP[0] if n < 0.5 else TOP[1] if n < 0.75 else TOP[2] if n < 0.92 else TOP[3]


def under(x, y, z):
    n = K.hsh(x, y, z, 1102)
    return UNDER[0] if n < 0.4 else UNDER[1] if n < 0.7 else UNDER[2] if n < 0.88 else UNDER[3]


def slab_area(g, x0, x1, z0, z1, depth=3, rim=True):
    """白い上面と黒い下面の床 (縁は金)"""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            g.put(x, 0, z, "minecraft:gold_block" if rim and edge else top(x, z))
            for y in range(-depth, 0):
                g.put(x, y, z, under(x, y, z))
            for y in range(1, 30):
                g.put(x, y, z, AIR)


def disc(g, cx, cz, r, depth=4):
    for x in range(cx - r - 1, cx + r + 2):
        for z in range(cz - r - 1, cz + r + 2):
            d = math.hypot(x - cx, z - cz)
            if d > r + 0.4:
                continue
            g.put(x, 0, z, "minecraft:gold_block" if d > r - 0.6 else top(x, z))
            dd = int(2 + depth * (1 - d / (r + 0.5)))
            for y in range(-dd, 0):
                g.put(x, y, z, under(x, y, z))
            for y in range(1, 30):
                g.put(x, y, z, AIR)


def fragment(g, cx, cy, cz, size, salt):
    R = int(size) + 1
    depth = int(size * 2.2)
    for x in range(cx - R, cx + R + 1):
        for z in range(cz - R, cz + R + 1):
            d = math.hypot(x - cx, z - cz) + 0.6 * K.hsh(x, z, salt)
            if d > size:
                continue
            g.put(x, cy, z, top(x, z))
            dd = int(depth * (1 - d / size) + 1)
            for y in range(cy - dd, cy):
                g.put(x, y, z, under(x, y, z))


def bridge():
    g = K.Grid(-48, 48, -96, 64, -46, 64)
    # 橋の本体 (z -32 .. 40)
    slab_area(g, -7, 7, -32, 40, depth=3, rim=False)
    for z in range(-32, 41):
        for x in (-7, 7):
            g.put(x, 0, z, "minecraft:chiseled_quartz_block" if z % 6 == 0 else "minecraft:quartz_bricks")
            g.put(x, 1, z, "minecraft:quartz_pillar[axis=y]" if z % 6 == 0 else "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]")
            if z % 6 == 0:
                g.put(x, 2, z, "minecraft:lantern[hanging=false,waterlogged=false]")
        # 真ん中の筋 (金と方解石の帯)
        g.put(0, 0, z, "minecraft:gold_block" if z % 4 == 0 else "minecraft:calcite")
        for x in (-1, 1):
            g.put(x, 0, z, "minecraft:calcite")
    # 橋の下の弧と、淵へ降りる支柱 (z 16 ごと)
    for z in range(-32, 41):
        for x in range(-7, 8):
            dy = int(3 + 6 * (1 - abs(math.cos(math.pi * (z + 32) / 16.0))) ** 2)
            for y in range(-dy, -3):
                if abs(x) >= 5 or y == -dy:
                    g.put(x, y, z, under(x, y, z))
    for zc in range(-32, 41, 16):
        for x in range(-4, 5):
            for z in range(zc - 2, zc + 3):
                if abs(x) == 4 or abs(z - zc) == 2:
                    for y in range(-44, -3):
                        if K.hsh(x, y, z, 1103) < 0.97 or y > -20:
                            g.put(x, y, z, under(x, y, z))
        for y in range(-44, -6):
            g.put(0, y, zc, "minecraft:chain[axis=y,waterlogged=false]")
    # 入口の台 (休み場) と帰りの天然磁石
    slab_area(g, -11, 11, 41, 52)
    for x in range(-11, 12):
        for z in range(41, 53):
            d = math.hypot(x, z - 46)
            if d < 1.2:
                g.put(x, 0, z, "minecraft:gold_block")
            elif d < 2.8:
                g.put(x, 0, z, "minecraft:calcite")
    g.put(0, 0, 51, "minecraft:lodestone")
    g.marks["entry"] = [0, 1, 46]
    g.marks["heal"] = [0, 1, 46]
    g.marks["return"] = [0, 0, 51]
    # 入口の門 (白い大きな弧)
    for a in range(0, 181, 3):
        r = math.radians(a)
        x = int(round(math.cos(r) * 10))
        y = int(round(math.sin(r) * 14))
        for zz in (40, 41):
            g.put(x, y, zz, "minecraft:quartz_pillar[axis=y]" if a in (0, 180) or y < 2 else "minecraft:chiseled_quartz_block")
    # 真ん中の筋の三つの印の石
    for i, z in enumerate((24, 4, -16)):
        for x in range(-2, 3):
            for zz in range(z - 2, z + 3):
                if abs(x) == 2 or abs(zz - z) == 2:
                    g.put(x, 0, zz, "minecraft:polished_blackstone")
        g.put(0, 0, z, "minecraft:crying_obsidian")
        g.marks["sigil_%d" % (i + 1)] = [0, 1, z]
    # 側の台 (側の目)
    sides = [("eye_l_1", -1, 26, 4), ("eye_r_1", 1, 26, 4), ("eye_l_2", -1, 6, 4), ("eye_r_2", 1, 6, 4), ("eye_big_l", -1, -10, 6), ("eye_big_r", 1, -10, 6)]
    for name, s, z, r in sides:
        cx = s * (7 + r + 2)
        disc(g, cx, z, r)
        for x in range(min(s * 7, cx), max(s * 7, cx) + 1):
            for zz in (z - 1, z, z + 1):
                g.put(x, 0, zz, top(x, zz))
                g.put(x, -1, zz, under(x, -1, zz))
                for y in range(1, 4):
                    g.put(x, y, zz, AIR)
        g.put(cx, 1, z, "minecraft:polished_blackstone")
        g.put(cx, 2, z, "minecraft:polished_blackstone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
        g.marks[name] = [cx, 3, z]
    # 北の広場 (手の置き場・癒し手の台・月の生まれる所)
    slab_area(g, -20, 20, -46, -33)
    for x in range(-20, 21):
        for z in range(-46, -32):
            if (x + z) % 7 == 0:
                g.put(x, 0, z, "minecraft:polished_blackstone")
    g.marks["hand_l"] = [-13, 1, -40]
    g.marks["hand_r"] = [13, 1, -40]
    for name, s in (("healer_l", -1), ("healer_r", 1)):
        disc(g, s * 26, -40, 4)
        for x in range(min(s * 20, s * 26), max(s * 20, s * 26) + 1):
            for zz in (-41, -40, -39):
                g.put(x, 0, zz, top(x, zz))
        g.marks[name] = [s * 26, 1, -40]
    g.marks["moon"] = [0, 6, -38]
    g.marks["bridge_n"] = [0, 1, -30]
    g.marks["bridge_s"] = [0, 1, 38]
    # 玉座の段 (広場の北。主が座る。段の下へ鎖)
    for step in range(8):
        z0 = -47 - step * 4
        h = step * 3
        w = 26 - step
        for x in range(-w, w + 1):
            for z in range(z0 - 3, z0 + 1):
                for y in range(-12, h + 1):
                    if y == h:
                        g.put(x, y, z, "minecraft:gold_block" if abs(x) == w or z == z0 else "minecraft:polished_blackstone_bricks")
                    elif abs(x) == w or y > h - 3:
                        g.put(x, y, z, under(x, y, z))
    for x in range(-18, 19, 6):
        for y in range(-44, -12):
            g.put(x, y, -52, "minecraft:chain[axis=y,waterlogged=false]")
    # 玉座の左右の柱 (高く。上に天秤の印の台)
    for s in (-1, 1):
        for y in range(0, 52):
            for x in range(s * 30 - 2, s * 30 + 3):
                for z in range(-66, -61):
                    if abs(x - s * 30) == 2 or abs(z + 64) == 2:
                        g.put(x, y, z, "minecraft:quartz_pillar[axis=y]" if y % 12 else "minecraft:gold_block")
        for y in range(52, 55):
            for x in range(s * 30 - 3, s * 30 + 4):
                for z in range(-67, -60):
                    g.put(x, y, z, "minecraft:chiseled_quartz_block")
    g.marks["boss"] = [0, 10, -64]
    # 灯り (見えない光)
    for z in range(-44, 52, 8):
        for x in (-5, 5):
            g.put(x, 3, z, LIGHT)
    # 上に浮かぶかけら
    for k in range(18):
        a = k / 18.0 * math.pi * 2
        fragment(g, int(math.cos(a) * 40), int(18 + 30 * K.hsh(k, 1, 1104)), int(-10 + math.sin(a) * 60), 2.0 + 3.5 * K.hsh(k, 2, 1104), 1110 + k)
    return g


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.json"):
        old.unlink()
    g = bridge()
    kb, cells, runs = g.write(OUT / "bridge.json", "tools/gen/world/build_tower.py が書く。手で直さない", {"floor": 0})
    print("bridge", kb, "KB", cells, "cells", runs, "runs")
