"""町の仕上げ (手本の水準へ): 建て終えた Vox に、風化の斑・窓の鎧戸と花の箱・戸の灯り・蔦・煙突の煙・小路を渡る鎖の灯りを足す。

手本 (docs/review/build_refs) から取ったこと:
- 一つの面を一つのブロックで塗らない。苔・ひび・色の違う石を、点ではなく斑 (かたまり) で混ぜ、下ほど苔むす。
- 窓のまわりに奥行き (鎧戸・花の箱)。戸には灯り。壁には蔦。
- 煙突から煙。通りの上を渡る鎖と灯りで、上の空間にも物を置く。

PlanTown.finish() の最後 (connect の前) に dress(town) を呼ぶ。owner (柱 → 建物の番号) を使う。
"""
import math

from vox import DX, OPP

# 風化の斑: 元のブロック → (替え, 重み)。形 (階段・半ブロック・塀) は、替えにも同じ形があるものだけ
FAMILY = {
    'stone_bricks': [('mossy_stone_bricks', 3), ('cracked_stone_bricks', 2)],
    'stone_brick': [('mossy_stone_brick', 1)],                       # stairs / slab / wall
    'cobblestone': [('mossy_cobblestone', 1)],
    'deepslate_tiles': [('cracked_deepslate_tiles', 2), ('deepslate_bricks', 1)],
    'deepslate_tile': [('deepslate_brick', 1)],
    'deepslate_bricks': [('cracked_deepslate_bricks', 1)],
    'polished_blackstone_bricks': [('cracked_polished_blackstone_bricks', 2)],
    'nether_bricks': [('cracked_nether_bricks', 1)],
    'weathered_cut_copper': [('oxidized_cut_copper', 2), ('exposed_cut_copper', 1)],
    'tuff_bricks': [('polished_tuff', 1), ('chiseled_tuff_bricks', 1)],
    'smooth_sandstone': [('sandstone', 2), ('cut_sandstone', 1)],
    'smooth_sandstone_stairs': [('sandstone_stairs', 1)],
    'sandstone': [('smooth_sandstone', 1)],
    'bricks': [('mud_bricks', 1)],
    'prismarine_bricks': [('prismarine', 1)],
    'dark_prismarine': [('prismarine_bricks', 1)],
}
SHAPES = ('_stairs', '_slab', '_wall')
SKIP_TOP = 2  # 地面 (y = -1) の舗装は別 (舗装はもう混ぜてある)


def _hash(x, y, z, s=0):
    h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (s * 2654435761)
    h = (h ^ (h >> 13)) * 1274126177
    return ((h ^ (h >> 16)) & 0xFFFFFF) / float(0xFFFFFF)


def noise(x, y, z, cell=3.0, s=0):
    """かたまりの斑 (値のノイズを三方向に線形で補間)"""
    fx, fy, fz = x / cell, y / cell, z / cell
    ix, iy, iz = math.floor(fx), math.floor(fy), math.floor(fz)
    tx, ty, tz = fx - ix, fy - iy, fz - iz
    acc = 0.0
    for a in (0, 1):
        for b in (0, 1):
            for c in (0, 1):
                w = (tx if a else 1 - tx) * (ty if b else 1 - ty) * (tz if c else 1 - tz)
                acc += w * _hash(ix + a, iy + b, iz + c, s)
    return acc


def weather(v, region_ix=0, amount=0.42):
    """風化の斑。下ほど (y が低いほど) 苔とひびが多い"""
    out = {}
    for (x, y, z), s in v.b.items():
        if y < 0 or s == 'air':
            continue
        n = s.split('[')[0]
        base, shape = n, ''
        for sh in SHAPES:
            if n.endswith(sh):
                base, shape = n[:-len(sh)], sh
                break
        alts = FAMILY.get(base) if not shape else FAMILY.get(base) or FAMILY.get(base + 's')
        if not alts:
            continue
        if shape:
            alts = [(a, w) for (a, w) in alts if _shape_ok(a, shape)]
            if not alts:
                continue
        low = max(0.0, 1.0 - y / 9.0) * 0.25
        k = noise(x, y, z, 3.0, region_ix) + low
        if k < 1.0 - amount:
            continue
        tot = sum(w for _, w in alts)
        r = _hash(x, y, z, 7) * tot
        for a, w in alts:
            r -= w
            if r <= 0:
                break
        rest = s[len(n):]
        out[(x, y, z)] = a + shape + rest
    v.b.update(out)
    return len(out)


