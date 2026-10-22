# -*- coding: utf-8 -*-
"""深淵の層を「箱の部屋」から自然の洞にする (docs/design/36 の 8 章。2026-10-23 の見直し)。gen_abyss_layers.py が呼ぶ。

expand(ctx)   囲いと床を付ける前に、空気を外へ広げる (元の空気は一つも消さない。道・仕掛け・印の所はそのまま)
  - 部屋 (野営・道の部屋・印の部屋・主の間): 天井をノイズで丸天井に持ち上げ、壁を外へ削って窪み・張り出しにする
    広げた床は元の床と同じ高さか一段上 (主の間の灰は一段ずつの段の客席)。宙に浮いた棚は作らない (立てる所は床だけ)
  - 守り: 広げたマスの 2 マス以内に、ほかの区切り (廊下・仕掛けの部屋・近道) の空気があってはいけない
    (壁はいつも 2 マス以上残る。戸・封・格子・脆い床・雷の回廊・肋の橋の回り道はできない)。
    壁に埋めた物 (刻み・門の穴・支え木) の隣も削らない。吊りの灯りは天井が上がったら鎖を足す
  - 脆い床の間・井戸・雷の回廊・裂け目は横に広げない (床の仕掛けを回り込めないように。天井だけ)
  - 層ごとの見晴らし (vista): 門の奥の下りの廊下を、深い裂け目に架かる岩の橋にする (下は次の層の色の灯り、上は闇)
decorate(ctx) 囲いと床を付けた後の飾り: 層の色 (骨の肋・塩の結晶と塩水の浅い溜まり・雷の銅と焦げたガラス・灰の玄武岩の柱と燠)、
  印の部屋の祭壇の台と形 (肋・晶洞・避雷の柱・玄武岩)、主の間の形 (肋の籠・晶洞・銅の尖塔・玄武岩の円形劇場)、野営の荷と寝床
"""
from __future__ import annotations

import math
import zlib

NO_SIDE = {('salt', 'brittle'), ('salt', 'well'), ('storm', 'hall'), ('bone', 'chasm')}
NO_DOME = {('salt', 'well')}
GUARD = 2


def _lantern(st):
    return 'lantern' in st and 'hanging=true' in st


