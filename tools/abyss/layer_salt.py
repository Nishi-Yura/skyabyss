# -*- coding: utf-8 -*-
"""五章の二「塩の層」(docs/design/36 の 8 章)。形 B: 護りの下り道。荷運びのロクを、測量士ハヤセの縄 (帰り道のしるし) ごと主の間まで送る。

野営の北の口から一本の長い下り道。三つの待ち場で足を止めるロクを守りながら、待ち場を確かにすると印が入る:
  待ち場 1「塩の殻の床」: 脆い床の間 (殻の下は浅い穴。重さの術なしで乗ると割れる)。重さの術で向こうへ渡り、杭 (stake_salt) を打つと
                        ロクの渡る板の道 (bridge_salt) が殻の上に出る
  待ち場 2「塩風の井戸」: 貯め池の奥の乾いた井戸 (高さ 15)。重さの術で底に立つと塩の風で上の縁へ。縁の灯り (beacon_salt) を点けると
                        梯子 (ladder_salt) が下りて、ロクが上る
  待ち場 3「白殻の縄張り」: ロクが錨を打つあいだ (45 秒)、縄張りの白い貝守と塩の殻虫から守る
  逃げの道「塩の崩れ」: 錨の一打ちで殻がゆるみ、後ろから床が崩れてくる。崩れより先に、主の間の前の格子の門へ
測量士の地図の切れ端 (frag_salt_1..3) を三つの待ち場に一枚ずつ。
"""
from __future__ import annotations

KIND = 'B'


