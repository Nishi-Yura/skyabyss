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
    'fin': ['#0a2a30', '#0f3c44', '#165058', '#1e666e', '#2a7e84', '#3a989a'],
    'deep': ['#0e4a4a', '#147070', '#1e9a96', '#3cc4bc', '#86e8dc', '#d4fff6'],
    # 沈んだ骨（2026-11-20 の作り直しから）: 灰色にせず、深みの青緑へ寄せた骨。明るい段ほど白く、影は青緑
    'sbone': ['#1c3436', '#2c4c4c', '#40665f', '#5e8478', '#83a596', '#abc6b4', '#d3e6d6', '#f0fbf2'],
})
V, B, G = 'void', 'abone', 'deep'
SB = 'sbone'
F = 'fin'


def spine_haft(m, top):
    """作り直した品の柄（深淵）: 淵の岩の握りと柄に、小さな椎骨の輪が三つ。柄頭も小さな椎骨で、下へ棘が一本"""
    m.box('grip', V, [-1.6, -12, -1.6], [1.6, 8, 1.6], grad=(-12, 2, 8, 4), wrap=(-12, 8))
    m.box('haft', V, [-1.3, 8, -1.3], [1.3, top, 1.3], grad=(8, 3, top, 5))
    for k, y in enumerate((-11, 6, 16)):
        m.box(f'ring_{k}', SB, [-2.3, y, -2.3], [2.3, y + 1.8, 2.3], grad=(y, 3, y + 1.8, 5), rim=True)
    m.box('pommel', SB, [-2.8, -15.5, -2.8], [2.8, -12, 2.8], grad=(-15.5, 3, -12, 5), rim=True)
    m.seg('pommel_spine', SB, (0, -15.5), (0.6, -19), 2.0, 1.2, 4, 6, tip=True)


def drum(m, name, mat, x0, x1, cy, r, t, glow=None, rim=True):
    """x 向きの太い円柱（八角: 縦長と横長の箱を十字に重ねる）"""
    k = 0.62
    m.box(f'{name}_a', mat, [x0, cy - r, -r * k], [x1, cy + r, r * k], grad=(cy - r, t - 1, cy + r, t + 1), rim=rim, glow=glow)
    m.box(f'{name}_b', mat, [x0, cy - r * k, -r], [x1, cy + r * k, r], t, glow=glow)


def M(name, accent=None):
    return Model(name, accent=accent or B, glowmat=G)


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


def sword_grip(m, top=6.0):
    """剣・短剣の握り: 淵の岩に椎骨の輪、柄頭は小さな椎骨"""
    m.box('grip', V, [-1.6, -9, -1.6], [1.6, top, 1.6], grad=(-9, 2, top, 4), wrap=(-9, top))
    for k, y in enumerate((-9.5, top - 1.5)):
        m.box(f'ring_{k}', SB, [-2.3, y, -2.3], [2.3, y + 1.8, 2.3], grad=(y, 3, y + 1.8, 5), rim=True)
    m.box('pommel', SB, [-2.6, -13, -2.6], [2.6, -9.5, 2.6], grad=(-13, 3, -9.5, 5), rim=True)
    m.seg('pommel_spine', SB, (0, -13), (0.5, -16), 1.8, 1.1, 4, 6, tip=True)


def staff2(m, top):
    spine_haft(m, top)
    m.box('band_hi', SB, [-2.0, top - 6, -2.0], [2.0, top - 4.5, 2.0], 4, rim=True)


def jaw_guard(m, y):
    """鍔: 骨の顎が左右へ開く（歯が刃の根を噛む）"""
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'jaw_{n}', SB, [(0, y), (sd * 6, y + 0.5), (sd * 11, y + 3), (sd * 13, y + 6)], [3.6, 3.0, 2.0], [2.0, 1.6, 1.2], 4, 6)
        for k, x in enumerate((3, 6, 9)):
            m.seg(f'tooth_{n}{k}', SB, (sd * x, y + 1.5), (sd * (x + 0.4), y + 4.2), 1.6, 1.0, 6, 7, tip=True)


def whale_eye(m, c, r):
    """鯨の目: 骨の白目（八角）と淵の光の瞳"""
    m.box('sclera_a', SB, [c[0] - r, c[1] - r * 0.62, -2.0], [c[0] + r, c[1] + r * 0.62, 2.0], 6, rim=True)
    m.box('sclera_b', SB, [c[0] - r * 0.62, c[1] - r, -2.0], [c[0] + r * 0.62, c[1] + r, 2.0], 6)
    m.box('pupil_glow', G, [c[0] - r * 0.45, c[1] - r * 0.45, -2.3], [c[0] + r * 0.45, c[1] + r * 0.45, 2.3], 4)


def ear_bell(m, pre, x, top, s, curl=1):
    """耳の骨の鈴（鼓室胞）: 丸く膨れた骨の殻で、片側が巻いて口が開く。舌は淵の光"""
    m.box(f'{pre}_cap', SB, [x - 1.2 * s, top - 1.6 * s, -1.2 * s], [x + 1.2 * s, top, 1.2 * s], 5)
    m.box(f'{pre}_body', SB, [x - 2.6 * s, top - 7.0 * s, -2.4 * s], [x + 2.6 * s, top - 1.4 * s, 2.4 * s],
          grad=(top - 7.0 * s, 4, top - 1.4 * s, 6), rim=True)
    cx0, cx1 = sorted((x + curl * 1.8 * s, x + curl * 3.8 * s))
    m.box(f'{pre}_curl', SB, [cx0, top - 6.2 * s, -1.6 * s], [cx1, top - 2.6 * s, 1.6 * s], 6)
    m.box(f'{pre}_lip', V, [x - 2.9 * s, top - 8.0 * s, -2.7 * s], [x + 2.9 * s, top - 6.8 * s, 2.7 * s], 4)
    m.box(f'{pre}_tongue_glow', G, [x - 0.7 * s, top - 9.6 * s, -0.7 * s], [x + 0.7 * s, top - 7.6 * s, 0.7 * s], 4)


def shell_whorls(m, pre, x, y0, n, w0, h0, shrink, mat=SB, tilt=6):
    """巻貝の殻の輪を下から積む（上ほど細い）。輪の継ぎ目に淵の光の線。積んだ上端の y を返す"""
    y = y0
    for k in range(n):
        w = w0 * (shrink ** k)
        h = h0 * (shrink ** k)
        m.seg(f'{pre}_{k}', mat, (x - w / 2, y + h / 2), (x + w / 2, y + h / 2 + (tilt if k % 2 else -tilt) * 0.05 * w), h, w * 0.36, 4, 6)
        m.box(f'{pre}_suture_{k}_glow', G, [x - w * 0.42, y + h - 0.4, -w * 0.38], [x + w * 0.42, y + h + 0.3, w * 0.38], 3)
        y += h * 0.92
    return y


