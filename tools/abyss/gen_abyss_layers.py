# -*- coding: utf-8 -*-
"""深淵の層の大縦穴 (docs/design/36 の 2・3 の A2)。何もない空間 (ディメンション skyabyss:abyss) に、壁の筒・螺旋の道・層ごとの部屋だけを置く。

python -I tools/abyss/gen_abyss_layers.py  →  src/main/resources/data/skyabyss/opening/abyss_layers.json (runs 形式。原点 = 縦穴の中心、y はそのまま)

形:
  - 縦穴: 中心 (0, 0)、内の半径 R_IN = 20、壁の厚み 2。上端 y 240 の縁から、心臓の間の天井 y -250 まで。真ん中は底まで抜けた吹き抜け (落ちると下の層へ。落ちる高さでは傷)
  - 螺旋の道: 壁の内側に沿って幅 4 (半径 16〜19)。角度が増える向きに下りる。弧の長さ 2 で 1 段 (半ブロックで刻む)。内の縁に低い壁
  - 層: 骨 (240〜120)・塩 (120〜0)・雷 (0〜-120)・灰 (-120〜-240)。層ごとに壁の素材を替え、層の境に灯籠の休み場 (道を広げた踊り場。印 rest_<n>)
  - 部屋: 層ごとに 3 つ、壁の外へ張り出す箱 (溜まりの間 pool_<層>・前の私の野営 camp_<層>・巣 den_<層>)。道から戸の口で入る
  - 心臓の間: 縦穴の底の下に丸い大部屋 (半径 30、y -300〜-252)。真ん中に印 heart。道の最後は大部屋の縁に下りる
  - 着く所: 上端の縁の踊り場 (印 arrival)
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'capital'))
import vox  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss', 'opening', 'abyss_layers.json')

R_IN = 20          # 縦穴の内の面
WALL = 2           # 壁の厚み
PATH_IN = 15.5     # 道の内の縁
PATH_OUT = R_IN - 0.5
TOP = 240
HEART_TOP = -252
HEART_BOTTOM = -300
HEART_R = 30
LAYERS = [  # (名前, 上, 下, 壁, 床, 飾り)
    ('bone', 240, 120, ['bone_block[axis=y]', 'calcite', 'calcite', 'smooth_basalt'], 'polished_diorite', 'bone_block[axis=y]'),
    ('salt', 120, 0, ['calcite', 'white_terracotta', 'smooth_quartz', 'diorite'], 'smooth_quartz', 'prismarine_bricks'),
    ('storm', 0, -120, ['tuff', 'tuff_bricks', 'oxidized_cut_copper', 'polished_tuff'], 'polished_tuff', 'lightning_rod[facing=up,powered=false,waterlogged=false]'),
    ('ash', -120, -240, ['blackstone', 'basalt[axis=y]', 'polished_blackstone_bricks', 'cracked_polished_blackstone_bricks'], 'polished_blackstone', 'magma_block'),
]
HEART_WALL = ['deepslate_tiles', 'sculk', 'deepslate_tiles', 'crying_obsidian']
STEP_ARC = 2.0     # 弧の長さ 2 で半段 (0.5) 下りる → 1 周 (約 110) で 27 段ほど
LANTERN_EVERY = 6  # 道の内の縁の灯り (歩数)


def layer_of(y):
    for L in LAYERS:
        if L[2] < y <= L[1]:
            return L
    return LAYERS[-1]


def main():
    v = vox.Vox(seed=5)
    rng = v.rng

    def wall_block(y, x=0, z=0):
        """層ごとの柄 (まだらにしない)。骨: 縦の肋 (角度 10 度ごとに骨の柱、あいだは方解石、12 段ごとに玄武岩の帯)、
        塩: 横の地層 (高さで方解石・白い陶・石英の帯)、雷: 凝灰岩の煉瓦に 10 段ごとの錆びた銅の帯、灰: 玄武岩の柱と黒石"""
        name = layer_of(y)[0]
        a = math.degrees(math.atan2(z, x)) % 360
        if name == 'bone':
            if y % 12 == 0:
                return 'smooth_basalt'
            return 'bone_block[axis=y]' if a % 10 < 2.2 else 'calcite'
        if name == 'salt':
            band = (y // 4) % 5
            return ['calcite', 'white_terracotta', 'calcite', 'smooth_quartz', 'diorite'][band]
        if name == 'storm':
            if y % 10 == 0:
                return 'oxidized_cut_copper'
            return 'tuff_bricks' if (y // 2 + int(a // 8)) % 2 else 'polished_tuff'
        if name == 'ash':
            if a % 12 < 2.5:
                return 'basalt[axis=y]'
            return 'cracked_polished_blackstone_bricks' if rng.random() < 0.12 else 'polished_blackstone_bricks'
        return rng.choice(layer_of(y)[3])

    # ---- 縦穴の壁の筒
    r_out = R_IN + WALL
    for x in range(-r_out - 1, r_out + 2):
        for z in range(-r_out - 1, r_out + 2):
            d = math.hypot(x, z)
            if R_IN - 0.5 < d <= r_out + 0.5:
                for y in range(HEART_TOP, TOP + 1):
                    v.set(x, y, z, wall_block(y, x, z))
    # 上端の縁 (着く所の踊り場をまわりに張り出す)
    for x in range(-r_out - 6, r_out + 7):
        for z in range(-r_out - 6, r_out + 7):
            d = math.hypot(x, z)
            if R_IN - 0.5 < d <= r_out + 6.5:
                v.set(x, TOP, z, 'polished_deepslate')
                if d > r_out + 5.5:
                    v.set(x, TOP + 1, z, 'deepslate_brick_wall')
    # ---- 螺旋の道 (半ブロックの段)。角度 0 (東) から
    s = 0.0
    y2 = (TOP + 1) * 2      # 半段の単位 (y2 / 2 = 歩く面の高さ)。最初は縁の上面 (TOP + 1) と同じ
    rests = {}
    layer_index = 0
    flat_left = 0
    step_i = 0
    path = []               # (θ, y2)
    while y2 > (HEART_BOTTOM + 1) * 2:  # 心臓の間の中も同じ螺旋で床まで下りる
        th = s / ((PATH_IN + PATH_OUT) / 2)
        path.append((th, y2))
        y = y2 // 2
        # 層の境で休み場 (10 歩平ら)
        if layer_index < len(LAYERS) - 1 and y <= LAYERS[layer_index][2] and flat_left == 0 and ('rest_%d' % (layer_index + 1)) not in rests:
            flat_left = 10
            rests['rest_%d' % (layer_index + 1)] = (th, (y2 + 1) // 2)
            layer_index += 1
        if flat_left > 0:
            flat_left -= 1
        else:
            y2 -= 1
        s += STEP_ARC
        step_i += 1
    # 道のマス: 角度ごとに、半径 PATH_IN〜PATH_OUT のマスへ (半段 = 下の半ブロック、整数 = 丸ごと)
    for i, (th, h2) in enumerate(path):
        # 歩く面の高さ = h2 / 2。偶数なら丸ごとのブロック (上面 = h2/2)、奇数なら下の半ブロック (上面 = h2/2)
        half = h2 % 2 == 1
        block_y = h2 // 2 if half else h2 // 2 - 1
        y = block_y + 1          # 立つ高さ
        for x in range(-R_IN, R_IN + 1):
            for z in range(-R_IN, R_IN + 1):
                d = math.hypot(x, z)
                if not (PATH_IN <= d <= PATH_OUT):
                    continue
                a = math.atan2(z, x)
                diff = (a - th + math.pi) % (2 * math.pi) - math.pi
                if abs(diff) * d > 1.05:
                    continue
                floor = layer_of(y)[4]
                v.set(x, block_y - 1, z, wall_block(y, x, z))
                v.set(x, block_y, z, slab_of(floor) if half else floor)
                for yy in range(block_y + 1, block_y + 4):
                    if v.get(x, yy, z) is None:
                        v.set(x, yy, z, 'air')
                if d < PATH_IN + 0.9:
                    v.set(x, block_y + 1, z, wall_of(floor))
                    if i % LANTERN_EVERY == 0:
                        v.set(x, block_y + 2, z, 'soul_lantern[hanging=false,waterlogged=false]')
    # ---- 休み場: 道の幅を内へ 4 広げ、灯籠と篝火
    for name, (th, y) in rests.items():
        cx, cz = math.cos(th) * 14, math.sin(th) * 14
        for x in range(int(cx) - 5, int(cx) + 6):
            for z in range(int(cz) - 5, int(cz) + 6):
                d = math.hypot(x, z)
                if 11.5 <= d <= PATH_OUT and math.hypot(x - cx, z - cz) <= 5.5:
                    # y = 立つ高さ。床は一つ下
                    v.set(x, y - 1, z, 'polished_deepslate')
                    v.set(x, y - 2, z, 'deepslate_tiles')
                    for yy in range(y, y + 3):
                        if v.get(x, yy, z) not in (None, 'air') and 'lantern' not in v.get(x, yy, z) and 'wall' not in v.get(x, yy, z):
                            v.set(x, yy, z, 'air')
                    if d < 12.4:
                        v.set(x, y, z, 'deepslate_brick_wall')
        lx, lz = int(round(math.cos(th) * 15)), int(round(math.sin(th) * 15))
        v.set(lx, y, lz, 'soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
        v.mark(name, lx, y, lz)
    # ---- 層ごとの部屋 (壁の外へ張り出す箱)。角度は層の中ほどの道の上
    for li, L in enumerate(LAYERS):
        name, top, bottom = L[0], L[1], L[2]
        span = [p for p in path if bottom < p[1] // 2 <= top]
        if not span:
            continue
        for k, kind in enumerate(('pool', 'camp', 'den')):
            th, h2 = span[int(len(span) * (0.2 + 0.3 * k))]
            room(v, name, kind, th, (h2 + 1) // 2, L)
    # ---- 心臓の間 (縦穴の底の下の丸い大部屋)
    for x in range(-HEART_R - 1, HEART_R + 2):
        for z in range(-HEART_R - 1, HEART_R + 2):
            d = math.hypot(x, z)
            if d > HEART_R + 0.5:
                continue
            for y in range(HEART_BOTTOM, HEART_TOP + 1):
                dome = HEART_TOP - (HEART_TOP - HEART_BOTTOM) * 0.0
                if y == HEART_BOTTOM or d > HEART_R - 0.5:
                    v.set(x, y, z, rng.choice(HEART_WALL))
                elif y == HEART_TOP and d > R_IN + 0.5:
                    v.set(x, y, z, rng.choice(HEART_WALL))
    # 心臓の台と、まわりの灯籠 (番人の戦いで点ける灯り。点けた所だけ姿が見える)
    for x in range(-4, 5):
        for z in range(-4, 5):
            if math.hypot(x, z) <= 4.5:
                v.set(x, HEART_BOTTOM + 1, z, 'sculk_catalyst' if (x, z) == (0, 0) else 'reinforced_deepslate')
    for i in range(8):
        a = i * math.pi / 4
        lx, lz = int(round(math.cos(a) * 18)), int(round(math.sin(a) * 18))
        v.set(lx, HEART_BOTTOM + 1, lz, 'polished_deepslate')
        v.set(lx, HEART_BOTTOM + 2, lz, 'polished_deepslate_wall')
        v.mark('beacon_%d' % i, lx, HEART_BOTTOM + 3, lz)
    v.mark('heart', 0, HEART_BOTTOM + 2, 0)
    v.mark('arrival', R_IN + 4, TOP + 1, 0)
    ext = HEART_R + 2
    meta = {'name': 'abyss_layers', 'note': 'docs/design/36 A2。生成は tools/abyss/gen_abyss_layers.py', 'top': TOP, 'bottom': HEART_BOTTOM,
            'rect': [-ext, -ext, ext, ext]}
    data = v.runs(meta)
    vox.save(OUT, data)
    n = len(v.b)
    print('wrote', os.path.relpath(OUT, ROOT), n, 'blocks', len(data['palette']), 'states', 'marks', sorted(v.marks))


def slab_of(floor):
    stem = floor.split('[')[0]
    table = {'polished_diorite': 'polished_diorite_slab', 'smooth_quartz': 'smooth_quartz_slab', 'polished_tuff': 'polished_tuff_slab',
             'polished_blackstone': 'polished_blackstone_slab'}
    return table.get(stem, 'polished_deepslate_slab') + '[type=bottom,waterlogged=false]'


def wall_of(floor):
    stem = floor.split('[')[0]
    table = {'polished_diorite': 'diorite_wall', 'smooth_quartz': 'diorite_wall', 'polished_tuff': 'polished_tuff_wall',
             'polished_blackstone': 'polished_blackstone_wall'}
    return table.get(stem, 'deepslate_brick_wall')


def room(v, layer, kind, th, y, L):
    """壁の外へ張り出す箱の部屋 (幅 9、奥行き 9、高さ 6)。道の外の縁から戸の口 (幅 3、高さ 3)"""
    rng = v.rng
    ox, oz = math.cos(th), math.sin(th)
    cx, cz = ox * (R_IN + WALL + 5), oz * (R_IN + WALL + 5)
    icx, icz = int(round(cx)), int(round(cz))
    for x in range(icx - 5, icx + 6):
        for z in range(icz - 5, icz + 6):
            if math.hypot(x, z) <= R_IN + WALL - 0.5:
                continue
            for yy in range(y - 1, y + 6):
                edge = x in (icx - 5, icx + 5) or z in (icz - 5, icz + 5) or yy in (y - 1, y + 5)
                v.set(x, yy, z, rng.choice(L[3]) if edge else 'air')
            v.set(x, y - 1, z, L[4])
    # 戸の口: 道の外の縁から部屋まで
    for k in range(int(PATH_OUT) - 1, R_IN + WALL + 2):
        for w in (-1, 0, 1):
            px = int(round(ox * k - oz * w))
            pz = int(round(oz * k + ox * w))
            if math.hypot(px, pz) < PATH_IN + 1:
                continue
            v.set(px, y - 1, pz, L[4])
            for yy in range(y, y + 3):
                v.set(px, yy, pz, 'air')
    # 中身
    if kind == 'pool':
        # 溜まりの間: 返った鼓動が溜まる池 (真ん中に光る台)。深淵術を覚える所
        for x in range(icx - 2, icx + 3):
            for z in range(icz - 2, icz + 3):
                v.set(x, y - 1, z, 'sculk')
        v.set(icx, y, icz, 'sculk_shrieker')
        v.set(icx, y + 4, icz, 'soul_lantern[hanging=true,waterlogged=false]')
        v.set(icx, y + 4 + 1, icz, L[3][0])
    elif kind == 'camp':
        # 前の私の野営: 寝床・消えた焚き火・帳面・荷
        v.set(icx - 3, y, icz - 3, 'white_wool')
        v.set(icx - 3, y, icz - 2, 'white_carpet')
        v.set(icx, y, icz, 'campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]')
        v.set(icx + 3, y, icz - 3, 'lectern[facing=west,has_book=true,powered=false]')
        v.set(icx + 3, y, icz + 3, 'barrel[facing=up,open=false]')
        v.set(icx - 3, y + 4, icz + 3, 'soul_lantern[hanging=true,waterlogged=false]')
    else:
        # 巣: 骨や殻の山、暗い (灯りなし)
        for _ in range(10):
            v.set(icx + rng.randint(-3, 3), y, icz + rng.randint(-3, 3), L[5] if '[' in L[5] or L[5] else 'cobweb')
        v.set(icx, y, icz, 'cobweb')
    v.mark('%s_%s' % (kind, layer), icx, y, icz)


if __name__ == '__main__':
    main()
