"""python lodstat.py [a b c]: 三角形数・アトラス・チャート面積の合計 (テクセル) を表示"""
import sys
import importlib
from common import *  # noqa

for o in (sys.argv[1:] or "abc"):
    opt = importlib.import_module("opt_" + o).OPT
    for lod in (0, 1, 2):
        lpkit.TEX = opt["tex"][lod]
        faces = [f for f in opt["build"](lod) if f is not None]
        size, P, N, M, L = build_atlas(faces, 64, 0)
        area = sum(f.chart[2] * f.chart[3] for f in faces)
        per = {}
        for f in faces:
            per[f.bone] = per.get(f.bone, 0) + f.chart[2] * f.chart[3]
        print(o, lod, tri_count(faces), "faces", len(faces), "atlas", size, "area", area, {k: v // 1000 for k, v in per.items()}, flush=True)