def sunk_bell(m, pre, cx, top, s):
    """沈んだ鐘: 冠・肩・胴・口の縁、口の奥の淵の光、まわりにフジツボ"""
    m.box(f'{pre}_crown', V, [cx - 1.4 * s, top - 1.6 * s, -1.4 * s], [cx + 1.4 * s, top, 1.4 * s], 3)
    m.box(f'{pre}_shoulder', F, [cx - 3.0 * s, top - 3.6 * s, -3.0 * s], [cx + 3.0 * s, top - 1.4 * s, 3.0 * s], 4, rim=True)
    m.box(f'{pre}_body', F, [cx - 3.8 * s, top - 9.0 * s, -3.8 * s], [cx + 3.8 * s, top - 3.4 * s, 3.8 * s], grad=(top - 9 * s, 3, top - 3.4 * s, 5))
    m.box(f'{pre}_lip', F, [cx - 4.8 * s, top - 10.4 * s, -4.8 * s], [cx + 4.8 * s, top - 8.8 * s, 4.8 * s], 5, rim=True)
    m.box(f'{pre}_mouth_glow', G, [cx - 3.0 * s, top - 10.5 * s, -3.0 * s], [cx + 3.0 * s, top - 9.8 * s, 3.0 * s], 4)
    for k, (dx, dy, dz) in enumerate(((-3.9, -5, 1.5), (3.9, -7, -1), (1, -4, 3.9), (-1.5, -7.5, -3.9))):
        m.box(f'{pre}_barnacle_{k}', SB, [cx + dx * s - 1.0, top + dy * s - 1.0, dz * s - 1.0], [cx + dx * s + 1.0, top + dy * s + 1.2, dz * s + 1.0], 6)


# ================================================================ 底の品（大剣・剣・短剣・錨・鎌）

def abyss_greatsword():
    """底なしの大剣（伝説、2026-11-20 に作り直し・案 A）: A 割れて開く刃: 黒い幅広の刃が縦に二つに割れ、あいだが抜けて向こうが見える。外の縁は青緑の明るい縁取り。
    割れ目の底（刃の根）に淵の光の玉。鍔は骨の顎
    ※ 2026-11-20 に Blockbench で手を入れた（刃の 13 箱 → 縁が薄い二枚刃のメッシュ、顎の先を手前へ y 傾け、刃を筆で塗った）。正本は tools/models/x32/abyss_greatsword.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('abyss_greatsword', SB)
    sword_grip(m)
    jaw_guard(m, 7.0)
    m.box('root', V, [-5, 7.5, -2], [5, 13, 2], grad=(7.5, 2, 13, 4), rim=True)
    m.box('core_glow', G, [-1.8, 10, -2.3], [1.8, 13.5, 2.3], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        pts = [(sd * 3.2, 12), (sd * 4.6, 30), (sd * 4.4, 48), (sd * 2.4, 62)]
        m.chain(f'half_{n}', V, pts, [5.2, 4.8, 3.8], [1.8, 1.6, 1.3], 3, 5)
        m.chain(f'edge_{n}', F, [(p[0] + sd * 2.6, p[1]) for p in pts[:3]] + [(sd * 1.0, 64)], [1.4, 1.4, 1.2], [1.2, 1.1, 0.9], 4, 5)
    m.seg('tip', V, (0, 61), (0, 70), 3.0, 1.2, 4, 6, tip=True)
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
    """背骨打ちの鎚（伝説、2026-11-20 に作り直し・案 B）: 頭は横に連なる岩鯨の椎骨三つ。両端の大きな椎骨が打つ面で、
    面は淵の黒い岩に骨の縁。真ん中は小さく、骨と骨のあいだの椎間板が淵の光。棘突起が背びれのように三本、
    後ろへ反って並ぶ（真ん中が一番高い）。両端の椎骨の下に短い横突起。柄との継ぎ目は椎骨の首
    ※ 2026-11-20 に Blockbench で手を入れた（棘 9 箱 → 反る板のメッシュ 3 枚、横突起を外へ x 傾け、棘と椎骨の面を筆で塗った）。
    正本は tools/models/x32/abyss_whale_hammer.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('abyss_whale_hammer', SB)
    spine_haft(m, 27)
    cy = 33.0
    for k, (x0, x1, r) in enumerate(((-15, -7, 6.0), (-4.5, 4.5, 4.6), (7, 15, 6.0))):
        drum(m, f'vert_{k}', SB, x0, x1, cy, r, 4 + (k == 1))
        h = (8.0, 13.0, 9.0)[k]
        xm = (x0 + x1) / 2
        m.chain(f'spine_{k}', SB, [(xm + 0.6, cy + r - 0.5), (xm - 0.8, cy + r + h * 0.5), (xm - 2.6, cy + r + h * 0.85),
                                   (xm - 4.0, cy + r + h)], [4.2 if k == 1 else 3.6, 2.8, 1.8], [1.6, 1.3, 1.0], 4, 7)
        if k != 1:
            for sd in (-1, 1):
                m.seg(f'process_{k}{"lr"[sd > 0]}', SB, (xm, cy - r + 1.5), (xm + (-1.5 if k == 0 else 1.5), cy - r - 3.0),
                      2.4, 1.2, 4, 6, tip=True, zc=sd * 2.6)
    for k, x in enumerate((-7.2, 4.7)):
        drum(m, f'disc_{k}', G, x - 0.6, x + 3.0, cy, 4.2, 3, glow=True, rim=False)
    for sd, n in ((-1, 'l'), (1, 'r')):
        x = 15 if sd > 0 else -17
        drum(m, f'face_{n}', SB, x, x + 2, cy, 5.6, 6)
        x2 = x + 2 if sd > 0 else x - 1
        drum(m, f'strike_{n}', V, x2, x2 + 1, cy, 4.4, 3)
    m.box('neck', SB, [-2.6, 25.5, -2.6], [2.6, 28.8, 2.6], grad=(25.5, 3, 28.8, 5), rim=True)
    return m


def abyss_whale_staff():
    """鯨眼の杖（伝説、2026-11-20 に作り直し・案 A）: A 瞼に抱かれた目: 大きな鯨の目を、上と下から淵の黒い石の瞼（三日月）が抱く。上の瞼から骨のまつげが四本反る
    ※ 2026-11-20 に Blockbench で手を入れた（瞼の 11 箱 → 目を包む三日月の殻のメッシュ 2 枚、まつげ 4 本を尖る板に、瞳を縦長に、虹彩の輪を筆で）。正本は tools/models/x32/abyss_whale_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('abyss_whale_staff', SB)
    staff2(m, 40)
    c = (0.0, 52.0)
    whale_eye(m, c, 6.0)
    m.arc('lid_up', V, c, 8.0, 165, 15, 6, 3.4, 2.6, 3, 5)
    m.arc('lid_dn', V, c, 8.0, 200, 340, 5, 3.0, 2.6, 3, 4)
    for k, a in enumerate((140, 110, 75, 45)):
        p = (c[0] + math.cos(math.radians(a)) * 9.5, c[1] + math.sin(math.radians(a)) * 9.5)
        q = (c[0] + math.cos(math.radians(a)) * 14.5, c[1] + math.sin(math.radians(a)) * 14.5 + 1.5)
        m.seg(f'lash_{k}', SB, p, q, 1.8, 1.0, 5, 7, tip=True)
    m.box('stem', V, [-2.2, 40, -2.2], [2.2, 44.5, 2.2], 3, rim=True)
    return m


def abyss_whale_bell_staff():
    """耳骨の鈴杖（伝説、2026-11-20 に作り直し・案 A）: A U の顎と二つの耳骨: 杖の先に鯨の下顎骨が U に開き、両方の顎先から耳の骨の鈴が一つずつ下がる
    ※ 2026-11-20 に Blockbench で手を入れた（顎の 10 箱 → 付け根が太く先へ細る顎骨のメッシュ 2 本、鈴を外へ振れる向きに x/z 傾け、骨を筆で塗った）。正本は tools/models/x32/abyss_whale_bell_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('abyss_whale_bell_staff', SB)
    staff2(m, 38)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'jaw_{n}', SB, [(0, 38), (sd * 6, 40), (sd * 11, 45), (sd * 12.5, 52), (sd * 11, 58), (sd * 7.5, 60.5)],
                [4.0, 3.6, 3.0, 2.6, 2.2], 2.0, 4, 6)
        m.box(f'cord_{n}', V, [sd * 6.2 - 0.5, 57.5, -0.5], [sd * 6.2 + 0.5, 60.6, 0.5], 3)
        ear_bell(m, f'bell_{n}', sd * 6.2, 58.5, 1.15, curl=-sd)
    return m


