"""余燼の鎧 (最終) を作り、docs/review/lowpoly_roughs/armor/c/thunder_wing/ に出す (install_armor_lowpoly.py の入力)。
実行: python build_storm.py [preview]   (build_tide.py と同じ形。build_ember.py は STYLE と BASE を変えたもの)"""
from __future__ import annotations

import sys

from PIL import Image

import aview
import style_ember as style
from akit import ROOT, OUT

KEY, TITAN, DIR = "ember", "ember", "ember"
BASE = OUT / "c" / DIR
BUDGET = 3000


def main():
    aview.SS = 2
    allres = {}
    for rar in ("common", "rare", "legend"):
        res = aview.build_set(style.build(TITAN, rar), TITAN, rar)
        suffix = "" if rar == "common" else f"_{rar}"
        aview.save_set(res, BASE, KEY, suffix=suffix)
        allres[rar] = res
        n = aview.set_tris(res)
        print(rar, {k: v["tris"] for k, v in res.items()}, n)
        assert n <= BUDGET, n
        if rar != "common":
            for plus in (5, 10):
                r3 = aview.build_set(style.build(TITAN, rar), TITAN, rar, plus)
                aview.save_set(r3, BASE, KEY, suffix=f"{suffix}_plus{plus}", mesh=False)
    if len(sys.argv) > 1:
        tiles = []
        for rar in ("common", "rare", "legend"):
            for yaw, pitch in ((180, 6), (0, 6), (215, 16)):
                tiles.append(aview.render(allres[rar], TITAN, yaw, pitch, (260, 400), 8.5))
        im = Image.new("RGB", (260 * 3, 400 * 3))
        for i, t in enumerate(tiles):
            im.paste(t, ((i % 3) * 260, (i // 3) * 400))
        out = ROOT / "tools/_scratch" / f"{DIR}_armor_preview.png"
        out.parent.mkdir(exist_ok=True)
        im.save(out)
        print(out)


if __name__ == "__main__":
    main()
