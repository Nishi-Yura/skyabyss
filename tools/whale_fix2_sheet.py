# -*- coding: utf-8 -*-
"""tools/shots/whale_fix2.txt が撮った絵から、確認用の一枚を docs/review/previews/whale_fix2_sheet.png に組む (岩鯨の試遊の声 2 回目。版 24)。

使い方: ./gradlew runShots -Pscript=tools/shots/whale_fix2.txt のあと、python -I tools/whale_fix2_sheet.py
出力: docs/review/previews/whale_fix2_sheet.png (日本語の見出しつき。落ち着いた色。音なし)
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SHOTS = ROOT / "run/shots/whale_fix2/shots/whale_fix2"
OUT = ROOT / "docs/review/previews/whale_fix2_sheet.png"

TILES = [
    ("tablets_arc", "碑: 左右対称の弧 (歩・智・闘)・台座・灯り"),
    ("tablets_side", "碑を横から: 同じ高さ・同じ向き"),
    ("hub_board_pad", "板・案内の碑・光る床 (試練の階へ飛ぶ)"),
    ("stair_sign", "入口の通路: 試練の階への階段の口の碑"),
    ("deck_hall", "試練の階の広間: 案内の碑と戻る光る床"),
    ("gallery_e2", "東: 射手の回廊は戦わない通路"),
    ("heart_rest_west", "心臓の前の休み場の西: 歌の祠をなくした"),
    ("trial_seal", "試練済みの封印 (越えた試練の入口)"),
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
