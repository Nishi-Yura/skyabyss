# -*- coding: utf-8 -*-
"""五章の三「雷の層」(docs/design/36 の 8 章)。

三本の道:
  1 東「鼓動の戸」: 控え → 三つの戸の間 (戸の奥で鼓動が鳴るのは一つだけ。鼓動の術で聞き分ける。違う戸は待ち伏せと、前の私の影) → 印の部屋
  2 南「雷の回廊」: 長い回廊 (三つの区間に拍子で雷。床に赤い予告。鼓動の術を使った人には拍の前に心臓の音) → 印の部屋
  3 西「羽虫の巣」: 銅の巻き線の間 → 巣 (帯電の羽虫と避雷の像の波) → 印の部屋
"""
from __future__ import annotations

DOOR_TRUE = 2   # 三つの戸のうち、奥へ続くもの (0..2)

ROUTES = [
    {'nodes': [('vestibule', 16, 4, 0, 3, 3, 5), ('doors', 26, 10, 0, 4, 6, 6), ('mark', 46, 6, 0, 4, 4, 6)],
     'link': ['uw', 'uw', {'from': (32, 14, 0), 'via': [(40, 14, 0), (40, 6, 0)]}]},
    {'nodes': [('hall', 30, 5, 0, 14, 3, 7), ('mark', 52, 6, 0, 3, 3, 6)]},
    {'nodes': [('coil', 18, 4, 2, 3, 3, 5), ('nest', 32, 10, 2, 7, 6, 8), ('mark', 48, 6, 2, 4, 4, 6)]},
]


def build(ctx, routes):
    v = ctx.v
    rng = v.rng
    (r1, rooms1), (r2, rooms2), (r3, rooms3) = routes
    # ---- 1: 三つの戸 (戸の間の +u の壁、w = 6, 10, 14)。違う二つの奥は小部屋
    u, w, y, hu, hw, h = rooms1['doors']
    du = u + hu + 1
    for k, dw in enumerate((w - 4, w, w + 4)):
        for b in (dw - 1, dw, dw + 1):
            for yy in range(y, y + 3):
                r1.cell(du, b, yy)
                r1.put(du, b, yy, 'chiseled_copper' if yy == y + 1 else 'waxed_weathered_cut_copper')
        r1.mark('door_storm_%d' % k, du, dw, y + 1)
        a0 = r1.xz(du, dw - 1)
        a1 = r1.xz(du, dw + 1)
        v.mark('door_storm_%d_a' % k, min(a0[0], a1[0]), y, min(a0[1], a1[1]))
        v.mark('door_storm_%d_b' % k, max(a0[0], a1[0]), y + 2, max(a0[1], a1[1]))
        if k != DOOR_TRUE:
            r1.room(du + 2, dw, y, 1, 1, 3)
            r1.mark('closet_storm_%d' % k, du + 2, dw, y)
    r1.mark('puzzle_storm', u, w, y)
    # ---- 2: 雷の回廊。三つの区間 (両端と真ん中に避雷針)
    u, w, y, hu, hw, h = rooms2['hall']
    r2.mark('bolt_storm_a', u - hu + 3, w, y)
    r2.mark('bolt_storm_b', u + hu - 3, w, y)
    for k in range(3):
        cu = u - hu + 3 + k * (2 * hu - 6) // 2
        for b in (w - hw, w + hw):
            r2.put(cu, b, y, 'polished_tuff')
            r2.put(cu, b, y + 1, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    for a in range(u - hu, u + hu + 1, 4):
        r2.put(a, w, y + h, 'copper_bulb[lit=true,powered=false]')
    # ---- 3: 巣
    u, w, y, hu, hw, h = rooms3['nest']
    r3.mark('nest_storm', u, w, y)
    for _ in range(8):
        a, b = u + rng.randint(-hu + 1, hu - 1), w + rng.randint(-hw + 1, hw - 1)
        if abs(a - u) + abs(b - w) > 2:
            r3.put(a, b, y, 'exposed_copper')
            r3.put(a, b, y + 1, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
