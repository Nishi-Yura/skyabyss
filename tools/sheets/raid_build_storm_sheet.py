# 雷翼のレイドの部屋の建築の前と後の台紙 (tools/shots/raid_build_storm.txt を前 / 後の名前で二度撮ったもの)。
#   python -I tools/sheets/raid_build_storm_sheet.py [前のフォルダ] [後のフォルダ]
#   既定: run/shots/raid_build_storm_before/shots/raid_build_storm と run/shots/raid_build_storm_after/shots/raid_build_storm
#   出力: docs/review/raid_build_storm/<部屋>.jpg (左 = 前、右 = 後。行ごとに一つの視点)
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
before = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'run/shots/raid_build_storm_before/shots/raid_build_storm')
after = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'run/shots/raid_build_storm_after/shots/raid_build_storm')
out = os.path.join(ROOT, 'docs/review/raid_build_storm')
os.makedirs(out, exist_ok=True)
W, H = 560, 315
names = sorted(n[:-4] for n in os.listdir(after) if n.endswith('.png'))
rooms = sorted({n.split('_')[0] for n in names})
for room in rooms:
    rows = [n for n in names if n.split('_')[0] == room]
    sheet = Image.new('RGB', (2 * W + 30, len(rows) * (H + 24) + 10), (24, 24, 28))
    dr = ImageDraw.Draw(sheet)
    for i, n in enumerate(rows):
        y = 10 + i * (H + 24)
        for k, folder in enumerate((before, after)):
            p = os.path.join(folder, n + '.png')
            if os.path.exists(p):
                im = Image.open(p).convert('RGB').resize((W, H), Image.LANCZOS)
                sheet.paste(im, (10 + k * (W + 10), y + 16))
        dr.text((12, y), n + '   before | after', fill=(230, 230, 230))
    sheet.save(os.path.join(out, room + '.jpg'), quality=82)
print('wrote', len(rooms), 'sheets to', out)
