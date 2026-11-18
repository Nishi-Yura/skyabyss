# -*- coding: utf-8 -*-
"""あいだに座すもの (tower_master) の塗り直し (2026-11-12 の声「ボスのモデルが他のボスと同じになっているか」)。

主は 1/5 の大きさで作ってゲームで 5 倍に描くので、絵の細かさも 1/5 (1 ブロックに約 6 画素) で、近くで見ると一色の平らな面だった。
形 (tower_master_c.py) はそのまま、絵だけを
  - 2 倍の細かさ (lpkit.TEX 2 → 4。アトラス 2048 → 4096)
  - 材質ごとの模様 (黒い石: 割れ目の網と層の筋・星の粒 / 白い衣: 布目と縦のひだの陰 / 金: 刻みの帯と縁の光 /
    大理石: 灰の脈 / 鉄: 鋲の列と打ち跡 / 歯: 根元の陰)
で描き直す。出力は docs/review/cloud_roughs/tower_master/a/ (gen_cloud_mobs.py の取り込み元)。

    python -X utf8 tools/rough/lowpoly/cloud/tower_master_repaint.py           (描いて review/tower_master_repaint.png に前後を並べる)
    python -X utf8 tools/rough/lowpoly/cloud/tower_master_repaint.py install   (上のあと、gen_cloud_mobs.install(["tower_master"]) で入れる)
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import tower_master_c as TC  # noqa: E402
import mk  # noqa: E402
import lpkit  # noqa: E402

ck = mk._moonmk._ck
ROOT = HERE.parents[3]
OUTD = ROOT / "docs/review/cloud_roughs/tower_master/a"
TEX_NEW = 4.0


def h3(P, seed):
    """3D の格子点の乱数 (0..1)"""
    q = np.floor(P).astype(np.int64)
    v = (q[..., 0] * 73856093) ^ (q[..., 1] * 19349663) ^ (q[..., 2] * 83492791) ^ (seed * 2654435761)
    return ((v & 0xFFFFFF) / float(0xFFFFFF))


def cells(P, s, seed):
    """3D のセルの F2 - F1 (境目で 0)"""
    q = P / s
    i0 = np.floor(q)
    f1 = np.full(q.shape[:-1], 9.0)
    f2 = np.full(q.shape[:-1], 9.0)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                c = i0 + np.array([dx, dy, dz])
                j = np.stack([h3(c, seed + 1), h3(c, seed + 2), h3(c, seed + 3)], -1)
                d = np.linalg.norm(q - (c + j), axis=-1)
                nf1 = np.minimum(f1, d)
                f2 = np.where(d < f1, f1, np.minimum(f2, d))
                f1 = nf1
    return f2 - f1


def shade(col, sel, k):
    col[sel, :3] = np.clip(col[sel, :3].astype(float) * k, 0, 255).astype(np.uint8)


def tint(col, sel, rgb, a):
    c = col[sel, :3].astype(float)
    col[sel, :3] = np.clip(c * (1 - a) + np.asarray(rgb, float) * a, 0, 255).astype(np.uint8)


def detail(faces, atlas, glow, P, N, M):
    """材質ごとの模様を足す (P = 画素の 3D 位置 (ラフの単位)、N = 法線、M = 面の番号)"""
    mats = np.array([f.mat for f in faces] + [""])
    mat = mats[M]                     # M = -1 は末尾の "" を引く
    own = (M >= 0) & (atlas[..., 3] > 0) & ~glow
    X, Y, Z = P[..., 0], P[..., 1], P[..., 2]
    S = 1.0                           # ラフの単位 (1 ブロック = 3.2) で模様の大きさを決める
    # 黒い石: 大きな割れ目の網 (暗い)、その縁の明るい欠け、層の筋、細かな粒
    st = own & (mat == "stone")
    if st.any():
        e1 = cells(P, 14 * S, 3)
        e2 = cells(P, 5 * S, 4)
        shade(atlas, st & (e1 < 0.05), 0.45)
        shade(atlas, st & (e1 >= 0.05) & (e1 < 0.09), 1.22)
        shade(atlas, st & (e2 < 0.04), 0.72)
        band = (np.sin(Y * 0.55 + np.sin(X * 0.13 + Z * 0.11) * 2.2) > 0.82)
        shade(atlas, st & band, 0.86)
        grit = h3(P * 1.6, 9) > 0.93
        shade(atlas, st & grit, 1.18)
        # 割れ目の奥の小さな星 (光る)
        star = st & (e1 < 0.03) & (h3(P * 0.9, 11) > 0.86)
        tint(atlas, star, (210, 228, 255), 0.95)
        glow |= star
    # 白い衣: 縦のひだの陰と光、布目、裾の汚れ
    rb = own & (mat == "robe")
    if rb.any():
        fold = np.sin(X * 0.42 + Z * 0.37 + np.sin(Y * 0.05) * 1.5)
        shade(atlas, rb & (fold > 0.55), 0.84)
        shade(atlas, rb & (fold < -0.75), 1.08)
        weave = ((np.floor(X * 2.2) + np.floor(Y * 2.2) + np.floor(Z * 2.2)) % 2) == 0
        shade(atlas, rb & weave, 0.96)
        dirt = rb & (Y < np.percentile(Y[rb], 20)) & (h3(P * 0.7, 21) > 0.4)
        tint(atlas, dirt, (120, 112, 100), 0.35)
    # 金: 刻みの帯 (二本の暗い線のあいだに点の列)、縁の光、打ち跡
    gd = own & (mat == "gold")
    if gd.any():
        t = (Y * 0.9 + (X + Z) * 0.25) % 6.0
        shade(atlas, gd & ((np.abs(t - 1.0) < 0.22) | (np.abs(t - 2.6) < 0.22)), 0.62)
        dots = gd & (np.abs(t - 1.8) < 0.3) & (((np.floor(X * 1.4) + np.floor(Z * 1.4)) % 3) == 0)
        shade(atlas, dots, 1.25)
        shade(atlas, gd & (h3(P * 1.3, 31) > 0.95), 0.8)
        facing = N[..., 1] > 0.6
        shade(atlas, gd & facing, 1.1)
    # 大理石: 灰の脈
    mb = own & (mat == "marble")
    if mb.any():
        vein = np.abs(np.sin(X * 0.21 + Y * 0.17 + 3.0 * np.sin(Z * 0.09 + X * 0.05))) < 0.06
        tint(atlas, mb & vein, (110, 110, 120), 0.55)
        shade(atlas, mb & (cells(P, 9 * S, 41) < 0.04), 0.8)
    # 鉄: 鋲の列と打ち跡
    ir = own & (mat == "iron")
    if ir.any():
        rivet = (((X * 0.5) % 3.0) < 0.6) & (((Y * 0.5) % 3.0) < 0.6)
        shade(atlas, ir & rivet, 1.3)
        shade(atlas, ir & (h3(P * 1.1, 51) > 0.9), 0.75)
    # 面当て・歯: 細かなひび
    vl = own & np.isin(mat, ["veil", "teeth"])
    if vl.any():
        shade(atlas, vl & (cells(P, 6 * S, 61) < 0.035), 0.7)
    return atlas, glow


def build(tex):
    lpkit.TEX = tex
    ck.TEX = tex
    faces, eyes = TC.build("a")
    size, P, N, M, L = lpkit.build_atlas(faces, 2048, 0)
    atlas, glow = ck.paint(faces, size, P, N, M, TC.PAL, eyes)
    return faces, eyes, size, atlas, glow, P, N, M


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "review"
    faces, eyes, size, atlas, glow, P, N, M = build(TEX_NEW)
    atlas, glow = detail(faces, atlas, glow, P, N, M)
    print("atlas", size, "tris", sum(len(f.verts) - 2 for f in faces), flush=True)
    OUTD.mkdir(parents=True, exist_ok=True)
    Image.fromarray(atlas, "RGBA").save(OUTD / "tower_master_a_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(OUTD / "tower_master_a_glowmask.png")
    ck.export_mesh(faces, size, OUTD / "tower_master_a_mesh.json", "tower_master_a", "tower_master_a_atlas.png", TC.BONES,
                   {"glowmask": "tower_master_a_glowmask.png"})
    if mode == "install":
        sys.path.insert(0, str(ROOT / "tools/gen/finale"))
        import os
        os.chdir(ROOT)
        import gen_cloud_mobs as G
        G.install(["tower_master"])


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
