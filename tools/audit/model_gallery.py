#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""全モデルの見本帳 (2026-11-12 の声「モデルがラフレベルになっているものは存在しない？ このmodの全モデル確認して」)。

ゲームが読む低ポリのメッシュ (assets/skyabyss/lowpoly/**.json) を、そのままテクスチャ付きで絵にして並べ、
ラフ止まりを見つける手がかりの数も出す。

    python -X utf8 -I tools/audit/model_gallery.py [名前の一部 ...]

出力: docs/review/model_audit/sheet_<組>_<番号>.png と model_audit.tsv (一つ一行)
数:
  tris     三角形の数
  box      軸にそろった面の割合 (直方体を積んだだけの形ほど高い)
  colors   テクスチャで実際に使われている色の数 (塗りが平らなほど少ない)
  px/blk   1 ブロックあたりのテクスチャの画素 (ぼやけ・粗さ)
"""
from __future__ import annotations

import csv
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough/lowpoly"))
sys.path.insert(0, str(ROOT / "tools/rough"))
import lpkit  # noqa: E402
import voxel  # noqa: E402

RES = ROOT / "src/main/resources/assets/skyabyss"
OUT = ROOT / "docs/review/model_audit"
TILE = 200
COLS = 8
ROWS = 6


def group(rel: str) -> str:
    if rel.startswith("items/"):
        return "item"
    if rel.startswith("armor/"):
        return "armor"
    n = Path(rel).stem
    for g in ("prop", "npc", "abyss", "king", "mount"):
        if n.startswith(g + "_"):
            return g
    return "mob"


def load(path: Path):
    d = json.loads(path.read_text(encoding="utf-8"))
    tex = RES / "textures/lowpoly" / (d["texture"] + ".png")
    if not tex.exists():
        return None, d, None, None
    im = Image.open(tex).convert("RGBA")
    S = max(im.size)
    if im.size[0] != im.size[1]:
        sq = Image.new("RGBA", (S, S))
        sq.paste(im, (0, 0))
        im = sq
    atlas = np.array(im)
    gm = RES / "textures/lowpoly" / (d["texture"] + "_glowmask.png")
    if d.get("glowmask"):
        g2 = RES / "textures/lowpoly" / d["glowmask"]
        if g2.exists():
            gm = g2
    if gm.exists():
        gi = np.array(Image.open(gm).convert("RGBA").resize((S, S), Image.NEAREST))
        glow = (gi[..., 3] > 0) & (gi[..., :3].max(-1) > 8)
    else:
        glow = np.zeros((S, S), bool)
    faces = []
    for bname, b in d["bones"].items():
        V = np.asarray(b.get("vertices") or [], float)
        if len(V) == 0:
            continue
        UV = np.asarray(b["uvs"], float) * S
        for t in b["triangles"]:
            vs = V[list(t)]
            n = np.cross(vs[1] - vs[0], vs[2] - vs[0])
            ln = np.linalg.norm(n)
            if ln < 1e-9:
                continue
            f = lpkit.Face(verts=vs, bone=bname, mat="", ref=vs.mean(0), normal=n / ln, uv=UV[list(t)])
            faces.append(f)
    return faces, d, atlas, glow


def metrics(faces, atlas, d):
    tris = len(faces)
    axis = 0
    area_all = 0.0
    px = []
    for f in faces:
        a = np.linalg.norm(np.cross(f.verts[1] - f.verts[0], f.verts[2] - f.verts[0])) / 2
        area_all += a
        if np.max(np.abs(f.normal)) > 0.995:
            axis += a
        uva = abs(np.cross(f.uv[1] - f.uv[0], f.uv[2] - f.uv[0])) / 2
        if a > 1e-6:
            px.append((math.sqrt(uva / a) * 16, a))
    box = axis / area_all if area_all else 0
    # 使われている色 (UV で触れる画素だけ)
    used = set()
    S = atlas.shape[0]
    for f in faces[:: max(1, len(faces) // 400)]:
        c = f.uv.mean(0)
        for p in (c, *f.uv):
            x, y = int(min(S - 1, max(0, p[0]))), int(min(S - 1, max(0, p[1])))
            used.add(tuple(atlas[y, x, :3] // 4))
    w = sum(a for _, a in px) or 1
    ppb = sum(v * a for v, a in px) / w
    return tris, box, len(used), ppb


def tile(faces, atlas, glow, label, sub):
    V = np.concatenate([f.verts for f in faces])
    lo, hi = V.min(0), V.max(0)
    c = (lo + hi) / 2
    ext = float(np.max(hi - lo))
    sc = (TILE - 30) / max(1.0, ext * 1.25)
    img = lpkit.render(faces, atlas, glow, 35, 18, (TILE, TILE - 24), c, sc, ss=1)
    t = Image.new("RGB", (TILE, TILE), (22, 24, 30))
    t.paste(img, (0, 24), img)
    dd = ImageDraw.Draw(t)
    dd.text((4, 2), label[:30], font=voxel.font(11), fill=(150, 232, 255))
    dd.text((4, 13), sub, font=voxel.font(10), fill=(200, 200, 160))
    return t


def main():
    flt = [a for a in sys.argv[1:] if not a.startswith("--")]
    files = sorted(p for p in (RES / "lowpoly").rglob("*.json"))
    OUT.mkdir(parents=True, exist_ok=True)
    rows = []
    groups = {}
    for p in files:
        rel = p.relative_to(RES / "lowpoly").as_posix()
        if flt and not any(s in rel for s in flt):
            continue
        faces, d, atlas, glow = load(p)
        if not faces:
            rows.append([rel, group(rel), 0, 0, 0, 0, "no texture or empty"])
            continue
        tris, box, colors, ppb = metrics(faces, atlas, d)
        rows.append([rel, group(rel), tris, round(box, 2), colors, round(ppb, 1), ""])
        name = rel[:-5].replace("items/", "").replace("armor/", "")
        groups.setdefault(group(rel), []).append(tile(faces, atlas, glow, name, f"{tris}t box{box:.2f} c{colors} {ppb:.0f}px"))
        print(rel, tris, flush=True)
    with open(OUT / "model_audit.tsv", "w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh, delimiter="\t")
        w.writerow(["file", "group", "tris", "box", "colors", "px_per_block", "note"])
        w.writerows(rows)
    per = COLS * ROWS
    for g, tiles in groups.items():
        for k in range(0, len(tiles), per):
            chunk = tiles[k:k + per]
            rws = (len(chunk) + COLS - 1) // COLS
            cv = Image.new("RGB", (COLS * TILE, rws * TILE), (10, 10, 14))
            for i, t in enumerate(chunk):
                cv.paste(t, ((i % COLS) * TILE, (i // COLS) * TILE))
            cv.save(OUT / f"sheet_{g}_{k // per + 1:02d}.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
