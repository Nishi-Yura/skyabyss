# -*- coding: utf-8 -*-
"""野・荒れ地（look5・個性版）。灰の降る火口の荒れ地で拾う 10 品（英・叙）。
色: 焼けた鉱滓と煤の鉄（slag 赤みの焦げ茶の 1 系統）、差し色は降り積もった灰（ash 冷たい灰白）、光は熔岩（magma 橙赤）を品ごとに一か所。
灰の民の道具と、火口の獣の骨。表面は割れ目と灰の積もりで、光は割れ目の奥から覗かせる。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'slag': ['#140c0a', '#20140e', '#2e1c14', '#3e261a', '#503222', '#64402c', '#7a5038', '#946448', '#b07c5c', '#cc9a78'],
    'cinder': ['#3a3634', '#5a5450', '#7c7570', '#a09892', '#c4bcb6', '#e6e0dc'],
    'magma': ['#6a1004', '#a82006', '#e04010', '#ff7a20', '#ffb850', '#fff0b0'],
})
S, A, G = 'slag', 'cinder', 'magma'


def M(name):
    return Model(name, accent=A, glowmat=G)


def wrap_grip(m, y0, y1, r=1.5):
    """灰色の布を巻いた握り。上下に鉱滓の鉄の輪"""
    m.box('grip', A, [-r, y0, -r], [r, y1, r], grad=(y0, 1, y1, 3), wrap=(y0, y1))
    m.box('ring_d', S, [-r - 0.4, y0 - 1.25, -r - 0.4], [r + 0.4, y0, r + 0.4], 6)
    m.box('ring_u', S, [-r - 0.4, y1, -r - 0.4], [r + 0.4, y1 + 1.25, r + 0.4], 7)


def diamond(m, name, mat, c, s, d, t0, t1, zc=0.0):
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


# ---------------------------------------------------------------- 弓

def wild_ashhook_bow():
    """灰ひきずりの鉤弓: 弓腕の先がそれぞれ内側（弦の方）へ鋭く曲がる鉤。弓腕の中ほどに灰の布が巻かれ、
    握りの上の割れ目から熔岩が覗く"""
    m = M('wild_ashhook_bow')
    m.box('grip', A, [10.5, 10, -1.5], [13.5, 20, 1.5], grad=(10, 1, 20, 3), wrap=(10, 20))
    m.box('crack_glow', G, [13.4, 20.25, -0.5], [14, 22, 0.5], 4)
    arm = [(12, 19.5), (11.75, 27), (10, 34), (7.5, 39.5), (5.5, 42.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'arm_{n}', S, [mirror(p, sign) for p in arm], [3, 2.75, 2.25, 2], 1.25, 4, 7, tip=False)
        m.chain(f'hook_{n}', S, [mirror(p, sign) for p in ((5.5, 42.5), (3.5, 44.5), (1, 43.5), (0.5, 41))], [2, 1.75, 1.25], 1, 7, 9, tip=True)
        y = mirror((10.5, 31), sign)[1]
        m.box(f'rag_{n}', A, [9.25, y - 1.5, -1.5], [12.5, y + 1.5, 1.5], grad=(y - 1.5, 2, y + 1.5, 3))
    strings(m, (3.5, 44), (3.5, -14))
    return m


def wild_crater_longbow():
    """火口の大弓: 背丈ほどの重い弓。弓腕は冷えた溶岩の塊を継いだように節くれ、継ぎ目ごとに赤い割れ目。
    いちばん上の継ぎ目だけが光る。弦受けは灰の骨"""
    m = M('wild_crater_longbow')
    m.box('grip', A, [10.25, 9, -1.75], [13.75, 21, 1.75], grad=(9, 1, 21, 3), wrap=(9, 21))
    nodes = [(12, 20.5), (11.5, 28), (10, 36), (7.75, 43.5), (5, 50), (3.5, 53)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        for k in range(len(nodes) - 1):
            a, b = mirror(nodes[k], sign), mirror(nodes[k + 1], sign)
            m.seg(f'lump_{n}{k}', S, a, b, 3.5 - k * 0.4, 1.6 - k * 0.15, 3 + k, 4 + k)
        for k, p in enumerate(nodes[1:4]):
            q = mirror(p, sign)
            m.box(f'seam_{n}{k}', S, [q[0] - 1.9, q[1] - 0.3, -1.7], [q[0] + 1.9, q[1] + 0.3, 1.7], 1)
        m.seg(f'nock_{n}', A, mirror((3.5, 53), sign), mirror((1, 53.5), sign), 1.75, 0.9, 3, 5)
    m.box('seam_glow', G, [8.1, 43.25, -1.75], [11.9, 43.75, 1.75], 4)
    strings(m, (1.5, 53.5), (1.5, -23.5))
    return m


# ---------------------------------------------------------------- 鎚

def wild_ashrite_maul():
    """灰の民の葬り鎚: 墓石のような長方形の石の頭に、灰の民の印（三本線）が刻まれた大鎚。
    頭の底に骨壺の蓋のような輪、印の溝の一本だけが熔岩で光る"""
    m = M('wild_ashrite_maul')
    wrap_grip(m, -7, 7, 1.6)
    m.box('shaft', S, [-1.3, 8.25, -1.3], [1.3, 22, 1.3], grad=(8.25, 3, 22, 5))
    m.box('lid', A, [-3, 21, -3], [3, 22.5, 3], 3)
    m.box('head', A, [-6.5, 22.5, -3.25], [6.5, 31, 3.25], grad=(22.5, 2, 31, 4))
    m.box('head_cap', A, [-6, 31, -2.75], [6, 32, 2.75], 4)
    for k, x in enumerate((-3, 0, 3)):
        mat = G if k == 1 else S
        m.box(f'rune_{k}', mat, [x - 0.4, 24, -3.4], [x + 0.4, 29.5, -3.1], 3 if k != 1 else 4)
    m.box('face_l', S, [-7.25, 23, -2.75], [-6.5, 30.5, 2.75], 5)
    m.box('face_r', S, [6.5, 23, -2.75], [7.25, 30.5, 2.75], 5)
    return m


def wild_bellows_hammer():
    """灰炉の鞴鎚: 鎚の頭の後ろ（-x）に、革と木の小さな鞴（ふいご）が付く。鞴の口は頭を通って
    打つ面（+x）の穴に抜け、穴の奥が熔岩で光る"""
    m = M('wild_bellows_hammer')
    wrap_grip(m, -6, 6)
    m.box('shaft', S, [-1.25, 7.25, -1.25], [1.25, 21, 1.25], grad=(7.25, 3, 21, 5))
    m.box('head', S, [-2.5, 19.5, -2.75], [5, 26, 2.75], grad=(19.5, 4, 26, 6))
    m.box('face', S, [5, 19, -3], [6.5, 26.5, 3], 7)
    m.box('vent_glow', G, [6.5, 21.75, -1], [6.9, 23.75, 1], 4)
    m.box('bellows_board_u', A, [-8, 24, -2.25], [-2.5, 25, 2.25], 4)
    m.box('bellows_board_d', A, [-8, 20.5, -2.25], [-2.5, 21.5, 2.25], 4)
    m.box('bellows_hide', S, [-7.5, 21.5, -2], [-2.5, 24, 2], grad=(21.5, 2, 24, 3))
    m.seg('bellows_handle', A, (-8, 24.5), (-10.5, 26.5), 1, 0.6, 3, 4)
    return m


# ---------------------------------------------------------------- 杖・鈴杖

def wild_emberbug_staff():
    """熾り蟲の籠杖: 杖の先に鉄の籠（縦の格子）、中で熾り蟲が光る。籠の上に煙突のような細い口、
    籠の外に蟲が二匹とまる（小さな灰の点）"""
    m = M('wild_emberbug_staff')
    wrap_grip(m, -14, 2, 1.3)
    m.box('shaft', S, [-1.15, 3.25, -1.15], [1.15, 30, 1.15], grad=(3.25, 3, 30, 5))
    m.box('cage_floor', S, [-3, 29.5, -3], [3, 30.75, 3], 6)
    m.box('bug_glow', G, [-1.75, 31, -1.75], [1.75, 35, 1.75], grad=(31, 2, 35, 4))
    for k, (x, z) in enumerate(((-2.6, -2.6), (2.2, -2.6), (-2.6, 2.2), (2.2, 2.2), (-0.2, -2.9), (-0.2, 2.5))):
        m.box(f'bar_{k}', S, [x, 30.75, z], [x + 0.4, 36, z + 0.4], 6)
    m.box('cage_roof', S, [-3, 36, -3], [3, 37.25, 3], 7)
    m.box('flue', S, [-0.75, 37.25, -0.75], [0.75, 40, 0.75], 6)
    for k, (x, y) in enumerate(((3.1, 33), (-3.1, 34.5))):
        m.box(f'bug_{k}', A, [x - 0.4, y, -0.5], [x + 0.4, y + 0.9, 0.5], 4)
    return m


def wild_ashsow_bell_staff():
    """灰撒きの鈴杖: 種撒きの柄杓（ひしゃく）を逆さに吊るした鈴杖。杖の先から横へ腕、そこに灰の柄杓が下を向き、
    柄杓の中の舌が熔岩で光る。腕の反対には小さな鈴が二つ"""
    m = M('wild_ashsow_bell_staff')
    wrap_grip(m, -14, 2)
    m.box('shaft', S, [-1.2, 3.25, -1.2], [1.2, 33, 1.2], grad=(3.25, 3, 33, 5))
    m.box('arm', S, [-4.5, 31.5, -1], [7, 33, 1], 6)
    m.box('ladle_rim', A, [3.5, 27, -2.5], [8.5, 28.25, 2.5], 4)
    m.box('ladle_bowl', A, [4, 28.25, -2], [8, 30.75, 2], grad=(28.25, 3, 30.75, 4))
    m.box('ladle_neck', A, [5.5, 30.75, -0.4], [6.5, 31.5, 0.4], 3)
    m.box('tongue_glow', G, [5.5, 26, -0.6], [6.5, 27.5, 0.6], 4)
    for k, x in enumerate((-2.5, -4)):
        m.box(f'cord_{k}', A, [x - 0.15, 28.5 + k, -0.15], [x + 0.15, 31.5, 0.15], 3)
        m.box(f'bell_{k}', S, [x - 1, 26.5 + k, -1], [x + 1, 28.5 + k, 1], 6)
    return m


# ---------------------------------------------------------------- 大剣

def wild_fortress_greatsword():
    """焼け砦の門剣: 砦の門の閂（かんぬき）を叩き延ばした、厚く四角い大剣。刃に鋲の列、
    先は斜めに断ち切られ、鍔は門の蝶番。鋲の一つが熔けて光る"""
    m = M('wild_fortress_greatsword')
    wrap_grip(m, -8, 8, 1.6)
    m.box('pommel', S, [-2.25, -11, -2.25], [2.25, -9.25, 2.25], 6)
    m.box('hinge', S, [-6, 9.25, -2], [6, 12, 2], grad=(9.25, 4, 12, 6))
    for sd in (-1, 1):
        m.box(f'hinge_pin_{sd}', A, [sd * 5.5 - 0.75, 8.5, -2.25], [sd * 5.5 + 0.75, 12.75, 2.25], 4)
    m.box('blade', S, [-4.5, 12, -1.25], [4.5, 42, 1.25], grad=(12, 4, 42, 7))
    m.seg('blade_cut', S, (-4.5, 42), (4.5, 46.5), 3, 1.2, 7, 8, over=0)
    m.box('blade_tip', S, [-1, 42, -1.2], [4.5, 46, 1.2], 7)
    for k, y in enumerate((16, 22, 28, 34)):
        mat = G if k == 2 else A
        m.box(f'stud_{k}', mat, [-0.75, y, -1.6], [0.75, y + 1.5, 1.6], 4 if k == 2 else 5)
    return m


def wild_lavajaw_greatsword():
    """熔け顎の大剣: 刃の両縁がぎざぎざの顎の歯になった大剣（片側 5 本ずつ）。刃の中央に、
    顎の喉のような縦の割れ目があり熔岩が流れる。鍔は顎の蝶番の骨"""
    m = M('wild_lavajaw_greatsword')
    wrap_grip(m, -8, 8, 1.6)
    m.box('pommel', A, [-2, -11, -2], [2, -9.25, 2], 4)
    m.box('jaw_hinge', A, [-5.5, 9.25, -1.75], [5.5, 11.75, 1.75], grad=(9.25, 2, 11.75, 4))
    m.box('blade', S, [-3.5, 11.75, -1.1], [3.5, 42, 1.1], grad=(11.75, 4, 42, 7))
    m.box('throat_glow', G, [-0.6, 14, -1.2], [0.6, 37, 1.2], grad=(14, 1, 37, 4))
    for sd, n in ((-1, 'l'), (1, 'r')):
        for k in range(5):
            y = 15 + k * 5.5
            m.seg(f'tooth_{n}{k}', A, (sd * 3.5, y), (sd * 5.75, y + 2.5), 2, 0.8, 4, 5, tip=True)
    for k, w in enumerate((2.75, 1.75, 0.75)):
        m.box(f'tip_{k}', S, [-w, 42 + k * 1.75, -1], [w, 43.75 + k * 1.75, 1], 7 + k)
    return m


# ---------------------------------------------------------------- 爪・牙

def wild_cinder_claw():
    """灰の屑の双爪: 籠手の甲から、灰の鉄屑を打ち延ばした二枚の湾刀状の爪が平行に前（上）へ伸びる。
    二枚の爪の根元をつなぐ横棒が熔岩で赤い"""
    m = M('wild_cinder_claw')
    wrap_grip(m, -7, 3, 1.4)
    m.box('gauntlet', S, [-3, 3, -2.5], [3, 9, 2.5], grad=(3, 3, 9, 6))
    m.box('knuckle_bar_glow', G, [-2.75, 9, -0.6], [2.75, 10, 0.6], 4)
    for k, x in enumerate((-1.5, 1.5)):
        m.chain(f'claw_{k}', S, [(x, 9), (x + 0.5, 14), (x + 2, 19), (x + 4, 22.5)], [2, 1.75, 1.25], 0.6, 6, 9, tip=True)
    m.box('scrap_0', A, [-3.25, 5, -2.75], [-1.5, 7, -2.5], 3)
    m.box('scrap_1', A, [1, 4, 2.5], [2.75, 5.5, 2.75], 3)
    return m


def wild_venom_fang():
    """灰炉の毒牙: 火口の大蜥蜴の毒牙を、灰の布で柄に括ったもの。牙は内側に毒の溝が走り、
    溝の根元に毒の熔岩色の雫が一つ"""
    m = M('wild_venom_fang')
    wrap_grip(m, -4, 4, 1.35)
    m.box('lash', A, [-2, 4.5, -1.9], [2, 7, 1.9], grad=(4.5, 2, 7, 4), wrap=(4.5, 7))
    m.chain('fang', A, [(0, 6), (-0.5, 11.5), (-1.75, 16.5), (-3.5, 20.5)], [3, 2.25, 1.25], 1.3, 4, 6, tip=True)
    m.chain('groove', S, [(0.25, 8), (-0.25, 12), (-1.25, 15.5)], [0.6, 0.6], 1.35, 2, 3, tip=False)
    m.box('drop_glow', G, [-0.25, 7.25, -1.45], [0.75, 8.25, 1.45], 4)
    return m


ALL = [wild_ashhook_bow, wild_crater_longbow, wild_ashrite_maul, wild_bellows_hammer, wild_emberbug_staff,
       wild_ashsow_bell_staff, wild_fortress_greatsword, wild_lavajaw_greatsword, wild_cinder_claw, wild_venom_fang]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
