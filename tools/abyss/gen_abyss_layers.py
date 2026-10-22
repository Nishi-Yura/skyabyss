# -*- coding: utf-8 -*-
"""深淵の四つの層と心臓の間 (docs/design/36 の 2・8 章。2026-10-23 の作り直し: 層ごとに探す場所)。

python -I tools/abyss/gen_abyss_layers.py  →  src/main/resources/data/skyabyss/opening/abyss_layers.json (runs 形式。空気も書く)

形 (層ごとに同じ骨組み。中身は tools/abyss/layer_<層>.py):
  - 野営 (camp): 層の入口の部屋。中心 (0, Y, 0)、内のりの半分 8、高さ 7。焚き火 (休み場 rest_<n>)・生き残り (survivor_<層>)・帳面・溜まり (pool_<層>)。
    東・南・西の壁に道の口 (三本の道)、同じ壁の脇に近道の口 (格子で閉じている)。北の壁に門 (gate_<層>。印が三つそろうと通れる)
  - 道 (route): 野営から東・南・西へ一本ずつ。部屋と廊下の並び (layer_<層>.ROUTES)。道の終わりの部屋に印の台 (mark_<層>_<1..3>)
  - 近道 (shortcut): 印の部屋から野営へまっすぐ戻る廊下。野営の側の格子 (scut_<層>_<i>) は、その印を誰かが取ると開く (ワールドで共有)
  - 門の奥: 下りの階段 → 層の主の間 (arena_<層>。丸い間、半径 14)。主を倒すと下りの鎖 (descend_<層>) で次の層の野営へ
  - 心臓の間: 前と同じ (中心 (0, -300, 0)、半径 30)。南に控えの間 (heart_in・rest_5) と、間へ続く廊下
  - 囲い: 空気のマスのまわり 2 マスを層の岩で埋める (外の何もない空間は見えない。継ぎ目もない)
  - 道ごとの持ち主の印 (camp・r1〜3・gate・arena) を付け、ちがう持ち主の空気が隣り合ったら止める (道どうしが壁を抜けてつながらない)

局所の座標: 道の向き (u = 野営から外へ、w = 横)。東 (x, z) = (u, w)、南 = (-w, u)、西 = (-u, -w)。北 (門) = (w, -u)。
  道は w ≥ 0 の側、近道は w = -6 の線を通る。くさび |w| < u の中に収める (隣の道とは重ならない)
"""
from __future__ import annotations

import importlib.util
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'capital'))
import vox  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss', 'opening', 'abyss_layers.json')

ORDER = ['bone', 'salt', 'storm', 'ash']
CAMP_Y = {'bone': 180, 'salt': 60, 'storm': -60, 'ash': -180}
CAMP_HALF = 8
CAMP_H = 7
SHELL = 2
DIRS = {'e': lambda u, w: (u, w), 's': lambda u, w: (-w, u), 'w': lambda u, w: (-u, -w), 'n': lambda u, w: (w, -u)}
ROUTE_DIRS = ['e', 's', 'w']
ROUTE_W = 3          # 道の口の横の位置
SCUT_W = -6          # 近道の線
ARENA_R = 14
ARENA_DROP = 6       # 主の間の床は野営より 6 下
ARENA_U = 48         # 主の間の中心 (北へ)
ARENA_H = 11
HEART_TOP = -252
HEART_BOTTOM = -300
HEART_R = 30

# 層の素材: 壁 (柄)・床・床の縁・柱・灯り
LAYERS = {
    'bone': dict(floor='polished_diorite', floor2='bone_block[axis=x]', trim='smooth_basalt', pillar='bone_block[axis=y]', lamp='soul_lantern',
                 seal='chiseled_polished_blackstone', accent='bone_block[axis=y]'),
    'salt': dict(floor='smooth_quartz', floor2='diorite', trim='white_terracotta', pillar='quartz_pillar[axis=y]', lamp='soul_lantern',
                 seal='chiseled_quartz_block', accent='prismarine_bricks'),
    'storm': dict(floor='polished_tuff', floor2='tuff_bricks', trim='oxidized_cut_copper', pillar='weathered_copper', lamp='soul_lantern',
                  seal='chiseled_tuff_bricks', accent='oxidized_copper'),
    'ash': dict(floor='polished_blackstone', floor2='blackstone', trim='polished_basalt[axis=y]', pillar='basalt[axis=y]', lamp='soul_lantern',
                seal='gilded_blackstone', accent='magma_block'),
}


