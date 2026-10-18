"""拡大確認: python pz.py <style> <titan> out.png [rarity]  (正面・3/4・背・横を大きく)"""
import importlib
import sys

from PIL import Image

import aview

style, T, out = sys.argv[1], sys.argv[2], sys.argv[3]
rar = sys.argv[4] if len(sys.argv) > 4 else "common"
mod = importlib.import_module("style_" + style)
res = aview.build_set(mod.build(T, rar), T, rar)
print(T, {k: v["tris"] for k, v in res.items()}, aview.set_tris(res))
VIEWS = [(180, 6), (215, 16), (0, 6), (90, 6)]
W, H = 400, 560
im = Image.new("RGB", (W * len(VIEWS), H))
for c, (yaw, pitch) in enumerate(VIEWS):
    im.paste(aview.render(res, T, yaw, pitch, (W, H), 12.5), (c * W, 0))
im.save(out)
