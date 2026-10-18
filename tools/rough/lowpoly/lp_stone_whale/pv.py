"""手早い確認: python pv.py a|b|c out.png [lod] [save]"""
import sys, importlib, time
from common import *  # noqa

opt = importlib.import_module("opt_" + sys.argv[1]).OPT
SC = Path(sys.argv[2])
lod = int(sys.argv[3]) if len(sys.argv) > 3 else 0
t = time.time()
r = build_lod(opt, lod, OUTDIR / opt["id"], save=len(sys.argv) > 4)
print("tris", r["tris"], "atlas", r["size"], r["by_bone"], "build", round(time.time() - t, 1))
views = [(90, 5, (0, 50, 10), 4.6, (1400, 420)), (200, -25, (0, 45, 0), 3.4, (700, 420)), (180, 90, (0, 45, 10), 3.4, (700, 420)),
         (165, 4, (0, 42, -105), 17.0, (700, 420))]
tiles = []
for yaw, pitch, cen, sc, size in views:
    im = render(r["faces"], r["atlas"], r["glow"], yaw, pitch, size, cen, sc)
    bg = Image.fromarray(np.clip(voxel._background(*size, "dark"), 0, 255).astype(np.uint8)).convert("RGBA")
    bg.alpha_composite(im)
    tiles.append(bg.convert("RGB"))
W = Image.new("RGB", (1400, 420 * 3))
W.paste(tiles[0], (0, 0))
W.paste(tiles[1], (0, 420)); W.paste(tiles[2], (700, 420))
W.paste(tiles[3], (0, 840))
W.save(SC)
print("render", round(time.time() - t, 1))
