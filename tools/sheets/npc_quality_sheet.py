#!/usr/bin/env python3
"""NPC (人の形) の見た目の比べ表。入っているメッシュ (assets/lowpoly/<名>.json) を絵 (textures/lowpoly/<絵>.png) で描く。
一人につき 3/4 前・横・後ろ・顔の接写の 4 枚。語り部を基準に並べて、作りの差を見る (docs/review/npc_quality_2026-10-23.md)。

  python -I tools/sheets/npc_quality_sheet.py <出力.png> [メッシュ:絵:見出し ...]
  見出しの指定が無ければ、既定の一覧 (語り部・鯨追い・職人・係の人・町の人・生き残り) を描く。
  --ref <git の参照> を付けると、その参照のメッシュと絵で描く (直す前の比べ用)。
"""
from __future__ import annotations

import io
import json
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "audit"))
import watertight_sheet as WS  # noqa: E402

ROOT = WS.ROOT
ASSETS = "src/main/resources/assets/skyabyss"

DEFAULT = [
    ("storyteller", "storyteller", "語り部 (基準)"),
    ("whale_chaser", "whale_chaser", "鯨追いイサナ"),
    ("artisan", "artisan_ch1", "旅の職人 (一章)"),
    ("attendant_a", "attendant_a_guild_clerk", "組合の書記"),
    ("attendant_a", "attendant_a_smith", "鍛冶師"),
    ("attendant_a", "attendant_a_innkeeper", "宿の女将"),
    ("attendant_a", "attendant_a_stable_keeper", "厩舎の番"),
    ("attendant_a", "attendant_a_survivor_bone", "灯り番ミオ"),
    ("attendant_a", "attendant_a_abyss_porter", "荷運びロク"),
    ("attendant_a", "guest_baker", "町の人 (パン屋)"),
    ("attendant_a", "guest_child", "町の人 (子ども)"),
]


def read(ref, rel, binary=False):
    if ref is None:
        p = os.path.join(ROOT, rel)
        if not os.path.exists(p):
            return None
        return open(p, "rb").read()
    out = subprocess.run(["git", "show", "%s:%s" % (ref, rel)], cwd=ROOT, capture_output=True)
    return out.stdout if out.returncode == 0 else None


def load(ref, mesh, tex):
    raw = read(ref, "%s/lowpoly/%s.json" % (ASSETS, mesh))
    if raw is None:
        return None, None
    data = json.loads(raw.decode("utf-8"))
    t = read(ref, "%s/textures/lowpoly/%s.png" % (ASSETS, tex))
    atlas = np.array(Image.open(io.BytesIO(t)).convert("RGBA")) if t else None
    return data, atlas


def bounds(data):
    mats = WS.pose_matrices(data, {})
    pts = []
    for n, b in data["bones"].items():
        V = np.array(b.get("vertices", []), float).reshape(-1, 3)
        if len(V):
            pts.append((np.c_[V, np.ones(len(V))] @ mats[n].T)[:, :3])
    P = np.vstack(pts)
    return P.min(0), P.max(0)


def head_center(data):
    b = data["bones"].get("head")
    if not b or not b.get("vertices"):
        lo, hi = bounds(data)
        return ((lo + hi) / 2 + np.array([0, (hi[1] - lo[1]) * 0.35, 0])).tolist()
    V = np.array(b["vertices"], float).reshape(-1, 3)
    return ((V.min(0) + V.max(0)) / 2).tolist()


def cell(data, atlas, title, w=200, h=300):
    lo, hi = bounds(data)
    ht = max(hi[1] - lo[1], 1.0)
    cen = [0, (lo[1] + hi[1]) / 2, 0]
    sc = h * 0.86 / ht
    # 前 = +Z のメッシュ (root を 180 度回す語り部・鯨追い) と、前 = -Z のメッシュ (係の人など) で見る向きを替える
    rot = (data["bones"].get("root", {}).get("rotation") or [0, 0, 0])[1]
    off = 0 if abs(rot - 180) < 1 else 180
    ims = [WS.render(data, atlas, {}, (yaw + off) % 360, 8, cen, sc, size=(w, h)) for yaw in (200, 270, 20)]
    hc = head_center(data)
    ims.append(WS.render(data, atlas, {}, (195 + off) % 360, 4, hc, h * 0.86 / 13.0, size=(w, h)))
    out = Image.new("RGB", (w * 4, h + 30), (22, 22, 30))
    for i, im in enumerate(ims):
        out.paste(im, (i * w, 30))
    d = ImageDraw.Draw(out)
    d.text((8, 4), title, fill=(236, 230, 214), font=WS.font(18))
    return out


def main(argv):
    ref = None
    if "--ref" in argv:
        i = argv.index("--ref")
        ref = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    out = argv[0]
    items = [tuple(a.split(":", 2)) for a in argv[1:]] or DEFAULT
    cells = []
    for mesh, tex, title in items:
        data, atlas = load(ref, mesh, tex)
        if data is None:
            print("skip", mesh)
            continue
        cells.append(cell(data, atlas, title))
    cols = 2
    rows = (len(cells) + cols - 1) // cols
    cw, ch = cells[0].size
    sheet = Image.new("RGB", (cw * cols, ch * rows), (16, 16, 22))
    for i, c in enumerate(cells):
        sheet.paste(c, ((i % cols) * cw, (i // cols) * ch))
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    main(sys.argv[1:])
