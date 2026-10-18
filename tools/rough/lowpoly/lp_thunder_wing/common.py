"""雷翼 (thunder_wing) 低ポリ案の共通部品。ラフ。src には触らない。

座標は 1 単位 = 1/16 ブロック (ゲーム内では x8 で 0.5 ブロック)、y が上、正面は -Z。
骨の名前・階層・pivot は src/.../geo/entity/thunder_wing.geo.json から読む (休めの回転なし)。
メッシュの頂点はモデル全体の絶対座標 (geo の cube と同じ。+X が wing_l / hand_l / leg_l 側)。
LOD (詳細度): 0 = 近 (<64 ブロック), 1 = 中 (<160), 2 = 遠。

薄い面 (羽根) は「ひし形断面の細い筒」にして、表と裏 (上は灰青、下は白) の両面が必ず出る閉じた形にする。
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
OUTDIR = ROOT / "docs/review/lowpoly_roughs/lp_thunder_wing"
SUBJ = "thunder_wing"


def V(*a):
    return np.array(a, float)


def lerp(a, b, t):
    return a + (b - a) * t


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


# ------------------------------------------------------------------ 輪切りの殻 (胴・首・頭)

class Shell:
    """z の駅ごとに (z, cy, hw, ht, hb[, p[, cx]]) を与え、n 角の輪をつなぐ。
    hw: 半幅、ht/hb: 中心から上/下の半分の高さ、p: 角の丸さ (2 = 楕円、大きいほど箱)。"""

    def __init__(self, bone, st, n=10, p=2.4):
        self.bone, self.n, self.p = bone, n, p
        self.st = [tuple(s) + (None,) * (7 - len(s)) for s in st]
        self.rings = [self.ring(s) for s in self.st]

    def params(self, z):
        st = self.st
        z = min(max(z, st[0][0]), st[-1][0])
        for a, b in zip(st, st[1:]):
            if a[0] <= z <= b[0]:
                t = (z - a[0]) / max(b[0] - a[0], 1e-9)
                t = t * t * (3 - 2 * t)
                pa = self.p if a[5] is None else a[5]
                pb = self.p if b[5] is None else b[5]
                return (lerp(a[1], b[1], t), lerp(a[2], b[2], t), lerp(a[3], b[3], t), lerp(a[4], b[4], t),
                        lerp(pa, pb, t), lerp(a[6] or 0, b[6] or 0, t))
        s = st[-1]
        return (s[1], s[2], s[3], s[4], self.p if s[5] is None else s[5], s[6] or 0)

    def ring(self, s):
        z, cy, hw, ht, hb, p, cx = s
        p = self.p if p is None else p
        cx = cx or 0.0
        out = []
        for k in range(self.n):
            a = 2 * math.pi * k / self.n
            sx, cs = math.sin(a), math.cos(a)
            x = hw * math.copysign(abs(sx) ** (2 / p), sx)
            y = cy + (ht if cs >= 0 else hb) * math.copysign(abs(cs) ** (2 / p), cs)
            out.append(V(cx + x, y, z))
        return np.array(out)

    def faces(self, matfn=None, caps=(True, True)):
        n = self.n
        faces = []
        for i in range(len(self.rings) - 1):
            r0, r1 = self.rings[i], self.rings[i + 1]
            zc = (self.st[i][0] + self.st[i + 1][0]) / 2
            cen = (r0.mean(0) + r1.mean(0)) / 2
            for k in range(n):
                k2 = (k + 1) % n
                am = 2 * math.pi * (k + 0.5) / n
                mat = matfn(zc, math.cos(am), math.sin(am)) if matfn else ("belly" if math.cos(am) < -0.35 else "back")
                f = _mk([r0[k], r0[k2], r1[k2], r1[k]], self.bone, mat, cen)
                if f is not None:
                    faces.append(f)
        for idx, cap in zip((0, len(self.rings) - 1), caps):
            if cap:
                r = self.rings[idx]
                other = self.rings[1].mean(0) if idx == 0 else self.rings[-2].mean(0)
                cm = cap if isinstance(cap, str) else "back"
                f = _mk(list(r), self.bone, cm, other)
                if f is not None:
                    faces.append(f)
        return faces

    def at(self, z, y, side=1):
        """表面の点と外向きの法線 (+X 側。side=-1 で -X)"""
        cy, hw, ht, hb, p, cx = self.params(z)
        h = ht if y >= cy else hb
        d = min(abs(y - cy) / max(h, 1e-6), 0.999)
        x = cx + hw * (1 - d ** p) ** (1 / p)

        def F(xx, yy, zz):
            c_, w_, t_, b_, p_, cx_ = self.params(zz)
            hh = t_ if yy >= c_ else b_
            return (abs((xx - cx_) / w_)) ** p_ + (abs((yy - c_) / max(hh, 1e-6))) ** p_

        e = 0.05
        g = V(F(x + e, y, z) - F(x - e, y, z), F(x, y + e, z) - F(x, y - e, z), F(x, y, z + e) - F(x, y, z - e))
        nn = nrm(g)
        if nn[0] < 0:
            nn = -nn
        pt = V(x, y, z)
        if side < 0:
            pt[0] = 2 * cx - pt[0]
            nn = V(-nn[0], nn[1], nn[2])
        return pt, nn

    def top(self, z):
        cy, hw, ht, hb, p, cx = self.params(z)
        return cy + ht


# ------------------------------------------------------------------ 羽根 (ひし形断面の細い筒。表裏とも出る)

def feather(bone, root, tip, w, th=0.7, top="sec", bot="belly", bend=(0, 0, 0), segs=1, tipw=0.0,
            hint=(0, 1, 0), cap=False, mid=1.0, tmat=None):
    """root から tip へ伸びる羽根。w: 根元の幅 (全幅)。tipw: 先の幅の割合 (0 で尖る)。
    mid: 途中 (中ほど) の幅の割合。bend: 先端へ向けた曲がり。"""
    root, tip = np.asarray(root, float), np.asarray(tip, float)
    path, rb = [], []
    for i in range(segs + 1):
        t = i / segs
        path.append(root + (tip - root) * t + np.asarray(bend, float) * t * t)
        wv = w / 2 * (1 + (mid - 1) * math.sin(math.pi * t) ** 0.7 if mid != 1.0 else 1.0) * (1 - (1 - tipw) * t ** 1.3)
        rb.append(max(0.0, wv))
    rb[-1] = w / 2 * tipw
    ra = [th / 2 * (1 - 0.8 * (i / segs)) if i < segs else (0.0 if tipw == 0 else th * 0.15) for i in range(segs + 1)]
    ra[0] = th / 2
    return tube(bone, top, path, ra, rb, sides=4, hint=hint, phase=0.0, caps=(False, cap),
                mats=[top, bot, bot, top] if tmat is None else tmat)


def scale(bone, root, tip, w, mat, th=0.9, **kw):
    """面にのせる羽根・鱗: 上の二面だけ (下は面に埋まって見えないので作らない)"""
    fs = feather(bone, root, tip, w, th=th, top=mat, bot="_hide", tmat=[mat, "_hide", "_hide", mat], **kw)
    return [f for f in fs if f.mat != "_hide"]


def bar(bone, mat, a, b, ra, rb=None, sides=4, hint=(0, 1, 0), phase=45.0, caps=(True, True), ta=1.0, tb=1.0):
    """a から b への角柱 (先を細くできる)。"""
    rb_ = ra if rb is None else rb
    return tube(bone, mat, [np.asarray(a, float), np.asarray(b, float)], [ra * ta, ra * tb], [rb_ * ta, rb_ * tb],
                sides=sides, hint=hint, phase=phase, caps=caps)


def ribbon(bone, mat, pts, width, up=(0, 1, 0), lift=0.0):
    """pts に沿った細い帯 (光る筋)。上から見える片面だけ。"""
    pts = [np.asarray(p, float) + V(0, lift, 0) for p in pts]
    faces = []
    L, R = [], []
    for i, p in enumerate(pts):
        t = nrm(pts[min(i + 1, len(pts) - 1)] - pts[max(i - 1, 0)])
        s = nrm(np.cross(t, V(*up)))
        L.append(p + s * width / 2)
        R.append(p - s * width / 2)
    for i in range(len(pts) - 1):
        f = _mk([L[i], R[i], R[i + 1], L[i + 1]], bone, mat, pts[i] - V(0, 5, 0))
        if f is not None:
            faces.append(f)
    return faces


# ------------------------------------------------------------------ 部品 (岩鯨の common から)

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


def mirror_x(faces, bone=None, swap=None):
    out = []
    for f in faces:
        v = f.verts.copy()
        v[:, 0] = -v[:, 0]
        b = bone or f.bone
        if bone is None and f.bone.endswith("_l"):
            b = f.bone[:-2] + "_r"
        mat = (swap or {}).get(f.mat, f.mat)
        g = _mk(list(v), b, mat, V(-f.ref[0], f.ref[1], f.ref[2]))
        if g is not None:
            out.append(g)
    return out


# ------------------------------------------------------------------ 絵

LIGHT = np.array([-0.35, 0.85, -0.4])
LIGHT = LIGHT / np.linalg.norm(LIGHT)

A_ = np.array
PAL = dict(
    back=A_([(36, 42, 62), (48, 56, 80), (62, 72, 100), (78, 90, 120), (96, 110, 140), (116, 130, 160)]),
    cov=A_([(50, 58, 84), (66, 76, 104), (84, 96, 126), (104, 118, 148), (126, 140, 170)]),
    sec=A_([(34, 40, 58), (46, 54, 76), (60, 70, 96), (76, 88, 116), (94, 108, 138)]),
    tailf=A_([(40, 46, 66), (54, 62, 88), (70, 80, 108), (90, 102, 132), (112, 124, 154)]),
    prim=A_([(26, 30, 46), (36, 42, 62), (48, 56, 80), (62, 72, 100), (78, 90, 120)]),
    belly=A_([(128, 134, 150), (160, 166, 182), (190, 196, 210), (214, 218, 228), (236, 238, 244)]),
    bar=A_([(60, 68, 92), (84, 94, 120), (110, 120, 146), (140, 148, 170)]),
    copper=A_([(84, 46, 28), (128, 70, 38), (172, 100, 52), (212, 136, 76), (240, 176, 112)]),
    beak=A_([(70, 62, 48), (100, 88, 62), (132, 116, 80), (164, 146, 100), (196, 178, 124)]),
    beak_d=A_([(24, 22, 24), (36, 32, 34), (50, 46, 48), (66, 60, 62)]),
    talon=A_([(24, 24, 30), (38, 38, 46), (56, 56, 66), (80, 80, 92)]),
    leg=A_([(96, 84, 58), (122, 108, 72), (148, 132, 88), (176, 160, 106)]),
    stone=A_([(70, 72, 86), (92, 94, 108), (116, 118, 132), (142, 144, 156), (168, 170, 180)]),
    glass=A_([(150, 196, 240), (190, 226, 255), (226, 244, 255), (250, 254, 255)]),
    gold=A_([(210, 150, 40), (240, 186, 70), (255, 218, 120), (255, 240, 180)]),
    vein=A_([(120, 176, 240), (170, 214, 255), (220, 240, 255), (255, 255, 255)]),
    eye=A_([(220, 168, 50), (250, 206, 90), (255, 232, 150), (255, 248, 210)]),
    dark=A_([(10, 10, 16), (20, 20, 28), (32, 32, 42), (44, 44, 56)]),
    cloud=A_([(120, 128, 150), (152, 160, 182), (184, 192, 210), (214, 220, 232), (240, 244, 250)]),
    plume=A_([(90, 100, 128), (120, 132, 162), (156, 168, 196), (196, 206, 226), (230, 236, 248)]),
)
FEATHER_MATS = ["back", "cov", "sec", "prim", "tailf", "plume", "wunder", "belly_f"]


def pick(pal, idx):
    pal = np.asarray(pal)
    return pal[np.clip(idx, 0, len(pal) - 1)].astype(float)


def ramp(pal, v):
    pal = np.asarray(pal)
    return pal[np.clip(np.floor(v * len(pal)).astype(int), 0, len(pal) - 1)].astype(float)


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
    pal = dict(PAL)
    pal.update(ctx.get("pal", {}))

    def put(mask, c):
        nonlocal col
        col = np.where(mask[..., None], c, col)

    def shaded(p, base, lw=0.30, nw=0.14, extra=0.0, ed=True):
        ns = len(p)
        v = base + lw * lit + nw * (n_lo - 0.5) + 0.10 * (n_mid - 0.5) + 0.07 * bay + extra
        idx = np.floor(v * ns).astype(int)
        if ed:
            idx = idx + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave
        return idx

    barb = (np.mod(LV * 0.9 + LU * 0.55, 3.0) < 0.8)
    # 羽の素材: 毛羽の線と、先の淡い縁
    for m, base in (("back", 0.50), ("cov", 0.52), ("sec", 0.50), ("prim", 0.48), ("tailf", 0.50), ("plume", 0.55)):
        sel = mats == m
        if not sel.any():
            continue
        p = pal[m]
        idx = shaded(p, base + ctx.get("shift_" + m, 0.0)) - (barb & (n_mid > 0.4)) - (n_hi > 0.8)
        tipband = ((CH - LV) < ctx.get("tipband", 3)) & (CH > 10) & (m in ("sec", "prim", "tailf", "plume"))
        idx = idx + tipband * ctx.get("tipbright", 1)
        put(sel, pick(p, idx))
    if ctx.get("prim_glow"):
        pg = (mats == "prim") & ((CH - LV) < ctx.get("prim_glow_len", 6)) & (CW < 40) & (N[..., 1] > 0)
        put(pg, ramp(pal["vein"], 0.5 + 0.4 * (0.5 + 0.5 * lit) + 0.1 * bay))
        glow |= pg
    # 腹 (白い羽に灰の横縞)
    sel = (mats == "belly") | (mats == "wunder") | (mats == "belly_f")
    if sel.any():
        bi = shaded(pal["belly"], ctx.get("belly_base", 0.92), 0.16, 0.10)
        bc = pick(pal["belly"], bi)
        barring = ((np.abs(Z) * ctx.get("bar_f", 0.16) + 0.4 * np.abs(X) * 0.03) % 1.0 < ctx.get("bar_w", 0.22))
        barmask = barring & (mats != "belly_f") & (np.abs(N[..., 1]) > 0.2)
        bc = np.where((barmask & (n_mid > 0.25))[..., None], pick(pal["belly"], bi - 1), bc)
        bc = np.where((barb & (mats == "belly_f"))[..., None], pick(pal["belly"], bi - 1), bc)
        wu = mats == "wunder"
        bc = np.where((wu & barb)[..., None], pick(pal["belly"], bi - 1), bc)
        tipd = wu & ((CH - LV) < ctx.get("under_tip", 7)) & (CH > 24) & (CW < 40)
        bars = wu & (CH > 30) & (CW < 40) & ((LV // 7) % 2 == 0) & ((CH - LV) < 36) & ((CH - LV) >= ctx.get("under_tip", 7))
        bc = np.where(bars[..., None], pick(pal["belly"], bi - 1), bc)
        bc = np.where(tipd[..., None], pick(pal["bar"], shaded(pal["bar"], 0.55, 0.2, 0.1)), bc)
        put(sel, bc)
    for m in ("copper", "beak", "beak_d", "talon", "leg", "stone", "dark", "cloud"):
        sel = mats == m
        if sel.any():
            p = pal[m]
            extra = 0.0
            put(sel, pick(p, shaded(p, ctx.get("base_" + m, 0.52), 0.34, 0.14, extra) - (n_hi > 0.82)))
    # 光る素材
    for m, p in (("vein", "vein"), ("glass", "glass"), ("gold", "gold"), ("gate", "glass"), ("knob_w", "glass"),
                 ("knob_t", "gold"), ("bolt", "vein")):
        sel = mats == m
        if sel.any():
            v = 0.40 + 0.35 * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.08 * bay
            put(sel, ramp(pal[p], v))
            glow |= sel
    # 目 (金色、縦長の黒い瞳)
    ey = mats == "eye"
    if ey.any():
        ez = ctx.get("eye_y", 56.0)
        pupil = ey & (np.abs(Y - ez) < ctx.get("pupil_w", 0.7)) & (np.abs(Y - ez) >= 0) & (hash1(0, 0) >= 0)
        ev = 0.45 + 0.22 * (0.5 + 0.5 * lit) + 0.2 * (1 - np.clip(np.abs(Z - ctx.get("eye_z", -101.0)) / 3.0, 0, 1)) + 0.08 * bay
        put(ey, ramp(pal["eye"], ev))
        pup = ey & (np.hypot(Z - ctx.get("eye_z", -101.0), (Y - ez) * 0.55) < ctx.get("pupil_r", 0.9))
        put(pup, pick(pal["dark"], np.floor(0.6 + 0.8 * (0.5 + bay)).astype(int)))
        glow |= ey & ~pup
    hook = ctx.get("hook")
    if hook:
        col, glow = hook(col, glow, dict(P=P, N=N, mats=mats, lit=lit, bay=bay, n_lo=n_lo, n_mid=n_mid, n_hi=n_hi,
                                         X=X, Y=Y, Z=Z, convex=convex, concave=concave, D=D, INS=INS, LU=LU, LV=LV,
                                         CW=CW, CH=CH, fi=fi, K=K, pick=pick, ramp=ramp, shaded=shaded, pal=pal,
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
        data = export_mesh(faces, size, d / f"{tag}_mesh.json", f"thunder_wing_{tag}_lowpoly",
                           f"{tag}_atlas.png", f"{tag}_glowmask.png", bones, tex)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, data=data, bones=bones, tris=tri_count(faces), tex=tex,
                by_bone={b: tri_count([f for f in faces if f.bone == b]) for b in sorted({f.bone for f in faces})})
