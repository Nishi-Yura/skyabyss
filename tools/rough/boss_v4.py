"""岩鯨の心臓 (最終ボス) 第4回ラフ: 案B「石の殻」を細かく作り直す 3案 A/B/C x 3段階。

使い方 (リポジトリ直下で): python tools/rough/boss_v4.py
出力: docs/roughs/v4/boss/<a|b|c>/phaseN.(geo.json|tex.png|glow.png|render.png)
      docs/roughs/v4/boss.png (比較シート。左端は今の実装の3段階), docs/roughs/v4/boss.md は手で書く

作り方:
- 1 セル = 0.5 単位 = テクスチャ 1 画素 (2 px / 単位)。形は格子に彫り、部品 (bone) ごとに
  3D の貪欲法で箱に分ける。1 セルには 1 つの箱しか入らないので、同じ骨の箱は重ならない。
- 殻の板 8 枚は、板の正面を +z に向けた「板の座標」で彫り、骨を y 軸まわりに回して八角形に並べる
  (板 i の中心の向き = 22.5 + 45 i 度。+x=0, +z=90)。板は、隣との境の面から 0.3 単位以上離して彫る
  (くさび形の制限) ので、回した板どうしも重ならない。光る継ぎ目は境の面の上 (幅 0.5) に置く。
- 回っていない部品 (心臓、冠、足、根、棘、鎖など) は 1 つの共有の格子に彫る。セルごとに持ち主を 1 つ決める
  ので重ならない。回った部品との重なりは、全ての箱の組を分離軸の判定 (OBB の SAT) で調べる。
- 乱数は seed 固定。
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
from voxel import _rot, check_overlaps, font, load_geo, render  # noqa: E402

OUT = ROOT / "docs/roughs/v4/boss"
SHEET = ROOT / "docs/roughs/v4/boss.png"
ASSET = ROOT / "src/main/resources/assets/skyabyss"
H = 0.5                       # セルの大きさ (単位)
T22 = math.tan(math.radians(22.5))
CY = 12.0                     # 殻の中心の高さ
M = 0.9                       # くさびの余白 (継ぎ目の面からの距離。継ぎ目の半幅 0.75 < M cos22.5)
AW = 512                      # アトラスの幅 (画素)

# ---------------------------------------------------------------- 色

STONE = [(98, 112, 118), (110, 108, 102), (88, 100, 108), (104, 116, 116)]   # 地層 (帯ごとに交互)
STONE_D = (58, 68, 74)
STRATA_LINE = (66, 74, 80)
RIM = (134, 148, 150)
RIM_D = (112, 124, 128)
FRESH = (168, 160, 146)        # 割れたばかりの面
CARVE = (62, 70, 78)           # 彫った溝
BONE = (230, 218, 192)
BONE_D = (186, 170, 142)
BONE_DD = (150, 134, 110)
MOSS = (88, 124, 58)
MOSS_L = (126, 160, 72)
IRON = (84, 82, 82)
IRON_L = (128, 120, 108)
RUST = (122, 84, 58)
EMBER = (255, 112, 46)
EMBER_D = (214, 70, 34)
AMBER = (255, 184, 84)
HOT = (255, 236, 176)
FLESH = (152, 48, 52)
FLESH_D = (100, 30, 38)
FLESH_DD = (66, 16, 26)
FLESH_L = (192, 84, 78)
ART = (196, 70, 66)            # 動脈
ART_L = (222, 120, 108)
VEIN_C = (96, 58, 104)         # 静脈 (紫)
VALVE = (238, 206, 190)
FAT = (226, 196, 128)
CORE = (255, 246, 236)
RED_GLOW = (255, 86, 70)
CYAN = (127, 244, 255)

LIGHT = np.array([-0.45, 0.75, 0.5])
LIGHT = LIGHT / np.linalg.norm(LIGHT)


# ---------------------------------------------------------------- 骨の格子

class Grid:
    """セル 0.5 単位の格子。lab = 持ち主の骨の番号 (0 = 空)。"""

    def __init__(self, lo, hi, seed):
        self.lo = np.array(lo, float)
        self.n = tuple(int(v) for v in np.round((np.array(hi, float) - self.lo) / H))
        g = [self.lo[i] + (np.arange(self.n[i]) + 0.5) * H for i in range(3)]
        self.X, self.Y, self.Z = np.meshgrid(*g, indexing="ij")
        # 1 単位の格子に丸めた座標 (大きい塊を 1 単位の段で作るため)
        self.Xc, self.Yc, self.Zc = (np.floor(a) + 0.5 for a in (self.X, self.Y, self.Z))
        self.lab = np.zeros(self.n, np.int16)
        self.col = np.zeros(self.n + (3,), float)
        self.glow = np.zeros(self.n, bool)
        self.flat = np.zeros(self.n, bool)     # 陰影を付けない (光る所など)
        self.rng = np.random.default_rng(seed)
        self.noise = self.rng.uniform(0.93, 1.07, self.n)
        self.names = {}

    def label(self, name):
        if name not in self.names:
            self.names[name] = len(self.names) + 1
        return self.names[name]

    def put(self, name, m, color, glow=False, over=False):
        """空いているセル (over=True なら他の骨のセルも) を、この骨の持ち物にして色を塗る"""
        if not over:
            m = m & (self.lab == 0)
        if not m.any():
            return m
        self.lab[m] = self.label(name)
        self.col[m] = color if not callable(color) else color(m)
        self.glow[m] = glow
        self.flat[m] = glow
        return m

    def paint(self, m, color, glow=None):
        m = m & (self.lab > 0)
        if m.any():
            self.col[m] = color if not callable(color) else color(m)
            if glow is not None:
                self.glow[m] = glow
                self.flat[m] = glow
        return m

    def carve(self, m):
        self.lab[m] = 0
        self.glow[m] = False

    @property
    def occ(self):
        return self.lab > 0

    def up_open(self):
        o = self.occ
        u = np.ones_like(o)
        u[:, :-1, :] = ~o[:, 1:, :]
        return o & u

    def surface(self):
        o = self.occ
        p = np.pad(o, 1)
        inner = (p[2:, 1:-1, 1:-1] & p[:-2, 1:-1, 1:-1] & p[1:-1, 2:, 1:-1] & p[1:-1, :-2, 1:-1]
                 & p[1:-1, 1:-1, 2:] & p[1:-1, 1:-1, :-2])
        return o & ~inner


# ---------------------------------------------------------------- 形の道具

def octa_xz(g, cx, cz, r, coarse=False):
    """段の付いた八角形 (半径 r) の足もと。長方形 4 つの和なので、箱は 7 つ前後で済む"""
    dx, dz = np.abs(g.X - cx), np.abs(g.Z - cz)
    q = lambda a: round(a * 2) / 2
    m = np.zeros(g.n, bool)
    for a, b in ((r, 0.42 * r), (0.9 * r, 0.72 * r), (0.72 * r, 0.9 * r), (0.42 * r, r)):
        m |= (dx < q(a)) & (dz < q(b))
    return m


def slab(g, y0, y1, cx, cz, r):
    return (g.Y > y0) & (g.Y < y1) & octa_xz(g, cx, cz, r)


def box(g, lo, hi):
    return ((g.X > lo[0]) & (g.X < hi[0]) & (g.Y > lo[1]) & (g.Y < hi[1]) & (g.Z > lo[2]) & (g.Z < hi[2]))


def tube(g, p0, p1, r):
    """軸に沿った管 (断面は角を落とした正方形)。p0, p1 は 1 つの座標だけが違う"""
    p0, p1 = np.array(p0, float), np.array(p1, float)
    ax = int(np.argmax(np.abs(p1 - p0)))
    o = [i for i in range(3) if i != ax]
    P = (g.X, g.Y, g.Z)
    lo, hi = min(p0[ax], p1[ax]), max(p0[ax], p1[ax])
    a, b = np.abs(P[o[0]] - p0[o[0]]), np.abs(P[o[1]] - p0[o[1]])
    return (P[ax] > lo) & (P[ax] < hi) & (a < r) & (b < r) & (a + b < r * 1.5)


def ell(g, c, r, coarse=False):
    r = (r, r, r) if np.isscalar(r) else r
    X, Y, Z = (g.Xc, g.Yc, g.Zc) if coarse else (g.X, g.Y, g.Z)
    return ((X - c[0]) / r[0]) ** 2 + ((Y - c[1]) / r[1]) ** 2 + ((Z - c[2]) / r[2]) ** 2 <= 1.0


def heart_shape(g, c, R, coarse=True):
    X, Y, Z = (g.Xc, np.floor(g.Y / 1.5) * 1.5 + 0.75, g.Zc) if coarse else (g.X, g.Y, g.Z)
    a = (X - c[0]) / R
    v = (Y - c[1]) / R
    b = (Z - c[2]) / R
    f = (a * a + 2.25 * b * b + v * v - 1) ** 3 - a * a * v ** 3 - (9 / 80) * b * b * v ** 3
    return f <= 0


def dist_poly2(px, py, pts):
    """2D の折れ線までの距離"""
    d = np.full(px.shape, 1e9)
    for (ax, ay), (bx, by) in zip(pts[:-1], pts[1:]):
        vx, vy = bx - ax, by - ay
        t = np.clip(((px - ax) * vx + (py - ay) * vy) / max(vx * vx + vy * vy, 1e-9), 0, 1)
        d = np.minimum(d, np.hypot(px - ax - t * vx, py - ay - t * vy))
    return d


def chain_v(g, name, cx, cz, ytop, n, first_x=True, color=None):
    """縦に吊った鎖。輪は 2.5 x 3 x 0.5、交互に 90 度ずつ向きを変えて、本当に輪どうしが通り合う。
    cx, cz は 0.25 ずれの格子 (n + 0.25)。輪の中心から外へ 1.25、穴は 1.5"""
    m_all = np.zeros(g.n, bool)
    for k in range(n):
        yt = ytop - 2 * k
        yb = yt - 3
        along_x = (k % 2 == 0) == first_x
        if along_x:   # x-y 面の輪 (z に薄い)
            thin = np.abs(g.Z - cz) < 0.25
            outer = (np.abs(g.X - cx) < 1.25) & (g.Y > yb) & (g.Y < yt)
            hole = (np.abs(g.X - cx) < 0.75) & (g.Y > yb + 0.5) & (g.Y < yt - 0.5)
        else:
            thin = np.abs(g.X - cx) < 0.25
            outer = (np.abs(g.Z - cz) < 1.25) & (g.Y > yb) & (g.Y < yt)
            hole = (np.abs(g.Z - cz) < 0.75) & (g.Y > yb + 0.5) & (g.Y < yt - 0.5)
        m = thin & outer & ~hole
        m_all |= m
    col = color or (lambda m: iron_col(g, m))
    g.put(name, m_all, col)
    return m_all


def iron_col(g, m):
    t = g.noise[m][:, None]
    base = np.array(IRON, float) * t
    hi = (g.Y[m] * 2 % 6 < 1)[:, None]
    rust = (g.rng.random(m.sum()) < 0.18)[:, None]
    c = np.where(hi, np.array(IRON_L, float), base)
    return np.where(rust, np.array(RUST, float) * t, c)


def mix(m, g, c0, c1, p, seed_shift=0.0):
    """セルごとに c0 と c1 を割合 p で混ぜた色"""
    r = g.noise[m]
    sel = (np.modf(r * 37.0 + seed_shift)[0] < p)[:, None]
    return np.where(sel, np.array(c1, float), np.array(c0, float)) * r[:, None]


# ---------------------------------------------------------------- 殻の板 (板の座標: 正面 +z)

BAND_H = 1.5
NB = 12            # 帯の数 (v = -9 .. 9)


def band_r(k, RS=12.0):
    vc = -9 + BAND_H * (k + 0.5)
    return math.floor(math.sqrt(RS * RS - vc * vc) * 2) / 2


class Plate:
    """1 枚の殻 (回った骨)。g は板の座標の格子。"""

    def __init__(self, name, phi, seed, dz=0.0, dy=0.0, parent=None):
        self.name, self.phi, self.dz, self.dy, self.parent = name, phi, dz, dy, parent
        self.g = Grid((-6.5, CY - 9.5 + dy, 2.5 + dz), (6.5, CY + 9.5 + dy, 14.5 + dz), seed)
        g = self.g
        self.v = g.Y - CY - dy
        self.k = np.clip(np.floor((self.v + 9) / BAND_H).astype(int), -1, NB)
        zz = g.Z - dz
        # くさびの余り (正なら、境の面から離れている)
        self.slack = ((zz - 0.25) * T22 - M) - (np.abs(g.X) + 0.25)
        self.wedge = self.slack >= 0
        self.zz = zz
        self.ro = np.array([band_r(k) if 0 <= k < NB else 0 for k in range(-1, NB + 1)])[self.k + 1]

    def bone(self):
        return {"name": self.name, "pivot": [0, CY, 0], "rotation": [0, 90 - self.phi, 0], "parent": self.parent}


def build_plate(p: Plate, opt, ph, i, krange=(0, NB), thick=2.0):
    """板を彫る。opt = 'a'|'b'|'c'。戻り値なし (p.g に彫る)"""
    g, v, k, zz, ro = p.g, p.v, p.k, p.zz, p.ro
    inb = (k >= krange[0]) & (k < krange[1])
    field = inb & p.wedge & (zz <= ro) & (zz > ro - thick)
    kk = np.clip(k, 0, NB - 1)
    tone = np.array(STONE, float)[kk % len(STONE)]

    def stone_col(m):
        c = tone[m] * g.noise[m][:, None]
        # 帯の境 (下端 1 セル) は暗い線
        edge = (((v[m] + 9) % BAND_H) < 0.5) & (kk[m] % 2 == 0)
        c[edge] = np.array(STRATA_LINE, float) * g.noise[m][edge, None]
        return c

    g.put(p.name, field, stone_col)
    # 縁 (左右と上下の端): 0.5 盛り上げる
    top_k, bot_k = krange[1] - 1, krange[0]
    rim_w = 1.0 if opt != "c" else 0.5
    rim_layer = inb & p.wedge & (zz > ro) & (zz <= ro + 0.5)
    side = p.slack < rim_w
    endband = (k == top_k) | (k == bot_k)
    rim = rim_layer & (side | endband)
    if opt == "c":
        rim = rim_layer & endband
    g.put(p.name, rim, lambda m: mix(m, g, RIM, RIM_D, 0.3))
    # 鋲 (骨の釘): 縁の上に 1x1 単位
    rv = []
    for kb in sorted({bot_k, (bot_k + top_k) // 2, top_k}):
        r0 = band_r(kb)
        yc = CY + p.dy - 9 + BAND_H * (kb + 0.5)
        xe = (r0 + 0.5 - 0.25) * T22 - M - 0.25 - 0.5     # 縁の外の端から 0.5 内側
        xs = [-xe + 0.25, xe - 0.25] if kb not in (bot_k, top_k) else [-xe + 0.25, 0.0, xe - 0.25]
        for xc in xs:
            xc = round(xc * 2) / 2
            rv.append(box(g, (xc - 0.5, yc - 0.5, r0 + 0.5 + p.dz), (xc + 0.5, yc + 0.5, r0 + 1.0 + p.dz)) & p.wedge)
    if opt != "c":
        for m in rv:
            g.put(p.name, m, lambda mm: np.where((g.Y[mm] % 1 > 0.5)[:, None], np.array(BONE, float),
                                                  np.array(BONE_D, float)))
    rng = np.random.default_rng(1000 + i * 17 + ord(opt))

    # --- 案ごとの彫刻
    if opt == "b":
        carve_b(p, i, ph, krange)
    if opt == "c":
        veins_c(p, i, ph, krange, rng)

    # --- 光るひび
    n_cracks = {1: (1 if i % 3 == 0 else 0), 2: (2 if i % 2 == 0 else 1), 3: (1 if i % 2 == 0 else 0)}[ph]
    if opt == "c" and ph == 1:
        n_cracks = 1 if i % 2 == 0 else 0
    width = {1: 0.3, 2: 0.55, 3: 0.45}[ph]
    for c in range(n_cracks):
        x0 = rng.uniform(-2.5, 2.5)
        vtop = -9 + BAND_H * krange[1] - rng.uniform(0.5, 2.0)
        pts = [(x0, vtop)]
        steps = rng.integers(5, 9)
        for s in range(steps):
            x0 = float(np.clip(x0 + rng.uniform(-1.4, 1.4), -3.5, 3.5))
            pts.append((x0, pts[-1][1] - rng.uniform(0.8, 1.6)))
        crack(p, pts, width)
        j = int(rng.integers(2, len(pts) - 1))
        bx = pts[j][0] + rng.choice([-1, 1]) * rng.uniform(1.5, 2.5)
        crack(p, [pts[j], (bx, pts[j][1] - 1.2), (bx + rng.uniform(-0.8, 0.8), pts[j][1] - 2.6)], width * 0.8)

    # --- 苔: 上を向いた段の上に
    if True:
        up = g.up_open() & (g.lab == g.label(p.name)) & ~g.glow & (v > -2.5)
        amount = {"a": 0.3, "b": 0.22, "c": 0.6}[opt]
        moss = up & (np.modf(g.noise * 53.0)[0] < amount)
        g.paint(moss, lambda m: mix(m, g, MOSS, MOSS_L, 0.4))
        if opt == "c":
            # 苔の房 (0.5 の盛り): 上の段に少し
            tuft = np.zeros(g.n, bool)
            tuft[:, 1:, :] = moss[:, :-1, :]
            tuft &= (np.modf(g.noise * 91.0)[0] < 0.35) & p.wedge & (v < 8.5)
            g.put(p.name, tuft, lambda m: mix(m, g, MOSS, MOSS_L, 0.6))


def crack(p: Plate, pts, width):
    g = p.g
    lab = g.label(p.name)
    d = dist_poly2(g.X, p.v, pts)
    mine = g.lab == lab
    surf_depth = p.ro + 1.0 - p.zz     # 表面 (縁を含む) からの深さの目安
    groove = mine & (d < width) & (surf_depth < 2.0) & (p.zz > p.ro - 1.0)
    g.carve(groove)
    floor_ = (g.lab == lab) & (d < width + 0.1) & (p.zz > p.ro - 1.5) & (p.zz <= p.ro - 1.0)
    g.paint(floor_, HOT, glow=True)
    edge = (g.lab == lab) & (d < width + 0.3) & (p.zz > p.ro - 1.0) & ~g.glow
    g.paint(edge, lambda m: np.where((p.zz[m] <= p.ro[m] - 0.5)[:, None], np.array(EMBER, float),
                                     np.array(EMBER_D, float)), glow=True)


def carve_b(p: Plate, i, ph, krange):
    """B: 古い彫刻。上の帯に「鯨の眼」の輪の紋、中の帯に帯 (girdle) と鎖、下の帯に縦の溝"""
    g, v, zz, ro = p.g, p.v, p.zz, p.ro
    lab = g.label(p.name)
    k0, k1 = krange
    # 眼の紋: 帯 7〜9 (v 1.5..6) の中心 (0, 3.75)
    if k0 <= 7 and k1 >= 10:
        r = np.hypot(g.X, (v - 3.75) * 1.0)
        layer = p.wedge & (zz > ro) & (zz <= ro + 0.5) & (p.k >= 7) & (p.k <= 9)
        ring = layer & (r > 1.75) & (r < 2.75)
        g.put(p.name, ring, lambda m: mix(m, g, RIM, BONE_D, 0.25))
        # 中心の瞳: 光る (封印の眼)
        pupil = (g.lab == lab) & (r < 1.0) & (zz > ro - 0.5) & (zz <= ro)
        g.carve(pupil)
        pfloor = (g.lab == lab) & (r < 1.1) & (zz > ro - 1.0) & (zz <= ro - 0.5)
        g.paint(pfloor, EMBER if ph < 3 else RED_GLOW, glow=True)
        # 放射の刻み線 (色だけ)
        ang = np.degrees(np.arctan2(v - 3.75, g.X)) % 30
        rays = (g.lab == lab) & (r > 2.9) & (r < 4.2) & ((ang < 4) | (ang > 26)) & (zz > ro - 0.5)
        g.paint(rays, CARVE)
    # 帯 (girdle): 帯 5,6 を 0.5 盛り上げ、骨色の縁
    if k0 <= 5 and k1 >= 7 and i % 2 == 0:
        gird = p.wedge & (zz > ro) & (zz <= ro + 0.5) & ((p.k == 5) | (p.k == 6)) & (p.slack >= 1.0)
        g.put(p.name, gird, lambda m: np.where((np.abs(v[m]) > 1.0)[:, None], np.array(BONE_D, float),
                                                np.array(BONE, float) * g.noise[m][:, None]))
        # 帯の上の横の鎖: 輪は板の座標で x 方向に並ぶ。表面 z = ro+0.5 の外に
        zb = band_r(5) + 0.5 + p.dz          # 帯の表面
        # 寝た輪 (x-y 面) と立った輪 (x-z 面) が 2 単位おきに交互。立った輪の端は寝た輪の穴を通り、
        # 寝た輪 (帯から 0.5 浮いて、立った輪の下の棒に載る) の端は立った輪の穴を通る
        zb = zb - p.dz
        chain_m = np.zeros(g.n, bool)
        for n_, xc in enumerate((-3.0, -1.0, 1.0, 3.0)):
            if n_ % 2 == 0:    # 寝た輪 (z に薄い)
                thin = (zz > zb + 0.5) & (zz <= zb + 1.0)
                outer = (np.abs(g.X - xc) < 1.5) & (np.abs(v - 0.25) < 1.25)
                hole = (np.abs(g.X - xc) < 1.0) & (np.abs(v - 0.25) < 0.75)
            else:              # 立った輪 (y に薄い)
                thin = np.abs(v - 0.25) < 0.25
                outer = (np.abs(g.X - xc) < 1.5) & (zz > zb) & (zz <= zb + 2.0)
                hole = (np.abs(g.X - xc) < 1.0) & (zz > zb + 0.5) & (zz <= zb + 1.5)
            chain_m |= thin & outer & ~hole & p.wedge
        g.put(p.name, chain_m, lambda m: iron_col(g, m))
    # 縦の溝 (下の帯 1〜3): 0.5 の深さの溝を 1.5 おきに
    if k0 <= 1:
        flute = (g.lab == lab) & (p.k >= 1) & (p.k <= 3) & (zz > ro - 0.5) & (zz <= ro) \
            & ((np.floor(g.X / 0.5).astype(int) % 3) == 0) & (p.slack > 1.0)
        g.paint(flute, CARVE)     # 溝は色だけ (箱を増やさない)


def veins_c(p: Plate, i, ph, krange, rng):
    """C: 殻の表に這う血管 (0.5 の盛り上がり) と、骨の瘤"""
    g, v, zz, ro = p.g, p.v, p.zz, p.ro
    k0, k1 = krange
    vtop = -9 + BAND_H * k1
    side = 1 if i % 2 == 0 else -1
    # 継ぎ目の側の上から、斜めに下りる血管 2 本
    paths = []
    for n in range(2):
        x = side * rng.uniform(2.0, 3.5)
        pts = [(x, vtop - 0.25)]
        for s in range(int(rng.integers(4, 7))):
            x = float(np.clip(x - side * rng.uniform(0.2, 1.3), -3.6, 3.6))
            pts.append((x, pts[-1][1] - rng.uniform(1.0, 1.8)))
        paths.append(pts)
        side = -side
    for pts in paths:
        d = dist_poly2(g.X, v, pts)
        layer = p.wedge & (zz > ro) & (zz <= ro + 0.5) & (p.k >= k0) & (p.k < k1)
        vein = layer & (d < 0.45)
        glow = ph >= 2
        g.put(p.name, vein, lambda m: np.where((d[m] < 0.2)[:, None], np.array(ART_L, float),
                                                np.array(ART, float)) * g.noise[m][:, None])
        if glow:
            core_line = (g.lab == g.label(p.name)) & (d < 0.2) & layer
            g.paint(core_line, AMBER if ph == 2 else RED_GLOW, glow=True)
    # 骨の瘤 (フジツボのような白い粒) 3 つ
    for n in range(3):
        xc = round(rng.uniform(-3, 3) * 2) / 2
        kb = int(rng.integers(k0 + 1, max(k0 + 2, k1 - 1)))
        r0 = band_r(kb)
        yc = CY + p.dy - 9 + BAND_H * kb + 0.75
        knob = box(g, (xc - 0.5, yc - 0.5, r0 + p.dz), (xc + 0.5, yc + 0.5, r0 + 1.0 + p.dz)) & p.wedge
        g.put(p.name, knob, lambda m: np.where((g.Y[m] % 1 > 0.5)[:, None], np.array(BONE, float),
                                                np.array(BONE_D, float)))


def broken_edge(p: Plate, side, seed):
    """開いた側の縁を、ぎざぎざに割る (side = +1 / -1: 板の座標の x の向き)"""
    g = p.g
    rng = np.random.default_rng(seed)
    lab = g.label(p.name)
    jag = rng.uniform(0.8, 3.0, NB + 2)[p.k + 1] + 0.5 * np.sin(g.Y * 2.1)
    xedge = (p.zz - 0.25) * T22 - M - 0.25
    cut = (g.lab == lab) & (side * g.X > xedge - jag)
    g.carve(cut)
    # 新しい割れ面: 切った所に接するセルは明るい石、内側は光る
    near = np.zeros(g.n, bool)
    c = cut
    near[1:, :, :] |= c[:-1, :, :]
    near[:-1, :, :] |= c[1:, :, :]
    near &= g.lab == lab
    g.paint(near, lambda m: np.where((p.zz[m] < p.ro[m] - 1.0)[:, None], np.array(EMBER, float),
                                     np.array(FRESH, float) * g.noise[m][:, None]),
            glow=None)
    hotm = near & (p.zz < p.ro - 1.0) & (p.zz > p.ro - 1.5) & (np.modf(g.noise * 29)[0] < 0.6)
    g.paint(near & (p.zz < p.ro - 1.0) & ~hotm, lambda m: np.array(FRESH, float) * 0.8 * g.noise[m][:, None])
    g.glow[hotm] = True
    g.flat[hotm] = True


# ---------------------------------------------------------------- 継ぎ目 (回った骨、幅 0.5)

class Seam:
    def __init__(self, name, phi, seed, parent):
        self.name, self.phi, self.parent = name, phi, parent
        self.g = Grid((-0.75, CY - 9, 2.5), (0.75, CY + 9, 13.5), seed)
        self.v = self.g.Y - CY
        self.k = np.clip(np.floor((self.v + 9) / BAND_H).astype(int), 0, NB - 1)
        self.ro = np.array([band_r(k) for k in range(NB)])[self.k]

    def bone(self):
        return {"name": self.name, "pivot": [0, CY, 0], "rotation": [0, 90 - self.phi, 0], "parent": self.parent}


def build_seam(s: Seam, opt, ph, vmax=9.0):
    g = s.g
    g.carve(g.Y - CY > vmax)
    # 3 区間 (上・中・下) ごとに、外の端を一定に (箱を減らす)
    sec = np.select([s.v < -4.5, s.v < 4.5], [0, 1], 2)
    top = np.array([min(band_r(k) for k in ks) for ks in ([0, 1, 2], range(3, 9), [9, 10, 11])])[sec]
    dep = 2.5 if opt != "c" else 1.75
    inner = np.array([band_r(1) - dep, band_r(5) - dep, band_r(10) - dep])[sec]
    if opt == "c":
        # 血管の継ぎ目: 外の端まで出る赤い管、芯は光る
        m = (g.Z > inner) & (g.Z <= top - 0.5) & (g.Y - CY < vmax)
        tube_m = m & (g.Z > top - 2.0) & (np.abs(g.X) < 0.75)
        g.put(s.name, m & ~tube_m, EMBER if ph == 1 else AMBER, glow=True)
        g.put(s.name, tube_m, lambda mm: np.where((np.modf(g.Y[mm] / 1.5)[0] < 0.34)[:, None],
                                                  np.array(FLESH_L, float), np.array(ART, float)))
        return
    m = (g.Z > inner) & (g.Z <= top - 0.5) & (g.Y - CY < vmax)
    hot = m & (np.abs(g.X) < 0.25)
    c_hot = {1: HOT, 2: AMBER, 3: EMBER}[ph]
    c_side = {1: EMBER, 2: EMBER_D, 3: (190, 40, 30)}[ph]
    g.put(s.name, hot, c_hot, glow=True)
    g.put(s.name, m & ~hot, lambda mm: np.where((g.Z[mm] > top[mm] - 1.5)[:, None], np.array(c_side, float),
                                                 np.array(c_hot, float) * 0.85), glow=True)


# ---------------------------------------------------------------- 共有の格子の部品

def crown(g, opt, ph, horns=False):
    """殻の上の冠 (y 21 から上)"""
    stone = lambda m: mix(m, g, RIM_D, STONE[0], 0.4)
    if opt == "c":
        # 肉の結び目: 石の低い蓋 + 突き出す血管 (弁の口)
        g.put("crown", slab(g, 21, 22, 0, 0, 7.0), stone)
        g.put("crown", slab(g, 22, 22.5, 0, 0, 5.0), lambda m: mix(m, g, FLESH, FLESH_D, 0.35))
        vessels = [((1.5, 22.5, 0.5), 4.0, 1.5, ART), ((-2.0, 22.5, 1.5), 2.5, 1.25, ART_L),
                   ((2.5, 22.5, -2.5), 2.0, 1.0, VEIN_C), ((-1.5, 22.5, -2.5), 3.0, 1.0, ART)]
        for (x, y, z), L, r, c in vessels:
            m = tube(g, (x, y, z), (x, y + L, z), r)
            g.put("crown", m, lambda mm, c=c: np.array(c, float) * g.noise[mm][:, None])
            lip = m & (g.Y > y + L - 0.5)
            g.paint(lip, VALVE)
            lumen = lip & (np.abs(g.X - x) < r * 0.5) & (np.abs(g.Z - z) < r * 0.5)
            g.carve(lumen)
        return
    g.put("crown", slab(g, 21, 22, 0, 0, 7.5), lambda m: np.where(
        (~octa_xz(g, 0, 0, 7.0)[m])[:, None], np.array(RIM, float) * g.noise[m][:, None],
        np.array(STONE[0], float) * g.noise[m][:, None]))
    g.put("crown", slab(g, 22, 23, 0, 0, 5.5), stone)
    g.put("crown", slab(g, 23, 23.5, 0, 0, 3.5), stone)
    if opt == "b":
        # 古い冠: 段をもう 1 つと、骨の尖塔
        g.put("crown", slab(g, 23.5, 24.5, 0, 0, 2.0), lambda m: mix(m, g, BONE_D, BONE, 0.4))
        g.put("crown", box(g, (-0.5, 24.5, -0.5), (0.5, 27.0, 0.5)), lambda m: mix(m, g, BONE, BONE_D, 0.3))
        g.put("crown", box(g, (-1.0, 25.0, -1.0), (1.0, 25.5, 1.0)), BONE_DD)
        # 8 本の骨の角 (外へ反る)
        for a in range(8):
            th = math.radians(a * 45 + 22.5)
            cx, cz = round(6.5 * math.cos(th) * 2) / 2, round(6.5 * math.sin(th) * 2) / 2
            ox, oz = np.sign(cx) * 0.5, np.sign(cz) * 0.5
            g.put("crown", box(g, (cx - 0.5, 22, cz - 0.5), (cx + 0.5, 23.5, cz + 0.5)), BONE_D)
            g.put("crown", box(g, (cx - 0.5 + ox, 23.5, cz - 0.5 + oz), (cx + ox, 24.5, cz + oz)), BONE)
        # 冠の上面の刻み (放射の線)
        top = g.up_open() & (g.lab == g.label("crown")) & (g.Y < 23.5)
        ang = np.degrees(np.arctan2(g.Z, g.X)) % 45
        g.paint(top & ((ang < 3) | (ang > 42)), CARVE)
    else:
        g.put("crown", box(g, (-1.5, 23.5, -1.5), (1.5, 24.5, 1.5)), lambda m: mix(m, g, BONE_D, BONE, 0.5))
        g.put("crown", box(g, (-0.5, 24.5, -0.5), (0.5, 25.0, 0.5)), BONE)
        # 骨の鋲 8 個
        for a in range(8):
            th = math.radians(a * 45 + 22.5)
            cx, cz = round(6.25 * math.cos(th) * 2) / 2, round(6.25 * math.sin(th) * 2) / 2
            g.put("crown", box(g, (cx - 0.5, 22, cz - 0.5), (cx + 0.5, 22.5, cz + 0.5)), BONE)
    # 冠の光るひび (上面、色だけ)
    top = g.up_open() & (g.lab == g.label("crown"))
    ang = np.degrees(np.arctan2(g.Z, g.X))
    r = np.hypot(g.X, g.Z)
    cr = top & (np.abs(((ang + 360) % 90) - 45 - 3 * np.sin(r * 1.7)) < 2.2) & (r > 2.0) & (g.Y < 23)
    g.paint(cr, EMBER if ph == 1 else AMBER, glow=True)


def spikes(g, opt):
    """第2段階: 冠の縁から外へ反る骨の棘 4 本"""
    for a in range(4):
        th = math.radians(a * 90 + 45)
        sx, sz = np.sign(math.cos(th)), np.sign(math.sin(th))
        cx, cz = 4.0 * sx, 4.0 * sz
        tiers = [(3.0, 1.0), (2.5, 1.5), (2.0, 1.5), (1.5, 1.5), (1.0, 1.0), (0.5, 1.0)]
        if opt == "b":
            tiers = [(3.0, 1.0), (2.5, 1.0), (2.0, 1.5), (1.5, 1.5), (1.5, 1.0), (1.0, 1.0), (0.5, 1.5)]
        y = 22.0
        off = 0.0
        for n, (w, h) in enumerate(tiers):
            a0, a1 = 3.0 + off, 3.0 + off + w          # 内の端から外へ (0.5 の格子にそろう)
            xa, xb = (a0, a1) if sx > 0 else (-a1, -a0)
            za, zb = (a0, a1) if sz > 0 else (-a1, -a0)
            col = BONE if n % 2 == 0 else BONE_D
            g.put("spikes", box(g, (xa, y, za), (xb, y + h, zb)), col)
            y += h
            off += 0.5 if n < 2 else 1.0
        # 節のふくらみ (骨の関節) は根元に
        pass


def foot(g, opt, ph):
    """下の足 (y 0..3) と、地を這う根 (tendrils)"""
    st = lambda m: mix(m, g, STONE_D, STONE[2], 0.4)
    g.put("base", slab(g, 2, 3, 0, 0, 6.0), st)
    g.put("base", slab(g, 1, 2, 0, 0, 5.0), st)
    g.put("base", slab(g, 0, 1, 0, 0, 6.5), lambda m: mix(m, g, STONE_D, MOSS, 0.25))
    # 根: 4 本は軸に沿って、4 本は斜めに段で
    fl = lambda m: np.where((np.modf(g.noise[m] * 11)[0] < 0.25)[:, None], np.array(FLESH_L, float),
                            np.array(FLESH, float) * g.noise[m][:, None])
    n_roots = 8 if opt == "c" else 4
    for a in range(4):
        th = a * 90
        dx, dz = round(math.cos(math.radians(th))), round(math.sin(math.radians(th)))
        p0 = (dx * 6.0, 0.75, dz * 6.0)
        p1 = (dx * 10.5, 0.75, dz * 10.5)
        g.put("tendrils", tube(g, p0, p1, 0.75), fl)
        tip = (dx * 10.5 + dx * 0.75, 0.5, dz * 10.5 + dz * 0.75)
        g.put("tendrils", box(g, (tip[0] - 0.5, 0, tip[2] - 0.5), (tip[0] + 0.5, 1.0, tip[2] + 0.5)), BONE)
    if n_roots > 4:
        for a in range(4):
            sx, sz = (1, 1, -1, -1)[a], (1, -1, 1, -1)[a]
            x, z = 4.5 * sx, 4.5 * sz
            for s in range(4):
                g.put("tendrils", box(g, (min(x, x + sx * 1.5), 0, min(z, z + sz * 1.5)),
                                      (max(x, x + sx * 1.5), 1.0, max(z, z + sz * 1.5))), fl)
                x += sx * 1.0
                z += sz * 1.0


def heart_body(g, opt, ph, clip):
    """心臓。ph 1 は小さい塊、2 は部屋と血管と弁、3 は大きく胸が開いて心核が見える"""
    if ph == 1:
        hm = heart_shape(g, (0, 11.5, 0), 5.0) & clip
        g.put("heart", hm, lambda m: mix(m, g, FLESH_D, FLESH, 0.3))
        return
    big = ph == 3
    hy = 12.0 if not big else 13.0
    R = 6.5 if not big else 8.0
    if opt == "c":
        R += 0.5
    vessels = np.zeros(g.n, bool)
    vcol = []

    def V(p0, p1, r, col, valve=True):
        m = tube(g, p0, p1, r) & clip
        vcol.append((m, col, "put"))
        if valve:
            # 管の口: 薄い色の弁の輪、真ん中に 3 枚の弁の線、口は 0.5 くぼむ
            ax = int(np.argmax(np.abs(np.array(p1, float) - np.array(p0, float))))
            P = (g.X, g.Y, g.Z)[ax]
            end = p1[ax]
            sgn = 1 if p1[ax] > p0[ax] else -1
            lip = m & (sgn * (P - end) > -0.5)
            vcol.append((lip, VALVE, "paint"))
            o = [i for i in range(3) if i != ax]
            Q = (g.X, g.Y, g.Z)
            a, b = Q[o[0]] - p1[o[0]], Q[o[1]] - p1[o[1]]
            lumen = lip & (np.abs(a) < r - 0.5) & (np.abs(b) < r - 0.5)
            cusp = lumen & ((np.abs(a) < 0.3) | (np.abs(b) < 0.3))
            vcol.append((lumen & ~cusp, None, "carve"))
        return m

    s = 1.0 if not big else 1.2
    # 大動脈: 上へ、弓、後ろへ下る。弓の上に 3 本の枝
    ay = hy + R * 0.55
    V((1.0 * s, hy + 2, 0.5), (1.0 * s, ay + 3.0 * s, 0.5), 1.5 * s, ART, valve=False)
    V((1.0 * s, ay + 2.0 * s, 0.5), (1.0 * s, ay + 2.0 * s, -4.0 * s), 1.25 * s, ART, valve=False)
    V((1.0 * s, ay + 2.0 * s, -4.0 * s), (1.0 * s, hy, -4.0 * s), 1.25 * s, ART, valve=False)
    for zb in (-0.5, -2.0, -3.5):
        V((1.0 * s, ay + 3.0 * s, zb * s), (1.0 * s, ay + 4.5 * s, zb * s), 0.5, ART_L, valve=False)
    # 肺動脈: 前の左へ
    V((-1.5 * s, hy + 2, 2.5 * s), (-1.5 * s, ay + 1.5 * s, 2.5 * s), 1.25 * s, ART_L, valve=False)
    V((-1.5 * s, ay + 0.75 * s, 2.5 * s), (-5.0 * s, ay + 0.75 * s, 2.5 * s), 0.75 * s, ART_L)
    # 上大静脈 (紫)
    V((4.0 * s, hy + 2, -1.5 * s), (4.0 * s, ay + 2.5 * s, -1.5 * s), 1.0 * s, VEIN_C)
    if opt == "c":
        # 解剖寄り: 肺静脈 4 本、冠動脈の太い枝
        for (x, z) in ((-3.5, -2.5), (3.0, -3.5)):
            V((x * s, hy + 3.0, z * s), (x * s, hy + 3.0, (z - 2.5) * s), 0.5 * s + 0.25, VEIN_C)
        V((-4.5 * s, hy + 1.0, 1.0), (-6.5 * s, hy + 1.0, 1.0), 0.75, VEIN_C)
    # 下大静脈 (足までつながる柄)
    V((2.5, hy - R * 0.6, -1.5), (2.5, 3.0, -1.5), 1.0 if not big else 1.25, VEIN_C, valve=False)
    # 心房 2 つ
    atria = (ell(g, (-3.0 * s, hy + 3.0 * s, -1.0 * s), (2.5 * s, 2.0 * s, 2.5 * s), coarse=True)
             | ell(g, (3.0 * s, hy + 3.0 * s, -1.0 * s), (2.5 * s, 2.0 * s, 2.5 * s), coarse=True)) & clip
    vent = heart_shape(g, (0, hy - 0.5, 0.5), R) & clip
    # 先に血管、次に心房と心室 (血管が表に出る)
    for m, col, mode in vcol:
        if mode == "put":
            g.put("heart_veins", m, lambda mm, c=col: np.array(c, float) * g.noise[mm][:, None])
    for m, col, mode in vcol:
        if mode == "paint":
            g.paint(m & (g.lab == g.label("heart_veins")), col)
        elif mode == "carve":
            g.carve(m & (g.lab == g.label("heart_veins")))
    g.put("heart", atria, lambda m: mix(m, g, FLESH_D, FLESH, 0.4))
    g.put("heart", vent, lambda m: mix(m, g, FLESH, FLESH_L, 0.08))
    lab = g.label("heart")
    hs = g.surface() & (g.lab == lab)
    # 心房と心室の境の溝 (冠状溝) に脂肪と冠動脈
    groove = hs & (np.abs(g.Y - (hy + 1.5 * s)) < 0.5)
    g.paint(groove, FAT if opt != "b" else FLESH_DD)
    # 冠動脈 (前の溝): 斜めの線
    ant = hs & (np.abs(g.X - (-0.4 * (g.Y - hy) - 0.5)) < 0.4) & (g.Z > 0) & (g.Y < hy + 1.5 * s)
    circ = hs & (np.abs(g.Y - (hy + 1.0 * s)) < 0.3)
    glow_col = AMBER if ph == 2 else RED_GLOW
    if opt == "c":
        # 細かい枝の血管 (色) を多く
        br = hs & (np.abs(np.sin(g.X * 1.3 + g.Y * 0.6) * np.cos(g.Z * 1.1 - g.Y * 0.4)) < 0.09) & (g.Y < hy + 1.5)
        g.paint(br, ART_L)
        # 表に盛り上がった冠動脈 (0.5 の管): 冠状溝を一周、前の溝を斜めに下りる。芯は光る
        occ = g.lab == lab
        adj = np.zeros(g.n, bool)
        for ax in range(3):
            for sh in (1, -1):
                adj |= np.roll(occ, sh, axis=ax)
        adj &= (g.lab == 0) & clip
        ring = adj & (np.abs(g.Y - (hy + 1.0 * s)) < 0.5)
        lad = adj & (np.abs(g.X - (-0.4 * (g.Y - hy) - 0.5)) < 0.5) & (g.Z > 0) & (g.Y < hy + 1.0 * s)
        rca = adj & (np.abs(g.X - (0.5 * (g.Y - hy) + 3.0)) < 0.5) & (g.Z > -1) & (g.Y < hy) & (g.Y > hy - R * 0.6)
        cor = ring | lad | rca
        g.put("heart_veins", cor, lambda m: np.array(ART_L, float) * g.noise[m][:, None])
        g.paint(cor & (np.modf(g.noise * 13)[0] < 0.22), glow_col, glow=True)
        # 脂肪の帽子 (冠状溝の上、薄い黄色)
        g.paint(hs & (np.abs(g.Y - (hy + 1.5 * s)) < 0.75) & (np.modf(g.noise * 17)[0] < 0.6), FAT)
    else:
        veins = hs & (np.abs(np.sin(1.1 * g.Xc + 0.7 * g.Yc) * np.cos(0.9 * g.Zc)) < 0.10) & (g.Y < hy + 1.0)
        g.paint(veins, glow_col, glow=True)
        g.paint(ant, ART_L)
    # 心室の筋 (色の帯)
    if opt == "b":
        # 鉄の帯で縛られた心臓: 冠状溝の高さに 0.5 の帯、骨の鋲
        occ = g.lab == lab
        adj = np.zeros(g.n, bool)
        for ax in range(3):
            for sh in (1, -1):
                adj |= np.roll(occ, sh, axis=ax)
        hb = hy + 1.0 * s
        ring = adj & (g.lab == 0) & (np.abs(g.Y - hb) < 0.5) & clip
        g.put("heart", ring, lambda m: np.where((np.abs(np.degrees(np.arctan2(g.Z[m], g.X[m])) % 40) < 4)[:, None],
                                                np.array(BONE, float), np.array(IRON, float) * g.noise[m][:, None]))
    if big:
        # 胸を開く: 2 つの部屋 (左の部屋に心核)、中の壁 (中隔) は残す
        cavA = ell(g, (-1.75, hy + 0.5, 5.0), 3.25)
        cavB = ell(g, (3.25, hy - 1.0, 4.5), 2.25)
        cav = (cavA | cavB) & (g.lab == lab)
        g.carve(cav)
        wall = g.surface() & (g.lab == lab)
        nearcav = np.zeros(g.n, bool)
        cc = cavA | cavB
        for ax in range(3):
            for sh in (1, -1):
                nearcav |= np.roll(cc, sh, axis=ax)
        inner = wall & nearcav
        g.paint(inner, lambda m: mix(m, g, FLESH_DD, FLESH_D, 0.3))
        rimc = inner & (g.Z > (hy * 0 + 4.0)) & ~cc
        g.paint(rimc & ell(g, (-1.75, hy + 0.5, 5.0), 3.75), RED_GLOW, glow=True)
        g.paint(rimc & ell(g, (3.25, hy - 1.0, 4.5), 2.75), EMBER_D, glow=True)
        # 心核 (白赤) と、それを奥の壁につなぐ柄 (stalk)
        core = ell(g, (-1.75, hy + 0.5, 4.0), 2.25)
        g.put("core", core & (g.lab == 0), lambda m: np.where(
            (ell(g, (-2.25, hy + 1.0, 4.5), 1.25)[m])[:, None], np.array(CORE, float),
            np.where((np.modf(g.noise[m] * 7)[0] < 0.3)[:, None], np.array(RED_GLOW, float),
                     np.array((255, 196, 186), float))), glow=True)
        stalk = (tube(g, (-1.75, hy + 0.5, 1.75), (-1.75, hy + 0.5, 0.0), 0.75)
                 | tube(g, (-1.75, hy + 2.5, 3.5), (-1.75, hy + 4.0, 3.5), 0.5))
        g.put("stalk", stalk & (g.lab == 0), lambda m: mix(m, g, BONE_D, ART_L, 0.4))
        # 中の部屋の弁 (3 枚の白い弁) — 右の部屋の奥
        valve = ell(g, (3.25, hy - 1.0, 3.0), 1.25) & (g.lab == 0) & (g.Z < 3.25)
        g.put("heart", valve, VALVE)


def yoke(g, opt, xs, ytop):
    """第3段階: 心臓の上の骨の天秤 (柱と横木)。鎖を吊る。C は動脈の天秤"""
    bone = lambda m: np.where((np.modf(g.Y[m] / 1.5)[0] < 0.33)[:, None], np.array(BONE_D, float),
                              np.array(BONE, float) * g.noise[m][:, None])
    if opt == "c":
        bone = lambda m: np.where((np.modf(g.X[m] / 2.0 + g.Y[m] / 3.0)[0] < 0.2)[:, None], np.array(ART_L, float),
                                  np.array(ART, float) * g.noise[m][:, None])
    g.put("chains", tube(g, (0, 19.0, 0), (0, ytop + 1.0, 0), 1.0), bone)
    # 柱の節 (椎骨のふくらみ)
    for y in (20.5, 23.0):
        g.put("chains", slab(g, y, y + 1.0, 0, 0, 1.75) & (g.lab == 0), BONE_D if opt != "c" else VALVE)
    # 横木: 中央が高く、端が 0.5 ずつ下がる
    xe = max(abs(x) for x in xs) + 1.0
    for sx in (1, -1):
        x = 1.0
        y = ytop
        while x < xe:
            x1 = min(x + 3.0, xe)
            g.put("chains", box(g, (min(sx * x, sx * x1), y, -0.75), (max(sx * x, sx * x1), y + 1.5, 0.75)), bone)
            x = x1
            y -= 0.5
    if opt == "b":
        # 横木の端の飾り (骨の輪)
        for xc in xs:
            sx = np.sign(xc)
            g.put("chains", box(g, (sx * xe - (0.5 if sx > 0 else -0.5) - 0.5, ytop - 3, -1.0),
                                (sx * xe - (0.5 if sx > 0 else -0.5) + 0.5, ytop + 2.5, 1.0)) & (g.lab == 0), BONE_D)


# ---------------------------------------------------------------- 組み立て

def clip_inside(g, rad_fn, ylo, yhi, margin=0.6):
    """殻の内側 (八角形の 16 方向で、内面から margin 離れる) の範囲"""
    ok = (g.Y - 0.25 > ylo) & (g.Y + 0.25 < yhi)
    v = g.Y - CY
    kk = np.clip(np.floor((v + 9) / BAND_H).astype(int), 0, NB - 1)
    rin = np.array([rad_fn(k) for k in range(NB)])[kk]
    for a in range(16):
        th = math.radians(a * 22.5)
        proj = g.X * math.cos(th) + g.Z * math.sin(th) + 0.25 * (abs(math.cos(th)) + abs(math.sin(th)))
        ok &= proj < rin - margin
    return ok


def build_phase(opt, ph):
    """1 つの段階の骨の一覧 [(bone dict, Grid or None)] を作る"""
    seed = 20261015 + ph * 7 + ord(opt)
    G = Grid((-16, -1, -16), (16, 30, 16), seed)
    rot_parts = []      # (bone dict, grid)
    groups = []         # 立方体のない親の骨
    thick = 2.0 if opt != "c" else 1.5
    rin = lambda k: band_r(k) - thick

    if ph in (1, 2):
        keep = list(range(8)) if ph == 1 else [0, 1, 2, 3, 6, 7]
        for i in keep:
            p = Plate(f"plate_{i}", 22.5 + 45 * i, seed + i)
            build_plate(p, opt, ph, i, thick=thick)
            if ph == 2 and i == 3:
                broken_edge(p, -1, seed + 50)
            if ph == 2 and i == 6:
                broken_edge(p, +1, seed + 60)
            rot_parts.append((p.bone(), p.g))
        grp = "seams" if ph == 1 else "cracks"
        groups.append({"name": grp, "pivot": [0, CY, 0]})
        seams = list(range(8)) if ph == 1 else [0, 1, 2, 3, 7]
        for kq in seams:
            s = Seam(f"{grp[:-1]}_{kq}", 45 * kq, seed + 100 + kq, grp)
            build_seam(s, opt, ph)
            rot_parts.append((s.bone(), s.g))
        crown(G, opt, ph)
        if ph == 2:
            spikes(G, opt)
        foot(G, opt, ph)
        clip = clip_inside(G, rin, 3.0, 21.0, margin=1.2)
        heart_body(G, opt, ph, clip)
    else:
        # 第3段階: 受け皿 (下 4 帯 = v -9..-3) と、鎖で吊られた殻 2 枚
        groups.append({"name": "bowl", "pivot": [0, 6.0, 0]})
        for i in range(8):
            p = Plate(f"bowl_{i}", 22.5 + 45 * i, seed + i, parent="bowl")
            kr = (0, 4)
            build_plate(p, opt, ph, i, krange=kr, thick=thick)
            rot_parts.append((p.bone(), p.g))
        for kq in range(8):
            s = Seam(f"bowl_seam_{kq}", 45 * kq, seed + 100 + kq, "bowl")
            build_seam(s, opt, ph, vmax=-3.0)
            rot_parts.append((s.bone(), s.g))
        foot(G, opt, ph)
        groups.append({"name": "shard", "pivot": [0, 20.0, 0]})
        dz = 3.5
        zc = band_r(11) - thick / 2 + dz       # 吊る点: 上の帯の中央 (板の座標 z)
        xs = [zc, -zc]
        # 心臓 (受け皿の中は内面で切る。上は自由)
        clip = clip_inside(G, rin, 3.0, 9.0, margin=1.2) | ((G.Y - 0.25 > 9.0) & (np.hypot(G.X, G.Z) < 11))
        heart_body(G, opt, ph, clip & (G.Y < 22))
        # 天秤と鎖
        ytop = 26.0
        yoke(G, opt, xs, ytop)
        nlinks = 4
        for n, (x, phi) in enumerate(zip(xs, (0.0, 180.0))):
            cx = math.floor(x) + 0.25
            # 横木の下面 (鎖の上端) = その x の横木の段の下面
            col_x = np.abs(G.X - cx) < 0.3
            ys = G.Y[col_x & (G.lab == G.label("chains")) & (np.abs(G.Z) < 0.5)]
            y_under = float(ys.min() - 0.25)
            chain_v(G, "chains", cx, 0.25, y_under, nlinks, first_x=False)
            y_bottom = y_under - 2 * (nlinks - 1) - 3
            dy = y_bottom - (CY + 9.0)        # 殻の上端 (v=9) を鎖の下端に合わせる
            p = Plate(f"shard_{n}", phi, seed + 30 + n, dz=dz, dy=dy, parent="shard")
            build_plate(p, opt, ph, 2 + n * 3, krange=(7, 12), thick=thick)
            rot_parts.append((p.bone(), p.g))
    # 共有の格子の骨
    bones = []
    for name, lab in G.names.items():
        bones.append(({"name": name, "pivot": pivot_of(G, lab)}, G, lab))
    return G, bones, rot_parts, groups


def pivot_of(g, lab):
    m = g.lab == lab
    if not m.any():
        return [0, CY, 0]
    return [round(float(g.X[m].mean()) * 2) / 2, round(float(g.Y[m].mean()) * 2) / 2,
            round(float(g.Z[m].mean()) * 2) / 2]


# ---------------------------------------------------------------- 陰影

def shade(g, labs, R):
    """セルの色に、向きの明るさと角の暗さを焼き込む (光るセルは除く)。R は骨の回転"""
    occ = np.isin(g.lab, labs)
    e = ~np.pad(occ, 1)
    nx = e[2:, 1:-1, 1:-1].astype(float) - e[:-2, 1:-1, 1:-1]
    ny = e[1:-1, 2:, 1:-1].astype(float) - e[1:-1, :-2, 1:-1]
    nz = e[1:-1, 1:-1, 2:].astype(float) - e[1:-1, 1:-1, :-2]
    nexp = (e[2:, 1:-1, 1:-1].astype(int) + e[:-2, 1:-1, 1:-1] + e[1:-1, 2:, 1:-1]
            + e[1:-1, :-2, 1:-1] + e[1:-1, 1:-1, 2:] + e[1:-1, 1:-1, :-2])
    n = np.stack([nx, ny, nz], -1) @ R.T
    nl = np.linalg.norm(n, axis=-1)
    lamb = np.where(nl > 0, (n @ LIGHT) / np.maximum(nl, 1e-9), 0.0)
    lum = 0.66 + 0.42 * np.clip(lamb, 0, 1)
    lum = lum * np.where(nexp >= 3, 0.84, 1.0)
    m = occ & ~g.flat
    out = g.col.copy()
    out[m] = np.clip(g.col[m] * lum[m][:, None], 0, 255)
    return out


# ---------------------------------------------------------------- 箱に分ける

def greedy(occ):
    best = None
    for perm in ((0, 1, 2), (0, 2, 1), (1, 0, 2), (1, 2, 0), (2, 0, 1), (2, 1, 0)):
        A = np.transpose(occ, perm).copy()
        used = np.zeros_like(A)
        n0, n1, n2 = A.shape
        boxes = []
        for i, j, k in np.argwhere(A):
            if used[i, j, k]:
                continue
            k1 = k + 1
            while k1 < n2 and A[i, j, k1] and not used[i, j, k1]:
                k1 += 1
            j1 = j + 1
            while j1 < n1 and A[i, j1, k:k1].all() and not used[i, j1, k:k1].any():
                j1 += 1
            i1 = i + 1
            while i1 < n0 and A[i1, j:j1, k:k1].all() and not used[i1, j:j1, k:k1].any():
                i1 += 1
            used[i:i1, j:j1, k:k1] = True
            lo, hi = [0, 0, 0], [0, 0, 0]
            for a, (l, h) in enumerate(((i, i1), (j, j1), (k, k1))):
                lo[perm[a]], hi[perm[a]] = l, h
            boxes.append((*lo, *hi))
        if best is None or len(boxes) < len(best):
            best = boxes
    return best


FACES = {
    # load_geo と同じ角と向き (セルの座標で)
    "north": (lambda b: (b[3], b[1], b[2]), lambda s: (-s[0], 0, 0), lambda s: (0, s[1], 0), (0, 0, -1)),
    "south": (lambda b: (b[0], b[1], b[5]), lambda s: (s[0], 0, 0), lambda s: (0, s[1], 0), (0, 0, 1)),
    "east": (lambda b: (b[3], b[1], b[5]), lambda s: (0, 0, -s[2]), lambda s: (0, s[1], 0), (1, 0, 0)),
    "west": (lambda b: (b[0], b[1], b[2]), lambda s: (0, 0, s[2]), lambda s: (0, s[1], 0), (-1, 0, 0)),
    "up": (lambda b: (b[0], b[4], b[5]), lambda s: (s[0], 0, 0), lambda s: (0, 0, -s[2]), (0, 1, 0)),
    "down": (lambda b: (b[0], b[1], b[2]), lambda s: (s[0], 0, 0), lambda s: (0, 0, s[2]), (0, -1, 0)),
}


def export(opt, ph):
    G, gbones, rot_parts, groups = build_phase(opt, ph)
    recs = []
    bones_out = []
    all_boxes = []      # SAT 用 (骨の名前, 中心, 軸, 半分の大きさ)

    def emit(bone, g, labmask, R, t):
        col = shade(g, np.unique(g.lab[g.lab > 0]), R)
        occ_all = g.lab > 0
        pad = np.pad(occ_all, 1)
        cubes = []
        for bx in greedy(labmask):
            x0, y0, z0, x1, y1, z1 = bx
            s = (x1 - x0, y1 - y0, z1 - z0)
            cube = {"origin": [float(g.lo[0] + x0 * H), float(g.lo[1] + y0 * H), float(g.lo[2] + z0 * H)],
                    "size": [s[0] * H, s[1] * H, s[2] * H], "uv": {}}
            for fname, (cof, euf, evf, nrm) in FACES.items():
                co = np.array(cof(bx), float)
                eu = np.array(euf(s), float)
                ev = np.array(evf(s), float)
                W = int(round(np.abs(eu).sum()))
                Hh = int(round(np.abs(ev).sum()))
                rr, cc = np.meshgrid(np.arange(Hh), np.arange(W), indexing="ij")
                tt = (rr + 0.5) / Hh if fname == "down" else 1 - (rr + 0.5) / Hh
                pos = co[None, None, :] + ((cc + 0.5) / W)[..., None] * eu + tt[..., None] * ev
                cell = np.floor(pos - 0.5 * np.array(nrm)).astype(int)
                xi, yi, zi = cell[..., 0], cell[..., 1], cell[..., 2]
                nb = pad[xi + 1 + nrm[0], yi + 1 + nrm[1], zi + 1 + nrm[2]]
                if nb.all():
                    continue
                tex = np.zeros((Hh, W, 4), np.uint8)
                tex[..., :3] = np.clip(col[xi, yi, zi], 0, 255).astype(np.uint8)
                tex[..., 3] = 255
                recs.append({"W": W, "H": Hh, "tex": tex, "gl": g.glow[xi, yi, zi], "cube": cube, "face": fname})
            cubes.append(cube)
            c_local = np.array(cube["origin"]) + np.array(cube["size"]) / 2
            all_boxes.append((bone["name"], R @ c_local + t, R, np.array(cube["size"]) / 2))
        b = dict(bone)
        b["cubes"] = cubes
        if b.get("parent") is None:
            b.pop("parent", None)
        bones_out.append(b)

    I3 = np.eye(3)
    for grp in groups:
        bones_out.append(dict(grp, cubes=[]))
    for bone, g, lab in gbones:
        emit(bone, g, g.lab == lab, I3, np.zeros(3))
    for bone, g in rot_parts:
        r = bone["rotation"]
        R = _rot(*r)
        piv = np.array(bone["pivot"], float)
        emit(bone, g, g.lab > 0, R, piv - R @ piv)
    # 並べ替え: 親が先
    order = {b["name"]: n for n, b in enumerate(bones_out)}
    bones_out.sort(key=lambda b: (0 if "parent" not in b else 1, order[b["name"]]))

    # アトラス
    recs.sort(key=lambda r: (-r["H"], -r["W"]))
    x = y = shelf = 0
    for r in recs:
        if x + r["W"] > AW:
            x, y, shelf = 0, y + shelf, 0
        r["u"], r["v"] = x, y
        x += r["W"]
        shelf = max(shelf, r["H"])
    AH = y + shelf
    tex = np.zeros((AH, AW, 4), np.uint8)
    gtex = np.zeros((AH, AW, 4), np.uint8)
    for r in recs:
        u, v, W, Hh = r["u"], r["v"], r["W"], r["H"]
        tex[v:v + Hh, u:u + W] = r["tex"]
        gtex[v:v + Hh, u:u + W, :3] = r["tex"][..., :3] * r["gl"][..., None]
        gtex[v:v + Hh, u:u + W, 3] = np.where(r["gl"], 255, 0)
        r["cube"]["uv"][r["face"]] = {"uv": [u, v], "uv_size": [W, Hh]}
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.skyabyss.stone_whale_heart.p{ph}",
                        "texture_width": AW, "texture_height": AH,
                        "visible_bounds_width": 5, "visible_bounds_height": 5,
                        "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones_out}]}
    stats = {"cubes": sum(len(b["cubes"]) for b in bones_out),
             "bones": {b["name"]: len(b["cubes"]) for b in bones_out},
             "faces": len(recs), "atlas": [AW, AH],
             "check_overlaps": check_overlaps(geo), "sat": sat_overlaps(all_boxes)}
    return geo, tex, gtex, stats


def sat_overlaps(boxes, tol=1e-3):
    """全ての箱の組 (違う骨どうし) を、分離軸の判定で調べる。接するだけは可"""
    n = len(boxes)
    C = np.array([b[1] for b in boxes])
    ext = np.array([np.abs(b[2]) @ b[3] for b in boxes])   # 世界の軸の外接箱の半分
    lo, hi = C - ext, C + ext
    out = []
    for i in range(n):
        cand = np.nonzero(((np.minimum(hi[i], hi[i + 1:]) - np.maximum(lo[i], lo[i + 1:])) > tol).all(1))[0] + i + 1
        for j in cand:
            if boxes[i][0] == boxes[j][0]:
                continue
            if _sat(boxes[i], boxes[j], tol):
                out.append(f"{boxes[i][0]} x {boxes[j][0]} at {np.round(C[i], 2).tolist()}")
    return out


def _sat(a, b, tol):
    _, ca, Ra, ea = a
    _, cb, Rb, eb = b
    axes = [Ra[:, i] for i in range(3)] + [Rb[:, i] for i in range(3)]
    for i in range(3):
        for j in range(3):
            c = np.cross(Ra[:, i], Rb[:, j])
            nn = np.linalg.norm(c)
            if nn > 1e-6:
                axes.append(c / nn)
    d = cb - ca
    for L in axes:
        ra = np.sum(ea * np.abs(Ra.T @ L))
        rb = np.sum(eb * np.abs(Rb.T @ L))
        if abs(d @ L) >= ra + rb - tol:
            return False
    return True


# ---------------------------------------------------------------- 描画とシート

VIEWS = {1: (35, 24), 2: (150, 22), 3: (22, 20)}
# 寄りの絵: 案ごとに見せたい所 (段階, 2 倍の絵の中で切り出す左上の割合)
CLOSE = {"ref": (1, 0.3, 0.2), "a": (1, 0.3, 0.2), "b": (1, 0.3, 0.2), "c": (2, 0.25, 0.25)}
CELLPX = 400


def draw(geo, tex, gtex, ph, size=CELLPX, close=None):
    faces = load_geo(geo, tex, gtex)
    yaw, pitch = VIEWS[ph]
    if not close:
        return render(faces, yaw, pitch, (size, size))
    big = render(faces, yaw, pitch, (size * 2, size * 2), margin=0.05)
    c = size * 2
    x0 = int(c * close[0])
    y0 = int(c * close[1])
    return big.crop((x0, y0, x0 + size, y0 + size))


def label_img(im, text):
    im = im.copy()
    d = ImageDraw.Draw(im)
    d.text((10, 8), text, font=font(17), fill=(220, 220, 232))
    return im


def main():
    only = sys.argv[1:] or ["a", "b", "c"]
    report = {}
    cols = []
    # 今の実装 (参考の列)
    ref = []
    for ph in (1, 2, 3):
        g = ASSET / f"geo/entity/stone_whale_heart_p{ph}.geo.json"
        t = ASSET / f"textures/entity/stone_whale_heart_p{ph}.png"
        gm = ASSET / f"textures/entity/stone_whale_heart_p{ph}_glowmask.png"
        geo = json.loads(g.read_text(encoding="utf-8"))
        tex = np.array(Image.open(t).convert("RGBA"))
        gtex = np.array(Image.open(gm).convert("RGBA"))
        n = sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"])
        ref.append(label_img(draw(geo, tex, gtex, ph), f"第{ph}段階  {n} 箱"))
        if ph == CLOSE["ref"][0]:
            ref_close = label_img(draw(geo, tex, gtex, ph, close=CLOSE["ref"][1:]), f"寄り (第{ph}段階)")
    ref.append(ref_close)
    cols.append(("今の実装 (参考)", ref))
    names = {"a": "A 忠実に細かく", "b": "B 古い彫刻と鎖", "c": "C 生き物の心臓"}
    for opt in only:
        ims = []
        d = OUT / opt
        d.mkdir(parents=True, exist_ok=True)
        for ph in (1, 2, 3):
            geo, tex, gtex, st = export(opt, ph)
            (d / f"phase{ph}.geo.json").write_text(json.dumps(geo, ensure_ascii=False, indent=1), encoding="utf-8")
            Image.fromarray(tex).save(d / f"phase{ph}.tex.png")
            Image.fromarray(gtex).save(d / f"phase{ph}.glow.png")
            im = draw(geo, tex, gtex, ph)
            im.save(d / f"phase{ph}.render.png")
            ims.append(label_img(im, f"第{ph}段階  {st['cubes']} 箱"))
            if ph == CLOSE[opt][0]:
                close = label_img(draw(geo, tex, gtex, ph, close=CLOSE[opt][1:]), f"寄り (第{ph}段階)")
                close.save(d / "closeup.render.png")
            report[f"{opt}{ph}"] = {k: st[k] for k in ("cubes", "faces", "atlas", "bones")}
            report[f"{opt}{ph}"]["check_overlaps"] = len(st["check_overlaps"])
            report[f"{opt}{ph}"]["sat"] = len(st["sat"])
            print(opt, ph, st["cubes"], "faces", st["faces"], "atlas", st["atlas"],
                  "ovl", len(st["check_overlaps"]), "sat", len(st["sat"]), st["sat"][:3], st["check_overlaps"][:3])
        ims.append(close)
        cols.append((names[opt], ims))
    (OUT / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
    if len(only) == 3:
        sh = make_sheet(cols)
        sh.save(SHEET)
    else:
        make_sheet(cols).save(OUT / f"preview_{''.join(only)}.png")


def make_sheet(cols):
    pad = 16
    w = CELLPX
    W = pad + (w + pad) * len(cols)
    rows = 4
    head = 96
    Hh = head + 40 + rows * (w + pad) + pad
    out = Image.new("RGB", (W, Hh), (18, 18, 26))
    d = ImageDraw.Draw(out)
    d.text((pad, 14), "岩鯨の心臓 第4回ラフ — 案B「石の殻」を細かく (0.5 単位、2 画素/単位)", font=font(28), fill=(235, 235, 245))
    d.text((pad, 56), "行: 第1段階 (殻 8 枚) / 第2段階 (殻が割れ心臓がのぞく) / 第3段階 (受け皿と吊られた殻、白赤の心核) / 寄り。"
           "左端は今の実装", font=font(17), fill=(170, 170, 190))
    for ci, (label, ims) in enumerate(cols):
        x = pad + ci * (w + pad)
        d.text((x, head), label, font=font(22), fill=(120, 220, 230))
        y = head + 40
        for im in ims:
            out.paste(im, (x, y))
            y += w + pad
    return out


if __name__ == "__main__":
    main()
