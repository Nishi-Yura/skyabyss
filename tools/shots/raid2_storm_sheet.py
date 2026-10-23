# 雷翼のレイドの新しい三つの部屋 (SR4・SR5・SR6) の台紙 (docs/review/raid2_storm/sheet.png)。
#   python tools/shots/raid2_storm_sheet.py run/shots/raid2_storm/shots/raid2_storm docs/review/raid2_storm
# 撮影は tools/shots/raid2_storm.txt。落ち着いた色の台紙に、部屋ごとの二枚と一行の説明を並べる
import sys
from PIL import Image, ImageDraw, ImageFont

src, out = sys.argv[1], sys.argv[2]
FONT = "C:/Windows/Fonts/YuGothM.ttc"
BG = (38, 40, 44)
INK = (214, 208, 196)
SUB = (150, 146, 138)
ACC = (176, 128, 84)

rows = [
    ("歩 SR4「雷柱から逃げて鍵集め」", "雷の柱が最も近い人を追い、立ち止まって前へ赤い帯を引く。散らばる三つの鍵を集める",
     "sr4_a_column", "sr4_b_beam", "甲板を歩く雷の柱", "赤い帯の予告"),
    ("智 SR5「雷の拍子覚え」", "九枚の板が拍に合わせて光る。同じ順に、拍に合わせて踏む。順は四回で 3→6 枚へ",
     "sr5_a_first", "sr5_b_third", "一つ目の板が光る", "三つ目の板が光る"),
    ("闘 SR6「嵐の目へ」", "中央から外へ吹く突風に逆らって進む。印が出たら柱の陰へ。中央の守り手を倒す",
     "sr6_a_keeper", "sr6_b_gust", "中央の守り手と柱", "突風の押しの印"),
]
W, H = 640, 360
PAD = 24
head = 64
rowh = 56 + H + 36
canvas = Image.new("RGB", (PAD * 3 + W * 2, head + PAD + rowh * len(rows)), BG)
d = ImageDraw.Draw(canvas)
f_title = ImageFont.truetype(FONT, 26)
f_row = ImageFont.truetype(FONT, 20)
f_sub = ImageFont.truetype(FONT, 15)
d.text((PAD, 18), "雷翼のレイド 新しい三つの部屋", font=f_title, fill=INK)
y = head
for title, line, a, b, ca, cb in rows:
    d.rectangle([PAD, y + 4, PAD + 4, y + 40], fill=ACC)
    d.text((PAD + 14, y), title, font=f_row, fill=INK)
    d.text((PAD + 14, y + 28), line, font=f_sub, fill=SUB)
    for i, (name, cap) in enumerate(((a, ca), (b, cb))):
        im = Image.open(f"{src}/{name}.png").convert("RGB").resize((W, H), Image.LANCZOS)
        x = PAD + i * (W + PAD)
        canvas.paste(im, (x, y + 56))
        d.text((x, y + 56 + H + 8), cap, font=f_sub, fill=SUB)
    y += rowh
canvas.save(f"{out}/sheet.png")
print("saved", f"{out}/sheet.png")
