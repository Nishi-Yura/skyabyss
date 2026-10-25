#!/usr/bin/env python3
"""天と淵の大陸の、土地ごとの小さな町 (docs/design/40 の 3 章「作る順」の 4) と、外れの一本道の休み場・祠 (4 章) の部品を書き出す。

  python tools/gen/world/gen_region_towns.py
  -> src/main/resources/data/skyabyss/opening/town_{whale,tide,storm,ember}.json・outskirts_fire.json・outskirts_shrine.json

形式は tools/gen/ch1/gen_ch1_blueprints.py と同じ (palette / blocks [x,y,z,palette 番号] / marks / meta)。
座標は町の広場の中心が (0, 0)、y=0 が地面の一つ上 (地面の上面は y=-1)。北 (-z) 向きで書き、置く向きは SiteJob が回す。
町の並び: 広場 (井戸・依頼の板・街灯 4) を真ん中に、北西に宿、北東に店、南西と南東に家。十字の道が町の外まで。
土地ごとに色を変える (岩鯨 = 針葉樹と丸石、潮殻 = 白樺と砂岩、雷翼 = 石煉瓦と錆びた銅、余燼 = 泥煉瓦と黒い石)。
印: rest (休み場。広場の井戸の脇)、board (依頼の板)、sign (町の名前と目安のレベルの札)、inn / shop (戸の前)、npc_innkeeper / npc_tool_merchant (宿の主人と道具屋の立つ所。facility/Attendants の役の印)、inn_bed (宿で休むと目覚める寝台)
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss', 'opening')

PALETTES = {
    'whale': {
        'wall': 'minecraft:spruce_planks', 'frame': 'minecraft:stripped_spruce_log[axis=y]', 'floor': 'minecraft:cobblestone',
        'roof': 'minecraft:spruce_stairs', 'ridge': 'minecraft:spruce_slab[type=bottom,waterlogged=false]',
        'plaza': 'minecraft:cobblestone', 'plaza2': 'minecraft:mossy_cobblestone', 'road': 'minecraft:dirt_path',
        'door': 'minecraft:spruce_door', 'fence': 'minecraft:spruce_fence', 'well': 'minecraft:stone_bricks', 'awning': 'warm',
    },
    'tide': {
        'wall': 'minecraft:birch_planks', 'frame': 'minecraft:stripped_birch_log[axis=y]', 'floor': 'minecraft:smooth_sandstone',
        'roof': 'minecraft:dark_prismarine_stairs', 'ridge': 'minecraft:dark_prismarine_slab[type=bottom,waterlogged=false]',
        'plaza': 'minecraft:smooth_sandstone', 'plaza2': 'minecraft:cut_sandstone', 'road': 'minecraft:sandstone',
        'door': 'minecraft:birch_door', 'fence': 'minecraft:birch_fence', 'well': 'minecraft:cut_sandstone', 'awning': 'sea',
    },
    'storm': {
        'wall': 'minecraft:stone_bricks', 'frame': 'minecraft:dark_oak_log[axis=y]', 'floor': 'minecraft:polished_andesite',
        'roof': 'minecraft:oxidized_cut_copper_stairs', 'ridge': 'minecraft:oxidized_cut_copper_slab[type=bottom,waterlogged=false]',
        'plaza': 'minecraft:polished_andesite', 'plaza2': 'minecraft:andesite', 'road': 'minecraft:gravel',
        'door': 'minecraft:dark_oak_door', 'fence': 'minecraft:dark_oak_fence', 'well': 'minecraft:stone_bricks', 'awning': 'storm',
    },
    'ember': {
        'wall': 'minecraft:mud_bricks', 'frame': 'minecraft:polished_blackstone', 'floor': 'minecraft:polished_blackstone_bricks',
        'roof': 'minecraft:polished_blackstone_brick_stairs', 'ridge': 'minecraft:polished_blackstone_brick_slab[type=bottom,waterlogged=false]',
        'plaza': 'minecraft:polished_blackstone_bricks', 'plaza2': 'minecraft:cracked_polished_blackstone_bricks', 'road': 'minecraft:coarse_dirt',
        'door': 'minecraft:crimson_door', 'fence': 'minecraft:crimson_fence', 'well': 'minecraft:polished_blackstone_bricks', 'awning': 'ash',
    },
}

HALF = 28


class BP:
    def __init__(self, name):
        self.name = name
        self.cells = {}
        self.marks = {}
        self.palette = []
        # 範囲の半分の一辺 (町は HALF)
        self.meta_rect = HALF

    def idx(self, state):
        if state not in self.palette:
            self.palette.append(state)
        return self.palette.index(state)

    def set(self, x, y, z, state):
        self.cells[(x, y, z)] = self.idx(state)

    def get(self, x, y, z):
        i = self.cells.get((x, y, z))
        return None if i is None else self.palette[i]

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, state)

    def mark(self, name, x, y, z):
        self.marks[name] = [x, y, z]

    def save(self):
        blocks = [[x, y, z, i] for (x, y, z), i in sorted(self.cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2]))]
        data = {
            'site': self.name,
            'note': '天と淵の大陸の土地の町 (docs/design/40 の 3 章)。tools/gen/world/gen_region_towns.py が書く',
            'palette': self.palette,
            'blocks': blocks,
            'marks': self.marks,
            'meta': {'rect': [-self.meta_rect, -self.meta_rect, self.meta_rect, self.meta_rect], 'level': True, **({'round': True} if getattr(self, 'meta_round', False) else {}), 'ring': getattr(self, 'meta_ring', None) or (5 if self.meta_rect >= HALF else 3)},
        }
        with open(os.path.join(OUT, self.name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(data, f, ensure_ascii=False, separators=(',', ':'))
            f.write('\n')
        return len(blocks)


def stairs(block, facing, half='bottom'):
    return f'{block}[facing={facing},half={half},shape=straight,waterlogged=false]'


def door(block, facing, half):
    return f'{block}[facing={facing},half={half},hinge=left,open=false,powered=false]'


def fence_post_lantern(bp, x, z, pal):
    bp.fill(x, 0, z, x, 2, z, pal['fence'])
    bp.set(x, 3, z, 'minecraft:lantern[hanging=false,waterlogged=false]')


def house(bp, x0, z0, x1, z1, h, door_at, pal, beds=0, counter=False):
    """四角い家。x0..x1 × z0..z1 の外壁、高さ h (壁は y=0..h-1)、切妻の屋根は x に沿う。door_at = (x, z, facing)"""
    # 床
    bp.fill(x0, -1, z0, x1, -1, z1, pal['floor'])
    # 中を空ける (地形の草などを消す)
    bp.fill(x0 + 1, 0, z0 + 1, x1 - 1, h - 1, z1 - 1, 'minecraft:air')
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            for y in range(h):
                bp.set(x, y, z, pal['wall'])
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            for y in range(h):
                bp.set(x, y, z, pal['wall'])
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        bp.fill(x, 0, z, x, h - 1, z, pal['frame'])
    # 窓 (長い壁に 3 ごと)
    for x in range(x0 + 2, x1 - 1, 3):
        for z in (z0, z1):
            bp.set(x, 1, z, 'minecraft:glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]')
    # 戸
    dx, dz, facing = door_at
    bp.set(dx, 0, dz, door(pal['door'], facing, 'lower'))
    bp.set(dx, 1, dz, door(pal['door'], facing, 'upper'))
    # 屋根 (切妻。棟は x に沿う)。階段は高い側の向き: 北の斜面は南へ上がる (facing=south)
    zc = (z0 + z1) / 2
    layer = 0
    a, b = z0 - 1, z1 + 1
    while a < b:
        y = h + layer
        for x in range(x0 - 1, x1 + 2):
            bp.set(x, y, a, stairs(pal['roof'], 'south'))
            bp.set(x, y, b, stairs(pal['roof'], 'north'))
        # 妻の壁 (屋根の下の三角)
        if layer > 0:
            for z in range(a + 1, b):
                bp.set(x0, y - 1, z, pal['wall'])
                bp.set(x1, y - 1, z, pal['wall'])
        a += 1
        b -= 1
        layer += 1
    if a == b:
        for x in range(x0 - 1, x1 + 2):
            bp.set(x, h + layer, a, pal['ridge'])
        for z in range(a, a + 1):
            bp.set(x0, h + layer - 1, z, pal['wall'])
            bp.set(x1, h + layer - 1, z, pal['wall'])
    # 天井の下を閉じる (屋根の内側の空気は残す)
    # 明かり
    bp.set((x0 + x1) // 2, h - 1, int(zc), 'minecraft:lantern[hanging=true,waterlogged=false]')
    # 寝台
    for i in range(beds):
        bx = x0 + 1 + i * 2
        if bx >= x1:
            break
        bp.set(bx, 0, z0 + 1, f'minecraft:red_bed[facing=south,occupied=false,part=head]')
        bp.set(bx, 0, z0 + 2, f'minecraft:red_bed[facing=south,occupied=false,part=foot]')
    if counter:
        for x in range(x0 + 1, x1):
            if x != dx:
                bp.set(x, 0, z0 + 2, 'minecraft:barrel[facing=up,open=false]')
        bp.set(x0 + 1, 0, z0 + 1, 'minecraft:barrel[facing=south,open=false]')


def town(region):
    pal = PALETTES[region]
    bp = BP('town_' + region)
    # 道 (十字。町の外の縁まで)
    for i in range(-HALF, HALF + 1):
        for w in (-1, 0, 1):
            bp.set(w, -1, i, pal['road'])
            bp.set(i, -1, w, pal['road'])
    # 広場
    for x in range(-6, 7):
        for z in range(-6, 7):
            bp.set(x, -1, z, pal['plaza2'] if (x * 7 + z * 3) % 5 == 0 else pal['plaza'])
    # 井戸 (真ん中。余燼は焚き火の炉)
    if region == 'ember':
        bp.fill(-1, 0, -1, 1, 0, 1, pal['well'])
        bp.set(0, 0, 0, 'minecraft:magma_block')
        bp.set(0, 1, 0, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    else:
        bp.fill(-1, 0, -1, 1, 0, 1, pal['well'])
        bp.set(0, 0, 0, 'minecraft:water[level=0]')
        bp.set(0, -1, 0, pal['well'])
        for x, z in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            bp.fill(x, 1, z, x, 2, z, pal['fence'])
        bp.fill(-1, 3, -1, 1, 3, 1, pal['ridge'])
    bp.mark('rest', 2, 0, 2)
    # 依頼の板 (広場の北の縁。言伝ての板 = 日替わり・週替わりの頼み。quest/free/MessageBoardBlock)
    bp.set(-1, 0, -5, pal['fence'])
    bp.set(1, 0, -5, pal['fence'])
    bp.set(0, 0, -5, 'skyabyss:message_board[facing=south]')
    bp.mark('board', 0, 0, -4)
    # 町の名前と目安のレベルの札 (字は建てたあとに ContinentTowns が書く)
    bp.set(3, 0, -5, 'minecraft:oak_sign[rotation=0,waterlogged=false]')
    bp.mark('sign', 3, 0, -5)
    # 街灯
    for x, z in ((-6, -6), (6, -6), (-6, 6), (6, 6)):
        fence_post_lantern(bp, x, z, pal)
    # 宿 (北西)・店 (北東)・家 (南西・南東)
    house(bp, -14, -15, -4, -8, 4, (-9, -8, 'south'), pal, beds=4)
    bp.mark('inn', -9, 0, -7)
    bp.mark('npc_innkeeper', -6, 0, -10)
    bp.mark('inn_bed', -13, 0, -14)
    house(bp, 4, -14, 12, -8, 4, (8, -8, 'south'), pal, counter=True)
    bp.mark('shop', 8, 0, -7)
    bp.mark('npc_tool_merchant', 8, 0, -11)
    house(bp, -13, 5, -6, 11, 4, (-9, 5, 'north'), pal, beds=1)
    house(bp, 6, 5, 13, 11, 4, (9, 5, 'north'), pal, beds=1)
    # 戸の前の踏み石
    for x, z in ((-9, -7), (8, -7), (-9, 4), (9, 4)):
        bp.set(x, -1, z, pal['plaza'])
    # 外側の家 (東西の道の先の、北と南)
    house(bp, -19, -6, -14, -3, 3, (-16, -3, 'south'), pal, beds=1)
    house(bp, 14, 3, 19, 6, 3, (17, 3, 'north'), pal, beds=1)
    # 市の屋台 (広場の南の角。色の布の屋根と、品の樽・干し草)
    for sx, wool in ((-5, AWNING[pal['awning']][0]), (3, AWNING[pal['awning']][1])):
        for dx in (0, 2):
            bp.fill(sx + dx, 0, 4, sx + dx, 1, 4, pal['fence'])
        bp.fill(sx, 2, 3, sx + 2, 2, 5, wool)
        bp.set(sx + 1, 0, 4, 'minecraft:barrel[facing=up,open=false]')
        bp.set(sx, 0, 5, 'minecraft:hay_block[axis=y]' if sx < 0 else 'minecraft:composter[level=0]')
    # 腰かけ (広場の東西の縁)
    for z in (-3, 3):
        bp.set(-5, 0, z, stairs(pal['roof'], 'west'))
        bp.set(5, 0, z, stairs(pal['roof'], 'east'))
    # 花の植え込み (宿と店の前)
    for x0 in (-13, 5):
        for i in range(4):
            bp.set(x0 + i, -1, -6, 'minecraft:grass_block[snowy=false]')
            bp.set(x0 + i, 0, -6, FLOWERS[i % len(FLOWERS)])
    # 町の入口の門 (四つの道の、広場から 27。柱二本と梁、吊り灯り二つ)
    for d in (27, -27):
        town_gate(bp, 0, d, 'x', pal)
        town_gate(bp, d, 0, 'z', pal)
    # 外の輪の家と、土地ごとの名物
    outer_ring(bp, region, pal)
    # 宿の二階 (床と梯子と窓)。宿の屋根は二階の上へ上げる
    inn_upper(bp, -14, -15, -4, -8, pal)
    # 煙突 (宿・店・南の家二つ)
    for x, z in ((-13, -14), (11, -13), (-12, 10), (12, 10)):
        top = 9 if x == -13 else 6
        bp.fill(x, 0, z, x, top, z, 'minecraft:cobblestone' if region != 'ember' else 'minecraft:polished_blackstone_bricks')
        bp.set(x, top + 1, z, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    # 木 (土地の木) と、外の家の脇の畑
    for x, z in ((16, -12), (-16, 12), (-9, 15), (9, -17), (-25, -12), (25, 12), (-12, 25), (12, -25)):
        tree(bp, x, z, region)
    for (gx0, gz0, gx1, gz1) in ((15, 8, 19, 12), (-19, -12, -15, -8)):
        for x in range(gx0, gx1 + 1):
            for z in range(gz0, gz1 + 1):
                if x in (gx0, gx1) or z in (gz0, gz1):
                    bp.set(x, 0, z, pal['fence'])
                else:
                    bp.set(x, -1, z, 'minecraft:farmland[moisture=7]')
                    bp.set(x, 0, z, 'minecraft:wheat[age=7]' if (x + z) % 2 else 'minecraft:carrots[age=7]')
        bp.set((gx0 + gx1) // 2, 0, gz0, f"{pal['fence']}_gate[facing=north,in_wall=false,open=false,powered=false]")
    bp.mark('stand', 0, 0, 3)
    return bp


# 外の輪の家 (どの町にも。名物と重なる所は名物を建てる)
OUTER = {
    'nw': ((-24, -24, -17, -18), (-20, -18, 'south')),
    'ne': ((17, -25, 24, -19), (20, -19, 'south')),
    'sw': ((-25, 17, -18, 23), (-21, 17, 'north')),
    'se': ((16, 18, 23, 24), (19, 18, 'north')),
    'w': ((-26, 4, -20, 9), (-23, 4, 'north')),
    'e': ((20, -9, 26, -4), (23, -4, 'south')),
    'n': ((-9, -26, -3, -21), (-6, -21, 'south')),
    's': ((4, 21, 10, 26), (7, 21, 'north')),
}
# 名物が使う区画
LANDMARK_SLOTS = {'whale': ('ne',), 'tide': ('se',), 'storm': ('nw', 'se'), 'ember': ('e',)}


def outer_ring(bp, region, pal):
    used = LANDMARK_SLOTS[region]
    for k, ((x0, z0, x1, z1), door_at) in OUTER.items():
        if k in used:
            continue
        house(bp, x0, z0, x1, z1, 3 + (1 if k in ('n', 's') else 0), door_at, pal, beds=1)
        dx, dz, facing = door_at
        step = {'south': (0, 1), 'north': (0, -1), 'east': (1, 0), 'west': (-1, 0)}[facing]
        bp.set(dx + step[0], -1, dz + step[1], pal['plaza'])
        # 戸の脇の樽か花の鉢
        side = (dx + 2 * (1 if step[1] else 0), dz + 2 * (1 if step[0] else 0))
        bp.set(side[0] + step[0], 0, side[1] + step[1], 'minecraft:barrel[facing=up,open=false]' if (x0 + z0) % 2 else 'minecraft:potted_red_tulip')
    {'whale': whale_landmark, 'tide': tide_landmark, 'storm': storm_landmark, 'ember': ember_landmark}[region](bp, pal)


def whale_landmark(bp, pal):
    """鯨見の町: 鯨を見張る高い塔 (北東) と、北の門の上の鯨の骨のアーチ"""
    cx, cz = 20, -22
    for y in range(0, 16):
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                edge = max(abs(x - cx), abs(z - cz)) == 2
                if edge:
                    corner = abs(x - cx) == 2 and abs(z - cz) == 2
                    bp.set(x, y, z, pal['frame'] if corner else ('minecraft:cobblestone' if y < 5 else pal['wall']))
                else:
                    bp.set(x, y, z, AIR_STATE)
    for y in range(0, 16):
        bp.set(cx, y, cz - 1, 'minecraft:ladder[facing=south,waterlogged=false]')
    bp.set(cx, 0, cz + 2, door(pal['door'], 'south', 'lower'))
    bp.set(cx, 1, cz + 2, door(pal['door'], 'south', 'upper'))
    for y in (5, 9, 13):
        bp.set(cx + 2, y, cz, 'minecraft:glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]')
        bp.set(cx - 2, y, cz, 'minecraft:glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]')
    # 見張りの床と屋根 (四本柱の上に寄棟の小屋根)。鐘
    for x in range(cx - 3, cx + 4):
        for z in range(cz - 3, cz + 4):
            bp.set(x, 16, z, 'minecraft:spruce_planks')
            if max(abs(x - cx), abs(z - cz)) == 3:
                bp.set(x, 17, z, pal['fence'])
    bp.set(cx, 16, cz - 1, 'minecraft:spruce_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]')
    for x, z in ((cx - 3, cz - 3), (cx + 3, cz - 3), (cx - 3, cz + 3), (cx + 3, cz + 3)):
        bp.fill(x, 17, z, x, 19, z, pal['frame'])
    for k in range(4):
        for x in range(cx - 4 + k, cx + 5 - k):
            for z in range(cz - 4 + k, cz + 5 - k):
                if max(abs(x - cx), abs(z - cz)) == 4 - k:
                    bp.set(x, 20 + k, z, pal['ridge'] if k == 3 else 'minecraft:spruce_planks' if k == 0 else pal['ridge'])
    bp.set(cx, 19, cz, 'minecraft:bell[attachment=ceiling,facing=north,powered=false]')
    bp.set(cx, 20, cz, 'minecraft:spruce_planks')
    bp.set(cx + 2, 19, cz + 2, 'minecraft:lantern[hanging=true,waterlogged=false]')
    # 鯨の骨のアーチ (北の門のすぐ内側。肋骨二本が道をまたぐ)
    for z in (-24, -22):
        for x, y in ((-3, 0), (-3, 1), (-3, 2), (-3, 3), (-2, 4), (-2, 5), (-1, 6), (0, 6), (1, 6), (2, 5), (2, 4), (3, 3), (3, 2), (3, 1), (3, 0)):
            bp.set(x, y, z, 'minecraft:bone_block[axis=y]')
    bp.set(0, 7, -23, 'minecraft:bone_block[axis=z]')


def tide_landmark(bp, pal):
    """汐守の港: 白と青の灯台 (南東) と、網を干す棚"""
    cx, cz = 20, 21
    for y in range(0, 18):
        for x in range(cx - 3, cx + 4):
            for z in range(cz - 3, cz + 4):
                d = (x - cx) ** 2 + (z - cz) ** 2
                r = 3
                if d <= r * r + 1:
                    edge = d > (r - 1) ** 2 + 1
                    if edge:
                        bp.set(x, y, z, 'minecraft:white_terracotta' if (y // 3) % 2 == 0 else 'minecraft:light_blue_terracotta')
                    else:
                        bp.set(x, y, z, AIR_STATE)
    for y in range(0, 18):
        bp.set(cx, y, cz - 2, 'minecraft:ladder[facing=south,waterlogged=false]')
    bp.set(cx, 0, cz + 3, door(pal['door'], 'south', 'lower'))
    bp.set(cx, 1, cz + 3, door(pal['door'], 'south', 'upper'))
    for x in range(cx - 3, cx + 4):
        for z in range(cz - 3, cz + 4):
            d = (x - cx) ** 2 + (z - cz) ** 2
            if d <= 10:
                bp.set(x, 18, z, 'minecraft:smooth_sandstone')
                if d >= 7:
                    bp.set(x, 19, z, pal['fence'])
    bp.set(cx, 18, cz - 2, 'minecraft:birch_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]')
    bp.set(cx, 19, cz, 'minecraft:sea_lantern')
    for x, z in ((cx - 1, cz - 1), (cx + 1, cz - 1), (cx - 1, cz + 1), (cx + 1, cz + 1)):
        bp.set(x, 20, z, 'minecraft:glass')
    bp.fill(cx - 1, 21, cz - 1, cx + 1, 21, cz + 1, pal['ridge'])
    # 網を干す棚 (柵の柱と、垂れた網 = 蜘蛛の巣でなく紐の代わりに鎖)
    for (x0, z0) in ((-26, 12), (-26, 16)):
        for x in (x0, x0 + 5):
            bp.fill(x, 0, z0, x, 2, z0, pal['fence'])
        for x in range(x0, x0 + 6):
            bp.set(x, 3, z0, 'minecraft:birch_slab[type=bottom,waterlogged=false]')
            if 0 < x - x0 < 5:
                bp.set(x, 2, z0, 'minecraft:chain[axis=y,waterlogged=false]')
    bp.set(-23, 0, 14, 'minecraft:barrel[facing=up,open=false]')
    bp.set(-22, 0, 14, 'minecraft:barrel[facing=up,open=false]')
    bp.set(-24, 0, 14, 'minecraft:hay_block[axis=y]')


def windmill_at(bp, cx, cz, pal):
    for y in range(0, 9):
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                if max(abs(x - cx), abs(z - cz)) == 2:
                    corner = abs(x - cx) == 2 and abs(z - cz) == 2
                    bp.set(x, y, z, pal['frame'] if corner else pal['wall'])
                else:
                    bp.set(x, y, z, AIR_STATE)
    bp.set(cx, 0, cz + 2, door(pal['door'], 'south', 'lower'))
    bp.set(cx, 1, cz + 2, door(pal['door'], 'south', 'upper'))
    for k in range(3):
        for x in range(cx - 3 + k, cx + 4 - k):
            for z in range(cz - 3 + k, cz + 4 - k):
                if max(abs(x - cx), abs(z - cz)) == 3 - k:
                    bp.set(x, 9 + k, z, pal['ridge'] if k < 2 else 'minecraft:dark_oak_planks')
    # 羽根 (南の面。軸と四枚の帆)
    bp.set(cx, 6, cz + 3, 'minecraft:dark_oak_log[axis=z]')
    for i in range(1, 6):
        for (dx, dy) in ((0, i), (0, -i), (i, 0), (-i, 0)):
            bp.set(cx + dx, 6 + dy, cz + 4, 'minecraft:white_wool' if i > 1 else 'minecraft:dark_oak_planks')
            if i > 1:
                ox, oy = (1, 0) if dx == 0 else (0, 1)
                sgn = 1 if (dx > 0 or dy > 0) else -1
                bp.set(cx + dx + ox * sgn, 6 + dy + oy * -sgn, cz + 4, 'minecraft:white_wool')


def storm_landmark(bp, pal):
    """風待の里: 風車二基 (北西と南東) と、風の向きを見る旗の柱"""
    windmill_at(bp, -21, -21, pal)
    windmill_at(bp, 20, 21, pal)
    for x, z, wool in ((-2, -18, 'minecraft:yellow_wool'), (-3, 14, 'minecraft:light_gray_wool')):
        bp.fill(x, 0, z, x, 6, z, pal['fence'])
        for k in range(3):
            bp.set(x + 1 + k, 6 - (k // 2), z, wool)


def ember_landmark(bp, pal):
    """灰炉の村: 黒い石の低い囲い (道の所は開く)・門の篝火・村の東の鍛冶場"""
    R = 27
    for i in range(-R, R + 1):
        for (x, z) in ((i, -R), (i, R), (-R, i), (R, i)):
            if abs(i) <= 4:
                continue
            bp.fill(x, 0, z, x, 1, z, 'minecraft:polished_blackstone_bricks')
            bp.set(x, 2, z, 'minecraft:polished_blackstone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]' if i % 4 == 0 else AIR_STATE)
    for d in (R, -R):
        for (x, z) in ((5, d), (-5, d), (d, 5), (d, -5)):
            bp.fill(x, 0, z, x, 2, z, 'minecraft:polished_blackstone_bricks')
            bp.set(x, 3, z, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    # 鍛冶場 (東。屋根だけで三方が開く。炉・金床・溶岩の大釜)
    x0, z0, x1, z1 = 19, -10, 25, -3
    bp.fill(x0, -1, z0, x1, -1, z1, pal['floor'])
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        bp.fill(x, 0, z, x, 3, z, pal['frame'])
    for x in range(x0, x1 + 1):
        bp.fill(x, 0, z0, x, 3, z0, pal['wall'])
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            bp.set(x, 4, z, pal['ridge'])
    bp.fill(x1 - 1, 0, z0 + 1, x1 - 1, 0, z0 + 2, 'minecraft:blast_furnace[facing=west,lit=true]')
    bp.set(x0 + 2, 0, z0 + 3, 'minecraft:anvil[facing=north]')
    bp.set(x0 + 1, 0, z0 + 1, 'minecraft:lava_cauldron')
    bp.set(x0 + 4, 0, z0 + 1, 'minecraft:grindstone[face=floor,facing=north]')
    bp.set(x0 + 3, 3, z0 + 3, 'minecraft:lantern[hanging=true,waterlogged=false]')
    bp.fill(x1 - 1, 1, z0 + 1, x1 - 1, 7, z0 + 1, 'minecraft:polished_blackstone_bricks')
    bp.set(x1 - 1, 8, z0 + 1, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')


def town_gate(bp, cx, cz, along, pal):
    """道をまたぐ門。along = 'x' なら道は z に沿う (柱は x に並ぶ)"""
    for s in (-3, 3):
        x, z = (cx + s, cz) if along == 'x' else (cx, cz + s)
        bp.fill(x, 0, z, x, 4, z, pal['frame'] if 'log' in pal['frame'] else pal['frame'])
    for s in range(-3, 4):
        x, z = (cx + s, cz) if along == 'x' else (cx, cz + s)
        bp.set(x, 5, z, pal['wall'])
        bp.set(x, 6, z, pal['ridge'])
    for s in (-2, 2):
        x, z = (cx + s, cz) if along == 'x' else (cx, cz + s)
        bp.set(x, 4, z, 'minecraft:lantern[hanging=true,waterlogged=false]')


def inn_upper(bp, x0, z0, x1, z1, pal):
    """宿を二階建てに: 一階の壁の上に二階の壁 (y 4..6)、床 (y 3)、梯子、窓。屋根は y 7 から"""
    # 一階の屋根 (y 4 から) を消す
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for y in range(4, 10):
                bp.set(x, y, z, AIR_STATE)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            corner = x in (x0, x1) and z in (z0, z1)
            for y in range(3, 7):
                if edge:
                    bp.set(x, y, z, pal['frame'] if corner else pal['wall'])
                elif y == 3:
                    bp.set(x, y, z, 'minecraft:spruce_planks')
    # 梯子 (北東の角の内側)
    for y in range(0, 4):
        bp.set(x1 - 1, y, z0 + 1, 'minecraft:ladder[facing=south,waterlogged=false]')
    bp.set(x1 - 1, 3, z0 + 1, 'minecraft:ladder[facing=south,waterlogged=false]')
    for x in range(x0 + 2, x1 - 1, 3):
        for z in (z0, z1):
            bp.set(x, 4, z, 'minecraft:glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]')
    # 二階の寝台
    for i, x in enumerate(range(x0 + 1, x1 - 2, 2)):
        bp.set(x, 4, z1 - 2, 'minecraft:red_bed[facing=north,occupied=false,part=head]')
        bp.set(x, 4, z1 - 1, 'minecraft:red_bed[facing=north,occupied=false,part=foot]')
    bp.set((x0 + x1) // 2, 6, (z0 + z1) // 2, 'minecraft:lantern[hanging=true,waterlogged=false]')
    # 屋根 (y 7 から)
    a, b, layer = z0 - 1, z1 + 1, 0
    while a < b:
        y = 7 + layer
        for x in range(x0 - 1, x1 + 2):
            bp.set(x, y, a, stairs(pal['roof'], 'south'))
            bp.set(x, y, b, stairs(pal['roof'], 'north'))
        if layer > 0:
            for z in range(a + 1, b):
                bp.set(x0, y - 1, z, pal['wall'])
                bp.set(x1, y - 1, z, pal['wall'])
        a += 1
        b -= 1
        layer += 1
    if a == b:
        for x in range(x0 - 1, x1 + 2):
            bp.set(x, 7 + layer, a, pal['ridge'])


AIR_STATE = 'minecraft:air'
TREES = {
    'whale': ('minecraft:spruce_log[axis=y]', 'minecraft:spruce_leaves[distance=1,persistent=true,waterlogged=false]', 'cone'),
    'tide': ('minecraft:birch_log[axis=y]', 'minecraft:birch_leaves[distance=1,persistent=true,waterlogged=false]', 'round'),
    'storm': ('minecraft:spruce_log[axis=y]', 'minecraft:spruce_leaves[distance=1,persistent=true,waterlogged=false]', 'cone'),
    'ember': ('minecraft:dark_oak_log[axis=y]', None, 'dead'),
}


def tree(bp, x, z, region):
    trunk, leaves, shape = TREES[region]
    h = 6
    bp.fill(x, 0, z, x, h - 1, z, trunk)
    if shape == 'cone':
        for k, r in enumerate((2, 2, 1, 1, 0)):
            y = 2 + k
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    if abs(dx) + abs(dz) <= r + 1 and (dx or dz):
                        bp.set(x + dx, y, z + dz, leaves)
        bp.set(x, h, z, leaves)
    elif shape == 'round':
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                for dy in range(3, 7):
                    if dx * dx + dz * dz + (dy - 5) ** 2 <= 5 and not (dx == 0 and dz == 0 and dy < h):
                        bp.set(x + dx, dy, z + dz, leaves)
    else:
        for dx, dy, dz in ((1, 4, 0), (2, 5, 0), (-1, 3, 0), (-2, 4, 1), (0, 5, -1), (0, 6, -2)):
            bp.set(x + dx, dy, z + dz, 'minecraft:dark_oak_log[axis=x]' if dz == 0 else 'minecraft:dark_oak_log[axis=z]')


AWNING = {
    'warm': ('minecraft:red_wool', 'minecraft:white_wool'),
    'sea': ('minecraft:cyan_wool', 'minecraft:white_wool'),
    'storm': ('minecraft:yellow_wool', 'minecraft:gray_wool'),
    'ash': ('minecraft:orange_wool', 'minecraft:black_wool'),
}
FLOWERS = ['minecraft:poppy', 'minecraft:dandelion', 'minecraft:oxeye_daisy', 'minecraft:cornflower']


def outskirts_fire():
    """外れの一本道の休み場: 灯した焚き火・丸太の腰かけ・食べ物の樽・札。印 barrel・sign・stand"""
    bp = BP('outskirts_fire')
    for x in range(-4, 5):
        for z in range(-4, 5):
            if x * x + z * z <= 18:
                bp.set(x, -1, z, 'minecraft:coarse_dirt' if (x + z) % 3 else 'minecraft:podzol')
    bp.set(0, -1, 0, 'minecraft:cobblestone')
    bp.set(0, 0, 0, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    for x in (-1, 0, 1):
        bp.set(x, 0, -3, 'minecraft:stripped_spruce_log[axis=x]')
        bp.set(x, 0, 3, 'minecraft:stripped_spruce_log[axis=x]')
    for z in (-1, 0, 1):
        bp.set(-3, 0, z, 'minecraft:stripped_spruce_log[axis=z]')
    bp.set(3, 0, -1, 'minecraft:barrel[facing=up,open=false]')
    bp.mark('barrel', 3, 0, -1)
    bp.set(3, 0, 1, 'minecraft:oak_sign[rotation=4,waterlogged=false]')
    bp.mark('sign', 3, 0, 1)
    bp.mark('stand', 2, 0, 0)
    bp.meta_rect = 5
    return bp


def outskirts_shrine():
    """外れの一本道の宿り木の祠: 苔の石の台に小さな屋根と灯り。そばにいると傷が癒える (ContinentOutskirts)。印 heal・sign"""
    bp = BP('outskirts_shrine')
    for x in range(-3, 4):
        for z in range(-3, 4):
            bp.set(x, -1, z, 'minecraft:mossy_stone_bricks' if (x * 3 + z) % 4 else 'minecraft:stone_bricks')
    bp.fill(-1, 0, -1, 1, 0, 1, 'minecraft:mossy_stone_brick_slab[type=bottom,waterlogged=false]')
    for x, z in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        bp.fill(x, 0, z, x, 2, z, 'minecraft:spruce_fence')
    for x in range(-3, 4):
        bp.set(x, 3, -2, stairs('minecraft:spruce_stairs', 'south'))
        bp.set(x, 3, 2, stairs('minecraft:spruce_stairs', 'north'))
        for z in (-1, 0, 1):
            bp.set(x, 3, z, 'minecraft:spruce_slab[type=bottom,waterlogged=false]')
    bp.set(0, 1, 0, 'minecraft:flowering_azalea')
    bp.set(0, 2, 0, 'minecraft:lantern[hanging=true,waterlogged=false]')
    bp.set(0, 0, 3, 'minecraft:oak_sign[rotation=0,waterlogged=false]')
    bp.mark('sign', 0, 0, 3)
    bp.mark('heal', 0, 1, 0)
    bp.mark('stand', 0, 0, 4)
    bp.meta_rect = 4
    return bp


def outskirts_dock():
    """はじまりの島の船着き場: 板の桟橋・小さな屋根・吊り鐘・渡し舟の飾り・札。桟橋の先 (印 ferry) に立つと鐘の都へ渡る (ContinentOutskirts)"""
    bp = BP('outskirts_dock')
    for x in range(-5, 6):
        for z in range(-4, 5):
            bp.set(x, -1, z, 'minecraft:coarse_dirt' if (x * z) % 3 else 'minecraft:gravel')
    # 桟橋 (東へ)
    for x in range(-1, 6):
        for z in (-1, 0, 1):
            bp.set(x, -1, z, 'minecraft:spruce_planks')
    for x in (0, 3, 5):
        for z in (-2, 2):
            bp.fill(x, 0, z, x, 1, z, 'minecraft:spruce_fence')
    # 渡し舟 (桟橋の北の脇に、板と階段の舟の形)
    for x in range(1, 6):
        bp.set(x, -1, -3, 'minecraft:spruce_slab[type=top,waterlogged=false]')
    bp.set(0, -1, -3, stairs('minecraft:spruce_stairs', 'east'))
    bp.set(6, -1, -3, stairs('minecraft:spruce_stairs', 'west'))
    # 小さな屋根と吊り鐘
    for x, z in ((-4, -3), (-4, 3), (-1, -3), (-1, 3)):
        bp.fill(x, 0, z, x, 2, z, 'minecraft:spruce_log[axis=y]')
    for x in range(-5, 1):
        for z in range(-4, 5):
            bp.set(x, 3, z, 'minecraft:spruce_slab[type=bottom,waterlogged=false]')
    bp.set(-2, 2, 0, 'minecraft:bell[attachment=ceiling,facing=east,powered=false]')
    bp.set(-3, 0, -2, 'minecraft:barrel[facing=up,open=false]')
    bp.set(-3, 0, 2, 'minecraft:barrel[facing=up,open=false]')
    bp.set(5, 2, 2, 'minecraft:lantern[hanging=false,waterlogged=false]')
    bp.set(5, 2, -2, 'minecraft:lantern[hanging=false,waterlogged=false]')
    bp.set(1, 0, 2, 'minecraft:oak_sign[rotation=4,waterlogged=false]')
    bp.mark('sign', 1, 0, 2)
    bp.mark('ferry', 4, 0, 0)
    bp.mark('stand', -2, 0, 0)
    bp.meta_rect = 6
    return bp


def main():
    os.makedirs(OUT, exist_ok=True)
    for region in PALETTES:
        n = town(region).save()
        print(f'town_{region}: {n} blocks')
    for bp in (outskirts_fire(), outskirts_shrine(), outskirts_dock()):
        print(f'{bp.name}: {bp.save()} blocks')


if __name__ == '__main__':
    main()
