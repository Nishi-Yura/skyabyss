# -*- coding: utf-8 -*-
"""深淵の層の検査 (docs/design/36 の 8 章):
  1. 囲い: 空気のマスの 26 の隣がどれも置いてある (外の何もない空間が見えない)
  2. 落ちるブロックの下が空でない
  3. 歩いて届くか: 各層の野営から、三つの印・門・主の間・下りの鎖まで。戸・封・格子・門は開いたものとして、
     下りの鎖 → 次の層の着く所、井戸の底 → 上の口 (重さの術で浮く) はつながりとして数える

python -I tools/abyss/audit_layers.py   (設計図を作り直すが書き出さない。書くのは gen_abyss_layers.py)
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

OPENABLE = ('door_bone', 'hid_bone', 'door_storm_0', 'door_storm_1', 'door_storm_2', 'seal_ash')


def build():
    captured = {}
    real_save = vox.save
    vox.save = lambda path, data: captured.setdefault('d', data)
    try:
        g.main()
    finally:
        vox.save = real_save
    return captured['d']


def decode(data):
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
    return b


def boxes(marks, scuts=True):
    out = []
    for m in OPENABLE:
        if m + '_a' in marks:
            out.append((marks[m + '_a'], marks[m + '_b']))
    for L in g.ORDER:
        for i in (1, 2, 3):
            if scuts:
                out.append((marks['scut_%s_%d_a' % (L, i)], marks['scut_%s_%d_b' % (L, i)]))
        Y = g.CAMP_Y[L]
        out.append(([-1, Y, -g.CAMP_HALF - 2], [1, Y + 2, -g.CAMP_HALF - 1]))
    return out


def main():
    data = build()
    marks = data['marks']
    b = decode(data)
    probs = []
    # 1. 囲い
    holes = 0
    for (x, y, z), s in b.items():
        if s not in ('air', 'cave_air'):
            continue
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if (x + dx, y + dy, z + dz) not in b:
                        holes += 1
                        if holes <= 5:
                            probs.append('open to the void next to %s' % ((x, y, z),))
    # 2. 落ちるブロック
    for (x, y, z), st in b.items():
        n = st.split('[')[0]
        if n in g.FALLING or any(n.endswith('_' + f) for f in g.FALLING):
            below = b.get((x, y - 1, z))
            if below is None or below in ('air', 'cave_air'):
                probs.append('%s at %s: falls' % (n, (x, y, z)))
    # 3. 歩いて届くか (開けられる物は空気に)
    w = dict(b)
    for (a, c) in boxes(marks, scuts=False):
        for x in range(a[0], c[0] + 1):
            for y in range(a[1], c[1] + 1):
                for z in range(a[2], c[2] + 1):
                    w[(x, y, z)] = 'air'
    links = {}
    for i, L in enumerate(g.ORDER):
        nxt = 'arrive_%s' % g.ORDER[i + 1] if i + 1 < len(g.ORDER) else 'heart_in'
        links[tuple(marks['descend_%s' % L])] = tuple(marks[nxt])
    links[tuple(marks['well_salt'])] = tuple(marks['well_salt_top'])

    def walk(start):
        seen = {start}
        q = deque([start])
        while q:
            x, y, z = q.popleft()
            for (lx, ly, lz), to in links.items():
                if abs(lx - x) <= 2 and abs(lz - z) <= 2 and abs(ly - y) <= 1 and to not in seen:
                    seen.add(to)
                    q.append(to)
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + dx, z + dz
                for ny in (y, y + 1, y - 1, y - 2, y - 3):
                    # 上りの一段は 0.5 まで (平らな床から段の上へは 1.5 で上れない)
                    if ny == y + 1 and step_ok(w.get((nx, ny, nz))) and not step_ok(w.get((x, y, z))):
                        continue
                    if stand_ok(w, nx, ny, nz) and (ny <= y or passable(w.get((x, y + 2, z)))):
                        if ny < y - 1 and not all(passable(w.get((nx, yy, nz))) for yy in range(ny, y + 2)):
                            continue
                        if (nx, ny, nz) not in seen:
                            seen.add((nx, ny, nz))
                            q.append((nx, ny, nz))
                        break
        return seen

    seen = walk(tuple(marks['arrival']))
    must = []
    for L in g.ORDER:
        must += ['mark_%s_%d' % (L, i) for i in (1, 2, 3)]
        must += ['gate_%s_in' % L, 'arena_%s' % L, 'descend_%s' % L, 'survivor_%s' % L, 'pool_%s' % L, 'journal_%s_0' % L]
        must += ['journal_%s_%d' % (L, i) for i in (1, 2, 3)]
        must += ['grave_%s_%d' % (L, i) for i in (1, 2, 3)]
    must += ['heart', 'heart_in', 'rest_5']
    for m in must:
        mx, my, mz = marks[m]
        if not any((mx + a, my + c, mz + e) in seen for a in range(-2, 3) for e in range(-2, 3) for c in (-1, 0, 1)):
            probs.append('%s at %s: not reached on foot' % (m, (mx, my, mz)))
    # 近道の格子を開ける
    for (a, c) in boxes(marks):
        for x in range(a[0], c[0] + 1):
            for y in range(a[1], c[1] + 1):
                for z in range(a[2], c[2] + 1):
                    w[(x, y, z)] = 'air'
    # 近道の線の上にいれば、印の部屋から野営へ歩いて帰れる (格子は開いたもの)
    for L in g.ORDER:
        for i in (1, 2, 3):
            back = walk(tuple(marks['markroom_%s_%d' % (L, i)]))
            cx, cy, cz = marks['camp_%s' % L]
            if not any((cx + a, cy, cz + e) in back for a in range(-3, 4) for e in range(-3, 4)):
                probs.append('mark room %s %d: no way back to camp' % (L, i))
    ys = [p[1] for p in seen]
    print('walkable cells: %d (y %d..%d), void holes: %d' % (len(seen), min(ys), max(ys), holes))
    for p in probs[:40]:
        print(p)
    print('%d problems' % len(probs))
    return probs


if __name__ == '__main__':
    sys.exit(1 if main() else 0)
