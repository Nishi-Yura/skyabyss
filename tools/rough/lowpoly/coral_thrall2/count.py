import importlib
import sys
from collections import Counter

from common import *  # noqa

mod = importlib.import_module(f"opt_{sys.argv[1]}")
F, B, eyes = mod.build()
c = Counter()
m = Counter()
for f in F:
    c[f.bone] += len(f.verts) - 2
    m[f.mat] += len(f.verts) - 2
print("body", sum(c.values()), dict(c))
print(dict(m))
print("spear", tri_count(B))
if hasattr(mod, "statue_coral"):
    print("statue", tri_count(mod.statue_coral()))
