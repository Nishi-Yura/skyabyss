"""岩鯨の心臓 (stone_whale_heart) 低ポリ案の共通部品。ラフ。src には触らない。

座標は 1 単位 = 1/16 ブロック、y が上、正面は -Z。骨の名前・階層・pivot・休めの回転は
src/main/resources/assets/skyabyss/geo/entity/stone_whale_heart_p{1,2,3}.geo.json から読む。
メッシュの頂点は geo の cube と同じ「骨の回転をかける前」の座標 (板 plate_k / seam_k は正面 -Z 向きで作り、
骨の rotation で回る)。JSON の骨に rotation を書いておく。
"""
from __future__ import annotations

import copy
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
from bone_sentinel_lp import hash1, vnoise, BAYER  # noqa: E402
import voxel  # noqa: E402

GEO = ROOT / "src/main/resources/assets/skyabyss/geo/entity"
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
OUTDIR = ROOT / "docs/review/lowpoly_roughs/lp_whale_heart"

CEN = np.array([0.0, 12.0, 0.0])
R0 = 11.6          # 今の殻の外側の球の半径 (中心 CEN)
SUBJ = "stone_whale_heart"


def V(*a):
    return np.array(a, float)


# ------------------------------------------------------------------ 骨 (今の geo から)

def load_geo(phase):
    return json.loads((GEO / f"{SUBJ}_p{phase}.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]


def bone_defs(phase):
    g = load_geo(phase)
    d = {}
    for b in g["bones"]:
        d[b["name"]] = (b.get("parent"), [float(x) for x in b["pivot"]], [float(x) for x in b.get("rotation", [0, 0, 0])])
    return d


def present_bones(phase):
    g = load_geo(phase)
    return {b["name"] for b in g["bones"] if b.get("cubes")}


def cube_counts(phase):
    g = load_geo(phase)
    return sum(len(b.get("cubes", [])) for b in g["bones"])


def rest_rot(rot):
    return rot_z(rot[2]) @ rot_y(rot[1]) @ rot_x(rot[0])


def bone_xf(bones, name, cache=None):
    """骨の休めの変換: 世界 = R @ p + t。"""
    cache = {} if cache is None else cache
    if name in cache:
        return cache[name]
    par, pv, rot = bones[name]
    pv = np.array(pv)
    R = rest_rot(rot)
    t = pv - R @ pv
    if par is not None:
        Rp, tp = bone_xf(bones, par, cache)
        R, t = Rp @ R, Rp @ t + tp
    cache[name] = (R, t)
    return cache[name]


def to_world(faces, bones):
    cache = {}
    out = []
    for f in faces:
        R, t = bone_xf(bones, f.bone, cache)
        g = copy.copy(f)
        g.verts = f.verts @ R.T + t
        g.normal = R @ f.normal
        out.append(g)
    return out


def world_arrays(faces, bones, P, N, M):
    cache = {}
    Pw, Nw = P.copy(), N.copy()
    byb = {}
    for i, f in enumerate(faces):
        byb.setdefault(f.bone, []).append(i)
    for b, ids in byb.items():
        R, t = bone_xf(bones, b, cache)
        if np.allclose(R, np.eye(3)) and np.allclose(t, 0):
            continue
        m = np.isin(M, ids)
        Pw[m] = P[m] @ R.T + t
        Nw[m] = N[m] @ R.T
    return Pw, Nw


# ------------------------------------------------------------------ 形の道具

def spx(x, y, r):
    """球 (中心 CEN, 半径 r) の上の点。x は横のずれ、y は赤道からの高さ。正面 -Z。"""
    return V(x, 12 + y, -math.sqrt(max(r * r - x * x - y * y, 1e-6)))


def spa(a, y, r):
    rho = math.sqrt(max(r * r - y * y, 1e-6))
    t = math.radians(a)
    return V(rho * math.sin(t), 12 + y, -rho * math.cos(t))


def jit(*key, amp=1.0):
    s = 0
    for k in key:
        s = ((s * 73856093) ^ (int(k) * 19349663 + 83492791)) & 0xFFFFFFFF
    s = (s * 1103515245 + 12345) & 0x7FFFFFFF
    s = (s * 1103515245 + 12345) & 0x7FFFFFFF
    return ((s / 0x7FFFFFFF) * 2 - 1) * amp


def pull_in(p, d):
    q = p - CEN
    n = np.linalg.norm(q)
    return CEN + q * (max(n - d, 0.5) / n)


def outward(p, d):
    return pull_in(p, -d)


def quad_up(bone, mat, pts, up=True):
    """法線を上 (下) に向けたい水平の面。"""
    c = np.mean(pts, 0)
    return poly(bone, mat, pts, c - V(0, 5, 0) if up else c + V(0, 5, 0))


def backing(bone, O, t=2.6, inner_mat="inner", wall_mat="rim", walls=(1, 1, 1, 1)):
    """外の格子 O[行 (下→上)][列 (左→右)] の裏: 内側の面 (2 列 × 全行) と四辺の壁。"""
    nr, nc = len(O), len(O[0])
    I = [[pull_in(O[i][j], t) for j in (0, nc - 1)] for i in range(nr)]
    F = []
    for i in range(nr - 1):
        q = [I[i][0], I[i][1], I[i + 1][1], I[i + 1][0]]
        c = np.mean(q, 0)
        F += poly(bone, inner_mat, q, CEN + (c - CEN) * 3)
    if walls[0]:   # 左
        for i in range(nr - 1):
            q = [O[i][0], O[i + 1][0], I[i + 1][0], I[i][0]]
            F += poly(bone, wall_mat, q, np.mean(q, 0) + V(5, 0, 0))
    if walls[1]:   # 右
        for i in range(nr - 1):
            q = [O[i][-1], O[i + 1][-1], I[i + 1][1], I[i][1]]
            F += poly(bone, wall_mat, q, np.mean(q, 0) + V(-5, 0, 0))

    def lerp_inner(i, j):
        x0, x1 = O[i][0][0], O[i][-1][0]
        s = 0.0 if abs(x1 - x0) < 1e-6 else (O[i][j][0] - x0) / (x1 - x0)
        return I[i][0] * (1 - s) + I[i][1] * s
    if walls[2]:   # 下
        for j in range(nc - 1):
            q = [O[0][j], O[0][j + 1], lerp_inner(0, j + 1), lerp_inner(0, j)]
            F += poly(bone, wall_mat, q, np.mean(q, 0) + V(0, 5, 0))
    if walls[3]:   # 上
        for j in range(nc - 1):
            q = [O[-1][j], O[-1][j + 1], lerp_inner(nr - 1, j + 1), lerp_inner(nr - 1, j)]
            F += poly(bone, wall_mat, q, np.mean(q, 0) - V(0, 5, 0))
    return F


def surface_grid(bone, mat, O, holes=(), ref=None):
    """格子 O の外面。holes に (行, 列) を入れるとその四角を抜く。"""
    F = []
    ref = CEN if ref is None else ref
    for i in range(len(O) - 1):
        for j in range(len(O[0]) - 1):
            if (i, j) in holes:
                continue
            F += poly(bone, mat, [O[i][j], O[i][j + 1], O[i + 1][j + 1], O[i + 1][j]], ref)
    return F


def ell_pt(center, radii, lat, phi, deform=None, ph=0.0):
    """ellipsoid() と同じ作りの点 (緯度 lat、経度 phi 度。phi=0 が正面 -Z)。"""
    L, f = math.radians(lat), math.radians(phi)
    rx, ry, rz = radii
    p = V(math.cos(L) * math.sin(f) * rx, math.sin(L) * ry, -math.cos(L) * math.cos(f) * rz)
    if deform:
        p = deform(p)
    return p + np.asarray(center, float)


def vein_path(center, radii, pts, deform=None, lift=0.0):
    """(lat, phi) の列に沿った、表面から lift だけ浮かせた点列。"""
    out = []
    for lat, phi in pts:
        p = ell_pt(center, radii, lat, phi, deform)
        d = p - np.asarray(center, float)
        out.append(p + d / max(np.linalg.norm(d), 1e-6) * lift)
    return out


def chain_links(bone, mat, a, b, n, size=(1.1, 1.6), alt=0):
    """a から b へ、2 枚の板を交互に向けた鎖 (マイクラの鎖ブロックの形)。1 個 = 六角の板の表裏。"""
    a, b = np.asarray(a, float), np.asarray(b, float)
    d = b - a
    L = np.linalg.norm(d)
    t = d / L
    F = []
    for i in range(n):
        c = a + t * (L * (i + 0.5) / n)
        side = V(1, 0, 0) if (i + alt) % 2 == 0 else V(0, 0, 1)
        side = side - t * np.dot(side, t)
        side = side / np.linalg.norm(side)
        w, h = size
        h = min(h, L / n * 0.62)
        pts = [c + t * h, c + side * w * 0.55 + t * h * 0.45, c + side * w * 0.55 - t * h * 0.45, c - t * h,
               c - side * w * 0.55 - t * h * 0.45, c - side * w * 0.55 + t * h * 0.45]
        n0 = np.cross(side, t)
        F += poly(bone, mat, pts, c - n0 * 3)
        F += poly(bone, mat, pts, c + n0 * 3)
    return F


# ------------------------------------------------------------------ 描画 (光る色を変えられる版)

_src = inspect.getsource(lpkit.render).replace("np.array([140, 230, 255], float)", "np.array(GCOL, float)") \
    .replace("def render(", "def render_g(")
_ns = dict(vars(lpkit))
_ns["GCOL"] = (255, 150, 70)
exec(_src, _ns)


def render_glow(*a, gcol=(255, 150, 70), **k):
    _ns["GCOL"] = gcol
    return _ns["render_g"](*a, **k)


LIGHT = np.array([-0.35, 0.85, -0.4])
LIGHT = LIGHT / np.linalg.norm(LIGHT)

FIRE = np.array([(96, 20, 14), (168, 42, 18), (230, 86, 28), (255, 138, 44), (255, 190, 88), (255, 232, 160), (255, 250, 220)])
MOSS = np.array([(24, 40, 28), (44, 66, 36), (72, 100, 48), (110, 140, 70)])
BONE = np.array([(44, 38, 40), (88, 76, 64), (134, 119, 96), (176, 160, 128), (212, 198, 164), (238, 229, 203)])
IRON = np.array([(14, 14, 20), (30, 30, 40), (52, 52, 64), (80, 80, 94), (116, 116, 130)])
FLESH = np.array([(40, 6, 16), (78, 12, 26), (122, 22, 34), (164, 40, 46), (198, 68, 62), (228, 108, 92)])
VEINB = np.array([(30, 18, 54), (52, 30, 92), (82, 52, 128), (120, 86, 160)])
FAT = np.array([(150, 120, 70), (190, 158, 96), (222, 194, 128), (240, 218, 160)])


def ramp(pal, v):
    pal = np.asarray(pal)
    return pal[np.clip(np.floor(v * len(pal)).astype(int), 0, len(pal) - 1)].astype(float)


def pick(pal, idx):
    pal = np.asarray(pal)
    return pal[np.clip(idx, 0, len(pal) - 1)].astype(float)


# ------------------------------------------------------------------ 絵

def paint(faces, size, P, N, M, L, Pw, Nw, ctx, phase, bones):
    D, K, LU, LV, CW, CH, INS = edge_fields(faces, size)
    H = W = size
    yy, xx = np.mgrid[0:H, 0:W]
    fi = np.where(M >= 0, M, 0)
    owned = M >= 0
    mats = np.array([f.mat for f in faces])[fi]
    bnames = np.array([f.bone for f in faces])
    bon = bnames[fi]
    bset = sorted(set(bnames))
    bid = np.array([bset.index(b) for b in bnames])[fi]
    nverts = np.array([len(f.verts) for f in faces])[fi]
    bay = BAYER[yy % 4, xx % 4] - 0.5
    ny = Nw[..., 1]
    lit = Nw @ LIGHT
    area = (CW * CH).astype(float)
    edge = (D < 1.05) & INS
    convex = edge & (K > 24)
    concave = edge & (K < -24)
    near_concave = (D < 2.4) & (K < -24) & INS
    Y = Pw[..., 1]
    col = np.zeros((H, W, 3))
    glow = np.zeros((H, W), bool)
    n_lo = vnoise(Pw * 0.16 + 3, 1)
    n_mid = vnoise(Pw * 0.45 + 11, 2)
    n_hi = vnoise(Pw * 0.9 + 5, 7)

    def put(mask, c):
        nonlocal col
        col = np.where(mask[..., None], c, col)

    # ---- 石 (板の外側・殻)
    pal = ctx["stone"]
    ns = len(pal)
    sv = 0.50 + 0.30 * lit + 0.14 * (n_lo - 0.5) + 0.10 * (n_mid - 0.5) + 0.06 * bay \
        - 0.12 * np.clip((6 - Y) / 6, 0, 1)
    if ctx.get("tier"):
        t0, dt = ctx["tier"]
        tier = np.floor((Y - t0) / dt)
        sv = sv + 0.10 * (hash1(tier * 7 + bid * 3.1, 4) - 0.5) * 2
    sidx = np.floor(sv * ns).astype(int) + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave
    # ひび (面の中を縦にくねらせる。暗い or 光る)
    hf = hash1(fi, 5)
    cp = ctx["crack_p"][phase - 1]
    crack_on = (hf < cp) & (area > 26) & (nverts <= 4) & np.isin(mats, ["stone", "shard", "spike_stone"])
    c0 = (hash1(fi, 6) * np.maximum(CW - 2, 1)).astype(int) + 1
    v0 = (hash1(fi, 7) * 0.3 * CH).astype(int)
    v1 = v0 + (CH * (0.4 + 0.4 * hash1(fi, 8))).astype(int)
    zig = np.round(0.7 * np.sin(LV * 0.8 + fi * 1.7)).astype(int)
    seg = INS & (LV >= v0) & (LV <= v1)
    on_crack = crack_on & seg & (LU == c0 + zig)
    cglow = ctx["crack_glow"][phase - 1]
    sidx = np.where(on_crack & (not cglow), 0, sidx)
    c_stone = pick(pal, sidx)
    # 苔
    mn = 0.62 * vnoise(Pw * 0.21 + 21, 4) + 0.38 * vnoise(Pw * 0.52 + 7, 9)
    mscore = mn + 0.34 * np.clip(ny, 0, 1) + 0.36 * near_concave + 0.10 * (1 - np.clip(Y / 30, 0, 1))
    thr = ctx.get("moss_thr", 0.92)
    moss = (mscore + 0.06 * bay > thr) & (ny > -0.2)
    mi = np.clip(np.floor((mscore - thr) * 7 + 0.4 * lit + 0.6 * bay), 0, 3).astype(int)
    mi = np.where(convex & (ny > 0.3), 3, mi)
    if ctx.get("rust") is not None:
        rn = vnoise(Pw * np.array([0.22, 0.07, 0.22]) + 41, 3)
        rthr = ctx.get("rust_thr", 0.66)
        rust = (rn > rthr) & (ny > -0.3)
        c_stone = np.where(rust[..., None], pick(ctx["rust"], np.floor((rn - rthr) * 14 + 0.5 * lit + 0.8).astype(int)), c_stone)
    stone_like = np.isin(mats, ["stone", "spike_stone", "shard"])
    c_stone = np.where((moss & (ctx.get("moss_on", True)))[..., None], pick(MOSS, mi), c_stone)
    if ctx.get("veins_pale") is not None:   # 眼球の血管 (C)
        rn = vnoise(Pw * 0.30 + 71, 5)
        rn2 = vnoise(Pw * 0.62 + 13, 6)
        vl = ((np.abs(rn - 0.5) < 0.012) | ((np.abs(rn2 - 0.5) < 0.009) & (rn > 0.5))) & (area > 12)
        c_stone = np.where(vl[..., None], pick(ctx["veins_pale"], np.floor(2.4 + 0.9 * lit + 0.6 * bay).astype(int)), c_stone)
    if ctx.get("pupil"):
        pu = ctx["pupil"]
        cx, cy = pu["c"]
        dx = (Pw[..., 0] - cx) / pu["rx"]
        dy = (Pw[..., 1] - cy) / pu["ry"]
        isp = (dx * dx + dy * dy < 1.0) & (Pw[..., 2] < -3.0) & (Nw[..., 2] < -0.2) & stone_like
        c_stone = np.where(isp[..., None], np.array(pu["col"], float), c_stone)
    if cglow:
        gl = on_crack & ~np.zeros_like(on_crack)
        gv = 0.50 + 0.14 * (1 - np.abs(LU - c0 - zig) * 0.5) + 0.07 * bay
        c_stone = np.where(gl[..., None], ramp(FIRE, gv), c_stone)
        glow |= gl
        # 縁の弱い光
    put(stone_like, c_stone)

    # ---- 殻の側面・裏 (暗い石、割れの内側は熱で光る)
    rim_i = np.clip(np.floor(sv * ns).astype(int) - ctx.get("rim_dark", 2) + (convex & (lit > 0)), 0, ns - 1)
    c_rim = pick(pal, rim_i)
    put(mats == "rim", c_rim)
    if phase >= 2:
        eh = vnoise(Pw * 0.55 + 9, 8) + 0.1 * bay
        hot = eh > ctx.get("ember_thr", 0.55)
        hv = np.clip((eh - 0.55) * 4 + 0.35, 0, 0.8)
        c_in = np.where(hot[..., None], ramp(FIRE, hv), pick(pal, np.floor(sv * ns * 0.45).astype(int) - 1))
        if ctx.get("inner_pal") is not None:
            ip = ctx["inner_pal"]
            c_in = np.where(hot[..., None], ramp(FIRE, hv), pick(ip, np.floor((0.30 + 0.25 * lit + 0.12 * (n_lo - 0.5) + 0.06 * bay) * len(ip)).astype(int)))
        put(mats == "inner", c_in)
        glow |= hot & (mats == "inner")
        hr = hot & (mats == "rim") & (vnoise(Pw * 0.7 + 3, 4) > 0.5)
        put(hr, ramp(FIRE, np.full(hr.shape, 0.35)))
        glow |= hr
    else:
        put(mats == "inner", pick(ctx["inner_pal"] if ctx.get("inner_pal") is not None else pal, np.floor(sv * ns * 0.6).astype(int) - 1))

    # ---- 光る継ぎ目・割れ目
    ishine = np.isin(mats, ["seam", "crack"])
    halfw = ctx.get("seam_w", 1.4)
    lat = np.abs(L[..., 0])
    if ctx.get("seam_lens"):   # 継ぎ目ごとの幅の違いを使う場合は ctx の関数に任せる
        halfw = np.where(np.isin(bon, ctx["seam_lens"]), ctx.get("seam_w_lens", 2.0), halfw)
    t = np.clip(lat / halfw, 0, 1)
    ht = np.abs(Y - 12) / 9.0
    jag = 0.12 * (vnoise(Pw * 0.7 + 5, 3) - 0.5) + 0.07 * bay
    sv2 = ctx.get("seam_peak", 0.86) - 0.55 * t ** 1.4 - 0.16 * ht ** 2 + jag
    put(ishine, ramp(FIRE, sv2))
    glow |= ishine
    if phase == 1 and ctx.get("seam_dim"):
        pass

    # ---- 骨
    bv = 0.66 + 0.18 * lit + 0.09 * (n_lo - 0.5) + 0.05 * bay - 0.08 * np.clip((24 - Y) / 24, 0, 1)
    bi = np.floor(bv * 6).astype(int) + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave
    streak = (hash1(LU * 7 + fi * 13, 3) < 0.15) & (area > 14)
    bi = bi - streak
    c_bone = pick(ctx.get("bone", BONE), np.maximum(bi, 1))
    c_bone = np.where((moss & (mats == "bone") & (mscore > thr + 0.05))[..., None], pick(MOSS, mi), c_bone)
    put(mats == "bone", c_bone)
    put(mats == "tip", pick(ctx.get("bone", BONE), np.clip(np.floor(3.6 + 1.6 * lit + 0.8 * bay).astype(int), 3, 5)))

    # ---- 鉄・鎖
    iv = 0.40 + 0.32 * lit + 0.07 * bay + 0.1 * (n_mid - 0.5)
    ii = np.floor(iv * 5).astype(int) + (convex & (lit > 0)) - concave
    c_iron = pick(IRON, ii)
    rn = vnoise(Pw * 0.5 + 63, 3)
    c_iron = np.where(((rn > 0.7) & (ny > -0.3))[..., None], pick(MOSS, np.floor((rn - 0.7) * 12 + 0.5).astype(int)), c_iron)
    put(np.isin(mats, ["iron", "link"]), c_iron)

    # ---- 根・触手
    put(mats == "tend", pick(FLESH, np.floor(1.4 + 2.6 * (lit * 0.5 + 0.5) + 0.8 * bay + 0.8 * convex).astype(int)))

    # ---- 底・台座
    bsv = 0.40 + 0.30 * lit + 0.1 * (n_lo - 0.5) + 0.06 * bay
    put(mats == "base", np.where(((mscore + 0.06 * bay > 0.86) & (ny > -0.2))[..., None], pick(MOSS, mi),
                                 pick(pal, np.floor(bsv * ns).astype(int) - 1 + convex - concave)))

    # ---- 棘 (石・骨・赤)
    sp = ctx.get("spike_pal")
    if sp is not None:
        spv = 0.45 + 0.32 * lit + 0.06 * bay + 0.1 * (n_mid - 0.5)
        sidx2 = np.floor(spv * len(sp)).astype(int) + (convex & (lit > 0)) - concave
        # 先へ向けて明るく (根元は暗く): 局所 y でなく島の v (根元から先) を使う
        put(mats == "spike", pick(sp, sidx2))

    # ---- 心臓の筋肉
    fp = ctx.get("flesh", FLESH)
    nf = len(fp)
    fib = vnoise(Pw * np.array([0.55, 0.16, 0.55]) + 17, 5)
    fv = 0.42 + 0.30 * lit + 0.14 * (fib - 0.5) + 0.07 * bay - 0.10 * np.clip(-ny, 0, 1)
    fi_ = np.floor(fv * nf).astype(int) + (convex & (lit > 0)) - concave
    c_fl = pick(fp, fi_)
    # 筋の帯 (横方向の暗い線)
    band = np.abs(((Y * 0.62) % 1.0) - 0.5) < 0.07
    c_fl = np.where((band & (ctx.get("fl_band", True)))[..., None], pick(fp, fi_ - 1), c_fl)
    if ctx.get("fat_y") is not None:
        fy = ctx["fat_y"](Pw)
        fat = (np.abs(fy) < ctx.get("fat_w", 0.9)) & (mats == "flesh")
        c_fl = np.where(fat[..., None], pick(FAT, np.floor(1.5 + 1.6 * lit + 0.8 * bay + 0.8 * convex).astype(int)), c_fl)
    # 脈の暗い溝
    gv = (np.abs(vnoise(Pw * 0.4 + 91, 6) - 0.5) < 0.022)
    c_fl = np.where((gv & (area > 14))[..., None], pick(fp, fi_ - 2), c_fl)
    put(mats == "flesh", c_fl)
    put(mats == "vein", pick(ctx.get("vein_pal", VEINB), np.floor(1.2 + 2.0 * (lit * 0.5 + 0.5) + 0.7 * bay + 0.9 * convex).astype(int)))
    # 結晶 (B): 面ごとの明るさだけの赤い宝石
    if ctx.get("gem") is not None:
        gp = ctx["gem"]
        gvv = 0.40 + 0.42 * lit + 0.05 * bay + 0.20 * (hash1(fi, 21) - 0.5)
        c_gem = pick(gp, np.floor(gvv * len(gp)).astype(int) + convex)
        put(mats == "gem", c_gem)


    for ir in ctx.get("irises", []) if "irises" in ctx else ([ctx["iris"]] if ctx.get("iris") else []):
        cx, cy = ir["c"]
        dx = Pw[..., 0] - cx
        dy = Pw[..., 1] - cy
        r = np.sqrt(dx * dx + (dy * 0.95) ** 2)
        ang = np.arctan2(dy, dx)
        zf = ir.get("zmax", -3.0)
        front = (Pw[..., 2] < zf) & (Nw[..., 2] < -0.2)
        isiris = front & (r < ir["R"]) & np.isin(mats, ir.get("mats", ["stone", "spike_stone", "shard"]))
        fib = hash1(np.floor((ang + math.pi) / (2 * math.pi) * ir["fibres"]) + 3, 11)
        fib2 = hash1(np.floor((ang + math.pi) / (2 * math.pi) * ir["fibres"] * 2) + 5, 12)
        rr = r / ir["R"]
        fa = ir.get("fibamp", 0.22)
        rr = np.clip(rr, 0, 1.0)
        v = ir.get("base", 0.30) + 0.45 * (1 - rr) ** 0.7 + fa * (fib - 0.5) + 0.5 * fa * (fib2 - 0.5) + 0.08 * bay
        v = np.where((rr > 0.84), 0.12 + 0.12 * fib, v)
        v = np.where((rr > 0.28) & (rr < 0.36), v + 0.22, v)
        put(isiris, ramp(ir["pal"], v))
        if ir.get("glow"):
            glow |= isiris
    # ---- 核 (光る玉) と黒目
    cc = ctx.get("core_c")
    if cc is not None:
        d = np.sqrt(((Pw - np.asarray(cc)) ** 2).sum(-1))
        r0 = ctx.get("core_r", 2.6)
        cv = np.clip(1.05 - d / r0 * 0.75, 0, 1)
        cvv = cv + 0.10 * bay
        put(mats == "core", ramp(FIRE, cvv * 0.55 + 0.45))
        glow |= mats == "core"
        if ctx.get("core_iris"):
            ang = np.arctan2(Pw[..., 1] - cc[1], Pw[..., 0] - cc[0])
            fibc = hash1(np.floor((ang + math.pi) / (2 * math.pi) * 16) + 9, 14)
            rr = np.clip(d / r0, 0, 1.2)
            v = 0.55 + 0.40 * (1 - rr) + 0.18 * (fibc - 0.5) + 0.07 * bay
            v = np.where(rr < 0.22, 1.05, v)
            v = np.where(rr > 0.86, 0.35 + 0.1 * fibc, v)
            put(mats == "core", ramp(FIRE, v))
    put(mats == "eye_dark", np.array([18.0, 10.0, 14.0]))

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
            "units": "1/16 block, y up, front = -Z (vertices are in the bone's local frame BEFORE the bone's rest rotation, like geo cubes)",
            "uv": "normalized, origin top-left (v down)", "texture": texture, "glowmask": glowfile,
            "texture_size": [size, size], "texels_per_unit": TEX, "uv_inset_texels": INSET, "triangle_count": tot,
            "bones": out}
    Path(path).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data


def build_phase(opt, phase, outdir=None, save=True):
    """opt: {'id','build':fn(phase,present)->faces,'ctx':dict}。戻り値: dict"""
    bones = bone_defs(phase)
    pres = present_bones(phase)
    faces = [f for f in opt["build"](phase, pres) if f is not None]
    for f in faces:
        assert f.bone in bones, f.bone
    size, P, N, M, L = build_atlas(faces, 128, 0)
    Pw, Nw = world_arrays(faces, bones, P, N, M)
    ctx = opt["ctx"](phase) if callable(opt["ctx"]) else opt["ctx"]
    atlas, glow = paint(faces, size, P, N, M, L, Pw, Nw, ctx, phase, bones)
    data = None
    if save:
        d = Path(outdir)
        d.mkdir(parents=True, exist_ok=True)
        Image.fromarray(atlas, "RGBA").save(d / f"p{phase}_atlas.png")
        gm = np.zeros((size, size, 4), np.uint8)
        gm[glow] = (255, 255, 255, 255)
        Image.fromarray(gm, "RGBA").save(d / f"p{phase}_glowmask.png")
        data = export_mesh(faces, size, d / f"p{phase}_mesh.json", f"stone_whale_heart_p{phase}_lowpoly",
                           f"p{phase}_atlas.png", f"p{phase}_glowmask.png", bones)
    world = to_world(faces, bones)
    return dict(faces=faces, world=world, size=size, atlas=atlas, glow=glow, data=data, bones=bones,
                tris=tri_count(faces))
