# -*- coding: utf-8 -*-
"""全部の層に共通の探すもの (docs/design/36 の 8 章)。gen_abyss_layers.py から build(ctx, layer, routes) で呼ばれる。

  - 前の私の墓 (grave_<層>_1..3): 三本の道の最初の部屋の奥の隅に一つずつ。塚 (半石)・頭の印・蝋燭。右クリックで読む (story/ch5/AbyssFinds)
  - 前の私の刻み (carve_<層>_1..4): 道 1・2・3 の印の部屋の横の壁と、野営の西の壁。彫った深層岩
  - 探掘隊の手記 (journal_<層>_1..3): 道ごとに一冊、二つめの部屋 (無ければ印の部屋) の隅の書見台。野営の帳面は journal_<層>_0 (gen_abyss_layers)
  - 見る者の立つ所 (watch_<層>): 道 2 の最初の部屋の奥 (story/ch5/AbyssHorror)
"""
from __future__ import annotations


def build(ctx, layer, routes):
    v = ctx.v
    Y = ctx.CAMP_Y[layer]
    for i, (r, rooms) in enumerate(routes, start=1):
        names = list(rooms)
        first = rooms[names[0]]
        u, w, y, hu, hw, h = first
        gu, gw = u + hu - 1, w + hw - 1
        r.put(gu, gw, y, 'cobbled_deepslate_slab[type=bottom,waterlogged=false]')
        r.put(gu, gw, y + 1, 'skeleton_skull[rotation=0]')
        gx, gz = r.xz(gu, gw)
        # 蝋燭は東隣 (AbyssFinds は東隣も墓とみなす)
        if (gx + 1, y, gz) in ctx.lay.air and (gx + 1, y, gz) not in ctx.lay.fixed:
            ctx.lay.put(gx + 1, y, gz, 'candle[candles=2,lit=false,waterlogged=false]')
        v.mark('grave_%s_%d' % (layer, i), gx, y, gz)
        # 刻み: 印の部屋の +w の壁 (頭の高さ)
        mu, mw, my, mhu, mhw, mh = rooms[names[-1]]
        cu, cw = wall_spot(ctx, r, rooms[names[-1]])
        r.put(cu, cw, my + 1, 'chiseled_deepslate')
        r.mark('carve_%s_%d' % (layer, i), cu, cw, my + 1)
        # 手記
        ju, jw, jy, jhu, jhw, jh = rooms[names[1]] if len(names) > 2 else rooms[names[-1]]
        r.put(ju - jhu + 1, jw + jhw - 1, jy, 'lectern[facing=%s,has_book=true,powered=false]' % r.facing(1, 0))
        r.mark('journal_%s_%d' % (layer, i), ju - jhu + 1, jw + jhw - 1, jy)
        if i == 2:
            r.mark('watch_%s' % layer, u + hu - 1, w - hw + 1, y)
    # 四つめの刻み: 野営の西の壁 (北寄り)
    ctx.lay.put(-9, Y + 1, -5, 'chiseled_deepslate')
    v.mark('carve_%s_4' % layer, -9, Y + 1, -5)


def wall_spot(ctx, r, room):
    """部屋の壁の、頭の高さの一マス (外も内も通り道でない所)。+w の壁 → -u の壁 → +u の壁の順に探す"""
    u, w, y, hu, hw, h = room
    air = ctx.lay.air
    cands = [(a, w + hw + 1, (0, -1)) for a in range(u - hu + 1, u + hu)]
    cands += [(u - hu - 1, b, (1, 0)) for b in range(w - hw + 1, w + hw)]
    cands += [(u + hu + 1, b, (-1, 0)) for b in range(w - hw + 1, w + hw)]
    for (a, b, (da, db)) in cands:
        ok = True
        for yy in (y, y + 1, y + 2):
            x, z = r.xz(a, b)
            if (x, yy, z) in air:
                ok = False
            # 隣 (壁に沿って) も通り道でない
            for s in (-1, 1):
                x2, z2 = r.xz(a + (s if db else 0), b + (s if da else 0))
                if (x2, yy, z2) in air and (x2, yy, z2) not in _room_cells(r, room):
                    ok = False
        if ok:
            return a, b
    return u, w + hw + 1


def _room_cells(r, room):
    u, w, y, hu, hw, h = room
    out = set()
    for a in range(u - hu, u + hu + 1):
        for b in range(w - hw, w + hw + 1):
            x, z = r.xz(a, b)
            for yy in range(y, y + h):
                out.add((x, yy, z))
    return out