class Grow:
    def __init__(self, ctx):
        self.ctx = ctx
        self.lay = ctx.lay
        self.v = ctx.v
        lay = self.lay
        # 壁に埋めた物 (空気でない置いた物。吊りの灯りは除く) の回りは削らない
        self.blocked = set()
        for (x, y, z), st in lay.fixed.items():
            if (x, y, z) in lay.air or _lantern(st):
                continue
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        self.blocked.add((x + dx, y + dy, z + dz))
        self.cells = {}
        for p, s in lay.seg.items():
            self.cells.setdefault(s, []).append(p)

    def can(self, q, group):
        lay = self.lay
        if q in lay.air or q in lay.fixed or q in self.blocked or q in self.v.b:
            return False
        x, y, z = q
        air, seg = lay.air, lay.seg
        for dx in range(-GUARD, GUARD + 1):
            for dy in range(-GUARD, GUARD + 1):
                for dz in range(-GUARD, GUARD + 1):
                    n = (x + dx, y + dy, z + dz)
                    if n in air and seg.get(n) not in group:
                        return False
        return True

    def add(self, q, sg, owner):
        self.lay.air[q] = owner
        self.lay.seg[q] = sg
        self.lay.organic.add(q)

    # ------------------------------------------------------------ 部屋
    def room(self, sg, meta):
        ctx, lay = self.ctx, self.lay
        cells = self.cells.get(sg)
        if not cells:
            return
        owner = lay.air[cells[0]]
        L = meta['layer']
        kind = meta['kind']
        key = (L, meta.get('name'))
        cols = {}
        for (x, y, z) in cells:
            lo, hi = cols.get((x, z), (y, y))
            cols[(x, z)] = (min(lo, y), max(hi, y))
        f0 = meta['floor']
        group = {sg}
        seed = zlib.crc32(sg.encode()) % 997
        noise = ctx.noise
        # 縁からの距離 (内側)
        edge = {}
        frontier = []
        for (x, z) in cols:
            if any((x + dx, z + dz) not in cols for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                edge[(x, z)] = 1
                frontier.append((x, z))
        while frontier:
            nxt = []
            for (x, z) in frontier:
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    c = (x + dx, z + dz)
                    if c in cols and c not in edge:
                        edge[c] = edge[(x, z)] + 1
                        nxt.append(c)
            frontier = nxt
        slope, emax, amp = {'room': (0.7, 5, 3), 'mark': (0.9, 6, 3), 'camp': (0.8, 8, 3), 'arena': (1.5, 14, 4)}[kind]
        top = {}
        if key not in NO_DOME:
            for (x, z), (lo, hi) in cols.items():
                e = edge[(x, z)]
                extra = int(min(e - 1, emax) * slope + noise(x, 0, z, 5.0, seed) * amp)
                t = hi
                y = hi + 1
                while y <= hi + extra:
                    q = (x, y, z)
                    if q in lay.fixed and _lantern(lay.fixed[q]):
                        y += 1
                        continue
                    if not self.can(q, group):
                        break
                    self.add(q, sg, owner)
                    t = y
                    y += 1
                top[(x, z)] = t
                # 雷の層: 天井の風の穴 (細い縦穴)
                if L == 'storm' and e >= 3 and ctx.h3(x, 7, z, seed) < 0.025:
                    for yy in range(t + 1, t + 4 + int(ctx.h3(x, 8, z, seed) * 4)):
                        if not self.can((x, yy, z), group):
                            break
                        self.add((x, yy, z), sg, owner)
        else:
            for c, (lo, hi) in cols.items():
                top[c] = hi
        if key in NO_SIDE:
            return
        depth = meta.get('depth', 3)
        terrace = (kind == 'arena' and L == 'ash')
        if terrace:
            depth = 5
        dist = {c: 0 for c in cols}
        frontier = list(cols)
        for d in range(1, depth + 1):
            nxt = []
            for (x, z) in frontier:
                pt = top.get((x, z))
                if pt is None:
                    continue
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    c = (x + dx, z + dz)
                    if c in dist:
                        continue
                    dist[c] = d
                    cx, cz = c
                    if terrace:
                        reach = depth
                        f = f0 + min(d, 4)
                    else:
                        reach = depth * min(1.0, max(0.0, (noise(cx, 3, cz, 6.0, seed) - 0.28) / 0.5))
                        f = f0 + (1 if d >= 2 and noise(cx, 1, cz, 3.0, seed) > 0.6 else 0)
                    if d > reach + 0.5:
                        continue
                    t = pt - (1 if noise(cx, 2, cz, 2.5, seed) > 0.55 else 0) - (1 if d >= 3 else 0)
                    if terrace:
                        t = max(t, f + 6)
                    if t - f < 2:
                        continue
                    if not self.can((cx, f, cz), group):
                        continue
                    ct = f - 1
                    for y in range(f, t + 1):
                        if not self.can((cx, y, cz), group):
                            break
                        self.add((cx, y, cz), sg, owner)
                        ct = y
                    if ct - f < 1:
                        # 低すぎる穴は埋め戻す
                        for y in range(f, ct + 1):
                            lay.air.pop((cx, y, cz), None)
                            lay.seg.pop((cx, y, cz), None)
                            lay.organic.discard((cx, y, cz))
                        continue
                    top[c] = ct
                    nxt.append(c)
            frontier = nxt

    # ------------------------------------------------------------ 見晴らし (門の奥の裂け目と岩の橋)
    def vista(self, layer):
        ctx, lay = self.ctx, self.lay
        meta = lay.segs['gatecorr_%s' % layer]
        gseg = meta['seg']
        cells = self.cells[gseg]
        owner = lay.air[cells[0]]
        fy = {}
        for (x, y, z) in cells:
            if x == 0:
                fy[z] = min(fy.get(z, y), y)
        Y = ctx.CAMP_Y[layer]
        sg = 'vista_%s' % layer
        group = {sg, gseg}
        zc, rz, rx = -23.0, 7.5, 11.5
        seed = 300 + ctx.ORDER.index(layer)
        noise = ctx.noise
        rail = {'bone': 'diorite_wall', 'salt': 'diorite_wall', 'storm': 'cobbled_deepslate_wall',
                'ash': 'polished_blackstone_wall'}[layer]
        for x in range(-14, 15):
            for z in range(-32, -13):
                e = (x / rx) ** 2 + ((z - zc) / rz) ** 2 + (noise(x, 0, z, 4.0, seed) - 0.5) * 0.5
                if e >= 1.0:
                    continue
                f = fy.get(z)
                if f is None:
                    continue
                yb = Y - 26 + int(noise(x, 1, z, 5.0, seed) * 5) + int(e * 10)
                yt = Y + 11 - int(e * 7) + int(noise(x, 2, z, 4.0, seed) * 4)
                for y in range(yb, yt + 1):
                    if abs(x) <= 2 and f - 2 <= y <= f - 1:
                        continue      # 橋 (二枚の厚みの岩)
                    if abs(x) == 2 and y == f:
                        continue      # 手すり
                    q = (x, y, z)
                    if not self.can(q, group):
                        continue
                    self.add(q, sg, owner)
        # 手すり: 裂け目に面している所だけ
        for z, f in fy.items():
            for x in (-2, 2):
                q = (x, f, z)
                if q in lay.air:
                    continue
                if any((x + dx, f + dy, z) in lay.organic for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                    lay.put(x, f, z, rail + '[east=none,north=low,south=low,up=true,waterlogged=false,west=none]')
        lay.segs[sg] = dict(layer=layer, kind='vista', floor=Y - 26, bridge=fy)


def expand(ctx):
    g = Grow(ctx)
    lay = ctx.lay
    for layer in ctx.ORDER:
        g.vista(layer)
    for sg, meta in list(lay.segs.items()):
        if meta['kind'] in ('camp', 'arena', 'room', 'mark'):
            g.room(sg, meta)
    print('organic: +%d air cells' % len(lay.organic))


# ================================================================ 飾り
class Deco:
    def __init__(self, ctx):
        self.ctx = ctx
        self.lay = ctx.lay
        self.v = ctx.v
        self.b = ctx.v.b
        self.keep = set()
        for (x, y, z) in self.v.marks.values() if isinstance(self.v.marks, dict) else []:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        self.keep.add((x + dx, y + dy, z + dz))
        # 床からの高さ (空気のマスの、その列の一番下の空気)
        self.floor = {}
        for p in sorted((p for p in self.lay.air if self.b.get(p) == 'air'), key=lambda p: p[1]):
            x, y, z = p
            self.floor[p] = self.floor.get((x, y - 1, z), y)
        self.layer_cells = {}
        for p in self.floor:
            L = self.lay.layer_of.get(self.lay.air[p], 'heart')
            self.layer_cells.setdefault(L, []).append(p)

    def air(self, p):
        return self.b.get(p) == 'air'

    def shell(self, p):
        st = self.b.get(p)
        return st is not None and st != 'air' and p not in self.lay.fixed and p not in self.lay.air

    def free(self, p, high=4):
        """飾りを置いてよい空気 (広げた所か、床から high 以上の所。印の回り・廊下は除く)"""
        if not self.air(p) or p in self.keep or p in self.lay.fixed:
            return False
        if p in self.lay.organic:
            return True
        fl = self.floor.get(p)
        sg = self.lay.seg.get(p, '')
        return fl is not None and p[1] - fl >= high and not sg.startswith('corr')

    def put(self, p, st):
        self.b[p] = st

    def lichen(self, p):
        faces = {}
        x, y, z = p
        for nm, (dx, dy, dz) in (('north', (0, 0, -1)), ('south', (0, 0, 1)), ('east', (1, 0, 0)), ('west', (-1, 0, 0)),
                                 ('up', (0, 1, 0)), ('down', (0, -1, 0))):
            faces[nm] = self.shell((x + dx, y + dy, z + dz))
        if not any(faces[k] for k in ('north', 'south', 'east', 'west', 'up')):
            return None
        if faces['down'] and not any(faces[k] for k in ('north', 'south', 'east', 'west', 'up')):
            return None
        return 'glow_lichen[%s,waterlogged=false]' % ','.join('%s=%s' % (k, 'true' if faces[k] else 'false')
                                                              for k in ('down', 'east', 'north', 'south', 'up', 'west'))

    def facing_off_wall(self, p):
        x, y, z = p
        for nm, (dx, dy, dz) in (('up', (0, -1, 0)), ('down', (0, 1, 0)), ('south', (0, 0, -1)), ('north', (0, 0, 1)),
                                 ('east', (-1, 0, 0)), ('west', (1, 0, 0))):
            if self.shell((x + dx, y + dy, z + dz)):
                return nm
        return None


def decorate(ctx):
    d = Deco(ctx)
    lay, b = d.lay, d.b
    h3, noise = ctx.h3, ctx.noise
    # 吊りの灯り: 上が空いたら鎖で天井から吊る (探掘隊の古い灯り)
    for (x, y, z), st in list(lay.fixed.items()):
        if _lantern(st) and b.get((x, y, z)) == st:
            yy = y + 1
            while b.get((x, yy, z)) == 'air' and yy - y < 48:
                b[(x, yy, z)] = 'chain[axis=y,waterlogged=false]'
                yy += 1
    # 支え木: 天井まで伸ばす
    for (x, y, z), st in list(lay.fixed.items()):
        if st.startswith('stripped_spruce_log') and b.get((x, y + 1, z)) == 'air':
            yy = y + 1
            while b.get((x, yy, z)) == 'air' and yy - y < 20:
                b[(x, yy, z)] = st
                yy += 1
    for L in ctx.ORDER:
        cells = d.layer_cells.get(L, [])
        _surfaces(ctx, d, L, cells)
        _vista(ctx, d, L)
        for sg, meta in lay.segs.items():
            if meta.get('layer') != L:
                continue
            if meta['kind'] == 'mark':
                _mark_room(ctx, d, L, meta)
            elif meta['kind'] == 'arena':
                _arena(ctx, d, L, sg, meta)
            elif meta['kind'] == 'camp':
                _camp(ctx, d, L, sg)


def _surfaces(ctx, d, L, cells):
    b, h3, noise = d.b, ctx.h3, ctx.noise
    lich = {'bone': 0.06, 'salt': 0.035, 'storm': 0.02, 'ash': 0.008}[L]
    for p in cells:
        if not d.air(p) or p in d.keep or p in d.lay.fixed:
            continue
        x, y, z = p
        fl = d.floor[p]
        r = h3(x, y, z, 41)
        clump = noise(x, y, z, 5.0, 42)
        above = (x, y + 1, z)
        # 天井から垂れる物
        if d.shell(above) and d.free(p) and y - fl >= 3:
            if L in ('bone', 'salt') and r < 0.07:
                b[p] = 'pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]'
                continue
            if L == 'salt' and r < 0.1:
                b[p] = 'amethyst_cluster[facing=down,waterlogged=false]'
                continue
            if L == 'storm' and r < 0.03:
                b[p] = 'lightning_rod[facing=down,powered=false,waterlogged=false]'
                continue
            if L == 'ash' and r < 0.05:
                b[p] = 'polished_basalt[axis=y]'
                continue
        # 床の物 (広げた所だけ)
        if p in d.lay.organic and y == fl and d.shell((x, y - 1, z)):
            if L == 'bone':
                if r < 0.04:
                    b[p] = 'pointed_dripstone[thickness=tip,vertical_direction=up,waterlogged=false]'
                    continue
                if r < 0.07:
                    b[p] = 'bone_block[axis=%s]' % ('x' if h3(x, 1, z, 3) < 0.5 else 'z')
                    continue
                if r < 0.075:
                    b[p] = 'skeleton_skull[rotation=%d]' % int(h3(x, 2, z, 3) * 16)
                    continue
            elif L == 'salt':
                if r < 0.05:
                    b[p] = 'amethyst_cluster[facing=up,waterlogged=false]'
                    continue
                if r < 0.07:
                    b[p] = 'large_amethyst_bud[facing=up,waterlogged=false]'
                    continue
                # 塩水の浅い溜まり (床の一マスを水に。回りが岩のときだけ。泳がない深さ)
                if noise(x, 0, z, 5.0, 43) > 0.62 and all(d.shell(q) for q in ((x + 1, y - 1, z), (x - 1, y - 1, z), (x, y - 1, z + 1),
                                                                                   (x, y - 1, z - 1), (x, y - 2, z))):
                    b[(x, y - 1, z)] = 'water[level=0]'
                    continue
            elif L == 'storm':
                if r < 0.025:
                    b[p] = 'lightning_rod[facing=up,powered=false,waterlogged=false]'
                    continue
                if r < 0.05:
                    b[p] = 'exposed_copper'
                    continue
            elif L == 'ash':
                if r < 0.05:
                    b[p] = 'basalt[axis=y]'
                    continue
                if r < 0.065:
                    b[p] = 'blackstone_slab[type=bottom,waterlogged=false]'
                    continue
        # 光る苔
        if r < lich * (3.0 if clump > 0.58 else 0.25):
            st = d.lichen(p)
            if st:
                b[p] = st
    # 壁の色: 雷は焦げたガラス、塩は結晶の塊、灰は燠
    for p in cells:
        x, y, z = p
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y + 1, z)):
            if not d.shell(q):
                continue
            r = h3(q[0], q[1], q[2], 51)
            behind = (2 * q[0] - x, 2 * q[1] - y, 2 * q[2] - z)
            if L == 'storm' and r < 0.012 and d.shell(behind):
                b[q] = 'tinted_glass'
            elif L == 'salt' and r < 0.02:
                b[q] = 'amethyst_block'
            elif L == 'ash' and r < 0.006 and q[1] > d.floor.get(p, y) + 1:
                b[q] = 'magma_block'