def abyss_whale_fang():
    """古銛の牙（伝説、2026-11-20 に作り直し・案 A）: A 返しの銛先: 黒い古鉄の三角の銛先、両側に大きな返し。根元の袋（ソケット）に縄が巻いて垂れる
    ※ 2026-11-20 に Blockbench で手を入れた（段の銛先 16 箱と返し 6 箱 → 稜線の立つ菱の断面の刃と、細って尖る返しのメッシュ、刃と返しを筆で塗った）。正本は tools/models/x32/abyss_whale_fang.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('abyss_whale_fang', SB)
    sword_grip(m, 4)
    m.box('socket', V, [-2.4, 4, -2.4], [2.4, 11, 2.4], grad=(4, 2, 11, 4), rim=True)
    for k, y in enumerate((5, 7.5)):
        m.box(f'rope_{k}', SB, [-2.8, y, -2.8], [2.8, y + 1.6, 2.8], 3)
    m.chain('rope_tail', SB, [(2.6, 6), (5.0, 3.5), (6.0, -1), (5.4, -5)], [1.6, 1.4, 1.2], 1.0, 3, 4)
    # 頭: 根元が一番広い三角の銛先。段ごとに 2 単位ずつ細って尖る（方眼の段の輪郭）
    y = 11.0
    for k, w in enumerate((5.6, 5.0, 4.4, 3.8, 3.2, 2.6, 2.0, 1.4)):
        h = 2.4 if k < 6 else 2.0
        m.box(f'head_{k}', V, [-w, y, -1.5 + 0.08 * k], [w, y + h, 1.5 - 0.08 * k], 3 + k * 0.4, rim=True)
        m.box(f'edge_{k}', F, [-w - 0.8, y, -1.0], [w + 0.8, y + h - 0.3, 1.0], 4 + k * 0.2)
        y += h
    m.seg('point', V, (0, y - 0.5), (0, y + 4.0), 2.0, 1.0, 6, 7, tip=True)
    # 返し: 根元の両角から下へ大きく反る鉤
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'barb_{n}', V, [(sd * 5.0, 13.0), (sd * 7.6, 10.0), (sd * 8.6, 6.0), (sd * 8.0, 3.0)], [3.0, 2.4, 1.6], 1.3, 4, 6)
    m.box('rust_glow', G, [-1.2, 13.5, -1.7], [1.2, 17.5, 1.7], 3)
    return m


def abyss_whale_bow():
    """鯨ひげの弓（伝説）: 太く反った淵の岩の弓腕。腕の腹（弦の側）から鯨ひげが櫛のように密に垂れ下がって弓の懐を埋める（板の絵、先は骨の白の毛羽）。
    上の先は大きな鯨の尾びれ、下の先は骨の銛の穂先。握りの前には骨の頭骨の殻が張り出し、その潮吹きの穴から淵の光の
    しぶきが上へ噴き上がって浮かぶ。弦には骨の椎骨の玉が並ぶ"""
    m = M('abyss_whale_bow')
    m.box('grip', B, [10.25, 9.5, -2], [13.75, 20.5, 2], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'band_{n}', V, [9.75, y, -2.4], [14.25, y + 1.5, 2.4], 4)
    m.box('skull', B, [13.5, 10.5, -2.5], [19.5, 19, 2.5], grad=(10.5, 2, 19, 5))
    m.box('skull_brow', B, [15, 19, -2], [20, 20.5, 2], 5)
    m.seg('jaw', B, (18.5, 11.5), (23.5, 13), 2.25, 1.9, 3, 4, tip=True)
    m.seg('spout_glow', G, (16.6, 20.5), (19.5, 26), 1.25, 0.6, 3, 5, over=0.2)
    m.box('blowhole', V, [15.75, 20, -0.9], [17.4, 20.75, 0.9], 1)
    for k, (x, y, s) in enumerate(((17.5, 26.5, 2.25), (19.5, 28.25, 1.75), (22, 27.5, 1.75), (18.5, 31, 1.4), (23.25, 30.75, 1.4), (20.75, 31.5, 1.6), (20, 34.5, 1), (24, 34, 1), (16.75, 29.5, 1.1))):
        diamond(m, f'spray_{k}_glow', G, (x, y), s, 0.6, 4, 5)
    arm = [(12, 21), (13.5, 27), (13.25, 33), (11.5, 38.5), (8.5, 43), (5, 46)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        pts = [P(q) for q in arm]
        m.chain(f'arm_{n}', V, pts, [4.25, 4, 3.5, 3, 2.5], 1.6, 3, 6, tip=False)
        m.chain(f'ridge_{n}', B, [P((q[0] + 1.9 - k * 0.15, q[1])) for k, q in enumerate(arm[1:5])], [0.8, 0.8, 0.7], 1.75, 3, 5, tip=False)
        baleen_comb(m, f'baleen_{n}', arm, sign < 0)
    # 上の先: 鯨の尾びれ
    m.chain('fluke_stem', V, [(5, 46), (5.5, 48.5)], [2.25], 1.3, 5, 6, tip=False)
    m.chain('fluke_l', V, [(5.5, 48.5), (1.5, 51.5), (-3.5, 52.5)], [3.5, 2.5], 1.1, 5, 7, tip=True)
    m.chain('fluke_r', V, [(5.5, 48.5), (9.5, 51.75), (14.5, 53)], [3.5, 2.5], 1.1, 5, 7, tip=True)
    m.chain('fluke_edge', B, [(-3, 53.5), (1.5, 53), (5.5, 50.5), (9.5, 53.25), (14, 54.25)], [0.8, 0.8, 0.8, 0.8], 1.15, 4, 5, tip=False)
    # 下の先: 骨の銛の穂先
    m.chain('harpoon', B, [(5, -16), (5.75, -19), (5, -23)], [2.25, 1.75], 1.1, 4, 5, tip=True)
    m.seg('barb_0', B, (5.5, -20), (8, -18.25), 1.1, 0.8, 4, 5, tip=True)
    m.seg('barb_1', B, (5.25, -20.5), (2.75, -18.75), 1.1, 0.8, 4, 5, tip=True)
    top, bot = (4.5, 46.5), (4.5, -16.5)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((42, 38.5, 35, -5, -8.5, -12)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_vert{k}', B, [q[0] - 0.9, q[1] - 0.75, -0.9], [q[0] + 0.9, q[1] + 0.75, 0.9], 4 + k % 2)
    return m


# ================================================================ 潮（潮殻の心核）

def abyss_tide_sword():
    """螺旋貝の大剣（伝説、2026-11-20 に作り直し・案 A）: 刃はタケノコ貝の殻そのもの。殻の輪が段ごとに細って先へ尖り、
    輪の継ぎ目（縫合線）が淵の光。輪は一段ごとに左右へ少し傾いて螺旋に見える。鍔は貝の口の縁（青緑の殻）
    ※ 2026-11-20 に Blockbench で手を入れた（殻の輪 21 箱 → 段ごとに 24° 回りながら細る菱の断面の尖塔メッシュ（ねじれた巻貝）、継ぎ目の青緑と角の明るい螺旋の線を筆で）。正本は tools/models/x32/abyss_tide_sword.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('abyss_tide_sword', SB)
    sword_grip(m)
    m.chain('lip', F, [(-8.5, 10), (-4, 7.2), (4, 7.2), (8.5, 10.5)], [3.4, 2.6, 3.4], 2.6, 4, 6)
    y = shell_whorls(m, 'whorl', 0, 8.5, 10, 11.0, 7.6, 0.9)
    m.seg('apex', SB, (0, y - 0.5), (0.5, y + 8), 2.8, 1.1, 6, 8, tip=True)
    return m


