# -*- coding: utf-8 -*-
"""鯨のシリーズ（look5）。手本は鯨の鎚 v5（whale_hammer.py）。
色: 深い紺 → 青 → 泡の白（sea）の 1 系統に、差し色の骨、光る空色の結晶 1 つ。前の模型の特徴（骨の刃・骨の弓腕・釣鐘・輪・三日月・肋骨）を残す。

  cd <作業フォルダ> && python -I <repo>/tools/models/x32/look5/whale_series.py [id ...]
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from lib import Model  # noqa: E402


def grip(m, y0, y1, pommel_y, bands=(), wrap_to=None, butt='glow'):
    """細い紺の柄（根元が暗く先へ明るく）、下に巻き、骨の輪、骨の柄頭と光る先"""
    m.box('shaft', 'sea', [-1.5, y0, -1.5], [1.5, y1, 1.5], grad=(y0, 1, y1, 4.5), wrap=(y0, wrap_to) if wrap_to else None)
    for k, by in enumerate(bands):
        m.box(f'band_{k}', 'bone', [-2, by, -2], [2, by + 2, 2], 3)
    m.box('pommel', 'bone', [-2, pommel_y - 3, -2], [2, pommel_y, 2], 3)
    m.box('pommel_flare', 'sea', [-2.5, pommel_y, -2.5], [2.5, pommel_y + 2, 2.5], 2)
    if butt == 'glow':
        m.box('pommel_tip_glow', 'glow', [-1, pommel_y - 6, -1], [1, pommel_y - 3, 1], 3)
    elif butt == 'none':
        pass
    else:
        m.seg('butt_tip', 'bone', (0, pommel_y - 3), (0, pommel_y - 8), 2, 1, 3, 5, tip=True)


def whale_dagger():
    m = Model('whale_dagger')
    grip(m, -5, 4, -5, bands=(), wrap_to=4)
    m.box('guard', 'sea', [-4, 4, -2], [4, 6, 2], 4)
    m.seg('guard_spike_l', 'bone', (-3.5, 5), (-6.5, 8), 2, 1, 3, 5, tip=True)
    m.seg('guard_spike_r', 'bone', (3.5, 5), (6.5, 8), 2, 1, 3, 5, tip=True)
    g = (6, 2.5, 33, 5)
    m.box('blade_0', 'bone', [-2.5, 6, -1.25], [2.5, 16, 1.25], grad=g, rim=True)
    m.box('blade_1', 'bone', [-2, 16, -1.1], [3, 24, 1.1], grad=g)
    m.box('blade_2', 'bone', [-1.5, 24, -1], [3, 29, 1], grad=g)
    m.seg('blade_tip', 'bone', (1.2, 28.5), (0, 34), 2.5, 1, 4.5, 5, tip=True)
    m.box('fuller_glow', 'glow', [-0.5, 8, -1.5], [1.5, 25, 1.5], grad=(8, 2, 25, 4))
    return m


def whalebone_greatsword():
    m = Model('whalebone_greatsword')
    grip(m, -7, 8, -7, bands=(1,), wrap_to=8)
    m.box('guard', 'sea', [-7, 8, -2.5], [7, 11, 2.5], 4, rim=False)
    m.seg('guard_spike_l', 'bone', (-6.5, 9.5), (-10, 13.5), 2, 1.5, 3, 5, tip=True)
    m.seg('guard_spike_r', 'bone', (6.5, 9.5), (10, 13.5), 2, 1.5, 3, 5, tip=True)
    g = (11, 2.4, 68, 5)
    m.box('blade_0', 'bone', [-5, 11, -1.5], [5, 20, 1.5], grad=g, rim=True)
    m.box('blade_1', 'bone', [-7, 20, -1.5], [7, 40, 1.5], grad=g, rim=True)
    m.box('blade_2', 'bone', [-6, 40, -1.25], [6, 50, 1.25], grad=g, rim=True)
    m.box('blade_3', 'bone', [-4.5, 50, -1.25], [4.5, 56, 1.25], grad=g)
    m.box('blade_4', 'bone', [-3, 56, -1], [3, 61, 1], grad=g)
    m.box('blade_5', 'bone', [-1.5, 61, -1], [1.5, 65, 1], grad=g)
    m.box('blade_tip', 'bone', [-1, 65, -1], [1, 68, 1], 5)
    m.box('fuller_glow', 'glow', [-1, 14, -1.75], [1, 56, 1.75], grad=(14, 2, 56, 4))
    for k, ty in enumerate((22, 28, 34)):   # 背（-x）の欠けた骨の歯
        m.box(f'tooth_{k}', 'bone', [-9, ty, -1], [-7, ty + 3, 1], 3)
    return m


def staff_body(m):
    grip(m, -14, 30, -14, bands=(10,), wrap_to=0, butt='spike')
    m.box('socket', 'bone', [-3, 30, -2], [3, 33, 2], 4)
    m.box('drip_l', 'sea', [-4, 26, -1], [-2, 30, 1], grad=(26, 2, 30, 4))
    m.box('drip_r', 'sea', [2, 27, -1], [4, 30, 1], grad=(27, 2, 30, 4))


def whale_staff():
    m = Model('whale_staff')
    staff_body(m)
    m.box('ring_stub', 'bone', [-1, 33, -1], [1, 35, 1], 4)
    m.arc('ring', 'bone', (0, 42), 8, -90, 270, 12, 2.5, 1.5, 3, 4.5)
    m.box('crystal_glow', 'glow', [-2.5, 38, -2.5], [2.5, 46, 2.5], gem=True)
    return m


def whale_star_staff():
    m = Model('whale_star_staff')
    staff_body(m)
    m.box('ring_stub', 'bone', [-1, 33, -1], [1, 35, 1], 4)
    m.arc('crescent_l', 'bone', (0, 45), 11, 270, 150, 6, [3.5, 3, 3, 2.5, 2, 2], 1.75, 3, 5, tip=True)
    m.arc('crescent_r', 'bone', (0, 45), 11, 270, 390, 6, [3.5, 3, 3, 2.5, 2, 2], 1.75, 3, 5, tip=True)
    m.box('crystal_glow', 'glow', [-3, 41, -3], [3, 51, 3], gem=True)
    m.box('shard_l_glow', 'glow', [-7, 50, -1], [-5, 53, 1], 3)
    m.box('shard_r_glow', 'glow', [5, 39, -1], [7, 42, 1], 3)
    return m


def bell(m, prefix, cx, top, size, clapper=True):
    """紺の釣鐘（上がすぼまり、口が開く）と光る舌。size 1 = レア、1.4 = 伝説"""
    s = size
    m.box(f'{prefix}_crown', 'sea', [cx - 2 * s, top - 3 * s, -2 * s], [cx + 2 * s, top, 2 * s], grad=(top - 3 * s, 3, top, 5))
    m.box(f'{prefix}_body', 'sea', [cx - 3 * s, top - 8 * s, -3 * s], [cx + 3 * s, top - 3 * s, 3 * s], grad=(top - 8 * s, 2, top - 3 * s, 4), rim=s > 1.2)
    m.box(f'{prefix}_lip', 'bone', [cx - 4 * s, top - 10 * s, -3.5 * s], [cx + 4 * s, top - 8 * s, 3.5 * s], 4)
    if clapper:
        m.box(f'{prefix}_clapper_glow', 'glow', [cx - 1, top - 10 * s - 2.5, -1], [cx + 1, top - 10 * s, 1], 4)


def whale_bell_staff():
    m = Model('whale_bell_staff')
    grip(m, -14, 38, -14, bands=(8, 24), wrap_to=0)
    m.arc('yoke', 'bone', (0, 52), 14, 235, 305, 5, 2.5, 1.5, 4, 4)
    m.seg('yoke_tip_l', 'bone', (-8, 40.5), (-10, 43.5), 2, 1, 4, 5, tip=True)
    m.seg('yoke_tip_r', 'bone', (8, 40.5), (10, 43.5), 2, 1, 4, 5, tip=True)
    m.box('cord_r', 'bone', [7, 36, -1], [9, 40, 1], 3)
    bell(m, 'bell', 8, 36, 1.2)
    m.box('cord_l', 'bone', [-9, 36, -1], [-7, 40, 1], 3)
    m.box('bead_l', 'bone', [-10, 31, -2], [-6, 36, 2], 4)
    m.box('bead_l_glow', 'glow', [-9, 29, -1], [-7, 31, 1], 3)
    return m


def whale_deep_bell_staff():
    m = Model('whale_deep_bell_staff')
    grip(m, -16, 34, -16, bands=(8, 20, 28), wrap_to=2)
    m.arc('crook', 'sea', (6.5, 34), 6.5, 180, 0, 7, 3, 1.5, 4.5, 6.5)
    m.box('crook_cap', 'bone', [11.5, 31, -2], [14.5, 34, 2], 4)
    m.box('cord', 'bone', [12, 28, -1], [14, 31, 1], 3)
    bell(m, 'bell', 13, 28, 1.4)
    m.box('bell_eye_f_glow', 'glow', [11, 17.5, -4.6], [15, 21.5, -4.2], 4)
    m.box('bell_eye_b_glow', 'glow', [11, 17.5, 4.2], [15, 21.5, 4.6], 4)
    return m


def whale_choir_hammer():
    m = Model('whale_choir_hammer')
    grip(m, -12, 22, -12, bands=(-1, 15), wrap_to=12)
    m.box('collar', 'bone', [-2.5, 20, -2.5], [2.5, 24, 2.5], 3, rim=False)
    m.box('core', 'sea', [-4, 24, -3.5], [4, 36, 3.5], 4, rim=True)
    m.box('gem_glow', 'glow', [-2.5, 26, -4], [2.5, 34, 4], gem=True)
    for s, nm in ((1, 'r'), (-1, 'l')):
        x0, x1 = (4, 10) if s > 0 else (-10, -4)
        m.box(f'strike_{nm}', 'sea', [x0, 23, -4], [x1, 37, 4], 4, rim=True)
        f0, f1 = (10, 12) if s > 0 else (-12, -10)
        m.box(f'face_{nm}', 'bone', [f0, 24, -3.5], [f1, 36, 3.5], 4)
    m.arc('rib', 'bone', (0, 36), 11.5, 15, 165, 7, 2, 1.25, 3, 5)
    for k, ang in enumerate((60, 120)):
        import math
        bx = 11.5 * math.cos(math.radians(ang))
        by = 36 + 11.5 * math.sin(math.radians(ang))
        m.box(f'choir_bell_{k}', 'sea', [bx - 1.5, by - 5, -1.5], [bx + 1.5, by - 1, 1.5], grad=(by - 5, 3, by - 1, 5))
        m.box(f'choir_lip_{k}_glow', 'glow', [bx - 1, by - 7, -1], [bx + 1, by - 5, 1], 4)
    return m


def bow_body(m, body, tips, w_body, w_tip, thorns=True, ribs=False):
    """弓腕（上と下）。y は握りの高さ 15 で折り返す。骨は根元が暗く先が明るい"""
    for sign, n in ((1, 'u'), (-1, 'd')):
        f = (lambda p: (p[0], 15 + sign * (p[1] - 15)))
        m.chain(f'arm_{n}', 'bone', [f(p) for p in body], w_body, 1.75, 2.6, 3.8, tip=False)
        m.chain(f'tip_{n}', 'bone', [f(p) for p in tips], w_tip, 1.25, 3.8, 5, tip=True)
        if thorns:
            m.seg(f'thorn_{n}0', 'bone', f((14, 24)), f((17, 26.5)), 2, 1, 3, 5, tip=True)
            m.seg(f'thorn_{n}1', 'bone', f((14, 30)), f((17, 32.5)), 2, 1, 3, 5, tip=True)
        if ribs:
            for zc, zn in ((2.75, 'b'), (-2.75, 'f')):
                m.chain(f'rib_{n}{zn}', 'sea', [f(p) for p in body], [2, 2, 2, 2, 2][:len(body) - 1], 1, 3, 6, tip=True, zc=zc)


def bow_grip(m, legend=False, gems=True):
    m.box('grip', 'sea', [9, 9, -3], [15, 21, 3], grad=(9, 2, 21, 4), wrap=(9, 21))
    m.box('grip_ring_t', 'bone', [8.5, 21, -3.5], [15.5, 23, 3.5], 4)
    m.box('grip_ring_b', 'bone', [8.5, 7, -3.5], [15.5, 9, 3.5], 3)
    if not gems:
        return
    m.box('socket_f', 'bone', [10, 12, -4], [14, 18, -3], 4)
    m.box('crystal_f_glow', 'glow', [10.5, 12.5, -4.75], [13.5, 17.5, -3.75], gem=True)
    m.box('socket_b', 'bone', [10, 12, 3], [14, 18, 4], 4)
    m.box('crystal_b_glow', 'glow', [10.5, 12.5, 3.75], [13.5, 17.5, 4.75], gem=True)


def strings(m, top, bot, pulls):
    """弦（1 ドット）。0 はまっすぐ、ほかは引きの段。名前は string_<段>_*"""
    for key, pull in pulls:
        if pull == 0:
            m.box(f'string_{key}', 'string', [top[0] - 0.5, bot[1], -0.5], [top[0] + 0.5, top[1], 0.5], 3)
        else:
            mid = (top[0] - pull, 15)
            m.seg(f'string_{key}_u', 'string', mid, top, 1, 0.5, 3, 3, over=0.2)
            m.seg(f'string_{key}_d', 'string', bot, mid, 1, 0.5, 3, 3, over=0.2)


def whale_bow():
    m = Model('whale_bow')
    bow_body(m, [(12, 21), (13.5, 26), (14, 31), (13, 35)], [(13, 35), (10, 39.5), (5, 42.5), (0, 44), (-3, 44.5)],
             3, [3, 2.5, 2, 2])
    bow_grip(m)
    m.seg('nock_u', 'bone', (-1, 44.3), (-3.5, 44.6), 2, 1.25, 4, 5)
    m.seg('nock_d', 'bone', (-3.5, -14.6), (-1, -14.3), 2, 1.25, 4, 5)
    strings(m, (-3, 44.5), (-3, -14.5), [('0', 0), ('p0', 2.4), ('p1', 6.8), ('p2', 11.6)])
    return m


def whale_rib_longbow():
    m = Model('whale_rib_longbow')
    bow_body(m, [(12, 21), (14, 28), (15, 35), (14.5, 41), (12, 45)], [(12, 45), (8, 48), (3, 49.5), (-1, 50)],
             3.5, [3, 2.5, 2], thorns=False, ribs=True)
    bow_grip(m, legend=True)
    m.box('tip_crystal_glow', 'glow', [-3, 50, -1.5], [0, 53, 1.5], gem=True)
    m.box('tip_crystal_b_glow', 'glow', [-3, -23, -1.5], [0, -20, 1.5], gem=True)
    strings(m, (-1, 50), (-1, -20), [('0', 0), ('p0', 2.4), ('p1', 6.8), ('p2', 11.6)])
    return m


ALL = [whale_dagger, whalebone_greatsword, whale_staff, whale_star_staff, whale_bell_staff, whale_deep_bell_staff,
       whale_choir_hammer, whale_bow, whale_rib_longbow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
