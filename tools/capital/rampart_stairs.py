# -*- coding: utf-8 -*-
"""都の城壁の上 (胸壁のあいだの歩廊) へ上がる石の段を、書き出し済みの都の設計図に足す (2026-11-08 の声「上らせる気の無い建築」→
「門のそばに階段」に決まり)。都 (capital.json) は作り直さず、"blocks" (runs のあとに上書きする並び) に段を足す。二度目は何もしない (meta.rampart_stairs)。

    python -I tools/capital/rampart_stairs.py            # 都
    python -I tools/capital/rampart_stairs.py --dry      # 置き場所だけ出す

四つの門の側ごとに、壁の内の面に沿った 2 列の帯で、地面が平らで頭の上が空いた所のうち門にいちばん近い所 (門の口から 8〜60) を選び、
門から遠ざかる向きに上がる段を置く (段の下は石レンガで詰める)。上り切った所の内の手すり (壁の上の内の列) を抜く。
"""
from __future__ import annotations

import json
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
sys.path.insert(0, os.path.join(ROOT, 'tools', 'audit'))
from check_stairs import load_blueprint, top  # noqa: E402

PATH = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss', 'opening', 'capital.json')
W = 100           # 壁の外の面 (z = -W が外の列、-W+2 が内の列)
WALK_Y = 12       # 壁の上の段 (立つ高さ 13)
STEP = 'minecraft:stone_brick_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]'
FILL = 'minecraft:stone_bricks'
AIR = 'minecraft:air'
CW = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}


def rot(x, z, r):
    for _ in range(r):
        x, z = -z, x
    return x, z


def turn(f, r):
    for _ in range(r):
        f = CW[f]
    return f


def ground(cells, x, z):
    ys = [y for y in range(-2, WALK_Y) if top(cells.get((x, y, z))) >= 1]
    return max(ys) if ys else None


def plan(cells, r):
    """側 r (0 = 北) の段の置き場所: (u0, 向き +1/-1, 地面の y)。無ければ None"""
    rows = (-W + 3, -W + 4)
    best = None
    for sign in (1, -1):
        for start in range(8, 90):
            g = None
            n = None
            ok = True
            us = None
            for k in range(0, 16):
                u = sign * (start + k)
                for lz in rows:
                    x, z = rot(u, lz, r)
                    gy = ground(cells, x, z)
                    if gy is None or (g is not None and gy != g):
                        ok = False
                        break
                    g = gy
                    if any(top(cells.get((x, y, z))) > 0 for y in range(gy + 1, WALK_Y + 3)):
                        ok = False
                        break
                # 内がわの一列は空けておく (ほかの段と並ばない)
                bx, bz = rot(u, -W + 5, r)
                if any(top(cells.get((bx, y, bz))) > 0 or 'stairs' in (cells.get((bx, y, bz)) or '') for y in range(g + 1 if g is not None else 0, WALK_Y + 3)):
                    ok = False
                    break
                if not ok:
                    break
                n = WALK_Y - g
                if n <= 0:
                    ok = False
                    break
                if k + 1 >= n:
                    us = [sign * (start + i) for i in range(n)]
                    break
            if ok and us:
                # 上り切る所の壁の上 (内の列) が歩廊であること
                x, z = rot(us[-1], -W + 2, r)
                if top(cells.get((x, WALK_Y, z))) >= 1:
                    cand = (start, sign, g, us)
                    if best is None or start < best[0]:
                        best = cand
                break
    return best


