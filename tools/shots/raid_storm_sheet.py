# 雷翼のレイドの三つの部屋の台紙 (docs/review/raid_storm/sheet.png)。
#   python tools/shots/raid_storm_sheet.py run/shots/raid_storm/shots/raid_storm docs/review/raid_storm
# 撮影は tools/shots/raid_storm.txt。落ち着いた色の台紙に、部屋ごとの二枚と一行の説明を並べる
import sys
from PIL import Image, ImageDraw, ImageFont

src, out = sys.argv[1], sys.argv[2]
FONT = "C:/Windows/Fonts/YuGothM.ttc"
BG = (38, 40, 44)
INK = (214, 208, 196)
SUB = (150, 146, 138)
ACC = (176, 128, 84)

rows = [
    ("歩 SR1「避雷の羽運び」", "風の甲板で雷羽を東の止まり木へ。持つ人に拍で雷、護符の仲間か避雷板が受ける",
     "sr1_a_deck", "sr1_b_perches", "甲板 (西の羽の台と避雷板)", "東の止まり木"),
    ("智 SR2「銅の回路つなぎ」", "タイルを叩いて回し、電源から四つのランプへ。つながった線は光って流れる",
     "sr2_a_grid", "sr2_b_floor", "回路の盤", "盤の近く"),
    ("闘 SR3「帯電の連鎖」", "帯電して光る敵を倒すと、雷が近くの敵へ跳ぶ。三つの波のあと帯電した精鋭",
     "sr3_a_charging", "sr3_b_glow", "帯電していく敵", "光る敵"),
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
d.text((PAD, 18), "雷翼のレイド 三つの部屋", font=f_title, fill=INK)
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
