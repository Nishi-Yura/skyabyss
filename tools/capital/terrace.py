"""町の段 (手本の「地面も形を持つ」): 平らに建て終えた町の一部を、柱ごと持ち上げて上の町にする。

- 段の境は建物を切らない。上の町の柱は建てる前に決め (PlanTown.upper)、区画・施設は境をまたがない (rect_free)。
- 段の縁は石垣 (土地の石。下ほど荒い石、ところどころ苔)。道でない縁の上には低い胸壁。
- 道が段を越える所は、低い側の道に階段を刻む (段の高さ - 1 マスぶん、上へ向いた階段)。
- 印 (marks)・張りぼての戸・煙突の位置も一緒に持ち上げる。town.h[(x, z)] = その柱の上げ幅。
"""
from collections import deque

from vox import DX, OPP

N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
N8 = N4 + ((1, 1), (1, -1), (-1, 1), (-1, -1))


def _units(v, cols):
    """y >= 1 にブロックのある柱を、8 方のつながりで一かたまりに"""
    up = {}
    for (x, y, z), s in v.b.items():
        if y >= 1 and s != 'air' and (x, z) in cols:
            up[(x, z)] = True
    unit = {}
    n = 0
    for c in up:
        if c in unit:
            continue
        n += 1
        dq = deque([c])
        unit[c] = n
        while dq:
            a = dq.popleft()
            for (dx, dz) in N8:
                b = (a[0] + dx, a[1] + dz)
                if b in up and b not in unit:
                    unit[b] = n
                    dq.append(b)
    return unit


def lift(town, R, dy=3):
    """R = 上の町の柱 (建てる前に決め、区画は境をまたがない)。返り値 = (上げた柱, 階段のマス)"""
    v, occ, st = town.v, town.occ, town.st
    h = {c: dy for c in R}
    # 道が段を越える所: 低い側の道に階段 (R からの距離 d = 1 … dy、d の段の上面は y = dy - d)
    road = lambda c: occ.get(c) in ('road', 'plaza') and c not in R
    dist = {}
    dq = deque()
    for c in R:
        if occ.get(c) in ('road', 'plaza'):
            for (dx, dz) in N4:
                b = (c[0] + dx, c[1] + dz)
                if road(b) and b not in dist:
                    dist[b] = 1
                    dq.append(b)
    while dq:
        a = dq.popleft()
        if dist[a] >= dy:
            continue
        for (dx, dz) in N4:
            b = (a[0] + dx, a[1] + dz)
            if road(b) and b not in dist:
                dist[b] = dist[a] + 1
                dq.append(b)
    ramp = {}
    for c, d in dist.items():
        up_dir = None
        for f, (dx, dz) in DX.items():
            b = (c[0] + dx, c[1] + dz)
            if (b in R and occ.get(b) in ('road', 'plaza')) or dist.get(b, 99) < d:
                up_dir = f
                break
        h[c] = dy - d + 1
        ramp[c] = up_dir
    # 持ち上げ (柱ごとに同じ幅なのでぶつからない)
    nb = {}
    for (x, y, z), s in v.b.items():
        nb[(x, y + h.get((x, z), 0), z)] = s
    v.b = nb
    # 下を埋める: 縁は石垣、内は土
    mix = _wall_mix(st)
    for c, k in h.items():
        x, z = c
        edge = any(h.get((x + dx, z + dz), 0) < k and occ.get((x + dx, z + dz)) != 'wall' for (dx, dz) in N4)
        for y in range(-1, k - 1):
            v.set(x, y, z, _pick(mix, x, y, z, k) if edge else 'dirt')
        if v.b.get((x, k - 1, z)) in (None, 'air'):
            v.set(x, k - 1, z, 'grass_block[snowy=false]' if occ.get(c) not in ('road', 'plaza') else st.pave[0][0])
        top = v.b.get((x, k - 1, z)) or ''
        if edge and not ramp.get(c) and occ.get(c) not in ('road', 'plaza') and any(t_ in top for t_ in ('grass', 'dirt', 'podzol', 'moss')):
            # 縁の上も石 (土の横腹を見せない)
            v.set(x, k - 1, z, st.base.full)
        if edge:
            # 石垣の面に垂れる蔦
            for f, (dx, dz) in DX.items():
                n_ = (x + dx, z + dz)
                if h.get(n_, 0) < k and occ.get(n_) not in ('road', 'plaza', 'wall') and _h(x, z, f) < 0.22:
                    for yy in range(k - 1, max(-1, k - 4), -1):
                        if v.b.get((n_[0], yy, n_[1])) not in (None, 'air'):
                            break
                        v.set(n_[0], yy, n_[1], 'vine[%s]' % ','.join('%s=%s' % (g, 'true' if g == OPP[f] else 'false') for g in ('east', 'north', 'south', 'up', 'west')))
        if ramp.get(c):
            v.set(x, k - 1, z, '%s_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]' % (_stair_base(st), ramp[c]))
            for y in range(k, k + 3):
                if (v.b.get((x, y, z)) or '').split('[')[0] in ('short_grass', 'fern') or 'flower' in (v.b.get((x, y, z)) or ''):
                    v.set(x, y, z, 'air')
        # 胸壁: 道でない縁で、上が空いている所 (階段の脇はあける)
        if edge and k == dy and occ.get(c) not in ('road', 'plaza') and v.b.get((x, k, z)) in (None, 'air')                 and not any((x + dx, z + dz) in ramp for (dx, dz) in N8):
            v.set(x, k, z, st.base.wall())
    for m, p in v.marks.items():
        p[1] += h.get((p[0], p[2]), 0)
    v.fake_doors = [(x, y + h.get((x, z), 0), z, f) for (x, y, z, f) in v.fake_doors]
    if hasattr(v, 'chimneys'):
        v.chimneys = [(x, y + h.get((x, z), 0), z) for (x, y, z) in v.chimneys]
    town.h = h
    return len(R), len(ramp)


def _h(x, z, f):
    from dress import _hash
    return _hash(x, len(f), z, 41)


def _drop_islands(R, n):
    seen = set()
    out = set()
    for c in R:
        if c in seen:
            continue
        comp = [c]
        seen.add(c)
        dq = deque([c])
        while dq:
            a = dq.popleft()
            for (dx, dz) in N4:
                b = (a[0] + dx, a[1] + dz)
                if b in R and b not in seen:
                    seen.add(b)
                    comp.append(b)
                    dq.append(b)
        if len(comp) >= n:
            out.update(comp)
    return out


def _stair_base(st):
    n = st.pave[0][0].split('[')[0]
    for a, b in (('stone_bricks', 'stone_brick'), ('polished_tuff', 'polished_tuff'), ('tuff_bricks', 'tuff_brick'),
                 ('smooth_sandstone', 'smooth_sandstone'), ('polished_blackstone_bricks', 'polished_blackstone_brick')):
        if n == a:
            return b
    return 'stone_brick'


def _wall_mix(st):
    """石垣: 下の段は荒い石、上は土地の石の切り石"""
    rough = list(st.base2) if isinstance(st.base2, list) else [st.base2]
    cut = [st.base.full, st.wall_main] + list(st.wall_mix2)
    return rough, cut


def _pick(mix, x, y, z, k):
    from dress import _hash, noise
    rough, cut = mix
    use = rough if (y < 0 or noise(x, y, z, 3.0, 31) > 0.66) else cut
    return use[int(_hash(x, y, z, 32) * len(use))]
