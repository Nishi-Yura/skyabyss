"""岩鯨の甲殻 (最終) を作り、docs/review/lowpoly_roughs/armor/c/stone_whale/ に出す (install_armor_lowpoly.py の入力)。
実行: python build_whale.py [preview]   preview を付けると tools/_scratch に 3 階級 x 3 方向の絵も出す。"""
from __future__ import annotations

import sys

from PIL import Image

import aview
import style_whale
from akit import ROOT, OUT

BASE = OUT / "c" / "stone_whale"
BUDGET = 3000


def main():
    aview.SS = 2
    allres = {}
    for rar in ("common", "rare", "legend"):
        res = aview.build_set(style_whale.build("whale", rar), "whale", rar)
        suffix = "" if rar == "common" else f"_{rar}"
        aview.save_set(res, BASE, "stone_whale", suffix=suffix)
        allres[rar] = res
        n = aview.set_tris(res)
        print(rar, {k: v["tris"] for k, v in res.items()}, n)
        assert n <= BUDGET, n
        if rar != "common":
            for plus in (5, 10):
                r3 = aview.build_set(style_whale.build("whale", rar), "whale", rar, plus)
                aview.save_set(r3, BASE, "stone_whale", suffix=f"{suffix}_plus{plus}", mesh=False)
    if len(sys.argv) > 1:
        tiles = []
        for rar in ("common", "rare", "legend"):
            for yaw, pitch in ((180, 6), (0, 6), (215, 16)):
                tiles.append(aview.render(allres[rar], "whale", yaw, pitch, (260, 400), 8.5))
        im = Image.new("RGB", (260 * 3, 400 * 3))
        for i, t in enumerate(tiles):
            im.paste(t, ((i % 3) * 260, (i // 3) * 400))
        out = ROOT / "tools/_scratch/whale_armor_preview.png"
        out.parent.mkdir(exist_ok=True)
        im.save(out)
        print(out)


if __name__ == "__main__":
    main()
