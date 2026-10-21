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
    return below is not None and name(below) not in ('air', 'cave_air')


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
    # 戸 (本物も入れない家の戸も) の前は、足の高さと頭の高さが空いていること (前に物があると、戸の前を塞いで見える)
    def front_clear(x, y, z, dx, dz):
        for d in (1, 2) if False else (1,):
            fx, fz = x + dx * d, z + dz * d
            if not (passable(b.get((fx, y, fz))) and passable(b.get((fx, y + 1, fz)))):
                return False
        return b.get((x + dx, y - 1, z + dz)) not in (None, 'air')
    for (x, y, z), s_ in list(b.items()):
        n_ = name(s_)
        if n_.endswith('_door') and 'trapdoor' not in n_ and 'half=lower' in s_:
            f = s_.split('facing=')[1].split(',')[0].split(']')[0]
            dx, dz = DX[OPP[f]]
            # 本物の戸は外の面の一つ奥にある: 前の 1 (外の面の口) と 2 (通り) を見る
            for d in (1, 2):
                fx, fz = x + dx * d, z + dz * d
                if not (passable(b.get((fx, y, fz))) and passable(b.get((fx, y + 1, fz)))):
                    problems.append('door at %s: something in front (%d): %s / %s' % ((x, y, z), d, name(b.get((fx, y, fz))), name(b.get((fx, y + 1, fz)))))
                    break
    for (x, y, z, out) in v.fake_doors:
        dx, dz = DX[out]
        px, pz = x + dx, z + dz
        if not front_clear(x, y, z, dx, dz):
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
    # 古井戸の下 (docs/design/35 の 6.9) は、縁の内のレバーで格子を開けて梯子で下りる。井戸の底 well_down からも歩く
    if 'well_down' in v.marks:
        seen.add(tuple(v.marks['well_down']))
        q.append(tuple(v.marks['well_down']))
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
    # 施設の印 (docs/design/35 の 6.6): 係の人 npc_<役> は足もとの 2 マスが空いて下が床、机ごしに話せる (3 以内まで歩いて行ける)。
    # 町のクエストの家 quest_house_<n> は立てて、歩いて届く。宿の寝台 inn_bed* は寝台のそばまで歩いて届く
    for nm, (x, y, z) in v.marks.items():
        if nm.startswith('npc_') or nm.startswith('quest_house_'):
            if not (passable(full.get((x, y, z))) and passable(full.get((x, y + 1, z)))
                    and name(full.get((x, y - 1, z))) not in ('air', 'cave_air') and full.get((x, y - 1, z)) is not None):
                problems.append('mark %s at %s: the attendant cannot stand (%s / %s / below %s)' % (
                    nm, (x, y, z), name(full.get((x, y, z))), name(full.get((x, y + 1, z))), name(full.get((x, y - 1, z)))))
            if not reach(x, y, z, 3 if nm.startswith('npc_') else 1):
                problems.append('mark %s at %s: cannot be reached on foot' % (nm, (x, y, z)))
        elif nm.startswith('inn_bed'):
            if 'bed' not in name(full.get((x, y, z))):
                problems.append('mark %s at %s: not on a bed (%s)' % (nm, (x, y, z), name(full.get((x, y, z)))))
            if not reach(x, y, z, 2):
                problems.append('mark %s at %s: the bed cannot be reached on foot' % (nm, (x, y, z)))
    # 本物の戸の内側: 戸の一つ奥 (足と頭) が空いている (中の家具が戸を塞がない)
    for (x, y, z), s in full.items():
        n = name(s)
        if n.endswith('_door') and 'trapdoor' not in n and 'half=lower' in s:
            f = s.split('facing=')[1].split(',')[0].split(']')[0]
            dx, dz = DX[f]
            if not (passable(full.get((x + dx, y, z + dz))) and passable(full.get((x + dx, y + 1, z + dz)))):
                problems.append('door at %s: blocked inside (%s / %s)' % ((x, y, z), name(full.get((x + dx, y, z + dz))), name(full.get((x + dx, y + 1, z + dz)))))
    # 梯子: 背の壁が丸ごとのブロック、一番下に歩いて届き、一番上の隣に立てる
    ladders = {}
    for (x, y, z), s in full.items():
        if name(s) == 'ladder':
            f = s.split('facing=')[1].split(',')[0].split(']')[0]
            dx, dz = DX[OPP[f]]
            bn = name(full.get((x + dx, y, z + dz)))
            if bn == 'air' or any(t in bn for t in ('pane', 'glass', 'door', 'stairs', 'slab', 'fence', 'wall', 'lantern', 'leaves', 'ladder', 'bars')):
                problems.append('ladder at %s: no solid wall behind (%s)' % ((x, y, z), bn))
            ladders.setdefault((x, z), []).append(y)
    for (x, z), ys in ladders.items():
        lo, hi = min(ys), max(ys)
        if not reach(x, lo, z, 1):
            problems.append('ladder at %s: the bottom cannot be reached on foot' % ((x, lo, z),))
        if 'trapdoor' in name(full.get((x, hi + 1, z))):
            continue   # 上は跳ね上げ戸 (開けて出る。古井戸の格子)
        if not any(stand_ok(full, x + ddx, yy, z + ddz) for (ddx, ddz) in ((1, 0), (-1, 0), (0, 1), (0, -1)) for yy in (hi, hi + 1)):
            problems.append('ladder at %s: no floor to step off at the top' % ((x, hi, z),))
    bx, by, bz = v.marks['bell']
    if not reach(bx, by - 2, bz, 3):
        problems.append('bell: the storyteller spot cannot be reached on foot')
    # 建物どうしの間: 足もと (軒を含む) が重なる・くっつく組み合わせ (同じ建物の部品と、北の段・街区の枠は除く)
    parts = [p for p in t.parts if p[0] not in ('block', 'terrace', 'plaza', 'part')]
    for i in range(len(parts)):
        for j in range(i + 1, len(parts)):
            a_, b_ = parts[i], parts[j]
            if a_[0] == b_[0]:
                continue
            gx = max(a_[1], b_[1]) - min(a_[3], b_[3])
            gz = max(a_[2], b_[2]) - min(a_[4], b_[4])
            if gx <= 0 and gz <= 0:
                problems.append('buildings touch: %s and %s (gap x %d, z %d)' % (a_[0], b_[0], gx - 1, gz - 1))
    # 地面より下 (y <= -1) の空気は設計図に書かれない (地面のまま残る)。地下の部屋・穴は cave_air で書く
    for (x, y, z), s_ in full.items():
        if y <= -1 and s_ == 'air':
            problems.append('plain air below ground at %s (use cave_air)' % ((x, y, z),))
    print('houses: %d sealed, %d quest houses (enterable), facilities: %s' % (len(v.fake_doors), t.quest_n, ', '.join(t.fac_names)))
    print('walkable cells from the south gate: %d' % len(seen))
    print('checked %d doors, %d marks' % (doors, len(v.marks)))
    for p in problems:
        print('  ' + p)
    print('%d problems' % len(problems))
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
