# -*- coding: utf-8 -*-
"""施設の内装の検査 (違和感のもと): python -I tools/capital/audit_interiors.py [pkl] [-v]

- dark   : 立てる床の明るさ (ブロックの灯りだけで数える) が 8 より低い所
- float  : 浮いた灯り・鎖・額・敷物・蝋燭・鉢・感圧板 (下か上に支えがない)
- chair  : 椅子 (床の階段) の背が机の側を向いている、または背の側が空いている
- pocket : 戸の前から歩いて行けない立てる所 (受付の奥は除く: 数だけ出す)
- door   : 戸の前 2 マスと戸口の内側 2 マスに物がある
- tight  : 戸から受付までの通り道の幅 (歩ける所の最小幅) が 1 の所は一覧にしない (数だけ)
問題があれば一覧を出して終わりの番号 1。
"""
from __future__ import annotations

import os
import pickle
import sys
from collections import deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import interior_render as ir  # noqa: E402
from interior_render import base_name, prop, AIRS  # noqa: E402

DX = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
OPP = {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}

LIGHT = {'lantern': 15, 'torch': 14, 'wall_torch': 14, 'soul_torch': 10, 'soul_wall_torch': 10, 'soul_lantern': 10, 'campfire': 15, 'soul_campfire': 10,
         'glowstone': 15, 'sea_lantern': 15, 'shroomlight': 15, 'jack_o_lantern': 15, 'end_rod': 14, 'fire': 15, 'lava': 15, 'beacon': 15,
         'ochre_froglight': 15, 'verdant_froglight': 15, 'pearlescent_froglight': 15, 'redstone_lamp_lit': 15, 'enchanting_table': 7, 'magma_block': 3,
         'brewing_stand': 1, 'amethyst_cluster': 5, 'light': 15, 'respawn_anchor': 0, 'cake': 0}
LIT_ON = ('furnace', 'smoker', 'blast_furnace', 'campfire')


def emission(s):
    n = base_name(s)
    if n in LIGHT and n != 'campfire':
        return LIGHT[n]
    if n == 'campfire' and prop(s, 'lit') == 'true':
        return 15
    if n.endswith('candle') and prop(s, 'lit') == 'true':
        return min(15, 3 * int(prop(s, 'candles', '1')))
    if n.endswith('candle_cake') and prop(s, 'lit') == 'true':
        return 3
    if n == 'redstone_lamp' and prop(s, 'lit') == 'true':
        return 15
    if n == 'light':
        return int(prop(s, 'level', '15'))
    return 0


def opaque(s):
    if s is None:
        return False
    n = base_name(s)
    if n in AIRS:
        return False
    if any(k in n for k in ('glass', 'pane', 'door', 'trapdoor', 'stairs', 'slab', 'lantern', 'banner', 'sign', 'fence', '_wall', 'ladder', 'carpet',
                            'potted', 'candle', 'chain', 'bars', 'leaves', 'torch', 'bed', 'pressure_plate', 'rod', 'head', 'skull', 'cauldron',
                            'brewing', 'anvil', 'flower', 'fern', 'grass', 'bell', 'lectern', 'decorated_pot', 'cake', 'chest', 'water', 'vine', 'cluster', 'fire')):
        return False
    return True


def light_map(b, box):
    X0, X1, Y0, Y1, Z0, Z1 = box
    L = {}
    q = deque()
    for (x, y, z), s in b.items():
        if X0 <= x <= X1 and Y0 <= y <= Y1 and Z0 <= z <= Z1:
            e = emission(s)
            if e > 0:
                L[(x, y, z)] = e
                q.append((x, y, z))
    q = deque(sorted(q, key=lambda p: -L[p]))
    while q:
        p = q.popleft()
        lv = L[p]
        if lv <= 1:
            continue
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
            if not (X0 <= n[0] <= X1 and Y0 <= n[1] <= Y1 and Z0 <= n[2] <= Z1):
                continue
            if L.get(n, 0) >= lv - 1:
                continue
            L[n] = lv - 1
            if not opaque(b.get(n)):
                q.append(n)
    return L


