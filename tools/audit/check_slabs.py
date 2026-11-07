# -*- coding: utf-8 -*-
"""この Mod が建てる建物の半ブロック (ハーフブロック) が、ほかのブロックに本当に接しているかを調べる (ゲームは動かさない)。

    python tools/audit/check_slabs.py                  # data/skyabyss/opening/*.json の設計図を全部
    python tools/audit/check_slabs.py capital shrine_  # 名前の一部で絞る
    ... -v 指摘を全部出す (既定は設計図ごとに 6 件まで) / --fix 直して書き戻す (下の直し方)

2026-11-07 の声「ハーフブロックもおかしいかも。ブロックに接していないときある。判定が一ブロックだから、検知しにくい」。
マスの隣どうしでも、半ブロックは半分の高さしか埋めない。下付きの半ブロック (下半分) の上の面、上付き (上半分) の下の面は、マスの境に届かない。
ここでは形を半分ずつで見て、境の面で本当に重なる相手があるかを調べる:
  SLAB_FLOATING  どの面でも何にも接していない半ブロック (宙に浮いて見える)
  SLAB_GAP       下付きの半ブロックの真下が空き、または下付きの半ブロック (半分の隙間)。横で支えていても、下に隙間が見える
                 (横の壁・柵に掛かった棚・庇として置いた物は、横に接していれば FLOATING にはならない)
直し方 (--fix): FLOATING は下付き↔上付きを入れ替えて接するならそうし、だめなら丸ごとのブロック (同じ石の double) にする。
                SLAB_GAP は、下の半ブロックが下付きなら上の半ブロックを下の物と合わせて double にする…のではなく、下の段を double (丸ごと) にする。
許す例は ALLOW に理由つきで書く。指摘が 1 つでもあれば終了コード 1。
"""
from __future__ import annotations

import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_stairs import OPENING, load_blueprint, name, props, top  # noqa: E402

SIDES = ((1, 0), (-1, 0), (0, 1), (0, -1))
# 当たりの細い物 (柱の真ん中だけ。上下でだけ接する)
POST = ('_fence', '_wall', '_pane', 'iron_bars', 'chain', 'lightning_rod', 'end_rod')
# 接しても形の見えない物 (液体・草花など。top() が 0 の物) は「無い」として見る

ALLOW = [
    # (設計図の名前の一部, 種類, 理由)
]


def hangs(s):
    """下に吊った物 (吊りランタン・鎖)。半ブロックの下の面に掛かっている"""
    n = name(s)
    return (n in ('lantern', 'soul_lantern') and props(s).get('hanging') == 'true') or n == 'chain'


def halves(s):
    """その物がマスの下半分・上半分を (横から見て) 埋めているか: (lower, upper)。細い柱は (False, False)"""
    if s is None:
        return (False, False)
    n = name(s)
    if top(s) == 0:
        return (False, False)
    p = props(s)
    if n.endswith('_slab'):
        t = p.get('type', 'bottom')
        return (t != 'top', t != 'bottom')
    if n.endswith('_stairs'):
        # 段は、置いた半分は丸ごと、もう半分は奥の側だけ。横の面ではどちらの半分も少しは接する
        return (True, True)
    if n.endswith('_trapdoor'):
        if p.get('open') == 'true':
            return (False, False)
        return (p.get('half') != 'top', p.get('half') == 'top')
    if any(k in n for k in POST) or n.endswith(('_carpet', '_door', 'lantern', 'candle', 'torch')):
        return (False, False)
    return (True, True)


def reaches_up(s):
    """マスの上の面 (y + 1) に届くか (上に置いた物を支える)"""
    if s is None or top(s) == 0:
        return False
    if hangs(s):
        return True
    n = name(s)
    p = props(s)
    if n.endswith('_slab'):
        return p.get('type', 'bottom') != 'bottom'
    if n.endswith('_trapdoor'):
        return p.get('open') != 'true' and p.get('half') == 'top'
    if n.endswith(('_carpet', 'candle', 'torch')) or n in ('lantern', 'soul_lantern'):
        return False
    return top(s) >= 1


def reaches_down(s):
    """マスの下の面 (y) に届くか (下の物に乗っている・吊られている)"""
    if s is None or top(s) == 0:
        return False
    n = name(s)
    p = props(s)
    if n.endswith('_slab'):
        return p.get('type', 'bottom') != 'top'
    if n.endswith('_trapdoor'):
        return p.get('open') != 'true' and p.get('half') != 'top'
    return True


