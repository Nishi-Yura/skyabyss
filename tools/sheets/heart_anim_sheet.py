# -*- coding: utf-8 -*-
"""心臓の技の 4 こま (tools/shots/heart_anim_check.txt の撮影) を、心臓ごとに一枚へ並べる。
  python -X utf8 -I tools/sheets/heart_anim_sheet.py  -> docs/review/heart_anim/<心臓>.png
行 = 技、列 = 6・16・26・38 tick。真ん中を切り抜いて縮める"""
import glob
import os
import re
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "run/shots/heart_anim_check/shots/heart_anim_check"
OUT = ROOT / "docs/review/heart_anim"
CROP = (240, 40, 1040, 640)
TW, TH = 320, 240


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    rows = {}
    for p in sorted(glob.glob(str(SRC / "*.png"))):
        m = re.match(r"(.+_heart)_(.+)_(\d)\.png$", os.path.basename(p))
        if not m:
            continue
        rows.setdefault(m.group(1), {}).setdefault(m.group(2), {})[int(m.group(3))] = p
    for heart, moves in rows.items():
        names = list(moves)
        cv = Image.new("RGB", (120 + 4 * (TW + 6), len(names) * (TH + 6) + 6), (16, 16, 22))
        d = ImageDraw.Draw(cv)
        for r, mv in enumerate(names):
            y = 6 + r * (TH + 6)
            d.text((6, y + TH // 2), mv, fill=(220, 220, 230))
            for k in range(4):
                p = moves[mv].get(k)
                if p:
                    im = Image.open(p).convert("RGB").crop(CROP).resize((TW, TH))
                    cv.paste(im, (120 + k * (TW + 6), y))
        cv.save(OUT / (heart + ".png"))
        print("wrote", OUT / (heart + ".png"), len(names), "moves")


if __name__ == "__main__":
    main()
