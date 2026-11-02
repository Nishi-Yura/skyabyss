"""市の武具屋の防具 3 品 x 3 案の見本 (outfit_one の形、塗りは旅装の tpaint に組を足す)。
実行: python build_outfit.py [組 ...]   出力: docs/review/roughs_outfit/armor/<組>/views.png ・ docs/review/roughs_outfit/armor/<品>_compare.png
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'weapons'))

from PIL import Image, ImageDraw

import build_one
import outfit_one
import tpaint
import travel_one

A_ = tpaint.A_
tpaint.C.update(
    LINEN=A_([(118, 106, 86), (148, 136, 112), (176, 164, 138), (202, 192, 166), (226, 218, 194)]),
    INDIGO=A_([(22, 30, 50), (32, 44, 72), (46, 60, 96), (64, 82, 122), (88, 106, 148)]),
    STEEL=A_([(58, 62, 70), (88, 94, 104), (122, 128, 138), (158, 164, 172), (196, 200, 206)]),
    MAIL=A_([(48, 52, 58), (74, 78, 86), (104, 108, 116), (138, 142, 148)]),
    BRASS=A_([(86, 66, 26), (128, 100, 40), (168, 136, 60), (206, 174, 88), (236, 210, 132)]),
    SKY=A_([(70, 120, 150), (104, 160, 190), (146, 198, 222), (196, 230, 244), (236, 250, 255)]),
)
tpaint.C["TRIM_U"] = tpaint.C["BRASS"]
START = dict(main=("LINEN", 0.52, "wool"), sec=("LEATHER", 0.50, "leather"), edge=("BONE", 0.70, "shell"), acc=("LEATHER_D", 0.48, "leather"),
             cloth=("INDIGO", 0.46, "wool"), cord=("ROPE", 0.55, "rope"), fur=("STRAW", 0.56, "straw"), metal=("IRON", 0.58, "metal"), glow="G_CYAN")
IRON = dict(main=("MAIL", 0.55, "rope"), sec=("LEATHER", 0.48, "leather"), edge=("STEEL", 0.74, "metal"), acc=("LEATHER_D", 0.46, "leather"),
            cloth=("INDIGO", 0.44, "wool"), cord=("ROPE", 0.50, "rope"), fur=("STRAW", 0.54, "straw"), metal=("STEEL", 0.56, "metal"), glow="G_CYAN")
SKY = dict(main=("LINEN", 0.50, "wool"), sec=("LEATHER", 0.48, "leather"), edge=("COPPER", 0.66, "metal"), acc=("COPPER", 0.56, "metal"),
           cloth=("INDIGO", 0.44, "wool"), cord=("ROPE", 0.52, "rope"), fur=("FUR_W", 0.58, "fur"), metal=("COPPER", 0.62, "metal"),
           glass=("SKY", 0.56, "glass"), glow="G_CYAN")
for k in outfit_one.SETS:
    tpaint.SETS[k] = {"start": START, "iron": IRON, "sky": SKY}[k.split("_")[0]]
    build_one.TRAVEL_GLOW[k] = "whale"
# 胸当てだけの品 (空石) は、空の部位を渡さない
travel_one.SETS.update({k: (lambda fn: lambda: {p: v for p, v in fn().items() if v})(fn) for k, fn in outfit_one.SETS.items()})
build_one.REVIEW = build_one.ROOT / "docs/review/roughs_outfit/armor"


def compare(item):
    rows = [Image.open(build_one.REVIEW / f"{item}_{k}" / "views.png") for k in "abc"]
    W = rows[0].width
    S = Image.new("RGB", (W, sum(r.height + 30 for r in rows)), build_one.aview.PLAT)
    dr = ImageDraw.Draw(S)
    y = 0
    for k, r in zip("ABC", rows):
        dr.text((8, y + 4), f"{outfit_one.JP[item]}  案 {k}", font=build_one.font(18), fill=(255, 232, 160))
        S.paste(r, (0, y + 30))
        y += r.height + 30
    S.save(build_one.REVIEW / f"{item}_compare.png")


if __name__ == "__main__":
    names = sys.argv[1:] or list(outfit_one.SETS)
    build_one.review(names)
    for item in sorted({n.split("_")[0] for n in names}):
        if all((build_one.REVIEW / f"{item}_{k}" / "views.png").exists() for k in "abc"):
            compare(item)