def _arc(d, pts, st):
    for p in pts:
        if p in d.lay.air:
            if d.free(p):
                d.b[p] = st
        elif d.shell(p):
            d.b[p] = st


def _mark_room(ctx, d, L, meta):
    r = meta['route']
    u, w, y, hu, hw, h = meta['room']
    mu = u + hu - 1
    # 祭壇の台 (岩の張り出し): 台座の回りに半石
    for (a, c) in ((mu, w - 1), (mu, w + 1), (mu - 1, w - 1), (mu - 1, w), (mu - 1, w + 1)):
        x, z = r.xz(a, c)
        p = (x, y, z)
        if d.air(p) and p not in d.lay.fixed:
            d.b[p] = ctx.slab_of(L)
    mx, mz = r.xz(mu, w)
    if L == 'bone':
        # 肋の弧 (部屋をまたぐ)
        for a in range(u - hu + 1, u + hu + 1, 3):
            pts = []
            for k in range(0, 41):
                t = math.pi * k / 40
                bw = w + (hw + 1.5) * math.cos(t)
                by = y + 2 + (h + 3) * math.sin(t)
                x, z = r.xz(a, int(round(bw)))
                pts.append((x, int(round(by)), z))
            _arc(d, pts, 'bone_block[axis=y]')
    elif L == 'salt':
        _geode(d, [p for p in d.layer_cells.get(L, []) if abs(p[0] - mx) + abs(p[2] - mz) <= 6 and p[1] >= y], 0.55)
    elif L == 'storm':
        # 祭壇の後ろの壁に銅の柱と避雷の針、焦げたガラス
        bx, bz = r.xz(mu + 2, w)
        for yy in range(y - 1, y + h + 6):
            p = (bx, yy, bz)
            if d.shell(p):
                d.b[p] = 'oxidized_copper' if yy % 3 else 'oxidized_cut_copper'
        for c in (w - 2, w + 2):
            x, z = r.xz(mu + 2, c)
            for yy in range(y + 1, y + h):
                if d.shell((x, yy, z)) and ctx.h3(x, yy, z, 61) < 0.4:
                    d.b[(x, yy, z)] = 'tinted_glass'
    elif L == 'ash':
        for c in (w - 3, w + 3):
            x, z = r.xz(mu, c)
            if all(d.air((x, yy, z)) and (x, yy, z) not in d.keep for yy in range(y, y + 3)):
                yy = y
                while d.air((x, yy, z)):
                    d.b[(x, yy, z)] = 'basalt[axis=y]'
                    yy += 1
        bx, bz = r.xz(mu + 2, w)
        for yy in range(y + 1, y + h + 2):
            if d.shell((bx, yy, bz)):
                d.b[(bx, yy, bz)] = 'magma_block'