_SHAPED = {
    '_stairs': {'mossy_stone_brick', 'deepslate_brick', 'sandstone', 'prismarine', 'prismarine_brick', 'mossy_cobblestone',
                'oxidized_cut_copper', 'exposed_cut_copper', 'polished_tuff', 'mud_brick'},
    '_slab': {'mossy_stone_brick', 'deepslate_brick', 'sandstone', 'prismarine', 'prismarine_brick', 'mossy_cobblestone',
              'oxidized_cut_copper', 'exposed_cut_copper', 'polished_tuff', 'mud_brick', 'cut_sandstone'},
    '_wall': {'mossy_stone_brick', 'deepslate_brick', 'sandstone', 'prismarine', 'mossy_cobblestone', 'polished_tuff', 'mud_brick'},
}


def _shape_ok(alt, shape):
    return alt in _SHAPED[shape]


# ---------------------------------------------------------------- 家の面
def _bbox(owner):
    bb = {}
    for (x, z), o in owner.items():
        b = bb.get(o)
        bb[o] = (x, z, x, z) if b is None else (min(b[0], x), min(b[1], z), max(b[2], x), max(b[3], z))
    return bb


def _air(v, x, y, z):
    return v.b.get((x, y, z)) in (None, 'air')


def _solid(s):
    if s in (None, 'air'):
        return False
    n = s.split('[')[0]
    return not any(t in n for t in ('pane', 'glass', 'door', 'trapdoor', 'stairs', 'slab', 'fence', 'wall', 'lantern', 'torch',
                                    'leaves', 'vine', 'chain', 'carpet', 'potted', 'flower', 'sign', 'banner', 'campfire', 'bars'))


def windows(v, owner, bb):
    """外へ向いた窓 (板ガラス) の連なり: (o, y, 外の向き, [(x, z) …])"""
    seen = set()
    out = []
    for (x, y, z), s in v.b.items():
        if y < 1 or (x, y, z) in seen or 'pane' not in s.split('[')[0]:
            continue
        o = owner.get((x, z))
        if o is None:
            continue
        cx, cz = (bb[o][0] + bb[o][2]) / 2, (bb[o][1] + bb[o][3]) / 2
        face = None
        for f, (dx, dz) in DX.items():
            if _air(v, x + dx, y, z + dz) and _air(v, x + 2 * dx, y, z + 2 * dz) and (dx * (x - cx) + dz * (z - cz)) > 0:
                face = f
                break
        if not face:
            continue
        dx, dz = DX[face]
        ax, az = (1, 0) if dz else (0, 1)
        run = [(x, z)]
        for sg in (1, -1):
            k = 1
            while 'pane' in (v.b.get((x + ax * k * sg, y, z + az * k * sg)) or '') and k < 6:
                run.append((x + ax * k * sg, z + az * k * sg))
                k += 1
        for (a, b) in run:
            seen.add((a, y, b))
        out.append((o, y, face, sorted(run)))
    return out


def dress_facades(town):
    v, st, rng = town.v, town.st, town.rng
    owner = town.owner
    bb = _bbox(owner)
    wood = getattr(st, 'shutter_wood', None) or st.wood
    style = {}           # 建物ごとに一つの決め (全部の家に同じ飾りを付けない)
    n_sh = n_box = 0
    wins = windows(v, owner, bb)
    for (o, y, face, run) in wins:
        if o not in style:
            style[o] = (rng.random() < 0.55, rng.random() < 0.45, rng.choice(['flowering_azalea_leaves', 'azalea_leaves', 'flowering_azalea_leaves']))
        shut, box, leaf = style[o]
        dx, dz = DX[face]
        ax, az = (1, 0) if dz else (0, 1)
        # 二段の窓は上の段だけ数えない (鎧戸は窓の高さぶん)
        if shut:
            ends = [(run[0][0] - ax, run[0][1] - az), (run[-1][0] + ax, run[-1][1] + az)]
            for (a, b) in ends:
                X, Z = a + dx, b + dz
                if _air(v, X, y, Z) and _solid(v.b.get((a, y, b))):
                    v.set(X, y, Z, '%s_trapdoor[facing=%s,half=bottom,open=true,powered=false,waterlogged=false]' % (wood, face))
                    n_sh += 1
        if box and 'pane' not in (v.b.get((run[0][0], y - 1, run[0][1])) or ''):
            cells = [(a + dx, b + dz) for (a, b) in run]
            if all(_air(v, X, y - 1, Z) and town.occ.get((X, Z)) not in ('road', 'plaza') or y - 1 >= 2 for (X, Z) in cells) \
                    and all(_air(v, X, y - 1, Z) for (X, Z) in cells):
                for (X, Z) in cells:
                    v.set(X, y - 1, Z, '%s[distance=1,persistent=true,waterlogged=false]' % leaf)
                    n_box += 1
    return n_sh, n_box


