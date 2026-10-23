# -*- coding: utf-8 -*-
"""小さな施設の内装 その 2 (地図屋・止まり木の番小屋・闇市)。furnish_shops.FurnishShops と同じ作り。"""
from __future__ import annotations


class FurnishShops2:
    # ======================================================================= 地図屋 (一階の店先と屋根裏の仕事場)
    def fx_mapmaker(self):
        v = self.v
        rect = (85, 71, 92, 77)
        stairs = {(x, z) for x in range(86, 92) for z in (76, 77)}     # 屋根裏への階段 (手をつけない)
        self.fclear(rect, 0, 5, keep=stairs)
        # 受付の台 z=73 (東の端は柵の戸)。番はその奥
        self.fcounter([(x, 73) for x in range(85, 92)], tops={(85, 73): self.LANT, (87, 73): self.candle(2), (90, 73): 'potted_fern'})
        self.fgate(92, 0, 73, 'north')
        self.npc('mapmaker', 88, 0, 74)
        # 番の側: 西は地図の机と製図の机、東は書見台と棚
        v.set(85, 0, 74, 'cartography_table')
        v.set(85, 0, 75, 'cartography_table')
        self.ftable([(86, 74), (87, 74)], tops={(86, 74): 'white_carpet', (87, 74): self.candle(2)})
        self.chair(86, 0, 75, 'north')
        self.chair(87, 0, 75, 'north')
        v.set(89, 0, 74, 'lectern[facing=west,has_book=false,powered=false]')
        self.fcol(90, 75, [self.chest('north'), self.pot('north')])
        self.fcol(91, 75, [self.barrel('north'), self.LANT])
        self.fwall([(92, 74), (92, 75)], 'west', [['bookshelf', 'bookshelf', 'bookshelf'], [self.fbook('west'), 'bookshelf']])
        # 客の側 (z 71, 72): 壁に地図 (旗)、棚と地図の机
        for x, col in ((86, 'light_blue'), (87, 'green'), (89, 'light_blue'), (90, 'green')):
            if self.solid_at(x, 2, 70) and (v.get(x, 2, 71) in (None, 'air')):
                v.set(x, 2, 71, '%s_wall_banner[facing=south]' % col)
        self.fcol(85, 71, ['bookshelf', 'bookshelf'])
        self.fcol(85, 72, [self.barrel('up'), self.LANT])
        self.fcol(92, 71, [self.chest('west'), self.pot('west')])
        self.fcol(92, 72, ['bookshelf', 'bookshelf'])
        self.ftable([(90, 71), (91, 71)], tops={(90, 71): 'white_carpet', (91, 71): self.candle(2)})
        self.frug_rect(86, 72, 91, 72, 'light_blue')
        self.hang(87, 72, 0, 5, 3)
        self.hang(91, 72, 0, 5, 3)
        self.hang(88, 75, 0, 5, 3)
        self.fautolight(rect, 0, 5)
        # ---- 屋根裏 (床の上 y 6): 製図の部屋。階段の穴と柵は残る
        y = 6
        self.fclear(rect, y, 4)
        self.fcol(85, 71, ['cartography_table'], y=y)
        self.fcol(86, 71, ['cartography_table'], y=y)
        self.fwall([(x, 71) for x in range(88, 93)], 'south', [['bookshelf', self.candle(2)], [self.barrel('south'), self.LANT], [self.chest('south'), 'potted_fern']], y=y)
        self.ftable([(87, 73), (88, 73), (89, 73), (90, 73)], y=y, tops={(87, 73): 'white_carpet', (88, 73): self.candle(3), (89, 73): 'white_carpet', (90, 73): 'potted_fern'})
        self.chair(87, y, 72, 'south')
        self.chair(88, y, 72, 'south')
        self.chair(89, y, 74, 'north')
        self.chair(90, y, 74, 'north')
        self.fcol(85, 74, [self.chest('east'), self.pot('east')], y=y)
        self.fcol(85, 75, [self.barrel('east'), self.LANT], y=y)
        self.frug_rect(86, 74, 88, 75, 'light_blue', y=y)
        self.hang(88, 73, y, 4, 2)
        self.fautolight(rect, y, 4)

    # ======================================================================= 止まり木の番小屋
    def fx_perch(self):
        v = self.v
        rect = (-92, 8, -86, 14)
        self.fclear(rect, 0, 5)
        # 受付の台 z=10 (東の端は柵の戸)
        self.fcounter([(x, 10) for x in range(-92, -86)], tops={(-92, 10): self.LANT, (-90, 10): self.candle(2), (-88, 10): 'potted_fern'})
        self.fgate(-86, 0, 10, 'north')
        self.npc('perch_keeper', -90, 0, 11)
        # 奥の壁 z=14: 干し草と餌と道具の棚
        self.fwall([(x, 14) for x in range(-92, -85)], 'north', [
            ['hay_block[axis=y]', 'hay_block[axis=y]'], [self.barrel('north'), self.LANT], [self.chest('north'), 'potted_azalea_bush'],
            ['bone_block[axis=y]', 'potted_fern'], [self.barrel('north'), self.barrel('north')]])
        # 番の側: 机と椅子 (地図と記録)、水桶
        self.ftable([(-89, 12), (-88, 12)], tops={(-89, 12): 'white_carpet', (-88, 12): self.candle(2)})
        self.chair(-89, 0, 13, 'north')
        self.chair(-88, 0, 13, 'north')
        v.set(-92, 0, 11, 'cartography_table')
        v.set(-92, 0, 12, 'water_cauldron[level=3]')
        # 客の側 z 8..9: 待つ腰掛け、荷物
        self.fput(-92, 0, 8, self.stairs('east'))
        self.fcol(-92, 9, [self.barrel('east'), 'potted_fern'])
        self.fcol(-86, 8, ['bookshelf', 'bookshelf'])
        self.fcol(-86, 9, [self.barrel('west'), self.LANT])
        self.frug_rect(-89, 8, -88, 9, 'brown')
        self.hang(-90, 12, 0, 5, 3)
        self.hang(-88, 9, 0, 5, 3)
        self.fautolight(rect, 0, 5)

    # ======================================================================= 闇市
    def fx_black_market(self):
        v = self.v
        rect = (55, 42, 61, 48)
        self.fclear(rect, 0, 5)
        # 売り台 x=58 (南の端 z=48 は柵の戸)。上に魂の灯りと戦利品
        self.fcounter([(58, z) for z in range(42, 48)], tops={
            (58, 42): 'soul_lantern[hanging=false,waterlogged=false]', (58, 44): 'amethyst_cluster[facing=up,waterlogged=false]',
            (58, 46): 'purple_candle[candles=3,lit=true,waterlogged=false]', (58, 47): 'skeleton_skull[rotation=12]'})
        self.fgate(58, 0, 48, 'east')
        self.npc('black_marketeer', 59, 0, 46)
        # 奥の壁 x=61: 箱と樽と紫水晶の棚
        self.fwall([(61, z) for z in range(42, 49)], 'west', [
            [self.chest('west'), 'purple_candle[candles=2,lit=true,waterlogged=false]'], [self.barrel('west'), self.barrel('west')],
            ['ender_chest[facing=west,waterlogged=false]', 'amethyst_cluster[facing=up,waterlogged=false]'], [self.barrel('west'), self.LANT]])
        # 番の側: 帳簿の机 (半ブロック)
        self.ftable([(60, 43), (60, 44)], tops={(60, 43): self.candle(2), (60, 44): 'potted_red_mushroom'})
        self.chair(59, 0, 43, 'east')
        # 客の側 x 55..57: 壁ぎわの樽と箱 (戸 z=46 の前は空ける)、紫の敷物
        self.fwall([(55, z) for z in (42, 43, 44, 48)], 'east', [[self.barrel('east'), self.barrel('east')], [self.chest('east'), 'purple_candle[candles=1,lit=true,waterlogged=false]']])
        self.fcol(57, 42, [self.barrel('up'), self.LANT])
        self.fcol(57, 48, [self.barrel('up'), 'potted_fern'])
        for x in range(55, 58):
            for z in range(42, 49):
                if v.get(x, 0, z) in (None, 'air', 'cave_air') and self.solid_at(x, -1, z):
                    v.set(x, 0, z, 'purple_carpet' if (x + z) % 3 else 'black_carpet')
        self.hang(56, 45, 0, 5, 3)
        self.hang(60, 46, 0, 5, 3)
        self.fautolight(rect, 0, 5)
