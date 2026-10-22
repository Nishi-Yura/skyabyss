"""バニラの HUD をずらす確認の比べ表 (docs/review/previews/hud_vanilla_sheet.png)。
GUI 倍率 2・3・4 で、効き目の絵・ボスバー・チャット・アクションバー・スコアボードの横板・札と mod の HUD を同時に出した絵。
使い方: python tools/sheets/hud_vanilla_sheet.py <絵のフォルダ (run/shots/hud_vanilla/shots/hud_vanilla)> <出力の png>
"""
import sys
from PIL import Image, ImageDraw, ImageFont

folder, out = sys.argv[1:3]
cells = [
    ("vanilla_boss_s2", "ボス戦 / GUI 2: 効き目の絵は右の板の下へ、横板も下へ"),
    ("vanilla_boss_s3", "ボス戦 / GUI 3"),
    ("vanilla_boss_s4", "ボス戦 / GUI 4 (混みすぎて空きがない所は、そのまま)"),
    ("vanilla_opening_s2", "序盤 / GUI 2: チャット・アクションバー"),
    ("vanilla_opening_s3", "序盤 / GUI 3: 効き目は右上のまま (重ならない)"),
    ("vanilla_back_s3", "背の中 / GUI 3"),
]
W, H = 640, 360
PAD = 8
LABEL = 24
cols = 2
try:
    font = ImageFont.truetype("C:/Windows/Fonts/YuGothM.ttc", 16)
except Exception:
    font = ImageFont.load_default()
rows = (len(cells) + cols - 1) // cols
img = Image.new("RGB", (PAD + (W + PAD) * cols, PAD + rows * (LABEL + H + PAD)), (24, 28, 34))
d = ImageDraw.Draw(img)
for i, (name, label) in enumerate(cells):
    x = PAD + (i % cols) * (W + PAD)
    y = PAD + (i // cols) * (LABEL + H + PAD)
    d.text((x, y), label, fill=(240, 220, 150), font=font)
    im = Image.open(f"{folder}/{name}.png").convert("RGB").resize((W, H), Image.LANCZOS)
    img.paste(im, (x, y + LABEL))
img.save(out, optimize=True)
print("saved", out, img.size)
