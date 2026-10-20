"""武器用の低ポリ道具 (lpkit の上に載せる薄い層)。

座標: 1 単位 = 1/16 ブロック (item モデルの 1 単位)。武器は直立 (+Y が刃先)、x = 0・z = 0 が中心、
広い面は ±Z を向く (正面 = -Z)。今の item モデル (回転 -45 度を掛ける前) と同じ向き・同じ高さ (y -3..19)。
"""
from __future__ import annotations

import copy
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
LP = HERE.parent
ROOT = HERE.parents[3]
for _p in (LP, ROOT / "tools/rough"):
    sys.path.insert(0, str(_p))
import lpkit  # noqa: E402
from lpkit import (tube, spike, loft, ring, poly, grid, transform, tri_count, build_atlas, render,  # noqa: E402,F401
                   edge_fields, fill_outside, rot_x, rot_y, rot_z, nrm, Face)
from lpkit import _mk  # noqa: E402
from bone_sentinel_lp import hash1, vnoise, BAYER  # noqa: E402

ASSETS = ROOT / "src/main/resources/assets/skyabyss"
OUT = ROOT / "docs/review/lowpoly_roughs/weapons"
BONE = "w"


def V(*a):
    return np.array(a, float)


# ------------------------------------------------------------------ 形

CHAMFER = [0.0]   # > 0 のとき、大きめの箱の縦の角を面取りする (整え / 細密の案)


def cblock(mat, c, size, rot, taper, skip, shear, ch):
    """縦の角を面取りした箱 (八角柱)。"""
    sx, sy, sz = size
    hy = sy / 2
    R = np.eye(3) if rot is None else rot
    c = np.asarray(c, float)

    def ringp(y, hx, hz, sh):
        k = min(ch, 0.3 * min(hx, hz))
        return [R @ np.array(p, float) + c for p in (
            (-hx + k + sh, y, -hz), (hx - k + sh, y, -hz), (hx + sh, y, -hz + k), (hx + sh, y, hz - k),
            (hx - k + sh, y, hz), (-hx + k + sh, y, hz), (-hx + sh, y, hz - k), (-hx + sh, y, -hz + k))]
    b = ringp(-hy, sx / 2, sz / 2, 0.0)
    t = ringp(hy, sx / 2 * taper[0], sz / 2 * taper[1], shear)
    out = []
    qs = []
    if "b" not in skip:
        qs.append(b[::-1])
    if "u" not in skip:
        qs.append(t)
    for i in range(8):
        j = (i + 1) % 8
        qs.append([b[i], b[j], t[j], t[i]])
    for q in qs:
        f = _mk(list(q), BONE, mat, c)
        if f is not None:
            out.append(f)
    return out


def block(mat, c, size, rot=None, taper=(1.0, 1.0), skip=(), shear=0.0):
    """箱 (taper: 上面の x, z の倍率。shear: 上面を x へずらす量)。skip に 'b' 'u' を入れると下・上の面を作らない。"""
    sx, sy, sz = size
    if CHAMFER[0] > 0 and mat not in ("gem", "trim", "dark", "cord", "cloth") and min(sx, sz) >= 2.0 and sy >= 0.9:
        return cblock(mat, c, size, rot, taper, skip, shear, CHAMFER[0])
    hx, hy, hz = sx / 2, sy / 2, sz / 2
    tx, tz = taper
    b = [(-hx, -hy, -hz), (hx, -hy, -hz), (hx, -hy, hz), (-hx, -hy, hz)]
    t = [(-hx * tx + shear, hy, -hz * tz), (hx * tx + shear, hy, -hz * tz),
         (hx * tx + shear, hy, hz * tz), (-hx * tx + shear, hy, hz * tz)]
    R = np.eye(3) if rot is None else rot
    c = np.asarray(c, float)

    def P(p):
        return R @ np.array(p, float) + c

    qs = []
    if "b" not in skip:
        qs.append((b[0], b[1], b[2], b[3]))
    if "u" not in skip:
        qs.append((t[3], t[2], t[1], t[0]))
    qs += [(b[0], t[0], t[1], b[1]), (b[1], t[1], t[2], b[2]), (b[2], t[2], t[3], b[3]), (b[3], t[3], t[0], b[0])]
    out = []
    for q in qs:
        f = _mk([P(p) for p in q], BONE, mat, c)
        if f is not None:
            out.append(f)
    return out


def blade(mat, st, colmats=None, tip=True, k=0.55):
    """刃。st = [(y, xl, xr, tf, tb[, cx[, cz]]), ...] の断面 (六角形のレンズ) を下から順につなぐ。
    xl は負、xr は正 (左右の縁)。tf / tb は前 (-Z) と後ろの厚み。cx, cz は断面の中心のずれ (反り)。
    最後の断面の幅と厚みを 0 にすると先が尖る。colmats は 6 列の材質 (縁の斜面・前・縁・縁・後・縁)。"""
    cm = colmats or [mat if mat else "main"] * 6
    if colmats is None and mat == "main":
        cm = ["edge", "main", "edge", "edge", "main", "edge"]
    rings = []
    for s in st:
        y, xl, xr, tf, tb = s[:5]
        cx = s[5] if len(s) > 5 else 0.0
        cz = s[6] if len(s) > 6 else 0.0
        rings.append([V(xl + cx, y, cz), V(xl * k + cx, y, cz - tf), V(xr * k + cx, y, cz - tf),
                      V(xr + cx, y, cz), V(xr * k + cx, y, cz + tb), V(xl * k + cx, y, cz + tb)])
    out = []
    for i in range(len(rings) - 1):
        ref = (np.mean(rings[i], 0) + np.mean(rings[i + 1], 0)) / 2
        for j in range(6):
            q = [rings[i][j], rings[i][(j + 1) % 6], rings[i + 1][(j + 1) % 6], rings[i + 1][j]]
            f = _mk(q, BONE, cm[j], ref)
            if f is not None:
                out.append(f)
    return out


