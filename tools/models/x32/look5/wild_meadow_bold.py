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
    """古戦の旗槍（レア、2026-11-20 に作り直し・案 C）: 鎌槍。槍の穂の下に、草刈りの鎌の刃を横向きに縛りつけた村の鎌槍。縛りの縄から短い旗の切れ端と蛍の飾り
    ※ 2026-11-20 に Blockbench で手を入れた（穂を鎬のある木の葉形メッシュ、鎌の刃を反った片刃メッシュにし、旗を風になびかせて傾けた）。正本は tools/models/x32/wild_banner_spear.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_banner_spear')
    spear_shaft(m, 42)
    m.box('socket', I, [-1.7, 41, -1.7], [1.7, 44.5, 1.7], grad=(41, 3, 44.5, 6))
    m.chain('head', I, [(0, 44), (0, 49), (0, 55)], [3.6, 2.6], 1.0, 4, 7, tip=True)
    m.chain('sickle', I, [(0, 39), (-4, 40.5), (-8, 43.5), (-9.5, 47.5)], [2.8, 2.2, 1.4], 0.8, 4, 7, tip=True)
    m.box('lash', W, [-1.8, 37.6, -1.8], [1.8, 40.6, 1.8], grad=(37.6, 3, 40.6, 5))
    m.box('flag', W, [1.6, 32.5, -0.35], [6.0, 39.5, 0.35], grad=(32.5, 2, 39.5, 4))
    m.box('flag_tear', W, [3.6, 30.5, -0.35], [6.0, 32.5, 0.35], 2)
    m.box('charm_glow', F, [5.4, 29.0, -0.6], [6.6, 30.6, 0.6], 4)
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
    """衛士の払い剣（アンコモン、2026-11-20 に作り直し・案 A）: 衛士の幅広剣。幅広の真っ直ぐな鉄の刃、根元に払いを受ける鉤。鍔は麦わらの革を巻いた鉄の横木
    ※ 2026-11-20 に Blockbench で手を入れた（刃を鎬のある一枚の木の葉形メッシュにし、研いだ縁と樋を筆で描いた）。正本は tools/models/x32/wild_guard_sword.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_guard_sword')
    sword_hilt(m)
    m.box('guard', I, [-6.5, 7.25, -1.8], [6.5, 9.5, 1.8], grad=(7.25, 3, 9.5, 6))
    m.box('guard_wrap', W, [-2.6, 7.0, -2.0], [2.6, 9.75, 2.0], grad=(7, 3, 9.75, 5))
    m.chain('catch', I, [(-4, 9.5), (-6.5, 12), (-6, 15)], [2.0, 1.6], 1.0, 4, 6, tip=True)
    m.box('blade', I, [-3.2, 9.5, -0.9], [3.2, 40, 0.9], grad=(9.5, 3, 40, 6))
    m.box('fuller', I, [-0.6, 11, -1.0], [0.6, 33, 1.0], 2)
    m.seg('tip', I, (0, 39.5), (0, 45), 6.4, 0.9, 5, 7, tip=True)
    return m


# ---------------------------------------------------------------- 石切りの鎚

def meadow_haft(m, top):
    """牧草地の長柄（鎚・杖）: 麻紐巻きの握りと使い込んだ樫の柄。柄頭は鉄の口金と麦わらの吊り紐の輪"""
    twine_grip(m, -12, 2, 1.6)
    m.box('shaft', A, [-1.3, 3.25, -1.3], [1.3, top, 1.3], grad=(3.25, 4, top, 6))
    m.box('butt', I, [-2.0, -15.5, -2.0], [2.0, -13.25, 2.0], grad=(-15.5, 3, -13.25, 6))
    m.chain('loop', W, [(0, -15.5), (1.6, -17.5), (0, -19), (-1.6, -17.5), (0, -15.5)], [0.8, 0.8, 0.8, 0.8], 0.5, 3, 5, tip=False)


def sword_hilt(m, top=6):
    twine_grip(m, -7, top, 1.5)
    m.box('pommel', I, [-2.6, -10.5, -1.4], [2.6, -8.25, 1.4], grad=(-10.5, 3, -8.25, 6))


def knife_hilt(m):
    twine_grip(m, -5, 4, 1.4)
    m.box('pommel', I, [-2.0, -7.5, -1.4], [2.0, -6.25, 1.4], 5)


