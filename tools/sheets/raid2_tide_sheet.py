# 潮殻のレイドの新しい四部屋の台紙 (docs/review/raid2_tide/sheet.png)。
#   python tools/sheets/raid2_tide_sheet.py run/shots/raid2_tide/shots/raid2_tide docs/review/raid2_tide
# 撮影は tools/shots/raid2_tide.txt。落ち着いた色の台紙に、部屋ごとの二枚と一行の説明を並べる
import sys
from PIL import Image, ImageDraw, ImageFont

src, out = sys.argv[1], sys.argv[2]
FONT = "C:/Windows/Fonts/YuGothM.ttc"
BG = (38, 40, 44)
INK = (214, 208, 196)
SUB = (150, 146, 138)
ACC = (176, 128, 84)

rows = [
    ("歩 TR4「伸びる珊瑚の道」", "三枚の珊瑚の壁の戸が、枝を伸ばして閉じ、引いて開く。床の桃色の絨毯が、伸びる順を知らせる。戸に立てば閉じない",
     "tr4_a_gates", "tr4_b_carpet", "入口側から見た三枚の壁", "予告の絨毯 (閉じる戸)"),
    ("智 TR5「光を返す真珠」", "灯台の光を、真珠の台を回して北の水晶へ導く。台の縁の白い印が、光を返す向き。配置は挑戦ごとに変わる",
     "tr5_a_unsolved", "tr5_b_lit", "はじめ (光は途中で途切れる)", "導いたあと (水晶が灯る)"),
    ("智 TR6「潮見の暦」", "壁画の六つの月と点の数を読み、床の月の石を同じ月に合わせる。合っているかは、全部揃うまで分からない",
     "tr6_a_mural_north", "tr6_c_dials", "北の壁の壁画 (1・2・3 番)", "月の石の台 (三つ合わせたところ)"),
    ("闘 TR7「本物の群れの母」", "群れの母とそっくりの偽物たち。本物だけが足もとに影と足跡を持つ。偽物を叩くと弾けて、時間が無駄になる",
     "tr7_a_hall", "tr7_b_mother", "広間 (どれが本物か)", "足もとの影と足跡"),
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
d.text((PAD, 18), "潮殻のレイド 新しい四つの部屋", font=f_title, fill=INK)
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
