"""潮殻 (tide_shell) 低ポリ案の共通部品。ラフ。src には触らない。

座標は 1 単位 = 1/16 ブロック (ゲームでは x8 で 0.5 ブロック)、y が上、正面は -Z。
骨の名前・階層・pivot は src/.../geo/entity/tide_shell*.geo.json (3 段階とも同じ) から読む。休めの回転なし。
メッシュの頂点はモデル全体の絶対座標。+X が潮殻の左 (潮門の側)。
LOD: 0 = 近 (<64 ブロック), 1 = 中 (<160), 2 = 遠。
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "lp_stone_whale"))
from common import *  # noqa: E402,F401,F403  (岩鯨の共通部品: 骨以外の幾何・絵・出力を流用)
import common as wc  # noqa: E402
from family import arch  # noqa: E402

OUTDIR = ROOT / "docs/review/lowpoly_roughs/lp_tide_shell"
SUBJ = "tide_shell"

CX, CZ = 0.0, -3.0          # 甲羅の平面の中心
AX, AZ = 84.0, 101.0        # 甲羅の半幅・半長 (今のモデルは x ±84 (縁の張り出しは ±100)、z -104..98)
WATER = 52.0                # 巡航中の水面の高さ (甲羅の頂 68 が水面の上 8 ブロック)


# ------------------------------------------------------------------ 骨

def geo(name=SUBJ):
    return json.loads((GEO / f"{name}.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]


def bone_defs():
    g = geo()
    out = {b["name"]: (b.get("parent"), [float(x) for x in b["pivot"]], [float(x) for x in b.get("rotation", [0, 0, 0])])
           for b in g["bones"]}
    for nm in ("tide_shell_mid", "tide_shell_far"):
        o = {b["name"]: (b.get("parent"), [float(x) for x in b["pivot"]], [float(x) for x in b.get("rotation", [0, 0, 0])])
             for b in geo(nm)["bones"]}
        assert o == out, nm
    return out


def box_count(name):
    return sum(len(b.get("cubes", [])) for b in geo(name)["bones"])


# ------------------------------------------------------------------ 甲羅

def rn_of(s, c, m):
    return (abs(s) ** m + abs(c) ** m) ** (1.0 / m)


def rr_of(x, z, m):
    return ((abs((x - CX) / AX)) ** m + (abs((z - CZ) / AZ)) ** m) ** (1.0 / m)


class Shape:
    """甲羅の形。Hf(u, x, z): 上面の高さ (u = 平面の入れ子の度合 0 中心 .. 1 縁)。"""

    def __init__(self, m=2.5, H=None, rimmod=None, ax=AX, az=AZ):
        self.m, self.Hf, self.rimmod = m, H, rimmod
        self.ax, self.az = ax, az

    def pos(self, phi, u):
        s, c = math.sin(phi), math.cos(phi)
        rn = rn_of(s, c, self.m)
        return self.ax * u * s / rn + CX, CZ + self.az * u * c / rn

    def H(self, x, z):
        u = min(rr_of(x, z, self.m), 1.0)
        return self.Hf(u, x, z)

    def surf(self, x, z, lift=0.0):
        """甲羅の上面の点と法線"""
        y = self.H(x, z)
        e = 0.6
        dx = (self.H(x + e, z) - self.H(x - e, z)) / (2 * e)
        dz = (self.H(x, z + e) - self.H(x, z - e)) / (2 * e)
        n = nrm(V(-dx, 1.0, -dz))
        return V(x, y, z) + n * lift, n


def shell_faces(shape, S, top_u, rim_rows, under_rows, jit=0.0, bone="body", mats=("shell", "shell", "plast"), tag=0):
    """甲羅の本体 (上面 + 縁 + 腹)。top_u: 上面の u の列 (0 で始まり 1 で終わる)。
    rim_rows: [(u, y)] 縁の外への張り出しと下面へ回り込む列。under_rows: [(u, y)] 腹へ向かう列 (u が減る)。"""
    rows = []
    for u in top_u:
        rows.append((u, None, mats[0]))
    for (u, y) in rim_rows:
        rows.append((u, y, mats[1]))
    for (u, y) in under_rows:
        rows.append((u, y, mats[2]))
    phis = [2 * math.pi * i / S for i in range(S)]
    pts = []
    for ri, (u, y, _m) in enumerate(rows):
        row = []
        for ci, phi in enumerate(phis):
            ue = u
            if shape.rimmod and u >= 0.8:
                ue = u * (1 + shape.rimmod(phi) * smooth(0.8, 1.0, min(u, 1.0)))
            x, z = shape.pos(phi, ue)
            yy = shape.H(x, z) if y is None else y
            if u == 0:
                x, z = CX, CZ
            elif jit:
                j = (hash1(ri * 97 + ci * 13 + tag, 5) - 0.5) * 2 * jit
                yy += j * smooth(0.05, 0.3, u)
                x += (hash1(ri * 31 + ci * 7 + tag, 6) - 0.5) * jit * 0.8 * smooth(0.1, 0.5, u)
                z += (hash1(ri * 17 + ci * 11 + tag, 7) - 0.5) * jit * 0.8 * smooth(0.1, 0.5, u)
            row.append(V(x, yy, z))
        pts.append(row)
    ref = V(0, 40, CZ)
    faces = []
    for r in range(len(rows) - 1):
        a, b = pts[r], pts[r + 1]
        mat = rows[r + 1][2]
        g = [a + [a[0]], b + [b[0]]]
        faces += grid(bone, mat, g, ref)
    return faces


# ------------------------------------------------------------------ 頭・首

def lvi(k, n):
    if k == 0:
        return -1
    if k <= n:
        return k - 1
    if k == n + 1:
        return n
    return 2 * n + 1 - k


class Head:
    """頭の外殻。stations: [dict(z, w, y0, y1)] (後ろから前)。levels: 縦の段 (t = -1 腹 .. 1 背、'L' は口の線)。"""

    def __init__(self, stations, levels, p=2.4, lip_t=-0.28, hook=None, jit=0.0):
        self.st = stations
        self.lv = [lip_t if t == "L" else t for t in levels]
        self.iL = [i for i, t in enumerate(levels) if t == "L"][0]
        self.n = len(levels)
        self.p = p
        self.hook = hook
        self.rings = [self._ring(i, s, jit) for i, s in enumerate(stations)]

    def _ring(self, i, s, jit):
        n = self.n
        z, w, y0, y1 = s["z"], s["w"], s["y0"], s["y1"]
        yc, h = (y0 + y1) / 2, (y1 - y0)
        out = np.zeros((2 * n + 2, 3))
        out[0] = (0, y0, z)
        out[n + 1] = (0, y1, z)
        for k, t in enumerate(self.lv):
            x = w * max(0.0, 1 - abs(t) ** self.p) ** (1 / self.p)
            y = yc + t * h / 2
            if jit:
                j = (hash1(i * 53 + k * 7, 8) - 0.5) * 2 * jit
                x *= 1 + j
                y += j * 0.6
            if self.hook:
                x, y = self.hook(z, t, x, y, k)
            out[1 + k] = (x, y, z)
            out[2 * n + 1 - k] = (-x, y, z)
        return out

    def faces(self, matfn, bone="head", cap_front=True, mats_cap="beak"):
        n = self.n
        m = 2 * n + 2
        faces = []
        for a in range(len(self.st) - 1):
            r0, r1 = self.rings[a], self.rings[a + 1]
            cz = (self.st[a]["z"] + self.st[a + 1]["z"]) / 2
            cen = (r0.mean(0) + r1.mean(0)) / 2
            for k in range(m):
                k2 = (k + 1) % m
                l1, l2 = lvi(k, n), lvi(k2, n)
                lo, hi = min(l1, l2), max(l1, l2)
                mat = matfn(cz, lo, hi, self)
                f = wc._mk([r0[k], r0[k2], r1[k2], r1[k]], bone, mat, cen)
                if f is not None:
                    faces.append(f)
        if cap_front:
            r = self.rings[-1]
            f = wc._mk(list(r), bone, mats_cap, self.rings[-2].mean(0))
            if f is not None:
                faces.append(f)
        return faces

    def ring_at(self, z):
        st = self.st
        lo_, hi_ = sorted((st[0]["z"], st[-1]["z"]))
        z = min(max(z, lo_), hi_)
        for i in range(len(st) - 1):
            if min(st[i]["z"], st[i + 1]["z"]) <= z <= max(st[i]["z"], st[i + 1]["z"]):
                t = (z - st[i]["z"]) / (st[i + 1]["z"] - st[i]["z"])
                return self.rings[i] * (1 - t) + self.rings[i + 1] * t, i, t
        return self.rings[-1], len(st) - 2, 1.0

    def at(self, z, y, side=1):
        r, i, t = self.ring_at(z)
        n = self.n
        half = [r[k] for k in range(0, n + 2)]
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
        dz = self.rings[i0 + 1][k_near] - self.rings[i0][k_near]
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

    def y_lip(self, z):
        zs = np.array([s["z"] for s in self.st])
        ys = np.array([r[1 + self.iL][1] for r in self.rings])
        o = np.argsort(zs)
        return np.interp(z, zs[o], ys[o])


# ------------------------------------------------------------------ ひれ

def paddle(bone, mat, x0, L, nr, zl, zt, th, y, scallop=0.0, tag=0, tip_cap=True):
    """ひれ (+X 側)。s = 0..1 のとき zl(s) 前縁、zt(s) 後縁、th(s) 厚み、y(s) 中心の高さ。"""
    rings = []
    for i in range(nr):
        s = i / (nr - 1)
        a, b, t, yc = zl(s), zt(s), th(s), y(s)
        if scallop and 0 < i < nr - 1:
            b += scallop * (1 if i % 2 else -0.5) * (hash1(i * 5 + tag, 3) * 0.6 + 0.6)
        x = x0 + L * s
        c = b - a
        ring = [V(x, yc, a), V(x, yc + t * 0.5, a + c * 0.28), V(x, yc + t * 0.42, a + c * 0.7),
                V(x, yc - t * 0.1, b), V(x, yc - t * 0.5, a + c * 0.68), V(x, yc - t * 0.5, a + c * 0.25)]
        rings.append(ring)
    return loft(bone, mat, rings, caps=(False, tip_cap))


# ------------------------------------------------------------------ 珊瑚・海藻

def on_surf(shape, x, z, sink=1.2):
    p, n = shape.surf(x, z)
    return p - n * sink, n


def brain_coral(c, r, mat="coral_o", segs=7, rings=2, flat=0.75, tag=0):
    def dfm(p):
        return p * (1 + 0.08 * (hash1(int(abs(p[0] * 3 + p[1] * 5 + p[2] * 7) * 10) + tag, 1) - 0.5))
    return ellipsoid("coral", mat, V(*c) + V(0, r * flat * 0.5, 0), (r, r * flat, r), segs=segs, rings=rings, deform=dfm)


def branch_coral(base, d, length, r, mat="coral_p", depth=2, sides=4, tag=0, spread=0.55):
    """枝サンゴ: 太い幹から細い枝が分かれる"""
    base = V(*base)
    d = nrm(V(*d))
    faces = spike("coral", mat, base, d, length, r, sides=sides, rings=1, hint=(1, 0, 0), phase=tag * 17)
    if depth > 0:
        for j in range(3 if depth > 1 else 2):
            a = 2 * math.pi * (j / 3.0 + hash1(tag * 3 + j, 4) * 0.2)
            side = nrm(V(math.cos(a), 0.0, math.sin(a)))
            nd = nrm(d + side * spread)
            b2 = base + d * length * (0.45 + 0.2 * j / 3.0)
            faces += branch_coral(b2, nd, length * 0.62, r * 0.62, mat, depth - 1, sides, tag * 7 + j + 1, spread)
    return faces


def tube_coral(base, n, h, r, mat="coral_v", tag=0, sides=5):
    faces = []
    base = V(*base)
    for j in range(n):
        a = 2 * math.pi * j / n + hash1(tag + j, 2)
        rad = (0.0 if j == 0 else r * 1.5)
        b = base + V(math.cos(a) * rad, 0, math.sin(a) * rad)
        hh = h * (0.65 + 0.5 * hash1(tag * 5 + j, 3)) * (1.2 if j == 0 else 1.0)
        path = [b - V(0, 0.8, 0), b + V(0, hh, 0)]
        faces += tube("coral", mat, path, [r, r * 0.92], sides=sides, hint=(1, 0, 0), caps=(False, True))
    return faces


def fan_coral(base, w, h, rot=0.0, mat="coral_y", n=4):
    """うちわ形のサンゴ (薄い板を 2 枚あわせる)"""
    base = V(*base)
    R = rot_y(rot)
    top = [R @ V(-w / 2 + w * i / (n - 1), h * (0.78 + 0.22 * math.sin(math.pi * i / (n - 1) + 0.4)), 0) + base for i in range(n)]
    faces = []
    for sgn in (1, -1):
        off = R @ V(0, 0, 0.7 * sgn)
        for i in range(n - 1):
            q = [base + off + R @ V(-w * 0.1 + w * 0.2 * i / (n - 1), 0, 0), base + off + R @ V(-w * 0.1 + w * 0.2 * (i + 1) / (n - 1), 0, 0),
                 top[i + 1] + off, top[i] + off]
            f = wc._mk(q, "coral", mat, base + V(0, h / 2, 0) + R @ V(0, 0, -5 * sgn))
            if f is not None:
                faces.append(f)
    return faces


def kelp_blade(base, h, w, wave, tag, mat="kelp", rows=4, lean=(0, 0)):
    base = V(*base)
    ph = hash1(tag, 9) * 6.28
    pts = []
    for r in range(rows + 1):
        t = r / rows
        c = base + V(lean[0] * t * t * h + wave * math.sin(ph + t * 3.2), h * t, lean[1] * t * t * h + wave * 0.6 * math.cos(ph + t * 2.7))
        ww = w * (1 - 0.65 * t) * (0.7 + 0.3 * math.sin(t * 9 + ph))
        pts.append([c + V(-ww / 2, 0, 0), c, c + V(ww / 2, 0, 0)])
    ref = base + V(0, h / 2, -40)
    return grid("kelp", mat, pts, ref)


# ------------------------------------------------------------------ 遺跡・門

def tower(cx, cz, by, h, w, lod, taper=0.14, crown=True, glyph=True, mat="ruin"):
    faces = []
    nseg = 3 if lod == 0 else 2
    faces += slab("ruins", mat, (cx, by + 2.0, cz), (w * 1.7, 5, w * 1.7), taper=1.4)
    y0 = by + 4.5
    path = [V(cx, y0 + h * i / nseg, cz) for i in range(nseg + 1)]
    ras = [w * 0.5 / 0.7071 * (1 - taper * i / nseg) for i in range(nseg + 1)]
    faces += tube("ruins", mat, path, ras, sides=4, hint=(1, 0, 0), phase=45, caps=(False, True))
    if lod == 0 and glyph:
        for yy in (y0 + h * 0.3, y0 + h * 0.62):
            wr = w * 0.5 * (1 - taper * (yy - y0) / h) + 0.3
            for (dx, dz) in ((wr, 0), (-wr, 0), (0, wr), (0, -wr)):
                size = (0.9, 4.2, 3.2) if dx else (3.2, 4.2, 0.9)
                faces += slab("ruins", "glyph", (cx + dx, yy, cz + dz), size, taper=0.0)
    if crown:
        ty = y0 + h
        wr = w * 0.5 * (1 - taper) + 0.5
        if lod < 2:
            for sx in (-1, 1):
                for sz in (-1, 1):
                    faces += slab("ruins", mat, (cx + sx * wr, ty + 4.0, cz + sz * wr), (2.4, 8.0, 2.4), taper=0.5)
        faces += slab("ruins", "glyph", (cx, ty + 3.5, cz), (w * 0.62, 6.0, w * 0.62), taper=0.0)
        faces += slab("ruins", mat, (cx, ty + 8.6, cz), (w * 1.5, 1.8, w * 1.5), taper=1.2)
        faces += spike("ruins", mat, V(cx, ty + 9.0, cz), V(0, 1, 0), 6.0, w * 0.55, sides=4, rings=1, hint=(1, 0, 0), phase=45)
    return faces


def gate_frame(lod, glow=True, mat="ruin", wide=1.0):
    """潮門: 左後ろの縁 (x 62..76, z 53..75)。+X 向きの石の枠と光る口"""
    faces = []
    zc = 64.0
    zs = 11.0 * wide
    by = 10.0
    for sz in (-1, 1):
        faces += slab("gate", mat, (72.5, by + 14, zc + sz * zs), (4.4, 28, 3.6), taper=0.5)
    faces += slab("gate", mat, (71.0, by + 29.5, zc), (7.0, 4.6, zs * 2 + 6), taper=0.9)
    if lod < 2:
        faces += slab("gate", mat, (69.0, by + 33.5, zc), (4.0, 3.4, zs * 2 + 1.5), taper=0.8)
    faces += slab("gate", mat, (71.0, by + 1.6, zc), (10.0, 3.4, zs * 2 + 7), taper=1.0)
    if glow:
        faces += slab("gate", "glyph", (73.2, by + 14, zc), (0.8, 24, zs * 2 - 3.2), taper=0.0)
    if lod == 0:
        faces += slab("gate", "glyph", (73.0, by + 32.0, zc), (1.0, 1.6, 7.0), taper=0.0)
    return faces


# ------------------------------------------------------------------ 絵

def hex_cells(X, Z, s):
    """ずらした格子 (煉瓦) のセル境界までの距離 (単位) とセル番号"""
    v = Z / s
    row = np.floor(v)
    u = X / s + 0.5 * (row % 2)
    fu, fv = u - np.floor(u), v - row
    d = np.minimum(np.minimum(fu, 1 - fu) * s, np.minimum(fv, 1 - fv) * s)
    return d, np.floor(u) * 31 + row * 17


def scute_field(X, Z, m=2.5, nm=22, vw=0.26, nv=5, nc=4, rm=0.88):
    """甲羅の鱗板 (背 5・肋 4 組・縁 nm 枚) の境目までの距離 (単位) とセル番号"""
    ex, ez = (X - CX) / AX, (Z - CZ) / AZ
    rr = (np.abs(ex) ** m + np.abs(ez) ** m) ** (1.0 / m)
    phi = np.arctan2(ex, ez)
    ang = phi / (2 * np.pi) * nm
    d_rad = np.abs(ang - np.round(ang)) * (2 * np.pi / nm) * np.maximum(rr, 0.2) * AX * 0.95
    d_ring = np.abs(rr - rm) * AX * 0.95
    cell_m = np.round(ang)
    inner = rr < rm
    vwid = vw * (1 + 0.06 * np.cos(ez * 9.0))
    d_vs = np.abs(np.abs(ex) - vwid) * AX
    in_v = np.abs(ex) < vwid
    zb_v = np.linspace(-0.62, 0.62, nv - 1)
    d_vt = np.min([np.abs(ez - b) * AZ * 0.92 for b in zb_v], axis=0)
    cell_v = np.sum([ez > b for b in zb_v], axis=0)
    zb_c = np.linspace(-0.62, 0.62, nc - 1)
    fan = 1.0 + 0.8 * (np.abs(ex) - vw)
    d_ct = np.min([np.abs(ez - b * fan) * AZ * 0.9 for b in zb_c], axis=0)
    cell_c = np.sum([ez > b * fan for b in zb_c], axis=0) + 10 * (ex > 0)
    d_in = np.where(in_v, np.minimum(d_vs, d_vt), np.minimum(d_vs, d_ct))
    d = np.where(inner, np.minimum(d_in, d_ring), np.minimum(d_rad, d_ring))
    cell = np.where(inner, np.where(in_v, cell_v + 40, cell_c), cell_m + 100)
    return d, cell


def make_hook(cfg):
    """絵の差し替え。cfg に各素材のパレットと模様の設定。岩鯨の paint のあとで呼ばれる。"""
    DARK = wc.PAL["DARK"]

    def hook(col, glow, e):
        mats, N, X, Y, Z = e["mats"], e["N"], e["X"], e["Y"], e["Z"]
        pick, shaded, bay, lit = e["pick"], e["shaded"], e["bay"], e["lit"]
        n_lo, n_mid, n_hi = e["n_lo"], e["n_mid"], e["n_hi"]
        convex, concave = e["convex"], e["concave"]
        out = col

        def setm(name, c):
            nonlocal out
            out = np.where((mats == name)[..., None], c, out)

        def setmask(mask, c):
            nonlocal out
            out = np.where(mask[..., None], c, out)

        # ---- 甲羅
        sp = cfg["shell"]
        d, cell = scute_field(X, Z, cfg.get("m", 2.5), cfg.get("nm", 22), cfg.get("vw", 0.26))
        tint = (hash1(cell, 4) - 0.5) * cfg.get("cell_tint", 0.22)
        idx = shaded(sp, 0.52 + tint, 0.30, 0.12) - (n_hi > 0.8) + (n_hi < 0.12)
        sc = pick(sp, idx)
        sw = cfg.get("seam_w", 0.9)
        if cfg.get("rings", 0) > 0:
            ringv = ((d - sw) % cfg.get("ring_p", 3.6)) < cfg.get("ring_w", 0.6)
            ringv &= (d > sw) & (d < cfg.get("ring_max", 16.0))
            sc = np.where(ringv[..., None], pick(sp, idx - 1), sc)
        if cfg.get("seams", 1) > 0:
            sc = np.where((d < sw)[..., None], pick(sp, idx - 3), sc)
        if cfg.get("shell_hook"):
            sc = cfg["shell_hook"](sc, e, d, cell)
        alg = cfg.get("algae", 0.0)
        if alg > 0:
            ay = cfg.get("algae_y", 40.0)
            score = (ay - Y) / ay * 1.3 + 0.7 * (n_mid - 0.5) + 0.3 * (n_lo - 0.5)
            m_ = score > (1.0 - alg)
            ap = cfg.get("algae_pal", wc.PAL["MOSS"])
            ai = np.floor((0.5 + 0.25 * lit + 0.2 * (n_mid - 0.5) + 0.12 * bay) * len(ap)).astype(int) + (convex & (lit > 0)) - concave
            sc = np.where(m_[..., None], pick(ap, ai), sc)
        setm("shell", sc)
        # ---- 腹
        pp = cfg["plast"]
        dpl, cpl = hex_cells(np.abs(X), Z, 14.0)
        setm("plast", pick(pp, shaded(pp, 0.5, 0.28, 0.12) - (dpl < 0.7)))
        # ---- 皮 (頭・首・ひれ)
        kp = cfg["skin"]
        du, cu = hex_cells(Z, Y + 0.8 * np.abs(X), cfg.get("scale_s", 5.0))
        ki = shaded(kp, 0.5, 0.26, 0.14) - (du < 0.55) * cfg.get("scale_seam", 1) + (n_hi > 0.85)
        sk = pick(kp, ki)
        under = N[..., 1] < -0.35
        kb = cfg.get("skin_belly", kp)
        sk = np.where(under[..., None], pick(kb, shaded(kb, 0.55, 0.22, 0.1)), sk)
        setm("skin", sk)
        fp = cfg.get("flip", kp)
        rays = (np.abs(((Z + 0.5 * np.abs(X)) * cfg.get("ray_f", 0.30)) % 1.0 - 0.5) < 0.08) & (np.abs(N[..., 1]) > 0.3)
        fl = pick(fp, shaded(fp, 0.5, 0.3, 0.12) - rays + (n_hi > 0.85))
        fl = np.where(under[..., None], pick(kb, shaded(kb, 0.55, 0.22, 0.1)), fl)
        setm("flip", fl)
        # ---- くちばし・顔
        bp = cfg["beak"]
        bi = shaded(bp, 0.55, 0.3, 0.1) - (np.abs(((Z * 0.9 + 3 * (n_lo - 0.5)) % 1.0) - 0.5) < 0.05)
        setm("beak", pick(bp, bi))
        setm("jaw", pick(bp, bi - 1))
        ym = cfg.get("ymouth")
        if ym is not None:
            mm = (np.abs(Y - ym(Z)) < cfg.get("mouth_w", 0.9)) & (Z < cfg.get("mouth_z", -118.0)) & np.isin(mats, ["beak", "jaw", "skin"])
            setmask(mm, pick(DARK, np.floor(1.0 + 0.8 * (0.5 + bay)).astype(int)))
        setm("nostril", pick(DARK, np.floor(1.0 + 0.8 * (0.5 + bay)).astype(int)))
        lp_ = cfg.get("lid", kp)
        setm("lid", pick(lp_, shaded(lp_, 0.5, 0.3, 0.12)))
        brp = cfg.get("brow", lp_)
        setm("brow", pick(brp, shaded(brp, 0.45, 0.3, 0.1)))
        cl = cfg.get("claw", wc.PAL["BONE"])
        setm("claw", pick(cl, shaded(cl, 0.55, 0.3, 0.1)))
        ec = cfg.get("eye_c", (22, 30, -130))
        r = np.sqrt((Y - ec[1]) ** 2 + (Z - ec[2]) ** 2)
        ey = mats == "eye"
        ev = 0.40 + 0.22 * (0.5 + 0.5 * lit) + 0.28 * (1 - np.clip(r / cfg.get("eye_r", 3.2), 0, 1)) + 0.08 * bay
        setm("eye", wc.ramp(wc.PAL["EYE"], ev))
        pup = ey & (r < cfg.get("pupil_r", 1.3))
        setmask(pup, pick(DARK, np.floor(0.6 + 0.8 * (0.5 + bay)).astype(int)))
        glow = glow | (ey & ~pup)
        # ---- 盛り上がった鱗板・岩
        pl = cfg.get("plate", sp)
        pidx = shaded(pl, 0.55 + 0.2 * (hash1(cell, 6) - 0.5), 0.34, 0.1) - (n_hi > 0.8)
        pc = pick(pl, pidx)
        if cfg.get("plate_rings", 0) > 0:
            rg = ((d % cfg.get("pr_p", 3.0)) < 0.75) & (d > 0.9) & (d < cfg.get("pr_max", 24.0))
            pc = np.where(rg[..., None], pick(pl, pidx - 1), pc)
            pc = np.where((d < 0.9)[..., None], pick(pl, pidx - 3), pc)
        setm("plate", pc)
        # ---- サンゴ
        for nm_ in ("coral_o", "coral_p", "coral_v", "coral_y", "coral_g"):
            cp = cfg[nm_]
            gv = np.abs(((n_mid * 5.0 + Y * 0.15) % 1.0) - 0.5) < 0.07
            ci = shaded(cp, 0.5, 0.34, 0.16) - (gv if nm_ == "coral_o" else 0) + (n_hi > 0.8)
            setm(nm_, pick(cp, ci))
        kp2 = cfg["kelp"]
        kv = np.abs(((Y * 0.5 + Z * 0.02) % 1.0) - 0.5) < 0.1
        setm("kelp", pick(kp2, shaded(kp2, 0.5, 0.3, 0.12) - kv))
        if cfg.get("extra_hook"):
            out, glow = cfg["extra_hook"](out, glow, e)
        return out, glow

    return hook


# ------------------------------------------------------------------ 出力

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
        data = export_mesh(faces, size, d / f"{tag}_mesh.json", f"tide_shell_{tag}_lowpoly",
                           f"{tag}_atlas.png", f"{tag}_glowmask.png", bones, tex)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, data=data, bones=bones, tris=tri_count(faces), tex=tex,
                by_bone={b: tri_count([f for f in faces if f.bone == b]) for b in sorted({f.bone for f in faces})})
