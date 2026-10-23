# -*- coding: utf-8 -*-
"""施設の内装の切り取り図 (屋根を外した等角の図)。

python -I tools/capital/interior_render.py dump <pkl>             都を建てて、ブロックと部屋の範囲を <pkl> に保存
python -I tools/capital/interior_render.py render <pkl> <名前> <出力 png> [層 0|1] [向き se|sw|ne|nw]
python -I tools/capital/interior_render.py list <pkl>              部屋の名前の一覧
python -I tools/capital/interior_render.py sheet <出力 png> <png>...   縮小して並べる
絵は箱の組み合わせ (階段・半ブロック・柵・灯りなどは本物に近い形)。色はブロック名から決めた近似。
"""
from __future__ import annotations

import os
import pickle
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw  # noqa: E402

AIRS = ('air', 'cave_air', 'void_air', 'light', 'barrier')

COL = [
    ('deepslate_tile', (62, 64, 72)), ('deepslate', (75, 75, 80)), ('red_nether_brick', (120, 30, 34)), ('nether_brick', (50, 26, 30)),
    ('polished_andesite', (138, 140, 140)), ('andesite', (136, 137, 137)), ('stone_brick', (125, 125, 125)), ('smooth_stone', (160, 160, 160)),
    ('cobblestone', (120, 120, 120)), ('stone', (128, 128, 128)), ('blackstone', (45, 40, 50)), ('dark_oak', (66, 44, 22)), ('spruce', (104, 78, 46)),
    ('oak', (160, 130, 78)), ('birch', (200, 184, 120)), ('jungle', (160, 116, 80)), ('acacia', (170, 92, 52)), ('cherry', (226, 170, 170)),
    ('mangrove', (118, 54, 52)), ('crimson', (110, 50, 70)), ('warped', (44, 120, 110)), ('bamboo', (200, 180, 80)),
    ('mud_brick', (140, 108, 80)), ('tuff', (110, 112, 104)), ('brick', (160, 80, 60)), ('calcite', (225, 225, 220)), ('quartz', (235, 232, 225)),
    ('diorite', (200, 200, 198)), ('granite', (150, 100, 80)), ('sandstone', (220, 205, 160)), ('copper', (190, 110, 80)), ('gravel', (130, 125, 120)),
    ('dirt', (110, 80, 55)), ('grass', (80, 130, 60)), ('leaves', (50, 110, 50)), ('hay', (200, 170, 40)), ('wool', (215, 215, 215)),
    ('bookshelf', (130, 100, 60)), ('barrel', (120, 88, 48)), ('chest', (150, 110, 40)), ('furnace', (110, 110, 110)), ('smoker', (110, 100, 90)),
    ('blast_furnace', (100, 100, 105)), ('anvil', (60, 60, 64)), ('grindstone', (140, 140, 140)), ('cauldron', (60, 60, 64)),
    ('lantern', (255, 200, 90)), ('candle', (240, 230, 200)), ('torch', (255, 190, 60)), ('crafting', (150, 110, 60)), ('table', (150, 110, 60)),
    ('lectern', (150, 110, 60)), ('loom', (170, 130, 80)), ('cartography', (140, 110, 80)), ('fletching', (200, 180, 120)), ('smithing', (60, 50, 60)),
    ('brewing', (120, 120, 100)), ('glass', (170, 210, 230)), ('pane', (170, 210, 230)), ('water', (64, 110, 220)), ('lava', (235, 120, 30)),
    ('iron', (200, 200, 200)), ('chain', (60, 70, 80)), ('bars', (110, 112, 116)), ('ladder', (140, 110, 60)), ('door', (130, 96, 56)),
    ('bed', (180, 40, 40)), ('carpet', (160, 40, 40)), ('terracotta', (150, 90, 70)), ('concrete', (130, 130, 130)), ('log', (100, 75, 50)),
    ('wood', (100, 75, 50)), ('planks', (140, 100, 60)), ('slab', (140, 100, 60)), ('stairs', (140, 100, 60)), ('fence', (140, 100, 60)),
    ('flower', (220, 80, 120)), ('potted', (200, 90, 110)), ('azalea', (80, 130, 60)), ('fern', (60, 120, 60)), ('mushroom', (200, 60, 60)),
    ('skull', (230, 230, 220)), ('banner', (180, 40, 40)), ('sign', (150, 110, 60)), ('jukebox', (110, 70, 50)), ('note_block', (120, 80, 50)),
    ('amethyst', (150, 100, 200)), ('obsidian', (25, 20, 40)), ('composter', (110, 80, 40)), ('decorated_pot', (170, 80, 60)), ('cake', (240, 230, 220)),
    ('dried_kelp', (50, 70, 40)), ('bell', (230, 190, 60)), ('soul', (80, 200, 220)), ('end_rod', (240, 240, 240)),
]
DYE = {'white': (230, 230, 230), 'orange': (230, 130, 40), 'magenta': (190, 70, 190), 'light_blue': (90, 160, 220), 'yellow': (230, 200, 50),
       'lime': (120, 190, 40), 'pink': (230, 140, 170), 'gray': (80, 84, 88), 'light_gray': (150, 150, 145), 'cyan': (30, 130, 140),
       'purple': (120, 50, 170), 'blue': (50, 60, 160), 'brown': (110, 70, 40), 'green': (80, 110, 40), 'red': (170, 40, 40), 'black': (30, 30, 34)}


