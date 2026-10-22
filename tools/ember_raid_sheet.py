# 余燼のレイド三部屋の見取り図 (自己テストが run/raid_ember_dump.json に出す上からの形) → docs/review/raid_ember/ember_raid_rooms.png
import json, sys
from PIL import Image, ImageDraw, ImageFont

src = sys.argv[1] if len(sys.argv) > 1 else "run/raid_ember_dump.json"
out = sys.argv[2] if len(sys.argv) > 2 else "docs/review/raid_ember/ember_raid_rooms.png"
d = json.load(open(src, encoding="utf-8"))
F = lambda n: ImageFont.truetype("C:/Windows/Fonts/YuGothM.ttc", n)
BG, FG, MUTE = (34, 31, 30), (214, 205, 196), (150, 140, 132)
COL = {
    "air": (20, 18, 18), "blackstone": (52, 48, 52), "polished_blackstone": (70, 66, 72), "polished_blackstone_bricks": (82, 78, 86),
    "smooth_basalt": (66, 62, 64), "basalt": (74, 72, 72), "polished_basalt": (88, 86, 86), "magma_block": (176, 78, 44),
    "shroomlight": (214, 150, 70), "coal_block": (28, 26, 26), "smithing_table": (120, 96, 78), "blast_furnace": (96, 90, 90),
    "water_cauldron": (72, 92, 110), "anvil": (60, 60, 66), "light_gray_concrete": (150, 150, 146), "orange_concrete": (196, 110, 52),
    "cyan_concrete": (70, 130, 140), "yellow_concrete": (200, 170, 70), "cracked_polished_blackstone_bricks": (76, 72, 80),
}
titles = {
    "ER1": ("歩 ER1「熾火運び」", "北の噴き口の熾を、冷める前に南の冷えた炉へ三つ。運ぶ者は熱で傷み、しゃがむと仲間へ渡せる。赤い印のあとに噴気。"),
    "ER2": ("智 ER2「鍛冶の手順」", "北の壁画の順（型・火・水・槌）に、四つの台を使う。台は先に支度でき、順違いなら戻る（傷なし）。"),
    "ER3": ("闘 ER3「炉を守る」", "中央の大炉を三つの波から守る。四隅の火鉢が灯っていると敵が遅くなる。炉が消えたらやり直し。"),
}
S = 11
panels = []
for k in ("ER1", "ER2", "ER3"):
    r = d[k]["rows"]
    h, w = len(r), len(r[0])
    im = Image.new("RGB", (w * S, h * S), COL["air"])
    dr = ImageDraw.Draw(im)
    for z, row in enumerate(r):
        for x, (name, top) in enumerate(row):
            c = COL.get(name, (110, 100, 100))
            k2 = 0.78 + 0.05 * max(0, min(top, 6))
            c = tuple(min(255, int(v * k2)) for v in c)
            dr.rectangle([x * S, z * S, x * S + S - 1, z * S + S - 1], fill=c)
    panels.append((k, im))
W = sum(p[1].width for p in panels) + 60 * 4
H = max(p[1].height for p in panels) + 190
sheet = Image.new("RGB", (W, H), BG)
dr = ImageDraw.Draw(sheet)
dr.text((60, 20), "余燼の背のレイドの三つの部屋（上から見た形。北が上）", font=F(26), fill=FG)
x = 60
for k, im in panels:
    sheet.paste(im, (x, 80))
    t, desc = titles[k]
    dr.text((x, 80 + im.height + 14), t, font=F(20), fill=FG)
    line, y = "", 80 + im.height + 46
    for ch in desc:
        line += ch
        if dr.textlength(line, font=F(14)) > im.width - 6:
            dr.text((x, y), line, font=F(14), fill=MUTE)
            line, y = "", y + 22
    dr.text((x, y), line, font=F(14), fill=MUTE)
    x += im.width + 60
sheet.save(out)
print(out, sheet.size)
