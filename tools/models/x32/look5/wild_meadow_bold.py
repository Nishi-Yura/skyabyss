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
    # 古い鉄（2026-11-20 の作り直しから）: 樫の茶と分けるため、少し青みの灰。明るい段は打たれて光った縁
    'oldiron': ['#18181c', '#26262c', '#36363e', '#484852', '#5c5c68', '#72727e', '#8c8c98', '#aaaab4', '#cacad2'],
})
A, W, F = 'ash', 'straw', 'firefly'
I = 'oldiron'


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
    """鳥追いの弓（アンコモン）: 畑の鳥を追う素朴な丸木の弓。弓腕はまっすぐ細く、先はわらの縛り。
    上の先に黒い羽根が一枚結ばれる。握りは麦わらの紐巻き。光はない"""
    m = M('wild_crow_bow')
    m.box('grip', W, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'band_{n}', W, [9.75, y, -2.3], [14.25, y + 1.5, 2.3], 4)
    arm = [(12, 21), (13, 27), (12.5, 33), (10.5, 38.5), (7.5, 42.5), (4.5, 44.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'stave_{n}', A, [P(q) for q in arm], [2.6, 2.4, 2.2, 2, 1.8], 1.2, 3, 5, tip=False)
        m.seg(f'tie_{n}', W, P((6, 43.75)), P((4, 44.75)), 2.6, 1.4, 5, 6)
    m.seg('feather', A, (5, 45), (8.5, 49.5), 1.6, 0.4, 1, 2, tip=True)
    strings(m, (4.5, 44.5), (4.5, -14.5))
    return m


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

def meadow_haft(m, top):
    """牧草地の長柄（鎚・杖）: 麻紐巻きの握りと使い込んだ樫の柄。柄頭は鉄の口金と麦わらの吊り紐の輪"""
    twine_grip(m, -12, 2, 1.6)
    m.box('shaft', A, [-1.3, 3.25, -1.3], [1.3, top, 1.3], grad=(3.25, 4, top, 6))
    m.box('butt', I, [-2.0, -15.5, -2.0], [2.0, -13.25, 2.0], grad=(-15.5, 3, -13.25, 6))
    m.chain('loop', W, [(0, -15.5), (1.6, -17.5), (0, -19), (-1.6, -17.5), (0, -15.5)], [0.8, 0.8, 0.8, 0.8], 0.5, 3, 5, tip=False)


def wild_quarry_hammer():
    """石切りの鎚（アンコモン、2026-11-20 に作り直し・案 A）: 石工の両口鎚。大きな古い鉄の頭、+x は平たい打つ面（角が欠けている）、
    -x は細く尖った鑿。頭の上に樫の楔の頭が覗き、頭の根元に麦わらの紐が巻かれて房が下がる。アンコモンなので光なし
    ※ 2026-11-20 に Blockbench で手を入れた（鑿を下へ反って細る四角いメッシュにし、打つ面の摩れ・頭の凹みと錆・柄の木目を筆で描いた）。正本は tools/models/x32/wild_quarry_hammer.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_quarry_hammer')
    meadow_haft(m, 30)
    cy = 33.0
    m.box('head', I, [-5, cy - 3.6, -2.6], [5, cy + 3.6, 2.6], grad=(cy - 3.6, 3, cy + 3.6, 6))
    m.box('face', I, [5, cy - 4.2, -3.0], [7.6, cy + 4.2, 3.0], grad=(cy - 4.2, 4, cy + 4.2, 7))
    m.box('face_chip', I, [6.6, cy + 2.6, -3.1], [7.7, cy + 4.3, -1.6], 2)
    m.box('face_chip_b', I, [6.6, cy - 4.3, 1.6], [7.7, cy - 2.6, 3.1], 2)
    m.seg('pick', I, (-5, cy + 0.6), (-13.5, cy - 1.8), 5.6, 2.2, 4, 7, tip=True)
    m.box('wedge', A, [-1.0, cy + 3.6, -1.0], [1.0, cy + 4.6, 1.0], 6)
    m.box('lash', W, [-1.9, cy - 5.8, -1.9], [1.9, cy - 3.4, 1.9], grad=(cy - 5.8, 3, cy - 3.4, 5))
    m.chain('tassel', W, [(1.6, cy - 5.0), (3.0, cy - 8.5), (2.6, cy - 11)], [1.0, 0.8], 0.6, 3, 5, tip=True)
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
