"""岩鯨の背 (低ポリ案 C のブロックの鯨) の構造の書き出しと、案 C のメッシュを並べて描く。
使い方: python tools/whale_back_c_views.py [dump.sbd]   (既定 run/dumps/titan_back.sbd)
出力: docs/review/previews/whale_back_c_{side,top,three_quarter}.png (左 = ブロックの背、右 = 案 C のメッシュ)
ブロックの図は structure/render.py の色 (ブロックの地図の色)。メッシュは tools/rough/lowpoly/lp_stone_whale/common.render。"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/structure"))
sys.path.insert(0, str(ROOT / "tools/rough/lowpoly/lp_stone_whale"))
import sbd  # noqa: E402
import render as R  # noqa: E402

OUT = ROOT / "docs/review/previews"
SKY = (24, 26, 34)


def block_view(d, kind):
    """kind: side (西から)、top (真上)、three (南西の斜め上から。北 = 頭を左下に)"""
    vis = ~d.air
    col = R._colors(d).astype(np.float32)
    sy, sz, sx = d.shape
    if kind == "side":
        # 西から見る: 画面の左 = 北 (頭)。各 (y, z) で x が小さい方の最初
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
    if kind == "top":
        return R.top(d, scale=3)
    raise ValueError(kind)


def mesh_view(yaw, pitch, cen, sc, size):
    from common import build_lod, render, voxel  # noqa
    import importlib
    opt = importlib.import_module("opt_c").OPT
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
    p = sys.argv[1] if len(sys.argv) > 1 else str(ROOT / "run/dumps/titan_back.sbd")
    d = sbd.load(p)
    OUT.mkdir(parents=True, exist_ok=True)
    pair(block_view(d, "side"), mesh_view(90, 5, (0, 50, 10), 4.6, (1400, 420)),
         "back dimension blocks (west side, head left)", "option C mesh (near LOD)", OUT / "whale_back_c_side.png")
    pair(block_view(d, "top"), mesh_view(180, 90, (0, 45, 10), 3.4, (700, 900)),
         "back dimension blocks (top, head up)", "option C mesh (top)", OUT / "whale_back_c_top.png")
    pair(R.iso(d, 1), mesh_view(200, -25, (0, 45, 0), 3.4, (900, 520)),
         "back dimension blocks (iso from SE above)", "option C mesh (below, 3/4)", OUT / "whale_back_c_three_quarter.png")


if __name__ == "__main__":
    main()
