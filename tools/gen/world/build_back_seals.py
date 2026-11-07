# -*- coding: utf-8 -*-
"""巨神の体に残る門・天窓・尖塔を閉じる継ぎ当て (titan/BackSeal)。data/skyabyss/opening/seal_<背>.json を書く (世界の座標のまま)。

  bash tools/dev/mc_run.sh runShots --no-daemon -Pscript=tools/shots/back_seal_dump.txt -PshotsName=back_seal_dump
  python -X utf8 tools/gen/world/build_back_seals.py run/shots/back_seal_dump/dumps

書き出しは継ぎ当ての前の背 (部品 seal_* があると建てるときに置かれるので、作り直すときは先に部品を消してから書き出す)。
区画ごとに、区画のすぐ外の一周の列の「体の面の高さ」から区画の中の面を結び (Coons の面)、面より上は空け、面から下 12 段は
いちばん近い外の列の積み方 (面からの深さごとの石) を写し、その下の空きは体の石で埋める。門の石 (銅・海のランタン・閃緑岩) は面に数えない
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools/structure"))
from sbd import load  # noqa: E402

OUT = ROOT / "src/main/resources/data/skyabyss/opening"
NOTE = "tools/gen/world/build_back_seals.py が書く (背の書き出しから)。手で直さない"
DEPTH = 12
BUILT = ("waxed_copper", "waxed_cut_copper", "sea_lantern", ":diorite", "polished_diorite", "stone_bricks", "polished_deepslate")

# 背 → (書き出しの名前, 区画 [(x0, x1, z0, z1)])。区画は門の石をすべて含み、外の一周が体の面
SEALS = {
    "storm_back": ("seal_storm", [
        (-12, 11, 55, 77),    # 羽根の門の輪と前の段
        (-30, 29, -26, 8),    # 風の広間の天窓 (銅の輪と光る面) と左右の風の民の塔 (一つの区画にして、外の一周を塔の外の背にする)
    ]),
    "tide_back": ("seal_tide", [
        (-106, -90, 65, 109),  # 潮門の枠と光る面
    ]),
}


def is_built(name):
    return any(k in name for k in BUILT)


def main(dump_dir):
    for back, (dump_name, regions) in SEALS.items():
        d = load(Path(dump_dir) / f"{dump_name}.sbd")
        ox, oy, oz = d.origin
        sy, sz, sx = d.shape
        states = [e["state"] for e in d.palette]
        names = d.names
        air_state = "minecraft:air"

        def col(x, z):
            return d.ids[:, z - oz, x - ox]

        def body_top(x, z):
            c = col(x, z)
            for iy in range(sy - 1, -1, -1):
                nm = names[c[iy]]
                if d.collide[iy, z - oz, x - ox] and not is_built(nm):
                    return iy + oy
            return None

        def stack(x, z, top):
            c = col(x, z)
            out = []
            for k in range(DEPTH):
                iy = top - k - oy
                if iy < 0:
                    break
                nm = names[c[iy]]
                st = states[c[iy]]
                if not d.collide[iy, z - oz, x - ox] or is_built(nm):
                    st = out[-1] if out else None
                if st:
                    out.append(st)
            return out

        cells = {}
        for (x0, x1, z0, z1) in regions:
            # 外の一周の面の高さ
            def edge(x, z):
                t = body_top(x, z)
                return t
            west = {z: edge(x0 - 1, z) for z in range(z0 - 1, z1 + 2)}
            east = {z: edge(x1 + 1, z) for z in range(z0 - 1, z1 + 2)}
            north = {x: edge(x, z0 - 1) for x in range(x0 - 1, x1 + 2)}
            south = {x: edge(x, z1 + 1) for x in range(x0 - 1, x1 + 2)}

            def fill_line(m):
                ks = sorted(m)
                known = [k for k in ks if m[k] is not None]
                if not known:
                    return m
                for k in ks:
                    if m[k] is None:
                        near = min(known, key=lambda q: abs(q - k))
                        m[k] = m[near]
                return m
            for m in (west, east, north, south):
                fill_line(m)
            c00, c10 = north[x0 - 1], north[x1 + 1]
            c01, c11 = south[x0 - 1], south[x1 + 1]
            ring = [(x0 - 1, z) for z in range(z0 - 1, z1 + 2)] + [(x1 + 1, z) for z in range(z0 - 1, z1 + 2)] + \
                   [(x, z0 - 1) for x in range(x0, x1 + 1)] + [(x, z1 + 1) for x in range(x0, x1 + 1)]
            ring = [p for p in ring if body_top(*p) is not None]
            # 面の高さ (Coons の面)。一つだけ周りより低い/高いくぼみ・出っ張りは周りに合わせる (建築の監査の FLOOR_DIMPLE)
            tops = {}
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    u = (x - (x0 - 1)) / ((x1 + 1) - (x0 - 1))
                    v = (z - (z0 - 1)) / ((z1 + 1) - (z0 - 1))
                    tops[(x, z)] = int(round((1 - u) * west[z] + u * east[z] + (1 - v) * north[x] + v * south[x]
                                             - ((1 - u) * (1 - v) * c00 + u * (1 - v) * c10 + (1 - u) * v * c01 + u * v * c11)))

            def top_at(x, z):
                if (x, z) in tops:
                    return tops[(x, z)]
                return body_top(x, z)
            for _ in range(4):
                changed = False
                for (x, z), t in list(tops.items()):
                    nb = [top_at(x + dx, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
                    nb = [q for q in nb if q is not None]
                    if len(nb) == 4 and all(q > t for q in nb):
                        tops[(x, z)] = min(nb)
                        changed = True
                    elif len(nb) == 4 and all(q < t for q in nb):
                        tops[(x, z)] = max(nb)
                        changed = True
                if not changed:
                    break
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    u = (x - (x0 - 1)) / ((x1 + 1) - (x0 - 1))
                    v = (z - (z0 - 1)) / ((z1 + 1) - (z0 - 1))
                    t = ((1 - u) * west[z] + u * east[z] + (1 - v) * north[x] + v * south[x]
                         - ((1 - u) * (1 - v) * c00 + u * (1 - v) * c10 + (1 - u) * v * c01 + u * v * c11))
                    top = tops[(x, z)]
                    src = min(ring, key=lambda p: (p[0] - x) ** 2 + (p[1] - z) ** 2)
                    st = stack(src[0], src[1], body_top(*src))
                    if not st:
                        continue
                    deep = st[-1]
                    c = col(x, z)
                    # この列の体のいちばん下 (それより下は空のまま)
                    bottom = None
                    for iy in range(sy):
                        if d.collide[iy, z - oz, x - ox]:
                            bottom = iy + oy
                            break
                    if bottom is None:
                        bottom = top - DEPTH
                    for iy in range(sy):
                        y = iy + oy
                        cur = states[c[iy]]
                        if y > top:
                            want = air_state
                        elif y > top - DEPTH:
                            want = st[min(top - y, len(st) - 1)]
                        elif y >= bottom and not d.collide[iy, z - oz, x - ox]:
                            want = deep
                        elif is_built(names[c[iy]]):
                            want = deep
                        else:
                            continue
                        if want != cur and not (want == air_state and names[c[iy]] in ("minecraft:air", "minecraft:cave_air")):
                            cells[(x, y, z)] = want
        pal, idx, blocks = [], {}, []
        for (x, y, z), b in sorted(cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
            if b not in idx:
                idx[b] = len(pal)
                pal.append(b)
            blocks.append([x, y, z, idx[b]])
        xs = [k[0] for k in cells] or [0]
        zs = [k[2] for k in cells] or [0]
        data = {"note": NOTE, "palette": pal, "blocks": blocks, "marks": {}, "meta": {"rect": [min(xs), min(zs), max(xs), max(zs)]}}
        (OUT / f"seal_{back}.json").write_text(json.dumps(data, separators=(",", ":")), encoding="utf8")
        print(back, len(blocks), "cells")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ROOT / "run/shots/back_seal_dump/dumps")
