"""余燼の巨神の低ポリ案 C (皮が岩になる火蜥蜴) の近景メッシュを、0.5 単位のセルの格子 (部品 * 16 + 材料、uint8) にする。
gen_ember_back_body.py が使う (lowpoly_whale_voxel.py と同じ作り)。座標は geo と同じ (1 単位 = 1/16 ブロック)。
メッシュ表面を細かい点でなぞり、アトラスの色から材料を決め、z の断面ごとに中を埋める (中の材料は断面の中で近い表面から)。"""
import json
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[1]
CDIR = ROOT / "docs/review/lowpoly_roughs/lp_ember_titan/c"
CELL = 2
OX, OY, OZ = 220, 8, 420
SHAPE = (440, 300, 920)
PARTS = {"body": 1, "ridge": 2, "head": 3, "jaw": 4, "leg_fl": 5, "leg_fr": 6, "leg_rl": 7, "leg_rr": 8, "tail": 9}
MAT = {"shell": 1, "skin": 2, "redskin": 3, "belly": 4, "lava": 5, "molten": 6, "bone": 7, "mouth": 8}
HULL = [3, 4]           # 頭・顎: 断面の穴埋め (口の隙間は開けたまま)
SHELL = [1, 2, 9]       # 胴・背・尾: 断面の列ごとに、いちばん下の表面からいちばん上の表面まで埋める (火口は開いた鉢なので穴埋めでは埋まらない)
LEGS = [5, 6, 7, 8]


def classify(rgb, glow):
    r, g, b = [int(v) for v in rgb]
    lum = (r + g + b) / 3
    if glow or (r > 200 and 60 < g < 210 and b < 90):
        return "molten" if g > 170 else "lava"
    if r > 195 and g > 185 and b > 160:
        return "bone"
    if r > 85 and g < 45 and b < 40:
        return "mouth"          # 冷めかけた割れ目・口の中の赤
    if lum < 80:
        return "shell"
    if lum > 150:
        return "belly"
    if r - b > 28 and r > g + 30:
        return "redskin"
    return "skin"


def voxelize():
    m = json.load(open(CDIR / "near_mesh.json"))
    atlas = np.array(Image.open(CDIR / "near_atlas.png").convert("RGB"))
    glowm = np.array(Image.open(CDIR / "near_glowmask.png").convert("L"))
    H, W = atlas.shape[:2]
    pts, keys = [], []
    for bone, b in m["bones"].items():
        if bone not in PARTS:
            continue          # 煙は置かない (粒で出す)
        V = np.array(b["vertices"], float)
        UV = np.array(b["uvs"], float)
        for t in b["triangles"]:
            p, u = V[t], UV[t]
            e = max(np.linalg.norm(p[1] - p[0]), np.linalg.norm(p[2] - p[1]), np.linalg.norm(p[0] - p[2]))
            n = max(2, int(np.ceil(e / 0.2)))
            i, j = np.meshgrid(np.arange(n + 1), np.arange(n + 1), indexing="ij")
            sel = i + j <= n
            a, c = i[sel] / n, j[sel] / n
            w = np.stack([1 - a - c, a, c], 1)
            pp, uu = w @ p, w @ u
            px = np.clip((uu[:, 0] * W).astype(int), 0, W - 1)
            py = np.clip((uu[:, 1] * H).astype(int), 0, H - 1)
            rgb, gl = atlas[py, px], glowm[py, px] > 40
            pts.append(pp)
            keys.append(PARTS[bone] * 16 + np.array([MAT[classify(rgb[k], gl[k])] for k in range(len(rgb))]))
    P, K = np.concatenate(pts), np.concatenate(keys)
    ix = np.floor(P[:, 0] * CELL + OX).astype(int)
    iy = np.floor(P[:, 1] * CELL + OY).astype(int)
    iz = np.floor(P[:, 2] * CELL + OZ).astype(int)
    ok = (ix >= 0) & (ix < SHAPE[0]) & (iy >= 0) & (iy < SHAPE[1]) & (iz >= 0) & (iz < SHAPE[2])
    ix, iy, iz, K = ix[ok], iy[ok], iz[ok], K[ok]
    lin = (ix * SHAPE[1] + iy) * SHAPE[2] + iz
    comb = lin.astype(np.int64) * 256 + K
    u, cnt = np.unique(comb, return_counts=True)
    cell, kk = u // 256, u % 256
    order = np.lexsort((cnt, cell))
    cell, kk = cell[order], kk[order]
    last = np.r_[cell[1:] != cell[:-1], True]
    G = np.zeros(SHAPE, np.uint8)
    G.reshape(-1)[cell[last]] = kk[last]
    del pts, keys, P, K
    st = np.ones((3, 3), bool)
    out = G.copy()
    for k in range(SHAPE[2]):
        sl = G[:, :, k]
        if not sl.any():
            continue
        mask = np.isin(sl // 16, SHELL) & (sl != 0)
        if mask.any():
            rows = mask.any(1)
            lo = np.argmax(mask, axis=1)
            hi = mask.shape[1] - 1 - np.argmax(mask[:, ::-1], axis=1)
            ys = np.arange(mask.shape[1])[None, :]
            fl = rows[:, None] & (ys >= lo[:, None]) & (ys <= hi[:, None])
            inner = fl & ~mask & (out[:, :, k] == 0)
            if inner.any():
                idx = ndimage.distance_transform_edt(~mask, return_distances=False, return_indices=True)
                lab = sl[idx[0], idx[1]]
                out[:, :, k] = np.where(inner, lab, out[:, :, k])
        for grp in (HULL, LEGS):
            mask = np.isin(sl // 16, grp) & (sl != 0)
            if not mask.any():
                continue
            fl = ndimage.binary_fill_holes(ndimage.binary_closing(np.pad(mask, 6), st, iterations=1)[6:-6, 6:-6])
            inner = fl & ~mask & (out[:, :, k] == 0)
            if not inner.any():
                continue
            idx = ndimage.distance_transform_edt(~mask, return_distances=False, return_indices=True)
            lab = sl[idx[0], idx[1]]
            out[:, :, k] = np.where(inner, lab, out[:, :, k])
    return out


if __name__ == "__main__":
    from collections import Counter
    G = voxelize()
    print((G != 0).sum(), "cells")
    print(Counter((G[G != 0] % 16).tolist()))
