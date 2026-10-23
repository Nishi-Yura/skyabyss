# 潮殻のレイドの部屋の建築の見比べ (docs/review/raid_build_tide/sheet.png)。
#   python tools/sheets/raid_build_tide_sheet.py docs/review/raid_build_tide
# 撮影は tools/shots/raid_build_tide.txt (前 = before/、後 = after/)。部屋ごとに「前の広間・後の広間・後の入口・後の隅」を並べる
import sys
from PIL import Image, ImageDraw, ImageFont

root = sys.argv[1]
FONT = "C:/Windows/Fonts/YuGothM.ttc"
BG = (38, 40, 44)
INK = (214, 208, 196)
SUB = (150, 146, 138)
ACC = (196, 150, 160)

rows = [
    ("tr1", "歩 TR1「真珠の灯り運び」", "真珠の座の帆立貝の扇が出口を蝶番に開く。届けた数だけ扇の畝が灯る。広間は暗いまま (隅は暗視で)", "d_hall_nv"),
    ("tr4", "歩 TR4「伸びる珊瑚の道」", "出口を珊瑚の門で囲む。壁は足もとが死んだ珊瑚、肩から上が珊瑚の色。戸を通る床の筋", "c_corner"),
    ("tr5", "智 TR5「光を返す真珠」", "水晶を光の輪で囲む (光を保つと目が灯る)。灯台の上に縞の塔、南東に見渡し台", "c_corner"),
    ("tr6", "智 TR6「潮見の暦」", "六つの壁画を貝殻の額に。天井の真ん中に月の円盤と六つの珊瑚", "c_corner"),
    ("tr3", "闘 TR3「珊瑚の苗を守る」", "苗ごとに床の輪と珊瑚の天蓋 (一番明るい所)。床に渦の線、巣穴は暗い庇", "c_corner"),
    ("tr7", "闘 TR7「本物の群れの母」", "出口の上に群れの母の巣 (巻貝の断面)。柱の頭は珊瑚。床は明るいまま (足跡が見える)", "c_corner"),
    ("trm", "中ボス TRM「潮招きの間」", "北の壁に大きな巻貝の断面、東西に箱庭の窪み。岩の小島は干上がった礁", "c_corner"),
]
W, H = 480, 270
PAD = 18
head = 60
rowh = 52 + H + 30
cols = 4
canvas = Image.new("RGB", (PAD * (cols + 1) + W * cols, head + PAD + rowh * (len(rows) + 1)), BG)
d = ImageDraw.Draw(canvas)
f_title = ImageFont.truetype(FONT, 26)
f_row = ImageFont.truetype(FONT, 20)
f_sub = ImageFont.truetype(FONT, 15)
d.text((PAD, 16), "潮殻のレイドの部屋の建築 (前 → 後)", font=f_title, fill=INK)
y = head
for rid, title, line, extra in rows:
    d.rectangle([PAD, y + 4, PAD + 4, y + 40], fill=ACC)
    d.text((PAD + 14, y), title, font=f_row, fill=INK)
    d.text((PAD + 14, y + 27), line, font=f_sub, fill=SUB)
    shots = [("before", "b_hall", "前: 広間"), ("after", "b_hall", "後: 広間"), ("after", "a_entry", "後: 入口の休み場から"), ("after", extra, "後: " + ("暗視で広間" if extra == "d_hall_nv" else "北東の隅から"))]
    for i, (side, name, cap) in enumerate(shots):
        im = Image.open(f"{root}/{side}/{rid}_{name}.png").convert("RGB").resize((W, H), Image.LANCZOS)
        x = PAD + i * (W + PAD)
        canvas.paste(im, (x, y + 52))
        d.text((x, y + 52 + H + 6), cap, font=f_sub, fill=SUB)
    y += rowh
# 灯る目印 (途中の姿)
d.rectangle([PAD, y + 4, PAD + 4, y + 40], fill=ACC)
d.text((PAD + 14, y), "灯る目印 (途中の姿)", font=f_row, fill=INK)
d.text((PAD + 14, y + 27), "TR1 は届けた真珠の数だけ扇の畝が灯り、TR5 は光を保つあいだ水晶の輪の目が灯る。出口の枠の三つの目は戸が開くと灯る", font=f_sub, fill=SUB)
for i, (name, cap) in enumerate((("tr1_e_clam_lit", "TR1: 三つの真珠を届けた姿"), ("tr5_d_halo_lit", "TR5: 光を水晶へ導いた姿"))):
    im = Image.open(f"{root}/after/{name}.png").convert("RGB").resize((W, H), Image.LANCZOS)
    x = PAD + i * (W + PAD)
    canvas.paste(im, (x, y + 52))
    d.text((x, y + 52 + H + 6), cap, font=f_sub, fill=SUB)
canvas.save(f"{root}/sheet.png")
print("wrote", f"{root}/sheet.png", canvas.size)
