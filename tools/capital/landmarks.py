"""町の目印 (手本の「一つの強い輪郭」)。ラフ 3 案 (鯨見の町で):

  A 鯨の肋の塔   : 見張りの塔を、巨きな鯨の肋骨が両側から抱く。背骨が頂を渡り、肋の先に灯り
  B 岩の上の見張り : 彫った岩の柱 (層・苔・張り出し) の上に、張り出した木組みの見張り小屋と鐘。岩を巻く外の階段
  C 鐘の大樹     : ねじれた大樹。根の張り出し、幹を巻く二つの床、枝から吊った鐘と鎖の灯り

python tools/capital/landmarks.py rough  → 設計図 lm_rough_<a|b|c>.json と上から見た図
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vox  # noqa: E402
from vox import Vox, tower, DX, OPP  # noqa: E402
from dress import noise, _hash  # noqa: E402
import town as T  # noqa: E402


# ---------------------------------------------------------------- 部品
def disk(v, cx, cz, y, r, pick):
    for x in range(int(cx - r) - 1, int(cx + r) + 2):
        for z in range(int(cz - r) - 1, int(cz + r) + 2):
            if (x - cx) ** 2 + (z - cz) ** 2 <= r * r:
                v.set(x, y, z, pick(x, y, z))


def strata(palette, seed=0):
    """層の石: y の帯で地の石を替え、かたまりのノイズで混ぜる"""
    def pick(x, y, z):
        band = (y + int(noise(x, 0, z, 6.0, seed) * 3)) // 3
        base = palette[band % len(palette)]
        if noise(x, y, z, 2.5, seed + 1) > 0.68:
            return palette[(band + 1) % len(palette)]
        return base
    return pick


def rock_pillar(v, cx, cz, r0, h, palette, moss='moss_block', seed=0, lip_every=4):
    """彫った岩の柱: 下ほど太く、ゆがみ、数段おきに張り出す縁。上の面に苔"""
    pick = strata(palette, seed)
    tops = {}
    for y in range(-1, h):
        t = y / max(1, h - 1)
        r = r0 * (1.0 - 0.45 * max(0.0, t) ** 1.3)
        r += (noise(0, y, 0, 3.0, seed + 5) - 0.5) * 1.6
        if lip_every and y % lip_every == lip_every - 1:
            r += 0.9
        ox = (noise(1, y, 0, 7.0, seed + 6) - 0.5) * 2.0
        oz = (noise(0, y, 1, 7.0, seed + 7) - 0.5) * 2.0
        for x in range(int(cx - r - 3), int(cx + r + 4)):
            for z in range(int(cz - r - 3), int(cz + r + 4)):
                d = math.hypot(x - cx - ox, z - cz - oz)
                rr = r + (noise(x, y, z, 2.0, seed + 8) - 0.5) * 1.8
                if d <= rr:
                    v.set(x, y, z, pick(x, y, z))
                    tops[(x, z)] = y
    # 上を向いた面 (上が空) に苔、ときどき草
    for (x, z), y in list(tops.items()):
        for yy in range(y, -1, -1):
            s = v.b.get((x, yy, z))
            if s and s != 'air' and v.b.get((x, yy + 1, z)) in (None, 'air'):
                if noise(x, yy, z, 2.0, seed + 9) > 0.45:
                    v.set(x, yy, z, moss)
                    if _hash(x, yy, z, 3) < 0.25:
                        v.set(x, yy + 1, z, 'short_grass')
                break
    # 張り出しの下から垂れる蔦
    for (x, y, z), s in list(v.b.items()):
        if s == moss:
            continue
        for f, (dx, dz) in DX.items():
            n = (x + dx, y, z + dz)
            if v.b.get(n) in (None, 'air') and v.b.get((n[0], y - 1, n[2])) in (None, 'air') and _hash(x, y, z, len(f)) < 0.035:
                for k in range(0, 1 + int(_hash(x, y, z, 9) * 4)):
                    if v.b.get((n[0], y - k, n[2])) not in (None, 'air'):
                        break
                    v.set(n[0], y - k, n[2], _vine(OPP[f]))
    return max(tops.values()) if tops else 0


def _vine(att):
    return 'vine[%s]' % ','.join('%s=%s' % (g, 'true' if g == att else 'false') for g in ('east', 'north', 'south', 'up', 'west'))


def arc(v, pts, block, thick=1):
    """折れ線 pts (x, y, z) を太さ thick の塊でなぞる"""
    for (a, b) in zip(pts, pts[1:]):
        n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1]), abs(b[2] - a[2])) * 2) + 1
        for i in range(n + 1):
            t = i / n
            x, y, z = (a[k] + (b[k] - a[k]) * t for k in range(3))
            r = thick - 1
            for dx in range(-r, r + 1):
                for dy in range(-r, r + 1):
                    for dz in range(-r, r + 1):
                        if dx * dx + dy * dy + dz * dz <= r * r:
                            v.set(int(round(x)) + dx, int(round(y)) + dy, int(round(z)) + dz, block)


def leaf_blob(v, cx, cy, cz, rx, ry, rz, leaves, seed=0):
    L = '%s[distance=1,persistent=true,waterlogged=false]' % leaves
    for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
        for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
            for z in range(int(cz - rz) - 1, int(cz + rz) + 2):
                d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                if d <= 1.0 - (noise(x, y, z, 2.0, seed) - 0.5) * 0.7 and v.b.get((x, y, z)) in (None, 'air'):
                    v.set(x, y, z, L)


def ring_deck(v, cx, cz, y, r, wood, fence, lantern=True):
    """幹を巻く床 (上の半ブロック) と手すり、下に持ち送り"""
    for x in range(int(cx - r) - 1, int(cx + r) + 2):
        for z in range(int(cz - r) - 1, int(cz + r) + 2):
            d = math.hypot(x - cx, z - cz)
            if d <= r and v.b.get((x, y, z)) in (None, 'air'):
                v.set(x, y, z, '%s_slab[type=top,waterlogged=false]' % wood)
                if d > r - 1.0:
                    v.set(x, y + 1, z, fence)
    if lantern:
        for (a, b) in ((r, 0), (-r, 0), (0, r), (0, -r)):
            x, z = int(round(cx + a)), int(round(cz + b))
            if v.b.get((x, y - 1, z)) in (None, 'air'):
                v.set(x, y - 1, z, 'lantern[hanging=true,waterlogged=false]')


# ---------------------------------------------------------------- A 鯨の肋の塔
def lm_ribs(v, st, cx, cz):
    """低い見張りの塔の上に、鯨の骨 (背骨と 4 対の肋) が覆いかぶさる"""
    tower(v, st, cx, cz, half=3, y0=0, h=13, belfry=True, spire_rise=2, pinnacles=True, sealed=True, door_side='south')
    bone = 'bone_block[axis=y]'
    TOP = 26
    for dz in (-7, -2, 3, 8):
        span = 10.5 - abs(dz - 0.5) * 0.25
        top = TOP - 1 - abs(dz - 0.5) * 0.35
        for s in (1, -1):
            pts = []
            for k in range(0, 17):
                t = k / 16
                ang = t * math.pi * 0.5
                # 下は外へふくらみ、上で背骨へ寄る (肋の弓)
                x = cx + s * span * math.cos(ang) * (1.0 + 0.18 * math.sin(t * math.pi))
                y = -1 + top * math.sin(ang)
                pts.append((x, y, cz + dz - 1.2 * math.sin(t * math.pi)))
            arc(v, pts[:7], bone, thick=2)
            arc(v, pts[6:], bone, thick=1)
            bx, bz = int(round(cx + s * span)), cz + dz
            for (a, b) in ((0, 0), (s, 0), (0, 1), (0, -1), (s, 1)):
                v.set(bx + a, -1, bz + b, 'mossy_cobblestone')
            v.set(bx + s, 0, bz, 'mossy_cobblestone_wall')
            v.set(bx + s, 0, bz + 1, 'moss_carpet')
    # 背骨: 南北に、椎骨の棘と横の突起。北の端は頭骨のように太く
    for z in range(cz - 11, cz + 12):
        v.set(cx, TOP, z, 'bone_block[axis=z]')
        if z % 2 == 0:
            v.set(cx, TOP + 1, z, bone)
            if z % 4 == 0:
                v.set(cx, TOP + 2, z, 'bone_block[axis=y]')
            v.set(cx + 1, TOP, z, 'bone_block[axis=x]')
            v.set(cx - 1, TOP, z, 'bone_block[axis=x]')
    for z in range(cz - 15, cz - 11):
        for x in range(cx - 2, cx + 3):
            for y in range(TOP - 2, TOP + 2):
                if abs(x - cx) + abs(y - TOP + 0.5) <= 3 - (cz - 11 - z) * 0.4:
                    v.set(x, y, z, 'bone_block[axis=z]')
    v.set(cx - 1, TOP, cz - 14, 'black_concrete')
    v.set(cx + 1, TOP, cz - 14, 'black_concrete')
    # 肋の間から吊った灯り
    for dz in (-7, -2, 3, 8):
        for y in range(TOP - 4, TOP):
            v.set(cx, y, cz + dz, 'chain[axis=y,waterlogged=false]')
        v.set(cx, TOP - 5, cz + dz, 'lantern[hanging=true,waterlogged=false]')
    return 12


# ---------------------------------------------------------------- B 岩の上の見張り
def lm_rock(v, st, cx, cz):
    pal = ['stone', 'andesite', 'mossy_cobblestone', 'cobblestone', 'stone', 'tuff']
    top = rock_pillar(v, cx, cz, 7.5, 13, pal, seed=3)
    y0 = top + 1
    wood, dark = st.wood, st.dark
    # 張り出した木組みの小屋 (岩より一回り大きく、持ち送りで支える)
    x0, z0, x1, z1 = cx - 4, cz - 4, cx + 4, cz + 4
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            v.set(x, y0 - 1, z, '%s_planks' % dark)
    for x in range(x0 - 1, x1 + 2, 2):
        for z in (z0 - 1, z1 + 1):
            v.set(x, y0 - 2, z, '%s_stairs[facing=%s,half=top,shape=straight,waterlogged=false]' % (dark, 'south' if z == z0 - 1 else 'north'))
    for z in range(z0 + 1, z1, 2):
        for x in (x0 - 1, x1 + 1):
            v.set(x, y0 - 2, z, '%s_stairs[facing=%s,half=top,shape=straight,waterlogged=false]' % (dark, 'east' if x == x0 - 1 else 'west'))
    for y in range(y0, y0 + 9):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if not edge:
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                beam = corner or (y - y0) % 4 == 0
                if beam:
                    v.set(x, y, z, '%s_log[axis=y]' % dark if corner else '%s_planks' % dark)
                elif (x + z) % 4 == 0 and (y - y0) % 4 in (1, 2):
                    v.set(x, y, z, st.glass)
                else:
                    v.set(x, y, z, 'calcite' if (y - y0) >= 4 else '%s_planks' % wood)
        if y == y0 + 4:
            # 上の階を一つ張り出す
            pass
    # 上の階: 一回り張り出す (持ち送り)
    ya = y0 + 5
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                for y in range(ya, ya + 4):
                    corner = x in (x0 - 1, x1 + 1) and z in (z0 - 1, z1 + 1)
                    if corner or y == ya:
                        v.set(x, y, z, '%s_log[axis=y]' % dark if corner else '%s_planks' % dark)
                    elif (x + z) % 3 == 0 and y in (ya + 1, ya + 2):
                        v.set(x, y, z, st.glass)
                    else:
                        v.set(x, y, z, 'calcite')
    # 尖り屋根と鐘の小塔
    rt = vox.hip_spire(v, st, x0 - 1, z0 - 1, x1 + 1, z1 + 1, ya + 4, mat=st.roof, rise=2, flare=True)
    v.set(cx, ya + 4, cz, 'bell[attachment=ceiling,facing=north,powered=false]')
    # 岩を巻く外の階段 (下から小屋の戸まで、時計回り)
    steps = []
    n = 0
    for y in range(0, y0):
        ang = y * 0.42
        r = 8.6 - 0.45 * (y / max(1, y0)) * 7.5 + 1.0
        x = int(round(cx + r * math.cos(ang)))
        z = int(round(cz + r * math.sin(ang)))
        steps.append((x, y, z))
    for (x, y, z) in steps:
        for (a, b) in ((0, 0), (1, 0), (0, 1)):
            if v.b.get((x + a, y, z + b)) in (None, 'air'):
                v.set(x + a, y, z + b, '%s_slab[type=top,waterlogged=false]' % st.base.stairs('north').split('_stairs')[0].replace('mossy_', '') if False else st.base.slab('top'))
        v.set(x, y + 3, z, 'air')
    # 持ち送りの柱 (小屋の四隅から岩へ斜めに)
    for (x, z) in ((x0 - 1, z0 - 1), (x1 + 1, z0 - 1), (x0 - 1, z1 + 1), (x1 + 1, z1 + 1)):
        for k in range(1, 4):
            xx = x + (1 if x < cx else -1) * (k // 2)
            zz = z + (1 if z < cz else -1) * (k // 2)
            if v.b.get((xx, y0 - 1 - k, zz)) in (None, 'air'):
                v.set(xx, y0 - 1 - k, zz, '%s_log[axis=y]' % dark)
    return 10


# ---------------------------------------------------------------- C 鐘の大樹
def lm_tree(v, st, cx, cz):
    trunk = 'spruce_log' if 'spruce' in st.trunk else st.trunk.split('[')[0]
    wood_bark = trunk.replace('_log', '_wood') if trunk.endswith('_log') else trunk
    # 根の張り出し (8 方へ、地を這って沈む)
    for k in range(7):
        ang = k * 2 * math.pi / 7 + 0.3
        pts = [(cx + math.cos(ang) * 1.5, 2, cz + math.sin(ang) * 1.5), (cx + math.cos(ang) * 4.5, 0.5, cz + math.sin(ang) * 4.5),
               (cx + math.cos(ang + 0.2) * 7, -0.5, cz + math.sin(ang + 0.2) * 7)]
        arc(v, pts, wood_bark + '[axis=y]', thick=2 if k % 2 == 0 else 1)
    # 幹: ねじれて上る (太さ 2.6 → 1.4)
    path = []
    for y in range(0, 25):
        t = y / 24
        ox = 2.2 * math.sin(t * 3.4)
        oz = 1.6 * math.sin(t * 2.3 + 1.0)
        path.append((cx + ox, y, cz + oz, 2.8 - 1.4 * t))
    for (x, y, z, r) in path:
        disk(v, x, z, y, r, lambda a, b, c: wood_bark + '[axis=y]')
    # 枝と葉の塊
    branches = [(0.4, 12, 9, 1.0), (2.4, 15, 8, 0.7), (4.3, 17, 9, 0.9), (1.4, 20, 6, 0.6), (5.3, 21, 6, 0.5)]
    crowns = []
    for (ang, y, L, rise) in branches:
        bx, _, bz, _ = path[y]
        ex, ez = bx + math.cos(ang) * L, bz + math.sin(ang) * L
        ey = y + L * rise
        arc(v, [(bx, y, bz), ((bx + ex) / 2, y + L * rise * 0.6, (bz + ez) / 2), (ex, ey, ez)], wood_bark + '[axis=y]', thick=2 if L > 7 else 1)
        crowns.append((ex, ey + 1.5, ez, L))
    tx, _, tz, _ = path[-1]
    crowns.append((tx, 26, tz, 9))
    leaves = st.leaves.split('[')[0]
    for i, (x, y, z, L) in enumerate(crowns):
        leaf_blob(v, x, y, z, 3.2 + L * 0.25, 2.2, 3.2 + L * 0.25, leaves if i % 2 == 0 else st.leaves2.split('[')[0], seed=i)
    # 幹を巻く二つの床
    for (y, r) in ((9, 4.5), (16, 3.6)):
        px, _, pz, _ = path[y]
        ring_deck(v, px, pz, y, r, st.wood, st.fence)
    # 枝から吊った鐘 (一本目の枝の先の下)
    ex, ey, ez, _ = crowns[0]
    bx, by, bz = int(round(ex - 2)), int(round(ey - 4)), int(round(ez))
    for y in range(by + 1, int(ey)):
        v.set(bx, y, bz, 'chain[axis=y,waterlogged=false]')
    v.set(bx, by, bz, 'bell[attachment=ceiling,facing=north,powered=false]')
    # 根もとの社 (小さな祠と石の灯籠)
    for (a, b) in ((-5, 6), (5, 6)):
        x, z = cx + a, cz + b
        v.set(x, 0, z, st.base.full)
        v.set(x, 1, z, st.base.wall())
        v.set(x, 2, z, 'lantern[hanging=false,waterlogged=false]')
    return 11


DESIGNS = {'a': lm_ribs, 'b': lm_rock, 'c': lm_tree}


def main():
    if sys.argv[1:2] != ['rough']:
        print(__doc__)
        return
    from region_town import STYLES, _check_blocks
    region = sys.argv[2] if len(sys.argv) > 2 else 'whale'
    st = STYLES[region]
    for k, fn in DESIGNS.items():
        v = Vox(seed=7)
        for x in range(-16, 17):
            for z in range(-16, 17):
                v.set(x, -1, z, 'grass_block[snowy=false]')
        r = fn(v, st, 0, 0)
        v.connect()
        meta = {'rect': [-16, -16, 16, 16], 'level': True, 'ring': 4, 'fillWater': True, 'height': 48}
        data = v.runs(meta)
        data['site'] = 'lm_rough_' + k
        _check_blocks(data, region)
        path = os.path.join(T.DATA, 'lm_rough_%s.json' % k)
        vox.save(path, data)
        print(k, fn.__name__, 'radius', r, sum(1 for s in v.b.values() if s != 'air'), 'blocks')


if __name__ == '__main__':
    main()
