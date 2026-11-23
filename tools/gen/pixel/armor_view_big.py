# -*- coding: utf-8 -*-
"""防具の一式を大きく 4 方向（前・斜め前・横・後ろ）と歩く姿で描く（塗った .bbmodel の下見）。
  python -X utf8 -I tools/gen/pixel/armor_view_big.py <出力.png> <a.bbmodel> [<b.bbmodel> ...]
複数渡すと行を重ねる（一覧用）。
"""
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from armor_view import load_bb, render  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

out, srcs = sys.argv[1], sys.argv[2:]
views = [(0, 'stand'), (35, 'stand'), (90, 'stand'), (150, 'stand'), (180, 'stand')]
S = 360
sheet = Image.new('RGB', (S * len(views), S * len(srcs)), (24, 28, 36))
dr = ImageDraw.Draw(sheet)
for r, src in enumerate(srcs):
    quads, atlas = load_bb(src)
    for c, (yaw, pose) in enumerate(views):
        im = render(quads, atlas, yaw, S, scale=float(os.environ.get('AV_SCALE', 4.4)), pose=pose, cy=float(os.environ.get('AV_CY', 34)))
        sheet.paste(im, (c * S, r * S))
    dr.text((6, r * S + 4), Path(src).stem, fill=(220, 220, 220))
sheet.save(out)
