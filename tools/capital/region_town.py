# -*- coding: utf-8 -*-
"""土地の町 (鯨見・潮・風・灰の町) の設計図を、都と同じ部品集 (vox.py・town.Town・facilities) で書く。
2026-10-26 の声「始まりの町とそれ以外の町はクオリティ違いすぎる」「建築もへぼいし。小さいし。看板だし」:
都と同じ作りの、壁と門のある町にする (壁の内 101 四方。都は 195)。どの町にも:
  - 四つの門 (門塔と楼、外へ延びる道と灯り)、十字の大通り、真ん中の広場 (土地の飾り・依頼の板・腰掛け・井戸)
  - 宿 (一階 = 受付と食堂と暖炉、二階 = 客室と寝台。宿の主人)、鍛冶場 (炉・金床・鍛冶師。誂えの店)、道具屋、薬師の店、小さな社
  - 街区の町家 (大通りに面する家は入れる家)、庭・木・花壇・灯り
  - 土地の見どころ: 鯨見 = 見張りの塔と鯨骨の門 / 潮 = 灯台と魚の市 / 風 = 風車と避雷の塔 / 灰 = 溶鉱の煙突と熾火の炉
印は前の町と同じ名前 (rest・board・inn・shop・stand・sign・folk_a・folk_b・inn_bed・npc_<役>・npc_chaser) に、
鍛冶師・薬師 (npc_smith・npc_apothecary) と浮く名札 (label_<施設>) を足した。

python -I tools/capital/region_town.py [whale|tide|storm|ember ...]  → src/main/resources/data/skyabyss/opening/town_<土地>.json と
docs/review/town_roughs/town_<土地>_plan.png
座標: 中心 (0, 0)、+z が南、y=0 が地面の上の最初の段。回さない (SiteFinder は町を図の場所に向きそのまま置く)。
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vox  # noqa: E402
import town as T  # noqa: E402
from vox import Vox, Body, Mat, Style, DX, OPP, gable_roof, tower, fountain, rosette, pave, flowerbed, lamp_post, double_lamp, tree, bench, planter  # noqa: E402

IN = 50          # 壁の内の面
W = 53           # 壁の外の面
H = 9            # 壁の高さ
ROAD = 3         # 大通りの半幅 (x -3..3)


def _style(src, key, **kw):
    d = dict(src.__dict__)
    d.update(kw)
    d.pop('key', None)
    return Style(key, **d)


# ---------------------------------------------------------------- 土地の作風 (都の B「灰石と緋の屋根」をもとに、土地の石と屋根)
STYLES = {
    # 鯨見の町: 苔むした灰石と骨の白、濃い瓦。丘の上の見張りの町
    'whale': _style(vox.STYLE_B, 'whale',
                    base=Mat('mossy_stone_bricks', 'mossy_stone_brick', 'mossy_stone_brick_wall'), base2=['mossy_cobblestone', 'cracked_stone_bricks'],
                    wall=Mat('stone_bricks', 'stone_brick', 'stone_brick_wall'), trim=Mat('polished_diorite', 'polished_diorite', 'diorite_wall'),
                    trim2='bone_block[axis=y]', pillar='bone_block[axis=y]',
                    roof=Mat('deepslate_tiles', 'deepslate_tile', 'deepslate_tile_wall'), roof_edge=Mat('polished_diorite', 'polished_diorite'),
                    accent=Mat('mossy_cobblestone', 'mossy_cobblestone', 'mossy_cobblestone_wall'), accent2='moss_block',
                    wood='spruce', log='spruce_log', dark='dark_oak', glass='glass_pane', frame='spruce_trapdoor', fence='spruce_fence',
                    infill='stone_bricks', infill_mix=['mossy_stone_bricks', 'cracked_stone_bricks', 'andesite'], infill_var=0.18,
                    band='polished_andesite', pilaster='bone_block[axis=y]', corner='polished_andesite', shutter_wood='spruce', awning='spruce',
                    house_infills=['stone_bricks', 'calcite', 'mossy_stone_bricks', 'polished_andesite'],
                    roof_alts=[Mat('mossy_cobblestone', 'mossy_cobblestone', 'mossy_cobblestone_wall'), Mat('dark_oak_planks', 'dark_oak'), Mat('spruce_planks', 'spruce')],
                    wall_main='stone_bricks', wall_mix2=['mossy_stone_bricks', 'cracked_stone_bricks', 'mossy_cobblestone'],
                    pave=[('stone_bricks', 3), ('mossy_stone_bricks', 3), ('andesite', 2), ('mossy_cobblestone', 2), ('moss_block', 1)],
                    pave_trim='polished_andesite', pave_inlay=['bone_block[axis=y]', 'calcite'],
                    flowers=['cornflower', 'azure_bluet', 'oxeye_daisy', 'lily_of_the_valley', 'fern'], leaves='spruce_leaves', trunk='spruce_log',
                    leaves2='birch_leaves', trunk2='birch_log', banner_color='light_gray', fence_rail='spruce_fence',
                    spire=Mat('deepslate_tiles', 'deepslate_tile', 'deepslate_tile_wall')),
    # 潮の町: 白い砂岩と海の青緑の瓦、真珠の白。浜の町
    'tide': _style(vox.STYLE_C, 'tide',
                   roof=Mat('dark_prismarine', 'dark_prismarine'), roof_edge=Mat('smooth_quartz', 'smooth_quartz'),
                   accent=Mat('prismarine_bricks', 'prismarine_brick'), accent2='sea_lantern',
                   wood='birch', log='stripped_birch_log', dark='spruce', glass='light_blue_stained_glass_pane', fence='birch_fence',
                   house_infills=['smooth_sandstone', 'white_terracotta', 'calcite', 'light_blue_terracotta'],
                   roof_alts=[Mat('prismarine_bricks', 'prismarine_brick'), Mat('smooth_sandstone', 'smooth_sandstone')],
                   pave=[('smooth_sandstone', 4), ('cut_sandstone', 2), ('sandstone', 2), ('prismarine_bricks', 1)],
                   flowers=['blue_orchid', 'azure_bluet', 'white_tulip', 'oxeye_daisy', 'pink_tulip'], banner_color='cyan',
                   spire=Mat('dark_prismarine', 'dark_prismarine')),
    # 風の町: 凝灰岩と灰色の石、緑青の銅の屋根、濃い木の梁。高地の風の町
    'storm': _style(vox.STYLE_B, 'storm',
                    base=Mat('tuff_bricks', 'tuff_brick', 'tuff_brick_wall'), base2=['chiseled_tuff', 'polished_tuff'],
                    wall=Mat('polished_tuff', 'polished_tuff', 'polished_tuff_wall'), trim=Mat('polished_andesite', 'polished_andesite', 'andesite_wall'),
                    trim2='chiseled_tuff_bricks', pillar='spruce_log[axis=y]',
                    roof=Mat('weathered_cut_copper', 'weathered_cut_copper'), roof_edge=Mat('polished_andesite', 'polished_andesite'),
                    accent=Mat('oxidized_cut_copper', 'oxidized_cut_copper'), accent2='lightning_rod[facing=up,powered=false,waterlogged=false]',
                    wood='spruce', log='spruce_log', dark='dark_oak', glass='glass_pane', frame='spruce_trapdoor', fence='spruce_fence',
                    infill='tuff_bricks', infill_mix=['polished_tuff', 'stone_bricks'], infill_var=0.12,
                    band='smooth_stone', pilaster='spruce_log[axis=y]', corner='polished_tuff', shutter_wood='spruce', awning='spruce',
                    house_infills=['tuff_bricks', 'polished_tuff', 'stone_bricks', 'calcite'],
                    roof_alts=[Mat('oxidized_cut_copper', 'oxidized_cut_copper'), Mat('cut_copper', 'cut_copper')],
                    wall_main='tuff_bricks', wall_mix2=['polished_tuff', 'chiseled_tuff', 'stone_bricks'],
                    pave=[('polished_tuff', 3), ('tuff_bricks', 3), ('stone_bricks', 2), ('andesite', 2)],
                    pave_trim='smooth_stone', pave_inlay=['cut_copper', 'polished_tuff'],
                    flowers=['dandelion', 'oxeye_daisy', 'azure_bluet', 'cornflower'], banner_color='yellow', fence_rail='spruce_fence',
                    spire=Mat('weathered_cut_copper', 'weathered_cut_copper')),
    # 灰の町: 黒い石 (ブラックストーン) と煤けた煉瓦、焼けた赤い屋根。鍛冶の町
    'ember': _style(vox.STYLE_B, 'ember',
                    base=Mat('polished_blackstone_bricks', 'polished_blackstone_brick', 'polished_blackstone_brick_wall'),
                    base2=['cracked_polished_blackstone_bricks', 'blackstone'],
                    wall=Mat('bricks', 'brick', 'brick_wall'), trim=Mat('polished_blackstone', 'polished_blackstone', 'polished_blackstone_wall'),
                    trim2='chiseled_polished_blackstone', pillar='basalt[axis=y]',
                    roof=Mat('nether_bricks', 'nether_brick', 'nether_brick_wall'), roof_edge=Mat('polished_blackstone', 'polished_blackstone'),
                    accent=Mat('red_nether_bricks', 'red_nether_brick', 'red_nether_brick_wall'), accent2='magma_block',
                    wood='dark_oak', log='dark_oak_log', dark='dark_oak', glass='orange_stained_glass_pane', frame='dark_oak_trapdoor', fence='dark_oak_fence',
                    infill='bricks', infill_mix=['mud_bricks', 'polished_blackstone_bricks'], infill_var=0.16,
                    band='polished_blackstone', pilaster='basalt[axis=y]', corner='polished_blackstone', shutter_wood='dark_oak', awning='dark_oak',
                    house_infills=['bricks', 'mud_bricks', 'polished_blackstone_bricks', 'packed_mud'],
                    roof_alts=[Mat('red_nether_bricks', 'red_nether_brick', 'red_nether_brick_wall'), Mat('mud_bricks', 'mud_brick', 'mud_brick_wall')],
                    wall_main='polished_blackstone_bricks', wall_mix2=['cracked_polished_blackstone_bricks', 'blackstone', 'basalt[axis=y]'],
                    pave=[('polished_blackstone_bricks', 3), ('blackstone', 3), ('basalt[axis=y]', 1), ('polished_basalt[axis=y]', 1), ('coarse_dirt', 1)],
                    pave_trim='polished_blackstone', pave_inlay=['magma_block', 'polished_blackstone'],
                    flowers=['red_tulip', 'orange_tulip', 'poppy', 'dead_bush'], leaves='dark_oak_leaves', trunk='dark_oak_log',
                    leaves2='acacia_leaves', trunk2='acacia_log', banner_color='orange', lamp='lantern', fence_rail='dark_oak_fence',
                    spire=Mat('nether_bricks', 'nether_brick', 'nether_brick_wall')),
}
NAMES = {'whale': '鯨見の町', 'tide': '潮の町', 'storm': '風の町', 'ember': '灰の町'}
SEEDS = {'whale': 31001, 'tide': 31002, 'storm': 31003, 'ember': 31004}


class RegionTown(T.Town):
    def __init__(self, region):
        super().__init__(STYLES[region])
        self.region = region
        self.v = Vox(seed=SEEDS[region])
        self.rng = self.v.rng

    # ---------------------------------------------------------------- 地面と通り
    def ground(self):
        st, v = self.st, self.v
        pave(v, st, -IN, -IN, IN, IN, y=-1)
        for i in range(-IN, IN + 1):
            for k in (-ROAD - 1, ROAD):
                v.set(k, -1, i, st.pave_trim)
                v.set(i, -1, k, st.pave_trim)
            if i % 6 == 0:
                for k in (-1, 0):
                    v.set(k, -1, i, st.pave_inlay[0])
                    v.set(i, -1, k, st.pave_inlay[0])

    # ---------------------------------------------------------------- 壁・塔・門
    def walls(self):
        st, v = self.st, self.v
        T_ = 2
        for (ax0, az0, ax1, az1) in ((-W, -W, W, -W + T_), (-W, W - T_, W, W), (-W, -W, -W + T_, W), (W - T_, -W, W, W)):
            v.fill(ax0, -1, az0, ax1, -1, az1, st.base.full)
            for x in range(ax0, ax1 + 1):
                for z in range(az0, az1 + 1):
                    for y in range(0, H + 1):
                        v.set(x, y, z, st.base.full if y < 2 else (st.wall_main if self.rng.random() > 0.14 else self.rng.choice(st.wall_mix2)))
        for i in range(-W, W + 1):
            for (x, z, out) in ((i, -W, 'north'), (i, W, 'south'), (-W, i, 'west'), (W, i, 'east')):
                v.set(x, H + 1, z, st.trim.wall() if i % 2 == 0 else st.trim.slab('bottom'))
                dx, dz = DX[out]
                v.set(x + dx, H, z + dz, st.trim.stairs(OPP[out], 'top'), keep=True)
                v.set(x + dx, 0, z + dz, st.base.stairs(OPP[out], 'bottom'), keep=True)
                if i % 9 == 0 and abs(i) < W - 6 and abs(i) > ROAD + 6:
                    for y in range(0, H - 1):
                        v.set(x + dx, y, z + dz, st.base.full if y < 2 else st.pilaster)
                    v.set(x + dx, H - 1, z + dz, st.trim.stairs(OPP[out], 'top'))
            for (x, z) in ((i, -W + T_), (i, W - T_), (-W + T_, i), (W - T_, i)):
                if -W + T_ <= x <= W - T_ and -W + T_ <= z <= W - T_:
                    v.set(x, H + 1, z, st.railing)
        for i in range(-W + 9, W - 8, 18):
            for (x, z) in ((i, -W + 1), (i, W - 1), (-W + 1, i), (W - 1, i)):
                if abs(i) > ROAD + 6:
                    v.set(x, H + 1, z, 'lantern[hanging=false,waterlogged=false]')
        for cx in (-W - 1, W + 1):
            for cz in (-W - 1, W + 1):
                tower(v, st, cx, cz, half=4, y0=0, h=17, belfry=False, spire_rise=2, pinnacles=True, sealed=True)
        for k in (-28, 28):
            for (cx, cz) in ((k, -W - 1), (k, W + 1), (-W - 1, k), (W + 1, k)):
                tower(v, st, cx, cz, half=3, y0=0, h=13, belfry=False, spire_rise=2, pinnacles=False, sealed=True)
        for side in ('north', 'south', 'west', 'east'):
            self.region_gate(side)

    def region_gate(self, side):
        """門 (北で書いて回す): 門塔 2 本、幅 7 の口と楼、外へ 14 の道と灯り"""
        st = self.st
        g = Vox()
        g.rng = self.rng
        for xt in (-7, 7):
            tower(g, st, xt, -W + 1, half=3, y0=0, h=15, belfry=False, spire_rise=3, pinnacles=True, sealed=True)
        b = Body(g, st, -4, -W - 2, 4, -W + 3, 0, floors=1, fh=7, bay=3, plinth=2, door_side='none', interior=False,
                 sides=('north', 'south'), pots=0)
        b.build()
        gable_roof(g, st, -4, -W - 2, 4, -W + 3, b.top + 1, axis='x', steep=True, finial=True)
        g.seal(-3, -W - 1, 3, -W + 2, 0)
        g.clear(-3, 0, -W - 3, 3, 4, -W + 4)
        for z in (-W - 2, -W + 3):
            g.set(-3, 4, z, st.trim.stairs('west', 'top'))
            g.set(3, 4, z, st.trim.stairs('east', 'top'))
            for x in range(-2, 3):
                g.set(x, 5, z, st.trim.slab('top'))
            g.set(0, 5, z, st.trim2)
        g.fill(-3, -1, -W - 4, 3, -1, -W + 4, st.pave_trim)
        for x in (-4, 4):
            g.set(x, 3, -W - 3, 'lantern[hanging=false,waterlogged=false]')
            g.set(x, 3, -W + 4, 'lantern[hanging=false,waterlogged=false]')
        for x in (-8, -6, 6, 8):
            g.set(x, 9, -W - 3, '%s_wall_banner[facing=north]' % st.banner_color)
            g.set(x, 9, -W + 5, '%s_wall_banner[facing=south]' % st.banner_color)
        for z in range(-W - 16, -W - 3):
            for x in range(-3, 4):
                g.set(x, -1, z, st.pave_trim if abs(x) == 3 else self.v.pick(st.pave))
        for z in (-W - 8, -W - 14):
            for x in (-5, 5):
                g.set(x, 0, z, st.base.full)
                g.fill(x, 1, z, x, 2, z, st.fence)
                g.set(x, 3, z, 'lantern[hanging=false,waterlogged=false]')
        rot = {'north': 0, 'east': 1, 'south': 2, 'west': 3}[side]
        for (x, y, z), s in g.b.items():
            X, Z, S = T._rot(x, z, s, rot)
            self.v.set(X, y, Z, S)

    # ---------------------------------------------------------------- 広場
    def plaza(self):
        st, v = self.st, self.v
        R = 11
        rosette(v, st, 0, 0, R, y=-1)
        # 四隅の植え込みと腰掛け
        for sx in (1, -1):
            for sz in (1, -1):
                cx, cz = sx * 9, sz * 9
                flowerbed(v, st, cx - 1, cz - 1, cx + 1, cz + 1, y=0, edge=True, density=0.7)
                tree(v, cx, 0, cz, st.trunk, st.leaves, h=5, r=2)
                bench(v, st, cx - sx * 3, 0, cz, 'east' if sx < 0 else 'west', length=2)
        # 依頼の板 (北西の角の手前): 柵の脚に板と額
        bx, bz = -6, -8
        for x in range(bx - 1, bx + 2):
            v.set(x, 0, bz, st.fence)
            v.set(x, 1, bz, '%s_planks' % st.wood)
            v.set(x, 2, bz, '%s_planks' % st.wood)
            v.set(x, 1, bz + 1, '%s_wall_sign[facing=south,waterlogged=false]' % st.wood)
        v.set(bx, 3, bz, '%s_slab[type=bottom,waterlogged=false]' % st.wood)
        v.set(bx - 1, 3, bz, '%s_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]' % st.wood)
        v.set(bx + 1, 3, bz, '%s_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]' % st.wood)
        self.mark('board', bx, 0, bz + 2)
        # 町の札 (南の門から入って見える、広場の南の縁)
        v.set(5, 0, 12, '%s_sign[rotation=0,waterlogged=false]' % st.wood)
        self.mark('sign', 5, 0, 12)
        self.mark('stand', 0, 0, 8)
        self.mark('rest', 6, 0, 6)
        self.mark('folk_a', -7, 0, 6)
        self.mark('folk_b', 7, 0, -6)
        self.claim(-R, -R, R, R, 'plaza')

    # ---------------------------------------------------------------- 施設 (都の facilities の作りを、町の場所へ)
    def town_inn(self, x0, z0, x1, z1):
        """宿 (戸は南)。一階: 受付 (宿の主人)・食堂の長机・暖炉・酒の台。二階: 廊下の両側に客室 (寝台、印 inn_bed)"""
        st, v = self.st, self.v
        w = st.wood
        b, ridge = self.fac_body('inn', x0, z0, x1, z1, 'south', floors=2, fh=6, ground='shop', axis='x', dormers=2)
        vox.timber_upper(v, st, b)
        self.fac_front(b, 'inn')
        dx_, _, dz_, _ = b.doors[0]
        self.mark('inn', dx_, 0, dz_ + 1)
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2
        self.room(ax0, az0, ax1, az1, 0, 6, rug=False)
        # 受付 (戸の東、L 字の机)
        cx = dx_ + 3
        self.counter([(cx, z) for z in range(az0 + 3, az1 - 1)] + [(x, az0 + 3) for x in range(cx, cx + 4)],
                     tops=((cx, az0 + 5, 'bell[attachment=floor,facing=west,powered=false]'), (cx, az1 - 3, 'lantern[hanging=false,waterlogged=false]')))
        for x in range(cx + 1, min(cx + 4, ax1)):
            for z in range(az0 + 4, az1 - 1):
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
        self.attendant('innkeeper', cx + 2, 0, az0 + 6)
        # 食堂 (戸の西): 長机二つ、暖炉 (西の壁)、酒の台
        for (tx, tz) in ((ax0 + 3, az0 + 3), (ax0 + 3, az0 + 7)):
            self.table(tx, 0, tz, length=4, axis='x')
        fz = (az0 + az1) // 2
        for yy in range(0, 6):
            v.set(ax0, yy, fz - 1, 'bricks')
            v.set(ax0, yy, fz + 1, 'bricks')
            if yy >= 2:
                v.set(ax0, yy, fz, 'bricks')
        v.set(ax0, 0, fz, 'campfire[facing=east,lit=true,signal_fire=false,waterlogged=false]')
        v.set(ax0, 1, fz, 'air')
        for z in (fz - 1, fz, fz + 1):
            v.set(ax0 + 1, 2, z, 'brick_stairs[facing=west,half=top,shape=straight,waterlogged=false]')
        for x in range(ax0, ax0 + 5):
            v.set(x, 0, az0, 'barrel[facing=south,open=false]')
            v.set(x, 1, az0, 'barrel[facing=south,open=false]' if x % 2 else 'air')
        # 二階への階段 (北の壁ぞい、東へ上る) と客室
        self.room(ax0, az0, ax1, az1, 7, 5, rug=False, shelves=False, beams=False, wall_props=False)
        sx0 = ax1 - 9
        cleared = self.stair_run(sx0, az0, 0, 7, 'east', width=2)
        self.rail_hole([(x, z) for (x, yy, z) in cleared if yy == 6], 6, skip={(sx0 + 7, az0), (sx0 + 7, az0 + 1)})
        y2 = 7
        mid = (az0 + az1) // 2 + 1
        colors = ['red', 'blue', 'green', 'yellow', 'cyan', 'purple']
        n_bed = 0
        rooms_x = list(range(ax0, ax1 - 11, 5))
        for i, rx in enumerate(rooms_x):
            # 南の客室 (廊下 mid の南): 仕切り板、戸、寝台・箱・灯り
            for z in range(mid + 1, az1 + 1):
                for yy in range(y2, y2 + 4):
                    v.set(rx, yy, z, '%s_planks' % w)
            for yy in range(y2, y2 + 4):
                for x in range(rx, rx + 5):
                    if x != rx + 2:
                        v.set(x, yy, mid + 1, '%s_planks' % w)
            v.set(rx + 2, y2, mid + 1, '%s_door[facing=south,half=lower,hinge=left,open=false,powered=false]' % w)
            v.set(rx + 2, y2 + 1, mid + 1, '%s_door[facing=south,half=upper,hinge=left,open=false,powered=false]' % w)
            c = colors[i % len(colors)]
            v.set(rx + 1, y2, az1, '%s_bed[facing=south,occupied=false,part=head]' % c)
            v.set(rx + 1, y2, az1 - 1, '%s_bed[facing=south,occupied=false,part=foot]' % c)
            self.mark('inn_bed' if n_bed == 0 else 'inn_bed_%d' % (n_bed + 1), rx + 1, y2, az1)
            n_bed += 1
            v.set(rx + 3, y2, az1, 'chest[facing=north,type=single,waterlogged=false]')
            v.set(rx + 3, y2 + 1, az1, 'lantern[hanging=false,waterlogged=false]')
            v.set(rx + 3, y2, mid + 3, '%s_carpet' % c)
        for x in range(ax0, ax1 + 1):
            if v.get(x, y2, mid) in (None, 'air'):
                v.set(x, y2, mid, 'red_carpet')
        v.set(ax1 - 4, y2 + 4, mid, 'lantern[hanging=true,waterlogged=false]')
        v.set(ax0 + 3, y2 + 4, mid, 'lantern[hanging=true,waterlogged=false]')

    def town_smithy(self, x0, z0, x1, z1):
        """鍛冶場 (戸は南。一階建ての高い炉の間)。炉 (溶鉱炉と火の床と煙突)、金床・砥石・鍛冶台、鍛冶師の机 (誂えの店)"""
        st, v = self.st, self.v
        b, ridge = self.fac_body('smithy', x0, z0, x1, z1, 'south', floors=1, fh=8, ground='shop', axis='x', dormers=0,
                                 chimney=(x0 + 3, z0 + 3))
        self.fac_front(b, 'smithy')
        dx_, _, dz_, _ = b.doors[0]
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2
        self.room(ax0, az0, ax1, az1, 0, 8, rug=False, shelves=False)
        # 炉 (北西の隅): 溶鉱炉二つ、熾火の床、上は煉瓦の覆い
        for x in range(ax0, ax0 + 4):
            for z in range(az0, az0 + 3):
                v.set(x, -1, z, 'bricks')
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
        v.set(ax0, 0, az0, 'blast_furnace[facing=south,lit=true]')
        v.set(ax0 + 3, 0, az0, 'blast_furnace[facing=south,lit=true]')
        v.set(ax0 + 1, 0, az0, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
        v.set(ax0 + 2, 0, az0, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
        for x in range(ax0, ax0 + 4):
            for yy in range(3, 8):
                v.set(x, yy, az0, 'bricks')
            v.set(x, 2, az0 + 1, 'brick_stairs[facing=north,half=top,shape=straight,waterlogged=false]')
        v.set(ax0 + 1, 1, az0 + 2, 'water_cauldron[level=3]')
        # 金床・砥石・鍛冶台・刃の棚
        v.set(ax0 + 6, 0, az0 + 2, 'anvil[facing=east]')
        v.set(ax0 + 6, 0, az0 + 4, 'chipped_anvil[facing=east]')
        v.set(ax0 + 8, 0, az0 + 2, 'grindstone[face=floor,facing=north]')
        v.set(ax0 + 8, 0, az0 + 4, 'smithing_table')
        for z in range(az0, az1 - 2):
            v.set(ax1, 0, z, 'barrel[facing=west,open=false]' if z % 2 else 'chest[facing=west,type=single,waterlogged=false]')
            v.set(ax1, 2, z, 'lantern[hanging=false,waterlogged=false]' if z % 3 == 0 else 'air')
        # 鍛冶師の机 (戸の東)
        cx = dx_ + 3
        self.counter([(cx, z) for z in range(az1 - 4, az1 + 1)], tops=((cx, az1 - 4, 'lantern[hanging=false,waterlogged=false]'),))
        for x in range(cx + 1, min(cx + 3, ax1)):
            for z in range(az1 - 4, az1 + 1):
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
        self.attendant('smith', cx + 1, 0, az1 - 2)
        # 外の店先: 薪の山と鉄の塊
        for (x, z) in ((x0 - 2, z1 - 1), (x0 - 2, z1 - 3)):
            v.set(x, 0, z, 'barrel[facing=up,open=false]')
        v.set(x1 + 2, 0, z1 - 1, 'iron_block')

    def town_tool_shop(self, x0, z0, x1, z1):
        """道具屋 (戸は北)。売り台の向こうに道具屋、奥の壁に樽・箱・棚、客の側の壁に品"""
        st, v = self.st, self.v
        b, ridge = self.fac_body('tool_shop', x0, z0, x1, z1, 'north', floors=2, fh=6, ground='shop', axis='z')
        self.fac_front(b, 'tool_shop')
        dx_, _, dz_, _ = b.doors[0]
        self.mark('shop', dx_, 0, dz_ - 1)
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2
        self.room(ax0, az0, ax1, az1, 0, 6, rug=True)
        self.room(ax0, az0, ax1, az1, 7, 5, rug=False)
        cz = az1 - 3
        self.counter([(x, cz) for x in range(ax0, ax1 + 1)],
                     tops=((ax0, cz, 'lantern[hanging=false,waterlogged=false]'), (ax1, cz, 'decorated_pot[cracked=false,facing=north,waterlogged=false]')))
        for x in range(ax0, ax1 + 1):
            for z in (cz + 1, cz + 2):
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
            v.set(x, 0, az1, ['barrel[facing=north,open=false]', 'chest[facing=north,type=single,waterlogged=false]', 'bookshelf', 'fletching_table'][x % 4])
            v.set(x, 1, az1, ['barrel[facing=north,open=false]', 'decorated_pot[cracked=false,facing=north,waterlogged=false]', 'bookshelf', 'air'][x % 4])
        self.attendant('tool_merchant', dx_, 0, cz + 1)
        for z in range(az0, cz):
            v.set(ax0, 0, z, ['hay_block[axis=y]', 'barrel[facing=up,open=false]', 'white_wool'][z % 3])
            v.set(ax1, 0, z, ['barrel[facing=up,open=false]', 'chest[facing=west,type=single,waterlogged=false]', 'smoker[facing=west,lit=false]'][z % 3])
            v.set(ax1, 1, z, 'lantern[hanging=false,waterlogged=false]' if z % 3 == 1 else 'air')
        v.set((ax0 + ax1) // 2, 5, (az0 + cz) // 2, 'lantern[hanging=true,waterlogged=false]')

    def town_apothecary(self, x0, z0, x1, z1):
        """薬師の店 (戸は北)。机の上に醸造台、奥に薬棚と大釜、吊るした灯り"""
        st, v = self.st, self.v
        b, ridge = self.fac_body('apothecary', x0, z0, x1, z1, 'north', floors=2, fh=6, ground='shop', axis='z',
                                 roof=Mat('mud_bricks', 'mud_brick', 'mud_brick_wall'))
        self.fac_front(b, 'apothecary')
        dx_, _, dz_, _ = b.doors[0]
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2
        self.room(ax0, az0, ax1, az1, 0, 6, rug=True)
        self.room(ax0, az0, ax1, az1, 7, 5, rug=False)
        cz = az1 - 3
        self.counter([(x, cz) for x in range(ax0, ax1 + 1)],
                     tops=((ax0 + 1, cz, 'brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]'),
                           (ax1 - 1, cz, 'brewing_stand[has_bottle_0=false,has_bottle_1=true,has_bottle_2=true]'),
                           (dx_ + 2 if dx_ + 2 <= ax1 else dx_ - 2, cz, 'candle[candles=4,lit=true,waterlogged=false]')))
        for x in range(ax0, ax1 + 1):
            for z in (cz + 1, cz + 2):
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
            v.set(x, 0, az1, ['barrel[facing=north,open=false]', 'bookshelf', 'cauldron', 'water_cauldron[level=3]'][x % 4])
            v.set(x, 1, az1, ['potted_fern', 'candle[candles=2,lit=true,waterlogged=false]', 'air', 'air'][x % 4])
            v.set(x, 2, az1, ['air', 'potted_allium', 'air', 'potted_blue_orchid'][x % 4])
        self.attendant('apothecary', dx_, 0, cz + 1)
        for z in range(az0, cz):
            v.set(ax0, 0, z, 'composter[level=7]' if z % 2 else 'barrel[facing=east,open=false]')
            v.set(ax0, 1, z, ['potted_azalea_bush', 'potted_oxeye_daisy', 'potted_cornflower'][z % 3])
            v.set(ax1, 0, z, 'bookshelf')
            v.set(ax1, 1, z, 'potted_fern' if z % 2 else 'candle[candles=3,lit=true,waterlogged=false]')
        for x in (ax0 + 2, ax1 - 2):
            v.set(x, 5, (az0 + cz) // 2, 'chain[axis=y,waterlogged=false]')
            v.set(x, 4, (az0 + cz) // 2, 'lantern[hanging=true,waterlogged=false]')

    # ---------------------------------------------------------------- 都と同じ施設を土地の町にも (2026-10-27 の声「始まりの町に戻らなくてもいいように」)
    # 一つの建物に係の人を何人か並べる (受付の机の向こうに、役ごとの棚)。戸は北
    STATION = {
        'guild_clerk': ['lectern[facing=north,has_book=false,powered=false]', 'bookshelf', 'bookshelf'],
        'trade_keeper': ['chest[facing=north,type=single,waterlogged=false]', 'barrel[facing=north,open=false]', 'decorated_pot[cracked=false,facing=north,waterlogged=false]'],
        'storage_keeper': ['barrel[facing=north,open=false]', 'chest[facing=north,type=single,waterlogged=false]', 'barrel[facing=up,open=false]'],
        'stable_keeper': ['hay_block[axis=y]', 'hay_block[axis=x]', 'composter[level=6]'],
        'perch_keeper': ['bone_block[axis=y]', 'hay_block[axis=y]', 'bone_block[axis=y]'],
        'mapmaker': ['cartography_table', 'bookshelf', 'lectern[facing=north,has_book=false,powered=false]'],
        'training_master': ['target[power=0]', 'grindstone[face=floor,facing=north]', 'smithing_table'],
        'keep_veteran': ['lectern[facing=north,has_book=false,powered=false]', 'bookshelf', 'bookshelf'],
        'black_marketeer': ['barrel[facing=north,open=false]', 'cobweb', 'chest[facing=north,type=single,waterlogged=false]'],
    }

    def hall(self, name, label, roles, x0, z0, x1, z1, floors=2, roof=None, timber=False):
        """係の人の並ぶ建物 (戸は北)。受付の机は奥の壁の手前に一本、役ごとの持ち場の後ろに棚"""
        st, v = self.st, self.v
        b, ridge = self.fac_body(name, x0, z0, x1, z1, 'north', floors=floors, fh=6, ground='shop',
                                 axis='x' if (x1 - x0) >= (z1 - z0) else 'z', roof=roof)
        if timber and floors >= 2:
            vox.timber_upper(v, st, b)
        self.fac_front(b, label)
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2
        self.room(ax0, az0, ax1, az1, 0, 6, rug=True)
        if floors >= 2:
            self.room(ax0, az0, ax1, az1, 7, 5, rug=False)
        cz = az1 - 3
        n = len(roles)
        xs = [int(round(ax0 + (ax1 - ax0) * (i + 0.5) / n)) for i in range(n)]
        self.counter([(x, cz) for x in range(ax0, ax1 + 1)],
                     tops=tuple((x + 1, cz, 'lantern[hanging=false,waterlogged=false]') for x in xs if x + 1 <= ax1))
        for x in range(ax0, ax1 + 1):
            for z in (cz + 1, cz + 2):
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
        for i, (role, x) in enumerate(zip(roles, xs)):
            items = self.STATION.get(role, ['bookshelf', 'barrel[facing=north,open=false]', 'bookshelf'])
            for k, dx in enumerate((-1, 0, 1)):
                if ax0 <= x + dx <= ax1:
                    v.set(x + dx, 0, az1, items[k % len(items)])
            if i > 0:
                px = (xs[i - 1] + x) // 2
                v.set(px, 0, cz + 1, st.fence)
                v.set(px, 1, cz + 1, st.fence)
            v.set(x, 4, cz + 1, 'lantern[hanging=true,waterlogged=false]')
            self.attendant(role, x, 0, cz + 1)
        return b

    def town_market(self, x0, z0, x1, z1):
        """市の店: 道具屋と薬師が一つの机に並ぶ"""
        b = self.hall('market', 'market', ['tool_merchant', 'apothecary'], x0, z0, x1, z1, floors=2, timber=True)
        dx_, _, dz_, _ = b.doors[0]
        self.mark('shop', dx_, 0, dz_ - 1)

    def town_guild(self, x0, z0, x1, z1):
        """組合会館: 組合の受付・取引所・預かり所が一つの机に並ぶ"""
        self.hall('guild', 'guild_hall', ['guild_clerk', 'trade_keeper', 'storage_keeper'], x0, z0, x1, z1, floors=2, timber=True)

    def town_travel(self, x0, z0, x1, z1):
        """旅の館 (門の外): 厩舎の番・子の止まり木の番・地図屋"""
        self.hall('travel', 'travel', ['stable_keeper', 'perch_keeper', 'mapmaker'], x0, z0, x1, z1, floors=1)

    def town_training(self, x0, z0, x1, z1):
        """訓練所 (門の外): 師範と砦の古兵"""
        self.hall('training', 'training', ['training_master', 'keep_veteran'], x0, z0, x1, z1, floors=1)

    def town_stall(self, x0, z0, x1, z1):
        """裏の店 (闇市。名は浮かべない)"""
        self.hall('stall', '', ['black_marketeer'], x0, z0, x1, z1, floors=1)

    def town_chapel(self, x0, z0, x1, z1):
        """小さな社 (戸は北)。祭壇の蝋燭と書見台、腰掛けの列、小さな鐘の塔"""
        st, v = self.st, self.v
        b, ridge = self.fac_body('chapel', x0, z0, x1, z1, 'north', floors=1, fh=8, ground='window', axis='z', dormers=0)
        self.fac_front(b, 'shrine', banners=True)
        dx_, _, dz_, _ = b.doors[0]
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2
        self.room(ax0, az0, ax1, az1, 0, 8, rug=False, shelves=False)
        for z in range(az0 + 2, az1 - 3, 2):
            for x in list(range(ax0 + 1, dx_ - 1)) + list(range(dx_ + 2, ax1)):
                v.set(x, 0, z, '%s_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]' % st.wood)
        for z in range(az0, az1 + 1):
            v.set(dx_, 0, z, 'red_carpet' if v.get(dx_, 0, z) in (None, 'air') else v.get(dx_, 0, z))
        v.set(dx_, 0, az1, 'lectern[facing=north,has_book=false,powered=false]')
        for x in (dx_ - 2, dx_ + 2):
            v.set(x, 0, az1, st.trim.full)
            v.set(x, 1, az1, 'candle[candles=4,lit=true,waterlogged=false]')
        v.set(dx_, 3, az1, st.trim2)
        tower(v, st, x1, z0, half=2, y0=0, h=14, belfry=True, spire_rise=2, pinnacles=False, sealed=True)
        # 社の守り (鍵の間の頼み) と書庫番 (伝承の書・地図の写し)。祭壇の両わき
        self.attendant('shrine_priest', dx_ - 3, 0, az1 - 1)
        self.attendant('archivist', dx_ + 3, 0, az1 - 1)
        for x in (dx_ - 4, dx_ + 4):
            if ax0 <= x <= ax1:
                v.set(x, 0, az1, 'bookshelf')
                v.set(x, 1, az1, 'bookshelf')

    # ---------------------------------------------------------------- 街区
    def free(self, x0, z0, x1, z1, gap=1):
        """建物を置いてよい範囲か (今ある建物・広場・大通りから gap 離れる)"""
        if x0 < -IN or z0 < -IN or x1 > IN or z1 > IN:
            return False
        for (_n, a0, b0, a1, b1) in self.parts:
            if x0 - gap <= a1 and x1 + gap >= a0 and z0 - gap <= b1 and z1 + gap >= b0:
                return False
        # 大通り (半幅 ROAD) と広場には建てない
        if x0 <= ROAD + 1 and x1 >= -ROAD - 1:
            return False
        if z0 <= ROAD + 1 and z1 >= -ROAD - 1:
            return False
        return not (x0 <= 13 and x1 >= -13 and z0 <= 13 and z1 >= -13)

    def row(self, a0, a1, b0, b1, front, quest_every=0):
        """一列の町家: 長い向き a0..a1 に、奥行き b0..b1 (front の側が通り)。幅 7〜10、あいだ 2 の路地"""
        rng = self.rng
        along_x = front in ('north', 'south')
        a = a0
        n = 0
        while a + 6 <= a1:
            wdt = rng.choice((7, 8, 9, 9, 10))
            b = min(a + wdt - 1, a1)
            if b - a < 6:
                break
            x0, z0, x1, z1 = (a, b0, b, b1) if along_x else (b0, a, b1, b)
            if self.free(x0, z0, x1, z1):
                quest = quest_every and n % quest_every == 0
                floors = 2 if quest or rng.random() < 0.6 else 3 if rng.random() < 0.3 else 1
                extras = tuple(e for e in ('dormer', 'chimney') if rng.random() < 0.5)
                self.house(x0, z0, x1, z1, 0, floors, 5, front, 'x' if along_x else 'z', ('north', 'south', 'east', 'west'),
                           ground='window', extras=extras, quest=quest)
                n += 1
                a = b + 3
            else:
                a += 2

    def town_blocks(self):
        """町家の並び: 壁ぞい (背を壁に)、大通りの両わき、施設の裏の小路。大通りに面する家は三つに一つ入れる家"""
        D = 9
        for (a0, a1) in ((-IN, -6), (6, IN)):
            self.row(a0, a1, -IN, -IN + D - 1, 'south')
            self.row(a0, a1, IN - D + 1, IN, 'north')
        for (a0, a1) in ((-IN + D + 2, -6), (6, IN - D - 2)):
            self.row(a0, a1, -IN, -IN + D - 2, 'east')
            self.row(a0, a1, IN - D + 2, IN, 'west')
        # 大通りの両わき (広場の外)
        S = 8
        for (a0, a1) in ((-IN + D + 2, -15), (15, IN - D - 2)):
            self.row(a0, a1, -ROAD - 2 - S, -ROAD - 2, 'east', quest_every=3)
            self.row(a0, a1, ROAD + 2, ROAD + 2 + S, 'west', quest_every=3)
            self.row(a0, a1, -ROAD - 2 - S, -ROAD - 2, 'south', quest_every=3)
            self.row(a0, a1, ROAD + 2, ROAD + 2 + S, 'north', quest_every=3)
        # 残りの空き (施設の裏): 二列目
        for (a0, a1) in ((-IN + D + 2, -15), (15, IN - D - 2)):
            for (b0, b1, f) in ((-28, -20, 'south'), (20, 28, 'north')):
                self.row(a0, a1, b0, b1, f)
            for (b0, b1, f) in ((-28, -20, 'east'), (20, 28, 'west')):
                self.row(a0, a1, b0, b1, f)

    def fill_open(self):
        """家の建たなかった空き地 (6 四方以上) を、小さな庭・市の屋台・井戸で埋める (広い舗装のままにしない)"""
        st, v, rng = self.st, self.v, self.rng
        k = 0
        for gz in range(-IN + 2, IN - 6, 7):
            for gx in range(-IN + 2, IN - 6, 7):
                x0, z0, x1, z1 = gx, gz, gx + 5, gz + 5
                if not self.free(x0, z0, x1, z1, gap=1):
                    continue
                cx, cz = gx + 2, gz + 2
                kind = k % 3
                k += 1
                if kind == 0:
                    flowerbed(v, st, x0 + 1, z0 + 1, x1 - 1, z1 - 1, y=0, edge=True, density=0.6)
                    tree(v, cx + 1, 0, cz + 1, st.trunk2, st.leaves2, h=5, r=2)
                    bench(v, st, x0, 0, cz, 'east', length=2)
                elif kind == 1:
                    # 市の屋台: 四本の柱と布の屋根、樽と箱
                    for (x, z) in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1)):
                        v.fill(x, 0, z, x, 2, z, st.fence)
                    for x in range(x0 + 1, x1):
                        for z in range(z0 + 1, z1):
                            v.set(x, 3, z, '%s_wool' % st.banner_color if (x + z) % 2 else 'white_wool')
                    v.set(cx, 0, cz, 'barrel[facing=up,open=true]')
                    v.set(cx + 1, 0, cz, 'barrel[facing=up,open=false]')
                    v.set(cx, 0, cz + 1, 'chest[facing=north,type=single,waterlogged=false]')
                    v.set(cx + 1, 1, cz, 'lantern[hanging=false,waterlogged=false]')
                else:
                    # 小さな井戸と植木鉢
                    for x in range(cx - 1, cx + 2):
                        for z in range(cz - 1, cz + 2):
                            v.set(x, 0, z, st.base.full if (x, z) != (cx, cz) else 'water')
                    v.set(cx, -1, cz, 'water')
                    for (x, z) in ((cx - 1, cz - 1), (cx + 1, cz + 1)):
                        v.fill(x, 1, z, x, 2, z, st.fence)
                    v.fill(cx - 1, 3, cz - 1, cx + 1, 3, cz + 1, '%s_slab[type=bottom,waterlogged=false]' % st.wood)
                    planter(v, st, x0, 0, z1)
                    planter(v, st, x1, 0, z0)
                self.claim(x0, z0, x1, z1, 'yard')

    def gardens(self):
        """空いた地面に木と花壇 (建物・通り・広場の外)"""
        st, v = self.st, self.v
        spots = [(-30, 30), (30, 30), (-24, -36), (22, -40), (-40, 10), (40, -10)]
        for (x, z) in spots:
            if all(not (p[1] - 1 <= x <= p[3] + 1 and p[2] - 1 <= z <= p[4] + 1) for p in self.parts):
                if v.get(x, 0, z) in (None, 'air') and abs(x) > ROAD + 2 and abs(z) > ROAD + 2:
                    tree(v, x, 0, z, st.trunk2 if (x + z) % 2 else st.trunk, st.leaves2 if (x + z) % 2 else st.leaves, h=5, r=2)

    def lamps(self):
        """大通りの灯り (8 ごと、戸と施設の口の前は避ける)"""
        st, v = self.st, self.v
        fronts = {(x, z) for (x, z, _o) in self.openings}

        def ok(x, z):
            return all(abs(x - fx) > 2 or abs(z - fz) > 2 for (fx, fz) in fronts) and v.get(x, 0, z) in (None, 'air') \
                and v.get(x, -1, z) not in (None, 'air')
        for i in range(-IN + 4, IN - 3, 8):
            if abs(i) <= 13:
                continue
            for (x, z) in ((ROAD + 1, i), (-ROAD - 2, i), (i, ROAD + 1), (i, -ROAD - 2)):
                if ok(x, z):
                    lamp_post(v, st, x, 0, z, h=4)

    # ---------------------------------------------------------------- 土地の見どころ
    def landmark(self):
        getattr(self, 'landmark_' + self.region)()

    def landmark_whale(self):
        """鯨見の町: 見張りの塔 (北東の角。登れない高い石の塔、頂に鐘の間) と、広場の上を渡る鯨骨の門二つ、真ん中の古井戸。塔の足もとに鯨追い"""
        st, v = self.st, self.v
        cx, cz = getattr(self, "lm", (30, -26))
        tower(v, st, cx, cz, half=4, y0=0, h=26, belfry=True, spire_rise=3, pinnacles=True, sealed=True, door_side='south')
        self.claim(cx - 5, cz - 5, cx + 5, cz + 5, 'lookout')
        self.mark('npc_chaser', cx, 0, cz + 7)
        if getattr(self, 'symbol_only', False):
            return
        self.plaza_whale()

    def plaza_whale(self):
        st, v = self.st, self.v
        # 鯨骨の門 (広場の南北と東西の入口に、肋の弧)
        for axis in ('x', 'z'):
            for s in (1, -1):
                for i in range(-6, 7):
                    t = i / 6.0
                    y = int(round(10 * (1 - t * t)))
                    if axis == 'x':
                        x, z = i, s * 12
                    else:
                        x, z = s * 12, i
                    for yy in range(max(0, y - 1), y + 1):
                        v.set(x, yy, z, 'bone_block[axis=y]')
                    if abs(i) == 6:
                        for yy in range(0, 2):
                            v.set(x, yy, z, 'bone_block[axis=y]')
        # 古井戸 (広場の真ん中)
        for x in range(-2, 3):
            for z in range(-2, 3):
                if abs(x) == 2 or abs(z) == 2:
                    v.set(x, 0, z, 'mossy_cobblestone')
                    v.set(x, 1, z, 'mossy_cobblestone_wall' if (x + z) % 2 else 'air')
                else:
                    v.set(x, -1, z, 'water')
                    v.set(x, 0, z, 'water')
        for (x, z) in ((-2, -2), (2, 2), (-2, 2), (2, -2)):
            v.fill(x, 1, z, x, 3, z, st.fence)
        v.fill(-2, 4, -2, 2, 4, 2, '%s_slab[type=bottom,waterlogged=false]' % st.wood)
        v.set(0, 3, 0, 'lantern[hanging=true,waterlogged=false]')

    def landmark_tide(self):
        """潮の町: 灯台 (北東の角。白と青緑の帯の塔、頂の灯りの間に海の灯り) と、広場の噴水、通りの魚の市の屋台"""
        st, v = self.st, self.v
        cx, cz = getattr(self, "lm", (30, -26))
        for y in range(0, 26):
            r = 4.2 - y * 0.06
            band = 'white_terracotta' if (y // 4) % 2 == 0 else 'cyan_terracotta'
            for x in range(-5, 6):
                for z in range(-5, 6):
                    d = math.hypot(x, z)
                    if r - 1.0 <= d <= r:
                        v.set(cx + x, y, cz + z, band)
                    elif d < r - 1.0:
                        v.set(cx + x, y, cz + z, 'black_concrete')
        for x in range(-4, 5):
            for z in range(-4, 5):
                d = math.hypot(x, z)
                if d <= 4.2:
                    v.set(cx + x, 26, cz + z, 'smooth_quartz')
                if 2.4 <= d <= 3.4:
                    for y in (27, 28, 29):
                        v.set(cx + x, y, cz + z, 'light_blue_stained_glass')
                elif d < 2.4:
                    v.set(cx + x, 27, cz + z, 'sea_lantern')
                    v.set(cx + x, 28, cz + z, 'sea_lantern' if d < 1.5 else 'air')
                if d <= 3.8:
                    v.set(cx + x, 30, cz + z, 'dark_prismarine_slab[type=bottom,waterlogged=false]')
        v.set(cx, 31, cz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
        self.claim(cx - 5, cz - 5, cx + 5, cz + 5, 'lighthouse')
        if getattr(self, 'symbol_only', False):
            return
        self.plaza_tide()

    def plaza_tide(self):
        st, v = self.st, self.v
        fountain(v, st, 0, 0, r=4, y=0)
        # 魚の市の屋台 (広場の東西の通りぞい)
        for (sx, sz) in ((-16, -6), (-22, -6), (16, 6), (22, 6)):
            for x in range(sx - 1, sx + 2):
                v.set(x, 0, sz, 'barrel[facing=up,open=true]' if x == sx else 'barrel[facing=up,open=false]')
            for (x, z) in ((sx - 1, sz - 1), (sx + 1, sz - 1), (sx - 1, sz + 1), (sx + 1, sz + 1)):
                v.fill(x, 0, z, x, 2, z, st.fence)
            for x in range(sx - 1, sx + 2):
                for z in range(sz - 1, sz + 2):
                    v.set(x, 3, z, 'cyan_wool' if (x + z) % 2 else 'white_wool')

    def landmark_storm(self):
        """風の町: 風車 (北東の角。石の胴に四枚の帆) と避雷の塔 (南西の角の手前)、広場の真ん中の銅の避雷の柱"""
        st, v = self.st, self.v
        cx, cz = getattr(self, "lm", (30, -26))
        tower(v, st, cx, cz, half=3, y0=0, h=14, belfry=False, spire_rise=2, pinnacles=False, sealed=True)
        self.claim(cx - 4, cz - 4, cx + 4, cz + 4, 'windmill')
        hub = (cx, 14, cz + 4)
        v.set(*hub, 'spruce_log[axis=z]')
        for k in range(4):
            a = math.radians(45 + 90 * k)
            for r in range(1, 9):
                x = int(round(hub[0] + math.cos(a) * r))
                y = int(round(hub[1] + math.sin(a) * r))
                v.set(x, y, hub[2], 'stripped_spruce_log[axis=y]' if r % 3 else 'spruce_log[axis=y]')
                if r >= 3:
                    px = int(round(-math.sin(a)))
                    py = int(round(math.cos(a)))
                    v.set(x + px, y + py, hub[2], 'white_wool')
        # 避雷の塔 (細い石の塔、頂に避雷針)
        rx, rz = getattr(self, "lm2", (-30, -26))
        tower(v, st, rx, rz, half=2, y0=0, h=20, belfry=False, spire_rise=3, pinnacles=False, sealed=True)
        v.set(rx, 26, rz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
        self.claim(rx - 3, rz - 3, rx + 3, rz + 3, 'rod_tower')
        if getattr(self, 'symbol_only', False):
            return
        self.plaza_storm()

    def plaza_storm(self):
        st, v = self.st, self.v
        # 広場の真ん中の銅の柱
        for y in range(0, 6):
            v.set(0, y, 0, 'cut_copper' if y < 5 else 'oxidized_cut_copper')
        v.set(0, 6, 0, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
        for (x, z) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            v.set(x, 0, z, 'smooth_stone_slab[type=bottom,waterlogged=false]')

    def landmark_ember(self):
        """灰の町: 溶鉱の煙突 (北東の角。煉瓦の太い塔と、頂の煙の焚き火)、広場の真ん中の熾火の炉 (黒い石の台に火)"""
        st, v = self.st, self.v
        cx, cz = getattr(self, "lm", (30, -26))
        for y in range(0, 28):
            r = 4 if y < 6 else 3
            for x in range(-r, r + 1):
                for z in range(-r, r + 1):
                    edge = max(abs(x), abs(z)) == r
                    if edge:
                        v.set(cx + x, y, cz + z, 'bricks' if (y + x + z) % 7 else 'polished_blackstone_bricks')
                    else:
                        v.set(cx + x, y, cz + z, 'black_concrete')
        for x in range(-2, 3):
            for z in range(-2, 3):
                v.set(cx + x, 28, cz + z, 'campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]' if (x, z) in ((0, 0), (1, 1), (-1, -1)) else 'polished_blackstone')
        for y in (6, 14, 22):
            for x in range(-4, 5):
                for z in (-4, 4):
                    v.set(cx + x, y, cz + z, 'polished_blackstone_bricks')
        self.claim(cx - 5, cz - 5, cx + 5, cz + 5, 'smelter')
        if getattr(self, 'symbol_only', False):
            return
        self.plaza_ember()

    def plaza_ember(self):
        st, v = self.st, self.v
        # 熾火の炉 (広場の真ん中)
        for x in range(-2, 3):
            for z in range(-2, 3):
                v.set(x, 0, z, 'polished_blackstone_bricks' if max(abs(x), abs(z)) == 2 else 'magma_block')
        v.set(0, 1, 0, 'campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
        for (x, z) in ((-2, -2), (2, 2), (-2, 2), (2, -2)):
            v.fill(x, 1, z, x, 3, z, 'polished_blackstone_wall')
            v.set(x, 4, z, 'lantern[hanging=false,waterlogged=false]')

    # ---------------------------------------------------------------- 組み立て
    def build(self):
        self.ground()
        self.walls()
        self.plaza()
        self.landmark()
        # 施設 (広場のまわり、大通りの角)
        self.town_inn(-42, -32, -16, -16)
        self.town_smithy(10, -34, 24, -18)
        self.town_tool_shop(10, 16, 22, 30)
        self.town_apothecary(26, 16, 38, 30)
        self.town_chapel(-34, 16, -20, 30)
        self.town_blocks()
        self.fill_open()
        self.gardens()
        self.lamps()
        self.tidy_for_walkers()
        self.drop_floaters()
        self.fix_stairs()
        self.house_lights()
        self.drop_unsupported()
        self.v.connect()
        return self.v


def _check_blocks(data, region):
    """バニラにない状態の名前 (例: cut_sandstone_stairs) があると、ゲームが設計図を読めない。作る前に弾く"""
    import glob
    import zipfile
    jars = glob.glob(os.path.join(T.ROOT, 'build', 'moddev', 'artifacts', '*client-extra*.jar'))
    if not jars:
        return
    with zipfile.ZipFile(jars[0]) as z:
        blocks = {n.split('/')[-1][:-5] for n in z.namelist() if n.startswith('assets/minecraft/blockstates/')}
    bad = sorted({p.split('[')[0] for p in data['palette'] if p.startswith('minecraft:') and p.split('[')[0][10:] not in blocks})
    if bad:
        raise SystemExit('town_%s: unknown blocks %s' % (region, bad))


def main():
    regions = [a for a in sys.argv[1:] if a in STYLES] or list(STYLES)
    for region in regions:
        t = RegionTown(region)
        v = t.build()
        meta = {'rect': [-W - 18, -W - 18, W + 18, W + 18], 'level': True, 'ring': 8, 'fillWater': True, 'height': 40}
        data = v.runs(meta)
        data['site'] = 'town_' + region
        path = os.path.join(T.DATA, 'town_%s.json' % region)
        _check_blocks(data, region)
        vox.save(path, data)
        n = sum(1 for s in v.b.values() if s != 'air')
        print('wrote', path, os.path.getsize(path) // 1024, 'KB', n, 'blocks', 'marks', sorted(v.marks))
        T.PREVIEW = os.path.join(T.ROOT, 'docs', 'review', 'town_roughs', 'town_%s_plan.png' % region)
        T.preview(v)


if __name__ == '__main__':
    main()
