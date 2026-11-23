# -*- coding: utf-8 -*-
"""設計の生成スクリプトが from_gen.py で読めない模型 (王・淵の王・心臓・後半のボスなど、別の道具で作ったもの) を、
入っている低ポリのメッシュから熔岩の大蛇と同じやり方で彫る。

from_gen.py との違いは部品の取り方だけ: 生成スクリプトの代わりに、メッシュの骨ごとの三角形を、位置でつないだ「ひとかたまり」
(= 設計の部品一つ。部品はどれも閉じた形で、重なっても頂点は共有しない) に分け、一つずつ距離の場にする。
そのあとは from_gen.py と同じ: 骨ごとに部品を滑らかにつなぐ (小さい部品ほど鋭く)、材質ごとに表面を彫って描く (materials.py)、
大蛇と同じ強い光と陰。材質は絵の色から見分ける (hand_detail.classify)。色は絵から取る。

  python -I tools/gen/splat/mesh_sculpt.py <名前> ... [--preview] [--budget 90000]
"""
import argparse
import json
import math
import sys
import time
from pathlib import Path

import numpy as np
from PIL import Image
from scipy.spatial import cKDTree

sys.path.insert(0, str(Path(__file__).resolve().parent))
import splat_lib as S  # noqa: E402
import from_gen as FG  # noqa: E402
import materials as MT  # noqa: E402
import hand_detail as HD  # noqa: E402

ASSETS = S.ROOT / "src/main/resources/assets/skyabyss"
FAM_OF_CLASS = {HD.FLESH: "flesh", HD.STONE: "stone", HD.PALE: "bone", HD.EYE: "eye", HD.GLOWY: "jelly", HD.METAL: "metal", HD.OTHER: "plain"}


class Face:
    """三角形一つ (from_gen.sdf_mesh が読む形)"""

    def __init__(self, v):
        self.verts = v
        self.ref = v.mean(0)

    def tris(self):
        return [(0, 1, 2)]


def components(V, T):
    """位置でつないだ三角形のかたまり"""
    key = np.round(V, 3)
    _, inv = np.unique(key, axis=0, return_inverse=True)
    inv = inv.reshape(-1)
    parent = np.arange(inv.max() + 1)

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    for t in T:
        a, b, c = find(inv[t[0]]), find(inv[t[1]]), find(inv[t[2]])
        parent[b] = a
        parent[find(c)] = a
    roots = np.array([find(inv[t[0]]) for t in T])
    out = {}
    for i, r in enumerate(roots):
        out.setdefault(r, []).append(i)
    return list(out.values())


def load(name):
    d = json.loads((ASSETS / "lowpoly" / (name + ".json")).read_text(encoding="utf-8"))
    tex = d.get("texture", name).split(":")[-1]
    tex = tex[:-4] if tex.endswith(".png") else tex
    img = np.array(Image.open(ASSETS / "textures/lowpoly" / (tex + ".png")).convert("RGBA"))
    gp = ASSETS / "textures/lowpoly" / (tex + "_glowmask.png")
    if not gp.exists():
        gp = ASSETS / "textures/lowpoly" / (tex + "_glow.png")
    glow = np.array(Image.open(gp).convert("RGBA")) if gp.exists() else None
    return d, img, glow


# 紙の切り絵の見た目 (docs の boss paper style: 黒・生成り・桃) のボス。材質の模様を彫らず、平らな紙のまま強い光と陰だけ
PAPER = set("pilgrim_eye pilgrim_hand pilgrim_moon pilgrim_plume tower_eye tower_master tower_moon moon_core mirror_bearer full_keeper last_bellkeeper rift_serpent".split())


