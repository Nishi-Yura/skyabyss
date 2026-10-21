# -*- coding: utf-8 -*-
"""裏通りの隠し (docs/design/35 の 6.8)。facilities.Facilities が継ぐ。

- 古井戸の下の小さな迷路と宝 (well_maze): 井戸の縁の内の古い取っ手 (レバー) で格子 (鉄の跳ね上げ戸) の一枚が開き、
  梯子で井戸の底へ。底から西へ、分かれ道・行き止まりの骨の間・崩れた横穴・鉄の戸の間。鉄の戸は、
  脇の細い抜け道の奥のレバーで開く (レバーの付いた石が戸の隣)。戸の奥が宝の間 (箱 well_chest、歌の碑、壁の音の石 5 つ)。
- 酒場の地下の奥の隠し通路 (cellar_passage): 奥の壁 (印 cellar_gap の 2 マス) の向こうに、下っていく通路と封じた戸。
  壁は閉じたまま建てる。開けるのは町のクエストの側 (cellar_gap とその上の 1 マスを空気に)。

地下はすべて閉じた箱で書く (床・壁・天井。天井から地面の下 y=-2 までも土と石で埋める)。地形の空洞に左右されない。
"""
from __future__ import annotations

# 迷路の地図 (x = -73 + 列、z = 38 + 行)。立つ所 y = -10 (床 -11、天井 -7)
#   # 壁  . 床  W 井戸の底 (縦穴)  D 鉄の戸  L レバー (南の石に付く)  G 崩れた砂利  C 宝の箱  T 碑
MAZE = [
    '####################',
    '########.....#######',
    '########.....#######',
    '########.....#######',
    '##########.#########',
    '##########.#########',
    '##########.#########',
    '##########.#########',
    '######.....#########',
    '######.###.###WWW###',
    '######.###....WWW###',
    '######.###.###WWW###',
    '######L###.#########',
    '##########.#########',
    '##C...D......#######',
    '##....#......#######',
    '##....#.........G###',
    '##T...#......#######',
    '#######......#######',
    '####################',
]
MX0, MZ0 = -73, 38
MY = -10   # 立つ所


