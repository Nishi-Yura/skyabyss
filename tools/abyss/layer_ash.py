# -*- coding: utf-8 -*-
"""五章の四「灰の層」(docs/design/36 の 8 章)。灯りは少ない (部屋に一つ、廊下は間を広く)。

三本の道:
  1 東「灰の迷路」: 灯りのない迷路 (7 x 7 のマス。完全迷路)。光の術の灯りの中でだけ、出口へ続く燠の跡が見える → 印の部屋
  2 南「三つの術の封」: 燠の広間 → 封の間 (封の戸は、光・重さ・鼓動の三つの術を戸のそばで続けて使うと開く) → 印の部屋
  3 西「燠の番の縄張り」: 燃えかすの間 → 縄張り (燠の番の大きいもの) → 印の部屋
"""
from __future__ import annotations

import random

MAZE_N = 7
MAZE_U0 = 20     # 迷路の格子の左下 (局所)
MAZE_W0 = 2

ROUTES = [
    # 迷路は build で掘る。道の部屋は迷路の出口の先の印の部屋だけ (最初の部屋 = 迷路の入口の手前の小部屋)
    {'nodes': [('mouth', 15, 3, 0, 2, 2, 4), ('mark', 46, 8, 0, 4, 4, 6)],
     'link': ['uw', {'from': (MAZE_U0 + 2 * MAZE_N + 1, MAZE_W0 + 2 * MAZE_N - 1, 0), 'via': [(46, MAZE_W0 + 2 * MAZE_N - 1, 0)]}],
     'room_lamps': 1, 'lamps': 0},
    {'nodes': [('embers', 18, 4, -1, 4, 3, 5), ('seal', 32, 8, -2, 6, 6, 7), ('mark', 47, 8, -2, 4, 4, 6)],
     'link': ['uw', 'uw', {'from': (41, 8, -2)}], 'room_lamps': 1, 'lamps': 11},
    {'nodes': [('cinders', 18, 4, 1, 4, 3, 5), ('territory', 34, 10, 1, 8, 7, 9), ('mark', 50, 6, 1, 4, 4, 6)],
     'room_lamps': 1, 'lamps': 11},
]


def build(ctx, routes):
    v = ctx.v
    rng = v.rng
    (r1, rooms1), (r2, rooms2), (r3, rooms3) = routes
    y = ctx.CAMP_Y['ash']
    # ---- 1: 迷路。格子 (2N+1) の奇数の所がマス。入口 = 局所 (MAZE_U0, MAZE_W0 + 1)、出口 = 最後の列の右上のマス
    g = 2 * MAZE_N + 1
    open_ = set()
    rnd = random.Random(67)
    seen = {(0, 0)}
    stack = [(0, 0)]
    open_.add((1, 1))
    while stack:
        cx, cz = stack[-1]
        nb = [(cx + dx, cz + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
              if 0 <= cx + dx < MAZE_N and 0 <= cz + dz < MAZE_N and (cx + dx, cz + dz) not in seen]
        if not nb:
            stack.pop()
            continue
        nx, nz = rnd.choice(nb)
        seen.add((nx, nz))
        open_.add((2 * nx + 1, 2 * nz + 1))
        open_.add((cx + nx + 1, cz + nz + 1))
        stack.append((nx, nz))
    # 入口と出口の穴
    open_.add((0, 1))
    open_.add((g - 1, g - 2))
    for (a, b) in open_:
        for yy in range(y, y + 3):
            r1.cell(MAZE_U0 + a, MAZE_W0 + b, yy)
        if rng.random() < 0.18:
            r1.put(MAZE_U0 + a, MAZE_W0 + b, y - 1, 'magma_block')
        elif rng.random() < 0.1:
            r1.put(MAZE_U0 + a, MAZE_W0 + b, y, 'bone_block[axis=y]' if rng.random() < 0.3 else 'blackstone_slab[type=bottom,waterlogged=false]')
    # 入口の廊下 (口の小部屋 → 迷路の入口)
    r1.corridor([(17, 3, y), (MAZE_U0, 3, y)], half=0, h=3, lamps=0)
    r1.mark('maze_ash_in', MAZE_U0, MAZE_W0 + 1, y)
    r1.mark('maze_ash_out', MAZE_U0 + g - 1, MAZE_W0 + g - 2, y)
    a0 = r1.xz(MAZE_U0, MAZE_W0)
    a1 = r1.xz(MAZE_U0 + g - 1, MAZE_W0 + g - 1)
    v.mark('maze_ash_a', min(a0[0], a1[0]), y, min(a0[1], a1[1]))
    v.mark('maze_ash_b', max(a0[0], a1[0]), y + 2, max(a0[1], a1[1]))
    # ---- 2: 封の戸 (封の間の +u の壁)
    u, w, yy0, hu, hw, h = rooms2['seal']
    for du in (1, 2):
        for k in (-1, 0, 1):
            for yy in range(yy0, yy0 + 4):
                r2.cell(u + hu + du, w + k, yy)
                r2.put(u + hu + du, w + k, yy, 'gilded_blackstone' if (k == 0 and yy == yy0 + 1) else 'polished_blackstone_bricks')
    r2.mark('seal_ash', u + hu + 1, w, yy0 + 1)
    a0 = r2.xz(u + hu + 1, w - 1)
    a1 = r2.xz(u + hu + 2, w + 1)
    v.mark('seal_ash_a', min(a0[0], a1[0]), yy0, min(a0[1], a1[1]))
    v.mark('seal_ash_b', max(a0[0], a1[0]), yy0 + 3, max(a0[1], a1[1]))
    # 三つの台 (光・重さ・鼓動の印。戸の前)
    for k, b in enumerate((w - 4, w, w + 4)):
        r2.put(u + hu - 1, b, yy0, ['sea_lantern', 'lodestone', 'sculk_catalyst'][k])
        r2.mark('seal_ped_%d' % k, u + hu - 1, b, yy0)
    r2.mark('puzzle_ash', u, w, yy0)
    # ---- 3: 縄張り
    u, w, yy0, hu, hw, h = rooms3['territory']
    r3.mark('terr_ash', u, w, yy0)
    for _ in range(12):
        a, b = u + rng.randint(-hu + 2, hu - 2), w + rng.randint(-hw + 2, hw - 2)
        if abs(a - u) + abs(b - w) > 3:
            for yy in range(yy0, yy0 + rng.randint(2, 5)):
                r3.put(a, b, yy, 'basalt[axis=y]')