def prism(mat, p0, p1, ra, rb=None, sides=6, caps=(True, True), phase=0.0, hint=(1, 0, 0), mats=None):
    """p0 から p1 への筒 (ra, rb は 2 点の配列も可)。"""
    return tube(BONE, mat, [V(*p0), V(*p1)], ra, rb, sides=sides, hint=hint, phase=phase, caps=caps, mats=mats)


def curve(mat, pts, ra, rb=None, sides=4, caps=(True, True), phase=45.0, hint=(0, 0, 1)):
    return tube(BONE, mat, [V(*p) for p in pts], ra, rb, sides=sides, hint=hint, phase=phase, caps=caps)


def extrude(mat, pts, thick, c=(0, 0, 0), rot=None, mats=None):
    """凸多角形 (xy 平面) を z 方向に厚み thick で押し出す。"""
    R = np.eye(3) if rot is None else rot
    c = np.asarray(c, float)
    h = thick / 2

    def P(x, y, z):
        return R @ np.array([x, y, z]) + c

    n = len(pts)
    out = []
    front = [P(x, y, -h) for x, y in pts]
    back = [P(x, y, h) for x, y in pts]
    for ring_, m in ((front, mat), (back[::-1], mat)):
        f = _mk(ring_, BONE, m, c)
        if f:
            out.append(f)
    for i in range(n):
        j = (i + 1) % n
        q = [front[i], front[j], back[j], back[i]]
        f = _mk(q, BONE, (mats[i % len(mats)] if mats else mat), c)
        if f:
            out.append(f)
    return out


def at(faces, R=None, t=(0, 0, 0)):
    return transform(faces, R, t)


def feather(mat, base, tip, w, th=0.6, mats=None, big=True):
    """羽: 先の尖った多角形の板。base から tip へ。広い面は ±Z (前後)。"""
    base, tip = V(*base), V(*tip)
    d = tip - base
    L = float(np.linalg.norm(d))
    if big:
        pts = [(0, 0), (w * 0.8, L * 0.25), (w, L * 0.6), (0, L), (-w, L * 0.6), (-w * 0.8, L * 0.25)]
    else:
        pts = [(0, 0), (w, L * 0.4), (0, L), (-w, L * 0.4)]
    up = V(0, 1, 0)
    ax = np.cross(up, d)
    if np.linalg.norm(ax) > 1e-9:
        a = nrm(ax)
        ang = math.atan2(np.linalg.norm(ax), np.dot(up, d))
        K = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
        R = np.eye(3) + math.sin(ang) * K + (1 - math.cos(ang)) * (K @ K)
    else:
        R = np.eye(3) if d[1] > 0 else rot_z(180)
    return extrude(mat, pts, th, c=base, rot=R, mats=mats)


def mirror_x(faces):
    out = []
    for f in faces:
        g = copy.deepcopy(f)
        g.verts = f.verts * np.array([-1, 1, 1])
        g.verts = g.verts[::-1].copy()
        g.ref = f.ref * np.array([-1, 1, 1])
        g.normal = f.normal * np.array([-1, 1, 1])
        g.loc = g.verts.copy()
        out.append(g)
    return out


def remat(faces, mat):
    for f in faces:
        f.mat = mat
    return faces


# ------------------------------------------------------------------ 絵

def pick(pal, idx):
    pal = np.asarray(pal)
    return pal[np.clip(idx, 0, len(pal) - 1)].astype(float)


def ramp(pal, v):
    pal = np.asarray(pal)
    return pal[np.clip(np.floor(v * len(pal)).astype(int), 0, len(pal) - 1)].astype(float)


LIGHT = nrm([-0.35, 0.85, -0.4])


def build(faces, tex=2.0):
    lpkit.TEX = tex
    faces = [f for f in faces if f is not None]
    size, P, N, M, L = build_atlas(faces, 32, 0)
    return faces, size, P, N, M, L


def save_img(arr, path):
    Image.fromarray(arr, "RGBA").save(path)


def export_mesh(faces, size, path, name, texture, glowfile, tex=2.0):
    verts, uvs, tris = [], [], []
    INSET = 0.02
    for f in faces:
        base = len(verts)
        cen = f.uv.mean(0)
        for p, uv in zip(f.verts, f.uv):
            d = cen - uv
            nn = np.linalg.norm(d)
            uv = uv + d / nn * min(INSET, nn * 0.4) if nn > 1e-9 else uv
            verts.append([round(float(x), 3) for x in p])
            uvs.append([round(float(uv[0]) / size, 5), round(float(uv[1]) / size, 5)])
        for a, c, d in f.tris():
            tris.append([base + a, base + c, base + d])
    data = {"format_version": 1, "name": name,
            "units": "1/16 block (item model units), y up, flat faces toward +-Z, front = -Z, blade upright, x=0 z=0 center",
            "uv": "normalized, origin top-left (v down)", "texture": texture, "glowmask": glowfile,
            "texture_size": [size, size], "texels_per_unit": tex, "uv_inset_texels": INSET,
            "triangle_count": len(tris), "bones": {"weapon": {"pivot": [0, 0, 0], "vertices": verts, "uvs": uvs,
                                                              "triangles": tris, "triangle_count": len(tris)}}}
    Path(path).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data
