#!/usr/bin/env python3
"""天と淵の大陸の、土地ごとの小さな町 (docs/design/40 の 3 章「作る順」の 4) と、外れの一本道の休み場・祠 (4 章) の部品を書き出す。

  python tools/gen/world/gen_region_towns.py
  -> src/main/resources/data/skyabyss/opening/town_{whale,tide,storm,ember}.json・outskirts_fire.json・outskirts_shrine.json

形式は tools/gen/ch1/gen_ch1_blueprints.py と同じ (palette / blocks [x,y,z,palette 番号] / marks / meta)。
座標は町の広場の中心が (0, 0)、y=0 が地面の一つ上 (地面の上面は y=-1)。北 (-z) 向きで書き、置く向きは SiteJob が回す。
町の並びと建物は town_arch.py (81 × 81。十字の大通りと環状の道・広場の記念碑と市・木組みの二〜三階の家・土地ごとの壁と門楼と名物)。
印: rest (休み場。広場の井戸の脇)、board (依頼の板)、sign (町の名前と目安のレベルの札)、inn / shop (戸の前)、npc_innkeeper / npc_tool_merchant (宿の主人と道具屋の立つ所。facility/Attendants の役の印)、inn_bed (宿で休むと目覚める寝台)
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import town_arch  # noqa: E402

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
        bp = BP('town_' + region)
        bp.meta_rect = town_arch.R
        n = town_arch.build_town(bp, region).save()
        print(f'town_{region}: {n} blocks')
    for bp in (outskirts_fire(), outskirts_shrine(), outskirts_dock()):
        print(f'{bp.name}: {bp.save()} blocks')


if __name__ == '__main__':
    main()
