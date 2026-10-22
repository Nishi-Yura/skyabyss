"""HUD の置き場の整理の前後の比べ (docs/review/previews/hud_layout_sheet.png)。
前 = HUD 置き場の整理の前のコードで撮った絵、後 = 整理のあと。三つの場面 x GUI 倍率 2 と 4。
使い方: python tools/sheets/hud_layout_sheet.py <前の絵のフォルダ> <後の絵のフォルダ> <出力の png>
"""
import sys
from PIL import Image, ImageDraw, ImageFont

before, after, out = sys.argv[1:4]
scenes = [("opening", "序盤 (手引き 2 行・字幕・目的の帯)"), ("boss", "ボス戦 (ボスバー 3 本・効き目・仲間・誓い・目盛り)"), ("back", "背の中 (目盛りと目的の板・誓い・仲間・札)")]
cols = [("s2", "GUI 倍率 2"), ("s4", "GUI 倍率 4")]
W, H = 640, 360
PAD = 8
LABEL = 26
try:
    font = ImageFont.truetype("C:/Windows/Fonts/YuGothM.ttc", 16)
except Exception:
    font = ImageFont.load_default()

rows = len(scenes)
sheet_w = PAD + (W + PAD) * len(cols) * 2
sheet_h = PAD + rows * (LABEL + H + PAD) + LABEL
img = Image.new("RGB", (sheet_w, sheet_h), (24, 28, 34))
d = ImageDraw.Draw(img)
x0 = PAD
for ci, (c, cl) in enumerate(cols):
    for bi, (folder, bl) in enumerate([(before, "整理の前"), (after, "整理のあと")]):
        x = PAD + (ci * 2 + bi) * (W + PAD)
        d.text((x, 4), f"{bl} / {cl}", fill=(240, 220, 150) if bi else (200, 200, 200), font=font)
        for ri, (s, sl) in enumerate(scenes):
            y = LABEL + ri * (LABEL + H + PAD)
            im = Image.open(f"{folder}/{s}_{c}.png").convert("RGB").resize((W, H), Image.LANCZOS)
            img.paste(im, (x, y + LABEL - 4))
            if ci == 0 and bi == 0:
                pass
for ri, (s, sl) in enumerate(scenes):
    y = LABEL + ri * (LABEL + H + PAD)
    d.text((PAD + 4, y), sl, fill=(150, 220, 215), font=font)
img.save(out, optimize=True)
print("saved", out, img.size)
