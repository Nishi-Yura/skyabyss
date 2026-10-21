# -*- coding: utf-8 -*-
"""はじまりの都「鐘の宿り」(docs/design/33 の 2) の設計図を、建築の部品集 (vox.py) で書く。

python -I tools/capital/town.py [作風 a|b|c]   → src/main/resources/data/skyabyss/opening/capital.json と docs/review/town_roughs/capital_plan.png
座標: 中心 (0, 0)、+z が南 (野営地の方。SiteFinder が野営地の向きへ回す)、y=0 が地面の上の最初の段 (地面の上面は y=-1)。
壁の内は x, z とも -97..97。北の段 (x -60..60, z -97..-42) は高さ 5 (上面 y=4、建物は y=5 から)。
見取り図は案 A「鐘の都」、建物の形は 33 の 2.2 の採用の一覧 (大鐘楼 A・砦 A・組合 A・社 C・館 B・鍛冶場 A・市 C・住まい A・門と壁 A) を、
作り込んだ版にしたもの。壁の内は通りと広場と庭のほかは建物で埋める。

印 (marks) は今までと同じ名前: bell・smoke・door・stand・villager_1..10・open_<施設>・sign_<碑>。
閉じた施設 (組合・社・砦・館) の戸は鉄の戸と鉄格子 (CapitalDoors が印のまわり ±4 で外す)。
"""
from __future__ import annotations

import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vox  # noqa: E402
from facilities import Facilities  # noqa: E402
from vox import (Vox, Body, Mat, DX, OPP, gable_roof, hip_spire, tower, turret, arcade, fountain, rosette, pave,  # noqa: E402
                 flowerbed, lamp_post, double_lamp, tree, bench, planter, bush, pinnacle, _facade_cells)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss', 'opening')
PREVIEW = os.path.join(ROOT, 'docs', 'review', 'town_roughs', 'capital_plan.png')

W = 100          # 壁の外の面
IN = 97          # 壁の内の面
IN2 = 94         # 街区の外の縁 (壁ぎわに幅 3 の道を残す)
TER = 4          # 北の段の上面
TERZ = -42       # 北の段の前の縁 (z)
TERX = 60        # 北の段の東西の縁 (|x|)
GUILD_X1 = 78
SMITHY_X1 = 26
SMITHY_Z1 = 21
GAP = 4          # 町家と町家のあいだ (路地。軒が 1 出るので、軒のあいだに 2 の空き。3 では詰まって見える所があった: 2026-10-21 の試遊)


