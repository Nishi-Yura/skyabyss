#!/usr/bin/env python3
"""序章・一章 (P4a) の小要素の部品 (data/skyabyss/opening/ch1_*.json) を書き出す。

  python tools/gen_ch1_blueprints.py

形式は tools/rough/opening_sites.py と同じ (palette / blocks [x,y,z,palette 番号] / marks / meta)。
座標は部品の中心が (0, 0)、y=0 が最初の段 (地面の上面は y=-1)。向きは SiteJob が回す・反転するので、向きのある物は北向きで書く。
小要素は「人が去った跡」。説明の看板は置かない (17 の 3.6)。積み石 (cairn) は、序章の「積み石の見回り」の目印にもなる。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss', 'opening')


class BP:
    def __init__(self, name, rect, ring=2, level=True):
        self.name = name
        self.rect = rect
        self.ring = ring
        self.level = level
        self.cells = {}
        self.marks = {}
        self.palette = []

    def idx(self, state):
        if state not in self.palette:
            self.palette.append(state)
        return self.palette.index(state)

    def set(self, x, y, z, state):
        self.cells[(x, y, z)] = self.idx(state)

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, state)

    def mark(self, name, x, y, z):
        self.marks[name] = [x, y, z]

    def cairn(self, x, z, h, mark='cairn'):
        # 低い積み石: 下から丸石と苔むした丸石を交互に h 段、てっぺんに石のハーフ
        for i in range(h):
            self.set(x, i, z, 'minecraft:cobblestone' if i % 2 == 0 else 'minecraft:mossy_cobblestone')
        self.set(x, h, z, 'minecraft:cobblestone_slab[type=bottom]')
        if mark:
            self.mark(mark, x, h, z)

    def save(self):
        blocks = [[x, y, z, i] for (x, y, z), i in sorted(self.cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2]))]
        data = {
            'site': self.name,
            'note': 'P4a (docs/design/17 の 1.5)。tools/gen_ch1_blueprints.py が書く',
            'palette': self.palette,
            'blocks': blocks,
            'marks': self.marks,
            'meta': {'rect': self.rect, 'level': self.level, 'ring': self.ring},
        }
        os.makedirs(OUT, exist_ok=True)
        with open(os.path.join(OUT, self.name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(data, f, ensure_ascii=False, separators=(',', ':'))
            f.write('\n')
        return len(blocks)


CHEST = 'minecraft:chest[facing=south,type=single,waterlogged=false]'
BARREL = 'minecraft:barrel[facing=up,open=false]'
BARREL_SIDE = 'minecraft:barrel[facing=east,open=false]'
COBBLE = 'minecraft:cobblestone'
MOSSY = 'minecraft:mossy_cobblestone'
CAMPFIRE_OUT = 'minecraft:campfire[lit=false,facing=north,signal_fire=false,waterlogged=false]'
LOG_Z = 'minecraft:oak_log[axis=z]'
LOG_X = 'minecraft:oak_log[axis=x]'
CARPET_W = 'minecraft:white_carpet'
CARPET_B = 'minecraft:brown_carpet'
PLANKS = 'minecraft:spruce_planks'


def stairs(facing, half='bottom'):
    return 'minecraft:spruce_stairs[facing=%s,half=%s,shape=straight]' % (facing, half)


def wheel(x, z, bp):
    # 寝かせた車輪 (トラップドア 1 枚。中が抜けている)
    bp.set(x, 0, z, 'minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]')


def cart(bp, ox, oz, chest_state=BARREL_SIDE, broken=True):
    """横倒しの荷車。板の壁・欠けた床・投げ出された樽・外れた車輪。"""
    for dx in (-1, 0, 1):
        bp.set(ox + dx, 0, oz, PLANKS)
        bp.set(ox + dx, 1, oz, PLANKS if dx != 0 or not broken else 'minecraft:spruce_slab[type=top]')
    bp.set(ox - 1, 0, oz + 1, stairs('south'))
    bp.set(ox + 1, 0, oz + 1, stairs('south'))
    bp.set(ox, 0, oz + 1, chest_state)
    bp.mark('chest', ox, 0, oz + 1)
    wheel(ox + 3, oz + 1, bp)
    bp.set(ox + 3, 0, oz - 1, 'minecraft:spruce_fence[east=false,north=false,south=false,west=false,waterlogged=false]')
    bp.set(ox - 3, 0, oz, 'minecraft:hay_block[axis=y]')
    bp.set(ox - 3, 0, oz + 1, 'minecraft:hay_block[axis=x]')


# ---------------------------------------------------------------- 序章 (日 1) の旅の跡 3 つ
def trace_camp():
    bp = BP('ch1_trace_camp', [-4, -3, 4, 3])
    for x in range(-2, 3):
        for z in range(-2, 3):
            if x * x + z * z <= 5:
                bp.set(x, -1, z, 'minecraft:coarse_dirt')
    bp.set(0, 0, 0, CAMPFIRE_OUT)
    for i, (x, z) in enumerate([(-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)]):
        bp.set(x, 0, z, COBBLE if i % 2 == 0 else MOSSY)
    bp.set(-3, 0, 0, LOG_Z)
    bp.set(3, 0, 1, LOG_Z)
    bp.set(-2, 0, 2, CARPET_W)
    bp.set(-3, 0, 2, CARPET_W)
    bp.set(2, 0, -2, CHEST)
    bp.mark('chest', 2, 0, -2)
    bp.cairn(3, -2, 3)
    bp.mark('stand', 0, 0, 3)
    return bp


def trace_cart():
    bp = BP('ch1_trace_cart', [-5, -3, 5, 4])
    cart(bp, 0, 0)
    bp.cairn(-4, 3, 3)
    bp.mark('stand', 0, 0, 3)
    return bp


def trace_altar():
    bp = BP('ch1_trace_altar', [-4, -3, 4, 3])
    bp.fill(-1, -1, -1, 1, -1, 1, 'minecraft:stone_bricks')
    bp.set(0, 0, 0, COBBLE)
    bp.set(0, 1, 0, MOSSY)
    bp.set(0, 2, 0, 'minecraft:smooth_stone')
    bp.set(0, 3, 0, 'minecraft:candle[candles=1,lit=false,waterlogged=false]')
    for x in (-2, 2):
        for z in (-2, 2):
            bp.set(x, 0, z, 'minecraft:cobblestone_wall[up=true,east=none,north=none,south=none,west=none,waterlogged=false]')
    bp.set(0, 0, -2, CHEST)
    bp.mark('chest', 0, 0, -2)
    bp.cairn(3, 2, 2)
    bp.mark('stand', 0, 0, 3)
    return bp


# ---------------------------------------------------------------- 一章 (地上) の小要素
def trace_well():
    bp = BP('ch1_trace_well', [-4, -4, 4, 3])
    for x in (-1, 0, 1):
        for z in (-1, 0, 1):
            if (x, z) != (0, 0):
                bp.set(x, 0, z, MOSSY if (x + z) % 2 else COBBLE)
    bp.set(0, 0, 0, 'minecraft:water_cauldron[level=3]')
    for x in (-1, 1):
        for z in (-1, 1):
            bp.set(x, 1, z, 'minecraft:spruce_fence[east=false,north=false,south=false,west=false,waterlogged=false]')
            bp.set(x, 2, z, 'minecraft:spruce_fence[east=false,north=false,south=false,west=false,waterlogged=false]')
    bp.fill(-1, 3, -1, 1, 3, 1, 'minecraft:spruce_slab[type=bottom]')
    bp.set(2, 0, 2, BARREL)
    bp.mark('chest', 2, 0, 2)
    bp.cairn(-3, -2, 3)
    bp.mark('stand', 0, 0, 3)
    return bp


def trace_pack():
    bp = BP('ch1_trace_pack', [-4, -3, 4, 3])
    bp.set(0, 0, 0, 'minecraft:brown_wool')
    bp.set(1, 0, 0, 'minecraft:brown_wool')
    bp.set(0, 1, 0, 'minecraft:brown_carpet')
    bp.set(-1, 0, 0, BARREL_SIDE)
    bp.mark('chest', -1, 0, 0)
    bp.set(0, 0, 1, CARPET_W)
    bp.set(1, 0, 1, CARPET_W)
    bp.set(2, 0, -1, 'minecraft:lantern[hanging=false,waterlogged=false]')
    bp.set(-2, 0, 1, LOG_X)
    bp.cairn(3, 2, 2)
    bp.mark('stand', 0, 0, 3)
    return bp


def lookout():
    bp = BP('ch1_lookout', [-4, -4, 4, 4], ring=3)
    bp.fill(-3, -1, -3, 3, -1, 3, 'minecraft:stone_bricks')
    bp.fill(-3, 0, -3, 3, 0, -3, 'minecraft:stone_brick_wall[up=true,east=none,north=none,south=none,west=none,waterlogged=false]')
    for z in range(-3, 4):
        bp.set(-3, 0, z, 'minecraft:stone_brick_wall[up=true,east=none,north=none,south=none,west=none,waterlogged=false]')
        bp.set(3, 0, z, 'minecraft:stone_brick_wall[up=true,east=none,north=none,south=none,west=none,waterlogged=false]')
    for x in range(-3, 4):
        bp.set(x, 0, -3, 'minecraft:stone_brick_wall[up=true,east=none,north=none,south=none,west=none,waterlogged=false]')
    bp.set(0, 0, 0, 'minecraft:polished_andesite')
    bp.set(0, 1, 0, 'minecraft:lightning_rod[facing=north,powered=false,waterlogged=false]')
    bp.mark('scope', 0, 1, 0)
    bp.set(-2, 0, 1, 'minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight]')
    bp.set(2, 0, 1, 'minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight]')
    bp.mark('stand', 0, 0, 2)
    return bp


def song_stone(n):
    bp = BP('ch1_song_stone_%d' % n, [-3, -3, 3, 3])
    bp.fill(-1, -1, -1, 1, -1, 1, 'minecraft:mossy_stone_bricks')
    bp.set(0, 0, 0, 'skyabyss:song_stone[lit=false]')
    bp.set(0, 1, 0, 'minecraft:mossy_stone_brick_slab[type=bottom]')
    bp.mark('stone', 0, 0, 0)
    bp.cairn(2, 2, n)
    bp.mark('stand', 0, 0, 2)
    return bp


def lost_cart():
    bp = BP('ch1_lost_cart', [-5, -3, 5, 4])
    cart(bp, 0, 0, chest_state=CHEST)
    bp.set(-2, 0, 3, 'minecraft:lantern[hanging=false,waterlogged=false]')
    bp.set(2, 0, 3, CARPET_B)
    bp.mark('stand', 0, 0, 3)
    return bp


def diary_spot():
    bp = BP('ch1_diary_spot', [-4, -3, 4, 3])
    bp.set(0, 0, 0, 'minecraft:stone_stairs[facing=south,half=bottom,shape=straight]')
    bp.set(-1, 0, 0, 'minecraft:stone_slab[type=bottom]')
    bp.set(0, 0, 2, CAMPFIRE_OUT)
    bp.set(-1, 0, 2, COBBLE)
    bp.set(1, 0, 2, MOSSY)
    bp.set(0, 0, 3, MOSSY)
    bp.set(-3, 0, -1, CHEST)
    bp.mark('chest', -3, 0, -1)
    bp.set(2, 0, 0, CARPET_W)
    bp.set(2, 0, 1, CARPET_W)
    bp.mark('stand', 0, 0, 1)
    return bp


def main():
    names = []
    for bp in [trace_camp(), trace_cart(), trace_altar(), trace_well(), trace_pack(), lookout(), lost_cart(), diary_spot()] + [song_stone(n) for n in range(1, 6)]:
        n = bp.save()
        names.append('%s=%d' % (bp.name, n))
    print(', '.join(names))


if __name__ == '__main__':
    main()
