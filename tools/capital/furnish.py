# -*- coding: utf-8 -*-
"""都の施設の内装 (2026-10-23 の直し: 「もっと違和感ないように」)。facilities.Facilities が継ぐ。

建物の形・窓・戸・梯子は facilities.py / town.py が作ったまま。ここでは部屋の中身を作り直す:
  polish_interiors() が 部屋ごとに fclear (中を空にする。梯子・戸・梁は残す) → 家具 → fautolight (暗い所に灯りを足す)。
- 机 = 板の上付き半ブロック (tidy_for_walkers が感圧板の机をこの形に直すので、最初からこの形)。
- 椅子 = 床の階段。背を机と反対に向ける (chair(x, y, z, toward): toward = 机の向き)。
- 吊り灯り = 天井から鎖で。置いた灯りは必ず何かの上。窓 (y+2 あたりの板ガラス) の前に背の高い物を置かない (fwin)。
- 戸から受付・奥の仕事場まで、足と頭の 2 マスを空けた道を通す。
都のほかの部分の乱数は動かさない (家具は frng() だけを使う)。
"""
from __future__ import annotations

import audit_interiors as ai
from interior_render import base_name
from vox import DX, OPP

AIRS = ('air', 'cave_air')


def _prop_fence(w, n=False, e=False, s=False, ww=False):
    return '%s_fence[east=%s,north=%s,south=%s,waterlogged=false,west=%s]' % (w, str(e).lower(), str(n).lower(), str(s).lower(), str(ww).lower())


