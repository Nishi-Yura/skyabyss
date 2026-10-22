"""余燼の背 (低ポリ案 C のブロックの火蜥蜴) の外形を、案 C のメッシュと並べて描く。
使い方: python tools/gen/items/gen_ember_back_body.py --cache  のあとで  python tools/sheets/ember_back_c_views.py
入力: tools/rough/_ember_back_body_cache.npz (体のブロック。部屋を掘る前の外形)
出力: docs/review/previews/ember_back_c_{side,top,three_quarter}.png (左 = ブロックの背、右 = 案 C のメッシュ)
ブロックの図は structure/render.py の色 (ブロックの地図の色)。メッシュは tools/rough/lowpoly/lp_ember_titan/common.render。"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/structure"))
sys.path.insert(0, str(ROOT / "tools/rough/lowpoly/lp_ember_titan"))
sys.path.insert(0, str(ROOT / "tools/rough"))
import sbd  # noqa: E402
import render as R  # noqa: E402

OUT = ROOT / "docs/review/previews"
SKY = (24, 26, 34)


def load_dump():
    z = np.load(ROOT / "tools/rough/_ember_back_body_cache.npz")
    B, org, names = z["B"], z["org"], [str(n) for n in z["names"]]
    pal = [{"name": "minecraft:" + n, "air": n == "air", "collide": n != "air", "color": "#000000"} for n in names]
    ids = np.transpose(B, (1, 2, 0)).astype(np.uint16)          # (x, y, z) -> (y, z, x)
    return sbd.Dump({"origin": [int(org[0]), int(org[1]), int(org[2])], "name": "ember_back_body"}, ids, pal)


def block_side(d):
    """西から見る: 画面の左 = 北 (頭)"""
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
    L = left.resize((int(left.width * h / left.height), h), Image.LANCZOS)
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
    d = load_dump()
    OUT.mkdir(parents=True, exist_ok=True)
    a = [float(v) for v in sys.argv[1:]]
    pair(block_side(d), mesh_view(90, 5, (0, 60, 20), 3.0, (1400, 420)),
         "ember back blocks (west side, head left)", "option C mesh (near LOD)", OUT / "ember_back_c_side.png")
    pair(R.top(d, scale=3), mesh_view(180, 90, (0, 45, 20), 2.6, (700, 1000)),
         "ember back blocks (top, head up)", "option C mesh (top)", OUT / "ember_back_c_top.png")
    pair(R.iso(d, 1), mesh_view(200, -25, (0, 60, 20), 2.6, (1000, 560)),
         "ember back blocks (iso from SE above)", "option C mesh (3/4)", OUT / "ember_back_c_three_quarter.png")


if __name__ == "__main__":
    main()
