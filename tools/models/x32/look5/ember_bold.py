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


def haft(m, y0, grip_to, y1, tail=True):
    """火の柄: 革巻きの鉄の握り、上に焼き物の帯、その上は鉄の棒。柄頭は火蜥蜴の尾が横へ巻く"""
    m.box('grip', I, [-1.5, y0, -1.5], [1.5, grip_to, 1.5], grad=(y0, 1, grip_to, 3), wrap=(y0, grip_to))
    m.box('collar', K, [-2, grip_to, -2], [2, grip_to + 2, 2], 4)
    if y1 > grip_to + 2:
        m.box('rod', I, [-1.25, grip_to + 2, -1.25], [1.25, y1, 1.25], grad=(grip_to + 2, 3, y1, 6))
    if tail:
        m.chain('tail', I, [(0, y0 + 0.5), (0.75, y0 - 2.5), (2.5, y0 - 4.5), (4.5, y0 - 4.75)],
                [3, 2.5, 2], [1.5, 1.25, 1], 3, 6)


# ---------------------------------------------------------------- 杖・香炉

def ember_staff():
    """熾火の杖: 鉄の棒の先が火箸になり、内へ曲がる二本の先で熾をひとつ挟む"""
    m = M('ember_staff')
    haft(m, -14, 2, 33)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'tong_{n}', I, [(sd * 0.75, 32.5), (sd * 3.5, 36), (sd * 3.5, 41), (sd * 1.75, 43.5)], [2.5, 2, 2], 1, 4, 7, tip=True)
    diamond(m, 'coal', E, (0, 39), 4.5, 1.5, 2, 4)
    return m


def ember_kiln_staff():
    """炉心の杖: 焼き物の台に鉄の籠が立ち、籠の中で熾が燃える。籠の上は鉄の帯の丸い屋根"""
    m = M('ember_kiln_staff')
    haft(m, -14, 2, 32)
    m.box('base', K, [-4.5, 31.5, -3], [4.5, 34, 3], grad=(31.5, 3, 34, 5))
    for k, (x, z) in enumerate(((-3.25, -1.75), (3.25, -1.75), (-3.25, 1.75), (3.25, 1.75))):
        m.box(f'bar_{k}', I, [x - 1, 34, z - 0.75], [x + 1, 43, z + 0.75], grad=(34, 3, 43, 5))
    m.arc('roof', I, (0, 43), 4.25, 180, 0, 4, 2, 2, 4, 6)
    m.box('fire', E, [-2.25, 34.5, -1.5], [2.25, 41.5, 1.5], grad=(34.5, 2, 41.5, 5))
    m.seg('flame', E, (0, 41), (0, 45), 2, 1, 4, 5, tip=True)
    return m


def ember_censer():
    """灰の香炉: 鉄の棒の先に焼き物の鉢。鉢の熾が光り、灰の煙がひと筋のぼる"""
    m = M('ember_censer')
    haft(m, -14, 2, 32)
    m.box('foot', K, [-3, 31.5, -2.5], [3, 33.5, 2.5], 3)
    m.box('bowl', K, [-5.5, 33.5, -3.5], [5.5, 37.5, 3.5], grad=(33.5, 2, 37.5, 5))
    m.box('lip', K, [-6, 37, -3.75], [6, 38.5, 3.75], 5)
    m.box('coals', E, [-4, 38, -2.5], [4, 39.5, 2.5], 3)
    diamond(m, 'flame_a', E, (-1.75, 40.25), 3, 1, 3, 5)                  # 熾から立つ小さな火（煙の筋は棒に見えたので外した）
    diamond(m, 'flame_b', E, (1.5, 41.25), 3.5, 1, 3, 5)
    diamond(m, 'flame_c', E, (0, 43.5), 2.5, 0.75, 4, 5)
    return m


