"""弓の確認: python pvb.py item_xxx out.png [stage] [plus]"""
import importlib
import sys
from scene import *
from paint import paint

mod = importlib.import_module(sys.argv[1])
out = sys.argv[2]
stage = int(sys.argv[3]) if len(sys.argv) > 3 else 0
plus = int(sys.argv[4]) if len(sys.argv) > 4 else 0
tiles = []
for rar in ("common", "rare", "legend"):
    faces = mod.faces(rar, stage)
    faces, size, P, N, M, L = build(faces)
    atlas, glow = paint(faces, size, P, N, M, L, mod.TITAN, rar, plus, mod.inlay, mod.EDGE)
    print(rar, "tris", tri_count(faces), "atlas", size)
    for yaw, pitch in ((180, 0), (150, -12), (90, 0)):
        im = view(faces, atlas, glow, mod.TITAN, yaw, pitch, (300, 400), (-1, 8, 0), 14)
        bg = Image.new("RGBA", (300, 400), (40, 44, 52, 255))
        bg.alpha_composite(im)
        tiles.append(bg.convert("RGB"))
W = Image.new("RGB", (9 * 300, 400))
for i, t in enumerate(tiles):
    W.paste(t, (i * 300, 0))
W.save(out)
