#!/usr/bin/env python3
"""ASCII top-down slices of the tide turtle around the tower: '#' solid, '.' empty. Usage: python tools/misc/tide_turtle_map.py y [y ...]  (window x -16..16, z -18..12;
set TIDE_MAP_W=1 for a wider window x -14..14 z -16..10 printed without the circle hint)"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../lib')]
import sys

sys.path.insert(0, 'tools')
from tide_turtle_slices import load, solid  # noqa: E402

dims, pal, cols = load('src/main/resources/data/skyabyss/tide_back/turtle.bin')
for y in [int(a) for a in sys.argv[1:]]:
    print('y', y)
    for z in range(-18, 13):
        row = ''
        for x in range(-16, 17):
            s = solid(cols, pal, x, y, z)
            row += '#' if s else '.'
        print('%4d %s' % (z, row))
