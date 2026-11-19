# -*- coding: utf-8 -*-
"""神器（look5・個性版）。終章の神器 12 品（docs/design/50_finale.md の 4.5）。巡礼者の翼・鐘・灯・瞼・時計から一品ずつ形を立てる。
色: 白い石（stone）の 1 系統、差し色は金（gold）、光は朝の光（dawn）。黒い硝子（glass）は巻き戻りの短剣の刃だけ。

形は他のシリーズから借りない。
- 柄: 白い石の握りに金の細い帯を三本、柄頭は小さな金の輪（ループ）。
- 振る武器は握りの真ん中を原点にする。杖・鈴杖は握り -14..2。弓は握りの中心 (12, 15)。
- 光: 天穹の剣 = 樋の朝の光、羽の番人 = 面頬の目、七つ鐘 = 鐘の口の撞木、灰打ち = 胴の熱の継ぎ目、暁の灯杖 = 籠の中の目、
  雲の瞼 = 瞳、巡礼の羽弓 = 握りの外の輪、霞鳥の弓 = 光なし、巻き戻り = 刃の残り火の縁、灯蛾 = 翅の紋、約束 = 四つの灯、鐘守 = 光なし。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'stone': ['#2a2a32', '#45454f', '#62626c', '#80808a', '#9e9ea6', '#bbbbc2', '#d4d4d9', '#e8e8ec', '#f6f6f8', '#ffffff'],
    'gold': ['#4a3410', '#7a5618', '#a87a22', '#d4a432', '#f0c858', '#fce69a'],
    'dawn': ['#a85a3a', '#d8805a', '#f4a878', '#fcd0a8', '#fff0dc', '#fffaf2'],
    'rose': ['#4a2026', '#6e2e34', '#94403f', '#b8584c', '#d47862', '#ea9c80'],
    'glass': ['#0c0c14', '#18182a', '#26263e', '#383856', '#4e4e70', '#6a6a8c'],
})
S, G, D, K = 'stone', 'gold', 'dawn', 'glass'


def M(name):
    return Model(name, accent=G, glowmat=D)


def grip(m, y0, y1, loop=True):
    """白い石の握りに金の細い帯三本、上に金の鍔元、柄頭は金の輪"""
    m.box('grip', S, [-1.5, y0, -1.5], [1.5, y1, 1.5], grad=(y0, 4, y1, 6))
    for k in range(3):
        y = y0 + (y1 - y0) * (k + 0.5) / 3 - 0.5
        m.box(f'grip_band_{k}', G, [-1.75, y, -1.75], [1.75, y + 1, 1.75], 3)
    m.box('grip_cap', G, [-2.25, y1, -2.25], [2.25, y1 + 1.5, 2.25], grad=(y1, 2, y1 + 1.5, 4))
    if loop:
        m.box('pommel_neck', G, [-1.25, y0 - 1.5, -1.25], [1.25, y0, 1.25], 3)
        m.arc('pommel', G, (0, y0 - 4), 2.5, 90, 450, 6, 1.25, 0.8, 3, 5)


def rod(m, y0, y1, bands=()):
    m.box('rod', S, [-1.25, y0, -1.25], [1.25, y1, 1.25], grad=(y0, 4, y1, 7))
    for k, y in enumerate(bands):
        m.box(f'band_{k}', G, [-1.75, y, -1.75], [1.75, y + 1.25, 1.75], 3)


def wing(m, name, sd, base, n, L, ang0, step, w, d, mat=S, t0=5, t1=8, zc=0.0):
    """畳んだ翼: 根元から外上へ扇に開く風切羽 n 枚（sd = -1 左、1 右）"""
    for k in range(n):
        a = math.radians(ang0 + step * k)
        bx, by = base[0] + sd * k * 0.6, base[1] - k * 0.4
        m.seg(f'{name}_{k}', mat, (bx, by), (bx + sd * math.cos(a) * (L - k * 1.2), by + math.sin(a) * (L - k * 1.2)),
              w, d, t0, t1, tip=True, zc=zc)


# ---------------------------------------------------------------- 剣

def divine_sky_sword():
    """天穹の剣: 白い石の大剣に金の縁、樋に朝の光。鍔は畳んだ巡礼者の翼と金の輪"""
    m = M('divine_sky_sword')
    grip(m, -6, 6)
    m.box('guard', G, [-3, 7.5, -1.75], [3, 10, 1.75], grad=(7.5, 2, 10, 4))
    m.arc('guard_ring', G, (0, 9.25), 3.5, 0, 360, 8, 1.25, 1.1, 3, 5)
    for sd, n in ((-1, 'l'), (1, 'r')):
        wing(m, f'wing_{n}', sd, (sd * 3, 9), 3, 8, 25, 18, 2, 0.9)
    m.box('blade', S, [-3, 10, -1], [3, 40, 1], grad=(10, 5, 40, 8))
    for sd, n in ((-1, 'l'), (1, 'r')):
        x0, x1 = (3, 3.75) if sd > 0 else (-3.75, -3)
        m.box(f'edge_{n}', G, [x0, 11, -0.7], [x1, 39, 0.7], grad=(11, 3, 39, 5))
        m.seg(f'tip_{n}', S, (sd * 3, 39.5), (0, 47), 3, 0.9, 7, 9, tip=True)
    m.box('fuller', D, [-0.75, 12, -1.1], [0.75, 36, 1.1], grad=(12, 2, 36, 4))
    m.box('tip_core', S, [-1.5, 39, -0.95], [1.5, 42, 0.95], 8)
    return m


def divine_warden_blade():
    """羽の番人の大剣: 幅広の大剣に白い羽の鱗（山形の列）、鍔は番人の面頬と畳んだ羽。面頬の目が光る"""
    m = M('divine_warden_blade')
    grip(m, -8, 8)
    m.box('visor', S, [-4, 9.5, -2.25], [4, 15, 2.25], grad=(9.5, 4, 15, 6))
    m.box('visor_brow', G, [-4.5, 14, -2.5], [4.5, 15.5, 2.5], 4)
    m.box('visor_eye_f', D, [-3, 11.75, -2.6], [3, 12.75, -2.25], 4)
    m.box('visor_eye_b', D, [-3, 11.75, 2.25], [3, 12.75, 2.6], 4)
    m.box('visor_chin', G, [-2.5, 9, -2.4], [2.5, 10, 2.4], 3)
    for sd, n in ((-1, 'l'), (1, 'r')):
        wing(m, f'wing_{n}', sd, (sd * 4, 12), 3, 7, 30, 20, 2, 0.9)
    m.box('blade', S, [-4.5, 15.5, -1.1], [4.5, 50, 1.1], grad=(15.5, 5, 50, 8))
    for k, y in enumerate((20, 27, 34, 41)):
        for sd, n in ((-1, 'l'), (1, 'r')):
            m.seg(f'plume_{k}{n}', S, (0, y + 3), (sd * 4, y), 2, 1.25, 6, 8)
    m.box('spine', G, [-0.6, 16, -1.3], [0.6, 46, 1.3], grad=(16, 3, 46, 5))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'tip_{n}', S, (sd * 4.5, 49.5), (0, 57), 4, 1, 7, 9, tip=True)
    m.box('tip_core', S, [-2, 49, -1.05], [2, 52.5, 1.05], 8)
    return m


# ---------------------------------------------------------------- 鎚

def divine_bell_hammer():
    """七つ鐘の大鎚: 頭は寝かせた金の大鐘（口は +x）、口の奥で撞木が光る。鐘の帯から小さな鐘が三つ下がる"""
    m = M('divine_bell_hammer')
    grip(m, -6, 6)
    rod(m, 7.5, 25, bands=(15,))
    c = 30.5
    m.box('crown', G, [-7, c - 2, -2], [-5.5, c + 2, 2], 3)
    m.box('bell_0', G, [-5.5, c - 3, -3], [-2, c + 3, 3], grad=(c - 3, 2, c + 3, 4))
    m.box('bell_1', G, [-2, c - 4, -4], [2.5, c + 4, 4], grad=(c - 4, 3, c + 4, 5))
    m.box('bell_2', G, [2.5, c - 5, -5], [6.5, c + 5, 5], grad=(c - 5, 3, c + 5, 5))
    m.box('bell_lip', G, [6.5, c - 5.75, -5.75], [8, c + 5.75, 5.75], 5)
    m.box('band', S, [-0.25, c - 4.4, -4.4], [1.25, c + 4.4, 4.4], 7)
    m.box('clapper_glow', D, [6.75, c - 1.5, -1.5], [8.5, c + 1.5, 1.5], 4)
    m.box('socket', G, [-2, 24, -2], [2, 27, 2], 3)
    for k, z in enumerate((-3, 0, 3)):
        m.box(f'chime_cord_{k}', S, [0.25, c - 7.5, z - 0.3], [0.75, c - 4.4, z + 0.3], 5)
        m.box(f'chime_{k}', G, [-0.5, c - 9.5, z - 0.9], [1.5, c - 7.5, z + 0.9], 4)
    return m


def divine_ash_hammer():
    """灰打ちの大鎚: 頭は金床の形（-x に角、+x に打つ面）、胴に光る熱の継ぎ目と金の箍二本"""
    m = M('divine_ash_hammer')
    grip(m, -6, 6)
    rod(m, 7.5, 25, bands=(15,))
    c = 30
    m.box('waist', S, [-3, c - 3, -3], [3, c + 2, 3], grad=(c - 3, 3, c + 2, 5))
    m.box('top', S, [-8, c + 1, -3.5], [8, c + 5, 3.5], grad=(c + 1, 5, c + 5, 7))
    m.box('base', S, [-5, c - 5, -3.5], [5, c - 2.5, 3.5], grad=(c - 5, 3, c - 2.5, 5))
    m.seg('horn', S, (-8, c + 3), (-13, c + 3.5), 3.5, 2.5, 5, 7, tip=True)
    m.box('face', S, [8, c + 0.5, -3.75], [9.5, c + 5.5, 3.75], 8)
    m.box('seam_glow', D, [-3.4, c - 1.5, -3.4], [3.4, c - 0.5, 3.4], 3)
    for k, x in enumerate((-4.5, 4.5)):
        m.box(f'hoop_{k}', G, [x - 0.75, c + 0.75, -3.9], [x + 0.75, c + 5.25, 3.9], 4)
    m.box('socket', G, [-2, 24, -2], [2, 25.5, 2], 3)
    return m


# ---------------------------------------------------------------- 杖

def divine_dawn_staff():
    """暁の灯杖: 金の灯の籠（四本の柱・屋根・半ば開いた鎧戸）。籠の中に朝の光の目"""
    m = M('divine_dawn_staff')
    grip(m, -14, 2)
    rod(m, 3.5, 33, bands=(13, 23))
    m.box('floor', G, [-4.5, 32.5, -3.5], [4.5, 34.5, 3.5], grad=(32.5, 2, 34.5, 4))
    for k, (x, z) in enumerate(((-3.75, -2.75), (3.75, -2.75), (-3.75, 2.75), (3.75, 2.75))):
        m.box(f'post_{k}', G, [x - 0.6, 34.5, z - 0.6], [x + 0.6, 43, z + 0.6], 4)
    m.box('roof', G, [-5, 43, -4], [5, 44.75, 4], 4)
    m.box('roof_top', G, [-3, 44.75, -2.5], [3, 46.5, 2.5], 5)
    m.seg('finial', G, (0, 46.5), (0, 50), 1.5, 0.75, 4, 5, tip=True)
    m.box('eye_glow', D, [-2.25, 36.25, -2], [2.25, 41.25, 2], grad=(36.25, 3, 41.25, 5))
    m.box('pupil', S, [-0.75, 38, -2.3], [0.75, 39.5, 2.3], 1)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'shutter_{n}', S, (sd * 3.75, 42.5), (sd * 6.5, 37), 3.5, 0.4, 6, 8, zc=-2.9)
    return m


def divine_eyelid_staff():
    """雲の瞼の杖: 一つ目の上下から雲の瞼が半ば閉じる。瞳が光る。目のまわりに金の輪"""
    m = M('divine_eyelid_staff')
    grip(m, -14, 2)
    rod(m, 3.5, 33, bands=(13, 23))
    m.box('socket', G, [-2.5, 32.5, -2.25], [2.5, 35, 2.25], grad=(32.5, 2, 35, 4))
    c = (0, 42)
    m.arc('rim', G, c, 6, 0, 360, 10, 1.25, 1, 3, 5)
    m.box('white', S, [-4.5, c[1] - 2.5, -1.25], [4.5, c[1] + 2.5, 1.25], 9)
    m.box('iris_glow', D, [-2, c[1] - 2, -1.4], [2, c[1] + 2, 1.4], 3)
    m.box('pupil', K, [-0.75, c[1] - 1.25, -1.6], [0.75, c[1] + 1.25, 1.6], 1)
    for sd, n in ((1, 'u'), (-1, 'd')):
        for k, (x, r) in enumerate(((-3, 2.75), (0.5, 3.25), (3.75, 2.5))):
            y = c[1] + sd * 3.5
            m.box(f'lid_{n}{k}', S, [x - r, y - 1.5, -1.75], [x + r, y + 1.5, 1.75], 6 + k * 0.4 if sd > 0 else 4)
    return m


# ---------------------------------------------------------------- 鈴杖

def divine_promise_bell_staff():
    """約束の鈴杖: 白い石の三日月（上へ開く）に縦の金の輪、三日月の四か所に灯（光る）と鈴"""
    m = M('divine_promise_bell_staff')
    grip(m, -14, 2)
    rod(m, 3.5, 34, bands=(13, 23))
    m.box('socket', G, [-2.5, 33.5, -2.25], [2.5, 36, 2.25], grad=(33.5, 2, 36, 4))
    m.arc('crescent', S, (0, 42), 7, 200, 340, 6, 2.5, 1.25, 6, 8)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'horn_{n}', S, (sd * 6.6, 39.6), (sd * 7.25, 46), 2, 1.1, 7, 9, tip=True)
    m.arc('ring', G, (0, 42.5), 4, 0, 360, 8, 1, 0.9, 3, 5)
    for k, ang in enumerate((215, 250, 290, 325)):
        x, y = 7 * math.cos(math.radians(ang)), 42 + 7 * math.sin(math.radians(ang))
        m.box(f'lamp_{k}_glow', D, [x - 0.9, y + 1.25, -0.9], [x + 0.9, y + 3, 0.9], 4)
        m.box(f'cord_{k}', G, [x - 0.3, y - 3, -0.3], [x + 0.3, y - 1.25, 0.3], 3)
        m.box(f'bell_{k}', G, [x - 1.1, y - 4.75, -1.1], [x + 1.1, y - 3, 1.1], grad=(y - 4.75, 3, y - 3, 5))
    return m


def divine_keeper_bell_staff():
    """鐘守の鈴杖: 小さな鐘楼（石の台・四本の柱・金の屋根）に鐘が吊られる。光なし"""
    m = M('divine_keeper_bell_staff')
    grip(m, -14, 2)
    rod(m, 3.5, 32, bands=(13, 23))
    m.box('base', S, [-5, 31.5, -4], [5, 34, 4], grad=(31.5, 4, 34, 6))
    m.box('base_step', S, [-4, 34, -3], [4, 35, 3], 6)
    for k, (x, z) in enumerate(((-4, -3), (4, -3), (-4, 3), (4, 3))):
        m.box(f'pillar_{k}', S, [x - 0.75, 35, z - 0.75], [x + 0.75, 44, z + 0.75], grad=(35, 5, 44, 7))
    m.box('lintel', S, [-5, 44, -4], [5, 45.5, 4], 6)
    m.box('roof_0', G, [-5.75, 45.5, -4.75], [5.75, 47, 4.75], 4)
    m.box('roof_1', G, [-4, 47, -3.25], [4, 48.5, 3.25], 4.5)
    m.box('roof_2', G, [-2, 48.5, -1.75], [2, 50, 1.75], 5)
    m.seg('spire', G, (0, 50), (0, 53.5), 1.25, 0.6, 4, 5, tip=True)
    m.box('hanger', G, [-0.4, 42.5, -0.4], [0.4, 44, 0.4], 3)
    m.box('bell_top', G, [-1.5, 41, -1.5], [1.5, 42.5, 1.5], 4)
    m.box('bell', G, [-2.5, 37.5, -2.5], [2.5, 41, 2.5], grad=(37.5, 2, 41, 4))
    m.box('bell_lip', G, [-3, 36.75, -3], [3, 37.75, 3], 5)
    return m


# ---------------------------------------------------------------- 短剣

def divine_rewind_dagger():
    """巻き戻りの短剣: 黒い硝子の刃に残り火の縁（光る）、欠けた歯車の鍔、時計の針の柄頭"""
    m = M('divine_rewind_dagger')
    grip(m, -4, 4, loop=False)
    m.seg('hand_long', G, (0, -4), (0, -9.5), 1.25, 0.6, 3, 5, tip=True)
    m.seg('hand_short', G, (0, -4.5), (2.5, -7), 1.25, 0.6, 3, 5, tip=True)
    m.box('hand_pin', G, [-1, -5.25, -1], [1, -3.75, 1], 4)
    for k in range(7):
        a = math.radians(-60 + k * 40)
        x, y = 4 * math.cos(a), 7.5 + 4 * math.sin(a) * 0.55
        m.seg(f'gear_{k}', G, (x * 0.7, 7.5 + (y - 7.5) * 0.7), (x * 1.15, 7.5 + (y - 7.5) * 1.15), 1.5, 1, 3, 5, over=0.4)
    m.box('gear_hub', G, [-3, 6, -1.4], [3, 9, 1.4], grad=(6, 2, 9, 4))
    m.box('blade', K, [-2, 9, -0.8], [2, 26, 0.8], grad=(9, 1, 26, 4))
    for sd, n in ((-1, 'l'), (1, 'r')):
        x0, x1 = (2, 2.6) if sd > 0 else (-2.6, -2)
        m.box(f'ember_{n}', D, [x0, 10, -0.5], [x1, 25.5, 0.5], grad=(10, 1, 25.5, 3))
        m.seg(f'tip_{n}', K, (sd * 2, 25.5), (0, 32), 2.5, 0.75, 3, 5, tip=True)
    m.box('tip_core', K, [-1.25, 25.5, -0.75], [1.25, 28, 0.75], 4)
    m.box('facet', K, [-0.4, 10, -0.95], [0.4, 25, 0.95], 5)
    return m


def divine_moth_dagger():
    """灯蛾の短剣: 少し開いた二枚の翅の刃（翅ごとに光る紋が一つ）、金の触角の鍔"""
    m = M('divine_moth_dagger')
    grip(m, -4, 4)
    m.box('thorax', G, [-2, 5.5, -1.5], [2, 9, 1.5], grad=(5.5, 2, 9, 4))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'antenna_{n}', G, [(sd * 1, 8.5), (sd * 4, 10), (sd * 6, 9)], [1, 1], 0.5, 3, 5, tip=True)
        m.chain(f'wing_{n}', S, [(sd * 0.5, 9), (sd * 2.5, 16), (sd * 2.75, 23), (sd * 1, 30)], [4, 4.5, 3], 0.7, 6, 8, tip=True)
        m.box(f'spot_{n}_glow', D, [sd * 2.75 - 1, 16, -0.85], [sd * 2.75 + 1, 19, 0.85], 3)
        m.chain(f'vein_{n}', G, [(sd * 1, 10), (sd * 2.25, 18), (sd * 1.5, 27)], [0.75, 0.75], 0.75, 3, 4, tip=False)
    return m


# ---------------------------------------------------------------- 弓（握りの中心 (12, 15)）

def diamond(m, name, mat, c, s, d, t0, t1, zc=0.0):
    """45 度に回した四角（菱）"""
    h = s / (2 * math.sqrt(2))
    m.seg(name, mat, (c[0] - h, c[1] - h), (c[0] + h, c[1] + h), s, d, t0, t1, over=0, zc=zc)


def mirror(p, sign):
    return (p[0], 15 + sign * (p[1] - 15))


def strings(m, top, bot):
    for key, pull in (('0', 0), ('p0', 2.4), ('p1', 6.8), ('p2', 11.6)):
        if pull == 0:
            m.box(f'string_{key}', 'string', [top[0] - 0.5, bot[1], -0.5], [top[0] + 0.5, top[1], 0.5], 3)
        else:
            mid = (top[0] - pull, 15)
            m.seg(f'string_{key}_u', 'string', mid, top, 1, 0.5, 3, 3, over=0.2)
            m.seg(f'string_{key}_d', 'string', bot, mid, 1, 0.5, 3, 3, over=0.2)


def bow_grip(m):
    m.box('grip', S, [10.5, 10, -1.75], [13.5, 20, 1.75], grad=(10, 4, 20, 6))
    for n, y in (('u', 19.5), ('d', 9)):
        m.box(f'band_{n}', G, [10, y, -2.1], [14, y + 1.5, 2.1], 4)


def divine_plume_bow():
    """巡礼の羽弓（伝説）: 聖堂の柱のような白い石の弓腕を金の縁で挟み、両端は金の尖塔になって弦の先へ伸びる。
    握りの背には暁の光の二重の光輪が立ち、外の輪から金の光条が放たれ、まわりを金の小さな菱が巡る。
    弓腕の中ほどからは左右（奥行き）へ白い巡礼の羽根飾りが立つ。弦には石と金の数珠が連なり、上に暁色の聖印が下がる"""
    m = M('divine_plume_bow')
    m.box('grip', G, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'cap_{n}', S, [9.75, y, -2.3], [14.25, y + 1.5, 2.3], grad=(y, 6, y + 1.5, 8))
    c = (21, 15)
    m.box('halo_stem', G, [13.75, 14.25, -0.7], [14.5, 15.75, 0.7], 4)
    m.arc('halo_rim', G, c, 7, -155, 155, 14, 1.4, 0.8, 3, 5)
    m.arc('halo_out', S, c, 5.5, 0, 360, 12, 1, 0.65, 7, 8)
    m.arc('halo_in_glow', D, c, 3.75, 0, 360, 10, 1.1, 0.85, 3, 5)
    diamond(m, 'halo_core', G, c, 2.75, 1, 4, 5)
    diamond(m, 'halo_core_glow', D, c, 1.25, 1.1, 4, 5)
    for k, a in enumerate((-110, -70, -35, 0, 35, 70, 110)):
        dx, dy = math.cos(math.radians(a)), math.sin(math.radians(a))
        ln = 4.5 if a % 70 == 0 else 3
        m.seg(f'ray_{k}', G, (c[0] + dx * 7.75, c[1] + dy * 7.75), (c[0] + dx * (7.75 + ln), c[1] + dy * (7.75 + ln)), 1.25, 0.55, 4, 5, tip=True, over=0)
    for k, a in enumerate((-55, -15, 15, 55)):
        dx, dy = math.cos(math.radians(a)), math.sin(math.radians(a))
        diamond(m, f'orbit_{k}', G, (c[0] + dx * 14, c[1] + dy * 14), 1.5 if k % 2 else 2, 0.6, 4, 5)
    for k, pts in enumerate(([(14, 8.75), (15.25, 5), (14.25, 1), (15.75, -3), (15, -7.5)], [(12.5, 8.25), (12.75, 4.5), (11.75, 0.5), (12.75, -3.5)])):
        m.chain(f'streamer_{k}', 'rose', pts, [1.75, 1.75, 1.5, 1.25][:len(pts) - 1], 0.3, 1, 3, tip=False)
        m.seg(f'streamer_end_{k}', G, pts[-1], (pts[-1][0], pts[-1][1] - 1.5), 1.6, 0.4, 4, 5, tip=True)
    arm = [(12, 21), (12.75, 27), (12, 33), (10, 38.5), (7, 43), (4, 46)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        pts = [P(q) for q in arm]
        m.chain(f'pillar_{n}', S, pts, [3.5, 3.25, 3, 2.5, 2.25], 1.3, 6, 8, tip=False)
        m.chain(f'gild_back_{n}', G, [P((q[0] + 1.75 - k * 0.1, q[1])) for k, q in enumerate(arm[:5])], [0.8, 0.8, 0.75, 0.7], 1.45, 3, 5, tip=False)
        m.chain(f'gild_belly_{n}', G, [P((q[0] - 1.6 + k * 0.1, q[1])) for k, q in enumerate(arm[1:5])], [0.7, 0.7, 0.6], 1.45, 3, 5, tip=False)
        for k, w in ((2, 4.75), (4, 3.75)):
            x, y = pts[k]
            m.box(f'capital_{n}{k}', G, [x - w / 2, y - 0.9, -1.75], [x + w / 2, y + 0.9, 1.75], 4)
            m.box(f'capital_s_{n}{k}', S, [x - w / 2 + 0.5, y - 1.6, -1.55], [x + w / 2 - 0.5, y + 1.6, 1.55], 7)
        m.chain(f'spire_{n}', G, [P((4, 46)), P((3.5, 49)), P((4.5, 56))], [2.5, 1.75], 1.2, 3, 5, tip=True)
        for k, (a, b) in enumerate((((5.5, 47.5), (8, 52)), ((2.5, 48), (1, 51.5)))):
            m.seg(f'pinnacle_{n}{k}', G, P(a), P(b), 1.25, 0.8, 4, 5, tip=True)
        diamond(m, f'spire_gem_{n}_glow', D, P((3.75, 49)), 1.5, 1.25, 4, 5)
        for zs, zn in ((1, 'f'), (-1, 'b')):
            for k, (x0, y0, x1, y1, z0, z1) in enumerate(((11, 32.5, 13.25, 35, 1.3, 4), (11.25, 35, 13, 37.5, 3.5, 6), (11.5, 37.5, 12.75, 40, 5.5, 7.75), (11.75, 40, 12.5, 41.5, 7.25, 8.75))):
                ya, yb = sorted((P((x0, y0))[1], P((x1, y1))[1]))
                za, zb = (z0, z1) if zs > 0 else (-z1, -z0)
                m.box(f'plume_{n}{zn}{k}', S if k < 3 else G, [x0, ya, za], [x1, yb, zb], 7 + k * 0.6 if k < 3 else 4)
    top, bot = (3.5, 49), (3.5, -19)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        ys = [y for y in (46, 42.5, 39, 35.5, 32, 28.5, 25, 21.5, 8.5, 5, 1.5, -2, -5.5, -9, -12.5, -16)]
        for k, y in enumerate(ys):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            diamond(m, f'string_{key}_bead{k}', G if k % 3 == 0 else S, q, 1.3, 0.7, 4 if k % 3 == 0 else 7, 5 if k % 3 == 0 else 8)
        q = lib.string_at(top, bot, pull, lib.string_t(top, bot, 28.5))
        m.box(f'string_{key}_seal_cord', G, [q[0] - 0.2, q[1] - 2.5, -0.2], [q[0] + 0.2, q[1], 0.2], 4)
        diamond(m, f'string_{key}_seal', G, (q[0], q[1] - 3.75), 2.5, 0.6, 3, 5)
        diamond(m, f'string_{key}_seal_glow', D, (q[0], q[1] - 3.75), 1.25, 0.75, 4, 5)
    return m


def divine_mist_bow():
    """霞鳥の弓（伝説）: 弓腕は霞鳥の灰白の幅広い翼。上の先は霞鳥の頭（金の嘴が前へ）、下の先は長い尾羽が三枚垂れる。
    上の弓腕の腹（弓の懐）からは金の鈴が大中小の三つ吊られ、鈴の舌が暁の光で光る。弓のまわりには霞の帯が
    奥行きの前後に離れて漂い、弦には金の糸が絡んで小さな鈴が上下に付く"""
    m = M('divine_mist_bow')
    m.box('grip', S, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 5, 20.5, 7), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'cap_{n}', G, [9.75, y, -2.3], [14.25, y + 1.5, 2.3], 4)
    wingp = [(12, 20.5), (15, 26), (16.25, 32), (14.75, 38), (11, 42.5), (6.5, 45.5), (3, 47)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'wing_{n}', S, [P(q) for q in wingp], [4.5, 5, 4.5, 4, 3, 2.25], 1.2, 4, 8, tip=False)
        m.chain(f'wing_edge_{n}', G, [P((q[0] + 2.3 - k * 0.2, q[1])) for k, q in enumerate(wingp[1:5])], [0.75, 0.7, 0.65], 1.3, 3, 5, tip=False)
        for k, (a, b, w) in enumerate((((15, 27), (20, 29.5), 2.75), ((16.25, 32.5), (21.25, 34.5), 2.75), ((15, 38), (19.25, 41), 2.25), ((12, 42), (15, 45.5), 2))):
            m.seg(f'covert_{n}{k}', S, P(a), P(b), w, 0.75, 6, 9, tip=True)
    # 上の先: 霞鳥の頭
    m.box('head', S, [1.5, 46.25, -1.6], [5, 49.5, 1.6], grad=(46.25, 6, 49.5, 8))
    m.seg('beak', G, (5, 48.25), (8.25, 47.25), 1.6, 1, 3, 5, tip=True)
    m.box('eye_glow', D, [3.75, 48, -1.7], [4.5, 48.75, 1.7], 4)
    m.chain('crest', S, [(2, 49.5), (0.5, 51.5), (-1.5, 52.25)], [1.25, 1], 0.6, 7, 9, tip=True)
    # 下の先: 長い尾羽三枚
    for k, (e, w) in enumerate((((1.5, -27), 2.25), ((4.5, -28.5), 2.5), ((7.5, -26.5), 2.25))):
        m.seg(f'tail_{k}', S, (3.5 + (k - 1) * 0.75, -16.5), e, w, 0.55, 7, 9, tip=True)
        m.seg(f'tail_tip_{k}', G, e, (e[0] + (k - 1) * 0.5, e[1] - 1.5), w * 0.8, 0.5, 4, 5, tip=True)
    # 懐の鈴三つ（上の弓腕の腹から）
    m.box('bell_bar', G, [6.5, 33.75, -0.35], [14.75, 34.4, 0.35], 4)
    for k, (x, ln, s) in enumerate(((7.25, 3, 3.25), (9.5, 5.5, 2.5), (11.75, 2.25, 2))):
        m.box(f'bell_cord_{k}', G, [x - 0.2, 33.75 - ln, -0.2], [x + 0.2, 33.75, 0.2], 3)
        y1 = 33.75 - ln
        m.box(f'bell_{k}', G, [x - s / 2, y1 - s, -s / 2], [x + s / 2, y1, s / 2], grad=(y1 - s, 2, y1, 5))
        m.box(f'bell_lip_{k}', G, [x - s / 2 - 0.3, y1 - s - 0.4, -s / 2 - 0.3], [x + s / 2 + 0.3, y1 - s, s / 2 + 0.3], 3)
        m.box(f'clapper_{k}_glow', D, [x - 0.4, y1 - s - 1.25, -0.4], [x + 0.4, y1 - s - 0.4, 0.4], 4)
    # 霞の帯（前後に離れて漂う）
    for zs, zn in ((1, 'f'), (-1, 'b')):
        for k, (x, y, w, h) in enumerate(((18.5, 22, 7, 1.25), (20.5, 9, 6, 1), (17, 38.5, 5, 1), (16.5, -6, 5.5, 1.25))):
            z = zs * (4.25 + (k % 2) * 1.5)
            m.box(f'mist_{zn}{k}', S, [x - w / 2, y - h / 2, z - 0.4], [x + w / 2, y + h / 2, z + 0.4], 8 + k % 2 * 0.5)
            m.box(f'mist_tail_{zn}{k}', S, [x + w / 2 - 0.25, y - h / 2 + 0.25, z - 0.3], [x + w / 2 + 2, y + h / 2 - 0.25, z + 0.3], 9)
    top = (3, 47)
    bot = (3, -16.75)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((40, 36, 32, -2, -6, -10)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_thread{k}', G, [q[0] - 0.65, q[1] - 0.3, -0.65], [q[0] + 0.65, q[1] + 0.3, 0.65], 4)
        for k, y in enumerate((28, 1.5)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_chime{k}', G, [q[0] - 0.75, q[1] - 1.75, -0.75], [q[0] + 0.75, q[1] - 0.25, 0.75], 4)
            m.box(f'string_{key}_chime{k}_glow', D, [q[0] - 0.3, q[1] - 2.25, -0.3], [q[0] + 0.3, q[1] - 1.75, 0.3], 4)
    return m


ALL = [divine_sky_sword, divine_warden_blade, divine_bell_hammer, divine_ash_hammer, divine_dawn_staff, divine_eyelid_staff,
       divine_promise_bell_staff, divine_keeper_bell_staff, divine_rewind_dagger, divine_moth_dagger, divine_plume_bow, divine_mist_bow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
