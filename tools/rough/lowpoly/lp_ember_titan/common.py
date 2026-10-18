"""余燼 (ember_titan) 低ポリ案の共通部品。ラフ。src には触らない。

座標は 1 単位 = 1/16 ブロック (ゲーム内では x8 で 0.5 ブロック)、y が上、正面は -Z。
骨の名前・階層・pivot は src/.../geo/entity/ember_titan{,_mid,_far}.geo.json から読む (休めの回転なし)。
メッシュの頂点はモデル全体の絶対座標 (geo の cube と同じ。+X は余燼の左 = leg_fl 側)。
LOD (詳細度): 0 = 近, 1 = 中, 2 = 遠。
"""
from __future__ import annotations

import inspect
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
LP = HERE.parent
ROOT = HERE.parents[3]
for _p in (LP, ROOT / "tools/rough"):
    sys.path.insert(0, str(_p))
import lpkit  # noqa: E402
from lpkit import *  # noqa: E402,F401,F403
from lpkit import _mk  # noqa: E402
from bone_sentinel_lp import hash1, hash3, vnoise, BAYER  # noqa: E402
import voxel  # noqa: E402

GEO = ROOT / "src/main/resources/assets/skyabyss/geo/entity"
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
OUTDIR = ROOT / "docs/review/lowpoly_roughs/lp_ember_titan"
SUBJ = "ember_titan"
GEOS = ("ember_titan", "ember_titan_mid", "ember_titan_far")

# lpkit.render の光のにじみは青白。余燼は橙なので、同じ関数の色と強さだけ差し替えて使う (lpkit.py は触らない)
_src = inspect.getsource(lpkit.render).replace("[140, 230, 255]", "[255, 140, 50]").replace("* 0.9", "* 0.6") \
    .replace("def render(", "def render_ember(")
exec(compile(_src, "render_ember", "exec"), lpkit.__dict__)
render_ember = lpkit.render_ember


def V(*a):
    return np.array(a, float)


def smooth(a, b, x):
    t = min(max((x - a) / (b - a), 0.0), 1.0)
    return t * t * (3 - 2 * t)


# ------------------------------------------------------------------ 骨 (今の geo から)

