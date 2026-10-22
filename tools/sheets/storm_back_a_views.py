"""雷翼の背 (低ポリ案 A の色のブロックの鳥) の構造の書き出しと、案 A の近景メッシュを並べて描く。
使い方: python tools/sheets/storm_back_a_views.py [dump.sbd]
  書き出し: SKYABYSS_DUMP=storm_back ./gradlew runServer   (run/dumps/storm_back.sbd。SKYABYSS_DUMP_OUT で場所を変えられる)
出力: docs/review/previews/storm_back_a_{side,top,three_quarter}.png (左 = ブロックの背、右 = 案 A のメッシュ)
ブロックの図は structure/render.py の色。メッシュは tools/rough/lowpoly/lp_thunder_wing/common.render。"""
import importlib
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/structure"))
sys.path.insert(0, str(ROOT / "tools/rough/lowpoly/lp_thunder_wing"))
import sbd  # noqa: E402
import render as R  # noqa: E402

OUT = ROOT / "docs/review/previews"
SKY = (24, 26, 34)


def block_side(d):
    vis = ~d.air
    col = R._colors(d).astype(np.float32)
    sy, sz, sx = d.shape
    first = np.argmax(vis, axis=2)
    any_ = vis.any(axis=2)
    yy, zz = np.indices((sy, sz))
    c = col[d.ids[yy, zz, first]]
    depth = first.astype(np.float32)
    sh = np.zeros_like(depth)
    sh[:, 1:] = (depth[:, :-1] - depth[:, 1:]) * 0.05
    sh[1:, :] += (depth[1:, :] - depth[:-1, :]) * 0.05
    c = c * np.clip(1.0 + sh, 0.6, 1.3)[..., None]
    c[~any_] = SKY
    return Image.fromarray(np.clip(c[::-1], 0, 255).astype(np.uint8)).resize((sz * 3, sy * 3), Image.NEAREST)


_R = None


def mesh_view(yaw, pitch, cen, sc, size):
    global _R
    from common import build_lod, render, voxel  # noqa
    if _R is None:
        _R = build_lod(importlib.import_module("opt_a").OPT, 0, save=False)
    im = render(_R["faces"], _R["atlas"], _R["glow"], yaw, pitch, size, cen, sc)
    bg = Image.fromarray(np.clip(voxel._background(*size, "dark"), 0, 255).astype(np.uint8)).convert("RGBA")
    bg.alpha_composite(im)
    return bg.convert("RGB")


def pair(left, right, tl, tr, path):
    h = 520
    L = left.resize((int(left.width * h / left.height), h), Image.LANCZOS)
    Rr = right.resize((int(right.width * h / right.height), h), Image.LANCZOS)
    W = Image.new("RGB", (L.width + Rr.width + 12, h + 24), (16, 16, 20))
    W.paste(L, (0, 24))
    W.paste(Rr, (L.width + 12, 24))
    dr = ImageDraw.Draw(W)
    dr.text((6, 6), tl, fill=(230, 230, 230))
    dr.text((L.width + 18, 6), tr, fill=(230, 230, 230))
    W.save(path)
    print("saved", path)


def main():
    p = sys.argv[1] if len(sys.argv) > 1 else str(ROOT / "run/dumps/storm_back.sbd")
    d = sbd.load(p)
    OUT.mkdir(parents=True, exist_ok=True)
    pair(block_side(d), mesh_view(90, 4, (0, 52, 4), 2.9, (780, 300)),
         "storm back blocks (west side, head left)", "option A mesh (near LOD)", OUT / "storm_back_a_side.png")
    pair(R.top(d, scale=3), mesh_view(0, 90, (0, 52, 4), 1.62, (780, 500)),
         "storm back blocks (top, head up)", "option A mesh (top)", OUT / "storm_back_a_top.png")
    pair(R.iso(d, 1), mesh_view(35, 28, (0, 50, 0), 1.7, (900, 560)),
         "storm back blocks (iso from SE above)", "option A mesh (3/4)", OUT / "storm_back_a_three_quarter.png")


if __name__ == "__main__":
    main()
