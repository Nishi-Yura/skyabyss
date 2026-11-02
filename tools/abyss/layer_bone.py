# -*- coding: utf-8 -*-
"""五章の一「骨の層」(docs/design/36 の 8 章)。データを直接直す（生成スクリプトはない） が ROUTES で道を作り、build(ctx, routes) で中身を置く。

三本の道 (局所の座標 u = 野営から外、w = 横。データを直接直す（生成スクリプトはない） の説明):
  1 東「肋の回廊」: 骨の広間 (待ち伏せ) → 灯皿の間 (光の術で三つの灯皿を同時に灯すと奥の骨の戸が開く) → 印の部屋。広間の横に、光の中でだけ崩れる壁と隠し部屋
  2 南「骨笛の巣」: 細い首 → 巣 (入ると三つの波。払うと印が取れる) → 印の部屋
  3 西「崩れた肋の橋」: 墓の回廊 → 裂け目 (一マス幅の肋の橋。橋の上で骨拾いが両端から) → 印の部屋
印は story/ch5/layers/AbyssMarks、仕掛けは story/ch5/layers/LayerPuzzles が読む。
"""
from __future__ import annotations

ROUTES = [
    {'nodes': [('hall', 18, 4, 0, 5, 3, 5), ('braziers', 28, 14, 2, 5, 5, 6), ('mark', 46, 6, 2, 4, 4, 6)],
     'link': ['uw', 'uw', {'from': (36, 14, 2), 'via': [(46, 14, 2)]}]},
    {'nodes': [('neck', 19, 3, -3, 3, 2, 4), ('nest', 30, 8, -3, 7, 6, 7), ('mark', 46, 6, -3, 4, 4, 6)]},
    {'nodes': [('gallery', 22, 8, 1, 4, 4, 5), ('chasm', 34, 10, 1, 6, 5, 6), ('mark', 48, 4, 1, 4, 4, 6)],
     'link': ['uw', 'uw', 'uw']},
]


def build(ctx, routes):
    v = ctx.v
    (r1, rooms1), (r2, rooms2), (r3, rooms3) = routes
    # ---- 1: 灯皿の間。三つの灯皿 (消えた魂の焚き火) と、奥の骨の戸 (u = 34..35 の廊下をふさぐ)
    u, w, y, hu, hw, h = rooms1['braziers']
    for k, (bu, bw) in enumerate(((u - 3, w - 3), (u + 3, w - 3), (u, w + 3))):
        r1.put(bu, bw, y, 'bone_block[axis=y]')
        r1.put(bu, bw, y + 1, 'soul_campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]')
        r1.mark('brazier_bone_%d' % k, bu, bw, y + 1)
    # 灯皿の間の奥の口 (u = u+hu+1 .. +2) を骨でふさぐ。開くのは三つが灯ったとき
    for du in (1, 2):
        for k in (-1, 0, 1):
            for yy in range(y, y + 4):
                r1.cell(u + hu + du, w + k, yy)
                r1.put(u + hu + du, w + k, yy, 'bone_block[axis=y]')
    v.mark('door_bone_a', *_corner(r1, u + hu + 1, w - 1, y, u + hu + 2, w + 1, y + 3, 0))
    v.mark('door_bone_b', *_corner(r1, u + hu + 1, w - 1, y, u + hu + 2, w + 1, y + 3, 1))
    r1.mark('puzzle_bone', u, w, y)
    # 骨の広間: 待ち伏せ (広間に入ると壁の窪みから骨拾い)。窪みは飾り
    u, w, y, hu, hw, h = rooms1['hall']
    r1.mark('ambush_bone', u, w, y)
    for du in range(-hu + 1, hu, 3):
        r1.put(u + du, w - hw, y + h - 1, 'bone_block[axis=x]')
    # 光の中でだけ崩れる壁 (広間の +w の壁) と隠し部屋
    for k in (-1, 0, 1):
        for yy in range(y, y + 3):
            r1.cell(u + k, w + hw + 1, yy)
            r1.put(u + k, w + hw + 1, yy, 'cracked_deepslate_bricks')
    r1.room(u, w + hw + 3, y, 1, 1, 3)
    r1.put(u, w + hw + 4, y, 'chest[facing=north,type=single,waterlogged=false]')
    r1.mark('hidden_bone', u, w + hw + 3, y)
    v.mark('hid_bone_a', *_corner(r1, u - 1, w + hw + 1, y, u + 1, w + hw + 1, y + 2, 0))
    v.mark('hid_bone_b', *_corner(r1, u - 1, w + hw + 1, y, u + 1, w + hw + 1, y + 2, 1))
    # ---- 2: 巣
    u, w, y, hu, hw, h = rooms2['nest']
    r2.mark('nest_bone', u, w, y)
    rng = v.rng
    for _ in range(14):
        a, b = u + rng.randint(-hu + 1, hu - 1), w + rng.randint(-hw + 1, hw - 1)
        if abs(a - u) + abs(b - w) > 2:
            r2.put(a, b, y, rng.choice(['bone_block[axis=y]', 'bone_block[axis=x]', 'cobweb']))
    # ---- 3: 裂け目と肋の橋 (床を六つ下げ、一マス幅の橋)。下に落ちたら梯子で手前へ戻る
    u, w, y, hu, hw, h = rooms3['chasm']
    for a in range(u - hu + 2, u + hu - 1):
        for b in range(w - hw, w + hw + 1):
            for yy in range(y - 6, y):
                r3.cell(a, b, yy)
    for a in range(u - hu + 2, u + hu - 1):
        r3.put(a, w, y - 1, 'bone_block[axis=x]' if r3.d in ('e', 'w') else 'bone_block[axis=z]')
        # 橋の下の肋の弧
        if (a - u) % 3 == 0:
            for b in range(w - 3, w + 4):
                yy = y - 2 - abs(b - w) // 2
                r3.put(a, b, yy, 'bone_block[axis=y]')
    for yy in range(y - 6, y):
        r3.put(u - hu + 2, w - 3, yy, 'ladder[facing=%s,waterlogged=false]' % r3.facing(1, 0))
    r3.mark('bridge_bone_a', u - hu + 2, w, y)
    r3.mark('bridge_bone_b', u + hu - 2, w, y)
    r3.mark('pit_bone', u, w, y - 6)


def _corner(r, u0, w0, y0, u1, w1, y1, which):
    """局所の箱の、世界の座標の小さい角 (which=0) と大きい角 (1)"""
    x0, z0 = r.xz(u0, w0)
    x1, z1 = r.xz(u1, w1)
    if which == 0:
        return min(x0, x1), min(y0, y1), min(z0, z1)
    return max(x0, x1), max(y0, y1), max(z0, z1)
