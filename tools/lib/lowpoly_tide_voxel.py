"""潮殻の巨神の低ポリ案 B「段になった珊瑚礁の島」(近景メッシュ) を、0.5 単位のセルの格子 (部品 * 32 + 材料) にする。
tools/gen/mobs/gen_tide_back_turtle.py が使う。tools/lib/lowpoly_whale_voxel.py と同じ方法: メッシュ表面を細かく点でなぞり、アトラスの色に
いちばん近い材料 (下の CANDIDATES。バニラのブロックの平均色) を選び、体の中を z の断面ごとに埋める。
座標は geo と同じ (1 単位 = 1/16 ブロック。+X が潮殻の左 = 潮門の側。頭は -Z)。"""
import json
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[2]
CDIR = ROOT / "docs/review/lowpoly_roughs/lp_tide_shell/b"
CELL = 2
OX, OY, OZ = 280, 0, 360          # 格子の原点 (セル)
SHAPE = (560, 310, 620)
PARTS = {"body": 1, "ruins": 2, "gate": 3, "coral": 4, "kelp": 5, "head": 6, "tail": 7,
         "fin_front_left": 8, "fin_front_right": 8, "fin_rear_left": 8, "fin_rear_right": 8}
HULL = [1, 6, 7]

# 材料 (番号は 1 から。最大 31) = (ブロック名, 平均色)。アトラスの色にいちばん近い物が選ばれる。色は tools/rough/block_colors.json
CANDIDATES = [
    ("smooth_sandstone", (224, 214, 170)), ("cut_sandstone", (218, 206, 160)), ("sandstone", (216, 203, 156)),
    ("white_terracotta", (210, 178, 161)), ("light_gray_terracotta", (135, 107, 98)), ("terracotta", (152, 94, 68)),
    ("mud_bricks", (137, 104, 79)), ("packed_mud", (142, 107, 80)),
    ("stone_bricks", (122, 122, 122)), ("andesite", (136, 136, 137)), ("polished_andesite", (132, 135, 134)),
    ("tuff", (108, 109, 103)), ("polished_deepslate", (72, 73, 73)), ("deepslate_tiles", (55, 55, 55)),
    ("prismarine", (98, 162, 146)), ("dark_prismarine", (52, 92, 76)), ("oxidized_cut_copper", (80, 154, 126)),
    ("moss_block", (89, 110, 45)), ("green_terracotta", (76, 83, 42)),
    ("pink_wool", (238, 141, 172)), ("orange_wool", (241, 118, 20)), ("orange_terracotta", (162, 84, 38)),
    ("purple_wool", (122, 42, 173)), ("purpur_block", (170, 126, 170)), ("yellow_wool", (249, 198, 40)),
    ("magenta_wool", (190, 69, 180)), ("calcite", (223, 224, 221)), ("blackstone", (42, 36, 41)),
]
GLOW_BLOCKS = [("sea_lantern", (172, 200, 191)), ("shroomlight", (241, 147, 71))]
NAMES = [n for n, _ in CANDIDATES] + [n for n, _ in GLOW_BLOCKS]
GLOW_BASE = len(CANDIDATES) + 1         # 材料番号 (1 始まり)
assert len(NAMES) <= 31
_PAL = np.array([c for _, c in CANDIDATES], float)


def materials(rgb, glow):
    """rgb (N, 3), glow (N,) -> 材料番号 (N,)"""
    d = ((rgb[:, None, :].astype(float) - _PAL[None]) ** 2).sum(2)
    m = d.argmin(1) + 1
    g = np.where(rgb[:, 2] > rgb[:, 0], GLOW_BASE, GLOW_BASE + 1)
    return np.where(glow, g, m)


