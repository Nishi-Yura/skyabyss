"""python bybone.py a|b|c lod: 骨ごとの三角形数"""
import sys
import importlib
from common import *  # noqa

opt = importlib.import_module("opt_" + sys.argv[1]).OPT
lod = int(sys.argv[2])
lpkit.TEX = opt["tex"][lod]
faces = [f for f in opt["build"](lod) if f]
per = {}
for f in faces:
    per.setdefault(f.bone, {}).setdefault(f.mat, 0)
    per[f.bone][f.mat] += len(f.verts) - 2
print(tri_count(faces))
for b, d in per.items():
    print(b, sum(d.values()), d)
