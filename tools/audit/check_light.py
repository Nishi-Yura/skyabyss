# -*- coding: utf-8 -*-
"""設計図の上で、街路・広場・階段の明るさを調べる (ゲームは動かさない。夜の見え方の目安)。

    python tools/audit/check_light.py                 # 都 (capital) の街路・広場・階段
    python tools/audit/check_light.py capital -v      # 名前の一部で絞る / 暗い所を全部出す

見るもの: 都の壁の内で、地面か段の上 (足が y <= 6)・空の見える所・石畳か階段の床の、足の高さの「ブロックの明るさ」。
  明るさ = 灯りの光源から、空気や半ブロックを 1 マスずつ減りながら届く分 (丸ごとのブロックは通さない)。
  光源: 吊り灯り 15、魂のランタン 10、魂の松明 10、焚き火 (点いて) 15、魂の焚き火 10、点いた蝋燭 (本数 x 3)、
        点いた竈・燻し窯 13、嘆きの黒曜石 10、紫水晶 5 など
  空の明かりは数えない (月の無い晩の、いちばん暗い見え方を見る)。
閾値 DARK (既定 4): これより暗い歩く所を「暗い」とする。
plan_wall_torches() は、暗い所の近くの壁に魂の松明を足す (town.py の street_light から使う。歩く道はふさがない)。
数を出す (-v で場所を全部。既定は場所をまとめて 12 件まで)。終了コードは 0 (参考の数)。
"""
from __future__ import annotations

import os
import sys
from collections import defaultdict, deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import check_stairs as cs  # noqa: E402

OPENING = cs.OPENING
DARK = 4        # 歩く所の明るさがこれ未満なら暗い (空の明かりは数えない)
WALL = 97       # 都の壁の内 (|x|, |z| <= 97)。壁の外の野原は見ない
GROUND_TOP = 6  # 足がこれ以下 (地面・段の上)。屋根の上は道ではない
PAVE = ('stone_bricks', 'andesite', 'polished_andesite', 'cobblestone', 'mossy_cobblestone', 'mossy_stone_bricks',
        'cracked_stone_bricks', 'smooth_stone', 'gravel', 'stone_brick')
TORCH_LEVEL = 10  # 魂の松明 (壁の松明)
NB = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
AIR = (None, 'air', 'cave_air', 'void_air')

SOURCES = {  # 名前 -> 光の強さ (lit や本数で変わるものは source_level で決める)
    'lantern': 15, 'soul_lantern': 10, 'soul_wall_torch': 10, 'soul_campfire': 10, 'torch': 14, 'wall_torch': 14,
    'crying_obsidian': 10, 'sculk_catalyst': 6, 'amethyst_cluster': 5, 'small_amethyst_bud': 1,
    'glowstone': 15, 'sea_lantern': 15, 'shroomlight': 15, 'jack_o_lantern': 15, 'beacon': 15, 'end_rod': 14, 'lava': 15,
}


def source_level(s):
    """ブロックの光の強さ (0 = 光らない)"""
    n = cs.name(s)
    p = cs.props(s)
    if n in ('campfire', 'soul_campfire'):
        if p.get('lit') != 'true':
            return 0
        return 15 if n == 'campfire' else 10
    if n == 'candle' or n.endswith('_candle'):
        if p.get('lit') != 'true':
            return 0
        return 3 * int(p.get('candles', '1'))
    if n in ('furnace', 'smoker', 'blast_furnace'):
        return 13 if p.get('lit') == 'true' else 0
    return SOURCES.get(n, 0)


def spread(cells, lm, p, lv):
    """光源 p (強さ lv) から広げる。既に明るい所は上書きしない (最大をとる)"""
    if lm.get(p, 0) < lv:
        lm[p] = lv
    todo = deque([(p, lv)])
    while todo:
        q, lq = todo.popleft()
        if lm.get(q, 0) != lq or lq <= 1:
            continue
        x, y, z = q
        for dx, dy, dz in NB:
            n = (x + dx, y + dy, z + dz)
            s = cells.get(n)
            if s is not None and cs.full_cube(s):
                continue
            if lm.get(n, 0) < lq - 1:
                lm[n] = lq - 1
                todo.append((n, lq - 1))


