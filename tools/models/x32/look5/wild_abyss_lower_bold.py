# -*- coding: utf-8 -*-
"""野・深淵下（look5・個性版）。深淵の下層（雷の落ちる黒い底・古い機械の墓場）で拾う 18 品（叙）。
色: 底の黒鉄（umbra 紫がかった黒の 1 系統）、差し色は錆びた真鍮（brass 緑青の金）、光は底の雷（plasma 淡い紫白）を品ごとに一か所。
古い機械（歯車・砂時計・望遠筒）の部品と、雷に焼かれた硝子。光は割れ目・芯・珠など、品ごとに場所を変える。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'umbra': ['#0c0812', '#140e1c', '#1e1628', '#2a1f36', '#382946', '#473558', '#58436c', '#6c5482', '#82689a', '#9c80b4'],
    'brass': ['#3c3a1e', '#5e5a2c', '#82783c', '#a89a52', '#c8bc74', '#e6dca2'],
    'plasma': ['#4a1e8a', '#7034c4', '#9a5ef0', '#c49aff', '#e4ccff', '#f8f0ff'],
})
U, B, P = 'umbra', 'brass', 'plasma'


def M(name):
    return Model(name, accent=B, glowmat=P)


def coil_grip(m, y0, y1, r=1.5):
    """黒い革を巻いた握り。上下は真鍮の太い輪（機械の継ぎ手）"""
    m.box('grip', U, [-r, y0, -r], [r, y1, r], grad=(y0, 3, y1, 5), wrap=(y0, y1))
    m.box('joint_d', B, [-r - 0.45, y0 - 1.25, -r - 0.45], [r + 0.45, y0, r + 0.45], 3)
    m.box('joint_u', B, [-r - 0.45, y1, -r - 0.45], [r + 0.45, y1 + 1.25, r + 0.45], 4)


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


# ================================================================ 弓

def wild_calling_bow():
    """呼び声の弓（エピック）: 弓腕そのものが真鍮の角笛。握りから先へ太りながら反り、管の継ぎ目に濃い帯。
    弓の先で口が開いて奥が雷の色に光り、口から音の波が雷の色の弧になって三重に広がる（板の絵）"""
    m = M('wild_calling_bow')
    m.box('grip', U, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 3, 20.5, 5), wrap=(9.5, 20.5))
    arm = [(12, 21), (13.5, 27), (13.5, 33), (11.5, 38.5), (8, 42), (4.5, 44)]
    c0 = (4, 44.75)
    cv = lib.Canvas(-6, 45, 14, 55)

    def f(x, y, cur):
        dx, dy = x - c0[0], y - c0[1]
        r = math.hypot(dx, dy)
        a = math.degrees(math.atan2(dy, dx))
        if not 30 < a < 150:
            return None
        for k, rr in enumerate((3.25, 5.25, 7.5)):
            if abs(r - rr) < 0.45 - k * 0.05:
                return (P, 4 - k)
        return None
    cv.paint(f)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P_ = lambda q, s=sign: mirror(q, s)
        m.chain(f'horn_{n}', B, [P_(q) for q in arm], [2.25, 2.75, 3.25, 4, 4.75], 1.4, 2, 5, tip=False)
        for k in range(1, 5):
            q = P_(arm[k])
            w = 1.6 + k * 0.45
            m.box(f'ring_{n}{k}', B, [q[0] - w, q[1] - 0.35, -1.65 - k * 0.1], [q[0] + w, q[1] + 0.35, 1.65 + k * 0.1], 1)
        c = P_(c0)
        m.box(f'mouth_{n}', B, [c[0] - 3, c[1] - 1, -2.5], [c[0] + 3, c[1] + 1, 2.5], 5)
        m.box(f'mouth_in_{n}_glow', P, [c[0] - 2, c[1] - 1.05 if sign < 0 else c[1] + 0.2, -1.7], [c[0] + 2, c[1] - 0.2 if sign < 0 else c[1] + 1.05, 1.7], 4)
        cv.put(m, f'wave_{n}_glow', at=0.0, mirror15=sign < 0)
    strings(m, (3.25, 43.75), (3.25, -13.75))
    return m


def wild_flash_bow():
    """閃光の弓: 弓腕が稲妻のように折れ曲がった黒鉄の弓。上の弓腕の先端だけが雷の色に光り、
    折れ目ごとに真鍮の留め金"""
    m = M('wild_flash_bow')
    m.box('grip', U, [10.5, 10, -1.5], [13.5, 20, 1.5], grad=(10, 3, 20, 5), wrap=(10, 20))
    zig = [(12, 19.5), (12.5, 25), (9.5, 29), (10.5, 34), (7, 38.5), (5, 42.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'bolt_{n}', U, [mirror(p, sign) for p in zig], [2.75, 2.5, 2.25, 2, 1.75], 1.25, 4, 7, tip=False)
        for k, p in enumerate(zig[1:5]):
            q = mirror(p, sign)
            m.box(f'clasp_{n}{k}', B, [q[0] - 1.4, q[1] - 0.4, -1.35], [q[0] + 1.4, q[1] + 0.4, 1.35], 4)
        tip = mirror((5, 42.5), sign)
        mat = P if sign > 0 else U
        m.seg(f'tip_{n}' + ('_glow' if sign > 0 else ''), mat, tip, mirror((3.75, 44.5), sign), 1.5, 1.1, 4, 6, tip=True)
    strings(m, (4, 44), (4, -14))
    for sign, n in ((1, 'u'), (-1, 'd')):
        for zs, zn in ((1, 'f'), (-1, 'b')):
            q = mirror((9.5, 29), sign)
            z0 = 1.3 if zs > 0 else -1.3
            m.box(f'rod_{n}{zn}', B, [q[0] - 0.4, q[1] - 0.4, min(z0, z0 + zs * 3.25)], [q[0] + 0.4, q[1] + 0.4, max(z0, z0 + zs * 3.25)], 4)
            z1 = z0 + zs * 3.25
            m.box(f'rod_ball_{n}{zn}', B, [q[0] - 0.75, q[1] - 0.75, min(z1, z1 + zs * 1.25)], [q[0] + 0.75, q[1] + 0.75, max(z1, z1 + zs * 1.25)], 5)
    return m


def wild_scope_longbow():
    """遠見の大弓（エピック）: 弓腕そのものが伸び縮みする望遠筒。握りから先へ一段ずつ細る黒鉄の筒が重なり、
    継ぎ目ごとに真鍮の襟。先のレンズが雷の色に光り、レンズの先に照準の輪（板の絵、十字と目盛り）が浮かぶ"""
    m = M('wild_scope_longbow')
    m.box('grip', U, [10.25, 9, -1.9], [13.75, 21, 1.9], grad=(9, 3, 21, 5), wrap=(9, 21))
    arm = [(12, 21), (12.5, 31), (11, 40), (8, 48), (4.5, 53)]
    c0 = (2.5, 58.5)
    cv = lib.Canvas(-2, 54, 7, 63)

    def f(x, y, cur):
        dx, dy = x - c0[0], y - c0[1]
        r = math.hypot(dx, dy)
        if 3.6 < r < 4.2:
            return (P, 4)
        if r < 3.6 and (abs(dx) < 0.25 or abs(dy) < 0.25) and r > 0.8:
            return (P, 3)
        if 4.2 <= r < 4.6 and (abs(dx) < 0.3 or abs(dy) < 0.3):
            return (B, 5)
        return None
    cv.paint(f)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P_ = lambda q, s=sign: mirror(q, s)
        for k in range(len(arm) - 1):
            a, b = arm[k], arm[k + 1]
            w = 4 - k * 0.6
            m.seg(f'tube_{n}{k}', U, P_(a), P_(b), w, w / 2.2, 4 + k * 0.6, 6 + k * 0.5, over=0.2)
            m.seg(f'collar_{n}{k}', B, P_(b), P_((b[0] + (a[0] - b[0]) * 0.12, b[1] + (a[1] - b[1]) * 0.12)), w + 0.8, w / 2.2 + 0.4, 4, 5, over=0)
        q = P_((4.5, 53))
        m.box(f'lens_{n}_glow', P, [q[0] - 1, q[1] - 0.5, -1], [q[0] + 1, q[1] + 0.5, 1], 4)
        cv.put(m, f'reticle_{n}_glow', at=0.0, mirror15=sign < 0)
    strings(m, (3.25, 54), (3.25, -24))
    return m


# ================================================================ 剣・大剣・短剣

def wild_fulgurite_sword():
    """閃電岩の剣: 雷に焼かれた砂が固まった硝子の剣。黒い硝子の刃の両側から、小さな枝が斜めに伸び、
    刃の芯を雷の色の筋が通る。鍔は真鍮の継ぎ手"""
    m = M('wild_fulgurite_sword')
    coil_grip(m, -6, 6)
    m.box('pommel', B, [-1.5, -9, -1.5], [1.5, -7.25, 1.5], 4)
    m.box('guard', B, [-3.25, 7.25, -1.75], [3.25, 9, 1.75], grad=(7.25, 3, 9, 5))
    m.box('blade', U, [-1.75, 9, -1], [1.75, 27, 1], grad=(9, 5, 27, 8))
    m.seg('tip', U, (0, 27), (0, 31.5), 3.5, 2, 7, 9, tip=True)
    m.box('vein_glow', P, [-0.35, 10.5, -1.1], [0.35, 26, 1.1], grad=(10.5, 2, 26, 4))
    for k, (y, sd) in enumerate(((12.5, 1), (16, -1), (20, 1), (23.5, -1))):
        m.seg(f'branch_{k}', U, (sd * 1.5, y), (sd * 4, y + 2.75), 1.25, 0.8, 6, 8, tip=True)
    return m


def wild_chain_greatsword():
    """鎖巻きの大剣: 分厚い黒鉄の大剣に真鍮の鎖が斜めに三重に巻き付き、柄頭から短い鎖の先に重りが下がる。
    重りの中で雷の色が脈打つ"""
    m = M('wild_chain_greatsword')
    coil_grip(m, -8, 8, 1.6)
    m.box('guard', U, [-5, 9, -2], [5, 11.5, 2], grad=(9, 4, 11.5, 6))
    m.box('blade', U, [-3.5, 11.5, -1.25], [3.5, 41, 1.25], grad=(11.5, 4, 41, 7))
    for k, w in enumerate((2.75, 1.75, 0.75)):
        m.box(f'tip_{k}', U, [-w, 41 + k * 1.75, -1.2 + k * 0.15], [w, 42.75 + k * 1.75, 1.2 - k * 0.15], 7 + k)
    for k, y in enumerate((15, 23, 31)):
        m.seg(f'wrap_{k}', B, (-3.9, y), (3.9, y + 3.5), 1.1, 2.9, 3, 5, over=0)
    m.chain('drop_chain', B, [(0, -9.25), (-0.5, -11.5), (0.25, -13.5)], [0.7, 0.7], 0.7, 3, 4, tip=False)
    m.box('weight', U, [-1.25, -16.25, -1.25], [1.25, -13.5, 1.25], 6)
    m.box('weight_glow', P, [-0.6, -15.75, -1.3], [0.6, -14, 1.3], 4)
    return m


def wild_fishhook_knife():
    """釣り針の小刀: 刃の先が大きな釣り針のように後ろへ返り、針の返しが一つ立つ。返しの付け根が雷の色に光る。
    柄頭には糸を通す真鍮の輪"""
    m = M('wild_fishhook_knife')
    coil_grip(m, -4, 4, 1.25)
    m.box('eye', B, [-1, -7, -0.35], [1, -5.25, 0.35], 4)
    m.box('bolster', U, [-1.6, 5.25, -1.1], [1.6, 6.5, 1.1], 5)
    m.box('shank', U, [-0.8, 6.5, -0.7], [0.8, 15, 0.7], grad=(6.5, 5, 15, 7))
    m.chain('bend', U, [(0, 15), (-0.75, 17.5), (-2.75, 18.5), (-4.5, 17), (-4.75, 14.5)], [1.5, 1.4, 1.25, 1.1], 0.7, 6, 8, tip=True)
    m.seg('barb', U, (-4.5, 15.5), (-3.25, 13.5), 0.8, 0.6, 7, 8, tip=True)
    m.box('barb_glow', P, [-5, 15.25, -0.75], [-4, 16, 0.75], 4)
    return m


def wild_spark_dagger():
    """火花の短剣: 細い三角の刃の根元に、真鍮の二本の電極が角のように立ち、その間に雷の火花が渡る。
    刃は黒鉄"""
    m = M('wild_spark_dagger')
    coil_grip(m, -4, 4, 1.25)
    m.box('guard', U, [-2.5, 5.25, -1], [2.5, 6.5, 1], 5)
    m.seg('blade', U, (0, 6.5), (0, 19), 2.5, 1, 6, 9, tip=True)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'electrode_{n}', B, (sd * 2, 6), (sd * 3.25, 10), 0.9, 0.9, 3, 5)
    m.box('arc_glow', P, [-3.1, 9.5, -0.3], [3.1, 10.1, 0.3], 4)
    return m


# ================================================================ 爪

def wild_ash_sickle_claw():
    """灰鎌の爪: 籠手から、草刈り鎌の小さな刃が三枚、外（-x）へ向かって弧を描いて伸びる。
    籠手の甲の真鍮の鋲の一つが雷の色"""
    m = M('wild_ash_sickle_claw')
    coil_grip(m, -7, 3, 1.4)
    m.box('gauntlet', U, [-2.75, 3.25, -2.5], [2.75, 8.5, 2.5], grad=(3.25, 4, 8.5, 6))
    for k, (y, z) in enumerate(((5, -1.6), (6.75, 0), (5, 1.6))):
        mat = P if k == 1 else B
        m.box(f'stud_{k}' + ('_glow' if k == 1 else ''), mat, [2.75, y - 0.5, z - 0.5], [3.1, y + 0.5, z + 0.5], 4)
    for k, x in enumerate((-1.5, 0, 1.5)):
        m.arc(f'sickle_{k}', U, (x - 3.5, 12 + k * 1.25), 4.25, -10, 110, 5, [1.25, 1.25, 1, 0.9, 0.7], 0.6, 6, 9)
        m.seg(f'root_{k}', U, (x, 8.5), (x + 0.5, 12), 1.2, 0.8, 5, 6)
    return m


# ================================================================ 鎚・斧・槍

def wild_abyss_mace():
    """深淵の鎚矛: 黒鉄の芯の周りに四枚の鰭（フランジ）が立つ鎚矛。鰭の間の奥から雷の光が覗く。
    頭の上に短い真鍮の棘"""
    m = M('wild_abyss_mace')
    coil_grip(m, -6, 6)
    m.box('shaft', U, [-1.2, 7.25, -1.2], [1.2, 19, 1.2], grad=(7.25, 3, 19, 5))
    m.box('core', U, [-2, 19, -2], [2, 26, 2], grad=(19, 4, 26, 6))
    m.box('core_glow', P, [-2.1, 21, -2.1], [2.1, 24, 2.1], 4)
    for k, (a, b) in enumerate((((-2, 0), (-4.25, 0)), ((2, 0), (4.25, 0)))):
        m.box(f'flange_x{k}', U, [min(a[0], b[0]), 19.5, -0.5], [max(a[0], b[0]), 25.5, 0.5], grad=(19.5, 6, 25.5, 8))
    for k, z in enumerate((-1, 1)):
        m.box(f'flange_z{k}', U, [-0.5, 19.5, min(z * 2, z * 4.25)], [0.5, 25.5, max(z * 2, z * 4.25)], grad=(19.5, 6, 25.5, 8))
    m.box('cap', B, [-1.5, 26, -1.5], [1.5, 27, 1.5], 4)
    m.seg('spike', B, (0, 27), (0, 29.5), 1.5, 1.5, 4, 5, tip=True)
    m.box('collar', B, [-1.75, 18.25, -1.75], [1.75, 19.25, 1.75], 3)
    return m


def wild_cog_hammer():
    """歯車の大鎚: 大鎚の頭が一枚の大きな歯車。歯車の面は柄と同じ平面に立ち、縁に八つの歯、中心の軸受けが雷の色。
    柄は黒鉄、歯車は錆びた真鍮"""
    m = M('wild_cog_hammer')
    coil_grip(m, -7, 7, 1.6)
    m.box('shaft', U, [-1.3, 8.25, -1.3], [1.3, 20, 1.3], grad=(8.25, 3, 20, 5))
    cx, cy, r = 0, 26, 5
    m.box('wheel', B, [cx - r, cy - r + 1.5, -1.5], [cx + r, cy + r - 1.5, 1.5], grad=(cy - r, 2, cy + r, 4))
    m.box('wheel_v', B, [cx - r + 1.5, cy - r, -1.5], [cx + r - 1.5, cy + r, 1.5], grad=(cy - r, 2, cy + r, 4))
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        x, y = cx + math.cos(a) * (r + 0.5), cy + math.sin(a) * (r + 0.5)
        m.box(f'tooth_{k}', B, [x - 0.9, y - 0.9, -1.4], [x + 0.9, y + 0.9, 1.4], 3)
    m.box('hub_glow', P, [-1.25, cy - 1.25, -1.6], [1.25, cy + 1.25, 1.6], 4)
    m.box('hub_ring', U, [-2, cy - 2, -1.55], [2, cy + 2, 1.55], 6)
    return m


def wild_molten_axe():
    """熔け斧: 黒鉄の半月の斧の刃が熔けかけて下へ垂れ、垂れた雫が三つ。刃の縁に雷の色の熱の筋。
    柄頭は真鍮の帽子"""
    m = M('wild_molten_axe')
    coil_grip(m, -6, 6)
    m.box('shaft', U, [-1.25, 7.25, -1.25], [1.25, 25, 1.25], grad=(7.25, 3, 25, 5))
    m.box('cheek', U, [1, 18, -1.3], [4.5, 25, 1.3], grad=(18, 4, 25, 6))
    m.arc('blade', U, (3, 21.5), 5, -80, 80, 5, [2.5, 3, 3.25, 3, 2.5], 1.2, 5, 7)
    m.arc('edge_glow', P, (3, 21.5), 5.9, -60, 60, 4, [0.7, 0.7, 0.7, 0.7], 1.3, 3, 4)
    for k, (x, y, h) in enumerate(((5.5, 16.5, 2.5), (7.25, 17.75, 1.75), (3.75, 16.75, 1.5))):
        m.box(f'drip_{k}', U, [x - 0.5, y - h, -0.6], [x + 0.5, y, 0.6], grad=(y - h, 4, y, 6))
    m.box('cap', B, [-1.6, 25, -1.6], [1.6, 26.5, 1.6], 4)
    return m


def wild_twin_spear():
    """双子の槍: 長柄の先が二股に分かれ、平行な二本の穂が並ぶ。二本の穂の間に雷の色の弧が一本渡る。
    分かれ目は真鍮の継ぎ手"""
    m = M('wild_twin_spear')
    m.box('grip', U, [-1.5, -16, -1.5], [1.5, 0, 1.5], grad=(-16, 2, 0, 4), wrap=(-16, 0))
    m.box('butt', B, [-1.9, -18.5, -1.9], [1.9, -16, 1.9], 3)
    m.seg('butt_tip', B, (0, -18.5), (0, -20.5), 2, 1.1, 3, 5, tip=True)
    m.box('haft', U, [-1.25, 0, -1.25], [1.25, 33, 1.25], grad=(0, 3, 33, 5))
    m.box('yoke', B, [-3.5, 33, -1.5], [3.5, 35, 1.5], grad=(33, 3, 35, 5))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'tine_{n}', U, [sd * 2.25 - 0.8, 35, -0.8], [sd * 2.25 + 0.8, 44, 0.8], grad=(35, 5, 44, 7))
        m.seg(f'point_{n}', U, (sd * 2.25, 44), (sd * 2.25, 49), 2, 1, 7, 9, tip=True)
    m.box('arc_glow', P, [-1.5, 40, -0.35], [1.5, 40.75, 0.35], 4)
    return m


# ================================================================ 杖・香炉

def wild_ballspark_staff():
    """球雷の杖: 杖の先で、真鍮の二つの輪（縦と横）が交差した籠が、雷の球を閉じ込める。
    籠の下に絶縁の黒い受け皿"""
    m = M('wild_ballspark_staff')
    coil_grip(m, -14, 2, 1.3)
    m.box('shaft', U, [-1.1, 3.25, -1.1], [1.1, 29, 1.1], grad=(3.25, 3, 29, 5))
    m.box('dish', U, [-2.5, 29, -2.5], [2.5, 30, 2.5], 6)
    m.arc('ring_v', B, (0, 34), 3.75, 0, 360, 10, [0.9] * 10, 0.9, 3, 5)
    m.box('ring_h', B, [-3.75, 33.6, -3.75], [3.75, 34.4, -3.1], 4)
    m.box('ring_h2', B, [-3.75, 33.6, 3.1], [3.75, 34.4, 3.75], 4)
    m.box('ball_glow', P, [-1.75, 32.25, -1.75], [1.75, 35.75, 1.75], grad=(32.25, 3, 35.75, 5))
    return m


def wild_deep_eye_staff():
    """深淵の眼の杖: 杖の先に、上下のまぶたの間から一つの大きな眼が覗く。眼の瞳が雷の色に光り、
    まぶたは黒鉄の板、周りに真鍮のまつげが四本"""
    m = M('wild_deep_eye_staff')
    coil_grip(m, -14, 2, 1.3)
    m.box('shaft', U, [-1.1, 3.25, -1.1], [1.1, 29, 1.1], grad=(3.25, 3, 29, 5))
    m.box('socket', U, [-2, 28.5, -2], [2, 30.25, 2], 5)
    m.box('eyeball', B, [-3, 30.25, -1.75], [3, 35.75, 1.75], grad=(30.25, 4, 35.75, 5))
    m.box('pupil_glow', P, [-1.1, 31.9, -1.9], [1.1, 34.1, 1.9], 4)
    m.box('lid_up', U, [-3.4, 34.5, -2.1], [3.4, 36.5, 2.1], grad=(34.5, 6, 36.5, 7))
    m.box('lid_dn', U, [-3.4, 29.75, -2.1], [3.4, 31.5, 2.1], 5)
    for k, x in enumerate((-2.5, -0.85, 0.85, 2.5)):
        m.seg(f'lash_{k}', B, (x, 36.5), (x * 1.4, 38.5), 0.7, 0.6, 3, 4, tip=True)
    return m


def wild_hourglass_staff():
    """砂時計の杖: 杖の先に真鍮の枠で支えた砂時計。上の玉は黒い硝子で空っぽ、下の玉に雷の色の砂が溜まる。
    枠の柱は二本、上下に真鍮の円盤"""
    m = M('wild_hourglass_staff')
    coil_grip(m, -14, 2, 1.3)
    m.box('shaft', U, [-1.1, 3.25, -1.1], [1.1, 28.5, 1.1], grad=(3.25, 3, 28.5, 5))
    m.box('plate_d', B, [-3, 28.5, -2.25], [3, 29.5, 2.25], 4)
    m.box('plate_u', B, [-3, 38, -2.25], [3, 39, 2.25], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'post_{n}', B, [sd * 2.5 - 0.4, 29.5, -0.4], [sd * 2.5 + 0.4, 38, 0.4], 3)
    m.box('bulb_low', U, [-1.6, 29.5, -1.6], [1.6, 33, 1.6], 5)
    m.box('sand_glow', P, [-1.65, 29.5, -1.65], [1.65, 31.25, 1.65], 4)
    m.box('neck', U, [-0.5, 33, -0.5], [0.5, 34.5, 0.5], 6)
    m.box('bulb_up', U, [-1.6, 34.5, -1.6], [1.6, 38, 1.6], 7)
    m.seg('finial', B, (0, 39), (0, 41), 1.25, 1.25, 4, 5, tip=True)
    return m


def wild_calming_censer():
    """鎮めの香炉: 杖の先が下へ鉤のように曲がり、短い鎖で丸い真鍮の香炉を吊る。香炉の側面の格子窓から
    雷の色の煙火が漏れ、蓋には黒い摘み"""
    m = M('wild_calming_censer')
    coil_grip(m, -14, 2, 1.3)
    m.box('shaft', U, [-1.1, 3.25, -1.1], [1.1, 32, 1.1], grad=(3.25, 3, 32, 5))
    m.chain('crook', U, [(0, 31.5), (1.25, 34.5), (3.75, 35.25), (5.25, 33.5)], [2, 1.75, 1.5], 1.4, 5, 7, tip=False)
    m.box('chain', B, [5, 29.5, -0.2], [5.4, 33.25, 0.2], 3)
    m.box('lid', U, [3.5, 28.5, -1.75], [7, 29.5, 1.75], 6)
    m.box('knob', U, [4.85, 29.5, -0.4], [5.65, 30, 0.4], 7)
    m.box('pot', B, [3.25, 24.5, -2], [7.25, 28.5, 2], grad=(24.5, 2, 28.5, 4))
    m.box('window_glow', P, [3.15, 25.75, -1], [7.35, 27.25, 1], 4)
    m.box('foot', B, [4.25, 23.75, -1.25], [6.25, 24.5, 1.25], 2)
    return m


# ================================================================ 鈴杖・祈り柱

def wild_raincall_bell_staff():
    """雨呼びの鈴杖: 杖の先に黒鉄の小さな傘が開き、傘の縁から雨粒の形の鈴が四つ下がる。
    手前の一粒だけが雷の色に光る"""
    m = M('wild_raincall_bell_staff')
    coil_grip(m, -14, 2)
    m.box('shaft', U, [-1.2, 3.25, -1.2], [1.2, 35, 1.2], grad=(3.25, 3, 35, 5))
    m.box('canopy_low', U, [-5, 32, -3], [5, 33, 3], 5)
    m.box('canopy_mid', U, [-3.5, 33, -2.25], [3.5, 34.25, 2.25], 6)
    m.box('canopy_top', U, [-1.75, 34.25, -1.25], [1.75, 35.25, 1.25], 7)
    m.seg('tip', B, (0, 35.25), (0, 37.5), 1.2, 1.2, 4, 5, tip=True)
    for k, (x, z) in enumerate(((-4.25, 0), (4.25, 0), (0, -2.4), (0, 2.4))):
        mat = P if k == 2 else B
        m.box(f'string_{k}', U, [x - 0.15, 29.5, z - 0.15], [x + 0.15, 32, z + 0.15], 3)
        m.box(f'drop_{k}' + ('_glow' if k == 2 else ''), mat, [x - 0.75, 27.5, z - 0.75], [x + 0.75, 29.5, z + 0.75], 4)
        m.seg(f'drop_top_{k}', mat, (x, 29.5), (x, 30.5), 1, 1, 4, 5, tip=True, zc=z)
    return m


def wild_effigy_totem():
    """人形の祈り柱: 黒鉄の柱の上に、真鍮の針金で組んだ人形（頭・広げた腕・胴）が括られる。
    人形の胸の奥に雷の色の心臓が一つ。柱には針金がらせんに巻く"""
    m = M('wild_effigy_totem')
    coil_grip(m, -14, 2, 1.6)
    m.box('pillar', U, [-1.75, 3.25, -1.75], [1.75, 27, 1.75], grad=(3.25, 3, 27, 5))
    for k, y in enumerate((8, 14, 20)):
        m.seg(f'wire_{k}', B, (-1.9, y), (1.9, y + 2.5), 0.6, 3.9, 3, 4, over=0)
    m.box('body', B, [-2, 27, -1.25], [2, 33, 1.25], grad=(27, 2, 33, 4))
    m.box('heart_glow', P, [-0.75, 29.5, -1.35], [0.75, 31, 1.35], 4)
    m.box('arms', B, [-6, 31, -0.6], [6, 32.25, 0.6], 3)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'hand_{n}', B, (sd * 6, 31.5), (sd * 7, 29.5), 0.9, 0.9, 3, 4, tip=True)
    m.box('neck', B, [-0.5, 33, -0.5], [0.5, 34, 0.5], 3)
    m.box('head', B, [-1.5, 34, -1.5], [1.5, 37, 1.5], grad=(34, 3, 37, 5))
    m.box('binding', U, [-2.2, 26.25, -2.2], [2.2, 27.75, 2.2], 2)
    return m


ALL = [wild_calling_bow, wild_flash_bow, wild_scope_longbow, wild_fulgurite_sword, wild_chain_greatsword,
       wild_fishhook_knife, wild_spark_dagger, wild_ash_sickle_claw, wild_abyss_mace, wild_cog_hammer,
       wild_molten_axe, wild_twin_spear, wild_ballspark_staff, wild_deep_eye_staff, wild_hourglass_staff,
       wild_calming_censer, wild_raincall_bell_staff, wild_effigy_totem]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
