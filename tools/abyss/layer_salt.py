# -*- coding: utf-8 -*-
"""塩の層 (y 120〜0) の遊び: 満ち引きの塩の湖 (docs/design/36 の 8 章。A9)。gen_abyss_layers.py が build(ctx) を呼ぶ。

形:
  - 縦穴の真ん中に、塩の湖の器 (半径 11 の内。外の縁は半径 12.5)。底は塩の板、器の下は塩の柱で支える
  - 下の道 (湖の橋): 螺旋の途中の周り (θ0) から、真ん中を横切って反対側 (θ0 + π) へ。二マス幅、塩の床。
    形は橋と同じ（半段の高さが θ0 の高さから反対側の高さへ直線で下る）。橋の柱は器の中で湖を二つに仕切る
  - 潮: 水の高さが S_low (引き潮) と S_high (満ち潮) のあいだを、深淵の潮の周期で上下する (story/ch5/layers/SaltTide)
    引き潮では橋が水の上に出て歩ける。満ち潮では橋は水の下 (泳いで渡る)
  - 高い棚 shelf_salt: 器の縁の上 (満ち潮の水面より 2 つ上)。螺旋からは離れていて、重さの術で浮いて上がるしかない
  - 印: tide_salt_low / tide_salt_high (潮の高さ。y を読む)、lake_salt (湖の底の真ん中)、shelf_salt (棚の上)、
    光: 湖の底の海灯 (水の中から照らす)、塩の柱・結晶・滴る塩 (つらら)
  - 水は設計図に「水」として置く (器の中で、塩と柱のない所)。潮の係が水の高さを変える
"""
import math
import random

R_LAKE = 11          # 湖の内の半径
R_RIM = 12.5         # 器の外の縁
R_RING = 15.5        # 螺旋の内の縁 (橋の端は内側)
HALF_W = 1.1         # 橋の半幅 (二マス)
PILLAR_R = 4.5       # 柱の太さ (底の下で細くなる)


def stand_of(h2):
    return h2 // 2 if h2 % 2 == 0 else h2 // 2 + 1


def block_of(h2):
    return h2 // 2 if h2 % 2 == 1 else h2 // 2 - 1