def light_map(cells):
    """すべての光源から広がる明るさ (cell -> level)"""
    lm = {}
    for p, s in cells.items():
        lv = source_level(s)
        if lv > 0:
            spread(cells, lm, p, lv)
    return lm


def street(s):
    """街路・広場の石の床 (石畳・敷石) か、階段・半ブロック (段として歩く)"""
    n = cs.name(s)
    return n in PAVE or n.endswith(('_stairs', '_slab'))


def street_feet(cells):
    """都の街路・広場・階段の歩く所 (足の高さの (x, y, z))。空の見える所だけ (屋根の下の床は数えない)"""
    cover = {}  # (x, z) -> いちばん高い丸ごとのブロックの y
    for (x, y, z), s in cells.items():
        if cs.full_cube(s) and y > cover.get((x, z), -10 ** 9):
            cover[(x, z)] = y
    g = cs.Grid(cells, -1)
    out = []
    for (x, y, z), s in cells.items():
        if max(abs(x), abs(z)) > WALL or y + 1 > GROUND_TOP:
            continue
        if (x, z) in cover and cover[(x, z)] > y:
            continue
        if s in AIR or not street(s):
            continue
        if cs.standable(g, x, y + 1, z) and cs.feet(g, x, y + 1, z) is not None:
            out.append((x, y + 1, z))
    return out


def dark_spots(lm, feet, target=DARK):
    return [p for p in feet if lm.get(p, 0) < target]


CORE = 60  # 町の中心 (|x|, |z| <= 60): 広場・北の段・大通り・施設の前。ここを先に明るくする (外の輪の道は見るだけ)


def in_core(p):
    return max(abs(p[0]), abs(p[2])) <= CORE


POST_COST = 4   # 柱は道をふさぐので、壁の松明より選ばれにくい
POST_FENCE = 'dark_oak_fence[east=false,north=false,south=false,west=false,waterlogged=false]'


def post_ok(cells, c):
    """柱を立ててよい所: 床は固く、柱とその上 (c の上 2 つ) が空き、四方の三つ以上が空いている (道の真ん中ではない)"""
    if cs.top(cells.get((c[0], c[1] - 1, c[2]))) < 1:
        return False
    if any(cells.get((c[0], c[1] + k, c[2])) not in AIR for k in (0, 1, 2)):
        return False
    free = 0
    for ex, ez in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        n = cells.get((c[0] + ex, c[1], c[2] + ez))
        if n in AIR or (n is not None and cs.top(n) < 0.5):
            free += 1
    return free >= 3


