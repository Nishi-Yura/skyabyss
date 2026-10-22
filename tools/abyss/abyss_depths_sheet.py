# -*- coding: utf-8 -*-
"""Contact sheet for the abyss depths section shots (docs/design/36 section 9).

python -I tools/abyss/abyss_depths_sheet.py
Reads docs/review/abyss_depths/d_<kind>_<top|mid>.png (copied from run/shots/abyss_depths/shots/abyss_depths)
and writes docs/review/abyss_depths/contact_sheet.png: one row per section kind, top view then mid view.
"""
from __future__ import annotations

import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
SRC = os.path.join(ROOT, 'docs', 'review', 'abyss_depths')
OUT = os.path.join(SRC, 'contact_sheet.png')

KINDS = ['inverted_forest', 'floating_graves', 'pulse_cave', 'mirror_dark',
         'chain_net', 'lamp_hall', 'dry_well', 'hollow_stars']
THUMB_W, THUMB_H = 480, 270
LABEL_W = 170
PAD = 10


def load_font(size):
    for name in ('arial.ttf', 'DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def main():
    font = load_font(18)
    rows = len(KINDS)
    width = LABEL_W + 2 * (THUMB_W + PAD) + PAD
    height = rows * (THUMB_H + PAD) + PAD + 30
    sheet = Image.new('RGB', (width, height), (18, 18, 22))
    draw = ImageDraw.Draw(sheet)
    draw.text((PAD, 6), 'abyss depths: 8 section kinds (left: from above, right: lower in the section)', fill=(200, 200, 210), font=font)
    y0 = 30
    for i, kind in enumerate(KINDS):
        y = y0 + PAD + i * (THUMB_H + PAD)
        draw.text((PAD, y + THUMB_H // 2 - 10), kind, fill=(230, 230, 240), font=font)
        for j, view in enumerate(('top', 'mid')):
            path = os.path.join(SRC, 'd_%s_%s.png' % (kind, view))
            x = LABEL_W + j * (THUMB_W + PAD)
            if os.path.exists(path):
                img = Image.open(path).convert('RGB').resize((THUMB_W, THUMB_H), Image.LANCZOS)
                sheet.paste(img, (x, y))
            else:
                draw.rectangle([x, y, x + THUMB_W, y + THUMB_H], outline=(120, 40, 40))
                draw.text((x + 10, y + 10), 'missing ' + view, fill=(220, 120, 120), font=font)
    sheet.save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
