#!/usr/bin/env python3
"""三章の地上 (docs/design/34 の 4.4・C3、21 の 5.1) の部品・クエスト・会話・伝承・文を書き出す。

  PYTHONIOENCODING=utf-8 python -I tools/gen_ch3_ground.py

書くもの:
  data/skyabyss/opening/ch3_rod_tower.json        避雷の塔の跡 (折れた塔・銅の戸の小部屋・針を立てる台 4・避雷の見本の柱)。旧い ch3_ruin の後継
  data/skyabyss/opening/ch3_wind_flag.json        風の道の旗 (5 つ。稜線に沿って)
  data/skyabyss/opening/ch3_scorched_watch.json   焼けた見張り塔 (小さなダンジョン。入口の間・中の間・頂の間 + 職人の野営)
  data/skyabyss/opening/ch3_gleaner_field.json    雷拾いの原 (焼けた荷と、硝子のように固まった砂)
  data/skyabyss/opening/ch3_wind_cliff.json       風読みの崖 (岩の舞台と遠眼鏡の樽)
  data/skyabyss/quest/ch3/lightning_pillars|scorched_watch|sky_stone.json   支度の段 3 本 (メインの段。順不同)
  data/skyabyss/quest/ch3/wind_flags.json         サブ「風の道の旗」
  data/skyabyss/dialogue/ch3/{storyteller,whale_chaser,artisan}_ground.json   足し書き (節は ch3g_ で始める)
  data/skyabyss/lore/entry/ch3/*.json             伝承 4 (風の民の柱・留まる者は、また来た・遅れて映る影・焼けた見張り塔)
  assets/skyabyss_ch3/lang/{ja_jp,en_us}.json     上の文すべて (三章の文に混ぜる。キー順)

語りの決まり (34 の 2・7、01): 前の私の正体は言わない (三章は「前にもここへ来た者がいる」まで。字が似ている、影が遅れる)。
目標はバニラの品と行為 (銅と避雷針・ダイヤ)。理由は物語。チャットに流さない (着いたときの一行と、出来事の一行だけ)。
経験値 (新しい分。ChapterXpBudget の三章 900 の枠): 避雷の柱 50+100、焼けた見張り塔 40+80 (+ 塔のクリア 60)、空の石 120、風の道の旗 120 = 570。
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
CH = 'titan_storm'
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


SB = 'minecraft:stone_bricks'
MSB = 'minecraft:mossy_stone_bricks'
CSB = 'minecraft:cracked_stone_bricks'
CHSB = 'minecraft:chiseled_stone_bricks'
SLAB = 'minecraft:stone_brick_slab[type=bottom,waterlogged=false]'
TOPSLAB = 'minecraft:stone_brick_slab[type=top,waterlogged=false]'
ROD = 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]'
BS = 'minecraft:blackstone'
PBB = 'minecraft:polished_blackstone_bricks'
CPBB = 'minecraft:cracked_polished_blackstone_bricks'
BASALT = 'minecraft:basalt[axis=y]'
COAL = 'minecraft:coal_block'
GRAVEL = 'minecraft:gravel'
COPPER_DOOR = 'minecraft:waxed_oxidized_cut_copper'
PLINTH = 'minecraft:waxed_chiseled_copper'
CUT_COPPER = 'minecraft:waxed_cut_copper'
BARREL = 'minecraft:barrel[facing=up,open=false]'
BARREL_SIDE = 'minecraft:barrel[facing=east,open=false]'
LANTERN = 'minecraft:lantern[hanging=true,waterlogged=false]'
LANTERN_UP = 'minecraft:lantern[hanging=false,waterlogged=false]'
S_ROD = 'skyabyss:storm_rod'
S_BULB = 'skyabyss:storm_rod_bulb[lit=false]'
S_SHAFT = 'skyabyss:storm_rod_shaft'
S_TIP = 'skyabyss:storm_rod_tip'
AIR = 'minecraft:air'


def stair(facing, half='bottom', block='stone_brick_stairs'):
    return 'minecraft:%s[facing=%s,half=%s,shape=straight,waterlogged=false]' % (block, facing, half)


def rough(rng, burnt=0.0):
    r = rng.random()
    if r < burnt:
        return rng.choice([BS, PBB, CPBB])
    r = rng.random()
    return SB if r < 0.55 else MSB if r < 0.8 else CSB


# ================================================================ 部品
# 避雷の塔の跡: 折れた塔の根 (7x7。中は銅の戸で閉じた小部屋) と屋根の上に残った針 1 本、塔のまわりの斜めの 4 か所に針を立てる台、東の隣に避雷の見本の柱。
#   台は 石レンガ + 銅の飾り台 (waxed_chiseled_copper)。針 (バニラの避雷針) は台の上 (mark pedestal0..3) に立てる。
#   戸は南の壁の 3x3 (mark door0..8。緑の銅)。中に石の箱 (樽。mark vault_pack) と碑 (mark vault_tablet)
#   見本の柱は skyabyss:storm_rod の見かけ (台・電球・軸・先)。雷が落ちると電球が光る (mark sample_bulb・sample_tip)
def rod_tower():
    bp = BP('ch3_rod_tower', [-9, -9, 9, 9], ring=2)
    rng = random.Random(3301)
    # 足もとの敷石 (半径 8 の円に、欠けたところと砂利)
    for x in range(-8, 9):
        for z in range(-8, 9):
            d = x * x + z * z
            if d <= 64:
                r = rng.random()
                if d > 36 and r < 0.35:
                    continue
                bp.set(x, -1, z, GRAVEL if r < 0.15 else rough(rng))
    # 塔の根: 壁 (x,z = ±3) の高さ 0..4 (中は 0..3 の高さ 4)、屋根 y4。壁の上は崩れて段違い
    for x in range(-3, 4):
        for z in range(-3, 4):
            edge = abs(x) == 3 or abs(z) == 3
            if edge:
                for y in range(0, 5):
                    bp.set(x, y, z, rough(rng))
                top = 5 + (rng.random() < 0.55) + (rng.random() < 0.35) + (abs(x) == 3 and abs(z) == 3)
                for y in range(5, top):
                    bp.set(x, y, z, rough(rng))
            else:
                bp.set(x, 4, z, SB if (x + z) % 3 else CSB)
    # 屋根の上に残った針 1 本 (mark rod)
    bp.set(0, 5, 0, CHSB)
    for y in range(6, 10):
        bp.set(0, y, 0, ROD)
    bp.mark('rod', 0, 9, 0)
    # 小部屋の中: 床は石レンガ、奥の壁ぎわに石の箱 (樽)・碑・灯り
    for x in range(-2, 3):
        for z in range(-2, 3):
            bp.set(x, -1, z, SB)
    bp.set(0, 0, -2, BARREL)
    bp.mark('vault_pack', 0, 0, -2)
    bp.mark('vault_tablet', -2, 0, -2)
    bp.set(2, 0, -2, CUT_COPPER)
    bp.set(2, 1, -2, LANTERN_UP)
    bp.set(-2, 0, 2, SLAB)
    # 戸 (南の壁 z=3 の x -1..1・y 0..2)
    k = 0
    for y in range(0, 3):
        for x in range(-1, 2):
            bp.set(x, y, 3, COPPER_DOOR)
            bp.mark('door%d' % k, x, y, 3)
            k += 1
    # 戸の前の段
    bp.set(-1, -1, 4, CHSB)
    bp.set(0, -1, 4, CHSB)
    bp.set(1, -1, 4, CHSB)
    # 針を立てる台 4 (斜め)。台の下に棒の刻み (四本ずつ束ねた刻みのつもりの石の縁)
    for i, (x, z) in enumerate(((-6, -6), (6, -6), (6, 6), (-6, 6))):
        bp.set(x, -1, z, CHSB)
        bp.set(x, 0, z, SB)
        bp.set(x, 1, z, PLINTH)
        bp.mark('pedestal%d' % i, x, 2, z)
        sx = 1 if x < 0 else -1
        sz = 1 if z < 0 else -1
        bp.set(x + sx, 0, z, SLAB)
        bp.set(x, 0, z + sz, SLAB)
    # 避雷の見本 (東の隣。見かけは背中の避雷の柱)
    bp.set(8, -1, 0, CHSB)
    bp.set(8, 0, 0, S_ROD)
    bp.set(8, 1, 0, S_BULB)
    bp.set(8, 2, 0, S_SHAFT)
    bp.set(8, 3, 0, S_TIP)
    bp.mark('sample_bulb', 8, 1, 0)
    bp.mark('sample_tip', 8, 3, 0)
    # 崩れた石の転がり
    for (x, z) in ((-5, 1), (4, -5), (-1, 7), (5, 3), (-7, -2)):
        bp.set(x, 0, z, rng.choice([SLAB, CSB, MSB]))
    bp.mark('stand', 0, 0, 6)
    return save(bp, '三章 避雷の塔の跡 (34 の 4.4 避雷の柱)。tools/gen_ch3_ground.py が書く')


# 風の道の旗: 積み石の上の丸石の塀の柱、銅の細線の飾り、てっぺんに旗 (旗の向きは LightningPillars ではなく Ch3Ground が風下へ向ける)
def wind_flag():
    bp = BP('ch3_wind_flag', [-2, -2, 2, 2], ring=1)
    for (x, z) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        bp.set(x, 0, z, 'minecraft:cobblestone_slab[type=bottom,waterlogged=false]')
    bp.set(0, 0, 0, 'minecraft:mossy_cobblestone')
    for y in range(1, 4):
        bp.set(0, y, 0, 'minecraft:cobblestone_wall')
    bp.set(0, 4, 0, CUT_COPPER)
    bp.set(0, 5, 0, 'minecraft:light_blue_banner[rotation=0]')
    bp.mark('banner', 0, 5, 0)
    bp.mark('stand', 0, 0, 2)
    return save(bp, '三章 風の道の旗 (21 の 5.1)。tools/gen_ch3_ground.py が書く')


# 焼けた見張り塔: 外 9x9 (x,z = ±4)・中 7x7。入口の間 (床 y -1、高さ 0..4)・中の間 (床 y 5、高さ 6..10)・頂の間 (床 y 11、胸壁 y 12)。
#   上り下りは北東の隅の梯子 (x 3, z -3。y 0..11 の穴)。頂のまんなかに柱 (storm_rod の見かけ。mark pillar_base・pillar_bulb・pillar_tip)。
#   北の面に一筋だけ青く焼けた石 (cyan_terracotta)。入口は南の面のまんなか。入口の脇に碑 (mark tablet)。
#   南西の外に旅の職人の野営 (mark artisan)。湧き台の立ち位置は ScorchedWatch.pads と同じ (入口 y 0、中 y 6、頂 y 12)
def scorched_watch():
    bp = BP('ch3_scorched_watch', [-9, -9, 9, 9], ring=2)
    rng = random.Random(3302)
    # 焦げた地面
    for x in range(-8, 9):
        for z in range(-8, 9):
            d = x * x + z * z
            if 25 <= d <= 72 and rng.random() < 0.35:
                bp.set(x, -1, z, rng.choice([BS, BASALT, COAL, 'minecraft:coarse_dirt']))
    for x in range(-4, 5):
        for z in range(-4, 5):
            edge = abs(x) == 4 or abs(z) == 4
            # 床 (入口の間)
            bp.set(x, -1, z, rough(rng, 0.25) if edge else rng.choice([SB, SB, CSB, BS]))
            if edge:
                for y in range(0, 13):
                    burnt = 0.15 + 0.05 * (y // 3)
                    bp.set(x, y, z, rough(rng, burnt))
            else:
                bp.set(x, 5, z, rng.choice([SB, SB, CSB, PBB]))
                bp.set(x, 11, z, rng.choice([SB, PBB, PBB, CPBB]))
    # 胸壁 (y 12 の角と一つおき。y 13 に凸)
    for x in range(-4, 5):
        for z in range(-4, 5):
            if abs(x) == 4 or abs(z) == 4:
                if (x + z) % 2 == 0:
                    bp.set(x, 13, z, rough(rng, 0.4))
    # 一筋だけ青く焼けた石 (北の面 z=-4 の x=1、y 0..12)
    for y in range(0, 13):
        bp.set(1, y, -4, 'minecraft:cyan_terracotta')
    # 入口 (南の面のまんなか。幅 1・高さ 2) と上の小さな庇
    bp.set(0, 0, 4, AIR)
    bp.set(0, 1, 4, AIR)
    bp.set(0, 2, 4, CHSB)
    bp.set(0, 3, 5, stair('south', 'top'))
    # 窓 (矢狭間)
    for (x, y, z) in ((-4, 2, 0), (4, 2, 1), (0, 8, -4), (-4, 8, -1), (4, 8, 2), (-2, 8, 4)):
        bp.set(x, y, z, AIR)
        bp.set(x, y + 1, z, AIR)
    # 梯子 (北東の隅。東の壁 x=4 に掛ける)。床の穴 y 5・11
    for y in range(0, 12):
        bp.set(3, y, -3, 'minecraft:ladder[facing=west,waterlogged=false]')
    # 灯り (入口の間・中の間の天井から)
    bp.set(-2, 4, -2, LANTERN)
    bp.set(2, 4, 2, LANTERN)
    bp.set(0, 10, 0, LANTERN)
    bp.set(-2, 10, 2, LANTERN)
    # 入口の間: 崩れた棚と樽 (焦げ)
    bp.set(-3, 0, 3, BARREL_SIDE)
    bp.set(-3, 0, 2, 'minecraft:dark_oak_slab[type=bottom,waterlogged=false]')
    bp.set(3, 0, 3, COAL)
    # 中の間: 避雷の柱の見本 (台と短い針) ― 毛玉の連鎖の予習
    bp.set(-2, 6, -2, PLINTH)
    bp.set(-2, 7, -2, ROD)
    # 頂の間: 柱 (まんなか)
    bp.set(0, 12, 0, S_ROD)
    bp.set(0, 13, 0, S_BULB)
    bp.set(0, 14, 0, S_SHAFT)
    bp.set(0, 15, 0, S_TIP)
    bp.mark('pillar_base', 0, 12, 0)
    bp.mark('pillar_bulb', 0, 13, 0)
    bp.mark('pillar_tip', 0, 15, 0)
    # 入口の碑 (外、入口の東)
    bp.mark('tablet', 2, 0, 5)
    bp.set(2, -1, 5, CHSB)
    # 職人の野営 (南西の外)
    for (x, z) in ((-7, 6), (-6, 6), (-7, 7)):
        bp.set(x, -1, z, 'minecraft:coarse_dirt')
    bp.set(-7, 0, 5, 'minecraft:campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]')
    bp.set(-8, 0, 7, BARREL_SIDE)
    bp.set(-8, 1, 7, 'minecraft:white_carpet')
    bp.mark('artisan', -6, 0, 7)
    bp.mark('stand', 0, 0, 6)
    return save(bp, '三章 焼けた見張り塔 (21 の 5.1。小さなダンジョン)。tools/gen_ch3_ground.py が書く')


# 雷拾いの原: 硝子のように固まった砂の跡 (雷の落ちた所)、焼けた荷の散らばり、拾い残しの箱 (mark cache)
def gleaner_field():
    bp = BP('ch3_gleaner_field', [-7, -7, 7, 7], ring=2)
    rng = random.Random(3303)
    for (cx, cz) in ((-4, -3), (3, 2), (-1, 5)):
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                d = (x - cx) ** 2 + (z - cz) ** 2
                if d <= 4 and rng.random() < 0.85:
                    bp.set(x, -1, z, rng.choice(['minecraft:sand', 'minecraft:sand', BS, BASALT]))
        bp.set(cx, -1, cz, 'minecraft:tinted_glass')
        bp.set(cx, 0, cz, 'minecraft:glass')
    # 焼けた荷 (割れた木箱と樽、焦げた板)
    bp.set(1, 0, -3, BARREL_SIDE)
    bp.set(2, 0, -3, 'minecraft:dark_oak_planks')
    bp.set(2, 1, -3, 'minecraft:dark_oak_slab[type=bottom,waterlogged=false]')
    bp.set(-5, 0, 3, 'minecraft:stripped_dark_oak_log[axis=x]')
    bp.set(-4, 0, 3, 'minecraft:stripped_dark_oak_log[axis=x]')
    bp.set(5, 0, -1, COAL)
    bp.set(0, 0, 0, BARREL)
    bp.mark('cache', 0, 0, 0)
    bp.set(4, 0, 5, 'minecraft:dark_oak_fence')
    bp.mark('stand', 0, 0, 3)
    return save(bp, '三章 雷拾いの原 (21 の 5.1)。tools/gen_ch3_ground.py が書く')


# 風読みの崖: 岩の舞台 (安山岩・凝灰岩)、石の腰掛け、遠眼鏡の樽 (mark spyglass)、碑 (mark tablet)
def wind_cliff():
    bp = BP('ch3_wind_cliff', [-4, -4, 4, 4], ring=2)
    rng = random.Random(3304)
    for x in range(-3, 4):
        for z in range(-3, 4):
            if abs(x) + abs(z) <= 5:
                bp.set(x, -1, z, rng.choice(['minecraft:polished_andesite', 'minecraft:andesite', 'minecraft:tuff', 'minecraft:polished_tuff']))
    for x in (-1, 0, 1):
        bp.set(x, 0, 2, stair('north', block='andesite_stairs'))
    bp.set(2, 0, -2, BARREL)
    bp.mark('spyglass', 2, 0, -2)
    bp.mark('tablet', -2, 0, -2)
    bp.set(-2, -1, -2, 'minecraft:chiseled_tuff')
    bp.set(3, 0, 1, 'minecraft:tuff_wall')
    bp.set(-3, 0, 1, 'minecraft:tuff_wall')
    bp.mark('stand', 0, 0, 1)
    return save(bp, '三章 風読みの崖 (21 の 5.1)。tools/gen_ch3_ground.py が書く')


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


# 三本とも、三章 (段階 2) の人に、メイン「嵐を渡る翼」が始まったら出る (語り部の祖母の話のあと)
# 支度の段は一本ずつ順に出る (2026-10-22 の試遊: メインが並ぶと迷う)。
#   避雷の柱       三章 (段階 2) の人が、語り部に三つ目のことを尋ねたら (main_thunder の rumor のあと)
#   焼けた見張り塔 避雷の柱を終えたら (語り部が職人を指す)
#   空の石         焼けた見張り塔を終えたら (職人がイサナを指す)
START = {'type': 'all', 'of': [{'type': 'stage', 'min': 2, 'max': 2}, quest('main_thunder', 'started'),
                               {'type': 'not', 'of': quest('main_thunder', 'active', 'rumor')}]}


def after(q):
    return {'type': 'all', 'of': [{'type': 'stage', 'min': 2, 'max': 2}, quest(q, 'done')]}


def quests():
    q = os.path.join(RES, 'data/skyabyss/quest/ch3')
    write_json(os.path.join(q, 'lightning_pillars.json'), {
        'category': 'main', 'order': 31, 'giver': STORYTELLER, 'chapter': CH, 'start_when': START,
        'stages': [
            # 避雷の塔の跡の台 4 つに避雷針を立てる (LightningPillars が、台に針が立っているのを見て、人ごとに台一つにつき一度 ch3_pillar を送る)
            {'id': 'raise', 'xp': 50, 'objectives': [{'id': 'rods', 'type': 'signal', 'target': 'ch3_pillar', 'count': 4, 'party': False}]},
            # 四本目で塔の戸が開く。奥の石の箱で風の民の書 (伝承 ch3/stayer_returns)
            {'id': 'vault', 'objectives': [{'id': 'page', 'type': 'lore', 'targets': ['ch3/stayer_returns']}]},
        ],
        'reward': {'xp': 100, 'items': [], 'affinity': {STORYTELLER: 3}},
    })
    write_json(os.path.join(q, 'scorched_watch.json'), {
        'category': 'main', 'order': 32, 'giver': ARTISAN, 'chapter': CH, 'start_when': after('ch3/lightning_pillars'),
        'stages': [
            # 焼けた見張り塔を抜けると、頂の柱の根もとに焦げた頁 (ScorchedWatch が伝承 ch3/scorched_watch を記す。先に抜けた人もさかのぼって済む)
            {'id': 'clear', 'xp': 40, 'objectives': [{'id': 'page', 'type': 'lore', 'targets': ['ch3/scorched_watch']}]},
            {'id': 'show', 'objectives': [{'id': 'talk', 'type': 'talk', 'target': ARTISAN, 'topic': 'ch3g_watch_show'}]},
        ],
        'reward': {'xp': 80, 'items': [], 'affinity': {ARTISAN: 3}},
    })
    write_json(os.path.join(q, 'sky_stone.json'), {
        'category': 'main', 'order': 33, 'giver': ISANA, 'chapter': CH, 'start_when': after('ch3/scorched_watch'),
        'stages': [
            # ダイヤ 5、またはダイヤの胸当て・剣のどちらか (着ていても数える)
            {'id': 'dig', 'mode': 'any', 'objectives': [
                {'id': 'diamonds', 'type': 'collect', 'target': 'minecraft:diamond', 'count': 5, 'party': False},
                {'id': 'chest', 'type': 'collect', 'target': 'minecraft:diamond_chestplate', 'count': 1, 'party': False},
                {'id': 'sword', 'type': 'collect', 'target': 'minecraft:diamond_sword', 'count': 1, 'party': False}]},
        ],
        'reward': {'xp': 120, 'items': [], 'affinity': {ISANA: 3}},
    })
    write_json(os.path.join(q, 'wind_flags.json'), {
        'category': 'sub', 'order': 45, 'giver': STORYTELLER, 'chapter': CH, 'min_stage': 2,
        'start_when': {'type': 'stage', 'min': 2, 'max': 2},
        'stages': [
            # 旗 5 つ (着いたとき、人ごとに旗一つにつき一度 ch3_flag)
            {'id': 'trace', 'objectives': [{'id': 'flags', 'type': 'signal', 'target': 'ch3_flag', 'count': 5, 'party': False}]},
        ],
        'reward': {'xp': 120},
    })

    b = 'quest.skyabyss.ch3.lightning_pillars.'
    put(b + 'title', '避雷の柱', 'The Lightning Pillars')
    put(b + 'summary', '嵐を渡る支度の一つめ。風の民の塔の跡に、銅の針を四本立てる。', 'The first step in getting ready for the storm. Raise four copper rods at the ruin of the wind people\'s tower.')
    put(b + 'raise.lead', '語り部「嵐へ行くなら、雷を知っておくことだね。風の民は、銅の柱で雷を逃がしたそうだよ」',
        'The storyteller: "If you mean to go into the storm, learn the lightning first. The wind people let it down with copper pillars."')
    put(b + 'raise.journal',
        '語り部が言っていた。「風の民は、銅の柱で雷を地へ逃がしたそうだよ。峰の塔の跡に、柱を立てる台が四つ残っているはずさ」\n銅の鉱石は、かまどで溶かせば銅になる。銅三つで、避雷針が一本できる。',
        'The storyteller said: "The wind people let the lightning down into the ground with copper pillars. There should be four stands left at the ruined tower on the peak."\nCopper ore smelts into copper in a furnace. Three copper make one lightning rod.')
    put(b + 'raise.rods', '避雷針を作り、塔の跡の台に立てよう', "Set rods on the ruin's stands")
    put(b + 'vault.journal', '四本目の針に雷が落ちると、塔の銅の戸がずれた。中を見よう。',
        'When the fourth rod took the lightning, the tower\'s copper door slid aside. Look inside.')
    put(b + 'vault.page', '開いた戸の奥を見に行こう', 'Look past the open door')
    put(b + 'done', '「四本、立ったかい。……祖母が言っていたとおりだね。雷は、立てた所にしか落ちないんだよ」\n次は、雷の中を抜ける身のこなしだ。旅の職人が、焼けた塔の話をしていた。',
        '"Four, standing? ...Just as grandmother said. Lightning falls only where something is raised for it."\nNext, learning to move through lightning. The traveling artisan spoke of a burnt tower.')

    b = 'quest.skyabyss.ch3.scorched_watch.'
    put(b + 'title', '焼けた見張り塔', 'The Scorched Watch')
    put(b + 'summary', '嵐を渡る支度の二つめ。峰の中腹の焼けた塔を抜け、頂の番を倒す。', 'The second step in getting ready. Climb the scorched tower on the peak\'s slope and fell the warden at the top.')
    put(b + 'clear.lead', '職人「峰の中腹に、焼けた塔がある。頂の番は、雷に斬らせるといい」',
        'The artisan: "There is a burnt tower halfway up the peak. Let the lightning cut the warden on top."')
    put(b + 'show.lead', '頂の柱の根もとに、焦げた頁が落ちていた。職人に見せよう。', 'A scorched page lay at the foot of the top pillar. Show it to the artisan.')
    put(b + 'clear.journal',
        '旅の職人が言っていた。「峰の中腹に、焼けた塔がある。頂の柱に雷が落ちるたび、番の羽守が焦げていく。……柱のそばに誘え。雷が、代わりに斬ってくれる」\n中の間の毛玉は、固まって立つと雷が渡る。離れて立て。',
        'The traveling artisan said: "There is a burnt tower halfway up the peak. Every time lightning hits the pillar on top, the warden there burns a little more. ...Lure it to the pillar. The lightning will cut for you."\nIn the middle room, lightning jumps between people who stand together. Spread out.')
    put(b + 'clear.page', '焼けた見張り塔の頂の番を倒そう', 'Fell the warden atop the Scorched Watch')
    put(b + 'show.journal', '頂の柱の根もとで、焦げた頁を拾った。旅の職人に見せよう。', 'At the foot of the top pillar you picked up a scorched page. Show it to the traveling artisan.')
    put(b + 'show.talk', '焦げた頁を、旅の職人に見せよう', 'Show the scorched page to the artisan')
    put(b + 'done', '「雷に焼かれても、字は残るもんだな。……道具も、そういうものだ」\n残るは、雷を通さない石だ。鯨追いが、峰の下の石の話をしていた。',
        '"Burnt by lightning, and still the writing stays. ...Tools are like that too."\nAll that is left is the stone lightning cannot pass. The whale chaser spoke of it.')

    b = 'quest.skyabyss.ch3.sky_stone.'
    put(b + 'title', '空の石', 'Sky Stone')
    put(b + 'summary', '嵐を渡る支度の三つめ。峰の下の、雷を通さない石を掘る。', 'The last step in getting ready. Dig the stone beneath the peaks that lightning cannot pass.')
    put(b + 'dig.lead', 'イサナ「峰の下の石は、雷を通さない。前の旅人は、それを胸に着けていた」',
        'Isana: "The stone under the peaks does not let lightning through. The traveller before you wore it on their chest."')
    put(b + 'dig.journal',
        'イサナが言っていた。「峰の下の石は、雷を通さない。前の旅人は、それを胸に着けていた」\nダイヤモンドは、深い所の岩の中にある。鉄のつるはしでなければ掘れない。五つあれば、胸当てか剣になる。',
        'Isana said: "The stone under the peaks does not let lightning through. The traveller before you wore it on their chest."\nDiamonds lie in the rock deep down, and only an iron pickaxe will dig them. Five make a chestplate or a sword.')
    put(b + 'dig.diamonds', '雷を通さない石、ダイヤモンドを集めよう', 'Gather diamonds, the stone lightning cannot pass')
    put(b + 'dig.chest', 'ダイヤモンドの胸当てを持とう', 'Have a diamond chestplate')
    put(b + 'dig.sword', 'ダイヤモンドの剣を持とう', 'Have a diamond sword')
    put(b + 'done', '「深い所の石だな。……雷の鳥も、これは噛めないだろう」\nこれで、嵐を渡る支度がそろった。',
        '"Stone from deep down. ...Even the thunder bird cannot bite through that."\nYou are ready to cross the storm.')

    b = 'quest.skyabyss.ch3.wind_flags.'
    put(b + 'title', '風の道の旗', 'Flags of the Wind Road')
    put(b + 'summary', '峰の稜線に立つ旗を、五つ辿る。', 'Follow the five flags along the ridge.')
    put(b + 'trace.journal', '峰の稜線に、銅の細線を巻いた旗の柱が並んでいるらしい。旗は、風の向きを示している。',
        'Flag posts wound with fine copper wire are said to line the ridge. The flags show which way the wind blows.')
    put(b + 'trace.flags', '風の道の旗を辿る', 'Follow the flags of the wind road')
    put(b + 'done', '旗は、いまも風に逆らって鳴っている。', 'The flags still snap against the wind.')


# ================================================================ 会話
def dialogue():
    d = os.path.join(RES, 'data/skyabyss/dialogue/ch3')
    write_json(os.path.join(d, 'storyteller_ground.json'), {
        'npc': STORYTELLER, 'extends': True,
        'entries': [
            {'node': 'ch3g_pillars_tale', 'if': [quest('ch3/lightning_pillars', 'active', 'raise'), not_(flag('ch3g_pillars_told'))]},
            {'node': 'ch3g_s_verse', 'if': [quest('main_thunder', 'done'), not_(flag('ch3g_verse_told'))]},
        ],
        'nodes': {
            'ch3g_pillars_tale': {'face': 'grave', 'do': [flag('ch3g_pillars_told')], 'next': 'ch3g_pillars_tale2'},
            'ch3g_pillars_tale2': {'face': 'neutral', 'next': 'menu'},
            'ch3g_s_verse': {'face': 'grave', 'do': [flag('ch3g_verse_told'), {'type': 'affinity', 'amount': 1}], 'next': 'menu'},
        },
    })
    s = 'dialogue.skyabyss.storyteller.'
    put(s + 'ch3g_pillars_tale',
        '祖母の話の続きだよ。風の民は、嵐の鳥を空の影だと思っていたそうだ。……それでも、雷のことはよく知っていた。\n銅の柱を立てて、雷を地へ逃がしたんだとさ。峰の塔の跡に、柱を立てる台が残っているはずだよ。四つ。祖母は、四つと言った。',
        'This is the rest of grandmother\'s story. The wind people took the storm bird for the sky\'s shadow. ...Even so, they knew lightning well.\nThey raised copper pillars and let it down into the earth. There should still be stands for them at the ruined tower on the peak. Four. Grandmother said four.')
    put(s + 'ch3g_pillars_tale2',
        '台を数えるとき、祖母はいつも指を折った。四本目で、手を握るんだよ。……雷が落ちる前に、耳をふさぐみたいにね。',
        'When she counted the stands, grandmother folded her fingers. At the fourth she made a fist. ...Like covering your ears before the thunder.')
    put(s + 'ch3g_s_verse',
        '……みっつ　みあげちゃだめよ。\n子どもらの歌の三つめだよ。見上げるなと言うのは、鳥のことじゃなかったのかもしれないね。あの鳥でさえ、顔を伏せていたんだろう？',
        '...Three, do not look up.\nThe third line of the children\'s song. Maybe "do not look up" was never about the bird. Even that bird hid its face, didn\'t it?')

    write_json(os.path.join(d, 'whale_chaser_ground.json'), {
        'npc': ISANA, 'extends': True,
        'entries': [
            {'node': 'ch3g_c_stay', 'if': [quest('main_thunder', 'done'), not_(flag('ch3g_stay_told'))]},
            {'node': 'ch3g_stone_offer', 'if': [quest('ch3/sky_stone', 'active', 'dig'), not_(flag('ch3g_stone_told'))]},
        ],
        'nodes': {
            'ch3g_stone_offer': {'face': 'neutral', 'do': [flag('ch3g_stone_told')], 'next': 'c_menu'},
            'ch3g_c_stay': {'face': 'grave', 'next': 'ch3g_c_stay2'},
            'ch3g_c_stay2': {'face': 'smile', 'do': [flag('ch3g_stay_told'), {'type': 'affinity', 'amount': 2},
                                                      {'type': 'give', 'item': 'skyabyss:warm_stone', 'count': 1}], 'next': 'c_menu'},
        },
    })
    c = 'dialogue.skyabyss.whale_chaser.'
    put(c + 'ch3g_stone_offer',
        '峰の下の石は、雷を通さない。……祖父の口癖だ。\n前の旅人は、その石を胸に着けていた、と言う奴がいた。深い所の石だ。鉄のつるはしで掘れ。五つあれば足りる。',
        'The stone under the peaks does not let lightning through. ...My grandfather\'s saying.\nSomeone told me the traveller before you wore that stone on their chest. It lies deep. Dig with an iron pickaxe. Five will do.')
    put(c + 'ch3g_c_stay',
        '三つ、そろったな。\n……決めたよ。俺は残る。誰かが覚えていなければ、あんたが昇っていった空のことも、ここで待っていた火のことも、なかったことになる。',
        'Three of them now.\n...I have decided. I am staying. If no one remembers, the sky you climbed and the fire that waited here will be as if they never were.')
    put(c + 'ch3g_c_stay2',
        'これを持っていけ。温石だ。握ると、ずっと温かい。\n……灰の山の話を聞いたら、思い出してくれ。俺はここで、数えて待っている。',
        'Take this. A warm stone. Hold it and it stays warm.\n...When you hear about the mountain of ash, remember me. I will be here, counting, waiting.')

    write_json(os.path.join(d, 'artisan_ground.json'), {
        'npc': ARTISAN, 'extends': True,
        'entries': [
            {'node': 'ch3g_watch_show', 'if': quest('ch3/scorched_watch', 'active', 'show')},
            {'node': 'ch3g_watch_tell', 'if': [quest('ch3/scorched_watch', 'active', 'clear'), not_(flag('ch3g_watch_told'))]},
        ],
        'nodes': {
            'ch3g_watch_tell': {'face': 'neutral', 'do': [flag('ch3g_watch_told')], 'next': 'a_menu'},
            'ch3g_watch_show': {'face': 'smile', 'do': [{'type': 'affinity', 'amount': 1}], 'next': 'a_menu'},
        },
    })
    a = 'dialogue.skyabyss.artisan.'
    put(a + 'ch3g_watch_tell',
        '峰の中腹の塔を見たか。石が一筋、青く焼けている。\n頂に柱があってな。雷はいつも、あの柱を選ぶ。番の羽守は柱のそばを離れない。……柱の近くへ誘え。雷が、代わりに斬ってくれる。',
        'Seen the tower halfway up the peak? One streak of its stone is burnt blue.\nThere is a pillar on top. Lightning always picks it. The warden there never leaves its side. ...Lure it close. The lightning will cut for you.')
    put(a + 'ch3g_watch_show',
        '焦げた頁か。……この焦げ方は、雷だな。\n道具は覚えている。持ち主は、そうでもない。この頁も、誰かの道具だったんだろう。……大事にしな。',
        'A scorched page. ...That burn is lightning.\nTools remember. Their owners, less so. This page was someone\'s tool too. ...Keep it well.')


# ================================================================ 伝承
def lore():
    entries = [
        ('pillars', 174, None, '風の民の柱', 'Pillars of the Wind People',
         '峰の塔の跡の四つの台に銅の針を立てると、雷が一本ずつ降りてきた。\n\n風の民は、銅の柱で雷を地へ逃がしていたらしい。台の縁には、棒の刻みが四本ずつ束ねて並んでいる。\n\n四つ目の束だけ、刻みが深かった。',
         'When copper rods stood on the four stands at the ruined tower, lightning came down to each, one by one.\n\nThe wind people seem to have let lightning into the earth through copper pillars. Along the stands\' edges, tally marks are bound in fours.\n\nOnly the fourth bundle was cut deeper.'),
        ('stayer_returns', 175, 'sign.skyabyss.ch3_vault', '留まる者は、また来た', 'The Stayer Came Again',
         '塔の跡の銅の戸の奥、石の箱に、風の民の書が一枚あった。\n\n「留まる者は、また来た。前と同じ外套で、前と同じ道を。棒の束は、これで九つ目になる。あの者は、鳥より先に空を見る」\n\n余白に、別の手で一行。「銅を四つ。台の数だけ」\n\nその字は、坑の頁の字に似ていた。',
         'Behind the copper door of the ruined tower, in a stone box, lay one sheet of the wind people\'s writing.\n\n"The stayer came again. The same coat as before, the same road as before. This makes the ninth sheaf of tallies. That one looks to the sky before the bird does."\n\nIn the margin, one line in another hand: "Four copper. As many as the stands."\n\nThe writing looked like the writing on the page in the pit.'),
        ('mirror_lag', 176, None, '遅れて映る影', 'The Shadow That Lagged',
         '眼の間の羽鏡に、自分の姿が映っていた。\n\nこちらが腕を下ろしても、映った影はまだ腕を上げていた。少し遅れて、影も腕を下ろした。\n\nそれから、こちらが向くより先に、影がこちらを向いた。',
         'Your figure showed in a feather mirror in the hall of the eye.\n\nWhen you lowered your arm, the reflection still held it up. A moment later, it lowered its arm too.\n\nThen, before you turned, the reflection turned to face you.'),
        ('scorched_watch', 177, None, '焼けた見張り塔', 'The Scorched Watch',
         '塔の頂の柱は、何度も雷を受けて青く焼けていた。番をしていた羽守の外套も、同じ色に焦げていた。\n\n柱の根もとに、焦げた頁が一枚。風の民の見張りの帳らしい。「雷の数、今日も九。番は動かず」\n\n番をしていたのは、塔だったのか、羽守だったのか。',
         'The pillar atop the tower had taken lightning so many times it was burnt blue. The coat of the warden that kept it was scorched the same colour.\n\nAt the pillar\'s foot lay a scorched page, a watch log of the wind people: "Strikes today, nine again. The warden did not move."\n\nWas it the tower keeping watch, or the warden?'),
    ]
    for name, order, sign, tj, te, bj, be in entries:
        data = {'chapter': CH, 'order': order, 'unlock': [{'type': 'manual'}], 'related': []}
        if sign:
            # 碑は箱と同じ部屋にある。頁は箱で記す (碑を読むと文が出るだけ)
            pass
        write_json(os.path.join(RES, 'data/skyabyss/lore/entry/ch3/%s.json' % name), data)
        put('lore.skyabyss.ch3.%s.title' % name, tj, te)
        put('lore.skyabyss.ch3.%s.body' % name, bj, be)


# ================================================================ 場所の文・碑
def texts():
    m = 'message.skyabyss.ch3g.'
    put(m + 'arrive.ch3_wind_flag', '旗の柱に、銅の細い線が巻いてある。旗は、風下を指している。',
        'Fine copper wire is wound round the flag post. The flag points downwind.')
    put(m + 'flags.turn', '五つ目の旗の前に立つと、峰じゅうの旗が音を立てて、一斉に逆を向いた。風は、変わっていない。',
        'As you stand at the fifth flag, every flag on the peak snaps round at once to face the other way. The wind has not changed.')
    put(m + 'arrive.ch3_scorched_watch', '塔の石が、一筋だけ青く焼けている。', 'One streak of the tower\'s stone is burnt blue.')
    put(m + 'watch.page', '柱の根もとに、焦げた頁が一枚落ちている。', 'A scorched page lies at the foot of the pillar.')
    put(m + 'watch.warn', '柱のまわりの空気が、ぴりぴりと鳴りはじめた。', 'The air round the pillar starts to crackle.')
    put(m + 'arrive.ch3_gleaner_field', '砂が、ところどころ硝子のように固まっている。焼けた荷が散らばり、何かが拾い集めていった跡がある。',
        'In places the sand has fused like glass. Burnt cargo lies scattered, and something has been gathering it up.')
    put(m + 'field.cache', '焼けた箱の底に、銅の塊が残っていた。拾い残しだろうか。', 'At the bottom of a burnt crate, lumps of copper remain. Left behind by whatever gathers here?')
    put(m + 'field.cache_empty', '箱は空だ。新しい爪の痕だけが増えている。', 'The crate is empty. Only the fresh claw marks have grown in number.')
    put(m + 'arrive.ch3_wind_cliff', '崖の先に、岩の舞台が張り出している。ここからなら、嵐の通り道が見える。',
        'A stage of rock juts from the cliff\'s edge. From here you can see the path the storms take.')
    put(m + 'ruin.stands', '折れた塔のまわりに、空の台が四つある。針を立てる穴が開いている。',
        'Four empty stands circle the broken tower, each with a hole for a rod.')
    put(m + 'ruin.strike', '立てた針に、雷が降りてきた。', 'Lightning came down to the rod you raised.')
    put(m + 'ruin.open', '四本目の針に雷が落ちると、塔の銅の戸が、音を立ててずれた。', 'Lightning struck the fourth rod, and the tower\'s copper door slid aside with a grind.')
    put(m + 'ruin.vault', '石の箱に、風の民の書と、銅が四つ。', 'In the stone box: a sheet of the wind people\'s writing, and four copper.')
    put(m + 'ruin.vault_empty', '箱には、書の写しだけが残っている。', 'Only a copy of the writing remains in the box.')
    put(m + 'mirror.1', '磨いた銅の羽に、自分の姿が映っている。', 'Your figure shows in a polished copper feather.')
    put(m + 'mirror.2', '……映った影が、少し遅れて動いた。', '...The reflection moved, a moment late.')
    put(m + 'ready', '峰へ行く支度が、そろった。', 'You are ready for the peaks.')
    put('entity.skyabyss.scorched_warden', '焼けた羽守', 'Scorched Warden')
    for key, lines_ja, lines_en in (
            ('sign.skyabyss.ch3_watch', ['焼けた見張り塔', '雷は 柱を選ぶ', '番は 柱を離れず', '柱は 番を焼く'],
             ['Scorched Watch', 'Bolts seek posts', 'The guard stays', 'The post burns it']),
            ('sign.skyabyss.ch3_vault', ['留まる者は', 'また来た', '棒を 束ねて', '九つ目'],
             ['The stayer', 'came again', 'Tallies bound', 'the ninth']),
            ('sign.skyabyss.ch3_cliff', ['風読みの崖', '嵐は 風に', '逆らって', '西から来る'],
             ['Wind Cliff', 'Storms come', 'against wind', 'from the west'])):
        for i in range(4):
            put('%s.%d' % (key, i + 1), lines_ja[i], lines_en[i])


# 看板の幅 (SignFit と同じ表。gen_ch3_content.py と同じ)
ASCII_WIDTHS = "2466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247"


def width(t):
    w = 0
    for c in t:
        o = ord(c)
        w += 4 if c == ' ' else (int(ASCII_WIDTHS[o - 33]) if 32 < o < 127 else 9)
    return w


def main():
    counts = {'rod_tower': rod_tower(), 'wind_flag': wind_flag(), 'scorched_watch': scorched_watch(),
              'gleaner_field': gleaner_field(), 'wind_cliff': wind_cliff()}
    quests()
    dialogue()
    lore()
    texts()
    assert set(ja) == set(en)
    bad = []
    for k in ja:
        if k.startswith('sign.'):
            for lang, t in (('ja', ja[k]), ('en', en[k])):
                if width(t) > 90:
                    bad.append('%s (%s) %d px: %s' % (k, lang, width(t), t))
            if len(ja[k]) > 8:
                bad.append('%s (ja) %d chars: %s' % (k, len(ja[k]), ja[k]))
    # 三章の文の名前空間 skyabyss_ch3 に混ぜる (gen_ch3_content.py も、自分の持たないキーは残して書く)
    for code, table in (('ja_jp', ja), ('en_us', en)):
        path = os.path.join(RES, 'assets/skyabyss_ch3/lang/%s.json' % code)
        with open(path, encoding='utf-8') as f:
            merged = json.load(f)
        merged.update(table)
        write_json(path, dict(sorted(merged.items())))
    print('wrote ch3 ground: blocks %s, %d texts; sign problems: %s' % (counts, len(ja), bad or 'none'))
    if bad:
        sys.exit(1)


if __name__ == '__main__':
    main()
