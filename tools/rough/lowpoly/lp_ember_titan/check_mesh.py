"""出力したメッシュ JSON の確認: 骨の名前・階層・pivot が各 LOD の今の geo と同じか、uv・添字・左右対称。
python tools/rough/lowpoly/lp_ember_titan/check_mesh.py"""
import json

import numpy as np

from common import *  # noqa

LODN = ("near", "mid", "far")
ok = True
for o in "abc":
    for lod in range(3):
        d = json.loads((OUTDIR / o / f"{LODN[lod]}_mesh.json").read_text(encoding="utf-8"))
        g = bone_defs(GEOS[lod])
        bones = d["bones"]
        if set(bones) != set(g):
            print("BONES DIFFER", o, lod, set(bones) ^ set(g))
            ok = False
        tot = 0
        vmin, vmax = np.full(3, 1e9), np.full(3, -1e9)
        asym = 0.0
        for name, (parent, pivot, rot) in g.items():
            b = bones[name]
            if b["parent"] != parent or [float(x) for x in b["pivot"]] != pivot or [float(x) for x in b.get("rotation", [0, 0, 0])] != rot:
                print("BONE MISMATCH", o, lod, name, b["parent"], b["pivot"], parent, pivot)
                ok = False
            v = np.array(b["vertices"]) if b["vertices"] else np.zeros((0, 3))
            uv = np.array(b["uvs"]) if b["uvs"] else np.zeros((0, 2))
            t = np.array(b["triangles"]) if b["triangles"] else np.zeros((0, 3), int)
            if len(v):
                assert uv.min() >= 0 and uv.max() <= 1, (o, lod, name, uv.min(), uv.max())
                assert len(uv) == len(v) and t.max() < len(v) and t.min() >= 0
                vmin, vmax = np.minimum(vmin, v.min(0)), np.maximum(vmax, v.max(0))
            tot += len(t)
            assert len(t) == b["triangle_count"]
        assert tot == d["triangle_count"]
print("OK" if ok else "PROBLEMS")

# 左右対称 (近景の面の頂点を x 反転して、いちばん近い頂点までの距離)。尾・火山・煙・煉瓦・銅は除く (尾は今と同じく -X へ曲がる。火山は多角形の輪)
import importlib
from scipy.spatial import cKDTree
for o in "abc":
    opt = importlib.import_module("opt_" + o).OPT
    lpkit.TEX = 2.0
    fs = opt["build"](0)
    skip_m = {"cone", "rimcrust", "lava", "brick", "copper", "ruinglow", "smoke"}
    pts = np.array([p for f in fs if f.bone not in ("tail",) and not f.bone.startswith("smoke") and f.mat not in skip_m
                    and not (np.hypot(f.verts[:, 0], f.verts[:, 2] + 46.0).min() < 62 and f.verts[:, 1].mean() > 56) for p in f.verts])
    mir = pts * np.array([-1, 1, 1])
    dd, _ = cKDTree(pts).query(mir)
    print(o, "mirror: max", round(float(dd.max()), 3), "p99", round(float(np.percentile(dd, 99)), 3), "n", len(pts))