def base_name(s):
    return s.split('[')[0].replace('minecraft:', '').replace('skyabyss:', '')


def color_of(n):
    for d, c in DYE.items():
        if n.startswith(d + '_') and any(k in n for k in ('wool', 'carpet', 'bed', 'banner', 'terracotta', 'concrete', 'candle', 'glass')):
            return c
    for k, c in COL:
        if k in n:
            return c
    return (150, 150, 150)


def prop(s, k, default=None):
    m = re.search(r'[\[,]' + k + r'=([a-z0-9_]+)', s)
    return m.group(1) if m else default


def boxes(s):
    """(x0,y0,z0,x1,y1,z1) の一覧 (0..1)。None = 描かない"""
    n = base_name(s)
    if n in AIRS:
        return None
    F = [(0, 0, 0, 1, 1, 1)]
    if n.endswith('_slab'):
        t = prop(s, 'type', 'bottom')
        return F if t == 'double' else [(0, .5, 0, 1, 1, 1)] if t == 'top' else [(0, 0, 0, 1, .5, 1)]
    if n.endswith('_stairs'):
        f = prop(s, 'facing', 'north')
        top = prop(s, 'half', 'bottom') == 'top'
        y0 = .5 if top else 0
        base = (0, y0, 0, 1, y0 + .5, 1)
        yy = 0 if top else .5
        back = {'north': (0, yy, 0, 1, yy + .5, .5), 'south': (0, yy, .5, 1, yy + .5, 1),
                'west': (0, yy, 0, .5, yy + .5, 1), 'east': (.5, yy, 0, 1, yy + .5, 1)}[f]
        return [base, back]
    if n.endswith('_fence') or n.endswith('_wall') or n.endswith('_bars') or n.endswith('_pane') or n == 'iron_bars':
        return [(.375, 0, .375, .625, 1, .625)]
    if n.endswith('_fence_gate'):
        return [(0, .3, .4, 1, 1, .6)]
    if 'carpet' in n or 'pressure_plate' in n or n in ('snow',):
        return [(0, 0, 0, 1, .08, 1)]
    if n.endswith('_trapdoor'):
        if prop(s, 'open') == 'true':
            f = prop(s, 'facing', 'north')
            return [{'north': (0, 0, .82, 1, 1, 1), 'south': (0, 0, 0, 1, 1, .18), 'west': (.82, 0, 0, 1, 1, 1), 'east': (0, 0, 0, .18, 1, 1)}[f]]
        return [(0, .82, 0, 1, 1, 1)] if prop(s, 'half') == 'top' else [(0, 0, 0, 1, .18, 1)]
    if n.endswith('_door'):
        f = prop(s, 'facing', 'north')
        return [{'north': (0, 0, .8, 1, 1, 1), 'south': (0, 0, 0, 1, 1, .2), 'west': (.8, 0, 0, 1, 1, 1), 'east': (0, 0, 0, .2, 1, 1)}[f]]
    if n.endswith('_bed'):
        return [(0, 0, 0, 1, .56, 1)]
    if n == 'lantern' or n == 'soul_lantern':
        h = prop(s, 'hanging') == 'true'
        return [(.3, .1 if h else 0, .3, .7, .6 if h else .5, .7)]
    if n == 'chain':
        return [(.45, 0, .45, .55, 1, .55)]
    if 'candle' in n and 'cake' not in n:
        return [(.35, 0, .35, .65, .35, .65)]
    if n in ('torch', 'soul_torch', 'redstone_torch'):
        return [(.4, 0, .4, .6, .7, .6)]
    if n.endswith('wall_torch') or n.endswith('wall_banner') or n.endswith('wall_sign') or n == 'ladder' or n.endswith('wall_hanging_sign') or n.endswith('wall_head') or n.endswith('wall_skull'):
        f = prop(s, 'facing', 'north')
        t = .12
        return [{'north': (0, 0, 1 - t, 1, 1, 1), 'south': (0, 0, 0, 1, 1, t), 'west': (1 - t, 0, 0, 1, 1, 1), 'east': (0, 0, 0, t, 1, 1)}[f]]
    if n.endswith('banner') or n.endswith('_sign') or n.endswith('_hanging_sign'):
        return [(.4, 0, .4, .6, 1, .6)]
    if n.startswith('potted_') or n in ('flower_pot',):
        return [(.3, 0, .3, .7, .6, .7)]
    if n.endswith('_head') or n.endswith('_skull'):
        return [(.25, 0, .25, .75, .5, .75)]
    if n in ('anvil', 'chipped_anvil', 'damaged_anvil'):
        return [(.2, 0, .2, .8, .3, .8), (.3, .3, .3, .7, 1, .7)]
    if n in ('cauldron', 'water_cauldron', 'lava_cauldron', 'powder_snow_cauldron'):
        return [(0, .25, 0, 1, .95, 1)]
    if n == 'brewing_stand':
        return [(.1, 0, .1, .9, .15, .9), (.45, 0, .45, .55, .85, .55)]
    if n == 'grindstone':
        return [(.15, .1, .2, .85, .95, .8)]
    if n.endswith('chest'):
        return [(.06, 0, .06, .94, .88, .94)]
    if n in ('cake',):
        return [(.06, 0, .06, .94, .5, .94)]
    if n == 'composter':
        return [(0, 0, 0, 1, .9, 1)]
    if n in ('amethyst_cluster', 'end_rod'):
        return [(.3, 0, .3, .7, .7, .7)]
    if n in ('short_grass', 'fern', 'tall_grass', 'dandelion', 'poppy') or n.endswith('tulip') or n.endswith('orchid') or n in ('allium', 'cornflower', 'lily_of_the_valley', 'azure_bluet', 'oxeye_daisy'):
        return [(.3, 0, .3, .7, .6, .7)]
    if n == 'decorated_pot':
        return [(.12, 0, .12, .88, 1, .88)]
    if n in ('bell',):
        return [(.25, .2, .25, .75, .8, .75)]
    if n.endswith('lectern'):
        return [(0, 0, 0, 1, .3, 1), (.2, .3, .2, .8, 1, .8)]
    if n in ('skeleton_skull', 'player_head'):
        return [(.25, 0, .25, .75, .5, .75)]
    if n == 'water':
        return [(0, 0, 0, 1, .85, 1)]
    return F


