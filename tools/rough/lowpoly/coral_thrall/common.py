"""珊瑚憑き (coral_thrall) 低ポリ ラフ 3 案の共通部分。src には触らない。

座標: 1 単位 = 1/16 ブロック。モデルは「前 = -Z」の向きで作る (ゲーム内 geo は root が Y 軸 180 度回っていて
cube は +Z 向きで焼いてあるため、書き出すとき (x, z) の符号を反転して geo の空間へ戻す)。
骨の名前・階層・pivot は geo/entity/coral_thrall.geo.json と同じ。forearm_r は休めの回転 (70,0,0)、
socket_main は (-150,0,0) を持ち、頂点は回転前の空間。銛は geo/gear/coral_spear.geo.json と同じ
局所座標 (socket_main の pivot が原点・先端が +Y) の別メッシュ。
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

SUBJ = "coral_thrall"
OUT = ROOT / "docs/review/lowpoly_roughs"
ASSETS = ROOT / "src/main/resources/assets/skyabyss/"
INSET = 0.02

# ---- geo と同じ骨 (geo 空間 = root 回転の前の座標)
BONES = {
    "root": (None, [0.0, 0.0, 0.0]),
    "body": ("root", [0.0, 14.0, 0.0]),
    "head": ("body", [0.0, 26.5, 0.0]),
    "arm_r": ("body", [-4.0, 24.0, 0.0]),
    "forearm_r": ("arm_r", [-3.75, 16.0, 0.5]),
    "socket_main": ("forearm_r", [-3.75, 11.5, 2.5]),
    "arm_l": ("body", [4.0, 24.0, 0.0]),
    "forearm_l": ("arm_l", [3.75, 16.0, 0.0]),
    "leg_r": ("root", [-2.0, 13.5, 0.0]),
    "leg_l": ("root", [2.0, 13.5, 0.0]),
    "statue_coral": ("body", [0.0, 14.0, 0.0]),
}
REST = {"forearm_r": (70.0, 0.0, 0.0), "socket_main": (-150.0, 0.0, 0.0)}
SOCKET = np.array(BONES["socket_main"][1])
SPEAR_BONES = {"spear": (None, [0.0, 0.0, 0.0])}


def V(*a):
    return np.array(a, float)


def flipxz(p):
    p = np.asarray(p, float)
    return p * np.array([-1.0, 1.0, -1.0])


def fpivot(bone):
    """作図用 (前 = -Z) の空間での骨の pivot。"""
    return flipxz(BONES[bone][1])


# ====================================================================== 姿勢

# attack の 0.45 秒 (突き) と 0.3 秒 (構え)。position は body の (0,-0.5,2.5)
THRUST = {"arm_r": (48.0, 0.0, 0.0), "forearm_r": (-60.0, 0.0, 0.0), "body": (16.0, 12.0, 0.0),
          "head": (-10.0, -8.0, 0.0), "arm_l": (-20.0, 0.0, 10.0)}
WINDUP = {"arm_r": (-28.0, 0.0, 0.0), "forearm_r": (36.0, 0.0, 0.0), "body": (-8.0, -14.0, 0.0),
          "head": (8.0, 10.0, 0.0), "arm_l": (14.0, 0.0, 6.0)}
WALK = {"leg_r": (-22 * 0.9, 0, 0), "leg_l": (22 * 0.9, 0, 0), "arm_l": (14, 0, 3), "arm_r": (-3, 0, 0), "body": (4, 4, 0)}
POS = {"body": (0.0, -0.5, 2.5)}


def rot_geo(rx, ry, rz):
    """GeckoLib の骨の回転 (度): 3 軸とも符号を反転した Z→Y→X。"""
    return rot_z(-rz) @ rot_y(-ry) @ rot_x(-rx)


def pose_point(p, bone, pose, bones=BONES, pos=None):
    """geo 空間の点 p (骨 bone の休めの姿勢) を、pose のときの geo 空間へ。root は含めない。"""
    pos = pos or {}
    while bone is not None and bone != "root":
        parent, pivot = bones[bone]
        pv = np.array(pivot, float)
        a = np.array(REST.get(bone, (0, 0, 0)), float) + np.array(pose.get(bone, (0, 0, 0)), float)
        p = rot_geo(*a) @ (p - pv) + pv + np.array(pos.get(bone, (0, 0, 0)), float)
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


def pose_body(faces, pose, pos=None):
    import copy
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = np.array([flipxz(pose_point(flipxz(p), f.bone, pose, pos=pos)) for p in f.verts])
        _refresh(g, f)
        out.append(g)
    return out


def pose_gear(faces, pose, pos=None):
    """銛 (socket_main の pivot が原点の局所座標) を pose の姿勢の作図空間へ。"""
    import copy
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = np.array([flipxz(pose_point(np.asarray(p, float) + SOCKET, "socket_main", pose, pos=pos)) for p in f.verts])
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


def paint(faces, size, P, N, M, pal, eyes=()):
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
        if sp.get("dots"):
            dot = (hash1(LU * 17 + LV * 31 + fi * 7, 11) < sp["dots"]) & INS & (D > 0.9)
            idx = idx + dot.astype(int) + (dot & (lit > 0.2)).astype(int)
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
    sk = pal.get("skull_bone", "head")
    hd = (bones == sk) & (mats == pal.get("face_mat", "bone")) & owned & (N[..., 2] < 0.5)
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
    for gmn in pal.get("glow_mats", []):
        gsel = (mats == gmn) & owned
        glow |= gsel
        col = np.where(gsel[..., None], np.clip(col * 1.15 + 16, 0, 255), col)
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
    for k, rv in REST.items():
        if k in out and flip:
            out[k]["rotation"] = list(rv)
    data = {"format_version": 1, "name": name, "units": "1/16 block, y up, front = -Z",
            "uv": "normalized, origin top-left (v down)", "texture": texture, "texture_size": [size, size],
            "texels_per_unit": TEX, "uv_inset_texels": INSET, "triangle_count": tot}
    if extra:
        data.update(extra)
    data["bones"] = out
    Path(path).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data



def build_option(key, build, pal, spear_pal=None, body_hint=128, spear_hint=64, extra_note="", statue_fn=None):
    """build() -> (body faces, spear faces, eyes)。書き出して dict を返す。"""
    body, spear, eyes = build()
    stat = statue_fn() if statue_fn else []
    body_n = body
    body = body + stat
    d = OUT / SUBJ / key
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = build_atlas(body, body_hint, 0)
    atlas, glow = paint(body, size, P, N, M, pal, eyes)
    pre = f"{SUBJ}_{key}"
    Image.fromarray(atlas, "RGBA").save(d / f"{pre}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"{pre}_glowmask.png")
    Image.fromarray(statue_atlas(atlas), "RGBA").save(d / f"{pre}_statue_atlas.png")
    data = export_mesh(body, size, d / f"{pre}_mesh.json", pre, f"{pre}_atlas.png", BONES,
                       {"glowmask": f"{pre}_glowmask.png", "statue_texture": f"{pre}_statue_atlas.png",
                        "triangle_count_normal": tri_count(body_n), "triangle_count_statue_only": tri_count(stat),
                        "note": "geo space (root carries rotation [0,180,0]); vertices are absolute like geo cubes, pre-rotation "
                                "under rotated bones (forearm_r rest rotation [70,0,0], socket_main [-150,0,0]). socket_main is an "
                                "empty bone (spear = separate mesh). bone statue_coral is shown only for the statue variant."
                                + extra_note})
    ssize, sP, sN, sM, sL = build_atlas(spear, spear_hint, 0)
    satlas, sglow = paint(spear, ssize, sP, sN, sM, spear_pal or pal, ())
    Image.fromarray(satlas, "RGBA").save(d / f"{pre}_spear_atlas.png")
    sg = np.zeros((ssize, ssize, 4), np.uint8)
    sg[sglow] = (255, 255, 255, 255)
    Image.fromarray(sg, "RGBA").save(d / f"{pre}_spear_glowmask.png")
    Image.fromarray(statue_atlas(satlas), "RGBA").save(d / f"{pre}_spear_statue_atlas.png")
    sdata = export_mesh(spear, ssize, d / f"{pre}_spear_mesh.json", f"{pre}_spear", f"{pre}_spear_atlas.png", SPEAR_BONES,
                        {"glowmask": f"{pre}_spear_glowmask.png", "statue_texture": f"{pre}_spear_statue_atlas.png",
                         "attach": {"socket_bone": "socket_main", "socket_pivot": [float(x) for x in SOCKET],
                                    "note": "vertices are in the socket bone's local space (origin = socket pivot), "
                                            "same as geo/gear/coral_spear.geo.json (tip toward +Y)"}},
                        flip=False)
    return dict(key=key, body=body_n, statue=stat, spear=spear, eyes=eyes, size=size, atlas=atlas, glow=glow, data=data,
                ssize=ssize, satlas=satlas, sglow=sglow, sdata=sdata)


def statue_atlas(atlas):
    """像の白い絵: 明るさを保ったまま、白い石の 5 階調に置き換える。"""
    ramp = np.array([(70, 74, 82), (120, 122, 128), (170, 170, 170), (206, 204, 198), (234, 232, 222)], float)
    rgb = atlas[..., :3].astype(float)
    lum = (rgb @ np.array([0.3, 0.55, 0.15])) / 255.0
    lum = np.clip((lum - 0.12) / 0.7, 0, 1)
    idx = np.clip(np.floor(lum * 5).astype(int), 0, 4)
    out = atlas.copy()
    out[..., :3] = ramp[idx].astype(np.uint8)
    return out


def combined_atlas(r):
    S, BS = r["size"], r["ssize"]
    side = max(S + BS, S)
    big = np.zeros((side, side, 4), np.uint8)
    big[:S, :S] = r["atlas"]
    big[S:S + BS, :BS] = r["satlas"]
    glow = np.zeros((side, side), bool)
    glow[:S, :S] = r["glow"]
    glow[S:S + BS, :BS] = r["sglow"]
    return big, glow, S


def spear_faces_shifted(r, yoff):
    import copy
    out = []
    for f in r["spear"]:
        g = copy.copy(f)
        g.uv = f.uv + np.array([0, yoff])
        out.append(g)
    return out


def render_pose(r, big, glow, yoff, pose, yaw, pitch, size, center, scale, body=True, spear=True, pos=None, statue=False):
    faces = []
    if body:
        faces += pose_body(r["body"] + (r["statue"] if statue else []), pose, pos)
    if spear:
        faces += pose_gear(spear_faces_shifted(r, yoff), pose, pos)
    return render(faces, big, glow, yaw, pitch, size, center, scale)


# ====================================================================== 部品

def limb(bone, mat, pts, ra, rb=None, sides=5, hint=(1, 0, 0), **kw):
    return tube(bone, mat, pts, ra, rb if rb is not None else ra, sides=sides, hint=hint, **kw)
