# -*- coding: utf-8 -*-
"""深淵の層の大縦穴の検査: 着く所から、休み場・各層の部屋・心臓まで歩いて届くか (跳ぶ所の数も)。

python -I tools/abyss/audit_layers.py
"""
from __future__ import annotations

import os
import sys
from collections import deque

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'capital'))
import gen_abyss_layers as g  # noqa: E402
import vox  # noqa: E402
from audit import name, passable, stand_ok, step_ok  # noqa: E402


def build():
    captured = {}
    real_save = vox.save
    vox.save = lambda path, data: captured.setdefault('d', data)
    try:
        g.main()
    finally:
        vox.save = real_save
    return captured['d']


def main():
    data = build()
    pal = data['palette']
    b = {}
    for r in data['runs']:
        y, z, x = r[0], r[1], r[2]
        for i in range(3, len(r) - 1, 2):
            idx, n = r[i], r[i + 1]
            for k in range(n):
                if idx >= 0:
                    b[(x + k, y, z)] = pal[idx].replace('minecraft:', '')
            x += n
    start = tuple(data['marks']['arrival'])
    seen = {start}
    q = deque([start])
    jumps = []
    while q:
        x, y, z = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            for ny in (y, y + 1, y - 1):
                if stand_ok(b, nx, ny, nz) and (ny <= y or passable(b.get((x, y + 2, z)))):
                    fl = name(b.get((nx, ny - 1, nz)))
                    if ny == y + 1 and not step_ok(b.get((x, y, z))) and not step_ok(b.get((nx, ny, nz))) and 'wall' not in fl and 'lantern' not in fl:
                        jumps.append((nx, ny, nz, fl, name(b.get((x, y - 1, z)))))
                    if (nx, ny, nz) not in seen:
                        seen.add((nx, ny, nz))
                        q.append((nx, ny, nz))
                    break
    probs = []
    for m, (mx, my, mz) in sorted(data['marks'].items()):
        if m.startswith('beacon_'):
            continue
        if not any((mx + a, my + c, mz + e) in seen for a in range(-3, 4) for e in range(-3, 4) for c in (-1, 0, 1)):
            probs.append('%s at %s: not reached on foot from the arrival' % (m, (mx, my, mz)))
    ys = [p[1] for p in seen]
    print('walkable cells: %d (y %d..%d), jumps: %d' % (len(seen), min(ys), max(ys), len(jumps)))
    import collections
    print(collections.Counter((j[3], j[4]) for j in jumps).most_common(8))
    for j in jumps[:6]:
        print('  jump', j)
    for p in probs:
        print(p)
    print('%d problems' % len(probs))


if __name__ == '__main__':
    main()
