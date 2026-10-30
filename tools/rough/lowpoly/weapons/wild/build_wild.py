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