class Cells(dict):
    """設計図のマス。ground 以下で書いていない所は地面 (固い。check_stairs と同じ決まり)"""
    ground = -1

    def get(self, k, default=None):
        v = dict.get(self, k)
        if v is None and k[1] <= self.ground:
            return 'minecraft:stone'
        return v


def check(cells, label, verbose=False):
    out = []
    for (x, y, z), s in list(cells.items()):
        n = name(s)
        if not n.endswith('_slab'):
            continue
        t = props(s).get('type', 'bottom')
        if t == 'double':
            continue
        below = cells.get((x, y - 1, z))
        above = cells.get((x, y + 1, z))
        side = False
        for dx, dz in SIDES:
            lo, hi = halves(cells.get((x + dx, y, z + dz)))
            if (t == 'bottom' and lo) or (t == 'top' and hi):
                side = True
                break
        if t == 'bottom':
            vert = reaches_up(below)
        else:
            vert = reaches_down(above)
        if not side and not vert:
            out.append(('SLAB_FLOATING', (x, y, z), s))
        elif t == 'bottom' and not vert and below is not None and top(below) > 0 and not hangs(below):
            # 下に物はあるが上の面に届かない (下付きの半ブロックの上など): 半分の隙間が見える
            out.append(('SLAB_GAP', (x, y, z), s))
    out = [o for o in out if not any(a[0] in label and a[1] == o[0] for a in ALLOW)]
    return out


def near(cells, x, y, z):
    c = Cells({k: v for k, v in cells.items() if abs(k[0] - x) <= 1 and abs(k[1] - y) <= 1 and abs(k[2] - z) <= 1})
    c.ground = cells.ground
    return c


LOG = []


def fix(cells, found):
    """直す順: 下の置きランタンを吊りにする → 上下を入れ替える → 丸ごと (double) にする → それでも何にも接しなければ取り除く (崩れの屑)"""
    n = 0
    for kind, (x, y, z), s in found:
        if cells.get((x, y, z)) != s:
            continue
        t = props(s).get('type', 'bottom')
        below = cells.get((x, y - 1, z))
        if kind == 'SLAB_GAP':
            if below is not None and name(below).endswith('_slab') and props(below).get('type') == 'bottom':
                cells[(x, y - 1, z)] = below.replace('type=bottom', 'type=double')
                n += 1
            continue
        if bridge(cells, x, y, z):
            LOG.append(('bridge', (x, y, z), s))
            n += 1
            continue
        if t == 'top' and ledge(cells, x, y, z, s):
            LOG.append(('ledge', (x, y, z), s))
            n += 1
            continue
        if t == 'bottom' and below is not None and name(below) in ('lantern', 'soul_lantern') and props(below).get('hanging') == 'false':
            cells[(x, y - 1, z)] = below.replace('hanging=false', 'hanging=true')
            LOG.append(('hang lantern', (x, y, z), s))
            n += 1
            continue
        for cand in (s.replace('type=' + t, 'type=' + ('top' if t == 'bottom' else 'bottom')), s.replace('type=' + t, 'type=double'), None):
            if cand is None:
                del cells[(x, y, z)]
                LOG.append(('remove', (x, y, z), s))
                break
            cells[(x, y, z)] = cand
            if not any(o[1] == (x, y, z) for o in check(near(cells, x, y, z), '')) and not floating_alone(cells, x, y, z):
                break
        n += 1
    return n


def bridge(cells, x, y, z):
    """横の空きの一マスを、その先の丸ごとのブロックと同じ物で埋めて、半ブロックを壁・岩につなぐ (岩を巻く段など)"""
    from check_stairs import full_cube
    for dx, dz in SIDES:
        n = (x + dx, y, z + dz)
        if cells.get(n) is not None and top(cells.get(n)) > 0:
            continue
        for ex, ey, ez in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
            m = (n[0] + ex, n[1] + ey, n[2] + ez)
            if m == (x, y, z):
                continue
            v = cells.get(m)
            if v is not None and full_cube(v) and 'stone' != name(v) or (v == 'minecraft:stone' and dict.get(cells, m) is not None):
                cells[n] = v
                return True
    return False