def load_geo(name=SUBJ):
    return json.loads((GEO / f"{name}.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]


def bone_defs(name=SUBJ):
    g = load_geo(name)
    return {b["name"]: (b.get("parent"), [float(x) for x in b["pivot"]], [float(x) for x in b.get("rotation", [0, 0, 0])])
            for b in g["bones"]}


def box_count(name):
    return sum(len(b.get("cubes", [])) for b in load_geo(name)["bones"])


# ------------------------------------------------------------------ 部品 (面取りの箱・面に貼る台)

def slab(bone, mat, c, size, taper=0.0, rot=0.0, jit=0.0, tag=0, bottom=False, top_mat=None, skew=(0, 0)):
    """面取りした箱 (上が taper だけ小さい)。rot: y まわりの回転 (度)。jit: 頂点のゆらぎ"""
    cx, cy, cz = c
    sx, sy, sz = size
    R = rot_y(rot)

    def corner(sxn, szn, up):
        t = taper if up else 0.0
        return V(sxn * max(0.1, sx / 2 - t), sy / 2 if up else -sy / 2, szn * max(0.1, sz / 2 - t))

    bs = [corner(-1, -1, 0), corner(1, -1, 0), corner(1, 1, 0), corner(-1, 1, 0)]
    ts = [corner(-1, -1, 1), corner(1, -1, 1), corner(1, 1, 1), corner(-1, 1, 1)]
    ts = [t + V(skew[0], 0, skew[1]) for t in ts]
    if jit:
        for i in range(4):
            bs[i] = bs[i] + V((hash1(tag * 7 + i, 1) - .5) * jit, 0, (hash1(tag * 7 + i, 2) - .5) * jit)
            ts[i] = ts[i] + V((hash1(tag * 7 + i, 3) - .5) * jit, (hash1(tag * 7 + i, 4) - .5) * jit * .6,
                              (hash1(tag * 7 + i, 5) - .5) * jit)
    bs = [R @ b + V(cx, cy, cz) for b in bs]
    ts = [R @ t + V(cx, cy, cz) for t in ts]
    ref = V(cx, cy, cz)
    faces = []
    for i in range(4):
        j = (i + 1) % 4
        faces.append(_mk([bs[i], bs[j], ts[j], ts[i]], bone, mat, ref))
    faces.append(_mk(ts, bone, top_mat or mat, ref))
    if bottom:
        faces.append(_mk(bs[::-1], bone, mat, ref))
    return [f for f in faces if f]


def plinth(bone, mat, c, nrm_, up, rx, ry, h, shrink=0.8, sides=8, top_mat=None, embed=0.9, rot=0.0):
    """面に貼る平たい多角錐台 (板・まぶた・目)。c は面上の中心、nrm_ は外向き法線。rx: 横方向, ry: 上下方向"""
    n = nrm(nrm_)
    upv = nrm(V(*up) - np.dot(V(*up), n) * n)
    tv = np.cross(upv, n)
    base, top = [], []
    for k in range(sides):
        a = math.radians(rot + 360.0 * k / sides)
        u, v = math.cos(a), math.sin(a)
        base.append(c - n * embed + tv * rx * u + upv * ry * v)
        top.append(c + n * h + tv * rx * shrink * u + upv * ry * shrink * v)
    ref = c - n * embed * 3
    faces = [_mk([base[k], base[(k + 1) % sides], top[(k + 1) % sides], top[k]], bone, mat, ref) for k in range(sides)]
    faces.append(_mk(top, bone, top_mat or mat, ref))
    return [f for f in faces if f]


def mirror_x(faces, bone=None):
    out = []
    for f in faces:
        v = f.verts.copy()
        v[:, 0] = -v[:, 0]
        g = _mk(list(v), bone or f.bone, f.mat, V(-f.ref[0], f.ref[1], f.ref[2]))
        if g is not None:
            out.append(g)
    return out


def col6(bone, mat, base, h, r, sides=6, taper=0.9, top_mat=None, cap=True, rings=1, hint=(1, 0, 0)):
    """立てた柱 (多角柱)。base は根元の中心"""
    base = V(*base)
    path = [base + V(0, h * i / rings, 0) for i in range(rings + 1)]
    ras = [r * (1 - (1 - taper) * i / rings) for i in range(rings + 1)]
    fs = tube(bone, mat, path, ras, sides=sides, hint=hint, phase=360.0 / sides / 2, caps=(False, cap))
    if top_mat:
        for f in fs:
            if f.normal[1] > 0.9:
                f.mat = top_mat
    return fs


def blob(bone, mat, c, r, n=6, tag=0, jit=0.25, flat=1.0):
    """ごつごつした多面体の塊 (煙・岩)。八面体を少し崩したもの"""
    c = V(*c)
    rr = [r * (1 + (hash1(tag * 5 + i, 7) - .5) * 2 * jit) for i in range(6)]
    px, nx = c + V(rr[0], 0, 0), c - V(rr[1], 0, 0)
    py, ny = c + V(0, rr[2] * flat, 0), c - V(0, rr[3] * flat, 0)
    pz, nz = c + V(0, 0, rr[4]), c - V(0, 0, rr[5])
    fs = []
    for a, b, d in [(px, py, pz), (py, nx, pz), (nx, ny, pz), (ny, px, pz), (py, px, nz), (nx, py, nz), (ny, nx, nz), (px, ny, nz)]:
        fs.append(_mk([a, b, d], bone, mat, c))
    return [f for f in fs if f]


# ------------------------------------------------------------------ 胴の断面 (輪の列)

class Hull:
    """z 方向に輪を並べた筒。輪は、右半分の (u, t) の型 (u: 幅の割合 0..1, t: 腹 0 → 背 1) を鏡写しにしたもの。
    keys: [(z, cx, hw, yb, yt)] 中心 x・半幅・底・上。tmpl は (0,0) で始まり (0,1) で終わる。"""

    def __init__(self, tmpl, keys, stations, hook=None, jit=0.02, jit_y=0.4, bone_fn=None, mat_fn=None, seed=0):
        self.tmpl = list(tmpl)
        self.K = len(tmpl)
        self.keys = keys
        self.st = list(stations)
        self.hook = hook
        self.jit, self.jit_y, self.seed = jit, jit_y, seed
        self.rings = [self._ring(z, i) for i, z in enumerate(self.st)]
        self.bone_fn, self.mat_fn = bone_fn, mat_fn

    def key_at(self, z):
        ks = self.keys
        if z <= ks[0][0]:
            return np.array(ks[0][1:], float)
        if z >= ks[-1][0]:
            return np.array(ks[-1][1:], float)
        for a, b in zip(ks, ks[1:]):
            if a[0] <= z <= b[0]:
                t = (z - a[0]) / max(b[0] - a[0], 1e-9)
                t = t * t * (3 - 2 * t)
                return np.array(a[1:], float) * (1 - t) + np.array(b[1:], float) * t

    def _ring(self, z, i):
        cx, hw, yb, yt = self.key_at(z)
        K = self.K
        right = []
        amp = min(1.0, hw / 28.0)
        for k, (u, t) in enumerate(self.tmpl):
            j = self.jit * (hash1(i * 17 + k * 131 + self.seed, 3) - 0.5) * 2 * amp if 0 < k < K - 1 else 0.0
            jy = self.jit_y * (hash1(i * 29 + k * 71 + self.seed, 5) - 0.5) * 2 * amp if 0 < k < K - 1 else 0.0
            x = u * hw * (1 + j)
            y = yb + t * (yt - yb) + jy
            if self.hook:
                x, y = self.hook(z, k, u, t, x, y, hw, yb, yt)
            right.append((x, y))
        pts = [V(cx + x, y, z) for x, y in right]
        left = [V(cx - x, y, z) for x, y in right[1:-1]][::-1]
        return np.array(pts + left)

    def ring_at(self, z):
        st = self.st
        z = min(max(z, st[0]), st[-1])
        for i in range(len(st) - 1):
            if st[i] <= z <= st[i + 1]:
                t = (z - st[i]) / max(st[i + 1] - st[i], 1e-9)
                return self.rings[i] * (1 - t) + self.rings[i + 1] * t, i
        return self.rings[-1], len(st) - 2

    def at(self, z, s, side=1):
        """z の断面の型の位置 s (0..K-1, 小数可) の点と外向きの法線。side=-1 は -X 側"""
        r, i = self.ring_at(z)
        K = self.K
        s = min(max(s, 0.0), K - 1.0)
        k0 = min(int(s), K - 2)
        f = s - k0
        pt = r[k0] * (1 - f) + r[k0 + 1] * f
        e = r[k0 + 1] - r[k0]
        i0 = min(max(i, 0), len(self.st) - 2)
        kk = min(max(int(round(s)), 0), K - 1)
        dz = self.rings[i0 + 1][kk] - self.rings[i0][kk]
        if abs(dz[2]) < 1e-6:
            dz = V(0, 0, 1)
        n = np.cross(e, dz)
        if np.linalg.norm(n) < 1e-9:
            n = V(1, 0, 0)
        n = nrm(n)
        cen = r.mean(0)
        if np.dot(n, pt - cen) < 0:
            n = -n
        if side < 0:
            pt = V(2 * self.key_at(z)[0] - pt[0], pt[1], pt[2])
            n = V(-n[0], n[1], n[2])
        return pt, n

    def top(self, z, x=0.0):
        """背の面の高さ (x の位置で)"""
        r, _ = self.ring_at(z)
        cx = self.key_at(z)[0]
        xs = [(p[0] - cx, p[1]) for p in r[:self.K]]
        ax = abs(x)
        best = xs[-1][1]
        for (x0, y0), (x1, y1) in zip(xs, xs[1:]):
            if x0 >= ax >= x1 or x0 <= ax <= x1:
                t = 0 if abs(x1 - x0) < 1e-9 else (ax - x0) / (x1 - x0)
                best = y0 + (y1 - y0) * t
        return best

    def faces(self, caps=(True, True)):
        K = self.K
        m = 2 * K - 2
        out = []
        for a in range(len(self.st) - 1):
            r0, r1 = self.rings[a], self.rings[a + 1]
            zc = (self.st[a] + self.st[a + 1]) / 2
            cen = (r0.mean(0) + r1.mean(0)) / 2
            for k in range(m):
                k2 = (k + 1) % m
                ti = lambda j: j if j < K else 2 * K - 2 - j
                lo, hi = min(ti(k), ti(k2)), max(ti(k), ti(k2))
                P = [r0[k], r0[k2], r1[k2], r1[k]]
                bone = self.bone_fn(zc, lo, hi) if self.bone_fn else "body"
                mat = self.mat_fn(zc, lo, hi, bone) if self.mat_fn else "rock"
                f = _mk(P, bone, mat, cen)
                if f is not None:
                    out.append(f)
        for idx, cap in zip((0, len(self.st) - 1), caps):
            if cap:
                r = self.rings[idx]
                z = self.st[idx]
                bone = self.bone_fn(z + (0.1 if idx == 0 else -0.1), 2, 3) if self.bone_fn else "body"
                mat = self.mat_fn(z, 2, 3, bone) if self.mat_fn else "rock"
                other = self.rings[1].mean(0) if idx == 0 else self.rings[-2].mean(0)
                f = _mk(list(r), bone, mat, other)
                if f is not None:
                    out.append(f)
        return out


# ------------------------------------------------------------------ アトラスの詰め (lpkit の棚詰めより隙間の少ない MaxRects)

def _shelf(rects, size):
    """rects: [(id, w, h)]。高さの降順の棚詰め (lpkit と同じ)。置けなければ None"""
    x = y = rowh = 0
    place = {}
    for rid, w, h in sorted(rects, key=lambda r: (-r[2], -r[1])):
        if x + w > size:
            x, y, rowh = 0, y + rowh, 0
        if y + h > size or w > size:
            return None
        place[rid] = (x, y, w, h)
        x += w
        rowh = max(rowh, h)
    return place


def build_atlas2(faces, size=128, pad=0):
    """lpkit.build_atlas と同じ戻り値。島を横長に向けてから棚詰め (細長い面の隙間を減らす)"""
    charts = []
    for fi, f in enumerate(faces):
        n = f.normal
        u = nrm(f.verts[1] - f.verts[0])
        w = np.cross(n, u)
        q = np.array([[np.dot(p - f.verts[0], u), np.dot(p - f.verts[0], w)] for p in f.verts]) * lpkit.TEX
        lo = q.min(0)
        q = q - lo
        cw, ch = max(1, int(math.ceil(q[:, 0].max() - 1e-6))), max(1, int(math.ceil(q[:, 1].max() - 1e-6)))
        if ch > cw:
            q = q[:, ::-1].copy()
            cw, ch = ch, cw
        f.uv = q
        charts.append((fi, cw, ch))
    while True:
        place = _shelf([(fi, cw + 2 * pad, ch + 2 * pad) for fi, cw, ch in charts], size)
        if place is not None:
            break
        size *= 2
    P = np.zeros((size, size, 3))
    N = np.zeros((size, size, 3))
    M = np.full((size, size), -1, int)
    L = np.zeros((size, size, 3))
    for fi, (x, y, cw, ch) in place.items():
        f = faces[fi]
        cw, ch = cw - 2 * pad, ch - 2 * pad
        f.chart = (x + pad, y + pad, cw, ch)
        f.uv = f.uv + np.array([x + pad, y + pad])
        xs = np.arange(x, x + cw + 2 * pad) + 0.5
        ys = np.arange(y, y + ch + 2 * pad) + 0.5
        gx, gy = np.meshgrid(xs, ys)
        q = np.stack([gx, gy], -1).reshape(-1, 2)
        pos = np.zeros((len(q), 3))
        loc = np.zeros((len(q), 3))
        best = np.full(len(q), -1e9)
        for (a, b, c) in f.tris():
            A, B, C = f.uv[a], f.uv[b], f.uv[c]
            T = np.array([[B[0] - A[0], C[0] - A[0]], [B[1] - A[1], C[1] - A[1]]])
            if abs(np.linalg.det(T)) < 1e-9:
                continue
            bc = np.linalg.solve(T, (q - A).T).T
            w = np.stack([1 - bc[:, 0] - bc[:, 1], bc[:, 0], bc[:, 1]], -1)
            score = w.min(1)
            sel = score > best
            best[sel] = score[sel]
            pos[sel] = w[sel] @ f.verts[[a, b, c]]
            loc[sel] = w[sel] @ f.loc[[a, b, c]]
        yy = gy.reshape(-1).astype(int)
        xx = gx.reshape(-1).astype(int)
        P[yy, xx] = pos
        L[yy, xx] = loc
        N[yy, xx] = f.normal
        M[yy, xx] = fi
    return size, P, N, M, L


# ------------------------------------------------------------------ 絵

LIGHT = np.array([-0.35, 0.85, -0.4])
LIGHT = LIGHT / np.linalg.norm(LIGHT)


def ramp(pal, v):
    pal = np.asarray(pal)
    return pal[np.clip(np.floor(v * len(pal)).astype(int), 0, len(pal) - 1)].astype(float)


def pick(pal, idx):
    pal = np.asarray(pal)
    return pal[np.clip(idx, 0, len(pal) - 1)].astype(float)


PAL = dict(
    ROCK=np.array([(14, 13, 16), (24, 23, 27), (36, 35, 40), (50, 48, 54), (66, 64, 70), (84, 82, 88)]),
    ROCKW=np.array([(20, 15, 15), (32, 25, 24), (46, 38, 35), (62, 53, 48), (80, 69, 62)]),
    ASH=np.array([(92, 90, 92), (122, 120, 122), (152, 150, 150), (180, 178, 176), (208, 206, 202)]),
    SKIN=np.array([(26, 22, 24), (40, 34, 35), (56, 48, 47), (74, 64, 61), (94, 82, 76)]),
    BELLY=np.array([(60, 52, 50), (82, 72, 68), (106, 94, 88), (130, 118, 108), (152, 140, 128)]),
    LAVA=np.array([(120, 28, 10), (190, 56, 14), (236, 104, 24), (255, 160, 44), (255, 214, 100)]),
    COOL=np.array([(44, 14, 12), (72, 20, 14), (104, 30, 16), (140, 44, 18)]),
    TOOTH=np.array([(120, 110, 96), (160, 150, 130), (196, 188, 164), (226, 218, 194)]),
    MOUTH=np.array([(36, 10, 10), (62, 18, 16), (92, 28, 22), (122, 42, 30)]),
    BRICK=np.array([(52, 20, 20), (80, 30, 28), (108, 44, 36), (136, 62, 48)]),
    COPPER=np.array([(30, 70, 62), (46, 100, 86), (70, 134, 110), (100, 168, 138)]),
    HORN=np.array([(16, 14, 18), (30, 26, 32), (48, 42, 48), (68, 60, 66)]),
    SMOKE=np.array([(60, 60, 64), (82, 82, 86), (106, 106, 110), (132, 132, 136)]),
    DARK=np.array([(8, 8, 10), (16, 15, 18), (26, 25, 28), (38, 36, 40)]),
    ASHD=np.array([(54, 50, 50), (74, 70, 68), (98, 92, 88), (124, 118, 112)]),
    CLAW=np.array([(40, 36, 36), (70, 64, 60), (104, 96, 88), (140, 130, 116)]),
)

CELL_OFFS = [(a, b, c) for a in (-1, 0, 1) for b in (-1, 0, 1) for c in (-1, 0, 1)]


def cellular(P, scale, seed=0):
    """3 次元の細胞状ノイズ。戻り値 (F1, F2) (どちらも 1 つの細胞の大きさ = 1 に正規化した距離)"""
    q = P * scale
    i = np.floor(q)
    f1 = np.full(q.shape[:-1], 9.0)
    f2 = np.full(q.shape[:-1], 9.0)
    for off in CELL_OFFS:
        cell = i + np.array(off)
        jit = np.stack([hash3(cell, seed + 1), hash3(cell + 17.3, seed + 2), hash3(cell + 41.9, seed + 3)], -1)
        d = np.linalg.norm(cell + 0.15 + 0.7 * jit - q, axis=-1)
        n1 = d < f1
        f2 = np.where(n1, f1, np.minimum(f2, d))
        f1 = np.where(n1, d, f1)
    return f1, f2


def paint(faces, size, P, N, M, L, ctx):
    D, K, LU, LV, CW, CH, INS = edge_fields(faces, size)
    H = W = size
    yy, xx = np.mgrid[0:H, 0:W]
    fi = np.where(M >= 0, M, 0)
    owned = M >= 0
    mats = np.array([f.mat for f in faces])[fi]
    bay = BAYER[yy % 4, xx % 4] - 0.5
    lit = N @ LIGHT
    edge = (D < 1.05) & INS
    convex = edge & (K > 24)
    concave = edge & (K < -24)
    X, Y, Z = P[..., 0], P[..., 1], P[..., 2]
    n_lo = vnoise(P * 0.07 + 3, 1)
    n_mid = vnoise(P * 0.22 + 11, 2)
    n_hi = vnoise(P * 0.8 + 5, 7)
    col = np.zeros((H, W, 3))
    glow = np.zeros((H, W), bool)
    c = ctx
    tex = lpkit.TEX

    def put(mask, cc):
        nonlocal col
        col = np.where(mask[..., None], cc, col)

    def shaded(pal, base, lw=0.30, nw=0.14, extra=0.0, ed=True):
        ns = len(pal)
        nz = c.get('nz', 1.0)
        v = base + lw * lit + nz * nw * (n_lo - 0.5) + nz * 0.10 * (n_mid - 0.5) + 0.07 * bay + extra
        idx = np.floor(v * ns).astype(int)
        if ed:
            idx = idx + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave
        return idx

    # 割れ目 (溶岩) の場: 細胞状ノイズの F2-F1 が小さい所
    cr_ok = np.isin(mats, c.get("crack_mats", ["rock", "plate", "cone", "skin"]))
    heat = c["heat"](X, Y, Z, N) if "heat" in c else np.full(X.shape, 0.5)
    crack = np.zeros(X.shape, bool)
    crack_edge = np.zeros(X.shape, bool)
    if c.get("crack_w", 0) > 0:
        idxs = np.nonzero(owned & cr_ok)
        F1 = np.full(X.shape, 9.0)
        F2 = np.full(X.shape, 9.0)
        cell = c.get("crack_cell", 14.0) * (1.0 if tex >= 2 else 1.35 if tex >= 1 else 1.9)    # 遠いほど割れ目を粗く
        f1, f2 = cellular(P[idxs], 1.0 / cell, 5)
        F1[idxs], F2[idxs] = f1, f2
        wcell = (F2 - F1) * cell * tex          # テクセル単位の縁までの距離
        wmax = c["crack_w"] * (0.5 + 0.5 * tex / 2.0) * (0.25 + 1.2 * heat)
        crack = (wcell < wmax) & cr_ok & (heat > 0.18)
        crack_edge = (wcell < wmax + 0.9) & ~crack & cr_ok & (heat > 0.25)

    # 岩 (玄武岩)
    rp = c.get("rock", PAL["ROCK"])
    up = N[..., 1]
    ridx = shaded(rp, c.get("rock_base", 0.46), c.get("rock_lw", 0.30)) - (n_hi > 0.80) + (hash1(xx * 7 + yy * 13 + fi * 3, 5) > 0.97)
    rc = pick(rp, ridx)
    put(mats == "rock", rc)
    pp = c.get("plate", PAL["ROCKW"])
    put(mats == "plate", pick(pp, shaded(pp, 0.52, 0.34, 0.12) - (n_hi > 0.8)))
    sp = c.get("skin", PAL["SKIN"])
    skc = pick(sp, shaded(sp, 0.50, 0.26, 0.07, ed=False) + (n_hi > 0.80) - (n_hi < 0.12))
    # 皮のしわ (腹から背へ向かう細い筋)
    wr = (np.abs(((Y * 0.55 + 0.9 * (X * 0.04) + 2.2 * (n_lo - 0.5)) % 1.0) - 0.5) < 0.06)
    skc = np.where(wr[..., None], pick(sp, shaded(sp, 0.50, 0.26, 0.18, ed=False) - 2), skc)
    put(mats == "skin", skc)
    put(mats == "belly", pick(c.get("belly", PAL["BELLY"]), shaded(c.get("belly", PAL["BELLY"]), 0.5, 0.28, 0.14)
                              - (np.abs(((Z * 0.33) % 1.0) - 0.5) < 0.08)))
    put(mats == "ash", pick(PAL["ASH"], shaded(PAL["ASH"], 0.55, 0.3, 0.14)))
    put(mats == "tooth", pick(PAL["TOOTH"], shaded(PAL["TOOTH"], 0.5, 0.36, 0.1)))
    put(mats == "mouth", pick(PAL["MOUTH"], shaded(PAL["MOUTH"], 0.45, 0.2, 0.2, ed=False)))
    put(mats == "tongue", pick(PAL["MOUTH"], shaded(PAL["MOUTH"], 0.7, 0.2, 0.2, ed=False)))
    put(mats == "horn", pick(PAL["HORN"], shaded(PAL["HORN"], 0.55, 0.3, 0.1)))
    put(mats == "claw", pick(PAL["CLAW"], shaded(PAL["CLAW"], 0.5, 0.34, 0.1)))
    put(mats == "brick", pick(PAL["BRICK"], shaded(PAL["BRICK"], 0.5, 0.3, 0.14)
                              - ((((X * 0.3 + 0.5 * np.floor(Y * 0.3)) % 1.0) < 0.1) | ((Y * 0.3) % 1.0 < 0.1))))
    put(mats == "copper", pick(PAL["COPPER"], shaded(PAL["COPPER"], 0.5, 0.3, 0.16)))
    put(mats == "smoke", pick(PAL["SMOKE"], shaded(PAL["SMOKE"], 0.55, 0.3, 0.12, ed=False)))
    put(mats == "dark", pick(PAL["DARK"], np.floor(1.2 + 1.2 * (0.5 + 0.5 * lit) + 0.8 * bay).astype(int)))
    # 火口の縁・煙突の口の赤茶
    put(mats == "rimcrust", pick(PAL["COOL"], shaded(PAL["COOL"], 0.55, 0.3, 0.2, ed=False)))

    # 火山の斜面: 岩に、縦に走る溶岩の筋
    cone = mats == "cone"
    cp = c.get("cone", PAL["ROCK"])
    put(cone, pick(cp, shaded(cp, 0.46) - (n_hi > 0.8)))
    ccx, ccz = c.get("cone_c", (0.0, -46.0))
    ang = np.arctan2(X - ccx, Z - ccz)
    nstr = c.get("streaks", 7)
    ph = (ang / (2 * math.pi) * nstr + 0.35 * (n_mid - 0.5) * 2 + 0.18 * np.sin(Y * 0.09 + ang * 3)) % 1.0
    st_w = c.get("streak_w", 0.07) * (0.35 + 1.2 * np.clip((Y - 60) / 70.0, 0, 1))
    streak = cone & (np.abs(ph - 0.5) < st_w) & (hash1(np.floor(Y * 0.12) + np.floor(ang * 6.0) * 9, 3) > 0.25)
    # 頂の灰の帽子
    capm = cone & (Y > c.get("ash_cap_y", 120.0) + 4 * (n_mid - 0.5))
    put(capm, pick(PAL["ASH"], shaded(PAL["ASH"], 0.52, 0.3, 0.14)))
    streak &= ~capm
    # 灰の積もり (上向きの面に、粒)
    ashd = c.get("ash_dust", 0.0)
    if ashd > 0:
        dust = np.isin(mats, ["rock", "plate", "cone", "skin"]) & (up > 0.5) & (n_hi + 0.15 * n_mid > 0.84 - ashd)
        dpal = PAL["ASHD"]
        put(dust, pick(dpal, np.floor((0.40 + 0.30 * lit + 0.35 * (n_mid - 0.5) + 0.14 * bay) * len(dpal)).astype(int)))
    # 殻 (皮の上に広がる岩)
    if c.get("crust_hook"):
        col = c["crust_hook"](col, dict(Y=Y, X=X, Z=Z, N=N, n_mid=n_mid, n_lo=n_lo, n_hi=n_hi, bay=bay, pick=pick,
                                        shaded=shaded, mats=mats, convex=convex, concave=concave, lit=lit, put=put,
                                        PAL=PAL))

    # 溶岩 (光る)
    lv = np.clip(0.50 + 0.22 * (n_mid - 0.5) * 2 + 0.2 * bay + 0.15 * heat, 0, 0.999)
    lava_m = (mats == "lava") | streak | crack
    put(lava_m, ramp(PAL["LAVA"], lv))
    glow |= lava_m
    # 割れ目の縁の冷えた赤
    put(crack_edge, pick(PAL["COOL"], np.floor(1.5 + 1.5 * heat + 0.8 * bay).astype(int)))
    # 面と面の継ぎ目の溶岩 (凹んだ縁)
    sp_ = c.get("seam_p", 0.0)
    if sp_ > 0:
        seam_ok = np.isin(mats, c.get("seam_mats", ["rock", "plate"])) & concave & (hash1(xx * 3 + yy * 5, 2) < sp_ * (0.3 + 1.1 * heat))
        put(seam_ok, ramp(PAL["LAVA"], 0.30 + 0.30 * hash1(xx + yy * 7, 4)))
        glow |= seam_ok
    # 目と縁
    ey = mats == "eye"
    ex = c.get("eye_z", -176.0)
    pupil = ey & (np.abs(Z - ex) < c.get("pupil_w", 0.7))
    put(ey, ramp(PAL["LAVA"], 0.55 + 0.25 * (0.5 + 0.5 * lit) + 0.1 * bay))
    put(pupil, pick(PAL["DARK"], np.floor(0.6 + 0.8 * (0.5 + bay)).astype(int)))
    glow |= ey & ~pupil
    rim = mats == "rim"
    put(rim, ramp(PAL["LAVA"], 0.35 + 0.35 * (0.5 + 0.5 * lit) + 0.15 * bay))
    glow |= rim
    ht = mats == "hornglow"
    put(ht, ramp(PAL["LAVA"], 0.7 + 0.28 * bay))
    glow |= ht
    gl = mats == "ruinglow"
    put(gl, ramp(PAL["LAVA"], 0.45 + 0.3 * hash1(LU + LV * 3, 2)))
    glow |= gl

    hook = c.get("hook")
    if hook:
        col, glow = hook(col, glow, dict(P=P, N=N, mats=mats, lit=lit, bay=bay, n_lo=n_lo, n_mid=n_mid, n_hi=n_hi,
                                         X=X, Y=Y, Z=Z, convex=convex, concave=concave, D=D, INS=INS, LU=LU, LV=LV,
                                         CW=CW, CH=CH, fi=fi, K=K, pick=pick, ramp=ramp, shaded=shaded, edge=edge,
                                         put=put, heat=heat))
    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl_ = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl_ & owned


# ------------------------------------------------------------------ 出力

INSET = 0.02


def export_mesh(faces, size, path, name, texture, glowfile, bones, tex):
    bd = {b: {"vertices": [], "uvs": [], "triangles": []} for b in bones}
    for f in faces:
        b = bd[f.bone]
        base = len(b["vertices"])
        cen = f.uv.mean(0)
        for p, uv in zip(f.verts, f.uv):
            d = cen - uv
            nn = np.linalg.norm(d)
            uv = uv + d / nn * min(INSET, nn * 0.4) if nn > 1e-9 else uv
            b["vertices"].append([round(float(x), 3) for x in p])
            b["uvs"].append([round(float(uv[0]) / size, 5), round(float(uv[1]) / size, 5)])
        for a, c, d in f.tris():
            b["triangles"].append([base + a, base + c, base + d])
    tot = 0
    out = {}
    for k, (parent, pivot, rot) in bones.items():
        b = bd[k]
        b["triangle_count"] = len(b["triangles"])
        tot += b["triangle_count"]
        e = {"parent": parent, "pivot": pivot}
        if any(rot):
            e["rotation"] = rot
        e.update(b)
        out[k] = e
    data = {"format_version": 1, "name": name,
            "units": "1/16 block, y up, front = -Z (absolute model-space vertices, like geo cubes; no bone has a rest rotation; the entity renders at scale 8)",
            "uv": "normalized, origin top-left (v down)", "texture": texture, "glowmask": glowfile,
            "texture_size": [size, size], "texels_per_unit": tex, "uv_inset_texels": INSET, "triangle_count": tot,
            "bones": out}
    Path(path).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data


def build_lod(opt, lod, outdir=None, save=True, tex=None):
    bones = bone_defs(GEOS[lod])
    tex = tex or opt["tex"][lod]
    lpkit.TEX = tex
    faces = [f for f in opt["build"](lod) if f is not None]
    for f in faces:
        assert f.bone in bones, f.bone
    size, P, N, M, L = build_atlas2(faces, 256, 0)
    atlas, glow = paint(faces, size, P, N, M, L, opt["ctx"])
    data = None
    tag = ("near", "mid", "far")[lod]
    if save:
        d = Path(outdir)
        d.mkdir(parents=True, exist_ok=True)
        Image.fromarray(atlas, "RGBA").save(d / f"{tag}_atlas.png")
        gm = np.zeros((size, size, 4), np.uint8)
        gm[glow] = (255, 255, 255, 255)
        Image.fromarray(gm, "RGBA").save(d / f"{tag}_glowmask.png")
        data = export_mesh(faces, size, d / f"{tag}_mesh.json", f"ember_titan_{tag}_lowpoly",
                           f"{tag}_atlas.png", f"{tag}_glowmask.png", bones, tex)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, data=data, bones=bones, tris=tri_count(faces), tex=tex,
                by_bone={b: tri_count([f for f in faces if f.bone == b]) for b in sorted({f.bone for f in faces})})
