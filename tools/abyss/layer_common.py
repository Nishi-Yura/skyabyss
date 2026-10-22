# -*- coding: utf-8 -*-
"""深淵の全部の層に共通の寄り道 (docs/design/36 の 8 章。00 の 252〜255)。gen_abyss_layers.py から build(ctx) で呼ばれる。

  - 脇の洞: 層ごとに 2 本。道の外の縁から壁を抜けて外へ出て、横へ曲がる細い洞 (幅 3・高さ 3) の先に小部屋 (7 x 7、高さ 5)
            小部屋に前の私の墓 (印 grave_<層>_<1|2>) と刻み (印 carve_<層>_<1|2>)
  - 墓の三つめ: 前の私の野営の部屋 (camp_<層>) の奥 (印 grave_<層>_3)
  - 刻み: 道の外の壁にもう二つ (層の上の方と下の方。印 carve_<層>_<3|4>)。刻みは目立つ石 (彫った深層岩) で、右クリックで読む (story/ch5/AbyssFinds)
  - 近道: 層の底の休み場に巻き上げ機 (磁石石。印 shortcut_<層>_bottom)、層の上の休み場 (骨は着く所) に鎖の掛け金 (印 shortcut_<層>_top)。
          開くのは底で巻き上げ機を使ったとき (story/ch5/AbyssShortcuts。ワールドで共有)。灰の層の底は心臓の間の上の縁 (印 ash_bottom)
  - 見る者の立つ所: 層ごとに一つ、脇の洞の入口の奥 (印 watch_<層>)。近づくと消える (story/ch5/AbyssHorror)
"""
from __future__ import annotations

import math

CAVE_AT = (0.35, 0.65)     # 層の道の何割の所から脇の洞へ入るか
CARVE_AT = (0.1, 0.9)      # 道の外の壁の刻み
CAVE_OUT = 6               # 壁の外へ何マス出てから曲がるか
CAVE_SIDE = 9              # 横へ曲がる長さ (弧の長さ)
CHAMBER = 3                # 小部屋の半分の幅 (7 x 7)


