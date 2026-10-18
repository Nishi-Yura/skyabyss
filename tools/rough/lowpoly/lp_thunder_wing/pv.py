"""手早い確認: python pv.py a|b|c out.png [lod] [save]"""
import sys, importlib, time
from common import *  # noqa

opt = importlib.import_module("opt_" + sys.argv[1]).OPT
SC = Path(sys.argv[2])
lod = int(sys.argv[3]) if len(sys.argv) > 3 else 0
t = time.time()
r = build_lod(opt, lod, OUTDIR / opt["id"], save=len(sys.argv) > 4)
print("tris", r["tris"], "atlas", r["size"], r["by_bone"], "build", round(time.time() - t, 1))
sc = 2.0 if lod == 0 else 1.6
views = [(35, 28, (0, 50, 0), 1.7, (900, 560)), (90, 4, (0, 52, -2), 2.9, (900, 330)), (205, -28, (0, 40, 0), 1.6, (900, 560)),
         (125, 6, (0, 54, -104), 10.0, (900, 560))]
tiles = []
for yaw, pitch, cen, scl, size in views:
    im = render(r["faces"], r["atlas"], r["glow"], yaw, pitch, size, cen, scl)
    bg = Image.fromarray(np.clip(voxel._background(*size, "dark"), 0, 255).astype(np.uint8)).convert("RGBA")
    bg.alpha_composite(im)
    tiles.append(bg.convert("RGB"))
W2 = Image.new("RGB", (1800, 560 + 330 + 560))
W2.paste(tiles[0], (0, 0)); W2.paste(tiles[2], (900, 0)); W2.paste(tiles[1], (0, 560)); W2.paste(tiles[3], (0, 890))
W2.save(SC)
print("render", round(time.time() - t, 1))
