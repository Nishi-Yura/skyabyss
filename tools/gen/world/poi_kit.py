"""見どころ (data/skyabyss/opening/poi_*.json・outskirts_*.json) を作る部品 (docs/design/40 の 3 章、作り込みの基準は「削り出した岩・縦の層・細部の密度」)。

座標: x 東、z 南、y 上。y = -1 が地面の層 (置くときに範囲 meta.rect を平らにしてから重ねる)。空気も明示して書く (坑道・塚の中)。
今のファイルの site・meta・marks・note は変えずに、ブロックだけを書き換える (印の位置は Java が読む: 宝箱・碑・敵の湧き・立つ所・回復)。
"""
import json
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "skyabyss", "opening")
AIR = "minecraft:air"


class Rng:
    """決まった種の乱数 (xorshift)。同じ種なら同じ建物"""

    def __init__(self, seed):
        self.s = (seed * 2654435761 + 1) & 0xFFFFFFFF or 1

    def next(self):
        s = self.s
        s ^= (s << 13) & 0xFFFFFFFF
        s ^= s >> 17
        s ^= (s << 5) & 0xFFFFFFFF
        self.s = s
        return s / 4294967296.0

    def random(self):
        return self.next()

    def chance(self, p):
        return self.next() < p

    def pick(self, seq):
        return seq[int(self.next() * len(seq)) % len(seq)]

    def range(self, a, b):
        return a + int(self.next() * (b - a + 1)) % (b - a + 1)


def hash3(x, y, z, seed=0):
    h = (x * 374761393 + y * 668265263 + z * 2147483647 + seed * 974634901) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) / 4294967296.0


def noise3(x, y, z, scale=4.0, seed=0):
    """なめらかな値のノイズ (0..1)"""
    fx, fy, fz = x / scale, y / scale, z / scale
    x0, y0, z0 = math.floor(fx), math.floor(fy), math.floor(fz)
    tx, ty, tz = fx - x0, fy - y0, fz - z0
    sx, sy, sz = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty), tz * tz * (3 - 2 * tz)
    v = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (sx if dx else 1 - sx) * (sy if dy else 1 - sy) * (sz if dz else 1 - sz)
                v += w * hash3(x0 + dx, y0 + dy, z0 + dz, seed)
    return v


# ---------------------------------------------------------------- ブロックの名前
def st(block, facing, half="bottom", shape="straight"):
    return f"minecraft:{block}_stairs[facing={facing},half={half},shape={shape},waterlogged=false]"


def sl(block, half="bottom"):
    return f"minecraft:{block}_slab[type={half},waterlogged=false]"


def wall(block, up=True):
    return f"minecraft:{block}_wall[east=none,north=none,south=none,up={'true' if up else 'false'},waterlogged=false,west=none]"


def log(block, axis="y"):
    return f"minecraft:{block}[axis={axis}]"


def fence(block):
    return f"minecraft:{block}_fence[east=false,north=false,south=false,waterlogged=false,west=false]"


def trap(block, facing, half="bottom", open_=False):
    return f"minecraft:{block}_trapdoor[facing={facing},half={half},open={'true' if open_ else 'false'},powered=false,waterlogged=false]"


def lantern(hang=False, soul=False):
    return f"minecraft:{'soul_' if soul else ''}lantern[hanging={'true' if hang else 'false'},waterlogged=false]"


CHAIN_Y = "minecraft:chain[axis=y,waterlogged=false]"
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


