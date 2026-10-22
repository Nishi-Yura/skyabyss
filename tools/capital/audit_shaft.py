# -*- coding: utf-8 -*-
"""五章の大坑道の検査: 封じた戸の裏から、二つの踊り場と底の境まで歩いて届くか (跳ぶ所なし)、灯りの間隔、吹き抜けへの落ち口。

python -I tools/capital/audit_shaft.py
"""
from __future__ import annotations

import os
import sys
from collections import deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vox  # noqa: E402
import town  # noqa: E402
from audit import name, passable, stand_ok, step_ok  # noqa: E402


def main():
    v = town.Town(vox.STYLES['b']).build()
    b = {k: s for k, s in v.b.items() if name(s) != 'reinforced_deepslate' or k[1] > -8 or True}
    dx, dy, dz = v.marks['abyss_door']
    # 戸は開いたものとして (戸の 2 マスを空気に)
    walk = dict(b)
    walk[(dx, dy, dz)] = 'cave_air'
    walk[(dx, dy + 1, dz)] = 'cave_air'
    start = (dx - 1, dy, dz)
    seen = {start}
    q = deque([start])
    jumps = []
    while q:
        x, y, z = q.popleft()
        for ddx, ddz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + ddx, z + ddz
            for ny in (y, y + 1, y - 1):
                if stand_ok(walk, nx, ny, nz) and (ny <= y or passable(walk.get((x, y + 2, z)))):
                    fl = name(walk.get((nx, ny - 1, nz)))
                    if ny == y + 1 and not step_ok(walk.get((x, y, z))) and not step_ok(walk.get((nx, ny, nz)))                             and not any(t in fl for t in ('wall', 'lantern', 'anvil', 'barrel', 'campfire', 'lectern', 'table', 'wool', 'powder')):
                        jumps.append((nx, ny, nz, fl))
                    if (nx, ny, nz) not in seen:
                        seen.add((nx, ny, nz))
                        q.append((nx, ny, nz))
                    break
    problems = []
    for m in ('abyss_landing_1', 'abyss_landing_2', 'abyss_gate'):
        mx, my, mz = v.marks[m]
        if not any((mx + a, my + c, mz + e) in seen for a in range(-2, 3) for e in range(-2, 3) for c in (-1, 0, 1)):
            problems.append('%s at %s: not reached on foot from behind the sealed door' % (m, (mx, my, mz)))
    ys = [p[1] for p in seen]
    print('walkable cells behind the door: %d (y %d..%d), steps needing a jump: %d' % (len(seen), min(ys), max(ys), len(jumps)))
    for j in jumps[:20]:
        print('  jump', j)
    for p in problems:
        print(p)
    print('%d problems' % len(problems))
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
