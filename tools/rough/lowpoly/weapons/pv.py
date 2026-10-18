"""手早い確認: python pv.py <weapon> out.png [rarity] [plus]"""
import importlib
import sys
from scene import *
from paint import paint

weapon, out = sys.argv[1], sys.argv[2]
rarity = sys.argv[3] if len(sys.argv) > 3 else "common"
plus = int(sys.argv[4]) if len(sys.argv) > 4 else 0
mod = importlib.import_module("d_" + weapon)
tiles = []
for o in "abc":
    fn, inlay, em = mod.OPTS[o]
    faces = fn(rarity)
    faces, size, P, N, M, L = build(faces)
    atlas, glow = paint(faces, size, P, N, M, L, mod.TITAN, rarity, plus, inlay, em)
    print(o, "tris", tri_count(faces), "atlas", size)
    for yaw, pitch in ((180, 0), (145, -12)):
        im = view(faces, atlas, glow, mod.TITAN, yaw, pitch, (360, 420), (0, 8, 0), 15)
        bg = Image.new("RGBA", (360, 420), (40, 44, 52, 255))
        bg.alpha_composite(im)
        tiles.append(bg.convert("RGB"))
W = Image.new("RGB", (3 * 360, 2 * 420))
for i, t in enumerate(tiles):
    W.paste(t, ((i // 2) * 360, (i % 2) * 420))
W.save(out)
