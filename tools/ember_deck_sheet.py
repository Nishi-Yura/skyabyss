# -*- coding: utf-8 -*-
"""tools/shots/ember_deck.txt が撮った絵から、確認用の一枚を docs/review/previews/trial_p2_ember_sheet.png に組む (余燼の試練の階。版 23)。

使い方: ./gradlew runShots -Pscript=tools/shots/ember_deck.txt のあと、python -I tools/ember_deck_sheet.py
出力: docs/review/previews/trial_p2_ember_sheet.png (日本語の見出しつき。落ち着いた色。音なし)
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SHOTS = ROOT / "run/shots/ember_deck/shots/ember_deck"
OUT = ROOT / "docs/review/previews/trial_p2_ember_sheet.png"

TILES = [
    ("hub_board", "炉心の広間: 試練の板と休み場。井戸の看板が試練の階を指す"),
    ("landing_corridor", "螺旋の踊り場から南へ: 試練の階の通路"),
    ("deck_hall", "試練の階の広間: 板・休み場・東西の回廊"),
    ("e02_start", "E02 噴気の渡り: 入口。割れ目は光ってから噴く"),
    ("e02_bands", "E02: 噴気の帯 (筋ごとに噴き方が違う)"),
    ("e10_hall", "E10 鞴持ちの炉: 炉・灰溜まり・噴気の割れ目"),
    ("e06_hall", "E06 煙突の風読み: 八本の煙突と炉の扉"),
    ("e07_hall", "E07 三つの鞴: 踏み板・鞴の塔・炉"),
]
W, H, COLS = 480, 270, 2
BG = (30, 32, 36)
FG = (200, 206, 210)


def font(size):
    for name in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


f = font(15)
rows = (len(TILES) + COLS - 1) // COLS
sheet = Image.new("RGB", (W * COLS, (H + 26) * rows), BG)
d = ImageDraw.Draw(sheet)
for i, (name, title) in enumerate(TILES):
    x = (i % COLS) * W
    y = (i // COLS) * (H + 26)
    p = SHOTS / f"{name}.png"
    if p.exists():
        sheet.paste(Image.open(p).convert("RGB").resize((W, H), Image.LANCZOS), (x, y + 26))
    d.text((x + 6, y + 4), title, fill=FG, font=f)
OUT.parent.mkdir(parents=True, exist_ok=True)
sheet.save(OUT)
print(OUT)
