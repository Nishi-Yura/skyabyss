"""雷獣 (raiju) 低ポリ ラフ 3 案の共通部分。src には触らない。

座標: 1 単位 = 1/16 ブロック。モデルは「前 = -Z」の向きで作る (ゲーム内 geo は root が Y 軸 180 度回っていて
cube は +Z 向きで焼いてあるため、書き出すとき (x, z) の符号を反転して geo の空間へ戻す)。
骨の名前・階層・pivot は geo/entity/raiju.geo.json と同じ (17 骨、武器なし)。
"""
from __future__ import annotations

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
sys.path.insert(0, str(ROOT / "tools/rough"))
from lpkit import *  # noqa
from lpkit import _mk  # noqa
import voxel  # noqa

OUT = ROOT / "docs/review/lowpoly_roughs"
ASSETS = ROOT / "src/main/resources/assets/skyabyss/"
INSET = 0.02

# ---- geo と同じ骨 (geo 空間 = root 回転の前の座標。前は +Z)
def _load_bones():
    g = json.loads((ASSETS / "geo/entity/raiju.geo.json").read_text(encoding="utf-8"))
    out = {}
    for b in g["minecraft:geometry"][0]["bones"]:
        out[b["name"]] = (b.get("parent"), [float(x) for x in b["pivot"]])
    return out


BONES = _load_bones()
assert len(BONES) == 17, len(BONES)
REST = {}


def V(*a):
    return np.array(a, float)


def flipxz(p):
    p = np.asarray(p, float)
    return p * np.array([-1.0, 1.0, -1.0])


def fpivot(bone):
    """作図用 (前 = -Z) の空間での骨の pivot。"""
    return flipxz(BONES[bone][1])


# ====================================================================== 姿勢

def rot_geo(rx, ry, rz):
    """意図した角度 (度) → geo 空間の回転。X 正で前 (+Z) が下がる。ファイルの値は (-x, y, -z) で、GeckoLib の読みは
    すべて符号反転なので、結局 Z(z) Y(-y) X(x)。"""
    return rot_z(rz) @ rot_y(-ry) @ rot_x(rx)


def pose_point(p, bone, pose, bones=BONES):
    """geo 空間の点 p (骨 bone の休めの姿勢) を、pose のときの geo 空間へ。root は含めない。"""
    while bone is not None and bone != "root":
        parent, pivot = bones[bone]
        pv = np.array(pivot, float)
        a = np.array(REST.get(bone, (0, 0, 0)), float) + np.array(pose.get(bone, (0, 0, 0)), float)
        p = rot_geo(*a) @ (p - pv) + pv
        bone = parent
    return p


def _refresh(g, f):
    k = len(g.verts)
    n = np.cross(g.verts[2] - g.verts[0], g.verts[3 % k] - g.verts[1]) if k == 4 else \
        np.cross(g.verts[1] - g.verts[0], g.verts[2] - g.verts[0])
    n = nrm(n)
    if np.dot(n, f.normal) < 0:
        n = -n
    g.normal = n


def pose_body(faces, pose):
    import copy
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = np.array([flipxz(pose_point(flipxz(p), f.bone, pose)) for p in f.verts])
        _refresh(g, f)
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
PUPIL = np.array([(120, 205, 232), (205, 248, 255)])
DARK = np.array([(8, 8, 16), (18, 18, 28), (32, 32, 44)])


