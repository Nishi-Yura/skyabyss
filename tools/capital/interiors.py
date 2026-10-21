# -*- coding: utf-8 -*-
"""都の家の中身 (docs/design/35 の 6.7)。facilities.Facilities が継ぐ。

- 家具は「型」(スタンプ) で置く。型は北を背にして書き (背の列 dz=0、手前へ +dz)、部屋の中で回して空いた所に置く。
  wall=True の型は背を壁 (丸ごとのブロック) につける。型と型のあいだは 1 マス空ける (通り道)。
  '.' のマスは空けておく所 (箱・かまどの前。建築の監査 ATTACH_FACING)。
- 床の穴 (階段・梯子) のまわり 1 マスと keep のマスには置かない。
- 町のクエストの家 11 の住み手 (HOME_THEMES) と、施設の上の階 (窓から見える部屋) の中身 (upper_rooms)。
"""
from __future__ import annotations

import random
import re

from vox import DX, OPP, CW

AIR = '.'
SLAB = '{w}_slab[type=top,waterlogged=false]'
CHAIR_S = '{w}_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]'
CHAIR_N = '{w}_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]'
CANDLE = 'candle[candles=%d,lit=true,waterlogged=false]'
LANTERN = 'lantern[hanging=false,waterlogged=false]'
BARREL = 'barrel[facing=up,open=false]'
CHEST = 'chest[facing=south,type=single,waterlogged=false]'


def _bed(c, dx=0):
    return [(dx, 0, '%s_bed[facing=north,part=head,occupied=false]' % c, None), (dx, 1, '%s_bed[facing=north,part=foot,occupied=false]' % c, None)]


def _note(i):
    return 'note_block[instrument=harp,note=%d,powered=false]' % i