def view_xform(x, z, mode, X, Z):
    """向きを回す: 'se' = 南東の上から (標準)"""
    if mode == 'se':
        return x, z
    if mode == 'sw':
        return z, X - x
    if mode == 'nw':
        return X - x, Z - z
    return Z - z, x   # ne


def render(b, rect, layer_y, h, out, mode='se', S=28, low_walls=True):
    """rect = (x0, z0, x1, z1) 内の空き。layer_y = 床の上 y。h = 部屋の高さ"""
    x0, z0, x1, z1 = rect
    X0, X1, Z0, Z1 = x0 - 1, x1 + 1, z0 - 1, z1 + 1
    Y0, Y1 = layer_y - 1, layer_y + h - 2      # 天井と梁の層は外す
    cells = []
    for (x, y, z), s in b.items():
        if not (X0 <= x <= X1 and Z0 <= z <= Z1 and Y0 <= y <= Y1):
            continue
        n = base_name(s)
        if n in AIRS:
            continue
        # 手前の壁 (見る側) は低く
        if low_walls:
            near = {'se': (x == X1 or z == Z1), 'sw': (x == X0 or z == Z1), 'nw': (x == X0 or z == Z0), 'ne': (x == X1 or z == Z0)}[mode]
            if near and y >= layer_y and (x in (X0, X1) or z in (Z0, Z1)):
                continue
        cells.append((x, y, z, s))
    W, D = X1 - X0 + 1, Z1 - Z0 + 1
    polys = []
    for (x, y, z, s) in cells:
        bx = boxes(s)
        if not bx:
            continue
        n = base_name(s)
        c = color_of(n)
        glass = 'glass' in n or n.endswith('_pane')
        for (a0, b0, c0, a1, b1, c1) in bx:
            polys.append((x - X0, y - Y0, z - Z0, a0, b0, c0, a1, b1, c1, c, glass, n))
    # 向きを回す
    def tr(px, pz, ax, az):
        return view_xform(px, pz, mode, W, D)
    rot = []
    for (x, y, z, a0, b0, c0, a1, b1, c1, c, g, n) in polys:
        # 角を回して箱を作り直す
        pts = [view_xform(x + a, z + cc, mode, W - 0, D - 0) for a in (a0, a1) for cc in (c0, c1)]
        # view_xform は整数のマス用なので、連続座標にそのまま使うため +1 補正
        if mode == 'se':
            nx0, nx1, nz0, nz1 = x + a0, x + a1, z + c0, z + c1
        elif mode == 'sw':   # (x, z) -> (z, W - x)
            nx0, nx1 = z + c0, z + c1
            nz0, nz1 = (W - x) - a1, (W - x) - a0
        elif mode == 'nw':   # (x, z) -> (W - x, D - z)
            nx0, nx1 = (W - x) - a1, (W - x) - a0
            nz0, nz1 = (D - z) - c1, (D - z) - c0
        else:                # ne: (x, z) -> (D - z, x)
            nx0, nx1 = (D - z) - c1, (D - z) - c0
            nz0, nz1 = x + a0, x + a1
        rot.append((nx0, y + b0, nz0, nx1, y + b1, nz1, c, g, (x + y + z)))
    SW = max(W, D)
    rot.sort(key=lambda r: ((r[0] + r[2]) / 2 + (r[1] + r[4]) / 2 * 1.0, r[1]))
    H = Y1 - Y0 + 1
    Wp = int((W + D) * S + 40)
    Hp = int(((W + D) * 0.5 + H * 0.85) * S + 40)
    img = Image.new('RGB', (Wp, Hp), (36, 38, 44))
    dr = ImageDraw.Draw(img, 'RGBA')
    ox, oy = D * S + 20, int(H * 0.85 * S) + 20

    def P(x, y, z):
        return (ox + (x - z) * S, oy + (x + z) * S * 0.5 - y * S * 0.85)

    for (x0_, y0_, z0_, x1_, y1_, z1_, c, g, _) in rot:
        al = 110 if g else 255
        top = tuple(min(255, int(v * 1.12)) for v in c) + (al,)
        left = tuple(int(v * 0.80) for v in c) + (al,)
        right = tuple(int(v * 0.62) for v in c) + (al,)
        ln = tuple(int(v * 0.45) for v in c) + (al,)
        dr.polygon([P(x0_, y1_, z0_), P(x1_, y1_, z0_), P(x1_, y1_, z1_), P(x0_, y1_, z1_)], fill=top, outline=ln)
        dr.polygon([P(x0_, y1_, z1_), P(x1_, y1_, z1_), P(x1_, y0_, z1_), P(x0_, y0_, z1_)], fill=left, outline=ln)
        dr.polygon([P(x1_, y1_, z0_), P(x1_, y1_, z1_), P(x1_, y0_, z1_), P(x1_, y0_, z0_)], fill=right, outline=ln)
    bb = img.getbbox()
    img.save(out)
    return img