def ledge(cells, x, y, z, s, reach=6):
    """上付きの半ブロック (歩く段) が岩・壁から離れて浮いているとき、いちばん近い丸ごとのブロック (同じ高さ) まで、
    同じ半ブロックの張り出しを伸ばしてつなぐ (岩を巻く段の岩が細って離れたところ)"""
    from check_stairs import full_cube
    best = None
    for dx in range(-reach, reach + 1):
        for dz in range(-reach, reach + 1):
            v = dict.get(cells, (x + dx, y, z + dz))
            if v is not None and full_cube(v):
                d = abs(dx) + abs(dz)
                if best is None or d < best[0]:
                    best = (d, dx, dz)
    if best is None:
        return False
    _, dx, dz = best
    cx, cz = x, z
    # 4 つながりの道 (x を先に、次に z) で、岩の手前まで
    while (cx, cz) != (x + dx, z + dz):
        if cx != x + dx:
            cx += 1 if dx > 0 else -1
        else:
            cz += 1 if dz > 0 else -1
        if (cx, cz) == (x + dx, z + dz):
            break
        v = cells.get((cx, y, cz))
        if v is not None and top(v) > 0:
            break
        cells[(cx, y, cz)] = s
    return True


def floating_alone(cells, x, y, z):
    """丸ごと・入れ替えにしても、どの面にも何も無い (宙の屑)"""
    for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
        v = cells.get((x + dx, y + dy, z + dz))
        if v is not None and top(v) > 0:
            return False
    return True


def fix_cells(cells, meta=None):
    """生成スクリプトから: 設計図のマス (dict) の半ブロックを直して返す (build_poi_places.py が書く前に呼ぶ)"""
    meta = meta or {}
    c = Cells(cells)
    c.ground = 10 ** 6 if 'bottom' in meta and not meta.get('level') else -1
    for _ in range(3):
        found = check(c, '')
        if not found:
            break
        fix(c, found)
    LOG.clear()
    return dict(c)


def save(path, d, cells):
    """直したマスを blocks に書き戻す (runs・boxes の上に後から書いたものが勝つ)"""
    pal = d['palette']
    index = {s: i for i, s in enumerate(pal)}
    orig = load_blueprint(path)[1]
    extra = []
    gone = {k: 'minecraft:air' for k in orig if k not in cells}
    for k, s in list(cells.items()) + list(gone.items()):
        if orig.get(k) != s:
            if s not in index:
                index[s] = len(pal)
                pal.append(s)
            extra.append([k[0], k[1], k[2], index[s]])
    if not extra:
        return 0
    if 'blocks' in d and not d.get('runs') and not d.get('boxes'):
        pos = {(b[0], b[1], b[2]): i for i, b in enumerate(d['blocks'])}
        for e in extra:
            i = pos.get((e[0], e[1], e[2]))
            if i is None:
                d['blocks'].append(e)
            else:
                d['blocks'][i] = e
    else:
        d.setdefault('blocks', []).extend(extra)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(d, f, ensure_ascii=False, separators=(',', ':'))
    return len(extra)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('-')]
    verbose = '-v' in sys.argv
    do_fix = '--fix' in sys.argv
    total = 0
    for path in sorted(glob.glob(os.path.join(OPENING, '*.json'))):
        label = os.path.basename(path)[:-5]
        if args and not any(a in label for a in args):
            continue
        try:
            d, cells = load_blueprint(path)
        except (KeyError, ValueError, TypeError):
            continue
        if label.startswith('capital_style_') or label.startswith('rough_'):
            continue
        meta = d.get('meta', {})
        cells = Cells(cells)
        cells.ground = 10 ** 6 if 'bottom' in meta and not meta.get('level') else -1
        found = check(cells, label, verbose)
        if not found:
            continue
        if do_fix:
            for _ in range(3):
                fix(cells, found)
                found = check(cells, label)
                if not found:
                    break
            wrote = save(path, d, cells)
            for op, pos, st in LOG:
                print(f'    {op} {pos} {name(st)}[{props(st).get("type")}]')
            LOG.clear()
            print(f'{label}: fixed, wrote {wrote} cells, left {len(found)}')
        total += len(found)
        if found and not do_fix:
            kinds = {}
            for k, pos, s in found:
                kinds[k] = kinds.get(k, 0) + 1
            print(f'{label}: ' + ', '.join(f'{k} {v}' for k, v in sorted(kinds.items())))
            for k, pos, s in (found if verbose else found[:6]):
                print(f'    {k} {pos} {name(s)}[{props(s).get("type")}]')
    print(f'total {total}')
    sys.exit(1 if total else 0)


if __name__ == '__main__':
    main()
