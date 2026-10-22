#!/usr/bin/env python3
"""四章の地上 (docs/design/34 の 4.5・C4、21 の 6.1) の部品・クエスト・会話・伝承・文を書き出す。

  PYTHONIOENCODING=utf-8 python -I tools/gen_ch4_ground.py

書くもの:
  data/skyabyss/opening/ch4_ash_mark.json        灰の道の標 (5 つ。丘から灰の降る円の縁へ)
  data/skyabyss/opening/ch4_dead_forge_pit.json  消えた炉の坑 (小さなダンジョン。入口の間・鍛えの跡・奥の間 + 出口の梯子 + 職人の野営)
  data/skyabyss/opening/ch4_warm_rocks.json      温かい岩の群れ (2 つ。温石を置く台)
  data/skyabyss/opening/ch4_soot_kiln.json       煤の窯の跡 (灰の民の窯。拾い残しの箱・碑)
  data/skyabyss/opening/ch4_ash_watch.json       灰降りの高台 (石の舞台と碑)
  data/skyabyss/quest/ch4/fire_draught|forge_ruins.json   支度の段 2 本 (メインの段。順不同)
  data/skyabyss/quest/ch4/ash_road|dragger_trail.json     サブ 2 本
  data/skyabyss/quest/daily_mite_clear.json               言伝ての板の日替わり
  data/skyabyss/dialogue/ch4/{storyteller,whale_chaser}_ground.json・artisan.json   足し書き (節は ch4g_ / ch4a_ / ch4t_ / ch4p_)
  data/skyabyss/lore/entry/ch4/*.json             伝承 4 (灰の民の薬・炉跡の頁・返しに来たか・煤の窯の跡)
  data/skyabyss/recipe/soot_whetstone.json・ash_cooler.json
  assets/skyabyss_ch4/lang/{ja_jp,en_us}.json     上の文すべてと、敵・品・音の字幕・旅の覚書 (四章) の行 (四章の文に混ぜる。キー順)

語りの決まり (34 の 2・7、01): 前の私の正体は言わない (字が似ている、「今度こそ」まで)。心臓を止めるとは借りた鼓動を下へ返すこと (四章で分かる)。
目標はバニラの品と行為 (醸造台と耐火のポーション。ネザーのマグマクリーム)。理由は物語。チャットに流さない。
経験値 (新しい分。ChapterXpBudget の四章 1,200 の枠): 火除けの薬 60+120、炉跡 60+160 (+ 坑のクリア 80)、灰の道の標 160、灰ひきずり 120 = 760。
"""
import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_ch1_blueprints import BP  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
OUT = os.path.join(RES, 'data', 'skyabyss', 'opening')

ISANA = 'skyabyss:whale_chaser'
ARTISAN = 'skyabyss:artisan'
STORYTELLER = 'skyabyss:storyteller'
CH = 'titan_ember'
ja = {}
en = {}


def put(key, j, e):
    assert key not in ja, key
    ja[key] = j
    en[key] = e


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


