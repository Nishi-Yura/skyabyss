# -*- coding: utf-8 -*-
"""Builds docs/review/previews/smoke_column_sheet.png from tools/shots/smoke_sheet.txt runs (docs/design/17 3.2.1).

usage: python tools/smoke_sheet.py <before dir> <after dir> <spot:label> [<spot:label> ...]
  rows = terrains, columns = before noon / after noon / before dusk / after dusk
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
before, after = Path(sys.argv[1]), Path(sys.argv[2])
spots = [a.split(":", 1) for a in sys.argv[3:]]
W, H = 480, 270
COLS = [("before, noon", before, "noon"), ("after, noon", after, "noon"), ("before, dusk", before, "dusk"), ("after, dusk", after, "dusk")]
sheet = Image.new("RGB", (W * 4, (H + 20) * len(spots)), (20, 20, 24))
d = ImageDraw.Draw(sheet)
for r, (n, label) in enumerate(spots):
    for c, (title, folder, tod) in enumerate(COLS):
        f = folder / ("s%s_%s.png" % (n, tod))
        im = Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS) if f.exists() else Image.new("RGB", (W, H), (60, 20, 20))
        x, y = c * W, r * (H + 20)
        sheet.paste(im, (x, y + 20))
        d.text((x + 6, y + 4), "%s - %s" % (label, title), fill=(230, 230, 230))
out = ROOT / "docs/review/previews/smoke_column_sheet.png"
out.parent.mkdir(parents=True, exist_ok=True)
sheet.save(out)
print("wrote", out)
