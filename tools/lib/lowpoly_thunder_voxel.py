"""雷翼の低ポリ案 A (嵐の大鷲) の近景メッシュを、0.5 単位のセルの格子 (骨 * 32 + 材料) にする。
tools/gen/items/gen_back_shape.py (bird) が使う。格子の決まりは install_thunder_wing.build_grid と同じ (骨の番号 BONE、材料 thunder_wing_titan.MAT、
左 = +X、1 単位 = 2 セル、原点 T.X0/Y0/Z0)。メッシュの座標は geo と同じ単位。
メッシュ表面を細かく点でなぞり、アトラスの色と光るマスクから材料を決め、骨ごとに中を埋める。"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
import thunder_wing_titan as T  # noqa: E402

ADIR = ROOT / "docs/review/lowpoly_roughs/lp_thunder_wing/a"
CELL = T.CELL
MAT = T.MAT
BONE = {"body": 1, "ruins": 2, "gate": 3, "neck": 4, "head": 5, "crest": 6, "wing_l": 7, "hand_l": 8,
        "wing_r": 9, "hand_r": 10, "tail": 11, "leg_l": 12, "leg_r": 13}
ORDER = ["body", "tail", "leg_l", "leg_r", "wing_l", "wing_r", "neck", "head", "crest", "hand_l", "hand_r",
         "ruins", "gate"]


def samples(step=0.2):
    m = json.load(open(ADIR / "near_mesh.json"))
    atlas = np.array(Image.open(ADIR / "near_atlas.png").convert("RGB"))
    glowm = np.array(Image.open(ADIR / "near_glowmask.png").convert("L"))
    H, W = atlas.shape[:2]
    out = {}
    for bone, b in m["bones"].items():
        if not b["triangles"]:
            continue
        V = np.array(b["vertices"], float)
        UV = np.array(b["uvs"], float)
        P, C, G = [], [], []
        for t in b["triangles"]:
            p, u = V[t], UV[t]
            e = max(np.linalg.norm(p[1] - p[0]), np.linalg.norm(p[2] - p[1]), np.linalg.norm(p[0] - p[2]))
            n = max(2, int(np.ceil(e / step)))
            i, j = np.meshgrid(np.arange(n + 1), np.arange(n + 1), indexing="ij")
            sel = i + j <= n
            a, c = i[sel] / n, j[sel] / n
            w = np.stack([1 - a - c, a, c], 1)
            uu = w @ u
            px = np.clip((uu[:, 0] * W).astype(int), 0, W - 1)
            py = np.clip((uu[:, 1] * H).astype(int), 0, H - 1)
            P.append(w @ p)
            C.append(atlas[py, px])
            G.append(glowm[py, px])
        out[bone] = (np.concatenate(P), np.concatenate(C), np.concatenate(G))
    return out


def classify(bone, rgb, glow):
    """アトラスの色 (と光るマスク) から材料 (thunder_wing_titan.MAT) を決める"""
    r, g, b = [int(v) for v in rgb]
    lum = (r + g + b) / 3.0
    warm = r - b
    if bone == "gate":
        if glow:
            return MAT["gate"]
        return MAT["copper"] if warm > 40 else MAT["stone"]
    if bone == "ruins":
        if glow:
            return MAT["glass"] if (b > r + 20 and lum < 200) else MAT["gate"]
        if warm > 40:
            return MAT["copper"]
        return MAT["stone"] if lum > 150 else MAT["cov"]
    if bone == "crest":
        return MAT["copper"]
    if bone == "head":
        if glow:
            return MAT["eye"]
        if warm > 40:
            return MAT["beak"] if lum > 110 else MAT["beak_d"]
        if lum < 60:
            return MAT["brow"]
    if bone in ("leg_l", "leg_r"):
        if lum < 40:
            return MAT["talon"]
        if warm > 30:
            return MAT["beak"] if lum > 130 else MAT["beak_d"]
    if bone in ("hand_l", "hand_r") and glow:
        if bone == "hand_r" and warm > 30:
            return MAT["knob_t"]
        if bone == "hand_l" and b >= r + 40 and g > 200:
            return MAT["knob_w"]
        return MAT["bolt"]
    if glow:
        return MAT["vein"] if lum < 215 else MAT["bolt"]
    if lum >= 205:
        return MAT["tip"]
    if lum >= 150:
        return MAT["under"]
    if lum >= 108:
        return MAT["cov"]
    if lum >= 84:
        return MAT["back"]
    if lum >= 62:
        return MAT["sec"]
    return MAT["prim"]


def _surface_cells(P, K, shape):
    ix = np.floor((P[:, 0] - T.X0) * CELL).astype(int)
    iy = np.floor((P[:, 1] - T.Y0) * CELL).astype(int)
    iz = np.floor((P[:, 2] - T.Z0) * CELL).astype(int)
    ok = (ix >= 0) & (ix < shape[0]) & (iy >= 0) & (iy < shape[1]) & (iz >= 0) & (iz < shape[2])
    return ix[ok], iy[ok], iz[ok], K[ok]


def voxelize():
    S = samples(0.2)
    shape = (T.NX, T.NY, T.NZ)
    G = np.zeros(shape, np.int16)
    fills, surfs = {}, {}
    for bone in ORDER:
        if bone not in S:
            continue
        P, C, GL = S[bone]
        mats = np.array([classify(bone, C[k], GL[k] > 40) for k in range(len(C))]) if len(C) < 0 else None
        # 色ごとに分類 (同じ色は同じ材料)
        q = (C.astype(np.int64) * 256 + 0)
        key = (C[:, 0].astype(np.int64) << 16) | (C[:, 1].astype(np.int64) << 8) | C[:, 2].astype(np.int64)
        key = key * 2 + (GL > 40)
        uk, inv = np.unique(key, return_inverse=True)
        lut = np.zeros(len(uk), np.int16)
        for n, kv in enumerate(uk):
            gl = kv & 1
            rgb = ((kv >> 1) >> 16 & 255, (kv >> 1) >> 8 & 255, (kv >> 1) & 255)
            lut[n] = classify(bone, rgb, bool(gl))
        mats = lut[inv]
        ix, iy, iz, mm = _surface_cells(P, mats, shape)
        lo = np.array([ix.min(), iy.min(), iz.min()]) - 3
        hi = np.array([ix.max(), iy.max(), iz.max()]) + 4
        lo = np.maximum(lo, 0)
        hi = np.minimum(hi, shape)
        sh = tuple(hi - lo)
        lin = ((ix - lo[0]) * sh[1] + (iy - lo[1])) * sh[2] + (iz - lo[2])
        comb = lin.astype(np.int64) * 64 + mm
        u, cnt = np.unique(comb, return_counts=True)
        cell, kk = u // 64, u % 64
        order = np.lexsort((cnt, cell))
        cell, kk = cell[order], kk[order]
        last = np.r_[cell[1:] != cell[:-1], True]
        loc = np.zeros(sh, np.int16)
        loc.reshape(-1)[cell[last]] = kk[last]
        surf = loc != 0
        closed = ndimage.binary_closing(np.pad(surf, 2), structure=np.ones((3, 3, 3), bool), iterations=1)[2:-2, 2:-2, 2:-2] | surf
        filled = ndimage.binary_fill_holes(closed)
        # 断面ごとに埋める (蓋が開いた輪も埋まる)。z と x の両方向
        for ax in (2, 0):
            for k in range(sh[ax]):
                sl2 = [slice(None)] * 3
                sl2[ax] = k
                sl2 = tuple(sl2)
                sec = closed[sl2]
                if sec.any():
                    filled[sl2] |= ndimage.binary_fill_holes(np.pad(sec, 1))[1:-1, 1:-1]
        inner = filled & ~surf
        ind = ndimage.distance_transform_edt(~surf, return_distances=False, return_indices=True)
        full = np.where(inner, loc[ind[0], ind[1], ind[2]], loc)
        b = BONE[bone]
        fills[bone] = (lo, hi, np.where(full != 0, full + b * 32, 0).astype(np.int16), surf)
        print(bone, "cells", int((full != 0).sum()), "surf", int(surf.sum()), flush=True)
    for bone in ORDER:       # 先に中身、あとから表面 (表面は骨の順で上書き)
        if bone in fills:
            lo, hi, full, surf = fills[bone]
            sl = tuple(slice(a, b) for a, b in zip(lo, hi))
            sub = G[sl]
            sub[(sub == 0) & (full != 0)] = full[(sub == 0) & (full != 0)]
    for bone in ORDER:
        if bone in fills:
            lo, hi, full, surf = fills[bone]
            sl = tuple(slice(a, b) for a, b in zip(lo, hi))
            sub = G[sl]
            sub[surf] = full[surf]
    return G


def cached_grid():
    import hashlib
    import tempfile
    h = hashlib.sha1()
    for f in (Path(__file__), ADIR / "near_mesh.json", ADIR / "near_atlas.png", ADIR / "near_glowmask.png"):
        h.update(f.read_bytes())
    cache = Path(tempfile.gettempdir()) / ("thunder_wing_a_grid_%s.npy" % h.hexdigest()[:12])
    if cache.exists():
        return np.load(cache)
    G = voxelize()
    np.save(cache, G)
    return G


if __name__ == "__main__":
    from collections import Counter
    G = cached_grid()
    print((G != 0).sum(), "cells")
    print(sorted(Counter((G[G != 0] // 32).tolist()).items()))
    print(sorted(Counter((G[G != 0] % 32).tolist()).items()))


GLOW_MATS = [MAT[k] for k in ("vein", "eye", "gate", "knob_w", "knob_t", "bolt", "glass")]


def recolor(O, A):
    """今の格子 O (形・骨の番号はそのまま。部屋の座標が頼っている) の表面の材料を、案 A のメッシュの格子 A の近い所から取る。
    A の光るセルは、同じ骨の O のいちばん近いセルに写す (細い筋が消えないように)。戻り値は新しい格子"""
    R = O.copy()
    for b in range(1, 14):
        om = (O // 32) == b
        am = (A // 32) == b
        if not om.any() or not am.any():
            continue
        oi = np.argwhere(om)
        ai = np.argwhere(am)
        lo = np.minimum(oi.min(0), ai.min(0))
        hi = np.maximum(oi.max(0), ai.max(0)) + 1
        sl = tuple(slice(a, c) for a, c in zip(lo, hi))
        om_, am_ = om[sl], am[sl]
        Ab = A[sl]
        ind = ndimage.distance_transform_edt(~am_, return_distances=False, return_indices=True)
        mat = (Ab[ind[0], ind[1], ind[2]] % 32).astype(np.int16)
        sub = R[sl]
        sub[om_] = b * 32 + mat[om_]
        # A の光るセル -> O のいちばん近いセル
        gl = am_ & np.isin(Ab % 32, GLOW_MATS)
        if gl.any():
            io = ndimage.distance_transform_edt(~om_, return_distances=False, return_indices=True)
            gi = np.argwhere(gl)
            tgt = io[:, gi[:, 0], gi[:, 1], gi[:, 2]]
            sub[tgt[0], tgt[1], tgt[2]] = b * 32 + (Ab[gi[:, 0], gi[:, 1], gi[:, 2]] % 32)
    return R
