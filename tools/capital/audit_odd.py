# -*- coding: utf-8 -*-
"""都の設計図の「おかしな所」の検査 (audit.py の続き。見た目の違和感を拾う)。

python -I tools/capital/audit_odd.py [作風]
- 浮いた塊: まわり 6 方向がすべて空気のブロック (飾りの鎖・灯り・旗を除く)
- 支えのない付き物: 壁の松明・壁の旗・壁の札・はしご・吊るした灯り・ボタン・床の灯り
- 跳ぶ所: 南門から歩いて広がる外の通り (空が見える所) で、階段や半ブロックなしに丸ごと 1 段上がる所
- 外の段の行き止まり: 空の見える所の、床に置いた階段で、低い側に立てるのに高い側が壁で上がれないもの (またはその逆)
一覧を出す。数だけ見るときは -q
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vox  # noqa: E402
import town  # noqa: E402
from audit import name, passable, stand_ok, step_ok  # noqa: E402
from collections import deque

DX = vox.DX
OPP = vox.OPP
AIRS = ('air', 'cave_air', 'light')
FREE = ('chain', 'lantern', 'banner', 'end_rod', 'torch', 'candle', 'flower_pot', 'skull', 'head', 'bell')


def prop(s, k):
    if not s or (k + '=') not in s:
        return None
    return s.split(k + '=')[1].split(',')[0].split(']')[0]


def solid(s):
    return s is not None and name(s) not in AIRS and not passable(s)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('-')]
    quiet = '-q' in sys.argv
    key = args[0] if args else 'b'
    v = town.Town(vox.STYLES[key]).build()
    b = v.b
    top = {}
    for (x, y, z), s in b.items():
        if name(s) not in AIRS:
            if (x, z) not in top or y > top[(x, z)]:
                top[(x, z)] = y
    out = {'floating': [], 'unsupported': [], 'dead step': []}
    for (x, y, z), s in b.items():
        n = name(s)
        if n in AIRS:
            continue
        nb = [b.get((x + dx, y + dy, z + dz)) for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))]
        if y > 0 and all(t is None or name(t) in AIRS for t in nb) and not any(f in n for f in FREE):
            out['floating'].append(((x, y, z), n))
        # 付き物
        need = None
        if n in ('wall_torch', 'soul_wall_torch') or n.endswith('_wall_banner') or n.endswith('_wall_sign') or n == 'ladder' or n.endswith('_wall_hanging_sign'):
            f = prop(s, 'facing')
            if f:
                dx, dz = DX[OPP[f]]
                need = (x + dx, y, z + dz)
        elif n in ('lantern', 'soul_lantern'):
            need = (x, y + 1, z) if prop(s, 'hanging') == 'true' else (x, y - 1, z)
        elif n in ('torch', 'soul_torch') or n.endswith('_carpet') or n.endswith('_pressure_plate') or n == 'candle' or n.endswith('_candle'):
            need = (x, y - 1, z)
        elif n.endswith('_hanging_sign'):
            need = (x, y + 1, z)
        if n.endswith('_wall_hanging_sign'):
            # 壁の吊り札は、向きの横 (左右) のどちらかに支えがあればよい
            f = prop(s, 'facing')
            ax, az = DX[f]
            sides = [(x + az, y, z + ax), (x - az, y, z - ax)]
            need = None if any(b.get(p) is not None and name(b.get(p)) not in AIRS for p in sides) else sides[0]
        if need is not None and not n.endswith('_hanging_sign') or (n.endswith('_wall_hanging_sign') and need is not None):
            t = b.get(need)
            if t is None or name(t) in AIRS:
                out['unsupported'].append(((x, y, z), n))
        # 外の段
        if n.endswith('_stairs') and prop(s, 'half') == 'bottom' and solid(b.get((x, y - 1, z))) and top.get((x, z), y) <= y + 0:
            f = prop(s, 'facing')
            hx, hz = DX[f]
            lx, lz = DX[OPP[f]]
            low_ok = stand_ok(b, x + lx, y, z + lz)
            high_ok = stand_ok(b, x + hx, y + 1, z + hz) or (passable(b.get((x + hx, y + 1, z + hz))) and passable(b.get((x + hx, y + 2, z + hz))) and solid(b.get((x + hx, y, z + hz))))
            head = passable(b.get((x, y + 1, z))) and passable(b.get((x, y + 2, z)))
            if head and low_ok != high_ok and y <= 8:
                out['dead step'].append(((x, y, z), n + (' (上が壁)' if low_ok else ' (下が壁)')))
    # 跳ぶ所
    out['jump'] = []
    sx, sy, sz = v.marks['stand']
    seen = {(sx, sy, sz)}
    q = deque([(sx, sy, sz)])
    walk = {k: s_ for k, s_ in b.items() if name(s_) not in ('iron_bars', 'iron_door')}
    jumps = set()
    while q:
        x, y, z = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (-140 <= nx <= 120 and -120 <= nz <= 120):
                continue
            for ny in (y, y + 1, y - 1):
                if stand_ok(walk, nx, ny, nz) and (ny <= y or passable(walk.get((x, y + 2, z)))):
                    if ny == y + 1 and not step_ok(walk.get((x, y, z))) and not step_ok(walk.get((nx, ny, nz)))                             and top.get((x, z), -99) < y and top.get((nx, nz), -99) < ny + 2:
                        fl = name(walk.get((nx, ny - 1, nz)))
                        if not any(t in fl for t in ('wall', 'fence', 'hay', 'barrel', 'cauldron', 'composter', 'trapdoor', 'tablet',
                                                     'chest', 'bed', 'table', 'lectern', 'anvil', 'leaves', 'log', 'wood', 'pot',
                                                     'campfire', 'bookshelf', 'loom', 'smoker', 'furnace', 'grindstone', 'stonecutter', 'bell')):
                            jumps.add(((nx, ny, nz), fl))
                    if (nx, ny, nz) not in seen:
                        seen.add((nx, ny, nz))
                        q.append((nx, ny, nz))
                    break
    out['jump'] = sorted(jumps)
    for k, lst in out.items():
        print('%s: %d' % (k, len(lst)))
        if not quiet:
            for p, n in sorted(lst)[:400]:
                print('  ', p, n)


if __name__ == '__main__':
    main()
