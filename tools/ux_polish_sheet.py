# -*- coding: utf-8 -*-
"""tools/shots/ux_polish.txt の絵を 1 枚の比べる表にまとめる。

使い方: python tools/ux_polish_sheet.py [撮った絵のフォルダ] [出力ファイル]
出力: docs/review/previews/ux_polish_sheet.png (仲間の札の記号 / 墓の標の札と矢印 / 石の知らせ 3 通り / 控え帳の巨神の頁)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "run/shots/ux_polish/shots/ux_polish"
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "docs/review/previews/ux_polish_sheet.png"
CELLS = [
    ("party_symbols", "A11 party marks: symbol + health bar"),
    ("grave_marker_tag", "A8 grave marker: on screen"),
    ("grave_marker_arrow", "A8 grave marker: edge arrow"),
    ("stele_self", "A4 stele: you rang it"),
    ("stele_other", "A4 stele: a companion rang it"),
    ("stele_none", "A4 stele: not yet"),
    ("record_titan_page", "A4 record book: trial stones row"),
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
