# -*- coding: utf-8 -*-
"""ボスの彫り直し: 低ポリのメッシュを中身の詰まった立体 (距離の場) に作り直し、点の模型として仕上げる。

1. メッシュの全部の三角形で、格子の各点の「巻き数」(下から上へ通り抜けた面の向きの和) を数え、中か外かを決める
   (部品が重なっていても、どれかの中なら中)。距離の場は中・外それぞれの距離変換の差。少しぼかして角と継ぎ目を丸める。
2. 表面を横切る格子の点を表面へ寄せて点にする (tools/gen/splat/splat_lib.py の竜と同じ)。
3. 色はメッシュの面に細かく撒いた点 (uv つき) の一番近いものから絵を引く。光る画素 (発光マスク) は光る点。
4. 陰は竜と同じ: 主光・空の明るさ・窪みの暗さ (距離の場で)・尾根の明るさ・谷の暗さを、5 段の明るさにディザで落とす。
5. 骨は一番近い面の骨。親子の骨の継ぎ目の近くは二本の骨に重みを分ける。
書き出しは assets/skyabyss/splat/<名前>.bin (あれば SplatGen の自動変換より優先される)。

使い方:
  python -I tools/gen/splat/resculpt.py lord_ash king_ash_c0 ...   # 名前は lowpoly/<名前>.json
  python -I tools/gen/splat/resculpt.py --bosses                    # BOSSES の全部
  --preview で docs/review/splat_bosses/<名前>.png に下見 (3 方向)
"""
import argparse
import json
import math
import sys
import time
from pathlib import Path

import numpy as np
from PIL import Image
from scipy.ndimage import distance_transform_edt, gaussian_filter
from scipy.spatial import cKDTree

sys.path.insert(0, str(Path(__file__).resolve().parent))
import splat_lib as S  # noqa: E402

ASSETS = S.ROOT / "src/main/resources/assets/skyabyss"
# 彫り直すボス (手彫りの lava_wyrm は除く)。巨神は大きすぎるので自動変換のまま
BOSSES = """bone_centipede bone_lancer crystal_lizard forge_giant fort_warlord ice_lord jelly_priestess lava_toad shell_octopus temple_keeper thunder_owl
stone_whale_heart_p1 stone_whale_heart_p2 stone_whale_heart_p3 tide_heart_p1 tide_heart_p2 tide_heart_p3 storm_heart_p1 storm_heart_p2 storm_heart_p3
ember_heart_p1 ember_heart_p2 ember_heart_p3 lord_ash lord_rib lord_salt lord_storm
king_ash_c0 king_ash_c1 king_ash_c2 king_bone_c0 king_bone_c1 king_bone_c2 king_salt_c0 king_salt_c1 king_salt_c2 king_shadow_c0 king_shadow_c1 king_shadow_c2
king_storm_c0 king_storm_c1 king_storm_c2 heart_warden feather_warden plume_warden scorched_warden shore_warden whalebone_captain elder_sentinel
rift_serpent moon_core tower_eye tower_master tower_moon pilgrim_eye pilgrim_hand pilgrim_moon pilgrim_plume last_bellkeeper full_keeper mirror_bearer
nameless_shape past_shadow heartbeat_mimic""".split()

LEVELS = np.array([0.5, 0.64, 0.78, 0.9, 1.0, 1.1])


def load_mesh(name):
    d = json.loads((ASSETS / "lowpoly" / (name + ".json")).read_text(encoding="utf-8"))
    tex = d.get("texture", name)
    tex = tex.split(":")[-1]
    tex = tex[:-4] if tex.endswith(".png") else tex
    bones, V, T, B, UV = [], [], [], [], []
    off = 0
    for bi, (bn, b) in enumerate(d["bones"].items()):
        bones.append(bn)
        v = np.array(b["vertices"], float).reshape(-1, 3)
        uv = np.array(b["uvs"], float).reshape(-1, 2)
        t = np.array(b["triangles"], int).reshape(-1, 3)
        if len(t) == 0:
            continue
        if (uv > 1.0001).any():
            sz = d.get("texture_size", [256, 256])
            uv = uv / np.array(sz, float)
        V.append(v)
        UV.append(uv)
        T.append(t + off)
        B.append(np.full(len(t), bi))
        off += len(v)
    parents = {bn: b.get("parent") for bn, b in d["bones"].items()}
    return dict(name=name, bones=bones, parents=parents, V=np.concatenate(V), UV=np.concatenate(UV), T=np.concatenate(T), B=np.concatenate(B), tex=tex)


