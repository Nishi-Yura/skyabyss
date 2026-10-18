"""余燼の心臓 (ember_heart) 低ポリ案の共通部品。ラフ。src には触らない。

座標は 1 単位 = 1/16 ブロック、y が上、正面は -Z。骨の名前・pivot は
src/main/resources/assets/skyabyss/geo/entity/ember_heart_p{1,2,3}.geo.json から読む
(どの骨も親なし・pivot (0,16,0)・休めの回転なし)。頂点はモデル全体の絶対座標。
"""
from __future__ import annotations

import inspect
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
OUTDIR = ROOT / "docs/review/lowpoly_roughs/lp_ember_heart"
SUBJ = "ember_heart"

# lpkit.render の光のにじみは青白。余燼は橙なので、同じ関数の色と強さだけ差し替えて使う (lpkit.py は触らない)
_src = inspect.getsource(lpkit.render).replace("[140, 230, 255]", "[255, 150, 60]").replace("* 0.9", "* 0.5") \
    .replace("def render(", "def render_ember(")
exec(compile(_src, "render_ember", "exec"), lpkit.__dict__)
render_ember = lpkit.render_ember

CEN = np.array([0.0, 15.5, 0.0])       # 殻の中心
RAD = np.array([10.2, 11.0, 9.6])      # 殻の表面 (板の基準面) の半径
HC = np.array([0.0, 15.0, 0.5])        # 心臓
HR = np.array([7.0, 6.0, 4.6])
CORE_C = {1: np.array([0.0, 16.0, -4.0]), 2: np.array([0.0, 16.0, -4.0]), 3: np.array([0.0, 16.0, -3.2])}
CORE_R = {1: 2.0, 2: 2.3, 3: 1.4}

EMB = {1: np.array([(110, 26, 14), (176, 50, 18), (226, 92, 26), (250, 140, 40), (255, 190, 84)]),
       2: np.array([(190, 84, 20), (240, 140, 28), (255, 192, 52), (255, 222, 110), (255, 244, 180)]),
       3: np.array([(60, 12, 12), (96, 18, 16), (138, 30, 20), (176, 48, 24), (212, 78, 34)])}
HEAT = np.array([(40, 18, 16), (74, 30, 20), (112, 44, 22), (150, 66, 26)])


def V(*a):
    return np.array(a, float)


def rad(a):
    return math.radians(a)


def smooth(a, b, x):
    t = min(max((x - a) / (b - a), 0.0), 1.0)
    return t * t * (3 - 2 * t)


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


def cube_list(phase, bone):
    for b in load_geo(phase)["bones"]:
        if b["name"] == bone:
            return [(np.array(c["origin"], float) + np.array(c["size"], float) / 2, np.array(c["size"], float))
                    for c in b.get("cubes", [])]
    return []


# ------------------------------------------------------------------ 絵

LIGHT = np.array([-0.35, 0.85, -0.4])
LIGHT = LIGHT / np.linalg.norm(LIGHT)


def ramp(pal, v):
    pal = np.asarray(pal)
    return pal[np.clip(np.floor(v * len(pal)).astype(int), 0, len(pal) - 1)].astype(float)