def abyss_tide_hammer():
    """大蛤の鎚（伝説、2026-11-20 に作り直し・案 A）: 頭は横に寝た大きな二枚貝。上下の殻が閉じて、青緑の筋が放射に走る。
    合わせ目の前と後ろから真珠が光る。殻の両端が打つ面、上に黒い蝶番
    ※ 2026-11-20 に Blockbench で手を入れた（殻の箱と筋 9 箱 → ふくらんだ二枚の殻のメッシュ、放射の筋と成長線を筆で、両端に黒い打ち板を足した）。正本は tools/models/x32/abyss_tide_hammer.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('abyss_tide_hammer', SB)
    spine_haft(m, 27)
    cy = 33.0
    drum(m, 'valve_up', SB, -15, 15, cy + 3.2, 4.2, 5)
    drum(m, 'valve_dn', SB, -15, 15, cy - 3.2, 4.2, 4)
    for k, x in enumerate((-10, -5, 0, 5, 10)):
        m.box(f'rib_{k}', F, [x - 0.6, cy - 7.0, -5.0], [x + 0.6, cy + 7.0, 5.0], 4)
    m.box('seam', V, [-15.2, cy - 0.7, -4.4], [15.2, cy + 0.7, 4.4], 2)
    m.box('pearl_glow', G, [-2.0, cy - 2.0, 3.6], [2.0, cy + 2.0, 5.6], 4)
    m.box('pearl_b_glow', G, [-2.0, cy - 2.0, -5.6], [2.0, cy + 2.0, -3.6], 4)
    m.box('hinge', V, [-4, cy + 6.6, -2], [4, cy + 8.6, 2], 3, rim=True)
    m.box('neck', SB, [-2.6, 25.5, -2.6], [2.6, 28.8, 2.6], 4, rim=True)
    return m


def abyss_tide_staff():
    """渦潮の杖（伝説、2026-11-20 に作り直し・案 A）: 杖の先に大きく巻いた平たい渦巻の殻（オウムガイのよう）。
    殻は外から内へ細って巻き込み、殻の口の中で渦潮が光の輪になって回る
    ※ 2026-11-20 に Blockbench で手を入れた（渦の節 14 箱 → 外ほど太く巻き込むオウムガイの殻のメッシュ、縞を筆で）。正本は tools/models/x32/abyss_tide_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('abyss_tide_staff', SB)
    staff2(m, 40)
    c = (0.0, 53.0)
    pts = []
    for k in range(15):
        a = math.radians(-90 + k * 30)
        r = 11.5 * (0.93 ** k)
        pts.append((c[0] + math.cos(a) * r, c[1] + math.sin(a) * r))
    m.chain('coil', SB, pts, [5.0 * 0.94 ** k for k in range(14)], [3.6 * 0.95 ** k for k in range(14)], 4, 7, tip=False)
    m.arc('vortex_glow', G, (c[0] + 0.5, c[1] - 1.5), 4.4, 0, 330, 8, 1.2, 1.0, 3, 4)
    m.box('stem', V, [-2.2, 39, -2.2], [2.2, 43, 2.2], 3, rim=True)
    return m


def abyss_tide_dagger():
    """海胆の針（伝説、2026-11-20 に作り直し・案 C）: 鍔は丸い海胆の殻そのもの（まわりに短い棘が放射に生える）。
    そこから長い棘が一本伸びて刃になる。殻の表と裏に淵の光の目
    ※ 2026-11-20 に Blockbench で手を入れた（棘の箱 → 六角の断面で細る針のメッシュ（節の輪を筆で）、海胆の棘を前後に傾け、前後の棘 4 本を足した）。正本は tools/models/x32/abyss_tide_dagger.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('abyss_tide_dagger', SB)
    sword_grip(m, 3)
    drum(m, 'test', F, -5, 5, 8.0, 5.0, 4)
    for k in range(8):
        a = math.radians(k * 45 + 22)
        m.seg(f'spinelet_{k}', SB, (math.cos(a) * 4.6, 8 + math.sin(a) * 4.6), (math.cos(a) * 8.6, 8 + math.sin(a) * 8.6), 1.2, 0.8, 5, 7, tip=True)
    m.seg('spine', SB, (0, 12), (0, 40), 3.2, 1.5, 4, 7, tip=True)
    m.box('eye_glow', G, [-1.2, 6.8, 4.8], [1.2, 9.2, 5.6], 4)
    m.box('eye_b_glow', G, [-1.2, 6.8, -5.6], [1.2, 9.2, -4.8], 4)
    return m


def abyss_tide_bow():
    """鰭の弓（伝説）: 弓腕そのものが大魚の鰭。細い骨の芯の背に幅広の暗い膜が付き、骨の筋が膜を横切って縁へ走り、
    縁は淵の光で光る。先は外へ跳ね返る。握りの左右（奥行き）からは胸びれが大きく張り出す。握りの前に淵の光の鰓の筋が三本走り、
    両端のまわりには淵の光の水の粒が浮かぶ。弦には鱗の玉が並ぶ"""
    m = M('abyss_tide_bow')
    m.box('grip', V, [10.25, 9.5, -2], [13.75, 20.5, 2], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'band_{n}', B, [9.75, y, -2.4], [14.25, y + 1.5, 2.4], 4)
    m.box('gill_plate', B, [13.5, 11, -1.75], [15.5, 19, 1.75], grad=(11, 3, 19, 5))
    for k, y in enumerate((12.5, 14.75, 17)):
        m.seg(f'gill_{k}_glow', G, (15.25, y), (17.25, y + 0.75), 0.75, 1.1, 4, 5, over=0)
    for zs, zn in ((1, 'f'), (-1, 'b')):
        for k, (y0, y1, z0, z1) in enumerate(((12, 18, 2, 4.5), (12.5, 17.5, 4.5, 7), (13, 16.75, 7, 9.25), (13.5, 16, 9.25, 11))):
            za, zb = (z0, z1) if zs > 0 else (-z1, -z0)
            m.box(f'pec_{zn}{k}', V if k % 2 else B, [11, y0, za], [13, y1, zb], 3 + k * 0.5)
    arm = [(12, 21), (12.5, 27), (11.5, 33), (9.25, 38.5), (6.25, 42.5), (5, 45.5), (6.75, 48)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'web_{n}', 'fin', [P(q) for q in lib.offset_line(arm, 1.8)], [5.5, 7, 7.5, 6.5, 4.5, 2.5], 0.6, 2, 4, tip=True)
        m.chain(f'bone_{n}', B, [P(q) for q in arm], [2.25, 2, 1.85, 1.6, 1.4, 1.2], 1.2, 3, 5, tip=True)
        edge = lib.offset_line(arm, 5.2)
        m.chain(f'rim_{n}_glow', G, [P(q) for q in edge[1:5]], [0.7, 0.7, 0.6], 0.65, 4, 5, tip=False)
        for k in range(1, 5):
            a, b = arm[k], edge[k]
            m.seg(f'ray_{n}{k}', B, P(a), P((b[0] + (k - 2) * 0.6, b[1] + 0.8)), 0.8, 0.7, 4, 5, tip=True)
        for k, (x, y, s) in enumerate(((9.5, 49.5, 1.4), (12, 47.25, 1), (4, 51.5, 1.1), (11, 51.5, 0.8))):
            diamond(m, f'drop_{n}{k}_glow', G, P((x, y)), s, 0.55, 4, 5)
    top, bot = (6.25, 47.75), (6.25, -17.75)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((41, 36.5, 32, 27.5, -0.5, -5, -9.5)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            diamond(m, f'string_{key}_scale{k}', B if k % 2 else V, q, 1.5, 0.75, 4, 6)
    return m


def abyss_bell_staff():
    """沈み鐘の杖（エピック、2026-11-20 に作り直し・案 A）: 杖の先が錆びた鉤に曲がり、鉤から大きな鐘が下がる。
    鐘はフジツボに覆われ、口の中が淵の光
    ※ 2026-11-20 に Blockbench で手を入れた（鐘の 3 箱 → 八角でまわしてふくらむ鐘のメッシュ、フジツボを不揃いに傾けた）。正本は tools/models/x32/abyss_bell_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('abyss_bell_staff', SB)
    staff2(m, 44)
    m.chain('hook', V, [(0, 43), (0.5, 50), (4, 54), (8.5, 53), (10, 49.5)], [2.6, 2.4, 2.2, 2.0], 1.4, 3, 5)
    sunk_bell(m, 'bell', 10, 50.2, 0.95)
    return m


