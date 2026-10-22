# -*- coding: utf-8 -*-
"""五章の二「塩の層」(docs/design/36 の 8 章)。

三本の道:
  1 東「塩の殻の床」: 控え → 脆い床の間 (塩の殻の床の下は浅い穴。重さの術なしで乗ると割れる。術の中なら渡れる) → 印の部屋
  2 南「塩風の井戸」: 貯め池 → 乾いた井戸 (高さ 14。重さの術を使って井戸の底に立つと、塩の風で上の口まで浮く) → 上の印の部屋
  3 西「白い貝守の縄張り」: 潮だまり → 縄張り (白い貝守の大きいもの。倒すと印) → 印の部屋
測量士の地図の切れ端 (frag_salt_1..3) を三本の道に一枚ずつ。
"""
from __future__ import annotations

ROUTES = [
    {'nodes': [('ante', 16, 3, 0, 3, 2, 4), ('brittle', 30, 8, 0, 8, 4, 6), ('mark', 46, 6, 0, 4, 4, 6)]},
    {'nodes': [('cistern', 18, 6, 0, 4, 4, 5), ('well', 30, 6, 0, 2, 2, 15), ('mark', 44, 6, 11, 4, 4, 6)],
     'link': ['uw', 'uw', {'from': (33, 6, 11)}]},
    {'nodes': [('pool', 18, 4, -2, 4, 3, 5), ('territory', 34, 10, -2, 8, 7, 8), ('mark', 50, 6, -2, 4, 4, 6)]},
]


def build(ctx, routes):
    v = ctx.v
    rng = v.rng
    (r1, rooms1), (r2, rooms2), (r3, rooms3) = routes
    # ---- 1: 脆い床。真ん中の u-5..u+5 を三つ下げ、上に塩の殻 (白い漆喰)。手前の穴の壁に梯子
    u, w, y, hu, hw, h = rooms1['brittle']
    for a in range(u - hu + 3, u + hu - 2):
        for b in range(w - hw, w + hw + 1):
            for yy in range(y - 3, y):
                r1.cell(a, b, yy)
            r1.put(a, b, y - 1, 'white_concrete')
        # 穴の底は塩の結晶 (光る)
        if (a + w) % 3 == 0:
            r1.put(a, w, y - 3, 'sea_lantern')
    for yy in range(y - 3, y - 1):
        r1.put(u - hu + 3, w - hw, yy, 'ladder[facing=%s,waterlogged=false]' % r1.facing(1, 0))
    a0, b0 = r1.xz(u - hu + 3, w - hw)
    a1, b1 = r1.xz(u + hu - 3, w + hw)
    v.mark('brittle_salt_a', min(a0, a1), y - 1, min(b0, b1))
    v.mark('brittle_salt_b', max(a0, a1), y - 1, max(b0, b1))
    r1.mark('puzzle_salt', u, w, y)
    r1.mark('frag_salt_1', u + hu - 1, w + hw - 1, y)
    r1.put(u + hu - 1, w + hw - 1, y, 'chest[facing=north,type=single,waterlogged=false]')
    # ---- 2: 乾いた井戸。壁に塩の筋、底に印 (浮く所)
    u, w, y, hu, hw, h = rooms2['well']
    for yy in range(y, y + h, 3):
        r2.put(u - hu - 1, w, yy, 'prismarine_bricks')
    r2.mark('well_salt', u, w, y)
    r2.mark('well_salt_top', u + hu, w, y + 11)
    # 井戸の上の口の縁 (立てる床。印の部屋へ続く廊下の床と同じ高さ)
    for b in range(w - hw, w + hw + 1):
        r2.put(u + hu, b, y + 10, ctx.LAYERS['salt']['floor'])
    u, w, y, hu, hw, h = rooms2['cistern']
    r2.mark('frag_salt_2', u - hu + 1, w + hw - 1, y)
    r2.put(u - hu + 1, w + hw - 1, y, 'chest[facing=north,type=single,waterlogged=false]')
    for a in range(u - 2, u + 2):
        for b in range(w + 2, w + 4):
            r2.put(a, b, y - 1, 'water[level=0]')
            r2.put(a, b, y - 2, 'prismarine')
    # ---- 3: 縄張り
    u, w, y, hu, hw, h = rooms3['territory']
    r3.mark('terr_salt', u, w, y)
    for _ in range(10):
        a, b = u + rng.randint(-hu + 2, hu - 2), w + rng.randint(-hw + 2, hw - 2)
        if abs(a - u) + abs(b - w) > 3:
            top = rng.randint(1, 3)
            for yy in range(y, y + top):
                r3.put(a, b, yy, 'calcite' if yy < y + top - 1 else 'white_terracotta')
    u, w, y, hu, hw, h = rooms3['pool']
    r3.mark('frag_salt_3', u + hu - 1, w - hw + 1, y)
    r3.put(u + hu - 1, w - hw + 1, y, 'chest[facing=north,type=single,waterlogged=false]')
    # 塩売り (野営の東の隅。前の探掘隊の荷運びの残り)
    v.mark('trader_salt', 5, ctx.CAMP_Y['salt'], -3)
