"""雷翼の心臓 (storm_heart) 低ポリ案の共通部品。ラフ。src には触らない。

座標は 1 単位 = 1/16 ブロック、y が上、正面は -Z。骨の名前・pivot は
src/main/resources/assets/skyabyss/geo/entity/storm_heart_p{1,2,3}.geo.json から読む
(どの骨も親なし・休めの回転なし)。頂点はモデル全体の絶対座標。
"""
from __future__ import annotations

import json
import math
import sys
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
OUTDIR = ROOT / "docs/review/lowpoly_roughs/lp_storm_heart"
SUBJ = "storm_heart"

HC = {1: np.array([0.0, 17.0, 0.5]), 2: np.array([0.0, 21.0, 0.0]), 3: np.array([0.0, 17.0, 0.0])}
HR = {1: (7.0, 6.0, 4.7), 2: (7.0, 6.0, 5.0), 3: (7.0, 6.0, 5.0)}
CORE = {1: np.array([0.0, 18.0, -4.0]), 2: np.array([0.0, 22.0, -4.25]), 3: np.array([0.0, 18.0, -4.25])}
CORE_R = {1: 2.0, 2: 2.3, 3: 2.8}


def V(*a):
    return np.array(a, float)


def rad(a):
    return math.radians(a)


def smooth(a, b, x):
    t = min(max((x - a) / (b - a), 0.0), 1.0)
    return t * t * (3 - 2 * t)


def rj(i, amp=1.0, seed=1):
    return (hash1(i, seed) - 0.5) * 2 * amp


# ------------------------------------------------------------------ 骨 (今の geo から)

