# -*- coding: utf-8 -*-
"""裏通りの隠し (docs/design/35 の 6.9)。facilities.Facilities が継ぐ。

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
        # 通路の灯り (幅 1 の通路は壁の松明の向きが壁に当たるので、天井から下げた魂の灯り。頭より上)
        for (c, r) in ((12, 10), (10, 6), (10, 12), (8, 8), (6, 10)):
            x, z = at(c, r)
            v.set(x, y + 2, z, 'soul_lantern[hanging=true,waterlogged=false]')
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
        # 灯り: 天井から下げた魂の灯り (頭より上の段)
        v.set(wx + 2, -3, wz, 'soul_lantern[hanging=true,waterlogged=false]')
        v.set(wx + 11, -7, wz, 'soul_lantern[hanging=true,waterlogged=false]')
        v.set(wx + 14, -7, wz, 'soul_lantern[hanging=true,waterlogged=false]')
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
        # 戸の向こうは五章の大坑道 (docs/design/34 の 4.7。戸は閉じたまま建てる。開けるのは abyss/AbyssEntrance)
        self.abyss_shaft(door_x, wz)

    def abyss_shaft(self, door_x, wz):
        """封じた戸の向こうの大坑道 (五章の入口)。2026-10-23 の試遊「深淵の門のスペースもっと広くしてもっと装飾してもいい」で広げた。
        戸の東に幅 3 の通路 (碑「大坑道」)、その先に大縦穴 (中心 cx、半径 11 の壁。前は 6)。壁ぞいに螺旋の段 (幅 3、角度が増える向きに下りる。1 歩で 1 段)。
        真ん中は底まで抜けた吹き抜けで、段の内の縁に低い壁と魂の灯り。壁は 12 段ごとの帯・縦の筋・深いほど泣く黒曜石とスカルク。天井から鎖と灯りを吊る。
        踊り場 2 (灰の民の作業場・前の私の野営。壁の外に 9×9 の小部屋) と、底の境。
        底の境: 縦穴の底の床に輪の象嵌と灯りの台。南の壁を抜けて門の大広間 (幅 11・奥行き 14・高さ 9、柱の列・壁の龕に骨と蝋燭・黒い旗・碑)、
        奥に深淵の門 (強化深層岩の柱と泣く黒曜石の楣、格子は幅 5・高さ 6)。格子の向こうは下っていく暗い穴 (スカルク)。
        印: abyss_landing_1・abyss_landing_2・abyss_gate (門の前、立つ所)・abyss_gate_bars (格子の列の真ん中。都が回っても列の向きが分かる)"""
        import math
        v = self.v
        top = -10                     # 一段目の高さ (通路の床 -10、立つ所 -9 から一段下りる)
        depth = 90                    # 底の床の高さ = top - depth
        R = 11
        cx, cz = door_x + 14, wz
        bottom = top - depth

        def deep():
            return self._rough('deep')

        rng = self.frng()
        # 通路: 戸の裏 (door_x + 1) から縦穴の縁まで。立つ所 -9、高さ 4 (天井 -5)。戸の裏の 1 マスは幅 1、そこから幅 3
        for x in range(door_x + 1, cx - R + 1):
            half = 0 if x == door_x + 1 else 1
            for dz in range(-2, 3):
                for yy in range(-10, -4):
                    inside = abs(dz) <= half and -9 <= yy <= -6
                    if inside:
                        v.set(x, yy, wz + dz, 'cave_air')
                    elif yy == -10:
                        v.set(x, yy, wz + dz, 'polished_deepslate' if abs(dz) <= half else deep())
                    elif yy == -5:
                        v.set(x, yy, wz + dz, 'deepslate_tiles')
                    else:
                        v.set(x, yy, wz + dz, deep())
        # 通路の飾り: 碑「大坑道」(南の壁、北を向く)・吊った魂の灯り・足もとの縁
        v.set(door_x + 3, -8, wz + 2, 'skyabyss:tablet[facing=north,standing=false]')
        self.mark('abyss_shaft_sign', door_x + 3, -8, wz + 2)
        v.set(door_x + 2, -6, wz, 'soul_lantern[hanging=true,waterlogged=false]')
        # 縦穴: 壁 (半径 R の輪) と中 (空気) を、底の下から天井まで
        ceil = -5
        for x in range(cx - R, cx + R + 1):
            for z in range(cz - R, cz + R + 1):
                d = math.hypot(x - cx, z - cz)
                if d > R + 0.5:
                    continue
                ang = math.atan2(z - cz, x - cx)
                for yy in range(bottom - 1, ceil + 1):
                    if yy == ceil:
                        v.set(x, yy, z, 'deepslate_tiles' if d < R - 1.5 else 'polished_deepslate')
                    elif yy == bottom - 1:
                        v.set(x, yy, z, 'polished_deepslate')
                    elif d > R - 0.5:
                        v.set(x, yy, z, self._shaft_wall(x, yy, z, ang, top, bottom, rng))
                    else:
                        v.set(x, yy, z, 'cave_air')
        # 通路の口を壁に開ける (幅 3)
        for dz in (-1, 0, 1):
            for yy in range(-9, -5):
                v.set(cx - R, yy, wz + dz, 'cave_air')
        # 口の両わきは足もとに低い壁 (下の段へ落ちない。真ん中の一マスから最初の段へ下りる)
        for dz in (-1, 1):
            v.set(cx - R, -9, wz + dz, 'deepslate_brick_wall')
        # 天井から吊る鎖と魂の灯り (吹き抜けの中、四本。長さを変えて)
        for k, (ox, oz, n) in enumerate(((2, 2, 28), (-2, -2, 40), (2, -2, 18), (-2, 2, 52))):
            for yy in range(ceil - n, ceil):
                v.set(cx + ox, yy, cz + oz, 'chain[axis=y,waterlogged=false]')
            v.set(cx + ox, ceil - n - 1, cz + oz, 'soul_lantern[hanging=true,waterlogged=false]')
        # 螺旋の段: 一歩 = 弧の長さ 1。最初は西 (通路の口) から
        rmid = 8.8
        landings = {30: 'abyss_landing_1', 60: 'abyss_landing_2'}  # 下りた段の数 → 踊り場 (8 歩ぶん平ら)
        flat = 8
        steps = []
        s = 0.0
        drop = 0
        flat_left = 0
        theta0 = math.pi
        landing_at = {}
        while drop <= depth:  # 最後の一歩は底の床の上 (底の床へ一段で下りる)
            th = theta0 + s / rmid
            steps.append((th, top - drop))
            if flat_left > 0:
                flat_left -= 1
            else:
                drop += 1
                if drop in landings:
                    flat_left = flat
                    landing_at[landings[drop]] = (theta0 + (s + 1 + flat / 2) / rmid, top - drop)
            s += 1.0

        def cells_at(th):
            out = []
            for x in range(cx - R, cx + R + 1):
                for z in range(cz - R, cz + R + 1):
                    d = math.hypot(x - cx, z - cz)
                    if not (7.1 <= d <= 10.45):
                        continue
                    a = math.atan2(z - cz, x - cx)
                    diff = (a - th + math.pi) % (2 * math.pi) - math.pi
                    if abs(diff) * d <= 0.55:
                        out.append((x, z, d))
            return out

        placed = set()
        last_y = {}   # (x, z) → 最後に置いた段の高さ (角度の近い二歩が同じマスを取り合うと、段が一つずれて重なる)
        for i, (th, y) in enumerate(steps):
            nxt = steps[i + 1][1] if i + 1 < len(steps) else y
            # 上る向き (角度が減る向き) = (sin θ, -cos θ)
            ax, az = math.sin(th), -math.cos(th)
            if abs(ax) >= abs(az):
                facing = 'east' if ax > 0 else 'west'
            else:
                facing = 'south' if az > 0 else 'north'
            is_step = nxt < y or i == len(steps) - 1  # 最後の一歩も段 (底の床から上がれる)
            for (x, z, d) in cells_at(th):
                if (x, z, y) in placed or ((x, z) in last_y and abs(last_y[(x, z)] - y) < 4):
                    continue
                placed.add((x, z, y))
                last_y[(x, z)] = y
                v.set(x, y - 1, z, deep())
                if d < 7.7:
                    # 内の縁は段にせず、石の上に低い壁 (手すり)。3 歩ごとに魂の灯り、12 歩ごとに鎖の柱の頭 (彫った深層岩)
                    v.set(x, y, z, deep() if i % 12 else 'chiseled_deepslate')
                    v.set(x, y + 1, z, 'deepslate_brick_wall')
                    if i % 3 == 0:
                        v.set(x, y + 2, z, 'soul_lantern[hanging=false,waterlogged=false]')
                    continue
                if is_step:
                    v.set(x, y, z, 'deepslate_brick_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]' % facing)
                else:
                    v.set(x, y, z, 'polished_deepslate')
                for yy in range(y + 1, y + 4):
                    if v.get(x, yy, z) in (None, 'cave_air'):
                        v.set(x, yy, z, 'cave_air')
        # 壁の魂の松明: 18 度ごと・7 段ごと (段の上の空いた所だけ)。間の暗がりの中に、点々と青い灯り
        for k in range(20):
            ang = k * math.pi / 10 + (math.pi / 20 if k % 2 else 0)
            ox_, oz_ = math.cos(ang), math.sin(ang)
            if abs(ox_) >= abs(oz_):
                outward = 'east' if ox_ > 0 else 'west'
            else:
                outward = 'south' if oz_ > 0 else 'north'
            facing = {'east': 'west', 'west': 'east', 'south': 'north', 'north': 'south'}[outward]
            tx = int(round(cx + ox_ * (R - 1)))
            tz = int(round(cz + oz_ * (R - 1)))
            wx_ = tx + {'east': 1, 'west': -1}.get(outward, 0)
            wz_ = tz + {'south': 1, 'north': -1}.get(outward, 0)
            for yy in range(top - 3 - (k % 3) * 2, bottom + 2, -7):
                if v.get(tx, yy, tz) == 'cave_air' and v.get(tx, yy - 1, tz) == 'cave_air' and v.get(wx_, yy, wz_) not in (None, 'cave_air'):
                    v.set(tx, yy, tz, 'soul_wall_torch[facing=%s]' % facing)
        # 踊り場: 壁の外に小部屋 (9×9、高さ 5)。段から 3 マス幅で壁を抜く
        for name, (th, y) in landing_at.items():
            ox, oz = math.cos(th), math.sin(th)
            mx = int(round(cx + ox * (R + 5)))
            mz = int(round(cz + oz * (R + 5)))
            for x in range(mx - 4, mx + 5):
                for z in range(mz - 4, mz + 5):
                    if math.hypot(x - cx, z - cz) <= R - 0.5:
                        continue
                    for yy in range(y - 1, y + 7):
                        edge = x in (mx - 4, mx + 4) or z in (mz - 4, mz + 4) or yy <= y or yy == y + 6
                        if edge:
                            v.set(x, yy, z, 'deepslate_tiles' if yy == y + 6 else ('polished_deepslate' if yy == y else deep()))
                        else:
                            v.set(x, yy, z, 'cave_air')
            # 柱 (四隅の内側) と梁
            for sx in (-1, 1):
                for sz in (-1, 1):
                    for yy in range(y + 1, y + 6):
                        v.set(mx + sx * 3, yy, mz + sz * 3, 'stripped_dark_oak_log[axis=y]')
            for k in range(R - 1, R + 2):
                for w in (-1, 0, 1):
                    px = int(round(cx + ox * k - oz * w))
                    pz = int(round(cz + oz * k + ox * w))
                    if math.hypot(px - cx, pz - cz) < 7.1:
                        continue
                    v.set(px, y - 1, pz, deep())
                    v.set(px, y, pz, 'polished_deepslate')
                    for yy in range(y + 1, y + 4):
                        v.set(px, yy, pz, 'cave_air')
            if name == 'abyss_landing_1':
                # 灰の民の作業場: 金床・樽・鍛冶台・砥石・消えた炉・吊った鎖・灰・壊れたトロッコの線路・足場
                v.set(mx - 2, y + 1, mz - 2, 'anvil[facing=north]')
                v.set(mx + 2, y + 1, mz - 2, 'barrel[facing=up,open=false]')
                v.set(mx + 2, y + 1, mz - 1, 'barrel[facing=up,open=false]')
                v.set(mx + 2, y + 2, mz - 2, 'barrel[facing=up,open=false]')
                v.set(mx + 2, y + 1, mz + 2, 'barrel[facing=up,open=false]')
                v.set(mx - 2, y + 1, mz + 2, 'smithing_table')
                v.set(mx - 1, y + 1, mz + 2, 'grindstone[face=floor,facing=north]')
                v.set(mx, y + 1, mz, 'soul_campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]')  # 消えた炉 (煙が部屋にこもらない)
                for yy in (y + 3, y + 4, y + 5):
                    v.set(mx - 2, yy, mz, 'chain[axis=y,waterlogged=false]')
                v.set(mx + 1, y + 1, mz + 1, 'gray_concrete_powder')
                v.set(mx - 3, y + 1, mz, 'rail[shape=north_south,waterlogged=false]')
                v.set(mx - 3, y + 1, mz - 1, 'rail[shape=north_south,waterlogged=false]')
                v.set(mx + 3, y + 1, mz + 1, 'scaffolding[bottom=false,distance=0,waterlogged=false]')
                v.set(mx + 3, y + 2, mz + 1, 'scaffolding[bottom=false,distance=0,waterlogged=false]')
                v.set(mx - 1, y + 1, mz - 3, 'cobweb')
            else:
                # 前の私の野営: 寝床・焚き火・帳面の台・荷・本棚・頭蓋・灯り
                v.set(mx - 2, y + 1, mz - 1, 'white_wool')
                v.set(mx - 2, y + 1, mz, 'white_carpet')
                v.set(mx - 1, y + 1, mz - 1, 'white_carpet')
                v.set(mx, y + 1, mz, 'campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]')  # 消えた焚き火 (まだ温かい気がする)
                v.set(mx + 2, y + 1, mz - 2, 'lectern[facing=west,has_book=true,powered=false]')
                v.set(mx + 2, y + 1, mz + 2, 'barrel[facing=up,open=false]')
                v.set(mx + 1, y + 1, mz + 2, 'chiseled_bookshelf[facing=north,slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=true,slot_3_occupied=false,slot_4_occupied=true,slot_5_occupied=false]')
                v.set(mx - 3, y + 1, mz + 2, 'skeleton_skull[rotation=6]')
                v.set(mx - 2, y + 5, mz + 2, 'soul_lantern[hanging=true,waterlogged=false]')
            v.set(mx + 2, y + 5, mz - 2, 'soul_lantern[hanging=true,waterlogged=false]')
            v.set(mx, y + 5, mz, 'lantern[hanging=true,waterlogged=false]')   # 人のいた所だけ温かい灯り
            self.mark(name, mx, y + 1, mz)
        # ---------------------------------------------------------------- 底の境
        # 縦穴の底の床: 輪の象嵌 (半径 3 と 6.5) と、四つの灯りの台
        for x in range(cx - R + 1, cx + R):
            for z in range(cz - R + 1, cz + R):
                d = math.hypot(x - cx, z - cz)
                if d > R - 0.5:
                    continue
                if 2.6 <= d <= 3.4:
                    st = 'chiseled_deepslate'
                elif 6.1 <= d <= 6.9:
                    st = 'polished_blackstone_bricks'
                elif d < 1.5:
                    st = 'sculk_catalyst' if (x, z) == (cx, cz) else 'sculk'
                else:
                    st = 'polished_deepslate'
                v.set(x, bottom - 1, z, st)
        for (ox, oz) in ((4, 4), (-4, 4), (4, -4), (-4, -4)):
            v.set(cx + ox, bottom, cz + oz, 'polished_deepslate_wall')
            v.set(cx + ox, bottom + 1, cz + oz, 'soul_lantern[hanging=false,waterlogged=false]')
        # 門の大広間: 南の壁 (z = cz + R) を抜け、奥 (南) に門。内のり x cx-5..cx+5、z cz+R-1..gz-1、高さ 9 (上の 2 段は少し狭いアーチ)
        gz = cz + R + 14              # 格子の列
        z0 = cz + R - 2
        for x in range(cx - 7, cx + 8):
            for z in range(z0, gz + 1):
                for yy in range(bottom - 1, bottom + 11):
                    ax = abs(x - cx)
                    if z < cz + R - 0.5 and math.hypot(x - cx, z - cz) <= R - 0.5:
                        continue          # 縦穴の中 (もう空気)
                    inner = ax <= 5 and bottom <= yy <= bottom + 6 or ax <= 4 and yy == bottom + 7 or ax <= 3 and yy == bottom + 8
                    if z == gz:
                        inner = False     # 奥の壁 (門の壁) は後で
                    if inner:
                        v.set(x, yy, z, 'cave_air')
                    elif yy == bottom - 1:
                        v.set(x, yy, z, 'polished_deepslate')
                    elif yy >= bottom + 7 and ax <= 5:
                        v.set(x, yy, z, 'deepslate_tiles')
                    else:
                        v.set(x, yy, z, deep())
        # 床: 真ん中に黒い石の道 (幅 3)、縁に深層岩のタイル、門へ近いほどスカルクがにじむ
        for z in range(z0, gz):
            for x in range(cx - 5, cx + 6):
                if math.hypot(x - cx, z - cz) <= R - 0.5:
                    continue
                ax = abs(x - cx)
                st = 'polished_blackstone_bricks' if ax <= 1 else ('deepslate_tiles' if ax == 5 else 'polished_deepslate')
                if z >= gz - 5 and rng.random() < (z - (gz - 6)) * 0.12:
                    st = 'sculk'
                v.set(x, bottom - 1, z, st)
        # 柱の列 (x = cx ± 4、z = 4 ごと): 磨いた深層岩、根と頭は彫った石。柱の間の天井に吊った魂の灯り
        for z in range(cz + R + 1, gz - 1, 4):
            for sx in (-4, 4):
                for yy in range(bottom, bottom + 7):
                    v.set(cx + sx, yy, z, 'chiseled_deepslate' if yy in (bottom, bottom + 6) else 'polished_deepslate')
                v.set(cx + sx, bottom + 7, z, 'deepslate_tiles')
            v.set(cx, bottom + 8, z, 'chain[axis=y,waterlogged=false]')
            v.set(cx, bottom + 7, z, 'soul_lantern[hanging=true,waterlogged=false]')
            # 柱の内向きの面に魂の松明 (大広間の真ん中を照らす)
            v.set(cx - 3, bottom + 3, z, 'soul_wall_torch[facing=east]')
            v.set(cx + 3, bottom + 3, z, 'soul_wall_torch[facing=west]')
        # 壁の龕 (x = cx ± 6 に 1 マスの窪み、高さ 2): 骨・頭蓋・蝋燭・蜘蛛の巣。龕の上に黒い旗
        for k, z in enumerate(range(cz + R + 3, gz - 1, 4)):
            for sx, facing, rot in ((-6, 'east', 4), (6, 'west', 12)):
                x = cx + sx
                v.set(x, bottom, z, 'cave_air')
                v.set(x, bottom + 1, z, 'cave_air')
                v.set(x, bottom - 1, z, 'bone_block[axis=y]')
                if (k + (sx > 0)) % 2 == 0:
                    v.set(x, bottom, z, 'skeleton_skull[rotation=%d]' % rot)
                else:
                    v.set(x, bottom, z, 'white_candle[candles=3,lit=true,waterlogged=false]')
                v.set(x, bottom + 1, z, 'cobweb' if k % 3 == 1 else 'cave_air')
                v.set(x + (1 if sx < 0 else -1), bottom + 4, z, 'black_wall_banner[facing=%s]' % facing)
        # 奥の門の壁 (z = gz): 強化深層岩の柱 (x = cx ± 3、高さ 7)、泣く黒曜石の楣、上に目の浮き彫り、格子 (幅 5・高さ 6)
        for x in range(cx - 7, cx + 8):
            for yy in range(bottom - 1, bottom + 11):
                if abs(x - cx) <= 2 and bottom <= yy <= bottom + 5:
                    v.set(x, yy, gz, 'iron_bars[east=true,north=false,south=false,waterlogged=false,west=true]')
                elif abs(x - cx) == 3 and bottom <= yy <= bottom + 6:
                    v.set(x, yy, gz, 'reinforced_deepslate')
                elif abs(x - cx) <= 3 and yy == bottom + 6:
                    v.set(x, yy, gz, 'crying_obsidian')
                elif yy == bottom - 1:
                    v.set(x, yy, gz, 'polished_deepslate')
                else:
                    v.set(x, yy, gz, deep())
        for (dx, dy, st) in ((0, 7, 'crying_obsidian'), (-1, 7, 'chiseled_polished_blackstone'), (1, 7, 'chiseled_polished_blackstone'),
                             (0, 8, 'chiseled_deepslate'), (-2, 7, 'polished_blackstone'), (2, 7, 'polished_blackstone')):
            v.set(cx + dx, bottom + dy, gz - 1, st)
        # 門の両わきの灯り (魂の焚き火を台に) と碑「深淵の門」(北を向く。門の壁の外の一マス)
        for sx in (-5, 5):
            v.set(cx + sx, bottom, gz - 1, 'polished_deepslate')
            v.set(cx + sx, bottom + 1, gz - 1, 'soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
        v.set(cx - 4, bottom + 1, gz, 'skyabyss:tablet[facing=north,standing=false]')
        self.mark('abyss_gate_sign', cx - 4, bottom + 1, gz)
        # 格子の向こう: 下っていく暗い穴 (幅 5、7 マス。一歩ごとに一段下がる)。床はスカルク、奥にかすかな魂の灯り
        for k in range(1, 8):
            z = gz + k
            fl = bottom - 1 - k
            for x in range(cx - 4, cx + 5):
                for yy in range(fl - 1, bottom + 8):
                    ax = abs(x - cx)
                    if ax <= 2 and fl < yy <= bottom + 5:
                        v.set(x, yy, z, 'cave_air')
                    elif ax <= 2 and yy == fl:
                        v.set(x, yy, z, 'sculk' if rng.random() < 0.7 else 'deepslate_tiles')
                    else:
                        v.set(x, yy, z, deep())
        for x in range(cx - 4, cx + 5):
            for yy in range(bottom - 10, bottom + 8):
                v.set(x, yy, gz + 8, deep())
        v.set(cx, bottom - 6, gz + 7, 'soul_lantern[hanging=false,waterlogged=false]')
        # 門の前 (立つ所) の床に印のスカルク
        v.set(cx, bottom - 1, gz - 1, 'sculk')
        self.mark('abyss_gate', cx, bottom, gz - 1)
        self.mark('abyss_gate_bars', cx, bottom, gz)

    def _shaft_wall(self, x, y, z, ang, top, bottom, rng):
        """大縦穴の壁の一マス: 12 段ごとの帯 (深層岩のタイル、その上下に磨いた石)・30 度ごとの縦の筋 (磨いた玄武岩)・
        深いほど泣く黒曜石とスカルクがにじむ"""
        import math
        depth = (top - y) / max(1, top - bottom)      # 0 (上) 〜 1 (底)
        band = (top - y) % 12
        if band == 0:
            return 'deepslate_tiles'
        if band in (1, 11):
            return 'polished_deepslate'
        deg = (math.degrees(ang) + 360) % 30
        if deg < 2.2:
            return 'polished_basalt[axis=y]'
        r = rng.random()
        if depth > 0.55 and r < (depth - 0.55) * 0.22:
            return 'crying_obsidian'
        if depth > 0.7 and r < (depth - 0.7) * 0.9:
            return 'sculk'
        return self._rough('deep')
