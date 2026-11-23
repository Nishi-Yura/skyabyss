# -*- coding: utf-8 -*-
"""ゲームで撮った防具の絵を、行ごとに並べて 1 枚にする（見せる用。前の組と見比べる）。
  python -X utf8 -I tools/gen/pixel/armor_sheet.py <出力.png> "<行の見出し>=<撮影の絵のフォルダ>:set|player" ...
set = tools/shots/armor_set_close.txt の 5 方向、player = armor_player_check.txt の着た姿。
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]
FONT = ImageFont.truetype(str(ROOT / 'tools/sources/fonts/DotGothic16-Regular.ttf'), 22)
SETS = ['set_front', 'set_q34', 'set_side', 'set_q34back', 'set_back']
PLAYER = ['p_idle', 'p_back', 'p_attack', 'p_crouch', 'p_walk']
H = 430


def row(src, kind):
    names = SETS if kind == 'set' else PLAYER
    ims = []
    for n in names:
        p = Path(src) / (n + '.png')
        if not p.exists():
            continue
        im = Image.open(p).convert('RGB')
        w, h = im.size
        im = im.crop((int(w * 0.31), int(h * 0.04), int(w * 0.69), int(h * 0.96)))
        ims.append(im.resize((int(im.width * H / im.height), H), Image.LANCZOS))
    r = Image.new('RGB', (sum(i.width for i in ims), H))
    x = 0
    for i in ims:
        r.paste(i, (x, 0))
        x += i.width
    return r


out = sys.argv[1]
rows = []
for a in sys.argv[2:]:
    label, rest = a.split('=', 1)
    src, kind = rest.rsplit(':', 1) if rest.endswith((':set', ':player')) else (rest, 'set')
    rows.append((label, row(src, kind)))
W = max(r.width for _, r in rows)
sheet = Image.new('RGB', (W, sum(r.height + 34 for _, r in rows)), (22, 26, 34))
dr = ImageDraw.Draw(sheet)
y = 0
for label, r in rows:
    dr.text((8, y + 5), label, fill=(232, 232, 232), font=FONT)
    sheet.paste(r, (0, y + 34))
    y += r.height + 34
sheet.save(out)
print(sheet.size)