def paint(faces, size, P, N, M, pal, eyes=(), extra=None, L=None):
    """面ごとの材質 mat → pal["mats"][mat] (ramp 階調, base, lit, noise, crack, stain...) で描く。"""
    D, K, LU, LV, CW, CH, INS = edge_fields(faces, size)
    H = W = size
    yy, xx = np.mgrid[0:H, 0:W]
    fi = np.where(M >= 0, M, 0)
    owned = M >= 0
    mats = np.array([f.mat for f in faces])[fi]
    bones = np.array([f.bone for f in faces])[fi]
    bay = BAYER[yy % 4, xx % 4] - 0.5
    ny = N[..., 1]
    lit = N @ LIGHT
    area = (CW * CH).astype(float)
    edge = (D < 1.05) & INS
    convex = edge & (K > 24)
    concave = edge & (K < -24)
    near_concave = (D < 2.4) & (K < -24) & INS
    nverts = np.array([len(f.verts) for f in faces])[fi]
    col = np.zeros((H, W, 3))
    n_lo = vnoise(P * 0.16 + 3, 1)
    for name, sp in pal["mats"].items():
        sel = mats == name
        if not sel.any():
            continue
        ramp = np.array(sp["ramp"], float)
        nr = len(ramp)
        ao = sp.get("ao", 0.08) * np.clip((14 - P[..., 1]) / 14, 0, 1)
        v = sp.get("base", 0.72) + sp.get("lit", 0.13) * lit + sp.get("noise", 0.05) * (n_lo - 0.5) - ao + 0.03 * bay
        # 手前の面ごとの淡い違い (一枚ずつ違う骨の色)
        v = v + sp.get("facevar", 0.03) * (hash1(fi, 21) - 0.5)
        streak = (hash1(LU * 7 + fi * 13, 3) < sp.get("streak", 0.08)) & (area > 14)
        v = v - 0.09 * streak
        idx = np.floor(v * nr).astype(int)
        idx = idx + (convex & (lit > 0.0)) - (convex & (lit <= 0.0)) - concave
        idx = np.maximum(idx, sp.get("min", 1))
        if sp.get("stripe"):
            per = sp["stripe"]
            coord = LV if sp.get("axis", "v") == "v" else LU
            ln = (coord % per == 0) & INS
            idx = idx - ln.astype(int) + ((coord % per == 1) & INS & (lit > 0.1)).astype(int)
            idx = np.maximum(idx, 0)
        cp = sp.get("crack", 0.16)
        if cp > 0:
            crack_on = (hash1(fi, 5) < cp) & (area > 16) & (nverts <= 4)
            c0 = (hash1(fi, 6) * np.maximum(CW - 2, 1)).astype(int) + 1
            v0 = (hash1(fi, 7) * 0.35 * CH).astype(int)
            v1 = v0 + (CH * (0.35 + 0.35 * hash1(fi, 8))).astype(int)
            zig = np.round(0.62 * np.sin(LV * 0.7 + fi * 1.7)).astype(int)
            on_crack = crack_on & INS & (LV >= v0) & (LV <= v1) & (LU == c0 + zig)
            idx = np.where(on_crack, 1, idx)
        idx = np.clip(idx, 0, nr - 1)
        c = ramp[idx]
        st = sp.get("stain")
        if st is not None:
            sr = np.array(st["ramp"], float)
            mn = 0.62 * vnoise(P * 0.21 + 21, 4) + 0.38 * vnoise(P * 0.52 + 7, 9)
            ms = mn + st.get("up", 0.3) * np.clip(ny, 0, 1) + st.get("conc", 0.34) * near_concave + \
                st.get("low", 0.12) * (1 - np.clip(P[..., 1] / 34, 0, 1))
            thr = st.get("thr", 0.92)
            on = (ms + 0.06 * bay > thr) & (ny > -0.25)
            si = np.clip(np.floor((ms - thr) * 7 + 0.4 * lit + 0.6 * bay), 0, len(sr) - 1).astype(int)
            c = np.where(on[..., None], sr[si], c)
        col = np.where(sel[..., None], c, col)

    glow = np.zeros((H, W), bool)
    sk = pal.get("eye_bone", "eyes")
    hd = (bones == sk) & (mats == pal.get("face_mat", "eye")) & owned
    for e in eyes:
        c = np.array(e["c"], float)
        s = 1 if c[0] > 0 else -1
        dx = (P[..., 0] - c[0]) * s
        dy = P[..., 1] - c[1]
        dyy = dy - e.get("tilt", 0.3) * dx
        near = np.abs(P[..., 2] - c[2]) < e.get("zr", 3.0)
        r = np.sqrt((dx / e["rx"]) ** 2 + (dyy / e["ry"]) ** 2)
        sock = hd & near & (r + 0.06 * bay < 1.0)
        dk = np.clip(np.floor(2.9 - (1.0 - r) * 4.0 + 0.4 * bay).astype(int), 0, 2)
        col = np.where(sock[..., None], DARK[dk], col)
        pr = np.sqrt(((dx - e.get("px", 0.0)) / e["pr"][0]) ** 2 + ((dyy - e.get("py", 0.0)) / e["pr"][1]) ** 2)
        pup = hd & near & (pr + 0.05 * bay < 1.0)
        col = np.where(pup[..., None], np.where(pr[..., None] < 0.5, PUPIL[1], PUPIL[0]), col)
        glow |= pup
    for nz in pal.get("noses", []):
        c = np.array(nz, float)
        t = (P[..., 1] - (c[1] - 0.8)) / 1.6
        ax = np.abs(P[..., 0] - c[0])
        near = np.abs(P[..., 2] - c[2]) < 2.5
        core = hd & near & (ax < 0.5 * (1 - t) + 0.08) & (t > 0) & (t < 1.0)
        rim = hd & near & (ax < 0.5 * (1 - t) + 0.62) & (t > -0.3) & (t < 1.25) & ~core
        col = np.where(rim[..., None], DARK[2], col)
        col = np.where(core[..., None], DARK[0], col)
    for m in pal.get("glow_mats", []):
        glow |= (mats == m) & owned
    if extra:
        col, glow = extra(dict(faces=faces, P=P, N=N, M=M, fi=fi, mats=mats, bones=bones, INS=INS, D=D, K=K, LU=LU,
                               LV=LV, CW=CW, CH=CH, bay=bay, lit=lit, convex=convex, concave=concave, owned=owned,
                               area=area, ny=ny, L=L), col, glow)
    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl & owned


