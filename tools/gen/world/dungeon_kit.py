# -*- coding: utf-8 -*-
"""地下のダンジョンの層を彫る共通の部品 (docs/design/55)。build_dungeons.py から読む。

  Cave      … 彫る空き (numpy の箱)・壁の殻・飾りのブロック・印。書き出しは塔と同じ書式 (palette・runs・marks・rect)
  tunnel()  … 折れ線に沿う坑道 (床は平ら、天井は弧)。room() … 平らな床の丸天井の洞
  paint()   … 空きのまわり SHELL ブロックを壁にする。床・天井・壁で材料を分け、縦の層の筋と化石の脈を入れる

座標は層の手元 (原点 = 入口の立つ所の足もと、y 0 が立つ高さ = 床の上面の一つ上)。DungeonTerrain が区画の原点へずらして流し込む。
空きは AIR として書く (流し直したとき、水・溶岩・扉の仕掛けが元へ戻る)。殻の外は何も書かない (虚空のまま。見えない)。
"""
import json
import math
from pathlib import Path

import numpy as np
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[3]
AIR = "minecraft:air"
SHELL = 3


def hsh(*a):
    h = 2166136261
    for v in a:
        h = ((h ^ (int(v) & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    h ^= h >> 13
    h = (h * 1274126177) & 0xFFFFFFFF
    return (h & 0xFFFFFF) / float(0x1000000)


def _hash_arr(ix, iy, iz, seed):
    h = (ix.astype(np.int64) * 374761393 + iy.astype(np.int64) * 668265263 + iz.astype(np.int64) * 2147483647 + seed * 974634901) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFF) / float(0x1000000)


def vnoise(X, Y, Z, scale, seed):
    """なめらかな値のノイズ (0..1)。配列で"""
    fx, fy, fz = X / scale, Y / scale, Z / scale
    x0, y0, z0 = np.floor(fx), np.floor(fy), np.floor(fz)
    tx, ty, tz = fx - x0, fy - y0, fz - z0
    sx, sy, sz = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty), tz * tz * (3 - 2 * tz)
    v = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (sx if dx else 1 - sx) * (sy if dy else 1 - sy) * (sz if dz else 1 - sz)
                v = v + w * _hash_arr(x0 + dx, y0 + dy, z0 + dz, seed)
    return v


def pick(mats, x, y, z, seed):
    return mats[int(hsh(x, y, z, seed) * len(mats)) % len(mats)]


class Cave:
    def __init__(self, x0, x1, y0, y1, z0, z1, seed):
        self.x0, self.x1, self.y0, self.y1, self.z0, self.z1 = x0, x1, y0, y1, z0, z1
        self.seed = seed
        shape = (x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)
        self.air = np.zeros(shape, bool)
        # 彫ったが空きにしない所 (水・溶岩のたまりの下など、床を残したい所)
        self.keep = np.zeros(shape, bool)
        self.blk = {}
        self.marks = {}
        self.solid = None

    # ---------------------------------------------------------------- 座標
    def idx(self, x, y, z):
        return x - self.x0, y - self.y0, z - self.z0

    def inside(self, x, y, z):
        return self.x0 <= x <= self.x1 and self.y0 <= y <= self.y1 and self.z0 <= z <= self.z1

    def box(self, x0, x1, y0, y1, z0, z1):
        """手元の範囲の、配列の切り口と座標の格子"""
        x0, x1 = max(self.x0, int(math.floor(x0))), min(self.x1, int(math.ceil(x1)))
        y0, y1 = max(self.y0, int(math.floor(y0))), min(self.y1, int(math.ceil(y1)))
        z0, z1 = max(self.z0, int(math.floor(z0))), min(self.z1, int(math.ceil(z1)))
        if x0 > x1 or y0 > y1 or z0 > z1:
            return None
        sl = (slice(x0 - self.x0, x1 - self.x0 + 1), slice(y0 - self.y0, y1 - self.y0 + 1), slice(z0 - self.z0, z1 - self.z0 + 1))
        X, Y, Z = np.meshgrid(np.arange(x0, x1 + 1), np.arange(y0, y1 + 1), np.arange(z0, z1 + 1), indexing="ij")
        return sl, X.astype(float), Y.astype(float), Z.astype(float)

    # ---------------------------------------------------------------- 彫る
    def room(self, cx, cz, rx, rz, h, floor=0, rough=0.18, seed=1, wall_h=3.0):
        """平らな床 (立つ高さ floor) の丸天井の洞。rx・rz は床の半径、h は真ん中の天井の高さ"""
        b = self.box(cx - rx * 1.4 - 2, cx + rx * 1.4 + 2, floor, floor + h * 1.4 + 2, cz - rz * 1.4 - 2, cz + rz * 1.4 + 2)
        if b is None:
            return
        sl, X, Y, Z = b
        n = (vnoise(X, Y * 0.7, Z, 5.0, seed) - 0.5) * 2 * rough + (vnoise(X, Y, Z, 2.2, seed + 7) - 0.5) * rough * 0.6
        d = np.sqrt(((X - cx) / rx) ** 2 + ((Z - cz) / rz) ** 2) / (1 + n)
        top = floor + wall_h + (h - wall_h) * np.sqrt(np.clip(1 - d * d, 0, 1)) * (1 + 0.6 * n)
        m = (d <= 1.0) & (Y >= floor) & (Y <= top)
        self.air[sl] |= m

    def tunnel(self, pts, w=1.6, h=3.6, rough=0.22, seed=2):
        """折れ線 pts [(x, y, z) 立つ高さ] に沿う坑道。w = 半分の幅、h = 真ん中の高さ"""
        for a, c in zip(pts, pts[1:]):
            a, c = np.array(a, float), np.array(c, float)
            lo = np.minimum(a, c) - (w + 3)
            hi = np.maximum(a, c) + (w + 3)
            b = self.box(lo[0], hi[0], min(a[1], c[1]) - 1, max(a[1], c[1]) + h + 3, lo[2], hi[2])
            if b is None:
                continue
            sl, X, Y, Z = b
            dx, dz = c[0] - a[0], c[2] - a[2]
            L2 = max(1e-6, dx * dx + dz * dz)
            t = np.clip(((X - a[0]) * dx + (Z - a[2]) * dz) / L2, 0, 1)
            px, pz = a[0] + dx * t, a[2] + dz * t
            py = a[1] + (c[1] - a[1]) * t
            d = np.hypot(X - px, Z - pz)
            n = (vnoise(X, Y, Z, 3.0, seed) - 0.5) * 2 * rough
            ww = w * (1 + n)
            top = py + 2.2 + (h - 2.2) * np.sqrt(np.clip(1 - (d / np.maximum(ww, 0.3)) ** 2, 0, 1))
            m = (d <= ww + 0.35) & (Y >= np.round(py)) & (Y <= top)
            self.air[sl] |= m

    def shaft(self, cx, cz, r, y0, y1, rough=0.15, seed=3):
        """縦の穴 (縦坑)"""
        b = self.box(cx - r * 1.3 - 2, cx + r * 1.3 + 2, y0, y1, cz - r * 1.3 - 2, cz + r * 1.3 + 2)
        sl, X, Y, Z = b
        n = (vnoise(X, Y * 0.5, Z, 4.0, seed) - 0.5) * 2 * rough
        m = np.hypot(X - cx, Z - cz) <= r * (1 + n)
        self.air[sl] |= m

    def fill_air(self, x0, x1, y0, y1, z0, z1, val=True):
        b = self.box(x0, x1, y0, y1, z0, z1)
        if b is not None:
            self.air[b[0]] = val

    def is_air(self, x, y, z):
        if not self.inside(x, y, z):
            return False
        i = self.idx(x, y, z)
        return bool(self.air[i]) and self.blk.get((x, y, z), AIR) == AIR

    def floor_y(self, x, z, y_hint, span=6):
        """(x, z) で y_hint のそばの床の上 (立つ高さ)。なければ None"""
        for dy in range(0, span):
            for y in (y_hint - dy, y_hint + dy):
                if self.inside(x, y, z) and self.inside(x, y - 1, z) and self.air[self.idx(x, y, z)] and not self.air[self.idx(x, y - 1, z)]:
                    return y
        return None

    # ---------------------------------------------------------------- 飾り
    def put(self, x, y, z, s):
        if self.inside(x, y, z):
            self.blk[(x, y, z)] = s

    def putif_air(self, x, y, z, s):
        if self.inside(x, y, z) and self.air[self.idx(x, y, z)] and (x, y, z) not in self.blk:
            self.blk[(x, y, z)] = s

    def mark(self, name, x, y, z):
        self.marks[name] = [int(x), int(y), int(z)]

    # ---------------------------------------------------------------- 壁
    def paint(self, theme):
        """空きのまわりの殻を壁・床・天井の材料で塗る。theme は辞書 (wall(x, y, z, k) → 状態 など)"""
        air = self.air & ~self.keep
        solid = ndimage.binary_dilation(air, iterations=SHELL) & ~air
        self.solid = solid
        up = np.zeros_like(air)
        up[:, :-1, :] = air[:, 1:, :]
        down = np.zeros_like(air)
        down[:, 1:, :] = air[:, :-1, :]
        xs, ys, zs = np.nonzero(solid)
        X, Y, Z = xs + self.x0, ys + self.y0, zs + self.z0
        band = vnoise(X.astype(float), Y.astype(float) * 2.5, Z.astype(float), 9.0, self.seed + 31)
        fossil = vnoise(X.astype(float), Y.astype(float), Z.astype(float), 3.5, self.seed + 41)
        self.mats = {}
        for i in range(len(X)):
            x, y, z = int(X[i]), int(Y[i]), int(Z[i])
            kind = "floor" if up[xs[i], ys[i], zs[i]] else ("ceil" if down[xs[i], ys[i], zs[i]] else "wall")
            self.mats[(x, y, z)] = theme[kind](x, y, z, float(band[i]), float(fossil[i]))

    def ceiling_drips(self, mats, chance, seed, max_len=3, only=None):
        """天井から垂れるもの (鍾乳石・根・つらら)。mats = 下へ向かう状態の列の作り方 (長さ → 状態の列)"""
        xs, ys, zs = np.nonzero(self.air)
        for i in range(len(xs)):
            x, y, z = int(xs[i]) + self.x0, int(ys[i]) + self.y0, int(zs[i]) + self.z0
            if (x, y + 1, z) not in self.mats or (x, y, z) in self.blk:
                continue
            if only is not None and not only(x, y, z):
                continue
            if hsh(x, y, z, seed) >= chance:
                continue
            n = 1 + int(hsh(x, z, y, seed + 1) * max_len)
            col = mats(n)
            # 垂れる先の下に 2 つ以上の空きを残す (通り道をふさがない)
            if not all(self.is_air(x, y - k, z) for k in range(len(col) + 2)):
                continue
            for k, s in enumerate(col):
                self.blk[(x, y - k, z)] = s

    def floor_scatter(self, states, chance, seed, only=None):
        """床の上に散らす (小石・きのこ・骨)"""
        for (x, y, z), s in list(self.mats.items()):
            if not self.is_air(x, y + 1, z) or (x, y + 1, z) in self.blk:
                continue
            if only is not None and not only(x, y + 1, z):
                continue
            if hsh(x, y, z, seed) < chance:
                self.blk[(x, y + 1, z)] = states[int(hsh(x, z, y, seed + 3) * len(states)) % len(states)]

    def wall_lichen(self, chance, seed):
        """壁の光る苔 (灯りの足し)"""
        for (x, y, z) in list(self.mats.keys()):
            for dx, dz, face in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
                p = (x + dx, y, z + dz)
                if self.is_air(*p) and p not in self.blk and hsh(x, y, z, seed + dx * 3 + dz) < chance:
                    st = {"north": "false", "south": "false", "east": "false", "west": "false"}
                    st[face] = "true"
                    self.blk[p] = (f"minecraft:glow_lichen[down=false,east={st['east']},north={st['north']},south={st['south']},up=false,"
                                   f"waterlogged=false,west={st['west']}]")

    def lights(self, step, level, seed, height=4):
        """見えない灯り (light) を床から height の高さに格子で置く (暗すぎない洞。天井が低ければ天井の一つ下)"""
        xs, ys, zs = np.nonzero(self.air)
        seen = set()
        for i in range(len(xs)):
            x, y, z = int(xs[i]) + self.x0, int(ys[i]) + self.y0, int(zs[i]) + self.z0
            if x % step or z % step or (x, z, y // 12) in seen:
                continue
            # 床 (下が固い空き) から上へ height、天井に当たればその一つ下
            if not ((x, y - 1, z) in self.mats or (self.inside(x, y - 1, z) and not self.air[self.idx(x, y - 1, z)])):
                continue
            yy = y
            for k in range(1, height + 1):
                if self.is_air(x, y + k, z):
                    yy = y + k
                else:
                    break
            if yy - y < 2 or (x, yy, z) in self.blk:
                continue
            self.blk[(x, yy, z)] = f"minecraft:light[level={level},waterlogged=false]"
            seen.add((x, z, y // 12))

    # ---------------------------------------------------------------- 書き出し
    def write(self, out, note):
        cells = {}
        xs, ys, zs = np.nonzero(self.air)
        for i in range(len(xs)):
            cells[(int(xs[i]) + self.x0, int(ys[i]) + self.y0, int(zs[i]) + self.z0)] = AIR
        cells.update(self.mats)
        cells.update(self.blk)
        settle_gravity(cells)
        palette, index, runs = [], {}, []
        rows = {}
        for (x, y, z), s in cells.items():
            rows.setdefault((y, z), {})[x] = s
        for (y, z) in sorted(rows):
            row = rows[(y, z)]
            xa, xb = min(row), max(row)
            seq = []
            i = xa
            while i <= xb:
                s = row.get(i)
                j = i
                while j <= xb and row.get(j) == s:
                    j += 1
                if s is None:
                    idx = -1
                else:
                    if s not in index:
                        index[s] = len(palette)
                        palette.append(s)
                    idx = index[s]
                seq += [idx, j - i]
                i = j
            runs.append([y, z, xa] + seq)
        data = {"note": note, "palette": palette, "runs": runs, "marks": self.marks,
                "rect": [self.x0, self.z0, self.x1, self.z1]}
        out = Path(out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        return out.stat().st_size // 1024, len(cells), len(runs)


# 支えのない砂・砂利 (下が空き・水・草など) は、落ちない石に替える。写しを建てたあと、水の満ち引きや隣の置き換えで落ち、
# 通り道を埋めていた (2026-11-08 の声「海の中ダンジョン、2層目の最後おかしい。砂で最後埋まってる」)
GRAVITY = {"minecraft:sand": "minecraft:smooth_sandstone", "minecraft:red_sand": "minecraft:smooth_red_sandstone",
           "minecraft:gravel": "minecraft:tuff", "minecraft:suspicious_sand": "minecraft:smooth_sandstone",
           "minecraft:suspicious_gravel": "minecraft:tuff"}
SUPPORT_NOT = ("minecraft:air", "minecraft:water", "minecraft:lava", "minecraft:light", "minecraft:kelp", "minecraft:seagrass",
               "minecraft:cave_air")


def _supported(below):
    if below is None:
        return False
    name = below.split("[")[0]
    if name in SUPPORT_NOT or "slab" in name and "type=top" not in below and "type=double" not in below:
        return False
    return not any(k in name for k in ("fence", "lantern", "torch", "button", "carpet", "coral", "pickle", "lichen", "vine", "mushroom",
                                       "bars", "chain", "dripstone", "bud", "cluster", "cobweb", "flower", "grass", "kelp", "rod"))


def settle_gravity(cells):
    for _ in range(4):
        changed = 0
        for (x, y, z), s in list(cells.items()):
            to = GRAVITY.get(s.split("[")[0])
            if to and not _supported(cells.get((x, y - 1, z))):
                cells[(x, y, z)] = to
                changed += 1
        if not changed:
            break


# ---------------------------------------------------------------- 部品
def stair(block, facing, half="bottom"):
    return f"minecraft:{block}_stairs[facing={facing},half={half},shape=straight,waterlogged=false]"


def slab(block, half="bottom"):
    return f"minecraft:{block}_slab[type={half},waterlogged=false]"


def logs(block, axis="y"):
    return f"minecraft:{block}[axis={axis}]"


def fence(block):
    return f"minecraft:{block}_fence[east=false,north=false,south=false,waterlogged=false,west=false]"


def lantern(hang=False, soul=False):
    return f"minecraft:{'soul_' if soul else ''}lantern[hanging={'true' if hang else 'false'},waterlogged=false]"


def rail(shape):
    return f"minecraft:rail[shape={shape},waterlogged=false]"


CHAIN_Y = "minecraft:chain[axis=y,waterlogged=false]"
CHAIN_X = "minecraft:chain[axis=x,waterlogged=false]"
CHAIN_Z = "minecraft:chain[axis=z,waterlogged=false]"


def frame(cv, x, y, z, along, w=2, h=4, wood="spruce", lamp=False):
    """坑道の木組み (二本の柱と梁)。along = 坑道の向き ("x" か "z")"""
    for s in (-w, w):
        px, pz = (x, z + s) if along == "x" else (x + s, z)
        for k in range(h):
            if cv.is_air(px, y + k, pz):
                cv.put(px, y + k, pz, logs(f"stripped_{wood}_log" if k % 3 == 1 else f"{wood}_log"))
    for s in range(-w, w + 1):
        px, pz = (x, z + s) if along == "x" else (x + s, z)
        if cv.is_air(px, y + h, pz):
            cv.put(px, y + h, pz, logs(f"{wood}_log", "z" if along == "x" else "x"))
    if lamp and cv.is_air(x, y + h - 1, z):
        cv.put(x, y + h - 1, z, lantern(True))


def bone_arc(cv, cx, cz, r, y0, angle, thick=1, seed=0, block="minecraft:bone_block"):
    """巨神の肋骨の弧 (縦の半円)。angle は弧の面の向き (度、0 = x の面)"""
    a = math.radians(angle)
    ux, uz = math.cos(a), math.sin(a)
    for k in range(0, 181, 3):
        t = math.radians(k)
        px = cx + ux * r * math.cos(t)
        pz = cz + uz * r * math.cos(t)
        py = y0 + r * math.sin(t) * 0.9
        for dx in range(-thick + 1, thick):
            for dy in range(-thick + 1, thick):
                cv.put(int(round(px)) + dx, int(round(py)) + dy, int(round(pz)), f"{block}[axis=y]" if "bone_block" in block else block)
