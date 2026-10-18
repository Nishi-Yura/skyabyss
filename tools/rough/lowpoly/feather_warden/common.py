"""羽守 (feather_warden) 低ポリ ラフ 3 案の共通部分。src には触らない。

座標: 1 単位 = 1/16 ブロック。作図は「前 = -Z」の向き。ゲーム内 geo は root が Y 軸 180 度回っていて cube は
+Z 向きで焼いてあるため、書き出すとき (x, z) の符号を反転して geo の空間へ戻す。
骨の名前・階層・pivot は geo/entity/feather_warden.geo.json と同じ。鉤槍は geo/gear/hooked_glaive.geo.json と同じ
局所座標 (socket_main の pivot が原点・柄は y 方向) の別メッシュ。
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
sys.path.insert(0, str(ROOT / "tools/rough"))
from lpkit import *  # noqa
from lpkit import _mk  # noqa
import voxel  # noqa

OUT = ROOT / "docs/review/lowpoly_roughs"
ASSETS = ROOT / "src/main/resources/assets/skyabyss/"
INSET = 0.02

BONES = {
    "root": (None, [0.0, 0.0, 0.0]),
    "body": ("root", [0.0, 13.0, 0.0]),
    "head": ("body", [0.0, 25.0, 0.0]),
    "cloak_l": ("body", [0.5, 24.5, -3.0]),
    "cloak_r": ("body", [-0.5, 24.5, -3.0]),
    "arm_r": ("body", [-4.5, 24.0, 0.0]),
    "forearm_r": ("arm_r", [-6.6, 17.5, 1.5]),
    "socket_main": ("forearm_r", [-6.6, 12.4, 4.3]),
    "arm_l": ("body", [4.5, 24.0, 0.0]),
    "forearm_l": ("arm_l", [6.6, 17.5, 0.5]),
    "leg_r": ("body", [-2.25, 13.0, 0.0]),
    "leg_l": ("body", [2.25, 13.0, 0.0]),
}
REST = {"socket_main": (-15.0, 0.0, 0.0)}
SOCKET = np.array(BONES["socket_main"][1])
GEAR_BONES = {"glaive": (None, [0.0, 0.0, 0.0])}


def V(*a):
    return np.array(a, float)


def flipxz(p):
    return np.asarray(p, float) * np.array([-1.0, 1.0, -1.0])


# ====================================================================== 姿勢 (アニメの値は Blockbench 流の度)

DIVE = {"body": (40, 0, 0), "head": (-34, 0, 0), "arm_r": (-95, 0, 0), "socket_main": (150, 0, 0),
        "arm_l": (-90, 0, 0), "cloak_r": (0, 0, -80), "cloak_l": (0, 0, 80), "leg_r": (30, 0, 0), "leg_l": (24, 0, 0)}
CROUCH = {"body": (-28, 0, 0), "leg_r": (46, 0, -6), "leg_l": (40, 0, 6), "head": (22, 0, 0),
          "cloak_r": (0, 0, 82), "cloak_l": (0, 0, -82), "arm_r": (60, 0, 10), "socket_main": (-105, 0, 0),
          "arm_l": (-20, 0, 30)}


def rot_geo(rx, ry, rz):
    return rot_z(-rz) @ rot_y(-ry) @ rot_x(-rx)


def pose_point(p, bone, pose, bones=BONES):
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
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = np.array([flipxz(pose_point(flipxz(p), f.bone, pose)) for p in f.verts])
        _refresh(g, f)
        out.append(g)
    return out


def pose_gear(faces, pose):
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = np.array([flipxz(pose_point(np.asarray(p, float) + SOCKET, "socket_main", pose)) for p in f.verts])
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
VOID = np.array([(4, 6, 12), (10, 13, 24), (20, 26, 42)])
SPARK = np.array([(120, 190, 240), (190, 232, 255), (250, 252, 255)])


def paint(faces, size, P, N, M, pal, eyes=()):
    """pal["mats"][mat]: ramp, base, lit, noise, kind ('feather'|'plate'|None), stain, glow."""
    D, K, LU, LV, CW, CH, INS = edge_fields(faces, size)
    H = W = size
    yy, xx = np.mgrid[0:H, 0:W]
    fi = np.where(M >= 0, M, 0)
    owned = M >= 0
    mats = np.array([f.mat for f in faces])[fi]
    bay = BAYER[yy % 4, xx % 4] - 0.5
    ny = N[..., 1]
    lit = N @ LIGHT
    area = (CW * CH).astype(float)
    edge = (D < 1.05) & INS
    convex = edge & (K > 24)
    concave = edge & (K < -24)
    near_concave = (D < 2.4) & (K < -24) & INS
    AX = np.array([getattr(f, "axis", None) if getattr(f, "axis", None) is not None else [0, 1, 0] for f in faces], float)[fi]
    ORG = np.array([getattr(f, "org", f.verts[0]) for f in faces], float)[fi]
    FL = np.array([getattr(f, "flen", 6.0) for f in faces], float)[fi]
    rel = P - ORG
    tpos = (rel * AX).sum(-1)
    perp = np.cross(N, AX)
    perp = perp / np.maximum(np.linalg.norm(perp, axis=-1, keepdims=True), 1e-6)
    spos = (rel * perp).sum(-1)
    col = np.zeros((H, W, 3))
    glow = np.zeros((H, W), bool)
    n_lo = vnoise(P * 0.16 + 3, 1)
    for name, sp in pal["mats"].items():
        sel = mats == name
        if not sel.any():
            continue
        ramp = np.array(sp["ramp"], float)
        nr = len(ramp)
        ao = sp.get("ao", 0.08) * np.clip((14 - P[..., 1]) / 14, 0, 1)
        v = sp.get("base", 0.7) + sp.get("lit", 0.13) * lit + sp.get("noise", 0.05) * (n_lo - 0.5) - ao + 0.03 * bay
        v = v + sp.get("facevar", 0.03) * (hash1(fi, 21) - 0.5)
        idx = np.floor(v * nr).astype(int)
        idx = idx + (convex & (lit > 0.0)) - (convex & (lit <= 0.0)) - concave
        kind = sp.get("kind")
        if kind == "feather":
            tt = tpos * TEX
            ss = np.abs(spos) * TEX
            bw = sp.get('bw', 0.45)
            band = np.floor(tt * bw - ss * bw * 0.9).astype(int) % 2 == 0
            idx = idx - (band & INS & (area > 10) & (np.abs(spos) * TEX > 1.6)).astype(int)
            rach = ss < sp.get("rach", 0.9)
            idx = np.where(rach & INS & (area > 8), idx + 2, idx)
            if sp.get("tip", True):
                idx = np.where((tpos > FL * 0.86) & INS, idx - 1, idx)
            fv = sp.get("fvar", 0)
            idx = idx + ((hash1(fi, 11) > 0.8).astype(int) - (hash1(fi, 12) > 0.8).astype(int)) * fv
        if kind == "plate":
            seam = (LV % sp.get("per", 7) == 0) & INS & (area > 24)
            idx = idx - seam
            riv = (LU % 6 == 2) & (LV % 6 == 2) & INS & (area > 40) & (D > 1.5)
            idx = idx + riv * 2
        if sp.get("stripe"):
            per = sp["stripe"]
            coord = LV if sp.get("axis", "v") == "v" else LU
            ln = (coord % per == 0) & INS
            idx = idx - ln.astype(int) + ((coord % per == 1) & INS & (lit > 0.1)).astype(int)
        idx = np.clip(np.maximum(idx, sp.get("min", 0)), 0, nr - 1)
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
        if sp.get("glow"):
            glow |= sel & owned
    vs = (mats == "void") & owned
    dk = np.clip(np.floor(1.4 + 0.9 * (lit + 0.2) + 0.7 * bay), 0, 2).astype(int)
    col = np.where(vs[..., None], VOID[dk], col)
    for e in eyes:
        c = np.array(e["c"], float)
        s = 1 if c[0] > 0 else -1
        dx = (P[..., 0] - c[0]) * s
        dy = P[..., 1] - c[1]
        near = np.abs(P[..., 2] - c[2]) < e.get("zr", 2.0)
        r = np.sqrt((dx / e["rx"]) ** 2 + (dy / e["ry"]) ** 2)
        pup = vs & near & (r + 0.05 * bay < 1.0)
        col = np.where(pup[..., None], np.where(r[..., None] < 0.45, SPARK[2], SPARK[1]), col)
        glow |= pup
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
        if flip and k in REST:
            out[k]["rotation"] = list(REST[k])
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


def build_option(key, build, pal, gear_pal=None, body_hint=128, gear_hint=64, write=True):
    body, gear, eyes = build()
    d = OUT / "lp_feather_warden" / key
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = build_atlas(body, body_hint, 0)
    atlas, glow = paint(body, size, P, N, M, pal, eyes)
    gsize, gP, gN, gM, gL = build_atlas(gear, gear_hint, 0)
    gatlas, gglow = paint(gear, gsize, gP, gN, gM, gear_pal or pal, ())
    data = gdata = None
    if write:
        Image.fromarray(atlas, "RGBA").save(d / f"feather_warden_{key}_atlas.png")
        gm = np.zeros((size, size, 4), np.uint8)
        gm[glow] = (255, 255, 255, 255)
        Image.fromarray(gm, "RGBA").save(d / f"feather_warden_{key}_glowmask.png")
        data = export_mesh(body, size, d / f"feather_warden_{key}_mesh.json", f"feather_warden_{key}",
                           f"feather_warden_{key}_atlas.png", BONES,
                           {"glowmask": f"feather_warden_{key}_glowmask.png",
                            "note": "geo space (root bone carries rotation [0,180,0]); vertices are absolute like geo cubes; "
                                    "socket_main keeps its rest rotation [-15,0,0] and is an empty bone (spear = separate mesh)."})
        Image.fromarray(gatlas, "RGBA").save(d / f"feather_warden_{key}_spear_atlas.png")
        extra = {"attach": {"socket_bone": "socket_main", "socket_pivot": [float(x) for x in SOCKET],
                            "note": "vertices are in the socket bone's local space (origin = socket pivot), "
                                    "same as geo/gear/hooked_glaive.geo.json (shaft along +y)"}}
        if gglow.any():
            ggm = np.zeros((gsize, gsize, 4), np.uint8)
            ggm[gglow] = (255, 255, 255, 255)
            Image.fromarray(ggm, "RGBA").save(d / f"feather_warden_{key}_spear_glowmask.png")
            extra["glowmask"] = f"feather_warden_{key}_spear_glowmask.png"
        gdata = export_mesh(gear, gsize, d / f"feather_warden_{key}_spear_mesh.json", f"feather_warden_{key}_spear",
                            f"feather_warden_{key}_spear_atlas.png", GEAR_BONES, extra, flip=False)
    return dict(key=key, body=body, gear=gear, eyes=eyes, size=size, atlas=atlas, glow=glow, data=data,
                gsize=gsize, gatlas=gatlas, gglow=gglow, gdata=gdata,
                tris=tri_count(body), gtris=tri_count(gear))


def combined_atlas(r):
    S, GS = r["size"], r["gsize"]
    side = S + GS
    big = np.zeros((side, side, 4), np.uint8)
    big[:S, :S] = r["atlas"]
    big[S:S + GS, :GS] = r["gatlas"]
    glow = np.zeros((side, side), bool)
    glow[:S, :S] = r["glow"]
    glow[S:S + GS, :GS] = r["gglow"]
    return big, glow, S


def gear_shifted(r, yoff):
    out = []
    for f in r["gear"]:
        g = copy.copy(f)
        g.uv = f.uv + np.array([0, yoff])
        out.append(g)
    return out


def render_pose(r, big, glow, yoff, pose, yaw, pitch, size, center, scale, body=True, gear=True):
    faces = []
    if body:
        faces += pose_body(r["body"], pose)
    if gear:
        faces += pose_gear(gear_shifted(r, yoff), pose)
    return render(faces, big, glow, yaw, pitch, size, center, scale)


# ====================================================================== 部品

def feather(bone, mat, base, tip, width, ref, fold=0.5, fold_up=None, taper=0.35, n_mid=0.45, bow=0.0):
    """羽 1 枚: 軸 base→tip を持つ菱形 (根元が細く真ん中が太く先が尖る)。軸で折れた 4 三角形。
    fold: 軸の山の高さ (面の外向き法線方向)、bow: 軸の中央の曲がり (法線方向)。"""
    base, tip, ref = (np.asarray(x, float) for x in (base, tip, ref))
    ax = tip - base
    L = np.linalg.norm(ax)
    a = ax / L
    up = np.asarray(fold_up, float) if fold_up is not None else nrm(ref - (base + tip) / 2) * -1
    # ref の側が外: up を ref 方向へ
    out_dir = nrm(ref - (base + tip) / 2)
    side = nrm(np.cross(a, out_dir))
    if np.linalg.norm(side) < 1e-6:
        side = nrm(np.cross(a, np.array([0, 0, 1.0])))
    nrm_dir = nrm(np.cross(side, a))
    if np.dot(nrm_dir, out_dir) < 0:
        nrm_dir = -nrm_dir
    mid = base + a * L * n_mid + nrm_dir * bow
    pl = mid + side * width / 2
    pr = mid - side * width / 2
    b0 = base + side * width * taper * 0.3
    b1 = base - side * width * taper * 0.3
    ridge_m = mid + nrm_dir * fold
    ridge_t = tip + nrm_dir * bow * 0.6
    faces = []
    for tri in ([b0, pl, ridge_m], [b1, ridge_m, pr], [ridge_m, pl, ridge_t], [ridge_m, ridge_t, pr]):
        f = _mk(tri, bone, mat, ref)
        if f is not None:
            f.axis = a
            f.org = base
            f.flen = L
            faces.append(f)
    return faces


def shingle(bone, mat, top, down, out, w, h, flare, ref=None, tip_frac=1.0):
    """鎧羽のうろこ 1 枚 (五角形)。top = 上辺の中心、down = 下向き、out = 外向き、flare = 先端の浮き。"""
    top, down, out = np.asarray(top, float), nrm(down), nrm(out)
    right = nrm(np.cross(down, out))
    mid = top + down * h * 0.5 + out * flare * 0.45
    tip = top + down * h * tip_frac + out * flare
    pts = [top + right * w / 2, mid + right * w / 2 * 1.04, tip, mid - right * w / 2 * 1.04, top - right * w / 2]
    f = _mk(pts, bone, mat, ref if ref is not None else top - out * 3)
    if f is None:
        return []
    f.axis = down
    f.org = top
    f.flen = h
    return [f]


def torso_pt(y, rx, rz, theta_deg, zc=0.0):
    """胴の断面 (前 = -Z) の表面の点。theta 0 = 正面、90 = 右 (+x)。"""
    a = math.radians(theta_deg)
    return V(rx * math.sin(a), y, zc - rz * math.cos(a))


def torso_n(theta_deg):
    a = math.radians(theta_deg)
    return V(math.sin(a), 0, -math.cos(a))


def loft2(bone, rings, matfn, caps=(None, None)):
    rings = [np.array(r, float) for r in rings]
    cen = [r.mean(0) for r in rings]
    k = len(rings[0])
    faces = []
    for i in range(len(rings) - 1):
        ref = (cen[i] + cen[i + 1]) / 2
        for j in range(k):
            quad = [rings[i][j], rings[i][(j + 1) % k], rings[i + 1][(j + 1) % k], rings[i + 1][j]]
            f = _mk(quad, bone, matfn(i, j), ref)
            if f is not None:
                faces.append(f)
    for idx, cap in zip((0, len(rings) - 1), caps):
        if cap:
            other = cen[1] if idx == 0 else cen[-2]
            f = _mk(list(rings[idx]), bone, cap, other)
            if f is not None:
                faces.append(f)
    return faces


def surf(rings, y, k):
    """輪の列 rings (同じ点数 n) の表面の点と外向きの法線。y は高さ、k は輪の頂点の添字 (小数可。0 = 正面)。"""
    rings = [np.asarray(r, float) for r in rings]
    n = len(rings[0])
    ys = [r[0][1] for r in rings]
    i = 0
    while i < len(rings) - 2 and y > ys[i + 1]:
        i += 1
    t = (y - ys[i]) / (ys[i + 1] - ys[i])
    k0 = int(math.floor(k)) % n
    k1 = (k0 + 1) % n
    u = k - math.floor(k)

    def at(ri):
        r = rings[ri]
        return r[k0] * (1 - u) + r[k1] * u

    p = at(i) * (1 - t) + at(i + 1) * t
    c0 = rings[i].mean(0) * (1 - t) + rings[i + 1].mean(0) * t
    o = p - c0
    o[1] = 0
    return p, nrm(o)
