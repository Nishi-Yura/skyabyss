#!/usr/bin/env python3
"""For each y: the largest radius R (around block (0,-3)) such that every cell with hypot<=R, plus a 1-cell margin on all sides (hypot<=R+1.5), is solid. Usage: python tools/misc/tide_turtle_radius.py [ymin ymax]"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../lib',)]
import sys

sys.path.insert(0, 'tools')
from tide_turtle_slices import load, solid  # noqa: E402

dims, pal, cols = load('src/main/resources/data/skyabyss/tide_back/turtle.bin')
y0, y1 = (int(a) for a in sys.argv[1:3]) if len(sys.argv) > 2 else (98, 176)
for y in range(y0, y1 + 1):
    best = 0.0
    r = 1.0
    while r < 14:
        ok = True
        for x in range(-16, 17):
            for z in range(-19, 13):
                d = (x * x + (z + 3) * (z + 3)) ** 0.5
                if d <= r + 1.5 and not solid(cols, pal, x, y, z):
                    # air within the margin: only fine if it is the interior we will carve (d <= r) - which we treat as solid shell? no: require solid
                    ok = False
                    break
            if not ok:
                break
        if not ok:
            break
        best = r
        r += 0.5
    print(y, 'R_max(all solid within R+1.5)=', best)
