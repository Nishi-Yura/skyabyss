# -*- coding: utf-8 -*-
"""都の建築の部品集 (docs/design/33 の 2)。

座標: x = 東、z = 南、y = 上。y=0 が地面の上の最初の段 (地面の上面は y=-1)。
Vox は (x, y, z) → ブロックの状態の文字列。部品はみな Vox と作風 (Style) を受け取って書き込む。
最後に connect() でフェンス・板ガラス・鉄格子・塀のつながりを周りから決める (設計図は隣の更新なしで置かれるので)。
"""
from __future__ import annotations

import json
import math
import random

M = 'minecraft:'

# ---------------------------------------------------------------- 素材


class Mat:
    """石・木などの一族。full = 一つのブロック、stem = 階段・半ブロック・塀の名前の頭"""

    def __init__(self, full, stem=None, wall=None, stairs=True, slab=True):
        self.full = full
        self.stem = stem
        self.wall_name = wall
        self.has_stairs = stairs and stem is not None
        self.has_slab = slab and stem is not None

    def stairs(self, facing, half='bottom', shape='straight'):
        return '%s_stairs[facing=%s,half=%s,shape=%s,waterlogged=false]' % (self.stem, facing, half, shape)

    def slab(self, t='bottom'):
        return '%s_slab[type=%s,waterlogged=false]' % (self.stem, t)

    def wall(self):
        return self.wall_name or 'stone_brick_wall'


def W(name):
    """塀・フェンス・板ガラスなど、つながりを connect() が決めるもの"""
    return name


class Style:
    def __init__(self, key, **kw):
        self.key = key
        self.__dict__.update(kw)


# 作風 A「白の鐘都」: 白い石 (閃緑岩・石英)、青黒い瓦の尖塔、緑青の銅、苔と割れで古び
STYLE_A = Style(
    'a',
    base=Mat('stone_bricks', 'stone_brick', 'stone_brick_wall'),
    base2=['mossy_stone_bricks', 'cracked_stone_bricks'],
    wall=Mat('polished_diorite', 'polished_diorite', 'diorite_wall'),
    wall_mix=['diorite', 'calcite', 'polished_diorite', 'polished_diorite', 'polished_diorite'],
    trim=Mat('smooth_quartz', 'smooth_quartz', 'diorite_wall'),
    trim2='quartz_bricks',
    pillar='quartz_pillar[axis=y]',
    roof=Mat('deepslate_tiles', 'deepslate_tile', 'deepslate_tile_wall'),
    roof_edge=Mat('smooth_quartz', 'smooth_quartz'),
    accent=Mat('oxidized_cut_copper', 'oxidized_cut_copper'),
    accent2='weathered_cut_copper',
    wood='spruce', log='spruce_log', dark='dark_oak',
    glass='glass_pane', frame='spruce_trapdoor',
    railing='diorite_wall', fence='spruce_fence',
    pave=[('stone_bricks', 5), ('polished_andesite', 3), ('andesite', 2), ('cobblestone', 1), ('mossy_stone_bricks', 1)],
    pave_trim='smooth_stone', pave_inlay=['polished_diorite', 'calcite'],
    flowers=['cornflower', 'azure_bluet', 'oxeye_daisy', 'lily_of_the_valley', 'white_tulip', 'allium'],
    leaves='spruce_leaves', trunk='spruce_log', leaves2='birch_leaves', trunk2='birch_log',
    lamp='lantern', banner_wool='light_blue_wool',
)

# 作風 B「灰石と緋の屋根」: 灰色の石レンガと黒っぽい石の土台、白い石の縁、濃い木の梁、緋色の瓦
STYLE_B = Style(
    'b',
    base=Mat('deepslate_bricks', 'deepslate_brick', 'deepslate_brick_wall'),
    base2=['cracked_deepslate_bricks', 'deepslate_tiles'],
    wall=Mat('stone_bricks', 'stone_brick', 'stone_brick_wall'),
    wall_mix=['stone_bricks', 'stone_bricks', 'stone_bricks', 'cracked_stone_bricks', 'andesite', 'mossy_stone_bricks'],
    trim=Mat('polished_diorite', 'polished_diorite', 'diorite_wall'),
    trim2='chiseled_stone_bricks',
    pillar='dark_oak_log[axis=y]',
    roof=Mat('red_nether_bricks', 'red_nether_brick', 'red_nether_brick_wall'),
    roof_edge=Mat('polished_diorite', 'polished_diorite'),
    accent=Mat('polished_blackstone_bricks', 'polished_blackstone_brick', 'polished_blackstone_brick_wall'),
    accent2='chiseled_polished_blackstone',
    wood='dark_oak', log='dark_oak_log', dark='dark_oak',
    glass='gray_stained_glass_pane', frame='dark_oak_trapdoor',
    railing='stone_brick_wall', fence='dark_oak_fence',
    pave=[('stone_bricks', 4), ('andesite', 3), ('cobblestone', 2), ('gravel', 1), ('polished_andesite', 2), ('mossy_cobblestone', 1)],
    pave_trim='polished_andesite', pave_inlay=['polished_diorite', 'polished_andesite'],
    flowers=['red_tulip', 'poppy', 'oxeye_daisy', 'azure_bluet', 'white_tulip', 'dandelion'],
    leaves='oak_leaves', trunk='oak_log', leaves2='spruce_leaves', trunk2='spruce_log',
    lamp='lantern', banner_wool='red_wool',
)

# 作風 C「砂岩と瓦の段丘」: 白い砂岩と石英の柱、橙の瓦、青い硝子と銅の飾り、緑の多い段々の都
STYLE_C = Style(
    'c',
    base=Mat('cut_sandstone', 'sandstone', 'sandstone_wall'),
    base2=['chiseled_sandstone', 'sandstone'],
    wall=Mat('smooth_sandstone', 'smooth_sandstone', 'sandstone_wall'),
    wall_mix=['smooth_sandstone', 'smooth_sandstone', 'smooth_sandstone', 'cut_sandstone', 'birch_planks'],
    trim=Mat('smooth_quartz', 'smooth_quartz', 'diorite_wall'),
    trim2='chiseled_quartz_block',
    pillar='quartz_pillar[axis=y]',
    roof=Mat('bricks', 'brick', 'brick_wall'),
    roof_edge=Mat('smooth_quartz', 'smooth_quartz'),
    accent=Mat('prismarine_bricks', 'prismarine_brick'),
    accent2='dark_prismarine',
    wood='birch', log='stripped_birch_log', dark='spruce',
    glass='light_blue_stained_glass_pane', frame='birch_trapdoor',
    railing='sandstone_wall', fence='birch_fence',
    pave=[('smooth_sandstone', 4), ('cut_sandstone', 3), ('sandstone', 2), ('birch_planks', 1)],
    pave_trim='smooth_quartz', pave_inlay=['prismarine_bricks', 'smooth_quartz'],
    flowers=['red_tulip', 'orange_tulip', 'pink_tulip', 'allium', 'oxeye_daisy', 'poppy'],
    leaves='oak_leaves', trunk='oak_log', leaves2='azalea_leaves', trunk2='oak_log',
    lamp='lantern', banner_wool='cyan_wool',
)


# 壁の作り (二重壁の Body が使う): infill = 壁の地、band = 階の帯、pilaster = 柱型、corner = 隅の柱
STYLE_A.__dict__.update(
    infill='calcite', infill_var=0.06, infill_mix=['polished_diorite', 'diorite'],
    band='polished_diorite', pilaster='quartz_pillar[axis=y]', corner='quartz_bricks',
    shutters=False, shutter_wood='spruce', awning='oxidized_cut_copper', door_lamps=True,
    pot_flowers=['white_tulip', 'azure_bluet', 'cornflower', 'lily_of_the_valley', 'fern'],
    spire=Mat('deepslate_tiles', 'deepslate_tile', 'deepslate_tile_wall'),
    dome=Mat('oxidized_cut_copper', 'oxidized_cut_copper'))
STYLE_B.__dict__.update(
    infill='stone_bricks', infill_var=0.14, infill_mix=['cracked_stone_bricks', 'mossy_stone_bricks', 'andesite'],
    band='polished_andesite', pilaster='polished_diorite', corner='polished_andesite',
    shutters=True, shutter_wood='dark_oak', awning='dark_oak', door_lamps=True,
    pot_flowers=['red_tulip', 'poppy', 'oxeye_daisy', 'azure_bluet', 'fern'],
    spire=Mat('red_nether_bricks', 'red_nether_brick', 'red_nether_brick_wall'),
    dome=Mat('polished_blackstone_bricks', 'polished_blackstone_brick'))