# 型: (名前, wall, [(dx, dz, 状態, 上に置く物)])。{w} = 木、{c} = 色
STAMPS = {
    'bed': (True, _bed('{c}') + [(1, 0, BARREL, CANDLE % 1)]),
    'bed_plain': (True, _bed('{c}')),
    'bed_pair': (True, _bed('{c}') + [(1, 0, BARREL, LANTERN)] + _bed('{c}', 2)),
    'crib': (True, _bed('white') + [(1, 0, '{w}_fence', None), (1, 1, '{w}_fence', None)]),
    'cold_bed': (True, _bed('light_gray') + [(1, 0, BARREL, 'candle[candles=1,lit=false,waterlogged=false]')]),
    'chest': (True, [(0, 0, CHEST, None), (0, 1, AIR, None)]),
    'wardrobe': (True, [(0, 0, 'barrel[facing=south,open=false]', 'barrel[facing=up,open=false]'), (1, 0, CHEST, None), (1, 1, AIR, None)]),
    'desk': (True, [(0, 0, SLAB, CANDLE % 2), (1, 0, SLAB, 'potted_fern'), (0, 1, CHAIR_S, None)]),
    'writing': (True, [(0, 0, 'lectern[facing=south,has_book=false,powered=false]', None), (1, 0, 'bookshelf', 'bookshelf'),
                       (2, 0, 'chiseled_bookshelf[facing=south,slot_0_occupied=true,slot_1_occupied=true,slot_2_occupied=false,slot_3_occupied=true,slot_4_occupied=true,slot_5_occupied=true]', CANDLE % 3)]),
    'books': (True, [(0, 0, 'bookshelf', 'bookshelf'), (1, 0, 'bookshelf', 'chiseled_bookshelf[facing=south,slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=true,slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=true]')]),
    'paper': (False, [(0, 0, SLAB, 'white_carpet'), (1, 0, SLAB, CANDLE % 1), (0, 1, CHAIR_S, None)]),
    'table': (False, [(0, 0, SLAB, CANDLE % 2), (1, 0, SLAB, 'potted_red_tulip'), (0, -1, CHAIR_N, None), (1, 1, CHAIR_S, None)]),
    'long_table': (False, [(0, 0, SLAB, 'potted_oxeye_daisy'), (1, 0, SLAB, CANDLE % 3), (2, 0, SLAB, 'cake[bites=1]'),
                           (0, -1, CHAIR_N, None), (2, -1, CHAIR_N, None), (0, 1, CHAIR_S, None), (1, 1, CHAIR_S, None), (2, 1, CHAIR_S, None)]),
    'kitchen': (True, [(0, 0, 'smoker[facing=south,lit=true]', None), (0, 1, AIR, None), (1, 0, BARREL, LANTERN), (2, 0, 'water_cauldron[level=3]', None),
                       (3, 0, 'crafting_table', None)]),
    'crates': (False, [(0, 0, BARREL, BARREL), (1, 0, 'barrel[facing=north,open=false]', None), (0, 1, 'hay_block[axis=y]', None)]),
    'hay': (False, [(0, 0, 'hay_block[axis=x]', 'hay_block[axis=x]'), (1, 0, 'hay_block[axis=x]', None)]),
    'store': (True, [(0, 0, CHEST, None), (0, 1, AIR, None), (1, 0, BARREL, BARREL), (2, 0, CHEST, None), (2, 1, AIR, None)]),
    'loom': (True, [(0, 0, 'loom[facing=south]', None), (0, 1, AIR, None), (1, 0, 'white_wool', 'red_wool'), (2, 0, 'light_blue_wool', None)]),
    'wool': (False, [(0, 0, 'white_wool', 'yellow_wool'), (1, 0, 'red_wool', None), (0, 1, 'light_blue_wool', None)]),
    'fish': (True, [(0, 0, BARREL, 'dried_kelp_block'), (1, 0, 'water_cauldron[level=3]', None), (2, 0, 'barrel[facing=south,open=false]', LANTERN),
                    (3, 0, 'composter[level=7]', None)]),
    'nets': (True, [(0, 0, 'dried_kelp_block', None), (1, 0, BARREL, 'potted_dead_bush'), (2, 0, 'dried_kelp_block', 'dried_kelp_block')]),
    'brew': (True, [(0, 0, SLAB, 'brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]'), (1, 0, 'cauldron', None),
                    (2, 0, SLAB, 'potted_red_mushroom')]),
    'herbs': (True, [(0, 0, SLAB, 'potted_fern'), (1, 0, SLAB, 'potted_blue_orchid'), (2, 0, SLAB, 'potted_allium'), (3, 0, 'composter[level=5]', None)]),
    'bakery': (True, [(0, 0, 'furnace[facing=south,lit=true]', None), (0, 1, AIR, None), (1, 0, 'smoker[facing=south,lit=true]', None), (1, 1, AIR, None),
                      (2, 0, 'hay_block[axis=y]', 'hay_block[axis=y]'), (3, 0, BARREL, 'cake[bites=0]')]),
    'flour': (False, [(0, 0, 'hay_block[axis=x]', None), (1, 0, BARREL, 'white_carpet'), (0, 1, BARREL, None)]),
    'smith': (False, [(0, 0, 'anvil[facing=east]', None), (1, 0, 'grindstone[face=floor,facing=north]', None), (2, 0, 'smithing_table', None)]),
    'soldier': (True, [(0, 0, 'smithing_table', None), (1, 0, BARREL, LANTERN), (2, 0, 'barrel[facing=south,open=false]', 'skeleton_skull[rotation=8]')]),
    'map': (False, [(0, 0, 'cartography_table', None), (0, 1, CHAIR_S, None), (1, 0, SLAB, CANDLE % 2)]),
    'music': (True, [(0, 0, 'jukebox[has_record=false]', None), (1, 0, _note(5), None), (2, 0, _note(9), CANDLE % 2)]),
    'clutter': (False, [(0, 0, BARREL, 'decorated_pot[cracked=false,facing=north,waterlogged=false]'), (1, 0, 'red_wool', None), (0, 1, 'brown_wool', None)]),
    'toys': (False, [(0, 0, 'decorated_pot[cracked=false,facing=south,waterlogged=false]', None), (1, 0, 'pink_wool', None), (1, 1, _note(12), None)]),
    'mourn': (False, [(0, 0, SLAB, 'candle[candles=1,lit=false,waterlogged=false]'), (1, 0, SLAB, 'potted_lily_of_the_valley'), (2, 0, SLAB, 'candle[candles=1,lit=false,waterlogged=false]'),
                      (0, 1, CHAIR_S, None), (1, 1, CHAIR_S, None), (2, 1, CHAIR_S, None), (1, -1, CHAIR_N, None)]),
    'flowers': (True, [(0, 0, 'potted_white_tulip', None), (1, 0, 'potted_lily_of_the_valley', None)]),
    'plants': (False, [(0, 0, 'potted_flowering_azalea_bush', None)]),
    'vault': (True, [(0, 0, CHEST, None), (0, 1, AIR, None), (1, 0, 'barrel[facing=south,open=false]', 'barrel[facing=south,open=false]'), (2, 0, CHEST, None), (2, 1, AIR, None),
                     (3, 0, 'barrel[facing=south,open=false]', LANTERN)]),
    'lectern': (False, [(0, 0, 'lectern[facing=south,has_book=false,powered=false]', None), (0, 1, AIR, None)]),
    'fletch': (True, [(0, 0, 'fletching_table', None), (1, 0, BARREL, 'lantern[hanging=false,waterlogged=false]')]),
    'amethyst': (False, [(0, 0, BARREL, 'amethyst_cluster[facing=up,waterlogged=false]'), (1, 0, 'barrel[facing=north,open=false]', 'purple_candle[candles=2,lit=true,waterlogged=false]')]),
}

