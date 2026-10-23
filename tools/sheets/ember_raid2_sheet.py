# 余燼のレイドの二案め三部屋 (ER4〜ER6) の見取り図 (自己テストが run/raid_ember2_dump.json に出す上からの形) → docs/review/raid_ember/ember_raid_rooms.png
import json, sys
from PIL import Image, ImageDraw, ImageFont

src = sys.argv[1] if len(sys.argv) > 1 else "run/raid_ember2_dump.json"
out = sys.argv[2] if len(sys.argv) > 2 else "docs/review/raid2_ember/ember_raid2_rooms.png"
d = json.load(open(src, encoding="utf-8"))
F = lambda n: ImageFont.truetype("C:/Windows/Fonts/YuGothM.ttc", n)
BG, FG, MUTE = (34, 31, 30), (214, 205, 196), (150, 140, 132)
COL = {
    "air": (20, 18, 18), "blackstone": (52, 48, 52), "polished_blackstone": (70, 66, 72), "polished_blackstone_bricks": (82, 78, 86),
    "smooth_basalt": (66, 62, 64), "basalt": (74, 72, 72), "polished_basalt": (88, 86, 86), "magma_block": (176, 78, 44),
    "shroomlight": (214, 150, 70), "coal_block": (28, 26, 26), "smithing_table": (120, 96, 78), "blast_furnace": (96, 90, 90),
    "water_cauldron": (72, 92, 110), "anvil": (60, 60, 66), "light_gray_concrete": (150, 150, 146), "orange_concrete": (196, 110, 52),
    "cyan_concrete": (70, 130, 140), "yellow_concrete": (200, 170, 70), "cracked_polished_blackstone_bricks": (76, 72, 80),
    "dark_oak_planks": (92, 66, 40), "iron_bars": (150, 150, 156), "chain": (110, 112, 120), "copper_block": (190, 110, 80),
    "exposed_copper": (160, 120, 96), "weathered_copper": (100, 150, 120), "oxidized_copper": (80, 160, 140), "gilded_blackstone": (150, 120, 60),
    "chiseled_polished_blackstone": (62, 58, 66), "grindstone": (120, 120, 126),
}
titles = {
    "ER4": ("歩 ER4「落ちる鎖の道」", "熾の海の上の島四つを、鎖で吊った板の橋三本でつなぐ。乗ると手前の板から赤く光って落ちる。歩き続ければ渡れる。落ちたら手前の島から。"),
    "ER5": ("智 ER5「炉の圧合わせ」", "北の壁に七目盛りの圧力計が四つ。手前の弁を回すと隣の針も動く。四つの針を同じ目盛りへそろえる。始まりは毎回違い、必ず解ける。"),
    "ER6": ("闘 ER6「鍛え手の弟子たち」", "金床から弟子が一人ずつ出る（四人）。後ほど強く、鍛え手の技を一つずつ増やして使う。壁の四つの灯が倒した数。"),
}
S = 9
panels = []
for k in ("ER4", "ER5", "ER6"):
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
H = max(p[1].height for p in panels) + 290
sheet = Image.new("RGB", (W, H), BG)
dr = ImageDraw.Draw(sheet)
dr.text((60, 20), "余燼の背のレイドの二案めの三つの部屋（上から見た形。北が上）", font=F(26), fill=FG)
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
