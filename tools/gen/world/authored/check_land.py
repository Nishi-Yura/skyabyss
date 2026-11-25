# -*- coding: utf-8 -*-
"""前もって組んだ土地を、ゲームを立てずに確かめる (ContinentAuthored と同じ読み方)。

    python -I tools/gen/world/authored/check_land.py tide [whale ...]

見ること (自己テスト continent・continent roads と同じ決まり):
  1. 道の線の上に 2 以上の段がない (道の両側とも組んだ列のとき)
  2. 地面に穴がない (±3 のどれよりも 2 以上低い列)。土地の列だけ数える
  3. 浮いた木がない (木の部品の塊で、下に地面も木もない列だけでできたもの)
"""
import gzip
import os
import json
import math
import struct
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[4]
OUT = Path(os.environ["SKYABYSS_AUTHORED_OUT"]) if os.environ.get("SKYABYSS_AUTHORED_OUT") else ROOT / "src" / "main" / "resources" / "data" / "skyabyss" / "continent" / "authored"
T = 256
SEA = 63
# 自己テスト continent の穴を見る所
WINDOWS = ((-324, 444), (1184, 224), (-512, 812), (300, 1100), (-100, -600))

GROUND_WORDS = ("dirt", "grass_block", "podzol", "mycelium", "stone", "granite", "diorite", "andesite", "tuff", "deepslate",
                "sand", "gravel", "moss_block", "snow_block", "calcite", "cobblestone", "dirt_path", "packed_mud", "clay", "mud",
                "bedrock", "terracotta", "blackstone", "basalt", "rooted_dirt", "polished_", "smooth_stone", "mud_bricks", "stone_bricks")
NOT_GROUND = ("stairs", "slab", "wall", "button", "pressure", "mud_brick")


def is_ground(state):
    b = state.split("[")[0].replace("minecraft:", "")
    if any(w in b for w in NOT_GROUND):
        return False
    return any(w in b for w in GROUND_WORDS)


def read_tile(land, tx, tz):
    p = OUT / land / f"{tx}_{tz}.bin"
    if not p.exists():
        return None
    b = gzip.decompress(p.read_bytes())
    n = struct.unpack(">i", b[8:12])[0]
    head = json.loads(b[12:12 + n])
    pal = head["palette"]
    y0 = head["y0"]
    gnd = np.array([is_ground(s) for s in pal])
    air = np.array([s.endswith(":air") for s in pal])
    p = 12 + n + 4
    ground = np.full((T, T), -10**6, dtype=np.int32)
    for c in range(T * T):
        k = struct.unpack(">H", b[p:p + 2])[0]
        p += 2
        if k == 0xFFFF:
            continue
        runs = np.frombuffer(b[p:p + 4 * k], dtype=">u2").reshape(k, 2)
        p += 4 * k
        yy = y0
        last = y0 - 1
        below = y0 - 1
        gap_from = None
        air_n = 0
        for ln, pid in runs:
            if gnd[pid]:
                if air_n >= 4 and last >= y0:
                    below = last
                    gap_from = yy
                last = yy + int(ln) - 1
                air_n = 0
            elif air[pid]:
                air_n += int(ln)
            yy += int(ln)
        ground[c // T, c % T] = below if gap_from is not None and last - gap_from < 24 else last
    return ground


def region_map():
    b = gzip.decompress((ROOT / "src/main/resources/data/skyabyss/continent/region.bin").read_bytes())
    size, scale, ox, oz = struct.unpack(">4i", b[4:20])
    return size, scale, ox, oz, np.frombuffer(b[20:], dtype=np.uint8).reshape(size, size)


REGION = {"meadow": 0, "outskirts": 1, "whale": 2, "tide": 3, "storm": 4, "ember": 5}


def check(land):
    idx = json.loads((OUT / land / "index.json").read_text(encoding="utf-8"))
    tiles = [tuple(t) for t in idx["tiles"]]
    G = {}
    for t in tiles:
        g = read_tile(land, *t)
        if g is not None:
            G[t] = g

    def h(x, z):
        g = G.get((x // T, z // T))
        if g is None:
            return None
        v = g[z % T, x % T]
        return None if v < -10**5 else int(v)

    size, scale, ox, oz, reg = region_map()

    def mine(x, z):
        i, j = (x - ox) // scale, (z - oz) // scale
        return 0 <= i < size and 0 <= j < size and reg[j, i] == REGION[land]

    # 1. 道
    lay = json.loads((ROOT / "src/main/resources/data/skyabyss/continent/layout.json").read_text(encoding="utf-8"))
    steps = []
    for ri, line in enumerate(lay["roads"]):
        last = None
        for (ax, az), (bx, bz) in zip(line, line[1:]):
            n = max(1, int(math.ceil(math.hypot(bx - ax, bz - az))))
            for k in range(n + 1):
                x = int(math.floor(ax + (bx - ax) * k / n))
                z = int(math.floor(az + (bz - az) * k / n))
                if last == (x, z):
                    continue
                if last is not None and mine(x, z):
                    a, b = h(*last), h(x, z)
                    if a is not None and b is not None and abs(a - b) >= 2 and min(a, b) >= SEA - 1:
                        steps.append((ri, x, z, a, b))
                last = (x, z)
    # 2. 穴
    pits = 0
    first = None
    win_pits, win_n = [], [0]
    for (tx, tz), g in G.items():
        gg = np.where(g < -10**5, 10**6, g)
        pad = np.pad(gg, 3, mode="edge")
        around = np.minimum.reduce([pad[3:-3, 6:], pad[3:-3, :-6], pad[6:, 3:-3], pad[:-6, 3:-3]])
        bad = (g > -10**5) & (g >= SEA) & (g <= around - 2) & (around < 10**5)
        xs, zs = np.meshgrid(np.arange(T) + tx * T, np.arange(T) + tz * T)
        size_, scale_, ox_, oz_, reg_ = size, scale, ox, oz, reg
        ii = np.clip((xs - ox_) // scale_, 0, size_ - 1)
        jj = np.clip((zs - oz_) // scale_, 0, size_ - 1)
        bad &= reg_[jj, ii] == REGION[land]
        # 区画の縁の 3 列は隣の区画を見ていないので数えない
        bad[:3, :] = bad[-3:, :] = bad[:, :3] = bad[:, -3:] = False
        c = int(bad.sum())
        if c and first is None:
            z_, x_ = np.argwhere(bad)[0]
            first = (int(x_ + tx * T), int(z_ + tz * T), int(g[z_, x_]))
        pits += c
        for (wx, wz) in WINDOWS:
            inw = bad & (np.abs(xs - wx) < 48) & (np.abs(zs - wz) < 48)
            for z_, x_ in np.argwhere(inw)[:3]:
                win_pits.append((int(x_ + tx * T), int(z_ + tz * T), int(g[z_, x_])))
            win_n[0] += int(inw.sum())
    print(f"{land}: {len(G)} tiles; road steps {len(steps)}" + (f" e.g. road {steps[0][0]} {steps[0][1]},{steps[0][2]} {steps[0][3]}->{steps[0][4]}" if steps else "")
          + f"; pits {pits} (in self-test windows {win_n[0]} {win_pits[:3]})")
    by = {}
    for s in steps:
        by.setdefault(s[0], []).append(s)
    for r, ss in sorted(by.items()):
        print(f"  road {r}: {len(ss)} steps, e.g. {ss[0][1]},{ss[0][2]} {ss[0][3]}->{ss[0][4]}")
    return steps, pits


if __name__ == "__main__":
    for land in sys.argv[1:]:
        check(land)
