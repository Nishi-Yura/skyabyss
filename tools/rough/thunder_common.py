"""雷翼の巨神のラフの共通部品 (心臓・敵・中ボス・武器のような小さめのモデル用)。

使い方 (他のスクリプトから):
    import sys; from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import thunder_common as tc

作り方 (tide_heart.py の Grid と同じ考え方を、範囲を引数にして使い回せるようにした):
- 1 セル = 0.5 単位 = テクスチャ 1 画素 (2 px / 単位 = 32 px / ブロック)。
- 1 つのモデルは 1 つの格子に彫る。セルの持ち主 (骨) は 1 つだけなので、箱どうしは格子の上で重ならない。
  書き出しのときに「箱の体積の合計 == 埋まったセルの数」を確かめ、check_overlaps でも 0 を確かめる。
- 箱は骨ごとに貪欲法でまとめる (6 通りの軸の順で一番少ないもの)。面がすべて隠れた箱は捨てる。
- 回転は使わない (曲がりは 0.5 単位の段で作る)。アニメで動かす骨は pivot だけ持つ。
- 面の向きの明るさを焼き込む (光るセルは除く)。光るセルは glowmask へ (0 画素は不可: GeckoLib が落ちる)。
- 乱数は seed 固定 (Grid の noise)。同じ引数なら同じ結果。
- 座標: 1 単位 = 1/16 ブロック。y が上。ラフは +Z が前 (ゲームへ入れるときに 180 度回す。tide_mobs と同じ)。
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
from voxel import Face, check_overlaps, font, load_geo, render, sheet  # noqa: E402,F401

REVIEW = ROOT / "docs/review/thunder_wing_roughs"
H = 0.5
LIGHT = np.array([-0.45, 0.75, 0.5])
LIGHT = LIGHT / np.linalg.norm(LIGHT)

# ---------------------------------------------------------------- 雷翼の色 (章の色: 嵐の灰青・白・稲妻の白金・銅)
STORM = (74, 82, 104)        # 嵐の灰青 (羽の地)
STORM_D = (50, 56, 76)
STORM_L = (104, 114, 138)
ASH_W = (214, 218, 228)      # 白い羽 (腹・縁)
ASH_W_D = (170, 176, 192)
SLATE = (40, 44, 58)         # 濃い影の羽
COPPER = (196, 112, 70)      # 磨かれた銅 (雷が錆を落とす)
COPPER_D = (150, 82, 52)
COPPER_L = (226, 150, 100)
VERDI = (88, 150, 132)       # 少しだけ残る緑青
BOLT = (250, 246, 210)       # 稲妻の白金 (光る)
BOLT_Y = (255, 226, 120)     # 稲妻の黄 (光る)
BOLT_B = (170, 210, 255)     # 静電の青白 (光る)
BONE = (222, 212, 190)
BONE_D = (182, 170, 148)
HORN = (60, 54, 52)          # 嘴・爪の黒
CLOTH = (110, 98, 132)       # 風の民の布 (灰紫)
CLOTH_D = (82, 72, 100)
EYE_DARK = (24, 22, 30)


# ---------------------------------------------------------------- 格子

class Grid:
    """セル 0.5 単位の格子。lab = 持ち主の骨の番号 (0 = 空)。lo, hi は単位。"""

    def __init__(self, lo, hi, seed=0):
        self.LO = np.array(lo, float)
        self.HI = np.array(hi, float)
        self.n = tuple(int(round((self.HI - self.LO)[i] / H)) for i in range(3))
        ax = [self.LO[i] + (np.arange(self.n[i]) + 0.5) * H for i in range(3)]
        self.X, self.Y, self.Z = np.meshgrid(*ax, indexing="ij")
        self.lab = np.zeros(self.n, np.int16)
        self.glow = np.zeros(self.n, bool)
        self.col = np.zeros(self.n + (3,), float)
        self.names = {}
        self.noise = np.random.default_rng(seed).uniform(0.93, 1.07, self.n)

    def lid(self, name):
        if name not in self.names:
            self.names[name] = len(self.names) + 1
        return self.names[name]

    def put(self, name, m, color, glow=False, over=False):
        """空いているセルに、骨 name の持ち物として色を塗る (over なら他の骨のセルも取る)。
        color: (r,g,b) か、マスク m を受けて (N,3) を返す関数。glow: bool か、(N,) を返す関数"""
        if not over:
            m = m & (self.lab == 0)
        if not m.any():
            return m
        self.lab[m] = self.lid(name)
        c = color(m) if callable(color) else np.repeat(np.array(color, float)[None], int(m.sum()), 0)
        self.col[m] = c
        self.glow[m] = glow(m) if callable(glow) else glow
        return m

    def recolor(self, m, color, glow=None):
        """埋まっているセルの色だけを替える (持ち主は変えない)"""
        m = m & (self.lab > 0)
        if not m.any():
            return
        c = color(m) if callable(color) else np.repeat(np.array(color, float)[None], int(m.sum()), 0)
        self.col[m] = c
        if glow is not None:
            self.glow[m] = glow(m) if callable(glow) else glow

    def carve(self, m):
        """セルを空ける (彫る)"""
        self.lab[m] = 0
        self.glow[m] = False

    def occ(self, name=None):
        return self.lab > 0 if name is None else self.lab == self.names.get(name, -1)


def sl_of(g, lo, hi):
    lo = np.clip(np.floor((np.asarray(lo, float) - g.LO) / H).astype(int), 0, np.array(g.n))
    hi = np.clip(np.ceil((np.asarray(hi, float) - g.LO) / H).astype(int), 0, np.array(g.n))
    return tuple(slice(lo[i], hi[i]) for i in range(3))


def ell(g, c, r, mod=None):
    """楕円体 (中心 c、半径 r)。mod(X,Y,Z) は半径の倍率。"""
    c = np.asarray(c, float)
    r = np.asarray(r, float)
    out = np.zeros(g.n, bool)
    s = sl_of(g, c - r * 1.6 - 0.5, c + r * 1.6 + 0.5)
    X, Y, Z = g.X[s], g.Y[s], g.Z[s]
    d = ((X - c[0]) / r[0]) ** 2 + ((Y - c[1]) / r[1]) ** 2 + ((Z - c[2]) / r[2]) ** 2
    if mod is not None:
        d = d / np.maximum(mod(X, Y, Z), 1e-3) ** 2
    out[s] = d <= 1.0
    return out


def box_m(g, lo, hi):
    """箱 (lo〜hi、単位)。セルの中心が内側にあるもの"""
    out = np.zeros(g.n, bool)
    s = sl_of(g, lo, hi)
    X, Y, Z = g.X[s], g.Y[s], g.Z[s]
    out[s] = ((X > lo[0]) & (X < hi[0]) & (Y > lo[1]) & (Y < hi[1]) & (Z > lo[2]) & (Z < hi[2]))
    return out


def tube(g, pts, r):
    """折れ線の管。r は一つの値か、点ごとの半径 (区間の中で線形に変わる = 先細り)"""
    pts = [np.asarray(p, float) for p in pts]
    rs = [float(r)] * len(pts) if np.isscalar(r) else [float(v) for v in r]
    out = np.zeros(g.n, bool)
    for (a, b), (ra, rb) in zip(zip(pts[:-1], pts[1:]), zip(rs[:-1], rs[1:])):
        rm = max(ra, rb)
        s = sl_of(g, np.minimum(a, b) - rm - 0.5, np.maximum(a, b) + rm + 0.5)
        P = np.stack([g.X[s] - a[0], g.Y[s] - a[1], g.Z[s] - a[2]], -1)
        v = b - a
        t = np.clip((P @ v) / max(v @ v, 1e-9), 0, 1)
        d = np.linalg.norm(P - t[..., None] * v, axis=-1)
        out[s] |= d <= ra + (rb - ra) * t
    return out


def plate(g, c, normal, r, thick):
    """円板 (中心 c、法線 normal、半径 r、厚み thick)"""
    c = np.asarray(c, float)
    n = np.asarray(normal, float)
    n = n / np.linalg.norm(n)
    out = np.zeros(g.n, bool)
    s = sl_of(g, c - r - thick, c + r + thick)
    P = np.stack([g.X[s] - c[0], g.Y[s] - c[1], g.Z[s] - c[2]], -1)
    h = P @ n
    rad = np.linalg.norm(P - h[..., None] * n, axis=-1)
    out[s] = (np.abs(h) <= thick / 2) & (rad <= r)
    return out


def ring(g, c, normal, r, w, thick):
    """輪 (半径 r、幅 w、厚み thick)"""
    c = np.asarray(c, float)
    n = np.asarray(normal, float)
    n = n / np.linalg.norm(n)
    out = np.zeros(g.n, bool)
    s = sl_of(g, c - r - w - thick, c + r + w + thick)
    P = np.stack([g.X[s] - c[0], g.Y[s] - c[1], g.Z[s] - c[2]], -1)
    h = P @ n
    rad = np.linalg.norm(P - h[..., None] * n, axis=-1)
    out[s] = (np.abs(h) <= thick / 2) & (np.abs(rad - r) <= w / 2)
    return out


def zigzag(g, a, b, amp, n_seg, r, axis_hint=(0, 1, 0), seed=0):
    """稲妻の形の折れ線 (a から b へ、n_seg 区間、横へ amp だけ互い違いに振る)"""
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    v = b - a
    side = np.cross(v, np.asarray(axis_hint, float))
    if np.linalg.norm(side) < 1e-6:
        side = np.cross(v, (1, 0, 0))
    side = side / np.linalg.norm(side)
    pts = [a]
    for i in range(1, n_seg):
        t = i / n_seg
        k = (1 if i % 2 else -1) * amp * (0.6 + 0.4 * ((i * 7 + seed) % 3) / 2)
        pts.append(a + v * t + side * k)
    pts.append(b)
    return tube(g, pts, r)


# ---------------------------------------------------------------- 色の関数

def tint(g, base, alt=None, p=0.0):
    """セルごとに少し揺らぐ色。p の割合で alt を混ぜる"""
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
    base = np.array(base, float)

    def f(m):
        k = 1 + amp * (g.noise[m] - 1) / 0.07
        return np.repeat(base[None], int(m.sum()), 0) * k[:, None]
    return f


def solid(color):
    return lambda m: np.repeat(np.array(color, float)[None], int(m.sum()), 0)


def by_height(g, stops):
    """y の高さで色を混ぜる。stops = [(y, (r,g,b)), ...] (y の小さい順)"""
    ys = np.array([s[0] for s in stops], float)
    cs = np.array([s[1] for s in stops], float)

    def f(m):
        y = g.Y[m]
        out = np.stack([np.interp(y, ys, cs[:, i]) for i in range(3)], -1)
        return out * g.noise[m][:, None]
    return f


def rows(g, axis, period, c0, c1, frac=0.5):
    """羽の列: axis (0=x,1=y,2=z) の向きに period ごとに c0 と c1 を交互にする (frac が c1 の割合)"""
    c0 = np.array(c0, float)
    c1 = np.array(c1, float)

    def f(m):
        a = (g.X, g.Y, g.Z)[axis][m]
        sel = np.mod(a, period) >= period * (1 - frac)
        out = np.where(sel[:, None], c1[None], c0[None])
        return out * g.noise[m][:, None]
    return f


def shade_all(g):
    """面の向きの明るさを焼き込む (光るセルは除く)。書き出しの前に 1 回だけ"""
    occ = g.lab > 0
    e = ~np.pad(occ, 1)
    nx = e[2:, 1:-1, 1:-1].astype(float) - e[:-2, 1:-1, 1:-1]
    ny = e[1:-1, 2:, 1:-1].astype(float) - e[1:-1, :-2, 1:-1]
    nz = e[1:-1, 1:-1, 2:].astype(float) - e[1:-1, 1:-1, :-2]
    n = np.stack([nx, ny, nz], -1)
    nl = np.linalg.norm(n, axis=-1)
    lamb = np.where(nl > 0, (n @ LIGHT) / np.maximum(nl, 1e-9), 0.0)
    lum = 0.72 + 0.34 * np.clip(lamb, 0, 1)
    m = occ & ~g.glow
    g.col[m] = np.clip(g.col[m] * lum[m][:, None], 0, 255)


# ---------------------------------------------------------------- 箱と書き出し

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


def export(g, ident, pivots=None, parents=None, aw=512, prune_hidden=True):
    """格子を geo にする。pivots = {骨: (x,y,z)}、parents = {骨: 親}。
    骨をまたいで隠れる面も描かない (ラフ用。アニメで骨が動く所は、installer で骨の中だけで数え直すこと)。
    返り値: geo, tex(RGBA), glow(RGBA), stats(dict)"""
    shade_all(g)
    occ_all = g.lab > 0
    pad = np.pad(occ_all, 1)
    pivots = pivots or {}
    parents = parents or {}
    recs, bones_out = [], []
    vol = 0
    hidden = 0
    for name, lid in g.names.items():
        m = g.lab == lid
        if not m.any():
            bones_out.append({"name": name, "pivot": list(map(float, pivots.get(name, (0, 0, 0)))), "cubes": []})
            continue
        cubes = []
        for bx in greedy(m):
            x0, y0, z0, x1, y1, z1 = bx
            s = (x1 - x0, y1 - y0, z1 - z0)
            vol += s[0] * s[1] * s[2]
            cube = {"origin": [float(g.LO[0] + x0 * H), float(g.LO[1] + y0 * H), float(g.LO[2] + z0 * H)],
                    "size": [s[0] * H, s[1] * H, s[2] * H], "uv": {}}
            seen = False
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
                seen = True
                tex = np.zeros((Hh, W, 4), np.uint8)
                tex[..., :3] = np.clip(g.col[xi, yi, zi], 0, 255).astype(np.uint8)
                tex[..., 3] = 255
                recs.append({"W": W, "H": Hh, "tex": tex, "gl": g.glow[xi, yi, zi], "cube": cube, "face": fname})
            if seen or not prune_hidden:
                cubes.append(cube)
            else:
                hidden += 1
        b = {"name": name, "pivot": [float(v) for v in pivots.get(name, (0, 0, 0))], "cubes": cubes}
        if name in parents:
            b["parent"] = parents[name]
        bones_out.append(b)
    assert vol == int(occ_all.sum()), "箱の体積の合計がセル数と合わない (重なりか抜けがある)"
    # 親を先に並べる (GeckoLib は順を問わないが、読みやすさのため)
    recs.sort(key=lambda r: (-r["H"], -r["W"]))
    x = y = shelf = 0
    for r in recs:
        if x + r["W"] > aw:
            x, y, shelf = 0, y + shelf, 0
        r["u"], r["v"] = x, y
        x += r["W"]
        shelf = max(shelf, r["H"])
    ah = y + shelf
    ah = max(16, int(math.ceil(ah / 16.0) * 16))
    tex = np.zeros((ah, aw, 4), np.uint8)
    gtex = np.zeros((ah, aw, 4), np.uint8)
    for r in recs:
        u, v, W, Hh = r["u"], r["v"], r["W"], r["H"]
        tex[v:v + Hh, u:u + W] = r["tex"]
        gtex[v:v + Hh, u:u + W, :3] = r["tex"][..., :3] * r["gl"][..., None]
        gtex[v:v + Hh, u:u + W, 3] = np.where(r["gl"], 255, 0)
        r["cube"]["uv"][r["face"]] = {"uv": [u, v], "uv_size": [W, Hh]}
    span = (g.HI - g.LO) / 16.0
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.skyabyss.{ident}",
                        "texture_width": aw, "texture_height": ah,
                        "visible_bounds_width": float(round(max(span[0], span[2]) + 1, 1)),
                        "visible_bounds_height": float(round(span[1] + 1, 1)),
                        "visible_bounds_offset": [0, float(round(span[1] / 2, 1)), 0]},
        "bones": bones_out}]}
    ncubes = sum(len(b["cubes"]) for b in bones_out)
    glow_px = int((gtex[..., 3] > 0).sum())
    stats = {"cubes": ncubes, "hidden_dropped": hidden, "cells": int(occ_all.sum()), "atlas": [aw, ah],
             "glow_px": glow_px, "bones": len(bones_out),
             "size_blocks": [round(float(v), 2) for v in span]}
    return geo, tex, gtex, stats


def save(folder: Path, stem: str, geo, tex, gtex, stats):
    """geo.json / tex.png / glow.png を書き、重なりを数える (stats に入れて返す)"""
    folder.mkdir(parents=True, exist_ok=True)
    (folder / f"{stem}.geo.json").write_text(json.dumps(geo, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    Image.fromarray(tex).save(folder / f"{stem}.tex.png", optimize=True)
    Image.fromarray(gtex).save(folder / f"{stem}.glow.png", optimize=True)
    ov = check_overlaps(geo)
    stats = dict(stats)
    stats["overlaps"] = len(ov)
    assert stats["glow_px"] > 0, f"{stem}: glowmask is empty (GeckoLib の発光の層が落ちる)"
    return stats


# ---------------------------------------------------------------- 描画

def faces_of(geo, tex, gtex, offset=(0, 0, 0), R=None, pose=None):
    """geo の面の一覧。offset と回転 R (3x3) で動かせる (武器を手の受け口へ付けて描くため)"""
    fs = load_geo(geo, tex, gtex, pose=pose)
    if R is None and not any(offset):
        return fs
    R = np.eye(3) if R is None else np.asarray(R, float)
    t = np.asarray(offset, float)
    out = []
    for f in fs:
        out.append(Face(R @ f.origin + t, R @ f.eu, R @ f.ev, f.tex, f.glow, f.shade))
    return out


def rot_y(deg):
    a = math.radians(deg)
    return np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])


def rot_x(deg):
    a = math.radians(deg)
    return np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])


def rot_z(deg):
    a = math.radians(deg)
    return np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])


def player_ref(x=0.0, z=0.0, y=0.0):
    """大きさの目安の人 (プレイヤーと同じ 32 単位の背丈、灰色の箱)"""
    g = Grid((x - 5, y, z - 3), (x + 5, y + 33, z + 3), seed=1)
    gray = (120, 124, 134)
    g.put("ref", box_m(g, (x - 4, y + 24, z - 4), (x + 4, y + 32, z + 4)), (150, 150, 160))
    g.put("ref", box_m(g, (x - 4, y + 12, z - 2), (x + 4, y + 24, z + 2)), gray)
    g.put("ref", box_m(g, (x - 4, y, z - 2), (x + 4, y + 12, z + 2)), (90, 94, 110))
    g.put("ref", box_m(g, (x - 2, y + 26, z + 2), (x - 1, y + 27, z + 2.6)), (255, 255, 255), glow=True)
    geo, tex, gtex, _ = export(g, "ref")
    return faces_of(geo, tex, gtex)


def draw(faces, yaw=35, pitch=22, size=420, bg="dark", margin=0.06):
    return render(faces, yaw, pitch, (size, size), bg=bg, margin=margin)


def night(im: Image.Image, k=0.45):
    """ゲームの夜・暗い部屋を真似る: 全体を暗くし、光る所はにじみを残す (目安)"""
    a = np.asarray(im).astype(float)
    lum = a.max(-1, keepdims=True)
    bright = np.clip((lum - 200) / 55.0, 0, 1)
    out = a * (k + (1 - k) * bright)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def label(im, text, size=17):
    im = im.copy()
    d = ImageDraw.Draw(im)
    d.text((8, 6), text, font=font(size), fill=(230, 230, 240))
    return im


def write_stats(path: Path, stats: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(stats, ensure_ascii=False, indent=1), encoding="utf-8")
