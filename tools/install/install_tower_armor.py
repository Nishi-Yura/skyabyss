# -*- coding: utf-8 -*-
"""あいだの塔の防具 3 式 (docs/design/54 の 2.6) の低ポリを assets へ入れる。入れ方は野の防具 (install_wild_gear.py の install_armor) と同じ。

入力 (先に作る): python -I tools/rough/lowpoly/armor/tower/build_tower_armor.py   → docs/review/roughs_tower/raw/armor_<一式>/
出力: lowpoly/armor/<一式>/<部位>{,_legend}.json・textures/lowpoly/armor/<一式>/...、textures/item/<一式>_<部位>.png と平らなモデル
使い方: python -I tools/install/install_tower_armor.py [一式 id ...]
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import install_wild_gear as W  # noqa: E402

SETS = ("tower_scale", "tower_bridge", "tower_halo")


def main(ids):
    W.RAW = W.ROOT / "docs/review/roughs_tower/raw"
    for sid in ids or SETS:
        W.install_armor({"id": sid, "tier": "heroic"})
        print("installed", sid)


if __name__ == "__main__":
    main([a for a in sys.argv[1:] if not a.startswith("-")])