def save(bp, note):
    blocks = [[x, y, z, i] for (x, y, z), i in sorted(bp.cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2]))]
    data = {'site': bp.name, 'note': note, 'palette': bp.palette, 'blocks': blocks, 'marks': bp.marks,
            'meta': {'rect': bp.rect, 'level': bp.level, 'ring': bp.ring}}
    with open(os.path.join(OUT, bp.name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, separators=(',', ':'))
        f.write('\n')
    return len(blocks)


BS = 'minecraft:blackstone'
PBS = 'minecraft:polished_blackstone'
PBB = 'minecraft:polished_blackstone_bricks'
CPBB = 'minecraft:cracked_polished_blackstone_bricks'
CHPB = 'minecraft:chiseled_polished_blackstone'
GBS = 'minecraft:gilded_blackstone'
PBB_WALL = 'minecraft:polished_blackstone_brick_wall'
PBB_SLAB = 'minecraft:polished_blackstone_brick_slab[type=bottom,waterlogged=false]'
PBS_SLAB = 'minecraft:polished_blackstone_slab[type=bottom,waterlogged=false]'
BASALT = 'minecraft:basalt[axis=y]'
PBASALT = 'minecraft:polished_basalt[axis=y]'
SBASALT = 'minecraft:smooth_basalt'
COAL = 'minecraft:coal_block'
TUFF = 'minecraft:tuff'
ASHC = 'minecraft:light_gray_concrete'
ASHD = 'minecraft:gray_concrete'
ASHP = 'minecraft:gray_concrete_powder'
COARSE = 'minecraft:coarse_dirt'
BARREL = 'minecraft:barrel[facing=up,open=false]'
BARREL_SIDE = 'minecraft:barrel[facing=east,open=false]'
LANTERN = 'minecraft:lantern[hanging=true,waterlogged=false]'
LANTERN_UP = 'minecraft:lantern[hanging=false,waterlogged=false]'
CAMPFIRE_OUT = 'minecraft:campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]'
ANVIL = 'minecraft:chipped_anvil[facing=north]'
ANVIL2 = 'minecraft:damaged_anvil[facing=east]'
BLAST = 'minecraft:blast_furnace[facing=south,lit=false]'
CAULDRON = 'minecraft:cauldron'
BARS = 'minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]'
CHAIN = 'minecraft:chain[axis=y,waterlogged=false]'
AIR = 'minecraft:air'
CAVE_AIR = 'minecraft:cave_air'


def stair(facing, half='bottom', block='polished_blackstone_brick_stairs'):
    return 'minecraft:%s[facing=%s,half=%s,shape=straight,waterlogged=false]' % (block, facing, half)


def rough(rng, warm=0.0):
    r = rng.random()
    if r < warm:
        return rng.choice([BASALT, SBASALT])
    r = rng.random()
    return BS if r < 0.45 else PBB if r < 0.75 else CPBB


# ================================================================ 部品
# 灰の道の標: 黒石の台に黒石の杭。杭の三方に灰の積もりの目盛り (淡い灰と濃い灰の帯)。てっぺんに黒石の板 (mark top)
def ash_mark():
    bp = BP('ch4_ash_mark', [-2, -2, 2, 2], ring=1)
    for (x, z) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        bp.set(x, 0, z, PBS_SLAB)
    bp.set(0, 0, 0, CHPB)
    bp.set(0, 1, 0, PBB)
    bp.set(0, 2, 0, ASHD)
    bp.set(0, 3, 0, PBB)
    bp.set(0, 4, 0, ASHC)
    bp.set(0, 5, 0, PBB_WALL)
    bp.set(0, 6, 0, PBS_SLAB)
    bp.mark('top', 0, 6, 0)
    # 足もとに降り積もった灰
    for (x, z) in ((1, 1), (-1, -1), (1, -1)):
        bp.set(x, -1, z, ASHP)
    bp.mark('stand', 0, 0, 2)
    return save(bp, '四章 灰の道の標 (21 の 6.1)。tools/gen_ch4_ground.py が書く')


class Pit:
    """地下の部屋。inside = 中身 (空気など) の箱、その 1 まわりを荒い黒石で包む (地面から上の殻は keep が真の所だけ)。gen_ch1_sites.Pit と同じ作り"""

    def __init__(self, name, rect, seed):
        self.bp = BP(name, rect, ring=3)
        self.rng = random.Random(seed)
        self.inside = {}

    def box(self, x0, y0, z0, x1, y1, z1, state=CAVE_AIR):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.inside[(x, y, z)] = state

    def build(self, keep, warm=0.0):
        bp = self.bp
        shell = set()
        for (x, y, z) in self.inside:
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                q = (x + dx, y + dy, z + dz)
                if q not in self.inside:
                    shell.add(q)
        shell = {q for q in shell if q[1] < -1 or keep(q)}
        for q in sorted(shell):
            bp.set(q[0], q[1], q[2], rough(self.rng, warm))
        for q, st in sorted(self.inside.items()):
            bp.set(q[0], q[1], q[2], st)
        return shell


FL = -13  # 消えた炉の坑の床のブロックの y (敵が立つのは -12)
STAND = FL + 1


# 消えた炉の坑 (14〜18 分): 丘の斜面に、黒石の枠の坑口と崩れた煙突。階段で地下 13 へ。
#   入口の間  x -4..4、z 12..24。熾り蟲 4。温石の台 (玄武岩の柱。mark warm_stand = 台の上の面のブロック)
#   鍛えの跡  x -20..-11、z 12..24 (入口の間から西への回廊)。灰の屑 2、冷えた金床・水の釜・崩れた道具棚
#   奥の間    x -22..-6、z -10..3 (鍛えの跡から南への回廊)。古い灰の番人。北の壁ぎわに炉の壇: 碑 (mark forge_tablet)・前の私の荷 (樽。mark pack)・冷えた金床 (mark anvil)
#   出口      奥の間の東の壁の外 (x -5、z -2) に梯子の縦穴。地上の口は黒石の縁
#   地上      坑口の碑 (mark tablet)、職人の野営 (mark artisan)。湧き台の立ち位置は DeadForgePit.pads と同じ
def dead_forge_pit():
    c = Pit('ch4_dead_forge_pit', [-8, -4, 7, 6], 4401)
    bp = c.bp
    # 階段 (z 0..11、一段ずつ下がる。幅 3)
    for i in range(0, 12):
        c.box(-1, -i, i, 1, -i + 2, i)
    # 入口の間
    c.box(-4, STAND, 12, 4, STAND + 4, 24)
    # 回廊 (入口の間 → 鍛えの跡)
    c.box(-10, STAND, 17, -5, STAND + 2, 19)
    # 鍛えの跡
    c.box(-20, STAND, 12, -11, STAND + 4, 24)
    # 回廊 (鍛えの跡 → 奥の間)
    c.box(-17, STAND, 4, -15, STAND + 2, 11)
    # 奥の間
    c.box(-22, STAND, -10, -6, STAND + 5, 3)
    # 出口の縦穴 (奥の間の東の壁 x -5 の z -2 を戸口にして、上へ)
    c.box(-5, STAND, -2, -5, 0, -2)
    shell = c.build(lambda q: -2 <= q[0] <= 2 and 0 <= q[2] <= 3 and q[1] <= 3, warm=0.08)

    # ---- 坑口: 黒石の枠と屋根 (階段の上)
    for q in shell:
        x, y, z = q
        if -2 <= x <= 2 and 0 <= z <= 3 and y >= 0:
            if abs(x) == 2:
                bp.set(x, y, z, PBB if y < 3 else CPBB)
            elif y == 3:
                bp.set(x, y, z, PBB_SLAB if z > 0 else PBB)
    for x in (-2, 2):
        bp.set(x, 0, -1, PBB_WALL)
        bp.set(x, 1, -1, PBB_WALL)
    bp.set(0, 3, -1, CHPB)
    for i in range(0, 12):
        for x in (-1, 0, 1):
            bp.set(x, -1 - i, i, stair('north'))
    # 崩れた煙突 (坑口の東の丘の上。2x2 の黒石の柱、上は欠けている)
    for (x, z) in ((4, 1), (5, 1), (4, 2), (5, 2)):
        h = 7 if (x, z) == (4, 1) else 5 if (x, z) == (5, 2) else 6
        for y in range(0, h):
            bp.set(x, y, z, CPBB if (y + x) % 3 == 0 else PBB)
    bp.set(5, 0, 3, COAL)
    bp.set(3, -1, 3, ASHP)
    bp.set(6, -1, 0, ASHP)
    # 坑口の碑 (階段の口の西)
    bp.mark('tablet', -3, 0, 0)
    bp.set(-3, -1, 0, CHPB)
    # 職人の野営 (坑口の南西の外)
    for (x, z) in ((-6, 4), (-5, 4), (-6, 5)):
        bp.set(x, -1, z, COARSE)
    bp.set(-6, 0, 3, CAMPFIRE_OUT)
    bp.set(-7, 0, 5, BARREL_SIDE)
    bp.set(-7, 1, 5, 'minecraft:gray_carpet')
    bp.mark('artisan', -5, 0, 5)

    # ---- 入口の間: 煤けた床、灰の吹き溜まり、温石の台 (西の壁ぎわ)、灯り
    for x in range(-4, 5):
        for z in range(12, 25):
            r = c.rng.random()
            bp.set(x, FL, z, PBS if r < 0.4 else BS if r < 0.75 else SBASALT if r < 0.92 else COAL)
    bp.set(-3, STAND, 23, PBASALT)
    bp.set(-3, STAND + 1, 23, SBASALT)
    bp.mark('warm_stand', -3, STAND + 1, 23)
    for (x, z) in ((3, 13), (4, 14), (-4, 13), (2, 24), (4, 24)):
        bp.set(x, STAND, z, ASHP)
    bp.set(0, STAND + 4, 15, LANTERN)
    bp.set(0, STAND + 4, 21, LANTERN)
    # 回廊の灯り
    bp.set(-7, STAND + 2, 18, LANTERN)

    # ---- 鍛えの跡: 冷えた金床、水の釜、崩れた道具棚 (鉄の格子)、煤の山
    for x in range(-20, -10):
        for z in range(12, 25):
            r = c.rng.random()
            bp.set(x, FL, z, PBB if r < 0.5 else CPBB if r < 0.8 else BS)
    bp.set(-19, STAND, 18, ANVIL)
    bp.set(-19, STAND, 16, CAULDRON)
    bp.set(-20, STAND, 21, BARS)
    bp.set(-20, STAND + 1, 21, BARS)
    bp.set(-20, STAND, 22, BARS)
    bp.set(-20, STAND + 1, 22, PBB_SLAB)
    bp.set(-12, STAND, 23, COAL)
    bp.set(-12, STAND, 22, ASHP)
    bp.set(-11, STAND, 13, ASHP)
    bp.set(-16, STAND + 4, 15, LANTERN)
    bp.set(-16, STAND + 4, 21, LANTERN)
    bp.set(-16, STAND + 2, 7, LANTERN)

    # ---- 奥の間: 広い床、灰の番人の背の釜の跡、北の壁ぎわに炉の壇 (碑・荷・冷えた金床・消えた炉)
    for x in range(-22, -5):
        for z in range(-10, 4):
            r = c.rng.random()
            bp.set(x, FL, z, SBASALT if r < 0.3 else PBS if r < 0.6 else CPBB if r < 0.85 else BASALT)
    # 床の古い亀裂 (見た目だけ。熾の色の筋は光らないブロック)
    for (x, z) in ((-12, -2), (-11, -1), (-10, -1), (-16, 0), (-17, 1), (-13, 1)):
        bp.set(x, FL, z, 'minecraft:red_nether_bricks')
    # 炉の壇 (z -10 の北の壁ぎわ、一段上げる)
    for x in range(-18, -9):
        bp.set(x, STAND, -10, PBB_SLAB)
    bp.set(-14, STAND, -10, CHPB)
    bp.mark('forge_tablet', -14, STAND + 1, -10)
    bp.set(-12, STAND + 1, -10, BARREL)
    bp.mark('pack', -12, STAND + 1, -10)
    bp.set(-16, STAND + 1, -10, ANVIL2)
    bp.mark('anvil', -16, STAND + 1, -10)
    bp.set(-19, STAND, -10, BLAST)
    bp.set(-20, STAND, -10, PBB)
    bp.set(-20, STAND + 1, -10, CPBB)
    bp.set(-19, STAND + 1, -10, CPBB)
    for (x, z) in ((-21, -9), (-21, 2), (-7, 2), (-7, -9)):
        bp.set(x, STAND, z, PBB)
        bp.set(x, STAND + 1, z, PBB_WALL)
        bp.set(x, STAND + 2, z, LANTERN_UP)
    for (x, z) in ((-14, -6), (-14, 0)):
        bp.set(x, STAND + 5, z, CHAIN)
        bp.set(x, STAND + 4, z, LANTERN)
    # 出口の梯子 (x -5、東の壁 x -4 に掛ける)。地上の口のまわりは黒石の縁
    for y in range(STAND, 1):
        bp.set(-5, y, -2, 'minecraft:ladder[facing=west,waterlogged=false]')
    for y in range(-1, 1):
        bp.set(-4, y, -2, PBB)
    for (x, z) in ((-6, -3), (-5, -3), (-4, -3), (-6, -1), (-5, -1), (-4, -1), (-6, -2)):
        bp.set(x, -1, z, PBB)
    bp.mark('exit', -5, 1, -2)
    bp.mark('stand', 0, 0, 5)
    return save(bp, '四章 消えた炉の坑 (21 の 6.1。小さなダンジョン)。tools/gen_ch4_ground.py が書く')


# 温かい岩の群れ: 玄武岩の柱 (高さ 1〜4) の群れと、真ん中の温石の台 (磨いた玄武岩の上の滑らかな玄武岩。mark stone)
def warm_rocks():
    bp = BP('ch4_warm_rocks', [-5, -5, 5, 5], ring=2)
    rng = random.Random(4403)
    for (x, z, h) in ((-3, -2, 3), (-2, -3, 2), (3, -1, 4), (2, 3, 2), (-3, 3, 1), (4, 2, 3), (0, -4, 2), (-4, 0, 2), (1, -3, 1)):
        for y in range(0, h):
            bp.set(x, y, z, BASALT if rng.random() < 0.7 else SBASALT)
    for x in range(-4, 5):
        for z in range(-4, 5):
            if x * x + z * z <= 18 and rng.random() < 0.5:
                bp.set(x, -1, z, rng.choice([BASALT, SBASALT, BS, TUFF]))
    bp.set(0, 0, 0, PBASALT)
    bp.set(0, 1, 0, SBASALT)
    bp.mark('stone', 0, 1, 0)
    bp.mark('stand', 0, 0, 2)
    return save(bp, '四章 温かい岩の群れ (21 の 6.1)。tools/gen_ch4_ground.py が書く')


# 煤の窯の跡: 灰の民の窯 (黒石と煉瓦の丸屋根、南に焚き口)、まわりに灰の山。窯の中に拾い残しの箱 (mark cache)、焚き口の脇に碑 (mark tablet)
def soot_kiln():
    bp = BP('ch4_soot_kiln', [-6, -6, 6, 6], ring=2)
    rng = random.Random(4404)
    BRICK = 'minecraft:bricks'
    for x in range(-3, 4):
        for z in range(-3, 4):
            d = x * x + z * z
            if d <= 10:
                bp.set(x, -1, z, rng.choice([BS, PBB, COAL]))
                # 基礎 (でこぼこの地で床の下が浮かない。build audit の SITE_FLOAT)
                bp.set(x, -2, z, BS)
                bp.set(x, -3, z, BS)
            if 5 <= d <= 10:
                for y in range(0, 3):
                    bp.set(x, y, z, BRICK if rng.random() < 0.45 else rng.choice([BS, PBB, CPBB]))
            if d <= 6:
                bp.set(x, 3, z, rng.choice([BRICK, PBB, CPBB]))
    bp.set(0, 4, 0, PBB_WALL)
    bp.set(0, 5, 0, PBB_WALL)
    # 焚き口 (南の面 z = 3 のまんなか。幅 1・高さ 2)
    bp.set(0, 0, 3, AIR)
    bp.set(0, 1, 3, AIR)
    bp.set(0, 0, 2, AIR)
    bp.set(0, 1, 2, AIR)
    # 中: 拾い残しの箱、煤
    bp.set(0, 0, -1, BARREL)
    bp.mark('cache', 0, 0, -1)
    bp.set(-1, 0, 0, COAL)
    bp.set(1, 0, 1, ASHP)
    # 碑と灰の山
    bp.mark('tablet', 2, 0, 4)
    bp.set(2, -1, 4, CHPB)
    for (x, z) in ((-4, 2), (-5, 1), (4, -2), (5, -3), (-3, -4), (3, 5)):
        bp.set(x, 0, z, ASHP)
        bp.set(x, -1, z, COARSE)
    bp.set(-4, 0, -3, BARREL_SIDE)
    bp.set(-4, -1, -3, BS)
    bp.mark('stand', 0, 0, 5)
    return save(bp, '四章 煤の窯の跡 (21 の 6.1)。tools/gen_ch4_ground.py が書く')


# 灰降りの高台: 黒石の舞台と、灰をかぶった石の腰掛け、碑 (mark tablet)、灰の円を指す杭 (mark post)
def ash_watch():
    bp = BP('ch4_ash_watch', [-4, -4, 4, 4], ring=2)
    rng = random.Random(4405)
    for x in range(-3, 4):
        for z in range(-3, 4):
            if abs(x) + abs(z) <= 5:
                bp.set(x, -1, z, rng.choice([PBS, PBB, BS, TUFF]))
    for x in (-1, 0, 1):
        bp.set(x, 0, 2, stair('north', block='polished_blackstone_stairs'))
    bp.set(-2, -1, -2, CHPB)
    bp.mark('tablet', -2, 0, -2)
    for y in range(0, 4):
        bp.set(2, y, -2, PBB_WALL if y else PBB)
    bp.set(2, 4, -2, ASHC)
    bp.mark('post', 2, 4, -2)
    bp.set(3, 0, 1, PBB_WALL)
    bp.set(-3, 0, 1, PBB_WALL)
    bp.mark('stand', 0, 0, 1)
    return save(bp, '四章 灰降りの高台 (21 の 6.1)。tools/gen_ch4_ground.py が書く')


# ================================================================ クエスト
def flag(name, npc=None):
    c = {'type': 'flag', 'flag': name}
    if npc:
        c['npc'] = npc
    return c


def not_(c):
    return {'type': 'not', 'of': c}


def quest(q, is_='active', stage=None):
    c = {'type': 'quest', 'quest': q, 'is': is_}
    if stage:
        c['stage'] = stage
    return c


# 二本とも、四章 (段階 3) の人に、メイン「灰の昇る山」が始まったら出る (イサナの灰の山の話のころ)
START = {'type': 'all', 'of': [{'type': 'stage', 'min': 3, 'max': 3}, quest('main_ember', 'started')]}
# 耐火のポーション (延長版も数える。品の ID @ 効き目の ID。QuestItems)
FIRE_POTION = 'minecraft:potion@minecraft:fire_resistance'


def quests():
    q = os.path.join(RES, 'data/skyabyss/quest/ch4')
    write_json(os.path.join(q, 'fire_draught.json'), {
        'category': 'main', 'order': 41, 'giver': ARTISAN, 'chapter': CH, 'start_when': START,
        'stages': [
            # 醸造台で耐火のポーションを 2 つ (延長版も数える)。薬師の店の醸造の手ほどき (FacilityActions.brewLesson) で道具がそろう
            {'id': 'brew', 'xp': 60, 'objectives': [{'id': 'potion', 'type': 'collect', 'target': FIRE_POTION, 'count': 2, 'party': False}]},
            # 旅の職人に見せる (灰の民の薬の伝承 ch4/fire_draught を記す)
            {'id': 'show', 'objectives': [{'id': 'talk', 'type': 'talk', 'target': ARTISAN, 'topic': 'ch4g_draught_show'}]},
        ],
        'reward': {'xp': 120, 'items': [], 'affinity': {ARTISAN: 3}},
    })
    write_json(os.path.join(q, 'forge_ruins.json'), {
        'category': 'main', 'order': 42, 'giver': STORYTELLER, 'chapter': CH, 'start_when': START,
        'stages': [
            # 消えた炉の坑を抜けると (奥の間の古い灰の番人を倒す)、冷えた金床の上の頁 (DeadForgePit が伝承 ch4/forge_page を記す)
            {'id': 'pit', 'xp': 60, 'objectives': [{'id': 'page', 'type': 'lore', 'targets': ['ch4/forge_page']}]},
            {'id': 'tell', 'objectives': [{'id': 'talk', 'type': 'talk', 'target': STORYTELLER, 'topic': 'ch4g_forge_read2'}]},
        ],
        'reward': {'xp': 160, 'items': [], 'affinity': {STORYTELLER: 3}},
    })
    write_json(os.path.join(q, 'ash_road.json'), {
        'category': 'sub', 'order': 47, 'giver': STORYTELLER, 'chapter': CH, 'min_stage': 3,
        'start_when': {'type': 'stage', 'min': 3, 'max': 3},
        'stages': [
            # 標 5 つ (着いたとき、人ごとに標一つにつき一度 ch4_ash_mark)
            {'id': 'trace', 'objectives': [{'id': 'marks', 'type': 'signal', 'target': 'ch4_ash_mark', 'count': 5, 'party': False}]},
        ],
        'reward': {'xp': 160},
    })
    write_json(os.path.join(q, 'dragger_trail.json'), {
        'category': 'sub', 'order': 48, 'giver': ISANA, 'chapter': CH, 'min_stage': 3,
        'start_when': {'type': 'stage', 'min': 3, 'max': 3},
        'stages': [
            # 灰ひきずりを追い、煤の窯の跡に着く (窯の跡に着いたとき ch4_kiln)
            {'id': 'follow', 'objectives': [{'id': 'kiln', 'type': 'signal', 'target': 'ch4_kiln', 'party': False}]},
        ],
        'reward': {'xp': 120, 'affinity': {ISANA: 2}},
    })
    write_json(os.path.join(RES, 'data/skyabyss/quest/daily_mite_clear.json'), {
        # 板の頼みは段階 1 から読み込まれる決まり (FreeQuestSelfTest)。四章 (段階 3) とイサナに会ったあとに限るのは unlock で
        'category': 'sub', 'order': 66, 'repeat': 'daily', 'board': True, 'min_stage': 1,
        'unlock': {'type': 'all', 'of': [{'type': 'flag', 'flag': 'met', 'npc': ISANA}, {'type': 'stage', 'min': 3}]},
        'stages': [
            {'id': 'sweep', 'objectives': [{'id': 'kill', 'type': 'kill', 'target': 'skyabyss:cinder_mite', 'count': 10}]},
        ],
        'reward': {'xp': 60, 'items': [{'item': 'skyabyss:soot_iron', 'count': 2}]},
    })

    b = 'quest.skyabyss.ch4.fire_draught.'
    put(b + 'title', '火除けの薬', 'The Fire Draught')
    put(b + 'summary', '炉に入る支度に、醸造台で耐火のポーションを二つ作る。', 'Brew two potions of fire resistance at a brewing stand.')
    put(b + 'brew.journal',
        '旅の職人が言っていた。「灰の民は、火の薬を飲んでから炉に入った。二つ作っておけ。炉は一度じゃ済まない」\n'
        '醸造台で、水の瓶にネザーウォートを入れると奇妙な薬になる。そこへマグマクリームを足せば、耐火のポーションになる。都の薬師の店で、醸造の手ほどきが受けられる。マグマクリームは、ネザーのマグマキューブが落とす。',
        'The traveling artisan said: "The ash people drank a fire draught before they went into the forge. Make two. One forge is never enough."\n'
        'At a brewing stand, nether wart turns a water bottle into an awkward potion. Add magma cream and it becomes a potion of fire resistance. The apothecary in the capital teaches brewing. Magma cream drops from magma cubes in the Nether.')
    put(b + 'brew.potion', '炉に入る前に、耐火のポーションを作ろう', 'Brew fire resistance for the forge')
    put(b + 'show.journal', '耐火のポーションが二つ、そろった。旅の職人に見せよう。', 'Two fire draughts are ready. Show them to the traveling artisan.')
    put(b + 'show.talk', '耐火のポーションを、旅の職人に見せよう', 'Show the potions to the artisan')
    put(b + 'done', '「火を飲んで、火の中へ。灰の民は、そうやって炉に入ったんだ」', '"Drink fire, walk into fire. That is how the ash people entered the forge."')

    b = 'quest.skyabyss.ch4.forge_ruins.'
    put(b + 'title', '炉跡', 'The Forge Ruins')
    put(b + 'summary', '語り部に聞いた灰の民の炉跡を、消えた炉の坑の奥に探す。', 'See the ash people\'s forge at the bottom of the dead forge pit.')
    put(b + 'pit.journal',
        '語り部が言っていた。「南の灰の地の丘の下に、灰の民の炉跡があるそうだよ。古い灰の番人が、まだ火の番をしているって」\n'
        '坑の奥の古い灰の番人を倒せば、炉跡を見られるだろう。灰の殻は熱で割れやすい。床の亀裂が赤く脈を打ったら、そこを離れること。',
        'The storyteller said: "Under the hills of the southern ash, there is a forge of the ash people. An old thrall still keeps its fire, they say."\n'
        'Fell the old thrall at the bottom of the pit and the forge will be yours to see. Ash shells crack under heat. When a crack in the floor starts to pulse red, step away.')
    put(b + 'pit.page', '坑の奥の古い灰の番人を倒そう', "Fell the pit's old keeper")
    put(b + 'tell.journal', '冷えた金床の上に、破れた頁があった。語り部に見せよう。', 'A torn page lay on the cold anvil. Show it to the storyteller.')
    put(b + 'tell.talk', '炉跡の頁を、語り部に見せよう', 'Show the forge page to the storyteller')
    put(b + 'done', '「四つ返したら、下へ」。……返しに行くんだね。', '"Return four, then go down." ...You are going to give it back, then.')

    b = 'quest.skyabyss.ch4.ash_road.'
    put(b + 'title', '灰の道の標', 'Marks of the Ash Road')
    put(b + 'summary', '灰の降る円の縁まで、黒石の杭を五つ辿る。', 'Follow five blackstone posts to the rim of the falling ash.')
    put(b + 'trace.journal', '語り部が言っていた。灰の民は、黒石の杭で灰の積もりを測りながら、灰の降る円の縁まで歩いたという。',
        'The storyteller said the ash people walked to the rim of the falling ash, measuring how deep it lay with blackstone posts.')
    put(b + 'trace.marks', '灰の道の標を辿る', 'Follow the marks of the ash road')
    put(b + 'done', '杭の灰は、円の中ほど濃かった。', 'The nearer the circle, the deeper the ash on the posts.')

    b = 'quest.skyabyss.ch4.dragger_trail.'
    put(b + 'title', '灰ひきずり', 'The Ash Dragger')
    put(b + 'summary', '灰を引きずる虫を追って、灰の民の窯の跡へ。', 'Chase the ash-dragging bug to the ash people\'s kiln.')
    put(b + 'follow.journal',
        'イサナが言っていた。「灰を引きずって歩く虫がいる。追うと丸まって転がって逃げる。逃げる先に、灰の民の窯があったんだ」',
        'Isana said: "There is a bug that drags ash behind it. Chase it and it curls up and rolls away. Where it rolls, there was a kiln of the ash people."')
    put(b + 'follow.kiln', '灰ひきずりを追い、窯の跡を探す', 'Follow a dragger to the kiln')
    put(b + 'done', '窯の跡は、灰ひきずりの巣になっていた。', 'The old kiln had become the draggers\' nest.')

    b = 'quest.skyabyss.daily_mite_clear.'
    put(b + 'title', '熾り蟲の掃除', 'Mite Sweep')
    put(b + 'summary', '灰の地の熾り蟲を十匹払う。', 'Clear ten cinder mites from the ash lands.')
    put(b + 'sweep.journal', '板の紙（煤で汚れた字）：「熾り蟲が干し場の火に寄ってくる。十匹ほど払ってほしい。温石を持っていけば、向こうから来る」',
        'A note on the board, smudged with soot: "Cinder mites keep creeping to the drying fires. Clear about ten. Carry a warm stone and they will come to you."')
    put(b + 'sweep.kill', '熾り蟲を払う', 'Clear cinder mites')
    put(b + 'done', 'しばらく、火のまわりは静かだろう。', 'The fires will be quiet for a while.')


# ================================================================ 会話
def dialogue():
    d = os.path.join(RES, 'data/skyabyss/dialogue/ch4')
    key_open = not_(quest('main_key', 'active'))
    write_json(os.path.join(d, 'storyteller_ground.json'), {
        'npc': STORYTELLER, 'extends': True,
        'entries': [
            {'node': 'ch4g_forge_read', 'if': quest('ch4/forge_ruins', 'active', 'tell')},
            {'node': 'ch4g_forge_tale', 'if': [quest('ch4/forge_ruins', 'active', 'pit'), not_(flag('ch4g_forge_told')), key_open]},
            {'node': 'ch4g_road_tale', 'if': [quest('ch4/ash_road', 'active'), not_(flag('ch4g_road_told')), key_open]},
            {'node': 'ch4g_s_kiln', 'if': [flag('ch4g_kiln_seen'), not_(flag('ch4g_kiln_told')), key_open]},
        ],
        'nodes': {
            'ch4g_forge_tale': {'face': 'grave', 'do': [flag('ch4g_forge_told')], 'next': 'ch4g_forge_tale2'},
            'ch4g_forge_tale2': {'face': 'neutral', 'next': 'menu'},
            'ch4g_forge_read': {'face': 'grave', 'next': 'ch4g_forge_read2'},
            'ch4g_forge_read2': {'face': 'grave', 'do': [{'type': 'affinity', 'amount': 1}], 'next': 'menu'},
            'ch4g_road_tale': {'face': 'neutral', 'do': [flag('ch4g_road_told')], 'next': 'menu'},
            'ch4g_s_kiln': {'face': 'smile', 'do': [flag('ch4g_kiln_told')], 'next': 'menu'},
        },
    })
    s = 'dialogue.skyabyss.storyteller.'
    put(s + 'ch4g_forge_tale',
        '灰の民の話をしようか。祖母の祖母の頃には、南の灰の地に、まだ炉の火が残っていたそうだよ。\n丘の下の坑に炉があってね。灰の民は火を焚くんじゃなく、火を預かっていたんだと。……今も、古い灰の番人が火の番をしているって。',
        'Shall I tell you of the ash people? In my grandmother\'s grandmother\'s day, a forge fire still burned in the southern ash.\nThere was a forge in a pit under the hills. The ash people did not burn fire there. They kept it in trust. ...An old thrall still keeps its watch, they say.')
    put(s + 'ch4g_forge_tale2',
        '番をする者は、火が消えても番をやめないものさ。\n……行くなら、殻を熱で割るといい。冷たい刃は、灰に吸われるからね。',
        'Those who keep watch do not stop when the fire dies.\n...If you go, crack its shell with heat. A cold blade just sinks into ash.')
    put(s + 'ch4g_forge_read',
        '見せておくれ。……「四つ返したら、下へ。今度こそ」。\nこの字、やっぱりどこかで見た字だよ。今度こそ、と書く人は、前にもしくじった人だね。',
        'Let me see. ..."Return four, then go down. This time."\nI have seen this hand before, I am sure of it. Someone who writes "this time" has failed once already.')
    put(s + 'ch4g_forge_read2',
        '返す、と書いてある。奪うでも、壊すでもなく。\n……あたしの祖母も、預けたものは返しに行くものだと言っていた。あんたは、返しに行くんだね。',
        'It says "return". Not take, not break.\n...My grandmother said the same: what was left in trust is something you go to give back. You are going to give it back.')
    put(s + 'ch4g_road_tale',
        '灰の民は、黒石の杭で灰の積もりを測っていたそうだよ。灰が薄くなるほうへ歩けば、灰の降る円の縁に着く。\n……縁から先へは、誰も行かなかったけどね。',
        'The ash people measured the fallen ash with blackstone posts, they say. Walk where it lies deeper and you reach the rim of the falling ash.\n...No one ever went past the rim, though.')
    put(s + 'ch4g_s_kiln',
        '窯の跡を見つけたのかい。……丘が眠るあいだだけ焚く、か。\n火を借りて、返す。灰の民は、ずっとそうやって暮らしてきたんだね。',
        'You found the kiln? ..."Burn only while the hill sleeps."\nBorrow the fire, then give it back. That is how the ash people lived, all along.')

    write_json(os.path.join(d, 'whale_chaser_ground.json'), {
        'npc': ISANA, 'extends': True,
        'entries': [
            # 天の鍵: 鍵の話 (ms_c_key) を先に聞いた人も、メイン「天の鍵」のあいだはイサナの答えを聞き直せる (始める前に聞いた話題は数えないので)
            {'node': 'ms_c_key3', 'if': [quest('main_key', 'active', 'show'), flag('c_key_seen')]},
            {'node': 'ch4g_c_board', 'if': [quest('main_ember', 'active', 'board'), not_(flag('ch4g_board_told'))]},
            {'node': 'ch4g_c_dragger', 'if': [quest('ch4/dragger_trail', 'active'), not_(flag('ch4g_dragger_told')), key_open]},
            {'node': 'ch4g_c_watch', 'if': [flag('ch4g_watch_seen'), not_(flag('ch4g_watch_told')), key_open]},
        ],
        'nodes': {
            'ch4g_c_board': {'face': 'grave', 'do': [flag('ch4g_board_told')], 'next': 'c_menu'},
            'ch4g_c_dragger': {'face': 'smile', 'do': [flag('ch4g_dragger_told')], 'next': 'c_menu'},
            'ch4g_c_watch': {'face': 'grave', 'do': [flag('ch4g_watch_told'), {'type': 'affinity', 'amount': 1}], 'next': 'c_menu'},
        },
    })
    c = 'dialogue.skyabyss.whale_chaser.'
    put(c + 'ch4g_c_board',
        'あの山は、夜に眠って、ゆっくり息をする。吸うときに、口が開く。\n……吸い込まれるのを待て。怖いなら、怖いままでいい。俺は、ここで数えて待っている。',
        'That mountain sleeps at night and breathes slowly. When it breathes in, its mouth opens.\n...Wait to be drawn in. If you are afraid, stay afraid. I will be here, counting, waiting.')
    put(c + 'ch4g_c_dragger',
        '灰を引きずって歩く虫を見たか。追うと、丸まって転がって逃げる。\n……ガキの頃、あれを追いかけて、灰の民の窯の跡に迷い込んだことがある。今も、あそこにあるはずだ。',
        'Seen the bug that drags ash behind it? Chase it and it curls up and rolls away.\n...As a boy I chased one and wandered into an old kiln of the ash people. It should still be there.')
    put(c + 'ch4g_c_watch',
        '南の高台に登ったのか。……円の真ん中だけ、夜に赤く見えただろう。\n俺は、あれを火だと思っていた。今は、息だと思う。',
        'You climbed the southern lookout? ...Only the middle of the circle glows red at night, doesn\'t it.\nI used to think that was fire. Now I think it is breath.')

    write_json(os.path.join(d, 'artisan.json'), {
        'npc': ARTISAN, 'extends': True,
        'entries': [
            {'node': 'ch4g_draught_show', 'if': quest('ch4/fire_draught', 'active', 'show')},
            {'node': 'ch4t_ward_weave', 'if': [{'type': 'item', 'item': 'skyabyss:ash_ward_thread', 'count': 1, 'optional': True}, not_(flag('ch4t_woven')),
                                               {'type': 'ward_ok', 'titan': 'ember'}]},
            {'node': 'ch4a_first', 'if': [{'type': 'stage', 'min': 3, 'max': 3}, not_(flag('ch4a_met'))]},
            {'node': 'ch4g_draught_tell', 'if': [quest('ch4/fire_draught', 'active', 'brew'), not_(flag('ch4g_draught_told'))]},
            # 編み直し: 主の選択肢は上限いっぱいなので、余燼の鼓動守をなくして煤鉄 8 を持っている人には、会話の入口で尋ねる (1 日に 1 つ)
            {'node': 'ch4a_ward_offer', 'if': [flag('ch4t_woven'), {'type': 'ward_ok', 'titan': 'ember'},
                                               {'type': 'item', 'item': 'skyabyss:soot_iron', 'count': 8, 'optional': True}]},
        ],
        'nodes': {
            'ch4a_first': {'face': 'neutral', 'do': [flag('ch4a_met'), flag('met'), {'type': 'give_notes', 'chapter': 4}, {'type': 'affinity', 'amount': 1}],
                           'next': 'a_menu'},
            'ch4g_draught_tell': {'face': 'neutral', 'do': [flag('ch4g_draught_told')], 'next': 'a_menu'},
            'ch4g_draught_show': {'face': 'smile', 'do': [{'type': 'lore', 'entry': 'ch4/fire_draught'}, {'type': 'affinity', 'amount': 1}], 'next': 'a_menu'},
            'ch4t_ward_weave': {'face': 'smile', 'do': [{'type': 'take', 'item': 'skyabyss:ash_ward_thread', 'count': 1},
                                                         {'type': 'give', 'item': 'skyabyss:heartbeat_ward_ember', 'count': 1}, flag('ch4t_woven'),
                                                         {'type': 'affinity', 'amount': 2}], 'next': 'a_menu'},
            'ch4a_ward_offer': {'face': 'neutral', 'options': [{'id': 'yes', 'goto': 'ch4a_ward'}, {'id': 'no', 'goto': 'a_menu'}]},
            'ch4a_ward': {'face': 'smile', 'do': [{'type': 'take', 'item': 'skyabyss:soot_iron', 'count': 8}, {'type': 'remake_ward', 'titan': 'ember'},
                                                   {'type': 'affinity', 'amount': 1}], 'next': 'a_menu'},
        },

    })
    a = 'dialogue.skyabyss.artisan.'
    put(a + 'ch4a_first',
        '灰の地まで来たか。……ここの道具は、煤けて重い。\n覚書を渡しておく。灰の地の虫の見分け方と、煤の砥石、灰の冷やし水の作り方だ。',
        'So you came as far as the ash. ...Tools here are sooty and heavy.\nTake these notes: how to tell the ash bugs apart, and how to make a soot whetstone and an ash cooler.')
    put(a + 'ch4g_draught_tell',
        '灰の民は、火の薬を飲んでから炉に入った。火を飲んで、火の中へ、だ。\n醸造台で作れる。都の薬師が手ほどきをしてくれる。マグマクリームはネザーにある。……二つ作っておけ。炉は一度じゃ済まない。',
        'The ash people drank a fire draught before they went into the forge. Drink fire, walk into fire.\nYou can brew it at a stand. The apothecary in the capital will teach you. Magma cream is in the Nether. ...Make two. One forge is never enough.')
    put(a + 'ch4g_draught_show',
        '耐火の薬か。よく作ったな。\n道具は覚えている。持ち主は、そうでもない。……火の薬だけは、飲んだ者が覚えているもんだ。',
        'Fire draughts. Well brewed.\nTools remember. Their owners, less so. ...Only a fire draught is remembered by the one who drinks it.')
    put(a + 'ch4t_ward_weave',
        '灰の護り糸か。古い灰の番人の背の釜に、編みかけで入っていたんだろう。\n鼓動守に仕上げよう。熱の中で鼓動が速くなったら、握るといい。',
        'Ash ward thread. It must have been in the old thrall\'s pot, half woven.\nI will finish it into a heartbeat ward. When your heart races in the heat, hold it.')
    put(a + 'ch4a_ward', '煤鉄で編み直した。……今度は、溶かすなよ。', 'Rewoven with soot iron. ...Don\'t melt this one.')
    put(a + 'ch4a_ward_offer', '余燼の鼓動守を、なくしたのか。煤鉄が八つあれば、編み直せるが。', 'Lost your ember heartbeat ward? With eight soot iron, I can weave another.')
    put(a + 'ch4a_ward_offer.yes', '編み直してほしい', 'Please reweave it')
    put(a + 'ch4a_ward_offer.no', '今はいい', 'Not now')


# ================================================================ 伝承
def lore():
    entries = [
        ('fire_draught', 185, '灰の民の薬', 'The Ash People\'s Draught',
         '旅の職人が言った。灰の民は、火の薬を飲んでから炉に入ったという。\n\n炉の中は、外の火よりずっと熱い。灰の民はそこで、借りた火を打ち直していた。\n\n薬の瓶の底には、いつも灰がひとつまみ沈めてあった。火に、自分たちの匂いを覚えさせるために。',
         'The traveling artisan said the ash people drank a fire draught before they entered the forge.\n\nInside the forge it was far hotter than any fire outside. There, the ash people reforged the fire they had borrowed.\n\nAt the bottom of every flask lay a pinch of ash, so the fire would learn their scent.'),
        ('forge_page', 186, '炉跡の頁', 'The Forge Page',
         '消えた炉の坑の奥、冷えた金床の上に、破れた頁が一枚あった。\n\n「四つ返したら、下へ。今度こそ」\n\n今度こそ、と書いた手は、前にも同じことを書いたことがあるような書き方をしていた。その字は、自分のものに似ている。',
         'At the bottom of the dead forge pit, on a cold anvil, lay one torn page.\n\n"Return four, then go down. This time."\n\nThe hand that wrote "this time" wrote it like a hand that had written it before. The writing looks like your own.'),
        ('return_the_beat', 187, '返しに来たか', 'Have You Come to Return It',
         '鍛えの間の奥で、灰の鍛え手は槌を上げる前に、低く言った。\n\n「返しに来たか」\n\n怒っている声ではなかった。待っていた者の声だった。',
         'Deep in the forge hall, before it raised its hammer, the ash smith spoke low.\n\n"Have you come to return it?"\n\nIt was not an angry voice. It was the voice of one who had been waiting.'),
        ('soot_kiln', 188, '煤の窯の跡', 'The Soot Kiln',
         '灰の民の窯の跡。焚き口の上に、刻みが残っている。\n\n「窯は、丘が眠るあいだだけ焚く。丘が起きたら、火を返す」\n\n灰の民にとって火は、焚くものではなく、預かるものだったらしい。',
         'A kiln of the ash people. Above its mouth, words are cut in the stone.\n\n"Fire the kiln only while the hill sleeps. When the hill wakes, give the fire back."\n\nTo the ash people, fire seems to have been something held in trust, not something to burn.'),
    ]
    for name, order, tj, te, bj, be in entries:
        write_json(os.path.join(RES, 'data/skyabyss/lore/entry/ch4/%s.json' % name), {'chapter': CH, 'order': order, 'unlock': [{'type': 'manual'}], 'related': []})
        put('lore.skyabyss.ch4.%s.title' % name, tj, te)
        put('lore.skyabyss.ch4.%s.body' % name, bj, be)


# ================================================================ レシピ
def recipes():
    r = os.path.join(RES, 'data/skyabyss/recipe')
    write_json(os.path.join(r, 'soot_whetstone.json'), {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'item': 'skyabyss:soot_iron'}, {'item': 'skyabyss:soot_iron'}, {'item': 'minecraft:flint'}],
        'result': {'id': 'skyabyss:soot_whetstone', 'count': 1}})
    write_json(os.path.join(r, 'ash_cooler.json'), {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'item': 'skyabyss:ash_hemp'}, {'item': 'skyabyss:ash_hemp'}, {'item': 'minecraft:glass_bottle'}],
        'result': {'id': 'skyabyss:ash_cooler', 'count': 1}})


