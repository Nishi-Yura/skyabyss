"""岩鯨 (stone_whale) 低ポリ案の共通部品。ラフ。src には触らない。

座標は 1 単位 = 1/16 ブロック (ゲーム内では x8 で 0.5 ブロック)、y が上、正面は -Z。
骨の名前・階層・pivot は src/.../geo/entity/stone_whale.geo.json から読む (休めの回転なし)。
メッシュの頂点はモデル全体の絶対座標 (geo の cube と同じ。+X がゲームでは鯨の左)。
LOD (詳細度): 0 = 近 (<64 ブロック), 1 = 中 (<160), 2 = 遠。
"""
from __future__ import annotations

import json
import math
import sys
from dataclasses import dataclass
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
from bone_sentinel_lp import hash1, vnoise, BAYER  # noqa: E402
import voxel  # noqa: E402

GEO = ROOT / "src/main/resources/assets/skyabyss/geo/entity"
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
OUTDIR = ROOT / "docs/review/lowpoly_roughs/lp_stone_whale"
SUBJ = "stone_whale"

YC = 40.0
MOUTH = 34.0
KEYS = [(-132, 40, 34), (-110, 52, 44), (-80, 56, 50), (-40, 58, 52), (-5, 56, 50),
        (40, 50, 44), (80, 34, 30), (114, 20, 18), (138, 9, 8)]


def V(*a):
    return np.array(a, float)


def smooth(a, b, x):
    t = min(max((x - a) / (b - a), 0.0), 1.0)
    return t * t * (3 - 2 * t)


# ------------------------------------------------------------------ 骨 (今の geo から)

