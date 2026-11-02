"""巨神のヒロイック武器 (専用の逸品) の足りない職ぶん x 3 案の絵 (2026-10-28)。作りは五章の武器 (../gear_gaps/build_gaps.py) と弓の段 (staged) を借り、
形 (d_heroic) と出力先を差し替え、塗りは巨神の武器の塗り (../paint.py) をそのまま使う。

実行: python -I tools/rough/lowpoly/weapons/heroic/build_heroic.py [武器 id ...] [--quick]
出力: docs/review/roughs_heroic/raw/<id>/<k>/... ・ docs/review/roughs_heroic/<id>/opt_<k>_views.png・opt_<k>_actions.png・compare.png
"""
from __future__ import annotations

import sys
import types
from pathlib import Path

HERE = Path(__file__).resolve().parent
for p in (HERE, HERE.parent / "gear_gaps", HERE.parent / "legends", HERE.parent):
    sys.path.insert(0, str(p))
import build_gaps as B  # noqa: E402
import staged  # noqa: E402
import d_heroic  # noqa: E402
import paint as titan_paint  # noqa: E402

PG = types.SimpleNamespace(
    GLOW_RGB={k: B.scene.GLOW_RGB[k] for k in ("whale", "tide", "thunder", "ember")},
    paint=lambda faces, size, P, N, M, L, titan, tier, plus, inlay, em: titan_paint.paint(
        faces, size, P, N, M, L, titan, tier, plus, inlay, em, lift=0.14 if titan == "ember" else 0.0))
B.D = d_heroic
B.PG = PG
B.OUT = B.ROOT / "docs/review/roughs_heroic"
B.RAW = B.OUT / "raw"
staged.install(B)
for k, rgb in PG.GLOW_RGB.items():
    B.scene.GLOW_RGB["gg_" + k] = rgb

if __name__ == "__main__":
    B.main()
