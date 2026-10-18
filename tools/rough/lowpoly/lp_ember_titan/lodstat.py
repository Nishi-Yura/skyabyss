"""三角形の数と画像の大きさだけを見る: python lodstat.py [a b c]"""
import importlib
import sys

from common import *  # noqa

for o in (sys.argv[1:] or "abc"):
    opt = importlib.import_module("opt_" + o).OPT
    for lod in (0, 1, 2):
        r = build_lod(opt, lod, save=False)
        print(o, lod, r["tris"], r["size"], r["by_bone"], flush=True)
