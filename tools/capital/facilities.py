# -*- coding: utf-8 -*-
"""都の施設 (docs/design/35 の 2.1 の見取り図 A「四つの通りの役割」) の建物と中身。town.Town が継ぐ。

- 東の大通り = 交易: 道具屋 (広場の東の角、戸は広場へ)・預かり所 (東の通りの北)・薬師の店 (東の通りの南)・組合 (今の場所)
- 西の大通り = 祈りと旅: 社 (今の場所)・宿「鐘の宿」(西の通りの南の 1 列目)・子の止まり木 (西門の内の南)
- 南の大通り = 職: 鍛冶場 (今の場所。西の通りに口を開ける)・訓練場 (鍛冶場の南。章で開く鉄格子の門)
- 裏通り: 闇市 (南東)・古井戸 (南西)・地図屋 (南東の奥、屋根裏)・酒場の地下 (宿の南の路地)・墓守の小屋 (西門の外の墓地)
- 大通りの 1 列目の町家は入れる家 (町のクエストの家 quest_house_<n>)。入れない家は 2 列目より奥だけ

係の人の印 npc_<役> は受付の机の向こう (囲いの中) の床、足もとの 2 マスを空ける。宿の寝台の頭に inn_bed / inn_bed_<n>。
"""
from __future__ import annotations

import math

import vox
from backalley import BackAlley
from interiors import Interiors, HOME_THEMES
from vox import Body, DX, OPP, CW, gable_roof, hanging_sign, lamp_post, tree, bench, flowerbed, turret, rosette, hip_spire

PLANK = '%s_planks'


