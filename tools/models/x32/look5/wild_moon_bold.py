# -*- coding: utf-8 -*-
"""野・月（look5・個性版）。月の見える岬と星見の丘で拾う 6 品（叙）。
色: 夜の鉄（gloam 紺がかった灰の 1 系統）、差し色は月の真珠色（pearl）、光は星の淡い黄（star）を品ごとに一か所。
灯台守と星読みの道具。三日月の切り欠きと細い輪を形の芯にする。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'gloam': ['#0e1020', '#16192e', '#20243e', '#2c3150', '#3a4064', '#4a5178', '#5c648e', '#7078a4', '#8890ba', '#a4acd0'],
    'pearl': ['#5e5a62', '#86828c', '#aaa6b2', '#ccc8d4', '#e6e2ec', '#faf8fe'],
    'star': ['#7a5a10', '#b08a1c', '#dcb834', '#f6dc64', '#fff0a8', '#fffce6'],
})
N, P, S = 'gloam', 'pearl', 'star'


def M(name):
    return Model(name, accent=P, glowmat=S)


def cord_grip(m, y0, y1, r=1.5):
    """紺の紐を固く巻いた握り。上下は真珠色の細い輪"""
    m.box('grip', N, [-r, y0, -r], [r, y1, r], grad=(y0, 3, y1, 5), wrap=(y0, y1))
    m.box('pearl_d', P, [-r - 0.3, y0 - 0.75, -r - 0.3], [r + 0.3, y0, r + 0.3], 3)
    m.box('pearl_u', P, [-r - 0.3, y1, -r - 0.3], [r + 0.3, y1 + 0.75, r + 0.3], 4)


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


def wild_lighthouse_longbow():
    """灯台の大弓: 細く高い塔のような弓腕。握りの上（+x 側）に小さな灯台の灯室が付き、その窓が星色に光る。
    弓腕には真珠色の帯が二本ずつ、先は三日月形に反った弦受け"""
    m = M('wild_lighthouse_longbow')
    m.box('grip', N, [10.25, 9, -1.75], [13.75, 21, 1.75], grad=(9, 3, 21, 5), wrap=(9, 21))
    m.box('lamp_base', P, [13.5, 19, -1.75], [15.5, 20, 1.75], 4)
    m.box('lamp_glow', S, [13.75, 20, -1.25], [15.25, 22.5, 1.25], grad=(20, 3, 22.5, 5))
    m.box('lamp_roof', N, [13.4, 22.5, -1.6], [15.6, 23.5, 1.6], 6)
    m.box('lamp_spire', N, [14.25, 23.5, -0.4], [14.75, 25, 0.4], 7)
    arm = [(12, 20.5), (11.25, 30), (9.5, 39), (7, 46.5), (4.5, 51.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'arm_{n}', N, [mirror(p, sign) for p in arm], [3, 2.5, 2, 1.75], 1.3, 4, 7, tip=False)
        for k, (x, y) in enumerate(((11.5, 28), (8.75, 41))):
            q = mirror((x, y), sign)
            m.box(f'band_{n}{k}', P, [q[0] - 1.6, q[1] - 0.5, -1.4], [q[0] + 1.6, q[1] + 0.5, 1.4], 4)
        m.chain(f'crescent_{n}', P, [mirror(p, sign) for p in ((4.5, 51.5), (2.5, 54), (3.25, 56))], [1.5, 1.1], 0.9, 3, 5, tip=True)
    strings(m, (2.75, 54), (2.75, -24))
    return m


def wild_meteor_greatsword():
    """隕鉄の大剣: 空から落ちた隕鉄を打った、ごつごつと厚い大剣。刃の中央に一本の樋、両縁に欠けが一つずつ、
    刃先は斜めに砕けたように欠ける。鍔は落ちる星の尾のように後ろへ流れ、刃の根元の割れ目が星色に光る"""
    m = M('wild_meteor_greatsword')
    cord_grip(m, -8, 8, 1.6)
    m.box('pommel', N, [-2, -10.75, -2], [2, -8.75, 2], 6)
    m.box('guard', P, [-3.5, 8.75, -2], [3.5, 11, 2], grad=(8.75, 2, 11, 4))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'trail_{n}', P, (sd * 3, 10), (sd * 7, 8), 1.75, 1.2, 3, 5, tip=True)
    m.box('blade', N, [-3.75, 11, -1.4], [3.75, 40, 1.4], grad=(11, 4, 40, 7))
    m.box('fuller', N, [-0.6, 16, -1.5], [0.6, 36, 1.5], grad=(16, 2, 36, 3))
    for k, (y, sd) in enumerate(((22, -1), (31, 1))):
        m.box(f'chip_{k}', N, [sd * 3.75 - 0.75, y, -1.2], [sd * 3.75 + 0.75, y + 2.5, 1.2], 8)
    m.seg('tip', N, (-3.75, 40), (1, 46), 3.5, 2.4, 7, 8, over=0)
    m.box('tip_chunk', N, [-3.75, 40, -1.35], [3.75, 42, 1.35], 7)
    m.box('crack_glow', S, [1.5, 11.25, -1.5], [2.25, 15, 1.5], 4)
    return m


def wild_mirror_dagger():
    """月鏡の短剣: 刃が磨いた真珠色の鏡の板で、紺の鉄の細い縁取りが刃を囲む。鍔は三日月を横に寝かせた形、
    柄頭に星色の小さな珠"""
    m = M('wild_mirror_dagger')
    cord_grip(m, -4, 4, 1.3)
    m.box('pommel_glow', S, [-0.9, -6.25, -0.9], [0.9, -4.75, 0.9], 4)
    m.chain('crescent', N, [(-3.5, 6.5), (-2, 5), (0, 4.6), (2, 5), (3.5, 6.5)], [1.5, 1.5, 1.5, 1.5], 1.6, 5, 7, tip=False)
    m.box('frame', N, [-2.25, 5, -0.8], [2.25, 17, 0.8], grad=(5, 5, 17, 7))
    m.box('mirror', P, [-1.6, 5.5, -0.95], [1.6, 16.5, 0.95], grad=(5.5, 3, 16.5, 5))
    m.seg('tip', N, (-2.25, 17), (0, 20.5), 2.5, 1.5, 7, 8, tip=True)
    m.seg('tip_r', N, (2.25, 17), (0, 20.5), 2.5, 1.5, 7, 8, tip=True)
    return m


def wild_starbell_totem():
    """星鈴の祈り柱: 紺の木の柱の頂に、四方へ尖った星の飾りが立つ。星の下に三日月の腕が横に伸び、
    その先に鈴が一つ下がって、鈴の舌が星色に光る。柱には星の刻みの輪が二つ"""
    m = M('wild_starbell_totem')
    cord_grip(m, -14, 2, 1.6)
    m.box('pillar', N, [-1.75, 2.75, -1.75], [1.75, 30, 1.75], grad=(2.75, 3, 30, 5))
    for k, y in enumerate((10, 19)):
        m.box(f'ring_{k}', P, [-2.1, y, -2.1], [2.1, y + 1, 2.1], 4)
        m.box(f'ring_notch_{k}', N, [-0.4, y, -2.2], [0.4, y + 1, 2.2], 3)
    m.box('star_core', P, [-1.75, 30, -1.5], [1.75, 33.5, 1.5], 5)
    for k, (a, b) in enumerate((((0, 33.5), (0, 37.5)), ((1.75, 31.75), (5, 31.75)), ((-1.75, 31.75), (-5, 31.75)))):
        m.seg(f'star_ray_{k}', P, a, b, 2, 1.25, 4, 5, tip=True)
    m.chain('crescent_arm', N, [(1.5, 28.5), (4.5, 27.5), (7, 28.5), (8, 30.5)], [1.5, 1.5, 1.25], 1.1, 5, 7, tip=True)
    m.box('cord', P, [5.75, 24.5, -0.2], [6.25, 27.75, 0.2], 3)
    m.box('bell', P, [4.5, 22, -1.5], [7.5, 24.5, 1.5], grad=(22, 2, 24.5, 4))
    m.box('clapper_glow', S, [5.5, 20.75, -0.5], [6.5, 22, 0.5], 4)
    return m


def wild_starchart_staff():
    """星図の杖: 杖の先に斜めに傾いた細い天の輪（環）が浮かび、その中心に真珠色の星図の円盤。
    輪の上の一点だけが星色に光る（北の星）。杖の首に紺の房"""
    m = M('wild_starchart_staff')
    cord_grip(m, -14, 2, 1.3)
    m.box('shaft', N, [-1.1, 2.75, -1.1], [1.1, 28.5, 1.1], grad=(2.75, 3, 28.5, 5))
    m.box('collar', P, [-1.6, 27, -1.6], [1.6, 28.5, 1.6], 4)
    m.box('stem', N, [-0.6, 28.5, -0.6], [0.6, 30.5, 0.6], 6)
    m.arc('ring', N, (0, 34.5), 4.5, 0, 360, 10, [1] * 10, 1, 6, 7)
    m.box('chart', P, [-2.75, 31.75, -0.4], [2.75, 37.25, 0.4], grad=(31.75, 3, 37.25, 5))
    m.box('chart_line', N, [-2.75, 34.25, -0.5], [2.75, 34.75, 0.5], 3)
    m.box('pole_star_glow', S, [-0.75, 38.5, -0.75], [0.75, 40, 0.75], 4)
    for k, x in enumerate((-1.75, 1.75)):
        m.box(f'tassel_{k}', N, [x - 0.4, 23.5, -0.4], [x + 0.4, 27, 0.4], grad=(23.5, 4, 27, 6))
    return m


def wild_starforge_hammer():
    """星鍛えの鎚: 頭が小さな金床の形をした鎚。打つ面（+x）は広く平ら、反対（-x）は金床の角のように細く尖る。
    頭の上面に落ちた星を受ける窪みがあり、そこが星色に光る"""
    m = M('wild_starforge_hammer')
    cord_grip(m, -6, 6)
    m.box('shaft', N, [-1.25, 6.75, -1.25], [1.25, 21, 1.25], grad=(6.75, 3, 21, 5))
    m.box('waist', N, [-2, 19.5, -2], [2, 21.5, 2], 5)
    m.box('head', N, [-3, 21.5, -2.5], [4.5, 26, 2.5], grad=(21.5, 4, 26, 6))
    m.box('face', P, [4.5, 21, -2.75], [5.75, 26.5, 2.75], 4)
    m.seg('horn', N, (-3, 24.5), (-8, 23.75), 3, 2.5, 5, 7, tip=True)
    m.box('top_plate', N, [-2.5, 26, -2.25], [4, 26.75, 2.25], 7)
    m.box('cradle_glow', S, [-0.5, 26.4, -1], [1.5, 27, 1], 4)
    return m


ALL = [wild_lighthouse_longbow, wild_meteor_greatsword, wild_mirror_dagger, wild_starbell_totem,
       wild_starchart_staff, wild_starforge_hammer]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