# ---------------------------------------------------------------- 設計図
class BP:
    def __init__(self, name):
        self.name = name
        self.path = os.path.join(OUT, name + ".json")
        old = json.load(open(self.path, encoding="utf-8"))
        self.old = old
        self.site = old["site"]
        self.meta = old["meta"]
        self.marks = old["marks"]
        self.note = "天と淵の大陸の見どころ (docs/design/40 の 3 章)。tools/gen/world/build_pois.py が書く"
        self.cells = {}
        r = old["meta"]["rect"]
        self.rect = r

    def set(self, x, y, z, state):
        self.cells[(x, y, z)] = state

    def setif(self, x, y, z, state):
        """空いているときだけ"""
        if (x, y, z) not in self.cells or self.cells[(x, y, z)] == AIR:
            self.cells[(x, y, z)] = state

    def get(self, x, y, z):
        return self.cells.get((x, y, z))

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, state)

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, AIR)

    def inside(self, x, z):
        r = self.rect
        return r[0] <= x <= r[2] and r[1] <= z <= r[3]

    def keep_old(self, pred):
        """今の設計図のブロックのうち pred(x, y, z, state) を満たすものを残す (坑道の地下など)"""
        pal = self.old["palette"]
        for x, y, z, i in self.old["blocks"]:
            if pred(x, y, z, pal[i]):
                self.cells[(x, y, z)] = pal[i]

    def old_at(self, x, y, z):
        pal = self.old["palette"]
        for bx, by, bz, i in self.old["blocks"]:
            if (bx, by, bz) == (x, y, z):
                return pal[i]
        return None

    def keep_marks(self):
        """印のブロック (宝箱・碑・樽・看板) は今のまま、印の上の空きも残す"""
        for name, (x, y, z) in self.marks.items():
            s = self.old_at(x, y, z)
            if s is not None:
                self.cells[(x, y, z)] = s
            elif name in ("foes", "stand", "heal", "ferry"):
                self.cells[(x, y, z)] = AIR
                self.cells[(x, y + 1, z)] = AIR
            below = self.old_at(x, y - 1, z)
            if below is not None and (x, y - 1, z) not in self.cells:
                self.cells[(x, y - 1, z)] = below

    def save(self):
        r = self.rect
        cells = {k: v for k, v in self.cells.items() if r[0] - 1 <= k[0] <= r[2] + 1 and r[1] - 1 <= k[2] <= r[3] + 1}
        palette = []
        idx = {}
        blocks = []
        for (x, y, z), s in sorted(cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
            if s not in idx:
                idx[s] = len(palette)
                palette.append(s)
            blocks.append([x, y, z, idx[s]])
        data = {"site": self.site, "note": self.note, "palette": palette, "blocks": blocks, "marks": self.marks, "meta": self.meta}
        with open(self.path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
            f.write("\n")
        return len(blocks)


# ---------------------------------------------------------------- 形
def ground(bp, r, mix, seed=1, round_=True, y=-1, edge_fade=0.35):
    """地面の層: mix = [(状態, 重み)]。縁はまばらに (自然の地面に溶ける)"""
    tot = sum(w for _, w in mix)
    for x in range(-r, r + 1):
        for z in range(-r, r + 1):
            d = math.hypot(x, z) if round_ else max(abs(x), abs(z))
            if d > r + 0.4:
                continue
            if d > r * (1 - edge_fade) and hash3(x, 7, z, seed) < (d - r * (1 - edge_fade)) / (r * edge_fade + 0.01):
                continue
            n = noise3(x, 0, z, 3.0, seed) * 0.6 + hash3(x, 1, z, seed) * 0.4
            acc = 0
            for s, w in mix:
                acc += w / tot
                if n <= acc:
                    bp.set(x, y, z, s)
                    break
            else:
                bp.set(x, y, z, mix[-1][0])


def strata(y, layers, seed, x=0, z=0):
    """縦の層: layers = [(上限の y, [材料…])]。層の境目を少し揺らす"""
    j = (noise3(x, 0, z, 5.0, seed) - 0.5) * 2.2
    for top, mats in layers:
        if y + j <= top:
            return mats[int(hash3(x, y, z, seed) * len(mats)) % len(mats)]
    mats = layers[-1][1]
    return mats[int(hash3(x, y, z, seed) * len(mats)) % len(mats)]


def rock(bp, cx, cy, cz, rx, ry, rz, layers, seed, rough=0.35, below=True):
    """削り出した岩: ノイズで揺れる楕円体。layers は strata の形 (y は岩の中心からの高さではなく絶対の y)"""
    for x in range(int(cx - rx - 2), int(cx + rx + 3)):
        for y in range(int(cy - ry - 2), int(cy + ry + 3)):
            if not below and y < -1:
                continue
            for z in range(int(cz - rz - 2), int(cz + rz + 3)):
                d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                n = (noise3(x, y, z, 2.5, seed) - 0.5) * 2 * rough + (noise3(x, y, z, 6.0, seed + 9) - 0.5) * rough
                if d <= 1 + n:
                    bp.set(x, y, z, strata(y, layers, seed, x, z))


def column(bp, x, z, y0, y1, state):
    for y in range(y0, y1 + 1):
        bp.set(x, y, z, state)


def disc(bp, cx, cz, r, y, state, hollow=False, inner=0.0):
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            d = math.hypot(x - cx, z - cz)
            if d <= r + 0.35 and (not hollow or d >= r - 0.85) and d >= inner:
                bp.set(x, y, z, state)


def ring_states(cx, cz, r):
    """円の縁のセル"""
    out = []
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            d = math.hypot(x - cx, z - cz)
            if r - 0.85 <= d <= r + 0.35:
                out.append((x, z))
    return out


def scatter(bp, r, y, states, density, seed, keep_free=(), only_on=None):
    """散らす (草・花・小石)。only_on: 下のブロックがこの集合のときだけ"""
    for x in range(-r, r + 1):
        for z in range(-r, r + 1):
            if math.hypot(x, z) > r or (x, z) in keep_free:
                continue
            if hash3(x, y, z, seed) >= density:
                continue
            if bp.get(x, y, z) not in (None, AIR):
                continue
            if only_on is not None and bp.get(x, y - 1, z) not in only_on:
                continue
            bp.set(x, y, z, states[int(hash3(x, y + 5, z, seed) * len(states)) % len(states)])


def gable_roof(bp, x0, z0, x1, z1, y, mat, ridge_mat=None, along="x", overhang=1, slab_edge=True):
    """切妻: along = 棟の向き。x0..x1 × z0..z1 の外壁に、軒を overhang 出す"""
    if along == "x":
        a0, a1 = x0 - overhang, x1 + overhang
        lo, hi = z0 - overhang, z1 + overhang
        k = 0
        while lo + k <= hi - k:
            for a in range(a0, a1 + 1):
                if lo + k == hi - k:
                    bp.set(a, y + k, lo + k, sl(ridge_mat or mat))
                else:
                    bp.set(a, y + k, lo + k, st(mat, "south"))
                    bp.set(a, y + k, hi - k, st(mat, "north"))
            k += 1
        return y + k
    a0, a1 = z0 - overhang, z1 + overhang
    lo, hi = x0 - overhang, x1 + overhang
    k = 0
    while lo + k <= hi - k:
        for a in range(a0, a1 + 1):
            if lo + k == hi - k:
                bp.set(lo + k, y + k, a, sl(ridge_mat or mat))
            else:
                bp.set(lo + k, y + k, a, st(mat, "east"))
                bp.set(hi - k, y + k, a, st(mat, "west"))
        k += 1
    return y + k


def hip_roof(bp, x0, z0, x1, z1, y, mat, top=None):
    """寄棟 (四方から): 一段ごとに内へ。上が閉じたら top のブロック"""
    k = 0
    while x0 + k <= x1 - k and z0 + k <= z1 - k:
        a0, a1, b0, b1 = x0 + k, x1 - k, z0 + k, z1 - k
        if a0 == a1 or b0 == b1:
            for x in range(a0, a1 + 1):
                for z in range(b0, b1 + 1):
                    bp.set(x, y + k, z, sl(mat))
            break
        for x in range(a0, a1 + 1):
            bp.set(x, y + k, b0, st(mat, "south"))
            bp.set(x, y + k, b1, st(mat, "north"))
        for z in range(b0 + 1, b1):
            bp.set(a0, y + k, z, st(mat, "east"))
            bp.set(a1, y + k, z, st(mat, "west"))
        for x, z, s in ((a0, b0, "outer_left"), (a1, b0, "outer_right"), (a0, b1, "outer_right"), (a1, b1, "outer_left")):
            bp.set(x, y + k, z, st(mat, "south" if z == b0 else "north", "bottom", s))
        k += 1
    if top:
        bp.set((x0 + x1) // 2, y + k, (z0 + z1) // 2, top)
