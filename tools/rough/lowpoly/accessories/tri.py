"""A 案の面数を階級ごとに数える: python tri.py [thunder ember whale tide]"""
import sys
import importlib
from common import *

for name in (sys.argv[1:] or ["thunder", "ember"]):
    m = importlib.import_module(name)
    for k, it in m.ITEMS.items():
        row = []
        for r in ("common", "rare", "legend"):
            fs = it["fn"]["a"](r)
            fs, *_ = build(fs)
            row.append(tri_count(fs))
        print(k, it["kind"], it["rar"], row)
