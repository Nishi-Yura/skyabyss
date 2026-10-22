# -*- coding: utf-8 -*-
"""雷の層 (y 0〜-120) の遊び: 拍子で落ちる雷の道 (docs/design/36 の 8 章。A9)。gen_abyss_layers.py が build(ctx) を呼ぶ。

形:
  - 層の上から 35% の所の螺旋の道 (角度 th_a) から、縦穴の真ん中を横切る銅の吊り橋を架ける。向こう側 (th_a + π) に着く張り出しの足場。
    橋は三区間 (section 0〜2)。区間の真ん中に避雷の像 (印 idol_0〜2) が立つ。像を壊すとその区間の雷が止まる。
  - 橋の向こうの足場から、半回り下の螺旋の道へ鎖のはしご (梯子) で下りる。橋は螺旋を半周ぶん飛ばす近道になる（螺旋の本道は残る）。
  - 橋の両側に避雷針の柱と、ほつれた鎖（吊り索）。銅は酸化の帯 (oxidized) と若い銅を交互に。
  - 印: storm_bridge_a (橋の手前・螺旋の上)、storm_bridge_b (橋の向こうの足場)、idol_0〜2 (区間の真ん中)。
  - 雷の拍と予告は story/ch5/layers/StormBridge (世界の時間から決まる)。ここは置くだけ。
"""
import math
import random

BRIDGE_FRAC = 0.35   # 層の上から何割の所で橋を架けるか
R_IN = 15.5          # 螺旋の内の縁 (橋はここから)
PATH_OUT = 19.5      # 螺旋の外の縁
LAND_IN = 12.5       # 向こうの足場の内の縁
LAND_HALF = 2        # 向こうの足場の半幅
SECTIONS = 3


def block_of(h2):
    """半段の高さ h2 から、床ブロックの高さ（gen_abyss_layers の道と同じ決まり）"""
    return h2 // 2 if h2 % 2 == 1 else h2 // 2 - 1


def build(ctx):
    v = ctx.v
    rng = random.Random(53)
    span = ctx.span('storm')
    ia = int(len(span) * BRIDGE_FRAC)
    th_a, h2 = span[ia]
    D = block_of(h2)                      # 橋の床の高さ (螺旋の床と同じ)
    th_b = th_a + math.pi
    ua = (math.cos(th_a), math.sin(th_a))
    ub = (math.cos(th_b), math.sin(th_b))
    pa = (-ua[1], ua[0])                  # 橋の横向き

    # ---- 橋の床: 中心を通る直線 (近い縁 R_IN から遠い縁 -R_IN)。幅 3 (中・両側)
    deck = []
    for k in range(-int(R_IN * 2) - 1, int(R_IN * 2) + 2):
        t = k * 0.5
        if abs(t) > R_IN:
            continue
        for o in (-1, 0, 1):
            x = round(ua[0] * t + pa[0] * o)
            z = round(ua[1] * t + pa[1] * o)
            if (x, z) not in deck:
                deck.append((x, z))
    # 橋の長さの順 (近い縁から遠い縁へ) に並べ直す: 螺旋の近い縁が 0
    deck.sort(key=lambda c: -(c[0] * ua[0] + c[1] * ua[1]))
    n = len(deck)
    for i, (x, z) in enumerate(deck):
        for y in range(D + 1, D + 4):
            v.set(x, y, z, 'air')
        mat = _deck_block(i, n, rng)
        v.set(x, D, z, mat)
        # 両側の柱 (避雷針と吊り鎖)
        if i % 4 == 0 and n > 4:
            for o in (-2, 2):
                px = round(x + pa[0] * o)
                pz = round(z + pa[1] * o)
                if (px, pz) in deck:
                    continue
                v.set(px, D, pz, 'oxidized_cut_copper')
                v.set(px, D + 1, pz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
                for y in range(D + 2, D + 4):
                    v.set(px, y, pz, 'chain[axis=y,waterlogged=false]')

    # ---- 向こうの足場 (螺旋の外の縁の内側。床 D、支え D-1、頭の上を空ける)
    for x in range(-22, 23):
        for z in range(-22, 23):
            along = x * ub[0] + z * ub[1]
            side = abs(x * pa[0] + z * pa[1])
            d = math.hypot(x, z)
            if not (LAND_IN <= along <= PATH_OUT and side <= LAND_HALF + 0.5 and d <= PATH_OUT + 0.5):
                continue
            v.set(x, D, z, 'stone_bricks' if rng.random() < 0.7 else 'cracked_stone_bricks')
            for y in range(D + 1, D + 4):
                v.set(x, y, z, 'air')
            v.set(x, D - 1, z, 'deepslate_bricks')

    # ---- 足場からはしご: 螺旋の外の縁の壁に沿って、半回り下の螺旋の床まで
    low = _lower_pass(span, th_b, h2)
    if low is not None:
        low_stand = block_of(low) + 1
        cx = round(ub[0] * 19)
        cz = round(ub[1] * 19)
        face = _facing_inward(cx, cz)
        back = (cx + _back(face)[0], cz + _back(face)[1])
        if v.get(back[0], D, back[1]) not in (None, 'air'):
            for y in range(low_stand, D + 2):
                v.set(cx, y, cz, 'ladder[facing=%s,waterlogged=false]' % face)

    # ---- 印
    sx, sz = round(ua[0] * (R_IN - 0.5)), round(ua[1] * (R_IN - 0.5))
    v.mark('storm_bridge_a', sx, D + 1, sz)
    v.mark('storm_bridge_b', round(ub[0] * 16), D + 1, round(ub[1] * 16))
    for s in range(SECTIONS):
        tc = R_IN - (2 * R_IN) * (s + 0.5) / SECTIONS
        ix, iz = round(ua[0] * tc), round(ua[1] * tc)
        v.set(ix, D + 1, iz, 'air')
        v.mark('idol_%d' % s, ix, D + 1, iz)


def _deck_block(i, n, rng):
    """橋の床: 酸化の帯 (oxidized) と、ところどころ若い銅。三区間ごとに帯を変える"""
    band = (i * 3) // max(1, n)
    if i % 5 == 0:
        return 'oxidized_cut_copper'
    if band == 1 and i % 2 == 0:
        return 'weathered_cut_copper'
    return 'cut_copper' if rng.random() < 0.8 else 'exposed_cut_copper'


def _lower_pass(span, th, h2):
    """角度 th にいちばん近い、一回り手前 (半段で 20 以上) の螺旋の高さ (半段)。無ければ None"""
    best = None
    for (t, hh) in span:
        d = abs((t - th + math.pi) % (2 * math.pi) - math.pi)
        if hh < h2 - 20 and d < 0.25:
            if best is None or hh > best:
                best = hh
    return best


def _facing_inward(cx, cz):
    """壁の内側へ向く向き (梯子の表)。壁は外側にある"""
    if abs(cx) >= abs(cz):
        return 'west' if cx > 0 else 'east'
    return 'north' if cz > 0 else 'south'


def _back(face):
    return {'west': (1, 0), 'east': (-1, 0), 'north': (0, 1), 'south': (0, -1)}[face]
