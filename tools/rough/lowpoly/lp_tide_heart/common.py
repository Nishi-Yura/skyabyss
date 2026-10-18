"""潮殻の心臓 (tide_heart) 低ポリ案の共通部品。ラフ。src には触らない。

座標は 1 単位 = 1/16 ブロック、y が上、正面は -Z。骨の名前・pivot は
src/main/resources/assets/skyabyss/geo/entity/tide_heart_p{1,2,3}.geo.json から読む
(どの骨も親なし・pivot (0,16,0)・休めの回転なし)。頂点はモデル全体の絶対座標。

形: 渦巻の殻 (spiral) が心臓 (heart) を取り巻き、心臓の正面に核 (core_eye) が光る。
殻の渦の中心 S、心臓の中心 C。
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
from bone_sentinel_lp import hash1, vnoise, BAYER  # noqa: E402
import voxel  # noqa: E402

GEO = ROOT / "src/main/resources/assets/skyabyss/geo/entity"
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
OUTDIR = ROOT / "docs/review/lowpoly_roughs/lp_tide_heart"
SUBJ = "tide_heart"

S = np.array([1.0, 15.0, 0.0])      # 殻の渦の中心
C = np.array([0.0, 15.0, 0.0])      # 心臓の中心
CORE = np.array([0.0, 16.0, -4.0])  # 核の中心


def V(*a):
    return np.array(a, float)


def rad(a):
    return math.radians(a)


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
    """骨の cube の (中心, 大きさ) の一覧。"""
    for b in load_geo(phase)["bones"]:
        if b["name"] == bone:
            return [(np.array(c["origin"], float) + np.array(c["size"], float) / 2, np.array(c["size"], float))
                    for c in b.get("cubes", [])]
    return []


def smooth(a, b, x):
    t = min(max((x - a) / (b - a), 0.0), 1.0)
    return t * t * (3 - 2 * t)


# ------------------------------------------------------------------ 心臓 (核の極を持つ丸い塊)

HR = (7.1, 6.3, 5.7)   # 心臓の半径 (x, y, z)


def heart_deform(p, notch=1.5, tip=0.8):
    """局所座標 (中心原点) の点をハート形へ (上に切れ込み、下に尖り)。"""
    x, y, z = p
    if y > 0:
        y -= notch * math.exp(-(x / 2.2) ** 2) * smooth(0.5, 3.5, y)
    else:
        k = -y / HR[1]
        x = x * (1 - 0.20 * k)
        y -= tip * math.exp(-(x / 2.6) ** 2) * k
    return V(x, y, z)


def heart_pt(th, ph, lift=0.0, scale=1.0, notch=1.5, tip=0.8):
    """th: 核の極 (-Z) からの角度 (度)、ph: 方位 (度。0 = +x、反時計回り)。"""
    t, f = rad(th), rad(ph)
    d = V(math.sin(t) * math.cos(f), math.sin(t) * math.sin(f), -math.cos(t))
    p = heart_deform(d * np.array(HR) * scale, notch, tip)
    if lift:
        q = p / max(np.linalg.norm(p), 1e-6)
        p = p + q * lift
    return p + C


def blob(bone, mat, thetas, segs=12, dent=0.0, scale=1.0, notch=1.5, tip=0.8, ph0=15.0, dent_fn=None):
    """核の極から後ろの極までの輪 (thetas: 極を除く角度の列) をつないだ塊。
    dent: 奇数番の子午線を内へ引く深さ (放射状の溝)。"""
    ring_pts = []
    for ti, th in enumerate(thetas):
        row = []
        for k in range(segs):
            ph = ph0 + 360.0 * k / segs
            dd = 0.0
            if dent and k % 2 == 1:
                dd = -dent * math.sin(rad(th)) ** 2
            if dent_fn:
                dd += dent_fn(th, ph, k)
            row.append(heart_pt(th, ph, lift=dd, scale=scale, notch=notch, tip=tip))
        ring_pts.append(row)
    front = heart_pt(0, 0, scale=scale, notch=notch, tip=tip)
    back = heart_pt(180, 0, scale=scale, notch=notch, tip=tip)
    F = []
    for k in range(segs):
        k2 = (k + 1) % segs
        F += poly(bone, mat, [front, ring_pts[0][k2], ring_pts[0][k]], C)
        F += poly(bone, mat, [back, ring_pts[-1][k], ring_pts[-1][k2]], C)
        for i in range(len(thetas) - 1):
            F += poly(bone, mat, [ring_pts[i][k], ring_pts[i][k2], ring_pts[i + 1][k2], ring_pts[i + 1][k]], C)
    return [f for f in F if f]


# ------------------------------------------------------------------ 渦巻の帯

def spiral_xy(theta):
    return theta


def band(bone, mat, th0, th1, n, rc, hw, zt, zc, sides=8, phase=22.5, caps=(False, False), mats=None,
         jit=None):
    """渦の帯。th0→th1 (度。+x が 0、反時計回り)。rc/hw/zt/zc は theta の関数 (中心半径・半径方向の半幅・
    z の半厚・z の中心)。jit(i) -> (dr, dz) で 1 点ずつずらす。"""
    path, ra, rb = [], [], []
    for i in range(n + 1):
        th = th0 + (th1 - th0) * i / n
        r = rc(th)
        dz = 0.0
        if jit:
            dr, dz = jit(i)
            r += dr
        path.append(V(S[0] + r * math.cos(rad(th)), S[1] + r * math.sin(rad(th)), zc(th) + dz))
        ra.append(max(zt(th), 0.01))
        rb.append(max(hw(th), 0.01))
    return tube(bone, mat, path, ra, rb, sides=sides, hint=(0, 0, 1), phase=phase, caps=caps, mats=mats)


def band_pt(th, r, z):
    return V(S[0] + r * math.cos(rad(th)), S[1] + r * math.sin(rad(th)), z)


def strip(bone, mat, outer, inner, z0, z1, cen):
    """外周の点列 outer と内周の点列 inner (同じ数の (x, y)) を z0〜z1 の厚みの板にする。cen: 外向き判定の中心 (xy)。"""
    A = lambda p, z: V(p[0], p[1], z)
    c3 = V(cen[0], cen[1], 0)
    n = len(outer)
    F = []
    for i in range(n - 1):
        o0, o1, i0, i1 = outer[i], outer[i + 1], inner[i], inner[i + 1]
        m = (A(o0, 0) + A(o1, 0) + A(i0, 0) + A(i1, 0)) / 4
        F += poly(bone, mat, [A(o0, z0), A(o1, z0), A(i1, z0), A(i0, z0)], m + V(0, 0, 9))
        F += poly(bone, mat, [A(o0, z1), A(o1, z1), A(i1, z1), A(i0, z1)], m - V(0, 0, 9))
        F += poly(bone, mat, [A(o0, z0), A(o1, z0), A(o1, z1), A(o0, z1)], c3)
        F += poly(bone, mat, [A(i0, z0), A(i1, z0), A(i1, z1), A(i0, z1)], A(i0, 0) + (A(i0, 0) - c3) * 3)
    for idx, nb in ((0, 1), (n - 1, n - 2)):
        o, i_ = outer[idx], inner[idx]
        q = [A(o, z0), A(i_, z0), A(i_, z1), A(o, z1)]
        mid = (np.array(o) + np.array(i_)) / 2
        nbm = (np.array(outer[nb]) + np.array(inner[nb])) / 2
        F += poly(bone, mat, q, A(nbm, (z0 + z1) / 2))
    return [f for f in F if f]


def crescent(bone, mat, c, R, a0, a1, w, z0, z1, n=7):
    mid, half = (a0 + a1) / 2, (a1 - a0) / 2
    outer, inner = [], []
    for i in range(n + 1):
        a = a0 + (a1 - a0) * i / n
        rr = R - w * math.cos(math.pi / 2 * (a - mid) / half)
        outer.append((c[0] + R * math.cos(rad(a)), c[1] + R * math.sin(rad(a))))
        inner.append((c[0] + rr * math.cos(rad(a)), c[1] + rr * math.sin(rad(a))))
    return strip(bone, mat, outer, inner, z0, z1, c)


def gem(bone, mat, center, radii, rot_y=0.0, segs=4, rings=1):
    return ellipsoid(bone, mat, center, radii, segs=segs, rings=rings, rot=rot_y_m(rot_y))


def rot_y_m(a):
    return rot_y(a)


def twig(bone, mat, base, direction, length, r, bend=(0, 0, 0), sides=4, rings=2, hint=(0, 0, 1)):
    return spike(bone, mat, base, direction, length, r, sides=sides, rings=rings, bend=bend, hint=hint)


def branching(bone, mat, base, direction, length, r, forks, bend=(0, 0, 0), sides=4, seed=0, hint=(0, 0, 1)):
    """幹 + 枝 (forks: [(幹の上の位置 0〜1, 方位のずれ ベクトル, 長さの割合)])。"""
    d = nrm(direction)
    F = spike(bone, mat, base, d, length, r, sides=sides, rings=3, bend=bend, hint=hint)
    for t, off, lf in forks:
        p = np.asarray(base, float) + d * length * t + np.asarray(bend, float) * t * t
        dd = nrm(d + np.asarray(off, float))
        F += spike(bone, mat, p, dd, length * lf, r * (1 - t * 0.6), sides=sides, rings=2, hint=hint)
    return F


# ------------------------------------------------------------------ 描画ヘルパ

FIRE_CY = np.array([(24, 78, 110), (40, 140, 178), (80, 205, 230), (150, 240, 252), (222, 254, 255)])


def ramp(pal, v):
    pal = np.asarray(pal)
    return pal[np.clip(np.floor(v * len(pal)).astype(int), 0, len(pal) - 1)].astype(float)


def pick(pal, idx):
    pal = np.asarray(pal)
    return pal[np.clip(idx, 0, len(pal) - 1)].astype(float)


LIGHT = np.array([-0.35, 0.85, -0.4])
LIGHT = LIGHT / np.linalg.norm(LIGHT)


# ------------------------------------------------------------------ 絵

def paint(faces, size, P, N, M, ctx, phase):
    D, K, LU, LV, CW, CH, INS = edge_fields(faces, size)
    H = W = size
    yy, xx = np.mgrid[0:H, 0:W]
    fi = np.where(M >= 0, M, 0)
    owned = M >= 0
    mats = np.array([f.mat for f in faces])[fi]
    Pw, Nw = P, N
    bay = BAYER[yy % 4, xx % 4] - 0.5
    ny = Nw[..., 1]
    lit = Nw @ LIGHT
    edge = (D < 1.05) & INS
    convex = edge & (K > 24)
    concave = edge & (K < -24)
    area = (CW * CH).astype(float)
    col = np.zeros((H, W, 3))
    glow = np.zeros((H, W), bool)
    n_lo = vnoise(Pw * 0.16 + 3, 1)
    n_mid = vnoise(Pw * 0.45 + 11, 2)
    n_hi = vnoise(Pw * 0.9 + 5, 7)
    X, Y, Z = Pw[..., 0], Pw[..., 1], Pw[..., 2]

    def put(mask, c):
        nonlocal col
        col = np.where(mask[..., None], c, col)

    def stone(pal, base=0.5, lw=0.30, nw=0.14, tint=None):
        ns = len(pal)
        v = base + lw * lit + nw * (n_lo - 0.5) + 0.10 * (n_mid - 0.5) + 0.07 * bay
        idx = np.floor(v * ns).astype(int) + (convex & (lit > 0)) - (convex & (lit <= 0)) * 1 - concave
        c = pick(pal, idx)
        if tint is not None:
            c = c + (n_lo[..., None] - 0.5) * 2 * np.asarray(tint, float) * 1.0
        return np.clip(c, 0, 255), v

    # ---------------- 殻
    shell_like = np.isin(mats, ["shell", "rib", "lip", "plate"])
    c_sh, v_sh = stone(ctx["shell"], ctx.get("shell_base", 0.50), 0.32, 0.14, ctx.get("shell_tint"))
    c_rib, _ = stone(ctx.get("rib", ctx["shell"]), ctx.get("shell_base", 0.50) + 0.08, 0.30, 0.10, ctx.get("shell_tint"))
    c_lip, _ = stone(ctx.get("lip", ctx["shell"]), ctx.get("shell_base", 0.50) + 0.06, 0.30, 0.12, ctx.get("shell_tint"))
    put(mats == "shell", c_sh)
    put(mats == "plate", c_sh)
    put(mats == "rib", c_rib)
    put(mats == "lip", c_lip)
    # 割れ: 縁の光る割れ (段階が進むほど多く太い)
    cw = ctx["crack_w"][phase - 1]
    if cw > 0:
        n2 = vnoise(Pw * ctx.get("crack_f", 0.30) + 7, 3)
        n3 = vnoise(Pw * ctx.get("crack_f", 0.30) * 1.9 + 17, 5)
        cr = ((np.abs(n2 - 0.5) < cw) | ((np.abs(n3 - 0.5) < cw * 0.55) & (n2 > 0.5))) & shell_like & (area > 8)
        gv = 0.62 + 0.25 * (1 - np.abs(n2 - 0.5) / max(cw, 1e-6)) + 0.1 * bay
        put(cr, ramp(FIRE_CY, gv))
        glow |= cr
    # 溝の光 (凹んだ縁。段階ごとの確率)
    gg = ctx.get("groove_glow")
    if gg:
        pr = gg[phase - 1]
        on = concave & (hash1(fi, 3) < pr) & np.isin(mats, ctx.get("groove_mats", ["shell", "rib", "lip", "plate", "pearl", "petal"]))
        put(on, ramp(FIRE_CY, 0.55 + 0.1 * bay))
        glow |= on
    eg = ctx.get("edge_glow")
    if eg:
        on = (D < 0.9) & INS & (K > 40) & (hash1(fi, 6) < eg[phase - 1]) & np.isin(mats, ["shell", "lip", "rib"])
        put(on, ramp(FIRE_CY, 0.5 + 0.1 * bay))
        glow |= on
    # 色むら (珊瑚が付いた殻)
    if ctx.get("overgrow"):
        og = ctx["overgrow"]
        for k, (pal, thr, f, sd) in enumerate(og):
            ns = vnoise(Pw * f + sd, 4 + k)
            m = (ns > thr) & np.isin(mats, ["shell", "plate", "rib"]) & (ny > -0.4)
            idx = np.floor((ns - thr) * 9 + 1.2 * (0.5 + 0.5 * lit) + 0.7 * bay).astype(int)
            put(m, pick(pal, idx))

    # ---------------- 心臓の真珠 (cracks は核から放射)
    pe_pal = ctx["pearl"]
    c_pe, v_pe = stone(pe_pal, ctx.get("pearl_base", 0.52), 0.30, 0.10, ctx.get("pearl_tint"))
    heart_like = np.isin(mats, ["pearl", "petal"])
    if ctx.get("petal") is not None:
        c_pt, _ = stone(ctx["petal"], 0.56, 0.32, 0.10, ctx.get("pearl_tint"))
        put(mats == "petal", c_pt)
        c_pe, _ = stone(ctx.get("pearl_in", pe_pal), 0.50, 0.30, 0.10)
    put(mats == "pearl", c_pe)
    dx, dy = X - CORE[0], Y - CORE[1]
    rr = np.sqrt(dx * dx + dy * dy)
    ang = np.arctan2(dy, dx)
    hcr = ctx["h_crack"][phase - 1]
    if hcr > 0:
        nr = 12
        base_ang = ((np.floor((ang + math.pi) / (2 * math.pi) * nr + 0.5) / nr) * 2 * math.pi - math.pi)
        wob = 0.22 * np.sin(rr * 0.9 + base_ang * 5.0) + 0.1 * (vnoise(Pw * 0.5 + 2, 8) - 0.5)
        dang = np.abs(ang - base_ang - wob * 0.35)
        perp = rr * np.minimum(dang, 2 * math.pi - dang)
        keep = hash1(np.floor((base_ang + math.pi) / (2 * math.pi) * nr + 0.5) + 4, 5) < ctx["h_crack_prob"][phase - 1]
        crk = (perp < hcr * (1.0 - 0.35 * np.clip(rr / 9, 0, 1))) & keep & (rr > 1.8) & (Z < 1.5) & heart_like
        put(crk, ramp(FIRE_CY, 0.62 + 0.28 * (1 - np.clip(rr / 9, 0, 1)) + 0.1 * bay))
        glow |= crk
    # 点々 (紫の斑)
    if ctx.get("spot"):
        sp = ctx["spot"]
        cell = np.floor(Pw * sp["f"])
        h = hash1(cell[..., 0] * 3 + cell[..., 1] * 7 + cell[..., 2] * 13, 9)
        spot = (h < sp["p"]) & heart_like & (area > 6)
        put(spot, pick(sp["pal"], np.floor(1.5 + 1.5 * (0.5 + 0.5 * lit) + 0.7 * bay).astype(int)))
    # 描いた血管の網 (暗い線)
    if ctx.get("painted_vein"):
        pv = ctx["painted_vein"]
        a1 = vnoise(Pw * 0.34 + 31, 6)
        a2 = vnoise(Pw * 0.70 + 7, 7)
        vl = ((np.abs(a1 - 0.5) < pv["w"]) | ((np.abs(a2 - 0.5) < pv["w"] * 0.7) & (a1 > 0.45))) & heart_like & (area > 8)
        vi = np.floor(1.3 + 1.6 * (0.5 + 0.5 * lit) + 0.6 * bay).astype(int)
        put(vl, pick(pv["pal"], vi))
        if pv.get("glow") is not None and pv["glow"][phase - 1] > 0:
            gl = vl & (hash1(np.floor(Pw[..., 0] * 0.5) + np.floor(Pw[..., 1] * 0.5) * 5 + np.floor(Pw[..., 2] * 0.5) * 11, 3) < pv["glow"][phase - 1])
            put(gl, ramp(FIRE_CY, 0.5 + 0.1 * bay))
            glow |= gl

    # ---------------- 血管の隆起
    vp = ctx["vein"]
    vi = np.floor(1.2 + 2.2 * (0.5 + 0.5 * lit) + 0.7 * bay + 0.8 * convex).astype(int)
    put(mats == "vein", pick(vp, vi))
    vg = ctx["vein_glow"][phase - 1]
    if vg > 0:
        gl = (mats == "vein") & (vnoise(Pw * 0.55 + 5, 2) < vg)
        put(gl, ramp(FIRE_CY, 0.45 + 0.35 * (0.5 + 0.5 * np.sin(rr * 0.7)) + 0.1 * bay))
        glow |= gl

    # ---------------- 器官・珊瑚・棘
    cp = ctx["coral"]
    put(mats == "coral", pick(cp, np.floor(1.6 + 2.0 * (0.5 + 0.5 * lit) + 0.8 * bay + 0.8 * convex + 0.8 * (n_mid - 0.5)).astype(int)))
    fr = ctx["frond"]
    hsel = (hash1(fi, 17) * len(fr)).astype(int)
    c_fr = np.zeros((H, W, 3))
    for k, pal in enumerate(fr):
        c_fr = np.where((hsel == k)[..., None], pick(pal, np.floor(1.7 + 2.0 * (0.5 + 0.5 * lit) + 0.8 * bay + 0.7 * convex).astype(int)), c_fr)
    put(mats == "frond", c_fr)
    st = ctx["stud"]
    put(mats == "stud", pick(st, np.floor(1.4 + 2.2 * (0.5 + 0.5 * lit) + 0.8 * bay + 0.8 * convex).astype(int)))
    sd = ctx.get("shard", ctx["shell"])
    c_sd, _ = stone(sd, 0.56, 0.32, 0.12)
    put(mats == "shard", c_sd)
    cl, _ = stone(ctx.get("collar", ctx["shell"]), 0.50, 0.30, 0.10)
    put(mats == "collar", cl)
    orb, _ = stone(ctx.get("orbit", ctx["pearl"]), 0.62, 0.30, 0.06)
    put(mats == "orbit", orb)
    put(mats == "spine", pick(ctx.get("spine", ctx["stud"]), np.floor(1.2 + 2.6 * (0.5 + 0.5 * lit) + 0.7 * bay + 0.9 * convex).astype(int)))
    # 先端の発光 (鞭など)
    tipg = ctx.get("tip_glow")
    if tipg:
        tp = (mats == "frond") & (LV > CH * 0.0) & (hash1(fi, 33) < tipg)
        # 先 (島の端) だけを光らせる
        near = (D < 1.6) & INS & (K > 24) & (hash1(fi, 34) < 0.7)
        put(tp & near, ramp(FIRE_CY, 0.7 + 0.1 * bay))
        glow |= tp & near

    # ---------------- 内側の玉 (花弁の下)
    ip = ctx.get("inner")
    if ip is not None:
        iv = 0.28 + 0.22 * lit + 0.08 * (n_lo - 0.5) + 0.07 * bay
        put(mats == "inner", pick(ip, np.floor(iv * len(ip)).astype(int)))
        ig = ctx.get("inner_glow", [0, 0, 0])[phase - 1]
        if ig > 0:
            gl = (mats == "inner") & (vnoise(Pw * 0.5 + 9, 4) < ig)
            put(gl, ramp(FIRE_CY, 0.38 + 0.4 * (vnoise(Pw * 0.8, 3)) + 0.1 * bay))
            glow |= gl

    # ---------------- 光る面 (核・室・月・滴)
    d3 = np.sqrt(((Pw - CORE) ** 2).sum(-1))
    cr_ = ctx.get("core_r", 2.2)
    cv = np.clip(1.05 - d3 / cr_ * 0.65, 0, 1)
    put(mats == "core", ramp(FIRE_CY, cv * 0.55 + 0.45 + 0.05 * bay))
    glow |= mats == "core"
    wave = 0.5 + 0.5 * np.sin((np.arctan2(Y - S[1], X - S[0]) * 7.0) + np.sqrt((X - S[0]) ** 2 + (Y - S[1]) ** 2) * 0.8)
    cv2 = 0.42 + 0.30 * wave + 0.12 * (n_mid - 0.5) + 0.10 * bay + 0.1 * lit
    cm = mats == "chamber"
    put(cm, ramp(FIRE_CY, cv2))
    glow |= cm
    mm = mats == "moon"
    put(mm, ramp(FIRE_CY, 0.62 + 0.3 * (0.5 + 0.5 * lit) + 0.08 * bay))
    glow |= mm
    dm = mats == "drop"
    put(dm, ramp(FIRE_CY, 0.55 + 0.3 * (0.5 + 0.5 * lit) + 0.08 * bay))
    glow |= dm

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
    ctx = opt["ctx"]
    atlas, glow = paint(faces, size, P, N, M, ctx, phase)
    data = None
    if save:
        d = Path(outdir)
        d.mkdir(parents=True, exist_ok=True)
        Image.fromarray(atlas, "RGBA").save(d / f"p{phase}_atlas.png")
        gm = np.zeros((size, size, 4), np.uint8)
        gm[glow] = (255, 255, 255, 255)
        Image.fromarray(gm, "RGBA").save(d / f"p{phase}_glowmask.png")
        data = export_mesh(faces, size, d / f"p{phase}_mesh.json", f"tide_heart_p{phase}_lowpoly",
                           f"p{phase}_atlas.png", f"p{phase}_glowmask.png", bones)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, data=data, bones=bones, tris=tri_count(faces),
                by_bone={b: tri_count([f for f in faces if f.bone == b]) for b in sorted({f.bone for f in faces})})
