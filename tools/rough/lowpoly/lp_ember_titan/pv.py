"""手早い確認: python pv.py a|b|c out.png [lod] [save]"""
import sys, importlib, time
from common import *  # noqa

opt = importlib.import_module("opt_" + sys.argv[1]).OPT
SC = Path(sys.argv[2])
lod = int(sys.argv[3]) if len(sys.argv) > 3 else 0
t = time.time()
r = build_lod(opt, lod, OUTDIR / opt["id"], save=len(sys.argv) > 4)
print("tris", r["tris"], "atlas", r["size"], r["by_bone"], "build", round(time.time() - t, 1))
views = [(90, 5, (0, 70, 20), 1.7, (800, 400)), (140, 26, (0, 70, 20), 1.7, (800, 460)), (205, -26, (0, 60, 0), 1.6, (700, 380)),
         (90, 90, (0, 60, 20), 1.7, (800, 360)), (150, 8, (0, 20, -175), 8.0, (700, 420)), (180, 6, (0, 60, 0), 2.4, (500, 420))]
tiles = []
for yaw, pitch, cen, sc, size in views:
    im = render_ember(r["faces"], r["atlas"], r["glow"], yaw, pitch, size, cen, sc)
    bg = Image.fromarray(np.clip(voxel._background(*size, "dark"), 0, 255).astype(np.uint8)).convert("RGBA")
    bg.alpha_composite(im)
    tiles.append(bg.convert("RGB"))
Wd = 1600
y = 0
rows = [(0, 1), (2, 3), (4, 5)]
H = sum(max(tiles[a].size[1], tiles[b].size[1]) for a, b in rows)
W = Image.new("RGB", (Wd, H))
for a, b in rows:
    W.paste(tiles[a], (0, y)); W.paste(tiles[b], (tiles[a].size[0], y))
    y += max(tiles[a].size[1], tiles[b].size[1])
W.save(SC)
print("render", round(time.time() - t, 1))
