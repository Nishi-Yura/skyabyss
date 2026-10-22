# -*- coding: utf-8 -*-
"""灰の層 (y -120〜-240) の遊び: 真っ暗な灰の迷路 (docs/design/36 の 8 章。A9)。gen_abyss_layers.py が build(ctx) を呼ぶ。

形:
  - 縦穴の外、軸の向き (x か z の正負) の一つに、27 マス四方の迷路の箱を置く。入口は螺旋の道から壁を抜けた一マス幅の廊下 (印 ash_maze_in)。
    迷路は完全迷路 (行き止まりだけで、どの道も一本で奥へ届く)。中ほどに 灰の這い跡の見張り (印 watch_ash_maze)。
  - 一番遠い所に燠の番人の間 (印 ash_gate。3 マス四方に開く)。番人を倒すと AbyssShortcuts.open("ash") (story/ch5/layers/AshMaze)。
  - 照明は置かない（真っ暗）。床は灰（砂利と玄武岩）、燠の溜まり（マグマ・ほのかな光）、埋もれた骨。
  - 迷路の路そのものは置くだけ。灯りの中でだけ道を見せる仕掛けは story/ch5/layers/AshMaze（道は置いたブロックから BFS で求める）。
  - 迷路と螺旋・部屋が重ならないように、箱の場所は軸の向きを選んで決める（入口は螺旋の道の角度が軸に近い所）。
"""
import math
import random
from collections import deque

N = 15           # 迷路の一辺のマス数
G = 2 * N + 1    # 格子 (壁を含む一辺)。奇数の所がマス、偶数の所が壁か通路
ROOM_R = 27      # 部屋の箱の中心の半径 (gen_abyss_layers.room と同じ)
ROOM_HALF = 5
OUT0 = 24        # 迷路の内の縁 (縦穴の壁の外)
CORR = 5         # 入口の廊下の長さ (縦穴の壁を抜ける)
HEIGHT = 4       # 床から天井まで (床の上に 3 マス空く)


def block_of(h2):
    return h2 // 2 if h2 % 2 == 1 else h2 // 2 - 1