def door_lamps(town):
    """戸の両脇 (庇の外) の壁に腕木 (柵) と吊り灯り。戸は厚い壁の奥にあるので、戸の前の空いた所から測る"""
    v, st = town.v, town.st
    fence = st.fence
    n = 0
    doors = []
    for (x, y, z), s in list(v.b.items()):
        nm = s.split('[')[0]
        if not nm.endswith('_door') or 'half=lower' not in s or y > 1 or town.owner.get((x, z)) is None:
            continue
        f0 = s.split('facing=')[1].split(',')[0].split(']')[0]
        doors.append((x, y, z, (f0, OPP[f0])))
    for (x, y, z, face) in getattr(v, 'fake_doors', []):
        doors.append((x, y, z, (face,)))
    for (x, y, z, outs) in doors:
        found = None
        for out in outs:
            dx, dz = DX[out]
            k = 1
            while k < 4 and not (_air(v, x + dx * k, y, z + dz * k) and _air(v, x + dx * k, y + 1, z + dz * k)):
                k += 1
            c = (x + dx * k, z + dz * k)
            # 外 = 戸の前の柱が家の中でない (持ち主がない、あるいは道の上の軒)
            if k < 4 and (town.owner.get(c) is None or town.occ.get(c) in ('road', 'plaza')):
                found = (out, k)
                break
        if not found:
            continue
        out, k = found
        dx, dz = DX[out]
        fx, fz = x + dx * k, z + dz * k
        ax, az = (1, 0) if dz else (0, 1)
        done = False
        for j in (2, 1, 3, 4):
            for sg in (1, -1):
                X, Z = fx + ax * j * sg, fz + az * j * sg
                if all(_air(v, X, y + h, Z) for h in (0, 1, 2)) and _solid(v.b.get((X - dx, y + 2, Z - dz)))                         and v.b.get((X, y + 3, Z)) in (None, 'air') or False:
                    v.set(X, y + 2, Z, fence)
                    v.set(X, y + 1, Z, 'lantern[hanging=true,waterlogged=false]')
                    n += 1
                    done = True
                    break
            if done:
                break
    return n


def ivy(town, share=0.3):
    """蔦: 石の壁の、通りに面しない側に、上から垂らす"""
    v, rng = town.v, town.rng
    owner = town.owner
    bb = _bbox(owner)
    picked = {o for o in bb if rng.random() < share}
    n = 0
    for (x, y, z), s in list(v.b.items()):
        if y < 2 or y > 9:
            continue
        o = owner.get((x, z))
        if o not in picked or not _solid(s):
            continue
        nm = s.split('[')[0]
        if not any(t in nm for t in ('stone', 'brick', 'tuff', 'cobble', 'sandstone', 'blackstone', 'andesite', 'calcite', 'terracotta')):
            continue
        if noise(x, y, z, 2.5, 11) < 0.62:
            continue
        cx, cz = (bb[o][0] + bb[o][2]) / 2, (bb[o][1] + bb[o][3]) / 2
        for f, (dx, dz) in DX.items():
            X, Z = x + dx, z + dz
            if (dx * (x - cx) + dz * (z - cz)) <= 0 or town.occ.get((X, Z)) in ('road', 'plaza'):
                continue
            if not _air(v, X, y, Z):
                continue
            att = OPP[f]       # 蔦の付く面 = 壁の側
            L = 1 + int(_hash(x, y, z, 3) * 4)
            for k in range(L):
                if not _air(v, X, y - k, Z) or y - k < 1:
                    break
                v.set(X, y - k, Z, 'vine[%s]' % ','.join('%s=%s' % (g, 'true' if g == att else 'false') for g in ('east', 'north', 'south', 'up', 'west')))
                n += 1
            break
    return n