class Furnish:
    # ------------------------------------------------------------ 状態の文字列
    def sl(self, top=False):
        return '%s_slab[type=%s,waterlogged=false]' % (self.fw, 'top' if top else 'bottom')

    def stairs(self, facing, half='bottom', wood=None):
        return '%s_stairs[facing=%s,half=%s,shape=straight,waterlogged=false]' % (wood or self.fw, facing, half)

    @staticmethod
    def barrel(f='up'):
        return 'barrel[facing=%s,open=false]' % f

    @staticmethod
    def chest(f):
        return 'chest[facing=%s,type=single,waterlogged=false]' % f

    LANT = 'lantern[hanging=false,waterlogged=false]'
    LANTH = 'lantern[hanging=true,waterlogged=false]'
    CHAIN = 'chain[axis=y,waterlogged=false]'

    @staticmethod
    def candle(n=1, lit=True):
        return 'candle[candles=%d,lit=%s,waterlogged=false]' % (n, 'true' if lit else 'false')

    @staticmethod
    def pot(f='south'):
        return 'decorated_pot[cracked=false,facing=%s,waterlogged=false]' % f

    @staticmethod
    def shelf_full(f, mask=(1, 1, 0, 1, 1, 1)):
        return 'chiseled_bookshelf[facing=%s,slot_0_occupied=%s,slot_1_occupied=%s,slot_2_occupied=%s,slot_3_occupied=%s,slot_4_occupied=%s,slot_5_occupied=%s]' % (
            (f,) + tuple('true' if m else 'false' for m in mask))

    def fence(self, **k):
        return _prop_fence(self.fw, **k)

    # ------------------------------------------------------------ 置く
    def fput(self, x, y, z, s):
        self.v.set(x, y, z, s)

    def fcol(self, x, z, states, y=0):
        """x, z の一列に下から上へ。None は置かない (空気にしない)"""
        for i, s in enumerate(states):
            if s is not None:
                self.v.set(x, y + i, z, s)

    def fline(self, x0, z0, x1, z1, y, states, cycle=True):
        """線に沿って states (状態の一覧) を順に置く。states が文字列ならずっとそれ"""
        if isinstance(states, str):
            states = [states]
        dx = (x1 > x0) - (x1 < x0)
        dz = (z1 > z0) - (z1 < z0)
        n = max(abs(x1 - x0), abs(z1 - z0)) + 1
        for i in range(n):
            s = states[i % len(states)] if cycle else states[min(i, len(states) - 1)]
            if s is not None:
                self.v.set(x0 + dx * i, y, z0 + dz * i, s)

    def chair(self, x, y, z, toward, wood=None):
        """椅子: 机 (toward の側) を向いて座る。階段の背は向きと反対"""
        self.v.set(x, y, z, self.stairs(OPP[toward], 'bottom', wood))

    def ftable(self, cells, y=0, tops=None):
        """机: 板の上付き半ブロック。tops = {(x, z): 上に置く物}"""
        for (x, z) in cells:
            self.v.set(x, y, z, self.sl(True))
        for (x, z), s in (tops or {}).items():
            self.v.set(x, y + 1, z, s)

    def table_set(self, x0, z0, x1, z1, y=0, tops=None, chairs='both'):
        """長方形の机 (x0..x1, z0..z1) とまわりの椅子。chairs: 'both' (長い辺の両側) / 'ends' / None"""
        cells = [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)]
        self.ftable(cells, y, tops)
        if chairs:
            for x in range(x0, x1 + 1):
                self.chair(x, y, z0 - 1, 'south')
                self.chair(x, y, z1 + 1, 'north')
            if chairs == 'ends':
                pass
        return cells

    def ceil_at(self, x, z, y0, ymax):
        """y0 から上へ、最初の空気でないブロックの y (灯りや鎖でなく、丸ごとか梁や板)。無ければ None"""
        for yy in range(y0, ymax + 1):
            t = self.v.get(x, yy, z)
            if t is None or t in AIRS:
                continue
            n = base_name(t)
            if any(k in n for k in ('lantern', 'chain', 'candle', 'carpet', 'potted', 'flower', 'pot')):
                return None
            return yy
        return None

    def hang(self, x, z, y, h, drop=3, s=None):
        """鎖で吊る灯り (灯りは床の上 drop の高さ)。鎖は上の最初のブロック (梁か天井) まで。届かなければ置かない"""
        ly = y + drop
        c = self.ceil_at(x, z, ly + 1, y + h + 2)
        if c is None:
            return False
        for yy in range(ly + 1, c):
            self.v.set(x, yy, z, self.CHAIN)
        self.v.set(x, ly, z, s or self.LANTH)
        return True

    def chandelier(self, x, z, y, h, drop=3):
        """車輪の吊り灯り: 鎖の先に柵の十字、四つの腕の先に灯り"""
        v = self.v
        top = y + h - 1
        for yy in range(y + drop + 1, top + 1):
            v.set(x, yy, z, self.CHAIN)
        v.set(x, y + drop, z, self.fence(n=True, e=True, s=True, ww=True))
        for (d, a) in (('north', dict(s=True)), ('south', dict(n=True)), ('east', dict(ww=True)), ('west', dict(e=True))):
            dx, dz = DX[d]
            v.set(x + dx, y + drop, z + dz, self.fence(**a))
            v.set(x + dx, y + drop + 1, z + dz, self.LANT)

    def fwin(self, x, z):
        """(x, z) の隣の壁 (部屋の外側) に窓 (板ガラス) があるか"""
        x0, z0, x1, z1, y, h = self._fr
        for (a, b) in ((x - 1, z), (x + 1, z), (x, z - 1), (x, z + 1)):
            if x0 <= a <= x1 and z0 <= b <= z1:
                continue
            for yy in range(y, y + h):
                t = self.v.get(a, yy, b) or ''
                if 'pane' in t or 'glass' in t:
                    return True
        return False

    def fclear(self, rect, y, h, keep=()):
        """部屋の中 (rect) の y..y+h-2 を空にする。残すもの: keep の列、梯子とその前の一マス、床の穴 (階段・梯子の穴) とそのまわりの柵、梁 (y+h-1)"""
        x0, z0, x1, z1 = rect
        self._fr = (x0, z0, x1, z1, y, h)
        v = self.v
        ladders = {}
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                for yy in range(y - 1, y + h + 2):
                    s = v.get(x, yy, z) or ''
                    if s.startswith('ladder'):
                        ladders[(x, z)] = s
        keepc = set(keep)
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (v.get(x, y - 1, z) or 'air') in ('air', 'cave_air'):
                    keepc.add((x, z))
                    for (a, b) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        if x0 <= x + a <= x1 and z0 <= z + b <= z1 and 'fence' in (v.get(x + a, y, z + b) or ''):
                            keepc.add((x + a, z + b))
        hold = set()
        for (lx, lz), s in ladders.items():
            f = s.split('facing=')[1].split(',')[0].split(']')[0]
            hold.add((lx, lz))
            hold.add((lx + DX[f][0], lz + DX[f][1]))
        self._hold = hold | keepc
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x, z) in keepc or (x, z) in ladders:
                    continue
                for yy in range(y, y + h - 1):
                    v.set(x, yy, z, self.air(yy))
                s = v.get(x, y + h - 1, z) or ''
                if 'chain' in s or 'lantern' in s:
                    v.set(x, y + h - 1, z, self.air(y + h - 1))

    def fence_line(self, cells, y=0, gates=()):
        """柵の仕切り: cells をつなげた形 (隣どうしがつながる)。gates = {(x, z): facing} は柵の戸"""
        cs = set(cells) | set(gates)
        for (x, z) in cs:
            if (x, z) in gates:
                self.v.set(x, y, z, '%s_fence_gate[facing=%s,in_wall=false,open=false,powered=false]' % (self.fw, gates[(x, z)]))
            else:
                self.v.set(x, y, z, self.fence(n=(x, z - 1) in cs, s=(x, z + 1) in cs, e=(x + 1, z) in cs, ww=(x - 1, z) in cs))

    def fdoor_cells(self):
        """部屋の戸 (戸の内側の一マス) の一覧"""
        x0, z0, x1, z1, y, h = self._fr
        out = []
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                s = self.v.get(x, y, z) or ''
                if '_door[' in s and 'half=lower' in s:
                    out.append((x, z))
        return out

    def frug(self, cells, color, y=0):
        for (x, z) in cells:
            if self.v.get(x, y, z) in (None, 'air', 'cave_air') and self.solid_at(x, y - 1, z):
                self.v.set(x, y, z, '%s_carpet' % color)

    def frug_rect(self, x0, z0, x1, z1, color, y=0, border=None):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = border and (x in (x0, x1) or z in (z0, z1))
                if self.v.get(x, y, z) in (None, 'air', 'cave_air') and self.solid_at(x, y - 1, z):
                    self.v.set(x, y, z, '%s_carpet' % (border if edge else color))

    def fautolight(self, rect, y, h, drop=None, tries=300):
        """立てる床で明るさが 8 より低い所に、天井から吊る灯りを足す (家具の上に吊って、通り道は空ける)"""
        v = self.v
        x0, z0, x1, z1 = rect
        drop = drop if drop is not None else (3 if h >= 5 else 2)
        added = 0
        for _ in range(tries):
            box = (x0 - 10, x1 + 10, y - 2, y + h + 1, z0 - 10, z1 + 10)
            L = ai.light_map(v.b, box)
            cells = ai.stand_cells(v.b, rect, y, h)
            dark = [(x, z) for (x, z) in cells if L.get((x, y, z), 0) < 8]
            if not dark:
                break
            # 暗い所が最も多く入る場所に灯りを置く (そこから水平の距離が (15 - 8 - drop) 以内)
            r = 7 - drop
            best = None
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if (x, z) in self._hold:
                        continue
                    c = self.ceil_at(x, z, y + drop + 1, y + h + 2)
                    ok = c is not None and all((v.get(x, yy, z) in (None, 'air', 'cave_air')) for yy in range(y + drop, c))
                    if not ok:
                        continue
                    n = sum(1 for (a, b) in dark if abs(a - x) + abs(b - z) <= r)
                    # 窓や壁ぎわを少し避け、鎖の上端の天井が丸ごとなことを確かめる
                    if n and (best is None or n > best[0]):
                        best = (n, x, z)
            if not best:
                break
            self.hang(best[1], best[2], y, h, drop)
            added += 1
        return added

    def flight(self, rect, y, h, drop=None):
        """部屋を作り直さずに明るさだけ足す"""
        x0, z0, x1, z1 = rect
        self._fr = (x0, z0, x1, z1, y, h)
        self._hold = set()
        return self.fautolight(rect, y, h, drop)

    def fring_thin(self, rect, y, h):
        """壁ぎわの輪 (room() の棚: 樽・本棚・鉢が全部の壁に並ぶ) を間引く: 鉢と上の小物を消し、3 つ置きに 3 つ消す"""
        x0, z0, x1, z1 = rect
        v = self.v
        ring = [(x, z0) for x in range(x0, x1 + 1)] + [(x1, z) for z in range(z0 + 1, z1 + 1)] +                [(x, z1) for x in range(x1 - 1, x0 - 1, -1)] + [(x0, z) for z in range(z1 - 1, z0, -1)]
        small = ('potted_', 'decorated_pot', 'candle', 'flower_pot', 'cake')
        for i, (x, z) in enumerate(ring):
            for yy in range(y, y + 3):
                t = v.get(x, yy, z) or ''
                n = base_name(t)
                if any(k in n for k in small) or (n == 'lantern' and 'hanging=false' in t):
                    v.set(x, yy, z, self.air(yy))
            if (i // 3) % 2 == 1:
                for yy in range(y, y + 3):
                    t = v.get(x, yy, z) or ''
                    if base_name(t) in ('bookshelf', 'barrel', 'chiseled_bookshelf'):
                        v.set(x, yy, z, self.air(yy))

    def fpost_light(self, rect, y, h, tries=300):
        """高い広間 (社) の灯り: 壁や柱に寄せた石の灯籠 (石煉瓦の壁の上に灯り) を、暗い所が減るまで足す"""
        v = self.v
        x0, z0, x1, z1 = rect
        self._fr = (x0, z0, x1, z1, y, h)
        self._hold = set()
        added = 0
        avoid = set()
        for nm, (mx, my, mz) in v.marks.items():
            if abs(my - y) <= 1:
                for a in range(-2, 3):
                    for b in range(-2, 3):
                        avoid.add((mx + a, mz + b))
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                if '_door[' in (v.get(x, y, z) or ''):
                    for a in range(-2, 3):
                        for b in range(-2, 3):
                            avoid.add((x + a, z + b))
        for _ in range(tries):
            box = (x0 - 10, x1 + 10, y - 2, y + h + 1, z0 - 10, z1 + 10)
            L = ai.light_map(v.b, box)
            cells = ai.stand_cells(v.b, rect, y, h)
            dark = [(x, z) for (x, z) in cells if L.get((x, y, z), 0) < 8]
            if not dark:
                break
            best = None
            for (x, z) in cells:
                if (x, z) in avoid:
                    continue
                if (v.get(x, y, z) or 'air') not in AIRS or (v.get(x, y + 1, z) or 'air') not in AIRS:
                    continue
                wall = any(self.solid_at(x + a, y, z + b) for (a, b) in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                n = sum(1 for (a, b) in dark if abs(a - x) + abs(b - z) <= 6)
                n = n * (4 if wall else 1)          # 壁や柱ぎわを先に。足りなければ床の上にも
                if n and (best is None or n > best[0]):
                    best = (n, x, z)
            if not best:
                break
            _, x, z = best
            v.set(x, y, z, self.st.railing)
            v.set(x, y + 1, z, self.LANT)
            added += 1
        return added

    # ------------------------------------------------------------ 受付の机
    def fcounter(self, cells, y=0, tops=None, front=None):
        """受付の机: 板の箱 (丸太の剥いだ柱を等間隔に)、前の面に腰の高さの飾り。tops = {(x, z): 上の物}"""
        w = self.fw
        for i, (x, z) in enumerate(cells):
            self.v.set(x, y, z, 'stripped_%s_log[axis=y]' % w if i % 3 == 0 else '%s_planks' % w)
            self.v.set(x, y + 1, z, 'air')
        for (x, z), s in (tops or {}).items():
            self.v.set(x, y + 1, z, s)

    def fgate(self, x, y, z, axis_facing, wood=None):
        """机の端の跳ね上げ (柵の戸、閉)。axis_facing = 戸の向き (向きは通る方向に垂直)"""
        self.v.set(x, y, z, '%s_fence_gate[facing=%s,in_wall=false,open=false,powered=false]' % (wood or self.fw, axis_facing))

    def npc(self, role, x, y, z):
        self.attendant(role, x, y, z)

    # ------------------------------------------------------------ 仕上げの灯り (town.build の最後、小物の整理のあと)
    LIGHT_DROP = {'smithy': 4}

    def polish_final(self):
        import interior_render as ir
        self.fw = self.st.wood
        saved = self.rng
        self.rng = self.frng()
        try:
            for (nm, rect, y, h, mode) in ir.ROOMS:
                if nm == 'shrine':
                    self.fpost_light(rect, y, h)
                else:
                    self.flight(rect, y, h, self.LIGHT_DROP.get(nm))
            for (name, rect, y, names, rugc) in self.UPPER:
                self.flight(rect, y, 5 if y == 7 else 4)
            for (name, rect, y, h) in getattr(self, 'home_rooms', []):
                self.flight(rect, y, h)
        finally:
            self.rng = saved

    # ------------------------------------------------------------ 本体
    def polish_interiors(self):
        self.fw = self.st.wood
        saved = self.rng
        self.rng = self.frng()
        try:
            for fn in sorted(n for n in dir(self) if n.startswith('fx_')):
                getattr(self, fn)()
        finally:
            self.rng = saved

    # ======================================================================= 道具屋
    def fx_tool_shop(self):
        v, w = self.v, self.fw
        rect = (35, -36, 43, -30)
        self.fclear(rect, 0, 6)
        # 売り台 x=40 (北から南まで)、南の端は柵の戸 (番が出入りする)
        cells = [(40, z) for z in range(-36, -30)]
        self.fcounter(cells, tops={(40, -36): self.LANT, (40, -34): self.candle(2), (40, -32): self.pot('west'), (40, -35): 'potted_fern'})
        self.fgate(40, 0, -30, 'east')
        # 番の側 (x 41..43): 奥の壁 (x 43) は道具の棚。窓の前は低く
        back = [
            (-36, ['fletching_table', self.LANT]),
            (-35, [self.barrel('west'), self.barrel('west'), 'potted_fern']),
            (-34, [self.chest('west'), self.pot('west')]),
            (-33, ['bookshelf', 'bookshelf', 'bookshelf'] if not self.fwin(43, -33) else ['bookshelf', 'bookshelf']),
            (-32, ['crafting_table', self.candle(3)]),
            (-31, [self.barrel('west'), self.chest('west'), None]),
            (-30, ['grindstone[face=floor,facing=west]', None]),
        ]
        for z, st_ in back:
            self.fcol(43, z, st_)
        # 番の足もと (x 41..42)。吊るした道具の見本 (鎖の先の灯り) は売り台の上
        self.npc('tool_merchant', 42, 0, -36)  # 市の四人の列 (x 42、2 おき) の北の端 (2026-10-28 の試遊「道具屋が前に出て横並びになってない」)
        # 客の側 (x 35..39)
        # 戸 (34, -32) の内側は空け、真ん中の通り (z -33..-31) を売り台の前まで通す
        # 北の壁 (z -36): 売り物の樽・箱・干し糧。窓は y2 から
        self.fline(35, -36, 39, -36, 0, [self.barrel('up'), self.chest('south'), 'hay_block[axis=y]', self.barrel('up'), 'crafting_table'])
        self.fline(35, -36, 39, -36, 1, [self.candle(2), None, None, self.LANT, None])
        # 南の壁 (z -30)
        self.fline(35, -30, 39, -30, 0, [self.barrel('up'), 'white_wool', self.barrel('up'), 'hay_block[axis=y]', 'smithing_table'])
        self.fline(35, -30, 39, -30, 1, [None, 'potted_azalea_bush', None, None, None])
        # 陳列の台 (半ブロック) に品
        self.ftable([(37, -35), (38, -35)], tops={(37, -35): self.LANT, (38, -35): self.pot('south')})
        self.ftable([(37, -31), (38, -31)], tops={(37, -31): self.candle(3), (38, -31): 'potted_red_tulip'})
        # 戸の北の腰掛け (客が待つ)
        self.fput(35, 0, -34, self.stairs('west'))
        self.fput(35, 0, -35, self.stairs('west'))
        # 敷物
        self.frug_rect(36, -34, 39, -32, 'brown', border='red')
        # 灯り: 売り台の上に吊る (道具屋の見本のように 2 つ)
        self.hang(40, -35, 0, 6, drop=3)
        self.hang(40, -32, 0, 6, drop=3)
        self.hang(37, -33, 0, 6, drop=3)
        self.fautolight(rect, 0, 6)
