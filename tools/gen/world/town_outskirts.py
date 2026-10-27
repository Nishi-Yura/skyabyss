"""土地の町の壁の外 (門の外の家と畑から設計図の縁まで) の地面を、まわりの土地になじませる (2026-10-28 の町の見直し)。

壁の外は草と土だけの平らな台で、まわりの土地 (炎の赤い荒れ地、潮の浜、雷の岩山) から浮いていた。
設計図の縁から、地面の段 (y=-1) の柔らかい地面 (草・土・粗い土・根の土・ポドゾル) で、上に何も無い列をたどって (道・壁・家・畑は越えない)、
  - 地面を土地ごとの混ぜ (縁ほど土地の地面が多い。畑のそばは草のまま)
  - 小物 (枯れ草・草・羊歯・小岩・雪)。道・壁・家・畑と印の 1 まわりには置かない (通り道をふさがない)
に塗り直す。二度は塗らない (note に town_outskirts があればやめる)。

  python -I tools/gen/world/town_outskirts.py [whale tide storm ember]
"""
from __future__ import annotations

import json
import zlib
import math
import os
import random
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
DIR = os.path.join(ROOT, "src", "main", "resources", "data", "skyabyss", "opening")
TAG = "town_outskirts"

SOFT = {"minecraft:grass_block[snowy=false]", "minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
        "minecraft:podzol[snowy=false]", "minecraft:podzol"}
FIELD = ("farmland", "water")

G = "minecraft:grass_block[snowy=false]"
# 土地ごとの地面の混ぜ: (ブロック, 重さ) の組。inner = 壁のそば、edge = 設計図の縁 (まわりの土地)
MIX = {
    "ember": dict(inner=[(G, 5), ("minecraft:coarse_dirt", 3), ("minecraft:rooted_dirt", 1), ("minecraft:red_sand", 1)],
                  edge=[("minecraft:red_sand", 5), ("minecraft:coarse_dirt", 2), ("minecraft:terracotta", 2), ("minecraft:orange_terracotta", 1), (G, 1)]),
    "tide": dict(inner=[(G, 6), ("minecraft:coarse_dirt", 1), ("minecraft:sand", 2)],
                 edge=[("minecraft:sand", 5), (G, 3), ("minecraft:coarse_dirt", 1)]),
    "storm": dict(inner=[(G, 6), ("minecraft:coarse_dirt", 2), ("minecraft:gravel", 1)],
                  edge=[("minecraft:stone", 4), (G, 3), ("minecraft:gravel", 2), ("minecraft:andesite", 2), ("minecraft:snow_block", 1)]),
    "whale": dict(inner=[(G, 7), ("minecraft:coarse_dirt", 1), ("minecraft:podzol[snowy=false]", 1)],
                  edge=[(G, 5), ("minecraft:podzol[snowy=false]", 2), ("minecraft:moss_block", 2), ("minecraft:coarse_dirt", 1)]),
}
# 小物: (ブロック, 確率)。草の上だけに置くもの (grass_only) と、どこでも置くもの
PROPS = {
    "ember": dict(any=[("minecraft:dead_bush", 0.05)], grass=[("minecraft:short_grass", 0.06)], rock=("minecraft:blackstone", "minecraft:basalt[axis=y]", 0.006)),
    "tide": dict(any=[("minecraft:dead_bush", 0.01)], grass=[("minecraft:short_grass", 0.14), ("minecraft:dandelion", 0.01)], rock=("minecraft:sandstone", "minecraft:smooth_sandstone", 0.004)),
    "storm": dict(any=[], grass=[("minecraft:short_grass", 0.1), ("minecraft:fern", 0.05)], rock=("minecraft:cobblestone", "minecraft:mossy_cobblestone", 0.006),
                  snow=("minecraft:snow[layers=1]", 0.25)),
    "whale": dict(any=[], grass=[("minecraft:short_grass", 0.14), ("minecraft:fern", 0.06), ("minecraft:sweet_berry_bush[age=1]", 0.006)],
                  rock=("minecraft:mossy_cobblestone", "minecraft:cobblestone", 0.006)),
}


def load(path):
    d = json.load(open(path, encoding="utf-8"))
    pal = d["palette"]
    cells = {}
    for a in d["runs"]:
        y, z, x = a[0], a[1], a[2]
        for i in range(3, len(a) - 1, 2):
            idx, n = a[i], a[i + 1]
            if idx >= 0:
                for k in range(n):
                    cells[(x + k, y, z)] = pal[idx]
            x += n
    for a in d.get("blocks", []):
        cells[(a[0], a[1], a[2])] = pal[a[3]]
    d.pop("blocks", None)
    return d, cells


def save(path, d, cells):
    pal = []
    index = {}

    def idx(s):
        if s not in index:
            index[s] = len(pal)
            pal.append(s)
        return index[s]

    rows = {}
    for (x, y, z), s in cells.items():
        rows.setdefault((y, z), []).append((x, s))
    runs = []
    for (y, z) in sorted(rows):
        xs = sorted(rows[(y, z)])
        run = [y, z, xs[0][0]]
        px = xs[0][0]
        cur, n = None, 0
        for x, s in xs:
            if x != px:
                if n:
                    run += [cur, n]
                run += [-1, x - px]
                cur, n = None, 0
            i = idx(s)
            if i == cur:
                n += 1
            else:
                if n:
                    run += [cur, n]
                cur, n = i, 1
            px = x + 1
        if n:
            run += [cur, n]
        runs.append(run)
    d["palette"] = pal
    d["runs"] = runs
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(d, f, ensure_ascii=False, separators=(",", ":"))


