"""python areas.py a|b|c [bone]: 骨ごとに素材別のチャート面積 (テクセル)"""
import sys
import importlib
from common import *  # noqa

opt = importlib.import_module("opt_" + sys.argv[1]).OPT
bone = sys.argv[2] if len(sys.argv) > 2 else "wing_l"
lpkit.TEX = 2.0
faces = [f for f in opt["build"](0) if f]
size, P, N, M, L = build_atlas(faces, 64, 0)
per = {}
for f in faces:
    if f.bone == bone:
        per[f.mat] = per.get(f.mat, 0) + f.chart[2] * f.chart[3]
print(per)
print(sorted([(f.chart[2] * f.chart[3], f.mat, f.bone) for f in faces])[-8:])