def build_b(ctx, ex):
    v, lay = ctx.v, ctx.lay
    rng = v.rng
    r, Y = ex.r, ex.Y
    y1, y2, y3 = Y - 4, Y - 8, Y - 14
    # ---- 道 A (北へ。見晴らしの裂け目の上を渡る)
    ex.corridor([(11, 0, Y), (14, 0, Y), (35, 0, y1)], h=4, half=1, lamps=6)
    lay.segs['gatecorr_salt'] = dict(layer='salt', kind='gatecorr', seg=lay.cur, route=r)

    # ---- 待ち場 1: 脆い床の間。真ん中の u 38..46 を三つ下げ、上に塩の殻 (白い漆喰)。両側の穴の壁に梯子
    room1 = ex.room('brittle', 42, 0, y1, 7, 5, 7)
    for a in range(38, 47):
        for b in range(-5, 6):
            for yy in range(y1 - 3, y1):
                r.cell(a, b, yy)
            r.put(a, b, y1 - 1, 'white_concrete')
        if a % 3 == 0:
            r.put(a, 0, y1 - 3, 'sea_lantern')
    for yy in range(y1 - 3, y1 - 1):
        r.put(38, -5, yy, 'ladder[facing=%s,waterlogged=false]' % r.facing(1, 0))
        r.put(46, 5, yy, 'ladder[facing=%s,waterlogged=false]' % r.facing(-1, 0))
    box(v, r, 'brittle_salt', (38, -5, y1 - 1), (46, 5, y1 - 1))
    box(v, r, 'bridge_salt', (38, 0, y1 - 1), (46, 0, y1 - 1))
    r.mark('puzzle_salt', 42, 0, y1)
    # 杭 (向こうの縁。右クリックで縄を張る)
    r.put(48, -3, y1, 'spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]')
    r.put(48, -3, y1 + 1, 'lantern[hanging=false,waterlogged=false]')
    r.mark('stake_salt', 48, -3, y1)
    r.mark('frag_salt_1', 36, -4, y1)
    r.put(36, -4, y1, 'chest[facing=north,type=single,waterlogged=false]')
    ex.altar(1, 49, 0, y1, room1)
    ex.spawns(1, [(36, -2), (36, 2), (48, -1), (48, 2)], y1)
    ex.walk((35, 0, y1), (37, 0, y1))
    ex.wait(1)
    ex.walk((37, 0, y1), (47, 0, y1), (47, 5, y1))
    ex.routes.append((r, {'brittle': room1}))

    # ---- 道 B (東へ)
    ex.corridor([(47, 5, y1), (47, 26, y2)], lamps=7)

    # ---- 待ち場 2: 貯め池と乾いた井戸。井戸の上の縁の灯りを点けると、北の壁 (道 C の口の下) に梯子が下りる
    room2 = ex.room('cistern', 47, 30, y2, 4, 4, 5)
    for a in (44, 45):
        for b in (27, 28, 29):
            r.put(a, b, y2 - 1, 'water[level=0]')
            r.put(a, b, y2 - 2, 'prismarine')
    r.mark('frag_salt_2', 44, 31, y2)
    r.put(44, 31, y2, 'chest[facing=north,type=single,waterlogged=false]')
    ex.corridor([(47, 34, y2), (47, 38, y2)], lamps=0)
    lay.fresh('well')
    r.room(47, 40, y2, 2, 2, 15, seg=lay.cur)
    for yy in range(y2, y2 + 15, 3):
        r.put(50, 40, yy, 'prismarine_bricks')
        r.put(47, 43, yy, 'prismarine_bricks')
    # 上の縁 (道 C の床と同じ高さ)。梯子の列 (w = 40) は灯りを点けるまで縁の石
    for b in range(38, 43):
        r.put(45, b, y2 + 10, ctx.LAYERS['salt']['floor'])
    box(v, r, 'ladder_salt', (45, 40, y2), (45, 40, y2 + 10))
    r.mark('well_salt', 47, 40, y2)
    r.mark('well_salt_top', 45, 41, y2 + 11)
    r.put(45, 38, y2 + 11, 'soul_campfire[facing=south,lit=false,signal_fire=false,waterlogged=false]')
    r.mark('beacon_salt', 45, 38, y2 + 11)
    ex.altar(2, 50, 31, y2, room2)
    ex.spawns(2, [(48, 27), (48, 33), (46, 33), (48, 41)], y2)
    ex.walk((47, 38, y2), (47, 39, y2))
    ex.wait(2)
    ex.walk((47, 39, y2), (47, 40, y2), (45, 40, y2), (45, 40, y2 + 11))
    ex.routes.append((r, {'cistern': room2}))

    # ---- 道 C (井戸の上の口から南へ、長く下る)
    ex.corridor([(44, 40, y2 + 11), (-33, 40, y3)], lamps=7)

    # ---- 待ち場 3: 白殻の縄張り。真ん中でロクが錨を打つ (錨の杭 anchor_salt)
    room3 = ex.room('territory', -40, 40, y3, 7, 8, 8)
    path = {(u, 40) for u in range(-44, -32)} | {(-44, w) for w in range(32, 41)}
    for _ in range(14):
        a, b = -40 + rng.randint(-5, 5), 40 + rng.randint(-6, 6)
        if abs(a + 40) + abs(b - 40) > 3 and all((a + da, b + db) not in path for da in (-1, 0, 1) for db in (-1, 0, 1)):
            top = rng.randint(1, 3)
            for yy in range(y3, y3 + top):
                r.put(a, b, yy, 'calcite' if yy < y3 + top - 1 else 'white_terracotta')
    r.put(-40, 42, y3, 'spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]')
    r.put(-40, 42, y3 + 1, 'chain[axis=y,waterlogged=false]')
    r.mark('anchor_salt', -40, 42, y3)
    r.mark('frag_salt_3', -34, 33, y3)
    r.put(-34, 33, y3, 'chest[facing=north,type=single,waterlogged=false]')
    ex.altar(3, -46, 40, y3, room3)
    ex.spawns(3, [(-46, 34), (-46, 46), (-35, 46), (-35, 35)], y3)
    ex.walk((-33, 40, y3), (-40, 40, y3))
    ex.wait(3)
    ex.walk((-40, 40, y3), (-44, 40, y3), (-44, 32, y3))
    ex.routes.append((r, {'territory': room3}))


def box(v, r, name, p0, p1):
    """局所の二点の箱を、世界の印 <名前>_a (小さい角)・<名前>_b (大きい角) に"""
    a = r.xz(p0[0], p0[1])
    b = r.xz(p1[0], p1[1])
    v.mark(name + '_a', min(a[0], b[0]), min(p0[2], p1[2]), min(a[1], b[1]))
    v.mark(name + '_b', max(a[0], b[0]), max(p0[2], p1[2]), max(a[1], b[1]))