def _geode(d, cells, amount):
    for p in cells:
        x, y, z = p
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y + 1, z)):
            if d.shell(q):
                d.b[q] = 'amethyst_block' if d.ctx.noise(q[0], q[1], q[2], 2.0, 71) < amount else 'calcite'
    for p in cells:
        if d.free(p, high=3) and d.ctx.h3(p[0], p[1], p[2], 72) < 0.12:
            f = d.facing_off_wall(p)
            if f:
                d.b[p] = 'amethyst_cluster[facing=%s,waterlogged=false]' % f


def _arena(ctx, d, L, sg, meta):
    ax, az = meta['center']
    ay = meta['floor']
    cells = [p for p in d.layer_cells.get(L, []) if d.lay.seg.get(p) == sg]
    R = ctx.ARENA_R
    if L == 'bone':
        # 肋の籠: 間を東西にまたぐ大きな肋と、天井の背骨
        for off in range(-12, 13, 4):
            half = math.sqrt(max(0.0, (R + 2) ** 2 - off ** 2))
            pts = []
            for k in range(0, 61):
                t = math.pi * k / 60
                bx = ax + half * math.cos(t)
                by = ay + 1 + (R + 6) * math.sin(t)
                pts.append((int(round(bx)), int(round(by)), az + off))
            _arc(d, pts, 'bone_block[axis=y]')
        for zz in range(az - R, az + R + 1):
            col = [p for p in cells if p[0] == ax and p[2] == zz]
            if col:
                t = max(c[1] for c in col)
                if (zz - az) % 2 == 0:
                    _arc(d, [(ax, t, zz), (ax - 1, t, zz), (ax + 1, t, zz)], 'bone_block[axis=z]')
    elif L == 'salt':
        _geode(d, cells, 0.6)
    elif L == 'storm':
        # 縁の銅の尖塔 (避雷の針つき) と、焦げたガラスの筋
        for k in range(8):
            ang = k * math.pi / 4 + math.pi / 8
            x, z = ax + int(round(math.cos(ang) * (R + 2))), az + int(round(math.sin(ang) * (R + 2)))
            col = sorted((p for p in cells if p[0] == x and p[2] == z), key=lambda p: p[1])
            if not col or col[0] in d.keep:
                continue
            hgt = 7 + int(ctx.h3(x, 0, z, 81) * 6)
            last = None
            for p in col[:hgt]:
                d.b[p] = 'oxidized_copper' if (p[1] - col[0][1]) % 4 else 'oxidized_cut_copper'
                last = p
            if last and d.air((last[0], last[1] + 1, last[2])):
                d.b[(last[0], last[1] + 1, last[2])] = 'lightning_rod[facing=up,powered=false,waterlogged=false]'
        for p in cells:
            x, y, z = p
            q = (x, y + 1, z)
            if d.shell(q) and ctx.noise(x, 0, z, 3.0, 82) > 0.7:
                d.b[q] = 'tinted_glass' if d.shell((x, y + 2, z)) else d.b[q]
    elif L == 'ash':
        # 円形劇場の一番外に玄武岩の柱。段は expand で作った
        for p in cells:
            x, y, z = p
            dd = math.hypot(x - ax, z - az)
            if dd > R + 4.2 and d.floor.get(p) == y and ctx.noise(x, 0, z, 2.0, 91) > 0.5 and p not in d.keep:
                yy = y
                while d.air((x, yy, z)):
                    d.b[(x, yy, z)] = 'basalt[axis=y]'
                    yy += 1


