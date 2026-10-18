"""潮殻の背 (低ポリ案 B のブロックの亀) の構造の書き出しと、案 B のメッシュを並べて描く。
使い方: python tools/tide_back_b_views.py [dump.sbd]   (既定 run/dumps/tide_back.sbd。書き出しは README の「構造をデータで確かめる」)
出力: docs/review/previews/tide_back_b_{side,top,three_quarter}.png (左 = ブロックの背、右 = 案 B のメッシュ)
ブロックの図は structure/render.py の色。メッシュは tools/rough/lowpoly/lp_tide_shell の build_lod / render。"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/structure"))
sys.path.insert(0, str(ROOT / "tools/rough/lowpoly/lp_tide_shell"))
import sbd  # noqa: E402
import render as R  # noqa: E402

OUT = ROOT / "docs/review/previews"
SKY = (24, 26, 34)


def block_view(d, kind):
    """kind: side (西から。左 = 北 = 頭)、top (真上)"""
    if kind == "top":
        return R.top(d, scale=3)
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
    img = Image.fromarray(np.clip(c[::-1], 0, 255).astype(np.uint8))
    return img.resize((sz * 3, sy * 3), Image.NEAREST)


def mesh_view(yaw, pitch, cen, sc, size):
    import importlib
    from tcommon import build_lod, render, voxel  # noqa
    opt = importlib.import_module("topt_b").OPT
    global _R
    if "_R" not in globals():
        _R = build_lod(opt, 0, save=False)
    r = _R
    im = render(r["faces"], r["atlas"], r["glow"], yaw, pitch, size, cen, sc)
    bg = Image.fromarray(np.clip(voxel._background(*size, "dark"), 0, 255).astype(np.uint8)).convert("RGBA")
    bg.alpha_composite(im)
    return bg.convert("RGB")


def pair(left, right, title_l, title_r, path):
    h = 520
    L = left.resize((int(left.width * h / left.height), h), Image.LANCZOS) if left.height != h else left
    Rr = right.resize((int(right.width * h / right.height), h), Image.LANCZOS)
    W = Image.new("RGB", (L.width + Rr.width + 12, h + 24), (16, 16, 20))
    W.paste(L, (0, 24))
    W.paste(Rr, (L.width + 12, 24))
    dr = ImageDraw.Draw(W)
    dr.text((6, 6), title_l, fill=(230, 230, 230))
    dr.text((L.width + 18, 6), title_r, fill=(230, 230, 230))
    W.save(path)
    print("saved", path)


def main():
    p = sys.argv[1] if len(sys.argv) > 1 else str(ROOT / "run/dumps/tide_back.sbd")
    d = sbd.load(p)
    OUT.mkdir(parents=True, exist_ok=True)
    pair(block_view(d, "side"), mesh_view(90, 5, (0, 70, 0), 3.6, (1400, 560)),
         "back dimension blocks (west side, head left)", "option B mesh (near LOD)", OUT / "tide_back_b_side.png")
    pair(block_view(d, "top"), mesh_view(180, 90, (0, 70, 0), 2.9, (800, 900)),
         "back dimension blocks (top, head up)", "option B mesh (top)", OUT / "tide_back_b_top.png")
    pair(R.iso(d, 1), mesh_view(220, -25, (0, 60, 0), 3.0, (900, 560)),
         "back dimension blocks (iso from SE above)", "option B mesh (3/4)", OUT / "tide_back_b_three_quarter.png")


if __name__ == "__main__":
    main()