def spear_shaft(m, top):
    twine_grip(m, -16, 0, 1.5)
    m.seg('butt', I, (0, -17.25), (0, -21), 2.6, 1.3, 4, 6, tip=True)
    m.box('shaft', A, [-1.2, 1.25, -1.2], [1.2, top, 1.2], grad=(1.25, 4, top, 6))


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
    """鼠捕りの小刀（アンコモン、2026-11-20 に作り直し・案 A）: 罠師の鋸小刀。片刃の小刀、背に鋸の歯。柄頭に鼠の尾のような紐の輪
    ※ 2026-11-20 に Blockbench で手を入れた（刃を先が背へ上がる片刃のメッシュにし、背の鋸歯を左右交互に振り分け、縁を描いた）。正本は tools/models/x32/wild_rat_knife.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_rat_knife')
    knife_hilt(m)
    m.chain('tail', W, [(0, -7.5), (1.8, -9.5), (0.6, -12), (-1.2, -11)], [0.9, 0.9, 0.9], 0.5, 3, 5, tip=False)
    m.box('bolster', I, [-2.4, 5.25, -1.4], [2.8, 7.0, 1.4], 5)
    m.box('blade', I, [-2.0, 7.0, -0.7], [2.4, 21, 0.7], grad=(7, 3, 21, 6))
    for k in range(5):
        y = 8.5 + k * 2.4
        m.seg(f'tooth_{k}', I, (-2.0, y), (-3.4, y + 1.0), 1.4, 0.6, 5, 6, tip=True)
    m.seg('tip', I, (2.4, 20.5), (-2.0, 26), 3.0, 0.65, 5, 7, tip=True)
    return m


# ---------------------------------------------------------------- 案山子の鳴子杖

def wild_scarecrow_bell_staff():
    """案山子の鳴子杖（アンコモン、2026-11-20 に作り直し・案 A）: 案山子の十字。縦の杖の上に横木、両端から木の鳴子が紐で下がる。てっぺんに麦わら帽の頭（目は布の継ぎ当て）
    ※ 2026-11-20 に Blockbench で手を入れた（帽子と顔を傾け、鳴子を風になびくように振り、帽子の麦わらの筋と縫い口を描いた）。正本は tools/models/x32/wild_scarecrow_bell_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_scarecrow_bell_staff')
    meadow_haft(m, 40)
    m.box('arm', A, [-11, 33, -1.2], [11, 35.2, 1.2], grad=(33, 4, 35.2, 6))
    m.box('straw_wrap', W, [-2.0, 32, -2.0], [2.0, 36.2, 2.0], grad=(32, 3, 36.2, 5))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'straw_tuft_{n}', W, [sd * 11 - 1.4, 32.2, -1.6], [sd * 11 + 1.4, 36, 1.6], 4)
        m.box(f'cord_{n}', W, [sd * 9 - 0.3, 27.5, -0.3], [sd * 9 + 0.3, 33, 0.3], 3)
        m.box(f'clapper_bar_{n}', A, [sd * 9 - 2.6, 26.5, -1.2], [sd * 9 + 2.6, 27.7, 1.2], 6)
        for k, dx in enumerate((-1.6, 0, 1.6)):
            m.box(f'clapper_{n}{k}', A, [sd * 9 + dx - 0.6, 21.5, -1.0], [sd * 9 + dx + 0.6, 26.5, 1.0], grad=(21.5, 4, 26.5, 7))
    m.box('head', W, [-3.0, 40, -2.6], [3.0, 45.5, 2.6], grad=(40, 2, 45.5, 4))
    for k, x in enumerate((-1.3, 1.3)):
        for sd, z in ((1, 2.6), (-1, -2.9)):
            m.box(f'patch_{k}{"fb"[sd < 0]}', A, [x - 0.6, 42.4, z], [x + 0.6, 43.6, z + 0.3], 2)
    m.box('hat_brim', W, [-6, 45.5, -4.5], [6, 47, 4.5], grad=(45.5, 3, 47, 5))
    m.box('hat_crown', W, [-3.2, 47, -2.6], [3.2, 51, 2.6], grad=(47, 3, 51, 5))
    m.box('hat_band', A, [-3.3, 47, -2.7], [3.3, 48.2, 2.7], 4)
    return m


# ---------------------------------------------------------------- 写本師の羽杖

def wild_scribe_staff():
    """写本師の羽杖（アンコモン、2026-11-20 に作り直し・案 A）: 羽根ペンの杖。杖の先に大きな羽根ペンが斜めに挿さる。杖にはインク壺が括られている
    ※ 2026-11-20 に Blockbench で手を入れた（小羽の箱を羽軸の両側の羽板メッシュ 2 枚にし、羽の筋を描き、インク壺を傾けた）。正本は tools/models/x32/wild_scribe_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_scribe_staff')
    meadow_haft(m, 38)
    m.box('collar', I, [-2.0, 37, -2.0], [2.0, 39.5, 2.0], grad=(37, 3, 39.5, 6))
    m.seg('quill_shaft', W, (0, 38), (5.5, 58), 1.4, 0.8, 4, 6)
    for k, (t, w) in enumerate(((0.25, 3.0), (0.45, 3.8), (0.65, 3.6), (0.82, 2.6))):
        x, y = 5.5 * t, 38 + 20 * t
        m.seg(f'vane_l{k}', A, (x, y), (x - w, y + 2.6), 2.6, 0.4, 6, 8, tip=True)
        m.seg(f'vane_r{k}', A, (x + 0.4, y), (x + w * 0.8 + 0.4, y + 1.4), 2.6, 0.4, 6, 8, tip=True)
    m.seg('nib', I, (0, 38), (-1.0, 34.5), 1.2, 0.6, 6, 8, tip=True)
    m.box('inkpot', I, [1.4, 29, -1.8], [5.2, 33.5, 1.8], grad=(29, 2, 33.5, 5))
    m.box('inkpot_neck', I, [2.4, 33.5, -1.0], [4.2, 34.6, 1.0], 6)
    m.box('inkpot_tie', W, [-1.6, 31, -2.0], [5.4, 32, 2.0], 3)
    return m


ALL = [wild_banner_spear, wild_crow_bow, wild_guard_sword, wild_quarry_hammer, wild_rat_knife,
       wild_scarecrow_bell_staff, wild_scribe_staff]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
