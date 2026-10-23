# -*- coding: utf-8 -*-
"""五章の四「灰の層」(docs/design/36 の 8 章)。形 B: 護りの下り道。隊長のトウヤと、心臓の手前の主の間まで一緒に下りる。灯りは少ない。

野営の北の口から一本の長い下り道。三つの待ち場で足を止めるトウヤを守りながら、待ち場を確かにすると印が入る:
  待ち場 1「灰の迷路」: 灯りのない迷路 (7 x 7 のマス。完全迷路)。光の術の灯りの中でだけ、出口へ続く燠の跡が見える。
                      出口の火 (beacon_ash) を灯すと、トウヤが跡をたどって来る
  待ち場 2「三つの術の封」: 封の間 (封の戸は、光・重さ・鼓動の三つの術を戸のそばで続けて使うと開く)。トウヤは灯りを掲げて待つ
  待ち場 3「燠の番の縄張り」: 大きな燠の番と煤の子の群れを払う
  逃げの道「闇の波」: 灯り喰いの主の吐いた闇が、後ろから灯りを喰いながら追ってくる。光の術で押し返せる。闇に呑まれる前に格子の門へ
"""
from __future__ import annotations

import random
from collections import deque

KIND = 'B'
MAZE_N = 7
MAZE_U0 = 36     # 迷路の格子の左下 (局所)
MAZE_W0 = -7


