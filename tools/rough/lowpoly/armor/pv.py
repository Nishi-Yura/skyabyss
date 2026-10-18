"""手早い確認: python pv.py <style a|b|c> out.png [rarity]"""
import importlib
import sys

from PIL import Image

import aview
aview.SS = 1
from common import TITANS

style, out = sys.argv[1], sys.argv[2]
rar = sys.argv[3] if len(sys.argv) > 3 else "common"
mod = importlib.import_module("style_" + style)
VIEWS = [(180, 6), (90, 6), (0, 6), (215, 16)]
W, H = 240, 330
im = Image.new("RGB", (W * len(VIEWS), H * len(TITANS)))
for r, T in enumerate(TITANS):
    res = aview.build_set(mod.build(T, rar), T, rar)
    print(T, {k: v["tris"] for k, v in res.items()}, aview.set_tris(res))
    for c, (yaw, pitch) in enumerate(VIEWS):
        im.paste(aview.render(res, T, yaw, pitch, (W, H), 7.4), (c * W, r * H))
im.save(out)
