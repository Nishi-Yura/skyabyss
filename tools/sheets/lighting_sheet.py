# 歩く道の灯りの前後の表 (docs/review/previews/lighting_sheet.png)。灯りの場所 (route_lamps.json) を上から見た点と、暗い所の数
import json, os
from PIL import Image, ImageDraw, ImageFont
R = os.path.join(os.path.dirname(__file__), '..', '..')
F = ImageFont.truetype('C:/Windows/Fonts/meiryo.ttc', 15)
FS = ImageFont.truetype('C:/Windows/Fonts/meiryo.ttc', 12)
rows = [('潮殻', 'tide', 5287, 2, 481, 36, (90, 150, 170)), ('雷翼', 'storm', 500, 0, 116, 0, (170, 150, 90)), ('余燼', 'ember', 1187, 0, 220, 0, (170, 100, 80))]
W, H = 900, 330
im = Image.new('RGB', (W, H), (36, 36, 40))
d = ImageDraw.Draw(im)
d.text((12, 8), '歩く道の灯り  光度 4 未満の立ち位置 (前 → 後) と、灯りを置いた場所 (上から)', font=F, fill=(210, 210, 215))
for i, (jp, key, b, a, ab, aa, col) in enumerate(rows):
    x0 = 12 + i * 296
    pts = json.load(open(os.path.join(R, 'src/main/resources/data/skyabyss/%s_back/route_lamps.json' % key)))
    xs = [p[0] for p in pts]; zs = [p[2] for p in pts]
    mx, Mx, mz, Mz = min(xs), max(xs), min(zs), max(zs)
    s = min(270 / max(1, Mx - mx), 170 / max(1, Mz - mz))
    d.rectangle((x0, 40, x0 + 280, 220), fill=(24, 24, 28))
    for p in pts:
        px = x0 + 5 + (p[0] - mx) * s; pz = 45 + (p[2] - mz) * s
        d.ellipse((px - 2, pz - 2, px + 2, pz + 2), fill=col)
    d.text((x0, 226), '%s  灯り %d 個' % (jp, len(pts)), font=F, fill=(210, 210, 215))
    d.text((x0, 250), '暗い立ち位置  %d → %d' % (b, a), font=FS, fill=(170, 170, 175))
    d.text((x0, 270), '監査 LIGHT_DARK  %d → %d' % (ab, aa), font=FS, fill=(170, 170, 175))
d.text((12, 305), '潮殻=海のランタン・雷翼=銅の電球・余燼=茂み光 を壁に埋め込み。5 マス以上あけて影を残す。', font=FS, fill=(130, 130, 136))
im.save(os.path.join(R, 'docs/review/previews/lighting_sheet.png'))
