"""土地の町の建物と並び (2026-10-25 夕方「各バイオームの町をもっと豪華な村に。Wynncraft を手本に」)。gen_region_towns.py から呼ぶ。

町は 81 × 81 (広場の中心が (0, 0)、北 = -z)。
  道    : 十字の大通り (幅 5) が町の縁の門まで。広場の外に環状の道 (中心から 20〜22)
  広場  : 八角 (半径 10)。真ん中に土地の記念碑、まわりに市の屋台・言伝ての板・町の札・腰かけ
  区画  : 四つの隅ごとに 7 区画 (内に 3・外に 4) と、角の大きな区画 (土地の名物か畑)
  建物  : 石の土台・木組みの柱と梁・窓・二〜三階・切妻 (棟は長い辺に沿う) か平屋根の胸壁・煙突。中に灯り・梯子・家具
  囲い  : 土地ごとの壁 (木の柵・白い低い壁・石の城壁・黒い石の城壁) と、四つの門の門楼
  灯り  : 大通りに 8 ごと (左右交互)・環状の道の角・広場の八角の角・門楼。ほかには置かない
印 (ContinentTowns が拾う): rest・board・sign・inn・shop・npc_innkeeper・npc_tool_merchant・inn_bed・stand
"""
import math

import numpy as np

AIR = 'minecraft:air'
R = 40          # 町の半分の一辺
RING = (20, 22)  # 環状の道 (中心からの距離)

STYLES = {
    'whale': {  # 北の丘陵: 丸石の土台、唐檜の木組み、急な屋根
        'base': 'minecraft:cobblestone', 'base2': 'minecraft:mossy_cobblestone',
        'wall': 'minecraft:spruce_planks', 'wall2': 'minecraft:stripped_spruce_wood[axis=y]',
        'frame': 'minecraft:stripped_spruce_log', 'beam': 'minecraft:spruce_log',
        'roof': 'minecraft:dark_oak_stairs', 'roofslab': 'minecraft:dark_oak_slab', 'roofblock': 'minecraft:dark_oak_planks',
        'floor': 'minecraft:spruce_planks', 'door': 'minecraft:spruce_door', 'trap': 'minecraft:spruce_trapdoor',
        'fence': 'minecraft:spruce_fence', 'road': 'minecraft:dirt_path', 'road2': 'minecraft:coarse_dirt', 'pave': 'minecraft:cobblestone',
        'pave2': 'minecraft:stone_bricks', 'lamp': 'minecraft:spruce_fence', 'flat': False, 'bed': 'minecraft:red_bed',
    },
    'meadow': {  # 都のまわりの野 (小さな集落だけが使う): 丸石の土台、樫の木組み、黒樫の屋根
        'base': 'minecraft:cobblestone', 'base2': 'minecraft:mossy_cobblestone',
        'wall': 'minecraft:oak_planks', 'wall2': 'minecraft:stripped_oak_wood[axis=y]',
        'frame': 'minecraft:stripped_oak_log', 'beam': 'minecraft:oak_log',
        'roof': 'minecraft:dark_oak_stairs', 'roofslab': 'minecraft:dark_oak_slab', 'roofblock': 'minecraft:dark_oak_planks',
        'floor': 'minecraft:oak_planks', 'door': 'minecraft:oak_door', 'trap': 'minecraft:oak_trapdoor',
        'fence': 'minecraft:oak_fence', 'road': 'minecraft:dirt_path', 'road2': 'minecraft:coarse_dirt', 'pave': 'minecraft:cobblestone',
        'pave2': 'minecraft:stone_bricks', 'lamp': 'minecraft:oak_fence', 'flat': False, 'bed': 'minecraft:green_bed',
    },
    'tide': {  # 東の浜の港町: 白い壁、青い屋根、砂岩の土台
        'base': 'minecraft:cut_sandstone', 'base2': 'minecraft:smooth_sandstone',
        'wall': 'minecraft:calcite', 'wall2': 'minecraft:white_terracotta',
        'frame': 'minecraft:stripped_birch_log', 'beam': 'minecraft:birch_log',
        'roof': 'minecraft:dark_prismarine_stairs', 'roofslab': 'minecraft:dark_prismarine_slab', 'roofblock': 'minecraft:dark_prismarine',
        'floor': 'minecraft:birch_planks', 'door': 'minecraft:birch_door', 'trap': 'minecraft:birch_trapdoor',
        'fence': 'minecraft:birch_fence', 'road': 'minecraft:smooth_sandstone', 'road2': 'minecraft:sandstone', 'pave': 'minecraft:smooth_sandstone',
        'pave2': 'minecraft:cut_sandstone', 'lamp': 'minecraft:birch_fence', 'flat': False, 'bed': 'minecraft:light_blue_bed',
    },
    'storm': {  # 西の高地の里: 石の家、銅の屋根、黒い木の梁
        'base': 'minecraft:cobbled_deepslate', 'base2': 'minecraft:deepslate_bricks',
        'wall': 'minecraft:stone_bricks', 'wall2': 'minecraft:polished_andesite',
        'frame': 'minecraft:dark_oak_log', 'beam': 'minecraft:dark_oak_log',
        'roof': 'minecraft:oxidized_cut_copper_stairs', 'roofslab': 'minecraft:oxidized_cut_copper_slab', 'roofblock': 'minecraft:oxidized_cut_copper',
        'floor': 'minecraft:dark_oak_planks', 'door': 'minecraft:dark_oak_door', 'trap': 'minecraft:dark_oak_trapdoor',
        'fence': 'minecraft:dark_oak_fence', 'road': 'minecraft:gravel', 'road2': 'minecraft:andesite', 'pave': 'minecraft:polished_andesite',
        'pave2': 'minecraft:andesite', 'lamp': 'minecraft:dark_oak_fence', 'flat': False, 'bed': 'minecraft:yellow_bed',
    },
    'ember': {  # 南の荒野の砦の村: 泥煉瓦の家、黒い石の胸壁、平らな屋根
        'base': 'minecraft:blackstone', 'base2': 'minecraft:polished_blackstone_bricks',
        'wall': 'minecraft:mud_bricks', 'wall2': 'minecraft:packed_mud',
        'frame': 'minecraft:polished_blackstone', 'beam': 'minecraft:crimson_stem',
        'roof': 'minecraft:polished_blackstone_brick_stairs', 'roofslab': 'minecraft:polished_blackstone_brick_slab',
        'roofblock': 'minecraft:polished_blackstone_bricks',
        'floor': 'minecraft:crimson_planks', 'door': 'minecraft:crimson_door', 'trap': 'minecraft:crimson_trapdoor',
        'fence': 'minecraft:crimson_fence', 'road': 'minecraft:coarse_dirt', 'road2': 'minecraft:packed_mud', 'pave': 'minecraft:polished_blackstone_bricks',
        'pave2': 'minecraft:cracked_polished_blackstone_bricks', 'lamp': 'minecraft:polished_blackstone_wall', 'flat': True, 'bed': 'minecraft:orange_bed',
    },
}

