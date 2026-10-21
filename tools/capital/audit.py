# -*- coding: utf-8 -*-
"""都の設計図の検査 (建てたあとの形を、置く前に調べる)。

python -I tools/capital/audit.py [作風]
- 戸 (本物の戸と、入れない家の跳ね上げ戸の戸): 前の 2 ブロック (足と頭) が空いていて、その先の地面が歩けるか
- 門の口・鐘楼のアーチ: 通り抜けられるか
- 店先・露店・碑: 前に立てるか (碑は読めるように、前が空いているか)
- 印 (語り部・鐘・イサナ・村人・stand): 立つ所に体が入るか
問題があれば一覧を出して終わりの番号 1。
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vox  # noqa: E402
import town  # noqa: E402

DX = vox.DX
OPP = vox.OPP

SOFT = ('carpet', 'flower', 'tulip', 'poppy', 'daisy', 'bluet', 'allium', 'cornflower', 'lily_of', 'dandelion',
        'short_grass', 'fern', 'pressure_plate', 'snow[', 'rail', 'torch', 'button', 'wall_banner', 'vine')


def name(s):
    if s is None:
        return 'air'
    return s.split('[')[0].replace('minecraft:', '')


def passable(s):
    n = name(s)
    if n == 'air' or n == 'cave_air' or n == 'light':
        return True
    if any(t in n for t in SOFT):
        return True
    if n.endswith('_door') and 'trap' not in n:
        return True
    return False


def step_ok(s):
    """足もとに置いて越えられる (半ブロック・階段)"""
    n = name(s)
    return n.endswith('_slab') and 'type=bottom' in (s or '') or n.endswith('_stairs')


def solid_floor(s):
    n = name(s)
    return n != 'air' and not passable(s) or n.endswith('_slab') or n.endswith('_stairs')


def stand_ok(b, x, y, z):
    """(x, y, z) に人が立てるか: 足と頭が空いていて、下が床"""
    if not passable(b.get((x, y, z))) or not passable(b.get((x, y + 1, z))):
        if step_ok(b.get((x, y, z))) and passable(b.get((x, y + 1, z))) and passable(b.get((x, y + 2, z))):
            return True
        return False
    below = b.get((x, y - 1, z))
    return below is not None and below != 'air'


def main():
    key = sys.argv[1] if len(sys.argv) > 1 else 'b'
    t = town.Town(vox.STYLES[key])
    v = t.build()
    b = v.b
    problems = []
    doors = 0
    for (x, y, z), s in list(b.items()):
        n = name(s)
        if n.endswith('_door') and 'trapdoor' not in n and 'half=lower' in s:
            doors += 1
            f = s.split('facing=')[1].split(',')[0].split(']')[0]
            out = OPP[f]
            dx, dz = DX[out]
            # 前の 2 つ (外の面の口と、その先)
            for d in (1, 2):
                px, pz = x + dx * d, z + dz * d
                ok = stand_ok(b, px, y, pz) or (d == 2 and stand_ok(b, px, y + 1, pz)) or (d == 2 and stand_ok(b, px, y - 1, pz))
                if not ok:
                    problems.append('door %s at %s: blocked %d in front (%s / %s / below %s)' % (
                        n, (x, y, z), d, name(b.get((px, y, pz))), name(b.get((px, y + 1, pz))), name(b.get((px, y - 1, pz)))))
                    break
    for (x, y, z, out) in v.fake_doors:
        dx, dz = DX[out]
        px, pz = x + dx, z + dz
        if not (stand_ok(b, px, y, pz) or stand_ok(b, px, y + 1, pz) or stand_ok(b, px, y - 1, pz)):
            problems.append('fake door at %s: blocked in front (%s / %s / below %s)' % ((x, y, z), name(b.get((px, y, pz))), name(b.get((px, y + 1, pz))), name(b.get((px, y - 1, pz)))))
    # 碑: 前が空いているか
    for nm, (x, y, z) in v.marks.items():
        if nm.startswith('sign_'):
            s = b.get((x, y, z), '')
            f = s.split('facing=')[1].split(',')[0].split(']')[0] if 'facing=' in s else 'south'
            dx, dz = DX[f]
            if not (stand_ok(b, x + dx, y, z + dz) or stand_ok(b, x + dx, y + 1, z + dz) or stand_ok(b, x + dx, y - 1, z + dz)):
                problems.append('tablet %s at %s: nowhere to stand in front (%s)' % (nm, (x, y, z), name(b.get((x + dx, y, z + dz)))))
        elif nm in ('bell',):
            continue
        elif nm.startswith('villager_') or nm in ('stand', 'chaser', 'door'):
            if not stand_ok(b, x, y, z) and nm != 'door':
                problems.append('mark %s at %s: cannot stand (%s / %s / below %s)' % (nm, (x, y, z), name(b.get((x, y, z))), name(b.get((x, y + 1, z))), name(b.get((x, y - 1, z)))))
    # 鐘の足もと: 語り部が鐘のまわり 3 に立てるか
    bx, by, bz = v.marks['bell']
    spots = [(bx + dx, by - 2, bz + dz) for dx in range(-3, 4) for dz in range(-3, 4) if (dx, dz) != (0, 0)]
    if not any(stand_ok(b, *p) for p in spots):
        problems.append('bell: no spot for the storyteller within 3')
    # 門の口: 真ん中の列が通り抜けられるか (北の門は x=0、z -104..-94)
    for (gx, gz, ax) in ((0, -100, 'z'), (0, 100, 'z'), (-100, 0, 'x'), (100, 0, 'x')):
        for k in range(-5, 6):
            px, pz = (gx, gz + k) if ax == 'z' else (gx + k, gz)
            if not any(stand_ok(b, px, yy, pz) or (passable(b.get((px, yy, pz))) and passable(b.get((px, yy + 1, pz)))) for yy in range(0, 6)):
                problems.append('gate passage at %s blocked (%s / %s)' % ((px, 0, pz), name(b.get((px, 0, pz))), name(b.get((px, 1, pz)))))
                break
    # 歩いて届くか: 南門の内 (stand) から、段差 1 まで上り下りして歩ける所を広げ、戸の前・碑の前・印に届くか
    # (閉じた施設の鉄の戸と鉄格子は章で外れるので、外したものとして歩く)
    from collections import deque
    full = b
    b = {k: s_ for k, s_ in full.items() if name(s_) not in ('iron_bars', 'iron_door')}
    sx, sy, sz = v.marks['stand']
    seen = {(sx, sy, sz)}
    q = deque([(sx, sy, sz)])
    while q:
        x, y, z = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (-140 <= nx <= 120 and -120 <= nz <= 120):
                continue
            for ny in (y, y + 1, y - 1):
                if (nx, ny, nz) in seen:
                    break
                if stand_ok(b, nx, ny, nz) and (ny <= y or passable(b.get((x, y + 2, z)))):
                    seen.add((nx, ny, nz))
                    q.append((nx, ny, nz))
                    break
    def reach(x, y, z, r=1):
        return any((x + dx, y + dy, z + dz) in seen for dx in range(-r, r + 1) for dz in range(-r, r + 1) for dy in (-1, 0, 1))
    for (x, y, z), s in b.items():
        n = name(s)
        pass
    for (x, y, z), s in full.items():
        n = name(s)
        if n.endswith('_door') and 'trapdoor' not in n and 'half=lower' in s:
            f = s.split('facing=')[1].split(',')[0].split(']')[0]
            dx, dz = DX[OPP[f]]
            if not reach(x + 2 * dx, y, z + 2 * dz):
                problems.append('door %s at %s: cannot be reached on foot from the south gate' % (n, (x, y, z)))
    for nm, (x, y, z) in v.marks.items():
        if nm.startswith('sign_') or nm.startswith('villager_') or nm in ('chaser', 'smoke'):
            if not reach(x, y, z, 2):
                problems.append('mark %s at %s: cannot be reached on foot' % (nm, (x, y, z)))
    bx, by, bz = v.marks['bell']
    if not reach(bx, by - 2, bz, 3):
        problems.append('bell: the storyteller spot cannot be reached on foot')
    print('walkable cells from the south gate: %d' % len(seen))
    print('checked %d doors, %d marks' % (doors, len(v.marks)))
    for p in problems:
        print('  ' + p)
    print('%d problems' % len(problems))
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
