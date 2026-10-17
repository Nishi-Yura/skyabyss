"""Sanity test: cutting every box into sub-boxes WITHOUT removing anything must render identically to the original."""
import sys
sys.path.insert(0, "tools/rough")
import numpy as np
import hpkit
import voxel

for name in sys.argv[1:] or ["bone_sentinel"]:
    m = hpkit.Model.load(name)
    base = m.faces()
    img0 = voxel.render(base, yaw=200, pitch=20, size=(400, 400), ss=1, outline=False, bloom=False)
    for b in m.g["bones"]:
        new = []
        for c in b.get("cubes", []):
            new.extend(hpkit.round_cube(c, step=0.5, R=2, min_dim=1, _keep=True))
        if "cubes" in b:
            b["cubes"] = new
    n, q, mn, bn = hpkit.stats(m.geo)
    img1 = voxel.render(m.faces(), yaw=200, pitch=20, size=(400, 400), ss=1, outline=False, bloom=False)
    d = np.abs(np.array(img0).astype(int) - np.array(img1).astype(int))
    print(name, "cubes", n, "quads", q, "max diff", d.max(), "pixels differing", int((d.sum(2) > 0).sum()))