FACE = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
OPP = {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}


def st(block, **props):
    if not props:
        return block
    return block + '[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']'


def stairs(block, facing, half='bottom'):
    return st(block, facing=facing, half=half, shape='straight', waterlogged='false')


def slab(block, kind='bottom'):
    return st(block, type=kind, waterlogged='false')


def axis(block, a):
    # 向きを持つのは丸太・茎・柱のような石だけ (磨いた黒い石などは向きなし)
    if any(k in block for k in ('_log', '_stem', '_wood', '_hyphae', 'basalt', 'pillar', 'bone_block', 'hay_block')):
        return st(block, axis=a)
    return block


def pane():
    return 'minecraft:glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]'


def lantern(hanging):
    return f'minecraft:lantern[hanging={"true" if hanging else "false"},waterlogged=false]'


def wall_post(block):
    if block.endswith('_wall'):
        return st(block, east='none', north='none', south='none', up='true', waterlogged='false', west='none')
    return block


# ---------------------------------------------------------------- 建物
def building(bp, s, x0, z0, x1, z1, floors, door, rng, chimney=True, flat=None, kind='house', marks=None):
    """木組みの家。door = (x, z, 向き)。kind = house | inn | shop | hall。marks: 印の名前 → 'door_in' など"""
    flat = s['flat'] if flat is None else flat
    H = 4 * floors
    w, d = x1 - x0, z1 - z0
    # 土台と床 (土台は地面の下まで一段)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            bp.set(x, -1, z, s['base'] if x in (x0, x1) or z in (z0, z1) else s['floor'])
            for y in range(0, H):
                bp.set(x, y, z, AIR)
    # 壁
    posts = set()
    for x in range(x0, x1 + 1, 4):
        posts.add(('x', x))
    posts.add(('x', x1))
    for z in range(z0, z1 + 1, 4):
        posts.add(('z', z))
    posts.add(('z', z1))
    for y in range(0, H):
        storey = y // 4
        level = y % 4
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                post = corner or (z in (z0, z1) and ('x', x) in posts) or (x in (x0, x1) and ('z', z) in posts)
                if level == 3:
                    # 階の境の梁 (横に寝かせた丸太)
                    a = 'x' if z in (z0, z1) and not corner else 'z'
                    bp.set(x, y, z, axis(s['beam'], a) if not corner else axis(s['frame'], 'y'))
                elif post:
                    bp.set(x, y, z, axis(s['frame'], 'y'))
                elif storey == 0:
                    bp.set(x, y, z, s['base'] if level == 0 else s['wall'] if rng.random() > 0.08 else s['base2'])
                else:
                    bp.set(x, y, z, s['wall2'] if (x + z + y) % 7 == 0 else s['wall'])
    # 窓 (柱と柱のあいだのまんなか。各階の 1〜2 段目)
    for k in range(floors):
        for x in range(x0 + 2, x1 - 1, 4):
            for z in (z0, z1):
                for yy in (4 * k + 1, 4 * k + 2):
                    if bp.get(x, yy, z) not in (None, AIR) and 'frame' not in bp.get(x, yy, z):
                        bp.set(x, yy, z, pane())
        for z in range(z0 + 2, z1 - 1, 4):
            for x in (x0, x1):
                for yy in (4 * k + 1, 4 * k + 2):
                    if bp.get(x, yy, z) not in (None, AIR):
                        bp.set(x, yy, z, pane())
    # 中の床 (上の階) と梯子
    lx, lz = x1 - 1, z0 + 1
    for k in range(1, floors):
        y = 4 * k - 1
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                bp.set(x, y, z, s['floor'])
        bp.set(lx, y, lz, AIR)
    if floors > 1:
        for y in range(0, 4 * (floors - 1)):
            bp.set(lx, y, lz, 'minecraft:ladder[facing=south,waterlogged=false]')
    # 戸 (と、戸の上の小さな庇)
    dx, dz, facing = door
    bp.set(dx, 0, dz, st(s['door'], facing=facing, half='lower', hinge='left', open='false', powered='false'))
    bp.set(dx, 1, dz, st(s['door'], facing=facing, half='upper', hinge='left', open='false', powered='false'))
    bp.set(dx, 2, dz, s['wall'] if floors > 0 else AIR)
    fx, fz = FACE[facing]
    bp.set(dx + fx, -1, dz + fz, s['pave'])
    bp.set(dx + fx, 3, dz + fz, slab(s['roofslab'], 'top'))
    # 灯り (各階の天井に一つ) と家具
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for k in range(floors):
        bp.set(cx, 4 * k + 2, cz, lantern(True))
    furnish(bp, s, x0, z0, x1, z1, floors, door, kind, rng)
    # 屋根
    if flat:
        flat_roof(bp, s, x0, z0, x1, z1, H)
        top = H + 1
    else:
        top = gable(bp, s, x0, z0, x1, z1, H)
    if chimney and kind != 'hall':
        hx, hz = (x0 + 1, z1 - 1) if (x0 + z0) % 2 else (x1 - 1, z1 - 1)
        if (hx, hz) != (lx, lz):
            for y in range(0, top + 2):
                bp.set(hx, y, hz, 'minecraft:bricks' if s is not STYLES['ember'] else s['base2'])
            bp.set(hx, 0, hz, 'minecraft:furnace[facing=north,lit=false]' if hz - 1 > z0 else 'minecraft:bricks')
            bp.set(hx, top + 2, hz, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    return top


def gable(bp, s, x0, z0, x1, z1, H):
    """切妻。棟は長い辺に沿い、軒は 1 出す。妻の三角は壁で埋め、棟は板の半ブロック"""
    along_x = (x1 - x0) >= (z1 - z0)
    if along_x:
        a, b = z0 - 1, z1 + 1
        lo, hi = x0 - 1, x1 + 1
    else:
        a, b = x0 - 1, x1 + 1
        lo, hi = z0 - 1, z1 + 1
    layer = 0
    while a < b:
        y = H + layer
        for t in range(lo, hi + 1):
            if along_x:
                bp.set(t, y, a, stairs(s['roof'], 'south'))
                bp.set(t, y, b, stairs(s['roof'], 'north'))
            else:
                bp.set(a, y, t, stairs(s['roof'], 'east'))
                bp.set(b, y, t, stairs(s['roof'], 'west'))
        # 妻の壁と、屋根の内側の空気
        for u in range(a + 1, b):
            for t in range(lo + 1, hi):
                gx, gz = (t, u) if along_x else (u, t)
                end = (gx in (x0, x1)) if along_x else (gz in (z0, z1))
                if end:
                    bp.set(gx, y, gz, s['wall'] if (u - a) % 3 else s['wall2'])
                else:
                    bp.set(gx, y, gz, AIR)
        a += 1
        b -= 1
        layer += 1
    if a == b:
        for t in range(lo, hi + 1):
            if along_x:
                bp.set(t, H + layer, a, slab(s['roofslab']))
            else:
                bp.set(a, H + layer, t, slab(s['roofslab']))
    # 妻の小窓
    if along_x:
        for gx in (x0, x1):
            zc = (z0 + z1) // 2
            if bp.get(gx, H + 1, zc) and 'stairs' not in bp.get(gx, H + 1, zc):
                bp.set(gx, H + 1, zc, pane())
    else:
        for gz in (z0, z1):
            xc = (x0 + x1) // 2
            if bp.get(xc, H + 1, gz) and 'stairs' not in bp.get(xc, H + 1, gz):
                bp.set(xc, H + 1, gz, pane())
    return H + layer


def flat_roof(bp, s, x0, z0, x1, z1, H):
    """平らな屋根と胸壁 (一つおきに欠く)。屋根の上に樽や布の日よけ"""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            bp.set(x, H, z, s['roofblock'] if x in (x0, x1) or z in (z0, z1) else s['floor'])
            if x in (x0, x1) or z in (z0, z1):
                if (x + z) % 2 == 0:
                    bp.set(x, H + 1, z, s['roofblock'])
                else:
                    bp.set(x, H + 1, z, slab(s['roofslab']))
    # 張り出した梁の端 (外へ一つ)
    for x in range(x0 + 2, x1 - 1, 4):
        bp.set(x, H - 1, z0 - 1, axis(s['beam'], 'z'))
        bp.set(x, H - 1, z1 + 1, axis(s['beam'], 'z'))
    bp.set(x0 + 2, H + 1, z0 + 2, 'minecraft:barrel[facing=up,open=false]')


def furnish(bp, s, x0, z0, x1, z1, floors, door, kind, rng):
    dx, dz, facing = door
    near = {(dx + a, dz + b) for a in (-1, 0, 1) for b in (-1, 0, 1)}

    def free(x, z, y=0):
        return x0 < x < x1 and z0 < z < z1 and (x, z) not in near and bp.get(x, y, z) in (None, AIR)
    # 一階: 机 (柵と感圧板)・樽・本棚か作業台
    picks = [(x0 + 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1), (x0 + 2, z0 + 1)]
    items = ['minecraft:barrel[facing=up,open=false]', 'minecraft:bookshelf', 'minecraft:barrel[facing=up,open=false]', 'minecraft:flower_pot']
    if kind == 'shop':
        items = ['minecraft:barrel[facing=up,open=false]'] * 4
    for (x, z), it in zip(picks, items):
        if free(x, z):
            bp.set(x, 0, z, it)
    tx, tz = (x0 + x1) // 2, (z0 + z1) // 2 + 1
    if free(tx, tz) and kind != 'shop':
        bp.set(tx, 0, tz, s['fence'])
        bp.set(tx, 1, tz, 'minecraft:oak_pressure_plate[powered=false]' if 'birch' not in s['fence'] else 'minecraft:birch_pressure_plate[powered=false]')
    # 上の階 (なければ一階) に寝台
    y = 4 * (floors - 1)
    beds = 3 if kind == 'inn' else 1
    placed = 0
    for x in range(x0 + 1, x1 - 1, 2):
        if placed >= beds:
            break
        if bp.get(x, y, z1 - 1) in (None, AIR) and bp.get(x, y, z1 - 2) in (None, AIR) and (floors > 1 or (x, z1 - 1) not in near):
            bp.set(x, y, z1 - 2, st(s['bed'], facing='north', occupied='false', part='head'))
            bp.set(x, y, z1 - 1, st(s['bed'], facing='north', occupied='false', part='foot'))
            placed += 1


# ---------------------------------------------------------------- 町の並び
# 一つの隅 (u, v ≥ 0) の区画。door: 'main' = 縦の大通り側 (u の小さい側)、'cross' = 横の大通り側 (v の小さい側)、'ring' = 環状の道の側
LOTS = [
    ('in_a', (4, 12, 11, 18), 'main'),
    ('in_b', (12, 4, 18, 10), 'cross'),
    ('in_c', (13, 13, 18, 18), 'ring'),
    ('out_a', (4, 24, 11, 32), 'main'),
    ('out_b', (14, 24, 21, 30), 'ring'),
    ('out_c', (24, 4, 32, 11), 'cross'),
    ('out_d', (24, 14, 30, 21), 'ring'),
]
CORNER = (25, 25, 35, 35)
QUADS = {'nw': (-1, -1), 'ne': (1, -1), 'sw': (-1, 1), 'se': (1, 1)}


def to_xz(q, u0, v0, u1, v1):
    sx, sz = QUADS[q]
    xs = sorted((sx * u0, sx * u1))
    zs = sorted((sz * v0, sz * v1))
    return xs[0], zs[0], xs[1], zs[1]


def door_of(q, rect, side):
    sx, sz = QUADS[q]
    x0, z0, x1, z1 = rect
    if side == 'main':
        x = x0 if sx > 0 else x1
        return x, (z0 + z1) // 2, 'west' if sx > 0 else 'east'
    if side == 'cross':
        z = z0 if sz > 0 else z1
        return (x0 + x1) // 2, z, 'north' if sz > 0 else 'south'
    # ring: 環状の道は中心から 20〜22。区画が環の内なら外側の壁、外なら内側の壁
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    if max(abs(x0), abs(x1)) <= RING[0] and max(abs(z0), abs(z1)) <= RING[0]:
        # 内の区画: 環に近いほうの辺
        if abs(cz) >= abs(cx):
            z = z1 if sz > 0 else z0
            return (x0 + x1) // 2, z, 'south' if sz > 0 else 'north'
        x = x1 if sx > 0 else x0
        return x, (z0 + z1) // 2, 'east' if sx > 0 else 'west'
    if min(abs(z0), abs(z1)) > RING[1]:
        z = z0 if sz > 0 else z1
        return (x0 + x1) // 2, z, 'north' if sz > 0 else 'south'
    x = x0 if sx > 0 else x1
    return x, (z0 + z1) // 2, 'west' if sx > 0 else 'east'


def roads(bp, s, rng):
    for i in range(-R, R + 1):
        for w in range(-2, 3):
            edge = abs(w) == 2
            for (x, z) in ((w, i), (i, w)):
                bp.set(x, -1, z, s['road2'] if edge and rng.random() < 0.5 else s['road'])
    for i in range(-RING[1], RING[1] + 1):
        for r in range(RING[0], RING[1] + 1):
            for (x, z) in ((i, r), (i, -r), (r, i), (-r, i)):
                bp.set(x, -1, z, s['road2'] if r != 21 and rng.random() < 0.4 else s['road'])


def plaza(bp, s, region, rng):
    for x in range(-10, 11):
        for z in range(-10, 11):
            if abs(x) + abs(z) <= 14:
                ring = max(abs(x), abs(z))
                bp.set(x, -1, z, s['pave2'] if ring % 3 == 0 or (x * 5 + z * 3) % 7 == 0 else s['pave'])
    monument(bp, s, region)
    # 八角の角の灯り
    for x, z in ((-7, -7), (7, -7), (-7, 7), (7, 7)):
        lamp(bp, s, x, z)
    # 市の屋台 (東西の縁の南寄り)。色の布・品の樽・干し草・籠
    cloth = {'whale': ('minecraft:red_wool', 'minecraft:white_wool'), 'tide': ('minecraft:cyan_wool', 'minecraft:white_wool'),
             'storm': ('minecraft:yellow_wool', 'minecraft:gray_wool'), 'ember': ('minecraft:orange_wool', 'minecraft:black_wool')}[region]
    for (sx0, sz0) in ((-9, 3), (6, 3), (-9, -6), (6, -6)):
        for x in (sx0, sx0 + 2):
            for z in (sz0, sz0 + 2):
                bp.fill(x, 0, z, x, 1, z, s['fence'])
        for x in range(sx0, sx0 + 3):
            for z in range(sz0, sz0 + 3):
                bp.set(x, 2, z, cloth[(x + z) % 2])
        bp.set(sx0 + 1, 0, sz0 + 1, 'minecraft:barrel[facing=up,open=false]')
        bp.set(sx0 + 1, 0, sz0 + 2, 'minecraft:composter[level=7]' if region != 'ember' else 'minecraft:smoker[facing=south,lit=false]')
        bp.set(sx0, 0, sz0 + 1, 'minecraft:hay_block[axis=y]' if region in ('whale', 'storm') else 'minecraft:barrel[facing=up,open=false]')
    # 言伝ての板と町の札 (北の縁、大通りの左右)
    bp.set(4, 0, -9, s['fence'])
    bp.set(6, 0, -9, s['fence'])
    bp.set(5, 0, -9, 'skyabyss:message_board[facing=south]')
    bp.mark('board', 5, 0, -8)
    bp.set(-5, 0, -9, 'minecraft:oak_sign[rotation=0,waterlogged=false]')
    bp.mark('sign', -5, 0, -9)
    # 腰かけ (南の縁)
    for x in (-5, -4, 4, 5):
        bp.set(x, 0, 9, stairs(s['roof'], 'south'))
    bp.mark('rest', 4, 0, 6)
    bp.mark('stand', 0, 0, 6)


def monument(bp, s, region):
    if region == 'whale':
        # 鯨の骨の泉: 水の鉢の上に肋骨四本が弧を描く
        basin(bp, s, 'minecraft:water[level=0]')
        # 肋骨: 鉢の縁から立ち上がり、真ん中の上で合わさる弧 (四分の一の円)
        for a in range(4):
            ang = a * math.pi / 2 + math.pi / 4
            for t in range(0, 13):
                th = t / 12 * math.pi / 2
                rr = 3.6 * math.cos(th)
                y = 1 + int(round(7 * math.sin(th)))
                x = int(round(math.cos(ang) * rr))
                z = int(round(math.sin(ang) * rr))
                bp.set(x, y, z, 'minecraft:bone_block[axis=y]')
        bp.set(0, 9, 0, 'minecraft:bone_block[axis=y]')
        bp.set(0, 7, 0, lantern(True))
    elif region == 'tide':
        # 噴水: 二段の鉢と真ん中の柱から水
        basin(bp, s, 'minecraft:water[level=0]')
        bp.fill(0, 0, 0, 0, 3, 0, 'minecraft:prismarine_bricks')
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            bp.set(dx, 3, dz, slab('minecraft:prismarine_brick_slab'))
        bp.set(0, 4, 0, 'minecraft:sea_lantern')
        bp.set(0, 5, 0, slab('minecraft:prismarine_brick_slab'))
    elif region == 'storm':
        # 風の柱: 高い石の柱に避雷の棒と、四方の旗
        basin(bp, s, 'minecraft:water[level=0]')
        bp.fill(0, 0, 0, 0, 9, 0, 'minecraft:chiseled_stone_bricks')
        bp.set(0, 10, 0, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]')
        for dx, dz, c in ((1, 0, 'yellow'), (-1, 0, 'white'), (0, 1, 'gray'), (0, -1, 'light_blue')):
            for t in range(3):
                bp.set(dx * (1 + t), 8 - t // 2, dz * (1 + t), f'minecraft:{c}_wool')
    else:
        # 炉の碑: 黒い石の台に溶岩の鉢と四つの篝火
        for x in range(-3, 4):
            for z in range(-3, 4):
                if abs(x) + abs(z) <= 4:
                    bp.set(x, 0, z, s['base2'])
        for x in range(-1, 2):
            for z in range(-1, 2):
                bp.set(x, 1, z, s['base2'])
        bp.set(0, 1, 0, 'minecraft:magma_block')
        bp.set(0, 2, 0, 'minecraft:campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]')
        for x, z in ((-3, 0), (3, 0), (0, -3), (0, 3)):
            bp.fill(x, 1, z, x, 2, z, s['base2'])
            bp.set(x, 3, z, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')


def basin(bp, s, liquid):
    for x in range(-4, 5):
        for z in range(-4, 5):
            d = math.hypot(x, z)
            if d <= 4.2:
                bp.set(x, -1, z, s['base2'])
                if d > 3.2:
                    bp.set(x, 0, z, s['base2'])
                else:
                    bp.set(x, 0, z, liquid)


def lamp(bp, s, x, z):
    """街灯 (どの町も同じ形: 柱 3 と、上に吊った灯り)"""
    post = wall_post(s['lamp'])
    bp.fill(x, 0, z, x, 2, z, post)
    bp.set(x, 3, z, lantern(False))
    bp.set(x, 4, z, slab(s['roofslab']))


def street_lamps(bp, s):
    # 大通りに 8 ごと (左右交互)、環状の道の四隅
    k = 0
    for i in range(14, R - 4, 8):
        side = 3 if k % 2 == 0 else -3
        for (x, z) in ((side, i), (-side, -i), (i, -side), (-i, side)):
            lamp(bp, s, x, z)
        k += 1
    for x, z in ((23, 23), (-23, 23), (23, -23), (-23, -23)):
        lamp(bp, s, x, z)


# ---------------------------------------------------------------- 囲いと門
def walls(bp, s, region, rng):
    E = R - 1
    for i in range(-E, E + 1):
        for (x, z) in ((i, -E), (i, E), (-E, i), (E, i)):
            if abs(i) <= 4:
                continue
            if region == 'whale':
                bp.fill(x, 0, z, x, 3, z, 'minecraft:spruce_log[axis=y]')
                if (i % 2) == 0:
                    bp.set(x, 4, z, 'minecraft:spruce_fence')
            elif region == 'tide':
                bp.fill(x, 0, z, x, 1, z, s['base'])
                bp.set(x, 2, z, slab(s['roofslab']) if i % 3 else s['base'])
            else:
                bp.fill(x, 0, z, x, 3, z, s['base'] if rng.random() > 0.15 else s['base2'])
                bp.set(x, 4, z, s['base2'] if i % 2 == 0 else AIR)
    # 角の塔
    for x, z in ((E, E), (-E, E), (E, -E), (-E, -E)):
        tower(bp, s, region, x, z)
    # 門楼 (四つの道)
    for d in (E, -E):
        gatehouse(bp, s, region, 0, d, 'x')
        gatehouse(bp, s, region, d, 0, 'z')


def tower(bp, s, region, cx, cz):
    h = 6 if region != 'tide' else 4
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            edge = max(abs(x - cx), abs(z - cz)) == 2
            for y in range(0, h):
                bp.set(x, y, z, (s['base2'] if region != 'whale' else 'minecraft:stripped_spruce_log[axis=y]') if edge else AIR)
            bp.set(x, h, z, s['floor'] if region == 'whale' else s['base2'])
            if edge:
                bp.set(x, h + 1, z, (s['fence'] if region == 'whale' else s['base2']) if (x + z) % 2 == 0 else AIR)
    if region == 'whale':
        for k in range(3):
            for x in range(cx - 3 + k, cx + 4 - k):
                for z in range(cz - 3 + k, cz + 4 - k):
                    if max(abs(x - cx), abs(z - cz)) == 3 - k:
                        bp.set(x, h + 3 + k, z, slab(s['roofslab']))
        for x, z in ((cx - 2, cz - 2), (cx + 2, cz - 2), (cx - 2, cz + 2), (cx + 2, cz + 2)):
            bp.fill(x, h + 1, z, x, h + 2, z, 'minecraft:spruce_log[axis=y]')


def gatehouse(bp, s, region, cx, cz, along):
    """道をまたぐ門楼: 左右の柱 (3 × 3) と上の渡り (高さ 5)、旗、吊り灯り"""
    def at(u, v):
        return (cx + u, cz + v) if along == 'x' else (cx + v, cz + u)
    pillar = s['base2'] if region != 'whale' else 'minecraft:spruce_log[axis=y]'
    top = 6 if region != 'tide' else 5
    for u in (-4, -3, 3, 4):
        for v in (-1, 0, 1):
            x, z = at(u, v)
            bp.fill(x, 0, z, x, top - 1, z, pillar)
    for u in range(-4, 5):
        for v in (-1, 0, 1):
            x, z = at(u, v)
            bp.set(x, top, z, s['roofblock'])
            if v != 0 and abs(u) <= 2:
                bp.set(x, top - 1, z, stairs(s['roof'], 'north' if (along == 'x') == (v < 0) else 'south', 'top') if along == 'x' else
                       stairs(s['roof'], 'west' if v < 0 else 'east', 'top'))
        x, z = at(u, 0)
        bp.set(x, top + 1, z, s['roofblock'] if u % 2 == 0 else slab(s['roofslab']))
    for u in (-2, 2):
        x, z = at(u, 0)
        bp.set(x, top - 1, z, lantern(True))
    color = {'whale': 'red', 'tide': 'light_blue', 'storm': 'yellow', 'ember': 'orange'}[region]
    for u in (-3, 3):
        for v in (-2, 2):
            x, z = at(u, v)
            bp.set(x, top - 2, z, f'minecraft:{color}_wool')
            bp.set(x, top - 3, z, f'minecraft:{color}_wool')


# ---------------------------------------------------------------- 土地の名物 (角の区画)
def landmark(bp, s, region, q, rng):
    x0, z0, x1, z1 = to_xz(q, *CORNER)
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    kind = {
        'whale': {'ne': 'lookout', 'nw': 'farm', 'sw': 'garden', 'se': 'farm'},
        'tide': {'se': 'lighthouse', 'ne': 'nets', 'nw': 'garden', 'sw': 'farm'},
        'storm': {'nw': 'windmill', 'se': 'windmill', 'ne': 'farm', 'sw': 'garden'},
        'ember': {'ne': 'forge', 'sw': 'forge_yard', 'nw': 'garden', 'se': 'farm'},
    }[region][q]
    if kind == 'farm':
        farm(bp, s, x0 + 1, z0 + 1, x1 - 1, z1 - 1, rng)
    elif kind == 'garden':
        garden(bp, s, region, x0, z0, x1, z1, rng)
    elif kind == 'lookout':
        lookout(bp, s, cx, cz)
        # 鯨追い (イサナ) の立つ所と、その焚き火 (塔の戸の前。00 の 278 = A: 一章の入口は鯨見の町)
        bp.set(cx + 3, 0, cz + 5, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
        for dx, dz in ((4, 5), (3, 6)):
            bp.set(cx + dx, 0, cz + dz, 'minecraft:spruce_log[axis=x]')
        bp.mark('npc_chaser', cx + 1, 0, cz + 5)
    elif kind == 'lighthouse':
        lighthouse(bp, s, cx, cz)
    elif kind == 'nets':
        for zz in (z0 + 2, z0 + 5, z0 + 8):
            for x in (x0 + 1, x1 - 1):
                bp.fill(x, 0, zz, x, 2, zz, s['fence'])
            for x in range(x0 + 1, x1):
                bp.set(x, 3, zz, slab('minecraft:birch_slab'))
                if x0 + 1 < x < x1 - 1:
                    bp.set(x, 2, zz, 'minecraft:chain[axis=y,waterlogged=false]')
        bp.set(cx, 0, z1 - 1, 'minecraft:barrel[facing=up,open=false]')
        bp.set(cx + 1, 0, z1 - 1, 'minecraft:barrel[facing=up,open=false]')
    elif kind == 'windmill':
        windmill(bp, s, cx, cz)
    elif kind == 'forge':
        forge(bp, s, x0 + 1, z0 + 1, x1 - 1, z1 - 1)
    elif kind == 'forge_yard':
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                bp.set(x, -1, z, s['road2'] if (x + z) % 3 else s['road'])
        for k in range(6):
            x, z = int(rng.integers(x0 + 1, x1)), int(rng.integers(z0 + 1, z1))
            bp.set(x, 0, z, rng.choice(['minecraft:anvil[facing=north]', 'minecraft:barrel[facing=up,open=false]', 'minecraft:blackstone', 'minecraft:magma_block']))


def farm(bp, s, x0, z0, x1, z1, rng):
    crops = ['minecraft:wheat[age=7]', 'minecraft:carrots[age=7]', 'minecraft:potatoes[age=7]', 'minecraft:beetroots[age=3]']
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                bp.set(x, 0, z, s['fence'])
            elif (z - z0) % 4 == 2:
                bp.set(x, -1, z, 'minecraft:water[level=0]')
            else:
                bp.set(x, -1, z, 'minecraft:farmland[moisture=7]')
                bp.set(x, 0, z, crops[((z - z0) // 4) % len(crops)])
    bp.set((x0 + x1) // 2, 0, z0, st(s['fence'] + '_gate', facing='north', in_wall='false', open='false', powered='false'))
    bp.set(x1 - 1, 0, z1 - 1, 'minecraft:hay_block[axis=y]')
    bp.set(x1 - 1, 1, z1 - 1, 'minecraft:hay_block[axis=y]')


TREES = {
    'meadow': ('minecraft:oak_log[axis=y]', 'minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]'),
    'whale': ('minecraft:spruce_log[axis=y]', 'minecraft:spruce_leaves[distance=1,persistent=true,waterlogged=false]'),
    'tide': ('minecraft:birch_log[axis=y]', 'minecraft:birch_leaves[distance=1,persistent=true,waterlogged=false]'),
    'storm': ('minecraft:spruce_log[axis=y]', 'minecraft:spruce_leaves[distance=1,persistent=true,waterlogged=false]'),
    'ember': ('minecraft:dark_oak_log[axis=y]', 'minecraft:azalea_leaves[distance=1,persistent=true,waterlogged=false]'),
}


def tree(bp, region, x, z, h=6):
    trunk, leaves = TREES[region]
    bp.fill(x, 0, z, x, h - 1, z, trunk)
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in range(h - 3, h + 2):
                if dx * dx + dz * dz + (dy - h + 1) ** 2 <= 5 and not (dx == 0 and dz == 0 and dy < h):
                    bp.set(x + dx, dy, z + dz, leaves)


def garden(bp, s, region, x0, z0, x1, z1, rng):
    flowers = ['minecraft:poppy', 'minecraft:dandelion', 'minecraft:oxeye_daisy', 'minecraft:cornflower', 'minecraft:allium', 'minecraft:azure_bluet']
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            bp.set(x, -1, z, 'minecraft:grass_block[snowy=false]')
            if rng.random() < 0.25:
                bp.set(x, 0, z, rng.choice(flowers) if region != 'ember' else 'minecraft:dead_bush')
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for x in range(x0 + 1, x1):
        bp.set(x, -1, cz, s['pave'])
        bp.set(x, 0, cz, AIR)
    tree(bp, region, cx - 3, cz - 3)
    tree(bp, region, cx + 3, cz + 3)
    for x in (cx - 1, cx + 1):
        bp.set(x, 0, cz - 1, stairs(s['roof'], 'south'))
    lamp(bp, s, cx, cz + 2)


def lookout(bp, s, cx, cz):
    """鯨見の見張りの塔 (高さ 18。上に鐘と寄棟の小屋根)"""
    for y in range(0, 18):
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                if max(abs(x - cx), abs(z - cz)) == 2:
                    corner = abs(x - cx) == 2 and abs(z - cz) == 2
                    bp.set(x, y, z, 'minecraft:spruce_log[axis=y]' if corner else (s['base'] if y < 6 else s['wall']))
                else:
                    bp.set(x, y, z, AIR)
    for y in range(0, 18):
        bp.set(cx, y, cz - 1, 'minecraft:ladder[facing=south,waterlogged=false]')
    bp.set(cx, 0, cz + 2, st(s['door'], facing='south', half='lower', hinge='left', open='false', powered='false'))
    bp.set(cx, 1, cz + 2, st(s['door'], facing='south', half='upper', hinge='left', open='false', powered='false'))
    for y in (6, 10, 14):
        for x, z in ((cx + 2, cz), (cx - 2, cz)):
            bp.set(x, y, z, pane())
    for x in range(cx - 3, cx + 4):
        for z in range(cz - 3, cz + 4):
            bp.set(x, 18, z, s['floor'])
            if max(abs(x - cx), abs(z - cz)) == 3:
                bp.set(x, 19, z, s['fence'])
    bp.set(cx, 18, cz - 1, st(s['trap'], facing='south', half='top', open='false', powered='false', waterlogged='false'))
    for x, z in ((cx - 3, cz - 3), (cx + 3, cz - 3), (cx - 3, cz + 3), (cx + 3, cz + 3)):
        bp.fill(x, 19, z, x, 21, z, 'minecraft:spruce_log[axis=y]')
    for k in range(4):
        for x in range(cx - 4 + k, cx + 5 - k):
            for z in range(cz - 4 + k, cz + 5 - k):
                if max(abs(x - cx), abs(z - cz)) == 4 - k:
                    bp.set(x, 22 + k, z, slab(s['roofslab']))
    bp.fill(cx - 1, 22, cz - 1, cx + 1, 22, cz + 1, s['roofblock'])
    bp.set(cx, 21, cz, 'minecraft:bell[attachment=ceiling,facing=north,powered=false]')


def lighthouse(bp, s, cx, cz):
    for y in range(0, 20):
        for x in range(cx - 3, cx + 4):
            for z in range(cz - 3, cz + 4):
                d = (x - cx) ** 2 + (z - cz) ** 2
                if d <= 10:
                    if d > 5:
                        bp.set(x, y, z, 'minecraft:white_terracotta' if (y // 3) % 2 == 0 else 'minecraft:light_blue_terracotta')
                    else:
                        bp.set(x, y, z, AIR)
    for y in range(0, 20):
        bp.set(cx, y, cz - 2, 'minecraft:ladder[facing=south,waterlogged=false]')
    bp.set(cx, 0, cz + 3, st(s['door'], facing='south', half='lower', hinge='left', open='false', powered='false'))
    bp.set(cx, 1, cz + 3, st(s['door'], facing='south', half='upper', hinge='left', open='false', powered='false'))
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            d = (x - cx) ** 2 + (z - cz) ** 2
            if d <= 17:
                bp.set(x, 20, z, s['base'])
                if d >= 13:
                    bp.set(x, 21, z, s['fence'])
    bp.set(cx, 20, cz - 2, st(s['trap'], facing='south', half='top', open='false', powered='false', waterlogged='false'))
    bp.set(cx, 21, cz, 'minecraft:sea_lantern')
    for x, z in ((cx - 1, cz - 1), (cx + 1, cz - 1), (cx - 1, cz + 1), (cx + 1, cz + 1)):
        bp.fill(x, 21, z, x, 22, z, 'minecraft:glass')
    bp.set(cx, 22, cz, 'minecraft:sea_lantern')
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            if abs(x - cx) + abs(z - cz) <= 3:
                bp.set(x, 23, z, slab(s['roofslab']))
    bp.set(cx, 24, cz, s['roofblock'])


def windmill(bp, s, cx, cz):
    for y in range(0, 10):
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                if max(abs(x - cx), abs(z - cz)) == 2:
                    corner = abs(x - cx) == 2 and abs(z - cz) == 2
                    bp.set(x, y, z, axis(s['frame'], 'y') if corner else s['wall'])
                else:
                    bp.set(x, y, z, AIR)
    bp.set(cx, 0, cz + 2, st(s['door'], facing='south', half='lower', hinge='left', open='false', powered='false'))
    bp.set(cx, 1, cz + 2, st(s['door'], facing='south', half='upper', hinge='left', open='false', powered='false'))
    for k in range(3):
        for x in range(cx - 3 + k, cx + 4 - k):
            for z in range(cz - 3 + k, cz + 4 - k):
                if max(abs(x - cx), abs(z - cz)) == 3 - k:
                    bp.set(x, 10 + k, z, slab(s['roofslab']) if k < 2 else s['roofblock'])
    hub = 7
    bp.set(cx, hub, cz + 3, 'minecraft:dark_oak_log[axis=z]')
    for i in range(1, 7):
        for (ux, uy) in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            bp.set(cx + ux * i, hub + uy * i, cz + 4, 'minecraft:dark_oak_planks' if i == 1 else 'minecraft:stripped_dark_oak_log[axis=y]' if ux == 0 else 'minecraft:stripped_dark_oak_log[axis=x]')
            if i > 1:
                # 帆 (羽根を時計回りに見て、後ろの側に布。風車らしく回って見える)
                sx, sy = uy, -ux
                bp.set(cx + ux * i + sx, hub + uy * i + sy, cz + 4, 'minecraft:white_wool')


def forge(bp, s, x0, z0, x1, z1):
    """大きな鍛冶場: 三方が開いた屋根、炉・金床・溶岩の大釜・煙突二本"""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            bp.set(x, -1, z, s['base2'])
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), ((x0 + x1) // 2, z0), ((x0 + x1) // 2, z1)):
        bp.fill(x, 0, z, x, 4, z, s['frame'])
    for x in range(x0, x1 + 1):
        bp.fill(x, 0, z0, x, 4, z0, s['wall'] if x not in (x0, x1, (x0 + x1) // 2) else s['frame'])
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            bp.set(x, 5, z, s['roofblock'] if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1) else slab(s['roofslab']))
    bp.set(x0 + 2, 0, z0 + 1, 'minecraft:blast_furnace[facing=south,lit=true]')
    bp.set(x0 + 3, 0, z0 + 1, 'minecraft:blast_furnace[facing=south,lit=true]')
    bp.set(x0 + 2, 0, z0 + 4, 'minecraft:anvil[facing=east]')
    bp.set(x1 - 2, 0, z0 + 4, 'minecraft:anvil[facing=east]')
    bp.set(x0 + 5, 0, z0 + 1, 'minecraft:lava_cauldron')
    bp.set(x1 - 2, 0, z0 + 1, 'minecraft:grindstone[face=floor,facing=north]')
    bp.set(x1 - 1, 0, z1 - 1, 'minecraft:barrel[facing=up,open=false]')
    for x in (x0 + 2, x1 - 2):
        bp.fill(x, 1, z0, x, 8, z0, s['base2'])
        bp.set(x, 9, z0, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    for x in (x0 + 3, x1 - 3):
        bp.set(x, 4, (z0 + z1) // 2, lantern(True))


# ---------------------------------------------------------------- 町
def build_town(bp, region):
    s = STYLES[region]
    rng = np.random.default_rng({'whale': 11, 'tide': 23, 'storm': 37, 'ember': 41}[region])
    roads(bp, s, rng)
    plaza(bp, s, region, rng)
    street_lamps(bp, s)
    for q in QUADS:
        for name, (u0, v0, u1, v1), side in LOTS:
            rect = to_xz(q, u0, v0, u1, v1)
            door = door_of(q, rect, side)
            x0, z0, x1, z1 = rect
            kind = 'house'
            floors = 2 if rng.random() < 0.6 else 1
            if name.startswith('in_'):
                floors = 2 if rng.random() < 0.5 else 3
            if (q, name) == ('nw', 'in_a'):
                kind, floors = 'inn', 3
            elif (q, name) == ('ne', 'in_a'):
                kind, floors = 'shop', 2
            flat = None
            if region == 'ember' and rng.random() < 0.3:
                flat = False  # 灰炉にも切妻をいくつか (並びが単調にならないように)
            building(bp, s, x0, z0, x1, z1, floors, door, rng, chimney=rng.random() < 0.6 or kind == 'inn', flat=flat, kind=kind)
            dx, dz, facing = door
            fx, fz = FACE[facing]
            if kind in ('inn', 'shop'):
                # 戸の上の看板 (字は ContinentTowns が書く)
                wood = s['door'].split(':')[1].replace('_door', '')
                bp.set(dx + fx, 2, dz + fz, f'minecraft:{wood}_wall_sign[facing={facing},waterlogged=false]')
                bp.mark('sign_' + kind, dx + fx, 2, dz + fz)
            if kind == 'inn':
                bp.mark('inn', dx + fx, 0, dz + fz)
                bp.mark('npc_innkeeper', dx - fx * 2, 0, dz - fz * 2)
                # 一階の寝台 (目覚める所)
                bx, bz = x0 + 1, z0 + 1
                bp.set(bx, 0, bz, st(s['bed'], facing='north', occupied='false', part='head'))
                bp.set(bx, 0, bz + 1, st(s['bed'], facing='north', occupied='false', part='foot'))
                bp.mark('inn_bed', bx, 0, bz)
            elif kind == 'shop':
                bp.mark('shop', dx + fx, 0, dz + fz)
                bp.mark('npc_tool_merchant', dx - fx * 2, 0, dz - fz * 2)
                # 店先の品台 (戸の脇)
                for k in (-2, 2):
                    px, pz = (dx + fx, dz + k) if fx else (dx + k, dz + fz)
                    bp.set(px, 0, pz, 'minecraft:barrel[facing=up,open=false]')
            else:
                # 戸の脇の飾り (樽・花の鉢・薪)
                k = 2 if (x0 + z0) % 2 else -2
                px, pz = (dx + fx, dz + k) if fx else (dx + k, dz + fz)
                bp.set(px, 0, pz, rng.choice(['minecraft:barrel[facing=up,open=false]', 'minecraft:potted_red_tulip', 'minecraft:spruce_log[axis=x]', 'minecraft:composter[level=0]']))
        landmark(bp, s, region, q, rng)
    walls(bp, s, region, rng)
    # 道ばたの木 (区画のあいだの空き地)
    for x, z in ((19, 9), (-19, -9), (9, -19), (-9, 19), (33, -18), (-33, 18), (18, 33), (-18, -33)):
        tree(bp, region, x, z)
    return bp
