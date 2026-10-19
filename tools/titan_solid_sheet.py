"""Builds docs/review/previews/titan_solid_sheet.png from the shots of tools/shots/titan_solid.txt (before = titanweight off, after = on)."""
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'run', 'shots', 'titan_solid', 'shots', 'titan_solid')
OUT = os.path.join(ROOT, 'docs', 'review', 'previews', 'titan_solid_sheet.png')
W, H = 400, 225
titans = ['whale', 'tide', 'storm', 'ember']
views = ['close', 'below', 'side']
sheet = Image.new('RGB', (W * 6 + 10, (H + 18) * 4 + 22), (24, 24, 28))
d = ImageDraw.Draw(sheet)
col = 0
for v in views:
    for ba in ('before', 'after'):
        d.text((col * W + 6, 4), '%s %s' % (v, ba), fill=(230, 230, 230))
        col += 1
for r, t in enumerate(titans):
    col = 0
    for v in views:
        for ba in ('before', 'after'):
            p = os.path.join(SRC, '%s_%s_%s.png' % (t, v, ba))
            if os.path.exists(p):
                im = Image.open(p).convert('RGB').resize((W, H), Image.LANCZOS)
                sheet.paste(im, (col * W, 22 + r * (H + 18)))
            d.text((col * W + 6, 22 + r * (H + 18) + H + 3), t, fill=(180, 180, 190))
            col += 1
os.makedirs(os.path.dirname(OUT), exist_ok=True)
sheet.save(OUT)
print(OUT)
