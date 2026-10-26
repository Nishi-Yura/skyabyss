"""島の祠の始めの武器 3 品 x 3 案の絵 (docs/design/43 の 4 節)。作りは五章の武器 (../gear_gaps/build_gaps.py) をそのまま借り、
形 (d_starter) と絵 (paint_starter) と出力先だけ差し替える。

実行: python -I tools/rough/lowpoly/weapons/starter/build_starter.py [武器 id ...] [--quick]
出力: docs/review/roughs_starter/raw/<id>/<k>/... ・ docs/review/roughs_starter/<id>/opt_<k>_views.png・opt_<k>_actions.png・compare.png
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "gear_gaps"))
sys.path.insert(0, str(HERE.parent))
import build_gaps as B  # noqa: E402
import d_starter  # noqa: E402
import paint_starter  # noqa: E402

B.D = d_starter
B.PG = paint_starter
B.OUT = B.ROOT / "docs/review/roughs_starter"
B.RAW = B.OUT / "raw"
for k, rgb in paint_starter.GLOW_RGB.items():
    B.scene.GLOW_RGB["gg_" + k] = rgb

if __name__ == "__main__":
    B.main()
