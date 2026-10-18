"""アトラスの詰まり具合を見る: python atlasdbg.py b 1"""
import importlib
import sys

from common import *  # noqa

opt = importlib.import_module("opt_" + sys.argv[1]).OPT
lod = int(sys.argv[2])
lpkit.TEX = opt["tex"][lod]
fs = opt["build"](lod)
size, P, N, M, L = build_atlas2(fs, 256, 0)
area = sum(f.chart[2] * f.chart[3] for f in fs)
print("size", size, "area", area, area / size / size, "faces", len(fs))
big = sorted(fs, key=lambda f: -f.chart[2] * f.chart[3])[:8]
for f in big:
    print(f.bone, f.mat, f.chart)
wid = sorted(fs, key=lambda f: -f.chart[2])[:5]
for f in wid:
    print("wide", f.bone, f.mat, f.chart)