def build(ctx):
    v = ctx.v
    rng = random.Random(67)
    span = ctx.span('ash')
    rooms = _room_boxes(ctx)
    pick = _pick_entry(ctx, span, rooms)
    if pick is None:
        return
    th, h2, axis, sgn, tc = pick
    Y = block_of(h2)      # 迷路の床 (螺旋の床と同じ高さ)

    def world(a, b):
        """格子 (a, b) を世界の (x, z) へ。a は外へ、b は接線方向へ (中心 = 13 → tc)"""
        t = tc + (b - N)
        r = OUT0 + a
        if axis == 'x':
            return (sgn * r, t)
        return (t, sgn * r)

    def door(a):
        """廊下の列 (a < 0 は縦穴の中 / 壁)"""
        r = OUT0 + a
        return (sgn * r, tc) if axis == 'x' else (tc, sgn * r)

    # ---- 迷路の格子 (True = 通路)。完全迷路を深さ優先で掘る
    open_ = [[False] * G for _ in range(G)]
    seen = [[False] * N for _ in range(N)]
    start = (0, N // 2)
    stack = [start]
    seen[start[0]][start[1]] = True
    open_[2 * start[0] + 1][2 * start[1] + 1] = True
    while stack:
        ci, cj = stack[-1]
        nb = []
        for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ni, nj = ci + di, cj + dj
            if 0 <= ni < N and 0 <= nj < N and not seen[ni][nj]:
                nb.append((ni, nj, di, dj))
        if not nb:
            stack.pop()
            continue
        ni, nj, di, dj = nb[rng.randrange(len(nb))]
        seen[ni][nj] = True
        open_[2 * ni + 1][2 * nj + 1] = True
        open_[2 * ci + 1 + di][2 * cj + 1 + dj] = True
        stack.append((ni, nj))
    enter = (1, N)           # 入口 (格子)。外の壁 a=0 の b=N が開く
    open_[0][N] = True
    # ---- 一番遠いマスを燠の番人の間に、中ほどを見張りに
    dist = {}
    q = deque([(2 * start[0] + 1, 2 * start[1] + 1)])
    dist[q[0]] = 0
    while q:
        a, b = q.popleft()
        for da, db in ((2, 0), (-2, 0), (0, 2), (0, -2)):
            na, nb_ = a + da, b + db
            if 0 < na < G - 1 and 0 < nb_ < G - 1 and (na, nb_) not in dist and open_[(a + na) // 2][(b + nb_) // 2]:
                dist[(na, nb_)] = dist[(a, b)] + 1
                q.append((na, nb_))
    # 燠の番人の間: 道のりが長く、かつ入口から空間的にも遠いマス (迷路は曲がるので、道のりの最長だけでは入口の近くになることがある)
    deep = [k for k in dist if dist[k] >= 0.6 * max(dist.values())]
    far = max(deep, key=lambda k: (k[0] - 1) ** 2 + (k[1] - N) ** 2)
    mid = min(dist, key=lambda k: abs(dist[k] - dist[far] // 2))
    for a in range(far[0] - 1, far[0] + 2):
        for b in range(far[1] - 1, far[1] + 2):
            if 1 <= a <= G - 2 and 1 <= b <= G - 2:
                open_[a][b] = True

    # ---- 箱: 壁・床・天井・通路
    for a in range(G):
        for b in range(G):
            x, z = world(a, b)
            if open_[a][b]:
                v.set(x, Y, z, _floor(rng))
                for y in range(Y + 1, Y + HEIGHT):
                    v.set(x, y, z, 'air')
                v.set(x, Y + HEIGHT, z, 'blackstone')
            else:
                for y in range(Y, Y + HEIGHT + 1):
                    v.set(x, y, z, _wall(rng))
    # ---- 入口の廊下 (縦穴の壁を抜ける。一マス幅)
    for k in range(-CORR, 1):
        x, z = door(k)
        v.set(x, Y, z, 'blackstone')
        for y in range(Y + 1, Y + HEIGHT):
            v.set(x, y, z, 'air')
        v.set(x, Y + HEIGHT, z, 'blackstone')
    # 迷路の外の壁の戸口の両脇は、遠くの灯りの下でも暗いまま (壁の柄だけ)

    ex, ez = world(*enter)
    v.mark('ash_maze_in', ex, Y + 1, ez)
    gx, gz = world(far[0], far[1])
    v.mark('ash_gate', gx, Y + 1, gz)
    mx, mz = world(mid[0], mid[1])
    v.mark('watch_ash_maze', mx, Y + 1, mz)


def _floor(rng):
    r = rng.random()
    if r < 0.55:
        return 'gravel'
    if r < 0.75:
        return 'blackstone'
    if r < 0.88:
        return 'soul_soil'
    if r < 0.93:
        return 'magma_block'         # 燠の溜まり（灯りの中で赤く見える）
    return 'bone_block[axis=y]'      # 埋もれた骨


def _wall(rng):
    r = rng.random()
    if r < 0.35:
        return 'basalt[axis=y]'
    if r < 0.75:
        return 'polished_blackstone_bricks'
    return 'cracked_polished_blackstone_bricks'


def _room_boxes(ctx):
    """部屋（pool・camp・den。gen_abyss_layers.room と同じ決まり）の箱 (x0,z0,x1,z1,y0,y1)。迷路とは高さも重なるときだけぶつかる"""
    boxes = []
    for L in ctx.LAYERS:
        sp = ctx.span(L[0])
        if not sp:
            continue
        for k in range(3):
            th, h2 = sp[int(len(sp) * (0.2 + 0.3 * k))]
            cx = round(math.cos(th) * (ctx.R_IN + ctx.WALL + 5))
            cz = round(math.sin(th) * (ctx.R_IN + ctx.WALL + 5))
            y = (h2 + 1) // 2
            boxes.append((cx - ROOM_HALF - 2, cz - ROOM_HALF - 2, cx + ROOM_HALF + 2, cz + ROOM_HALF + 2, y - 2, y + 7))
    return boxes


def _pick_entry(ctx, span, rooms):
    """軸に近い角度の螺旋の道を選ぶ。箱（迷路 27 マス四方・外へ）が部屋と重ならず、入口の廊下が螺旋の道に届く所"""
    lo, hi = int(len(span) * 0.1), int(len(span) * 0.9)
    for i in range(lo, hi):
        th, h2 = span[i]
        for axis, sgn, ang in (('x', 1, 0.0), ('x', -1, math.pi), ('z', 1, math.pi / 2), ('z', -1, -math.pi / 2)):
            d = abs((th - ang + math.pi) % (2 * math.pi) - math.pi)
            if d > 0.12:
                continue
            lane = 17.5
            if axis == 'x':
                tc = round(lane * math.sin(th))
                box = (sgn * OUT0 - (0 if sgn > 0 else G), tc - N - 1, sgn * OUT0 + (G if sgn > 0 else 0), tc + N + 1)
            else:
                tc = round(lane * math.cos(th))
                box = (tc - N - 1, sgn * OUT0 - (0 if sgn > 0 else G), tc + N + 1, sgn * OUT0 + (G if sgn > 0 else 0))
            x0, z0, x1, z1 = min(box[0], box[2]), min(box[1], box[3]), max(box[0], box[2]), max(box[1], box[3])
            y0, y1 = block_of(h2), block_of(h2) + HEIGHT
            if any(not (x1 < a0 or x0 > a1 or z1 < b0 or z0 > b1 or y1 < c0 or y0 > c1) for (a0, b0, a1, b1, c0, c1) in rooms):
                continue
            return th, h2, axis, sgn, tc
    return None