# ================================================================ 雷（雷翼の心核）

def abyss_storm_sword():
    """避雷の大剣（伝説、2026-11-20 に作り直し）: まっすぐな黒い刃に骨の帯が斜めに巻く（表と裏で向きが交差する）。
    鍔は避雷針の碍子のような骨の円盤を横に重ねたもの。先は二股に割れて外へ反り、股のあいだに淵の稲妻の火花が浮く
    ※ 2026-11-20 に Blockbench で手を入れた（刃と二股の先をメッシュにし、筆で骨の帯と火花を描いた）。正本は tools/models/x32/abyss_storm_sword.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('abyss_storm_sword', SB)
    sword_grip(m)
    for k, x in enumerate((-8.4, -5.2, 5.2, 8.4)):
        drum(m, f'insulator_{k}', SB, x - 1.1, x + 1.1, 8.0, 2.6 - 0.4 * (abs(x) > 6), 5)
    m.box('guard_core', V, [-7.5, 5.6, -1.6], [7.5, 9.6, 1.6], 3, rim=True)
    m.box('blade', V, [-3.2, 9.4, -1.3], [3.2, 54, 1.3], grad=(9.4, 2, 54, 5), rim=True)
    for k in range(5):
        y = 14 + k * 8.0
        for sd, z in ((1, 1.35), (-1, -1.35)):
            m.seg(f'band_{k}{"fb"[sd < 0]}', SB, (-3.6, y), (3.6, y + sd * 3.6), 1.6, 0.3, 5, 6, zc=z)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'prong_{n}', V, [(sd * 1.6, 53.5), (sd * 3.2, 59), (sd * 4.8, 64), (sd * 5.6, 68)], [3.0, 2.4, 1.6], 1.2, 4, 6)
    m.box('float_spark_glow', G, [-1.0, 58.5, -1.0], [1.0, 61.5, 1.0], 4)
    return m


def abyss_storm_hammer():
    """雷鼓の鎚（伝説、2026-11-20 に作り直し）: 頭は横に寝かせた雷の太鼓。胴は淵の黒、両端の打面は骨の皮、縁は骨の輪と鋲。
    胴の表と裏に淵の稲妻が一筋ずつ走る
    ※ 2026-11-20 に Blockbench で手を入れた（胴を膨らんだ樽のメッシュにし、骨の輪・鋲・ひびを筆で描いた）。正本は tools/models/x32/abyss_storm_hammer.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('abyss_storm_hammer', SB)
    spine_haft(m, 26)
    cy = 33.0
    drum(m, 'shell', V, -11, 11, cy, 6.4, 3)
    for sd, n in ((-1, 'l'), (1, 'r')):
        x0 = -13.5 if sd < 0 else 11
        drum(m, f'rim_{n}', SB, x0, x0 + 2.5, cy, 7.0, 4)
        x1 = -14.3 if sd < 0 else 13.5
        drum(m, f'head_{n}', SB, x1, x1 + 0.8, cy, 6.0, 6, rim=False)
        for k, a in enumerate((45, 135, 225, 315)):
            import math as _m
            y = cy + _m.sin(_m.radians(a)) * 5.0
            z = _m.cos(_m.radians(a)) * 5.0
            xc = x0 + 1.25
            m.box(f'tack_{n}{k}', V, [xc - 0.8, y - 0.8, z - 0.8], [xc + 0.8, y + 0.8, z + 0.8], 5)
    for sd, n in ((1, 'f'), (-1, 'b')):
        m.chain(f'bolt_{n}_glow', G, [(-8, cy + 3), (-3, cy - 1), (0, cy + 2), (4, cy - 2.5), (8, cy + 1)], 1.4, 0.4, 4, 5, zc=sd * 6.3)
    m.box('neck', SB, [-2.6, 25, -2.6], [2.6, 27.5, 2.6], 4, rim=True)
    return m


