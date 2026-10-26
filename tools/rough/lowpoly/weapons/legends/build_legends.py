"""伝説の武器 20 品 x 3 案 (--ch5 で五章の武器 3 品 x 3 案) の絵。作りは五章の武器 (../gear_gaps/build_gaps.py) と弓の段 (staged) を借りる。
塗り: 伝説は深淵の専用伝説 (../abyss/paint_abyss.py。glow の鍵 = 巨神)、五章は五章の装備 (../gear_gaps/paint_gaps.py)。

実行: python -I tools/rough/lowpoly/weapons/legends/build_legends.py [武器 id ...] [--quick] [--ch5]
出力: docs/review/roughs_legends/ (五章は docs/review/roughs_ch5/) の <id>/opt_<k>_views.png・opt_<k>_actions.png・compare.png と raw/<id>/<k>/
"""
from __future__ import annotations

import sys
import types
from pathlib import Path

HERE = Path(__file__).resolve().parent
for p in (HERE, HERE.parent / "gear_gaps", HERE.parent / "abyss", HERE.parent):
    sys.path.insert(0, str(p))
import build_gaps as B  # noqa: E402
import staged  # noqa: E402
import d_legend  # noqa: E402

CH5 = "--ch5" in sys.argv
if CH5:
    import paint_gaps as PG  # noqa: E402
    B.D = types.SimpleNamespace(WEAPONS=d_legend.CH5)
    B.PG = PG
    B.OUT = B.ROOT / "docs/review/roughs_ch5"
else:
    import paint_abyss as PA  # noqa: E402
    B.D = types.SimpleNamespace(WEAPONS=d_legend.LEGENDS)
    B.PG = types.SimpleNamespace(GLOW_RGB=PA.GLOW_RGB,
                                 paint=lambda faces, size, P, N, M, L, titan, tier, plus, inlay, em: PA.paint(faces, size, P, N, M, L, titan, plus, inlay, em))
    B.OUT = B.ROOT / "docs/review/roughs_legends"
B.RAW = B.OUT / "raw"
staged.install(B)
for k, rgb in B.PG.GLOW_RGB.items():
    B.scene.GLOW_RGB["gg_" + k] = rgb
sys.argv = [a for a in sys.argv if a != "--ch5"]

if __name__ == "__main__":
    B.main()
