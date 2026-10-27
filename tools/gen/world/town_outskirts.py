"""土地の町の壁の外 (門の外の家と畑から設計図の縁まで) の地面を、まわりの土地になじませる (2026-10-28 の町の見直し)。

壁の外は草と土だけの平らな台で、まわりの土地 (炎の赤い荒れ地、潮の浜、雷の岩山) から浮いていた。
設計図の縁から、地面の段 (y=-1) の柔らかい地面 (草・土・粗い土・根の土・ポドゾル) で、上に何も無い列をたどって (道・壁・家・畑は越えない)、
  - 地面を土地ごとの混ぜ (縁ほど土地の地面が多い。畑のそばは草のまま)
  - 小物 (枯れ草・草・羊歯・小岩・雪)。道・壁・家・畑と印の 1 まわりには置かない (通り道をふさがない)
に塗り直す。二度は塗らない (note に town_outskirts があればやめる)。

  python -I tools/gen/world/town_outskirts.py [whale tide storm ember capital]   (都は壁の外の畑と木立も)
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
    "capital": dict(inner=[(G, 24), ("minecraft:coarse_dirt", 1), ("minecraft:rooted_dirt", 1)],
                    edge=[(G, 16), ("minecraft:moss_block", 1), ("minecraft:coarse_dirt", 1)]),
    "whale": dict(inner=[(G, 7), ("minecraft:coarse_dirt", 1), ("minecraft:podzol[snowy=false]", 1)],
                  edge=[(G, 5), ("minecraft:podzol[snowy=false]", 2), ("minecraft:moss_block", 2), ("minecraft:coarse_dirt", 1)]),
}
# 小物: (ブロック, 確率)。草の上だけに置くもの (grass_only) と、どこでも置くもの
PROPS = {
    "ember": dict(any=[("minecraft:dead_bush", 0.05)], grass=[("minecraft:short_grass", 0.06)], rock=("minecraft:blackstone", "minecraft:basalt[axis=y]", 0.006)),
    "tide": dict(any=[("minecraft:dead_bush", 0.01)], grass=[("minecraft:short_grass", 0.14), ("minecraft:dandelion", 0.01)], rock=("minecraft:sandstone", "minecraft:smooth_sandstone", 0.004)),
    "storm": dict(any=[], grass=[("minecraft:short_grass", 0.1), ("minecraft:fern", 0.05)], rock=("minecraft:cobblestone", "minecraft:mossy_cobblestone", 0.006),
                  snow=("minecraft:snow[layers=1]", 0.25)),
    "capital": dict(any=[], grass=[("minecraft:short_grass", 0.16), ("minecraft:fern", 0.02), ("minecraft:dandelion", 0.012), ("minecraft:poppy", 0.01),
                                   ("minecraft:oxeye_daisy", 0.01), ("minecraft:cornflower", 0.008), ("minecraft:tall_grass[half=lower]", 0.0)],
                    rock=("minecraft:mossy_cobblestone", "minecraft:cobblestone", 0.0015)),
    "whale": dict(any=[], grass=[("minecraft:short_grass", 0.14), ("minecraft:fern", 0.06), ("minecraft:sweet_berry_bush[age=1]", 0.006)],
                  rock=("minecraft:mossy_cobblestone", "minecraft:cobblestone", 0.006)),
}


LAYOUT = os.path.join(ROOT, "src", "main", "resources", "data", "skyabyss", "continent", "layout.json")
ROAD_KEEP = 5.5   # 大陸の道 (芯からこの距離まで) は塗らない (幅 7 の道と荒れた縁。設計図に道は入っていないので、上書きすると道が消える)


def origin_of(name):
    lay = json.load(open(LAYOUT, encoding="utf-8"))
    return tuple(lay["capital"]) if name == "capital" else tuple(lay["towns"][name])


def road_distance(name):
    """設計図の座標 (回さない。町も都も南向きのまま置く) での大陸の道の芯からの距離"""
    ox, oz = origin_of(name)
    segs = []
    for line in json.load(open(LAYOUT, encoding="utf-8"))["roads"]:
        for a, b in zip(line, line[1:]):
            if min(a[0], b[0]) - ox < 200 and max(a[0], b[0]) - ox > -200 and min(a[1], b[1]) - oz < 200 and max(a[1], b[1]) - oz > -200:
                segs.append((a[0] - ox, a[1] - oz, b[0] - ox, b[1] - oz))

    def f(x, z):
        best = 1e9
        for x0, z0, x1, z1 in segs:
            dx, dz = x1 - x0, z1 - z0
            l2 = dx * dx + dz * dz
            u = 0 if l2 == 0 else max(0.0, min(1.0, ((x - x0) * dx + (z - z0) * dz) / l2))
            best = min(best, math.hypot(x - (x0 + u * dx), z - (z0 + u * dz)))
        return best
    return f


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
    path = os.path.join(DIR, "capital.json" if name == "capital" else f"town_{name}.json")
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
    # 何も無い列: 町では縁の自然の地面 (たどるだけで塗らない)。都は壁の外が全部何も無いので、そこも塗る (均した平らな芝のままだった)
    fill = name == "capital"
    rd = road_distance(name)
    seen = {(x, z) for (x, z) in seen if (fill or (x, -1, z) in cells) and rd(x, z) > ROAD_KEEP}
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
    extra = capital_extras(cells, seen, rd, rnd) if fill else ""
    d["note"] = (d.get("note") or "") + f" / {TAG}{extra}: 壁の外 {len(seen)} 列の地面をまわりの土地へ ({changed} 塗り、小物 {placed}、小岩 {len(rocks)})"
    save(path, d, cells)
    print(f"{name}: {len(seen)} outside columns, {changed} repainted, {placed} props, {len(rocks)} rocks")


def capital_extras(cells, seen, rd, rnd):
    """都の壁の外: 門のそばの畑 (柵・水路・干し草) と、四隅の木立"""
    def free(x0, z0, x1, z1, gap):
        return all((x, z) in seen and rd(x, z) > ROAD_KEEP + 1 for x in range(x0 - gap, x1 + gap + 1) for z in range(z0 - gap, z1 + gap + 1))

    crops = ["minecraft:wheat[age=7]", "minecraft:carrots[age=7]", "minecraft:potatoes[age=7]", "minecraft:beetroots[age=3]", "minecraft:wheat[age=5]"]
    fields = []
    xs = sorted({x for x, _ in seen})
    zs = sorted({z for _, z in seen})
    # 畑 11 × 7 (縁は柵、まんなかの列は水)。壁から離れすぎない所 (まわり 2 があいていて、たどった地面の中)
    for z0 in range(zs[0] + 2, zs[-1] - 9, 3):
        for x0 in range(xs[0] + 2, xs[-1] - 13, 3):
            if len(fields) >= 10:
                break
            if any(abs(x0 - fx) < 16 and abs(z0 - fz) < 11 for fx, fz in fields):
                continue
            if rnd.random() < 0.5 or not free(x0, z0, x0 + 10, z0 + 6, 2):
                continue
            fields.append((x0, z0))
    for x0, z0 in fields:
        crop = rnd.choice(crops)
        for x in range(x0, x0 + 11):
            for z in range(z0, z0 + 7):
                edge = x in (x0, x0 + 10) or z in (z0, z0 + 6)
                if edge:
                    cells[(x, -1, z)] = "minecraft:coarse_dirt" if rnd.random() < 0.4 else G
                    gate = (z == z0 + 6 and x == x0 + 5)
                    cells[(x, 0, z)] = "minecraft:air" if gate else "minecraft:oak_fence"
                elif z == z0 + 3:
                    cells[(x, -1, z)] = "minecraft:water"
                    cells[(x, 0, z)] = "minecraft:air"
                else:
                    cells[(x, -1, z)] = "minecraft:farmland[moisture=7]"
                    cells[(x, 0, z)] = crop
        # 柵の角の柱に灯りは置かない (灯りは都の決まった所だけ)。干し草と樽を柵の外の一角に
        hx, hz = x0 + 11 + 1, z0 + rnd.randint(1, 4)
        if (hx, hz) in seen:
            cells[(hx, 0, hz)] = "minecraft:hay_block[axis=y]"
            if (hx, hz + 1) in seen and rnd.random() < 0.6:
                cells[(hx, 0, hz + 1)] = "minecraft:hay_block[axis=x]"
            if rnd.random() < 0.5:
                cells[(hx, 1, hz)] = "minecraft:hay_block[axis=z]"
    # 木立: 3〜5 本の樫と白樺 (畑と道から離れて)
    taken = {(x, z) for fx, fz in fields for x in range(fx - 3, fx + 15) for z in range(fz - 3, fz + 10)}
    groves = 0
    for _ in range(400):
        if groves >= 9:
            break
        cx, cz = rnd.choice(sorted(seen))
        if (cx, cz) in taken or not free(cx - 5, cz - 5, cx + 5, cz + 5, 0):
            continue
        groves += 1
        for _t in range(rnd.randint(3, 5)):
            tx, tz = cx + rnd.randint(-2, 2), cz + rnd.randint(-2, 2)
            birch = rnd.random() < 0.35
            log = "minecraft:birch_log[axis=y]" if birch else "minecraft:oak_log[axis=y]"
            leaf = "minecraft:birch_leaves[distance=1,persistent=true,waterlogged=false]" if birch else "minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]"
            h = rnd.randint(5, 7)
            for y in range(0, h):
                cells[(tx, y, tz)] = log
            for y in range(h - 3, h + 2):
                r = 2.6 if y < h else (1.6 if y == h else 1.0)
                for dx in range(-3, 4):
                    for dz in range(-3, 4):
                        if dx * dx + dz * dz <= r * r + rnd.random() * 0.8 and (tx + dx, y, tz + dz) not in cells or cells.get((tx + dx, y, tz + dz), "").endswith("air"):
                            if dx * dx + dz * dz <= r * r + 0.4 and not (dx == 0 and dz == 0 and y < h):
                                cells[(tx + dx, y, tz + dz)] = leaf
        for x in range(cx - 5, cx + 6):
            for z in range(cz - 5, cz + 6):
                taken.add((x, z))
    return f"、畑 {len(fields)}、木立 {groves}"


if __name__ == "__main__":
    for n in sys.argv[1:] or ["whale", "tide", "storm", "ember", "capital"]:
        outskirts(n)
