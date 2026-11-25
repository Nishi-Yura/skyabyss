# -*- coding: utf-8 -*-
"""土地全体の書き出しの台本を書く (今の世界 = 前もって組む元)。
創造の人が土地を格子で回って、見どころ・外れの集落・奥のダンジョン・章の跡地・祠を建てさせ、
そのあと 256×256 の区画ごとに読み込んで書き出す (forceload は 1 区画 = 256 チャンクちょうど)。

    python -I tools/gen/world/authored/gen_land_dump.py whale
    SKYABYSS_OPENING_SITES=1 bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/land_dump_whale.txt -PshotsName=land_whale
    → run/shots/land_whale/dumps/whale_<tx>_<tz>.sbd
"""
import gzip
import json
import struct
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[4]
REGIONS = {"whale": 2, "tide": 3, "storm": 4, "ember": 5}
Y0, Y1 = 24, 250
STEP = 330


def rd(n):
    b = gzip.decompress((ROOT / "src/main/resources/data/skyabyss/continent" / n).read_bytes())
    size, scale, ox, oz = struct.unpack(">4i", b[4:20])
    return size, scale, ox, oz, np.frombuffer(b[20:], dtype=np.uint8).reshape(size, size)


def land_cells(region):
    size, scale, ox, oz, r = rd("region.bin")
    _, _, _, _, h = rd("height.bin")
    m = (r == region) & (h > 64)
    jj, ii = np.nonzero(m)
    return ii * scale + ox, jj * scale + oz, (size, scale, ox, oz, m)


def tiles_of(region):
    xs, zs, _ = land_cells(region)
    return sorted({(int(x) // 256, int(z) // 256) for x, z in zip(xs, zs)})


def main():
    land = sys.argv[1] if len(sys.argv) > 1 else "whale"
    xs, zs, (size, scale, ox, oz, m) = land_cells(REGIONS[land])
    tiles = tiles_of(REGIONS[land])
    # 回る点: 格子のうち、陸の上 (まわり 120 に陸がある) のもの
    pts = []
    for x in range(int(xs.min()), int(xs.max()) + 1, STEP):
        for z in range(int(zs.min()), int(zs.max()) + 1, STEP):
            i, j = (x - ox) // scale, (z - oz) // scale
            r = 120 // scale
            if m[max(0, j - r):j + r + 1, max(0, i - r):i + r + 1].any():
                pts.append((x, z))
    out = [f"# {land} の土地全体の書き出し。gen_land_dump.py が書く ({len(pts)} 点を回り、{len(tiles)} 区画を書き出す)",
           f"world land_{land} continent", "size 960 540", f"timeout {600 + len(pts) * 60 + len(tiles) * 120}", "render 8", "wait 100",
           "cmd skyabyss cinematic stop", "cmd time set 4000", "cmd gamerule doDaylightCycle false", "cmd gamerule doMobSpawning false",
           "cmd weather clear", "cmd skyabyss dev opening build", "wait 100", "cmd gamemode creative"]
    for (x, z) in pts:
        out += [f"cmd tp @s {x} 200 {z}", "waitchunks 300", "wait 500"]
    # もう一回り (前の点で建て始めた物を仕上げる)
    for (x, z) in pts:
        out += [f"cmd tp @s {x} 200 {z}", "waitchunks 200", "wait 120"]
    out += ["cmd gamemode spectator"]
    for (tx, tz) in tiles:
        x0, z0 = tx * 256, tz * 256
        out += ["cmd forceload remove all", f"cmd forceload add {x0} {z0} {x0 + 255} {z0 + 255}", f"camera {x0 + 128} 220 {z0 + 128} 0 90",
                "waitchunks 300", "wait 400", f"cmd skyabyss dev dump {land}_{tx}_{tz} {x0} {Y0} {z0} {x0 + 255} {Y1} {z0 + 255}", "waitdump"]
    out += ["cmd forceload remove all"]
    p = ROOT / "tools" / "shots" / f"land_dump_{land}.txt"
    p.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"{p}: {len(pts)} points, {len(tiles)} tiles")


if __name__ == "__main__":
    main()
