import importlib
from common import *  # noqa

for o in "abc":
    opt = importlib.import_module("opt_" + o).OPT
    for lod in (0, 1, 2):
        r = build_lod(opt, lod, save=False)
        print(o, lod, r["tris"], r["size"], r["by_bone"], flush=True)
