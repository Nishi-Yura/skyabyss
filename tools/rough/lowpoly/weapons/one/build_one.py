"""一品一案の武器の絵とメッシュ (2026-10-26)。作りは五章の武器 (../gear_gaps/build_gaps.py) と弓の段 (staged) を借り、案は一つ (a) だけ。
塗り: 巨神の品 (o_whale / o_tide / o_storm / o_ember) は巨神の塗り (../paint.py。伝説は縁が金と淵の欠片)、五章 (o_abyss) は五章の塗り (../gear_gaps/paint_gaps.py)。

実行: python -I tools/rough/lowpoly/weapons/one/build_one.py <o_whale|o_tide|o_storm|o_ember|o_abyss> [武器 id ...] [--quick]
出力: docs/review/roughs_one/<id>/views.png・actions.png と raw/<id>/a/ (install_job_weapons.py が読む)
"""
from __future__ import annotations

import importlib
import sys
import types
from pathlib import Path

HERE = Path(__file__).resolve().parent
for p in (HERE, HERE.parent / "gear_gaps", HERE.parent):
    sys.path.insert(0, str(p))
import build_gaps as B  # noqa: E402
import staged  # noqa: E402
import paint as titan_paint  # noqa: E402

mod_name = sys.argv[1]
M = importlib.import_module(mod_name)
B.D = types.SimpleNamespace(WEAPONS=M.WEAPONS)
if mod_name == "o_abyss":
    import paint_gaps as PG  # noqa: E402
    B.PG = PG
else:
    B.PG = types.SimpleNamespace(
        GLOW_RGB={k: B.scene.GLOW_RGB[k] for k in ("whale", "tide", "thunder", "ember")},
        paint=lambda faces, size, P, N, M_, L, titan, tier, plus, inlay, em: titan_paint.paint(
            faces, size, P, N, M_, L, titan, tier, plus, inlay, em, lift=0.14 if titan == "ember" else 0.0))
B.OUT = B.ROOT / "docs/review/roughs_one"
B.RAW = B.OUT / "raw"
staged.install(B)
for k, rgb in B.PG.GLOW_RGB.items():
    B.scene.GLOW_RGB["gg_" + k] = rgb


def main():
    args = [a for a in sys.argv[2:] if not a.startswith("--")]
    quick = "--quick" in sys.argv
    for wid in args or list(M.WEAPONS):
        out = B.OUT / wid
        out.mkdir(parents=True, exist_ok=True)
        res = B.build_option(wid, "a", quick)
        print(wid, "tris", res[0]["tris"], "atlas", res[0]["size"], flush=True)
        B.views_image(wid, res).save(out / "views.png")
        if not quick:
            B.actions_image(wid, res).save(out / "actions.png")


if __name__ == "__main__":
    main()