class Facilities(Interiors, BackAlley):
    # ---------------------------------------------------------------- 小道具
    def _init_fac(self):
        self.openings = []   # (外の面の x, z, 外向き): 戸の前。通りの灯りを避ける
        self.quest_n = 0
        self.fac_names = []

    def solid_at(self, x, y, z):
        s = self.v.get(x, y, z)
        if s is None or s in ('air', 'cave_air'):
            return False
        n = s.split('[')[0]
        return not any(t in n for t in ('pane', 'glass', 'door', 'trapdoor', 'stairs', 'slab', 'lantern', 'banner', 'sign',
                                         'fence', 'wall', 'ladder', 'carpet', 'potted', 'candle', 'chain', 'bars', 'leaves', 'torch'))

    def air(self, y):
        """y が地面 (y=-1) より下なら、設計図に書かれる空気 (cave_air) にする (普通の空気は書き出されない)"""
        return 'cave_air' if y <= -1 else 'air'

    def counter(self, cells, y=0, tops=()):
        """受付の机 (板の箱。ところどころ樽)。tops = (x, z, 物) 机の上に置く物"""
        st, v = self.st, self.v
        for i, (x, z) in enumerate(cells):
            v.set(x, y, z, 'barrel[facing=up,open=false]' if i % 4 == 2 else 'stripped_%s_wood[axis=y]' % st.wood if i % 4 == 0 else PLANK % st.wood)
            if v.get(x, y + 1, z) not in (None, 'air'):
                v.set(x, y + 1, z, 'air')
        for (x, z, s) in tops:
            v.set(x, y + 1, z, s)

    def attendant(self, role, x, y, z):
        """係の人の立つ所: 足もとの 2 マスを空け、下が床であることを確かめて印を置く"""
        v = self.v
        v.set(x, y, z, self.air(y))
        v.set(x, y + 1, z, self.air(y + 1))
        below = v.get(x, y - 1, z)
        if below in (None, 'air', 'cave_air') or 'carpet' in below:
            v.set(x, y - 1, z, PLANK % self.st.wood)
        self.mark('npc_' + role, x, y, z)

    def fac_body(self, name, x0, z0, x1, z1, front, floors=2, fh=6, ground='shop', infill=None, axis=None, dormers=None,
                 roof=None, door='wood', chimney=None, door_at=None, y0=0):
        st, v = self.st, self.v
        b = Body(v, st, x0, z0, x1, z1, y0, floors=floors, fh=fh, bay=4, door_side=front, door=door, ground=ground,
                 sides=('north', 'south', 'east', 'west'), pots=0.5, infill=infill, door_at=door_at).build()
        ax = axis or ('x' if (x1 - x0) >= (z1 - z0) else 'z')
        span = (x1 - x0) if ax == 'x' else (z1 - z0)
        ridge = gable_roof(v, st, x0, z0, x1, z1, b.top + 1, axis=ax, steep=True,
                           dormers=(dormers if dormers is not None else (2 if span >= 14 else 1 if span >= 8 else 0)),
                           mat=roof, chimney=chimney)
        self.claim(x0 - 1, z0 - 1, x1 + 1, z1 + 1, name)
        self.fac_names.append(name)
        return b, ridge

    def fac_front(self, b, sign, sign_side=1, banners=True):
        """施設の正面: 戸の上の吊り看板、戸の脇の碑 (sign_<名前>)、戸の両わきの旗。戸の前は空けたまま"""
        st, v = self.st, self.v
        ix, y, iz, out = b.doors[0]
        dx, dz = DX[out]
        ox, oz = ix + dx, iz + dz
        sx, sz = DX[CW[out]]
        hanging_sign(v, st, ox, y + 3, oz, out)
        # 戸の上に浮く施設の名 (建物のほかに、遠くからでも入れる店と分かるように。ShelterBuilder が文字の表示を置く)
        if sign:
            self.mark('label_' + sign, ox, y + 5, oz)
        if sign:
            for s in (sign_side, -sign_side):
                tx, tz = ox + 2 * sx * s + dx, oz + 2 * sz * s + dz
                if v.get(tx, y + 1, tz) in (None, 'air'):
                    self.tablet(tx, y, tz, out, sign)
                    v.set(tx, y + 1, tz, 'air')
                    break
        if banners:
            for s in (1, -1):
                wx, wz = ox + sx * s, oz + sz * s
                if self.solid_at(wx, y + 4, wz) and v.get(wx + dx, y + 4, wz + dz) in (None, 'air') \
                        and 'lantern' not in (v.get(wx + dx, y + 3, wz + dz) or ''):
                    v.set(wx + dx, y + 4, wz + dz, '%s_wall_banner[facing=%s]' % (st.banner_color, out))
        self.openings.append((ox, oz, out))

    def stair_run(self, x, z, y, n, toward, width=2, fill=None, head=3):
        """上り向き toward の階段 (一段目 = (x, y, z)、n 段、幅 width は toward の右手へ)。
        各段の上を head 段空け (上の階の床にかかれば穴になる)、段の下は fill で埋める。返り値 = 空けたセル"""
        st, v = self.st, self.v
        dx, dz = DX[toward]
        px, pz = DX[CW[toward]]
        fill = fill or (PLANK % st.wood)
        cleared = []
        base = y
        for k in range(n):
            for w in range(width):
                cx, cz, cy = x + dx * k + px * w, z + dz * k + pz * w, y + k
                v.set(cx, cy, cz, '%s_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]' % (st.wood, toward))
                for yy in range(cy + 1, cy + 1 + head):
                    if v.get(cx, yy, cz) not in (None, 'air', 'cave_air'):
                        cleared.append((cx, yy, cz))
                    v.set(cx, yy, cz, self.air(yy))
                for yy in range(base, cy):
                    v.set(cx, yy, cz, fill)
        return cleared

    def rail_hole(self, cells, yf, skip=()):
        """床の穴 (yf の段の cells) のまわりの床の上に柵 (落ちないように)。skip = 柵を置かない床 (階段の上り口)"""
        st, v = self.st, self.v
        holes = {(x, z) for (x, z) in cells}
        for (x, z) in holes:
            for (ddx, ddz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + ddx, z + ddz
                if (nx, nz) in holes or (nx, nz) in skip:
                    continue
                if self.solid_at(nx, yf, nz) and v.get(nx, yf + 1, nz) in (None, 'air', 'cave_air'):
                    v.set(nx, yf + 1, nz, st.fence)

    def place_ladder(self, ax0, az0, ax1, az1, y, floor_y, door=None, keep_free=()):
        """部屋 (中 ax0..ax1 / az0..az1、床の上 y) から上の階 (床の段 floor_y) への梯子。背の壁が y..floor_y で丸ごとの
        ブロック (窓でない) の所のうち、戸から遠い隅を選ぶ。梯子は上の階の床の段まで通し (そこが穴)、上の 2 段を空ける。返り値 (x, z)"""
        v = self.v
        cands = []
        for x in range(ax0, ax1 + 1):
            for z in range(az0, az1 + 1):
                if (x, z) in keep_free or any(v.get(x, yy, z) not in (None, 'air') for yy in range(y, floor_y)):
                    continue
                for (wx, wz, f) in ((x - 1, z, 'east'), (x + 1, z, 'west'), (x, z - 1, 'south'), (x, z + 1, 'north')):
                    if not (wx < ax0 or wx > ax1 or wz < az0 or wz > az1):
                        continue
                    if all(self.solid_at(wx, yy, wz) for yy in range(y, floor_y + 1)):
                        dist = abs(x - door[0]) + abs(z - door[1]) if door else 0
                        corner = (x in (ax0, ax1)) + (z in (az0, az1))
                        cands.append((dist + corner * 2, x, z, f))
        if not cands:
            return None
        cands.sort(reverse=True)
        _, lx, lz, lf = cands[0]
        for yy in range(y, floor_y + 1):
            v.set(lx, yy, lz, 'ladder[facing=%s,waterlogged=false]' % lf)
        v.set(lx, floor_y + 1, lz, 'air')
        v.set(lx, floor_y + 2, lz, 'air')
        self.ladders = getattr(self, 'ladders', []) + [(lx, lz, y, floor_y, lf)]
        return (lx, lz)

    def relay_ladders(self):
        """あとで部屋の床を敷き直した所に、梯子を置き直す (床の段の穴)"""
        for (lx, lz, y, fy, lf) in getattr(self, 'ladders', []):
            for yy in range(y, fy + 1):
                self.v.set(lx, yy, lz, 'ladder[facing=%s,waterlogged=false]' % lf)
            self.v.set(lx, fy + 1, lz, 'air')
            self.v.set(lx, fy + 2, lz, 'air')

    def wall_props(self, x, y, z, items):
        for i, s in enumerate(items):
            self.v.set(x, y + i, z, s)

    # ---------------------------------------------------------------- 東の大通り: 道具屋
    def tool_shop(self):
        """道具屋 (広場の東の角、x 33..45 z -38..-28)。戸は西 (広場へ)。売り台の向こうに道具屋、奥の壁に樽・箱・棚"""
        st, v = self.st, self.v
        x0, z0, x1, z1 = 33, -38, 45, -28
        b, ridge = self.fac_body('tool_shop', x0, z0, x1, z1, 'west', floors=2, fh=6, ground='shop', axis='x')
        self.fac_front(b, 'tool_shop')
        ix, _, dz, _ = b.doors[0]
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2   # 35..43, -36..-30
        self.room(ax0, az0, ax1, az1, 0, 6, rug=True)
        self.room(ax0, az0, ax1, az1, 7, 5, rug=False)
        w = st.wood
        # 売り台 (x 40、北から南まで) と、その向こうの道具屋 (x 41..43)
        self.counter([(40, z) for z in range(az0, az1 + 1)],
                     tops=((40, az0, 'lantern[hanging=false,waterlogged=false]'), (40, az0 + 2, 'candle[candles=3,lit=true,waterlogged=false]'),
                           (40, az1, 'decorated_pot[cracked=false,facing=west,waterlogged=false]')))
        for z in range(az0, az1 + 1):
            for x in (41, 42):
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
        back = ['barrel[facing=west,open=false]', 'chest[facing=west,type=single,waterlogged=false]', 'bookshelf',
                'barrel[facing=west,open=false]', 'fletching_table', 'barrel[facing=west,open=false]', 'chest[facing=west,type=single,waterlogged=false]']
        for i, z in enumerate(range(az0, az1 + 1)):
            v.set(43, 0, z, back[i % len(back)])
            v.set(43, 1, z, 'decorated_pot[cracked=false,facing=west,waterlogged=false]' if 'chest' in back[i % len(back)]
                  else 'barrel[facing=west,open=false]' if i % 2 else 'bookshelf')
            v.set(43, 2, z, ['lantern[hanging=false,waterlogged=false]', 'decorated_pot[cracked=false,facing=west,waterlogged=false]', 'potted_red_tulip', 'air'][i % 4])
        self.attendant('tool_merchant', 42, 0, dz + 1 if dz + 1 <= az1 else dz - 1)
        # 客の側: 北と南の壁の品 (干し糧・松明・矢・寝袋・縄)
        north = ['hay_block[axis=y]', 'barrel[facing=up,open=false]', 'crafting_table', 'chest[facing=south,type=single,waterlogged=false]', 'fletching_table']
        south = ['smoker[facing=north,lit=false]', 'barrel[facing=up,open=false]', 'white_wool', 'hay_block[axis=y]', 'barrel[facing=up,open=false]']
        tops_n = ['air', 'lantern[hanging=false,waterlogged=false]', 'air', 'candle[candles=2,lit=true,waterlogged=false]', 'air']
        tops_s = ['air', 'potted_azalea_bush', 'white_carpet', 'air', 'lantern[hanging=false,waterlogged=false]']
        for i, x in enumerate(range(ax0, 40)):
            if abs(x - ix) <= 1 and False:
                continue
            v.set(x, 0, az0, north[i % len(north)])
            v.set(x, 1, az0, tops_n[i % len(tops_n)])
            v.set(x, 0, az1, south[i % len(south)])
            v.set(x, 1, az1, tops_s[i % len(tops_s)])
        # 真ん中の陳列台 (半ブロックの台に品)
        v.set(37, 0, dz - 2 if dz - 2 > az0 else dz + 2, '%s_slab[type=top,waterlogged=false]' % w)
        v.set(37, 1, dz - 2 if dz - 2 > az0 else dz + 2, 'lantern[hanging=false,waterlogged=false]')
        # 吊り灯り
        v.set(41, 5, (az0 + az1) // 2, 'lantern[hanging=true,waterlogged=false]')
        # 外: 店先の樽と木箱、看板の旗
        for (x, z) in ((x0 - 2, z0 + 1), (x0 - 2, z1 - 1)):
            v.set(x, 0, z, 'barrel[facing=up,open=false]')
        v.set(x0 - 2, 1, z0 + 1, 'lantern[hanging=false,waterlogged=false]')

    # ---------------------------------------------------------------- 東の大通り: 預かり所
    def storage(self):
        """預かり所 (東の大通りの北、x 32..44 z -16..-7)。戸は南 (大通りへ)。石の床、受付の机の向こうに預かり番、奥は箱の壁"""
        st, v = self.st, self.v
        x0, z0, x1, z1 = 32, -16, 44, -7
        b, ridge = self.fac_body('storage', x0, z0, x1, z1, 'south', floors=2, fh=6, ground='window', infill='polished_andesite', axis='x')
        self.fac_front(b, 'storage')
        dx_, _, dz_, _ = b.doors[0]
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2   # 34..42, -14..-9
        self.room(ax0, az0, ax1, az1, 0, 6, floor='stone', rug=False)
        self.room(ax0, az0, ax1, az1, 7, 5, rug=False)
        # 受付の机 (z -11) と、奥の箱の壁 (z -14、二段)
        self.counter([(x, -11) for x in range(ax0, ax1 + 1)],
                     tops=((ax0, -11, 'lantern[hanging=false,waterlogged=false]'), (dx_ + 2, -11, 'candle[candles=3,lit=true,waterlogged=false]'),
                           (ax1, -11, 'potted_fern')))
        for x in range(ax0, ax1 + 1):
            for z in (-13, -12):
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
            # 下は樽、中の段は箱と樽を交互、箱の上は壺 (箱の上に丸ごとのブロックを置かない)
            v.set(x, 0, az0, 'barrel[facing=south,open=false]')
            v.set(x, 1, az0, 'chest[facing=south,type=single,waterlogged=false]' if x % 2 else 'barrel[facing=south,open=false]')
            v.set(x, 2, az0, 'decorated_pot[cracked=false,facing=south,waterlogged=false]' if x % 2
                  else 'chiseled_bookshelf[facing=south,slot_0_occupied=true,slot_1_occupied=true,slot_2_occupied=false,slot_3_occupied=true,slot_4_occupied=true,slot_5_occupied=false]' if x % 3 == 0 else 'barrel[facing=south,open=false]')
        v.set(ax1, 0, -13, 'lectern[facing=west,has_book=false,powered=false]')
        self.attendant('storage_keeper', dx_ - 1, 0, -13)
        # 客の側: 両わきの鉢と腰掛け
        v.set(ax0, 0, -10, 'potted_flowering_azalea_bush')
        v.set(ax1, 0, -10, 'potted_flowering_azalea_bush')
        v.set(ax0, 0, -9, '%s_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]' % st.wood)
        v.set(ax1, 0, -9, '%s_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]' % st.wood)
        for x in range(ax0 + 1, ax1):
            if abs(x - dx_) > 1:
                v.set(x, 0, -9, 'air') if v.get(x, 0, -9) and 'carpet' not in v.get(x, 0, -9) else None
        v.set((ax0 + ax1) // 2, 5, -12, 'lantern[hanging=true,waterlogged=false]')

    # ---------------------------------------------------------------- 東の大通り: 取引所 (プレイヤーどうしの取引。組合の東)
    def trade_house(self):
        """取引所 (x 82..93 z -26..-13)。戸は南 (東の大通りへ)。中は帳場の机と取引所の番、両わきに品を並べる棚、出し物の掲示の板"""
        st, v = self.st, self.v
        x0, z0, x1, z1 = 82, -26, 93, -13
        b, ridge = self.fac_body('trade', x0, z0, x1, z1, 'south', floors=2, fh=6, ground='shop', infill='polished_andesite', axis='z')
        self.fac_front(b, 'trade')
        dx_, _, dz_, _ = b.doors[0]
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2   # 84..91, -24..-15
        self.room(ax0, az0, ax1, az1, 0, 6, floor='wood', rug=True)
        self.room(ax0, az0, ax1, az1, 7, 5, rug=False)
        # 帳場の机 (z -20) の向こうに番、奥の壁に掲示の板 (額縁) と棚
        # 机の東の端は空けて、番の側へ回れるように
        self.counter([(x, -20) for x in range(ax0, ax1)],
                     tops=((ax0, -20, 'lantern[hanging=false,waterlogged=false]'), (ax1 - 1, -20, 'candle[candles=3,lit=true,waterlogged=false]')))
        for x in range(ax0, ax1 + 1):
            for z in (-22, -21):
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
            v.set(x, 0, az0, 'barrel[facing=south,open=false]')
            v.set(x, 1, az0, 'chiseled_bookshelf[facing=south,slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=true,slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=true]' if x % 2 else 'bookshelf')
            v.set(x, 2, az0, 'decorated_pot[cracked=false,facing=south,waterlogged=false]' if x % 3 == 0 else 'air')
        self.attendant('trade_keeper', dx_, 0, -22)
        # 客の側: 両わきの品の台 (樽と半ブロック) と鉢
        for z in (-18, -16):
            v.set(ax0, 0, z, 'barrel[facing=east,open=false]')
            v.set(ax1, 0, z, 'barrel[facing=west,open=false]')
        v.set(ax0, 0, -17, 'potted_flowering_azalea_bush')
        v.set(ax1, 0, -17, 'potted_flowering_azalea_bush')
        v.set((ax0 + ax1) // 2, 5, -18, 'lantern[hanging=true,waterlogged=false]')

    # ---------------------------------------------------------------- 東の大通り: 薬師の店
    def apothecary(self):
        """薬師の店 (東の大通りの南、x 53..66 z 6..17)。戸は北。机の上に醸造台、奥に薬棚と大釜、吊るした薬草の鉢"""
        st, v = self.st, self.v
        x0, z0, x1, z1 = 53, 6, 66, 17
        b, ridge = self.fac_body('apothecary', x0, z0, x1, z1, 'north', floors=2, fh=6, ground='shop', axis='z',
                                 roof=vox.Mat('mud_bricks', 'mud_brick', 'mud_brick_wall'))
        self.fac_front(b, 'apothecary')
        dx_, _, dz_, _ = b.doors[0]
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2   # 55..64, 8..15
        self.room(ax0, az0, ax1, az1, 0, 6, rug=True)
        self.room(ax0, az0, ax1, az1, 7, 5, rug=False)
        self.counter([(x, 12) for x in range(ax0, ax1 + 1)],
                     tops=((ax0 + 1, 12, 'brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]'),
                           (ax1 - 1, 12, 'brewing_stand[has_bottle_0=false,has_bottle_1=true,has_bottle_2=true]'),
                           (dx_ + 2, 12, 'candle[candles=4,lit=true,waterlogged=false]'), (ax0 + 4, 12, 'potted_red_mushroom'),
                           (ax1 - 3, 12, 'potted_brown_mushroom')))
        for x in range(ax0, ax1 + 1):
            for z in (13, 14):
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
            # 奥の薬棚 (z 15): 下に樽と本棚、上に鉢と蝋燭
            v.set(x, 0, az1, ['barrel[facing=north,open=false]', 'bookshelf', 'cauldron', 'barrel[facing=north,open=false]', 'water_cauldron[level=3]'][x % 5])
            v.set(x, 1, az1, ['potted_fern', 'candle[candles=2,lit=true,waterlogged=false]', 'air', 'potted_blue_orchid', 'air'][x % 5]
                  if 'cauldron' not in v.get(x, 0, az1) else 'air')
            v.set(x, 2, az1, ['air', 'potted_allium', 'air', 'potted_crimson_fungus', 'air'][x % 5])
        v.set(ax0, 0, 14, 'brewing_stand[has_bottle_0=true,has_bottle_1=true,has_bottle_2=true]')
        self.attendant('apothecary', dx_ + 1, 0, 14)
        # 客の側: 両わきの薬草の棚 (鉢)、大釜、腰掛け
        for z in range(az0, 12):
            if abs(z - az0) <= 1 and False:
                continue
            v.set(ax0, 0, z, 'composter[level=7]' if z == az0 + 1 else 'barrel[facing=east,open=false]')
            v.set(ax0, 1, z, ['potted_azalea_bush', 'potted_oxeye_daisy', 'potted_cornflower', 'potted_lily_of_the_valley'][z % 4])
            v.set(ax1, 0, z, 'water_cauldron[level=3]' if z == az0 + 2 else 'bookshelf')
            v.set(ax1, 1, z, ['potted_fern', 'candle[candles=3,lit=true,waterlogged=false]', 'air', 'potted_poppy'][z % 4] if z != az0 + 2 else 'air')
        # 天井から吊るした薬草 (鎖と鉢の代わりに、梁の下の葉と灯り)
        for x in (ax0 + 2, ax1 - 2):
            v.set(x, 5, 10, 'chain[axis=y,waterlogged=false]')
            v.set(x, 4, 10, 'lantern[hanging=true,waterlogged=false]')

    # ---------------------------------------------------------------- 組合の受付
    def guild_desk(self):
        """鯨追いの組合の受付 (入ってすぐ北)。机の向こうに受付の書記。言伝ての板は机の外のまま"""
        st, v = self.st, self.v
        cells = [(x, -25) for x in range(52, 58)] + [(57, z) for z in (-26, -27, -28)]
        self.counter(cells, tops=((53, -25, 'lantern[hanging=false,waterlogged=false]'), (56, -25, 'candle[candles=3,lit=true,waterlogged=false]'),
                                  (57, -27, 'decorated_pot[cracked=false,facing=east,waterlogged=false]')))
        for x in range(52, 57):
            v.set(x, 0, -29, 'bookshelf')
            v.set(x, 1, -29, 'bookshelf' if x % 2 else 'chiseled_bookshelf[facing=south,slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=true,slot_3_occupied=true,slot_4_occupied=true,slot_5_occupied=false]')
            v.set(x, 2, -29, 'air')
            for z in (-28, -27, -26):
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
        v.set(52, 0, -28, 'lectern[facing=south,has_book=false,powered=false]')
        v.set(52, 0, -26, 'barrel[facing=up,open=false]')
        self.attendant('guild_clerk', 54, 0, -27)
        # 二階への梯子 (前の梯子は二階の床で切れていたので、窓のない東の壁ぎわに置き直す)
        for yy in range(0, 9):
            if (v.get(76, yy, -14) or '').startswith('ladder'):
                v.set(76, yy, -14, 'air')
        v.set(75, 7, -14, 'air')
        v.set(76, 6, -14, PLANK % st.wood)
        lad = self.place_ladder(70, -16, 76, -12, 0, 6, door=(51, -22))
        if lad:
            for (ddx, ddz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for yy in (7, 8):
                    s = v.get(lad[0] + ddx, yy, lad[1] + ddz) or ''
                    if 'bed' not in s and 'chest' not in s and s not in ('', 'air') and not self.solid_at(lad[0] + ddx, yy, lad[1] + ddz):
                        v.set(lad[0] + ddx, yy, lad[1] + ddz, 'air')

    # ---------------------------------------------------------------- 鍛冶場: 西の口と鍛冶師の机
    def smithy_front(self):
        """鍛冶場の西の面 (南の大通りに面する) に口を開ける。入ってすぐ北に鍛冶師の机"""
        st, v = self.st, self.v
        for z in range(16, 19):
            for yy in range(0, 4):
                v.set(8, yy, z, 'air')
                v.set(9, yy, z, 'air')
            v.set(8, -1, z, st.trim.full)
            v.set(9, -1, z, st.trim.full)
            v.set(7, 0, z, 'air')
            v.set(7, -1, z, st.trim.full)
        for x in (8, 9):
            v.set(x, 3, 16, st.trim.stairs('north', 'top'))
            v.set(x, 3, 18, st.trim.stairs('south', 'top'))
            v.set(x, 4, 17, st.trim2)
        for z in (15, 19):
            v.fill(7, 0, z, 7, 3, z, st.pilaster)
            v.set(7, 4, z, st.trim.stairs('east', 'top'))
        hanging_sign(v, st, 8, 4, 17, 'west')
        self.mark('label_smithy', 6, 6, 17)
        v.set(7, 2, 14, 'lantern[hanging=false,waterlogged=false]') if False else None
        self.openings.append((8, 17, 'west'))
        # 鍛冶師の机 (z 14、x 10..15) と横 (x 15)
        self.counter([(x, 14) for x in range(10, 16)] + [(15, z) for z in (13, 12, 10)],
                     tops=((11, 14, 'lantern[hanging=false,waterlogged=false]'), (14, 14, 'anvil[facing=west]') if False else (14, 14, 'candle[candles=2,lit=true,waterlogged=false]')))
        v.set(15, 0, 11, 'grindstone[face=floor,facing=north]')
        for x in range(10, 15):
            for z in range(11, 14):
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
        v.set(10, 0, 11, 'smithing_table')
        v.set(13, 0, 11, 'anvil[facing=east]')
        self.attendant('smith', 12, 0, 12)

    # ---------------------------------------------------------------- 南の大通り: 訓練場
    def training_yard(self):
        """訓練場 (鍛冶場の南、x 7..45 z 35..61)。高さ 4 の塀に囲まれた庭。西の門ははじめから開いている (印 open_training)。
        入ってすぐ北に師範の東屋と机、木偶の列、手合わせの輪、弓の的、武具の掛け台"""
        st, v, rng = self.st, self.v, self.rng
        x0, z0, x1, z1 = 7, 35, 45, 61
        gz0, gz1 = 46, 50
        # 床
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                v.set(x, -1, z, rng.choice(['coarse_dirt', 'packed_mud', 'gravel', 'coarse_dirt', 'rooted_dirt']))
                for yy in range(0, 12):
                    if v.get(x, yy, z) is not None:
                        v.b.pop((x, yy, z), None)
        # 塀 (高さ 3 + 胸壁) と控え柱
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x not in (x0, x1) and z not in (z0, z1):
                    continue
                v.set(x, -1, z, st.base.full)
                for yy in range(0, 3):
                    v.set(x, yy, z, st.base.full if yy == 0 else (st.wall_main if rng.random() > 0.12 else rng.choice(st.wall_mix2)))
                k = (x - x0) + (z - z0)
                v.set(x, 3, z, st.trim.wall() if k % 2 == 0 else st.trim.slab('bottom'))
        for side in ('north', 'south', 'east', 'west'):
            cells, out = vox._facade_cells(x0, z0, x1, z1, side)
            dx, dz = DX[out]
            for i, (cx, cz) in enumerate(cells):
                if i % 6 == 3 and 2 < i < len(cells) - 3 and not (side == 'west' and gz0 - 2 <= cz <= gz1 + 2):
                    v.fill(cx + dx, 0, cz + dz, cx + dx, 2, cz + dz, st.base.full)
                    v.set(cx + dx, 3, cz + dz, st.base.stairs(OPP[out], 'bottom'))
                    v.set(cx, 4, cz, 'lantern[hanging=false,waterlogged=false]') if side != 'west' else None
        for (cx, cz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            turret(v, st, cx, cz, 0, 8, half=1, rise=3)
        # 西の門: 柱・まぐさ・旗 (はじめから開いている)
        for z in range(gz0, gz1 + 1):
            for yy in range(0, 5):
                v.set(x0, yy, z, 'air')
            v.set(x0, -1, z, st.pave_trim)
            v.set(x0 - 1, -1, z, st.pave_trim)
        for z in (gz0 - 1, gz1 + 1):
            for x in (x0 - 1, x0, x0 + 1):
                v.fill(x, 0, z, x, 5, z, st.base.full if x == x0 else st.pilaster)
            v.set(x0, 6, z, st.trim.wall())
            v.set(x0, 7, z, 'lantern[hanging=false,waterlogged=false]')
        for z in range(gz0, gz1 + 1):
            v.set(x0, 5, z, st.trim.full)
            v.set(x0 - 1, 5, z, st.trim.stairs('east', 'top'))
            v.set(x0 + 1, 5, z, st.trim.stairs('west', 'top'))
            v.set(x0, 6, z, st.trim.wall() if (z - gz0) % 2 == 0 else st.trim.slab('bottom'))
        v.set(x0, 4, gz0, st.trim.stairs('north', 'top'))
        v.set(x0, 4, gz1, st.trim.stairs('south', 'top'))
        mid = (gz0 + gz1) // 2
        v.set(x0, 6, mid, st.trim2)
        v.set(x0 - 1, 5, mid, '%s_wall_banner[facing=west]' % st.banner_color)
        # 門ははじめから開いている (鉄格子なし)。印 open_training は門の真ん中 (今は読む仕組みはない)
        self.mark('open_training', x0, 0, mid)
        self.tablet(x0 - 1, 0, gz0 - 3, 'west', 'training')
        v.set(x0 - 1, -1, gz0 - 3, st.trim.full)
        self.openings.append((x0, mid, 'west'))
        # 門から東へ石の道
        for x in range(x0 + 1, 22):
            for z in range(gz0 + 1, gz1):
                v.set(x, -1, z, st.pave_trim if z == mid else v.pick(st.pave))
        # 師範の東屋 (北西の隅、x 8..14 z 36..44): 机 (z 43) の向こうに師範、奥に武具の掛け台
        w = st.wood
        self.counter([(x, 43) for x in range(8, 14)] + [(13, z) for z in (42, 41, 40, 39)],
                     tops=((9, 43, 'lantern[hanging=false,waterlogged=false]'), (12, 43, 'candle[candles=3,lit=true,waterlogged=false]')))
        for x in range(8, 13):
            v.set(x, 0, 38, '%s_fence' % w)
            v.set(x, 1, 38, 'lightning_rod[facing=up,powered=false,waterlogged=false]' if x % 2 else '%s_fence' % w)
            v.set(x, 2, 38, 'air')
            for z in range(39, 43):
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
                v.set(x, -1, z, PLANK % w)
        v.fill(8, 2, 38, 12, 2, 38, '%s_slab[type=bottom,waterlogged=false]' % w)
        v.set(8, 0, 36, 'grindstone[face=floor,facing=south]')
        v.set(9, 0, 36, 'anvil[facing=east]')
        v.set(10, 0, 36, 'barrel[facing=up,open=false]')
        v.set(11, 0, 36, 'barrel[facing=up,open=false]')
        v.set(12, 0, 36, 'chest[facing=south,type=single,waterlogged=false]')
        for (px, pz) in ((14, 37), (14, 44)):
            v.fill(px, 0, pz, px, 3, pz, '%s_log[axis=y]' % w)
        for x in range(8, 15):
            v.set(x, 3, 37, '%s_log[axis=x]' % w)
            v.set(x, 3, 44, '%s_log[axis=x]' % w)
        gable_roof(v, st, 8, 37, 14, 44, 4, axis='z', steep=False, finial=False)
        v.set(11, 3, 41, 'lantern[hanging=true,waterlogged=false]')
        self.attendant('training_master', 10, 0, 41)
        # 木偶の列 (柵の脚・わらの胴・彫った南瓜の頭)
        for x in range(22, 41, 4):
            for z in (38, 42):
                v.set(x, 0, z, '%s_fence' % w)
                v.set(x, 1, z, 'hay_block[axis=y]')
                v.set(x, 2, z, 'carved_pumpkin[facing=west]')
        # 手合わせの輪 (砂の円と縁石)
        cx, cz, r = 31, 51, 4
        for x in range(cx - r - 1, cx + r + 2):
            for z in range(cz - r - 1, cz + r + 2):
                d = math.hypot(x - cx, z - cz)
                if d <= r - 0.5:
                    v.set(x, -1, z, 'sand' if (x + z) % 3 else 'smooth_sandstone')
                elif d <= r + 0.5:
                    v.set(x, -1, z, st.pave_trim)
                    v.set(x, 0, z, st.trim.slab('bottom')) if (x + z) % 2 == 0 and abs(z - cz) >= 2 else None
        for (bx, bz) in ((cx - r - 2, cz - 2), (cx + r + 2, cz - 2)):
            pass
        # 弓の的 (東の塀ぎわ) と射る位置の柵
        for z in (52, 55, 58):
            v.set(44, 0, z, 'hay_block[axis=y]')
            v.set(44, 1, z, 'target[power=0]')
            v.set(44, 2, z, 'target[power=0]')
        for z in range(52, 60):
            v.set(39, 0, z, '%s_fence' % w) if z % 3 else None
        # 南の塀ぎわの腰掛けと樽、旗、灯り、木
        bench(v, st, 10, 0, 60, 'south', 3)
        bench(v, st, 17, 0, 60, 'south', 3)
        for x in (23, 24, 26):
            v.set(x, 0, 60, 'barrel[facing=up,open=false]')
        v.set(24, 1, 60, 'barrel[facing=up,open=false]')
        for x in range(12, 44, 8):
            v.set(x, 2, z0 + 1, '%s_wall_banner[facing=south]' % st.banner_color)
            v.set(x, 2, z1 - 1, '%s_wall_banner[facing=north]' % st.banner_color)
        lamp_post(v, st, 21, 0, 36, h=4)
        lamp_post(v, st, 43, 0, 37, h=4)
        lamp_post(v, st, 9, 0, 57, h=4)
        tree(v, 40, 0, 47, st.trunk, st.leaves, h=5, r=2)
        self.claim(x0 - 2, z0 - 2, x1 + 2, z1 + 2, 'training_yard')
        self.fac_names.append('training_yard')

    # ---------------------------------------------------------------- 西の大通り: 宿「鐘の宿」
    def inn(self):
        """宿「鐘の宿」(西の大通りの南の 1 列目、x -44..-8 z 6..26)。二階建て、屋根に小さな鐘楼。
        一階: 入ってすぐ東に受付 (女将)、西に食堂 (長机・暖炉・酒の台)、東の奥に談話室と台所、南の壁ぞいに二階への階段。
        二階: 真ん中の廊下の両側に客室 9 (寝台・箱・敷物・灯り)。寝台の頭に印 inn_bed, inn_bed_2 …"""
        st, v = self.st, self.v
        x0, z0, x1, z1 = -44, 6, -8, 26
        b, ridge = self.fac_body('inn', x0, z0, x1, z1, 'north', floors=2, fh=6, ground='shop', axis='x', dormers=3)
        vox.timber_upper(v, st, b)
        self.fac_front(b, 'inn')
        dx_, _, dz_, _ = b.doors[0]   # (-26, 0, 7)
        w = st.wood
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2   # -42..-10, 8..24
        self.room(ax0, az0, ax1, az1, 0, 6, rug=False)
        # ---- 一階
        # 受付 (x -23 の机、z 14 の机、x -18 の棚で囲う)
        self.counter([(-23, z) for z in range(8, 14)] + [(x, 14) for x in range(-23, -18)],
                     tops=((-23, 9, 'lantern[hanging=false,waterlogged=false]'), (-23, 11, 'bell[attachment=floor,facing=east,powered=false]'),
                           (-23, 13, 'candle[candles=3,lit=true,waterlogged=false]'), (-20, 14, 'potted_red_tulip')))
        for z in range(8, 15):
            v.set(-18, 0, z, 'bookshelf' if z % 2 else 'barrel[facing=west,open=false]')
            v.set(-18, 1, z, 'chiseled_bookshelf[facing=west,slot_0_occupied=true,slot_1_occupied=true,slot_2_occupied=true,slot_3_occupied=false,slot_4_occupied=true,slot_5_occupied=true]')
            v.set(-18, 2, z, 'air')
        for x in range(-22, -18):
            for z in range(8, 14):
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
        v.set(-19, 0, 8, 'lectern[facing=west,has_book=false,powered=false]')
        v.set(-22, 0, 8, 'barrel[facing=up,open=false]')
        v.set(-22, 1, 8, 'potted_fern')
        self.attendant('innkeeper', -21, 0, 11)
        # 入口の広間の敷物 (x -28..-24)
        for x in range(-28, -23):
            for z in range(8, 21):
                if v.get(x, 0, z) in (None, 'air'):
                    v.set(x, 0, z, 'red_carpet' if x == -26 else 'brown_carpet')
        # 食堂 (x -42..-30): 長机 4、暖炉 (西の壁)、酒の台 (z 21) と樽 (z 24)
        for (tx, tz) in ((-40, 11), (-35, 11), (-40, 16), (-35, 16)):
            self.table(tx, 0, tz, length=4, axis='x')
        for z in (15, 16, 17):
            v.set(-42, 0, z, 'air')
        for yy in range(0, 6):
            v.set(-42, yy, 15, 'bricks')
            v.set(-42, yy, 17, 'bricks')
        v.set(-42, 0, 16, 'campfire[facing=east,lit=true,signal_fire=false,waterlogged=false]')
        v.set(-42, 1, 16, 'air')
        for yy in range(2, 6):
            v.set(-42, yy, 16, 'bricks')
        for z in (15, 16, 17):
            v.set(-41, 2, z, 'brick_stairs[facing=west,half=top,shape=straight,waterlogged=false]')
            v.set(-41, 0, z, 'air')
            v.set(-41, 1, z, 'air')
        v.set(-42, 3, 14, 'air') if False else None
        self.counter([(x, 21) for x in range(-41, -34)],
                     tops=((-40, 21, 'candle[candles=3,lit=true,waterlogged=false]'), (-37, 21, 'lantern[hanging=false,waterlogged=false]')))
        for x in range(-42, -33):
            v.set(x, 0, 24, 'barrel[facing=north,open=false]')
            v.set(x, 1, 24, 'barrel[facing=north,open=false]' if x % 2 else 'air')
            v.set(x, 2, 24, 'air')
            for z in (22, 23):
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
        for x in range(-41, -34):
            v.set(x, 0, 20, 'air') if v.get(x, 0, 20) and 'carpet' not in v.get(x, 0, 20) else None
        for x in (-41, -38, -35):
            v.set(x, 0, 19, '%s_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]' % w)  # 酒の台の前の腰掛け
        # 談話室 (x -17..-10, z 8..15): 肘掛け椅子、低い机、本棚
        for (x, z, f) in ((-16, 10, 'west'), (-16, 12, 'west'), (-12, 10, 'east'), (-12, 12, 'east')):
            v.set(x, 0, z, '%s_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]' % (w, f))
        for z in (10, 11, 12):
            v.set(-14, 0, z, '%s_slab[type=top,waterlogged=false]' % w)
        v.set(-14, 1, 11, 'potted_flowering_azalea_bush')
        for x in range(-17, -9):
            for z in range(13, 16):
                if v.get(x, 0, z) in (None, 'air'):
                    v.set(x, 0, z, 'white_carpet' if (x + z) % 2 else 'light_gray_carpet')
        # 談話室と台所のあいだの仕切り (z 16、x -14 に口)
        for x in range(-17, -9):
            for yy in range(0, 5):
                v.set(x, yy, 16, 'air' if x == -14 and yy < 2 else PLANK % w if x not in (-17, -10) else '%s_log[axis=y]' % w)
        v.set(-14, 2, 16, '%s_log[axis=x]' % w)
        # 台所 (x -17..-10, z 17..24)
        kitchen = [(-10, 18, 'smoker[facing=west,lit=true]'), (-10, 19, 'furnace[facing=west,lit=false]'), (-10, 20, 'water_cauldron[level=3]'),
                   (-10, 21, 'crafting_table'), (-10, 22, 'barrel[facing=west,open=false]'), (-10, 23, 'barrel[facing=up,open=false]'),
                   (-10, 24, 'hay_block[axis=y]'), (-11, 24, 'hay_block[axis=y]'), (-12, 24, 'composter[level=4]'), (-13, 24, 'barrel[facing=north,open=false]'),
                   (-17, 18, 'barrel[facing=east,open=false]'), (-17, 19, 'chest[facing=east,type=single,waterlogged=false]')]
        for (x, z, s) in kitchen:
            v.set(x, 0, z, s)
            v.set(x, 1, z, 'air')
        v.set(-11, 1, 24, 'pumpkin')
        v.set(-10, 1, 23, 'cake[bites=2]')
        self.table(-15, 0, 21, length=3, axis='x')
        # 階段 (南の壁ぞい、x -31..-25、z 23..24、東へ上る) と、二階の床の穴の柵
        self.room(ax0, az0, ax1, az1, 7, 6, rug=False, shelves=False, beams=False, wall_props=False)
        cleared = self.stair_run(-31, 23, 0, 7, 'east', width=2)
        holes = [(x, z) for (x, yy, z) in cleared if yy == 6]
        self.rail_hole(holes, 6, skip={(-24, 23), (-24, 24)})
        # ---- 二階 (床 y 6、立つ所 y 7、天井 y 12): 廊下 z 15..18、北の客室 z 8..13、南の客室 z 20..24
        y2 = 7
        pw = PLANK % w
        post = '%s_log[axis=y]' % w

        def wall(xa, za, xb, zb, door_at=None):
            for x in range(min(xa, xb), max(xa, xb) + 1):
                for z in range(min(za, zb), max(za, zb) + 1):
                    for yy in range(y2, y2 + 5):
                        if door_at is not None and (x, z) == door_at and yy < y2 + 2:
                            v.set(x, yy, z, 'air')
                        else:
                            v.set(x, yy, z, PLANK % w if yy == y2 + 4 else post if (x + z) % 4 == 0 else 'calcite')
        north = [(-42, -37), (-35, -30), (-28, -23), (-21, -16), (-14, -10)]
        south = [(-42, -38), (-36, -33), (-21, -16), (-14, -10)]
        # 北の客室の前の壁 (z 14) と仕切り
        for (a, c) in north:
            wall(a, 14, c, 14, door_at=((a + c) // 2, 14))
        for x in (-36, -29, -22, -15):
            wall(x, 8, x, 14)
        # 南の客室の前の壁 (z 19) と仕切り (x -32 と x -22 は踊り場との境)
        for (a, c) in south:
            wall(a, 19, c, 19, door_at=((a + c) // 2, 19))
        for x in (-37, -15):
            wall(x, 20, x, 24)
        for x in (-32, -22):
            wall(x, 19, x, 24)
        self.inn_corridor(ax0, ax1, y2, north_doors=[(a + c) // 2 for (a, c) in north], south_doors=[(a + c) // 2 for (a, c) in south])
        # 客室の中: 寝台 (奥の壁ぎわ)、枕もとの樽と蝋燭、箱、敷物、灯り、鉢
        beds = 0
        colors = ['red', 'white', 'light_gray', 'brown', 'red', 'white', 'light_gray', 'brown', 'red']
        for (a, c) in north:
            beds = self.inn_room(a, 8, c, 13, 'north', beds, colors[beds % len(colors)])
        for (a, c) in south:
            beds = self.inn_room(a, 20, c, 24, 'south', beds, colors[beds % len(colors)])
        # 屋根の上の小さな鐘楼 (宿の名の鐘)
        cx, cz = dx_, (z0 + z1) // 2
        top = ridge
        for x in range(cx - 1, cx + 2):
            for z in range(cz - 1, cz + 2):
                v.set(x, top, z, st.trim.full)
        for (px, pz) in ((cx - 1, cz - 1), (cx + 1, cz - 1), (cx - 1, cz + 1), (cx + 1, cz + 1)):
            v.fill(px, top + 1, pz, px, top + 3, pz, '%s_log[axis=y]' % w)
        for x in range(cx - 1, cx + 2):
            for z in range(cz - 1, cz + 2):
                v.set(x, top + 4, z, st.trim.full)
        for (ddx, ddz, f) in ((-1, 0, 'east'), (1, 0, 'west'), (0, -1, 'south'), (0, 1, 'north')):
            v.set(cx + ddx, top + 5, cz + ddz, st.roof.stairs(f, 'bottom'))
        for (ddx, ddz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            v.set(cx + ddx, top + 5, cz + ddz, st.roof.slab('bottom'))
        v.set(cx, top + 5, cz, st.roof.full)
        v.set(cx, top + 6, cz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
        v.set(cx, top + 3, cz, 'bell[attachment=ceiling,facing=north,powered=false]')
        for yy in range(top + 1, top + 3):
            v.set(cx, yy, cz, 'air')
        # 外: 戸の両わきの灯りの柱、植え込み、腰掛け
        for s in (-1, 1):
            lamp_post(v, st, dx_ + 4 * s, 0, z0 - 2, h=3)
            v.set(dx_ + 6 * s, 0, z0 - 1, st.trim.full)
            v.set(dx_ + 6 * s, 1, z0 - 1, '%s[persistent=true,distance=1,waterlogged=false]' % st.leaves)

    def inn_corridor(self, ax0, ax1, y2, north_doors, south_doors):
        """宿の二階の廊下 (z 15..18、x ax0..ax1)。長く暗く見えないように:
        - 客室の仕切りの筋 (x -36・-29・-22・-15) に梁を渡し、廊下を区切る (柱は立てない)。梁の間の天井は明るい白樺
        - 両端の壁に透明な窓 (2×2) と窓の腰掛け、灯りは鎖で下げて 4 ごと
        - 戸の前を空けて、壁ぎわに小卓 (鉢・蝋燭・灯り)・腰掛け・本棚
        - 階段の上の踊り場 (x -31..-23, z 20..22) に小さな談話の席"""
        st, v = self.st, self.v
        w = st.wood
        log = '%s_log' % w
        arches = (-36, -29, -22, -15)
        y_top = y2 + 4     # 壁の上の段 (天井の板は y2 + 5)
        # 天井: 梁のあいだは白樺の板
        for x in range(ax0, ax1 + 1):
            for z in (15, 16, 17, 18):
                v.set(x, y2 + 5, z, 'birch_planks')
        # 床: 真ん中の 2 列は赤い敷物、縁は茶
        for x in range(ax0, ax1 + 1):
            for z in (15, 16, 17, 18):
                if v.get(x, y2, z) in (None, 'air'):
                    v.set(x, y2, z, 'red_carpet' if z in (16, 17) else 'brown_carpet')
        # 区切りの梁: 廊下の幅いっぱいに渡す (頭より上、y2 + 3。柱は立てず、廊下を狭めない)。梁の上は板で天井まで
        for x in arches:
            for z in (15, 16, 17, 18):
                v.set(x, y2 + 3, z, 'stripped_%s[axis=z]' % log)
                v.set(x, y2 + 4, z, PLANK % w)
        # 吊り灯り (梁のあいだ、鎖で 1 下げる)
        xs = sorted(set([ax0 + 2] + [a + d for a in arches for d in (-3, 3)] + [ax1 - 2]))
        for x in xs:
            if ax0 <= x <= ax1 and x not in arches:
                v.set(x, y_top, 16 if x % 2 else 17, 'chain[axis=y,waterlogged=false]')
                v.set(x, y_top - 1, 16 if x % 2 else 17, 'lantern[hanging=true,waterlogged=false]')
        # 両端の窓 (内の面を透明な硝子、外の面を空ける) と窓の腰掛け
        for (xo, xi, xs_, face) in ((ax0 - 2, ax0 - 1, ax0, 'west'), (ax1 + 2, ax1 + 1, ax1, 'east')):
            for z in (16, 17):
                for yy in (y2 + 1, y2 + 2):
                    v.set(xi, yy, z, 'glass')
                    v.set(xo, yy, z, 'air')
                v.set(xs_, y2, z, '%s_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]' % (w, face))
            for z in (15, 18):
                v.set(xs_, y2, z, 'potted_flowering_azalea_bush')
        # 壁ぎわの飾り (戸の前と柱は空ける)
        side_items = [
            ('%s_slab[type=top,waterlogged=false]' % w, 'lantern[hanging=false,waterlogged=false]'),
            ('%s_stairs[facing={back},half=bottom,shape=straight,waterlogged=false]' % w, None),
            ('%s_slab[type=top,waterlogged=false]' % w, 'potted_red_tulip'),
            ('bookshelf', 'candle[candles=3,lit=true,waterlogged=false]'),
            (None, None),
            ('%s_slab[type=top,waterlogged=false]' % w, 'potted_fern'),
        ]
        for (z, back, doors) in ((15, 'north', north_doors), (18, 'south', south_doors)):
            i = 0
            for x in range(ax0 + 2, ax1 - 1):
                if any(abs(x - d) <= 1 for d in doors):
                    continue
                if z == 18 and -31 <= x <= -23:
                    continue   # 踊り場への口
                blk, top = side_items[i % len(side_items)]
                i += 1
                if blk is None:
                    continue
                v.set(x, y2, z, blk.replace('{back}', back))
                if top:
                    v.set(x, y2 + 1, z, top)
        # 踊り場の談話の席: 低い卓、肘掛け椅子 2、鉢、本棚
        v.set(-28, y2, 20, '%s_slab[type=top,waterlogged=false]' % w)
        v.set(-28, y2 + 1, 20, 'candle[candles=3,lit=true,waterlogged=false]')
        v.set(-29, y2, 20, '%s_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]' % w)
        v.set(-27, y2, 20, '%s_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]' % w)
        for x in range(-31, -22):
            if v.get(x, y2, 21) in (None, 'air') and v.get(x, y2, 22) in (None, 'air'):
                v.set(x, y2, 21, 'light_gray_carpet' if x % 2 else 'white_carpet')
        v.set(-31, y2, 20, 'potted_flowering_azalea_bush')
        v.set(-23, y2, 20, 'bookshelf')
        v.set(-23, y2 + 1, 20, 'lantern[hanging=false,waterlogged=false]')

    def inn_room(self, a, za, c, zb, back, beds, color):
        st, v = self.st, self.v
        w = st.wood
        y2 = 7
        zback = za if back == 'north' else zb
        zin = za + 1 if back == 'north' else zb - 1
        f = back
        bx = a + 1 if c - a >= 3 else a
        v.set(bx, y2, zback, '%s_bed[facing=%s,part=head,occupied=false]' % (color, f))
        v.set(bx, y2, zin, '%s_bed[facing=%s,part=foot,occupied=false]' % (color, f))
        v.set(bx, y2 + 1, zback, 'air')
        name = 'inn_bed' if beds == 0 else 'inn_bed_%d' % (beds + 1)
        self.mark(name, bx, y2, zback)
        if bx - 1 >= a:
            v.set(bx - 1, y2, zback, 'barrel[facing=up,open=false]')
            v.set(bx - 1, y2 + 1, zback, 'candle[candles=2,lit=true,waterlogged=false]')
        v.set(c, y2, zback, 'chest[facing=%s,type=single,waterlogged=false]' % OPP[back])
        if c - a >= 4:
            v.set(c - 1, y2, zback, 'barrel[facing=%s,open=false]' % OPP[back])
            v.set(c - 1, y2 + 1, zback, 'potted_' + self.rng.choice(st.pot_flowers))
        # 敷物 (寝台の横)
        for x in range(bx + 1, c):
            for z in (zin, zin + (1 if back == 'north' else -1)):
                if za <= z <= zb and v.get(x, y2, z) in (None, 'air'):
                    v.set(x, y2, z, '%s_carpet' % color)
        v.set((a + c) // 2, y2 + 4, (za + zb) // 2, 'lantern[hanging=true,waterlogged=false]')
        return beds + 1

    # ---------------------------------------------------------------- 西の大通り: 子の止まり木
    def perch(self):
        """子の止まり木 (西門の内の南)。番小屋 (x -94..-84 z 6..16、戸は北) と、東の降り場 (丸い台、真ん中に止まり木の柱)"""
        st, v = self.st, self.v
        w = st.wood
        x0, z0, x1, z1 = -94, 6, -84, 16
        b, ridge = self.fac_body('perch_lodge', x0, z0, x1, z1, 'north', floors=2, fh=5, ground='window', axis='z')
        self.fac_front(b, 'perch')
        dx_, _, dz_, _ = b.doors[0]
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2   # -92..-86, 8..14
        self.room(ax0, az0, ax1, az1, 0, 5, rug=True)
        self.room(ax0, az0, ax1, az1, 6, 4, rug=False, beams=False)
        self.counter([(x, 11) for x in range(ax0, ax1 + 1)],
                     tops=((ax0, 11, 'lantern[hanging=false,waterlogged=false]'), (ax1, 11, 'potted_fern'), (dx_ + 2, 11, 'candle[candles=2,lit=true,waterlogged=false]')))
        for x in range(ax0, ax1 + 1):
            for z in (12, 13):
                v.set(x, 0, z, 'air')
                v.set(x, 1, z, 'air')
            v.set(x, 0, az1, ['hay_block[axis=y]', 'bone_block[axis=y]', 'barrel[facing=north,open=false]', 'chest[facing=north,type=single,waterlogged=false]'][x % 4])
            v.set(x, 1, az1, ['air', 'lantern[hanging=false,waterlogged=false]', 'potted_azalea_bush', 'air'][x % 4])
        self.attendant('perch_keeper', dx_ + 1, 0, 13)
        for z in range(az0, 11):
            v.set(ax0, 0, z, 'barrel[facing=east,open=false]' if z % 2 else 'hay_block[axis=y]')
            v.set(ax1, 0, z, 'bookshelf' if z % 2 else 'cartography_table')
        # 降り場 (丸い台、中心 -76, 21、半径 5)
        cx, cz, r = -76, 21, 5
        for x in range(cx - r - 1, cx + r + 2):
            for z in range(cz - r - 1, cz + r + 2):
                d = math.hypot(x - cx, z - cz)
                for yy in range(0, 10):
                    if v.get(x, yy, z) is not None and d <= r + 1.5:
                        v.b.pop((x, yy, z), None)
                if d <= r - 0.5:
                    v.set(x, -1, z, st.base.full)
                    v.set(x, 0, z, st.trim.full if (int(d + 0.5) in (r - 1, 2)) else v.pick(st.pave))
                elif d <= r + 0.5:
                    v.set(x, -1, z, st.base.full)
                    ddx, ddz = cx - x, cz - z
                    f = ('east' if ddx > 0 else 'west') if abs(ddx) >= abs(ddz) else ('south' if ddz > 0 else 'north')
                    v.set(x, 0, z, st.base.stairs(f, 'bottom'))
                elif d <= r + 1.5:
                    v.set(x, -1, z, st.pave_trim)
        # 止まり木の柱 (太い黒い木、上に横木、鎖の灯り、骨の飾り)
        log = '%s_log' % w
        v.fill(cx, 1, cz, cx, 10, cz, '%s[axis=y]' % log)
        for (ddx, ddz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            v.set(cx + ddx, 1, cz + ddz, st.base.stairs(OPP[{(1, 0): 'east', (-1, 0): 'west', (0, 1): 'south', (0, -1): 'north'}[(ddx, ddz)]], 'bottom'))
        for x in range(cx - 4, cx + 5):
            v.set(x, 10, cz, '%s[axis=x]' % log)
        for z in range(cz - 2, cz + 3):
            v.set(cx, 9, z, '%s[axis=z]' % log) if z != cz else None
        for x in (cx - 4, cx + 4):
            v.set(x, 9, cz, 'chain[axis=y,waterlogged=false]')
            v.set(x, 8, cz, 'lantern[hanging=true,waterlogged=false]')
            v.set(x, 11, cz, 'bone_block[axis=y]')
        for (ddx, ddz, f) in ((1, 0, 'east'), (-1, 0, 'west'), (0, 1, 'south'), (0, -1, 'north')):
            v.set(cx + ddx, 6, cz + ddz, '%s_wall_banner[facing=%s]' % (st.banner_color, f))
        v.set(cx, 11, cz, st.trim.wall())
        v.set(cx, 12, cz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
        self.mark('perch', cx, 1, cz + 3)
        # 台のまわり: 水桶とわら、灯りの柱
        for (x, z) in ((cx - 7, cz - 4), (cx + 7, cz + 4)):
            lamp_post(v, st, x, 0, z, h=4)
        v.set(cx - 7, 0, cz + 3, 'water_cauldron[level=3]')
        v.set(cx - 7, 0, cz + 4, 'hay_block[axis=y]')
        v.set(cx - 8, 0, cz + 4, 'hay_block[axis=y]')
        v.set(cx - 7, 1, cz + 4, 'hay_block[axis=y]')
        # 番小屋の南の庭 (z 19..27): 木と花壇
        flowerbed(v, st, -93, 20, -86, 26, density=0.5)
        tree(v, -89, 0, 23, st.trunk2, st.leaves2, h=5, r=2)
        self.claim(cx - r - 1, cz - r - 1, cx + r + 1, cz + r + 1, 'perch_ring')

    # ---------------------------------------------------------------- 裏通り: 古井戸
    def old_well(self):
        """古井戸 (南西の街区の東の端、x -64..-53 z 43..53 の小さな広場)。錆びた鉄の格子で閉じた井戸。印 old_well は格子の上"""
        st, v, rng = self.st, self.v, self.rng
        x0, z0, x1, z1 = -64, 43, -53, 53
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                v.set(x, -1, z, rng.choice(['mossy_cobblestone', 'cobblestone', 'gravel', 'mossy_stone_bricks', 'cracked_stone_bricks']))
                for yy in range(0, 12):
                    v.b.pop((x, yy, z), None)
        cx, cz = -58, 48
        # 井戸の縁 (5x5 の輪、高さ 1) と、格子 (鉄の跳ね上げ戸)
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                if max(abs(x - cx), abs(z - cz)) == 2:
                    v.set(x, -1, z, 'mossy_cobblestone')
                    v.set(x, 0, z, rng.choice(['mossy_cobblestone', 'cobblestone', 'mossy_stone_bricks']))
                else:
                    v.set(x, -1, z, 'iron_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]')
        # 格子の下の深い穴 (暗い水)
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                ring = max(abs(x - cx), abs(z - cz)) == 2
                for yy in range(-9, -1):
                    v.set(x, yy, z, 'mossy_cobblestone' if ring or yy == -9 else ('water[level=0]' if yy <= -7 else 'cave_air'))
        # 屋根の柱と小さな屋根、鎖
        for (px, pz) in ((cx - 2, cz - 2), (cx + 2, cz - 2), (cx - 2, cz + 2), (cx + 2, cz + 2)):
            v.fill(px, 1, pz, px, 3, pz, '%s_fence' % st.wood)
        for x in range(cx - 3, cx + 4):
            for z in range(cz - 3, cz + 4):
                d = max(abs(x - cx), abs(z - cz))
                if d == 3:
                    v.set(x, 4, z, st.roof.slab('bottom'))
                elif d == 2:
                    v.set(x, 4, z, st.roof.full)
                elif d == 1:
                    v.set(x, 5, z, st.roof.slab('bottom'))
        v.set(cx, 5, cz, st.roof.full)
        v.set(cx, 4, cz, '%s_planks' % st.wood)
        for d in range(-1, 2):
            v.set(cx + d, 4, cz, '%s_planks' % st.wood)
        v.fill(cx, 1, cz, cx, 3, cz, 'chain[axis=y,waterlogged=false]')
        self.mark('old_well', cx, -1, cz)
        # まわり: 古い桶・腰掛け・苔・灯り・碑
        v.set(cx + 3, 0, cz - 3, 'cauldron')
        v.set(cx - 4, 0, cz + 3, 'composter[level=2]')
        bench(v, st, x0 + 1, 0, z1 - 1, 'south', 3)
        for (x, z) in ((x0 + 1, z0 + 1), (x0 + 2, z0 + 1), (x0 + 1, z0 + 2)):
            v.set(x, 0, z, 'moss_carpet')
        lamp_post(v, st, x1 - 1, 0, z0 + 1, h=3)
        flowerbed(v, st, x0, z0 + 4, x0 + 2, z0 + 7, density=0.4)
        self.tablet(x1, 0, cz + 4, 'east', 'well')
        self.claim(cx - 4, cz - 4, cx + 4, cz + 4, 'old_well')
        # 井戸の下の迷路と宝 (backalley.well_maze。縁の内のレバーで東の格子が開く)
        self.well_maze(cx, cz)

    # ---------------------------------------------------------------- 裏通り: 闇市
    def black_market(self):
        """闇市 (南東の街区の西、x 53..63 z 40..50、戸は西の横通りへ)。黒い石の家、魂の灯り、紫の敷物、蜘蛛の巣"""
        st, v = self.st, self.v
        x0, z0, x1, z1 = 53, 40, 63, 50
        b, ridge = self.fac_body('black_market', x0, z0, x1, z1, 'west', floors=2, fh=5, ground='window', infill=st.accent.full,
                                 axis='z', roof=st.roof_alts[0])
        self.fac_front(b, 'black_market', banners=False)
        dx_, _, dz_, _ = b.doors[0]
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2   # 55..61, 42..48
        self.room(ax0, az0, ax1, az1, 0, 5, rug=False, beams=False, shelves=False, wall_props=False)
        for x in range(ax0, ax1 + 1):
            for z in range(az0, az1 + 1):
                v.set(x, 0, z, 'purple_carpet' if (x + z) % 3 else 'black_carpet')
        self.counter([(59, z) for z in range(az0, az1 + 1)],
                     tops=((59, az0, 'soul_lantern[hanging=false,waterlogged=false]'), (59, az0 + 2, 'amethyst_cluster[facing=up,waterlogged=false]'),
                           (59, az1 - 1, 'purple_candle[candles=3,lit=true,waterlogged=false]'), (59, az1, 'skeleton_skull[rotation=12]')))
        for z in range(az0, az1 + 1):
            v.set(60, 0, z, 'air')
            v.set(60, 1, z, 'air')
            v.set(61, 0, z, ['chest[facing=west,type=single,waterlogged=false]', 'barrel[facing=west,open=false]', 'ender_chest[facing=west,waterlogged=false]', 'barrel[facing=west,open=false]'][z % 4])
            v.set(61, 1, z, ['purple_candle[candles=2,lit=true,waterlogged=false]', 'air', 'amethyst_cluster[facing=up,waterlogged=false]', 'air'][z % 4])
        self.attendant('black_marketeer', 60, 0, dz_ + 1)
        for z in range(az0, az1 + 1):
            if abs(z - dz_) <= 1:
                continue
            v.set(ax0, 0, z, 'barrel[facing=east,open=false]' if z % 2 else 'chest[facing=east,type=single,waterlogged=false]')
        for (x, z) in ((ax0, az0), (ax1, az0), (ax0, az1), (ax1, az1)):
            v.set(x, 4, z, 'cobweb')
        v.set(57, 4, 45, 'soul_lantern[hanging=true,waterlogged=false]')
        v.set(57, 0, az0, 'cobweb') if False else None
        # 戸の脇の魂の灯り
        ox = x0 - 1
        v.set(ox, 2, dz_ + 1, 'air')
        v.set(x0, 2, dz_ - 1, st.accent.full)

    # ---------------------------------------------------------------- 裏通り: 地図屋 (屋根裏)
    def mapmaker(self):
        """地図屋 (南東の奥、x 83..94 z 69..79、戸は北の通りへ)。一階は地図の店先、階段で屋根裏へ。地図屋は屋根裏の机の向こう"""
        st, v = self.st, self.v
        w = st.wood
        x0, z0, x1, z1 = 83, 69, 94, 79
        b, ridge = self.fac_body('mapmaker', x0, z0, x1, z1, 'north', floors=2, fh=5, ground='shop', axis='x', dormers=1)
        self.fac_front(b, 'mapmaker')
        dx_, _, dz_, _ = b.doors[0]
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2   # 85..92, 71..77
        self.room(ax0, az0, ax1, az1, 0, 5, rug=True)
        self.room(ax0, az0, ax1, az1, 6, 4, rug=False, beams=False, shelves=False, wall_props=False)
        # 一階: 地図の机と書見台、本棚
        v.set(ax0, 0, az0, 'cartography_table')
        v.set(ax0 + 1, 0, az0, 'cartography_table')
        v.set(ax1, 0, az0, 'lectern[facing=south,has_book=false,powered=false]')
        v.set(ax1, 0, az0 + 2, 'loom[facing=west]')
        v.set(ax0, 0, az0 + 2, 'bookshelf')
        v.set(ax0, 1, az0 + 2, 'bookshelf')
        # 階段 (南の壁ぞい x 86..91、z 76..77、東へ上る)
        cleared = self.stair_run(86, 76, 0, 6, 'east', width=2)
        holes = [(x, z) for (x, yy, z) in cleared if yy == 5]
        self.rail_hole(holes, 5, skip={(92, 76), (92, 77)})
        # 屋根裏 (立つ所 y 6): 地図屋の机 (z 74 と x 88) の向こうに地図屋、地図の旗、箱
        y = 6
        self.counter([(x, 74) for x in range(ax0, 89)] + [(88, z) for z in (73, 72, 71)], y=y,
                     tops=((ax0, 74, 'candle[candles=3,lit=true,waterlogged=false]'), (87, 74, 'lantern[hanging=false,waterlogged=false]')))
        for x in range(ax0, 88):
            for z in range(71, 74):
                v.set(x, y, z, 'air')
                v.set(x, y + 1, z, 'air')
        v.set(ax0, y, 71, 'cartography_table')
        v.set(ax0 + 1, y, 71, 'chest[facing=south,type=single,waterlogged=false]')
        self.attendant('mapmaker', 86, y, 72)
        for x in range(89, ax1 + 1):
            v.set(x, y, 71, ['bookshelf', 'barrel[facing=south,open=false]', 'bookshelf', 'chest[facing=south,type=single,waterlogged=false]'][x % 4])
            v.set(x, y + 1, 71, ['lantern[hanging=false,waterlogged=false]', 'air', 'potted_fern', 'air'][x % 4])
        for x in range(89, ax1 + 1):
            for z in range(72, 76):
                if v.get(x, y, z) in (None, 'air'):
                    v.set(x, y, z, 'white_carpet' if (x + z) % 2 else 'light_blue_carpet')
        for (x, z, f) in ((90, 70, 'south'), (85, 78, 'north')):
            if self.solid_at(x, y + 2, z):
                v.set(x, y + 2, z + (1 if f == 'south' else -1), 'light_blue_wall_banner[facing=%s]' % f)
        v.set(90, y + 3, 73, 'lantern[hanging=true,waterlogged=false]')

    # ---------------------------------------------------------------- 裏通り: 酒場と地下
    def tavern(self):
        """酒場「深い杯」(宿の南の路地、x -40..-28 z 36..46、戸は北)。一階は酒の台と樽、南の壁ぞいの階段で地下へ。
        地下 (床 y -6、立つ所 -5): 樽と古い机、酔いどれ。奥の壁に印 cellar_wall (深淵の手がかり。あとで)"""
        st, v = self.st, self.v
        w = st.wood
        x0, z0, x1, z1 = -40, 36, -28, 46
        b, ridge = self.fac_body('tavern', x0, z0, x1, z1, 'north', floors=2, fh=5, ground='shop', axis='x', dormers=1,
                                 roof=st.roof_alts[1])
        self.fac_front(b, 'tavern')
        dx_, _, dz_, _ = b.doors[0]   # (-34, 0, 37)
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2   # -38..-30, 38..44
        self.room(ax0, az0, ax1, az1, 0, 5, rug=False)
        self.room(ax0, az0, ax1, az1, 6, 4, rug=False, beams=False)
        # 酒の台 (x -36、z 39..42) と、奥の樽 (x -38)
        self.counter([(-36, z) for z in range(39, 43)],
                     tops=((-36, 39, 'lantern[hanging=false,waterlogged=false]'), (-36, 41, 'candle[candles=2,lit=true,waterlogged=false]')))
        for z in range(az0, 43):
            v.set(-38, 0, z, 'barrel[facing=east,open=false]')
            v.set(-38, 1, z, 'barrel[facing=east,open=false]' if z % 2 else 'air')
            v.set(-37, 0, z, 'air')
            v.set(-37, 1, z, 'air')
        for z in (39, 41):
            v.set(-35, 0, z, '%s_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]' % w)
        # 机と腰掛け (東)
        for (tx, tz) in ((-31, 39), (-31, 41)):
            v.set(tx, 0, tz, '%s_fence' % w)
            v.set(tx, 1, tz, '%s_pressure_plate[powered=false]' % w)
            v.set(tx + 1, 0, tz, '%s_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]' % w)
        v.set(-31, 1, 39, 'candle[candles=1,lit=true,waterlogged=false]')
        # 地下への階段 (z 43..44、x -35 から東へ上る = 西へ下りる)
        cleared = self.stair_run(-35, 43, -5, 5, 'east', width=2, fill=st.base.full)
        holes = [(x, z) for (x, yy, z) in cleared if yy == -1]
        for (x, z) in holes:
            v.set(x, -1, z, 'cave_air')
        self.rail_hole(holes, -1, skip={(-30, 43), (-30, 44), (-31, 43), (-31, 44)})
        for x in range(ax0, ax1 + 1):
            for z in (43, 44):
                if x >= -30:
                    for yy in (0, 1):
                        if v.get(x, yy, z) not in (None, 'air'):
                            v.set(x, yy, z, 'air')
        # 地下の部屋 (中 x -38..-30 z 38..44、y -5..-2)
        cx0, cz0, cx1, cz1 = ax0, az0, ax1, az1
        for x in range(cx0 - 1, cx1 + 2):
            for z in range(cz0 - 1, cz1 + 2):
                edge = x in (cx0 - 1, cx1 + 1) or z in (cz0 - 1, cz1 + 1)
                v.set(x, -6, z, 'deepslate_tiles' if (x + z) % 2 else 'cobbled_deepslate')
                for yy in range(-5, -1):
                    if edge:
                        v.set(x, yy, z, self.rng.choice(['deepslate_bricks', 'deepslate_bricks', 'cracked_deepslate_bricks', 'mossy_stone_bricks']))
                    elif v.get(x, yy, z) is None or 'stairs' not in v.get(x, yy, z) and v.get(x, yy, z) != st.base.full:
                        v.set(x, yy, z, 'cave_air')
        # 地下の中身: 樽の山、古い長机と腰掛け、蝋燭、蜘蛛の巣、吊り灯り
        for z in range(cz0, 43):
            v.set(cx0, -5, z, 'barrel[facing=east,open=false]')
            v.set(cx0, -4, z, 'barrel[facing=east,open=false]' if z % 2 else 'cave_air')
        for x in range(cx0 + 1, cx1 + 1):
            v.set(x, -5, cz0, 'barrel[facing=south,open=false]' if x % 3 else 'chest[facing=south,type=single,waterlogged=false]')
            v.set(x, -4, cz0, 'barrel[facing=south,open=false]' if x % 2 == 0 and x % 3 else 'cave_air')
        for x in range(-34, -31):
            v.set(x, -5, 40, '%s_fence' % w)
            v.set(x, -4, 40, '%s_pressure_plate[powered=false]' % w)
        v.set(-33, -4, 40, 'candle[candles=3,lit=true,waterlogged=false]')
        v.set(-34, -5, 41, '%s_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]' % w)
        v.set(-32, -5, 41, '%s_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]' % w)
        for (x, z) in ((cx1, cz0 + 1), (cx0 + 1, cz1 - 2)):
            v.set(x, -2, z, 'cobweb')
        v.set(-33, -2, 41, 'lantern[hanging=true,waterlogged=false]')
        v.set(-36, -2, 40, 'lantern[hanging=true,waterlogged=false]')
        v.set(cx1, -5, 41, 'cauldron')
        self.attendant('drunkard', -31, -5, 40)
        # 奥 (東) の壁の割れ目: 風が抜ける。壁の向こうに隠し通路 (backalley.cellar_passage。壁は閉じたまま)
        self.mark('cellar_wall', cx1, -5, 40)
        self.cellar_passage(cx1 + 1, 40)

    # ---------------------------------------------------------------- 墓守の小屋
    def gravekeeper_hut(self):
        """墓守の小屋 (西門の外の墓地の北西の隅、x -134..-126 z -20..-12、戸は東の墓の列へ)"""
        st, v = self.st, self.v
        w = st.wood
        x0, z0, x1, z1 = -134, -20, -126, -12
        b, ridge = self.fac_body('gravekeeper_hut', x0, z0, x1, z1, 'east', floors=1, fh=5, ground='window', axis='z', dormers=0,
                                 infill='mossy_stone_bricks', chimney=(x0 + 2, z0 + 2))
        self.fac_front(b, None, banners=False)
        dx_, _, dz_, _ = b.doors[0]
        ax0, az0, ax1, az1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2   # -132..-128, -18..-14
        self.room(ax0, az0, ax1, az1, 0, 5, rug=False, shelves=False)
        v.set(ax0, 0, az0, 'red_bed[facing=north,part=head,occupied=false]')
        v.set(ax0, 0, az0 + 1, 'red_bed[facing=north,part=foot,occupied=false]')
        v.set(ax0 + 1, 0, az0, 'barrel[facing=up,open=false]')
        v.set(ax0 + 1, 1, az0, 'candle[candles=3,lit=true,waterlogged=false]')
        v.set(ax1, 0, az0, 'chest[facing=south,type=single,waterlogged=false]')
        v.set(ax0, 0, az1, 'bookshelf')
        v.set(ax0, 1, az1, 'potted_dead_bush')
        v.set(ax0 + 1, 0, az1, 'lectern[facing=north,has_book=false,powered=false]')
        for yy in (0, 1):
            v.set(ax1, yy, az1, '%s_fence' % w)
        v.set(ax1, 2, az1, 'air')
        v.set(ax0 + 2, 0, az0, 'composter[level=3]')
        v.set(ax0 + 2, 3, az0 + 2, 'lantern[hanging=true,waterlogged=false]')
        self.attendant('gravekeeper', ax0 + 1, 0, az1 - 1)
        v.set(dx_ + 2, 0, dz_ - 2, 'lantern[hanging=false,waterlogged=false]') if False else None

    # ---------------------------------------------------------------- 入れる家 (町のクエストの家)
    def furnish_home(self, b):
        """大通りの 1 列目の入れる家: 一階は居間、梯子で二階の寝室。住み手ごとに中身が違う (interiors.HOME_THEMES、
        docs/design/35 の 6.8)。印 quest_house_<n> は一階の空いた床"""
        st, v = self.st, self.v
        self.quest_n += 1
        n = self.quest_n
        theme, ground_names, upper_names, bedc = HOME_THEMES[n]
        y = b.y0
        ax0, az0, ax1, az1 = b.x0 + 2, b.z0 + 2, b.x1 - 2, b.z1 - 2
        ix, _, iz, out = b.doors[0]
        idx, idz = DX[OPP[out]]   # 中へ
        entry = {(ix + idx * k, iz + idz * k) for k in (1, 2)}
        keep = {(ex + a, ez + c) for (ex, ez) in entry for a in (-1, 0, 1) for c in (-1, 0, 1)}
        # 梯子: 壁の裏が丸ごとのブロック (窓でない) の所。戸から遠い角を選ぶ
        ladder = self.place_ladder(ax0, az0, ax1, az1, y, y + 5, door=(ix, iz), keep_free=entry)
        saved, self.rng = self.rng, self.frng()
        self.room(ax0, az0, ax1, az1, y, 5, rug=False, shelves=False)
        self.room(ax0, az0, ax1, az1, y + 6, 4, rug=False, beams=False, shelves=False, wall_props=False)
        self.rng = saved
        self.relay_ladders()
        if ladder:
            keep |= {(ladder[0] + a, ladder[1] + c) for a in (-1, 0, 1) for c in (-1, 0, 1)}
        rect = (ax0, az0, ax1, az1)
        # 一階: 住み手の型、残りの床に敷物
        self.place_stamps(rect, y, ground_names, color=bedc, keep=keep)
        if theme == 'herbalist':
            self.herb_bundles(rect, y + 4, n=5)
        # 印 (一階の空いた床。戸の筋と梯子のそばは避ける)
        cx, cz = (ax0 + ax1) // 2, (az0 + az1) // 2
        spot = None
        for r in range(0, 6):
            for x in range(cx - r, cx + r + 1):
                for z in range(cz - r, cz + r + 1):
                    if spot is None and ax0 <= x <= ax1 and az0 <= z <= az1 and (x, z) not in entry                             and v.get(x, y, z) in (None, 'air') and v.get(x, y + 1, z) in (None, 'air') and self.solid_at(x, y - 1, z)                             and not (ladder and (x, z) == ladder):
                        spot = (x, z)
        if spot:
            self.mark('quest_house_%d' % n, spot[0], y, spot[1])
        self.rug(rect, y, 'brown' if theme != 'mourning' else 'light_gray')
        # 二階: 寝台・箱・住み手の小物
        y2 = y + 6
        self.place_stamps(rect, y2, upper_names, color=bedc, keep=keep)
        self.rug(rect, y2, bedc if bedc not in ('white', 'yellow') else 'light_gray')
        if not (ladder and (cx, cz) == ladder) and v.get(cx, y2 + 3, cz) in (None, 'air') and self.solid_at(cx, y2 + 4, cz):
            v.set(cx, y2 + 3, cz, 'lantern[hanging=true,waterlogged=false]')
