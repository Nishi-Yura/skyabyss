#!/usr/bin/env python3
"""二章の地上 (docs/design/34 の 4.3・C2、21 の 4.1) の部品・クエスト・会話・伝承・文を書き出す。

  python tools/gen/ch2/gen_ch2_ground.py

書くもの:
  data/skyabyss/opening/ch2_salt_vault|ch2_salt_mark|ch2_tidal_pool|ch2_drift_raft|ch2_ebb_stakes.json   場所の部品 (tools/gen/ch1/gen_ch1_sites.py と同じ形式)
  data/skyabyss/quest/ch2/salt_road|ebb_boat.json      支度の段 2 本 (メインの段。塩の道 → 船と貝の順。07 の 6.8)
  data/skyabyss/quest/ch2/drift_trail|wader_gear.json  サブ (イサナ: 宿借りの行列と筏の跡 / 職人: 潮渡りの旅装。任意)
  data/skyabyss/quest/daily_pool_sweep.json             日替わり (板): 潮だまり貝 6 と小真珠の納品
  data/skyabyss/dialogue/ch2/storyteller_trail.json|whale_chaser_trail.json|artisan.json   語り部・イサナ・職人の二章の一言
  data/skyabyss/lore/entry/ch2/salt_vault|drift_raft|wreck_page|why_they_rode.json         前の私の刻みと頁、潮の民の頁、村の記憶
  assets/skyabyss_ch2/lang/{ja_jp,en_us}.json            文 (既存のキーはそのまま。新しいキーを後ろに足す)
語り部の報告 (ms_tide_back2 → 村の記憶 → 数え歌のふたつ) は storyteller_trail.json の上書きの節と、tools/gen/story/gen_story_quests.py の節の行き先。

語りの決まり (34 の 1・2・7、21 の 0.2・8): 前の私の正体は言わない (字が似ている、まで)。目標はバニラの品と行為 (船)、理由は物語。
心臓の正体・深淵・眠りの理由は書かない。二章で明かすのは「空が落ちかけた夜、人は巨神の背へ逃れた」まで。
"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../ch1',)]
import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_ch1_blueprints import BP  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
RES = os.path.join(ROOT, 'src', 'main', 'resources')

ISANA = 'skyabyss:whale_chaser'
ARTISAN = 'skyabyss:artisan'
STORYTELLER = 'skyabyss:storyteller'
ja = {}
en = {}


def put(key, j, e):
    ja[key] = j
    en[key] = e


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


SB = 'minecraft:stone_bricks'
MSB = 'minecraft:mossy_stone_bricks'
CSB = 'minecraft:cracked_stone_bricks'
PRB = 'minecraft:prismarine_bricks'
CALCITE = 'minecraft:calcite'
SAND = 'minecraft:sand'
AIR = 'minecraft:air'
WATER = 'minecraft:water[level=0]'
LANTERN = 'minecraft:sea_lantern'
DOOR = 'minecraft:smooth_quartz'


def ladder(facing, wet=False):
    return 'minecraft:ladder[facing=%s,waterlogged=%s]' % (facing, 'true' if wet else 'false')


def stair(facing, block='stone_brick_stairs'):
    return 'minecraft:%s[facing=%s,half=bottom,shape=straight,waterlogged=false]' % (block, facing)


def wall_torch(facing):
    return 'minecraft:wall_torch[facing=%s]' % facing


def salty(rng):
    """塩をかぶった石レンガ (殻)。白い方解石の筋と、潮の青のプリズマリンを少し"""
    r = rng.random()
    if r < 0.50:
        return SB
    if r < 0.70:
        return MSB
    if r < 0.82:
        return CSB
    if r < 0.93:
        return CALCITE
    return PRB


# ---------------------------------------------------------------- 沈んだ塩の蔵 (21 の 4.1。12〜16 分の小さなダンジョン)
class Vault:
    """地下の部屋。inside = 中身 (空気か水) の箱、その 1 まわりを塩の殻で包む (地面から上の殻は keep が真の所だけ)"""

    def __init__(self):
        # 地表の範囲 (ならす所) は坑口の屋根と口のまわりだけ。部屋は地下へ広がる (骨の坑と同じ)
        self.bp = BP('ch2_salt_vault', [-5, -7, 5, 4], ring=2)
        self.rng = random.Random(2204)
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
            bp.set(q[0], q[1], q[2], salty(self.rng))
        for q, st in sorted(self.inside.items()):
            bp.set(q[0], q[1], q[2], st)
        return shell


VAULT_FL = -12  # 蔵の床のブロックの y (敵が立つのは -11)
# 潮の喉 (干潮で空気、満潮で水。SaltVault が世界の潮で切り替える)。部品の座標の箱
SUMP = (-1, VAULT_FL - 3, 13, 1, VAULT_FL, 19)


def salt_vault():
    c = Vault()
    bp = c.bp
    FL = VAULT_FL
    # ---- 階段 (z 0..11、一段ずつ下がる。幅 3)
    for i in range(0, 12):
        c.box(-1, -i, i, 1, -i + 2, i)
    c.box(-1, FL + 1, 12, 1, FL + 3, 14)
    # ---- 潮の喉: 井戸 A (z 13..14) → 水の底の抜け道 (z 15..17。上は塞ぐ) → 井戸 B (z 18..19)。乾いた刻は梯子で下りて歩いて上る
    c.box(-1, FL - 3, 13, 1, FL, 14)
    c.box(-1, FL - 3, 15, 1, FL - 1, 17)
    c.box(-1, FL - 3, 18, 1, FL, 19)
    c.box(-1, FL + 1, 18, 1, FL + 3, 20)
    # ---- 入口の間 (x -6..6, z 21..33)。潮だまり貝 3
    c.box(-6, FL + 1, 21, 6, FL + 4, 33)
    # ---- 抜け道 (入口の間 → 塩樽の間。x 7..10, z 26..28)
    c.box(7, FL + 1, 26, 10, FL + 3, 28)
    # ---- 塩樽の間 (x 11..25, z 21..35)。砂潜り 2 と塩樽 3
    c.box(11, FL + 1, 21, 25, FL + 5, 35)
    # ---- 塩の戸 (x = 26 の 3x3) と奥の間 (x 27..43, z 14..36)。潮の番人
    c.box(26, FL + 1, 27, 26, FL + 3, 29)
    c.box(27, FL + 1, 14, 43, FL + 6, 36)
    # ---- 出口の縦穴 (奥の間の北の壇の上 x 35, z 13 から地上へ)
    c.box(35, FL + 2, 13, 35, -1, 13)
    # 口の覆い: 階段の上の地上の殻 (z 0..3) だけ残す
    shell = c.build(lambda q: -2 <= q[0] <= 2 and 0 <= q[2] <= 3 and q[1] <= 3)

    # ---- 階段と口の覆い (積んだ石の低い屋根。塩で白い)
    for i in range(0, 12):
        for x in (-1, 0, 1):
            bp.set(x, -1 - i, i, stair('north'))
    for q in shell:
        x, y, z = q
        if -2 <= x <= 2 and 0 <= z <= 3 and y >= 0:
            bp.set(x, y, z, CALCITE if (x + y + z) % 3 == 0 else (SB if abs(x) == 2 else 'minecraft:stone_brick_slab[type=bottom,waterlogged=false]' if y == 3 else MSB))
    # 屋根の上の塩の塊と、口の前の割れた敷石
    for x, z in ((-1, 1), (1, 2), (0, 3)):
        bp.set(x, 4, z, 'minecraft:white_concrete_powder' if (x + z) % 2 else CALCITE)
    for x, z in ((-1, -1), (1, -1), (0, -2), (-1, -3), (1, -3)):
        bp.set(x, -1, z, 'minecraft:smooth_sandstone' if (x + z) % 2 else 'minecraft:cut_sandstone')
    # 坑口の碑 (文は sign.skyabyss.ch2_salt_vault)
    bp.set(3, 0, -2, 'skyabyss:tablet[facing=north,standing=true]')
    bp.mark('tablet', 3, 0, -2)
    bp.set(-3, 0, -2, CALCITE)
    bp.set(-3, 1, -2, 'minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]')
    bp.mark('stand', 0, 0, -5)
    bp.mark('mouth', 0, 0, 0)

    # ---- 床
    def floor(x0, z0, x1, z1, state=None):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x, FL, z) in c.inside:
                    continue
                bp.set(x, FL, z, state or (SB if (x * 3 + z) % 5 else MSB))
    floor(-1, 12, 1, 12)
    floor(-1, 20, 1, 20)
    floor(-6, 21, 6, 33, SAND)
    floor(7, 26, 10, 28)
    floor(11, 21, 25, 35, SAND)
    floor(27, 14, 43, 36)
    # ---- 潮の喉: 梯子 (井戸 A は北の壁、井戸 B は南の壁につく)。乾いた刻の状態で書く (SaltVault が満潮で水にする)
    for y in range(FL - 3, FL + 1):
        bp.set(0, y, 13, ladder('south'))
        bp.set(0, y, 19, ladder('north'))
    bp.mark('sump_lo', SUMP[0], SUMP[1], SUMP[2])
    bp.mark('sump_hi', SUMP[3], SUMP[4], SUMP[5])
    # 喉の手前と奥の灯り (水の底は暗い)
    bp.set(-1, FL + 3, 12, 'minecraft:lantern[hanging=true,waterlogged=false]')
    bp.set(1, FL + 3, 20, 'minecraft:lantern[hanging=true,waterlogged=false]')
    bp.set(0, FL - 4, 16, LANTERN)
    # ---- 入口の間: 潮だまり (床の 1 段の水)、塩の筋、海の灯り
    for (x, z) in ((-4, 23), (-3, 23), (-4, 24), (4, 30), (5, 30), (4, 31), (-2, 31), (-1, 31)):
        bp.set(x, FL, z, WATER)
        bp.set(x, FL - 1, z, SAND)
    for (x, z) in ((-5, 22), (5, 25), (-5, 32), (3, 22)):
        bp.set(x, FL + 1, z, 'minecraft:dead_brain_coral[waterlogged=false]' if (x + z) % 2 else 'minecraft:dead_tube_coral[waterlogged=false]')
    for (x, z) in ((-6, 27), (6, 24), (0, 33)):
        bp.set(x, FL + 1, z, 'minecraft:sea_pickle[pickles=2,waterlogged=false]')
    for (x, y, z) in ((-7, FL + 3, 25), (7, FL + 3, 31), (0, FL + 5, 27)):
        bp.set(x, y, z, LANTERN)
    # ---- 塩樽の間: 濡れた南 (塩樽の置き場 3)、乾いた北の棚 (棚の上が樽の置き場 3)
    for x in range(13, 24):
        for z in (21, 22):
            bp.set(x, FL + 1, z, 'minecraft:smooth_sandstone')
    for k, x in enumerate((15, 18, 21)):
        bp.set(x, FL + 1, 21, 'minecraft:chiseled_sandstone')
        bp.mark('shelf%d' % k, x, FL + 2, 21)
    for k, x in enumerate((14, 18, 22)):
        bp.set(x, FL + 1, 34, 'minecraft:barrel[facing=up,open=false]')
        bp.mark('barrel%d' % k, x, FL + 1, 34)
    for (x, z) in ((12, 33), (13, 35), (16, 34), (20, 33), (24, 35), (25, 34), (23, 32), (11, 31)):
        bp.set(x, FL, z, WATER)
        bp.set(x, FL - 1, z, SAND)
    for (x, y, z) in ((10, FL + 3, 23), (26, FL + 3, 32), (18, FL + 6, 28), (18, FL + 6, 22)):
        bp.set(x, y, z, LANTERN)
    # 棚の上の「乾いた所」の印 (方解石の帯)
    for x in range(13, 24):
        bp.set(x, FL + 3, 20, CALCITE)
    # ---- 塩の戸 (白い石英の 3x3。三つの塩樽が乾いた棚にそろうと開く。印 door0..8)。枠はプリズマリン
    k = 0
    for y in range(FL + 1, FL + 4):
        for z in (27, 28, 29):
            bp.set(26, y, z, DOOR)
            bp.mark('door%d' % k, 26, y, z)
            k += 1
    for z in (26, 30):
        for y in range(FL + 1, FL + 5):
            bp.set(26, y, z, PRB)
    for z in range(26, 31):
        bp.set(26, FL + 4, z, PRB)
    # ---- 奥の間: 西の乾いた縁 (x 27..29) と北の壇 (z 14..17) は 1 段高く、ほかは膝までの浅瀬
    for x in range(27, 44):
        for z in range(14, 37):
            if x <= 29 or z <= 17:
                bp.set(x, FL + 1, z, 'minecraft:smooth_sandstone' if (x + z) % 4 else CALCITE)
            else:
                bp.set(x, FL + 1, z, WATER)
    # 柱 (潮の民の積んだ石) と天井の灯り、珊瑚の骸
    for (x, z) in ((33, 22), (39, 22), (33, 31), (39, 31)):
        for y in range(FL + 1, FL + 7):
            bp.set(x, y, z, PRB if y % 2 else SB)
    for (x, z) in ((36, 20), (36, 27), (36, 34), (31, 27), (41, 27)):
        bp.set(x, FL + 7, z, LANTERN)
    for (x, z) in ((31, 35), (42, 19), (42, 34), (30, 19)):
        bp.set(x, FL, z, 'minecraft:dead_horn_coral_block')
    # 壇の碑: 前の私の刻み (文は sign.skyabyss.ch2_salt_wall。読むと伝承 ch2/salt_vault)
    bp.set(35, FL + 2, 15, 'skyabyss:tablet[facing=south,standing=true]')
    bp.mark('wall_tablet', 35, FL + 2, 15)
    bp.set(33, FL + 2, 14, 'minecraft:sea_pickle[pickles=3,waterlogged=false]')
    bp.set(37, FL + 2, 14, 'minecraft:sea_pickle[pickles=3,waterlogged=false]')
    # 出口の縦穴: 北の壁に梯子 (z 13 の列、南向き = z 12 の壁につく)、口は跳ね戸
    for y in range(FL + 2, -1):
        bp.set(35, y, 13, ladder('south'))
    bp.set(35, -1, 13, 'minecraft:spruce_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]')
    bp.mark('exit', 35, 0, 13)
    # 部屋の中心 (自己テスト用)
    bp.mark('room1', 0, FL + 1, 27)
    bp.mark('room2', 18, FL + 1, 28)
    bp.mark('room3', 36, FL + 1, 27)
    underpin(bp)
    return bp


def underpin(bp):
    """砂と砂利の下が空き (部品に何もない) なら砂岩で支える。地下の洞や空の上 (自己テスト) に置いても床が落ちない"""
    loose = {bp.idx(s) for s in (SAND, 'minecraft:gravel')}
    for (x, y, z), i in sorted(bp.cells.items()):
        if i in loose and (x, y - 1, z) not in bp.cells:
            bp.set(x, y - 1, z, 'minecraft:sandstone')


# ---------------------------------------------------------------- 塩の道の標 (白く固まった塩の柱。北を向く矢印。SaltVault が次の標・蔵へ向けて回して置く)
def salt_mark():
    bp = BP('ch2_salt_mark', [-2, -2, 2, 2], ring=1)
    bp.set(0, 0, 0, CALCITE)
    bp.set(0, 1, 0, 'minecraft:light_blue_concrete')  # 朝の潮の目盛り
    bp.set(0, 2, 0, CALCITE)
    bp.set(0, 3, 0, 'minecraft:cyan_concrete')  # 夕の潮の目盛り
    bp.set(0, 4, 0, 'minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]')
    bp.set(1, 0, 0, 'minecraft:white_concrete_powder')
    bp.set(-1, 0, 1, 'minecraft:white_concrete_powder')
    # 矢印 (北向き)
    bp.set(0, 0, -1, 'minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]')
    bp.set(0, 0, -2, CALCITE)
    bp.set(-1, 0, -1, 'minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]')
    bp.set(1, 0, -1, 'minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]')
    bp.mark('sign', 0, 0, 0)
    bp.mark('stand', 0, 0, 2)
    return bp


# ---------------------------------------------------------------- 潮だまりの浜 (潮だまり貝の罠と小真珠の拾い場)
def tidal_pool():
    bp = BP('ch2_tidal_pool', [-5, -5, 5, 5], ring=2)
    rng = random.Random(77)
    for x in range(-5, 6):
        for z in range(-5, 6):
            if x * x + z * z <= 26:
                bp.set(x, -1, z, SAND if rng.random() < 0.8 else 'minecraft:gravel')
    pools = [(-2, -1, 2), (3, 2, 2)]
    for (cx, cz, r) in pools:
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                if (x - cx) ** 2 + (z - cz) ** 2 <= r * r:
                    bp.set(x, -1, z, WATER)
                    bp.set(x, -2, z, SAND)
        bp.set(cx, -1, cz, 'minecraft:sea_pickle[pickles=1,waterlogged=true]')
    for (x, z) in ((-4, 3), (1, -4), (4, -2), (-1, 4)):
        bp.set(x, 0, z, 'minecraft:dead_tube_coral[waterlogged=false]' if (x + z) % 2 else 'minecraft:dead_fire_coral[waterlogged=false]')
    for (x, z) in ((-5, 0), (0, 5), (5, -1)):
        bp.set(x, 0, z, CALCITE)
    bp.mark('pad1', -2, 0, 2)
    bp.mark('pad2', 2, 0, -1)
    bp.mark('stand', 0, 0, 0)
    return bp


# ---------------------------------------------------------------- 漂着の筏の跡 (潮の民の筏の骨組み。職人が立つ所)
def drift_raft():
    bp = BP('ch2_drift_raft', [-4, -4, 4, 4], ring=2)
    LOG_X = 'minecraft:stripped_oak_log[axis=x]'
    LOG_Z = 'minecraft:stripped_oak_log[axis=z]'
    for z in (-2, 0, 2):
        for x in range(-3, 3):
            if (x, z) not in ((2, 2), (-3, -2)):
                bp.set(x, 0, z, LOG_X)
    for x in (-2, 1):
        for z in (-1, 1):
            bp.set(x, 0, z, LOG_Z)
    # 倒れた帆柱と破れた帆
    for z in range(-3, 4):
        bp.set(3, 0, z, 'minecraft:oak_fence[east=false,north=false,south=false,waterlogged=false,west=false]' if z != 0 else LOG_Z)
    bp.set(3, 1, 0, 'minecraft:white_carpet')
    bp.set(2, 1, 0, 'minecraft:white_carpet')  # 破れた帆は丸太の上 (前は z 1 で、下に何もなかった)
    # 縄と浮き (乾いた昆布)
    bp.set(-3, 0, 2, 'minecraft:dried_kelp_block')
    bp.set(0, 1, 0, 'minecraft:chain[axis=x,waterlogged=false]')
    # 塩をかぶった樽 (印 chest。人ごとの一度の抽選はコードが渡す。中身は空)
    bp.set(-3, 0, -3, 'minecraft:barrel[facing=up,open=false]')
    bp.mark('chest', -3, 0, -3)
    bp.set(-4, 0, -2, 'minecraft:white_concrete_powder')
    bp.mark('artisan', 0, 0, 4)
    bp.mark('stand', 0, 0, -4)
    return bp


# ---------------------------------------------------------------- 引き潮の観測の杭 (岬。杭に潮の刻み)
def ebb_stakes():
    bp = BP('ch2_ebb_stakes', [-3, -3, 3, 3], ring=1)
    LOG = 'minecraft:stripped_dark_oak_log[axis=y]'
    for i, (x, h) in enumerate(((-2, 3), (0, 4), (2, 5))):
        for y in range(h):
            notch = (y == 1 and i == 0) or (y == 2 and i == 1) or (y == 3 and i == 2)
            bp.set(x, y, 0, 'minecraft:light_blue_concrete' if notch else LOG)
        bp.set(x, h, 0, 'minecraft:dark_oak_slab[type=bottom,waterlogged=false]')
    bp.set(0, 0, 2, 'skyabyss:tablet[facing=south,standing=true]')
    bp.mark('tablet', 0, 0, 2)
    bp.mark('stake', 0, 1, 0)
    bp.mark('stand', 0, 0, 3)
    for (x, z) in ((-3, 1), (3, -1), (1, -2)):
        bp.set(x, -1, z, 'minecraft:gravel')
    return bp


# ---------------------------------------------------------------- クエスト
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


# 支度の段は一本ずつ順に出る (2026-10-22 の試遊: メインが並ぶと迷う)。
#   塩の道   二章 (段階 1) の人が、イサナの「海の噂」(main_tide の rumor) を済ませたら。噂を飛ばして沖の島を見た人にも
#   船と貝   塩の道を終えたら (語り部がイサナの船の話を指す)
START = {
    'type': 'all',
    'of': [
        {'type': 'stage', 'min': 1, 'max': 1},
        quest('main_tide', 'started'),
        not_(quest('main_tide', 'active', 'rumor')),
    ],
}


def quests():
    q = os.path.join(RES, 'data/skyabyss/quest/ch2')
    write_json(os.path.join(q, 'salt_road.json'), {
        'category': 'main', 'order': 21, 'giver': STORYTELLER, 'chapter': 'titan_tide', 'start_when': START,
        'stages': [
            # 塩の道の標 5 つ (一つ目に着くと人ごとに合図 ch2_salt_mark。蔵の刻みを先に読んだ人は飛ばす)
            {'id': 'road', 'xp': 40, 'objectives': [{'id': 'marks', 'type': 'signal', 'target': 'ch2_salt_mark', 'count': 5, 'party': False}],
             'skip_if': {'type': 'lore', 'entry': 'ch2/salt_vault'}},
            # 蔵の奥の刻み (前の私) を読み、潮の番人を倒す (蔵のクリア = 合図 site_clear:ch2_salt_vault。仲間のクリアも数える)
            {'id': 'vault', 'xp': 60, 'mode': 'all', 'objectives': [
                {'id': 'wall', 'type': 'lore', 'targets': ['ch2/salt_vault']},
                {'id': 'warden', 'type': 'signal', 'target': 'site_clear:ch2_salt_vault', 'count': 1}]},
        ],
        'reward': {'xp': 80, 'items': [], 'affinity': {STORYTELLER: 3}},
    })
    write_json(os.path.join(q, 'ebb_boat.json'), {
        'category': 'main', 'order': 22, 'giver': ISANA, 'chapter': 'titan_tide',
        'start_when': {'type': 'all', 'of': [{'type': 'stage', 'min': 1, 'max': 1}, quest('ch2/salt_road', 'done')]},
        'stages': [
            # 船を作る (どの木の船でもよい。バニラの船)
            {'id': 'boat', 'xp': 30, 'objectives': [{'id': 'boat', 'type': 'collect', 'target': '#minecraft:boats', 'count': 1, 'party': False}]},
            # 沖の難破船の箱で潮の民の頁 (Wreck が記す) と潮見の貝 (まだ持っていなければ箱が渡す)
            {'id': 'wreck', 'xp': 60, 'mode': 'all', 'objectives': [
                {'id': 'page', 'type': 'lore', 'targets': ['ch2/wreck_page']},
                {'id': 'conch', 'type': 'collect', 'target': 'skyabyss:tide_watching_shell', 'count': 1, 'party': False}]},
        ],
        'reward': {'xp': 90, 'items': [], 'affinity': {ISANA: 3}},
    })
    write_json(os.path.join(q, 'drift_trail.json'), {
        'category': 'sub', 'order': 47, 'giver': ISANA, 'chapter': 'titan_tide',
        'start_when': {'type': 'all', 'of': [{'type': 'stage', 'min': 1, 'max': 1}, quest('ch2/ebb_boat', 'started')]},
        'stages': [
            # 浜の筏の跡に着く (宿借りの行列が浜を歩いている。着くと合図 ch2_raft)
            {'id': 'raft', 'xp': 20, 'objectives': [{'id': 'raft', 'type': 'signal', 'target': 'ch2_raft', 'count': 1, 'party': False}]},
        ],
        'reward': {'xp': 40, 'items': [], 'affinity': {ISANA: 1}},
    })
    write_json(os.path.join(q, 'wader_gear.json'), {
        'category': 'sub', 'order': 48, 'giver': ARTISAN, 'chapter': 'titan_tide',
        'start_when': {'type': 'all', 'of': [{'type': 'stage', 'min': 1, 'max': 1}, flag('ch2a_met', ARTISAN)]},
        'stages': [
            # 職人に潮渡りの旅装の作り方を教わる (会話の結果 unlock_recipes 2)
            {'id': 'learn', 'objectives': [{'id': 'talk', 'type': 'talk', 'target': ARTISAN, 'topic': 'ch2p_teach'}]},
            # 一式そろえる (合図 ch2_travel_gear_made。旅装の担当が送る)
            {'id': 'make', 'objectives': [{'id': 'made', 'type': 'signal', 'target': 'ch2_travel_gear_made', 'count': 1, 'party': False}]},
        ],
        'reward': {'xp': 60, 'items': [], 'affinity': {ARTISAN: 2}},
    })
    write_json(os.path.join(RES, 'data/skyabyss/quest/daily_pool_sweep.json'), {
        'category': 'sub', 'order': 65, 'repeat': 'daily', 'board': True, 'min_stage': 1,
        'stages': [
            {'id': 'pools', 'objectives': [{'id': 'kill', 'type': 'kill', 'targets': ['skyabyss:tidepool_clam'], 'count': 6}]},
            {'id': 'pearls', 'objectives': [{'id': 'pearl', 'type': 'turn_in', 'target': 'skyabyss:small_pearl', 'count': 4}]},
        ],
        'reward': {'xp': 60, 'items': [{'item': 'skyabyss:salt_crystal', 'count': 3}]},
        'unlock': {'type': 'flag', 'flag': 'met', 'npc': ISANA},
    })

    b = 'quest.skyabyss.ch2.salt_road.'
    put(b + 'title', '塩の道', 'The Salt Road')
    put(b + 'summary', '島へ渡る支度の一つめ。浜から内陸へ続く塩の柱を辿り、沈んだ塩の蔵で潮の読み方を探す。',
        'The first step in getting ready to cross. Follow the salt pillars inland from the beach to the Sunken Salt Vault, and look for a way to read the tide.')
    put(b + 'road.lead', 'イサナ「潮を読めなきゃ、島へは渡れない。浜の古い道のことは、語り部が知っている」',
        'Isana: "You cannot cross to the island if you cannot read the tide. The storyteller knows the old road on the beach."')
    put(b + 'vault.lead', '塩の道は、浅瀬の下の蔵で終わっていた。潮の読み方は、この奥にあるはずだ。',
        'The salt road ends at a vault beneath the shallows. The way to read the tide should be inside.')
    put(b + 'road.journal',
        'イサナが言っていた。潮を読めなければ、島へは渡れない、と。\n浜には白い柱が並んでいて、塩の道と呼ばれている。柱には潮の刻みがあるという。五つ辿れば、塩の蔵に着く。その道の昔話は、語り部が知っているらしい。',
        'Isana said you cannot cross to the island if you cannot read the tide.\nWhite pillars line the beach: the salt road. They carry tide marks. Follow five and you reach the salt vault. The storyteller is said to know the old tale of that road.')
    put(b + 'road.marks', '浜の白い柱を辿り、塩の道を行こう', 'Follow the white salt pillars')
    put(b + 'vault.journal',
        '浅瀬の下に、積んだ石の屋根が見えた。沈んだ塩の蔵だ。\n潮が引けば喉は乾いて歩ける。満ちていれば、息を止めて潜ればいい。奥には、蔵の番人がいる。',
        'Beneath the shallows, a roof of stacked stone. The Sunken Salt Vault.\nAt low tide its throat is dry to walk; at high tide, hold your breath and dive. Something guards the back of the vault.')
    put(b + 'vault.wall', '蔵の奥の刻みを読もう', 'Read the carving at the back of the vault')
    put(b + 'vault.warden', '蔵を守る潮の番人を倒そう', "Defeat the vault's Shore Warden")
    put(b + 'done', '「潮は読める、か。……あんたの字みたいな刻みだったんだろう？　顔に書いてあるよ」\nあとは、沖へ渡る足だ。イサナが、船のことを言っていた。',
        '"The tide can be read, was it. ...The carving looked like your own hand, didn\'t it? It\'s written on your face."\nNow you need a way across the water. Isana spoke of a boat.')

    b = 'quest.skyabyss.ch2.ebb_boat.'
    put(b + 'title', '船と貝', 'Boat and Shell')
    put(b + 'summary', '島へ渡る支度の二つめ。船を作って沖の難破船へ渡り、潮の民の頁と潮見の貝を手に入れる。',
        'The second step in getting ready. Build a boat, cross to the wreck offshore, and find the tide people\'s page and the tide-watching shell.')
    put(b + 'boat.lead', '語り部「潮は読めるようになったね。あとは渡る足さ。イサナが、船のことを言っていたよ」',
        'The storyteller: "You can read the tide now. Next you need a way across. Isana was talking about a boat."')
    put(b + 'wreck.lead', 'イサナ「沖に、古い船が傾いている。潮の数え方を教える貝が、あそこにある」',
        'Isana: "An old ship lists offshore. The shell that teaches you to count the tide is there."')
    put(b + 'boat.journal',
        'イサナが言っていた。「浜から沖へ渡るなら、船を作れ。……沖に、古い船が沈みかけている。貝はあそこにある」\n船は、木の板があれば作業台で作れる。',
        'Isana said: "If you mean to cross from the beach, build a boat. ...There is an old ship sinking offshore. The shell is there."\nA boat can be made at a workbench from planks.')
    put(b + 'boat.boat', '沖へ渡る船を作ろう', 'Build a boat to cross the water')
    put(b + 'wreck.journal',
        '難破船は、浜から見える浅い沖に傾いている。帆柱が水の上に出ている。\n船の箱に、潮の民の頁が残っているかもしれない。潮見の貝も。',
        'The wreck lists in the shallows, in sight of the beach, its mast above the water.\nThe tide people\'s page may still be in its chests. The tide-watching shell too.')
    put(b + 'wreck.page', '難破船の箱で、潮の民の頁を探そう', 'Find the tide people\'s page in the wreck')
    put(b + 'wreck.conch', '潮見の貝を手に入れよう', 'Get the tide-watching shell')
    put(b + 'done', '「その貝を耳に当てると、潮の数え方が分かる。……島は、待つ者のところへ寄るんだそうだ」\nこれで、島へ渡る支度がそろった。',
        '"Hold that shell to your ear and you learn to count the tide. ...They say the island comes to those who wait."\nYou are ready to cross to the island.')

    b = 'quest.skyabyss.ch2.drift_trail.'
    put(b + 'title', '宿借りの行列', 'The Hermit Procession')
    put(b + 'summary', '浜を歩く宿借りの行列を追い、筏の跡へ。', 'Follow the hermit crabs along the beach to the drift raft.')
    put(b + 'raft.journal', 'イサナ「浜の宿借りは、流れ着いた物のところへ行列を作る。……あいつらの後をついていけば、何か見つかるかもしれない」',
        'Isana: "The hermits on the beach form a line to whatever washes up. ...Follow them and you may find something."')
    put(b + 'raft.raft', '浜の筏の跡を探そう', 'Find the drift raft on the beach')
    put(b + 'done', '「筏か。……誰かが、ここから沖へ出ようとしたんだな」', '"A raft. ...Someone tried to put out to sea from here."')

    b = 'quest.skyabyss.ch2.wader_gear.'
    put(b + 'title', '潮渡りの旅装', 'Tide-Wader Garb')
    put(b + 'summary', '旅の職人に潮渡りの旅装の作り方を教わり、一式そろえる（任意）。', 'Learn the tide-wader garb from the traveling artisan and make the full set (optional).')
    put(b + 'learn.journal', '職人の前掛けは塩で白い。「水の中で足が重いなら、それ用の旅装がある。教えようか」',
        'The artisan\'s apron is white with salt. "If water drags at your legs, there is garb for that. Shall I show you?"')
    put(b + 'learn.talk', '旅の職人に、潮渡りの旅装を教わろう', 'Learn the tide-wader garb from the artisan')
    put(b + 'make.journal', '小真珠と塩結晶と、前の巨神の鱗片。作り方は旅の覚書に。', 'Small pearls, salt crystals and scales from the last titan. The patterns are in your wayfarer\'s notes.')
    put(b + 'make.made', '潮渡りの旅装を一式そろえよう', 'Make the full tide-wader set')
    put(b + 'done', '「道具は覚えている。……着ているうちに、あんたの歩き方を覚えるよ」', '"Tools remember. ...Wear it a while and it will learn the way you walk."')

    b = 'quest.skyabyss.daily_pool_sweep.'
    put(b + 'title', '潮だまりの見回り', 'Tide Pool Rounds')
    put(b + 'summary', '浜の潮だまり貝を六つ鎮め、小真珠を四つ納める。', 'Quiet six tidepool clams on the beach and hand in four small pearls.')
    put(b + 'pools.journal', '板の紙（塩で白い字）：「潮だまりの貝が、また口を開けている。足を挟まれた者がいる。六つ、閉じさせてきてほしい」',
        'A note on the board (salt-white ink): "The tide pool clams are gaping again. Someone got a foot caught. Close six of them, please."')
    put(b + 'pools.kill', '潮だまり貝を倒そう', 'Defeat tidepool clams')
    put(b + 'pearls.journal', '「貝の中の小真珠は、薬師が欲しがる。四つ、板の箱へ」', '"The apothecary wants the small pearls from inside. Four, into the board\'s box."')
    put(b + 'pearls.pearl', '小真珠を納めよう', 'Hand in small pearls')
    put(b + 'done', '浜は、しばらく静かになるだろう。', 'The beach should be quiet for a while.')
    put(b + 'unlock_hint', 'イサナと話すと、受けられる', 'Talk to Isana to take this on')


# ---------------------------------------------------------------- 会話
def dialogue():
    d_ = os.path.join(RES, 'data/skyabyss/dialogue/ch2')
    write_json(os.path.join(d_, 'storyteller_trail.json'), {
        'npc': STORYTELLER, 'extends': True,
        'entries': [
            {'node': 'ch2t_s_vault', 'if': [quest('ch2/salt_road', 'done'), not_(flag('ch2t_s_vault_told'))]},
            {'node': 'ch2t_salt_tell', 'if': [quest('ch2/salt_road', 'active', 'road'), not_(flag('ch2t_salt_told'))]},
        ],
        'nodes': {
            'ch2t_salt_tell': {'face': 'neutral', 'do': [flag('ch2t_salt_told')], 'next': 'menu'},
            'ch2t_s_vault': {'face': 'grave', 'do': [flag('ch2t_s_vault_told'), {'type': 'affinity', 'amount': 1}], 'next': 'menu'},
            # 報告 (メイン「満ち引きの門」): ms_tide_back2 の先に、村の記憶と数え歌のふたつめ
            'ms_tide_memory': {'face': 'grave', 'do': [{'type': 'lore', 'entry': 'ch2/why_they_rode'}], 'next': 'ms_tide_verse'},
            'ms_tide_verse': {'face': 'grave', 'options': [{'id': 'ok', 'goto': 'end', 'do': [{'type': 'affinity', 'amount': 2}]}]},
        },
    })
    d = 'dialogue.skyabyss.storyteller.'
    put(d + 'ch2t_salt_tell',
        '浜の白い柱を見たかい。塩の道さ。あたしの祖母の、そのまた祖母のころには、あれを辿って塩を運んだそうだよ。\n道の終わりに、塩の蔵がある。潮が満ちると沈むんだ。……蔵の中のことは、誰も話したがらなかった。',
        'Have you seen the white pillars on the beach? The salt road. In my grandmother\'s grandmother\'s day, salt was carried along it.\nAt the end of the road is the salt vault. It sinks when the tide comes in. ...No one liked to talk about what is inside.')
    put(d + 'ch2t_s_vault',
        '蔵の壁に、刻みがあったんだって？「潮は読める。待て」……。\nあんたが持ってきた頁と、同じ癖の字だね。待つのが下手な人だったのかね。それとも、待ちくたびれた人か。',
        'There was a carving on the vault wall? "The tide can be read. Wait." ...\nThe same hand as the pages you bring me. Someone bad at waiting, I wonder. Or someone tired of it.')
    put(d + 'ms_tide_memory',
        '……ひとつ、思い出したことがあるよ。村の、いちばん古い話さ。\n空が落ちかけた夜があった。月が近づいて、海が引いて、どこまでも干潟になった。人は、歩いて逃げた。島へ。……島が、待っていたんだそうだ。\n乗った者は、帰ってこなかった。残った者が、この村をつくった。',
        '...I remembered something. The village\'s oldest story.\nThere was a night the sky nearly fell. The moon came close, the sea drew back, and the shore became flats as far as you could see. People fled on foot. To the island. ...The island was waiting, they say.\nThose who climbed on never came back. Those who stayed built this village.')
    put(d + 'ms_tide_verse',
        '数え歌の、ふたつめさ。「ふたつ　ひつじとふせなさい」。\n伏せていた者が残った。乗った者は、歌になった。……子どもらは、知らずに歌っていたんだね。',
        'The second line of the counting song. "Two, lie down with the sheep."\nThose who lay down stayed. Those who rode became the song. ...The children sang it without knowing.')
    put(d + 'ms_tide_verse.ok', '……覚えておく。', '...I will remember.')

    write_json(os.path.join(d_, 'whale_chaser_trail.json'), {
        'npc': ISANA, 'extends': True,
        'entries': [
            {'node': 'ch2t_c_wreck', 'if': [quest('ch2/ebb_boat', 'done'), not_(flag('ch2t_c_wreck_told'))]},
            {'node': 'ch2t_c_boat', 'if': [quest('ch2/ebb_boat', 'active', 'boat'), not_(flag('ch2t_c_boat_told'))]},
        ],
        'nodes': {
            'ch2t_c_boat': {'face': 'neutral', 'do': [flag('ch2t_c_boat_told')], 'next': 'c_menu'},
            'ch2t_c_wreck': {'face': 'grave', 'do': [flag('ch2t_c_wreck_told'), {'type': 'affinity', 'amount': 1}], 'next': 'c_menu'},
        },
    })
    d = 'dialogue.skyabyss.whale_chaser.'
    put(d + 'ch2t_c_boat',
        '島は朝と夕に寄ってくる。……だが、待つだけじゃ足りない。浜から沖へ渡れ。船を作れ。\n沖に、古い船が傾いている。帆柱が見えるはずだ。貝は、あそこの箱にあると聞いた。……潮の数え方を教えてくれる貝だ。',
        'The island comes at dawn and dusk. ...But waiting is not enough. Cross from the beach. Build a boat.\nThere is an old ship listing offshore. You should see its mast. I hear the shell is in its chests. ...A shell that teaches you to count the tide.')
    put(d + 'ch2t_c_wreck',
        '難破船の頁を読んだか。「三度目に島が来たら、皆で乗る」……。\n島へ乗る舟だったのか、あの船は。……乗れなかった者の船だ。',
        'You read the page from the wreck? "When the island comes the third time, we all board." ...\nSo that ship was meant to reach the island. ...It belonged to those who never made it aboard.')

    write_json(os.path.join(d_, 'artisan.json'), {
        'npc': ARTISAN, 'extends': True,
        'entries': [
            {'node': 'ch2t_ward_weave', 'if': [{'type': 'item', 'item': 'skyabyss:pearl_ward_thread', 'count': 1, 'optional': True},
                                               not_(flag('ch2t_woven')), {'type': 'ward_ok', 'titan': 'tide_shell'}]},
            {'node': 'ch2a_first', 'if': [{'type': 'stage', 'min': 1, 'max': 1}, not_(flag('ch2a_met'))]},
        ],
        'nodes': {
            'ch2a_first': {'face': 'neutral', 'do': [flag('ch2a_met'), flag('met'), {'type': 'give_notes', 'chapter': 2}, {'type': 'affinity', 'amount': 1}], 'next': 'a_menu'},
            'ch2p_teach': {'face': 'neutral', 'do': [{'type': 'unlock_recipes', 'chapter': 2}, flag('ch2p_taught')], 'next': 'a_menu'},
            'ch2t_ward_weave': {'face': 'smile', 'do': [{'type': 'take', 'item': 'skyabyss:pearl_ward_thread', 'count': 1},
                                                        {'type': 'give', 'item': 'skyabyss:heartbeat_ward_tide_shell', 'count': 1},
                                                        flag('ch2t_woven'), {'type': 'affinity', 'amount': 2}], 'next': 'a_menu'},
            'ch2a_ward': {'face': 'smile', 'do': [{'type': 'take', 'item': 'skyabyss:salt_crystal', 'count': 8},
                                                  {'type': 'remake_ward', 'titan': 'tide_shell'}, {'type': 'affinity', 'amount': 1}], 'next': 'a_menu'},
            'ch2a_ward_ask': {'face': 'neutral', 'next': 'a_menu'},
        },
        'add_options': {
            'a_menu': {'before': 'bye', 'options': [
                {'id': 'teach2', 'kind': 'quest', 'if': [{'type': 'stage', 'min': 1, 'max': 1}, flag('ch2a_met')], 'goto': 'ch2p_teach'},
                {'id': 'ward2', 'kind': 'give', 'if': [flag('ch2t_woven'), {'type': 'ward_ok', 'titan': 'tide_shell'},
                                                       {'type': 'item', 'item': 'skyabyss:salt_crystal', 'count': 8, 'optional': True}], 'goto': 'ch2a_ward'},
                {'id': 'ward_ask2', 'kind': 'quest', 'if': [flag('ch2t_woven'), {'type': 'ward_ok', 'titan': 'tide_shell'},
                                                            not_({'type': 'item', 'item': 'skyabyss:salt_crystal', 'count': 8, 'optional': True})], 'goto': 'ch2a_ward_ask'},
            ]},
        },
    })
    d = 'dialogue.skyabyss.artisan.'
    put(d + 'ch2a_first',
        'おや、旅の人。……どこかで会ったかな。いや、いい。浜は道具がすぐ錆びる。直すものがあれば言ってくれ。\nこれは浜の覚書だ。塩と真珠の扱いを書いておいた。',
        'Ah, a traveller. ...Have we met? No matter. Tools rust fast on the beach. Tell me if anything needs mending.\nHere, notes for the shore. How to work salt and pearl.')
    put(d + 'ch2p_teach',
        '潮渡りの旅装だ。小真珠を継いで、塩で締める。前の巨神の鱗を肩に。\n水の中でも、足が重くならない。……教えた手つきを、どこかで見た気がするって？　道具はみんな、そう言うよ。',
        'Tide-wader garb. Pearls stitched in, bound tight with salt. Scales of the last titan at the shoulder.\nYour legs will not drag in the water. ...You feel you have seen these hands before? Tools all say that.')
    put(d + 'ch2t_ward_weave',
        '真珠の護り糸か。……いい糸だ。潮の音がする。\n鼓動守に編もう。殻の中で、鼓動がうるさくなったら、これを握るといい。',
        'Pearl ward thread. ...Good thread. It sounds of the tide.\nI will weave it into a heartbeat ward. When the beat grows loud inside the shell, grip this.')
    put(d + 'ch2a_ward', '塩結晶を八つ。……ほら、編み直した。', 'Eight salt crystals. ...There, rewoven.')
    put(d + 'ch2a_ward_ask', '編み直すには、塩結晶が八つ要る。浜の貝や砂の下のものが持っているよ。', 'To reweave it I need eight salt crystals. The clams and the things under the sand carry them.')
    put(d + 'a_menu.teach2', '潮渡りの旅装を教えてほしい', 'Teach me the tide-wader garb')
    put(d + 'a_menu.ward2', '潮殻の鼓動守を編み直してほしい', 'Reweave my tide ward')
    put(d + 'a_menu.ward_ask2', '鼓動守の編み直しには、何が要る？', 'What do you need to reweave the ward?')


# ---------------------------------------------------------------- 伝承
def lore():
    entries = [
        ('drift_raft', 30, None, '筏の跡の頁', 'The Page at the Drift Raft',
         '浜に打ち上げられた筏の骨組みの、樽の底に頁が一枚。塩で固まって、めくると割れそうだった。\n\n「筏では届かない。木を継げ。舟にしろ。島は朝と夕に寄る。寄った島へは、浜から漕げ」\n\n字の癖に、見覚えがある。……また、この字だ。',
         'At the bottom of a barrel by the raft\'s washed-up frame lay a single page, so stiff with salt it nearly cracked when turned.\n\n"A raft will not reach. Join the timbers. Make a boat. The island comes at dawn and dusk. Row to it from the beach."\n\nThe hand is familiar. ...This hand again.'),
        ('salt_vault', 31, 'sign.skyabyss.ch2_salt_wall', '蔵の刻み', 'The Carving in the Vault',
         '沈んだ塩の蔵の奥、番人が守っていた壇の壁に、爪で刻んだ字があった。塩の膜の下で、刻みはまだ新しい。\n\n「潮は読める。待て」\n\nその下に、もう一行。こすれて読めない。……読めないのに、何と書いたか分かる気がした。',
         'At the back of the Sunken Salt Vault, on the wall of the dais the warden kept, letters were scratched with a nail. Under a film of salt, the scratches still looked fresh.\n\n"The tide can be read. Wait."\n\nBeneath it, one more line, rubbed away. ...Unreadable, yet you feel you know what it said.'),
        ('wreck_page', 32, None, '潮の民の頁', 'The Tide People\'s Page',
         '沖の難破船の箱に、油紙に包んだ帳面の切れ端があった。潮の民の字だ。\n\n「潮が二度引いた。空はまだ低い。三度目に島が来たら、皆で乗る。舟は置いていく。家も、名も」\n\n最後の行は、急いだ字で。「乗れなかった者は、この船で待つ」',
         'In a chest on the wreck offshore, a scrap of ledger wrapped in oilcloth. The hand of the tide people.\n\n"The tide has drawn back twice. The sky is still low. When the island comes a third time, we all board. We leave the boats. The houses too, and our names."\n\nThe last line is hurried: "Those who could not board will wait on this ship."'),
        ('why_they_rode', 33, None, '村の記憶', 'The Village\'s Memory',
         '語り部が思い出した、村のいちばん古い話。\n\n空が落ちかけた夜があった。月が近づき、海が引いて、どこまでも干潟になった。人は歩いて島へ逃れた。島は、待っていた。\n\n乗った者は帰らなかった。残った者がこの村をつくった。数え歌のふたつめ「ふたつ　ひつじとふせなさい」は、伏せて残った者の歌だという。',
         'The village\'s oldest story, as the storyteller remembered it.\n\nThere was a night the sky nearly fell. The moon came close, the sea drew back, and the shore became endless flats. People fled to the island on foot. The island was waiting.\n\nThose who climbed on never returned. Those who stayed built this village. The counting song\'s second line, "Two, lie down with the sheep," is said to be the song of those who lay low and stayed.'),
    ]
    for name, order, sign, tj, te, bj, be in entries:
        data = {'chapter': 'titan_tide', 'order': order, 'unlock': [{'type': 'manual'}], 'related': []}
        if sign:
            data['inscription_sign'] = sign
            data['unlock'] = [{'type': 'sign', 'target': sign}]
        write_json(os.path.join(RES, 'data/skyabyss/lore/entry/ch2/%s.json' % name), data)
        put('lore.skyabyss.ch2.%s.title' % name, tj, te)
        put('lore.skyabyss.ch2.%s.body' % name, bj, be)


# ---------------------------------------------------------------- 場所の文・碑
def texts():
    m = 'message.skyabyss.ch2.'
    put(m + 'arrive.ch2_salt_vault', '浅瀬の下に、積んだ石の屋根が見える。塩で白い。', 'Beneath the shallows, a roof of stacked stone, white with salt.')
    put(m + 'vault.dry', '潮が引いている。蔵の喉の水も引いた。', 'The tide is out. The vault\'s throat has drained.')
    put(m + 'vault.wet', '潮が満ちている。蔵の喉は水の底だ。息を止めれば、抜けられる。', 'The tide is in. The vault\'s throat is under water. Hold your breath and you can get through.')
    put(m + 'vault.lift', '塩樽を抱えた。重い。乾いた所へ運ぼう。', 'You lift a salt barrel. Heavy. Carry it somewhere dry.')
    put(m + 'vault.broke', '樽が割れて、塩が散った。……樽は、元の場所に戻っている。', 'The barrel splits and the salt spills. ...The barrel is back where it was.')
    put(m + 'vault.shelf', '塩樽を乾いた棚に置いた。', 'You set the salt barrel on the dry shelf.')
    put(m + 'vault.wet_floor', '樽は、乾いた棚の上へ。', 'The barrel belongs on the dry shelf.')
    put(m + 'vault.door', '三つの樽が並ぶと、白い石の戸が、塩をこぼしながら退いていく。', 'With three barrels in a row, the white stone door slides back, shedding salt.')
    put(m + 'vault.door_shut', '白い石の戸は動かない。棚が、三つの樽を待っている。', 'The white stone door will not move. The shelf seems to be waiting for three barrels.')
    for n, (j, e) in enumerate((
            ('塩は、道を覚えている。', 'The salt remembers the road.'),
            ('柱の刻み。下の線が朝の潮、上の線が夕の潮の高さだ。', 'Marks on the pillar. The lower line is the morning tide, the upper the evening tide.'),
            ('刻みのそばに、爪の跡。「数えて、待つ」', 'Beside the marks, scratches from a nail: "Count, and wait."'),
            ('柱の根もとが濡れている。ここまで潮が来るのだ。', 'The foot of the pillar is wet. The tide reaches this far.'),
            ('最後の柱。矢印は、浅瀬の下の石の屋根を指している。', 'The last pillar. Its arrow points to a stone roof under the shallows.')), start=1):
        put(m + 'salt_mark.%d' % n, j, e)
    put(m + 'arrive.ch2_tidal_pools', '潮だまりに、貝が口を閉じて並んでいる。……一つだけ、少し開いた。', 'Clams sit closed in the tide pools. ...One of them has opened, just a little.')
    put(m + 'arrive.ch2_drift_raft', '浜に、筏の骨組みが打ち上げられている。縄は、潮の民の結び方だ。', 'The frame of a raft lies washed up on the beach. The rope is tied the tide people\'s way.')
    put(m + 'raft.found', '樽の底に、塩で固まった頁が一枚。', 'At the bottom of the barrel, a page stiff with salt.')
    put(m + 'arrive.ch2_ebb_stakes', '岬に、刻みのある杭が三本。潮の高さを測っていたらしい。', 'Three notched stakes stand on the headland. Someone measured the tide here.')
    put(m + 'stakes.read', '杭の刻みを読む。次の引き潮まで、およそ %s 分。', 'You read the notches. About %s minutes until the next low tide.')
    put(m + 'stakes.now', '杭の刻みを読む。潮は今、引いている。満ちるまで、およそ %s 分。', 'You read the notches. The tide is out now; about %s minutes until it turns.')
    put(m + 'stakes.ebb', '一番下の刻みは、新しい。……大きな引き潮は、夜の前半に来る。', 'The lowest notch is fresh. ...The great ebb comes in the first half of the night.')
    put(m + 'wreck.page', '箱の底に、油紙の包み。潮の民の帳面の切れ端だ。', 'At the bottom of the chest, an oilcloth bundle. A scrap of the tide people\'s ledger.')
    put(m + 'wreck.shell', '頁のそばに、白い巻き貝が一つ。耳に当てると、潮の音がする。', 'Beside the page, a single white whelk. Held to your ear, it sounds of the tide.')
    put(m + 'wreck.seen', '沖に、傾いた船の帆柱が見える。', 'Offshore, the mast of a listing ship.')
    put(m + 'trail.ready', '塩の道を辿り、船で沖へ渡った。……潮の数え方が、身についてきた。', 'You walked the salt road and crossed to the wreck by boat. ...You are learning to count the tide.')
    put('item.skyabyss.ch2_salt_barrel', '塩樽', 'Salt Barrel')
    # 碑 (4 行)
    for key, lines_ja, lines_en in (
            ('sign.skyabyss.ch2_salt_vault', ['塩の蔵', '潮が引けば 歩いて', '満ちれば 潜って', '番人は 眠らない'],
             ['The Salt Vault', 'At low tide, walk', 'At high tide, dive', 'The warden is awake']),
            ('sign.skyabyss.ch2_salt_wall', ['潮は 読める', '待て', '', ''],
             ['The tide can be read', 'Wait', '', '']),
            ('sign.skyabyss.ch2_ebb_stakes', ['潮の杭', '刻みを 数えよ', '大きく引くのは', '夜の はじめ'],
             ['Tide stakes', 'Count the notches', 'The great ebb', 'comes early at night'])):
        for i in range(4):
            put('%s.%d' % (key, i + 1), lines_ja[i], lines_en[i])


NL = chr(10)


def append_lang(path, table):
    """既存の行 (空行も) はそのまま。このスクリプトのキーの行だけ消して、ファイルの末尾にひとまとまりで書き直す"""
    with open(path, encoding='utf-8') as f:
        lines = f.read().rstrip().split(NL)
    assert lines[-1].strip() == '}'
    body = [ln for ln in lines[1:-1] if not any(ln.strip().startswith(json.dumps(k, ensure_ascii=False) + ':') for k in table)]
    while body and body[-1].strip() == '':
        body.pop()
    if body and not body[-1].rstrip().endswith(','):
        body[-1] = body[-1].rstrip() + ','
    block = ['  %s: %s,' % (json.dumps(k, ensure_ascii=False), json.dumps(v, ensure_ascii=False)) for k, v in table.items()]
    block[-1] = block[-1][:-1]
    with open(path, 'w', encoding='utf-8', newline=NL) as f:
        f.write(NL.join([lines[0]] + body + [''] + block + ['}']) + NL)
    json.load(open(path, encoding='utf-8'))


def main():
    out = []
    for bp in (salt_vault(), salt_mark(), tidal_pool(), drift_raft(), ebb_stakes()):
        out.append('%s=%d' % (bp.name, bp.save()))
    quests()
    dialogue()
    lore()
    texts()
    assert set(ja) == set(en)
    for code, table in (('ja_jp', ja), ('en_us', en)):
        append_lang(os.path.join(RES, 'assets/skyabyss_ch2/lang/%s.json' % code), table)
    print(', '.join(out))
    print('wrote ch2 ground: %d texts' % len(ja))


if __name__ == '__main__':
    main()