def chimney_smoke(town):
    """煙突の頂の半ブロックを焚き火に (煙が上がる)。v.chimneys は _chimney が覚えた (x, top, z)"""
    v = town.v
    n = 0
    for (x, top, z) in getattr(v, 'chimneys', []):
        if (v.b.get((x, top + 1, z)) or '').endswith('_slab[type=bottom,waterlogged=false]') or 'slab' in (v.b.get((x, top + 1, z)) or ''):
            v.set(x, top + 1, z, 'campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
            n += 1
    return n


def lane_chains(town, every=9):
    """小路と通りの上を渡る鎖と吊り灯り: 道の両側の家の壁が近い (幅 7 まで) 所に、一階の上の高さで"""
    v, occ = town.v, town.occ
    owner = town.owner
    n = 0
    used = []
    cells = sorted(c for c, t in occ.items() if t == 'road')
    for (x, z) in cells:
        if any(abs(x - a) + abs(z - b) < every for (a, b) in used):
            continue
        for (dx, dz) in ((1, 0), (0, 1)):
            y = 4
            # 両側へ、家の壁 (この高さで固いブロック) を探す
            ends = []
            for sg in (1, -1):
                for k in range(0, 5):
                    X, Z = x + dx * k * sg, z + dz * k * sg
                    if owner.get((X, Z)) is not None and _solid(v.b.get((X, y, Z))):
                        ends.append(k * sg)
                        break
            if len(ends) != 2 or ends[0] - ends[1] < 3:
                continue
            a, b = ends[1] + 1, ends[0] - 1
            line = [(x + dx * k, z + dz * k) for k in range(a, b + 1)]
            if not all(_air(v, X, y, Z) and _air(v, X, y - 1, Z) for (X, Z) in line):
                continue
            axis = 'x' if dx else 'z'
            for (X, Z) in line:
                v.set(X, y, Z, 'chain[axis=%s,waterlogged=false]' % axis)
            mx, mz = line[len(line) // 2]
            v.set(mx, y - 1, mz, 'lantern[hanging=true,waterlogged=false]')
            used.append((x, z))
            n += 1
            break
    return n


def dress(town):
    r = {}
    r['weather'] = weather(town.v, getattr(town, 'region_ix', 0))
    r['shutters'], r['boxes'] = dress_facades(town)
    r['lamps'] = door_lamps(town)
    r['ivy'] = ivy(town)
    r['smoke'] = chimney_smoke(town)
    r['chains'] = lane_chains(town)
    r['ground'] = ground(town)
    print('  dress', r)
    return r


# ---------------------------------------------------------------- 地面
GROUND = {
    # 土地ごと: (斑の地, 道の脇のすり減り, 岩)
    'whale': (['moss_block', 'podzol', 'coarse_dirt'], ['coarse_dirt', 'rooted_dirt', 'dirt_path'], ['mossy_cobblestone', 'andesite', 'stone']),
    'tide': (['sand', 'coarse_dirt', 'gravel'], ['sand', 'dirt_path', 'coarse_dirt'], ['sandstone', 'smooth_sandstone', 'calcite']),
    'storm': (['coarse_dirt', 'gravel', 'stone'], ['gravel', 'dirt_path', 'coarse_dirt'], ['tuff', 'andesite', 'stone']),
    'ember': (['coarse_dirt', 'rooted_dirt', 'blackstone'], ['coarse_dirt', 'dirt_path', 'blackstone'], ['blackstone', 'basalt[axis=y]', 'tuff']),
}


def ground(town):
    """草だけの地面をやめる: 道の脇はすり減り、ところどころ土地の斑、空き地に小さな岩"""
    v, occ = town.v, town.occ
    patch, worn, rock = GROUND.get(getattr(town, 'region', 'whale'), GROUND['whale'])
    ix = getattr(town, 'region_ix', 0)
    n = 0
    rocks = []
    for (x, y, z), s in list(v.b.items()):
        if y != -1 or not s.startswith('grass_block'):
            continue
        if v.b.get((x, 0, z)) not in (None, 'air') and 'grass' not in v.b.get((x, 0, z)) and 'flower' not in v.b.get((x, 0, z)):
            continue
        near = any(occ.get((x + a, z + b)) in ('road', 'plaza') for a in (-1, 0, 1) for b in (-1, 0, 1))
        k = noise(x, 0, z, 4.0, ix + 21)
        if near and _hash(x, 0, z, 5) < 0.55:
            v.set(x, -1, z, worn[int(_hash(x, 1, z, 6) * len(worn))])
            if v.b.get((x, 0, z), 'air') != 'air' and 'path' in v.b[(x, -1, z)]:
                v.set(x, 0, z, 'air')
            n += 1
        elif k > 0.7 and _hash(x, 0, z, 8) < 0.8:
            v.set(x, -1, z, patch[int(_hash(x, 2, z, 9) * len(patch))])
            n += 1
        elif occ.get((x, z)) is None and k < 0.18 and _hash(x, 0, z, 10) < 0.02 and all(abs(x - a) + abs(z - b) > 10 for (a, b) in rocks) \
                and all(occ.get((x + a, z + b)) is None for a in range(-2, 3) for b in range(-2, 3)):
            rocks.append((x, z))
            _boulder(v, x, z, rock, _hash(x, 3, z, 12))
    return n, len(rocks)


def _boulder(v, x, z, rock, r):
    """小さな岩: 2〜5 個のブロックを低く積み、苔を少し"""
    cells = [(0, 0, 0), (1, 0, 0), (0, 0, 1)] + ([(1, 0, 1), (0, 1, 0)] if r > 0.4 else []) + ([(-1, 0, 0)] if r > 0.7 else [])
    for i, (a, b, c) in enumerate(cells):
        v.set(x + a, b, z + c, rock[int(_hash(x + a, b, z + c, 13) * len(rock))])
    if r > 0.5:
        v.set(x, 2 if (0, 1, 0) in cells else 1, z, 'moss_carpet')
