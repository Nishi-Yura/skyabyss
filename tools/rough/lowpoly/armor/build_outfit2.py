"""初めの旅装・鉄張りの旅装の作り直し 3 案の見本 (outfit_two の形。塗りは build_outfit と同じ組の色)。
実行: python build_outfit2.py   出力: docs/review/roughs_outfit/armor/<組>/views.png ・ <品>_compare.png
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import build_outfit as BO  # noqa: E402  (塗りの色と見本の作りを借りる)
import build_one  # noqa: E402
import outfit_one  # noqa: E402
import outfit_two  # noqa: E402
import tpaint  # noqa: E402
import travel_one  # noqa: E402

for k, fn in outfit_two.SETS.items():
    tpaint.SETS[k] = {"start2": BO.START, "iron2": BO.IRON}[k.split("_")[0]]
    build_one.TRAVEL_GLOW[k] = "whale"
    travel_one.SETS[k] = fn
    outfit_one.SETS[k] = fn
outfit_one.JP.update(outfit_two.JP)

if __name__ == "__main__":
    names = sys.argv[1:] or list(outfit_two.SETS)
    build_one.review(names)
    for item in sorted({n.split("_")[0] for n in names}):
        if all((build_one.REVIEW / f"{item}_{k}" / "views.png").exists() for k in "abc"):
            BO.compare(item)
    print("ok")
