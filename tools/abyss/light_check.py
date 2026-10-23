# -*- coding: utf-8 -*-
"""主の間・野営・心臓の間の明るさ (ブロック光の下り 1/マスの見積り)。python -I tools/abyss/light_check.py"""
from __future__ import annotations
import math, os, sys
from collections import deque
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import audit_layers as A  # noqa: E402
import gen_abyss_layers as g  # noqa: E402

LIGHT = {'soul_lantern': 10, 'lantern': 15, 'soul_campfire': 10, 'campfire': 15, 'sea_lantern': 15, 'glowstone': 15, 'shroomlight': 15,
         'magma_block': 3, 'torch': 14, 'soul_torch': 10, 'redstone_lamp': 15, 'copper_lantern': 15, 'jack_o_lantern': 15,
         'lava': 15, 'fire': 15, 'soul_fire': 10, 'glow_lichen': 7, 'candle': 3, 'sculk_catalyst': 6, 'end_rod': 14,
         'ochre_froglight': 15, 'verdant_froglight': 15, 'pearlescent_froglight': 15, 'crying_obsidian': 10,
         'amethyst_cluster': 5, 'large_amethyst_bud': 4, 'enchanting_table': 7, 'lightning_rod': 0}


def emit(st):
    n = st.split('[')[0]
    if n in ('campfire', 'soul_campfire') or n.endswith('copper_bulb'):
        return (15 if n != 'soul_campfire' else 10) if 'lit=true' in st else 0
    if n == 'copper_bulb':
        return 15 if 'lit=true' in st else 0
    return LIGHT.get(n, 0)


def clear(st):
    n = st.split('[')[0]
    return st in ('air', 'cave_air') or n in LIGHT and n not in ('magma_block', 'sea_lantern', 'glowstone', 'shroomlight', 'redstone_lamp') \
        or 'glass' in n or 'slab' in n or 'carpet' in n or n.endswith('wall') or 'lichen' in n or n == 'chain' or 'sign' in n or 'fence' in n \
        or n in ('barrel', 'lectern', 'lightning_rod', 'skeleton_skull', 'sculk_shrieker', 'ladder')


def levels(b):
    lv = {}
    q = deque()
    for p, st in b.items():
        e = emit(st)
        if e:
            lv[p] = e
            q.append(p)
    while q:
        p = q.popleft()
        l = lv[p]
        if l <= 1:
            continue
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
            st = b.get(n)
            if st is None or not clear(st):
                continue
            if lv.get(n, 0) < l - 1:
                lv[n] = l - 1
                q.append(n)
    return lv


def air(b, c):
    return b.get(c) in ('air', 'cave_air')


def pct(cells, lv):
    return 100 * sum(1 for c in cells if lv.get(c, 0) >= 7) // max(1, len(cells))


def report(b, lv):
    for L in g.ORDER:
        Y = g.CAMP_Y[L]
        ax, az = g.DIRS['n'](g.ARENA_U, 0)
        cells = []
        for x in range(ax - g.ARENA_R, ax + g.ARENA_R + 1):
            for z in range(az - g.ARENA_R, az + g.ARENA_R + 1):
                if math.hypot(x - ax, z - az) > g.ARENA_R - 1:
                    continue
                for y in range(Y - g.ARENA_DROP - 1, Y - g.ARENA_DROP + 6):
                    if air(b, (x, y, z)) and not air(b, (x, y - 1, z)) and b.get((x, y - 1, z)) is not None:
                        cells.append((x, y, z))
                        break
        print('arena %-5s floor cells %4d  >=7: %3d%%  avg %.1f' % (L, len(cells), pct(cells, lv), sum(lv.get(c, 0) for c in cells) / max(1, len(cells))))
        cc = [(x, Y, z) for x in range(-7, 8) for z in range(-7, 8) if air(b, (x, Y, z))]
        print('camp  %-5s cells %4d >=7: %3d%%' % (L, len(cc), pct(cc, lv)))
    hy = g.HEART_BOTTOM + 1
    hc = [(x, hy, z) for x in range(-g.HEART_R, g.HEART_R + 1) for z in range(-g.HEART_R, g.HEART_R + 1)
          if math.hypot(x, z) <= g.HEART_R - 1 and air(b, (x, hy, z))]
    print('heart cells %d >=7: %d%%' % (len(hc), pct(hc, lv)))


if __name__ == '__main__':
    data = A.build()
    b = A.decode(data)
    print('blocks (nonair)', sum(1 for s in b.values() if s not in ('air', 'cave_air')), 'total', len(b))
    report(b, levels(b))