def sculpt_mesh(name, preview=False, budget=90000.0, out_name=None):
    paper = name in PAPER
    t0 = time.time()
    d, img, glow = load(name)
    th, tw = img.shape[:2]
    bones = list(d["bones"].keys())
    allV = []
    per = []
    for bi, (bn, b) in enumerate(d["bones"].items()):
        V = np.array(b["vertices"], float).reshape(-1, 3)
        T = np.array(b["triangles"], int).reshape(-1, 3)
        uv = np.array(b["uvs"], float).reshape(-1, 2)
        # 塞ぎの面 (cap_vertex_start 以降) も使う: 部品を閉じた形にするための面で、捨てると槍の柄などがまるごと抜ける
        if len(T) == 0:
            continue
        if (uv > 1.0001).any():
            uv = uv / np.array(d.get("texture_size", [tw, th]), float)
        per.append((bi, bn, V, T, uv))
        allV.append(V[T.reshape(-1)])
    allv = np.concatenate(allV)
    ext = allv.max(0) - allv.min(0)
    maxdim = float(ext.max())
    area = 2 * (ext[0] * ext[1] + ext[1] * ext[2] + ext[0] * ext[2])
    h = max(0.18, min(maxdim / 150.0, (area / budget) ** 0.5), (area / (budget * 1.6)) ** 0.5)
    rng = np.random.default_rng(abs(hash(name)) % (2 ** 31))
    key = np.array([0.35, 0.9, -0.55])
    key /= np.linalg.norm(key)
    Ps, ns, cols, flagsL, As = [], [], [], [], []
    for bi, bn, V, T, uv in per:
        # 色の引き当て: この骨の面に撒いた点 (uv つき)
        SP, SU, SB, SN = RSurface(V, T, uv, h * 0.5, rng)
        tree = cKDTree(SP)

        def colour(Q):
            nn = tree.query(Q)[1]
            u = np.clip((SU[nn, 0] * tw).astype(int), 0, tw - 1)
            v = np.clip((SU[nn, 1] * th).astype(int), 0, th - 1)
            c = img[v, u]
            lit = np.zeros(len(Q), bool)
            if glow is not None:
                gh, gw = glow.shape[:2]
                g = glow[np.clip((SU[nn, 1] * gh).astype(int), 0, gh - 1), np.clip((SU[nn, 0] * gw).astype(int), 0, gw - 1)]
                lit = (g[:, 3] > 16) & (g[:, :3].sum(1) > 0)
            return c[:, :3].astype(float), lit, c[:, 3]
        sc = S.Sculpt(name, bones, h=h)
        for comp in components(V, T):
            faces = [Face(V[T[i]]) for i in comp]
            r = FG.sdf_mesh(faces, h * 0.4)
            if r is None:
                continue
            f0, box = r
            vv = V[T[comp].reshape(-1)]
            size = float(np.min(vv.max(0) - vv.min(0))) / 2
            c_mid, lit_mid, _ = colour(vv.mean(0)[None])
            k = float(np.clip(size * 0.28, 0.12, 1.4))
            if lit_mid[0]:
                k = min(k, 0.25)
            sc.add(((lambda P, f0=f0: f0(P) - 0.55 * h), (box[0] - h, box[1] + h)), "x", bn, k=k, glow=bool(lit_mid[0]))
        if not sc.parts:
            continue
        sc.evaluate(margin=2.5 * h)
        band = np.argwhere(np.abs(sc.D) < 2.6 * h)
        if len(band):
            Q = band * h + sc.lo
            cq, lq, _ = colour(Q)
            fams = np.array([FAM_OF_CLASS[c] for c in HD.classify(cq)])
            if paper:
                fams[:] = "plain"
            sc.D[band[:, 0], band[:, 1], band[:, 2]] += MT.relief(Q, fams, h, lq).astype(sc.D.dtype)
        P, n, pid1, pid2, gap, ijk = sc.surface(seed=bi + 1)
        if len(P) == 0:
            continue
        col, lit, alpha = colour(P)
        keep = alpha >= 128
        P, n, ijk, col, lit = P[keep], n[keep], ijk[keep], col[keep], lit[keep]
        fams = np.array([FAM_OF_CLASS[c] for c in HD.classify(col)])
        if paper:
            fams[:] = "plain"
        lam = np.clip(n @ key, 0, 1)
        hemi = 0.5 + 0.5 * n[:, 1]
        ao = sc.ao(P, n)
        lap = sc.curvature(P)
        Lv = np.clip((0.06 + 0.3 * hemi + 0.68 * lam) * (0.38 + 0.62 * ao), 0, 1)
        dither = S.BAYER4[(ijk[:, 0] + ijk[:, 2]) % 4, ijk[:, 1] % 4]
        # 暗い色の模型は形が沈むので底を上げる
        lum = col @ np.array([0.3, 0.59, 0.11]) / 255.0
        col = col + np.clip(0.2 - lum, 0, 0.2)[:, None] * np.array([110, 112, 130])
        col, lit, shine = MT.paint(P, n, fams, col, lit, h, lap, key)
        lv = np.array([0.5, 0.64, 0.8, 0.93, 1.06, 1.16])
        f = np.clip(Lv * (len(lv) - 1) + (dither - 0.5) * 0.9, 0, len(lv) - 1)
        lvl = lv[np.rint(f).astype(int)]
        lvl = np.where(lap > 0.5 / h, np.minimum(lvl + 0.12, lv[-1]), np.where(lap < -0.3 / h, lvl * 0.72, lvl))
        out = np.where(lit[:, None], col, np.clip(col * lvl[:, None], 0, 255))
        out = np.where(shine[:, None], out * 0.35 + np.array([255, 248, 238]) * 0.65, out)
        Ps.append(P)
        ns.append(n)
        cols.append(out)
        flagsL.append(np.where(lit, S.F_GLOW, 0))
        As.append(np.full(len(P), bi))
    P = np.concatenate(Ps)
    n = np.concatenate(ns)
    col = np.concatenate(cols)
    flags = np.concatenate(flagsL).astype(np.int32)
    A = np.concatenate(As)
    N = len(P)
    size = h * 1.42 * rng.uniform(0.9, 1.1, N)
    g = np.nonzero(flags & S.F_GLOW)[0]
    gi = g[rng.random(len(g)) < min(0.12, 400.0 / max(1, len(g)))]
    allP = np.concatenate([P, P[gi] + n[gi] * h])
    allS = np.concatenate([size, rng.uniform(3.5, 5.5, len(gi)) * h])
    allC = np.concatenate([col, col[gi] * 0.07])
    allA = np.concatenate([A, A[gi]])
    allF = np.concatenate([flags, np.full(len(gi), S.F_HALO)])
    allN = np.concatenate([n, np.zeros((len(gi), 3))])
    out = ASSETS / "splat" / ((out_name or name) + ".bin")
    S.write_bin(out, bones, allP, allS, S.argb(allC), allA, allA, np.ones(len(allP)), allF, N=allN)
    print(f"{out_name or name}: {N} splats (+{len(gi)} halo) h={h:.2f} {time.time() - t0:.1f}s")
    if preview:
        pv = S.ROOT / "docs/review/splat_handsculpt" / ((out_name or name) + ".png")
        pv.parent.mkdir(parents=True, exist_ok=True)
        S.preview(allP, allS, allC, allF, pv, yaw_list=(200, 150, 90), px=600)


def RSurface(V, T, uv, h, rng):
    import resculpt as RS
    return RS.sample_surface(V, T, np.zeros(len(T), int), uv, h, rng)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="+")
    ap.add_argument("--preview", action="store_true")
    ap.add_argument("--budget", type=float, default=90000.0)
    ap.add_argument("--titan", action="store_true", help="巨神: 近い段のメッシュから近・中・遠の 3 段を書く")
    a = ap.parse_args()
    for nm in a.names:
        if a.titan:
            for suffix, pts in (("", 220000.0), ("_mid", 80000.0), ("_far", 30000.0)):
                sculpt_mesh(nm, a.preview and suffix == "", pts, out_name=nm + suffix)
        else:
            sculpt_mesh(nm, a.preview, a.budget)


if __name__ == "__main__":
    main()