def load_img(path):
    if not path.exists():
        return None
    return np.array(Image.open(path).convert("RGBA"))


def winding_grid(V, T, lo, shape, h):
    """格子の各点の巻き数 (z の下から上へ、面の向きつきで数える)。0 より大きければ中"""
    inc = np.zeros(shape, np.int32)
    A, Bv, C = V[T[:, 0]], V[T[:, 1]], V[T[:, 2]]
    nz = (Bv[:, 0] - A[:, 0]) * (C[:, 1] - A[:, 1]) - (Bv[:, 1] - A[:, 1]) * (C[:, 0] - A[:, 0])
    for i in np.nonzero(np.abs(nz) > 1e-12)[0]:
        a, b, c = A[i], Bv[i], C[i]
        x0 = int(math.ceil((min(a[0], b[0], c[0]) - lo[0]) / h - 0.5))
        x1 = int(math.floor((max(a[0], b[0], c[0]) - lo[0]) / h - 0.5))
        y0 = int(math.ceil((min(a[1], b[1], c[1]) - lo[1]) / h - 0.5))
        y1 = int(math.floor((max(a[1], b[1], c[1]) - lo[1]) / h - 0.5))
        if x1 < x0 or y1 < y0:
            continue
        xs = lo[0] + (np.arange(x0, x1 + 1) + 0.5) * h
        ys = lo[1] + (np.arange(y0, y1 + 1) + 0.5) * h
        X, Y = np.meshgrid(xs, ys, indexing="ij")
        # 2D の重心座標
        d = nz[i]
        w0 = ((b[0] - X) * (c[1] - Y) - (b[1] - Y) * (c[0] - X)) / d
        w1 = ((c[0] - X) * (a[1] - Y) - (c[1] - Y) * (a[0] - X)) / d
        w2 = 1 - w0 - w1
        m = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
        if not m.any():
            continue
        z = w0 * a[2] + w1 * b[2] + w2 * c[2]
        k = np.clip(np.ceil((z - lo[2]) / h - 0.5).astype(int), 0, shape[2] - 1)
        # 下から入る面 (法線が -z) で +1、出る面で -1
        s = -1 if nz[i] > 0 else 1
        ii, jj = np.nonzero(m)
        np.add.at(inc, (ii + x0, jj + y0, k[ii, jj]), s)
    return np.cumsum(inc, axis=2)


def sample_surface(V, T, Bi, UV, h, rng):
    """面に一様に撒いた点 (位置・uv・骨・面の法線)"""
    A, Bv, C = V[T[:, 0]], V[T[:, 1]], V[T[:, 2]]
    cr = np.cross(Bv - A, C - A)
    area = 0.5 * np.linalg.norm(cr, axis=1)
    n = np.maximum(1, np.ceil(area / (h * h) * 3)).astype(int)
    idx = np.repeat(np.arange(len(T)), n)
    r1 = rng.random(len(idx))
    r2 = rng.random(len(idx))
    flip = r1 + r2 > 1
    r1[flip], r2[flip] = 1 - r1[flip], 1 - r2[flip]
    w0 = 1 - r1 - r2
    P = w0[:, None] * A[idx] + r1[:, None] * Bv[idx] + r2[:, None] * C[idx]
    ua, ub, uc = UV[T[idx, 0]], UV[T[idx, 1]], UV[T[idx, 2]]
    U = w0[:, None] * ua + r1[:, None] * ub + r2[:, None] * uc
    N = cr[idx] / np.maximum(np.linalg.norm(cr[idx], axis=1, keepdims=True), 1e-12)
    return P, U, Bi[idx], N


