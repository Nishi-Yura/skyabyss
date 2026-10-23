# -*- coding: utf-8 -*-
"""大きな施設 (鍛冶場・宿・組合・砦・館・社・語り部の家・墓守の小屋) の内装。furnish.Furnish の部品で作る。"""
from __future__ import annotations


class FurnishBig:
    # ======================================================================= 鍛冶場
    def fx_smithy(self):
        """鍛冶場: 北の壁に炉 (共鳴炉は動かさない)、西に鍛冶師の台と仕事場、東に金床・焼き入れ桶・砥石の列、南の前に品の陳列"""
        v = self.v
        rect = (10, 10, 24, 18)
        keep = {(x, z) for x in range(16, 19) for z in range(9, 12)}      # 炉と煙突
        self.fclear(rect, 0, 7, keep=keep)
        # 炉のわき: 薪と焼き入れの桶 (炉の前 (16..18, 12) は空ける)
        v.set(15, 0, 11, 'coal_block')
        v.set(19, 0, 11, 'water_cauldron[level=3]')
        # 西の仕事場 (x 10..15, z 11..13): 鍛冶師の台 z=14 (西の端に柵の戸)
        self.fcounter([(x, 14) for x in range(11, 16)],
                      tops={(11, 14): self.LANT, (13, 14): self.candle(2), (15, 14): self.pot('south')})
        self.fgate(10, 0, 14, 'north')
        v.set(11, 0, 11, 'smithing_table')
        v.set(12, 0, 11, 'anvil[facing=north]')
        v.set(13, 0, 11, 'water_cauldron[level=3]')
        v.set(14, 0, 11, 'grindstone[face=floor,facing=north]')
        self.fcol(10, 11, [self.chest('east'), self.pot('east')])
        self.fcol(10, 12, [self.barrel('east'), self.barrel('east')])
        self.npc('smith', 12, 0, 12)
        # 北の壁 z=10 の棚 (西): 設計の帳面と道具の樽
        self.fwall([(x, 10) for x in range(10, 16)], 'south', [
            ['bookshelf', 'bookshelf'], [self.barrel('south'), self.LANT], [self.chest('south'), self.candle(2)], [self.barrel('south'), self.barrel('south')],
            ['crafting_table', None], [self.barrel('south'), 'potted_fern']])
        # 東の仕事場 (z 11): 金床・焼き入れ桶・金床・鍛冶台・砥石。背の壁 z=10 は炭と樽
        v.set(20, 0, 11, 'anvil[facing=north]')
        v.set(21, 0, 11, 'water_cauldron[level=3]')
        v.set(22, 0, 11, 'anvil[facing=north]')
        v.set(23, 0, 11, 'smithing_table')
        v.set(24, 0, 11, 'grindstone[face=floor,facing=north]')
        self.fwall([(x, 10) for x in range(20, 25)], 'south', [
            ['coal_block', 'coal_block'], [self.barrel('south'), self.LANT], ['coal_block', self.candle(2)], [self.barrel('south'), self.barrel('south')], ['coal_block']])
        # 東の壁 x=24 の脇: 出来上がった品の棚
        self.fwall([(24, z) for z in (13, 14, 15)], 'west', [[self.chest('west'), self.pot('west')], ['bookshelf', 'bookshelf']])
        # 前 (南) の陳列の台 z=16 (半ブロック) と、客の通り
        self.ftable([(x, 16) for x in range(20, 24)], tops={(20, 16): self.pot('south'), (21, 16): self.LANT, (22, 16): self.candle(3), (23, 16): 'potted_fern'})
        self.fwall([(x, 18) for x in range(12, 25)], 'north', [
            [self.barrel('up'), self.LANT], ['coal_block'], [self.barrel('up'), 'potted_fern'], [self.chest('north')], ['hay_block[axis=y]'], [self.barrel('up')]])
        # 炉の前の灰色の敷物の代わりに、炭の灯り (炉の上の吊り灯りは鎖で)
        self.hang(13, 12, 0, 7, 4)
        self.hang(21, 13, 0, 7, 4)
        self.hang(17, 14, 0, 7, 4)
        self.hang(12, 16, 0, 7, 4)
        self.hang(22, 17, 0, 7, 4)
        self.fautolight(rect, 0, 7, drop=4)

    # ======================================================================= 宿 (入れ物は作ったまま、並べ方の直しと灯り)
    def fx_inn(self):
        v = self.v
        rect = (-42, 8, -10, 24)
        self.fring_thin(rect, 0, 6)
        # 酒の台 (z 21) の前の止まり木: 台に着くように 1 つ前 (z 20) へ
        for x in (-41, -38, -35):
            v.set(x, 0, 19, 'air')
            self.chair(x, 0, 20, 'south')
        # 談話室の肘掛け椅子は、低い机 (x -14) のすぐ両わきへ
        for (x, z) in ((-16, 10), (-16, 12), (-12, 10), (-12, 12)):
            v.set(x, 0, z, 'air')
        for z in (10, 12):
            self.chair(-15, 0, z, 'east')
            self.chair(-13, 0, z, 'west')

    def fx_guild(self):
        rect = (52, -32, 76, -12)
        self.fring_thin(rect, 0, 6)

    def fx_fixes(self):
        """机と椅子の向きの直し (語り部の家・館)"""
        self.chair(-17, 5, -75, 'east')
        self.chair(-15, 5, -75, 'west')
        for x in (-52, -50):
            self.chair(x, 5, -86, 'north')
