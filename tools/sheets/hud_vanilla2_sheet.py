"""バニラの HUD をずらす確認の比べ表 2 (docs/review/previews/hud_vanilla2_sheet.png)。札・チャット・効き目・字幕・アクションバー・横板が mod の HUD に重ならない。
GUI 倍率 2・3・4 で、効き目の絵・ボスバー・チャット・アクションバー・スコアボードの横板・札と mod の HUD を同時に出した絵。
使い方: python tools/sheets/hud_vanilla2_sheet.py <絵のフォルダ (run/shots/hud_vanilla2/shots/hud_vanilla2)> <出力の png>
"""
import sys
from PIL import Image, ImageDraw, ImageFont

folder, out = sys.argv[1:3]
cells = [
    ("vanilla2_boss_s2", "ボス戦 / GUI 2"),
    ("vanilla2_boss_s3", "ボス戦 / GUI 3"),
    ("vanilla2_boss_s4", "ボス戦 / GUI 4 (1920x1080 の自動)"),
    ("vanilla2_opening_s2", "序盤 / GUI 2"),
    ("vanilla2_opening_s3", "序盤 / GUI 3"),
    ("vanilla2_opening_s4", "序盤 / GUI 4"),
    ("vanilla2_back_s2", "背の中 / GUI 2"),
    ("vanilla2_back_s3", "背の中 / GUI 3"),
    ("vanilla2_back_s4", "背の中 / GUI 4"),
]
W, H = 640, 360
PAD = 8
LABEL = 24
cols = 3
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
