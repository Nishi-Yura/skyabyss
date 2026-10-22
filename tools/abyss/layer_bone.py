# -*- coding: utf-8 -*-
"""骨の層 (y 240〜120) の遊び: 崩れた螺旋と肋の橋 (docs/design/36 の 8 章。A9)。gen_abyss_layers.py が build(ctx) を呼ぶ。

形:
  - 層の深さ 60% の所で、螺旋の道を角度 GAP (約 2 rad) ぶん抜く (崩れ)。抜けた両端を、縦穴の中を横切る肋の橋でつなぐ
  - 橋: 二マス幅の方解石の道 (半段は多角形の石のハーフ)。下に肋のアーチ (骨ブロックの弧)、灯籠を下げる
  - 橋の両端の印: bridge_bone_a (橋の手前) / bridge_bone_b (橋の向こう)。骨拾いの湧きは story/ch5/layers/BoneBridge
  - 橋の向こうの螺旋の先に巣 (den_bone)。肋の番の門は BoneBridge が、巣の戸の口 (半径 20〜22) を封じて作る
  - 近道の印 shortcut_bone = 巣の床の真ん中 (肋の番を倒すと鎖の近道。近道の作りは layer_common / main の仕事)
"""
import math
import random

GAP = 2.0           # 抜く角度 (rad)。橋の長さ約 25
GAP_FRAC = 0.6      # 層の上から何割の所で崩れるか
HALF_W = 1.1        # 橋の半幅 (二マス幅)
R_RING = 15.5       # 螺旋の内の縁。橋はこの内側だけ (螺旋の道に当たらない)
STAND_LAG = 1


def stand_of(h2):
    """半段の高さ h2 (= 歩く面 × 2) から、立つ高さ (y)"""
    return h2 // 2 if h2 % 2 == 0 else h2 // 2 + 1


def block_of(h2):
    """半段の高さから、床ブロックの高さ (奇数は半ブロック、偶数は丸ごと)"""
    return h2 // 2 if h2 % 2 == 1 else h2 // 2 - 1


def build(ctx):
    v = ctx.v
    rng = random.Random(41)
    span = ctx.span('bone')
    ia = int(len(span) * GAP_FRAC)
    th_a = span[ia][0]
    ib = next(j for j in range(ia, len(span)) if span[j][0] - th_a >= GAP)
    th_b, h_a, h_b = span[ib][0], span[ia][1], span[ib][1]

    # ---- 崩れ: 螺旋の道のうち、角度 (th_a, th_b) の間で、その層の高さの窓だけを抜く (次の周回は 27 段下なので当たらない)
    lo = stand_of(h_b) - 4
    hi = stand_of(h_a) + 4
    for x in range(-21, 22):
        for z in range(-21, 22):
            d = math.hypot(x, z)
            if not (ctx.PATH_IN - 1.5 <= d <= ctx.PATH_OUT):
                continue
            rel = (math.atan2(z, x) - th_a) % (2 * math.pi)
            if not (0.12 < rel < GAP - 0.12):
                continue
            for y in range(lo, hi + 1):
                v.set(x, y, z, 'air')

    # ---- 肋の橋 (縦穴を横切る。両端は螺旋の内の縁の内側)
    A = (R_RING * math.cos(th_a), R_RING * math.sin(th_a))
    B = (R_RING * math.cos(th_b), R_RING * math.sin(th_b))
    lay_chord(v, rng, A, B, h_a, h_b)
    v.mark('bridge_bone_a', round(A[0]), stand_of(h_a), round(A[1]))
    v.mark('bridge_bone_b', round(B[0]), stand_of(h_b), round(B[1]))

    # ---- 巣の床 (肋の番の部屋) と近道の印
    den = v.marks.get('den_bone')
    if den:
        v.mark('shortcut_bone', den[0], den[1], den[2])


def lay_chord(v, rng, A, B, h_a, h_b):
    """A から B へ、二マス幅の橋を架ける (半段の高さは h_a → h_b に直線で変わる)。橋の下に肋のアーチ"""
    ax, az = A
    bx, bz = B
    dx, dz = bx - ax, bz - az
    L2 = dx * dx + dz * dz
    L = math.sqrt(L2)
    px, pz = -dz / L, dx / L          # 橋に直角な向き
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
            v.set(x, by, z, 'polished_diorite_slab[type=bottom,waterlogged=false]')
        elif k % 6 == 0:
            v.set(x, by, z, 'bone_block[axis=y]')
        else:
            v.set(x, by, z, 'calcite')
        v.set(x, by - 1, z, 'calcite')
    # 肋: 四マスごとに、橋の下へ弧を描く骨 (幅 9、深さは中央ほど深い)
    for k in range(0, int(L) + 1, 4):
        t = k / L
        qx, qz = ax + t * dx, az + t * dz
        by = block_of(round(h_a + (h_b - h_a) * t))
        for u in range(-4, 5):
            depth = round(5 * math.sqrt(max(0.0, 1 - (u / 4.5) ** 2)))
            x, z = round(qx + px * u), round(qz + pz * u)
            if math.hypot(x, z) > R_RING:
                continue
            for dd in range(1, depth + 1):
                if v.get(x, by - dd, z) in (None, 'air'):
                    v.set(x, by - dd, z, 'bone_block[axis=y]')
        # 橋の下の灯籠 (次の層の灯りを見下ろす目印。骨の影を照らす)
        if (k // 4) % 2 == 0:
            x, z = round(qx), round(qz)
            if math.hypot(x, z) <= R_RING and v.get(x, by - 2, z) in (None, 'air'):
                v.set(x, by - 2, z, 'soul_lantern[hanging=true,waterlogged=false]')
