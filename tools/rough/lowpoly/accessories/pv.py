"""手早い確認: python pv.py thunder|ember out.png [rarity]"""
import importlib, sys
from common import *

mod = importlib.import_module(sys.argv[1])
out = sys.argv[2]
rar = sys.argv[3] if len(sys.argv) > 3 else "common"
items = list(mod.ITEMS.items())
T = 250
W = Image.new("RGB", (3 * 2 * T, len(items) * T), (40, 44, 52))
for row, (iid, it) in enumerate(items):
    for ci, o in enumerate("abc"):
        r = make(it["fn"][o], mod.TITAN, rar)
        print(iid, o, r["tris"])
        for k, (yaw, pitch) in enumerate(((180, 0), (150, -22))):
            im = view(r["faces"], r["atlas"], r["glow"], mod.TITAN, yaw, pitch, (T, T), (0, 0, 0), 13)
            bg = Image.new("RGBA", (T, T), (30, 34, 42, 255)); bg.alpha_composite(im)
            W.paste(bg.convert("RGB"), ((ci * 2 + k) * T, row * T))
W.save(out)
