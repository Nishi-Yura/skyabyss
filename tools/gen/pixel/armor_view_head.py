# -*- coding: utf-8 -*-
"""兜を頭の高さで大きく 5 方向から描く（Blockbench に UV を貼る前に、塗った .bbmodel を確かめる）。
  python -X utf8 -I tools/gen/pixel/armor_view_head.py <塗った.bbmodel> <出力.png>
"""
import sys
sys.path.insert(0, 'tools/gen/pixel')
from armor_view import load_bb, render
from PIL import Image
src, out = sys.argv[1], sys.argv[2]
quads, atlas = load_bb(src)
views = [0, 35, 90, 145, 180]
S = 300
sheet = Image.new('RGB', (S * len(views), S), (24, 26, 34))
for c, yaw in enumerate(views):
    im = render(quads, atlas, yaw, S, scale=9.5, cy=58)
    sheet.paste(im, (c * S, 0))
sheet.save(out)
