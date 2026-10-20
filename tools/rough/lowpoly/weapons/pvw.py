"""確認: python pvw.py item_a item_b ... (3 階級 x 正面/3/4/横) → tools/_scratch/pvw.png"""
import importlib
import sys
from scene import *
from paint import paint

rows = []
for name in sys.argv[1:]:
    mod = importlib.import_module(name)
    cy = float(getattr(mod, "CY_V", 9))
    sc = float(getattr(mod, "SC_V", 13))
    tiles = []
    for rar in ("common", "rare", "legend"):
        faces = mod.faces(rar)
        faces, size, P, N, M, L = build(faces)
        atlas, glow = paint(faces, size, P, N, M, L, mod.TITAN, rar, 0, mod.inlay, mod.EDGE)
        print(name, rar, "tris", tri_count(faces))
        for yaw, pitch in ((180, 0), (150, -12), (90, 0)):
            im = view(faces, atlas, glow, mod.TITAN, yaw, pitch, (240, 330), (0, cy, 0), sc)
            bg = Image.new("RGBA", (240, 330), (40, 44, 52, 255))
            bg.alpha_composite(im)
            tiles.append(bg.convert("RGB"))
    W = Image.new("RGB", (9 * 240, 330))
    for i, t in enumerate(tiles):
        W.paste(t, (i * 240, 0))
    rows.append(W)
H = Image.new("RGB", (9 * 240, 330 * len(rows)))
for i, w in enumerate(rows):
    H.paste(w, (0, 330 * i))
H.save("../../../_scratch/pvw.png")
