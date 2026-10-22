# -*- coding: utf-8 -*-
"""tools/shots/builds.txt の絵を 1 枚にまとめる (天職・刻印・伝説の書き換えの K 画面)。

使い方: python tools/sheets/builds_sheet.py [撮った絵のフォルダ] [出力ファイル]
出力: docs/review/previews/builds_sheet.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "run/shots/builds/shots/builds"
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "docs/review/previews/builds_sheet.png"
CELLS = [
    ("builds_1_before", "天職の前: 木は 1 行目だけ"),
    ("builds_2_calling", "天職を選ぶ (岩鯨の心臓のあと)"),
    ("builds_3_rune", "天職の木: 極めた技に刻印の 3 択"),
    ("builds_4_legend", "伝説の武器を持つと、4 つ目の行"),
    ("builds_5_calling2", "二つ目の天職 (雷翼のあと)"),
]
W, H = 560, 315
COLS = 2
rows = (len(CELLS) + COLS - 1) // COLS
font = None
for name in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc", "C:/Windows/Fonts/YuGothM.ttc"):
    try:
        font = ImageFont.truetype(name, 14)
        break
    except OSError:
        pass
img = Image.new("RGB", (W * COLS, (H + 24) * rows), (20, 20, 24))
d = ImageDraw.Draw(img)
for i, (name, label) in enumerate(CELLS):
    x = (i % COLS) * W
    y = (i // COLS) * (H + 24)
    f = SRC / (name + ".png")
    if f.exists():
        im = Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS)
        img.paste(im, (x, y + 24))
    d.text((x + 6, y + 4), label, fill=(230, 230, 230), font=font)
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(OUT)
