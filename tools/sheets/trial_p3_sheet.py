# -*- coding: utf-8 -*-
"""雷翼の第二の渡り場 (docs/design/29 の P3、版 24) の見取り図。ゲームを起こさず、座標だけから描く (落ち着いた色)。
使い方: python tools/sheets/trial_p3_sheet.py → docs/review/previews/trial_p3_storm_ember_sheet.png"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent.parent
OUT = ROOT / "docs/review/previews/trial_p3_storm_ember_sheet.png"
FONT = next(p for p in ["C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"] if Path(p).exists())
BG, LINE, TEXT, DIM = (24, 27, 34), (58, 66, 80), (200, 205, 214), (128, 136, 150)
WIND, THUNDER, GARDEN, YARD = (110, 150, 176), (190, 150, 100), (70, 90, 70), (150, 156, 168)
S = 3.2  # px / block
X0, Z0 = -160, 60


def f(n):
    return ImageFont.truetype(FONT, n)


W, H = int(330 * S) + 40, int(220 * S) + 90
img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)


def box(x1, z1, x2, z2, col, label=None, fill=True):
    a = (20 + (x1 - X0) * S, 70 + (z1 - Z0) * S)
    b = (20 + (x2 + 1 - X0) * S, 70 + (z2 + 1 - Z0) * S)
    d.rectangle((*a, *b), fill=col if fill else None, outline=LINE if fill else col)
    if label:
        d.text((a[0] + 3, a[1] + 2), label, font=f(11), fill=(20, 22, 28) if fill else TEXT)


d.text((20, 10), "雷翼 P3: 第二の渡り場と五つの新作 (版 24)", font=f(20), fill=TEXT)
d.text((20, 40), "真上から。第一の渡り場 (z 64〜82) の南から橋 (x -2〜2) が尾羽の庭の隙間を越えて、第二の渡り場 (z 172〜190) へ。戸は五つ", font=f(11), fill=DIM)
box(-34, 122, -5, 164, GARDEN, "尾羽の庭 (西)")
box(5, 122, 34, 164, GARDEN, "尾羽の庭 (東)")
box(-24, 64, 24, 82, YARD, "第一の渡り場")
box(-2, 83, 2, 171, YARD)
box(-24, 172, 24, 190, YARD, "第二の渡り場")
box(-150, 178, -26, 184, WIND, "S04 羽根の吊り橋")
box(-145 - 5, 176, -140, 186, WIND)
box(32, 171, 52, 191, WIND, "S08 気圧の弁")
box(-64, 199, -20, 225, THUNDER, "S07 二枚の鏡の間")
box(-13, 215, 13, 241, THUNDER, "S12 羽守長の頂")
box(-5, 199, 5, 209, WIND)
box(-4, 246, 4, 254, THUNDER)
box(18, 198, 70, 250, THUNDER, "S11 避雷の護送")
img.save(OUT)
print(OUT)