def _vista(ctx, d, L):
    meta = d.lay.segs.get('vista_%s' % L)
    if not meta:
        return
    sg = 'vista_%s' % L
    cells = [p for p in d.layer_cells.get(L, []) if d.lay.seg.get(p) == sg]
    if not cells:
        return
    h3, noise = ctx.h3, ctx.noise
    bridge = meta['bridge']
    low = min(p[1] for p in cells)
    # 底の灯り (次の層の色): 骨 → 塩の白い光、塩 → 雷の緑の光、雷 → 灰の燠、灰 → 心臓の紫
    glow = {'bone': ['pearlescent_froglight', 'soul_soil'], 'salt': ['verdant_froglight', 'calcite'],
            'storm': ['magma_block', 'blackstone'], 'ash': ['crying_obsidian', 'sculk']}[L]
    for p in cells:
        x, y, z = p
        if d.floor.get(p) != y or y > low + 12:
            continue
        q = (x, y - 1, z)
        if d.shell(q):
            n = noise(x, 0, z, 3.0, 101)
            if n > 0.62:
                d.b[q] = glow[0]
            elif n > 0.45:
                d.b[q] = glow[1]
    if L == 'bone':
        # 裂け目をまたぐ肋 (橋の上、頭より高く)
        for zz in range(-29, -16, 3):
            f = bridge.get(zz)
            if f is None:
                continue
            pts = []
            for k in range(0, 61):
                t = math.pi * k / 60
                pts.append((int(round(12 * math.cos(t))), int(round(f - 14 + (f + 20 - (f - 14)) * math.sin(t) * 0.9)), zz))
            _arc(d, pts, 'bone_block[axis=y]')
    elif L == 'salt':
        _geode(d, [p for p in cells if noise(p[0], p[1], p[2], 6.0, 102) > 0.6], 0.6)
    elif L in ('storm', 'ash'):
        # 底から立つ柱 (雷は銅、灰は玄武岩)。橋の近くは避ける
        for p in cells:
            x, y, z = p
            if abs(x) < 5 or d.floor.get(p) != y or y > low + 6 or noise(x, 0, z, 2.2, 103) < 0.7:
                continue
            hgt = 6 + int(h3(x, 0, z, 104) * 14)
            yy = y
            while d.air((x, yy, z)) and yy - y < hgt:
                d.b[(x, yy, z)] = 'basalt[axis=y]' if L == 'ash' else ('oxidized_copper' if (yy - y) % 5 else 'weathered_cut_copper')
                yy += 1
            if L == 'storm' and d.air((x, yy, z)):
                d.b[(x, yy, z)] = 'lightning_rod[facing=up,powered=false,waterlogged=false]'


def _camp(ctx, d, L, sg):
    """野営の窪みに探掘隊の荷 (木箱・樽)・寝床・古い灯り"""
    h3 = ctx.h3
    cells = [p for p in d.layer_cells.get(L, []) if d.lay.seg.get(p) == sg and p in d.lay.organic]
    for p in cells:
        x, y, z = p
        if d.floor.get(p) != y or p in d.keep or not d.air(p):
            continue
        r = h3(x, y, z, 111)
        if r < 0.08:
            d.b[p] = 'barrel[facing=up,open=false]'
            if r < 0.03 and d.air((x, y + 1, z)):
                d.b[(x, y + 1, z)] = 'lantern[hanging=false,waterlogged=false]'
        elif r < 0.14:
            d.b[p] = 'spruce_planks'
            if d.air((x, y + 1, z)) and r < 0.11:
                d.b[(x, y + 1, z)] = 'spruce_slab[type=bottom,waterlogged=false]'
        elif r < 0.2:
            d.b[p] = 'brown_carpet' if r < 0.17 else 'white_carpet'