# ================================================================ 場所の文・碑・敵・品・音・覚書
def texts():
    m = 'message.skyabyss.ch4g.'
    put(m + 'arrive.ch4_ash_mark', '黒石の杭に、灰の目盛りが刻んである。灰は、南へ行くほど濃い。', 'Ash gauges are cut into a blackstone post. The ash lies deeper to the south.')
    put(m + 'marks.last', '五つ目の杭の灰だけが、まだ温かい。灰の降る円の縁に着いた。', 'Only the ash on the fifth post is still warm. You have reached the rim of the falling ash.')
    put(m + 'arrive.ch4_dead_forge_pit', '坑の口から、冷えた鉄の匂いがする。', 'The mouth of the pit smells of cold iron.')
    put(m + 'pit.trail', '語り部の言っていた炉跡は、この坑の奥だろう。', 'The forge the storyteller spoke of must lie at the bottom of this pit.')
    put(m + 'pit.stand', '台の上の玄武岩がくぼんでいる。温かいものを置く場所らしい。', 'The basalt on the stand is worn hollow. Something warm was set here.')
    put(m + 'pit.stand_warm', '温石を台に置いた。熾り蟲が、台のほうへ向きを変えた。', 'You set the warm stone on the stand. The cinder mites turn toward it.')
    put(m + 'pit.stand_cold', '台は冷たい。温石があれば、熱を返すかもしれない。', 'The stand is cold. A warm stone might wake it.')
    put(m + 'pit.pack', '冷えた炉の脇に、誰かの荷が残っていた。ブレイズパウダーが一つと、松明。', 'Beside the cold furnace, someone left a pack: one blaze powder, and torches.')
    put(m + 'pit.pack_empty', '荷はもう空だ。', 'The pack is empty now.')
    put(m + 'pit.page', '冷えた金床の上に、破れた頁が一枚のっている。', 'A torn page lies on the cold anvil.')
    put(m + 'pit.crack', '床の亀裂が、赤く脈を打ちはじめた。', 'The cracks in the floor begin to pulse red.')
    put(m + 'pit.wave', '殻が割れた。中から熱が噴き出す。', 'The shell splits. Heat bursts out.')
    put(m + 'arrive.ch4_warm_rocks', '玄武岩の群れのあいだだけ、空気が揺れている。', 'Only among the basalt pillars does the air shimmer.')
    put(m + 'rocks.stone', '温石を近づけると、岩が熱を返してきた。手の中の石が、かすかに脈を打つ。', 'As you bring the warm stone close, the rock answers with heat. The stone in your hand gives a faint beat.')
    put(m + 'arrive.ch4_soot_kiln', '灰の民の窯の跡。焚き口が、煤で黒い。', 'An old kiln of the ash people. Its mouth is black with soot.')
    put(m + 'kiln.cache', '窯の底に、煤けた鉄と灰麻の束が残っていた。', 'At the bottom of the kiln: sooty iron and a bundle of ash hemp.')
    put(m + 'kiln.cache_empty', '窯の底には、もう灰しかない。', 'Only ash remains at the bottom of the kiln.')
    put(m + 'kiln.dragger', '灰ひきずりは、窯の口へ転がり込んでいった。ここが巣なのだろう。', 'The dragger rolls into the kiln\'s mouth. This must be its nest.')
    put(m + 'arrive.ch4_ash_watch', '灰降りの高台。ここから、灰の降る円がよく見える。', 'The ashfall lookout. From here the circle of falling ash is plain to see.')
    put(m + 'watch.night', '円の真ん中の地面だけが、夜の中でうっすら赤い。', 'Only the ground at the circle\'s heart glows faintly red in the night.')
    put(m + 'ready', '灰の地へ行く支度が、そろった。', 'You are ready for the ash lands.')
    put(m + 'smith.return', '灰の鍛え手が、槌を上げる前に低く言った。「……返しに来たか」', 'Before raising its hammer, the ash smith speaks low: "...Have you come to return it?"')
    put(m + 'mite.drawn', '熾り蟲の群れが、手の中の温石へ向きを変えた。', 'The mites turn as one toward the warm stone in your hand.')
    put(m + 'cooler.drink', '冷たい灰の水が、喉の奥の火照りを引いていく。', 'The cold ash water draws the heat from your throat.')
    for key, lines_ja, lines_en in (
            ('sign.skyabyss.ch4_forge_pit', ['消えた炉の坑', '火は 預かり物', '番は 灰の中', '殻は 熱で割れる'],
             ['Dead Forge Pit', 'Fire is held', 'in trust. Its', 'keeper waits']),
            ('sign.skyabyss.ch4_forge', ['四つ 返したら', '下へ', '今度こそ', ''],
             ['Return four,', 'then go down', 'This time', '']),
            ('sign.skyabyss.ch4_kiln', ['窯は 丘が', '眠るあいだ', 'だけ焚く', '起きたら 返す'],
             ['Fire it only', 'while the hill', 'sleeps. Give', 'it back after']),
            ('sign.skyabyss.ch4_watch', ['灰降りの高台', '円の真ん中は', '夜に赤い', '息のように'],
             ['Ashfall Watch', 'The heart of', 'the circle glows', 'red, like breath'])):
        for i in range(4):
            put('%s.%d' % (key, i + 1), lines_ja[i], lines_en[i])
    # 敵 (名前・スポーンエッグ・音の字幕。音の出来事は skyabyss_ch4 の名前空間。tools/gen_ch4_ground_mobs.py)
    for mid, nj, ne in (('cinder_mite', '熾り蟲', 'Cinder Mite'), ('ash_husk', '灰の屑', 'Ash Husk'), ('ash_dragger', '灰ひきずり', 'Ash Dragger'),
                        ('elder_thrall', '古い灰の番人', 'Elder Thrall')):
        put('entity.skyabyss.' + mid, nj, ne)
        put('item.skyabyss.%s_spawn_egg' % mid, nj + 'のスポーンエッグ', ne + ' Spawn Egg')
    for sid, sj, se in (('cinder_mite_ambient', '熾り蟲がはぜる', 'Mite crackles'), ('cinder_mite_death', '熾り蟲が消える', 'Mite fizzles out'),
                        ('ash_dragger_curl', '灰ひきずりが丸まる', 'Dragger curls up'), ('ash_dragger_roll', '灰が転がる', 'Ash rolls'),
                        ('elder_thrall_crack', '床の亀裂が鳴る', 'Floor cracks rumble'), ('elder_thrall_burst', '亀裂が噴く', 'Cracks burst'),
                        ('elder_thrall_wave', '熱の波', 'Heat wave'), ('elder_thrall_rake', '火掻きが振り上がる', 'Rake raised')):
        put('subtitles.skyabyss_ch4.' + sid, sj, se)
    # 試遊のコマンド /skyabyss playtest now の四章の分 (story/ch4/Ch4Playtest)
    for k, j, e in (('ch4_draught', '耐火のポーションを 2 つ渡した。旅の職人に見せよう', 'Gave two fire resistance potions. Show them to the artisan'),
                    ('ch4_draught_show', '耐火のポーションを、旅の職人に見せた扱いにした', 'Counted the draughts as shown to the artisan'),
                    ('ch4_pit', '消えた炉の坑の口へ運んだ。奥の古い灰の番人を倒そう', 'Moved you to the dead forge pit. Fell the old thrall at the bottom'),
                    ('ch4_forge_page', '炉跡の頁を記した。語り部に見せよう', 'Recorded the forge page. Show it to the storyteller'),
                    ('ch4_forge_tell', '炉跡の頁を、語り部に見せた扱いにした', 'Counted the forge page as shown to the storyteller')):
        put('commands.skyabyss.playtest.now.' + k, j, e)
    # 品
    for iid, nj, ne, dj, de in (
            ('soot_iron', '煤鉄', 'Soot Iron', '灰の地の煤けた鉄。砥石と、鼓動守の編み直しに使う', 'Sooty iron from the ash lands. For whetstones and reweaving a heartbeat ward'),
            ('ash_hemp', '灰麻', 'Ash Hemp', '灰の中でも枯れない麻。冷やし水に漬ける', 'Hemp that thrives even in ash. Steeped into coolers'),
            ('ash_ward_thread', '灰の護り糸', 'Ash Ward Thread', '古い灰の番人の背の釜にあった編みかけの糸。旅の職人が鼓動守に編む',
             'A half-woven thread from the old thrall\'s pot. The artisan weaves it into a heartbeat ward'),
            ('soot_whetstone', '煤の砥石', 'Soot Whetstone', '使うと刃を研ぐ（煤の刃: 攻撃 +2.6、20 分）。灰の番人の殻へ 1.5 倍の傷',
             'Use to hone your blade (Soot Blade: +2.6 attack for 20 minutes). Deals 1.5x to ash shells'),
            ('ash_cooler', '灰の冷やし水', 'Ash Cooler', '飲むと体の芯の熱が引き、しばらく火に強くなる（余燼の背の熱 −40・耐火 30 秒）',
             'Drink to draw the heat from your core (-40 heat on the ember\'s back) and resist fire for a while (30 s)')):
        put('item.skyabyss.' + iid, nj, ne)
        put('item.skyabyss.%s.desc' % iid, dj, de)
    # 旅の覚書 (四章)。敵の見分け方と備えの一行ずつ
    for i, (lj, le) in enumerate((
            ('熾り蟲: 熱に寄る。温石を持っていると群れで来る。台に温石を置けば、群れは台へ。叩けば一撃。',
             'Cinder mites: drawn to heat. Carry a warm stone and they swarm you. Set it on a stand and they go there. One hit each.'),
            ('灰の屑: 殻を割るまで傷は半分。割れると燃えて速くなり、やがて燃え尽きる。',
             'Ash husks: half damage until the shell breaks. Then they burn, speed up, and burn out.'),
            ('灰ひきずり: 敵意はない。追うと丸まって転がる。行き先は窯の跡。',
             'Ash draggers: harmless. Chase one and it rolls away, toward the old kiln.'),
            ('古い灰の番人: 床の亀裂が赤く脈を打ったら離れる（1.5 秒後に噴く）。殻が割れると熱の波。',
             'Old thrall: when floor cracks pulse red, step away (they burst 1.5 s later). Breaking its shell sends out a heat wave.'),
            ('耐火のポーション: 醸造台・水の瓶・ネザーウォート・マグマクリーム。都の薬師が手ほどきをする。',
             'Fire draught: brewing stand, water bottle, nether wart, magma cream. The capital\'s apothecary teaches it.'),
            ('煤の砥石（煤鉄 2・火打ち石）と灰の冷やし水（灰麻 2・ガラス瓶）。冷やし水は背の熱を下げる。',
             'Soot whetstone (2 soot iron, flint) and ash cooler (2 ash hemp, bottle). The cooler lowers the heat on the back.'),
            ('灰歩きの旅装（煤鉄・灰麻・雷翼の羽軸）は、背の熱がたまりにくい。灰焼きのパン（小麦 3・灰麻）は吸収と熱の引き。',
             'Ash walking gear (soot iron, ash hemp, thunder wing shafts) slows the heat on the back. Ash-baked bread (3 wheat, ash hemp) gives absorption and cools.')), start=1):
        put('wayfarer_notes.ch4.line.%d' % i, lj, le)


