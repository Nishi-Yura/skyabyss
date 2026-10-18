# -*- coding: utf-8 -*-
"""tools/shots/ux_polish2.txt の絵を 1 枚の比べる表にまとめる。

使い方: python tools/ux_polish2_sheet.py [撮った絵のフォルダ] [出力ファイル]
出力: docs/review/previews/ux_polish2_sheet.png (A2 扉の予告 2 通り / A7 乗り込み口の一行 / A6 出発の帯 / A9 控え帳の章の進み)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "run/shots/ux_polish2/shots/ux_polish2"
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "docs/review/previews/ux_polish2_sheet.png"
CELLS = [
    ("summary_red", "A2 door warning: your lamp is red (+ fix line)"),
    ("summary_others", "A2 door warning: a companion is low (no names)"),
    ("boarding_note_advice", "A7 boarding note: reason + remedy"),
    ("depart_bar", "A6 everyone aboard: leaves in 5 s"),
    ("record_checklist", "A7/A9 record book: lamps + chapter checklist"),
]
W, H = 640, 360
COLS = 2
rows = (len(CELLS) + COLS - 1) // COLS
img = Image.new("RGB", (W * COLS, (H + 22) * rows), (20, 20, 24))
d = ImageDraw.Draw(img)
for i, (name, label) in enumerate(CELLS):
    x = (i % COLS) * W
    y = (i // COLS) * (H + 22)
    f = SRC / (name + ".png")
    if f.exists():
        im = Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS)
        img.paste(im, (x, y + 22))
    d.text((x + 6, y + 5), label, fill=(230, 230, 230))
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(OUT)
