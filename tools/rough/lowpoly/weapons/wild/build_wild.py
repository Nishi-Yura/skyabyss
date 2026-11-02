"""野の装備 (docs/design/51) の武器の絵とメッシュ。作りは一品一案 (../one/build_one.py) と同じ build_gaps と弓の段 (staged)、塗りは paint_wild。

実行: python -I tools/rough/lowpoly/weapons/wild/build_wild.py <o_wild1|...|o_wild5> [武器 id ...] [--quick]
出力: docs/review/roughs_wild/<id>/views.png・actions.png と raw/<id>/a/ (tools/install/install_wild_gear.py が読む)
"""
from __future__ import annotations

import importlib
import sys
import types
from pathlib import Path

HERE = Path(__file__).resolve().parent
for p in (HERE, HERE.parent / "one", HERE.parent / "gear_gaps", HERE.parent):
    sys.path.insert(0, str(p))
import build_gaps as B  # noqa: E402
import staged  # noqa: E402
import paint_wild as PG  # noqa: E402

mod_name = sys.argv[1]
M = importlib.import_module(mod_name)
B.D = types.SimpleNamespace(WEAPONS=M.WEAPONS)
B.PG = PG
B.OUT = B.ROOT / "docs/review/roughs_wild"
B.RAW = B.OUT / "raw"
staged.install(B)
for k, rgb in PG.GLOW_RGB.items():
    B.scene.GLOW_RGB["gg_" + k] = rgb


_icon = B.icon


def icon(r, wid):
    w = M.WEAPONS[wid]
    if w.get("kind") not in ("trinket", "charm"):
        return _icon(r, wid)
    import numpy as np
    from PIL import Image
    pts = np.concatenate([f.verts for f in r["faces"]])
    ext = max(np.ptp(pts[:, 0]), np.ptp(pts[:, 1]))
    sc = 28.0 / ext * 4
    mid = (pts.max(0) + pts.min(0)) / 2
    im = B.view(r["faces"], r["atlas"], r["glow"], B.vk(w), 200, 8, (128, 128), (mid[0], mid[1], mid[2]), sc, ss=2, outline=False)
    small = Image.fromarray(np.array(im)).resize((32, 32), Image.BOX)
    arr = np.array(small).astype(float)
    solid = arr[..., 3] >= 70
    rgb = np.clip(arr[..., :3] * 1.45 + 10, 0, 255)
    out = np.zeros((32, 32, 4), np.uint8)
    out[solid, :3] = rgb[solid].astype(np.uint8)
    out[solid, 3] = 255
    near = np.zeros_like(solid)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        near |= np.roll(np.roll(solid, dx, 1), dy, 0)
    out[~solid & near] = (14, 10, 22, 255)
    return Image.fromarray(out, "RGBA")


B.icon = icon


def main():
    args = [a for a in sys.argv[2:] if not a.startswith("--")]
    quick = "--quick" in sys.argv
    for wid in args or list(M.WEAPONS):
        out = B.OUT / wid
        out.mkdir(parents=True, exist_ok=True)
        res = B.build_option(wid, "a", quick)
        print(wid, "tris", res[0]["tris"], "atlas", res[0]["size"], flush=True)
        if "--noviews" in sys.argv:
            continue
        B.views_image(wid, res).save(out / "views.png")
        if not quick:
            B.actions_image(wid, res).save(out / "actions.png")


if __name__ == "__main__":
    main()
