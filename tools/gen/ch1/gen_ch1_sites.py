#!/usr/bin/env python3
"""一章の地上の新しい場所 (docs/design/21 の 3.1、P6) の部品を data/skyabyss/opening/ に書き出す。

  python tools/gen/ch1/gen_ch1_sites.py

形式は tools/gen/ch1/gen_ch1_blueprints.py と同じ (palette / blocks / marks / meta)。座標は部品の中心が (0, 0)、y=0 が最初の段 (地面の上面は y=-1)。
向きは SiteJob が回す・反転するので、向きのある物は北向きで書く。バニラのブロックだけ。

書く部品:
  ch1_road_cairn      章の道しるべの石 (低い積み石と、空の下を指す石の矢印)。今は道には並べない (2026-10-24 にやめた。SiteJob の試験が部品として使う)
  ch1_road_shrine     鯨道の祠: 道の終わりの祠 (一章の道の石はこれ一つ)
  ch1_bone_cache      骨の集め場 (骨の山と骨負いの籠)
  ch1_cart_track      荷車の傍らの道 (轍と、旅の職人の荷の置き場。失せ物の荷車の建て終わりに SiteJob.placeSmall で隣に置く)
  ch1_cleft_shell_cave 割れ殻の洞 (地表の口 + 地下 3 部屋 + 鉄格子の戸 + 三つの鳴り石)
  ch1_song_chamber    歌の間 (鐘の下。部品の座標は鐘を (0, 0, 0) とする。story.ch1.SongChamber が宿りの向きに合わせて置く)
  ch1_bone_pit        骨の坑 (34 の 4.2.1。坑口の肋骨と碑 + 地下 3 部屋 (坑口の間・水の底・荷の間) + 冷えの戸の奥 + 出口の縦穴)
"""
import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_ch1_blueprints import BP, OUT, CHEST, COBBLE, MOSSY  # noqa: E402

SB = 'minecraft:stone_bricks'
MSB = 'minecraft:mossy_stone_bricks'
CSB = 'minecraft:cracked_stone_bricks'
AIR = 'minecraft:air'
BARS = 'minecraft:iron_bars[east=false,north=false,south=false,west=false,waterlogged=false]'


def wall_torch(facing):
    return 'minecraft:wall_torch[facing=%s]' % facing


def stair(facing, half='bottom', block='stone_brick_stairs'):
    return 'minecraft:%s[facing=%s,half=%s,shape=straight,waterlogged=false]' % (block, facing, half)


def candles(n, lit=False):
    return 'minecraft:candle[candles=%d,lit=%s,waterlogged=false]' % (n, 'true' if lit else 'false')


def rough(rng):
    r = rng.random()
    return SB if r < 0.55 else MSB if r < 0.85 else CSB


# ---------------------------------------------------------------- 鯨道の石標
def road_cairn():
    bp = BP('ch1_road_cairn', [-2, -2, 2, 2], ring=1)
    # 低い積み石 (丸石と苔むした丸石)
    for i in range(3):
        bp.set(0, i, 0, COBBLE if i % 2 == 0 else MOSSY)
    bp.set(0, 3, 0, 'minecraft:cobblestone_slab[type=bottom]')
    # 空の下を指す石の矢印 (北向き)。幹と二枚の羽
    bp.set(0, 0, -1, MOSSY)
    bp.set(0, 0, -2, COBBLE)
    bp.set(-1, 0, -1, 'minecraft:cobblestone_slab[type=bottom]')
    bp.set(1, 0, -1, 'minecraft:cobblestone_slab[type=bottom]')
    bp.mark('stand', 0, 0, 2)
    return bp