# ================================================================ メインの段の足し書き (34 の 4.5: 「乗る」の段と、天の鍵の二人の答え)
# main_ember・main_key・語り部とイサナの物語の会話は tools/gen_story_quests.py の生成物だが、生成元がデータと離れているので、
# ここではデータのファイルを直接、何度流しても同じになるように足す (文は四章の lang)
def read_json(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def story_patch():
    q = os.path.join(RES, 'data/skyabyss/quest')
    ember = read_json(os.path.join(q, 'main_ember.json'))
    stages = [s for s in ember['stages'] if s['id'] != 'board']
    i = [s['id'] for s in stages].index('see') + 1
    stages.insert(i, {'id': 'board', 'objectives': [{'id': 'board', 'type': 'lore', 'targets': ['chapter1/ember_ruins']}], 'xp': 60,
                      'skip_if': {'type': 'stilled', 'titan': 'ember'}})
    ember['stages'] = stages
    write_json(os.path.join(q, 'main_ember.json'), ember)
    b = 'quest.skyabyss.main_ember.board.'
    put(b + 'journal', '山は夜、眠りの中でゆっくり息をする。吸うあいだだけ、口が開く。\nその口へ入れば、背の中へ運ばれるだろう。',
        'At night the mountain breathes slowly in its sleep. Only while it breathes in does its mouth open.\nStep into that mouth and you will be carried inside its back.')
    put(b + 'board', '吸うあいだに、余燼の口へ入ろう', 'Enter its mouth as it breathes in')

    key = read_json(os.path.join(q, 'main_key.json'))
    obs = [o for o in key['stages'][0]['objectives'] if o['id'] != 'isana']
    obs.append({'id': 'isana', 'type': 'talk', 'target': ISANA, 'topic': 'ms_c_key3'})
    key['stages'][0]['objectives'] = obs
    write_json(os.path.join(q, 'main_key.json'), key)
    put('quest.skyabyss.main_key.show.isana', 'イサナにも、鍵を見せよう', 'Show Isana the key too')

    d = os.path.join(RES, 'data/skyabyss/dialogue')
    st = read_json(os.path.join(d, 'storyteller_story.json'))
    n = st['nodes']
    n['ms_key2']['next'] = 'ms_key_four'
    n['ms_key_four'] = {'face': 'grave', 'next': 'ms_key3'}
    n['ms_key3']['next'] = 'ms_key_return'
    n['ms_key_return'] = {'face': 'grave', 'next': 'ms_key4'}
    write_json(os.path.join(d, 'storyteller_story.json'), st)
    s_ = 'dialogue.skyabyss.storyteller.'
    put(s_ + 'ms_key_four', '数え歌のよっつめは、よるにはつきがくる、だったね。\nひとつ、ふたつ、みっつ、よっつ。巨神が一つ眠るたびに、あたしは一行ずつ思い出してきた。',
        'The fourth line of the counting song was "four, at night the moon will come".\nOne, two, three, four. Each time a titan slept, I remembered one more line.')
    put(s_ + 'ms_key_return', '鍵が下を向くなら、下へ返しにお行き。\n四つの背が預かっていた鼓動を、もとの場所へ。……借りたものは、返すものだよ。',
        'If the key points down, then go down and give it back.\nThe heartbeats the four backs held in trust, back to where they came from. ...What is borrowed is given back.')

    wc = read_json(os.path.join(d, 'whale_chaser_story.json'))
    n = wc['nodes']
    n['ms_c_key2'] = {'face': 'neutral', 'next': 'ms_c_key3'}
    n['ms_c_key3'] = {'face': 'grave', 'options': [{'id': 'ok', 'goto': 'end', 'do': [{'type': 'affinity', 'amount': 2}]}]}
    write_json(os.path.join(d, 'whale_chaser_story.json'), wc)
    c_ = 'dialogue.skyabyss.whale_chaser.'
    put(c_ + 'ms_c_key3', 'ばあさんは、返せと言っただろう。\n……俺には分からない。巨神は眠るだけだと言う。だが、眠らせてよかったのか。あいつらは、ずっと誰かの言いつけを守っていたのに。',
        'The old woman told you to give it back, didn\'t she.\n...I don\'t know. They say the titans only sleep. But was it right to put them to sleep? They kept someone\'s orders all this time.')
    put(c_ + 'ms_c_key3.ok', '……分からない。', '...I don\'t know either.')


# 看板の幅 (SignFit と同じ表)
ASCII_WIDTHS = "2466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247"


def width(t):
    w = 0
    for c in t:
        o = ord(c)
        w += 4 if c == ' ' else (int(ASCII_WIDTHS[o - 33]) if 32 < o < 127 else 9)
    return w


def main():
    counts = {'ash_mark': ash_mark(), 'dead_forge_pit': dead_forge_pit(), 'warm_rocks': warm_rocks(), 'soot_kiln': soot_kiln(), 'ash_watch': ash_watch()}
    quests()
    dialogue()
    lore()
    recipes()
    texts()
    story_patch()
    assert set(ja) == set(en)
    bad = []
    for k in ja:
        if k.startswith('sign.'):
            for lang, t in (('ja', ja[k]), ('en', en[k])):
                if width(t) > 90:
                    bad.append('%s (%s) %d px: %s' % (k, lang, width(t), t))
            if len(ja[k]) > 8:
                bad.append('%s (ja) %d chars: %s' % (k, len(ja[k]), ja[k]))
    # 四章の文の名前空間 skyabyss_ch4 に混ぜる (gen_ch4_content.py も、自分の持たないキーは残して書く)
    for code, table in (('ja_jp', ja), ('en_us', en)):
        path = os.path.join(RES, 'assets/skyabyss_ch4/lang/%s.json' % code)
        with open(path, encoding='utf-8') as f:
            merged = json.load(f)
        merged.update(table)
        write_json(path, dict(sorted(merged.items())))
    print('wrote ch4 ground: blocks %s, %d texts; sign problems: %s' % (counts, len(ja), bad or 'none'))
    if bad:
        sys.exit(1)


if __name__ == '__main__':
    main()