def plan_wall_torches(cells, target=DARK, max_add=3000, reach=8, log=None, region=in_core):
    """暗い歩く所の近くの壁に、魂の松明を足す (cells を書き換える)。
    いちばん暗い所を選び、その近く (reach の中) で、壁に付けられる空きの一マスのうち、
    いちばん多くの暗い所を (松明の届く中で) 照らす所を選ぶ。松明は当たりが無いので、道はふさがない。
    返り値 = 足した所の一覧 [(x, y, z, facing)]。壁の近くに置けず届かない暗い所は諦める"""
    lm = light_map(cells)
    feet = street_feet(cells)
    dark = set(p for p in dark_spots(lm, feet, target) if region(p))
    ball = [(a, b, c) for a in range(-6, 7) for b in range(-6, 7) for c in range(-6, 7) if abs(a) + abs(b) + abs(c) <= 6]
    placed = []
    hopeless = set()
    while dark and len(placed) < max_add:
        todo = [p for p in sorted(dark, key=lambda p: (lm.get(p, 0), p)) if p not in hopeless]
        if not todo:
            break
        d = todo[0]
        best = None
        for dx in range(-reach, reach + 1):
            for dy in range(-3, 4):
                for dz in range(-reach, reach + 1):
                    c = (d[0] + dx, d[1] + dy, d[2] + dz)
                    if not region(c) or c[1] < 1 or c[1] > 6 or cells.get(c) not in AIR or cells.get((c[0], c[1] + 1, c[2])) not in AIR:
                        continue
                    face = None
                    for f, (ex, ez) in (('north', (0, -1)), ('south', (0, 1)), ('west', (-1, 0)), ('east', (1, 0))):
                        w = cells.get((c[0] + ex, c[1], c[2] + ez))
                        if w is not None and cs.full_cube(w):
                            face = f
                            break
                    score = sum(1 for o in ball if (c[0] + o[0], c[1] + o[1], c[2] + o[2]) in dark)
                    if face is not None:
                        kind, sc = 'torch', score
                    else:
                        # 壁の無い所は、柱の上に灯り (道の真ん中をふさがないよう、四方の三つは空いている所だけ)
                        if not post_ok(cells, c):
                            continue
                        kind, sc = 'post', score - POST_COST
                    if not score:
                        continue
                    key = (sc, -(abs(dx) + abs(dz)))
                    if best is None or key > best[0]:
                        best = (key, c, kind, face)
        if best is None:
            hopeless.add(d)
            continue
        _, c, kind, face = best
        if kind == 'torch':
            # 松明は壁から外へ向く (facing は壁の逆側)。壁は face の向きにある
            facing = {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}[face]
            cells[c] = 'soul_wall_torch[facing=%s]' % facing
            placed.append((c[0], c[1], c[2], 'torch:' + facing))
            spread(cells, lm, c, TORCH_LEVEL)
        else:
            cells[c] = POST_FENCE
            cells[(c[0], c[1] + 1, c[2])] = POST_FENCE
            cells[(c[0], c[1] + 2, c[2])] = 'lantern[hanging=false,waterlogged=false]'
            placed.append((c[0], c[1], c[2], 'post'))
            spread(cells, lm, (c[0], c[1] + 2, c[2]), 15)
        dark = set(q for q in dark if lm.get(q, 0) < target)
    if log:
        log('plan_wall_torches: %d torches, %d dark left, %d hopeless' % (len(placed), len(dark), len(hopeless)))
    return placed


def main():
    args = sys.argv[1:]
    verbose = '-v' in args
    names = [a for a in args if not a.startswith('-')]
    total_dark = 0
    core_dark = 0
    for path in sorted(os.path.join(OPENING, f) for f in os.listdir(OPENING) if f.endswith('.json')):
        label = os.path.basename(path)[:-5]
        if names and not any(n in label for n in names):
            continue
        if 'capital' not in label or label.startswith('capital_style_'):
            continue
        d, cells = cs.load_blueprint(path)
        lm = light_map(cells)
        feet = street_feet(cells)
        dark = dark_spots(lm, feet)
        total_dark += len(dark)
        core_dark += sum(1 for p in dark if in_core(p))
        print('%s: 街路・広場・階段の歩く所 %d か所、暗い (明るさ < %d) %d か所' % (label, len(feet), DARK, len(dark)))
        bins = defaultdict(list)
        for p in dark:
            bins[(p[0] // 8, p[2] // 8)].append(p)
        for (bx, bz), lst in sorted(bins.items(), key=lambda kv: -len(kv[1]))[: (10 ** 6 if verbose else 12)]:
            xs = [p[0] for p in lst]
            zs = [p[2] for p in lst]
            print('   x %d..%d z %d..%d  %d か所 (最も暗い明るさ %d)' % (min(xs), max(xs), min(zs), max(zs), len(lst), min(lm.get(p, 0) for p in lst)))
    print('check_light: %d dark walkable spots (中心 |x|,|z| <= %d の内 %d 所)' % (total_dark, CORE, core_dark))
    sys.exit(0)  # 参考の数 (街路を全部明るくはしない。tools/capital/town.py の street_light が中心の暗い所に松明・柱を足す)


if __name__ == '__main__':
    main()