def wall_block(layer, x, y, z, rng):
    """層ごとの壁の柄"""
    a = math.degrees(math.atan2(z, x)) % 360
    if layer == 'bone':
        if y % 9 == 0:
            return 'smooth_basalt'
        return 'bone_block[axis=y]' if (x * 3 + z * 5) % 11 == 0 else ('calcite' if (x + z) % 5 else 'dripstone_block')
    if layer == 'salt':
        band = (y // 3) % 5
        return ['calcite', 'white_terracotta', 'calcite', 'smooth_quartz', 'diorite'][band]
    if layer == 'storm':
        if y % 8 == 0:
            return 'oxidized_cut_copper'
        return 'tuff_bricks' if (y // 2 + (x + z) // 4) % 2 else 'polished_tuff'
    if layer == 'ash':
        if (x * 7 + z * 3) % 13 == 0:
            return 'basalt[axis=y]'
        return 'cracked_polished_blackstone_bricks' if rng.random() < 0.12 else 'polished_blackstone_bricks'
    return 'deepslate_tiles'


HEART_WALL = ['deepslate_tiles', 'sculk', 'deepslate_tiles', 'crying_obsidian']


class Layout:
    """空気のマス (持ち主つき) を集め、最後に囲い・床を付ける"""

    def __init__(self, v):
        self.v = v
        self.air = {}          # (x, y, z) -> 持ち主
        self.layer_of = {}     # 持ち主 -> 層 (囲いの柄)
        self.floor_of = {}     # (x, z, y) 床にする所 -> 素材 (空気の下)
        self.fixed = {}        # 空気でも囲いでもない、置いたもの (飾り・灯り・仕掛け)
        self.allowed = set()   # 隣り合ってよい持ち主の組
        self.near = {}         # 決まった所の近くだけ隣り合ってよい組

    def carve(self, x, y, z, owner):
        prev = self.air.get((x, y, z))
        if prev is not None and prev != owner and not self._ok(prev, owner, (x, y, z)):
            raise SystemExit('carve overlap at %s: %s vs %s' % ((x, y, z), prev, owner))
        self.air[(x, y, z)] = owner

    def box(self, x0, y0, z0, x1, y1, z1, owner):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                for y in range(min(y0, y1), max(y0, y1) + 1):
                    self.carve(x, y, z, owner)

    def put(self, x, y, z, s):
        self.fixed[(x, y, z)] = s

    def allow(self, a, b):
        self.allowed.add(frozenset((a, b)))

    def allow_near(self, a, b, at, r):
        """a と b の空気が隣り合ってよいのは at から r の内だけ"""
        self.near.setdefault(frozenset((a, b)), []).append((at, r))

    def _ok(self, o, o2, p):
        k = frozenset((o, o2))
        if k in self.allowed:
            return True
        for (at, r) in self.near.get(k, ()):
            if max(abs(p[0] - at[0]), abs(p[1] - at[1]), abs(p[2] - at[2])) <= r:
                return True
        return False

    def check_neighbors(self):
        bad = []
        for (x, y, z), o in self.air.items():
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        o2 = self.air.get((x + dx, y + dy, z + dz))
                        if o2 is not None and o2 != o and not self._ok(o, o2, (x, y, z)):
                            bad.append(((x, y, z), o, o2))
        if bad:
            for b in bad[:10]:
                print('touch', b)
            raise SystemExit('%d cells of different owners touch' % len(bad))

    def finish(self):
        v = self.v
        rng = v.rng
        layer_by_cell = {}
        for p, o in self.air.items():
            layer_by_cell[p] = self.layer_of.get(o, 'heart')
        # 囲い (空気のまわり SHELL マス。空気でも置いたものでもない所)
        for (x, y, z), o in self.air.items():
            L = layer_by_cell[(x, y, z)]
            for dx in range(-SHELL, SHELL + 1):
                for dy in range(-SHELL, SHELL + 1):
                    for dz in range(-SHELL, SHELL + 1):
                        q = (x + dx, y + dy, z + dz)
                        if q in self.air or q in v.b:
                            continue
                        if L == 'heart':
                            v.b[q] = rng.choice(HEART_WALL)
                        else:
                            v.b[q] = wall_block(L, q[0], q[1], q[2], rng)
        # 床 (空気の真下の囲い)
        for (x, y, z), o in self.air.items():
            if (x, y - 1, z) in self.air:
                continue
            L = layer_by_cell[(x, y, z)]
            if L == 'heart':
                continue
            m = LAYERS[L]
            fl = self.floor_of.get((x, z, y - 1))
            if fl is None:
                fl = m['floor'] if (x * 31 + z * 17) % 7 else m['floor2']
            v.b[(x, y - 1, z)] = fl
        for p in self.air:
            v.b[p] = 'air'
        for p, s in self.fixed.items():
            v.b[p] = s


class Route:
    """一本の道の局所の座標の書き手"""

    def __init__(self, lay, layer, d, owner, y0):
        self.lay = lay
        self.layer = layer
        self.d = d
        self.f = DIRS[d]
        self.owner = owner
        self.y0 = y0

    def xz(self, u, w):
        return self.f(u, w)

    def facing(self, du, dw):
        """局所の向き (du, dw) の世界の向き (north/south/east/west)"""
        x0, z0 = self.f(0, 0)
        x1, z1 = self.f(du, dw)
        dx, dz = x1 - x0, z1 - z0
        return {(1, 0): 'east', (-1, 0): 'west', (0, 1): 'south', (0, -1): 'north'}[(dx, dz)]

    def cell(self, u, w, y, owner=None):
        x, z = self.f(u, w)
        self.lay.carve(x, y, z, owner or self.owner)

    def put(self, u, w, y, s):
        x, z = self.f(u, w)
        self.lay.put(x, y, z, s)

    def mark(self, name, u, w, y):
        x, z = self.f(u, w)
        self.lay.v.mark(name, x, y, z)

    def room(self, u, w, y, hu, hw, h, owner=None):
        """局所の箱 (u±hu, w±hw)、立つ高さ y、高さ h"""
        for a in range(u - hu, u + hu + 1):
            for b in range(w - hw, w + hw + 1):
                for yy in range(y, y + h):
                    self.cell(a, b, yy, owner)

    def leg(self, s0, s1, y_from, y_to, free=None):
        """まっすぐの廊下の列ごとの立つ高さ。y_from → y_to を一段に二マスで。段は部屋の外 (free が真の列) の一番長い並びの真ん中にまとめる
        (部屋の床の上に段を置くと、床のない宙の列ができて上れない)"""
        n = abs(s1 - s0)
        step = 1 if s1 >= s0 else -1
        dy = y_to - y_from
        need = 2 * abs(dy)
        lo, hi = 0, n
        if dy and free is not None:
            best, cur = (0, -1), None
            for i in range(n + 1):
                if free[i]:
                    cur = i if cur is None else cur
                    if i - cur > best[1] - best[0]:
                        best = (cur, i)
                else:
                    cur = None
            lo, hi = best
        if hi - lo < need:
            raise SystemExit('leg too steep %s' % ((s0, s1, y_from, y_to, lo, hi),))
        start = lo + (hi - lo - need) // 2
        ys = []
        for i in range(n + 1):
            k = max(0, min(need, i - start))
            ys.append(y_from + (k // 2) * (1 if dy > 0 else -1) if dy else y_from)
        return [(s0 + i * step, ys[i]) for i in range(n + 1)], step

    def corridor(self, path, h=4, half=1, owner=None, lamps=7):
        """折れ線の廊下: path = [(u, w, y), ...] (隣どうしは u か w のどちらかだけが変わる)。幅 2*half+1"""
        owner = owner or self.owner
        run = 0
        for (u0, w0, y0), (u1, w1, y1) in zip(path, path[1:]):
            if u0 != u1 and w0 != w1:
                raise SystemExit('corridor must be axis aligned %s' % ((u0, w0, u1, w1),))
            axis = 'u' if u0 != u1 else 'w'
            s0, s1 = (u0, u1) if axis == 'u' else (w0, w1)
            n = abs(s1 - s0)
            sg = 1 if s1 >= s0 else -1
            free = []
            for i in range(n + 1):
                cu, cw = (s0 + i * sg, w0) if axis == 'u' else (u0, s0 + i * sg)
                x, z = self.f(cu, cw)
                free.append((x, min(y0, y1), z) not in self.lay.air and (x, max(y0, y1), z) not in self.lay.air
                            and (x, min(y0, y1) - 1, z) not in self.lay.air)
            cells, step = self.leg(s0, s1, y0, y1, free)
            for i, (s, y) in enumerate(cells):
                u, w = (s, w0) if axis == 'u' else (u0, s)
                nxt = cells[i + 1][1] if i + 1 < len(cells) else y
                for k in range(-half, half + 1):
                    cu, cw = (u, w + k) if axis == 'u' else (u + k, w)
                    for yy in range(min(y, nxt), max(y, nxt) + h):
                        self.cell(cu, cw, yy, owner)
                    if nxt > y:
                        # 上りの段: この列の足もとに階段 (上る向き)
                        du, dw = (step, 0) if axis == 'u' else (0, step)
                        self.put(cu, cw, y, '%s[facing=%s,half=bottom,shape=straight,waterlogged=false]' % (stair_of(self.layer), self.facing(du, dw)))
                    elif nxt < y:
                        # 下りの段: 次の列の足もとに階段 (戻る向きが上り)
                        du, dw = (-step, 0) if axis == 'u' else (0, -step)
                        nu, nw = (cu + step, cw) if axis == 'u' else (cu, cw + step)
                        self.put(nu, nw, nxt, '%s[facing=%s,half=bottom,shape=straight,waterlogged=false]' % (stair_of(self.layer), self.facing(du, dw)))
                run += 1
                if lamps and run % lamps == 0:
                    self.put(u, w, y + h, LAYERS[self.layer]['lamp'] + '[hanging=true,waterlogged=false]')


def stair_of(layer):
    return {'bone': 'polished_diorite_stairs', 'salt': 'smooth_quartz_stairs', 'storm': 'polished_tuff_stairs',
            'ash': 'polished_blackstone_stairs'}[layer]


def slab_of(layer):
    return {'bone': 'polished_diorite_slab', 'salt': 'smooth_quartz_slab', 'storm': 'polished_tuff_slab',
            'ash': 'polished_blackstone_slab'}[layer] + '[type=bottom,waterlogged=false]'


class Ctx:
    """層の組み立て (layer_<層>.py) に渡すもの"""

    def __init__(self, v, lay):
        self.v = v
        self.lay = lay
        self.LAYERS = LAYERS
        self.CAMP_Y = CAMP_Y
        self.ORDER = ORDER
        self.wall_block = wall_block
        self.stair_of = stair_of
        self.slab_of = slab_of

    def route(self, layer, i):
        d = ROUTE_DIRS[i - 1]
        return Route(self.lay, layer, d, '%s_r%d' % (layer, i), CAMP_Y[layer])


def build_camp(ctx, layer, n):
    """野営の部屋と門・主の間・下りの鎖"""
    lay, v = ctx.lay, ctx.v
    Y = CAMP_Y[layer]
    m = LAYERS[layer]
    camp = '%s_camp' % layer
    lay.layer_of[camp] = layer
    lay.box(-CAMP_HALF, Y, -CAMP_HALF, CAMP_HALF, Y + CAMP_H - 1, CAMP_HALF, camp)
    # 天井を少し高く (真ん中)
    lay.box(-4, Y + CAMP_H, -4, 4, Y + CAMP_H + 1, 4, camp)
    # 床の模様: 縁を一回り、真ん中に焚き火の輪
    for x in range(-CAMP_HALF, CAMP_HALF + 1):
        for z in range(-CAMP_HALF, CAMP_HALF + 1):
            if max(abs(x), abs(z)) == CAMP_HALF:
                lay.floor_of[(x, z, Y - 1)] = m['trim']
            elif max(abs(x), abs(z)) <= 2:
                lay.floor_of[(x, z, Y - 1)] = 'polished_deepslate'
    # 柱 (四隅)
    for sx in (-1, 1):
        for sz in (-1, 1):
            for yy in range(Y, Y + CAMP_H):
                lay.put(sx * (CAMP_HALF - 1), yy, sz * (CAMP_HALF - 1), m['pillar'])
            lay.put(sx * (CAMP_HALF - 2), Y + CAMP_H - 1, sz * (CAMP_HALF - 1), m['lamp'] + '[hanging=true,waterlogged=false]')
            del_air(lay, sx * (CAMP_HALF - 1), Y, sz * (CAMP_HALF - 1), Y + CAMP_H - 1)
    # 焚き火 (休み場)。暖かい色の火
    lay.put(0, Y, 0, 'campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    v.mark('rest_%d' % n, 0, Y, 2)
    v.mark('camp_%s' % layer, 0, Y, 0)
    for (x, z) in ((2, 0), (-2, 0), (0, -2)):
        lay.put(x, Y, z, 'cobbled_deepslate_slab[type=bottom,waterlogged=false]')
    # 寝床と荷 (南西の隅)、帳面 (南東)
    lay.put(-5, Y, 5, 'brown_wool')
    lay.put(-5, Y, 4, 'brown_carpet')
    lay.put(-6, Y, 5, 'barrel[facing=up,open=false]')
    lay.put(5, Y, 5, 'lectern[facing=north,has_book=true,powered=false]')
    v.mark('journal_%s_0' % layer, 5, Y, 5)
    # 生き残りの立つ所 (焚き火の東)
    v.mark('survivor_%s' % layer, 3, Y, 1)
    # 溜まり (返った鼓動の池。北西の隅寄り)。ここで術を覚える
    for x in range(-6, -3):
        for z in range(-6, -3):
            lay.floor_of[(x, z, Y - 1)] = 'sculk'
    lay.put(-5, Y, -5, 'sculk_shrieker[can_summon=false,shrieking=false,waterlogged=false]')
    lay.put(-5, Y + CAMP_H - 1, -5, 'soul_lantern[hanging=true,waterlogged=false]')
    v.mark('pool_%s' % layer, -5, Y, -5)
    # 門 (北の壁の外、二枚の厚み)。印が三つの人だけが通れる
    gate = '%s_gate' % layer
    lay.layer_of[gate] = layer
    lay.allow(camp, gate)
    for z in (-CAMP_HALF - 1, -CAMP_HALF - 2):
        for x in range(-1, 2):
            for yy in range(Y, Y + 3):
                lay.put(x, yy, z, m['seal'])
    v.mark('gate_%s' % layer, 0, Y, -CAMP_HALF - 1)
    v.mark('gate_%s_out' % layer, 0, Y, -CAMP_HALF + 1)
    v.mark('gate_%s_in' % layer, 0, Y, -CAMP_HALF - 4)
    # 門の上の三つの穴 (印の台座。持つ印の数だけ光る: story/ch5/layers/AbyssMarks)
    for k, x in enumerate((-2, 0, 2)):
        lay.put(x, Y + 4, -CAMP_HALF - 1, 'chiseled_deepslate')
        v.mark('socket_%s_%d' % (layer, k + 1), x, Y + 4, -CAMP_HALF)
    r = Route(lay, layer, 'n', gate, Y)
    # 門の奥: 二マス平らに、それから下りの階段で主の間へ
    r.corridor([(CAMP_HALF + 3, 0, Y), (CAMP_HALF + 6, 0, Y), (ARENA_U - ARENA_R - 1, 0, Y - ARENA_DROP)], h=4, half=1, lamps=6)
    # 主の間 (丸い。高さ ARENA_H)
    arena = '%s_arena' % layer
    lay.layer_of[arena] = layer
    lay.allow(gate, arena)
    ay = Y - ARENA_DROP
    for a in range(-ARENA_R, ARENA_R + 1):
        for b in range(-ARENA_R, ARENA_R + 1):
            d = math.hypot(a, b)
            if d > ARENA_R + 0.4:
                continue
            top = ARENA_H if d < ARENA_R - 3 else ARENA_H - 2
            x, z = DIRS['n'](ARENA_U + a, b)
            for yy in range(ay, ay + top):
                lay.carve(x, yy, z, arena)
            if d > ARENA_R - 1.2:
                lay.floor_of[(x, z, ay - 1)] = m['trim']
    ax, az = DIRS['n'](ARENA_U, 0)
    v.mark('arena_%s' % layer, ax, ay, az)
    v.mark('lord_%s' % layer, ax, ay, az - 4)
    # 柱の灯台 (四つ。骨の層では主の戦いの灯り: 点けた灯りの中だけ刃が通る)
    for k in range(4):
        ang = math.pi / 4 + k * math.pi / 2
        lx, lz = ax + int(round(math.cos(ang) * 9)), az + int(round(math.sin(ang) * 9))
        lay.put(lx, ay, lz, 'polished_deepslate')
        lay.put(lx, ay + 1, lz, 'polished_deepslate_wall')
        lay.put(lx, ay + 2, lz, 'soul_campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]')
        v.mark('lamp_%s_%d' % (layer, k), lx, ay + 2, lz)
    # 天井の灯り (薄く)
    for k in range(6):
        ang = k * math.pi / 3
        lay.put(ax + int(round(math.cos(ang) * 12)), ay + ARENA_H - 3, az + int(round(math.sin(ang) * 12)),
                'soul_lantern[hanging=true,waterlogged=false]')
    # 下りの鎖 (間の奥。主を倒した人だけが使える)
    dx_, dz_ = DIRS['n'](ARENA_U + ARENA_R - 2, 0)
    lay.put(dx_, ay, dz_, 'lodestone')
    for yy in range(ay + 1, ay + ARENA_H - 2):
        lay.put(dx_, yy, dz_, 'chain[axis=y,waterlogged=false]')
    v.mark('descend_%s' % layer, dx_, ay, dz_)
    return camp


def del_air(lay, x, y0, z, y1):
    for y in range(y0, y1 + 1):
        lay.air.pop((x, y, z), None)


def build_route(ctx, layer, i, spec):
    """道の部屋と廊下と近道。spec = layer_<層>.ROUTES[i-1]:
         nodes: [(名前, u, w, y_off, hu, hw, h)]  部屋 (最後が印の部屋)
         link:  部屋ごとのつなぎ方。'uw' (u を先に動かす) | 'wu' | {'from': (u, w, y_off), 'via': [(u, w, y_off), ...]}
                (from を省くと前の部屋の真ん中から。最初の部屋の前は野営の口 (u = CAMP_HALF + 1, w = ROUTE_W))
         近道は印の部屋の w の小さい側の縁から w = SCUT_W へ、そこから野営へまっすぐ"""
    lay, v = ctx.lay, ctx.v
    camp = '%s_camp' % layer
    r = ctx.route(layer, i)
    lay.layer_of[r.owner] = layer
    lay.allow(camp, r.owner)
    Y = CAMP_Y[layer]
    nodes = spec['nodes']
    rooms = {}
    for (name, u, w, yo, hu, hw, h) in nodes:
        r.room(u, w, Y + yo, hu, hw, h)
        rooms[name] = (u, w, Y + yo, hu, hw, h)
        for k in range(spec.get('room_lamps', 2)):
            lu = u + (hu - 1 if k == 0 else -(hu - 1))
            r.put(lu, w, Y + yo + h, LAYERS[layer]['lamp'] + '[hanging=true,waterlogged=false]')
    prev = (CAMP_HALF + 1, ROUTE_W, Y)
    links = spec.get('link') or ['uw'] * len(nodes)
    for (name, u, w, yo, hu, hw, h), how in zip(nodes, links):
        to = (u, w, Y + yo)
        if isinstance(how, dict):
            start = how.get('from')
            start = prev if start is None else (start[0], start[1], Y + start[2])
            path = [start] + [(a, b, Y + c) for (a, b, c) in how.get('via', [])] + [to]
        elif how == 'uw':
            if prev[1] == w:
                path = [prev, to]
            elif abs(u - prev[0]) >= abs(w - prev[1]):
                path = [prev, (u, prev[1], to[2]), to]
            else:
                path = [prev, (u, prev[1], prev[2]), to]
        else:
            if prev[0] == u:
                path = [prev, to]
            elif abs(w - prev[1]) >= abs(u - prev[0]):
                path = [prev, (prev[0], w, to[2]), to]
            else:
                path = [prev, (prev[0], w, prev[2]), to]
        if path[0] != path[-1]:
            r.corridor(path, lamps=spec.get('lamps', 7))
        prev = to
    # 近道
    name, u, w, yo, hu, hw, h = nodes[-1]
    so = '%s_s%d' % (layer, i)
    lay.layer_of[so] = layer
    lay.allow(so, camp)
    sy = Y + yo
    r2 = Route(lay, layer, ROUTE_DIRS[i - 1], so, Y)
    r2.corridor([(u, w - hw - 1, sy), (u, SCUT_W, sy), (CAMP_HALF + 1, SCUT_W, Y)], lamps=9)
    jx, jz = r2.xz(u, w - hw - 1)
    lay.allow_near(so, r.owner, (jx, sy, jz), 6)
    for uu in (CAMP_HALF + 1, CAMP_HALF + 2):
        for k in (-1, 0, 1):
            for yy in range(Y, Y + 4):
                r2.put(uu, SCUT_W + k, yy, 'iron_bars[east=true,north=true,south=true,waterlogged=false,west=true]')
    a = r2.xz(CAMP_HALF + 1, SCUT_W - 1)
    b = r2.xz(CAMP_HALF + 2, SCUT_W + 1)
    v.mark('scut_%s_%d_a' % (layer, i), min(a[0], b[0]), Y, min(a[1], b[1]))
    v.mark('scut_%s_%d_b' % (layer, i), max(a[0], b[0]), Y + 3, max(a[1], b[1]))
    # 印の台 (印の部屋の奥)
    mu = u + hu - 1
    r.put(mu, w, sy, 'polished_deepslate')
    r.put(mu, w, sy + 1, 'respawn_anchor[charges=0]')
    r.mark('mark_%s_%d' % (layer, i), mu, w, sy + 1)
    r.mark('markroom_%s_%d' % (layer, i), u, w, sy)
    return r, rooms


def build_heart(ctx):
    """心臓の間 (前の形のまま) と、南の控えの間"""
    lay, v = ctx.lay, ctx.v
    rng = v.rng
    lay.layer_of['heart'] = 'heart'
    for x in range(-HEART_R, HEART_R + 1):
        for z in range(-HEART_R, HEART_R + 1):
            d = math.hypot(x, z)
            if d > HEART_R - 0.5:
                continue
            # 丸天井: 端ほど低い
            top = HEART_TOP - int(round((d / HEART_R) ** 2 * 18))
            for y in range(HEART_BOTTOM + 1, top):
                lay.carve(x, y, z, 'heart')
    for x in range(-4, 5):
        for z in range(-4, 5):
            if math.hypot(x, z) <= 4.5:
                lay.put(x, HEART_BOTTOM + 1, z, 'sculk_catalyst' if (x, z) == (0, 0) else 'reinforced_deepslate')
                lay.air.pop((x, HEART_BOTTOM + 1, z), None)
    for i in range(8):
        a = i * math.pi / 4
        lx, lz = int(round(math.cos(a) * 18)), int(round(math.sin(a) * 18))
        lay.put(lx, HEART_BOTTOM + 1, lz, 'polished_deepslate')
        lay.put(lx, HEART_BOTTOM + 2, lz, 'polished_deepslate_wall')
        v.mark('beacon_%d' % i, lx, HEART_BOTTOM + 3, lz)
    v.mark('heart', 0, HEART_BOTTOM + 2, 0)
    # 控えの間 (南。半径の外) と廊下
    lay.layer_of['heart_ante'] = 'heart'
    lay.allow('heart', 'heart_ante')
    fy = HEART_BOTTOM + 1
    lay.box(-5, fy, HEART_R + 6, 5, fy + 5, HEART_R + 16, 'heart_ante')
    lay.box(-1, fy, HEART_R - 2, 1, fy + 3, HEART_R + 5, 'heart_ante')
    for x in range(-5, 6):
        for z in range(HEART_R + 6, HEART_R + 17):
            v.set(x, fy - 1, z, 'polished_deepslate' if (x + z) % 2 else 'deepslate_tiles')
    for z in range(HEART_R - 2, HEART_R + 6):
        for x in (-1, 0, 1):
            v.set(x, fy - 1, z, 'deepslate_tiles')
    lay.put(0, fy, HEART_R + 12, 'soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    lay.put(-4, fy + 5, HEART_R + 8, 'soul_lantern[hanging=true,waterlogged=false]')
    lay.put(4, fy + 5, HEART_R + 14, 'soul_lantern[hanging=true,waterlogged=false]')
    v.mark('rest_5', 0, fy, HEART_R + 10)
    v.mark('heart_in', 0, fy, HEART_R + 14)
    # 上りの鎖 (灰の野営へ戻る)
    lay.put(3, fy, HEART_R + 15, 'lodestone')
    for yy in range(fy + 1, fy + 6):
        lay.put(3, yy, HEART_R + 15, 'chain[axis=y,waterlogged=false]')
    v.mark('ascend_heart', 3, fy, HEART_R + 15)
    # 間の床
    for x in range(-HEART_R, HEART_R + 1):
        for z in range(-HEART_R, HEART_R + 1):
            if math.hypot(x, z) <= HEART_R - 0.5 and (x, HEART_BOTTOM, z) not in v.b:
                v.set(x, HEART_BOTTOM, z, rng.choice(HEART_WALL))


def layer_module(name):
    f = os.path.join(HERE, 'layer_%s.py' % name)
    if not os.path.exists(f):
        return None
    spec = importlib.util.spec_from_file_location('layer_%s' % name, f)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def main():
    v = vox.Vox(seed=5)
    lay = Layout(v)
    ctx = Ctx(v, lay)
    common = layer_module('common')
    for n, layer in enumerate(ORDER, start=1):
        build_camp(ctx, layer, n)
        mod = layer_module(layer)
        routes = []
        for i, spec in enumerate(mod.ROUTES, start=1):
            routes.append(build_route(ctx, layer, i, spec))
        mod.build(ctx, routes)
        if common is not None:
            common.build(ctx, layer, routes)
        # 上りの鎖 (骨は地上へ: 印 exit。ほかは前の層の野営へ: 印 ascend_<層>)。着く所 (arrive_<層>) は野営の南
        Y = CAMP_Y[layer]
        lay.put(-3, Y, 6, 'lodestone')
        for yy in range(Y + 1, Y + CAMP_H):
            lay.put(-3, yy, 6, 'chain[axis=y,waterlogged=false]')
        del_air(lay, -3, Y, 6, Y + CAMP_H - 1)
        v.mark('exit' if layer == 'bone' else 'ascend_%s' % layer, -3, Y, 6)
        v.mark('arrive_%s' % layer, 0, Y, 5)
        if layer == 'bone':
            v.mark('arrival', 0, Y, 5)
    build_heart(ctx)
    lay.check_neighbors()
    lay.finish()
    support_falling(v)
    ext = 0
    for (x, y, z) in v.b:
        ext = max(ext, abs(x), abs(z))
    meta = {'name': 'abyss_layers', 'note': 'docs/design/36 の 2・8 章 (層ごとに探す場所)。生成は tools/abyss/gen_abyss_layers.py',
            'top': max(y for (_, y, _) in v.b), 'bottom': HEART_BOTTOM, 'rect': [-ext, -ext, ext, ext]}
    data = v.runs(meta, keep_air=True)
    vox.save(OUT, data)
    print('wrote', os.path.relpath(OUT, ROOT), len(v.b), 'blocks', len(data['palette']), 'states', len(v.marks), 'marks')
    return data


FALLING = ('gravel', 'sand', 'red_sand', 'concrete_powder', 'suspicious_gravel', 'suspicious_sand')


def support_falling(v):
    for (x, y, z), st in list(v.b.items()):
        name = st.split('[')[0]
        if not any(name == f or name.endswith('_' + f) for f in FALLING):
            continue
        below = v.get(x, y - 1, z)
        if below is None or below in ('air', 'cave_air'):
            v.set(x, y - 1, z, 'tuff')


if __name__ == '__main__':
    main()