ROOMS = [
    # (名前, (x0, z0, x1, z1), 床の上 y, 高さ, 見る向き)
    ('smithy', (10, 10, 24, 18), 0, 7, 'se'),
    ('inn', (-42, 8, -10, 24), 0, 6, 'se'),
    ('inn_upper', (-42, 8, -10, 24), 7, 5, 'se'),
    ('apothecary', (55, 8, 64, 15), 0, 6, 'sw'),
    ('tool_shop', (35, -36, 43, -30), 0, 6, 'se'),
    ('guild', (52, -32, 76, -12), 0, 6, 'se'),
    ('storage', (34, -14, 42, -9), 0, 6, 'se'),
    ('trade', (84, -24, 91, -15), 0, 6, 'se'),
    ('stable', (84, 8, 91, 15), 0, 6, 'se'),
    ('tavern', (-38, 38, -30, 44), 0, 5, 'se'),
    ('mapmaker', (85, 71, 92, 77), 0, 5, 'se'),
    ('mapmaker_loft', (85, 71, 92, 77), 6, 4, 'se'),
    ('keep', (20, -78, 35, -71), 5, 6, 'se'),
    ('hall', (-56, -94, -42, -86), 5, 6, 'se'),
    ('shrine', (-78, -35, -50, -7), 0, 12, 'se'),
    ('perch', (-92, 8, -86, 14), 0, 5, 'se'),
    ('black_market', (55, 42, 61, 48), 0, 5, 'se'),
    ('storyteller', (-17, -80, -11, -74), 5, 4, 'se'),
    ('tavern_cellar', (-38, 38, -30, 44), -5, 4, 'se'),
    ('gravekeeper', (-132, -18, -128, -14), 0, 5, 'se'),
]