def noise(seed):
    rnd = random.Random(seed)
    grid = {}

    def v(ix, iz):
        if (ix, iz) not in grid:
            grid[(ix, iz)] = rnd.random()
        return grid[(ix, iz)]

    def f(x, z, s=6.0):
        gx, gz = x / s, z / s
        ix, iz = math.floor(gx), math.floor(gz)
        tx, tz = gx - ix, gz - iz
        tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
        a = v(ix, iz) + (v(ix + 1, iz) - v(ix, iz)) * tx
        b = v(ix, iz + 1) + (v(ix + 1, iz + 1) - v(ix, iz + 1)) * tx
        return a + (b - a) * tz
    return f


def pick(mix, r):
    total = sum(w for _, w in mix)
    r *= total
    for s, w in mix:
        r -= w
        if r < 0:
            return s
    return mix[-1][0]


def outskirts(name):
    path = os.path.join(DIR, f"town_{name}.json")
    d, cells = load(path)
    if TAG in (d.get("note") or ""):
        print(f"{name}: already done")
        return
    x0, z0, x1, z1 = d["meta"]["rect"]
    marks = set()
    for v in d.get("marks", {}).values():
        pts = v if v and isinstance(v[0], list) else [v]
        for p in pts:
            marks.add((p[0], p[2]))

    def soft(x, z):
        s = cells.get((x, -1, z))
        if s is not None and s not in SOFT:
            return False
        above = cells.get((x, 0, z))
        return above is None or above == "minecraft:air" or above in ("minecraft:short_grass", "minecraft:fern", "minecraft:dead_bush")

    # 縁からたどる (柔らかい地面だけ。道・壁・家・畑は越えない)
    seen = set()
    stack = [(x, z) for x in range(x0, x1 + 1) for z in (z0, z1)] + [(x, z) for z in range(z0, z1 + 1) for x in (x0, x1)]
    while stack:
        x, z = stack.pop()
        if (x, z) in seen or not (x0 <= x <= x1 and z0 <= z <= z1) or not soft(x, z):
            continue
        seen.add((x, z))
        stack += [(x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)]

    def near_field(x, z, r):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                s = cells.get((x + dx, -1, z + dz)) or ""
                if any(k in s for k in FIELD):
                    return True
        return False

    mix = MIX[name]
    props = PROPS[name]
    n1, n2 = noise(zlib.crc32(name.encode()) & 0xffff), noise(zlib.crc32(name.encode()) >> 16)
    rnd = random.Random(f"{name}-outskirts")
    span = max(x1, z1)
    changed = placed = 0
    rocks = []
    seen = {(x, z) for (x, z) in seen if (x, -1, z) in cells}   # 何も無い列 (縁の自然の地面) はたどるだけで塗らない
    for (x, z) in sorted(seen):
        cheb = max(abs(x), abs(z))
        edge = min(1.0, max(0.0, (cheb - (span - 26)) / 22.0))
        e = min(1.0, max(0.0, edge + (n1(x, z, 7.0) - 0.5) * 0.9))
        # 斑はまとまりで (一つずつ混ぜると砂嵐のように見える): 内か縁かも、どのブロックかも、ゆっくり変わる雑音で決める
        table = mix["inner"] if (e + (rnd.random() - 0.5) * 0.12 < 0.5 or near_field(x, z, 2)) else mix["edge"]
        s = pick(table, min(0.999, max(0.0, (n2(x, z, 4.5) - 0.5) * 1.6 + 0.5 + (rnd.random() - 0.5) * 0.14)))
        if cells.get((x, -1, z)) != s:
            cells[(x, -1, z)] = s
            changed += 1
        # 小物は、8 まわりも全部たどった地面で、印から離れた所だけ
        if any((x + dx, z + dz) not in seen for dx in (-1, 0, 1) for dz in (-1, 0, 1)) or any(abs(mx - x) <= 1 and abs(mz - z) <= 1 for mx, mz in marks):
            continue
        cells.pop((x, 0, z), None)
        r = rnd.random()
        top = None
        if s.startswith("minecraft:grass_block"):
            for b, p in props["grass"]:
                if r < p:
                    top = b
                    break
                r -= p
        elif s in ("minecraft:stone", "minecraft:andesite", "minecraft:gravel") and "snow" in props and rnd.random() < props["snow"][1] * e:
            top = props["snow"][0]
        if top is None and props["any"] and not s.startswith("minecraft:snow"):
            b, p = props["any"][0]
            if rnd.random() < p * (0.4 + e):
                top = b
        if top:
            cells[(x, 0, z)] = top
            placed += 1
        elif rnd.random() < props["rock"][2] * (0.5 + e):
            rocks.append((x, z))
    # 小岩: 2〜4 個の塊 (高さ 1、まんなかだけ 2)
    for (x, z) in rocks:
        a, b, _ = props["rock"]
        for dx, dz in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
            if (x + dx, z + dz) in seen and (dx == 0 and dz == 0 or rnd.random() < 0.55):
                if (x + dx, z + dz) not in marks:
                    cells[(x + dx, 0, z + dz)] = a if rnd.random() < 0.6 else b
        if rnd.random() < 0.5:
            cells[(x, 1, z)] = b
    d["note"] = (d.get("note") or "") + f" / {TAG}: 壁の外 {len(seen)} 列の地面をまわりの土地へ ({changed} 塗り、小物 {placed}、小岩 {len(rocks)})"
    save(path, d, cells)
    print(f"{name}: {len(seen)} outside columns, {changed} repainted, {placed} props, {len(rocks)} rocks")


if __name__ == "__main__":
    for n in sys.argv[1:] or ["whale", "tide", "storm", "ember"]:
        outskirts(n)
