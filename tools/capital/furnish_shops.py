# -*- coding: utf-8 -*-
"""小さな施設 (店) の内装 (furnish.Furnish の部品で作る)。座標は施設の関数 (facilities.py) の部屋の中 (ax0..ax1, az0..az1)。
どの店も: 戸 → 客の側 (陳列・腰掛け) → 受付の机 (端に柵の戸) → 番の側 (仕事場・奥の棚) の順に、2 マスの道が通る。"""
from __future__ import annotations


class FurnishShops:
    # ----------------------------------------------------------------- 部品
    def fwall(self, cells, f, cols, y=0):
        """壁ぎわの棚。cells = [(x, z)] に cols (状態の一覧の一覧) を順に。'{f}' は壁から見た前の向き。窓の前は 2 段まで"""
        for i, (x, z) in enumerate(cells):
            col = cols[i % len(cols)]
            n = 2 if self.fwin(x, z) else len(col)
            for k, s in enumerate(col[:n]):
                if s is not None:
                    self.v.set(x, y + k, z, s.replace('{f}', f))

    def fbook(self, f, mask=(1, 1, 0, 1, 1, 1)):
        return self.shelf_full(f, mask)

    # ======================================================================= 道具屋
    def fx_tool_shop(self):
        v = self.v
        rect = (35, -36, 43, -30)
        self.fclear(rect, 0, 6)
        # 売り台 x=40 (北から南へ)。南の端は柵の戸 (番が出入りする)
        self.fcounter([(40, z) for z in range(-36, -30)],
                      tops={(40, -36): self.LANT, (40, -35): 'potted_fern', (40, -34): self.candle(2), (40, -32): self.pot('west')})
        self.fgate(40, 0, -30, 'east')
        # 番の側 x 41..43: 奥の壁 x=43 は道具の棚
        self.fwall([(43, z) for z in range(-36, -29)], 'west', [
            ['fletching_table', self.LANT], [self.barrel('west'), self.barrel('west'), 'potted_fern'], [self.chest('west'), self.pot('west')],
            ['bookshelf', 'bookshelf', 'bookshelf'], ['crafting_table', self.candle(3)], [self.barrel('west'), self.chest('west')],
            ['grindstone[face=floor,facing=west]']])
        self.npc('tool_merchant', 42, 0, -36)  # 市の四人の列 (x 42、2 おき) の北の端 (2026-10-28 の試遊「道具屋が前に出て横並びになってない」)
        # 客の側 x 35..39: 北の壁 z=-36 に売り物の樽と箱、南の壁 z=-30 に束ねた品
        self.fwall([(x, -36) for x in range(35, 40)], 'south', [
            [self.barrel('up'), self.candle(2)], [self.chest('south')], ['hay_block[axis=y]'], [self.barrel('up'), self.LANT], ['crafting_table']])
        self.fwall([(x, -30) for x in range(35, 40)], 'north', [
            [self.barrel('up')], ['white_wool', 'potted_azalea_bush'], [self.barrel('up')], ['hay_block[axis=y]'], ['smithing_table']])
        # 陳列の台 (半ブロックに品)
        self.ftable([(37, -35), (38, -35)], tops={(37, -35): self.LANT, (38, -35): self.pot('south')})
        self.ftable([(37, -31), (38, -31)], tops={(37, -31): self.candle(3), (38, -31): 'potted_red_tulip'})
        # 戸 (34, -32) の脇: 待つ人の腰掛け
        self.fput(35, 0, -34, self.stairs('west'))
        self.fput(35, 0, -35, self.stairs('west'))
        self.frug_rect(36, -34, 39, -32, 'brown', border='red')
        self.hang(40, -35, 0, 6)
        self.hang(40, -32, 0, 6)
        self.hang(37, -33, 0, 6)
        self.fautolight(rect, 0, 6)

    # ======================================================================= 預かり所
    def fx_storage(self):
        v = self.v
        rect = (34, -14, 42, -9)
        self.fclear(rect, 0, 6)
        # 受付の台 z=-11 (東の端は柵の戸)
        self.fcounter([(x, -11) for x in range(34, 42)],
                      tops={(34, -11): self.LANT, (36, -11): self.pot('south'), (38, -11): self.candle(3), (41, -11): 'potted_fern'})
        self.fgate(42, 0, -11, 'north')
        # 奥の壁 z=-14: 預かり箱の棚 (下は樽、中は箱と樽、上は壺と本棚)
        self.fwall([(x, -14) for x in range(34, 43)], 'south', [
            [self.barrel('south'), self.chest('south'), self.pot('south')], [self.barrel('south'), self.barrel('south'), self.fbook('south')]])
        # 番の側 (z -13, -12): 帳簿の書見台、机と椅子
        v.set(35, 0, -13, 'lectern[facing=south,has_book=false,powered=false]')
        self.ftable([(41, -13), (42, -13)], tops={(41, -13): self.candle(2), (42, -13): 'potted_fern'})
        self.chair(41, 0, -12, 'north')
        self.npc('storage_keeper', 38, 0, -12)
        # 客の側 (z -10, -9): 待つ人の腰掛け、荷物の樽
        self.fput(34, 0, -10, self.stairs('east'))
        self.fput(34, 0, -9, self.stairs('east'))
        self.fcol(36, -9, [self.barrel('up'), self.LANT])
        self.fcol(40, -9, [self.barrel('up'), 'potted_fern'])
        self.fcol(42, -10, [self.barrel('west'), self.barrel('west')])
        self.fcol(42, -9, ['hay_block[axis=y]'])
        self.frug_rect(37, -10, 39, -9, 'gray')
        self.hang(36, -12, 0, 6)
        self.hang(40, -12, 0, 6)
        self.hang(38, -10, 0, 6)
        self.fautolight(rect, 0, 6)

    # ======================================================================= 取引所
    def fx_trade(self):
        v = self.v
        rect = (84, -24, 91, -15)
        self.fclear(rect, 0, 6)
        # 帳場の台 z=-20 (東の端は柵の戸)
        self.fcounter([(x, -20) for x in range(84, 91)],
                      tops={(84, -20): self.LANT, (86, -20): 'bell[attachment=floor,facing=north,powered=false]', (87, -20): self.candle(3), (89, -20): 'potted_fern'})
        self.fgate(91, 0, -20, 'north')
        # 奥の壁 z=-24: 品の棚
        self.fwall([(x, -24) for x in range(84, 92)], 'south', [
            [self.barrel('south'), self.fbook('south', (1, 0, 1, 1, 0, 1)), self.pot('south')], [self.barrel('south'), 'bookshelf', None]])
        # 番の側 (z -23..-21): 取引の机 (番と客がはさむ側は帳場)、椅子
        self.ftable([(85, -22), (86, -22), (87, -22)], tops={(85, -22): self.candle(2), (87, -22): 'potted_red_tulip'})
        self.chair(85, 0, -21, 'north')
        self.chair(86, 0, -21, 'north')
        self.fcol(91, -23, [self.chest('west'), self.pot('west')])
        self.fcol(91, -22, [self.barrel('west'), self.barrel('west')])
        self.npc('trade_keeper', 88, 0, -21)
        # 客の側 (z -19..-15): 両わきの陳列の台
        self.ftable([(85, -18), (85, -17)], tops={(85, -18): self.candle(3), (85, -17): self.pot('east')})
        self.ftable([(90, -18), (90, -17)], tops={(90, -18): self.LANT, (90, -17): 'potted_fern'})
        self.fcol(84, -16, [self.barrel('east'), self.barrel('east')])
        self.fcol(84, -15, ['hay_block[axis=y]'])
        self.fcol(91, -16, [self.barrel('west'), 'potted_fern'])
        self.fcol(91, -15, [self.chest('west')])
        self.fput(86, 0, -15, self.stairs('north'))
        self.fput(90, 0, -15, self.stairs('north'))
        self.frug_rect(86, -19, 89, -16, 'brown', border='red')
        self.hang(86, -21, 0, 6)
        self.hang(90, -21, 0, 6)
        self.hang(88, -17, 0, 6)
        self.fautolight(rect, 0, 6)

    # ======================================================================= 厩舎
    def fx_stable(self):
        v = self.v
        rect = (84, 8, 91, 15)
        self.fclear(rect, 0, 6)
        # 受付の台 z=11 (東の端は柵の戸)
        self.fcounter([(x, 11) for x in range(84, 91)], tops={(84, 11): self.LANT, (87, 11): self.candle(3), (89, 11): 'potted_fern'})
        self.fgate(91, 0, 11, 'north')
        # 馬房 3 つ (奥 z 13..15): 柵の仕切りと柵の戸の前、中は藁敷き。奥に飼い葉と水桶
        gates = {(85, 13): 'north', (88, 13): 'north', (91, 13): 'north'}
        cells = [(84, 13), (86, 13), (87, 13), (89, 13), (90, 13)] + [(86, z) for z in (14, 15)] + [(89, z) for z in (14, 15)]
        self.fence_line(cells, gates=gates)
        for x0 in (84, 87, 90):
            for x in (x0, x0 + 1):
                v.set(x, 0, 14, 'yellow_carpet')
            v.set(x0, 0, 15, 'hay_block[axis=x]')
            v.set(x0 + 1, 0, 15, 'water_cauldron[level=3]')
        # 番の足もと (z=12)
        self.npc('stable_keeper', 88, 0, 12)
        # 客の側 (z 8..10): 飼い葉の山、桶、鞍掛け (柵の上に伏せた階段)
        self.fcol(84, 8, ['hay_block[axis=y]', 'hay_block[axis=y]'])
        self.fcol(84, 9, [self.barrel('east'), self.LANT])
        self.fcol(91, 8, [self.barrel('west'), 'potted_fern'])
        for z in (9, 10):
            self.fput(91, 0, z, self.fence(n=(z == 10), s=(z == 9)))
        self.fput(91, 1, 9, self.stairs('west', 'top'))
        self.fput(91, 1, 10, self.stairs('west', 'top'))
        self.frug_rect(86, 9, 89, 10, 'brown', border='red')
        self.hang(86, 10, 0, 6)
        self.hang(90, 10, 0, 6)
        self.hang(87, 12, 0, 6)
        self.fautolight(rect, 0, 6)

    # ======================================================================= 薬師の店
    def fx_apothecary(self):
        v = self.v
        rect = (55, 8, 64, 15)
        self.fclear(rect, 0, 6)
        # 売り台 z=11 (東の端は柵の戸)
        self.fcounter([(x, 11) for x in range(55, 64)],
                      tops={(55, 11): self.LANT, (58, 11): 'potted_red_mushroom', (60, 11): self.candle(4), (62, 11): 'potted_brown_mushroom'})
        self.fgate(64, 0, 11, 'north')
        # 奥の壁 z=15: 薬の棚
        self.fwall([(x, 15) for x in range(55, 65)], 'north', [
            [self.barrel('north'), 'potted_fern'], ['bookshelf', self.candle(2)], [self.fbook('north'), 'potted_blue_orchid'],
            [self.barrel('north'), 'potted_allium'], ['bookshelf', 'potted_azalea_bush']])
        # 作業台 z=13 (醸造台と大釜、椅子)
        self.ftable([(58, 13), (59, 13), (60, 13), (61, 13)], tops={
            (58, 13): 'brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]',
            (60, 13): 'brewing_stand[has_bottle_0=false,has_bottle_1=true,has_bottle_2=true]', (61, 13): 'potted_red_mushroom'})
        v.set(57, 0, 13, 'cauldron')
        v.set(62, 0, 13, 'water_cauldron[level=3]')
        self.chair(59, 0, 14, 'north')
        self.chair(60, 0, 14, 'north')
        self.fcol(55, 12, ['composter[level=5]'])
        self.fcol(55, 14, [self.barrel('east')])
        self.npc('apothecary', 59, 0, 12)
        # 天井から吊るして干す薬草 (梁の層で天井につく)
        for x in (57, 59, 61, 63):
            if (v.get(x, 5, 12) or 'air') in ('air', 'cave_air'):
                v.set(x, 5, 12, ('flowering_azalea_leaves' if x % 4 == 1 else 'azalea_leaves') + '[distance=1,persistent=true,waterlogged=false]')
        # 客の側 z 8..10: 薬草の鉢の台、棚、桶
        self.ftable([(55, 9), (55, 10)], tops={(55, 9): 'potted_fern', (55, 10): 'potted_blue_orchid'})
        self.fcol(55, 8, ['composter[level=7]'])
        self.fcol(64, 8, [self.barrel('west'), 'potted_azalea_bush'])
        self.fcol(64, 9, ['bookshelf', 'bookshelf'])
        self.fcol(64, 10, ['bookshelf', self.candle(2)])
        self.fcol(63, 8, [self.barrel('up'), self.LANT])
        self.frug_rect(58, 9, 61, 10, 'green', border='lime')
        self.hang(57, 9, 0, 6)
        self.hang(62, 9, 0, 6)
        self.hang(60, 13, 0, 6)
        self.fautolight(rect, 0, 6)

    # ======================================================================= 酒場 (一階)
    def fx_tavern(self):
        v = self.v
        rect = (-38, 38, -30, 44)
        self.fclear(rect, 0, 5)          # 地下への階段の穴と柵は残る
        # 酒の台 x=-36 (z 38..42、南の端 z=43 は柵の戸)。奥 (x -38) は樽の列
        self.fcounter([(-36, z) for z in range(38, 43)],
                      tops={(-36, 38): self.LANT, (-36, 39): self.pot('east'), (-36, 40): self.candle(2), (-36, 42): 'potted_red_tulip'})
        self.fgate(-36, 0, 43, 'east')
        for z in range(38, 45):
            self.fcol(-38, z, [self.barrel('east'), self.barrel('east') if z % 2 == 0 else self.chest('east')])
        v.set(-37, 0, 44, 'water_cauldron[level=3]')
        v.set(-37, 0, 38, 'crafting_table')
        # 客の側: 酒の台の前の止まり木 (椅子)、東の机と椅子
        self.chair(-35, 0, 39, 'west')
        self.chair(-35, 0, 41, 'west')
        self.ftable([(-31, 39), (-31, 40)], tops={(-31, 39): self.candle(2), (-31, 40): self.pot('south')})
        for z in (39, 40):
            self.chair(-32, 0, z, 'east')
            self.chair(-30, 0, z, 'west')
        self.fcol(-30, 38, [self.barrel('up'), self.LANT])
        self.chandelier(-33, 40, 0, 5, drop=3)
        self.fautolight(rect, 0, 5)