def batch(pkl, tag, outdir, only=None):
    d = pickle.load(open(pkl, 'rb'))
    os.makedirs(outdir, exist_ok=True)
    for (nm, rect, y, h, mode) in ROOMS:
        if only and nm not in only:
            continue
        render(d['b'], rect, y, h, os.path.join(outdir, 'interior_%s_%s.png' % (nm, tag)), mode)
        print('rendered', nm)


def sym(s):
    if s is None:
        return ' .'
    n = base_name(s)
    if n in AIRS:
        return ' .'
    t = {'barrel': 'Ba', 'chest': 'Ch', 'lantern': 'La', 'chain': 'ch', 'bookshelf': 'Bk', 'chiseled_bookshelf': 'Bk', 'anvil': 'An', 'furnace': 'Fu',
         'smoker': 'Sm', 'blast_furnace': 'BF', 'grindstone': 'Gr', 'cauldron': 'Ca', 'water_cauldron': 'Wc', 'lava_cauldron': 'Lc', 'brewing_stand': 'Br', 'crafting_table': 'Cr',
         'cartography_table': 'Mp', 'smithing_table': 'Sw', 'fletching_table': 'Fl', 'lectern': 'Le', 'loom': 'Lo', 'composter': 'Co', 'hay_block': 'Hy', 'cake': 'Ck',
         'decorated_pot': 'Dp', 'jukebox': 'Ju', 'note_block': 'Nb', 'ladder': 'Ld', 'iron_bars': '##'}
    if n in t:
        return t[n]
    if n.endswith('_stairs'):
        return {'north': '^s', 'south': 'vs', 'east': '>s', 'west': '<s'}[prop(s, 'facing', 'north')] if prop(s, 'half') != 'top' else 'Ts'
    if n.endswith('_slab'):
        return '__' if prop(s, 'type') != 'top' else '^^'
    if n.endswith('_fence'):
        return 'ff'
    if n.endswith('_fence_gate'):
        return 'fg'
    if n.endswith('_door'):
        return 'Dr'
    if n.endswith('_trapdoor'):
        return 'Td'
    if n.endswith('_bed'):
        return 'Bd'
    if 'carpet' in n:
        return 'cp'
    if 'pressure_plate' in n:
        return 'pp'
    if 'candle' in n:
        return 'ca'
    if n.startswith('potted'):
        return 'pt'
    if 'wall_' in n:
        return 'w_'
    if 'torch' in n:
        return 'to'
    if n.endswith('pane'):
        return '||'
    if 'banner' in n or 'sign' in n:
        return 'bn'
    if 'skull' in n or 'head' in n:
        return 'sk'
    if 'wool' in n:
        return 'Wo'
    if 'leaves' in n:
        return 'lv'
    return '[]'