def road_shrine():
    bp = BP('ch1_road_shrine', [-3, -3, 3, 3], ring=2)
    # 道の終わりの祠: 四隅の柱と低い屋根、奥の壁龕に消えた蝋燭
    bp.fill(-2, -1, -2, 2, -1, 2, SB)
    for x in (-2, 2):
        for z in (-2, 1):
            for y in range(0, 3):
                bp.set(x, y, z, MSB if y == 1 else SB)
    bp.fill(-2, 3, -2, 2, 3, 1, 'minecraft:stone_brick_slab[type=bottom]')
    for x in range(-1, 2):
        bp.set(x, 0, -2, SB)
        bp.set(x, 1, -2, SB)
        bp.set(x, 2, -2, SB)
    bp.set(0, 0, -2, 'minecraft:chiseled_stone_bricks')
    bp.set(0, 1, -2, AIR)
    bp.set(0, 1, -1, candles(1))
    bp.set(0, 0, -1, MOSSY)
    bp.cairn(-3, 2, 3)
    bp.mark('stand', 0, 0, 3)
    return bp


# ---------------------------------------------------------------- 骨の集め場
def bone_cache():
    bp = BP('ch1_bone_cache', [-4, -4, 4, 4], ring=2)
    rng = random.Random(1101)
    for x in range(-3, 4):
        for z in range(-3, 4):
            d = x * x + z * z
            if d <= 4:
                bp.set(x, 0, z, 'minecraft:bone_block[axis=%s]' % rng.choice('xyz'))
                if d <= 1:
                    bp.set(x, 1, z, 'minecraft:bone_block[axis=%s]' % rng.choice('xz'))
            elif d <= 10 and rng.random() < 0.35:
                bp.set(x, 0, z, 'minecraft:bone_block[axis=%s]' % rng.choice('xz'))
    bp.set(0, 2, 0, 'minecraft:bone_block[axis=y]')
    # 骨負いの籠 (落とした荷)
    bp.set(3, 0, 0, 'minecraft:composter[level=4]')
    bp.set(3, 0, 1, 'minecraft:hay_block[axis=x]')
    bp.set(-3, 0, -2, 'minecraft:skeleton_skull[rotation=4]')
    bp.cairn(-3, 3, 2)
    bp.mark('stand', 0, 0, 4)
    return bp


# ---------------------------------------------------------------- 荷車の傍らの道
def cart_track():
    bp = BP('ch1_cart_track', [-4, -3, 4, 3], ring=1)
    # 轍: 二本の砂利と土の筋
    for z in range(-3, 4):
        bp.set(-1, -1, z, 'minecraft:dirt_path' if z % 2 else 'minecraft:coarse_dirt')
        bp.set(1, -1, z, 'minecraft:coarse_dirt' if z % 2 else 'minecraft:dirt_path')
    # 職人の荷: 樽と布と小さな荷台 (人は P4 が立たせる。mark artisan が立つ所)
    bp.set(0, 0, -2, 'minecraft:barrel[facing=up,open=false]')
    bp.set(-1, 0, -2, 'minecraft:barrel[facing=east,open=false]')
    bp.set(1, 0, -2, 'minecraft:gray_carpet')
    bp.set(2, 0, -2, 'minecraft:anvil[facing=east]')
    bp.set(-2, 0, -1, 'minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]')
    bp.set(-2, 0, 0, 'minecraft:spruce_fence[east=false,north=false,south=false,west=false,waterlogged=false]')
    bp.set(2, 0, 1, 'minecraft:lantern[hanging=false,waterlogged=false]')
    bp.mark('artisan', 0, 0, 0)
    bp.mark('sign', 0, 0, 0)
    bp.mark('stand', 0, 0, 2)
    return bp


# ---------------------------------------------------------------- 割れ殻の洞
class Cave:
    """地下 3 部屋。floor = 床のブロックの y。interior = 空気にする箱、shell = その 1 まわりを石レンガで包む。"""

    def __init__(self):
        self.bp = BP('ch1_cleft_shell_cave', [-4, -3, 4, 4], ring=3)
        self.rng = random.Random(2203)
        self.air = set()
        self.shell = set()

    def box(self, x0, y0, z0, x1, y1, z1):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.air.add((x, y, z))

    def build(self):
        bp = self.bp
        for (x, y, z) in list(self.air):
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                p = (x + dx, y + dy, z + dz)
                if p not in self.air:
                    self.shell.add(p)
        for p in sorted(self.shell):
            bp.set(p[0], p[1], p[2], rough(self.rng))
        for p in sorted(self.air):
            bp.set(p[0], p[1], p[2], AIR)


