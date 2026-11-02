"""敵の形の見直し用: 一体ずつ 前 3/4・横・後ろ 3/4・予告の姿勢を横に並べる。
実行: python look.py <out.png> <モジュール ...>"""
import importlib
import sys

from PIL import Image, ImageDraw

import fk  # noqa: F401  (光る材質の塗りを差し込む)
import ck

out = sys.argv[1]
mods = sys.argv[2:]
PW, PH = 300, 420
rows = []
for name in mods:
    m = importlib.import_module(name)
    faces, eyes = m.build("a")
    size, P, N, M, L = ck.build_atlas(faces, 128, 0)
    atlas, glow = ck.paint(faces, size, P, N, M, m.PAL, eyes)
    r = dict(faces=faces)
    tele = "telegraph" if "telegraph" in m.POSES else "idle"
    cen = m.SPEC["rows"][0][5]
    sc = m.SPEC["rows"][0][6] * 0.62
    ims = []
    for pose, yaw, pit in (("idle", 210, 10), ("idle", 270, 6), ("idle", 30, 10), (tele, 210, 8)):
        ims.append(ck.render_view(r, atlas, glow, m.POSES[pose], m.BONES, yaw, pit, (PW, PH), cen, sc))
    row = Image.new("RGB", (PW * 4, PH + 24), (16, 16, 22))
    for i, im in enumerate(ims):
        row.paste(im, (i * PW, 0))
    ImageDraw.Draw(row).text((6, PH + 4), f"{m.SPEC['short']}  {name}  {ck.tri_count(faces)} 三角形", fill=(230, 222, 200), font=ck.voxel.font(15))
    rows.append(row)
S = Image.new("RGB", (PW * 4, sum(r.height for r in rows)))
y = 0
for r in rows:
    S.paste(r, (0, y))
    y += r.height
S.save(out)
print("saved", out)
