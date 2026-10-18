"""手早い確認: python pv.py a|b|c out.png [lod] [save]"""
import sys, importlib, time
from tcommon import *  # noqa

opt = importlib.import_module("topt_" + sys.argv[1]).OPT
SC = Path(sys.argv[2])
lod = int(sys.argv[3]) if len(sys.argv) > 3 else 0
t = time.time()
r = build_lod(opt, lod, OUTDIR / opt["id"], save=len(sys.argv) > 4)
print("tris", r["tris"], "atlas", r["size"], r["by_bone"], "build", round(time.time() - t, 1))
views = [(90, 4, (0, 56, -20), 3.5, (1100, 400)), (205, -20, (0, 40, -10), 2.6, (700, 420)), (90, 90, (0, 50, -10), 3.0, (900, 620)),
         (125, 8, (0, 26, -140), 11.0, (700, 420)), (60, 15, (0, 40, 10), 2.4, (700, 420))]
tiles = []
for yaw, pitch, cen, sc, size in views:
    im = render(r["faces"], r["atlas"], r["glow"], yaw, pitch, size, cen, sc)
    bg = Image.fromarray(np.clip(voxel._background(*size, "dark"), 0, 255).astype(np.uint8)).convert("RGBA")
    bg.alpha_composite(im)
    tiles.append(bg.convert("RGB"))
W = Image.new("RGB", (1400, 400 + 420 + 620 + 420))
W.paste(tiles[0], (0, 0))
W.paste(tiles[1], (0, 400)); W.paste(tiles[4], (700, 400))
W.paste(tiles[2], (0, 820))
W.paste(tiles[3], (0, 1440))
W.save(SC)
print("render", round(time.time() - t, 1))
