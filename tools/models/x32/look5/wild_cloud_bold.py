# -*- coding: utf-8 -*-
"""野・雲（look5・個性版）。雲の上の浮島で拾う 6 品（叙）。
色: 雲の白銀（nimbus 青みの白〜灰青の 1 系統）、差し色は朝焼けの淡い金（gilt）、光は空の青（azure）を品ごとに一か所。
雲を固めた白い金属と、鳥の羽の意匠。丸い雲のふくらみ（段々の箱）を形の芯にする。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'nimbus': ['#2a3446', '#3a465c', '#4c5a72', '#606e88', '#76849e', '#8e9cb4', '#a8b4c8', '#c2ccdc', '#dce2ec', '#f2f5fa'],
    'gilt': ['#5a4420', '#86682e', '#b08e44', '#d2b264', '#ecd290', '#fcf0c8'],
    'azure': ['#0c3a7a', '#1660b0', '#2a8ce0', '#5cb8f8', '#a4dcff', '#e6f6ff'],
})
N, G, Z = 'nimbus', 'gilt', 'azure'


def M(name):
    return Model(name, accent=G, glowmat=Z)


def ribbon_grip(m, y0, y1, r=1.5):
    """白い布を斜めに巻いた握り。上下は金の細い輪"""
    m.box('grip', N, [-r, y0, -r], [r, y1, r], grad=(y0, 4, y1, 6), wrap=(y0, y1))
    m.box('gilt_d', G, [-r - 0.3, y0 - 0.75, -r - 0.3], [r + 0.3, y0, r + 0.3], 3)
    m.box('gilt_u', G, [-r - 0.3, y1, -r - 0.3], [r + 0.3, y1 + 0.75, r + 0.3], 4)


def puff(m, name, c, w, h, d, t=7, mat=N):
    """雲のふくらみ: 幅の違う 3 段の箱を重ねて丸く見せる"""
    x, y = c
    m.box(f'{name}_a', mat, [x - w / 2, y, -d / 2], [x + w / 2, y + h, d / 2], t)
    m.box(f'{name}_b', mat, [x - w / 2 + 0.75, y - 0.75, -d / 2 + 0.4], [x + w / 2 - 0.75, y, d / 2 - 0.4], t - 2)
    m.box(f'{name}_c', mat, [x - w / 2 + 0.75, y + h, -d / 2 + 0.4], [x + w / 2 - 0.75, y + h + 0.75, d / 2 - 0.4], t + 1)


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


def wild_skylark_bow():
    """雲雀の弓（ヒロイック）: 弓腕が大きく広げた雲雀の翼。腕の背に長短二列の風切羽が重なって後ろへ流れ、羽根の先は金。
    腕の外縁を金の縁取りが走り、握りの背からは金の小翼が上下に開く。握りの前に空の青の宝玉、両端の先にも青い光の羽先。
    握りの下から金の飾り紐が垂れる"""
    m = M('wild_skylark_bow')
    m.box('grip', N, [10.5, 10, -1.5], [13.5, 20, 1.5], grad=(10, 4, 20, 6), wrap=(10, 20))
    for k, y in enumerate((9.25, 20)):
        m.box(f'grip_cap_{k}', G, [10.2, y, -1.8], [13.8, y + 0.75, 1.8], 4)
    m.box('gem_seat', G, [13.4, 13.5, -1.4], [14.4, 16.5, 1.4], 3)
    diamond(m, 'gem_glow', Z, (15, 15), 3, 2, 3, 5)
    arm = [(12, 19.5), (11, 27), (9.25, 34), (7, 39.5), (4.75, 43.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'arm_{n}', N, [mirror(p, sign) for p in arm], [3.25, 2.75, 2.25, 1.75], 1.3, 5, 8, tip=False)
        m.chain(f'edge_{n}', G, [mirror((p[0] + 1.6 - k * 0.15, p[1]), sign) for k, p in enumerate(arm[:4])], [0.75, 0.7, 0.6], 1.45, 3, 5, tip=False)
        m.chain(f'winglet_{n}', G, [mirror(p, sign) for p in ((13.5, 17.5), (16.5, 19.5), (19, 23), (19.75, 26.5))], [2.5, 2, 1.25], 0.9, 3, 5, tip=True)
        for k, (x, y, ln) in enumerate(((11.75, 23.5, 7.5), (10.25, 29.5, 8.5), (8.5, 35, 7.5), (6.5, 39.5, 5.5))):
            a, b = mirror((x, y), sign), mirror((x + ln, y - ln * 0.45), sign)
            m.seg(f'quill_{n}{k}', N, a, b, 2.5, 0.8, 6, 8, tip=False)
            m.seg(f'quill_tip_{n}{k}', G, b, (b[0] + 1.75, b[1] - sign * 1), 2, 0.8, 4, 5, tip=True)
        for k, (x, y) in enumerate(((11, 26.5), (9.25, 32.5), (7.25, 37.5))):
            a = mirror((x, y), sign)
            m.seg(f'covert_{n}{k}', N, a, mirror((x + 3.25, y - 1.5), sign), 2, 0.6, 7, 9, tip=True, zc=0.35)
        m.chain(f'tip_{n}', G, [mirror(p, sign) for p in ((4.75, 43.5), (3.25, 45.5), (4.25, 47.5))], [1.4, 1.1], 0.9, 4, 5, tip=False)
        diamond(m, f'tip_glow_{n}', Z, mirror((4.75, 48), sign), 1.5, 1.1, 4, 5)
    m.chain('tassel', G, [(12.75, 9.25), (13.75, 6.5), (13.25, 3.5)], [0.6, 0.6], 0.6, 3, 4, tip=False)
    diamond(m, 'tassel_bead', Z, (13.25, 2.75), 1.25, 1.25, 4, 5)
    strings(m, (3.25, 45.5), (3.25, -15.5))
    return m


def wild_cloudhymn_bell_staff():
    """雲歌の鈴杖: 杖の先に雲のふくらみが三つ重なった冠、その下に逆さの鐘が一つ（右の雲から）吊られ、
    鐘の口の中で空の青が鳴る。杖には金の輪が二つ"""
    m = M('wild_cloudhymn_bell_staff')
    ribbon_grip(m, -14, 2)
    m.box('shaft', N, [-1.2, 2.75, -1.2], [1.2, 30, 1.2], grad=(2.75, 4, 30, 6))
    for k, y in enumerate((12, 21)):
        m.box(f'band_{k}', G, [-1.5, y, -1.5], [1.5, y + 0.75, 1.5], 4)
    puff(m, 'crown_l', (-2.75, 31), 4, 2.5, 4, 7)
    puff(m, 'crown_r', (2.75, 31.5), 4, 2.5, 4, 7)
    puff(m, 'crown_m', (0, 33.5), 5, 3, 4.5, 8)
    m.box('hanger', G, [4.15, 27.25, -0.3], [4.75, 30.75, 0.3], 3)
    m.box('bell', G, [2.75, 24.25, -1.75], [6.25, 27.25, 1.75], grad=(24.25, 2, 27.25, 4))
    m.box('bell_lip', G, [2.35, 23.5, -2.15], [6.65, 24.25, 2.15], 3)
    m.box('tongue_glow', Z, [3.9, 22.25, -0.6], [5.1, 23.5, 0.6], 4)
    return m


def wild_prism_staff():
    """虹石の杖: 杖の先に三角柱の水晶（プリズム）が斜めに浮き、金の三本爪が下から支える。
    水晶の芯だけが空の青で光り、外側は白銀"""
    m = M('wild_prism_staff')
    ribbon_grip(m, -14, 2, 1.3)
    m.box('shaft', N, [-1.1, 2.75, -1.1], [1.1, 29, 1.1], grad=(2.75, 4, 29, 6))
    m.box('cup', G, [-2, 28.5, -2], [2, 30, 2], 4)
    for k, (x, z) in enumerate(((-2.25, 0), (1.25, -1.75), (1.25, 1.75))):
        m.seg(f'prong_{k}', G, (x * 0.6, 29.5), (x, 33), 0.8, 0.8, 3, 5, tip=True, zc=z)
    m.seg('prism', N, (-1.5, 31.5), (1.5, 39.5), 4, 3, 7, 9, over=0)
    m.seg('prism_core_glow', Z, (-0.9, 32.5), (0.9, 38.5), 1.5, 3.2, 3, 5, over=0)
    m.seg('prism_tip', N, (1.5, 39.5), (2.25, 41.5), 2, 2, 8, 9, tip=True)
    return m


def wild_cloudcleaver_greatsword():
    """雲裂きの大剣: 幅広の白銀の刃の片側（+x）が雲のふくらみのように波打ち、反対側は真っ直ぐな刃。
    鍔は翼を左右に広げた金の飾り、刃の根元に空の青の宝玉"""
    m = M('wild_cloudcleaver_greatsword')
    ribbon_grip(m, -8, 8, 1.6)
    m.box('pommel', G, [-1.75, -10.5, -1.75], [1.75, -8.75, 1.75], 4)
    m.box('guard', G, [-3, 8.75, -1.75], [3, 11, 1.75], grad=(8.75, 2, 11, 4))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'guard_wing_{n}', G, [(sd * 2.5, 10), (sd * 5.5, 11.5), (sd * 7.5, 13.5)], [2, 1.25], 1.2, 3, 5, tip=True)
    m.box('blade', N, [-3.5, 11, -1], [2.5, 41, 1], grad=(11, 5, 41, 8))
    for k, y in enumerate((14, 21, 28, 35)):
        m.box(f'billow_{k}', N, [2.5, y, -0.9], [4.25, y + 5, 0.9], 6 + k % 2)
    m.seg('tip', N, (-3.5, 41), (2.5, 46), 2.5, 1.9, 8, 9, over=0)
    m.box('tip_cap', N, [-1, 41, -0.95], [2.5, 44, 0.95], 8)
    m.box('gem_glow', Z, [-1.25, 12, -1.15], [0.25, 13.5, 1.15], 4)
    return m


def wild_skysplit_axe():
    """天割りの斧: 雷が空を割るように、刃の中央に稲妻形の切れ込みが入った半月の斧。切れ込みの奥が空の青で光る。
    刃の背には雲のふくらみ、柄頭は金の羽根飾り"""
    m = M('wild_skysplit_axe')
    ribbon_grip(m, -6, 6)
    m.seg('tail_feather', G, (0, -6.75), (-1.75, -10.5), 1.75, 0.6, 3, 5, tip=True)
    m.box('shaft', N, [-1.25, 6.75, -1.25], [1.25, 25, 1.25], grad=(6.75, 4, 25, 6))
    m.arc('edge', N, (2, 21), 7, -70, 70, 5, [2.5, 3, 3, 3, 2.5], 1.1, 6, 8)
    m.box('cheek', N, [1, 17, -1.2], [5, 25, 1.2], grad=(17, 5, 25, 7))
    m.chain('rift_glow', Z, [(7.75, 23.5), (6, 21.75), (7.25, 20.25), (5.5, 18.5)], [0.8, 0.8, 0.8], 1.25, 3, 5, tip=False)
    puff(m, 'back', (-2.5, 19.5), 3, 4, 3, 6)
    m.box('cap', G, [-1.5, 25, -1.5], [1.5, 26.25, 1.5], 4)
    return m


def wild_cloudveil_claw():
    """雲紗の爪: 籠手から薄い雲の紗（帯）が垂れ、その紗を突き破るように三本の細い白銀の爪が伸びる。
    爪の付け根の金の輪のうち中央の一つが空の青"""
    m = M('wild_cloudveil_claw')
    ribbon_grip(m, -7, 3, 1.4)
    m.box('gauntlet', N, [-2.75, 3.75, -2.5], [2.75, 8.5, 2.5], grad=(3.75, 5, 8.5, 7))
    for k, (x, y, h) in enumerate(((-3.25, 7, 6), (3, 6.5, 4.5))):
        m.box(f'veil_{k}', N, [x - 0.3, y - h, -2], [x + 0.3, y, 2], grad=(y - h, 7, y, 9))
    for k, x in enumerate((-1.75, 0, 1.75)):
        mat = Z if k == 1 else G
        m.box(f'ring_{k}', mat, [x - 0.75, 8.5, -0.75], [x + 0.75, 9.5, 0.75], 4)
        m.chain(f'talon_{k}', N, [(x, 9.5), (x + 0.25, 14), (x + 1.25, 18.5), (x + 2.75, 21 - abs(k - 1))], [1.25, 1, 0.75], 0.6, 6, 9, tip=True)
    return m


ALL = [wild_skylark_bow, wild_cloudhymn_bell_staff, wild_prism_staff, wild_cloudcleaver_greatsword,
       wild_skysplit_axe, wild_cloudveil_claw]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