def load_geo(phase):
    return json.loads((GEO / f"{SUBJ}_p{phase}.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]


def bone_defs(phase):
    g = load_geo(phase)
    return {b["name"]: (b.get("parent"), [float(x) for x in b["pivot"]], [float(x) for x in b.get("rotation", [0, 0, 0])])
            for b in g["bones"]}


def present_bones(phase):
    return {b["name"] for b in load_geo(phase)["bones"] if b.get("cubes")}


def cube_counts(phase):
    return sum(len(b.get("cubes", [])) for b in load_geo(phase)["bones"])


def cubes(phase, bone):
    for b in load_geo(phase)["bones"]:
        if b["name"] == bone:
            return [(np.array(c["origin"], float) + np.array(c["size"], float) / 2, np.array(c["size"], float))
                    for c in b.get("cubes", [])]
    return []


def clusters(phase, bone, r=5.0):
    cs = [c for c, _ in cubes(phase, bone)]
    out = []
    for c in cs:
        for o in out:
            if np.linalg.norm(o[0] / o[1] - c) < r:
                o[0] += c
                o[1] += 1
                break
        else:
            out.append([c.copy(), 1])
    return sorted([(o[0] / o[1], o[1]) for o in out], key=lambda x: (x[0][0], x[0][2]))


# ------------------------------------------------------------------ 曲線

def curve(ctrl, n):
    """制御点 (Catmull-Rom) を通る n+1 点。"""
    c = [np.asarray(p, float) for p in ctrl]
    c = [c[0] * 2 - c[1]] + c + [c[-1] * 2 - c[-2]]
    out = []
    m = len(c) - 3
    for i in range(n + 1):
        u = i / n * m
        k = min(int(u), m - 1)
        t = u - k
        p0, p1, p2, p3 = c[k], c[k + 1], c[k + 2], c[k + 3]
        out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    return out


# ------------------------------------------------------------------ 羽根 (平らな菱形断面の板)

def _key(p):
    return tuple(np.round(p, 4))


def feather(bone, mat, pts, wr, wl, hint, thick=0.45, keel=0.6):
    """中心線 pts (n+1 点) に沿う羽根。wr/wl は点ごとの右・左の幅 (最後は 0 = 尖る)。
    断面は 4 点 (上の稜・右縁・下の稜・左縁)。loc = (根元からの割合 t, 幅方向 s)。"""
    n = len(pts)
    rings, locs = [], []
    for i, p in enumerate(pts):
        h = np.asarray(hint(p) if callable(hint) else hint, float)
        t = nrm(pts[min(i + 1, n - 1)] - pts[max(i - 1, 0)])
        nn = h - np.dot(h, t) * t
        if np.linalg.norm(nn) < 1e-6:
            nn = np.array([1.0, 0, 0]) - np.dot([1.0, 0, 0], t) * t
        nn = nrm(nn)
        sd = nrm(np.cross(t, nn))
        tk = thick * (1.0 if i < n - 1 else 0.0)
        u = i / (n - 1)
        rings.append([p + nn * tk, p + sd * wr[i], p - nn * tk * keel, p - sd * wl[i]])
        locs.append([(u, 0.0, 0), (u, 1.0, 0), (u, 0.0, 0), (u, -1.0, 0)])
    lm = {}
    for r_, l_ in zip(rings, locs):
        for a, b in zip(r_, l_):
            lm[_key(a)] = b
    F = []
    for i in range(n - 1):
        ref = (pts[i] + pts[i + 1]) / 2
        for k in range(4):
            q = [rings[i][k], rings[i][(k + 1) % 4], rings[i + 1][(k + 1) % 4], rings[i + 1][k]]
            for f in poly(bone, mat, q, ref):
                f.loc = np.array([lm[_key(v)] for v in f.verts], float)
                F.append(f)
    return F


def blade(bone, mat, root, ctrl_pts, W, hint, n=5, notch=0.0, seed=0, thick=0.45, peak=0.38, tipn=1.0, asym=0.0):
    """root から ctrl_pts (root を含まない) を通る羽根。W は最大幅、notch は切れ込みの深さ (0〜1)。"""
    pts = curve([root] + list(ctrl_pts), n)
    wr, wl = [], []
    for i in range(n + 1):
        u = i / n
        prof = (math.sin(math.pi * min(1.0, u / peak / 2.0)) ** 0.7 if u < peak else (1 - ((u - peak) / (1 - peak)) ** 1.5)) if u > 0 else 0.35
        prof = max(prof, 0.0) if i < n else 0.0
        a = W * (0.4 + 0.6 * prof) if i < n else 0.0
        if i == 0:
            a = W * 0.45
        ra = a * (1 + asym)
        la = a * (1 - asym)
        if notch > 0 and 0.45 < u < 1.0 and i < n:
            side = hash1(i * 7 + seed, 3 + seed % 5) < 0.5
            cut = notch * (0.5 + 0.5 * hash1(i + seed, 9))
            if i % 2 == 1:
                if side:
                    ra *= 1 - cut
                else:
                    la *= 1 - cut
            else:
                if side:
                    la *= 1 - cut * 0.5
                else:
                    ra *= 1 - cut * 0.5
        wr.append(ra * (1 if i < n else 0))
        wl.append(la * (1 if i < n else 0))
    return feather(bone, mat, pts, wr, wl, hint, thick=thick)


def rod(bone, mat, pts, r, sides=4, hint=(0, 0, 1), phase=45.0, taper=False, caps=(False, False)):
    n = len(pts)
    rs = [r * (1 - 0.85 * i / (n - 1)) if taper else r for i in range(n)]
    if taper:
        rs[-1] = 0.0
    return tube(bone, mat, pts, rs, rs, sides=sides, hint=hint, phase=phase, caps=caps)


def node(bone, mat, c, r, rot=0.0):
    return ellipsoid(bone, mat, c, (r, r * 1.15, r), segs=4, rings=1, rot=rot_y(rot))


# ------------------------------------------------------------------ 心臓

def heart_ball(bone, mat, phase, segs=12, rings=5, notch=1.0, jit=0.05, seam=0.0, scale=1.0, seed=3):
    """丸い心臓 (上に小さな切れ込み、下は少し尖る)。seam: 割れ目の溝の深さ (経線ごとに板に割れる)。"""
    c = HC[phase]
    rx, ry, rz = [x * scale for x in HR[phase]]

    def deform(p):
        x, y, z = p
        k = math.sin(x * 2.9 + y * 1.7 + seed) * math.cos(z * 2.3 - y * 1.1 + seed * 2) * jit
        x, y, z = x * (1 + k), y * (1 + k), z * (1 + k)
        if y > 0:
            y -= notch * math.exp(-(x / 2.3) ** 2) * smooth(0.5, 4.0, y)
        else:
            q = -y / ry
            x *= 1 - 0.15 * q
            y -= 0.9 * math.exp(-(x / 2.6) ** 2) * q
        if seam:
            a = math.atan2(x, -z)
            s = abs(((a / (2 * math.pi)) * 6.0) % 1.0 - 0.5) * 2.0   # 0 = 縫い目
            f = 1 - seam * max(0.0, 1 - s * 3.0) * (math.hypot(x, z) / max(rx, 1e-6))
            x, z = x * f, z * f
        return V(x, y, z)

    return ellipsoid(bone, mat, c, (rx, ry, rz), segs=segs, rings=rings, deform=deform, phase=0.0)


def hsurf(phase, th, ph, lift=0.0, scale=1.0):
    """th: 核の極 (-Z) からの角度、ph: 方位 (0 = +x、反時計回り) の心臓の表面の点。"""
    c = HC[phase]
    t, f = rad(th), rad(ph)
    d = V(math.sin(t) * math.cos(f), math.sin(t) * math.sin(f), -math.cos(t))
    p = d * np.array(HR[phase]) * scale
    return c + p + nrm(p) * lift


def core_sphere(phase, mat="core", segs=6, rings=2, k=1.0):
    return ellipsoid("core", mat, CORE[phase], (CORE_R[phase] * k,) * 3, segs=segs, rings=rings, phase=30.0)


# ------------------------------------------------------------------ 絵

ARC = np.array([(36, 54, 118), (72, 112, 204), (142, 192, 255), (216, 238, 255), (255, 255, 255)])


def ramp(pal, v):
    pal = np.asarray(pal)
    return pal[np.clip(np.floor(v * len(pal)).astype(int), 0, len(pal) - 1)].astype(float)


def pick(pal, idx):
    pal = np.asarray(pal)
    return pal[np.clip(idx, 0, len(pal) - 1)].astype(float)


LIGHT = np.array([-0.35, 0.85, -0.4])
LIGHT = LIGHT / np.linalg.norm(LIGHT)


def paint(faces, size, P, N, M, L, ctx, phase):
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
    col = np.zeros((H, W, 3))
    glow = np.zeros((H, W), bool)
    n_lo = vnoise(P * 0.16 + 3, 1)
    n_mid = vnoise(P * 0.45 + 11, 2)
    X, Y, Z = P[..., 0], P[..., 1], P[..., 2]
    t, s = L[..., 0], L[..., 1]
    ph = phase - 1

    def put(mask, c):
        nonlocal col
        col = np.where(mask[..., None], c, col)

    def stone(pal, base, lw=0.30, nw=0.14, extra=0.0):
        ns = len(pal)
        v = base + lw * lit + nw * (n_lo - 0.5) + 0.10 * (n_mid - 0.5) + 0.07 * bay + extra
        idx = np.floor(v * ns).astype(int) + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave
        return pick(pal, idx)

    def scars(f, w, wf=0.5):
        a = vnoise(P * f + 7, 3)
        b = vnoise(P * f * 2.1 + 17, 5)
        warp = vnoise(P * f * 0.6 + 41, 6) - 0.5
        a2 = vnoise(P * f + warp[..., None] * 3.0 + 29, 4)
        return (np.abs(a2 - 0.5) < w) | ((np.abs(b - 0.5) < w * wf) & (a > 0.52))

    # ---- 石の心臓
    st = stone(ctx["stone"], ctx.get("stone_base", 0.5))
    put(mats == "stone", st)
    stone_like = mats == "stone"
    sc_w = ctx["scar_w"][ph]
    if sc_w > 0:
        cr = scars(ctx.get("scar_f", 0.32), sc_w) & stone_like & (area > 6)
        gv = 0.60 + 0.3 * (1 - np.abs(vnoise(P * 0.32 + 7, 3) - 0.5) / max(sc_w, 1e-6)) + 0.1 * bay
        put(cr, ramp(ARC, 0.72 + 0.25 * (gv - 0.6) + 0.06 * bay))
        glow |= cr
    # ---- 羽根の板
    nb, skew = ctx.get("barb_n", 9.0), ctx.get("barb_skew", 2.2)
    q = t * nb + np.abs(s) * skew
    stripe = (np.floor(q * 2.0) % 2) - 0.5
    vane_v = ctx.get("vane_base", 0.5) + 0.30 * lit + 0.10 * (n_lo - 0.5) + 0.12 * stripe + 0.07 * bay \
        + 0.10 * np.clip((np.abs(s) - 0.7) * 3, 0, 1) + ctx.get("tip_light", 0.18) * np.clip((t - 0.7) * 3.3, 0, 1)
    vidx = np.floor(vane_v * len(ctx["vane"])).astype(int) + (convex & (lit > 0)) - concave
    c_v = pick(ctx["vane"], vidx)
    put(mats == "vane", c_v)
    vane = mats == "vane"
    # 軸 (銅)
    shaft = vane & (np.abs(s) < ctx.get("shaft_w", 0.13))
    cu_idx = np.floor(1.6 + 2.4 * (0.5 + 0.5 * lit) + 0.7 * bay + 0.6 * (n_mid - 0.5)).astype(int)
    put(shaft, pick(ctx["copper"], cu_idx))
    # 羽根の傷 (光る割れ) と先の灯り
    vsc = ctx["vane_scar"][ph]
    if vsc > 0:
        cr = scars(ctx.get("scar_f", 0.32) * 1.15, vsc) & vane & ~shaft & (area > 8)
        put(cr, ramp(ARC, 0.58 + 0.28 * vnoise(P * 0.8 + 3, 2) + 0.1 * bay))
        glow |= cr
    tg = ctx["tip_glow"][ph]
    if tg > 0:
        cell = np.floor(P * 0.7)
        h = hash1(cell[..., 0] * 3 + cell[..., 1] * 7 + cell[..., 2] * 13, 5)
        on = vane & (t > 0.80) & (np.abs(s) < 0.35) & (h < tg * 1.8) & ~shaft & (D < 3.2)
        on2 = vane & (t > 0.90) & (np.abs(s) < 0.3) & ~shaft
        on = on | (on2 & (hash1(fi, 8) < tg * 2.0))
        put(on, ramp(ARC, 0.66 + 0.2 * (t - 0.8) * 5 + 0.1 * bay))
        glow |= on
    # 縁の凹みの光
    gg = ctx.get("groove_glow")
    if gg and gg[ph] > 0:
        on = concave & (hash1(fi, 3) < gg[ph]) & np.isin(mats, ["vane", "stone", "bone"])
        put(on, ramp(ARC, 0.55 + 0.1 * bay))
        glow |= on
    # ---- 銅
    cu = pick(ctx["copper"], np.floor(1.5 + 2.5 * (0.5 + 0.5 * lit) + 0.8 * bay + 0.9 * convex + 0.9 * (n_mid - 0.5)).astype(int))
    if ctx.get("verd") is not None:
        vd = vnoise(P * 0.30 + 53, 7) > ctx["verd"][0]
        cu = np.where(vd[..., None], pick(ctx["verd_pal"], np.floor(1.5 + 2 * (0.5 + 0.5 * lit) + 0.7 * bay).astype(int)), cu)
    put(mats == "copper", cu)
    # 銀の腱
    sn = pick(ctx["sinew"], np.floor(1.3 + 2.6 * (0.5 + 0.5 * lit) + 0.7 * bay + 0.9 * convex).astype(int))
    put(mats == "sinew", sn)
    sg = ctx["sinew_glow"][ph]
    if sg > 0:
        on = (mats == "sinew") & (vnoise(P * 0.5 + 5, 2) < sg)
        put(on, ramp(ARC, 0.5 + 0.35 * vnoise(P * 0.9, 3) + 0.1 * bay))
        glow |= on
    # 骨
    bn = stone(ctx.get("bone", ctx["stone"]), 0.55, 0.32, 0.10)
    put(mats == "bone", bn)
    bg = ctx["bone_scar"][ph]
    if bg > 0:
        cr = scars(0.40, bg) & (mats == "bone") & (area > 4)
        put(cr, ramp(ARC, 0.6 + 0.2 * vnoise(P * 0.8 + 3, 2) + 0.1 * bay))
        glow |= cr
    # 針・とげ
    put(mats == "quill", pick(ctx.get("quill", ctx["vane"]), np.floor(1.3 + 2.8 * (0.5 + 0.5 * lit) + 0.7 * bay + 0.9 * convex).astype(int)))
    qt = ctx.get("quill_tip", [0, 0, 0])[ph]
    if qt > 0:
        on = (mats == "quill") & (D < 0.9) & INS & (K > 20) & (hash1(fi, 33) < qt)
        put(on, ramp(ARC, 0.7 + 0.1 * bay))
        glow |= on
    # ---- 光るもの
    d3 = np.sqrt(((P - CORE[phase]) ** 2).sum(-1))
    cv = np.clip(1.05 - d3 / (CORE_R[phase] * 1.1) * 0.65, 0, 1)
    put(mats == "core", ramp(ARC, cv * 0.55 + 0.45 + 0.05 * bay))
    glow |= mats == "core"
    wave = 0.5 + 0.5 * np.sin(X * 1.7 + Y * 0.9 + Z * 2.3)
    av = 0.45 + 0.30 * wave + 0.12 * (n_mid - 0.5) + 0.10 * bay + 0.1 * lit
    put(mats == "arc", ramp(ARC, av))
    glow |= mats == "arc"
    put(mats == "spark", ramp(ARC, 0.78 + 0.2 * (0.5 + 0.5 * lit) + 0.05 * bay))
    glow |= mats == "spark"

    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl & owned


# ------------------------------------------------------------------ 出力

INSET = 0.02


def export_mesh(faces, size, path, name, texture, glowfile, bones):
    bd = {b: {"vertices": [], "uvs": [], "triangles": []} for b in bones}
    for f in faces:
        b = bd[f.bone]
        base = len(b["vertices"])
        cen = f.uv.mean(0)
        for p, uv in zip(f.verts, f.uv):
            d = cen - uv
            nn = np.linalg.norm(d)
            uv = uv + d / nn * min(INSET, nn * 0.4) if nn > 1e-9 else uv
            b["vertices"].append([round(float(x), 4) for x in p])
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
            "units": "1/16 block, y up, front = -Z (absolute model-space vertices, like geo cubes; all bones have no rest rotation)",
            "uv": "normalized, origin top-left (v down)", "texture": texture, "glowmask": glowfile,
            "texture_size": [size, size], "texels_per_unit": TEX, "uv_inset_texels": INSET, "triangle_count": tot,
            "bones": out}
    Path(path).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data


def build_phase(opt, phase, outdir=None, save=True):
    bones = bone_defs(phase)
    pres = present_bones(phase)
    faces = [f for f in opt["build"](phase, pres) if f is not None]
    for f in faces:
        assert f.bone in bones, f.bone
        assert f.bone in pres, (f.bone, "not present in this phase's geo")
    size, P, N, M, L = build_atlas(faces, 128, 0)
    atlas, glow = paint(faces, size, P, N, M, L, opt["ctx"], phase)
    data = None
    if save:
        d = Path(outdir)
        d.mkdir(parents=True, exist_ok=True)
        Image.fromarray(atlas, "RGBA").save(d / f"p{phase}_atlas.png")
        gm = np.zeros((size, size, 4), np.uint8)
        gm[glow] = (255, 255, 255, 255)
        Image.fromarray(gm, "RGBA").save(d / f"p{phase}_glowmask.png")
        data = export_mesh(faces, size, d / f"p{phase}_mesh.json", f"storm_heart_p{phase}_lowpoly",
                           f"p{phase}_atlas.png", f"p{phase}_glowmask.png", bones)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, data=data, bones=bones, tris=tri_count(faces),
                by_bone={b: tri_count([f for f in faces if f.bone == b]) for b in sorted({f.bone for f in faces})})