def cleft_shell_cave():
    c = Cave()
    bp = c.bp
    FL = -16  # 床のブロック
    # 階段 (z 0..14、一段ずつ下がる。幅 3)
    for i in range(0, 15):
        c.box(-1, -i, i, 1, -i + 2, i)
    # 入口の間 (x -5..5, z 15..25)
    c.box(-5, FL + 1, 15, 5, FL + 4, 25)
    # 回廊 1 (入口の間 -> 棘の間。x 6..10, z 19..21)
    c.box(6, FL + 1, 19, 10, FL + 3, 21)
    # 棘の間 (x 11..23, z 14..28)
    c.box(11, FL + 1, 14, 23, FL + 4, 28)
    # 回廊 2 (棘の間 -> 奥の間。x 16..18, z 8..13。z=10 に鉄格子の戸)
    c.box(16, FL + 1, 8, 18, FL + 3, 13)
    # 奥の間 (x 11..25, z -7..7)
    c.box(11, FL + 1, -7, 25, FL + 5, 7)
    c.build()
    # 階段の段 (空気を上書きして段を置く) と、口の両脇の壁・柱
    for i in range(0, 15):
        for x in (-1, 0, 1):
            bp.set(x, -1 - i, i, stair('north'))  # facing = 登る向き (背の高い側)。口 (z 0) へ登るので北
        # 両脇の壁。口 (z 0..3) は低い壁にして、空へ開く
        top = 1 if i <= 3 else -i + 3
        for x in (-2, 2):
            for y in range(-1 - i, top + 1):
                bp.set(x, y, i, MSB if (i + y) % 3 == 0 else SB)
    # 階段の天井 (口が空に開いているのは z 0..3)
    for i in range(4, 15):
        for x in (-1, 0, 1):
            bp.set(x, -i + 3, i, SB)
    # 口の門: 二本の柱と楣 (z = -1)
    for x in (-2, 2):
        for y in range(0, 3):
            bp.set(x, y, -1, SB if y != 1 else MSB)
    for x in range(-2, 3):
        bp.set(x, 3, -1, 'minecraft:mossy_stone_brick_slab[type=bottom]')
    bp.set(0, 3, -1, 'minecraft:chiseled_stone_bricks')
    bp.cairn(-4, -1, 2)
    bp.mark('stand', 0, 0, -3)
    bp.mark('mouth', 0, 0, 0)

    # ---- 床と飾り
    def floor(x0, z0, x1, z1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                bp.set(x, FL, z, SB if (x + z) % 5 else MSB)
    floor(-5, 14, 5, 26)
    floor(6, 18, 10, 22)
    floor(10, 13, 24, 29)
    floor(15, 7, 19, 14)
    floor(10, -8, 26, 8)
    # 入口の間: 柱 4 本と壁の松明。琥珀の目の傷 (苔の石)
    for x, z in ((-3, 17), (3, 17), (-3, 23), (3, 23)):
        for y in range(FL + 1, FL + 5):
            bp.set(x, y, z, MSB if y % 2 else SB)
    bp.set(-5 + 0, FL + 2, 20, wall_torch('east'))
    bp.set(5, FL + 2, 20, wall_torch('west'))
    bp.set(0, FL + 2, 25, wall_torch('north'))
    # 棘の間: 遮蔽の石柱 (1x1 を高く) と奥の壁の松明
    for x, z in ((14, 17), (14, 25), (18, 21), (21, 17), (21, 25)):
        for y in range(FL + 1, FL + 5):
            bp.set(x, y, z, SB if y % 2 else CSB)
    bp.set(12, FL + 2, 14, wall_torch('south'))
    bp.set(22, FL + 2, 14, wall_torch('south'))
    bp.set(11, FL + 2, 27, wall_torch('east'))
    bp.set(23, FL + 2, 16, wall_torch('west'))
    bp.set(11, FL + 2, 21, wall_torch('east'))
    bp.set(23, FL + 2, 27, wall_torch('west'))
    bp.set(18, FL + 2, 28, wall_torch('north'))
    bp.set(-5, FL + 2, 16, wall_torch('east'))
    bp.set(5, FL + 2, 24, wall_torch('west'))
    # 階段の途中の松明 (暗い所を歩く導き)
    for i in (6, 10, 13):
        bp.set(-1, -i + 1, i, wall_torch('east'))
    # 鳴り石 3 つ (石の台。上のろうそくの数が叩く順)。棘の間の東の壁ぎわ
    for n, z in ((1, 19), (2, 21), (3, 23)):
        bp.set(22, FL + 1, z, 'minecraft:chiseled_stone_bricks')
        bp.set(22, FL + 2, z, candles(n))
        bp.mark('plate%d' % n, 22, FL + 1, z)
    # 戸 (回廊 2、z = 10): 鉄格子の 3x3。印 gate0..8
    k = 0
    for y in range(FL + 1, FL + 4):
        for x in (16, 17, 18):
            bp.set(x, y, 10, BARS)
            bp.mark('gate%d' % k, x, y, 10)
            k += 1
    # 奥の間: 台座 (歩哨の割符の跡) と柱。琥珀の目の傷、天井近くの松明
    for x, z in ((13, -5), (23, -5), (13, 5), (23, 5)):
        for y in range(FL + 1, FL + 6):
            bp.set(x, y, z, MSB if y % 2 else SB)
    bp.set(18, FL + 1, -6, 'minecraft:chiseled_stone_bricks')
    bp.set(18, FL + 2, -6, candles(3, True))
    bp.mark('plinth', 18, FL + 2, -6)
    bp.set(11, FL + 3, 0, wall_torch('east'))
    bp.set(25, FL + 3, 0, wall_torch('west'))
    bp.set(18, FL + 3, 7, wall_torch('north'))
    bp.set(11, FL + 3, -4, wall_torch('east'))
    bp.set(25, FL + 3, 4, wall_torch('west'))
    bp.set(18, FL + 3, -7, wall_torch('south'))
    bp.set(15, FL + 3, 7, wall_torch('north'))
    # 休息の印 (奥の間の出口側): 床の光る石は P9 の既知の道の仕組みでは扱わない (洞は周回しない)
    # 部屋の中心 (湧き台の基点計算用の印)
    bp.mark('room1', 0, FL + 1, 20)
    bp.mark('room2', 17, FL + 1, 21)
    bp.mark('room3', 18, FL + 1, 0)
    return bp


# ---------------------------------------------------------------- 歌の間 (鐘の下)
def song_chamber():
    """鐘 (部品の (0,0,0)) の真下の部屋。地面は y=-1 (鐘は地面から 3 段上に吊られている = 鐘の y は +2 だが、ここでは鐘の下の地面を y=0 と見なす)。"""
    bp = BP('ch1_song_chamber', [-3, -3, 3, 3], ring=0, level=False)
    rng = random.Random(3307)
    FL = -6
    air = set()
    for x in range(-3, 4):
        for y in range(FL + 1, FL + 5):
            for z in range(-3, 4):
                air.add((x, y, z))
    # 縦穴 (戸から下りる。x=3, z=0)
    for y in range(FL + 1, -1):
        air.add((4, y, 0))
    shell = set()
    for (x, y, z) in air:
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            p = (x + dx, y + dy, z + dz)
            if p not in air:
                shell.add(p)
    for p in sorted(shell):
        if p[1] >= -1:
            continue  # 地面 (天井) は自然のまま
        bp.set(p[0], p[1], p[2], rough(rng))
    for p in sorted(air):
        bp.set(p[0], p[1], p[2], AIR)
    # 縦穴の梯子。戸 = 地面と同じ高さの石レンガ 1 枚 (開くと梯子になる。mark door)
    for y in range(FL + 1, -1):
        bp.set(4, y, 0, 'minecraft:ladder[facing=west,waterlogged=false]')
    for y in range(FL + 1, 0):
        bp.set(5, y, 0, SB)
    bp.set(4, -1, 0, SB)
    bp.mark('door', 4, -1, 0)
    # 床
    for x in range(-3, 4):
        for z in range(-3, 4):
            bp.set(x, FL, z, 'minecraft:polished_andesite' if (x + z) % 2 == 0 else SB)
    bp.set(4, FL, 0, SB)
    # 壁画: 北の壁 (z=-3) に 5 つの柱 (高さ 1..5。数え歌の順)。上に紫水晶
    for i, x in enumerate((-2, -1, 0, 1, 2)):
        h = i + 1
        for y in range(FL + 1, FL + 1 + h):
            bp.set(x, y, -3, 'minecraft:chiseled_stone_bricks' if y < FL + h else 'minecraft:amethyst_block')
    bp.mark('mural', 0, FL + 1, -3)
    # 伝承の書見台 (西の壁ぎわ)。読む人ごとの伝承は Java が与える
    bp.set(-3, FL + 1, 1, 'minecraft:lectern[facing=east,has_book=false,powered=false]')
    bp.mark('lectern', -3, FL + 1, 1)
    bp.set(-3, FL + 3, -2, wall_torch('east'))
    bp.set(3, FL + 3, 2, wall_torch('west'))
    bp.set(3, FL + 3, -2, wall_torch('west'))
    bp.mark('floor', 0, FL, 0)
    return bp


# ---------------------------------------------------------------- 骨の坑 (34 の 4.2.1)
class Pit:
    """地下の部屋。inside = 中身 (空気か水) の箱、その 1 まわりを荒い石レンガで包む (地面から上の殻は keep が真の所だけ)。"""

    def __init__(self):
        self.bp = BP('ch1_bone_pit', [-12, -6, 6, 6], ring=3)
        self.rng = random.Random(3411)
        self.inside = {}

    def box(self, x0, y0, z0, x1, y1, z1, state=AIR):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.inside[(x, y, z)] = state

    def build(self, keep):
        bp = self.bp
        shell = set()
        for (x, y, z) in self.inside:
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                q = (x + dx, y + dy, z + dz)
                if q not in self.inside:
                    shell.add(q)
        shell = {q for q in shell if q[1] < -1 or keep(q)}
        for q in sorted(shell):
            bp.set(q[0], q[1], q[2], rough(self.rng))
        for q, st in sorted(self.inside.items()):
            bp.set(q[0], q[1], q[2], st)
        return shell


PIT_FL = -13  # 骨の坑の床のブロックの y (敵が立つのは -12)


def bone_pit():
    c = Pit()
    bp = c.bp
    FL = PIT_FL
    WATER = 'minecraft:water[level=0]'
    LOG = 'minecraft:spruce_log[axis=y]'
    PLANK = 'minecraft:spruce_planks'
    # 階段 (z 0..11、一段ずつ下がる。幅 3)
    for i in range(0, 12):
        c.box(-1, -i, i, 1, -i + 2, i)
    # 坑口の間 (x -3..3, z 12..30)。苔這い 3
    c.box(-3, FL + 1, 12, 3, FL + 4, 30)
    # 水の底: 坑口の間の床の井戸 (x -3..-1, z 27..29) → 水の坑道 (x -18..-4) → 巣の間の床の井戸 (x -21..-19)。
    # 水の上面は井戸の口 (床の高さ) と坑道の天井で閉じる。息継ぎは坑道の天井を 2 段上げた所 (x -9..-8 と -14..-13)
    c.box(-3, FL - 3, 27, -1, FL, 29, WATER)
    c.box(-18, FL - 3, 27, -4, FL - 1, 29, WATER)
    c.box(-21, FL - 3, 27, -19, FL, 29, WATER)
    for x0 in (-9, -14):
        c.box(x0, FL, 27, x0 + 1, FL + 1, 29)
    # 巣の間 (x -30..-19, z 20..36)。苔這いの巣
    c.box(-30, FL + 1, 20, -19, FL + 4, 36)
    # 回廊 (巣の間 → 荷の間。x -26..-24, z 6..19)
    c.box(-26, FL + 1, 6, -24, FL + 3, 19)
    # 荷の間 (x -27..-12, z -6..5)。岩殻虫 2。前の私の荷
    c.box(-27, FL + 1, -6, -12, FL + 5, 5)
    # 冷えの戸 (x = -28 の 3x3) と、その奥 (x -33..-29, z -2..2)
    c.box(-28, FL + 1, -1, -28, FL + 3, 1)
    c.box(-33, FL + 1, -2, -29, FL + 3, 2)
    # 出口の縦穴 (荷の間の東の壁の外 x = -11、z = 0。梯子で地上へ。口は跳ね戸)
    c.box(-11, FL + 1, 0, -11, -1, 0)
    # 口の覆い: 階段の上の地上の殻 (z 0..3) だけ残す
    shell = c.build(lambda q: -2 <= q[0] <= 2 and 0 <= q[2] <= 3 and q[1] <= 3)

    # ---- 階段: 段と、口の覆い (木の枠と板の屋根)
    for i in range(0, 12):
        for x in (-1, 0, 1):
            bp.set(x, -1 - i, i, stair('north'))
    for q in shell:
        x, y, z = q
        if -2 <= x <= 2 and 0 <= z <= 3 and y >= 0:
            if abs(x) == 2:
                bp.set(x, y, z, LOG if z in (0, 3) else rough(c.rng))
            else:
                bp.set(x, y, z, PLANK)
    # 口の前の肋骨 (骨のブロックの弧が二つ)。骨の坑の名の元
    for z in (-2, -4):
        for y in range(0, 3):
            bp.set(-3, y, z, 'minecraft:bone_block[axis=y]')
            bp.set(3, y, z, 'minecraft:bone_block[axis=y]')
        bp.set(-2, 3, z, 'minecraft:bone_block[axis=x]')
        bp.set(2, 3, z, 'minecraft:bone_block[axis=x]')
        for x in (-1, 0, 1):
            bp.set(x, 4 if z == -4 else 3, z, 'minecraft:bone_block[axis=x]')
        if z == -4:
            bp.set(-2, 4, z, 'minecraft:bone_block[axis=x]')
            bp.set(2, 4, z, 'minecraft:bone_block[axis=x]')
    # 掘り出した土 (口の両脇)
    for x, z in ((-5, -1), (-5, 1), (-6, 0), (5, 0), (5, 2), (6, 1)):
        bp.set(x, 0, z, 'minecraft:gravel' if (x + z) % 2 else 'minecraft:coarse_dirt')
    # 坑口の碑 (看板の代わり。文は sign.skyabyss.ch1_bone_pit)
    bp.set(4, 0, -3, 'skyabyss:tablet[facing=north,standing=true]')
    bp.mark('tablet', 4, 0, -3)
    bp.cairn(-5, -4, 2)
    bp.mark('stand', 0, 0, -5)
    bp.mark('mouth', 0, 0, 0)

    # ---- 床 (井戸の口は水のまま)
    def floor(x0, z0, x1, z1, state=None):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x, FL, z) in c.inside:
                    continue
                bp.set(x, FL, z, state or (SB if (x * 3 + z) % 5 else MSB))
    floor(-3, 12, 3, 30, 'minecraft:coarse_dirt')
    floor(-30, 20, -19, 36, 'minecraft:moss_block')
    floor(-26, 6, -24, 19)
    floor(-27, -6, -12, 5)
    floor(-33, -2, -29, 2, 'minecraft:packed_ice')
    # 坑口の間: 坑木 (柱と梁)、こわれた線路、崩れた石、松明の残り
    for z in (15, 20, 25):
        for y in range(FL + 1, FL + 4):
            bp.set(-3, y, z, LOG)
            bp.set(3, y, z, LOG)
        for x in range(-3, 4):
            bp.set(x, FL + 4, z, PLANK)
    for z in range(13, 27):
        if z % 6 != 4:
            bp.set(1, FL + 1, z, 'minecraft:rail[shape=north_south,waterlogged=false]')
    bp.set(-2, FL + 1, 18, 'minecraft:cobblestone')
    bp.set(-2, FL + 1, 19, 'minecraft:gravel')
    bp.set(2, FL + 1, 29, 'minecraft:cobblestone')
    bp.set(-2, FL + 2, 13, wall_torch('east'))
    bp.set(2, FL + 2, 23, wall_torch('west'))
    bp.set(0, FL + 2, 30, wall_torch('north'))
    # 鉄鉱石の筋: 坑口の間と巣の間の壁 (掘ってよい)。18 個
    veins = [(-4, FL + 1, 14), (-4, FL + 2, 14), (-4, FL + 2, 17), (-4, FL + 1, 22), (-4, FL + 3, 22), (-4, FL + 2, 23),
             (4, FL + 2, 16), (4, FL + 3, 16), (4, FL + 1, 19), (4, FL + 2, 27), (4, FL + 1, 28), (0, FL + 5, 18),
             (-31, FL + 2, 24), (-31, FL + 1, 25), (-31, FL + 3, 31), (-18, FL + 2, 33), (-18, FL + 1, 34), (-25, FL + 5, 32)]
    for q in veins:
        assert q in shell, q
        bp.set(q[0], q[1], q[2], 'minecraft:iron_ore')
    # 水の底: 井戸の縁の灯りと、坑道の息継ぎの天井の光 (水の中は暗い)
    bp.set(-2, FL + 1, 26, 'minecraft:lantern[hanging=false,waterlogged=false]')
    for x0 in (-9, -14):
        bp.set(x0, FL + 2, 28, 'minecraft:glowstone')
    # 巣の間: 苔と骨と蜘蛛の糸。壁の松明は二本だけ
    for (x, z) in ((-28, 22), (-27, 23), (-22, 34), (-21, 33), (-29, 30), (-24, 25), (-20, 22), (-26, 35)):
        bp.set(x, FL + 1, z, 'minecraft:moss_carpet')
    for (x, z) in ((-29, 34), (-28, 35), (-29, 35)):
        bp.set(x, FL + 1, z, 'minecraft:bone_block[axis=%s]' % c.rng.choice('xz'))
    for (x, y, z) in ((-30, FL + 4, 20), (-19, FL + 4, 36), (-30, FL + 4, 36), (-26, FL + 3, 6)):
        bp.set(x, y, z, 'minecraft:cobweb')
    bp.set(-19, FL + 2, 24, wall_torch('west'))
    bp.set(-30, FL + 2, 28, wall_torch('east'))
    bp.set(-26, FL + 2, 12, wall_torch('east'))
    # 荷の間: 前の私の荷 (樽。触れると人ごとに一度、鉄と松明と頁)、寝床、まだ点いている灯り、柱
    # 荷は一段高い台 (磨いた石) の上。両脇にろうそく、真上に吊り灯り (最後に触れる所を目立たせる。2026-10-21 の試遊)
    bp.set(-20, FL + 1, -5, 'minecraft:polished_andesite')
    bp.set(-20, FL + 2, -5, 'minecraft:barrel[facing=south,open=false]')
    bp.mark('pack', -20, FL + 2, -5)
    bp.set(-20, FL + 1, -6, 'minecraft:polished_andesite_slab[type=bottom,waterlogged=false]')
    bp.set(-20, FL + 1, -4, 'minecraft:polished_andesite_slab[type=bottom,waterlogged=false]')
    bp.set(-19, FL + 1, -6, 'minecraft:candle[candles=3,lit=true,waterlogged=false]')
    bp.set(-21, FL + 1, -4, 'minecraft:candle[candles=2,lit=true,waterlogged=false]')
    bp.set(-20, FL + 4, -5, 'minecraft:chain[axis=y,waterlogged=false]')
    bp.set(-20, FL + 3, -5, 'minecraft:lantern[hanging=true,waterlogged=false]')
    bp.set(-19, FL + 1, -5, 'minecraft:brown_carpet')
    bp.set(-18, FL + 1, -5, 'minecraft:brown_carpet')
    bp.set(-18, FL + 1, -4, 'minecraft:white_carpet')
    bp.set(-21, FL + 1, -5, 'minecraft:lantern[hanging=false,waterlogged=false]')
    for x, z in ((-15, -3), (-15, 3), (-24, 3)):
        for y in range(FL + 1, FL + 6):
            bp.set(x, y, z, MSB if y % 2 else SB)
    bp.set(-12, FL + 3, -3, wall_torch('west'))
    bp.set(-27, FL + 3, 4, wall_torch('east'))
    # 冷えの戸 (石の 3x3。鉄の防具を着た人が触れると開く。印 cold0..8)。枠は深層岩のタイル
    k = 0
    for y in range(FL + 1, FL + 4):
        for z in (-1, 0, 1):
            bp.set(-28, y, z, 'minecraft:polished_deepslate')
            bp.mark('cold%d' % k, -28, y, z)
            k += 1
    for z in (-2, 2):
        for y in range(FL + 1, FL + 5):
            bp.set(-28, y, z, 'minecraft:deepslate_tiles')
    for z in range(-2, 3):
        bp.set(-28, FL + 4, z, 'minecraft:deepslate_tiles')
    # 戸の奥: 氷の壁と、冷えの戸の碑文 (文は sign.skyabyss.ch1_cold_door)
    for q in shell:
        if q[0] <= -29 and -3 <= q[2] <= 3 and FL <= q[1] <= FL + 4:
            bp.set(q[0], q[1], q[2], 'minecraft:packed_ice' if (q[0] + q[1] + q[2]) % 3 else 'minecraft:blue_ice')
    bp.set(-32, FL + 1, 0, 'skyabyss:tablet[facing=east,standing=true]')
    bp.mark('cold_tablet', -32, FL + 1, 0)
    for z in (-2, 2):
        bp.set(-30, FL + 1, z, 'minecraft:snow[layers=1]')
    # 出口の縦穴: 東の壁に梯子、口は跳ね戸 (地面の高さ)
    for y in range(FL + 1, -1):
        bp.set(-11, y, 0, 'minecraft:ladder[facing=west,waterlogged=false]')
    bp.set(-11, -1, 0, 'minecraft:spruce_trapdoor[facing=west,half=top,open=false,powered=false,waterlogged=false]')
    bp.mark('exit', -11, 0, 0)
    # 部屋の中心 (自己テスト用の印)
    bp.mark('room1', 0, FL + 1, 20)
    bp.mark('room2', -25, FL + 1, 28)
    bp.mark('room3', -20, FL + 1, 0)
    return bp


def main():
    out = []
    for bp in [road_cairn(), road_shrine(), bone_cache(), cart_track(), cleft_shell_cave(), song_chamber(), bone_pit()]:
        out.append('%s=%d' % (bp.name, bp.save()))
    print(', '.join(out))


if __name__ == '__main__':
    main()
