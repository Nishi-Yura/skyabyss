"""手早い確認: python pv.py a|b|c out.png [save]"""
import sys, importlib
from common import *  # noqa

SC = Path(sys.argv[2])
opt = importlib.import_module("opt_" + sys.argv[1]).OPT
tiles = []
for ph in (1, 2, 3):
    r = build_phase(opt, ph, OUTDIR / opt["id"], save=len(sys.argv) > 3)
    print(ph, r["tris"], r["size"], r["by_bone"])
    for yaw, pitch, cen, sc in ((200, 13, (1, 15, 0), 9.5), (20, 13, (1, 15, 0), 9.5), (190, 8, (0, 15.5, -3), 22)):
        im = lpkit.render(r["faces"], r["atlas"], r["glow"], yaw, pitch, (420, 420), cen, sc)
        bg = Image.fromarray(np.clip(voxel._background(420, 420, "dark"), 0, 255).astype(np.uint8)).convert("RGBA")
        bg.alpha_composite(im)
        tiles.append(bg.convert("RGB"))
W = Image.new("RGB", (420 * 3, 420 * 3))
for i, t in enumerate(tiles):
    W.paste(t, ((i % 3) * 420, (i // 3) * 420))
W.save(SC)
