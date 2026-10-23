# 岩鯨のレイドの二つ目の三部屋の見取り図 (WhaleRaid2SelfTest が run/raid2_whale_dump.json に出す、上から見た形と南から見た断面)
#   → docs/review/raid2_whale/raid2_whale_rooms.png
import json
import sys

from PIL import Image, ImageDraw, ImageFont

src = sys.argv[1] if len(sys.argv) > 1 else "run/raid2_whale_dump.json"
out = sys.argv[2] if len(sys.argv) > 2 else "docs/review/raid2_whale/raid2_whale_rooms.png"
d = json.load(open(src, encoding="utf-8"))
F = lambda n: ImageFont.truetype("C:/Windows/Fonts/YuGothM.ttc", n)
BG, FG, MUTE = (30, 31, 33), (208, 208, 202), (140, 142, 140)
COL = {
    "air": (18, 18, 20), "stone_bricks": (92, 92, 94), "polished_andesite": (118, 120, 120), "bone_block": (226, 222, 202),
    "sea_lantern": (170, 210, 200), "iron_bars": (110, 110, 112), "deepslate_tiles": (44, 44, 50), "chiseled_stone_bricks": (104, 104, 108),
    "smooth_quartz_stairs": (206, 202, 190), "lantern": (222, 170, 80), "andesite_wall": (126, 126, 124), "redstone_block": (200, 40, 36),
    "red_nether_bricks": (96, 28, 32), "polished_andesite_wall": (126, 126, 124),
}
titles = {
    "WR4a": ("歩 WR4「胃の縦穴の上り」(階段は引っ込んでいる)", "縦穴の底から胃液が上がる。四つの踊り場を、スイッチを叩くと生える階段でつないで頂へ。"),
    "WR4b": ("同 (階段が四つとも生えたところ)", "親台 (底の壇) と踊り場の端の手元に、骨のスイッチ。一人が壇で叩き、もう一人が登る。"),
    "WR5a": ("智 WR5「石の海の床合わせ」(ばらばら)", "床の石板は叩くと回る。北の壁の高いところの見本と見比べる。入口の上の見張り台から見渡せる。"),
    "WR5b": ("同 (見本どおりに合わせたところ)", "背骨・肋骨・潮吹き・尾びれ。形は挑戦ごとに違う。"),
    "WR6a": ("闘 WR6「縮む輪」(始まり)", "安全な輪は広間いっぱい。隅の床だけ最初から赤い。"),
    "WR6b": ("同 (輪が縮んだところ)", "縁は赤い線、外は赤いレンガ。外にいる人と敵は傷を受ける。"),
}
S = 9
order = [("WR4a", "WR4b"), ("WR5a", "WR5b"), ("WR6a", "WR6b")]


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def render(rows, mode):
    h, w = len(rows), len(rows[0])
    im = Image.new("RGB", (w * S, h * S), COL["air"])
    dr = ImageDraw.Draw(im)
    for z, row in enumerate(rows):
        for x, (name, v) in enumerate(row):
            c = COL.get(name, (120, 100, 110))
            if mode == "top":
                k = 0.62 + 0.016 * min(v, 22)
            else:
                k = max(0.5, 1.0 - 0.04 * v)
            if name == "air":
                c, k = COL["air"], 1.0
            dr.rectangle([x * S, z * S, x * S + S - 1, z * S + S - 1], fill=shade(c, k))
    return im


panels = []
for pair in order:
    row = []
    for k in pair:
        row.append((k, render(d[k]["top"], "top"), render(d[k]["elev"], "elev")))
    panels.append(row)

colw = max(max(p[1].width + 16 + p[2].width for p in row) for row in panels)
W = 60 * 3 + colw * 2
rowh = [max(max(p[1].height, p[2].height) for p in row) + 110 for row in panels]
H = 90 + sum(rowh) + 30
sheet = Image.new("RGB", (W, H), BG)
dr = ImageDraw.Draw(sheet)
dr.text((60, 22), "岩鯨のレイド 二つ目の三部屋（左 = 上から見た形・北が上、右 = 南から見た断面・北の壁が奥）", font=F(24), fill=FG)
y = 90
for ri, row in enumerate(panels):
    x = 60
    for k, top, elev in row:
        sheet.paste(top, (x, y))
        sheet.paste(elev, (x + top.width + 16, y))
        t, desc = titles[k]
        dr.text((x, y + max(top.height, elev.height) + 10), t, font=F(18), fill=FG)
        line, yy = "", y + max(top.height, elev.height) + 40
        for ch in desc:
            line += ch
            if dr.textlength(line, font=F(13)) > colw - 8:
                dr.text((x, yy), line, font=F(13), fill=MUTE)
                line, yy = "", yy + 20
        dr.text((x, yy), line, font=F(13), fill=MUTE)
        x += colw + 60
    y += rowh[ri]
sheet.save(out)
print(out, sheet.size)
