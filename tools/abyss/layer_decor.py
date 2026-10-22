# -*- coding: utf-8 -*-
"""深淵の縦穴の中の大きな目印と灯り (docs/design/36 の 8 章の見た目)。gen_abyss_layers.py から、層の遊びと共通の寄り道のあとに build(ctx) で呼ばれる。

縦穴の壁は縦の筋ばかりで暗く、どの層も同じに見えた。螺旋の道の巻きと巻きのあいだ (道のない高さ) に、層ごとの大きな形を壁から吹き抜けへ張り出す:
  骨  巨大な肋の弧 (壁から芯へ垂れて戻る骨の梁)。先に鎖で吊った魂の灯り
  塩  塩の結晶の房 (壁から斜めに突き出す白い柱の束)。芯に真珠色の光
  雷  銅の輪 (芯のまわりに浮く輪を、壁からの銅の梁が支える)。輪の上に灯った銅の電球と避雷針
  灰  玄武岩の柱 (下から突き上げる柱と、燠の筋)。灯りは少なく、燠の赤だけ
道の通る高さ (巻きの上下 5) には何も置かない。芯の半径 4 の中は空けておく (落ちる人の通り道と、上から下の灯りの見え方)
"""
from __future__ import annotations

import math

CLEAR = 4          # 道の歩く面から上下にこれだけは空ける
CORE = 4           # 芯のこの半径の中には置かない