def ember_censer_bell_staff():
    """香炉の鈴杖: 鉄の棒の先が羊飼いの鉤に曲がり、鎖の先に穴の開いた香炉が下がる。穴から熾の光が漏れる"""
    m = M('ember_censer_bell_staff')
    haft(m, -14, 2, 40)
    m.chain('hook', I, [(0, 39.5), (0.5, 44), (2.5, 46.75), (5.5, 47.25), (8, 45.75), (8.75, 43)], [2.5, 2.5, 2.5, 2, 2], 1.25, 4, 7)
    m.box('link_0', I, [7.75, 39.5, -0.5], [9.75, 43, 0.5], 4)
    m.box('link_1', I, [8.25, 37, -1], [9.25, 40, 1], 5)
    m.box('lid', K, [6.5, 35.5, -2.25], [11.5, 37.5, 2.25], 5)
    m.box('pot', K, [5.5, 28.5, -2.75], [12.5, 35.5, 2.75], grad=(28.5, 2, 35.5, 4))
    m.box('pot_foot', K, [7, 27, -2], [11, 28.5, 2], 2)
    for k, x in enumerate((6.75, 9.75)):                                  # 香炉の穴（表と裏）
        m.box(f'vent_f{k}', E, [x, 30.5, -3], [x + 1.5, 33.5, -2.6], 3)
        m.box(f'vent_b{k}', E, [x, 30.5, 2.6], [x + 1.5, 33.5, 3], 3)
    return m


# ---------------------------------------------------------------- 短剣・小刀・斧

def ember_dagger():
    """熾の短剣: 炉の火箸を打ち延ばした刃。背は丸い鉄の棒のまま、+x に薄く打った刃が付く。先は火箸の先のように少し曲がる"""
    m = M('ember_dagger')
    haft(m, -8, 3, 3)
    m.chain('spine', I, [(-0.75, 4.5), (-0.5, 13), (0.25, 20), (1.75, 26)], [2.5, 2.5, 2], 1.1, 3, 6)
    m.chain('edge', I, [(1.25, 5), (1.75, 13), (2.25, 19.5)], [2.5, 2], 0.7, 6, 8.5, tip=True)
    return m


def ember_ash_knife():
    """灰の小刀: 黒曜の欠片を研いだ細い刃。切子の面の縁だけが灰白に光り返す"""
    m = M('ember_ash_knife')
    haft(m, -8, 3, 3)
    m.seg('shard_l', I, (-1, 4.5), (0.75, 28), 3, 1, 0, 2)
    m.seg('shard_r', I, (2.5, 4.5), (1, 24), 3, 0.9, 1, 3)
    m.seg('facet', I, (2.25, 6), (1.25, 22), 2, 1.05, 6, 9, tip=True)
    return m


def ember_war_axe():
    """灰の戦斧: 鉄の頬から +x に三日月の刃、刃先は焼けたまま橙に光る。背に焼き物の重り"""
    m = M('ember_war_axe')
    haft(m, -14, 2, 30)
    m.box('cheek', I, [-1.75, 22, -1.75], [3, 30, 1.75], grad=(22, 3, 30, 5))
    m.arc('blade', I, (3, 26), 5.5, -70, 70, 5, 4, 0.9, 4, 6)
    m.box('web', I, [2.5, 22, -0.85], [7.5, 30, 0.85], grad=(22, 3, 30, 5))              # 頬と刃のあいだの板
    m.arc('edge', E, (3, 26), 8, -60, 60, 5, 2, 0.7, 3, 5)
    m.seg('weight', K, (-1.5, 26), (-5.5, 26.5), 3.5, 1.5, 3, 5)
    return m


# ---------------------------------------------------------------- 大剣

def ember_greatsword():
    """熾火の大剣: 玄武岩の厚い刃（柱状の面）。鍔は焼き物の煉瓦、刃元の継ぎ目に熾火が残る"""
    m = M('ember_greatsword')
    haft(m, -14, 1, 1)
    m.box('guard', K, [-6.5, 3, -2.25], [6.5, 6, 2.25], grad=(3, 3, 6, 5))
    m.box('seam', E, [-4, 5.75, -1.4], [4, 8.25, 1.4], 3)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'blade_{n}', I, (sd * 3, 7), (sd * 1.25, 46), 4.5, 1.2, 2, 5)
        m.seg(f'column_{n}', I, (sd * 4.75, 8), (sd * 2.75, 44), 2, 1.3, 4, 7)
        m.seg(f'tip_{n}', I, (sd * 2.25, 45), (0, 54), 3, 1.1, 4, 7, tip=True)
    return m