def resculpt(name, preview=False, h_div=170.0, max_points=70000):
    t0 = time.time()
    m = load_mesh(name)
    V, T = m["V"], m["T"]
    rng = np.random.default_rng(abs(hash(name)) % (2 ** 31))
    lo0, hi0 = V.min(0), V.max(0)
    maxdim = (hi0 - lo0).max()
    h = maxdim / h_div
    # 面積から点の数を見積もり、多すぎれば格子を粗く
    A, Bv, C = V[T[:, 0]], V[T[:, 1]], V[T[:, 2]]
    area = 0.5 * np.linalg.norm(np.cross(Bv - A, C - A), axis=1).sum() * 0.75
    if area / (h * h) > max_points:
        h = math.sqrt(area / max_points)
    pad = 4 * h
    lo = lo0 - pad
    shape = np.ceil((hi0 + pad - lo) / h).astype(int) + 1
    wind = winding_grid(V, T, lo, shape, h)
    inside = wind > 0
    D = (distance_transform_edt(~inside) - distance_transform_edt(inside)) * h
    D = D + 0.5 * h * (D < 0)  # 境目を面の上へ (中の点は半分ずれている)
    D = gaussian_filter(D.astype(np.float32), 0.8)
    sc = S.Sculpt(name, m["bones"], h=h)
    sc.D, sc.lo, sc.shape = D, lo, shape
    # 表面の点
    ins = D < 0
    cross = np.zeros_like(ins)
    for ax in range(3):
        a = np.swapaxes(ins, 0, ax)
        c = np.swapaxes(cross, 0, ax)
        diff = a[1:] != a[:-1]
        c[1:] |= diff
        c[:-1] |= diff
    cross &= np.abs(D) < h * 1.2
    ijk = np.argwhere(cross)
    P = ijk * h + lo + rng.uniform(-0.35, 0.35, ijk.shape) * h
    for _ in range(3):
        n = sc.grad(P)
        P = P - n * sc.sample(P)[:, None]
    n = sc.grad(P)
    # 色・骨: 面に撒いた点の一番近いもの
    SP, SU, SB, SN = sample_surface(V, T, m["B"], m["UV"], h * 0.5, rng)
    tree = cKDTree(SP)
    dist, nn = tree.query(P, k=8)
    near = nn[:, 0]
    tex = load_img(ASSETS / "textures/lowpoly" / (m["tex"] + ".png"))
    glow = load_img(ASSETS / "textures/lowpoly" / (m["tex"] + "_glowmask.png"))
    if glow is None:
        glow = load_img(ASSETS / "textures/lowpoly" / (m["tex"] + "_glow.png"))
    th, tw = tex.shape[:2]
    u = np.clip((SU[near, 0] * tw).astype(int), 0, tw - 1)
    v = np.clip((SU[near, 1] * th).astype(int), 0, th - 1)
    col = tex[v, u, :3].astype(float)
    alpha = tex[v, u, 3]
    lit = np.zeros(len(P), bool)
    if glow is not None:
        gh, gw = glow.shape[:2]
        gu = np.clip((SU[near, 0] * gw).astype(int), 0, gw - 1)
        gv = np.clip((SU[near, 1] * gh).astype(int), 0, gh - 1)
        gp = glow[gv, gu]
        lit = (gp[:, 3] > 16) & (gp[:, :3].sum(1) > 0)
    # 骨と継ぎ目の重み (親子の骨だけ混ぜる)
    bones = m["bones"]
    bi = {b: i for i, b in enumerate(bones)}
    par = np.array([bi.get(m["parents"].get(b)) if m["parents"].get(b) in bi else -1 for b in bones])
    A_ = SB[near]
    Bb = A_.copy()
    W = np.ones(len(P))
    for k in range(1, 8):
        other = SB[nn[:, k]]
        related = (other != A_) & ((par[A_] == other) | (par[other] == A_)) & (Bb == A_)
        wo = 0.5 * np.clip(1 - dist[:, k] / (2.0 * h), 0, 1)
        sel = related & (wo > 0)
        Bb[sel] = other[sel]
        W[sel] = 1 - wo[sel]
    # 陰
    key = np.array([0.35, 0.9, -0.55])
    key /= np.linalg.norm(key)
    lam = np.clip(n @ key, 0, 1)
    hemi = 0.5 + 0.5 * n[:, 1]
    ao = sc.ao(P, n)
    L = np.clip((0.32 + 0.26 * hemi + 0.46 * lam) * (0.55 + 0.45 * ao), 0, 1)
    # 暗い色の模型は形が沈むので、色の明るさに応じて底を上げ、縁 (横を向いた面) に冷たい照り返しを足す
    lum = col @ np.array([0.3, 0.59, 0.11]) / 255.0
    lift = np.clip(0.22 - lum, 0, 0.22)[:, None]
    rim = np.clip(1 - np.abs(n[:, 1]) - 0.35, 0, 1)[:, None] * (lum < 0.3)[:, None]
    col = col + lift * np.array([110, 112, 130]) + rim * np.array([26, 30, 42])
    lap = sc.curvature(P)
    ridge = (lap > 2.4 / max(h * 4, 1.0)) & (lam > 0.2)
    valley = lap < -1.2 / max(h * 4, 1.0)
    dither = S.BAYER4[(ijk[:, 0] + ijk[:, 2]) % 4, ijk[:, 1] % 4]
    f = np.clip(L * (len(LEVELS) - 2) + (dither - 0.5) * 0.9, 0, len(LEVELS) - 2)
    lv = LEVELS[np.rint(f).astype(int)]
    lv = np.where(ridge, np.minimum(lv + 0.12, LEVELS[-1]), lv)
    lv = np.where(valley, lv * 0.7, lv)
    shaded = np.where(lit[:, None], col, np.clip(col * lv[:, None], 0, 255))
    keep = alpha >= 128
    P, n, shaded, A_, Bb, W, lit = P[keep], n[keep], shaded[keep], A_[keep], Bb[keep], W[keep], lit[keep]
    N = len(P)
    size = h * 1.45 * rng.uniform(0.88, 1.12, N)
    flags = np.where(lit, S.F_GLOW, 0).astype(np.int32)
    # にじみ (光る点の一部に柔らかい丸)
    g = np.nonzero(lit)[0]
    gi = g[rng.random(len(g)) < 0.12]
    hP = P[gi] + n[gi] * h
    allP = np.concatenate([P, hP])
    allS = np.concatenate([size, rng.uniform(5, 8, len(gi)) * h])
    allC = np.concatenate([shaded, shaded[gi] * 0.09])
    allA = np.concatenate([A_, A_[gi]])
    allB = np.concatenate([Bb, Bb[gi]])
    allW = np.concatenate([W, W[gi]])
    allF = np.concatenate([flags, np.full(len(gi), S.F_HALO)])
    allN = np.concatenate([n, np.zeros((len(gi), 3))])
    out = ASSETS / "splat" / (name + ".bin")
    S.write_bin3(out, bones, allP, allS, S.argb(allC), allA, allB, allW, allF, N=allN)
    print(f"{name}: {N} splats (+{len(gi)} halo) h={h:.2f} grid {tuple(shape)} {time.time() - t0:.1f}s")
    if preview:
        pv = S.ROOT / "docs/review/splat_bosses" / (name + ".png")
        pv.parent.mkdir(parents=True, exist_ok=True)
        S.preview(allP, allS, allC, allF, pv, yaw_list=(200, 150, 90), px=520)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="*")
    ap.add_argument("--bosses", action="store_true")
    ap.add_argument("--preview", action="store_true")
    a = ap.parse_args()
    names = BOSSES if a.bosses else a.names
    for nm in names:
        if not (ASSETS / "lowpoly" / (nm + ".json")).exists():
            print("skip (no mesh)", nm)
            continue
        resculpt(nm, a.preview)


if __name__ == "__main__":
    main()