def main(argv):
    dry = '--dry' in argv
    d = json.load(open(PATH, encoding='utf-8'))
    meta = d.setdefault('meta', {})
    if meta.get('rampart_stairs') and not dry:
        print('capital: rampart stairs already added')
        return 0
    _, cells = load_blueprint(PATH)
    pal = d['palette']
    index = {s: i for i, s in enumerate(pal)}

    def idx(s):
        if s not in index:
            index[s] = len(pal)
            pal.append(s)
        return index[s]

    blocks = d.setdefault('blocks', [])
    for r in range(4):
        p = plan(cells, r)
        if p is None:
            print('side', r, ': no place found')
            continue
        start, sign, g, us = p
        face = turn('east' if sign > 0 else 'west', r)
        print('side', r, ': ground y', g, 'steps', len(us), 'from u', us[0], 'to', us[-1])
        if dry:
            continue
        for k, u in enumerate(us):
            y = g + 1 + k
            for lz in (-W + 3, -W + 4):
                x, z = rot(u, lz, r)
                for yy in range(g + 1, y):
                    blocks.append([x, yy, z, idx(FILL)])
                blocks.append([x, y, z, idx(STEP % face)])
        # 着く所の内の手すりを抜く (上り切った段と、その先の一マス)
        for u in (us[-1], us[-1] + sign):
            x, z = rot(u, -W + 2, r)
            blocks.append([x, WALK_Y + 1, z, idx(AIR)])
            blocks.append([x, WALK_Y + 2, z, idx(AIR)])
            if 'stairs' in (cells.get((x, WALK_Y, z)) or ''):
                blocks.append([x, WALK_Y, z, idx(FILL)])
    # 歩廊をさえぎる塔に、歩廊の真ん中の列 (z = -W+1) で幅 1・高さ 2 の通り抜けを開ける (塔ごとに歩廊が切れていた)
    tunnels = 0
    for r in range(4):
        run = []
        for u in range(-W - 4, W + 5):
            x, z = rot(u, -W + 1, r)
            solid = top(cells.get((x, WALK_Y + 1, z))) >= 1 or top(cells.get((x, WALK_Y + 2, z))) >= 1
            floor = top(cells.get((x, WALK_Y, z))) >= 1
            if solid and floor:
                run.append(u)
                continue
            if run and len(run) <= 25 and not solid:
                # 両側が歩廊 (床があって頭が空いている) の塔だけ
                ux, uz = rot(run[0] - 1, -W + 1, r)
                if top(cells.get((ux, WALK_Y + 1, uz))) < 1 and floor:
                    for uu in run:
                        tx, tz = rot(uu, -W + 1, r)
                        if not dry:
                            blocks.append([tx, WALK_Y + 1, tz, idx(AIR)])
                            blocks.append([tx, WALK_Y + 2, tz, idx(AIR)])
                    # 通り抜けの床と、その前後の塔の裾の段は平らな石に (段のまま床になると埋まった段に見える)
                    for uu in [run[0] - 2, run[0] - 1] + run + [run[-1] + 1, run[-1] + 2]:
                        for lz in (-W + 1,):
                            fx, fz = rot(uu, lz, r)
                            st = cells.get((fx, WALK_Y, fz)) or ''
                            if 'stairs' in st and not dry:
                                blocks.append([fx, WALK_Y, fz, idx(FILL)])
                    for uu in run:
                        tx, tz = rot(uu, -W + 1, r)
                        if not dry:
                            # 通り抜けの壁と天井に、封じの詰め物 (黒いコンクリート) が見えないよう石レンガを張る
                            for (lu, lz, ly) in ((uu, -W, WALK_Y + 1), (uu, -W, WALK_Y + 2), (uu, -W + 2, WALK_Y + 1), (uu, -W + 2, WALK_Y + 2),
                                                 (uu, -W + 1, WALK_Y + 3)):
                                nx, nz = rot(lu, lz, r)
                                st = cells.get((nx, ly, nz)) or ''
                                if 'concrete' in st or st == '' or st.endswith(':air'):
                                    blocks.append([nx, ly, nz, idx(FILL)])
                    tunnels += 1
            run = []
    print('tunnels through towers:', tunnels)
    if not dry:
        meta['rampart_stairs'] = True
        with open(PATH, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(d, f, ensure_ascii=False, separators=(',', ':'))
            f.write('\n')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