def ember_cinder_blade():
    """熾の大刃: 焔のように左右へ揺れる大刃。鍔は炉の口で、口の奥の熾が光る"""
    m = M('ember_cinder_blade')
    haft(m, -14, 1, 1)
    m.box('kiln', K, [-5.5, 2.5, -2.75], [5.5, 9, 2.75], grad=(2.5, 2, 9, 4))
    m.box('kiln_top', K, [-4, 9, -2.25], [4, 10.5, 2.25], 5)
    m.box('mouth_f', E, [-2.5, 3.5, -3.1], [2.5, 7, -2.7], 3)
    m.box('mouth_b', E, [-2.5, 3.5, 2.7], [2.5, 7, 3.1], 3)
    m.chain('blade', I, [(0, 10), (1.75, 18), (-1, 26), (1.5, 34), (-0.75, 42), (0.5, 49), (0, 55)],
            [6.5, 6, 5.5, 5, 4, 3], 1.1, 3, 7.5, tip=True)
    return m


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
    """火の弓の握り: 革巻きの鉄の握り、両端に焼き物の帯"""
    m.box('grip', I, [10.5, 10, -1.75], [13.5, 20, 1.75], grad=(10, 1, 20, 3), wrap=(10, 20))
    m.box('band_u', K, [10, 19.5, -2], [14, 21.5, 2], 4)
    m.box('band_d', K, [10, 8.5, -2], [14, 10.5, 2], 3)


def ember_bow():
    """灰の弓: 余燼の鉄の腕。背（+x）に火蜥蜴の背骨のようなとげが三本ずつ並ぶ。光なし"""
    m = M('ember_bow')
    bow_grip(m)
    arm = [(12, 21), (12.5, 26.5), (12, 32), (10, 38), (7, 42.5), (4.5, 45)]
    spikes = [((12.75, 26.5), (15.25, 28)), ((12.25, 32), (14.75, 34)), ((10.25, 38), (12.75, 40.5))]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'arm_{n}', I, [mirror(p, sign) for p in arm], [3, 3, 2.5, 2.5, 2], 1.25, 3, 6, tip=False)
        for k, (a, b) in enumerate(spikes):
            m.seg(f'spike_{n}{k}', I, mirror(a, sign), mirror(b, sign), 2, 0.75, 4, 7, tip=True)
        m.seg(f'nock_{n}', K, mirror((6, 45.5), sign), mirror((3, 45.5), sign), 2, 1, 4, 5)
    strings(m, (4.5, 45.5), (4.5, -15.5))
    return m


def ember_flare_bow():
    """火の粉の弓: 焼けた枝の腕。節ごとの裂け目から橙の光が漏れ、腕の途中から焦げた小枝が出る"""
    m = M('ember_flare_bow')
    bow_grip(m)
    arm = [(12, 21), (12, 27), (11, 33), (9, 38.5), (6.5, 43), (3.5, 46)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        pts = [mirror(p, sign) for p in arm]
        m.chain(f'arm_{n}', I, pts, [3, 3, 2.5, 2.5, 2], 1.25, 1, 4, tip=False)
        for k in (1, 2, 3):                                               # 節の裂け目
            x, y = pts[k]
            px, py = pts[k + 1]
            ux, uy = (px - x), (py - y)
            L = math.hypot(ux, uy)
            m.seg(f'crack_{n}{k}', E, (x - ux / L * 0.75, y - uy / L * 0.75), (x + ux / L * 0.75, y + uy / L * 0.75),
                  3.25 - 0.25 * k, 1.4, 3, 4, over=0)
        m.chain(f'twig_{n}', I, [mirror(p, sign) for p in ((11.75, 30), (14.5, 33), (15, 36.5))], [2, 2], 0.9, 2, 5, tip=True)
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