def ascii_plan(b, rect, y, h):
    x0, z0, x1, z1 = rect
    print('     ' + ''.join('%2d' % (x % 100) for x in range(x0 - 1, x1 + 2)))
    for lvl in range(0, h - 1):
        print('--- y=%d (floor+%d)  x %d..%d' % (y + lvl, lvl, x0 - 1, x1 + 1))
        for z in range(z0 - 1, z1 + 2):
            print('%4d ' % z + ''.join(sym(b.get((x, y + lvl, z))) for x in range(x0 - 1, x1 + 2)))


def dump(path):
    import town
    import vox
    from facilities import Facilities
    rects = []
    cur = [None]
    ofb = Facilities.fac_body
    oroom = town.Town.room

    def fb(self, name, x0, z0, x1, z1, front, floors=2, fh=6, *a, **k):
        cur[0] = name
        rects.append(('body', name, (x0, z0, x1, z1), 0, fh, floors))
        return ofb(self, name, x0, z0, x1, z1, front, floors, fh, *a, **k)

    def rm(self, x0, z0, x1, z1, y, h, *a, **k):
        rects.append(('room', cur[0], (x0, z0, x1, z1), y, h, 0))
        return oroom(self, x0, z0, x1, z1, y, h, *a, **k)
    Facilities.fac_body = fb
    town.Town.room = rm
    t = town.Town(vox.STYLES['b'])
    v = t.build()
    pickle.dump({'b': v.b, 'rects': rects, 'marks': v.marks}, open(path, 'wb'))
    print('dumped', len(v.b), 'blocks', len(rects), 'rects')


def main():
    a = sys.argv[1:]
    if a[0] == 'dump':
        dump(a[1])
    elif a[0] == 'list':
        d = pickle.load(open(a[1], 'rb'))
        for r in d['rects']:
            print(r)
    elif a[0] == 'render':
        d = pickle.load(open(a[1], 'rb'))
        # 名前 = "x0,z0,x1,z1,y,h"
        x0, z0, x1, z1, y, h = [int(t) for t in a[2].split(',')]
        mode = a[5] if len(a) > 5 else 'se'
        render(d['b'], (x0, z0, x1, z1), y, h, a[3], mode)
    elif a[0] == 'batch':
        batch(a[1], a[2], a[3], a[4:] or None)
    elif a[0] == 'ascii':
        d = pickle.load(open(a[1], 'rb'))
        x0, z0, x1, z1, y, h = [int(t) for t in a[2].split(',')]
        ascii_plan(d['b'], (x0, z0, x1, z1), y, h)
    elif a[0] == 'sheet':
        ims = [Image.open(p) for p in a[2:]]
        w = 640
        ims = [im.resize((w, int(im.height * w / im.width))) for im in ims]
        cols = 2
        rows = [ims[i:i + cols] for i in range(0, len(ims), cols)]
        Hs = [max(i.height for i in r) for r in rows]
        sheet = Image.new('RGB', (cols * w, sum(Hs)), (20, 20, 24))
        yy = 0
        for r, hh in zip(rows, Hs):
            for j, im in enumerate(r):
                sheet.paste(im, (j * w, yy))
            yy += hh
        sheet.save(a[1])


if __name__ == '__main__':
    main()
