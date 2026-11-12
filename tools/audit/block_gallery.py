#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ブロックの立体モデル (models/block/*.json の elements) の見本帳 (2026-11-12、全モデルの確かめ)。model_gallery.py の組。

    python -X utf8 -I tools/audit/block_gallery.py

出力: docs/review/model_audit/sheet_block_<番号>.png
"""
from __future__ import annotations

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
TILE, COLS, ROWS = 200, 8, 6
CELL = 32  # 一枚のテクスチャを置く升の大きさ


def tex_path(ref: str) -> Path | None:
    ns, _, p = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
    base = RES if ns == "skyabyss" else None
    if base is None:
        return None
    f = base / "textures" / (p + ".png")
    return f if f.exists() else None


def resolve(d):
    """親をたどって textures と elements をそろえる"""
    tex, elems = {}, None
    chain = []
    cur = d
    while cur is not None:
        chain.append(cur)
        par = cur.get("parent")
        cur = None
        if par and par.startswith("skyabyss:"):
            pf = RES / "models" / (par.split(":", 1)[1] + ".json")
            if pf.exists():
                cur = json.loads(pf.read_text(encoding="utf-8"))
    for c in reversed(chain):
        tex.update(c.get("textures", {}))
        if "elements" in c:
            elems = c["elements"]
    return tex, elems


def lookup(tex, key):
    seen = 0
    while key.startswith("#") and seen < 8:
        key = tex.get(key[1:], "")
        seen += 1
    return key


DIRS = {"north": ((0, 0, -1)), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def face_quad(fr, to, name):
    x0, y0, z0 = fr
    x1, y1, z1 = to
    # 頂点の並び: 左上・右上・右下・左下 (外から見て)、uv の (u0,v0)-(u1,v1) に合わせる
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[name]


def default_uv(fr, to, name):
    x0, y0, z0 = fr
    x1, y1, z1 = to
    return {"north": (16 - x1, 16 - y1, 16 - x0, 16 - y0), "south": (x0, 16 - y1, x1, 16 - y0), "west": (z0, 16 - y1, z1, 16 - y0),
            "east": (16 - z1, 16 - y1, 16 - z0, 16 - y0), "up": (x0, z0, x1, z1), "down": (x0, 16 - z1, x1, 16 - z0)}[name]


def rot_pts(P, r):
    if not r:
        return P
    o = np.asarray(r.get("origin", (8, 8, 8)), float)
    a = math.radians(r.get("angle", 0))
    ax = r.get("axis", "y")
    c, s = math.cos(a), math.sin(a)
    M = {"x": np.array([[1, 0, 0], [0, c, -s], [0, s, c]]), "y": np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
         "z": np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])}[ax]
    return (P - o) @ M.T + o


def build(path: Path):
    d = json.loads(path.read_text(encoding="utf-8"))
    tex, elems = resolve(d)
    if not elems:
        return None
    keys = []
    for e in elems:
        for fn, f in e.get("faces", {}).items():
            k = lookup(tex, f.get("texture", ""))
            if k not in keys:
                keys.append(k)
    n = max(1, len(keys))
    side = math.ceil(math.sqrt(n))
    S = side * CELL
    atlas = np.zeros((S, S, 4), np.uint8)
    glow = np.zeros((S, S), bool)
    slot = {}
    for i, k in enumerate(keys):
        ox, oy = (i % side) * CELL, (i // side) * CELL
        slot[k] = (ox, oy)
        tp = tex_path(k)
        if tp:
            im = Image.open(tp).convert("RGBA")
            im = im.crop((0, 0, im.width, im.width)).resize((CELL, CELL), Image.NEAREST)
            atlas[oy:oy + CELL, ox:ox + CELL] = np.array(im)
        else:
            atlas[oy:oy + CELL, ox:ox + CELL] = (255, 0, 255, 255)
    faces = []
    for e in elems:
        fr, to = e["from"], e["to"]
        for fn, f in e.get("faces", {}).items():
            k = lookup(tex, f.get("texture", ""))
            ox, oy = slot[k]
            u0, v0, u1, v1 = f.get("uv") or default_uv(fr, to, fn)
            q = np.asarray(face_quad(fr, to, fn), float)
            q = rot_pts(q, e.get("rotation"))
            uv = np.array([(u0, v0), (u1, v0), (u1, v1), (u0, v1)], float)
            r = f.get("rotation", 0) // 90
            uv = np.roll(uv, -r, axis=0)
            uv = uv / 16 * CELL + (ox, oy)
            uv = np.clip(uv, (ox, oy), (ox + CELL - 0.01, oy + CELL - 0.01))
            nrm = np.cross(q[1] - q[0], q[3] - q[0])
            ln = np.linalg.norm(nrm)
            if ln < 1e-9:
                continue
            faces.append(lpkit.Face(verts=q / 1.0, bone="b", mat="", ref=q.mean(0), normal=nrm / ln, uv=uv))
    return faces, atlas, glow, len(elems)


def main():
    files = sorted((RES / "models/block").glob("*.json"))
    tiles = []
    for p in files:
        try:
            r = build(p)
        except Exception as ex:  # noqa: BLE001
            print("skip", p.name, ex)
            continue
        if not r:
            continue
        faces, atlas, glow, ne = r
        V = np.concatenate([f.verts for f in faces])
        lo, hi = V.min(0), V.max(0)
        c = (lo + hi) / 2
        sc = (TILE - 30) / max(1.0, float(np.max(hi - lo)) * 1.3)
        img = lpkit.render(faces, atlas, glow, 35, 22, (TILE, TILE - 24), c, sc, ss=1)
        t = Image.new("RGB", (TILE, TILE), (22, 24, 30))
        t.paste(img, (0, 24), img)
        dd = ImageDraw.Draw(t)
        dd.text((4, 2), p.stem[:30], font=voxel.font(11), fill=(150, 232, 255))
        dd.text((4, 13), f"{ne} cubes", font=voxel.font(10), fill=(200, 200, 160))
        tiles.append(t)
        print(p.name, ne, flush=True)
    per = COLS * ROWS
    for k in range(0, len(tiles), per):
        chunk = tiles[k:k + per]
        rws = (len(chunk) + COLS - 1) // COLS
        cv = Image.new("RGB", (COLS * TILE, rws * TILE), (10, 10, 14))
        for i, t in enumerate(chunk):
            cv.paste(t, ((i % COLS) * TILE, (i // COLS) * TILE))
        cv.save(OUT / f"sheet_block_{k // per + 1:02d}.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
