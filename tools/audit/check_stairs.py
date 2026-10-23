# -*- coding: utf-8 -*-
"""この Mod が建てる建物の階段を調べる (ゲームは動かさない)。

    python tools/audit/check_stairs.py                   # data/skyabyss/opening/*.json の設計図を全部 (都・遺跡・洞・祠・淵の野営地など)
    python tools/audit/check_stairs.py capital ch1_      # 名前の一部で絞る
    python tools/audit/check_stairs.py --sbd run/dumps/x.sbd   # 書き出した構造 (.sbd。Java で建てる祠・試練の部屋など) も
    ... -v 指摘を全部出す (既定は種類ごとに 8 件まで) / --json 機械向け

規則 (ゲームの BuildAudit の階段の規則を、設計図の上で同じように見る。都の飾りの階段も、歩ける所にある物は対象):
  STAIR_UPSIDE    床の高さにある逆さ (上付き) の階段 (足場になるのに段になっていない → 下付きに)
  STAIR_REVERSED  段の低い側が壁で、高い側が空いている (向きが逆)
  STAIR_RUN       上り下りの並びと向きが合わない / 同じ列で隣が逆向き
  STAIR_RISE      段の前後で 1 段より高い (跳ばないと上がれない・最初の段が高い)
  STAIR_BURIED    床に埋まった段 (前後とも丸ごとのブロック)
  STAIR_DOOR      戸の前後 (足と頭の高さ) を塞ぐ階段・戸から遠ざかって上がる段
  ROOF_GAP        屋根の段の穴 (列の 1 マスの抜け・2 段の跳び)
  CORNER_MISSING  二つの列が出会う角の 1 マスが空いている (角の形の階段が無い)
階段の形 (内角・外角) は置いたあとゲームが隣に合わせる (SiteJob.finish) ので、ここでもバニラの決め方で計算してから見る。
許す例は ALLOW に理由つきで書く。指摘が 1 つでもあれば終了コード 1。
"""
from __future__ import annotations

import glob
import json
import os
import sys
from collections import Counter, defaultdict

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
OPENING = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss', 'opening')