# ====================================================================== 書き出し

def export_mesh(faces, size, path, name, texture, bone_defs, extra=None, flip=True):
    bones = {b: {"vertices": [], "uvs": [], "triangles": []} for b in bone_defs}
    for f in faces:
        b = bones[f.bone]
        base = len(b["vertices"])
        cen = f.uv.mean(0)
        for p, uv in zip(f.verts, f.uv):
            d = cen - uv
            nn = np.linalg.norm(d)
            uv = uv + d / nn * min(INSET, nn * 0.4) if nn > 1e-9 else uv
            q = flipxz(p) if flip else p
            b["vertices"].append([round(float(x) + 0.0, 4) for x in q])
            b["uvs"].append([round(float(uv[0]) / size, 5), round(float(uv[1]) / size, 5)])
        for a, c, d in f.tris():
            b["triangles"].append([base + a, base + c, base + d])
    tot = 0
    out = {}
    for k, (parent, pivot) in bone_defs.items():
        b = bones[k]
        b["triangle_count"] = len(b["triangles"])
        tot += b["triangle_count"]
        out[k] = {"parent": parent, "pivot": pivot, **b}
    if "root" in out:
        out["root"]["rotation"] = [0.0, 180.0, 0.0]
    data = {"format_version": 1, "name": name, "units": "1/16 block, y up, front = -Z",
            "uv": "normalized, origin top-left (v down)", "texture": texture, "texture_size": [size, size],
            "texels_per_unit": TEX, "uv_inset_texels": INSET, "triangle_count": tot}
    if extra:
        data.update(extra)
    data["bones"] = out
    Path(path).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data


