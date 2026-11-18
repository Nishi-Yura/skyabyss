# -*- coding: utf-8 -*-
"""野・牧草地（look5・個性版）。はじめの草原で拾う 7 品（稀）。
色: 日に焼けた樫と古い鉄（ash 灰茶の 1 系統）、差し色は麦わら（straw）、光は野の蛍（firefly 黄緑）を品ごとに一か所だけ。
村の道具を武器にしたものが多いので、柄は麻紐巻き・鋲・木目の筋で「使い込んだ道具」に見せる。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'ash': ['#1a1510', '#2a221a', '#3a3024', '#4c3f30', '#60513e', '#76664e', '#8e7e64', '#a8987c', '#c4b698', '#e0d6bc'],
    'straw': ['#4a3a14', '#7a6020', '#a88a30', '#ccae48', '#e6cc6e', '#faeaa4'],
    'firefly': ['#3a5a0a', '#5e8a12', '#8cbc22', '#bce048', '#e0f88a', '#f8ffd8'],
})
A, W, F = 'ash', 'straw', 'firefly'


def M(name):
    return Model(name, accent=W, glowmat=F)


def twine_grip(m, y0, y1, r=1.5):
    """麻紐を巻いた木の握り。上下に鉄の口金"""
    m.box('grip', A, [-r, y0, -r], [r, y1, r], grad=(y0, 3, y1, 5), wrap=(y0, y1))
    m.box('ferrule_d', A, [-r - 0.4, y0 - 1.25, -r - 0.4], [r + 0.4, y0, r + 0.4], 7)
    m.box('ferrule_u', A, [-r - 0.4, y1, -r - 0.4], [r + 0.4, y1 + 1.25, r + 0.4], 7)


# ---------------------------------------------------------------- 古戦の旗槍

def wild_banner_spear():
    """古戦の旗槍: 古い戦の槍。長い木の柄の上に鉄の細い穂、穂の下の横木から麦わら色の破れた旗が -x へ下がる。
    旗の端に縫い付けた小さな蛍の飾りが光る"""
    m = M('wild_banner_spear')
    twine_grip(m, -16, 0)
    m.seg('butt', A, (0, -17.25), (0, -20), 2.5, 1.2, 6, 8, tip=True)
    m.box('shaft', A, [-1.2, 1.25, -1.2], [1.2, 38, 1.2], grad=(1.25, 4, 38, 6))
    m.box('crossbar', A, [-9, 35, -0.9], [1.5, 36.5, 0.9], grad=(35, 5, 36.5, 7))
    m.box('crossbar_cap', A, [-9.75, 34.5, -1.1], [-8.75, 37, 1.1], 7)
    # 旗: 三枚の帯を少しずつずらし、下の縁は破れて段になる
    for k, (x0, x1, yb) in enumerate(((-8.75, -5.5, 22), (-5.5, -2.5, 25.5), (-2.5, -1.2, 28))):
        m.box(f'banner_{k}', W, [x0, yb, -0.35], [x1, 35, 0.35], grad=(yb, 2, 35, 4))
    m.box('banner_tear_0', W, [-8.75, 19.5, -0.35], [-7.25, 22, 0.35], 2)
    m.box('banner_tear_1', W, [-4.5, 23.5, -0.35], [-3.25, 25.5, 0.35], 2)
    m.box('banner_stripe', A, [-8.75, 30, -0.45], [-1.2, 31.25, 0.45], 4)
    m.box('charm_glow', F, [-9.25, 17.75, -0.6], [-7.75, 19.25, 0.6], 4)
    m.box('socket', A, [-1.6, 38, -1.6], [1.6, 41, 1.6], grad=(38, 5, 41, 7))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'head_{n}', A, (sd * 1.75, 41), (0, 51), 2.5, 0.9, 6, 8)
    m.seg('head_rib', A, (0, 41.5), (0, 49.5), 1, 1.05, 7, 9)
    m.seg('head_tip', A, (0, 50), (0, 52.5), 1.5, 0.7, 8, 9, tip=True)
    return m


# ---------------------------------------------------------------- 鳥追いの弓

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


def wild_crow_bow():
    """鳥追いの弓: 畑の鳥を追う素朴な木の弓。弓腕はまっすぐ細く、上の先に黒い羽根が二枚結ばれて揺れる。
    握りは麦わらの紐巻き。光はない素朴な弓（アンコモン）"""
    m = M('wild_crow_bow')
    m.box('grip', W, [10.5, 9.5, -1.5], [13.5, 20.5, 1.5], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    arm = [(12, 20), (11, 28), (9, 36), (6.5, 42.5), (4.5, 46)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'arm_{n}', A, [mirror(p, sign) for p in arm], [2.75, 2.5, 2.25, 2], 1.25, 4, 7, tip=False)
        m.seg(f'nock_{n}', A, mirror((5, 46), sign), mirror((2, 46), sign), 2, 1, 6, 7)
    m.box('knot', W, [3.5, 42.5, -1.1], [5.5, 44, 1.1], 4)
    m.chain('feather_0', A, [(4.5, 43), (7, 39.5), (8, 35.5)], [2.25, 1.5], 0.35, 1, 2, tip=True)
    m.chain('feather_1', A, [(4.5, 43), (5.75, 38.5), (5.75, 34.5)], [2, 1.25], 0.35, 1, 2, tip=True, zc=0.5)
    strings(m, (2.5, 46), (2.5, -16))
    return m


# ---------------------------------------------------------------- 衛士の払い剣

def wild_guard_sword():
    """衛士の払い剣: 村の衛士の幅広の剣。真っ直ぐな刃の根元に、払いを受け止める鉄の鉤（-x へ曲がる）。
    鍔は麦わら色の革を巻いた横木、柄頭は平たい円盤。刃の樋に蛍の光の細い筋"""
    m = M('wild_guard_sword')
    twine_grip(m, -6, 6)
    m.box('pommel', A, [-2.5, -9, -1], [2.5, -7, 1], grad=(-9, 5, -7, 7))
    m.box('guard', A, [-5.5, 7.25, -1.6], [5.5, 9.25, 1.6], grad=(7.25, 5, 9.25, 7))
    m.box('guard_wrap', W, [-3, 7, -1.8], [3, 9.5, 1.8], 3)
    m.chain('catch', A, [(-5, 9), (-6.5, 11.5), (-5.5, 13.5)], [1.75, 1.5], 0.8, 6, 8, tip=True)
    m.box('blade', A, [-2.5, 9.25, -0.8], [2.5, 36, 0.8], grad=(9.25, 6, 36, 9))
    m.box('fuller_glow', F, [-0.4, 11, -0.9], [0.4, 30, 0.9], grad=(11, 1, 30, 3))
    for k, w in enumerate((2, 1.25, 0.5)):
        m.box(f'tip_{k}', A, [-w, 36 + k * 1.5, -0.75 + k * 0.1], [w, 37.5 + k * 1.5, 0.75 - k * 0.1], 8 + k * 0.5)
    return m


# ---------------------------------------------------------------- 石切りの鎚

def wild_quarry_hammer():
    """石切りの鎚: 石工の鎚。片側（+x）は平たい打つ面、反対は細い鑿の刃。鉄の頭の真ん中に石の粉が詰まった割れ目、
    柄は太い樫に麦わらの紐。打つ面の芯に蛍の光（石の目を見る灯り）"""
    m = M('wild_quarry_hammer')
    twine_grip(m, -6, 6, 1.6)
    m.box('shaft', A, [-1.3, 7.25, -1.3], [1.3, 21, 1.3], grad=(7.25, 4, 21, 6))
    m.box('wedge_band', W, [-1.7, 16, -1.7], [1.7, 17.5, 1.7], 3)
    m.box('head', A, [-4, 20, -2.75], [4.5, 26, 2.75], grad=(20, 5, 26, 8))
    m.box('crack', A, [-0.4, 20.5, -2.9], [0.4, 25.5, 2.9], 2)
    m.box('face', A, [4.5, 19.5, -3.1], [6.5, 26.5, 3.1], grad=(19.5, 6, 26.5, 8))
    m.box('face_glow', F, [6.5, 22, -1], [6.9, 24, 1], 4)
    m.seg('chisel', A, (-4, 23), (-9.5, 23), 4.5, 1.6, 7, 9, tip=True)
    m.box('chisel_edge', W, [-9.25, 21.25, -0.4], [-8.25, 24.75, 0.4], 4)
    return m


# ---------------------------------------------------------------- 鼠捕りの小刀

def wild_rat_knife():
    """鼠捕りの小刀: 罠師の短い片刃。+x の刃は鋸の歯、背はまっすぐ。柄は骨のように白い麦わらを巻き、
    柄頭に鼠の尾のような細い紐の輪が下がる。刃先の一点に蛍の光"""
    m = M('wild_rat_knife')
    twine_grip(m, -4, 4, 1.4)
    m.chain('tail', W, [(0, -5.25), (1.5, -7.5), (0.5, -9.5), (-1, -8.5)], [0.8, 0.8, 0.8], 0.4, 3, 4, tip=False)
    m.box('bolster', A, [-2, 5.25, -1.3], [2.5, 6.75, 1.3], 7)
    m.box('blade', A, [-1.5, 6.75, -0.6], [2, 18, 0.6], grad=(6.75, 6, 18, 9))
    for k in range(4):
        y = 8 + k * 2.5
        m.seg(f'tooth_{k}', A, (2, y), (3.25, y + 1), 1.25, 0.5, 8, 9, tip=True)
    m.seg('tip', A, (2, 18), (-1.5, 22), 2.5, 0.55, 8, 9, tip=True)
    m.box('tip_glow', F, [-0.75, 20.25, -0.65], [0.25, 21.25, 0.65], 4)
    return m


# ---------------------------------------------------------------- 案山子の鳴子杖

def wild_scarecrow_bell_staff():
    """案山子の鳴子杖: 案山子の十字の木組み。縦の杖の上に横木、横木の両端から鳴子（竹筒の束の代わりに木の板）が紐で下がる。
    てっぺんに麦わら帽の形、帽子の下の空洞（顔）に蛍の目が二つ"""
    m = M('wild_scarecrow_bell_staff')
    twine_grip(m, -14, 2)
    m.box('shaft', A, [-1.2, 3.25, -1.2], [1.2, 33, 1.2], grad=(3.25, 4, 33, 6))
    m.box('arm', A, [-9, 26, -1], [9, 28, 1], grad=(26, 4, 28, 6))
    m.box('straw_wrap', W, [-1.8, 25, -1.8], [1.8, 29, 1.8], grad=(25, 3, 29, 5))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'cord_{n}', W, [sd * 8 - 0.3, 20.5, -0.3], [sd * 8 + 0.3, 26, 0.3], 3)
        for k, dx in enumerate((-1.25, 0, 1.25)):
            y0 = 16.5 - abs(dx) * 0.8
            m.box(f'clapper_{n}{k}', A, [sd * 8 + dx - 0.5, y0, -0.9], [sd * 8 + dx + 0.5, y0 + 4.5, 0.9], grad=(y0, 5, y0 + 4.5, 7))
        m.box(f'clapper_bar_{n}', A, [sd * 8 - 2, 20.5, -1.1], [sd * 8 + 2, 21.5, 1.1], 6)
    m.box('head', W, [-2.5, 33, -2.5], [2.5, 37.5, 2.5], grad=(33, 2, 37.5, 4))
    m.box('hat_brim', W, [-5, 37.5, -4], [5, 38.75, 4], grad=(37.5, 3, 38.75, 5))
    m.box('hat_crown', W, [-2.75, 38.75, -2.25], [2.75, 42, 2.25], grad=(38.75, 3, 42, 5))
    m.box('hat_band', A, [-2.9, 38.75, -2.4], [2.9, 39.75, 2.4], 4)
    for k, x in enumerate((-1.25, 1.25)):
        m.box(f'eye_glow_{k}', F, [x - 0.5, 35, -2.6], [x + 0.5, 36, -2.4], 4)
    return m


# ---------------------------------------------------------------- 写本師の羽杖

def wild_scribe_staff():
    """写本師の羽杖: 細い木の杖の先に、大きな羽根ペンが斜めに挿さる（羽軸は麦わら色、羽は灰茶）。
    杖の上に小さなインク壺が括られ、壺の口から蛍の光の滴"""
    m = M('wild_scribe_staff')
    twine_grip(m, -14, 2, 1.3)
    m.box('shaft', A, [-1.1, 3.25, -1.1], [1.1, 31, 1.1], grad=(3.25, 4, 31, 6))
    for k, y in enumerate((12, 22)):
        m.box(f'ring_{k}', W, [-1.5, y, -1.5], [1.5, y + 1, 1.5], 4)
    m.box('collar', A, [-1.8, 30, -1.8], [1.8, 32.5, 1.8], 7)
    m.seg('quill_shaft', W, (0, 31), (4.5, 47), 1.25, 0.6, 3, 5)
    vane = [(0.75, 34), (2.75, 40), (4, 45)]
    for k, (x, y) in enumerate(vane):
        m.seg(f'vane_l{k}', A, (x, y), (x - 3.5 + k * 0.5, y + 3), 2.5, 0.35, 5 + k, 6 + k, tip=True)
        m.seg(f'vane_r{k}', A, (x + 0.5, y), (x + 3.5 - k * 0.25, y + 1), 2.5, 0.35, 5 + k, 6 + k, tip=True)
    m.seg('nib', A, (0, 31), (-1, 27.5), 1, 0.5, 8, 9, tip=True)
    m.box('inkpot', A, [1.2, 24, -1.5], [4.2, 27.5, 1.5], grad=(24, 2, 27.5, 4))
    m.box('inkpot_tie', W, [-1.4, 25.5, -1.6], [4.4, 26.25, 1.6], 3)
    m.box('ink_glow', F, [2, 27.5, -0.75], [3.5, 28.5, 0.75], 4)
    return m


ALL = [wild_banner_spear, wild_crow_bow, wild_guard_sword, wild_quarry_hammer, wild_rat_knife,
       wild_scarecrow_bell_staff, wild_scribe_staff]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
