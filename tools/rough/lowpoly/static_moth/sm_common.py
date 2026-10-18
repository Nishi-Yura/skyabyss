"""雷蛾 (static_moth) 低ポリ 3 案の共通部分。src には触らない。lpkit.py は import だけ。

作図の座標: 1 単位 = 1/16 ブロック、前 (頭) = -Z、翅は x 方向。ゲーム内 geo は root が Y 180 度回っていて
cube は +Z 向きで焼いてあるので、書き出すとき (x, z) の符号を反転して geo の空間へ戻す (泡吹きと同じ)。
骨は現行 geo と同じ: root > body > wing_l / wing_r / antennae。pivot は geo 空間の値。
wing_l は geo の +x 側 = 作図の -x 側。
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
sys.path.insert(0, str(LP))
sys.path.insert(0, str(LP / "bubble_puffer"))
sys.path.insert(0, str(ROOT / "tools/rough"))
from lpkit import *  # noqa
from lpkit import _mk  # noqa
import voxel  # noqa
from pf_common import (V, zring, frame_from_normal, dome, hash1, hash3, vnoise, paint, sphere_dir,  # noqa
                       ell_point, flipxz, BAYER)

SUBJECT = "lp_static_moth"
OUT = ROOT / "docs/review/lowpoly_roughs"
ASSETS = ROOT / "src/main/resources/assets/skyabyss/"
INSET = 0.02

# geo 空間の pivot (現行 geo と同じ)
BONES = {
    "root": (None, [0.0, 0.0, 0.0]),
    "body": ("root", [0.0, 6.0, 0.0]),
    "wing_l": ("body", [1.0, 7.2, 0.0]),
    "wing_r": ("body", [-1.0, 7.2, 0.0]),
    "antennae": ("body", [0.0, 8.2, 5.0]),
}
# 作図空間の pivot
PIV = {k: flipxz(v[1]) for k, v in BONES.items()}
SIDE = {"wing_r": 1.0, "wing_l": -1.0}      # 作図空間での x の符号


# ---------------------------------------------------------------- 部品

def wing(bone, rays, fr=(0.0, 0.55, 1.0), th=(0.7, 0.3), rise=0.8, fold=0.25, a0=1.1, lead=0.0,
         mats=("wtop", "wbot", "wrim")):
    """翅 1 枚。rays = [(phi度, 長さ)]。phi は +x 軸から前 (-Z) へ測る。放射状の格子を上面・下面・縁に張る。
    厚みは付け根 th[0] → 先 th[1]。fold は筋ごとに交互に上下して折り目の面を作る。"""
    side = SIDE[bone]
    piv = PIV[bone]
    J = len(rays)
    T, B = [], []
    for i, f in enumerate(fr):
        rt, rb = [], []
        for j, (phi, L) in enumerate(rays):
            ph = math.radians(phi)
            d = np.array([side * math.cos(ph), 0.0, -math.sin(ph)])
            r = a0 * (1 - f) + L * f
            y = piv[1] + rise * f + fold * f * (1 if j % 2 else -1) * (1.0 if 0 < j < J - 1 else 0.3) \
                + lead * f * max(0.0, math.sin(ph))
            t = th[0] * (1 - f) + th[1] * f
            p = np.array([piv[0], y, piv[2]]) + d * r
            rt.append(p + V(0, t / 2, 0))
            rb.append(p - V(0, t / 2, 0))
        T.append(rt)
        B.append(rb)
    F = []
    mt, mb, mr = mats
    cen = piv + V(side * 6, 0, 0)
    for i in range(len(fr) - 1):
        for j in range(J - 1):
            q = [T[i][j], T[i][j + 1], T[i + 1][j + 1], T[i + 1][j]]
            F += poly(bone, mt, q, np.mean(q, 0) - V(0, 4, 0))
            q = [B[i][j], B[i][j + 1], B[i + 1][j + 1], B[i + 1][j]]
            F += poly(bone, mb, q, np.mean(q, 0) + V(0, 4, 0))
    for j in (0, J - 1):
        for i in range(len(fr) - 1):
            q = [T[i][j], T[i + 1][j], B[i + 1][j], B[i][j]]
            F += poly(bone, mr, q, cen)
    for j in range(J - 1):
        q = [T[-1][j], T[-1][j + 1], B[-1][j + 1], B[-1][j]]
        F += poly(bone, mr, q, cen)
    return F


def wing_edge_points(bone, rays, fr_tip=1.0, rise=0.8, a0=2.4):
    side, piv = SIDE[bone], PIV[bone]
    out = []
    for phi, L in rays:
        ph = math.radians(phi)
        out.append(np.array([piv[0], piv[1] + rise, piv[2]]) + np.array([side * math.cos(ph), 0, -math.sin(ph)]) * L)
    return out


def bolt(bone, mat, p0, d, segs=3, seg=1.7, jit=1.0, r=0.28, seed=0, up=(0, 1, 0)):
    """ぎざぎざの小さな稲妻 (三角断面の細い筒)。"""
    rng = np.random.default_rng(seed)
    d = nrm(np.asarray(d, float))
    side = nrm(np.cross(d, up))
    pts = [np.asarray(p0, float)]
    for k in range(segs):
        off = side * jit * (1 if k % 2 == 0 else -1) * (0.7 + 0.5 * rng.random()) + V(0, 0.4 * (rng.random() - 0.5), 0)
        pts.append(pts[-1] + d * seg + off)
    ra = [r * (1 - 0.8 * k / segs) for k in range(segs)] + [0.0]
    return tube(bone, mat, pts, ra, sides=3, hint=up, phase=90, axis_fn=lambda p: pts[0])


def legs(mat="leg", y0=3.7, zs=(-2.6, -1.2, 0.4), x=1.1, bone="body", r=0.3, drop=3.0):
    F = []
    for s in (-1, 1):
        for k, z in enumerate(zs):
            a = V(s * x, y0 + 0.9, z)
            m = V(s * (x + 1.0), y0 - 0.3, z - 0.5 + 0.5 * k)
            b = V(s * (x + 1.5), y0 - drop + 0.5, z - 0.9 + 0.7 * k)
            F += tube(bone, mat, [a, m, b], [r, r * 0.8, 0.0], sides=3, hint=(0, 0, 1), phase=90,
                      axis_fn=lambda p, a=a: a)
    return F


def body_loft(profile, n=8, phase=0.0, mats=None):
    rings = [zring(z, rx, rt, rb, cy, n=n, phase=phase) for (z, rx, rt, rb, cy) in profile]
    return loft("body", "body", rings, caps=("body", "body"), mats=mats)


# ---------------------------------------------------------------- 絵 (翅の座標)

def wing_coords(ctx, rays):
    """翅のテクセルの局所座標 (中心からの距離 r、角度 phi、縁までの割合 u)。"""
    P = ctx.P
    X = np.abs(P[..., 0]) - 1.0
    Z = P[..., 2]
    r = np.hypot(X, Z)
    phi = np.degrees(np.arctan2(-Z, X))
    ph = np.array([a for a, _ in rays])
    Ls = np.array([b for _, b in rays])
    order = np.argsort(ph)
    Lint = np.interp(phi, ph[order], Ls[order])
    u = r / np.maximum(Lint, 1e-3)
    return X, Z, r, phi, u


def arc_dist(r, phi, phis):
    d = np.full(r.shape, 99.0)
    for p in phis:
        d = np.minimum(d, r * np.radians(np.abs(phi - p)))
    return d


# ---------------------------------------------------------------- 書き出し

def export_mesh(faces, size, path, name, texture, extra=None):
    bones = {b: {"vertices": [], "uvs": [], "triangles": []} for b in BONES}
    for f in faces:
        b = bones[f.bone]
        base = len(b["vertices"])
        cen = f.uv.mean(0)
        for p, uv in zip(f.verts, f.uv):
            d = cen - uv
            nn = np.linalg.norm(d)
            uv = uv + d / nn * min(INSET, nn * 0.4) if nn > 1e-9 else uv
            q = flipxz(p)
            b["vertices"].append([round(float(x) + 0.0, 4) for x in q])
            b["uvs"].append([round(float(uv[0]) / size, 5), round(float(uv[1]) / size, 5)])
        for a, c, d in f.tris():
            b["triangles"].append([base + a, base + c, base + d])
    tot = 0
    out = {}
    for k, (parent, pivot) in BONES.items():
        b = bones[k]
        b["triangle_count"] = len(b["triangles"])
        tot += b["triangle_count"]
        out[k] = {"parent": parent, "pivot": pivot, **b}
    out["root"]["rotation"] = [0.0, 180.0, 0.0]
    data = {"format_version": 1, "name": name, "units": "1/16 block, y up, front = -Z",
            "uv": "normalized, origin top-left (v down)", "texture": texture, "texture_size": [size, size],
            "texels_per_unit": TEX, "uv_inset_texels": INSET, "triangle_count": tot}
    if extra:
        data.update(extra)
    data["bones"] = out
    Path(path).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data


def build_option(key, mod, hint=128):
    faces = mod.build()
    d = OUT / "lp_static_moth" / key
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = build_atlas(faces, hint, 0)
    atlas, glow = paint(faces, size, P, N, M, mod.PAL, mod.decor)
    stem = f"lp_static_moth_{key}"
    Image.fromarray(atlas, "RGBA").save(d / f"{stem}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"{stem}_glowmask.png")
    data = export_mesh(faces, size, d / f"{stem}_mesh.json", stem, f"{stem}_atlas.png",
                       {"glowmask": f"{stem}_glowmask.png",
                        "note": "geo space (root carries rotation [0,180,0]); vertices are absolute like geo cubes; "
                                "wing_l/wing_r/antennae are children of body; wings are double-sided plates (no-cull)."})
    return dict(key=key, faces=faces, size=size, atlas=atlas, glow=glow, data=data)


def pose_faces(faces, flap=0.0, ant=0.0):
    """翅を flap 度 (先が上がる向き) 回した姿勢 (作図空間)。"""
    if not flap and not ant:
        return faces
    out = []
    for f in faces:
        g = copy.copy(f)
        if f.bone in SIDE and flap:
            piv = PIV[f.bone]
            R = rot_z(flap * SIDE[f.bone])
            g.verts = (np.asarray(f.verts) - piv) @ R.T + piv
            g.ref = R @ (np.asarray(f.ref) - piv) + piv
            g.normal = R @ f.normal
        out.append(g)
    return out


def render_pose(r, yaw, pitch, size, center, scale, flap=0.0):
    return render(pose_faces(r["faces"], flap), r["atlas"], r["glow"], yaw, pitch, size, center, scale)


# ---------------------------------------------------------------- 共通の頭・目

HC = V(0, 6.3, -4.8)     # 頭の中心
HR = (1.75, 1.65, 1.75)


def eyes(mat="eye", r=1.25, depth=0.95, theta=40, phi=6, sides=7):
    F, E = [], []
    for s in (-1, 1):
        d = sphere_dir(s * theta, phi)
        p, nn = ell_point(HC, HR, d)
        E.append(dict(c=p + nn * 0.1, n=nn, r=r))
        F += dome("body", mat, p, nn, r, depth, sides=sides, mid=0.72, phase=22.5)
    return F, E


def eye_paint(ctx, col, glow, E, ramp_iris, ramp_pupil, glow_r=0.62, mat="eye"):
    sel = ctx.mats == mat
    for e in E:
        rel = ctx.P - e["c"]
        ax = np.einsum("...k,k->...", rel, e["n"])
        perp = np.linalg.norm(rel - ax[..., None] * e["n"], axis=-1)
        near = sel & (np.linalg.norm(rel, axis=-1) < e["r"] * 2.2)
        col = np.where(near[..., None], np.array(ramp_iris, float), col)
        pup = near & (perp < e["r"] * glow_r)
        col = np.where(pup[..., None], np.array(ramp_pupil, float), col)
        glow = glow | pup
    return col, glow
