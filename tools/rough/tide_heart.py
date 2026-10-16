"""潮殻の心臓 (第2の巨人の最終ボス) 第2回ラフ: 案A「大貝」 / 案B「巻貝」 / 案C「珊瑚の籠」 x 3段階。

使い方 (リポジトリ直下で): python -B tools/rough/tide_heart.py [a b c]
出力: docs/review/tide_shell_roughs/heart/<a|b|c>/phaseN.(geo.json|tex.png|glow.png|render.png|front.png)
      docs/review/tide_shell_roughs/heart.png (比較シート), docs/review/tide_shell_roughs/heart/bars.png (ボスバーの色案)

作り方:
- 1 セル = 0.5 単位 = テクスチャ 1 画素 (2 px / 単位)。全ての部品は回さず、1 つの共有の格子に彫る。
  セルの持ち主は 1 つ (lab) なので、箱どうしは格子上で重ならない。箱の体積の合計がセル数と一致することも確かめる。
- 箱は部品 (bone) ごとに貪欲法で作る (boss_v4 と同じ)。面の陰影と光るセルは boss_v4 と同じ扱い。
- 心臓は全案で共通の形 (2 つの房 + 3 本の短く曲がった大動脈)。段階ごとに核の大きさと色を変える。
  第3段階の心臓は前へ出る (殻の外に浮く)。
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
from voxel import check_overlaps, font, load_geo, render, sheet  # noqa: E402

OUT = ROOT / "docs/review/tide_shell_roughs/heart"
SHEET = ROOT / "docs/review/tide_shell_roughs/heart.png"
H = 0.5                                   # セルの大きさ (単位)
LO = np.array([-20.0, -4.0, -20.0])
HI = np.array([20.0, 38.0, 20.0])
CX, CY, CZ = 0.0, 16.0, 0.0               # 心臓の中心
AW = 512
REND = 360

LIGHT = np.array([-0.45, 0.75, 0.5])
LIGHT = LIGHT / np.linalg.norm(LIGHT)

# ---------------------------------------------------------------- 色 (RGB)
PEARL = (238, 224, 216)
PEARL_D = (196, 170, 176)
PEARL_W = (216, 200, 206)
SILVER_P = (212, 224, 238)
VEIN = (122, 66, 122)
ART = (190, 80, 84)
CORE_W = (250, 252, 250)                  # 核の白い中心
CORE_T = (120, 246, 222)                  # 海のガラスの青緑
CORE_S = (168, 204, 255)                  # 月の銀青
NACRE = (216, 202, 224)                   # 貝の真珠層 (薄紫)
NACRE_D = (172, 158, 186)
TEAL_P = (196, 230, 224)                  # 案Aの肋 (青緑がかった真珠色)
CORAL = [(255, 118, 128), (255, 160, 98), (186, 120, 230), (240, 96, 160), (255, 200, 120)]
BLEACH = (242, 238, 232)
BLEACH_D = (196, 192, 188)
KELP = (74, 152, 84)
WATER_T = (96, 212, 212)
WATER_G = (160, 252, 244)
SILVER_G = (200, 220, 250)
ROCK = (120, 110, 108)


# ---------------------------------------------------------------- 格子

class Grid:
    """セル 0.5 単位の格子。lab = 持ち主の部品の番号 (0 = 空)。"""

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
        """空いているセルに、部品 name の持ち物として色を塗る (over なら他の部品のセルも取る)"""
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
    """楕円体 (中心 c、半径 r)。mod(X,Y,Z) は半径の倍率 (ひだ・ろく用)。
    coarse=True は 1 単位の格子の中心で測る (大きな殻の段を 1 単位にそろえて、箱の数を減らす)"""
    c = np.asarray(c, float)
    r = np.asarray(r, float)
    out = np.zeros(g.n, bool)
    s = sl_of(g, c - r - 0.2, c + r + 0.2)
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
    """折れ線の管 (半径 r)。各区間から中心線までの距離で決める"""
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


def tint(g, base, alt=None, p=0.0):
    """セルごとの色 (少し揺らぐ)。p の割合で alt の色を混ぜる"""
    base = np.array(base, float)
    alt = None if alt is None else np.array(alt, float)

    def f(m):
        c = np.repeat(base[None], int(m.sum()), 0)
        if alt is not None:
            sel = np.modf(g.noise[m] * 37.0)[0] < p
            c[sel] = alt
        return c * g.noise[m][:, None]
    return f


def soft(g, base, amp=0.03):
    """ほとんど揺らがない色 (±amp)。案Aの殻を落ち着いた色にする"""
    base = np.array(base, float)

    def f(m):
        k = 1 + amp * (g.noise[m] - 1) / 0.08
        return np.repeat(base[None], int(m.sum()), 0) * k[:, None]
    return f


def solid(color):
    return lambda m: np.repeat(np.array(color, float)[None], int(m.sum()), 0)


def palette_by_node(g, nodes, pal, bleach=False):
    """セルを一番近い節の番号で、珊瑚の色に分ける"""
    def f(m):
        P = np.stack([g.X[m], g.Y[m], g.Z[m]], 1)
        k = ((P[:, None, :] - nodes[None]) ** 2).sum(-1).argmin(1)
        base = np.array(pal, float)[k % len(pal)]
        return base * g.noise[m][:, None]
    return f


def shade_all(g):
    """面の向きの明るさを焼き込む (光るセルは除く)。全体で 1 回だけ呼ぶ"""
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


# ---------------------------------------------------------------- 心臓 (全案共通の形)

def heart(g, ph, dz=0.0):
    """心臓。dz > 0 なら前へ出る (第3段階は殻の外に浮く)。"""
    X, Y, Z = g.X, g.Y, g.Z
    heart_c = {1: PEARL, 2: PEARL_W, 3: SILVER_P}[ph]
    heart_d = {1: PEARL_D, 2: (176, 150, 160), 3: (176, 190, 210)}[ph]
    # 房は 1 単位の段 (箱を減らす)。切れ込みと窪みは 0.5 単位で彫る
    lobes = (ell(g, (-2.4, CY, dz), (4.8, 5.2, 4.6), coarse=True) | ell(g, (2.4, CY, dz), (4.8, 5.2, 4.6), coarse=True)
             | ell(g, (0, CY - 3.6, dz), (3.4, 3.8, 3.4), coarse=True))
    lobes &= ~((np.abs(X) < 0.6) & (Y > CY + 3.6))          # 上の切れ込み (房の間)
    lobes &= ~ell(g, (0, CY, dz + 4.6), (2.4, 2.4, 2.4))    # 前の窪み (核の座)
    Xc, Yc, Zc = (np.floor(a) + 0.5 for a in (X, Y, Z))
    vein = lobes & (np.sin(Xc * 1.7 + Yc * 1.1) * np.cos(Zc * 1.3) > 0.82)
    g.put("heart", lobes & ~vein, tint(g, heart_c, heart_d, 0.25))
    g.put("heart_vein", vein, VEIN)
    # 大動脈: 根元の 1 点から 3 本の短く曲がった管 (棒に見えないよう、分かれて広がる)
    base = (0.0, CY + 4.0, dz)
    arts = [
        [base, (-0.8, CY + 6.0, dz + 0.6), (-2.2, CY + 7.6, dz + 0.4)],
        [base, (0.9, CY + 6.4, dz - 0.4), (2.6, CY + 8.2, dz - 0.8)],
        [base, (0.2, CY + 7.0, dz + 1.0), (0.4, CY + 9.0, dz + 2.2)],
    ]
    for pts in arts:
        g.put("vessel", tube3(g, pts, 0.7), tint(g, ART, (222, 120, 108), 0.3))
    # 核: 拍動する光。段階ごとに大きさと色を変える
    rc = {1: 2.0, 2: 2.5, 3: 2.1}[ph]
    cc = {1: CORE_T, 2: CORE_T, 3: CORE_S}[ph]
    core = ell(g, (0, CY, dz + 3.5), (rc, rc, rc))
    eye = ell(g, (0, CY, dz + 3.5), (rc - 0.9, rc - 0.9, rc - 0.9))
    g.put("core_ring", core & ~eye, solid(cc), glow=True, over=True)
    g.put("core_eye", core & eye, solid(CORE_W), glow=True, over=True)


def moon_disk(g, z=-12.5):
    """月の円盤 (心臓の後ろ、銀青の光)。月潮の段階で使う"""
    g.put("moon", ell(g, (0, CY, z), (4.0, 4.0, 0.8)), solid(CORE_S), glow=True)


def moon_crescent(g):
    """三日月の縁の光 (第3段階の月。円の一部を欠いて、細い銀の縁が残る)"""
    moon = ell(g, (-6.0, CY + 7.0, -4.0), (5.0, 5.0, 1.0))
    moon &= ~ell(g, (-3.6, CY + 9.2, -4.0), (4.6, 4.6, 1.2))
    g.put("moon_crescent", moon, solid(CORE_S), glow=True)


def barnacles(g, name, c, R, grow, col, pick=0.45, seed=7):
    rng = np.random.default_rng(seed)
    for phi in (0.7, 1.15, 1.6, 2.05, 2.5):
        for th in np.linspace(0, 2 * np.pi, 10, endpoint=False):
            if rng.random() > pick:
                continue
            p = np.array([c[0] + R[0] * 1.14 * np.cos(phi),
                          c[1] + R[1] * 1.14 * np.sin(phi) * np.cos(th),
                          c[2] + R[2] * 1.14 * np.sin(phi) * np.sin(th)])
            g.put(name, box_m(g, p - grow, p + grow), col)


def ripple(X, Y, Z):
    """貝の殻の放射状のひだ (y-z 面の角度で 12 本)"""
    return 1 + 0.05 * np.cos(12 * np.arctan2(Y - CY, Z))


def shell(g, c, R, T, mod=ripple):
    return ell(g, c, R, mod, coarse=True) & ~ell(g, c, (R[0] - T, R[1] - T, R[2] - T), coarse=True)


# ---------------------------------------------------------------- 案A 大貝

def build_A(g, ph):
    # 第3段階は心臓が殻の前へ出る (殻の窪みから顔を出す)
    dz = 5.5 if ph == 3 else 0.0
    heart(g, ph, dz)
    R = (14.0, 12.5, 9.5)
    X, Y, Z = g.X, g.Y, g.Z
    ribs = np.cos(12 * np.arctan2(Y - CY, Z)) > 0.9        # 放射状の肋 (細い線)
    rib_col = lambda mm: soft(g, TEAL_P, 0.03)(mm)
    base_col = soft(g, NACRE, 0.03)

    def shell_col(mm):
        c = base_col(mm)
        r = ribs[mm]
        c[r] = rib_col(mm)[r]
        return c

    if ph == 1 or ph == 3:
        sh = shell(g, (0, CY, 0), R, 1.5)
        if ph == 1:
            # 狭い口 (前に縦に細く開く)。ここから心臓の光が見える
            gap = (np.abs(X) <= 1.2) & (np.abs(Y - CY) <= 5.0) & (Z > 0)
            sh &= ~gap
        else:
            # 割れた殻: 斜めの割れ目 + 前上の大きな欠け。心臓は前へ出る
            g.put("water_ring", ((np.sqrt(X * X + Z * Z) - 10.0) ** 2 + (Y - CY) ** 2 <= 1.0),
                  solid(WATER_G), glow=True)
            Xc, Yc, Zc = (np.floor(a) + 0.5 for a in (X, Y, Z))
            broken = (np.sin(Xc * 0.8 + Yc * 0.5) + np.cos(Zc * 0.7 + Yc * 0.9)) > 0.95
            sh &= ~broken
            sh &= ~((X > -7.0) & (X < 7.0) & (Y > CY + 1.0) & (Z > 2.0))
            sh &= ~((X > 7.0) & (Y < CY - 2.0) & (Z > -4.0))
            sh &= ~((X < -6.0) & (Y > CY - 6.0) & (Y < CY + 5.0) & (Z < 4.0))   # 左の大きな欠け
            sh &= ~((Y < CY - 6.0) & (np.abs(X) < 9.0) & (Z > -2.0))            # 下の欠け
        g.put("valve_l", sh & (X < -0.5), shell_col)
        g.put("valve_r", sh & (X > 0.5), shell_col)
        g.put("hinge", sh & (np.abs(X) <= 0.5), soft(g, NACRE_D, 0.03))
        if ph == 1:
            # 珊瑚は数か所だけ (殻の縁に少し)
            barnacles(g, "barnacle", (0, CY, 0), R, 0.8, soft(g, CORAL[0], 0.03), pick=0.12)
        else:
            # 飛び散った殻の欠片 (殻の外側に浮く)
            rng = np.random.default_rng(44)
            cnt = 0
            while cnt < 22:
                d = rng.normal(size=3)
                d = d / np.linalg.norm(d)
                p = np.array([CX, CY, CZ]) + d * np.array(R) * 1.22
                g.put("shard", box_m(g, p - 0.8, p + 0.8), soft(g, NACRE_D, 0.05))
                cnt += 1
            moon_disk(g)
    else:
        # 大きく開いた殻: 2 枚の椀を外へ ±9 ずらす。椀の開いた面は心臓を向く
        cut_l = shell(g, (-9.0, CY, 0), R, 1.5) & (X < -9.0)
        cut_r = shell(g, (9.0, CY, 0), R, 1.5) & (X > 9.0)
        g.put("valve_l", cut_l, shell_col)
        g.put("valve_r", cut_r, shell_col)
        kelp = [
            [(-6.5, CY - 5, 2.5), (-9.0, CY - 8, 4.0), (-7.5, CY - 11, 6.0), (-10.0, CY - 13, 5.0)],
            [(6.5, CY - 5, -2.5), (9.0, CY - 8, -4.0), (7.5, CY - 11, -6.0), (10.0, CY - 13, -5.0)],
            [(-5.0, CY + 4, -3.0), (-8.0, CY + 6, -5.5), (-10.0, CY + 9, -4.5)],
            [(5.0, CY + 4, 3.0), (8.0, CY + 6, 5.5), (10.0, CY + 9, 4.5)],
        ]
        for pts in kelp:
            g.put("kelp", tube3(g, pts, 0.7), tint(g, KELP, (120, 186, 96), 0.3))
        rng = np.random.default_rng(31)
        for _ in range(60):
            p = np.array([rng.uniform(-16, 16), CY + rng.uniform(-12, 14), rng.uniform(-15, 15)])
            if np.linalg.norm(p - np.array([CX, CY, CZ])) < 10.5:
                continue
            g.put("droplet", box_m(g, p - 0.6, p + 0.6), solid(WATER_G), glow=True)


# ---------------------------------------------------------------- 案B 巻貝

def spiral(n=120, turns=2.0):
    t = np.linspace(0, 1, n)
    th = 2 * np.pi * turns * t + 0.6
    Rr = 9.0 + 6.5 * t
    P = np.stack([CX + Rr * np.cos(th), CY + Rr * np.sin(th), 1.2 * t * np.sin(th * 0.5)], 1)
    s = 1.4 + 1.6 * t
    return t, P, s


def spiral_tube(g, t_max=1.0, window=None):
    """巻貝の殻 (中が空いた螺旋の管)。window = (t0, t1) の間は、前側の殻を破って中の部屋を光らせる。
    戻り値: (殻の管, 光る部屋)"""
    t, P, s = spiral()
    grooves = {int(round((k + 0.5) / 10 * (len(t) - 1))) for k in range(10)}   # 部屋の仕切りの溝
    outer = np.zeros(g.n, bool)
    inner = np.zeros(g.n, bool)
    lit = np.zeros(g.n, bool)
    winmask = np.zeros(g.n, bool)
    for i in range(len(t)):
        if t[i] > t_max:
            continue
        si = s[i] - (0.5 if i in grooves else 0.0)
        o = ell(g, P[i], (si, si, si * 0.8), coarse=True)
        ii = ell(g, P[i], (s[i] - 1.0, s[i] - 1.0, s[i] * 0.8 - 1.0), coarse=True)
        outer |= o
        inner |= ii
        if window and window[0] <= t[i] <= window[1]:
            winmask |= o & (g.Z > 0.5)
            lit |= ii & (g.Z > 0.5)
    shell_m = outer & ~inner & ~winmask
    return shell_m, lit


def build_B(g, ph):
    heart(g, ph)
    if ph == 1:
        sh, _ = spiral_tube(g)
        g.put("spiral", sh, tint(g, NACRE, (240, 226, 236), 0.3))
        t, P, s = spiral()
        # 口の縁 (巻貝の入口に厚い縁を一周分つける)
        ring = np.zeros(g.n, bool)
        for k in (-12, -9, -6, -3):
            c = P[k]
            ring |= ell(g, c, (s[k] + 0.9, s[k] + 0.9, s[k] * 0.8 + 0.9), coarse=True)
        ring &= ~ell(g, P[-1], (s[-1] - 0.2, s[-1] - 0.2, s[-1] * 0.8 - 0.2), coarse=True)
        g.put("aperture_lip", ring, soft(g, NACRE_D, 0.03))
        # 螺旋のフジツボと珊瑚の小さな粒 (殻の外側の巻に点々と)
        rng = np.random.default_rng(21)
        for k in range(10, len(t) - 4, 11):
            nrm = P[k] - np.array([CX, CY, CZ])
            nrm = nrm / np.linalg.norm(nrm)
            c = P[k] + nrm * (s[k] + 0.6)
            col = soft(g, CORAL[(k // 11) % len(CORAL)], 0.05) if rng.random() < 0.6 else soft(g, BLEACH, 0.03)
            g.put("barnacle", box_m(g, c - 0.6, c + 0.6), col)
        # 珊瑚の触手 (螺旋の外側の巻から垂れる)
        for i, k in enumerate((96, 108, 116)):
            p0 = P[k]
            nrm = (p0 - np.array([CX, CY, CZ]))
            nrm = nrm / np.linalg.norm(nrm)
            pts = [p0, p0 + nrm * 2.5, p0 + nrm * 4.5 + np.array([0, -2.0, 1.0]),
                   p0 + nrm * 5.5 + np.array([0.5, -5.0, 2.0 * (i - 1)])]
            g.put("frond", tube3(g, pts, 0.7), palette_by_node(g, np.array(pts), CORAL[i:] + CORAL[:i]))
    elif ph == 2:
        sh, lit = spiral_tube(g, t_max=0.62, window=(0.3, 0.62))
        g.put("spiral", sh, tint(g, NACRE, (246, 214, 210), 0.35))
        g.put("chamber", lit, solid(CORE_T), glow=True)
        # 壊れた外の巻の欠片
        rng = np.random.default_rng(5)
        for _ in range(4):
            c = np.array([rng.uniform(-14, 14), CY + rng.uniform(-14, 14), rng.uniform(-3, 3)])
            if np.linalg.norm(c[:2] - np.array([CX, CY])) < 10.5:
                continue
            g.put("shard", box_m(g, c - 1.0, c + 1.0), tint(g, NACRE_D, CORAL[0], 0.3))
    else:
        sh, lit = spiral_tube(g, t_max=0.3, window=(0.0, 0.3))
        g.put("spiral", sh, tint(g, NACRE, (240, 226, 236), 0.3))
        g.put("chamber", lit, solid(CORE_S), glow=True)
        # 心臓の周りを回る水滴の輪 (銀青の光)
        for j in range(20):
            a = 2 * np.pi * j / 20
            c = np.array([CX + 12.5 * np.cos(a), CY + 1.2 * np.sin(2 * a), CZ + 12.5 * np.sin(a)])
            g.put("water_drop", box_m(g, c - 0.6, c + 0.6), solid(WATER_G), glow=True)
        # 周りを回る殻の欠片 (月の光をはね返す)
        for j in range(14):
            a = 2 * np.pi * j / 14 + 0.3
            c = np.array([CX + 17.0 * np.cos(a), CY + 3.0 * np.sin(2 * a), CZ + 4.0 * np.sin(a)])
            g.put("orbit", box_m(g, c - np.array([1.0, 0.8, 1.0]), c + np.array([1.0, 0.8, 1.0])),
                  tint(g, NACRE, SILVER_G, 0.5), glow=False)
        moon_crescent(g)


# ---------------------------------------------------------------- 案C 珊瑚の籠

def fib_sphere(n, R):
    i = np.arange(n) + 0.5
    phi = np.arccos(1 - 2 * i / n)
    th = np.pi * (1 + 5 ** 0.5) * i
    return np.stack([R * np.cos(th) * np.sin(phi), R * np.cos(phi), R * np.sin(th) * np.sin(phi)], 1) + [CX, CY, CZ]


def build_C(g, ph):
    heart(g, ph)
    nodes = fib_sphere(20, 11.5)
    edges = [(a, b) for a in range(len(nodes)) for b in range(a + 1, len(nodes))
             if np.linalg.norm(nodes[a] - nodes[b]) < 9.6]
    rng = np.random.default_rng(12)
    twigs = []
    for k, p in enumerate(nodes):
        if k % 2:
            continue
        d = (p - np.array([CX, CY, CZ]))
        d = d / np.linalg.norm(d) + rng.uniform(-0.4, 0.4, 3)
        d = d / np.linalg.norm(d)
        twigs.append([p, p + d * 2.6])
    if ph == 1:
        # 籠を軽くする: 辺の 1/3 と小枝の 1/3 を抜く (約 800 箱に収める)
        cage = np.zeros(g.n, bool)
        for k, (a, b) in enumerate(edges):
            if k % 3 == 2:
                continue
            cage |= tube3(g, [nodes[a], nodes[b]], 0.7)
        for k, pts in enumerate(twigs):
            if k % 3 != 0:
                continue
            cage |= tube3(g, pts, 0.6)
        for p in nodes:
            cage |= ell(g, p, (0.9, 0.9, 0.9), coarse=True)
        g.put("cage", cage, palette_by_node(g, nodes, CORAL))
        # 海藻の房 (籠の底から)
        g.put("kelp", tube3(g, [(-4, CY - 9, 2), (-5, CY - 12, 4), (-3, CY - 14, 5)], 0.6), tint(g, KELP))
    elif ph == 2:
        snapped = {e for k, e in enumerate(edges) if k % 4 == 1}
        cage = np.zeros(g.n, bool)
        for a, b in edges:
            if (a, b) in snapped:
                continue
            cage |= tube3(g, [nodes[a], nodes[b]], 0.8)
        for pts in twigs:
            cage |= tube3(g, pts, 0.6)
        for p in nodes:
            cage |= ell(g, p, (1.1, 1.1, 1.1))
        g.put("cage", cage, tint(g, BLEACH, BLEACH_D, 0.35))
    else:
        keep = [e for k, e in enumerate(edges) if k % 3 == 0]
        for k, (a, b) in enumerate(keep):
            mid = (nodes[a] + nodes[b]) / 2
            off = (mid - np.array([CX, CY, CZ]))
            off = off / np.linalg.norm(off) * 3.5
            pts = [nodes[a] + off, nodes[b] + off]
            g.put(f"shard{k}", tube3(g, pts, 0.8), tint(g, CORAL[k % len(CORAL)], BLEACH, 0.2))
        # 水の殻: 籠を包む水の粒 (銀青の光)
        for p in fib_sphere(40, 14.5):
            g.put("water", box_m(g, p - 0.6, p + 0.6), solid(WATER_G), glow=True)
        moon_disk(g)


# ---------------------------------------------------------------- 書き出し

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
    # boss_v4 と同じ角と向き (セルの座標で)
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
    # アトラス (2 px / 単位 = 1 セル 1 画素)
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
        "description": {"identifier": "geometry.skyabyss.tide_shell_heart",
                        "texture_width": AW, "texture_height": AH,
                        "visible_bounds_width": 5, "visible_bounds_height": 5,
                        "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones_out}]}
    cubes = sum(len(b["cubes"]) for b in bones_out)
    return geo, tex, gtex, cubes


# ---------------------------------------------------------------- 描画とシート

VIEW_34 = (35, 22)
VIEW_FRONT = (0, 6)
BUILDERS = {"a": build_A, "b": build_B, "c": build_C}
NAMES = {"a": "A 大貝", "b": "B 巻貝", "c": "C 珊瑚の籠"}
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
            (d / f"phase{ph}.geo.json").write_text(json.dumps(geo, ensure_ascii=False, indent=1), encoding="utf-8")
            Image.fromarray(tex).save(d / f"phase{ph}.tex.png")
            Image.fromarray(gtex).save(d / f"phase{ph}.glow.png")
            im34 = draw(geo, tex, gtex, VIEW_34)
            im34.save(d / f"phase{ph}.render.png")
            imf = draw(geo, tex, gtex, VIEW_FRONT)
            imf.save(d / f"phase{ph}.front.png")
            results[f"{opt}{ph}"] = {"cubes": cubes, "bones": len(geo["minecraft:geometry"][0]["bones"]),
                                     "atlas": [AW, tex.shape[0]], "overlaps": len(ov), "ov_list": ov[:3]}
            print(opt, ph, results[f"{opt}{ph}"], flush=True)
            ims34.append(label_img(im34, f"{PHASE_NAMES[ph]}  {cubes} 箱"))
            imsF.append(label_img(imf, f"{PHASE_NAMES[ph]} 正面"))
        cols.append((NAMES[opt], ims34 + imsF))
    (OUT / "stats.json").write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")
    return cols, results


def bars():
    """ボスバーの色案 (第1〜第3段階)"""
    W, Hh = 720, 260
    im = Image.new("RGB", (W, Hh), (18, 18, 26))
    d = ImageDraw.Draw(im)
    d.text((20, 14), "ボスバーの色案 (段階ごと)", font=font(24), fill=(235, 235, 245))
    spec = [(1, "第1段階 閉じた大貝", (96, 220, 210)),
            (2, "第2段階 開いた殻・潮の満ち引き", (246, 128, 130)),
            (3, "第3段階 月潮・割れた殻", (170, 200, 255))]
    for i, (ph, label, col) in enumerate(spec):
        y = 64 + i * 62
        d.text((20, y), label, font=font(18), fill=(200, 200, 215))
        d.rectangle((20, y + 26, 700, y + 46), fill=(40, 40, 52))
        d.rectangle((20, y + 26, 20 + int(680 * (1.0 - 0.25 * (i))), y + 46), fill=col)
        d.rectangle((20, y + 26, 700, y + 46), outline=(90, 90, 110))
    return im


def main():
    opts = sys.argv[1:] or ["a", "b", "c"]
    cols, results = run(opts)
    sh = sheet("潮殻の心臓 第2回ラフ (3案 x 3段階)", cols,
               note="上3段: 斜めからの絵 / 下3段: 正面。光る画素は核・月・水の粒。すべて回さない箱 (重なりなし)")
    sh.save(SHEET)
    if len(opts) == 3:
        bars().save(OUT / "bars.png")


if __name__ == "__main__":
    main()