STYLE_C.__dict__.update(
    infill='smooth_sandstone', infill_var=0.05, infill_mix=['cut_sandstone'],
    band='cut_sandstone', pilaster='quartz_pillar[axis=y]', corner='smooth_quartz',
    shutters=True, shutter_wood='spruce', awning='spruce', door_lamps=True,
    pot_flowers=['red_tulip', 'orange_tulip', 'pink_tulip', 'allium', 'azalea_bush'],
    spire=Mat('bricks', 'brick', 'brick_wall'),
    dome=Mat('oxidized_cut_copper', 'oxidized_cut_copper'))


# 都の大きな部品 (城壁・家の壁の揺れ・屋根の別色)
STYLE_A.__dict__.update(
    wall_main='polished_diorite', wall_mix2=['diorite', 'calcite', 'mossy_stone_bricks'],
    house_infills=['calcite', 'calcite', 'polished_diorite', 'smooth_quartz'],
    roof_alts=[Mat('polished_blackstone_bricks', 'polished_blackstone_brick'), Mat('oxidized_cut_copper', 'oxidized_cut_copper')])
STYLE_B.__dict__.update(
    wall_main='stone_bricks', wall_mix2=['cracked_stone_bricks', 'mossy_stone_bricks', 'andesite'],
    house_infills=['stone_bricks', 'stone_bricks', 'tuff_bricks', 'polished_andesite'],
    roof_alts=[Mat('nether_bricks', 'nether_brick', 'nether_brick_wall'), Mat('mud_bricks', 'mud_brick', 'mud_brick_wall')])
STYLE_C.__dict__.update(
    wall_main='cut_sandstone', wall_mix2=['sandstone', 'smooth_sandstone', 'chiseled_sandstone'],
    house_infills=['smooth_sandstone', 'smooth_sandstone', 'white_terracotta', 'calcite'],
    roof_alts=[Mat('polished_granite', 'polished_granite'), Mat('mud_bricks', 'mud_brick', 'mud_brick_wall')])


STYLE_A.__dict__.update(banner_color='light_blue', fence_rail='diorite_wall')
STYLE_B.__dict__.update(banner_color='red', fence_rail='dark_oak_fence')
STYLE_C.__dict__.update(banner_color='cyan', fence_rail='birch_fence')

STYLES = {'a': STYLE_A, 'b': STYLE_B, 'c': STYLE_C}

FACINGS = ('north', 'east', 'south', 'west')
DX = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
OPP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
CW = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}


# ---------------------------------------------------------------- 書き込み先


