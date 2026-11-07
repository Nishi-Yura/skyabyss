# -*- coding: utf-8 -*-
"""外れの集落 (2026-11-06。案の頁「大陸の外れ」の m_hamlets = a: 土地ごとに 2 つ、計 8) の形のラフ 3 案。土地の町と同じ部品集
(vox.py・town.Town・region_town.RegionTown・massing.build_house) で建てる。どの案にも、宿 (戸は南)・道具屋 (戸は北)・家 3〜5・
井戸・依頼の板と、土地の町と同じ名前の印 (inn・inn_bed・shop・rest・board・stand・sign・folk_a・folk_b・npc_innkeeper・npc_tool_merchant)。
正面 = 南 (+z)。見どころの部品と同じく、建つときに一番近い道の方へ回る。案ごとに骨組みを変える:
  A 街道の宿場: 東西の一本の通りの両側に宿・店・家。通りの東に馬留め、西に畑、北に果樹。道の口に灯り
  B 柵の砦村: 丸太の柵で囲み (南に門・四隅の見張り台)、真ん中に見張りの塔 (頂に狼煙の火。遠くから見える)。宿と家は柵ぞい
  C 目印の里: 真ん中に土地の目印 (岩鯨 = 鐘の見張り塔 / 潮殻 = 灯台 / 雷翼 = 風車 / 余燼 = 炉の煙突)。丸い小道と、まわりを囲む家 5 つ
選定 (2026-11-06。頁「外れの集落の形」の h_shape = d): 土地ごとに、奥のダンジョンに近いほうを B、もう一つを C (FINAL)
python -I tools/capital/frontier_hamlet.py           → src/main/resources/data/skyabyss/opening/hamlet_<鍵>.json (8 か所。world/continent/FrontierHamlets)
python -I tools/capital/frontier_hamlet.py rough [a b c] [whale tide storm ember]
  → src/main/resources/data/skyabyss/opening/frontier_rough_<案>_<土地>.json (見比べのラフ。選んだので、ふだんは書かない)
撮影: tools/shots/frontier_hamlets.txt
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vox  # noqa: E402
import town as T  # noqa: E402
import massing as M  # noqa: E402
from region_town import RegionTown, STYLES, _check_blocks  # noqa: E402
from vox import tower, flowerbed, tree, bench, lamp_post  # noqa: E402

LANTERN = 'lantern[hanging=false,waterlogged=false]'
HANG = 'lantern[hanging=true,waterlogged=false]'
R_OUT = 34  # 部品の範囲の半分 (地面をならす所)


class FrontierHamlet(RegionTown):
    def __init__(self, region, option, key=''):
        super().__init__(region)
        self.option = option
        self.v = vox.Vox(seed=(sum(map(ord, region + option + key)) * 131) % 100000 + 7)
        self.rng = self.v.rng

    # ---------------------------------------------------------------- 小道具
    def path(self, x0, z0, x1, z1):
        """土の小道 (y -1)。砂利と粗い土をまぜる"""
        v = self.v
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                r = self.rng.random()
                v.set(x, -1, z, 'dirt_path' if r < 0.62 else 'coarse_dirt' if r < 0.82 else 'gravel')

    def house(self, rect, front, floors=2, kind=None, quest=False):
        kind = kind or self.rng.choice(['gable', 'row', 'jetty'] if floors >= 2 else ['cottage', 'row'])
        roof = self.rng.choice(['steep', 'bell', 'half_hip', 'kick'])
        M.build_house(self, kind, rect, front, floors, roof, quest=quest, extras=('chimney',) if floors >= 2 else ())
        self.claim(*rect, 'house')

    def cottage(self, rect, front, floors=1):
        self.house(rect, front, floors, kind='cottage' if floors == 1 else None)

    def lamp(self, x, z):
        lamp_post(self.v, self.st, x, 0, z)

    def log_name(self):
        w = self.st.wood
        return w if w not in ('bamboo', 'crimson', 'warped') else 'spruce'

    def inn(self, x0, z0, x1, z1):
        """宿 (戸は南。土地の町の宿の作り)"""
        self.town_inn(x0, z0, x1, z1)

    def shop(self, x0, z0, x1, z1):
        """道具屋 (戸は北)"""
        self.town_tool_shop(x0, z0, x1, z1)

    def well(self, cx, cz):
        st, v = self.st, self.v
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                if abs(x - cx) == 2 or abs(z - cz) == 2:
                    v.set(x, 0, z, st.base.full)
                    v.set(x, 1, z, st.base.wall() if (x + z) % 2 else 'air')
                else:
                    v.set(x, -1, z, 'water')
                    v.set(x, 0, z, 'water')
        for (x, z) in ((cx - 2, cz - 2), (cx + 2, cz + 2), (cx - 2, cz + 2), (cx + 2, cz - 2)):
            v.fill(x, 1, z, x, 3, z, st.fence)
        v.fill(cx - 2, 4, cz - 2, cx + 2, 4, cz + 2, '%s_slab[type=bottom,waterlogged=false]' % st.wood)
        v.set(cx, 3, cz, HANG)

    def board(self, bx, bz):
        st, v = self.st, self.v
        for x in range(bx - 1, bx + 2):
            v.set(x, 0, bz, st.fence)
            v.set(x, 1, bz, '%s_planks' % st.wood)
            v.set(x, 2, bz, '%s_planks' % st.wood)
            v.set(x, 1, bz + 1, '%s_wall_sign[facing=south,waterlogged=false]' % st.wood)
        v.set(bx, 3, bz, '%s_slab[type=bottom,waterlogged=false]' % st.wood)
        self.mark('board', bx, 0, bz + 2)

    def marks_at(self, stand, rest, folk_a, folk_b, sign):
        st, v = self.st, self.v
        self.mark('stand', *stand)
        self.mark('rest', *rest)
        self.mark('folk_a', *folk_a)
        self.mark('folk_b', *folk_b)
        v.set(sign[0], 0, sign[2], '%s_sign[rotation=0,waterlogged=false]' % st.wood)
        self.mark('sign', *sign)

    def field(self, fx0, fz0, fx1, fz1):
        st, v = self.st, self.v
        for x in range(fx0, fx1 + 1):
            for z in range(fz0, fz1 + 1):
                if x in (fx0, fx1) or z in (fz0, fz1):
                    v.set(x, 0, z, st.fence)
                elif (x - fx0) % 3 == 0:
                    v.set(x, -1, z, 'water')
                else:
                    v.set(x, -1, z, 'farmland[moisture=7]')
                    v.set(x, 0, z, 'wheat[age=7]' if (x + z) % 4 else 'carrots[age=7]')
        sx, sz = (fx0 + fx1) // 2, (fz0 + fz1) // 2
        v.set(sx, 0, sz, st.fence)
        v.set(sx, 1, sz, 'hay_block[axis=y]')
        v.set(sx, 2, sz, 'carved_pumpkin[facing=south]')

    # ---------------------------------------------------------------- A 街道の宿場 (一本の通り)
    def option_a(self):
        st, v = self.st, self.v
        # 東西の通り (z -2..2) と、南の道の口からの道 (x -2..2)
        self.path(-33, -2, 33, 2)
        self.path(-2, 3, 2, R_OUT - 1)
        # 北側 (戸は南の通りへ): 宿と家二つ
        self.inn(-9, -18, 9, -6)
        self.cottage((-27, -12, -18, -5), 'south', 2)
        self.cottage((18, -12, 27, -5), 'south', 1)
        # 南側 (戸は北の通りへ): 道具屋と家
        self.shop(6, 5, 16, 15)
        self.cottage((-17, 5, -9, 12), 'north', 1)
        self.well(-5, 8)
        self.board(-12, -4)
        self.marks_at((0, 0, 3), (4, 0, -3), (-6, 0, 3), (10, 0, -3), (3, 0, 4))
        # 東の端の馬留め、西の端の畑、北の果樹
        for x in range(22, 33):
            for z in range(5, 17):
                if x in (22, 32) or z in (5, 16):
                    if not (z == 5 and x in (26, 27)):
                        v.set(x, 0, z, st.fence)
                elif self.rng.random() < 0.5:
                    v.set(x, -1, z, 'coarse_dirt')
        for x in range(24, 31, 2):
            v.set(x, 0, 15, 'hay_block[axis=x]')
        v.set(31, 0, 7, 'cauldron')
        self.field(-33, 6, -22, 17)
        for (x, z) in ((-30, -10), (30, -9), (-16, -24), (16, -24), (0, -26), (-26, -22), (26, -21)):
            tree(v, x, 0, z, st.trunk, st.leaves, h=5, r=2)
        # 荷車 (通りの西)
        log = self.log_name()
        for z in (-1, 1):
            v.set(-20, 0, z, 'stripped_%s_log[axis=x]' % log)
        v.fill(-21, 1, -1, -19, 1, 1, '%s_slab[type=bottom,waterlogged=false]' % st.wood)
        v.set(-20, 2, 0, 'barrel[facing=up,open=false]')
        # 道の口の灯りと、通りの灯り
        for x in (-4, 4):
            v.set(x, 0, R_OUT - 3, st.base.full)
            v.fill(x, 1, R_OUT - 3, x, 2, R_OUT - 3, st.fence)
            v.set(x, 3, R_OUT - 3, LANTERN)
        for x in (-30, -16, 16, 30):
            self.lamp(x, -3)
        self.lamp(4, 20)

    # ---------------------------------------------------------------- B 柵の砦村 (柵と真ん中の見張りの塔)
    def option_b(self):
        st, v = self.st, self.v
        L = 27
        log = self.log_name()
        logy = '%s_log[axis=y]' % log
        tip = '%s_fence' % log
        self.path(-2, 3, 2, R_OUT - 1)
        self.path(-8, -9, 8, 8)
        # 柵 (南に門)
        for i in range(-L, L + 1):
            for (x, z) in ((i, -L), (i, L), (-L, i), (L, i)):
                if z == L and abs(x) <= 2:
                    continue
                h = 5 if (x + z) % 2 else 6
                v.fill(x, 0, z, x, h - 1, z, logy)
                v.set(x, h, z, tip)
        for x in (-3, 3):
            v.fill(x, 0, L, x, 7, L, logy)
            v.set(x, 8, L, LANTERN)
        for x in range(-2, 3):
            v.set(x, 7, L, '%s_log[axis=x]' % log)
            v.set(x, 8, L, '%s_slab[type=bottom,waterlogged=false]' % st.wood)
        v.set(-2, 6, L + 1, '%s_wall_banner[facing=south]' % st.banner_color)
        v.set(2, 6, L + 1, '%s_wall_banner[facing=south]' % st.banner_color)
        # 四隅の見張り台
        for sx in (-1, 1):
            for sz in (-1, 1):
                cx, cz = sx * (L - 2), sz * (L - 2)
                for (x, z) in ((cx - 1, cz - 1), (cx + 1, cz - 1), (cx - 1, cz + 1), (cx + 1, cz + 1)):
                    v.fill(x, 0, z, x, 5, z, logy)
                v.fill(cx - 1, 6, cz - 1, cx + 1, 6, cz + 1, '%s_planks' % st.wood)
                for dx in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        if dx * sx > 0 or dz * sz > 0:
                            v.set(cx + dx, 7, cz + dz, tip)
                for y in range(0, 6):
                    v.set(cx, y, cz - sz * 2, 'ladder[facing=%s,waterlogged=false]' % ('north' if sz > 0 else 'south'))
                v.set(cx, 8, cz, LANTERN)
        # 真ん中の見張りの塔 (頂に狼煙の火。遠くから見える)
        tower(v, st, 0, -1, half=3, y0=0, h=18, belfry=False, spire_rise=0, pinnacles=False, sealed=True, door_side='south')
        for x in range(-3, 4):
            for z in range(-4, 3):
                v.set(x, 19, z, st.base.full)
        v.set(0, 20, -1, 'campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]')
        for (x, z) in ((-3, -4), (3, -4), (-3, 2), (3, 2)):
            v.fill(x, 20, z, x, 21, z, st.base.wall())
            v.set(x, 22, z, LANTERN)
        self.claim(-4, -5, 4, 3, 'beacon')
        # 宿 (北の柵ぞい、戸は南)・道具屋 (南東、戸は北)・家 (西・東・南西)
        self.inn(-9, -24, 9, -12)
        self.shop(10, 11, 20, 21)
        self.cottage((-24, -8, -16, 0), 'east', 2)
        self.cottage((16, -10, 24, -2), 'west', 1)
        self.cottage((-21, 12, -13, 20), 'north', 1)
        self.well(-9, 6)
        self.board(5, -9)
        self.marks_at((0, 0, 6), (4, 0, 6), (-5, 0, 4), (7, 0, 2), (4, 0, 9))
        # 焚き火の腰掛け (塔の南)
        v.set(0, -1, 9, 'magma_block')
        v.set(0, 0, 9, 'campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
        bench(v, st, -3, 0, 9, 'east', length=1)
        bench(v, st, 3, 0, 9, 'west', length=1)
        # 稽古の的 (西の内)・薪の山 (南西の内)
        for z in (-22, -18, -14):
            v.set(-23, 0, z, st.fence)
            v.set(-23, 1, z, 'hay_block[axis=y]')
            v.set(-23, 2, z, 'carved_pumpkin[facing=east]')
        for x in range(-24, -19):
            v.set(x, 0, 24, '%s_log[axis=x]' % log)
            if x % 2:
                v.set(x, 1, 24, '%s_log[axis=x]' % log)
        for x in (-12, 12):
            self.lamp(x, 8)

    # ---------------------------------------------------------------- C 目印の里 (真ん中の目印を家が囲む)
    def option_c(self):
        st, v = self.st, self.v
        self.lm = (0, -1)
        self.lm2 = (-24, 22)
        self.symbol_only = True
        self.landmark()
        # 目印のまわりの丸い小道と、外への小道
        for a in range(0, 360, 4):
            for r in (8, 9):
                x = int(round(math.cos(math.radians(a)) * r))
                z = int(round(math.sin(math.radians(a)) * r)) - 1
                if v.get(x, 0, z) in (None, 'air'):
                    v.set(x, -1, z, self.rng.choice(['dirt_path', 'dirt_path', 'coarse_dirt', 'gravel']))
        self.path(-1, 8, 1, R_OUT - 1)
        self.path(9, -2, 26, 0)
        self.path(-26, -2, -9, 0)
        # 宿 (北、戸は南の目印へ)・道具屋 (南東、戸は北)・家五つ (ぐるりと、目印を向く)
        self.inn(-9, -28, 9, -16)
        self.shop(12, 10, 22, 20)
        self.cottage((-27, -17, -19, -9), 'east', 2)
        self.cottage((19, -17, 27, -9), 'west', 2)
        self.cottage((-28, 2, -20, 9), 'east', 1)
        self.cottage((-18, 13, -10, 20), 'north', 1)
        self.cottage((20, 1, 28, 8), 'west', 1)
        self.well(-12, -6)
        self.board(-5, -12)
        self.marks_at((0, 0, 10), (4, 0, 10), (-6, 0, 8), (8, 0, 6), (3, 0, 12))
        # 土地の飾り: 岩鯨 = 鯨骨の門 (南の小道の上)、潮殻 = 魚の市の屋台、雷翼 = 銅の柱、余燼 = 熾火の台
        if self.region == 'whale':
            for i in range(-6, 7):
                t = i / 6.0
                y = int(round(8 * (1 - t * t)))
                for yy in range(max(0, y - 1), y + 1):
                    v.set(i, yy + 1, 14, 'bone_block[axis=y]')
                if abs(i) == 6:
                    v.fill(i, 0, 14, i, 2, 14, 'bone_block[axis=y]')
        elif self.region == 'tide':
            for (sx, sz) in ((-8, 12), (8, -10)):
                for x in range(sx - 1, sx + 2):
                    v.set(x, 0, sz, 'barrel[facing=up,open=true]' if x == sx else 'barrel[facing=up,open=false]')
                for (x, z) in ((sx - 1, sz - 1), (sx + 1, sz - 1), (sx - 1, sz + 1), (sx + 1, sz + 1)):
                    v.fill(x, 0, z, x, 2, z, st.fence)
                for x in range(sx - 1, sx + 2):
                    for z in range(sz - 1, sz + 2):
                        v.set(x, 3, z, 'cyan_wool' if (x + z) % 2 else 'white_wool')
        elif self.region == 'storm':
            for (x, z) in ((-8, 12), (8, 12)):
                for y in range(0, 5):
                    v.set(x, y, z, 'cut_copper' if y < 4 else 'oxidized_cut_copper')
                v.set(x, 5, z, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
        else:
            for (x, z) in ((-8, 12), (8, 12)):
                v.set(x, -1, z, 'magma_block')
                v.set(x, 0, z, 'campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
        # 庭・花・果樹・灯り
        for (x0, z0) in ((-30, -6), (29, -6), (-24, 22), (-31, -24)):
            flowerbed(v, st, x0, z0, x0 + 2, z0 + 2, y=0, edge=True, density=0.8)
        for (x, z) in ((26, 26), (31, 22), (30, 30), (-6, -32), (8, -32), (-31, 14)):
            tree(v, x, 0, z, st.trunk, st.leaves, h=5, r=2)
        for (x, z) in ((-10, 4), (10, 4), (-10, -10), (10, -10), (3, 22)):
            self.lamp(x, z)

    def build(self):
        getattr(self, 'option_' + self.option)()
        # 口: 正面 (南) の端の小道の上。建てたとき、ここから一番近い道まで小道を敷く (FrontierHamlets)
        self.mark('door', 0, 0, R_OUT - 1)
        self.tidy_for_walkers()
        self.drop_floaters()
        self.fix_stairs()
        self.house_lights()
        self.drop_unsupported()
        self.v.connect()
        # 片付けのあとにもう一度 (灯り・支えの無い物を外したあとで、段の向きと埋まりを直す)
        self.fix_stairs()
        return self.v


# 置き場の鍵 (tools/lib/frontier_roads.SPOTS) → (土地, 形)。奥のダンジョンに近いほうが B (柵の砦村)、もう一つが C (目印の里)
FINAL = {
    'w_ne': ('whale', 'b'), 'w_nw': ('whale', 'c'),
    't_se': ('tide', 'b'), 't_e': ('tide', 'c'),
    'e_se': ('ember', 'b'), 'e_n': ('ember', 'c'),
    's_nw': ('storm', 'b'), 's_sw': ('storm', 'c'),
}


def final():
    for key, (region, o) in FINAL.items():
        t = FrontierHamlet(region, o, key)
        v = t.build()
        meta = {'rect': [-R_OUT, -R_OUT, R_OUT, R_OUT], 'level': True, 'ring': 6, 'height': 34}
        data = v.runs(meta)
        data['site'] = 'hamlet_' + key
        path = os.path.join(T.DATA, 'hamlet_%s.json' % key)
        _check_blocks(data, region)
        vox.save(path, data)
        n = sum(1 for s in v.b.values() if s != 'air')
        print('wrote', os.path.basename(path), os.path.getsize(path) // 1024, 'KB', n, 'blocks', 'marks', sorted(v.marks))


def main():
    if 'rough' not in sys.argv[1:]:
        final()
        return
    opts = [a for a in sys.argv[1:] if a in ('a', 'b', 'c')] or ['a', 'b', 'c']
    regions = [a for a in sys.argv[1:] if a in STYLES] or list(STYLES)
    for o in opts:
        for region in regions:
            t = FrontierHamlet(region, o)
            v = t.build()
            meta = {'rect': [-R_OUT, -R_OUT, R_OUT, R_OUT], 'level': True, 'ring': 6, 'height': 34}
            data = v.runs(meta)
            data['site'] = 'frontier_rough_%s_%s' % (o, region)
            path = os.path.join(T.DATA, 'frontier_rough_%s_%s.json' % (o, region))
            _check_blocks(data, region)
            vox.save(path, data)
            n = sum(1 for s in v.b.values() if s != 'air')
            print('wrote', os.path.basename(path), os.path.getsize(path) // 1024, 'KB', n, 'blocks', 'marks', len(v.marks))


if __name__ == '__main__':
    main()