def build(ctx):
    v = ctx.v
    rng = v.rng
    walks = [((th % (2 * math.pi)), (h2 + 1) // 2) for th, h2 in ctx.path]

    def free(a, y0, y1):
        """角度 a の前後 0.35 rad で、y0..y1 に道の歩く面 (±CLEAR) が無いか"""
        for th, y in walks:
            d = abs((th - a + math.pi) % (2 * math.pi) - math.pi)
            if d < 0.25 and y0 - CLEAR <= y <= y1 + CLEAR:
                return False
        return True

    def put(x, y, z, s):
        if math.hypot(x, z) < CORE:
            return
        cur = v.get(x, y, z)
        if cur is None or cur in ('air', 'cave_air'):
            v.set(x, y, z, s)

    # 形ごとの (上の端からの高さ, 下の端からの高さ): 置く中心 c に対して c + up 〜 c - down を使う
    SPAN = {'bone': (1, 10), 'salt': (8, 6), 'storm': (2, 2), 'ash': (2, 9)}
    PER_LAYER = {'bone': 14, 'salt': 16, 'storm': 6, 'ash': 14}
    for L in ctx.LAYERS:
        name, top, bottom = L[0], L[1], L[2]
        up, down = SPAN[name]
        placed = 0
        for k in range(200):
            if placed >= PER_LAYER[name]:
                break
            a = (k * 2.399 + hash(name) % 7) % (2 * math.pi)
            # この角度で道が通る高さ (層の中、上から)
            ys = sorted({y for th, y in walks if abs((th - a + math.pi) % (2 * math.pi) - math.pi) < 0.06 and bottom <= y <= top}, reverse=True)
            gaps = [(ys[n] + ys[n + 1]) // 2 for n in range(len(ys) - 1)]
            if not gaps:
                continue
            c = gaps[k % len(gaps)]
            # 道の帯 (半径 15.5〜19.5) を横切る所だけ、道の高さから離れているかを見る (芯へ垂れる所は道にかからない)
            if name == 'bone':
                ok = free(a, c + 1, c + 6)
            elif name == 'ash':
                ok = free(a, c - 5, c + 2)
            else:
                ok = free(a, c - down, c + up)
            if not ok:
                continue
            placed += 1
            if name == 'bone':
                rib(put, a, c + 5)
            elif name == 'salt':
                crystal(put, rng, a, c)
            elif name == 'storm':
                halo(put, c, free)
            elif name == 'ash':
                column(put, rng, a, c + 7)

def rib(put, a, y):
    """壁 (半径 20) から芯 (半径 6) へ、下へ垂れてから少し上がる骨の弧 (太さ 2)。先に鎖と灯り"""
    steps = 40
    for i in range(steps + 1):
        t = i / steps
        r = 20.5 - 14.5 * t
        h = y - 9 * math.sin(math.pi * t * 0.85)
        aa = a + 0.18 * math.sin(math.pi * t)
        cx, cz = math.cos(aa) * r, math.sin(aa) * r
        for dx in (0, 1):
            for dy in (0, 1):
                put(int(round(cx)) + dx * int(round(-math.sin(aa))), int(round(h)) + dy, int(round(cz)) + dx * int(round(math.cos(aa))), 'bone_block[axis=y]')
    tx, tz = int(round(math.cos(a) * 6)), int(round(math.sin(a) * 6))
    ty = int(round(y - 9 * math.sin(math.pi * 0.85))) - 1
    for d in range(1, 4):
        put(tx, ty - d, tz, 'chain[axis=y,waterlogged=false]')
    put(tx, ty - 4, tz, 'soul_lantern[hanging=true,waterlogged=false]')


def crystal(put, rng, a, y):
    """壁から芯へ斜め上に突き出す塩の柱の房 (3〜5 本)。芯に真珠色の光"""
    for n in range(rng.randint(3, 5)):
        da = (rng.random() - 0.5) * 0.5
        up = 0.3 + rng.random() * 0.5
        length = 8 + rng.random() * 7
        thick = 1.2 + rng.random() * 0.8
        aa = a + da
        y0 = y + rng.randint(-3, 3)
        for i in range(int(length * 2) + 1):
            s = i / 2
            r = 20.5 - s
            h = y0 + s * up
            cx, cz = math.cos(aa) * r, math.sin(aa) * r
            w = thick * (1 - s / (length + 1))
            for x in range(int(cx - 2), int(cx + 3)):
                for z in range(int(cz - 2), int(cz + 3)):
                    for yy in range(int(h - 2), int(h + 3)):
                        if math.hypot(x - cx, z - cz) ** 2 + (yy - h) ** 2 <= w * w:
                            core = s > 2 and s < length - 2 and math.hypot(x - cx, z - cz) < 0.6 and abs(yy - h) < 0.6
                            put(x, yy, z, 'pearlescent_froglight[axis=y]' if core and i % 5 == 0 else rng.choice(['calcite', 'quartz_block', 'calcite', 'white_stained_glass']))


def halo(put, y, free):
    """芯のまわりに浮く銅の輪 (半径 9〜10) と、壁から輪へ四本の銅の梁。輪の上に灯った電球と避雷針"""
    for i in range(0, 360, 3):
        a = math.radians(i)
        for r in (9.0, 9.7):
            put(int(round(math.cos(a) * r)), y, int(round(math.sin(a) * r)), 'waxed_oxidized_cut_copper')
        if i % 30 == 0:
            x, z = int(round(math.cos(a) * 9.4)), int(round(math.sin(a) * 9.4))
            put(x, y + 1, z, 'waxed_copper_bulb[lit=true,powered=false]')
            if i % 90 == 0:
                put(x, y + 2, z, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    for q in range(4):
        a = q * math.pi / 2 + math.pi / 4
        if not free(a, y - 1, y + 1):
            continue
        for r10 in range(100, 206):
            r = r10 / 10
            put(int(round(math.cos(a) * r)), y, int(round(math.sin(a) * r)), 'waxed_weathered_cut_copper')


def column(put, rng, a, y):
    """壁際から吹き抜けへ傾いて突き上げる玄武岩の柱。燠の筋 (マグマ) をところどころ"""
    r0 = 19.5
    lean = 0.25 + rng.random() * 0.25
    height = 8 + rng.randint(0, 2)
    for i in range(height):
        r = r0 - i * lean * 1.5
        cx, cz = math.cos(a) * r, math.sin(a) * r
        h = y - 12 + i
        for x in range(int(cx - 2), int(cx + 3)):
            for z in range(int(cz - 2), int(cz + 3)):
                if math.hypot(x - cx, z - cz) <= 1.6:
                    put(x, h, z, 'magma_block' if rng.random() < 0.08 else 'basalt[axis=y]')
    tx, tz = int(round(math.cos(a) * (r0 - height * lean * 1.5))), int(round(math.sin(a) * (r0 - height * lean * 1.5)))
    put(tx, y - 12 + height, tz, 'magma_block')
