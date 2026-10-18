"""泡吹き (bubble_puffer) 低ポリ ラフ 3 案の共通部分。src には触らない。

座標: 1 単位 = 1/16 ブロック。作図は「前 = -Z」。ゲーム内 geo は root が Y 180 度回っていて cube は +Z 向きで
焼いてあるので、書き出すとき (x, z) の符号を反転して geo の空間へ戻す。
骨の名前・階層・pivot は geo/entity/bubble_puffer.geo.json と同じ (すべて body の子。pivot は geo 空間)。
"""
from __future__ import annotations

import copy
import json
import math
import sys
from pathlib import Path
from types import SimpleNamespace

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
LP = HERE.parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(LP))
sys.path.insert(0, str(ROOT / "tools/rough"))
from lpkit import *  # noqa
from lpkit import _mk  # noqa
import voxel  # noqa

NAME = "lp_bubble_puffer"
OUT = ROOT / "docs/review/lowpoly_roughs"
ASSETS = ROOT / "src/main/resources/assets/skyabyss/"
INSET = 0.02

BONES = {
    "root": (None, [0.0, 0.0, 0.0]),
    "body": ("root", [0.0, 6.5, 0.0]),
    "spines": ("body", [0.0, 6.5, 0.0]),
    "fin_r": ("body", [-4.5, 5.25, 1.0]),
    "fin_l": ("body", [4.5, 5.25, 1.0]),
    "tail": ("body", [0.0, 6.5, -6.0]),
}
CEN = np.array([0.0, 6.5, 0.0])      # body / spines の pivot (作図空間でも x, z = 0 なので同じ)
INFLATE_BODY, INFLATE_SPINES = 1.45, 1.12     # animation.bubble_puffer.inflated


def V(*a):
    return np.array(a, float)


def flipxz(p):
    p = np.asarray(p, float)
    return p * np.array([-1.0, 1.0, -1.0])


# ====================================================================== 部品

def zring(z, rx, rt, rb, cy, n=10, cx=0.0, phase=0.0):
    """z 一定の輪 (前 = -Z へ向かって積む)。頂点 0 は真上。rt = 上半分の高さ半径、rb = 下半分。"""
    pts = []
    for k in range(n):
        a = math.radians(phase + 360.0 * k / n)
        s, c = math.sin(a), math.cos(a)
        pts.append(np.array([cx + rx * s, cy + (rt * c if c >= 0 else rb * c), z]))
    return pts


def sphere_dir(theta, phi):
    """theta: 前 (-Z) からの水平角 (度, +x 側が正)、phi: 仰角 (度)。"""
    t, p = math.radians(theta), math.radians(phi)
    return np.array([math.cos(p) * math.sin(t), math.sin(p), -math.cos(p) * math.cos(t)])


def ell_point(c, r, d):
    """中心 c、半径 r の楕円体の、方向 d の表面の点と外向き法線。"""
    c, r, d = np.asarray(c, float), np.asarray(r, float), np.asarray(d, float)
    q = d / np.linalg.norm(d / r) if False else d
    k = 1.0 / math.sqrt(sum((d[i] / r[i]) ** 2 for i in range(3)))
    p = c + d * k
    n = nrm((p - c) / (r * r))
    return p, n


def quad(bone, mat, a, b, c, d, ref):
    return poly(bone, mat, [a, b, c, d], ref)


def tri(bone, mat, a, b, c, ref):
    return poly(bone, mat, [a, b, c], ref)


def fan_membrane(bone, mat, base, tips, ref, scoop=0.0):
    """付け根の線 base[i] と先端 tips[i] の対応で作る扇の膜 (四角の板を並べる)。"""
    F = []
    for i in range(len(tips) - 1):
        F += poly(bone, mat, [base[i], base[i + 1], tips[i + 1], tips[i]], ref)
    return F


# ====================================================================== 姿勢 (膨らませる)

def inflate_faces(faces, kb=INFLATE_BODY, ks=INFLATE_SPINES):
    """inflated の姿勢: body を kb 倍 (子の spines / fin / tail もついてくる)、spines はさらに ks 倍。"""
    out = []
    for f in faces:
        g = copy.copy(f)
        k = kb * (ks if f.bone == "spines" else 1.0)
        g.verts = CEN + (np.asarray(f.verts) - CEN) * k
        g.ref = CEN + (np.asarray(f.ref) - CEN) * k
        out.append(g)
    return out


# ====================================================================== 絵

def hash1(a, salt=0):
    q = np.sin(np.asarray(a, float) * 12.9898 + salt * 78.233) * 43758.5453
    return q - np.floor(q)


def hash3(p, seed=0):
    q = (p * np.array([127.1, 311.7, 74.7]) + seed * 17.13).sum(-1)
    return np.modf(np.sin(q) * 43758.5453)[0] % 1.0


def vnoise(p, seed=0):
    i = np.floor(p)
    f = p - i
    f = f * f * (3 - 2 * f)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[..., 0] if dx else 1 - f[..., 0]) * (f[..., 1] if dy else 1 - f[..., 1]) * \
                    (f[..., 2] if dz else 1 - f[..., 2])
                out = out + w * hash3(i + np.array([dx, dy, dz]), seed)
    return out


BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0
LIGHT = nrm(np.array([-0.35, 0.85, -0.4]))


