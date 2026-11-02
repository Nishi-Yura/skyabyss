"""イサナの祖父の風見鶏 3 案の絵。作りは祠の始めの武器 (../starter/build_starter.py) と同じ。

実行: python -I tools/rough/lowpoly/weapons/weathercock/build_weathercock.py [品 id ...] [--quick]
出力: docs/review/roughs_weathercock/raw/<id>/<k>/... ・ docs/review/roughs_weathercock/<id>/opt_<k>_views.png・opt_<k>_actions.png・compare.png
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "gear_gaps"))
sys.path.insert(0, str(HERE.parent / "outfit"))
sys.path.insert(0, str(HERE.parent))
import build_gaps as B  # noqa: E402
import d_weathercock  # noqa: E402
import paint_outfit  # noqa: E402

B.D = d_weathercock
import staged  # noqa: E402
staged.install(B)
B.PG = paint_outfit
B.OUT = B.ROOT / "docs/review/roughs_weathercock"
B.RAW = B.OUT / "raw"
for k, rgb in paint_outfit.GLOW_RGB.items():
    B.scene.GLOW_RGB["gg_" + k] = rgb

if __name__ == "__main__":
    B.main()
