# -*- coding: utf-8 -*-
"""終章「雲海」の島を彫る共通の部品 (docs/design/50)。build_cloud_*.py から読む。

  Grid      … セルの入れ物 (put / get / runs の書き出し)
  island()  … 雲の上に浮く島 (上は草と土、縁は苔と方解石、下は層の筋のある逆さの岩の根と鍾乳石、縁の下に垂れる蔓)
  billows() … 島のまわりの低い雲のうねり (雲の海 y 36〜39 の上)
  ruin_house() … 崩れた石と木組みの家 (前の都の家並み)
書き出しの書式は月の区画と同じ (palette・runs [y, z, x0, (色, 横の数)...]・marks・rect)。FinaleTerrain が読む。
"""
import json
import math
import random
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
AIR = "minecraft:air"
CLOUD = "skyabyss:cloud"
GRASS = "minecraft:grass_block[snowy=false]"
STRATA = ["minecraft:calcite", "minecraft:diorite", "minecraft:tuff", "minecraft:andesite", "minecraft:calcite", "minecraft:smooth_basalt", "minecraft:diorite"]


def hsh(*a):
    h = 2166136261
    for v in a:
        h = ((h ^ (int(v) & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    h ^= h >> 13
    h = (h * 1274126177) & 0xFFFFFFFF
    return (h & 0xFFFFFF) / float(0x1000000)


def noise2(x, z, sc, s):
    fx, fz = x / sc, z / sc
    ix, iz = math.floor(fx), math.floor(fz)
    tx, tz = fx - ix, fz - iz
    tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
    v = 0.0
    for dx in (0, 1):
        for dz in (0, 1):
            w = (tx if dx else 1 - tx) * (tz if dz else 1 - tz)
            v += w * hsh(ix + dx, iz + dz, s)
    return v


class Grid:
    def __init__(self, x0, x1, z0, z1, y0, y1):
        self.x0, self.x1, self.z0, self.z1, self.y0, self.y1 = x0, x1, z0, z1, y0, y1
        self.cells = {}
        self.marks = {}
        # 島の上面 (x, z) → y と、島の縁までの近さ (水平の島の内側か)
        self.tops = {}
        self.edge = {}

    def put(self, x, y, z, b):
        if self.x0 <= x <= self.x1 and self.y0 <= y <= self.y1 and self.z0 <= z <= self.z1:
            self.cells[(x, y, z)] = b

    def get(self, x, y, z):
        return self.cells.get((x, y, z))

    def top(self, x, z):
        return self.tops.get((x, z))

    def write(self, out, note, extra=None):
        palette, index, runs = [], {}, []
        for y in range(self.y0, self.y1 + 1):
            for z in range(self.z0, self.z1 + 1):
                row = [self.cells.get((x, y, z)) for x in range(self.x0, self.x1 + 1)]
                if all(b is None for b in row):
                    continue
                cells = []
                i = 0
                while i < len(row):
                    b = row[i]
                    j = i
                    while j < len(row) and row[j] == b:
                        j += 1
                    if b is None:
                        idx = -1
                    else:
                        if b not in index:
                            index[b] = len(palette)
                            palette.append(b)
                        idx = index[b]
                    cells += [idx, j - i]
                    i = j
                runs.append([y, z, self.x0] + cells)
        data = {"note": note, "palette": palette, "runs": runs, "marks": self.marks, "rect": [self.x0, self.z0, self.x1, self.z1]}
        if extra:
            data.update(extra)
        out = Path(out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        return out.stat().st_size // 1024, len(self.cells), len(runs)


def island(g, cx, cz, rx, rz, ground, depth, seed, wobble=4.0, rim_rocks=0.03):
    """楕円の島。上面 ground、下の根の深さ depth (真ん中)。g.tops / g.edge に上面と縁までの近さを残す"""
    def edge_k(x, z):
        a = math.atan2((z - cz) / rz, (x - cx) / rx)
        w = wobble * (noise2(math.cos(a) * 6 + seed, math.sin(a) * 6 + seed, 2.2, seed) - 0.5) + 0.4 * wobble * (noise2(math.cos(a) * 14, math.sin(a) * 14, 2.0, seed + 1) - 0.5)
        d = math.hypot((x - cx) / rx, (z - cz) / rz)
        return d, 1 + w / max(rx, rz)

    for x in range(int(cx - rx - wobble - 2), int(cx + rx + wobble + 3)):
        for z in range(int(cz - rz - wobble - 2), int(cz + rz + wobble + 3)):
            d, lim = edge_k(x, z)
            if d > lim:
                continue
            k = max(0.0, 1 - d / lim)
            t = ground + int(round(0.9 * (noise2(x, z, 7.0, seed + 3) - 0.5) * 2 - (1 - k) * 1.2))
            bottom = t - int(round(4 + depth * k ** 1.4 + 6 * (noise2(x, z, 6.0, seed + 4) - 0.5) * k))
            rim = d > lim - 1.8 / max(rx, rz) * 1.0 and k < 0.07
            g.tops[(x, z)] = t
            g.edge[(x, z)] = k
            for y in range(bottom, t + 1):
                dt = t - y
                if dt == 0:
                    b = GRASS
                elif dt <= 2 and not rim:
                    b = "minecraft:dirt" if hsh(x, y, z, seed) < 0.85 else "minecraft:coarse_dirt"
                elif dt <= 2:
                    b = "minecraft:moss_block" if hsh(x, y, z, seed + 5) < 0.4 else "minecraft:calcite"
                else:
                    band = int((y + 3 * (noise2(x, z, 9.0, seed + 6) - 0.5)) // 3) % len(STRATA)
                    b = STRATA[band] if hsh(x, y, z, seed + 7) >= 0.08 else "minecraft:stone"
                g.put(x, y, z, b)
            if k > 0.8 and hsh(x, z, seed + 8) < 0.5:
                for kk in range(1, 2 + int(hsh(x, z, seed + 9) * 3)):
                    g.put(x, bottom - kk, z, "minecraft:dripstone_block")
                g.put(x, bottom - 4, z, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]")
            elif k < 0.12 and hsh(x, z, seed + 11) < 0.18:
                for kk in range(1, 2 + int(hsh(x, z, seed + 12) * 4)):
                    if g.get(x, bottom - kk, z) is None:
                        g.put(x, bottom - kk, z, "minecraft:vine[east=false,north=false,south=false,up=%s,west=false]" % ("true" if kk == 1 else "false"))
            # 縁の低い岩
            if k < 0.1 and hsh(x, z, seed + 21) < rim_rocks:
                hh = 1 + int(hsh(x, z, seed + 22) * 2)
                for y in range(t + 1, t + 1 + hh):
                    g.put(x, y, z, "minecraft:andesite" if hsh(x, y, z, seed + 23) < 0.4 else "minecraft:calcite")
                if hsh(x, z, seed + 25) < 0.5:
                    g.put(x, t + 1 + hh, z, "minecraft:moss_carpet")


def flowers(g, seed, keep=lambda x, z: False):
    for (x, z), t in g.tops.items():
        if keep(x, z) or g.get(x, t, z) != GRASS or g.get(x, t + 1, z) is not None:
            continue
        h = hsh(x, z, seed)
        if h < 0.04:
            g.put(x, t + 1, z, "minecraft:lily_of_the_valley")
        elif h < 0.07:
            g.put(x, t + 1, z, "minecraft:azure_bluet")
        elif h < 0.2:
            g.put(x, t + 1, z, "minecraft:short_grass")


def billows(g, seed, n, avoid):
    """雲の海の上の低い雲の塊 n 個。avoid(x, z) が真の所 (島の下) には置かない"""
    rnd = random.Random(seed)
    for _ in range(n):
        cx, cz = rnd.uniform(g.x0 + 6, g.x1 - 6), rnd.uniform(g.z0 + 6, g.z1 - 6)
        if avoid(cx, cz):
            continue
        rh, rv = rnd.uniform(4, 10), rnd.uniform(1.5, 3.5)
        for x in range(int(cx - rh) - 1, int(cx + rh) + 2):
            for z in range(int(cz - rh) - 1, int(cz + rh) + 2):
                for y in range(40, 40 + int(rv) + 2):
                    d = ((x - cx) / rh) ** 2 + ((y - 39.5) / rv) ** 2 + ((z - cz) / rh) ** 2
                    if d <= 1 + (noise2(x, z, 3.0, seed + 31) - 0.5) * 0.6 and g.get(x, y, z) is None:
                        g.put(x, y, z, CLOUD)


def ruin_house(g, x0, z0, w, d, seed, facing="south"):
    """崩れた家: 石の腰 (1)・漆喰と木組みの壁 (2〜5 の不揃い)・床・崩れた屋根の名残・中の物。足もとは g.top"""
    t = max(g.top(x, z) or 0 for x in range(x0, x0 + w) for z in range(z0, z0 + d))
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            for y in range(t - 2, t + 1):
                g.put(x, y, z, "minecraft:stone_bricks" if y == t else "minecraft:cobblestone")
            edge = x in (x0, x0 + w - 1) or z in (z0, z0 + d - 1)
            corner = x in (x0, x0 + w - 1) and z in (z0, z0 + d - 1)
            hmax = 2 + int(hsh(x, z, seed) * 4)
            door = (facing == "south" and z == z0 + d - 1 and x == x0 + w // 2) or (facing == "north" and z == z0 and x == x0 + w // 2)
            for y in range(t + 1, t + 7):
                if not edge:
                    g.put(x, y, z, AIR)
                    continue
                if door and y <= t + 2:
                    g.put(x, y, z, AIR)
                    continue
                if y - t > hmax and not corner:
                    break
                if y - t > hmax + 1:
                    break
                if corner:
                    b = "minecraft:stripped_spruce_log[axis=y]"
                elif y == t + 1:
                    b = "minecraft:stone_bricks" if hsh(x, y, z, seed + 1) < 0.7 else "minecraft:mossy_stone_bricks"
                elif y == t + 4:
                    b = "minecraft:stripped_spruce_log[axis=%s]" % ("x" if z in (z0, z0 + d - 1) else "z")
                else:
                    b = "minecraft:calcite" if hsh(x, y, z, seed + 2) < 0.8 else "minecraft:white_terracotta"
                    if y == t + 2 and (x + z) % 3 == 0 and not corner:
                        b = "minecraft:spruce_trapdoor[facing=%s,half=bottom,open=true,powered=false,waterlogged=false]" % ("south" if z == z0 + d - 1 else "north" if z == z0 else "east" if x == x0 + w - 1 else "west")
                g.put(x, y, z, b)
    # 崩れた屋根の名残 (片側だけ)
    for x in range(x0, x0 + w):
        if hsh(x, seed, 3) < 0.55:
            g.put(x, t + 5, z0, "minecraft:dark_oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]")
    # 中: 割れた樽・敷物・崩れた石
    g.put(x0 + 1, t + 1, z0 + 1, "minecraft:barrel[facing=up,open=false]")
    g.put(x0 + w - 2, t + 1, z0 + 1, "minecraft:cobblestone_slab[type=bottom,waterlogged=false]")
    g.put(x0 + w // 2, t + 1, z0 + d // 2, "minecraft:white_carpet")