class Vox:
    def __init__(self, seed=1):
        self.b: dict[tuple[int, int, int], str] = {}
        self.marks: dict[str, list[int]] = {}
        self.fake_doors: list = []  # 入れない家の戸 (検査用)
        self.rng = random.Random(seed)

    # -- 基本
    def set(self, x, y, z, s, keep=False):
        if keep and (x, y, z) in self.b and self.b[(x, y, z)] != 'air':
            return
        self.b[(x, y, z)] = s

    def get(self, x, y, z):
        return self.b.get((x, y, z))

    def fill(self, x0, y0, z0, x1, y1, z1, s, keep=False):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, s, keep)

    def fill_mix(self, x0, y0, z0, x1, y1, z1, choices, keep=False):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, self.rng.choice(choices), keep)

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, 'air')

    def mark(self, name, x, y, z):
        self.marks[name] = [x, y, z]

    def seal(self, x0, z0, x1, z1, y0, filler='black_concrete', y1=None):
        """入れない家の中を埋める: 各列で一番上の物より下の、空き (空気・未設定) を filler に。窓の奥が暗く見え、中は見えない"""
        tops = {}
        for (x, y, z), s in self.b.items():
            if x0 <= x <= x1 and z0 <= z <= z1 and s != 'air' and y >= y0:
                if tops.get((x, z), -999) < y:
                    tops[(x, z)] = y
        for (x, z), yt in tops.items():
            for y in range(y0, yt if y1 is None else min(yt, y1 + 1)):
                s = self.b.get((x, y, z))
                if s is None or s == 'air':
                    self.b[(x, y, z)] = filler

    def pick(self, weighted):
        tot = sum(w for _, w in weighted)
        r = self.rng.uniform(0, tot)
        for s, w in weighted:
            r -= w
            if r <= 0:
                return s
        return weighted[-1][0]

    # -- 階段の形 (バニラの StairBlock.getStairsShape と同じ決め方)。設計図は隣の更新なしで置かれるので、角の内・外をここで決める
    def stair_shapes(self):
        CCW = {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'}

        def parse(s):
            if s is None or '_stairs[' not in s:
                return None
            props = dict(kv.split('=') for kv in s[s.index('[') + 1:-1].split(','))
            return props

        out = {}
        for (x, y, z), s in self.b.items():
            st = parse(s)
            if st is None:
                continue
            f, half = st['facing'], st['half']
            shape = 'straight'
            dx, dz = DX[f]
            fr = parse(self.b.get((x + dx, y, z + dz)))
            done = False
            if fr is not None and fr['half'] == half:
                f1 = fr['facing']
                if (f1 in ('north', 'south')) != (f in ('north', 'south')):
                    ox, oz = DX[OPP[f1]]
                    nb = parse(self.b.get((x + ox, y, z + oz)))
                    if nb is None or nb['facing'] != f or nb['half'] != half:
                        shape = 'outer_left' if f1 == CCW[f] else 'outer_right'
                        done = True
            if not done:
                bk = parse(self.b.get((x - dx, y, z - dz)))
                if bk is not None and bk['half'] == half:
                    f2 = bk['facing']
                    if (f2 in ('north', 'south')) != (f in ('north', 'south')):
                        ox, oz = DX[f2]
                        nb = parse(self.b.get((x + ox, y, z + oz)))
                        if nb is None or nb['facing'] != f or nb['half'] != half:
                            shape = 'inner_left' if f2 == CCW[f] else 'inner_right'
            if st['shape'] != shape:
                out[(x, y, z)] = s.replace('shape=' + st['shape'], 'shape=' + shape)
        self.b.update(out)
        return len(out)

    # -- 支え: 置いた灯り・鎖が、ゲームでは支えが無くて落ちる所を直す (立つ灯りは下に、吊る灯りと鎖は上に支えが要る)
    def supports(self):
        def name(s):
            return 'air' if s is None else s.split('[')[0].replace('minecraft:', '')

        def holds_below(s):
            # 立つ灯りの下: 丸ごとのブロック・上半分の半ブロック・上向きの階段・柵・塀・鎖・灯り
            n = name(s)
            if n == 'air' or any(t in n for t in ('carpet', 'pane', 'iron_bars', 'trapdoor', 'flower', 'potted', 'banner', 'sign', 'door', 'leaves', 'lantern', 'candle', 'pressure', 'tulip', 'grass', 'fern', 'rail', 'torch', 'water')):
                return n.endswith('_fence') or n.endswith('_wall') or n == 'chain'
            if n.endswith('_slab'):
                return 'type=bottom' not in s
            if n.endswith('_stairs'):
                return 'half=top' in s
            return True

        def holds_above(s):
            n = name(s)
            if n == 'air' or any(t in n for t in ('carpet', 'flower', 'potted', 'tulip', 'grass', 'fern', 'candle', 'pressure', 'rail', 'torch', 'water', 'lantern')):
                return False
            if n.endswith('_slab'):
                return 'type=top' not in s or True
            return True

        changed = 0
        for _ in range(2):
            out = {}
            for (x, y, z), s in self.b.items():
                n = name(s)
                if n in ('lantern', 'soul_lantern'):
                    hanging = 'hanging=true' in s
                    below = self.b.get((x, y - 1, z))
                    above = self.b.get((x, y + 1, z))
                    if hanging and not (holds_above(above) or name(above) == 'chain'):
                        out[(x, y, z)] = s.replace('hanging=true', 'hanging=false') if holds_below(below) else 'air'
                    elif not hanging and not holds_below(below):
                        out[(x, y, z)] = s.replace('hanging=false', 'hanging=true') if holds_above(above) else 'air'
                elif n == 'chain' and 'axis=y' in s:
                    above = self.b.get((x, y + 1, z))
                    if not (holds_above(above) or name(above) == 'chain'):
                        out[(x, y, z)] = 'air'
            for k, s in out.items():
                self.b[k] = s
            changed += len(out)
        return changed

    # -- つながり (フェンス・板ガラス・鉄格子・塀)
    def connect(self):
        self.stair_shapes()
        self.supports()
        def name(s):
            if s is None:
                return 'air'
            s = s.split('[')[0]
            return s[len(M):] if s.startswith(M) else s

        def kind(n):
            if n.endswith('_fence') and 'gate' not in n:
                return 'fence'
            if n.endswith('glass_pane') or n == 'iron_bars':
                return 'pane'
            if n.endswith('_wall') and 'sign' not in n and 'banner' not in n and 'torch' not in n:
                return 'wall'
            return None

        nonsolid = ('air', 'stairs', 'slab', 'lantern', 'torch', 'flower', 'potted', 'door', 'trapdoor', 'sign', 'carpet',
                    'leaves', 'chain', 'end_rod', 'lightning_rod', 'button', 'banner', 'campfire', 'bell', 'tablet', 'short_grass',
                    'fern', 'vine', 'sapling', 'bush', 'candle', 'pot', 'rail', 'ladder', 'water', 'tulip', 'poppy', 'daisy',
                    'bluet', 'allium', 'cornflower', 'lily', 'dandelion', 'orchid', 'rose', 'lilac', 'peony', 'gate', 'pressure',
                    'hopper', 'grindstone', 'anvil', 'lectern', 'cauldron', 'brewing', 'skull', 'head', 'scaffolding', 'snow',
                    'azalea', 'moss_carpet', 'pointed', 'amethyst_cluster', 'bed', 'barrel_')

        def solid(n):
            return not any(t in n for t in nonsolid) and kind(n) is None

        out = {}
        for (x, y, z), s in self.b.items():
            n = name(s)
            k = kind(n)
            if k is None:
                continue
            sides = {}
            for f, (dx, dz) in DX.items():
                m = name(self.b.get((x + dx, y, z + dz)))
                mk = kind(m)
                if k == 'fence':
                    c = mk == 'fence' or m.endswith('fence_gate') or solid(m)
                else:
                    c = mk in ('pane', 'wall') or solid(m) or (k == 'wall' and m.endswith('fence_gate'))
                sides[f] = c
            if k == 'wall':
                above = name(self.b.get((x, y + 1, z)))
                tall = above != 'air' and (solid(above) or kind(above) == 'wall')
                straight = (sides['north'] and sides['south'] and not sides['east'] and not sides['west']) or \
                           (sides['east'] and sides['west'] and not sides['north'] and not sides['south'])
                up = (not straight) or (above != 'air' and not tall)
                props = ['up=%s' % ('true' if up or tall and not straight else 'false')]
                for f in FACINGS:
                    props.append('%s=%s' % (f, ('tall' if tall else 'low') if sides[f] else 'none'))
                props.append('waterlogged=false')
            else:
                props = ['%s=%s' % (f, 'true' if sides[f] else 'false') for f in FACINGS]
                props.append('waterlogged=false')
            out[(x, y, z)] = '%s[%s]' % (n, ','.join(props))
        self.b.update(out)

    # -- 書き出し
    def runs(self, meta, extra_marks=None, keep_air=False):
        """詰めた形: 同じ y・z の x の並びを [y, z, x0, 番号, 長さ, …] に (-1 = 置かない)。
        空気は書かない (場所の範囲は建てる前に建物の高さまで片付くので)。keep_air = 空気も書く"""
        palette, pidx = [], {}
        rows: dict[tuple[int, int], dict[int, int]] = {}
        for (x, y, z), s in self.b.items():
            if s == 'air' and not keep_air:
                continue
            s2 = s if ':' in s else M + s
            if s2 not in pidx:
                pidx[s2] = len(palette)
                palette.append(s2)
            rows.setdefault((y, z), {})[x] = pidx[s2]
        out = []
        for (y, z) in sorted(rows):
            row = rows[(y, z)]
            xs = sorted(row)
            r = [y, z, xs[0]]
            cur, n = row[xs[0]], 1
            prev = xs[0]
            for x in xs[1:]:
                if x == prev + 1 and row[x] == cur:
                    n += 1
                else:
                    r += [cur, n]
                    if x > prev + 1:
                        r += [-1, x - prev - 1]
                    cur, n = row[x], 1
                prev = x
            r += [cur, n]
            out.append(r)
        marks = dict(self.marks)
        if extra_marks:
            marks.update(extra_marks)
        return {'palette': palette, 'runs': out, 'marks': marks, 'meta': meta}

    def blueprint(self, meta, extra_marks=None, drop_air_below=None):
        palette, pidx, blocks = [], {}, []
        for (x, y, z), s in sorted(self.b.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            if drop_air_below is not None and s == 'air' and y < drop_air_below:
                continue
            s2 = s if ':' in s else M + s
            if s2 not in pidx:
                pidx[s2] = len(palette)
                palette.append(s2)
            blocks.append([x, y, z, pidx[s2]])
        marks = dict(self.marks)
        if extra_marks:
            marks.update(extra_marks)
        return {'palette': palette, 'blocks': blocks, 'marks': marks, 'meta': meta}


def save(path, data):
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, separators=(',', ':'))


# ---------------------------------------------------------------- 小物


def flower_state(f):
    return f


def lamp_post(v: Vox, st: Style, x, y, z, h=4, arm=None):
    """塀の台 + フェンスの柱 + 灯り。arm = 腕を出す向き (吊り灯)"""
    v.set(x, y, z, st.railing)
    v.fill(x, y + 1, z, x, y + h - 1, z, st.fence)
    if arm:
        dx, dz = DX[arm]
        v.set(x, y + h, z, st.fence)
        v.set(x + dx, y + h, z + dz, st.fence)
        v.set(x + dx, y + h - 1, z + dz, 'lantern[hanging=true,waterlogged=false]')
    else:
        v.set(x, y + h, z, 'lantern[hanging=false,waterlogged=false]')


def double_lamp(v: Vox, st: Style, x, y, z, h=4, axis='x'):
    v.set(x, y, z, st.base.wall())
    v.fill(x, y + 1, z, x, y + h, z, st.fence)
    pairs = (('east', 'west') if axis == 'x' else ('north', 'south'))
    for f in pairs:
        dx, dz = DX[f]
        v.set(x + dx, y + h, z + dz, st.fence)
        v.set(x + dx, y + h - 1, z + dz, 'lantern[hanging=true,waterlogged=false]')
    v.set(x, y + h + 1, z, st.trim.slab('bottom') if st.trim.has_slab else st.fence)


def bush(v: Vox, x, y, z, leaves, r=1):
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            if abs(dx) + abs(dz) <= r + (1 if v.rng.random() < 0.4 else 0):
                v.set(x + dx, y, z + dz, '%s[persistent=true,distance=1,waterlogged=false]' % leaves, keep=True)
    v.set(x, y + 1, z, '%s[persistent=true,distance=1,waterlogged=false]' % leaves, keep=True)


def tree(v: Vox, x, y, z, trunk, leaves, h=5, r=2, cone=False):
    """木。幹の上の方に枝を 2〜3 本出し、枝先ごとに不揃いの葉の塊を置く (棒の先の玉にしない)"""
    rng = v.rng
    L = '%s[persistent=true,distance=1,waterlogged=false]' % leaves
    log = trunk if '[' in trunk else trunk + '[axis=y]'
    v.fill(x, y, z, x, y + h - 1, z, log)
    tips = [(x, y + h, z)]
    for k in range(rng.choice([2, 3])):
        dx, dz = rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)])
        yb = y + h - 2 - rng.randint(0, 1)
        bx, bz = x + dx, z + dz
        axis = 'x' if dx and not dz else ('z' if dz and not dx else 'y')
        v.set(bx, yb, bz, trunk.split('[')[0] + '[axis=%s]' % axis)
        tips.append((bx + dx, yb + 1, bz + dz))
    for (tx, ty, tz) in tips:
        rr = r + (0 if (tx, tz) == (x, z) else -1 + rng.random())
        for ddx in range(-r - 1, r + 2):
            for ddz in range(-r - 1, r + 2):
                for ddy in range(-1, 3):
                    d = (ddx * ddx + ddz * ddz) / (rr + 0.7) ** 2 + ((ddy - 0.5) / 1.6) ** 2
                    if d <= 1.0 and rng.random() > 0.12 * d:
                        v.set(tx + ddx, ty + ddy, tz + ddz, L, keep=True)