DX = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
OPP = {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}
CCW = {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'}
CW = {v: k for k, v in CCW.items()}

# 許す例: (設計図の名前の一部, 種類, 座標の箱 (x0,y0,z0,x1,y1,z1) か None, 理由)
ALLOW = [
]

# 当たりの無いもの
NOCOLL = ('air', 'cave_air', 'void_air', 'light', 'water', 'lava', 'torch', 'wall_torch', 'redstone_torch', 'flower', 'tulip', 'poppy', 'daisy',
          'bluet', 'allium', 'cornflower', 'lily_of', 'dandelion', 'short_grass', 'tall_grass', 'fern', 'pressure_plate', 'rail', 'button',
          'banner', 'sign', 'vine', 'sapling', 'dead_bush', 'seagrass', 'kelp', 'mushroom', 'cobweb', 'tripwire', 'lever', 'coral', 'sugar_cane',
          'redstone_wire', 'glow_lichen', 'moss_carpet_', 'bush', 'roots', 'sprouts', 'petals', 'nether_portal', 'fire', 'structure_void')
NOCOLL_EXACT = {'grass', 'snow', 'wheat', 'carrots', 'potatoes', 'beetroots', 'sweet_berry_bush', 'pink_petals'}


def name(s):
    if s is None:
        return 'air'
    return s.split('[')[0].replace('minecraft:', '')


def props(s):
    if s is None or '[' not in s:
        return {}
    return dict(kv.split('=', 1) for kv in s[s.index('[') + 1:s.rindex(']')].split(',') if '=' in kv)


_TOP = {}


def top(s):
    """当たりの上の高さ (0 = 当たりなし)。ゲームの getCollisionShape の max y のおおよそ"""
    if s is None:
        return 0.0
    t = _TOP.get(s)
    if t is not None:
        return t
    n = name(s)
    p = props(s)
    if n in NOCOLL_EXACT or any(k in n for k in NOCOLL) and not n.endswith(('_stairs', '_slab', '_wall', '_fence', '_block', '_planks', '_bricks')):
        t = 0.0
    elif n == 'snow':
        t = 0.0
    elif n.endswith('_slab'):
        t = 0.5 if p.get('type') == 'bottom' else 1.0
    elif n.endswith('_carpet'):
        t = 0.0625
    elif n.endswith(('_fence', '_wall', '_fence_gate')) and 'sign' not in n:
        t = 0.0 if n.endswith('_fence_gate') and p.get('open') == 'true' else 1.5
    elif n.endswith('_trapdoor'):
        t = 1.0 if p.get('open') == 'true' or p.get('half') == 'top' else 0.1875
    elif n.endswith('_door'):
        t = 0.0  # 戸は通り道 (開けて通る)
    elif n in ('lantern', 'soul_lantern'):
        t = 0.5625
    elif n == 'chain' or n.endswith('iron_bars') or n.endswith('_pane') or n == 'glass_pane':
        t = 1.0
    elif n.endswith('_bed'):
        t = 0.5625
    elif 'candle' in n and 'cake' not in n:
        t = 0.0  # 小さい当たり。足場としては見ない
    elif n in ('ladder', 'scaffolding'):
        t = 0.0
    elif n.startswith('potted_') or n == 'flower_pot':
        t = 0.375
    else:
        t = 1.0
    _TOP[s] = t
    return t


def full_cube(s):
    """丸ごとの立方体 (壁・床。家具の柵・半ブロック・階段・樽などの台は含めない)"""
    n = name(s)
    return top(s) == 1 and not n.endswith(('_stairs', '_slab', '_trapdoor', '_pane', 'iron_bars', 'chain', 'lantern')) and         not any(t in n for t in ('barrel', 'chest', 'table', 'lectern', 'anvil', 'cauldron', 'composter', 'loom', 'smoker', 'furnace',
                                 'stonecutter', 'grindstone', 'bookshelf', 'hay', 'bed', 'pot', 'campfire'))


def is_stair(s):
    return s is not None and name(s).endswith('_stairs')


class Grid:
    def __init__(self, cells, ground=None):
        self.c = cells
        self.ground = ground  # この y 以下で、設計図に無い所は地面 (固い)

    def get(self, x, y, z):
        s = self.c.get((x, y, z))
        if s is None and self.ground is not None and y <= self.ground:
            return 'minecraft:stone'
        return s

    def top(self, x, y, z):
        return top(self.get(x, y, z))

    def clear(self, x, y, z):
        return self.top(x, y, z) == 0

    def surf(self, x, y, z):
        """その柱の立てる床の高さ (y+1 から y-4)。無ければ None"""
        for yy in range(y + 1, y - 5, -1):
            t = self.top(x, yy, z)
            if t > 0 and self.clear(x, yy + 1, z) and self.clear(x, yy + 2, z):
                if name(self.get(x, yy + 1, z)) in ('water', 'lava'):
                    return None  # 水の底は床ではない
                return yy + t
        return None


def stair_props(g, x, y, z):
    s = g.get(x, y, z)
    if not is_stair(s):
        return None
    return props(s)


def eff_shape(g, x, y, z, p):
    """バニラの StairBlock.getStairsShape"""
    f, half = p.get('facing', 'north'), p.get('half', 'bottom')

    def take(face):
        dx, dz = DX[face]
        n = stair_props(g, x + dx, y, z + dz)
        return n is None or n.get('facing') != f or n.get('half') != half

    dx, dz = DX[f]
    fr = stair_props(g, x + dx, y, z + dz)
    if fr is not None and fr.get('half') == half:
        f1 = fr.get('facing')
        if (f1 in ('north', 'south')) != (f in ('north', 'south')) and take(OPP[f1]):
            return 'outer_left' if f1 == CCW[f] else 'outer_right'
    bk = stair_props(g, x - dx, y, z - dz)
    if bk is not None and bk.get('half') == half:
        f2 = bk.get('facing')
        if (f2 in ('north', 'south')) != (f in ('north', 'south')) and take(f2):
            return 'inner_left' if f2 == CCW[f] else 'inner_right'
    return 'straight'


def standable(g, x, y, z):
    """(x, y, z) に足を置いて立てる (足と頭が空き、下に床)"""
    return g.clear(x, y, z) and g.clear(x, y + 1, z) and g.top(x, y - 1, z) >= 0.5


def feet(g, x, y, z):
    """足を (x, y, z) のマスに置いて立てるときの足の高さ。立てなければ None"""
    s = g.get(x, y, z)
    t = top(s)
    if t == 0.5 and (is_stair(s) and props(s).get('half') == 'bottom' or name(s).endswith('_slab')):
        h = y + (1.0 if is_stair(s) else 0.5)
    elif t <= 0.2:
        b = g.top(x, y - 1, z)
        if b == 0 or b > 1:
            return None
        h = y - 1 + b if t == 0 else y + t
    elif is_stair(s) and props(s).get('half') == 'bottom':
        h = y + 0.5
    else:
        return None
    if not g.clear(x, y + 1, z):
        return None
    if h > y + 0.4 and not g.clear(x, y + 2, z):
        return None
    return h


def reach(g, seeds, limit=3_000_000):
    """seeds から歩いて (1 段までは跳んで) 行ける足の位置の集合 (建物の範囲の 2 外まで)"""
    xs = [k[0] for k in g.c]
    zs = [k[2] for k in g.c]
    bx0, bx1, bz0, bz1 = min(xs) - 2, max(xs) + 2, min(zs) - 2, max(zs) + 2
    seen = {}
    todo = []
    for (x, y, z) in seeds:
        for yy in range(y + 2, y - 4, -1):
            h = feet(g, x, yy, z)
            if h is not None:
                if (x, yy, z) not in seen:
                    seen[(x, yy, z)] = h
                    todo.append((x, yy, z))
                break
    while todo and len(seen) < limit:
        x, y, z = todo.pop()
        h = seen[(x, y, z)]
        for dx, dz in DX.values():
            nx, nz = x + dx, z + dz
            if not (bx0 <= nx <= bx1 and bz0 <= nz <= bz1):
                continue
            for ny in (y + 1, y, y - 1, y - 2, y - 3):
                if (nx, ny, nz) in seen:
                    break
                h2 = feet(g, nx, ny, nz)
                if h2 is None:
                    # 足の高さのマスが塞がっていたら、そこより下へは落ちない
                    if ny <= y and not g.clear(nx, ny, nz) and not g.clear(nx, ny + 1, nz):
                        break
                    continue
                if h2 - h > 1.25 or (h2 - h > 0.6 and not g.clear(x, y + 2, z)):
                    break
                seen[(nx, ny, nz)] = h2
                todo.append((nx, ny, nz))
                break
    return seen


def check(g, label, seeds=None):
    out = []
    walk = reach(g, seeds) if seeds is not None else None

    def flag(kind, pos, msg):
        out.append((label, kind, pos, msg))

    stairs = [(k, props(s), s) for k, s in g.c.items() if is_stair(s)]
    shapes = {k: eff_shape(g, *k, p) for k, p, s in stairs}
    doors = [(k, props(s)) for k, s in g.c.items() if name(s).endswith('_door') and 'trap' not in name(s) and props(s).get('half') == 'lower']
    for (x, y, z), p, s in stairs:
        f = p.get('facing', 'north')
        half = p.get('half', 'bottom')
        fx, fz = DX[f]
        head = g.clear(x, y + 1, z) and g.clear(x, y + 2, z)
        # 床の高さ: 四方のどれかに、同じ高さ (足が y) か 1 つ上で立てる所がある
        if walk is not None:
            near_floor = (x, y, z) in walk or (x, y + 1, z) in walk
        else:
            near_floor = any(standable(g, x + dx, y, z + dz) or standable(g, x + dx, y + 1, z + dz) for dx, dz in DX.values())
        if half == 'top':
            if g.top(x, y - 1, z) >= 1 and head and any(((x, y + 1, z) in walk or (x + dx, y, z + dz) in walk) if walk is not None else standable(g, x + dx, y, z + dz)
                                                        for dx, dz in DX.values()):
                flag('STAIR_UPSIDE', (x, y, z), 'upside-down stair at floor level (stood on like a step) facing %s: %s' % (f, name(s)))
            continue
        if not head or not near_floor or shapes[(x, y, z)] != 'straight':
            continue
        # 同じ列で隣が逆向き
        for side in (CW[f], CCW[f]):
            sx, sz = DX[side]
            n = stair_props(g, x + sx, y, z + sz)
            if n and n.get('half') == 'bottom' and n.get('facing') == OPP[f] and shapes.get((x + sx, y, z + sz)) == 'straight' and \
                    g.clear(x + sx, y + 1, z + sz):
                flag('STAIR_RUN', (x, y, z), 'neighbour stair in the same row faces the opposite way (%s vs %s)' % (f, OPP[f]))
        # 次の段: 1 つ上の下付きの階段で、頭が空き、その背の高い側の先にも頭の空きがある (窓台・蛇腹は除く)
        up = [d for d, (dx, dz) in DX.items() if (n := stair_props(g, x + dx, y + 1, z + dz)) and n.get('half') == 'bottom'
              and g.clear(x + dx, y + 2, z + dz) and g.clear(x + dx + DX[n.get('facing', 'north')][0], y + 2, z + dz + DX[n.get('facing', 'north')][1])]
        down = [d for d, (dx, dz) in DX.items() if (n := stair_props(g, x + dx, y - 1, z + dz)) and n.get('half') == 'bottom'
                and g.clear(x + dx, y, z + dz)]
        if up:
            if f not in up and OPP[f] not in down:
                flag('STAIR_RUN', (x, y, z), 'stair faces %s but the next step up is toward %s' % (f, up))
            continue
        if f in down and OPP[f] not in down:
            flag('STAIR_RUN', (x, y, z), 'stair faces %s, toward the step below it (the run descends the way it faces)' % f)
            continue
        lo = (x - fx, y, z - fz)
        hi = (x + fx, y, z + fz)
        lo_wall = g.top(*lo) >= 1 or g.top(lo[0], y + 1, lo[2]) >= 1
        hi_open = g.clear(*hi) and g.clear(hi[0], y + 1, hi[2])
        s_lo = g.surf(*lo)
        s_hi = g.surf(*hi)
        lp = stair_props(g, *lo)
        valley = lp is not None and lp.get('facing') == OPP[f]
        if g.top(*lo) >= 1 and g.top(*hi) >= 1 and g.top(lo[0], y + 1, lo[2]) == 0 and not valley:
            flag('STAIR_BURIED', (x, y, z), 'stair embedded in a floor (full blocks on both its low and high side) facing %s' % f)
            continue
        if lo_wall and hi_open and full_cube(g.get(*lo)):
            flag('STAIR_REVERSED', (x, y, z), 'low side is a wall but the high side is open (facing %s rises into open air)' % f)
            continue
        side_exit = any(d != OPP[f] and g.top(x + dx, y, z + dz) >= 1 and g.clear(x + dx, y + 1, z + dz) and g.clear(x + dx, y + 2, z + dz)
                        for d, (dx, dz) in DX.items())
        if side_exit and not lo_wall:
            continue
        if s_lo is not None and s_hi is not None and s_hi + 0.4 < s_lo and full_cube(g.get(*lo)):
            flag('STAIR_REVERSED', (x, y, z), 'high side floor (%.1f) is lower than the low side floor (%.1f), facing %s' % (s_hi, s_lo, f))
            continue
        if s_hi is not None and s_hi >= y + 1.75 and OPP[f] in down:
            flag('STAIR_RISE', (x, y, z), 'top of the run: the floor beyond the high side (%.1f) is a whole block above the step, facing %s' % (s_hi, f))
            continue
        if g.clear(*lo) and s_lo is not None and y - 1.5 <= s_lo <= y - 0.75:
            flag('STAIR_RISE', (x, y, z), 'low side floor %.1f is %.1f below the stair (first step too high), facing %s' % (s_lo, y - s_lo, f))
    # 戸の前後
    for (x, y, z), p in doors:
        f = p.get('facing', 'north')
        fx, fz = DX[f]
        for sgn in (1, -1):
            cx, cz = x + fx * sgn, z + fz * sgn
            for dy in (0, 1):
                if is_stair(g.get(cx, y + dy, cz)):
                    flag('STAIR_DOOR', (cx, y + dy, cz), 'stair in the doorway path of the door at %s (height %+d)' % ((x, y, z), dy))
            sp = stair_props(g, cx, y - 1, cz)
            if sp and sp.get('half') == 'bottom':
                # 戸の 1 つ下の段は、戸の方へ上がる (背の高い側が戸の方)
                toward = f if sgn == -1 else OPP[f]
                if sp.get('facing') != toward and shapes.get((cx, y - 1, cz)) == 'straight' and g.clear(cx, y, cz):
                    flag('STAIR_DOOR', (cx, y - 1, cz), 'step below the door at %s rises %s, away from the door' % ((x, y, z), sp.get('facing')))
    # 屋根の穴・角
    for (x, y, z), p, s in stairs:
        f = p.get('facing', 'north')
        half = p.get('half', 'bottom')
        fx, fz = DX[f]
        if half == 'bottom' and g.clear(x, y + 1, z):
            # 列の 1 マスの抜け (屋根の面。下が空いている所)
            for side in (CW[f],):
                sx, sz = DX[side]
                gx, gz = x + sx, z + sz
                n2 = stair_props(g, x + 2 * sx, y, z + 2 * sz)
                if g.get(gx, y, gz) is None or name(g.get(gx, y, gz)) in ('air', 'cave_air'):
                    if n2 and n2.get('facing') == f and n2.get('half') == half and g.clear(gx, y + 1, gz) and \
                            g.clear(gx, y - 1, gz) and g.clear(x, y - 1, z):
                        flag('ROOF_GAP', (gx, y, gz), 'one-cell hole in a row of %s stairs facing %s' % (name(s), f))
            # 2 段の跳び: 背の側の 1 つ上が空いていて、2 つ上に次の段
            if g.clear(x + fx, y, z + fz) and g.clear(x + fx, y + 1, z + fz):
                n2 = stair_props(g, x + fx, y + 2, z + fz)
                if n2 and n2.get('facing') == f and n2.get('half') == 'bottom' and g.clear(x, y + 2, z) and g.clear(x + fx, y - 1, z + fz):
                    flag('ROOF_GAP', (x + fx, y + 1, z + fz), 'roof steps rise 2 at once (hole between %s and the next row)' % ((x, y, z),))
        # 角の抜け: p の西と北 (など) の二つの列が、p で出会うはずなのに p が空き
        for a in DX:
            for b in (CW[a],):
                ax, az = DX[a]
                bx, bz = DX[b]
                # この階段が a 側の列 (p = この階段の位置 - a)
                px, pz = x - ax, z - az
                if g.get(px, y, pz) is not None and name(g.get(px, y, pz)) not in ('air', 'cave_air'):
                    continue
                q = stair_props(g, px - bx, y, pz - bz)
                if q is None or q.get('half') != half:
                    continue
                fq = q.get('facing')
                if f in (b, OPP[b]) and fq in (a, OPP[a]) and ((f == b) == (fq == a)):
                    # 列が p をはさんで続いているか (角の先が無い = 本当の端)
                    if is_stair(g.get(x + ax, y, z + az)) and is_stair(g.get(px - 2 * bx, y, pz - 2 * bz)) and g.clear(px, y + 1, pz):
                        flag('CORNER_MISSING', (px, y, pz), 'rows of %s (facing %s) and (facing %s) meet here but the corner cell is empty' % (name(s), f, fq))
    return out


FULL_OF = {'brick': 'bricks', 'stone_brick': 'stone_bricks', 'mossy_stone_brick': 'mossy_stone_bricks', 'deepslate_brick': 'deepslate_bricks',
           'deepslate_tile': 'deepslate_tiles', 'quartz': 'quartz_block', 'purpur': 'purpur_block', 'nether_brick': 'nether_bricks',
           'red_nether_brick': 'red_nether_bricks', 'mud_brick': 'mud_bricks', 'end_stone_brick': 'end_stone_bricks', 'tuff_brick': 'tuff_bricks',
           'polished_blackstone_brick': 'polished_blackstone_bricks', 'prismarine_brick': 'prismarine_bricks', 'cut_copper': 'cut_copper',
           'smooth_quartz': 'smooth_quartz', 'cobbled_deepslate': 'cobbled_deepslate'}
WOODS = ('oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'bamboo', 'crimson', 'warped')


def full_of(s):
    """階段と同じ材の丸ごとのブロック"""
    n = name(s)[:-len('_stairs')]
    pre = 'minecraft:' if s.startswith('minecraft:') else ''
    if n in WOODS:
        return pre + n + '_planks'
    for w in ('waxed_', ''):
        if n.startswith(w) and n[len(w):] in FULL_OF:
            return pre + w + FULL_OF[n[len(w):]]
    if n.endswith('_brick') or n.endswith('_tile'):
        return pre + n + 's'
    return pre + n


def with_props(s, **kv):
    p = props(s)
    p.update(kv)
    return s.split('[')[0] + '[' + ','.join('%s=%s' % (k, v) for k, v in p.items()) + ']'


def fix(cells, seeds=None, rounds=4, ground=-1):
    """check の指摘のうち、形で直せるものを直す (cells を書き換える。返り値 = 直した数と残り)。
    BURIED → 背の側の丸ごとのブロック (無ければ同じ材の丸ごと)、REVERSED → 向きを逆に、RUN → 次の段の向きへ / 逆に、
    UPSIDE → 下付きに、ROOF_GAP の 1 マスの抜け → 隣と同じ階段、2 段の跳び → 間に同じ階段、RISE (最初の段が 1 高い) → 下に 1 段足す"""
    done = 0
    for _ in range(rounds):
        g = Grid(cells, ground)
        fs = check(g, 'fix', seeds)
        changed = 0
        for _, kind, (x, y, z), msg in fs:
            s = cells.get((x, y, z))
            p = props(s) if s else {}
            f = p.get('facing')
            if kind == 'STAIR_BURIED':
                fx, fz = DX[f]
                hi = cells.get((x + fx, y, z + fz))
                cells[(x, y, z)] = hi if hi and full_cube(hi) else full_of(s)
            elif kind == 'STAIR_REVERSED':
                cells[(x, y, z)] = with_props(s, facing=OPP[f])
            elif kind == 'STAIR_RUN' and 'next step up is toward' in msg:
                ups = [d for d in DX if "'%s'" % d in msg]
                if len(ups) != 1:
                    continue
                cells[(x, y, z)] = with_props(s, facing=ups[0])
            elif kind == 'STAIR_RUN' and 'descends' in msg:
                cells[(x, y, z)] = with_props(s, facing=OPP[f])
            elif kind == 'STAIR_RUN':
                continue  # 同じ列で隣が逆向き: どちらが正しいか形からは決まらない
            elif kind == 'STAIR_UPSIDE':
                cells[(x, y, z)] = with_props(s, half='bottom')
            elif kind == 'ROOF_GAP' and 'one-cell hole' in msg:
                for d, (dx, dz) in DX.items():
                    n = cells.get((x + dx, y, z + dz))
                    m = cells.get((x - dx, y, z - dz))
                    if is_stair(n) and n == m:
                        cells[(x, y, z)] = n
                        break
                else:
                    continue
            elif kind == 'ROOF_GAP':
                n = cells.get((x, y + 1, z))
                if not is_stair(n):
                    continue
                cells[(x, y, z)] = n
            elif kind == 'STAIR_RISE' and 'first step too high' in msg:
                fx, fz = DX[f]
                lx, lz = x - fx, z - fz
                if g.clear(lx, y - 1, lz) and g.top(lx, y - 2, lz) >= 1:
                    cells[(lx, y - 1, lz)] = s
                else:
                    continue
            else:
                continue
            changed += 1
        done += changed
        if not changed:
            break
    left = check(Grid(cells, ground), 'fix', seeds)
    return done, left


def load_blueprint(path):
    d = json.load(open(path, encoding='utf-8'))
    pal = d['palette']
    cells = {}
    for b in d.get('boxes', []):
        for x in range(b[0], b[3] + 1):
            for y in range(b[1], b[4] + 1):
                for z in range(b[2], b[5] + 1):
                    cells[(x, y, z)] = pal[b[6]]
    for r in d.get('runs', []):
        y, z, x = r[0], r[1], r[2]
        for i in range(3, len(r) - 1, 2):
            idx, n = r[i], r[i + 1]
            if idx >= 0:
                s = pal[idx]
                for k in range(n):
                    cells[(x + k, y, z)] = s
            x += n
    for b in d.get('blocks', []):
        cells[(b[0], b[1], b[2])] = pal[b[3]]
    return d, cells


def load_sbd(path):
    sys.path.insert(0, os.path.join(ROOT, 'tools', 'structure'))
    from sbd import load  # noqa
    d = load(path)
    ox, oy, oz = d.origin
    cells = {}
    sy, sz, sx = d.ids.shape
    for y in range(sy):
        for z in range(sz):
            row = d.ids[y, z]
            for x in range(sx):
                e = d.palette[row[x]]
                st = e.get('state') or e.get('name') or 'minecraft:air'
                if e.get('air'):
                    continue
                cells[(x + ox, y + oy, z + oz)] = st
    return cells


ARROW = {'north': '^', 'south': 'v', 'west': '<', 'east': '>'}


def glyph(s):
    n = name(s)
    if is_stair(s):
        p = props(s)
        a = ARROW[p.get('facing', 'north')]
        return a if p.get('half') == 'bottom' else {'^': 'A', 'v': 'V', '<': '{', '>': '}'}[a]
    if n.endswith('_door'):
        return 'D'
    if n.endswith('_slab'):
        return '_' if props(s).get('type') == 'bottom' else '='
    t = top(s)
    if t == 0:
        return '.' if n in ('air', 'cave_air') else ','
    if t > 1:
        return '+'
    if t < 1:
        return 'o'
    return '#'


def picture(g, pos, r=4):
    x, y, z = pos
    rows = []
    for yy in range(y + 2, y - 2, -1):
        rows.append('      y=%d%s' % (yy, ' <' if yy == y else ''))
        for zz in range(z - r, z + r + 1):
            rows.append('      ' + ''.join(('[' if (xx, zz) == (x, z) else ' ') + glyph(g.get(xx, yy, zz)) for xx in range(x - r, x + r + 1)))
    return chr(10).join(rows)


def seeds_of(d, cells):
    """歩き始め: 印 (marks) と、場所の外周 (meta.rect か、建物の範囲の 2 外) の地面"""
    seeds = []
    for v in d.get('marks', {}).values():
        if isinstance(v, list) and len(v) >= 3 and all(isinstance(t, int) for t in v[:3]):
            seeds.append(tuple(v[:3]))
    xs = [k[0] for k in cells]
    zs = [k[2] for k in cells]
    rect = d.get('meta', {}).get('rect')
    if 'bottom' in d.get('meta', {}) and not d.get('meta', {}).get('level'):
        return seeds  # 淵の縦穴: 外周は岩
    x0, z0, x1, z1 = rect if rect else (min(xs) - 2, min(zs) - 2, max(xs) + 2, max(zs) + 2)
    for x in range(x0, x1 + 1):
        seeds += [(x, 0, z0), (x, 0, z1)]
    for z in range(z0, z1 + 1):
        seeds += [(x0, 0, z), (x1, 0, z)]
    return seeds


def allowed(label, kind, pos):
    for part, k, box, _ in ALLOW:
        if part in label and k == kind and (box is None or all(box[i] <= pos[i] <= box[i + 3] for i in range(3))):
            return True
    return False


def main():
    args = sys.argv[1:]
    verbose = '-v' in args
    as_json = '--json' in args
    show = '--show' in args
    sbds = [args[i + 1] for i, a in enumerate(args) if a == '--sbd']
    names = [a for i, a in enumerate(args) if not a.startswith('-') and (i == 0 or args[i - 1] != '--sbd')]
    findings = []
    grids = {}
    skipped = Counter()
    for path in sorted(glob.glob(os.path.join(OPENING, '*.json'))):
        label = os.path.basename(path)[:-5]
        if names and not any(n in label for n in names):
            continue
        if label.startswith('capital_style_'):
            continue  # 作風の見本 (tools/capital/style_roughs.py。ゲームでは建てない)
        d, cells = load_blueprint(path)
        if not any(is_stair(s) for s in cells.values()):
            continue
        meta = d.get('meta', {})
        # 地面: 平らにする場所 (level) は y -1 以下が地面。空気も書く大きな設計図 (淵の縦穴) は、書いていない所が岩
        ground = 10 ** 6 if 'bottom' in meta and not meta.get('level') else -1
        g = Grid(cells, ground)
        grids[label] = g
        for f in check(g, label, seeds_of(d, cells)):
            if allowed(*f[:3]):
                skipped[f[1]] += 1
            else:
                findings.append(f)
    for path in sbds:
        label = os.path.basename(path)
        for f in check(Grid(load_sbd(path)), label):
            if allowed(*f[:3]):
                skipped[f[1]] += 1
            else:
                findings.append(f)
    if as_json:
        print(json.dumps([{'structure': a, 'kind': b, 'pos': c, 'msg': m} for a, b, c, m in findings], ensure_ascii=False))
    else:
        by = defaultdict(list)
        for f in findings:
            by[(f[0], f[1])].append(f)
        for (lab, kind), lst in sorted(by.items()):
            print('%s %s x%d' % (lab, kind, len(lst)))
            for _, _, pos, msg in (lst if verbose else lst[:8]):
                print('   %s %s' % (pos, msg))
                if show and lab in grids:
                    print(picture(grids[lab], pos))
        print('check_stairs: %d findings%s' % (len(findings), (' (allowed: %s)' % dict(skipped)) if skipped else ''))
    sys.exit(1 if findings else 0)


if __name__ == '__main__':
    main()