def build_option(key, build, pal, body_hint=256, extra_note=""):
    """build() -> (faces, eyes, extra_paint)。書き出して dict を返す。"""
    body, eyes, extra = build()
    d = OUT / "lp_raiju" / key
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = build_atlas(body, body_hint, 0)
    def ex(c, col, glow):
        if eyes:
            col, glow = eye_marks(c, col, glow, eyes)
        if extra:
            col, glow = extra(c, col, glow)
        return col, glow
    atlas, glow = paint(body, size, P, N, M, pal, (), ex, L)
    Image.fromarray(atlas, "RGBA").save(d / f"raiju_{key}_atlas.png")
    if glow.any():
        gm = np.zeros((size, size, 4), np.uint8)
        gm[glow] = (255, 255, 255, 255)
        Image.fromarray(gm, "RGBA").save(d / f"raiju_{key}_glowmask.png")
    data = export_mesh(body, size, d / f"raiju_{key}_mesh.json", f"raiju_{key}",
                       f"raiju_{key}_atlas.png", BONES,
                       {"glowmask": f"raiju_{key}_glowmask.png",
                        "note": "geo space (root rotated 180 deg about Y: root carries rotation [0,180,0]); "
                                "vertices are absolute like geo cubes; 17 bones = in-game raiju.geo.json; no gear"
                                + extra_note})
    return dict(key=key, body=body, eyes=eyes, size=size, atlas=atlas, glow=glow, data=data)


def render_pose(r, pose, yaw, pitch, size, center, scale):
    return render(pose_body(r["body"], pose), r["atlas"], r["glow"], yaw, pitch, size, center, scale)


def old_faces(pose=None, only=None):
    """今のモデル (箱)。pose は {骨: (rx, ry, rz)} (アニメの値)。"""
    pose = {k: (v[0], -v[1], v[2]) for k, v in (pose or {}).items()}
    eg = json.loads((ASSETS / "geo/entity/raiju.geo.json").read_text(encoding="utf-8"))
    n = sum(len(b.get("cubes", [])) for b in eg["minecraft:geometry"][0]["bones"])
    if only:
        for b in eg["minecraft:geometry"][0]["bones"]:
            if b["name"] not in only:
                b["cubes"] = []
    f = voxel.load_geo(eg, ASSETS / "textures/entity/raiju.png",
                       ASSETS / "textures/entity/raiju_glowmask.png", pose)
    return f, n


def eye_marks(c, col, glow, eyes, dark=DARK, pupil=PUPIL):
    """球の目の表面に、正面から見た形で黒目と光る瞳を描く。eyes = [{c, r, dir, a, b}] (作図空間)。"""
    P, N, bay, owned = c["P"], c["N"], c["bay"], c["owned"]
    for e in eyes:
        cen = np.array(e["c"], float)
        f = nrm(np.array(e.get("dir", (0, 0, -1)), float))
        up = np.array([0, 1.0, 0])
        rt = nrm(np.cross(up, f))
        up2 = np.cross(f, rt)
        d = P - cen
        df, dr, du = d @ f, d @ rt, d @ up2
        rad = e["r"]
        ok = owned & (np.linalg.norm(d, axis=-1) < rad * 1.5) & (df > rad * 0.15) & (c["bones"] == e.get("bone", "head"))
        tilt = e.get("tilt", 0.0)
        dun = du - tilt * np.abs(dr)
        rr = np.sqrt((dr / e["a"]) ** 2 + (dun / e["b"]) ** 2)
        sock = ok & (rr < 1.0 + 0.06 * bay)
        dk = np.clip(np.floor(2.9 - (1.0 - rr) * 3.2 + 0.4 * bay).astype(int), 0, 2)
        col = np.where(sock[..., None], dark[dk], col)
        pr = np.sqrt((dr / (e["a"] * 0.62)) ** 2 + (dun / (e["b"] * 0.5)) ** 2)
        pup = ok & (pr + 0.05 * bay < 1.0)
        col = np.where(pup[..., None], np.where(pr[..., None] < 0.5, pupil[1], pupil[0]), col)
        glow = glow | pup
    return col, glow