def tree_old(v: Vox, x, y, z, trunk, leaves, h=5, r=2, cone=False):
    """小さな木。cone = 針葉樹らしい円錐"""
    L = '%s[persistent=true,distance=1,waterlogged=false]' % leaves
    v.fill(x, y, z, x, y + h - 1, z, '%s[axis=y]' % trunk)
    if cone:
        top = y + h + 1
        for i, yy in enumerate(range(y + 2, top + 1)):
            rr = max(0, r - (i * (r + 1)) // max(1, (top - y - 2)))
            for dx in range(-rr, rr + 1):
                for dz in range(-rr, rr + 1):
                    if abs(dx) + abs(dz) <= rr + (0 if rr < 2 else 1) and (dx, dz) != (0, 0) or yy >= y + h:
                        if abs(dx) + abs(dz) <= rr + 1:
                            v.set(x + dx, yy, z + dz, L, keep=True)
        v.set(x, top + 1, z, L, keep=True)
    else:
        cy = y + h - 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                for dy in range(-1, 3):
                    d = dx * dx + dz * dz + (dy * 1.4) ** 2
                    if d <= (r + 0.6) ** 2 and v.rng.random() > 0.08:
                        v.set(x + dx, cy + dy, z + dz, L, keep=True)


def flowerbed(v: Vox, st: Style, x0, z0, x1, z1, y=0, edge=True, density=0.55, shrubs=0.12):
    """縁石つきの花壇。y-1 を草に、y に花と低木"""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            on_edge = x in (x0, x1) or z in (z0, z1)
            if edge and on_edge:
                v.set(x, y - 1, z, st.pave_trim)
                v.set(x, y, z, st.trim.slab('bottom') if st.trim.has_slab else st.pave_trim)
                continue
            v.set(x, y - 1, z, 'grass_block[snowy=false]')
            r = v.rng.random()
            if r < shrubs:
                v.set(x, y, z, '%s[persistent=true,distance=1,waterlogged=false]' % v.rng.choice([st.leaves, 'azalea_leaves', 'flowering_azalea_leaves']))
            elif r < shrubs + density:
                v.set(x, y, z, v.rng.choice(st.flowers))
            elif r < shrubs + density + 0.15:
                v.set(x, y, z, 'short_grass')


def planter(v: Vox, st: Style, x, y, z, tall=False):
    v.set(x, y, z, st.trim.full if not tall else st.trim2)
    v.set(x, y + 1, z, '%s[persistent=true,distance=1,waterlogged=false]' % v.rng.choice([st.leaves, 'azalea_leaves', 'flowering_azalea_leaves']))


def bench(v: Vox, st: Style, x, y, z, facing, length=3):
    """背もたれの向き facing (座る人は facing と反対を向く)"""
    ax = 'x' if facing in ('north', 'south') else 'z'
    for i in range(length):
        xx, zz = (x + i, z) if ax == 'x' else (x, z + i)
        v.set(xx, y, zz, '%s_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]' % (st.wood, facing))
    # 肘掛け
    if ax == 'x':
        v.set(x - 1, y, z, '%s_trapdoor[facing=west,half=bottom,open=true,waterlogged=false,powered=false]' % st.wood)
        v.set(x + length, y, z, '%s_trapdoor[facing=east,half=bottom,open=true,waterlogged=false,powered=false]' % st.wood)
    else:
        v.set(x, y, z - 1, '%s_trapdoor[facing=north,half=bottom,open=true,waterlogged=false,powered=false]' % st.wood)
        v.set(x, y, z + length, '%s_trapdoor[facing=south,half=bottom,open=true,waterlogged=false,powered=false]' % st.wood)


def pave(v: Vox, st: Style, x0, z0, x1, z1, y=-1, pattern=None):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            v.set(x, y, z, v.pick(st.pave))
    if pattern == 'border':
        for x in range(x0, x1 + 1):
            v.set(x, y, z0, st.pave_trim)
            v.set(x, y, z1, st.pave_trim)
        for z in range(z0, z1 + 1):
            v.set(x0, y, z, st.pave_trim)
            v.set(x1, y, z, st.pave_trim)


def rosette(v: Vox, st: Style, cx, cz, r, y=-1):
    """広場の床の模様 (同心の輪と放射)"""
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
            d = math.hypot(x - cx, z - cz)
            if d > r + 0.4:
                continue
            a = math.atan2(z - cz, x - cx)
            ring = int(d + 0.5)
            ray = int(((a + math.pi) / (2 * math.pi)) * 16 + 0.5) % 2 == 0
            if ring in (r, r - 3) or ring == 1:
                s = st.pave_trim
            elif ray and 2 < ring < r - 3:
                s = st.pave_inlay[0]
            elif ring == 0:
                s = st.trim2
            else:
                s = st.pave_inlay[1] if (ring % 2 == 0) else v.pick(st.pave)
            v.set(x, y, z, s)


def fountain(v: Vox, st: Style, cx, cz, r=5, y=0):
    """丸い水盤 + 中の台 + 吹き上げる水"""
    for x in range(cx - r - 1, cx + r + 2):
        for z in range(cz - r - 1, cz + r + 2):
            d = math.hypot(x - cx, z - cz)
            if d <= r - 0.6:
                v.set(x, y - 1, z, st.base.full)
                v.set(x, y, z, 'water[level=0]')
            elif d <= r + 0.5:
                v.set(x, y - 1, z, st.base.full)
                v.set(x, y, z, st.trim.full)
                v.set(x, y + 1, z, st.trim.slab('bottom'))
            elif d <= r + 1.4:
                v.set(x, y, z, st.trim.slab('bottom'))
    # 中の柱と鉢
    v.fill(cx - 1, y, cz - 1, cx + 1, y, cz + 1, st.base.full)
    v.fill(cx, y + 1, cz, cx, y + 3, cz, st.pillar)
    for f, (dx, dz) in DX.items():
        v.set(cx + dx, y + 1, cz + dz, st.trim.stairs(OPP[f], 'bottom'))
        v.set(cx + dx, y + 4, cz + dz, st.trim.stairs(OPP[f], 'top'))
    v.set(cx, y + 4, cz, st.trim2)
    v.set(cx, y + 5, cz, st.trim.wall())
    v.set(cx, y + 6, cz, 'lantern[hanging=false,waterlogged=false]')


# ---------------------------------------------------------------- 壁面


def _facade_cells(x0, z0, x1, z1, side):
    """側の壁の座標の並び (左から右、外から見て) と外向き"""
    if side == 'south':
        return [(x, z1) for x in range(x0, x1 + 1)], 'south'
    if side == 'north':
        return [(x, z0) for x in range(x1, x0 - 1, -1)], 'north'
    if side == 'east':
        return [(x1, z) for z in range(z1, z0 - 1, -1)], 'east'
    return [(x0, z) for z in range(z0, z1 + 1)], 'west'


def pane_for(st, out):
    return st.glass


def shutter(st, out, wood=None):
    w = wood or st.wood
    return '%s_trapdoor[facing=%s,half=bottom,open=true,waterlogged=false,powered=false]' % (w, out)


class Body:
    """建物の本体。足もと x0..x1 / z0..z1 は外の面 (柱型・帯・土台)。壁は二重で、窓は内の面 (一つ奥) に入る。
    y0 から floors 階、各階 fh の高さ。bay = 柱型の間隔。ground = 一階の作り ('window' / 'shop')"""

    def __init__(self, v: Vox, st: Style, x0, z0, x1, z1, y0=0, floors=2, fh=5, bay=4, plinth=2,
                 door_side='south', door_at=None, door='wood', ground='window', interior=True,
                 sides=('north', 'south', 'east', 'west'), shutters=None, pots=0.3, infill=None, ornate=True, sealed=False):
        self.v, self.st = v, st
        self.x0, self.z0, self.x1, self.z1 = x0, z0, x1, z1
        self.y0, self.floors, self.fh = y0, floors, fh
        self.top = y0 + floors * fh  # 帯 (外の面の一番上の段)
        self.bay, self.plinth = bay, plinth
        self.door_side, self.door_at, self.door = door_side, door_at, door
        self.ground, self.interior, self.sides = ground, interior, sides
        self.shutters = st.shutters if shutters is None else shutters
        self.pots = pots
        self.infill = infill
        self.ornate = ornate
        self.no_skirt = set()
        self.sealed = sealed  # 入れない家: 床も家具も作らず、戸は壁に貼った跳ね上げ戸 (あとで Vox.seal で中を埋める)
        self.doors = []

    def fillb(self):
        st = self.st
        if self.infill:
            return self.infill
        return st.infill if self.v.rng.random() > st.infill_var else self.v.rng.choice(st.infill_mix)

    def baseb(self):
        st = self.st
        return st.base.full if self.v.rng.random() > 0.15 else self.v.rng.choice(st.base2)

    def build(self):
        v, st = self.v, self.st
        x0, z0, x1, z1, y0, top = self.x0, self.z0, self.x1, self.z1, self.y0, self.top
        v.clear(x0 + 1, y0, z0 + 1, x1 - 1, top, z1 - 1)
        v.fill(x0, y0 - 1, z0, x1, y0 - 1, z1, st.base.full)
        # 内の面
        for y in range(y0, top + 1):
            for x in range(x0 + 1, x1):
                v.set(x, y, z0 + 1, self.fillb())
                v.set(x, y, z1 - 1, self.fillb())
            for z in range(z0 + 1, z1):
                v.set(x0 + 1, y, z, self.fillb())
                v.set(x1 - 1, y, z, self.fillb())
        # 階の床
        for f in (range(1, self.floors) if not self.sealed else ()):
            yf = y0 + f * self.fh
            v.fill(x0 + 2, yf, z0 + 2, x1 - 2, yf, z1 - 2, '%s_planks' % st.wood)
        if not self.sealed:
            v.fill(x0 + 2, top, z0 + 2, x1 - 2, top, z1 - 2, '%s_planks' % st.wood)
        for side in ('north', 'south', 'east', 'west'):
            self._outer(side, side in self.sides)
        # 隅の柱
        for (cx, cz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            for y in range(y0, top + 1):
                v.set(cx, y, cz, self.baseb() if y < y0 + self.plinth else (st.trim.full if y == top else st.corner))
        # 裾と上の蛇腹
        self.ring(y0, st.base, 'bottom')
        self.ring(top, st.trim, 'top')
        if self.interior and not self.sealed:
            self._interior()
        return self

    def ring(self, y, mat, half, sides=None):
        """外の面の外へ一周の階段 (蛇腹・裾)。sides = 飾る側 (隣と壁を接する側は出さない)"""
        v = self.v
        sides = self.sides if sides is None else sides
        for side in ('north', 'south', 'east', 'west'):
            if side not in sides:
                continue
            cells, out = _facade_cells(self.x0, self.z0, self.x1, self.z1, side)
            dx, dz = DX[out]
            for (cx, cz) in cells:
                if half == 'bottom' and (cx + dx, cz + dz) in self.no_skirt:
                    continue
                v.set(cx + dx, y, cz + dz, mat.stairs(OPP[out], half), keep=(half == 'bottom'))
        for (cx, cz, a, b) in ((self.x0 - 1, self.z0 - 1, 'north', 'west'), (self.x1 + 1, self.z0 - 1, 'north', 'east'),
                               (self.x0 - 1, self.z1 + 1, 'south', 'west'), (self.x1 + 1, self.z1 + 1, 'south', 'east')):
            if a in sides and b in sides:
                v.set(cx, y, cz, mat.full if half == 'top' else mat.slab('bottom'), keep=(half == 'bottom'))

    def _bays(self, n):
        inner = n - 2
        k = max(1, round(inner / self.bay))
        pil = [0] + [round(1 + j * inner / k) - 1 for j in range(1, k)] + [n - 1]
        return sorted(set(p for p in pil if 0 <= p <= n - 1))

    def _outer(self, side, dressed):
        v, st = self.v, self.st
        cells, out = _facade_cells(self.x0, self.z0, self.x1, self.z1, side)
        dx, dz = DX[out]
        n = len(cells)
        y0, top, fh = self.y0, self.top, self.fh
        pil = self._bays(n)
        floor_rows = {y0 + f * fh for f in range(1, self.floors)}
        for i, (cx, cz) in enumerate(cells):
            if i in (0, n - 1):
                continue
            for y in range(y0, top + 1):
                if y < y0 + self.plinth:
                    s = self.baseb()
                elif y in floor_rows or y == top:
                    s = st.band
                elif i in pil and dressed:
                    s = st.pilaster
                else:
                    s = self.fillb()
                v.set(cx, y, cz, s)
            if i in pil and dressed:
                v.set(cx + dx, top - 1, cz + dz, st.trim.stairs(OPP[out], 'top'), keep=True)
                v.set(cx + dx, y0 + self.plinth, cz + dz, st.trim.stairs(OPP[out], 'bottom'), keep=True)
                # 通りの明かり: 一階の柱型の足もとの段に、ときどき灯り
                if side == self.door_side and 0 < i < n - 1 and v.rng.random() < 0.35 and self.y0 <= 5:
                    v.set(cx + dx, y0 + self.plinth + 1, cz + dz, 'lantern[hanging=false,waterlogged=false]', keep=True)
        if not dressed:
            return
        door_i = None
        if side == self.door_side:
            door_i = self.door_at if self.door_at is not None else n // 2
            if door_i in pil:
                door_i += 1
        for a, b in zip(pil, pil[1:]):
            w = b - a - 1
            if w <= 0:
                continue
            for f in range(self.floors):
                fy = y0 + f * fh
                yb = (y0 + self.plinth) if f == 0 else fy + 1
                yt = fy + fh - 2
                has_door = door_i is not None and a < door_i < b
                if f == 0 and has_door:
                    self._door(cells, out, door_i)
                    continue
                if f == 0 and self.ground == 'shop':
                    self._shop(cells, out, a + 1, b - 1, fy + fh - 1)
                    continue
                ww = 1 if w <= 3 else (2 if w % 2 == 0 else 3)
                c0 = a + 1 + (w - ww) // 2
                yt = min(yt, yb + (3 if fh >= 6 else 2) - 1)
                if yt >= yb:
                    self._window(cells, out, c0, c0 + ww - 1, yb, yt)
                    # 表の面の上の階: ときどき張り出し窓か露台
                    if f >= 1 and side == self.door_side and w >= 3 and self.ornate:
                        r = v.rng.random()
                        if r < 0.22:
                            self._oriel(cells, out, a + 1, b - 1, yb, yt)
                        elif r < 0.40 and f < self.floors:
                            self._balcony(cells, out, a + 1, b - 1, fy)
        # 柱型の旗 (表の面、上の階)
        if self.ornate and side == self.door_side and self.floors >= 2 and v.rng.random() < 0.45:
            for p in pil[1:-1]:
                if v.rng.random() < 0.5:
                    px, pz = cells[p]
                    v.set(px + dx, top - 2, pz + dz, '%s_wall_banner[facing=%s]' % (st.banner_color, out), keep=True)

    def _window(self, cells, out, i0, i1, yb, yt):
        v, st = self.v, self.st
        dx, dz = DX[out]
        ix, iz = -dx, -dz
        for i in range(i0, i1 + 1):
            cx, cz = cells[i]
            for y in range(yb, yt + 1):
                v.set(cx, y, cz, 'air')
                v.set(cx + ix, y, cz + iz, st.glass)
            v.set(cx, yb - 1, cz, st.trim.stairs(OPP[out], 'bottom'))
            if i1 == i0:
                v.set(cx, yt + 1, cz, st.trim.stairs(OPP[out], 'top'))
            elif i == i0:
                v.set(cx, yt + 1, cz, st.trim.stairs(_dir_toward(cells, i0, -1), 'top'))
            elif i == i1:
                v.set(cx, yt + 1, cz, st.trim.stairs(_dir_toward(cells, i1, 1), 'top'))
            else:
                v.set(cx, yt + 1, cz, st.trim.slab('top'))
            if yb > self.y0 + self.plinth and v.rng.random() < self.pots:
                v.set(cx, yb, cz, 'potted_' + v.rng.choice(st.pot_flowers))
        if self.shutters:
            for i in (i0 - 1, i1 + 1):
                if 0 < i < len(cells) - 1:
                    cx, cz = cells[i]
                    for y in range(yb, yt + 1):
                        v.set(cx + dx, y, cz + dz, '%s_trapdoor[facing=%s,half=bottom,open=true,waterlogged=false,powered=false]' % (st.shutter_wood, out))

    def _oriel(self, cells, out, i0, i1, yb, yt):
        """張り出し窓: 窓の前へ一つ出た硝子の箱。下に持ち送り、上に小さな屋根"""
        v, st = self.v, self.st
        dx, dz = DX[out]
        for i in range(i0, i1 + 1):
            cx, cz = cells[i]
            ox, oz = cx + dx, cz + dz
            edge = i in (i0, i1)
            for y in range(yb, yt + 1):
                v.set(cx, y, cz, 'air')
                v.set(ox, y, oz, st.pilaster if edge else st.glass)
            v.set(ox, yb - 1, oz, st.trim.stairs(OPP[out], 'top'))
            v.set(ox, yt + 1, oz, st.trim.full)
            v.set(ox + dx, yt + 1, oz + dz, st.roof.stairs(OPP[out], 'bottom'))
            v.set(ox, yt + 2, oz, st.roof.stairs(OPP[out], 'bottom'))
            v.set(ox + dx, yb - 1, oz + dz, 'air')

    def _balcony(self, cells, out, i0, i1, fy):
        """露台: 階の帯の段に床を一つ出し、手すり。窓は戸口のように高く"""
        v, st = self.v, self.st
        dx, dz = DX[out]
        # 露台は「その階の床の段」(fy) の一つ上の階に付ける: 床 = 次の階の帯 (fy + fh)
        yf = fy + self.fh
        if yf >= self.top:
            return
        for i in range(i0, i1 + 1):
            cx, cz = cells[i]
            ox, oz = cx + dx, cz + dz
            v.set(ox, yf, oz, st.trim.full)
            v.set(ox, yf - 1, oz, st.trim.stairs(OPP[out], 'top'))
            v.set(ox, yf + 1, oz, st.railing if i in (i0, i1) else st.fence_rail)

    def _shop(self, cells, out, i0, i1, ytop):
        v, st = self.v, self.st
        dx, dz = DX[out]
        ix, iz = -dx, -dz
        yt = ytop - 2
        for i in range(i0, i1 + 1):
            cx, cz = cells[i]
            for y in range(self.y0 + 1, yt + 1):
                v.set(cx, y, cz, 'air')
                v.set(cx + ix, y, cz + iz, st.glass if y > self.y0 + 1 else st.trim.full)
            v.set(cx, self.y0, cz, st.base.full)
            if i == i0 and i1 > i0:
                v.set(cx, yt + 1, cz, st.trim.stairs(_dir_toward(cells, i0, -1), 'top'))
            elif i == i1 and i1 > i0:
                v.set(cx, yt + 1, cz, st.trim.stairs(_dir_toward(cells, i1, 1), 'top'))
            else:
                v.set(cx, yt + 1, cz, st.trim.slab('top'))
            v.set(cx + dx, yt + 1, cz + dz, '%s_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]' % (st.awning, OPP[out]))
        if v.rng.random() < 0.6:
            cx, cz = cells[i0]
            v.set(cx + dx, self.y0, cz + dz, 'barrel[facing=up,open=false]', keep=True)
        if v.rng.random() < 0.7:
            cx, cz = cells[i0]
            hanging_sign(v, st, cx, yt + 2, cz, out) if yt + 2 < self.top else None

    def _door(self, cells, out, i):
        v, st = self.v, self.st
        dx, dz = DX[out]
        ix, iz = -dx, -dz
        cx, cz = cells[i]
        y = self.y0
        for yy in range(y, y + 2):
            v.set(cx, yy, cz, 'air')
        if self.door == 'iron':
            d = 'iron_door[facing=' + OPP[out] + ',half=%s,hinge=left,open=false,powered=false]'
        elif self.door == 'open':
            d = None
        else:
            d = st.wood + '_door[facing=' + OPP[out] + ',half=%s,hinge=left,open=false,powered=false]'
        if self.sealed:
            # 入れない戸: 奥は壁、手前に濃い木の跳ね上げ戸を二枚 (戸に見えるが開けても壁)
            v.set(cx + ix, y, cz + iz, self.fillb())
            v.set(cx + ix, y + 1, cz + iz, self.fillb())
            tp = '%s_trapdoor[facing=%s,half=bottom,open=true,waterlogged=false,powered=false]' % (st.shutter_wood, out)
            v.set(cx, y, cz, tp)
            v.set(cx, y + 1, cz, tp)
            v.fake_doors.append((cx, y, cz, out))
        elif d:
            v.set(cx + ix, y, cz + iz, d % 'lower')
            v.set(cx + ix, y + 1, cz + iz, d % 'upper')
        else:
            v.set(cx + ix, y, cz + iz, 'air')
            v.set(cx + ix, y + 1, cz + iz, 'air')
        v.set(cx + ix, y + 2, cz + iz, st.trim.full)
        v.set(cx, y + 2, cz, st.trim.stairs(OPP[out], 'top'))
        v.set(cx, y + 3, cz, st.trim2)
        for j in (-1, 1):
            if 0 < i + j < len(cells) - 1:
                ox, oz = cells[i + j]
                v.set(ox, y + 2, oz, st.trim.full)
                v.set(ox + dx, y + 2, oz + dz, 'lantern[hanging=false,waterlogged=false]', keep=True) if st.door_lamps else None
        v.set(cx, y - 1, cz, st.trim.full)
        v.set(cx + dx, y - 1, cz + dz, st.trim.full)
        # 戸の前の裾の段を外して平らに (つまずかない・戸の前を塞がない)。裾は後で描くので、ここは描かない所として覚える
        for j in (-1, 0, 1):
            if 0 < i + j < len(cells) - 1:
                ox, oz = cells[i + j]
                v.set(ox + dx, y, oz + dz, 'air')
                v.set(ox + dx, y - 1, oz + dz, st.trim.full)
                self.no_skirt.add((ox + dx, oz + dz))
        self.doors.append((cx + ix, y, cz + iz, out))

    def _interior(self):
        v, st = self.v, self.st
        x0, z0, x1, z1 = self.x0 + 2, self.z0 + 2, self.x1 - 2, self.z1 - 2
        for f in range(self.floors):
            yb = self.y0 + f * self.fh + (1 if f else 0)
            yc = self.y0 + (f + 1) * self.fh - 1
            if x1 - x0 >= 2 and z1 - z0 >= 2:
                v.set((x0 + x1) // 2, yc, (z0 + z1) // 2, 'lantern[hanging=true,waterlogged=false]')
            if v.rng.random() < 0.7:
                v.set(x0, yb, z0, 'barrel[facing=up,open=false]')
            if v.rng.random() < 0.5:
                v.set(x1, yb, z0, 'bookshelf')


def _dir_toward(cells, i, sgn):
    """並びの i から i+sgn の向き (世界の向き)"""
    j = i + sgn
    if 0 <= j < len(cells):
        ax, az = cells[i]
        bx, bz = cells[j]
        d = (bx - ax, bz - az)
    else:
        ax, az = cells[i]
        bx, bz = cells[i - sgn]
        d = (ax - bx, az - bz)
    for f, ab in DX.items():
        if ab == d:
            return f
    return 'north'



def timber_upper(v: Vox, st: Style, b: 'Body', log=None, plaster='calcite'):
    """上の階を木の骨組みに: 飾る側の外の面の、壁の地のブロックを、柱型の所は縦の木、帯の段は横の木、間は白い漆喰に"""
    log = log or ('%s_log' % st.wood)
    y2a = b.y0 + b.fh
    keep = ('pane', 'glass', 'stairs', 'slab', 'potted', 'trapdoor', 'banner', 'door', 'lantern', 'air')
    for side in b.sides:
        if side not in ('north', 'south', 'east', 'west'):
            continue
        cells, out = _facade_cells(b.x0, b.z0, b.x1, b.z1, side)
        pil = set(b._bays(len(cells)))
        for i, (px, pz) in enumerate(cells):
            for yy in range(y2a, b.top + 1):
                s0 = v.get(px, yy, pz)
                if s0 is None or any(k in s0 for k in keep):
                    continue
                if (yy - b.y0) % b.fh == 0 or yy == b.top:
                    v.set(px, yy, pz, '%s[axis=%s]' % (log, 'x' if side in ('north', 'south') else 'z'))
                elif i in pil or i in (0, len(cells) - 1):
                    v.set(px, yy, pz, '%s[axis=y]' % log)
                else:
                    v.set(px, yy, pz, plaster)


def door_canopy(v: Vox, st: Style, x, y, z, out):
    """戸の上の小さな庇 (幅 3) と持ち送り"""
    dx, dz = DX[out]
    ax, az = (1, 0) if out in ('north', 'south') else (0, 1)
    for k in (-1, 0, 1):
        v.set(x + dx + ax * k, y + 3, z + dz + az * k, st.roof.stairs(OPP[out], 'bottom'))
    for k in (-1, 1):
        v.set(x + dx + ax * k, y + 2, z + dz + az * k, '%s_stairs[facing=%s,half=top,shape=straight,waterlogged=false]' % (st.wood, OPP[out]))


def hanging_sign(v: Vox, st: Style, x, y, z, out):
    """壁の吊り看板 (壁の外の一つ前、向きは壁に沿う)"""
    dx, dz = DX[out]
    f = CW[out]
    v.set(x + dx, y, z + dz, '%s_wall_hanging_sign[facing=%s,waterlogged=false]' % (st.wood, f), keep=True)


# ---------------------------------------------------------------- 屋根

_SWAP = {'north': 'west', 'south': 'east', 'east': 'south', 'west': 'north'}


def _transpose_into(v: Vox, tmp: Vox):
    """tmp (x と z を入れかえて書いたもの) を v へ戻す"""
    for (a, yy, b), s in tmp.b.items():
        for f, g in _SWAP.items():
            if 'facing=%s,' % f in s or 'facing=%s]' % f in s:
                s = s.replace('facing=%s' % f, 'facing=%s' % g, 1)
                break
        if 'outer_left' in s:
            s = s.replace('outer_left', 'outer_right')
        elif 'outer_right' in s:
            s = s.replace('outer_right', 'outer_left')
        if '_log[axis=x]' in s:
            s = s.replace('axis=x', 'axis=z')
        elif '_log[axis=z]' in s:
            s = s.replace('axis=z', 'axis=x')
        v.set(b, yy, a, s)


def gable_roof(v: Vox, st: Style, x0, z0, x1, z1, y, axis='x', over=1, steep=True, dormers=0, chimney=None,
               gable_fill=None, mat=None, finial=True):
    """切妻。足もと x0..x1 / z0..z1 は壁の外の面、y は屋根の最初の段 (壁の上の帯の一つ上)。
    axis = 棟の向き ('x' = 東西に棟)。over = 軒の出。steep = 1 段で 2 上がる。
    妻の縁は白い縁石 (roof_edge)、妻壁は一つ奥。chimney = (x, z) (世界の座標)。返り値 = 棟の高さ"""
    R = mat or st.roof
    if axis == 'z':
        tmp = Vox()
        tmp.rng = v.rng
        ridge = gable_roof(tmp, st, z0, x0, z1, x1, y, 'x', over, steep, dormers, None, gable_fill, mat, finial)
        _transpose_into(v, tmp)
        if chimney:
            _chimney(v, st, chimney[0], chimney[1], y, ridge)
        return ridge
    gf = gable_fill or st.infill
    E = st.roof_edge
    za, zb = z0 - over, z1 + over
    xa, xb = x0 - over, x1 + over
    rise = 2 if steep else 1
    yy = y
    zn, zs = za, zb
    while zn <= zs:
        for r in range(rise):
            yl = yy + r
            last_layer = r == rise - 1
            for x in range(xa, xb + 1):
                end = x in (xa, xb)
                m = E if end else R
                if zn == zs:
                    v.set(x, yl, zn, m.slab('bottom') if last_layer else m.full)
                    continue
                # どの段も階段 (瓦の筋が出る)。下の段は一つ外へずれないので、段の下は屋根の地で埋める
                v.set(x, yl, zn, m.stairs('south'))
                v.set(x, yl, zs, m.stairs('north'))
                if not last_layer or r > 0:
                    v.set(x, yl, zn + 1, R.full if not end else E.full, keep=True)
                    v.set(x, yl, zs - 1, R.full if not end else E.full, keep=True)
            # 妻壁 (縁の一つ奥) と屋根裏の床の下の詰め
            for x in (x0, x1):
                for z in range(zn + 1, zs):
                    v.set(x, yl, z, gf)
        if zn + 1 == zs:
            # 偶数の幅: 棟に半ブロックを二列
            for x in range(xa, xb + 1):
                m = E if x in (xa, xb) else R
                v.set(x, yy + rise, zn, m.slab('bottom'))
                v.set(x, yy + rise, zs, m.slab('bottom'))
            yy += rise
            break
        yy += rise
        zn, zs = zn + 1, zs - 1
    ridge = yy
    zc = (za + zb) // 2
    if finial:
        for x in (xa, xb):
            v.set(x, ridge + (0 if (zb - za) % 2 == 0 else 1), zc, st.trim.wall())
            v.set(x, ridge + (1 if (zb - za) % 2 == 0 else 2), zc, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    # 妻の窓
    if z1 - z0 >= 6:
        for x in (x0, x1):
            v.set(x, y + rise, (z0 + z1) // 2, st.glass)
            v.set(x, y + rise + 1, (z0 + z1) // 2, st.glass)
            if (z1 - z0) % 2 == 1:
                v.set(x, y + rise, (z0 + z1) // 2 + 1, st.glass)
                v.set(x, y + rise + 1, (z0 + z1) // 2 + 1, st.glass)
    # 屋根窓 (南と北の流れに)
    if dormers:
        span = x1 - x0
        xs = [x0 + round((k + 1) * span / (dormers + 1)) for k in range(dormers)]
        for xd in xs:
            _dormer(v, st, xd, z1, y, 'south', R)
            _dormer(v, st, xd, z0, y, 'north', R)
    if chimney:
        _chimney(v, st, chimney[0], chimney[1], y, ridge)
    return ridge


def _dormer(v: Vox, st: Style, x, zface, y, out, R):
    """屋根窓: 幅 3 の小さな切妻。前の面は壁の外の面 zface にそろえる"""
    s = 1 if out == 'south' else -1
    zf = zface
    zb = zface - s * 3
    # 壁と窓
    for zz in range(min(zf, zb), max(zf, zb) + 1):
        for yy in range(y, y + 3):
            for xx in (x - 1, x + 1):
                v.set(xx, yy, zz, st.infill)
            v.set(x, yy, zz, 'air')
    for yy in range(y, y + 3):
        v.set(x - 1, yy, zf, st.trim.full if yy == y else st.pilaster)
        v.set(x + 1, yy, zf, st.trim.full if yy == y else st.pilaster)
    v.set(x, y, zf, st.trim.full)
    v.set(x, y + 1, zf, st.glass)
    v.set(x, y + 2, zf, st.glass)
    v.set(x, y, zf + s, st.trim.stairs(OPP[out], 'top'))
    # 小さな屋根 (棟は前後)
    for zz in range(min(zf + s, zb), max(zf + s, zb) + 1):
        v.set(x - 2, y + 3, zz, R.stairs('east'))
        v.set(x + 2, y + 3, zz, R.stairs('west'))
        v.set(x - 1, y + 3, zz, R.full)
        v.set(x + 1, y + 3, zz, R.full)
        v.set(x, y + 3, zz, st.infill)
        v.set(x - 1, y + 4, zz, R.stairs('east'))
        v.set(x + 1, y + 4, zz, R.stairs('west'))
        v.set(x, y + 4, zz, R.full)
        v.set(x, y + 5, zz, R.slab('bottom'))
    E = st.roof_edge
    v.set(x - 2, y + 3, zf + s, E.stairs('east'))
    v.set(x + 2, y + 3, zf + s, E.stairs('west'))
    v.set(x - 1, y + 4, zf + s, E.stairs('east'))
    v.set(x + 1, y + 4, zf + s, E.stairs('west'))
    v.set(x, y + 5, zf + s, E.slab('bottom'))
    v.set(x - 1, y + 3, zf + s, E.full)
    v.set(x + 1, y + 3, zf + s, E.full)
    v.set(x, y + 3, zf + s, st.trim2)
    v.set(x, y + 4, zf + s, E.full)


def _chimney(v, st, x, z, y, ridge):
    top = max(ridge + 1, y + 3)
    v.fill(x, y - 1, z, x, top, z, st.base.full)
    v.set(x, top + 1, z, st.base.slab('bottom'))


def hip_spire(v: Vox, st: Style, x0, z0, x1, z1, y, mat=None, rise=2, finial='lightning_rod', flare=True):
    """四角錐の尖り屋根。rise = 一段の高さ (大きいほど細く高い)。flare = 裾を一段広げる。返り値 = 頂の高さ"""
    R = mat or st.roof
    if flare:
        x0, z0, x1, z1 = x0 - 1, z0 - 1, x1 + 1, z1 + 1
    yy = y
    while x0 <= x1 and z0 <= z1:
        for r in range(rise):
            last = r == rise - 1
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    n, s_, w, e = z == z0, z == z1, x == x0, x == x1
                    if not (n or s_ or w or e) or (x0 == x1 and z0 == z1):
                        v.set(x, yy + r, z, R.full)
                    elif n and w:
                        v.set(x, yy + r, z, R.stairs('south', 'bottom', 'outer_left'))
                    elif n and e:
                        v.set(x, yy + r, z, R.stairs('south', 'bottom', 'outer_right'))
                    elif s_ and w:
                        v.set(x, yy + r, z, R.stairs('north', 'bottom', 'outer_right'))
                    elif s_ and e:
                        v.set(x, yy + r, z, R.stairs('north', 'bottom', 'outer_left'))
                    elif n:
                        v.set(x, yy + r, z, R.stairs('south'))
                    elif s_:
                        v.set(x, yy + r, z, R.stairs('north'))
                    elif w:
                        v.set(x, yy + r, z, R.stairs('east'))
                    else:
                        v.set(x, yy + r, z, R.stairs('west'))
        yy += rise
        if x0 == x1 and z0 == z1:
            break
        x0, z0, x1, z1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
        if x0 > x1 or z0 > z1:
            break
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    if finial == 'lightning_rod':
        v.set(cx, yy, cz, st.trim.wall())
        v.set(cx, yy + 1, cz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
        return yy + 1
    if finial:
        v.set(cx, yy, cz, finial)
    return yy


def pinnacle(v: Vox, st: Style, x, y, z, h=3):
    v.set(x, y, z, st.trim.full)
    v.fill(x, y + 1, z, x, y + h, z, st.trim.wall())
    v.set(x, y + h + 1, z, 'lightning_rod[facing=up,powered=false,waterlogged=false]')


def tower(v: Vox, st: Style, cx, cz, half=3, y0=0, h=20, belfry=True, spire_rise=2, spire_mat=None, pinnacles=True,
          door_side=None, door='wood', clock=False, fh=6, sealed=False):
    """四角い塔。中心 (cx, cz)、幅 2*half+1。胴は二重壁の Body、頂に鐘の間 (四方のアーチ)、胸壁、尖り屋根、四隅の小尖塔"""
    x0, z0, x1, z1 = cx - half, cz - half, cx + half, cz + half
    floors = max(1, (h - (5 if belfry else 0)) // fh)
    b = Body(v, st, x0, z0, x1, z1, y0, floors=floors, fh=fh, bay=max(3, half + 1), door_side=door_side or 'none',
             door=door, interior=False, pots=0.0, shutters=False)
    b.build()
    top = b.top
    if sealed:
        # 入れない塔: 胴の中を埋める (鐘の間の吹き抜けは残す)
        v.seal(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y0, y1=top if belfry else None)
    if belfry:
        # 鐘の間: 柱だけ残して四方が抜けた 4 段
        yb = top + 1
        v.fill(x0, yb, z0, x1, yb + 4, z1, 'air')
        for (px, pz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            v.fill(px, yb, pz, px, yb + 4, pz, st.corner)
        for side in ('north', 'south', 'east', 'west'):
            cells, out = _facade_cells(x0, z0, x1, z1, side)
            n = len(cells)
            for i, (px, pz) in enumerate(cells):
                if i in (0, n - 1):
                    continue
                v.set(px, yb, pz, st.railing)
                if i == 1:
                    v.set(px, yb + 3, pz, st.trim.stairs(_dir_toward(cells, i, -1), 'top'))
                elif i == n - 2:
                    v.set(px, yb + 3, pz, st.trim.stairs(_dir_toward(cells, i, 1), 'top'))
                v.set(px, yb + 4, pz, st.band)
                if n >= 9 and i == n // 2:
                    v.fill(px, yb, pz, px, yb + 3, pz, st.pilaster)
        v.fill(x0 + 1, top, z0 + 1, x1 - 1, top, z1 - 1, '%s_planks' % st.wood)
        top = yb + 4
        b.top = top
        b.ring(top, st.trim, 'top')
    # 胸壁と屋根
    for x in range(x0 - 1, x1 + 2):
        for z in (z0 - 1, z1 + 1):
            v.set(x, top + 1, z, st.trim.wall() if (x + z) % 2 == 0 else st.trim.slab('bottom'))
    for z in range(z0 - 1, z1 + 2):
        for x in (x0 - 1, x1 + 1):
            v.set(x, top + 1, z, st.trim.wall() if (x + z) % 2 == 0 else st.trim.slab('bottom'))
    v.fill(x0, top + 1, z0, x1, top + 1, z1, st.trim.full)
    peak = hip_spire(v, st, x0 + 1, z0 + 1, x1 - 1, z1 - 1, top + 2, mat=spire_mat or st.spire, rise=spire_rise, flare=True)
    if pinnacles:
        for (px, pz) in ((x0 - 1, z0 - 1), (x1 + 1, z0 - 1), (x0 - 1, z1 + 1), (x1 + 1, z1 + 1)):
            pinnacle(v, st, px, top + 1, pz, h=3)
    if clock:
        cells, out = _facade_cells(x0, z0, x1, z1, 'south')
        px, pz = cells[len(cells) // 2]
        dx, dz = DX[out]
        yc = b.y0 + floors * fh - 3
        # 丸窓 (縁は白い石、真ん中は硝子)
        for (ax, ay) in ((-1, 0), (1, 0), (0, 1), (0, -1)):
            v.set(px + ax, yc + ay, pz + dz, st.trim.full)
        v.set(px, yc, pz + dz, st.trim2)
        v.set(px, yc, pz, st.glass)
    return peak


def turret(v: Vox, st: Style, cx, cz, y0, h, half=1, rise=3):
    """建物の隅の小塔 (細い胴と尖り屋根)"""
    x0, z0, x1, z1 = cx - half, cz - half, cx + half, cz + half
    for y in range(y0, y0 + h):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                v.set(x, y, z, (st.corner if (x in (x0, x1) and z in (z0, z1)) else st.infill) if edge else 'air')
    for side in ('north', 'south', 'east', 'west'):
        cells, out = _facade_cells(x0, z0, x1, z1, side)
        px, pz = cells[len(cells) // 2]
        for y in range(y0 + 2, y0 + h - 1, 4):
            v.set(px, y, pz, st.glass)
    tmp = Body(v, st, x0, z0, x1, z1, y0, floors=1, fh=h, plinth=0, interior=False)
    tmp.top = y0 + h - 1
    tmp.ring(y0 + h - 1, st.trim, 'top')
    peak = hip_spire(v, st, x0, z0, x1, z1, y0 + h, mat=st.spire, rise=rise, flare=True)
    v.seal(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y0)  # 小塔には入れない
    return peak


def arcade(v: Vox, st: Style, x0, z, x1, y0=0, h=4, step=3, roof=True):
    """柱とアーチの並び (z 一列、x0..x1)。上に塀と半ブロック"""
    for x in range(x0, x1 + 1):
        i = x - x0
        if i % step == 0:
            v.fill(x, y0, z, x, y0 + h - 1, z, st.pilaster)
            v.set(x, y0, z, st.base.full)
        else:
            k = i % step
            if k == 1:
                v.set(x, y0 + h - 1, z, st.trim.stairs('west', 'top'))
            elif k == step - 1:
                v.set(x, y0 + h - 1, z, st.trim.stairs('east', 'top'))
            else:
                v.set(x, y0 + h - 1, z, st.trim.slab('top'))
        v.set(x, y0 + h, z, st.band)
        if roof:
            v.set(x, y0 + h + 1, z, st.railing if i % step == 0 else st.trim.slab('bottom'))
