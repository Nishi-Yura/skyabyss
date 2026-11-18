# -*- coding: utf-8 -*-
"""火（余燼）のシリーズ（look5・個性版）。四章の巨神「余燼」（地の中を泳ぐ火蜥蜴）、灰の鍛え手、炉と香炉から一品ずつ形を立てる。
色: 灰をかぶった炭鉄（cinder）の 1 系統、差し色は焼き物の土（clay）、光は熾火の橙（ember）。

形は他のシリーズから借りない（ユーザー: 「形自体はシリーズごとに毎回作り直してね」）。
- 柄: 革を巻いた鉄の握り、上に焼き物の帯、柄頭は火蜥蜴の尾が横へ巻く。
- 弓: 握りの両端に焼き物の帯。灰の弓は背に火蜥蜴の背骨のとげ、火の粉の弓は焼けた枝。
- 光は品ごとに場所を変える（同じ種類の場所は 2 回まで、光なしもある）:
  熾火の杖 = 火箸が挟む熾、炉心の杖 = 籠の中の火、灰の香炉 = 鉢の熾、香炉の鈴杖 = 香炉の穴、戦斧 = 焼けた刃、
  熾火の大剣 = 鍔ぎわの刃元、熾の大刃 = 鍔の炉の口、火の粉の弓 = 枝の裂け目。熾の短剣・灰の小刀・灰の弓は光なし。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'cinder': ['#141012', '#221c1c', '#322a28', '#463b36', '#5c4f48', '#75665c', '#928272', '#b2a290', '#d4c6b4', '#f2ead8'],
    'clay': ['#3e1a10', '#6a2e1a', '#94442a', '#bc6040', '#dc8a62', '#f4b894'],
    'ember': ['#7a1e04', '#c04008', '#f07018', '#ffa040', '#ffd890', '#fff6e0'],
})
I, K, E = 'cinder', 'clay', 'ember'


def M(name):
    return Model(name, accent=K, glowmat=E)


def diamond(m, name, mat, c, s, d, t0, t1, zc=0.0):
    """一辺 s の正方形を 45 度回した菱形"""
    h = s / (2 * math.sqrt(2))
    m.seg(name, mat, (c[0] - h, c[1] - h), (c[0] + h, c[1] + h), s, d, t0, t1, over=0, zc=zc)


def haft(m, y0, grip_to, y1, tail=True, bands=()):
    """火の柄: 革巻きの鉄の握り、上に焼き物の帯と鋲。その上は鉄の棒で、bands の高さに鉄の輪。
    柄頭は火蜥蜴の尾が横へ巻き、尾の背に小さなとげが二つ"""
    m.box('grip', I, [-1.5, y0, -1.5], [1.5, grip_to, 1.5], grad=(y0, 1, grip_to, 3), wrap=(y0, grip_to))
    m.box('collar', K, [-2.25, grip_to, -2.25], [2.25, grip_to + 2.5, 2.25], grad=(grip_to, 3, grip_to + 2.5, 5))
    m.box('rivet_f', I, [-0.75, grip_to + 0.5, -2.75], [0.75, grip_to + 2, -2.25], 7)
    m.box('rivet_b', I, [-0.75, grip_to + 0.5, 2.25], [0.75, grip_to + 2, 2.75], 7)
    if y1 > grip_to + 2.5:
        m.box('rod', I, [-1.25, grip_to + 2.5, -1.25], [1.25, y1, 1.25], grad=(grip_to + 2.5, 3, y1, 6))
        for k, y in enumerate(bands):
            m.box(f'band_{k}', I, [-1.75, y, -1.75], [1.75, y + 2, 1.75], grad=(y, 2, y + 2, 4))
    if tail:
        m.chain('tail', I, [(0, y0 + 0.5), (0.75, y0 - 2.5), (2.5, y0 - 4.75), (5, y0 - 5.25), (6.5, y0 - 4)],
                [3, 2.75, 2.25, 2], [1.5, 1.25, 1.1, 1], 3, 6.5)
        m.seg('tail_spine_0', K, (-0.75, y0 - 1.5), (-2.75, y0 - 2.5), 2, 0.75, 4, 5, tip=True)
        m.seg('tail_spine_1', K, (1.25, y0 - 4.5), (0.5, y0 - 6.5), 2, 0.75, 4, 5, tip=True)


def lift(m, dy):
    """模型ごと上下に動かす。原点を握りの真ん中に合わせるため（docs/design/60 の 1.2。2026-11-18「持ち手がおかしい」）"""
    for p in m.parts:
        for k in ('frm', 'to', 'origin'):
            p[k][1] += dy
        if p['grad']:
            g = p['grad']
            p['grad'] = (g[0] + dy, g[1], g[2] + dy, g[3])
        if p['wrap']:
            p['wrap'] = (p['wrap'][0] + dy, p['wrap'][1] + dy)
    return m


# ---------------------------------------------------------------- 杖・香炉

def ember_staff():
    """熾火の杖: 鉄の棒の先が二組の火箸に割れ、内へ曲がる先で大きな熾を挟む。熾のまわりに火の粉が三つ"""
    m = M('ember_staff')
    haft(m, -14, 2, 32, bands=(12, 22))
    m.box('hinge', K, [-2.5, 31, -2.25], [2.5, 34, 2.25], grad=(31, 3, 34, 5))
    m.box('hinge_pin', I, [-3.25, 31.75, -0.75], [3.25, 33.25, 0.75], 6)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'tong_{n}', I, [(sd * 1.25, 33.5), (sd * 5, 37), (sd * 5.5, 42), (sd * 3.5, 46), (sd * 1.5, 47)],
                [2.5, 2.5, 2, 2], 1.1, 4, 7.5, tip=True)
        m.chain(f'claw_{n}', I, [(sd * 1, 34), (sd * 3, 36.5), (sd * 3.25, 39)], [2, 2], 0.8, 5, 7, tip=True, zc=-0.9 * sd)
    diamond(m, 'coal', E, (0, 41), 6, 1.75, 2, 4)
    diamond(m, 'coal_core', E, (0, 41), 3, 2, 4, 5)
    for k, (x, y) in enumerate(((-4.5, 49), (3, 50.5), (-0.5, 53))):
        diamond(m, f'spark_{k}', E, (x, y), 2, 0.6, 4, 5)
    return m


def ember_kiln_staff():
    """炉心の杖: 焼き物の台に四本の鉄の柱の籠、柱は上で丸い屋根に合わさり、てっぺんに尖り。
    籠の中で熾が燃え、炎の舌が二枚。台の下に鉄の鉤爪が二つ"""
    m = M('ember_kiln_staff')
    haft(m, -14, 2, 31, bands=(13, 23))
    m.box('base', K, [-5, 31, -3.25], [5, 34, 3.25], grad=(31, 2, 34, 5))
    m.box('base_rim', K, [-5.5, 33.5, -3.5], [5.5, 34.5, 3.5], 5)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'talon_{n}', I, (sd * 3.5, 31.5), (sd * 6, 28.5), 2, 1, 4, 6, tip=True)
    for k, (x, z) in enumerate(((-3.75, -2), (3.75, -2), (-3.75, 2), (3.75, 2))):
        m.box(f'bar_{k}', I, [x - 1, 34.5, z - 0.75], [x + 1, 44, z + 0.75], grad=(34.5, 3, 44, 5))
    m.box('girdle', I, [-4.75, 38.5, -2.9], [4.75, 40.5, 2.9], 4)
    m.arc('roof', I, (0, 43.5), 4.5, 180, 0, 5, 2, 2.5, 4, 6)
    m.seg('finial', I, (0, 47.5), (0, 52), 2, 1, 5, 8, tip=True)
    m.box('fire', E, [-2.5, 34.5, -1.75], [2.5, 40, 1.75], grad=(34.5, 1, 40, 4))
    m.seg('flame_a', E, (-0.75, 39.5), (-1.25, 44), 2.5, 1, 3, 5, tip=True)
    m.seg('flame_b', E, (1, 39.5), (1.5, 43), 2, 0.9, 3, 5, tip=True)
    return m


def ember_censer():
    """灰の香炉: 鉄の棒の先に三本足の焼き物の鉢。鉢には左右の耳、縁に刻み。熾の山から小さな炎が立つ"""
    m = M('ember_censer')
    haft(m, -14, 2, 31, bands=(13, 23))
    m.box('cup', I, [-2.5, 30.5, -2], [2.5, 32.5, 2], 4)
    for sd, n in ((-1, 'l'), (0, 'm'), (1, 'r')):
        m.seg(f'leg_{n}', K, (sd * 4, 34), (sd * 2.5, 31), 2, 1, 3, 4)
    m.box('bowl_low', K, [-4.5, 33, -3], [4.5, 35, 3], grad=(33, 2, 35, 3))
    m.box('bowl', K, [-6, 35, -3.75], [6, 38.5, 3.75], grad=(35, 3, 38.5, 5))
    m.box('lip', K, [-6.75, 38, -4.25], [6.75, 39.5, 4.25], 5)
    for k, x in enumerate((-3.5, 0, 3.5)):
        m.box(f'notch_{k}', I, [x - 1, 38.25, -4.5], [x + 1, 39.75, 4.5], 2)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'ear_{n}', I, [(sd * 6, 37.5), (sd * 8.5, 37.5), (sd * 8.5, 35), (sd * 6.5, 35)], 2, 0.75, 4, 6)
    m.box('coals', E, [-4.5, 39, -3], [4.5, 40.5, 3], 2)
    diamond(m, 'flame_a', E, (-2.25, 41.75), 3, 1, 3, 5)
    diamond(m, 'flame_b', E, (1.75, 42.75), 3.5, 1, 3, 5)
    diamond(m, 'flame_c', E, (-0.25, 45.25), 2.5, 0.75, 4, 5)
    return m


def ember_censer_bell_staff():
    """香炉の鈴杖: 鉄の棒の先が羊飼いの鉤に曲がり、鉤の先は火蜥蜴の頭（目が光る）。二つの輪の鎖の先に、
    段のある蓋と尖りの付いた香炉が下がる。胴の穴から熾の光が漏れる"""
    m = M('ember_censer_bell_staff')
    haft(m, -14, 2, 40, bands=(14, 26))
    m.chain('hook', I, [(0, 39.5), (0.5, 44), (2.5, 47), (5.5, 47.75), (8.25, 46.25), (9, 43.5)], [2.5, 2.5, 2.5, 2.25, 2], 1.25, 4, 7)
    m.box('head', I, [7.75, 41.5, -1.5], [10.25, 44, 1.5], grad=(41.5, 4, 44, 6))
    m.box('eye_f', E, [8.75, 42.5, -1.9], [10, 43.75, -1.5], 5)
    m.box('eye_b', E, [8.75, 42.5, 1.5], [10, 43.75, 1.9], 5)
    m.box('link_0', I, [8.25, 37.75, -0.5], [9.75, 41.5, 0.5], 4)
    m.box('link_1', I, [8.5, 35, -1], [9.5, 38.5, 1], 5)
    m.box('finial', K, [8, 33.5, -1], [10, 35.5, 1], 5)
    m.box('lid_top', K, [7, 32, -1.75], [11, 34, 1.75], 5)
    m.box('lid', K, [6, 30.5, -2.5], [12, 32.5, 2.5], grad=(30.5, 3, 32.5, 5))
    m.box('pot', K, [5.25, 23.5, -3], [12.75, 30.5, 3], grad=(23.5, 2, 30.5, 4))
    m.box('pot_band', I, [5, 26.5, -3.25], [13, 27.5, 3.25], 3)
    m.box('pot_foot', K, [6.75, 22, -2.25], [11.25, 23.5, 2.25], 2)
    for k, (x, y) in enumerate(((6.25, 28), (9.25, 28), (7.75, 24))):
        m.box(f'vent_f{k}', E, [x, y, -3.4], [x + 2, y + 2, -3], 3)
        m.box(f'vent_b{k}', E, [x, y, 3], [x + 2, y + 2, 3.4], 3)
    return m


# ---------------------------------------------------------------- 短剣・小刀・斧

def ember_dagger():
    """熾の短剣: 火箸を打ち延ばした刃。背は丸い鉄の棒、+x に薄い刃、背に鋲が三つ。鍔は焼き物で、両端が尾のように巻く"""
    m = M('ember_dagger')
    haft(m, -8, 3, 3)
    m.box('guard', K, [-3, 5.5, -1.75], [3.5, 7.5, 1.75], grad=(5.5, 3, 7.5, 5))
    m.chain('quillon_l', K, [(-3, 6.5), (-5, 5.5), (-5.75, 3.5)], 2, 0.9, 4, 6, tip=True)
    m.chain('quillon_r', K, [(3.5, 6.5), (5.5, 7.5), (6.25, 9.5)], 2, 0.9, 4, 6, tip=True)
    # 刃は背（-x）の側へ反る。刃（+x）が外側のふくらみに来る（2026-11-18「剣の向きがおかしい」: 前は刃の側へ鉤のように反っていた）
    m.chain('spine', I, [(-0.75, 7), (-1, 15), (-1.75, 22), (-3.25, 28), (-5, 31)], [2.5, 2.5, 2, 2], 1.1, 3, 6.5, tip=True)
    m.chain('edge', I, [(1.5, 7.5), (1, 15), (0.5, 21.5), (-0.25, 26)], [3, 2.5, 2], 0.7, 6, 8.5, tip=True)
    for k, (x, y) in enumerate(((-1.75, 10), (-2.25, 15.5), (-2.75, 21))):
        m.box(f'stud_{k}', K, [x, y, -1.4], [x + 1.75, y + 2, 1.4], 5)
    return lift(m, 2.5)


def ember_ash_knife():
    """灰の小刀: 黒曜の欠片を研いだ刃。三枚の切子の面が灰白に光り返し、根元に小さな黒曜の欠片が二つ噛む"""
    m = M('ember_ash_knife')
    haft(m, -8, 3, 3)
    m.box('socket', I, [-2.25, 5.5, -1.75], [2.75, 7.5, 1.75], grad=(5.5, 3, 7.5, 5))
    m.seg('shard_l', I, (-1.25, 6.5), (0.75, 31), 3.5, 1, 0, 2)
    m.seg('shard_r', I, (2.75, 6.5), (1, 26), 3.5, 0.9, 1, 3)
    m.seg('facet_a', I, (2.5, 8), (1.25, 24), 2, 1.05, 6, 9, tip=True)
    m.seg('facet_b', I, (-0.75, 18), (0.5, 29), 2, 1.05, 5, 8, tip=True)
    m.seg('facet_c', I, (-1, 8.5), (-0.5, 15), 2, 1.05, 4, 6)
    m.seg('chip_l', I, (-1.5, 7), (-4, 10.5), 2, 0.8, 1, 4, tip=True)
    m.seg('chip_r', I, (3, 7), (5, 9.5), 2, 0.8, 1, 4, tip=True)
    m.seg('chip_l2', I, (-1.25, 12.5), (-3.5, 15.5), 2, 0.8, 1, 4, tip=True)   # 刃の途中の欠け（Blockbench で足した）
    m.seg('chip_r2', I, (2.5, 11.5), (4.5, 14), 2, 0.8, 1, 4, tip=True)
    return lift(m, 2.5)


def ember_war_axe():
    """灰の戦斧: 鉄の頬から +x に大きな三日月の刃、刃先は焼けたまま橙に光る。背は焼き物の重りと鉄のとげ、柄に添え金が下へ伸びる"""
    m = M('ember_war_axe')
    haft(m, -14, 2, 32, bands=(11,))
    m.box('cheek', I, [-2, 21.5, -1.9], [3.5, 31.5, 1.9], grad=(21.5, 3, 31.5, 5))
    for sd, n in ((-1, 'f'), (1, 'b')):
        m.box(f'langet_{n}', I, [-1, 15, sd * 1.6 - 0.4], [1, 22, sd * 1.6 + 0.4], 4)
        m.box(f'rivet_{n}', K, [0, 25.5, sd * 2.2 - 0.3], [2, 27.5, sd * 2.2 + 0.3], 6)
    m.box('web', I, [3, 21.5, -0.9], [8.5, 31.5, 0.9], grad=(21.5, 3, 31.5, 5))
    m.arc('blade', I, (3.5, 26.5), 6.5, -70, 70, 6, 4.5, 0.95, 4, 6)
    m.arc('edge', E, (3.5, 26.5), 9.25, -62, 62, 6, 2, 0.75, 3, 5)
    m.seg('weight', K, (-1.75, 26.5), (-5.5, 26.5), 5, 1.75, 3, 5)
    m.seg('spike', I, (-5.5, 26.5), (-9.5, 25.5), 2.5, 1, 4, 7, tip=True)
    return m


# ---------------------------------------------------------------- 大剣

def ember_greatsword():
    """熾火の大剣: 玄武岩の厚い刃（柱の面を左右に三本ずつ）。鍔は鋲を打った焼き物の煉瓦、刃元の継ぎ目に熾火が残る"""
    m = M('ember_greatsword')
    haft(m, -14, 1, 1)
    m.box('guard', K, [-7.5, 3.5, -2.5], [7.5, 6.5, 2.5], grad=(3.5, 3, 6.5, 5))
    m.box('guard_top', K, [-5.5, 6.5, -2.25], [5.5, 7.5, 2.25], 5)
    for k, x in enumerate((-5.5, 5.5)):
        m.box(f'stud_f{k}', I, [x - 1, 4, -2.9], [x + 1, 6, -2.5], 6)
        m.box(f'stud_b{k}', I, [x - 1, 4, 2.5], [x + 1, 6, 2.9], 6)
    m.box('seam', E, [-4.5, 7.25, -1.5], [4.5, 9.25, 1.5], 3)
    # 左右の刃は真ん中で重ねる（2026-11-18「刃に間があるのおかしくない？」: 前は刃元に隙間があった）。右を少し薄くして面が重ならないように
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'blade_{n}', I, (sd * 2.5, 8.5), (sd * 0.5, 47), 5, 1.25 if sd < 0 else 1.15, 2, 5)
        m.seg(f'column_{n}', I, (sd * 4.25, 9.5), (sd * 2.25, 45), 2, 1.35, 4, 7)
        m.seg(f'facet_{n}', I, (sd * 1.5, 10), (sd * 0.75, 40), 2, 1.3, 1, 3)
        m.seg(f'tip_{n}', I, (sd * 2.75, 46), (0, 55), 3.5, 1.15, 4, 7, tip=True)
    return lift(m, 6.5)


def ember_cinder_blade():
    """熾の大刃: 焔のように左右へ揺れる大刃（細かい節でなめらかに）。鍔は炉の口で口の奥の熾が光り、炉の上に煙突が二本"""
    m = M('ember_cinder_blade')
    haft(m, -14, 1, 1)
    m.box('kiln', K, [-6, 2.5, -3], [6, 9.5, 3], grad=(2.5, 2, 9.5, 4))
    m.box('kiln_top', K, [-4.5, 9.5, -2.5], [4.5, 11, 2.5], 5)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'chimney_{n}', K, [sd * 5 - 1, 9.5, -1], [sd * 5 + 1, 12.5, 1], grad=(9.5, 3, 12.5, 5))
    m.box('mouth_f', E, [-3, 3.5, -3.4], [3, 7.5, -3], grad=(3.5, 1, 7.5, 4))
    m.box('mouth_b', E, [-3, 3.5, 3], [3, 7.5, 3.4], grad=(3.5, 1, 7.5, 4))
    pts = [(0, 10.5)]
    for k in range(1, 12):
        pts.append((math.sin(k * 0.95) * 2.25 * (1 - k / 14), 10.5 + k * 4))
    widths = [7 - 0.4 * k for k in range(11)]
    m.chain('blade', I, pts, widths, 1.1, 3, 7.5, tip=True)
    return lift(m, 6.5)


# ---------------------------------------------------------------- 弓（握りの中心 (12, 15) だけを残し、形は火で立て直す）

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
    """火の弓の握り: 革巻きの鉄の握り、両端に鋲を打った焼き物の帯"""
    m.box('grip', I, [10.5, 10, -1.75], [13.5, 20, 1.75], grad=(10, 1, 20, 3), wrap=(10, 20))
    for n, y in (('u', 19.5), ('d', 8.5)):
        m.box(f'band_{n}', K, [10, y, -2.25], [14, y + 2, 2.25], 4)
        m.box(f'band_{n}_rivet', I, [11, y, -2.6], [13, y + 2, 2.6], 6)


def ember_bow():
    """灰の弓: 余燼の鉄の腕（太め）。背（+x）に火蜥蜴の背骨のとげが四本ずつ、先へ短く。腕の付け根に焼き物の鱗、弓の先は焼き物の弦受け"""
    m = M('ember_bow')
    bow_grip(m)
    arm = [(12, 21), (12.75, 26.5), (12.25, 32), (10.25, 38), (7.25, 42.5), (4.5, 45)]
    spikes = [((13.25, 25), (16.25, 26.5), 2.5), ((13, 29.75), (15.75, 31.75), 2.25),
              ((12, 34.5), (14.5, 37), 2), ((10, 38.75), (12, 41.25), 2)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'arm_{n}', I, [mirror(p, sign) for p in arm], [3.5, 3.25, 3, 2.75, 2.25], 1.4, 3, 6, tip=False)
        for k, (a, b, w) in enumerate(spikes):
            m.seg(f'spike_{n}{k}', I, mirror(a, sign), mirror(b, sign), w, 0.8, 4, 7.5, tip=True)
        m.seg(f'scale_{n}', K, mirror((12.25, 22.5), sign), mirror((12.5, 27.5), sign), 2.5, 1.6, 3, 5)
        m.seg(f'nock_{n}', K, mirror((6.5, 45.5), sign), mirror((3, 45.5), sign), 2.5, 1.1, 4, 5)
    strings(m, (4.5, 45.5), (4.5, -15.5))
    return m


def ember_flare_bow():
    """火の粉の弓: 焼けた枝の腕（表に焦げた樹皮の板）。節ごとの裂け目から橙の光が漏れ、腕の途中から焦げた小枝が二本出る"""
    m = M('ember_flare_bow')
    bow_grip(m)
    arm = [(12, 21), (12, 27), (11, 33), (9, 38.5), (6.5, 43), (3.5, 46)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        pts = [mirror(p, sign) for p in arm]
        m.chain(f'arm_{n}', I, pts, [3.25, 3, 2.75, 2.5, 2.25], 1.3, 1, 4, tip=False)
        for k in (1, 2, 3, 4):
            x, y = pts[k]
            px, py = pts[k + 1]
            ux, uy = (px - x), (py - y)
            L = math.hypot(ux, uy)
            m.seg(f'crack_{n}{k}', E, (x - ux / L * 0.75, y - uy / L * 0.75), (x + ux / L * 0.75, y + uy / L * 0.75),
                  3.5 - 0.25 * k, 1.45, 3, 4, over=0)
        for k in (0, 2):
            ax, ay = pts[k]
            bx, by = pts[k + 1]
            m.seg(f'bark_{n}{k}', I, (ax + (bx - ax) * 0.2, ay + (by - ay) * 0.2), (ax + (bx - ax) * 0.8, ay + (by - ay) * 0.8),
                  2, 0.4, 0, 1, zc=-1.5)
        m.chain(f'twig_{n}0', I, [mirror(p, sign) for p in ((11.75, 30), (14.5, 33), (15, 36.5))], [2, 2], 0.9, 2, 5, tip=True)
        m.chain(f'twig_{n}1', I, [mirror(p, sign) for p in ((9.5, 37.5), (12, 40), (11.5, 42.5))], [2, 2], 0.8, 2, 5, tip=True)
        m.seg(f'nock_{n}', K, mirror((5, 46.5), sign), mirror((2, 46.5), sign), 2, 1, 4, 5)
    strings(m, (3, 46.5), (3, -16.5))
    return m


ALL = [ember_staff, ember_kiln_staff, ember_censer, ember_censer_bell_staff, ember_dagger, ember_ash_knife,
       ember_war_axe, ember_greatsword, ember_cinder_blade, ember_bow, ember_flare_bow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
