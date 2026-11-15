# -*- coding: utf-8 -*-
"""点の模型の見直し: すべての低ポリのメッシュを、ゲームの自動変換 (client/splat/SplatGen) とほぼ同じやり方で点にして、
数字 (点の数・透明で捨てた割合・真っ黒の割合・光る割合・絵が無い) と一覧の絵を作る。

  python -I tools/gen/splat/audit_auto.py            # docs/review/splat_audit/sheet_NN.png と audit.csv
手彫り・彫り直しの .bin があるものは、そちらを描く (ゲームで実際に出る方)。
"""
import csv
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import splat_lib as S  # noqa: E402
import view_bin  # noqa: E402

ASSETS = S.ROOT / "src/main/resources/assets/skyabyss"
OUT = S.ROOT / "docs/review/splat_audit"
TILE = 200
COLS = 10
ROWS = 8


def img(path):
    return np.array(Image.open(path).convert("RGBA")) if path.exists() else None


def auto_points(name, rng):
    d = json.loads((ASSETS / "lowpoly" / (name + ".json")).read_text(encoding="utf-8"))
    tex = d.get("texture", name).split(":")[-1]
    tex = tex[:-4] if tex.endswith(".png") else tex
    T_img = img(ASSETS / "textures/lowpoly" / (tex + ".png"))
    G = img(ASSETS / "textures/lowpoly" / (tex + "_glowmask.png"))
    if G is None:
        G = img(ASSETS / "textures/lowpoly" / (tex + "_glow.png"))
    tris, uvs = [], []
    for b in d["bones"].values():
        v = np.array(b["vertices"], float).reshape(-1, 3)
        uv = np.array(b["uvs"], float).reshape(-1, 2)
        t = np.array(b["triangles"], int).reshape(-1, 3)
        if len(t) == 0:
            continue
        if b.get("cap_vertex_start") is not None:
            t = t[(t < b["cap_vertex_start"]).all(1)]
        if (uv > 1.0001).any():
            uv = uv / np.array(d.get("texture_size", [256, 256]), float)
        tris.append(v[t])
        uvs.append(uv[t])
    stats = dict(name=name, tex=tex, missing_tex=T_img is None)
    if not tris:
        stats.update(points=0)
        return None, stats
    Tr = np.concatenate(tris)
    Uv = np.concatenate(uvs)
    allv = Tr.reshape(-1, 3)
    maxdim = (allv.max(0) - allv.min(0)).max()
    h = max(0.1, min(8.0, maxdim / 140))
    cap = 14000 if (d.get("socket") or "attach" in d) else 60000
    cr = np.cross(Tr[:, 1] - Tr[:, 0], Tr[:, 2] - Tr[:, 0])
    area = 0.5 * np.linalg.norm(cr, axis=1)
    if area.sum() / (h * h) > cap:
        h = math.sqrt(area.sum() / cap)
    n = np.minimum(np.ceil(area / (h * h)).astype(int), 4000)
    idx = np.repeat(np.arange(len(Tr)), np.maximum(n, 1))
    r1, r2 = rng.random(len(idx)), rng.random(len(idx))
    f = r1 + r2 > 1
    r1[f], r2[f] = 1 - r1[f], 1 - r2[f]
    w0 = 1 - r1 - r2
    P = w0[:, None] * Tr[idx, 0] + r1[:, None] * Tr[idx, 1] + r2[:, None] * Tr[idx, 2]
    # 色を引く uv は三角形の中心へ 12% 寄せる (SplatGen.UV_INSET)
    c0, c1, c2 = (x * 0.88 + 0.04 for x in (w0, r1, r2))
    U = c0[:, None] * Uv[idx, 0] + c1[:, None] * Uv[idx, 1] + c2[:, None] * Uv[idx, 2]
    N = cr[idx] / np.maximum(np.linalg.norm(cr[idx], axis=1, keepdims=True), 1e-9)
    if T_img is None:
        col = np.full((len(P), 3), 255.0)
        alpha = np.full(len(P), 255)
    else:
        th, tw = T_img.shape[:2]
        u = np.clip((U[:, 0] * tw).astype(int), 0, tw - 1)
        v = np.clip((U[:, 1] * th).astype(int), 0, th - 1)
        col = T_img[v, u, :3].astype(float)
        alpha = T_img[v, u, 3]
    lit = np.zeros(len(P), bool)
    if G is not None:
        gh, gw = G.shape[:2]
        gu = np.clip((U[:, 0] * gw).astype(int), 0, gw - 1)
        gv = np.clip((U[:, 1] * gh).astype(int), 0, gh - 1)
        lit = (G[gv, gu, 3] > 16) & (G[gv, gu, :3].sum(1) > 0)
    key = np.array([0.33, 0.85, -0.41])
    lam = np.clip(N @ key, 0, 1)
    L = 0.5 + 0.32 * lam + 0.18 * (0.5 + 0.5 * N[:, 1])
    shaded = np.where(lit[:, None], col, col * L[:, None])
    keep = alpha >= 128
    lum = col @ np.array([0.3, 0.59, 0.11])
    stats.update(points=int(keep.sum()), dropped=float(1 - keep.mean()), black=float((lum[keep] < 12).mean()) if keep.any() else 1.0,
                 glow=float(lit[keep].mean()) if keep.any() else 0.0, size=float(maxdim), h=float(h))
    return (P[keep], np.full(keep.sum(), h * 1.45), shaded[keep], np.where(lit[keep], 1, 0)), stats


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    names = sorted(str(p.relative_to(ASSETS / "lowpoly")).replace("\\", "/")[:-5] for p in (ASSETS / "lowpoly").rglob("*.json"))
    rng = np.random.default_rng(1)
    rows = []
    tiles = []
    for k, nm in enumerate(names):
        binp = ASSETS / "splat" / (nm + ".bin")
        if binp.exists():
            P, size, rgb, flags = view_bin.read3(binp)
            pts = (P, size, rgb, flags)
            st = dict(name=nm, points=len(P), source="bin")
        else:
            pts, st = auto_points(nm, rng)
            st["source"] = "auto"
        rows.append(st)
        t = Image.new("RGB", (TILE, TILE), (14, 12, 18))
        if pts is not None and len(pts[0]):
            tmp = OUT / "_tile.png"
            S.preview(pts[0], pts[1], pts[2], pts[3], tmp, yaw_list=(210,), px=TILE)
            t = Image.open(tmp).convert("RGB")
        ImageDraw.Draw(t).text((3, 3), nm[-34:], fill=(235, 235, 235))
        tiles.append(t)
        if k % 50 == 0:
            print(k, nm, flush=True)
    per = COLS * ROWS
    for si in range(0, len(tiles), per):
        sheet = Image.new("RGB", (COLS * TILE, ROWS * TILE))
        for j, t in enumerate(tiles[si:si + per]):
            sheet.paste(t, ((j % COLS) * TILE, (j // COLS) * TILE))
        sheet.save(OUT / ("sheet_%02d.png" % (si // per)))
    keys = ["name", "source", "points", "dropped", "black", "glow", "size", "h", "tex", "missing_tex"]
    with open(OUT / "audit.csv", "w", newline="", encoding="utf-8") as fo:
        w = csv.DictWriter(fo, fieldnames=keys, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow(r)
    (OUT / "_tile.png").unlink(missing_ok=True)
    print("done", len(names), "meshes,", (len(tiles) + per - 1) // per, "sheets")


if __name__ == "__main__":
    main()