def abyss_storm_staff():
    """雲籠の杖（伝説、2026-11-20 に作り直し）: 杖の先に骨の籠（上下の輪と、外へふくらむ柱）。籠の中に雷雲の塊が浮き、
    雲の芯が淵の光をはらむ。籠の頂に骨の避雷の尖り
    ※ 2026-11-20 に Blockbench で手を入れた（籠の柱 6 本を軸のまわりに 60° ずつ回し、上下の輪を 6 本のスポークにし、雲を傾けて稲妻を描いた）。正本は tools/models/x32/abyss_storm_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('abyss_storm_staff', SB)
    staff2(m, 40)
    drum(m, 'ring_lo', SB, -4.5, 4.5, 42.0, 1.4, 5)
    drum(m, 'ring_hi', SB, -3.6, 3.6, 58.0, 1.2, 5)
    for k in range(6):
        m.chain(f'rib_{k}', SB, [(3.8, 42.5), (6.6, 47), (7.0, 51), (5.6, 55), (3.0, 58)], [1.6, 1.5, 1.4, 1.3], 1.0, 4, 6)
    for k, (x, y, s) in enumerate(((0, 50, 3.4), (-2.4, 48.5, 2.6), (2.6, 49, 2.6), (-1.2, 52.6, 2.4), (1.6, 52, 2.2))):
        m.box(f'float_cloud_{k}', V, [x - s, y - s * 0.7, -s * 0.8], [x + s, y + s * 0.7, s * 0.8], 8 - (k % 2), rim=True)
    m.box('float_core_glow', G, [-1.4, 48.6, -2.9], [1.4, 51.4, 2.9], 4)
    m.seg('spire', SB, (0, 58.5), (0, 65), 2.2, 1.0, 5, 7, tip=True)
    m.box('stem', V, [-2.2, 39, -2.2], [2.2, 42.5, 2.2], 3, rim=True)
    return m


def abyss_storm_dagger():
    """雷翼の鉤爪（伝説、2026-11-20 に作り直し）: 雷翼の大きな鉤爪がそのまま刃。根元が太く、背へ大きく反って先が鋭い。
    爪の内側の縁が淵の光。根元に骨の羽毛の房が三枚、下へ開く
    ※ 2026-11-20 に Blockbench で手を入れた（爪を反った一枚刃のメッシュにし、大きな光る縁は消して内側の縁を筆で光らせた）。正本は tools/models/x32/abyss_storm_dagger.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('abyss_storm_dagger', SB)
    sword_grip(m, 4)
    m.box('knuckle', V, [-3.4, 3.5, -2.6], [3.4, 8.5, 2.6], grad=(3.5, 2, 8.5, 4), rim=True)
    m.chain('claw', V, [(0, 8), (1.6, 16), (1.4, 24), (-1.6, 31), (-6, 36)], [6.4, 5.2, 4.0, 2.6], [2.0, 1.8, 1.5, 1.1], 3, 6, tip=True)
    m.chain('edge_glow', G, [(-2.6, 10), (-1.4, 17), (-1.8, 24), (-4.0, 30)], [1.2, 1.1, 1.0], 2.1, 3, 4)
    for k, a in enumerate((-150, -120, -90)):
        import math as _m
        r = _m.radians(a)
        m.seg(f'plume_{k}', SB, (2.6 + _m.cos(r) * 1.0, 6 + _m.sin(r) * 1.0), (2.6 + _m.cos(r) * 7.0 + 4.0, 6 + _m.sin(r) * 7.0 + 1.0),
              2.4, 0.6, 6, 4, tip=True, zc=(k - 1) * 0.9)
    return m