def voxelize():
    m = json.load(open(CDIR / "near_mesh.json"))
    atlas = np.array(Image.open(CDIR / "near_atlas.png").convert("RGB"))
    glowm = np.array(Image.open(CDIR / "near_glowmask.png").convert("L"))
    H, W = atlas.shape[:2]
    pts, keys = [], []
    for bone, b in m["bones"].items():
        V = np.array(b["vertices"], float)
        UV = np.array(b["uvs"], float)
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
            mats = materials(atlas[py, px], glowm[py, px] > 40)
            pts.append(pp)
            keys.append(PARTS[bone] * 32 + mats)
    P = np.concatenate(pts)
    K = np.concatenate(keys)
    ix = np.floor(P[:, 0] * CELL + OX).astype(int)
    iy = np.floor(P[:, 1] * CELL + OY).astype(int)
    iz = np.floor(P[:, 2] * CELL + OZ).astype(int)
    ok = (ix >= 0) & (ix < SHAPE[0]) & (iy >= 0) & (iy < SHAPE[1]) & (iz >= 0) & (iz < SHAPE[2])
    ix, iy, iz, K = ix[ok], iy[ok], iz[ok], K[ok]
    lin = (ix * SHAPE[1] + iy) * SHAPE[2] + iz
    comb = lin.astype(np.int64) * 512 + K
    u, cnt = np.unique(comb, return_counts=True)
    cell = u // 512
    kk = u % 512
    order = np.lexsort((cnt, cell))
    cell, kk = cell[order], kk[order]
    last = np.r_[cell[1:] != cell[:-1], True]
    cell, kk = cell[last], kk[last]
    G = np.zeros(SHAPE, np.int16)
    G.reshape(-1)[cell] = kk
    surf = G != 0
    hullm = surf & np.isin(G // 32, HULL)
    # 体に触れている遺跡・塔・階段・珊瑚は、断面の輪の欠けをふさぐ (体の表面を上書きしている所がある)。中の材料は体の表面から取る
    near = ndimage.binary_dilation(hullm, iterations=3)
    hullf = hullm | (surf & near & ~np.isin(G // 32, [7, 8]))
    del near
    F = np.zeros(SHAPE, bool)
    for k in range(SHAPE[2]):
        sl = hullm[:, :, k]
        slf = hullf[:, :, k]
        if sl.any():
            sl2 = ndimage.binary_closing(np.pad(slf, 10), structure=np.ones((3, 3), bool), iterations=6)[10:-10, 10:-10]
            f = ndimage.binary_fill_holes(sl2)
            F[:, :, k] = f
            inner = f & ~surf[:, :, k]
            if inner.any():
                idx = ndimage.distance_transform_edt(~sl, return_distances=False, return_indices=True)
                col = G[:, :, k]
                G[:, :, k] = np.where(inner, col[idx[0], idx[1]], col)
    # 断面の輪が閉じずに埋まらなかった細い層 (z に 1〜6 セル) は、前後の層から埋める
    Fz = ndimage.binary_closing(np.pad(F, ((0, 0), (0, 0), (8, 8))), structure=np.ones((1, 1, 7), bool))[:, :, 8:-8]
    for k in range(SHAPE[2]):
        new = Fz[:, :, k] & ~F[:, :, k] & (G[:, :, k] == 0)
        if not new.any():
            continue
        col = G[:, :, k]
        for dk in range(1, 5):
            for kk in (k - dk, k + dk):
                if 0 <= kk < SHAPE[2]:
                    take = new & (col == 0) & (G[:, :, kk] != 0)
                    col[take] = G[:, :, kk][take]
    # 遺跡・塔・潮門は、部品ごとに 3 次元で埋める (塔は詰まった塊にする。部屋は Java が掘る)
    for part in (2, 3):
        pm = surf & (G // 32 == part)
        if not pm.any():
            continue
        xs, ys, zs = np.nonzero(pm)
        sl = (slice(max(xs.min() - 2, 0), xs.max() + 3), slice(max(ys.min() - 2, 0), ys.max() + 3),
              slice(max(zs.min() - 2, 0), zs.max() + 3))
        sub = pm[sl]
        f = ndimage.binary_fill_holes(sub)
        Gs = G[sl]
        inner = f & ~sub & (Gs == 0)
        idx = ndimage.distance_transform_edt(~sub, return_distances=False, return_indices=True)
        near = Gs[idx[0], idx[1], idx[2]]
        Gs[inner] = near[inner]
    return G


if __name__ == "__main__":
    from collections import Counter
    G = voxelize()
    print((G != 0).sum(), "cells")
    print(Counter((G[G != 0] // 32).tolist()))
    print({NAMES[k - 1]: v for k, v in sorted(Counter((G[G != 0] % 32).tolist()).items())})