def build(ctx):
    v = ctx.v
    for li, L in enumerate(ctx.LAYERS):
        name = L[0]
        span = ctx.span(name)
        if not span:
            continue
        for k, f in enumerate(CAVE_AT):
            th, h2 = on_path(ctx, span, f)
            cave(ctx, L, name, k + 1, th, (h2 + 1) // 2, side=1 if k == 0 else -1)
        for k, f in enumerate(CARVE_AT):
            th, h2 = span[int(len(span) * f)]
            carve_on_wall(ctx, name, k + 3, th, (h2 + 1) // 2)
        camp_grave(ctx, name)
        shortcut(ctx, li, name)


def on_path(ctx, span, f):
    """道の割合 f の所。層の仕掛けで道が抜かれていれば (骨の層の崩れなど)、道の床が残っている所までずらす"""
    n = len(span)
    for d in range(0, n):
        for i in (int(n * f) + d, int(n * f) - d):
            if 0 <= i < n:
                th, h2 = span[i]
                y = (h2 + 1) // 2
                x, z = int(round(math.cos(th) * 17.5)), int(round(math.sin(th) * 17.5))
                if any(ctx.v.get(x, yy, z) not in (None, 'air', 'cave_air') for yy in (y - 1, y - 2)):
                    return span[i]
    return span[int(n * f)]


def cave(ctx, L, name, k, th, y, side):
    """道の外の縁 → 壁を抜けて外へ CAVE_OUT → 横へ CAVE_SIDE → 小部屋。床は道と同じ高さ (立つ高さ y)"""
    v = ctx.v
    rock = L[3]
    floor = L[4]
    ox, oz = math.cos(th), math.sin(th)
    r0 = int(ctx.PATH_OUT) - 1
    r1 = ctx.R_IN + ctx.WALL + CAVE_OUT
    # 洞の芯の線 (0.5 刻み)。芯から 1.3 以内のマスを洞にする (斜めでも穴があかない幅 3)
    line = []
    r = float(r0)
    while r <= r1:
        line.append((ox * r, oz * r))
        r += 0.5
    a = th
    steps = int(CAVE_SIDE * 2)
    for i in range(1, steps + 1):
        a = th + side * (i * 0.5) / r1
        line.append((math.cos(a) * r1, math.sin(a) * r1))
    cells = []
    for (px, pz) in line:
        for x in range(int(math.floor(px - 2)), int(math.ceil(px + 2)) + 1):
            for z in range(int(math.floor(pz - 2)), int(math.ceil(pz + 2)) + 1):
                if math.hypot(x - px, z - pz) <= 1.3:
                    cells.append((x, z))
    # 小部屋 (弧の先、少し外)
    rc = r1 + 2
    mx, mz = int(round(math.cos(a) * rc)), int(round(math.sin(a) * rc))
    room = [(x, z) for x in range(mx - CHAMBER, mx + CHAMBER + 1) for z in range(mz - CHAMBER, mz + CHAMBER + 1)]
    inner = ctx.PATH_IN + 1
    # 殻 (まわりの岩) → 中を空ける。道の内の縁を壊さない
    for (x, z) in set(cells + room):
        if math.hypot(x, z) < inner:
            continue
        top = 4 if (x, z) in room else 3
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                xx, zz = x + dx, z + dz
                if math.hypot(xx, zz) < ctx.R_IN - 0.5:
                    continue
                for yy in range(y - 1, y + top + 1):
                    if v.get(xx, yy, zz) in (None, 'air'):
                        v.set(xx, yy, zz, ctx.wall_block(yy, xx, zz))
    for (x, z) in set(cells + room):
        if math.hypot(x, z) < inner:
            continue
        top = 4 if (x, z) in room else 3
        v.set(x, y - 1, z, floor if (x, z) in room else rock[0])
        for yy in range(y, y + top):
            v.set(x, yy, z, 'cave_air')
    # 灯りは小部屋に一つだけ (洞の道は暗い)
    v.set(mx, y + 3, mz, 'soul_lantern[hanging=true,waterlogged=false]')
    # 墓: 奥の壁の前に、塚 (半石) と頭の印 (骷髏) と蝋燭
    ax, az = math.cos(a), math.sin(a)
    gx, gz = int(round(mx + ax * 2)), int(round(mz + az * 2))
    grave(v, name, k, gx, y, gz)
    # 刻み: 小部屋の横の壁 (彫った深層岩)
    sx, sz = int(round(mx - az * CHAMBER - az)), int(round(mz + ax * CHAMBER + ax))
    v.set(sx, y + 1, sz, 'chiseled_deepslate')
    v.mark('carve_%s_%d' % (name, k), sx, y + 1, sz)
    # 見る者は一本目の洞の曲がり角に立つ (洞の入口から見える奥)
    if k == 1:
        wx, wz = int(round(math.cos(th + side * 2 / r1) * r1)), int(round(math.sin(th + side * 2 / r1) * r1))
        v.mark('watch_%s' % name, wx, y, wz)


def grave(v, name, k, x, y, z):
    v.set(x, y, z, 'cobbled_deepslate_slab[type=bottom,waterlogged=false]')
    v.set(x, y + 1, z, 'skeleton_skull[rotation=0]')
    v.set(x + 1, y, z, 'candle[candles=2,lit=false,waterlogged=false]')
    v.mark('grave_%s_%d' % (name, k), x, y, z)


def camp_grave(ctx, name):
    """前の私の野営の部屋の奥の隅に三つめの墓"""
    m = ctx.v.marks.get('camp_%s' % name)
    if m is None:
        return
    x, y, z = m
    grave(ctx.v, name, 3, x + 3, y, z + 3)


def carve_on_wall(ctx, name, k, th, y):
    """道の外の壁 (半径 R_IN) の頭の高さに彫った石"""
    x, z = int(round(math.cos(th) * (ctx.R_IN + 0.3))), int(round(math.sin(th) * (ctx.R_IN + 0.3)))
    ctx.v.set(x, y + 1, z, 'chiseled_deepslate')
    ctx.v.mark('carve_%s_%d' % (name, k), x, y + 1, z)


def shortcut(ctx, li, name):
    """層の上の休み場 (骨は着く所) と、層の底の休み場 (灰は心臓の間の上の縁) をつなぐ鎖。開いた鎖は story/ch5/AbyssShortcuts が掛ける"""
    v = ctx.v
    rests = ctx.rests
    if li == 0:
        top = v.marks.get('arrival')
    else:
        top = v.marks.get('rest_%d' % li)
    if li < len(ctx.LAYERS) - 1:
        bottom = v.marks.get('rest_%d' % (li + 1))
    else:
        th, h2 = ctx.path[-1]
        bottom = (int(round(math.cos(th) * 17)), ctx.HEART_BOTTOM + 1, int(round(math.sin(th) * 17)))
        v.mark('ash_bottom', *bottom)
    if top is None or bottom is None:
        return
    # 巻き上げ機: 底の休み場の焚き火から 3 横に磁石石
    bx, by, bz = bottom
    wx, wz = side_of(bx, bz, 3)
    v.set(wx, by, wz, 'lodestone')
    v.mark('shortcut_%s_bottom' % name, wx, by, wz)
    # 掛け金: 上の休み場の焚き火から反対の向きに 3 横に鉄格子の杭 (鎖はここから下がる。同じ休み場の巻き上げ機と重ならない)
    tx, ty, tz = top
    kx, kz = side_of(tx, tz, -3)
    v.set(kx, ty, kz, 'iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]')
    v.mark('shortcut_%s_top' % name, kx, ty, kz)


def side_of(x, z, d):
    """(x, z) から縦穴の周りの向きに d ずらした所 (反時計回り)"""
    a = math.atan2(z, x) + d / max(1.0, math.hypot(x, z))
    r = math.hypot(x, z)
    return int(round(math.cos(a) * r)), int(round(math.sin(a) * r))