def abyss_storm_bell_staff():
    """風鈴の鈴杖（伝説、2026-11-20 に作り直し）: 杖の先に骨の横木。横木から長さの違う五本の黒い管が紐で下がり、
    真ん中の一本は淵の光。管の口は骨の輪。横木の上に骨の風受け（菱の板）
    ※ 2026-11-20 に Blockbench で手を入れた（横木を十字にし、光る管を表と裏に 1 本ずつ下げ、管をすべて風になびくように傾け、骨の帯を描いた）。正本は tools/models/x32/abyss_storm_bell_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('abyss_storm_bell_staff', SB)
    staff2(m, 46)
    m.box('beam', SB, [-10, 46, -1.4], [10, 48.4, 1.4], grad=(46, 4, 48.4, 6), rim=True)
    m.seg('vane', SB, (0, 48), (0, 55), 5.0, 0.6, 5, 7, tip=True)
    for k, (x, L) in enumerate(((-8, 9), (-4, 12), (0, 15), (4, 12.5), (8, 9.5))):
        m.box(f'cord_{k}', V, [x - 0.3, 43.4, -0.3], [x + 0.3, 46.2, 0.3], 3)
        top = 43.6
        mat = G if k == 2 else V
        m.box(f'tube_{k}' + ('_glow' if k == 2 else ''), mat, [x - 1.1, top - L, -1.1], [x + 1.1, top, 1.1], grad=(top - L, 3, top, 5) if k != 2 else None)
        m.box(f'mouth_{k}', SB, [x - 1.4, top - L - 0.6, -1.4], [x + 1.4, top - L + 0.8, 1.4], 5)
    return m


def bolt_picture(m, name, branches, box, at=0.0, plane='xy', core=1.0):
    """稲妻の絵（板）: 折れ線の枝ごとに、芯は白に近い淵の光、外は濃い淵の光。枝は先へ細る"""
    cv = lib.Canvas(*box)

    def f(x, y, cur):
        for pts, w in branches:
            d, t = lib.near_line((x, y), pts)
            ww = w * (1 - 0.6 * t)
            ww *= 1.5
            if d < ww * 0.45 * core:
                return (G, 5)
            if d < ww:
                return (G, 3) if cur is None or cur[1] < 3 else cur
        return cur
    cv.paint(f)
    cv.put(m, name, at=at, plane=plane)


def fin_membrane(m, name, rays, mirror15, at=0.0):
    """背びれの膜（板の絵）: 骨の軸のあいだを暗い膜で埋め、軸の脇に濃い筋、縁へ向かって明るく、縁にぎざぎざの切れ込み"""
    poly = [rays[0][0]] + [e for _, e in rays] + [rays[-1][0]]
    xs = [p[0] for p in poly]
    ys = [p[1] for p in poly]
    cv = lib.Canvas(min(xs) - 1, min(ys) - 1, max(xs) + 1, max(ys) + 1)
    base = [r for r, _ in rays]

    def f(x, y, cur):
        if not lib.inside((x, y), poly):
            return None
        dmin = min(lib.near_line((x, y), [r, e])[0] for r, e in rays)
        _, tb = lib.near_line((x, y), base)
        db = min(math.hypot(x - r[0], y - r[1]) for r in base)
        de = min(lib.near_line((x, y), [rays[k][1], rays[k + 1][1]])[0] for k in range(len(rays) - 1))
        if de < 1.2 and (int(x * 2) + int(y * 2)) % 3 == 0:
            return None
        if dmin < 0.5:
            return ('fin', 1)
        return ('fin', 2 if de > 3 else (3 if de > 1.5 else 4))
    cv.paint(f)
    cv.put(m, name, at=at, mirror15=mirror15)



def baleen_comb(m, name, arm, mirror15, at=0.0):
    """鯨ひげの櫛（板の絵）: 弓腕の腹から斜め下へ細い板が並んで垂れる。板ごとに濃淡を変え、先は骨の白の毛羽"""
    strands = []
    L = [math.hypot(arm[i + 1][0] - arm[i][0], arm[i + 1][1] - arm[i][1]) for i in range(len(arm) - 1)]
    for k in range(12):
        t = 0.14 + k * 0.066
        d = t * sum(L)
        for i, l in enumerate(L):
            if d <= l:
                f = d / l
                b = (arm[i][0] + (arm[i + 1][0] - arm[i][0]) * f - 1.2, arm[i][1] + (arm[i + 1][1] - arm[i][1]) * f)
                break
            d -= l
        ln = 8 * math.sin(math.pi * (t - 0.1) / 0.85) + 1.5 + (k % 2) * 0.75
        e = (b[0] - 0.36 * ln, b[1] - 0.93 * ln)
        strands.append((b, e, k))
    cv = lib.Canvas(1, 17, 15, 45)

    def f(x, y, cur):
        for b, e, k in strands:
            d, t = lib.near_line((x, y), [b, e])
            if d < 0.55:
                if t > 0.85:
                    return (B, 5 if d < 0.3 else 4)
                return (V, 7 if k % 2 else 5) if d < 0.3 else (V, 4 if k % 2 else 3)
        return cur
    cv.paint(f)
    cv.put(m, name, at=at, mirror15=mirror15)


def shard_halo(m, name, at):
    """黒曜の破片の後光（握りの前に横向き＝ZY の板で立てる）: 中心から鋭い破片が放射に並び、
    破片の片面は明るく片面は暗い切子。中心の割れ目は淵の光"""
    spikes = [(a, (9 if k % 2 == 0 else 6.5)) for k, a in enumerate(range(0, 360, 30))]
    cv = lib.Canvas(-10, 5, 10, 25)

    def f(z, y, cur):
        dz, dy = z, y - 15
        r = math.hypot(dz, dy)
        if r < 1.6:
            return (G, 4)
        a = math.degrees(math.atan2(dy, dz)) % 360
        for ang, ln in spikes:
            da = (a - ang + 180) % 360 - 180
            w = 13 * (1 - r / ln)
            if abs(da) < w and r < ln:
                if r < 2.6:
                    return (G, 2)
                return (V, 7 if da > 0 else 3) if r < ln - 1 else (V, 9)
        return None
    cv.paint(f)
    cv.put(m, name, at=at, plane='zy')



def abyss_storm_bow():
    """雷淵の弓（伝説）: 雷雲の底で鍛えた弓。弓腕は稲妻のように三度鋭く折れ、折れ目ごとに淵の光の核が継ぐ。
    折れ目の背からは骨の雷角が外へ、左右（奥行き）へは骨の風切りの板が張り出す。握りの前に黒い雷雲の塊が湧き、雲から稲妻（板の絵）が後ろへ大きく枝分かれして走り（横向きの板にも一本）、
    その奥に淵の光の目。弦は三本、真ん中の一本が淵の光で光り、外の二本は骨の白。弓の懐には折れた稲妻が走り、
    両端の先には雷雲のかけらが浮かぶ"""
    m = M('abyss_storm_bow')
    m.box('grip', V, [10.25, 9.5, -2], [13.75, 20.5, 2], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'band_{n}', B, [9.75, y, -2.4], [14.25, y + 1.5, 2.4], 4)
    # 握りの前の雷雲
    for k, (x, y, w, h, d) in enumerate(((15.5, 15, 4, 5, 2.4), (17.25, 17.75, 3.5, 3, 2), (17, 12.25, 3.25, 2.75, 1.9), (19, 15.25, 2.5, 3, 1.6))):
        m.box(f'cloud_{k}', V, [x - w / 2, y - h / 2, -d], [x + w / 2, y + h / 2, d], grad=(y - h / 2, 2, y + h / 2, 5))
    for k, (x, y, w, h, d) in enumerate(((21, 15, 3.5, 4.5, 1.8), (20, 19.25, 3, 2.5, 1.5), (20.25, 10.75, 3, 2.5, 1.5))):
        m.box(f'cloud_x{k}', V, [x - w / 2, y - h / 2, -d], [x + w / 2, y + h / 2, d], grad=(y - h / 2, 3, y + h / 2, 6))
    bolt_picture(m, 'bolt_glow', [([(22, 15.5), (26, 18), (24.75, 20.5), (29, 23), (28, 25.5), (32.5, 28.5)], 1.6),
                                  ([(22, 14.5), (25.5, 12), (24.25, 9.75), (28.5, 7), (27.5, 4.5), (31.5, 1.5)], 1.5),
                                  ([(26, 18), (30.5, 17.5), (29.5, 15.5), (34, 14.75)], 1.1),
                                  ([(28.5, 7), (31.75, 9), (34.5, 8)], 0.9)], (21, 0, 36, 30))
    bolt_picture(m, 'bolt_z_glow', [([(-6.5, 25), (-3, 22), (-4.25, 19.5), (0, 17)], 1.2), ([(6.5, 5), (3, 8), (4.25, 10.5), (0, 13)], 1.2)],
                 (-8, 3, 8, 27), at=21.5, plane='zy')
    diamond(m, 'eye_glow', G, (17.25, 15), 2.75, 2.5, 3, 5)
    m.box('eye_pupil', V, [16.9, 14.25, -2.6], [17.6, 15.75, 2.6], 1)
    arm = [(12, 21), (17.5, 26.5), (10.25, 31), (16.75, 37), (8.5, 42.5), (4.75, 47)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        pts = [P(q) for q in arm]
        m.chain(f'arm_{n}', V, pts, [4.25, 4, 3.75, 3.25, 2.75], 1.7, 3, 6, tip=False)
        for k, sz in ((1, 4), (2, 3.5), (3, 3.5), (4, 3)):
            diamond(m, f'joint_{n}{k}_glow', G, pts[k], sz, 1.85, 3, 5)
        for k, (b, e) in enumerate((((17.5, 26.5), (24.5, 29.5)), ((16.75, 37), (23.5, 41.5)))):
            m.seg(f'horn_{n}{k}', B, P(b), P(e), 2.5, 1.2, 3, 5, tip=True)
            m.chain(f'fork_{n}{k}_glow', G, [P(e), P((e[0] + 2.5, e[1] - 1.5)), P((e[0] + 3.5, e[1] + 1)), P((e[0] + 6, e[1] - 0.5))], [0.9, 0.8, 0.7], 0.4, 4, 5, tip=True)
        for zs, zn in ((1, 'f'), (-1, 'b')):
            for k, (x, y) in enumerate(((17.5, 26.5), (16.75, 37))):
                q = P((x, y))
                z0, z1 = (1.5, 4.25) if zs > 0 else (-4.25, -1.5)
                m.box(f'vane_{n}{zn}{k}', B, [q[0] - 1.25, q[1] - 1, z0], [q[0] + 1, q[1] + 1, z1], 3 + k)
                z2 = (4.25, 5.5) if zs > 0 else (-5.5, -4.25)
                m.box(f'vane_tip_{n}{zn}{k}', B, [q[0] - 0.75, q[1] - 0.5, z2[0]], [q[0] + 0.5, q[1] + 0.5, z2[1]], 5)
        m.chain(f'hook_{n}', B, [P((4.75, 47)), P((3.25, 48.5)), P((4, 50))], [1.75, 1.25], 1, 4, 5, tip=True)
        # 懐の稲妻（弓腕の腹から弦の手前まで、折れながら走る）
        m.chain(f'bolt_{n}_glow', G, [P((10, 31)), P((7.75, 29.25)), P((8.75, 27.25)), P((6.5, 25))], [1, 0.9, 0.8], 0.45, 3, 5, tip=True)
        # 先に浮かぶ雷雲のかけら
        for k, (x, y, w, h) in enumerate(((9.5, 52.5, 4.5, 2.25), (13.5, 51.25, 3, 1.75), (7, 55, 2.5, 1.5))):
            q = P((x, y))
            m.box(f'drift_{n}{k}', V, [q[0] - w / 2, q[1] - h / 2, -1.4], [q[0] + w / 2, q[1] + h / 2, 1.4], 3 + k)
        q = P((11.25, 49.25))
        m.seg(f'spark_{n}_glow', G, (q[0] - 0.75, q[1] + sign * 1), (q[0] + 0.75, q[1] - sign * 1), 0.8, 0.4, 4, 5, over=0)
    top, bot = (3.5, 49), (3.5, -19)
    for key, pull in lib.PULLS:
        for zc, zn in ((0, 'c'), (1, 'f'), (-1, 'b')):
            mat = G if zc == 0 else 'string'
            nm = f'string_{key}_{zn}' + ('_glow' if zc == 0 else '')
            if pull == 0:
                m.box(nm, mat, [top[0] - 0.5, bot[1], zc - 0.4], [top[0] + 0.5, top[1], zc + 0.4], 4 if zc == 0 else 3)
            else:
                mid = (top[0] - pull, 15)
                m.seg(nm + '_u', mat, mid, top, 1, 0.4, 3, 3, over=0.2, zc=zc)
                m.seg(nm + '_d', mat, bot, mid, 1, 0.4, 3, 3, over=0.2, zc=zc)
        for k, y in enumerate((38, -8)):
            c = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_clasp{k}', B, [c[0] - 0.9, c[1] - 0.6, -1.6], [c[0] + 0.9, c[1] + 0.6, 1.6], 4)
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
    """黒曜の弓（伝説）: 黒曜石の切子の板を継いだ弓。上の弓腕は途中で割れて、割れたかけらが淵の光の熾火の芯に
    支えられて宙に浮く。握りの前には大きな黒曜の結晶の房、その房を横切って黒曜の破片の後光（横向きの板の絵）が奥行きへ放射に開く。
    下の先は斜めに割れた黒曜の穂。弦は熾火の光の一本で、上下に黒曜の留め玉"""
    m = M('abyss_ember_bow')
    m.box('grip', V, [10.25, 9.5, -2], [13.75, 20.5, 2], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'band_{n}', B, [9.75, y, -2.4], [14.25, y + 1.5, 2.4], 4)
    for k, (a, b, w) in enumerate((((13.5, 15), (24, 16.5), 4.25), ((13.5, 16.5), (21, 24), 3), ((13.5, 13.5), (21.25, 6.5), 3), ((14, 15.5), (19.5, 20.5), 2), ((14, 14), (19.75, 9.5), 2))):
        m.seg(f'crystal_{k}', V, a, b, w, 1.7 - k * 0.15, 4, 8, tip=True)
    for k, (x, y, s) in enumerate(((19, 33, 2.5), (16, 39.5, 2), (20.5, 41, 1.6), (11.5, 50.5, 2), (17.5, -3, 2.5), (14.5, -9.5, 2), (19.5, -11, 1.5))):
        diamond(m, f'float_{k}', V, (x, y), s, 1, 5, 8)
        diamond(m, f'float_{k}_glow', G, (x, y), s * 0.45, 1.1, 4, 5)
    diamond(m, 'crystal_core_glow', G, (16, 15.25), 3, 1.85, 4, 5)
    shard_halo(m, 'shard_halo', 16.5)
    arm = [(12, 21), (13, 27), (12.5, 33), (10.5, 38.5), (7.5, 43), (4.5, 46)]
    pts = arm
    # 上の弓腕: 割れて浮く切子（継ぎ目に光の芯）
    for k in range(len(pts) - 1):
        a, b = pts[k], pts[k + 1]
        sh = 0.55 * k
        dx, dy = (b[0] - a[0]), (b[1] - a[1])
        L0 = math.hypot(dx, dy)
        ux, uy = dx / L0, dy / L0
        a2 = (a[0] + ux * 0.6 + sh * 0.6, a[1] + uy * 0.6)
        b2 = (b[0] - ux * 0.6 + sh * 0.6, b[1] - uy * 0.6)
        m.seg(f'shard_u{k}', V, a2, b2, [3.75, 3.5, 3.25, 2.75, 2.25][k], 1.5, 3 + k * 0.5, 6, over=0.1)
        if k:
            diamond(m, f'ember_u{k}_glow', G, (a[0] + sh * 0.55, a[1]), 1.75, 1.2, 4, 5)
    m.chain('tip_u', V, [(6.7, 46), (5.5, 49.5)], [2], 1.1, 6, 8, tip=True)
    # 下の弓腕: 継いだ切子、腹に光の筋
    low = [mirror(p, -1) for p in pts]
    m.chain('arm_d', V, low, [3.75, 3.5, 3.25, 2.75, 2.25], 1.5, 3, 6, tip=False)
    m.chain('core_d_glow', G, [(x - 1.2, y) for x, y in low[1:5]], [0.8, 0.8, 0.7], 1.55, 3, 5, tip=False)
    for k in (1, 3):
        x, y = low[k]
        m.seg(f'facet_d{k}', V, (x + 1.5, y + 1), (x + 4.25, y - 1.5), 2.25, 1.2, 6, 8, tip=True)
    m.chain('blade_d', V, [(5, -16), (7, -19.5), (4.75, -23)], [2.5, 1.75], 1.2, 5, 8, tip=True)
    top, bot = (5.5, 47), (5.5, -16.5)
    for key, pull in lib.PULLS:
        if pull == 0:
            m.box(f'string_{key}', G, [top[0] - 0.5, bot[1], -0.5], [top[0] + 0.5, top[1], 0.5], 4)
        else:
            mid = (top[0] - pull, 15)
            m.seg(f'string_{key}_u', G, mid, top, 1, 0.5, 4, 4, over=0.2)
            m.seg(f'string_{key}_d', G, bot, mid, 1, 0.5, 4, 4, over=0.2)
        for k, y in enumerate((40, -9)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            diamond(m, f'string_{key}_stud{k}', V, q, 2, 1, 5, 7)
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
    """測鉛の弓（エピック）: 弓腕そのものが、骨の紐でつないだ鉛の錘の列（握りから先へ小さくなる）。
    両端の弦受けから大きな測鉛が下へ吊り下がり、錘の腹の目が淵の光で光る。弦にも小さな錘が二つ"""
    m = M('abyss_depth_bow')
    bow_grip(m)
    beads = [((12.75, 24), 5.4), ((13.5, 29.5), 5.0), ((12.5, 34.75), 4.5), ((10.25, 39.25), 4.0), ((7.25, 43), 3.4)]
    top, bot = (3, 46), (3, -16)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'cord_{n}', B, [P((12, 21))] + [P(c) for c, _ in beads] + [P((4.5, 45.5))], 1.1, 0.6, 3, 4, tip=False)
        for k, (c, w) in enumerate(beads):
            q = P(c)
            m.box(f'bead_{n}{k}', V, [q[0] - w / 2, q[1] - w / 2.6, -w / 2], [q[0] + w / 2, q[1] + w / 2.6, w / 2], 3 + k * 0.4)
            m.box(f'beadc_{n}{k}', V, [q[0] - w / 2 + 0.6, q[1] - w / 2.6 - 0.5, -w / 2 + 0.6],
                  [q[0] + w / 2 - 0.6, q[1] + w / 2.6 + 0.5, w / 2 - 0.6], 5 + k * 0.3)
        m.seg(f'nock_{n}', B, P((6, 46)), P((-0.75, 46)), 2.2, 1.2, 4, 5)
        # 測鉛は弦受けの外の端から下へ吊るす（上も下も重さで下へ）
        hang = P((0, 46))
        y0 = hang[1]
        m.box(f'plumb_line_{n}', B, [-0.4, y0 - 4.5, -0.4], [0.4, y0, 0.4], 3)
        m.box(f'plumb_{n}', V, [-2.2, y0 - 10, -2.2], [2.2, y0 - 4.5, 2.2], grad=(y0 - 10, 2, y0 - 4.5, 5))
        m.box(f'plumb_tip_{n}', V, [-1.2, y0 - 11.5, -1.2], [1.2, y0 - 10, 1.2], 2)
        for zs, zn in ((1, 'f'), (-1, 'b')):
            m.box(f'plumb_eye_{n}{zn}_glow', G, [-0.9, y0 - 8.2, zs * 2.2 - 0.15], [0.9, y0 - 6.4, zs * 2.2 + 0.15], 4)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((37, -7)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_weight{k}', V, [q[0] - 1.1, q[1] - 1.3, -1.1], [q[0] + 1.1, q[1] + 1.3, 1.1], 4)
            m.box(f'string_{key}_weight{k}_glow', G, [q[0] - 0.5, q[1] - 0.5, -1.25], [q[0] + 0.5, q[1] + 0.5, 1.25], 5)
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