# 町のクエストの家 1..11 の住み手 (docs/design/35 の 6.7): (名前, 一階の型, 二階の型, 寝台の色)
HOME_THEMES = {
    1: ('scribe', ['writing', 'books', 'desk', 'paper', 'chest', 'plants'], ['bed', 'books', 'desk', 'chest'], 'white'),
    2: ('old_sailor', ['fish', 'nets', 'chest'], ['bed', 'chest', 'nets'], 'blue'),
    3: ('weaver', ['loom', 'wool', 'table', 'chest', 'plants'], ['bed', 'wool', 'wardrobe'], 'red'),
    4: ('fisher', ['fish', 'kitchen', 'table', 'crates'], ['bed_pair', 'nets', 'chest'], 'cyan'),
    5: ('family', ['kitchen', 'long_table', 'toys', 'chest'], ['bed', 'crib', 'bed', 'chest', 'toys'], 'yellow'),
    6: ('baker', ['bakery', 'flour', 'table', 'hay'], ['bed', 'flour', 'chest'], 'orange'),
    7: ('apprentice', ['smith', 'bed_plain', 'table', 'chest'], ['bed_pair', 'bed', 'chest'], 'brown'),
    8: ('herbalist', ['brew', 'herbs', 'kitchen', 'table'], ['bed', 'herbs', 'chest'], 'green'),
    9: ('old_soldier', ['soldier', 'map', 'table', 'chest'], ['bed_plain', 'chest', 'soldier'], 'gray'),
    10: ('bard', ['music', 'table', 'clutter'], ['bed', 'chest', 'clutter'], 'purple'),
    11: ('mourning', ['mourn', 'kitchen', 'flowers', 'chest'], ['bed', 'cold_bed', 'flowers', 'chest'], 'white'),
}


def _rot_state(s, k):
    if s is None or s == AIR or k == 0:
        return s
    for _ in range(k):
        s = re.sub(r'facing=(north|east|south|west)', lambda m: 'facing=' + CW[m.group(1)], s)
        if 'axis=x' in s:
            s = s.replace('axis=x', 'axis=z')
        elif 'axis=z' in s:
            s = s.replace('axis=z', 'axis=x')
        m = re.search(r'rotation=(\d+)', s)
        if m:
            s = s.replace(m.group(0), 'rotation=%d' % ((int(m.group(1)) + 4) % 16))
    return s


def _rot_xy(dx, dz, k):
    for _ in range(k):
        dx, dz = -dz, dx
    return dx, dz


