"""岩鯨・雷翼の伝説 (C) だけを作り直す (style_c.LEAN)。レア以下は触らない。
実行: python build_legend_lean.py  -> docs/review/lowpoly_roughs/armor/c/<set>/*_legend*  を書き換える。
そのあと python tools/install/install_armor_lowpoly.py。"""
from __future__ import annotations

import aview
from akit import OUT
from common import SETS
import style_c

aview.SS = 2
for T in style_c.LEAN:
    base = OUT / "c" / SETS[T]
    res = aview.build_set(style_c.build(T, "legend"), T, "legend")
    aview.save_set(res, base, SETS[T], suffix="_legend")
    for plus in (5, 10):
        r3 = aview.build_set(style_c.build(T, "legend"), T, "legend", plus)
        aview.save_set(r3, base, SETS[T], suffix=f"_legend_plus{plus}", mesh=False)
    print(T, aview.set_tris(res), {k: v["tris"] for k, v in res.items()})
