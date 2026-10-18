"""骨ごとの箱の占有を正面 (x-y) と上 (x-z) で色分けして見る: python occ.py out.png"""
import json, sys
from pathlib import Path
from PIL import Image, ImageDraw
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
GEO = ROOT / "src/main/resources/assets/skyabyss/geo/entity"
COL = {}
pal = [(255, 90, 90), (90, 255, 120), (90, 160, 255), (255, 220, 60), (255, 120, 255), (60, 240, 240), (255, 160, 60), (200, 200, 200),
       (160, 120, 255), (120, 255, 200), (255, 120, 160), (180, 255, 90), (255, 255, 255)]
S = 14
img = Image.new("RGB", (3 * 2 * 52 * S // 2, 52 * S // 2 * 1 + 10), (10, 10, 20))
W, H = 56, 56
img = Image.new("RGB", (W * S * 2, H * S * 3), (10, 10, 20))
d = ImageDraw.Draw(img)
for ph in (1, 2, 3):
    g = json.load(open(GEO / f"storm_heart_p{ph}.geo.json"))["minecraft:geometry"][0]["bones"]
    oy = (ph - 1) * H * S
    for bi, b in enumerate(g):
        c = pal[bi % len(pal)]
        for cu in b.get("cubes", []):
            o, s = cu["origin"], cu["size"]
            # front: x right, y up. x in [-28,28], y in [0,56]
            x0 = (o[0] + 28) * S
            y1 = oy + (56 - o[1]) * S
            d.rectangle([x0, y1 - s[1] * S, x0 + s[0] * S, y1], outline=c)
            # top view: x, z (front=-z at bottom)
            xx = W * S + (o[0] + 28) * S
            zz = oy + (o[2] + 28) * S
            d.rectangle([xx, zz, xx + s[0] * S, zz + s[2] * S], outline=c)
    d.text((4, oy + 4), f"phase {ph}", fill=(255, 255, 255))
    for bi, b in enumerate(g):
        d.text((4, oy + 20 + bi * 12), f"{b['name']} ({len(b.get('cubes', []))})", fill=pal[bi % len(pal)])
img.save(sys.argv[1])
