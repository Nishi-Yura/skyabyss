"""選んだ巨神シリーズ (S2 祭) を 4 巨神 x 3 階級 (+5 / +10 の光) で docs/review/lowpoly_roughs/armor/c/<set>/ に出す (install_armor_lowpoly.py の入力)。
実行: python build_series_install.py [S2]"""
from __future__ import annotations

import sys

import aview
import spaint
import sstyle
from akit import OUT
from common import TITANS, SETS

aview.apaint = spaint
BUDGET = 3000


def main(v="S2"):
    aview.SS = 2
    for T in TITANS:
        base = OUT / "c" / SETS[T]
        for rar in ("common", "rare", "legend"):
            res = aview.build_set(sstyle.build(T, rar, v), T, rar)
            suffix = "" if rar == "common" else f"_{rar}"
            aview.save_set(res, base, SETS[T], suffix=suffix)
            n = aview.set_tris(res)
            print(v, T, rar, {k: r["tris"] for k, r in res.items()}, n, flush=True)
            assert n <= BUDGET, n
            if rar != "common":
                for plus in (5, 10):
                    r3 = aview.build_set(sstyle.build(T, rar, v), T, rar, plus)
                    aview.save_set(r3, base, SETS[T], suffix=f"{suffix}_plus{plus}", mesh=False)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "S2")
