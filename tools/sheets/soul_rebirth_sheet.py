# -*- coding: utf-8 -*-
"""tools/shots/soul_rebirth.txt の絵を 1 枚の表にまとめる (控えめな色)。
使い方: python -I tools/sheets/soul_rebirth_sheet.py → docs/review/previews/rebirth_sheet.png"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "run/shots/soul_rebirth/shots/soul_rebirth"
OUT = ROOT / "docs/review/previews/rebirth_sheet.png"
CELLS = [
    ("soul_locked", "Lv 50 前: 灰色の説明だけ"),
    ("soul_ready", "転生 2 回・Lv 50: 恩恵 3 択と魂盤の腕を選ぶ"),
    ("soul_12", "転生 12 回: 魂盤と魂盤の力 (台座の外ではボタンが止まる)"),
    ("pouch_spare", "旅嚢の「余白」(転生 12 回で 3 枠)"),
]
W, H, COLS = 560, 315, 2
font = None
for name in ("meiryo.ttc", "msgothic.ttc", "YuGothR.ttc"):
    try:
        font = ImageFont.truetype("C:/Windows/Fonts/" + name, 14)
        break
    except OSError:
        pass
rows = (len(CELLS) + COLS - 1) // COLS
img = Image.new("RGB", (W * COLS, (H + 24) * rows), (28, 30, 34))
d = ImageDraw.Draw(img)
for i, (n, label) in enumerate(CELLS):
    x, y = (i % COLS) * W, (i // COLS) * (H + 24)
    f = SRC / (n + ".png")
    if f.exists():
        img.paste(Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS), (x, y + 24))
    d.text((x + 6, y + 4), label, fill=(190, 194, 200), font=font)
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(OUT)