class Town(Facilities):
    def __init__(self, st):
        self.st = st
        self._init_fac()
        self.v = Vox(seed=20261021)
        self.rng = self.v.rng
        self.used: list[tuple[int, int, int, int]] = []  # 建物で埋めた範囲 (x0, z0, x1, z1)
        self.parts: list = []  # (名前, x0, z0, x1, z1): 建物ごとの足もと (軒・張り出しを含む)。検査 (audit.py) が間を調べる

    # ---------------------------------------------------------------- 小道具
    def mark(self, name, x, y, z):
        self.v.mark(name, x, y, z)

    def tablet(self, x, y, z, facing, name):
        self.v.set(x, y, z, 'skyabyss:tablet[facing=%s,standing=true]' % facing)
        self.v.mark('sign_' + name, x, y, z)

    def claim(self, x0, z0, x1, z1, name='part'):
        self.used.append((x0, z0, x1, z1))
        self.parts.append((name, x0, z0, x1, z1))

    def house(self, x0, z0, x1, z1, y0, floors, fh, front, axis, sides, ground='window', door=True, extras=(), infill=None,
              roof=None, sealed=True, quest=False):
        """町家一棟。front = 戸のある側、axis = 棟の向き、sides = 飾る側 (隣と接しない側)。
        sealed = 入れない家 (物語に関係のない家。中は埋め、窓は暗く、戸は壁に貼った跳ね上げ戸)
        quest = 大通りの 1 列目の入れる家 (二階建て。中を作り、印 quest_house_<n>。facilities.furnish_home)"""
        st, v = self.st, self.v
        if quest:
            sealed = False
            floors, fh = 2, 5
            extras = tuple(e for e in extras if e != 'turret')
        b = Body(v, st, x0, z0, x1, z1, y0, floors=floors, fh=fh, bay=4, door_side=front if door else 'none',
                 ground=ground, sides=sides, infill=infill or self.rng.choice(st.house_infills), pots=0.35, sealed=sealed).build()
        if floors >= 2 and self.rng.random() < 0.3:
            vox.timber_upper(v, st, b)
        for (dx_, dy_, dz_, out) in (v.fake_doors[-1:] if sealed and v.fake_doors and b.door_side != 'none' else []):
            vox.door_canopy(v, st, dx_, dy_, dz_, out) if self.rng.random() < 0.6 else None
        mat = roof or (self.rng.choice(st.roof_alts) if self.rng.random() < 0.25 else None)
        ridge = gable_roof(v, st, x0, z0, x1, z1, b.top + 1, axis=axis, steep=True,
                           dormers=(1 if (axis == 'x' and x1 - x0 >= 8) or (axis == 'z' and z1 - z0 >= 8) else 0) if 'dormer' in extras else 0,
                           chimney=((x0 + 2, z0 + 2) if 'chimney' in extras else None), mat=mat)
        if 'turret' in extras:
            cx = x1 if 'east' in sides else x0
            cz = z1 if front == 'south' else z0
            turret(v, st, cx, cz, b.top - 5, 8, half=1, rise=3)
        if sealed:
            v.seal(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y0)
        if quest:
            self.furnish_home(b)
        self.claim(x0 - 1, z0 - 1, x1 + 1, z1 + 1, ('quest@%d,%d' if quest else 'house@%d,%d') % (x0, z0))
        return b

    # ---------------------------------------------------------------- 地面と通り
    def ground(self):
        st, v = self.st, self.v
        # 壁の内は全部舗装 (あとで庭・花壇・草で上書き)
        pave(v, st, -IN, -IN, IN, IN, y=-1)
        # 大通り (幅 8): 縁石の筋と真ん中の模様
        for z in range(-IN, IN + 1):
            for x in (-4, 3):
                v.set(x, -1, z, st.pave_trim)
            if z % 6 == 0:
                v.set(-1, -1, z, st.pave_inlay[0])
                v.set(0, -1, z, st.pave_inlay[0])
        for x in range(-IN, IN + 1):
            for z in (-4, 3):
                v.set(x, -1, z, st.pave_trim)
            if x % 6 == 0:
                v.set(x, -1, -1, st.pave_inlay[0])
                v.set(x, -1, 0, st.pave_inlay[0])

    def street_lamps(self):
        """大通りの灯り。戸と施設の口の前 (外へ 4 まで、横に 1) には立てない"""
        st, v = self.st, self.v
        fronts = set()
        outs = list(self.openings) + [(x, z, o) for (x, y, z, o) in v.fake_doors]
        for (x, y, z), s in v.b.items():
            if '_door[' in s and 'trapdoor' not in s and 'half=lower' in s and y == 0:
                f = s.split('facing=')[1].split(',')[0].split(']')[0]
                outs.append((x, z, OPP[f]))
        for (x, z, o) in outs:
            dx, dz = DX[o]
            for k in range(1, 5):
                for j in (-1, 0, 1):
                    fronts.add((x + dx * k + (j if dz else 0), z + dz * k + (j if dx else 0)))
        for nm, (x, y, z) in v.marks.items():
            if nm.startswith('sign_') and y == 0:
                for ddx in (-1, 0, 1):
                    for ddz in (-1, 0, 1):
                        fronts.add((x + ddx, z + ddz))

        def lamp(x, z, arm):
            if (x, z) not in fronts:
                lamp_post(v, st, x, 0, z, h=4, arm=arm)
        for z in range(-36, IN - 2, 12):
            if -8 <= z <= 6:
                continue
            lamp(-5, z, 'east')
            lamp(4, z, 'west')
        for x in list(range(-IN + 4, -34, 12)) + list(range(36, IN - 2, 12)):
            lamp(x, -5, 'south')
            lamp(x, 4, 'north')

    # ---------------------------------------------------------------- 壁と門
    def walls(self):
        st, v = self.st, self.v
        H = 12
        T = 2
        wallb = st.wall_main
        for (ax0, az0, ax1, az1) in ((-W, -W, W, -W + T), (-W, W - T, W, W), (-W, -W, -W + T, W), (W - T, -W, W, W)):
            v.fill(ax0, -1, az0, ax1, -1, az1, st.base.full)
            for x in range(ax0, ax1 + 1):
                for z in range(az0, az1 + 1):
                    for y in range(0, H + 1):
                        v.set(x, y, z, st.base.full if y < 2 else (wallb if self.rng.random() > 0.12 else self.rng.choice(st.wall_mix2)))
        # 壁の上の歩廊と胸壁 (内と外)、外の面の帯と控え柱
        for i in range(-W, W + 1):
            for (x, z, out) in ((i, -W, 'north'), (i, W, 'south'), (-W, i, 'west'), (W, i, 'east')):
                v.set(x, H + 1, z, st.trim.wall() if i % 2 == 0 else st.trim.slab('bottom'))
                dx, dz = DX[out]
                v.set(x + dx, H, z + dz, st.trim.stairs(OPP[out], 'top'), keep=True)
                v.set(x + dx, 0, z + dz, st.base.stairs(OPP[out], 'bottom'), keep=True)
                if i % 10 == 0 and abs(i) < W - 6:
                    for y in range(0, H - 2):
                        v.set(x + dx, y, z + dz, st.base.full if y < 2 else st.pilaster)
                    v.set(x + dx, H - 2, z + dz, st.trim.stairs(OPP[out], 'top'))
            for (x, z) in ((i, -W + T), (i, W - T), (-W + T, i), (W - T, i)):
                if -W + T <= x <= W - T and -W + T <= z <= W - T:
                    v.set(x, H + 1, z, st.railing)
        # 壁の上の灯り
        for i in range(-W + 10, W - 9, 20):
            for (x, z) in ((i, -W + 1), (i, W - 1), (-W + 1, i), (W - 1, i)):
                v.set(x, H + 1, z, 'lantern[hanging=false,waterlogged=false]')
        # 隅の塔と、壁の途中の塔 (40 ごと)
        for cx in (-W - 1, W + 1):
            for cz in (-W - 1, W + 1):
                tower(v, st, cx, cz, half=5, y0=0, h=22, belfry=False, spire_rise=2, pinnacles=True, sealed=True)
        for k in (-60, -30, 30, 60):
            for (cx, cz) in ((k, -W - 1), (k, W + 1), (-W - 1, k), (W + 1, k)):
                tower(v, st, cx, cz, half=3, y0=0, h=17, belfry=False, spire_rise=2, pinnacles=False, sealed=True)
        # 四つの門 (門塔 2 本 + 幅 7 の門の口 + 上の楼)
        gates = (('gate_n', 'north'), ('gate_s', 'south'), ('gate_w', 'west'), ('gate_e', 'east'))
        for name, side in gates:
            self.gate(name, side)

    def gate(self, name, side):
        st, v = self.st, self.v
        # 北の門で書いて、回す
        g = Vox()
        g.rng = self.rng
        # 門の口 (x -3..3、z -W..-W+T)、門塔 (x -11..-5 と 5..11、z -W-3..-W+4)
        for xt in (-8, 8):
            tower(g, st, xt, -W + 1, half=3, y0=0, h=20, belfry=False, spire_rise=3, pinnacles=True, sealed=True)
        # 門楼 (口の上の部屋)
        b = Body(g, st, -5, -W - 2, 5, -W + 3, 0, floors=1, fh=8, bay=3, plinth=2, door_side='none', interior=False,
                 sides=('north', 'south'), pots=0)
        b.build()
        gable_roof(g, st, -5, -W - 2, 5, -W + 3, b.top + 1, axis='x', steep=True, finial=True)
        g.seal(-4, -W - 1, 4, -W + 2, 0)
        # 口を抜く (幅 7、高さ 6) とアーチ
        g.clear(-3, 0, -W - 3, 3, 5, -W + (3 if side == 'north' else 4))  # 北は門の内がすぐ段の階段なので、一段目を残す
        for z in (-W - 2, -W + 3):
            g.set(-3, 5, z, st.trim.stairs('west', 'top'))
            g.set(3, 5, z, st.trim.stairs('east', 'top'))
            g.set(-2, 6, z, st.trim.stairs('west', 'top'))
            g.set(2, 6, z, st.trim.stairs('east', 'top'))
            for x in range(-1, 2):
                g.set(x, 6, z, st.trim.slab('top'))
            g.set(0, 7, z, st.trim2)
        g.fill(-3, -1, -W - 4, 3, -1, -W + 4, st.pave_trim)
        for z in range(-W - 2, -W + 4):
            g.set(-3, -1, z, st.base.full)
            g.set(3, -1, z, st.base.full)
        # 門の両わきの灯りと旗
        for x in (-4, 4):
            g.set(x, 4, -W - 3, 'lantern[hanging=false,waterlogged=false]')
            g.set(x, 4, -W + 4, 'lantern[hanging=false,waterlogged=false]')
        # 門塔の外の面に旗を二枚ずつ、楼の屋根に旗竿
        for x in (-9, -7, 7, 9):
            g.set(x, 12, -W - 3, '%s_wall_banner[facing=north]' % st.banner_color)
            g.set(x, 12, -W + 5, '%s_wall_banner[facing=south]' % st.banner_color)
        g.fill(0, 17, -W, 0, 24, -W, st.fence)
        for yy in range(20, 24):
            g.set(1, yy, -W, '%s_wool' % st.banner_color)
            g.set(2, yy, -W, '%s_wool' % st.banner_color if yy > 20 else 'air')
        # 上がった落とし格子 (口の上の縁に鉄格子の歯) と鎖
        for x in range(-3, 4):
            g.set(x, 5, -W, 'iron_bars')
        for x in (-2, 2):
            g.set(x, 4, -W + 1, 'chain[axis=y,waterlogged=false]') if False else None
        # 門の中の番兵のくぼみ (両わき) と灯り
        for x, f in ((-4, 'east'), (4, 'west')):
            for z in (-W + 1,):
                g.set(x, 0, z, 'air')
                g.set(x, 1, z, 'air')
                g.set(x, 2, z, st.trim.stairs(f, 'top'))
                g.set(x - (1 if x < 0 else -1), 1, z, 'lantern[hanging=false,waterlogged=false]') if False else None
            g.set(x, 3, -W - 1, 'lantern[hanging=false,waterlogged=false]')
            g.set(x, 3, -W + 3, 'lantern[hanging=false,waterlogged=false]')
        # 門の外の道 (14 先まで) と灯りの並び
        for z in range(-W - 18, -W - 3):
            for x in range(-3, 4):
                g.set(x, -1, z, st.pave_trim if abs(x) == 3 else self.v.pick(st.pave))
        for z in (-W - 8, -W - 14):
            for x in (-5, 5):
                g.set(x, 0, z, st.base.full)
                g.fill(x, 1, z, x, 3, z, st.fence)
                g.set(x, 4, z, 'lantern[hanging=false,waterlogged=false]')
        # 碑は門の内の東
        rot = {'north': 0, 'east': 1, 'south': 2, 'west': 3}[side]
        for (x, y, z), s in g.b.items():
            X, Z, S = _rot(x, z, s, rot)
            v.set(X, y, Z, S)
        tx, tz = _rot(6, -W + 3 + 1, '', rot)[:2]
        facing = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}[side]
        if side == 'north':
            # 北の門の内は段の階段なので、碑は段の上 (階段を上がった所の東)
            self.tablet(6, TER + 1, -90, 'south', name)
        else:
            self.tablet(tx, 0, tz, facing, name)

    # ---------------------------------------------------------------- 北の段
    def terrace_steps(self):
        """北の段の前の縁の東と西に、段へ上がる掘り込みの階段 (幅 4。前の縁から北へ 5 段で段の上へ)。
        真ん中の大階段だけでは、段の東西の端から遠かった (2026-10-21 の試遊: 「ここに階段がほしい」)"""
        st, v = self.st, self.v
        z1 = TERZ
        for xa in (28, -32):
            xs = range(xa, xa + 4)
            for k in range(TER + 1):
                z = z1 - k
                for x in xs:
                    for y in range(0, k):
                        v.set(x, y, z, st.base.full)
                    v.set(x, k, z, st.base.stairs('north', 'bottom'))
                    for y in range(k + 1, TER + 4):
                        v.set(x, y, z, 'air')
            # 上がりきった所 (段の上面) と、前の縁の欄干・軒をあける
            for x in xs:
                for y in range(TER + 1, TER + 4):
                    v.set(x, y, z1 - TER - 1, 'air')
                v.set(x, TER, z1 + 1, 'air')
            # 両わきの壁 (掘った土を見せない) と、段の上の欄干
            for xe in (xa - 1, xa + 4):
                for k in range(TER + 2):
                    z = z1 - k
                    for y in range(0, TER + 1):
                        v.set(xe, y, z, st.base.full if y < TER else st.band)
                    v.set(xe, TER + 1, z, st.railing if k <= TER else st.trim.full)
                    for y in range(TER + 2, TER + 4):
                        v.set(xe, y, z, 'air')
                v.set(xe, TER + 2, z1, 'lantern[hanging=false,waterlogged=false]')

    def terrace(self):
        st, v = self.st, self.v
        x0, x1, z0, z1 = -TERX, TERX, -IN, TERZ
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                v.set(x, -1, z, st.base.full)
                for y in range(0, TER):
                    v.set(x, y, z, 'dirt')
                v.set(x, TER, z, v.pick(st.pave))
        # 前の縁と東西の縁: 土台の石、柱型、上に欄干
        for x in range(x0, x1 + 1):
            for y in range(0, TER + 1):
                v.set(x, y, z1, st.base.full if y < TER else st.band)
            v.set(x, TER + 1, z1, st.railing if x % 4 else st.trim.full)
            if x % 4 == 0:
                v.set(x, TER + 2, z1, 'lantern[hanging=false,waterlogged=false]' if x % 12 == 0 else st.trim.slab('bottom'))
            v.set(x, 0, z1 + 1, st.base.stairs('north', 'bottom'))
            v.set(x, TER, z1 + 1, st.trim.stairs('north', 'top'))
        for z in range(z0, z1 + 1):
            for xe, out in ((x0, 'west'), (x1, 'east')):
                for y in range(0, TER + 1):
                    v.set(xe, y, z, st.base.full if y < TER else st.band)
                v.set(xe, TER + 1, z, st.railing if z % 4 else st.trim.full)
                dx = -1 if out == 'west' else 1
                v.set(xe + dx, TER, z, st.trim.stairs(OPP[out], 'top'), keep=True)
        # 真ん中の大階段 (z -37 → -41 で 0 → 4 の 5 段、-42 の縁から段の上)。幅 12、両わきに塀と灯り
        # (前は 4 段目のあとが丸ごとのブロックで、上りきる所で一度跳ぶ形だった)
        for i in range(TER + 1):
            z = z1 + 5 - i
            for x in range(-6, 6):
                for y in range(0, TER + 1):
                    v.set(x, y, z, 'air' if y > i else st.base.full)
                v.set(x, i, z, st.base.stairs('north', 'bottom'))
            for x in (-7, 6):
                for y in range(0, i + 1):
                    v.set(x, y, z, st.base.full)
                v.set(x, i + 1, z, st.railing)
        for x in range(-6, 6):
            v.set(x, TER + 1, z1, 'air')
            v.set(x, TER + 2, z1, 'air')
            v.set(x, TER, z1, v.pick(st.pave))
        for x in (-7, 6):
            pinnacle(v, st, x, TER + 1, z1, h=2)
            v.set(x, 0, z1 + 6, st.base.full)
            v.set(x, 1, z1 + 6, 'lantern[hanging=false,waterlogged=false]')
        # 段の上の大通り
        for z in range(z0, z1 + 1):
            for x in (-4, 3):
                v.set(x, TER, z, st.pave_trim)
        self.claim(x0, z0, x1, z1, 'terrace')

    def north_gate_steps(self):
        """北の門の内から段の上へ上がる階段。門 (門楼の基壇・窓の板) を建てたあとに置く (先に置くと門が一段目を上書きする)"""
        st, v = self.st, self.v
        # 北の門から段の上へ上がる階段 (門の口 z -97 は地面の高さ、z -96..-92 で 0 → 4 の段、-91 から段の上。
        # 段の上面の高さ TER まで 5 段。門の口を抜くのは z -97 まで (前は -96 の一段目を消し、四段で最後に一段跳ぶ形だった)
        for x in range(-3, 4):
            for yy in range(0, TER + 4):
                v.set(x, yy, -97, 'air')
            v.set(x, -1, -97, st.pave_trim)
            for i in range(TER + 1):
                z = -96 + i
                for yy in range(0, TER + 4):
                    v.set(x, yy, z, 'air')
                for yy in range(-1, i):
                    v.set(x, yy, z, st.base.full)
                v.set(x, i, z, st.base.stairs('south', 'bottom'))
        for z in range(-97, -91):
            for x in (-4, 4):
                for yy in range(0, TER + 1):
                    v.set(x, yy, z, st.base.full)
                v.set(x, TER + 1, z, st.railing)

    def terrace_gardens(self):
        """段の上のすきま: 庭と並木、小さな噴水、記録の離れ"""
        st, v = self.st, self.v
        y = TER + 1
        # 大通りの両わきの並木と花壇 (z -70..-46)
        for z in range(-70, -45, 6):
            for x in (-8, 7):
                flowerbed(v, st, x - 1, z - 1, x + 1, z + 1, y=y, density=0.3, shrubs=0.1)
                tree(v, x, y, z, st.trunk2, st.leaves2, h=5, r=2)
        # 西の庭 (x -60..-38, z -70..-46): 花壇の格子と噴水
        flowerbed(v, st, -59, -69, -49, -60, y=y)
        flowerbed(v, st, -47, -69, -38, -60, y=y)
        # 鯨追いの野営の場所 (草地。NpcSpawner.spawnChaser が印 chaser を先に使う)
        for x in range(-60, -48):
            for z in range(-57, -45):
                v.set(x, TER, z, 'grass_block[snowy=false]')
        for (x, z) in ((-59, -46), (-49, -56), (-59, -56)):
            tree(v, x, y, z, st.trunk, st.leaves, h=5, r=2)
        v.set(-56, y, -49, 'spruce_log[axis=x]')
        v.set(-55, y, -49, 'spruce_log[axis=x]')
        self.mark('chaser', -53, y, -51)
        flowerbed(v, st, -47, -56, -38, -46, y=y)
        fountain(v, st, -48, -58, r=2, y=y)
        for (x, z) in ((-54, -64), (-43, -51), (-54, -51), (-43, -64)):
            tree(v, x, y, z, st.trunk, st.leaves, h=6, r=2)
        # 鐘楼の南の庭 (x -36..-10, z -66..-46)
        rosette(v, st, -27, -53, 6, y=TER)
        fountain(v, st, -27, -53, r=2, y=y)
        for (x, z) in ((-35, -47), (-19, -47), (-35, -58), (-19, -58)):
            double_lamp(v, st, x, y, z, h=4, axis='x')
        for (x, z) in ((-23, -46),):
            bench(v, st, x - 4, y, z, 'south', 3)
            bench(v, st, x + 2, y, z, 'south', 3)
        # 東の前庭 (砦の前、x 8..58, z -50..-44)
        for x in range(10, 58, 8):
            planter(v, st, x, y, -47, tall=True)
        # 記録の離れ (北の壁ぎわ x -24..-6, z -96..-86)
        self.house(-24, -96, -15, -86, y, 3, 5, 'south', 'z', ('south', 'west', 'east'), extras=('chimney',))
        self.house(-11, -96, -5, -86, y, 2, 5, 'south', 'z', ('south', 'east', 'west'), extras=('dormer',))
        # 館の南 (x -60..-38, z -82..-73): 写字の家
        # 館の前庭 (x -60..-38, z -80..-71): 石畳の模様、植え込み、腰掛け、灯り
        rosette(v, st, -49, -76, 4, y=TER)
        for (x, z) in ((-57, -78), (-41, -78), (-57, -73), (-41, -73)):
            planter(v, st, x, y, z, tall=True)
        bench(v, st, -55, y, -71, 'south', 3)
        bench(v, st, -45, y, -71, 'south', 3)
        double_lamp(v, st, -53, y, -76, h=4, axis='z')
        double_lamp(v, st, -45, y, -76, h=4, axis='z')

    # ---------------------------------------------------------------- 大鐘楼と語り部の家
    def bell_tower(self):
        """大鐘楼 (採用 A「石の鐘楼」を作り込んだもの)。中心 (-30, -76)、胴は 9×9、足もとは 11×11 の台座。
        台座は四方にアーチの口 (語り部は一階の鐘の足もとに立つ。鐘は一階の天井)。胴は四隅の控え柱 (二度細る)、
        各面の真ん中に細長いアーチ窓、両わきに盲アーチ、7 段ごとの持ち送りの帯。頂は露台つきの鐘の間 (5 幅のアーチ)、
        小尖塔の胸壁、屋根窓つきの尖塔。胴の中 (二階より上) は入れないので埋める"""
        st, v = self.st, self.v
        y0 = TER + 1
        cx, cz = -30, -76
        H = 4            # 胴の半幅
        BASE_TOP = y0 + 6
        SHAFT_TOP = y0 + 34
        BELL0 = SHAFT_TOP + 1       # 鐘の間の床の一つ上
        BELL1 = BELL0 + 5           # 鐘の間の天井
        g = Vox()
        g.rng = self.rng
        # ---- 南の面 (local: u = 東西 -H..H, z = H が外の面) を書いて、四方へ回す
        def face(gv: Vox):
            # 台座 (z = H+1 が外の面)
            for u in range(-H - 1, H + 2):
                for y in range(y0, BASE_TOP):
                    gv.set(u, y, H + 1, st.base.full if (y - y0) < 2 or abs(u) == H + 1 else st.wall_main)
                gv.set(u, y0, H + 2, st.base.stairs('north', 'bottom'))
                gv.set(u, BASE_TOP, H + 2, st.trim.stairs('north', 'top'))
                gv.set(u, BASE_TOP, H + 1, st.trim.full)
            # 台座のアーチの口 (幅 3、高さ 4)
            for u in range(-1, 2):
                for y in range(y0, y0 + 4):
                    for z in (H, H + 1):
                        gv.set(u, y, z, 'air')
                # 口の前の裾の段は置かない (入る手前の段がいらなかった。2026-10-21 の試遊)
                gv.set(u, y0, H + 2, 'air')
            for z in (H, H + 1):
                gv.set(-1, y0 + 3, z, st.trim.stairs('west', 'top'))
                gv.set(1, y0 + 3, z, st.trim.stairs('east', 'top'))
                gv.set(0, y0 + 4, z, st.trim2)
                gv.set(-2, y0 + 4, z, st.trim.stairs('west', 'top'))
                gv.set(2, y0 + 4, z, st.trim.stairs('east', 'top'))
            for u in (-2, 2):
                gv.fill(u, y0, H + 2, u, y0 + 3, H + 2, st.pilaster)
                gv.set(u, y0 + 4, H + 2, st.trim.stairs('north', 'top'))
            gv.set(-2, y0 + 2, H + 3, 'lantern[hanging=false,waterlogged=false]') if False else None
            # 胴 (z = H)
            for y in range(BASE_TOP + 1, SHAFT_TOP + 1):
                for u in range(-H, H + 1):
                    gv.set(u, y, H, st.wall_main if self.rng.random() > 0.08 else self.rng.choice(st.wall_mix2))
                    gv.set(u, y, H - 1, st.wall_main)
            # 持ち送りの帯 (7 段ごと)
            for yb in range(BASE_TOP + 7, SHAFT_TOP, 7):
                for u in range(-H, H + 1):
                    gv.set(u, yb, H, st.band)
                    gv.set(u, yb, H + 1, st.trim.stairs('north', 'top'))
                    if u % 2 == 0:
                        gv.set(u, yb - 1, H + 1, st.trim.stairs('north', 'top'))
            # 窓: 真ん中に細長いアーチ窓 (高さ 4)、両わきに盲アーチ (高さ 3、奥行き 1)
            for yb in range(BASE_TOP + 2, SHAFT_TOP - 3, 7):
                for y in range(yb, yb + 4):
                    gv.set(0, y, H, 'air')
                    gv.set(0, y, H - 1, st.glass)
                gv.set(0, yb - 1, H, st.trim.stairs('north', 'bottom'))
                gv.set(0, yb + 4, H, st.trim.stairs('north', 'top'))
                gv.set(0, yb + 5, H, st.trim2)
                for u in (-2, 2):
                    for y in range(yb + 1, yb + 4):
                        gv.set(u, y, H, st.base.full)
                    gv.set(u, yb + 4, H, st.trim.stairs('north', 'top'))
                    gv.set(u, yb, H, st.trim.slab('top'))
            # 丸窓 (上の帯の下)
            yc = SHAFT_TOP - 3
            for (a, b) in ((-1, 0), (1, 0), (0, 1), (0, -1)):
                gv.set(a, yc + b, H + 1, st.trim.full)
            gv.set(0, yc, H + 1, st.trim2)
            # 旗 (帯の下に二枚)
            for u in (-3, 3):
                gv.set(u, SHAFT_TOP - 9, H + 1, '%s_wall_banner[facing=south]' % st.banner_color)
            # 鐘の間 (5 幅のアーチ、柱)
            for y in range(BELL0, BELL1 + 1):
                for u in range(-H, H + 1):
                    gv.set(u, y, H, st.wall_main)
            for u in range(-2, 3):
                for y in range(BELL0, BELL0 + 4):
                    gv.set(u, y, H, 'air')
            gv.set(-2, BELL0 + 3, H, st.trim.stairs('west', 'top'))
            gv.set(2, BELL0 + 3, H, st.trim.stairs('east', 'top'))
            gv.set(-1, BELL0 + 4, H, st.trim.stairs('west', 'top'))
            gv.set(1, BELL0 + 4, H, st.trim.stairs('east', 'top'))
            gv.set(0, BELL0 + 4, H, st.trim2)
            for u in (-3, 3):
                gv.fill(u, BELL0, H, u, BELL0 + 4, H, st.pilaster)
            # 露台 (鐘の間の床の段を外へ一つ)
            for u in range(-H - 1, H + 2):
                gv.set(u, SHAFT_TOP, H + 1, st.trim.full)
                gv.set(u, SHAFT_TOP - 1, H + 1, st.trim.stairs('north', 'top'))
                gv.set(u, BELL0, H + 1, st.railing)
            # 上の蛇腹と胸壁
            for u in range(-H - 1, H + 2):
                gv.set(u, BELL1 + 1, H, st.band)
                gv.set(u, BELL1 + 1, H + 1, st.trim.stairs('north', 'top'))
                gv.set(u, BELL1 + 2, H + 1, st.trim.wall() if u % 2 == 0 else st.trim.slab('bottom'))
            # 隅の控え柱 (外へ 1、二度細る)
            for u in (-H, H):
                for y in range(BASE_TOP + 1, SHAFT_TOP + 1):
                    gv.set(u, y, H, st.corner)
                for y in range(BASE_TOP + 1, BASE_TOP + 15):
                    gv.set(u, y, H + 1, st.base.full)
                gv.set(u, BASE_TOP + 15, H + 1, st.base.stairs('north', 'bottom'))
                for y in range(BASE_TOP + 15, BASE_TOP + 24):
                    gv.set(u, y, H + 1, st.corner)
                gv.set(u, BASE_TOP + 24, H + 1, st.trim.stairs('north', 'bottom'))

        face(g)
        for r in range(4):
            for (x, y, z), s in g.b.items():
                X, Z, S = _rot(x, z, s, r)
                v.set(cx + X, y, cz + Z, S)
        # 隅の塊 (控え柱の角の 2×2) と、胸壁の角の小尖塔
        for sx in (-1, 1):
            for sz in (-1, 1):
                for y in range(BASE_TOP + 1, BASE_TOP + 15):
                    v.set(cx + sx * (H + 1), y, cz + sz * (H + 1), st.base.full)
                v.set(cx + sx * (H + 1), BASE_TOP + 15, cz + sz * (H + 1), st.base.slab('bottom'))
                for y in range(y0, BASE_TOP):
                    v.set(cx + sx * (H + 1), y, cz + sz * (H + 1), st.base.full)
                pinnacle(v, st, cx + sx * (H + 1), BELL1 + 2, cz + sz * (H + 1), h=4)
        # 中: 一階 (台座の中) は入れる。床・天井・鐘
        v.clear(cx - H, y0, cz - H, cx + H, y0 + 3, cz + H)
        for x in range(cx - H, cx + H + 1):
            for z in range(cz - H, cz + H + 1):
                v.set(x, y0 - 1, z, st.trim.full)
        rosette(v, st, cx, cz, 3, y=y0 - 1)
        v.fill(cx - H, y0 + 4, cz - H, cx + H, y0 + 4, cz + H, '%s_planks' % st.wood)
        v.set(cx, y0 + 3, cz, '%s_planks' % st.wood)
        v.set(cx, y0 + 2, cz, 'bell[attachment=ceiling,facing=north,powered=false]')
        self.mark('bell', cx, y0 + 2, cz)
        for (dx, dz) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
            v.fill(cx + dx, y0, cz + dz, cx + dx, y0 + 3, cz + dz, st.pilaster)
            v.set(cx + dx, y0 + 2, cz + dz + (1 if dz < 0 else -1), 'lantern[hanging=false,waterlogged=false]') if False else None
        v.set(cx - 2, y0 + 3, cz, 'lantern[hanging=true,waterlogged=false]')
        v.set(cx + 2, y0 + 3, cz, 'lantern[hanging=true,waterlogged=false]')
        # 胴の中は埋める (二階より上、鐘の間の床まで)。鐘の間の床と、頂の鐘
        v.seal(cx - H + 1, cz - H + 1, cx + H - 1, cz + H - 1, y0 + 5, filler=st.base.full, y1=SHAFT_TOP)
        for x in range(cx - H + 1, cx + H):
            for z in range(cz - H + 1, cz + H):
                v.set(x, SHAFT_TOP, z, '%s_planks' % st.wood)
                for y in range(BELL0, BELL1 + 1):
                    v.set(x, y, z, 'air')
                v.set(x, BELL1 + 1, z, '%s_planks' % st.wood)
        v.set(cx, BELL1, cz, 'bell[attachment=ceiling,facing=north,powered=false]')
        # 尖塔 (7×7 から、一段 3 の高さ) と、四面の屋根窓
        base = BELL1 + 2
        v.fill(cx - H, base, cz - H, cx + H, base, cz + H, st.trim.full)
        peak = hip_spire(v, st, cx - 3, cz - 3, cx + 3, cz + 3, base + 1, mat=st.spire, rise=4, flare=True)
        for (dx, dz, f) in ((0, 4, 'south'), (0, -4, 'north'), (4, 0, 'east'), (-4, 0, 'west')):
            x, z = cx + dx, cz + dz
            v.set(x, base + 1, z, st.trim.full)
            v.set(x, base + 2, z, st.glass)
            v.set(x, base + 3, z, st.glass)
            ox, oz = (1, 0) if f in ('north', 'south') else (0, 1)
            v.set(x - ox, base + 1, z - oz, st.pilaster)
            v.set(x + ox, base + 1, z + oz, st.pilaster)
            v.set(x - ox, base + 2, z - oz, st.pilaster)
            v.set(x + ox, base + 2, z + oz, st.pilaster)
            v.set(x - ox, base + 3, z - oz, st.spire.stairs(vox.CW[f] if f in ('north', 'south') else vox.CW[f], 'bottom'))
            v.set(x, base + 4, z, st.spire.full)
            v.set(x, base + 5, z, st.trim.wall())
        v.set(cx, peak + 1, cz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
        # 足もとの灯り (台座の四隅の外)
        for dx, dz in ((-7, 7), (7, 7), (-7, -7), (7, -7)):
            lamp_post(v, st, cx + dx, y0, cz + dz, h=3)
        self.claim(cx - H - 1, cz - H - 1, cx + H + 1, cz + H + 1, 'bell_tower')

    def storyteller_house(self):
        """語り部の家 (鐘楼の東、x -19..-9, z -82..-72。入れる)。一階は石、二階は濃い木の骨組みに白い漆喰。
        南に屋根つきの玄関、柵で囲んだ前庭と花壇。中: 暖炉と煙突、本棚と書見台、机と椅子、敷物。二階に寝台 (梯子で上がる)"""
        st, v = self.st, self.v
        y = TER + 1
        x0, z0, x1, z1 = -19, -82, -9, -72   # 鐘楼から 3 離す (前は -22..-12 で詰まっていた。2026-10-21 の試遊)
        b = Body(v, st, x0, z0, x1, z1, y, floors=2, fh=5, bay=4, door_side='south', door_at=5,
                 infill='stone_bricks', pots=0.6).build()
        log = '%s_log' % st.wood
        plaster = 'calcite'
        # 二階の骨組み (外の面の柱型と帯の段を木に、間を白い漆喰に)
        y2a, y2b = y + 5, b.top
        for side in ('north', 'south', 'east', 'west'):
            cells, out = _facade_cells(x0, z0, x1, z1, side)
            for i, (px, pz) in enumerate(cells):
                for yy in range(y2a, y2b + 1):
                    s = v.get(px, yy, pz)
                    if s is None or s == 'air' or 'pane' in s or 'stairs' in s or 'slab' in s or 'potted' in s:
                        continue
                    if yy in (y2a, y2b):
                        v.set(px, yy, pz, '%s[axis=%s]' % (log, 'x' if side in ('north', 'south') else 'z'))
                    elif i in (0, len(cells) - 1) or i % 3 == 0:
                        v.set(px, yy, pz, '%s[axis=y]' % log)
                    else:
                        v.set(px, yy, pz, plaster)
            # 斜めの筋交いの代わりに、柱のあいだの半分に濃い木の板
        roof_y = b.top + 1
        ridge = gable_roof(v, st, x0, z0, x1, z1, roof_y, axis='x', steep=True, dormers=1)
        # 東の妻の暖炉の煙突 (外に張り出す)
        cxh = x1 + 1
        for yy in range(y - 1, ridge + 3):
            for z in (-78, -77, -76):
                v.set(cxh, yy, z, st.base.full if yy < ridge + 2 else st.base.wall())
        v.set(cxh, ridge + 3, -77, 'campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
        # 玄関 (幅 5、奥行き 2): 柱・半ブロックの屋根・段・灯り
        dx_, dy_, dz_, _ = b.doors[0]
        px0, px1 = dx_ - 2, dx_ + 2
        for x in range(px0, px1 + 1):
            for z in (z1 + 1, z1 + 2):
                v.set(x, y - 1, z, st.trim.full)
                v.set(x, y + 3, z, st.roof.stairs('south', 'bottom') if z == z1 + 2 else st.roof.full)
                v.set(x, y + 4, z, st.roof.stairs('south', 'bottom') if z == z1 + 1 else 'air')
            v.set(x, y + 3, z1 + 3, st.roof_edge.stairs('south', 'bottom'))
            v.set(x, y - 1, z1 + 3, st.base.full)
            v.set(x, y, z1 + 3, st.base.stairs('north', 'bottom') if px0 < x < px1 else 'air')
        for x in (px0, px1):
            v.fill(x, y, z1 + 2, x, y + 2, z1 + 2, '%s[axis=y]' % log)
            v.set(x, y + 2, z1 + 1, '%s_fence' % st.wood)
        v.set(px0 + 1, y + 2, z1 + 2, 'lantern[hanging=true,waterlogged=false]')
        v.set(px1 - 1, y + 2, z1 + 2, 'lantern[hanging=true,waterlogged=false]')
        # 前庭 (柵、花壇、腰掛け)
        fz0, fz1 = z1 + 4, z1 + 9
        for x in range(x0 - 1, x1 + 2):
            v.set(x, y, fz1, st.fence if x not in (dx_, dx_ - 1, dx_ + 1) else 'air')
        for z in range(fz0, fz1 + 1):
            v.set(x0 - 1, y, z, st.fence)
            v.set(x1 + 1, y, z, st.fence)
        flowerbed(v, st, x0, fz0, dx_ - 3, fz1 - 1, y=y, edge=False, density=0.65)
        flowerbed(v, st, dx_ + 3, fz0, x1, fz1 - 1, y=y, edge=False, density=0.65)
        for x in range(dx_ - 1, dx_ + 2):
            for z in range(z1 + 4, fz1 + 1):
                v.set(x, y - 1, z, 'gravel' if (x + z) % 3 else st.pave_trim)
        bench(v, st, x0 + 1, y, fz0, 'north', 2)
        # ---- 中 (一階): 暖炉、本棚、書見台、机と椅子、敷物、樽
        ix0, iz0, ix1, iz1 = x0 + 2, z0 + 2, x1 - 2, z1 - 2
        for x in range(ix0, ix1 + 1):
            for z in range(iz0, iz1 + 1):
                v.set(x, y - 1, z, '%s_planks' % st.wood)
        for x in range(ix0 + 2, ix1 - 1):
            for z in range(iz0 + 2, iz1 - 1):
                v.set(x, y, z, 'red_carpet' if (x + z) % 2 == 0 else 'brown_carpet')
        # 暖炉 (東の壁、内の面に)
        hx = x1 - 1
        for z in (-78, -77, -76):
            v.fill(hx, y, z, hx, y + 3, z, 'bricks')
        v.set(hx, y, -77, 'campfire[facing=west,lit=true,signal_fire=false,waterlogged=false]')
        v.set(hx, y + 1, -77, 'air')
        v.set(hx - 1, y + 2, -78, 'brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]')
        v.set(hx - 1, y + 2, -77, 'brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]')
        v.set(hx - 1, y + 2, -76, 'brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]')
        v.set(hx - 1, y + 3, -77, 'candle[candles=3,lit=true,waterlogged=false]') if False else None
        # 北の壁の本棚と書見台
        v.fill(ix0, y, iz0, ix1 - 2, y + 2, iz0, 'bookshelf')
        v.set(ix0 + 3, y, iz0 + 1, 'lectern[facing=south,has_book=false,powered=false]')
        # 机と椅子
        v.set(ix0 + 1, y, iz1 - 1, '%s_fence' % st.wood)
        v.set(ix0 + 1, y + 1, iz1 - 1, '%s_pressure_plate[powered=false]' % st.wood)
        v.set(ix0 + 2, y, iz1 - 1, '%s_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]' % st.wood)
        v.set(ix0, y, iz1 - 1, '%s_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]' % st.wood)
        v.set(ix0 + 1, y + 2, iz1 - 1, 'air')
        v.set(ix1, y, iz1, 'barrel[facing=up,open=false]')
        v.set(ix1 - 1, y, iz1, 'barrel[facing=up,open=false]')
        v.set(ix1, y + 1, iz1, 'potted_fern')
        v.set((ix0 + ix1) // 2, y + 3, (iz0 + iz1) // 2, 'lantern[hanging=true,waterlogged=false]')
        # 二階 (床の段 y+5、立つ所 y+6) への梯子 (窓のない壁に)、寝台・箱・本棚
        self.place_ladder(ix0, iz0, ix1, iz1, y, y + 5, door=(b.doors[0][0], b.doors[0][2]))
        v.set(ix1, y + 6, iz0, 'red_bed[facing=north,part=head,occupied=false]')
        v.set(ix1, y + 6, iz0 + 1, 'red_bed[facing=north,part=foot,occupied=false]')
        v.set(ix0, y + 6, iz0, 'chest[facing=south,type=single,waterlogged=false]')
        v.fill(ix0 + 1, y + 6, iz0, ix0 + 3, y + 7, iz0, 'bookshelf')
        v.set((ix0 + ix1) // 2, y + 9, (iz0 + iz1) // 2, 'lantern[hanging=true,waterlogged=false]')
        # 煙の出る焚き火 (わら束の上)。大鐘楼の南の口の前の広場 (語り部は鐘楼の一階に立つ。煙のすぐそばで会えるように。
        # 2026-10-21 の試遊: 家の前の道だと語り部から遠く、一瞬迷った)。口の筋 (x -30) からは西へ外す
        sx, sz = -33, -67
        v.set(sx, y, sz, 'hay_block[axis=y]')
        v.set(sx, y + 1, sz, 'campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]')
        for (ddx, ddz) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            v.set(sx + ddx, y, sz + ddz, st.base.slab('bottom'))
        self.mark('smoke', sx, y + 1, sz)
        self.mark('door', dx_, y, dz_)
        self.claim(x0 - 1, z0 - 1, x1 + 2, fz1, 'storyteller_house')


    # ---------------------------------------------------------------- 空見の砦
    def keep(self):
        st, v = self.st, self.v
        y = TER + 1
        x0, z0, x1, z1 = 10, -92, 54, -52
        H = 10
        # 中庭の床
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                v.set(x, TER, z, v.pick(st.pave))
        # 幕壁 (厚さ 2)
        for (ax0, az0, ax1, az1, out) in ((x0, z0, x1, z0 + 1, 'north'), (x0, z1 - 1, x1, z1, 'south'),
                                          (x0, z0, x0 + 1, z1, 'west'), (x1 - 1, z0, x1, z1, 'east')):
            for x in range(ax0, ax1 + 1):
                for z in range(az0, az1 + 1):
                    for yy in range(y, y + H):
                        v.set(x, yy, z, st.base.full if yy < y + 2 else (st.wall_main if self.rng.random() > 0.1 else self.rng.choice(st.wall_mix2)))
            cells, o = _facade_cells(x0, z0, x1, z1, out)
            dx, dz = DX[o]
            for i, (cx, cz) in enumerate(cells):
                v.set(cx, y + H, cz, st.trim.wall() if i % 2 == 0 else st.trim.slab('bottom'))
                v.set(cx + dx, y + H - 1, cz + dz, st.trim.stairs(OPP[o], 'top'), keep=True)
                v.set(cx + dx, y, cz + dz, st.base.stairs(OPP[o], 'bottom'), keep=True)
                if i % 6 == 3:
                    for yy in range(y + 3, y + 5):
                        v.set(cx, yy, cz, 'iron_bars')
        # 隅の塔 4 と、北東の見張り塔 (高い)
        for (cx, cz) in ((x0 + 2, z0 + 2), (x1 - 2, z0 + 2), (x0 + 2, z1 - 2), (x1 - 2, z1 - 2)):
            tower(v, st, cx, cz, half=3, y0=y, h=17, belfry=False, spire_rise=3, pinnacles=False, sealed=True)
        tower(v, st, 44, -84, half=4, y0=y, h=30, belfry=True, spire_rise=3, pinnacles=True, sealed=True)
        # 幕壁の外の控え柱 (6 ごと) と、上の胸壁の内の手すり
        for side in ('north', 'south', 'east', 'west'):
            cells, o = _facade_cells(x0, z0, x1, z1, side)
            dx, dz = DX[o]
            for i, (cx, cz) in enumerate(cells):
                if i % 6 == 0 and 3 < i < len(cells) - 4:
                    for yy in range(y, y + H - 2):
                        v.set(cx + dx, yy, cz + dz, st.base.full if yy < y + 3 else st.wall_main)
                    v.set(cx + dx, y + H - 2, cz + dz, st.base.stairs(OPP[o], 'bottom'))
                    v.set(cx + 2 * dx, y, cz + 2 * dz, st.base.stairs(OPP[o], 'bottom'))
        for x in range(x0 + 2, x1 - 1):
            for z in (z0 + 2, z1 - 2):
                v.set(x, y + H, z, st.railing) if v.get(x, y + H - 1, z) not in (None, 'air') else None
        # 本丸 (閉じた館): x 18..37, z -80..-69。正面の真ん中に破風の張り出し、屋根に旗竿
        hall = Body(v, st, 18, -80, 37, -69, y, floors=2, fh=6, bay=4, door_side='south', door_at=9, door='iron',
                    pots=0.0).build()
        gable_roof(v, st, 18, -80, 37, -69, hall.top + 1, axis='x', steep=True, dormers=2, mat=st.spire)
        pav = Body(v, st, 23, -71, 31, -67, y, floors=2, fh=6, bay=4, door_side='south', door_at=4, door='iron',
                   sides=('south', 'east', 'west'), pots=0.0, interior=False).build()
        gable_roof(v, st, 23, -71, 31, -67, pav.top + 1, axis='z', steep=True, mat=st.spire)
        for (tx, tz) in ((18, -69), (37, -69), (18, -80), (37, -80)):
            turret(v, st, tx, tz, y, hall.top - y + 4, half=1, rise=3)
        for fx in (21, 34):
            v.fill(fx, hall.top + 1, -75, fx, hall.top + 12, -75, st.fence)
            for yy in range(hall.top + 8, hall.top + 12):
                v.set(fx + 1, yy, -75, '%s_wool' % st.banner_color)
                v.set(fx + 2, yy, -75, '%s_wool' % st.banner_color if yy > hall.top + 8 else 'air')
        dx_, dy_, dz_, _ = pav.doors[0]
        self.mark('open_keep2', dx_, y, dz_)
        # 南の門 (幅 5、高さ 5)。鉄格子で閉じる。両わきに小さな門塔、上に楼
        for x in range(30, 35):
            for yy in range(y, y + 5):
                v.set(x, yy, z1 - 1, 'air')
                v.set(x, yy, z1, 'iron_bars')
        v.set(30, y + 4, z1, st.trim.stairs('west', 'top'))
        v.set(34, y + 4, z1, st.trim.stairs('east', 'top'))
        for x in range(29, 36):
            v.set(x, y + 5, z1 + 1, st.trim.stairs('north', 'top'))
        v.set(32, y + 6, z1 + 1, st.trim2)
        for gx in (27, 37):
            tower(v, st, gx, z1, half=2, y0=y, h=15, belfry=False, spire_rise=3, pinnacles=False, sealed=True)
        for x in range(29, 36):
            for yy in range(y + 5, y + H + 3):
                v.set(x, yy, z1, st.wall_main if yy < y + H + 2 else (st.trim.wall() if x % 2 else st.trim.slab('bottom')))
            v.set(x, y + H + 1, z1 + 1, st.trim.stairs('north', 'top'))
        for x in (31, 33):
            v.set(x, y + 8, z1, st.glass)
            v.set(x, y + 9, z1, st.glass)
            v.set(x, y + 9, z1 + 1, '%s_wall_banner[facing=south]' % st.banner_color)
        self.tablet(23, y, z1 + 1, 'south', 'keep')
        self.mark('open_keep', 32, y, z1)
        lamp_post(v, st, 24, y, z1 + 3)
        lamp_post(v, st, 40, y, z1 + 3)
        # 中庭: 井戸、木、弓の的、わら束の厩 (東の壁ぎわ)、武具の樽、訓練の杭
        fountain(v, st, 28, -60, r=2, y=y)
        for (x, z) in ((16, -58), (48, -58), (16, -86), (24, -88)):
            tree(v, x, y, z, st.trunk, st.leaves, h=5, r=2)
        for x in range(40, 50, 3):
            v.fill(x, y, -62, x, y + 1, -62, st.fence)
            v.set(x, y + 2, -62, 'carved_pumpkin[facing=south]')
        for z in range(-76, -64, 3):
            v.set(51, y, z, 'target[power=0]')
            v.set(51, y + 1, z, 'target[power=0]')
        for z in range(-90, -82):
            v.set(50, y, z, '%s_fence' % st.wood if z % 3 else 'air')
            v.set(51, y, z, 'hay_block[axis=y]')
            v.set(51, y + 1, z, 'hay_block[axis=y]' if z % 2 else 'air')
            v.set(50, y + 3, z, '%s_slab[type=bottom,waterlogged=false]' % st.wood)
            v.set(51, y + 3, z, '%s_slab[type=bottom,waterlogged=false]' % st.wood)
            v.set(52, y + 3, z, '%s_slab[type=bottom,waterlogged=false]' % st.wood)
        for (x, z) in ((14, -66), (15, -66), (14, -65)):
            v.set(x, y, z, 'barrel[facing=up,open=false]')
        v.set(16, y, -66, 'grindstone[face=floor,facing=north]')
        v.set(14, y + 1, -66, 'anvil[facing=east]')
        self.claim(x0 - 1, z0 - 1, x1 + 1, z1 + 1, 'keep')

    # ---------------------------------------------------------------- 空見の館 (記録の塔の館)
    def hall(self):
        st, v = self.st, self.v
        y = TER + 1
        x0, z0, x1, z1 = -58, -96, -40, -84
        b = Body(v, st, x0, z0, x1, z1, y, floors=2, fh=6, bay=4, door_side='south', door_at=9, door='iron',
                 sides=('south', 'west', 'east'), pots=0.0).build()
        gable_roof(v, st, x0, z0, x1, z1, b.top + 1, axis='x', steep=True, dormers=2, mat=st.spire)
        # 中は本棚と書見台
        v.fill(x0 + 2, y, z0 + 2, x1 - 2, y + 1, z0 + 2, 'bookshelf')
        v.fill(x0 + 2, y, z1 - 2, x0 + 6, y + 1, z1 - 2, 'bookshelf')
        v.fill(x1 - 6, y, z1 - 2, x1 - 2, y + 1, z1 - 2, 'bookshelf')
        v.set((x0 + x1) // 2, y, (z0 + z1) // 2, 'lectern[facing=south,has_book=false,powered=false]')
        # 東の記録の塔 (八角、中心 -35, -90)
        self.octagon_tower(-34, -90, 5, y, 22)
        dx_, dy_, dz_, _ = b.doors[0]
        self.tablet(dx_ - 6, y, z1 + 3, 'south', 'hall')
        self.mark('open_hall', dx_, y, dz_)
        lamp_post(v, st, dx_ - 3, y, z1 + 3, h=3)
        lamp_post(v, st, dx_ + 3, y, z1 + 3, h=3)
        self.claim(x0 - 1, z0 - 1, x1 + 1, z1 + 1, 'hall')

    def octagon_tower(self, cx, cz, r, y0, h):
        """八角の塔 (半径 r)。帯と細い窓、頂は八角の尖り屋根"""
        st, v = self.st, self.v

        def inside(x, z, rr):
            return abs(x - cx) + abs(z - cz) <= rr + rr // 2 and abs(x - cx) <= rr and abs(z - cz) <= rr

        for yy in range(y0 - 1, y0 + h):
            for x in range(cx - r, cx + r + 1):
                for z in range(cz - r, cz + r + 1):
                    if not inside(x, z, r):
                        continue
                    edge = not inside(x + 1, z, r) or not inside(x - 1, z, r) or not inside(x, z + 1, r) or not inside(x, z - 1, r)
                    if yy == y0 - 1:
                        v.set(x, yy, z, st.base.full)
                    elif edge:
                        band = (yy - y0) % 7 == 6
                        win = (yy - y0) % 7 in (2, 3) and (x == cx or z == cz)
                        s = st.band if band else (st.glass if win else (st.base.full if yy < y0 + 2 else st.infill))
                        v.set(x, yy, z, s)
                    else:
                        v.set(x, yy, z, 'air')
        # 頂: 縮む八角を階段で
        yy = y0 + h
        rr = r + 1
        while rr >= 0:
            for x in range(cx - rr, cx + rr + 1):
                for z in range(cz - rr, cz + rr + 1):
                    if not inside(x, z, rr):
                        continue
                    edge = not inside(x + 1, z, rr) or not inside(x - 1, z, rr) or not inside(x, z + 1, rr) or not inside(x, z - 1, rr)
                    if edge and rr > 1:
                        # 外へ向く側の階段
                        if not inside(x, z - 1, rr):
                            s = st.spire.stairs('south')
                        elif not inside(x, z + 1, rr):
                            s = st.spire.stairs('north')
                        elif not inside(x - 1, z, rr):
                            s = st.spire.stairs('east')
                        else:
                            s = st.spire.stairs('west')
                        v.set(x, yy, z, s)
                        v.set(x, yy + 1, z, s)
                    else:
                        v.set(x, yy, z, st.spire.full)
                        v.set(x, yy + 1, z, st.spire.full)
            yy += 2
            rr -= 1
        v.set(cx, yy, cz, st.trim.wall())
        v.set(cx, yy + 1, cz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')

    # ---------------------------------------------------------------- 広場と市
    def plaza(self):
        st, v = self.st, self.v
        cx, cz, R = 0, -16, 19
        # 広場の床 (x -30..30, z -38..4)
        for x in range(-30, 31):
            for z in range(-38, 5):
                v.set(x, -1, z, v.pick(st.pave))
        rosette(v, st, cx, cz, R)
        fountain(v, st, cx, cz, r=4)
        # 露店 8 (輪)
        n = 1
        for k in range(8):
            a = math.radians(k * 45 + 22.5)
            x = int(round(math.cos(a) * 12)) + cx
            z = int(round(math.sin(a) * 12)) + cz
            self.stall(x, z, k)
            if n <= 6:
                self.mark('villager_%d' % n, x, 0, z)
                n += 1
        # 灯りの輪と腰掛け
        for k in range(8):
            a = math.radians(k * 45)
            double_lamp(v, st, int(round(math.cos(a) * 18)) + cx, 0, int(round(math.sin(a) * 18)) + cz, h=4,
                        axis='x' if k % 4 in (0,) or k % 4 == 2 else 'z')
        for (x, z, f) in ((-8, -2, 'south'), (5, -2, 'south'), (-8, -31, 'north'), (5, -31, 'north')):
            bench(v, st, x, 0, z, f, 3)
        # 四隅の花壇と木 (東西の大通りの口 z -4..3 と、広場の東の角の道具屋の戸 z -32 の前は空ける)
        for (x, z) in ((-26, -35), (26, -35), (-26, -10), (26, -10)):
            flowerbed(v, st, x - 2, z - 2, x + 2, z + 2, density=0.5)
            tree(v, x, 0, z, st.trunk2, st.leaves2, h=6, r=2)
        self.claim(-30, -38, 30, 4, 'plaza')

    STALLS = [
        # 種類, 布の二色, 台の上の品 (順に並べる), 台の下・まわり
        ('fruit', ('red_wool', 'white_wool'), ['melon', 'pumpkin', 'potted_red_tulip', 'melon'], ['hay_block[axis=y]', 'barrel[facing=up,open=false]']),
        ('flower', ('white_wool', 'lime_wool'), ['potted_red_tulip', 'potted_white_tulip', 'potted_azure_bluet', 'potted_oxeye_daisy'], ['flowering_azalea', 'azalea']),
        ('books', ('brown_wool', 'white_wool'), ['bookshelf', 'lectern[facing=south,has_book=false,powered=false]', 'bookshelf', 'chiseled_bookshelf[facing=south,slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=true,slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=true]'], ['barrel[facing=up,open=false]']),
        ('tools', ('gray_wool', 'red_wool'), ['anvil[facing=east]', 'grindstone[face=floor,facing=north]', 'smithing_table', 'chest[facing=south,type=single,waterlogged=false]'], ['barrel[facing=up,open=false]', 'iron_block']),
        ('cloth', ('red_wool', 'yellow_wool'), ['white_wool', 'red_wool', 'light_blue_wool', 'yellow_wool'], ['loom[facing=south]']),
        ('fish', ('blue_wool', 'white_wool'), ['barrel[facing=up,open=false]', 'cauldron', 'water_cauldron[level=3]', 'barrel[facing=up,open=false]'], ['dried_kelp_block']),
        ('bakery', ('white_wool', 'brown_wool'), ['hay_block[axis=y]', 'cake[bites=0]', 'hay_block[axis=y]', 'smoker[facing=south,lit=false]'], ['barrel[facing=up,open=false]']),
        ('lamps', ('black_wool', 'yellow_wool'), ['lantern[hanging=false,waterlogged=false]', 'soul_lantern[hanging=false,waterlogged=false]', 'lantern[hanging=false,waterlogged=false]', 'candle[candles=4,lit=false,waterlogged=false]'], ['chain[axis=y,waterlogged=false]']),
    ]

    def stall(self, x, z, k):
        """露店 (5×5)。四本の柱、縞の日よけ (切妻の布)、前に売り台 (半ブロック) と品物、吊り灯り、樽や箱"""
        st, v = self.st, self.v
        kind, (c1, c2), goods, extra = self.STALLS[k % len(self.STALLS)]
        post = '%s_fence' % st.wood
        for (ex, ez) in ((x - 2, z - 2), (x + 2, z - 2), (x - 2, z + 2), (x + 2, z + 2)):
            v.fill(ex, 0, ez, ex, 2, ez, post)
        # 日よけ: 縞 (x 方向に色を替える)、真ん中が高い
        for ex in range(x - 3, x + 4):
            c = c1 if (ex - x) % 2 == 0 else c2
            for ez in range(z - 3, z + 4):
                d = abs(ez - z)
                yy = 3 + (1 if d <= 1 else 0) + (1 if d == 0 else 0)
                v.set(ex, yy, ez, c)
        # 売り台 (南と北の縁) と品物
        for ex in range(x - 1, x + 2):
            for ez, f in ((z + 2, 'north'), (z - 2, 'south')):
                v.set(ex, 0, ez, '%s_slab[type=top,waterlogged=false]' % st.wood)
        for i, ex in enumerate(range(x - 1, x + 2)):
            g = goods[i % len(goods)]
            v.set(ex, 1, z + 2, g)
        v.set(x, 1, z - 2, goods[3 % len(goods)])
        # 中の箱と、吊り灯り
        v.set(x - 1, 0, z, extra[0])
        if len(extra) > 1:
            v.set(x + 1, 0, z, extra[1])
        v.set(x, 2, z, 'lantern[hanging=true,waterlogged=false]')

    # ---------------------------------------------------------------- 鯨追いの組合
    def guild(self):
        st, v = self.st, self.v
        # 東の端は 78 (前は 92。大きすぎた。2026-10-21 の試遊)。東の空いた所は取引所 (facilities.trade_house)
        x0, z0, x1, z1 = 50, -34, GUILD_X1, -10
        cz = (z0 + z1) // 2
        b = Body(v, st, x0, z0, x1, z1, 0, floors=2, fh=6, bay=4, door_side='west', door_at=12, door='iron',
                 sides=('west', 'north', 'south'), pots=0.2).build()
        ridge = gable_roof(v, st, x0, z0, x1, z1, b.top + 1, axis='x', steep=True, dormers=3)
        # 西の大戸: 両開きの鉄の戸 (二枚目)
        dx_, dy_, dz_, _ = b.doors[0]
        v.set(dx_, 0, dz_ - 1, 'iron_door[facing=east,half=lower,hinge=right,open=false,powered=false]')
        v.set(dx_, 1, dz_ - 1, 'iron_door[facing=east,half=upper,hinge=right,open=false,powered=false]')
        v.set(dx_ - 1, 0, dz_ - 1, 'air')
        v.set(dx_ - 1, 1, dz_ - 1, 'air')
        for zz in range(dz_ - 3, dz_ + 3):
            v.set(x0 - 1, 0, zz, 'air')
            v.set(x0 - 1, -1, zz, st.trim.full)
        self.mark('open_guild', dx_, 0, dz_)
        self.mark('label_guild', x0 - 4, 7, dz_)
        # 棟の上の鯨骨の肋 (三つ)
        for x in (x0 + 8, (x0 + x1) // 2, x1 - 8):
            prev = None
            for z in range(z0 - 2, z1 + 3):
                yy = b.top + 2 + int((ridge - b.top + 4) * (1 - ((z - cz) / ((z1 - z0) / 2 + 2)) ** 2))
                v.set(x, yy, z, 'bone_block[axis=z]')
                # 段が上がる所は縦に埋めて、肋を一続きにする (斜めだけでつながると、点線に見える)
                if prev is not None and prev != yy:
                    lo, hi = sorted((prev, yy))
                    zz = z if yy < prev else z - 1
                    for k in range(lo, hi + 1):
                        v.set(x, k, zz, 'bone_block[axis=y]')
                prev = yy
        # 西の前庭: 言伝ての板の枠と名簿の碑、灯り
        fx = x0 - 4
        for dz in (cz + 4, cz + 8):
            v.fill(fx, 0, dz, fx, 3, dz, '%s[axis=y]' % st.log)
        v.fill(fx, 4, cz + 3, fx, 4, cz + 9, '%s_slab[type=bottom,waterlogged=false]' % st.wood)
        v.fill(fx, 1, cz + 5, fx, 2, cz + 7, '%s_planks' % st.wood)
        self.tablet(x0 - 2, 0, dz_ - 4, 'west', 'guild')
        lamp_post(v, st, x0 - 2, 0, dz_ - 6)
        lamp_post(v, st, x0 - 2, 0, dz_ + 4)
        # 東の端の見張りの小塔
        turret(v, st, x1, z0, 0, b.top + 6, half=2, rise=3)
        self.claim(x0 - 1, z0 - 1, x1 + 1, z1 + 1, 'guild')

    # ---------------------------------------------------------------- 四巨神の社 (石の輪)
    def shrine(self):
        st, v = self.st, self.v
        cx, cz, r = -64, -21, 14
        # 社の堂 (2026-10-22 の二人の試遊: 転生の建物も都の作りに合わせる。上から入れてしまう)。石の輪をまるごと包む一階の高い堂と、
        # 四角錐の屋根。戸は東 (大通り側) の鉄の戸 (章で開く。CapitalDoors の open_shrine)。中の輪・石・祭壇はこのあと置く
        R2 = r + 1
        hall = Body(v, st, cx - R2, cz - R2, cx + R2, cz + R2, 0, floors=1, fh=13, bay=5, door_side='east', door='iron',
                    interior=False, sides=('north', 'south', 'east', 'west'), pots=0.0).build()
        hip_spire(v, st, cx - R2, cz - R2, cx + R2, cz + R2, hall.top + 1, mat=st.roof, rise=1, flare=True)
        for (lx, lz) in ((cx - 6, cz), (cx + 6, cz), (cx, cz - 6), (cx, cz + 6)):
            v.set(lx, 12, lz, 'chain[axis=y,waterlogged=false]')
            v.set(lx, 11, lz, 'lantern[hanging=true,waterlogged=false]')
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                v.set(x, -1, z, st.base.full)
        rosette(v, st, cx, cz, r - 2)
        # 低い壁と角柱
        for x in range(cx - r, cx + r + 1):
            for z in (cz - r, cz + r):
                v.set(x, 0, z, st.base.wall())
                if x % 5 == 1:
                    pinnacle(v, st, x, 0, z, h=1)
        for z in range(cz - r, cz + r + 1):
            for x in (cx - r, cx + r):
                v.set(x, 0, z, st.base.wall())
                if z % 5 == 1:
                    pinnacle(v, st, x, 0, z, h=1)
        # 四つの巨神の石 (3×3、高さ 9、頂に灯り) と骨の梁
        titan = (st.base.full, 'prismarine_bricks', 'cut_copper', 'polished_blackstone_bricks')
        for (dx, dz), tb, tid in zip(((-9, -9), (9, -9), (-9, 9), (9, 9)), titan, ('stone_whale', 'tide_shell', 'thunder_wing', 'ember')):
            v.fill(cx + dx - 1, 0, cz + dz - 1, cx + dx + 1, 8, cz + dz + 1, tb)
            self.mark('stone_' + tid, cx + dx, 0, cz + dz)  # 鍵の間: 石に触れると、その巨神の欠片が引かれる (CapitalShrine)
            for (ex, ez) in ((-2, 0), (2, 0), (0, -2), (0, 2)):
                v.set(cx + dx + ex, 0, cz + dz + ez, st.trim.stairs({(-2, 0): 'east', (2, 0): 'west', (0, -2): 'south', (0, 2): 'north'}[(ex, ez)]))
            v.set(cx + dx, 9, cz + dz, st.trim.full)
            v.set(cx + dx, 10, cz + dz, 'lantern[hanging=false,waterlogged=false]')
            for k in range(1, 9):
                x = cx + dx - (dx // 9) * k
                z = cz + dz - (dz // 9) * k
                v.set(x, 8 + k // 3, z, 'bone_block[axis=y]')
                # 斜めの一歩ごとに角を埋めて、肋を一続きにする
                if k > 1:
                    v.set(x + (dx // 9), 8 + k // 3, z, 'bone_block[axis=y]')
                    if (k - 1) // 3 != k // 3:
                        v.set(x + (dx // 9), 8 + (k - 1) // 3, z + (dz // 9), 'bone_block[axis=y]')
        v.fill(cx - 1, 11, cz - 1, cx + 1, 11, cz + 1, 'bone_block[axis=y]')
        v.set(cx, 10, cz, 'lantern[hanging=true,waterlogged=false]')
        v.fill(cx - 1, 0, cz - 1, cx + 1, 0, cz + 1, st.trim.full)
        v.set(cx, 1, cz, 'lantern[hanging=false,waterlogged=false]')
        # 東の門 (閉じている) と碑
        for z in range(cz - 1, cz + 2):
            for yy in range(0, 3):
                v.set(cx + r, yy, z, 'iron_bars')
        for z in (cz - 2, cz + 2):
            v.fill(cx + r, 0, z, cx + r, 3, z, st.pilaster)
            v.set(cx + r, 4, z, 'lantern[hanging=false,waterlogged=false]')
        v.fill(cx + r, 3, cz - 1, cx + r, 3, cz + 1, st.trim.full)
        self.tablet(cx + r + 4, 0, cz + 3, 'east', 'shrine')
        self.mark('open_shrine', cx + r, 0, cz)
        # 輪のまわりの木 (北と南)
        for x in range(cx - r + 2, cx + r - 1, 6):
            flowerbed(v, st, x - 1, cz - r - 4, x + 1, cz - r - 3, density=0.5)
            flowerbed(v, st, x - 1, cz + r + 3, x + 1, cz + r + 4, density=0.5)
        self.claim(cx - r - 1, cz - r - 5, cx + r + 1, cz + r + 5, 'shrine')

    # ---------------------------------------------------------------- 鍛冶場
    def smithy(self):
        st, v = self.st, self.v
        x0, z0, x1, z1 = 8, 8, SMITHY_X1, SMITHY_Z1   # 前は 36・26 (大きすぎた。2026-10-21 の試遊)
        cx = (x0 + x1) // 2
        b = Body(v, st, x0, z0, x1, z1, 0, floors=1, fh=7, bay=4, door_side='none', pots=0.0, interior=False,
                 infill=st.base.full).build()
        # 南は大きく開く (庇つき)
        cells, out = _facade_cells(x0, z0, x1, z1, 'south')
        for (px, pz) in cells[2:-2]:
            for yy in range(0, 5):
                v.set(px, yy, pz, 'air')
                v.set(px, yy, pz - 1, 'air')
                v.set(px, yy, pz + 1, 'air')
        for i, (px, pz) in enumerate(cells):
            if i % 5 == 0:
                v.fill(px, 0, pz + 3, px, 4, pz + 3, '%s[axis=y]' % st.log)
            v.set(px, 5, pz + 1, '%s_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]' % st.wood)
            v.set(px, 5, pz + 2, '%s_slab[type=top,waterlogged=false]' % st.wood)
            v.set(px, 5, pz + 3, '%s_slab[type=top,waterlogged=false]' % st.wood)
        gable_roof(v, st, x0, z0, x1, z1, b.top + 1, axis='x', steep=True, dormers=0)
        # 炉と煙突 (北の壁ぎわ)
        v.set(cx, 0, z0 + 3, 'skyabyss:resonance_forge')
        v.fill(cx - 1, 0, z0 + 2, cx + 1, 1, z0 + 2, st.base.full)
        v.fill(cx - 1, 2, z0 + 1, cx + 1, b.top + 12, z0 + 1, st.base.full)
        v.set(cx, b.top + 13, z0 + 1, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
        v.set(cx, 1, z0 + 3, 'air')
        v.set(cx + 4, 0, z0 + 3, 'anvil[facing=east]')
        v.set(cx + 5, 0, z0 + 3, 'smithing_table')
        v.set(cx + 6, 0, z0 + 3, 'grindstone[face=floor,facing=north]')
        v.set(cx - 4, 0, z0 + 3, 'blast_furnace[facing=south,lit=false]')
        v.set(cx - 6, 0, z0 + 3, 'chest[facing=south,type=single,waterlogged=false]')
        v.fill(x1 - 6, 0, z0 + 2, x1 - 2, 0, z0 + 2, 'barrel[facing=up,open=false]')
        v.fill(x1 - 6, 0, z0 + 9, x1 - 2, 0, z0 + 9, '%s_slab[type=top,waterlogged=false]' % st.wood)
        v.set(cx, 5, (z0 + z1) // 2, 'lantern[hanging=true,waterlogged=false]')
        self.tablet(x0 + 6, 0, z1 + 5, 'south', 'smithy')
        self.claim(x0 - 1, z0 - 1, x1 + 1, z1 + 4, 'smithy')

    # ---------------------------------------------------------------- 墓地 (西門の外)
    def graveyard(self):
        st, v = self.st, self.v
        x0, z0, x1, z1 = -136, -22, -108, 18
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                v.set(x, -1, z, 'grass_block[snowy=false]')
        for x in range(-107, -100):
            for z in range(-2, 2):
                v.set(x, -1, z, st.pave_trim)
        for x in range(x0, x1 + 1):
            v.set(x, 0, z0, 'cobblestone_wall')
            v.set(x, 0, z1, 'cobblestone_wall')
        for z in range(z0, z1 + 1):
            v.set(x0, 0, z, 'cobblestone_wall')
            v.set(x1, 0, z, 'cobblestone_wall')
        v.set(x1, 0, -1, 'air')
        v.set(x1, 0, 0, 'air')
        for x in range(x1 - 6, x1 + 1):
            v.set(x, -1, -1, 'gravel')
            v.set(x, -1, 0, 'gravel')
        for i in range(6):
            for j in range(5):
                gx = x0 + 4 + i * 4
                gz = z0 + 4 + j * 7
                if (i, j) == (2, 2) or (gx <= -122 and gz <= -9):  # 北西の隅は墓守の小屋 (facilities.gravekeeper_hut)
                    continue
                v.set(gx, 0, gz, 'stone_brick_wall')
                v.set(gx, 1, gz, 'stone_slab[type=bottom,waterlogged=false]')
                v.set(gx, -1, gz + 1, 'coarse_dirt')
                v.set(gx, -1, gz + 2, 'coarse_dirt')
        for z in (z0 + 18, z0 + 19, z0 + 20):
            v.set(x0 + 12, -1, z, 'coarse_dirt')
        v.set(x0 + 12, 0, z0 + 19, 'coarse_dirt')
        self.tablet(x0 + 14, 0, 0, 'east', 'graveyard')
        lamp_post(v, st, x0 + 14, 0, -3, h=3)
        lamp_post(v, st, x0 + 14, 0, 3, h=3)
        v.fill(x0 + 3, 0, z1 - 3, x0 + 3, 5, z1 - 3, 'stripped_spruce_log[axis=y]')
        v.set(x0 + 2, 5, z1 - 3, 'stripped_spruce_log[axis=x]')
        v.set(x0 + 4, 4, z1 - 3, 'stripped_spruce_log[axis=x]')

    # ---------------------------------------------------------------- 街区 (町家の並び)
    def block(self, x0, z0, x1, z1, fronts, depth=11, y0=0, shops=(), courtyard=True, lot=None, reserve=(), usable=(), prio='ns'):
        """街区 x0..x1 / z0..z1 の、fronts の側 (通りに面する側) に町家を隙間なく並べる。内は中庭。
        shops = 一階を店にする側。lot = 空き区画 (x0, z0, x1, z1, 名) を残す。reserve = 施設のために空けておく範囲の並び。
        usable = 入れる家 (町のクエストの家) にする側 (大通りの 1 列目)。prio = 'ew' なら東西の並びを端から端まで先に取る
        (南の大通りに面する街区。大通りに面する並びが角まで続く)"""
        st, v, rng = self.st, self.v, self.rng
        D = depth
        # 背中合わせの並びは、あいだに路地を残す (浅い街区では奥行きを縮める)
        if 'north' in fronts and 'south' in fronts and (z1 - z0 + 1) < 2 * D + GAP:
            D = max(6, (z1 - z0 + 1 - GAP) // 2)
        if 'west' in fronts and 'east' in fronts and (x1 - x0 + 1) < 2 * D + GAP:
            D = max(6, (x1 - x0 + 1 - GAP) // 2)
        taken = [tuple(r) for r in reserve]
        if lot:
            lx0, lz0, lx1, lz1, lname = lot
            taken.append((lx0, lz0, lx1, lz1))
        self._reserved = list(taken)

        def ns_rows(xa, xb):
            for side in ('north', 'south'):
                if side not in fronts:
                    continue
                zz0, zz1 = (z0, z0 + D - 1) if side == 'north' else (z1 - D + 1, z1)
                quest = side in usable
                xs = xa
                while xs <= xb:
                    w = rng.choice([9, 10, 11]) if quest else rng.choice([7, 8, 9, 9, 10, 11])
                    xe = min(xb, xs + w - 1)
                    if xb - xe < 6 + GAP:
                        xe = xb
                    if xe - xs < 5:
                        break
                    if any(not (xe + GAP < a or xs - GAP > c or zz1 + GAP < b or zz0 - GAP > d) for (a, b, c, d) in taken):
                        xs = xe + 1 + GAP
                        continue
                    sides = [side, 'west', 'east', OPP[side]]
                    floors = rng.choice([2, 3, 3, 4]) if D >= 11 else rng.choice([2, 3])
                    axis = 'z' if rng.random() < 0.7 else 'x'
                    extras = []
                    if rng.random() < 0.5:
                        extras.append('chimney')
                    if axis == 'x':
                        extras.append('dormer')
                    if (xe == x1 or xs == x0) and rng.random() < 0.6:
                        extras.append('turret')
                    self.house(xs, zz0, xe, zz1, y0, floors, 5, side, axis, tuple(sides),
                               ground='shop' if side in shops else 'window', extras=tuple(extras), quest=quest)
                    taken.append((xs, zz0, xe, zz1))
                    self.alley(xe + 1, zz0, xe + GAP, zz1, y0) if xe + GAP < xb else None
                    xs = xe + 1 + GAP

        def ew_rows(za, zb):
            for side in ('west', 'east'):
                if side not in fronts:
                    continue
                xx0, xx1 = (x0, x0 + D - 1) if side == 'west' else (x1 - D + 1, x1)
                quest = side in usable
                zs = za
                while zs <= zb:
                    w = rng.choice([9, 10, 11]) if quest else rng.choice([7, 8, 9, 10])
                    ze = min(zb, zs + w - 1)
                    if zb - ze < 6 + GAP:
                        ze = zb
                    if ze - zs < 5:
                        break
                    if any(not (xx1 + GAP < a or xx0 - GAP > c or ze + GAP < b or zs - GAP > d) for (a, b, c, d) in taken):
                        zs = ze + 1 + GAP
                        continue
                    sides = [side, OPP[side], 'north', 'south']
                    floors = rng.choice([2, 3, 3])
                    axis = 'x' if rng.random() < 0.7 else 'z'
                    extras = ['chimney'] if rng.random() < 0.5 else []
                    if axis == 'z':
                        extras.append('dormer')
                    self.house(xx0, zs, xx1, ze, y0, floors, 5, side, axis, tuple(sides),
                               ground='shop' if side in shops else 'window', extras=tuple(extras), quest=quest)
                    taken.append((xx0, zs, xx1, ze))
                    self.alley(xx0, ze + 1, xx1, ze + GAP, y0) if ze + GAP < zb else None
                    zs = ze + 1 + GAP

        if prio == 'ew':
            ew_rows(z0, z1)
            ns_rows(x0 + (D + GAP if 'west' in fronts else 0), x1 - (D + GAP if 'east' in fronts else 0))
        else:
            ns_rows(x0, x1)
            ew_rows(z0 + (D + GAP if 'north' in fronts else 0), z1 - (D + GAP if 'south' in fronts else 0))
        # 中庭
        if courtyard:
            cx0 = x0 + (D + 1 if 'west' in fronts else 0)
            cx1 = x1 - (D + 1 if 'east' in fronts else 0)
            cz0 = z0 + (D + 1 if 'north' in fronts else 0)
            cz1 = z1 - (D + 1 if 'south' in fronts else 0)
            if cx1 - cx0 >= 4 and cz1 - cz0 >= 4:
                self.courtyard(cx0, cz0, cx1, cz1, y0, taken)
        if lot:
            self.lot(*lot)
        self.claim(x0, z0, x1, z1, 'block')

    _edge_free: set = set()

    def alley(self, x0, z0, x1, z1, y0):
        """家と家のあいだの細い路地: 石畳、ときどき樽・灯り・木箱"""
        st, v, rng = self.st, self.v, self.rng
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                v.set(x, y0 - 1, z, rng.choice(['cobblestone', 'gravel', st.pave_trim, 'mossy_cobblestone']))
        if rng.random() < 0.5:
            cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
            v.set(cx, y0, cz, rng.choice(['barrel[facing=up,open=false]', 'composter[level=0]', 'hay_block[axis=y]']))

    def courtyard(self, x0, z0, x1, z1, y0, taken):
        st, v, rng = self.st, self.v, self.rng
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if any(a <= x <= c and b <= z <= d for (a, b, c, d) in taken):
                    continue
                v.set(x, y0 - 1, z, 'grass_block[snowy=false]')
        # 小道 (十字)、井戸か小さな噴水、木と花
        mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
        res = getattr(self, '_reserved', [])

        def near_res(x, z, r):
            return any(a - r <= x <= c + r and b - r <= z <= d + r for (a, b, c, d) in res)
        for x in range(x0, x1 + 1):
            if not near_res(x, mz, 0):
                v.set(x, y0 - 1, mz, 'gravel' if rng.random() < 0.5 else 'coarse_dirt')
        for z in range(z0, z1 + 1):
            if not near_res(mx, z, 0):
                v.set(mx, y0 - 1, z, 'gravel' if rng.random() < 0.5 else 'coarse_dirt')
        if x1 - x0 >= 8 and z1 - z0 >= 8 and not near_res(mx, mz, 5):
            fountain(v, st, mx, mz, r=2, y=y0)
        for (x, z) in ((x0 + 2, z0 + 2), (x1 - 2, z0 + 2), (x0 + 2, z1 - 2), (x1 - 2, z1 - 2)):
            if x1 - x0 >= 6 and z1 - z0 >= 6 and not near_res(x, z, 5):
                tree(v, x, y0, z, rng.choice([st.trunk, st.trunk2]), rng.choice([st.leaves, st.leaves2]), h=rng.choice([4, 5, 6]), r=2)
        for _ in range(max(1, (x1 - x0) * (z1 - z0) // 30)):
            x = rng.randint(x0, x1)
            z = rng.randint(z0, z1)
            if x != mx and z != mz and v.get(x, y0, z) is None and not near_res(x, z, 2):
                v.set(x, y0, z, rng.choice(st.flowers + ['short_grass', 'short_grass']))
        # 洗濯の綱 (フェンスの柱)
        if z1 - z0 >= 6 and not near_res(x0 + 1, z0 + 1, 2):
            v.fill(x0 + 1, y0, z0 + 1, x0 + 1, y0 + 2, z0 + 1, st.fence)

    def lot(self, x0, z0, x1, z1, name):
        """空き区画のあった所は小さな庭 (2026-10-21 の試遊: 都に空き区画もギルドの家もいらない)。草と花壇と木、真ん中に水盤、
        まわりに腰掛け。塀はなく、通りから歩いて入れる (詰まった町並みの息抜き)"""
        self.garden(x0, z0, x1, z1)

    def garden(self, x0, z0, x1, z1):
        st, v = self.st, self.v
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                v.set(x, -1, z, 'grass_block[snowy=false]')
                for yy in range(0, 12):
                    if v.get(x, yy, z) is not None:
                        v.b.pop((x, yy, z), None)
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        # 十字の小道 (砂利と石)
        for x in range(x0, x1 + 1):
            v.set(x, -1, cz, 'gravel' if (x + cz) % 3 else st.base.full)
        for z in range(z0, z1 + 1):
            v.set(cx, -1, z, 'gravel' if (cx + z) % 3 else st.base.full)
        # 水盤
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                v.set(cx + dx, -1, cz + dz, st.base.full)
                v.set(cx + dx, 0, cz + dz, st.base.slab('bottom') if (dx or dz) else 'water[level=0]')
        v.set(cx, -1, cz, st.base.full)
        v.set(cx, 0, cz, 'water[level=0]')
        # 四隅の花壇と木
        for (qx, qz) in ((x0 + 2, z0 + 2), (x1 - 2, z0 + 2), (x0 + 2, z1 - 2), (x1 - 2, z1 - 2)):
            flowerbed(v, st, qx - 1, qz - 1, qx + 1, qz + 1, density=0.6)
        for (qx, qz) in ((x0 + 3, z0 + 3), (x1 - 3, z1 - 3)):
            tree(v, qx, 0, qz, st.trunk2, st.leaves2, h=5, r=2)
        # 腰掛け (水盤のまわり、小道から外す)
        for (bx, bz, f) in ((cx - 3, cz - 2, 'east'), (cx + 3, cz + 2, 'west'), (cx - 2, cz + 3, 'north'), (cx + 2, cz - 3, 'south')):
            v.set(bx, 0, bz, '%s_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]' % (st.wood, f))
        for (lx, lz) in ((x0, z0), (x1, z1)):
            lamp_post(v, st, lx, 0, lz, h=3)

    def _old_lot(self, x0, z0, x1, z1, name):
        st, v = self.st, self.v
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                v.set(x, -1, z, 'grass_block[snowy=false]')
                for yy in range(0, 12):
                    if v.get(x, yy, z) is not None:
                        v.b.pop((x, yy, z), None)
        for (cx, cz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            v.set(cx, 0, cz, st.base.wall())
            v.set(cx, 1, cz, 'lantern[hanging=false,waterlogged=false]')
        for x in range(x0 + 1, x1):
            for z in (z0, z1):
                if (x - x0) % 3 != 0:
                    v.set(x, 0, z, st.fence)
        for z in range(z0 + 1, z1):
            for x in (x0, x1):
                if (z - z0) % 3 != 0:
                    v.set(x, 0, z, st.fence)
        mx = (x0 + x1) // 2
        v.set(mx, 0, z1, 'air')
        v.set(mx + 1, 0, z1, 'air')
        self.tablet(mx - 1, 0, z1 + 1, 'south', name)



    # ---------------------------------------------------------------- 閉じた施設の中身 (章で開いたあとに入れる)
    def furnish_guild(self):
        """組合 (イサナと会うと開く): 言伝ての板 2 枚 (日替わり・週替わりの頼み)、長机と腰掛け、帳場、暖炉、地図台、吊るした鯨の骨、二階の寝台"""
        st, v = self.st, self.v
        w = st.wood
        v.set(55, 0, -31, 'skyabyss:message_board[facing=south]')
        v.set(55, 0, -13, 'skyabyss:message_board[facing=north]')
        # 帰らなかった者の名簿 (帳場のそばの碑。読むと伝承に記される)
        self.tablet(70, 0, -24, 'west', 'guild_roll')
        for zt in (-26, -17):
            for x in range(58, 66):
                if (x - 58) % 5 == 0:
                    v.set(x, 0, zt, '%s_fence' % w)
                v.set(x, 1, zt, '%s_slab[type=bottom,waterlogged=false]' % w)
                if (x - 58) % 5 != 0:
                    v.set(x, 0, zt - 1, '%s_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]' % w)
                    v.set(x, 0, zt + 1, '%s_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]' % w)
            for x in range(60, 66, 4):
                v.set(x, 2, zt, 'candle[candles=3,lit=true,waterlogged=false]')
        # 帳場 (東の端)
        for z in range(-28, -15):
            v.set(71, 0, z, '%s_planks' % w)
            v.set(71, 1, z, '%s_slab[type=bottom,waterlogged=false]' % w)
            v.set(75, 0, z, 'barrel[facing=west,open=false]')
            v.set(75, 1, z, 'barrel[facing=west,open=false]' if z % 2 else 'bookshelf')
        v.set(71, 0, -22, 'air')
        v.set(71, 1, -22, 'air')
        v.set(72, 2, -24, 'lantern[hanging=false,waterlogged=false]')
        # 暖炉 (北の壁の真ん中)
        for x in range(63, 66):
            v.fill(x, 0, -32, x, 4, -32, 'bricks')
        v.set(64, 0, -32, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
        v.set(64, 1, -32, 'air')
        v.set(58, 0, -29, 'cartography_table')
        v.set(58, 0, -15, 'lectern[facing=north,has_book=false,powered=false]')
        # 吊るした鯨の骨と灯り
        for x in range(60, 70, 6):
            v.set(x, 5, -22, 'chain[axis=y,waterlogged=false]')
            v.set(x, 4, -22, 'bone_block[axis=x]')
            v.set(x - 1, 4, -22, 'bone_block[axis=x]')
            v.set(x + 1, 4, -22, 'bone_block[axis=x]')
            v.set(x, 5, -28, 'lantern[hanging=true,waterlogged=false]')
            v.set(x, 5, -16, 'lantern[hanging=true,waterlogged=false]')
        for x in range(58, 76, 8):
            v.set(x, 3, -32, '%s_wall_banner[facing=south]' % st.banner_color)
            v.set(x, 3, -12, '%s_wall_banner[facing=north]' % st.banner_color)
        # 二階へ梯子、寝台 6
        # 二階へ梯子 (東の内の壁 x 91 に付ける。二階の床に穴)
        for yy in range(0, 7):
            v.set(76, yy, -14, 'ladder[facing=west,waterlogged=false]')
        v.set(75, 7, -14, 'air')
        for x in range(56, 74, 5):
            v.set(x, 7, -32, 'red_bed[facing=north,part=head,occupied=false]')
            v.set(x, 7, -31, 'red_bed[facing=north,part=foot,occupied=false]')
            v.set(x + 1, 7, -32, 'chest[facing=south,type=single,waterlogged=false]')
            v.set(x, 11, -26, 'lantern[hanging=true,waterlogged=false]')

    def furnish_keep(self):
        """砦の本丸の記録の間 (心臓を二つ止めると開く): 順位の石、記録の板 2 枚、本棚と書見台、敷物"""
        st, v = self.st, self.v
        y = TER + 1
        v.set(27, y, -76, 'skyabyss:rebirth_stone')
        v.set(22, y, -78, 'skyabyss:record_board[facing=south]')
        v.set(32, y, -78, 'skyabyss:record_board[facing=south]')
        for z in range(-78, -71):
            v.fill(20, y, z, 20, y + 3, z, 'bookshelf')
            v.fill(35, y, z, 35, y + 3, z, 'bookshelf')
        for x in range(24, 31):
            for z in range(-75, -71):
                v.set(x, y, z, 'red_carpet')
        v.set(24, y, -78, 'lectern[facing=south,has_book=false,powered=false]')
        v.set(30, y, -78, 'lectern[facing=south,has_book=false,powered=false]')
        for x in (23, 31):
            v.set(x, y + 4, -74, 'chain[axis=y,waterlogged=false]')
            v.set(x, y + 3, -74, 'lantern[hanging=true,waterlogged=false]')

    def furnish_hall(self):
        """館の書庫 (心臓を三つ止めると開く): 本棚の列、閲覧の机、灯り"""
        st, v = self.st, self.v
        y = TER + 1
        for z in (-92, -89):
            for x in list(range(-55, -50)) + list(range(-47, -43)):
                v.fill(x, y, z, x, y + 2, z, 'bookshelf')
        for x in range(-52, -46):
            v.set(x, y, -87, '%s_slab[type=top,waterlogged=false]' % st.wood)
        v.set(-52, y, -86, '%s_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]' % st.wood)
        v.set(-50, y, -86, '%s_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]' % st.wood)
        v.set(-50, y + 1, -87, 'candle[candles=2,lit=true,waterlogged=false]')
        for x in (-53, -45):
            v.set(x, y + 4, -90, 'lantern[hanging=true,waterlogged=false]')


    # ---------------------------------------------------------------- 内装の部品 (入れる施設の部屋を豪華に)
    def room(self, x0, z0, x1, z1, y, h, floor='dark', shelves=True, beams=True, rug=True, wall_props=True):
        """部屋 (内の空き x0..x1 / z0..z1、床の上 y、高さ h) に: 縁取りの床、敷物の道、天井の梁と吊り灯り、
        壁ぎわの棚 (本棚・樽・箱・壺・鉢)、壁の灯りと旗、隅の植木鉢。家具の置かれた所と戸の前は避ける"""
        st, v, rng = self.st, self.v, self.rng
        w = st.wood
        doors = [(x, yy, z) for (x, yy, z), b in v.b.items() if '_door[' in b and 'half=lower' in b
                 and x0 - 2 <= x <= x1 + 2 and z0 - 2 <= z <= z1 + 2 and y - 1 <= yy <= y + 1]
        ladders = [(x, z) for (x, yy, z), b in v.b.items() if b.startswith('ladder') and x0 - 1 <= x <= x1 + 1 and z0 - 1 <= z <= z1 + 1 and y - 8 <= yy <= y + h]
        def near_door(x, z):
            return any(abs(x - dx) + abs(z - dz) <= 2 for (dx, _, dz) in doors) or any(abs(x - lx) <= 1 and abs(z - lz) <= 1 for (lx, lz) in ladders)
        def free(x, yy, z):
            b = v.get(x, yy, z)
            return b is None or b == 'air'
        # 床: 縁は濃い木、中は板と石の市松
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if floor == 'dark':
                    m = 'dark_oak_planks' if edge else ('%s_planks' % w if (x + z) % 2 == 0 else 'stripped_%s_wood[axis=y]' % w)
                else:
                    m = st.trim.full if edge else (st.base.full if (x // 2 + z // 2) % 2 == 0 else st.pave_trim)
                v.set(x, y - 1, z, m)
        # 敷物の道 (長い向きに、真ん中を 3 幅)
        if rug and x1 - x0 >= 6 and z1 - z0 >= 4:
            if x1 - x0 >= z1 - z0:
                zc = (z0 + z1) // 2
                for x in range(x0 + 2, x1 - 1):
                    for z in (zc - 1, zc, zc + 1):
                        if free(x, y, z):
                            v.set(x, y, z, 'red_carpet' if z == zc else 'brown_carpet')
            else:
                xc = (x0 + x1) // 2
                for z in range(z0 + 2, z1 - 1):
                    for x in (xc - 1, xc, xc + 1):
                        if free(x, y, z):
                            v.set(x, y, z, 'red_carpet' if x == xc else 'brown_carpet')
        # 梁と吊り灯り (梁は短い向きに渡す)
        if beams and h >= 4:
            yb = y + h - 1
            along_x = (x1 - x0) >= (z1 - z0)
            if along_x:
                for x in range(x0 + 2, x1 - 1, 4):
                    for z in range(z0, z1 + 1):
                        if free(x, yb, z):
                            v.set(x, yb, z, '%s_log[axis=z]' % w)
                    zc = (z0 + z1) // 2
                    if free(x, yb - 1, zc) and h >= 5:
                        v.set(x, yb - 1, zc, 'chain[axis=y,waterlogged=false]')
                        v.set(x, yb - 2, zc, 'lantern[hanging=true,waterlogged=false]')
            else:
                for z in range(z0 + 2, z1 - 1, 4):
                    for x in range(x0, x1 + 1):
                        if free(x, yb, z):
                            v.set(x, yb, z, '%s_log[axis=x]' % w)
                    xc = (x0 + x1) // 2
                    if free(xc, yb - 1, z) and h >= 5:
                        v.set(xc, yb - 1, z, 'chain[axis=y,waterlogged=false]')
                        v.set(xc, yb - 2, z, 'lantern[hanging=true,waterlogged=false]')
        # 壁ぎわの棚 (戸の前とすでに物がある所は避ける)。2 段: 下は本棚・樽・箱、上に壺・鉢・灯り
        if shelves:
            cells = [(x, z0) for x in range(x0, x1 + 1)] + [(x, z1) for x in range(x0, x1 + 1)] + \
                    [(x0, z) for z in range(z0 + 1, z1)] + [(x1, z) for z in range(z0 + 1, z1)]
            low = ['bookshelf', 'bookshelf', 'barrel[facing=up,open=false]', 'chiseled_bookshelf[facing=south,slot_0_occupied=true,slot_1_occupied=true,slot_2_occupied=false,slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=true]']
            top = ['decorated_pot[cracked=false,facing=north,waterlogged=false]', 'potted_fern', 'potted_red_tulip', 'candle[candles=3,lit=true,waterlogged=false]',
                   'lantern[hanging=false,waterlogged=false]', 'potted_azalea_bush', 'air']
            for i, (x, z) in enumerate(cells):
                if near_door(x, z) or not free(x, y, z):
                    continue
                if (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
                    v.set(x, y, z, 'potted_flowering_azalea_bush')
                    continue
                if i % 5 == 4:
                    continue
                b = rng.choice(low)
                if 'chiseled' in b:
                    f = 'south' if z == z0 else 'north' if z == z1 else 'east' if x == x0 else 'west'
                    b = b.replace('facing=south', 'facing=' + f)
                v.set(x, y, z, b)
                if h >= 4 and free(x, y + 1, z):
                    t = rng.choice(top)
                    if t != 'air':
                        v.set(x, y + 1, z, t)
        # 壁の旗 (内の面に、長い壁の真ん中あたり)
        if wall_props and h >= 5:
            for (x, z, f) in (((x0 + x1) // 2 - 3, z0 - 1, 'south'), ((x0 + x1) // 2 + 3, z0 - 1, 'south'),
                              ((x0 + x1) // 2 - 3, z1 + 1, 'north'), ((x0 + x1) // 2 + 3, z1 + 1, 'north')):
                b = v.get(x, y + 3, z)
                if b is not None and b != 'air' and 'pane' not in b and free(x, y + 3, z + (1 if f == 'south' else -1)):
                    v.set(x, y + 3, z + (1 if f == 'south' else -1), '%s_wall_banner[facing=%s]' % (st.banner_color, f))

    def table(self, x, y, z, length=3, axis='x', chairs=True):
        """長机 (柵の脚 + 板の天板 + 燭台) と両側の椅子"""
        st, v = self.st, self.v
        w = st.wood
        for k in range(length):
            xx, zz = (x + k, z) if axis == 'x' else (x, z + k)
            v.set(xx, y, zz, '%s_fence' % w if k in (0, length - 1) else 'air')
            v.set(xx, y, zz, '%s_fence' % w)
            v.set(xx, y + 1, zz, '%s_pressure_plate[powered=false]' % w)
            if k == length // 2:
                v.set(xx, y + 1, zz, 'candle[candles=2,lit=true,waterlogged=false]')
            if chairs:
                if axis == 'x':
                    v.set(xx, y, zz - 1, '%s_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]' % w)
                    v.set(xx, y, zz + 1, '%s_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]' % w)
                else:
                    v.set(xx - 1, y, zz, '%s_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]' % w)
                    v.set(xx + 1, y, zz, '%s_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]' % w)

    def decorate_interiors(self):
        """入れる施設の部屋を豪華に (形と家具を置いたあとに)"""
        y = TER + 1
        # 組合 (一階: 床 y0=0、高さ 6。二階: 床 7、高さ 5)
        self.room(52, -32, GUILD_X1 - 2, -12, 0, 6)
        self.room(52, -32, GUILD_X1 - 2, -12, 7, 5, rug=False)
        # 砦の本丸 (記録の間)
        self.room(20, -78, 35, -71, y, 6, rug=False)
        self.room(20, -78, 35, -71, y + 7, 5)
        # 空見の館 (書庫)
        self.room(-56, -94, -42, -86, y, 6, rug=True)
        self.room(-56, -94, -42, -86, y + 7, 5, rug=False)
        # 語り部の家 (一階は家具があるので、梁と灯りと隅の鉢だけ)
        self.room(-17, -80, -11, -74, y, 4, rug=False, shelves=False)
        # 鍛冶場 (石の床、棚は奥の壁だけ)
        self.room(10, 10, SMITHY_X1 - 2, 18, 0, 7, floor="stone", rug=False)
        # 大鐘楼の一階 (台座の中)
        self.room(-34, -80, -26, -72, y, 4, floor='stone', rug=False, shelves=False)


    def buttresses(self, x0, x1, z, out, y0, top, step=6, skip=()):
        """壁の外に控え柱 (下が太く、上で細る)。z 一列 (x0..x1)、out = 外の向き ('north'/'south')"""
        st, v = self.st, self.v
        dz = DX[out][1]
        for x in range(x0 + step // 2, x1, step):
            if any(abs(x - k) <= 2 for k in skip):
                continue
            h = top - y0
            for yy in range(y0, top - 1):
                v.set(x, yy, z + dz, st.base.full if yy < y0 + h // 2 else st.wall_main)
                if yy < y0 + h // 3:
                    v.set(x, yy, z + 2 * dz, st.base.full)
            v.set(x, y0 + h // 3, z + 2 * dz, st.base.stairs(OPP[out], 'bottom'))
            v.set(x, top - 1, z + dz, st.base.stairs(OPP[out], 'bottom'))
            v.set(x, y0 + h // 2 + 1, z + 2 * dz, 'lantern[hanging=false,waterlogged=false]') if False else None

    def exteriors(self):
        """形を建てたあとの外の作り込み: 組合・鍛冶場・砦の本丸の長い壁に控え柱と灯り、屋根の棟飾り"""
        st, v = self.st, self.v
        y = TER + 1
        # 組合: 北と南の長い壁 (x 50..GUILD_X1、高さ 12)
        self.buttresses(50, GUILD_X1, -34, 'north', 0, 12, step=7)
        self.buttresses(50, GUILD_X1, -10, 'south', 0, 12, step=7)
        for x in range(53, GUILD_X1 - 2, 7):
            for (z, f) in ((-11, 'south'), (-33, 'north')):
                lz = z + (1 if f == 'south' else -1)
                if v.get(x + 3, 3, lz) in (None, 'air'):
                    v.set(x + 3, 3, lz, 'lantern[hanging=false,waterlogged=false]') if False else None
        # 砦の本丸: 北の壁に控え柱
        self.buttresses(18, 37, -80, 'north', y, y + 12, step=5)
        # 鍛冶場: 北の壁 (炉の煙突の左右) と、東西の妻に控え柱
        # (前は x 8..36 のまま残っていて、鍛冶場を小さくしたあと、空き地に柱が立っていた)
        self.buttresses(8, SMITHY_X1, 8, 'north', 0, 7, step=6, skip=((8 + SMITHY_X1) // 2,))

    # ---------------------------------------------------------------- 名所の作り込み (形を建てたあとに足す)
    def hall_extra(self):
        """空見の館: 正面の列柱の玄関、南の壁の控え柱、棟の小尖塔"""
        st, v = self.st, self.v
        y = TER + 1
        x0, z0, x1, z1 = -58, -96, -40, -84
        # 控え柱 (南の面、4 ごと)
        for x in range(x0 + 2, x1 - 1, 4):
            for yy in range(y, y + 10):
                v.set(x, yy, z1 + 1, st.base.full if yy < y + 2 else st.wall_main, keep=True) if abs(x - (x0 + 9)) > 3 else None
            if abs(x - (x0 + 9)) > 3:
                v.set(x, y + 10, z1 + 1, st.base.stairs('north', 'bottom'))
        # 列柱の玄関 (戸の前、幅 9・奥行き 2)
        dxc = x0 + 9
        for x in range(dxc - 4, dxc + 5):
            for z in (z1 + 1, z1 + 2):
                v.set(x, y - 1, z, st.trim.full)
                v.set(x, y + 5, z, st.band)
            v.set(x, y + 6, z1 + 2, st.trim.wall() if x % 2 == 0 else st.trim.slab('bottom'))
            v.set(x, y + 5, z1 + 3, st.trim.stairs('north', 'top'))
            v.set(x, y - 1, z1 + 3, st.base.full)
        for x in (dxc - 4, dxc - 2, dxc + 2, dxc + 4):
            v.fill(x, y, z1 + 2, x, y + 4, z1 + 2, st.pilaster)
            v.set(x, y + 4, z1 + 3, st.trim.stairs('north', 'top'))
        for x in (dxc - 3, dxc + 3):
            v.set(x, y + 4, z1 + 2, st.trim.stairs('west' if x < dxc else 'east', 'top'))
        v.set(dxc, y + 6, z1 + 2, st.trim2)
        v.set(dxc, y + 7, z1 + 2, st.trim.wall())
        # 棟の小尖塔
        rx = (x0 + x1) // 2
        ridge_y = max(yy for (xx, yy, zz), b in v.b.items() if xx == rx and zz == -90 and b != 'air') + 1
        for yy in range(ridge_y - 2, ridge_y + 3):
            v.set(rx, yy, -90, st.pilaster)
        hip_spire(v, st, rx, -90, rx, -90, ridge_y + 3, mat=st.spire, rise=4, flare=True)

    def guild_extra(self):
        """鯨追いの組合: 西の正面の柱廊、戸の上の鯨の頭骨、前庭の顎骨の門、銛の掛け台と綱の樽"""
        st, v = self.st, self.v
        x0, z0, x1, z1 = 50, -34, GUILD_X1, -10
        dzc = z0 + 12
        # 柱廊 (西の面の外、奥行き 2)
        for z in range(z0 + 1, z1):
            v.set(x0 - 1, -1, z, st.trim.full)
            v.set(x0 - 2, -1, z, st.trim.full)
            v.set(x0 - 1, 5, z, st.band)
            v.set(x0 - 2, 5, z, st.band)
            v.set(x0 - 3, 5, z, st.trim.stairs('east', 'top'))
            v.set(x0 - 2, 6, z, st.trim.wall() if z % 2 == 0 else st.trim.slab('bottom'))
        for z in range(z0 + 1, z1, 3):
            if abs(z - dzc) <= 2:
                continue
            v.fill(x0 - 2, 0, z, x0 - 2, 4, z, st.pilaster)
            v.set(x0 - 2, 0, z, st.base.full)
            v.set(x0 - 2, 4, z + 1, st.trim.stairs('north', 'top')) if z + 1 < z1 else None
            v.set(x0 - 2, 4, z - 1, st.trim.stairs('south', 'top')) if z - 1 > z0 else None
        # 戸の上の鯨の頭骨 (骨のブロックで、口を開けた形)
        for (dz, yy) in ((-3, 7), (-2, 8), (-1, 8), (0, 8), (1, 8), (2, 7), (-3, 6), (2, 6), (-2, 9), (1, 9), (-1, 9), (0, 9)):
            v.set(x0 - 1, yy, dzc + dz, 'bone_block[axis=y]')
        v.set(x0 - 2, 8, dzc - 2, 'bone_block[axis=x]')
        v.set(x0 - 2, 8, dzc + 1, 'bone_block[axis=x]')
        v.set(x0 - 2, 7, dzc - 2, 'black_concrete') if False else None
        # 前庭の顎骨の門 (二本の反った骨が上で寄る。北の道具屋と南の預かり所のあいだ、幅 7)
        gx = 38
        for sgn in (1, -1):
            for (dz, yy) in ((-3, 0), (-3, 1), (-3, 2), (-3, 3), (-2, 4), (-2, 5), (-1, 6), (0, 7)):
                v.set(gx, yy, dzc + sgn * dz, 'bone_block[axis=y]')
        v.set(gx, 8, dzc, 'lantern[hanging=false,waterlogged=false]')
        v.set(gx, 6, dzc, 'chain[axis=y,waterlogged=false]')
        v.set(gx, 5, dzc, 'lantern[hanging=true,waterlogged=false]')
        # 銛の掛け台 (柵に避雷針の穂先) と綱の樽 (柱廊の北の端)
        for z in range(z0 + 3, z0 + 6):
            v.set(x0 - 3, 0, z, '%s_fence' % st.wood)
            v.set(x0 - 3, 1, z, '%s_fence' % st.wood)
            v.set(x0 - 3, 2, z, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
        v.fill(x0 - 3, 3, z0 + 3, x0 - 3, 3, z0 + 5, '%s_slab[type=bottom,waterlogged=false]' % st.wood)
        for (x, z) in ((x0 - 3, z0 + 1), (x0 - 3, z0 + 2)):
            v.set(x, 0, z, 'barrel[facing=up,open=false]')

    def shrine_extra(self):
        """四巨神の社: 低い壁の上に柱と梁の輪、真ん中に三段の祭壇、四方に篝火"""
        st, v = self.st, self.v
        cx, cz, r = -64, -21, 14
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                if max(abs(x - cx), abs(z - cz)) != r:
                    continue
                k = (x - cx) + (z - cz)
                if (abs(x - cx) == r and (z - cz) % 3 == 0) or (abs(z - cz) == r and (x - cx) % 3 == 0):
                    v.fill(x, 1, z, x, 4, z, st.pilaster)
                v.set(x, 5, z, st.band)
                v.set(x, 6, z, st.trim.wall() if k % 2 == 0 else st.trim.slab('bottom'))
        # 祭壇 (5×5 → 3×3 → 1)
        for (rr, yy, m) in ((2, 0, st.base.full), (1, 1, st.trim.full)):
            for x in range(cx - rr, cx + rr + 1):
                for z in range(cz - rr, cz + rr + 1):
                    v.set(x, yy, z, m)
        for (dx, dz, f) in ((0, 3, 'north'), (0, -3, 'south'), (3, 0, 'west'), (-3, 0, 'east')):
            for k in (-1, 0, 1):
                ax, az = (cx + k, cz + dz) if dx == 0 else (cx + dx, cz + k)
                v.set(ax, 0, az, st.base.stairs(f, 'bottom'))
        v.set(cx, 2, cz, st.trim2)
        v.set(cx, 3, cz, 'lantern[hanging=false,waterlogged=false]')
        self.mark('altar', cx, 2, cz)  # 転生の台座 (社が開いたあと右クリックで転生の画面)
        # 篝火
        for (dx, dz) in ((-6, 0), (6, 0), (0, -6), (0, 6)):
            v.set(cx + dx, 0, cz + dz, st.base.full)
            v.set(cx + dx, 1, cz + dz, 'campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
            for (ex, ez) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                v.set(cx + dx + ex, 0, cz + dz + ez, st.base.slab('bottom'))

    def smithy_extra(self):
        """鍛冶場: 太い煉瓦の煙突、前庭の水桶と金床、薪の山"""
        st, v = self.st, self.v
        x0, z0, x1, z1 = 8, 8, SMITHY_X1, SMITHY_Z1
        cx = (x0 + x1) // 2
        top = 7
        for x in range(cx - 2, cx + 3):
            for z in (z0, z0 + 1, z0 + 2):
                if abs(x - cx) == 2 or z == z0:
                    for yy in range(2, top + 16):
                        v.set(x, yy, z - 1, 'bricks' if yy < top + 14 else st.base.full)
        for x in range(cx - 3, cx + 4):
            v.set(x, top + 16, z0 - 2, st.base.slab('bottom'))
        v.set(cx, top + 16, z0 - 1, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
        for (x, z) in ((x0 + 1, z1 + 4), (x1 - 1, z1 + 4)):
            v.set(x, 0, z, 'water_cauldron[level=3]')
        v.set(x0 + 3, 0, z1 + 4, 'anvil[facing=north]')
        v.set(x1 - 3, 0, z1 + 4, 'anvil[facing=north]')
        for lx in range(x0 + 4, x1 - 2, 6):
            for lz in (z0 + 5, z0 + 11):
                v.set(lx, 6, lz, 'lantern[hanging=true,waterlogged=false]')
        for x in range(x0 + 5, x0 + 9):
            v.set(x, 0, z1 + 4, '%s_log[axis=x]' % st.wood)
            if x < x0 + 8:
                v.set(x, 1, z1 + 4, '%s_log[axis=x]' % st.wood)

    # ---------------------------------------------------------------- 全体
    def build(self):
        self.ground()
        self.terrace()
        self.walls()
        self.north_gate_steps()
        self.bell_tower()
        self.storyteller_house()
        self.keep()
        self.furnish_keep()
        self.hall()
        self.hall_extra()
        self.furnish_hall()
        self.terrace_gardens()
        self.plaza()
        self.guild()
        self.guild_extra()
        self.furnish_guild()
        self.shrine()
        self.shrine_extra()
        self.smithy()
        self.smithy_extra()
        self.exteriors()
        self.decorate_interiors()
        self.districts()
        self.facilities()
        self.street_lamps()
        self.terrace_steps()
        self.graveyard()
        self.gravekeeper_hut()
        self.mark('stand', 0, 0, 94)
        self.attendants()
        n = 7
        for (x, z) in ((-30, 31), (30, 31), (-30, 65), (30, 65)):
            self.mark('villager_%d' % n, x, 0, z)
            n += 1
        self.tidy_for_walkers()
        self.drop_floaters()
        self.v.connect()
        return self.v

    def drop_floaters(self):
        """床に置く小物 (壺・鉢・ろうそく・置いた灯り・敷物) で、下が空いているものを消す。
        棚や机をあとの手で消したとき、上の小物だけが宙に残る (audit_odd.py の「支えのない付き物」)"""
        v = self.v
        small = ('decorated_pot', 'potted_', 'candle', '_carpet', 'flower_pot')
        for (x, y, z), s in list(v.b.items()):
            n = s.split('[')[0]
            hang = n.endswith('lantern') and 'hanging=true' in s
            if any(t in n for t in small) or (n.endswith('lantern') and not hang):
                below = v.b.get((x, y - 1, z))
                if y > 0 and (below is None or below in ('air', 'cave_air')):
                    v.set(x, y, z, 'air')
            # 木の切れ端 (葉・幹がひとつだけ宙に残ったもの)
            elif n.endswith('_leaves') or n.endswith('_log'):
                nb = [v.b.get((x + dx, y + dy, z + dz)) for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))]
                if y > 0 and all(t is None or t in ('air', 'cave_air') for t in nb):
                    v.set(x, y, z, 'air')

    # 係の人の印 (docs/design/35 の 6.6)。今ある施設の戸 (open_* の印や開いた正面) から中へ歩き、
    # 戸から 3 歩以上入り、戸の筋から 2 横にずれた所で、足もと 2 マスが空き、下が床の最初の場所に置く。見つからない役は置かない (Attendants が飛ばす)
    def attendants(self):
        v = self.v
        m = v.marks

        def free(x, y, z):
            return v.get(x, y, z) in (None, 'air') and v.get(x, y + 1, z) in (None, 'air')

        def floor(x, y, z):
            below = v.get(x, y - 1, z)
            return (below not in (None, 'air') and 'slab[type=bottom' not in below and 'carpet' not in below) or (below is None and y == 0)

        def put(name, start, step):
            if start is None:
                return
            sx, sy, sz = start
            dx, dz = step
            for i in range(3, 12):
                for side in (2, -2, 1, -1, 3, -3, 0):  # 戸の筋 (両開きの二枚目も) は空ける
                    x = sx + dx * i + (1 if dx == 0 else 0) * side
                    z = sz + dz * i + (1 if dz == 0 else 0) * side
                    if free(x, sy, z) and floor(x, sy, z):
                        self.mark(name, x, sy, z)
                        return

        put('npc_shrine_priest', m.get('open_shrine'), (-1, 0))
        put('npc_keep_veteran', m.get('open_keep'), (0, -1))
        put('npc_archivist', m.get('open_hall'), (0, -1))

    def tidy_for_walkers(self):
        """入れる建物のまわりと中の、歩く人のじゃまになる飾りを直す (ゲーム内の建築の監査に合わせる)。
        - 柵の上の感圧板の机 → 板の上付きの半ブロックの机 (感圧板は丸いブロックの上にだけ)
        - 戸の内と外の一つ目の敷物を外す (戸の前は空ける)
        - 入れる建物 (施設・町のクエストの家・組合・館) の外の面の、低い所の開いた鎧戸を外す (歩く人の頭に当たる)"""
        v, st = self.v, self.st
        for (x, y, z), s_ in list(v.b.items()):
            if 'pressure_plate' in s_ and (v.get(x, y - 1, z) or '').endswith('_fence'):
                v.set(x, y - 1, z, '%s_slab[type=top,waterlogged=false]' % st.wood)
                v.set(x, y, z, self.air(y))
        for (x, y, z), s_ in list(v.b.items()):
            if '_door[' in s_ and 'trapdoor' not in s_ and 'half=lower' in s_:
                f = s_.split('facing=')[1].split(',')[0].split(']')[0]
                dx, dz = DX[f]
                for k in (1, -1, 2, -2):
                    c = v.get(x + dx * k, y, z + dz * k) or ''
                    if c.endswith('carpet'):
                        v.set(x + dx * k, y, z + dz * k, 'air')
        open_parts = [p for p in self.parts if p[0].startswith('quest@') or p[0] in self.fac_names or p[0] in ('guild', 'hall')]
        for (nm, x0, z0, x1, z1) in open_parts:
            ytop = 14 if nm in ('guild', 'hall') else 4
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if x not in (x0, x1) and z not in (z0, z1):
                        continue
                    for y in range(-1, ytop + 6):
                        c = v.get(x, y, z) or ''
                        if '_trapdoor[' in c and 'open=true' in c:
                            v.set(x, y, z, 'air')
                        # 柱型の足の上の飾りの階段 (高さ 2) は、灯りの柱や柱廊とのあいだで頭に当たるので外す
                        elif y == 2 and c.startswith(st.trim.stem + '_stairs') and 'half=bottom' in c:
                            v.set(x, y, z, 'air')

    def districts(self):
        """街区 (町家の並び)。施設の所は reserve で空け、大通りの 1 列目は usable で入れる家にする (docs/design/35 の 2.1)"""
        # 広場の西の並び (北半分)。南の面は社と広場のあいだの空き地 (広場から西の大通りへ) に面するので入れる家
        self.block(-47, -38, -33, -27, ('east', 'south'), depth=11, shops=('east',), courtyard=False, usable=('south',))
        # 広場の東の角は道具屋 (facilities.tool_shop)
        # 北西・北東の角 (段の外、x ±61..97、z -97..-44)
        self.block(-IN2, -IN2, -63, -45, ('south', 'east', 'north', 'west'), depth=11, lot=(-80, -80, -64, -64, 'lot_1'))
        self.block(63, -IN2, IN2, -45, ('south', 'west', 'north', 'east'), depth=11, lot=(64, -80, 80, -64, 'lot_2'))
        # 社の西 (x -97..-82)。南の面は西の大通りに面するので入れる家
        self.block(-IN2, -40, -81, -7, ('east', 'north', 'south', 'west'), depth=10, courtyard=False, usable=('south',))
        # 南: 東西の通り z 28..34 と 62..68、南北の横通り x -52..-46 と 46..52
        # 帯 0 (z 6..27): 止まり木 (x -94..-71)、入れる家と裏の並び、宿 (x -44..-8)、鍛冶場、狭い並び、薬師の店・入れる家・裏の並び
        self.block(-67, 6, -53, 27, ('north', 'east', 'south'), depth=10, usable=('north',))
        self.block(40, 6, 45, 27, ('east',), depth=6, courtyard=False)
        self.block(70, 6, IN2, 16, ('north',), depth=11, courtyard=False, usable=('north',))
        self.block(53, 21, IN2, 27, ('south',), depth=7, courtyard=False)
        # 帯 1 (z 35..61): 古井戸の小広場、酒場、訓練場、闇市
        self.block(-IN2, 35, -53, 61, ('north', 'east', 'west', 'south'), depth=11, reserve=[(-64, 43, -53, 53)])
        self.block(-45, 35, -7, 61, ('north', 'west', 'east', 'south'), depth=11, usable=('east',), prio='ew', reserve=[(-40, 36, -28, 46)])
        self.block(53, 35, IN2, 61, ('north', 'west', 'east', 'south'), depth=11, reserve=[(53, 40, 63, 50)])
        # 帯 2 (z 69..94): 空き区画 3・4・5、地図屋
        self.block(-IN2, 69, -53, IN2, ('north', 'east', 'west', 'south'), depth=11, lot=(-80, 76, -64, 92, 'lot_3'))
        self.block(-45, 69, -7, IN2, ('north', 'west', 'east', 'south'), depth=11, usable=('east',), prio='ew')
        self.block(7, 69, 45, IN2, ('north', 'west', 'east', 'south'), depth=11, usable=('west',), prio='ew', lot=(22, 75, 36, 89, 'lot_4'))
        self.block(53, 69, IN2, IN2, ('north', 'west', 'east', 'south'), depth=11, lot=(64, 76, 80, 92, 'lot_5'), reserve=[(83, 69, 94, 79)])

    def facilities(self):
        """施設 (facilities.py)。街区のあとに建てる (街区は reserve で場所を空けている)"""
        self.tool_shop()
        self.storage()
        self.trade_house()
        self.apothecary()
        self.guild_desk()
        self.smithy_front()
        self.inn()
        self.perch()
        self.training_yard()
        self.old_well()
        self.black_market()
        self.mapmaker()
        self.tavern()
        self.upper_rooms()


def _rot(x, z, s, rot):
    """北の門で書いたものを rot 回 (90 度ずつ時計回り) 回す。状態の向きも回す"""
    cw = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}
    X, Z = x, z
    for _ in range(rot):
        X, Z = -Z, X
    if s:
        for _ in range(rot):
            s = _turn(s, cw)
    return X, Z, s


def _turn(s, cw):
    import re

    def rep_facing(m):
        return 'facing=' + cw[m.group(1)]
    s2 = re.sub(r'facing=(north|east|south|west)', rep_facing, s)
    if '[' in s2 and any(k in s2 for k in ('north=', 'east=', 'south=', 'west=')):
        # つながりの向きは connect() が決め直すので消す
        name = s2.split('[')[0]
        props = [p for p in s2[s2.index('[') + 1:-1].split(',') if not p.split('=')[0] in ('north', 'south', 'east', 'west')]
        s2 = name + ('[' + ','.join(props) + ']' if props else '')
    if 'axis=x' in s2:
        s2 = s2.replace('axis=x', 'axis=z')
    elif 'axis=z' in s2:
        s2 = s2.replace('axis=z', 'axis=x')
    return s2


def preview(v: Vox):
    from PIL import Image
    xs = [k[0] for k in v.b]
    zs = [k[2] for k in v.b]
    x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
    top = {}
    for (x, y, z), s in v.b.items():
        if s == 'air':
            continue
        if (x, z) not in top or y > top[(x, z)][0]:
            top[(x, z)] = (y, s)
    S = 3
    im = Image.new('RGB', ((x1 - x0 + 1) * S, (z1 - z0 + 1) * S), (60, 90, 50))
    px = im.load()

    def col(s, y):
        n = s.split('[')[0].replace('minecraft:', '')
        base = (150, 150, 150)
        for k, c in (('deepslate_tile', (60, 64, 72)), ('red_nether', (120, 30, 34)), ('stone_brick', (130, 130, 130)), ('mud_brick', (140, 110, 80)), ('tuff', (110, 112, 104)), ('brick', (160, 80, 60)), ('calcite', (225, 225, 220)),
                     ('quartz', (235, 232, 225)), ('diorite', (200, 200, 198)), ('sandstone', (220, 205, 160)), ('copper', (90, 160, 140)),
                     ('leaves', (50, 110, 50)), ('grass', (80, 130, 60)), ('water', (60, 100, 200)), ('wool', (220, 220, 220)),
                     ('planks', (140, 100, 60)), ('log', (100, 75, 50)), ('stone_brick', (130, 130, 130)), ('andesite', (140, 142, 142)),
                     ('blackstone', (50, 45, 55)), ('deepslate', (75, 75, 80)), ('gravel', (130, 125, 120)), ('dirt', (110, 80, 55))):
            if k in n:
                base = c
                break
        f = 0.75 + min(0.5, max(0, y) / 60.0)
        return tuple(min(255, int(c * f)) for c in base)

    for (x, z), (y, s) in top.items():
        c = col(s, y)
        for i in range(S):
            for j in range(S):
                px[(x - x0) * S + i, (z - z0) * S + j] = c
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    im.save(PREVIEW)


def main():
    key = sys.argv[1] if len(sys.argv) > 1 else 'b'  # 作風は B「灰石と緋の屋根」で決定 (2026-10-21)
    st = vox.STYLES[key]
    t = Town(st)
    v = t.build()
    meta = {'rect': [-140, -120, 120, 120], 'level': True, 'ring': 12, 'fillWater': True, 'height': 60}
    data = v.runs(meta)
    data['site'] = 'capital'
    data['option'] = 'style_' + key
    path = os.path.join(DATA, 'capital.json')
    vox.save(path, data)
    n = sum(1 for s in v.b.values() if s != 'air')
    print('wrote', path, os.path.getsize(path) // 1024, 'KB', n, 'blocks', len(data['palette']), 'states', 'marks', sorted(v.marks))
    preview(v)


if __name__ == '__main__':
    main()
