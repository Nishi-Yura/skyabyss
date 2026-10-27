"""市の武具屋・道具屋の品 4 点 x 3 案の絵 (旅の盾・伐り斧・鶴嘴・土掘り)。作りは祠の始めの武器 (../starter/build_starter.py) と同じ。

実行: python -I tools/rough/lowpoly/weapons/outfit/build_outfit.py [品 id ...] [--quick]
出力: docs/review/roughs_outfit/raw/<id>/<k>/... ・ docs/review/roughs_outfit/<id>/opt_<k>_views.png・opt_<k>_actions.png・compare.png
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "gear_gaps"))
sys.path.insert(0, str(HERE.parent))
import build_gaps as B  # noqa: E402
import d_outfit  # noqa: E402
import paint_outfit  # noqa: E402

B.D = d_outfit
import staged  # noqa: E402
staged.install(B)
B.PG = paint_outfit
B.OUT = B.ROOT / "docs/review/roughs_outfit"
B.RAW = B.OUT / "raw"
for k, rgb in paint_outfit.GLOW_RGB.items():
    B.scene.GLOW_RGB["gg_" + k] = rgb

if __name__ == "__main__":
    B.main()