class Interiors:
    def frng(self):
        """家具だけの乱数 (町並みの乱数をずらさない)"""
        if not hasattr(self, '_frng'):
            self._frng = random.Random(20261022)
        return self._frng

    def _free_cell(self, x, y, z):
        s = self.v.get(x, y, z)
        return s is None or s in ('air', 'cave_air') or s.endswith('carpet')

    def _hole_block(self, x0, z0, x1, z1, y):
        """床の穴 (階段・梯子) と梯子のまわり 1 マス"""
        v = self.v
        out = set()
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                below = v.get(x, y - 1, z) or ''
                here = v.get(x, y, z) or ''
                inside = x0 <= x <= x1 and z0 <= z <= z1
                hole = inside and (below in ('', 'air', 'cave_air') or below.startswith('ladder') or 'stairs' in below)
                if hole or here.startswith('ladder') or 'stairs' in here and inside and not self._ours(x, y, z):
                    for ddx in (-1, 0, 1):
                        for ddz in (-1, 0, 1):
                            out.add((x + ddx, z + ddz))
        return out

    def _ours(self, x, y, z):
        return (x, y, z) in getattr(self, '_placed_furn', set())

    def place_stamps(self, rect, y, names, color='red', keep=(), hole_free=True):
        """rect = (x0, z0, x1, z1) の床 (立つ所 y) に、型 names を順に置く。置けた型の名前の一覧を返す"""
        st, v = self.st, self.v
        rng = self.frng()
        x0, z0, x1, z1 = rect
        w = st.wood
        area = {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}
        blocked = set(keep)
        if hole_free:
            blocked |= self._hole_block(x0, z0, x1, z1, y)
        if not hasattr(self, '_placed_furn'):
            self._placed_furn = set()
        anchors = sorted(area)
        gap = 0 if min(x1 - x0, z1 - z0) <= 2 else 1   # 狭い部屋 (奥行き 3 まで) は型を詰める
        done = []
        for nm in names:
            wall, cells = STAMPS[nm]
            opts = [(k, a) for k in range(4) for a in anchors]
            rng.shuffle(opts)
            for k, (ax, az) in opts:
                cs = []
                for (dx, dz, s, t) in cells:
                    rx, rz = _rot_xy(dx, dz, k)
                    s2 = _rot_state(s.replace('{w}', w).replace('{c}', color), k)
                    t2 = _rot_state(t.replace('{w}', w).replace('{c}', color), k) if t else None
                    cs.append((ax + rx, az + rz, s2, t2, dz))
                ok = True
                for (x, z, s, t, dz) in cs:
                    if (x, z) not in area or (x, z) in blocked or not self._free_cell(x, y, z) or not self.solid_at(x, y - 1, z):
                        ok = False
                        break
                    if t and not self._free_cell(x, y + 1, z):
                        ok = False
                        break
                    if s != AIR and not self._free_cell(x, y + 1, z) and v.get(x, y + 1, z) not in (None, 'air', 'cave_air'):
                        ok = False
                        break
                if ok and wall:
                    bx, bz = _rot_xy(0, -1, k)
                    for (x, z, s, t, dz) in cs:
                        if dz == 0 and s != AIR and not self.solid_at(x + bx, y, z + bz):
                            ok = False
                            break
                if not ok:
                    continue
                for (x, z, s, t, dz) in cs:
                    v.set(x, y, z, self.air(y) if s == AIR else s)
                    self._placed_furn.add((x, y, z))
                    if t:
                        v.set(x, y + 1, z, t)
                    for ddx in ((-1, 0, 1) if gap else (0,)):
                        for ddz in ((-1, 0, 1) if gap else (0,)):
                            blocked.add((x + ddx, z + ddz))
                done.append(nm)
                break
        return done

    def rug(self, rect, y, color):
        """空いた床の真ん中に敷物 (型のまわりの床は空けたまま)"""
        x0, z0, x1, z1 = rect
        v = self.v
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                if v.get(x, y, z) in (None, 'air') and self.solid_at(x, y - 1, z) and all(
                        v.get(x + a, y, z + b) in (None, 'air') or (v.get(x + a, y, z + b) or '').endswith('carpet') for (a, b) in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    v.set(x, y, z, '%s_carpet' % color)

    def herb_bundles(self, rect, y_ceiling, n=4):
        """天井から吊るした薬草の束 (梁の下に花の咲いた躑躅の葉)。頭より上 (立つ所 + 3 以上)"""
        x0, z0, x1, z1 = rect
        v = self.v
        cells = [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)]
        self.frng().shuffle(cells)
        k = 0
        for (x, z) in cells:
            if k >= n:
                break
            if v.get(x, y_ceiling - 1, z) in (None, 'air') and self.solid_at(x, y_ceiling, z):
                v.set(x, y_ceiling - 1, z, 'flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]' if k % 2 else 'azalea_leaves[distance=1,persistent=true,waterlogged=false]')
                k += 1

    # ---------------------------------------------------------------- 施設の上の階 (窓から見える部屋)
    UPPER = [
        # (名前, 内の範囲 (棚の輪の内), 立つ所 y, 型, 敷物の色)
        ('tool_shop', (36, -35, 42, -31), 7, ['store', 'crates', 'hay', 'fletch', 'crates', 'chest'], 'brown'),
        ('storage', (35, -13, 41, -10), 7, ['vault', 'vault', 'desk', 'lectern'], 'gray'),
        ('apothecary', (56, 9, 63, 14), 7, ['brew', 'herbs', 'bed', 'chest', 'plants'], 'green'),
        ('perch_lodge', (-91, 9, -87, 13), 6, ['bed', 'map', 'chest', 'hay'], 'light_blue'),
        ('tavern', (-37, 39, -31, 43), 6, ['bed', 'crates', 'table', 'chest'], 'red'),
        ('black_market', (56, 43, 60, 47), 6, ['amethyst', 'crates', 'chest', 'amethyst'], 'purple'),
        ('guild', (53, -31, 89, -13), 7, ['desk', 'desk', 'writing', 'map', 'table', 'table', 'books', 'vault', 'chest', 'plants', 'plants', 'desk'], 'red'),
    ]

    def upper_rooms(self):
        for (name, rect, y, names, rugc) in self.UPPER:
            if name == 'black_market':
                # 二階に部屋がまだない (床と壁だけ): 壁ぎわの棚と灯り
                x0, z0, x1, z1 = rect
                saved, self.rng = self.rng, self.frng()
                self.room(x0 - 1, z0 - 1, x1 + 1, z1 + 1, y, 4, rug=False, beams=False, wall_props=False)
                self.rng = saved
                self.v.set((x0 + x1) // 2, y + 3, (z0 + z1) // 2, 'soul_lantern[hanging=true,waterlogged=false]')
            self.place_stamps(rect, y, names, color=rugc)
            self.rug(rect, y, rugc)
