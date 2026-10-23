# -*- coding: utf-8 -*-
"""UI 直し 3 の確認シート (道しるべの一本線・つまみの字・ツールチップ)。台本 tools/shots/ui_fix3.txt の絵を並べる。

使い方: python -I tools/sheets/ui_fix3_sheet.py
出力: docs/review/previews/ui_fix3_sheet.png
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/review/previews/ui_fix3_sheet.png"
SHOTS = ROOT / "run/shots/ui_fix3/shots/ui_fix3"
FONT = "C:/Windows/Fonts/meiryo.ttc"
BG, INK = (22, 26, 33), (232, 236, 240)
W, H = 640, 360
PANELS = [
    ("trail_main_first", "道しるべ (メイン・金): 一人称"),
    ("trail_main_back", "道しるべ (メイン): 三人称・でこぼこの地面"),
    ("trail_side_back", "道しるべ (サブ・青緑)"),
    ("inv_g3_bow", "持ち物 GUI 3: 弓のツールチップ"),
    ("inv_g3_axe_lore", "GUI 3: 長い説明 (折り返し)"),
    ("inv_g4_axe_lore", "GUI 4: 長い説明 (縮めて収める)"),
    ("inv_g2_bow", "GUI 2"),
    ("acc_g3", "装飾の画面 GUI 3: つまみの字"),
    ("acc_g3_hover", "装飾 GUI 3: ツールチップ"),
    ("pouch_g3", "旅嚢 GUI 3: つまみの字"),
    ("acc_g4", "装飾 GUI 4"),
    ("acc_g2", "装飾 GUI 2"),
]


def main():
    f = ImageFont.truetype(FONT, 18)
    cols = 3
    rows = (len(PANELS) + cols - 1) // cols
    img = Image.new("RGB", (cols * (W + 10) + 10, rows * (H + 40) + 50), BG)
    d = ImageDraw.Draw(img)
    d.text((14, 10), "UI 直し 3: 道しるべの一本線 / つまみの字 / ツールチップの収まり", font=ImageFont.truetype(FONT, 22), fill=INK)
    for i, (name, label) in enumerate(PANELS):
        x = 10 + (i % cols) * (W + 10)
        y = 48 + (i // cols) * (H + 40)
        d.text((x, y), label, font=f, fill=INK)
        p = SHOTS / (name + ".png")
        if p.exists():
            im = Image.open(p).convert("RGB")
            im.thumbnail((W, H))
            img.paste(im, (x, y + 26))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(OUT)


if __name__ == "__main__":
    main()