def load_geo(name=SUBJ):
    return json.loads((GEO / f"{name}.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]


def bone_defs():
    g = load_geo()
    return {b["name"]: (b.get("parent"), [float(x) for x in b["pivot"]], [float(x) for x in b.get("rotation", [0, 0, 0])])
            for b in g["bones"]}


def box_count(name):
    return sum(len(b.get("cubes", [])) for b in load_geo(name)["bones"])


# ------------------------------------------------------------------ 胴の断面

def prof(z):
    for (z0, w0, h0), (z1, w1, h1) in zip(KEYS, KEYS[1:]):
        if z0 <= z <= z1:
            t = (z - z0) / (z1 - z0)
            t = t * t * (3 - 2 * t)
            return w0 + (w1 - w0) * t, h0 + (h1 - h0) * t
    return (KEYS[0][1], KEYS[0][2]) if z < KEYS[0][0] else (KEYS[-1][1], KEYS[-1][2])


def dims(z, nose=1.0):
    W, H = prof(z)
    f = 1.0
    if z < -124:
        d = max(0.0, (z + 132) / 8.0)
        f = math.sqrt(max(0.0, 1 - (1 - d) ** 2)) ** nose
    return W * f, H * f


@dataclass
class Spec:
    """胴の断面の作り方。levels: [(t, 幅の係数 or None)...] t は -1(腹)..1(背)、"L" は口の線の高さ"""
    levels: list
    p_top: float = 2.0
    p_bot: float = 2.2
    jit: float = 0.03          # 頂点の幅のゆらぎ (割合)
    jit_y: float = 0.5
    hook: object = None        # hook(z, t, x, y, i) -> (x, y)
    jaw_inset: float = 0.94
    nose: float = 1.0


def lip_t(z):
    W, H = prof(z)
    return max(-0.5, min(-0.05, (MOUTH - YC) / (max(H, 1e-6) / 2)))


def make_ring(z, spec: Spec):
    W, H = dims(z, spec.nose)
    n = len(spec.levels)
    amp = min(1.0, W / 28.0)
    out = np.zeros((2 * n + 2, 3))
    zi = int(round(z * 5))

    def hw(t, p):
        return W / 2 * max(0.0, 1 - abs(t) ** p) ** (1 / p)

    out[0] = (0, YC - H / 2, z)
    out[n + 1] = (0, YC + H / 2, z)
    if spec.hook:
        x_, y_ = spec.hook(z, 1.0, 0.0, YC + H / 2, -2)
        out[n + 1] = (x_, y_, z)
    for i, (t, f) in enumerate(spec.levels):
        if t == "L":
            t = lip_t(z)
        p = spec.p_top if t >= 0 else spec.p_bot
        j = spec.jit * (hash1(zi * 3 + i * 131, 3) - 0.5) * 2 * amp
        x = hw(t, p) * (1.0 if f is None else f) * (1 + j)
        y = YC + t * H / 2 + spec.jit_y * j * H * 0.3
        if spec.hook:
            x, y = spec.hook(z, t, x, y, i)
        out[1 + i] = (x, y, z)
        out[2 * n + 1 - i] = (-x, y, z)
    return out


def lvi(k, n):
    if k == 0:
        return -1
    if k <= n:
        return k - 1
    if k == n + 1:
        return n
    return 2 * n + 1 - k


class Hull:
    def __init__(self, spec: Spec, stations):
        self.spec = spec
        self.st = list(stations)
        self.rings = [make_ring(z, spec) for z in self.st]
        self.n = len(spec.levels)
        self.iL = [i for i, (t, f) in enumerate(spec.levels) if t == "L"][0]

    def ring_at(self, z):
        st = self.st
        z = min(max(z, st[0]), st[-1])
        for i in range(len(st) - 1):
            if st[i] <= z <= st[i + 1]:
                t = (z - st[i]) / max(st[i + 1] - st[i], 1e-9)
                return self.rings[i] * (1 - t) + self.rings[i + 1] * t, i, t
        return self.rings[-1], len(st) - 2, 1.0

    def at(self, z, y, side=1):
        """体の表面の点と外向きの法線 (側面。side=+1 は +X 側)"""
        r, i, t = self.ring_at(z)
        n = self.n
        half = [r[k] for k in range(0, n + 2)]     # 腹 → 背 (+X 側)
        pt, e = None, None
        for a, b in zip(half, half[1:]):
            if a[1] <= y <= b[1] and b[1] - a[1] > 1e-6:
                u = (y - a[1]) / (b[1] - a[1])
                pt = a + (b - a) * u
                e = b - a
                break
        if pt is None:
            pt, e = half[n // 2], V(0, 1, 0)
        i0 = min(max(i, 0), len(self.st) - 2)
        k_near = int(np.argmin([abs(h[1] - y) for h in half]))
        dz = (self.rings[i0 + 1][k_near] - self.rings[i0][k_near])
        nn = np.cross(e, dz)
        if np.linalg.norm(nn) < 1e-9:
            nn = V(1, 0, 0)
        nn = nrm(nn)
        if nn[0] < 0:
            nn = -nn
        pt = V(pt[0], pt[1], z)
        if side < 0:
            pt[0] = -pt[0]
            nn = V(-nn[0], nn[1], nn[2])
        return pt, nn

    def top(self, z):
        r, _, _ = self.ring_at(z)
        return r[self.n + 1][1]


def bone_of(cz, lo, hi, iL):
    if cz < -66 and hi <= iL:
        return "jaw"
    if cz < -60:
        return "head"
    if cz < 60:
        return "body"
    return "tail"


def jaw_pt(p, k, n, iL, spec):
    if lvi(k, n) == iL:
        return V(p[0] * spec.jaw_inset, p[1] - 0.7, p[2])
    return p


def hull_faces(h: Hull, matfn=None, mouth_inner=True, seam=True):
    """胴・頭・顎・尾の面。口の線 (頭の上唇のひさしと顎の上端) と口の中を作る。"""
    n, iL = h.n, h.iL
    m = 2 * n + 2
    faces = []
    for a in range(len(h.st) - 1):
        r0, r1 = h.rings[a], h.rings[a + 1]
        cz = (h.st[a] + h.st[a + 1]) / 2
        cen = (r0.mean(0) + r1.mean(0)) / 2
        for k in range(m):
            k2 = (k + 1) % m
            l1, l2 = lvi(k, n), lvi(k2, n)
            lo, hi = min(l1, l2), max(l1, l2)
            bone = bone_of(cz, lo, hi, iL)
            P = [r0[k], r0[k2], r1[k2], r1[k]]
            ks = [k, k2, k2, k]
            if bone == "jaw":
                P = [jaw_pt(p, kk, n, iL, h.spec) for p, kk in zip(P, ks)]
            mat = matfn(cz, lo, hi, bone, h) if matfn else "stone"
            f = _mk(P, bone, mat, cen)
            if f is not None:
                faces.append(f)
    if seam:
        for a in range(len(h.st) - 1):
            cz = (h.st[a] + h.st[a + 1]) / 2
            if not (-132 < cz < -66):
                continue
            r0, r1 = h.rings[a], h.rings[a + 1]
            for sk in (1 + iL, 2 * n + 1 - iL):
                A0, A1 = r0[sk], r1[sk]
                B0, B1 = jaw_pt(r0[sk], sk, n, iL, h.spec), jaw_pt(r1[sk], sk, n, iL, h.spec)
                f = _mk([A0, A1, B1, B0], "head", "dark", (A0 + A1 + B0 + B1) / 4 + V(0, 5, 0))
                if f is not None:
                    faces.append(f)
            if mouth_inner:
                xr0, xr1 = r0[1 + iL][0] * 0.9, r1[1 + iL][0] * 0.9
                y0, y1 = r0[1 + iL][1] - 0.2, r1[1 + iL][1] - 0.2
                za, zb = r0[0][2], r1[0][2]
                f = _mk([V(xr0, y0, za), V(-xr0, y0, za), V(-xr1, y1, zb), V(xr1, y1, zb)],
                        "head", "mouth", V(0, y0 + 5, (za + zb) / 2))
                if f is not None:
                    faces.append(f)
                yj0, yj1 = y0 - 1.6, y1 - 1.6
                f = _mk([V(xr0 * .95, yj0, za), V(-xr0 * .95, yj0, za), V(-xr1 * .95, yj1, zb), V(xr1 * .95, yj1, zb)],
                        "jaw", "tongue", V(0, yj0 - 5, (za + zb) / 2))
                if f is not None:
                    faces.append(f)
    return faces


# ------------------------------------------------------------------ 部品

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


def column(bone, mat, base, h, r, sides=6, taper=0.9, top_mat=None, cap=True, rings=1):
    base = V(*base)
    path = [base + V(0, h * i / rings, 0) for i in range(rings + 1)]
    ras = [r * (1 - (1 - taper) * i / rings) for i in range(rings + 1)]
    fs = tube(bone, mat, path, ras, sides=sides, hint=(1, 0, 0), phase=360.0 / sides / 2, caps=(False, cap))
    if top_mat:
        for f in fs:
            if f.normal[1] > 0.9:
                f.mat = top_mat
    return fs


def plinth(bone, mat, c, nrm_, up, rx, ry, h, shrink=0.8, sides=8, top_mat=None, embed=0.9, rot=0.0):
    """面に貼る平たい多角錐台 (まぶた・目)。c は面上の中心、nrm_ は外向き法線。rx: 前後方向, ry: 上下方向"""
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


def jit3(p, amp, tag):
    p = np.asarray(p, float)
    return p + V(hash1(tag * 3 + p[0] * 1.3 + p[1], 1) - .5, hash1(tag * 5 + p[1] * 1.7 + p[2], 2) - .5,
                 hash1(tag * 7 + p[2] * 1.1 + p[0], 3) - .5) * 2 * amp


# ------------------------------------------------------------------ ひれ・尾びれ

def fin_left(spec, bone="fin_left"):
    """胸びれ (+X 側)。根は体に埋まり、外へ行くほど細く、下がる。"""
    nr = spec.get("rings", 5)
    L, x0, yf = spec.get("L", 42), spec.get("x0", 24.0), spec.get("y", YC - 6.0)
    th0, th1 = spec.get("th", (5.0, 0.9))
    zl0, zt0, ztip = spec.get("z", (-58.0, -39.0, -31.0))
    sweep = spec.get("sweep", 1.35)
    drop = spec.get("drop", 0.3)
    rings = []
    for i in range(nr):
        e = L * i / (nr - 1)
        s = e / L
        zl = zl0 + (ztip - zl0) * s ** sweep
        zt = zt0 + (ztip - zt0) * s ** 0.85
        if i == nr - 1:
            zt = zl + 0.6
        th = th0 + (th1 - th0) * s ** 0.9
        yc = yf - drop * e
        x = x0 + e
        zm = zl + (zt - zl) * 0.42
        ring = [V(x, yc, zl), V(x, yc + th / 2, zm), V(x, yc + th * 0.15, zt), V(x, yc - th / 2, zm)]
        if spec.get("scallop") and 0 < i < nr - 1:
            ring[2] = V(x, yc + th * 0.15, zt - spec["scallop"] * (1 if i % 2 else -0.4))
        rings.append(ring)
    return loft(bone, spec.get("mat", "fin"), rings, caps=(False, True))


def fluke(spec, bone="fluke"):
    xs = spec.get("xs", [-58, -48, -37, -26, -15, -7, 0, 7, 15, 26, 37, 48, 58])
    yc0 = spec.get("y", YC - 1.0)
    rings = []
    for x in xs:
        ax = abs(x)
        za = 136.0 + 0.15 * ax
        zb = (146 + 0.28 * ax) if ax >= 4 else (141 + 0.5 * ax)
        th = max(1.0, (3.4 - 0.04 * ax) * spec.get("thick", 1.3))
        yc = yc0 + spec.get("dihedral", 0.035) * ax
        c = zb - za
        notch = spec.get("notch", 0.0) * (hash1(int(ax * 7), 4) - 0.5)
        rings.append([V(x, yc, za), V(x, yc + th / 2, za + c * 0.38), V(x, yc + th * 0.3, za + c * 0.75),
                      V(x, yc, zb + notch), V(x, yc - th * 0.3, za + c * 0.75), V(x, yc - th / 2, za + c * 0.38)])
    return loft(bone, spec.get("mat", "fin"), rings, caps=(True, True))


# ------------------------------------------------------------------ 絵

LIGHT = np.array([-0.35, 0.85, -0.4])
LIGHT = LIGHT / np.linalg.norm(LIGHT)

PAL = dict(
    STONE=np.array([(46, 50, 62), (60, 64, 76), (74, 78, 90), (90, 94, 106), (108, 112, 122), (128, 132, 140)]),
    MOSS=np.array([(28, 44, 30), (42, 64, 36), (60, 88, 44), (80, 110, 56)]),
    BELLY=np.array([(118, 110, 98), (146, 138, 122), (170, 162, 144), (192, 184, 164), (212, 204, 184)]),
    RUIN=np.array([(46, 44, 54), (64, 60, 72), (84, 80, 92), (104, 100, 110), (124, 118, 126)]),
    FIN=np.array([(36, 40, 50), (50, 55, 67), (64, 70, 82), (80, 86, 98), (98, 104, 116)]),
    BONE=np.array([(118, 108, 92), (150, 140, 120), (178, 170, 148), (206, 198, 174), (230, 224, 202)]),
    IRON=np.array([(34, 36, 44), (52, 54, 62), (72, 74, 84), (94, 96, 106)]),
    CRUST=np.array([(104, 96, 80), (132, 122, 102), (156, 146, 122), (188, 178, 148)]),
    EYE=np.array([(196, 118, 34), (236, 164, 54), (255, 198, 96), (255, 228, 156)]),
    CYAN=np.array([(40, 150, 184), (84, 214, 244), (150, 244, 255), (214, 252, 255)]),
    DARK=np.array([(10, 10, 16), (20, 20, 28), (32, 32, 42), (44, 44, 56)]),
    MOUTH=np.array([(44, 18, 22), (66, 28, 30), (90, 40, 40), (116, 56, 52)]),
    LIP=np.array([(30, 32, 42), (44, 46, 58), (58, 60, 74), (74, 78, 92)]),
    BAL=np.array([(170, 164, 140), (198, 192, 168), (224, 218, 194), (240, 236, 214)]),
)


def ramp(pal, v):
    pal = np.asarray(pal)
    return pal[np.clip(np.floor(v * len(pal)).astype(int), 0, len(pal) - 1)].astype(float)


def pick(pal, idx):
    pal = np.asarray(pal)
    return pal[np.clip(idx, 0, len(pal) - 1)].astype(float)


def rune(LU, LV, CW, CH, D, INS, fi, p):
    """面の島の中に、3x5 の彫り込みの文字 (5x7 の格子に 1 つ)"""
    cu, cv = LU // 5, LV // 7
    au, av = LU % 5, LV % 7
    cell = cu * 31 + cv * 17 + fi * 5
    on = (hash1(cell, 11) < p) & (au >= 1) & (au <= 3) & (av >= 1) & (av <= 5)
    bit = hash1(cell * 15 + (au % 3) * 5 + av, 13) < 0.58
    ok = (CW >= 8) & (CH >= 8) & (D > 1.3) & INS & ((cu + 1) * 5 <= CW) & ((cv + 1) * 7 <= CH)
    return on & bit & ok


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
    area = (CW * CH).astype(float)
    X, Y, Z = P[..., 0], P[..., 1], P[..., 2]
    n_lo = vnoise(P * 0.07 + 3, 1)
    n_mid = vnoise(P * 0.22 + 11, 2)
    n_hi = vnoise(P * 0.8 + 5, 7)
    col = np.zeros((H, W, 3))
    glow = np.zeros((H, W), bool)

    def put(mask, c):
        nonlocal col
        col = np.where(mask[..., None], c, col)

    def shaded(pal, base, lw=0.30, nw=0.14, extra=0.0, ed=True):
        ns = len(pal)
        v = base + lw * lit + nw * (n_lo - 0.5) + 0.10 * (n_mid - 0.5) + 0.07 * bay + extra
        idx = np.floor(v * ns).astype(int)
        if ed:
            idx = idx + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave
        return idx

    c = ctx
    st_pal = c.get("stone", PAL["STONE"])
    sw = c.get("strata_w", 0.3)
    sy = c.get("strata_y", 0.33)
    sband = np.floor(Y * sy + 2.2 * (n_lo - 0.5) + 0.6 * (n_mid - 0.5))
    stone_shift = (hash1(sband, 5) - 0.5) * sw
    pit = hash1(xx * 7 + yy * 13 + fi * 3, 5)
    base = c.get("stone_base", 0.50)
    idx = shaded(st_pal, base + stone_shift) - (pit < 0.05) - (n_hi > 0.78)
    sc = pick(st_pal, idx)
    seam = (np.abs(((Y * sy) % 1.0) - 0.5) < c.get("seam_w", 0.05)) & (hash1(sband, 8) < 0.55)
    sc = np.where(seam[..., None], pick(st_pal, idx - 1), sc)
    put(mats == "stone", sc)
    pl_pal = c.get("plate", PAL["STONE"])
    put(mats == "plate", pick(pl_pal, shaded(pl_pal, 0.62, 0.34, 0.10) - (n_hi > 0.8)))
    skin_pal = c.get("skin", PAL["STONE"])
    put(mats == "skin", pick(skin_pal, shaded(skin_pal, 0.5, 0.26, 0.2) - (n_hi > 0.82) + (pit > 0.96)))
    mo = c.get("moss", 0.0)
    if mo > 0:
        up = N[..., 1]
        score = 1.0 * n_mid + 0.45 * n_lo + 0.40 * up + 0.25 * (concave & (up > -0.2))
        m_all = np.isin(mats, c.get("moss_mats", ["stone", "plate", "ruin", "skin"])) & (score > (1.50 - mo)) & (up > 0.15 + c.get("moss_up", 0.0))
        mv = 0.5 + 0.25 * lit + 0.20 * (n_mid - 0.5) + 0.12 * bay
        mp = PAL["MOSS"]
        midx = np.floor(mv * len(mp)).astype(int) + (convex & (lit > 0)) - concave
        put(m_all, pick(mp, midx))
    bpal = c.get("belly", PAL["BELLY"])
    bi = shaded(bpal, 0.5, 0.28, 0.12)
    bcol = pick(bpal, bi)
    gro = c.get("belly_lines", 0.0)
    if gro > 0:
        line = ((np.abs(X) * 0.33) % 1.0 < gro) & (np.abs(X) < 12)
        bcol = np.where(line[..., None], pick(bpal, bi - 2), bcol)
    put(mats == "belly", bcol)
    put(mats == "lip", pick(PAL["LIP"], shaded(PAL["LIP"], 0.5, 0.30, 0.12)))
    put(mats == "dark", pick(PAL["DARK"], np.floor(1.2 + 1.2 * (0.5 + 0.5 * lit) + 0.8 * bay).astype(int)))
    put(mats == "mouth", pick(PAL["MOUTH"], shaded(PAL["MOUTH"], 0.45, 0.2, 0.2, ed=False)))
    put(mats == "tongue", pick(PAL["MOUTH"], shaded(PAL["MOUTH"], 0.65, 0.2, 0.2, ed=False)))
    lid = c.get("lid", PAL["LIP"])
    put(mats == "lid", pick(lid, shaded(lid, 0.55, 0.3, 0.1)))
    put(mats == "brow", pick(c.get("brow", PAL["LIP"]), shaded(c.get("brow", PAL["LIP"]), 0.42, 0.3, 0.1)))
    ey = mats == "eye"
    ex = c.get("eye_z", -111.0)
    pupil = ey & (np.abs(Z - ex) < c.get("pupil_w", 0.6))
    ev = 0.40 + 0.22 * (0.5 + 0.5 * lit) + 0.25 * (1 - np.clip(np.abs(Z - ex) / 3.0, 0, 1)) + 0.08 * bay
    put(ey, ramp(PAL["EYE"], ev))
    put(pupil, pick(PAL["DARK"], np.floor(0.6 + 0.8 * (0.5 + bay)).astype(int)))
    glow |= ey & ~pupil
    fin = np.isin(mats, ["fin"])
    fp = c.get("fin", PAL["FIN"])
    fidx = shaded(fp, 0.5, 0.34, 0.1)
    rays = (np.abs(((Z + 0.55 * np.abs(X)) * c.get("ray_f", 0.42)) % 1.0 - 0.5) < c.get("ray_w", 0.09)) & (np.abs(N[..., 1]) > 0.3)
    put(fin, pick(fp, fidx - rays))
    ru = mats == "ruin"
    rp = c.get("ruin", PAL["RUIN"])
    ridx = shaded(rp, 0.52, 0.30, 0.12)
    mort = (((X * 0.34 + 0.25 * np.floor(Y * 0.34)) % 1.0 < 0.1) | ((Y * 0.34) % 1.0 < 0.1)) & (area > 10)
    put(ru, pick(rp, ridx - mort))
    rune_on = np.isin(mats, c.get("rune_mats", ["ruin"]))
    gl = rune(LU, LV, CW, CH, D, INS, fi, c.get("rune_p", 0.0)) & rune_on
    put(gl, ramp(PAL["CYAN"], 0.45 + 0.3 * hash1(LU + LV * 3, 2)))
    glow |= gl
    gy = mats == "glyph"
    put(gy, ramp(PAL["CYAN"], 0.35 + 0.45 * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.08 * bay))
    glow |= gy
    put(mats == "bone", pick(PAL["BONE"], shaded(PAL["BONE"], 0.55, 0.34, 0.1)))
    put(mats == "iron", pick(PAL["IRON"], shaded(PAL["IRON"], 0.5, 0.34, 0.1)))
    cr = c.get("crust", PAL["CRUST"])
    put(mats == "crust", pick(cr, shaded(cr, 0.55, 0.3, 0.14)))
    put(mats == "baleen", pick(PAL["BAL"], shaded(PAL["BAL"], 0.55, 0.3, 0.1)))
    hook = c.get("hook")
    if hook:
        col, glow = hook(col, glow, dict(P=P, N=N, mats=mats, lit=lit, bay=bay, n_lo=n_lo, n_mid=n_mid, n_hi=n_hi,
                                         X=X, Y=Y, Z=Z, convex=convex, concave=concave, D=D, INS=INS, LU=LU, LV=LV,
                                         CW=CW, CH=CH, fi=fi, area=area, K=K, pick=pick, ramp=ramp, shaded=shaded,
                                         edge=edge, put=put))
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
    bones = bone_defs()
    tex = tex or opt["tex"][lod]
    lpkit.TEX = tex
    faces = [f for f in opt["build"](lod) if f is not None]
    for f in faces:
        assert f.bone in bones, f.bone
    size, P, N, M, L = build_atlas(faces, 64, 0)
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
        data = export_mesh(faces, size, d / f"{tag}_mesh.json", f"stone_whale_{tag}_lowpoly",
                           f"{tag}_atlas.png", f"{tag}_glowmask.png", bones, tex)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, data=data, bones=bones, tris=tri_count(faces), tex=tex,
                by_bone={b: tri_count([f for f in faces if f.bone == b]) for b in sorted({f.bone for f in faces})})
