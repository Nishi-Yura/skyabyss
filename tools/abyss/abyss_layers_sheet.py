# -*- coding: utf-8 -*-
"""深淵の層の見た目の比べ表 (contact sheet)。撮った絵を名前順に並べ、下に絵の名前を書く。

python -I tools/abyss/abyss_layers_sheet.py <撮った絵のフォルダ> <出す名前 (例 before / after)>
  → docs/review/abyss_layers/<出す名前>_sheet.png
撮った絵のフォルダは run/shots/abyss_layers_look/shots/abyss_layers_look/ (台本 tools/shots/abyss_layers_look.txt の名前)
"""
from __future__ import annotations

import glob
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
OUT_DIR = os.path.join(ROOT, 'docs', 'review', 'abyss_layers')
COLS = 4
CELL_W = 480
LABEL_H = 22


def main():
    src = sys.argv[1]
    tag = sys.argv[2] if len(sys.argv) > 2 else 'sheet'
    files = sorted(glob.glob(os.path.join(src, '*.png')))
    if not files:
        print('no png in', src)
        return 1
    os.makedirs(OUT_DIR, exist_ok=True)
    cell_h = CELL_W * 9 // 16
    rows = (len(files) + COLS - 1) // COLS
    sheet = Image.new('RGB', (COLS * CELL_W, rows * (cell_h + LABEL_H)), (16, 16, 20))
    draw = ImageDraw.Draw(sheet)
    for i, f in enumerate(files):
        im = Image.open(f).convert('RGB').resize((CELL_W, cell_h))
        x = (i % COLS) * CELL_W
        y = (i // COLS) * (cell_h + LABEL_H)
        sheet.paste(im, (x, y))
        draw.text((x + 6, y + cell_h + 4), os.path.splitext(os.path.basename(f))[0], fill=(230, 230, 230))
    out = os.path.join(OUT_DIR, '%s_sheet.png' % tag)
    sheet.save(out)
    print('wrote', os.path.relpath(out, ROOT), len(files), 'shots')
    return 0


if __name__ == '__main__':
    sys.exit(main())
