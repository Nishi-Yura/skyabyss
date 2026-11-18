# -*- coding: utf-8 -*-
"""塔（look5・個性版）。天と淵のあいだの塔の主たち「側」の武器 6 品（docs/design/03 の塔の装備）。
色: 塔の石（slate 青灰）の 1 系統、差し色は古い真鍮（brass）、光は印の青白（sigil）。
天秤・目・落ちる月・引力の環・追う印・合わせた掌から一品ずつ形を立てる。
- 柄: 石の握りに真鍮の四角い輪を二つ、柄頭は四角錐の石。
- 光: あいだの大剣 = 刃の真ん中の境の線、側の目の弓 = 目、落ちる月の杖 = 月、引き合いの鎚 = 環の芯、追う印の短剣 = 印、合掌の鈴杖 = 掌のあいだ。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'slate': ['#14181e', '#222a32', '#323c46', '#46525e', '#5c6a78', '#768694', '#92a2ae', '#b0bec8', '#ccd8e0', '#e8f0f4'],
    'brass': ['#3a2a12', '#5e4620', '#86682e', '#ae8e42', '#d0b264', '#ecd69a'],
    'sigil': ['#2a4a8a', '#3e6ec0', '#5a9ae6', '#8cc4f8', '#c4e4ff', '#f0f8ff'],
    'abyssal': ['#0a0812', '#14101e', '#201a2e', '#2e2640', '#3e3454', '#524668'],
})
T, B, G, A = 'slate', 'brass', 'sigil', 'abyssal'


def M(name):
    return Model(name, accent=B, glowmat=G)


def grip(m, y0, y1):
    m.box('grip', T, [-1.5, y0, -1.5], [1.5, y1, 1.5], grad=(y0, 2, y1, 4), wrap=(y0, y1))
    for k, y in enumerate((y0 + 0.5, y1 - 2)):
        m.box(f'ring_{k}', B, [-2, y, -2], [2, y + 1.5, 2], grad=(y, 2, y + 1.5, 4))
    m.box('pommel', T, [-2.25, y0 - 2.5, -2.25], [2.25, y0, 2.25], grad=(y0 - 2.5, 3, y0, 5))
    m.seg('pommel_tip', T, (0, y0 - 2.5), (0, y0 - 5), 2.5, 1.25, 5, 7, tip=True)


def rod(m, y0, y1, bands=()):
    m.box('rod', T, [-1.25, y0, -1.25], [1.25, y1, 1.25], grad=(y0, 3, y1, 6))
    for k, y in enumerate(bands):
        m.box(f'band_{k}', B, [-1.75, y, -1.75], [1.75, y + 1.5, 1.75], grad=(y, 2, y + 1.5, 4))


def tower_balance_sword():
    """あいだの大剣: 刃の半分（-x）は天の白い石、半分（+x）は淵の黒。真ん中の境に印の線が光る。鍔は天秤の竿と二つの皿"""
    m = M('tower_balance_sword')
    grip(m, -7, 7)
    m.box('beam', B, [-8, 8.5, -1], [8, 10, 1], grad=(8.5, 3, 10, 5))
    m.box('pivot', B, [-1.75, 7.5, -1.75], [1.75, 11.5, 1.75], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'cord_{n}', B, [sd * 7.5 - 0.3, 5.5, -0.3], [sd * 7.5 + 0.3, 8.5, 0.3], 3)
        m.box(f'pan_{n}', B, [sd * 7.5 - 2, 4.75, -2], [sd * 7.5 + 2, 5.75, 2], grad=(4.75, 2, 5.75, 4))
    m.box('blade_sky', T, [-3.5, 11.5, -1], [0, 44, 1], grad=(11.5, 6, 44, 9))
    m.box('blade_abyss', A, [0, 11.5, -1], [3.5, 44, 1], grad=(11.5, 1, 44, 4))
    m.box('seam_glow', G, [-0.4, 12, -1.1], [0.4, 43, 1.1], grad=(12, 2, 43, 4))
    for k, w in enumerate((2.75, 2, 1.25, 0.5)):   # 先は真ん中へ段で細る（天と淵の半分ずつ）
        y = 44 + k * 1.75
        m.box(f'tip_sky_{k}', T, [-w, y, -0.95 + k * 0.05], [0, y + 1.75, 0.95 - k * 0.05], 8)
        m.box(f'tip_abyss_{k}', A, [0, y, -0.95 + k * 0.05], [w, y + 1.75, 0.95 - k * 0.05], 3)
    return m


def tower_pull_hammer():
    """引き合いの鎚: 頭は二つの重い石の塊を真鍮の環がつなぐ。環の芯（引力の点）が光る。塊の外面は平ら"""
    m = M('tower_pull_hammer')
    grip(m, -6, 6)
    rod(m, 7.5, 25, bands=(16,))
    c = 30
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'mass_{n}', T, [sd * 4 - 3, c - 4, -3.5] if sd > 0 else [sd * 4 - 3, c - 4, -3.5],
              [sd * 4 + 3, c + 4, 3.5], grad=(c - 4, 3, c + 4, 6))
        m.box(f'face_{n}', T, [sd * 7 - (0 if sd > 0 else 1.25), c - 3.5, -3], [sd * 7 + (1.25 if sd > 0 else 0), c + 3.5, 3], 7)
    m.arc('orbit', B, (0, c), 6, 0, 360, 10, 1.25, 1, 3, 5)
    m.box('core_glow', G, [-1.25, c - 1.25, -1.25], [1.25, c + 1.25, 1.25], 4)
    m.box('socket', B, [-2, 24, -2], [2, 26, 2], 3)
    return m


def tower_moon_staff():
    """落ちる月の杖: 石の棒の先に三本の真鍮の爪が下向きの月（光る珠）を吊る。月の下に落ちる欠片が二つ"""
    m = M('tower_moon_staff')
    grip(m, -14, 2)
    rod(m, 3.5, 36, bands=(13, 24))
    m.box('cap', B, [-3, 35.5, -3], [3, 37.5, 3], grad=(35.5, 2, 37.5, 4))
    for k, a in enumerate((90, 210, 330)):
        x, z = 3 * math.cos(math.radians(a)), 3 * math.sin(math.radians(a))
        m.box(f'claw_{k}', B, [x - 0.5, 37.5, z - 0.5], [x + 0.5, 45, z + 0.5], 4)
    m.box('claw_ring', B, [-3.5, 44.5, -3.5], [3.5, 45.5, 3.5], 5)
    m.box('moon_h_glow', G, [-3, 39.5, -2.25], [3, 43.5, 2.25], grad=(39.5, 2, 43.5, 4))
    m.box('moon_v_glow', G, [-2.25, 38.75, -2], [2.25, 44.25, 2], 4)
    for k, (x, y) in enumerate(((-1.5, 35), (2, 33))):
        m.seg(f'shard_{k}', T, (x - 0.9, y - 0.9), (x + 0.9, y + 0.9), 1.5, 0.6, 6, 8, over=0)
    return m


def tower_sigil_dagger():
    """追う印の短剣: 細い石の刃に印の筋が刃元から先まで刻まれる（光る）。鍔は真鍮の四角い輪、刃元に印の菱形"""
    m = M('tower_sigil_dagger')
    grip(m, -4, 4)
    m.box('guard', B, [-3.5, 5.5, -1.5], [3.5, 7.5, 1.5], grad=(5.5, 2, 7.5, 4))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'guard_end_{n}', B, [sd * 3.5 - 1, 5, -1.75], [sd * 3.5 + 1, 8, 1.75], 4)
    m.box('blade', T, [-2, 7.5, -0.85], [2, 27, 0.85], grad=(7.5, 4, 27, 7))
    m.box('mark_glow', G, [-0.4, 10, -0.95], [0.4, 26, 0.95], grad=(10, 2, 26, 4))
    m.seg('rune', G, (-1.25, 10.5 - 1.25), (1.25, 10.5 + 1.25), 2.5, 1, 3, 4, over=0)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'tip_{n}', T, (sd * 2, 26.5), (0, 33), 2.5, 0.8, 6, 8, tip=True)
    m.box('tip_core', T, [-1.25, 26.5, -0.8], [1.25, 29, 0.8], 7)
    return m


def tower_clap_bell_staff():
    """合掌の鈴杖: 先に合わせた二つの石の掌（指は上へ）、掌のあいだが光る。手首に真鍮の腕輪、鈴が二つ下がる"""
    m = M('tower_clap_bell_staff')
    grip(m, -14, 2)
    rod(m, 3.5, 34, bands=(13, 23))
    m.box('cuff', B, [-3.25, 33.5, -2.5], [3.25, 36, 2.5], grad=(33.5, 2, 36, 4))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'palm_{n}', T, [sd * 0.4 - (2.5 if sd < 0 else 0), 36, -2.25], [sd * 0.4 + (2.5 if sd > 0 else 0), 44, 2.25], grad=(36, 4, 44, 6))
        for k, (h, z) in enumerate(((4.5, -1.5), (5.5, -0.5), (5, 0.5), (4, 1.5))):
            m.box(f'finger_{n}{k}', T, [sd * 0.4 - (1.5 if sd < 0 else 0), 44, z - 0.45], [sd * 0.4 + (1.5 if sd > 0 else 0), 44 + h, z + 0.45], 7)
        m.seg(f'thumb_{n}', T, (sd * 2.5, 39), (sd * 3.25, 42.5), 1.5, 1, 6, 7, tip=True)
        m.box(f'cord_{n}', B, [sd * 3.5 - 0.3, 31, -0.3], [sd * 3.5 + 0.3, 34, 0.3], 3)
        m.box(f'bell_{n}', B, [sd * 3.5 - 1.25, 28.5, -1.25], [sd * 3.5 + 1.25, 31, 1.25], grad=(28.5, 3, 31, 5))
    m.box('prayer_glow', G, [-0.4, 37, -1.75], [0.4, 48, 1.75], grad=(37, 3, 48, 5))
    return m


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


def tower_eye_bow():
    """側の目の弓: 握りの外に石の瞼に抱かれた一つ目（光る）。腕は角張った石の板を段に重ね、先に真鍮の弦受け"""
    m = M('tower_eye_bow')
    m.box('grip', T, [10.5, 10, -1.75], [13.5, 20, 1.75], grad=(10, 2, 20, 4), wrap=(10, 20))
    m.box('lid_u', T, [13.5, 17, -2.25], [19, 19.5, 2.25], 6)
    m.box('lid_d', T, [13.5, 10.5, -2.25], [19, 13, 2.25], 4)
    m.box('eye_glow', G, [13.75, 13, -2], [18.5, 17, 2], 4)
    m.box('pupil', A, [18, 14, -1], [18.9, 16, 1], 1)
    arm = [(12, 20), (12.75, 26), (12.25, 32), (10.25, 38), (7.25, 42.5), (4.5, 45)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'arm_{n}', T, [mirror(p, sign) for p in arm], [3.5, 3, 3, 2.5, 2.25], 1.4, 3, 7, tip=False)
        for k in (1, 3):
            x, y = mirror(arm[k], sign)
            m.box(f'plate_{n}{k}', B, [x - 2.25, y - 0.75, -1.6], [x + 2.25, y + 0.75, 1.6], 4)
        m.seg(f'nock_{n}', B, mirror((6, 45.5), sign), mirror((2.5, 45.5), sign), 2.25, 1.1, 3, 5)
    strings(m, (3.5, 45.5), (3.5, -15.5))
    return m


ALL = [tower_balance_sword, tower_pull_hammer, tower_moon_staff, tower_sigil_dagger, tower_clap_bell_staff, tower_eye_bow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
