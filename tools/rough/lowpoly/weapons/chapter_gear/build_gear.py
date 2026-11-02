"""章の武器の空き 8 品 x 3 案の絵 (全職に専用の武器)。作りは五章の武器 (../gear_gaps/build_gaps.py) を借り、
形 (d_gear) と出力先を差し替え、塗りは巨神の武器の塗り (../paint.py) をそのまま使う。

実行: python -I tools/rough/lowpoly/weapons/chapter_staves/build_gear.py [武器 id ...] [--quick]
出力: docs/review/roughs_jobgear/raw/<id>/<k>/... ・ docs/review/roughs_staves/<id>/opt_<k>_views.png・opt_<k>_actions.png・compare.png
"""
from __future__ import annotations

import sys
import types
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "gear_gaps"))
sys.path.insert(0, str(HERE.parent))
import build_gaps as B  # noqa: E402
import d_gear  # noqa: E402
import paint as titan_paint  # noqa: E402

# 巨神の塗り: glow の鍵 = 巨神、tier = 形の段。余燼は武器と同じく一段明るく
PG = types.SimpleNamespace(
    GLOW_RGB={k: B.scene.GLOW_RGB[k] for k in ("whale", "tide", "thunder", "ember")},
    paint=lambda faces, size, P, N, M, L, titan, tier, plus, inlay, em: titan_paint.paint(
        faces, size, P, N, M, L, titan, tier, plus, inlay, em, lift=0.14 if titan == "ember" else 0.0))
B.D = d_gear
import staged  # noqa: E402
staged.install(B)
B.PG = PG
B.OUT = B.ROOT / "docs/review/roughs_jobgear"
B.RAW = B.OUT / "raw"
for k, rgb in PG.GLOW_RGB.items():
    B.scene.GLOW_RGB["gg_" + k] = rgb

if __name__ == "__main__":
    B.main()
