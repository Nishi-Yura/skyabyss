"""手早い確認: python pv.py a|b|c out.png [save]"""
import sys, importlib
from common import *  # noqa

SC = Path(sys.argv[2])
opt = importlib.import_module("opt_" + sys.argv[1]).OPT
tiles = []
VIEWS = {1: (0, 17, 0.5, 14.0), 2: (0, 30, 0, 9.5), 3: (0, 20, 0, 9.5)}
for ph in (1, 2, 3):
    r = build_phase(opt, ph, OUTDIR / opt["id"], save=len(sys.argv) > 3)
    print(ph, r["tris"], r["size"], r["by_bone"])
    cx, cy, cz, sc = VIEWS[ph]
    for yaw, pitch, cen, s_ in ((200, 13, (cx, cy, cz), sc), (20, 13, (cx, cy, cz), sc), (190, 8, (0, 18, -4), 24)):
        im = lpkit.render(r["faces"], r["atlas"], r["glow"], yaw, pitch, (460, 460), cen, s_)
        bg = Image.fromarray(np.clip(voxel._background(460, 460, "dark"), 0, 255).astype(np.uint8)).convert("RGBA")
        bg.alpha_composite(im)
        tiles.append(bg.convert("RGB"))
W = Image.new("RGB", (460 * 3, 460 * 3))
for i, t in enumerate(tiles):
    W.paste(t, ((i % 3) * 460, (i // 3) * 460))
W.save(SC)
