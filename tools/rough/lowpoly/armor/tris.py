"""三角形の数だけ確認: python tris.py <style> [titan]"""
import sys

import importlib

import aview
from common import TITANS

style = sys.argv[1]
mod = importlib.import_module("style_" + style)
for T in (sys.argv[2:] or TITANS):
    for r in ("common", "rare", "legend"):
        res = aview.build_set(mod.build(T, r), T, r)
        print(style, T, r, {k: v["tris"] for k, v in res.items()}, aview.set_tris(res))