def build_b(ctx, ex):
    v, lay = ctx.v, ctx.lay
    rng = v.rng
    r, Y = ex.r, ex.Y
    y1, y2, y3 = Y - 4, Y - 8, Y - 14
    # ---- 道 A (北へ。見晴らしの裂け目の上を渡る)
    ex.corridor([(11, 0, Y), (14, 0, Y), (30, 0, y1)], h=4, half=1, lamps=8)
    lay.segs['gatecorr_ash'] = dict(layer='ash', kind='gatecorr', seg=lay.cur, route=r)

    # ---- 待ち場 1: 迷路の口の小部屋 (トウヤが待つ) → 迷路 → 出口の間 (出口の火)
    room1 = ex.room('mouth', 32, -3, y1, 2, 4, 4, lamps=1)
    for yy in range(y1, y1 + 3):
        r.cell(35, -6, yy)
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
    start, goal = (0, 1), (g - 1, g - 2)
    open_.add(start)
    open_.add(goal)
    solution = solve(open_, start, goal)
    on_path = set(solution)
    lay.fresh('maze')
    for (a, b) in sorted(open_):
        for yy in range(y1, y1 + 3):
            r.cell(MAZE_U0 + a, MAZE_W0 + b, yy)
        if (a, b) in on_path:
            continue
        if rng.random() < 0.18:
            r.put(MAZE_U0 + a, MAZE_W0 + b, y1 - 1, 'magma_block')
        elif rng.random() < 0.1:
            r.put(MAZE_U0 + a, MAZE_W0 + b, y1, 'bone_block[axis=y]' if rng.random() < 0.3 else 'blackstone_slab[type=bottom,waterlogged=false]')
    r.mark('maze_ash_in', MAZE_U0 + start[0], MAZE_W0 + start[1], y1)
    r.mark('maze_ash_out', MAZE_U0 + goal[0], MAZE_W0 + goal[1], y1)
    a0 = r.xz(MAZE_U0, MAZE_W0)
    a1 = r.xz(MAZE_U0 + g - 1, MAZE_W0 + g - 1)
    v.mark('maze_ash_a', min(a0[0], a1[0]), y1, min(a0[1], a1[1]))
    v.mark('maze_ash_b', max(a0[0], a1[0]), y1 + 2, max(a0[1], a1[1]))
    ex.room('exit', 52, 7, y1, 1, 2, 4, lamps=0)
    r.put(53, 6, y1, 'soul_campfire[facing=west,lit=false,signal_fire=false,waterlogged=false]')
    r.mark('beacon_ash', 53, 6, y1)
    ex.altar(1, 53, 8, y1, room1)
    ex.spawns(1, [(31, -7), (30, -4), (34, 1), (31, 1)], y1)
    ex.walk((30, 0, y1), (30, -6, y1), (34, -6, y1))
    ex.wait(1)
    ex.walk((34, -6, y1), (35, -6, y1))
    for (a, b) in solution:
        x, z = r.xz(MAZE_U0 + a, MAZE_W0 + b)
        ex.add(x, y1, z)
    ex.walk((MAZE_U0 + goal[0], MAZE_W0 + goal[1], y1), (52, 6, y1), (52, 9, y1))
    ex.routes.append((r, {'mouth': room1}))

    # ---- 道 B (東へ)
    ex.corridor([(52, 9, y1), (52, 26, y2)], lamps=11)

    # ---- 待ち場 2: 封の間。封の戸 (+w の壁、w = 39, 40) と三つの台 (光・重さ・鼓動)
    room2 = ex.room('seal', 52, 32, y2, 6, 6, 7, lamps=1)
    for dw in (39, 40):
        for k in (-1, 0, 1):
            for yy in range(y2, y2 + 4):
                r.cell(52 + k, dw, yy)
                r.put(52 + k, dw, yy, 'gilded_blackstone' if (k == 0 and yy == y2 + 1) else 'polished_blackstone_bricks')
    r.mark('seal_ash', 52, 39, y2 + 1)
    a0 = r.xz(51, 39)
    a1 = r.xz(53, 40)
    v.mark('seal_ash_a', min(a0[0], a1[0]), y2, min(a0[1], a1[1]))
    v.mark('seal_ash_b', max(a0[0], a1[0]), y2 + 3, max(a0[1], a1[1]))
    for k, u in enumerate((48, 52, 56)):
        r.put(u, 37, y2, ['sea_lantern', 'lodestone', 'sculk_catalyst'][k])
        r.mark('seal_ped_%d' % k, u, 37, y2)
    r.mark('puzzle_ash', 52, 32, y2)
    ex.altar(2, 57, 32, y2, room2)
    ex.spawns(2, [(48, 28), (56, 28), (48, 35), (56, 35)], y2)
    ex.walk((52, 26, y2), (52, 32, y2))
    ex.wait(2)
    ex.walk((52, 32, y2), (51, 32, y2), (51, 41, y2), (52, 41, y2))
    ex.routes.append((r, {'seal': room2}))

    # ---- 道 C (封の戸の向こうから東へ、それから南へ長く)
    ex.corridor([(52, 41, y2), (52, 44, y2), (-33, 44, y3)], lamps=11)

    # ---- 待ち場 3: 燠の番の縄張り
    room3 = ex.room('territory', -40, 40, y3, 7, 8, 9, lamps=1)
    path = {(u, 44) for u in range(-40, -32)} | {(-40, w) for w in range(40, 45)} | {(u, 40) for u in range(-44, -39)} \
        | {(-44, w) for w in range(32, 41)}
    for _ in range(14):
        a, b = -40 + rng.randint(-5, 5), 40 + rng.randint(-6, 6)
        if abs(a + 40) + abs(b - 40) > 3 and all((a + da, b + db) not in path for da in (-1, 0, 1) for db in (-1, 0, 1)):
            for yy in range(y3, y3 + rng.randint(2, 5)):
                r.put(a, b, yy, 'basalt[axis=y]')
    ex.altar(3, -46, 40, y3, room3)
    ex.spawns(3, [(-46, 34), (-46, 46), (-35, 46), (-35, 35)], y3)
    ex.walk((-33, 44, y3), (-40, 44, y3), (-40, 40, y3))
    ex.wait(3)
    ex.walk((-40, 40, y3), (-44, 40, y3), (-44, 32, y3))
    ex.routes.append((r, {'territory': room3}))


def solve(open_, start, goal):
    """迷路の入口から出口までの道 (幅優先)"""
    prev = {start: None}
    q = deque([start])
    while q:
        c = q.popleft()
        if c == goal:
            break
        for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (c[0] + d[0], c[1] + d[1])
            if n in open_ and n not in prev:
                prev[n] = c
                q.append(n)
    out = []
    c = goal
    while c is not None:
        out.append(c)
        c = prev[c]
    return out[::-1]