class BackAlley:
    def _rough(self, kind):
        rng = self.frng()
        if kind == 'wall':
            return rng.choice(['stone_bricks', 'stone_bricks', 'mossy_stone_bricks', 'cracked_stone_bricks', 'cobblestone', 'mossy_cobblestone'])
        if kind == 'floor':
            return rng.choice(['mossy_cobblestone', 'cobblestone', 'stone_bricks', 'mossy_stone_bricks', 'tuff'])
        if kind == 'deep':
            return rng.choice(['deepslate_bricks', 'deepslate_bricks', 'cracked_deepslate_bricks', 'deepslate_tiles', 'cobbled_deepslate'])
        if kind == 'earth':
            return rng.choice(['stone', 'stone', 'dirt', 'andesite', 'tuff'])
        return 'stone'

    def _fill_earth(self, x, z, y0, y1):
        """地面の下 (y0..y1) の、まだ何も書いていない所を土と石で埋める (地下の天井の上)"""
        for y in range(y0, y1 + 1):
            if self.v.get(x, y, z) is None:
                self.v.set(x, y, z, 'dirt' if y == -2 else self._rough('earth'))

    # ---------------------------------------------------------------- 古井戸の下の迷路
    def well_maze(self, cx, cz):
        v = self.v
        y = MY
        cell = {}
        for r, row in enumerate(MAZE):
            for c, ch in enumerate(row):
                cell[(MX0 + c, MZ0 + r)] = ch
        # 井戸の縦穴 (内 3×3、縁 5×5) を底まで: 縁は苔の丸石、内は空気、底は床
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                ring = max(abs(x - cx), abs(z - cz)) == 2
                for yy in range(y - 1, -1):
                    if ring:
                        v.set(x, yy, z, 'mossy_cobblestone' if (x + yy + z) % 3 else 'cobblestone')
                    else:
                        v.set(x, yy, z, self._rough('floor') if yy == y - 1 else 'cave_air')
        # 迷路
        for (x, z), ch in cell.items():
            if ch == 'W':
                continue
            if ch == '#':
                for yy in range(y - 1, y + 4):
                    v.set(x, yy, z, self._rough('wall'))
            else:
                v.set(x, y - 1, z, self._rough('floor'))
                for yy in range(y, y + 3):
                    v.set(x, yy, z, 'cave_air')
                v.set(x, y + 3, z, 'stone_bricks')
            self._fill_earth(x, z, y + 4, -2)
        # 井戸の底から西への口 (縁を 3 段開ける)
        for yy in range(y, y + 3):
            v.set(cx - 2, yy, cz, 'cave_air')
        # 梯子 (東の縁の内側、底から格子の下まで)。格子は東の一枚がレバーで開く
        for yy in range(y, -1):
            v.set(cx + 1, yy, cz, 'ladder[facing=west,waterlogged=false]')
        v.set(cx + 1, 0, cz, 'lever[face=wall,facing=west,powered=false]')
        self.mark('well_down', cx, y, cz)
        # 井戸の底: 苔と古い桶、灯り
        v.set(cx - 1, y, cz + 1, 'lantern[hanging=false,waterlogged=false]')
        v.set(cx - 1, y, cz - 1, 'moss_carpet')
        v.set(cx, y, cz - 1, 'cauldron')

        def at(c, r):
            return MX0 + c, MZ0 + r
        # 通路の灯り (魂の松明。壁に付ける)
        for (c, r, f) in ((12, 10, 'south'), (10, 6, 'east'), (10, 12, 'west'), (8, 8, 'south'), (6, 10, 'east')):
            x, z = at(c, r)
            v.set(x, y + 2, z, 'soul_wall_torch[facing=%s]' % f)
        # 骨の間 (行き止まり): 頭蓋・骨の塊・蜘蛛の巣・蝋燭
        x, z = at(8, 1)
        v.set(x, y, z, 'bone_block[axis=y]')
        v.set(x, y + 1, z, 'skeleton_skull[rotation=6]')
        x, z = at(12, 3)
        v.set(x, y, z, 'candle[candles=3,lit=true,waterlogged=false]')
        for (c, r) in ((8, 3), (12, 1)):
            x, z = at(c, r)
            v.set(x, y + 2, z, 'cobweb')
        x, z = at(10, 1)
        v.set(x, y, z, 'bone_block[axis=x]')
        x, z = at(11, 1)
        v.set(x, y, z, 'bone_block[axis=x]')
        v.set(at(9, 1)[0], y + 2, at(9, 1)[1], 'soul_wall_torch[facing=south]')
        # 細い抜け道の蜘蛛の巣 (頭より上) と、レバー
        x, z = at(7, 8)
        v.set(x, y + 2, z, 'cobweb')
        x, z = at(6, 12)
        v.set(x, y, z, 'lever[face=wall,facing=north,powered=false]')
        # 鉄の戸 (レバーの石の南隣)
        x, z = at(6, 14)
        v.set(x, y, z, 'iron_door[facing=east,half=lower,hinge=left,open=false,powered=false]')
        v.set(x, y + 1, z, 'iron_door[facing=east,half=upper,hinge=left,open=false,powered=false]')
        v.set(x, y + 2, z, 'chiseled_stone_bricks')
        for dz in (-1, 1):
            for yy in range(y, y + 3):
                if (x, z + dz) != at(6, 13) or yy != y:
                    v.set(x, yy, z + dz, 'chiseled_stone_bricks' if yy == y + 2 else 'polished_andesite')
        v.set(at(6, 13)[0], y, at(6, 13)[1], 'polished_andesite')
        # 戸の間 (R3): 崩れた樽、鎖、灯り、壁の割れ
        for (c, r, s, t) in ((12, 14, 'barrel[facing=up,open=false]', 'lantern[hanging=false,waterlogged=false]'),
                             (12, 18, 'barrel[facing=north,open=false]', None), (11, 18, 'cobblestone', None),
                             (7, 18, 'barrel[facing=up,open=false]', 'candle[candles=2,lit=true,waterlogged=false]')):
            x, z = at(c, r)
            v.set(x, y, z, s)
            if t:
                v.set(x, y + 1, z, t)
        x, z = at(9, 16)
        v.set(x, y + 2, z, 'lantern[hanging=true,waterlogged=false]')
        # 崩れた横穴の先 (砂利で塞がる)
        x, z = at(16, 16)
        for yy in range(y, y + 3):
            v.set(x, yy, z, 'gravel')
        x, z = at(15, 16)
        v.set(x, y + 2, z, 'cobweb')
        # 宝の間 (R4): 箱 (印 well_chest)、歌の碑、北の壁に音の石 5 つ、紫水晶、灯り
        x, z = at(2, 14)
        v.set(x, y, z, 'chest[facing=east,type=single,waterlogged=false]')
        self.mark('well_chest', x, y, z)
        x, z = at(2, 17)
        self.tablet(x, y, z, 'east', 'well_song')
        for c in range(2, 6):
            x, z = at(c, 13)
            v.set(x, y + 1, z, 'note_block[instrument=bell,note=%d,powered=false]' % (c * 5 % 25))
        x, z = at(1, 15)
        v.set(x, y + 1, z, 'note_block[instrument=bell,note=17,powered=false]')
        for (c, r) in ((5, 17), (5, 15)):
            x, z = at(c, r)
            v.set(x, y, z, 'amethyst_cluster[facing=up,waterlogged=false]' if r == 17 else 'small_amethyst_bud[facing=up,waterlogged=false]')
        x, z = at(3, 16)
        v.set(x, y + 2, z, 'lantern[hanging=true,waterlogged=false]')
        x, z = at(4, 17)
        v.set(x, y, z, 'candle[candles=4,lit=true,waterlogged=false]')

    # ---------------------------------------------------------------- 酒場の地下の奥の隠し通路
    def cellar_passage(self, wx, wz):
        """wx, wz = 地下の奥 (東) の壁のマス。壁の向こう (東) に通路: 上の段 (立つ所 -5) 4 マス、階段 4 段で下り、
        下の段 (立つ所 -9) 7 マス、突き当たりに封じた戸 (強化深層岩)。壁は閉じたまま (cellar_gap の 2 マスを空気にすると開く)"""
        v = self.v
        upper = [(x, -5, 3) for x in range(wx + 1, wx + 5)]           # (x, 立つ所, 高さ)
        stairs = [(wx + 5 + k, -6 - k) for k in range(4)]              # (x, 段の y): 東へ下りる (西へ上る)
        lower = [(x, -9, 3) for x in range(wx + 9, wx + 16)]
        door_x = wx + 16
        cells = {}
        for (x, ys, h) in upper + lower:
            cells[x] = (ys, h)
        # 通路の床・空気・天井
        for x, (ys, h) in cells.items():
            v.set(x, ys - 1, wz, self._rough('deep'))
            for yy in range(ys, ys + h):
                v.set(x, yy, wz, 'cave_air')
            v.set(x, ys + h, wz, 'deepslate_tiles')
        # 階段 (段の下は埋め、上は 3 段空ける)
        for (x, sy) in stairs:
            v.set(x, sy, wz, 'deepslate_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]')
            v.set(x, sy - 1, wz, self._rough('deep'))
            for yy in range(sy + 1, sy + 4):
                v.set(x, yy, wz, 'cave_air')
            v.set(x, sy + 4, wz, 'deepslate_tiles')
        # 壁 (両わき) と、天井の上の埋め
        xs = list(range(wx + 1, door_x + 1))
        for x in xs:
            lo, hi = -11, -2
            for dz in (-1, 1):
                for yy in range(lo, hi + 1):
                    if v.get(x, yy, wz + dz) is None:
                        v.set(x, yy, wz + dz, self._rough('deep') if yy <= -3 else 'dirt')
            for yy in range(lo, hi + 1):
                if v.get(x, yy, wz) is None:
                    v.set(x, yy, wz, self._rough('deep') if yy <= -3 else self._rough('earth'))
        # 開ける壁: 地下の部屋の奥の壁の 3 段を割れた深層岩レンガに (下の 2 段が開く所)
        for yy in (-5, -4, -3):
            v.set(wx, yy, wz, 'cracked_deepslate_bricks')
        self.mark('cellar_gap', wx, -5, wz)
        self.mark('cellar_passage', wx + 1, -5, wz)
        # 灯り: 魂の松明 (天井の下の段、北の壁に付ける)
        v.set(wx + 2, -3, wz, 'soul_wall_torch[facing=south]')
        v.set(wx + 11, -7, wz, 'soul_wall_torch[facing=south]')
        v.set(wx + 14, -7, wz, 'soul_wall_torch[facing=south]')
        # 下の段: 蜘蛛の巣、頭蓋、深淵のほのめかし (スカルクの床)
        v.set(wx + 9, -7, wz, 'cobweb')
        v.set(wx + 13, -10, wz, 'sculk')
        v.set(wx + 14, -10, wz, 'sculk')
        v.set(wx + 15, -10, wz, 'sculk')
        self.mark('abyss_hint', wx + 15, -9, wz)
        # 封じた戸 (幅 1、高さ 2 の強化深層岩)、枠は磨いた深層岩と泣く黒曜石、両わきに魂の灯りの台
        for yy in (-9, -8):
            v.set(door_x, yy, wz, 'reinforced_deepslate')
        v.set(door_x, -7, wz, 'crying_obsidian')
        v.set(door_x, -10, wz, 'polished_deepslate')
        for dz in (-1, 1):
            for yy in range(-10, -6):
                v.set(door_x, yy, wz + dz, 'chiseled_deepslate' if yy == -7 else 'polished_deepslate')
        v.set(door_x + 1, -9, wz, 'deepslate_tiles')
        v.set(door_x + 1, -8, wz, 'deepslate_tiles')
        self.mark('abyss_door', door_x, -9, wz)