def pick(pal, idx):
    pal = np.asarray(pal)
    return pal[np.clip(idx, 0, len(pal) - 1)].astype(float)


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
    n_hi = vnoise(P * 1.1 + 5, 7)
    X, Y, Z = P[..., 0], P[..., 1], P[..., 2]
    q = np.sqrt((((P - CEN) / RAD) ** 2).sum(-1))
    hu = (q - 1.0) * 10.0
    ph = phase - 1
    pit = hash1(xx * 7 + yy * 13 + fi * 3, 5)
    cc = CORE_C[phase]
    dcore = np.sqrt(((P - cc) ** 2).sum(-1))

    def put(mask, c):
        nonlocal col
        col = np.where(mask[..., None], c, col)

    def stone(pal, base, lw=0.30, nw=0.14, extra=0.0):
        ns = len(pal)
        v = base + lw * lit + nw * (n_lo - 0.5) + 0.10 * (n_mid - 0.5) + 0.07 * bay + extra
        idx = np.floor(v * ns).astype(int) + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave
        return idx

    glow_ramp = EMB[phase]
    Rf = -ctx["floor_depth"]
    plate = np.asarray(ctx["plate"][ph])

    # ---- 板の上面
    pidx = stone(plate, ctx["plate_base"][ph]) - (pit < ctx["pit"][ph]) - (n_hi > 0.76)
    pc = pick(plate, pidx)
    sp = ctx.get("sparkle", 0.0)
    if sp > 0:
        on = (lit > 0.5) & (hash1(xx * 11 + yy * 5 + fi, 9) < sp)
        pc = np.where(on[..., None], plate[-1].astype(float), pc)
    put(mats == "plate", pc)
    # ---- 板の壁: 下ほど熱く光る
    t = np.clip((hu - Rf) / max(-Rf, 1e-3), 0, 1.4)
    gw = ctx["glow_w"][ph]
    hw = ctx["heat_w"][ph]
    wall = mats == "wall"
    put(wall, pick(plate, stone(plate, ctx["plate_base"][ph] - 0.12, 0.22)))
    hot = wall & (t < gw + hw)
    hidx = np.floor(1.0 + 2.2 * (0.5 + 0.5 * lit) + 0.9 * bay + 1.4 * (1 - (t - gw) / max(hw, 1e-3))).astype(int)
    put(hot, pick(HEAT, hidx))
    gl = wall & (t < gw)
    put(gl, ramp(glow_ramp, 0.30 + 0.50 * (1 - t / max(gw, 1e-3)) * (0.7 + 0.5 * n_mid) + 0.08 * bay))
    glow |= gl
    # ---- 割れ目の床
    fl = mats == "floor"
    fv = ctx["floor_v"][ph] + 0.36 * n_mid + 0.18 * (n_lo - 0.5) + 0.12 * bay + 0.08 * lit
    fc = ramp(glow_ramp, fv)
    cool = fl & (hash1(xx * 5 + yy * 9 + fi, 3) < ctx["floor_cool"][ph])
    fc = np.where(cool[..., None], pick(HEAT, np.floor(1.5 + 1.5 * bay).astype(int)), fc)
    put(fl, fc)
    glow |= fl & ~cool
    # ---- 心臓
    heart = np.asarray(ctx["heart"][ph])
    hv = np.floor((0.46 + 0.34 * lit + 0.12 * (n_mid - 0.5) + 0.07 * bay) * len(heart)).astype(int) \
        + (convex & (lit > 0)) - concave + (ctx["heart_warm"][ph] * np.exp(-dcore / 4.5) * 3).astype(int)
    put(mats == "heart", pick(heart, hv))
    vn = mats == "vein"
    put(vn, ramp(glow_ramp, 0.40 + 0.42 * vnoise(P * 0.9 + 4, 3) + 0.08 * bay))
    glow |= vn
    # 血管 (大動脈): 輪の帯が光る
    vs = mats == "vessel"
    vessel = np.asarray(ctx["vessel"][ph])
    put(vs, pick(vessel, stone(vessel, 0.5, 0.34, 0.1)))
    bands = vs & (((Y * 0.55) % 1.0) < ctx["vessel_band"][ph]) & (area > 3)
    put(bands, ramp(glow_ramp, 0.34 + 0.4 * n_mid + 0.08 * bay))
    glow |= bands
    # 核
    dxy = np.sqrt((X - cc[0]) ** 2 + (Y - cc[1]) ** 2)
    cv = np.clip(1.0 - dxy / (CORE_R[phase] * 1.05) * 0.85 - 0.12 * (Z - cc[2] > 0), 0, 1)
    co = mats == "core"
    cvv = ctx["core_v"][ph]
    put(co, ramp(glow_ramp, cv * cvv + (1 - cvv) * 0.2 + 0.06 * bay))
    glow |= co
    # 垂れ・火の粉: 光る
    for m, base, rng_ in (("drip", 0.40, 0.40), ("cinder", 0.55, 0.35)):
        mm = mats == m
        put(mm, ramp(glow_ramp, base + rng_ * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.06 * bay))
        glow |= mm
    cd = (mats == "drip") & (Y > ctx["drip_cool_y"][ph])
    put(cd, pick(HEAT, np.floor(1.2 + 2.2 * (0.5 + 0.5 * lit) + 0.9 * bay).astype(int)))
    glow &= ~cd
    # 石の垂れ・棘 (冷えた石、先だけ灯る)
    st = mats == "stalac"
    put(st, pick(plate, stone(plate, 0.5, 0.3)))
    tip = st & (Y < ctx["stalac_tip_y"][ph])
    put(tip, ramp(glow_ramp, 0.4 + 0.4 * n_mid + 0.08 * bay))
    glow |= tip

    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl_ = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl_ & owned


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
        data = export_mesh(faces, size, d / f"p{phase}_mesh.json", f"ember_heart_p{phase}_lowpoly",
                           f"p{phase}_atlas.png", f"p{phase}_glowmask.png", bones)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, data=data, bones=bones, tris=tri_count(faces),
                by_bone={b: tri_count([f for f in faces if f.bone == b]) for b in sorted({f.bone for f in faces})})
