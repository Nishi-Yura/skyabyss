"""余燼の心臓 (第4の巨人の最終ボス) ラフ: 案A「炉」 / 案B「熔け殻」 / 案C「黒曜の棘」 x 3段階。

使い方 (リポジトリ直下で): PYTHONIOENCODING=utf-8 python -B tools/rough/ember_heart.py [a b c]
出力: docs/review/ember_roughs/heart/<a|b|c>/phaseN.(geo.json|tex.png|glow.png|render.png|front.png)
      docs/review/ember_roughs/heart/stats.json, docs/review/ember_roughs/heart/bars.png (ボスバーの色案)
      docs/review/ember_roughs/heart.png (比較シート)

作り方 (tide_heart.py と同じ):
- 1 セル = 0.5 単位 = テクスチャ 1 画素 (2 px / 単位)。全ての部品は回さず、1 つの共有の格子に彫る。
  セルの持ち主は 1 つ (lab) なので、箱どうしは格子上で重ならない。箱の体積の合計がセル数と一致することも確かめる。
- 箱は部品 (bone) ごとに貪欲法で作る。
- 心臓は全案で共通の形 (2 つの房 + 3 本の短く曲がった大動脈)。段階ごとに核の大きさと色を変える:
  第1段階 橙黄の強い光 / 第2段階 白熱してむき出し / 第3段階 灰の殻をかぶった暗い赤の残り火。
- 段階が進むほど火が消えていく (余燼)。第3段階の光は少しの画素と、浮かぶ火の粉だけ。
- 乱数は seed 固定。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
from voxel import check_overlaps, font, load_geo, render, sheet  # noqa: E402

OUT = ROOT / "docs/review/ember_roughs/heart"
SHEET = ROOT / "docs/review/ember_roughs/heart.png"
H = 0.5
LO = np.array([-22.0, -6.0, -20.0])
HI = np.array([22.0, 40.0, 20.0])
CX, CY, CZ = 0.0, 16.0, 0.0
AW = 512
REND = 360

LIGHT = np.array([-0.45, 0.75, 0.5])
LIGHT = LIGHT / np.linalg.norm(LIGHT)

# ---------------------------------------------------------------- 色 (RGB)
BASALT = (44, 40, 44)
BASALT_D = (28, 26, 30)
BASALT_L = (70, 64, 66)
ASH = (150, 144, 138)
ASH_W = (220, 214, 204)
ASH_D = (112, 106, 102)
SLAG = (106, 70, 50)
SLAG_D = (72, 48, 38)
BRICK = (156, 64, 44)
BRICK_D = (116, 46, 34)
MORTAR = (128, 116, 104)
IRON = (62, 58, 62)
IRON_D = (40, 38, 42)
WOOD = (96, 64, 42)
LEATHER = (70, 44, 34)
DEEP_RED = (130, 26, 22)
EMBER = (255, 122, 32)
MOLTEN = (255, 204, 68)
WHITE_HOT = (255, 248, 222)
DIM_RED = (196, 58, 30)
OBSID = (40, 28, 56)
OBSID_L = (122, 88, 156)
OBSID_M = (70, 48, 100)


# ---------------------------------------------------------------- 格子 (tide_heart.py と同じ)

class Grid:
    def __init__(self, seed):
        self.n = tuple(int(round((HI - LO)[i] / H)) for i in range(3))
        ax = [LO[i] + (np.arange(self.n[i]) + 0.5) * H for i in range(3)]
        self.X, self.Y, self.Z = np.meshgrid(*ax, indexing="ij")
        self.lab = np.zeros(self.n, np.int16)
        self.glow = np.zeros(self.n, bool)
        self.col = np.zeros(self.n + (3,), float)
        self.names = {}
        self.noise = np.random.default_rng(seed).uniform(0.92, 1.08, self.n)

    def lid(self, name):
        if name not in self.names:
            self.names[name] = len(self.names) + 1
        return self.names[name]

    def put(self, name, m, color, glow=False, over=False):
        if not over:
            m = m & (self.lab == 0)
        if not m.any():
            return
        self.lab[m] = self.lid(name)
        c = color(m) if callable(color) else np.repeat(np.array(color, float)[None], int(m.sum()), 0)
        self.col[m] = c
        self.glow[m] = glow


def sl_of(g, lo, hi):
    lo = np.clip(np.floor((np.asarray(lo, float) - LO) / H).astype(int), 0, np.array(g.n))
    hi = np.clip(np.ceil((np.asarray(hi, float) - LO) / H).astype(int), 0, np.array(g.n))
    return tuple(slice(lo[i], hi[i]) for i in range(3))


def ell(g, c, r, mod=None, coarse=False):
    c = np.asarray(c, float)
    r = np.asarray(r, float)
    out = np.zeros(g.n, bool)
    s = sl_of(g, c - r * 1.2 - 0.6, c + r * 1.2 + 0.6)
    X, Y, Z = g.X[s], g.Y[s], g.Z[s]
    if coarse:
        X, Y, Z = (np.floor(a) + 0.5 for a in (X, Y, Z))
    d = ((X - c[0]) / r[0]) ** 2 + ((Y - c[1]) / r[1]) ** 2 + ((Z - c[2]) / r[2]) ** 2
    if mod is not None:
        d = d / np.maximum(mod(X, Y, Z), 1e-3) ** 2
    out[s] = d <= 1.0
    return out


def box_m(g, lo, hi):
    out = np.zeros(g.n, bool)
    s = sl_of(g, lo, hi)
    X, Y, Z = g.X[s], g.Y[s], g.Z[s]
    out[s] = ((X > lo[0]) & (X < hi[0]) & (Y > lo[1]) & (Y < hi[1]) & (Z > lo[2]) & (Z < hi[2]))
    return out


def tube3(g, pts, r):
    out = np.zeros(g.n, bool)
    for a, b in zip(pts[:-1], pts[1:]):
        a = np.asarray(a, float)
        b = np.asarray(b, float)
        s = sl_of(g, np.minimum(a, b) - r - 0.2, np.maximum(a, b) + r + 0.2)
        P = np.stack([g.X[s] - a[0], g.Y[s] - a[1], g.Z[s] - a[2]], -1)
        v = b - a
        t = np.clip((P @ v) / max(v @ v, 1e-9), 0, 1)
        d = np.linalg.norm(P - t[..., None] * v, axis=-1)
        out[s] |= d <= r
    return out


def cone(g, a, b, r0, r1, diamond=False, tsel=None):
    """a から b への錐 (半径 r0 → r1)。diamond=True は断面がひし形 (結晶の面)。
    tsel=(t0, t1) なら、その区間だけを返す (折れた棘の欠片用)"""
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    v = b - a
    L2 = max(v @ v, 1e-9)
    rm = max(r0, r1)
    s = sl_of(g, np.minimum(a, b) - rm - 0.3, np.maximum(a, b) + rm + 0.3)
    P = np.stack([g.X[s] - a[0], g.Y[s] - a[1], g.Z[s] - a[2]], -1)
    t = (P @ v) / L2
    perp = P - t[..., None] * v
    rr = r0 + (r1 - r0) * np.clip(t, 0, 1)
    if diamond:
        u = v / np.sqrt(L2)
        e1 = np.cross(u, [0, 1, 0] if abs(u[1]) < 0.9 else [1, 0, 0])
        e1 /= np.linalg.norm(e1)
        e2 = np.cross(u, e1)
        d = np.abs(perp @ e1) + np.abs(perp @ e2)
        rr = rr * 1.25
    else:
        d = np.linalg.norm(perp, axis=-1)
    t0, t1 = tsel if tsel else (0.0, 1.0)
    out = np.zeros(g.n, bool)
    out[s] = (t >= t0) & (t <= t1) & (d <= rr)
    tf = np.full(g.n, -1.0)
    tf[s] = t
    return out, tf


def tint(g, base, alt=None, p=0.0):
    base = np.array(base, float)
    alt = None if alt is None else np.array(alt, float)

    def f(m):
        c = np.repeat(base[None], int(m.sum()), 0)
        if alt is not None:
            sel = np.modf(g.noise[m] * 37.0)[0] < p
            c[sel] = alt
        return c * g.noise[m][:, None]
    return f


def solid(color):
    return lambda m: np.repeat(np.array(color, float)[None], int(m.sum()), 0)


def shade_all(g):
    occ = g.lab > 0
    e = ~np.pad(occ, 1)
    nx = e[2:, 1:-1, 1:-1].astype(float) - e[:-2, 1:-1, 1:-1]
    ny = e[1:-1, 2:, 1:-1].astype(float) - e[1:-1, :-2, 1:-1]
    nz = e[1:-1, 1:-1, 2:].astype(float) - e[1:-1, 1:-1, :-2]
    n = np.stack([nx, ny, nz], -1)
    nl = np.linalg.norm(n, axis=-1)
    lamb = np.where(nl > 0, (n @ LIGHT) / np.maximum(nl, 1e-9), 0.0)
    lum = 0.7 + 0.36 * np.clip(lamb, 0, 1)
    m = occ & ~g.glow
    g.col[m] = np.clip(g.col[m] * lum[m][:, None], 0, 255)


def coarse_xyz(g):
    return tuple(np.floor(a) + 0.5 for a in (g.X, g.Y, g.Z))


# ---------------------------------------------------------------- 心臓 (全案共通の形)

def heart(g, ph):
    """心臓。第1段階: 赤い肉に橙の光る筋 / 第2段階: 白熱 / 第3段階: 灰の殻、光はわずか"""
    X, Y, Z = g.X, g.Y, g.Z
    lobes = (ell(g, (-2.4, CY, 0), (4.8, 5.2, 4.6), coarse=True) | ell(g, (2.4, CY, 0), (4.8, 5.2, 4.6), coarse=True)
             | ell(g, (0, CY - 3.6, 0), (3.4, 3.8, 3.4), coarse=True))
    lobes &= ~((np.abs(X) < 0.6) & (Y > CY + 3.6))
    rc = {1: 2.2, 2: 3.0, 3: 1.7}[ph]
    lobes &= ~ell(g, (0, CY, 4.6), (rc + 0.4, rc + 0.4, 2.4))
    Xc, Yc, Zc = coarse_xyz(g)
    vein = lobes & (np.sin(Xc * 1.7 + Yc * 1.1) * np.cos(Zc * 1.3) > 0.82)
    if ph == 1:
        g.put("heart", lobes & ~vein, tint(g, (150, 40, 32), (104, 28, 26), 0.3))
        g.put("heart_vein", vein, solid(EMBER), glow=True)
    elif ph == 2:
        g.put("heart", lobes & ~vein, tint(g, (206, 74, 38), (236, 120, 48), 0.3))
        g.put("heart_vein", vein, solid(MOLTEN), glow=True)
    else:
        # 灰の殻: 表の層の半分以上が白い灰。残り火は筋の数か所だけ
        # 色は暗い赤と灰のまだら (1 単位の塊)。殻は表に 0.5 単位盛り上がった灰の板 (つながった塊)
        lowf = np.sin(Xc * 0.9 + 0.5) * np.cos(Yc * 0.8) + np.sin(Zc * 0.7 + Xc * 0.4)
        live = vein & (np.modf(np.abs(Xc * 7.3 + Yc * 3.1 + Zc * 5.7) * 0.37)[0] < 0.3)
        grown = (ell(g, (-2.4, CY, 0), (5.3, 5.7, 5.1), coarse=True) | ell(g, (2.4, CY, 0), (5.3, 5.7, 5.1), coarse=True)
                 | ell(g, (0, CY - 3.6, 0), (3.9, 4.3, 3.9), coarse=True))
        flakes = grown & ~lobes & (lowf > 0.55) & ~ell(g, (0, CY, 4.6), (rc + 1.2, rc + 1.2, 3.0)) & ~((np.abs(X) < 1.2) & (Y > CY + 3.0))
        g.put("heart_vein", live, solid(DIM_RED), glow=True)
        g.put("heart_crust", flakes, tint(g, ASH, ASH_W, 0.2))

        def hc(m):
            lf = lowf[m]
            c = np.where((lf > 0.2)[:, None], np.array(ASH_D, float), np.array((84, 30, 28), float))
            c = np.where((lf > 0.8)[:, None], np.array(ASH, float), c)
            return c * g.noise[m][:, None]
        g.put("heart", lobes, hc)
    # 大動脈 (3 本の短く曲がった管)
    base = (0.0, CY + 4.0, 0.0)
    arts = [
        [base, (-0.8, CY + 6.0, 0.6), (-2.2, CY + 7.6, 0.4)],
        [base, (0.9, CY + 6.4, -0.4), (2.6, CY + 8.2, -0.8)],
        [base, (0.2, CY + 7.0, 1.0), (0.4, CY + 9.0, 2.2)],
    ]
    art_c = {1: ((120, 34, 28), (160, 52, 34)), 2: ((168, 54, 34), (220, 96, 44)), 3: (ASH_D, (70, 34, 30))}[ph]
    for pts in arts:
        g.put("vessel", tube3(g, pts, 0.7), tint(g, art_c[0], art_c[1], 0.3))
    # 核
    ring_c = {1: EMBER, 2: MOLTEN, 3: DEEP_RED}[ph]
    eye_c = {1: MOLTEN, 2: WHITE_HOT, 3: DIM_RED}[ph]
    zc = {1: 3.4, 2: 3.8, 3: 3.2}[ph]
    core = ell(g, (0, CY, zc), (rc, rc, rc), coarse=(ph == 2))   # 大きな白熱の核は 1 単位の段 (箱を減らす)
    re = rc - (0.5 if ph == 3 else 0.8)
    eye = ell(g, (0, CY, zc), (re, re, re))
    g.put("core_ring", core & ~eye, solid(ring_c), glow=(ph != 3), over=True)
    g.put("core_eye", core & eye, solid(eye_c), glow=True, over=True)


def cinders(g, n, seed, r_in=8.0, r_out=15.0, y0=CY - 2, y1=CY + 22, name="cinders"):
    """浮かぶ火の粉 (上へ昇る)。自分の骨に入れる"""
    rng = np.random.default_rng(seed)
    cols = [EMBER, DIM_RED, MOLTEN, DEEP_RED]
    k = 0
    tries = 0
    while k < n and tries < 2000:
        tries += 1
        y = rng.uniform(y0, y1)
        a = rng.uniform(0, 2 * np.pi)
        # 上ほど中心に寄る (火の粉の柱)
        rr = rng.uniform(r_in, r_out) * (1 - 0.5 * (y - y0) / (y1 - y0))
        p = np.array([CX + rr * np.cos(a), y, CZ + rr * np.sin(a)])
        hs = 0.25 if rng.random() < 0.6 else 0.5
        m = box_m(g, p - hs, p + hs)
        if not m.any() or (g.lab[m] > 0).any():
            continue
        # 他の部品に触れない (浮いた粒)
        near = box_m(g, p - hs - 0.5, p + hs + 0.5)
        if (g.lab[near] > 0).any():
            continue
        g.put(name, m, solid(cols[k % len(cols)]), glow=True)
        k += 1


# ---------------------------------------------------------------- 案A 炉

def cyl_shell(g, y0, y1, R, T, coarse=True):
    Xc, Yc, Zc = coarse_xyz(g) if coarse else (g.X, g.Y, g.Z)
    r = np.sqrt(Xc ** 2 + Zc ** 2)
    return (r <= R) & (r > R - T) & (g.Y > y0) & (g.Y < y1)


def brick_col(g, R, dark=False):
    """煉瓦の目地 (横 1.5 単位ごと、縦 3 単位ごと、段ごとにずらす)"""
    def f(m):
        X, Y, Z = g.X[m], g.Y[m], g.Z[m]
        ang = np.arctan2(Z, X)
        u = ang * R
        row = np.floor(Y / 1.5)
        mort = (np.mod(Y, 1.5) < 0.5) | (np.mod(u + 1.5 * (row % 2), 3.0) < 0.5)
        r = np.sqrt(X ** 2 + Z ** 2)
        top = Y > CY + 3.0          # 丸屋根は放射の目地
        mort = np.where(top, (np.mod(np.hypot(r, Y - (CY + 3)), 1.5) < 0.5) | (np.mod(ang * 8 / np.pi + row % 2, 2.0) < 0.25), mort)
        sel = np.modf(g.noise[m] * 29.0)[0] < 0.35
        base = np.where(sel[:, None], np.array(BRICK_D, float), np.array(BRICK, float))
        if dark:
            base = base * 0.8
        c = np.where(mort[:, None], np.array(MORTAR, float), base)
        return c * g.noise[m][:, None]
    return f


def grate(g, broken=False, y=CY - 11.0):
    """炉の下の鉄の火格子 (輪 + 格子の棒 + 4 本の爪)。第3段階では割れた輪だけが残る"""
    Xc, Yc, Zc = coarse_xyz(g)
    r = np.sqrt(Xc ** 2 + Zc ** 2)
    ang = np.arctan2(g.Z, g.X)
    ring = (r <= 12.5) & (r > 10.5) & (g.Y > y - 0.5) & (g.Y < y + 1.0)
    rivets = (r <= 13.5) & (r > 12.5) & (g.Y > y) & (g.Y < y + 0.5) & (np.mod(np.arctan2(Zc, Xc) * 12 / np.pi, 2.0) < 0.3)
    bars = (r <= 10.5) & (g.Y > y - 0.5) & (g.Y < y + 0.5) & (np.mod(Xc + 0.5, 3.0) < 1.0)
    claws = np.zeros(g.n, bool)
    for a in (0.25, 1.82, 3.39, 4.96):
        # 爪は 1 単位の段の 3 段 (外へ張り出してから下へ曲がる)
        for k, (rr, dy, hw) in enumerate(((11.5, -1.0, 0.75), (12.5, -2.0, 0.75), (12.5, -3.0, 0.5))):
            c = np.round(np.array([rr * np.cos(a), y + dy, rr * np.sin(a)]) * 2) / 2
            claws |= box_m(g, c - [hw, 0.5, hw], c + [hw, 0.5, hw])
    if broken:
        gap = (np.abs(np.mod(ang - 0.9, 2 * np.pi) - np.pi) < 0.5) | (np.abs(np.mod(ang + 1.6, 2 * np.pi) - np.pi) < 0.32)
        ring &= ~gap
        rivets &= ~gap
        # 格子の棒は折れて半分だけ残る (輪から内へ突き出た短い棒)
        bars &= (r > 6.5) & ~gap
        claws &= ~(np.abs(np.mod(ang - 0.25 + np.pi, 2 * np.pi) - np.pi) < 0.6)
        # 輪の割れ目の縁が赤く焼けている
        edge = ring & ((np.abs(np.mod(ang - 0.9, 2 * np.pi) - np.pi) < 0.58) | (np.abs(np.mod(ang + 1.6, 2 * np.pi) - np.pi) < 0.40))
        g.put("grate", ring & ~edge, tint(g, IRON, IRON_D, 0.3))
        g.put("grate", ring & edge, solid(DIM_RED), glow=True)
    else:
        g.put("grate", ring, tint(g, IRON, IRON_D, 0.3))
    g.put("grate", rivets, solid((96, 90, 92)))
    g.put("grate", bars, tint(g, IRON_D, IRON, 0.2))
    g.put("grate", claws, tint(g, IRON, IRON_D, 0.3))


def bellows(g, open_=0.0):
    """ふいご (炉の左)。open_ 0 = 閉じた / 1 = 大きく開いた (送風中)"""
    y = CY - 6.0
    xn = -11.0   # 炉の壁
    Xp = g.X
    d = np.clip((xn - 1.5) - Xp, 0, None)       # 鼻先からの距離
    inb = (Xp < xn - 1.5) & (Xp > xn - 9.5) & (np.abs(g.Z) < 3.0)
    half = 0.6 + d * (0.22 + 0.32 * open_)
    top = inb & (g.Y > y + half) & (g.Y < y + half + 0.6)
    bot = inb & (g.Y < y - half) & (g.Y > y - half - 0.6)
    leather = inb & (np.abs(g.Y - y) <= half) & (np.abs(g.Z) < 2.5)
    # 皮のひだ (2 本の筋が外へ出る)
    fold = leather & (np.abs(np.abs(g.Z) - 2.25) < 0.3)
    g.put("bellows", top | bot, tint(g, WOOD, (76, 50, 34), 0.3))
    g.put("bellows_leather", leather & ~fold, tint(g, LEATHER, (90, 58, 42), 0.3))
    pleat = inb & (np.abs(g.Y - y) <= half) & (np.abs(g.Z) >= 2.5) & (np.abs(g.Z) < 3.0) & (np.mod(d, 2.0) < 1.0)
    g.put("bellows_leather", pleat, tint(g, (56, 36, 30), LEATHER, 0.3))
    # 取っ手と鼻 (鉄の管が炉へ)
    h1 = half if np.isscalar(half) else None
    g.put("bellows", tube3(g, [(xn - 9.5, y + 0.6 + 8 * (0.22 + 0.32 * open_) + 0.3, 0), (xn - 12.0, y + 1.0 + 8 * (0.22 + 0.32 * open_), 0)], 0.5),
          solid(WOOD))
    g.put("bellows", tube3(g, [(xn - 9.5, y - 0.6 - 8 * (0.22 + 0.32 * open_) - 0.3, 0), (xn - 12.0, y - 1.0 - 8 * (0.22 + 0.32 * open_), 0)], 0.5),
          solid(WOOD))
    g.put("bellows", tube3(g, [(xn - 2.0, y, 0), (xn + 0.6, y, 0)], 0.7), tint(g, IRON, IRON_D, 0.2))


def coal_bed(g, R, hot):
    """炉の底の熾: 暗い滓の床 + 1 単位の塊で光る石炭"""
    Xc, Yc, Zc = coarse_xyz(g)
    r = np.sqrt(Xc ** 2 + Zc ** 2)
    bed = (r < R) & (g.Y > CY - 9.5) & (g.Y < CY - 9.0)
    lump = (r < R - 0.5) & (g.Y > CY - 9.0) & (g.Y < CY - 8.0) & (np.sin(Xc * 1.3) * np.sin(Zc * 1.1 + 0.4) > 0.2)
    g.put("coals", bed, tint(g, SLAG_D, BASALT, 0.4))
    g.put("coals", lump, solid(hot), glow=True)


def build_A(g, ph):
    heart(g, ph)
    X, Y, Z = g.X, g.Y, g.Z
    ang = np.arctan2(Z, X)
    R, T = 11.0, 1.5
    if ph == 1:
        wall = cyl_shell(g, CY - 10.0, CY + 3.0, R, T)
        dome = ell(g, (0, CY + 3.0, 0), (R, 9.0, R), coarse=True) & ~ell(g, (0, CY + 3.0, 0), (R - T, 9.0 - T, R - T), coarse=True) & (Y >= CY + 3.0)
        chim_o = cyl_shell(g, CY + 9.0, CY + 15.0, 2.5, 1.0, coarse=False)
        chim_hole = (np.sqrt(X ** 2 + Z ** 2) <= 1.5) & (Y > CY + 9.0)
        dome &= ~chim_hole
        floor = (np.sqrt(X ** 2 + Z ** 2) <= R) & (Y > CY - 10.5) & (Y < CY - 9.5)
        # 光る細い隙間 (縦に 6 本、横に 1 本の通風口)。外の 1 単位を彫り、奥の 0.5 単位が光る
        r = np.sqrt(X ** 2 + Z ** 2)
        slit_a = np.zeros(g.n, bool)
        for a0 in np.linspace(0, 2 * np.pi, 7, endpoint=False) + 0.45:
            da = np.abs(np.mod(ang - a0 + np.pi, 2 * np.pi) - np.pi) * R
            slit_a |= (da < 0.55) & (Y > CY - 5.0) & (Y < CY + 2.0)
        vent = (Y > CY + 6.5) & (Y < CY + 7.5) & (Z > 0) & (np.abs(X) < 6.0)
        door = (np.abs(X) < 3.0) & (Y > CY - 9.5) & (Y < CY - 5.0 + 0.0 * X) & (Z > 0)
        door |= ell(g, (0, CY - 5.0, R), (3.0, 2.0, 3.0)) & (Z > 0) & (Y >= CY - 5.0)
        cut = (slit_a | vent) & (wall | dome)
        glowlayer = cut & (r <= R - T + 0.5 + 0.01)
        g.put("kiln_glow", glowlayer, solid(EMBER), glow=True)
        wall &= ~cut & ~door
        dome &= ~(cut & ~glowlayer)
        dome &= ~cut
        # 鉄の帯 (2 本) と扉の鉄枠
        Xq, _, Zq = coarse_xyz(g)
        rq = np.sqrt(Xq ** 2 + Zq ** 2)
        band = np.zeros(g.n, bool)
        for yb in (CY - 7.5, CY + 2.0):
            band |= (rq <= R + 1.0) & (rq > R) & (Y > yb - 0.5) & (Y < yb + 0.5)
        band &= ~slit_a
        frame = ((np.abs(X) < 3.5) & (np.abs(X) >= 3.0) & (Y > CY - 9.5) & (Y < CY - 4.5) |
                 (ell(g, (0, CY - 5.0, R), (3.5, 2.5, 4.0)) & ~ell(g, (0, CY - 5.0, R), (3.0, 2.0, 4.5)) & (Y >= CY - 5.0)))
        frame &= (r > R - 0.1) & (r <= R + 0.5)
        g.put("kiln_iron", band | frame, tint(g, IRON, IRON_D, 0.3))
        g.put("kiln", wall | dome | floor, brick_col(g, R))
        g.put("kiln", chim_o, brick_col(g, 2.5, dark=True))
        # 炉の中の熾 (扉から見える)
        coal_bed(g, R - T, EMBER)
        g.put("chimney_glow", (np.sqrt(X ** 2 + Z ** 2) <= 1.5) & (Y > CY + 13.5) & (Y < CY + 14.0), solid(MOLTEN), glow=True)
        grate(g)
        bellows(g, 0.0)
        cinders(g, 6, 11, r_in=0.0, r_out=2.0, y0=CY + 16.5, y1=CY + 21.0)
    elif ph == 2:
        # 割れて崩れる炉: 後ろは高く残り、前は低い。崩れた縁と割れ目が赤く光る
        r = np.sqrt(X ** 2 + Z ** 2)
        zhat = Z / np.maximum(r, 1e-6)
        top = CY - 6.0 + 7.5 * (1 - zhat) / 2 + 1.8 * np.sin(5 * ang + 0.7) + 1.0 * np.sin(11 * ang)
        top = np.floor(top) + 0.0
        wall = cyl_shell(g, CY - 10.0, CY + 6.0, R, T) & (Y < top)
        Xc, Yc, Zc = coarse_xyz(g)
        holes = (np.sin(Xc * 0.9 + Yc * 0.6) + np.cos(Zc * 0.8 - Yc * 0.7)) > 1.35
        wall &= ~holes
        floor = (r <= R) & (Y > CY - 10.5) & (Y < CY - 9.5)
        rq = np.sqrt(Xc ** 2 + Zc ** 2)
        aq = np.arctan2(Zc, Xc)
        rim = wall & (Yc > top - 1.0)
        crack = wall & (np.abs(np.sin(aq * 7 + Yc * 0.45)) < 0.09)
        g.put("kiln_glow", rim | crack, solid(EMBER), glow=True)
        g.put("kiln", wall | floor, brick_col(g, R, dark=True))
        Xq, _, Zq = coarse_xyz(g)
        rq = np.sqrt(Xq ** 2 + Zq ** 2)
        band = (rq <= R + 1.0) & (rq > R) & (Y > CY - 8.0) & (Y < CY - 7.0) & ~(np.abs(np.mod(ang - 1.2, 2 * np.pi) - np.pi) < 0.4)
        g.put("kiln_iron", band, tint(g, IRON, IRON_D, 0.3))
        coal_bed(g, R - T, MOLTEN)
        grate(g)
        bellows(g, 1.0)
        # 崩れ落ちる煉瓦 (炉の外で宙に)
        rng = np.random.default_rng(23)
        k = 0
        while k < 22:
            a = rng.uniform(0, 2 * np.pi)
            if np.cos(a) < -0.55:
                continue      # ふいごの側は避ける
            rr = rng.uniform(12.5, 16.5)
            yy = CY + rng.uniform(-14.0, 6.0)
            c = np.array([rr * np.cos(a), yy, rr * np.sin(a)])
            hs = np.array([0.75, 0.5, 0.5]) if rng.random() < 0.5 else np.array([0.5, 0.5, 0.75])
            m = box_m(g, c - hs, c + hs)
            if (g.lab[box_m(g, c - hs - 0.5, c + hs + 0.5)] > 0).any():
                continue
            g.put("falling_bricks", m, brick_col(g, R), glow=False)
            k += 1
        cinders(g, 16, 12, r_in=3.0, r_out=9.0, y0=CY + 8.0, y1=CY + 20.0)
    else:
        # 割れた火格子の輪だけが残り、灰の輪が心臓の周りを回る
        grate(g, broken=True, y=CY - 10.0)
        r = np.sqrt(X ** 2 + Z ** 2)
        # 灰の輪 1: 水平、途切れ途切れ
        ring1 = (r <= 14.0) & (r > 13.0) & (np.abs(Y - CY - 1.0) < 0.5) & (np.mod(ang * 9 / np.pi, 2.0) < 1.3)
        g.put("ash_ring_1", ring1, tint(g, ASH, ASH_W, 0.4))
        # 灰の輪 2: 傾いた輪 (y が角度で上下する)。光る残り火の粒が少しまじる
        yr = CY + 1.0 + 6.0 * np.sin(ang + 0.6)
        ring2 = (r <= 16.5) & (r > 15.5) & (np.abs(Y - yr) < 0.5) & (np.mod(ang * 7 / np.pi + 0.5, 2.0) < 1.4)
        hot = ring2 & (np.mod(ang * 7 / np.pi + 0.5, 2.0) < 0.15)
        g.put("ash_ring_2", ring2 & ~hot, tint(g, ASH_D, ASH, 0.4))
        g.put("ash_ring_2", hot, solid(DIM_RED), glow=True)
        cinders(g, 18, 13, r_in=1.0, r_out=8.0, y0=CY + 6.0, y1=CY + 20.0)


# ---------------------------------------------------------------- 案B 熔け殻

def lumpy(X, Y, Z):
    return 1 + 0.06 * np.sin(X * 0.9 + 1.0) * np.cos(Z * 0.8) + 0.05 * np.sin(Y * 1.1 + Z * 0.5)


RB = (10.5, 11.5, 9.5)


def crust_layers(g, R=RB, T=1.5):
    o = ell(g, (0, CY, 0), R, lumpy, coarse=True)
    m1 = ell(g, (0, CY, 0), (R[0] - 1, R[1] - 1, R[2] - 1), lumpy, coarse=True)
    m2 = ell(g, (0, CY, 0), (R[0] - T, R[1] - T, R[2] - T), lumpy, coarse=True)
    return o & ~m1, m1 & ~m2      # 外の層, 内の層


def crack_net(g, w=0.2):
    X, Y, Z = coarse_xyz(g)
    a = np.sin(X * 0.55 + Y * 0.3) + np.sin(Y * 0.5 - Z * 0.45) + np.sin(Z * 0.6 + X * 0.35)
    return np.abs(a) < w * 3


def bottom_y(x, z, R=RB):
    q = 1 - (x / R[0]) ** 2 - (z / R[2]) ** 2
    return CY - R[1] * np.sqrt(max(q, 0.0))


def build_B(g, ph):
    heart(g, ph)
    X, Y, Z = g.X, g.Y, g.Z
    ang = np.arctan2(Z, X)
    if ph == 1:
        outer, inner = crust_layers(g)
        # 頭の穴 (大動脈の上): 熱い息が漏れる光
        top = ell(g, (0, CY + RB[1] - 0.5, 0.0), (2.0, 1.2, 2.0)) & (outer | inner)
        g.put("crust_glow", top & (Y < CY + RB[1] - 1.0), solid(MOLTEN), glow=True)
        outer &= ~top
        inner &= ~top
        cr = crack_net(g)
        g.put("crust_glow", inner & cr, solid(EMBER), glow=True)
        g.put("crust", outer & ~cr, tint(g, BASALT, BASALT_L, 0.25))
        g.put("crust", inner, tint(g, BASALT_D, BASALT, 0.3))
        # 玄武岩のこぶ (表面の起伏) と、下の短い垂れ (第2段階の前ぶれ)
        rng = np.random.default_rng(3)
        for _ in range(16):
            d = rng.normal(size=3)
            d /= np.linalg.norm(d)
            p = np.array([CX, CY, CZ]) + d * np.array(RB) * 1.0
            g.put("crust", ell(g, p, (1.4, 1.1, 1.4), coarse=True), tint(g, BASALT, BASALT_L, 0.25))
        for (x, z, L) in ((-3.0, 1.5, 3.0), (2.5, -1.0, 2.5), (0.5, 3.0, 2.0)):
            y0 = bottom_y(x, z) + 1.0
            m, t = cone(g, (x, y0, z), (x, y0 - L, z), 1.0, 0.25)
            g.put("drips", m, tint(g, BASALT, SLAG, 0.3))
        cinders(g, 6, 21, r_in=0.0, r_out=2.5, y0=CY + 13.0, y1=CY + 19.0)
    elif ph == 2:
        # 殻が熔ける: 上は熔けて流れ落ち、下の椀だけが残る。縁と筋は熔けた橙。下に熔岩の鍾乳石が垂れる
        outer, inner = crust_layers(g)
        Xc, Yc, Zc = coarse_xyz(g)
        aq = np.arctan2(Zc, Xc)
        melt = np.floor(CY - 1.0 + 2.2 * np.sin(3 * aq + 0.4) + 1.2 * np.sin(7 * aq))
        bowl_o = outer & (Y < melt)
        bowl_i = inner & (Y < melt)
        rim = (bowl_o | bowl_i) & (Y > melt - 1.0)
        streak = bowl_o & (np.abs(np.sin(aq * 9 + 0.3)) < 0.2)
        g.put("crust_glow", rim, solid(MOLTEN), glow=True)
        g.put("crust_glow", streak, solid(EMBER), glow=True)
        g.put("crust", bowl_o, tint(g, SLAG_D, BASALT, 0.4))
        g.put("crust", bowl_i, tint(g, BASALT_D, SLAG_D, 0.3))
        # 熔岩の鍾乳石: 椀の底から下へ (根元は暗い滓、先は熔けて光る)
        drips = [(0.0, 0.0, 9.0), (-3.5, 1.5, 7.0), (3.5, -1.0, 7.5), (-1.5, -3.0, 5.5), (2.0, 3.0, 6.0),
                 (-6.0, -1.0, 4.5), (6.0, 1.5, 5.0), (-4.5, 4.0, 3.5), (4.5, -4.0, 4.0), (0.5, 5.5, 3.0)]
        for (x, z, L) in drips:
            y0 = bottom_y(x, z) + 1.5
            r0 = 1.3 if L > 5 else 1.0
            m, t = cone(g, (x, y0, z), (x, y0 - L - 1.5, z), r0, 0.25)
            g.put("drips", m & (t < 0.45), tint(g, SLAG_D, SLAG, 0.3))
            g.put("drips", m & (t >= 0.45) & (t < 0.75), solid(EMBER), glow=True)
            g.put("drips", m & (t >= 0.75), solid(MOLTEN), glow=True)
        # 落ちる滴 (鍾乳石の下の宙に、自分の骨)
        for (x, z, L) in drips[:7]:
            y0 = bottom_y(x, z) + 1.5 - L - 1.5
            for k, gap in enumerate((1.5, 3.5)):
                if L < 6 and k == 1:
                    continue
                c = np.array([x, y0 - gap, z])
                hs = 0.5 if k == 0 else 0.25
                g.put("drops", box_m(g, c - hs, c + hs), solid(MOLTEN if k == 0 else EMBER), glow=True)
        # 外側を伝う熔けた筋 (縁から底へ、0.5 単位の盛り上がり)
        rv = ell(g, (0, CY, 0), (RB[0] + 0.6, RB[1] + 0.6, RB[2] + 0.6), lumpy) & ~ell(g, (0, CY, 0), RB, lumpy, coarse=True)
        band = np.zeros(g.n, bool)
        for a0 in (0.3, 1.4, 2.3, 3.5, 4.4, 5.5):
            band |= np.abs(np.mod(ang - a0 + np.pi, 2 * np.pi) - np.pi) < 0.09
        g.put("drips", rv & band & (Y < melt - 0.5), solid(EMBER), glow=True)
        cinders(g, 12, 22, r_in=2.0, r_out=8.0, y0=CY + 10.0, y1=CY + 20.0)
    else:
        # 白い灰の殻: 中は空。穴から暗い残り火が見え、火の粉が昇る
        R3 = (10.0, 11.0, 9.0)
        o = ell(g, (0, CY, 0), R3, lumpy, coarse=True)
        i_ = ell(g, (0, CY, 0), (R3[0] - 1, R3[1] - 1, R3[2] - 1), lumpy, coarse=True)
        sh = o & ~i_
        P = np.stack([X - CX, (Y - CY) * 0.9, Z - CZ], -1)
        nP = P / np.maximum(np.linalg.norm(P, axis=-1, keepdims=True), 1e-6)
        holes_spec = [((0.15, 0.25, 1.0), 0.42), ((-0.8, 0.5, 0.5), 0.30), ((0.85, -0.2, 0.45), 0.34),
                      ((0.1, 1.0, 0.1), 0.38), ((-0.4, -0.6, 0.7), 0.24), ((0.6, 0.6, -0.5), 0.28),
                      ((-0.7, 0.0, -0.7), 0.26), ((0.2, -0.3, -1.0), 0.30)]
        hole = np.zeros(g.n, bool)
        edge = np.zeros(g.n, bool)
        for d, rad in holes_spec:
            d = np.array(d) / np.linalg.norm(d)
            cosang = nP @ d
            hole |= cosang > np.cos(rad)
            edge |= (cosang > np.cos(rad + 0.12)) & (cosang <= np.cos(rad))
        sh &= ~hole
        g.put("ash_shell", sh & edge, tint(g, ASH_D, (86, 72, 64), 0.4))
        g.put("ash_shell", sh, tint(g, ASH_W, ASH, 0.3))
        # 固まった灰の垂れ (短く、灰色。光らない)
        for (x, z, L) in ((0.0, 0.0, 4.0), (-3.5, 1.5, 3.0), (3.5, -1.0, 3.5), (2.0, 3.0, 2.0), (-1.5, -3.0, 2.5)):
            y0 = bottom_y(x, z, R3) + 1.0
            m, t = cone(g, (x, y0, z), (x, y0 - L, z), 1.0, 0.25)
            g.put("ash_drips", m, tint(g, ASH, ASH_D, 0.4))
        cinders(g, 26, 23, r_in=0.5, r_out=6.0, y0=CY + 12.0, y1=CY + 23.0)


# ---------------------------------------------------------------- 案C 黒曜の棘

SPIKES = [  # (方向, 長さ, 根元の半径)
    ((0.25, 1.0, 0.15), 15.0, 1.9),
    ((-0.9, 0.55, 0.2), 14.0, 1.8),
    ((0.95, 0.35, -0.2), 13.5, 1.8),
    ((-0.6, -0.75, 0.35), 12.0, 1.6),
    ((0.7, -0.6, 0.4), 12.5, 1.6),
    ((0.1, -1.0, -0.3), 11.0, 1.5),
    ((-0.35, 0.3, 0.9), 11.0, 1.5),
    ((0.5, 0.2, 0.85), 10.0, 1.4),
    ((-0.5, 0.45, -0.8), 13.0, 1.7),
    ((0.55, 0.65, -0.55), 12.0, 1.6),
    ((-1.0, -0.15, -0.3), 10.5, 1.4),
]


def spike_col(g):
    def f(m):
        sel = np.modf(g.noise[m] * 41.0)[0]
        c = np.where((sel < 0.25)[:, None], np.array(OBSID_L, float),
                     np.where((sel < 0.6)[:, None], np.array(OBSID_M, float), np.array(OBSID, float)))
        return c * g.noise[m][:, None]
    return f


def build_C(g, ph):
    heart(g, ph)
    X, Y, Z = g.X, g.Y, g.Z
    c0 = np.array([CX, CY, CZ])
    # 心臓に触れるセル (棘の根元の光る襟に使う)
    hb = np.isin(g.lab, [g.names[n] for n in ("heart", "heart_vein", "vessel") if n in g.names])
    hnear = np.zeros(g.n, bool)
    for ax in range(3):
        for sh in (-1, 1):
            hnear |= np.roll(hb, sh, ax)
    hnear &= g.lab == 0
    if ph in (1, 2):
        broken = {1, 3, 4, 7, 9} if ph == 2 else set()
        for k, (d, L, r0) in enumerate(SPIKES):
            d = np.array(d) / np.linalg.norm(d)
            a = c0 + d * 2.0
            b = c0 + d * L
            if k in broken:
                # 折れた棘: 根元の切り株 + 宙に浮く欠片 (同じ向きのまま、すき間をあけて)
                m, t = cone(g, a, b, r0 * 1.4, 0.5, diamond=True, tsel=(0.0, 0.42))
                g.put("spikes", m & (t < 0.38), spike_col(g))
                g.put("spike_glow", m & (t >= 0.38), solid(EMBER), glow=True)
                for j, (t0, t1) in enumerate(((0.5, 0.66), (0.72, 0.86), (0.92, 1.0))):
                    off = d * (1.5 + 1.5 * j) + np.array([0.0, 0.6 * (j - 1), 0.0])
                    m2, _ = cone(g, a + off, b + off, r0 * 1.4, 0.5, diamond=True, tsel=(t0, t1))
                    g.put("shards", m2, spike_col(g))
            else:
                m, t = cone(g, a, b, r0 * 1.4, 0.5, diamond=True)
                # 根元 (心臓から出る所) は熔けた光の輪
                g.put("spike_glow", m & hnear, solid(EMBER if ph == 1 else MOLTEN), glow=True)
                g.put("spikes", m, spike_col(g))
                # 棘の中の光る筋 (第1段階: 熱が中を通る)
                if ph == 1:
                    pass
        # 小さな黒曜の粒 (割れた時の細かい欠片)
        if ph == 2:
            rng = np.random.default_rng(31)
            k = 0
            while k < 6:
                p = c0 + rng.normal(size=3) * 10.0
                if np.linalg.norm(p - c0) < 11.0 or np.linalg.norm(p - c0) > 17.0:
                    continue
                m = box_m(g, p - 1.0, p + 1.0)
                if (g.lab[box_m(g, p - 1.5, p + 1.5)] > 0).any():
                    continue
                g.put("shards", m, spike_col(g))
                k += 1
            cinders(g, 12, 32, r_in=3.0, r_out=10.0, y0=CY + 8.0, y1=CY + 20.0)
    else:
        # 棘は心臓から抜け、ゆっくり回る輪になる (接線の向きに寝た棘 8 本)。心臓には灰をかぶった切り株
        for k, (d, L, r0) in enumerate(SPIKES[:5]):
            d = np.array(d) / np.linalg.norm(d)
            m, t = cone(g, c0 + d * 2.0, c0 + d * L, r0 * 1.4, 0.5, diamond=True, tsel=(0.0, 0.32))
            g.put("stubs", m, tint(g, ASH_D, OBSID_M, 0.5))
        n = 8
        for k in range(n):
            a = 2 * np.pi * k / n
            Rr = 14.0
            yk = CY + 3.5 * np.sin(a + 0.8)
            p = np.array([Rr * np.cos(a), yk, Rr * np.sin(a)])
            tang = np.array([-np.sin(a), 0.25 * np.cos(a + 0.8), np.cos(a)])
            tang /= np.linalg.norm(tang)
            L = 9.0 if k % 2 == 0 else 7.0
            r0 = 1.6 if k % 2 == 0 else 1.3
            m, t = cone(g, p - tang * L * 0.35, p + tang * L * 0.65, r0 * 1.4, 0.5, diamond=True)
            g.put("orbit", m, spike_col(g))
        cinders(g, 16, 33, r_in=1.0, r_out=7.0, y0=CY + 7.0, y1=CY + 20.0)


# ---------------------------------------------------------------- 書き出し (tide_heart.py と同じ)

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
    "north": (lambda b: (b[3], b[1], b[2]), lambda s: (-s[0], 0, 0), lambda s: (0, s[1], 0), (0, 0, -1)),
    "south": (lambda b: (b[0], b[1], b[5]), lambda s: (s[0], 0, 0), lambda s: (0, s[1], 0), (0, 0, 1)),
    "east": (lambda b: (b[3], b[1], b[5]), lambda s: (0, 0, -s[2]), lambda s: (0, s[1], 0), (1, 0, 0)),
    "west": (lambda b: (b[0], b[1], b[2]), lambda s: (0, 0, s[2]), lambda s: (0, s[1], 0), (-1, 0, 0)),
    "up": (lambda b: (b[0], b[4], b[5]), lambda s: (s[0], 0, 0), lambda s: (0, 0, -s[2]), (0, 1, 0)),
    "down": (lambda b: (b[0], b[1], b[2]), lambda s: (s[0], 0, 0), lambda s: (0, 0, s[2]), (0, -1, 0)),
}


def export(g):
    shade_all(g)
    occ_all = g.lab > 0
    pad = np.pad(occ_all, 1)
    recs, bones_out = [], []
    vol = 0
    for name, lid in g.names.items():
        m = g.lab == lid
        if not m.any():
            continue
        cubes = []
        for bx in greedy(m):
            x0, y0, z0, x1, y1, z1 = bx
            s = (x1 - x0, y1 - y0, z1 - z0)
            vol += s[0] * s[1] * s[2]
            cube = {"origin": [float(LO[0] + x0 * H), float(LO[1] + y0 * H), float(LO[2] + z0 * H)],
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
                tex[..., :3] = np.clip(g.col[xi, yi, zi], 0, 255).astype(np.uint8)
                tex[..., 3] = 255
                recs.append({"W": W, "H": Hh, "tex": tex, "gl": g.glow[xi, yi, zi], "cube": cube, "face": fname})
            cubes.append(cube)
        bones_out.append({"name": name, "pivot": [0.0, CY, 0.0], "cubes": cubes})
    assert vol == int(occ_all.sum()), "箱の体積の合計がセル数と合わない (重なりか抜けがある)"
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
        "description": {"identifier": "geometry.skyabyss.ember_heart",
                        "texture_width": AW, "texture_height": AH,
                        "visible_bounds_width": 6, "visible_bounds_height": 6,
                        "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones_out}]}
    cubes = sum(len(b["cubes"]) for b in bones_out)
    return geo, tex, gtex, cubes


# ---------------------------------------------------------------- 描画とシート

VIEW_34 = (35, 22)
VIEW_FRONT = (0, 6)
BUILDERS = {"a": build_A, "b": build_B, "c": build_C}
NAMES = {"a": "A 炉", "b": "B 熔け殻", "c": "C 黒曜の棘"}
PHASE_NAMES = {1: "第1段階", 2: "第2段階", 3: "第3段階"}


def draw(geo, tex, gtex, view, size=REND):
    faces = load_geo(geo, tex, gtex)
    yaw, pitch = view
    return render(faces, yaw, pitch, (size, size))


def label_img(im, text):
    im = im.copy()
    d = ImageDraw.Draw(im)
    d.text((8, 6), text, font=font(17), fill=(230, 230, 240))
    return im


def build_one(opt, ph):
    g = Grid(seed=100 * ph + ord(opt))
    BUILDERS[opt](g, ph)
    return g


def run(opts):
    results = {}
    cols = []
    for opt in opts:
        ims34, imsF = [], []
        d = OUT / opt
        d.mkdir(parents=True, exist_ok=True)
        for ph in (1, 2, 3):
            g = build_one(opt, ph)
            geo, tex, gtex, cubes = export(g)
            ov = check_overlaps(geo)
            glow_px = int((gtex[..., 3] > 0).sum())
            (d / f"phase{ph}.geo.json").write_text(json.dumps(geo, ensure_ascii=False, indent=1), encoding="utf-8")
            Image.fromarray(tex).save(d / f"phase{ph}.tex.png")
            Image.fromarray(gtex).save(d / f"phase{ph}.glow.png")
            im34 = draw(geo, tex, gtex, VIEW_34)
            im34.save(d / f"phase{ph}.render.png")
            imf = draw(geo, tex, gtex, VIEW_FRONT)
            imf.save(d / f"phase{ph}.front.png")
            results[f"{opt}{ph}"] = {"cubes": cubes, "bones": [b["name"] for b in geo["minecraft:geometry"][0]["bones"]],
                                     "atlas": [AW, tex.shape[0]], "overlaps": len(ov), "ov_list": ov[:3],
                                     "glow_px": glow_px, "cells": int((g.lab > 0).sum())}
            per = {b["name"]: len(b["cubes"]) for b in geo["minecraft:geometry"][0]["bones"]}
            results[f"{opt}{ph}"]["per_bone"] = per
            print(opt, ph, cubes, "boxes", len(ov), "ov", glow_px, "glow", per, flush=True)
            ims34.append(label_img(im34, f"{PHASE_NAMES[ph]}  {cubes} 箱"))
            imsF.append(label_img(imf, f"{PHASE_NAMES[ph]} 正面"))
        cols.append((NAMES[opt], ims34 + imsF))
    if len(opts) == 3:
        (OUT / "stats.json").write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")
    return cols, results


def bars():
    W, Hh = 720, 260
    im = Image.new("RGB", (W, Hh), (18, 18, 26))
    d = ImageDraw.Draw(im)
    d.text((20, 14), "ボスバーの色案 (段階ごと)", font=font(24), fill=(235, 235, 245))
    spec = [(1, "第1段階 燃える心臓 (橙)", (246, 126, 40)),
            (2, "第2段階 白熱・むき出し (熔けた黄)", (255, 214, 96)),
            (3, "第3段階 消えかけの余燼 (暗い赤)", (150, 40, 30))]
    for i, (ph, label, col) in enumerate(spec):
        y = 64 + i * 62
        d.text((20, y), label, font=font(18), fill=(200, 200, 215))
        d.rectangle((20, y + 26, 700, y + 46), fill=(40, 40, 52))
        d.rectangle((20, y + 26, 20 + int(680 * (1.0 - 0.25 * i)), y + 46), fill=col)
        d.rectangle((20, y + 26, 700, y + 46), outline=(90, 90, 110))
    return im


def main():
    opts = sys.argv[1:] or ["a", "b", "c"]
    cols, results = run(opts)
    if len(opts) == 3:
        sh = sheet("余燼の心臓 ラフ 3案 × 3段階", cols,
                   note="上3段: 斜めからの絵 / 下3段: 正面。光る画素は核・熾・割れ目・火の粉。すべて回さない箱 (重なりなし)")
        sh.save(SHEET)
        bars().save(OUT / "bars.png")


if __name__ == "__main__":
    main()