def free(s):
    """体が通れる (空気・敷物・感圧板・花・戸・梯子・蝋燭の絵 などは別扱い)"""
    if s is None:
        return True
    n = base_name(s)
    return n in AIRS or 'carpet' in n or n in ('short_grass', 'fern') or n.endswith('pressure_plate') or n.endswith('_door') or n.endswith('wall_torch') \
        or n.endswith('wall_banner') or n.endswith('wall_sign') or n.endswith('_button') or n == 'ladder' or n.endswith('wall_hanging_sign') or n.endswith('item_frame') or n == 'redstone_wire'


def bad_head(s):
    """頭の高さで体にぶつかる物 (吊り灯りの下端など)"""
    return not free(s)


def floor_ok(s):
    """立てる床 (体の下)"""
    if s is None:
        return False
    n = base_name(s)
    if n in AIRS or 'carpet' in n or n.endswith('pressure_plate') or n.endswith('_door'):
        return False
    return True


def stand_cells(b, rect, y, h):
    x0, z0, x1, z1 = rect
    out = []
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if floor_ok(b.get((x, y - 1, z))) and free(b.get((x, y, z))) and free(b.get((x, y + 1, z))):
                out.append((x, z))
    return out


def audit_room(b, nm, rect, y, h, L):
    x0, z0, x1, z1 = rect
    errs = {'dark': [], 'float': [], 'chair': [], 'pocket': [], 'door': []}
    cells = stand_cells(b, rect, y, h)
    for (x, z) in cells:
        lv = L.get((x, y, z), 0)
        if lv < 8:
            errs['dark'].append((x, z, lv))
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for yy in range(y, y + h):
                s = b.get((x, yy, z))
                if not s:
                    continue
                n = base_name(s)
                if n in AIRS:
                    continue
                below = b.get((x, yy - 1, z))
                above = b.get((x, yy + 1, z))
                if n in ('lantern', 'soul_lantern'):
                    if prop(s, 'hanging') == 'true':
                        if not (above and (opaque(above) or base_name(above) in ('chain',) or base_name(above).endswith('slab') or base_name(above).endswith('fence') or base_name(above).endswith('_wall'))):
                            errs['float'].append((x, yy, z, 'hanging lantern'))
                        elif base_name(above) == 'chain':
                            # 鎖の上をたどる
                            k = yy + 1
                            while base_name(b.get((x, k, z)) or 'air') == 'chain':
                                k += 1
                            t = b.get((x, k, z))
                            if not (t and base_name(t) not in AIRS):
                                errs['float'].append((x, yy, z, 'chain end'))
                    else:
                        if not (below and base_name(below) not in AIRS):
                            errs['float'].append((x, yy, z, 'lantern'))
                elif n == 'chain':
                    k = yy + 1
                    while base_name(b.get((x, k, z)) or 'air') == 'chain':
                        k += 1
                    t = b.get((x, k, z))
                    if prop(s, 'axis') == 'y' and not (t and base_name(t) not in AIRS):
                        errs['float'].append((x, yy, z, 'chain'))
                elif 'carpet' in n or n.endswith('pressure_plate') or 'candle' in n or n.startswith('potted_') or n in ('flower_pot', 'cake'):
                    if not (below and base_name(below) not in AIRS and 'carpet' not in base_name(below) or (below and 'carpet' in base_name(below) and False)):
                        errs['float'].append((x, yy, z, n))
                elif n.endswith('_stairs') and prop(s, 'half') != 'top':
                    f = prop(s, 'facing')
                    dxf, dzf = DX[f]
                    front = b.get((x - dxf, yy, z - dzf))   # 背の反対 = 座った人の向き
                    back = b.get((x + dxf, yy, z + dzf))
                    isl = lambda t: t and (base_name(t).endswith('_fence') or base_name(t).endswith('_slab') or base_name(t).endswith('pressure_plate') or base_name(t) in ('lectern', 'barrel', 'cartography_table', 'crafting_table', 'smithing_table', 'fletching_table'))
                    # 椅子 = 左右が同じ向きの階段でない孤立の階段で、背の側が壁でない
                    side_same = any(b.get((x + a, yy, z + c)) and base_name(b.get((x + a, yy, z + c))).endswith('_stairs') and prop(b.get((x + a, yy, z + c)), 'facing') == f
                                    for (a, c) in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                    if isl(back) and not isl(front):
                        errs['chair'].append((x, yy, z, 'back to table ' + f))
                    elif not side_same and not isl(front) and not (front and opaque(front)) and not (back and opaque(back)):
                        pass
    # 戸の前
    for (dxx, yy, dzz), s in b.items():
        if x0 - 1 <= dxx <= x1 + 1 and z0 - 1 <= dzz <= z1 + 1 and y - 1 <= yy <= y + 1 and base_name(s).endswith('_door') and prop(s, 'half') == 'lower' and 'iron' not in base_name(s):
            f = prop(s, 'facing')
            for k in (-2, -1, 1, 2):
                for hh in (0, 1):
                    t = b.get((dxx + DX[f][0] * k, yy + hh, dzz + DX[f][1] * k))
                    if t and not free(t):
                        errs['door'].append((dxx, yy, dzz, 'blocked at %d: %s' % (k, base_name(t))))
    # 戸から歩いて行ける所
    doors = [(dxx, yy, dzz) for (dxx, yy, dzz), s in b.items() if x0 - 1 <= dxx <= x1 + 1 and z0 - 1 <= dzz <= z1 + 1 and y - 1 <= yy <= y + 1
             and base_name(s).endswith('_door') and prop(s, 'half') == 'lower']
    if doors:
        seen = set()
        dq = deque()
        for (dxx, yy, dzz) in doors:
            for f in DX.values():
                p = (dxx + f[0], dzz + f[1])
                if p in cells:
                    dq.append(p)
                    seen.add(p)
        while dq:
            p = dq.popleft()
            for d in DX.values():
                n = (p[0] + d[0], p[1] + d[1])
                if n in cells and n not in seen:
                    seen.add(n)
                    dq.append(n)
        for p in cells:
            if p not in seen:
                errs['pocket'].append(p)
    return errs, len(cells)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('-')]
    verbose = '-v' in sys.argv
    homes = []
    if args:
        d = pickle.load(open(args[0], 'rb'))
        b = d['b']
        homes = d.get('homes', [])
    else:
        import town
        import vox
        t = town.Town(vox.STYLES['b'])
        b = t.build().b
        homes = getattr(t, 'home_rooms', [])
    bad = 0
    rows = []
    for (nm, rect, y, h, mode) in list(ir.ROOMS) + [(a, r, yy, hh, 'se') for (a, r, yy, hh) in homes]:
        x0, z0, x1, z1 = rect
        box = (x0 - 12, x1 + 12, y - 2, y + h + 1, z0 - 12, z1 + 12)
        L = light_map(b, box)
        errs, n = audit_room(b, nm, rect, y, h, L)
        cnt = {k: len(v) for k, v in errs.items()}
        rows.append((nm, n, cnt))
        for k, v in errs.items():
            if k == 'pocket':
                continue
            bad += len(v)
        if verbose:
            for k, v in errs.items():
                if k == 'pocket' and '-p' not in sys.argv:
                    continue
                for e in v[:12]:
                    print(nm, k, e)
    for (nm, n, cnt) in rows:
        print('%-14s cells %3d  %s' % (nm, n, ' '.join('%s=%d' % kv for kv in cnt.items())))
    print('problems (excluding pocket):', bad)
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