def build(ctx):
    v = ctx.v
    rng = random.Random(43)
    span = ctx.span('salt')
    i0 = min(range(len(span)), key=lambda j: abs(stand_of(span[j][1]) - 60))
    th0, h0 = span[i0][0], span[i0][1]
    ie = next(j for j in range(i0, len(span)) if span[j][0] - th0 >= math.pi)
    the, he = span[ie][0], span[ie][1]
    sH, sE = stand_of(h0), stand_of(he)
    S_LOW = sE - 3          # 引き潮の水面 (橋は水の上)
    S_HIGH = sH + 2         # 満ち潮の水面 (橋は水の下。高い棚は届かない)
    F = S_LOW - 6           # 器の底
    RIM = S_HIGH + 2        # 器の縁の上面 (満ち潮の水面より 2 つ上 = 浮かないと届かない)

    # ---- 器: 底、外の壁、塩の柱 (底の下)
    for x in range(-14, 15):
        for z in range(-14, 15):
            d = math.hypot(x, z)
            if d <= R_RIM:
                v.set(x, F, z, rng.choice(['smooth_quartz', 'smooth_quartz', 'calcite']))
            if R_LAKE < d <= R_RIM:
                for y in range(F + 1, RIM + 1):
                    v.set(x, y, z, rng.choice(['calcite', 'calcite', 'white_terracotta', 'smooth_quartz']))
            if d <= PILLAR_R:
                for y in range(F - 18, F):
                    r = PILLAR_R - (F - 1 - y) / 7.0
                    if d <= max(1.5, r):
                        v.set(x, y, z, rng.choice(['calcite', 'smooth_quartz', 'calcite']))

    # ---- 下の道 (湖の橋): 螺旋の θ0 の高さから、θe の高さへ、真ん中を横切る
    A = (R_RING * math.cos(th0), R_RING * math.sin(th0))
    B = (R_RING * math.cos(the), R_RING * math.sin(the))
    lay_chord(v, rng, A, B, h0, he, F)
    v.mark('bridge_salt_a', round(A[0]), sH, round(A[1]))
    v.mark('bridge_salt_b', round(B[0]), sE, round(B[1]))

    # ---- 湖: 海灯 (底の光。橋の下からも照らす)、水 (満ち潮まで)、塩の結晶と滴る塩
    for (x, z) in ((-6, 4), (5, -5), (-3, -7), (6, 6), (0, 7)):
        if v.get(x, F + 1, z) in (None, 'air'):
            v.set(x, F + 1, z, 'sea_lantern')
    for x in range(-R_LAKE, R_LAKE + 1):
        for z in range(-R_LAKE, R_LAKE + 1):
            if math.hypot(x, z) >= R_LAKE:
                continue
            for y in range(F + 1, S_HIGH + 1):
                if v.get(x, y, z) in (None, 'air'):
                    v.set(x, y, z, 'water')
    for k in range(9):
        a = k * 2 * math.pi / 9 + 0.2
        x, z = round(math.cos(a) * 13.2), round(math.sin(a) * 13.2)
        for y in range(F + 1, F + 2 + rng.randint(2, 5)):
            if v.get(x, y, z) in (None, 'air', 'water'):
                v.set(x, y, z, 'calcite' if (y - F) < 3 else 'pointed_dripstone[vertical_direction=up,thickness=middle,waterlogged=false]')
    for x in range(-R_LAKE, R_LAKE + 1):
        for z in range(-R_LAKE, R_LAKE + 1):
            if R_LAKE <= math.hypot(x, z) < R_RIM - 0.4 and rng.random() < 0.3:
                v.set(x, RIM + 1, z, 'pointed_dripstone[vertical_direction=up,thickness=tip,waterlogged=false]')

    # ---- 高い棚 (器の縁。螺旋とは離れている)。θ0 の直角の向き
    sa = th0 + math.pi / 2
    sx, sz = round(math.cos(sa) * 11.5), round(math.sin(sa) * 11.5)
    v.mark('shelf_salt', sx, RIM + 1, sz)
    v.mark('tide_salt_low', 0, S_LOW, 0)
    v.mark('tide_salt_high', 0, S_HIGH, 0)
    v.mark('lake_salt', 0, F + 1, 0)


def lay_chord(v, rng, A, B, h_a, h_b, floor_y):
    """A から B へ、二マス幅の塩の橋 (半段の高さは h_a → h_b に直線で変わる)。湖の中では下の柱で支える"""
    ax, az = A
    bx, bz = B
    dx, dz = bx - ax, bz - az
    L2 = dx * dx + dz * dz
    L = math.sqrt(L2)
    cells = {}
    for x in range(int(min(ax, bx)) - 3, int(max(ax, bx)) + 4):
        for z in range(int(min(az, bz)) - 3, int(max(az, bz)) + 4):
            t = ((x - ax) * dx + (z - az) * dz) / L2
            if t < -0.05 or t > 1.05:
                continue
            t = min(1.0, max(0.0, t))
            if math.hypot(x - (ax + t * dx), z - (az + t * dz)) > HALF_W:
                continue
            if math.hypot(x, z) > R_RING:
                continue
            cells[(x, z)] = (round(h_a + (h_b - h_a) * t), t)
    for (x, z), (h2, t) in cells.items():
        by = block_of(h2)
        for y in range(by + 1, by + 4):
            v.set(x, y, z, 'air')
        k = int(t * L)
        if h2 % 2 == 1:
            v.set(x, by, z, 'smooth_quartz_slab[type=bottom,waterlogged=false]')
        elif k % 5 == 0:
            v.set(x, by, z, 'calcite')
        else:
            v.set(x, by, z, 'smooth_quartz')
        # 湖の中は、橋の下を底まで柱で支える (橋が水の上に出る)
        if math.hypot(x, z) < R_LAKE:
            for y in range(floor_y + 1, by):
                v.set(x, y, z, 'calcite' if (x + z + y) % 3 else 'smooth_quartz')
        elif k % 4 == 1 and rng.random() < 0.5 and v.get(x, by - 1, z) in (None, 'air'):
            # 橋の下から滴る塩 (つらら)
            v.set(x, by - 1, z, 'pointed_dripstone[vertical_direction=down,thickness=tip,waterlogged=false]')
