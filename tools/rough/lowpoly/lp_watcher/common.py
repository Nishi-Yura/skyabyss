"""見る者 (watcher) 低ポリ 3 案の共通部分。src には触らない。

座標: 1 単位 = 1/16 ブロック。作図は「前 = -Z」の向き。ゲーム内 geo は root が Y 軸 180 度回っていて cube は +Z 向きで
焼いてあるため、書き出すとき (x, z) の符号を反転して geo の空間へ戻す。
骨の名前・階層・pivot は geo/entity/watcher.geo.json と同じ (7 骨: root > body > hem / mantle / head / mist、head > eyes)。
休めの回転があるのは root の Y 180 度だけ。アニメは idle 1 本 (body・hem・mantle の微小な揺れと mist の上下)。
本体の描画は entityTranslucent (半透明・裏面も描く) なので、閉じた塊は裏の壁が透けて見える。内側の面は意図したものだけにする。
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
LP = HERE.parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(LP))
sys.path.insert(0, str(ROOT / "tools/rough"))
from lpkit import *  # noqa
from lpkit import _mk  # noqa
import lpkit  # noqa
import voxel  # noqa

OUT = ROOT / "docs/review/lowpoly_roughs"
ASSETS = ROOT / "src/main/resources/assets/skyabyss/"
INSET = 0.02
SUBJ = "lp_watcher"
NAME = "watcher"
ALPHA = 0.72          # 描画の半透明の濃さ (ゲーム内は WatcherEntity.alpha で 0〜1。通常は 1 に近い)


def _load_bones():
    g = json.loads((ASSETS / "geo/entity/watcher.geo.json").read_text(encoding="utf-8"))
    out = {}
    for b in g["minecraft:geometry"][0]["bones"]:
        out[b["name"]] = (b.get("parent"), [float(x) for x in b["pivot"]])
    return out


BONES = _load_bones()
assert list(BONES) == ["root", "body", "hem", "mantle", "head", "mist", "eyes"], list(BONES)


def V(*a):
    return np.array(a, float)


def flipxz(p):
    return np.asarray(p, float) * np.array([-1.0, 1.0, -1.0])


def lerp(a, b, t):
    return np.asarray(a, float) * (1 - t) + np.asarray(b, float) * t


def hash1(a, salt=0):
    q = np.sin(np.asarray(a, float) * 12.9898 + salt * 78.233) * 43758.5453
    return q - np.floor(q)


def hash2(x, y, seed=0):
    q = np.sin(np.asarray(x, float) * 127.1 + np.asarray(y, float) * 311.7 + seed * 17.13) * 43758.5453
    return q - np.floor(q)


def vnoise2(x, y, seed=0):
    xi, yi = np.floor(x), np.floor(y)
    fx, fy = x - xi, y - yi
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = hash2(xi, yi, seed), hash2(xi + 1, yi, seed)
    c, d = hash2(xi, yi + 1, seed), hash2(xi + 1, yi + 1, seed)
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


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


# ====================================================================== 形

def oring(y, rx, zf, zb, n=8, phase=22.5, zc=0.0, cx=0.0, angs=None):
    """前 (-Z) が角度 0 の楕円の輪。angs (度) を渡すとその角度に頂点を置く。"""
    pts = []
    for k in range(n if angs is None else len(angs)):
        a = math.radians(phase + 360.0 * k / n) if angs is None else math.radians(angs[k])
        s, c = math.sin(a), math.cos(a)
        pts.append(np.array([cx + rx * s, y, zc + (zf * c if c >= 0 else zb * -c)]))
    return pts


def loft_skip(bone, mat, rings, skip=None, caps=(False, False), mats=None):
    """lpkit.loft と同じだが、skip(i, j) が真の四角を作らない (切れ目・窓)。"""
    rings = [np.array(r, float) for r in rings]
    cen = [r.mean(0) for r in rings]
    k = len(rings[0])
    faces = []
    for i in range(len(rings) - 1):
        ref = (cen[i] + cen[i + 1]) / 2
        for j in range(k):
            if skip and skip(i, j):
                continue
            quad = [rings[i][j], rings[i][(j + 1) % k], rings[i + 1][(j + 1) % k], rings[i + 1][j]]
            f = _mk(quad, bone, (mats[i] if mats else mat), ref)
            if f is not None:
                faces.append(f)
    for idx, cap in zip((0, len(rings) - 1), caps):
        if cap:
            other = cen[1] if idx == 0 else cen[-2]
            f = _mk(list(rings[idx]), bone, cap if isinstance(cap, str) else mat, other)
            if f is not None:
                faces.append(f)
    return faces


def flipf(faces):
    """面の向きを反転 (内向きの面にする)。"""
    for f in faces:
        f.verts = f.verts[::-1].copy()
        f.normal = -f.normal
        f.loc = f.verts
    return faces


def inset_ring(r, d):
    """輪を中心軸へ d だけ縮める (x, z のみ。高さは変えない)。"""
    r = np.array(r, float)
    c = r.mean(0)
    out = []
    for p in r:
        v = np.array([p[0] - c[0], 0.0, p[2] - c[2]])
        L = np.linalg.norm(v)
        q = p.copy()
        if L > 1e-9:
            q[0] -= v[0] / L * min(d, L * 0.8)
            q[2] -= v[2] / L * min(d, L * 0.8)
        out.append(q)
    return out


def rim_strip(bone, mat, ring_o, ring_i, ref, a, b):
    """外の輪と内の輪の、頂点 a→b の辺のあいだの帯 (切れ目や口の縁)。ref の反対側が表。"""
    quad = [ring_o[a], ring_o[b], ring_i[b], ring_i[a]]
    f = _mk(quad, bone, mat, ref)
    return [f] if f else []


def tetra(bone, mat, c, r, rot=0.0, stretch=1.0):
    c = np.asarray(c, float)
    a = math.radians(rot)
    ps = []
    for k in range(3):
        t = a + 2 * math.pi * k / 3
        ps.append(c + np.array([r * math.cos(t), -r * 0.45 * stretch, r * math.sin(t)]))
    ps.append(c + np.array([0, r * 1.1 * stretch, 0]))
    fs = []
    for tri in ((0, 1, 2), (0, 1, 3), (1, 2, 3), (2, 0, 3)):
        f = _mk([ps[i] for i in tri], bone, mat, c)
        if f:
            fs.append(f)
    return fs


def octa(bone, mat, c, rx, ry, rz):
    c = np.asarray(c, float)
    ps = [c + V(rx, 0, 0), c + V(0, 0, rz), c + V(-rx, 0, 0), c + V(0, 0, -rz)]
    top, bot = c + V(0, ry, 0), c - V(0, ry, 0)
    fs = []
    for k in range(4):
        for apex in (top, bot):
            f = _mk([ps[k], ps[(k + 1) % 4], apex], bone, mat, c)
            if f:
                fs.append(f)
    return fs


# ====================================================================== 絵

def paint(faces, size, P, N, M, pal, extra=None):
    """面ごとの材質 mat → pal["mats"][mat] (ramp 階調) で描く。pal["glow_mats"] は光る画素。
    extra(ctx, col, glow, alpha) -> (col, glow, alpha) で手描きの絵・穴 (alpha 0)・ほつれを足す。"""
    D, K, LU, LV, CW, CH, INS = edge_fields(faces, size)
    H = W = size
    yy, xx = np.mgrid[0:H, 0:W]
    fi = np.where(M >= 0, M, 0)
    owned = M >= 0
    mats = np.array([f.mat for f in faces])[fi]
    bones = np.array([f.bone for f in faces])[fi]
    bay = BAYER[yy % 4, xx % 4] - 0.5
    lit = N @ LIGHT
    area = (CW * CH).astype(float)
    edge = (D < 1.05) & INS
    convex = edge & (K > 24)
    concave = edge & (K < -24)
    col = np.zeros((H, W, 3))
    n_lo = vnoise(P * 0.18 + 3, 1)
    for name, sp in pal["mats"].items():
        sel = mats == name
        if not sel.any():
            continue
        ramp = np.array(sp["ramp"], float)
        nr = len(ramp)
        v = sp.get("base", 0.6) + sp.get("lit", 0.2) * lit + sp.get("noise", 0.06) * (n_lo - 0.5) + 0.04 * bay
        v = v + sp.get("facevar", 0.03) * (hash1(fi, 21) - 0.5)
        v = v + sp.get("up", 0.0) * np.clip(N[..., 1], 0, 1) - sp.get("low", 0.0) * np.clip((14 - P[..., 1]) / 14, 0, 1)
        idx = np.floor(v * nr).astype(int)
        idx = idx + (convex & (lit > 0.0)) - (convex & (lit <= 0.0)) - concave
        if sp.get("stripe"):
            per = sp["stripe"]
            ln = (LU % per == 0) & INS
            idx = idx - ln.astype(int) + ((LU % per == 1) & INS & (lit > 0.1)).astype(int)
        idx = np.clip(np.maximum(idx, sp.get("min", 0)), 0, nr - 1)
        col = np.where(sel[..., None], ramp[idx], col)
    glow = np.zeros((H, W), bool)
    for m in pal.get("glow_mats", []):
        glow |= (mats == m) & owned
    alpha = np.where(owned, 255, 0).astype(np.uint8)
    if extra:
        col, glow, alpha = extra(dict(faces=faces, P=P, N=N, M=M, fi=fi, mats=mats, bones=bones, INS=INS, D=D, K=K,
                                      LU=LU, LV=LV, CW=CW, CH=CH, bay=bay, lit=lit, convex=convex, concave=concave,
                                      owned=owned, area=area, ny=N[..., 1]), col, glow, alpha)
    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    a_f = fill_outside(np.stack([alpha] * 3, -1), faces, INS, M)[..., 0]
    img[..., 3] = np.where(owned, a_f, 0)
    gl = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl & owned & (img[..., 3] > 0)


def fit_atlas(faces, hint=96, step=16):
    size = hint
    while True:
        r = build_atlas(faces, size, 0)
        if r[0] == size:
            return r
        size += step


# ====================================================================== 書き出し

def export_mesh(faces, size, path, key, texture, glowmask):
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
    tot, out = 0, {}
    for k, (parent, pivot) in BONES.items():
        b = bones[k]
        b["triangle_count"] = len(b["triangles"])
        tot += b["triangle_count"]
        out[k] = {"parent": parent, "pivot": pivot, **b}
    out["root"]["rotation"] = [0.0, 180.0, 0.0]
    data = {"format_version": 1, "name": f"{NAME}_{key}", "units": "1/16 block, y up, front = -Z",
            "uv": "normalized, origin top-left (v down)", "texture": texture, "glowmask": glowmask,
            "texture_size": [size, size], "texels_per_unit": TEX, "uv_inset_texels": INSET, "triangle_count": tot,
            "render": "entityTranslucent (no cull); texture alpha 0 texels are holes",
            "note": "geo space (root carries rotation [0,180,0]); vertices are absolute like geo cubes; 7 bones = in-game watcher.geo.json",
            "bones": out}
    Path(path).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data


def build_option(key, build, hint=96):
    b = build()
    faces = b["faces"]
    d = OUT / SUBJ / key
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = fit_atlas(faces, hint)
    atlas, glow = paint(faces, size, P, N, M, b["pal"], b.get("extra"))
    Image.fromarray(atlas, "RGBA").save(d / f"{NAME}_{key}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"{NAME}_{key}_glowmask.png")
    data = export_mesh(faces, size, d / f"{NAME}_{key}_mesh.json", key, f"{NAME}_{key}_atlas.png", f"{NAME}_{key}_glowmask.png")
    return dict(key=key, faces=faces, size=size, atlas=atlas, glow=glow, data=data)


# ====================================================================== 半透明の描画 (entityTranslucent の近似)

def render_t(faces, atlas, glow, yaw, pitch, size, center, scale, alpha=ALPHA, ss=2, light=1.0,
             glow_col=(190, 225, 255), bloom=0.55):
    """裏面も描く (no cull)。奥から手前へ面を並べ、テクセルの alpha (穴) を見て、alpha でブレンドする。
    戻り値: RGBA (非乗算)。light: 面の明るさの倍率 (夜は小さく)。光る画素は明るさそのまま。"""
    W, H = size[0] * ss, size[1] * ss
    Vm = rot_x(pitch) @ rot_y(yaw)
    cam_dir = Vm.T @ np.array([0, 0, 1.0])
    cen = Vm @ np.asarray(center, float)
    sc = scale * ss
    S = atlas.shape[0]
    pm = np.zeros((H, W, 3))
    A = np.zeros((H, W))
    gb = np.zeros((H, W))
    order = sorted(range(len(faces)), key=lambda i: (faces[i].verts @ Vm.T)[:, 2].mean())
    for fi in order:
        f = faces[fi]
        cv = f.verts @ Vm.T
        sx = (cv[:, 0] - cen[0]) * sc + W / 2
        sy = -(cv[:, 1] - cen[1]) * sc + H / 2
        n = f.normal
        nv = n if np.dot(n, cam_dir) >= 0 else -n
        sh = shade_of(nv, cam_dir) * light
        for (a, b, c) in f.tris():
            xs, ys = sx[[a, b, c]], sy[[a, b, c]]
            x0, x1 = int(max(0, math.floor(xs.min()))), int(min(W - 1, math.ceil(xs.max())))
            y0, y1 = int(max(0, math.floor(ys.min()))), int(min(H - 1, math.ceil(ys.max())))
            if x1 < x0 or y1 < y0:
                continue
            den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(den) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
            l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
            l2 = 1 - l0 - l1
            inside = (l0 >= -1e-9) & (l1 >= -1e-9) & (l2 >= -1e-9)
            if not inside.any():
                continue
            u = l0 * f.uv[a, 0] + l1 * f.uv[b, 0] + l2 * f.uv[c, 0]
            v = l0 * f.uv[a, 1] + l1 * f.uv[b, 1] + l2 * f.uv[c, 1]
            ui = np.clip(np.floor(u).astype(int), 0, S - 1)
            vi = np.clip(np.floor(v).astype(int), 0, S - 1)
            tex = atlas[vi, ui]
            g = glow[vi, ui]
            m = inside & (tex[..., 3] > 127)
            if not m.any():
                continue
            colr = np.where(g[..., None], tex[..., :3].astype(float), tex[..., :3].astype(float) * sh)
            a_ = np.where(g, np.maximum(alpha, 0.9), alpha)[..., None]
            sub = pm[y0:y1 + 1, x0:x1 + 1]
            sa = A[y0:y1 + 1, x0:x1 + 1]
            mm = m[..., None]
            sub[:] = np.where(mm, sub * (1 - a_) + colr * a_, sub)
            sa[:] = np.where(m, sa * (1 - a_[..., 0]) + a_[..., 0], sa)
            gsub = gb[y0:y1 + 1, x0:x1 + 1]
            gsub[:] = np.where(m, g.astype(float), gsub)
    if gb.any() and bloom > 0:
        gm = np.array(Image.fromarray((gb * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2.6 * ss)), float) / 255.0
        add = gm[..., None] * np.array(glow_col, float) * bloom
        pm = pm + add * 0.5 * (1 - A[..., None] * 0.5)
        A = np.maximum(A, np.clip(gm * 0.55, 0, 1))
    pm = np.clip(pm, 0, 255)
    pm = pm.reshape(size[1], ss, size[0], ss, 3).mean((1, 3))
    A = A.reshape(size[1], ss, size[0], ss).mean((1, 3))
    rgb = np.where(A[..., None] > 1e-4, pm / np.maximum(A[..., None], 1e-4), 0)
    rgba = np.concatenate([np.clip(rgb, 0, 255), (A * 255)[..., None]], -1)
    return Image.fromarray(rgba.astype(np.uint8), "RGBA")


def tri_by_bone(faces):
    out = {}
    for f in faces:
        out[f.bone] = out.get(f.bone, 0) + len(f.verts) - 2
    return out
