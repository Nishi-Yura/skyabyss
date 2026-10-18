"""python shot.py opt lod out.png yaw pitch cx cy cz scale w h"""
import sys, importlib
from tcommon import *  # noqa
opt = importlib.import_module("topt_" + sys.argv[1]).OPT
lod = int(sys.argv[2])
r = build_lod(opt, lod, save=False)
print("tris", r["tris"], r["by_bone"])
a = sys.argv[4:]
yaw, pitch, cx, cy, cz, sc, w, h = [float(x) for x in a]
im = render(r["faces"], r["atlas"], r["glow"], yaw, pitch, (int(w), int(h)), (cx, cy, cz), sc)
bg = Image.fromarray(np.clip(voxel._background(int(w), int(h), "dark"), 0, 255).astype(np.uint8)).convert("RGBA")
bg.alpha_composite(im)
bg.convert("RGB").save(sys.argv[3])