def paint(faces, size, P, N, M, pal, decor=None):
    """面ごとの材質 mat → pal["mats"][mat] のランプで描き、decor(ctx, col, glow) で位置による模様・目などを重ねる。"""
    D, K, LU, LV, CW, CH, INS = edge_fields(faces, size)
    H = W = size
    yy, xx = np.mgrid[0:H, 0:W]
    fi = np.where(M >= 0, M, 0)
    owned = M >= 0
    mats = np.array([f.mat for f in faces])[fi]
    bones = np.array([f.bone for f in faces])[fi]
    bay = BAYER[yy % 4, xx % 4] - 0.5
    lit = N @ LIGHT
    edge = (D < 1.05) & INS
    convex = edge & (K > 24)
    concave = edge & (K < -24)
    n_lo = vnoise(P * 0.16 + 3, 1)
    ctx = SimpleNamespace(P=P, N=N, M=M, fi=fi, owned=owned, mats=mats, bones=bones, bay=bay, lit=lit, edge=edge,
                          convex=convex, concave=concave, D=D, K=K, LU=LU, LV=LV, CW=CW, CH=CH, INS=INS,
                          faces=faces, size=size, yy=yy, xx=xx, ny=N[..., 1], n_lo=n_lo)

    def ramp_color(ramp, base=0.72, litk=0.13, noise=0.05, facevar=0.03, minidx=1, edges=True):
        ramp = np.array(ramp, float)
        nr = len(ramp)
        v = base + litk * lit + noise * (n_lo - 0.5) + 0.03 * bay + facevar * (hash1(fi, 21) - 0.5)
        idx = np.floor(v * nr).astype(int)
        if edges:
            idx = idx + (convex & (lit > 0.0)) - (convex & (lit <= 0.0)) - concave
        idx = np.clip(np.maximum(idx, minidx), 0, nr - 1)
        return ramp[idx]

    ctx.ramp_color = ramp_color
    col = np.zeros((H, W, 3))
    for name, sp in pal["mats"].items():
        sel = mats == name
        if not sel.any():
            continue
        kw = {k: v for k, v in sp.items() if k in ("base", "litk", "noise", "facevar", "minidx", "edges")}
        col = np.where(sel[..., None], ramp_color(sp["ramp"], **kw), col)
    glow = np.zeros((H, W), bool)
    if decor is not None:
        col, glow = decor(ctx, col, glow)
    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl & owned


# ====================================================================== 書き出し

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
    d = OUT / "lp_bubble_puffer" / key
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = build_atlas(faces, hint, 0)
    atlas, glow = paint(faces, size, P, N, M, mod.PAL, mod.decor)
    stem = f"lp_bubble_puffer_{key}"
    Image.fromarray(atlas, "RGBA").save(d / f"{stem}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"{stem}_glowmask.png")
    data = export_mesh(faces, size, d / f"{stem}_mesh.json", stem, f"{stem}_atlas.png",
                       {"glowmask": f"{stem}_glowmask.png",
                        "note": "geo space (root rotated 180 deg about Y: the root bone carries rotation [0,180,0]); "
                                "vertices are absolute like geo cubes. fin_r / fin_l / tail / spines are children of body."})
    return dict(key=key, faces=faces, size=size, atlas=atlas, glow=glow, data=data)


def render_pose(r, yaw, pitch, size, center, scale, inflated=False):
    faces = inflate_faces(r["faces"]) if inflated else r["faces"]
    return render(faces, r["atlas"], r["glow"], yaw, pitch, size, center, scale)


def frame_from_normal(n, up=(0, 1, 0)):
    """局所 -Z が n に向く回転 (列 = 局所 x, y, z の向き)。"""
    n = nrm(n)
    zl = -n
    up = np.asarray(up, float)
    if abs(np.dot(nrm(up), zl)) > 0.97:
        up = np.array([0.0, 0, 1.0])
    yl = nrm(up - np.dot(up, zl) * zl)
    xl = np.cross(yl, zl)
    return np.stack([xl, yl, zl], 1)


def dome(bone, mat, c, n, r, depth, sides=7, mid=0.7, ry=None, up=(0, 1, 0), mid_depth=0.55, phase=0.0, base_in=0.3):
    """表面の点 c に法線 n で付く、面の見える小さな丸屋根 (目・こぶ)。
    根元の輪は少し内側 (base_in) に入れて隙間を作らない。"""
    n = nrm(n)
    R = frame_from_normal(n, up)
    ry = r if ry is None else ry
    ring0 = [R @ np.array([r * math.cos(math.radians(phase + 360 * k / sides)),
                           ry * math.sin(math.radians(phase + 360 * k / sides)), base_in]) + c for k in range(sides)]
    ring1 = [R @ np.array([mid * r * math.cos(math.radians(phase + 360 * k / sides)),
                           mid * ry * math.sin(math.radians(phase + 360 * k / sides)), -depth * mid_depth]) + c
             for k in range(sides)]
    apex = R @ np.array([0, 0, -depth]) + c
    F = []
    for k in range(sides):
        k2 = (k + 1) % sides
        F += poly(bone, mat, [ring0[k], ring0[k2], ring1[k2], ring1[k]], c)
        F += poly(bone, mat, [ring1[k], ring1[k2], apex], c)
    return F
