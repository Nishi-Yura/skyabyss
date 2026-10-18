"""岩鯨の低ポリ案 C (近景メッシュ) を、0.5 単位のセルの格子 (部品 * 16 + 材料) にする。
gen_titan_back_whale.py が使う。メッシュ表面を細かく点でなぞり、アトラスの色から材料を決め、中を埋める。
座標は geo と同じ (1 単位 = 1/16 ブロック。+X がゲームの鯨の左)。"""
import json
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[1]
CDIR = ROOT / "docs/review/lowpoly_roughs/lp_stone_whale/c"
CELL = 2
OX, OY, OZ = 160, 0, 270          # 格子の原点 (セル)
SHAPE = (320, 160, 600)
YC = 40.0
PARTS = {"head": 1, "jaw": 2, "body": 3, "tail": 4, "fin_left": 5, "fluke": 6, "ruins": 7, "spine": 8,
         "chain": 9, "fin_right": 10}
MAT = {"stone": 1, "belly": 2, "moss": 3, "dark": 4, "bone": 5, "fin": 6, "ruin": 7, "glyph": 8,
       "eye": 9, "cyan": 10, "iron": 11, "plate": 12, "baleen": 13, "brow": 14, "crust": 15}
# このスクリプトでの意味: stone = 青灰の皮、plate = 明るい岩の殻、crust = 暗い割れた岩、belly = 腹、dark = 口の中・暗い皮


def classify(bone, rgb, glow, y=40.0):
    r, g, b = [int(v) for v in rgb]
    if bone == "ruins":
        return "ruin"
    if bone == "spine":
        return "bone"
    if bone == "chain":
        return "iron"
    if bone in ("fin_left", "fin_right", "fluke"):
        return "fin"
    if glow:
        return "eye"
    lum = (r + g + b) / 3
    if g > r + 8 and g > b + 8:
        return "moss"
    if r > 235 and g > 225:
        return "bone"
    if b - r >= 14:
        return "dark" if lum < 62 else "stone"
    if (lum > 150 and abs(r - b) < 16) or y < YC - 9:     # 腹 (体の下側の岩色も腹)
        return "belly"
    return "crust" if lum < 80 else "plate"


def voxelize():
    m = json.load(open(CDIR / "near_mesh.json"))
    atlas = np.array(Image.open(CDIR / "near_atlas.png").convert("RGB"))
    glowm = np.array(Image.open(CDIR / "near_glowmask.png").convert("L"))
    H, W = atlas.shape[:2]
    pts, keys = [], []
    for bone, b in m["bones"].items():
        V = np.array(b["vertices"], float)
        UV = np.array(b["uvs"], float)
        hull = bone in ("head", "jaw", "body", "tail")
        for t in b["triangles"]:
            p = V[t]
            u = UV[t]
            e = max(np.linalg.norm(p[1] - p[0]), np.linalg.norm(p[2] - p[1]), np.linalg.norm(p[0] - p[2]))
            n = max(2, int(np.ceil(e / 0.2)))
            i, j = np.meshgrid(np.arange(n + 1), np.arange(n + 1), indexing="ij")
            sel = i + j <= n
            a = i[sel] / n
            c = j[sel] / n
            w = np.stack([1 - a - c, a, c], 1)
            pp = w @ p
            uu = w @ u
            px = np.clip((uu[:, 0] * W).astype(int), 0, W - 1)
            py = np.clip((uu[:, 1] * H).astype(int), 0, H - 1)
            rgb = atlas[py, px]
            gl = glowm[py, px] > 40
            pts.append(pp)
            if hull:
                mats = np.array([MAT[classify(bone, rgb[k], gl[k], pp[k, 1])] for k in range(len(rgb))])
            else:
                cm = len(rgb) // 2
                mats = np.full(len(rgb), MAT[classify(bone, rgb[cm], gl[cm])])
            keys.append(PARTS[bone] * 16 + mats)
    P = np.concatenate(pts)
    K = np.concatenate(keys)
    ix = np.floor(P[:, 0] * CELL + OX).astype(int)
    iy = np.floor(P[:, 1] * CELL + OY).astype(int)
    iz = np.floor(P[:, 2] * CELL + OZ).astype(int)
    ok = (ix >= 0) & (ix < SHAPE[0]) & (iy >= 0) & (iy < SHAPE[1]) & (iz >= 0) & (iz < SHAPE[2])
    ix, iy, iz, K = ix[ok], iy[ok], iz[ok], K[ok]
    lin = (ix * SHAPE[1] + iy) * SHAPE[2] + iz
    comb = lin.astype(np.int64) * 256 + K
    u, cnt = np.unique(comb, return_counts=True)
    cell = u // 256
    kk = u % 256
    order = np.lexsort((cnt, cell))
    cell, kk = cell[order], kk[order]
    last = np.r_[cell[1:] != cell[:-1], True]
    cell, kk = cell[last], kk[last]
    G = np.zeros(SHAPE, np.int16)
    G.reshape(-1)[cell] = kk
    surf = G != 0
    hullm = surf & np.isin(G // 16, [1, 2, 3, 4, 5, 9, 10])
    near = ndimage.binary_dilation(hullm, iterations=3)
    hullm = hullm | (surf & np.isin(G // 16, [7, 8]) & near)   # 体に触れている遺跡・背骨は輪の穴をふさぐ
    other = surf & ~hullm
    # 体は z の断面ごとに埋める (鼻先・尾の先の蓋が開いていても漏れない)。そのほかの部品は 3 次元で埋める
    filled = np.zeros_like(surf)
    for k in range(SHAPE[2]):
        sl = hullm[:, :, k]
        if sl.any():
            filled[:, :, k] = ndimage.binary_fill_holes(ndimage.binary_closing(np.pad(sl, 10), structure=np.ones((3, 3), bool), iterations=6)[10:-10, 10:-10])
    hullm_fill = filled.copy()
    filled |= ndimage.binary_fill_holes(other)
    inner = filled & ~surf
    # 中の部品・材料は近い表面から。体の輪の中は体 (頭・顎・胴・尾) の表面から取る (ひれ・錨石の根の部品にしない)
    hs = surf & np.isin(G // 16, [1, 2, 3, 4])
    ih = ndimage.distance_transform_edt(~hs, return_distances=False, return_indices=True)
    io = ndimage.distance_transform_edt(~other, return_distances=False, return_indices=True)
    fill_h = inner & hullm_fill
    fill_o = inner & ~fill_h
    Gh = G[ih[0], ih[1], ih[2]]
    Go = G[io[0], io[1], io[2]]
    G = np.where(fill_h, Gh, np.where(fill_o, Go, G)).astype(np.int16)
    return G


if __name__ == "__main__":
    from collections import Counter
    G = voxelize()
    print((G != 0).sum(), "cells")
    print(Counter((G[G != 0] % 16).tolist()))
    print(Counter((G[G != 0] // 16).tolist()))
