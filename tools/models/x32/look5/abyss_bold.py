# -*- coding: utf-8 -*-
"""深淵（look5・個性版）。五章の深淵の武器 30 品（巨神の心核から鍛えた逸品 24 と、深淵の番人・層の品 6）。
色: 淵の黒い岩（void 紫がかった黒）の 1 系統、差し色は沈んだ骨（bone 灰白）、光は淵の青緑（deep）。
巨神ごとの形（鯨・潮・雷・火）は品の説明（ja_jp の desc）から一品ずつ立てる。柄は淵の岩に骨の輪、柄頭は小さな錘。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'void': ['#0c0a12', '#16131e', '#221e2c', '#302a3c', '#40384e', '#524862', '#665a78', '#7e7090', '#988aa8', '#b4a8c2'],
    'abone': ['#3e3a34', '#5e584e', '#827a6c', '#a69e8e', '#c8c0b0', '#e6e0d2'],
    'deep': ['#0e4a4a', '#147070', '#1e9a96', '#3cc4bc', '#86e8dc', '#d4fff6'],
})
V, B, G = 'void', 'abone', 'deep'


def M(name):
    return Model(name, accent=B, glowmat=G)


def diamond(m, name, mat, c, s, d, t0, t1, zc=0.0):
    h = s / (2 * math.sqrt(2))
    m.seg(name, mat, (c[0] - h, c[1] - h), (c[0] + h, c[1] + h), s, d, t0, t1, over=0, zc=zc)


def grip(m, y0, y1):
    """淵の岩の握りに骨の輪二つ、柄頭は下へ垂れる錘"""
    m.box('grip', V, [-1.5, y0, -1.5], [1.5, y1, 1.5], grad=(y0, 2, y1, 4), wrap=(y0, y1))
    for k, y in enumerate((y0 + 0.25, y1 - 1.75)):
        m.box(f'grip_ring_{k}', B, [-1.9, y, -1.9], [1.9, y + 1.5, 1.9], grad=(y, 2, y + 1.5, 4))
    m.box('weight_neck', V, [-0.75, y0 - 1.5, -0.75], [0.75, y0, 0.75], 3)
    m.box('weight', V, [-2, y0 - 4.5, -2], [2, y0 - 1.5, 2], grad=(y0 - 4.5, 2, y0 - 1.5, 5))
    m.seg('weight_tip', V, (0, y0 - 4.5), (0, y0 - 6.5), 2, 1, 4, 6, tip=True)


def rod(m, y0, y1, bands=()):
    m.box('rod', V, [-1.25, y0, -1.25], [1.25, y1, 1.25], grad=(y0, 3, y1, 6))
    for k, y in enumerate(bands):
        m.box(f'band_{k}', B, [-1.75, y, -1.75], [1.75, y + 1.5, 1.75], grad=(y, 2, y + 1.5, 4))


def staff(m, top=34):
    grip(m, -14, 2)
    rod(m, 3.5, top, bands=(13, 23))


def hammer_haft(m, top=25):
    grip(m, -6, 6)
    rod(m, 7.5, top, bands=(16,))


def guard_bar(m, y, half, mat=B):
    m.box('guard', mat, [-half, y, -1.5], [half, y + 2, 1.5], grad=(y, 2, y + 2, 4))


def point(m, prefix, mat, x0, x1, y0, steps, d, t):
    """段で細る刃先（左右対称、x0..x1 の幅から中心へ）"""
    w = (x1 - x0) / 2
    c = (x0 + x1) / 2
    for k in range(steps):
        ww = w * (1 - (k + 1) / (steps + 1))
        m.box(f'{prefix}_{k}', mat, [c - ww, y0 + k * 1.75, -d + k * 0.05], [c + ww, y0 + (k + 1) * 1.75, d - k * 0.05], t + k * 0.5)


def bell(m, prefix, mat, c, r, h, t=4):
    """吊り鐘: 肩・胴・口の縁"""
    x, y = c
    m.box(f'{prefix}_top', mat, [x - r * 0.55, y + h - 1.25, -r * 0.55], [x + r * 0.55, y + h, r * 0.55], t + 2)
    m.box(f'{prefix}_body', mat, [x - r * 0.8, y + 1, -r * 0.8], [x + r * 0.8, y + h - 1.25, r * 0.8], grad=(y + 1, t, y + h - 1.25, t + 2))
    m.box(f'{prefix}_lip', mat, [x - r, y, -r], [x + r, y + 1.25, r], t + 3)


# ================================================================ 底の品（大剣・剣・短剣・錨・鎌）

def abyss_greatsword():
    """底なしの大剣: 黒い幅広の刃の真ん中が縦に割れ、割れ目の奥で淵の光がのぞく。鍔は骨の顎"""
    m = M('abyss_greatsword')
    grip(m, -8, 8)
    guard_bar(m, 9, 7)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'jaw_{n}', B, (sd * 6.5, 10), (sd * 8.5, 13.5), 2.25, 1.25, 4, 6, tip=True)
        m.box(f'blade_{n}', V, [sd * 1 - (4 if sd < 0 else 0), 11, -1.2], [sd * 1 + (4 if sd > 0 else 0), 48, 1.2], grad=(11, 2, 48, 5))
        m.box(f'edge_{n}', V, [sd * 5 - (0.75 if sd < 0 else 0), 12, -0.8], [sd * 5 + (0.75 if sd > 0 else 0), 47, 0.8], 7)
    m.box('rift_glow', G, [-1, 13, -0.9], [1, 44, 0.9], grad=(13, 1, 44, 4))
    point(m, 'tip', V, -5.75, 5.75, 48, 4, 1.15, 4)
    return m


def abyss_lamp_sword():
    """灯り番の剣: 細身の黒い剣。鍔の片側から骨の鉤に角灯が下がり、灯の中で淵の火が燃える"""
    m = M('abyss_lamp_sword')
    grip(m, -6, 6)
    guard_bar(m, 7, 5)
    m.chain('hook', B, [(5, 8), (7.5, 9), (8.5, 6.5)], [1.25, 1.25], 0.7, 4, 5, tip=False)
    m.box('lamp_cap', B, [7, 4.5, -1.75], [10, 6, 1.75], 5)
    m.box('lamp_glow', G, [7.25, 0.5, -1.5], [9.75, 4.5, 1.5], grad=(0.5, 2, 4.5, 4))
    for k, (x, z) in enumerate(((7, -1.75), (9.6, -1.75), (7, 1.35), (9.6, 1.35))):
        m.box(f'lamp_post_{k}', B, [x, 0.5, z], [x + 0.4, 4.5, z + 0.4], 3)
    m.box('lamp_base', B, [7, -0.5, -1.75], [10, 0.5, 1.75], 3)
    m.box('blade', V, [-2, 9, -0.9], [2, 38, 0.9], grad=(9, 3, 38, 6))
    m.box('fuller', B, [-0.4, 10, -1], [0.4, 33, 1], 2)
    point(m, 'tip', V, -2, 2, 38, 3, 0.9, 5)
    return m


def abyss_pulse_dagger():
    """鼓動の短剣: 黒い刃に淵の光の脈が根元から先へ走る（枝分かれ）。鍔は肋のような骨の輪"""
    m = M('abyss_pulse_dagger')
    grip(m, -4, 4)
    m.box('guard', B, [-3, 5.5, -1.75], [3, 7.5, 1.75], grad=(5.5, 2, 7.5, 4))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'rib_{n}', B, [(sd * 3, 7), (sd * 4.5, 9), (sd * 3.5, 11)], [1.25, 1.25], 0.8, 3, 5, tip=True)
    m.box('blade', V, [-2.25, 7.5, -0.85], [2.25, 26, 0.85], grad=(7.5, 2, 26, 5))
    m.chain('vein_glow', G, [(0, 8), (-0.5, 14), (0.5, 20), (0, 25)], [0.75, 0.75, 0.75], 0.95, 2, 4, tip=False)
    m.seg('vein_l', G, (-0.4, 13), (-1.75, 16.5), 0.6, 0.95, 2, 3)
    m.seg('vein_r', G, (0.4, 19), (1.75, 22), 0.6, 0.95, 2, 3)
    point(m, 'tip', V, -2.25, 2.25, 26, 3, 0.85, 5)
    return m


def abyss_sinking_anchor():
    """沈め錨: 長い柄の先に錨の頭。横の竿と、下へ反る爪の片方が欠けている。爪の先に淵の光"""
    m = M('abyss_sinking_anchor')
    hammer_haft(m, 32)
    m.box('ring', B, [-1, 31.5, -2], [1, 35.5, 2], 4)
    m.box('stock', B, [-7, 29, -1.25], [7, 30.5, 1.25], grad=(29, 3, 30.5, 5))
    m.arc('arm_r', V, (0, 26), 7, 270, 360, 4, 3, 1.5, 3, 6)
    m.arc('arm_l', V, (0, 26), 7, 210, 270, 2, 3, 1.5, 3, 5)
    m.seg('fluke_r', V, (7, 26), (8.5, 30), 3.5, 1.75, 6, 8, tip=True)
    m.box('fluke_glow', G, [7.25, 28.5, -0.5], [8.75, 30, 0.5], 4)
    m.seg('break', V, (-6, 22.5), (-6.75, 24), 2.5, 1.5, 3, 4)
    m.box('crown', V, [-2, 18, -2], [2, 20.5, 2], 4)
    return m


def abyss_ash_scythe():
    """灰喰いの鎌: 長い柄の先から -x へ大きく反る黒い刃、刃の背に灰の骨の鋸歯、刃の縁に淵の光"""
    m = M('abyss_ash_scythe')
    grip(m, -12, 2)
    rod(m, 3.5, 34, bands=(14, 24))
    m.box('socket', B, [-2, 32, -2], [2, 36, 2], grad=(32, 2, 36, 4))
    pts = [(1, 35), (-4, 37.5), (-9, 38), (-13.5, 36), (-16.5, 32.5)]
    m.chain('blade', V, pts, [4, 3.5, 3, 2.5], 1, 3, 6, tip=True)
    m.chain('edge_glow', G, [(x, y - 1.75) for x, y in pts[:4]], [1, 1, 1], 0.7, 2, 4, tip=False)
    for k, (x, y) in enumerate(((-3, 39.5), (-7, 40), (-11, 38.5))):
        m.seg(f'tooth_{k}', B, (x, y), (x - 1, y + 2), 1.5, 0.8, 4, 5, tip=True)
    return m


# ================================================================ 鯨（岩鯨の心核）

def abyss_whale_hammer():
    """背骨打ちの鎚: 頭は岩鯨の背骨ひとつ（椎体の円筒と上へ伸びる棘突起、左右の横突起）"""
    m = M('abyss_whale_hammer')
    hammer_haft(m)
    c = 30
    m.box('centrum', B, [-6, c - 3.5, -3.5], [6, c + 3.5, 3.5], grad=(c - 3.5, 3, c + 3.5, 5))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'face_{n}', B, [sd * 6 - (1 if sd < 0 else 0), c - 3, -3], [sd * 6 + (1 if sd > 0 else 0), c + 3, 3], 5)
        m.seg(f'process_{n}', B, (sd * 2, c), (sd * 3, c - 6), 2, 1.5, 4, 3, tip=True, zc=sd * 2.5)
    m.seg('spine', B, (0, c + 3.5), (-1, c + 10), 2.5, 1.25, 4, 5, tip=True)
    m.box('canal_glow', G, [-6.1, c - 1, -1], [6.1, c + 1, 1], 3)
    m.box('socket', V, [-2, 24, -2], [2, 26.5, 2], 3)
    return m


def abyss_whale_staff():
    """鯨眼の杖: 先に石の瞼が上下から抱く大きな鯨の目（淵の光の瞳）"""
    m = M('abyss_whale_staff')
    staff(m, 33)
    m.box('socket', B, [-2.5, 32.5, -2.25], [2.5, 35, 2.25], 3)
    c = 40.5
    m.box('eye_glow', G, [-3.5, c - 2.5, -2.25], [3.5, c + 2.5, 2.25], grad=(c - 2.5, 2, c + 2.5, 4))
    m.box('pupil', V, [-1, c - 1.25, -2.4], [1, c + 1.25, 2.4], 0)
    for sd, n in ((1, 'u'), (-1, 'd')):
        m.box(f'lid_{n}', V, [-5, c + sd * 2.25 - 1.5, -2.75], [5, c + sd * 2.25 + 1.5, 2.75], 5 if sd > 0 else 3)
        m.box(f'lid_{n}_rim', B, [-4.5, c + sd * 3.75 - 0.5, -3], [4.5, c + sd * 3.75 + 0.5, 3], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'corner_{n}', V, [sd * 5 - (1.5 if sd < 0 else 0), c - 1.5, -2], [sd * 5 + (1.5 if sd > 0 else 0), c + 1.5, 2], 4)
    return m


def abyss_whale_bell_staff():
    """耳骨の鈴杖: 先は U 字の鯨の顎骨、顎の先から耳の骨の鈴が二つ下がる"""
    m = M('abyss_whale_bell_staff')
    staff(m, 33)
    m.arc('jaw', B, (0, 40), 6, 190, 350, 6, 2.25, 1.4, 4, 6)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'ramus_{n}', B, (sd * 5.9, 39), (sd * 6.5, 46), 2.25, 1.4, 5, 7, tip=True)
        m.box(f'cord_{n}', V, [sd * 6.25 - 0.3, 41, -0.3], [sd * 6.25 + 0.3, 44, 0.3], 3)
        m.box(f'ossicle_{n}', B, [sd * 6.25 - 1.5, 38, -1.25], [sd * 6.25 + 1.5, 41, 1.25], grad=(38, 3, 41, 5))
        m.box(f'ossicle_{n}_glow', G, [sd * 6.25 - 0.75, 37.5, -0.75], [sd * 6.25 + 0.75, 38.25, 0.75], 3)
    return m


def abyss_whale_fang():
    """古銛の牙: 錆びた古い銛先（返しの付いた三角の頭）、柄に巻いた縄が垂れる"""
    m = M('abyss_whale_fang')
    grip(m, -4, 5)
    m.box('socket', V, [-1.75, 6.5, -1.5], [1.75, 11, 1.5], grad=(6.5, 2, 11, 4))
    m.box('lash', B, [-2, 7.5, -1.75], [2, 9, 1.75], 4)
    m.box('head', V, [-3, 11, -0.9], [3, 17, 0.9], grad=(11, 3, 17, 5))
    point(m, 'point', V, -3, 3, 17, 4, 0.9, 5)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'barb_{n}', V, (sd * 2.5, 13), (sd * 5, 9.5), 2, 0.8, 5, 6, tip=True)
    m.box('rust_glow', G, [-0.5, 12, -1], [0.5, 22, 1], 3)
    m.chain('rope', B, [(1.75, 8), (3.5, 5), (3.25, 1.5)], [1, 1], 0.6, 3, 4, tip=True)
    return m


def abyss_whale_bow():
    """鯨ひげの弓: 腕は鯨のひげ板を重ねた暗い板（腕に沿って斜めの板が並ぶ）、握りは骨"""
    m = M('abyss_whale_bow')
    bow_grip(m)
    arm = [(12, 21), (13, 27), (12.75, 33), (11, 38.5), (8, 43), (5, 45.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        pts = [mirror(p, sign) for p in arm]
        m.chain(f'arm_{n}', V, pts, [3, 2.75, 2.5, 2.25, 2], 1.2, 3, 6, tip=False)
        for k in range(1, 5):
            x, y = pts[k]
            m.seg(f'plate_{n}{k}', V, (x + 0.5, y), (x + 4 - k * 0.5, y - sign * 2), 2, 0.4, 5, 7, tip=True)
        m.seg(f'nock_{n}', B, mirror((6.5, 45.25), sign), mirror((3, 45.25), sign), 2, 1, 4, 5)
    strings(m, (3.5, 45.5), (3.5, -15.5))
    return m


# ================================================================ 潮（潮殻の心核）

def abyss_tide_sword():
    """螺旋貝の大剣: 長い巻貝（タケノコ貝）がそのまま刃。段ごとに少しずつ細る殻の輪と、輪の溝の淵の光"""
    m = M('abyss_tide_sword')
    grip(m, -7, 7)
    m.box('aperture', B, [-4.5, 8.5, -3], [4.5, 12, 3], grad=(8.5, 3, 12, 5))
    for k in range(9):
        w = 3.75 - k * 0.38
        y = 12 + k * 4.25
        m.box(f'whorl_{k}', V if k % 2 == 0 else B, [-w, y, -w * 0.6], [w, y + 4.25, w * 0.6], 3 + k * 0.3)
        if k < 8:
            m.box(f'suture_{k}_glow', G, [-w - 0.1, y + 3.75, -w * 0.6 - 0.1], [w + 0.1, y + 4.25, w * 0.6 + 0.1], 2)
    m.seg('apex', V, (0, 50), (0, 54), 1.25, 0.6, 5, 7, tip=True)
    return m


def abyss_tide_hammer():
    """大蛤の鎚: 頭は閉じた大きな二枚貝（上下の殻の筋）、殻の隙間に真珠が光る"""
    m = M('abyss_tide_hammer')
    hammer_haft(m)
    c = 30.5
    for sd, n in ((1, 'u'), (-1, 'd')):
        m.box(f'valve_{n}', V, [-6, c + (0.4 if sd > 0 else -4.5), -4], [6, c + (4.5 if sd > 0 else -0.4), 4], grad=(c - 4, 3, c + 4.5, 6))
        for k, x in enumerate((-3.5, 0, 3.5)):
            m.box(f'rib_{n}{k}', B, [x - 0.4, c + (0.6 if sd > 0 else -4.3), -4.2], [x + 0.4, c + (4.3 if sd > 0 else -0.6), 4.2], 4)
    m.box('pearl_glow', G, [5, c - 1, -1], [7, c + 1, 1], 4)
    m.box('hinge', B, [-7, c - 1.5, -2], [-5.5, c + 1.5, 2], 4)
    m.box('socket', V, [-2, 24, -2], [2, 26.5, 2], 3)
    return m


def abyss_tide_staff():
    """渦潮の杖: 先に巻いた貝の殻（輪が三段、上ほど小さく片側へずれる）、殻の口の中で渦が光る"""
    m = M('abyss_tide_staff')
    staff(m, 33)
    for k, (x, y, w) in enumerate(((0, 34, 5), (1, 38, 4), (1.75, 41.5, 3), (2.25, 44.5, 1.75))):
        m.box(f'coil_{k}', V if k % 2 == 0 else B, [x - w, y, -w], [x + w, y + 3.5, w], 4 + k * 0.6)
    m.box('mouth_glow', G, [-5.25, 35, -2], [-4.4, 37, 2], 4)
    m.arc('whirl', G, (-5, 36), 1.75, 0, 300, 5, 0.75, 0.6, 3, 4, zc=-2.6)
    return m


def abyss_tide_dagger():
    """海胆の針: 大海胆の長い棘（細くまっすぐ、先へ光る）。殻の欠片の鍔に小さな棘が並ぶ"""
    m = M('abyss_tide_dagger')
    grip(m, -4, 4)
    m.box('test', B, [-3.5, 5.5, -2.5], [3.5, 8.5, 2.5], grad=(5.5, 3, 8.5, 5))
    for k, (x, z) in enumerate(((-3, -1.5), (3, 1.5), (-1.5, 2.5), (1.5, -2.5))):
        m.seg(f'spinelet_{k}', V, (x, 8.5), (x * 1.35, 10.5), 1, 0.5, 3, 5, tip=True, zc=z)
    m.box('spine', V, [-1.25, 8.5, -1.25], [1.25, 26, 1.25], grad=(8.5, 2, 26, 4))
    m.box('spine_glow', G, [-0.75, 24, -0.75], [0.75, 31, 0.75], grad=(24, 2, 31, 5))
    m.seg('needle', G, (0, 31), (0, 34), 1, 0.5, 4, 5, tip=True)
    return m


def abyss_tide_bow():
    """鰭の弓: 大魚の鰭の骨（細い軸が扇に開く）と間に張った膜。腕の外側に鰭が広がる"""
    m = M('abyss_tide_bow')
    bow_grip(m)
    arm = [(12, 21), (12.5, 27), (11.75, 33), (9.75, 38.5), (6.75, 43), (3.5, 46)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        pts = [mirror(p, sign) for p in arm]
        m.chain(f'arm_{n}', B, pts, [2.5, 2.25, 2, 2, 1.75], 1, 3, 5, tip=False)
        m.chain(f'membrane_{n}', V, [(x + 2.5, y) for x, y in pts[1:5]], [4, 4, 3.5], 0.3, 3, 5, tip=False)
        for k in range(1, 5):
            x, y = pts[k]
            m.seg(f'ray_{n}{k}', B, (x + 0.5, y), (x + 5.5 - k * 0.6, y + sign * 0.5), 1, 0.5, 4, 5, tip=True)
        m.seg(f'nock_{n}', B, mirror((5, 46.25), sign), mirror((2, 46.25), sign), 2, 1, 4, 5)
    strings(m, (3, 46.25), (3, -16.25))
    return m


def abyss_bell_staff():
    """沈み鐘の杖: 先に沈んだ鐘を吊る。鐘はフジツボに覆われ、口の奥に淵の光、鐘を吊る錆びた鉤"""
    m = M('abyss_bell_staff')
    staff(m, 36)
    m.chain('hook', V, [(0, 35.5), (0.5, 41), (3.5, 44), (7, 43.5), (8, 41.5)], [2.25, 2.25, 2, 1.75], 1.1, 4, 6, tip=False)
    bell(m, 'bell', V, (7.5, 29.5), 4.5, 11, 3)
    m.box('throat_glow', G, [3.75, 29.25, -3.25], [11.25, 29.75, 3.25], 4)
    for k, (x, y, z) in enumerate(((4.5, 35, -3.7), (10, 32, 3.7), (8.5, 37.5, -3.7))):
        m.box(f'barnacle_{k}', B, [x - 0.75, y - 0.75, z - 0.4], [x + 0.75, y + 0.75, z + 0.4], 5)
    return m


# ================================================================ 雷（雷翼の心核）

def abyss_storm_sword():
    """避雷の大剣: まっすぐな黒い刃に骨の帯（銅の代わり）を螺旋に巻く。先は二股に割れ、間に淵の稲妻"""
    m = M('abyss_storm_sword')
    grip(m, -7, 7)
    guard_bar(m, 8.5, 5.5)
    m.box('blade', V, [-3, 10.5, -1.1], [3, 44, 1.1], grad=(10.5, 3, 44, 6))
    for k in range(6):
        y = 13 + k * 5
        m.seg(f'wrap_{k}', B, (-3.2, y), (3.2, y + 2), 1.25, 1.25, 4, 5, over=0)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'prong_{n}', V, (sd * 1.75, 44), (sd * 2.5, 52), 2.5, 1, 5, 7, tip=True)
    m.chain('arc_glow', G, [(0, 44), (-0.75, 46.5), (0.75, 48.5), (0, 51)], [0.9, 0.9, 0.9], 0.7, 3, 5, tip=False)
    return m


def abyss_storm_hammer():
    """雷鼓の鎚: 頭は横に寝かせた太鼓（胴は黒、両の打面は骨、縁に鋲）。胴の帯に淵の稲妻"""
    m = M('abyss_storm_hammer')
    hammer_haft(m)
    c = 30
    m.box('shell', V, [-5.5, c - 4, -4], [5.5, c + 4, 4], grad=(c - 4, 2, c + 4, 5))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'head_{n}', B, [sd * 5.5 - (1.25 if sd < 0 else 0), c - 4.5, -4.5], [sd * 5.5 + (1.25 if sd > 0 else 0), c + 4.5, 4.5], 5)
        for k, (y, z) in enumerate(((c + 3.5, 0), (c - 3.5, 0))):
            m.box(f'tack_{n}{k}', V, [sd * 6.8 - 0.4, y - 0.5, z - 0.5], [sd * 6.8 + 0.4, y + 0.5, z + 0.5], 6)
    m.chain('bolt_glow', G, [(-4, c + 1), (-1.5, c - 1.5), (1.5, c + 1.5), (4, c - 1)], [1, 1, 1], 4.1, 3, 5, tip=False)
    m.box('socket', B, [-2, 24, -2], [2, 26.5, 2], 3)
    return m


def abyss_storm_staff():
    """雲籠の杖: 先に骨の籠（六本の柱と上下の輪）、中に雷雲の塊が淵の光をはらむ"""
    m = M('abyss_storm_staff')
    staff(m, 33)
    for k, (y, r) in enumerate(((33.5, 4.5), (44, 4))):
        m.box(f'hoop_{k}', B, [-r, y, -r], [r, y + 1.5, r], 4)
    for k in range(6):
        a = math.radians(30 + k * 60)
        x, z = 3.9 * math.cos(a), 3.9 * math.sin(a)
        m.box(f'bar_{k}', B, [x - 0.45, 35, z - 0.45], [x + 0.45, 44, z + 0.45], 5)
    for k, (x, y, s) in enumerate(((-1, 38, 3), (1.25, 40, 3.25), (0, 41.5, 2.5))):
        m.box(f'cloud_{k}', V, [x - s, y - s * 0.6, -s * 0.8], [x + s, y + s * 0.6, s * 0.8], 6 + k * 0.5)
    m.chain('bolt_glow', G, [(-0.5, 41), (0.75, 38.5), (-0.5, 36.5)], [0.9, 0.9], 2.9, 3, 5, tip=False)
    m.seg('finial', B, (0, 45.5), (0, 49), 1.5, 0.75, 4, 6, tip=True)
    return m


def abyss_storm_dagger():
    """雷翼の鉤爪: 大きな鉤爪がそのまま刃（背へ反る太い爪）、根元の羽毛の房、爪の内側の縁に淵の光"""
    m = M('abyss_storm_dagger')
    grip(m, -4, 4)
    m.box('knuckle', B, [-2.5, 5.5, -2], [2.5, 8.5, 2], grad=(5.5, 3, 8.5, 5))
    pts = [(0, 8), (0.5, 13), (0, 18), (-2, 22.5), (-5, 25.5), (-8, 26)]
    m.chain('talon', V, pts, [4.5, 4, 3.5, 2.75, 2], 1.25, 3, 6, tip=True)
    m.chain('edge_glow', G, [(x + 1.75, y) for x, y in pts[:4]], [0.75, 0.75, 0.75], 0.8, 2, 4, tip=False)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'tuft_{n}', B, (sd * 2, 7), (sd * 4, 4), 2, 0.6, 3, 5, tip=True)
    return m


def abyss_storm_bell_staff():
    """風鈴の鈴杖: 先の骨の横木から長さの違う管が五本下がる（中の一本が光る）。横木の上に小さな風受け"""
    m = M('abyss_storm_bell_staff')
    staff(m, 40)
    m.box('crossbar', B, [-6.5, 39, -1.5], [6.5, 41, 1.5], grad=(39, 3, 41, 5))
    m.box('cap', V, [-2.5, 41, -2], [2.5, 43, 2], 4)
    m.seg('vane', B, (0, 43), (0, 47), 3, 0.4, 4, 6, tip=True)
    for k, (x, L) in enumerate(((-5.5, 7), (-3, 9.5), (0, 11), (3, 9), (5.5, 6.5))):
        mat = G if k == 2 else V
        m.box(f'tube_{k}' + ('_glow' if k == 2 else ''), mat, [x - 0.75, 39 - L, -0.75], [x + 0.75, 39, 0.75], grad=(39 - L, 2, 39, 5))
    return m


def abyss_storm_bow():
    """雷淵の弓: 雷雲の底で曲がった弓。腕は途中で一度鋭く折れ（稲妻の形）、折れ目が淵の光で継がれる"""
    m = M('abyss_storm_bow')
    bow_grip(m)
    arm = [(12, 21), (13.5, 27), (10.5, 30), (12.5, 36), (9, 41), (5, 45.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        pts = [mirror(p, sign) for p in arm]
        m.chain(f'arm_{n}', V, pts, [3, 2.75, 2.75, 2.5, 2], 1.3, 3, 6, tip=False)
        for k in (2, 3):
            x, y = pts[k]
            m.box(f'joint_{n}{k}_glow', G, [x - 1, y - 1, -1.4], [x + 1, y + 1, 1.4], 4)
        m.seg(f'nock_{n}', B, mirror((6.5, 45.25), sign), mirror((3, 45.25), sign), 2, 1, 4, 5)
    strings(m, (3.5, 45.5), (3.5, -15.5))
    return m


# ================================================================ 火（余燼の心核）

def abyss_ember_sword():
    """溶岩流の大剣: 冷えかけた溶岩の流れが刃（幅が波打つ黒い塊の列）、塊の継ぎ目の割れ目が淵の光（青い熾）"""
    m = M('abyss_ember_sword')
    grip(m, -7, 7)
    m.box('crust', B, [-4.5, 8.5, -2], [4.5, 11, 2], grad=(8.5, 3, 11, 5))
    for k in range(8):
        w = 3.5 + (0.75 if k % 2 == 0 else 0) - k * 0.2
        x = 0.5 if k % 2 == 0 else -0.5
        y = 11 + k * 4.5
        m.box(f'flow_{k}', V, [x - w, y, -1.2], [x + w, y + 4.5, 1.2], 2 + k * 0.4)
        m.box(f'crack_{k}_glow', G, [x - w * 0.6, y + 4, -1.3], [x + w * 0.6, y + 4.6, 1.3], 3)
    point(m, 'tip', V, -3, 3, 47, 3, 1.1, 4)
    return m


def abyss_ember_staff():
    """火山弾の杖: 先の三本の黒い爪の上に、噴き上げられた火山弾（割れ目が光る黒い塊）が浮く"""
    m = M('abyss_ember_staff')
    staff(m, 34)
    for k, a in enumerate((90, 210, 330)):
        x, z = 2.5 * math.cos(math.radians(a)), 2.5 * math.sin(math.radians(a))
        m.seg(f'prong_{k}', V, (x * 0.5, 34), (x * 1.6, 39), 1.5, 0.75, 4, 6, tip=True, zc=z)
    c = 43
    m.box('bomb', V, [-3.5, c - 3, -3], [3.5, c + 3, 3], grad=(c - 3, 2, c + 3, 5))
    m.box('bomb_top', V, [-2.5, c + 3, -2], [2.5, c + 4.5, 2], 5)
    m.box('bomb_tail', V, [-2, c - 4.5, -2], [2, c - 3, 2], 3)
    m.chain('fissure_glow', G, [(-3.6, c + 1), (-1, c - 1), (1.5, c + 1.5), (3.6, c - 0.5)], [1, 1, 1], 3.1, 3, 5, tip=False)
    return m


def abyss_ember_bell_staff():
    """坩堝の鈴杖: 先の骨の鉤から鎖で坩堝が下がる。坩堝の口から淵の熔けた光が溢れて縁を垂れる"""
    m = M('abyss_ember_bell_staff')
    staff(m, 38)
    m.chain('hook', B, [(0, 37.5), (0.5, 41), (3, 43), (5.5, 42)], [2, 2, 1.75], 1, 4, 6, tip=False)
    m.box('chain_0', V, [5, 38.5, -0.5], [6, 41.5, 0.5], 4)
    m.box('chain_1', V, [5.25, 36, -1], [5.75, 39, 1], 5)
    m.box('crucible', V, [2.5, 30, -3], [8.5, 36, 3], grad=(30, 2, 36, 5))
    m.box('crucible_foot', V, [3.5, 29, -2], [7.5, 30, 2], 3)
    m.box('lip', B, [2, 35.5, -3.5], [9, 36.5, 3.5], 4)
    m.box('melt_glow', G, [3, 35.75, -2.5], [8, 36.75, 2.5], 4)
    m.box('drip_glow', G, [8.6, 32, -0.5], [9.4, 35.5, 0.5], 3)
    return m


def abyss_ember_dagger():
    """熔け滴の短剣: 滴った熔け鉄が波打つ刃のまま固まった（左右へ揺れる節の刃）、先の滴が光る"""
    m = M('abyss_ember_dagger')
    grip(m, -4, 4)
    m.box('guard', B, [-3, 5.5, -1.75], [3, 7.5, 1.75], grad=(5.5, 2, 7.5, 4))
    pts = [(0, 7)]
    for k in range(1, 7):
        pts.append((math.sin(k * 1.2) * 1.5, 7 + k * 3.5))
    m.chain('blade', V, pts, [4.5, 4.25, 4, 3.5, 3, 2.5], 1, 3, 6, tip=True)
    m.box('drop_glow', G, [-0.75, 30, -0.75], [0.75, 32, 0.75], 4)
    m.seg('drop_tip', G, (0, 32), (0, 34), 1, 0.5, 4, 5, tip=True)
    return m


def abyss_ember_bow():
    """黒曜の弓: 腕は黒曜石の切子（角張った板）で、内側に熾火の芯（淵の光）が透ける"""
    m = M('abyss_ember_bow')
    bow_grip(m)
    arm = [(12, 21), (12.75, 27), (12.25, 33), (10.25, 38.5), (7.25, 43), (4.5, 45.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        pts = [mirror(p, sign) for p in arm]
        m.chain(f'arm_{n}', V, pts, [3.5, 3.25, 3, 2.75, 2.25], 1.4, 2, 5, tip=False)
        m.chain(f'core_{n}_glow', G, [(x - 0.25, y) for x, y in pts[:4]], [1, 1, 1], 1.5, 2, 4, tip=False)
        for k in (1, 3):
            x, y = pts[k]
            m.seg(f'facet_{n}{k}', V, (x + 1.5, y - 1), (x + 3, y + 1), 2, 1, 6, 7, tip=True)
        m.seg(f'nock_{n}', B, mirror((6, 45.5), sign), mirror((2.5, 45.5), sign), 2, 1, 4, 5)
    strings(m, (3, 45.5), (3, -15.5))
    return m


# ================================================================ 層の品

def abyss_lamp_staff():
    """灯籠の杖: 先の骨の腕から石灯籠を吊る（笠・火袋・台）、火袋の窓から青白い火"""
    m = M('abyss_lamp_staff')
    staff(m, 40)
    m.chain('arm', B, [(0, 39.5), (2, 42), (5.5, 42.5)], [2, 1.75], 1, 4, 6, tip=False)
    m.box('cord', V, [5.25, 39.5, -0.3], [5.75, 42, 0.3], 3)
    m.box('roof', V, [2, 37.5, -3.5], [9, 39.5, 3.5], 5)
    m.box('roof_top', V, [4.5, 39.5, -1], [6.5, 40.5, 1], 6)
    m.box('firebox', V, [3, 32, -2.5], [8, 37.5, 2.5], grad=(32, 2, 37.5, 4))
    m.box('window_glow', G, [3.75, 33, -2.6], [7.25, 36.5, 2.6], 4)
    m.box('base', V, [2.5, 30.5, -3], [8.5, 32, 3], 3)
    return m


def abyss_toll_bell_staff():
    """弔鐘の鈴杖: 先の骨の横木に青銅の弔いの鐘（淵の黒）、鐘から結び目のある縄が垂れる"""
    m = M('abyss_toll_bell_staff')
    staff(m, 41)
    m.box('yoke', B, [-4.5, 40.5, -1.5], [4.5, 42.5, 1.5], grad=(40.5, 3, 42.5, 5))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'post_{n}', B, [sd * 4 - 0.6, 34, -0.6], [sd * 4 + 0.6, 40.5, 0.6], 4)
    bell(m, 'bell', V, (0, 32.5), 3.5, 8, 3)
    for k in range(3):
        y = 31 - k * 2.25
        m.box(f'knot_{k}', B, [-0.75, y - 0.75, -0.75], [0.75, y + 0.75, 0.75], 4)
        m.box(f'rope_{k}', B, [-0.35, y - 2.25, -0.35], [0.35, y - 0.75, 0.35], 3)
    m.box('tongue_glow', G, [-0.75, 32, -0.75], [0.75, 33.5, 0.75], 4)
    return m


def abyss_depth_bow():
    """測鉛の弓: 細い黒い弓の両端から、深さを測る鉛の錘が紐で下がる（錘の底が光る）"""
    m = M('abyss_depth_bow')
    bow_grip(m)
    arm = [(12, 21), (12.5, 27), (11.75, 33), (9.75, 38.5), (6.75, 43), (3.5, 46)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'arm_{n}', V, [mirror(p, sign) for p in arm], [3, 2.75, 2.5, 2.25, 2], 1.2, 3, 6, tip=False)
        m.seg(f'nock_{n}', B, mirror((5, 46.25), sign), mirror((2, 46.25), sign), 2, 1, 4, 5)
    for n, top in (('u', 46), ('d', -16)):
        m.box(f'line_{n}', B, [-1.25, top - 5, -0.3], [-0.75, top + 0.25, 0.3], 3)
        m.box(f'arm_tie_{n}', B, [-1.25, top - 0.25, -0.3], [2.5, top + 0.25, 0.3], 3)
        m.box(f'lead_{n}', V, [-2.5, top - 9, -1.25], [0.5, top - 5, 1.25], grad=(top - 9, 2, top - 5, 4))
        m.box(f'lead_{n}_glow', G, [-1.75, top - 9.5, -0.75], [-0.25, top - 9, 0.75], 3)
    strings(m, (3, 46.25), (3, -16.25))
    return m


# ---------------------------------------------------------------- 弓の共通（握りの中心 (12, 15)）

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
    """淵の弓の握り: 骨を巻いた黒い握り、上下に錘の形の留め"""
    m.box('grip', V, [10.5, 10, -1.75], [13.5, 20, 1.75], grad=(10, 2, 20, 4), wrap=(10, 20))
    for n, y in (('u', 19.5), ('d', 9)):
        m.box(f'band_{n}', B, [10, y, -2.1], [14, y + 1.5, 2.1], 4)


ALL = [abyss_greatsword, abyss_lamp_sword, abyss_pulse_dagger, abyss_sinking_anchor, abyss_ash_scythe,
       abyss_whale_hammer, abyss_whale_staff, abyss_whale_bell_staff, abyss_whale_fang, abyss_whale_bow,
       abyss_tide_sword, abyss_tide_hammer, abyss_tide_staff, abyss_tide_dagger, abyss_tide_bow, abyss_bell_staff,
       abyss_storm_sword, abyss_storm_hammer, abyss_storm_staff, abyss_storm_dagger, abyss_storm_bell_staff, abyss_storm_bow,
       abyss_ember_sword, abyss_ember_staff, abyss_ember_bell_staff, abyss_ember_dagger, abyss_ember_bow,
       abyss_lamp_staff, abyss_toll_bell_staff, abyss_depth_bow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
