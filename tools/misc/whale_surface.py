"""鯨の外の面の高さを表にする (胸びれ・尾の道の置き場所を決める)。python tools/misc/whale_surface.py"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../lib',)]
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from whale_bin import load

d = load()
s = d["solid"]
x0, y0, z0 = d["x0"], d["y0"], d["z0"]


def top(x, z):
    col = s[x - x0, :, z - z0]
    ys = np.nonzero(col)[0]
    return (int(ys.max()) + y0, int(ys.min()) + y0) if len(ys) else None


print("left fin (x rows, z cols): top,bottom")
zs = list(range(-110, -20, 10))
print("      " + " ".join("%9d" % z for z in zs))
for x in range(-170, -60, 10):
    print("%5d " % x + " ".join(("%4s,%-4s" % top(x, z)) if top(x, z) else "    -    " for z in zs))
print("tail (z rows at x -6..6)")
for z in range(250, 413, 10):
    print(z, [top(x, z) for x in (-30, -12, 0, 12, 30)])
